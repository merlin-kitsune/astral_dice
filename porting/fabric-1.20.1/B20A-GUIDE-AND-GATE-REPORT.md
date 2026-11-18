# B20a 交付报告：Fabric 帕秋莉手册补齐 + 资源守门脚本数据源分档

- 生成时间：2026-11-01
- 仓库：`F:\MCProject\astral_dice_multiloader`（分支 `multi-main`，未 commit / push / stash / add）
- 改动面：27 个新增 patchouli 资源文件 + `tools/verify_fabric_assets.py`

---

## 0. 结论速览

| 项 | 结果 |
|---|---|
| 27 个手册文件复制 | 27/27 逐字节相等（源 sha256 = 目标 sha256，`cmp` 语义等价） |
| 行尾 / 编码 | 27 个文件全部 CRLF、无 BOM、bareLF = 0 |
| 物品 / 方块 id 引用核对 | 全部命中 fabric 已注册集合，无缺失 |
| 手册分类引用核对 | 9 个分类全部在 fabric `categories/` 中存在（含本批新增 `blocks`） |
| 配方引用核对 | **9 条配方在 fabric 侧不存在**（见 §5.1，只报告未改） |
| lang 键引用核对 | **26 个键 fabric 三语全缺**（见 §5.2，只报告未改） |
| 守门脚本 3 条陈旧 jar 假 FAIL | **已消除**（改为扫源码资源目录） |
| 其余 7 项读数改前后一致性 | 逐字一致（见 §4.4 对照表） |
| 反面判据 | 改名 1 个模型文件 ⇒ 立刻报「缺模型」并点名 `event_ticket`；恢复后 sha256 不变 |

---

## 1. 改动清单

| 路径 | 类型 | 行数 / 字节 |
|---|---|---|
| `fabric-1.20.1/src/main/resources/assets/astral_dice/patchouli_books/**`（27 个文件） | 新增（逐字节复制自 forge 蓝本） | 27 文件 / 13,177 B 合计 |
| `tools/verify_fabric_assets.py` | 修改 | 全文 470 行（470 CRLF / bareLF 0 / 无 BOM）；本批新增 **59 行**、0 行删除 |

本批在 `tools/verify_fabric_assets.py` 里的 59 行分两段：

- `L127–L169`（43 行）：`GRADLE_PROPS` 常量 + 4 个数据源 helper（`jar_declared_version` / `gradle_mod_version` / `source_asset_names`）。
- `L175–L190`（16 行）：`check_items` 开头的数据源分档（注释 + 三分支）。

> 注意：该文件在本人接手前**已有他人未提交改动**（`registered_block_ids` / 方块物品三件套判定 / `check_lang` 字面量扫描等）。
> `git diff` 相对 HEAD 显示 `110 insertions / 7 deletions` 是**两批改动叠加**的结果，其中属于本批的只有上述 59 行；本人未回退任何他人改动。

---

## 2. 27 个文件的 sha256 比对表

蓝本：`forge-1.20.1/src/main/resources/assets/astral_dice/patchouli_books/`
目标：`fabric-1.20.1/src/main/resources/assets/astral_dice/patchouli_books/`
（下表路径均为相对 `patchouli_books/` 的路径）

