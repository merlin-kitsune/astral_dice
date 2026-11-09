#!/usr/bin/env python3
"""mt_inject — 游戏内输入注入（阶段 C 的执行臂，python 版）。

与旧编排器的输入注入子命令（约 950 行，随迁移删除）**契约等价**：子命令 key / cmd / mouse、
参数名、机器行、退出码（PASS 0 / FAIL 1 / ERROR 2）逐条对齐。

唯一刻意差异：本文件支持 --help / -h（ps1 没有帮助入口，会按「未知参数」退 2）——
测试台要求两个一次性 CLI 都能自描述。

投递通道：
  sendinput（默认）—— SendInput 发**真实**键鼠（按键按扫描码）。GLFW 只吃真实输入，
                     PostMessage 投递的按键会被静默丢弃而调用方仍打印成功（2026-09-13 实测）。
  postmessage     —— 旧路径，仅排查时用 --transport postmessage 切回。

硬前置：注入前自动把目标窗口线程切到 en-US（mt_ime.set_window_us），不需要手动换输入法，
        也不影响用户其它程序的输入语言。
非 Windows：ERROR 2（窗口枚举与真实输入都不可用）。

用法：
  mt_inject.py key  --key rclick
  mt_inject.py key  --key w --hold-ms 1000
  mt_inject.py mouse --button left
  mt_inject.py mouse --button wheel --notches -1
  mt_inject.py cmd  --command "/astral_dice targetselect enemy"
  mt_inject.py cmd  --command "/give Dev x" --layout as-is   # 排查用：不切语言
"""
from __future__ import annotations

import ctypes
import os
import sys
import time
from ctypes import wintypes
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import mt_ps  # noqa: E402
from mt_compat import (  # noqa: E402
    MT_EXIT_ERROR,
    MT_EXIT_FAIL,
    MT_EXIT_PASS,
    get_paths,
    init_console,
    list_java_processes,
    test_client_process,
    write_mt_err_line,
    write_mt_error_line,
    write_mt_line,
)

BSL = chr(92)

# ── 扫描码表（美式布局；注入前由 mt_ime 把目标窗口线程切到 en-US）────────
# 2026-09-17 新增 k/o/r（光影键位取自 Iris 的 GLFW 键码）。q 为 2026-09-22 新增（原版 drop）。
SCAN = {
    "t": 0x14, "enter": 0x1C, "escape": 0x01, "e": 0x12, "j": 0x24,
    "h": 0x23, "w": 0x11, "f2": 0x3C, "f3": 0x3D, "slash": 0x35, "tab": 0x0F,
    "k": 0x25, "o": 0x18, "r": 0x13, "q": 0x10,
}
VK = {
    "t": 0x54, "enter": 0x0D, "escape": 0x1B, "e": 0x45, "j": 0x4A,
    "h": 0x48, "w": 0x57, "f2": 0x71, "f3": 0x72, "slash": 0xBF, "tab": 0x09,
    "k": 0x4B, "o": 0x4F, "r": 0x52, "q": 0x51,
}
for _i in range(1, 10):
    _digit = str(_i)
    VK[_digit] = 0x30 + _i
    SCAN[_digit] = 0x02 + (_i - 1)
# 功能键 F1..F12：VK 连续 F1=0x70..F12=0x7B；扫描码 F1..F10 = 0x3B..0x44，F11/F12 = 0x57/0x58。
for _i in range(1, 13):
    _fn = "f" + str(_i)
    VK[_fn] = 0x6F + _i
    SCAN[_fn] = (0x3A + _i) if _i <= 10 else (0x57 + (_i - 11))

# 语义键 → 实际按键。
# 2026-09-17 删除 'confirm' = 'enter'（「确认」在目标选择器语义下是鼠标左键）。
KEY_ALIAS = {
    "chat": "t", "skill": "j", "cancel": "escape",
    "screenshot": "f2", "debug": "f3", "inventory": "e", "card": "h",
    "shadertoggle": "k", "shaderscreen": "o", "shaderreload": "r",
    "drop": "q", "thirdperson": "f5",
}

WM_KEYDOWN, WM_KEYUP, WM_CHAR = 0x0100, 0x0101, 0x0102
WM_LBUTTONDOWN, WM_LBUTTONUP = 0x0201, 0x0202
WM_RBUTTONDOWN, WM_RBUTTONUP = 0x0204, 0x0205
WM_MOUSEWHEEL = 0x020A
WHEEL_DELTA = 120
VK_SHIFT = 0xA0
SHIFT_SCAN = 0x2A

