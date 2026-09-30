#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Mixin 注入参数审计（离线门禁；exit 0 = 通过，1 = 有硬违规）。

=====================================================================
为什么需要它
=====================================================================
2026-09-30 的线上事故：`forge-1.20.1` 的 `EffectRenderingInventoryScreenMixin` 两处 `@Redirect`
写了 `require = 2`，而目标方法 `renderEffects` 内这两个调用**各只有 1 处** ⇒ Mixin 的注入点
计数校验 `(1/2)` 硬失败 ⇒ **该类首次加载（打开物品栏）即 InjectionError、客户端直接崩**。
当时是**靠 1.20.1 的 datagen 偶然抓到**的（datagen 会加载客户端类并应用 client 组 mixin）——
**datagen 不是稳定闸门**（它只在该类恰好被加载时才暴露），故补本离线门禁。

（事故复盘见 `.workbuddy/memory/2026-10-01.md`；修复提交 `cba8e956`。）

=====================================================================
判据
=====================================================================
硬失败（exit 1）：
- **H1** `require = N` 且 **N >= 2** —— `require` 是「**最少**匹配数」而非「容忍失败的次数」，
  写大于实际注入点数即 fatal。本仓三线 `astral_dice.mixins.json` 都设了
  `injectors.defaultRequire = 1` ⇒ **不写**就已经有「上游签名一变就报错」的 fail-loud 效果。
  确需「恰好 N 个」时应改用 `expect = N`（见 H2）。
- **H2** 写了 `expect = N` 却没有配套的 `require = 1`，或 `N < 1` —— `expect` 是「**恰好** N 个」，
  必须在 `require` 已保证匹配的前提下才有意义。
- **H3** 三线 `astral_dice.mixins.json` 缺 `injectors.defaultRequire` 或值 < 1
  —— 少了它，「不写 require」就不再 fail-loud。

提示（不影响 exit）：
- **W1** `require = 0`：有意静默失败。请确认该 mixin 确实允许「目标不存在」（如面向可选联动模组）。
- **W2** `@Redirect` 的 `@At` 是 `INVOKE` / `FIELD` 时，用 patched 原版**源码**粗算目标方法内
  该调用的出现次数，与 `require`/`expect` 对照。**仅作参考** —— 源码文本计数 ≠ 字节码指令计数
  （lambda / 内部类 / 桥接方法都会偏），故**不作硬判据**，只打印供人工核对。

用法：
    python tools/audit_mixin_injection.py
    python tools/audit_mixin_injection.py --hint-sources      # 额外打印 W2 的源码粗算
