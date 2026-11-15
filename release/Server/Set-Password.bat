@echo off
REM ==========================================================================
REM  Set-Password.bat — Administrative User Password Assignment
REM  Safely hashes and assigns individual passwords without plaintext logging.
REM ==========================================================================
title Set User Password

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

if "%~1"=="" (
    echo.
    echo  ===============================================================
    echo   SET USER PASSWORD — PRIVATE CHAT SERVER
    echo  ===============================================================
    echo.
    echo   Authorized usernames: jyoshna, meghana, yash, yashuu
    echo.
    set /p USERNAME=" Enter username: "
) else (
    set "USERNAME=%~1"
)

if "%USERNAME%"=="" (
    echo  ERROR: Username cannot be empty.
    pause
    exit /b 1
)

set "JAR_FILE=%SCRIPT_DIR%\private-chat-server.jar"
if not exist "%JAR_FILE%" (
    echo  ERROR: private-chat-server.jar not found in: %SCRIPT_DIR%
    pause
    exit /b 1
)

set "DATA_DIR=%SCRIPT_DIR%\data"
if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"

java -Dspring.main.web-application-type=none ^
     "-DDB_PATH=%DATA_DIR:\=/%/chat.db" ^
     -jar "%JAR_FILE%" --set-password=%USERNAME%

echo.
pause

