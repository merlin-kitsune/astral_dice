# Astral Dice（星之骰戏）项目总结报告

> 生成日期：2026-08-15 ｜ 分析者：接手开发的 AI 工程师
> 项目根目录：`F:\MCProject\astra_dice`

---

## 一、项目概览

### 1.1 基本信息

| 项目 | 内容 |
|---|---|
| **模组名称** | Astral Dice（星之骰戏） |
| **modid** | `astral_dice` |
| **包名** | `com.merlinkitsune.astral_dice` |
| **Minecraft** | 1.21.1 |
| **NeoForge** | 21.1.235 |
| **Java 版本** | 21 |
| **构建系统** | Gradle + NeoForge ModDevGradle 2.0.141 |
| **当前版本** | 1.0-SNAPSHOT.15 |
| **作者** | MerlinKitsune, Nintyfive95 |
| **游戏定位** | 以 Curios API 饰品驱动的生存增强类模组，模仿《吉星派对》玩法 |

### 1.2 核心依赖

- **Curios API**（必需前置）：提供骰子/立牌/筹码三类饰品栏
- **KubeJS 2101.7.2-build.372**（测试用）：`/astralcurios` 槽位检查命令等自动化测试脚本
- **神秘遗物+（enigmaticlegacyplus）**（可选联动）：七咒之戒、启示之证、倒转之启的伤害联动
- **Iron 的法术与魔法书（irons_spellbooks）**（可选联动，compileOnly）：法伤判定白名单 + 魔力消耗减半
- 兼容联动（反射/标签方式）：FTB Teams、Open Parties and Claims、车万女仆、农夫乐事、新生魔艺（ars_nouveau）、诡厄巫法（goety）

### 1.3 开发/测试工程设施（本仓库特有）

- `scripts/test/prepare_world.ps1`：超平坦测试世界生成（含 NBT 修改 allowCommands）
- `scripts/test/inject_input.ps1`：向 MC 窗口注入按键/命令（PostMessage）
- `scripts/test/run_gradle.ps1`：gradlew 30 秒超时 + 最多 3 次重试（规避 gradle 不退出问题）
- `scripts/test/stop_game.ps1`：停止游戏进程
- `scripts/test/reports/`：三份 2026-08-14 测试报告
- `.opencode/agent/game-test.md`：自动化游戏测试完整流程文档
- `run/kubejs/server_scripts/`：`astralcurios` 槽位检查命令 + CurioChangeEvent 监听脚本
- `build.gradle` 内置 `pushToGame` 任务：编译后自动推送 jar 到整合包 mods 目录
- git 仓库：master 分支**尚无任何提交**（开发未纳入版本控制）

---

## 二、代码架构总览

### 2.1 包结构与职责

