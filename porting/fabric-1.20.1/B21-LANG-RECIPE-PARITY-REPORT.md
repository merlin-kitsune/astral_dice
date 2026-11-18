# B21 — fabric-1.20.1 语言缺口 + 掉落/合成配方缺口 对齐报告

- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-main`
- 基准（蓝本）：`forge-1.20.1`
- 目标：`fabric-1.20.1`
- 批次纪律：只改 3 个 lang 文件 + 新增 9 个配方文件；未动任何 .java、未动 `tools/**`、未动 `patchouli_books/**`；未 commit / push / stash / add / checkout；未跑 gradle、未启动游戏。
- 结论：**全部缺口已补齐**，`verify_fabric_assets.py` 两条 FAIL 均已消除（脚本退出码 0）。**有 1 项需裁决（行尾：6 个 generated 配方按蓝本为 LF）**，详见第四节。

---

## 一、语言键补齐

### 1.1 三语新增键数

| 语言 | 编辑前 | 新增 | 编辑后 | forge（蓝本） | set(forge)-set(fabric) |
|---|---|---|---|---|---|
| zh_cn | 980 | **115** | **1095** | 1070 | 空集 |
| en_us | 980 | **115** | **1095** | 1070 | 空集 |
| ja_jp | 980 | **115** | **1095** | 1070 | 空集 |

- 三语缺键**恰好是同一批 115 个键**（逐语言独立计算后比对：键集相同、且在 forge 文件中的**相对顺序也完全相同**）。
- 追加方式：按 **forge 文件内的原始相对顺序**，逐条追加到 fabric 对应语言文件**末尾**（`}` 之前）；未按字母序重排，未插入中间。
- 值**逐字照搬**：直接取蓝本行中「键名闭合引号之后」的全部字节（含 `§` 颜色码、`%%` 字面百分号、全角标点、`\n` 转义），未重新序列化、未 `json.dump` 整写。
- **既有 980 键一条未改**（脚本断言 `changed-old-values: []`，且写前后做 dict 逐键比对）。
- 因追加在末位，原最后一条键行（`jei.astral_dice.chip_shop.price`）补上了逗号；新末位键行不带逗号（与文件既有风格一致：末位无尾随逗号）。

### 1.2 逐条键名清单（按前缀分组，共 115 条；三语同一清单）

**`astral_dice.guide.*`（27 条）**

```
astral_dice.guide.category.blocks
astral_dice.guide.category.blocks.desc
astral_dice.guide.chip_source.note
astral_dice.guide.entry.battle_system_changes.name
astral_dice.guide.entry.battle_system_changes.1
astral_dice.guide.entry.battle_system_changes.2
astral_dice.guide.entry.battle_system_changes.4
astral_dice.guide.entry.battle_system_changes.5
astral_dice.guide.entry.battle_system_changes.6
astral_dice.guide.entry.card_shop
astral_dice.guide.entry.card_shop.1
astral_dice.guide.entry.card_trader.1
astral_dice.guide.entry.chip_shop.1
astral_dice.guide.entry.chip_trader.1
astral_dice.guide.entry.event_drawer.1
astral_dice.guide.entry.effect_card_barricade.1
astral_dice.guide.entry.effect_card_barricade.2
astral_dice.guide.entry.effect_card_barricade.3
astral_dice.guide.entry.effect_card_repulsion.1
astral_dice.guide.entry.effect_card_repulsion.2
astral_dice.guide.entry.effect_card_repulsion.3
astral_dice.guide.entry.effect_card_repulsion.4
astral_dice.guide.entry.effect_card_repulsion.5
astral_dice.guide.entry.integration.1
astral_dice.guide.entry.integration.2
astral_dice.guide.entry.integration.3
astral_dice.guide.entry.integration.name
```

**`tooltip.astral_dice.*`（31 条）**

```
tooltip.astral_dice.card.fate_curse_mitigation
tooltip.astral_dice.card.fate_spell_mana
tooltip.astral_dice.chip.cursed_sword_enigmatic
tooltip.astral_dice.chip.flashlight_burst
tooltip.astral_dice.chip.fortune_stick
tooltip.astral_dice.chip.abyss_reagent
tooltip.astral_dice.chip.infinite_grid
tooltip.astral_dice.chip.sensor
tooltip.astral_dice.chip.extra_battery
tooltip.astral_dice.weird_dice_heal
tooltip.astral_dice.sign.misaki_enigmatic
tooltip.astral_dice.sign.moses_enigmatic
tooltip.astral_dice.sign.kangel_active
tooltip.astral_dice.sign.kangel_passive
tooltip.astral_dice.sign.kangel_note
tooltip.astral_dice.sign.kangel_fans
tooltip.astral_dice.sign.ame_active
tooltip.astral_dice.sign.ame_passive
tooltip.astral_dice.sign.ame_note
tooltip.astral_dice.sign.ame_love_layers
tooltip.astral_dice.sign.lingling_active
tooltip.astral_dice.sign.lingling_passive
tooltip.astral_dice.sign.lingling_note
tooltip.astral_dice.sign.jill_active
tooltip.astral_dice.sign.jill_passive
tooltip.astral_dice.sign.dorothy_active
tooltip.astral_dice.sign.dorothy_passive
tooltip.astral_dice.sign.alana_active
tooltip.astral_dice.sign.alana_passive
tooltip.astral_dice.sign.deepseek_active
tooltip.astral_dice.sign.deepseek_passive
```

**`msg.astral_dice.*`（34 条）**

```
msg.astral_dice.blue_curse_blocked
msg.astral_dice.kangel_active
msg.astral_dice.kangel_active_heal
msg.astral_dice.kangel_active_ally
msg.astral_dice.kangel_active_full
msg.astral_dice.ame_overdose
msg.astral_dice.ame_overdose_expire
msg.astral_dice.lingling_active
msg.astral_dice.chip_shop_no_coin
msg.astral_dice.chip_shop_no_refresh
msg.astral_dice.chip_shop_bought
msg.astral_dice.chip_shop_chip_dropped
msg.astral_dice.chip_shop_owned_all
msg.astral_dice.chip_shop_inventory_full
msg.astral_dice.chip_shop_cooldown
msg.astral_dice.event_global_cooldown
msg.astral_dice.card_shop_bought
msg.astral_dice.card_shop_exchange_done
msg.astral_dice.card_shop_transfer_cooling
msg.astral_dice.card_shop_transfer_target_lost
msg.astral_dice.card_shop_transfer_done
msg.astral_dice.card_shop_transfer_received
msg.astral_dice.card_shop_no_starlight
msg.astral_dice.card_shop_parunan_enter
msg.astral_dice.jill_ready
msg.astral_dice.jill_not_enough_cards
msg.astral_dice.jill_done
msg.astral_dice.dorothy_ready
msg.astral_dice.dorothy_no_spot
msg.astral_dice.dorothy_done
msg.astral_dice.alana_done
msg.astral_dice.deepseek_done
msg.astral_dice.deepseek_no_coins
msg.astral_dice.event_animating
```

**`event.astral_dice.*`（13 条）**

```
event.astral_dice.red_heat_warning.name
event.astral_dice.swift_stride.name
event.astral_dice.storm_approaching.name
event.astral_dice.learn_from_others.name
event.astral_dice.shop_bonanza.name
event.astral_dice.hand_erasure.name
event.astral_dice.world_collapse.name
event.astral_dice.gift_from_sky.name
event.astral_dice.angel_descent.name
event.astral_dice.problem_delivery.name
event.astral_dice.crowded_passage.name
event.astral_dice.battle_frenzy.name
event.astral_dice.investigation_stage.name
```

**其他模组命名空间 + 杂项键（10 条，按既定裁决照搬，不删不改）**

```
curios.modifiers.chip
curios.modifiers.dice
curios.modifiers.stand
bountiful.decree.astral.name
config.jade.plugin_astral_dice.shop_cooldown
config.jade.plugin_astral_dice.event_drawer_cost
command.astral_dice.astralparty.killbarricade.success
command.astral_dice.astralparty.killrepulsion.success
gui.astral_dice.card_shop.cooldown
hud.astral_dice.target_select.tag.undercover
```

> 分组计数：27 + 31 + 34 + 13 + 10 = **115**。

### 1.3 fabric 独有键清单（`set(fabric) - set(forge)`，三语相同，共 25 条）

```
accessories.slot.chip
accessories.slot.dice
accessories.slot.stand
accessories.slot_group.astral_dice
trinkets.slot.astral_dice.chip
trinkets.slot.astral_dice.dice
trinkets.slot.astral_dice.stand
msg.astral_dice.fanny_event
msg.astral_dice.fanny_event.1
msg.astral_dice.fanny_event.2
msg.astral_dice.fanny_event.3
msg.astral_dice.fanny_event.4
msg.astral_dice.fanny_event.5
msg.astral_dice.fanny_event.6
msg.astral_dice.fanny_event.7
msg.astral_dice.fanny_event.8
msg.astral_dice.fanny_event.9
msg.astral_dice.fanny_event.10
msg.astral_dice.fanny_event.11
msg.astral_dice.parunan_active_no_starlight
tooltip.astral_dice.weird_dice_cooldown
astral_dice.guide.entry.hanna_sign.3
astral_dice.guide.entry.nardis_sign.3
astral_dice.guide.entry.sherry_sign.3
astral_dice.guide.entry.teru_sign.3
```

说明：这 25 条是 fabric 本线特有的**平台差异键**（`accessories.*` / `trinkets.slot.*` 是 Fabr​ic 侧饰品槽命名空间，对应 forge 的 `curios.*`）与本线新增内容（`fanny_event` 系列、`weird_dice_cooldown`、`parunan_active_no_starlight`、4 条 `*_sign.3` 手册页）。**未删除、未改写**，故最终键数 1095 > forge 的 1070，符合任务书「若 fabric 原有独有键导致 >1070，如实报告差集」。

### 1.4 语言文件指纹

| 文件 | 编辑前 sha256 | 编辑后 sha256 | 字节数（前 → 后） |
|---|---|---|---|
| zh_cn.json | 6e7a96265197b7adfd5a1df5bdb256a01008c51bc5f4906931f21e01251f41e6 | 02848dd2868e0cce1d135702d100ab75e08234ce2fd8fe85813d92cb01a6c10f | 128791 → 146881 |
| en_us.json | 578a57998b6e059aa6175da26a819eadc308a622a0f8b8070557fa7b4d7beb28 | d0868cdad62a29ad714d44d5f9a2067eb5d0aa9f55cc599d0afa3119ef97466b | 132666 → 152014 |
| ja_jp.json | 14340713d2da3fe89940ec2f1e71bf28e7cbd8d7fd8e59ab97341e344d12e85c | 3429af4e677d6003948a8530689f2b9982ea518f8215b7f04de8aa3beea987d3 | 164561 → 187493 |

三语自检：**bareLF = 0 / UTF-8 无 BOM / 结尾 CRLF / json.loads 通过**。

---

## 二、配方缺口 9 个

### 2.1 落位与 sha256 对照表

蓝本侧分两个层级（与任务书一致：「若某文件在 forge 侧位于 main/resources，则同样放到 fabric 的 main/resources 对应位置」）：

| 文件 | 层级（蓝本 → 目标） | 字节 | CRLF/bareLF | sha256（蓝本） | sha256（fabric） | 相等 |
|---|---|---|---|---|---|---|
| alana_sign.json | forge generated → fabric generated | 542 | 0/32 | 8a8d7442d3656b4b772292285a0c2d3f9592fe522a9b70677ec10f2fc2120806 | 8a8d7442d3656b4b772292285a0c2d3f9592fe522a9b70677ec10f2fc2120806 | ✅ |
| ame_sign.json | forge generated → fabric generated | 552 | 0/32 | 52e9033120d409fdbb115b45a26d3fddf47e5ec7d7fb2a013d5293ae4351ecab | 52e9033120d409fdbb115b45a26d3fddf47e5ec7d7fb2a013d5293ae4351ecab | ✅ |
| dorothy_sign.json | forge generated → fabric generated | 572 | 0/32 | 7f08dddf35f34e729e6ed5cc2ae07176bbc43a6d2f91b6c192a1e0a3715978d3 | 7f08dddf35f34e729e6ed5cc2ae07176bbc43a6d2f91b6c192a1e0a3715978d3 | ✅ |
| jill_sign.json | forge generated → fabric generated | 552 | 0/32 | 1a28f4f09ef82e08e90c4a0654106e8e92673e7f390d4ab64ef0f4bb57eaf966 | 1a28f4f09ef82e08e90c4a0654106e8e92673e7f390d4ab64ef0f4bb57eaf966 | ✅ |
| kangel_sign.json | forge generated → fabric generated | 565 | 0/32 | e9545d0681b315d3a5c61bc8d31e35650d696599594a35bcea9617609381ac73 | e9545d0681b315d3a5c61bc8d31e35650d696599594a35bcea9617609381ac73 | ✅ |
| lingling_sign.json | forge generated → fabric generated | 455 | 0/26 | b1f008c47c8e1d0cf0f831203893583eedda3eb50e8e850e58d2494990d7d157 | b1f008c47c8e1d0cf0f831203893583eedda3eb50e8e850e58d2494990d7d157 | ✅ |
| card_shop.json | forge main → fabric main | 392 | 24/0 | ac852faf926a4d01cd2170ae7e0bcc9005aabac778f317a71e2c7bc942958d55 | ac852faf926a4d01cd2170ae7e0bcc9005aabac778f317a71e2c7bc942958d55 | ✅ |
| chip_shop.json | forge main → fabric main | 469 | 27/0 | a67f83487d47405b3afdcf6f19b19704edeb4abcee94634f76e195ab026289da | a67f83487d47405b3afdcf6f19b19704edeb4abcee94634f76e195ab026289da | ✅ |
| event_drawer.json | forge main → fabric main | 401 | 24/0 | 91d852f3e8a68785ae03315e7ecc81acbb92cebc48a76a48d7163f17d4a781b9 | 91d852f3e8a68785ae03315e7ecc81acbb92cebc48a76a48d7163f17d4a781b9 | ✅ |

- 复制方式：**二进制读写**（`open(src,"rb").read()` → `open(dst,"wb").write()`），未经过任何文本转换，**9/9 sha256 与蓝本相等**。
- 缺口核对方式：`(forge main ∪ forge generated)  (fabric main ∪ fabric generated)` = 恰好这 9 个，无第 10 个。
- 9 个文件 `json.loads` 全部通过，均无 BOM。

### 2.2 配方引用的 item/block id 注册核对（`fabric-1.20.1/.../item/ModItems.java` + `block/ModBlocks.java`）

**全部已注册，无需裁决。** 逐个 id 的注册行（fabric 侧实读）：

| recipe | 引用的 astral_dice id | fabric 注册位置 |
|---|---|---|
| alana_sign | golden_dice / blank_sign / alana_sign | ModItems.java:166 / ModItems.java:416 / ModItems.java:1159 |
| ame_sign | amethyst_dice / blank_sign / golden_star_plate / ame_sign | ModItems.java:220 / 416 / 542 / 1135 |
| dorothy_sign | amethyst_dice / blank_sign / regeneration_reagent / golden_star_plate / dorothy_sign | ModItems.java:220 / 416 / 549 / 542 / 1153 |
| jill_sign | amethyst_dice / blank_sign / golden_star_plate / jill_sign | ModItems.java:220 / 416 / 542 / 1147 |
| kangel_sign | amethyst_dice / blank_sign / golden_star_plate / kangel_sign | ModItems.java:220 / 416 / 542 / 1129 |
| lingling_sign | conductive_wire / star_plate / diamond_dice / lingling_sign | ModItems.java:554 / 537 / 178 / 1141 |
| card_shop | star_coin / star_plate（`minecraft:logs` 标签） / card_shop | ModItems.java:405 / 537 ；ModItems.java:1082 + ModBlocks.java:45 |
| chip_shop | star_plate / blank_chip / chip_shop | ModItems.java:537 / 466 ；ModItems.java:1086 + ModBlocks.java:53 |
| event_drawer | star_plate / star_coin / diamond_dice / event_drawer | ModItems.java:537 / 405 / 178 ；ModItems.java:1096 + ModBlocks.java:61 |

未发现 fabric 未注册的 id，**未修改任何配方内容**。

---

## 三、验收原始输出

### a) `python tools/check_lang_sync.py --line fabric-1.20.1`

```
[OK]   fabric-1.20.1: en_us=1095 键，3 份语言键集一致
[exit=0]
```

三语键集一致；键数 **1095**（= fabric 原 980 + 新增 115）。与 forge 的 1070 不等，原因是 fabric 原有 25 个独有键（见 1.3 节），**差集已如实列出**，无缺失。

### b) 内联断言：`set(forge) - set(fabric) == 空集` + fabric 独有键清单

```
[zh_cn] forge= 1070  fabric= 1095  set(forge)-set(fabric)== True (n=0)
        set(fabric)-set(forge) n=25
        fabric-only keys:
          accessories.slot.chip
          accessories.slot.dice
          accessories.slot.stand
          accessories.slot_group.astral_dice
          astral_dice.guide.entry.hanna_sign.3
          astral_dice.guide.entry.nardis_sign.3
          astral_dice.guide.entry.sherry_sign.3
          astral_dice.guide.entry.teru_sign.3
          msg.astral_dice.fanny_event
          msg.astral_dice.fanny_event.1
          msg.astral_dice.fanny_event.10
          msg.astral_dice.fanny_event.11
          msg.astral_dice.fanny_event.2
          msg.astral_dice.fanny_event.3
          msg.astral_dice.fanny_event.4
          msg.astral_dice.fanny_event.5
          msg.astral_dice.fanny_event.6
          msg.astral_dice.fanny_event.7
          msg.astral_dice.fanny_event.8
          msg.astral_dice.fanny_event.9
          msg.astral_dice.parunan_active_no_starlight
          tooltip.astral_dice.weird_dice_cooldown
          trinkets.slot.astral_dice.chip
          trinkets.slot.astral_dice.dice
          trinkets.slot.astral_dice.stand
[en_us] forge= 1070  fabric= 1095  set(forge)-set(fabric)== True (n=0)
        set(fabric)-set(forge) n=25
[ja_jp] forge= 1070  fabric= 1095  set(forge)-set(fabric)== True (n=0)
        set(fabric)-set(forge) n=25
ALL THREE LANGUAGES: forge subset of fabric -> True
```

### c) `python tools/verify_fabric_assets.py`

先确认脚本已是「改后版本」——输出里出现 `[!] build/libs 内 jar 版本 1.3.7-alpha.2+fabric_1.20.1 与 mod_version 1.5.0-rc.7+fabric_1.20.1 不符，改为扫描源码资源目录`，即「jar 版本与 mod_version 不符则扫源码」那一行提示，**说明守门脚本已被另一批次改过，本次跑的是改后版本**。

```
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar

[!] build/libs 内 jar 版本 1.3.7-alpha.2+fabric_1.20.1 与 mod_version 1.5.0-rc.7+fabric_1.20.1 不符，改为扫描源码资源目录
[1] 物品闭环: 注册 157 (方块物品 3) / 模型 158 / 贴图 155 (方块贴图另计)
[2] 标签闭环: 引用 11 / 自建 29 / 有提供者 11
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
[4] 粒子闭环: 注册 2 / particles/*.json 2
[5] 语言闭环: zh_cn 1095 / en_us 1095 / ja_jp 1095
[6] 手册闭环: 条目 184 / 引用配方 147
[7] 创意标签覆盖: 156/157
    已知与基线一致（不阻塞）: ['repulsion']
[8] 饰品槽位图标: 检查 7 条 icon

=== PASS: 8 项闭环全部通过 ===
[exit=0]
```

对比改前基线（同一脚本、同一环境）：

```
=== FAIL ===
  ✗ 手册引用了不存在的配方: ['alana_sign', 'ame_sign', 'card_shop', 'chip_shop', 'dorothy_sign', 'event_drawer', 'jill_sign', 'kangel_sign', 'lingling_sign']
  ✗ 手册引用但 lang 缺键(玩家会看到键名): ['astral_dice.guide.category.blocks', ... 共 26 条 ...]
[5] 语言闭环: zh_cn 980 / en_us 980 / ja_jp 980
[6] 手册闭环: 条目 184 / 引用配方 138
```

**两条 FAIL 均已消除**，脚本现为 PASS（退出码 0），无残留 FAIL。附带变化：`引用配方 138 → 147`（新增 9 个）、`标签闭环 引用/有提供者 10 → 11`（`card_shop`/`chip_shop`/`event_drawer` 三条配方引用 `minecraft:logs` 标签）、`语言闭环 980 → 1095`。

### d) 9 个 recipe 的 sha256 对照表

见 2.1 节表格：**9/9 全部相等**。

---

## 四、需裁决 / 无法判定清单

### ⚠ 4.1 需裁决：6 个 generated 配方的行尾是 LF，与「全部 CRLF + bareLF=0」冲突

- 事实：蓝本 `forge-1.20.1/src/generated/resources/data/astral_dice/recipes/*.json` 的 6 个立牌配方本身就是 **LF**（该目录 108 个文件中 107 个 LF，只有 `ren_sign.json` 是 CRLF）；仓库 `.gitattributes` 明确写了 `src/generated/**/*.json text eol=lf`，注释为「generated files, they always generate with LF」。
- 冲突点：任务书同时要求「**逐字节复制，sha256 必须相等**」与「**全部 CRLF + 无 BOM，bareLF = 0**」。对 LF 蓝本，这两条不可同时满足。
- 本次处理：**优先满足 sha256 逐字节相等**（9/9 相等），因此这 6 个文件落盘为 LF（bareLF = 32/32/32/32/32/26）；3 个 main/resources 配方（card_shop / chip_shop / event_drawer）蓝本即 CRLF，落盘 CRLF、bareLF = 0。
- 如果你要的是「6 个文件强制转 CRLF」，转换后的 sha256 会变成（与蓝本不再相等）：

```
alana_sign.json    f1e33a7fc3c99492bcaea84eaf9e704cc874aaa154080d09f55502f85204da03
ame_sign.json      203e147096b24fc98184f7628db74d94513044503a00015a8ab7a3606d68905c
dorothy_sign.json  64faa01ea048ed4c2cc7fbed77b74714be2bc68d3c499324591fb22fc642dcb2
jill_sign.json     68c9738ed96a324590330e25cec19a3446ce1c6fe9a5694ff6b202306d1e7989
kangel_sign.json   b327e8b10a5752a3fb571d85199554a68afe788e301b98c602103b20fb100657
lingling_sign.json 4fc1f3907aed289b6f9b8459f32238f8f5f672ad01e91ef97b25d23c86662775
```

> 补充：`fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/` 现有 124 个工作区文件**全部是 CRLF**（与 `.gitattributes` 的 `eol=lf` 相反，但因 git 读取时会做 CRLF→LF 归一化，`git status` 并不报它们为脏）。所以本目录在工作区层面的实际观感是 CRLF，本次新增的 6 个 LF 文件在这一点上与该目录其余文件**不一致**。该不一致是否保留，请裁决（保 sha256 相等 = 保留 LF；保目录行尾统一 = 转 CRLF 并放弃 sha256 相等）。

### 4.2 无需裁决、但需知悉（既有缺陷，本次未动）

1. **fabric 语言文件有 2 个重复键**：`effect.astral_dice.concealment`（第 229 行 / 第 860 行）与 `effect.astral_dice.concealment.description`（第 228 行 / 第 861 行），两处取值相同。这是编辑前既有状态，`json.loads` 取后者、键数不受影响。按「已存在的键不要覆盖、只补缺」的要求**未动**；如需清理请另开批次（属结构改动）。
2. **forge 蓝本有 6 处缩进为 0 的键行**（`zh_cn.json` 第 297 / 354 / 474 / 475 / 539 / 990 行，均为 barricade 相关键）。属蓝本既有格式瑕疵，且这些键 fabric 侧**本来就有**（不在 115 缺口内），故本次追加块不受影响；新增行统一 2 空格缩进。
3. **既有键的跨线取值漂移**：fabric 与 forge 共有的键中，取值不同的有 zh_cn **112** 条 / en_us **158** 条 / ja_jp **104** 条（fabric 本线特有措辞）。按任务书「已存在的键不要覆盖」**全部保留**，未做任何回改，也未列入缺陷（需另批逐键裁决）。
4. **配方目录另有非本批差异（未触碰）**：fabric 侧有 22 个 forge 没有的配方（`attack_card_*` / `defense_card_*` / `effect_card_*` 等）；`fabric-1.20.1/src/generated/.../recipes/` 另有 6 个未跟踪新增文件（`abyss_reagent_chip` / `extra_battery_chip` / `flashlight_burst_chip` / `fortune_stick_chip` / `infinite_grid_chip` / `sensor_chip`），均为其他批次产物，本次未读写。

### 4.3 无法判定项

无。9 个配方引用的 19 个 `astral_dice:*` id 全部在 `ModItems.java` / `ModBlocks.java` 中找到注册行；三语键缺口集合逐语言计算后完全一致；两条守门 FAIL 的消除均以脚本退出码 0 与原始输出为证。

---

## 五、纪律自查

| 项 | 结果 |
|---|---|
| 改动文件 = 3 个 lang + 9 个新增 recipe | ✅（`git status` 见下） |
| 未改 .java | ✅ |
| 未改 `tools/**` | ✅（仅执行，未写） |
| 未改 `patchouli_books/**` | ✅ |
| 未 commit / push / stash / add / checkout | ✅ |
| 未跑 gradle、未启动游戏 | ✅ |
| lang 三语：CRLF / 无 BOM / bareLF=0 / json.loads 通过 | ✅ |
| recipe 9 个：sha256 与蓝本 9/9 相等 / json.loads 通过 | ✅（行尾见 4.1） |

`git status`（限定本批关注目录）：

```
 M fabric-1.20.1/src/main/resources/assets/astral_dice/lang/en_us.json
 M fabric-1.20.1/src/main/resources/assets/astral_dice/lang/ja_jp.json
 M fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json
?? fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/alana_sign.json
?? fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/ame_sign.json
?? fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/dorothy_sign.json
?? fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/jill_sign.json
?? fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/kangel_sign.json
?? fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/lingling_sign.json
?? fabric-1.20.1/src/main/resources/data/astral_dice/recipes/card_shop.json
?? fabric-1.20.1/src/main/resources/data/astral_dice/recipes/chip_shop.json
?? fabric-1.20.1/src/main/resources/data/astral_dice/recipes/event_drawer.json
```

> 同一 `git status` 里另有 6 个未跟踪的 `*_chip.json` 文件（abyss_reagent_chip / extra_battery_chip / flashlight_burst_chip / fortune_stick_chip / infinite_grid_chip / sensor_chip），属其他批次产物，本次未读写，见 4.2 第 4 条。

（`forge-1.20.1` 侧 `git status` 为空，蓝本未被改动。）
