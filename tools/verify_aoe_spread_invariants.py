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
    `LivingPageImpact.java`（活体书页**主命中**，非第二次波及）。`cardSpell` 是法伤白名单里
    **唯一**的本模组类型 ⇒ 一旦被用作 AOE 伤害源即形成「波及 → 法伤链 → 再波及」无限递归。
  - **A4b 块级**：每个 `beginAoe()…endAoe()` 块内**不得**出现 `diceDamage(`/`cardCost(`/
    `playerAttack(`（以玩家为 directEntity ⇒ 会重入骰战结算 = 递归入口）。
  - **A4c 实参级**：`.hurt(ModDamageTypes.cardSpell(...)` 这种**直接实参**形态同上只允许
    `LivingPageImpact.java`。
  ⚠️ 三层缺一不可：只查「直接实参」会漏掉「经局部变量传递」的形态（那是**永真门**，已实测）。
- **A5** 每个 `beginAoe();` 的**下一行必须**是 `try {` —— 保证窗口被 `try/finally` 真正包住：
  `beginAoe()` 与 `try` 之间若夹了任何语句，一旦它抛异常，配对的 `endAoe()` 不会被调用 ⇒
  守卫卡死（此后**所有**骰战结算都会被早退掉，表现为「打谁都不结算骰战」）。

退出码：0 通过 / 1 存在缺陷。
"""
import os
import re
import sys

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
    'combat/SpellDamageRegistry.java': 2,       # ① 定向爆破 AOE ② 电击手套 AOE
}

A4_ALLOWED_FILES = {'combat/LivingPageImpact.java'}


def read(p):
    with open(p, encoding='utf-8') as fh:
        return fh.read()


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
        src = read(p)
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
            src = read(p)
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
            src = read(p)
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
            src = read(p)

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
            src = read(p)
            for m in re.finditer(r'beginAoe\(\);(\r?\n[ \t]*)(\S[^\r\n]*)', src):
                nxt = m.group(2).strip()
                if not nxt.startswith('try {'):
                    fails.append(f'A5 {line}/{rel}: `beginAoe();` 之后不是 `try`'
                                 f'（下一行 = `{nxt[:48]}`）⇒ 窗口未被 try/finally 包住，'
                                 f'异常路径下守卫不归零')
        if verbose and not any(line in f for f in fails):
            print(f'    ok A5 {line}: 8 处 beginAoe() 均紧邻 try')
    return fails


def main():
    verbose = '-v' in sys.argv
    fails = []
    print('== 溅射/扩散伤害不变量（A1 窗口早退 / A2 旧布尔退役 / A3 开窗配对 / A4 不重入法伤链 / A5 紧邻 try）==')
    fails += check_a1(verbose)
    fails += check_a2(verbose)
    fails += check_a3(verbose)
    fails += check_a4(verbose)
    fails += check_a5(verbose)
    if fails:
        print()
        for f in fails:
            print('  [FAIL]', f)
        print(f'\nGATE: FAIL（{len(fails)} 项）')
        return 1
    print('\nGATE: PASS（四线 A1~A5 全部通过）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
