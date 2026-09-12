#!/usr/bin/env python3
"""mt_ime — 目标窗口输入语言（键盘布局）管理。

## 为什么需要它

注入通道按**美式扫描码表**投递（见 mt_inject.py 的 SCAN/VK）。旧流程把「系统输入法
必须是 英语(美国)」写成硬前置，要靠人手动切 —— 这既脆弱（忘记切就整轮失败），
又会在流程中途给人增加一步操作。

本模块把这件事变成程序化、可验证的动作：**只切目标窗口所在线程的输入语言**，
不触碰用户的系统默认输入法与全局设置。

## 关键性质

- `GetKeyboardLayout` / `ActivateKeyboardLayout` 作用于**线程**，不是全局；
- 因此切完只影响被测游戏客户端，用户在别处的输入法不受影响（「不与现有功能冲突」）；
- 线程随进程退出而销毁，所以**无需恢复**：游戏进程结束，改动自然消失；
- 不会调用 `SystemParametersInfo(SPI_SETDEFAULTINPUTLANG)` 之类的全局接口。

## 用法

  mt_ime.py list                          # 已加载布局 + en-US 可用性
  mt_ime.py status --version 1.21.1       # 目标窗口线程当前布局
  mt_ime.py ensure --version 1.21.1       # 切到 en-US（幂等）
  mt_ime.py selftest                      # 跨进程自检（建临时窗口验证切换真实生效）
"""
from __future__ import annotations

import argparse
import ctypes
import sys
import time
from ctypes import wintypes
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from mt_paths import VERSIONS  # noqa: E402

EXIT_ERROR = 2

LANGID_EN_US = 0x0409
KLID_EN_US = "00000409"

WM_INPUTLANGCHANGEREQUEST = 0x0050
KLF_NOTELLSHELL = 0x0008

_PM_REMOVE = 0x0001


def _user32() -> ctypes.WinDLL:
    if sys.platform != "win32":
        raise RuntimeError("mt_ime 仅支持 Windows")
    return ctypes.WinDLL("user32", use_last_error=True)


# ══ 布局查询 ══════════════════════════════════════════════════════════════
def list_layouts() -> list[dict]:
    """当前已加载到本进程的键盘布局。"""
    u = _user32()
    n = u.GetKeyboardLayoutList(0, None)
    if n <= 0:
        return []
    buf = (wintypes.HKL * n)()
    u.GetKeyboardLayoutList(n, buf)
    out = []
    for hkl in buf:
        langid = hkl & 0xFFFF
        out.append({"hkl": int(hkl), "langid": langid,
                    "tag": f"{langid & 0x3FF:03x}{langid >> 10:02x}"})
    return out


def en_us_available() -> bool:
    """en-US 布局是否可用（已加载或可加载）。"""
    if any(l["langid"] == LANGID_EN_US for l in list_layouts()):
        return True
    return bool(_user32().LoadKeyboardLayoutW(KLID_EN_US, KLF_NOTELLSHELL))


def langid_of_thread(tid: int) -> int:
    return _user32().GetKeyboardLayout(tid) & 0xFFFF


def thread_of_window(hwnd: int) -> int:
    return _user32().GetWindowThreadProcessId(wintypes.HWND(hwnd), None)


def describe(langid: int) -> str:
    return {0x0409: "英语(美国) en-US", 0x0804: "中文(简体) zh-CN"}.get(
        langid, f"LANGID 0x{langid:04X}"
    )


# ══ 切换 ══════════════════════════════════════════════════════════════════
def _wait_langid(tid: int, want: int, timeout: float) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if langid_of_thread(tid) == want:
            return True
        time.sleep(0.05)
    return False


def set_window_us(hwnd: int, timeout: float = 1.0) -> tuple[bool, str]:
    """把 hwnd 所在线程的输入语言切到 en-US。返回 (是否成功, 说明)。

    两条路径依次尝试，任一成功即返回，并如实报告走的是哪条：
      1. 向窗口投递 WM_INPUTLANGCHANGEREQUEST（窗口/DefWindowProc 自行激活）；
      2. 退路：AttachThreadInput 附加到目标线程后 ActivateKeyboardLayout
         （对忽略该消息的窗口有效，是标准的跨线程强切手法）。
    """
    if sys.platform != "win32":
        return False, "非 Windows，改用会话层 MCP 通道"

    u = _user32()
    tid = thread_of_window(hwnd)
    if not tid:
        return False, f"无法取得窗口 {hwnd} 的线程"

    cur = langid_of_thread(tid)
    if cur == LANGID_EN_US:
        return True, "已是 en-US（无需切换）"

    hkl_us = u.LoadKeyboardLayoutW(KLID_EN_US, KLF_NOTELLSHELL)
    if not hkl_us:
        return False, ("en-US 布局未安装或无法加载 —— 请在系统设置中安装"
                       "「英语(美国)」键盘后重试")

    # 路径 1：请窗口自己切
    u.PostMessageW(wintypes.HWND(hwnd), WM_INPUTLANGCHANGEREQUEST, 0,
                   wintypes.LPARAM(hkl_us))
    if _wait_langid(tid, LANGID_EN_US, timeout):
        return True, f"已切换 0x{cur:04X} → en-US（消息路径）"

    # 路径 2：附加到目标线程后强切
    my_tid = ctypes.windll.kernel32.GetCurrentThreadId()
    attached = bool(u.AttachThreadInput(my_tid, tid, True))
    try:
        if attached:
            u.ActivateKeyboardLayout(wintypes.HKL(hkl_us), 0)
    finally:
        if attached:
            u.AttachThreadInput(my_tid, tid, False)

    if _wait_langid(tid, LANGID_EN_US, timeout):
        return True, f"已切换 0x{cur:04X} → en-US（线程附加路径）"

    return False, (f"切换失败：线程 {tid} 仍为 0x{langid_of_thread(tid):04X}"
                   "（窗口可能拒绝输入语言变更）")


