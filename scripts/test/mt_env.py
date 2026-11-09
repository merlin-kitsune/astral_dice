#!/usr/bin/env python3
"""mt_env —— 测试环境准备（python 复原版）。

子命令面（与 ps1 的闭集一致）：mods | world | kill | kubejs | shaders | debug
选项：--version <v>（必填） --seed --state <on|off|status> --pause-lock <on|off|status>
      --keybinds <status|repair> --timeout <秒>

机器行（与 ps1 逐字同形）：
    MT_MODS: OK / MT_MODS: BLOCKED — <原因>            （BLOCKED 退 11）
    MT_WORLD: OK — 世界重建（allowCommands=1, keepInventory=true） <客户端存档>
    MT_WORLD: OK — 种子快恢复（allowCommands=1, keepInventory=true） <客户端存档>
    MT_WORLD: BLOCKED — 世界未在时限内生成 | level.dat 的 AllowCommands 未能设置 | <keepInventory 原因>
    MT_WORLD: FAIL — 生成世界期间产生崩溃报告（<文件名>）
    MT_WORLD: keepInventory 落地于 <game_rules.dat>（minecraft:keep_inventory=1b，读回复核通过）
    MT_KUBEJS: SKIP / OK — 已同步 N/M 个脚本… / 已清理 N 个…
    MT_SHADERS: <当前值> → <目标值> (enableShaders=<目标值>)  |  MT_SHADERS: <当前值> (enableShaders=…, <配置文件>)
    MT_DEBUG: <v> pause-lock=<…> / keybinds=<OK|DRIFT …> / shaders=<…>
    MT_PAUSE_LOCK / MT_WINDOWED / MT_KEYBINDS / MT_KILL / MT_WARN

退出码：0 = 成功；1 = 失败（world 生成期崩溃报告）；11 = BLOCKED；2 = 参数错误 / 能力缺失。

与 ps1 的差异（都在报告里逐条说明）：
  * mods 的 Install-* 安装器（探针运行时 / 渲染栈 / 光影包 / AutoJS / 优化模组 / Carpet /
    史莱姆压制）本轮**未复原**（ps1 那部分约 850 行，涉及 maven 拉取与 jar 内 toml 版本比对）。
    本文件改为**状态判据**：要求的 jar 集合已就位 → 打 OK 并在同一行注明安装器未执行；
    缺哪个就 BLOCKED(11)，绝不静默返回 0。
  * 26.1.2 的 keepInventory 写在 <世界>/data/minecraft/game_rules.dat（MC 26.1 起 gamerule 搬出
    level.dat 到 SavedData），写后**读回复核**；1.20.1 / 1.21.1 仍写 level.dat 的 GameRules.keepInventory。
  * 未复原 = 能力缺失时一律 BLOCKED/ERROR，不返回 0。
  * 增加 -h/--help。

本文件不调用任何外部脚本资产：起服务端走 cmd.exe /c gradlew.bat（Windows）或 sh gradlew。
"""

import hashlib
import gzip
import io
import os
import shutil
import struct
import subprocess
import sys
import time
import zipfile

TEST_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(TEST_DIR, "lib"))

import mt_compat as mc  # noqa: E402

EXIT_ERROR = mc.MT_EXIT_ERROR
EXIT_FAIL = mc.MT_EXIT_FAIL
EXIT_BLOCKED = mc.MT_EXIT_BLOCKED
EXIT_PASS = mc.MT_EXIT_PASS

WORLD_NAME = mc.WORLD_NAME
CLIENT_ONLY_MARKS = ("imblocker", "sodium", "iris", "embeddium", "oculus", "immediatelyfast")
CLIENT_ONLY_BAK = "__clientonly_bak__"
CLIENT_ONLY_SUFFIX = ".disabled"
GAMERULE_FILE_VERSION = "26.1.2"
KEEP_INVENTORY_RULE_KEY = "minecraft:keep_inventory"

NEOFORGE_MODS = (
    ("*sodium-neoforge*0.8.13*.jar", "sodium-neoforge-0.8.13+mc1.21.1.jar"),
    ("*iris-neoforge*1.8.14*.jar", "iris-neoforge-1.8.14-beta.1+mc1.21.1.jar"),
    ("*modernfix-neoforge*5.27.*.jar", "modernfix-neoforge-5.27.27+mc1.21.1.jar"),
)
MODS_REQUIRED_GLOBS = ("kubejs*", "rhino*", "AutoJS*")

KEYBIND_EXPECTED = (
    ("key_key.sneak", "key.keyboard.left.shift"),
    ("key_key.sprint", "key.keyboard.left.control"),
)

# ── NBT（大端；gzip 容器） ────────────────────────────────────────────────
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


class NbtError(Exception):
    pass


def _take(reader, count):
    data = reader.read(count)
    if len(data) != count:
        raise NbtError("数据截断（期望 " + str(count) + " 字节，实得 " + str(len(data)) + "）")
    return data


def _read_payload(reader, tag):
    if tag == TAG_BYTE:
        return struct.unpack(">b", _take(reader, 1))[0]
    if tag == TAG_SHORT:
        return struct.unpack(">h", _take(reader, 2))[0]
    if tag == TAG_INT:
        return struct.unpack(">i", _take(reader, 4))[0]
    if tag == TAG_LONG:
        return struct.unpack(">q", _take(reader, 8))[0]
    if tag == TAG_FLOAT:
        return struct.unpack(">f", _take(reader, 4))[0]
    if tag == TAG_DOUBLE:
        return struct.unpack(">d", _take(reader, 8))[0]
    if tag == TAG_BYTE_ARRAY:
        size = struct.unpack(">i", _take(reader, 4))[0]
        return _take(reader, size)
    if tag == TAG_STRING:
        size = struct.unpack(">H", _take(reader, 2))[0]
        return _take(reader, size).decode("utf-8", "surrogateescape")
    if tag == TAG_LIST:
        elem = struct.unpack(">b", _take(reader, 1))[0]
        size = struct.unpack(">i", _take(reader, 4))[0]
        return (elem, [_read_payload(reader, elem) for _i in range(max(0, size))])
    if tag == TAG_COMPOUND:
        return _read_compound(reader)
    if tag == TAG_INT_ARRAY:
        size = struct.unpack(">i", _take(reader, 4))[0]
        return struct.unpack(">" + str(size) + "i", _take(reader, 4 * size)) if size > 0 else ()
    if tag == TAG_LONG_ARRAY:
        size = struct.unpack(">i", _take(reader, 4))[0]
        return struct.unpack(">" + str(size) + "q", _take(reader, 8 * size)) if size > 0 else ()
    raise NbtError("未知标签类型 " + str(tag))


