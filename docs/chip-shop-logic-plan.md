# 筹码商店买卖逻辑 · 设计计划表 v2（已按裁决更新 · 待开工确认）

> **状态：设计已定稿冻结，暂不开工**（2026-10-12 用户原话：「不需要画标题，先纪录，暂时不开工」）。Q1–Q17 已**全部裁决完毕**（逐条见 §11，无残余项）；本表为最终方案，**开工令由用户下达**，届时按 §12 实施。
> 实现范围 = 两 P0 线 `neoforge-1.21.1` + `forge-1.20.1`（同批同号）；提交时随实现升版（四线同号，两份 CHANGELOG；实现批以其时最新号为准继续递增）。
> 上游文档：`docs/chip-shop-ui-spec.md`（界面骨架，.86 已落地）；本批的模板来源 = 卡牌商店（`shop/CardShopCatalog.java`、`shop/CardShopShelf.java`、`shop/CardShopMenu.java`、`shop/CardShopCycleTicker.java`、`network/CardShopBuyPayload.java`、`client/CardShopClientState.java`）——**架构照抄，只在「候选抽取 / 价格曲线 / 刷新恢复 / 候选落盘 / 方块入口」上新增**。
> 界面骨架现状（.86，本批不改）：`screen/ChipShopScreen.java`、`client/ChipShopClientState.java`、`shop/ChipShopMenu.java`；绘制坐标与冻结常量一律不动（`docs/chip-shop-ui-spec.md` §3）。

## §0 需求逐条映射（R1–R15，含裁决后的口径变更）

| # | 你的需求（要点） | 实现点 | 落点 | 判据 |
|---|---|---|---|---|
| R1 | 三格显示 3 个候选筹码，图标取 `images/shop/unbord_chips` 同名图 | 资产入库为 `assets/astral_dice/textures/gui/chip_shop/chips/<chip_id>.png`，`ChipShopCatalog` 记录图标路径 | assets + `shop/ChipShopCatalog.java` | 守门 G9：池 66 枚 ↔ 图标映射 66/66（含 2 条显式别名，见 §1.1） |
| R2 | 三格边缘三种框代表三种品质 | 屏幕层已有 `qualityFrameIndexOf(ItemStack)`（稀有=1/史诗=2/传奇=3） | .86 已实现 | G10：品质码由服务端槽内容推导，客户端不自行猜测 |
| R3 | 每次进店重抽 3 个，且互不相同 | **口径已改**：候选**落盘**，进店**不无条件重抽**（见 R16/R17）；三格共用一个池、抽走即移除 | `shop/ChipShopRoller.java` | G12：三格 id 互不相同；G19：候选持久化语义 |
| R4 | 骰子星级决定品质概率 60/37/3 · 40/45/15 · 25/50/25 | `QUALITY_PROB[starLevel]` 逐字落地；星级复用卡牌商店口径 | `ChipShopCatalog` | G11：概率表逐字比对 + 星级取值 0..3 |
| R5 | 只能抽「饰品栏与物品栏都没有」的筹码；不足 3 时依次移除第三槽、第二槽 | 抽前过滤 `ownedBy(player)`；抽不满自然留空（先缺第 3 格，再缺第 2 格） | `ChipShopRoller` | G12/G13：已拥有过滤 + 缺口顺序 |
| R6 | 池中筹码已全拥有 或 物品栏已满 ⇒ 阻止进入 | `ChipShopOpener` 前置校验，不满足则 actionbar 红字且不打开 | `shop/ChipShopOpener.java` | G13 + 用例：两种拒绝条件各一例 |
| R7 | 已装备筹码的流派与类别显著影响随机因子 | 流派权重 `1 + SCHOOL_ALPHA × 同流派已装备筹码数` | `ChipShopRoller` | G11 变体：装备 0/1/3 枚同流派时的权重读数 |
| R8 | 类别五种、单枚只属一类 | `ChipShopCatalog` 内 66 枚逐一手工标注（§1 表，**你已采信**） | `ChipShopCatalog` | G10：每枚恰一类、五类全覆盖 |
| R9 | 主导流派出现概率更大 | 同 R7（线性加权） | `ChipShopRoller` | 同 G11 |
| R10 | 第一个购买的筹码不受类别影响；第二个起上一次选取显著影响类别 | 落盘字段 `lastPickCategory`；无记录 ⇒ 类别因子恒 1.0 | `component/ModAttachments.java` + `ChipShopRoller` | G14：无记录时权重表不含类别偏置 |
| R11 | 三格中只有第一格显著受流派+类别影响，后两格加大随机性 | `SLOT_DECAY` 把 slot1/slot2 的因子压向 1.0 | `ChipShopRoller` | G11：slot0 的因子强度 > slot1/2 |
| R12 | 下方文本框显示名称（短名居中；超宽左右滚动） | 屏幕层已有标签框（labelY=61，42×15）；2026-10-13 超宽改 marquee | .86 已实现 | 离线合成预览 + 实机（需你下达） |
| R13 | 点筹码格或文本框都选中，绿条移动 | `chipIndexAt` 命中筹码格 + 文本框 → 同一 `select()` | `screen/ChipShopScreen.java` | G15：两处命中都只调 `select`，不发包 |
| R14 | 最左按钮=购买，下方显示花费；余额不足红色闪烁 | 确认按钮发 `ChipShopBuyPayload`；服务端校验 → 拒绝则 `ChipShopDenyPayload`；**成功后自动关店**（R18） | `network/` + `ChipShopMenu` + `ChipShopClientState` | G16/G20：红闪常量 30t；服务端为唯一权威；购买后菜单关闭 |
| R15 | 最右按钮=刷新，下方显示剩余次数；为 0 阻止刷新并红色闪烁 | 刷新按钮发 `ChipShopRefreshPayload`；次数为 0 服务端拒绝 | 同上 | G16 + 用例：0 次时刷新不生效 |
| **R16**（新） | 未购买就退出 ⇒ **候选不变** | 候选落盘为玩家附件；菜单构造**只读**附件，不重抽 | `ChipShopState` + `ChipShopMenu` | G19：关店再开店（未购买/未刷新）三格 id 与花费逐字相同 |
| **R17**（新） | 只有「点刷新」或「购买过筹码」才刷新候选 | 重抽入口仅两处：`handleRefresh`（−1 次）与购买成功回调 | `ChipShopMenu` | G19：两条路径后候选必变（抽样断言） |
| **R18**（新） | 购买后自动关闭商店，筹码进物品栏 | 服务端写入第一个空物品栏格 → `player.closeContainer()` → 视为「已购买」⇒ 重抽候选 | `ChipShopMenu` | G20：购买后 menu 关闭 + `purchasedCount` +1 + 候选重抽 |
| **R19**（新） | 入口 = `chipshop.bbmodel` 方块 | 注册 `astral_dice:chip_shop` 方块（模型/贴图仓内已有，缺 blockstate/item model/注册，见 §7） | `block/ModBlocks.java` + `block/ChipShopBlock.java` + `assets/**/blockstates/chip_shop.json` | G21：方块存在、四向（或单态）state 完整、物品模型/名称/配方/掉落齐备 |
| **R20**（新） | 筹码永不出现「巅峰/奇特」品质；界面不画标题 | 池只含 rare/epic/legendary；`ChipShopScreen` 不绘制标题（.86 已如此） | `ChipShopCatalog` + .86 | G10：池内品质码 ⊆ {1,2,3} |

