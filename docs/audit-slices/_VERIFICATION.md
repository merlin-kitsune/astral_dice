# 主代理对子代理结论的独立核验记录

> 规则:**不采信未核验结论**。每条都由主代理直接读源码复核,并标注「证实 / 修正 / 证伪」。
> 基线 `d48a529`;A = `neoforge-1.21.1/.../astral_dice/`,`B = forge-1.20.1/.../astral_dice/`。

## S2(骰战/赐福攻击链)核验结果

| ID | 子代理结论 | 核验判定 | 主代理证据与修正 |
|---|---|---|---|
| S2-C1 | 骰战防御把防御方的**攻击牌**也算进防御;显示口径有过滤 | **证实(high)** | 防御修饰器 `A:DiceCombatModifiers.java:419-431`(B:`422-434`)遍历 `ctx.targetEnhancement.appliedStones()` 后 `sum += CardRegistry.roll(...)`,**无 `isDefense` 过滤**;`CardRegistry.roll` 对攻击牌(`medium/large/epic/shadow_strike/charge/full_power/meito`)走 `t.roller().roll(ctx)` 返回真实点数(`A:CardRegistry.java:100-107`),**不是返回 0** → 攻击牌确实进入 `ctx.defenseCardSum`。显示侧 `A:489-493` 有 `if (!CardRegistry.isDefense(...)) continue;`。**补充修正**:攻击侧 `A:474` 的循环同样**没有** `isDefense` 过滤(我的正则在该区间未命中 `isDefense`),即"实战=全部已装卡、GUI=按攻/防过滤"在**两侧都存在**;`CardRegistry.java:118-121` 注释自述"当前攻击点数结算包含全部已装卡",与防御侧注释声明的"仅保留战斗防御牌"(`A:415-418`)相互矛盾。 |
| S2-C2 | 默认优先级覆盖写入吞掉前置修饰器 | **证实(high),但可达场景需收窄** | 覆盖点 `A:604 event.setNewDamage((float) finalDmg)`;`finalDmg` 源自主代理自算的 `baseDamage = attackPower`(`A:488`),**完全不读 `event` 旧值** → 任何在高优先级写入的修饰都会被丢弃。**收窄**:黑曜石火焰 -70% 与"赐福近战命中"**不可能同时发生**(火焰伤害源不是近战武器攻击,`isMeleeWeaponAttack` 不成立)⇒ 火焰那条是**理论风险**而非可达缺陷;真正可达的是**末影骰子雨中/水下 ×1.4**(`A:EnderDiceHandler.java:134-143` 作用于"受伤方",与攻击方是谁无关)在**被赐福玩家近战命中时被丢弃**(PvP)。**正确定性**:这是"事件阶段修饰"与"EXTERNAL_DAMAGE_FACTORS 注册表"两套机制的口径冲突——设计上骰战接管最终值,其它系统应注册 factor 而不是改事件。 |
| S2-C4 | "骰点=6"判定读被修改后的值 | **证实(medium),行号修正** | 顺序 `A:364 baseDice=rollCombatDie()` → `A:391-399` 护法爆发 `baseDice += starBonus` → `A:402-413` 上班族(先查 FORCE_SIX→`baseDice=6`,否则 `baseDice==1` 置位)→ `A:419`(经商按 `baseDice ×2` 给星光)、`A:424`(占星师 `baseDice==6` 给 6 星币)、`A:462` 把 `baseDice` 塞进 ctx → `A:DiceCombatModifiers.java:376-377` 读 `ctx.baseDice == 6` 置 `padmanDefBypass`,后者在 `A:576` 用于跳过防御。⇒ 抬点后的 6 会触发占星师/上班族破防(证实)。**反向也证实**:`rollCombatDie` 在 `A:988-990` 已把枪匠层数的最低骰点抬到 `1+stacks`,故 `baseDice==1` 在枪匠层数≥1 时永假 ⇒ 上班族"骰出1→下次必6"无法置位。子代理原文把 `padmanDefBypass` 的赋值点写成 `A:368-382` 有误,实际在 `DiceCombatModifiers.java:376-377`。 |
| S2-C16 | 1.20.1 `removeAllEffects()` vs 1.21.1 只清图腾可治愈效果 | **证实(行为不等价),但定性需修正** | 模组侧确如所述(`B:EnderDiceHandler.java:161` `removeAllEffects()` vs `A:160` `removeEffectsCuredBy(PROTECTED_BY_TOTEM)`)。**修正**:这**不是 1.20.1 写错**,而是**两版原版本身就不同** —— 反编译源码 `LivingEntity.java`:1.20.1 **L1270 `this.removeAllEffects();`**(图腾保命分支),1.21.1 **L1329 `removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM)`**。⇒ 1.20.1 的注释「与原版图腾一致」**是对的**(对 1.20.1 的原版而言)。结论应表述为「**跨版本行为不等价(各自符合各自原版)**」:**⚠️ 二次修正(据 S3-C2 与主代理复核)**:1.20.1 的 `removeAllEffects()` **不是**无条件清空 —— 原版 `LivingEntity.removeAllEffects()`(`forge sources L903-919`)对每个效果 `post(new MobEffectEvent.Remove(...))` 且**被取消就 `continue` 跳过该效果**(L912);而本模组守卫 `ModEffectEvents.onModEffectRemovalPrevented`(HIGH,`A:110-131`/`B:106-127`)对 `astral_dice:` 命名空间效果 `setCanceled(true)`,其唯一放行口是 `isDeadOrDying()`(L117/L113)与内部/强移标记。末影骰保命**先** `setHealth(1.0F)`(L159-160)**再**清效果(L161)⇒ `isDeadOrDying()` 已为假 ⇒ **本模组效果(赐福/汲取/虚弱印记…两版本都保留)**。
⇒ 正确的差异表述:**1.20.1 会清掉玩家的全部非本模组效果(含力量/迅捷/抗性等有益原版增益),1.21.1 只清「图腾可治愈」类(即有害效果)**;两者都保留本模组效果。 |

