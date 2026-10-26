#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""四张立牌（多萝西 / 调酒师 / 大肥鱼 / 骇客）P0 静态守门。

## 覆盖范围
只查 P0 两线：`neoforge-1.21.1` / `forge-1.20.1`。P1（`neoforge-26.1.2` /
`fabric-1.20.1`）明确**不在本任务范围，不读取、不判据**。

## 四组判据（`--only <组>` 可单独运行）
- `dorothy`：
  * `NewSignEvents` 存在 `LivingHealEvent` + `EventPriority.LOWEST` 监听；
  * 监听体内有服务端 / 存活 / 已取消 / 有效治疗量 / 未满血 / 佩戴过滤；
  * 监听体内读 `DorothyWarmthHealContext.current()`，并把该返回值的 `.level()` 用进加层，且调用 `grantWarmth`；
  * `grantWarmth` 仍以 `MAX_STACKS` + `Math.min` 封顶 5 层；
  * 不再存在 `MobEffectEvent.Added` 监听调用 `grantWarmth`（旧加层路径）；
  * 治疗来源上下文 `DorothyWarmthHealContext` 有 ThreadLocal 栈与 push/current/pop；
  * 瞬时治疗 / 生命恢复两个 Mixin 均存在、HEAD push + RETURN pop、`amplifier + 1`：
    1.21.1 用精确 `targets="net.minecraft.world.effect.HealOrHarmMobEffect"` /
    `…RegenerationMobEffect`（类包私有）；1.20.1 用 `MobEffect` 目标，并在
    `applyInstantenousEffect` / `applyEffectTick` 内以 `(Object)this == MobEffects.HEAL` /
    `MobEffects.REGENERATION` 单例身份守卫包住 push/pop（不得用 InstantenousMobEffect 目标）；
    两个 Mixin 类都登记在 `astral_dice.mixins.json` 的 `mixins` 数组（defaultRequire=1）；
  * 三语 tooltip/guide 写“瞬间治疗 / 生命恢复（Instant Health / Regeneration）”口径与
    5 层上限；旧“获得治疗效果”语义与旧组合“恢复生命值时获得 1 层「温暖」（按效果等级计算…）”
    不得复活（不禁止准确的“恢复生命值时获得 1 层”），en/ja 旧文案 Gaining a healing effect /
    回復効果を得ると 消失。
  * 【交际花】状态效果（DOR-4，2026-10-25 用户裁决）：队友那 1 点攻击力改由
    `DorothySocialiteEffect` 自带的 ATTACK_DAMAGE 修饰符承载，`DURATION_TICKS = 1200`
    （固定 1:00、与主动窗口解耦）；`ModEffects` 注册 `dorothy_socialite`；两处 `applySocialite(other)`；
    `tryTriggerDorothyPartner` 只保留 (player, uuid) 两参（10 秒节流已删）；旧
    `DOROTHY_THROTTLE_TICKS` / `dorothy_partner_attack` / `applyTempAttack` /
    `clearPartnerBuffs` / `DOROTHY_PARTNERS` / `dorothyPartnerUuids` 不得复活；图标必须存在。
- `jill`：
  * `resolve` 用 `HEAL_AMOUNT = 2.0F` **逐张各结算 1 次**：
    `for (int i = 0; i < effect; i++) target.heal(HEAL_AMOUNT);`
    （2026-10-26 用户裁决：每张 2 点、3 张 = 3 次治疗 = 6 点；治疗事件按次数各触发 ⇒ 多萝西「温暖」
    按张数叠层。**不得**合并成一次 `target.heal(HEAL_AMOUNT * effect)`），且不再施加 REGENERATION；
  * `boolean allEffect = effect == 3` 驱动 `JILL_MIXED`、迅捷、buff 计时、星币四类分支；
  * `JILL_MIXED` 受 `!allEffect` 守卫；迅捷受 `allSame && !allEffect` 守卫；
    星币受 `!allSame` 守卫；目标/结束刻只在 `attack + defense > 0` 时写；
  * 非全效果牌路径（攻/防修饰符、随机 3 张牌、冷却）保留；
  * 三语主动/被动/手册写「每张效果牌 2 点、按张数叠加」、全效果牌无计时器/图标且不给迅捷/星币，
    旧“生命恢复（等级 = 效果牌数量）”与“三张卡牌全部同类型…迅捷”文案消失。
- `deepseek`：
  * 主动：`nextInt(20) + 1` 掷 1..20，`Math.min(roll, getBalance(...))` 夹取实际消耗，
    余额 0 / 扣款失败直接返回，`setAttackPowerBonus` / `setDefenseArmorBonus` 用实际值，
    `BUFF_TICKS = 2400`，`CardCategory.ALL` 发 3 张；
  * 旧 `deposit(...100)` 与 `randomReward` 消失；
  * 被动：`IDLE_TICKS = 200`、`MOVE_LIMIT_TICKS = 100`、精确位置快照（不再 blockPosition）、
    获得摸鱼时清附近已有仇恨；
  * `tickBuff` 到期清 `deepseek_buff_attack` / `deepseek_buff_armor` 并归零结束刻，
    两线 `PlayerTickEvents` 调用；卸下也清理；
  * `ModAttachments` 有 `get/setDeepseekLastPos`（List<Double>）、`get/setDeepseekMoveTicks`
    （int）、`get/setDeepseekBuffEnd`（long）、`get/setDeepseekLastTick`（long）；
  * Curio tick 中断检测：`lastTick > 0 && now - lastTick > 1` ⇒ 重置静止起点 / 移动累计 /
    位置快照（摘骰子后同一坐标重戴不得沿用旧静止时间），每 tick 回写 lastTick、卸下清零；
  * 四个大肥鱼新键（last_pos / move_ticks / last_tick / buff_end）**仅内存、不 sync、不持久化**
    （与瞬态攻/防属性修饰符同寿命；重登/死亡不保留倒计时）；
  * 三语主动写随机 1–20、实际消耗等量攻防、2:00、随机 3 牌；被动写静止 10 秒与
    移动累计 5 秒解除；旧 100 星币 / 随机 1 立牌或筹码 / “移动后立即清除”文案消失。
- `nancy`：
  * `PASSIVE_RANGE = 3.0`、`ACTIVE_ATTACK_BONUS = 6`、`ACTIVE_DURATION_TICKS = 2400`；
  * `onAttackWhileHidden` 保留锁定 + 隐身检查与破隐，直接写固定 +6 / 2400 tick；
  * 删除 `findAndConsumeRandomBattleCard`、费用倍率/保底与 `CardRegistry` 消耗；
  * 保留被动 `RandomCardHandler.giveCardTo(..., BATTLE)` 与末影珍珠逻辑；
  * 三语主动/手册写攻击敌对目标破隐后 +6 / 2:00，被动 6 格改 3 格，
    actionbar 写敌对目标且不含 `§`；旧 ×2 / 最低 +2 / 消耗战斗牌 / 6 格 / 任意目标文案消失；
  * `docs/agent/AGENTS-full.md` 的「当前骇客规格行」（`| 骇客 nancy_lu |` 开头的表格行）
    与实现一致：固定 +6 / 2:00 / 敌对目标 / 被动 3 格，旧 ×2 / 最低 +2 / 消耗随机战斗牌 /
    6 格 / 「或玩家」不得残留（历史条目不在本判据范围内）。

## 退出码
0 = 全选组 PASS；1 = 至少一条判据 FAIL；2 = 文件/用法错误（无法判定）。
文件/读取失败（unresolved）优先于普通 FAIL：即使同时有 FAIL 也以 2 退出。
argparse 对非法 `--only` 值直接以 2 退出并打印 usage。

