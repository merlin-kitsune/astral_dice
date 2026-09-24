#!/usr/bin/env pwsh
<#
mt_inject.ps1 — 游戏内输入注入（阶段 C 的执行臂）。

1:1 移植自 scripts/test/mt_inject.py（python 原件已在 92fbeaf 删除，迁移期曾用于逐字节比对；取回：`git show 92fbeaf^:scripts/test/mt_inject.py`）。

## 平台边界（关键设计）

  Windows —— 用 user32 `PostMessage` 投递 WM_KEYDOWN/WM_CHAR/WM_*BUTTON，
             绕开前台焦点与前输入法限制（对 GLFW 窗口有效）。非 Windows 平台无法复现
             该机制，python 版改为输出结构化请求交会话层经 MCP 通道执行；
             本仓库测试链已收敛到仅 Windows，故该降级分支未移植（见「差异」）。

  硬前置: 本机需安装 en-US 键盘布局（注入按美式扫描码表投递）。
          注入前会**自动**把目标窗口所在线程的输入语言切到 en-US（见 mt_ime.ps1），
          因此**不需要用户手动切换输入法**，也不影响用户其它程序的输入语言。

## 用法

  mt_inject.ps1 key -Key rclick
  mt_inject.ps1 key -Key rclick -HoldMs 3000      # 按住右键 3 秒（长按/自动重复类回归）
  mt_inject.ps1 key -Key w -HoldMs 1000
  mt_inject.ps1 cmd -Command "/astral_dice targetselect enemy"
  mt_inject.ps1 cmd -Command "/give @s minecraft:stone" -NoEsc
  mt_inject.ps1 cmd -Command "/give Dev x" -Layout as-is     # 排查用：不切语言
  mt_inject.ps1 mouse -Button left                 # 窗口中心左键（目标选择器「确认」）
  mt_inject.ps1 mouse -Button right -Shift         # 窗口中心右键 + 潜行（「自用」提示 / 取消）
  mt_inject.ps1 mouse -Button right -HoldMs 3000   # 按住右键 3 秒（长按/自动重复类回归）
  mt_inject.ps1 mouse -Button wheel                # 向下滚一格（默认 notches=-1；「滚轮拦截」回归）
  mt_inject.ps1 mouse -Button wheel -Notches 2     # 向上滚两格
  mt_inject.ps1 mouse -Button wheel -Transport postmessage   # 排查：WM_MOUSEWHEEL 直投

## 退出码（与 python 版一致）

  0 = 注入完成；2 = 未找到窗口 / 语言未就绪 / 未知按键 / 非法 `--button`·`--notches` 值 / 参数错误。

## 与 python 版的差异（逐条）

1. **非 Windows 降级分支（`_degrade`）未移植**：它输出 JSON 结构化请求 + BLOCKED 行，
   是给非 Windows 会话层用的；本仓库已收敛到仅 Windows（Mt.Proc.psm1 同样移除了
   POSIX 分支），且本脚本依赖只存在于 Windows 的 user32。
2. **多两个「验证用」参数**（python 版没有，默认不改变任何行为）：
     -Hwnd  <long>  ：跳过窗口定位、直接对指定窗口投递（端到端注入实测用）
     -DryRun        ：只打印将要投递的 (hwnd,msg,wparam,lparam) 元组序列，不真的 PostMessage，
                      也不切语言、不 sleep。用于与 python 侧的**消息构造等价性**逐行比对：
                        临时脚本 monkeypatch `mt_inject` 的 PostMessageW 记录元组，
                        与 `-DryRun` 的输出逐字节比较（见验证记录）。
                     干跑输出**只有** MT_INJECT_DRYRUN / MT_INJECT_DRYRUN_RC 两类行。
3. `cmd` 的整串文本按**码点**切分（与 python `for ch in text` 一致）：非 BMP 字符
   （如 emoji）在 python 侧是**一个** WM_CHAR，幼稚的 .NET `ToCharArray()` 会拆成两个
   代理项 —— 这里显式按 Unicode 标量值遍历，与 python 等价。
4. 未知按键的报错沿用原参数（`未知按键 {key}`），大小写与 python 一致。
5. **参数解析不用 `param()`，改用手写 `$args` 循环**（与 mt.ps1 / mt_env.ps1 /
   mt_cleanup.ps1 / mt_stop.ps1 一致）。原因是 `param()` 有两个硬伤：
     · python 的 **kebab 长选项**（`--no-esc` / `--hold-ms` / `--dry-run`）在 PS 参数名里
       不合法（不能含连字符）→ 绑定器直接拒绝，而调用方按 kebab 拼写调；
     · 未知参数在 `param()` 下是绑定器报错（**exit 1**），本仓约定是
       `MT_ERROR: 未知参数 <x>` + **exit 2**（与 python argparse 一致）。
   归一化规则：去掉前导 `-` 后**删除全部连字符**再小写比较 ⇒ `--no-esc` / `-NoEsc` /
   `--hold-ms` / `-HoldMs` 等价，两种调用风格都能吃下。
6. `-Hwnd` / `-DryRun` 是**上面第 2 条那两个扩展参数**（python 版没有）：
   接受 `--hwnd` / `--dry-run` 与 `-Hwnd` / `-DryRun` 两种拼写。
7. **`mouse` 子命令是 pwsh 侧新增**（python 只有 `key` / `cmd`；鼠标此前只能经 `key` 的
   `attack` / `rclick` / `shift-rclick` 别名触达）。它**不新造注入路径**：内部直接调
   `Send-MtInjectMouseCenter`（与上述别名同一个函数、同一坐标口径）。`--button` 必填且
   接受 `left|right|wheel`（其它值 ⇒ `MT_ERROR: 非法 --button 值 …` + rc=2，在定位窗口**之前**
   校验，故客户端没跑也能得到可读报错）；`--shift` 与 `--hold-ms` 与 `key` 子命令同义。
   `--version` / `--hwnd` / `--layout` 与 `key` 子命令同形，输出行前缀为 `MT_INJECT_MOUSE:`
   （与 `MT_INJECT_KEY:` / `MT_INJECT_CMD:` 同一族）。
8. **`--button wheel` + `--notches` 是 2026-09-18（t23, F3）新增的滚轮原语**：`postmessage`
   通道走 `PostMessage(WM_MOUSEWHEEL, HIGHWORD=delta, lParam=窗口中心)`，`sendinput` 通道走
   **真实** `SendInput(MOUSEEVENTF_WHEEL)`（`mouseData = notches × 120`）；`--notches` 缺省 -1
   （向下滚一格），取值非 0 且 |n| ≤ 32，`wheel` 与 `--shift` 组合直接报错（不静默忽略）。
   存在理由：目标选择器「选择期间拦截滚轮」此前**零自动化断言**（用例没有滚轮步骤、注入器没有
   滚轮原语、探针没有选中栏位读数），该原语 + 探针 `selectedSlot` 读数把这条语义变成可跑的两步
   断言（会话中不变 / 取消后变化）。
#>

$ErrorActionPreference = 'Stop'

