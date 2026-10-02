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
$repoRoot = (Resolve-Path (Join-Path $probeRoot '..\..')).Path
$target = Join-Path $probeRoot 'target\magicdraw-sysml-v1-backend-probe'
$models = Join-Path $target 'models'
$diagramImages = Join-Path $target 'diagram-images'
$blockDiagramImages = Join-Path $target 'block-diagram-images'
$classes = Join-Path $target 'classes'
$source = Join-Path $probeRoot 'src\magicdraw\java\edu\gatech\mbsec\adapter\magicdraw\probe\MagicDrawSysmlV1BackendProbe.java'
$probeJar = Join-Path $target 'magicdraw-sysml-v1-backend-probe.jar'
$consoleLog = Join-Path $target 'console.log'
$dependencyClasspathFile = Join-Path $target 'maven-dependency-classpath.txt'
$sample = Join-Path $install 'samples\SysML v1\Introduction to SysML v1.mdzip'
$projectId = 'Introduction to SysML v1'
$scratchProject = Join-Path $models "$projectId.mdzip"
$moduleClasses = Join-Path $repoRoot 'edu.gatech.mbsec.adapter.magicdraw.repo-magicdraw-2026\target\classes'
$apiClasses = Join-Path $repoRoot 'edu.gatech.mbsec.adapter.magicdraw.repo-api\target\classes'
$resourceClasses = Join-Path $repoRoot 'edu.gatech.mbsec.adapter.magicdraw.resources\target\classes'
$productClasspath = "$install\lib\*;$install\lib\graphics\*;$install\plugins\com.nomagic.magicdraw.sysml\*;$install\plugins\com.nomagic.magicdraw.sysml\lib\*;$install\plugins\com.nomagic.requirements\lib\*"

New-Item -ItemType Directory -Path $target -Force | Out-Null

if (!(Test-Path -LiteralPath $java)) {
    throw "The product's bundled Java runtime was not found under $install"
}
if (!(Test-Path -LiteralPath $sample)) {
    throw "The installed SysML v1 sample was not found: $sample"
}

Push-Location $repoRoot
try {
    & mvn -B -Pmagicdraw-2026x-r1-community -pl :oslc4j-magicdraw-repo-magicdraw-2026 -am -DskipTests install
    if ($LASTEXITCODE -ne 0) {
        throw "The 2026 backend reactor install failed with exit code $LASTEXITCODE"
    }
    & mvn -B -f edu.gatech.mbsec.adapter.magicdraw.repo-magicdraw-2026\pom.xml -Pmagicdraw-2026x-r1-community dependency:build-classpath "-Dmdep.outputFile=$dependencyClasspathFile"
    if ($LASTEXITCODE -ne 0) {
        throw "Maven dependency classpath generation failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

New-Item -ItemType Directory -Path $models -Force | Out-Null
New-Item -ItemType Directory -Path $classes -Force | Out-Null
Copy-Item -LiteralPath $sample -Destination $scratchProject -Force
$dependencyClasspath = [System.IO.File]::ReadAllText($dependencyClasspathFile).Trim()
$compileClasspath = "$moduleClasses;$apiClasses;$resourceClasses;$dependencyClasspath;$productClasspath"
& $javac --release 21 -cp $compileClasspath -d $classes $source
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}
& $jarTool --create --file $probeJar -C $classes .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

$runtimeDependencies = ($dependencyClasspath -split ';' | Where-Object {
    $_ -and -not $_.StartsWith($install, [System.StringComparison]::OrdinalIgnoreCase)
}) -join ';'
$runtimeClasspath = "$probeJar;$moduleClasses;$apiClasses;$resourceClasses;$runtimeDependencies;$install\lib\classpath.jar"
Push-Location $install
try {
    $javaArgs = @(
        '-Xmx1200M',
        '-Xss1024K',
        '-cp',
        $runtimeClasspath,
        "-Dmd.plugins.dir=$install\plugins",
        "-Dsysml.magicdraw.modelsDirectory=$models",
        "-Dmagicdraw.sysml.diagramImageDirectory=$diagramImages",
        "-Dmagicdraw.sysml.blockDiagramImageDirectory=$blockDiagramImages",
        "-Dmagicdraw.sysml.mappingLog=$(Join-Path $target 'magicdraw-log')",
        '-Dfile.encoding=UTF-8',
        '@bin/vm.options',
        'edu.gatech.mbsec.adapter.magicdraw.probe.MagicDrawSysmlV1BackendProbe',
        "project=$scratchProject"
    )
    & $java @javaArgs 2>&1 | Tee-Object -FilePath $consoleLog
    if ($LASTEXITCODE -ne 0) {
        throw "The 2026 repository backend probe failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
