/**
 * Private Chat — Main client-side JavaScript
 *
 * Responsibilities:
 *  - Login / logout via form POST to Spring Security
 *  - Load message history via REST on page load
 *  - Establish STOMP/WebSocket connection
 *  - Render new messages in real time
 *  - Track and display member presence
 *  - Reconnect automatically on disconnect
 */

'use strict';

/* ============================================================
   State
   ============================================================ */
const state = {
    username: null,
    displayName: null,
    stompClient: null,
    reconnectDelay: 2000,
    maxReconnectDelay: 30000,
    reconnectTimer: null,
    connected: false,
    room: 'general',
    lastMessageDate: null,
    members: {},        // username -> { displayName, online }
};

/* ============================================================
   DOM references
   ============================================================ */
const dom = {
    loginScreen:       () => document.getElementById('login-screen'),
    chatScreen:        () => document.getElementById('chat-screen'),
    loginForm:         () => document.getElementById('login-form'),
    loginError:        () => document.getElementById('login-error'),
    loginBtn:          () => document.getElementById('login-btn'),
    usernameInput:     () => document.getElementById('username'),
    passwordInput:     () => document.getElementById('password'),
    currentUserLabel:  () => document.getElementById('current-user-label'),
    connectionStatus:  () => document.getElementById('connection-status'),
    memberList:        () => document.getElementById('member-list'),
    messagesList:      () => document.getElementById('messages-list'),
    messageInput:      () => document.getElementById('message-input'),
    sendBtn:           () => document.getElementById('send-btn'),
    logoutBtn:         () => document.getElementById('logout-btn'),
    scrollAnchor:      () => document.getElementById('scroll-anchor'),
};

/* ============================================================
   Login
   ============================================================ */
dom.loginForm().addEventListener('submit', async (e) => {
    e.preventDefault();
    const username = dom.usernameInput().value.trim();
    const password = dom.passwordInput().value;

    setLoginError('');
    dom.loginBtn().disabled = true;
    dom.loginBtn().textContent = 'Signing in…';

    try {
        const formData = new URLSearchParams();
        formData.append('username', username);
        formData.append('password', password);

        const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData,
            credentials: 'include',
        });

        const data = await res.json().catch(() => ({}));

        if (res.ok && data.success) {
            state.username = data.username;
            await initChat();
        } else {
            setLoginError(data.error || 'Invalid username or password.');
        }
    } catch (err) {
        setLoginError('Cannot reach the server. Check your connection.');
    } finally {
        dom.loginBtn().disabled = false;
        dom.loginBtn().textContent = 'Sign In';
    }
});

function setLoginError(msg) {
    const el = dom.loginError();
    if (msg) {
        el.textContent = msg;
        el.classList.remove('hidden');
    } else {
        el.textContent = '';
        el.classList.add('hidden');
    }
}

/* ============================================================
   Logout
   ============================================================ */
dom.logoutBtn().addEventListener('click', async () => {
    disconnectStomp();
    await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
    state.username = null;
    state.displayName = null;
    state.members = {};
    dom.messagesList().innerHTML = '';
    showScreen('login');
});

/* ============================================================
   Initialise chat after login
   ============================================================ */
async function initChat() {
    // Load current user profile
    const meRes = await fetch('/api/auth/me', { credentials: 'include' });
    const me = await meRes.json();
    state.username = me.username;
    dom.currentUserLabel().textContent = me.username;

    showScreen('chat');

    // Load users
    await loadUsers();

    // Load message history
    await loadHistory();

    // Connect WebSocket
    connectStomp();
}

/* ============================================================
   User / member list
   ============================================================ */
async function loadUsers() {
    try {
        const res = await fetch('/api/users', { credentials: 'include' });
        const users = await res.json();
        state.members = {};
        users.forEach(u => {
            state.members[u.username] = { displayName: u.displayName, online: u.online };
        });
        renderMemberList();
    } catch (e) {
        console.warn('Failed to load users:', e);
    }
}

function renderMemberList() {
    const list = dom.memberList();
    list.innerHTML = '';

    // Sort: online first, then alphabetical
    const sorted = Object.entries(state.members).sort(([, a], [, b]) => {
        if (a.online !== b.online) return a.online ? -1 : 1;
        return a.displayName.localeCompare(b.displayName);
    });

    sorted.forEach(([username, info]) => {
        const li = document.createElement('li');
        li.className = 'member-item';
        li.dataset.username = username;

        const initial = info.displayName.charAt(0).toUpperCase();
        li.innerHTML = `
            <div class="member-avatar ${info.online ? 'online' : ''}">
                ${initial}
            </div>
            <div class="member-info">
                <div class="member-name">${escHtml(info.displayName)}</div>
                <div class="member-status ${info.online ? 'online' : 'offline'}">
                    ${info.online ? 'Online' : 'Offline'}
                </div>
            </div>`;
        list.appendChild(li);
    });
}

function updateMemberPresence(username, online) {
    if (state.members[username]) {
        state.members[username].online = online;
    } else {
        // Unknown user; refresh list
        loadUsers();
        return;
    }
    renderMemberList();
}

/* ============================================================
   Message history
   ============================================================ */
async function loadHistory() {
    try {
        const res = await fetch(`/api/rooms/${state.room}/messages?limit=100`, { credentials: 'include' });
        const messages = await res.json();
        dom.messagesList().innerHTML = '';
        state.lastMessageDate = null;
        messages.forEach(msg => appendMessage(msg, false));
        scrollToBottom();
    } catch (e) {
        console.warn('Failed to load history:', e);
    }
}

