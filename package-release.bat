@echo off
REM ==========================================================================
REM  package-release.bat — Automated Build and Packaging Script
REM  Builds the application, validates tests, and generates distributable packages.
REM ==========================================================================
setlocal enabledelayedexpansion

echo.
echo ==========================================================================
echo  PRIVATE CHAT — RELEASE PACKAGING
echo ==========================================================================
echo.

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

cd /d "%ROOT_DIR%"

REM --------------------------------------------------------------------------
REM  Step 1: Check prerequisites
REM --------------------------------------------------------------------------
echo [1/6] Checking Java and Maven...
where java >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java is not in PATH.
    exit /b 1
)

where mvn >nul 2>&1
if errorlevel 1 (
    echo ERROR: Maven is not in PATH.
    exit /b 1
)
echo  OK  Java and Maven found.

REM --------------------------------------------------------------------------
REM  Step 2: Build and Test Application
REM --------------------------------------------------------------------------
echo.
echo [2/6] Building application and executing automated tests...
call mvn -Pqva-web clean verify
if errorlevel 1 (
    echo.
    echo ERROR: Maven build or automated tests failed. Packaging aborted.
    exit /b 1
)

set "SOURCE_JAR=%ROOT_DIR%\target\private-chat-server.jar"
if not exist "%SOURCE_JAR%" (
    echo.
    echo ERROR: Expected output JAR not found at:
    echo   %SOURCE_JAR%
    exit /b 1
)
echo  OK  Maven build and all tests passed.

REM --------------------------------------------------------------------------
REM  Step 3: Update deployment/Server with executable JAR
REM --------------------------------------------------------------------------
echo.
echo [3/6] Deploying executable JAR to deployment/Server/...
copy /y "%SOURCE_JAR%" "%ROOT_DIR%\deployment\Server\private-chat-server.jar" >nul
if errorlevel 1 (
    echo ERROR: Failed to copy JAR to deployment/Server/
    exit /b 1
)
echo  OK  Copied private-chat-server.jar to deployment/Server/

REM --------------------------------------------------------------------------
REM  Step 4: Stage release/Server
REM --------------------------------------------------------------------------
echo.
echo [4/6] Staging release/Server/...
set "REL_SERVER=%ROOT_DIR%\release\Server"
if not exist "%REL_SERVER%" mkdir "%REL_SERVER%"
if not exist "%REL_SERVER%\data" mkdir "%REL_SERVER%\data"
if not exist "%REL_SERVER%\backups" mkdir "%REL_SERVER%\backups"

copy /y "%ROOT_DIR%\deployment\Server\Start-Server.bat" "%REL_SERVER%\" >nul
copy /y "%ROOT_DIR%\deployment\Server\Set-Password.bat" "%REL_SERVER%\" >nul
copy /y "%ROOT_DIR%\deployment\Server\private-chat-server.jar" "%REL_SERVER%\" >nul
copy /y "%ROOT_DIR%\deployment\Server\server-config.properties" "%REL_SERVER%\" >nul
copy /y "%ROOT_DIR%\deployment\Server\README-SERVER.txt" "%REL_SERVER%\" >nul
echo  OK  release/Server staged successfully.

REM --------------------------------------------------------------------------
REM  Step 5: Stage release/Client
REM --------------------------------------------------------------------------
echo.
echo [5/6] Staging release/Client/...
set "REL_CLIENT=%ROOT_DIR%\release\Client"
if not exist "%REL_CLIENT%" mkdir "%REL_CLIENT%"

copy /y "%ROOT_DIR%\deployment\Client\Start-Client.bat" "%REL_CLIENT%\" >nul
copy /y "%ROOT_DIR%\deployment\Client\client-config.ini" "%REL_CLIENT%\" >nul
copy /y "%ROOT_DIR%\deployment\Client\README-CLIENT.txt" "%REL_CLIENT%\" >nul
echo  OK  release/Client staged successfully.

REM --------------------------------------------------------------------------
REM  Step 6: Generate Private-Chat-Client.zip
REM --------------------------------------------------------------------------
echo.
echo [6/6] Generating release/Private-Chat-Client.zip...
set "ZIP_PATH=%ROOT_DIR%\release\Private-Chat-Client.zip"
if exist "%ZIP_PATH%" del /f /q "%ZIP_PATH%"

powershell -NoProfile -Command ^
  "Compress-Archive -Path '%REL_CLIENT%\*' -DestinationPath '%ZIP_PATH%' -Force"

if not exist "%ZIP_PATH%" (
    echo ERROR: Failed to generate client ZIP archive.
    exit /b 1
)
echo  OK  Generated: release\Private-Chat-Client.zip

echo.
echo ==========================================================================
echo  RELEASE PACKAGING COMPLETE
echo ==========================================================================
echo.
echo  Server Package:
echo    release\Server\Start-Server.bat
echo    release\Server\private-chat-server.jar
echo    release\Server\server-config.properties
echo    release\Server\README-SERVER.txt
echo    release\Server\data\
echo    release\Server\backups\
echo.
echo  Client Package:
echo    release\Private-Chat-Client.zip
echo      ├── Start-Client.bat
echo      ├── client-config.ini
echo      └── README-CLIENT.txt
echo.
echo  Shareable Client ZIP is ready for distribution to the 3 client laptops.
echo ==========================================================================
