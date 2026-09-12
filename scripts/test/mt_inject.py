#!/usr/bin/env python3
"""mt_inject — 游戏内输入注入（阶段 C 的执行臂）。

平台边界（关键设计）:
  Windows —— 用 ctypes 调 user32 PostMessage 投递 WM_KEYDOWN/WM_CHAR/WM_*BUTTON，
             绕开前台焦点与前输入法限制（对 GLFW 窗口有效）。非 Windows 平台无法复现
             该机制，改为输出结构化请求，由会话层经 MCP 通道执行（见 AGENTS「MCP 集成」）。

硬前置: 本机需安装 en-US 键盘布局（注入按美式扫描码表投递）。
        注入前会**自动**把目标窗口所在线程的输入语言切到 en-US（见 mt_ime.py），
        因此**不需要用户手动切换输入法**，也不影响用户其它程序的输入语言。

用法:
  mt_inject.py key  --key rclick
  mt_inject.py key  --key w --hold-ms 1000
  mt_inject.py cmd  --command "/astral_dice targetselect enemy"
  mt_inject.py cmd  --command "/publish 25565" --no-esc
  mt_inject.py cmd  --command "/give Dev x" --layout as-is   # 排查用：不切语言
"""
from __future__ import annotations

import argparse
import ctypes
import os
import sys
import time
from ctypes import wintypes
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from mt_paths import Paths  # noqa: E402
from mt_ps import java_procs, run_ps  # noqa: E402

EXIT_ERROR = 2

# ── 扫描码表（美式布局；注入前由 mt_ime 把目标窗口线程切到 en-US）────────
SCAN: dict[str, int] = {
    "t": 0x14, "enter": 0x1C, "escape": 0x01, "e": 0x12, "j": 0x24,
    "h": 0x23, "w": 0x11, "f2": 0x3C, "f3": 0x3D, "slash": 0x35,
}
VK: dict[str, int] = {
    "t": 0x54, "enter": 0x0D, "escape": 0x1B, "e": 0x45, "j": 0x4A,
    "h": 0x48, "w": 0x57, "f2": 0x71, "f3": 0x72, "slash": 0xBF,
}
for _i in range(1, 10):
    digit = str(_i)
    VK[digit] = 0x30 + _i
    SCAN[digit] = 0x02 + (_i - 1)

# 语义键 → 实际按键
KEY_ALIAS: dict[str, str] = {
    "chat": "t", "skill": "j", "cancel": "escape",
    "confirm": "enter", "screenshot": "f2",
    "debug": "f3", "inventory": "e", "card": "h",
}

WM_KEYDOWN, WM_KEYUP, WM_CHAR = 0x0100, 0x0101, 0x0102
WM_LBUTTONDOWN, WM_LBUTTONUP = 0x0201, 0x0202
WM_RBUTTONDOWN, WM_RBUTTONUP = 0x0204, 0x0205
VK_SHIFT = 0xA0


class _RECT(ctypes.Structure):
    _fields_ = [("left", wintypes.LONG), ("top", wintypes.LONG),
                ("right", wintypes.LONG), ("bottom", wintypes.LONG)]


def _win32():
    return sys.platform == "win32"


def _degrade(action: str, payload: dict) -> int:
    """非 Windows：输出结构化请求交会话层走 MCP 通道，不算失败。"""
    import json
    print(json.dumps({"mt_inject_unsupported_platform": sys.platform,
                      "action": action, **payload}, ensure_ascii=False))
    print("MT_INJECT: BLOCKED — 非 Windows 平台不支持 PostMessage 注入；"
          "请由会话层经 computer_control MCP 执行等价的窗口级操作。", file=sys.stderr)
    return EXIT_ERROR


def _window_candidates() -> list[tuple[int, int]]:
    """所有可能是 Minecraft 客户端的窗口，返回 (hwnd, pid)。"""
    user32 = ctypes.windll.user32
    found: list[tuple[int, int]] = []

    @ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    def _cb(hwnd, _lparam):
        if not user32.IsWindowVisible(hwnd):
            return True
        n = user32.GetWindowTextLengthW(hwnd)
        if n == 0:
            return True
        buf = ctypes.create_unicode_buffer(n + 1)
        user32.GetWindowTextW(hwnd, buf, n + 1)
        title = buf.value
        if "Minecraft" not in title:
            return True
        if any(x in title for x in ("Chrome", "Edge", "Wiki", "Firefox")):
            return True
        pid = wintypes.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        if _pid_is_java(pid.value):
            found.append((int(hwnd), int(pid.value)))
        return True

    user32.EnumWindows(_cb, 0)
    return found