$script:MtLibDir = Join-Path $PSScriptRoot 'lib'
Import-Module (Join-Path $script:MtLibDir 'Mt.Phase.psm1') -Force
Import-Module (Join-Path $script:MtLibDir 'Mt.Paths.psm1') -Force
Import-Module (Join-Path $script:MtLibDir 'Mt.Proc.psm1') -Force
Import-Module (Join-Path $script:MtLibDir 'Mt.Win32.psm1') -Force

# ⚠️ 必须第一件事：不设 UTF-8 输出编码时中文会按本机码页(936/GBK)写出，与 python 版不等
Initialize-MtConsole

# 注：`$script:DryRun` 在下方参数解析之后才赋值（见「入口」段）

# ── 扫描码表（美式布局；注入前由 mt_ime 把目标窗口线程切到 en-US）────────
# 2026-09-17 新增 k/o/r：供「光影开关」回归使用（键位取自安装 jar 的字节码，
# Iris#onEarlyInitialize 里 `iris.keybind.toggleShaders`=GLFW 75('k')、
# `iris.keybind.shaderPackSelection`=GLFW 79('o')、`iris.keybind.reload`=GLFW 82('r')）。
$script:Scan = @{
    't' = 0x14; 'enter' = 0x1C; 'escape' = 0x01; 'e' = 0x12; 'j' = 0x24
    'h' = 0x23; 'w' = 0x11; 'f2' = 0x3C; 'f3' = 0x3D; 'slash' = 0x35; 'tab' = 0x0F
    'k' = 0x25; 'o' = 0x18; 'r' = 0x13; 'q' = 0x51
}
$script:Vk = @{
    't' = 0x54; 'enter' = 0x0D; 'escape' = 0x1B; 'e' = 0x45; 'j' = 0x4A
    'h' = 0x48; 'w' = 0x57; 'f2' = 0x71; 'f3' = 0x72; 'slash' = 0xBF; 'tab' = 0x09
    'k' = 0x4B; 'o' = 0x4F; 'r' = 0x52; 'q' = 0x10
}
for ($i = 1; $i -le 9; $i++) {
    $digit = [string]$i
    $script:Vk[$digit] = 0x30 + $i
    $script:Scan[$digit] = 0x02 + ($i - 1)
}
# 功能键 F1..F12（2026-09-25 新增）：护盾这类**世界空间特效**的取证必须切第三人称
# （第一人称下相机在球内、外壳会糊满整屏，渲染端有意跳过自己那一个）⇒ 需要能按 F5。
# VK 连续 F1=0x70..F12=0x7B；扫描码 F1..F10 = 0x3B..0x44 连续，F11/F12 = 0x57/0x58（不连续）。
for ($i = 1; $i -le 12; $i++) {
    $fn = "f$i"
    $script:Vk[$fn] = 0x6F + $i
    $script:Scan[$fn] = if ($i -le 10) { 0x3A + $i } else { 0x57 + ($i - 11) }
}

# 语义键 → 实际按键
# 2026-09-17：删除 `'confirm' = 'enter'`（全仓 grep 确认无任何用例/脚本引用该别名）——
# 「确认」在目标选择器语义下改为**鼠标左键**（走 `mouse --button left`），不再用 Enter；
# 确需 Enter 的场合直接写 `-Key enter` 即可（上面扫描码表里 enter 一直在）。
$script:KeyAlias = @{
    'chat' = 't'; 'skill' = 'j'; 'cancel' = 'escape'
    'screenshot' = 'f2'
    'debug' = 'f3'; 'inventory' = 'e'; 'card' = 'h'
    # 光影（Iris）语义键：开关 / 光影选择界面 / 重载光影
    'shadertoggle' = 'k'; 'shaderscreen' = 'o'; 'shaderreload' = 'r'
    # 2026-09-22 新增：丢出手中物品（原版 drop）。用途 = 制造**掉落物**（如星币）走
    # 「玩家拾取 ⇒ 吸收进钱包」这条真实路径，而不必依赖 /summon 的 NBT 语法。
    'drop' = 'q'
    # 视角：原版 F5 在 第一人称 → 第三人称背面 → 第三人称正面 之间循环
    'thirdperson' = 'f5'
}

$script:WM_KEYDOWN = 0x0100
$script:WM_KEYUP = 0x0101
$script:WM_CHAR = 0x0102
$script:WM_LBUTTONDOWN = 0x0201
$script:WM_LBUTTONUP = 0x0202
$script:WM_RBUTTONDOWN = 0x0204
$script:WM_RBUTTONUP = 0x0205
# 滚轮（2026-09-18 t23 新增，F3「滚轮拦截」自动化）：WM_MOUSEWHEEL 的**高位字**是
# 带符号的滚动量（一格 = WHEEL_DELTA = 120）；GLFW 的窗口过程按 `(SHORT)HIWORD(wParam)/WHEEL_DELTA`
# 转成 scroll 回调 ⇒ 与真实滚轮等价。低位字 lParam 是**屏幕坐标**（文档如此），但 GLFW/MC 的
# 滚轮处理只读 wParam ⇒ 这里沿用既有 lParam 口径（窗口矩形中心）并注明，不另造坐标来源。
$script:WM_MOUSEWHEEL = 0x020A
$script:WHEEL_DELTA = 120
$script:VK_SHIFT = 0xA0

# 投递通道（2026-09-13）：
#   sendinput   —— 默认。用 SendInput 发**真实**键鼠，GLFW 才会当作真实输入处理。
#   postmessage —— 旧路径（PostMessage(WM_KEYDOWN/WM_CHAR)），仅在排查时用 --transport postmessage 切回。
# 为什么换：PostMessage 的按键在 GLFW 侧不被接受（窗口失焦后尤其明显），命令被静默丢弃而脚本仍报成功。
$script:Transport = 'sendinput'
# 是否强制旧的「界面归一化」（T+Esc）。默认 false；sendinput 通道下保持 false，
# postmessage 通道下仍按老行为执行（见 Invoke-MtInjectCmdCommand 内注释）。
$script:EscNormalize = $false

# ══ 输出纪律 ══════════════════════════════════════════════════════════════

function Write-MtInjectLine {
    <#
    .SYNOPSIS
        面向观众的输出行（干跑模式下静默，保证干跑输出只有元组序列）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)

    if ($script:DryRun) { return }
    Write-MtLine $Text
}