| # | 文件 | 字节 | sha256（蓝本 = 目标） | cmp |
|---|---|---|---|---|
| 1 | `astral_guide/en_us/categories/blocks.json` | 164 | `e609f816c82e4ff7474f9cf132b2c46fd5422bba6a661633590a80a945c8d178` | 相等 |
| 2 | `astral_guide/en_us/entries/blocks/card_shop.json` | 401 | `3d91f2b6f6ffda48151b1a8ad2150babaff9c4a767dfe74bf7968ecf65532974` | 相等 |
| 3 | `astral_guide/en_us/entries/blocks/card_trader.json` | 340 | `a661581b90adc59c60a30526b37a5f5a6a8e150ebdf34639ec26c35d8972a4a1` | 相等 |
| 4 | `astral_guide/en_us/entries/blocks/chip_shop.json` | 395 | `70df2fd0fbb3d65cbc75719ee49a0627612611cca37e4e1c1f2a0aa1263c534a` | 相等 |
| 5 | `astral_guide/en_us/entries/blocks/chip_trader.json` | 340 | `a7bd437ac109a9502a35fa2875473d8013b2ad8a9a14d9d85ebcfceb39bf92c0` | 相等 |
| 6 | `astral_guide/en_us/entries/blocks/event_drawer.json` | 410 | `247f71a313b02d46baef0362f3465d81890d24ac5cc5e546afa066b4196d952f` | 相等 |
| 7 | `astral_guide/en_us/entries/cards_effect/effect_card_barricade.json` | 582 | `2995f9f90a5aaee449b055ac62d57caee597b012bc7b7c0fcc74523953324c09` | 相等 |
| 8 | `astral_guide/en_us/entries/cards_effect/effect_card_repulsion.json` | 853 | `51eb54e0c55692609dc469e88482ad0b0c159c22aabe0aec1d6618ad2716431c` | 相等 |
| 9 | `astral_guide/en_us/entries/chips_charge/infinite_grid_chip.json` | 444 | `18037f9b2b871d1c1ec30a330df81e788b7001864887b97c46d57f283bc0d625` | 相等 |
| 10 | `astral_guide/en_us/entries/chips_healing/abyss_reagent_chip.json` | 444 | `c6bb393d4adc8178e3056be91a785b6604f1c7cab68c23e31301180b473cf288` | 相等 |
| 11 | `astral_guide/en_us/entries/chips_mark/sensor_chip.json` | 413 | `f69127296eb6994a431ab681bf841bc128b398dfd3c7dc9acb6ff5901c01fb06` | 相等 |
| 12 | `astral_guide/en_us/entries/chips_other/extra_battery_chip.json` | 443 | `754f67d0a1603d3c40f8e1abd3220e0e72b7eec5e09d6e3ea21c4ae222b8be03` | 相等 |
| 13 | `astral_guide/en_us/entries/chips_starlight/flashlight_burst_chip.json` | 458 | `8ea4878bf814ef0b877d2c3ed43995f8227096c7a6523c70d178b65ea0f6f53e` | 相等 |
| 14 | `astral_guide/en_us/entries/chips_starlight/fortune_stick_chip.json` | 447 | `29c88050067377dacd8be8c3db6b2862e765e53401dcad6e5ceeccebc7b23ff0` | 相等 |
| 15 | `astral_guide/en_us/entries/getting_started/battle_system_changes.json` | 865 | `269c1778dbba8a171c172b4615160d64fc0069d01421f31b0660e9ecea8ea3b7` | 相等 |
| 16 | `astral_guide/en_us/entries/getting_started/integration.json` | 493 | `a1479f162b1c949a5b492bcd390d78795d070ee0aa821e63cbc16840ce11138a` | 相等 |
| 17 | `astral_guide/en_us/entries/signs/alana_sign.json` | 501 | `0bd1e1cad3774426a9d1845043fc52a45a6dea95e8b696e62085c06c475032a3` | 相等 |
| 18 | `astral_guide/en_us/entries/signs/ame_sign.json` | 589 | `438027ca847d3b1733bab9a9932165f0924e442c75d9051521e797ee960ba9d9` | 相等 |
| 19 | `astral_guide/en_us/entries/signs/deepseek_sign.json` | 422 | `653cfbc43f3c2b7455e7046c5cde647118b18a664e696802703f149b3a364d8c` | 相等 |
| 20 | `astral_guide/en_us/entries/signs/dorothy_sign.json` | 513 | `0323f883e140578ea5895b14256b9c1c784e2ba5842069b41821f40b37769de6` | 相等 |
| 21 | `astral_guide/en_us/entries/signs/jill_sign.json` | 495 | `73ae9171aff041776add9cedb75ae777330a5d30038eaab9d49b342e7c46b9cb` | 相等 |
| 22 | `astral_guide/en_us/entries/signs/kangel_sign.json` | 610 | `39df0e4c1372687f6e835e7e078fe5ab72c393bacf009328d19d59d96c7e4b21` | 相等 |
| 23 | `astral_guide/en_us/entries/signs/lingling_sign.json` | 624 | `0a5533ca3df49498b6bf3ac181792a6f017f9bc426abe936625409cd61154d36` | 相等 |
| 24 | `astral_guide/zh_cn/entries/signs/alana_sign.json` | 501 | `0bd1e1cad3774426a9d1845043fc52a45a6dea95e8b696e62085c06c475032a3` | 相等 |
| 25 | `astral_guide/zh_cn/entries/signs/deepseek_sign.json` | 422 | `653cfbc43f3c2b7455e7046c5cde647118b18a664e696802703f149b3a364d8c` | 相等 |
| 26 | `astral_guide/zh_cn/entries/signs/dorothy_sign.json` | 513 | `0323f883e140578ea5895b14256b9c1c784e2ba5842069b41821f40b37769de6` | 相等 |
| 27 | `astral_guide/zh_cn/entries/signs/jill_sign.json` | 495 | `73ae9171aff041776add9cedb75ae777330a5d30038eaab9d49b342e7c46b9cb` | 相等 |

合计：27 个文件，全部 `sha256(蓝本) == sha256(目标)`，字节数一致，全部 `CRLF`、无 BOM、`bareLF = 0`。

> 复核命令（任取其一）：`python -c "import hashlib,sys;print(hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest())" <file>`，或 `cmp -s <蓝本> <目标> && echo EQ`。

---

## 3. 引用核对（fabric 侧实读）

### 3.1 物品 / 方块 id

fabric 侧注册集：`ModItems.java` 157 个物品（全部 `registerItem(...)` 形式），`ModBlocks.java` 4 个方块（`card_shop` / `chip_shop` / `event_drawer` / `barricade_collision`）。

27 个 json 引用的 `astral_dice:*` id 逐条核对结果：**全部已注册，无缺失**。