```
com.merlinkitsune.astral_dice
├── AstralDiceMod.java            # 主入口：注册全部 DeferredRegister、Curios 注册、配置加载、条件联动
├── combat/                       # ★ 战斗模块化（骰战 + 法伤）——近期重构核心
│   ├── DiceCombatContext.java    #   骰神赐福一次攻击的上下文（只读输入 + 修饰器写入区）
│   ├── AttackPowerModifier.java  #   攻击力修饰器接口
│   ├── DefensePowerModifier.java #   防御力修饰器接口
│   ├── DiceCombatModifiers.java  #   攻防修饰器注册表（内置效果类 + 卡牌掷骰）
│   ├── SpellDamageContext.java   #   法伤上下文
│   ├── SpellDamageModifier.java  #   法伤修饰器接口
│   └── SpellDamageRegistry.java  #   法伤作用域白名单 + 加成修饰器注册表（275 行）
├── component/                    # 数据层
│   ├── ModDataComponents.java    #   DataComponentType 注册（武器强化、卡牌耐久、立牌状态等）
│   ├── ModAttachments.java       #   玩家 AttachmentType 注册（星光/标记/治愈/冷却等 20+ 项）
│   ├── WeaponEnhancement.java    #   骰子强化 record（cost/星级/已插卡牌）
│   ├── AppliedStone.java         #   已插卡牌 record（类型 + 剩余耐久）
│   └── GameplayConstants.java    #   玩法常量（运行时从配置刷新，非 final）
├── config/                       # 配置
│   ├── ModCommonConfig.java      #   公共玩法配置（CONFIG_VERSION=6，20+ 项）
│   └── ModClientConfig.java      #   客户端配置（已空壳，v2）
├── damage/ModDamageTypes.java    # 自定义伤害类型（dice_damage）
├── datagen/                      # 数据生成（物品模型 + 配方）
├── effect/                       # MobEffect 注册与实现（25+ 效果）
├── event/                        # ★ 核心逻辑层
│   ├── ModEventHandlers.java     #   ★ 1765 行"上帝类"：骰战攻击链路/受击/击杀/事件/tooltip/战利品
│   ├── DamageEffectCardHandler.java  # 法伤结算主链路（修饰器聚合 + 跳数字）
│   ├── AstralEventSystem.java    #   事件系统（触发→目标收集→立牌增益）
│   ├── AstralEvents/AstralEventType/EventContext/EventEffect.java  # 事件类型注册
│   ├── EventTargetCollector.java #   事件目标收集（MC 队伍/FTB/OPAC/女仆）
│   ├── ArmorPenaltyHandler.java  #   护甲-30% 惩罚（扫地机事件）
│   └── IronSpellbooksCompat.java #   Iron 联动（法力消耗减半）
├── init/ModCreativeTabs.java     # 创造物品栏
├── item/                         # ★ 物品实现（60+ 物品）
│   ├── ModItems.java             #   全部物品注册
│   ├── DiceCurioItem.java        #   骰子（4 阶升级、卡牌栏/立牌栏/筹码栏动态管理）
│   ├── BaseSignItem.java         #   立牌抽象基类（J 键主动技能/冷却/等待释放/自动装备）
│   ├── *SignItem.java            #   12 个立牌
│   ├── *ChipItem.java            #   14 个筹码
│   ├── CardItem.java             #   战斗牌（攻击/防御，带耐久条）
│   ├── *CardItem.java            #   效果牌（王之力/狂暴/岿然不动/对怪激光/板砖/轨道炮/定向爆破）
│   ├── RangedBoostCardItem.java  #   伤害效果牌基类
│   ├── EffectCardItem/EffectCardPeriod.java  # 效果牌出牌周期/冷却/出牌锁
│   ├── HealingManager.java       #   "治愈"点数管理器（30 秒结算衰减）
│   ├── StarLightManager.java     #   "星光"点数管理器
│   ├── MarkManager.java          #   "标记"层数管理器（目标侧）
│   ├── CurioSlotUtil.java        #   槽位校验/自动装备/重复装备检测
│   ├── RandomCardHandler.java    #   随机卡牌发放（类别筛选 + 专属牌排除 + 作用域）
│   ├── ExclusiveCardUtil.java    #   专属牌获得者绑定
│   ├── InvestigationEventUtil.java  # 调查阶段事件（I/II/III/真相揭露）
│   └── BossEntityUtil.java       #   Boss 判定（标签 + boss 血条反射）
├── network/                      # 网络载荷（4 个 payload）
├── recipe/                       # 自定义骰子升级配方（继承强化数据）
├── resource/                     # ★ 流派（点数）模块化
│   ├── PlayerResource.java       #   流派统一接口（get/cap/add/spend/clear）
│   ├── ResourceType.java         #   HEALING / STARLIGHT / COUNTER(预留)
│   ├── PlayerResourceRegistry.java  # 流派注册表
│   └── ResourceConversion.java   #   点数↔物品转化（星光2:1星币）
├── screen/                       # 卡牌栏 GUI（CardInventoryMenu/Screen）
├── client/                       # 客户端（跳数字 HUD、actionbar、按键、伤害数字）
└── test/SignSkillTests.java      # GameTest（3 个：占星师/秘密侦探/无立牌）
```