## §1 筹码池（66 枚）与四项属性

- 池 = `ModItems` 中所有 `<X>ChipItem` 注册项 = **66 枚**（已逐条实测：与 `ModItems` 注册表 66/66 吻合、与帕秋莉手册 66 个筹码条目 66/66 吻合、无多无缺）。
- ⛔ `blank_chip`（合成材料，手册 `category = astral_dice:materials`）**不入池**、也不参与「已拥有」判定（**你已确认**）。
- 品质分布：**稀有 19 / 史诗 28 / 传奇 19** —— 恰好覆盖 `quality_1/2/3` 三张外框；**永不出现 普通/巅峰/奇特**（你已确认）。
- **流派（唯一权威 = 帕秋莉手册条目的 `category` 字段）**：星光 11 / 治愈 10 / 标记 9 / 充能 11 / 泛用 25。
- **类别（我划，你已采信「先观察实际执行结果」）**：攻击力 26 / 强化 16 / 辅助 9 / 治疗 8 / 经济 7。

| 筹码 id | 名称 | 商店图标（`images/shop/unbord_chips/*.png`） | 品质 | 流派 | 类别 | 依据 |
|---|---|---|---|---|---|---|
| `atm_chip` | ATM机 | `ATM机.png` | 稀有(quality_1) | 星光 | 经济 | 装备后，每次向其他玩家转账星币时，转账双方各获得 1 星光与 2 星币（由转账发起者装备时触发）。 |
| `bank_card_high_chip` | 银行卡-余额多 | `银行卡-余额多.png` | 史诗(quality_2) | 星光 | 经济 | 装备期间获得 7 层基础星光。 |
| `bank_card_low_chip` | 银行卡-余额少 | `银行卡-余额少.png` | 稀有(quality_1) | 星光 | 经济 | 装备期间获得 4 层基础星光。 |
| `bank_card_unlimited_chip` | 银行卡-用不完 | `银行卡-用不完.png` | 传奇(quality_3) | 星光 | 经济 | 装备时获得 3 层星光；每次骰神赐福结束后，自身及友方玩家（已加入队伍时为同队成员，未加入任何队伍时为自身 32 格内的所有玩家，2026-10-15 起）获得 3 星币。（tooltip 按用户裁决沿用旧口径，未改 lang） |
| `eight_sided_dice_chip` | 八面骰 | `八面骰.png` | 稀有(quality_1) | 星光 | 经济 | 触发骰神赐福时掷骰并累计点数，每累计 8 点 +1 星光（有上限）；若本次骰点刚好为 8，获得 8 星币。 |
| `extra_battery_chip` | 额外电池 | `额外电池.png` | 史诗(quality_2) | 泛用 | 辅助 | 立牌主动技能冷却时间 -50%。 |
| `flashlight_burst_chip` | 手电筒-爆闪 | `手电筒-爆闪.png` | 传奇(quality_3) | 星光 | 攻击力 | 攻击「**该玩家**未攻击过的目标（玩家×目标判重）」时获得 1 层星光（去重载体 = **玩家级**附件 `flashlight_burst_targets`：逗号分隔 UUID 串、上限 256 满则不再发放；同一目标对不同佩戴者各可发放一次）；目标须为**敌对**（含被激怒的中立生物）；每拥有 2 层星光，攻击伤害 +1。 |
| `flashlight_chip` | 手电筒-强光 | `手电筒-强光.png` | 史诗(quality_2) | 星光 | 攻击力 | 击杀不少于 20 血的敌对目标后 +1 星光；每拥有 4 层星光，攻击伤害 +1。 |
| `fortune_stick_chip` | 财运签 | `财运签.png` | 史诗(quality_2) | 星光 | 攻击力 | 骰神赐福期间击杀第一个**怪物**时，使自身及周围 16 格内的友方玩家各随机获得 1~7 层星光（星光越多越容易获得少量星光；每个赐福周期只触发一次）。 |
| `golden_shooting_star_chip` | 金色飞星 | `金色飞星.png` | 传奇(quality_3) | 星光 | 攻击力 | 路过敌对目标时，使其受到 2 点伤害，并使自身获得 1 层「星光」；若目标为精英怪物或 Boss，则额外对其造成玩家当前「星光」层数的伤害。每 10 秒触发一次；与紫色飞星共享计时器。 |
| `purple_shooting_star_chip` | 紫色飞星 | `紫色飞星.png` | 史诗(quality_2) | 星光 | 攻击力 | 路过敌对目标时，使其受到 1 点伤害，并使自身获得 1 层「星光」。每 10 秒触发一次；与金色飞星共享计时器。 |
| `star_coin_hammer_chip` | 星币锤 | `星币锤.png` | 传奇(quality_3) | 星光 | 攻击力 | 装备时获得 5 层星光；若物品栏持有至少 32 枚星币，每次进入骰神赐福消耗 18 星币，并按持有星币总数的 30%% 提升攻击伤害（上限 100；星币袋按 9 枚计；零散星币不足时自动拆开 1 个星币袋、只扣走所需枚数 |
| `abyss_reagent_chip` | 试剂-深渊 | `试剂-深渊.png` | 史诗(quality_2) | 治愈 | 治疗 | 装备时 / 触发骰神赐福时 / 每 1:00 治愈结算时治愈层数 +2（卸下 -2，2026-10-15 起与医疗箱同口径）；触发治愈回血时获得「有效治疗量 ×2」的伤害吸收（黄心），有效治疗量不足 1 时至少获得 2 点。 |
| `big_bowl_stew_chip` | 大碗炖肉 | `大碗炖肉.png` | 传奇(quality_3) | 治愈 | 治疗 | 骰神赐福效果结束后，使自身和 16 格范围内所有友方玩家恢复 2 点生命值、获得 2 层治愈。 |
| `buffer_shield_chip` | 缓冲盾牌 | `缓冲盾牌.png` | 稀有(quality_1) | 治愈 | 治疗 | 受到攻击时 +2 治愈与 3 星币，每 15 秒只能触发一次。 |
| `candy_chip` | 可口糖果 | `可口糖果.png` | 史诗(quality_2) | 治愈 | 治疗 | 每使用一张效果牌，+1 层治愈并恢复 1 点生命；生命值已满时，本轮出牌数 +1（每轮最多一次）。 |
| `cutter_blade_chip` | 美工刀-锋利 | `美工刀-锋利.png` | 传奇(quality_3) | 治愈 | 攻击力 | 生命值不低于 60%% 时攻击力 +4，攻击伤害额外增加当前治愈层数的点数。 |
| `cutter_chip` | 美工刀-初级 | `美工刀-初级.png` | 史诗(quality_2) | 治愈 | 攻击力 | 生命值不低于 60%% 时攻击力 +2，攻击伤害额外增加当前治愈层数的点数。 |
| `friendship_badge_chip` | 友情徽章 | `友情徽章.png` | 史诗(quality_2) | 治愈 | 治疗 | 友情徽章：对友方玩家施加任意治疗效果时，双方各获得 2 点治愈。 |
| `medkit_complete_chip` | 医疗箱-完备治疗 | `医疗箱-完备治疗.png` | 传奇(quality_3) | 治愈 | 治疗 | 装备时（重生 / 重登 / 切换维度后同样触发）、触发骰神赐福、以及每 1:00 治愈结算时，+3 治愈。 |
| `medkit_emergency_chip` | 医疗箱-紧急治疗 | `医疗箱-紧急治疗.png` | 稀有(quality_1) | 治愈 | 治疗 | 装备时（重生 / 重登 / 切换维度后同样触发）、触发骰神赐福、以及每 1:00 治愈结算时，+1 治愈。 |
| `vitamin_pill_chip` | 维生素药丸 | `维生素药丸.png` | 史诗(quality_2) | 治愈 | 治疗 | 获得任意卡牌时，治愈 +1。 |
| `eagle_scope_chip` | 鹰眼瞄具 | `鹰眼瞄具.png` | 传奇(quality_3) | 标记 | 攻击力 | 攻击时对目标施加 1 层标记；对带标记目标额外造成其标记层数 ×2 的攻击伤害。 |
| `hand_fan_big_chip` | 手持风扇-大 | `手持风扇-大.png` | 史诗(quality_2) | 标记 | 辅助 | 使用主动技能后：获得一张随机效果牌（不含专属），并对周围一定范围内所有敌对目标施加 1 层标记（需指定目标的立牌改为确认目标后才触发）。 |
| `hand_fan_small_chip` | 手持风扇-小 | `手持风扇-小.png` | 稀有(quality_1) | 标记 | 辅助 | 使用主动技能后：对自身周围 16 格内所有敌对目标施加 1 层标记（需指定目标的立牌改为确认目标后才触发）。 |
| `magic_quiver_chip` | 魔法箭袋 | `魔法箭袋.png` | 史诗(quality_2) | 标记 | 辅助 | 使用伤害效果牌，并对已有标记的目标造成远程或法术伤害后，返还本次使用的效果牌并对目标施加 1 层标记。每 30 秒只触发一次。 |
| `marker_sprayer_chip` | 标记喷罐 | `标记喷罐.png` | 稀有(quality_1) | 标记 | 辅助 | 对目标造成远程或魔法伤害后，使目标获得 1 层标记。 |
| `ninja_star_chip` | 忍术飞镖 | `忍术飞镖.png` | 传奇(quality_3) | 标记 | 攻击力 | 效果牌出牌数 +1；对目标造成的远程和魔法伤害增加目标标记层数点。 |
| `scope_chip` | 普通瞄具 | `普通瞄具.png` | 史诗(quality_2) | 标记 | 攻击力 | 攻击力 +2；攻击时对目标施加 1 层标记。 |
| `sensor_chip` | 感测装置 | `感测装置.png` | 史诗(quality_2) | 标记 | 辅助 | 攻击的目标每拥有 3 层标记，其战斗骰点 -1（最多 -4）；闪避成功时，对攻击者施加 1 层标记。 |
| `target_chip` | 标靶 | `标靶.png` | 稀有(quality_1) | 标记 | 攻击力 | 攻击力 +1；触发骰神赐福后，对本次攻击目标之外距离最近的敌对目标施加 1 层标记。 |
| `advanced_peripherals_chip` | 高级外设 | `高级外设.png` | 史诗(quality_2) | 充能 | 攻击力 | 充能不少于 4 层时，攻击力 +4。每次触发骰神赐福时，消耗 1 层充能（属于「消耗」，佩戴原初核心时会计入赋能返还）。 |
| `airbag_chip` | 安全气囊 | `安全气囊.png` | 史诗(quality_2) | 充能 | 辅助 | 受到致命伤害时，消耗 6 层充能，使本次伤害无效（冷却 1:00）。该保命效果优先于磨刀石、不死图腾与末影骰子；无视无敌的致死伤害同样有效（如指令 /kill）。此外，受到伤害时充能 +1（冷却 15 秒）。 |
| `current_core_chip` | 电流核心 | `电流核心.png` | 史诗(quality_2) | 充能 | 强化 | 使用主动技能时，获得 1 层充能。主动技能冷却中按下主动技能键，可按剩余冷却占比消耗充能并立即完成冷却（充能不足则不生效）；消耗档位为剩余冷却 ÷ 180 秒的占比切成 6 档（最低 1 层、最高 6 层）。 |
| `electric_glove_chip` | 电击手套 | `电击手套.png` | 史诗(quality_2) | 充能 | 攻击力 | 使用效果牌时，充能 +1。充能不少于 4 层时，使用伤害类效果牌会消耗 4 层充能，使本次远程和魔法伤害同时命中目标 3 格范围内的其他目标。 |
| `electric_sword_chip` | 电流剑 | `电流剑.png` | 稀有(quality_1) | 充能 | 攻击力 | 每拥有 4 层充能，攻击力 +1。累计击杀 10 个敌对目标，充能 +2。 |
| `energy_recycler_chip` | 能量回收器 | `能量回收.png`（别名：物品名「能量回收器」） | 稀有(quality_1) | 充能 | 强化 | 每累计移动 150 米，充能 +1；当前充能 ≥ 5 层时，移动速度 +5%%。 |
| `infinite_grid_chip` | 无线输电杆 | `无线电网.png` | 史诗(quality_2) | 充能 | 强化 | 效果牌出牌数 +1；每次获得充能时，使自身周围 8 格内的其他友方玩家各获得 1 层充能（有队伍时仅同队，无队伍时为范围内所有玩家）。 |
| `perpetual_motion_chip` | 永动机 | `永动机.png` | 传奇(quality_3) | 充能 | 强化 | 触发骰神赐福时，若当前充能不足 8 点，则补充至 8 点；装备期间原版近战攻击冷却减半。 |
| `primordial_core_chip` | 原初核心 | `原初核心.png` | 传奇(quality_3) | 充能 | 强化 | 每消耗 1 层充能，获得 1 层「赋能」（上限 10 层）。每层赋能提供攻击力 +1 与防御力 +1，每 0:30 减少 1 层。 |
| `railgun_chip` | 电磁炮 | `电磁炮.png` | 传奇(quality_3) | 充能 | 攻击力 | 充能不少于 6 层时，攻击力 +5。近战攻击敌对目标时，延迟 1 秒对目标 3 格范围内的敌对目标降下雷击，雷击造成「5 + 本次攻击伤害的 50%%」（下限 5 点），随后进入 1:00 冷却。雷击是独立的真伤伤害源， |
| `warp_engine_chip` | 跃迁引擎 | `跃迁引擎.png` | 稀有(quality_1) | 充能 | 强化 | 触发瞬移时，充能 +2 并获得迅捷 (0:10)。计入的传送：末影珍珠、末影骰子不死图腾后的安全瞬移、维度传送门、Waystone（需安装 Waystones）。传送门与 Waystone 触发共享 3:00 冷却；末影 |
| `adrenaline_high_chip` | 肾上腺素-高效 | `肾上腺素-高效.png` | 传奇(quality_3) | 泛用 | 攻击力 | 肾上腺素-高效：生命值为 50%% 或更低时，攻击力/防御力 +8（吸血鬼立牌「汲取」生效期间视为条件通过）。 |
| `adrenaline_low_chip` | 肾上腺素-一般 | `肾上腺素-一般.png` | 史诗(quality_2) | 泛用 | 攻击力 | 肾上腺素-一般：生命值为 50%% 或更低时，攻击力/防御力 +3（吸血鬼立牌「汲取」生效期间视为条件通过）。 |
| `big_backpack_chip` | 大背包 | `大背包.png`（Q16 已补齐，2026-10-12） | 史诗(quality_2) | 泛用 | 强化 | 效果牌出牌数 +1。 |
| `bookmark_chip` | 书签 | `书签.png` | 稀有(quality_1) | 泛用 | 攻击力 | 使伤害类效果牌伤害加成 +2。 |
| `boxing_gloves_high_chip` | 拳击手套-高级 | `拳击手套-高级.png` | 传奇(quality_3) | 泛用 | 攻击力 | 骰神赐福攻击力 +8。 |
| `boxing_gloves_low_chip` | 拳击手套-初级 | `拳击手套-初级.png` | 稀有(quality_1) | 泛用 | 攻击力 | 骰神赐福攻击力 +2。 |
| `boxing_gloves_medium_chip` | 拳击手套-中级 | `拳击手套-中级.png` | 史诗(quality_2) | 泛用 | 攻击力 | 骰神赐福攻击力 +4。 |
| `cursed_sword_chip` | 诅咒之剑 | `诅咒之剑.png` | 稀有(quality_1) | 泛用 | 攻击力 | 装备时始终受到青之诅咒：护甲 -20%%，盔甲韧性 -50%%。 |
| `magic_tome_chip` | 魔法秘典 | `魔法秘典.png` | 史诗(quality_2) | 泛用 | 辅助 | 每使用 3 张效果牌，复制最后一张使用的效果牌并返回到物品栏。 |
| `member_recommendation_chip` | 会员推荐信 | `会员推荐信.png` | 稀有(quality_1) | 泛用 | 经济 | 每次触发骰神赐福时，获得一张随机卡牌。装备期间，自己商店内所有卡牌价格 -1 星币（可与经商立牌的降价叠加，最低 0 星币）。⚠ 该 −1 **只作用于卡牌商店**，筹码商店不适用（Q13 已裁决）。 |
| `moto_helmet_high_chip` | 摩托头盔-高级 | `摩托头盔-高级.png` | 传奇(quality_3) | 泛用 | 强化 | 防御力 +6，盔甲韧性 +2。 |
| `moto_helmet_low_chip` | 摩托头盔-一般 | `摩托头盔-一般.png` | 稀有(quality_1) | 泛用 | 强化 | 防御力 +2。 |
| `moto_helmet_medium_chip` | 摩托头盔-中级 | `摩托头盔-中级.png` | 史诗(quality_2) | 泛用 | 强化 | 防御力 +4。 |
| `piercing_gun_chip` | 贯穿之铳 | `贯穿之铳.png` | 传奇(quality_3) | 泛用 | 攻击力 | 对目标造成的远程或魔法伤害额外增加目标防御力点数的伤害。 |
| `piggy_bank_chip` | 小猪存钱罐 | `小猪存钱罐.png` | 稀有(quality_1) | 泛用 | 经济 | 每使用 2 张效果牌，获得 3 星币。 |
| `revenge_halberd_chip` | 复仇之戟 | `复仇之戟.png` | 史诗(quality_2) | 泛用 | 攻击力 | 获得以下任一效果时，攻击力 +6：虚弱、缓慢、挖掘疲劳、失明、黑暗、蓄风、盘丝、渗浆、寄生、青之诅咒。 |
| `sandwich_high_chip` | 夹心饼干-美味 | `夹心饼干-美味.png` | 传奇(quality_3) | 泛用 | 强化 | 最大生命值 +8；最大生命值超过 20 点时，超出部分每 4 点生命值 +1 攻击力。 |
| `sandwich_low_chip` | 夹心饼干-一般 | `夹心饼干-一般.png` | 稀有(quality_1) | 泛用 | 强化 | 最大生命值 +4。 |
| `sandwich_medium_chip` | 夹心饼干-可口 | `夹心饼干-可口.png` | 史诗(quality_2) | 泛用 | 强化 | 最大生命值 +8。 |
| `satellite_chip` | 探天卫星 | `探天卫星.png` | 传奇(quality_3) | 泛用 | 辅助 | 物品栏中「轨道炮」少于 6 张时，每 1:00 获得一张「轨道炮」。 |
| `smart_watch_chip` | 智能手表 | `智能手表.png` | 史诗(quality_2) | 泛用 | 辅助 | 物品栏中卡牌数量不足 6 张时，每击杀 1 个敌对目标获得一张随机卡牌。 |
| `speed_skates_high_chip` | 速度轮滑-高级 | `速度轮滑-高级.png` | 传奇(quality_3) | 泛用 | 强化 | 移动速度 +25%%。 |
| `speed_skates_low_chip` | 速度轮滑-初级 | `速度轮滑-初级.png` | 稀有(quality_1) | 泛用 | 强化 | 移动速度 +5%%。 |
| `speed_skates_medium_chip` | 速度轮滑-中级 | `速度轮滑-中级.png` | 史诗(quality_2) | 泛用 | 强化 | 移动速度 +15%%。 |
| `whetstone_chip` | 磨刀石 | `磨刀石.png` | 史诗(quality_2) | 泛用 | 攻击力 | 生命值为 50%% 或更低时，攻击力 +4、受到伤害 -2；且生命值大于 1 点时，单次所受伤害至多使生命值降至 1 点（冷却 1:00）。 |