## 本守门证明不了什么
静态结构 + 文案启发式：能拦“只改一半 / 改回旧形态 / 两线漏改 / 只改 tooltip 漏改 guide”
这类回归，不能证明游戏内治疗计数、扣款、移动计时、破隐奖励真的按预期运行（需实机或单测）。
"""

import argparse
import json
import os
import re
import sys


# --- stdout/stderr 编码自愈（与 tools/verify_misaki_balance.py 同口径）---
def _astral_heal_stdio() -> None:  # pragma: no cover - 环境适配
    for _stream_name in ("stdout", "stderr"):
        _stream = getattr(sys, _stream_name, None)
        _reconf = getattr(_stream, "reconfigure", None)
        if _reconf is None:
            continue
        try:
            _reconf(encoding="utf-8", errors="replace")
        except Exception as _exc:  # noqa: BLE001 - 明确告警，绝不静默
            sys.stderr.write(
                "[astral-gate] WARN: %s.reconfigure(utf-8) failed: %r\n"
                % (_stream_name, _exc)
            )


_astral_heal_stdio()

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# P0 对等两线；P1 明确不在本任务范围。
LINES = ("neoforge-1.21.1", "forge-1.20.1")
GROUPS = ("dorothy", "jill", "deepseek", "nancy")
LANGS = ("zh_cn", "en_us", "ja_jp")

JAVA_REL = os.path.join(
    "src", "main", "java", "com", "merlinkitsune", "astral_dice")
RES_REL = os.path.join("src", "main", "resources")
LANG_REL = os.path.join(RES_REL, "assets", "astral_dice", "lang")
MIXIN_CONFIG_REL = os.path.join(RES_REL, "astral_dice.mixins.json")

EVENT_REL = os.path.join(JAVA_REL, "event")
SIGN_REL = os.path.join(JAVA_REL, "item", "sign")
MIXIN_REL = os.path.join(JAVA_REL, "mixin")
COMPONENT_REL = os.path.join(JAVA_REL, "component")

DOROTHY_EVENTS = os.path.join(EVENT_REL, "NewSignEvents.java")
DOROTHY_CONTEXT = os.path.join(EVENT_REL, "DorothyWarmthHealContext.java")
JILL_ITEM = os.path.join(SIGN_REL, "JillSignItem.java")
DEEPSEEK_ITEM = os.path.join(SIGN_REL, "DeepseekSignItem.java")
NANCY_ITEM = os.path.join(SIGN_REL, "NancyLuSignItem.java")
ATTACHMENTS = os.path.join(COMPONENT_REL, "ModAttachments.java")
TICK_EVENTS = os.path.join(EVENT_REL, "PlayerTickEvents.java")


# ---------------------------------------------------------------------------
# 基础读写 / Java 结构抽取
# ---------------------------------------------------------------------------
def read_bytes(path):
    with open(path, "rb") as fh:
        return fh.read()


def read_text(path):
    return read_bytes(path).decode("utf-8", "replace")


def strip_comments(src):
    """去掉行/块注释，保留字符串字面量（判据统一在剥注释后的文本上跑）。"""
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if c == '"' or c == "'":
            q = c
            out.append(c)
            i += 1
            while i < n:
                if src[i] == "\\":
                    out.append(src[i:i + 2])
                    i += 2
                    continue
                out.append(src[i])
                if src[i] == q:
                    i += 1
                    break
                i += 1
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            if j < 0:
                break
            i = j
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i)
            if j < 0:
                break
            i = j + 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def matching_close(src, open_idx):
    """open_idx 指向 `{`，返回配对的闭括号下标；失败返回 None。"""
    depth = 0
    j = open_idx
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
            j = src.find("\n", j)
            if j < 0:
                return None
            continue
        elif c == "/" and j + 1 < n and src[j + 1] == "*":
            j = src.find("*/", j)
            if j < 0:
                return None
            j += 1
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return j
        j += 1
    return None


# Java 方法声明（只覆盖带 public/private/protected 的常规方法；本守门目标方法均符合）。
METHOD_RX = re.compile(
    r"\b(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?"
    r"[A-Za-z_][\w.$<>\[\], ?]*?\s+"
    r"([A-Za-z_$][\w$]*)\s*\(([^()]*)\)\s*(?:throws\s+[^{]+)?\{")


def iter_methods(src):
    """产出 {name, params, body, prefix, start}；prefix 为声明前的注解窗口。"""
    for m in METHOD_RX.finditer(src):
        open_idx = m.end() - 1
        close_idx = matching_close(src, open_idx)
        if close_idx is None:
            continue
        cut = max(
            src.rfind(";", 0, m.start()),
            src.rfind("}", 0, m.start()),
            src.rfind("{", 0, m.start()),
        )
        yield {
            "name": m.group(1),
            "params": m.group(2),
            "body": src[open_idx:close_idx + 1],
            "prefix": src[cut + 1:m.start()],
            "start": m.start(),
        }


def find_method(src, name, param_kw=None):
    for meth in iter_methods(src):
        if meth["name"] != name:
            continue
        if param_kw is not None and param_kw not in meth["params"]:
            continue
        return meth
    return None


def guarded_scope(src, cond_rx):
    """返回条件语句支配的代码片：有 `{}` 取配对块，无括号取到下一个 `;`。"""
    m = cond_rx.search(src)
    if not m:
        return None
    i = m.end()
    n = len(src)
    while i < n and src[i] in " \t\r\n":
        i += 1
    if i < n and src[i] == "{":
        j = matching_close(src, i)
        return src[i:j + 1] if j is not None else None
    j = src.find(";", i)
    if j < 0:
        return src[i:i + 400]
    return src[i:j + 1]


def find_if_block_containing(src, token_rx):
    """找到包含 token 的条件语句所支配的 `{...}` 块。

    支持 `if (token)` 与 `if (a || token)` 两种写法：从 token 向前找最近的
    `if`，再取条件后的第一个 `{`；条件内出现 `;` 说明不是同一条 if，跳过。
    """
    for m in token_rx.finditer(src):
        pos = m.start()
        if_pos = src.rfind("if", 0, pos)
        if if_pos < 0:
            continue
        brace = src.find("{", pos)
        if brace < 0:
            continue
        if ";" in src[pos:brace]:
            continue
        end = matching_close(src, brace)
        if end is None:
            continue
        return src[brace:end + 1], if_pos
    return None, None


def key_statement(src, pattern):
    """返回附件键注册那条语句（从匹配处到下一个 `;`），找不到返回 None。

    用来判「仅内存 / 不 sync」这类链式调用语义：只看这一条注册语句，
    不会把同文件其它键的 `.serialize(...)` / `.inMemory()` 误当成本键的属性。
    """
    m = re.search(pattern, src)
    if not m:
        return None
    end = src.find(";", m.end())
    if end < 0:
        return None
    return src[m.start():end + 1]


def count_rx(text, pattern, flags=0):
    return len(re.findall(pattern, text, flags))


# ---------------------------------------------------------------------------
# 结果收集
# ---------------------------------------------------------------------------
class Results(object):
    def __init__(self, verbose=False):
        self.verbose = verbose
        self.fails = []
        self.unresolved = []
        self.checks = 0

    def run(self, ok, msg=""):
        self.checks += 1
        if ok is None:
            self.unresolved.append(msg)
            print("  ?? " + msg)
        elif ok:
            if self.verbose and msg:
                print("    ok " + msg)
        else:
            self.fails.append(msg)
            print("  FAIL " + msg)


def require(res, text, pattern, code, line, label, flags=re.S):
    if re.search(pattern, text, flags):
        res.run(True, "%s [%s] %s" % (code, line, label))
        return True
    res.run(False, "%s [%s] 缺少%s" % (code, line, label))
    return False


def forbid(res, text, pattern, code, line, label, flags=re.S):
    if re.search(pattern, text, flags):
        res.run(False, "%s [%s] %s" % (code, line, label))
        return False
    res.run(True, "%s [%s] 无%s" % (code, line, label))
    return True


# ---------------------------------------------------------------------------
# Mixin 发现 / 注册检查
# ---------------------------------------------------------------------------
MIXIN_DECL_RX = re.compile(r"@Mixin\s*\(([^)]*)\)", re.S)
MIXIN_CLASS_LITERAL_RX = re.compile(r"\b([A-Za-z_][\w.]*)\.class")
MIXIN_TARGETS_STRING_RX = re.compile(r'\btargets\s*=\s*"([^"]+)"')
CLASS_DECL_RX = re.compile(
    r"\b(?:public\s+)?(?:abstract\s+)?class\s+([A-Za-z_]\w*)")


def parse_mixin_target(src):
    """解析 @Mixin 参数：targets 字符串优先，其次类字面量。"""
    m = MIXIN_DECL_RX.search(src)
    if not m:
        return None
    args = m.group(1)
    targets = MIXIN_TARGETS_STRING_RX.search(args)
    if targets:
        value = targets.group(1)
        return {"kind": "targets", "value": value,
                "display": 'targets="%s"' % value}
    cls = MIXIN_CLASS_LITERAL_RX.search(args)
    if cls:
        value = cls.group(1)
        return {"kind": "class", "value": value,
                "display": "%s.class" % value}
    return None


def find_mixin(line, accept_target, content_rx=None):
    """按 target 接受函数 + 可选内容前置过滤发现 Mixin（递归子目录）。"""
    mixin_dir = os.path.join(ROOT, line, MIXIN_REL)
    if not os.path.isdir(mixin_dir):
        return None
    for dirpath, dirnames, filenames in os.walk(mixin_dir):
        dirnames.sort()
        for fname in sorted(filenames):
            if not fname.endswith(".java"):
                continue
            path = os.path.join(dirpath, fname)
            src = strip_comments(read_text(path))
            parsed = parse_mixin_target(src)
            if parsed is None or not accept_target(parsed):
                continue
            if content_rx is not None and not re.search(content_rx, src, re.S):
                continue
            cm = CLASS_DECL_RX.search(src)
            return {
                "file": os.path.relpath(path, os.path.join(ROOT, line)),
                "path": path,
                "src": src,
                "target": parsed["display"],
                "target_kind": parsed["kind"],
                "target_value": parsed["value"],
                "class": cm.group(1) if cm else os.path.splitext(fname)[0],
            }
    return None


def load_mixin_config(line, res):
    rel = MIXIN_CONFIG_REL
    path = os.path.join(ROOT, line, rel)
    if not os.path.isfile(path):
        res.run(None, "MIXIN [%s] 文件不存在 %s" % (line, rel))
        return None
    try:
        return json.loads(read_bytes(path).decode("utf-8-sig"))
    except Exception as exc:  # noqa: BLE001 - 解析失败按无法判定处理
        res.run(None, "MIXIN [%s] %s 解析失败: %r" % (line, rel, exc))
        return None


def registered_mixin(config, class_name):
    if not config:
        return False
    entries = config.get("mixins") or []
    for entry in entries:
        if not isinstance(entry, str):
            continue
        if entry == class_name or entry.split(".")[-1] == class_name:
            return True
    return False


def _guard_before(body, action_idx, token):
    """action_idx 之前是否有 MobEffects.<token> 的单例身份守卫。"""
    prefix = body[:action_idx]
    token_rx = re.escape(token)
    negative = re.compile(
        r"if\s*\(\s*(?:\(\s*Object\s*\)\s*)?this\s*!=\s*MobEffects\s*\.\s*"
        r"%s\b\s*\)\s*\{?\s*return\s*;" % token_rx)
    if negative.search(prefix):
        return True
    positive = re.compile(
        r"if\s*\(\s*(?:\(\s*Object\s*\)\s*)?this\s*==\s*MobEffects\s*\.\s*"
        r"%s\b\s*\)" % token_rx)
    for m in positive.finditer(prefix):
        brace = body.find("{", m.end())
        if brace < 0:
            continue
        end = matching_close(body, brace)
        if end is not None and brace < action_idx < end:
            return True
    return False


def check_injection_mixin(res, line, code, mixin, target_label,
                          inject_methods, push_count, kind_token,
                          guard_token=None):
    res.run(True, "%s [%s] 找到 %s（%s → @Mixin(%s)）"
            % (code, line, mixin["class"], mixin["file"], mixin["target"]))
    body = mixin["src"]
    require(res, body, r"DorothyWarmthHealContext\s*\.\s*push\s*\(",
            code, line, "%s 的 DorothyWarmthHealContext.push(...) 调用" % target_label)
    require(res, body, r"DorothyWarmthHealContext\s*\.\s*pop\s*\(",
            code, line, "%s 的 DorothyWarmthHealContext.pop() 调用" % target_label)
    require(res, body, r"(?:getAmplifier\s*\(\s*\)|amplifier)\s*\+\s*1",
            code, line, "%s 的 amplifier + 1 来源等级" % target_label)
    require(res, body, r"@At\s*\(\s*[\"']HEAD[\"']\s*\)",
            code, line, "%s 的 HEAD 注入点" % target_label)
    require(res, body, r"@At\s*\(\s*[\"']RETURN[\"']\s*\)",
            code, line, "%s 的 RETURN 注入点" % target_label)
    require(res, body, r"\b" + re.escape(kind_token) + r"\b",
            code, line, "%s 的 Kind.%s 来源标记" % (target_label, kind_token))
    for method_name in inject_methods:
        require(res, body, r"\b" + re.escape(method_name) + r"\b",
                code, line, "%s 注入 %s" % (target_label, method_name))
    pushes = count_rx(body, r"DorothyWarmthHealContext\s*\.\s*push\s*\(")
    pops = count_rx(body, r"DorothyWarmthHealContext\s*\.\s*pop\s*\(")
    if pushes >= push_count and pops >= push_count:
        res.run(True, "%s [%s] %s push/pop 成对覆盖 %d 个注入点"
                % (code, line, target_label, push_count))
    else:
        res.run(False, "%s [%s] %s push=%d / pop=%d，应各 ≥ %d（每个注入点都要 push/pop）"
                % (code, line, target_label, pushes, pops, push_count))

    if guard_token is not None:
        guarded_methods = [
            m for m in iter_methods(body)
            if "DorothyWarmthHealContext.push" in m["body"]
            or "DorothyWarmthHealContext.pop" in m["body"]
        ]
        if not guarded_methods:
            res.run(False, "%s [%s] %s 找不到 push/pop 所在方法，无法验证 %s 身份守卫"
                    % (code, line, target_label, guard_token))
        else:
            bad = []
            for meth in guarded_methods:
                for action in ("push", "pop"):
                    idx = meth["body"].find("DorothyWarmthHealContext." + action)
                    if idx >= 0 and not _guard_before(meth["body"], idx, guard_token):
                        bad.append((meth["name"], action))
            if bad:
                for name, action in bad:
                    res.run(False, "%s [%s] %s 方法 %s 的 %s 前缺少 "
                                   "`(Object)this ... MobEffects.%s` 单例身份守卫"
                            % (code, line, target_label, name, action, guard_token))
            else:
                res.run(True, "%s [%s] %s 所有 push/pop 前都有 MobEffects.%s 身份守卫"
                        % (code, line, target_label, guard_token))


# ---------------------------------------------------------------------------
# dorothy
# ---------------------------------------------------------------------------
def check_dorothy(line, res):
    print("[%s] dorothy" % line)
    path = os.path.join(ROOT, line, DOROTHY_EVENTS)
    if not os.path.isfile(path):
        res.run(None, "DOR-1 [%s] 文件不存在 %s" % (line, DOROTHY_EVENTS))
        return
    src = strip_comments(read_text(path))

    # 先判旧行为：Added → grantWarmth 必须消失，LivingHealEvent 必须存在。
    added = [m for m in iter_methods(src) if "MobEffectEvent.Added" in m["params"]]
    if not added:
        res.run(True, "DOR-1 [%s] 无 MobEffectEvent.Added 加层监听" % line)
    for meth in added:
        forbid(res, meth["body"], r"grantWarmth\s*\(",
               "DOR-1", line,
               "MobEffectEvent.Added 监听仍调用 grantWarmth（旧按效果施加加层）")
        forbid(res, meth["body"], r"addEffect\s*\([^;]*DOROTHY_WARMTH",
               "DOR-1", line,
               "MobEffectEvent.Added 监听仍直接 addEffect(DOROTHY_WARMTH)")

    heal = None
    for candidate in iter_methods(src):
        if "LivingHealEvent" not in candidate["params"]:
            continue
        if "SubscribeEvent" in candidate["prefix"]:
            heal = candidate
            break
    if heal is None:
        res.run(False, "DOR-1 [%s] 缺少 LivingHealEvent 监听（新加层入口）" % line)
    else:
        require(res, heal["prefix"],
                r"@SubscribeEvent\s*\(\s*priority\s*=\s*(?:[\w.]+\.)?EventPriority\s*\.\s*LOWEST\b[^)]*\)",
                "DOR-1", line, "LivingHealEvent 监听的 LOWEST 优先级")
        body = heal["body"]
        require(res, body, r"isClientSide", "DOR-1", line,
                "LivingHealEvent 的服务端过滤（isClientSide）")
        require(res, body, r"\b(?:isAlive|isDeadOrDying)\s*\(",
                "DOR-1", line, "LivingHealEvent 的死亡玩家过滤")
        require(res, body, r"isEquippedForWarmth\s*\(",
                "DOR-1", line, "LivingHealEvent 的佩戴判定（isEquippedForWarmth）")
        require(res, body, r"isCanceled\s*\(\s*\)",
                "DOR-1", line, "LivingHealEvent 的已取消治疗过滤（isCanceled）")
        require(res, body, r"getAmount\s*\(\s*\)\s*(?:<=|<|>|>=)\s*0",
                "DOR-1", line, "LivingHealEvent 的有效治疗量过滤（getAmount）")
        require(res, body,
                r"(?:getHealth\s*\(\s*\)[\s\S]{0,160}getMaxHealth\s*\(\s*\)"
                r"|getMaxHealth\s*\(\s*\)[\s\S]{0,160}getHealth\s*\(\s*\))",
                "DOR-1", line, "LivingHealEvent 的满血过疗过滤（getHealth/getMaxHealth）")
        require(res, body, r"DorothyWarmthHealContext\s*\.\s*current\s*\(",
                "DOR-1", line, "LivingHealEvent 的治疗来源读取（current()）")
        source_match = re.search(
            r"(?:DorothyWarmthHealContext\s*\.\s*)?Source\s+([A-Za-z_]\w*)\s*="
            r"\s*DorothyWarmthHealContext\s*\.\s*current\s*\(\s*\)", body)
        if source_match is None:
            source_match = re.search(
                r"\b([A-Za-z_]\w*)\s*=\s*DorothyWarmthHealContext\s*\.\s*current\s*\(\s*\)",
                body)
        if source_match is None:
            require(res, body,
                    r"DorothyWarmthHealContext\s*\.\s*current\s*\(\s*\)\s*\.\s*level\s*\(\s*\)",
                    "DOR-1", line,
                    "LivingHealEvent 直接读取 current() 返回值的 level()")
        else:
            source_var = source_match.group(1)
            require(res, body,
                    r"\b%s\s*\.\s*level\s*\(\s*\)" % re.escape(source_var),
                    "DOR-1", line,
                    "LivingHealEvent 读取 current() 返回值 %s 的 level()" % source_var)
        require(res, body, r"grantWarmth\s*\(",
                "DOR-1", line, "LivingHealEvent 调 grantWarmth 加层")

    grant = find_method(src, "grantWarmth")
    if grant is None:
        res.run(False, "DOR-1 [%s] grantWarmth 被删除（温暖加层与 5 层封顶入口丢失）" % line)
    else:
        require(res, grant["body"], r"DorothyWarmthEffect\s*\.\s*MAX_STACKS",
                "DOR-1", line, "grantWarmth 仍使用 DorothyWarmthEffect.MAX_STACKS")
        require(res, grant["body"], r"Math\s*\.\s*min\s*\(",
                "DOR-1", line, "grantWarmth 仍用 Math.min 封顶")

    context_path = os.path.join(ROOT, line, DOROTHY_CONTEXT)
    if not os.path.isfile(context_path):
        res.run(False, "DOR-2 [%s] 未创建治疗来源上下文 %s"
                % (line, DOROTHY_CONTEXT))
    else:
        ctx = strip_comments(read_text(context_path))
        require(res, ctx, r"\bThreadLocal\b", "DOR-2", line,
                "DorothyWarmthHealContext 的 ThreadLocal 栈")
        require(res, ctx, r"\bpush\s*\(", "DOR-2", line,
                "DorothyWarmthHealContext.push(...)")
        require(res, ctx, r"\bcurrent\s*\(", "DOR-2", line,
                "DorothyWarmthHealContext.current()")
        require(res, ctx, r"\bpop\s*\(", "DOR-2", line,
                "DorothyWarmthHealContext.pop()")
        require(res, ctx, r"\bINSTANT\b", "DOR-2", line,
                "DorothyWarmthHealContext.Kind.INSTANT")
        require(res, ctx, r"\bREGENERATION\b", "DOR-2", line,
                "DorothyWarmthHealContext.Kind.REGENERATION")
        require(res, ctx, r"\bSource\b", "DOR-2", line,
                "DorothyWarmthHealContext.Source(kind, level)")
        require(res, ctx, r"\.\s*remove\s*\(\s*\)", "DOR-2", line,
                "pop 后清理空 ThreadLocal 栈（remove()）")

    if line == "forge-1.20.1":
        def forge_mob_effect(parsed):
            return ((parsed["kind"] == "class"
                     and parsed["value"].split(".")[-1] == "MobEffect")
                    or (parsed["kind"] == "targets"
                        and parsed["value"] == "net.minecraft.world.effect.MobEffect"))

        instant = find_mixin(line, forge_mob_effect,
                             content_rx=r"MobEffects\s*\.\s*HEAL\b")
        regen = find_mixin(line, forge_mob_effect,
                           content_rx=r"MobEffects\s*\.\s*REGENERATION\b")
        instant_methods = ("applyInstantenousEffect",)
        instant_push = 1
        instant_guard = "HEAL"
        regen_methods = ("applyEffectTick",)
        regen_push = 1
        regen_guard = "REGENERATION"
        instant_missing = ("缺少瞬时治疗效果 Mixin（Forge 目标必须是 MobEffect"
                           "（class 或精确 targets 字符串），HEAL 分支带单例身份守卫；"
                           "不得用 InstantenousMobEffect）")
        regen_missing = ("缺少生命恢复 Mixin（Forge 目标必须是 MobEffect"
                         "（class 或精确 targets 字符串），REGENERATION 分支带单例身份守卫）")
    else:
        def neo_instant(parsed):
            return ((parsed["kind"] == "class"
                     and parsed["value"].split(".")[-1] == "HealOrHarmMobEffect")
                    or (parsed["kind"] == "targets"
                        and parsed["value"]
                        == "net.minecraft.world.effect.HealOrHarmMobEffect"))

        def neo_regen(parsed):
            return ((parsed["kind"] == "class"
                     and parsed["value"].split(".")[-1] == "RegenerationMobEffect")
                    or (parsed["kind"] == "targets"
                        and parsed["value"]
                        == "net.minecraft.world.effect.RegenerationMobEffect"))

        instant = find_mixin(line, neo_instant)
        regen = find_mixin(line, neo_regen)
        instant_methods = ("applyInstantenousEffect", "applyEffectTick")
        instant_push = 2
        instant_guard = None
        regen_methods = ("applyEffectTick",)
        regen_push = 1
        regen_guard = None
        instant_missing = ("缺少瞬时治疗效果 Mixin（Neo 目标必须是包私有 "
                           "HealOrHarmMobEffect 的精确 targets 字符串或类字面量；"
                           "InstantenousMobEffect 不是 1.21.1 的正确目标）")
        regen_missing = ("缺少 RegenerationMobEffect Mixin（Neo 目标必须是包私有 "
                         "RegenerationMobEffect 的精确 targets 字符串或类字面量）")

    if instant is None:
        res.run(False, "DOR-3 [%s] %s" % (line, instant_missing))
    else:
        check_injection_mixin(
            res, line, "DOR-3", instant, "瞬时治疗效果 Mixin",
            instant_methods, instant_push, "INSTANT",
            guard_token=instant_guard)

    if regen is None:
        res.run(False, "DOR-3 [%s] %s" % (line, regen_missing))
    else:
        check_injection_mixin(
            res, line, "DOR-3", regen, "生命恢复 Mixin",
            regen_methods, regen_push, "REGENERATION",
            guard_token=regen_guard)

    config = load_mixin_config(line, res)
    if config is not None:
        for mixin in (instant, regen):
            if mixin is None:
                continue
            if registered_mixin(config, mixin["class"]):
                res.run(True, "DOR-3 [%s] Mixin %s 已登记到 astral_dice.mixins.json"
                        % (line, mixin["class"]))
            else:
                res.run(False, "DOR-3 [%s] Mixin %s（%s）未登记到 astral_dice.mixins.json 的 mixins 数组"
                        % (line, mixin["class"], mixin["file"]))
        injectors = config.get("injectors") or {}
        if injectors.get("defaultRequire") == 1:
            res.run(True, "DOR-3 [%s] mixin defaultRequire=1 保持不变" % line)
        else:
            res.run(False, "DOR-3 [%s] mixin defaultRequire=%r（应为 1）"
                    % (line, injectors.get("defaultRequire")))

    # ------------------------------------------------------------------
    # DOR-4：【交际花】状态效果（2026-10-25 用户裁决）
    # 队友那 1 点攻击力改由效果自带的属性修饰符承载：固定 1:00、与主动窗口解耦；
    # 同时删掉 10 秒节流（每人每窗口一次已足够防刷）。
    # ------------------------------------------------------------------
    socialite_item = os.path.join(ROOT, line, "src/main/java/com/merlinkitsune/astral_dice/item/sign/DorothySignItem.java")
    socialite_effect = os.path.join(ROOT, line, "src/main/java/com/merlinkitsune/astral_dice/effect/DorothySocialiteEffect.java")
    socialite_mods = os.path.join(ROOT, line, "src/main/java/com/merlinkitsune/astral_dice/effect/ModEffects.java")
    socialite_attach = os.path.join(ROOT, line, "src/main/java/com/merlinkitsune/astral_dice/component/ModAttachments.java")
    socialite_icon = os.path.join(ROOT, line, "src/main/resources/assets/astral_dice/textures/mob_effect/dorothy_socialite.png")

    if not os.path.isfile(socialite_effect):
        res.run(False, "DOR-4 [%s] 缺少 DorothySocialiteEffect.java（【交际花】效果类）" % line)
    else:
        eff = strip_comments(read_text(socialite_effect))
        require(res, eff, r"DURATION_TICKS\s*=\s*1200\s*;", "DOR-4", line,
                "【交际花】固定 1:00（DURATION_TICKS = 1200，与主动窗口解耦）")
        require(res, eff, r"Attributes\s*\.\s*ATTACK_DAMAGE", "DOR-4", line,
                "【交际花】走 ATTACK_DAMAGE 属性修饰符")
        require(res, eff, r"addAttributeModifier\s*\(", "DOR-4", line,
                "【交际花】用 addAttributeModifier 挂修饰符")
        require(res, eff, r"ATTACK_BONUS\s*=\s*1(?:\.0)?D?\s*;", "DOR-4", line,
                "【交际花】攻击力 +1")
        if line == "neoforge-1.21.1":
            require(res, eff, r'"dorothy_socialite_attack"', "DOR-4", line,
                    "Neo 修饰符 key = astral_dice:dorothy_socialite_attack")
            require(res, eff, r"Operation\s*\.\s*ADD_VALUE", "DOR-4", line,
                    "Neo Operation.ADD_VALUE")
        else:
            require(res, eff, r'"d4b20452-7270-5185-ab1e-46ce1b3325ab"', "DOR-4", line,
                    "Forge 修饰符 UUID = astral_dice:dorothy_socialite_attack 的 UUIDv5")
            require(res, eff, r"Operation\s*\.\s*ADDITION", "DOR-4", line,
                    "Forge Operation.ADDITION")
            require(res, eff, r"getAttributeModifierValue\s*\(", "DOR-4", line,
                    "Forge 覆写 getAttributeModifierValue")

    if not os.path.isfile(socialite_mods):
        res.run(False, "DOR-4 [%s] 文件不存在 effect/ModEffects.java" % line)
    else:
        mods_src = strip_comments(read_text(socialite_mods))
        require(res, mods_src, r'EFFECTS\s*\.\s*register\s*\(\s*"dorothy_socialite"',
                "DOR-4", line, "ModEffects 注册 dorothy_socialite")
        require(res, mods_src, r"DOROTHY_SOCIALITE", "DOR-4", line,
                "ModEffects 暴露 DOROTHY_SOCIALITE 常量")

    if not os.path.isfile(socialite_item):
        res.run(False, "DOR-4 [%s] 文件不存在 item/sign/DorothySignItem.java" % line)
    else:
        item_src = strip_comments(read_text(socialite_item))
        calls = len(re.findall(r"applySocialite\s*\(\s*other\s*\)", item_src))
        res.run(True if calls >= 2 else False,
                "DOR-4 [%s] applySocialite(other) 调用点 %d 处（tick 循环 + 落地即刻，应 ≥ 2）"
                % (line, calls))
        require(res, item_src, r"new\s+MobEffectInstance\s*\(\s*ModEffects\s*\.\s*DOROTHY_SOCIALITE",
                "DOR-4", line, "队友加成通过 MobEffectInstance(DOROTHY_SOCIALITE) 施加")
        require(res, item_src, r"tryTriggerDorothyPartner\s*\(\s*player\s*,\s*other\s*\.\s*getUUID\s*\(\s*\)\s*\)",
                "DOR-4", line, "按 (player, uuid) 两参调用 tryTriggerDorothyPartner")
        forbid(res, item_src, r"DOROTHY_THROTTLE_TICKS", "DOR-4", line,
               "仍存在 10 秒节流常量 DOROTHY_THROTTLE_TICKS（应删除）")
        forbid(res, item_src, r'tryTriggerDorothyPartner\s*\([^)]*,\s*now\s*,', "DOR-4", line,
               "仍按 4 参（含 now/throttle）调用 tryTriggerDorothyPartner")
        forbid(res, item_src, r"applyTempAttack\s*\(", "DOR-4", line,
               "仍调用 applyTempAttack（瞬态修饰符路径应删除）")
        forbid(res, item_src, r'"dorothy_partner_attack"', "DOR-4", line,
               "仍使用瞬态修饰符 key dorothy_partner_attack")
        forbid(res, item_src, r"clearPartnerBuffs\s*\(", "DOR-4", line,
               "仍调用 clearPartnerBuffs（窗口结束不再回收队友加成）")

    if not os.path.isfile(socialite_attach):
        res.run(False, "DOR-4 [%s] 文件不存在 component/ModAttachments.java" % line)
    else:
        attach_src = strip_comments(read_text(socialite_attach))
        require(res, attach_src, r"tryTriggerDorothyPartner\s*\([^)]*Player\s+player\s*,\s*UUID\s+other\s*\)",
                "DOR-4", line, "ModAttachments.tryTriggerDorothyPartner 只保留 (player, other)")
        forbid(res, attach_src, r"DOROTHY_PARTNERS\b", "DOR-4", line,
               "仍存在 DOROTHY_PARTNERS（uuid=tick 节流表，应删除）")
        forbid(res, attach_src, r"dorothyPartnerUuids\s*\(", "DOR-4", line,
               "仍存在 dorothyPartnerUuids（窗口结束扫除队友加成，应删除）")

    if os.path.isfile(socialite_icon):
        res.run(True, "DOR-4 [%s] 图标 textures/mob_effect/dorothy_socialite.png 存在" % line)
    else:
        res.run(False, "DOR-4 [%s] 缺少图标 textures/mob_effect/dorothy_socialite.png" % line)



# ---------------------------------------------------------------------------
# jill
# ---------------------------------------------------------------------------
def check_jill(line, res):
    print("[%s] jill" % line)
    path = os.path.join(ROOT, line, JILL_ITEM)
    if not os.path.isfile(path):
        res.run(None, "JIL-1 [%s] 文件不存在 %s" % (line, JILL_ITEM))
        return
    src = strip_comments(read_text(path))
    resolve = find_method(src, "resolve", "List<ItemStack>")
    if resolve is None:
        res.run(None, "JIL-1 [%s] 无法定位 resolve(ServerPlayer, List<ItemStack>) 方法" % line)
        return

    # 先判旧行为：REGENERATION 必须在文件里消失，heal 必须出现。
    forbid(res, src, r"MobEffects\s*\.\s*REGENERATION",
           "JIL-1", line, "仍施加 MobEffects.REGENERATION（旧按效果牌数量给再生）")
    require(res, resolve["body"],
            r"for\s*\([^)]*\)\s*\{[^}]{0,120}target\s*\.\s*heal\s*\(\s*HEAL_AMOUNT\s*\)",
            "JIL-1", line, "逐张各结算 1 次：for 循环体里的 target.heal(HEAL_AMOUNT)")
    forbid(res, resolve["body"],
           r"target\s*\.\s*heal\s*\(\s*HEAL_AMOUNT\s*\*\s*effect\s*\)",
           "JIL-1", line, "仍合并为一次 heal(HEAL_AMOUNT * effect)（2026-10-26 裁决：必须逐张各结算 1 次）")
    require(res, resolve["body"],
            r"effect\s*>\s*0[\s\S]{0,200}target\s*\.\s*heal\s*\(",
            "JIL-1", line, "仅效果牌计数 > 0 时执行治疗")
    require(res, src,
            r"HEAL_AMOUNT\s*=\s*2(?:\.0)?[Ff]\s*;",
            "JIL-1", line, "常量 HEAL_AMOUNT = 2.0F")
    require(res, resolve["body"],
            r"boolean\s+allEffect\s*=\s*effect\s*==\s*3\s*;",
            "JIL-1", line, "boolean allEffect = effect == 3")

    mixed_scope = guarded_scope(resolve["body"],
                                re.compile(r"if\s*\(\s*!\s*allEffect\s*\)"))
    if mixed_scope is None:
        res.run(False, "JIL-2 [%s] JILL_MIXED 未受 !allEffect 守卫（全效果牌仍会获得混合标记）" % line)
    else:
        require(res, mixed_scope, r"\bJILL_MIXED\b",
                "JIL-2", line, "!allEffect 守卫块内才施加 JILL_MIXED")

    speed_scope = guarded_scope(
        resolve["body"],
        re.compile(r"if\s*\(\s*(?:allSame\s*&&\s*!\s*allEffect|!\s*allEffect\s*&&\s*allSame)\s*\)"))
    if speed_scope is None:
        res.run(False, "JIL-2 [%s] 迅捷未受 allSame && !allEffect 守卫（全效果牌仍会获得迅捷）" % line)
    else:
        require(res, speed_scope, r"MOVEMENT_SPEED",
                "JIL-2", line, "allSame && !allEffect 守卫块内才施加迅捷")

    coin_scope = guarded_scope(resolve["body"],
                               re.compile(r"if\s*\(\s*!\s*allSame\s*\)"))
    if coin_scope is None:
        res.run(False, "JIL-2 [%s] 星币未受 !allSame 守卫（全效果牌会落入混合牌星币分支）" % line)
    else:
        require(res, coin_scope, r"StarEngineEconomy\s*\.\s*deposit",
                "JIL-2", line, "!allSame 守卫块内的双方星币")

    buff_scope = guarded_scope(
        resolve["body"],
        re.compile(r"if\s*\(\s*attack\s*\+\s*defense\s*>\s*0\s*\)"))
    if buff_scope is None:
        res.run(False, "JIL-2 [%s] 目标 buff 计时未受 attack + defense > 0 守卫"
                       "（全效果牌会写内部计时器）" % line)
    else:
        require(res, buff_scope, r"setJillBuffTarget\s*\(",
                "JIL-2", line, "attack + defense > 0 守卫块内写受惠目标")
        require(res, buff_scope, r"setJillBuffEnd\s*\(",
                "JIL-2", line, "attack + defense > 0 守卫块内写 buff 结束刻")

    require(res, resolve["body"], r"boolean\s+allSame\s*=",
            "JIL-2", line, "保留 allSame 判定")
    require(res, resolve["body"], r"grantRandomCards\s*\(\s*caster\s*,\s*3\s*\)",
            "JIL-3", line, "保留随机 3 张牌")
    require(res, resolve["body"], r"\bTARGET_ATTACK_KEY\b",
            "JIL-3", line, "保留攻击牌目标攻击力修饰符")
    require(res, resolve["body"], r"\bTARGET_ARMOR_KEY\b",
            "JIL-3", line, "保留防御牌目标防御力修饰符")
    require(res, src, r"ModEffects\s*\.\s*JILL_MIXED",
            "JIL-3", line, "保留 JILL_MIXED 效果注册/应用（非全效果牌）")
    require(res, resolve["body"], r"setSignActiveCooldownEnd\s*\(",
            "JIL-3", line, "保留主动冷却结算")


# ---------------------------------------------------------------------------
# deepseek
# ---------------------------------------------------------------------------
def _deepseek_active_body(src):
    handle = find_method(src, "handleUse", "Player")
    if handle is not None and re.search(r"nextInt\s*\(\s*20\s*\)", handle["body"]):
        return handle
    for meth in iter_methods(src):
        if re.search(r"nextInt\s*\(\s*20\s*\)", meth["body"]):
            return meth
    return handle


def check_deepseek(line, res):
    print("[%s] deepseek" % line)
    path = os.path.join(ROOT, line, DEEPSEEK_ITEM)
    if not os.path.isfile(path):
        res.run(None, "DEE-1 [%s] 文件不存在 %s" % (line, DEEPSEEK_ITEM))
        return
    src = strip_comments(read_text(path))

    # 先判旧行为：发放 100 星币 / 随机立牌筹码 / blockPosition 粗粒度必须消失。
    forbid(res, src, r"StarEngineEconomy\s*\.\s*deposit",
           "DEE-1", line, "仍保留旧主动 StarEngineEconomy.deposit(...100)（新主动是消耗）")
    forbid(res, src, r"\brandomReward\s*\(",
           "DEE-1", line, "仍保留旧 randomReward 随机立牌/筹码")
    forbid(res, src, r"\bblockPosition\s*\(",
           "DEE-2", line, "仍用 blockPosition() 粗粒度判定（要求精确位置快照）")

    active = _deepseek_active_body(src)
    if active is None:
        res.run(False, "DEE-1 [%s] 无法定位主动结算（handleUse / nextInt(20)）" % line)
        active_body = ""
    else:
        active_body = active["body"]
        require(res, active_body,
                r"(?:int\s+)?roll\s*=\s*[^;]*nextInt\s*\(\s*20\s*\)\s*\+\s*1",
                "DEE-1", line, "先掷随机 1..20（nextInt(20) + 1）")
        require(res, active_body, r"Math\s*\.\s*min\s*\(",
                "DEE-1", line, "余额夹取（Math.min(roll, balance)）")
        require(res, active_body, r"StarEngineEconomy\s*\.\s*getBalance\s*\(",
                "DEE-1", line, "读取余额（StarEngineEconomy.getBalance）")
        require(res, active_body, r"StarEngineEconomy\s*\.\s*withdraw\s*\(",
                "DEE-1", line, "按实际消耗扣款（StarEngineEconomy.withdraw）")

        clamp = re.search(
            r"\b(?:int|long)\s+([A-Za-z_]\w*)\s*=\s*(?:\(\s*int\s*\)\s*)?"
            r"Math\s*\.\s*min\s*\(\s*roll\s*,",
            active_body, re.S)
        actual_var = clamp.group(1) if clamp else "actual"

        zero_block, _ = find_if_block_containing(
            active_body,
            re.compile(r"%s\s*<=\s*0L?" % re.escape(actual_var)))
        if zero_block is None:
            res.run(False, "DEE-1 [%s] 缺少余额 0 / 实际消耗 <= 0 的失败早退" % line)
        else:
            require(res, zero_block, r"\breturn\b",
                    "DEE-1", line, "余额 0 失败分支直接 return（不加成/不发牌/不进冷却）")

        withdraw_block, _ = find_if_block_containing(
            active_body,
            re.compile(r"!\s*StarEngineEconomy\s*\.\s*withdraw\s*\("))
        if withdraw_block is None:
            res.run(False, "DEE-1 [%s] 缺少扣款失败（!withdraw）的失败早退" % line)
        else:
            require(res, withdraw_block, r"\breturn\b",
                    "DEE-1", line, "扣款失败分支直接 return")

        require(res, active_body,
                r"setAttackPowerBonus\s*\([^;]*deepseek_buff_attack[^;]*\b%s\b"
                % re.escape(actual_var),
                "DEE-1", line, "攻击加成用实际消耗 actual（deepseek_buff_attack）")
        require(res, active_body,
                r"setDefenseArmorBonus\s*\([^;]*deepseek_buff_armor[^;]*\b%s\b"
                % re.escape(actual_var),
                "DEE-1", line, "防御加成用实际消耗 actual（deepseek_buff_armor）")
        require(res, active_body, r"setDeepseekBuffEnd\s*\(",
                "DEE-1", line, "写入 buff 结束刻")
        require(res, active_body, r"\bnow\b", "DEE-1", line,
                "buff 结束刻以当前刻计算")
        require(res, active_body, r"\bBUFF_TICKS\b", "DEE-1", line,
                "buff 结束刻使用 BUFF_TICKS")
        require(res, active_body, r"CardCategory\s*\.\s*ALL",
                "DEE-1", line, "发牌使用 RandomCardHandler.CardCategory.ALL")

        all_calls = count_rx(active_body,
                             r"giveCardTo\s*\([^;]*CardCategory\s*\.\s*ALL")
        loop_all = re.search(
            r"for\s*\([^)]*\)\s*\{[\s\S]{0,400}?giveCardTo\s*\([^;]*CardCategory\s*\.\s*ALL",
            src)
        if all_calls >= 3:
            res.run(True, "DEE-1 [%s] 主动直接发 3 次 ALL 牌" % line)
        elif all_calls >= 1 and loop_all is not None and re.search(
                r"for\s*\([^)]*(?:CARD_COUNT|<\s*3|<=\s*3)[^)]*\)", src):
            res.run(True, "DEE-1 [%s] 主动以循环发 3 次 ALL 牌" % line)
        else:
            res.run(False, "DEE-1 [%s] ALL 发牌次数不足 3（直接调用 %d 次，且未见 CARD_COUNT 循环）"
                    % (line, all_calls))

    require(res, src, r"IDLE_TICKS\s*=\s*200L?\s*;",
            "DEE-2", line, "常量 IDLE_TICKS = 200")
    require(res, src, r"MOVE_LIMIT_TICKS\s*=\s*100L?\s*;",
            "DEE-2", line, "常量 MOVE_LIMIT_TICKS = 100")
    require(res, src, r"BUFF_TICKS\s*=\s*2400L?\s*;",
            "DEE-1", line, "常量 BUFF_TICKS = 2400")
    require(res, src, r"CARD_COUNT\s*=\s*3\s*;",
            "DEE-1", line, "常量 CARD_COUNT = 3")

    tick = find_method(src, "onCurioTick")
    if tick is None:
        res.run(None, "DEE-2 [%s] 无法定位 onCurioTick 方法" % line)
    else:
        body = tick["body"]
        require(res, body, r"getDeepseekLastPos\s*\(",
                "DEE-2", line, "读取上一精确位置快照")
        require(res, body, r"setDeepseekLastPos\s*\(",
                "DEE-2", line, "每 tick 更新精确位置快照")
        xyz = all(re.search(r"get%s\s*\(" % axis, body) for axis in "XYZ")
        vec3 = (re.search(r"position\s*\(", body) is not None
                and all(re.search(r"\.%s\b" % axis.lower(), body) for axis in "XYZ"))
        if xyz or vec3:
            res.run(True, "DEE-2 [%s] 位置快照包含精确 X/Y/Z 坐标" % line)
        else:
            res.run(False, "DEE-2 [%s] 位置快照缺少精确坐标"
                            "（getX/getY/getZ 或 position().x/y/z）" % line)
        require(res, body, r"getDeepseekMoveTicks\s*\(",
                "DEE-2", line, "读取摸鱼移动累计 tick")
        require(res, body, r"setDeepseekMoveTicks\s*\(",
                "DEE-2", line, "写入摸鱼移动累计 tick")
        require(res, body, r"\bIDLE_TICKS\b", "DEE-2", line,
                "静止 200 tick 判定使用 IDLE_TICKS")
        require(res, body, r"\bMOVE_LIMIT_TICKS\b", "DEE-2", line,
                "移动 100 tick 解除判定使用 MOVE_LIMIT_TICKS")
        require(res, body, r"DEEPSEEK_FISHING",
                "DEE-2", line, "保留摸鱼效果施加")

        # 获得摸鱼时清附近已有仇恨：只认真正构造
        # new MobEffectInstance(ModEffects.DEEPSEEK_FISHING...) 的“授予”方法，
        # 不把 removeFishing / hasEffect 等仅引用摸鱼键的方法当成授予路径。
        grant_rx = (r"new\s+MobEffectInstance\s*\(\s*"
                    r"ModEffects\s*\.\s*DEEPSEEK_FISHING")
        grant_methods = [m for m in iter_methods(src)
                         if re.search(grant_rx, m["body"], re.S)]
        clear_methods = []
        for meth in iter_methods(src):
            if re.search(r"setTarget\s*\(\s*null\s*\)", meth["body"]) \
                    and re.search(r"getTarget\s*\(\s*\)", meth["body"]):
                clear_methods.append(meth)
        if not clear_methods:
            res.run(False, "DEE-2 [%s] 缺少 setTarget(null) + getTarget() 的已有仇恨清理" % line)
        elif not grant_methods:
            res.run(False, "DEE-2 [%s] 未找到实际施加 DEEPSEEK_FISHING 的方法"
                            "（new MobEffectInstance(DEEPSEEK_FISHING...)）" % line)
        else:
            cleanup_hooked = False
            for meth in grant_methods:
                if re.search(r"setTarget\s*\(\s*null\s*\)", meth["body"]):
                    cleanup_hooked = True
                for clear in clear_methods:
                    if clear["name"] == meth["name"]:
                        continue
                    if re.search(r"\b%s\s*\(" % re.escape(clear["name"]), meth["body"]):
                        cleanup_hooked = True
            if cleanup_hooked:
                res.run(True, "DEE-2 [%s] 获得摸鱼的授予路径会清理附近怪物已有仇恨" % line)
            else:
                res.run(False, "DEE-2 [%s] 授予 DEEPSEEK_FISHING 的方法未调用已有仇恨清理"
                                "（只定义清理方法/只在移除路径调用不算）" % line)

            grant_names = [m["name"] for m in grant_methods]
            tick_grants = bool(re.search(grant_rx, body, re.S)) or any(
                re.search(r"\b%s\s*\(" % re.escape(name), body) for name in grant_names)
            if tick_grants:
                res.run(True, "DEE-2 [%s] onCurioTick 驱动摸鱼授予" % line)
            else:
                res.run(False, "DEE-2 [%s] onCurioTick 未驱动摸鱼授予" % line)

    tick_buff = find_method(src, "tickBuff")
    if tick_buff is None:
        res.run(False, "DEE-3 [%s] 缺少 tickBuff(Player) 到期清理" % line)
    else:
        body = tick_buff["body"]
        require(res, body, r"deepseek_buff_attack",
                "DEE-3", line, "tickBuff 清理 deepseek_buff_attack")
        require(res, body, r"deepseek_buff_armor",
                "DEE-3", line, "tickBuff 清理 deepseek_buff_armor")
        require(res, body, r"getDeepseekBuffEnd\s*\(",
                "DEE-3", line, "tickBuff 读取 buff 结束刻")
        require(res, body, r">=", "DEE-3", line,
                "tickBuff 到期比较（>=）")
        require(res, body, r"setAttackPowerBonus\s*\(",
                "DEE-3", line, "tickBuff 归零攻击修饰符")
        require(res, body, r"setDefenseArmorBonus\s*\(",
                "DEE-3", line, "tickBuff 归零防御修饰符")
        require(res, body, r"setDeepseekBuffEnd\s*\([^;]*\b0L?\b",
                "DEE-3", line, "tickBuff 归零 buff 结束刻")

    tick_path = os.path.join(ROOT, line, TICK_EVENTS)
    if not os.path.isfile(tick_path):
        res.run(None, "DEE-3 [%s] 文件不存在 %s" % (line, TICK_EVENTS))
    else:
        tick_src = strip_comments(read_text(tick_path))
        require(res, tick_src, r"DeepseekSignItem\s*\.\s*tickBuff\s*\(",
                "DEE-3", line, "PlayerTickEvents 调用 DeepseekSignItem.tickBuff")

    attachments_path = os.path.join(ROOT, line, ATTACHMENTS)
    if not os.path.isfile(attachments_path):
        res.run(None, "DEE-4 [%s] 文件不存在 %s" % (line, ATTACHMENTS))
    else:
        att = strip_comments(read_text(attachments_path))
        require(res, att,
                r"public\s+static\s+List\s*<\s*Double\s*>\s+getDeepseekLastPos\s*\(",
                "DEE-4", line, "getDeepseekLastPos(): List<Double>")
        require(res, att,
                r"public\s+static\s+void\s+setDeepseekLastPos\s*\([^)]*List\s*<\s*Double\s*>",
                "DEE-4", line, "setDeepseekLastPos(List<Double>)")
        require(res, att,
                r"public\s+static\s+int\s+getDeepseekMoveTicks\s*\(",
                "DEE-4", line, "getDeepseekMoveTicks(): int")
        require(res, att,
                r"public\s+static\s+void\s+setDeepseekMoveTicks\s*\([^)]*\bint\b",
                "DEE-4", line, "setDeepseekMoveTicks(int)")
        require(res, att,
                r"public\s+static\s+long\s+getDeepseekBuffEnd\s*\(",
                "DEE-4", line, "getDeepseekBuffEnd(): long")
        require(res, att,
                r"public\s+static\s+void\s+setDeepseekBuffEnd\s*\([^)]*\blong\b",
                "DEE-4", line, "setDeepseekBuffEnd(long)")

    # DEE-5：Curio tick 中断检测（lastTick）+ 大肥鱼新附件的「仅内存」语义。
    # 摘骰子 / 卸下再戴时 curioTick 整段被 BaseSignItem#curioTick 的骰子闸门跳过 ——
    # 若照旧沿用 idle_since / last_pos，同一坐标重戴即可白拿摸鱼。故要求：
    #   lastTick 缺口 (> 1 tick) 分支重置静止起点 / 移动累计 / 位置快照；
    #   四个新键全部仅内存、不 sync、不持久化（与瞬态攻/防修饰符同寿命）。
    if os.path.isfile(attachments_path):
        att = strip_comments(read_text(attachments_path))
        require(res, att, r"public\s+static\s+long\s+getDeepseekLastTick\s*\(",
                "DEE-5", line, "getDeepseekLastTick(): long")
        require(res, att, r"public\s+static\s+void\s+setDeepseekLastTick\s*\([^)]*\blong\b",
                "DEE-5", line, "setDeepseekLastTick(long)")
        neo_reg = r'ATTACHMENTS\s*\.\s*register\s*\(\s*"%s"'
        forge_reg = r'AttachedDataKey\s*\.\s*builder\s*\(\s*"%s"'
        for key, label in (
                ("deepseek_last_pos", "位置快照"),
                ("deepseek_move_ticks", "移动累计"),
                ("deepseek_last_tick", "Curio tick 戳"),
                ("deepseek_buff_end", "主动结束刻")):
            stmt = key_statement(
                att,
                (neo_reg if line.startswith("neoforge") else forge_reg) % re.escape(key))
            if stmt is None:
                res.run(False, "DEE-5 [%s] 未找到附件键 %s 的注册语句" % (line, key))
                continue
            if line.startswith("neoforge"):
                ok = ".serialize" not in stmt and ".sync" not in stmt
                why = "无 .serialize 且不 sync"
            else:
                ok = ".inMemory()" in stmt and ".sync()" not in stmt
                why = ".inMemory() 且不 sync"
            if ok:
                res.run(True, "DEE-5 [%s] %s %s 仅内存（%s）" % (line, key, label, why))
            else:
                res.run(False, "DEE-5 [%s] %s %s 必须仅内存、不持久化（%s）"
                        % (line, key, label, why))

    tick_lc = find_method(src, "onCurioTick")
    if tick_lc is not None:
        tick_body = tick_lc["body"]
        require(res, tick_body, r"getDeepseekLastTick\s*\(", "DEE-5", line,
                "onCurioTick 读取上一 Curio tick")
        require(res, tick_body, r"setDeepseekLastTick\s*\(", "DEE-5", line,
                "onCurioTick 写入当前 Curio tick")
        require(res, tick_body, r"-\s*lastTick\s*>\s*1L?", "DEE-5", line,
                "Curio tick 中断判定 now - lastTick > 1")
        gap_block, _ = find_if_block_containing(
            tick_body, re.compile(r"\blastTick\s*>\s*0L?"))
        if gap_block is None:
            res.run(False, "DEE-5 [%s] 缺少 lastTick > 0 的中断检测分支" % line)
        else:
            for token, what in (
                    (r"setDeepseekIdleSince\s*\(", "静止起点"),
                    (r"setDeepseekMoveTicks\s*\(", "移动累计"),
                    (r"setDeepseekLastPos\s*\(", "位置快照")):
                require(res, gap_block, token, "DEE-5", line, "中断分支重置" + what)
        i_tick = tick_body.find("getDeepseekLastTick")
        i_pos = tick_body.find("setDeepseekLastPos")
        if i_tick >= 0 and i_pos >= 0 and i_tick < i_pos:
            res.run(True, "DEE-5 [%s] lastTick 判定位于位置快照 / moved 计算之前" % line)
        else:
            res.run(False, "DEE-5 [%s] lastTick 判定必须位于位置快照 / moved 计算之前" % line)

    unequip = find_method(src, "onUnequip")
    if unequip is None:
        res.run(False, "DEE-3 [%s] 缺少 onUnequip 卸下清理" % line)
    else:
        body = unequip["body"]
        cleaned = (
            re.search(r"\btickBuff\s*\(", body) is not None
            or (re.search(r"deepseek_buff_attack", body)
                and re.search(r"deepseek_buff_armor", body)
                and re.search(r"setDeepseekBuffEnd\s*\(", body))
        )
        if cleaned:
            res.run(True, "DEE-3 [%s] 卸下时清理攻/防修饰符与 buff 结束刻" % line)
        else:
            res.run(False, "DEE-3 [%s] 卸下未清理攻/防修饰符或 buff 结束刻" % line)
        require(res, body, r"setDeepseekLastTick\s*\([^;]*\b0L\b",
                "DEE-5", line, "卸下清零 lastTick")


# ---------------------------------------------------------------------------
# nancy
# ---------------------------------------------------------------------------
def check_nancy(line, res):
    print("[%s] nancy" % line)
    path = os.path.join(ROOT, line, NANCY_ITEM)
    if not os.path.isfile(path):
        res.run(None, "NAN-1 [%s] 文件不存在 %s" % (line, NANCY_ITEM))
        return
    src = strip_comments(read_text(path))

    # 先判旧行为：消耗战斗牌 / 费用倍率 / 6 格范围必须消失。
    forbid(res, src, r"\bfindAndConsumeRandomBattleCard\s*\(",
           "NAN-1", line, "仍保留 findAndConsumeRandomBattleCard（旧主动消耗战斗牌）")
    forbid(res, src, r"\bACTIVE_BONUS_MULTIPLIER\b",
           "NAN-1", line, "仍保留 ACTIVE_BONUS_MULTIPLIER（旧费用 ×2）")
    forbid(res, src, r"\bACTIVE_MIN_BONUS\b",
           "NAN-1", line, "仍保留 ACTIVE_MIN_BONUS（旧保底 +2）")
    forbid(res, src, r"\bCardRegistry\s*\.",
           "NAN-1", line, "仍使用 CardRegistry 按费用计算主动加成")
    forbid(res, src, r"PASSIVE_RANGE\s*=\s*6(?:\.[0-9]+)?[DF]?\s*;",
           "NAN-2", line, "PASSIVE_RANGE 仍为 6 格")

    require(res, src, r"PASSIVE_RANGE\s*=\s*3\.0[DF]?\s*;",
            "NAN-2", line, "常量 PASSIVE_RANGE = 3.0")
    require(res, src, r"ACTIVE_ATTACK_BONUS\s*=\s*6\s*;",
            "NAN-1", line, "常量 ACTIVE_ATTACK_BONUS = 6")
    require(res, src, r"ACTIVE_DURATION_TICKS\s*=\s*2400\s*;",
            "NAN-1", line, "保留 ACTIVE_DURATION_TICKS = 2400")
    require(res, src, r"PASSIVE_BONUS\s*=\s*3\s*;",
            "NAN-2", line, "保留被动 PASSIVE_BONUS = 3")

    attack = find_method(src, "onAttackWhileHidden", "Player")
    if attack is None:
        res.run(None, "NAN-1 [%s] 无法定位 onAttackWhileHidden(Player) 方法" % line)
    else:
        body = attack["body"]
        require(res, body, r"isSignActiveLocked\s*\(\s*player\s*\)",
                "NAN-1", line, "保留锁定态检查（isSignActiveLocked）")
        require(res, body,
                r"hasEffect\s*\([^)]*\bINVISIBILITY\b",
                "NAN-1", line, "保留完全隐身检查")
        require(res, body, r"setNancyLuHiddenUntil\s*\(\s*player\s*,\s*0\s*\)",
                "NAN-1", line, "攻击破隐（隐藏截止刻归零）")
        require(res, body,
                r"removeNow\s*\([^;]*\bINVISIBILITY\b",
                "NAN-1", line, "攻击破隐（移除隐身效果）")
        require(res, body,
                r"setNancyLuActiveBonus\s*\(\s*player\s*,\s*ACTIVE_ATTACK_BONUS\s*\)",
                "NAN-1", line, "破隐后固定写入 +ACTIVE_ATTACK_BONUS")
        require(res, body,
                r"setNancyLuActiveBonusUntil\s*\(\s*player\s*,\s*now\s*\+\s*ACTIVE_DURATION_TICKS\s*\)",
                "NAN-1", line, "破隐后加成持续 ACTIVE_DURATION_TICKS")
        require(res, body,
                r"NANCY_LU_HACK[\s\S]{0,160}ACTIVE_DURATION_TICKS",
                "NAN-1", line, "NANCY_LU_HACK 效果时长 2400 tick")

    use = find_method(src, "handleUse", "Player")
    if use is None:
        res.run(None, "NAN-1 [%s] 无法定位 handleUse(..., Player, ...)"
                       "（无法判定按下主动是否误给 +6）" % line)
    else:
        forbid(res, use["body"],
               r"setNancyLuActiveBonus\s*\([^;]*(?:ACTIVE_ATTACK_BONUS|\b6\b)",
               "NAN-1", line,
               "handleUse 按下主动即写 +6（应只在破隐时写）")
        forbid(res, use["body"], r"\bNANCY_LU_HACK\b",
               "NAN-1", line,
               "handleUse 按下主动即施加 NANCY_LU_HACK（应只在破隐时施加）")

    blessing = find_method(src, "onDiceBlessingEnded", "Player")
    if blessing is None:
        res.run(None, "NAN-2 [%s] 无法定位 onDiceBlessingEnded(Player) 方法" % line)
    else:
        body = blessing["body"]
        require(res, body, r"PASSIVE_RANGE",
                "NAN-2", line, "被动范围使用 PASSIVE_RANGE")
        require(res, body,
                r"giveCardTo\s*\(\s*player\s*,\s*RandomCardHandler\s*\.\s*CardCategory\s*\.\s*BATTLE\s*\)",
                "NAN-2", line, "保留被动随机战斗牌发放（CardCategory.BATTLE）")
        require(res, body, r"setNancyLuPassiveType\s*\(",
                "NAN-2", line, "保留被动类型刷新")

    require(res, src, r"onNancyLuEnderPearlImpact", "NAN-2", line,
            "保留末影珍珠落地免疫窗口")
    require(res, src, r"onNancyLuEnderPearlDamage", "NAN-2", line,
            "保留末影珍珠摔落伤害免疫")
    require(res, src, r"onNancyLuAttackWhileHidden", "NAN-1", line,
            "保留近战破隐触发入口")
    require(res, src, r"onNancyLuAnyAttackWhileHidden", "NAN-1", line,
            "保留远程/投掷物破隐触发入口")


AGENTS_FULL_REL = os.path.join("docs", "agent", "AGENTS-full.md")
NANCY_SPEC_ROW_PREFIX = "| 骇客 nancy_lu |"


def check_nancy_spec_doc(res):
    """NAN-D1：AGENTS-full.md 当前骇客规格行须与固定破隐奖励实现一致。

    只取以 `| 骇客 nancy_lu |` 开头的表格行（当前规格），历史条目一律不判。
    """
    print("[docs] nancy spec row")
    path = os.path.join(ROOT, AGENTS_FULL_REL)
    if not os.path.isfile(path):
        res.run(None, "NAN-D1 [docs] 文件不存在 %s" % AGENTS_FULL_REL)
        return
    row = None
    for text in read_text(path).splitlines():
        if text.startswith(NANCY_SPEC_ROW_PREFIX):
            row = text
            break
    if row is None:
        res.run(None, "NAN-D1 [docs] 未找到以 %r 开头的当前骇客规格行"
                % NANCY_SPEC_ROW_PREFIX)
        return
    require(res, row, r"\+6", "NAN-D1", "docs",
            "当前骇客规格行写固定 +6 攻击力")
    require(res, row, r"2:00", "NAN-D1", "docs",
            "当前骇客规格行写持续 2:00")
    require(res, row, r"敌对目标", "NAN-D1", "docs",
            "当前骇客规格行写攻击敌对目标")
    require(res, row, r"周围\s*3\s*格", "NAN-D1", "docs",
            "当前骇客规格行写被动 3 格")
    require(res, row, r"网络防火墙", "NAN-D1", "docs",
            "当前规格行保留网络防火墙被动")
    require(res, row, r"末影珍珠", "NAN-D1", "docs",
            "当前规格行保留末影珍珠免疫")
    forbid(res, row, r"×\s*2", "NAN-D1", "docs",
           "当前规格行仍写旧费用 ×2")
    forbid(res, row, r"最低\s*\+2|保底\s*\+2", "NAN-D1", "docs",
           "当前规格行仍写旧最低/保底 +2")
    forbid(res, row, r"消耗主物品栏中一张随机战斗牌", "NAN-D1", "docs",
           "当前规格行仍写旧战斗牌消耗")
    forbid(res, row, r"周围\s*6\s*格", "NAN-D1", "docs",
           "当前规格行仍写旧被动 6 格")
    forbid(res, row, r"或玩家", "NAN-D1", "docs",
           "当前规格行仍写旧「或玩家」")


# ---------------------------------------------------------------------------
# 三语 tooltip / guide / actionbar 文案
# ---------------------------------------------------------------------------
def _spec(zh, en, ja):
    return {"zh_cn": zh, "en_us": en, "ja_jp": ja}


def _key_specs(key, label, per_lang):
    return (key, label, per_lang)


def dorothy_lang_specs():
    zh_req = [
        ("瞬间治疗口径", r"瞬间治疗"),
        ("生命恢复等级口径", r"生命恢复"),
        ("5 层封顶", r"(?:最多|上限|最大)\s*5\s*层|5\s*层(?:上限|封顶)"),
    ]
    zh_forbid = [
        ("旧语义「获得治疗效果」", r"获得治疗效果"),
        ("旧组合「恢复生命值时获得 1 层「温暖」（按效果等级计算…）」",
         r"恢复生命值时获得\s*1\s*层「温暖」\s*[（(]\s*按效果等级计算"),
    ]
    en_req = [
        ("Instant Health semantics", r"instant\s+health"),
        ("Regeneration semantics", r"regeneration"),
        ("max 5 stacks", r"(?:max(?:imum)?|up to|cap(?:ped)? at)\s*5|5\s*(?:stacks|layers)"),
    ]
    en_forbid = [("old copy 'Gaining a healing effect'", r"gaining\s+a\s+healing\s+effect")]
    ja_req = [
        ("即時回復口径", r"即時回復|インスタント(?:・)?ヘルス"),
        ("再生口径", r"再生"),
        ("最大 5 層", r"最大\s*5|5\s*層"),
    ]
    ja_forbid = [("旧文案「回復効果を得ると」", r"回復効果を得ると")]
    per_lang = _spec((zh_req, zh_forbid), (en_req, en_forbid), (ja_req, ja_forbid))
    return [
        _key_specs("tooltip.astral_dice.sign.dorothy_passive", "DOR-L1", per_lang),
        _key_specs("astral_dice.guide.entry.dorothy_sign.2", "DOR-L2", per_lang),
    ]


def jill_lang_specs():
    act_zh_req = [
        ("每张效果牌", r"每张"),
        ("2 点治疗量", r"2[^\u4e00-\u9fff]{0,6}点"),
        ("按张数叠加", r"张数[^。\n]{0,8}叠加"),
        ("逐张各结算一次治疗（2026-10-26 裁决）",
         r"每张各自结算|各自结算为一次治疗|逐张[^。\n]{0,12}(?:结算|治疗)"),
        ("全效果牌例外", r"全效果牌|三张效果牌|3\s*张效果牌|全部(?:为|是)效果牌"),
        ("全效果牌无计时器/图标",
         r"(?:不|无|没有|未)[^。\n]{0,20}(?:计时器|图标|状态)|(?:计时器|图标|状态)[^。\n]{0,12}(?:不|无|没有)"),
    ]
    act_zh_forbid = [
        ("旧文案「生命恢复（等级 = 效果牌数量）」",
         r"生命恢复\s*[（(]\s*等级|等级\s*=\s*效果牌数量"),
        ("旧文案「固定 2 点 / 不按张数叠加」",
         r"固定\s*2\s*点|不按效果牌张数叠加|不随张数叠加"),
        ("旧口径「合并为一次治疗 / 温暖不叠层」（2026-10-26 裁决作废）",
         r"合并为[^。\n]{0,6}一次(?:治疗| heal)|不因张数多叠层|温暖[^。\n]{0,12}不(?:会)?[^。\n]{0,6}叠层"),
    ]
    act_en_req = [
        ("each card heals 2 HP",
         r"(?:each\s+(?:effect\s+)?card|per\s+card)[^.\n]{0,60}2[^A-Za-z0-9]{0,6}HP"),
        ("stacks per card", r"stacks?\s+per\s+card"),
        ("each card is its own heal (2026-10-26 ruling)",
         r"(?:each card (?:is )?its own|its own (?:separate )?heal|one heal per card)"),
        ("all-effect exception",
         r"all[- ]effect|all three (?:cards are )?effect cards?|three effect cards"),
        ("no timer/icon",
         r"(?:no|without|not)[^.\n]{0,24}(?:timer|icon|status)|(?:timer|icon)[^.\n]{0,12}(?:none|nothing)"),
    ]
    act_en_forbid = [
        ("old 'Regeneration (level = number of effect cards)'",
         r"regeneration\s*\(\s*level|level\s*=\s*number of effect cards"),
        ("old 'flat 2' / 'not 2 per card'",
         r"flat\s+2|not\s+2\s+per\s+card|not\s+per\s+card"),
        ("old 'single heal' / 'Warmth gains no extra stacks' (void 2026-10-26)",
         r"single\s+heal[^\n]{0,80}(?:event|once)|gains?\s+no\s+extra\s+stacks|only\s+one\s+heal(?:ing)?\s+event"),
    ]
    act_ja_req = [
        ("1 枚につき 2 回復", r"1枚につき[^。\n]{0,24}2"),
        ("枚数分加算", r"枚数分加算"),
        ("各枚が独立した1回の回復（2026-10-26 裁決）",
         r"各枚が独立した1回の回復|1枚につき1回"),
        ("全効果カード例外",
         r"全効果カード|すべて(?:が)?効果カード|効果カード3枚|3枚とも効果カード"),
        ("タイマー/アイコンなし",
         r"(?:タイマー|アイコン|状態)[^。\n]{0,14}(?:なし|無し|付与しない|ない)|(?:なし|無し)[^。\n]{0,14}(?:タイマー|アイコン)"),
    ]
    act_ja_forbid = [
        ("旧文案「再生（レベル＝効果カード枚数）」",
         r"再生\s*[（(]\s*レベル|レベル\s*[=＝]\s*効果カード枚数"),
        ("旧文案「固定2 / 枚数分ではない」", r"固定2|枚数分ではない"),
        ("旧口径「まとめて1回の回復」（2026-10-26 裁決で無効）",
         r"まとめて1回|1回にまとめ|回数分重複しない"),
    ]

    pas_zh_req = [
        ("全效果牌例外", r"全效果牌|三张效果牌|3\s*张效果牌|全部(?:为|是)效果牌"),
        ("保留迅捷 II（非全效果牌）", r"迅捷"),
        ("全效果牌不给迅捷",
         r"(?:全效果牌|三张效果牌|3\s*张效果牌|全部(?:为|是)效果牌)[^。\n]{0,60}(?:不|无)[^。\n]{0,24}迅捷"),
        ("全效果牌不给星币",
         r"(?:全效果牌|三张效果牌|3\s*张效果牌|全部(?:为|是)效果牌)[^。\n]{0,60}(?:不|无)[^。\n]{0,24}星币"),
    ]
    pas_zh_forbid = [
        ("旧文案「三张卡牌全部同类型…迅捷」",
         r"三张卡牌全部同类型[^。\n]{0,30}迅捷"),
    ]
    pas_en_req = [
        ("all-effect exception",
         r"all[- ]effect|all three (?:cards are )?effect cards?|three effect cards"),
        ("Speed II retained", r"speed\s+ii"),
        ("no Speed II for all-effect",
         r"all[- ]effect[^.\n]{0,100}(?:no|not|without)[^.\n]{0,30}speed"
         r"|(?:three effect cards)[^.\n]{0,100}(?:no|not|without)[^.\n]{0,30}speed"),
        ("no coins for all-effect",
         r"all[- ]effect[^.\n]{0,100}(?:no|not|without)[^.\n]{0,30}coins?"
         r"|(?:three effect cards)[^.\n]{0,100}(?:no|not|without)[^.\n]{0,30}coins?"),
    ]
    pas_en_forbid = [
        ("old 'all three cards share the same/one type ... Speed II'",
         r"all three cards share\s+(?:the\s+same\s+|one\s+)?type"
         r"[^.\n]{0,80}speed\s+ii"),
    ]
    pas_ja_req = [
        ("全効果カード例外",
         r"全効果カード|すべて(?:が)?効果カード|効果カード3枚|3枚とも効果カード"),
        ("移動速度上昇 II を保留", r"移動速度上昇II"),
        ("全効果カードはスターナなし",
         r"(?:全効果カード|効果カード3枚|3枚とも効果カード)[^。\n]{0,80}"
         r"(?:なし|無し|得られない|付与しない|ない)"),
    ]
    pas_ja_forbid = [
        ("旧文案「3枚がすべて同じ種類なら」", r"3枚がすべて同じ種類なら"),
    ]

    specs = []
    for key, label in (
            ("tooltip.astral_dice.sign.jill_active", "JIL-L1"),
            ("astral_dice.guide.entry.jill_sign.1", "JIL-L2")):
        specs.append(_key_specs(
            key, label,
            _spec((act_zh_req, act_zh_forbid), (act_en_req, act_en_forbid),
                  (act_ja_req, act_ja_forbid))))
    for key, label in (
            ("tooltip.astral_dice.sign.jill_passive", "JIL-L3"),
            ("astral_dice.guide.entry.jill_sign.2", "JIL-L4")):
        specs.append(_key_specs(
            key, label,
            _spec((pas_zh_req, pas_zh_forbid), (pas_en_req, pas_en_forbid),
                  (pas_ja_req, pas_ja_forbid))))
    return specs


def deepseek_lang_specs():
    act_zh_req = [
        ("随机 1..20", r"1\s*[-–~～至到]\s*20"),
        ("持续 2:00", r"2:00"),
        ("随机 3 张牌", r"3\s*张"),
        ("按实际消耗", r"实际"),
        ("攻防等量", r"等量|等值|相同"),
    ]
    act_zh_forbid = [
        ("旧文案「100 星币」", r"100\s*星币"),
        ("旧文案「随机获得 1 个角色立牌或筹码」",
         r"随机获得[^。\n]{0,16}1\s*个角色立牌或筹码|1\s*个角色立牌或筹码"),
    ]
    act_en_req = [
        ("random 1..20", r"1\s*(?:[-–~]|to)\s*20"),
        ("2:00 duration", r"2:00"),
        ("3 random cards", r"3\s*(?:random\s*)?cards?"),
        ("actual cost", r"actual"),
        ("equal attack/defense", r"equal|same amount"),
    ]
    act_en_forbid = [
        ("old '100 star coins'", r"100\s*star\s*coins?"),
        ("old '1 random sign or chip'", r"1\s*random\s+sign\s+or\s+chip"),
    ]
    act_ja_req = [
        ("ランダム 1..20", r"1\s*[-–~〜～]\s*20"),
        ("2:00 持続", r"2:00"),
        ("ランダム 3 枚", r"3\s*枚"),
        ("実際の消費", r"実際"),
        ("攻防等量", r"等量|同じ"),
    ]
    act_ja_forbid = [
        ("旧文案「100 スターナ」", r"100\s*スターナ"),
        ("旧文案「スタンドかチップを 1 個（を）ランダム」",
         r"1\s*個(?:を)?\s*ランダム|ランダムで\s*1\s*個"),
    ]

    pas_zh_req = [
        ("静止 10 秒", r"10\s*秒"),
        ("移动累计 5 秒解除", r"5\s*秒"),
        ("摸鱼", r"摸鱼"),
    ]
    pas_zh_forbid = [
        ("旧文案「移动后立即清除」",
         r"移动后立即清除|移动则立即清除|移动立即清除|一旦移动立即清除"),
    ]
    pas_en_req = [
        ("idle 10 seconds", r"10\s*seconds"),
        ("move 5 seconds to clear", r"5\s*seconds"),
        ("Fishing", r"fishing"),
    ]
    pas_en_forbid = [
        ("old 'as soon as you move / moving clears it'",
         r"as\s+soon\s+as\s+you\s+move|moving\s+clears\s+it"
         r"|cleared\s+as\s+soon\s+as\s+you\s+move"),
    ]
    pas_ja_req = [
        ("静止 10 秒", r"10\s*秒"),
        ("移動 5 秒で解除", r"5\s*秒"),
        ("サボり", r"サボり"),
    ]
    pas_ja_forbid = [
        ("旧文案「移動すると即解除」",
         r"移動すると即解除|移動で即解除|移動するとすぐ解除"),
    ]

    specs = []
    for key, label in (
            ("tooltip.astral_dice.sign.deepseek_active", "DEE-L1"),
            ("astral_dice.guide.entry.deepseek_sign.1", "DEE-L2")):
        specs.append(_key_specs(
            key, label,
            _spec((act_zh_req, act_zh_forbid), (act_en_req, act_en_forbid),
                  (act_ja_req, act_ja_forbid))))
    for key, label in (
            ("tooltip.astral_dice.sign.deepseek_passive", "DEE-L3"),
            ("astral_dice.guide.entry.deepseek_sign.2", "DEE-L4")):
        specs.append(_key_specs(
            key, label,
            _spec((pas_zh_req, pas_zh_forbid), (pas_en_req, pas_en_forbid),
                  (pas_ja_req, pas_ja_forbid))))
    return specs


def nancy_lang_specs():
    act_zh_req = [
        ("固定 +6", r"\+6"),
        ("持续 2:00", r"2:00"),
        ("攻击敌对目标", r"敌对目标"),
    ]
    act_zh_forbid = [
        ("旧文案「×2」", r"×\s*2|[xX]2"),
        ("旧文案「最低 +2 / 保底」", r"最低\s*\+2|保底"),
        ("旧文案「消耗战斗牌」", r"消耗[^。\n]{0,24}战斗牌|战斗牌[^。\n]{0,24}消耗"),
        ("旧文案「或玩家」", r"或玩家"),
    ]
    act_en_req = [
        ("fixed +6", r"\+6"),
        ("2:00 duration", r"2:00"),
        ("hostile target", r"hostile"),
    ]
    act_en_forbid = [
        ("old x2", r"×\s*2|\bx2\b"),
        ("old 'minimum +2 / guaranteed +2'", r"minimum\s+of?\s*\+2|guaranteed\s+\+2|minimum\s+\+2"),
        ("old battle-card consumption",
         r"consum(?:e|es|ing)[^.\n]{0,30}battle\s+card|battle\s+card[^.\n]{0,30}consum"),
        ("old 'or player'", r"or\s+player"),
    ]
    act_ja_req = [
        ("固定 +6", r"\+6"),
        ("2:00 持続", r"2:00"),
        ("敵対ターゲット", r"敵対"),
    ]
    act_ja_forbid = [
        ("旧文案「×2」", r"×\s*2"),
        ("旧文案「最低 +2」", r"最低\s*\+2|最低限\s*\+2"),
        ("旧文案「戦闘カード消費」",
         r"戦闘カード[^。\n]{0,24}消費|消費[^。\n]{0,24}戦闘カード"),
        ("旧文案「またはプレイヤー」", r"またはプレイヤー"),
    ]

    pas_zh_req = [("范围 3 格", r"3\s*格"), ("被动 +3 保留", r"\+3")]
    pas_zh_forbid = [("旧范围 6 格", r"6\s*格")]
    pas_en_req = [("range 3 blocks", r"3\s*blocks?"), ("passive +3 retained", r"\+3")]
    pas_en_forbid = [("old range 6 blocks", r"6\s*blocks?")]
    pas_ja_req = [("範囲 3 ブロック", r"3\s*ブロック"), ("パッシブ +3 保持", r"\+3")]
    pas_ja_forbid = [("旧範囲 6 ブロック", r"6\s*ブロック")]

    msg_zh_req = [("敌对目标", r"敌对"), ("保留秒数占位符", r"%d")]
    msg_zh_forbid = [("actionbar 不含 §", r"§", True),
                     ("旧文案「任意一个目标」", r"任意一个目标|任意目标|攻击任意")]
    msg_en_req = [("hostile target", r"hostile"), ("seconds placeholder", r"%d")]
    msg_en_forbid = [("actionbar has no §", r"§", True),
                     ("old 'any target'", r"any\s+target")]
    msg_ja_req = [("敵対ターゲット", r"敵対"), ("秒数プレースホルダ", r"%d")]
    msg_ja_forbid = [("actionbar に § なし", r"§", True),
                     ("旧文案「任意のターゲット」", r"任意のターゲット|任意ターゲット")]

    specs = []
    for key, label in (
            ("tooltip.astral_dice.sign.nancy_lu_active", "NAN-L1"),
            ("astral_dice.guide.entry.nancy_lu_sign.1", "NAN-L2")):
        specs.append(_key_specs(
            key, label,
            _spec((act_zh_req, act_zh_forbid), (act_en_req, act_en_forbid),
                  (act_ja_req, act_ja_forbid))))
    for key, label in (
            ("tooltip.astral_dice.sign.nancy_lu_passive", "NAN-L3"),
            ("astral_dice.guide.entry.nancy_lu_sign.2", "NAN-L4")):
        specs.append(_key_specs(
            key, label,
            _spec((pas_zh_req, pas_zh_forbid), (pas_en_req, pas_en_forbid),
                  (pas_ja_req, pas_ja_forbid))))
    specs.append(_key_specs(
        "msg.astral_dice.nancy_lu_active", "NAN-L5",
        _spec((msg_zh_req, msg_zh_forbid), (msg_en_req, msg_en_forbid),
              (msg_ja_req, msg_ja_forbid))))
    return specs


LANG_SPECS = {
    "dorothy": dorothy_lang_specs,
    "jill": jill_lang_specs,
    "deepseek": deepseek_lang_specs,
    "nancy": nancy_lang_specs,
}


def load_lang(line, res):
    out = {}
    for lang in LANGS:
        rel = os.path.join(LANG_REL, lang + ".json")
        path = os.path.join(ROOT, line, rel)
        if not os.path.isfile(path):
            res.run(None, "LANG [%s] 文件不存在 %s" % (line, rel))
            continue
        try:
            out[lang] = json.loads(read_bytes(path).decode("utf-8-sig"))
        except Exception as exc:  # noqa: BLE001 - 解析失败按无法判定处理
            res.run(None, "LANG [%s] %s 解析失败: %r" % (line, rel, exc))
    return out


def check_lang_specs(line, lang_data, specs, res):
    for key, label, per_lang in specs:
        for lang in LANGS:
            data = lang_data.get(lang)
            if data is None:
                continue
            if key not in data:
                res.run(False, "%s [%s] [%s] 缺少键 %s" % (label, line, lang, key))
                continue
            raw = data[key]
            plain = re.sub(r"§.", "", raw)
            ok = True
            for req_label, pattern in per_lang[lang][0]:
                if re.search(pattern, plain, re.I):
                    res.run(True, "%s [%s] [%s] %s: %s"
                            % (label, line, lang, key, req_label))
                else:
                    res.run(False, "%s [%s] [%s] %s: 缺少%s"
                            % (label, line, lang, key, req_label))
                    ok = False
            for entry in per_lang[lang][1]:
                forb_label, pattern = entry[0], entry[1]
                use_raw = bool(entry[2]) if len(entry) > 2 else False
                target = raw if use_raw else plain
                if re.search(pattern, target, re.I):
                    res.run(False, "%s [%s] [%s] %s: %s"
                            % (label, line, lang, key, forb_label))
                    ok = False
                else:
                    res.run(True, "%s [%s] [%s] %s: 无%s"
                            % (label, line, lang, key, forb_label))


GROUP_CHECKS = {
    "dorothy": check_dorothy,
    "jill": check_jill,
    "deepseek": check_deepseek,
    "nancy": check_nancy,
}

# 与代码组配对、每组只跑一次的文档判据。
GROUP_DOC_CHECKS = {
    "nancy": check_nancy_spec_doc,
}


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Astral Dice four-sign P0 static gate "
                    "(neoforge-1.21.1 / forge-1.20.1; P1 excluded)")
    parser.add_argument("--only", choices=GROUPS,
                        help="run only one group (default: all four)")
    parser.add_argument("-v", "--verbose", action="store_true",
                        help="print passing checks too")
    args = parser.parse_args(argv)

    selected = [args.only] if args.only else list(GROUPS)
    res = Results(verbose=args.verbose)

    print("== 四立牌 P0 静态守门（neoforge-1.21.1 / forge-1.20.1；"
          "P1 neoforge-26.1.2 / fabric-1.20.1 明确不检查）==")
    print("selected: %s" % ", ".join(selected))

    lang_data = {}
    for line in LINES:
        lang_data[line] = load_lang(line, res)

    for group in selected:
        print("\n-- group: %s --" % group)
        for line in LINES:
            try:
                GROUP_CHECKS[group](line, res)
                check_lang_specs(line, lang_data[line], LANG_SPECS[group](), res)
            except OSError as exc:
                res.run(None, "GROUP %s [%s] 文件读取失败: %r"
                               % (group, line, exc))
        doc_check = GROUP_DOC_CHECKS.get(group)
        if doc_check is not None:
            doc_check(res)

    print()
    if res.unresolved:
        print("GATE: UNKNOWN  (%d 项无法判定 / 共 %d 项；另有 %d 项 FAIL)"
              % (len(res.unresolved), res.checks, len(res.fails)))
        for item in res.unresolved:
            print("  - " + item)
        return 2
    if res.fails:
        print("GATE: FAIL  (%d 项不满足 / 共 %d 项)"
              % (len(res.fails), res.checks))
        return 1
    print("GATE: PASS  (%d 项全部满足)" % res.checks)
    return 0


if __name__ == "__main__":
    sys.exit(main())
