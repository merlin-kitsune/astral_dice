<#
Mt.Win32.psm1 — Win32 输入 / 截图的原生层（user32/kernel32/gdi32 的 P/Invoke 单一来源）。

对应源文件：scripts/test/mt_ime.py、scripts/test/mt_inject.py、scripts/test/mt_capture.py
里散落的 ctypes 调用。三个脚本共用同一份声明，避免「同一 API 三种写法」。

## 为什么用一份内联 C# 而不是 Add-Type -MemberDefinition

  - 需要一个**类型**承载回调（窗口过程）：`-MemberDefinition` 只能挂静态方法，
    没法声明 `[UnmanagedFunctionPointer]` 委托与结构体（WNDCLASSEXW / MSG / RECT）；
  - 结构体 + 字符串字段的 marshalling 写在 C# 里一次即可，PS 侧只做薄包装；
  - 一处 `Add-Type` 编译，三处共用，类型名唯一（`Mt.Win32.Native`）。

## Add-Type 幂等（必须）

同一个 pwsh 会话里重复 `Add-Type` 同名类型会**报错**。因此：
  1. 先探测 `'Mt.Win32.Native' -as [type]`，已存在就完全跳过编译；
  2. 万一竞态导致编译期抛「类型已存在」，catch 后再探测一次，存在即视为成功。
这样 `Import-Module -Force` 反复导入也不会炸（模块被重载，但类型留在 AppDomain 里）。

## 委托保活（必须）

`RegisterMessageClass` 把托管委托的函数指针交给 Win32 当窗口过程。若不把委托实例
存进**静态字段**，GC 一旦回收它，窗口过程就变成野指针 —— 表现为「窗口建出来了，
但消息泵跑一会儿就崩 / 消息再也不被处理」。故 `Native.KeepAliveWndProc` 是必需的。

## 结构体出参一律走 C# 助手

`GetWindowRect(out RECT)` / `GetWindowThreadProcessId(..., out uint)` 这类出参不直接用
PowerShell 的 `[ref]` + PSObject 装箱结构体传递（容易踩装箱/装箱类型不匹配的坑），
改为在 C# 里包一层返回 `int[]` / `uint`。PS 侧只接原始类型。

## DPI（与 Pillow 对齐，本机实测结论）

本机 3840x2160 物理屏 / 150% 缩放：pwsh 与 python 都是 DPI-**unaware** 进程，
`GetSystemMetrics` / `GetDeviceCaps(HORZRES)` 都返回虚拟化后的 2560x1440；
而 Pillow 的 `ImageGrab.grab()` 抓出来是 **3840x2160**。
原因（反查 _imaging.pyd 的导入表确认）：Pillow 的 grabscreen_win32 内部调了
`SetThreadDpiAwarenessContext`，于是抓的是**物理像素**。
因此本模块的截图路径也必须临时把**当前线程**提升到 PER_MONITOR_AWARE_V2，
否则尺寸与 Pillow 版不一致（差 1.5 倍）。见 Set-MtThreadDpiAwareness / Get-MtScreenSize。
#>

$script:MtWin32CSharp = @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;