/* ============================================================
   WebSocket / STOMP  (uses @stomp/stompjs v7 UMD bundle)
   ============================================================ */
function connectStomp() {
    disconnectStomp();
    setConnectionStatus('connecting');

    state.stompClient = new StompJs.Client({
        webSocketFactory: () => new SockJS('/ws/chat'),
        reconnectDelay: 0,           // we manage reconnect ourselves
        debug: () => {},             // suppress verbose logs

        onConnect: onStompConnected,
        onStompError: onStompError,
        onDisconnect: () => {
            state.connected = false;
            setConnectionStatus('disconnected');
            scheduleReconnect();
        },
        onWebSocketClose: () => {
            if (state.connected) {
                state.connected = false;
                setConnectionStatus('disconnected');
                scheduleReconnect();
            }
        },
    });

    state.stompClient.activate();
}

function onStompConnected(frame) {
    state.connected = true;
    state.reconnectDelay = 2000;
    clearTimeout(state.reconnectTimer);
    setConnectionStatus('connected');
    console.log('STOMP connected');

    // Subscribe to room messages
    state.stompClient.subscribe(`/topic/messages/${state.room}`, (msg) => {
        const data = JSON.parse(msg.body);
        appendMessage(data, true);
    });

    // Subscribe to presence events
    state.stompClient.subscribe('/topic/presence', (msg) => {
        const event = JSON.parse(msg.body);
        const online = event.type === 'CONNECTED';
        updateMemberPresence(event.username, online);
        showSystemMessage(`${event.username} ${online ? 'joined' : 'left'} the chat`);
    });
}

function onStompError(frame) {
    state.connected = false;
    setConnectionStatus('disconnected');
    console.warn('STOMP error:', frame);
    scheduleReconnect();
}

function scheduleReconnect() {
    clearTimeout(state.reconnectTimer);
    state.reconnectTimer = setTimeout(() => {
        console.log('Reconnecting…');
        connectStomp();
    }, state.reconnectDelay);
    // Exponential backoff, capped at maxReconnectDelay
    state.reconnectDelay = Math.min(state.reconnectDelay * 2, state.maxReconnectDelay);
}

function disconnectStomp() {
    clearTimeout(state.reconnectTimer);
    if (state.stompClient) {
        try { state.stompClient.deactivate(); } catch (_) {}
        state.stompClient = null;
    }
    state.connected = false;
}

/* ============================================================
   Send message
   ============================================================ */
dom.sendBtn().addEventListener('click', sendMessage);

dom.messageInput().addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});

function sendMessage() {
    const input = dom.messageInput();
    const content = input.value.trim();
    if (!content) return;
    if (!state.connected || !state.stompClient) {
        alert('Not connected. Please wait for reconnection.');
        return;
    }

    state.stompClient.publish({
        destination: '/app/chat.send',
        body: JSON.stringify({
            content: content,
            roomName: state.room,
        }),
    });

    input.value = '';
    input.focus();
}

/* ============================================================
   Render messages
   ============================================================ */
function appendMessage(msg, scroll) {
    const list = dom.messagesList();

    // Date separator when the day changes
    const msgDate = new Date(msg.sentAt);
    const msgDateStr = msgDate.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });
    if (msgDateStr !== state.lastMessageDate) {
        const sep = document.createElement('div');
        sep.className = 'date-separator';
        sep.textContent = msgDateStr;
        list.appendChild(sep);
        state.lastMessageDate = msgDateStr;
    }

    const isOwn = msg.username === state.username;
    const timeStr = msgDate.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });

    const row = document.createElement('div');
    row.className = `message-row ${isOwn ? 'own' : 'other'}`;
    row.innerHTML = `
        <div class="message-meta">
            ${!isOwn ? `<strong>${escHtml(msg.displayName)}</strong> · ` : ''}${timeStr}
        </div>
        <div class="message-bubble">${escHtml(msg.content)}</div>`;

    list.appendChild(row);

    if (scroll) scrollToBottom();
}

function showSystemMessage(text) {
    const list = dom.messagesList();
    const div = document.createElement('div');
    div.className = 'system-message';
    div.textContent = text;
    list.appendChild(div);
    scrollToBottom();
}

/* ============================================================
   UI helpers
   ============================================================ */
function showScreen(name) {
    dom.loginScreen().classList.toggle('hidden', name !== 'login');
    dom.chatScreen().classList.toggle('hidden', name !== 'chat');
}

function setConnectionStatus(status) {
    const el = dom.connectionStatus();
    el.className = `status-badge ${status}`;
    el.textContent = {
        connected: 'Connected',
        connecting: 'Connecting…',
        disconnected: 'Disconnected',
    }[status] || status;
}

function scrollToBottom() {
    const anchor = dom.scrollAnchor();
    anchor.scrollIntoView({ behavior: 'instant' });
}

function escHtml(str) {
    const d = document.createElement('div');
    d.appendChild(document.createTextNode(str));
    return d.innerHTML;
}

/* ============================================================
   Session check on load
   — if the browser already has a valid session, skip login
   ============================================================ */
(async function checkExistingSession() {
    try {
        const res = await fetch('/api/auth/me', { credentials: 'include' });
        if (res.ok) {
            const data = await res.json();
            if (data.authenticated) {
                state.username = data.username;
                await initChat();
                return;
            }
        }
    } catch (_) {}
    showScreen('login');
})();
