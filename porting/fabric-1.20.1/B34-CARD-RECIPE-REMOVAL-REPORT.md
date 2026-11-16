# B34 — fabric-1.20.1「卡牌配方移除」补齐报告

> 状态：**完成**。provider 与主基准在「卡牌配方」上等价、44 个生成物已删并归档、五项验收全绿；无法判定项见 §7。
> 基准：主线提交 `a685d8d5`（2026-10-09，「卡牌配方移除 + JEI 获取方式」），主基准线 `neoforge-1.21.1`，次基准 `forge-1.20.1`。

## 0. 先声明：做了什么 / 没做什么

**实跑过的**（原始末段见 §5）：

1. `cmd.exe //c "gradlew.bat :fabric-1.20.1:classes --console=plain"` → `BUILD SUCCESSFUL`
2. `cmd.exe //c "gradlew.bat :fabric-1.20.1:remapJar --console=plain"` → `BUILD SUCCESSFUL`
3. jar 内容核对（`python` + `zipfile`，见 §5.3）
4. `python tools/verify_fabric_assets.py` → `=== PASS: 8 项闭环全部通过 ===`
5. `python tools/check_lang_sync.py` → 四线全 `[OK]`
6. `pwsh -File scripts/verify/verify_chip_recipes.ps1` → `RESULT: ALL OK`（任务书写的 `tools/verify_chip_recipes.ps1` **不存在**，实际脚本在 `scripts/verify/`，见 §7.3）

**没跑的**：`runDatagen` / `runData`（会重写 `src/generated` 下**他人**未提交的改动，见 §7.5）；任何实机 / 客户端 / 启动游戏（任务书禁止）。

**改动面只有 3 处**：`datagen/ModRecipeProvider.java` 1 个文件；`src/generated` 下 44 个文件删除；归档目录（新增 `fabric-1.20.1/` 44 文件 + `README.md` 追加 11 行）。lang / patchouli / 其他三线 / build 配置 / accesswidener **一行未碰**。

## 1. 与主基准的逐块比对结论

### 1.1 `datagen/ModRecipeProvider.java`

- 改前行数：fabric **1561**、neo **1450**、forge **1506**（fabric 工作树是 CRLF，neo/forge 是 LF；本批编辑全程保 CRLF：改后 CRLF=1391、lone LF=0）。
- 22 条卡牌配方在 fabric 里是 **5 个连续区块**（改前行号，1-based），每块 = 一条 `ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.X)` + N 个 `.unlockedBy(...)` + `.save(...)`，共 22 个产物、22 个 `unlockedBy`：

| 区块（改前 1-based） | 行数 | 产物常量 | 备注 |
|---|---|---|---|
| 47–105 | 59 | ATTACK_CARD_MEDIUM / DEFENSE_CARD_MEDIUM / ATTACK_CARD_SHADOW_STRIKE / ATTACK_CARD_MEITO / ATTACK_CARD_CHARGE / EFFECT_CARD_UNWAVERING / EFFECT_CARD_FIGHT_POISON_WITH_POISON / EFFECT_CARD_BERSERK | 8 段，id 走 `.save(output::accept)` 隐式推导 |
| 275–281 | 7 | EFFECT_CARD_KING_POWER | 1 段 |
| 334–363 | 30 | ATTACK_CARD_LARGE / ATTACK_CARD_EPIC / DEFENSE_CARD_LARGE / DEFENSE_CARD_EPIC | 4 段，`.save(output::accept, new ResourceLocation(...))` 显式 id：attack_card_large_from_medium / attack_card_epic_from_large / defense_card_large_from_medium / defense_card_epic_from_large |
| 498–530 | 33 | MONSTER_LASER_CARD / MONSTER_BRICK_CARD / ORBITAL_STRIKE_CARD / DIRECTIONAL_BLAST_CARD | 4 段，「=== 伤害效果牌 ===」段 |
| 1262–1302 | 41 | CHOCOLATE_CAKE / HAMBURGER / LUXURY_FEAST / YOU_HAVE_I_HAVE / EXPRESS_DELIVERY | 5 段，「=== 新效果牌 ===」段 |

