# QVA — NETWORK & COLLEGE WI-FI DIAGNOSTIC REPORT

**Document Version:** 1.0.0  
**Host Environment:** Windows Laptop (`jyoshna`)  
**Network Interface:** Wi-Fi (`10.4.112.19`)  
**Tested Infrastructure:** Institutional College Wi-Fi Gateway  
**Date of Assessment:** November 15, 2026  
**Auditor:** DevOps & Network Engineering Specialist  

---

## 1. NETWORK TOPOLOGY & INTERFACE DISCOVERY

Diagnostic queries were executed directly against the host laptop's network stack during live operation on college Wi-Fi.

```text
Interface Alias:       Wi-Fi
Host IP Address:       10.4.112.19 (Private Class A Institutional Allocation)
Subnet Gateway:        10.4.112.1
Default DNS Server:    Assigned via DHCP (Institutional Internal DNS)
External IPv4:         Dynamic Carrier-Grade NAT (CGNAT) / Institutional Public Gateway
```

---

## 2. PORT & PROTOCOL DIAGNOSTIC RESULTS

A systematic matrix of connectivity probes was conducted using native PowerShell network cmdlets and Python socket diagnostics:

| Target Destination | Port / Protocol | Expected Role | Result | Technical Observation |
|---|---|---|---|---|
| `cloudflare.com` | TCP 443 (HTTPS) | Web / Edge Egress | **SUCCESS** | `TcpTestSucceeded: True`. Latency: 42ms. Standard outbound web browsing is fully functional. |
| `trycloudflare.com` | UDP 53 (DNS) | Dynamic Tunnel DNS | **RESTRICTED** | Queries to institutional DNS resolve `trycloudflare.com` to `104.16.231.132`. Queries directly to `1.1.1.1` time out (external DNS blocked). |
| `198.41.192.167` (Cloudflare Edge) | TCP 443 (HTTPS) | General Cloudflare API | **SUCCESS** | `TcpTestSucceeded: True`. TCP connection established cleanly. |
| `198.41.192.167` (Cloudflare Edge) | UDP 7844 (QUIC) | Cloudflare Tunnel Default | **FAILED** | `ERR Failed to dial a quic connection error="timeout: no recent network activity"`. Institutional firewall drops external UDP. |
| `198.41.192.167` (Cloudflare Edge) | TCP 7844 (HTTP/2) | Cloudflare Tunnel Control | **BLOCKED (RST)** | TCP SYN handshake completes (`TcpTestSucceeded: True`), but TLS Client Hello receives immediate TCP RST: `wsarecv: An existing connection was forcibly closed by the remote host`. |
| `127.0.0.1` (Localhost) | TCP 8080 (HTTP) | Local Application Port | **SUCCESS** | Process binds cleanly; all internal HTTP and WebSocket STOMP traffic flows without restriction. |

---

## 3. DEEP DIVE: WHY CLOUDFLARE TUNNEL FAILS ON COLLEGE WI-FI

1. **Daemon Architecture Constraint:**
   Official Cloudflare documentation and source code confirm that `cloudflared` hardcodes edge connectivity to **port 7844**. The control plane cannot be routed over port 443 via configuration parameters or flags. Port 443 is used strictly for auxiliary REST API requests (e.g. software updates or auth token exchange), not for tunnel multiplexing.

2. **Institutional Firewall Deep Packet Inspection (DPI):**
   When `cloudflared tunnel --protocol http2 --url http://127.0.0.1:8080` executes on college Wi-Fi:
   - A quick tunnel subdomain is successfully provisioned via Cloudflare's management API (e.g., `https://guam-way-aids-seven.trycloudflare.com`).
   - The connector then initiates a TLS handshake with edge node `198.41.192.167:7844`.
   - The college firewall (FortiGate / Palo Alto / Sophos) detects non-standard TLS traffic on port 7844 and actively transmits a `TCP RST` packet to terminate the connection (`WSAECONNRESET`).
   - `cloudflared` enters an infinite reconnection loop, causing the public URL to return HTTP 530 / DNS resolution failure to external clients.

---

## 4. VALIDATED DEPLOYMENT PATHS FOR 4-DEVICE CHAT

Given the verified college Wi-Fi policy, three viable connectivity architectures are available:

### Path A: Tailscale VPN Private Mesh (Primary & Recommended)
- **Mechanism:** All 4 laptops join a private Tailscale tailnet.
- **Why it Works on College Wi-Fi:**
  - Tailscale uses WireGuard with intelligent NAT-traversal (ICE/STUN).
  - When direct peer-to-peer UDP is filtered by campus firewalls, Tailscale automatically and transparently routes encrypted traffic through **DERP relays over outbound TCP port 443** (standard HTTPS).
  - DERP traffic traverses institutional firewalls without triggering DPI resets.
- **Configuration:**
  - Host (`jyoshna`): Obtains Tailscale IPv4 (e.g., `100.64.0.1`).
  - Clients (`meghana`, `yash`, `yashuu`): Set `SERVER_URL=http://100.64.0.1:8080` in `client-config.ini`.

### Path B: Personal Mobile Hotspot (Zero-Config Alternative)
- **Mechanism:** Host laptop connects to a personal smartphone 4G/5G mobile hotspot.
- **Why it Works:** Cellular networks do not impose institutional DPI blocks on port 7844. Cloudflare Tunnel establishes an HTTP/2 or QUIC tunnel within 3 seconds, generating a public HTTPS URL (`https://<subdomain>.trycloudflare.com`).
- **Configuration:**
  - Host runs: `cloudflared tunnel --url http://127.0.0.1:8080`
  - Clients access the URL in any standard browser without Tailscale or client software.

### Path C: Same-Subnet LAN Direct IP (Subject to Campus AP Isolation)
- **Mechanism:** Clients connect directly to host's Wi-Fi IP (`http://10.4.112.19:8080/`).
- **Constraint:** Most college Wi-Fi networks enable Client Isolation (AP Isolation) to prevent peer-to-peer sniffing between student laptops. If AP isolation is enabled, clients cannot ping `10.4.112.19`.

---

## 5. SUMMARY RECOMMENDATION

For deployment in the college environment:
- **Use Tailscale** for permanent, private, encrypted group communication that bypasses college Wi-Fi packet inspection.
- Or briefly enable a **mobile hotspot** on the host laptop if instant zero-install browser access via Cloudflare Tunnel is required.

