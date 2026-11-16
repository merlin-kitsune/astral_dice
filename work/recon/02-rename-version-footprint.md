# 02 · 「试剂-深渊」改名 + 版本号 → 1.5.0 影响面测绘（只读取证）

- 工作树：`F:\MCProject\astral_dice_multiloader`
- 分支 / HEAD：`multi-main` / `fd990c15`（`git -C F:\MCProject\astral_dice_multiloader rev-parse --short HEAD`）
- 取证时间：2026-11-02（Asia/Hong_Kong）
- 取证方式：全部 `grep -rn --binary-files=without-match` + `sed -n` + `diff -q`，**未运行 gradlew、未启动游戏、未修改任何仓库文件**（唯一新建文件 = 本报告）。
- 工作树预置状态（非本次分析所致，勿触碰）：`AGENTS.md` M、`docs/agent/AGENTS-dsh-linux.md` M、`docs/agent/AGENTS-full.md` M、`docs/compat-1.5.0-porting.md` M、`fabric-1.20.1/build.gradle` M、`fabric-1.20.1/src/generated/**` 大量 D。

---

## 0. 结论速览（可直接执行的判定）

| 项 | 结论 |
|---|---|
| A「必须改」文件总数 | **4 个**（四线各一份 `zh_cn.json`，每份恰好 1 行） |
| A 玩家可见文案改动量 | `item.astral_dice.abyss_reagent_chip` 值：`试剂-深渊` → `深渊试剂` |
| A tooltip / 手册条目是否需要改 | **不需要**。tooltip 值 (`装备时治愈层数 §e+2§7…`) 与手册正文值都不含物品名；手册 "条目名" 是 lang 键引用 |
| A Java 注释（30 处） | 非玩家可见 ⇒ **不必须**；若要求全仓字符串零残留，则一并改（纯文本替换即可，零功能风险） |
| A 类似串误伤风险 | **无**。带空格写法 `试剂 - 深渊`、破折号写法 `试剂—深渊` 全仓 0 命中 |
| B 必改行 | **4 行**（四线 `gradle.properties` 的 `mod_version`） |
| B 产物改名 | 由 `mod_version` 直接决定 ⇒ 自动变 `astral_dice-1.5.0+<loader>.jar`；**无 sources jar**，只需一个 jar 名 |
| B CI 写死版本 | **无**。CI 动态读 `gradle.properties`，且 tag 由该值派生；无 rc 后 `is_prerelease` 由 1 变 0 |
| B 待补 | `release/1.5.0/` 目录不存在 ⇒ 打 tag 后 Release 正文退化为附件清单 |

---

## A. 「试剂-深渊」影响面

### A.1 全仓命中三分类

全仓命中 123 个文件（含 .git / build / bin / run / temp / release 等生成物与历史副本）。按「是否面向玩家」分类如下。

#### A.1.1 必须改（面向玩家，字符串字面量）

| # | 文件（绝对路径） | 行号 | 键 / 上下文 | 当前值 |
|---|---|---|---|---|
| 1 | `F:\MCProject\astral_dice_multiloader\neoforge-1.21.1\src\main\resources\assets\astral_dice\lang\zh_cn.json` | **379** | `item.astral_dice.abyss_reagent_chip` | `试剂-深渊` |
| 2 | `F:\MCProject\astral_dice_multiloader\neoforge-26.1.2\src\main\resources\assets\astral_dice\lang\zh_cn.json` | **888** | 同上 | `试剂-深渊` |
| 3 | `F:\MCProject\astral_dice_multiloader\forge-1.20.1\src\main\resources\assets\astral_dice\lang\zh_cn.json` | **385** | 同上 | `试剂-深渊` |
| 4 | `F:\MCProject\astral_dice_multiloader\fabric-1.20.1\src\main\resources\assets\astral_dice\lang\zh_cn.json` | **921** | 同上 | `试剂-深渊` |

证据（原文行）：
```json
neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/zh_cn.json:379:  "item.astral_dice.abyss_reagent_chip": "试剂-深渊",
```
四线均为**同一键、同一值、单一命中**（`grep -c` 每文件 1 行）。

**源码中不存在面向玩家的硬编码字符串** —— 四线 `src/main` 内全部 30 处命中断言为注释（详见 A.1.3），无一处位于 Java 字符串字面量。

#### A.1.2 不能改 / 不用改（非玩家可见，或不属本次改动面）

