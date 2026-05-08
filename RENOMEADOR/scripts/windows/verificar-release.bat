@echo off
setlocal
cd /d "%~dp0\..\.."

set "JAR=target\renomeador-nfse-0.1.0-SNAPSHOT.jar"

echo [1/4] Registrando arvore de dependencias...
call mvn dependency:tree -DoutputFile=target\dependency-tree.txt
if errorlevel 1 exit /b 1

echo [2/4] Rodando validacao completa...
call mvn verify -Pintegration
if errorlevel 1 exit /b 1

echo [3/4] Gerando JAR final...
call mvn package
if errorlevel 1 exit /b 1

echo [4/4] Testando JAR final...
java -jar "%JAR%" --help
if errorlevel 1 exit /b 1

echo Release verificado. Dependencias em target\dependency-tree.txt
