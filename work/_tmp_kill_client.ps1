Stop-Process -Id 42160 -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3
Stop-Process -Id 31152 -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2
Get-Process -Id 42160,31152 -ErrorAction SilentlyContinue | Select-Object Id,ProcessName
"done"
