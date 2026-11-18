# fabric-1.20.1 待接线台账（主 Agent 专用）

> 用途：各移植批次不允许改 AstralDiceMod.java / ModNetwork.java / ModClientEvents.java / mixins 配置，
> 凡是「类写好了但必须在别处登记才生效」的，一律登记到本表，由主 Agent 统一落。
> 漏登记的共同症状是「编译通过、运行无声」——每一条都必须有明确判据，不允许「大概接上了」。

## A. 已完成（归档，便于回溯）

| 项 | 落点 | 状态 |
|---|---|---|
| BeamFxRenderer 登记 | AstralDiceMod / LoaderBus | 已落 |
| CardShopCycleTicker 登记 | AstralDiceMod / LoaderBus | 已落 |
| ChipShopState 附件键静态初始化 | AstralDiceMod.onInitialize | 已落 |
| BarricadeAggroHandler 登记 | AstralDiceMod / LoaderBus | 已落 |
| RepulsionTeleportGuard 登记 | AstralDiceMod / LoaderBus | 已落 |
| CardChoiceManager 登记 | AstralDiceMod / LoaderBus | 已落 |
| CARD_SHOP / CHIP_SHOP / CARD_CHOICE 菜单类型 | screen/ModMenuTypes | 已落 |
| 四条 MenuScreens.register（卡包 / 筹码 / 卡店 / 选卡） | client/ModClientEvents | 已落 |
| 网络 id 16-19（卡牌商店四条）、20-21（选卡两条） | network/ModNetwork | 已落，现共 22 条（id 0-21） |
| REPULSION 加入 RandomCardHandler 专属牌 | item/card/RandomCardHandler | 已落 |
| 电击手套充能件数白名单（InfiniteGrid） | item/ChargeManager | 已落 |
| 电击手套重设计（executeShock / 20t 延迟 / 60s 冷却 / 每目标 2 点法伤） | item/chip/ElectricGloveChipItem | 已落（含 combat/SpellDamageRegistry.collectGloveShockTargets、DamageCardFxScheduler.applySpellDamageAmount 升 public + latestGloveTarget、event/RailgunStrikeScheduler 六参 Action 重载） |
| GlobalEventGate.reset / EventAnimationLock.reset 停服钩子 | platform/FabricBridges（SERVER_STOPPING） | 已落 |

## B. 待落（主 Agent 欠账）