### §1.1 素材映射实测（2026-10-12 复核你重命名后的目录）

- 目录实况：`images/shop/unbord_chips` 共 **63** 张 png（`images/shop` 下另有 `card+event/` 39 张与商店 UI 素材若干，与本池无关）。
- **64 枚**筹码与图标**同名精确命中**（含 `big_backpack_chip` ↔ `大背包.png`）；其余 **2 条为显式别名**，只影响贴图路径、**不改物品名** ⇒ **图标映射 66/66 全覆盖**：
  - `energy_recycler_chip`（名称「能量回收器」）↔ 图标 `能量回收.png`（你「能量回收期 → 能量回收」改名结果）；
  - `infinite_grid_chip`（名称「无线输电杆」）↔ 图标 `无线电网.png`（你定名：素材文件名与显示名不同，**按显示名「无线输电杆」入库**）。
- ✅ **Q16 已闭环**：`big_backpack_chip`（大背包）的 `大背包.png` 已由你补齐（2026-10-12 复测：63 张内「背包」命中 1 条），**不再使用** `textures/item/big_backpack_chip.png` 兜底。
- 未被任何筹码使用的素材 **1 张**：`循环往复.png`（`手电筒-爆闪.png` 已于本批被 `flashlight_burst_chip` 采用）。按你「不要弃用素材」的要求：**保留在仓库 `images/`**，但**不复制进 jar**（无对应筹码 ⇒ 引用不到）。