KEYEVENTF_EXTENDEDKEY = 0x0001
KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004
KEYEVENTF_SCANCODE = 0x0008
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_WHEEL = 0x0800
INPUT_MOUSE = 0
INPUT_KEYBOARD = 1
SW_RESTORE = 9

TRANSPORT = "sendinput"
ESC_NORMALIZE = False
DRY_RUN = False


def _win32():
    return sys.platform == "win32"


class _RECT(ctypes.Structure):
    _fields_ = [
        ("left", wintypes.LONG),
        ("top", wintypes.LONG),
        ("right", wintypes.LONG),
        ("bottom", wintypes.LONG),
    ]


class _MOUSEINPUT(ctypes.Structure):
    _fields_ = [
        ("dx", wintypes.LONG),
        ("dy", wintypes.LONG),
        ("mouseData", wintypes.DWORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.c_void_p),
    ]


class _KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.c_void_p),
    ]


class _HARDWAREINPUT(ctypes.Structure):
    _fields_ = [
        ("uMsg", wintypes.DWORD),
        ("wParamL", wintypes.WORD),
        ("wParamH", wintypes.WORD),
    ]


class _INPUTUNION(ctypes.Union):
    _fields_ = [("mi", _MOUSEINPUT), ("ki", _KEYBDINPUT), ("hi", _HARDWAREINPUT)]


class _INPUT(ctypes.Structure):
    _fields_ = [("type", wintypes.DWORD), ("u", _INPUTUNION)]


_U = None


def _user32():
    global _U
    if _U is None:
        u = ctypes.WinDLL("user32", use_last_error=True)
        u.GetForegroundWindow.restype = wintypes.HWND
        u.GetFocus.restype = wintypes.HWND
        u.GetWindowThreadProcessId.restype = wintypes.DWORD
        u.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
        u.SendInput.restype = wintypes.UINT
        u.SendInput.argtypes = [wintypes.UINT, ctypes.POINTER(_INPUT), ctypes.c_int]
        u.GetWindowRect.argtypes = [wintypes.HWND, ctypes.POINTER(_RECT)]
        u.PostMessageW.argtypes = [wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM]
        u.SetCursorPos.argtypes = [ctypes.c_int, ctypes.c_int]
# EnumWindows 不设 argtypes：回调对象必须原样传入（设成 c_void_p 会抛 TypeError）。
        u.GetWindowTextW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
        u.GetWindowTextLengthW.restype = ctypes.c_int
        _U = u
    return _U


def _kernel32():
    return ctypes.WinDLL("kernel32", use_last_error=True)


# ── 输出纪律 ──────────────────────────────────────────────────────────────
def inject_line(text):
    """面向观众的输出行（干跑时静默，保证干跑输出只有元组序列）。"""
    if DRY_RUN:
        return
    write_mt_line(text)


def dry_tuple(hwnd, msg, wparam, lparam):
    write_mt_line(
        "MT_INJECT_DRYRUN: hwnd=0x%016X msg=0x%04X wparam=0x%016X lparam=0x%016X"
        % (hwnd, msg & 0xFFFF, wparam, lparam)
    )


def inject_pause(ms):
    if DRY_RUN:
        return
    time.sleep(max(0, ms) / 1000.0)


def send_inject_message(hwnd, msg, wparam=0, lparam=0):
    """所有 PostMessage 注入的唯一出口：真实投递或（干跑时）记录元组。"""
    if DRY_RUN:
        dry_tuple(hwnd, msg, wparam, lparam)
        return
    _user32().PostMessageW(wintypes.HWND(hwnd), msg, wparam & 0xFFFFFFFF, lparam & 0xFFFFFFFF)


# ── 真实输入（SendInput）──────────────────────────────────────────────────
def _send_input(inp):
    return _user32().SendInput(1, ctypes.byref(inp), ctypes.sizeof(_INPUT)) == 1


def real_key_scan(scan, up=False, extended=False):
    inp = _INPUT()
    inp.type = INPUT_KEYBOARD
    inp.u.ki.wVk = 0
    inp.u.ki.wScan = scan & 0xFFFF
    flags = KEYEVENTF_SCANCODE
    if up:
        flags |= KEYEVENTF_KEYUP
    if extended:
        flags |= KEYEVENTF_EXTENDEDKEY
    inp.u.ki.dwFlags = flags
    return _send_input(inp)


def real_key_vk(vk, up=False):
    inp = _INPUT()
    inp.type = INPUT_KEYBOARD
    inp.u.ki.wVk = vk & 0xFFFF
    inp.u.ki.wScan = 0
    inp.u.ki.dwFlags = KEYEVENTF_KEYUP if up else 0
    return _send_input(inp)


