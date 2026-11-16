# 归档：卡牌合成配方（2026-10-09 移除）

用户裁决（2026-10-09）：**删除所有卡牌合成配方**，卡牌改为「通过在卡牌商店进行交易获得」（JEI 会显示获取方式）。

- 归档内容 = 移除前 `src/generated/resources/data/astral_dice/recipe(s)/` 下**22 个卡牌配方**（`attack_card_*` 6 + `defense_card_*` 3 + `effect_card_*` 13，与商店 22 张卡同集）及其对应的 `advancement(s)/recipes/misc/*` 解锁成就；两线各一份。
- 同时删除了 `datagen/ModRecipeProvider.java` 里对应的 22 段配方代码（每线 170 行），否则下次 `runData` 会把它们**重新生成**回来。
- 骰子 / 古书 / 炖菜 / 筹码 / 立牌 / 材料等**非卡牌配方未动**。
- 恢复方式：把本目录下对应线的 `recipe(s)`、`advancement(s)` 文件放回原位，并恢复 `ModRecipeProvider` 里被删的段落（见本批 git 历史）。

## fabric-1.20.1（2026-11-01 补档，B34 批次）

主线提交 `a685d8d5`（2026-10-09）当时只落了 neo/forge 两线，`fabric-1.20.1` 被跳过；B34 批次把同一裁决补齐到 fabric 线，故本目录新增 `fabric-1.20.1/` 一份。

- 目录结构：与 `forge-1.20.1/` 同构（`recipes/` + `advancements/recipes/misc/`，路径 = 原 `data/astral_dice/` 下的相对路径），共 **22 配方 + 22 解锁成就 = 44 个文件**。
- `datagen/ModRecipeProvider.java` 同步删除 22 段生成代码，**170 行**（1561 → 1391 行），与另两线数值一致。
- 字节比对结论（逐文件 SHA-256）：
  - 与 `forge-1.20.1/` 存档：**42 / 44 逐字节相同**；例外 2 个 = `effect_card_monster_brick` 的 recipe 与 advancement，差异仅一处 tag 命名空间（fabric `c:bricks` / forge `forge:bricks`），属平台正确差异。
  - 与 `neoforge-1.21.1/` 存档：**44 / 44 全部不同**（1.21 用单数目录 `recipe/` + `advancement/` 且 JSON 结构已变），因此**不能**采用「与主基准逐字节相同、不重复落盘」的方案，fabric 侧 44 个文件**全部落盘**。
- 恢复方式同上方通则：把 `fabric-1.20.1/` 下的 `recipes/`、`advancements/recipes/misc/` 放回 `fabric-1.20.1/src/generated/resources/data/astral_dice/` 对应位置，并恢复该线 `ModRecipeProvider` 中被删的段落。
