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
  echo Uso: rodar-batch-homologacao.bat C:\caminho\empresas.yaml [C:\caminho\PLANILHA_FISCAL.xlsm]
  exit /b 1
)

if "%~2"=="" (
  java -jar "%JAR%" batch --config "%~1" --homologacao
) else (
  java -jar "%JAR%" batch --config "%~1" --homologacao --planilha "%~2"
)
