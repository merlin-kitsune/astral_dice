#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验 PartyRelations 的**反射契约**与第三方发布产物是否一致。

## 为什么需要这个脚本

`PartyRelations` 用反射接入 FTB Teams / OPAC（不能编译期依赖：三条线装的第三方模组不同）。
反射的致命弱点是：**签名对不上时不会编译报错，只在运行时静默失效**。
2026-10-01 实测就踩了这个坑 —— FTB 后端四处签名对不上，整个后端恒为未启用、只打一条 debug。

本脚本把「源码里声明的契约」与「真实 jar 里的方法」逐个比对，把这类失效变成**可断言的红灯**。

## 做法

1. 从 `PartyRelations.java` 解析出：
   - `Class<?> X = Class.forName("...")` 的变量→类名绑定（常量名会先解析成字符串）；
   - `VAR = X.getMethod("name", T1.class, T2.class, ...)` 的方法契约。
   - 特例：访问器类的解析带 fallback（`accessorCls` 先试嵌套接口、失败退回外层类），
     脚本按同样顺序判定「至少一种形态存在」。
2. 用 `javap -p -classpath <jar> <cls>` 取真实签名，比对方法名、参数个数、参数类型。
3. 参数类型比对：JDK 类型按全名比；MC 类型同时接受 **Mojmap 名**与 **intermediary 名**
   （不同发布方 remap 口径不同：FTB 用 intermediary、OPAC 用 Mojmap）。
   映射取自 Loom 的 `mappings.tiny`（权威），取不到则退回内置表。

## 覆盖范围（2026-10-01 扩展为**四线**）

四条线的 `PartyRelations` 各有一份源码，反射契约必须逐线核对 —— 本脚本按
`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1` 逐线跑：
从该线源码抽契约，再按「加载器 + MC 版本提示」找该线的第三方发布产物（`ftb-teams-<loader>-*.jar` /
`open-parties-and-claims-<loader>-*.jar`）比对。

⚠️ **产物缺失只跳过对应后端**（SKIP 并记入摘要）—— 本机没有 fabric / 26.1.2 的第三方产物，
硬要「四线全亮」会让闸门长期常态变红、反而掩盖真回归。用 `--strict` 可把 SKIP 也判为失败（CI 用）。

## 用法

    python tools/verify_party_api.py                       # 默认全四线，从 temp/party_verify/jars 找 jar
    python tools/verify_party_api.py --line forge-1.20.1   # 只校验一条线（可重复）
    python tools/verify_party_api.py --jars <dir>
    python tools/verify_party_api.py --dump                # 附带打印全部相关 javap 签名
    python tools/verify_party_api.py --pre-fix             # 校验「修复前」的契约（用于回归取证）
    python tools/verify_party_api.py --strict              # 把 SKIP（缺产物）也判为失败

退出码：0 = 无 FAIL；1 = 有 FAIL（或 `--strict` 下有 SKIP）；2 = 环境缺失（javap 找不到）。
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import glob

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PARTY_REL = os.path.join(
    "src", "main", "java",
    "com", "merlinkitsune", "astral_dice", "combat", "PartyRelations.java",
)
# 默认线（保持向后兼容：单独调用不改参数时只看 fabric）。
PARTY = os.path.join(REPO, "fabric-1.20.1", PARTY_REL)

# 四条线 → 各后端**允许的产物文件名模式**。
# ⚠️ 必须是**显式登记**、且不允许「找不到就退回按加载器通配」的模糊回退 ——
#    实测踩到过：26.1.2 线因为通了加载器通配，拿 **1.21.1 的** `ftb-teams-neoforge-2101.1.11.jar`
#    去校验 26.1.2 的契约并全绿 —— **假绿**比缺校验更危险。找不到就是 SKIP。
#    FTB 自己的版本号已编码 MC 版本（2001 = 1.20.1 / 2101 = 1.21.1 / 26.1.2 = 26.1.2）。
LINE_JARS = {
    "neoforge-1.21.1": {"ftb": "ftb-teams-neoforge-2101*.jar",
                        "opac": "open-parties-and-claims-neoforge-*1.21.1*.jar"},
    "forge-1.20.1":    {"ftb": "ftb-teams-forge-2001*.jar",
                        "opac": "open-parties-and-claims-forge-1.20.1-*.jar"},
    "neoforge-26.1.2": {"ftb": "ftb-teams-neoforge-26.1.2*.jar",
                        "opac": "open-parties-and-claims-neoforge-*26.1.2*.jar"},
    "fabric-1.20.1":   {"ftb": "ftb-teams-fabric-2001*.jar",
                        "opac": "open-parties-and-claims-fabric-1.20.1-*.jar"},
}
LINES = list(LINE_JARS)