def _read_compound(reader):
    out = {}
    while True:
        tag = struct.unpack(">b", _take(reader, 1))[0]
        if tag == 0:
            return out
        size = struct.unpack(">H", _take(reader, 2))[0]
        name = _take(reader, size).decode("utf-8", "surrogateescape")
        out[name] = (tag, _read_payload(reader, tag))


def _write_payload(writer, tag, value):
    if tag == TAG_BYTE:
        writer.write(struct.pack(">b", int(value)))
    elif tag == TAG_SHORT:
        writer.write(struct.pack(">h", int(value)))
    elif tag == TAG_INT:
        writer.write(struct.pack(">i", int(value)))
    elif tag == TAG_LONG:
        writer.write(struct.pack(">q", int(value)))
    elif tag == TAG_FLOAT:
        writer.write(struct.pack(">f", float(value)))
    elif tag == TAG_DOUBLE:
        writer.write(struct.pack(">d", float(value)))
    elif tag == TAG_BYTE_ARRAY:
        writer.write(struct.pack(">i", len(value)))
        writer.write(bytes(value))
    elif tag == TAG_STRING:
        raw = str(value).encode("utf-8", "surrogateescape")
        writer.write(struct.pack(">H", len(raw)))
        writer.write(raw)
    elif tag == TAG_LIST:
        elem, items = value
        writer.write(struct.pack(">b", int(elem)))
        writer.write(struct.pack(">i", len(items)))
        for item in items:
            _write_payload(writer, elem, item)
    elif tag == TAG_COMPOUND:
        _write_compound(writer, value)
    elif tag == TAG_INT_ARRAY:
        writer.write(struct.pack(">i", len(value)))
        for item in value:
            writer.write(struct.pack(">i", int(item)))
    elif tag == TAG_LONG_ARRAY:
        writer.write(struct.pack(">i", len(value)))
        for item in value:
            writer.write(struct.pack(">q", int(item)))
    else:
        raise NbtError("未知标签类型 " + str(tag))


def _write_compound(writer, mapping):
    for name, (tag, value) in mapping.items():
        raw = str(name).encode("utf-8", "surrogateescape")
        writer.write(struct.pack(">b", int(tag)))
        writer.write(struct.pack(">H", len(raw)))
        writer.write(raw)
        _write_payload(writer, tag, value)
    writer.write(b"\x00")


def nbt_read(path):
    with gzip.open(path, "rb") as fh:
        tag = struct.unpack(">b", _take(fh, 1))[0]
        size = struct.unpack(">H", _take(fh, 2))[0]
        name = _take(fh, size).decode("utf-8", "surrogateescape")
        return tag, name, _read_payload(fh, tag)


def nbt_write(path, tag, name, payload):
    buf = io.BytesIO()
    raw = name.encode("utf-8", "surrogateescape")
    buf.write(struct.pack(">b", int(tag)))
    buf.write(struct.pack(">H", len(raw)))
    buf.write(raw)
    _write_payload(buf, tag, payload)
    with gzip.open(path, "wb") as fh:
        fh.write(buf.getvalue())


def set_allow_commands(level_dat):
    try:
        tag, name, payload = nbt_read(level_dat)
    except (OSError, EOFError, NbtError, struct.error) as exc:
        mc.write_mt_error_line("解析 level.dat 失败：" + str(exc))
        return False
    if tag != TAG_COMPOUND or "Data" not in payload:
        mc.write_mt_error_line("level.dat 结构异常（缺少 Data）")
        return False
    data = payload["Data"][1]
    if not isinstance(data, dict):
        mc.write_mt_error_line("Data 不是复合标签")
        return False
    data["allowCommands"] = (TAG_BYTE, 1)
    try:
        nbt_write(level_dat, tag, name, payload)
    except OSError as exc:
        mc.write_mt_error_line("写回 level.dat 失败：" + str(exc))
        return False
    return True


def set_keep_inventory(level_dat):
    """level.dat 的 Data.GameRules.keepInventory = 'true'（TAG_String）。"""
    try:
        tag, name, payload = nbt_read(level_dat)
    except (OSError, EOFError, NbtError, struct.error) as exc:
        mc.write_mt_error_line("解析 level.dat 失败：" + str(exc))
        return False
    if tag != TAG_COMPOUND or "Data" not in payload:
        mc.write_mt_error_line("level.dat 结构异常（缺少 Data）")
        return False
    data = payload["Data"][1]
    if not isinstance(data, dict):
        mc.write_mt_error_line("Data 不是复合标签")
        return False
    rules = data.get("GameRules")
    if not isinstance(rules, (tuple, list)) or len(rules) != 2 or not isinstance(rules[1], dict):
        rules = (TAG_COMPOUND, {})
        data["GameRules"] = rules
    rules[1]["keepInventory"] = (TAG_STRING, "true")
    try:
        nbt_write(level_dat, tag, name, payload)
    except OSError as exc:
        mc.write_mt_error_line("写回 level.dat 失败：" + str(exc))
        return False
    return True


# ── 26.1.2：gamerule 搬到 SavedData ────────────────────────────────────────
def game_rules_file(world_dir):
    return os.path.join(world_dir, "data", "minecraft", "game_rules.dat")


def read_rule_keep_inventory(rules_path):
    """返回 0/1；文件缺失 / 解析失败 / 无该键 / 类型不符 返回 None。"""
    if not os.path.isfile(rules_path):
        return None
    try:
        tag, _name, payload = nbt_read(rules_path)
    except (OSError, EOFError, NbtError, struct.error):
        return None
    if tag != TAG_COMPOUND or not isinstance(payload, dict):
        return None
    data = payload.get("data")
    if not isinstance(data, (tuple, list)) or len(data) != 2 or not isinstance(data[1], dict):
        return None
    entry = data[1].get(KEEP_INVENTORY_RULE_KEY)
    if not isinstance(entry, (tuple, list)) or len(entry) != 2 or entry[0] != TAG_BYTE:
        return None
    return int(entry[1])


def level_data_version(level_dat):
    try:
        tag, _name, payload = nbt_read(level_dat)
    except (OSError, EOFError, NbtError, struct.error):
        return 0
    if tag != TAG_COMPOUND or "Data" not in payload:
        return 0
    data = payload["Data"][1]
    if not isinstance(data, dict):
        return 0
    entry = data.get("DataVersion")
    if not isinstance(entry, (tuple, list)) or len(entry) != 2 or entry[0] != TAG_INT:
        return 0
    return int(entry[1])


