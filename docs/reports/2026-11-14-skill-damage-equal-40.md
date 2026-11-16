# 技能伤害等值改造（26.4-SNAPSHOT.40）

日期：2026-11-14
范围：五线（neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / fabric-1.20.1 / fabric-1.21.1）

## 问题

垃圾桶立牌（Z3000）的拖拽伤害走 `ModDamageTypes.skillDamage(...)`，实际只 bypass 了护甲与无敌帧：

- 保护附魔照常减免；
- 抗性提升照常减免；
- `ChipDamageHandler` 的固定点数减伤（磨刀石 -2、sherry 层数 -N、kangel -1）照常生效。

用户裁决（2026-11-14）：技能伤害必须是等值伤害，无视防御值与减伤（比法伤更彻底——法伤仍吃保护附魔与抗性）。

## 改动

1. 五线 `data/minecraft/tags/damage_type/bypasses_enchantments.json` 与 `bypasses_resistance.json` 追加 `astral_dice:skill_damage`（共 10 个文件）。
2. 五线 `event/ChipDamageHandler.java` 的 `unreducible` 布尔追加 `ModDamageTypes.SKILL_DAMAGE`，跳过磨刀石、sherry、kangel 的固定点数减伤；保命机制（安全气囊、磨刀石保留 1 血、RenShield 黄心吸收）保持不变。
3. 五线 `damage/ModDamageTypes.java` 的 `SKILL_DAMAGE` / `UNREDUCIBLE_DAMAGE` / `skillDamage()` javadoc 改为等值伤害口径。
4. 五线 `item/chip/WhetstoneChipItem.java` 的不可削减枚举补 `skill_damage`。
5. 文档：`docs/features/spell-damage-chain-verification.md` 的 skill_damage 行改为「保护附魔/抗性药水 = 是」，标签清单补 skill_damage；`docs/features/attack-damage-chain-audit.md` 新增 N11a 裁决记录。

## 构建

`./gradlew :neoforge-1.21.1:build :forge-1.20.1:build :neoforge-26.1.2:build :fabric-1.20.1:build :fabric-1.21.1:build` → BUILD SUCCESSFUL in 15s。

产物 sha256：

- neoforge-1.21.1: `88a8767cefe935b79ca7c3ead039827110e387b7ee0ddaf4b37d32eb1f09224d`
- forge-1.20.1: `76bbd00e7f73c8cbf0486a20ff54e5614ac10a929e2b32e872aea5ee0b96e21c`
- neoforge-26.1.2: `483565d1dbe21772978184904e5871f9bc411995e3fba6e4c087fd36a2b7b252`
- fabric-1.20.1: `8e5ba7055d000d0e8b256364b5860614f78944c99fa59359e87d3f5284b60bf5`
- fabric-1.21.1: `1438531703e02c0f2f56e4bf26a7e2f6b4084d8ecb05c72d576c746dc60996e1`

## 实机读数

### neoforge-1.21.1（log `build/tmp-dsh/live-logs/1.21.1-20261114-142017.log`）

测试体：`minecraft:zombie`，NoAI / Silent / PersistenceRequired / NoGravity，`setHealth(getMaxHealth())` = 20。

| 条件 | 施加 | 结果 |
|---|---|---|
| 裸僵尸 | `/damage ... 5 astral_dice:skill_damage` | 20 → 15，delta **5.0** |
| 抗性提升 II | 同上 | 11.999998 → 6.999998，delta **5.0**（修前 3.0） |
| 抗性 II + 钻石胸甲 Protection IV | 普通 `minecraft:mob_attack` 10 | 20 → 16.625216，delta 3.374784 |
| 抗性 II + 钻石胸甲 Protection IV | `astral_dice:skill_damage` 5 | 20 → 15，delta **5.0**（修前 4.2） |
| 抗性 II + Protection IV | 真实 `z3000_pull` 拖拽（4 格） | 20 → 15，delta **5.0** |

胸甲组件实读：`minecraft:enchantments=>ItemEnchantments{enchantments={...minecraft:protection=>4}}`。

拖拽测试第一轮被日光灼烧污染（僵尸 `RemainingFireTicks=149`，血量额外下降）；补 `fire_resistance` 后重测，2 秒内血量稳定在 15，确认 delta 正好 5.0。

### forge-1.20.1（log `build/tmp-dsh/live-logs/1.20.1-20261114-142203.log`）

测试体同上；抗性提升 II + 钻石胸甲 Protection IV（NBT `{Enchantments:[{id:"minecraft:protection",lvl:4}]}`）+ 防火。

| 条件 | 施加 | 结果 |
|---|---|---|
| 抗性 II + Protection IV | 普通 `minecraft:mob_attack` 10 | 20 → 16.1696，delta 3.830400 |
| 抗性 II + Protection IV | `astral_dice:skill_damage` 5 | 20 → 15，delta **5.0** |
| 抗性 II + Protection IV | 真实 `z3000_pull` 拖拽（4 格） | 20 → 15，delta **5.0** |

## 结论

- 保护附魔、抗性药水、固定点数减伤对技能伤害均不再生效；技能伤害对护甲值/韧性/保护附魔/抗性提升/固定点数减伤造成等值伤害（吸收黄心与闪避类保命仍照常结算）。
- 普通伤害仍按原公式被护甲/保护/抗性削减（对照读数 10 → 3.37 / 3.83），说明测试体上的防御确实存在。
- 其余三线（neoforge-26.1.2 / fabric-1.20.1 / fabric-1.21.1）只做了同源标签与代码改动并通过构建，未实机；标签语义与 1.20.1/1.21.1 同类，风险低。

## 未覆盖

- 磨刀石 -2、sherry 层数、kangel -1 三条固定减伤未在实机单独构造受害玩家验证；判据来自 `ChipDamageHandler.java` 的 `unreducible` 分支，代码路径与 card_cost / unreducible_damage 先例一致。
- 反击层数消耗、RenShield 黄心吸收等保命机制未回归；本次未改这些分支。
- 吸收（黄心）仍先扣技能伤害：1.20.1 / 1.21.1 均无 `bypasses_absorption` 标签，金苹果等吸收效果会先吃伤害；与「保命机制保留」一致，但不是字面意义上的血量等值。
- 摩西「破绽」闪避（`DiceCombatEvents.onMosesBrokenDodge`，1.21.1 `:2141-2152`）按 causing entity 判定，可整段取消技能伤害；是否允许技能被闪避待产品确认。
- neoforge-26.1.2 的原版 `getDamageAfterMagicAbsorb` 未做本地源码取证，标签语义按同名推断。
- 五线标签与 `unreducible` 分支没有自动化门禁，后续改动可能静默回退。