def real_text(text):
    """真实文本输入：整串一次 SendInput(KEYEVENTF_UNICODE)，逐 UTF-16 码元。"""
    data = str(text).encode("utf-16-le", "surrogatepass")
    batch = []
    for index in range(0, len(data), 2):
        unit = int.from_bytes(data[index:index + 2], "little")
        for up in (False, True):
            inp = _INPUT()
            inp.type = INPUT_KEYBOARD
            inp.u.ki.wVk = 0
            inp.u.ki.wScan = unit
            inp.u.ki.dwFlags = KEYEVENTF_UNICODE | (KEYEVENTF_KEYUP if up else 0)
            batch.append(inp)
    if not batch:
        return True
    array = (_INPUT * len(batch))(*batch)
    return _user32().SendInput(len(batch), array, ctypes.sizeof(_INPUT)) == len(batch)


def real_mouse(right, up):
    inp = _INPUT()
    inp.type = INPUT_MOUSE
    if right:
        inp.u.mi.dwFlags = MOUSEEVENTF_RIGHTUP if up else MOUSEEVENTF_RIGHTDOWN
    else:
        inp.u.mi.dwFlags = MOUSEEVENTF_LEFTUP if up else MOUSEEVENTF_LEFTDOWN
    return _send_input(inp)


def real_wheel(delta):
    inp = _INPUT()
    inp.type = INPUT_MOUSE
    inp.u.mi.dwFlags = MOUSEEVENTF_WHEEL
    inp.u.mi.mouseData = delta & 0xFFFFFFFF
    return _send_input(inp)


def cursor_to(x, y):
    return bool(_user32().SetCursorPos(int(x), int(y)))


# ── 窗口工具 ──────────────────────────────────────────────────────────────
def foreground_window():
    return int(_user32().GetForegroundWindow() or 0)


def thread_of_window(hwnd):
    return int(_user32().GetWindowThreadProcessId(wintypes.HWND(hwnd), None) or 0)


def current_tid():
    return int(_kernel32().GetCurrentThreadId())


def focus_window(hwnd):
    return bool(_user32().SetFocus(wintypes.HWND(hwnd)))


def show_window(hwnd, cmd_show):
    return bool(_user32().ShowWindow(wintypes.HWND(hwnd), int(cmd_show)))


def bring_window_to_top(hwnd):
    return bool(_user32().BringWindowToTop(wintypes.HWND(hwnd)))


def set_foreground_window(hwnd):
    return bool(_user32().SetForegroundWindow(wintypes.HWND(hwnd)))


def attach_thread_input(from_tid, to_tid, attach):
    return bool(_user32().AttachThreadInput(int(from_tid), int(to_tid), bool(attach)))


def window_rect(hwnd):
    rect = _RECT()
    if not _user32().GetWindowRect(wintypes.HWND(hwnd), ctypes.byref(rect)):
        return None
    return rect


def top_level_windows():
    found = []
    u = _user32()
    cb_type = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)

    def _cb(hwnd, _lparam):
        found.append(int(hwnd))
        return True

    u.EnumWindows(cb_type(_cb), 0)
    return found


def window_visible(hwnd):
    return bool(_user32().IsWindowVisible(wintypes.HWND(hwnd)))


def window_title(hwnd):
    u = _user32()
    length = int(u.GetWindowTextLengthW(wintypes.HWND(hwnd)))
    if length <= 0:
        return ""
    buffer = ctypes.create_unicode_buffer(length + 2)
    u.GetWindowTextW(wintypes.HWND(hwnd), buffer, length + 2)
    return buffer.value


def window_pid(hwnd):
    pid = wintypes.DWORD(0)
    _user32().GetWindowThreadProcessId(wintypes.HWND(hwnd), ctypes.byref(pid))
    return int(pid.value)


# ── java 进程表（宿主能力：mt_ps 走 JDK jps）──────────────────────────────
def _java_rows():
    try:
        return mt_ps.java_rows()
    except mt_ps.ProcessEnumerationUnavailable:
        pass
    except Exception:
        pass
    try:
        return [
            {"pid": int(row["pid"]), "cmdline": str(row.get("command_line") or "")}
            for row in list_java_processes()
        ]
    except Exception:
        return None


def _pid_is_java(pid):
    """对齐 Test-MtPidIsJava 的**容错**语义：探测不到进程时不阻断，按 True 处理。"""
    rows = _java_rows()
    if rows is None:
        return True
    for row in rows:
        if row["pid"] == int(pid):
            return True
    # 枚举成功但表里没有该 pid ⇒ 不是本用户可见的 JVM（jps 只列同用户进程）；
    # 枚举失败时沿用容错语义（上面 return True），不因探测能力缺口阻断注入。
    return False


