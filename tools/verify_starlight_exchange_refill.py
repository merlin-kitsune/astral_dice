#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_starlight_exchange_refill.py —— 星光「可兑换 + 兑换后补充」静态与数值守门
=============================================================================

对应 2026-10-24 用户裁决(星光兑换缺陷修复)的 **P0 两条线**(`neoforge-1.21.1` /
`forge-1.20.1`)。本脚本是「星光不再是不可兑换的下限,而是装备发放 + 每次兑换后按
当前佩戴补充」这一行为契约的**静态对偶 + 手算数值表**。

检查项
------
S1 下限 API 彻底移除(每线全树 `src/main/java/**/*.java`,代码与注释分别判定)
   S1-a 生产代码中**不得**再出现 `getBasePoints`
   S1-b 生产代码中**不得**再出现 `grantStarlightFloorOnEquip`
   S1-c 注释中也**不得**残留上述两个失效引用(`{@link}` 悬空同样是文档缺陷)
S2 装备发放入口统一
   S2-a 银行卡-余额少/多:`onEquip` 必须调用 `grantStarlightOnEquip(`,**不得**再调用
        `grantStarlightFloorOnEquip(`,且发放量不得是 4/7 字面量(必须来自契约常量)
   S2-b 银行卡-用不完 / 星币锤:`onEquip` 必须调用 `grantStarlightOnEquip(` 且以
        `STARLIGHT_ON_EQUIP` 传入数值
   S2-c `ModItems` 里两张银行卡的注册必须分别传 `BankCardChipItem.STARLIGHT_LOW` /
        `STARLIGHT_HIGH`(四件物品的数值只能有一处定义)
S3 具名常量与字面值
   S3-a `BankCardChipItem.STARLIGHT_LOW = 4`、`STARLIGHT_HIGH = 7`
   S3-b `BankCardUnlimitedChipItem.STARLIGHT_ON_EQUIP = 3`
   S3-c `StarCoinHammerChipItem.STARLIGHT_ON_EQUIP = 5`
   S3-d 旧名 `BASE_LOW` / `BASE_HIGH` 在代码中不得残留(避免同值双源漂移)
   S3-e `ResourceConversion.STARLIGHT_PER_COIN = 2` 保持不变
S4 值域钳制与消耗语义
   S4-a `StarLightManager.set` 必须为 `Math.max(0, Math.min(<value>, getCap()))`,
        且方法体不得引用 `getBasePoints` / `base`
   S4-b `spend` 不得再有「低于基础值自动补回」逻辑(方法体无 `getBasePoints`),
        且必须以「当前值 − 实扣量」写回:允许 `set(player, current - spent)` 与
        更安全的 `set(player, Math.max(0, current - spent))` 等**合法包裹**,
        但包裹内只能是两个标识符相减,不得借 `Math.max(getBasePoints(...), ...)` 复活下限
   S4-c `add` 仍按 `Math.min(... , getCap())` 封顶
S5 `StarLightManager.replenishOnExchange(Player)` 形状
   S5-a 签名 `public static int replenishOnExchange(Player ...)`
   S5-b 方法体检查全部四件物品(`BANK_CARD_LOW` / `BANK_CARD_HIGH` /
        `BANK_CARD_UNLIMITED` / `STAR_COIN_HAMMER`)
   S5-c 求和用的是四件物品的**同名常量**(`STARLIGHT_LOW` / `STARLIGHT_HIGH` /
        `STARLIGHT_ON_EQUIP` ×2),方法体内不得出现 3/4/5/7 字面量
   S5-d Curios 只读一次(`getCuriosInventory(` 出现 1 次),且存在性判定必须是
        `isPresent()` —— `isEmpty()` 语义相反,写反等于整段补充成死代码
   S5-e `add(` 恰好一次,实参必须是「`player` + 由四件同名常量 `+=` 累加出的变量」,
        且方法**唯一**的 `return` 就是该 `add(...)` 的结果(`add(player, 0)` /
        `return 0;` 都不算补充);累加语句必须逐件出现
   S5-f **不得**写装备账本 / 闸门:`claimEquipGrant`、`setGrantedStarlight`、
        `grantedStarlight`、`revokeStarlightOnUnequip`、`EquipGrant` 一律不得出现,
        `ModAttachments.setStarlight` 也不得出现(补充走普通 `add`)
S6 兑换顺序(按**方法调用名**匹配,限定 / 非限定均可;不绑定 `gained` 变量名)
   S6-a `starlightToStarCoins` 中可支配量 = 当前值,不再减基础值
   S6-b 顺序必须为 `spend(...)` → spend 之后读当前星光(算实扣量) → `replenishOnExchange(...)`
   S6-c `replenishOnExchange` 恰好调用一次,且**位于 `if (<实扣量> > 0)` 块内**;
        该 `if` 必须晚于 spend 后的那次读取(排除 `if (coins <= 0) return 0` 之类早退闸门);
        条件里的变量必须由「spend 后的当前值 − 扣除量」算出(不能是任意 >0 变量);
        该补充**不得**被常量假分支(`if (false)` 等)包住
   S6-d 商店入口仍以 `-1`(全量)调用兑换(数值表的前提)
S7 客户端口径
   S7-a `CardShopScreen#canExchange` 用完整当前值(不得再减基础值 / 不得出现减法)
   S7-b `starlightState` 的 return 字符串里 `base` 段必须是**常量 0**、`spendable` 段
        必须直接取当前值(全部在**去注释后的代码**上判定:注释里写 `base=0` 不算);
        两者必须出自**同一条、位于方法末尾的无条件 return**(未就绪哨兵 `-1` 是它
        之前的那条条件返回),常量假分支里的死 `return` 不能充当证据
S8 卸下扣回保留
   S8-a 四件物品的 `onChipUnequip` 仍调用 `revokeStarlightOnUnequip(...)`
        (用不完 / 星币锤必须带各自账本位)
   S8-b 管理器 `revokeStarlightOnUnequip` 仍按账本扣回并释放闸门,且不调用补充
   S8-c `grantStarlightOnEquip` 仍为「闸门 + add + 实际量记账」
N1 手算数值表(期望值为字面量,**不由被测实现生成**)
   high 7→8、low 4→4、unlimited 3→4、hammer 5→6、all four 19→20、
   no item 7→1、high 8→7、high 32→7、cap 32(all four)→19、
   超上限起始值 40→钳制 32→19、不足 2 点不兑换(带 high / 不带物品)均不补充。
   契约模型:全量兑换 → `coins = value // 2` → 扣 `coins*2` → `gained>0` 时
   `min(剩余 + 装备值之和, 32)`。

退出码
------
  0 = 全部通过(源码不变量 + 数值表)
  1 = 存在不满足的不变量(含「方法/常量缺失」——本门要求它们存在,缺失即缺陷)
  2 = 无法判定(源文件不可读)

