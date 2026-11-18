$ErrorActionPreference = 'SilentlyContinue'
Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" |
  Select-Object ProcessId, ParentProcessId, @{n='CreationDate';e={$_.CreationDate.ToString('o')}}, @{n='Cmd';e={ if ($_.CommandLine) { $_.CommandLine.Substring(0, [Math]::Min(240, $_.CommandLine.Length)) } else { '' } }} |
  Format-List | Out-String -Width 400