def find_window(version):
    """定位本次测试的 Minecraft 客户端窗口；无法唯一确定时返回 0。

    多候选是关键场景：用户自己的整合包客户端与本流程的 dev 客户端可能同时在跑，
    两个窗口标题都含 "Minecraft"，取第一个会把按键投到错误的客户端。
    """
    candidates = []
    for hwnd in top_level_windows():
        if not window_visible(hwnd):
            continue
        title = window_title(hwnd)
        if not title:
            continue
        if "Minecraft" not in title:
            continue
        if any(skip in title for skip in ("Chrome", "Edge", "Wiki", "Firefox")):
            continue
        pid = window_pid(hwnd)
        if _pid_is_java(pid):
            candidates.append((hwnd, pid))
    if not candidates:
        return 0

    if version:
        paths = None
        try:
            paths = get_paths(version)
        except Exception:
            paths = None
        markers = []
        if paths is not None:
            # 与 python 原 _find_window 同口径：裸子项目名 + run 目录 + run\<版本>
            markers = [str(paths.subproject), str(paths.run_dir), "run" + BSL + str(version)]
        rows = _java_rows() or []
        by_pid = {}
        for row in rows:
            by_pid[int(row["pid"])] = str(row["cmdline"] or "")
        for hwnd, pid in candidates:
            command = by_pid.get(int(pid), "")
            for marker in markers:
                if marker and marker in command:
                    return hwnd
        # 回退：标题含版本号的**唯一**候选（DevLaunch 短命令行读不到标记时用）
        hits = [hwnd for hwnd, _pid in candidates if str(version) in window_title(hwnd)]
        if len(hits) == 1:
            return hits[0]
        return 0

    if len(candidates) == 1:
        return candidates[0][0]
    return 0


def client_alive(version):
    """Get-MtClientStatus(.Alive)：True / False / None（判不了）。"""
    try:
        paths = get_paths(version)
    except Exception:
        return None
    rows = _java_rows()
    if rows is None:
        return None
    for row in rows:
        try:
            if test_client_process(paths, row["pid"], row["cmdline"]):
                return True
        except Exception:
            continue
    return False


def resolve_window(hwnd, version):
    """-Hwnd 显式指定优先，否则按版本定位；失败时先打错误行再返回 0。"""
    target = int(hwnd or 0)
    if not target:
        target = find_window(version)
    if not target:
        alive = client_alive(version)
        if alive is False:
            write_mt_err_line(
                "MT_INJECT: ERROR — %s 客户端未在运行（客户端入口进程：1.21.1/1.20.1 为 "
                "net.minecraft.client.main.Main，26.1.2 为 net.neoforged.fml.startup.Client；"
                "先跑 --phase launch）" % version
            )
        else:
            write_mt_err_line("MT_INJECT: ERROR — 未能唯一确定本版本 Minecraft 窗口（存在多个候选客户端）")
        return 0
    return target


def layout_ready(hwnd, layout):
    if layout == "as-is":
        return True, "按 --layout as-is 跳过切换"
    import mt_ime

    ok, message = mt_ime.set_window_us(hwnd)
    return bool(ok), str(message)


def assert_foreground(hwnd, retries=3):
    """注入前把目标窗口置前台：前台**且**焦点都是它才算通过。"""
    for _attempt in range(1, int(retries) + 1):
        if foreground_window() == hwnd and get_real_focus(hwnd) == hwnd:
            return True
        show_window(hwnd, SW_RESTORE)
        bring_window_to_top(hwnd)
        foreground = foreground_window()
        fg_tid = thread_of_window(foreground) if foreground else 0
        target_tid = thread_of_window(hwnd)
        my_tid = current_tid()
        a1 = attach_thread_input(my_tid, fg_tid, True) if fg_tid else False
        a2 = False
        if target_tid and target_tid != fg_tid:
            a2 = attach_thread_input(my_tid, target_tid, True)
        try:
            set_foreground_window(hwnd)
            # 前台不等于焦点：附加状态下把键盘焦点也交给目标窗口，否则按键被静默忽略。
            if target_tid and (target_tid == my_tid or a2):
                focus_window(hwnd)
            time.sleep(0.12)
        finally:
            if a2:
                attach_thread_input(my_tid, target_tid, False)
            if a1:
                attach_thread_input(my_tid, fg_tid, False)
        if foreground_window() == hwnd and get_real_focus(hwnd) == hwnd:
            return True
    return False


