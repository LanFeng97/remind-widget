# UI automation helper for the emulator.
# NOTE: keep this file ASCII-only. Windows PowerShell reads BOM-less UTF-8 as GBK,
#       and Chinese comments here were enough to break the parser.
#
# Usage:
#   .\ui.ps1 -find "TEXT"    print center coordinates of the matched node
#   .\ui.ps1 -tap  "TEXT"    tap the matched node
#   .\ui.ps1 -list           list all nodes that carry text/desc
# adb 会把进度信息写到 stderr，Stop 策略会把它当致命错误，这里用 Continue
$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$findStr = $null
$tapStr  = $null
$listAll = $false
for ($i = 0; $i -lt $args.Count; $i++) {
    switch ($args[$i]) {
        '-find' { $findStr = [string]$args[$i + 1]; $i++ }
        '-tap'  { $tapStr  = [string]$args[$i + 1]; $i++ }
        '-list' { $listAll = $true }
    }
}

$sdk = "E:\AndroidDevEnv"
$adb = "$sdk\sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "$sdk\platform-tools\adb.exe" }

& $adb shell uiautomator dump /sdcard/_ui.xml 2>&1 | Out-Null
$tmp = Join-Path $env:TEMP "_ui.xml"
& $adb pull /sdcard/_ui.xml $tmp 2>&1 | Out-Null
if (-not (Test-Path $tmp)) { throw "uiautomator dump failed" }

[xml]$x = Get-Content $tmp -Raw -Encoding UTF8
$nodes = New-Object System.Collections.ArrayList

function Walk($n) {
    if ($n -isnot [System.Xml.XmlElement]) { return }
    $t = $n.text
    $d = $n.'content-desc'
    $b = $n.bounds
    if (($t -or $d) -and $b) {
        $m = [regex]::Match($b, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
        if ($m.Success) {
            $x1 = [int]$m.Groups[1].Value; $y1 = [int]$m.Groups[2].Value
            $x2 = [int]$m.Groups[3].Value; $y2 = [int]$m.Groups[4].Value
            [void]$nodes.Add([pscustomobject]@{
                text = $t; desc = $d
                cx = [int](($x1 + $x2) / 2); cy = [int](($y1 + $y2) / 2)
                w = $x2 - $x1; h = $y2 - $y1
            })
        }
    }
    foreach ($c in $n.ChildNodes) { Walk $c }
}
Walk $x.hierarchy

if ($listAll) {
    $nodes | Where-Object { $_.w -gt 0 } | ForEach-Object {
        "{0,4},{1,-5} w={2,-4} text='{3}' desc='{4}'" -f $_.cx, $_.cy, $_.w, $_.text, $_.desc
    }
    return
}

$key = if ($findStr) { $findStr } else { $tapStr }
if (-not $key) { throw "need -find / -tap / -list" }

$hit = $nodes | Where-Object {
    ($_.text -and $_.text -like "*$key*") -or ($_.desc -and $_.desc -like "*$key*")
} | Sort-Object w -Descending | Select-Object -First 1

if (-not $hit) {
    Write-Host "NOT FOUND: $key" -ForegroundColor Yellow
    Write-Host "Visible nodes:" -ForegroundColor Yellow
    $nodes | Where-Object { $_.w -gt 0 } | Select-Object -First 30 | ForEach-Object {
        "  {0},{1}  '{2}' '{3}'" -f $_.cx, $_.cy, $_.text, $_.desc
    }
    exit 1
}

if ($tapStr) {
    Write-Host ("tap '" + $hit.text + $hit.desc + "' @ " + $hit.cx + "," + $hit.cy) -ForegroundColor Cyan
    & $adb shell input tap $hit.cx $hit.cy | Out-Null
} else {
    Write-Output ("{0},{1}" -f $hit.cx, $hit.cy)
}