function Write-MtInjectDryTuple {
    <#
    .SYNOPSIS
        干跑模式下打印一条将投递的消息元组（格式与验证脚本两边一致）。
    #>
    [CmdletBinding()]
    param([long]$Hwnd, [uint32]$Msg, [long]$WParam, [long]$LParam)

    Write-MtLine ('MT_INJECT_DRYRUN: hwnd=0x{0:X16} msg=0x{1:X4} wparam=0x{2:X16} lparam=0x{3:X16}' -f `
            $Hwnd, $Msg, $WParam, $LParam)
}

function Send-MtInjectMessage {
    <#
    .SYNOPSIS
        所有注入的唯一出口：真实投递或（干跑时）记录元组。

    .NOTES
        python 侧同位置是 `ctypes.windll.user32.PostMessageW(...)`；把出口收敛到这里
        才能让「消息构造等价性」有一个可复核的比对面。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][long]$Hwnd,
        [Parameter(Mandatory)][uint32]$Msg,
        [long]$WParam = 0,
        [long]$LParam = 0
    )

    if ($script:DryRun) {
        Write-MtInjectDryTuple -Hwnd $Hwnd -Msg $Msg -WParam $WParam -LParam $LParam
        return
    }
    [void](Send-MtPostMessage -Hwnd $Hwnd -Msg $Msg -WParam $WParam -LParam $LParam)
}

function Start-MtInjectPause {
    <#
    .SYNOPSIS
        注入节奏用的 sleep（干跑时跳过 —— python 侧验证脚本也 patch 掉了 time.sleep）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][int]$Milliseconds)

    if ($script:DryRun) { return }
    Start-Sleep -Milliseconds $Milliseconds
}

# ══ 按键投递 ══════════════════════════════════════════════════════════════

function Send-MtInjectKeyDown {
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd, [Parameter(Mandatory)][int]$Vk, [Parameter(Mandatory)][int]$Scan)

    if ($script:DryRun) { return }
    if ($script:Transport -eq 'sendinput') {
        # 真实输入：只进前台窗口（调用方已 Assert-MtInjectForeground）；按**扫描码**发送
        [void](Send-MtRealKey -Vk $Vk -Scan $Scan)
        return
    }
    Send-MtInjectMessage -Hwnd $Hwnd -Msg $script:WM_KEYDOWN -WParam $Vk -LParam (1 -bor ($Scan -shl 16))
}

function Send-MtInjectKeyUp {
    <#
    .NOTES
        lParam 里 python 版把 (1<<14) 或了两次（`(1<<30)|(1<<14)|(1<<14)|(scan<<16)`），
        结果与或一次完全相同；这里照抄以求字节级一致，不做「优化」。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd, [Parameter(Mandatory)][int]$Vk, [Parameter(Mandatory)][int]$Scan)

    if ($script:DryRun) { return }
    if ($script:Transport -eq 'sendinput') {
        [void](Send-MtRealKey -Vk $Vk -Scan $Scan -Up)
        return
    }
    Send-MtInjectMessage -Hwnd $Hwnd -Msg $script:WM_KEYUP -WParam $Vk `
        -LParam ((1 -shl 30) -bor (1 -shl 14) -bor (1 -shl 14) -bor ($Scan -shl 16))
}

function Send-MtInjectKey {
    <#
    .SYNOPSIS
        一次完整的按下 + 抬起（对应 python _post_key）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd, [Parameter(Mandatory)][int]$Vk, [Parameter(Mandatory)][int]$Scan)

    Send-MtInjectKeyDown -Hwnd $Hwnd -Vk $Vk -Scan $Scan
    Send-MtInjectKeyUp -Hwnd $Hwnd -Vk $Vk -Scan $Scan
}

function Send-MtInjectMouseCenter {
    <#
    .SYNOPSIS
        在窗口中心投递鼠标左右键（对应 python _click_center）；-HoldMs 可把按键按住一段时间。

    .NOTES
        坐标只由**窗口矩形**算出（不含客户区偏移），这是 python 版的原样行为：
        游戏窗口是全屏/无边框时二者等价，带边框时会有偏差 —— 保持 1:1 不擅自修正。
        HoldMs：down 与 up 之间的停顿毫秒数（0 = 旧行为 100ms 单击）。
        按住期间原版 `Minecraft#startUseItem` 每 4 tick 自动重复 —— 「长按右键」类回归
        （如效果牌长按不连发）必须用它，单击无法覆盖该分支。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][long]$Hwnd,
        [Parameter(Mandatory)][bool]$Right,
        [bool]$Shift = $false,
        [int]$HoldMs = 0
    )

    # ── 修饰键（SHIFT）必须**同通道成对**且异常路径也复位（2026-09-18 t21 实测修复）──────────
    # 缺陷（修复前）：按下走 `Send-MtInjectKeyDown`（sendinput 通道 ⇒ 真实 `SendInput`），
    # 抬起却直接写 `Send-MtInjectMessage … WM_KEYUP`（**PostMessage**）—— 通道不一致导致
    # OS 级 SHIFT 逻辑键状态**常驻按下**（实测 `GetAsyncKeyState(VK_SHIFT)` 的 0x8000 位在
    # 20/20 次 `mouse --right -Shift` 后仍为按下；不带 -Shift 的对照组 0/20）。
    # 现在：抬起改走 `Send-MtInjectKeyUp`（与按下同一个分发函数 ⇒ 两条通道各自自洽），
    # 并用 try/finally 保证「按下过就一定会抬起」（鼠标段抛异常也不留残留）。
    # 不引入任何全局状态：`$shiftDown` 只是本函数内的局部标志。
    $shiftDown = $false
    try {
        if ($Shift) {
            Send-MtInjectKeyDown -Hwnd $Hwnd -Vk $script:VK_SHIFT -Scan 0x2A
            $shiftDown = $true
            Start-MtInjectPause -Milliseconds 120
        }

        $rect = Get-MtWindowRect -Hwnd $Hwnd
        $x = [int](($rect.Right - $rect.Left) / 2)
        $y = [int](($rect.Bottom - $rect.Top) / 2)
        $hold = if ($HoldMs -gt 0) { $HoldMs } else { 100 }

        if ($script:Transport -eq 'sendinput') {
            # 真实鼠标：先把光标移到窗口中心（屏幕坐标），再发真实左右键
            if (-not $script:DryRun) {
                [void](Set-MtRealCursorPosition -X ($rect.Left + $x) -Y ($rect.Top + $y))
                Start-MtInjectPause -Milliseconds 80
                [void](Send-MtRealMouse -Right $Right -Up $false)
                Start-MtInjectPause -Milliseconds $hold
                [void](Send-MtRealMouse -Right $Right -Up $true)
            }
        } else {
            $lp = ($y -shl 16) -bor ($x -band 0xFFFF)
            $down = if ($Right) { $script:WM_RBUTTONDOWN } else { $script:WM_LBUTTONDOWN }
            $up = if ($Right) { $script:WM_RBUTTONUP } else { $script:WM_LBUTTONUP }
            Send-MtInjectMessage -Hwnd $Hwnd -Msg $down -WParam 1 -LParam $lp
            Start-MtInjectPause -Milliseconds $hold
            Send-MtInjectMessage -Hwnd $Hwnd -Msg $up -WParam 0 -LParam $lp
        }
    } finally {
        if ($shiftDown) {
            Start-MtInjectPause -Milliseconds 100
            # 与按下同一分发（sendinput ⇒ SendInput；postmessage ⇒ 同 lParam 的 WM_KEYUP）
            Send-MtInjectKeyUp -Hwnd $Hwnd -Vk $script:VK_SHIFT -Scan 0x2A
        }
    }
}