def get_real_focus(hwnd):
    """查询目标窗口是否持有键盘焦点（先附加到该窗口线程，再读 GetFocus）。"""
    tid = thread_of_window(hwnd)
    my_tid = current_tid()
    attached = False
    if tid and tid != my_tid:
        attached = attach_thread_input(my_tid, tid, True)
    try:
        return int(_user32().GetFocus() or 0)
    finally:
        if attached:
            attach_thread_input(my_tid, tid, False)


# ── 注入原语 ──────────────────────────────────────────────────────────────
def key_down(hwnd, vk, scan):
    if DRY_RUN:
        return
    if TRANSPORT == "sendinput":
        real_key_scan(scan)
        return
    send_inject_message(hwnd, WM_KEYDOWN, vk, 1 | (scan << 16))


def key_up(hwnd, vk, scan):
    if DRY_RUN:
        return
    if TRANSPORT == "sendinput":
        real_key_scan(scan, up=True)
        return
    # lParam 里 (1<<14) 或了两次（python 原件如此，结果与或一次相同）；照抄求字节级一致。
    lparam = (1 << 30) | (1 << 14) | (1 << 14) | (scan << 16)
    send_inject_message(hwnd, WM_KEYUP, vk, lparam)


def send_key(hwnd, vk, scan):
    key_down(hwnd, vk, scan)
    key_up(hwnd, vk, scan)


def mouse_center(hwnd, right, shift=False, hold_ms=0):
    """窗口中心投递鼠标按键；SHIFT 必须**同通道成对**且异常路径也复位。"""
    shift_down = False
    try:
        if shift:
            key_down(hwnd, VK_SHIFT, SHIFT_SCAN)
            shift_down = True
            inject_pause(120)
        rect = window_rect(hwnd)
        if rect is None:
            rect = _RECT(0, 0, 0, 0)
        x = int((rect.right - rect.left) / 2)
        y = int((rect.bottom - rect.top) / 2)
        hold = hold_ms if hold_ms > 0 else 100
        if TRANSPORT == "sendinput":
            if not DRY_RUN:
                cursor_to(rect.left + x, rect.top + y)
                inject_pause(80)
                real_mouse(right, False)
                inject_pause(hold)
                real_mouse(right, True)
        else:
            lparam = (y << 16) | (x & 0xFFFF)
            down = WM_RBUTTONDOWN if right else WM_LBUTTONDOWN
            up = WM_RBUTTONUP if right else WM_LBUTTONUP
            send_inject_message(hwnd, down, 1, lparam)
            inject_pause(hold)
            send_inject_message(hwnd, up, 0, lparam)
    finally:
        if shift_down:
            inject_pause(100)
            key_up(hwnd, VK_SHIFT, SHIFT_SCAN)


def wheel(hwnd, notches):
    delta = int(notches) * WHEEL_DELTA
    rect = window_rect(hwnd)
    if rect is None:
        rect = _RECT(0, 0, 0, 0)
    x = int((rect.right - rect.left) / 2)
    y = int((rect.bottom - rect.top) / 2)
    lparam = (y << 16) | (x & 0xFFFF)
    if TRANSPORT == "sendinput":
        if DRY_RUN:
            # 干跑不撒谎：一条与 postmessage 同形的元组 + 一条标明真实通道调用。
            dry_tuple(hwnd, WM_MOUSEWHEEL, (delta & 0xFFFF) << 16, lparam)
            dry_tuple(hwnd, 0xFFFF, delta, 0)
            return
        cursor_to(rect.left + x, rect.top + y)
        inject_pause(60)
        real_wheel(delta)
        return
    # 负数按 16 位截断后再左移：(-120 -band 0xFFFF) << 16 = 0xFF880000。
    wparam = ((delta & 0xFFFF) << 16) & 0xFFFFFFFF
    if DRY_RUN:
        dry_tuple(hwnd, WM_MOUSEWHEEL, wparam, lparam)
        return
    send_inject_message(hwnd, WM_MOUSEWHEEL, wparam, lparam)


def _prepare(hwnd, layout, focus_reason):
    """layout + 前台断言（干跑时整段跳过）。失败返回 False。"""
    if DRY_RUN:
        return True
    ok, message = layout_ready(hwnd, layout)
    inject_line("MT_INJECT_LAYOUT: %s — %s" % ("OK" if ok else "FAIL", message))
    if not ok:
        write_mt_err_line("MT_INJECT: ERROR — 输入语言未就绪，拒绝注入（%s）" % message)
        return False
    if not assert_foreground(hwnd):
        inject_line("MT_INJECT_FOCUS: FAIL — 目标窗口无法置前台，%s会被游戏忽略" % focus_reason)
        write_mt_err_line("MT_INJECT: ERROR — 目标窗口未取得前台，拒绝注入（失焦时 GLFW 会丢弃按键）")
        return False
    inject_line("MT_INJECT_FOCUS: OK")
    return True


