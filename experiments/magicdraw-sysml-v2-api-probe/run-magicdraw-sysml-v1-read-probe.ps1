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
$probeRoot = $PSScriptRoot
$target = Join-Path $probeRoot 'target\magicdraw-sysml-v1-read-probe'
$classes = Join-Path $target 'classes'
$source = Join-Path $probeRoot 'src\magicdraw\java\edu\gatech\mbsec\adapter\magicdraw\probe\MagicDrawSysmlV1ModelReadProbe.java'
$probeJar = Join-Path $target 'magicdraw-sysml-v1-read-probe.jar'
$sample = Join-Path $install 'samples\SysML v1\Introduction to SysML v1.mdzip'
$scratchProject = Join-Path $target 'Introduction to SysML v1.mdzip'
$productClasspath = "$install\lib\*;$install\lib\graphics\*;$install\plugins\com.nomagic.magicdraw.sysml\*;$install\plugins\com.nomagic.requirements\lib\*"
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
    throw "The installed SysML v1 sample was not found: $sample"
}

New-Item -ItemType Directory -Path $classes -Force | Out-Null
Copy-Item -LiteralPath $sample -Destination $scratchProject -Force
& $javac --release 21 -cp $productClasspath -d $classes $source
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}
& $jarTool --create --file $probeJar -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

$logOffset = Get-ApplicationLogOffset
Push-Location $install
try {
    $javaArgs = @(
        '-Xmx1200M',
        '-Xss1024K',
        '-cp',
        $runtimeClasspath,
        "-Dmd.plugins.dir=$install\plugins",
        '-Dfile.encoding=UTF-8',
        '@bin/vm.options',
        'edu.gatech.mbsec.adapter.magicdraw.probe.MagicDrawSysmlV1ModelReadProbe',
        "project=$scratchProject"
    )
    $output = & $java @javaArgs 2>&1
    $exitCode = $LASTEXITCODE
    $output | ForEach-Object { Write-Output $_ }
    if ($exitCode -ne 0) {
        throw "SysML v1 .mdzip read probe failed with exit code $exitCode"
    }

    $applicationOutput = Get-ApplicationLogSince $logOffset
    if ($applicationOutput -notmatch 'SYSMLV1_MDZIP_READ_OK') {
        throw "The application log has no SysML v1 read success marker: $applicationLog"
    }
    $applicationOutput | Select-String 'SYSMLV1_MDZIP_READ_OK' | ForEach-Object { Write-Output $_.Line }
} finally {
    Pop-Location
}
