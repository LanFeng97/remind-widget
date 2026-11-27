# 截一张当前屏幕到 dev-tools\shots\，方便逐轮对比界面变化
# 注意：不能直接用 `adb exec-out screencap -p` 接 PowerShell，
# 二进制会被当成字符串数组破坏掉；走设备内落盘 + pull 最稳。
$ErrorActionPreference = 'Stop'

$root = "E:\AndroidDevEnv"
$adb  = "$root\sdk\platform-tools\adb.exe"
$out  = Join-Path $PSScriptRoot "shots"
New-Item -ItemType Directory -Force $out | Out-Null

$name = if ($args.Count -gt 0) { $args[0] } else { "shot-" + (Get-Date -Format 'HHmmss') }
$file = Join-Path $out "$name.png"
$remote = "/sdcard/_dsh_shot.png"

& $adb shell screencap -p $remote | Out-Null
& $adb pull $remote $file 2>&1 | Out-Null
& $adb shell rm -f $remote | Out-Null

if (Test-Path $file) {
    Write-Host "saved: $file  ($([math]::Round((Get-Item $file).Length/1KB)) KB)" -ForegroundColor Green
} else {
    Write-Host "截图失败" -ForegroundColor Red
}
