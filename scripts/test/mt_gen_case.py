#!/usr/bin/env python3
"""mt_gen_case — 测试条目生成器（子技能 mod-test-case 的可执行部分）。

只为「新增内容 / 既有功能回归 / 用户指定内容」产出条目文件，**不执行、不启动游戏、
不修改环境脚本**。产出物是插在环境创建与清理之间的中间层，物理上无法触达两端。

三态输入与策略:
  --new     <注册id>   规范驱动：按内容类别套用 AGENTS 对应「必须遵守」条款 → 断言
  --feature <功能名>   回归驱动：从 CHANGELOG_ZH.md 找该功能条目 → 抽可复现步骤
  --spec    "<描述>"   描述驱动：拆成可执行步骤 + 可判定断言；不可判定的转为 vision/note

用法:
  mt_gen_case.py --version 1.21.1 --new ember_chip
  mt_gen_case.py --version 1.20.1 --feature dice.star_level
  mt_gen_case.py --version 1.21.1 --spec "验证充能类筹码顺序为永动机在电磁炮之前"
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

TEST_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(TEST_DIR))
sys.path.insert(0, str(TEST_DIR / "lib"))
from mt_case import validate  # noqa: E402
from mt_paths import ROOT, VERSIONS  # noqa: E402

CASES_DIR = TEST_DIR / "cases"
CHANGELOG = ROOT / "CHANGELOG_ZH.md"
AGENTS = ROOT / "AGENTS.md"

# 内容类别 → 校验切入点（规范驱动时用来挑断言）
CATEGORY_RULES: dict[str, dict] = {
    "chip": {
        "keywords": ("chip", "筹码"),
        "asserts": [
            {"type": "kubejs"},
            {"type": "crash"},
        ],
        "notes": "筹码需核对：配方两档互不越界、tooltip 染色（非时间数值黄/时间蓝）、"
                 "创造栏顺序、手册条目与 lang 双版本同步",
    },
    "sign": {
        "keywords": ("sign", "立牌"),
        "asserts": [{"type": "log", "pattern": r"TS_EQUIP_OK"}, {"type": "crash"}],
        "notes": "立牌需核对：J 进入选择、施加效果与冷却、旧机制无残留、玩家目标规则",
    },
    "dice": {
        "keywords": ("dice", "骰子"),
        "asserts": [{"type": "kubejs"}, {"type": "crash"}],
        "notes": "骰子需核对：星级决定卡牌格数（0★4/1★6/2★8/3★12，攻防各半）",
    },
    "effect_card": {
        "keywords": ("effect_card", "效果牌"),
        "asserts": [{"type": "crash"}, {"type": "kubejs"}],
        "notes": "效果牌需核对：冷却统一由 EffectCardPeriod 管理、命名规范、手册条目",
    },
    "recipe": {
        "keywords": ("recipe", "配方"),
        "asserts": [{"type": "crash"}],
        "notes": "配方需核对：配方 id 与物品 id 可能不同、手册 crafting 页引用存在",
    },
}


def classify(target: str) -> tuple[str, dict]:
    low = target.lower()
    for name, spec in CATEGORY_RULES.items():
        if any(k in low for k in spec["keywords"]):
            return name, spec
    return "other", {"keywords": (), "asserts": [{"type": "crash"}],
                     "notes": "未匹配到已知类别，按通用回归处理"}


def changelog_hits(feature: str, limit: int = 5) -> list[str]:
    if not CHANGELOG.is_file():
        return []
    key = feature.split(".")[-1]
    hits = [ln.strip() for ln in CHANGELOG.read_text(encoding="utf-8", errors="ignore").splitlines()
            if key and key in ln]
    return hits[-limit:]


def build_header(body: list[str]) -> list[str]:
    return [
        "// 由 mt_gen_case.py 生成；可手工调整后复跑。",
        "// 断言优先选择可机械判定的类型（log/absent/crash/kubejs/mixin）；",
        "// 视觉类一律降级为 vision（判定交视觉模型），不得用像素硬编码判定。",
        *body,
    ]


def gen_new(version: str, target: str) -> dict:
    cat, spec = classify(target)
    asserts = list(spec.get("asserts", []))
    return {
        "case_id": f"NEW-{target.upper().replace('_', '-')}",
        "title": f"新增内容 {target} 的可用性与规范一致性（{cat}）",
        "version": version,
        "target": {"type": "new-content", "ref": target, "category": cat},
        "fixtures": {"kubejs": []},
        "setup": [
            {"op": "inject_command", "command": "/clear Dev"},
            {"op": "wait", "ms": 300},
        ],
        "steps": [
            {"op": "inject_command", "command": f"/give Dev astral_dice:{target}"},
            {"op": "wait", "ms": 600},
            {"op": "screenshot", "tag": f"{target}_given"},
        ],
        "asserts": [
            *asserts,
            {"type": "vision", "image": f"{target}_given.png",
             "question": f"物品栏中是否出现了 {target}，且图标/名称渲染正常？"},
        ],
        "evidence": [f"{target}_given.png"],
        "notes": [spec.get("notes", ""),
                  "生成器只给出骨架；请按 AGENTS 对应章节补足行为断言。"],
        "on_fail": "keep_game_running",
    }


def gen_feature(version: str, feature: str) -> dict:
    hits = changelog_hits(feature)
    return {
        "case_id": f"REG-{feature.upper().replace('.', '-').replace('_', '-')}",
        "title": f"既有功能回归：{feature}",
        "version": version,
        "target": {"type": "existing-feature", "ref": feature},
        "fixtures": {"kubejs": []},
        "setup": [{"op": "inject_command", "command": "/clear Dev"}, {"op": "wait", "ms": 300}],
        "steps": [
            {"op": "kubejs_reload"},
            {"op": "note", "text": "按 CHANGELOG 条目复现该功能的最小操作路径"},
        ],
        "asserts": [
            {"type": "crash"},
            {"type": "kubejs"},
        ],
        "evidence": [],
        "notes": ["CHANGELOG 命中条目：", *hits] or ["CHANGELOG 未命中，请手工补充步骤。"],
        "on_fail": "keep_game_running",
    }


def _ascii_slug(text: str, limit: int = 32) -> str:
    """把任意描述压成 ASCII 文件名安全的 slug（中文等非 ASCII 一律丢弃）。"""
    out = []
    for ch in text:
        if ch.isascii() and ch.isalnum():
            out.append(ch.upper())
        elif out and out[-1] != "-":
            out.append("-")
    slug = "".join(out).strip("-")
    return slug[:limit].strip("-") or "CASE"


def gen_spec(version: str, spec_text: str) -> dict:
    import hashlib
    digest = hashlib.sha1(spec_text.encode("utf-8")).hexdigest()[:6]
    return {
        "case_id": f"SPEC-{_ascii_slug(spec_text, 24)}-{digest}",
        "title": f"用户指定：{spec_text[:60]}",
        "version": version,
        "target": {"type": "user-specified", "ref": spec_text},
        "fixtures": {"kubejs": []},
        "setup": [{"op": "note", "text": "按用户描述拆分的最小前置"}],
        "steps": [{"op": "note", "text": "在此填入可执行步骤（封闭原语）"}],
        "asserts": [
            {"type": "crash"},
            {"type": "vision", "question": f"截图是否满足以下描述：{spec_text}"},
        ],
        "evidence": [],
        "notes": ["描述驱动生成：可机械判定的部分请改为 log/absent/kubejs 断言；",
                  "无法机械判定的保留为 vision，并在报告中标注需人工复核。"],
        "on_fail": "keep_game_running",
    }


def write_case(case: dict, dry_run: bool) -> int:
    errs = validate(case)
    if errs:
        print(f"MT_GEN: ERROR — 生成的条目未通过校验：", file=sys.stderr)
        for e in errs:
            print(f"    {e}", file=sys.stderr)
        return 2

    CASES_DIR.mkdir(parents=True, exist_ok=True)
    json_path = CASES_DIR / f"{case['case_id']}.json"
    md_path = CASES_DIR / f"{case['case_id']}.md"

    if dry_run:
        print(json.dumps(case, ensure_ascii=False, indent=2))
        return 0

    json_path.write_text(json.dumps(case, ensure_ascii=False, indent=2), encoding="utf-8")
    md = [
        f"# {case['case_id']} — {case['title']}",
        "",
        f"- 版本: {case['version']}",
        f"- 目标类型: {case['target']['type']}（{case['target']['ref']}）",
        f"- 断言数: {len(case['asserts'])}",
        "",
        "## 步骤",
        "",
    ]
    for i, s in enumerate(case["steps"], 1):
        desc = s.get("command") or s.get("key") or s.get("tag") or s.get("text") or ""
        md.append(f"{i}. `{s['op']}` {desc}")
    md += ["", "## 断言", ""]
    for a in case["asserts"]:
        md.append(f"- `{a['type']}` {a.get('pattern') or a.get('question') or ''}")
    if case.get("notes"):
        md += ["", "## 备注", ""] + [f"- {n}" for n in case["notes"] if n]
    md_path.write_text("\n".join(md) + "\n", encoding="utf-8")

    print(f"MT_GEN: OK — {json_path.name} + {md_path.name}")
    print(f"MT_GEN_NEXT: 执行 bash scripts/test/mt.sh --version {case['version']} "
          f"--case cases/{json_path.name}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="mt 条目生成器（子技能）")
    ap.add_argument("--version", required=True, choices=list(VERSIONS))
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--new", metavar="ID", help="新增内容的注册 id")
    g.add_argument("--feature", metavar="NAME", help="既有功能名（回归）")
    g.add_argument("--spec", metavar="TEXT", help="用户自然语言描述")
    ap.add_argument("--dry-run", action="store_true", help="只打印，不落盘")
    args = ap.parse_args()

    if args.new:
        case = gen_new(args.version, args.new)
    elif args.feature:
        case = gen_feature(args.version, args.feature)
    else:
        case = gen_spec(args.version, args.spec)
    return write_case(case, args.dry_run)


if __name__ == "__main__":
    raise SystemExit(main())
