#!/usr/bin/env python3
"""mt_capture —— 截图取证（python 复原版，等价 scripts/test/mt_capture.ps1）。

两种模式：
  f2      向本版本游戏窗口投递 F2（user32 PostMessageW，VK 0x71 / 扫描码 0x3C），
          等 run/<版本>/screenshots/*.png 落盘（默认 3 次尝试，每次 10s 窗口）；
          全部失败则打 WARN 并**降级为窗口抓取**（与 ps1 / python 原件一致）。
  window  抓窗口（默认全屏；--crop-to-window 才按窗口矩形裁剪）。

截图按「本次运行世代」（cases/.mt_active_run）登记到 <shot_dir>/.mt_shots.json，
断言侧用 lib/mt_compat.current_shots 读；prune 只保留当前世代。

机器行（与 ps1 逐字同形）：
    MT_CAPTURE: OK — <文件> (tag=<tag>, run=<run>, mode=f2, attempt=<n>)
    MT_CAPTURE: OK — <文件> (tag=<tag>, mode=window)
    MT_CAPTURE: WARN — <原因>（已重试 <n> 次，<shot_dir>），改用窗口抓取     （stderr）
    MT_CAPTURE: WARN — 未定位到本版本游戏窗口，抓取的可能是桌面其它内容      （stderr）
    MT_CAPTURE: ERROR — 窗口抓取失败：<异常>                                 （stderr，退 2）
    MT_CAPTURE: ERROR — 当前世代无截图                                       （stderr，退 2）
    MT_CAPTURE: 当前世代（<run>）无截图
    MT_CAPTURE: 已清理 <n> 张历史截图，保留当前世代 <m> 张
    MT_CAPTURE: 无可清理内容

退出码：0 = 成功；2 = 参数错误 / 抓取失败（与 ps1 一致：除 capture/list 失败分支外都是 0）。
参数名归一化与 ps1 一致：去掉前导 '-' 后**删除全部连字符**再小写，
所以 --list-latest / -ListLatest / --list_latest 等价。

与 ps1 的两处**有意差异**：
  * --mode window 不需要 Pillow：本文件用 GDI（CreateCompatibleDC + BitBlt + GetDIBits）
    自己抓屏，再用 zlib 写 PNG。ps1 用 .NET Graphics.CopyFromScreen；python 原件用 Pillow。
    三者产物都是「可解码 + 同尺寸」，不是逐字节相等（ps1 头部已声明该判据）。
  * DPI：抓屏前把**本线程**上下文设为 PER_MONITOR_AWARE_V2(-4)，抓完恢复。本机 3840x2160/150%，
    不做这一步会拿到 2560x1440（ps1 头部记录的同一问题）。
  * 增加 -h/--help（ps1 没有；本仓约定每个 python 入口 --help 退出 0）。
  * --keep 与 ps1 一样**只解析不使用**（python 原件的已知缺陷，ps1 特意保留同一行为）。

本文件不调用任何 .ps1 / .psm1（F2 注入用 ctypes 内联实现，不依赖 mt_inject）。
"""

import ctypes
import json
import os
import struct
import sys
import time
import zlib

TEST_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(TEST_DIR, "lib"))

import mt_compat as mc  # noqa: E402

EXIT_ERROR = mc.MT_EXIT_ERROR
EXIT_PASS = mc.MT_EXIT_PASS

DEFAULT_TIMEOUT = 10.0
DEFAULT_ATTEMPTS = 3

VK_F2 = 0x71
SCAN_F2 = 0x3C
WM_KEYDOWN = 0x0100
WM_KEYUP = 0x0101

SW_RESTORE = 9
HWND_TOPMOST = -1
HWND_NOTOPMOST = -2
SWP_NOSIZE = 0x0001
SWP_NOMOVE = 0x0002

SM_CXSCREEN = 0
SM_CYSCREEN = 1
SRCCOPY = 0x00CC0020
CAPTUREBLT = 0x40000000
BI_RGB = 0
DIB_RGB_COLORS = 0
PER_MONITOR_AWARE_V2 = -4