### 2.2 核心系统详解

#### (1) 骰子系统（DiceCurioItem）
- 四阶升级链：基础骰子(2攻+2防) → 金骰子(3+3) → 钻石骰子(4+4) → 下界合金骰子(6+6)
- 卡牌栏：通过 H 键打开 `CardInventoryMenu` GUI 插入战斗牌
- 动态 Curios 槽位：佩戴不同骰子动态提供立牌栏/筹码栏数量
  - 立牌栏：基础 1 个，下界合金骰子 +1，3 星再 +1
  - 筹码栏：必须佩戴骰子才有（基础 1），钻石骰子 +2，合金骰子 +6
- 星级：铁砧用星币升级（0→1:15 币, 1→2:20, 2→3:25），升级增加 cost 上限
- 升级配方 `DiceUpgradeShapedRecipe`：合成产物**完整继承**原骰子的星级与插卡配置
- 防"饰品弹出 bug"：`onEquip` 采用防御式槽位调整（forceRemove=false），重载场景不移出物品

#### (2) 卡牌系统
- **战斗牌**（插骰子卡牌栏，消耗耐久）：
  - 攻击牌：中(1~3)/大(1~6)/特大(1~10)、暗影突袭(+3固定)、名刀·嘎呜切(1~20)、蓄力(+5)、全力攻击(+6，赐福结束返还)
  - 防御牌：中(1~3)/大(1~6)/特大(1~10)
  - 各牌有 cost（占卡牌栏费用）与耐久（CardItem 显示耐久条）
- **效果牌**（手持使用，出牌周期制）：
  - 功能效果牌：王之力(+5攻/层)、狂暴(+3攻/层，受伤+1/层)、岿然不动(+6防/层)
  - 伤害效果牌：对怪激光(+4 法伤)、对怪板砖(+6)、轨道炮(+8)、定向爆破(+5+AOE)、活体书页(专属，法伤+标记)
  - 专属牌：活体书页、命运的指引（仅获得者可用，`ExclusiveCardUtil` 绑定 UUID）
- **出牌周期（EffectCardPeriod）**：基础出牌数固定 1；固定加成（大背包+1/忍术飞镖+1）；临时加成（活体书页/命运指引/忍者立牌主动）；出牌即开始 30 秒冷却，冷却归零出牌数归零；效果未结束不可开新轮；出牌锁防刷新绕过

#### (3) 立牌系统（12 个，stand 栏，J 键主动技能，180 秒冷却）
| 立牌 | 被动 | 主动 |
|---|---|---|
| 经商立牌 parunan | 每 60 秒星光+1 | 星光→星币兑换 + 掷骰效果 |
| 扫地机立牌 jasmine | 每移动 300 米交替 +攻/防（上限20） | 清扫效果 + 掷骰增益 |
| 护法立牌 misaki | 赐福累计层数，爆发期间名刀下限提高 | 爆发 60 秒，3 层送名刀卡 |
| 看板立牌 mimi | 星光满自动兑换星币 | 随机变化已插卡牌+插入新卡 |
| 史莱姆立牌 lulu | 受击+1 治愈点、主动冷却-10s | +3 治愈 + 范围治疗/缓慢 |
| 忍者立牌 komachi | 每 3 张效果牌复制最后一张 | 临时出牌数+1 |
| 上班族立牌 padman | 每 60 秒随机攻防(-2~4) | 攻防取最大值 4 |
| 大侦探立牌 fanny | — | 随机事件（12 种，含调查阶段） |
| 调查员立牌 rin | 事件触发→发活体书页 | 立即获得活体书页 |
| 占星师立牌 haiqing | 骰 6 得星币；击杀虚弱印记目标得星币+命运指引 | 虚弱印记 5:00（等待释放） |
| 吸血鬼立牌 papara | 半血以下攻防+3 | 嘬一口 3:00（吸血） |
| 秘密侦探立牌 bonnie | 攻击标记目标+3攻；击杀标记目标得战斗牌 | 隐匿调查（等待释放） |