> 关键陷阱：13 张 effect 牌里有 **9 张的 item 常量名与 recipe id 不同名**（如 `CHOCOLATE_CAKE` → `effect_card_chocolate_cake`、`MONSTER_BRICK_CARD` → `effect_card_monster_brick`）。只按 `grep EFFECT_CARD_` 找会**漏 9 段**，所以本次是按「产物常量全集 + `RecipeCategory.MISC` 定位」逐块核的。

- 删后：**1391 行 / 79016 B**（删 170 行，88557 → 79016 B），与主基准提交信息里「每线删 170 行」数值一致。
- 删后 fabric 内 `ATTACK_CARD_` / `DEFENSE_CARD_` / `EFFECT_CARD_` 命中 **0**；只剩 `BRICKS_TAG` 声明（第 23 行），主基准 `neoforge-1.21.1/.../ModRecipeProvider.java:21-22` **同样保留**该声明（现为未使用私有字段，javac 不报错）——属于「与主基准等价」，不是残留。
- 归一化 diff（`diff -u --strip-trailing-cr fabric neo`）= **90 个 hunk，全部是平台差异**：`.save(output::accept)` vs `.save(output)`、`ChipRecipesEnabledCondition`、`NbtAugmentedRecipe`（药水 NBT 约束）、`Mace`、`DataComponentIngredient`，以及 neo 线后续新增的 JILL / DOROTHY / ALANA / KANGEL / AME / LINGLING 立牌等配方。**没有任何一个 hunk 与卡牌配方相关**。
- **必须保留**（已核主基准同款保留）：探天卫星 `SATELLITE_CHIP` 配方把 `ModItems.ORBITAL_STRIKE_CARD` 当**材料**并 `unlockedBy("has_orbital_strike", has(ModItems.ORBITAL_STRIKE_CARD.get()))` —— neo:802/805、forge:864/867 都保留，fabric 也保留（属「以卡牌为材料的筹码配方」，不动）。

### 1.2 数据面（生成物集合比对）

删后实测（递归计数，全部为 `data/astral_dice/` 下）：

| 线 | recipes | advancements | 卡牌类 recipe |
|---|---|---|---|
| fabric-1.20.1（改后） | **108** | **102** | 只剩 `bank_card_high_chip` / `bank_card_low_chip` / `bank_card_unlimited_chip`（3 条） |
| neoforge-1.21.1 | **108** | 108 | 同上 3 条 |
| forge-1.20.1 | **108** | 108 | 同上 3 条 |

- 配方集合：`fabric − neo = ∅`、`neo − fabric = ∅`、`fabric − forge = ∅`、`forge − fabric = ∅` —— **三线配方集合完全一致**（108 = 108 = 108，双向差为空）。
- advancement 集合：fabric 102 vs neo 108，差集是 **6 个 neo 独有立牌成就**（`recipes/misc/{alana,ame,dorothy,jill,kangel,lingling}_sign.json`）—— 既有线差、与卡牌无关；**卡牌成就两侧均为 0**。

## 2. 删除清单（22 配方 + 22 成就，逐条确认都是卡牌）

删除前对 44 个文件逐一核过：recipe 的 `result.item` 就是 `astral_dice:<id>`、advancement 的 criteria 指向同一条配方（`has_<id>` + `astral_dice:<id>`），22 个 id 与「主线 vs fabric」实测集合差**完全一致**，无一例外。sha256 前 12 位如下（归档副本实测）：