GEN_FLAT = "minecraft:flat"


def world_generator(level_dat):
    """返回主世界 generator type（取不到返回 ""）。

    规则来源：2026-11-02 用户裁决「建立存档一律使用超平坦」。
    路径：level.dat -> Data.WorldGenSettings.dimensions."minecraft:overworld".generator.type
    下界/末地仍是 minecraft:noise，属正常，只看主世界。
    """
    try:
        tag, _name, payload = nbt_read(level_dat)
    except (OSError, EOFError, NbtError, struct.error):
        return ""
    if tag != TAG_COMPOUND or not isinstance(payload, dict):
        return ""
    data = payload.get("Data")
    if not isinstance(data, (tuple, list)) or len(data) != 2 or not isinstance(data[1], dict):
        return ""
    settings = data[1].get("WorldGenSettings")
    if not isinstance(settings, (tuple, list)) or len(settings) != 2 or not isinstance(settings[1], dict):
        return ""
    dims = settings[1].get("dimensions")
    if not isinstance(dims, (tuple, list)) or len(dims) != 2 or not isinstance(dims[1], dict):
        return ""
    overworld = dims[1].get("minecraft:overworld")
    if not isinstance(overworld, (tuple, list)) or len(overworld) != 2 or not isinstance(overworld[1], dict):
        return ""
    gen = overworld[1].get("generator")
    if not isinstance(gen, (tuple, list)) or len(gen) != 2:
        return ""
    if isinstance(gen[1], str):
        return gen[1]
    if not isinstance(gen[1], dict):
        return ""
    entry = gen[1].get("type")
    if not isinstance(entry, (tuple, list)) or len(entry) != 2 or not isinstance(entry[1], str):
        return ""
    return entry[1]


def server_properties_level_type(world_dir):
    """26.x 回退判据：从 <run>/server.properties 读 level-type（取不到返回 ""）。

    背景（2026-11-02 实测）：26.1.2 的 level.dat **不再存 WorldGenSettings**
    （19 个键里没有任何 world/gen/dim 相关键，主世界数据整体搬到
    dimensions/<dim>/ 下），因此老路径必然「无法判定」。而 mt_env 自己生成世界时
    会写 server.properties: level-type=flat ⇒ 用该值作为 26.x 的判据。
    """
    run_dir = os.path.dirname(os.path.dirname(os.path.abspath(world_dir)))
    prop = os.path.join(run_dir, "server.properties")
    try:
        with open(prop, "r", encoding="utf-8", errors="replace") as fh:
            for line in fh:
                line = line.strip()
                if line.startswith("level-type="):
                    return line.split("=", 1)[1].strip()
    except OSError:
        return ""
    return ""


def require_flat_world(level_dat, source):
    """超平坦规范闸门：返回 "" 表示合规，否则返回要打的 BLOCKED 行。"""
    gen = world_generator(level_dat)
    if gen == GEN_FLAT:
        return ""
    if not gen:
        # 26.x：level.dat 无 WorldGenSettings ⇒ 回退到 server.properties 的 level-type
        lt = server_properties_level_type(os.path.dirname(os.path.abspath(level_dat)))
        if lt in ("flat", GEN_FLAT):
            _wd = os.path.dirname(os.path.abspath(level_dat))
            _run = os.path.dirname(os.path.dirname(_wd))
            print("MT_WORLD: 说明 — 26.x 的 level.dat 不含 WorldGenSettings，"
                  "按 " + os.path.join(os.path.basename(_run), "server.properties")
                  + " 的 level-type=" + lt + " 判定为超平坦")
            return ""
    shown = gen if gen else "无法判定（level.dat 里没有 generator.type）"
    return ("MT_WORLD: BLOCKED — " + source + " 的主世界 generator=" + shown
            + "，不符合「建立存档一律使用超平坦」规范（2026-11-02 用户裁决）；"
            + "请用 python scripts/test/mt_env.py world --version <v> 由专用服务端重建")


def write_rule_keep_inventory(world_dir, level_dat):
    """返回 (ok, 原因)。幂等：已经是 1b 就不重写（避免 mtime 抖动与半写文件）。"""
    if not os.path.isdir(world_dir):
        return False, "世界目录不存在，拒绝创建 game_rules.dat：" + world_dir
    rules_path = game_rules_file(world_dir)
    if read_rule_keep_inventory(rules_path) == 1:
        return True, ""
    payload = None
    root_tag = TAG_COMPOUND
    name = ""
    if os.path.isfile(rules_path):
        try:
            root_tag, name, payload = nbt_read(rules_path)
        except (OSError, EOFError, NbtError, struct.error) as exc:
            return False, "game_rules.dat 读取失败：" + str(exc)
        if root_tag != TAG_COMPOUND or not isinstance(payload, dict):
            return False, "game_rules.dat 结构异常（根不是复合标签）"
    if payload is None:
        payload = {"data": (TAG_COMPOUND, {
            "minecraft:natural_health_regeneration": (TAG_BYTE, 0),
            "minecraft:mob_griefing": (TAG_BYTE, 1),
            "minecraft:spawn_mobs": (TAG_BYTE, 1),
        })}
    data = payload.get("data")
    if not isinstance(data, (tuple, list)) or len(data) != 2 or not isinstance(data[1], dict):
        data = (TAG_COMPOUND, {})
        payload["data"] = data
    data[1][KEEP_INVENTORY_RULE_KEY] = (TAG_BYTE, 1)
    if "DataVersion" not in payload:
        version = level_data_version(level_dat)
        if version > 0:
            payload["DataVersion"] = (TAG_INT, version)
        else:
            mc.write_mt_warn("警告：无法确定 DataVersion（level.dat 里读不到），game_rules.dat 将不带该键")
    try:
        os.makedirs(os.path.dirname(rules_path), exist_ok=True)
        nbt_write(rules_path, root_tag, name, payload)
    except OSError as exc:
        return False, "game_rules.dat 写入失败：" + str(exc)
    actual = read_rule_keep_inventory(rules_path)
    if actual != 1:
        shown = "读不到（文件缺失 / 无该键 / 类型不符）" if actual is None else str(actual)
        return False, ("game_rules.dat 的 minecraft:keep_inventory 读回复核不为 1b："
                       + shown + "（" + rules_path + "）")
    return True, ""


