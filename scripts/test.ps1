param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $JdkHome) { throw 'Indica -JdkHome o configura JAVA_HOME con un JDK 26.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    New-Item -ItemType Directory -Force target/broker-test | Out-Null
    $sources = @((Get-ChildItem src/main/java/Software/ulpgc/*.java).FullName)
    $sources += 'src/test/java/Software/ulpgc/AttendanceBrokerTest.java'
    & (Join-Path $JdkHome 'bin/javac.exe') -encoding UTF-8 -d target/broker-test @sources
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron compilar las pruebas.' }
    & (Join-Path $JdkHome 'bin/java.exe') -cp target/broker-test Software.ulpgc.AttendanceBrokerTest
    if ($LASTEXITCODE -ne 0) { throw 'Han fallado las pruebas.' }
} finally { Pop-Location }