| # | id | 类别 | recipe sha256(12) | advancement sha256(12) | 与 forge 存档逐字节相同 |
|---|---|---|---|---|---|
| 1 | `attack_card_charge` | attack | `32e14406b360` | `f209f7d2f9d1` | 是 |
| 2 | `attack_card_medium` | attack | `891741323b91` | `d48336c04891` | 是 |
| 3 | `attack_card_meito` | attack | `326bf13acf8f` | `a69add2d53c9` | 是 |
| 4 | `attack_card_shadow_strike` | attack | `9bb7ed837bfc` | `af1d049125c6` | 是 |
| 5 | `attack_card_large_from_medium` | attack | `7edf1f92f161` | `8f1c836666ed` | 是 |
| 6 | `attack_card_epic_from_large` | attack | `6d5aa16b1d24` | `1bda4c14bf58` | 是 |
| 7 | `defense_card_medium` | defense | `5c587fda9b27` | `ab4294d3dd15` | 是 |
| 8 | `defense_card_large_from_medium` | defense | `c4a6b7924ce3` | `95bdb49c6fa8` | 是 |
| 9 | `defense_card_epic_from_large` | defense | `ebe5df9ccb30` | `d412e67b9761` | 是 |
| 10 | `effect_card_berserk` | effect | `013312567dda` | `1bacaa871d56` | 是 |
| 11 | `effect_card_chocolate_cake` | effect | `939dfc90d925` | `aa77ad37db6c` | 是 |
| 12 | `effect_card_directional_blast` | effect | `302ed036b385` | `eb3138b55edb` | 是 |
| 13 | `effect_card_express_delivery` | effect | `c1e70dc769b6` | `64bfa94b7ec8` | 是 |
| 14 | `effect_card_fight_poison_with_poison` | effect | `7aecb40f2a95` | `f7a570788c99` | 是 |
| 15 | `effect_card_hamburger` | effect | `e56b1c93a225` | `e0b4538536de` | 是 |
| 16 | `effect_card_king_power` | effect | `1445ad6a12b9` | `e7ad74d593f7` | 是 |
| 17 | `effect_card_luxury_feast` | effect | `71b0d5b18f64` | `57bd98d815b3` | 是 |
| 18 | `effect_card_monster_brick` | effect | `509d0107db62` | `8dd3ef0c39a3` | **否**（tag 命名空间） |
| 19 | `effect_card_monster_laser` | effect | `a001f4a40c7e` | `be74055e53f8` | 是 |
| 20 | `effect_card_orbital_strike` | effect | `6c5c9598fd73` | `2620f8e73e9a` | 是 |
| 21 | `effect_card_unwavering` | effect | `93a469f86616` | `fde6bfbf0242` | 是 |
| 22 | `effect_card_you_have_i_have` | effect | `b290d4c734f5` | `466a812ae07d` | 是 |

- `recipes/` = `fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/<id>.json`（22 个）
- `advancements/` = `fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/<id>.json`（22 个）
- 目录计数复核：`recipes/` **130 → 108**；`advancements/recipes/misc/` **123 → 101**（另加 `advancements/recipes/tools/` 1 个 = 递归 124 → 102）。

## 3. provider 删除行数（改前 / 改后 / 删掉）

| 项 | 改前 | 改后 | 差值 |
|---|---|---|---|
| 行数 | 1561 | 1391 | **−170** |
| 字节 | 88557 | 79016 | −9541 |
| CRLF 行 | 1561 | 1391 | −170 |
| lone LF | 0 | 0 | 0 |

删法：按 §1.1 的 5 个区块**从后往前**整段删除（含区块末尾的空行），不重排、不改写任何其它行；改前原文另存于 `C:/Users/xmace/AppData/Local/Temp/b34-ModRecipeProvider.orig.java`（88557 B，仓库外）。

## 4. 归档方案与路径

- 目标：`docs/archive/card-recipes-2026-10-09/fabric-1.20.1/`，结构与 `forge-1.20.1/` **同构**（路径 = 原 `data/astral_dice/` 下相对路径）：
  - `fabric-1.20.1/recipes/*.json`（22）
  - `fabric-1.20.1/advancements/recipes/misc/*.json`（22）
- **采用「全部落盘」，没有用「与主基准逐字节相同则免落盘」**，理由（逐文件 SHA-256 实测）：
  - 与 `neoforge-1.21.1/` 存档：**44 / 44 全部不同**（1.21 是 `recipe/` + `advancement/` 单数目录、JSON 结构也已变化）⇒ 那条豁免对 fabric 根本不成立。
  - 与 `forge-1.20.1/` 存档：**42 / 44 逐字节相同**；例外 2 个 = `effect_card_monster_brick` 的 recipe 与 advancement，唯一差异是 tag 命名空间（fabric `#c:bricks` / forge `#forge:bricks`），属**平台正确差异**，因此更该原样留档而不是拿 forge 存档顶替。
