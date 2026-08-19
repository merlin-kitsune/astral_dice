#Requires -Version 7.0
<#
.SYNOPSIS
    Astral Dice 一键发布:更新版本号 -> Git 提交并推送 -> 编译 -> 推送整合包

.DESCRIPTION
    1. 更新版本号:可用 -Version 显式指定;缺省自动递增 1.0-SNAPSHOT.N 的 N
    2. 编译正式版本并推送(gradlew build -PdeployToPack,含 run/mods 与整合包)
    3. Git 提交当前版本(消息: release: v<版本>)
    4. Git 推送当前分支到远程仓库(未配置 remote 时自动跳过并提示;
       有 remote 但分支无 upstream 时自动 git push -u origin <分支>)
    顺序说明:先编译部署、后提交——构建失败时自动回滚版本号,绝不产生"已提交但不可构建"的版本。

.PARAMETER Version
    显式指定新版本号(如 1.1.0 / 1.0.0-release.1);缺省自动递增 SNAPSHOT 补丁号。

.PARAMETER SkipCommit
    跳过 Git 提交与推送步骤(仅更新版本号 + 编译 + 部署)。

.PARAMETER SkipPush
    保留 Git 提交,但跳过推送到远程仓库。

.EXAMPLE
    .\deploy.ps1                  # 1.0-SNAPSHOT.19 -> 1.0-SNAPSHOT.20,提交+推送+部署
    .\deploy.ps1 -Version 1.1.0   # 指定版本提交推送并部署
    .\deploy.ps1 -SkipCommit      # 只构建部署,不提交不推送
    .\deploy.ps1 -SkipPush        # 提交但不推送远程
#>
param(
    [string]$Version = '',
    [switch]$SkipCommit,
    [switch]$SkipPush
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$propsFile = Join-Path $root 'gradle.properties'

function Write-Step([string]$msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Write-Ok([string]$msg) { Write-Host "  [OK] $msg" -ForegroundColor Green }

# ---------- 1. 读取并更新版本号 ----------
Write-Step '1/4 更新版本号'
if (-not (Test-Path $propsFile)) { throw "找不到 $propsFile" }
$propsContent = Get-Content $propsFile -Raw
$m = [regex]::Match($propsContent, '(?m)^mod_version=(.+)$')
if (-not $m.Success) { throw 'gradle.properties 中未找到 mod_version' }
$currentVersion = $m.Groups[1].Value.Trim()

if ($Version) {
    if ($Version -notmatch '^\d+\.\d+(\.\d+)?([-+][0-9A-Za-z.-]+)?$') {
        throw "版本号格式无效: $Version(示例: 1.1.0 / 1.0.0-release.1)"
    }
    $newVersion = $Version
} else {
    $inc = [regex]::Match($currentVersion, '^(\d+\.\d+-SNAPSHOT\.)(\d+)$')
    if (-not $inc.Success) {
        throw "当前版本 $currentVersion 无法自动递增(仅支持 x.y-SNAPSHOT.N),请用 -Version 显式指定"
    }
    $newVersion = $inc.Groups[1].Value + ([int]$inc.Groups[2].Value + 1)
}

Write-Host "  版本: $currentVersion -> $newVersion"
$propsNew = [regex]::Replace($propsContent, '(?m)^mod_version=.+$', "mod_version=$newVersion")
Set-Content -Path $propsFile -Value $propsNew -Encoding UTF8

# ---------- 2. 编译正式版本并推送(失败则回滚版本号) ----------
Write-Step '2/4 编译并部署(gradlew build -PdeployToPack)'
Push-Location $root
try {
    & (Join-Path $root 'gradlew.bat') build -PdeployToPack
    if ($LASTEXITCODE -ne 0) { throw "构建失败(exit code: $LASTEXITCODE)" }
} catch {
    Set-Content -Path $propsFile -Value $propsContent -Encoding UTF8
    Write-Host "  构建失败,版本号已回滚为 $currentVersion" -ForegroundColor Red
    throw
} finally {
    Pop-Location
}

# ---------- 3. Git 提交当前版本 ----------
if ($SkipCommit) {
    Write-Step '3/4 已跳过 Git 提交与推送(-SkipCommit)'
} else {
    Write-Step '3/4 Git 提交'
    Push-Location $root
    try {
        git add -A
        if ($LASTEXITCODE -ne 0) { throw 'git add 失败' }
        $staged = git diff --cached --name-only
        if ($LASTEXITCODE -ne 0) { throw 'git diff 失败' }
        if ($staged) {
            git commit -m "release: v$newVersion"
            if ($LASTEXITCODE -ne 0) { throw 'git commit 失败' }
            Write-Ok "已提交 $($staged.Count) 个文件: release: v$newVersion"
        } else {
            Write-Host '  无暂存变更,跳过提交' -ForegroundColor Yellow
        }
    } finally {
        Pop-Location
    }
}

# ---------- 4. Git 推送当前分支到远程仓库 ----------
if ($SkipCommit -or $SkipPush) {
    if (-not $SkipCommit) {
        Write-Step '4/4 已跳过 Git 推送(-SkipPush)'
    }
} else {
    Write-Step '4/4 Git 推送'
    Push-Location $root
    try {
        $remotes = git remote
        if (-not $remotes) {
            Write-Host '  未配置远程仓库(git remote 为空),跳过推送' -ForegroundColor Yellow
            Write-Host '  如需启用: git remote add origin <仓库地址>' -ForegroundColor Yellow
        } else {
            $branch = git rev-parse --abbrev-ref HEAD
            git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>$null | Out-Null
            if ($LASTEXITCODE -ne 0) {
                # 分支无 upstream:首次推送建立跟踪关系(取第一个远程)
                $origin = @($remotes)[0].Trim()
                Write-Host "  分支 $branch 无上游,推送并跟踪到 $origin"
                git push -u $origin $branch
            } else {
                git push
            }
            if ($LASTEXITCODE -ne 0) { throw 'git push 失败' }
            Write-Ok "已推送 $branch 到远程仓库"
        }
    } finally {
        Pop-Location
    }
}

Write-Host "`n部署完成: astral_dice-$newVersion.jar 已推送到 run/mods 与整合包" -ForegroundColor Green
