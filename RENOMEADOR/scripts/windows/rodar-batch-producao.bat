@echo off
setlocal

set "ROOT=%~dp0..\.."
set "PROJECT_ROOT=%ROOT%\.."
set "JAR=%ROOT%\target\renomeador-nfse-0.1.0-SNAPSHOT.jar"
set "PLANILHA=%PROJECT_ROOT%\PLANILHA_FISCAL.xlsm"
set "CONFIG=%ROOT%\operacao\empresas.yaml"
set "HOMOLOGACAO="

if /I "%~1"=="--homologacao" (
  set "HOMOLOGACAO=--homologacao"
)

if not exist "%JAR%" (
  echo ERRO: JAR nao encontrado: "%JAR%"
  echo Rode primeiro: scripts\windows\compilar.bat
  exit /b 2
)

if not exist "%PLANILHA%" (
  echo ERRO: planilha compartilhada nao encontrada: "%PLANILHA%"
  exit /b 3
)

if not exist "%ROOT%\operacao" (
  mkdir "%ROOT%\operacao"
)

java -jar "%JAR%" config import-excel --planilha "%PLANILHA%" --saida "%CONFIG%" --sobrescrever
if errorlevel 1 exit /b %errorlevel%

java -jar "%JAR%" config check --config "%CONFIG%"
if errorlevel 1 exit /b %errorlevel%

if "%HOMOLOGACAO%"=="" (
  java -jar "%JAR%" batch --config "%CONFIG%"
) else (
  java -jar "%JAR%" batch --config "%CONFIG%" --homologacao
)
