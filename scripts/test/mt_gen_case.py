#!/usr/bin/env python3
"""mt_gen_case —— 用例 JSON / MD 脚手架生成器（python 复原版，等价 scripts/test/mt_gen_case.ps1）。

只生成文件，不执行、不起游戏、不改环境脚本。三种互斥模式：
  --new <注册id>   按新内容生成（spec-driven：类别规则 + 骨架步骤 + vision 断言）
  --feature <功能名> 按既有功能回归生成（从 CHANGELOG_ZH.md 找命中条目）
  --spec "<描述>"   按用户描述生成（可机械判定的部分留 TODO，其余降级为 vision）

机器行：MT_GEN: OK — <json> + <md> / MT_GEN_NEXT: 执行 …
参数错误：MT_ERROR: … 退出 2；生成的条目过不了 lib/mt_case.py 校验：MT_GEN: ERROR — … 退出 2。

与 ps1 的差异（有意，仅两处，均为「资产已 bash 化」的必然结果）：
  * MT_GEN_NEXT 里的复跑命令由 pwsh -File scripts/test/mt.ps1 … 改为 bash scripts/test/mt.sh …
    （.ps1 已退役；机器行前缀与语义不变，只是宿主换成本仓现役的 bash 测试台）。
  * 增加 -h/--help（ps1 没有该开关；本仓约定每个 python 入口 --help 退出 0）。

本文件不调用任何 .ps1 / .psm1，也不 Import-Module。
"""

import hashlib
import os
import sys

TEST_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(TEST_DIR, "lib"))

import mt_case as mcase  # noqa: E402
import mt_compat as mc  # noqa: E402

EXIT_ERROR = mc.MT_EXIT_ERROR
ROOT = mc.ROOT
CASES_DIR = os.path.join(TEST_DIR, "cases")
CHANGELOG = os.path.join(ROOT, "CHANGELOG_ZH.md")
AGENTS = os.path.join(ROOT, "AGENTS.md")

CATEGORY_RULES = [
    ("chip", {
        "keywords": ("chip", "筹码"),
        "asserts": [{"type": "kubejs"}, {"type": "crash"}],
        "notes": "筹码需核对：配方两档互不越界、tooltip 染色（非时间数值黄/时间蓝）、创造栏顺序、手册条目与 lang 双版本同步",
    }),
    ("sign", {
        "keywords": ("sign", "立牌"),
        "asserts": [{"type": "log", "pattern": "TS_EQUIP_OK"}, {"type": "crash"}],
        "notes": "立牌需核对：J 进入选择、施加效果与冷却、旧机制无残留、玩家目标规则",
    }),
    ("dice", {
        "keywords": ("dice", "骰子"),
        "asserts": [{"type": "kubejs"}, {"type": "crash"}],
        "notes": "骰子需核对：星级决定卡牌格数（0★4/1★6/2★8/3★12，攻防各半）",
    }),
    ("effect_card", {
        "keywords": ("effect_card", "效果牌"),
        "asserts": [{"type": "crash"}, {"type": "kubejs"}],
        "notes": "效果牌需核对：冷却统一由 EffectCardPeriod 管理、命名规范、手册条目",
    }),
    ("recipe", {
        "keywords": ("recipe", "配方"),
        "asserts": [{"type": "crash"}],
        "notes": "配方需核对：配方 id 与物品 id 可能不同、手册 crafting 页引用存在",
    }),
]

OTHER_RULE = {
    "keywords": (),
    "asserts": [{"type": "crash"}],
    "notes": "未匹配到已知类别，按通用回归处理",
}


def usage():
    return chr(10).join([
        "用法: python scripts/test/mt_gen_case.py --version <版本> (--new <注册id> | --feature <功能名> | --spec <描述>) [--dry-run]",
        "",
        "用例脚手架生成器（等价 scripts/test/mt_gen_case.ps1）：只写 cases/<case_id>.json 与 .md，",
        "不执行用例、不启动游戏、不改环境脚本。",
        "",
        "选项:",
        "  --version <v>    必填；" + " ".join(mc.VERSIONS),
        "  --new <注册id>    生成新内容用例（类别规则 + 骨架步骤 + vision 断言）",
        "  --feature <功能名> 生成既有功能回归用例（从 CHANGELOG_ZH.md 找命中条目）",
        "  --spec <描述>     按用户描述生成（无法机械判定的部分降级为 vision）",
        "  --dry-run        只把生成的 JSON 打到 stdout，不落盘",
        "  -h, --help       显示本帮助",
        "",
        "退出码: 0 = 生成成功 | 2 = 参数错误 / 生成的条目未通过校验",
        "",
        "能力缺失: 本生成器不需要游戏与网络；--feature 找不到 CHANGELOG_ZH.md 时备注里直说「未命中」，",
        "          不静默伪造命中条目。",
    ])