def ensure_for_window(version: str | None = None, hwnd: int | None = None
                      ) -> tuple[bool, str]:
    """注入前置：确保目标窗口线程为 en-US。供 mt_inject 调用。"""
    if hwnd is None:
        from mt_inject import _find_window  # 延迟导入，避免循环依赖
        hwnd = _find_window(version)
    if not hwnd:
        return False, "未找到目标窗口（或存在多个候选客户端）"
    return set_window_us(hwnd)


# ══ 状态查询 ══════════════════════════════════════════════════════════════
def cmd_list() -> int:
    layouts = list_layouts()
    print(f"已加载布局 {len(layouts)} 个：")
    for l in layouts:
        mark = "   ← en-US" if l["langid"] == LANGID_EN_US else ""
        print(f"    HKL=0x{l['hkl']:08X}  LANGID=0x{l['langid']:04X}"
              f"  KLID={l['tag']}{mark}")
    ok = en_us_available()
    print(f"\nen-US({KLID_EN_US}) 可用：{'是' if ok else '否 —— 需在系统设置安装'}")
    return 0 if ok else EXIT_ERROR


def cmd_status(args) -> int:
    if sys.platform != "win32":
        print("MT_IME: 非 Windows 平台，跳过", file=sys.stderr)
        return EXIT_ERROR
    if args.hwnd:
        hwnd = args.hwnd
    else:
        from mt_inject import _find_window
        hwnd = _find_window(args.version)
    if not hwnd:
        print("MT_IME: ERROR — 未找到目标窗口", file=sys.stderr)
        return EXIT_ERROR
    tid = thread_of_window(hwnd)
    langid = langid_of_thread(tid)
    print(f"MT_IME: hwnd={hwnd} tid={tid} langid=0x{langid:04X}"
          f"（{describe(langid)}）")
    return 0 if langid == LANGID_EN_US else 1


def cmd_ensure(args) -> int:
    ok, msg = ensure_for_window(args.version, args.hwnd)
    print(f"MT_IME_ENSURE: {'OK' if ok else 'FAIL'} — {msg}")
    return 0 if ok else 1


# ══ 自检：跨进程验证切换真的生效 ══════════════════════════════════════════
_WNDPROC = ctypes.WINFUNCTYPE(ctypes.c_longlong, wintypes.HWND, wintypes.UINT,
                              wintypes.WPARAM, wintypes.LPARAM)


class _WNDCLASSEXW(ctypes.Structure):
    _fields_ = [
        ("cbSize", wintypes.UINT), ("style", wintypes.UINT),
        ("lpfnWndProc", _WNDPROC), ("cbClsExtra", ctypes.c_int),
        ("cbWndExtra", ctypes.c_int), ("hInstance", wintypes.HINSTANCE),
        ("hIcon", wintypes.HICON), ("hCursor", wintypes.HANDLE),
        ("hbrBackground", wintypes.HBRUSH), ("lpszMenuName", wintypes.LPCWSTR),
        ("lpszClassName", wintypes.LPCWSTR), ("hIconSm", wintypes.HICON),
    ]


