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
REM  2. Verify Tailscale is installed
REM --------------------------------------------------------------------------
echo [2/5] Checking Tailscale...
where tailscale >nul 2>&1
if errorlevel 1 (
    REM Check common install paths
    if exist "C:\Program Files\Tailscale\tailscale.exe" (
        set "TAILSCALE_EXE=C:\Program Files\Tailscale\tailscale.exe"
    ) else (
        echo.
        echo  WARNING: Tailscale not found in PATH.
        echo  Clients can still connect on localhost for local testing,
        echo  but remote Tailscale connections will NOT work.
        echo.
        echo  Install Tailscale from: https://tailscale.com/download/windows
        echo.
        set "TAILSCALE_IP=127.0.0.1"
        goto :skip_tailscale
    )
) else (
    set "TAILSCALE_EXE=tailscale"
)

REM Get Tailscale IP
for /f "delims=" %%i in ('"%TAILSCALE_EXE%" ip --4 2^>nul') do set "TAILSCALE_IP=%%i"
if "!TAILSCALE_IP!"=="" (
    echo.
    echo  WARNING: Tailscale is installed but no IPv4 address found.
    echo  Is Tailscale connected? Run: tailscale up
    echo.
    echo  Falling back to 0.0.0.0 (all interfaces). Clients must use
    echo  this machine's IP address to connect.
    echo.
    set "TAILSCALE_IP=0.0.0.0"
)

echo  OK  Tailscale IP: !TAILSCALE_IP!

:skip_tailscale

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

REM Load server-config.properties if present
set "CONFIG_FILE=%SCRIPT_DIR%\server-config.properties"
set "SERVER_PORT=8080"
set "SERVER_BIND_ADDRESS=auto"
if exist "%CONFIG_FILE%" (
    for /f "eol=# tokens=1,2 delims==" %%a in (%CONFIG_FILE%) do (
        if "%%a"=="SERVER_PORT" set "SERVER_PORT=%%b"
        if "%%a"=="SERVER_BIND_ADDRESS" set "SERVER_BIND_ADDRESS=%%b"
    )
)

set "BIND_IP=0.0.0.0"
if "!SERVER_BIND_ADDRESS!"=="auto" (
    if not "!TAILSCALE_IP!"=="" (
        if not "!TAILSCALE_IP!"=="0.0.0.0" (
            set "BIND_IP=!TAILSCALE_IP!"
        )
    )
) else (
    set "BIND_IP=!SERVER_BIND_ADDRESS!"
)

REM --------------------------------------------------------------------------
REM  5. Launch the server
REM --------------------------------------------------------------------------
echo [5/5] Starting server...
echo.
echo  ---------------------------------------------------------------
echo   Server address:  http://!TAILSCALE_IP!:%SERVER_PORT%
echo   Bound interface: !BIND_IP!
echo   Chat interface:  http://!TAILSCALE_IP!:%SERVER_PORT%/
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

