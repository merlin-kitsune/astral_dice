#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""筹码商店守门（旧版 verify_chip_shop_ui 实现 的 Python 等价迁移）。

静态判据 G1..G6 界面骨架 + G9..G27 业务逻辑（编译 G7 与资源守门 G8 另行执行）。
判据正本：docs/chip-shop-ui-spec.md §8 / docs/chip-shop-logic-plan.md §9。

用法（任意目录均可，仓库根按脚本位置上溯两级求）：
    python scripts/verify/verify_chip_shop_ui.py            # 两线全跑
    python scripts/verify/verify_chip_shop_ui.py neo        # 只查 neoforge-1.21.1
    python scripts/verify/verify_chip_shop_ui.py forge      # 只查 forge-1.20.1
退出码 0 = 全绿；1 = 有缺失/不一致；2 = 无法判定（一条线都没扫到 —— 反假绿守卫）。

输出契约与 旧实现 完全一致（前缀 / 状态词 / 行文本逐字对齐）：
    'CHIPUI: …' / 'CHIPUI_ROOT: …' / '--- <line> ---' / 'CHIPUI_FAIL: …' /
    'CHIPUI_ASSETS|LAYOUT|FRAME|INTERACT|LANG|LANGKEYS|MENUTYPE|SLOTS|BIND|G9|G10|G17|G18|
     LOGIC|G21|G21b|G22|G23..G27|NEXT|TOTAL_FAIL|RESULT: …'

反假绿守卫（2026-11-02 批次 B，本仓踩过「错误目录跑出 vacuous PASS」的坑）：
  · 仓库根一律从 __file__ 上溯两级求（不读 cwd）；
  · 一条线都没进（两个子项目目录都不存在）时追加一条 CHIPUI_FAIL 并 **exit 2**（无法判定），
    绝不以 'CHIPUI_RESULT: OK' 收场。正常仓库根下该分支永不触发，故不影响与 旧实现 的逐字输出。
