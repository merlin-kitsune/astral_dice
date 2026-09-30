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

## 用法

    python tools/verify_party_api.py                       # 默认从 temp/party_verify/jars 找 jar
    python tools/verify_party_api.py --jars <dir>
    python tools/verify_party_api.py --dump                # 附带打印全部相关 javap 签名
    python tools/verify_party_api.py --pre-fix            # 校验「修复前」的契约（用于回归取证）

退出码：0 = 全部 PASS；1 = 有 FAIL；2 = 环境缺失（jar / javap 找不到）。
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
PARTY = os.path.join(
    REPO, "fabric-1.20.1", "src", "main", "java",
    "com", "merlinkitsune", "astral_dice", "combat", "PartyRelations.java",
)

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

def main() -> int:
    ap = argparse.ArgumentParser(description="校验 PartyRelations 反射契约与真实 jar 的一致性")
    ap.add_argument("--jars", default=os.path.join(REPO, "temp", "party_verify", "jars"),
                    help="存放 ftb-teams / ftb-library / opac jar 的目录")
    ap.add_argument("--dump", action="store_true", help="打印相关方法的完整 javap 签名")
    ap.add_argument("--pre-fix", action="store_true",
                    help="改为校验修复前的契约（取证用，预期 FAIL）")
    args = ap.parse_args()

    global MC_ALIASES
    MC_ALIASES = load_mc_aliases()

    javap = find_javap()
    if not javap:
        print("FAIL 找不到 javap（需要 JDK）")
        return 2
    print(f"javap      : {javap}")
    print(f"映射来源   : {loom_mapping_file() or '(内置表)'}")

    src_path = PARTY
    if args.pre_fix:
        src_path = os.path.join(REPO, "temp", "party_verify", "PartyRelations.prefix.java")
        if not os.path.exists(src_path):
            print(f"FAIL --pre-fix 需要先准备修复前快照: {src_path}")
            return 2
    if not os.path.exists(src_path):
        print(f"FAIL 找不到 PartyRelations.java: {src_path}")
        return 2
    with open(src_path, "r", encoding="utf-8") as fh:
        src = fh.read()
    print(f"被校验源码 : {os.path.relpath(src_path, REPO)}")

    consts, var_to_const, var_alias, calls = parse_contract(src)

    # jar 定位：按类名前缀把「变量」路由到对应 jar
    jars = {
        "ftb": sorted(glob.glob(os.path.join(args.jars, "ftb-teams-fabric-*.jar"))),
        "opac": sorted(glob.glob(os.path.join(args.jars, "open-parties-and-claims-fabric-*.jar"))),
    }
    for key, hits in jars.items():
        if not hits:
            print(f"FAIL 缺少 {key} jar（目录: {args.jars}）")
            return 2
        jars[key] = hits[0] if key == "opac" else hits[-1]
    print("jar        : " + " | ".join(f"{k}={os.path.basename(v)}" for k, v in jars.items()))

    def pick_jar(class_name: str) -> str:
        return jars["opac"] if class_name.startswith("xaero.") else jars["ftb"]

    print()
    print(f"{'结果':<6}{'目标类':<60}{'方法(参数)':<60}")
    print("-" * 126)

    def show(cls: str) -> str:
        return cls if len(cls) <= 58 else "…" + cls[-57:]

    n_pass = n_fail = 0
    seen: set[tuple[str, str, int]] = set()
    for call in calls:
        recv = call["recv"]
        candidates = resolve_class_chain(recv, consts, var_to_const, var_alias)
        if not candidates:
            print(f"{'SKIP':<6}{recv:<52}{'（无法从源码解析出类名）':<46}")
            continue

        declared = [full_type(p, consts) for p in call["params"]]
        signature = f"{call['name']}({', '.join(declared)})"
        key = (candidates[0], call["name"], len(declared))
        if key in seen:
            continue
        seen.add(key)

        ok_pair = None
        last_err = "（未找到可用类名）"
        for cls in candidates:
            jar = pick_jar(cls)
            methods, err = javap_methods(javap, jar, cls)
            if methods is None:
                last_err = err
                continue
            entries = methods.get(call["name"], [])
            if not entries:
                last_err = f"类存在但无方法 {call['name']}"
                continue
            for params in entries:
                if len(params) != len(declared):
                    continue
                if all(type_matches(d, p, consts) for d, p in zip(declared, params)):
                    ok_pair = (cls, params)
                    break
                # 参数个数对、类型不符 ⇒ 记录为「类型不匹配」，便于给出诊断
                last_err = "参数类型不匹配: 期望 " + signature + " 实际 " + \
                           f"{call['name']}({', '.join(params)})"
            if ok_pair:
                break

        if ok_pair:
            cls, params = ok_pair
            note = "OK" if len(candidates) == 1 else "OK(用 fallback 类命中)"
            print(f"{'PASS':<6}{show(cls):<60}{call['name']}({', '.join(params)})  {note}")
            n_pass += 1
        else:
            print(f"{'FAIL':<6}{show(candidates[0]):<60}{signature}")
            print(f"       └─ {last_err}")
            n_fail += 1

    print("-" * 126)
    print(f"合计: PASS={n_pass} FAIL={n_fail}")

    if args.dump:
        print("\n===== javap 完整签名 =====")
        dumped = set()
        for call in calls:
            for cls in resolve_class_chain(call["recv"], consts, var_to_const, var_alias):
                if cls in dumped:
                    continue
                dumped.add(cls)
                methods, err = javap_methods(javap, pick_jar(cls), cls)
                print(f"\n----- {cls}")
                if methods is None:
                    print(f"   {err}")
                    continue
                for name, sigs in methods.items():
                    if name in {c["name"] for c in calls} or name in (
                            "api", "getId", "getTeamId", "isPartyTeam", "isServerTeam", "teamId"):
                        for params in sigs:
                            print(f"   {name}({', '.join(params)})")

    if n_fail:
        print("\n结论: FAIL —— 反射契约与发布产物不一致，该后端会在运行时静默失效。")
        return 1
    print("\n结论: PASS —— 反射契约与发布产物完全一致。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