def _find_window(version: str | None = None) -> int:
    """定位本次测试的 Minecraft 客户端窗口；无法唯一确定时返回 0。

    多候选是关键场景：用户自己的整合包客户端与本流程的 dev 客户端可能同时在跑，
    而两个窗口标题都含 "Minecraft"。此时「取第一个」会把按键投到错误的客户端，
    产生「看起来通过」的假结论——因此必须精确匹配而非猜测：

      version 指定 → 只认命令行匹配该版本 dev run 的进程（唯一权威依据）；
      未指定且候选 >1 → 返回 0，由调用方报错退出，不猜。
    """
    cands = _window_candidates()
    if not cands:
        return 0

    if version:
        p = Paths(version)
        markers = (p.subproject, str(p.run_dir), f"run{os.sep}{version}")
        cmd_by_pid = dict(java_procs())
        for hwnd, pid in cands:
            cmd = cmd_by_pid.get(pid, "")
            if any(m and m in cmd for m in markers):
                return hwnd
        return 0  # 有客户端，但都不是本版本的 dev 进程

    return cands[0][0] if len(cands) == 1 else 0


def _pid_is_java(pid: int) -> bool:
    rc, out = run_ps(
        f"(Get-Process -Id {pid} -ErrorAction SilentlyContinue).ProcessName",
        timeout=15,
    )
    if rc != 0 and not out.strip():
        return True  # 探测失败时不阻断，保持旧行为
    return "java" in out.lower()


def _post_key(hwnd: int, vk: int, scan: int) -> None:
    user32 = ctypes.windll.user32
    down = ctypes.c_void_p(1 | (scan << 16))
    up = ctypes.c_void_p((1 << 30) | (1 << 14) | (scan << 16))
    user32.PostMessageW(hwnd, WM_KEYDOWN, ctypes.c_void_p(vk), down)
    user32.PostMessageW(hwnd, WM_KEYUP, ctypes.c_void_p(vk), up)


def _post_key_down(hwnd: int, vk: int, scan: int) -> None:
    ctypes.windll.user32.PostMessageW(hwnd, WM_KEYDOWN, ctypes.c_void_p(vk),
                                      ctypes.c_void_p(1 | (scan << 16)))


def _post_key_up(hwnd: int, vk: int, scan: int) -> None:
    ctypes.windll.user32.PostMessageW(hwnd, WM_KEYUP, ctypes.c_void_p(vk),
                                      ctypes.c_void_p((1 << 30) | (1 << 14) | (1 << 14) | (scan << 16)))


def _click_center(hwnd: int, right: bool, shift: bool = False) -> None:
    user32 = ctypes.windll.user32
    if shift:
        _post_key_down(hwnd, VK_SHIFT, 0x2A)
        time.sleep(0.12)
    rect = _RECT()
    user32.GetWindowRect(hwnd, ctypes.byref(rect))
    x = (rect.right - rect.left) // 2
    y = (rect.bottom - rect.top) // 2
    lp = ctypes.c_void_p(((y << 16) | (x & 0xFFFF)))
    down = WM_RBUTTONDOWN if right else WM_LBUTTONDOWN
    up = WM_RBUTTONUP if right else WM_LBUTTONUP
    user32.PostMessageW(hwnd, down, ctypes.c_void_p(1), lp)
    time.sleep(0.1)
    user32.PostMessageW(hwnd, up, ctypes.c_void_p(0), lp)
    if shift:
        time.sleep(0.1)
        user32.PostMessageW(hwnd, WM_KEYUP, ctypes.c_void_p(VK_SHIFT),
                            ctypes.c_void_p((1 << 30) | (1 << 14) | (0x2A << 16)))


def _ensure_layout(hwnd: int, mode: str) -> tuple[bool, str]:
    """注入前的输入语言准备。

    mode=auto（默认）：把目标窗口线程切到 en-US（mt_ime，只影响该线程，
      不触碰用户系统默认输入法；线程随游戏进程退出而消失，无需恢复）。
    mode=as-is：不做任何切换，保持旧行为（排查用）。
    """
    if mode == "as-is":
        return True, "按 --layout as-is 跳过切换"
    try:
        import mt_ime
    except ImportError as exc:  # pragma: no cover
        return False, f"无法加载 mt_ime：{exc}"
    return mt_ime.set_window_us(hwnd)