namespace Mt.Win32
{
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT
    {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct POINT
    {
        public int X;
        public int Y;
    }

    // 与 ctypes 的 wintypes.MSG 同布局（x64 下 48 字节）
    [StructLayout(LayoutKind.Sequential)]
    public struct MSG
    {
        public IntPtr hwnd;
        public uint message;
        public IntPtr wParam;
        public IntPtr lParam;
        public uint time;
        public POINT pt;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct WNDCLASSEXW
    {
        public uint cbSize;
        public uint style;
        public IntPtr lpfnWndProc;
        public int cbClsExtra;
        public int cbWndExtra;
        public IntPtr hInstance;
        public IntPtr hIcon;
        public IntPtr hCursor;
        public IntPtr hbrBackground;
        [MarshalAs(UnmanagedType.LPWStr)] public string lpszMenuName;
        [MarshalAs(UnmanagedType.LPWStr)] public string lpszClassName;
        public IntPtr hIconSm;
    }

    public delegate IntPtr WndProcDelegate(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    public static class Native
    {
        public const uint PM_REMOVE = 0x0001;
        public const int SM_CXSCREEN = 0;
        public const int SM_CYSCREEN = 1;
        public const int HORZRES = 8;
        public const int VERTRES = 10;

        // ── user32：消息投递 ──────────────────────────────────────────
        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern IntPtr PostMessageW(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam);

        // ── user32：窗口 / 前台 ───────────────────────────────────────
        [DllImport("user32.dll", SetLastError = true)]
        public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

        [DllImport("user32.dll", SetLastError = true)]
        public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);

        [DllImport("user32.dll", SetLastError = true)]
        public static extern bool BringWindowToTop(IntPtr hWnd);

        [DllImport("user32.dll")]
        public static extern IntPtr GetForegroundWindow();

        [DllImport("user32.dll", SetLastError = true)]
        public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

        [DllImport("user32.dll", SetLastError = true)]
        public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);

        [DllImport("user32.dll", SetLastError = true)]
        public static extern bool SetForegroundWindow(IntPtr hWnd);

        [DllImport("user32.dll")]
        public static extern bool IsWindowVisible(IntPtr hWnd);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        public static extern int GetWindowTextLengthW(IntPtr hWnd);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        public static extern int GetWindowTextW(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

        [DllImport("user32.dll", SetLastError = true)]
        public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);

        [DllImport("user32.dll", SetLastError = true)]
        public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);

        [DllImport("user32.dll")]
        public static extern int GetSystemMetrics(int nIndex);

        // ⚠️ 只影响**调用线程**，可还原；Pillow 抓屏走的就是这条路（见文件头 DPI 说明）
        [DllImport("user32.dll", SetLastError = true)]
        public static extern IntPtr SetThreadDpiAwarenessContext(IntPtr dpiContext);

        // ── user32：键盘布局（输入语言，作用于**线程**）────────────────
        [DllImport("user32.dll", SetLastError = true)]
        public static extern int GetKeyboardLayoutList(int nBuff, [Out] IntPtr[] lpList);

        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern IntPtr LoadKeyboardLayoutW(string pwszKLID, uint Flags);

        [DllImport("user32.dll")]
        public static extern IntPtr GetKeyboardLayout(uint idThread);

        [DllImport("user32.dll", SetLastError = true)]
        public static extern IntPtr ActivateKeyboardLayout(IntPtr hkl, uint Flags);

        // ── user32：窗口类 / 消息泵（mt_ime _hostwin 的自检宿主）───────
        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern ushort RegisterClassExW(ref WNDCLASSEXW lpwcx);

        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern IntPtr CreateWindowExW(uint dwExStyle, string lpClassName, string lpWindowName,
            uint dwStyle, int x, int y, int nWidth, int nHeight,
            IntPtr hWndParent, IntPtr hMenu, IntPtr hInstance, IntPtr lpParam);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        public static extern IntPtr DefWindowProcW(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        public static extern bool PeekMessageW(out MSG lpMsg, IntPtr hWnd, uint wMsgFilterMin, uint wMsgFilterMax, uint wRemoveMsg);

        [DllImport("user32.dll")]
        public static extern bool TranslateMessage(ref MSG lpMsg);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        public static extern IntPtr DispatchMessageW(ref MSG lpMsg);

        [DllImport("user32.dll", CharSet = CharSet.Unicode)]
        public static extern int GetMessageW(out MSG lpMsg, IntPtr hWnd, uint wMsgFilterMin, uint wMsgFilterMax);

        [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern bool UnregisterClassW(string lpClassName, IntPtr hInstance);

        [DllImport("user32.dll", SetLastError = true)]
        public static extern bool DestroyWindow(IntPtr hWnd);

        // ── kernel32 ─────────────────────────────────────────────────
        [DllImport("kernel32.dll")]
        public static extern uint GetCurrentThreadId();

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern IntPtr GetModuleHandleW(string lpModuleName);

        // ── gdi32：屏幕尺寸（与 Pillow 的 GetDeviceCaps 同源）─────────
        [DllImport("gdi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern IntPtr CreateDCW(string pwszDriver, string pwszDevice, string pszPort, IntPtr pdm);

        [DllImport("gdi32.dll")]
        public static extern int GetDeviceCaps(IntPtr hdc, int nIndex);

        [DllImport("gdi32.dll")]
        public static extern bool DeleteDC(IntPtr hdc);

        // ══ 复合助手（出参不出 C#，PS 侧只接原始类型）═════════════════

        // ⚠️ 静态字段 = 委托保活。删掉它会让窗口过程失效（见文件头说明）。
        public static WndProcDelegate KeepAliveWndProc;

        public static IntPtr DefaultWndProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam)
        {
            return DefWindowProcW(hWnd, msg, wParam, lParam);
        }

        public static bool RegisterMessageClass(string className, IntPtr hInstance)
        {
            WndProcDelegate proc = new WndProcDelegate(DefaultWndProc);
            KeepAliveWndProc = proc;
            WNDCLASSEXW wc = new WNDCLASSEXW();
            wc.cbSize = (uint)Marshal.SizeOf(typeof(WNDCLASSEXW));
            wc.style = 0;
            wc.lpfnWndProc = Marshal.GetFunctionPointerForDelegate(proc);
            wc.cbClsExtra = 0;
            wc.cbWndExtra = 0;
            wc.hInstance = hInstance;
            wc.hIcon = IntPtr.Zero;
            wc.hCursor = IntPtr.Zero;
            wc.hbrBackground = IntPtr.Zero;
            wc.lpszMenuName = null;
            wc.lpszClassName = className;
            wc.hIconSm = IntPtr.Zero;
            return RegisterClassExW(ref wc) != 0;
        }

        public static IntPtr CreateMessageWindow(string className, string title, int width, int height, IntPtr hInstance)
        {
            // 与 mt_ime._hostwin 完全一致的参数：ExStyle=0, style=0, (0,0), w x h
            return CreateWindowExW(0, className, title, 0, 0, 0, width, height,
                                   IntPtr.Zero, IntPtr.Zero, hInstance, IntPtr.Zero);
        }

        // PeekMessage(PM_REMOVE) + TranslateMessage + DispatchMessage 的完整一趟
        public static int PumpPendingMessages()
        {
            int n = 0;
            MSG msg;
            while (PeekMessageW(out msg, IntPtr.Zero, 0, 0, PM_REMOVE))
            {
                TranslateMessage(ref msg);
                DispatchMessageW(ref msg);
                n++;
            }
            return n;
        }

        public static string GetWindowTitleText(IntPtr hWnd)
        {
            int len = GetWindowTextLengthW(hWnd);
            if (len <= 0) return "";
            StringBuilder sb = new StringBuilder(len + 1);
            GetWindowTextW(hWnd, sb, sb.Capacity);
            return sb.ToString();
        }

        public static IntPtr[] GetAllTopLevelWindows()
        {
            List<IntPtr> list = new List<IntPtr>();
            EnumWindowsProc cb = delegate(IntPtr h, IntPtr l) { list.Add(h); return true; };
            EnumWindows(cb, IntPtr.Zero);
            GC.KeepAlive(cb);   // 回调期间不能被回收
            return list.ToArray();
        }

        // [left, top, right, bottom]
        public static int[] GetWindowRectArray(IntPtr hWnd)
        {
            RECT r;
            if (!GetWindowRect(hWnd, out r)) return new int[] { 0, 0, 0, 0 };
            return new int[] { r.Left, r.Top, r.Right, r.Bottom };
        }

        public static uint GetWindowThreadIdOnly(IntPtr hWnd)
        {
            uint pid;
            return GetWindowThreadProcessId(hWnd, out pid);
        }

        public static uint GetWindowProcessIdOnly(IntPtr hWnd)
        {
            uint pid;
            GetWindowThreadProcessId(hWnd, out pid);
            return pid;
        }

        // [width, height]，与 Pillow grabscreen_win32 的取值口径一致
        // （调用前请先把线程提到 PER_MONITOR_AWARE_V2，否则拿到的是虚拟化尺寸）
        public static int[] GetPrimaryScreenSize()
        {
            int[] wh = new int[2];
            IntPtr hdc = CreateDCW("DISPLAY", null, null, IntPtr.Zero);
            if (hdc != IntPtr.Zero)
            {
                try
                {
                    wh[0] = GetDeviceCaps(hdc, HORZRES);
                    wh[1] = GetDeviceCaps(hdc, VERTRES);
                }
                finally
                {
                    DeleteDC(hdc);
                }
            }
            if (wh[0] <= 0 || wh[1] <= 0)
            {
                wh[0] = GetSystemMetrics(SM_CXSCREEN);
                wh[1] = GetSystemMetrics(SM_CYSCREEN);
            }
            return wh;
        }
    }
}
'@

# ── Add-Type（幂等；见文件头）────────────────────────────────────────────
if (-not ('Mt.Win32.Native' -as [type])) {
    try {
        [void](Add-Type -TypeDefinition $script:MtWin32CSharp -Language CSharp -ErrorAction Stop)
    } catch {
        # 「类型已存在」= 目标已达成；只有真的没有该类型才把异常抛出去
        if (-not ('Mt.Win32.Native' -as [type])) { throw }
    }
}

# ⚠️ 这里的嵌套导入必须带 **-Global**：模块顶层代码在**模块会话状态**里执行，
#    不带 -Global 时 Mt.Paths/Mt.Proc 只会被导进 Mt.Win32 自己的作用域；
#    更糟的是 `-Force` 会**卸载**先前由入口脚本全局导入的那一份，
#    导致入口脚本随后调用 Assert-MtVersion / Get-MtPaths 时报「术语不被识别」。
Import-Module (Join-Path $PSScriptRoot 'Mt.Paths.psm1') -Force -Global
Import-Module (Join-Path $PSScriptRoot 'Mt.Proc.psm1') -Force -Global

# ── 常量（与 python 侧逐字一致）──────────────────────────────────────────
$script:LANGID_EN_US = 0x0409
$script:KLID_EN_US = '00000409'
$script:WM_INPUTLANGCHANGEREQUEST = 0x0050
$script:KLF_NOTELLSHELL = 0x0008
$script:DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = -4

# ── 句柄类型纪律 ────────────────────────────────────────────────────────
# 所有 HWND/HKL 在 PS 侧一律用 [long] 承载，**不要**用 [IntPtr]：
# PowerShell 的 `if ($h)` 对 [IntPtr]::Zero 判真（非空对象即真），会把「没找到窗口」
# 当成「找到了」。用 long 才有 0 = false 的自然语义（python 侧 hwnd 也是 int）。

function Get-MtModuleHandle {
    <#
    .SYNOPSIS
        当前进程的模块句柄（`GetModuleHandleW(NULL)`）。
    #>
    [CmdletBinding()]
    param()

    return [long][Mt.Win32.Native]::GetModuleHandleW($null)
}

function Set-MtThreadDpiAwareness {
    <#
    .SYNOPSIS
        设置**当前线程**的 DPI 感知上下文，返回上一个上下文（供还原）。

    .NOTES
        只影响调用线程、可随时还原 —— 这是把截图尺寸对齐 Pillow 的关键
        （Pillow 的 grabscreen_win32 同样调 SetThreadDpiAwarenessContext）。
        还原：`$old = Set-MtThreadDpiAwareness -Context -4; ...; [void](Set-MtThreadDpiAwareness -Context $old)`。

        返回值/入参必须是 **[long]**：旧上下文是真实的句柄（实测 2147508240），
        塞不进 Int32。伪句柄（-1..-4）也照原样回传。
    #>
    [CmdletBinding()]
    param([long]$Context = -4)

    return [long][Mt.Win32.Native]::SetThreadDpiAwarenessContext([IntPtr]::new($Context))
}

function Get-MtScreenSize {
    <#
    .SYNOPSIS
        主屏尺寸（**当前线程 DPI 上下文**下的口径）。
    .OUTPUTS
        PSCustomObject：Width / Height。
    #>
    [CmdletBinding()]
    param()

    $wh = [Mt.Win32.Native]::GetPrimaryScreenSize()
    return [pscustomobject]@{ Width = [int]$wh[0]; Height = [int]$wh[1] }
}

# ══ 键盘布局（输入语言）══════════════════════════════════════════════════

function Get-MtKeyboardLayouts {
    <#
    .SYNOPSIS
        当前系统已加载的键盘布局（与 python list_layouts 同义）。
    .OUTPUTS
        PSCustomObject 数组：Hkl（uint64，句柄原始值）/ LangId（int）/ Tag（KLID 形如 0409）。
    #>
    [CmdletBinding()]
    param()

    $n = [Mt.Win32.Native]::GetKeyboardLayoutList(0, $null)
    if ($n -le 0) { return @() }

    $buf = [IntPtr[]]::new($n)
    [void][Mt.Win32.Native]::GetKeyboardLayoutList($n, $buf)

    $out = @()
    foreach ($hkl in $buf) {
        $v = [uint64]$hkl.ToInt64()
        $langid = [int]($v -band 0xFFFF)
        $out += , ([pscustomobject]@{
                Hkl    = $v
                LangId = $langid
                # python: f"{langid & 0x3FF:03x}{langid >> 10:02x}"
                Tag    = ('{0:x3}{1:x2}' -f ($langid -band 0x3FF), ($langid -shr 10))
            })
    }
    return $out
}

function Test-MtEnUsLayoutAvailable {
    <#
    .SYNOPSIS
        en-US 布局是否可用（已加载或可加载）。与 python en_us_available 同义。
    #>
    [CmdletBinding()]
    param()

    foreach ($l in (Get-MtKeyboardLayouts)) {
        if ($l.LangId -eq $script:LANGID_EN_US) { return $true }
    }
    $hkl = [Mt.Win32.Native]::LoadKeyboardLayoutW($script:KLID_EN_US, $script:KLF_NOTELLSHELL)
    return ($hkl -ne [IntPtr]::Zero)
}

function Get-MtLangIdOfThread {
    <#
    .SYNOPSIS
        某线程当前的输入语言 LANGID（= GetKeyboardLayout(tid) & 0xFFFF，与 python 同义）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$ThreadId)

    $h = [Mt.Win32.Native]::GetKeyboardLayout([uint32]$ThreadId)
    return [int]([uint64]$h.ToInt64() -band 0xFFFF)
}

function Get-MtLayoutDescription {
    <#
    .SYNOPSIS
        LANGID 的可读描述（与 python describe 逐字一致）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][int]$LangId)

