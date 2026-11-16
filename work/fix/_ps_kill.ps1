foreach ($p in 27680, 33100) {
  $proc = Get-Process -Id $p -ErrorAction SilentlyContinue
  if ($proc -eq $null) { Write-Output "PID $p : already gone"; continue }
  Write-Output ("PID $p : {0} started {1}" -f $proc.ProcessName, $proc.StartTime)
  Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 4
Write-Output "--- remaining ---"
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ForEach-Object { Write-Output ("{0} | {1}" -f $_.ProcessId, $_.CreationDate) }
Write-Output "--- ports ---"
$l = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 25565,25585 }
if ($l -eq $null) { Write-Output "25565/25585 : no listener" } else { $l | ForEach-Object { Write-Output ("LISTEN " + $_.LocalPort + " pid=" + $_.OwningProcess) } }
