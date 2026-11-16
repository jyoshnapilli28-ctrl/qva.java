@echo off
REM ==========================================================================
REM  Start-Client.bat — Private Chat Client Launcher
REM  Double-click this file to open the chat in your browser.
REM  No Java, Maven, or source code required.
REM ==========================================================================
title Private Chat Client

setlocal enabledelayedexpansion

echo.
echo  ===============================================================
echo   PRIVATE CHAT CLIENT
echo  ===============================================================
echo.

REM --------------------------------------------------------------------------
REM  Resolve the directory containing this script
REM --------------------------------------------------------------------------
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

REM --------------------------------------------------------------------------
REM  1. Read configuration from client-config.ini
REM --------------------------------------------------------------------------
set "CONFIG_FILE=%SCRIPT_DIR%\client-config.ini"
set "SERVER_URL="

if not exist "%CONFIG_FILE%" (
    echo  Config file not found. Creating default configuration...
    echo.
    echo  Please edit client-config.ini and set the host's private Tailscale Serve HTTPS name.
    echo  Then double-click Start-Client.bat again.
    echo.
    (
        echo # Private Chat Client Configuration
        echo # ===================================
        echo # Set SERVER_URL to the host's private HTTPS *.ts.net URL.
        echo # Example: SERVER_URL=https://qva-host.example-tailnet.ts.net
        echo #
        echo # Find the host's HTTPS name by running:
        echo #   tailscale serve status
        echo # on the HOST machine, then configure that name here.
        echo.
        echo SERVER_URL=https://SET-HOST-TS-NET-NAME
    ) > "%CONFIG_FILE%"

    REM Open the config file in Notepad for editing
    start notepad.exe "%CONFIG_FILE%"
    echo  Opened client-config.ini in Notepad.
    echo  Edit SERVER_URL, save the file, then run Start-Client.bat again.
    echo.
    pause
    exit /b 0
)

REM Parse the config file
for /f "eol=# tokens=1,2 delims==" %%a in (%CONFIG_FILE%) do (
    set "KEY=%%a"
    set "VAL=%%b"
    REM Trim whitespace from key
    set "KEY=!KEY: =!"
    if "!KEY!"=="SERVER_URL" (
        REM Trim leading/trailing whitespace from value
        set "SERVER_URL=!VAL!"
        set "SERVER_URL=!SERVER_URL: =!"
    )
)

REM --------------------------------------------------------------------------
REM  2. Validate the URL
REM --------------------------------------------------------------------------
if "!SERVER_URL!"=="" (
    echo  ERROR: SERVER_URL is not set in client-config.ini
    echo.
    echo  Edit the file and set:
    echo    SERVER_URL=https://qva-host.example-tailnet.ts.net
    echo.
    start notepad.exe "%CONFIG_FILE%"
    pause
    exit /b 1
)

echo [1/3] Server URL: !SERVER_URL!

REM Check for placeholder values
echo !SERVER_URL! | findstr /i "ENTER-SERVER" >nul
if not errorlevel 1 (
    echo.
    echo  ERROR: The SERVER_URL still contains the placeholder value.
    echo  Edit client-config.ini and replace it with the actual server IP.
    echo.
    start notepad.exe "%CONFIG_FILE%"
    pause
    exit /b 1
)

REM --------------------------------------------------------------------------
REM  3. Require private Tailscale HTTPS or localhost
REM --------------------------------------------------------------------------
echo !SERVER_URL! | findstr /i "trycloudflare cloudflareaccess" >nul
if not errorlevel 1 (
    echo ERROR: Public tunnel URLs are not supported. Use private Tailscale Serve.
    exit /b 1
)
echo !SERVER_URL! | findstr /i "localhost 127.0.0.1" >nul
if not errorlevel 1 (
    echo [2/3] Localhost connection detected.
    goto skip_tailscale
)
echo !SERVER_URL! | findstr /b /i "https://" >nul
if errorlevel 1 (
    echo ERROR: Remote QVA connections require private HTTPS through Tailscale Serve.
    exit /b 1
)
echo [2/3] Remote HTTPS configured. Ensure Tailscale is installed and connected.
:skip_tailscale

REM --------------------------------------------------------------------------
REM  4. Test server reachability (optional, non-blocking)
REM --------------------------------------------------------------------------
echo [3/3] Checking server connectivity...

REM Extract host and port from URL for ping test
REM This is a basic check; curl isn't always available on Windows
where curl >nul 2>&1
if not errorlevel 1 (
    curl -s --max-time 5 "!SERVER_URL!/api/auth/status" >nul 2>&1
    if errorlevel 1 (
        echo.
        echo  WARNING: Cannot reach !SERVER_URL!
        echo.
        echo  Possible causes:
        echo    1. The server is not running. Ask the server operator to start it.
        echo    2. Tailscale is not connected. Run: tailscale up
        echo    3. The IP/port in client-config.ini is wrong.
        echo    4. Windows Firewall is blocking port 8080 on the server.
        echo.
        echo  Opening browser anyway (it will show an error if the server is down)...
        echo.
    ) else (
        echo  OK  Server is reachable
    )
) else (
    echo  OK  Skipping reachability check (curl not available)
)

REM --------------------------------------------------------------------------
REM  5. Open the chat in the default browser
REM --------------------------------------------------------------------------
echo.
echo  Opening chat at: !SERVER_URL!
echo.
start "" "!SERVER_URL!"

echo  Browser launched. If nothing opens, paste this URL manually:
echo    !SERVER_URL!
echo.
echo  This window can be closed.
timeout /t 5 /nobreak >nul
exit /b 0