    switch ($LangId) {
        0x0409 { return '英语(美国) en-US' }
        0x0804 { return '中文(简体) zh-CN' }
        default { return ('LANGID 0x{0:X4}' -f $LangId) }
    }
}

function Wait-MtLangId {
    <#
    .SYNOPSIS
        轮询等待线程语言变为目标值（与 python _wait_langid 同节奏：先查再睡 50ms）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][long]$ThreadId,
        [Parameter(Mandatory)][int]$Want,
        [Parameter(Mandatory)][double]$TimeoutSec
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSec)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ((Get-MtLangIdOfThread -ThreadId $ThreadId) -eq $Want) { return $true }
        Start-Sleep -Milliseconds 50
    }
    return $false
}

function Invoke-MtLayoutSwitch {
    <#
    .SYNOPSIS
        把 hwnd 所在线程的输入语言切到指定 KLID（两条路径依次尝试，与 python
        set_window_us 的流程一致），返回结构化结果；**不做任何文案包装**。
    .OUTPUTS
        PSCustomObject：Ok（bool）/ Tid / CurLangId / NewLangId / Reason
        Reason ∈ already | msg | attach | no-tid | no-layout | failed
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][long]$Hwnd,
        [Parameter(Mandatory)][string]$Klid,
        [Parameter(Mandatory)][int]$LangId,
        [double]$TimeoutSec = 1.0
    )

    $tid = Get-MtThreadOfWindow -Hwnd $Hwnd
    if (-not $tid) {
        return [pscustomobject]@{ Ok = $false; Tid = 0; CurLangId = 0; NewLangId = 0; Reason = 'no-tid' }
    }

    $cur = Get-MtLangIdOfThread -ThreadId $tid
    if ($cur -eq $LangId) {
        return [pscustomobject]@{ Ok = $true; Tid = $tid; CurLangId = $cur; NewLangId = $cur; Reason = 'already' }
    }

    $hkl = [Mt.Win32.Native]::LoadKeyboardLayoutW($Klid, $script:KLF_NOTELLSHELL)
    if ($hkl -eq [IntPtr]::Zero) {
        return [pscustomobject]@{ Ok = $false; Tid = $tid; CurLangId = $cur; NewLangId = $cur; Reason = 'no-layout' }
    }

    # 路径 1：请窗口自己切（WM_INPUTLANGCHANGEREQUEST 交给 DefWindowProc 处理）
    [void][Mt.Win32.Native]::PostMessageW([IntPtr]::new($Hwnd), $script:WM_INPUTLANGCHANGEREQUEST, [IntPtr]::Zero, $hkl)
    if (Wait-MtLangId -ThreadId $tid -Want $LangId -TimeoutSec $TimeoutSec) {
        return [pscustomobject]@{ Ok = $true; Tid = $tid; CurLangId = $cur; NewLangId = $LangId; Reason = 'msg' }
    }

    # 路径 2：附加到目标线程后 ActivateKeyboardLayout（对忽略该消息的窗口有效）
    $myTid = [int][Mt.Win32.Native]::GetCurrentThreadId()
    $attached = [bool][Mt.Win32.Native]::AttachThreadInput([uint32]$myTid, [uint32]$tid, $true)
    try {
        if ($attached) { [void][Mt.Win32.Native]::ActivateKeyboardLayout($hkl, 0) }
    } finally {
        if ($attached) { [void][Mt.Win32.Native]::AttachThreadInput([uint32]$myTid, [uint32]$tid, $false) }
    }

    if (Wait-MtLangId -ThreadId $tid -Want $LangId -TimeoutSec $TimeoutSec) {
        return [pscustomobject]@{ Ok = $true; Tid = $tid; CurLangId = $cur; NewLangId = $LangId; Reason = 'attach' }
    }

    return [pscustomobject]@{
        Ok = $false; Tid = $tid; CurLangId = $cur
        NewLangId = (Get-MtLangIdOfThread -ThreadId $tid); Reason = 'failed'
    }
}