## §2 抽取算法（实现 = `shop/ChipShopRoller.java`）

```
roll(player, reason):                     # reason = ENTER_FIRST | REFRESH | POST_PURCHASE | INVALIDATED
  star   = currentStarLevel(player)       # 复用卡牌商店：CuriosApi → findFirstCurio(DiceCurioItem)
                                          #   → DiceCurioItem.configStarLevel(stack)（下界之星骰恒 3，否则 clamp 0..3）
  pool   = CHIP_POOL.filter(!ownedBy(player))     # ownedBy = chip 饰品槽 ∪ 36 物品栏格（不含副手，你已确认）
  ctx    = { schoolW  = 1 + SCHOOL_ALPHA * equippedSchoolCount(player),  # R7/R9
             lastCat  = state.lastPickCategory 或 null }                 # R10（null ⇒ 无类别偏置）
  picked = []
  for slot in [0, 1, 2]:
      q   = weightedPick(QUALITY_PROB[star] 在「本档仍有候选」上按比例重分配)   # R4
      w   = schoolW(cand) * catFactor(cand, lastCat, slot)                  # R11：slot0 全强度、slot1/2 衰减
      c   = weightedPick(pool.filter(tier == q), weight = w)
      if c == null: break                 # 池空 ⇒ 停止（进入时已保证 ≥1 枚可用，见 R6）
      picked += c; offers[slot] = c
```

