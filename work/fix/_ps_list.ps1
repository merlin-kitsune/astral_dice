Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ForEach-Object {
  $cl = $_.CommandLine
  if ($cl -eq $null) { $cl = "" }
  if ($cl.Length -gt 130) { $cl = $cl.Substring(0,130) }
  Write-Output ("{0} | {1} | {2}" -f $_.ProcessId, $_.CreationDate, $cl)
}