def set_world_keep_inventory(version, paths):
    """返回 None 表示成功，否则返回原因文本（拼在 MT_WORLD: BLOCKED — 之后）。"""
    level_dat = os.path.join(paths.client_world, "level.dat")
    if not set_keep_inventory(level_dat):
        return "level.dat 的 GameRules.keepInventory 未能设置"
    if version != GAMERULE_FILE_VERSION:
        return None
    ok, err = write_rule_keep_inventory(paths.client_world, level_dat)
    if not ok:
        return err
    mc.write_mt_line("MT_WORLD: keepInventory 落地于 " + game_rules_file(paths.client_world)
                     + "（minecraft:keep_inventory=1b，读回复核通过）")
    return None


# ── options.txt ──────────────────────────────────────────────────────────
def _options_path(paths):
    return os.path.join(paths.run_dir, "options.txt")


def _read_lines(path):
    try:
        with open(path, "rb") as fh:
            text = fh.read().decode("utf-8", "replace")
    except OSError:
        return None
    return mc.split_crlf(text)


def _write_ascii(path, lines):
    try:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="ascii", errors="replace", newline="\n") as fh:
            fh.write("\n".join(lines) + "\n")
        return True
    except OSError as exc:
        mc.write_mt_warn("MT_WARN: 写 " + path + " 失败：" + str(exc))
        return False


def get_pause_on_lost_focus(paths):
    lines = _read_lines(_options_path(paths))
    if lines is None:
        return None
    for line in lines:
        low = line.strip()
        if not low.lower().startswith("pauseonlostfocus"):
            continue
        parts = low.split(":", 1)
        if len(parts) != 2:
            continue
        return parts[1].strip().lower() == "true"
    return None


def set_pause_on_lost_focus(paths, enabled):
    want = "true" if enabled else "false"
    path = _options_path(paths)
    lines = _read_lines(path) or []
    kept = [ln for ln in lines if not ln.strip().lower().startswith("pauseonlostfocus:")]
    kept.append("pauseOnLostFocus:" + want)
    _write_ascii(path, kept)
    return enabled


def set_fullscreen_disabled(paths):
    path = _options_path(paths)
    lines = _read_lines(path) or []
    kept = [ln for ln in lines if not ln.strip().lower().startswith("fullscreen:")]
    kept.append("fullscreen:false")
    # 2026-11-05：手工新建的 options.txt 若缺 onboardAccessibility，1.21.1 客户端会停在
    # "辅助功能引导"界面（AccessibilityOnboardingScreen）而永不加载世界，比全屏更致命。
    # 这里与全屏一起补写，避免"空 options → 引导页 → quickplay 静默失效"。
    if not any(ln.strip().lower().startswith("onboardaccessibility:") for ln in kept):
        kept.append("onboardAccessibility:false")
    # 2026-11-05：NeoForge 线的 options.txt 会把带修饰键的绑定写成 "key.keyboard.o:CONTROL"，
    # 原版/fabric 的 InputConstants.getKey 会 NumberFormatException 并整份 options 放弃加载。
    # 复制跨线 options 或人工改写后统一在这里清掉这类行（游戏内会以默认绑定重建）。
    kept = [ln for ln in kept
            if not (ln.strip().lower().startswith("key_") and ln.strip().count(":") > 1)]
    _write_ascii(path, kept)
    return False


def get_keybinding(paths, key):
    lines = _read_lines(_options_path(paths))
    if lines is None:
        return None
    prefix = key.lower() + ":"
    for line in lines:
        low = line.strip().lower()
        if low.startswith(prefix):
            return line.strip().split(":", 1)[1].strip()
    return None


def repair_keybindings(paths):
    path = _options_path(paths)
    lines = _read_lines(path)
    if lines is None:
        return []
    changes = []
    kept = list(lines)
    for key, want in KEYBIND_EXPECTED:
        prefix = key.lower() + ":"
        current = None
        rest = []
        for line in kept:
            if line.strip().lower().startswith(prefix):
                if current is None:
                    current = line.strip().split(":", 1)[1].strip()
                continue
            rest.append(line)
        kept = rest
        if current != want:
            kept.append(key + ":" + want)
            changes.append((key, current if current is not None else "（未设置）", want))
    if changes:
        _write_ascii(path, kept)
    return changes


def pause_lock_enforce(paths):
    set_pause_on_lost_focus(paths, False)
    mc.write_mt_line("MT_PAUSE_LOCK: on — 禁止失焦打开 ESC 菜单（pauseOnLostFocus=false，"
                     + _options_path(paths) + "）")


def windowed_enforce(paths):
    set_fullscreen_disabled(paths)
    mc.write_mt_line("MT_WINDOWED: on — runClient 强制窗口模式（fullscreen=false，"
                     + _options_path(paths) + "）")


def keybind_enforce(paths):
    changes = repair_keybindings(paths)
    if not changes:
        mc.write_mt_line("MT_KEYBINDS: OK — 潜行/冲刺绑定未被改动（" + _options_path(paths) + "）")
        return
    detail = "; ".join(key + ":" + old + "→" + new for key, old, new in changes)
    mc.write_mt_warn("MT_KEYBINDS: REPAIRED — 测试环境按键绑定被改动过，已改回期望值："
                     + detail + "（" + _options_path(paths) + "）")


# ── kubejs 模板同步 ──────────────────────────────────────────────────────
def _sha256_file(path):
    digest = hashlib.sha256()
    try:
        with open(path, "rb") as fh:
            for block in iter(lambda: fh.read(1 << 16), b""):
                digest.update(block)
    except OSError:
        return ""
    return digest.hexdigest()


def sync_kubejs(version):
    src_root = os.path.join(TEST_DIR, "resources", "kubejs", version)
    if not os.path.isdir(src_root):
        mc.write_mt_line("MT_KUBEJS: SKIP — 模板目录不存在 " + src_root)
        return EXIT_PASS
    paths = mc.get_paths(version)
    dst_root = os.path.join(paths.run_dir, "kubejs")
    keep = set()
    for base, _dirs, files in os.walk(src_root):
        for name in files:
            full = os.path.join(base, name)
            keep.add(os.path.relpath(full, src_root).replace("\\", "/"))
    removed = 0
    for sub in ("server_scripts", "client_scripts", "startup_scripts"):
        sub_dir = os.path.join(dst_root, sub)
        if not os.path.isdir(sub_dir):
            continue
        for base, _dirs, files in os.walk(sub_dir):
            for name in files:
                if not name.lower().endswith(".js"):
                    continue
                full = os.path.join(base, name)
                rel = os.path.relpath(full, dst_root).replace("\\", "/")
                if rel in keep:
                    continue
                try:
                    os.remove(full)
                    removed += 1
                except OSError:
                    continue
    if removed:
        mc.write_mt_line("MT_KUBEJS: 已清理 " + str(removed)
                         + " 个模板中已不存在的旧脚本（避免重复注册 /astralprobe）")
    total = 0
    copied = 0
    for rel in sorted(keep):
        src = os.path.join(src_root, rel.replace("/", os.sep))
        dst = os.path.join(dst_root, rel.replace("/", os.sep))
        total += 1
        if os.path.isfile(dst) and _sha256_file(src) == _sha256_file(dst):
            continue
        try:
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
            copied += 1
        except OSError as exc:
            mc.write_mt_warn("MT_WARN: 复制脚本失败 " + src + " — " + str(exc))
    if copied:
        mc.write_mt_line("MT_KUBEJS: OK — 已同步 " + str(copied) + "/" + str(total)
                         + " 个脚本到 run/" + version
                         + "/kubejs（模板有更新；探针命令的改动需**冷启动**才生效）")
    else:
        mc.write_mt_line("MT_KUBEJS: OK — " + str(total) + " 个脚本均与模板一致（无需同步）")
    return EXIT_PASS


