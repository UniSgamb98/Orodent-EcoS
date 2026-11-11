@echo off
setlocal EnableExtensions
chcp 65001 >nul
call "%~dp0ecos-env.bat"
if errorlevel 1 (
    pause
    exit /b 2
)
echo Avvia EcoS aggiornato sul PC HOST e lascia il launcher nella Home.
echo Durante l'importazione nessuno deve modificare il CRM.
echo Configurazione: "%ECOS_CONFIG%"
echo.
"%ECOS_JAVA%" -cp "%ECOS_LIB%\*" com.orodent.ecos.crm.migration.LegacyCrmImport --dir "%~dp0output" --config "%ECOS_CONFIG%"
if errorlevel 1 (
    echo Verifica non superata. Nessuna importazione avviata.
    pause
    exit /b 2
)
echo.
choice /C SN /N /M "Confermi l'importazione nel CRM vuoto di EcoS, con backup preventivo? [S/N] "
if errorlevel 2 exit /b 0
"%ECOS_JAVA%" -cp "%ECOS_LIB%\*" com.orodent.ecos.crm.migration.LegacyCrmImport --dir "%~dp0output" --config "%ECOS_CONFIG%" --apply
set "CRM_RESULT=%ERRORLEVEL%"
echo.
if "%CRM_RESULT%"=="0" echo In EcoS premi Ricarica pagina o riapri il CRM per vedere i dati importati.
pause
exit /b %CRM_RESULT%
