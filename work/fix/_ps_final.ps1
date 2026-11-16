Remove-Item -LiteralPath "F:/MCProject/.agent-locks/dsh.lock" -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1
Write-Output ("locks: " + ((Get-ChildItem "F:/MCProject/.agent-locks" -ErrorAction SilentlyContinue | ForEach-Object { $_.Name }) -join ", "))
$l = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 25565,25585 }
if ($l -eq $null) { Write-Output "25565/25585 : no listener" } else { $l | ForEach-Object { Write-Output ("LISTEN " + $_.LocalPort) } }
Write-Output ("java pids: " + ((Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ForEach-Object { $_.ProcessId }) -join ","))