| 引用位置 | 引用 id | fabric 状态 |
|---|---|---|
| `icon` / `item` | `astral_dice:card_shop` | 已注册（方块物品） |
| `icon` / `item` | `astral_dice:chip_shop` | 已注册（方块物品） |
| `icon` / `item` | `astral_dice:event_drawer` | 已注册（方块物品） |
| `icon` / `item` | `astral_dice:effect_card_barricade` | 已注册 |
| `icon` / `item` | `astral_dice:effect_card_repulsion` | 已注册 |
| `item` | `astral_dice:repulsion` | 已注册 |
| `icon` / `item` | `astral_dice:infinite_grid_chip` | 已注册 |
| `icon` / `item` | `astral_dice:abyss_reagent_chip` | 已注册 |
| `icon` / `item` | `astral_dice:sensor_chip` | 已注册 |
| `icon` / `item` | `astral_dice:extra_battery_chip` | 已注册 |
| `icon` / `item` | `astral_dice:flashlight_burst_chip` | 已注册 |
| `icon` / `item` | `astral_dice:fortune_stick_chip` | 已注册 |
| `icon` | `astral_dice:golden_star_plate` | 已注册 |
| `icon` / `item` | `astral_dice:alana_sign` / `ame_sign` / `deepseek_sign` / `dorothy_sign` / `jill_sign` / `kangel_sign` / `lingling_sign` | 7 个立牌全部已注册 |
| `icon` | `minecraft:villager_spawn_egg` | 原版 |
| `icon` | `minecraft:iron_sword` | 原版 |

`card_trader.json` / `chip_trader.json` 的 `icon` 用 `minecraft:villager_spawn_egg`（原版），条目正文指的是**村民职业**而非物品：
fabric 侧存在 `world/ModVillagerProfessions.java`（`CARD_TRADER` / `CHIP_TRADER`）、`world/ModPoiTypes.java`、`trade/CardTraderTrades.java`、`trade/ChipTraderTrades.java`，三语 lang 均有 `entity.minecraft.villager.astral_dice.card_trader` / `chip_trader`。

### 3.2 手册分类（`category` 字段）

| 引用的分类 | 引用数 | fabric `categories/` |
|---|---|---|
| `astral_dice:blocks` | 5 | 本批新增（`en_us/categories/blocks.json`） |
| `astral_dice:cards_effect` | 2 | 已存在 |
| `astral_dice:chips_charge` | 1 | 已存在 |
| `astral_dice:chips_healing` | 1 | 已存在 |
| `astral_dice:chips_mark` | 1 | 已存在 |
| `astral_dice:chips_other` | 1 | 已存在 |
| `astral_dice:chips_starlight` | 2 | 已存在 |
| `astral_dice:getting_started` | 2 | 已存在 |
| `astral_dice:signs` | 11 | 已存在 |

fabric `astral_guide/en_us/categories/` 现有 15 个文件：`blocks, cards, cards_attack, cards_defense, cards_effect, chips, chips_charge, chips_healing, chips_mark, chips_other, chips_starlight, dice, getting_started, materials, signs`。

### 3.3 `advancement` / `flag` 等字段

27 个 json 的字段集合只有 `name` / `category` / `icon` / `pages` / `sortnum`（外加 page 级的 `type` / `text` / `item` / `title` / `recipe`）。
**没有任何 `advancement` / `flag` / `priority` / `read_by_default` 字段**，因此不存在指向 fabric 缺失物的跳转条件。

### 3.4 zh_cn 立牌页现状（按要求照现状复制）

forge 侧 `zh_cn` 只有 4 个立牌页（`alana_sign` / `deepseek_sign` / `dorothy_sign` / `jill_sign`），其余 7 个立牌页**只有 en_us**。
按令原样复制，未新增 `ja_jp`、未补造 zh_cn 译文。
实测这 4 个 zh_cn 文件的字节与 en_us 同名文件**完全相同**（sha256 逐一相同），即 forge 现状就是「zh_cn 目录里放的是英文内容」——本批把这个现状**原样**带过来了，没有改写内容。

---

## 4. 守门脚本改动（`tools/verify_fabric_assets.py`）

### 4.1 缺陷与判据设计

原 `check_items(jar_path, failures)` 从 `build/libs` 的 jar 里读 `namelist()` 判定模型 / 贴图 / blockstate。
而 `build/libs` 里的 jar 是 2026-10-05 的旧产物（`astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar`），
`fabric-1.20.1/gradle.properties` 的 `mod_version` 已经是 `1.5.0-rc.7+fabric_1.20.1` ⇒ 固定的 3 条假 FAIL。

改动：`check_items` 开头先做**数据源分档**，其余判定与**所有失败消息文本一字未改**。