# ── mods ─────────────────────────────────────────────────────────────────
def _require_jars(paths):
    """状态判据：ps1 的安装器未复原，改为核对它们要落的 jar 是否已就位。"""
    missing = []
    present = []
    for pattern in MODS_REQUIRED_GLOBS:
        hits = []
        try:
            names = os.listdir(paths.mods_dir)
        except OSError:
            names = []
        low_pattern = pattern.lower()
        for name in names:
            if not name.lower().endswith(".jar"):
                continue
            if low_pattern.endswith("*") and name.lower().startswith(low_pattern[:-1]):
                hits.append(name)
        if hits:
            present.extend(hits)
        else:
            missing.append(pattern)
    return present, missing


def sync_neoforge_mods(paths):
    copied = []
    missing = []
    for pattern, target in NEOFORGE_MODS:
        import fnmatch
        hits = []
        try:
            names = os.listdir(paths.pack_mods_dir)
        except OSError:
            names = []
        for name in sorted(names):
            if not fnmatch.fnmatch(name.lower(), pattern.lower()):
                continue
            if "neoforge" not in name.lower():
                continue
            hits.append(name)
        if not hits:
            missing.append(pattern)
            continue
        src = os.path.join(paths.pack_mods_dir, hits[0])
        dst = os.path.join(paths.mods_dir, target)
        if os.path.isfile(dst):
            try:
                if os.path.getmtime(dst) >= os.path.getmtime(src):
                    continue
            except OSError:
                pass
        try:
            os.makedirs(paths.mods_dir, exist_ok=True)
            shutil.copy2(src, dst)
            copied.append(target)
        except OSError as exc:
            mc.write_mt_warn("MT_WARN: 复制 " + src + " 失败 — " + str(exc))
    return copied, missing


def cmd_mods(version):
    sync_kubejs(version)
    paths = mc.get_paths(version)
    try:
        os.makedirs(paths.mods_dir, exist_ok=True)
    except OSError as exc:
        mc.write_mt_error_line("创建 mods 目录失败：" + str(exc))
        return EXIT_ERROR

    if mc.is_fabric(version):
        # fabric 车道（1.20.1 / 1.21.1）：渲染栈与测试台 mod 一律由该线的 build.gradle 提供，
        # 不走整合包同步；AutoJS 走 mavenLocal 的运行期 classpath（-PautojsTestMod=1），
        # 因此 mods/ 里**不得**再出现 AutoJS-*.jar（重复注册会崩，2026-11-03 实测）。
        names = os.listdir(paths.mods_dir) if os.path.isdir(paths.mods_dir) else []
        dup = [n for n in names if n.lower().startswith("autojs-") and n.lower().endswith(".jar")]
        if dup:
            mc.write_mt_line("MT_MODS: BLOCKED — fabric 的 AutoJS 由 mavenLocal classpath 注入，"
                             "mods/ 内不得再放同名 jar（重复注册会崩）：" + ", ".join(dup))
            return EXIT_BLOCKED
        mc.write_mt_line("MT_MODS: OK — " + version + " 渲染栈与测试台 mod 由 build.gradle 提供；"
                         "AutoJS 走 mavenLocal classpath（mods/ 无重复件）；mods/ 现有 "
                         + str(len(names)) + " 个 jar")
    elif version == "1.20.1":
        if not os.path.isdir(paths.pack_mods_dir):
            mc.write_mt_line("MT_MODS: BLOCKED — 生产环境目录不存在 " + paths.pack_mods_dir)
            return EXIT_BLOCKED
        try:
            names = os.listdir(paths.pack_mods_dir)
        except OSError:
            names = []
        for name in names:
            if "imblocker" in name.lower():
                mc.write_mt_line("MT_WARN: 生产环境仍含 IMBlocker（" + name + "），需移入 __disabled__")
                break
        present, missing = _require_jars(paths)
        if missing:
            mc.write_mt_line("MT_MODS: BLOCKED — 1.20.1 dev run 缺少 " + ", ".join(missing)
                             + "（ps1 的 Install-MtRenderStack/Install-MtAutoJs 未在本轮复原，"
                             + "需先把对应 jar 放进 " + paths.mods_dir + "）")
            return EXIT_BLOCKED
        mc.write_mt_line("MT_MODS: OK — 1.20.1 渲染栈由 build.gradle 提供；生产环境已校验；"
                         "kubejs/rhino/AutoJS 已就位（" + ", ".join(present[:4]) + "）")
    elif version == GAMERULE_FILE_VERSION:
        present, missing = _require_jars(paths)
        if missing:
            mc.write_mt_line("MT_MODS: BLOCKED — 26.1.2 dev run 缺少 " + ", ".join(missing)
                             + "（ps1 的 Install-MtProbeRuntime/Install-MtAutoJs 未在本轮复原）")
            return EXIT_BLOCKED
        mc.write_mt_line("MT_MODS: OK — 26.1.2 dev run：探针运行时 + Sodium/Iris + 超平坦史莱姆压制"
                         " + 优化模组（jar 已就位：kubejs/rhino/AutoJS；"
                         "ps1 的 Install-* 安装器本轮未复原，未做版本对齐）")
    else:
        copied, missing = sync_neoforge_mods(paths)
        if missing:
            mc.write_mt_line("MT_MODS: BLOCKED — 整合包缺少 " + ", ".join(missing))
            return EXIT_BLOCKED
        present, lacking = _require_jars(paths)
        if lacking:
            mc.write_mt_line("MT_MODS: BLOCKED — " + version + " mods 缺少 " + ", ".join(lacking)
                             + "（ps1 的 Install-MtProbeHost/Install-MtAutoJs 未在本轮复原）")
            return EXIT_BLOCKED
        detail = ("新装 " + str(len(copied)) + " 个") if copied else "已是最新"
        mc.write_mt_line("MT_MODS: OK — Sodium/Iris/ModernFix " + detail)
        mc.write_mt_line("MT_MODS: 提示 — 探针宿主 / AutoJS / 优化模组安装器本轮未复原"
                         "（对应 jar 已就位，视为已满足）")

    pause_lock_enforce(paths)
    windowed_enforce(paths)
    keybind_enforce(paths)
    return EXIT_PASS