# ── 子命令 ────────────────────────────────────────────────────────────────
def cmd_key(args):
    hwnd = resolve_window(args["hwnd"], args["version"])
    if not hwnd:
        return MT_EXIT_ERROR
    if not _prepare(hwnd, args["layout"], "按键"):
        return MT_EXIT_ERROR

    # key 子命令**从不**做 Esc→Tab→Enter 归一化；-NoEsc 只是把这一声明回显出来。
    if args["no_esc"]:
        inject_line("ESC_SKIP: key 子命令无 Esc 归一化 ⇒ -NoEsc 声明成立（按下的键就是本步输入）")

    raw = args["key"]
    key = raw.lower()
    key = KEY_ALIAS.get(key, key)
    hold = int(args["hold_ms"] or 0)

    if key in ("attack", "rclick", "shift-rclick"):
        mouse_center(hwnd, key in ("rclick", "shift-rclick"), key == "shift-rclick", hold)
        held = (" 按住 %dms" % hold) if hold > 0 else ""
        inject_line("MT_INJECT_KEY: %s (窗口中心)%s" % (key, held))
        return MT_EXIT_PASS

    if key == "w":
        ms = hold if hold > 0 else 1000
        key_down(hwnd, VK["w"], SCAN["w"])
        inject_pause(ms)
        key_up(hwnd, VK["w"], SCAN["w"])
        inject_line("MT_INJECT_KEY: w 按住 %dms" % ms)
        return MT_EXIT_PASS

    if key not in VK:
        write_mt_err_line("MT_INJECT: ERROR — 未知按键 %s" % raw)
        return MT_EXIT_ERROR

    send_key(hwnd, VK[key], SCAN[key])
    inject_line("MT_INJECT_KEY: %s" % key)
    return MT_EXIT_PASS


def cmd_mouse(args):
    button = str(args["button"] or "").strip().lower()
    if button not in ("left", "right", "wheel"):
        write_mt_error_line("非法 --button 值 %s（可选：left right wheel）" % args["button"])
        return MT_EXIT_ERROR
    if button == "wheel":
        if args["shift"]:
            write_mt_error_line("wheel 不接受 --shift（滚轮无修饰键语义；要潜行请用 left/right）")
            return MT_EXIT_ERROR
        notches = int(args["notches"])
        if notches == 0 or notches < -32 or notches > 32:
            write_mt_error_line("非法 --notches 值 %d（非 0 且 |n| ≤ 32）" % notches)
            return MT_EXIT_ERROR

    hwnd = resolve_window(args["hwnd"], args["version"])
    if not hwnd:
        return MT_EXIT_ERROR
    if not _prepare(hwnd, args["layout"], "鼠标"):
        return MT_EXIT_ERROR

    if args["no_esc"]:
        inject_line("ESC_SKIP: mouse 子命令无 Esc 归一化 ⇒ -NoEsc 声明成立（只发鼠标键）")

    hold = int(args["hold_ms"] or 0)
    if button == "wheel":
        wheel(hwnd, int(args["notches"]))
        transport = " transport=sendinput" if TRANSPORT == "sendinput" else " transport=postmessage"
        inject_line("MT_INJECT_MOUSE: wheel notches=%d%s (窗口中心)" % (int(args["notches"]), transport))
        return MT_EXIT_PASS

    mouse_center(hwnd, button == "right", bool(args["shift"]), hold)
    shift_tag = " +shift" if args["shift"] else ""
    held = (" 按住 %dms" % hold) if hold > 0 else ""
    inject_line("MT_INJECT_MOUSE: %s%s (窗口中心)%s" % (button, shift_tag, held))
    return MT_EXIT_PASS


def _pause_lock_on_lost_focus(version):
    """options.txt 的 pauseOnLostFocus:false ⇒ 失焦不再打开 ESC 暂停菜单。"""
    try:
        paths = get_paths(version)
    except Exception:
        return False
    options = os.path.join(str(paths.run_dir), "options.txt")
    try:
        with open(options, "rb") as handle:
            raw = handle.read()
    except OSError:
        return False
    text = raw.decode("utf-8", "replace").replace(chr(13) + chr(10), chr(10)).replace(chr(13), chr(10))
    for line in text.split(chr(10)):
        stripped = line.strip()
        if not stripped.lower().startswith("pauseonlostfocus"):
            continue
        if ":" not in stripped:
            continue
        value = stripped.split(":", 1)[1].strip()
        return value.lower() == "false"
    return False


