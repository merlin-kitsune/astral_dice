$ErrorActionPreference = 'SilentlyContinue'
$cut = [datetime]::Parse('2026-11-02T18:35:45')
Get-CimInstance Win32_Process | Where-Object { $_.CreationDate -gt $cut } | Sort-Object CreationDate | ForEach-Object {
  $c = ''
  if ($_.CommandLine) { $c = $_.CommandLine.Substring(0, [Math]::Min(200, $_.CommandLine.Length)) }
  Write-Output ("PID=" + $_.ProcessId + " PPID=" + $_.ParentProcessId + " NAME=" + $_.Name + " CREATED=" + $_.CreationDate.ToString('HH:mm:ss') + " CMD=" + $c)
}
