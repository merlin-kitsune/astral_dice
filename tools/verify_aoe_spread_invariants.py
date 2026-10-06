# -*- coding: utf-8 -*-
"""溅射/扩散伤害的「不重复吃溅伤、不递归」不变量守门（四线）。

## 为什么需要它
本模组的「扩散伤害」（顺劈/溅射/法伤波及/投掷落地/流星/轨道轰炸）必须**不递归**：
扩散伤害不得再次触发扩散或骰战结算。现状靠两条结构性保证：

1. **伤害源形状**：扩散伤害一律用 `directEntity == null` 的类型
   （`trueDamage` / `skillDamage` / `extraDamage` / `unreducibleDamage`）⇒
   `DiceCombatEvents#onLivingDamagePre` 的 `directEntity instanceof Player` 闸门天然早退；
   **唯一**以玩家为 directEntity 的注入是「反击」（`diceDamage`），它走 `counterDepth` 深度计数。
2. **内部波及窗口**：`DiceCombatEvents.beginAoe()/endAoe()` 包住每一次扩散，闸门顶部
   `if (isInternalAoe() || counterDepth > 0) return;` 让波及目标不再进入骰战结算。
   ⚠️ 窗口**必须**是深度计数而非布尔 —— 实测存在嵌套（活体书页命中 → 该伤害是
   `astral_dice:card_spell`、命中法伤白名单 ⇒ 进入法伤链 → onHit 再开定向爆破/电击手套的窗）。

## 判据（A1~A4）
- **A1** 四线 `DiceCombatEvents#onLivingDamagePre` 顶部含内部窗口早退
  `if (isInternalAoe() || counterDepth > 0) return;`
- **A2** 旧布尔字段 `aoeProcessing` 已彻底退役（全仓 0 命中）
- **A3** 四线「开窗点」计数与本表期望一致（新增/删改扩散点必须同步本表 ⇒ 有意摩擦），
  且每线 `beginAoe()` 总数 == `endAoe()` 总数（严格 try/finally 配对）
- **A4** **扩散/波及伤害不得使用会重入法伤链或骰战的类型**，分三层：
  - **A4a 文件级**：扩散文件里出现的 `ModDamageTypes.X(` 只允许 `null-directEntity` 家族
    （`trueDamage`/`skillDamage`/`extraDamage`/`unreducibleDamage`）；`cardSpell` 仅允许出现在
    **`A4_ALLOWED_FILES`** 列出的文件里（逐个理由见该集合上方注释；开发线放宽的三项由 **A6** 的补偿断言兜住）。
    `cardSpell` 是法伤白名单里**唯一**的本模组类型 ⇒ 一旦被用作**波及**伤害源即形成「波及 → 法伤链 → 再波及」无限递归。
  - **A4b 块级**：每个 `beginAoe()…endAoe()` 块内**不得**出现 `diceDamage(`/`cardCost(`/
    `playerAttack(`（以玩家为 directEntity ⇒ 会重入骰战结算 = 递归入口）。
  - **A4c 实参级**：`.hurt(ModDamageTypes.cardSpell(...)` 这种**直接实参**形态同上只允许
    `A4_ALLOWED_FILES` 里的文件。
  ⚠️ 三层缺一不可：只查「直接实参」会漏掉「经局部变量传递」的形态（那是**永真门**，已实测）。
- **A5** 每个 `beginAoe();` 的**下一行必须**是 `try {` —— 保证窗口被 `try/finally` 真正包住：
  `beginAoe()` 与 `try` 之间若夹了任何语句，一旦它抛异常，配对的 `endAoe()` 不会被调用 ⇒
  守卫卡死（此后**所有**骰战结算都会被早退掉，表现为「打谁都不结算骰战」）。
- **A6**（**开发线专属**）`A4_ALLOWED_FILES` 放宽的**补偿断言** —— 白名单里的 `cardSpell` 之所以安全，
  是因为开发线另有「扩散去重 + 重入闸门」两道锁；**这两道锁本身必须有断言**，否则把锁删掉 A1~A5 仍会 PASS。
  ⚠️ 断言必须判**生效形态**，不能只判「出现过」——实测（独立复核的内存变异）「去掉 `!` 取反」「保留调用但忽略返回值」
  都能骗过子串检查：
  - **A6a** 手套修饰器 `isActive` 必须**真正**要求 `DiceCombatEvents.isGloveSplashInstance()` **且取反**的
    `!DiceCombatEvents.isGloveSplashSettling()`（两个精确子串都在，含 `!`）；
  - **A6b** `DiceCombatEvents` 必须提供 `gloveSplashSettling` / `runAsGloveSplashSettlement` /
    `runAsCardSplashResolution` / `markCardSplashHit` 四个入口（定义处）；
  - **A6c** **含第二条 AOE 枚举**的白名单文件（`DamageCardFxScheduler`，其 6 格 AOE 与手套 3 格扩散
    锚点相同、半径 3 ⊂ 6 ⇒ 交集必非空）必须出现**生效形态**的去重调用
    `if (!DiceCombatEvents.markCardSplashHit(<x>)) continue;`（正则判定，光出现符号不算）；
  - **A6d** 两道锁的**调用点**（不只是定义）：`SpellDamageRegistry` 必须既用 `runAsGloveSplashSettlement(`
    包住手套扩散、又用生效形态去重；`DamageCardFxScheduler` 必须用 `runAsCardSplashResolution(` 开登记窗口。

退出码：0 通过 / 1 存在缺陷。

## ⚠️ 已知边界（诚实声明，2026-10-06 独立复核三轮确认后写定）
本守门是**文本启发式**判据（读源码 → 剥离注释 → 正则/子串）：它可靠拦住**真实回归**
（删除锚点、改写调用、去取反、`&&`→`||`、忽略返回值、把去重搬出枚举循环、把原文本藏进注释）——
本文件随附的 **17 例变异套件**逐条验证过（1 基线 PASS + 16 注入精确 FAIL）。
但它**无法根治「保留文本、破坏语义」的刻意混淆**，已实测仍能骗过的三类：
① **布尔短路/死分支**（把闸门写成 `false && <原表达式>`、`<原表达式> || true`，或把调用点包进 `if (false) {}`）；
② **字符串字面量**（`code_only()` 只剥注释，不剥 `"…"` ⇒ 把被要求文本放进字符串即可命中）；
③ **诱饵 `for (`**（`dedupe_inside_enumeration_window()` 只要求「某窗口块内同时出现 `for (` 与生效去重」，
不校验那是**哪个**循环 ⇒ 换个窗口块 + 塞一个空 `for` 即可）。
⇒ **要彻底闭合，必须换用不依赖文本的判据**：AST 解析循环体 / 先剥字符串字面量再匹配 /
断言布尔上下文（`isActive` 的主干就是该合取，未被 `false &&` 或 `|| true` 包裹）/
或补一条**运行时用例**验证「交叠目标只结算一次」。**本轮不做**（威胁模型是"改了扩散逻辑忘了同步"，
不是"有人刻意骗过门禁"）；如将来要防刻意混淆，按上述四选一升级。
"""
import os
import re
import sys
# --- stdout/stderr 编码自愈（统一口径：python 侧强制 UTF-8；勿删）---
# 背景：Windows 默认代码页 936 下 python 子进程的 sys.stdout.encoding 为 gbk，
# 本脚本打印的 ⇒/⚠/✅/↔ 等符号 GBK 不可表示 ⇒ 直接 UnicodeEncodeError
# 退出码 1（“门会红但红的原因是崩溃”）。此块使脚本不依赖调用者环境与宿主编码。
# ⚠ 块内引用 sys ⇒ 必须位于**所有 import 之后**；也**不得**加 except-pass（会静默失效）。
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
LINES = ['neoforge-1.21.1', 'forge-1.20.1', 'neoforge-26.1.2', 'fabric-1.20.1']
REL = 'src/main/java/com/merlinkitsune/astral_dice'