#### (4) 筹码系统（14 个，chip 栏，需佩戴骰子）
手电筒-强光 / 美工刀-初级(满血+2+治愈) / 美工刀-锋利(+4) / 普通瞄具 / 鹰眼瞄具 / 医疗箱-紧急治疗(+2治愈) / 医疗箱-完备治疗(+6) / 标靶(攻击+1，赐福后标记) / 标记喷灌(法伤后标记) / 八面骰(掷8得星币) / 魔法秘典(每3张复制) / 大背包(出牌+1) / 忍术飞镖(出牌+1) / 手持风扇-大(主动后发牌+范围标记)

#### (5) 战斗计算（骰神赐福）——近期模块化重构
- 触发：玩家近战攻击 + 佩戴骰子 → 掷 1d6（护法爆发/上班族修正）→ 获"骰神赐福"效果
- 攻击力 = 玩家攻击力属性 + 骰点 + 攻击卡掷骰总和 + 修饰器（效果类：王之力/狂暴/力量）+ 立牌/筹码加成
- 防御力 = 目标防御（自身立牌/筹码/效果/防御卡掷骰）
- **外部伤害因子注册表（DiceCombatFactor）**：接管七咒之戒实际倍率、命运指引等外部影响（可扩展）
- 判定滞后：虚弱印记倍率已并入骰战结算，避免同优先级覆盖；骰战攻击由 Pre(LOWEST) 最终接管
- 输出：红色跳数字（骰战）、草绿色跳数字（法伤加成）

#### (6) 法伤模块（SpellDamageRegistry）——模块化重构
- 作用域白名单：原生箭矢/三叉戟/投掷物、原版魔法、新生魔艺、诡厄巫法、Iron 法术（精确伤害类型）
- 军火黑名单保险：排除 tacz/维克斯/卓越前线/气动工艺/机械动力火炮/通用机械武器/沉浸工程等
- 加成来源：伤害效果牌（激光/板砖/轨道炮/定向爆破/活体书页）+ 立牌/筹码（标记喷灌等）
- 通过 `SpellDamageModifier` 修饰器注册表扩展

#### (7) 流派（点数）系统——模块化
- `PlayerResource` 统一接口 + `PlayerResourceRegistry` 注册表
- 治愈（效果器，30 秒结算衰减，上限=最大生命值，效果等级=点数）
- 星光（固定不衰减，上限 32，2:1 兑换星币）
- 标记（目标侧效果器，层数上限 16，60 秒衰减）
- COUNTER（反击）流派预留接口

#### (8) 事件系统
- `AstralEventSystem.trigger`：收集目标（范围/队伍/女仆）→ 事件效果 → 立牌增益
- 内置事件：random_buff、reduce_armor（扫地机 -30% 护甲）
- 调查阶段事件：I/II/III/真相揭露（击杀隐匿调查目标推进；大侦探可抽取）

#### (9) 资源获取体系
- 星币：怪物掉落（5% 箱子/9% 末地城）、骰子/立牌被动
- 星盘：**战利品体系**（怪物 1%、Boss 必掉、宝箱 1%/5%，loot_modifiers 18 个），无合成配方
- 黄金星盘：金色品质
- 合成配方：骰子（基础+升级）、战斗牌/效果牌、空白立牌/筹码、立牌（按强度分级 T1~T4，含空白立牌与骰子系列）、筹码（含空白筹码）

---

## 三、模组术语表（开发沟通专用）