本仓 1.20.1 同构性:骰点链顺序与 1.21.1 一致(`B:361/389/395/400-407/414-416`),S2-C1/C2/C4 **两版本皆有**成立。

## 主代理独立发现(不来自子代理)

见 `S0-host-verified.md`:S0-C1 法伤加成被无敌帧削减(high)、S0-C2 电击手套 AOE 跨版本不一致(medium-high)、S0-C3 1.20.1 火焰/法伤同事件同优先级、S0-C4 保命时死亡清理竞态(medium)、S0-C5 反击受无敌帧、S0-C6 缺 isCanceled、S0-C7 同优先级无保证、S0-C8 static 状态与关闭的闪避。

## S4 / S6 复核结论(主代理,2026-09-15 补)

| 切片结论 | 复核判定 | 说明 |
|---|---|---|
| S4-C2 星光 `spend` 口径 ⇒ 净扣 0 仍发币 | **证实** | 逐行复核 `StarLightManager:52-56,66-71` + `ResourceConversion:28-44` + `BankCardChipItem:14-17` |
| S4-C1 保命 vs 死亡清理竞态 | 竞态**证实**(与 A4 同源);玻璃骰子后果行号**未复核** | 后果链 `PlayerLifecycleHandler:126` → `DiceCurioItem:181-184`;对照气囊显式 HIGHEST `ChipDamageHandler:75` |
| S4-C6 死亡清理清单只对"死亡被取消"路径生效 | **部分证实** | 2 个 `copyOnDeath` 键(证实);清单逐项未复核 |
| S4 其余(C3/C4/C5/C7/C8/C13)与负结论 | 未核验 | 见 `S4-resources-counters.md` |
| S6-C1 Curios `onUnequip` 第 2 参绑定错误 | **证实,且切片结论需两处修正** | 参数顺序经**两加载器缓存 jar 的 LocalVariableTable 双证**;筹码 16 个 `onChipUnequip` + `tryRemoveChipBonus` 只用 `player` ⇒ 筹码/骰子侧**无害**(切片未区分);立牌侧真实后果:① misaki/jasmine/padman 的 `clearSignData` 写 item 组件却写错栈 ⇒ 卸下不清;② 传入 `EMPTY` 时 `EMPTY.set` 写**全局单例**;③ `stillInSlot` 用第 2 参 ⇒ 换装跳过整段清理 |
| S6-C2 残留 `sign_ready_expire` ⇒ 立牌主动永不冷却 | 机制**证实**;可达性**未实测**(由 blocker 降为 high) | 门槛单键 `BaseSignItem:112`(不看 type / 不看 `gameTime < expire`);残留唯一入口 = S6-C1 的"换装跳过清理";死亡会清(`PlayerLifecycleHandler:132-133`) |
| S6 其余 9 条 | 未核验 | 见 `S6-sign-active-dice.md` |

**本轮补验的平台事实(反编译源码/jar,非注释)**
- Curios 两加载器缓存 jar 的 `ICurioItem.onUnequip` LocalVariableTable 均为 `(slotContext, newStack, stack)` ⇒ 第 2 参是"将要占用槽位的栈",真正卸下时为 `EMPTY`。
- `ItemStack.EMPTY`(`ItemStack.java:271-274`)的 `components` 是**可变** `PatchedDataComponentMap`;`PatchedDataComponentMap.set`(:66-77)直接写 `patch` ⇒ 对 `EMPTY` 调 `set` 会污染全局单例。
- `ItemStack.isEmpty()`(:303-305)因 `this == EMPTY` 短路 ⇒ 上述污染暂无显性可玩后果(列为潜在)。
- 筹码 16 个 `onChipUnequip` 实现(两版本)与 `DiceCurioItem.tryRemoveChipBonus`(:120-127)**均不读取传入栈** ⇒ 该参数传错对筹码/骰子无行为后果。
- 两版本 `BaseSignItem` 的 `onUnequip`/`clearSignData` 段**逐行同构**(仅 `CuriosApi`/`CuriosCompat` 包装不同)⇒ 该缺陷两版本同时存在。