# ══ 滚轮注入（2026-09-18 t23 新增；F3「滚轮拦截」自动化）══════════════════════
#
# 为什么要有它：目标选择器会话激活期间**拦截滚轮**（`InputEvent.MouseScrollingEvent` 取消，
# 防切栏/缩放），但此前的工具链没有任何滚轮注入原语 ⇒ 该语义在全链路**零自动化断言**
# （t12 的 F3）。这里补上原语，配合探针的 `selectedSlot` 读数就能做出「会话中滚一格 ⇒
# selectedSlot 不变；取消后再滚一格 ⇒ selectedSlot 变化（正对照）」两步断言。
#
# 两条通道都给：
#   postmessage —— `PostMessage(WM_MOUSEWHEEL, (delta shl 16), lParam)`（与既有鼠标路径同渠道）；
#   sendinput   —— 真实 `SendInput(MOUSEEVENTF_WHEEL, mouseData = delta)`（默认通道；游戏侧
#                  GLFW 收到的就是真实滚轮）。`Mt.Win32.psm1` 的 `Mt.RealInput` 只暴露了
#                  左右键，没有「带标志的鼠标」入口，而该模块不在本任务 inScope ⇒ 这里在
#                  mt_inject 内部用**本脚本自己的**最小 P/Invoke（独立类型名，不与 Mt.RealInput 冲突）。
if (-not ('Mt.Inject.WheelInput' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

namespace Mt.Inject
{
    public static class WheelInput
    {
        [StructLayout(LayoutKind.Sequential)]
        private struct MOUSEINPUT
        {
            public int dx;
            public int dy;
            public uint mouseData;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct INPUT
        {
            public uint type;
            public MOUSEINPUT mi;
        }

        private const uint INPUT_MOUSE = 0;
        private const uint MOUSEEVENTF_WHEEL = 0x0800;

        [DllImport("user32.dll", SetLastError = true)]
        private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

        /// <summary>真实滚轮：delta = 格数 × WHEEL_DELTA（正 = 向上/远离用户，负 = 向下）。</summary>
        public static bool Wheel(int delta)
        {
            INPUT input = new INPUT();
            input.type = INPUT_MOUSE;
            input.mi.dwFlags = MOUSEEVENTF_WHEEL;
            input.mi.mouseData = (uint)delta;
            INPUT[] batch = new INPUT[] { input };
            return SendInput(1, batch, Marshal.SizeOf(typeof(INPUT))) == 1;
        }
    }
}
'@
}

function Send-MtInjectWheel {
    <#
    .SYNOPSIS
        在目标窗口触发一次滚轮（`-Notches` 格，正 = 向上滚，默认 -1 = 向下滚一格）。

    .NOTES
        与 `Send-MtInjectMouseCenter` 同一纪律：**同一通道成对自洽**（不存在按下/抬起，
        但 sendinput 走真实 SendInput、postmessage 走 PostMessage，二者不混用）；
        `-DryRun` 只打印将投递的元组，不真的注入（与既有 key/mouse 干跑口径一致）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][long]$Hwnd,
        [int]$Notches = -1
    )

    $delta = $Notches * $script:WHEEL_DELTA
    $rect = Get-MtWindowRect -Hwnd $Hwnd
    $x = [int](($rect.Right - $rect.Left) / 2)
    $y = [int](($rect.Bottom - $rect.Top) / 2)
    $lp = ($y -shl 16) -bor ($x -band 0xFFFF)

    if ($script:Transport -eq 'sendinput') {
        if ($script:DryRun) {
            # 干跑：与 postmessage 同形地打印一条元组（msg 用 WM_MOUSEWHEEL 便于逐行比对），
            # 另加一行标明真实通道调用，确保「干跑不撒谎」。
            Write-MtInjectDryTuple -Hwnd $Hwnd -Msg $script:WM_MOUSEWHEEL `
                -WParam (([long]($delta -band 0xFFFF)) -shl 16) -LParam $lp
            Write-MtInjectDryTuple -Hwnd $Hwnd -Msg 0xFFFF -WParam $delta -LParam 0
            return
        }
        [void](Set-MtRealCursorPosition -X ($rect.Left + $x) -Y ($rect.Top + $y))
        Start-MtInjectPause -Milliseconds 60
        [void][Mt.Inject.WheelInput]::Wheel($delta)
        return
    }

    # ⚠️ 负数要按 16 位截断后再左移 16：(-120 -band 0xFFFF) = 0xFF88 ⇒ 0xFF880000
    # （GLFW 按 (SHORT)HIWORD 取回 -120；直接算 -120 << 16 会得到负数的高位垃圾）。
    $w = [long](([long]($delta -band 0xFFFF)) -shl 16)
    if ($script:DryRun) {
        Write-MtInjectDryTuple -Hwnd $Hwnd -Msg $script:WM_MOUSEWHEEL -WParam $w -LParam $lp
        return
    }
    Send-MtInjectMessage -Hwnd $Hwnd -Msg $script:WM_MOUSEWHEEL -WParam $w -LParam $lp
}

# ══ 注入前的输入语言准备 ═════════════════════════════════════════════════

function Get-MtInjectLayoutReady {
    <#
    .SYNOPSIS
        注入前的输入语言准备（对应 python _ensure_layout）。

    .NOTES
        Layout=auto（默认）：把目标窗口线程切到 en-US（mt_ime，只影响该线程，
          不触碰用户系统默认输入法；线程随游戏进程退出而消失，无需恢复）。
        Layout=as-is：不做任何切换，保持旧行为（排查用）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd, [Parameter(Mandatory)][string]$Layout)

    if ($Layout -eq 'as-is') {
        # 注意：这里的 `--layout` 是 python 版原文（保留双横线），不要改成 `-layout`
        return [pscustomobject]@{ Ok = $true; Message = '按 --layout as-is 跳过切换' }
    }
    return (Set-MtWindowUs -Hwnd $Hwnd)
}

function Resolve-MtInjectWindow {
    <#
    .SYNOPSIS
        解析本次要投递的目标窗口（-Hwnd 显式指定优先，否则按版本定位）。

    .NOTES
        ⚠️ 先打印错误行再 return 2 的顺序与 python 一致：错误走 stderr、
        退出码 2、stdout 保持为空。
    #>
    [CmdletBinding()]
    param([long]$Hwnd, [string]$Version)

    $h = $Hwnd
    if (-not $h) { $h = Find-MtMinecraftWindow -Version $Version }
    if (-not $h) {
        $st = $null
        try { $st = Get-MtClientStatus -Paths (Get-MtPaths -Version $Version) } catch { }
        if ($st -and -not $st.Alive) {
            Write-MtErrLine ("MT_INJECT: ERROR — {0} 客户端未在运行（客户端入口进程：1.21.1/1.20.1 为 net.minecraft.client.main.Main，26.1.2 为 net.neoforged.fml.startup.Client；先跑 --phase launch）" -f $Version)
            return [long]0
        }
        Write-MtErrLine 'MT_INJECT: ERROR — 未能唯一确定本版本 Minecraft 窗口（存在多个候选客户端）'
        return [long]0
    }
    return [long]$h
}

# ══ 子命令 ═══════════════════════════════════════════════════════════════

function Assert-MtInjectForeground {
    <#
    .SYNOPSIS
        注入前把目标窗口置前台；置不上则返回 $false（调用方按错误处理，绝不静默通过）。

    .NOTES
        2026-09-13 实测根因：注入走 PostMessage(WM_KEYDOWN/WM_CHAR)，而 GLFW **忽略非前台窗口**
        收到的按键 —— 窗口一旦失焦，命令被静默丢弃而脚本仍打印成功；更糟的是注入器自带的
        「Esc 归一化」若第二个 Esc 因此丢失，暂停菜单会被永久顶开（`Minecraft.pause=true`
        → `IntegratedServer` 停止 tick），其后整轮用例全部无输出。
        SetForegroundWindow 受 Windows 前台锁限制，故按 Win32 惯例先 AttachThreadInput
        到当前前台线程与目标线程，再调用，最后恢复附加状态。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd, [int]$Retries = 3)

    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        if (((Get-MtForegroundWindow) -eq $Hwnd) -and ((Get-MtRealFocus -Hwnd $Hwnd) -eq $Hwnd)) { return $true }

        [void](Invoke-MtShowWindow -Hwnd $Hwnd -CmdShow 9)   # SW_RESTORE：最小化时先还原
        [void](Invoke-MtBringWindowToTop -Hwnd $Hwnd)

        $fg = Get-MtForegroundWindow
        $fgTid = 0
        if ($fg) { $fgTid = [int](Get-MtThreadOfWindow -Hwnd $fg) }
        $targetTid = [int](Get-MtThreadOfWindow -Hwnd $Hwnd)
        $myTid = [int][Mt.Win32.Native]::GetCurrentThreadId()

        $a1 = $false
        if ($fgTid) { $a1 = [bool](Set-MtAttachThreadInput -FromTid $myTid -ToTid $fgTid -Attach $true) }
        $a2 = $false
        if ($targetTid -and $targetTid -ne $fgTid) {
            $a2 = [bool](Set-MtAttachThreadInput -FromTid $myTid -ToTid $targetTid -Attach $true)
        }
        try {
            [void](Set-MtForegroundWindow -Hwnd $Hwnd)
            # 关键补充：**前台不等于焦点**。附加状态下把键盘焦点也交给目标窗口，
            # 否则按键会被游戏静默忽略（2026-09-13 实测根因）。
            if ($targetTid -and ($targetTid -eq $myTid -or $a2)) {
                [void][Mt.RealInput]::FocusWindow([IntPtr]::new($Hwnd))
            }
            Start-Sleep -Milliseconds 120
        } finally {
            if ($a2) { [void](Set-MtAttachThreadInput -FromTid $myTid -ToTid $targetTid -Attach $false) }
            if ($a1) { [void](Set-MtAttachThreadInput -FromTid $myTid -ToTid $fgTid -Attach $false) }
        }

        if (((Get-MtForegroundWindow) -eq $Hwnd) -and ((Get-MtRealFocus -Hwnd $Hwnd) -eq $Hwnd)) { return $true }
    }
    return $false
}

function Invoke-MtInjectKeyCommand {
    [CmdletBinding()]
    param([string]$Key, [int]$HoldMs, [string]$Version, [string]$Layout, [long]$Hwnd, [bool]$NoEsc = $false)

    $hwnd = Resolve-MtInjectWindow -Hwnd $Hwnd -Version $Version
    if (-not $hwnd) { return 2 }

    if (-not $script:DryRun) {
        $r = Get-MtInjectLayoutReady -Hwnd $hwnd -Layout $Layout
        $tag = if ($r.Ok) { 'OK' } else { 'FAIL' }
        Write-MtInjectLine ('MT_INJECT_LAYOUT: {0} — {1}' -f $tag, $r.Message)
        if (-not $r.Ok) {
            Write-MtErrLine ('MT_INJECT: ERROR — 输入语言未就绪，拒绝注入（{0}）' -f $r.Message)
            return 2
        }
        if (-not (Assert-MtInjectForeground -Hwnd $hwnd)) {
            Write-MtInjectLine 'MT_INJECT_FOCUS: FAIL — 目标窗口无法置前台，按键会被游戏忽略'
            Write-MtErrLine 'MT_INJECT: ERROR — 目标窗口未取得前台，拒绝注入（失焦时 GLFW 会丢弃按键）'
            return 2
        }
        Write-MtInjectLine 'MT_INJECT_FOCUS: OK'
    }

    # 2026-09-18（t18）会话期 Esc 保护：`key` 子命令**从不**做 Esc→Tab→Enter 归一化
    # （只有 `cmd` 子命令会，见 Invoke-MtInjectCmdCommand 的归一化段），它按下的键就是调用方
    # 要的那一个（含 `--key cancel` = 刻意按一次真实 Esc）。故这里的 `-NoEsc` 不是开关，
    # 而是把「本步不得引入归一化 Esc」这一声明**回显出来**，让用例输出可取证（否则只能读源码）。
    if ($NoEsc) {
        Write-MtInjectLine 'ESC_SKIP: key 子命令无 Esc 归一化 ⇒ -NoEsc 声明成立（按下的键就是本步输入）'
    }

    $k = $Key.ToLowerInvariant()
    if ($script:KeyAlias.ContainsKey($k)) { $k = $script:KeyAlias[$k] }

    if ($k -eq 'attack' -or $k -eq 'rclick' -or $k -eq 'shift-rclick') {
        Send-MtInjectMouseCenter -Hwnd $hwnd `
            -Right ($k -eq 'rclick' -or $k -eq 'shift-rclick') -Shift ($k -eq 'shift-rclick') `
            -HoldMs $HoldMs
        $held = if ($HoldMs -gt 0) { " 按住 ${HoldMs}ms" } else { '' }
        Write-MtInjectLine ('MT_INJECT_KEY: {0} (窗口中心){1}' -f $k, $held)
        return 0
    }

    if ($k -eq 'w') {
        $ms = if ($HoldMs -gt 0) { $HoldMs } else { 1000 }
        Send-MtInjectKeyDown -Hwnd $hwnd -Vk $script:Vk['w'] -Scan $script:Scan['w']
        Start-MtInjectPause -Milliseconds $ms
        Send-MtInjectKeyUp -Hwnd $hwnd -Vk $script:Vk['w'] -Scan $script:Scan['w']
        Write-MtInjectLine ("MT_INJECT_KEY: w 按住 ${ms}ms")
        return 0
    }

    if (-not $script:Vk.ContainsKey($k)) {
        # python: f"MT_INJECT: ERROR — 未知按键 {key}" —— 用**原始**入参，不是小写化后的 k
        Write-MtErrLine ("MT_INJECT: ERROR — 未知按键 $Key")
        return 2
    }

    Send-MtInjectKey -Hwnd $hwnd -Vk $script:Vk[$k] -Scan $script:Scan[$k]
    Write-MtInjectLine ("MT_INJECT_KEY: $k")
    return 0
}

function Invoke-MtInjectMouseCommand {
    <#
    .SYNOPSIS
        `mouse` 子命令：在窗口中心投递一次鼠标输入（左键 / 右键[+潜行] / 滚轮）。

    .NOTES
        为什么需要：目标选择器的按键语义里「确认」= **左键**、「自用/取消」= **右键**
        （或右键 + 潜行），而此前的 `key` 子命令只有 `attack` / `rclick` / `shift-rclick`
        三个语义别名 —— 没有可读的「左键」写法，也没有独立的鼠标入口。本函数把
        `Send-MtInjectMouseCenter` 暴露成子命令，**不新造第二条注入路径**（同一函数、
        同一坐标口径、同一 sendinput/postmessage 分支）。

        `--button wheel`（2026-09-18 t23 新增）走 `Send-MtInjectWheel`：`-Notches` 格、
        正 = 向上滚、缺省 -1 = 向下滚一格。用途 = 验证「选择期间滚轮拦截」——配合探针
        `AP_<tag>_DIAG:selectedSlot=` 读数做「会话中不变 / 取消后变化」两步断言（t12 F3）。
        wheel 不接受 `--shift`（无意义的组合，宁可报错也不要静默忽略）。

        `--button` 在**定位窗口之前**校验：客户端未运行 / 未启动时，非法值仍给出可读的
        `MT_ERROR: 非法 --button 值 <x>（可选：left right wheel）` + rc=2，而不是被
        「客户端未在运行」掩盖掉（任务自检项）。
    #>
    [CmdletBinding()]
    param([string]$Button, [bool]$Shift, [int]$HoldMs, [string]$Version, [string]$Layout, [long]$Hwnd, [bool]$NoEsc = $false, [int]$Notches = -1)

    $b = if ($null -eq $Button) { '' } else { $Button.Trim().ToLowerInvariant() }
    if ($b -ne 'left' -and $b -ne 'right' -and $b -ne 'wheel') {
        Write-MtErrorLine ("非法 --button 值 {0}（可选：left right wheel）" -f $Button)
        return 2
    }
    if ($b -eq 'wheel') {
        if ($Shift) {
            Write-MtErrorLine 'wheel 不接受 --shift（滚轮无修饰键语义；要潜行请用 left/right）'
            return 2
        }
        if ($Notches -eq 0 -or $Notches -lt -32 -or $Notches -gt 32) {
            Write-MtErrorLine ("非法 --notches 值 {0}（非 0 且 |n| ≤ 32）" -f $Notches)
            return 2
        }
    }

    $hwnd = Resolve-MtInjectWindow -Hwnd $Hwnd -Version $Version
    if (-not $hwnd) { return 2 }

    if (-not $script:DryRun) {
        $r = Get-MtInjectLayoutReady -Hwnd $hwnd -Layout $Layout
        $tag = if ($r.Ok) { 'OK' } else { 'FAIL' }
        Write-MtInjectLine ('MT_INJECT_LAYOUT: {0} — {1}' -f $tag, $r.Message)
        if (-not $r.Ok) {
            Write-MtErrLine ('MT_INJECT: ERROR — 输入语言未就绪，拒绝注入（{0}）' -f $r.Message)
            return 2
        }
        if (-not (Assert-MtInjectForeground -Hwnd $hwnd)) {
            Write-MtInjectLine 'MT_INJECT_FOCUS: FAIL — 目标窗口无法置前台，鼠标会被游戏忽略'
            Write-MtErrLine 'MT_INJECT: ERROR — 目标窗口未取得前台，拒绝注入（失焦时 GLFW 会丢弃按键）'
            return 2
        }
        Write-MtInjectLine 'MT_INJECT_FOCUS: OK'
    }

    # 2026-09-18（t18）：鼠标路径只有 shift/光标/左右键（Send-MtInjectMouseCenter），**没有**
    # Esc 归一化 ⇒ `-NoEsc` 对它是「声明成立」的回显（理由同 key 子命令处）。
    if ($NoEsc) {
        Write-MtInjectLine 'ESC_SKIP: mouse 子命令无 Esc 归一化 ⇒ -NoEsc 声明成立（只发鼠标键）'
    }

    if ($b -eq 'wheel') {
        Send-MtInjectWheel -Hwnd $hwnd -Notches $Notches
        Write-MtInjectLine ('MT_INJECT_MOUSE: wheel notches={0}{1} (窗口中心)' -f $Notches, $(if ($script:Transport -eq 'sendinput') { ' transport=sendinput' } else { ' transport=postmessage' }))
        return 0
    }

    Send-MtInjectMouseCenter -Hwnd $hwnd -Right ($b -eq 'right') -Shift $Shift -HoldMs $HoldMs
    $shiftTag = if ($Shift) { ' +shift' } else { '' }
    $held = if ($HoldMs -gt 0) { " 按住 ${HoldMs}ms" } else { '' }
    Write-MtInjectLine ('MT_INJECT_MOUSE: {0}{1} (窗口中心){2}' -f $b, $shiftTag, $held)
    return 0
}

function Invoke-MtInjectCmdCommand {
    [CmdletBinding()]
    param([string]$Command, [bool]$NoEsc, [string]$Version, [string]$Layout, [long]$Hwnd)

    $hwnd = Resolve-MtInjectWindow -Hwnd $Hwnd -Version $Version
    if (-not $hwnd) { return 2 }

    if (-not $script:DryRun) {
        $r = Get-MtInjectLayoutReady -Hwnd $hwnd -Layout $Layout
        $tag = if ($r.Ok) { 'OK' } else { 'FAIL' }
        Write-MtInjectLine ('MT_INJECT_LAYOUT: {0} — {1}' -f $tag, $r.Message)
        if (-not $r.Ok) {
            Write-MtErrLine ('MT_INJECT: ERROR — 输入语言未就绪，拒绝注入（{0}）' -f $r.Message)
            return 2
        }
        if (-not (Assert-MtInjectForeground -Hwnd $hwnd)) {
            Write-MtInjectLine 'MT_INJECT_FOCUS: FAIL — 目标窗口无法置前台，按键会被游戏忽略'
            Write-MtErrLine 'MT_INJECT: ERROR — 目标窗口未取得前台，拒绝注入（失焦时 GLFW 会丢弃按键）'
            return 2
        }
        Write-MtInjectLine 'MT_INJECT_FOCUS: OK'
    }

    # 目标选择会话激活期间 Esc 会取消选择，此时必须 -NoEsc（斜杠命令已被白名单放行）。
    #
    # ⚠️ sendinput 通道**默认不做**这步归一化（2026-09-13 实测）：旧实现每次注入先盲发
    # `Esc, Esc`，而 Esc 对暂停菜单是**开关**——真实输入下若 T 尚未把聊天打开（客户端一帧延迟、
    # 或窗口刚获得焦点），紧随的 Esc 就会打开暂停菜单；暂停后 `Minecraft.pause=true` →
    # `IntegratedServer` 停止 tick（日志 `Saving and pausing game...`），聊天键再也打不开聊天、
    # 整轮用例的命令全部静默丢失（断言「未命中」而 KUBEJS/CRASH/MIXIN 全绿）。
    # 改为 T+Esc 也挡不住这种竞态。真实输入下**根本不需要**归一化：每条命令末尾的 Enter
    # 本来就会关掉聊天，终端状态恒为「无界面」；万一有残留界面，用
    # `mt_inject.ps1 key --key cancel`（单次真实 Esc）显式关闭即可。
    # 需要旧行为时用 --esc-normalize 强制开启（仅排查用）。
    $escNormalize = ((-not $NoEsc) -and ($script:Transport -ne 'sendinput')) -or $script:EscNormalize
    if ($escNormalize) {
        Send-MtInjectKey -Hwnd $hwnd -Vk $script:Vk['t'] -Scan $script:Scan['t']
        Start-MtInjectPause -Milliseconds 150
        Send-MtInjectKey -Hwnd $hwnd -Vk $script:Vk['escape'] -Scan $script:Scan['escape']
        Start-MtInjectPause -Milliseconds 200
    }

    # 打开聊天并输入命令。
    # 2026-09-13 更正：「按 `/` 键会把游戏顶进暂停菜单」属**误判**——真实原因是入口把
    # `$script:Transport` 覆盖成空串，使 escNormalize 恒为真、每次注入先发一次真实 Esc
    # （完整推演见下方参数解析段的注释）。那条 `/` 观测是在「Esc 已经打开菜单」的前提下取得的，
    # 归因错了。sendinput 通道本来就不按 `/` 键，而是走与 computer-control MCP 同款的
    # 「按 T 开聊天 → 输入含前导 `/` 的全文 → 回车」。
    #
    # ── 状态归一化：Esc → Tab → Enter（2026-09-13 实机逐一验证，必须遵守）────────
    # 目的：把界面收敛到「无界面且未暂停」，随后 T 才能真正打开聊天。
    # 为什么是这三键（穷举起始状态，全部实测）：
    #   · 无界面       → Esc 打开暂停菜单 → Tab 聚焦首个按钮「回到游戏」→ Enter 激活 → 回到无界面
    #                    （**单按 Enter 点不掉暂停菜单**：1.21.1 GameMenuScreen 初始无聚焦控件）
    #   · 暂停菜单开着 → Esc 关掉 → Tab/Enter 无控件可作用、无副作用
    #   · 容器 GUI 开着 → Esc 关掉容器。这是**唯一**能关容器的安全键：E 会开关背包、T 与 / 在容器里
    #                    无绑定，都无法收敛状态；而容器 GUI 不关掉 ⇒ T 打不开聊天 ⇒ 命令被 GUI 吞掉
    #   · 聊天开着     → Esc 关聊天 → Tab/Enter 无副作用
    # 反面教材（都实测踩过，禁止改回去）：
    #   · 只按 Enter：容器关不掉、暂停菜单点不掉 ⇒ 从容器界面起「全部后续命令零输出」。
    #   · Esc×2：Esc 是暂停菜单**开关**，奇偶性取决于起始状态，可能把菜单顶开。
    #   · 只按 Esc：无界面时会**打开**暂停菜单（暂停 + 存盘 + 停止 tick）。
    # 代价：无界面时每条命令走一次「开菜单→关菜单」，日志多一条 Saving and pausing 并伴一次存盘
    # （几十毫秒）。这是换取「任意起始状态都能注入」的代价，属**预期行为**——不要再把
    # Saving and pausing 当作「暂停菜单被顶开」的故障信号（那条旧判据已作废）。
    # ── 2026-09-17 全局测试规则：pause-lock 生效时**删除这段不必要的 Esc 按键** ─────────────
    # 用户裁决原文：「如检测到已使用禁止失焦ESC菜单命令，则从测试流程中删除不必要的ESC按键操作」。
    # 判据 = `run/<版本>/options.txt` 的 `pauseOnLostFocus:false`（mt_launch 每次冷启动前强制写入，
    # 见 `mt_env.ps1 debug --pause-lock status`）。`-NoEsc` 表示调用方明确要求本次注入不要动 Esc
    # （例如目标选择会话：Esc 会取消选择）。两者同时成立 ⇒ 跳过整段 Esc→Tab→Enter 归一化：
    # 它存在的唯一理由是清掉「失焦自动弹出的暂停菜单」，而 pause-lock 已让该菜单不再自动出现；
    # 终端状态由每条命令末尾的 Enter 保证为「无界面」。⚠️ 真正需要清空**容器/聊天**界面的场景
    # （唯一必须按 Esc 的情形，见上方穷举）请显式去掉 -NoEsc 或用 `--esc-normalize`，行为不变。
    $pauseLocked = $false
    try { $pauseLocked = ((Get-MtPauseOnLostFocus -Paths (Get-MtPaths -Version $Version)) -eq $false) } catch { $pauseLocked = $false }
    if ($NoEsc -and $pauseLocked) {
        Write-MtInjectLine 'ESC_SKIP: pause-lock 生效 + -NoEsc ⇒ 跳过 Esc→Tab→Enter 归一化（失焦不再打开 ESC 暂停菜单）'
    } else {
        Send-MtInjectKey -Hwnd $hwnd -Vk $script:Vk['escape'] -Scan $script:Scan['escape']
        Start-MtInjectPause -Milliseconds 350
        Send-MtInjectKey -Hwnd $hwnd -Vk $script:Vk['tab'] -Scan $script:Scan['tab']
        Start-MtInjectPause -Milliseconds 150
        Send-MtInjectKey -Hwnd $hwnd -Vk $script:Vk['enter'] -Scan $script:Scan['enter']
        Start-MtInjectPause -Milliseconds 450
    }

    Send-MtInjectKey -Hwnd $hwnd -Vk $script:Vk['t'] -Scan $script:Scan['t']
    Start-MtInjectPause -Milliseconds 800

    $text = $Command
    if ($script:Transport -eq 'sendinput') {
        # 真实文本输入：整串一次 SendInput(KEYEVENTF_UNICODE)，保留前导 `/`
        [void](Send-MtRealText -Text $text)
    } else {
        # 旧路径：`/` 键自带前缀，故剥掉前导 `/`，再按**码点**遍历
        # （python `for ch in text` 的语义，不是 UTF-16 码元）
        if ($text.StartsWith('/')) { $text = $text.Substring(1) }
        Send-MtInjectKey -Hwnd $hwnd -Vk $script:Vk['slash'] -Scan $script:Scan['slash']
        Start-MtInjectPause -Milliseconds 800
        $i = 0
        while ($i -lt $text.Length) {
            $cp = [char]::ConvertToUtf32($text, $i)
            if ([char]::IsHighSurrogate($text[$i])) { $i += 2 } else { $i += 1 }
            Send-MtInjectMessage -Hwnd $hwnd -Msg $script:WM_CHAR -WParam $cp -LParam 1
        }
    }

    Start-MtInjectPause -Milliseconds 300
    Send-MtInjectKey -Hwnd $hwnd -Vk $script:Vk['enter'] -Scan $script:Scan['enter']
    Write-MtInjectLine ("MT_INJECT_CMD: $Command")
    return 0
}

# ══ 入口：参数解析（本仓入口脚本统一约定：手写 $args 循环，不用 param()）═══

function ConvertTo-MtArgLong {
    <#
    .SYNOPSIS
        解析整数型选项值（非整数时报 `MT_ERROR:` 并退出 2，与 argparse 一致）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$Raw, [Parameter(Mandatory)][string]$Name)

    $v = [long]0
    if (-not [long]::TryParse($Raw, [ref]$v)) {
        Write-MtErrorLine "参数 $Name 需要整数，收到 $Raw"
        exit $MT_EXIT_ERROR
    }
    return $v
}

$Mode = ''
$Key = ''
$Button = ''
$Shift = $false
$HoldMs = 0
$Notches = -1
$Command = ''
$NoEsc = $false
$Version = ''
$Layout = 'auto'
$Hwnd = [long]0
$DryRun = $false
# ⚠️ 这两个 CLI 解析变量**必须**带 Arg 后缀（2026-09-13 踩坑）：脚本顶层 `$X = ...` 与
# `$script:X` **是同一个变量**，早先写作 `$Transport = ''` / `$EscNormalize = $false`，会把上方
# 第 106/109 行设置的 `$script:Transport = 'sendinput'` / `$script:EscNormalize = $false` 直接
# 覆盖成空串 —— 结果是「默认走 sendinput」形同虚设：实际落进 postmessage 分支，且
# `$escNormalize` 因 `'' -ne 'sendinput'` 变为 **$true**，每条命令注入前先发一次真实 Esc，
# 把暂停菜单顶开（日志 `Saving and pausing game...`），后续按键与命令全部丢进菜单里。
# 症状极具误导性：脚本仍打印 MT_INJECT_CMD 成功，游戏里却毫无输出。
$TransportArg = ''
$EscNormalizeArg = $false

$i = 0
while ($i -lt $args.Count) {
    $tok = [string]$args[$i]
    if (-not $tok.StartsWith('-')) {
        if (-not $Mode) { $Mode = $tok; $i++; continue }
        Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
    }
    $optName = $tok.TrimStart('-').ToLowerInvariant().Replace('-', '')
    if ($optName -eq 'key') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --key 的值'; exit $MT_EXIT_ERROR }
        $Key = [string]$args[$i + 1]; $i += 2
    } elseif ($optName -eq 'button') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --button 的值'; exit $MT_EXIT_ERROR }
        $Button = [string]$args[$i + 1]; $i += 2
    } elseif ($optName -eq 'shift') {
        # 标志位（与 --no-esc / --esc-normalize 同形）：只对 mouse 子命令有意义
        $Shift = $true; $i++
    } elseif ($optName -eq 'command') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --command 的值'; exit $MT_EXIT_ERROR }
        $Command = [string]$args[$i + 1]; $i += 2
    } elseif ($optName -eq 'holdms') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --hold-ms 的值'; exit $MT_EXIT_ERROR }
        $HoldMs = [int](ConvertTo-MtArgLong -Raw ([string]$args[$i + 1]) -Name '--hold-ms'); $i += 2
    } elseif ($optName -eq 'notches') {
        # 滚轮格数（2026-09-18 t23）：正 = 向上滚、负 = 向下滚；只对 mouse --button wheel 有意义
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --notches 的值'; exit $MT_EXIT_ERROR }
        $Notches = [int](ConvertTo-MtArgLong -Raw ([string]$args[$i + 1]) -Name '--notches'); $i += 2
    } elseif ($optName -eq 'version') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --version 的值'; exit $MT_EXIT_ERROR }
        $Version = [string]$args[$i + 1]; $i += 2
    } elseif ($optName -eq 'layout') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --layout 的值'; exit $MT_EXIT_ERROR }
        $Layout = [string]$args[$i + 1]; $i += 2
    } elseif ($optName -eq 'hwnd') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --hwnd 的值'; exit $MT_EXIT_ERROR }
        $Hwnd = ConvertTo-MtArgLong -Raw ([string]$args[$i + 1]) -Name '--hwnd'; $i += 2
    } elseif ($optName -eq 'noesc') {
        $NoEsc = $true; $i++
    } elseif ($optName -eq 'transport') {
        if ($i + 1 -ge $args.Count) { Write-MtErrorLine '缺少 --transport 的值'; exit $MT_EXIT_ERROR }
        $TransportArg = [string]$args[$i + 1]; $i += 2
    } elseif ($optName -eq 'escnormalize') {
        $EscNormalizeArg = $true; $i++
    } elseif ($optName -eq 'dryrun') {
        $DryRun = $true; $i++
    } else {
        Write-MtErrorLine "未知参数 $tok"; exit $MT_EXIT_ERROR
    }
}

$script:DryRun = [bool]$DryRun

$modeName = if ($null -eq $Mode) { '' } else { $Mode.Trim() }

# python: --layout 走 argparse choices=["auto","as-is"]；手工校验，退出码对齐 2
if ($Layout -ne 'auto' -and $Layout -ne 'as-is') {
    Write-MtErrorLine ("非法 -Layout 值 {0}（可选：auto as-is）" -f $Layout)
    exit $MT_EXIT_ERROR
}

# 投递通道：默认 sendinput（真实键鼠）；--transport postmessage 可切回旧路径排查用
if ($TransportArg) {
    $t = $TransportArg.Trim().ToLowerInvariant()
    if ($t -ne 'sendinput' -and $t -ne 'postmessage') {
        Write-MtErrorLine ("非法 --transport 值 {0}（可选：sendinput postmessage）" -f $TransportArg)
        exit $MT_EXIT_ERROR
    }
    $script:Transport = $t
}

if ($EscNormalizeArg) { $script:EscNormalize = $true }

switch ($modeName) {
    'key' {
        if (-not $Key) {
            Write-MtErrorLine '缺少必填参数 --key'
            exit $MT_EXIT_ERROR
        }
        $rc = Invoke-MtInjectKeyCommand -Key $Key -HoldMs $HoldMs -Version $Version -Layout $Layout -Hwnd $Hwnd `
            -NoEsc ([bool]$NoEsc)
    }
    'cmd' {
        if (-not $Command) {
            Write-MtErrorLine '缺少必填参数 --command'
            exit $MT_EXIT_ERROR
        }
        $rc = Invoke-MtInjectCmdCommand -Command $Command -NoEsc ([bool]$NoEsc) -Version $Version -Layout $Layout -Hwnd $Hwnd
    }
    'mouse' {
        if (-not $Button) {
            Write-MtErrorLine '缺少必填参数 --button（可选：left right wheel）'
            exit $MT_EXIT_ERROR
        }
        # --button 的取值校验放在 Invoke-MtInjectMouseCommand 首行（早于窗口定位），
        # 这样客户端未运行时也能得到「非法 --button 值」而不是「客户端未在运行」。
        $rc = Invoke-MtInjectMouseCommand -Button $Button -Shift ([bool]$Shift) -HoldMs $HoldMs `
            -Version $Version -Layout $Layout -Hwnd $Hwnd -NoEsc ([bool]$NoEsc) -Notches $Notches
    }
    default {
        if (-not $modeName) {
            Write-MtErrorLine '缺少子命令（可选：key cmd mouse）'
        } else {
            Write-MtErrorLine ("未知子命令 {0}（可选：key cmd mouse）" -f $modeName)
        }
        exit $MT_EXIT_ERROR
    }
}

if ($script:DryRun) { Write-MtLine ("MT_INJECT_DRYRUN_RC: {0}" -f $rc) }
exit $rc