| 术语 | 含义 |
|---|---|
| **攻击力（Attack Power）** | 骰神赐福中玩家/怪物的伤害加成点数，受玩家卡牌、立牌、筹码、事件影响。基础=攻击属性，叠加骰点+攻击卡+效果+饰品 |
| **防御力（Defense Power）** | 骰神赐福中目标侧的防御加成点数，同样受卡牌/立牌/筹码/事件影响；怪物防御=0 |
| **骰神赐福（Dice Blessing）** | 核心战斗机制：玩家佩戴骰子近战攻击时掷 1d6 触发，攻击力 vs 防御力对垒决定伤害（模仿《吉星派对》攻防对垒） |
| **法伤模块（Spell Damage）** | 远程/魔法伤害加成体系，类似"无骰子的骰神赐福"；作用域=弓弩/三叉戟/魔法类模组法术，排除军火类 |
| **立牌（Sign）** | stand 栏 Curios 饰品，12 个角色，各有被动 + J 键主动技能（180 秒冷却）；等待类技能（占星师/秘密侦探）需先激活再攻击释放 |
| **筹码（Chip）** | chip 栏 Curios 饰品，14 个，被动增益为主；需佩戴骰子才开放槽位 |
| **骰子（Dice）** | dice 栏 Curios 饰品，4 阶升级；提供卡牌栏（插战斗牌）与动态立牌/筹码槽位；可 3 星升级 |
| **战斗牌（Battle Card）** | 攻击牌 + 防御牌，插入骰子卡牌栏，消耗耐久，掷骰加成攻防 |
| **效果牌（Effect Card）** | 手持使用的卡牌；分功能效果牌（buff）与伤害效果牌（法伤加成）；有出牌周期/冷却/出牌锁 |
| **专属牌（Exclusive Card）** | 绑定获得者的卡牌（活体书页/命运的指引），随机发放强制排除，他人不可用 |
| **流派（Resource）** | 玩家点数体系：治愈（衰减）、星光（固定）、标记（目标侧）；可扩展（预留反击） |
| **治愈点（Healing Points）** | 史莱姆立牌/医疗箱/美工刀联动点数，30 秒结算回血+减半 |
| **星光（Starlight）** | 经商立牌/骰子等产出的固定点数，2:1 兑换星币 |
| **标记（Mark）** | 目标侧层数效果（≤16 层），增强对标记目标的攻击（秘密侦探+3 攻/击杀奖励） |
| **星币（Star Coin）** | 通用货币：升级骰子/立牌星级、合成材料 |
| **星盘/黄金星盘（Star Plate）** | 高级合成材料，仅战利品获取 |
| **出牌数/出牌周期** | 效果牌轮询制：基础 1 张 + 固定/临时加成；出牌开始 30 秒冷却 |
| **事件（Event）** | 大侦探立牌随机触发/调查阶段等；作用范围、队伍、女仆 |
| **调查阶段（Investigation）** | 秘密侦探隐匿调查击杀后推进的 I/II/III/真相揭露阶段，给予隐身+增益 |

---

## 四、Minecraft 1.21.1 NeoForge + KubeJS 知识库（本项目应用要点）

> 📚 **完整知识库文件已单独交付**：`F:\MCProject\astra_dice\docs\neoforge-1.21.1-knowledge-base.md`（1074 行，14 章 + 1.21.1↔1.20.x 差异速查表）
> 该文件由知识调研子代理编写，**所有 Curios / KubeJS 结论均通过对本项目 Gradle 缓存中实际依赖 jar 的 `javap` 反编译与源码阅读逐条验证**（权威性高于网络教程）。
> 关键验证结论：Curios 9.x 中 `CurioEquipEvent/CurioUnequipEvent` 已不存在（被 `CurioCanEquipEvent/CurioCanUnequipEvent` 取代）；KubeJS 核心无 Curios 绑定（需自写 KubeJSPlugin 或 Java.loadClass 绕过 ClassFilter）。

### 4.1 NeoForge 1.21.1 核心 API