function Set-MtWindowUs {
    <#
    .SYNOPSIS
        把 hwnd 所在线程切到 en-US，返回 {Ok; Message}；**Message 与 mt_ime.py
        set_window_us 逐字一致**（迁移期要与 python 版逐字节比对）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd, [double]$TimeoutSec = 1.0)

    $r = Invoke-MtLayoutSwitch -Hwnd $Hwnd -Klid $script:KLID_EN_US -LangId $script:LANGID_EN_US -TimeoutSec $TimeoutSec
    switch ($r.Reason) {
        'no-tid' {
            return [pscustomobject]@{ Ok = $false; Message = "无法取得窗口 $Hwnd 的线程" }
        }
        'already' {
            return [pscustomobject]@{ Ok = $true; Message = '已是 en-US（无需切换）' }
        }
        'msg' {
            return [pscustomobject]@{ Ok = $true; Message = ('已切换 0x{0:X4} → en-US（消息路径）' -f $r.CurLangId) }
        }
        'attach' {
            return [pscustomobject]@{ Ok = $true; Message = ('已切换 0x{0:X4} → en-US（线程附加路径）' -f $r.CurLangId) }
        }
        'no-layout' {
            return [pscustomobject]@{
                Ok      = $false
                Message = 'en-US 布局未安装或无法加载 —— 请在系统设置中安装「英语(美国)」键盘后重试'
            }
        }
        default {
            return [pscustomobject]@{
                Ok      = $false
                Message = ('切换失败：线程 {0} 仍为 0x{1:X4}（窗口可能拒绝输入语言变更）' -f $r.Tid, $r.NewLangId)
            }
        }
    }
}

