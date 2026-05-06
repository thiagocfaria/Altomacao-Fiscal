@echo off
setlocal

set "ROOT=%~dp0..\.."
set "JAR=%ROOT%\target\renomeador-nfse-0.1.0-SNAPSHOT.jar"

if "%~1"=="" (
  echo Uso: rodar-watch.bat C:\caminho\empresas.yaml
  exit /b 1
)

java -jar "%JAR%" watch --config "%~1"