# A3：开窗点期望表（每线）—— 改扩散逻辑必须同步改这里
EXPECTED_BEGIN = {
    'combat/DiceCombatEvents.java': 2,          # ① 额外加伤段 ② 大当家「战斗爽·溅射」
    'combat/LivingPageImpact.java': 1,          # 活体书页主命中（card_spell，会进法伤链）
    'combat/OrbitalBombardmentManager.java': 1,  # 轨道轰炸单次落地
    'combat/SherryThrowManager.java': 1,        # 怪力侦探投掷落地
    'combat/ShootingStarManager.java': 1,       # 流星筹码命中
    # ⚠️ 本仓（开发线 `multi-dev-next`）口径：四张伤害效果牌的 FX 重做把「定向爆破 AOE」整段
    #    从 `SpellDamageRegistry` 迁到 `combat/DamageCardFxScheduler#resolveBlast`（2026-10-04）⇒
    #    本线 `SpellDamageRegistry` 只剩手套 1 处；另新增 `DamageCardFxScheduler` 2 处与
    #    `HuoCardItem`（符卡-祸立即命中）1 处。**发布线 `multi-main` 无这两项**（那边仍是 2 处）。
    'combat/SpellDamageRegistry.java': 1,       # 电击手套 AOE（爆破 AOE 已迁走）
    'combat/DamageCardFxScheduler.java': 2,     # ① FX 命中主目标 ② 定向爆破 6 格 AOE
    'item/card/HuoCardItem.java': 1,            # 符卡-祸 立即命中
}