def cmd_cmd(args):
    hwnd = resolve_window(args["hwnd"], args["version"])
    if not hwnd:
        return MT_EXIT_ERROR
    if not _prepare(hwnd, args["layout"], "按键"):
        return MT_EXIT_ERROR

    command = args["command"]
    esc_normalize = ((not args["no_esc"]) and TRANSPORT != "sendinput") or ESC_NORMALIZE
    if esc_normalize:
        send_key(hwnd, VK["t"], SCAN["t"])
        inject_pause(150)
        send_key(hwnd, VK["escape"], SCAN["escape"])
        inject_pause(200)

    pause_locked = _pause_lock_on_lost_focus(args["version"])
    if args["no_esc"] and pause_locked:
        inject_line("ESC_SKIP: pause-lock 生效 + -NoEsc ⇒ 跳过 Esc→Tab→Enter 归一化（失焦不再打开 ESC 暂停菜单）")
    else:
        # 状态归一化：Esc → Tab → Enter，把任意起始状态收敛到「无界面且未暂停」。
        send_key(hwnd, VK["escape"], SCAN["escape"])
        inject_pause(350)
        send_key(hwnd, VK["tab"], SCAN["tab"])
        inject_pause(150)
        send_key(hwnd, VK["enter"], SCAN["enter"])
        inject_pause(450)

    send_key(hwnd, VK["t"], SCAN["t"])
    inject_pause(800)

    text = command
    if TRANSPORT == "sendinput":
        if not DRY_RUN:
            real_text(text)
    else:
        if text.startswith("/"):
            text = text[1:]
        send_key(hwnd, VK["slash"], SCAN["slash"])
        inject_pause(800)
        for char in text:
            send_inject_message(hwnd, WM_CHAR, ord(char), 1)

    inject_pause(300)
    send_key(hwnd, VK["enter"], SCAN["enter"])
    inject_line("MT_INJECT_CMD: %s" % command)
    return MT_EXIT_PASS


# ── 入口：参数解析（与 ps1 同为手写循环，选项名归一化 = 去前导 -、删连字符、小写）──
USAGE = """用法：
  mt_inject.py key   --key <键名> [--hold-ms <毫秒>] [--version <ver>] [选项]
  mt_inject.py mouse --button left|right|wheel [--shift] [--notches <n>] [--hold-ms <ms>] [选项]
  mt_inject.py cmd   --command <文本> [--no-esc] [--esc-normalize] [--version <ver>] [选项]

子命令：
  key    按一次键（语义别名 chat/skill/cancel/screenshot/debug/inventory/card/
         shadertoggle/shaderscreen/shaderreload/drop/thirdperson；
         attack/rclick/shift-rclick 走窗口中心鼠标；w 默认按住 1000ms）
  cmd    打开聊天输入一条命令（默认 Esc→Tab→Enter 归一化 + T + 文本 + Enter）
  mouse  窗口中心鼠标：left / right[+--shift] / wheel[--notches n]

选项：
  --key <键名>            key 子命令的键（必填）
  --command <文本>        cmd 子命令的命令文本（必填，前导 / 保留）
  --button <l|r|wheel>    mouse 子命令的按键（必填）
  --shift                 右键 + 潜行（仅 mouse left/right）
  --notches <n>           滚轮格数，非 0 且 |n| ≤ 32（缺省 -1 = 向下滚一格）
  --hold-ms <ms>          按住毫秒数（鼠标缺省 100；key w 缺省 1000）
  --version <ver>         目标线：1.21.1 / 1.20.1 / 26.1.2 / fabric-1.20.1
  --layout auto|as-is     注入前是否把窗口线程切到 en-US（缺省 auto）
  --hwnd <十进制>         直接指定窗口句柄（跳过窗口定位）
  --no-esc                声明本步不得引入归一化 Esc（cmd 子命令下配合 pause-lock）
  --esc-normalize         强制旧行为（T+Esc 归一化）
  --transport sendinput|postmessage  投递通道（缺省 sendinput）
  --dry-run               只打印将投递的消息元组，不真的注入
  -h, --help              打印本帮助

退出码：PASS 0 / FAIL 1 / ERROR 2
"""


def _arg_long(raw, name):
    text = str(raw).strip()
    try:
        return int(text, 10)
    except (TypeError, ValueError):
        write_mt_error_line("参数 %s 需要整数，收到 %s" % (name, raw))
        raise SystemExit(MT_EXIT_ERROR)