| 项 | 落点 | 判据（怎么证明真的生效） | 状态 |
|---|---|---|---|
| EventAnimationLock 登记 | AstralDiceMod.registerListeners → LoaderBus | 死人/登出后事件锁被释放；LoaderBus.dispatchReport() 未派发段为空 | 待落（等立牌批次交出 AstralDiceMod） |
| 事件服务端核心各类的 LoaderBus 登记（EventEffects / EventChoiceManager / CurioDeathKeepHandler 等） | 同上 | 以 B2-CORE-REPORT.md 给出的清单为准 | 待 B2-CORE 报告 |
| 事件段网络 id 22-27（EventChoiceStart / Confirm / Close / Tick / Cancel + EventCard） | network/ModNetwork 末尾追加 | 顺序与 forge 16/17/18/19/20/27 的相对顺序一致；SimpleChannel.encode 不再抛 unregistered message type | 待客户端批次 |
| EventCardOverlay 注册 | client/ModClientEvents.onClientSetup → registerAbove(CROSSHAIR, "event_card", …) | 事件卡能弹出；机器行 AP_EVENT_CARD / AP_EVENT_CARD_CLIENT 两侧都出现 | 待客户端批次 |
| 事件抽屉两条附件键 event_drawer_cooldown_until / event_drawer_blank_insert_at | component/ModAttachments | 抽奖盒冷却真的生效（否则恒为 0） | 待事件物品批次 |
| FinalizeSpawn mixin 桥（MobSpawnEvent 等价物） | mixin/bridge + aes 配置 | 「雷暴将至」夜间刷怪 ×2 生效；无桥时该段必须显式 TODO 而非静默 | 待桥接批次（已裁决要做） |
| 爆炸事件桥（供 RepulsionCreeperGuard.handleExplosionStart 调用） | mixin/bridge | 苦力怕爆炸被拒止时 swellDir=-1 且火被扑灭 | 待桥接批次 |
| 末影人瞬移事件桥（EntityTeleportEvent.EnderEntity 等价物） | mixin/bridge | 末影人无法穿过拒止区域 | 待桥接批次 |
| compat/jei 8 个类 | compat/jei/** | Loom 升级后 JEI 进编译期依赖，再按 forge 移植 | 待 Loom 批次交付后 |

## B2. 越界改动裁决（已批准，勿回滚）

- `event/RailgunStrikeScheduler` 的 `StrikeAction` 泛化（6 参重载 + float 重载包成动作）在立牌批次的分区里，但它是电击手套编译的前提（MAPPING-1.5.0.md:503 本就列为缺口 #41）。用户裁决：保留，不回滚、不另立批次。
- `combat/DamageCardFxScheduler.latestGloveTarget`（peek）超出「只改可见性」字面范围，同属编译必需，保留。
## B3. 事件服务端核心（B2-CORE 批次）的接线欠账

| 项 | 落点 | 判据 | 状态 |
|---|---|---|---|
| LoaderBus 登记 EventEffects | AstralDiceMod.registerListeners | 缺 ⇒ 弹卡后永不结算 | 待落 |
| LoaderBus 登记 EventChoiceManager | 同上 | 缺 ⇒ 15s 倒计时不推进、死亡/登出不强制结算 | 待落 |
| LoaderBus 登记 CurioDeathKeepHandler | 同上 | 缺 ⇒ 饰品死亡照常掉落 | 待落 |
| 网络 22-27（EventCard / Start / Confirm / Close / Tick / Cancel） | network/ModNetwork.register | 缺 ⇒ EventCardService 与 EventChoiceManager 编不过（硬错） | 待网络批次 |
| Mob#finalizeSpawn TAIL 桥（NATURAL 且未 isSpawnCancelled） | mixin/bridge + aes 配置 | 缺 ⇒ 雷暴将至夜间刷怪 ×2 静默失效；注入点写在 EventEffects.java:359-397 TODO-BRIDGE | 待桥接批次 |

说明：`InvestigationArbitration` 原本 fabric 不存在、被 Registry/Dispatcher/ChoiceManager 三处引用，已由 B2-CORE 补（120 行）。全仓只此一份，后续批次勿重写。

## B4. 防御力折算 P1 欠账（待开批，与 1:1 要求直接冲突）

现状（实读）：fabric 仍在用旧的 ×2.0 折算 —— `item/sign/DorothySignItem.java:20 ARMOR_PER_DEFENSE = 2.0`、`item/sign/SignAttributeUtil.java:74 ARMOR_PER_DEFENSE = 2.0D`、`event/EventEffects.java:74 MOB_ARMOR_PER_DEFENSE = 2.0D`；
而 P0 两线的单一真值是 `DiceCombatModifiers.PLAYER_ARMOR_PER_DEFENSE = 1.0 / CombatFormula.PLAYER_ARMOR_COEF`（≈3.3333）与 `MOB_ARMOR_PER_DEFENSE = 1.0 / MOB_ARMOR_COEF`（= 2.5）。fabric 的 `combat/DiceCombatModifiers` 里这两个常量**根本不存在**。
影响：文案写「防御力 +X」，玩家实测只兑现约 0.6X（玩家侧）与 0.8X（生物侧）—— 属 AGENTS-full.md〈平衡性红线〉里的恶性 BUG。
待办：把 `DiceCombatModifiers` 的两个常量 + `armorValueForDefensePoints` 双载重 + `effectiveArmorPoints` 一并补进 fabric，改所有消费点（DorothySignItem / JillSignItem / MotoHelmetChipItem / ObsidianDiceItem / SignAttributeUtil / EventEffects），再按 D6 改 12 条指南文案（含 3.33 与 2.5 两个数），最后 `python tools/verify_defense_unit_parity.py` 的 D1..D9 全绿（D8 不再是「只统计」）。
**注意：这项要动 item/sign/**，必须等立牌批次全部结束再做。**
## B5. 立牌（B4-SIGNS）遗留与裁决

| 项 | 结论 / 待办 |
|---|---|
| `component/SignAttachments.java`（新增 386 行） | 立牌 7 个玩家级键暂居此处（ModAttachments 属禁改）。**收尾批次必须并回 ModAttachments** 并删本类，否则玩家数据键有两个事实源。 |
| `SignState` / `SignTimerSourceAdapter` 无消费者 | 保留（前向兼容）。若最终确认 forge 侧也无人用，再删。 |
| `SignActiveLockMachine` 不建独立类 | 接受：fabric `BaseSignItem` 已内联该状态机，第二份事实源风险更大。 |
| **`MobEffectEvent.Applicable` 在 fabric 全仓无派发点** ⇒ 「免疫 HARMFUL 效果」立牌功能缺失 | **真功能缺口**，待桥接批次补：mixin `LivingEntity#canBeAffected` 返回 false 时派发等价事件，再让 NewSignEvents 订阅。不补则该立牌静默无效。 |
| `BaseSignItem` 三处滞后（setSignActiveMaxCooldownBase / WeirdDiceHandler.onSignActiveEnded / AP_SIGN_SOUND 机器行） | 已派 B4-FOLLOWUP 批次补齐（只动这一个文件）。 |
| Patchouli 立牌手册条目未搬 | 待手册批次（forge 只有 en_us 7 条 / zh_cn 4 条，lang 键已先落，多余无害）。 |
| `ModCreativeTabs` 既有 RARE/EPIC 分组标题与实物品质不符（HANNA/MEGAS 在 forge 是奇特） | 待核：需与 forge 的创造栏分组逐条比对后统一，不擅自重排。 |
| `tools/verify_sign_display_names.py` 只覆盖 neo+forge，**不检 fabric** | 守门缺口，收尾时把 fabric 加进 LINES（同 verify_effect_icons.py 的做法）。 |
## C. 有意保留的差异（不是缺陷，别当 bug 修）

- 饰品槽位 validator：fabric 比 forge 多一道 curios:<slot> 标签校验。用户 2026-11-01 裁决「保持现状」。自家物品全在标签里，行为一致；第三方 ICurioItem 物品在 fabric 会被拒。
- REPULSION 物品不进创造栏与 JEI：与 forge 裁决一致。
- AstralEventIds / event/api 五类与 forge sha256 全等，不要为了「平台化」去改它们。
- EventTimerStore.clear 上游本就没有调用点（登出泄漏是蓝本既存），不擅自加钩子。