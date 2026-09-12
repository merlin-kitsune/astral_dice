#!/usr/bin/env python3
"""mt_capture — 截图采集与「世代」管理。

不再依赖任何固定截图目录的环境变量配置：每次运行由 mt_paths 分配唯一 run id，
截图在采集时登记进该运行的世代清单（<截图目录>/.mt_shots.json）。
断言一律经 current_shots(run_id) 取图 —— 因此只会识别「当前（最新）生成的截图」，
上一世代的残留证据不会被误当成本次结果。

模式:
  f2       游戏内 F2 截图（经 mt_inject 注入），落 run/<ver>/screenshots/
  window   窗口级抓取（Pillow ImageGrab），用于窗口/桌面取证

用法:
  mt_capture.py capture --version 1.21.1 --tag tc2_enemy --mode f2
  mt_capture.py list    --version 1.21.1
  mt_capture.py prune   --version 1.21.1 --keep 3
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from mt_paths import Paths, active_run_id  # noqa: E402
from mt_ps import run_py  # noqa: E402

EXIT_ERROR = 2


def _newest(dir_: Path, since: float) -> Path | None:
    if not dir_.is_dir():
        return None
    cands = [p for p in dir_.glob("*.png") if p.stat().st_mtime >= since]
    return max(cands, key=lambda p: p.stat().st_mtime) if cands else None


def _raise_window(version: str) -> int:
    """把本版本 dev 客户端窗口提到最前（返回 hwnd，0 = 未找到）。

    只为「窗口抓取」这条兜底路径服务：ImageGrab 抓的是屏幕像素，窗口被别的
    窗口盖住就抓不到游戏画面。**不做最小化再还原**——那会触发 GLFW 的焦点
    事件与一次 pause/unpause 抖动，可能打乱 tick 驱动的探针窗口。

    注入走 PostMessage、不依赖前台焦点，因此提窗对 F2/命令注入无副作用；
    且 options.txt 已设 pauseOnLostFocus:false，失焦不会暂停世界。
    """
    if sys.platform != "win32":
        return 0
    try:
        sys.path.insert(0, str(Path(__file__).resolve().parent))
        import mt_inject  # type: ignore
        import ctypes
    except ImportError:
        return 0
    hwnd = mt_inject._find_window(version)
    if not hwnd:
        return 0
    user32 = ctypes.windll.user32
    kernel32 = ctypes.windll.kernel32
    HWND_TOPMOST, HWND_NOTOPMOST = -1, -2
    SWP_NOSIZE, SWP_NOMOVE = 0x0001, 0x0002
    user32.ShowWindow(hwnd, 9)          # SW_RESTORE（非 6/SW_MINIMIZE，避免 tick 抖动）
    # Windows 有「前台窗口抢占限制」:后台进程直接 SetForegroundWindow 常被静默忽略,
    # 表现为「按键注入了但游戏收不到」(F2 截图不落盘)。标准解法两连:
    #   ① SetWindowPos 到 TOPMOST 再回 NOTOPMOST,强制把窗口抬到最前;
    #   ② AttachThreadInput 把本线程输入队列挂到当前前台线程上,绕过前台限制。
    user32.SetWindowPos(hwnd, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOSIZE | SWP_NOMOVE)
    user32.SetWindowPos(hwnd, HWND_NOTOPMOST, 0, 0, 0, 0, SWP_NOSIZE | SWP_NOMOVE)
    user32.BringWindowToTop(hwnd)
    fg = user32.GetForegroundWindow()
    tid_fg = user32.GetWindowThreadProcessId(fg, None)
    tid_self = kernel32.GetCurrentThreadId()
    attached = False
    if tid_fg and tid_fg != tid_self:
        attached = bool(user32.AttachThreadInput(tid_self, tid_fg, True))
    try:
        user32.SetForegroundWindow(hwnd)
    finally:
        if attached:
            user32.AttachThreadInput(tid_self, tid_fg, False)
    time.sleep(0.6)
    return hwnd


def capture_f2(p: Paths, tag: str, run_id: str, timeout: float = 10.0,
               attempts: int = 3) -> int:
    """经注入通道按 F2，等游戏落盘后登记世代。

    F2 走的是游戏自身帧缓冲落盘，**不受窗口遮挡影响**，是首选取证方式
    （上一轮改用 F2 失败的真因是：注入 F2 前若打开了 GUI 界面（如物品栏），
    Minecraft 只在 screen == null 时处理 keybind，F2 会被静默吞掉——因此本
    流程的条目里不得再出现「先开界面再截图」的步骤）。

    **多次重试**:前台窗口抢占限制会让 SetForegroundWindow 偶发失效,此时
    PostMessage 注入的 F2 不会进入游戏键绑定;单次失败不能判定为「不可取证」,
    故每轮重试都重新提窗 + 重新注入。全部失败才退回窗口抓取。
    """
    inject = Path(__file__).resolve().parent / "mt_inject.py"
    last_err = ""
    for attempt in range(1, attempts + 1):
        _raise_window(p.version)
        before = time.time() - 1.0
        rc, _out, err = run_py([sys.executable, str(inject), "key", "--key", "f2",
                               "--version", p.version])
        if rc != 0:
            last_err = f"F2 注入失败({err.strip()})"
            continue
        deadline = time.time() + timeout
        while time.time() < deadline:
            shot = _newest(p.shot_dir, before)
            if shot:
                p.record_shot(shot, tag, run_id)
                print(f"MT_CAPTURE: OK — {shot.name} (tag={tag}, run={run_id}, "
                      f"mode=f2, attempt={attempt})")
                return 0
            time.sleep(0.5)
        last_err = f"{timeout:.0f}s 内无 F2 落盘"
    print(f"MT_CAPTURE: WARN — {last_err}（已重试 {attempts} 次，{p.shot_dir}），"
          "改用窗口抓取", file=sys.stderr)
    return capture_window(p, tag, run_id)


def capture_window(p: Paths, tag: str, run_id: str) -> int:
    try:
        from PIL import ImageGrab  # type: ignore
    except ImportError:
        print("MT_CAPTURE: ERROR — 缺少 Pillow，无法窗口抓取；改用 --mode f2", file=sys.stderr)
        return EXIT_ERROR

    hwnd = _raise_window(p.version)
    if not hwnd:
        print("MT_CAPTURE: WARN — 未定位到本版本游戏窗口，抓取的可能是桌面其它内容",
              file=sys.stderr)
    p.shot_dir.mkdir(parents=True, exist_ok=True)
    dst = p.shot_dir / f"{tag}_{run_id}.png"
    try:
        img = ImageGrab.grab()
        img.save(dst)
    except Exception as exc:  # noqa: BLE001 — 平台差异统一归为环境问题
        print(f"MT_CAPTURE: ERROR — 窗口抓取失败：{exc}", file=sys.stderr)
        return EXIT_ERROR
    p.record_shot(dst, tag, run_id)
    print(f"MT_CAPTURE: OK — {dst.name} (tag={tag}, mode=window)")
    return 0


def cmd_capture(args) -> int:
    p = Paths(args.version)
    run_id = active_run_id()
    if args.mode == "f2":
        return capture_f2(p, args.tag, run_id)
    return capture_window(p, args.tag, run_id)


def cmd_list(args) -> int:
    p = Paths(args.version)
    run_id = active_run_id()
    shots = p.current_shots(run_id)
    if args.list_latest:
        if not shots:
            print("MT_CAPTURE: ERROR — 当前世代无截图", file=sys.stderr)
            return EXIT_ERROR
        print(shots[-1])
        return 0
    if not shots:
        print(f"MT_CAPTURE: 当前世代（{run_id}）无截图")
        return 0
    data = p.read_shots()
    tags = {s["file"]: s.get("tag", "") for s in data.get("shots", [])}
    for s in shots:
        print(f"{s.name}\t{tags.get(s.name, '')}")
    return 0


def cmd_prune(args) -> int:
    """清理历史世代截图，只保留最近 keep 个运行（清单同步收敛）。"""
    p = Paths(args.version)
    run_id = active_run_id()
    keep = set(p.current_shots(run_id))
    if not p.shot_dir.is_dir():
        print("MT_CAPTURE: 无可清理内容")
        return 0
    removed = 0
    for f in list(p.shot_dir.glob("*.png")):
        if f in keep:
            continue
        try:
            f.unlink()
            removed += 1
        except OSError:
            pass
    # 清单只保留当前世代
    if keep:
        data = {"run_id": run_id,
                "shots": [s for s in p.read_shots().get("shots", [])
                          if s.get("file") in {k.name for k in keep}]}
        p.shots_manifest.write_text(json.dumps(data, ensure_ascii=False, indent=2),
                                    encoding="utf-8")
    print(f"MT_CAPTURE: 已清理 {removed} 张历史截图，保留当前世代 {len(keep)} 张")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="mt 截图采集")
    sub = ap.add_subparsers(dest="cmd", required=True)

    c = sub.add_parser("capture")
    c.add_argument("--version", required=True, choices=["1.21.1", "1.20.1"])
    c.add_argument("--tag", required=True)
    c.add_argument("--mode", choices=["f2", "window"], default="f2")

    for name in ("list", "prune"):
        s = sub.add_parser(name)
        s.add_argument("--version", required=True, choices=["1.21.1", "1.20.1"])
        if name == "list":
            s.add_argument("--list-latest", action="store_true")
        if name == "prune":
            s.add_argument("--keep", type=int, default=3)

    args = ap.parse_args()
    if args.cmd == "capture":
        return cmd_capture(args)
    if args.cmd == "list":
        return cmd_list(args)
    return cmd_prune(args)


if __name__ == "__main__":
    raise SystemExit(main())
