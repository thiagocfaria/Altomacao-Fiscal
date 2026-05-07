@echo off
setlocal

set "ROOT=%~dp0..\.."
set "JAR=%ROOT%\target\renomeador-nfse-0.1.0-SNAPSHOT.jar"

where mvn >nul 2>nul
if errorlevel 1 (
  echo ERRO: Maven nao encontrado no PATH.
  echo Configure o Maven 3.9+ ou use a instalacao portatil documentada no README.
  exit /b 2
)

cd /d "%ROOT%"
mvn clean package
if errorlevel 1 exit /b %errorlevel%

if not exist "%JAR%" (
  echo ERRO: build terminou, mas o JAR nao foi encontrado: "%JAR%"
  exit /b 3
)

echo JAR gerado: "%JAR%"
