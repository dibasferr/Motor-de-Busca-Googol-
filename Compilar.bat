@echo off
echo ======================================
echo  Compilando código fonte Java...
echo ======================================

javac -encoding UTF-8 -cp ".;lib/jsoup-1.21.2.jar" -d target/classes -sourcepath src/main/java src/main/java/search/*.java
if %errorlevel% neq 0 (
    echo.
    echo ERRO: Falha na compilação! Verifique o código.
    pause
    exit /b
)

echo.
echo Compilação concluída com sucesso.

echo ======================================
echo  Gerando documentação Javadoc...
echo ======================================

REM Cria diretório docs se não existir
if not exist docs mkdir docs

javadoc -encoding UTF-8 -docencoding UTF-8 -charset UTF-8 -d docs -cp ".;lib/jsoup-1.21.2.jar" src/main/java/search/*.java
if %errorlevel% neq 0 (
    echo.
    echo  Javadoc gerado com avisos/erros, mas pasta docs foi criada.
) else (
    echo Documentação Javadoc gerada com sucesso na pasta /docs
)

echo.

"