# A4：允许出现 `cardSpell` 的扩散文件（白名单，逐个都要有理由；放宽处由 A6 的补偿断言兜住）
#   · LivingPageImpact —— 活体书页**主命中**（有意走法伤链），全程只有一条枚举 ⇒ 无需去重
#   · DamageCardFxScheduler —— 四张伤害效果牌的**主命中**与「定向爆破 6 格 AOE」都用 card_spell：
#     该 AOE **已不在修饰器链里**（由牌的 Pending 驱动）⇒ 不存在「波及 → 法伤链 → 再波及」的自递归；
#     与手套 3 格扩散的交叠由 `markCardSplashHit` 去重（**A6c/A6d 强制**）
#   · HuoCardItem —— 符卡-祸的立即命中（同上，属「牌自己的伤害」；单枚举）
A4_ALLOWED_FILES = {
    'combat/LivingPageImpact.java',
    'combat/DamageCardFxScheduler.java',
    'item/card/HuoCardItem.java',
}

# A6：A4 白名单放宽的**补偿断言**（判「生效形态」，不判「出现过」）
# ⚠️ 必须断言**连续表达式**：只查两个子串会被「去掉 `!`」与「`&&` → `||`」两种语义反转骗过（独立复核实测）。
A6_GLOVE_GATE_RX = re.compile(
    r'DiceCombatEvents\s*\.\s*isGloveSplashInstance\(\)\s*&&\s*!\s*'
    r'DiceCombatEvents\s*\.\s*isGloveSplashSettling\(\)')
A6_REQUIRED_SYMBOLS = ('gloveSplashSettling', 'runAsGloveSplashSettlement',
                       'runAsCardSplashResolution', 'markCardSplashHit')
# A6c：含**第二条 AOE 枚举**的白名单文件（必须生效去重）
A6_SPREAD_DEDUPE_REQUIRED = ('combat/DamageCardFxScheduler.java',)
# A6d：两道锁的**调用点**（不只是定义）
A6_CALL_SITES = {
    'combat/DamageCardFxScheduler.java': ('runAsCardSplashResolution(', True),
    'combat/SpellDamageRegistry.java': ('runAsGloveSplashSettlement(', True),
}
# 去重的**生效形态**：if (!DiceCombatEvents.markCardSplashHit(<实参>)) continue;
DEDUPE_EFFECTIVE_RX = re.compile(
    r'if\s*\(\s*!\s*DiceCombatEvents\s*\.\s*markCardSplashHit\s*\([^)]*\)\s*\)\s*continue\s*;')