def classify(target):
    low = target.lower()
    for name, rule in CATEGORY_RULES:
        for keyword in rule["keywords"]:
            if keyword in low:
                return name, rule
    return "other", OTHER_RULE


def changelog_hits(feature, limit=5):
    if not os.path.isfile(CHANGELOG):
        return []
    key = feature.split(".")[-1]
    with open(CHANGELOG, "rb") as fh:
        text = fh.read().decode("utf-8", "replace")
    lines = [ln.strip() for ln in mc.split_crlf(text) if key and key in ln]
    return lines[-limit:]


def build_header(body):
    """Get-MtGenHeader：ps1 里是死代码（定义但从未调用），为 1:1 对齐保留。"""
    return [
        "// 由 mt_gen_case.py 生成；可手工调整后复跑。",
        "// 断言优先选择可机械判定的类型（log/absent/crash/kubejs/mixin）；",
        "// 视觉类一律降级为 vision（判定交视觉模型），不得用像素硬编码判定。",
    ] + list(body)


def gen_new(version, target):
    category, rule = classify(target)
    return {
        "case_id": "NEW-" + target.upper().replace("_", "-"),
        "title": "新增内容 " + target + " 的可用性与规范一致性（" + category + "）",
        "version": version,
        "target": {"type": "new-content", "ref": target, "category": category},
        "fixtures": {"kubejs": []},
        "setup": [
            {"op": "inject_command", "command": "/clear Dev"},
            {"op": "wait", "ms": 300},
        ],
        "steps": [
            {"op": "inject_command", "command": "/give Dev astral_dice:" + target},
            {"op": "wait", "ms": 600},
            {"op": "screenshot", "tag": target + "_given"},
        ],
        "asserts": list(rule["asserts"]) + [
            {"type": "vision", "image": target + "_given.png",
             "question": "物品栏中是否出现了 " + target + "，且图标/名称渲染正常？"},
        ],
        "evidence": [target + "_given.png"],
        "notes": [rule["notes"], "生成器只给出骨架；请按 AGENTS 对应章节补足行为断言。"],
        "on_fail": "keep_game_running",
    }


def gen_feature(version, feature):
    hits = changelog_hits(feature)
    notes = ["CHANGELOG 命中条目："] + hits if hits else ["CHANGELOG 未命中，请手工补充步骤。"]
    return {
        "case_id": "REG-" + feature.upper().replace(".", "-").replace("_", "-"),
        "title": "既有功能回归：" + feature,
        "version": version,
        "target": {"type": "existing-feature", "ref": feature},
        "fixtures": {"kubejs": []},
        "setup": [
            {"op": "inject_command", "command": "/clear Dev"},
            {"op": "wait", "ms": 300},
        ],
        "steps": [
            {"op": "kubejs_reload"},
            {"op": "note", "text": "按 CHANGELOG 条目复现该功能的最小操作路径"},
        ],
        "asserts": [{"type": "crash"}, {"type": "kubejs"}],
        "evidence": [],
        "notes": notes,
        "on_fail": "keep_game_running",
    }


def ascii_slug(text, limit=32):
    out = []
    for ch in text:
        if ord(ch) < 128 and ch.isalnum():
            out.append(ch.upper())
        elif out and out[-1] != "-":
            out.append("-")
    return "".join(out).strip("-")[:limit].strip("-") or "CASE"


def gen_spec(version, spec_text):
    digest = hashlib.sha1(spec_text.encode("utf-8")).hexdigest()[:6]
    return {
        "case_id": "SPEC-" + ascii_slug(spec_text, 24) + "-" + digest,
        "title": "用户指定：" + spec_text[:60],
        "version": version,
        "target": {"type": "user-specified", "ref": spec_text},
        "fixtures": {"kubejs": []},
        "setup": [{"op": "note", "text": "按用户描述拆分的最小前置"}],
        "steps": [{"op": "note", "text": "在此填入可执行步骤（封闭原语）"}],
        "asserts": [
            {"type": "crash"},
            {"type": "vision", "question": "截图是否满足以下描述：" + spec_text},
        ],
        "evidence": [],
        "notes": [
            "描述驱动生成：可机械判定的部分请改为 log/absent/kubejs 断言；",
            "无法机械判定的保留为 vision，并在报告中标注需人工复核。",
        ],
        "on_fail": "keep_game_running",
    }