| 类别 | 位置（文件:行号） | 理由 |
|---|---|---|
| Java 注释 / javadoc（30 行，四线） | 见 A.1.3 逐行清单 | 非玩家可见；改名不要求 |
| 中文文档 | `docs/agent/AGENTS-full.md:1201,1324`；`docs/chip-recipe-plan.md:75,79`；`docs/chip-shop-logic-plan.md:55`；`docs/features/effect-client-server-split-audit.md`（含目标串「深渊试剂」） | 历史 / 工程文档 |
| 变更日志 | `CHANGELOG_ZH.md:127,131,426,462,614` | 已发布历史记录，改写会篡改历史 |
| README | `README_ZH.md:39` | 介绍性正文，随发布说明单独处理 |
| 备份规则文件 | `AGENTS.md.bak-a-plan-183829`（2 处）、`AGENTS.md.bak-slim-20261016-184259`（2 处） | 备份快照 |
| 移植报告 | `porting/fabric-1.20.1/B23-CHIP-TOOLTIP-REPORT.md:96,192,204` | 历史报告 |
| 历史发布说明 | `release/1.5.0-rc.1~rc.4` 的 `PLAYER_CHANGELOG_ZH.md:195`、`MCMOD.html:157`；`release/1.5.0-rc.5`/`rc.6` 各 2 处 | 已发布版本快照 |
| 生成物 | `temp/**`（30 文件：pre6-p0-live-evidence、tester1211_evidence 日志、`temp/lang_*.json`、`temp/ModItems_*.java`、`temp/patch_changelog_dmg.py`）、`run/**`（19 文件）、`build/**`、`bin/**`（`build/resources/main/.../lang/*.json`、`bin/main/.../lang/*.json` 均为 src 的编译拷贝） | 可由源码重建 |
| `.superpowers/**`（8 处） | 工具内部记录 | 非产品内容 |
| 测试台脚本注释 | `scripts/test/resources/kubejs/{1.21.1,1.20.1}/client_scripts/astral_pmfix_client.js:4` | 仅注释；脚本按 lang 键取文案，**不断言物品名** |
| 测试台运行副本 | `run/1.21.1/kubejs/client_scripts/astral_pmfix_client.js:4` | 与上一条 `diff -q` **IDENTICAL**（各 8169 B），是投放副本 |
| 自动生成快照 | `scripts/test/cases/.mt_autojs.json`（8 处）、`.mt_snapshot.json`（8 处） | 测试台自动生成，下次运行重写 |

> 建议同步（非必须，改了不会破坏机器断言）：`scripts/test/cases/PMFIX-TIP-1.21.1.json` 与 `PMFIX-TIP-1.20.1.json` 各 4 处（`title` / `note` / vision 提问文本含「试剂-深渊」）。其机器断言（log/absent 正则）只锚 `tooltip.astral_dice.chip.abyss_reagent` 文案与 ASTRA lang 键，**不含物品名**，故改名后不会变红；但 vision 提问会读起来过时。

#### A.1.3 四线 Java 注释逐行清单（30 行，全部为注释，非字符串）