def usage():
    return chr(10).join([
        "用法: python scripts/test/mt_capture.py (capture|list|prune) [选项]",
        "",
        "截图取证（等价 scripts/test/mt_capture.ps1）。",
        "",
        "子命令:",
        "  capture  拍一张（--mode f2 游戏内 F2；--mode window 窗口抓取）",
        "  list     列当前世代（cases/.mt_active_run）的截图；--list-latest 只打最新一张的全路径",
        "  prune    删除不属于当前世代的历史截图并重写清单（--keep 只解析不使用，同 ps1）",
        "",
        "选项（参数名连字符不敏感：--list-latest == -ListLatest == --list_latest）:",
        "  --version <v>       必填：1.21.1 | 1.20.1 | 26.1.2 | fabric-1.20.1",
        "  --tag <名>          capture 必填：截图标签（决定文件名 <tag>_<run>.png / 断言按 tag 取图）",
        "  --mode <f2|window>  默认 f2",
        "  --keep <n>          默认 3（保留项，当前实现不参与删除判定，与 ps1 一致）",
        "  --hwnd <十进制>     指定窗口句柄（默认 0 = 自动定位本版本窗口）",
        "  --list-latest       只打印最新一张截图的完整路径",
        "  --crop-to-window    窗口模式按窗口矩形裁剪（默认抓整屏）",
        "  -h, --help          显示本帮助",
        "",
        "退出码: 0 = 成功 | 2 = 参数错误 / 抓取失败（同 ps1）",
        "",
        "能力缺失: 非 Windows / 未找到窗口 / 抓屏失败都退 2 并打 MT_CAPTURE: ERROR，",
        "          不会返回 0 假装拍到了。F2 失败会先 WARN 再降级窗口抓取。",
    ])


# ── 清单 ─────────────────────────────────────────────────────────────────
def manifest_path(paths):
    return os.path.join(paths.shot_dir, mc.SHOTS_MANIFEST)


def read_manifest(paths):
    """只读清单，容忍 list / 裸 dict / 损坏文件（与 mt_compat 同形）。"""
    path = manifest_path(paths)
    data = {"run_id": "", "shots": []}
    if not os.path.isfile(path):
        return data
    try:
        with open(path, "rb") as fh:
            parsed = json.loads(fh.read().decode("utf-8-sig", "replace"))
    except (OSError, ValueError):
        return data
    if isinstance(parsed, list):
        parsed = parsed[0] if parsed else None
    if not isinstance(parsed, dict):
        return data
    rid = parsed.get("run_id")
    data["run_id"] = "" if rid is None else str(rid)
    shots = parsed.get("shots")
    if isinstance(shots, list):
        data["shots"] = shots
    elif shots is not None:
        data["shots"] = [shots]
    return data


def write_manifest(paths, run_id, shots):
    path = manifest_path(paths)
    try:
        os.makedirs(paths.shot_dir, exist_ok=True)
        payload = {"run_id": run_id, "shots": shots}
        text = json.dumps(payload, ensure_ascii=False, indent=2)
        with open(path, "w", encoding="utf-8", newline="") as fh:
            fh.write(text)
    except OSError as exc:
        mc.write_mt_warn("MT_CAPTURE: WARN — 写入截图清单失败：" + str(exc))


def record_shot(paths, name, tag, run_id, ts=None):
    data = read_manifest(paths)
    shots = data.get("shots", []) if data.get("run_id") == run_id else []
    shots = [s for s in shots if not (isinstance(s, dict) and str(s.get("file")) == name)]
    shots.append({"file": name, "tag": tag,
                  "ts": float(time.time() if ts is None else ts)})
    write_manifest(paths, run_id, shots)


def tag_map(paths):
    mapping = {}
    for item in read_manifest(paths).get("shots", []):
        if isinstance(item, dict):
            name = item.get("file")
            if name is not None:
                mapping[str(name)] = str(item.get("tag") or "")
    return mapping