- 品质概率**每格独立**掷；某档无可用筹码时把该档概率**按比例重分配给其它档**（避免出现空格）。
- 三格共用同一池 ⇒ 天然去重（R3）；不足 3 格时先缺第 3 格、再缺第 2 格（R5）。
- **常数一律为 `public static final`（不做配置项 —— 你已裁决）**，集中定义在 `ChipShopCatalog`：

| 量 | 冻结值 | 含义 |
|---|---|---|
| `QUALITY_PROB` | 0–1★ 60/37/3、2★ 40/45/15、3★ 25/50/25 | 你给的表，逐字落地 |
| `SCHOOL_ALPHA` | 0.60 | 已装备 N 枚同流派筹码 ⇒ 该流派权重 ×(1 + 0.60N) |
| `CATEGORY_ALPHA` | 2.0 | 「上次购买筹码」同类别 ⇒ 权重 ×2.0（仅 slot0 全强度） |
| `SLOT_DECAY` | 0.25 | slot1/2 用 (factor − 1) × 0.25 + 1 ⇒ 随机性显著更大 |
| `PRICE_INITIAL` / `PRICE_STEP` / `PRICE_MAX` | 10 / 5 / 100 | 见 §6（R 裁决 1） |
| `REFRESH_INITIAL` / `REFRESH_MAX` / `REFRESH_REGEN_TICKS` | 3 / 5 / 36000 | 36000 ticks = 30:00 ⇒ 见 §6（裁决 2） |

## §3 状态与落盘（**v1 的「候选不落盘」已作废**）

- **全部落盘为一个玩家附件 `CHIP_SHOP_STATE`**（机制与 `ModAttachments.SHOP_SHELF` 完全同型：neo 用 `AttachmentType` + `Codec`，forge 用 `AttachedDataKey` + `Codec`，见 §10）：

| 字段 | 类型 | 语义 |
|---|---|---|
| `offers` | 3 × `Optional<ItemStack>`（或 `ResourceLocation` + 空槽标记） | 当前候选（允许空槽，按 R5 的缺口顺序） |
| `lastPickCategory` | `Optional<ChipCategory>` | R10：上次购买筹码的类别 |
| `purchasedCount` | int | 累计购买次数 ⇒ 价格 = min(100, 10 + 5×count)（裁决 1） |
| `refreshesLeft` | int | 当前剩余刷新次数（初值 3、上限 5） |
| `regenAnchor` | long（世界 gameTime） | 刷新恢复锚点：首次消耗刷新后 = now + 36000；每次到点 +1 并顺延（裁决 2） |

- **重抽的唯一三个入口**（R16/R17）：① 附件**无候选**或候选**含已拥有项**时的进店补抽（免费，见 Q15）；② `handleRefresh` 成功（−1 次）；③ 购买成功（R18）。
- **刷新恢复**：服务端 tick（照抄 `CardShopCycleTicker.onServerTick` 的逐玩家遍历口径）检查 `refreshesLeft < REFRESH_MAX && now >= regenAnchor` ⇒ `+1`、`regenAnchor += 36000`、落盘；**玩家不在店内也照涨**（与卡牌商店沙漏口径一致）。
- 服务端运行态：`SimpleContainer(3)`（隐藏槽，承载候选 ItemStack 供同步）+ `int[] prices`（每格单价，见 §6）+ 附件读出的 `refreshesLeft`。

## §4 菜单 / 网络 / 客户端接线

- `shop/ChipShopMenu.java` 新增：
  - 隐藏槽 3 个（`HIDDEN_X/Y = -10000`，照抄 `CardShopMenu` 的 `ShelfSlot`；`mayPickup` 恒 false ⇒ 候选永不进入原版「光标携带」机制，杜绝拖拽白拿）；
  - DataSlot：`[0]` 单价花费、`[1]` 剩余刷新、`[2]` 总刷新（= 5 上限）、`[3]` 拒绝原因、`[4..6]` 三格品质码；
  - 服务端入口：`handleBuy(offerIndex)`、`handleRefresh()`（校验顺序照抄 `CardShopMenu.handleBuy`：菜单归属 → 索引范围 → 该格未购买 → 余额 → 扣款 → **写入物品栏 → 重抽并落盘 → `closeContainer()`**（2026-10-12 口径修订：关店放在全部落盘与提示之后 —— 同一服务端回调内完成，避免「状态未落盘就把玩家弹出去」）。
- 网络包（照抄卡牌商店三条）：`network/ChipShopBuyPayload.java`（`record(int offerIndex)`）、`network/ChipShopRefreshPayload.java`、`network/ChipShopDenyPayload.java`（`record(int reason)`，0=余额不足、1=刷新次数为 0、2=**保留码**（物品栏已满，购买路径不再下发））。
- 客户端：`screen/ChipShopScreen.java` 的 `onConfirmPressed()` / `onRefreshPressed()` 从「只计数 + TODO」改为**发包**；`containerTick` 从菜单同步 `setOffers/setCost/setRefreshes`；`removed()` 清状态（.86 已有）。
- **红闪**：`client/ChipShopClientState.java` 新增 30t 闪烁（与 `CardShopClientState.DENY_BLINK_TICKS = 30`、`denyBlinkRed() = denyTicks > 0 && ((denyTicks-1)/5) % 2 == 1` 同式）——余额不足闪**花费数字**、次数为 0 闪**剩余刷新次数文本**。