| 文件 | 行号 | 原文 |
|---|---|---|
| `neoforge-1.21.1/.../component/ModAttachments.java` | 449 | `// 试剂-深渊筹码:「装备时 +2 治愈点」的装备会话闸门(玩家级,持久化)。` |
| `neoforge-1.21.1/.../datagen/ModRecipeProvider.java` | 996 | `// 试剂-深渊:上排 海晶碎片·海洋之心·海晶碎片｜中排 再生试剂·空白筹码·再生试剂｜下排 星盘·星盘·星盘 [史诗·治愈]` |
| `neoforge-1.21.1/.../event/ModTooltipHandler.java` | 775 | `// 试剂-深渊(史诗/治愈):文案 + 治愈层数计数` |
| `neoforge-1.21.1/.../item/chip/AbyssReagentChipItem.java` | 20 | ` * 试剂-深渊筹码(史诗 / 治愈)。` |
| `neoforge-1.21.1/.../item/HealingManager.java` | 19, 21, 237, 252, 272, 291, 298 | javadoc / 行注释（7 行） |
| `neoforge-1.21.1/.../item/ModItems.java` | 914 | `// 试剂-深渊(史诗/治愈):装备时治愈层数 +2(卸下 -2);触发治愈回血时获得「有效治疗量 ×2」的伤害吸收。` |
| `neoforge-26.1.2/.../component/ModAttachments.java` | 454 | 同构 |
| `neoforge-26.1.2/.../datagen/ModRecipeProvider.java` | 995 | 同构 |
| `neoforge-26.1.2/.../event/ModTooltipHandler.java` | 845 | 同构 |
| `neoforge-26.1.2/.../item/chip/AbyssReagentChipItem.java` | 20 | 同构 |
| `neoforge-26.1.2/.../item/HealingManager.java` | 378 | 1 行 |
| `neoforge-26.1.2/.../item/ModItems.java` | 1132 | 同构 |
| `forge-1.20.1/.../component/ModAttachments.java` | 384 | 同构 |
| `forge-1.20.1/.../datagen/ModRecipeProvider.java` | 1056 | 同构 |
| `forge-1.20.1/.../event/ModTooltipHandler.java` | 798 | 同构 |
| `forge-1.20.1/.../item/chip/AbyssReagentChipItem.java` | 20 | 同构 |
| `forge-1.20.1/.../item/HealingManager.java` | 20, 22, 238, 253, 273, 292, 299 | javadoc / 行注释（7 行） |
| `forge-1.20.1/.../item/ModItems.java` | 915 | 同构 |
| `fabric-1.20.1/.../component/ModAttachments.java` | 406 | 同构 |
| `fabric-1.20.1/.../datagen/ModRecipeProvider.java` | 1001 | 同构 |
| `fabric-1.20.1/.../event/ModTooltipHandler.java` | 776 | 同构 |
| `fabric-1.20.1/.../item/chip/AbyssReagentChipItem.java` | 19 | 同构 |
| `fabric-1.20.1/.../item/HealingManager.java` | 291 | 1 行 |
| `fabric-1.20.1/.../item/ModItems.java` | 868 | 同构 |

（`...` = `src/main/java/com/merlinkitsune/astral_dice`）

### A.2 显示名 / tooltip / 手册：确切机制

**① 物品显示名来源键 = `item.astral_dice.abyss_reagent_chip`**（四线同键），三语现值：

| 语言 | 值 | neoforge-1.21.1 | neoforge-26.1.2 | forge-1.20.1 | fabric-1.20.1 |
|---|---|---|---|---|---|
| `zh_cn` | `试剂-深渊` | lang/zh_cn.json:379 | :888 | :385 | :921 |
| `en_us` | `Reagent - Abyss` | lang/en_us.json:379 | :888 | :385 | :921 |
| `ja_jp` | `試薬 - 深淵` | lang/ja_jp.json:378 | :888 | :384 | :921 |

> **待裁决**：en_us / ja_jp **字面不含「试剂-深渊」**，故不属于本次替换命中面；但它是同一旧语序（`A - B`）的直译。若「深渊试剂」是术语纠偏（而非仅中文措辞），建议同步为 `Abyss Reagent` / `深淵試薬`（保留 `A B` 正序）。此项不在本次 grep 命中集内，需 lead 明示。

**② tooltip 文案键 = `tooltip.astral_dice.chip.abyss_reagent`，值不含物品名 ⇒ 不需改**
- zh_cn：`装备时治愈层数 §e+2§7。触发治愈回血时：获得实际治疗量 × §e2§7 的伤害吸收`（:601 / :926 / :601 / :1001）
- en_us：`On equip: healing layers §e+2§7. When healing is triggered: gain damage absorption equal to the actual healing × §e2§7`
- ja_jp：`装備時：治癒スタック §e+2§7。…実際の治療量 × §e2§7 のダメージ吸収を獲得`

**③ 手册机制 = 纯 Patchouli 资源目录，没有 Java 侧的 guide 文案数据**
- 书定义：`<线>/src/main/resources/data/astral_dice/patchouli_books/astral_guide/book.json`
- 条目：`<线>/src/main/resources/assets/astral_dice/patchouli_books/astral_guide/en_us/entries/chips_healing/abyss_reagent_chip.json`
  四线该文件同 4 行引用（**全是 id 或 lang 键，改中文值即自动生效，json 不需动**）：
  ```
  2: "name": "item.astral_dice.abyss_reagent_chip",     ← 引用 A.2① 的键 ⇒ 中文值一改，手册条目标题跟着变
  4: "icon": "astral_dice:abyss_reagent_chip",          ← 物品 id，不改
  8:     "item": "astral_dice:abyss_reagent_chip",      ← 物品 id，不改
  9:     "text": "astral_dice.guide.entry.abyss_reagent_chip.1"
  ```