# ── Win32 ────────────────────────────────────────────────────────────────
def user32():
    return ctypes.WinDLL("user32", use_last_error=True)


def kernel32():
    return ctypes.WinDLL("kernel32", use_last_error=True)


def gdi32():
    return ctypes.WinDLL("gdi32", use_last_error=True)


def _window_titles():
    """{pid: title}：EnumWindows + GetWindowTextW（只取可见顶层窗口）。"""
    if not mc.IS_WINDOWS:
        return {}
    u = user32()
    found = {}

    @ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
    def _cb(hwnd, _lparam):
        if not u.IsWindowVisible(hwnd):
            return True
        length = u.GetWindowTextLengthW(hwnd)
        if length <= 0:
            return True
        buf = ctypes.create_unicode_buffer(length + 1)
        u.GetWindowTextW(hwnd, buf, length + 1)
        title = buf.value.strip()
        if not title:
            return True
        pid = ctypes.c_ulong(0)
        u.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        found[int(hwnd)] = (int(pid.value), title)
        return True

    u.EnumWindows(_cb, 0)
    return found


def find_window(version):
    """Find-MtMinecraftWindow：标题含 Minecraft + 非浏览器 + 属于 java 进程 + 版本标记命中。

    只在**唯一**候选时才接受「标题里含版本号」的回落（FML dev 启动的命令行很短）；
    多个候选一律返回 0 —— 不猜。
    """
    if not mc.IS_WINDOWS:
        return 0
    try:
        procs = mc.list_java_processes()
    except (mc.ProcessEnumerationUnavailable, OSError):
        return 0
    cmd_of = {}
    for item in procs:
        try:
            cmd_of[int(item.get("pid"))] = str(item.get("command_line") or "")
        except (TypeError, ValueError):
            continue
    try:
        paths = mc.get_paths(version)
        markers = [m for m in mc.process_markers(paths) if m]
    except (ValueError, OSError):
        markers = []
    excluded = ("chrome", "edge", "wiki", "firefox")
    candidates = []
    for hwnd, (pid, title) in _window_titles().items():
        low = title.lower()
        if "minecraft" not in low:
            continue
        if any(word in low for word in excluded):
            continue
        if pid not in cmd_of:
            continue
        candidates.append((hwnd, pid, title, cmd_of[pid]))
    matched = [c for c in candidates
               if any(m in c[3] for m in markers)]
    if len(matched) == 1:
        return matched[0][0]
    if not matched and len(candidates) == 1:
        if version.lower() in candidates[0][2].lower():
            return candidates[0][0]
    return 0


def raise_window(hwnd):
    """Invoke-MtRaiseWindow：还原 + 置顶抖动 + AttachThreadInput 抢前台/焦点。"""
    if not hwnd or not mc.IS_WINDOWS:
        return 0
    u = user32()
    k = kernel32()
    u.ShowWindow(ctypes.c_void_p(hwnd), SW_RESTORE)
    u.SetWindowPos(ctypes.c_void_p(hwnd), ctypes.c_void_p(HWND_TOPMOST), 0, 0, 0, 0,
                   SWP_NOSIZE | SWP_NOMOVE)
    u.SetWindowPos(ctypes.c_void_p(hwnd), ctypes.c_void_p(HWND_NOTOPMOST), 0, 0, 0, 0,
                   SWP_NOSIZE | SWP_NOMOVE)
    u.BringWindowToTop(ctypes.c_void_p(hwnd))
    tid_self = int(k.GetCurrentThreadId())
    tid_fg = int(u.GetWindowThreadProcessId(u.GetForegroundWindow(), None) or 0)
    tid_target = int(u.GetWindowThreadProcessId(ctypes.c_void_p(hwnd), None) or 0)
    attached = []
    try:
        for tid in (tid_fg, tid_target):
            if tid and tid != tid_self and tid not in attached:
                if u.AttachThreadInput(tid_self, tid, True):
                    attached.append(tid)
        u.SetForegroundWindow(ctypes.c_void_p(hwnd))
        u.SetFocus(ctypes.c_void_p(hwnd))
    finally:
        for tid in attached:
            u.AttachThreadInput(tid_self, tid, False)
    time.sleep(0.6)
    return hwnd


