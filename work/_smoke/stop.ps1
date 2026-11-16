$ErrorActionPreference = 'Continue'
$targets = @(42472, 2408, 43528)
foreach ($p in $targets) {
  $proc = Get-Process -Id $p -ErrorAction SilentlyContinue
  if ($proc) { Write-Output ("STOPPING PID=" + $p + " NAME=" + $proc.ProcessName); Stop-Process -Id $p -Force -ErrorAction Continue }
  else { Write-Output ("ALREADY_GONE PID=" + $p) }
}
Start-Sleep -Seconds 8
Write-Output '--- after stop ---'
foreach ($p in $targets) {
  $proc = Get-Process -Id $p -ErrorAction SilentlyContinue
  if ($proc) { Write-Output ("STILL_ALIVE PID=" + $p + " NAME=" + $proc.ProcessName) } else { Write-Output ("GONE PID=" + $p) }
}
$d = Get-Process -Id 33152 -ErrorAction SilentlyContinue
if ($d) { Write-Output 'GRADLE_DAEMON_33152_ALIVE (untouched)' } else { Write-Output 'GRADLE_DAEMON_33152_GONE' }