- 手册正文值（含 `×2` 但**不含物品名**，无需改）：键 `astral_dice.guide.entry.abyss_reagent_chip.1` =
  `装备时治愈层数 +2；触发治愈回血时，获得「实际治疗量 ×2」的伤害吸收。`
  位置：`neoforge-1.21.1/.../lang/zh_cn.json:132`、`neoforge-26.1.2/.../zh_cn.json:868`、`forge-1.20.1/.../zh_cn.json:132`、`fabric-1.20.1/.../zh_cn.json:922`。
- `astral_guide` 只有 `en_us` 与 `zh_cn` 两个语言目录；`en_us/entries` 164 个 json，`zh_cn/entries` 仅 5 个（仅 signs 子目录，`chips_healing` 为 0）⇒ 手册条目物理上单份，语言差异全在 lang 文件。
- 手册红线（后续改名注意）：条目正文换行用 `$(br)`，正文禁用 `§` 染色。本条只改 lang 值，不触及此红线。

### A.3 近似串与 id 引用（防误伤）

| 检索串 | 命中数 | 结论 |
|---|---|---|
| `试剂 - 深渊`（半角空格） | **0** | 无误伤 |
| `试剂—深渊`（破折号 U+2014） | **0** | 无误伤 |
| `深渊试剂`（目标串，全仓） | 仅出现在**非产品路径**：`docs/features/effect-client-server-split-audit.md:198`、`porting/fabric-1.20.1/B1-CHIPS-WIRING-REPORT.md:32`、`porting/fabric-1.20.1/MAPPING-1.5.0.md:150,459` | 说明文档早已用过「深渊试剂」这一正序写法，可作术语依据 |

`abyss_reagent` 命中面（**id 不改**，全部保持原样；n = 该文件命中行数）：

| 文件:行 | 说明 |
|---|---|
| 四线 `patchouli_books/astral_guide/en_us/entries/chips_healing/abyss_reagent_chip.json`:2,4,8,9 | 手册条目引用（见 A.2③） |
| 四线 `data/curios/tags/item/chip.json:66` 或 `data/curios/tags/items/chip.json:66` | Curios 标签，1.21 用 `item`、1.20.1 用 `items` |
| `neoforge-1.21.1` 与 `forge-1.20.1`：`data/astral_dice/tags/item(s)/chips.json:67` | 物品标签 |
| `neoforge-1.21.1` 与 `forge-1.20.1`：`data/bountiful/bounty_pools/bountiful/astral_rews.json:1253,1256` | 赏金奖励池 |
| 四线 `src/main/java/**/item/chip/AbyssReagentChipItem.java`、`ModItems.java`、`ModRecipeProvider.java`、`ModTooltipHandler.java` 等 | 类名 / 常量 / 配方与 tooltip 键构造，均为 id 语义 |
| 四线 `lang/*.json` 的键名（`item.astral_dice.abyss_reagent_chip`、`tooltip.astral_dice.chip.abyss_reagent`、`astral_dice.guide.entry.abyss_reagent_chip.1`） | 键名不改，只改 zh_cn 的值 |
| 四线 `lang/en_us.json` / `ja_jp.json` 对应行的**键** | 同上 |

> 唯一需要「人眼确认不误伤」的陷阱：**改 zh_cn 值时必须只替换值部分，不能碰键**。键里同时含 `abyss_reagent_chip`，而值里含 `试剂-深渊` —— 用 `sed` 全行替换 `试剂-深渊`→`深渊试剂` 是安全的（键中不含该中文字符串），但**不要**用 `abyss_reagent` 之类的 id 串做正则锚。

### A.4 改动的验收基线（「必须改」精确清单）

必须改 4 个文件 / 4 行：

```
F:\MCProject\astral_dice_multiloader\neoforge-1.21.1\src\main\resources\assets\astral_dice\lang\zh_cn.json   第 379 行
F:\MCProject\astral_dice_multiloader\neoforge-26.1.2\src\main\resources\assets\astral_dice\lang\zh_cn.json 第 888 行
F:\MCProject\astral_dice_multiloader\forge-1.20.1\src\main\resources\assets\astral_dice\lang\zh_cn.json     第 385 行
F:\MCProject\astral_dice_multiloader\fabric-1.20.1\src\main\resources\assets\astral_dice\lang\zh_cn.json    第 921 行
```

改后这些行应为（缩进/逗号/CRLF 保持不变，逐行替换即可）：

