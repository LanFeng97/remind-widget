# 一键构建 APK
# 依赖：E:\AndroidDevEnv 下的 JDK17 / Android SDK / Gradle 8.11.1
$ErrorActionPreference = 'Stop'

$root = "E:\AndroidDevEnv"
$env:JAVA_HOME        = "$root\jdk"
$env:ANDROID_HOME     = "$root\sdk"
$env:ANDROID_SDK_ROOT = "$root\sdk"
$env:GRADLE_USER_HOME = "$root\gradle-home"

$proj = Split-Path -Parent $PSScriptRoot
Set-Location $proj

$task = if ($args.Count -gt 0) { $args[0] } else { 'assembleDebug' }
Write-Host ">>> gradle $task" -ForegroundColor Cyan

& "$root\gradle\gradle-8.11.1\bin\gradle.bat" $task --console=plain
$code = $LASTEXITCODE

$apk = Join-Path $proj "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    Write-Host "APK: $apk  ($([math]::Round((Get-Item $apk).Length/1MB,1)) MB)" -ForegroundColor Green
}
exit $code