# javap 打印的是 **descriptor 里的类型名**，不同发布方 remap 口径不同 ⇒ 两种写法都接受。
# 映射来源：Loom 的 mappings.tiny（official / intermediary / named 三列），见 _load_mc_aliases。
MC_ALIASES_FALLBACK = {
    "net.minecraft.world.entity.player.Player": ["net.minecraft.class_1657"],
    "net.minecraft.server.level.ServerPlayer": ["net.minecraft.class_3222"],
    "net.minecraft.server.MinecraftServer": ["net.minecraft.server.MinecraftServer"],
}


# ────────────────────────────── javap 定位 ──────────────────────────────

def find_javap() -> str | None:
    found = shutil.which("javap")
    if found:
        return found
    patterns = [
        r"C:\Program Files\Java\*\bin\javap.exe",
        r"C:\Program Files\Zulu\*\bin\javap.exe",
        r"C:\Program Files\Eclipse Adoptium\*\bin\javap.exe",
        r"C:\Program Files\Microsoft\jdk*\bin\javap.exe",
    ]
    for pat in patterns:
        hits = sorted(glob.glob(pat))
        if hits:
            return hits[-1]
    return None


def loom_mapping_file() -> str | None:
    candidates = glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/1.20.1/loom.mappings.*/mappings.tiny"))
    return candidates[0] if candidates else None


def load_mc_aliases() -> dict[str, list[str]]:
    """named(Mojmap) → [intermediary]，外加自身，供参数类型比对时二选一命中。"""
    aliases = {k: list(v) for k, v in MC_ALIASES_FALLBACK.items()}
    path = loom_mapping_file()
    if not path:
        return aliases
    try:
        with open(path, "r", encoding="utf-8") as fh:
            for line in fh:
                if not line.startswith("c\t"):
                    continue
                parts = line.rstrip("\n").split("\t")
                if len(parts) < 4:
                    continue
                intermediary, named = parts[2], parts[3]
                aliases.setdefault(named, [])
                if intermediary not in aliases[named]:
                    aliases[named].append(intermediary)
    except OSError:
        pass
    return aliases


# ────────────────────────────── 源码契约解析 ──────────────────────────────

CONST_RE = re.compile(r'private\s+static\s+final\s+String\s+(\w+)\s*=\s*"([^"]*)"\s*;')
CLASS_FORNAME_RE = re.compile(r'(\w+)\s*=\s*Class\.forName\(\s*(\w+)\s*\)\s*;')
CLASS_ALIAS_RE = re.compile(r'(\w+)\s*=\s*(apiCls)\s*;')
GETMETHOD_RE = re.compile(
    r'(\w+)\s*=\s*(\w+)\.getMethod\(\s*"([^"]+)"((?:\s*,\s*[\w.]+\.class)*)\s*\)\s*;')


def split_scopes(src: str):
    """按内部类切分源码段。

    ⚠️ 必须分段解析：`Ftb` 与 `Opac` 两个内部类**共用** `apiCls` / `managerCls` 等变量名，
    若整文件一把梭，后者的 `Class.forName` 绑定会覆盖前者 ⇒ 契约被错误归属到别的类
    （2026-10-01 实测踩到：FTB 的方法被算到 OpenPACServerAPI 名下）。
    """
    marks = [(m.start(), m.group(1))
             for m in re.finditer(r'private\s+static\s+final\s+class\s+(\w+)\s*\{', src)]
    if not marks:
        return [("<top>", src)]
    scopes = []
    for idx, (pos, name) in enumerate(marks):
        end = marks[idx + 1][0] if idx + 1 < len(marks) else len(src)
        scopes.append((name, src[pos:end]))
    return scopes