def _hostwin(report: Path) -> int:
    """自检用的宿主进程：建一个真实窗口并持续汇报自身线程的输入语言。"""
    u = _user32()
    k = ctypes.WinDLL("kernel32", use_last_error=True)
    hinst = k.GetModuleHandleW(None)
    name = "mtImeSelfTestWnd"

    def _proc(hwnd, msg, wp, lp):
        return u.DefWindowProcW(hwnd, msg, wp, lp)

    wndproc = _WNDPROC(_proc)
    wc = _WNDCLASSEXW()
    wc.cbSize = ctypes.sizeof(_WNDCLASSEXW)
    wc.lpfnWndProc = wndproc
    wc.hInstance = hinst
    wc.lpszClassName = name
    if not u.RegisterClassExW(ctypes.byref(wc)):
        print("REGISTER_FAIL", flush=True)
        return EXIT_ERROR

    hwnd = u.CreateWindowExW(0, name, "mt-ime-selftest", 0, 0, 0, 200, 200,
                             None, None, hinst, None)
    if not hwnd:
        print("CREATE_FAIL", flush=True)
        return EXIT_ERROR

    tid = thread_of_window(hwnd)
    report.write_text("", encoding="utf-8")
    print(f"HWND={hwnd} TID={tid}", flush=True)

    msg = wintypes.MSG()
    deadline = time.time() + 40
    seen: list[int] = []
    while time.time() < deadline:
        while u.PeekMessageW(ctypes.byref(msg), None, 0, 0, _PM_REMOVE):
            u.TranslateMessage(ctypes.byref(msg))
            u.DispatchMessageW(ctypes.byref(msg))
        langid = langid_of_thread(tid)
        if not seen or seen[-1] != langid:
            seen.append(langid)
            with report.open("a", encoding="utf-8") as fh:
                fh.write(f"{time.time():.3f} 0x{langid:04X}\n")
        time.sleep(0.05)
    return 0


def _selftest() -> int:
    """跨进程自检：子进程建真窗口 → 父进程切语言 → 核对 ↔ 父线程不受影响。"""
    import subprocess

    if sys.platform != "win32":
        print("MT_IME_SELFTEST: SKIP — 仅 Windows")
        return 0

    report = Path(__file__).resolve().parent / ".." / "temp" / "_ime_selftest_report.txt"
    report = report.resolve()
    report.parent.mkdir(parents=True, exist_ok=True)

    my_langid_before = langid_of_thread(ctypes.windll.kernel32.GetCurrentThreadId())

    proc = subprocess.Popen(
        [sys.executable, str(Path(__file__).resolve()), "_hostwin",
         "--report", str(report)],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, encoding="utf-8", errors="replace",
    )
    try:
        line = ""
        deadline = time.time() + 20
        while time.time() < deadline:
            line = proc.stdout.readline().strip()
            if line.startswith("HWND="):
                break
        if not line.startswith("HWND="):
            print(f"MT_IME_SELFTEST: FAIL — 宿主窗口未就绪（{line!r}）")
            return 1

        parts = dict(kv.split("=") for kv in line.split())
        hwnd, tid = int(parts["HWND"]), int(parts["TID"])
        print(f"  宿主窗口 hwnd={hwnd} tid={tid}")
        before = langid_of_thread(tid)
        print(f"  切换前：0x{before:04X}（{describe(before)}）")

        ok, msg = set_window_us(hwnd)
        print(f"  切换调用：{'OK' if ok else 'FAIL'} — {msg}")
        after = langid_of_thread(tid)
        print(f"  切换后：0x{after:04X}（{describe(after)}）")

        time.sleep(0.4)
        seq = report.read_text(encoding="utf-8").strip().splitlines()

        my_langid_after = langid_of_thread(ctypes.windll.kernel32.GetCurrentThreadId())
        print(f"  本进程线程语言：0x{my_langid_before:04X} → 0x{my_langid_after:04X}"
              f"（应保持不变）")

        checks = [
            ("目标线程变为 en-US", after == LANGID_EN_US),
            ("宿主进程自行观测到变化（跨进程生效）",
             any("0x0409" in s for s in seq)),
            ("本进程输入语言未被改动（不与现有功能冲突）",
             my_langid_before == my_langid_after),
        ]
        bad = 0
        print()
        for name, passed in checks:
            print(f"  [{'PASS' if passed else 'FAIL'}] {name}")
            bad += 0 if passed else 1
        if seq:
            print(f"  宿主进程观测序列：{seq}")
        print(f"\nMT_IME_SELFTEST: {'ALL PASS' if bad == 0 else f'{bad} 项不符'}")
        return 0 if bad == 0 else 1
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()


def main() -> int:
    ap = argparse.ArgumentParser(description="mt 输入语言（键盘布局）管理")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list")

    st = sub.add_parser("status")
    st.add_argument("--version", choices=list(VERSIONS))
    st.add_argument("--hwnd", type=int)

    en = sub.add_parser("ensure")
    en.add_argument("--version", choices=list(VERSIONS))
    en.add_argument("--hwnd", type=int)

    sub.add_parser("selftest")

    hw = sub.add_parser("_hostwin")
    hw.add_argument("--report", required=True)

    args = ap.parse_args()
    if args.cmd == "list":
        return cmd_list()
    if args.cmd == "status":
        return cmd_status(args)
    if args.cmd == "ensure":
        return cmd_ensure(args)
    if args.cmd == "selftest":
        return _selftest()
    if args.cmd == "_hostwin":
        return _hostwin(Path(args.report))
    return EXIT_ERROR


if __name__ == "__main__":
    raise SystemExit(main())
