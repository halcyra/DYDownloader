Param(
    [string[]]$Tasks = @("compileDebugJavaWithJavac", "lintDebug", "testDebugUnitTest")
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$env:GRADLE_USER_HOME = Join-Path $RepoRoot ".gradle-user-home"
$env:ANDROID_USER_HOME = Join-Path $RepoRoot ".android-user-home"

# Route AGP metrics/home lookups to a writable project-local directory to avoid noisy warnings.
$env:JAVA_TOOL_OPTIONS = "-Duser.home=$($env:ANDROID_USER_HOME)"

if (!(Test-Path $env:GRADLE_USER_HOME)) {
    New-Item -ItemType Directory -Path $env:GRADLE_USER_HOME | Out-Null
}
if (!(Test-Path $env:ANDROID_USER_HOME)) {
    New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME | Out-Null
}

$taskArgs = $Tasks -join " "
$output = cmd /c "`"$RepoRoot\\gradlew.bat`" $taskArgs 2>&1"
$exitCode = $LASTEXITCODE

$output |
    Where-Object { $_ -notmatch '^Picked up JAVA_TOOL_OPTIONS:' } |
    ForEach-Object { $_ }

if ($exitCode -ne 0) {
    exit $exitCode
}
