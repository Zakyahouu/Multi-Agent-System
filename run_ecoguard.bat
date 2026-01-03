@echo off
setlocal enabledelayedexpansion

echo ============================================================
echo        ECOGUARDFARM - MULTI-AGENT SIMULATION
echo ============================================================
echo.

cd /d "%~dp0"

REM Set paths
set SRC_DIR=src\EcoGuardFarm
set BIN_DIR=bin\ecoguard
set LIB_DIR=lib\javalin
set "JADE_JAR=C:\Users\ZAKAR\Bureau\Univ\IA Master\S3\iadsma (mas)\Tp\jade\lib\jade.jar"
set RESOURCES=src\EcoGuardFarm\resources

REM Build classpath with all jars
set "CLASSPATH=%BIN_DIR%;%JADE_JAR%"
for %%f in (%LIB_DIR%\*.jar) do set "CLASSPATH=!CLASSPATH!;%%f"

echo [Build] Using JADE from: %JADE_JAR%
echo [Build] Compiling EcoGuardFarm sources...

REM Create bin directory
if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"

REM Copy resources
if exist "%RESOURCES%\public" (
    xcopy /E /I /Y "%RESOURCES%\public" "%BIN_DIR%\public" > nul 2>&1
    echo [Build] Copied public resources.
)

REM Compile all Java files
dir /s /b "%SRC_DIR%\*.java" > sources.txt
javac -encoding UTF-8 -d "%BIN_DIR%" -cp "%CLASSPATH%" @sources.txt 2>&1

if %ERRORLEVEL% neq 0 (
    echo [Build] Compilation failed!
    del sources.txt
    pause
    exit /b 1
)

del sources.txt
echo [Build] Compilation successful.
echo.

echo [Run] Starting EcoGuardFarm...
echo.

REM Run the application
java -cp "%CLASSPATH%" com.ecoguard.Main

echo.
echo [Run] Application exited with error code %ERRORLEVEL%
pause
endlocal