def ensure_foreground(hwnd, attempts=3):
    """Assert-MtInjectForeground：前台 != 焦点时 GLFW 会丢弃按键，所以必须同时抢到两者。"""
    if not mc.IS_WINDOWS or not hwnd:
        return False
    u = user32()
    for _i in range(max(1, attempts)):
        try:
            raise_window(hwnd)
        except OSError:
            pass
        if int(u.GetForegroundWindow() or 0) == int(hwnd):
            return True
        time.sleep(0.12)
    mc.write_mt_err_line("MT_INJECT_FOCUS: FAIL — 目标窗口无法置前台，按键会被游戏忽略")
    return False


def post_f2(hwnd):
    """F2 按下 + 抬起。lParam 编码与 mt_inject.ps1 逐位一致。"""
    u = user32()
    down = 1 | (SCAN_F2 << 16)
    up = (1 << 30) | (1 << 14) | (SCAN_F2 << 16)
    ok_down = bool(u.PostMessageW(ctypes.c_void_p(hwnd), WM_KEYDOWN, VK_F2, down))
    ok_up = bool(u.PostMessageW(ctypes.c_void_p(hwnd), WM_KEYUP, VK_F2, up))
    return ok_down and ok_up


def ensure_en_us(hwnd):
    """输入语言前置：按美式扫描码投递，布局不对时先切（只动目标线程）。"""
    try:
        import mt_ime
    except ImportError as exc:
        return False, "无法导入 lib/mt_ime.py：" + str(exc)
    try:
        if not mt_ime.en_us_available():
            return False, "系统未安装/无法加载 英语(美国) 键盘布局"
        ok, msg = mt_ime.set_window_us(hwnd)
        return bool(ok), str(msg)
    except OSError as exc:
        return False, str(exc)


class _BITMAPINFOHEADER(ctypes.Structure):
    _fields_ = [
        ("biSize", ctypes.c_uint32), ("biWidth", ctypes.c_int32),
        ("biHeight", ctypes.c_int32), ("biPlanes", ctypes.c_uint16),
        ("biBitCount", ctypes.c_uint16), ("biCompression", ctypes.c_uint32),
        ("biSizeImage", ctypes.c_uint32), ("biXPelsPerMeter", ctypes.c_int32),
        ("biYPelsPerMeter", ctypes.c_int32), ("biClrUsed", ctypes.c_uint32),
        ("biClrImportant", ctypes.c_uint32),
    ]


class _BITMAPINFO(ctypes.Structure):
    _fields_ = [("bmiHeader", _BITMAPINFOHEADER), ("bmiColors", ctypes.c_uint32 * 3)]


def _png_encode(width, height, bgra):
    """BGRA（自上而下）→ PNG（8bit RGBA），纯标准库 zlib。"""
    stride = width * 4
    raw = bytearray()
    for y in range(height):
        row = bgra[y * stride:(y + 1) * stride]
        rgba = bytearray(stride)
        rgba[0::4] = row[2::4]
        rgba[1::4] = row[1::4]
        rgba[2::4] = row[0::4]
        rgba[3::4] = row[3::4]
        raw.append(0)
        raw += rgba

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 6)) + chunk(b"IEND", b""))


