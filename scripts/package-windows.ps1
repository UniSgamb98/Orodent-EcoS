[CmdletBinding()]
param(
    [string]$JdkHome = $env:JAVA_HOME
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($env:OS -ne 'Windows_NT') {
    throw 'Questo script genera una distribuzione Windows e deve essere eseguito su Windows.'
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$appIcon = Join-Path $projectRoot 'packaging\windows\orodent-ecos.ico'
if (-not (Test-Path -LiteralPath $appIcon -PathType Leaf)) {
    throw "Icona dell'applicazione non trovata: $appIcon"
}
if ([string]::IsNullOrWhiteSpace($JdkHome)) {
    $jpackageCommand = Get-Command jpackage.exe -ErrorAction SilentlyContinue
    if ($null -eq $jpackageCommand) {
        throw 'JDK 21 non trovato. Passare -JdkHome con il percorso del JDK configurato in IntelliJ.'
    }
    $JdkHome = Split-Path (Split-Path $jpackageCommand.Source -Parent) -Parent
}
$JdkHome = (Resolve-Path $JdkHome).Path
foreach ($tool in @('java.exe', 'javac.exe', 'jlink.exe', 'jpackage.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $JdkHome "bin\$tool") -PathType Leaf)) {
        throw "Il JDK selezionato non contiene bin\$tool. Serve un JDK 21 o successivo completo, non solo un JRE."
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'jmods') -PathType Container)) {
    throw 'Il JDK selezionato non contiene jmods. Installare un JDK 21 o successivo completo.'
}
$javaExecutable = Join-Path $JdkHome 'bin\java.exe'
$javaVersion = (& $javaExecutable --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch '(?m)^(openjdk|java) (\d+)([.\s]|$)' -or [int]$Matches[2] -lt 21) {
    throw "Serve Java 21 o successivo. Il JDK selezionato riporta: $javaVersion"
}

$jdkReleaseFile = Join-Path $JdkHome 'release'
if (-not (Test-Path -LiteralPath $jdkReleaseFile -PathType Leaf) -or
    (Get-Content -LiteralPath $jdkReleaseFile -Raw) -notmatch '(?m)^OS_ARCH="(amd64|x86_64)"\r?$') {
    throw 'Il pacchetto e il catalogo aggiornamenti richiedono un JDK Windows x64.'
}

# Full JDK distributions may bundle a different JavaFX build and record its hashes
# in java.base. Application JavaFX must come exclusively from the Maven artifacts.
$jdkModules = (& $javaExecutable --list-modules | Out-String)
if ($LASTEXITCODE -ne 0) {
    throw 'Impossibile leggere i moduli del JDK selezionato.'
}
if ($jdkModules -match '(?m)^javafx\.') {
    throw @'
Il JDK selezionato include JavaFX (per esempio Liberica Full).
Usare un JDK 21 standard senza JavaFX incorporato, come Eclipse Temurin 21
o Liberica Standard 21, passando il suo percorso a -JdkHome.
I moduli JavaFX dell'applicazione sono gia forniti da Maven.
'@
}

function Invoke-CheckedTool {
    param([string]$FilePath, [string[]]$ToolArguments)
    & $FilePath @ToolArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Comando fallito (codice $LASTEXITCODE): $FilePath"
    }
}