# 波及窗口块（beginAoe()…endAoe()）
A6_WINDOW_RX = re.compile(r'beginAoe\(\);(.*?)endAoe\(\);', re.S)


def dedupe_inside_enumeration_window(src):
    """去重调用必须落在「同时含多目标枚举（`for (`）」的波及窗口块内。

    ⚠️ 只判「文件内出现过该形态」是不够的（独立复核实测：把去重行搬出 `for (e : nearby)` 循环、
    作为活代码放到别的方法里，仍能骗过纯子串/全文正则检查）。
    """
    for m in A6_WINDOW_RX.finditer(src):
        body = m.group(1)
        if 'for (' in body and DEDUPE_EFFECTIVE_RX.search(body):
            return True
    return False


def read(p):
    with open(p, encoding='utf-8') as fh:
        return fh.read()


# ⚠️ 所有判据都在**剥离注释后**的源码上跑：否则「把真码改成失效形态、再在被删位置留一条注释保住原文本」
#    即可骗过子串/正则检查（独立复核实测的把戏）。本函数是**宽松**剥离（不解析字符串字面量），
#    对本文件要匹配的模式足够；它只会让判据更严，不会放过真缺口。
_COMMENT_RX = re.compile(r'/\*.*?\*/|//[^\n]*', re.S)


def code_only(src):
    return _COMMENT_RX.sub('', src)


def read_code(p):
    """读源码并剥离注释（判据统一入口）。"""
    return code_only(read(p))


def java_files(line):
    base = os.path.join(ROOT, line, REL)
    for dirpath, _d, files in os.walk(base):
        for fn in files:
            if fn.endswith('.java'):
                yield os.path.join(dirpath, fn)


def check_a1(verbose):
    fails = []
    for line in LINES:
        p = os.path.join(ROOT, line, REL, 'combat/DiceCombatEvents.java')
        src = read_code(p)
        m = re.search(r'public static void onLivingDamagePre\(.*?\n(.*?\n)    \}', src, re.S)
        body = m.group(1) if m else src
        needle = 'if (isInternalAoe() || counterDepth > 0) return;'
        if needle not in body:
            fails.append(f'A1 {line}/combat/DiceCombatEvents.java: onLivingDamagePre 缺少内部窗口早退 `{needle}`')
        elif verbose:
            print(f'    ok A1 {line}: 窗口早退在位')
    return fails


def check_a2(verbose):
    fails = []
    for line in LINES:
        for p in java_files(line):
            src = read_code(p)
            if 'aoeProcessing' in src:
                fails.append(f'A2 {os.path.relpath(p, ROOT)}: 旧布尔字段 aoeProcessing 仍存在（应已改为 aoeDepth）')
        if verbose and not any(line in f for f in fails):
            print(f'    ok A2 {line}: 无 aoeProcessing 残留')
    return fails


def check_a3(verbose):
    fails = []
    for line in LINES:
        for rel, want in sorted(EXPECTED_BEGIN.items()):
            p = os.path.join(ROOT, line, REL, rel)
            if not os.path.isfile(p):
                fails.append(f'A3 {line}/{rel}: 文件不存在')
                continue
            src = read_code(p)
            n_begin = len(re.findall(r'beginAoe\(\);', src))
            n_end = len(re.findall(r'endAoe\(\);', src))
            if n_begin != want:
                fails.append(f'A3 {line}/{rel}: 开窗点 {n_begin} 处，期望 {want} 处'
                             f'（改扩散逻辑须同步本脚本的 EXPECTED_BEGIN）')
            if n_begin != n_end:
                fails.append(f'A3 {line}/{rel}: beginAoe={n_begin} 与 endAoe={n_end} 不配对')
            elif verbose:
                print(f'    ok A3 {line}/{rel}: 开窗 {n_begin} 处、配对')
    return fails