- **事件系统**：`@EventBusSubscriber(modid, bus=MOD/FORGE)` + `@SubscribeEvent(priority=LOWEST)`；Mod 事件（注册类）注册到 MOD 总线，游戏事件注册到 `NeoForge.EVENT_BUS`。本项目用 `EventPriority.LOWEST` 实现"判定滞后"（骰战最终伤害覆盖在护甲计算后）
- **Data Component（数据组件）**：1.21 取代旧 NBT 强化。`DeferredRegister.DataComponents` + `ComponentType`，需 `persistent(Codec)` + `networkSynchronized(StreamCodec)`。项目内 `WeaponEnhancement`/`AppliedStone`/卡牌耐久/立牌状态均用组件存储
- **Attachment（附件）**：`DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES)` + `AttachmentType.builder(...).serialize(Codec).sync(ByteBufCodecs)`；玩家级数据（星光/治愈/冷却/阶段）全部用 attachment 而非 NBT
- **Curios API（1.21.1，Curios 9.x）**：物品实现 `ICurioItem` 接口 + `CuriosApi.registerCurio`（或继承自动注册）；槽位用 `data/astral_dice/curios/slots/*.json` + `entities/player.json` 定义；物品标签 `data/curios/tags/item/*.json` 供 `curios:tag` 验证器校验；API 遍历 `CuriosApi.getCuriosInventory(player).get().findFirstCurio(s -> s.is(item))`；事件 `CurioChangeEvent`（`top.theillusivec4.curios.api.event.CurioChangeEvent`）
- **网络（Payload）**：`CustomPacketPayload` record + `StreamCodec` + `RegisterPayloadHandlersEvent` 的 `PayloadRegistrar.playToClient/playToServer`
- **菜单/屏幕**：`AbstractContainerMenu` + `MenuType`（`DeferredRegister.create(Registries.MENU)`）+ `RegisterMenuScreensEvent` 注册 Screen；客户端通过 `SimpleMenuProvider` 打开
- **伤害事件链**：`LivingIncomingDamageEvent`（最早，含 originalAmount）→ `LivingAttackEvent` → `LivingHurtEvent` → `LivingDamageEvent.Pre/Post`（Pre 可 setNewDamage，在护甲吸收后）；`LivingDeathEvent` 收尾。本项目在 Incoming 捕获七咒实际倍率、Pre(LOWEST) 接管骰战最终伤害
- **ModDevGradle 2.x**：`neoForge { version; parchment; runs { client/server/gameTestServer } }`；`runClient` 支持 `-Pquickplay` 快速进世界、`--width/--height` 固定窗口；`net.neoforged.moddev` 插件自动生成运行配置
- **数据生成**：`GatherDataEvent` + `ItemModelProvider`/`RecipeProvider`（`ModRecipeProvider extends RecipeProvider implements IConditionBuilder`），输出 `src/generated/resources`
- **战利品**：`GlobalLootModifier`（`neoforge:add_table` + `loot_modifiers/global_loot_modifiers.json`）+ `LootTableLoadEvent` 注入宝箱池
- **伤害类型**：`Registries.DAMAGE_TYPE` + `data/astral_dice/damage_type/dice_damage.json`（scaling/exhaustion）
- 参考：https://docs.neoforged.net/ ｜ https://github.com/neoforged/NeoForge/pull/1163（Attachment Codec）｜ https://docs.neoforged.net/docs/1.20.6/datastorage/attachments/ ｜ https://lexxie.dev/neoforge/1.21.1/net/neoforged/neoforge/event/entity/living/LivingDamageEvent.html

### 4.2 Curios API 参考

- 创建饰品：https://docs.illusivesoulworks.com/curios/items/curio-creation
- Curios 9.5.0+1.21.1（Modrinth）：https://modrinth.com/mod/curios/version/9.5.0+1.21.1
- 源码（1.21.1 分支）：https://github.com/SSKirillSS/Curios/blob/1.21.1/README.md

### 4.3 KubeJS 2101.x（1.21.1）知识

