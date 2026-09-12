#!/usr/bin/env python3
"""mt_env — 测试环境装配（阶段 E）。

职责（只做环境的建立与拆除，不做任何功能断言）:
  mods    安装兼容模组（1.21.1 Sodium/Iris/ModernFix；1.20.1 校验生产环境渲染栈）
  world   重建 testworld（超平坦/创造/允许命令），支持种子快恢复
  kill    按版本精确停止游戏进程（不误杀无关 java 进程）

用法:
  mt_env.py mods  --version 1.21.1
  mt_env.py world --version 1.20.1 [--seed]
  mt_env.py kill  --version 1.21.1
"""
from __future__ import annotations

import argparse
import gzip
import shutil
import struct
import subprocess
import sys
import time
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from mt_paths import Paths, WORLD_NAME  # noqa: E402
from mt_ps import java_procs  # noqa: E402

EXIT_ERROR = 2
EXIT_BLOCKED = 11

# ── 兼容模组版本（与 AGENTS「测试环境」表一一对应）────────────────────────
NEOFORGE_MODS: tuple[tuple[str, str], ...] = (
    ("*sodium-neoforge*0.8.13*.jar", "sodium-neoforge-0.8.13+mc1.21.1.jar"),
    ("*iris-neoforge*1.8.14*.jar", "iris-neoforge-1.8.14-beta.1+mc1.21.1.jar"),
    ("*modernfix-neoforge*5.27.24*.jar", "modernfix-neoforge-5.27.24+mc1.21.1.jar"),
)


# ══ NBT 读写（替代原 prepare_world 的内联 C# 实现）══════════════════════════
TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG = 1, 2, 3, 4
TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING = 5, 6, 7, 8
TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 9, 10, 11, 12


def _read_tag(r, tid):
    if tid == TAG_BYTE:
        return struct.unpack(">b", r.read(1))[0]
    if tid == TAG_SHORT:
        return struct.unpack(">h", r.read(2))[0]
    if tid == TAG_INT:
        return struct.unpack(">i", r.read(4))[0]
    if tid == TAG_LONG:
        return struct.unpack(">q", r.read(8))[0]
    if tid == TAG_FLOAT:
        return struct.unpack(">f", r.read(4))[0]
    if tid == TAG_DOUBLE:
        return struct.unpack(">d", r.read(8))[0]
    if tid == TAG_BYTE_ARRAY:
        n = struct.unpack(">i", r.read(4))[0]
        return r.read(n)
    if tid == TAG_STRING:
        n = struct.unpack(">h", r.read(2))[0]
        return r.read(n).decode("utf-8")
    if tid == TAG_LIST:
        et = struct.unpack(">b", r.read(1))[0]
        n = struct.unpack(">i", r.read(4))[0]
        return (et, [_read_tag(r, et) for _ in range(n)])
    if tid == TAG_COMPOUND:
        out: dict[str, tuple[int, object]] = {}
        while True:
            t = struct.unpack(">b", r.read(1))[0]
            if t == 0:
                return out
            nl = struct.unpack(">h", r.read(2))[0]
            name = r.read(nl).decode("utf-8")
            out[name] = (t, _read_tag(r, t))
    if tid == TAG_INT_ARRAY:
        n = struct.unpack(">i", r.read(4))[0]
        return list(struct.unpack(f">{n}i", r.read(4 * n)))
    if tid == TAG_LONG_ARRAY:
        n = struct.unpack(">i", r.read(4))[0]
        return list(struct.unpack(f">{n}q", r.read(8 * n)))
    raise ValueError(f"未知 NBT 标签类型 {tid}")