def write_case(case, dry_run):
    case_id = case["case_id"]
    json_path = os.path.join(CASES_DIR, case_id + ".json")
    md_path = os.path.join(CASES_DIR, case_id + ".md")
    errs = mcase.validate_case(case, json_path)
    if errs:
        mc.write_mt_err_line("MT_GEN: ERROR — 生成的条目未通过校验：")
        for item in errs:
            mc.write_mt_err_line("    " + item)
        return EXIT_ERROR

    if dry_run:
        mc.write_mt_line(mc.json_dump(case))
        return mc.MT_EXIT_PASS

    if not os.path.isdir(CASES_DIR):
        try:
            os.makedirs(CASES_DIR, exist_ok=True)
        except OSError as exc:
            mc.write_mt_err_line("MT_GEN: ERROR — 无法创建 " + CASES_DIR + "：" + str(exc))
            return EXIT_ERROR

    body = mc.json_dump(case)
    try:
        with open(json_path, "w", encoding="utf-8", newline="") as fh:
            fh.write(body)
    except OSError as exc:
        mc.write_mt_err_line("MT_GEN: ERROR — 写 json 失败：" + str(exc))
        return EXIT_ERROR

    lines = [
        "# " + case_id + " — " + str(case.get("title", "")),
        "",
        "- 版本: " + str(case.get("version", "")),
        "- 目标类型: " + str((case.get("target") or {}).get("type", "")) + "（" + str((case.get("target") or {}).get("ref", "")) + "）",
        "- 断言数: " + str(len(case.get("asserts") or [])),
        "",
        "## 步骤",
        "",
    ]
    for index, step in enumerate(case.get("steps") or [], 1):
        desc = ""
        for key in ("command", "key", "tag", "text"):
            if step.get(key):
                desc = str(step[key])
                break
        lines.append(str(index) + ". " + chr(96) + str(step.get("op", "")) + chr(96) + " " + desc)
    lines += ["", "## 断言", ""]
    for item in case.get("asserts") or []:
        detail = item.get("pattern") or item.get("question") or ""
        lines.append("- " + chr(96) + str(item.get("type", "")) + chr(96) + " " + str(detail))
    notes = [n for n in (case.get("notes") or []) if n]
    if notes:
        lines += ["", "## 备注", ""]
        lines += ["- " + str(n) for n in notes]
    try:
        with open(md_path, "w", encoding="utf-8", newline="") as fh:
            fh.write(chr(10).join(lines) + chr(10))
    except OSError as exc:
        mc.write_mt_err_line("MT_GEN: ERROR — 写 md 失败：" + str(exc))
        return EXIT_ERROR

    mc.write_mt_line("MT_GEN: OK — " + case_id + ".json + " + case_id + ".md")
    mc.write_mt_line("MT_GEN_NEXT: 执行 bash scripts/test/mt.sh --version " + str(case.get("version", ""))
                     + " --case cases/" + case_id + ".json")
    return mc.MT_EXIT_PASS


def main(argv):
    mc.init_console()
    version = ""
    new_id = ""
    feature = ""
    spec_text = ""
    dry_run = False
    index = 0
    while index < len(argv):
        token = argv[index]
        key = token.lstrip("-").lower()
        if token.startswith("-") and key in ("h", "help"):
            mc.write_mt_line(usage())
            return mc.MT_EXIT_PASS
        if token.startswith("-") and key in ("version", "new", "feature", "spec"):
            index += 1
            if index >= len(argv):
                mc.write_mt_error_line("缺少 --" + key + " 的值")
                return EXIT_ERROR
            value = argv[index]
            if key == "version":
                version = value
            elif key == "new":
                new_id = value
            elif key == "feature":
                feature = value
            else:
                spec_text = value
            index += 1
            continue
        if token.startswith("-") and key == "dry-run":
            dry_run = True
            index += 1
            continue
        mc.write_mt_error_line("未知参数 " + token)
        return EXIT_ERROR

    if not version:
        mc.write_mt_error_line("必须指定 --version")
        return EXIT_ERROR
    if not mc.assert_version(version):
        return EXIT_ERROR
    given = [name for name, value in (("--new", new_id), ("--feature", feature), ("--spec", spec_text)) if value]
    if len(given) != 1:
        mc.write_mt_error_line("必须且只能指定 --new / --feature / --spec 之一")
        return EXIT_ERROR

    if new_id:
        case = gen_new(version, new_id)
    elif feature:
        case = gen_feature(version, feature)
    else:
        case = gen_spec(version, spec_text)
    return write_case(case, dry_run)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
