param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs = @("runClient")
)

$javaHome = "C:\Program Files\Eclipse Adoptium\jdk-8.0.472.8-hotspot"

if (-not (Test-Path $javaHome)) {
    Write-Error "Configured JAVA_HOME '$javaHome' was not found."
    exit 1
}

$gradleCmd = Join-Path -Path $PSScriptRoot -ChildPath "gradlew.bat"
if (-not (Test-Path $gradleCmd)) {
    Write-Error "Could not find gradlew at '$gradleCmd'."
    exit 1
}

$originalPath = $env:PATH
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$originalPath"

try {
    & $gradleCmd @GradleArgs
    $exitCode = $LASTEXITCODE
}
finally {
    $env:PATH = $originalPath
    Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
}

exit $exitCode