# ── world ────────────────────────────────────────────────────────────────
SERVER_PROPS = (
    "#Minecraft server properties",
    "online-mode=false",
    "level-name=" + WORLD_NAME,
    "level-type=flat",
    "generator-settings=",
    "gamemode=creative",
    "difficulty=easy",
    "spawn-protection=0",
    "enable-command-block=true",
    "max-players=2",
    "allow-cheats=true",
)


def _server_cmd(paths, task):
    bat = os.path.join(paths.root, "gradlew.bat")
    sh = os.path.join(paths.root, "gradlew")
    if mc.IS_WINDOWS and os.path.isfile(bat):
        return ["cmd.exe", "/c", bat, task, "--console=plain"]
    if os.path.isfile(sh):
        return ["sh", sh, task, "--console=plain"]
    return []


def _restore_seed(version, paths):
    seed = os.path.join(TEST_DIR, "resources", "testworld-seed-" + version + ".zip")
    if not os.path.isfile(seed):
        return False
    shutil.rmtree(paths.client_world, ignore_errors=True)
    shutil.rmtree(paths.server_world, ignore_errors=True)
    try:
        os.makedirs(paths.saves_dir, exist_ok=True)
        with zipfile.ZipFile(seed) as zf:
            zf.extractall(paths.saves_dir)
    except (OSError, zipfile.BadZipFile) as exc:
        mc.write_mt_warn("MT_WARN: 解压种子包失败 " + seed + " — " + str(exc))
        return False
    set_pause_on_lost_focus(paths, False)
    return True


def _move_aside_client_mods(paths):
    moved = []
    try:
        names = os.listdir(paths.mods_dir)
    except OSError:
        return moved
    for name in names:
        low = name.lower()
        if not low.endswith(".jar"):
            continue
        if not any(mark in low for mark in CLIENT_ONLY_MARKS):
            continue
        src = os.path.join(paths.mods_dir, name)
        dst = os.path.join(paths.mods_dir, CLIENT_ONLY_BAK + name + CLIENT_ONLY_SUFFIX)
        try:
            os.rename(src, dst)
            moved.append((dst, src))
        except OSError as exc:
            mc.write_mt_warn("MT_WARN: 移出客户端专属模组失败 " + name + " — " + str(exc))
    return moved


def _restore_moved(moved):
    for dst, src in moved:
        try:
            os.rename(dst, src)
        except OSError:
            continue


def _crash_reports(paths):
    try:
        names = os.listdir(paths.crash_dir)
    except OSError:
        return set()
    return set(n for n in names if n.lower().endswith(".txt"))


def _read_text(path):
    try:
        with open(path, "rb") as fh:
            return fh.read().decode("utf-8", "replace")
    except OSError:
        return ""


