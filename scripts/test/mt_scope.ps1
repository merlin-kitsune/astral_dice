#Requires -Version 7.0
<#
.SYNOPSIS
    mt_scope — 变更分级自查（非核心 / 核心），**只读、只建议**。

.DESCRIPTION
    按 `AGENTS.md`「变更分级与验证口径」（2026-09-19 用户裁决）与
    `scripts/test/TESTING-SPEC.md` §1.1，把一批改动分成两档并回显各自「必需验证 / 不执行」：

      · 非核心（脚本 / 文本与 lang / 手册文本 / tooltip 文案 / 文档 / 注释 / 测试夹具）
        ⇒ 必需：受影响子项目 `gradlew build` + 静态闸门（`Test-MtSyntax`；改过 lang 另跑 `check_lang_sync`）
        ⇒ **不执行**：冒烟测试（阶段 L `--phase launch` / 阶段 C `--phase cases` / `mt.ps1` 全流程）
      · 核心（玩法逻辑与数值 / 网络 / 存档同步 / 注册表 / mixin / 渲染输入 / `data/**` 数据包 / build.gradle 行为项）
        ⇒ 必需：构建 + **定向最小冒烟**（只跑与该改动直接相关的用例；`mt_launch` 固有启动期硬闸门不得跳过）
        ⇒ **不执行**：与改动无直接关系的模块用例（默认取消并在附录 A 登记；确需者登记为推迟）
        ⇒ 命中「完整冒烟」五种情形（发版批次 / 跨模块公共路径 / 门槛类规则自身改动 / 用户显式要求 /
          26.1.2 迁移与一致性验收批次）时**必须跑全清单**（阶段 L + 阶段 C，三线各一遍）

    ⚠️ 本脚本**不做任何写入**，退出码**恒为 0**（它是建议，不是闸门）。
    判定为「核心」时只代表「未命中非核心白名单」；**拿不准的一律按核心处理**。
    与 `AGENTS.md` 的正表冲突时，**以 `AGENTS.md` 为准**。

    ⚠️ 参数一律 `--` 风格（与 `scripts/test` 其余工具一致），故**不用** `param()` 绑定，改手工解析 `$args`。

.参数
    --staged              只看已 `git add` 的改动（`git diff --cached --name-only`）。
    --paths <p1> <p2> …   直接给定相对仓库根的路径列表（不看 git 状态）。
    --root <path>         仓库根（缺省由 `$PSScriptRoot\..\..` 推导，不依赖当前目录）。

.EXAMPLE
    pwsh -NoProfile -File scripts/test/mt_scope.ps1
.EXAMPLE
    pwsh -NoProfile -File scripts/test/mt_scope.ps1 --staged
.EXAMPLE
    pwsh -NoProfile -File scripts/test/mt_scope.ps1 --paths AGENTS.md CHANGELOG_ZH.md
#>

$ErrorActionPreference = 'Stop'

# ── 参数（手工解析，`--` 风格）──────────────────────────────────────────
$Staged = $false
$Root = ''
$PathList = New-Object System.Collections.Generic.List[string]

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    $key = $tok.TrimStart('-').ToLowerInvariant()
    if ($key -eq 'staged') {
        $Staged = $true; $i++
    } elseif ($key -eq 'paths') {
        $i++
        while ($i -lt $args.Count -and -not ([string]$args[$i]).StartsWith('-')) {
            $PathList.Add([string]$args[$i]); $i++
        }
    } elseif ($key -eq 'root') {
        if ($i + 1 -ge $args.Count) { Write-Host 'MT_SCOPE: ERROR 缺少 --root 的值'; exit 0 }
        $Root = [string]$args[$i + 1]; $i += 2
    } else {
        Write-Host "MT_SCOPE: ERROR 未知参数 $tok（支持 --staged / --paths <相对路径…> / --root <路径>）"
        exit 0
    }
}

if (-not $Root) {
    $Root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
}

# 非核心白名单（**唯一实现**；与 AGENTS.md 的正表必须同口径）。
# 任一命中即判非核心；未命中一律核心（保守）。
$NonCorePatterns = @(
    '^scripts/',                       # 工具链 / 测试用例 / 测试夹具（含 resources/** 数据包夹具）
    '^tools/',                         # 守门脚本
    '^docs/',                          # 文档目录
    '\.md$',                           # 文档（AGENTS.md / CHANGELOG* / TESTING-SPEC.md / KNOWN-ISSUES.md …）
    '(^|/)lang/[^/]+\.json$',          # 语言文件（纯文案）
    '(^|/)patchouli_books/',           # 帕秋莉手册内容（文本）
    '^temp/',
    '^run/'
)