- 脚本目录：`kubejs/startup_scripts`、`server_scripts`、`client_scripts`
- 常用事件对象：`ServerEvents.commandRegistry`、`PlayerEvents`、`ItemEvents`、`EntityEvents`
- **要点（本项目实测教训）**：
  1. `/kubejs reload server-scripts` **只重载脚本文件、不更新命令闭包**，必须再执行 `/reload` 才重新注册命令
  2. KubeJS 2101 中事件对象名为 `ForgeEvents`/`NeoForgeEvents` **均不存在**，应使用 KubeJS 自带的 `ServerEvents.*` 等，或 `Java.loadClass` 加载 Java 类
  3. Rhino 中 `const` 为函数作用域，循环体内重复声明会报错（用 `var`）
  4. `sendSuccess` 的 Supplier 参数转换不可靠，用 `sendFailure(Component)` 直传
  5. 无内置 CuriosHelper，用 `Java.loadClass("top.theillusivec4.curios.api.CuriosApi")` 访问；更规范做法是自写 KubeJSPlugin 注册 binding + ClassFilter 放行
- 事件 wiki：https://kubejs.com/wiki/events ｜ https://kubejs.com/wiki/events/PlayerEvents
- **完整知识库见** `docs/neoforge-1.21.1-knowledge-base.md` 第 13 章（事件表、Curios 绑定方案 A/B/C、KJS6→7 差异）

---

## 五、开发历史（对话记录摘要）

> 对话文件 `E:\Downloads\minecraft-astral_dice.json`：1,730 条消息（93 用户 / 1,637 助手），约 17 万行、17MB。提取正文 15,433 行完整阅读。

### 5.1 时间线（按用户指令）

1. **初期**：分析项目 → 建立自动化测试（world 准备 / 命令注入 / 日志监控）
2. **KubeJS 排障**：槽位检查脚本 missing 误报排查（reload 机制、命令闭包不更新、ForgeEvents 不存在）
3. **Bug 修复轮**：
   - 史莱姆立牌被动重写为"受到伤害时"+ 冷却 -10s
   - **饰品弹出 bug**：定位为 `hasSameItemEquipped` 自匹配 + 槽位强制收缩 → 引用比较排除自身 + 防御式收缩修复；10 轮击杀测试验证通过
   - 秘密侦探隐匿调查重复触发修复（释放者击杀仅触发一次）
   - 星盘/黄金星盘品质修正；治愈效果可视化（新"治愈"效果，等级=点数）
   - 所有饰品下蹲右键自动装备；禁止重复装备
4. **测试基建迭代**：1920x1080 窗口、keepInventory、简单难度、IMBlocker 输入法模组、Superflat World No Slimes 模组（删除史莱姆清理）、/time set 0、game-test.md 全流程
5. **功能开发**：
   - 美工刀-锋利筹码、医疗箱装备即加成、跳数字 HUD（骰战红/法伤草绿）
   - 伤害效果牌：对怪激光/板砖/轨道炮/定向爆破/活体书页
   - 筹码：标记喷灌、魔法秘典、大背包、忍术飞镖、手持风扇-大
   - 忍者立牌/魔法秘典计数复制、吸血鬼立牌图标、护法立牌神秘遗物+联动
   - tooltip key 统一重命名（board→mimi 等 6 组）
6. **战斗系统重构（近期）**：
   - 用户指出骰战判定顺序隐患（护甲双算、优先级竞争）→ 方案审核 → 执行"方案2 + 接管外部伤害影响"
   - **combat 包模块化**：DiceCombatContext / 攻防修饰器注册表 / DiceCombatFactor 外部因子
   - **法伤模块**：SpellDamageRegistry 白名单作用域 + 修饰器注册表
   - **流派模块化**：PlayerResource 接口 + 注册表（治愈/星光/标记，预留反击）
   - **效果牌出牌周期**：冷却与效果判定分离、出牌锁
   - RandomCardHandler 随机发牌（类别/专属排除/作用域）
7. **配方与战利品**：立牌配方按强度分级（T1~T4，含骰子系列）；星盘改战利品获取；移除全力攻击配方（蓄力返还机制）；骰子升级配方继承配置

### 5.2 关键设计决策（从对话提取）