## §5 交互与防绕过（R13–R15）

| 判据 | 实现 |
|---|---|
| 必须点确定才能购买 | 唯一购买入口 = `mouseClicked` 的确认按钮分支（.86 已保证全类只有那一处调用 `onConfirmPressed`，守门 G4 已断言调用点位置）；候选**不经过**原版槽位协议 |
| 不能双击/拖动购买 | 不注册 `mouseDragged`；隐藏槽 `mayPickup` 恒 false；筹码格点击只 `select()` |
| 点筹码格或文本框都选中 | `chipIndexAt` 扩到「筹码格 ∪ 标签框」（labelY=61，42×15），两者都只调 `select()` |
| 绿条移动 | .86 已实现（画在对应标签框内） |
| 余额不足 / 次数为 0 | 服务端拒绝 + `ChipShopDenyPayload` → 客户端红闪；**服务端是唯一权威**（客户端余额只用于显示） |
| 购买落点 | 服务端先自判 36 格**有无空位**：有空位 `add`；无空位且**非创造** → `drop` 弹到玩家身边；**创造模式遵循原版默认**（满包被吞、不掉落）——**不含副手**（你已确认） |

## §6 价格与刷新次数（**已按裁决改写**）

- **价格（裁决 1）**：`price = min(PRICE_MAX, PRICE_INITIAL + PRICE_STEP × purchasedCount) = min(100, 10 + 5 × 已购买次数)`。
  - `purchasedCount` = 该玩家**累计购买次数**，随 `CHIP_SHOP_STATE` **持久化**（跨进店、跨存档）；三格**同价**（价格不再按品质区分 —— v1 的 `PRICE_BY_QUALITY` 作废）。
  - 价格曲线：第 1 次 10 → 第 2 次 15 → … → 第 19 次 100 → 之后恒 100。
  - `member_recommendation_chip`（会员推荐信）的商店 −1：**已裁决 = 不适用于筹码商店**（Q13：该 −1 只影响卡牌商店）⇒ 筹码商店**没有任何折扣路径**，价格恒为 `PRICE_INITIAL + PRICE_STEP × purchasedCount`（10 → 上限 100）。
  - 余额权威 = 前置库 `StarEngineEconomy.hasBalance / withdraw`；扣款成功后 `StarCoinBalanceSync.notifyChanged(player)`。
- **刷新（裁决 2）**：`REFRESH_INITIAL = 3`（首次获得该状态时）、`REFRESH_MAX = 5`。
  - 「**第一次使用刷新后**」才开始恢复：首次成功刷新时若 `regenAnchor == 0` ⇒ `regenAnchor = now + 36000`（30:00）。
  - 之后每 `REFRESH_REGEN_TICKS = 36000` tick `+1`（上限 5）；到上限后锚点停止顺延。
  - 次数为 0 时按钮**只红闪、不发包**（服务端同样拒绝）。
- **购买后（R18）**：写物品栏 → `purchasedCount += 1` → `lastPickCategory = 刚购买的类别` → **重抽候选并落盘** → 同步余额 + actionbar → `player.closeContainer()`（2026-10-12 口径修订：关店是这条链的**最后一步**，此前写的是「关店在重抽之前」，实现按本条执行；判据仍为「关店与扣款同分支」= G20/G20b）⇒ 下次开店看到的是新一批。

## §7 开屏入口（**已裁决：`chipshop.bbmodel` 方块**）

- **实测资源现状**（本日复核，两 P0 线一致）：
  - 已有：`assets/astral_dice/models/block/chip_shop.json`（7161 B，`format_version 26.3`、"Made with Blockbench"、`texture_size 64×64`、`render_type: cutout`、`particle` 已指向 `astral_dice:block/chip_shop`）+ `assets/astral_dice/textures/block/chip_shop.png`（3232 B）；源模型 = 仓根 `models/chipshop.bbmodel`（另有 `models/cardshop.bbmodel` 为既有方块）。
  - **缺**：`blockstates/chip_shop.json`、`models/item/chip_shop.json`、`ModBlocks` 注册、`BlockItem`、名称 lang、配方 / 掉落表、创造栏条目。
- 实现（照抄 `block/CardShopBlock.java`，含 1.21.1 特有的 `codec()` 覆写与 `HorizontalDirectionalBlock` 四向、1.20.1 无 `codec()`）：
  1. `block/ChipShopBlock.java`：`extends HorizontalDirectionalBlock`，`FACING` 四向 + `getStateForPlacement` 用 `context.getHorizontalDirection().getOpposite()`（与卡牌商店同口径，保证朝向面向放置者）；
  2. `ModBlocks` 注册 `CHIP_SHOP` + `ModItems` 注册 `BlockItem`（照 `CARD_SHOP` 两行）；
  3. `blockstates/chip_shop.json`（四向 y = north:180/east:270/south:∅/west:90——本模型屏幕造在 +Z 侧，故在 `card_shop` 基准上统一 +180°，使放置后屏幕朝玩家；2026-10-13 修正）+ `models/item/chip_shop.json`（parent = `block/chip_shop`）；
  4. 三语 `block.astral_dice.chip_shop`（筹码商店 / Chip Shop / チップショップ）+ 配方 + 掉落表 + 创造栏；
  5. 交互 = `use`/`useWithoutItem` → `ChipShopOpener.open(player)`（照抄 `CardShopOpener`，line 1.21.1 vs 1.20.1 签名不同，见 §10；⚠️ 本文档早先写作 `tryOpen`，实作为 `open`，已按实作更正）。
  6. **配方**（本批自定，计划表原先未规定）：`crafting_shaped` pattern `["TTT","BSB","MMM"]`，T=`minecraft:smooth_stone_slab`、B=`astral_dice:star_coin`、S=`astral_dice:blank_chip`、M=tag `minecraft:logs`，产出 `astral_dice:chip_shop`×1。⚠️ **不得与 `card_shop` 的 `["TTT","BBB","MMM"]` 同图案同材料** —— 原版 shaped 配方按「第一个匹配」取用，同图案配方会让其中一个方块**静默不可合成**（本批复核 P1，已修），并已加守门 G22 强制唯一性。
- 「阻止进入」判定（`ChipShopOpener` 前置，不满足则 actionbar 红字且不打开）：① 可用候选 = 0（池中筹码已全拥有）；② 物品栏无空位（**只算 36 格，不含副手** —— 你已确认）。

## §8 文案键（三语同批：zh_cn / en_us / ja_jp）

- 复用（.86 已有）：`gui.astral_dice.chip_shop`（筹码商店，**不绘制** —— 2026-10-12 Q17 裁决；键保留备用）、`.cost`（%s，界面画星币图标+数字）、`.refresh_counter`（%s，只显示剩余刷新次数）、`.empty`（无）。
- 新增：`block.astral_dice.chip_shop`（方块名）、`msg.astral_dice.chip_shop_no_coin`（余额不足）、`msg.astral_dice.chip_shop_no_refresh`（刷新次数已用尽）、`msg.astral_dice.chip_shop_bought`（已购买 %s）、`msg.astral_dice.chip_shop_owned_all`（已拥有全部筹码）、`msg.astral_dice.chip_shop_inventory_full`（物品栏已满）、`msg.astral_dice.chip_shop_chip_dropped`（物品栏已满，筹码已掉落在地上 —— 生存满包成交时**后发**的掉落提示）。