- 写入流程：先复制 → **逐字节回读比对通过** → 才 `unlink` 源文件（删前每个文件的归档副本都与源文件 byte-equal，脚本内断言）。
- `README.md`：只在末尾追加一节 `## fabric-1.20.1（2026-11-01 补档，B34 批次）`（11 行，保持原文件 CRLF；`git diff --stat` = `1 file changed, 11 insertions(+)`）。**归档结构（目录、既有两套存档）未被改动**。

## 5. 四条验收（原始末段）

### 5.1 `:fabric-1.20.1:classes`

```
Reusing configuration cache.
> Task :fabric-1.20.1:processResources
> Task :fabric-1.20.1:compileJava
> Task :fabric-1.20.1:classes

BUILD SUCCESSFUL in 2s
2 actionable tasks: 2 executed
Configuration cache entry reused.
```

### 5.2 `:fabric-1.20.1:remapJar` + `build/libs/` 产物

```
Reusing configuration cache.
> Task :fabric-1.20.1:processIncludeJars UP-TO-DATE
> Task :fabric-1.20.1:compileJava UP-TO-DATE
> Task :fabric-1.20.1:processResources UP-TO-DATE
> Task :fabric-1.20.1:classes UP-TO-DATE
> Task :fabric-1.20.1:jar
> Task :fabric-1.20.1:remapJar

BUILD SUCCESSFUL in 3s
5 actionable tasks: 2 executed, 3 up-to-date
Configuration cache entry reused.
```

`fabric-1.20.1/build/libs/` 列表（只有 1 个产物，无 sources 变体）：

```
astral_dice-1.5.0-rc.7+fabric_1.20.1.jar    3017627 B
```

### 5.3 产物自检（jar 内 recipes 里的 22 个 id）

```
jars: [(astral_dice-1.5.0-rc.7+fabric_1.20.1.jar, 3017627)]
--- astral_dice-1.5.0-rc.7+fabric_1.20.1.jar
  recipe entries: 126  advancement entries: 107
  card-id hits in recipe/advancement: 0 []
  remaining *card* recipes: 4 [bank_card_high_chip.json, bank_card_low_chip.json, bank_card_unlimited_chip.json, card_shop.json]
  satellite_chip in jar: True
```

（补充口径：`data/astral_dice/recipes/` 实际 **125 个 json** = 生成物 108 + `src/main/resources` 17；`data/astral_dice/advancements/` 实际 **103 个 json** = 102 + `src/main/resources` 1。上面 126 / 107 含 zip 目录条目。jar↔源码双向差集实测均为空。）

**命中数：22 个 id 在 jar 的 `recipe`/`advancement` 里 = 0 个。**

### 5.4 `python tools/verify_fabric_assets.py`

```
jar = fabric-1.20.1\build\libs\astral_dice-1.5.0-rc.7+fabric_1.20.1.jar

[1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计)
[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 1094 / en_us 1094 / ja_jp 1094
[6] 手册闭环: 条目 184 / 引用配方 125
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: [repulsion]
[8] 饰品槽位图标: 检查 7 条 icon

=== PASS: 8 项闭环全部通过 ===
```

改前 / 改后计数（同一脚本，改前 = provider 已改、44 个 json 未删时跑）：

| 项 | 改前 | 改后 | 说明 |
|---|---|---|---|
| [6] 引用配方 | **147** | **125** | 差 22 = 正好被删的 22 条卡牌配方，属**预期**，手册无悬空引用 |
| [2] 标签闭环 | 引用 11 / 自建 29 / 有提供者 11 | 引用 10 / 自建 29 / 有提供者 10 | 差 1 = 唯一使用 `#c:bricks` 标签的配方就是被删的 `effect_card_monster_brick`；标签文件本身仍在（`data/c/tags/items/bricks.json`），**仍 PASS**，见 §7.2 |
| 其余 [1][3][4][5][7][8] | 不变 | 不变 | — |

