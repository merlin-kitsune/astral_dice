$ErrorActionPreference = 'SilentlyContinue'
$c = Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -eq 25565 -or $_.LocalPort -eq 25585 }
if ($c) { $c | Select-Object LocalAddress, LocalPort, OwningProcess | Format-Table -AutoSize | Out-String -Width 200 } else { Write-Output 'NO_LISTEN_25565_25585' }
