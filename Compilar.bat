echo =========================================
echo   Gerando Javadoc via Maven...
echo =========================================


mvn javadoc:javadoc

echo.
echo =========================================
echo   Processo concluido.
echo   Se nao houver erros, Javadoc esta em:
echo   target/site/apidocs/index.html
echo =========================================
echo.

pause