def cmd_world(version, seed, timeout):
    paths = mc.get_paths(version)
    # fabric 的 run 目录按 client / server 分侧（mt_compat.run_root），而 get_paths 默认 side=client：
    # 服务端生成世界必须用 side=server 的目录写 server.properties、盯日志、搬存档。
    srv = mc.get_paths(version, "server") if mc.is_fabric(version) else paths
    if seed:
        if not _restore_seed(version, paths):
            mc.write_mt_line("MT_WORLD: BLOCKED — 未找到种子包 resources/testworld-seed-"
                             + version + ".zip")
            return EXIT_BLOCKED
        level_dat = os.path.join(paths.client_world, "level.dat")
        if not os.path.isfile(level_dat):
            mc.write_mt_line("MT_WORLD: BLOCKED — 种子包里没有 level.dat（" + level_dat + "）")
            return EXIT_BLOCKED
        if not set_allow_commands(level_dat):
            mc.write_mt_line("MT_WORLD: BLOCKED — level.dat 的 AllowCommands 未能设置")
            return EXIT_BLOCKED
        err = set_world_keep_inventory(version, paths)
        if err:
            mc.write_mt_line("MT_WORLD: BLOCKED — " + err)
            return EXIT_BLOCKED
        bad = require_flat_world(level_dat, "种子包 testworld-seed-" + version + ".zip 恢复出的世界")
        if bad:
            mc.write_mt_line(bad)
            return EXIT_BLOCKED
        mc.write_mt_line("MT_WORLD: OK — 种子快恢复（allowCommands=1, keepInventory=true, "
                         "generator=" + GEN_FLAT + "） " + paths.client_world)
        return EXIT_PASS

    shutil.rmtree(paths.client_world, ignore_errors=True)
    shutil.rmtree(paths.server_world, ignore_errors=True)
    try:
        os.makedirs(paths.saves_dir, exist_ok=True)
    except OSError as exc:
        mc.write_mt_error_line("创建 saves 目录失败：" + str(exc))
        return EXIT_ERROR
    if os.path.isfile(srv.latest_log):
        try:
            os.remove(srv.latest_log)
        except OSError:
            pass
    try:
        os.makedirs(srv.run_dir, exist_ok=True)
        with open(os.path.join(srv.run_dir, "server.properties"), "w",
                  encoding="ascii", errors="replace", newline="\n") as fh:
            fh.write("\n".join(SERVER_PROPS) + "\n")
    except OSError as exc:
        mc.write_mt_error_line("写 server.properties 失败：" + str(exc))
        return EXIT_ERROR
    # 专用服务端首启必须自带 eula.txt（2026-11-05 补：fabric-1.21.1 是全新 run/server，
    # 之前没人在这个目录点过 EULA => "You need to agree to the EULA" 直接退出，
    # 表现为 MT_WORLD: BLOCKED — 世界未在时限内生成）。
    try:
        with open(os.path.join(srv.run_dir, "eula.txt"), "w",
                  encoding="ascii", errors="replace", newline="\n") as fh:
            fh.write("#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).\neula=true\n")
    except OSError as exc:
        mc.write_mt_error_line("写 eula.txt 失败：" + str(exc))
        return EXIT_ERROR
    set_pause_on_lost_focus(paths, False)

    moved = _move_aside_client_mods(srv)
    baseline = _crash_reports(srv)
    cmd = _server_cmd(paths, paths.task_server)
    if not cmd:
        _restore_moved(moved)
        mc.write_mt_line("MT_WORLD: BLOCKED — 找不到 gradlew / gradlew.bat（" + paths.root + "）")
        return EXIT_BLOCKED
    done = False
    crashed = ""
    log_path = os.path.join(srv.run_dir, "mt_server_gen.log")
    proc = None
    try:
        log_handle = open(log_path, "wb")
    except OSError as exc:
        _restore_moved(moved)
        mc.write_mt_error_line("写 mt_server_gen.log 失败：" + str(exc))
        return EXIT_ERROR
    try:
        try:
            proc = subprocess.Popen(cmd, cwd=paths.root, stdout=log_handle,
                                    stderr=subprocess.STDOUT)
        except OSError as exc:
            mc.write_mt_error_line("启动服务端失败：" + str(exc))
            return EXIT_ERROR
        deadline = time.time() + max(1, timeout)
        while time.time() < deadline:
            time.sleep(5)
            if os.path.isfile(srv.latest_log):
                text = _read_text(srv.latest_log)
                if "Done (" in text:
                    done = True
                    break
            fresh = _crash_reports(srv) - baseline
            if fresh:
                crashed = sorted(fresh)[0]
                mc.write_mt_line("MT_WORLD: FAIL — 生成世界期间产生崩溃报告（" + crashed + "）")
                break
            if proc.poll() is not None:
                break
    finally:
        log_handle.close()
        # 服务端退出要等一下再搬档：runServer 的 java 进程会持有 testworld/session.lock，
        # 未退净就 move 会撞 WinError 33（2026-11-03 实测），且异步退出时 stop 可能一次命不中。
        for _ in range(24):
            if not mc.stop_version_processes(srv, quiet=True):
                break
            time.sleep(2)
        _restore_moved(moved)
        time.sleep(3)

    if os.path.isdir(srv.server_world):
        shutil.rmtree(paths.client_world, ignore_errors=True)
        # session.lock 是 Windows 上的独占锁残留：搬档前先摘掉，避免整个目录 move 失败。
        try:
            os.remove(os.path.join(srv.server_world, "session.lock"))
        except OSError:
            pass
        moved_ok = False
        for attempt in range(3):
            try:
                shutil.move(srv.server_world, paths.client_world)
                moved_ok = True
                break
            except OSError as exc:
                if attempt == 2:
                    mc.write_mt_warn("MT_WARN: 移动服务端存档失败：" + str(exc))
                time.sleep(3)
        if moved_ok:
            shutil.rmtree(srv.server_world, ignore_errors=True)
    level_dat = os.path.join(paths.client_world, "level.dat")
    if not done or not os.path.isfile(level_dat):
        if crashed:
            return EXIT_FAIL
        mc.write_mt_line("MT_WORLD: BLOCKED — 世界未在时限内生成")
        return EXIT_BLOCKED
    if not set_allow_commands(level_dat):
        mc.write_mt_line("MT_WORLD: BLOCKED — level.dat 的 AllowCommands 未能设置")
        return EXIT_BLOCKED
    err = set_world_keep_inventory(version, paths)
    if err:
        mc.write_mt_line("MT_WORLD: BLOCKED — " + err)
        return EXIT_BLOCKED
    bad = require_flat_world(level_dat, "服务端生成的世界")
    if bad:
        mc.write_mt_line(bad)
        return EXIT_BLOCKED
    mc.write_mt_line("MT_WORLD: OK — 世界重建（allowCommands=1, keepInventory=true, "
                     "generator=" + GEN_FLAT + "） " + paths.client_world)
    return EXIT_PASS


# ── kill / shaders / debug ───────────────────────────────────────────────
def cmd_kill(version):
    paths = mc.get_paths(version)
    killed = mc.stop_version_processes(paths, quiet=True)
    mc.write_mt_line("MT_KILL: " + version + " 停止 " + str(killed) + " 个进程")
    return EXIT_PASS


def shader_config(paths):
    name = "oculus.properties" if paths.version == "1.20.1" else "iris.properties"
    return os.path.join(paths.run_dir, "config", name)


def read_enable_shaders(cfg):
    lines = _read_lines(cfg)
    if lines is None:
        return None, None
    for line in lines:
        stripped = line.strip()
        if not stripped.lower().startswith("enableshaders"):
            continue
        parts = stripped.split("=", 1)
        if len(parts) != 2:
            continue
        return parts[1].strip(), lines
    return None, lines


def cmd_shaders(version, state):
    paths = mc.get_paths(version)
    cfg = shader_config(paths)
    if not os.path.isfile(cfg):
        mc.write_mt_line("MT_SHADERS: BLOCKED — 未找到 " + cfg
                         + "（先跑 mt_env.py mods --version " + version + "）")
        return EXIT_BLOCKED
    current, lines = read_enable_shaders(cfg)
    shown = current if current is not None else "未设置"
    if state == "status":
        mc.write_mt_line("MT_SHADERS: " + shown + " (enableShaders=" + shown + ", " + cfg + ")")
        return EXIT_PASS
    if state not in ("on", "off"):
        mc.write_mt_error_line("非法 --state " + state + "（可选：off on status）")
        return EXIT_ERROR
    want = "true" if state == "on" else "false"
    kept = [ln for ln in (lines or [])
            if not ln.strip().lower().startswith("enableshaders")]
    kept.append("enableShaders=" + want)
    if not _write_ascii(cfg, kept):
        mc.write_mt_error_line("写 " + cfg + " 失败")
        return EXIT_ERROR
    mc.write_mt_line("MT_SHADERS: " + shown + " → " + state + " (enableShaders=" + want + ")")
    return EXIT_PASS


