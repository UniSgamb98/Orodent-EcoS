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
        throw "Il JDK selezionato non contiene bin\$tool. Serve un JDK 21 completo, non solo un JRE."
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'jmods') -PathType Container)) {
    throw 'Il JDK selezionato non contiene jmods. Installare un JDK 21 completo.'
}
$javaExecutable = Join-Path $JdkHome 'bin\java.exe'
$javaVersion = (& $javaExecutable --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch '(?m)^(openjdk|java) 21([.\s]|$)') {
    throw "Serve Java 21. Il JDK selezionato riporta: $javaVersion"
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
        '--input', $distribution,
        '--main-jar', 'orodent-launcher.jar',
        '--main-class', 'com.orodent.ecos.launcher.EcosLauncher',
        '--dest', $destination,
        '--add-modules', 'java.se,jdk.unsupported,jdk.crypto.ec,jdk.charsets,jdk.localedata,jdk.zipfs',
        '--jlink-options', '--strip-debug --no-header-files --no-man-pages',
        '--java-options', '-Dorodent.ecos.packaged=true'
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