```json
  "item.astral_dice.abyss_reagent_chip": "深渊试剂",
```

**验收命令（在 `F:\MCProject\astral_dice_multiloader` 下，git bash）：**

```bash
# 1) 玩家可见面必须 0 残留
grep -rn --binary-files=without-match '试剂-深渊'   neoforge-1.21.1/src/main/resources/assets/astral_dice/lang   neoforge-26.1.2/src/main/resources/assets/astral_dice/lang   forge-1.20.1/src/main/resources/assets/astral_dice/lang   fabric-1.20.1/src/main/resources/assets/astral_dice/lang
# 期望：无输出（exit 1）

# 2) 目标串已落位（期望 4 行，行号同 A.1.1）
grep -rn '深渊试剂' neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/zh_cn.json   neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/zh_cn.json   forge-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json   fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json

# 3) 键未被破坏（仍是原键名，只值变了）
grep -rn 'item.astral_dice.abyss_reagent_chip' */src/main/resources/assets/astral_dice/lang/*.json
# 期望：每线 zh_cn/en_us/ja_jp 各 1 行，值分别为 深渊试剂 / Reagent - Abyss / 試薬 - 深淵
#       若 en_us/ja_jp 也按 lead 裁决同步，则值改为 Abyss Reagent / 深淵試薬

# 4) id 面零改动（应与改动前一致）
git -C F:\MCProject\astral_dice_multiloader diff --stat -- '*/src/main/resources/data/**' '*/src/main/resources/assets/*/patchouli_books/**'
# 期望：无输出
```

**若选择「全仓源码零残留」（连 Java 注释一起改）**，则额外命中 A.1.3 的 30 行；验收改为：

```bash
grep -rn --binary-files=without-match '试剂-深渊'   neoforge-1.21.1/src neoforge-26.1.2/src forge-1.20.1/src fabric-1.20.1/src
# 期望：无输出
```

---

## B. 版本号 → 1.5.0

### B.5 `mod_version` 当前值与所在行（4 行，必须改）

| 线 | 文件（绝对路径） | 行号 | 当前值 | 目标值 |
|---|---|---|---|---|
| neoforge-1.21.1 | `F:\MCProject\astral_dice_multiloader\neoforge-1.21.1\gradle.properties` | **37** | `1.5.0-rc.7+neoforge_1.21.1` | `1.5.0+neoforge_1.21.1` |
| neoforge-26.1.2 | `F:\MCProject\astral_dice_multiloader\neoforge-26.1.2\gradle.properties` | **54** | `1.5.0-rc.7+neoforge_26.1.2` | `1.5.0+neoforge_26.1.2` |
| forge-1.20.1 | `F:\MCProject\astral_dice_multiloader\forge-1.20.1\gradle.properties` | **41** | `1.5.0-rc.7+forge_1.20.1` | `1.5.0+forge_1.20.1` |
| fabric-1.20.1 | `F:\MCProject\astral_dice_multiloader\fabric-1.20.1\gradle.properties` | **29** | `1.5.0-rc.7+fabric_1.20.1` | `1.5.0+fabric_1.20.1` |

> 注意：只删 `-rc.7`，**保留 `+<loader>_<mcver>` 构建元数据后缀**。该后缀被部署脚本用于「只清理本线 jar」判定（见 B.7）且是 CI 剥后缀的输入。

同文件内其它版本相关键（**本次不改**）：

| 键 | neo1211 | neo26 | forge | fabric |
|---|---|---|---|---|
| `mod_name` | `Astral Dice` | 同 | 同 | 同 |
| `mod_group_id` | `com.merlinkitsune.astral_dice` | 同 | 同 | 同 |
| `starengine_version` | `1.1.0`（:126-127 区） | `1.1.0`（:113-114 区） | `1.1.0`（:114-115 区） | `1.1.0` |
| `starengine_version_range` | **已移除**（2026-09-25） | **已移除** | **已移除** | `>=1.1.0`（`fabric-1.20.1/gradle.properties:42`） |
| `minecraft_version` / `_range` | `1.21.1` / `[1.21.1]` | `26.1.2` / `[26.1.2]` | `1.20.1` / `[1.20.1]` | `1.20.1` / `~1.20.1` |
| 加载器版本 | `neo_version=21.1.235` | `neo_version=26.1.2.109`、`neo_version_range=[26.1.0.0,26.2)` | `forge_version=1.20.1-47.4.10` | `loader_version=0.19.5`、`fabric_version=0.92.12+1.20.1` |
| `loader_version_range` | `[1,)` | — | — | — |
| KubeJS | `2101.7.2-build.372` | `26.1.2-8.0.6` | `2001.6.5-build.26` | — |
| Curios | — | `curios_version_range=[15,)` | — | — |
| Patchouli | — | — | — | `patchouli_version=1.20.1-85-FABRIC` |

