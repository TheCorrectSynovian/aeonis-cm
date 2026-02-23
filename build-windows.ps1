param(
    [string] $JdkHome = "",
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs = @("test")
)

$ErrorActionPreference = "Stop"

function Test-Jdk21([string] $jdkHome) {
    if (-not $jdkHome) { return $false }
    $javaExe = Join-Path $jdkHome "bin\java.exe"
    if (-not (Test-Path $javaExe)) { return $false }
    $oldPref = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $versionOutput = & $javaExe -version 2>&1
    } finally {
        $ErrorActionPreference = $oldPref
    }
    return ($versionOutput | Select-String -SimpleMatch 'version "21' -Quiet)
}

function Find-Jdk21 {
    if (Test-Jdk21 $JdkHome) { return $JdkHome }
    if (Test-Jdk21 $env:AEONIS_JAVA_HOME) { return $env:AEONIS_JAVA_HOME }
    if (Test-Jdk21 $env:JAVA_HOME) { return $env:JAVA_HOME }

    $candidates = @(
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Java\jdk-21*",
        "C:\Program Files\Microsoft\jdk-21*",
        "C:\Program Files\Zulu\zulu-21*",
        "C:\Program Files\Azul\Zulu\zulu-21*"
    )

    foreach ($pattern in $candidates) {
        $matches = Get-ChildItem $pattern -Directory -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending
        foreach ($dir in $matches) {
            if (Test-Jdk21 $dir.FullName) { return $dir.FullName }
        }
    }

    return $null
}

$jdkHome = Find-Jdk21
if (-not $jdkHome) {
    Write-Host "JDK 21 not found. Install it or set AEONIS_JAVA_HOME or JAVA_HOME to a JDK 21 path." -ForegroundColor Red
    Write-Host "You can also run: .\\build-windows.ps1 -JdkHome \"C:\\Path\\To\\JDK21\" test" -ForegroundColor Yellow
    exit 1
}

$env:JAVA_HOME = $jdkHome
$env:PATH = (Join-Path $jdkHome "bin") + ";" + $env:PATH
$env:ORG_GRADLE_JAVA_HOME = $jdkHome

Write-Host "Using JDK 21 at: $jdkHome"

& .\gradlew.bat "-Dorg.gradle.java.home=$jdkHome" @GradleArgs
