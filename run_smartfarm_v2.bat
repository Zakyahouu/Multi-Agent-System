@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ============================================================
echo        SMARTFARM V2 - MULTI-AGENT SIMULATION
echo ============================================================
echo.

:: Configuration
set "PROJECT_DIR=%~dp0"
set "SRC_DIR=%PROJECT_DIR%src\SmartFarmV2"
set "OUT_DIR=%PROJECT_DIR%out\SmartFarmV2"
set "LIB_DIR=%PROJECT_DIR%lib\javalin"
set "RES_DIR=%SRC_DIR%\resources\public"
set "JADE_JAR=C:\Users\ZAKAR\Bureau\Univ\IA Master\S3\iadsma (mas)\Tp\jade\lib\jade.jar"

echo [Build] JADE location: %JADE_JAR%
echo.

:: Create output directories
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
if not exist "%OUT_DIR%\public" mkdir "%OUT_DIR%\public"

:: Initialize classpath with JADE
set "FULL_CP=%JADE_JAR%"

:: Add all Javalin jars
for %%f in ("%LIB_DIR%\*.jar") do (
    set "FULL_CP=!FULL_CP!;%%~f"
)

:: Add output directory
set "FULL_CP=!FULL_CP!;%OUT_DIR%"

echo [Build] Compiling SmartFarmV2 sources...

:: Find all Java files and compile them
dir /s /b "%SRC_DIR%\*.java" > "%OUT_DIR%\sources.txt"
javac -encoding UTF-8 -d "%OUT_DIR%" -cp "%FULL_CP%" @"%OUT_DIR%\sources.txt"

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)

:: Copy HTML resources
echo [Build] Copying resources...
if exist "%RES_DIR%\index.html" (
    copy /Y "%RES_DIR%\index.html" "%OUT_DIR%\public\" >nul
)

echo [Build] Compilation successful.
echo.

echo [Run] Starting SmartFarmV2...
echo.

:: Run
java -cp "%FULL_CP%" com.smartfarm.Main

pause