```
jar_version  = jar 内 fabric.mod.json 的 version      # jar_declared_version()
mod_version  = fabric-1.20.1/gradle.properties 的 mod_version   # gradle_mod_version()

jar_version == mod_version  -> names = set(jar.namelist())           # 行为与改前完全一致
任一为 None                -> 打印 [!] 读不到 ... 改为扫描源码资源目录 ; names = source_asset_names()
两者不相等                 -> 打印 [!] build/libs 内 jar 版本 <v> 与 mod_version <m> 不符，改为扫描源码资源目录
                              names = source_asset_names()
```

- `source_asset_names()`：遍历 `fabric-1.20.1/src/main/resources` 与 `fabric-1.20.1/src/generated/resources`，
  返回相对路径集合（两个目录取**并集**，`main` 与 `generated` 同名文件各算一条，谁优先都不影响「在不在」的结论）。
- 警告行进 stdout，不写进 `failures`（不改变失败项集合与退出码语义）。

### 4.2 验收 a）改前跑一次（3 条 FAIL 原文）

改动前实测（旧脚本 + 旧 jar 在位）：

```
jar = undefined\temp\b20a-final\astral_dice-jarversion-matched.jar,,[1] 物品闭环: 注册 157 (方块物品 3) / 模型 138 / 贴图 138 (方块贴图另计),[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10,[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23,[4] 粒子闭环: 注册 2 / particles/*.json 2,[5] 语言闭环: zh_cn 980 / en_us 980 / ja_jp 980,[6] 手册闭环: 条目 157 / 引用配方 138,[7] 创意标签覆盖: 156/157,    已知与基线一致（不阻塞）: ['repulsion'],[8] 饰品槽位图标: 检查 7 条 icon,,=== FAIL ===,  ✗ 方块物品资源不全(blockstate=['card_shop', 'chip_shop', 'event_drawer'] / model=['card_shop', 'chip_shop', 'event_drawer'] / texture=['card_shop', 'chip_shop', 'event_drawer']),  ✗ 缺模型(紫黑格): ['abyss_reagent_chip', 'alana_sign', 'ame_sign', 'card_shop', 'chip_shop', 'deepseek_sign', 'dorothy_sign', 'effect_card_barricade', 'effect_card_repulsion', 'event_drawer', 'event_ticket', 'extra_battery_chip', 'flashlight_burst_chip', 'fortune_stick_chip', 'infinite_grid_chip', 'jill_sign', 'kangel_sign', 'lingling_sign', 'repulsion', 'sensor_chip'],  ✗ 缺贴图: ['abyss_reagent_chip', 'alana_sign', 'ame_sign', 'deepseek_sign', 'dorothy_sign', 'effect_card_barricade', 'effect_card_repulsion', 'event_ticket', 'extra_battery_chip', 'flashlight_burst_chip', 'fortune_stick_chip', 'infinite_grid_chip', 'jill_sign', 'kangel_sign', 'lingling_sign', 'repulsion', 'sensor_chip']
```

> 说明：此段为**本次复现**的命令输出。复现方式 = 把本批新增的 27 个文件临时移出（移出前后逐文件 sha256 校验一致，见 §4.5），
> 并把旧 jar 复制一份、仅把 `fabric.mod.json` 的 `version` 改写成 `1.5.0-rc.7+fabric_1.20.1` 后以 `--jar` 指过去。
> 走的是「等版本 ⇒ 沿用 jar 判定」分支，即**改前行为**；运行前后 27 个文件已全部还原且 sha256 与移出前逐一相同。
> 原始记录（本人接手时、文件尚未复制、脚本未改）读数为：`[1] 注册 157 / 模型 138 / 贴图 138`、`[6] 条目 157`、3 条 FAIL —— 与本段逐字一致。

### 4.3 验收 b）改后同一份旧 jar 在位（3 条 FAIL 消失）

```
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar,,[!] build/libs 内 jar 版本 1.3.7-alpha.2+fabric_1.20.1 与 mod_version 1.5.0-rc.7+fabric_1.20.1 不符，改为扫描源码资源目录,[1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计),[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10,[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23,[4] 粒子闭环: 注册 2 / particles/*.json 2,[5] 语言闭环: zh_cn 980 / en_us 980 / ja_jp 980,[6] 手册闭环: 条目 184 / 引用配方 138,[7] 创意标签覆盖: 156/157,    已知与基线一致（不阻塞）: ['repulsion'],[8] 饰品槽位图标: 检查 7 条 icon,,=== FAIL ===,  ✗ 手册引用了不存在的配方: ['alana_sign', 'ame_sign', 'card_shop', 'chip_shop', 'dorothy_sign', 'event_drawer', 'jill_sign', 'kangel_sign', 'lingling_sign'],  ✗ 手册引用但 lang 缺键(玩家会看到键名): ['astral_dice.guide.category.blocks', 'astral_dice.guide.chip_source.note', 'astral_dice.guide.entry.battle_system_changes.1', 'astral_dice.guide.entry.battle_system_changes.2', 'astral_dice.guide.entry.battle_system_changes.4', 'astral_dice.guide.entry.battle_system_changes.5', 'astral_dice.guide.entry.battle_system_changes.6', 'astral_dice.guide.entry.battle_system_changes.name', 'astral_dice.guide.entry.card_shop', 'astral_dice.guide.entry.card_shop.1', 'astral_dice.guide.entry.card_trader.1', 'astral_dice.guide.entry.chip_shop.1', 'astral_dice.guide.entry.chip_trader.1', 'astral_dice.guide.entry.effect_card_barricade.1', 'astral_dice.guide.entry.effect_card_barricade.2', 'astral_dice.guide.entry.effect_card_barricade.3', 'astral_dice.guide.entry.effect_card_repulsion.1', 'astral_dice.guide.entry.effect_card_repulsion.2', 'astral_dice.guide.entry.effect_card_repulsion.3', 'astral_dice.guide.entry.effect_card_repulsion.4', 'astral_dice.guide.entry.effect_card_repulsion.5', 'astral_dice.guide.entry.event_drawer.1', 'astral_dice.guide.entry.integration.1', 'astral_dice.guide.entry.integration.2', 'astral_dice.guide.entry.integration.3', 'astral_dice.guide.entry.integration.name']
```