**根目录 `F:\MCProject\astral_dice_multiloader\gradle.properties`（7 行）不含任何版本号**，只有 `org.gradle.jvmargs=-Xmx3G` / daemon / parallel / caching / `configuration-cache=true` ⇒ 不需改。

**产物命名规则**：四线均 `version = mod_version` + `base { archivesName = 'astral_dice' }`（行号见 B.7）⇒ `astral_dice-<mod_version>.jar`。

### B.6 版本号复述位置（排除 docs / CHANGELOG / build / run / .superpowers）

`grep -rn '1\.5\.0-rc\.7\|1\.5\.0-rc'` 在源码 / 资源 / gradle / CI / tools 范围的命中：

| # | 文件:行号 | 内容 | 处置 |
|---|---|---|---|
| 1 | `neoforge-1.21.1/gradle.properties:37` | `mod_version=1.5.0-rc.7+neoforge_1.21.1` | **必改** |
| 2 | `neoforge-26.1.2/gradle.properties:54` | `mod_version=1.5.0-rc.7+neoforge_26.1.2` | **必改** |
| 3 | `forge-1.20.1/gradle.properties:41` | `mod_version=1.5.0-rc.7+forge_1.20.1` | **必改** |
| 4 | `fabric-1.20.1/gradle.properties:29` | `mod_version=1.5.0-rc.7+fabric_1.20.1` | **必改** |
| 5 | `tools/port_26_12.py:4,279` | 注释「1.5.0-rc.7 基线」 | 注释，可选同步 |
| 6 | `.github/workflows/build.yml:18,19,186` | 仅注释 / 示例 | 不需改（见 B.8） |
| 7 | `scripts/test/linux/logs/*.log`、`scripts/test/win/logs/*.log`、`scripts/test/reports/**` | 生成日志 | 不需改 |
| 8 | `ref/fabric-loader/**`（`minecraft_versions.json`、`VersionParsingTests.java`） | 第三方参考源码 | **不能改** |

补充（`1.5.0` 但不带 rc 的复述，全部为注释 / 文档串，**均不需改**）：

| 文件:行号 | 内容 |
|---|---|
| `README.md:73-76,114`、`README_ZH.md:73-76,114` | 正文写的是**过时**的 `1.5.0-rc.2`（本身已不一致，非本次范围） |
| `tools/curseforge_upload.py:29,33,1147,1149` | 全在 docstring 示例里，且已写 `astral_dice-1.5.0+neoforge_1.21.1.jar`（无 rc）⇒ 改版后示例反而变准 |
| `tools/gen_chip_effect_icons.py:40` | 注释「1.5.0 正式版前冻结」 |
| `tools/verify_hud_attack_parity.py:58` | 注释「1.5.0 正式版前不移植」 |
| `tools/verify_defense_unit_parity.py:313` | 指向 `docs/compat-1.5.0-porting.md` |
| `tools/verify_fabric_assets.py:156,176` | 注释举例 |
| `scripts/test/TESTING-SPEC.md` | 工程文档历史条目 |
| `scripts/test/fabric/lib/Ft.Common.psm1:19` | 过时注释 `mod_version=1.3.2+fabric_1.20.1` |
| `.curseforge/versions.json` | **CurseForge 游戏版本缓存**（21.1.x / 26.1.2.x 等），不含本项目 mod 版本 ⇒ 与本次无关 |

`.superpowers/`、`docs/`、`CHANGELOG*`、`build/`、`run/`、`temp/`、`release/` 按任务口径已排除（其中 `build/libs` 里的旧 jar 由 B.7 的清理逻辑处理）。

### B.7 产物命名与打包

**是否由 `mod_version` 直接决定：是。**