## §9 守门与验证

- 扩展 `scripts/verify/verify_chip_shop_ui.ps1`（机器行前缀 `CHIPUI_*`）：
  - 既有 G1–G8（.86 界面骨架：素材/常量/交互/文案/注册/槽位/绑定）；
  - **G9** 池 = 66 + 图标映射 66/66（66/66 全覆盖：64 条同名 + 2 条显式别名 `energy_recycler_chip → 能量回收` / `infinite_grid_chip → 无线电网`）；
  - **G10** 类别覆盖 5、每枚恰一类、品质码 ⊆ {1,2,3}；
  - **G11** 品质概率表逐字 + 因子随 slot 衰减（slot0 强度 > slot1/2）；
  - **G12** 去重与缺口顺序；**G13** 已拥有过滤 + 两种拒绝进入条件；**G14** 无记录时无类别偏置；**G15** 两处命中都只 `select`；**G16** 红闪常量 = 30t 且无第二购买入口；
  - **G17（新）** 价格公式：源码常量 10/5/100 且公式 `min(MAX, INITIAL + STEP×count)` 逐字 + 边界用例（count=0→10、1→15、18→100、30→100）；
  - **G18（新）** 刷新：常量 3/5/36000 + 「首次消耗才设锚点」+ 到上限不再顺延；
  - **G19（新）** 候选持久化：菜单构造**不调用** `roll`；重抽仅存在于 `handleRefresh` 与购买回调两处（源码调用点断言）；
  - **G20（新）** 购买成功后 `closeContainer()` 调用点存在且与扣款同分支；
  - **G21（新）** 方块接入：`ModBlocks` 注册 + `blockstates/chip_shop.json` 四态 + `models/item/chip_shop.json` + 三语键 + 配方/掉落表齐备。
  - **G21b/G22..G27（2026-10-12 复核后补强，判据正本仍在本节）**：
    - **G21b** 方块**朝向语义**（此前只查字符串，方块类去掉 `getStateForPlacement` 也能绿）：`extends HorizontalDirectionalBlock` + `simpleCodec`/`codec()` + 默认朝北 + `getHorizontalDirection().getOpposite()` + `createBlockStateDefinition` 加 `FACING` + `useWithoutItem` 的客户端守卫；
    - **G22** **shaped 配方唯一性**：扫 `data/astral_dice/recipe/**`（含 `src/generated/resources`）全部 `crafting_shaped`，按「pattern + key 映射」归一化签名，出现重复即判红；并断言 `chip_shop` 产出 = `astral_dice:chip_shop`×1（**不得与 card_shop 同图案同材料** —— 同图案会让其中一个方块静默不可合成）；
    - **G23** **DataSlot 布局与拒绝码**（§4）：菜单内 `addDataSlot` 顺序 = 单价 → 剩余刷新 → 总刷新（`ChipShopCatalog.REFRESH_MAX`）→ 拒绝原因 → 循环三格品质码（共 7 槽）；`DENY_NONE=-1 / DENY_NO_COIN=0 / DENY_NO_REFRESH=1 / DENY_INVENTORY_FULL=2`，且屏幕侧「花费闪红」只认 0/2、「刷新次数闪红」只认 1；
    - **G24** 星等来源：`starLevel` 体内必须是 `Math.max(0, Math.min(3, CardShopShelf.currentStarLevel(player)))`；
    - **G25** §6 Q13「无任何折扣路径」与 §8 Q17「标题键保留但不绘制」：`ChipShop*` 源码不得出现 `discount/promotion/MEMBER_RECOMMENDATION`，屏幕不得使用 `gui.astral_dice.chip_shop` 标题键（只能用 `.cost/.refresh_count/.empty`）；
    - **G26/G27** 语义锚定（治「标识符存在即绿」）：`categoryWeight` 方法体内 `slot <= 0 ⇒ CATEGORY_ALPHA` 的分支顺序、抽取器 `roll` 体内三处 `break`（池空 / 无档 / 无候选）与 `picked.add → pool.remove` 顺序、`chipIndexAt` 体内**必须同时**有筹码格与标签框两次 `isHoveringStrip` 且返 `chip`、`handleBuy`/`handleRefresh` 方法体内各恰 1 处 `roll(`、`.closeContainer();` 必须落在 `handleBuy` 方法体内且位于扣款之后、价格公式锚定**含右括号**（防 `* count * 2)` 混过）。
- **变异测试（必做；2026-10-12 起为 **24 条**（脚本正本 = `scripts/verify/mutate_chip_shop_ui.ps1`，条目名 M1..M24），全部必须判红）**：池去重去掉 / 品质概率表改一位 / 已拥有过滤去掉 / 确认按钮改成双击触发 / 出现第二购买入口（`mouseClicked` 末尾也调确认）/ 隐藏槽 `mayPickup` 改 true / 价格步长改 1 / 价格上限改 50 / 刷新恢复 36000 改 18000 / 刷新上限钳制改 `Math.max` / `hasNoRoom` 的 `isEmpty()` 取反 / `chip >= 0` 改 `> 0` / 确认守卫削成 `chip < 0` / 仅 en_us 多加一个无关键 / Java 悬空键 / 两枚筹码图标互换字节 / **配方撞车回归（`BSB`→`BBB`）** / 关店挪出 `handleBuy` / 标签框命中删掉 / 方块朝向去掉 `getOpposite()` / 拒绝码重复（`DENY_NO_REFRESH` 1→2）/ 星等来源写死 3 / 注入折扣常量 / 概率表行序对调。
- 用例与规范：`scripts/test/cases/**` 增筹码商店用例；把 §2/§6 的冻结常数写进 `docs/chip-shop-ui-spec.md` 或新建 `docs/chip-shop-logic-spec.md`。
- **实机**：按 AGENTS「子代理与测试环境硬规则」第 5 条，**需你明确下达**才跑；本批默认只做静态取证 + `:<线>:classes` 编译 + 守门 + `code_review` 复核。

## §10 平台差异（两线逐条登记）

| 项 | neoforge-1.21.1 | forge-1.20.1 |
|---|---|---|
| 网络包 | `CustomPacketPayload` + `StreamCodec.composite` | `SimpleChannel` + `FriendlyByteBuf`（六参 `use`） |
| 菜单屏注册 | `RegisterMenuScreensEvent` | `MenuScreens.register`（`onClientSetup` 内 `enqueueWork`） |
| 玩家持久化附件 | `AttachmentType.builder(...).serialize(Codec)` + `ATTACHMENTS.register` | `AttachedDataKey.builder(name, Codec, default)` + `register` |
| 方块基类 | `HorizontalDirectionalBlock` **必须**覆写 `codec()`（`simpleCodec`）+ 静态 `CODEC` | 无 `codec()` 机制，直接 `extends HorizontalDirectionalBlock` |
| 方块交互 | `useWithoutItem(...)` | 六参 `use(state, level, pos, player, hand, hit)` |
| 饰品 API | Curios 9.x `ICurioItem` 新签名 | Curios 5.x 签名（`canEquip` 返回值不同） |
| 品质 | `AstralRarities.tierOf(...)` | 同（前置库同号） |