def parse_args(argv):
    args = {
        "mode": "",
        "key": "",
        "button": "",
        "shift": False,
        "hold_ms": 0,
        "notches": -1,
        "command": "",
        "no_esc": False,
        "version": "",
        "layout": "auto",
        "hwnd": 0,
        "transport": "",
        "esc_normalize": False,
        "dry_run": False,
    }
    index = 0
    total = len(argv)
    while index < total:
        token = str(argv[index])
        if token in ("-h", "--help") or (not token.startswith("-") and token.lower() == "help" and not args["mode"]):
            sys.stdout.write(USAGE)
            raise SystemExit(MT_EXIT_PASS)
        if not token.startswith("-"):
            if not args["mode"]:
                args["mode"] = token
                index += 1
                continue
            write_mt_error_line("未知参数 %s" % token)
            raise SystemExit(MT_EXIT_ERROR)
        name = token.lstrip("-").lower().replace("-", "")
        if name in ("key", "button", "command", "version", "layout", "holdms", "notches", "hwnd", "transport"):
            if index + 1 >= total:
                write_mt_error_line("缺少 --%s 的值" % _option_label(name))
                raise SystemExit(MT_EXIT_ERROR)
            value = str(argv[index + 1])
            if name == "key":
                args["key"] = value
            elif name == "button":
                args["button"] = value
            elif name == "command":
                args["command"] = value
            elif name == "version":
                args["version"] = value
            elif name == "layout":
                args["layout"] = value
            elif name == "holdms":
                args["hold_ms"] = _arg_long(value, "--hold-ms")
            elif name == "notches":
                args["notches"] = _arg_long(value, "--notches")
            elif name == "hwnd":
                args["hwnd"] = _arg_long(value, "--hwnd")
            elif name == "transport":
                args["transport"] = value
            index += 2
            continue
        if name == "shift":
            args["shift"] = True
            index += 1
            continue
        if name == "noesc":
            args["no_esc"] = True
            index += 1
            continue
        if name == "escnormalize":
            args["esc_normalize"] = True
            index += 1
            continue
        if name == "dryrun":
            args["dry_run"] = True
            index += 1
            continue
        write_mt_error_line("未知参数 %s" % token)
        raise SystemExit(MT_EXIT_ERROR)
    return args


def _option_label(name):
    return {
        "key": "key",
        "button": "button",
        "command": "command",
        "version": "version",
        "layout": "layout",
        "holdms": "hold-ms",
        "notches": "notches",
        "hwnd": "hwnd",
        "transport": "transport",
    }.get(name, name)


def main(argv=None):
    global TRANSPORT, ESC_NORMALIZE, DRY_RUN
    init_console()
    argv = list(sys.argv[1:] if argv is None else argv)
    args = parse_args(argv)
    DRY_RUN = bool(args["dry_run"])
    mode = str(args["mode"] or "").strip()

    if str(args["layout"]) not in ("auto", "as-is"):
        write_mt_error_line("非法 -Layout 值 %s（可选：auto as-is）" % args["layout"])
        return MT_EXIT_ERROR

    if args["transport"]:
        transport = str(args["transport"]).strip().lower()
        if transport not in ("sendinput", "postmessage"):
            write_mt_error_line("非法 --transport 值 %s（可选：sendinput postmessage）" % args["transport"])
            return MT_EXIT_ERROR
        TRANSPORT = transport
    if args["esc_normalize"]:
        ESC_NORMALIZE = True

    if mode == "key" and not args["key"]:
        write_mt_error_line("缺少必填参数 --key")
        return MT_EXIT_ERROR
    if mode == "cmd" and not args["command"]:
        write_mt_error_line("缺少必填参数 --command")
        return MT_EXIT_ERROR
    if mode == "mouse" and not args["button"]:
        write_mt_error_line("缺少必填参数 --button（可选：left right wheel）")
        return MT_EXIT_ERROR
    if mode not in ("key", "cmd", "mouse"):
        if not mode:
            write_mt_error_line("缺少子命令（可选：key cmd mouse）")
        else:
            write_mt_error_line("未知子命令 %s（可选：key cmd mouse）" % mode)
        return MT_EXIT_ERROR

    if not _win32():
        write_mt_err_line("MT_INJECT: ERROR — 非 Windows 平台不支持 SendInput / PostMessage 注入")
        return MT_EXIT_ERROR

    if mode == "key":
        rc = cmd_key(args)
    elif mode == "mouse":
        rc = cmd_mouse(args)
    else:
        rc = cmd_cmd(args)

    if DRY_RUN:
        write_mt_line("MT_INJECT_DRYRUN_RC: %d" % rc)
    return rc


if __name__ == "__main__":
    sys.exit(main())