def check_a4(verbose):
    fails = []
    for line in LINES:
        for rel in sorted(EXPECTED_BEGIN):
            p = os.path.join(ROOT, line, REL, rel)
            if not os.path.isfile(p):
                continue
            src = read_code(p)

            # ⚠️ 正则必须容忍 `ModDamageTypes` 与 `.foo(` 之间的**换行与缩进**（本模组普遍写成
            #    `...ModDamageTypes\n        .trueDamage(...)`）—— 否则判据会变成**永真门**
            #    （实测：早期写成 `ModDamageTypes\.(\w+)\(` 时，把 trueDamage 换成 cardSpell 也抓不到）。
            TYPE_RX = re.compile(r'ModDamageTypes\s*\.\s*(\w+)\s*\(')

            # A4a 文件级：扩散文件里出现的伤害类型必须是 null-directEntity 家族
            for t in sorted(set(TYPE_RX.findall(src))):
                if t in ('trueDamage', 'skillDamage', 'extraDamage', 'unreducibleDamage'):
                    continue
                if t == 'cardSpell' and rel in A4_ALLOWED_FILES:
                    continue
                if t == 'diceDamage' and rel == 'combat/DiceCombatEvents.java':
                    continue  # 反击注入（走 counterDepth）；由 A4b 保证它不落在扩散块内
                fails.append(f'A4a {line}/{rel}: 扩散文件里出现 `ModDamageTypes.{t}(` —— '
                             f'它{"" if t != "cardSpell" else "（法伤白名单类型）"}会重入'
                             f'{"法伤链" if t == "cardSpell" else "骰战结算"} ⇒ 扩散伤害必须用 '
                             f'trueDamage/skillDamage/extraDamage/unreducibleDamage')

            # A4b 块级：beginAoe()…endAoe() 块内不得出现「以玩家为 directEntity」的类型
            for m in re.finditer(r'beginAoe\(\);(.*?)endAoe\(\);', src, re.S):
                block = m.group(1)
                for bad in ('diceDamage', 'cardCost', 'playerAttack'):
                    if re.search(r'(?:ModDamageTypes\s*\.\s*%s\s*\(|damageSources\(\)\s*\.\s*%s\s*\()'
                                 % (bad, bad), block):
                        fails.append(f'A4b {line}/{rel}: 扩散窗口内出现 `{bad}(` '
                                     f'（以玩家为 directEntity ⇒ 会重入骰战结算 = 递归入口）')

            # A4c 实参级：.hurt(<直接写 cardSpell>)
            for _m in re.finditer(r'\.hurt\(\s*(?:[\w.]+\.)?ModDamageTypes\s*\.\s*cardSpell\s*\(', src):
                if rel not in A4_ALLOWED_FILES:
                    fails.append(f'A4c {line}/{rel}: 波及伤害直接用 cardSpell 作 hurt 实参')

        if verbose and not any(line in f for f in fails):
            print(f'    ok A4 {line}: 扩散伤害源均为 null-directEntity 家族、未重入法伤链/骰战')
    return fails


def check_a5(verbose):
    """A5：beginAoe() 的下一行必须是 try {（窗口真正被 try/finally 包住）。"""
    fails = []
    for line in LINES:
        for rel in sorted(EXPECTED_BEGIN):
            p = os.path.join(ROOT, line, REL, rel)
            if not os.path.isfile(p):
                continue
            src = read_code(p)
            for m in re.finditer(r'beginAoe\(\);(\r?\n[ \t]*)(\S[^\r\n]*)', src):
                nxt = m.group(2).strip()
                if not nxt.startswith('try {'):
                    fails.append(f'A5 {line}/{rel}: `beginAoe();` 之后不是 `try`'
                                 f'（下一行 = `{nxt[:48]}`）⇒ 窗口未被 try/finally 包住，'
                                 f'异常路径下守卫不归零')
        if verbose and not any(line in f for f in fails):
            print(f'    ok A5 {line}: {sum(len(re.findall(r"beginAoe\(\);", read(os.path.join(ROOT, line, REL, rel)))) for rel in EXPECTED_BEGIN)} 处 beginAoe() 均紧邻 try')
    return fails