def do_key(key: str, hold_ms: int, no_esc: bool = False,
           version: str | None = None, layout: str = "auto") -> int:
    if not _win32():
        return _degrade("key", {"key": key, "hold_ms": hold_ms})
    hwnd = _find_window(version)
    if not hwnd:
        print("MT_INJECT: ERROR — 未能唯一确定本版本 Minecraft 窗口"
              "（未找到，或存在多个候选客户端）", file=sys.stderr)
        return EXIT_ERROR

    ok, msg = _ensure_layout(hwnd, layout)
    print(f"MT_INJECT_LAYOUT: {'OK' if ok else 'FAIL'} — {msg}")
    if not ok:
        print(f"MT_INJECT: ERROR — 输入语言未就绪，拒绝注入（{msg}）",
              file=sys.stderr)
        return EXIT_ERROR

    k = KEY_ALIAS.get(key.lower(), key.lower())
    if k in ("attack", "rclick", "shift-rclick"):
        _click_center(hwnd, right=k in ("rclick", "shift-rclick"), shift=k == "shift-rclick")
        print(f"MT_INJECT_KEY: {k} (窗口中心)")
        return 0
    if k == "w":
        ms = hold_ms if hold_ms > 0 else 1000
        _post_key_down(hwnd, VK["w"], SCAN["w"])
        time.sleep(ms / 1000)
        _post_key_up(hwnd, VK["w"], SCAN["w"])
        print(f"MT_INJECT_KEY: w 按住 {ms}ms")
        return 0
    if k not in VK:
        print(f"MT_INJECT: ERROR — 未知按键 {key}", file=sys.stderr)
        return EXIT_ERROR
    _post_key(hwnd, VK[k], SCAN[k])
    print(f"MT_INJECT_KEY: {k}")
    return 0


def do_cmd(command: str, no_esc: bool, version: str | None = None,
           layout: str = "auto") -> int:
    if not _win32():
        return _degrade("cmd", {"command": command, "no_esc": no_esc})
    hwnd = _find_window(version)
    if not hwnd:
        print("MT_INJECT: ERROR — 未能唯一确定本版本 Minecraft 窗口"
              "（未找到，或存在多个候选客户端）", file=sys.stderr)
        return EXIT_ERROR

    ok, msg = _ensure_layout(hwnd, layout)
    print(f"MT_INJECT_LAYOUT: {'OK' if ok else 'FAIL'} — {msg}")
    if not ok:
        print(f"MT_INJECT: ERROR — 输入语言未就绪，拒绝注入（{msg}）",
              file=sys.stderr)
        return EXIT_ERROR

    # 目标选择会话激活期间 Esc 会取消选择，此时必须 --no-esc（斜杠命令已被白名单放行）
    if not no_esc:
        _post_key(hwnd, VK["escape"], SCAN["escape"]); time.sleep(0.15)
        _post_key(hwnd, VK["escape"], SCAN["escape"]); time.sleep(0.2)

    _post_key(hwnd, VK["slash"], SCAN["slash"])   # 斜杠键自带 "/" 前缀
    time.sleep(0.8)
    text = command[1:] if command.startswith("/") else command
    for ch in text:
        ctypes.windll.user32.PostMessageW(hwnd, WM_CHAR, ctypes.c_void_p(ord(ch)),
                                          ctypes.c_void_p(1))
    time.sleep(0.3)
    _post_key(hwnd, VK["enter"], SCAN["enter"])
    print(f"MT_INJECT_CMD: {command}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="mt 输入注入")
    sub = ap.add_subparsers(dest="mode", required=True)
    k = sub.add_parser("key"); k.add_argument("--key", required=True)
    k.add_argument("--hold-ms", type=int, default=0)
    c = sub.add_parser("cmd"); c.add_argument("--command", required=True)
    c.add_argument("--no-esc", action="store_true")
    for sp in (k, c):
        # 指定版本后只投入该版本 dev 客户端的窗口，避免多客户端时投错
        sp.add_argument("--version", default=None)
        # auto：注入前自动把目标窗口线程切到 en-US（默认）；as-is：保持旧行为
        sp.add_argument("--layout", choices=["auto", "as-is"], default="auto")
    args = ap.parse_args()

    if args.mode == "key":
        return do_key(args.key, args.hold_ms, version=args.version,
                      layout=args.layout)
    return do_cmd(args.command, args.no_esc, version=args.version,
                  layout=args.layout)


if __name__ == "__main__":
    raise SystemExit(main())