def cmd_debug(version, pause_lock, keybinds, shaders):
    paths = mc.get_paths(version)
    rc = EXIT_PASS
    if pause_lock != "status":
        if pause_lock not in ("on", "off"):
            mc.write_mt_error_line("非法 --pause-lock " + pause_lock + "（可选：on off status）")
            return EXIT_ERROR
        set_pause_on_lost_focus(paths, pause_lock == "off")
        if pause_lock == "on":
            mc.write_mt_line("MT_DEBUG: PAUSE_LOCK=on — 失焦不再打开 ESC 暂停菜单"
                             "（测试流程据此删除多余的 Esc 按键）")
        else:
            mc.write_mt_warn("MT_DEBUG: PAUSE_LOCK=off — 已允许失焦暂停："
                             "后台注入/截图可能被 ESC 菜单挡住（仅对照实验用）")
    current = get_pause_on_lost_focus(paths)
    if current is None:
        pause_text = "(未设置/options.txt 不存在)"
    elif current is False:
        pause_text = "on (pauseOnLostFocus=false)"
    else:
        pause_text = "off (pauseOnLostFocus=true)"
    mc.write_mt_line("MT_DEBUG: " + version + " pause-lock=" + pause_text)

    if keybinds != "status":
        if keybinds not in ("status", "repair"):
            mc.write_mt_error_line("非法 --keybinds " + keybinds + "（可选：status repair）")
            return EXIT_ERROR
        keybind_enforce(paths)
    else:
        drift = []
        for key, want in KEYBIND_EXPECTED:
            got = get_keybinding(paths, key)
            if got != want:
                drift.append(key + ":" + (got if got is not None else "（未设置）") + "→" + want)
        if drift:
            mc.write_mt_warn("MT_DEBUG: " + version + " keybinds=DRIFT — " + "; ".join(drift)
                             + "；用 --keybinds repair 或重跑 mt_env world/mods 改回")
        else:
            mc.write_mt_line("MT_DEBUG: " + version + " keybinds=OK（潜行/冲刺绑定为期望值）")

    if shaders != "status":
        rc = cmd_shaders(version, shaders)
        if rc != EXIT_PASS:
            return rc
    else:
        cfg = shader_config(paths)
        if not os.path.isfile(cfg):
            text = "n/a(该线无光影加载器配置)"
        else:
            value, _lines = read_enable_shaders(cfg)
            pack = ""
            for line in (_read_lines(cfg) or []):
                if line.strip().lower().startswith("shaderpack"):
                    parts = line.split("=", 1)
                    if len(parts) == 2:
                        pack = parts[1].strip()
                    break
            text = "enableShaders=" + (value if value is not None else "未设置")
            if pack:
                text += " shaderPack=" + pack
        mc.write_mt_line("MT_DEBUG: " + version + " shaders=" + text)
    return rc


def usage():
    return chr(10).join([
        "用法: python scripts/test/mt_env.py (mods|world|kill|kubejs|shaders|debug) --version <v> [选项]",
        "",
        "测试环境准备。",
        "",
        "子命令:",
        "  mods     kubejs 模板同步 + 渲染模组同步 + options.txt 三项守门（1.21.1 / fabric / 1.20.1 / 26.1.2）",
        "  world    重建测试世界（默认起专用服务端生成；--seed 从种子包快恢复）",
        "  kill     收停本版本的本流程进程",
        "  kubejs   只做 kubejs 模板同步（sha256 比对 + 清理模板里已删除的旧脚本）",
        "  shaders  光影开关（--state on|off|status）",
        "  debug    options.txt 三项诊断与修复（--pause-lock / --keybinds / --shaders）",
        "",
        "选项:",
        "  --version <v>             必填：1.21.1 | 1.20.1 | 26.1.2 | fabric-1.20.1 | fabric-1.21.1",
        "  --seed                    world 用种子包快恢复（需要 resources/testworld-seed-<v>.zip）",
        "  --state <on|off|status>   shaders 用（默认 status）",
        "  --shaders <on|off|status>  debug 用（默认 status）",
        "  --pause-lock <on|off|status>  debug 用（默认 status）",
        "  --keybinds <status|repair>    debug 用（默认 status）",
        "  --timeout <秒>            world 生成世界的最长等待（默认 180）",
        "  -h, --help                显示本帮助",
        "",
        "退出码: 0 = 成功 | 1 = 生成期崩溃报告 | 11 = BLOCKED（能力/前置缺失） | 2 = 参数错误",
        "",
        "能力缺失: ps1 的 Install-* 安装器（探针运行时 / 渲染栈 / 光影包 / AutoJS / 优化模组 /",
        "          Carpet / 史莱姆压制）本轮未复原；mods 改按「要求的 jar 是否已就位」判定，",
        "          缺即 BLOCKED(11)，不会静默返回 0。",
    ])


def main(argv):
    mc.init_console()
    cmd = ""
    version = ""
    seed = False
    state = "status"
    pause_lock = "status"
    keybinds = "status"
    timeout = 180
    index = 0
    while index < len(argv):
        token = argv[index]
        key = token.lstrip("-").lower()
        if token.startswith("-") and key in ("h", "help"):
            mc.write_mt_line(usage())
            return EXIT_PASS
        if token.startswith("-") and key in ("version", "state", "shaders", "pause-lock",
                                              "keybinds", "timeout"):
            index += 1
            if index >= len(argv):
                mc.write_mt_error_line("缺少 --" + key + " 的值")
                return EXIT_ERROR
            value = argv[index]
            if key == "version":
                version = value
            elif key in ("state", "shaders"):
                # ps1 只有一个光影开关变量（--state，debug 也读它）；--shaders 是本实现的
                # 显式别名，两者写同一个变量，所以 debug --state off 与 ps1 行为一致。
                state = value.lower()
            elif key == "pause-lock":
                pause_lock = value.lower()
            elif key == "keybinds":
                keybinds = value.lower()
            else:
                try:
                    timeout = int(value)
                except ValueError:
                    mc.write_mt_error_line("参数 --timeout 需要整数，收到 " + value)
                    return EXIT_ERROR
            index += 1
            continue
        if token.startswith("-") and key == "seed":
            seed = True
            index += 1
            continue
        if token.startswith("-"):
            mc.write_mt_error_line("未知参数 " + token)
            return EXIT_ERROR
        if cmd:
            mc.write_mt_error_line("未知参数 " + token)
            return EXIT_ERROR
        cmd = token.lower()
        index += 1

    if cmd not in ("mods", "world", "kill", "kubejs", "shaders", "debug"):
        mc.write_mt_error_line("必须指定子命令 mods / world / kill / kubejs / shaders / debug")
        return EXIT_ERROR
    if not version:
        mc.write_mt_error_line("必须指定 --version")
        return EXIT_ERROR
    if not mc.assert_version(version):
        return EXIT_ERROR
    if cmd == "mods":
        return cmd_mods(version)
    if cmd == "world":
        return cmd_world(version, seed, timeout)
    if cmd == "kill":
        return cmd_kill(version)
    if cmd == "kubejs":
        return sync_kubejs(version)
    if cmd == "shaders":
        return cmd_shaders(version, state)
    return cmd_debug(version, pause_lock, keybinds, state)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
