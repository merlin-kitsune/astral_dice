# 归档：卡牌合成配方（2026-10-09 移除）

用户裁决（2026-10-09）：**删除所有卡牌合成配方**，卡牌改为「通过在卡牌商店进行交易获得」（JEI 会显示获取方式）。

- 归档内容 = 移除前 `src/generated/resources/data/astral_dice/recipe(s)/` 下**22 个卡牌配方**（`attack_card_*` 6 + `defense_card_*` 3 + `effect_card_*` 13，与商店 22 张卡同集）及其对应的 `advancement(s)/recipes/misc/*` 解锁成就；两线各一份。
- 同时删除了 `datagen/ModRecipeProvider.java` 里对应的 22 段配方代码（每线 170 行），否则下次 `runData` 会把它们**重新生成**回来。
- 骰子 / 古书 / 炖菜 / 筹码 / 立牌 / 材料等**非卡牌配方未动**。
- 恢复方式：把本目录下对应线的 `recipe(s)`、`advancement(s)` 文件放回原位，并恢复 `ModRecipeProvider` 里被删的段落（见本批 git 历史）。
