@REM ----------------------------------------------------------------------------
@REM Maven Wrapper for Windows (Auto-bootstrapping)
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "WRAPPER_DIR=%~dp0.mvn\wrapper"
set "MAVEN_VERSION=3.9.6"
set "USER_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%"
set "MAVEN_HOME=%USER_HOME%\apache-maven-%MAVEN_VERSION%"

if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    goto RUN
)

echo [Smart-Dashboard] Bootstrapping Apache Maven %MAVEN_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$url = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip';" ^
    "$dest = Join-Path $env:TEMP 'maven-%MAVEN_VERSION%.zip';" ^
    "$targetDir = '%USER_HOME%';" ^
    "if (!(Test-Path $targetDir)) { New-Item -ItemType Directory -Force -Path $targetDir | Out-Null };" ^
    "Write-Host 'Downloading Maven distribution...';" ^
    "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;" ^
    "(New-Object System.Net.WebClient).DownloadFile($url, $dest);" ^
    "Write-Host 'Extracting archive...';" ^
    "Expand-Archive -Path $dest -DestinationPath $targetDir -Force;" ^
    "Remove-Item $dest -Force -ErrorAction SilentlyContinue;" ^
    "Write-Host 'Maven wrapper setup complete!'"

:RUN
"%MAVEN_HOME%\bin\mvn.cmd" %*
