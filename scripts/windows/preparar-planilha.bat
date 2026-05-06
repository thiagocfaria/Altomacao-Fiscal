@echo off
setlocal

set "ROOT=%~dp0..\.."
set "JAR=%ROOT%\target\renomeador-nfse-0.1.0-SNAPSHOT.jar"

if "%~1"=="" (
  echo Uso: preparar-planilha.bat C:\caminho\PLANILHA_FISCAL_ORIGINAL.xlsm C:\caminho\PLANILHA_FISCAL_MODELO.xlsm
  exit /b 1
)

if "%~2"=="" (
  echo Uso: preparar-planilha.bat C:\caminho\PLANILHA_FISCAL_ORIGINAL.xlsm C:\caminho\PLANILHA_FISCAL_MODELO.xlsm
  exit /b 1
)

java -jar "%JAR%" config preparar-planilha --entrada "%~1" --saida "%~2"
