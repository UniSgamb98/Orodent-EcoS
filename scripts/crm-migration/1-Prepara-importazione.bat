@echo off
setlocal EnableExtensions
chcp 65001 >nul
set "PYTHONUTF8=1"
if exist "%~dp0output\migration.properties" del "%~dp0output\migration.properties"
if not exist "%~dp0input\note" mkdir "%~dp0input\note"
if not exist "%~dp0input\clients.txt" (
    echo Metti il backup CRM2 in "%~dp0input\clients.txt".
    echo Copia gli XML originali in "%~dp0input\note".
    pause
    exit /b 2
)
where py >nul 2>nul
if errorlevel 1 (
    where python >nul 2>nul
    if errorlevel 1 (
        echo Serve Python 3.10 o successivo, come per il vecchio generatore.
        pause
        exit /b 2
    )
    set "CRM_PYTHON=python"
) else (
    set "CRM_PYTHON=py -3"
)
if exist "%~dp0input\tutte_le_note.txt" (
    %CRM_PYTHON% "%~dp0generate_import_sql.py" --clients "%~dp0input\clients.txt" --notes "%~dp0input\tutte_le_note.txt" --output "%~dp0output"
) else (
    %CRM_PYTHON% "%~dp0generate_import_sql.py" --clients "%~dp0input\clients.txt" --notes-dir "%~dp0input\note" --output "%~dp0output"
)
set "CRM_RESULT=%ERRORLEVEL%"
if not "%CRM_RESULT%"=="0" (
    echo Preparazione interrotta. Correggi le anomalie indicate prima di importare.
) else (
    echo Controlla "%~dp0output\import_report.txt".
    echo Poi esegui 2-Importa-in-EcoS.bat sul PC HOST di EcoS.
)
pause
exit /b %CRM_RESULT%
