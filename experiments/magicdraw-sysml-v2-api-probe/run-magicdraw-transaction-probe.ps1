param(
    [Parameter(Mandatory = $false)]
    [string] $MagicDrawHome = $env:MAGICDRAW_HOME
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($MagicDrawHome)) {
    $MagicDrawHome = 'C:\Program Files\Magic Systems of Systems Architect'
}
$install = (Resolve-Path -LiteralPath $MagicDrawHome).Path
$java = Join-Path $install 'jre\bin\java.exe'
$javac = (Get-Command javac -ErrorAction Stop).Source
$jarTool = (Get-Command jar -ErrorAction Stop).Source
$sysml2 = Join-Path $install 'plugins\com.nomagic.magicdraw.sysml2'
$probeRoot = $PSScriptRoot
$target = Join-Path $probeRoot 'target\magicdraw-transaction-probe'
$classes = Join-Path $target 'classes'
$source = Join-Path $probeRoot 'src\magicdraw\java\edu\gatech\mbsec\adapter\magicdraw\probe\MagicDrawProjectTransactionProbe.java'
$sysmlSource = Join-Path $probeRoot 'src\magicdraw\java\edu\gatech\mbsec\adapter\magicdraw\probe\MagicDrawSysmlV2RuntimeProbe.java'
$probeJar = Join-Path $target 'magicdraw-transaction-probe.jar'
$sample = Join-Path $install 'samples\diagrams\class diagram.mdzip'
$scratchProject = Join-Path $target 'class-diagram-transaction-probe.mdzip'
$productClasspath = "$install\lib\*;$install\lib\graphics\*;$sysml2\*;$sysml2\lib\*"
$runtimeClasspath = "$probeJar;$install\lib\classpath.jar"
$applicationLog = Join-Path $env:LOCALAPPDATA '.magic.systems.of.systems.architect\2026x\msosa.log'

function Get-ApplicationLogOffset {
    if (Test-Path -LiteralPath $applicationLog) {
        return ([System.IO.File]::ReadAllText($applicationLog)).Length
    }
    return 0
}

function Get-ApplicationLogSince([long] $Offset) {
    if (!(Test-Path -LiteralPath $applicationLog)) {
        return ''
    }
    $contents = [System.IO.File]::ReadAllText($applicationLog)
    if ($Offset -gt $contents.Length) {
        return $contents
    }
    return $contents.Substring([int] $Offset)
}

if (!(Test-Path -LiteralPath $java)) {
    throw "The product's bundled Java runtime was not found under $install"
}
if (!(Test-Path -LiteralPath $sample)) {
    throw "The bundled sample project was not found: $sample"
}

New-Item -ItemType Directory -Path $classes -Force | Out-Null
& $javac --release 21 -cp $productClasspath -d $classes $source $sysmlSource
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}
& $jarTool --create --file $probeJar -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

Copy-Item -LiteralPath $sample -Destination $scratchProject -Force
$writeLogOffset = Get-ApplicationLogOffset

Push-Location $install
try {
    $writeOutput = & $java -Xmx1200M -Xss1024K -cp $runtimeClasspath `
        "-Dmd.plugins.dir=$install\plugins" `
        "-Desi.system.config=$install\data\application.conf" `
        '-Dfile.encoding=UTF-8' '@bin/vm.options' `
        'edu.gatech.mbsec.adapter.magicdraw.probe.MagicDrawProjectTransactionProbe' `
        "project=$scratchProject" 2>&1
    $writeExit = $LASTEXITCODE
    $writeOutput | ForEach-Object { Write-Output $_ }
    if ($writeExit -ne 0) {
        throw "MagicDraw transaction write probe failed with exit code $writeExit"
    }

    $writeLog = Get-ApplicationLogSince $writeLogOffset
    if ($writeLog -notmatch 'TRANSACTION_WRITE_OK') {
        throw "MagicDraw application log has no transaction success marker: $applicationLog"
    }

    if ($writeLog -match 'TRANSACTION_FILE_SAVE_OK') {
        $verifyLogOffset = Get-ApplicationLogOffset
        & $java -Xmx1200M -Xss1024K -cp $runtimeClasspath `
            "-Dmd.plugins.dir=$install\plugins" `
            "-Desi.system.config=$install\data\application.conf" `
            '-Dfile.encoding=UTF-8' '@bin/vm.options' `
            'edu.gatech.mbsec.adapter.magicdraw.probe.MagicDrawProjectTransactionProbe' `
            "project=$scratchProject" 'probe.mode=verify'
        $verifyExit = $LASTEXITCODE
        if ($verifyExit -ne 0) {
            throw "MagicDraw transaction reopen probe failed with exit code $verifyExit"
        }
        $verifyLog = Get-ApplicationLogSince $verifyLogOffset
        if ($verifyLog -notmatch 'TRANSACTION_REOPEN_OK') {
            throw "MagicDraw application log has no transaction reopen success marker: $applicationLog"
        }
    } else {
        if ($writeLog -notmatch 'TRANSACTION_FILE_SAVE_SKIPPED_READ_ONLY') {
            throw "MagicDraw transaction probe did not report save success or read-only status: $applicationLog"
        }
        Write-Output 'UML sample copy is read-only; it was not saved. The native SysML v2 project helper performs the persisted transaction and reopen verification.'
    }
} finally {
    Pop-Location
}