两线实现的差异**只允许**出现在上表这些点位；其余逻辑逐字相同（便于交叉复核）。

## §11 已裁决记录（Q1–Q17，2026-10-12；无残余项）

| Q | 你的裁决 | 落地位置 |
|---|---|---|
| Q1 价格 | 初始 **10** 星币，每购买一次 **+5**，上限 **100** | §6（v1 的按品质定价作废） |
| Q2 刷新 | 初始 **3**；**第一次使用刷新后**每 **30:00 +1**，上限 **5** | §6 |
| Q3 入口 | `chipshop.bbmodel` 方块（模型资源此前已用 Blockbench MCP 整理） | §7（R19） |
| Q4 素材 | 已重命名整理；「能量回收期」→「能量回收」；**不要弃用素材**（识别失败所致） | §1.1（实测：**66/66 映射**（含 1 别名）+ 1 张未使用；Q16 素材已补齐） |
| Q5 blank_chip | 材料，不入池 | §1 |
| Q6 类别 | **采信**，先观察实际执行结果 | §1 表 |
| Q7 因子 | 用该因子，**不做配置项，但做常量** | §2 常量表 |
| Q8 购买后 | **自动关闭商店 + 候选池刷新** | §6（R18） |
| Q9 候选落盘 | **未购买退出 ⇒ 候选不变**；只有「刷新」或「购买过」才刷新候选 ⇒ 候选**必须落盘** | §3（R16/R17） |
| Q10 物品栏满 | **不含副手**（36 格） | §5 |
| Q11 已拥有 | **只算 chip 饰品槽**（+ 物品栏 36 格） | §2 算法 |
| Q12 文案/品质 | 不加标题；**永不出现巅峰/奇特** | §0 R20 |
| Q17 标题 | **不需要画标题** ⇒ `gui.astral_dice.chip_shop` 键保留但**不绘制**（2026-10-12 追加裁决） | §8 |

**逐项裁决记录（Q13–Q17，2026-10-12 全部落定）**

- **Q13 已裁决（2026-10-12）**：会员推荐信的商店 −1 **只影响卡牌商店**，筹码商店**不适用** ⇒ 筹码商店无折扣路径（恒定「初始 10、每次购买 +5、上限 100」）。
- **Q14 已裁决**：刷新次数恢复用**世界 gameTime**、**离线照涨**（回来一次性补算，上限 5）—— 已确认，无需改动。
- **Q15 已裁决**：开店时若落盘候选含**玩家已拥有**的筹码（在别处获得）⇒ **直接整组免费重抽** —— 已确认。
- **Q16 已裁决（素材已补齐）**：`大背包.png` 已入 `images/shop/unbord_chips` ⇒ 用正式素材，取消 `textures/item/big_backpack_chip.png` 兜底。
- **Q17 已裁决（2026-10-12）**：**不需要画标题** ⇒ 三语 `gui.astral_dice.chip_shop` 键保留（不删除、不影响 key 集一致性），界面**不绘制任何标题文字**（照参考图）。

## §12 实施步骤（分工：基准线 T1–T6 由主 Agent 亲自编写，forge 移植与功能审查派子代理）

| 任务 | 内容 | 写作用域 | 依赖 |
|---|---|---|---|
| T1 | `ChipShopCatalog`（池 / 品质 / 流派 / 类别 / 图标映射 / 全部冻结常量）+ `ChipShopRoller`（抽取算法） | `neoforge-1.21.1/**/shop/ChipShopCatalog.java`、`.../shop/ChipShopRoller.java` | 无 |
| T2 | `ChipShopState` 附件（Codec）+ 刷新恢复 ticker（照 `CardShopCycleTicker`）+ `ModAttachments` 注册 | `.../shop/ChipShopState.java`、`.../shop/ChipShopRegenTicker.java`、`.../component/ModAttachments.java` | T1 |
| T3 | 服务端菜单逻辑：隐藏槽 / DataSlot / `handleBuy`（扣款→入包→`purchasedCount`+类别→重抽落盘→同步→actionbar→**关店**）/ `handleRefresh` | `.../shop/ChipShopMenu.java` | T1, T2 |
| T4 | 网络三包 + 客户端接线（发包 / 同步 / 30t 红闪） | `.../network/ChipShop*.java`、`.../client/ChipShopClientState.java`、`.../screen/ChipShopScreen.java` | T3 |
| T5 | 方块入口 `chip_shop`（Block+注册+blockstate+item model+lang+配方+掉落+创造栏+Opener） | `.../block/ChipShopBlock.java`、`.../block/ModBlocks.java`、`.../item/ModItems.java`、`.../shop/ChipShopOpener.java`、`.../resources/assets/**`、`.../resources/data/**` | T1 |
| T6 | 素材入库（66 张 → `textures/gui/chip_shop/chips/`）、守门 G9–G27 + 变异测试（24 条）、三语文案、用例/规范文档 | `assets/astral_dice/textures/gui/chip_shop/chips/**`、`scripts/verify/verify_chip_shop_ui.ps1`、`resources/**/lang/*.json`、`scripts/test/**`、`docs/**` | T1–T5 |
| T7 | `forge-1.20.1` 同批移植（逐文件，按 §10 差异改写） | `forge-1.20.1/**` 对应路径 | T1–T6 |

- 派发纪律（2026-10-12 新流程）：**T1–T6 由主 Agent 亲自编写**（基准线 `neoforge-1.21.1`）→ `code_review` 一次性独立会话复核 → 修完后**派子代理做独立功能审查**（`:<线>:classes` 编译 / 守门脚本 / 与需求逐字对账）→ **确认无误后**才派子代理做 **T7 `forge-1.20.1` 移植** → 移植线再 `code_review` + **另派一个独立子代理**功能审查。子代理单任务、用完即弃（硬规则第 1 条），优先派 DeepSeek；复核/审查发现的问题交**新**子代理改进（连续 3 次不到位 ⇒ 主 Agent 亲自重写）。
- 收尾：`:<线>:classes` 编译 + 守门 + `verify_resource_integrity` + 两份 CHANGELOG + 四线版本随批递增 + 本地提交（不 push）；实机按第 5 条等你下达。

> 审核方式：Q1–Q17 **已全部裁决并落定在本表；本表冻结待开工令**（用户 2026-10-12：「暂时不开工」）。开工时按 §12：T1–T6 由主 Agent 亲自编写基准线 → `code_review` → 独立子代理功能审查 → 确认无误后派子代理移植 `forge-1.20.1` → 再 `code_review` + 另一个独立子代理功能审查。