def grab_screen(rect=None):
    """抓屏（GDI）。rect=(x,y,w,h)；None = 主屏全屏。返回 PNG bytes。"""
    u = user32()
    g = gdi32()
    k = kernel32()
    prev = None
    try:
        prev = u.SetThreadDpiAwarenessContext(ctypes.c_void_p(PER_MONITOR_AWARE_V2))
    except (AttributeError, OSError):
        prev = None
    try:
        if rect:
            x, y, width, height = rect
        else:
            x, y = 0, 0
            width = int(u.GetSystemMetrics(SM_CXSCREEN))
            height = int(u.GetSystemMetrics(SM_CYSCREEN))
        if width <= 0 or height <= 0:
            raise RuntimeError("屏幕尺寸非法：" + str(width) + "x" + str(height))
        screen_dc = u.GetDC(None)
        if not screen_dc:
            raise RuntimeError("GetDC 失败（err=" + str(ctypes.get_last_error()) + "）")
        mem_dc = g.CreateCompatibleDC(ctypes.c_void_p(screen_dc))
        if not mem_dc:
            u.ReleaseDC(None, ctypes.c_void_p(screen_dc))
            raise RuntimeError("CreateCompatibleDC 失败")
        bmp = g.CreateCompatibleBitmap(ctypes.c_void_p(screen_dc), width, height)
        if not bmp:
            g.DeleteDC(ctypes.c_void_p(mem_dc))
            u.ReleaseDC(None, ctypes.c_void_p(screen_dc))
            raise RuntimeError("CreateCompatibleBitmap 失败")
        old = g.SelectObject(ctypes.c_void_p(mem_dc), ctypes.c_void_p(bmp))
        try:
            ok = g.BitBlt(ctypes.c_void_p(mem_dc), 0, 0, width, height,
                          ctypes.c_void_p(screen_dc), x, y, SRCCOPY | CAPTUREBLT)
            if not ok:
                raise RuntimeError("BitBlt 失败（err=" + str(ctypes.get_last_error()) + "）")
            info = _BITMAPINFO()
            info.bmiHeader.biSize = ctypes.sizeof(_BITMAPINFOHEADER)
            info.bmiHeader.biWidth = width
            info.bmiHeader.biHeight = -height
            info.bmiHeader.biPlanes = 1
            info.bmiHeader.biBitCount = 32
            info.bmiHeader.biCompression = BI_RGB
            buf = ctypes.create_string_buffer(width * height * 4)
            lines = g.GetDIBits(ctypes.c_void_p(mem_dc), ctypes.c_void_p(bmp), 0, height,
                                buf, ctypes.byref(info), DIB_RGB_COLORS)
            if not lines:
                raise RuntimeError("GetDIBits 失败（err=" + str(ctypes.get_last_error()) + "）")
            return _png_encode(width, height, buf.raw)
        finally:
            g.SelectObject(ctypes.c_void_p(mem_dc), ctypes.c_void_p(old))
            g.DeleteObject(ctypes.c_void_p(bmp))
            g.DeleteDC(ctypes.c_void_p(mem_dc))
            u.ReleaseDC(None, ctypes.c_void_p(screen_dc))
    finally:
        if prev:
            try:
                u.SetThreadDpiAwarenessContext(ctypes.c_void_p(prev))
            except (AttributeError, OSError):
                pass


def window_rect(hwnd):
    if not hwnd:
        return None
    u = user32()
    rect = (ctypes.c_long * 4)()
    if not u.GetWindowRect(ctypes.c_void_p(hwnd), ctypes.byref(rect)):
        return None
    left, top, right, bottom = rect[0], rect[1], rect[2], rect[3]
    width = int(right - left)
    height = int(bottom - top)
    if width <= 0 or height <= 0:
        return None
    return (int(left), int(top), width, height)


def newest_png(shot_dir, since):
    best = None
    best_mtime = float("-inf")
    try:
        names = os.listdir(shot_dir)
    except OSError:
        return None
    for name in names:
        if not name.lower().endswith(".png"):
            continue
        full = os.path.join(shot_dir, name)
        try:
            mtime = os.path.getmtime(full)
        except OSError:
            continue
        if mtime < since:
            continue
        if mtime > best_mtime:
            best_mtime = mtime
            best = full
    return best