- 治愈点数从"立牌组件"重构为"玩家级 HealingManager"（解耦，防装卸丢失）
- 饰品的 onUnequip 重载（from=to 同物品）不应清数据——只在真正离开槽位时清理
- 骰战伤害由 Pre(LOWEST) 接管 = "判定滞后"：规避整合包战斗模组覆盖
- 七咒倍率不固化：在 IncomingDamageEvent 捕获模组计算后的实际倍率，骰战最终伤害使用（可扩展因子注册表）
- 法伤模块统一"效果显示 + 作用域判定 + 修饰器加成"三层结构

---

## 六、当前实现状态与待办

### 6.1 完成度评估

| 系统 | 状态 |
|---|---|
| 骰子 4 阶 + 卡牌栏 GUI + 动态槽位 | ✅ 完整 |
| 战斗牌（7 攻 + 3 防） | ✅ 完整（含耐久/升级/掷骰） |
| 效果牌（功能 3 + 伤害 5 + 专属 2） | ✅ 完整（出牌周期/冷却/锁） |
| 12 立牌 | ✅ 完整（被动/主动/等待释放/调查阶段） |
| 14 筹码 | ✅ 完整 |
| 骰战计算（模块化修饰器） | ✅ 重构完成 |
| 法伤模块（白名单 + 修饰器） | ✅ 重构完成 |
| 流派系统（PlayerResource） | ✅ 重构完成 |
| 事件系统 | ✅ 基本完整 |
| 资源获取（星币/星盘战利品/配方） | ✅ 完整 |
| 联动（Iron/神秘遗物+/FTB/OPAC/女仆/农夫乐事） | ✅ 完成 |
| 自动化测试 | ✅ GameTest 3/3 + 击杀测试 10/10 |
| **git 版本控制** | ❌ **无任何提交** |

### 6.2 已知问题与待办（重要）

1. **⚠️ 疑似缺陷（子代理分析发现）**：`src/main/resources/data/curios/tags/item/chip.json` 只收录 **8 个筹码**，缺少后加的 6 个（美工刀-锋利 cutter_blade_chip、标记喷灌 marker_sprayer、魔法秘典 magic_tome、大背包 big_backpack、忍术飞镖 ninja_star、手持风扇-大 hand_fan_big）。Curios 的 `curios:tag` 验证器会阻止这 6 个筹码通过 **Curios GUI 拖拽装备**（`/curios replace` 命令与 mod 自带的下蹲右键自动装备可绕过，所以测试未暴露）。**立牌 12/12、骰子 4/4 标签完整。**
2. **唯一残留 TODO**：`AstralEventSystem.applySignBuffs` 第 69 行仅实现大侦探 +3 星币，"其他立牌增益"未实现
3. **技术债**：`ModEventHandlers` 1765 行上帝类；注释自认立牌/筹码加成应逐个迁移为修饰器但未完成
4. `AstralDiceMod.registerCurio` 列表冗余（物品实现 ICurioItem 后无需逐条注册）且未含 9 个新物品（无实际 bug）
5. `ModClientConfig` 已空壳（可清理或删除）
6. `ResourceType.COUNTER`、未来专属战斗牌（撕咬/龙之咆哮——蛟龙立牌设计）为预留占位
7. 开发未纳入 git：**接手后应尽快建立基线提交**（对话中用户也提到"修改前先 git commit 以便回滚"）
8. 对话最后停在"立牌配方改为包含整个骰子系列"——代码中已实现（T2~T4 立牌分别用 基础/黄金/钻石/合金骰子）

### 6.3 后续开发建议（供参考）

- **立即修复 chip.json 标签**（6 个缺失筹码），并补充 datagen 或手工维护使标签与 ModItems 同步
- 将 `ModEventHandlers` 按领域拆分：骰战结算 → combat 包、受击/击杀 → event、tooltip → item
- 完成立牌/筹码加成向修饰器注册表的迁移（先攻防、再法伤）
- 建立 git 基线 + 提交规范；重要改动前先 commit 便于回滚
- 参考对话中预留的"蛟龙立牌"设计（撕咬/龙之咆哮专属战斗牌）接入 RandomCardHandler 专属池
- 未来接入"反击"流派：实现 PlayerResource 并注册到 PlayerResourceRegistry
