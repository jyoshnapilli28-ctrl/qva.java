@echo off
REM ==========================================================================
REM  Start-Server.bat — Private Chat Server Launcher
REM  Place this file in the same directory as private-chat-server.jar
REM ==========================================================================
title Private Chat Server

setlocal enabledelayedexpansion

echo.
echo  ===============================================================
echo   PRIVATE CHAT SERVER
echo  ===============================================================
echo.

REM --------------------------------------------------------------------------
REM  Resolve the directory containing this script
REM --------------------------------------------------------------------------
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

REM --------------------------------------------------------------------------
REM  1. Verify Java is installed and meets minimum version requirement
REM --------------------------------------------------------------------------
echo [1/5] Checking Java runtime...
where java >nul 2>&1
if errorlevel 1 (
    echo.
    echo  ERROR: Java is not installed or not in PATH.
    echo.
    echo  This server requires Java 17 or later.
    echo  Download it free from: https://adoptium.net/
    echo.
    goto :error_exit
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER_STR=%%v"
)
REM Strip quotes
set "JAVA_VER_STR=%JAVA_VER_STR:"=%"
REM Extract major version (handles both "17.0.x" and "1.8.x" formats)
for /f "delims=." %%m in ("%JAVA_VER_STR%") do set "JAVA_MAJOR=%%m"
if "%JAVA_MAJOR%"=="1" (
    for /f "tokens=2 delims=." %%m in ("%JAVA_VER_STR%") do set "JAVA_MAJOR=%%m"
)
if %JAVA_MAJOR% LSS 17 (
    echo.
    echo  ERROR: Java %JAVA_MAJOR% detected. Java 17 or later is required.
    echo  Download it free from: https://adoptium.net/
    echo.
    goto :error_exit
)
echo  OK  Java %JAVA_VER_STR% detected

REM --------------------------------------------------------------------------
REM  2. Keep the application loopback-only; Tailscale Serve provides private HTTPS
REM --------------------------------------------------------------------------
echo [2/5] Application will bind to 127.0.0.1 only.
echo  Configure private Tailscale Serve after startup: tailscale serve --bg 8080
echo  Do not enable Tailscale Funnel, router port forwarding, or public tunnels.

REM --------------------------------------------------------------------------
REM  3. Verify JAR file exists
REM --------------------------------------------------------------------------
echo [3/5] Checking application JAR...
set "JAR_FILE=%SCRIPT_DIR%\private-chat-server.jar"
if not exist "%JAR_FILE%" (
    echo.
    echo  ERROR: private-chat-server.jar not found in:
    echo    %SCRIPT_DIR%
    echo.
    echo  Make sure Start-Server.bat is in the same folder as the JAR.
    echo.
    goto :error_exit
)
echo  OK  JAR found

REM --------------------------------------------------------------------------
REM  4. Create data directory if it does not exist
REM --------------------------------------------------------------------------
echo [4/5] Preparing data directory...
set "DATA_DIR=%SCRIPT_DIR%\data"
if not exist "%DATA_DIR%" (
    mkdir "%DATA_DIR%"
    echo  OK  Created: %DATA_DIR%
) else (
    echo  OK  Data directory exists: %DATA_DIR%
)

REM Load the application port from server-config.properties if present.
set "CONFIG_FILE=%SCRIPT_DIR%\server-config.properties"
set "SERVER_PORT=8080"
if exist "%CONFIG_FILE%" (
    for /f "eol=# tokens=1,2 delims==" %%a in (%CONFIG_FILE%) do (
        if "%%a"=="SERVER_PORT" set "SERVER_PORT=%%b"
    )
)
set "BIND_IP=127.0.0.1"

REM --------------------------------------------------------------------------
REM  5. Launch the server
REM --------------------------------------------------------------------------
echo [5/5] Starting server...
echo.
echo  ---------------------------------------------------------------
echo   Local app URL:   http://127.0.0.1:%SERVER_PORT%
echo   Bound interface: 127.0.0.1 (loopback only)
echo   Private URL:     Enable Tailscale Serve for this port
echo   Database:        %DATA_DIR%\chat.db
echo   Press CTRL+C to stop the server gracefully.
echo  ---------------------------------------------------------------
echo.

REM Set environment variables for Spring Boot
set "DB_PATH=%DATA_DIR%\chat.db"
set "JAVA_OPTS=-Xmx256m -Xms64m"

java %JAVA_OPTS% ^
    -Dserver.port=%SERVER_PORT% ^
    -Dserver.address=!BIND_IP! ^
    "-DDB_PATH=%DATA_DIR:\=/%/chat.db" ^
    -Dspring.profiles.active=prod ^
    -jar "%JAR_FILE%"

echo.
echo  Server stopped.
echo  Your chat database is preserved at: %DATA_DIR%\chat.db
echo.
goto :eof

:error_exit
echo.
echo  Startup failed. Press any key to close this window.
pause >nul
exit /b 1

