@echo off
REM ============================================================
REM Smart Farm V2 - Multi-Agent Simulation Launcher
REM ============================================================

echo.
echo ============================================================
echo        SMART FARM V2 - MULTI-AGENT SIMULATION
echo ============================================================
echo.

REM Set Java Home (comment out if using system Java)
REM set JAVA_HOME=C:\Program Files\Java\jdk-17

REM Set paths
set PROJECT_DIR=%~dp0
set SRC_DIR=%PROJECT_DIR%src\SmartFarm
set BIN_DIR=%PROJECT_DIR%bin
set JADE_JAR=C:\Users\ZAKAR\Bureau\Univ\IA Master\S3\iadsma (mas)\Tp\jade\lib\jade.jar
set JAVALIN_DIR=%PROJECT_DIR%lib\javalin

REM Build classpath with all required JARs
set CLASSPATH=%BIN_DIR%;%JADE_JAR%
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\javalin-5.6.3.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\slf4j-api-2.0.9.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\slf4j-simple-2.0.9.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\jetty-server-11.0.18.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\jetty-servlet-11.0.18.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\jetty-http-11.0.18.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\jetty-io-11.0.18.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\jetty-util-11.0.18.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\jetty-security-11.0.18.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\websocket-jetty-server-11.0.18.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\websocket-jetty-api-11.0.18.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\websocket-core-server-11.0.18.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\websocket-core-common-11.0.18.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\websocket-servlet-11.0.18.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\websocket-jetty-common-11.0.18.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\kotlin-stdlib-1.9.20.jar
set CLASSPATH=%CLASSPATH%;%JAVALIN_DIR%\jakarta.servlet-api-5.0.0.jar

REM Create bin directory if it doesn't exist
if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"

echo [Build] Compiling Smart Farm V2 sources...
echo.

REM Compile all Java files
javac --release 17 -encoding UTF-8 -cp "%CLASSPATH%" -d "%BIN_DIR%" ^
    "%SRC_DIR%\com\farm\models\*.java" ^
    "%SRC_DIR%\com\farm\gui\*.java" ^
    "%SRC_DIR%\com\farm\agents\*.java" ^
    "%SRC_DIR%\com\farm\Main.java"

if %errorlevel% neq 0 (
    echo.
    echo [Build] COMPILATION FAILED! Check errors above.
    pause
    exit /b 1
)

echo [Build] Compilation successful!
echo.

REM Copy static resources
echo [Build] Copying web resources...
if not exist "%BIN_DIR%\public" mkdir "%BIN_DIR%\public"
copy /Y "%SRC_DIR%\resources\public\*" "%BIN_DIR%\public\" > nul 2>&1
echo [Build] Resources copied.
echo.

REM Parse command line arguments
set FIELDS=3
set DRONES=2
set SUPPLIERS=2

:parse_args
if "%1"=="" goto run
if "%1"=="-f" set FIELDS=%2 & shift & shift & goto parse_args
if "%1"=="--fields" set FIELDS=%2 & shift & shift & goto parse_args
if "%1"=="-d" set DRONES=%2 & shift & shift & goto parse_args
if "%1"=="--drones" set DRONES=%2 & shift & shift & goto parse_args
if "%1"=="-s" set SUPPLIERS=%2 & shift & shift & goto parse_args
if "%1"=="--suppliers" set SUPPLIERS=%2 & shift & shift & goto parse_args
if "%1"=="-h" goto help
if "%1"=="--help" goto help
shift
goto parse_args

:help
echo Usage: run_smart_farm.bat [options]
echo.
echo Options:
echo   -f, --fields ^<n^>     Number of fields (1-6, default: 3)
echo   -d, --drones ^<n^>     Number of drones (1-3, default: 2)
echo   -s, --suppliers ^<n^>  Number of water suppliers (1-4, default: 2)
echo   -h, --help           Show this help message
echo.
exit /b 0

:run
echo [Run] Starting Smart Farm V2...
echo [Run] Configuration: %FIELDS% fields, %DRONES% drones, %SUPPLIERS% suppliers
echo.
echo ============================================================
echo.

REM Run the application
java -cp "%CLASSPATH%" -Dfile.encoding=UTF-8 com.farm.Main --fields %FIELDS% --drones %DRONES% --suppliers %SUPPLIERS%

if %errorlevel% neq 0 (
    echo.
    echo [Run] Application exited with error code %errorlevel%
    pause
)
