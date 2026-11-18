Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ForEach-Object {
  $c = $_.CommandLine
  if ($null -eq $c) { $c = '' }
  $len = [Math]::Min(240, $c.Length)
  "$($_.ProcessId) :: " + $c.Substring(0, $len)
}