"""

import argparse
import json
import pathlib
import re
import sys
import zipfile

REPO = pathlib.Path(__file__).resolve().parent.parent
LINES = ["neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2"]
MIXIN_JSON = "src/main/resources/astral_dice.mixins.json"
SRC_ROOT = "src/main/java"

# 注解块：从 @Redirect/@Inject/@ModifyXxx( 起，括号配平到结尾
ANN_RE = re.compile(r"@(Redirect|Inject|ModifyVariable|ModifyArg|ModifyConstant|ModifyReturnValue)\s*\(")
REQ_RE = re.compile(r"\brequire\s*=\s*(-?\d+)")
EXP_RE = re.compile(r"\bexpect\s*=\s*(-?\d+)")
METHOD_RE = re.compile(r'\bmethod\s*=\s*"([^"]*)"')
AT_TARGET_RE = re.compile(r'target\s*=\s*"L([^;]+);([^"(]+)(\([^)]*\)[^"]*)?"')
MIXIN_TARGET_RE = re.compile(r"@Mixin\s*\(\s*(?:value\s*=\s*)?\{?\s*([A-Za-z0-9_.$]+)\.class")


def iter_mixin_sources(line):
    """产出该线所有含 mixin 注入注解的源文件（不限 `/mixin/` 目录 —— 注解可能被放在别处）。"""
    root = REPO / line / SRC_ROOT
    for f in sorted(root.rglob("*.java")):
        txt = f.read_text(encoding="utf-8", errors="replace")
        if ANN_RE.search(txt):
            yield f


def iter_mixin_annots(path):
    """逐个产出 (注解名, 注解体文本, 行号)。"""
    text = path.read_text(encoding="utf-8", errors="replace")
    for m in ANN_RE.finditer(text):
        i = m.end() - 1  # 指向 '('
        depth = 0
        for j in range(i, len(text)):
            c = text[j]
            if c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    yield m.group(1), text[i:j + 1], text.count("\n", 0, j) + 1
                    break


def audit_line(line):
    hard, warn = [], []

    p = REPO / line / MIXIN_JSON
    if not p.is_file():
        hard.append(f"{line}: 缺 {MIXIN_JSON}")
    else:
        cfg = json.loads(p.read_text(encoding="utf-8"))
        dr = (cfg.get("injectors") or {}).get("defaultRequire")
        # H3：无 defaultRequire ⇒ 「不写 require」不再 fail-loud
        if not isinstance(dr, int) or dr < 1:
            hard.append(f"{line}: {MIXIN_JSON} 的 injectors.defaultRequire 缺失或 < 1（当前 {dr!r}）")

    src = REPO / line / SRC_ROOT
    n_annot = 0
    for f in iter_mixin_sources(line):
        for name, body, ln in iter_mixin_annots(f):
            n_annot += 1
            req = REQ_RE.search(body)
            exp = EXP_RE.search(body)
            rel = f.relative_to(REPO)
            tag = f"{rel}:{ln} @{name}"

            # H1：require >= 2
            if req and int(req.group(1)) >= 2:
                hard.append(f"{tag} 的 `require = {req.group(1)}` —— require 是「最少匹配数」，"
                            f"N>=2 会在注入点不足时 fatal（本仓默认 defaultRequire=1，不写即可）")
            # H2：expect 必须配 require=1，且 >= 1
            if exp:
                e = int(exp.group(1))
                if e < 1:
                    hard.append(f"{tag} 的 `expect = {e}` < 1（expect 是「恰好 N 个」）")
                if not req or int(req.group(1)) != 1:
                    hard.append(f"{tag} 用了 `expect = {e}` 但未配 `require = 1`"
                                f"（当前 require={req.group(1) if req else '未写'}）")
            # W1：require = 0
            if req and int(req.group(1)) == 0:
                m = METHOD_RE.search(body)
                warn.append(f"{tag} 的 `require = 0`（目标 {m.group(1) if m else '?'}）"
                            f" —— 有意静默失败，请确认确实允许目标不存在")

    return hard, warn, n_annot


def source_hint(line):
    """W2：用 patched 原版**源码**粗算 @Redirect 目标方法内的调用次数（仅参考）。

    ⚠️ 关键：`@Redirect(method = "X")` 的 `X` 是**被 mixin 的那个类**里的方法，
    不是 `@At.target` 成员所属类的方法 ⇒ 必须先从 mixin 类的 `@Mixin(SomeClass.class)` 取到目标类。
    """
    art = REPO / line / "build/moddev/artifacts"
    jars = sorted(art.glob("*sources.jar")) if art.is_dir() else []
    if not jars:
        return [f"  [{line}] 无 sources.jar（未跑过 build？）⇒ 跳过"], 0
    z = zipfile.ZipFile(jars[0])
    names = set(z.namelist())
    out, n = [], 0
    for f in iter_mixin_sources(line):
        text = f.read_text(encoding="utf-8", errors="replace")
        mt = MIXIN_TARGET_RE.search(text)          # 本 mixin 应用到的原版类
        target_cls = mt.group(1) if mt else None
        for name, body, ln in iter_mixin_annots(f):
            if name != "Redirect":
                continue
            mm = METHOD_RE.search(body)
            tm = AT_TARGET_RE.search(body)
            if not (mm and tm):
                continue
            n += 1
            req = REQ_RE.search(body)
            exp = EXP_RE.search(body)
            dec = f"require={req.group(1) if req else '未写'}" + (f", expect={exp.group(1)}" if exp else "")
            mname = mm.group(1).split("(")[0]
            member = tm.group(2)
            if not target_cls:
                out.append(f"  [{line}] {f.name}: 未解析到 @Mixin 目标类 ⇒ 跳过")
                continue
            # @Mixin 里通常是**简单类名**（包靠 import）⇒ 先按完整路径试，再按文件名在所有条目里唯一匹配
            entry = target_cls.replace(".", "/") + ".java"
            if entry not in names:
                simple = target_cls.rsplit(".", 1)[-1] + ".java"
                cands = [n for n in names if n.endswith("/" + simple)]
                if len(cands) == 1:
                    entry = cands[0]
                elif len(cands) > 1:
                    out.append(f"  [{line}] {target_cls}.{mname}: 同名类 {len(cands)} 个，"
                               f"无法消歧（{cands[:3]}）⇒ 跳过")
                    continue
            if entry not in names:
                out.append(f"  [{line}] {target_cls}.{mname}: 原版源码未找到（{entry}）")
                continue
            src_text = z.read(entry).decode("utf-8", errors="replace")
            mbody, ok = extract_method(src_text, mname)
            if not ok:
                out.append(f"  [{line}] {target_cls}.{mname}: 源码里未定位到方法体")
                continue
            hits = len(re.findall(r"\b" + re.escape(member) + r"\s*\(", mbody))
            flag = "" if hits >= (int(req.group(1)) if req and int(req.group(1)) >= 1 else 1) else "  ⚠️ 低于注解要求"
            out.append(f"  [{line}] {target_cls}.{mname} 内 `{member}` 出现 {hits} 次"
                       f"（注解 {dec}）{flag}")
    return out, n


def extract_method(text, name):
    """按大括号配平取方法体。

    ⚠️ 必须**优先匹配「声明形态」**（行首修饰符 + name(）—— 否则会先撞上别处的**调用点**
    （如 `this.renderEffects(...)`），取其后的第一个 `{` 会配平到完全不相干的块。
    """
    decl = re.compile(r"(?m)^[ \t]*(?:@\w+[^\n]*\n[ \t]*)?(?:public|protected|private|static|final|"
                      r"synchronized|abstract|native|default)[^\n]{0,140}?\b" + re.escape(name) + r"\s*\(")
    cands = list(decl.finditer(text)) or list(re.finditer(r"\b" + re.escape(name) + r"\s*\(", text))
    for m in cands:
        semi = text.find(";", m.end())
        brace = text.find("{", m.end())
        if brace < 0 or (0 <= semi < brace):   # 抽象/接口声明，无方法体
            continue
        depth = 0
        for j in range(brace, len(text)):
            if text[j] == "{":
                depth += 1
            elif text[j] == "}":
                depth -= 1
                if depth == 0:
                    return text[brace:j + 1], True
    return "", False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--hint-sources", action="store_true",
                    help="额外打印 W2：@Redirect 目标方法的源码粗算次数（仅参考）")
    args = ap.parse_args()

    all_hard, all_warn, total = [], [], 0
    for line in LINES:
        hard, warn, n = audit_line(line)
        total += n
        print(f"## {line:<18} mixin 注入注解 {n} 处")
        for h in hard:
            print(f"   [HARD] {h}")
        for w in warn:
            print(f"   [warn] {w}")
        all_hard += hard
        all_warn += warn

    print()
    print(f"合计注解 {total} 处 | 硬违规 {len(all_hard)} | 提示 {len(all_warn)}")

    if args.hint_sources:
        print()
        print("[W2] @Redirect 目标方法的源码粗算（**仅参考**，源码计数 != 字节码指令计数）：")
        for line in LINES:
            lines, _ = source_hint(line)
            for l in lines:
                print(l)

    if all_hard:
        print()
        print("RESULT: FAIL —— 存在硬违规（详见上方 [HARD]）")
        return 1
    print("RESULT: PASS（无硬违规）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