| 线 | `version = mod_version` 行 | `archivesName` 行 | 产物名 |
|---|---|---|---|
| neoforge-1.21.1 | `build.gradle:10` | `:58-59`（`base { archivesName = 'astral_dice' }`） | `astral_dice-1.5.0+neoforge_1.21.1.jar` |
| neoforge-26.1.2 | `build.gradle:11` | `:69-70` | `astral_dice-1.5.0+neoforge_26.1.2.jar` |
| forge-1.20.1 | `build.gradle:9` | `:58-59` | `astral_dice-1.5.0+forge_1.20.1.jar` |
| fabric-1.20.1 | `build.gradle:18` | `:21-22` | `astral_dice-1.5.0+fabric_1.20.1.jar` |

**sources jar / shadow jar：不存在。** 四线 `build.gradle` 内 `sourcesjar|withSources|shadowJar` **零命中**；`build/libs` 实际只含骨架 jar（`neoforge-1.21.1/build/libs` 现存 `astral_dice-1.5.0-rc.6+neoforge_1.21.1.jar` 与 `...-rc.7+...jar`，其余三线各一个 rc.7）⇒ **只需改一个 jar 名**。

**部署/清理任务按前缀删旧 jar，不按版本号**（旧 rc.7 jar 会被自动清掉，无需手工删）：

| 任务 | 行号 | 清理判据 |
|---|---|---|
| `pushToDevRun`（推 `run/1.21.1/mods`） | `neoforge-1.21.1/build.gradle:339-341` | `f.name.startsWith('astral_dice-') && f.name.endsWith('.jar')` → 全删 |
| `pushToGame`（推整合包 `versions/狐の航空学 Voxy Edition/mods`） | `:423-425` | 同上 |
| `pushToRootBuild`（推仓库根 `build/libs`） | `:471-473` | 同上 **且** `f.name.contains('+neoforge_1.21.1')` |

> ⚠️ **风险提示（本次不改、但需知晓）**：`neoforge-1.21.1/build.gradle` 里 `def packPushBranches = ['multi-main']`（`:316`），当前分支正是 `multi-main`；三个推送任务都挂在 `build` 的 `finalizedBy` 上（`:484-487`）。⇒ 一旦有人跑 `gradlew build`，新 jar 会**自动**推进整合包并删掉旧 rc.7 jar。若本次只改版本号、不想动整合包，请在出包前用其它分支 / 或明确不跑 `build`。

**manifest 均为占位符，不需手改**：
- `neoforge-1.21.1/src/main/templates/META-INF/neoforge.mods.toml:17` = `version="${mod_version}"`（forge / neo26 同构）
- `fabric-1.20.1/src/main/resources/fabric.mod.json:4` = `"version": "${version}"`（`:39` minecraft、`:43` starengine `"${lib_version_range}"`）
- 四线均有 `src/main/templates/astral_dice_version.properties`，内容仅一行 `mod_version=${mod_version}`（`processResources` + `expand` 生成，运行期 `network/VersionGate` 读它截 `major.minor` 作网络互通号）⇒ 版本串自动跟随。

### B.8 CI 配置

**存在，唯一文件**：`F:\MCProject\astral_dice_multiloader\.github\workflows\build.yml`

| 关注点 | 事实 | 行号 |
|---|---|---|
| 触发 | push 分支 `multi-main` / `multi-dev-next` / `multi-26.1.2-neoforge`；tags 过滤 `[0-9]*`；另有 `pull_request` 与 `workflow_dispatch` | 触发段 |
| 版本来源 | **动态读**：`VERSION=$(grep '^mod_version=' neoforge-1.21.1/gradle.properties \| cut -d'=' -f2)`，`BASE=${VERSION%%+*}` 剥加载器后缀 | Read base version 步骤 |
| 预发布判定 | 版本串含 `-` ⇒ `is_prerelease=1`，否则 `0` | 同步骤 |
| 守门正则 | `^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z][0-9A-Za-z.-]*)?$`；合规才 `skip_tag=0` | 同步骤 |
| tag 创建 | 仅 `push` 到 `refs/heads/multi-main` 且 `skip_tag==0` 时建 `tag = base` | Create Git tag |
| Release 正文 | 取 `release/<tag>/PLAYER_CHANGELOG_ZH.md` + `PLAYER_CHANGELOG.md`，都不存在 → 退回附件清单 + `::warning::` | Release 步骤 |
| 附件范围 | 2026-10-25/30 裁决：只发 P0 两线（neoforge-1.21.1 + forge-1.20.1）；26.1.2 / fabric 不进 Release | 打包步骤 |
| tag 命名纪律 | 只按 mod 版本，**禁平台前缀与 `v` 前缀**；已去 `--clobber` | 同前 |
| 写死版本号 | **无**（`:18,19,186` 的 `1.5.0-rc.7` 只出现在注释 / 示例里） | — |