# ══ 窗口查询 / 前台抢占 ══════════════════════════════════════════════════

function Get-MtTopLevelWindows {
    <#
    .SYNOPSIS
        所有顶层窗口句柄（EnumWindows 全量，不做过滤）。
    #>
    [CmdletBinding()]
    param()

    $out = @()
    foreach ($h in [Mt.Win32.Native]::GetAllTopLevelWindows()) { $out += [long]$h.ToInt64() }
    return $out
}

function Test-MtWindowVisible {
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd)
    return [bool][Mt.Win32.Native]::IsWindowVisible([IntPtr]::new($Hwnd))
}

function Get-MtWindowTitleLength {
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd)
    return [int][Mt.Win32.Native]::GetWindowTextLengthW([IntPtr]::new($Hwnd))
}

function Get-MtWindowTitle {
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd)
    return [string][Mt.Win32.Native]::GetWindowTitleText([IntPtr]::new($Hwnd))
}

function Get-MtThreadOfWindow {
    <#
    .SYNOPSIS
        窗口所属线程 id（GetWindowThreadProcessId 的返回值）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd)

    return [long][Mt.Win32.Native]::GetWindowThreadIdOnly([IntPtr]::new($Hwnd))
}

function Get-MtWindowProcessId {
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd)

    return [int][Mt.Win32.Native]::GetWindowProcessIdOnly([IntPtr]::new($Hwnd))
}

function Get-MtWindowRect {
    <#
    .SYNOPSIS
        窗口矩形（当前线程 DPI 上下文下的坐标；DPI-unaware 时是虚拟化坐标，
        与 python ctypes 版行为一致）。
    .OUTPUTS
        PSCustomObject：Left / Top / Right / Bottom / Width / Height。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd)

    $r = [Mt.Win32.Native]::GetWindowRectArray([IntPtr]::new($Hwnd))
    return [pscustomobject]@{
        Left   = [int]$r[0]
        Top    = [int]$r[1]
        Right  = [int]$r[2]
        Bottom = [int]$r[3]
        Width  = [int]($r[2] - $r[0])
        Height = [int]($r[3] - $r[1])
    }
}

function Send-MtPostMessage {
    <#
    .SYNOPSIS
        PostMessageW 薄包装（wParam/lParam 用 [long]，内部转 IntPtr）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][long]$Hwnd,
        [Parameter(Mandatory)][uint32]$Msg,
        [long]$WParam = 0,
        [long]$LParam = 0
    )

    return [bool][Mt.Win32.Native]::PostMessageW(
        [IntPtr]::new($Hwnd), $Msg, [IntPtr]::new($WParam), [IntPtr]::new($LParam))
}