def parse_contract(src: str):
    consts: dict[str, str] = {}
    var_to_const: dict[str, str] = {}
    var_alias: dict[str, str] = {}
    calls = []

    for scope_name, text in split_scopes(src):
        # ⚠️ 常量也按作用域限定：两个内部类的 `API_CLASS` / `MANAGER_CLASS` 同名，
        #    共用一张表会被后者覆盖（2026-10-01 实测踩到）。
        local_consts = {name: value for name, value in CONST_RE.findall(text)}
        for name, value in local_consts.items():
            consts[f"{scope_name}.{name}"] = value
        for var, const_name in CLASS_FORNAME_RE.findall(text):
            if const_name in local_consts:
                var_to_const[f"{scope_name}.{var}"] = f"{scope_name}.{const_name}"
        for var, target in CLASS_ALIAS_RE.findall(text):
            var_alias[f"{scope_name}.{var}"] = f"{scope_name}.{target}"
        for recv_var, parts, name, params_blob in GETMETHOD_RE.findall(text):
            raw = [p.strip() for p in params_blob.split(",") if p.strip()]
            params = [p[: -len(".class")] for p in raw]
            calls.append({"recv": f"{scope_name}.{parts}", "into": recv_var,
                          "name": name, "params": params})

    return consts, var_to_const, var_alias, calls


def resolve_class_chain(var: str, consts, var_to_const, var_alias, depth: int = 0):
    """返回该变量**可能**指向的类名候选列表（主形态在前、fallback 在后）。

    源码里的 `accessorCls` 是「先 `Class.forName(嵌套接口)`、失败退回外层类」的写法，
    两个绑定都要收进来，脚本才能按同样顺序判定「至少一种形态命中」。
    """
    if depth > 4:
        return []
    out: list[str] = []
    if var in var_to_const:
        out.append(consts[var_to_const[var]])
    if var in var_alias:
        for cand in resolve_class_chain(var_alias[var], consts, var_to_const, var_alias, depth + 1):
            if cand not in out:
                out.append(cand)
    return out


# 源码里写的简单类型名 → 全名（脚本只支持本文件实际用到的那些）
SIMPLE_TYPES = {
    "UUID": "java.util.UUID",
    "MinecraftServer": "net.minecraft.server.MinecraftServer",
}


def full_type(simple: str, consts: dict) -> str:
    if simple in SIMPLE_TYPES:
        return SIMPLE_TYPES[simple]
    # 形如 Player / ServerPlayer —— 源码里没写全名，用别名表反查 Mojmap 全名
    for named in MC_ALIASES:
        if named.rsplit(".", 1)[-1] == simple:
            return named
    return simple


# ────────────────────────────── javap 执行与解析 ──────────────────────────────

MC_ALIASES: dict[str, list[str]] = {}


