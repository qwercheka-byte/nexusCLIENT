@rem -------------------------------------------------------------------------------------------
@rem Gradle bootstrap script for Windows (replacement for the standard wrapper, no gradle-wrapper.jar).
@rem Downloads the Gradle distribution named in gradle\wrapper\gradle-wrapper.properties once,
@rem verifies its SHA-256 (distributionSha256Sum), unpacks it and runs it. Needs JDK 21 and PowerShell 5+.
@rem -------------------------------------------------------------------------------------------
@echo off
setlocal
set "APP_HOME=%~dp0"
set "GRADLE_HOME_DIR="
for /f "usebackq delims=" %%G in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%APP_HOME%gradle\wrapper\bootstrap.ps1"`) do set "GRADLE_HOME_DIR=%%G"
if not defined GRADLE_HOME_DIR (
    echo ERROR: could not prepare the Gradle distribution 1>&2
    exit /b 1
)
if not exist "%GRADLE_HOME_DIR%\bin\gradle.bat" (
    echo ERROR: %GRADLE_HOME_DIR%\bin\gradle.bat not found 1>&2
    exit /b 1
)
cd /d "%APP_HOME%"
call "%GRADLE_HOME_DIR%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
