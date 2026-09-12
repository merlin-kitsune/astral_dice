<#
Mt.Conf.psm1 — mt.conf 的解析（唯一实现）。

对应源文件：scripts/test/lib/paths.sh 的 mt_load_conf() 与 scripts/test/lib/mt_paths.py 的
_load_conf()。两侧语义本就一致，本模块把「同一份 KEY=VALUE 文件被两种语言解析」收敛为一种：

  · 逐行读取；跳过空行与 `#` 开头的注释行；不含 `=` 的行忽略；
  · 以**第一个** `=` 切分 key / value；
  · key、value 各自 strip 空白；
  · value 再依次剥掉外层 `"` 与 `'`（与 python 的 `.strip('"').strip("'")` 同序）。

⚠️ 本文件缺失不是错误：调用方（Mt.Paths / mt_paths）会回落到内置默认值。
#>

function Get-MtConf {
    <#
    .SYNOPSIS
        解析一份 mt.conf，返回 hashtable（键名原样保留）。
    .PARAMETER Path
        配置文件路径；不存在或读取失败时返回空 hashtable（不抛异常）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    $conf = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $conf
    }

    try {
        $lines = [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)
    } catch {
        return $conf
    }

    foreach ($raw in $lines) {
        $line = $raw.Trim()
        if (-not $line) { continue }
        if ($line.StartsWith('#')) { continue }
        if (-not $line.Contains('=')) { continue }

        $idx = $line.IndexOf('=')
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        # 与 python 的 .strip('"').strip("'") 同序：先剥双引号，再剥单引号
        $val = $val.Trim('"').Trim("'")

        if ($key) { $conf[$key] = $val }
    }

    return $conf
}

Export-ModuleMember -Function Get-MtConf
