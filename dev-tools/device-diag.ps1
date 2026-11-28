# 真机诊断：设备一上线就把小组件相关的所有状态抓下来
# 用法：.\device-diag.ps1 [-Serial xxxx]
# 输出：版本 / manifest 里的 provider 声明 / 系统注册情况 / 桌面加载情况 / 桌面包名
$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$adb = "E:\AndroidDevEnv\sdk\platform-tools\adb.exe"
$serial = $null
for ($i = 0; $i -lt $args.Count; $i++) {
    if ($args[$i] -eq '-Serial') { $serial = [string]$args[$i + 1]; $i++ }
}

if (-not $serial) {
    $lines = & $adb devices 2>&1
    $real = $lines | Where-Object { $_ -match '\sdevice\s*$' -and $_ -notmatch 'emulator' }
    if (-not $real) { Write-Host "没有连接真机" -ForegroundColor Red; & $adb devices; exit 1 }
    $serial = ($real | Select-Object -First 1) -split '\s+' | Select-Object -First 1
}

$A = @('-s', $serial)
function Sh($cmd) { & $adb @A shell $cmd 2>&1 }

Write-Host "================ 设备 ================" -ForegroundColor Cyan
Write-Host ("序列号      : " + $serial)
Write-Host ("型号        : " + (Sh 'getprop ro.product.model'))
Write-Host ("品牌        : " + (Sh 'getprop ro.product.brand'))
Write-Host ("Android     : " + (Sh 'getprop ro.build.version.release') + "  SDK " + (Sh 'getprop ro.build.version.sdk'))
Write-Host ("HyperOS     : " + (Sh 'getprop ro.mi.os.version.name') + "  " + (Sh 'getprop ro.mi.os.version.incremental'))
Write-Host ("桌面包名    : " + (Sh 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | Select-Object -Last 1'))

Write-Host "`n================ 已安装的提醒 App ================" -ForegroundColor Cyan
$pkg = Sh 'dumpsys package com.remind.widget | grep -E "versionName|versionCode|firstInstallTime|lastUpdateTime"'
if ($pkg) { $pkg } else { Write-Host "未安装 com.remind.widget" -ForegroundColor Red }

Write-Host "`n================ Manifest 里的 provider 声明 ================" -ForegroundColor Cyan
$recv = Sh 'dumpsys package com.remind.widget | grep -A3 "ReminderWidgetProvider"'
if ($recv) { $recv } else { Write-Host "没找到 ReminderWidgetProvider（manifest 可能有问题）" -ForegroundColor Red }

Write-Host "`n================ 系统是否注册了 provider ================" -ForegroundColor Cyan
$prov = Sh 'dumpsys appwidget | grep -i "remind.widget"'
if ($prov) { $prov } else { Write-Host "系统里没有本应用的 provider 记录！" -ForegroundColor Red }

Write-Host "`n================ 桌面是否加载了 provider ================" -ForegroundColor Cyan
$bound = Sh 'dumpsys appwidget | grep -i -A2 "boundWidgets\|Bound widget"'
if ($bound) { $bound } else { Write-Host "没有已绑定的组件实例" }

Write-Host "`n================ 组件尺寸声明 ================" -ForegroundColor Cyan
Sh 'dumpsys appwidget | grep -i -A6 "com.remind.widget" | grep -E "min=|span|targetCell"'

Write-Host "`n================ 桌面缓存的小部件列表（关键）================" -ForegroundColor Cyan
Write-Host "Launcher 会缓存 provider 列表，有时需要清缓存或重启桌面才会刷新" -ForegroundColor Yellow
Sh 'dumpsys activity service com.miui.home 2>/dev/null | head -5'

Write-Host "`n================ 最近与小组件相关的报错 ================" -ForegroundColor Cyan
$err = Sh 'logcat -d -t 400 | grep -iE "appwidget|remind.widget|InflateException|not allowed"'
if ($err) { $err | Select-Object -Last 30 } else { Write-Host "（无相关日志）" }

Write-Host "`n完成。如果第 3 段（系统是否注册）为空，问题在安装包；" -ForegroundColor Green
Write-Host "如果第 3 段有记录但桌面列表里看不到，问题在桌面侧的过滤/缓存。" -ForegroundColor Green
