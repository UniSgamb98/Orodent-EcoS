@echo off
rem Shared setup for both the repository and the portable EcoS distribution.
rem ECOS_HOME, ECOS_CONFIG, ECOS_LIB and ECOS_JAVA can be set explicitly by the caller.
if not defined ECOS_HOME for %%I in ("%~dp0..\..") do set "ECOS_HOME=%%~fI"
if not defined ECOS_LIB set "ECOS_LIB=%ECOS_HOME%\app\lib"
if not exist "%ECOS_LIB%\orodent-crm-*.jar" set "ECOS_LIB=%ECOS_HOME%\orodent-launcher\target\distribution\app\lib"
if not exist "%ECOS_LIB%\orodent-crm-*.jar" (
    echo Dipendenze EcoS non trovate. Aggiorna la distribuzione o esegui mvnw.cmd package dal progetto.
    exit /b 2
)
if not defined ECOS_CONFIG set "ECOS_CONFIG=%ECOS_HOME%\config\application.properties"
if not exist "%ECOS_CONFIG%" if exist "%ECOS_HOME%\..\config\application.properties" set "ECOS_CONFIG=%ECOS_HOME%\..\config\application.properties"
if not exist "%ECOS_CONFIG%" if exist "%ECOS_HOME%\orodent-launcher\target\distribution\config\application.properties" set "ECOS_CONFIG=%ECOS_HOME%\orodent-launcher\target\distribution\config\application.properties"
if not exist "%ECOS_CONFIG%" (
    echo Configurazione non trovata. Imposta ECOS_CONFIG con il file application.properties usato dall'HOST.
    exit /b 2
)
if not defined ECOS_JAVA if exist "%ECOS_HOME%\runtime\bin\java.exe" set "ECOS_JAVA=%ECOS_HOME%\runtime\bin\java.exe"
if not defined ECOS_JAVA if exist "%ECOS_HOME%\..\runtime\bin\java.exe" set "ECOS_JAVA=%ECOS_HOME%\..\runtime\bin\java.exe"
if not defined ECOS_JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "ECOS_JAVA=%JAVA_HOME%\bin\java.exe"
if not defined ECOS_JAVA set "ECOS_JAVA=java"
exit /b 0