function Invoke-MtShowWindow {
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd, [Parameter(Mandatory)][int]$CmdShow)
    return [bool][Mt.Win32.Native]::ShowWindow([IntPtr]::new($Hwnd), $CmdShow)
}

function Set-MtWindowPos {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][long]$Hwnd,
        [Parameter(Mandatory)][long]$InsertAfter,
        [int]$X = 0, [int]$Y = 0, [int]$Cx = 0, [int]$Cy = 0,
        [Parameter(Mandatory)][uint32]$Flags
    )

    return [bool][Mt.Win32.Native]::SetWindowPos(
        [IntPtr]::new($Hwnd), [IntPtr]::new($InsertAfter), $X, $Y, $Cx, $Cy, $Flags)
}

function Invoke-MtBringWindowToTop {
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd)
    return [bool][Mt.Win32.Native]::BringWindowToTop([IntPtr]::new($Hwnd))
}

function Get-MtForegroundWindow {
    [CmdletBinding()]
    param()
    return [long][Mt.Win32.Native]::GetForegroundWindow().ToInt64()
}

function Set-MtForegroundWindow {
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd)
    return [bool][Mt.Win32.Native]::SetForegroundWindow([IntPtr]::new($Hwnd))
}

function Set-MtAttachThreadInput {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][long]$FromTid,
        [Parameter(Mandatory)][long]$ToTid,
        [Parameter(Mandatory)][bool]$Attach
    )

    return [bool][Mt.Win32.Native]::AttachThreadInput([uint32]$FromTid, [uint32]$ToTid, $Attach)
}

function Get-MtCurrentThreadId {
    [CmdletBinding()]
    param()
    return [long][Mt.Win32.Native]::GetCurrentThreadId()
}

# ══ 消息窗口 / 消息泵（mt_ime _hostwin 用）═══════════════════════════════

function Register-MtMessageClass {
    <#
    .SYNOPSIS
        注册一个窗口类，窗口过程 = DefWindowProcW（与 python _hostwin 一致）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$ClassName,
        [Parameter(Mandatory)][long]$HInstance
    )

    return [bool][Mt.Win32.Native]::RegisterMessageClass($ClassName, [IntPtr]::new($HInstance))
}

function New-MtMessageWindow {
    <#
    .SYNOPSIS
        创建一个不可见消息窗口（style=0），返回 hwnd（0 = 失败）。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$ClassName,
        [Parameter(Mandatory)][string]$Title,
        [Parameter(Mandatory)][int]$Width,
        [Parameter(Mandatory)][int]$Height,
        [Parameter(Mandatory)][long]$HInstance
    )

    $h = [Mt.Win32.Native]::CreateMessageWindow($ClassName, $Title, $Width, $Height, [IntPtr]::new($HInstance))
    return [long]$h.ToInt64()
}

function Unregister-MtMessageClass {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$ClassName,
        [Parameter(Mandatory)][long]$HInstance
    )

    return [bool][Mt.Win32.Native]::UnregisterClassW($ClassName, [IntPtr]::new($HInstance))
}

function Remove-MtWindow {
    [CmdletBinding()]
    param([Parameter(Mandatory)][long]$Hwnd)
    return [bool][Mt.Win32.Native]::DestroyWindow([IntPtr]::new($Hwnd))
}

function Invoke-MtPumpMessages {
    <#
    .SYNOPSIS
        把当前线程消息队列里的消息全部取出并派发，返回处理条数。
    #>
    [CmdletBinding()]
    param()

    return [int][Mt.Win32.Native]::PumpPendingMessages()
}

# ══ 目标客户端定位（与 mt_inject._find_window 同义）══════════════════════

function Test-MtPidIsJava {
    <#
    .SYNOPSIS
        该 pid 是否 java 进程。
    .NOTES
        对齐 python _pid_is_java 的**容错**语义：探测不到进程时不阻断，按 True 处理
        （python 那边是子 powershell 报错 → rc!=0 且无输出 → 返回 True）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][int]$ProcessId)

    $p = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $p) { return $true }
    return ([string]$p.ProcessName).ToLower().Contains('java')
}