# ── 抓取 ─────────────────────────────────────────────────────────────────
def capture_f2(paths, tag, run_id, timeout=DEFAULT_TIMEOUT, attempts=DEFAULT_ATTEMPTS,
               hwnd=0):
    last_err = ""
    for attempt in range(1, max(1, attempts) + 1):
        hwnd_now = hwnd or find_window(paths.version)
        if not hwnd_now:
            last_err = "未定位到本版本游戏窗口"
            time.sleep(0.3)
            continue
        ok, msg = ensure_en_us(hwnd_now)
        if not ok:
            mc.write_mt_err_line("MT_INJECT: ERROR — 输入语言未就绪，拒绝注入（" + msg + "）")
            last_err = "F2 注入失败（输入语言未就绪）"
            continue
        if not ensure_foreground(hwnd_now):
            last_err = "F2 注入失败（目标窗口未取得前台，失焦时 GLFW 会丢弃按键）"
            continue
        before = time.time() - 1.0
        try:
            sent = post_f2(hwnd_now)
        except OSError as exc:
            sent = False
            last_err = "F2 注入失败（" + str(exc) + "）"
        if sent:
            deadline = time.time() + timeout
            while time.time() < deadline:
                shot = newest_png(paths.shot_dir, before)
                if shot:
                    name = os.path.basename(shot)
                    record_shot(paths, name, tag, run_id)
                    mc.write_mt_line("MT_CAPTURE: OK — " + name + " (tag=" + tag
                                     + ", run=" + run_id + ", mode=f2, attempt="
                                     + str(attempt) + ")")
                    return EXIT_PASS
                time.sleep(0.5)
            last_err = str(int(timeout)) + "s 内无 F2 落盘"
        time.sleep(0.2)
    mc.write_mt_err_line("MT_CAPTURE: WARN — " + (last_err or "F2 注入失败")
                         + "（已重试 " + str(attempts) + " 次，" + paths.shot_dir
                         + "），改用窗口抓取")
    return capture_window(paths, tag, run_id, hwnd=hwnd)


def capture_window(paths, tag, run_id, hwnd=0, crop=False):
    if not mc.IS_WINDOWS:
        mc.write_mt_err_line("MT_CAPTURE: ERROR — 非 Windows 平台，窗口抓取不可用；改用 --mode f2")
        return EXIT_ERROR
    resolved = hwnd or find_window(paths.version)
    if not resolved:
        mc.write_mt_err_line("MT_CAPTURE: WARN — 未定位到本版本游戏窗口，抓取的可能是桌面其它内容")
    else:
        try:
            raise_window(resolved)
        except OSError:
            pass
    rect = window_rect(resolved) if (crop and resolved) else None
    dst = os.path.join(paths.shot_dir, tag + "_" + run_id + ".png")
    try:
        os.makedirs(paths.shot_dir, exist_ok=True)
        data = grab_screen(rect)
        with open(dst, "wb") as fh:
            fh.write(data)
    except (OSError, RuntimeError, ValueError) as exc:
        mc.write_mt_err_line("MT_CAPTURE: ERROR — 窗口抓取失败：" + str(exc))
        return EXIT_ERROR
    record_shot(paths, os.path.basename(dst), tag, run_id)
    mc.write_mt_line("MT_CAPTURE: OK — " + os.path.basename(dst) + " (tag=" + tag
                     + ", mode=window)")
    return EXIT_PASS


# ── 子命令 ───────────────────────────────────────────────────────────────
def cmd_capture(version, tag, mode, hwnd, crop):
    paths = mc.get_paths(version)
    run_id = mc.active_run_id()
    if mode == "window":
        return capture_window(paths, tag, run_id, hwnd=hwnd, crop=crop)
    return capture_f2(paths, tag, run_id, hwnd=hwnd)


def cmd_list(version, list_latest):
    paths = mc.get_paths(version)
    run_id = mc.active_run_id()
    shots = mc.current_shots(paths, run_id)
    if list_latest:
        if not shots:
            mc.write_mt_err_line("MT_CAPTURE: ERROR — 当前世代无截图")
            return EXIT_ERROR
        mc.write_mt_line(shots[-1])
        return EXIT_PASS
    if not shots:
        mc.write_mt_line("MT_CAPTURE: 当前世代（" + run_id + "）无截图")
        return EXIT_PASS
    tags = tag_map(paths)
    for full in shots:
        name = os.path.basename(full)
        mc.write_mt_line(name + chr(9) + tags.get(name, ""))
    return EXIT_PASS