"""

import fnmatch
import hashlib
import json
import os
import re
import sys

OUT = []


def w(text=""):
    OUT.append(text + "\n")


def emit():
    sys.stdout.buffer.write("".join(OUT).encode("utf-8", "replace"))
    sys.stdout.buffer.flush()


ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

LINES = {"neo": "neoforge-1.21.1", "forge": "forge-1.20.1"}
MT_VERSIONS = {"neo": "1.21.1", "forge": "1.20.1"}

# 仓库根：默认从 __file__ 上溯三级（不读 cwd）；--root 显式覆盖（反假绿守卫的实测入口）。
_positionals = []
_root_arg = ""
_i = 1
while _i < len(sys.argv):
    _a = sys.argv[_i]
    if _a == "--root" and _i + 1 < len(sys.argv):
        _root_arg = sys.argv[_i + 1]
        _i += 2
        continue
    if not _a.startswith("-"):
        _positionals.append(_a.lower())
    _i += 1
if _root_arg:
    ROOT = os.path.abspath(_root_arg)

targets = []
arg0 = _positionals[0] if _positionals else ""
if arg0 == "neo":
    targets = ["neo"]
elif arg0 == "forge":
    targets = ["forge"]
else:
    targets = ["neo", "forge"]

fail = []


def Fail(msg):
    fail.append(msg)
    w("CHIPUI_FAIL: " + msg)


def read_src(p):
    """等价 Read-Src：文件不存在返回空串（避免 Stop 中断）。"""
    try:
        with open(p, "rb") as fh:
            data = fh.read()
    except OSError:
        return ""
    return data.decode("utf-8-sig", "replace")


def read_bytes(p):
    try:
        with open(p, "rb") as fh:
            return fh.read()
    except OSError:
        return b""


def sha256_hex(p):
    h = hashlib.sha256()
    with open(p, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest().upper()


def strip_comments(t):
    """等价 Strip-Comments：去 Java 块注释 + 行注释，不碰字符串/字符字面量。"""
    if not t:
        return ""
    sb = []
    i = 0
    n = len(t)
    in_str = False
    in_char = False
    in_line = False
    in_block = False
    while i < n:
        c = t[i]
        d = t[i + 1] if i + 1 < n else "\x00"
        if in_line:
            if c == "\n":
                in_line = False
                sb.append(c)
            i += 1
            continue
        if in_block:
            if c == "*" and d == "/":
                in_block = False
                i += 2
                continue
            if c == "\n":
                sb.append(c)
            i += 1
            continue
        if in_str:
            sb.append(c)
            if c == "\\":
                if i + 1 < n:
                    sb.append(t[i + 1])
                i += 2
                continue
            if c == '"':
                in_str = False
            i += 1
            continue
        if in_char:
            sb.append(c)
            if c == "\\":
                if i + 1 < n:
                    sb.append(t[i + 1])
                i += 2
                continue
            if c == "'":
                in_char = False
            i += 1
            continue
        if c == "/" and d == "/":
            in_line = True
            i += 2
            continue
        if c == "/" and d == "*":
            in_block = True
            i += 2
            continue
        if c == '"':
            in_str = True
            sb.append(c)
            i += 1
            continue
        if c == "'":
            in_char = True
            sb.append(c)
            i += 1
            continue
        sb.append(c)
        i += 1
    return "".join(sb)


def code_src(p):
    return strip_comments(read_src(p))


def isfile(p):
    return os.path.isfile(p)


def isdir(p):
    return os.path.isdir(p)


def java_files(line_dir):
    """等价 Get-ChildItem -Recurse -Filter '*.java' -File（确定性排序）。"""
    base = os.path.join(line_dir, "src", "main", "java")
    hits = []
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames.sort()
        for fn in filenames:
            if fn.lower().endswith(".java"):
                hits.append(os.path.join(dirpath, fn))
    hits.sort()
    return hits


def list_files(d, pattern):
    """等价 Get-ChildItem -LiteralPath d -Filter pattern -File（按名排序）。"""
    if not isdir(d):
        return []
    return sorted(n for n in os.listdir(d)
                  if fnmatch.fnmatch(n, pattern) and os.path.isfile(os.path.join(d, n)))


def sort_unique(seq):
    seen = set()
    out = []
    for s in sorted(seq, key=lambda x: (x.lower(), x)):
        k = s.lower()
        if k in seen:
            continue
        seen.add(k)
        out.append(s)
    return out


def as_list(v):
    """等价 @($x)：null -> []，标量 -> [标量]，数组 -> 自身。"""
    if v is None:
        return []
    if isinstance(v, list):
        return v
    return [v]


def get_body(text, sig):
    """等价 Get-Body：从 sig 起的第一个 '{' 做括号配平，返回含花括号的方法体。"""
    i = text.find(sig)
    if i < 0:
        return ""
    j = text.find("{", i)
    if j < 0:
        return ""
    depth = 0
    for k in range(j, len(text)):
        if text[k] == "{":
            depth += 1
        elif text[k] == "}":
            depth -= 1
            if depth == 0:
                return text[j:k + 1]
    return ""


def assert_order(text, seq, label, line):
    """等价 Assert-Order：前向扫描锚定 token 顺序（同名 token 重复出现也能定位）。"""
    prev = -1
    for t in seq:
        ix = text.find(t, prev + 1)
        if ix < 0:
            Fail(line + ": " + label + " 缺「" + t + "」")
            return False
        if ix < prev:
            Fail(line + ": " + label + " 顺序错误（「" + t + "」早于前一项）")
            return False
        prev = ix
    return True


TEX_NAMES = [
    "chipshop_bg.png", "chipshop_frame.png", "chipshop_frame_1.png", "chipshop_frame_2.png",
    "chipshop_inventory.png", "chipshop_confirm.png", "chipshop_confirm_push.png",
    "chipshop_refresh.png", "chipshop_refresh_push.png",
    "chipshop_quality_1.png", "chipshop_quality_2.png", "chipshop_quality_3.png", "chipshop_select.png",
]
DRAWN_TEX_NAMES = list(TEX_NAMES)

LAYOUT_TOKENS = [
    "GUI_WIDTH = 284", "GUI_HEIGHT = 183",
    "STRIP_X = 34", "STRIP_Y = 6", "STRIP_W = 216", "STRIP_H = 78",
    "CELL_X = {3, 45, 88, 131, 174}",
    "BUTTON_SIZE = 24", "CONFIRM_X = 11", "CONFIRM_Y = 36", "REFRESH_X = 181", "REFRESH_Y = 36",
    "QUALITY_SIZE = 28", "QUALITY_X = {51, 94, 137}", "QUALITY_Y = 31",
    "CHIP_ICON_SIZE = 24", "CHIP_ICON_OFF = (QUALITY_SIZE - CHIP_ICON_SIZE) / 2",
    "LABEL_W = 42", "LABEL_H = 15", "LABEL_Y = 61", "LABEL_X = {1, 44, 87, 130, 172}",
    "PANEL_X = 56", "PANEL_Y = 89", "PANEL_W = 172", "PANEL_H = 86",
    "SLOT_X0 = 61", "SLOT_Y0 = 94", "SLOT_PITCH = 18", "SLOT_COLS = 9", "SLOT_ROWS = 3", "HOTBAR_Y = 152",
]

LANG_KEYS = [
    "gui.astral_dice.chip_shop",
    "gui.astral_dice.chip_shop.cost",
    "gui.astral_dice.chip_shop.refresh_counter",
    "gui.astral_dice.chip_shop.empty",
    "block.astral_dice.chip_shop",
    "msg.astral_dice.chip_shop_no_coin",
    "msg.astral_dice.chip_shop_no_refresh",
    "msg.astral_dice.chip_shop_bought",
    "msg.astral_dice.chip_shop_owned_all",
    "msg.astral_dice.chip_shop_inventory_full",
]

FORBIDDEN = ["mouseDragged", "DOUBLE_CLICK_MS", "DRAG_THRESHOLD", "dragIndex"]

w("CHIPUI: 筹码商店界面守门（G1..G6 静态判据）")
w("CHIPUI_ROOT: " + ROOT)

scanned = 0

for key in targets:
    line = LINES[key]
    line_dir = os.path.join(ROOT, line)
    w("--- " + line + " ---")

    if not isdir(line_dir):
        Fail(line + ": 子项目目录不存在")
        continue
    scanned += 1

    # ================= G1 素材 =================
    tex_dir = os.path.join(line_dir, "src/main/resources/assets/astral_dice/textures/gui/shop")
    ok = 0
    for n in TEX_NAMES:
        src = os.path.join(ROOT, "images/shop/", n)
        dst = os.path.join(tex_dir, n)
        if not isfile(src):
            Fail(line + ": 源素材缺失 images/shop/" + n)
            continue
        if not isfile(dst):
            Fail(line + ": 素材未入库 " + n)
            continue
        if sha256_hex(src) != sha256_hex(dst):
            Fail(line + ": 素材与源不一致 " + n)
        else:
            ok += 1
    ref_dst = os.path.join(tex_dir, "chip_shop_GUI.png")
    if isfile(ref_dst):
        Fail(line + ": 参考图 chip_shop_GUI.png 不得入 jar")
    w("CHIPUI_ASSETS: " + str(ok) + "/" + str(len(TEX_NAMES)) + " 逐字节一致")

    # ================= G2/G3/G4 屏幕类 =================
    screen_path = os.path.join(line_dir, "src/main/java/com/merlinkitsune/astral_dice/screen/ChipShopScreen.java")
    body_match = None
    src_screen = ""
    if not isfile(screen_path):
        Fail(line + ": 缺少 screen/ChipShopScreen.java")
    else:
        src_screen = code_src(screen_path)
        missing = [t for t in LAYOUT_TOKENS if t not in src_screen]
        if missing:
            Fail(line + ": 布局常量缺失 " + " | ".join(missing))
        w("CHIPUI_LAYOUT: " + str(len(LAYOUT_TOKENS) - len(missing)) + "/" + str(len(LAYOUT_TOKENS)) + " 常量命中")

        reg_only = ["TEX_STRIP_FRAME"]
        defs = {}
        for m in re.finditer(r'private static final String (TEX_[A-Za-z0-9_]+)\s*=\s*"([^"]+)"', src_screen):
            defs[m.group(1)] = m.group(2)
        missing_defs = [n for n in DRAWN_TEX_NAMES if n not in defs.values()]
        if missing_defs:
            Fail(line + ": 缺少贴图常量定义 " + " | ".join(missing_defs))

        # 1) tex(...) 实参文本（括号配平）
        arg_text = ""
        helper_names = []
        for m in re.finditer(r"tex\(", src_screen):
            i = m.end()
            depth = 1
            start_arg = i
            while i < len(src_screen) and depth > 0:
                ch = src_screen[i]
                if ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
                i += 1
            arg = src_screen[start_arg:start_arg + max(0, i - 1 - start_arg)]
            arg_text += arg + " "
            for h in re.finditer(r"([A-Za-z_][A-Za-z0-9_]*)\s*\(", arg):
                helper_names.append(h.group(1))
        used = {}
        for m in re.finditer(r"(TEX_[A-Za-z0-9_]+)", arg_text):
            used[m.group(1)] = True
        # 2) 被 tex(...) 调用的字符串返回助手方法体（例如 qualityTex(frame)）
        for h in list(dict.fromkeys(helper_names)):
            mm = re.search(r"private static String " + re.escape(h) + r"\([^)]*\)\s*\{(?P<b>.*?)\n    \}",
                           src_screen, re.S)
            if not mm:
                continue
            for m in re.finditer(r"(TEX_[A-Za-z0-9_]+)", mm.group("b")):
                used[m.group(1)] = True
        unref = []
        for k in defs:
            if k in used:
                continue
            if k in reg_only:
                continue
            unref.append(k + "(" + defs[k] + ")")
        if unref:
            Fail(line + ": 贴图常量取不到绘制路径（tex(...) 实参或其字符串助手方法体内均未出现）" + " | ".join(unref))
        if "chip_shop_GUI" in src_screen:
            Fail(line + ": 源码不得引用参考图 chip_shop_GUI")

        # ---- G2b 品质外框（2026-10-22 用户裁决：恢复绘制）----
        frame_need = [
            "int frame = ChipShopClientState.qualityCode(chip)",
            "frame = ChipShopClientState.qualityFrameIndexOf(stack)",
            "frame != ChipShopClientState.QUALITY_FRAME_NONE",
            "blitStrip(guiGraphics, tex(qualityTex(frame))",
            "QUALITY_X[chip], QUALITY_Y, QUALITY_SIZE, QUALITY_SIZE",
        ]
        frame_miss = [x for x in frame_need if x not in src_screen]
        if frame_miss:
            Fail(line + ": 品质外框绘制锚点缺失 " + " | ".join(frame_miss))
        qm = re.search(r"private static String qualityTex\([^)]*\)\s*\{(?P<b>.*?)\n    \}", src_screen, re.S)
        if not qm:
            Fail(line + ": 缺少 qualityTex(...) 字符串助手（品质外框三档映射）")
        else:
            qb = qm.group("b")
            q_miss = [t for t in ("frame == 1", "TEX_QUALITY_1", "frame == 2", "TEX_QUALITY_2", "TEX_QUALITY_3")
                      if t not in qb]
            if q_miss:
                Fail(line + ": qualityTex 三档映射不完整 " + " | ".join(q_miss))
        icon_need = [
            "stripX(QUALITY_X[chip] + CHIP_ICON_OFF)",
            "stripY(QUALITY_Y + CHIP_ICON_OFF)",
            "entry.iconLocation()",
            "CHIP_ICON_SIZE, CHIP_ICON_SIZE",
            "CHIP_ICON_OFF = (QUALITY_SIZE - CHIP_ICON_SIZE) / 2",
        ]
        icon_miss = [x for x in icon_need if x not in src_screen]
        if icon_miss:
            Fail(line + ": 无框图标绘制锚点缺失 " + " | ".join(icon_miss))
        w("CHIPUI_FRAME: 品质外框已绘制（qualityCode 优先 / qualityFrameIndexOf 兜底 / NONE 跳过，"
          "三档 28x28 描边环）；24x24 图标仍锚在 28x28 格位（CHIP_ICON_OFF，内缩 2 px）")

        bad = [f for f in FORBIDDEN if f in src_screen]
        if bad:
            Fail(line + ": 出现被禁的拖拽/双击标识符 " + " | ".join(bad))
        occur = len(re.findall(re.escape("onConfirmPressed()"), src_screen))
        if occur != 2:
            Fail(line + ": onConfirmPressed() 出现次数应为 2（声明 + 确认分支各一次），实测 " + str(occur))
        body_match = re.search(r"public boolean mouseClicked\(double mouseX, double mouseY, int button\)\s*\{(?P<b>.*?)\n    \}",
                               src_screen, re.S)
        if not body_match:
            Fail(line + ": 未找到 mouseClicked 方法体（无法校验调用点位置）")
        else:
            body = body_match.group("b")
            call_idx = body.find("onConfirmPressed();")
            confirm_idx = body.find("CONFIRM_X")
            chip_idx = body.find("chipIndexAt(")
            if (call_idx < 0 or confirm_idx < 0 or chip_idx < 0
                    or call_idx < confirm_idx or call_idx > chip_idx):
                Fail(line + ": onConfirmPressed() 调用点不在确认按钮分支内（call=" + str(call_idx)
                     + " confirm=" + str(confirm_idx) + " chip=" + str(chip_idx) + "）")
        if "buyRequestCount" not in src_screen:
            Fail(line + ": 缺少只读探针 buyRequestCount")
        w("CHIPUI_INTERACT: 禁双击/拖拽标识符 " + str(len(FORBIDDEN) - len(bad)) + "/"
          + str(len(FORBIDDEN)) + " 通过；onConfirmPressed 调用次数 " + str(occur))

    # ================= G5 文案 =================
    lang_dir = os.path.join(line_dir, "src/main/resources/assets/astral_dice/lang")
    if not isdir(lang_dir):
        Fail(line + ": 缺少 lang 目录")
    else:
        lang_files = list_files(lang_dir, "*.json")
        if len(lang_files) < 3:
            Fail(line + ": lang 文件少于 3 个（实测 " + str(len(lang_files)) + "）")
        for lf in lang_files:
            t = read_src(os.path.join(lang_dir, lf))
            miss = [k for k in LANG_KEYS if ('"' + k + '"') not in t]
            if miss:
                Fail(line + ": " + lf + " 缺文案键 " + " | ".join(miss))
        lang_bad = len([f for f in fail
                        if f.startswith(line + ": ") and "缺文案键" in f])
        w("CHIPUI_LANG: " + str(len(lang_files)) + " 个语言文件，缺键文件数 " + str(lang_bad))

        # ---- G5b 三语键集对称 + Java 引用键不得悬空 ----
        lang_sets = {}
        for lf in lang_files:
            try:
                obj = json.loads(read_src(os.path.join(lang_dir, lf)))
            except ValueError:
                Fail(line + ": " + lf + " 不是合法 JSON")
                continue
            if not isinstance(obj, dict):
                obj = {}
            lang_sets[lf] = list(obj.keys())
        lang_names = list(lang_sets.keys())
        if len(lang_names) >= 2:
            base_keys = lang_sets[lang_names[0]]
            for n in lang_names[1:]:
                diff = []
                for k in base_keys:
                    if k not in lang_sets[n]:
                        diff.append("缺 " + k)
                for k in lang_sets[n]:
                    if k not in base_keys:
                        diff.append("多 " + k)
                if diff:
                    Fail(line + ": 语言键集不对称 " + lang_names[0] + " vs " + n + "：" + " / ".join(diff))
        used_keys = {}
        for f in java_files(line_dir):
            for m in re.finditer(r'"(?:gui|block|msg)\.astral_dice\.chip_shop[a-z0-9_.]*"', code_src(f)):
                used_keys[m.group(0).strip('"')] = os.path.basename(f)
        dangling = []
        for k in used_keys:
            for lf in lang_files:
                if k not in lang_sets.get(lf, []):
                    dangling.append(lf + " 缺 " + k)
        dangling = list(dict.fromkeys(dangling))
        if dangling:
            Fail(line + ": 悬空语言键（Java 引用但 lang 无）：" + " / ".join(dangling))
        base_count = len(lang_sets[lang_names[0]]) if lang_names else 0
        w("CHIPUI_LANGKEYS: 三语键集对称（" + str(base_count) + " 键）＋ Java 引用 "
          + str(len(used_keys)) + " 个 chip_shop 键；悬空 " + str(len(dangling)) + " 项")

    # ================= G6 接线 =================
    menu_types = os.path.join(line_dir, "src/main/java/com/merlinkitsune/astral_dice/screen/ModMenuTypes.java")
    if not isfile(menu_types):
        Fail(line + ": 缺少 ModMenuTypes.java")
    else:
        mt = code_src(menu_types)
        if "chip_shop" not in mt:
            Fail(line + ": ModMenuTypes 未注册 chip_shop")
        else:
            w("CHIPUI_MENUTYPE: chip_shop 已注册")
    menu_path = os.path.join(line_dir, "src/main/java/com/merlinkitsune/astral_dice/shop/ChipShopMenu.java")
    if not isfile(menu_path):
        Fail(line + ": 缺少 shop/ChipShopMenu.java")
    else:
        ms0 = code_src(menu_path)
        slot_count = ms0.count("addSlot(new Slot(playerInventory,")
        if slot_count != 36:
            Fail(line + ": ChipShopMenu 玩家槽应为 36 个（9x3 + 快捷栏 9），实测 " + str(slot_count))
        else:
            w("CHIPUI_SLOTS: 36 个玩家槽")
    bind = []
    for f in java_files(line_dir):
        for ln in code_src(f).split("\n"):
            if "CHIP_SHOP" in ln and "ChipShopScreen::new" in ln:
                bind.append(f)
                break
    if not bind:
        Fail(line + ": 客户端未把 ChipShopScreen 绑到 chip_shop 菜单"
             "（需同一行同时出现 CHIP_SHOP 与 ChipShopScreen::new，且不得在注释里）")
    else:
        w("CHIPUI_BIND: " + os.path.basename(bind[0]) + " 绑定 ChipShopScreen（同行含 CHIP_SHOP）")

    # ================= G9..G21 业务逻辑 =================
    java_dir = os.path.join(line_dir, "src/main/java/com/merlinkitsune/astral_dice")
    cat_src = code_src(os.path.join(java_dir, "shop/ChipShopCatalog.java"))
    roller_src = code_src(os.path.join(java_dir, "shop/ChipShopRoller.java"))
    state_src = code_src(os.path.join(java_dir, "shop/ChipShopState.java"))
    opener_src = code_src(os.path.join(java_dir, "shop/ChipShopOpener.java"))
    cs_src = code_src(os.path.join(java_dir, "client/ChipShopClientState.java"))
    src = code_src(screen_path)
    ms = code_src(menu_path)

    # ---- G9 池 67 条 + 图标 67/67 ----
    plan_path = os.path.join(ROOT, "docs/chip-shop-logic-plan.md")
    plan_icon_dir = os.path.join(ROOT, "images/shop/unbord_chips")
    plan_rows = []
    if not isfile(plan_path):
        Fail(line + ": 缺少判据正本 docs/chip-shop-logic-plan.md")
    else:
        row_pattern = r'^\|\s*\x60([a-z0-9_]+)\x60\s*\|([^|]*)\|\s*\x60([^\x60]+\.png)\x60[^|]*\|([^|]*)\|([^|]*)\|([^|]*)\|'
        for pl in read_src(plan_path).splitlines():
            pm = re.match(row_pattern, pl)
            if pm:
                plan_rows.append({
                    "id": pm.group(1),
                    "name": pm.group(2).strip(),
                    "icon": pm.group(3),
                    "quality": pm.group(4).split("(")[0].strip(),
                    "school": pm.group(5).strip(),
                    "cat": pm.group(6).strip(),
                })
    cat_rows = []
    for cm in re.finditer(r'e\("([a-z0-9_]+)",\s*TIER_([A-Z]+),\s*School\.([A-Z]+),\s*Category\.([A-Z]+)\)', cat_src):
        cat_rows.append({"id": cm.group(1), "tier": cm.group(2), "school": cm.group(3), "cat": cm.group(4)})
    if len(plan_rows) != 67:
        Fail(line + ": 计划表 §1 筹码行应为 67，实测 " + str(len(plan_rows)))
    if len(cat_rows) != 67:
        Fail(line + ": ChipShopCatalog 池条目应为 67，实测 " + str(len(cat_rows)))

    quality_map = {"稀有": 1, "史诗": 2, "传奇": 3}
    tier_code_map = {"RARE": 1, "EPIC": 2, "LEGENDARY": 3}
    school_map = {"星光": "STARLIGHT", "治愈": "HEALING", "标记": "MARK", "充能": "CHARGE", "泛用": "GENERIC"}
    cat_map = {"攻击力": "ATTACK", "强化": "REINFORCE", "辅助": "SUPPORT", "治疗": "HEAL", "经济": "ECONOMY"}
    cmp_n = 0
    mismatch = []
    for i in range(min(len(plan_rows), len(cat_rows))):
        pr = plan_rows[i]
        cr = cat_rows[i]
        eq = True
        if pr["id"] != cr["id"]:
            eq = False
            mismatch.append("行" + str(i + 1) + " id：计划表 " + pr["id"] + " vs 池 " + cr["id"])
        want_tier = quality_map.get(pr["quality"])
        got_tier = tier_code_map.get(cr["tier"])
        if not want_tier:
            eq = False
            mismatch.append(pr["id"] + " 计划表品质未识别：" + pr["quality"])
        elif not got_tier:
            eq = False
            mismatch.append(pr["id"] + " 池品质码未识别：" + cr["tier"])
        elif want_tier != got_tier:
            eq = False
            mismatch.append(pr["id"] + " 品质：计划表 " + str(want_tier) + " vs 池 " + str(got_tier))
        want_school = school_map.get(pr["school"])
        if not want_school:
            eq = False
            mismatch.append(pr["id"] + " 计划表流派未识别：" + pr["school"])
        elif want_school != cr["school"]:
            eq = False
            mismatch.append(pr["id"] + " 流派：计划表 " + want_school + " vs 池 " + cr["school"])
        want_cat = cat_map.get(pr["cat"])
        if not want_cat:
            eq = False
            mismatch.append(pr["id"] + " 计划表类别未识别：" + pr["cat"])
        elif want_cat != cr["cat"]:
            eq = False
            mismatch.append(pr["id"] + " 类别：计划表 " + want_cat + " vs 池 " + cr["cat"])
        if eq:
            cmp_n += 1
    if mismatch:
        Fail(line + ": 池与计划表 §1 不一致 " + " | ".join(mismatch))

    chip_tex_dir = os.path.join(line_dir, "src/main/resources/assets/astral_dice/textures/gui/chip_shop/chips")
    icon_ok = 0
    icon_bad = []
    if not isdir(chip_tex_dir):
        Fail(line + ": 缺少筹码图标目录 textures/gui/chip_shop/chips")
    else:
        icon_files = list_files(chip_tex_dir, "*.png")
        if len(icon_files) != 67:
            Fail(line + ": 筹码图标应为 67 张，实测 " + str(len(icon_files)))
        plan_ids = [r["id"] for r in plan_rows]
        plan_ids_l = {x.lower() for x in plan_ids}
        extra = [n for n in icon_files if os.path.splitext(n)[0].lower() not in plan_ids_l]
        if extra:
            Fail(line + ": 图标目录含计划表外文件 " + " | ".join(extra))
        for pr in plan_rows:
            dst = os.path.join(chip_tex_dir, pr["id"] + ".png")
            src_icon = os.path.join(plan_icon_dir, pr["icon"])
            if not isfile(dst):
                icon_bad.append(pr["id"] + "：图标缺失")
                continue
            if not isfile(src_icon):
                icon_bad.append(pr["id"] + "：源素材缺失 " + pr["icon"])
                continue
            if sha256_hex(src_icon) != sha256_hex(dst):
                icon_bad.append(pr["id"] + "：与 " + pr["icon"] + " 不一致")
                continue
            bb = read_bytes(dst)
            if len(bb) < 24:
                icon_bad.append(pr["id"] + "：PNG 头不足 24 字节")
                continue
            iw = bb[16] * 16777216 + bb[17] * 65536 + bb[18] * 256 + bb[19]
            ih = bb[20] * 16777216 + bb[21] * 65536 + bb[22] * 256 + bb[23]
            if iw != 32 or ih != 32:
                icon_bad.append(pr["id"] + "：尺寸 " + str(iw) + "x" + str(ih) + "（blit 源尺寸固定 32x32）")
                continue
            icon_ok += 1
        if icon_bad:
            Fail(line + ": 筹码图标不合格 " + " | ".join(icon_bad))
    w("CHIPUI_G9: 池 " + str(len(cat_rows)) + " 条（逐行一致 " + str(cmp_n) + "/" + str(len(plan_rows))
      + "）；图标 " + str(icon_ok) + "/67 逐字节一致 + 32x32")

    # ---- G10 类别 5 覆盖 / 每枚恰一类 / 品质码 ⊆ {1,2,3} ----
    cat_body_m = re.search(r"enum Category\s*\{(?P<b>.*?)\n    \}", cat_src, re.S)
    cat_names = []
    if cat_body_m:
        cat_names = re.findall(r"^\s*([A-Z][A-Z_]*)\s*[,;]?\s*$", cat_body_m.group("b"), re.M)
    if len(cat_names) != 5:
        Fail(line + ": Category 枚举应为 5 项，实测 " + str(len(cat_names)) + "（" + ",".join(cat_names) + "）")
    used_cats = sort_unique([r["cat"] for r in cat_rows])
    if len(used_cats) != 5:
        Fail(line + ": 池覆盖类别应为 5 个，实测 " + str(len(used_cats)) + "（" + ",".join(used_cats) + "）")
    n_cat_ref = len(re.findall(r"Category\.[A-Z]", cat_src))
    n_school_ref = len(re.findall(r"School\.[A-Z]", cat_src))
    if n_cat_ref != 67 or n_school_ref != 67:
        Fail(line + ": 池内 School./Category. 引用应各 67 次（每枚恰一类一流派），实测 "
             + str(n_school_ref) + "/" + str(n_cat_ref))
    for tc_name, tc_code in (("TIER_RARE", 1), ("TIER_EPIC", 2), ("TIER_LEGENDARY", 3)):
        if not re.search(re.escape(tc_name) + r"\s*=\s*" + str(tc_code) + r"\s*;", cat_src):
            Fail(line + ": 品质码常量不符 " + tc_name + " = " + str(tc_code))
    bad_tier = [r for r in cat_rows if r["tier"] not in tier_code_map]
    if bad_tier:
        Fail(line + ": 池内品质码超出 {1,2,3}：" + " | ".join(r["id"] + "/" + r["tier"] for r in bad_tier))
    w("CHIPUI_G10: 类别 " + str(len(cat_names)) + " 项、池覆盖 " + str(len(used_cats)) + " 项；品质码 ⊆ {1,2,3}")

    logic_fail_start = len(fail)

    # ---- G11 概率表逐字 + slot 衰减 ----
    prob_miss = []
    for t in ("{0.60, 0.37, 0.03}", "{0.40, 0.45, 0.15}", "{0.25, 0.50, 0.25}"):
        if t not in cat_src:
            prob_miss.append(t)
    n6037 = cat_src.count("{0.60, 0.37, 0.03}")
    if n6037 != 2:
        prob_miss.append("0★/1★ 同表（60/37/3 应出现 2 份，实测 " + str(n6037) + "）")
    for t in ("SCHOOL_ALPHA = 0.60", "CATEGORY_ALPHA = 2.0", "SLOT_DECAY = 0.25"):
        if t not in cat_src:
            prob_miss.append(t)
    if prob_miss:
        Fail(line + ": 概率/权重常量缺失或走样 " + " | ".join(prob_miss))
    if "ChipShopCatalog.QUALITY_PROB[star]" not in roller_src:
        Fail(line + ": 抽取器未按星级取表 QUALITY_PROB[star]")
    if "Math.max(0, Math.min(3," not in roller_src:
        Fail(line + ": 星级未夹在 0..3")
    if "(CATEGORY_ALPHA - 1.0) * SLOT_DECAY + 1.0" not in cat_src:
        Fail(line + ": slot1/2 衰减式不符 (factor-1)*SLOT_DECAY+1")
    if "return CATEGORY_ALPHA;" not in cat_src:
        Fail(line + ": slot0 全强度类别偏置缺失")

    # ---- G12 去重与缺口顺序 ----
    for t in ("if (pool.isEmpty()) {", "picked.add(chosen);", "pool.remove(chosen);"):
        if t not in roller_src:
            Fail(line + ": 抽取器缺「去重/缺口」语句 " + t)
    if "picked.set(" in roller_src:
        Fail(line + ": 抽取器不得 set 覆盖已抽中的槽（必须 append 保缺口顺序）")

    # ---- G13 已拥有过滤 + 两种拒绝进入 ----
    for t in ("availablePool(", "ownedChipItems("):
        if t not in roller_src:
            Fail(line + ": 抽取器缺已拥有过滤 " + t)
    if not re.search(r"public boolean needsReroll\((?:net\.minecraft\.world\.entity\.)?(?:Player|LivingEntity) \w+\)", state_src):
        Fail(line + ": ChipShopState 缺 needsReroll")
    if "ChipShopRoller.isOwned(player, entry)" not in state_src:
        Fail(line + ": needsReroll 未按已拥有判定")
    modern_opener = "private static boolean prepare(" in opener_src
    opener_var = "holder" if modern_opener and "ChipShopRoller.ownsEverything(holder)" in opener_src else "player"
    for t in ("ChipShopRoller.ownsEverything(" + opener_var + ")", "msg.astral_dice.chip_shop_owned_all"):
        if t not in opener_src:
            Fail(line + ": 开店入口缺「已集齐」拒绝 " + t)
    for t in ("hasNoRoom(" + opener_var + ")", "msg.astral_dice.chip_shop_inventory_full"):
        if t not in opener_src:
            Fail(line + ": 开店入口缺「物品栏已满」拒绝 " + t)
    # G13b 「物品栏已满」的语义
    hnr = re.search(r"private static boolean hasNoRoom\((?:Player|LivingEntity) \w+\)\s*\{(?P<b>.*?)\n    \}", opener_src, re.S)
    if not hnr:
        Fail(line + ": 未找到 hasNoRoom 方法体")
    else:
        hb = hnr.group("b")
        if not re.search(r"for \(int i = 0; i < INVENTORY_SLOTS; i\+\+\)", hb):
            Fail(line + ": hasNoRoom 未遍历物品栏 36 格")
        if not re.search(r"if \(player\.getInventory\(\)\.getItem\(i\)\.isEmpty\(\)\)\s*\{\s*return false;", hb):
            Fail(line + ": hasNoRoom 的物品栏已满判定被改（须「遇空格即返 false」）")
        if not re.search(r"return true;", hb):
            Fail(line + ": hasNoRoom 全满时应返 true")
    if modern_opener:
        prep_body = get_body(opener_src, "private static boolean prepare(")
        if prep_body == "":
            Fail(line + ": 未找到 prepare 方法体")
        else:
            assert_order(prep_body,
                         ["ChipShopRoller.ownsEverything(" + opener_var + ")", "hasNoRoom(" + opener_var + ")",
                          "ensureOffers(" + opener_var + ")"],
                         "prepare 顺序（已集齐 → 物品栏满 → 补抽）", line)
        open_body = get_body(opener_src, "public static boolean openFor(")
        if open_body == "":
            Fail(line + ": 未找到 openFor 方法体")
        else:
            assert_order(open_body, ["prepare(" + opener_var + ")", "openMenu("],
                         "openFor 顺序（prepare → openMenu）", line)
    else:
        open_body = get_body(opener_src, "public static void open(")
        if open_body == "":
            Fail(line + ": 未找到 open 方法体")
        else:
            assert_order(open_body,
                         ["ChipShopRoller.ownsEverything(" + opener_var + ")", "hasNoRoom(" + opener_var + ")",
                          "ensureOffers(" + opener_var + ")", "openMenu("],
                         "open 顺序（已集齐 → 物品栏满 → 补抽 → openMenu）", line)

    # ---- G14 无记录 / 异类别不得有偏置 ----
    for t in ("if (lastPick == null || candidate == null || candidate != lastPick) {", "return 1.0;"):
        if t not in cat_src:
            Fail(line + ": categoryWeight 缺「无记录无偏置」语句 " + t)

    # ---- G15 两处命中（筹码格 ∪ 标签框）都只 select ----
    for t in ("LABEL_X[cell]", "CELL_X[cell]"):
        if t not in src:
            Fail(line + ": 筹码格命中未扩到标签框（缺 " + t + "）")
    if body_match:
        mb = body_match.group("b")
        ci = mb.find("chipIndexAt(")
        if ci < 0:
            Fail(line + ": mouseClicked 内未调用 chipIndexAt")
        else:
            after = mb[ci:]
            if "sendToServer" in after:
                Fail(line + ": 筹码格命中分支不得发包（只允许 select）")
            if "ChipShopClientState.select(" not in after:
                Fail(line + ": 筹码格命中分支缺 ChipShopClientState.select(...)")
            if "int chip = chipIndexAt(mx, my);" not in mb:
                Fail(line + ": 筹码格命中未按 chipIndexAt(mx, my) 取索引")
            if "if (chip >= 0) {" not in mb:
                Fail(line + ": 筹码格命中判定被改（须逐字为 if (chip >= 0) {）")

    # ---- G16 拒绝红闪 30t + 购买/刷新入口唯一 ----
    if "DENY_BLINK_TICKS = 30" not in cs_src:
        Fail(line + ": 拒绝红闪应为 30 tick")
    if "((denyTicks - 1) / 5) % 2 == 1" not in cs_src:
        Fail(line + ": 红闪节奏应为 (denyTicks-1)/5%2==1（每 5 tick 翻转）")
    buy_sends = src.count("new ChipShopBuyPayload(")
    if buy_sends != 1:
        Fail(line + ": 购买入口必须唯一（new ChipShopBuyPayload 应恰 1 处），实测 " + str(buy_sends))
    refresh_sends = src.count("new ChipShopRefreshPayload(")
    if refresh_sends != 1:
        Fail(line + ": 刷新入口必须唯一（new ChipShopRefreshPayload 应恰 1 处），实测 " + str(refresh_sends))
    cm2 = re.search(r"private void onConfirmPressed\(\)\s*\{(?P<b>.*?)\n    \}", src, re.S)
    if not cm2:
        Fail(line + ": 未找到 onConfirmPressed 方法体")
    elif "new ChipShopBuyPayload(" not in cm2.group("b"):
        Fail(line + ": onConfirmPressed 未发购买包")
    if cm2:
        cb = cm2.group("b")
        if "int chip = ChipShopClientState.selectedIndex();" not in cb:
            Fail(line + ": 确认按钮未按当前选中索引取筹码")
        if "chip < 0 || ChipShopClientState.offer(chip).isEmpty()" not in cb:
            Fail(line + ": 确认按钮缺「未选中或空候选即不发包」守卫"
                 "（须逐字含 chip < 0 || ChipShopClientState.offer(chip).isEmpty()）")
    rm2 = re.search(r"private void onRefreshPressed\(\)\s*\{(?P<b>.*?)\n    \}", src, re.S)
    if not rm2:
        Fail(line + ": 未找到 onRefreshPressed 方法体")
    else:
        rb = rm2.group("b")
        i_deny_r = rb.find("notifyDeny")
        i_send_r = rb.find("new ChipShopRefreshPayload(")
        if i_deny_r < 0:
            Fail(line + ": 刷新次数为 0 时应只红闪（未调用 notifyDeny）")
        if i_send_r < 0:
            Fail(line + ": onRefreshPressed 未发刷新包")
        elif i_deny_r >= 0 and i_deny_r < i_send_r:
            if "return;" not in rb[i_deny_r:i_send_r]:
                Fail(line + ": 刷新次数为 0 的拒绝分支缺 return（会继续发包）")

    # ---- G16b 隐藏候选槽 ----
    osm = re.search(r"private static class OfferSlot extends Slot\s*\{(?P<b>.*?)\n    \}", ms, re.S)
    if not osm:
        Fail(line + ": 未找到隐藏候选槽 OfferSlot 类")
    else:
        ob = osm.group("b")
        if not re.search(r"public boolean mayPlace\([^)]*\)\s*\{\s*return false;", ob):
            Fail(line + ": 隐藏槽 mustmayPlace=false（候选不得被放入）")
        if not re.search(r"public boolean mayPickup\([^)]*\)\s*\{\s*return false;", ob):
            Fail(line + ": 隐藏槽 must mayPickup=false（候选不得被拿走）")
        if not re.search(r"getMaxStackSize\(\)\s*\{\s*return 1;", ob):
            Fail(line + ": 隐藏槽 getMaxStackSize 应为 1")

    # ---- G17 价格公式 + 边界 ----
    for t in ("PRICE_INITIAL = 10", "PRICE_STEP = 5", "PRICE_MAX = 100",
              "Math.min(PRICE_MAX, PRICE_INITIAL + PRICE_STEP * count)",
              "Math.max(0, purchasedCount)"):
        if t not in cat_src:
            Fail(line + ": 价格公式/常量不符 " + t)
    price_curve = [min(100, 10 + 5 * max(0, nn)) for nn in (0, 1, 18, 30)]
    if ",".join(str(x) for x in price_curve) != "10,15,100,100":
        Fail(line + ": 价格曲线边界应为 0→10 / 1→15 / 18→100 / 30→100，实测 "
             + ",".join(str(x) for x in price_curve))
    price_calls = state_src.count("ChipShopCatalog.priceOf(purchasedCount)")
    if price_calls != 1:
        Fail(line + ": 价格唯一来源应为 ChipShopState.price() 内的 ChipShopCatalog.priceOf(purchasedCount)"
             "（实测 " + str(price_calls) + " 处）")
    if "priceOf(" in ms:
        Fail(line + ": 菜单不得自行算价（三格同价只能来自状态）")
    w("CHIPUI_G17: 价格曲线 0→" + str(price_curve[0]) + " / 1→" + str(price_curve[1])
      + " / 18→" + str(price_curve[2]) + " / 30→" + str(price_curve[3]))

    # ---- G18 刷新常量 3/5/36000 + 首次消耗才起算 + 手写钳制 ----
    for t in ("REFRESH_INITIAL = 3", "REFRESH_MAX = 5", "REFRESH_REGEN_TICKS = 36000"):
        if t not in cat_src:
            Fail(line + ": 刷新常量不符 " + t)
    if "state.regenAnchor() == 0L ? now + ChipShopCatalog.REFRESH_REGEN_TICKS : state.regenAnchor()" not in ms:
        Fail(line + ": 刷新锚点未按「首次消耗才起算」设置")
    if "state.refreshesLeft() <= 0" not in ms:
        Fail(line + ": 服务端未拦「刷新次数为 0」")
    wr = re.search(r"public ChipShopState withRefreshes\(int left, long anchor\)\s*\{(?P<b>.*?)\n    \}", state_src, re.S)
    if not wr:
        Fail(line + ": 未找到 withRefreshes 方法体")
    else:
        wb = wr.group("b")
        if not re.search(r"Math\.max\(0,\s*Math\.min\(ChipShopCatalog\.REFRESH_MAX,\s*left\)\)", wb):
            Fail(line + ": 刷新次数未夹在 0..REFRESH_MAX"
                 "（须 Math.max(0, Math.min(ChipShopCatalog.REFRESH_MAX, left))）")
        n_max = wb.count("Math.max(")
        if n_max != 1:
            Fail(line + ": withRefreshes 内 Math.max 应恰 1 处（实测 " + str(n_max) + "）")

    # ---- G18c 自动恢复 ticker 必须彻底消失 ----
    if isfile(os.path.join(java_dir, "shop/ChipShopRegenTicker.java")):
        Fail(line + ": 自动恢复 ticker 仍存在（shop/ChipShopRegenTicker.java 必须删除）")
    ticker_refs = []
    for f in java_files(line_dir):
        if "ChipShopRegenTicker" in code_src(f):
            ticker_refs.append(os.path.basename(f))
        elif "{@link ChipShopRegenTicker}" in read_src(f):
            ticker_refs.append(os.path.basename(f) + "（javadoc 悬空链接）")
    if ticker_refs:
        Fail(line + ": 仍有 ticker 引用 " + " | ".join(ticker_refs))

    # ---- G18d needsReroll 不得再按骰子签名判定 ----
    nr = re.search(r"public boolean needsReroll\((?:net\.minecraft\.world\.entity\.)?(?:Player|LivingEntity) \w+\)\s*\{(?P<b>.*?)\n    \}", state_src, re.S)
    if not nr:
        Fail(line + ": 未找到 needsReroll 方法体")
    else:
        nb = nr.group("b")
        if "diceKey" in nb:
            Fail(line + ": needsReroll 仍按骰子签名判定（换装备/换骰子会免费刷新），须删除该比对")
        for t in ("list.isEmpty()", "list.size() != offers.size()", "ChipShopRoller.isOwned(player, entry)"):
            if t not in nb:
                Fail(line + ": needsReroll 缺其它重抽条件 " + t)

    # ---- G18e withRefreshGranted ----
    wrg = re.search(r"public ChipShopState withRefreshGranted\(\)\s*\{(?P<b>.*?)\n    \}", state_src, re.S)
    if not wrg:
        Fail(line + ": 未找到 withRefreshGranted 方法体（升星授予的唯一入口）")
    else:
        gb = wrg.group("b")
        if not re.search(r"Math\.max\(0,\s*Math\.min\(ChipShopCatalog\.REFRESH_MAX,\s*refreshesLeft \+ 1\)\)", gb):
            Fail(line + ": withRefreshGranted 未按「现有 refreshesLeft + 1 并夹 0..REFRESH_MAX」实现")
        if "REFRESH_INITIAL" in gb:
            Fail(line + ": withRefreshGranted 不得回填 REFRESH_INITIAL（会清掉存档次数）")
        keep = ["offers", "lastPickCategory", "purchasedCount", "regenAnchor", "cooldownUntil",
                "lastBlankInsertAt", "rolledDiceKey"]
        keep_miss = [t for t in keep if t not in gb]
        if keep_miss:
            Fail(line + ": withRefreshGranted 未保留其它 record 字段 " + " | ".join(keep_miss))

    # ---- G18f 升星授予唯一在 AnvilRepairEvent ----
    anvil_src = code_src(os.path.join(java_dir, "event/AnvilUpgradeHandler.java"))
    if anvil_src == "":
        Fail(line + ": 缺少 event/AnvilUpgradeHandler.java")
    else:
        if "AnvilRepairEvent" not in anvil_src:
            Fail(line + ": AnvilUpgradeHandler 未接入 AnvilRepairEvent（取走升星结果才发券）")
        rep = re.search(r"public static void onAnvilRepair\(AnvilRepairEvent event\)\s*\{(?P<b>.*?)\n    \}",
                        anvil_src, re.S)
        if not rep:
            Fail(line + ": 未找到 onAnvilRepair(AnvilRepairEvent) 处理")
        else:
            rb2 = rep.group("b")
            r_need = [
                "event.getEntity() instanceof ServerPlayer",
                "event.getLeft()", "event.getRight()", "event.getOutput()",
                "right.is(ModItems.STAR_COIN.get())",
                "output.is(left.getItem())",
                "starCoinCost(", "ChipShopState.store(", "withRefreshGranted()",
            ]
            r_miss = [t for t in r_need if t not in rb2]
            if r_miss:
                Fail(line + ": 取走授予处理缺校验/授予锚点 " + " | ".join(r_miss))
            if not re.search(r"starLevel\(\) \+ 1", rb2):
                Fail(line + ": 取走授予未校验「输出星阶 = 输入星阶 + 1」（高星复制品可刷次数）")
        upd = re.search(r"public static void onAnvilUpdate\(AnvilUpdateEvent event\)\s*\{(?P<b>.*?)\n    \}",
                        anvil_src, re.S)
        if not upd:
            Fail(line + ": 未找到 onAnvilUpdate(AnvilUpdateEvent) 预览处理")
        elif "withRefreshGranted" in upd.group("b"):
            Fail(line + ": AnvilUpdateEvent 预览分支不得授予刷新次数（每次重算都会白送）")
        cost_body = re.search(r"private static int starCoinCost\(int \w+\)\s*\{(?P<b>.*?)\n    \}", anvil_src, re.S)
        if not cost_body:
            Fail(line + ": 缺星币成本共用 helper starCoinCost(int)（预览与授予不得各写一套数值）")
        else:
            for t in ("case 0 -> 15;", "case 1 -> 20;", "case 2 -> 25;"):
                if t not in cost_body.group("b"):
                    Fail(line + ": 星币成本 helper 缺 " + t)
        for t in ("-> 15;", "-> 20;", "-> 25;"):
            n_cost = anvil_src.count(t)
            if n_cost != 1:
                Fail(line + ": 星币成本「" + t + "」应恰 1 处（预览与授予共用 starCoinCost），实测 " + str(n_cost))

    # ---- G18g 死亡不清零 ----
    if line == "neoforge-1.21.1":
        ma_src = code_src(os.path.join(java_dir, "component/ModAttachments.java"))
        reg = re.search(r'ATTACHMENTS\.register\("chip_shop_state",\s*\(\)\s*->\s*AttachmentType\.builder'
                        r'\(\(\)\s*->\s*ChipShopState\.EMPTY\)(?P<b>.*?)\);', ma_src, re.S)
        if not reg:
            Fail(line + ": 未找到 chip_shop_state 附件注册")
        elif ".copyOnDeath()" not in reg.group("b"):
            Fail(line + ": chip_shop_state 附件未加 .copyOnDeath()（死亡会把刷新次数重置为初值）")
    else:
        ad_src = code_src(os.path.join(java_dir, "component/AstralData.java"))
        kept = re.search(r"String\[\] kept = \{(?P<b>.*?)\};", ad_src, re.S)
        if not kept:
            Fail(line + ": 未找到 AstralData 死亡保留白名单 kept[]")
        elif "ModAttachments.CHIP_SHOP_STATE.name()" not in kept.group("b"):
            Fail(line + ": 死亡保留白名单未含 ModAttachments.CHIP_SHOP_STATE.name()（死亡会重置刷新次数）")

    # ---- G18h 写入方白名单 ----
    def get_call_site_files(needle):
        hits = []
        for f in java_files(line_dir):
            n = code_src(f).count(needle)
            if n > 0:
                hits.append(os.path.basename(f) + "×" + str(n))
        return "|".join(sorted(hits))

    wr_sites = get_call_site_files("withRefreshes(")
    if wr_sites != "ChipShopMenu.java×1|ChipShopState.java×1":
        Fail(line + ": withRefreshes 调用点越界（扣次数只允许 ChipShopMenu#handleRefresh 一次），实测 " + wr_sites)
    bi_sites = get_call_site_files("withBlankInsert(")
    if bi_sites != "ChipShopBlock.java×1|ChipShopState.java×1":
        Fail(line + ": withBlankInsert 调用点越界（空白投入只允许 ChipShopBlock 一次），实测 " + bi_sites)
    gr_sites = get_call_site_files("withRefreshGranted(")
    if gr_sites != "AlSignItem.java×1|AnvilUpgradeHandler.java×1|ChipShopService.java×1|ChipShopState.java×1":
        Fail(line + ": withRefreshGranted 调用点越界（升星授予只允许 AnvilUpgradeHandler + 少主立牌 / 服务面），实测 " + gr_sites)
    w("CHIPUI_G18: 刷新来源 = 铁砧升星取走 1 次（唯一授予点）+ 空白筹码；ticker 已删；"
      "扣次数仅 ChipShopMenu / 空白仅 ChipShopBlock")

    # ---- G19 菜单构造不掷骰；重抽只允许 3 处 ----
    roll_in_menu = ms.count("ChipShopRoller.roll(")
    if roll_in_menu != 2:
        Fail(line + ": ChipShopMenu 内重抽调用应 2 处（购买 + 刷新），实测 " + str(roll_in_menu))
    roll_in_opener = opener_src.count("ChipShopRoller.roll(")
    if roll_in_opener != 1:
        Fail(line + ": ChipShopOpener 内进店补抽应 1 处，实测 " + str(roll_in_opener))
    roll_files = []
    for f in java_files(line_dir):
        if "ChipShopRoller.roll(" in code_src(f):
            roll_files.append(os.path.basename(f))
    extra_roll = [x for x in roll_files if x not in ("ChipShopMenu.java", "ChipShopOpener.java")]
    if extra_roll:
        Fail(line + ": 出现第三个重抽入口 " + " | ".join(extra_roll))
    i_ctor = ms.find("public ChipShopMenu(")
    i_buy_m = ms.find("public void handleBuy(")
    if i_ctor < 0 or i_buy_m <= i_ctor:
        Fail(line + ": 无法定位菜单构造器 / handleBuy（" + str(i_ctor) + "/" + str(i_buy_m) + "）")
    else:
        ctor = ms[i_ctor:i_buy_m]
        if "ChipShopRoller.roll(" in ctor or "withOffers(" in ctor:
            Fail(line + ": 菜单构造器不得掷骰/写候选（唯一补抽入口在 ChipShopOpener.ensureOffers）")

    # ---- G20 closeContainer 与扣款同分支 ----
    close_count = ms.count(".closeContainer();")
    if close_count != 1:
        Fail(line + ": 购买成功后自动关店应恰 1 处（.closeContainer();），实测 " + str(close_count))
    i_wd = ms.find("StarEngineEconomy.withdraw(")
    i_close = ms.find(".closeContainer();")
    if i_wd < 0:
        Fail(line + ": 购买未走 StarEngineEconomy.withdraw")
    elif i_close >= 0 and i_close < i_wd:
        Fail(line + ": closeContainer() 必须在扣款之后（扣款=" + str(i_wd) + " 关店=" + str(i_close) + "）")
    if "StarCoinBalanceSync.notifyChanged(" not in ms:
        Fail(line + ": 扣款后未同步余额（notifyChanged）")

    w("CHIPUI_LOGIC: G11..G16b / G17..G20（含 G13b 语义锚定 / G17b 三格同价 / G18b 上限钳制 / "
      "G18c..G18h 刷新来源）判据组新增失败 " + str(len(fail) - logic_fail_start) + " 条")

    # ---- G21 方块接入齐备 ----
    block_path = os.path.join(java_dir, "block/ChipShopBlock.java")
    if not isfile(block_path):
        Fail(line + ": 缺少 block/ChipShopBlock.java")
    elif "ChipShopOpener.open(player)" not in code_src(block_path):
        Fail(line + ": ChipShopBlock 未接 ChipShopOpener.open")
    mod_blocks_src = code_src(os.path.join(java_dir, "block/ModBlocks.java"))
    if 'register("chip_shop"' not in mod_blocks_src or "new ChipShopBlock(" not in mod_blocks_src:
        Fail(line + ": ModBlocks 未注册 chip_shop")
    mi_src = code_src(os.path.join(java_dir, "item/ModItems.java"))
    mi_reg = re.search(r'CHIP_SHOP\s*=\s*(?P<fn>[\w.]*register\w*)\s*\(\s*"chip_shop"\s*,(?P<b>.*?);',
                       mi_src, re.S)
    if not mi_reg:
        Fail(line + ": ModItems 未登记 chip_shop BlockItem")
    else:
        mi_fn = mi_reg.group("fn")
        mi_body = mi_reg.group("b")
        if not ("new BlockItem(" in mi_body or mi_fn.endswith("registerSimpleBlockItem")):
            Fail(line + ": ModItems 的 chip_shop 未按 BlockItem 登记（方块物品形态缺失）")
        elif "ModBlocks.CHIP_SHOP" not in mi_body:
            Fail(line + ": ModItems 的 chip_shop BlockItem 未引用 ModBlocks.CHIP_SHOP")
    if "ModItems.CHIP_SHOP.get()" not in code_src(os.path.join(java_dir, "init/ModCreativeTabs.java")):
        Fail(line + ": 创造栏未收录筹码商店")
    res_dir = os.path.join(line_dir, "src/main/resources")
    data_dir = os.path.join(res_dir, "data/astral_dice")
    if line == "forge-1.20.1":
        recipe_dir = os.path.join(data_dir, "recipes")
        loot_dir = os.path.join(data_dir, "loot_tables/blocks")
        recipe_result = '"item": "astral_dice:chip_shop"'
    else:
        recipe_dir = os.path.join(data_dir, "recipe")
        loot_dir = os.path.join(data_dir, "loot_table/blocks")
        recipe_result = '"id": "astral_dice:chip_shop"'
    bs_src = read_src(os.path.join(res_dir, "assets/astral_dice/blockstates/chip_shop.json"))
    for t in ('"facing=north"', '"facing=east"', '"facing=south"', '"facing=west"',
              "astral_dice:block/chip_shop"):
        if t not in bs_src:
            Fail(line + ": blockstates/chip_shop.json 缺 " + t)
    for t in ('"y": 90', '"y": 180', '"y": 270'):
        if t not in bs_src:
            Fail(line + ": blockstates/chip_shop.json 缺旋转 " + t)
    if "astral_dice:block/chip_shop" not in read_src(os.path.join(res_dir, "assets/astral_dice/models/item/chip_shop.json")):
        Fail(line + ": models/item/chip_shop.json 未继承方块模型")
    if "astral_dice:chip_shop" not in read_src(os.path.join(loot_dir, "chip_shop.json")):
        Fail(line + ": 掉落表缺 chip_shop")
    if recipe_result not in read_src(os.path.join(recipe_dir, "chip_shop.json")):
        Fail(line + ": 方块配方缺 chip_shop 产出")
    w("CHIPUI_G21: 方块接入（Block/ModBlocks/BlockItem/创造栏/blockstates/物品模型/掉落表/配方）已核")

    # ================= G21b..G27 =================
    # ---- G21b 方块朝向语义（按线分支）----
    if isfile(block_path):
        bsrc = code_src(block_path)
        bneed = [
            "extends HorizontalDirectionalBlock",
            "registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH))",
            "public BlockState getStateForPlacement(BlockPlaceContext context)",
            "context.getHorizontalDirection().getOpposite()",
            "createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)",
            "builder.add(FACING);",
            "ChipShopOpener.open(player)",
            "if (!level.isClientSide()) {",
            "return InteractionResult.sidedSuccess(level.isClientSide());",
        ]
        if line == "forge-1.20.1":
            bflavor = "六参 use / 无 codec 体系"
            bneed += ["public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,",
                      "InteractionHand hand, BlockHitResult hitResult)"]
            bforbid = ["simpleCodec(ChipShopBlock::new)", "MapCodec<ChipShopBlock>", "useItemOn(", "ItemInteractionResult"]
        else:
            bflavor = "codec 覆写 / useWithoutItem 覆写点"
            bneed += ["simpleCodec(ChipShopBlock::new)",
                      "protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, "
                      "Player player, BlockHitResult hitResult)"]
            bforbid = ["InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,"]
        bmiss = [x for x in bneed if x not in bsrc]
        if bmiss:
            Fail(line + ": ChipShopBlock 朝向/交互语义缺失 " + " | ".join(bmiss))
        for bf in bforbid:
            if bf in bsrc:
                Fail(line + ": ChipShopBlock 出现本线不应有的平台专有字面「" + bf + "」")
        w("CHIPUI_G21b: 方块朝向语义（按线：" + bflavor + "）缺失 " + str(len(bmiss))
          + " 项、越线字面检查 " + str(len(bforbid)) + " 项命中 0")

    # ---- G22 shaped 配方唯一性 ----
    if line == "forge-1.20.1":
        recipe_dirs = [recipe_dir]
    else:
        recipe_dirs = [recipe_dir, os.path.join(line_dir, "src/generated/resources/data/astral_dice/recipe")]
    sigs = {}
    shaped = 0
    for rd in recipe_dirs:
        if not isdir(rd):
            continue
        for rf in list_files(rd, "*.json"):
            jt = read_src(os.path.join(rd, rf))
            if "crafting_shaped" not in jt:
                continue
            try:
                j = json.loads(jt)
            except ValueError:
                Fail(line + ": 配方 JSON 解析失败 " + rf)
                continue
            shaped += 1
            used = []
            for row in as_list(j.get("pattern")):
                for ch in str(row):
                    u = ch
                    if u != " " and u not in used:
                        used.append(u)
            ks = []
            key_obj = j.get("key") or {}
            if isinstance(key_obj, dict):
                for pk in key_obj:
                    if pk not in used:
                        continue
                    pv = key_obj[pk]
                    ing = "x"
                    if isinstance(pv, dict):
                        if pv.get("item") is not None:
                            ing = "i:" + str(pv["item"])
                        elif pv.get("tag") is not None:
                            ing = "t:" + str(pv["tag"])
                    ks.append(pk + "=" + ing)
            sig = "/".join(str(x) for x in as_list(j.get("pattern"))) + "|" + ",".join(sorted(ks))
            sigs.setdefault(sig, []).append(rf)
    dup_groups = [(s, n) for s, n in sigs.items() if len(n) > 1]
    for _sig, names in dup_groups:
        Fail(line + ": shaped 配方撞车（图案与材料完全相同只会命中一条，另一个产出静默不可合成）："
             + " / ".join(names))
    chip_rc = read_src(os.path.join(recipe_dir, "chip_shop.json"))
    if chip_rc != "":
        if recipe_result not in chip_rc or '"count": 1' not in chip_rc:
            Fail(line + ": chip_shop 配方产出应为 astral_dice:chip_shop ×1")
        try:
            chip_json = json.loads(chip_rc)
        except ValueError:
            chip_json = None
        if chip_json is None:
            Fail(line + ": chip_shop 配方 JSON 解析失败")
        else:
            chip_pat = "/".join(str(x) for x in as_list(chip_json.get("pattern")))
            ck = chip_json.get("key") or {}
            kde = ck.get("K") if isinstance(ck, dict) else None
            chip_k = ""
            if isinstance(kde, dict) and kde.get("item") is not None:
                chip_k = str(kde["item"])
            if chip_pat != "GPG/HKH/HHH":
                Fail(line + ": chip_shop 配方图案应为 GPG/HKH/HHH（与 card_shop 的 BBB 中排区分），实测 " + chip_pat)
            if chip_k != "astral_dice:blank_chip":
                Fail(line + ": chip_shop 配方 key K 应为 astral_dice:blank_chip，实测「" + chip_k + "」")
    w("CHIPUI_G22: shaped 配方 " + str(shaped) + " 条，同图案同材料重复组 " + str(len(dup_groups)))

    # ---- G23 DataSlot 布局与拒绝码 ----
    ds_start = ms.find("addDataSlot(new DataSlot()")
    ds_end = ms.find("if (!player.level().isClientSide()) {")
    if ds_start < 0 or ds_end <= ds_start:
        Fail(line + ": 无法定位 DataSlot 区块")
    else:
        ds_region = ms[ds_start:ds_end]
        price_token = "quotedPrice()" if "quotedPrice()" in ms else "ChipShopState.get(player).price()"
        assert_order(ds_region,
                     ["clientPrice", price_token, "clientRefreshesLeft",
                      "ChipShopState.get(player).refreshesLeft()", "ChipShopCatalog.REFRESH_MAX",
                      "clientDenyReason", "clientTiers[index]"],
                     "DataSlot 顺序（单价/剩余刷新/总刷新/拒绝原因/品质码）", line)
    ds_count = ms.count("addDataSlot(new DataSlot()")
    if ds_count != 5:
        Fail(line + ": addDataSlot 站点应 5 处（4 显式 + 1 循环 ⇒ 共 7 槽），实测 " + str(ds_count))
    if "for (int i = 0; i < OFFER_SLOTS; i++) {" not in ms:
        Fail(line + ": 品质码 DataSlot 应由 OFFER_SLOTS 循环产生")
    for t in ("DENY_NONE = -1", "DENY_NO_COIN = 0", "DENY_NO_REFRESH = 1", "DENY_INVENTORY_FULL = 2"):
        if t not in ms:
            Fail(line + ": 拒绝码常量缺 " + t)
    if "reason == ChipShopMenu.DENY_NO_COIN || reason == ChipShopMenu.DENY_INVENTORY_FULL" not in src:
        Fail(line + ": 花费文本闪红未按 0/2 两码判定")
    if "reason == ChipShopMenu.DENY_NO_REFRESH" not in src:
        Fail(line + ": N/N 文本闪红未按 1 号码判定")

    # ---- G24 星等来源 ----
    if "Math.max(0, Math.min(3, CardShopShelf.currentStarLevel(player)))" not in roller_src:
        Fail(line + ": starLevel 未复用 CardShopShelf.currentStarLevel 并夹 0..3")
    if "ChipShopCatalog.QUALITY_PROB[star]" not in roller_src:
        Fail(line + ": 抽取未按 star 取概率表 QUALITY_PROB[star]")

    # ---- G25 无折扣路径 + 标题键只保留不绘制 ----
    for cf in [p for p in java_files(line_dir)
               if os.path.basename(p).startswith("ChipShop") and p.endswith(".java")]:
        cs2 = code_src(cf)
        for ft in ("discount", "promotion", "MEMBER_RECOMMENDATION"):
            if ft in cs2:
                if ft == "discount" and os.path.basename(cf) in ("ChipShopDiscounts.java", "ChipShopService.java"):
                    continue
                Fail(line + ": " + os.path.basename(cf) + " 出现折扣路径标识符 " + ft
                     + "（折扣仅允许 ChipShopDiscounts / ChipShopService 的少主立牌路径）")
    if '"gui.astral_dice.chip_shop"' in src:
        Fail(line + ": 屏幕绘制了标题键 gui.astral_dice.chip_shop（键保留但不绘制）")
    for t in ("gui.astral_dice.chip_shop.cost", "gui.astral_dice.chip_shop.refresh_counter",
              "gui.astral_dice.chip_shop.empty"):
        if t not in src:
            Fail(line + ": 屏幕未使用 " + t)

    # ---- G26 语义锚定 ----
    cw_body = get_body(cat_src, "public static double categoryWeight(")
    if cw_body == "":
        Fail(line + ": 无法定位 categoryWeight 方法体")
    else:
        assert_order(cw_body,
                     ["if (lastPick == null || candidate == null || candidate != lastPick) {",
                      "return 1.0;", "if (slot <= 0) {", "return CATEGORY_ALPHA;",
                      "return (CATEGORY_ALPHA - 1.0) * SLOT_DECAY + 1.0;"],
                     "categoryWeight 分支顺序", line)
    roll_body = get_body(roller_src, "public static List<ChipShopCatalog.Entry> roll(net.minecraft.world.entity.LivingEntity player, RandomSource random,")
    if roll_body == "":
        roll_body = get_body(roller_src, "public static List<ChipShopCatalog.Entry> roll(LivingEntity player, RandomSource random,")
    if roll_body == "":
        roll_body = get_body(roller_src, "public static List<ChipShopCatalog.Entry> roll(Player player, RandomSource random,")
    if roll_body == "":
        Fail(line + ": 无法定位抽取器 roll 方法体")
    else:
        assert_order(roll_body,
                     ["if (pool.isEmpty()) {", "break;", "if (tier <= 0) {", "break;", "if (chosen == null) {",
                      "break;", "picked.add(chosen);", "pool.remove(chosen);"],
                     "抽取器三处 break 与去重顺序", line)
        if "picked.set(" in roll_body:
            Fail(line + ": 抽取器出现 picked.set(（按槽位占位会破坏去重/缺口顺序）")
    prob_body = get_body(cat_src, "QUALITY_PROB = {")
    if prob_body == "":
        Fail(line + ": 无法定位 QUALITY_PROB 初始化块")
    else:
        assert_order(prob_body,
                     ["{0.60, 0.37, 0.03}", "{0.60, 0.37, 0.03}", "{0.40, 0.45, 0.15}", "{0.25, 0.50, 0.25}"],
                     "概率表行序（0★/1★/2★/3★）", line)
    ci_body = get_body(src, "private int chipIndexAt(int mouseX, int mouseY)")
    if ci_body == "":
        Fail(line + ": 无法定位 chipIndexAt 方法体")
    else:
        assert_order(ci_body,
                     ["isHoveringStrip(mouseX, mouseY, CELL_X[cell], CELL_Y, CELL_W, CELL_H)",
                      "isHoveringStrip(mouseX, mouseY, LABEL_X[cell], LABEL_Y, LABEL_W, LABEL_H)",
                      "return chip;"],
                     "命中面（筹码格 ∪ 标签框）", line)
    hb_body = get_body(ms, "public void handleBuy(")
    hr_body = get_body(ms, "public void handleRefresh(")
    if hb_body == "" or hr_body == "":
        Fail(line + ": 无法定位 handleBuy / handleRefresh 方法体")
    else:
        if hb_body.count("ChipShopRoller.roll(") != 1:
            Fail(line + ": handleBuy 内重抽应恰 1 处")
        if hr_body.count("ChipShopRoller.roll(") != 1:
            Fail(line + ": handleRefresh 内重抽应恰 1 处")
        if hb_body.count(".closeContainer();") != 1:
            Fail(line + ": .closeContainer(); 必须落在 handleBuy 方法体内且恰 1 处")
        else:
            iw = hb_body.find("StarEngineEconomy.withdraw(")
            ic = hb_body.find(".closeContainer();")
            if iw < 0 or ic < iw:
                Fail(line + ": handleBuy 内关店必须在扣款之后（扣款=" + str(iw) + " 关店=" + str(ic) + "）")
    if "Math.min(PRICE_MAX, PRICE_INITIAL + PRICE_STEP * count)" not in cat_src:
        Fail(line + ": 价格公式未按 min(PRICE_MAX, PRICE_INITIAL + PRICE_STEP * count) 逐字实现"
             "（含右括号，须落在 ChipShopCatalog.priceOf）")
    w("CHIPUI_G23..G27: DataSlot/拒绝码、星等来源、无折扣无标题、语义锚定"
      "（categoryWeight/roll break/概率表行序/命中面/handleBuy 关店/价格公式）已核")
    w("CHIPUI_NEXT: bash scripts/test/win/mt.sh --version "
      + MT_VERSIONS[key] + " --phase build --timeout 300")

# ---- 反假绿守卫：一条线都没扫到 ⇒ 无法判定（exit 2），绝不以 OK 收场 ----
if scanned == 0:
    Fail("未核到任何一条线（仓库根可能不对：" + ROOT + "）；无法判定")

w("CHIPUI_TOTAL_FAIL: " + str(len(fail)))
if not fail:
    w("CHIPUI_RESULT: OK")
    emit()
    sys.exit(0)
if scanned == 0:
    w("CHIPUI_RESULT: FAIL")
    emit()
    sys.exit(2)
w("CHIPUI_RESULT: FAIL")
emit()
sys.exit(1)
