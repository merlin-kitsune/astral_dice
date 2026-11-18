$ErrorActionPreference = 'SilentlyContinue'
Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" | ForEach-Object {
  Write-Output ("PID=" + $_.ProcessId + " PPID=" + $_.ParentProcessId + " CREATED=" + $_.CreationDate.ToString('o'))
  Write-Output ("CMD=" + $_.CommandLine)
  Write-Output "-----"
}