def javap_methods(javap: str, jar: str, cls: str) -> tuple[dict[str, list[list[str]]] | None, str]:
    """返回 {方法名: [参数类型列表, ...]}；失败时第二项为错误摘要。"""
    try:
        proc = subprocess.run(
            [javap, "-p", "-classpath", jar, cls],
            capture_output=True, text=True, timeout=120,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        return None, f"javap 执行失败: {exc}"
    out = proc.stdout + proc.stderr
    if "找不到类" in out or "class not found" in out.lower() or "Error:" in out:
        return None, out.strip().splitlines()[0][:120] if out.strip() else "javap 无输出"

    methods: dict[str, list[list[str]]] = {}
    for line in proc.stdout.splitlines():
        line = line.strip()
        if not line.endswith(";") or "(" not in line:
            continue
        head = line[: line.index("(")]
        args_blob = line[line.index("(") + 1: line.rindex(")")]
        name = head.split()[-1].split("<")[0]
        params = [p.strip() for p in args_blob.split(",") if p.strip()]
        methods.setdefault(name, []).append(params)
    return methods, ""


def type_matches(declared_source_type: str, descriptor_type: str, consts: dict) -> bool:
    expected = full_type(declared_source_type, consts)
    accepted = {expected}
    for alias in MC_ALIASES.get(expected, []):
        accepted.add(alias)
    return descriptor_type in accepted


def type_matches_loose(descriptor_type: str) -> bool:
    """MC 类型的宽松判定：只看命名空间（用于给出「差一个类型」的诊断提示）。"""
    return descriptor_type.startswith("net.minecraft.")


# ────────────────────────────── 主流程 ──────────────────────────────

def payload_for(line: str, kind: str, jars_dir: str):
    """按该线**显式登记**的模式定位第三方发布产物；找不到返回 None（= SKIP，不模糊回退）。"""
    pat = LINE_JARS[line][kind]
    hits = sorted(glob.glob(os.path.join(jars_dir, pat)))
    if len(hits) > 1:
        print("  ⚠️ %s 的 %s 产物命中多份，取文件名最大者：%s" %
              (line, kind, [os.path.basename(h) for h in hits]))
    return hits[-1] if hits else None


def show(cls: str) -> str:
    return cls if len(cls) <= 58 else "…" + cls[-57:]


def check_line(line: str, javap: str, jars_dir: str, pre_fix: bool, dump: bool):
    """校验一条线。返回 (pass, fail, skip)。"""
    src_path = os.path.join(REPO, line, PARTY_REL)
    if pre_fix:
        cand = os.path.join(REPO, "temp", "party_verify",
                            "PartyRelations.prefix.%s.java" % line)
        if os.path.exists(cand):
            src_path = cand

    print()
    print("=" * 126)
    print("线 %s   源码 %s" % (line, os.path.relpath(src_path, REPO)))
    if not os.path.exists(src_path):
        print("  SKIP —— 找不到源码文件")
        return 0, 0, 1
    with open(src_path, "r", encoding="utf-8") as fh:
        src = fh.read()

    jars = {k: payload_for(line, k, jars_dir) for k in ("ftb", "opac")}
    print("  jar  " + " | ".join(
        "%s=%s" % (k, os.path.basename(v) if v else "(缺)")
        for k, v in jars.items()))
    print()
    print("%-6s%-60s%-60s" % ("结果", "目标类", "方法(参数)"))
    print("-" * 126)

    consts, var_to_const, var_alias, calls = parse_contract(src)

    def pick_jar(class_name: str):
        return jars["opac"] if class_name.startswith("xaero.") else jars["ftb"]

    n_pass = n_fail = n_skip = 0
    seen = set()
    for call in calls:
        recv = call["recv"]
        candidates = resolve_class_chain(recv, consts, var_to_const, var_alias)
        if not candidates:
            print("%-6s%-52s%s" % ("SKIP", recv, "（无法从源码解析出类名）"))
            n_skip += 1
            continue

        declared = [full_type(p, consts) for p in call["params"]]
        signature = "%s(%s)" % (call["name"], ", ".join(declared))
        key = (candidates[0], call["name"], len(declared))
        if key in seen:
            continue
        seen.add(key)

        # 该调用要用哪一份产物？缺失 ⇒ 只跳过这一条（不整线跳过）。
        jar = pick_jar(candidates[0])
        if not jar:
            want = "opac" if candidates[0].startswith("xaero.") else "ftb-teams"
            print("%-6s%-60s%s" % ("SKIP", show(candidates[0]), "（缺本地产物 %s）" % want))
            n_skip += 1
            continue

        ok_pair = None
        last_err = "（未找到可用类名）"
        for cls in candidates:
            methods, err = javap_methods(javap, jar, cls)
            if methods is None:
                last_err = err
                continue
            entries = methods.get(call["name"], [])
            if not entries:
                last_err = "类存在但无方法 %s" % call["name"]
                continue
            for params in entries:
                if len(params) != len(declared):
                    continue
                if all(type_matches(d, p, consts) for d, p in zip(declared, params)):
                    ok_pair = (cls, params)
                    break
                last_err = "参数类型不匹配: 期望 %s 实际 %s(%s)" % (
                    signature, call["name"], ", ".join(params))
            if ok_pair:
                break

        if ok_pair:
            cls, params = ok_pair
            note = "OK" if len(candidates) == 1 else "OK(用 fallback 类命中)"
            print("%-6s%-60s%s  %s" % ("PASS", show(cls), "%s(%s)" % (call["name"], ", ".join(params)), note))
            n_pass += 1
        else:
            print("%-6s%-60s%s" % ("FAIL", show(candidates[0]), signature))
            print("       └─ %s" % last_err)
            n_fail += 1

    if dump:
        print("\n===== javap 完整签名 =====")
        dumped = set()
        for call in calls:
            for cls in resolve_class_chain(call["recv"], consts, var_to_const, var_alias):
                j = pick_jar(cls)
                if not j or cls in dumped:
                    continue
                dumped.add(cls)
                methods, err = javap_methods(javap, j, cls)
                print("\n----- %s" % cls)
                if methods is None:
                    print("   %s" % err)
                    continue
                for name, sigs in methods.items():
                    if name in {c["name"] for c in calls} or name in (
                            "api", "getId", "getTeamId", "isPartyTeam", "isServerTeam", "teamId"):
                        for params in sigs:
                            print("   %s(%s)" % (name, ", ".join(params)))

    print("-" * 126)
    print("  小计: PASS=%d FAIL=%d SKIP=%d" % (n_pass, n_fail, n_skip))
    return n_pass, n_fail, n_skip


def main() -> int:
    ap = argparse.ArgumentParser(description="校验 PartyRelations 反射契约与真实 jar 的一致性（四线）")
    ap.add_argument("--jars", default=os.path.join(REPO, "temp", "party_verify", "jars"),
                    help="存放 ftb-teams / open-parties-and-claims jar 的目录")
    ap.add_argument("--line", action="append", default=None,
                    choices=sorted(LINE_JARS), help="只校验指定线（可重复；默认全四线）")
    ap.add_argument("--dump", action="store_true", help="打印相关方法的完整 javap 签名")
    ap.add_argument("--pre-fix", action="store_true",
                    help="改为校验修复前的契约（取证用，预期 FAIL）")
    ap.add_argument("--strict", action="store_true",
                    help="把 SKIP（缺产物 / 无法解析类名）也判为失败")
    args = ap.parse_args()

    global MC_ALIASES
    MC_ALIASES = load_mc_aliases()

    javap = find_javap()
    if not javap:
        print("FAIL 找不到 javap（需要 JDK）")
        return 2
    print("javap      : %s" % javap)
    print("映射来源   : %s" % (loom_mapping_file() or "(内置表)"))
    print("jar 目录   : %s" % args.jars)

    lines = args.line if args.line else sorted(LINE_JARS)
    tot_p = tot_f = tot_s = 0
    for line in lines:
        p, f, s = check_line(line, javap, args.jars, args.pre_fix, args.dump)
        tot_p += p
        tot_f += f
        tot_s += s

    print()
    print("=" * 126)
    print("汇总（%d 条线）: PASS=%d FAIL=%d SKIP=%d" % (len(lines), tot_p, tot_f, tot_s))
    if tot_s:
        print("SKIP 说明: 该线所需第三方发布产物不在 %s（本机未提供）—— 属**未验证**，非通过。" % args.jars)

    if tot_f:
        print("\n结论: FAIL —— 反射契约与发布产物不一致，该后端会在运行时静默失效。")
        return 1
    if tot_s and args.strict:
        print("\n结论: FAIL（--strict）—— 存在无法校验的条目。")
        return 1
    if tot_s:
        print("\n结论: PASS（有 SKIP）—— 已校验部分全部一致；未覆盖部分见上面 SKIP 说明。")
        return 0
    print("\n结论: PASS —— 全四线反射契约与发布产物完全一致。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