def _write_tag(w, tid, val) -> None:
    if tid == TAG_BYTE:
        w.write(struct.pack(">b", val))
    elif tid == TAG_SHORT:
        w.write(struct.pack(">h", val))
    elif tid == TAG_INT:
        w.write(struct.pack(">i", val))
    elif tid == TAG_LONG:
        w.write(struct.pack(">q", val))
    elif tid == TAG_FLOAT:
        w.write(struct.pack(">f", val))
    elif tid == TAG_DOUBLE:
        w.write(struct.pack(">d", val))
    elif tid == TAG_BYTE_ARRAY:
        w.write(struct.pack(">i", len(val))); w.write(val)
    elif tid == TAG_STRING:
        raw = val.encode("utf-8"); w.write(struct.pack(">h", len(raw))); w.write(raw)
    elif tid == TAG_LIST:
        et, items = val
        w.write(struct.pack(">b", et)); w.write(struct.pack(">i", len(items)))
        for it in items:
            _write_tag(w, et, it)
    elif tid == TAG_COMPOUND:
        for name, (t, v) in val.items():
            w.write(struct.pack(">b", t))
            nb = name.encode("utf-8"); w.write(struct.pack(">h", len(nb))); w.write(nb)
            _write_tag(w, t, v)
        w.write(b"\x00")
    elif tid == TAG_INT_ARRAY:
        w.write(struct.pack(">i", len(val)))
        for i in val:
            w.write(struct.pack(">i", i))
    elif tid == TAG_LONG_ARRAY:
        w.write(struct.pack(">i", len(val)))
        for i in val:
            w.write(struct.pack(">q", i))
    else:
        raise ValueError(f"未知 NBT 标签类型 {tid}")


def nbt_read(path: Path):
    with gzip.open(path, "rb") as f:
        tid = struct.unpack(">b", f.read(1))[0]
        nl = struct.unpack(">h", f.read(2))[0]
        name = f.read(nl).decode("utf-8")
        return tid, name, _read_tag(f, tid)


def nbt_write(path: Path, tid: int, name: str, payload) -> None:
    import io
    bio = io.BytesIO()
    bio.write(struct.pack(">b", tid))
    nb = name.encode("utf-8")
    bio.write(struct.pack(">h", len(nb))); bio.write(nb)
    _write_tag(bio, tid, payload)
    with gzip.open(path, "wb") as f:
        f.write(bio.getvalue())


def set_allow_commands(level_dat: Path) -> bool:
    """单人存档的「允许命令」由 level.dat 的 Data.allowCommands 决定。

    runServer 生成的世界默认不写该字段，因此必须补上，否则 /publish、/give 等
    测试命令不可用。返回 True 表示字段已就位。
    """
    try:
        tid, name, root = nbt_read(level_dat)
    except (OSError, ValueError, struct.error) as exc:
        print(f"MT_ERROR: 解析 level.dat 失败：{exc}", file=sys.stderr)
        return False
    if tid != TAG_COMPOUND or "Data" not in root:
        print("MT_ERROR: level.dat 结构异常（缺少 Data）", file=sys.stderr)
        return False
    data = root["Data"][1]
    if isinstance(data, dict):
        data["allowCommands"] = (TAG_BYTE, 1)
    else:
        print("MT_ERROR: Data 不是复合标签", file=sys.stderr)
        return False
    try:
        nbt_write(level_dat, tid, name, root)
    except OSError as exc:
        print(f"MT_ERROR: 写回 level.dat 失败：{exc}", file=sys.stderr)
        return False
    return True


# ══ 进程管理 ══════════════════════════════════════════════════════════════
# 进程枚举统一走 lib/mt_ps.py：那里以 bytes 取回输出并容错解码，
# 避免中文路径（非 UTF-8 字节）导致读取线程崩溃、进而「静默地拿不到任何进程」。
_java_procs = java_procs


def kill_version(version: str, quiet: bool = False) -> int:
    """只停止属于本版本测试的 java 进程。

    匹配依据由 Paths.process_markers 统一给出（gradle 任务选择器 / run 目录），
    刻意不用裸子项目名——那同时是仓库内的目录名，会把只是引用了该目录的进程
    （IDE 语言服务器等）误杀。旧流程「按进程名 java 全杀」更是明确的破坏性行为。
    """
    p = Paths(version)
    markers = p.process_markers
    killed = 0
    for pid, cmd in _java_procs():
        if any(m and m in cmd for m in markers):
            if sys.platform == "win32":
                subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"],
                               capture_output=True)
            else:
                subprocess.run(["kill", "-9", str(pid)], capture_output=True)
            killed += 1
            if not quiet:
                print(f"  已停止 PID={pid}")
    if not quiet:
        print(f"MT_KILL: {version} 停止 {killed} 个进程")
    return killed