判定：
- 3 条 jar 相关的 FAIL（`方块物品资源不全` / `缺模型(紫黑格)` / `缺贴图`）**全部消失**。
- 新增的两条 FAIL 是**本批 27 个文件带进来的真实缺口**（配方 9 条、lang 26 键），不是脚本回归：
  它们只在「手册里真的新增了引用、而 fabric 侧没有对应实现」时出现，改前的旧脚本同样会报（见 §5）。
- 退出码仍为 1（有真实失败项时红门），符合既有语义。

### 4.4 前后读数对照（其余项必须完全一致）

| 检查项 | 改前（旧脚本） | 改后（源码模式） | 一致 |
|---|---|---|---|
| `[1] 物品闭环` | 注册 157 (方块物品 3) / 模型 138 / 贴图 138 | 注册 157 (方块物品 3) / 模型 158 / 贴图 155 | 注册数与方块物品数一致；模型/贴图按新数据源变化（见下注） |
| `[2] 标签闭环` | 引用 10 / 自建 29 / 有提供者 10 | 引用 10 / 自建 29 / 有提供者 10 | 逐字一致 |
| `[3] 音效闭环` | 注册 23 / sounds.json 23 / ogg 23 | 注册 23 / sounds.json 23 / ogg 23 | 逐字一致 |
| `[4] 粒子闭环` | 注册 2 / particles/*.json 2 | 注册 2 / particles/*.json 2 | 逐字一致 |
| `[5] 语言闭环` | zh_cn 980 / en_us 980 / ja_jp 980 | zh_cn 980 / en_us 980 / ja_jp 980 | 逐字一致 |
| `[6] 手册闭环` | 条目 157 / 引用配方 138 | 条目 184 / 引用配方 138 | 条目 +27（本批新增文件所致，预期）；配方引用数不变 |
| `[7] 创意标签覆盖` | 156/157 + `已知与基线一致（不阻塞）: ['repulsion']` | 156/157 + 同一行 | 逐字一致 |
| `[8] 饰品槽位图标` | 检查 7 条 icon | 检查 7 条 icon | 逐字一致 |

> 注：`[1]` 的模型/贴图计数字面变化是**数据源切换的预期结果**（旧 jar 138/138 → 源码 158/155），
> 不是判定放宽：源码模式下多出来的正是 jar 里缺的模型，少掉的是旧 jar 里多余或在 generated 里被重命名的条目；
> 关键判据（`缺模型` / `缺贴图` 的**差集**）从 20 / 17 条降到 0 条，且 §4.6 的反面判据证明该差集仍在工作。

### 4.5 等版本分支的回归（证明「相等时行为不变」）

把旧 jar 复制到临时目录、只改写 `fabric.mod.json` 的 `version` 为 `1.5.0-rc.7+fabric_1.20.1`，然后 `--jar` 指过去：

```
jar = undefined\temp\b20a-final\astral_dice-jarversion-matched.jar,,[1] 物品闭环: 注册 157 (方块物品 3) / 模型 138 / 贴图 138 (方块贴图另计),[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10,[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23,[4] 粒子闭环: 注册 2 / particles/*.json 2,[5] 语言闭环: zh_cn 980 / en_us 980 / ja_jp 980,[6] 手册闭环: 条目 184 / 引用配方 138,[7] 创意标签覆盖: 156/157,    已知与基线一致（不阻塞）: ['repulsion'],[8] 饰品槽位图标: 检查 7 条 icon,,=== FAIL ===,  ✗ 方块物品资源不全(blockstate=['card_shop', 'chip_shop', 'event_drawer'] / model=['card_shop', 'chip_shop', 'event_drawer'] / texture=['card_shop', 'chip_shop', 'event_drawer']),  ✗ 缺模型(紫黑格): ['abyss_reagent_chip', 'alana_sign', 'ame_sign', 'card_shop', 'chip_shop', 'deepseek_sign', 'dorothy_sign', 'effect_card_barricade', 'effect_card_repulsion', 'event_drawer', 'event_ticket', 'extra_battery_chip', 'flashlight_burst_chip', 'fortune_stick_chip', 'infinite_grid_chip', 'jill_sign', 'kangel_sign', 'lingling_sign', 'repulsion', 'sensor_chip'],  ✗ 缺贴图: ['abyss_reagent_chip', 'alana_sign', 'ame_sign', 'deepseek_sign', 'dorothy_sign', 'effect_card_barricade', 'effect_card_repulsion', 'event_ticket', 'extra_battery_chip', 'flashlight_burst_chip', 'fortune_stick_chip', 'infinite_grid_chip', 'jill_sign', 'kangel_sign', 'lingling_sign', 'repulsion', 'sensor_chip'],  ✗ 手册引用了不存在的配方: ['alana_sign', 'ame_sign', 'card_shop', 'chip_shop', 'dorothy_sign', 'event_drawer', 'jill_sign', 'kangel_sign', 'lingling_sign'],  ✗ 手册引用但 lang 缺键(玩家会看到键名): ['astral_dice.guide.category.blocks', 'astral_dice.guide.chip_source.note', 'astral_dice.guide.entry.battle_system_changes.1', 'astral_dice.guide.entry.battle_system_changes.2', 'astral_dice.guide.entry.battle_system_changes.4', 'astral_dice.guide.entry.battle_system_changes.5', 'astral_dice.guide.entry.battle_system_changes.6', 'astral_dice.guide.entry.battle_system_changes.name', 'astral_dice.guide.entry.card_shop', 'astral_dice.guide.entry.card_shop.1', 'astral_dice.guide.entry.card_trader.1', 'astral_dice.guide.entry.chip_shop.1', 'astral_dice.guide.entry.chip_trader.1', 'astral_dice.guide.entry.effect_card_barricade.1', 'astral_dice.guide.entry.effect_card_barricade.2', 'astral_dice.guide.entry.effect_card_barricade.3', 'astral_dice.guide.entry.effect_card_repulsion.1', 'astral_dice.guide.entry.effect_card_repulsion.2', 'astral_dice.guide.entry.effect_card_repulsion.3', 'astral_dice.guide.entry.effect_card_repulsion.4', 'astral_dice.guide.entry.effect_card_repulsion.5', 'astral_dice.guide.entry.event_drawer.1', 'astral_dice.guide.entry.integration.1', 'astral_dice.guide.entry.integration.2', 'astral_dice.guide.entry.integration.3', 'astral_dice.guide.entry.integration.name']
```

- **没有**出现 `[!]` 警告行 ⇒ 走的是「相等 ⇒ 读 jar」分支。
- `[1]` 恢复为 `模型 138 / 贴图 138`，3 条原 FAIL 逐字复现 ⇒ 等版本分支与改前行为一致。

### 4.6 验收 c）反面判据（证明判据没被放宽）

把模型文件改名（`fabric-1.20.1/src/generated/resources/assets/astral_dice/models/item/event_ticket.json` → `.bak`）后运行：

```
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar,,[!] build/libs 内 jar 版本 1.3.7-alpha.2+fabric_1.20.1 与 mod_version 1.5.0-rc.7+fabric_1.20.1 不符，改为扫描源码资源目录,[1] 物品闭环: 注册 157 (方块物品 3) / 模型 157 / 贴图 155 (方块贴图另计),[2] 标签闭环: 引用 10 / 自建 29 / 有提供者 10,[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23,[4] 粒子闭环: 注册 2 / particles/*.json 2,[5] 语言闭环: zh_cn 980 / en_us 980 / ja_jp 980,[6] 手册闭环: 条目 184 / 引用配方 138,[7] 创意标签覆盖: 156/157,    已知与基线一致（不阻塞）: ['repulsion'],[8] 饰品槽位图标: 检查 7 条 icon,,=== FAIL ===,  ✗ 缺模型(紫黑格): ['event_ticket'],  ✗ 手册引用了不存在的配方: ['alana_sign', 'ame_sign', 'card_shop', 'chip_shop', 'dorothy_sign', 'event_drawer', 'jill_sign', 'kangel_sign', 'lingling_sign'],  ✗ 手册引用但 lang 缺键(玩家会看到键名): ['astral_dice.guide.category.blocks', 'astral_dice.guide.chip_source.note', 'astral_dice.guide.entry.battle_system_changes.1', 'astral_dice.guide.entry.battle_system_changes.2', 'astral_dice.guide.entry.battle_system_changes.4', 'astral_dice.guide.entry.battle_system_changes.5', 'astral_dice.guide.entry.battle_system_changes.6', 'astral_dice.guide.entry.battle_system_changes.name', 'astral_dice.guide.entry.card_shop', 'astral_dice.guide.entry.card_shop.1', 'astral_dice.guide.entry.card_trader.1', 'astral_dice.guide.entry.chip_shop.1', 'astral_dice.guide.entry.chip_trader.1', 'astral_dice.guide.entry.effect_card_barricade.1', 'astral_dice.guide.entry.effect_card_barricade.2', 'astral_dice.guide.entry.effect_card_barricade.3', 'astral_dice.guide.entry.effect_card_repulsion.1', 'astral_dice.guide.entry.effect_card_repulsion.2', 'astral_dice.guide.entry.effect_card_repulsion.3', 'astral_dice.guide.entry.effect_card_repulsion.4', 'astral_dice.guide.entry.effect_card_repulsion.5', 'astral_dice.guide.entry.event_drawer.1', 'astral_dice.guide.entry.integration.1', 'astral_dice.guide.entry.integration.2', 'astral_dice.guide.entry.integration.3', 'astral_dice.guide.entry.integration.name']
```

- 第一行 FAIL 变成 `缺模型(紫黑格): ['event_ticket']`，**点名到具体 id**，模型数从 158 降到 157。
- 说明源码扫描模式下的差集判定仍然生效，没有因为「改扫源码」而把缺模型判成通过。

恢复与校验：

| 时刻 | `models/item/event_ticket.json` 的 sha256 |
|---|---|
| 改名（= 改前的文件字节） | `b9324f2330532f945ca153d2a07bd5769719f37eab0337a3b74dc0f7e25542cc` |
| 恢复后（现状） | `b9324f2330532f945ca153d2a07bd5769719f37eab0337a3b74dc0f7e25542cc` |

字节未变（`cmp` 语义等价）。另做过一次同类反向验证：把 `fabric-1.20.1/src/main/resources/assets/astral_dice/blockstates/card_shop.json` 改名 ⇒ 报 `方块物品资源不全(blockstate=['card_shop'] / model=[] / texture=[])`，
恢复后 sha256 `228e240189d52068c15a879791dea2c272d0ec6d462f0d32261fca0104152229` 与改前相同。

---

## 5. 需要裁决的缺失项（按令只报告、未改内容）

### 5.1 fabric 侧不存在的配方（9 条）

27 个 json 共引用 9 条 `astral_dice:<id>` 配方，fabric 侧**一条都没有**：

| 配方 id | forge 侧位置 | fabric 侧 |
|---|---|---|
| `astral_dice:card_shop` | `forge-1.20.1/src/main/resources/data/astral_dice/recipes/card_shop.json` | 缺 |
| `astral_dice:chip_shop` | `forge-1.20.1/src/main/resources/data/astral_dice/recipes/chip_shop.json` | 缺 |
| `astral_dice:event_drawer` | `forge-1.20.1/src/main/resources/data/astral_dice/recipes/event_drawer.json` | 缺 |
| `astral_dice:alana_sign` | `forge-1.20.1/src/generated/resources/data/astral_dice/recipes/alana_sign.json` | 缺 |
| `astral_dice:ame_sign` | `forge-1.20.1/src/generated/resources/data/astral_dice/recipes/ame_sign.json` | 缺 |
| `astral_dice:dorothy_sign` | `forge-1.20.1/src/generated/resources/data/astral_dice/recipes/dorothy_sign.json` | 缺 |
| `astral_dice:jill_sign` | `forge-1.20.1/src/generated/resources/data/astral_dice/recipes/jill_sign.json` | 缺 |
| `astral_dice:kangel_sign` | `forge-1.20.1/src/generated/resources/data/astral_dice/recipes/kangel_sign.json` | 缺 |
| `astral_dice:lingling_sign` | `forge-1.20.1/src/generated/resources/data/astral_dice/recipes/lingling_sign.json` | 缺 |

补充事实：`deepseek_sign` 在 **forge 两侧都没有配方**（`main` 与 `generated` 均无），所以它不属于「fabric 落后于 forge」，而是两头都缺。
fabric 侧配方总数为 `src/main/resources/data/astral_dice/recipes` 14 个 + `src/generated/resources/data/astral_dice/recipes` 124 个 = 138 个，均不含上述 9 条；旧 jar 的 `data/astral_dice/recipes` 133 条同样没有。

**影响**：手册里这 9 页的 `patchouli:crafting` 页在游戏内会是空白 / 无法渲染配方（配方页依赖真实配方存在）。
**本人未改**：补配方属于数据包内容，不在本批授权范围内（且会与其它批次撞车）。

### 5.2 fabric 三语缺失的手册 lang 键（26 个，含 1 个仅 description 用到 ⇒ 实际 27 个）

本批 27 个 json 引用、而 fabric `zh_cn` / `en_us` / `ja_jp` **三语全缺**的键：

```
astral_dice.guide.category.blocks
astral_dice.guide.category.blocks.desc      （守门正则不查 description，故守门只报 26 个）
astral_dice.guide.chip_source.note
astral_dice.guide.entry.card_shop
astral_dice.guide.entry.card_shop.1
astral_dice.guide.entry.card_trader.1
astral_dice.guide.entry.chip_shop.1
astral_dice.guide.entry.chip_trader.1
astral_dice.guide.entry.event_drawer.1
astral_dice.guide.entry.effect_card_barricade.1 / .2 / .3
astral_dice.guide.entry.effect_card_repulsion.1 / .2 / .3 / .4 / .5
astral_dice.guide.entry.battle_system_changes.name / .1 / .2 / .4 / .5 / .6
astral_dice.guide.entry.integration.name / .1 / .2 / .3
```

- forge 侧这些键**齐全**（forge 三语各 1070 键，fabric 各 980 键）。
- 缺口 = 本批文件的引入成本，不是脚本问题；补 lang 会碰 `lang/*.json`，本批**明令禁止**，故只报告。
- 现状影响：游戏内这些手册页会显示原始键名（`astral_dice.guide.entry.xxx`）。

---

## 6. 自检

| 命令 / 判据 | 结果 |
|---|---|
| `python tools/verify_fabric_assets.py` | 退出码 1；3 条陈旧 jar 假 FAIL 消除，剩 2 条真实缺口（§5.1 / §5.2） |
| `python tools/check_lang_sync.py --line fabric-1.20.1` | 退出码 0（`en_us=980`） |
| `python tools/audit_patchouli_keys.py` | `引用 2000 处，悬空 96 处`；96 处**全部**落在本批 27 个文件里，其余文件 0 悬空 |
| `tools/verify_fabric_assets.py` 行尾 | 470 行 / 470 CRLF / bareLF 0 / 无 BOM |
| 27 个新增文件行尾 | 全部 CRLF、无 BOM、bareLF 0（见 §2） |
| `python -m py_compile tools/verify_fabric_assets.py` | 通过 |

`audit_patchouli_keys.py` 的 96 处悬空按文件分布（每个键 × 三语）：

| 文件 | 悬空处数 |
|---|---|
| `categories/blocks.json` | 6 |
| `blocks/card_shop.json` | 6 |
| `blocks/card_trader.json` | 3 |
| `blocks/chip_shop.json` | 3 |
| `blocks/chip_trader.json` | 3 |
| `blocks/event_drawer.json` | 3 |
| `cards_effect/effect_card_barricade.json` | 9 |
| `cards_effect/effect_card_repulsion.json` | 15 |
| `chips_charge/infinite_grid_chip.json` | 3 |
| `chips_healing/abyss_reagent_chip.json` | 3 |
| `chips_mark/sensor_chip.json` | 3 |
| `chips_other/extra_battery_chip.json` | 3 |
| `chips_starlight/flashlight_burst_chip.json` | 3 |
| `chips_starlight/fortune_stick_chip.json` | 3 |
| `getting_started/battle_system_changes.json` | 18 |
| `getting_started/integration.json` | 12 |

> 即审计结论与 §5.2 完全一致：**本批没有把任何「原本不悬空」的文件弄悬空**，悬空全部是这 27 个文件自带的引用 + fabric 缺 lang。

---

## 7. 「无法判定」清单

1. **不能确认 gradle 构建后 jar 内的资源集合**。本批禁止跑 gradle，因此「源码资源集合」与「新 jar 内资源集合」是否一致无法验证；
   只能确认源码模式下模型 158 / 贴图 155 / blockstate 4 / models/block 4 / textures/block 5 与实际文件树一致（逐个 `os.walk` 实读）。
2. **不能确认 3 条旧 FAIL 的彻底消失是「源码已补齐」还是「源码里本来就有、只是没进 jar」**：
   已确认源码里这些文件**确实存在**，但「它们为什么没进 2026-10-05 的 jar」需要 `sourceSets` 配置或一次构建才能判定，超出本批权限。
3. **不能判定 fabric 侧配方缺失是否已在其它批次计划中**。本报告只陈述「当前工作区里没有这 9 条配方」。
4. **不能判定 92 个「forge vs fabric 内容不同」的既有手册文件谁对**（见 §8）。它们的差异是历史漂移（多出/缺少 page），本批未触碰、也未核对内容正确性。
5. **不能判定 zh_cn 4 个立牌页「内容是英文」是否为刻意现状**。forge 侧就是这样，本批按令原样复制；是否要译成中文需用户裁决。
6. **未验证补丁后 jar 的实际游戏内表现**（禁止启动游戏）。

---

## 8. 观察记录（未触碰、仅供参考）

- 本批完成后 fabric 与 forge 的 patchouli 文件集合**完全对齐**：各 184 个文件，`onlyForge = 0`、`onlyFabric = 0`。
- 但其中 **92 个既有文件内容仍是两边不同**（本批 27 个之外的历史漂移；差异形如 fabric 多出 `patchouli:text` 页、或 page 数不同）。本批未改这些文件。
- `astral_guide/en_us/entries/materials/event_ticket.json` 在 fabric 侧是**未跟踪文件**且**不是本批新增**（本批清单里没有它），属他人/他批改动，本人未触碰。
- 临时文件：本次验证用到的临时目录 `undefined/temp/b20a-*`（含改写版本的临时 jar）已在使用后清理；`tools/__pycache__/` 下的 `.pyc` 由 `py_compile` 生成（该目录原本已存在他人产物）。