**改 1.5.0 后的 CI 行为变化（重要）**：
1. `BASE` 由 `1.5.0-rc.7` 变 `1.5.0`，仍匹配守门正则 ⇒ `skip_tag=0`，可建 tag。
2. `is_prerelease` 由 `1` 变 `0` ⇒ **GitHub Release 会从 prerelease 变为正式 release**。这是本次改动的预期效果，但请确认这是「正式发布」的时点。
3. `release/` 下**没有 `1.5.0/` 目录**（现仅有 `1.2.0`～`1.3.7` + `1.5.0-rc.1`～`rc.7` + `fabric-1.3.*` + `starengine-lib`）⇒ Release 正文会退化成附件清单 + warning。**若本次要发版，需先补 `release/1.5.0/PLAYER_CHANGELOG_ZH.md` 与 `PLAYER_CHANGELOG.md`**（属另一项工作，不在本报告范围）。

---

## 风险与待裁决清单

1. **en_us / ja_jp 是否同步改名**：现值 `Reagent - Abyss` / `試薬 - 深淵`，字面不含「试剂-深渊」故不在替换命中集内，但语序同源。若「深渊试剂」是术语纠偏 ⇒ 应同步为 `Abyss Reagent` / `深淵試薬`。（证据：四线 `lang/en_us.json` / `ja_jp.json` 第 378/379/384/385/888/921 行）
2. **Java 注释 30 行是否一起改**：不影响玩家，但会让「全仓零残留」验收口径不一致。建议：要么全改（纯文本、零功能风险），要么在验收里显式限定为 `*/src/main/resources/assets/*/lang/*.json`。
3. **`scripts/test/cases/PMFIX-TIP-*.json` 的文案**：机器断言安全，仅 title/note/vision 提问过时。建议同步（4 处 × 2 文件）。
4. **`run/1.21.1/kubejs/client_scripts/astral_pmfix_client.js`**：与 `scripts/test/resources` 版本字节一致（8169 B），属投放副本 ⇒ **改源不改 run**，下次投放自动覆盖。
5. **`gradlew build` 的自动部署副作用**：`multi-main` 分支 + `finalizedBy(pushToGame/pushToRootBuild/pushToDevRun)` ⇒ 出包会改整合包目录。不在本报告范围，但执行改动后若跑构建需知情。
6. **`release/1.5.0/` 缺失** ⇒ tag 后 Release 正文退化（见 B.8-3）。
7. **README.md / README_ZH.md 仍写 `1.5.0-rc.2`**（`:73-76,114`）：属既有不一致，与本次「去掉 rc.7」不冲突，但改版后会更显眼，可顺手一并更新（需 lead 裁决）。

---

## 附录：本次取证命令（原文，可复跑）

```bash
cd F:/MCProject/astral_dice_multiloader
# A 项
grep -rn --binary-files=without-match '试剂-深渊' .            # 123 文件命中（含生成物）
grep -rn --binary-files=without-match '试剂 - 深渊' .           # 0
grep -rn --binary-files=without-match '试剂—深渊' .             # 0
grep -rn --binary-files=without-match '深渊试剂' .              # 仅 docs / porting 文档
grep -rn 'abyss_reagent' <线>/src/main                         # id 面，不改
grep -n 'abyss_reagent' <线>/src/main/resources/assets/astral_dice/patchouli_books/astral_guide/en_us/entries/chips_healing/abyss_reagent_chip.json
diff -q run/1.21.1/kubejs/client_scripts/astral_pmfix_client.js scripts/test/resources/kubejs/1.21.1/client_scripts/astral_pmfix_client.js  # IDENTICAL

# B 项
grep -n '^mod_version=' */gradle.properties
grep -rn '1\.5\.0-rc' --exclude-dir=docs --exclude-dir=build --exclude-dir=run --exclude-dir=.superpowers --exclude-dir=.git .
grep -n 'version = mod_version\|archivesName' */build.gradle
grep -rn 'sourcesjar\|withSources\|shadowJar' */build.gradle      # 0
grep -n 'mod_version\|BASE\|is_prerelease\|skip_tag\|PLAYER_CHANGELOG' .github/workflows/build.yml
grep -n 'pushToDevRun\|pushToGame\|pushToRootBuild\|packPushBranches' neoforge-1.21.1/build.gradle
ls release/                                                        # 无 1.5.0/
```