# ══ 子命令：mods ══════════════════════════════════════════════════════════
def cmd_mods(args) -> int:
    p = Paths(args.version)
    p.mods_dir.mkdir(parents=True, exist_ok=True)

    if args.version == "1.20.1":
        # dev run 不装渲染模组（Embeddium/Oculus 的 refmap 在 mojmap 下无法解析）；
        # 本步骤只确认用户生产环境渲染栈就绪，供兼容性人工验证。
        if not p.pack_mods_dir.is_dir():
            print(f"MT_MODS: BLOCKED — 生产环境目录不存在 {p.pack_mods_dir}")
            return EXIT_BLOCKED
        imblocker = next((f for f in p.pack_mods_dir.glob("*.jar")
                          if "imblocker" in f.name.lower()), None)
        if imblocker:
            print(f"MT_WARN: 生产环境仍含 IMBlocker（{imblocker.name}），需移入 __disabled__")
        print(f"MT_MODS: OK — 1.20.1 dev run 不使用渲染模组；生产环境已校验")
        return 0

    copied, missing = [], []
    for pattern, target in NEOFORGE_MODS:
        src = None
        for f in p.pack_mods_dir.glob(pattern):
            if "neoforge" in f.name.lower():
                src = f
                break
        if src is None:
            missing.append(pattern)
            continue
        dst = p.mods_dir / target
        if dst.is_file() and dst.stat().st_mtime >= src.stat().st_mtime:
            continue
        shutil.copy2(src, dst)
        copied.append(target)

    if missing:
        print(f"MT_MODS: BLOCKED — 整合包缺少 {', '.join(missing)}")
        return EXIT_BLOCKED
    detail = f"新装 {len(copied)} 个" if copied else "已是最新"
    print(f"MT_MODS: OK — Sodium/Iris/ModernFix {detail}")
    return 0


# ══ 子命令：world ═════════════════════════════════════════════════════════
SERVER_PROPS = """#Minecraft server properties
online-mode=false
level-name={world}
level-type=flat
generator-settings=
gamemode=creative
difficulty=easy
spawn-protection=0
enable-command-block=true
max-players=2
allow-cheats=true
"""


def _gradle_cmd(task: str) -> list[str]:
    root = Paths("1.21.1").root
    if sys.platform == "win32":
        bash = shutil.which("bash") or r"C:\Program Files\Git\bin\bash.exe"
        return [bash, str(root / "gradlew"), task, "--console=plain"]
    return [str(root / "gradlew"), task, "--console=plain"]


def _restore_seed(version: str) -> bool:
    seed = Paths(version).root / "scripts" / "test" / "resources" / f"testworld-seed-{version}.zip"
    if not seed.is_file():
        return False
    p = Paths(version)
    if p.client_world.exists():
        shutil.rmtree(p.client_world, ignore_errors=True)
    if p.server_world.exists():
        shutil.rmtree(p.server_world, ignore_errors=True)
    p.saves_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(seed) as z:
        z.extractall(p.saves_dir)
    _disable_pause_on_lost_focus(p)
    return True


def _disable_pause_on_lost_focus(p: Paths) -> None:
    """失焦暂停会让后台注入失效，必须关闭。"""
    opt = p.run_dir / "options.txt"
    lines = []
    if opt.is_file():
        lines = [ln for ln in opt.read_text(encoding="utf-8", errors="ignore").splitlines()
                 if not ln.startswith("pauseOnLostFocus:")]
    lines.append("pauseOnLostFocus:false")
    opt.write_text("\n".join(lines) + "\n", encoding="ascii")


