# 安装到已连接的设备/模拟器并启动
$ErrorActionPreference = 'Stop'

$root = "E:\AndroidDevEnv"
$adb  = "$root\sdk\platform-tools\adb.exe"
$proj = Split-Path -Parent $PSScriptRoot
$apk  = Join-Path $proj "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apk)) { throw "APK 不存在，先跑 build.ps1" }

Write-Host ">>> 设备列表" -ForegroundColor Cyan
& $adb devices

Write-Host ">>> 安装" -ForegroundColor Cyan
& $adb install -r -d $apk

Write-Host ">>> 启动" -ForegroundColor Cyan
& $adb shell am start -n com.remind.widget/.MainActivity

# 可选：授予通知权限，省得每次点弹窗
if ($args -contains '-grant') {
    & $adb shell pm grant com.remind.widget android.permission.POST_NOTIFICATIONS
    Write-Host "已授予通知权限"
}