### 5.5 `python tools/check_lang_sync.py`

```
[OK]   neoforge-1.21.1: en_us=1070 键，3 份语言键集一致
[OK]   forge-1.20.1: en_us=1070 键，3 份语言键集一致
[OK]   neoforge-26.1.2: en_us=1094 键，3 份语言键集一致
[OK]   fabric-1.20.1: en_us=1094 键，3 份语言键集一致
```

本批**未改任何 lang**（另一批次在写），四线全 `[OK]`。

### 5.6 `scripts/verify/verify_chip_recipes.ps1`（筹码配方不得被删）

```
== neo (neoforge-1.21.1) ==
  [java] neoforge-1.21.1\src\main\java\...\datagen\ModRecipeProvider.java
  [java] 筹码 67 | 一致 67 | 不一致 0 | 缺配方 0
  [gen] 配方文件 108 | 筹码 67 | 一致 67 | 不一致 0 | 缺文件 0
  [jar] 配方文件 125 | 筹码 67 | 一致 67 | 不一致 0 | 缺文件 0

== forge (forge-1.20.1) ==
  [java] 筹码 67 | 一致 67 | 不一致 0 | 缺配方 0
  [gen] 配方文件 108 | 筹码 67 | 一致 67 | 不一致 0 | 缺文件 0
  [jar] 配方文件 125 | 筹码 67 | 一致 67 | 不一致 0 | 缺文件 0

RESULT: ALL OK
```

注意两点（见 §7.3）：① `tools/verify_chip_recipes.ps1` 不存在，实际脚本在 `scripts/verify/verify_chip_recipes.ps1`；② 它只覆盖 neo / forge 双版本，**不覆盖 fabric**，所以 fabric 的筹码未被机器判据覆盖（本批没删任何筹码，`fabric recipes 108` 与两基准 108 一致是间接证据）。

## 6. 残留自检

### 6.1 生成物（硬判据，通过）

- `src/generated` 下 22 个 id 的 recipe / advancement 文件：**0 个**（jar 内同为 0，见 §5.3）。
- `grep -rn "attack_card_\|defense_card_\|effect_card_" fabric-1.20.1/src/generated`（任务书原文命令）实测 **31 行**，构成如下 —— **不是卡牌配方残留**：
  - **29 行** = `assets/astral_dice/models/item/*.json` 的**物品模型**（`"layer0": "astral_dice:item/attack_card_*"`）——物品还在，模型必须留；主基准同款文件同样是 29 行。
  - **2 行** = `data/astral_dice/recipes/satellite_chip.json:9` 与 `data/astral_dice/advancements/recipes/misc/satellite_chip.json:9` 里 `astral_dice:effect_card_orbital_strike` 作**材料/条件**——主基准同款保留（neo 对应 2 行）。
  - 同一命令在主基准 `neoforge-1.21.1/src/generated` 上是 **60 行** = 29 模型 + 2 卫星 + **29 行 `src/generated/.cache/...` 哈希索引**（fabric 侧没有 `.cache` 目录，故少这 29 行）。

> 因此任务书里「该命令命中 0」这一条在**主基准上也不成立**；判定口径应落到「recipe/advancement 文件 0 个 + 无卡牌作为配方产物」，这两条都满足。fabric 的 31 行严格少于主基准的非缓存命中面（31 = 29+2，与 neo 的 29+2 同形）。

### 6.2 源码（配方产物引用）

- `grep -n "ATTACK_CARD_\|DEFENSE_CARD_\|EFFECT_CARD_" fabric-1.20.1/.../datagen/ModRecipeProvider.java` → **0**。
- `grep -rn "RecipeBuilder.*CARD" fabric-1.20.1/src` → 只剩 3 条 `BANK_CARD_LOW/HIGH/UNLIMITED`（**筹码**，三线都有）。
- 其余 `ATTACK_CARD_` / `DEFENSE_CARD_` / `EFFECT_CARD_` 出现在 20+ 个非 datagen 文件里（`ModItems` 注册、创造栏、tooltip、卡牌商店目录、战斗结算、立牌管理、音效名……），全部**不是配方产物**，正常保留。