def cmd_world(args) -> int:
    p = Paths(args.version)

    if args.seed:
        if not _restore_seed(args.version):
            print(f"MT_WORLD: BLOCKED — 未找到种子包 resources/testworld-seed-{args.version}.zip")
            return EXIT_BLOCKED
        if not set_allow_commands(p.client_world / "level.dat"):
            print("MT_WORLD: BLOCKED — level.dat 的 AllowCommands 未能设置")
            return EXIT_BLOCKED
        print(f"MT_WORLD: OK — 种子快恢复 {p.client_world}")
        return 0

    # 1. 清旧世界与旧日志
    for d in (p.client_world, p.server_world):
        if d.exists():
            shutil.rmtree(d, ignore_errors=True)
    p.saves_dir.mkdir(parents=True, exist_ok=True)
    if p.latest_log.is_file():
        p.latest_log.unlink()

    # 2. server.properties
    (p.run_dir / "server.properties").write_text(
        SERVER_PROPS.format(world=WORLD_NAME), encoding="ascii")
    _disable_pause_on_lost_focus(p)

    # 3. 生成世界期间临时移出纯客户端模组（服务端加载会崩溃或挂起）
    moved: list[tuple[Path, Path]] = []
    if p.mods_dir.is_dir():
        for f in p.mods_dir.glob("*.jar"):
            if any(k in f.name.lower() for k in ("imblocker", "sodium", "iris", "embeddium", "oculus")):
                bak = f.with_name("__clientonly_bak__" + f.name)
                shutil.move(str(f), str(bak))
                moved.append((bak, f))

    timeout = args.timeout
    done = False
    try:
        proc = subprocess.Popen(_gradle_cmd(p.task_server),
                                cwd=p.root, stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL)
        deadline = time.time() + timeout
        while time.time() < deadline:
            time.sleep(5)
            if p.latest_log.is_file():
                txt = p.latest_log.read_text(encoding="utf-8", errors="ignore")
                if "Done (" in txt:
                    done = True
                    break
                if "crash-reports" and (p.crash_dir.is_dir() and any(p.crash_dir.glob("*.txt"))):
                    print("MT_WORLD: FAIL — 生成世界期间产生崩溃报告")
                    break
            if proc.poll() is not None and not done:
                break
    finally:
        kill_version(args.version, quiet=True)
        for bak, orig in moved:
            if bak.exists():
                shutil.move(str(bak), str(orig))
        time.sleep(3)

    # 4. 世界从 run/<ver>/testworld 搬到 saves/testworld（客户端读取位置）
    if p.server_world.is_dir():
        if p.client_world.exists():
            shutil.rmtree(p.client_world, ignore_errors=True)
        shutil.move(str(p.server_world), str(p.client_world))

    level = p.client_world / "level.dat"
    if not (done and level.is_file()):
        print("MT_WORLD: BLOCKED — 世界未在时限内生成", file=sys.stderr)
        return EXIT_BLOCKED
    if not set_allow_commands(level):
        print("MT_WORLD: BLOCKED — level.dat 的 AllowCommands 未能设置", file=sys.stderr)
        return EXIT_BLOCKED
    print(f"MT_WORLD: OK — 世界重建并设 AllowCommands=1 {p.client_world}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="mt 环境装配")
    sub = ap.add_subparsers(dest="cmd", required=True)

    for name in ("mods", "world", "kill"):
        s = sub.add_parser(name)
        s.add_argument("--version", required=True, choices=["1.21.1", "1.20.1"])
        if name == "world":
            s.add_argument("--seed", action="store_true", help="从资源种子包快恢复")
            s.add_argument("--timeout", type=int, default=180)

    args = ap.parse_args()
    if args.cmd == "mods":
        return cmd_mods(args)
    if args.cmd == "world":
        return cmd_world(args)
    if args.cmd == "kill":
        kill_version(args.version)
        return 0
    return EXIT_ERROR


if __name__ == "__main__":
    raise SystemExit(main())
