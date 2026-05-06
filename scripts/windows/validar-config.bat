@echo off
setlocal

set "ROOT=%~dp0..\.."
set "JAR=%ROOT%\target\renomeador-nfse-0.1.0-SNAPSHOT.jar"

if "%~1"=="" (
  echo Uso: validar-config.bat C:\caminho\empresas.yaml
  exit /b 1
)

java -jar "%JAR%" config check --config "%~1"
