Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ForEach-Object {
  $cl = $_.CommandLine
  if ($null -eq $cl) { $cl = '' }
  $short = $cl.Substring(0, [Math]::Min(200, $cl.Length))
  "$($_.ProcessId) | $($_.CreationDate) | $short"
}