⚠️ 能力边界(不得省略)
  本脚本只做**静态取证**与**纸面数值契约**自检:
  - 不启动游戏、不跑 Gradle、不反射、不执行 Java ⇒ **不能证明运行时行为**;
  - 无法验证 Curios 实际槽位、登陆重放、network sync、钱包吸收等运行时路径;
  - `GameplayConstants.MAX_STARLIGHT` 的源码不在本仓(来自 starengine 依赖),
    `cap = 32` 是规格字面量,本门只能证明代码「用了 `getCap()`」而**不能**证明
    该常量在 jar 里确实是 32;
  - 「死分支」只认**字面常量假**条件(`if (false)` / `if (0)` / 两个整数字面量的
    比较)⇒ 用标识符或运行期条件伪造的死支(如 `if (someAlwaysFalseFlag)`)不在
    静态可判范围内,不能据此宣称「没有死代码」。
  静态全绿 ≠ 已验收;实机欠账照常登记。

用法
----
  python -B tools/verify_starlight_exchange_refill.py [-v]
"""

import os
import re
import sys


# --- stdout/stderr 编码自愈(统一口径:python 侧强制 UTF-8;勿删)---
# 背景:Windows 默认代码页 936 下 python 子进程的 sys.stdout.encoding 为 gbk,
# 本脚本打印的 ⇒/⚠/✅/↔ 等符号 GBK 不可表示 ⇒ 直接 UnicodeEncodeError 退出码 1
#(“门会红但红的原因是崩溃”)。此块使脚本不依赖调用者环境与宿主编码。
# ⚠ 块内引用 sys ⇒ 必须位于**所有 import 之后**;也**不得**加 except-pass(会静默失效)。
def _astral_heal_stdio() -> None:  # pragma: no cover - 环境适配
    for _stream_name in ("stdout", "stderr"):
        _stream = getattr(sys, _stream_name, None)
        _reconf = getattr(_stream, "reconfigure", None)
        if _reconf is None:
            continue
        try:
            _reconf(encoding="utf-8", errors="replace")
        except Exception as _exc:  # noqa: BLE001 - 明确告警,绝不静默
            sys.stderr.write(
                "[astral-gate] WARN: %s.reconfigure(utf-8) failed: %r\n"
                % (_stream_name, _exc)
            )


_astral_heal_stdio()

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 本特性只覆盖 P0 两条线;P1(neoforge-26.1.2 / fabric-1.20.1)按仓库默认**不**静默移植。
LINES = ["neoforge-1.21.1", "forge-1.20.1"]

PKG = os.path.join("com", "merlinkitsune", "astral_dice")

# 规格字面量(用户裁决 + 计划 Task 2 接口)。数值表的期望值一律由人工按契约手算,
# 这里只是把「同一份规格」写成常量,避免期望值从被测实现反推。
SPEC = {
    "low": 4,
    "high": 7,
    "unlimited": 3,
    "hammer": 5,
    "cap": 32,
    "per_coin": 2,
}

VERBOSE = "-v" in sys.argv


# ──────────────────────────── 基础工具 ────────────────────────────


def rel(path):
    return os.path.relpath(path, ROOT).replace("\\", "/")


def read(path):
    with open(path, "rb") as fh:
        return fh.read().decode("utf-8", "replace")


def split_code_comments(src):
    """拆成 (代码, 注释拼接)。字符串/字符字面量按代码保留并跳过其内部。

    为什么分开:行为类判定(调用/赋值/字符串输出)必须只看代码 —— 注释里写
    `replenishOnExchange(...)` 或 `base=0` 不能冒充实现。

    ⚠️ 但**注释里保留被删除的旧 API 名是不允许的**:S1-c 会把
    `getBasePoints` / `grantStarlightFloorOnEquip` 的注释残留判 FAIL(悬空
    `{@link}` 本身就是文档缺陷)。本函数只负责让行为判定不被注释污染,
    **不是**「旧 API 可以先留在注释里」的许可。
    """
    code = []
    comments = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if c == '"' or c == "'":
            q = c
            code.append(c)
            i += 1
            while i < n:
                if src[i] == "\\":
                    code.append(src[i:i + 2])
                    i += 2
                    continue
                code.append(src[i])
                if src[i] == q:
                    i += 1
                    break
                i += 1
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            if j < 0:
                j = n
            comments.append(src[i:j])
            i = j
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i)
            if j < 0:
                j = n
            comments.append(src[i:j + 2])
            i = j + 2
            continue
        code.append(c)
        i += 1
    return "".join(code), "\n".join(comments)


def method_body(src, pattern):
    """按 `pattern` 命中方法签名后,取其后第一个 `{ ... }` 配平块;找不到返回 None。

    扫描期间跳过字符串/字符字面量与行/块注释中的花括号。
    """
    m = re.search(pattern, src)
    if not m:
        return None
    i = src.find("{", m.end())
    if i < 0:
        return None
    depth = 0
    j = i
    n = len(src)
    while j < n:
        c = src[j]
        if c == '"' or c == "'":
            q = c
            j += 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == q:
                    break
                j += 1
        elif c == "/" and j + 1 < n and src[j + 1] == "/":
            k = src.find("\n", j)
            if k < 0:
                break
            j = k
            continue
        elif c == "/" and j + 1 < n and src[j + 1] == "*":
            k = src.find("*/", j)
            if k < 0:
                break
            j = k + 1
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[i:j + 1]
        j += 1
    return None


def decl_statement(src, pattern):
    """取 `pattern` 命中处到其后的第一个 `;`(用于读注册语句里的常量引用)。"""
    m = re.search(pattern, src)
    if not m:
        return None
    end = src.find(";", m.end())
    if end < 0:
        return None
    return src[m.start():end + 1]


def int_constant(src, pattern):
    """读取 `... NAME = <digits>;` 的字面值;缺失返回 None。"""
    m = re.search(pattern, src)
    if not m:
        return None
    return int(m.group(1))


# ── 调用 / 表达式小工具(S4-b、S6、S7 共用;只吃去注释后的代码)──


def iter_calls(src, name):
    """枚举 `name(` 调用,允许 `A.B.name(` 限定前缀;返回 (起点, 终点, 实参文本)。

    顺序判定与块归属判定必须走同一个匹配器,否则会出现
    「顺序认限定名、块归属认非限定名」的口径不一致。
    """
    pat = re.compile(
        r"(?<![\w.])(?:[A-Za-z_]\w*\s*\.\s*)*%s\s*\(" % re.escape(name)
    )
    for m in pat.finditer(src):
        i = m.end() - 1  # 匹配以 `(` 结尾
        depth = 0
        j = i
        while j < len(src):
            c = src[j]
            if c == '"' or c == "'":
                q = c
                j += 1
                while j < len(src):
                    if src[j] == "\\":
                        j += 2
                        continue
                    if src[j] == q:
                        break
                    j += 1
            elif c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    yield m.start(), j + 1, src[i + 1:j]
                    break
            j += 1


def split_top_level(text):
    """按顶层逗号切实参(忽略括号/字符串内部的逗号)。"""
    parts = []
    cur = []
    depth = 0
    i = 0
    while i < len(text):
        c = text[i]
        if c == '"' or c == "'":
            q = c
            cur.append(c)
            i += 1
            while i < len(text):
                cur.append(text[i])
                if text[i] == "\\":
                    if i + 1 < len(text):
                        cur.append(text[i + 1])
                        i += 2
                        continue
                    i += 1
                    break
                if text[i] == q:
                    i += 1
                    break
                i += 1
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        if c == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(c)
        i += 1
    parts.append("".join(cur))
    return [p.strip() for p in parts]


def strip_safe_wrappers(expr):
    """剥掉 `Math.max(0, X)` / `Math.min(X, getCap())` 这类**合法**钳制外壳。

    只剥这两种:更安全的 `set(player, Math.max(0, current - spent))` 应被接受;
    而 `Math.max(getBasePoints(...), current - spent)` 不在剥离范围 ⇒ 下限复活必红。
    """
    prev = None
    out = expr.strip()
    while out != prev:
        prev = out
        m = re.fullmatch(r"Math\.max\(\s*0\s*,\s*(.+)\)", out, re.S)
        if m:
            out = m.group(1).strip()
            continue
        m = re.fullmatch(r"Math\.min\(\s*(.+),\s*getCap\(\)\s*\)", out, re.S)
        if m:
            out = m.group(1).strip()
    return out


def if_blocks(code):
    """枚举带花括号的 `if (...) { ... }`,返回 (条件文本, 起点, 终点, 块文本)。

    无花括号的单语句 `if (coins <= 0) return 0;` 不产出 —— 它不可能是补充闸门。
    """
    out = []
    for m in re.finditer(r"\bif\s*\(", code):
        i = code.find("(", m.start())
        depth = 0
        cond_end = -1
        j = i
        while j < len(code):
            c = code[j]
            if c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    cond_end = j
                    break
            j += 1
        if cond_end < 0:
            continue
        k = cond_end + 1
        while k < len(code) and code[k] in " \t\r\n":
            k += 1
        if k >= len(code) or code[k] != "{":
            continue
        block = method_body(code[m.start():], r"\bif\s*\(")
        if not block:
            continue
        out.append((code[i + 1:cond_end], m.start(), k + len(block), block))
    return out


def return_statements(code):
    """枚举每条 `return <expr>;`,返回 (起点, 表达式文本);跳过字符串里的分号。

    起点用于判定该 return 是否落在某个 `if (...)` 块内 —— 只给文本的版本
    无法区分「就绪路径的无条件返回」与「`if (false)` 里的死返回」。
    """
    out = []
    for m in re.finditer(r"\breturn\b", code):
        i = m.end()
        depth = 0
        j = i
        while j < len(code):
            c = code[j]
            if c == '"' or c == "'":
                q = c
                j += 1
                while j < len(code):
                    if code[j] == "\\":
                        j += 2
                        continue
                    if code[j] == q:
                        break
                    j += 1
            elif c in "([{":
                depth += 1
            elif c in ")]}":
                depth -= 1
            elif c == ";" and depth == 0:
                out.append((m.start(), code[i:j]))
                break
            j += 1
    return out


def return_expressions(code):
    """取出每条 `return <expr>;` 的表达式文本(跳过字符串里的分号)。"""
    return [expr for _start, expr in return_statements(code)]


def is_statically_false(cond):
    """`if (...)` 条件是否为**字面常量假** ⇒ 块体是死代码。

    只认字面常量(`false` / `0` / 两个整数字面量的比较),不做数据流常量折叠:
    标识符条件一律当作「可能为真」。这条边界是有意的 —— 静态门不猜运行期取值,
    但「`if (false)` 里补一行仍然全绿」这类**无歧义**的死支必须判红。
    """
    c = " ".join(cond.split())
    if c in ("false", "!true", "Boolean.FALSE", "Boolean.FALSE.booleanValue()"):
        return True
    if re.fullmatch(r"0+[lL]?", c):
        return True
    m = re.fullmatch(r"(\d+)\s*(==|!=|<=|>=|<|>)\s*(\d+)", c)
    if m:
        a, op, b = int(m.group(1)), m.group(2), int(m.group(3))
        return not {
            "==": a == b,
            "!=": a != b,
            "<": a < b,
            ">": a > b,
            "<=": a <= b,
            ">=": a >= b,
        }[op]
    return False


class Gate:
    def __init__(self):
        self.checks = 0
        self.fails = []
        self.unknowns = []
        self.warns = []

    def run(self, ok, msg):
        self.checks += 1
        if ok is None:
            self.unknowns.append(msg)
            print("  ?? " + msg)
        elif ok:
            if VERBOSE:
                print("  ok " + msg)
        else:
            self.fails.append(msg)
            print("  FAIL " + msg)

    def warn(self, msg):
        self.warns.append(msg)
        print("  WARN " + msg)


def code_of(src):
    return split_code_comments(src)[0]


def body_for(g, path, pattern, what, label):
    """取方法体;文件缺失 ⇒ UNKNOWN,方法缺失 ⇒ FAIL(本门要求它存在)。"""
    if not os.path.isfile(path):
        g.run(None, "%s 文件不存在: %s" % (label, rel(path)))
        return None
    body = method_body(read(path), pattern)
    if body is None:
        g.run(False, "%s %s 缺少 %s" % (label, rel(path), what))
        return None
    return body


def java_files(line):
    base = os.path.join(ROOT, line, "src", "main", "java")
    out = []
    for dirpath, _dirs, files in os.walk(base):
        for fn in files:
            if fn.endswith(".java"):
                out.append(os.path.join(dirpath, fn))
    return sorted(out)


# ──────────────────────── N1:手算数值契约模型 ────────────────────────


def spec_exchange(start, equipped, cap=None, per_coin=None):
    """把规格契约写成可执行的纸面模型(**不是**被测实现)。

    契约(计划 Task 2 Step 3 + 用户裁决):
      1. `set`/`add` 先把当前值钳制到 0..cap;
      2. 商店恒以 amount=-1 全量兑换 ⇒ use = 当前值,coins = use // 2;
      3. 先 `spend(coins*2)`,再按**实际**扣减算 `gained`;
      4. 仅当 `gained > 0` 时 `replenishOnExchange`(普通 add,再封顶 cap);
      5. 余数(奇数那 1 点)保留在池里,不退回、不发币。

    返回 (coins, final, gained)。
    """
    cap = SPEC["cap"] if cap is None else cap
    per_coin = SPEC["per_coin"] if per_coin is None else per_coin
    current = max(0, min(start, cap))
    use = current
    coins = use // per_coin
    if coins <= 0:
        return coins, current, 0
    before = current
    current = before - coins * per_coin
    gained = max(0, before - current) // per_coin
    if gained > 0:
        current = min(current + sum(equipped), cap)
    return coins, current, gained


# (标签, 起始星光, (low, high, unlimited, hammer) 佩戴值, 期望星币, 期望终值)
# 期望值全部人工手算,见每条注释的算式。
NUMERIC_ROWS = [
    # 7 → 花 6 → 余 1 → +7 = 8;3 币
    ("high 7 -> 8", 7, (0, 7, 0, 0), 3, 8),
    # 4 → 花 4 → 余 0 → +4 = 4;2 币
    ("low 4 -> 4", 4, (4, 0, 0, 0), 2, 4),
    # 3 → 花 2 → 余 1 → +3 = 4;1 币
    ("unlimited 3 -> 4", 3, (0, 0, 3, 0), 1, 4),
    # 5 → 花 4 → 余 1 → +5 = 6;2 币
    ("hammer 5 -> 6", 5, (0, 0, 0, 5), 2, 6),
    # 19 = 4+7+3+5 → 花 18 → 余 1 → +19 = 20;9 币
    ("all four 19 -> 20", 19, (4, 7, 3, 5), 9, 20),
    # 7 → 花 6 → 余 1 → 无佩戴不补 = 1;3 币
    ("no item 7 -> 1", 7, (0, 0, 0, 0), 3, 1),
    # 8 → 花 8 → 余 0 → +7 = 7;4 币(偶数起始值不保留奇点)
    ("high 8 -> 7", 8, (0, 7, 0, 0), 4, 7),
    # 上限 32 → 花 32 → 余 0 → +7 = 7;16 币(守住 cap 起始值也是可全量兑换)
    ("high 32 -> 7", 32, (0, 7, 0, 0), 16, 7),
    # 上限 32 + 四件 → 花 32 → 余 0 → +19 = 19,不越过 cap;16 币
    ("cap all four 32 -> 19", 32, (4, 7, 3, 5), 16, 19),
    # 起始 40 先被钳到 32 → 同上一行;16 币
    ("cap oversized start 40 -> 19", 40, (4, 7, 3, 5), 16, 19),
    # 只有 1 点 ⇒ 0 币,兑换失败 ⇒ **不得**补充(gained=0);终值 1
    ("sub-2 with high -> 1", 1, (0, 7, 0, 0), 0, 1),
    # 只有 1 点且无佩戴 ⇒ 0 币,终值 1
    ("sub-2 no item -> 1", 1, (0, 0, 0, 0), 0, 1),
]


# ──────────────────────────── 各段检查 ────────────────────────────


def check_s1_floor_removed(g, line, files):
    print("[S1] 下限 API 移除(%s 全树)" % line)
    for name in ("getBasePoints", "grantStarlightFloorOnEquip"):
        code_hits = []
        comment_hits = []
        for fp in files:
            code, comments = split_code_comments(read(fp))
            if re.search(r"\b%s\b" % name, code):
                code_hits.append(rel(fp))
            elif re.search(r"\b%s\b" % name, comments):
                comment_hits.append(rel(fp))
        g.run(
            not code_hits,
            "%s S1 生产代码仍含 `%s`: %s" % (line, name, ", ".join(code_hits) or "(none)"),
        )
        g.run(
            not comment_hits,
            "%s S1 注释仍残留失效引用 `%s`: %s"
            % (line, name, ", ".join(comment_hits) or "(none)"),
        )


def check_s2_equip_grants(g, line, base):
    chip_dir = os.path.join(base, "item", "chip")
    bank_low_high = os.path.join(chip_dir, "BankCardChipItem.java")
    unlimited = os.path.join(chip_dir, "BankCardUnlimitedChipItem.java")
    hammer = os.path.join(chip_dir, "StarCoinHammerChipItem.java")
    mod_items = os.path.join(base, "item", "ModItems.java")

    print("[S2] 装备发放入口(%s)" % line)
    body = body_for(g, bank_low_high, r"public\s+void\s+onEquip\s*\(", "onEquip", "S2-a")
    if body is not None:
        code = code_of(body)
        g.run("grantStarlightOnEquip(" in code,
              "%s S2-a %s#onEquip 未调用 `grantStarlightOnEquip(`" % (line, rel(bank_low_high)))
        g.run("grantStarlightFloorOnEquip" not in code,
              "%s S2-a %s#onEquip 仍在调用已删除的 `grantStarlightFloorOnEquip`"
              % (line, rel(bank_low_high)))
        g.run(not re.search(r"(?<![\w.])(4|7)(?![\w.])", code),
              "%s S2-a %s#onEquip 发放量写成了 4/7 字面量(必须共用常量)"
              % (line, rel(bank_low_high)))

    for path, label in ((unlimited, "S2-b"), (hammer, "S2-b")):
        body = body_for(g, path, r"public\s+void\s+onEquip\s*\(", "onEquip", label)
        if body is None:
            continue
        code = code_of(body)
        g.run("grantStarlightOnEquip(" in code,
              "%s %s %s#onEquip 未调用 `grantStarlightOnEquip(`" % (line, label, rel(path)))
        g.run("STARLIGHT_ON_EQUIP" in code,
              "%s %s %s#onEquip 未使用具名常量 `STARLIGHT_ON_EQUIP`"
              % (line, label, rel(path)))

    if not os.path.isfile(mod_items):
        g.run(None, "S2-c 文件不存在: %s" % rel(mod_items))
        return
    src = read(mod_items)
    low_stmt = decl_statement(src, r"\bBANK_CARD_LOW\s*=\s*registerItem\(")
    high_stmt = decl_statement(src, r"\bBANK_CARD_HIGH\s*=\s*registerItem\(")
    g.run(low_stmt is not None and "BankCardChipItem.STARLIGHT_LOW" in low_stmt,
          "%s S2-c ModItems 的 BANK_CARD_LOW 注册未传 `BankCardChipItem.STARLIGHT_LOW`" % line)
    g.run(high_stmt is not None and "BankCardChipItem.STARLIGHT_HIGH" in high_stmt,
          "%s S2-c ModItems 的 BANK_CARD_HIGH 注册未传 `BankCardChipItem.STARLIGHT_HIGH`" % line)


def check_s3_constants(g, line, base):
    chip_dir = os.path.join(base, "item", "chip")
    bank_low_high = os.path.join(chip_dir, "BankCardChipItem.java")
    unlimited = os.path.join(chip_dir, "BankCardUnlimitedChipItem.java")
    hammer = os.path.join(chip_dir, "StarCoinHammerChipItem.java")
    conversion = os.path.join(base, "resource", "ResourceConversion.java")

    print("[S3] 具名常量与字面值(%s)" % line)

    def const_of(path, pattern, expected, label):
        if not os.path.isfile(path):
            g.run(None, "%s 文件不存在: %s" % (label, rel(path)))
            return
        value = int_constant(code_of(read(path)), pattern)
        g.run(value == expected,
              "%s %s %s 期望 `= %d`,实际读到 %r"
              % (line, label, rel(path), expected, value))

    const_of(bank_low_high,
             r"public\s+static\s+final\s+int\s+STARLIGHT_LOW\s*=\s*(\d+)\s*;",
             SPEC["low"], "S3-a")
    const_of(bank_low_high,
             r"public\s+static\s+final\s+int\s+STARLIGHT_HIGH\s*=\s*(\d+)\s*;",
             SPEC["high"], "S3-a")
    const_of(unlimited,
             r"public\s+static\s+final\s+int\s+STARLIGHT_ON_EQUIP\s*=\s*(\d+)\s*;",
             SPEC["unlimited"], "S3-b")
    const_of(hammer,
             r"public\s+static\s+final\s+int\s+STARLIGHT_ON_EQUIP\s*=\s*(\d+)\s*;",
             SPEC["hammer"], "S3-c")
    const_of(conversion,
             r"public\s+static\s+final\s+int\s+STARLIGHT_PER_COIN\s*=\s*(\d+)\s*;",
             SPEC["per_coin"], "S3-e")

    for path in (bank_low_high, unlimited, hammer,
                 os.path.join(base, "item", "ModItems.java"),
                 os.path.join(base, "item", "StarLightManager.java")):
        if not os.path.isfile(path):
            g.run(None, "S3-d 文件不存在: %s" % rel(path))
            continue
        code = code_of(read(path))
        g.run(not re.search(r"\bBASE_LOW\b|\bBASE_HIGH\b", code),
              "%s S3-d %s 代码仍含旧常量名 BASE_LOW/BASE_HIGH(数值双源)"
              % (line, rel(path)))

    # 非致命:字段/取值器若仍叫 base*,是命名整洁问题,不阻断行为契约。
    code = code_of(read(bank_low_high)) if os.path.isfile(bank_low_high) else ""
    if re.search(r"\bbaseStarlight\b|\bgetBaseStarlight\b", code):
        g.warn("%s S3 提示 %s 仍有 base* 命名字段/取值器(计划要求改名,但不影响契约)"
               % (line, rel(bank_low_high)))


def check_s4_clamp(g, line, base):
    manager = os.path.join(base, "item", "StarLightManager.java")
    print("[S4] 值域钳制与消耗语义(%s)" % line)

    body = body_for(g, manager, r"public\s+static\s+void\s+set\s*\(", "set", "S4-a")
    if body is not None:
        code = code_of(body)
        g.run(
            re.search(
                r"Math\.max\(\s*0\s*,\s*Math\.min\(\s*[A-Za-z_]\w*\s*,\s*getCap\(\)\s*\)\s*\)",
                code,
            ) is not None,
            "%s S4-a %s#set 未写成 `Math.max(0, Math.min(<value>, getCap()))`"
            % (line, rel(manager)),
        )
        g.run("getBasePoints" not in code,
              "%s S4-a %s#set 仍有基础值钳制(getBasePoints)" % (line, rel(manager)))

    body = body_for(g, manager, r"public\s+static\s+int\s+spend\s*\(", "spend", "S4-b")
    if body is not None:
        code = code_of(body)
        g.run("getBasePoints" not in code,
              "%s S4-b %s#spend 仍有「低于基础值自动补回」逻辑" % (line, rel(manager)))
        writes_back = False
        for _start, _end, inner in iter_calls(code, "set"):
            args = split_top_level(inner)
            if len(args) != 2 or args[0] != "player":
                continue
            core = strip_safe_wrappers(args[1])
            if re.fullmatch(r"[A-Za-z_]\w*\s*-\s*[A-Za-z_]\w*", core):
                writes_back = True
        g.run(writes_back,
              "%s S4-b %s#spend 未按 `set(player, <当前值> - <实扣量>)` 扣除"
              "(允许 `Math.max(0, ...)` 等合法包裹)" % (line, rel(manager)))

    body = body_for(g, manager, r"public\s+static\s+int\s+add\s*\(", "add", "S4-c")
    if body is not None:
        code = code_of(body)
        g.run("Math.min(" in code and "getCap()" in code,
              "%s S4-c %s#add 未按 `Math.min(..., getCap())` 封顶" % (line, rel(manager)))


def check_s5_replenish(g, line, base):
    manager = os.path.join(base, "item", "StarLightManager.java")
    pattern = r"public\s+static\s+int\s+replenishOnExchange\s*\(\s*Player\s+\w+\s*\)"
    print("[S5] replenishOnExchange 形状(%s)" % line)
    body = body_for(g, manager, pattern, "`public static int replenishOnExchange(Player)`", "S5-a")
    if body is None:
        return
    code = code_of(body)

    for item in ("BANK_CARD_LOW", "BANK_CARD_HIGH", "BANK_CARD_UNLIMITED", "STAR_COIN_HAMMER"):
        g.run("ModItems.%s" % item in code,
              "%s S5-b %s#replenishOnExchange 未检查 `ModItems.%s`"
              % (line, rel(manager), item))

    for const in ("BankCardChipItem.STARLIGHT_LOW",
                  "BankCardChipItem.STARLIGHT_HIGH",
                  "BankCardUnlimitedChipItem.STARLIGHT_ON_EQUIP",
                  "StarCoinHammerChipItem.STARLIGHT_ON_EQUIP"):
        g.run(const in code,
              "%s S5-c %s#replenishOnExchange 未累加 `%s`(四件物品数值必须共用常量)"
              % (line, rel(manager), const))

    g.run(not re.search(r"(?<![\w.])(3|4|5|7)(?![\w.])", code),
          "%s S5-c %s#replenishOnExchange 出现 3/4/5/7 字面量(应共用常量)"
          % (line, rel(manager)))

    g.run(code.count("getCuriosInventory(") == 1,
          "%s S5-d %s#replenishOnExchange 未做到「Curios 只读一次」(读到 %d 次)"
          % (line, rel(manager), code.count("getCuriosInventory(")))
    # ⚠️ 存在性判定必须落在 `getCuriosInventory(...)` 的 Optional 上:`isEmpty()` 语义相反 ——
    # 写成 `if (curios.isEmpty()) { ...四件累加... }` 会让整段补充成为永不执行的死代码,
    # 而「Curios 只读一次」「四件常量都出现」这些字面检查仍然满足
    # (方法体里 `findFirstCurio(...).isPresent()` 的 isPresent 会把它掩盖掉)。
    m_opt = re.search(r"([A-Za-z_]\w*)\s*=\s*[^;]*getCuriosInventory\(", code)
    opt_var = m_opt.group(1) if m_opt else None
    if opt_var is not None:
        ev = re.escape(opt_var)
        present_ok = (re.search(r"\b%s\s*\.\s*isPresent\(\)" % ev, code) is not None
                      or re.search(r"!\s*%s\s*\.\s*isEmpty\(\)" % ev, code) is not None)
        g.run(present_ok,
              "%s S5-d %s#replenishOnExchange 未用 `%s.isPresent()`"
              "(或 `!%s.isEmpty()`)判定 Curios 存在"
              % (line, rel(manager), opt_var, opt_var))
        bare_isempty = False
        for m in re.finditer(r"\b%s\s*\.\s*isEmpty\(\)" % ev, code):
            if not code[max(0, m.start() - 3):m.start()].rstrip().endswith("!"):
                bare_isempty = True
        g.run(not bare_isempty,
              "%s S5-d %s#replenishOnExchange 出现未取反的 `%s.isEmpty()`"
              "(存在性判定反了 ⇒ 补充成死代码)" % (line, rel(manager), opt_var))
    else:
        g.run(False,
              "%s S5-d %s#replenishOnExchange 无法从 `getCuriosInventory(` 的赋值解出 Optional 变量"
              % (line, rel(manager)))

    # S5-e 必须绑定到「四件常量累加出来的实参」:只数 `add(` 会放过
    # `add(player, 0)`(补充量为 0)与 `return 0;`(不返回 add 结果)。
    add_calls = list(iter_calls(code, "add"))
    ret_stmts = return_statements(code)
    total_var = None
    g.run(len(add_calls) == 1,
          "%s S5-e %s#replenishOnExchange 应恰好调用一次 `add(`(实际 %d 次)"
          % (line, rel(manager), len(add_calls)))
    if len(add_calls) == 1:
        args = split_top_level(add_calls[0][2])
        if len(args) == 2 and args[0] == "player" and re.fullmatch(r"[A-Za-z_]\w*", args[1]):
            total_var = args[1]
        g.run(total_var is not None,
              "%s S5-e %s#replenishOnExchange 的 `add(...)` 实参不是「玩家 + 累加变量」"
              "(写成 0 等常量等于不补充)" % (line, rel(manager)))
    ret_add = (re.fullmatch(r"add\(\s*player\s*,\s*([A-Za-z_]\w*)\s*\)",
                            ret_stmts[0][1].strip())
               if len(ret_stmts) == 1 else None)
    g.run(ret_add is not None,
          "%s S5-e %s#replenishOnExchange 未「唯一 return 就是 add(...) 的结果」"
          "(`return 0;` 之类不算补充)" % (line, rel(manager)))
    if ret_add is not None and total_var is not None:
        g.run(ret_add.group(1) == total_var,
              "%s S5-e %s#replenishOnExchange 返回的 add 实参与累加变量不一致(`%s` vs `%s`)"
              % (line, rel(manager), ret_add.group(1), total_var))
    if total_var is not None:
        for const in ("BankCardChipItem.STARLIGHT_LOW",
                      "BankCardChipItem.STARLIGHT_HIGH",
                      "BankCardUnlimitedChipItem.STARLIGHT_ON_EQUIP",
                      "StarCoinHammerChipItem.STARLIGHT_ON_EQUIP"):
            g.run(re.search(r"\b%s\s*\+=\s*[^;]*%s"
                            % (re.escape(total_var), re.escape(const)), code) is not None,
                  "%s S5-e %s#replenishOnExchange 未把 `%s` 累加进传给 add 的 `%s`"
                  % (line, rel(manager), const, total_var))

    for tok in ("claimEquipGrant", "setGrantedStarlight", "grantedStarlight",
                "revokeStarlightOnUnequip", "EquipGrant"):
        g.run(tok not in code,
              "%s S5-f %s#replenishOnExchange 触碰了装备账本/闸门 `%s`"
              % (line, rel(manager), tok))
    g.run("ModAttachments.setStarlight" not in code,
          "%s S5-f %s#replenishOnExchange 直接写 `ModAttachments.setStarlight`(应走 add)"
          % (line, rel(manager)))


def check_s6_conversion(g, line, base):
    conversion = os.path.join(base, "resource", "ResourceConversion.java")
    menu = os.path.join(base, "shop", "CardShopMenu.java")
    print("[S6] 兑换顺序(%s)" % line)

    body = body_for(g, conversion, r"public\s+static\s+int\s+starlightToStarCoins\s*\(",
                    "starlightToStarCoins", "S6")
    if body is None:
        return
    code = code_of(body)

    g.run("getBasePoints" not in code,
          "%s S6-a %s#starlightToStarCoins 仍在扣减基础值(可支配量 != 当前值)"
          % (line, rel(conversion)))
    g.run(re.search(r"StarLightManager\.get\(\s*player\s*\)\s*-", code) is None,
          "%s S6-a %s#starlightToStarCoins 对当前星光做了减法"
          % (line, rel(conversion)))
    g.run("StarLightManager.get(player)" in code,
          "%s S6-a %s#starlightToStarCoins 未以 `StarLightManager.get(player)` 作为可兑换量"
          % (line, rel(conversion)))
    g.run("STARLIGHT_PER_COIN" in code,
          "%s S6-a %s#starlightToStarCoins 未使用 `STARLIGHT_PER_COIN` 比例"
          % (line, rel(conversion)))

    spend_calls = list(iter_calls(code, "spend"))
    refill_calls = list(iter_calls(code, "replenishOnExchange"))
    reads = [m.start() for m in re.finditer(
        r"StarLightManager\.get\(\s*player\s*\)", code)]

    idx_spend = spend_calls[0][0] if spend_calls else -1
    idx_refill = refill_calls[0][0] if refill_calls else -1
    idx_read = next((i for i in reads if i > idx_spend), -1)

    g.run(idx_spend >= 0,
          "%s S6-b %s#starlightToStarCoins 缺少 `spend(...)` 调用"
          % (line, rel(conversion)))
    g.run(idx_read > idx_spend,
          "%s S6-b %s#starlightToStarCoins 未在 spend 之后读取当前星光以计算实扣量"
          % (line, rel(conversion)))
    g.run(idx_refill > idx_read,
          "%s S6-b %s#starlightToStarCoins 顺序错误(spend=%d, read=%d, refill=%d;"
          "应为 spend < 实扣量读取 < replenishOnExchange)"
          % (line, rel(conversion), idx_spend, idx_read, idx_refill))
    g.run(len(refill_calls) == 1,
          "%s S6-c %s#starlightToStarCoins 的 replenishOnExchange 应恰好调用一次(实际 %d 次)"
          % (line, rel(conversion), len(refill_calls)))

    owners = ([b for b in if_blocks(code) if b[1] <= idx_refill < b[2]]
              if idx_refill >= 0 else [])

    # 实扣量变量:必须由「spend 之后的当前值」与减法共同算出。
    # 只要求「某个 > 0 的条件」会放过 `if (coins > 0)` 这类早退闸门/任意标志量。
    gained_vars = set()
    for m_assign in re.finditer(r"(?<![\w.])([A-Za-z_]\w*)\s*=(?!=)([^;]*);", code):
        if m_assign.start() <= idx_spend:
            continue
        rhs = m_assign.group(2)
        if "StarLightManager.get(" not in rhs or re.search(r"\w\s*-\s*", rhs) is None:
            continue
        gained_vars.add(m_assign.group(1))
    g.run(bool(gained_vars),
          "%s S6-c %s#starlightToStarCoins 未由「spend 后的当前值 − 扣除量」算出实扣量变量"
          % (line, rel(conversion)))

    # 常量假分支包住补充 ⇒ 死代码,整段补充实际永不执行。
    dead_owners = [b for b in owners if is_statically_false(b[0])]
    g.run(not dead_owners,
          "%s S6-c %s#starlightToStarCoins 的 replenishOnExchange 被常量假分支包住 ⇒ 死代码"
          "(%s)" % (line, rel(conversion),
                    "; ".join("if (%s)" % " ".join(b[0].split()) for b in dead_owners) or "-"))

    cond_pat = None
    if gained_vars:
        alt = "|".join(sorted(re.escape(v) for v in gained_vars))
        cond_pat = r"(?:\b(?:%s)\b\s*>\s*0\b|\b0\s*<\s*(?:%s)\b)" % (alt, alt)
    qualifying = [b for b in owners
                  if b[1] > idx_read
                  and cond_pat is not None
                  and not is_statically_false(b[0])
                  and re.search(cond_pat, b[0])]
    g.run(bool(qualifying),
          "%s S6-c %s#starlightToStarCoins 的 replenishOnExchange 不在"
          "`if (<由实际扣减算出的 `%s`> > 0)` 块内(该 if 必须晚于 spend 后的读取;"
          "条件里的变量必须由实扣量算出,不能是任意 >0 变量)"
          % (line, rel(conversion), "/".join(sorted(gained_vars)) or "gained"))
    if qualifying:
        g.run("giveItem(" in qualifying[-1][3],
              "%s S6-c %s#starlightToStarCoins 的补充闸门丢了 giveItem"
              % (line, rel(conversion)))

    if os.path.isfile(menu):
        g.run("starlightToStarCoins(exchanger, -1)" in code_of(read(menu)),
              "%s S6-d %s 未以 `-1`(全量可兑换)调用兑换(数值表前提被破坏)"
              % (line, rel(menu)))
    else:
        g.run(None, "S6-d 文件不存在: %s" % rel(menu))


def check_s7_client(g, line, base):
    screen = os.path.join(base, "screen", "CardShopScreen.java")
    print("[S7] 客户端口径(%s)" % line)

    def current_value_sources(code):
        """starlightState 里由当前星光赋值的局部变量名。"""
        return set(re.findall(
            r"([A-Za-z_]\w*)\s*=\s*StarLightManager\.get\(\s*this\.minecraft\.player\s*\)",
            code))

    def value_ok(tok, sources):
        if re.fullmatch(r"StarLightManager\.get\(\s*this\.minecraft\.player\s*\)", tok):
            return True
        return tok in sources

    def base_zero_ok(expr):
        """base 段必须是常量 0:要么写在同一字符串里,要么 `+ 0` 拼接。"""
        if re.search(r"base=0(?![\w.])", expr):
            return True
        return re.search(r'base="\s*\+\s*0\s*(?=\+|$)', expr) is not None

    def spendable_ok(expr, sources):
        """spendable 段必须直接拼接当前值(允许 Math.max(0, ...) 包裹),不得再做减法。"""
        pats = (
            r'spendable="\s*\+\s*(?P<v>StarLightManager\.get\(\s*this\.minecraft\.player\s*\)'
            r'|[A-Za-z_]\w*)\s*(?=\+|$)',
            r'spendable="\s*\+\s*Math\.max\(\s*0\s*,\s*(?P<v>'
            r'StarLightManager\.get\(\s*this\.minecraft\.player\s*\)'
            r'|[A-Za-z_]\w*)\s*\)\s*(?=\+|$)',
        )
        for pat in pats:
            for m in re.finditer(pat, expr):
                if value_ok(m.group("v"), sources):
                    return True
        return False

    body = body_for(g, screen, r"private\s+boolean\s+canExchange\s*\(\s*\)",
                    "canExchange", "S7-a")
    if body is not None:
        code = code_of(body)
        g.run("getBasePoints" not in code and
              re.search(r"StarLightManager\.get\(\s*this\.minecraft\.player\s*\)\s*-", code) is None,
              "%s S7-a %s#canExchange 仍排除基础值(未使用完整当前值)"
              % (line, rel(screen)))
        g.run("StarLightManager.get(this.minecraft.player)" in code,
              "%s S7-a %s#canExchange 未读取完整当前星光" % (line, rel(screen)))
        g.run("ResourceConversion.STARLIGHT_PER_COIN" in code,
              "%s S7-a %s#canExchange 未与 `STARLIGHT_PER_COIN` 比较"
              % (line, rel(screen)))

    body = body_for(g, screen, r"public\s+String\s+starlightState\s*\(\s*\)",
                    "starlightState", "S7-b")
    if body is not None:
        # ⚠️ 只看去注释后的代码:注释里写 `base=0` / `spendable=` 不算输出。
        code = code_of(body)
        sources = current_value_sources(code)
        exprs = return_expressions(code)
        stmts = return_statements(code)
        blocks = if_blocks(code)
        g.run("getBasePoints" not in code,
              "%s S7-b %s#starlightState 仍读取基础值" % (line, rel(screen)))
        g.run(any(base_zero_ok(e) for e in exprs),
              "%s S7-b %s#starlightState 的 return 字符串未输出常量 `base=0`(探针兼容字段)"
              % (line, rel(screen)))
        g.run(any(spendable_ok(e, sources) for e in exprs),
              "%s S7-b %s#starlightState 的 `spendable` 段未直接拼接当前星光"
              % (line, rel(screen)))

        # ⚠️ 上面的两条只要求「某条 return」满足:在方法开头插一句
        # `if (false) { return "get=0:base=0:spendable=0..."; }` 就能同时满足,
        # 而真实就绪路径仍可以输出 `base=-1`。故补两条结构判定:
        #   ① 任何常量假分支里的 return 都不得充当证据(死返回);
        #   ② `base=0` 与 `spendable=<当前值>` 必须出自**同一条**、位于方法末尾的
        #      无条件 return(未就绪哨兵 `-1` 是它之前的那条条件返回)。
        dead_returns = [s for s in stmts
                        if any(b[1] <= s[0] < b[2] and is_statically_false(b[0])
                               for b in blocks)]
        g.run(not dead_returns,
              "%s S7-b %s#starlightState 存在常量假分支里的死 `return`(不能用来凑 base=0)"
              % (line, rel(screen)))
        ready_idx, ready = stmts[-1] if stmts else (-1, "")
        ready_conditional = any(b[1] <= ready_idx < b[2] for b in blocks)
        g.run(bool(stmts) and not ready_conditional,
              "%s S7-b %s#starlightState 末尾的就绪路径 return 落在 if 分支内"
              "(条件返回不能充当就绪路径)" % (line, rel(screen)))
        g.run(base_zero_ok(ready) and spendable_ok(ready, sources),
              "%s S7-b %s#starlightState 同一条就绪 return 必须同时输出常量 `base=0` 与"
              "直接拼接的当前星光" % (line, rel(screen)))


def check_s8_unequip(g, line, base):
    chip_dir = os.path.join(base, "item", "chip")
    manager = os.path.join(base, "item", "StarLightManager.java")
    print("[S8] 卸下扣回保留(%s)" % line)

    bank = os.path.join(chip_dir, "BankCardChipItem.java")
    body = body_for(g, bank, r"protected\s+void\s+onChipUnequip\s*\(", "onChipUnequip", "S8-a")
    if body is not None:
        code = code_of(body)
        g.run("revokeStarlightOnUnequip(" in code,
              "%s S8-a %s#onChipUnequip 未调用 `revokeStarlightOnUnequip`(卸下扣回被删)"
              % (line, rel(bank)))
    class_code = code_of(read(bank)) if os.path.isfile(bank) else ""
    g.run("GRANT_BIT_BANK_CARD_LOW" in class_code and "GRANT_BIT_BANK_CARD_HIGH" in class_code,
          "%s S8-a %s 未保留 low/high 两个账本位" % (line, rel(bank)))

    for filename, bit in (("BankCardUnlimitedChipItem.java", "GRANT_BIT_BANK_CARD_UNLIMITED"),
                          ("StarCoinHammerChipItem.java", "GRANT_BIT_STAR_COIN_HAMMER")):
        path = os.path.join(chip_dir, filename)
        body = body_for(g, path, r"protected\s+void\s+onChipUnequip\s*\(",
                        "onChipUnequip", "S8-a")
        if body is None:
            continue
        code = code_of(body)
        g.run("revokeStarlightOnUnequip(" in code and bit in code,
              "%s S8-a %s#onChipUnequip 未按 `%s` 扣回" % (line, rel(path), bit))

    body = body_for(g, manager, r"public\s+static\s+void\s+revokeStarlightOnUnequip\s*\(",
                    "revokeStarlightOnUnequip", "S8-b")
    if body is not None:
        code = code_of(body)
        g.run("setGrantedStarlight(player, bit, 0)" in code,
              "%s S8-b %s#revokeStarlightOnUnequip 未清空账本" % (line, rel(manager)))
        g.run("releaseEquipGrant(player, bit)" in code,
              "%s S8-b %s#revokeStarlightOnUnequip 未释放装备闸门" % (line, rel(manager)))
        g.run(re.search(
            r"ModAttachments\.setStarlight\(\s*player\s*,\s*Math\.max\(\s*0\s*,\s*get\(player\)\s*-\s*\w+\s*\)\s*\)",
            code) is not None,
            "%s S8-b %s#revokeStarlightOnUnequip 未按实际账本量直接扣回(不得走 set)"
            % (line, rel(manager)))
        g.run("replenishOnExchange" not in code,
              "%s S8-b %s#revokeStarlightOnUnequip 误调兑换补充(会把补充也扣掉)"
              % (line, rel(manager)))

    body = body_for(g, manager, r"public\s+static\s+int\s+grantStarlightOnEquip\s*\(",
                    "grantStarlightOnEquip", "S8-c")
    if body is not None:
        code = code_of(body)
        g.run(all(tok in code for tok in ("claimEquipGrant(", "add(", "setGrantedStarlight(")),
              "%s S8-c %s#grantStarlightOnEquip 不再是「闸门 + add + 实际量记账」"
              % (line, rel(manager)))


def check_n1_numeric(g):
    print("[N1] 手算数值表(期望值为字面量)")
    for label, start, equipped, want_coins, want_final in NUMERIC_ROWS:
        coins, final, gained = spec_exchange(start, equipped)
        ok = (coins == want_coins and final == want_final)
        g.run(ok,
              "N1 %s: 期望 %d 币 / 终值 %d,契约模型得 %d 币 / 终值 %d"
              % (label, want_coins, want_final, coins, final))
        # 期望终值必须落在 0..cap(规格硬约束)
        g.run(0 <= want_final <= SPEC["cap"],
              "N1 %s: 期望终值 %d 越出 0..%d" % (label, want_final, SPEC["cap"]))
        if VERBOSE:
            print("     %-26s start=%-2d equip=%-12s coins=%d final=%d gained=%d"
                  % (label, start, str(equipped), coins, final, gained))

    # 明确「补充不会越过上限」的纸面证明:余数 ≤ 1,四件之和 19 ⇒ 20 < 32。
    # 也就是说 cap 只在起始值钳制时生效;这里断言该前提,防止有人把常量改大后无声越界。
    max_remainder = 1
    max_equip = SPEC["low"] + SPEC["high"] + SPEC["unlimited"] + SPEC["hammer"]
    g.run(max_remainder + max_equip <= SPEC["cap"],
          "N1 补充上限前提被破坏:余数 %d + 装备值 %d > cap %d"
          % (max_remainder, max_equip, SPEC["cap"]))
    g.run(max_equip == 19,
          "N1 四件装备值之和期望 19(4+7+3+5),实际 %d" % max_equip)


# ──────────────────────────── 主流程 ────────────────────────────


def main():
    g = Gate()
    print("starlight exchange/refill gate  ROOT=%s" % ROOT)
    print("P0 lines: %s   cap=%d  per_coin=%d" % (", ".join(LINES), SPEC["cap"], SPEC["per_coin"]))
    print()

    for line in LINES:
        base = os.path.join(ROOT, line, "src", "main", "java", PKG)
        files = java_files(line)
        if not files:
            g.run(None, "无法枚举 %s 的 Java 源文件(目录缺失?)" % line)
            continue
        check_s1_floor_removed(g, line, files)
        check_s2_equip_grants(g, line, base)
        check_s3_constants(g, line, base)
        check_s4_clamp(g, line, base)
        check_s5_replenish(g, line, base)
        check_s6_conversion(g, line, base)
        check_s7_client(g, line, base)
        check_s8_unequip(g, line, base)
        print()

    check_n1_numeric(g)

    print()
    print("NOTE: 本门只做静态取证 + 纸面数值契约,不启动游戏、不跑 Gradle ⇒ 不能证明运行时行为。")
    if g.warns:
        print("WARN: %d 项(不阻断)" % len(g.warns))
    if g.fails:
        print("GATE: FAIL  (%d 项不满足 / 共 %d 项)" % (len(g.fails), g.checks))
        for f in g.fails:
            print("  - " + f)
        return 1
    if g.unknowns:
        print("GATE: UNKNOWN  (%d 项无法判定 / 共 %d 项)" % (len(g.unknowns), g.checks))
        for u in g.unknowns:
            print("  - " + u)
        return 2
    print("GATE: PASS  (%d 项全部满足)" % g.checks)
    return 0


if __name__ == "__main__":
    sys.exit(main())