function Find-MtMinecraftWindow {
    <#
    .SYNOPSIS
        定位本次测试的 Minecraft 客户端窗口；无法唯一确定时返回 0。

    .NOTES
        多候选是关键场景：用户自己的整合包客户端与本流程的 dev 客户端可能同时在跑，
        两个窗口标题都含 "Minecraft"。此时「取第一个」会把按键投到错误的客户端，
        产生「看起来通过」的假结论 —— 因此必须精确匹配而非猜测：

          -Version 指定 → 只认命令行匹配该版本 dev run 的进程（唯一权威依据）；
                         标记全都读不到时（FML DevLaunch 短命令行，见下方回退段），
                         回退为「标题含该版本号的唯一候选」；仍不唯一 → 返回 0；
          未指定且候选 >1 → 返回 0，由调用方报错退出，不猜。

        ⚠️ 这里的标记是 python `_find_window` 用的那三个（**裸子项目名** + run 目录
        + `run\<版本>`），**不是** Get-MtProcessMarkers 的进程标记集（后者是给
        mt_stop/kill 用的、带冒号的任务选择器）。两者判据不同，不要混用。
    #>
    [CmdletBinding()]
    param([string]$Version)

    $cands = @()
    foreach ($h in (Get-MtTopLevelWindows)) {
        if (-not (Test-MtWindowVisible -Hwnd $h)) { continue }
        if ((Get-MtWindowTitleLength -Hwnd $h) -eq 0) { continue }
        $title = Get-MtWindowTitle -Hwnd $h
        if (-not $title.Contains('Minecraft')) { continue }
        $skip = $false
        foreach ($x in @('Chrome', 'Edge', 'Wiki', 'Firefox')) {
            if ($title.Contains($x)) { $skip = $true; break }
        }
        if ($skip) { continue }

        $wpid = Get-MtWindowProcessId -Hwnd $h
        if (Test-MtPidIsJava -ProcessId $wpid) {
            $cands += , ([pscustomobject]@{ Hwnd = $h; Pid = $wpid })
        }
    }

    if ($cands.Count -eq 0) { return [long]0 }

    if ($Version) {
        $p = Get-MtPaths -Version $Version
        $markers = @([string]$p.subproject, [string]$p.run_dir, "run\$Version")
        $byPid = @{}
        foreach ($jp in (Get-JavaProcesses)) { $byPid[[int]$jp.Pid] = [string]$jp.CommandLine }
        foreach ($c in $cands) {
            $cmd = ''
            if ($byPid.ContainsKey([int]$c.Pid)) { $cmd = $byPid[[int]$c.Pid] }
            foreach ($m in $markers) {
                if ($m -and $cmd.Contains($m)) { return [long]$c.Hwnd }
            }
        }

        # ── 回退：标题含版本号的唯一候选 ───────────────────────────────────
        # 缺陷修复（2026-09-12，真机实测）：ModDevGradle/FML 的 DevLaunch 以
        # 「短命令行 + args 文件」启动客户端，窗口属主的命令行只有 56 字符
        # （实测 1.21.1: `net.caffeinemc.sodium / net.minecraft.client.main.Main /`），
        # 其父进程（FML bootstrapper）命令行 1185 字符里也不含子项目名/run 目录，
        # 再上一级启动器进程已退出（reparent）—— 即**整条链都读不到标记**，
        # 于是 -Version 分支永远返回 0，mt_inject 拒绝注入（MT_PUBLISH: FAILED），
        # 所有 inject_command 步骤在进入断言前就失败。
        # python `_find_window` 用的是同一套标记判据，故这是**原有缺陷**而非移植回归。
        #
        # 回退仍不猜：仅当候选**唯一**、且该候选窗口标题含本版本号时才接受；
        # 多候选（如用户自己的整合包客户端也在跑）一律返回 0，由调用方报错退出。
        # 安全性兜底：万一选中了错误窗口，被注入的命令不会写进
        # `run/<版本>/logs/latest.log`，用例的 MT_ASSERT_LOG 会判 FAIL ——
        # 该回退不可能把错误的客户端变成「看起来通过」。
        $titleHit = @($cands | Where-Object { (Get-MtWindowTitle -Hwnd $_.Hwnd).Contains($Version) })
        if ($cands.Count -eq 1 -and $titleHit.Count -eq 1) { return [long]$titleHit[0].Hwnd }

        return [long]0   # 有客户端，但都不是本版本的 dev 进程
    }

    if ($cands.Count -eq 1) { return [long]$cands[0].Hwnd }
    return [long]0
}

# ══ 真实输入（SendInput）════════════════════════════════════════════════════
# 背景（2026-09-13 实测）：注入原本走 PostMessage(WM_KEYDOWN/WM_CHAR)，而 GLFW **不把
# PostMessage 投递的按键当作真实输入**（窗口失焦时尤其明显：命令被静默丢弃、脚本仍报成功）。
# 这里提供 SendInput 版本的真实键鼠输入：必须先由调用方把目标窗口置前台
# （见 mt_inject.ps1 的 Assert-MtInjectForeground），真实输入只会进前台窗口。