$originalJavaHome = $env:JAVA_HOME
$originalPath = $env:PATH
Push-Location $projectRoot
try {
    $env:JAVA_HOME = $JdkHome
    $env:PATH = (Join-Path $JdkHome 'bin') + ';' + $originalPath
    Write-Host '1/3 - Build e test Maven. Chiudere prima eventuali copie avviate da target.'
    Invoke-CheckedTool -FilePath (Join-Path $projectRoot 'mvnw.cmd') -ToolArguments @('clean', 'package')

    $distribution = Join-Path $projectRoot 'orodent-launcher\target\distribution'
    if (-not (Test-Path -LiteralPath (Join-Path $distribution 'orodent-launcher.jar') -PathType Leaf)) {
        throw 'Il build non ha prodotto il JAR del launcher.'
    }
    $windowsNatives = @(Get-ChildItem -LiteralPath (Join-Path $distribution 'app\lib') -Filter 'javafx-graphics-*-win.jar' -File)
    if ($windowsNatives.Count -eq 0) {
        throw 'Dipendenze native JavaFX Windows non trovate. Controllare il build Maven e la piattaforma del JDK.'
    }
    $projectPom = [xml](Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml') -Raw)
    $appVersion = ([string]$projectPom.project.version -split '-', 2)[0]
    if ($appVersion -notmatch '^\d+(\.\d+){0,2}$') {
        throw "La versione $appVersion non e compatibile con jpackage Windows. Usare una versione numerica Maven."
    }
    $destination = Join-Path $projectRoot 'target\windows-portable'
    Write-Host '2/3 - Creazione EXE e runtime Java incorporato.'
    Invoke-CheckedTool -FilePath (Join-Path $JdkHome 'bin\jpackage.exe') -ToolArguments @(
        '--type', 'app-image',
        '--name', 'Orodent-EcoS',
        '--app-version', $appVersion,
        '--vendor', 'Orodent',
        '--description', 'Ecosistema applicativo Orodent',
        '--icon', $appIcon,
        '--input', $distribution,
        '--main-jar', 'orodent-launcher.jar',
        '--main-class', 'com.orodent.ecos.launcher.EcosLauncher',
        '--dest', $destination,
        '--add-modules', 'java.se,jdk.unsupported,jdk.crypto.ec,jdk.charsets,jdk.localedata,jdk.zipfs,jdk.jsobject',
        '--jlink-options', '--strip-debug --no-header-files --no-man-pages',
        '--java-options', '-Dorodent.ecos.packaged=true',
        '--java-options', '-Dorodent.ecos.config=$APPDIR/../config/application.properties'
    )
    $image = Join-Path $destination 'Orodent-EcoS'
    foreach ($relativeFile in @('Orodent-EcoS.exe', 'runtime\bin\java.exe', 'app\orodent-launcher.jar')) {
        if (-not (Test-Path -LiteralPath (Join-Path $image $relativeFile) -PathType Leaf)) {
            throw "Pacchetto incompleto: manca $relativeFile"
        }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $image 'app\app\lib') -PathType Container)) {
        throw 'Pacchetto incompleto: mancano le dipendenze applicative.'
    }
    # Keep the editable configuration beside the EXE, outside jpackage's app payload.
    Move-Item -LiteralPath (Join-Path $image 'app\config') -Destination (Join-Path $image 'config')
    if (-not (Test-Path -LiteralPath (Join-Path $image 'config\application.properties') -PathType Leaf)) {
        throw 'Pacchetto incompleto: manca config\application.properties.'
    }
    # Resolve the real application's module graph with the bundled runtime.
    # --dry-run loads the main class without starting JavaFX, discovery or the DB.
    $runtimeJava = Join-Path $image 'runtime\bin\java.exe'
    $runtimeModules = (& $runtimeJava --list-modules | Out-String)
    if ($LASTEXITCODE -ne 0 -or $runtimeModules -match '(?m)^javafx\.') {
        throw 'Il runtime incorporato non e valido o contiene JavaFX. Rigenerare con un JDK 21 standard.'
    }
    Write-Host 'Verifica del runtime incorporato e della compatibilita dei moduli JavaFX.'
    Invoke-CheckedTool -FilePath $runtimeJava -ToolArguments @(
        '--dry-run',
        '--module-path', (Join-Path $image 'app\app\lib'),
        '--module', 'com.orodent.ecos.app/com.orodent.ecos.app.MainApp'
    )
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'windows-portable-LEGGIMI.txt') -Destination (Join-Path $image 'LEGGIMI.txt')
    Write-Host '3/3 - Creazione ZIP distribuibile.'
    $zip = Join-Path $projectRoot 'target\Orodent-EcoS-windows-portable.zip'
    Compress-Archive -LiteralPath $image -DestinationPath $zip -CompressionLevel Optimal
    Write-Host "Pronto: $zip"
    Write-Host "Per provarlo: $image\Orodent-EcoS.exe"
    Write-Host 'Distribuire tutta la cartella o lo ZIP, non il solo EXE. Il database non e incluso.'
} finally {
    $env:JAVA_HOME = $originalJavaHome
    $env:PATH = $originalPath
    Pop-Location
}
