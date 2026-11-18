# 1.5.0 提交计划（只读审计，不含任何 git 写操作）

| 项 | 值 |
|---|---|
| 仓库 | `F:\MCProject\astral_dice_multiloader` |
| 分支 / HEAD | `multi-main` / `fd990c15`（领先 `origin/multi-main` 24 个提交） |
| 审计对象 | `git status --porcelain=v1` 全量 **724** 条 |
| 暂存区 | **空**（724 条 X 列全为空格，无任何已暂存改动） |
| mtime 判据 | 阈值 `2026-11-01T22:00:00+08:00` → recent 147 / old 532（另有 45 条 D 文件已不存在，无 mtime） |
| 生成时间 | 2026-11-02 03:2x（本地，Asia/Hong_Kong） |
| 本文件性质 | 只读分析产物；未执行 add / commit / push / tag / checkout / stash / restore |

> 原始取证：`work/_status_raw.txt`（724 行 status）、`work/_numstat.txt` / `work/_numstat_noeol.txt`（含 / 忽略 CR 的 numstat 对照）。

---

## 一、分组统计表（724 条全覆盖，A / B 双维度）

| 组 | 组名 | 文件数 | M | D | ?? | 归属 |
|---|---|---|---|---|---|---|
| A1 | 版本号（四线 gradle.properties，1.5.0-rc.7 → 1.5.0） | 4 | 4 | 0 | 0 | A |
| A2 | 发布说明（CHANGELOG / CHANGELOG_ZH / README / README_ZH） | 4 | 4 | 0 | 0 | A |
| A3 | CI 发布口径（.github/workflows/build.yml 四 JAR + release/<tag> 笔记） | 1 | 1 | 0 | 0 | A |
| A4 | i18n：ModTooltipHandler ×4 + lang zh_cn/en_us/ja_jp ×12 | 16 | 16 | 0 | 0 | A |
| A5 | 「深渊试剂」命名统一（forge / 1.21.1 / 26.1.2 × 5 文件，纯注释与配方注释） | 15 | 15 | 0 | 0 | A |
| A6 | neoforge-26.1.2 创造栏补 ATTACK_CARD_BITE / ATTACK_CARD_DRAGON_ROAR | 1 | 1 | 0 | 0 | A |
| A7 | fabric 立牌配方补齐（datagen 源码 + misaki_sign 修正 + 6 个 sign advancement） | 8 | 2 | 0 | 6 | A |
| A8 | 1.5.0 实机回归用例（RELEASE150-*，四线） | 4 | 0 | 0 | 4 | A |
| A9 | 发布产物与 1.5.0 版本映射文档 | 3 | 0 | 0 | 3 | A |
| B1 | fabric 第四线 Java 补齐 | 210 | 97 | 0 | 113 | B |
| B2 | fabric 第四线资源补齐 | 178 | 96 | 1 | 81 | B |
| B3 | fabric generated 存量（44 条卡牌配方/advancement 删除 + 35 新增 + 1 修改） | 79 | 0 | 44 | 35 | B |
| B4 | 立牌半透明 / YSM 渲染兼容线 | 14 | 6 | 0 | 8 | B |
| B5 | 26.1.2 其他 recent 改动 | 8 | 5 | 0 | 3 | B |
| B6 | 1.20.1 / 1.21.1 线零散改动 | 1 | 1 | 0 | 0 | B |
| B7 | 测试台与构建基建 | 35 | 9 | 0 | 26 | B |
| B8 | 移植报告（porting/**） | 64 | 0 | 0 | 64 | B |
| B9 | 文档（AGENTS.md / docs/**） | 14 | 6 | 0 | 8 | B |
| B10 | 守门脚本（tools/*.py） | 3 | 3 | 0 | 0 | B |
| B11 | work/ 工作区报告与探针 | 27 | 0 | 0 | 27 | B |
| B12 | stat-dirty 假阳性（无内容变更） | 3 | 3 | 0 | 0 | B |
| J1 | 临时 / 垃圾文件（禁止入库） | 32 | 0 | 0 | 32 | B |
| — | **合计** | **724** | **269** | **45** | **410** | A 56 / B 668 |

归属口径：**A = 本次 1.5.0 发布会话产出（应提交）**，**B = 存量（需用户裁决）**；J1 归入 B 但性质是「必须排除」。

---

## 二、A 清单（本次会话产出，共 56 条；其中 2 条是未跟踪目录条目：`release/1.5.0/` 含 3 个文件、`docs/porting/` 含 1 个文件）

### A1 版本号（四线 gradle.properties，1.5.0-rc.7 → 1.5.0）（4 条）

- `fabric-1.20.1/gradle.properties`
- `forge-1.20.1/gradle.properties`
- `neoforge-1.21.1/gradle.properties`
- `neoforge-26.1.2/gradle.properties`

### A2 发布说明（CHANGELOG / CHANGELOG_ZH / README / README_ZH）（4 条）

- `CHANGELOG.md`
- `CHANGELOG_ZH.md`
- `README.md`
- `README_ZH.md`

### A3 CI 发布口径（.github/workflows/build.yml 四 JAR + release/<tag> 笔记）（1 条）

- `.github/workflows/build.yml`

### A4 i18n：ModTooltipHandler ×4 + lang zh_cn/en_us/ja_jp ×12（16 条）

- `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java`
- `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json`
- `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json`
- `fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json`
- `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java`
- `forge-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json`
- `forge-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json`
- `forge-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json`
- `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java`
- `neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/en_us.json`
- `neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/ja_jp.json`
- `neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/zh_cn.json`
- `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java`
- `neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/en_us.json`
- `neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/ja_jp.json`
- `neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/zh_cn.json`

### A5 「深渊试剂」命名统一（forge / 1.21.1 / 26.1.2 × 5 文件，纯注释与配方注释）（15 条）

- `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/component/ModAttachments.java`
- `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java`
- `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/HealingManager.java`
- `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java`
- `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/AbyssReagentChipItem.java`
- `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/component/ModAttachments.java`
- `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java`
- `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/HealingManager.java`
- `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java`
- `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/AbyssReagentChipItem.java`
- `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/component/ModAttachments.java`
- `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java`
- `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/item/HealingManager.java`
- `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java`
- `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/item/chip/AbyssReagentChipItem.java`

### A6 neoforge-26.1.2 创造栏补 ATTACK_CARD_BITE / ATTACK_CARD_DRAGON_ROAR（1 条）

- `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/init/ModCreativeTabs.java`

### A7 fabric 立牌配方补齐（datagen 源码 + misaki_sign 修正 + 6 个 sign advancement）（8 条）

- `fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/misaki_sign.json`
- `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java`
- `fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/alana_sign.json`
- `fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/ame_sign.json`
- `fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/dorothy_sign.json`
- `fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/jill_sign.json`
- `fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/kangel_sign.json`
- `fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/lingling_sign.json`

### A8 1.5.0 实机回归用例（RELEASE150-*，四线）（4 条）

- `scripts/test/cases/RELEASE150-1.20.1.json`
- `scripts/test/cases/RELEASE150-1.21.1.json`
- `scripts/test/cases/RELEASE150-26.1.2.json`
- `scripts/test/cases/RELEASE150-fabric-1.20.1.json`

### A9 发布产物与 1.5.0 版本映射文档（3 条）

- `docs/porting/`
- `porting/fabric-1.20.1/MAPPING-1.5.0.md`
- `release/1.5.0/`

---

## 三、B 清单（存量，共 668 条；其中 J1 32 条为必排除项，B12 3 条为假阳性）

| 组 | 组名 | 文件数 | 判断 | 代表路径（3 个） |
|---|---|---|---|---|
| B1 | fabric 第四线 Java 补齐 | 210 | 他人并发改动 + 既有技术债：fabric 对 forge-1.20.1 的对齐补齐（ModNetwork 的 chipshop/beam FX 消息 id 12–15、BaseEffectCardItem 接 EventAnimationLock、TargetSelection 的 C-2 修复、B4-SIGNS 创造栏、EventCardService / CardTraderTrades / AlanaSignItem 等）。mtime 主体在 2026-11-01 白天，对应 porting/ 下 B0–B36 批次，与 1.5.0 版本号无耦合。 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceClient.java`<br>`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java`<br>`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/audio/ModSounds.java` |
| B2 | fabric 第四线资源补齐 | 178 | 他人并发改动：patchouli 手册 / 贴图 / 模型整批搬运，属第四线内容补齐，非发布必需。 | `fabric-1.20.1/src/main/resources/assets/astral_dice/patchouli_books/astral_guide/en_us/categories/cards.json`<br>`fabric-1.20.1/src/main/resources/assets/astral_dice/patchouli_books/astral_guide/en_us/categories/chips.json`<br>`fabric-1.20.1/src/main/resources/assets/astral_dice/patchouli_books/astral_guide/en_us/categories/signs.json` |
| B3 | fabric generated 存量（44 条卡牌配方/advancement 删除 + 35 新增 + 1 修改） | 79 | 发布必须一起进（有条件）：44 条 D 是 datagen 输出，与 A7 的 ModRecipeProvider 改写互为上下游——该源码已不再生成这批卡牌配方，只提源码不删旧 JSON 会造成「代码不生成、产物还在」的漂移。但目录 mtime = 2026-11-01 20:23（早于会话窗），且 `textures/mob_effect/railgun_ready.png` 是 Java 侧资产删除，须单独裁决。 | `fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/attack_card_charge.json`<br>`fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/attack_card_epic_from_large.json`<br>`fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/attack_card_large_from_medium.json` |
| B4 | 立牌半透明 / YSM 渲染兼容线 | 14 | 他人并发改动（同日另一条功能线）：HEAD fd990c15 已提交 26.1.2 两段式实现，这里是续作（1.21.1 / forge 补 ClientAlpha + 三条 mixin 并在 mixins.json 注册；26.1.2 新增 AlphaContext / YSM 兼容与 ysm.mixins.json 挂载）。不属 1.5.0 发布清单。 | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/client/SignTranslucencyMixin.java`<br>`forge-1.20.1/src/main/resources/astral_dice.mixins.json`<br>`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/mixin/client/SignTranslucencyMixin.java` |
| B5 | 26.1.2 其他 recent 改动 | 8 | 他人并发改动：SignHookDispatcher 口径对齐、CardChoiceConfirmPayload 注册补漏、星币队伍 / 32 格半径、光束粒子减量等，26.1.2 线自身收尾。 | `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/client/BeamFxRenderer.java`<br>`neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/combat/DamageCardFxScheduler.java`<br>`neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java` |
| B6 | 1.20.1 / 1.21.1 线零散改动 | 1 | 既有技术债：仅 javadoc 口径收窄（复仇之戟在 1.20.1 不存在蓄风 / 盘丝 / 渗浆 / 寄生），无功能变更。 | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/RevengeHalberdChipItem.java` |
| B7 | 测试台与构建基建 | 35 | 发布必须一起进（有条件）：fabric-loom 1.14.10→1.17.21 要求 Gradle ≥ 9.5（wrapper 已同步 9.5.1）；CI 若要构建 fabric jar 则强相关。其余用例 / 测试台脚本属工程基建，可独立提交。 | `fabric-1.20.1/build.gradle`<br>`gradle/wrapper/gradle-wrapper.properties`<br>`scripts/test/TESTING-SPEC.md` |
| B8 | 移植报告（porting/**） | 64 | 他人并发改动：B0–B36 / ALIGNMENT-AUDIT / B-BUILD-LOOM 报告与 P1 缺陷上下文，全部未跟踪；建议不入库（加 .gitignore）或单开一个 docs 提交。 | `porting/fabric-1.20.1/ALIGNMENT-AUDIT-2026-11-01.md`<br>`porting/fabric-1.20.1/B-BUILD-LOOM-REPORT.md`<br>`porting/fabric-1.20.1/B0-CONTENT-REPORT.md` |
| B9 | 文档（AGENTS.md / docs/**） | 14 | 既有技术债：规则正本、移交文档、ysm-compat 说明的同步改动，与发布无耦合。 | `AGENTS.md`<br>`docs/agent/AGENTS-dsh-linux.md`<br>`docs/agent/AGENTS-full.md` |
| B10 | 守门脚本（tools/*.py） | 3 | 既有技术债：verify_fabric_assets / verify_sign_display_names / verify_defense_unit_parity 的 fabric 四线适配。 | `tools/verify_defense_unit_parity.py`<br>`tools/verify_fabric_assets.py`<br>`tools/verify_sign_display_names.py` |
| B11 | work/ 工作区报告与探针 | 27 | 工作区报告、默认不提交：发布过程报告（plan-1.5.0-release.md、recon/、i18n-final-cleanup-evidence/）与探针脚本。`work/` 未被 gitignore，会被 `git add -A` 收进提交，必须显式排除；建议把 `work/_*`、`work/*.log` 写进 .gitignore。 | `work/_aj/`<br>`work/_probe_autojs.py`<br>`work/_probe_case.py` |
| B12 | stat-dirty 假阳性（无内容变更） | 3 | 不应进 commit：3 个 scripts/verify/*.ps1 的 `git diff` 为空、`i/lf w/crlf` 与同目录兄弟一致，属 stat 缓存脏；`git add` 后即无差异。 | `scripts/verify/verify_chip_recipes.ps1`<br>`scripts/verify/verify_chip_shop_ui.ps1`<br>`scripts/verify/verify_sign_registration.ps1` |
| J1 | 临时 / 垃圾文件（禁止入库） | 32 | 必须排除：15×`javac.20261101_*.args`、`_sched.bat` / `tmptekih3ja.sh` / `zz_screen_probe.py` / `_tmp_b11_*.py` / `_b20a_jarinfo.py`、`_fabric_remap.log`、`acl-backup-*.txt`、`nul`、`%SystemDrive%/`、`scripts/__proclist.ps1` / `__sides.ps1` / `__tok.gradle` / `__tr/`、`models/*.bbmodel`、2×`*.bak-before-release150-run`。 | `%SystemDrive%/`<br>`_b20a_jarinfo.py`<br>`_fabric_remap.log` |

> 真正需要用户逐条裁决的是 **B1–B11 共 633 条**；J1（32）无条件排除，B12（3）无内容变更不应提交。


---

## 四、四项特别核查

### 4.1 `fabric-1.20.1/.../combat/DiceCombatEvents.java` 的 CRLF 差异是纯 EOL 还是真内容？

**结论：不是纯 EOL，是真实内容变更；但可以安全转 LF（且无需人工转换）。**

- 事实：`git ls-files --eol` = `i/lf w/crlf`（**不是 mixed**）；文件 2218 行；工作树 145426 B，HEAD blob 122632 B；`core.autocrlf=true`，`.gitattributes` 只对 `src/generated/**/.cache/*`、`src/generated/**/*.json`、`gradlew`、`*.sh`、`*.py`、`*.conf` 强制 LF，`.java` 不在其中。
- 判据：`git diff --numstat`、`git diff --ignore-cr-at-eol --numstat`、`git diff -w --numstat` **三份输出逐字节相同**，均为 **+338 / −25**。EOL 不是差异来源，145426 − 122632 = 22794 B 是 CR 化 + 338 行新增共同造成。
- 全仓对照：311 条 tracked diff 中「纯 EOL」文件数 = **0**；A 集合 43 个 tracked 文件里 `w/mixed` = **0**，numstat 与 `--ignore-cr-at-eol` 版亦完全相同。
- 可安全转 LF 的依据：index 已是 LF，`autocrlf=true` 在 `git add` 时会把工作树 CRLF 归一为 LF，不会产生 EOL 噪声提交；文件非 mixed，故不存在「一半 CRLF 一半 LF」的歧义折叠。
- 但**该文件归 B1（fabric 第四线补齐），不属于 1.5.0 发布范围**，本次不应提交。三线工作树 EOL 状态不一致（forge-1.20.1 / neoforge-1.21.1 / neoforge-26.1.2 的 `DiceCombatEvents.java` 为 `w/lf`，fabric 为 `w/crlf`），提交后 index 统一为 LF，无仓库级噪声。
- 附带发现（同一性质的假阳性）：`scripts/verify/verify_chip_recipes.ps1`、`verify_chip_shop_ui.ps1`、`verify_sign_registration.ps1` 状态为 `M` 但 `git diff` 为空、`i/lf w/crlf` 与同目录兄弟一致 —— stat 缓存脏，**不应进 commit**。

### 4.2 45 个 `D`（删除）是否全为 fabric 陈旧卡牌配方 datagen 清理？

**结论：44/45 是，1/45 不是。**

- 22 条 `fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/*.json`：attack_card_charge / attack_card_epic_from_large / attack_card_large_from_medium / attack_card_medium / attack_card_meito / attack_card_shadow_strike / defense_card_epic_from_large / defense_card_large_from_medium / defense_card_medium / effect_card_berserk / effect_card_chocolate_cake / effect_card_directional_blast / effect_card_express_delivery / effect_card_fight_poison_with_poison / effect_card_hamburger / effect_card_king_power / effect_card_luxury_feast / effect_card_monster_brick / effect_card_monster_laser / effect_card_orbital_strike / effect_card_unwavering / effect_card_you_have_i_have
- 22 条同名 `.../data/astral_dice/advancements/recipes/misc/*.json`（配方解锁 advancement）
- **1 条非配方**：`fabric-1.20.1/src/main/resources/assets/astral_dice/textures/mob_effect/railgun_ready.png`（Java 侧状态效果贴图）→ 必须单独裁决，不能混在 datagen 清理里
- 旁证：`.../data/astral_dice/recipes` 目录 mtime = 2026-11-01 20:23:07、`.../textures/mob_effect` = 19:14:21，均早于 22:00 会话语义窗；`.../advancements/recipes/misc` = 2026-11-02 02:04:32 只因本会话写入 6 个 sign advancement 而刷新。对应过程报告 `porting/fabric-1.20.1/B34-CARD-RECIPE-REMOVAL-REPORT.md`。
- 对齐证据：`neoforge-1.21.1/src/generated/.../recipes/*card*.json` = **0** 条，`forge-1.20.1` 仅 3 条 `bank_card*`，而 fabric HEAD 仍有 **25** 条 —— 删除是「向主线看齐」，而非本会话新增决策；但它与 A7 的 `ModRecipeProvider`（+169/−170，已移除这批卡牌配方生成块）构成上下游，见 C6。

### 4.3 是否有 `.bak` / `.bak-*` / `*.orig` 之类临时文件混在待提交范围？

**结论：有，且全部未被 gitignore。**

- `.bak` 类：`scripts/test/cases/RELEASE150-1.20.1.json.bak-before-release150-run`、`RELEASE150-1.21.1.json.bak-before-release150-run`（2 个）
- `.orig` 类：**未发现**
- 其他一次性产物（同样未忽略）：15×`javac.20261101_18*.args`、`_fabric_remap.log`、`_sched.bat`、`_tmp_b11_bytes.py`、`_tmp_b11_fix.py`、`_b20a_jarinfo.py`、`acl-backup-20261101-153946.txt`、`tmptekih3ja.sh`、`zz_screen_probe.py`、`nul`、`%SystemDrive%/`（Windows 兼容层目录误落）、`scripts/__proclist.ps1`、`scripts/__sides.ps1`、`scripts/__tok.gradle`、`scripts/__tr/`、`models/astral_anvil.bbmodel`、`models/cardshop_villager_1.bbmodel`、`work/p1-build.log`
- 合计 **32 条**，全部 `git check-ignore` 返回非 0（未被忽略）⇒ 一次 `git add -A` 就会把它们收进提交。

### 4.4 `work/` 是否被 gitignore？

**结论：未被忽略。** `git status` 直接列出 `?? work/...`，`git check-ignore work/git-commit-plan.md` 无任何输出。同理 `release/1.5.0/`（3 个文件）与 `docs/porting/`（1 个文件）也**未被忽略**，只是从未跟踪；此前若按「被 .gitignore 挡住」判断，应以此实测为准更正。

处理建议（提交计划内明确）：

1. `work/` 定性为 **工作区报告 / 探针脚本，默认不提交**；本次审计自身产物（`work/_probe*.ps1`、`work/_d*.out`、`work/_status_raw.txt`、`work/_numstat*.txt`、`work/_alist.txt`、`work/_plan_part1.md`）与既有 `work/_*`、`work/recon/`、`work/i18n-final-cleanup-evidence/`、`work/_aj/` 一并排除。
2. 若确实要留档，只单独提交 **面向人的 .md 报告**（`work/plan-1.5.0-release.md`、`work/release150-test-runbook.md`、`work/p1-version-and-rename.md`、`work/ci-gates-and-fourline-release.md`、`work/fix-i18n-tooltip.md`、`work/i18n-final-cleanup.md` 等），且必须是独立一条 `docs(work)` 提交，不得与 C1–C6 混提。
3. 建议向 `.gitignore` 追加：`work/_*`、`work/*.log`、`*.bak-before*`、`scripts/__*`、`javac.*.args`、`nul`、`%SystemDrive%/`。

---

## 五、建议提交拆分（6 条，仅计划，不执行）

| # | 提交标题（中文） | 包含文件组 | 条数 | 一句话理由 | 是否需先修 EOL |
|---|---|---|---|---|---|
| C1 | build(release): 四线 mod_version 统一到 1.5.0，CI 产出四 JAR 并挂 Release 说明 | A1 + A3 + A8 | 9 | 版本号是发布地基，build.yml 已按 2026-11-02 裁决把 neoforge-26.1.2 与 fabric 纳入产物，配套 RELEASE150 用例作为同批验收口径 | 否（autocrlf 归一，A 集无 w/mixed） |
| C2 | docs(release): 1.5.0 CHANGELOG / README 与玩家发布说明、1.5.0 版本映射 | A2 + A9 | 7 | 发布说明、玩家侧 changelog 与映射文档是同一交付物；`release/1.5.0/`（3 文件）与 `docs/porting/MAPPING-1.5.0.md` 是未跟踪目录，须按**文件路径**精确 add | 否 |
| C3 | feat(i18n): tooltip 中文硬编码改 lang 键，四线 zh_cn / en_us / ja_jp 同步 | A4 | 16 | `ModTooltipHandler` 与 12 个 lang 是同一改动的两面，必须同提交；含 10 个 `tooltip.astral_dice.dice_face.*` 新键，`full_power` 行为 `%%` 转义写法 | 否 |
| C4 | refactor(i18n): 「深渊试剂」命名统一（forge / 1.21.1 / 26.1.2 注释与配方注释） | A5 | 15 | 15 个文件全是「试剂-深渊」→「深渊试剂」的 1/1 或 7/7 纯文案替换（新增行 0 条不含该词），行为零变更，独立成条便于回滚 | 否 |
| C5 | feat(26.1.2): 创造栏补齐 bite / dragon_roar 两张战斗牌 | A6 | 1 | 单文件单点（`ModCreativeTabs` 两行 `output.accept`），独立成条便于 cherry-pick 到其他分支 | 否 |
| C6 | fix(fabric): 立牌配方 datagen 补齐 + 生成物同步（含 44 条陈旧卡牌配方删除） | A7 + B3 | 87 | datagen 源码（+169/−170，移除卡牌配方生成块、新增 6 个 sign 配方）与生成物必须同批，否则出现「代码不再生成、JSON 仍在」的漂移 | **先裁决**：`railgun_ready.png` 的删除 + 44 条 D 是否随 1.5.0 一起进（否则 C6 只做 A7 的 8 条，生成物挂起） |

### 后续独立批次（本次不进，供用户排期）

- **M1** `fix(fabric): 第四线补齐`（B1 + B2，约 388 条）—— 他人并发，按 AGENTS.md 红线不得代为提交，需改动方确认。
- **M2** `feat(render): 立牌半透明 1.21.1 / forge 落地 + 26.1.2 YSM 兼容`（B4 + B5，22 条）—— HEAD 已提交 26.1.2 两段式，这份是同线续作。
- **M3** `build(fabric): loom 1.17.21 + Gradle 9.5.1 + JDK21 run 任务`（B7 中的 build 相关 2 条 + 测试台）。
- **M4** `docs: 规则与移交文档同步`（B9 + B8 + B10，81 条）。

---

## 六、执行前置检查清单（交给执行者）

1. **禁止 `git add -A` / `git add .`**：J1（32）+ B11（27）+ B12（3）会被误收。C1–C6 一律按文件路径显式列出。
2. **C6 前置裁决**：① `railgun_ready.png` 删除是否属于反制资产清理；② 44 条 D 是否随 1.5.0。未裁决则 C6 只含 A7 的 8 条。
3. **未跟踪目录按文件添加**：`release/1.5.0/PLAYER_CHANGELOG.md`、`PLAYER_CHANGELOG_ZH.md`、`PLAYER_CHANGELOG_MCMOD.html`、`docs/porting/MAPPING-1.5.0.md`、`porting/fabric-1.20.1/MAPPING-1.5.0.md`。
4. **本次审计自身产物需清理**：`work/_probe*.ps1`、`work/_d*.out`、`work/_status_raw.txt`、`work/_numstat.txt`、`work/_numstat_noeol.txt`、`work/_alist.txt`、`work/_plan_part1.md`（724 条基线是在它们产生之前捕获的，当前 status 条数会大于 724）。
5. **不碰他人改动**：B1–B5 未经改动方确认不得 add / commit（AGENTS.md 共同红线 4）。
6. **提交后复查**：`git status --porcelain=v1 | wc -l` 应回落到「B 组 + 审计产物」规模；`git diff --cached --numstat` 逐行核对不含 J1 任何路径。
7. **不跑 Gradle、不启动游戏**：实机测试占用 25565 / 25585 / 25575，本次审计全程未触碰、未构建。

---

## 七、审计方法与可复现命令（只读）

```powershell
git status --porcelain=v1                        # 724 条
git ls-files --eol -- <path>                     # i/lf w/crlf 判定
git diff --numstat vs git diff --ignore-cr-at-eol --numstat   # 排除纯 EOL
git check-ignore -v <path>                       # 无输出 = 未被忽略
git ls-files 'forge-1.20.1/src/generated/resources/data/astral_dice/recipes/*card*'  # 线间对齐证据
```

全程未执行 add / commit / push / tag / checkout / stash / restore；未启动 Minecraft，未运行 Gradle。
