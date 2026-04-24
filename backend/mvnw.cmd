@REM ---------------------------------------------------------------------------
@REM Apache Maven Wrapper startup script (Windows cmd).
@REM Fetches Maven if missing, then runs it.  Trimmed version of official 3.3.2.
@REM ---------------------------------------------------------------------------
@echo off
setlocal

set BASE_DIR=%~dp0
set WRAPPER_JAR=%BASE_DIR%.mvn\wrapper\maven-wrapper.jar
set WRAPPER_PROPS=%BASE_DIR%.mvn\wrapper\maven-wrapper.properties

if not exist "%WRAPPER_JAR%" (
  for /f "tokens=1,2 delims==" %%A in ('findstr "^wrapperUrl" "%WRAPPER_PROPS%"') do set WRAPPER_URL=%%B
  echo Downloading Maven Wrapper from !WRAPPER_URL!
  if not exist "%BASE_DIR%.mvn\wrapper" mkdir "%BASE_DIR%.mvn\wrapper"
  where curl >nul 2>nul && (
    curl -fsSL -o "%WRAPPER_JAR%" "!WRAPPER_URL!"
  ) || (
    powershell -Command "Invoke-WebRequest -Uri '!WRAPPER_URL!' -OutFile '%WRAPPER_JAR%'"
  )
)

if not defined JAVA_HOME (
  where java >nul 2>nul || (
    echo Error: JAVA_HOME not set and 'java' not on PATH.
    exit /b 1
  )
  set JAVA_EXE=java
) else (
  set JAVA_EXE="%JAVA_HOME%\bin\java"
)

%JAVA_EXE% -classpath "%WRAPPER_JAR%" ^
  "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" ^
  org.apache.maven.wrapper.MavenWrapperMain %*
endlocal
