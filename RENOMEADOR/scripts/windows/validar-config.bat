@echo off
setlocal

set "ROOT=%~dp0..\.."
set "JAR=%ROOT%\target\renomeador-nfse-0.1.0-SNAPSHOT.jar"

if not exist "%JAR%" (
  echo ERRO: JAR nao encontrado: "%JAR%"
  echo Rode primeiro: scripts\windows\compilar.bat
  exit /b 2
)

if "%~1"=="" (
  echo Uso: validar-config.bat C:\caminho\empresas.yaml
  exit /b 1
)

java -jar "%JAR%" config check --config "%~1"
