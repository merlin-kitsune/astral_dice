Stop-Process -Id 21204 -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3
Stop-Process -Id 44120 -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2
"done"