if (-not ('Mt.RealInput' -as [type])) {
    Add-Type -ErrorAction Stop -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

namespace Mt
{
    public static class RealInput
    {
        [StructLayout(LayoutKind.Sequential)]
        public struct KEYBDINPUT
        {
            public ushort wVk;
            public ushort wScan;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Sequential)]
        public struct MOUSEINPUT
        {
            public int dx;
            public int dy;
            public uint mouseData;
            public uint dwFlags;
            public uint time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Explicit)]
        public struct INPUTUNION
        {
            [FieldOffset(0)] public KEYBDINPUT ki;
            [FieldOffset(0)] public MOUSEINPUT mi;
        }

        [StructLayout(LayoutKind.Sequential)]
        public struct INPUT
        {
            public uint type;
            public INPUTUNION u;
        }

        private const uint INPUT_MOUSE = 0;
        private const uint INPUT_KEYBOARD = 1;
        private const uint KEYEVENTF_EXTENDEDKEY = 0x0001;
        private const uint KEYEVENTF_KEYUP = 0x0002;
        private const uint KEYEVENTF_UNICODE = 0x0004;
        private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
        private const uint MOUSEEVENTF_LEFTUP = 0x0004;
        private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
        private const uint MOUSEEVENTF_RIGHTUP = 0x0010;

        [DllImport("user32.dll", SetLastError = true)]
        private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern bool SetCursorPos(int X, int Y);

        private static bool Send(INPUT input)
        {
            INPUT[] batch = new INPUT[] { input };
            return SendInput(1, batch, Marshal.SizeOf(typeof(INPUT))) == 1;
        }

        public static bool Key(int vk, bool up)
        {
            INPUT input = new INPUT();
            input.type = INPUT_KEYBOARD;
            input.u.ki.wVk = (ushort)vk;
            input.u.ki.wScan = 0;
            input.u.ki.dwFlags = up ? KEYEVENTF_KEYUP : 0;
            return Send(input);
        }

        // 含扩展键（如右 Alt/方向键区）时置 KEYEVENTF_EXTENDEDKEY
        public static bool KeyEx(int vk, bool up, bool extended)
        {
            INPUT input = new INPUT();
            input.type = INPUT_KEYBOARD;
            input.u.ki.wVk = (ushort)vk;
            input.u.ki.wScan = 0;
            input.u.ki.dwFlags = (up ? KEYEVENTF_KEYUP : 0) | (extended ? KEYEVENTF_EXTENDEDKEY : 0);
            return Send(input);
        }

        public static bool Text(string text)
        {
            if (string.IsNullOrEmpty(text)) { return true; }
            INPUT[] batch = new INPUT[text.Length * 2];
            int n = 0;
            foreach (char c in text)
            {
                INPUT down = new INPUT();
                down.type = INPUT_KEYBOARD;
                down.u.ki.wVk = 0;
                down.u.ki.wScan = c;
                down.u.ki.dwFlags = KEYEVENTF_UNICODE;
                batch[n++] = down;

                INPUT up = new INPUT();
                up.type = INPUT_KEYBOARD;
                up.u.ki.wVk = 0;
                up.u.ki.wScan = c;
                up.u.ki.dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP;
                batch[n++] = up;
            }
            return SendInput((uint)batch.Length, batch, Marshal.SizeOf(typeof(INPUT))) == (uint)batch.Length;
        }

        public static bool CursorTo(int x, int y)
        {
            return SetCursorPos(x, y);
        }

        public static bool Mouse(bool right, bool up)
        {
            INPUT input = new INPUT();
            input.type = INPUT_MOUSE;
            input.u.mi.dwFlags = right
                ? (up ? MOUSEEVENTF_RIGHTUP : MOUSEEVENTF_RIGHTDOWN)
                : (up ? MOUSEEVENTF_LEFTUP : MOUSEEVENTF_LEFTDOWN);
            return Send(input);
        }
    }
}
'@
}

function Send-MtRealKey {
    <#
    .SYNOPSIS
        真实按键（SendInput）——只作用于当前前台窗口；扩展键需 -Extended。
    #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][int]$Vk,
        [switch]$Up,
        [switch]$Extended
    )

    if ($Extended) { return [bool][Mt.RealInput]::KeyEx($Vk, [bool]$Up, $true) }
    return [bool][Mt.RealInput]::Key($Vk, [bool]$Up)
}

function Send-MtRealText {
    <#
    .SYNOPSIS
        真实文本输入（SendInput KEYEVENTF_UNICODE，逐 UTF-16 码元）。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][AllowEmptyString()][string]$Text)

    return [bool][Mt.RealInput]::Text($Text)
}

function Set-MtRealCursorPosition {
    [CmdletBinding()]
    param([Parameter(Mandatory)][int]$X, [Parameter(Mandatory)][int]$Y)

    return [bool][Mt.RealInput]::CursorTo($X, $Y)
}

function Send-MtRealMouse {
    <#
    .SYNOPSIS
        真实鼠标按键（SendInput）——配合 Set-MtRealCursorPosition 使用。
    #>
    [CmdletBinding()]
    param([Parameter(Mandatory)][bool]$Right, [Parameter(Mandatory)][bool]$Up)

    return [bool][Mt.RealInput]::Mouse($Right, $Up)
}

Export-ModuleMember -Function @(
    'Get-MtModuleHandle', 'Set-MtThreadDpiAwareness', 'Get-MtScreenSize',
    'Get-MtKeyboardLayouts', 'Test-MtEnUsLayoutAvailable', 'Get-MtLangIdOfThread',
    'Get-MtLayoutDescription', 'Wait-MtLangId', 'Invoke-MtLayoutSwitch', 'Set-MtWindowUs',
    'Get-MtTopLevelWindows', 'Test-MtWindowVisible', 'Get-MtWindowTitleLength',
    'Get-MtWindowTitle', 'Get-MtThreadOfWindow',
    'Get-MtWindowProcessId', 'Get-MtWindowRect', 'Send-MtPostMessage',
    'Invoke-MtShowWindow', 'Set-MtWindowPos', 'Invoke-MtBringWindowToTop',
    'Get-MtForegroundWindow', 'Set-MtForegroundWindow', 'Set-MtAttachThreadInput',
    'Get-MtCurrentThreadId', 'Register-MtMessageClass', 'New-MtMessageWindow',
    'Unregister-MtMessageClass', 'Remove-MtWindow', 'Invoke-MtPumpMessages',
    'Test-MtPidIsJava', 'Find-MtMinecraftWindow',
    'Send-MtRealKey', 'Send-MtRealText', 'Set-MtRealCursorPosition', 'Send-MtRealMouse'
)