function Get-MtScopeGitPaths {
    param([string]$RepoRoot, [switch]$OnlyStaged)

    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { return @() }

    if ($OnlyStaged) {
        $raw = & git -C $RepoRoot diff --cached --name-only --diff-filter=ACMR 2>$null
        return @($raw | Where-Object { $_ -and ([string]$_).Trim() })
    }

    # 工作区变更（含未跟踪）：git status --porcelain 一行一项，前两列是状态码。
    $raw = & git -C $RepoRoot status --porcelain 2>$null
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($line in @($raw)) {
        $s = [string]$line
        if (-not $s -or $s.Length -lt 4) { continue }
        $p = $s.Substring(3).Trim()
        if ($p -match '^(?<a>.+) -> (?<b>.+)$') { $p = $Matches['b'] }   # 重命名取新路径
        $p = $p.Trim('"')
        if ($p) { $out.Add($p) }
    }
    return @($out)
}

$files = if ($PathList.Count -gt 0) {
    @($PathList | Where-Object { $_ -and $_.Trim() })
} else {
    Get-MtScopeGitPaths -RepoRoot $Root -OnlyStaged:$Staged
}

if ($files.Count -eq 0) {
    Write-Host 'MT_SCOPE: 没有检测到变更（工作区干净，或 --staged 下暂存区为空）。'
    Write-Host 'MT_SCOPE_REQUIRED: （无）'
    exit 0
}

$nonCore = New-Object System.Collections.Generic.List[string]
$core = New-Object System.Collections.Generic.List[string]
foreach ($f in $files) {
    $norm = ([string]$f) -replace '\\', '/'
    $isNonCore = $false
    foreach ($pat in $NonCorePatterns) {
        if ($norm -match $pat) { $isNonCore = $true; break }
    }
    if ($isNonCore) { $nonCore.Add($norm) } else { $core.Add($norm) }
}

Write-Host "MT_SCOPE: 本批变更 $($files.Count) 个路径（仓库根 $Root）"
foreach ($f in $nonCore) { Write-Host "  [非核心] $f" }
foreach ($f in $core) { Write-Host "  [核心  ] $f" }

Write-Host ''
Write-Host "MT_SCOPE_RESULT: 非核心 $($nonCore.Count) 个 / 核心 $($core.Count) 个"

if ($core.Count -gt 0) {
    Write-Host 'MT_SCOPE: 级别 = 核心（同一批以最高级别为准；未命中非核心白名单即按核心处理）→ 默认执行「定向最小冒烟」'
    Write-Host 'MT_SCOPE_REQUIRED: 构建 + 只跑与本改动直接相关的用例（映射与举证见 AGENTS.md「变更分级与验证口径」第 7 条）+ mt_launch 固有启动期硬闸门（noai / 史莱姆压制 / preflight，不得跳过）'
    Write-Host 'MT_SCOPE_SKIP: 与改动无直接关系的模块用例（本批取消，登记在附录 A；确需者登记为推迟并写明触发条件）'
    Write-Host 'MT_SCOPE_ESCALATE: 命中以下任一情形必须跑完整清单（阶段 L + 阶段 C 全清单，三线各一遍）—— ① 发版/Release 批次 ② 跨模块公共路径（共享库 starengine_lib / 注册表与数据组件结构 / 网络协议 / 存档读档结构 / 全局伤害结算出入口）③ 门槛类规则自身改动（版本互通门槛 / 加载器门槛 / mixin 目标串 / 依赖区间）④ 用户显式要求 ⑤ 26.1.2 迁移与一致性验收批次'
} else {
    Write-Host 'MT_SCOPE: 级别 = 非核心（脚本 / 文本 / tooltip / 文档 / 注释 / 测试夹具）'
    Write-Host 'MT_SCOPE_REQUIRED: 受影响子项目 gradlew build + 静态闸门（pwsh -NoProfile -File scripts/devtools/Test-MtSyntax.ps1；改过 lang 另跑 tools/check_lang_sync.ps1）+ 自动本地提交'
    Write-Host 'MT_SCOPE_SKIP: 冒烟测试 / 游戏内自动化流程（阶段 L --phase launch、阶段 C --phase cases、mt.ps1 全流程）'
}

Write-Host ''
Write-Host 'MT_SCOPE_NOTE: 本脚本只读、只建议，退出码恒 0；判定口径以 AGENTS.md「变更分级与验证口径」为准。'
exit 0