## 7. 无法判定 / 需裁决项

### 7.1 「`grep ... src/generated` 命中 0」的判据本身与主基准冲突
任务书原文把该命令的期望值写成 0，但主基准是 60（29 模型 + 2 卫星 + 29 `.cache`）。本批按「recipe/advancement 文件为 0、无卡牌作配方产物」执行并全绿。**若要求字面 0，则需要连卡牌物品模型一起删，那会破坏物品渲染，属另一批决策。**

### 7.2 [2] 标签闭环 11 → 10 的归因
实测 `#c:bricks` 在 fabric 里唯一的**消费方**就是被删的 `effect_card_monster_brick` 配方（标签定义文件 `data/c/tags/items/bricks.json` 仍在，`自建 29` 未变）。所以 11→10 我认为是本批预期，且守门仍 PASS。但脚本只报计数、不报具体 tag 名，**我无法 100% 证明没有第二个来源同时消失**——给出的是「唯一消费方」的静态证据。

### 7.3 筹码守门脚本的路径与覆盖面
- `tools/verify_chip_recipes.ps1` **不存在**（`tools/` 下只有 `check_lang_sync.ps1` / `check_mod_sources.ps1` / `run_mixin_stack_sanitizer_test.ps1`）。等价脚本在 `scripts/verify/verify_chip_recipes.ps1`，已实跑：`RESULT: ALL OK`。
- 该脚本用 **Windows PowerShell 5.1 跑不起来**（文件是无 BOM 的 UTF-8，5.1 按 ANSI 解码，第 367 行 `Write-Out 'RESULT: ALL OK'` 抛 ParserError，`EXIT=1`）；用 **pwsh 7** 正常。**这是既有缺陷，不是本批引入，我没有修它。**
- 它只跑 neo / forge 双版本（脚本头注释即言「双版本全跑」），**fabric 筹码无机器判据**。

### 7.4 归档豁免方案的选择
按「逐字节相同则免落盘」的豁免**不可用**（44/44 与 neo 存档不同），故 fabric 侧 44 个文件全部落盘。若你更希望改成「与 forge 存档相同则指向 forge」，需要动 README 之外的归档结构，本批**未做**。

### 7.5 没有跑 `runDatagen`（因此「下次 runData 不会长回来」是推理而非实测）
`runData` 会重写 `src/generated` —— 而该目录此刻有 **35 个他人未提交的新文件**（`abyss_reagent_chip` / `*_sign` / `event_ticket` / `extra_battery_chip` 等模型）。跑它必覆盖他人改动，故**不跑**。判据替代：22 段生成代码已从 provider 里删除（§1.1/§3），且 fabric provider 内卡牌常量命中 0 ⇒ 下次 datagen 不会再产出这 22 条。这一步**没有实跑证明**，请知悉。

### 7.6 未验证 / 不在本批范围
- 未实机、未启动游戏（任务书禁止），故「卡牌改为商店获得 + JEI 显示获取方式」的实机表现未验。
- 未检查其他三线（本批只动 fabric）。
- `fabric-1.20.1/bin/` 下有 IDE 旧副本（含 model/lang/patchouli 等），与源码无关，未计入任何判据、也未改动。

## 8. 改动文件清单（供复核）

- `M  fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java`（−170 行，CRLF）
- `D  fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/<22 个 id>.json`
- `D  fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/<22 个 id>.json`
- `M  docs/archive/card-recipes-2026-10-09/README.md`（+11 行）
- `?? docs/archive/card-recipes-2026-10-09/fabric-1.20.1/`（44 个文件，新增）

> 工作树里其余 `M` / `??` 均为他人未提交改动（含 fabric 的 35 个新生成物、lang、patchouli 等），本批**未触碰**；未执行任何 `git add` / `commit` / `stash` / `checkout` / `reset`。