def check_a6(verbose):
    """A6（开发线专属）：A4 白名单放宽的补偿断言 —— 扩散去重 / 重入闸门必须**生效**在位。"""
    fails = []
    for line in LINES:
        # A6a 手套修饰器的准入判据（断言**连续表达式**：取反 `!` 与 `&&` 都是判据的一部分）
        p = os.path.join(ROOT, line, REL, 'combat/SpellDamageRegistry.java')
        src = read_code(p)
        if not A6_GLOVE_GATE_RX.search(src):
            fails.append(f'A6a {line}/combat/SpellDamageRegistry.java: 手套修饰器 isActive 未同时要求 '
                         f'`DiceCombatEvents.isGloveSplashInstance() && !DiceCombatEvents.isGloveSplashSettling()` '
                         f'⇒ 扩散失去准入闸门（会递归或越界生效；⚠️ 去掉 `!`、把 `&&` 改成 `||` 都算失效）')
        # A6b 两道锁的入口定义
        p = os.path.join(ROOT, line, REL, 'combat/DiceCombatEvents.java')
        src = read_code(p)
        for sym in A6_REQUIRED_SYMBOLS:
            if sym not in src:
                fails.append(f'A6b {line}/combat/DiceCombatEvents.java: 缺少 `{sym}`'
                             f'（开发线扩散去重/重入闸门的入口，删掉即静默失效）')
        # A6c 含第二条枚举的白名单文件必须**生效**去重
        for rel in A6_SPREAD_DEDUPE_REQUIRED:
            p = os.path.join(ROOT, line, REL, rel)
            if not os.path.isfile(p):
                fails.append(f'A6c {line}/{rel}: 文件不存在')
                continue
            if not dedupe_inside_enumeration_window(read_code(p)):
                fails.append(f'A6c {line}/{rel}: 缺少**生效形态**的去重调用 '
                             f'`if (!DiceCombatEvents.markCardSplashHit(...)) continue;` ⇒ 与手套 3 格扩散的交叠目标'
                             f'会**重复吃扩散**（该文件含第二条 AOE 枚举，锚点与半径都构成内包含；'
                             f'⚠️ 只出现符号、忽略返回值不算生效）')
        # A6d 两道锁的**调用点**（第二项 True = 该文件还必须出现生效形态的去重）
        for rel, (sym, need_dedupe) in sorted(A6_CALL_SITES.items()):
            p = os.path.join(ROOT, line, REL, rel)
            if not os.path.isfile(p):
                fails.append(f'A6d {line}/{rel}: 文件不存在')
                continue
            src = read_code(p)
            if sym not in src:
                fails.append(f'A6d {line}/{rel}: 缺少调用点 `{sym}` ⇒ 去重/重入闸门没有被真正挂上')
            if need_dedupe and not dedupe_inside_enumeration_window(src):
                fails.append(f'A6d {line}/{rel}: 缺少**生效形态**的去重调用 '
                             f'`if (!DiceCombatEvents.markCardSplashHit(...)) continue;`（该文件是登记方）')
        if verbose and not any(line in f for f in fails):
            print(f'    ok A6 {line}: 去重+重入闸门四入口在位、调用点挂上、去重为生效形态')
    return fails


def main():
    verbose = '-v' in sys.argv
    fails = []
    print('== 溅射/扩散伤害不变量（A1 窗口早退 / A2 旧布尔退役 / A3 开窗配对 / A4 不重入法伤链 / A5 紧邻 try / A6 开发线去重链）==')
    fails += check_a1(verbose)
    fails += check_a2(verbose)
    fails += check_a3(verbose)
    fails += check_a4(verbose)
    fails += check_a5(verbose)
    fails += check_a6(verbose)
    if fails:
        print()
        for f in fails:
            print('  [FAIL]', f)
        print(f'\nGATE: FAIL（{len(fails)} 项）')
        return 1
    print('\nGATE: PASS（四线 A1~A6 全部通过）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