def cmd_prune(version, keep):
    paths = mc.get_paths(version)
    run_id = mc.active_run_id()
    shots = mc.current_shots(paths, run_id)
    keep_names = set(os.path.basename(p) for p in shots)
    if not os.path.isdir(paths.shot_dir):
        mc.write_mt_line("MT_CAPTURE: 无可清理内容")
        return EXIT_PASS
    removed = 0
    for name in sorted(os.listdir(paths.shot_dir)):
        if not name.lower().endswith(".png") or name in keep_names:
            continue
        try:
            os.remove(os.path.join(paths.shot_dir, name))
            removed += 1
        except OSError:
            continue
    if keep_names:
        data = read_manifest(paths)
        kept = [s for s in data.get("shots", [])
                if isinstance(s, dict) and str(s.get("file")) in keep_names]
        write_manifest(paths, run_id if data.get("run_id") != run_id else data.get("run_id"),
                        kept)
    mc.write_mt_line("MT_CAPTURE: 已清理 " + str(removed) + " 张历史截图，保留当前世代 "
                     + str(len(keep_names)) + " 张")
    return EXIT_PASS


def main(argv):
    mc.init_console()
    cmd = ""
    version = ""
    tag = ""
    mode = "f2"
    keep = 3
    hwnd = 0
    list_latest = False
    crop = False
    index = 0
    while index < len(argv):
        token = argv[index]
        if token.startswith("-"):
            opt = token.lstrip("-").lower().replace("-", "")
            if opt in ("h", "help"):
                mc.write_mt_line(usage())
                return EXIT_PASS
            if opt in ("version", "tag", "mode", "keep", "hwnd"):
                index += 1
                if index >= len(argv):
                    mc.write_mt_error_line("缺少 --" + opt + " 的值")
                    return EXIT_ERROR
                value = argv[index]
                if opt == "version":
                    version = value
                elif opt == "tag":
                    tag = value
                elif opt == "mode":
                    low = value.lower()
                    if low not in ("f2", "window"):
                        mc.write_mt_error_line("非法 --mode 值 " + value + "（可选：f2 window）")
                        return EXIT_ERROR
                    mode = low
                elif opt == "keep":
                    try:
                        keep = int(value)
                    except ValueError:
                        mc.write_mt_error_line("参数 --keep 需要整数，收到 " + value)
                        return EXIT_ERROR
                else:
                    try:
                        hwnd = int(value)
                    except ValueError:
                        mc.write_mt_error_line("参数 --hwnd 需要整数，收到 " + value)
                        return EXIT_ERROR
                index += 1
                continue
            if opt == "listlatest":
                list_latest = True
                index += 1
                continue
            if opt == "croptowindow":
                crop = True
                index += 1
                continue
            mc.write_mt_error_line("未知参数 " + token)
            return EXIT_ERROR
        if cmd:
            mc.write_mt_error_line("未知参数 " + token)
            return EXIT_ERROR
        cmd = token.lower()
        index += 1

    if cmd not in ("capture", "list", "prune"):
        if not cmd:
            mc.write_mt_error_line("缺少子命令（可选：capture list prune）")
        else:
            mc.write_mt_error_line("未知子命令 " + cmd + "（可选：capture list prune）")
        return EXIT_ERROR
    if not version:
        mc.write_mt_error_line("缺少必填参数 --version")
        return EXIT_ERROR
    if not mc.assert_version(version):
        return EXIT_ERROR
    if cmd == "capture":
        if not tag:
            mc.write_mt_error_line("缺少必填参数 --tag")
            return EXIT_ERROR
        return cmd_capture(version, tag, mode, hwnd, crop)
    if cmd == "list":
        return cmd_list(version, list_latest)
    return cmd_prune(version, keep)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
