# P1 — §C 冷却与计时器(第二轮只读审计)

> 范围:仓 `F:\MCProject\astral_dice_multiloader`,分支 `multi-1.20.1-1.21.1`,双版本 `neoforge-1.21.1`(下称 **A**) + `forge-1.20.1`(下称 **B**)。
> 本文件是本次唯一允许写入的文件;未修改任何源码/资源/配置。
> 上游清单:`docs/execution-order-checklist.md` §C 全项 + §B2/B4 冷却减免基准/取整条目。结论格式见该文档 §I。
> 首轮扫描:`docs/interaction-audit-1.2.1.md`、切片 `docs/audit-slices/S6-sign-active-dice.md`。

---

## 0. 仓库状态(读取时刻快照)

```text
$ git rev-parse HEAD
d48a529ba5fcf14610dc7b7383a10780dbe49168

$ git rev-parse --abbrev-ref HEAD
multi-1.20.1-1.21.1

$ git diff --name-only          # 第一次采样(本次会话开始,~T0)
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerLifecycleHandler.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/resource/ResourceConversion.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerLifecycleHandler.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/resource/ResourceConversion.java

$ git diff --name-only          # 第二次采样(审计进行中,~T1;另一批代理正在落改动)
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerLifecycleHandler.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/CurioSlotUtil.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/card/EffectCardPeriod.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java
forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/resource/ResourceConversion.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/event/PlayerLifecycleHandler.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/CurioSlotUtil.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/card/EffectCardPeriod.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/BonnieSignItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/HaiqingSignItem.java
neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/resource/ResourceConversion.java

$ git status --porcelain          # 未跟踪(与本次无关,属 B6 伤害标签那批)
?? forge-1.20.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json
?? neoforge-1.21.1/src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json
```

**结论**:HEAD 在两次采样间**未变**(仍是 `d48a529`),但工作区文件集合在审计进行中**扩张**,证明另一批代理正在**实时写入**。下列文件处于改动中(A/B 各自标注):

| 文件 | 状态 | 与本任务关系 |
|---|---|---|
| `item/sign/BaseSignItem.java` | **改动中(A+B)** | 立牌主动"起冷却闸"正被改写(状态/计时器分离,`getSignReadyType() <= 0`);新增 `tickSignReadyTimeout` |
| `item/card/EffectCardPeriod.java` | **改动中(A+B)** | `tick` 新增"未打满轮次也进入 30 秒冷却"分支(见 P06) |
| `event/PlayerTickEvents.java` | **改动中(A+B)** | 新增 `BaseSignItem.tickSignReadyTimeout(player)` 接线(A :118 / B :117) |
| `item/sign/{MosesSignItem,BonnieSignItem,HaiqingSignItem}.java` | **改动中** | 删除各自 `onCurioTick` 里的重复超时块(见 P05);**§2.3 引用的 `MosesSignItem.signCooldownTicks`(:117-125 / B :122-130)未变动**(diff 仅删超时块) |
| `combat/DiceCombatEvents.java` | **改动中(A+B)** | diff 仅落在其他 hunk;**§2.1 引用的三处待命释放冷却写入(:234/:255/:270 等)未变动** |
| `combat/{DiceCombatModifiers,HostileTargets}.java`、`event/EnderDiceHandler.java`、`item/chip/BaseChipItem.java`、`item/dice/{DiceCurioItem,NetherStarDiceItem}.java` | 改动中(A+B) | 属那批 13 项改动;`EnderDiceHandler.java` 的图腾冷却常量/写入未变(见 §2.10) |
| `item/CurioSlotUtil.java` | 改动中(A+B) | 间接(立牌卸载清理) |
| `event/PlayerLifecycleHandler.java` | 改动中(A+B) | `LivingDeathEvent` 优先级改 `LOWEST` |
| `resource/ResourceConversion.java` | 改动中(A+B) | 无关(冷却链路仅被 `BufferShieldChipItem` 调用发币) |

```text
$ git diff --name-only   # 第三次采样(~T2,共 32 个文件;仅列与会话相关的)
forge-1.20.1  .../combat/DiceCombatEvents.java, combat/DiceCombatModifiers.java, combat/HostileTargets.java,
              .../event/EnderDiceHandler.java, event/PlayerLifecycleHandler.java, event/PlayerTickEvents.java,
              .../item/CurioSlotUtil.java, item/card/EffectCardPeriod.java, item/chip/BaseChipItem.java,
              .../item/dice/DiceCurioItem.java, item/dice/NetherStarDiceItem.java, item/sign/BaseSignItem.java,
              .../item/sign/BonnieSignItem.java, item/sign/HaiqingSignItem.java, item/sign/MosesSignItem.java,
              .../resource/ResourceConversion.java
neoforge-1.21.1  (与 forge 侧同名的 16 个文件)
```

**关键**:`event/WeirdDiceHandler.java`、`item/sign/MosesSignItem.java` 的 `signCooldownTicks`、`item/card/FateGuidanceCardItem.java`、`item/sign/JasmineSignItem.java`、`item/sign/LuluSignItem.java`、`item/sign/KomachiSignItem.java`、`item/ChargeManager.java`、`component/GameplayConstants.java`、`item/chip/{RailgunChipItem,AirbagChipItem,BufferShieldChipItem,MagicQuiverChipItem,WarpEngineChipItem,SatelliteChipItem,CurrentCoreChipItem,StarCoinHammerChipItem}.java`、`item/EmpowerManager.java` **均不在改动清单内** —— 即本任务的核心结论(枪匠三问 + 减免基准/取整 + 各载体清单)**取自干净工作树**,不受那批 13 项改动污染。仅 §C1/§C4(P03/C-C4/P05)会随后续 `BaseSignItem`/`EffectCardPeriod`/`PlayerTickEvents` 最终形态而变,已逐条标注并在 §2.6 给出结论反转记录。

---

## 1. 领域表(参与方 → 写入载体 → 基准来源)

| 载体(附件/数据键) | 作用域 | 写入方数量 | "最大冷却"来源 | 减免口径 |
|---|---|---|---|---|
| `sign_active_cooldown_end` | 玩家级 | **8** 个写入点(见 §2.1) | **两条并列函数** `WeirdDiceHandler.signCooldownTicks`(180s 基准) 与 `MosesSignItem.signCooldownTicks`(120s 基准) | 三种混用:最大÷2 / 常量÷2 / 剩余×0.7 / 绝对−200t / 剩余占比换充能 |
| `effect_card_cooldown_end` | 玩家级 | 4 个写入点 | 单一常量 `EFFECT_CARD_COOLDOWN_SECONDS*20 = 600t` 经 `ChargeManager.cooldownTicks` | 起冷却时一次性算定;无"剩余/最大"减免入口 |
| `sign_ready_type` / `sign_ready_expire` | 玩家级 | type:3 写入点;expire:5 写入点 | 常量 `SKILL_WAIT_SECONDS*20 = 600t` | 无减免(固定 30s) |
| `railgun_cooldown_end` | 玩家级 | 1 | 常量 `COOLDOWN_TICKS = 1200t` | 无减免 |
| `airbag_cooldown_end` | 玩家级 | 1 | 常量 `1200t` | 无减免 |
| `buffer_shield_cooldown_end` | 玩家级 | 1 | 常量 `300t` | 无减免 |
| `magic_quiver_cooldown_end` | 玩家级 | 1(+死亡清 0) | 常量 `1200t` | 无减免 |
| `warp_engine_portal_cooldown_end` | 玩家级 | 1 | 常量 `6000t` | 无减免 |
| `ender_die_totem_cooldown_end` | 玩家级 | 1 | 常量 `TOTEM_COOLDOWN_TICKS` | 无减免 |
| `satellite_give_cooldown_end` / `satellite_play_bonus_cooldown_end` | 玩家级 | 各 1 | 常量 `1200t` / `1200t` | 无减免 |
| `empower_decay_at` | 玩家级 | 4 | 常量 `EmpowerEffect.DECAY_INTERVAL_TICKS` | 无减免(递减计时器) |
| `star_coin_hammer_bonus` | 玩家级 | 2 | —(不是计时器,是赐福期加成) | — |
| `flashlight_granted_targets` | 玩家级 | 3 | —(**不是计时器**:是"同一目标仅 +1"去重记录) | — |
| 战斗牌耐久 `CARD_USES` / `WEAPON_ENHANCEMENT` | 物品级 | 3 个消耗点 | `AppliedStone.defaultUses` | 与出牌轮冷却**无联动**(见 P20) |

---

## 2. §C 逐项结论

### 2.1 §C1 — "开始冷却"入口是否唯一(证伪"唯一",实测 8 个写入点)

```text
ID: C-C1
核验: 修正(清单原记 7 个写入方,实测为 8 个写入点;且其中 5 个可被"减免"二次改写,不是纯"起点")
现象: 同一个玩家级冷却值取决于"走了哪条路进入冷却"以及"之后被谁减免过";不存在单一权威的"本次最大冷却"记录。
顺序依赖: `BaseSignItem.performSkill`(立牌栏按键)与 `DiceCombatEvents`(待命释放)是两个互斥入口 —— 这是有契约的(待命类走后者);但五个"减免方"与起点之间**无任何契约**,任意减免方都能在任意时刻改写同一个 `long`,后写者无条件覆盖前者。
证据:
A(neoforge-1.21.1)
  [1] 立牌栏按键起冷却: item/sign/BaseSignItem.java:119-125
      if (ModAttachments.getSignReadyType(player) <= 0) {
          // 诡异骰子:立牌主动冷却 -50%
          ModAttachments.setSignActiveCooldownEnd(player,
                  now + com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player));
          // 电流核心筹码:主动技能实际生效时充能 +1
          com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.onActiveSkillUsed(player);
      }
      (注:该文件【改动中】;T0 采样时此处为 `if (ModAttachments.getSignReadyExpire(player) <= 0)`,行号 112-118)
  [2] 待命释放·占星师: combat/DiceCombatEvents.java:238-239
      ModAttachments.setSignActiveCooldownEnd(player,
              player.level().getGameTime() + WeirdDiceHandler.signCooldownTicks(player));
  [3] 待命释放·秘密侦探: combat/DiceCombatEvents.java:259-260(同上表达式)
  [4] 待命释放·枪匠: combat/DiceCombatEvents.java:273-274
      ModAttachments.setSignActiveCooldownEnd(player,
              player.level().getGameTime() + MosesSignItem.signCooldownTicks(player));
  [5] 命运指引减免: item/card/FateGuidanceCardItem.java:69
      ModAttachments.setSignActiveCooldownEnd(player, now + Math.max(0, remaining - reduction));
  [6] 忍者被动减免: item/sign/KomachiSignItem.java:138
      ModAttachments.setSignActiveCooldownEnd(player, now + (long) (remaining * 0.7));
  [7] 扫地机被动减免: item/sign/JasmineSignItem.java:89-90
      ModAttachments.setSignActiveCooldownEnd(player,
              Math.max(now, cdEnd - GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS / 2));
  [8] 史莱姆受击减免: item/sign/LuluSignItem.java:54-55
      ModAttachments.setSignActiveCooldownEnd(player,
              Math.max(0, cdEnd - 200));
  [9] 电流核心清零: item/chip/CurrentCoreChipItem.java:92
      ModAttachments.setSignActiveCooldownEnd(player, now);
B(forge-1.20.1) —— 逐条同构,仅行号偏移:
  [1] item/sign/BaseSignItem.java:119-125(内容与 A 逐字相同;【改动中】)
  [2] combat/DiceCombatEvents.java:234-235  [3] :255-256  [4] :270-271(【改动中】,但这三处未变)
  [5] item/card/FateGuidanceCardItem.java:68  [6] item/sign/KomachiSignItem.java:139
  [7] item/sign/JasmineSignItem.java:90-91    [8] item/sign/LuluSignItem.java:54-55
  [9] item/chip/CurrentCoreChipItem.java:92
可达性: 全部可达。[5][6][7][8] 只要求"cdEnd 大于 0(或大于 now)",不要求该冷却属于自己这枚立牌 —— 即**任意立牌起冷却、任意另一件装备来减免**。
修法: 见 §7 "统一修法建议"(C-C2a/C-C2b/P01/P02 共用)。核心是给玩家附件补一个"本次冷却的最终最大 tick"(起冷却时写死),所有减免方只读它。
```

### 2.2 §C2 — "最大值"是否取自同一函数(**证实两条并列函数**)

```text
ID: C-C2
核验: 证实
现象: 仓库里同时存在两个"立牌主动最大冷却"计算函数,消费方各取一条,导致"实际写入的冷却"与"减免基准"是两套数。
顺序依赖: 无契约。`MosesSignItem.signCooldownTicks` 只在 `DiceCombatEvents` 枪匠分支被用;`WeirdDiceHandler.signCooldownTicks` 被 5 个地方用(BaseSignItem 起冷却、占星师/秘密侦探释放、命运指引基准、以及 MosesSignItem 内部再调用)。
证据:
A(neoforge-1.21.1)
  函数①(180s 基准): event/WeirdDiceHandler.java:35-42
      /** 立牌主动技能冷却 tick:佩戴诡异骰子时减半;拥有充能时再 -20% */
      public static int signCooldownTicks(Player player) {
          int ticks = GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
          if (hasWeirdDice(player)) {
              ticks = Math.max(1, ticks / 2);
          }
          return (int) ChargeManager.cooldownTicks(player, ticks);
      }
  函数②(120s 基准): item/sign/MosesSignItem.java:114-125
      public static int signCooldownTicks(Player player) {
          int ticks = isEquipped(player)
                  ? ACTIVE_COOLDOWN_SECONDS * 20
                  : GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
          if (WeirdDiceHandler.hasWeirdDice(player)) {
              ticks = Math.max(1, ticks / 2);
          }
          return (int) ChargeManager.cooldownTicks(player, ticks);
      }
      (常量: item/sign/MosesSignItem.java:37-38 `public static final int ACTIVE_COOLDOWN_SECONDS = 120;`)
  基准常量: component/GameplayConstants.java:42-45
      public static int SIGN_ACTIVE_COOLDOWN_SECONDS = 180;
      public static int SIGN_ACTIVE_COOLDOWN_TICKS = SIGN_ACTIVE_COOLDOWN_SECONDS * 20;
B(forge-1.20.1) —— 同构:
  函数① event/WeirdDiceHandler.java:35-42(逐字相同)
  函数② item/sign/MosesSignItem.java:119-130;常量 :40-41 `ACTIVE_COOLDOWN_SECONDS = 120`
  基准常量 component/GameplayConstants.java:42-45
可达性: 两函数都可达。函数②的 `isEquipped(player)` 在枪匠分支里恒为 true(调用点已被 `mosesResult.isPresent()` 保护),故实际恒取 120s。
修法: 合并为单一入口(建议 `ChargeManager`/新 `SignCooldown` 类暴露 `maxTicks(player, sign)`),或给 `BaseSignItem` 加 `protected int activeCooldownBaseTicks()` 由子类覆写 —— 枪匠覆写为 120s,其余走默认 180s。**必须先由用户定夺"减免按什么基准"**,否则同一 bug 会以另一种形态重生(见 §7 统一修法建议)。
```

### 2.3 §C2 补充 — 枪匠三问逐条判定 + token 级数值

> 口径说明:`ChargeManager.cooldownTicks` 在 `baseTicks > 1` 且 `hasCharge` 时返回 `max(1, ceil(base*0.8))`;A 侧写入用 `Math.max(1, ticks/2)` 先对诡异骰子取整,B 侧相同。

```text
ID: C-C2a 【枪匠三问·①:命运指引取"通用最大值" ⇒ 过度减免】
核验: 证实(且实际后果比"过度减免"更重 —— 会出现"归零")
现象: 玩家在佩戴枪匠(实际最大 120s)时使用「命运的指引」,减免量按 180s÷2 = **1800t = 90s** 算;120s(2400t)冷却被一次减免 90s ⇒ 只剩 30s。若同时佩戴诡异骰子(实际最大 60s / 1200t),一次减免 90s ≥ 1200t ⇒ `Math.max(0, remaining - reduction)` 把剩余夹到 0 ⇒ **冷却立即完成**。
顺序依赖: `FateGuidanceCardItem.applyEffect`(:49-58)在**效果牌出牌流程内**执行;`reduceActiveSkillCooldown` 只读当前 `cdEnd`,不看"本次冷却是谁起的、基准多大"。反转(先减后起)不可能:起冷却写入的是 `now + X`,减免写入的是 `now + (remaining - reduction)`,后写者覆盖。
证据:
A(neoforge-1.21.1) item/card/FateGuidanceCardItem.java:60-71
      // 主动技能冷却时间减半(实时功能):冷却中则从剩余时间中扣除「当前生效的最大冷却时长」的 50%
      // (减少量 = 当前最大冷却 ÷ 2,例:180 秒冷却一次减少 90 秒;玩家级冷却,不受立牌装卸影响)
      private static void reduceActiveSkillCooldown(Player player) {
          long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
          if (cdEnd > 0) {
              long now = player.level().getGameTime();
              long remaining = cdEnd - now;
              long maxCooldown = com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player);
              long reduction = maxCooldown / 2;
              ModAttachments.setSignActiveCooldownEnd(player, now + Math.max(0, remaining - reduction));
          }
      }
B(forge-1.20.1) item/card/FateGuidanceCardItem.java:59-70 —— 逐字同构(行号 −1)。
数值表(减免量 = `WeirdDiceHandler.signCooldownTicks(player)/2`,与"用哪枚立牌起的冷却"无关):
  装备(减免那一刻)                  | 函数①值 | 减免量   | 枪匠实际冷却 | 减免后剩余        | 结论
  --------------------------------|--------|---------|------------|-----------------|------------------
  仅枪匠                           | 3600t  | 1800t   | 2400t(120s)| 2400−1800=600t(30s)| 过度减免(应为 60s)
  枪匠 + 诡异骰子                   | 1800t  | 900t    | 1200t(60s) | 1200−900=300t(15s) | 过度减免(应为 30s)
  枪匠 + 诡异 + 充能                | 1440t  | 720t    | 960t(48s)  | 960−720=240t(12s)  | 过度减免
  枪匠 + 诡异(冷却进行中)**卸下诡异骰子**再用命运指引 | 3600t | 1800t | 1200t(60s) | **夹到 0 = 立即完成** | 归零
  枪匠 + 诡异 + 充能,同 tick 内连用两张命运指引 | 1440t | 720t×2 | 960t | **夹到 0 = 立即完成** | 归零
可达性: 可达。「命运的指引」是调查员被动/占星师击杀发牌的专属牌(`docs/agent` 记录:仅获得者可用),实机路径 = 装备枪匠立牌 + 装备诡异骰子 → 触发一次枪匠主动并命中目标(起 60s 冷却)→ 立即使用命运的指引 ⇒ 冷却条直接消失。
最小实机验证: ① 装备枪匠立牌 + 诡异骰子 + 调查员(取命运的指引);② 按下枪匠主动 J,命中任一符合赐福条件的目标(记下"冷却中:60 秒"tooltip);③ 立刻使用命运的指引;④ 读立牌 tooltip `tooltip.astral_dice.sign.cooldown_remaining` —— 期望"冷却中:30 秒",实际会出现"冷却中:15 秒"或直接无冷却行。
修法: 起冷却时把 `maxTicks` 写入新附件 `sign_active_max_cooldown`,减免一律读它(并规定上限 = 剩余)。

ID: C-C2b 【枪匠三问·②:加急加快用常量 ⇒ 免费立即完成】
核验: 证实(且"归零"的触发面比怀疑更宽 —— 不需要枪匠)
现象: 扫地机被动「移动充能」在**任意玩家**使用「加急加快」效果牌时,从当前冷却剩余里固定扣 `SIGN_ACTIVE_COOLDOWN_TICKS/2` = **1800t = 90s**,并用 `Math.max(now, ...)` 夹底。任何"实际冷却 < 90s"的组合都会被夹到 `cdEnd = now` ⇒ **冷却立即结束(免费立即完成)**。这不只发生在"枪匠 + 诡异骰子 = 60s",下列全部组合都归零:任意立牌 + 诡异骰子(60s)、任意立牌 + 诡异 + 充能(48s)、枪匠单独(120s,剩 30s,非归零)、任意立牌 + 充能(144s,剩 54s,非归零)。
顺序依赖: `JasmineSignItem.onExpressDeliveryUsed` 由 `BaseEffectCardItem.tryUseCard` 在 `applyEffect` **之后**调用;它只要求"此刻佩戴扫地机",不要求该冷却属于扫地机,counter 也与"是谁起的冷却"无关。
证据:
A(neoforge-1.21.1) item/sign/JasmineSignItem.java:82-92
      // 使用「加急加快」效果牌后调用:立牌主动技能冷却立即减少最大冷却的 50%
      public static void onExpressDeliveryUsed(Player player) {
          if (player == null || player.level().isClientSide()) return;
          if (!isEquipped(player)) return;
          long now = player.level().getGameTime();
          long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
          if (cdEnd > now) {
              ModAttachments.setSignActiveCooldownEnd(player,
                      Math.max(now, cdEnd - GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS / 2));
          }
      }
  调用方: item/card/BaseEffectCardItem.java:233-236
      // 扫地机立牌被动:使用「加急加快」后,主动技能冷却立即减少最大冷却的 50%
      if (stack.is(ModItems.EXPRESS_DELIVERY.get())) {
          JasmineSignItem.onExpressDeliveryUsed(player);
      }
B(forge-1.20.1) item/sign/JasmineSignItem.java:83-93;item/card/BaseEffectCardItem.java:233-236 —— 逐字同构。
数值表(固定扣 1800t,夹底 now):
  当前冷却来源                        | 实际冷却 | 剩余(刚起) | 扣 1800 后 | 结果
  ----------------------------------|---------|-----------|-----------|------------------
  任意立牌(默认)                      | 3600t   | 3600t     | 1800t     | 剩 90s(正确 = 50%)
  任意立牌 + 诡异骰子                  | 1800t   | 1800t     | 0         | **归零 = 免费立即完成**(文案说 −50%,实为 −100%)
  枪匠(无诡异)                        | 2400t   | 2400t     | 600t      | 剩 30s(文案基准应是 120s÷2=60s ⇒ 少减 30s,**减免不足**)
  枪匠 + 诡异骰子                      | 1200t   | 1200t     | 0         | **归零 = 免费立即完成**
  枪匠 + 诡异 + 充能                   | 960t    | 960t      | 0         | **归零**
  任意立牌 + 充能                     | 2880t   | 2880t     | 1080t     | 剩 54s(≈50% 但基准错)
  任意立牌 + 诡异 + 充能               | 1440t   | 1440t     | 0         | **归零**
可达性: 可达。[加急加快] 是普通效果牌(可对自己右键使用),扫地机立牌是稀有品质(基础骰子配方),诡异骰子是紫品;组合门槛低。
最小实机验证: ① 装备任意立牌 + 诡异骰子 + 扫地机立牌(需 stand 槽可放?—— 见下方"不确定点"U2);② 按 J 起冷却(记 60 秒 tooltip);③ 使用「加急加快」;④ 再按 J —— 若技能**立即再次生效**,即命中。
修法: 改为 `Math.max(now, cdEnd - (读到的本次最大冷却)/2)`;或直接改成"按剩余比例"`now + remaining*0.5`。**口径需用户定夺**(文案写"最大冷却的 50%")。

ID: C-C2c 【枪匠三问·③:减免量取决于"减免那一刻"的装备】
核验: 证实(修正其范围:不止"换骰子/换立牌",还有"当前拿着哪枚立牌""是否有充能""是否佩戴扫地机")
现象: 所有减免方都只接收 `Player`,在减免发生的**当下**重新读装备:
  - `WeirdDiceHandler.signCooldownTicks(player)` 在减免时刻读 `hasWeirdDice(player)` → 冷却进行中装卸诡异骰子会改变减免量(↑或↓均可);
  - `MosesSignItem.signCooldownTicks` 在起冷却时刻读 `isEquipped` → 冷却进行中把枪匠换下/换上不改变已写入的值(这是对的),但会让**之后的减免基准**与实际值脱钩;
  - `JasmineSignItem.onExpressDeliveryUsed` 在减免时刻读 `isEquipped(player)` → 卸下扫地机后再用加急加快 = **完全无减免**;
  - `ChargeManager.cooldownTicks` 在起冷却时刻读 `hasCharge` → 先起冷却再吃充能,不减已起的冷却(方向正确);但先吃充能再起冷却则 −20%(合理)。
顺序依赖: 无契约;同一 `long` 被"装备状态"隐式驱动。反转(把基准写死)后,上述所有不一致一次性消失。
证据:
A(neoforge-1.21.1) —— 三处"减免时读装备":
  item/card/FateGuidanceCardItem.java:67  `long maxCooldown = ...WeirdDiceHandler.signCooldownTicks(player);`
  item/sign/WeirdDiceHandler.java:38       `if (hasWeirdDice(player)) { ticks = Math.max(1, ticks / 2); }`
  item/sign/JasmineSignItem.java:85        `if (!isEquipped(player)) return;`
B(forge-1.20.1) item/card/FateGuidanceCardItem.java:66;event/WeirdDiceHandler.java:38;item/sign/JasmineSignItem.java:86 —— 同构。
数值示例(token 级): 枪匠起冷 1200t(60s,配诡异) → 冷却进行中**卸下诡异骰子** → 用命运指引:减免量从 900t 变成 **1800t** ⇒ 直接归零(与 C-C2a 最后一行的"归零"是同一现象的两个触发方式)。
可达性: 可达,且"冷却中换骰子"是常规操作(Curios 骰子槽 1 格,换装即换基准)。
最小实机验证: 同 C-C2a,但在 ③ 之前先把诡异骰子摘下。
修法: 与 C-C2a/C-C2b 同一修法(把基准写进附件),不需要单独改。
```

### 2.4 §C3 — 减免口径:剩余 vs 最大 vs 常量 vs 绝对值(四种口径并存)

```text
ID: C-C3
核验: 证实(把 S6-C5 的"4 套口径"核准为 **5 套**,并补充"电流核心"是按**剩余占比**换充能)
现象: 同一个"减少冷却"语义在 5 个实现里用了 5 种基准,玩家无法从 tooltip 预测结果;只有"忍者 30%"是唯一按剩余比例的(它也是唯一不会归零的)。
顺序依赖: 各自独立读 `cdEnd`,互相覆盖;先后的组合效应不可预测(例:先命运指引再加急加快 ⇒ 双重减免叠加)。
证据:
A(neoforge-1.21.1)
  (a) 剩余 × 0.7(对): item/sign/KomachiSignItem.java:131-141
      long remaining = cdEnd - now;
      if (remaining > 0) { ModAttachments.setSignActiveCooldownEnd(player, now + (long) (remaining * 0.7)); }
  (b) 最大 ÷ 2(基准取自函数①): item/card/FateGuidanceCardItem.java:67-69(全文见 C-C2a)
  (c) 常量 ÷ 2(180s 的一半,固定 1800t): item/sign/JasmineSignItem.java:90(全文见 C-C2b)
  (d) 绝对 −200t(10 秒,与最大/剩余均无关): item/sign/LuluSignItem.java:52-56
      long cdEnd = com.merlinkitsune.astral_dice.component.ModAttachments.getSignActiveCooldownEnd(player);
      if (cdEnd > 0) {
          com.merlinkitsune.astral_dice.component.ModAttachments.setSignActiveCooldownEnd(player,
                  Math.max(0, cdEnd - 200));
      }
  (e) 剩余占比 → 充能档位(向上取整,钳 1~6): item/chip/CurrentCoreChipItem.java:65-70 + :28
      public static final int MAX_COOLDOWN_SECONDS = 180;   // 与玩家实际最大冷却无关
      double ratio = Math.min(1.0, remainingTicks / (double) (MAX_COOLDOWN_SECONDS * 20L));
      int cost = (int) Math.ceil(ratio * MAX_COOLDOWN_COST);
      return Math.max(1, Math.min(MAX_COOLDOWN_COST, cost));
B(forge-1.20.1) (a) item/sign/KomachiSignItem.java:132-142;(b) :66-68;(c) :91;(d) :52-56;(e) item/chip/CurrentCoreChipItem.java:28,65-70 —— 同构。
可达性: (a)(b)(c)(d)(e) 全部可达。
修法: 统一为"按剩余比例"或"按写入附件里的本次最大值" —— **两条路线都需用户先定夺**(§C8)。
```

### 2.5 §C8 — 减免口径与文案是否一致

```text
ID: C-C8
核验: 修正(文案本身有两种说法,代码有三种实现;「扫地机」文案与实现不符是确定缺陷)
现象:
  - 立牌 tooltip `tooltip.astral_dice.sign.jasmine_passive` 写「使用「加急加快」后,主动技能冷却立即减少最大冷却的 **50%**」——但代码扣的是**固定 180s 的一半**,不是"本立牌最大冷却的一半"。
  - 命运指引 tooltip `tooltip.astral_dice.card.fate_guidance_desc` 写「立刻减少主动技能最大冷却倒计时一半的时间」;其代码取 `WeirdDiceHandler` 的通用最大值 ——对枪匠/诡异骰子玩家**两者都不是**。
  - 枪匠 tooltip `tooltip.astral_dice.sign.moses_passive` 写「装备时,主动技能冷却时间减为 §9120 秒§r」——与代码一致(120s),但**未说明诡异骰子会把它再减半到 60s**,也没说明命运指引会把减免按 90s 算。
证据:
A(neoforge-1.21.1) src/main/resources/assets/astral_dice/lang/zh_cn.json:559
      "tooltip.astral_dice.sign.jasmine_passive": "每移动 §e300§7 米,交替提升 §e1§7 点攻击力与防御力（各自上限 §e%s§7 点）\n使用「加急加快」后，主动技能冷却立即减少最大冷却的 §e50%%§r",
      :577 "tooltip.astral_dice.sign.moses_passive": "装备时，主动技能冷却时间减为 §9120 秒§r\n…"
      :434 "tooltip.astral_dice.card.fate_guidance_desc": "使用后，获得以下能力：\n1. 出牌数 §e+1§7\n2. 立刻减少主动技能最大冷却倒计时一半的时间\n…"
B(forge-1.20.1) src/main/resources/assets/astral_dice/lang/zh_cn.json —— 同 key 同文案(行号略)。
可达性: 文案在 tooltip 必然显示。
修法: 先定夺口径,再改文案(禁止先改文案)。注意 checklist §C8 明确要求"−50%""−10 秒"要能对上算法;当前 (c) 对不上。
```

### 2.6 §C4 / §C5 — 计时器与状态分离、陈旧计时器

```text
ID: C-C4
核验: **证伪**(旧版 blocker 形态 S6-C2 已被本批改动**完整修复**,方向相反的"永久无法释放"风险亦**不存在** —— 接线已在审计进行中落地)
现象: 原 S6-C2 的"残留正计时器 ⇒ 任何立牌主动永不冷却"门槛(A 旧: `getSignReadyExpire(player) <= 0`)已改为**只看状态**(A: `getSignReadyType(player) <= 0`);同时新增玩家级重置方法 `BaseSignItem.tickSignReadyTimeout`,并已在两个版本的玩家级 tick 中接线。
顺序依赖: 起冷却判据 = 只看 `sign_ready_type`(状态);重置 = `tickSignReadyTimeout` 由 `PlayerTickEvent.Pre` 每 tick 调用、与立牌是否在槽位无关。二者构成 checklist §C4 要求的"状态与计时器分离",判定只看状态、计时器归零自动重置状态、且**不依赖载体仍在**。反转(不接线)会让玩家永久无法释放任何主动技能 —— 该形态在本次审计窗口内存在过约 [T1, T2],随后被那批代理接线消除。
证据:
A(neoforge-1.21.1)
  起冷却门槛(【改动中】): item/sign/BaseSignItem.java:119  `if (ModAttachments.getSignReadyType(player) <= 0) {`
  新增重置方法(【改动中】): item/sign/BaseSignItem.java:169-187
      public static void tickSignReadyTimeout(Player player) {
          if (player == null) return;
          if (player.level().isClientSide()) return;
          int type = ModAttachments.getSignReadyType(player);
          if (type <= 0) return;
          long expire = ModAttachments.getSignReadyExpire(player);
          // 计时器仍有效(未归 0 且未到期):等待继续,不做处理
          if (expire > 0 && player.level().getGameTime() < expire) return;
          // 计时器归 0:自动重置待命状态并移除对应的"待命"提示效果
          ModAttachments.setSignReadyType(player, 0);
          ModAttachments.setSignReadyExpire(player, 0);
          ...(按 type 移除 HAIQING_READY / BONNIE_READY / MOSES_READY)
      }
  接线(唯一每 tick 服务端入口): event/PlayerTickEvents.java:111-118
      public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
          ...
          BaseSignItem.tickSignReadyTimeout(player);   // :118
      }
  等待闸: item/sign/BaseSignItem.java:151-155
      private static boolean isSkillWaiting(Player player) {
          long expire = ModAttachments.getSignReadyExpire(player);
          return ModAttachments.getSignReadyType(player) > 0 && expire > 0
                  && player.level().getGameTime() < expire;
      }
  各立牌自有超时清理: **已删除**(见 P05) —— item/sign/MosesSignItem.java:45-58 现仅保留"每 20 tick 重发待命提示"。
B(forge-1.20.1) item/sign/BaseSignItem.java 【改动中】;event/PlayerTickEvents.java:109-117(**接线在 :117**);item/sign/MosesSignItem.java 自有超时块同样已删。
可达性: 修复后**无残留路径**。最小实机(回归验证用,期望"不命中"): 装备占星师 → 按 J 武装待命 → 30s 内**卸下**占星师立牌 → 装上任意其他立牌 → 等 30s 到期后再按 J,应正常释放并进入冷却(修复前该场景会被 `isSkillWaiting` 永久拦下)。
修法: 无需修。**保留**:接线必须留在 `PlayerTickEvent.Pre`(早于 20-tick 节流、早于 `onPlayerTick`),且 `tickSignReadyTimeout` 必须保持幂等(`type <= 0` 立即返回),因为 Pre/Post 两阶段都会经过。
```

> ⚠️ **本轮审计遭遇并发写入(重要)**:`item/sign/BaseSignItem.java`、`item/card/EffectCardPeriod.java`、`event/PlayerTickEvents.java`、`item/sign/{Bonnie,Haiqing,Moses}SignItem.java` 在本次审计窗口内**被另一批代理持续改写**。本小节在两次采样间结论反转:
> - `[T0]` 起冷却门槛仍为 `getSignReadyExpire(player) <= 0`(旧 blocker 形态);
> - `[T1]` 门槛已改为 `getSignReadyType(player) <= 0`,`tickSignReadyTimeout` 已定义但 **`PlayerTickEvents` 尚无调用点**(此时若定稿,即为反向 blocker);
> - `[T2]` **接线已落地(A :118 / B :117)**,结论定为**证伪**。
>
> 因此本次审计中"P03(未接线)"为**已消失的中间态**,保留在 §3 仅作过程记录,不列为交付缺陷。所有**数值类**结论(§2.3 枪匠三问、P01、P02)——取自 `WeirdDiceHandler` / `MosesSignItem.signCooldownTicks` / `FateGuidanceCardItem` / `JasmineSignItem` / `LuluSignItem` / `KomachiSignItem` / `CurrentCoreChipItem` / `ChargeManager` / `GameplayConstants` —— 这些文件**始终不在改动清单内**,故**不受并发写入影响**。

```text
ID: C-C5

ID: C-C5
核验: 修正(旧结论"冷却键死后归零(靠不随死亡复制)"经原版源码复核**成立**,但同一理由同时反驳了它想论证的另一半)
现象:
  (i) 「冷却键靠不随死亡复制而间接归零」经原版字节码/源码**证实**:NeoForge 死亡重生会 **new 一个新的 ServerPlayer**,`restoreFrom(that, keepEverything)` 只搬运有限字段,附件不经 `copyOnDeath()` 不搬 ⇒ 新实体上所有 `sign_active_cooldown_end` / `ender_die_totem_cooldown_end` / `warp_engine_portal_cooldown_end` / `railgun_cooldown_end` / `airbag_cooldown_end` 等**都回到 0**。
  (ii) 由此 **"死亡刷新末影骰图腾冷却"不成立**(新实体上冷却本就被清) —— 这条不算缺陷。
  (iii) 真正残留的是"跨**下线/重登**":这些键多数带 `.serialize()`(随玩家存档落盘),游戏内 `getGameTime()` 继续走;所以**离线期间冷却正常流逝**,不会出现"登录即无敌"。仅需注意:单机退出到主菜单再进入,世界时间仍在推进,行为正确。
证据:
  原版反编译源码 `neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-sources.jar`:
    `net/minecraft/server/players/PlayerList.java` → `respawn(...)`:
        ServerPlayer serverplayer = new ServerPlayer(this.server, serverlevel, player.getGameProfile(), player.clientInformation());
        serverplayer.connection = player.connection;
        serverplayer.restoreFrom(player, keepInventory);
    `net/minecraft/server/level/ServerPlayer.java` → `restoreFrom(ServerPlayer that, boolean keepEverything)`:
        this.wardenSpawnTracker = that.wardenSpawnTracker; this.chatSession = that.chatSession;
        this.gameMode.setGameModeForPlayer(...); this.onUpdateAbilities();
        this.getAttributes().assignBaseValues(that.getAttributes());
        this.setHealth(this.getMaxHealth());
        if (keepEverything) { this.getInventory().replaceWith(that.getInventory()); ... }
        # 附件相关字段完全不在搬运之列
  A: component/ModAttachments.java:400-404(sign 冷却只有 `.serialize(Codec.LONG).sync(...)`,无 `.copyOnDeath()`)
B: component/ModAttachments.java:372-380 + `AstralData.onPlayerClone` 只保留 2 键(首轮 S6 报告 §2.5 已核)
可达性: 无需实机(源码级确定)。最小验证(可选): 起冷却 → 记 tooltip 秒数 → `/kill` → 重生后立刻看 tooltip,应**无冷却行**。
修法: 无(设计如此)。**但请删除或修正首轮报告中"S6-C2 冷却键死亡残留"的推论**。
```

### 2.7 出牌轮冷却 `effect_card_cooldown_end`

```text
ID: C-C6
核验: 证实(载体健康:唯一基准、单一入口、无"剩余 vs 最大"混用)
现象: 出牌轮冷却只有一个基准常量(30s)经 `ChargeManager.cooldownTicks` 计算,且**在起冷却那一刻算定**;没有任何"二次减免"入口,故不存在枪匠式的基准混用。充能 −20% 同样在起冷却时读一次,冷却进行中吃/失充能都不影响已起的冷却(方向正确)。
顺序依赖: 写入点 4 个,但语义分层清晰:`registerPlay`(打满上限起冷却)、`tick`(情形②"未打满轮次收尾"起冷却、情形①到期清零)、`PlayerLifecycleHandler`(死亡显式清零 —— 属 S4-C6 的"无效项")。
证据:
A(neoforge-1.21.1)
  基准: component/GameplayConstants.java:18-19  `public static final int EFFECT_CARD_COOLDOWN_SECONDS = 30;`
  (a) 打满上限起冷却: item/card/EffectCardPeriod.java:288-294
      if (count >= getMaxAllowed(player)) {
          long cooldownTicks = ChargeManager.cooldownTicks(player,
                  GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
          ModAttachments.setEffectCardCooldownEnd(player, now + cooldownTicks);
      }
  (b) 未打满轮次收尾(【改动中】新增): item/card/EffectCardPeriod.java:328-343
      if (cooldown <= 0) {
          if (played <= 0) return;
          int maxAllowed = getMaxAllowed(player);
          if (played < maxAllowed) {
              if (getRemainingBlockTicks(player) > 0) return;
              ModAttachments.setEffectCardPlayCount(player, maxAllowed);
          }
          long recoverTicks = ChargeManager.cooldownTicks(player,
                  GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
          ModAttachments.setEffectCardCooldownEnd(player, now + recoverTicks);
          return;
      }
  (c) 到期清零: item/card/EffectCardPeriod.java:344-345
      ModAttachments.setEffectCardCooldownEnd(player, 0);
      ModAttachments.setEffectCardPlayCount(player, 0);
  (d) 死亡清零: event/PlayerLifecycleHandler.java:157-158(【改动中】)
      ModAttachments.setEffectCardPlayCount(player, 0);
      ModAttachments.setEffectCardCooldownEnd(player, 0);
  读取口径: item/card/EffectCardPeriod.java:200-212
      isBurstFull: `if (cdEnd > 0 && now >= cdEnd) return false;`
      isCooldownActive: `return cdEnd > 0 && now < cdEnd;`
  tooltip 基准一致性(正面): event/ModTooltipHandler.java:1306-1313
      /** 效果牌冷却显示:按玩家当前实际冷却取值(含充能的 -20% 减免),结果向下取整为秒 */
      long ticks = ChargeManager.cooldownTicks(player, baseTicks);
      return Math.max(1L, ticks / 20L);
B(forge-1.20.1) item/card/EffectCardPeriod.java(【改动中】)基准同;item/chip/... 同;PlayerLifecycleHandler.java:155 同。
可达性: 可达。
修法: 无需修。(b) 的"剩余出牌数作废"是有意裁决,非缺陷。
```

### 2.8 待命计时器 `sign_ready_type` / `sign_ready_expire`

```text
ID: C-C7
核验: 证实(载体指标正常,无减免入口,但有"无冷却续期"缝隙)
现象: 待命计时器固定 30s(`SKILL_WAIT_SECONDS*20`),无任何减免口径 —— 因此**不受枪匠三问影响**。写入点:`sign_ready_type` 3 个武装点(Moses/Haiqing/Bonnie 的 `handleUse`),`sign_ready_expire` 5 个写入点(3 个武装 + 死亡清理 + 释放时清零)。
顺序依赖: 起冷却判据改为只看 `sign_ready_type`(改动后),故"武装待命"本身就阻止本次进入冷却(这是契约:释放成功才起冷却)。
证据:
A(neoforge-1.21.1)
  常量: component/GameplayConstants.java:46-47  `public static int SKILL_WAIT_SECONDS = 30;`
  武装(以枪匠为例): item/sign/MosesSignItem.java:98-102
      ModAttachments.setSignReadyType(player, READY_TYPE);
      ModAttachments.setSignReadyExpire(player,
              level.getGameTime() + GameplayConstants.SKILL_WAIT_SECONDS * 20L);
  释放清零: combat/DiceCombatEvents.java:230-232(占星师)、:244-246(秘密侦探)、:270-271(枪匠)
  死亡清零: event/PlayerLifecycleHandler.java:134-135(【改动中】)
  卸载清理: item/sign/MosesSignItem.java:83-86 / HaiqingSignItem / BonnieSignItem(【改动中】)
B(forge-1.20.1) 同构:item/sign/MosesSignItem.java:104-105;combat/DiceCombatEvents.java:227-228/241-242/267-268;event/PlayerLifecycleHandler.java:131-132
可达性: 可达。
修法: 见 C-C4(接线 `tickSignReadyTimeout`);另 S6-C3 的"到期边界无冷却续期 + 风扇奖励重复结算"仍在(A: BaseSignItem.java:103-104 风扇结算在 `handleUse` 成功之后、冷却闸之前),属 §C1 邻域,本次不重复立项。
```

### 2.9 筹码类冷却逐个载体

```text
ID: C-C9
核验: 证实(6 个筹码冷却载体**全部**为"起冷却时算定的常量",无减免接口 ⇒ 不参与枪匠三问)
现象: 电磁炮 / 安全气囊 / 缓冲护盾 / 魔法箭袋 / 跃迁引擎(传送门+Waystone 共享) / 探天卫星(补牌 + 出牌数+1) 六类冷却都只用一个 `static final` 常量直接写 `now + X`,没有任何"最大/剩余/减免"分叉;也不会被任何减免方读写(减免方只碰 `sign_active_cooldown_end`)。故 §C2/C3 的缺陷**不外溢**到这些载体。
顺序依赖: 无跨系统顺序依赖;唯一契约是"先判冷却再写冷却"(全部满足)。
证据:
A(neoforge-1.21.1)
  电磁炮: item/chip/RailgunChipItem.java:52-53 `public static final int COOLDOWN_TICKS = 20 * 60;`
          判定 :80-83 `return player.level().getGameTime() < ModAttachments.getRailgunCooldownEnd(player);`
          写入 :151 `ModAttachments.setRailgunCooldownEnd(cause, cause.level().getGameTime() + COOLDOWN_TICKS);`
  安全气囊: item/chip/AirbagChipItem.java:22 `COOLDOWN_TICKS = 20 * 60;`;判定 :37-40;写入 :52
  缓冲护盾: item/chip/BufferShieldChipItem.java:17 `COOLDOWN_TICKS = 300;`;判定 :42;写入 :43
  魔法箭袋: item/chip/MagicQuiverChipItem.java:22 `COOLDOWN_TICKS = 1200;`;判定 :43,:57;写入 :70
  跃迁引擎: item/chip/WarpEngineChipItem.java:39 `PORTAL_WAYSTONE_COOLDOWN_TICKS = 20 * 60 * 5;`;判定 :63;写入 :64
  探天卫星: item/chip/SatelliteChipItem.java:31-33 `GIVE_INTERVAL_TICKS = 1200; PLAY_BONUS_COOLDOWN_TICKS = 1200;`
          判定/写入 :52-59(补牌) 与 :67-70(出牌数 +1)
B(forge-1.20.1) 逐一对应:RailgunChipItem / AirbagChipItem / BufferShieldChipItem / MagicQuiverChipItem / WarpEngineChipItem / SatelliteChipItem —— 常量与逻辑同构。
可达性: 全部可达。
修法: 无需修。**唯一值得记的信息**:这些冷却与立牌主动冷却**互相独立**,玩家会看到两条不同的冷却提示,这是设计,不是缺陷。
单侧差异: 无。
```

### 2.10 末影骰不死图腾 / 赋能递减

```text
ID: C-C10
核验: 证实(图腾无减免漏洞;赋能计时器无"最大"概念)
现象:
  - 不死图腾冷却:5:00 常量,起冷却在 `LivingDeathEvent` 取消死亡之后;判定 `now < cooldownEnd`。**不参与任何减免**;死亡重生走新实体 ⇒ 冷却归零(见 C-C5)。因此"死亡刷新图腾"只能靠**同一实体不死重置**(不存在),不可利用。
  - 赋能递减:`empower_decay_at` 是"下一次递减时刻",不是"冷却结束时刻";`EmpowerManager.tick` 每 20 tick 驱动、每 30s 减 1 层,且**获得新层数会把计时器重新起算**——这是"最大 vs 剩余"问题的一个良性变体(不会归零,只会延后)。
证据:
A(neoforge-1.21.1)
  图腾判定: event/EnderDiceHandler.java:71-74
      /** 不死图腾冷却是否仍在进行(剩余 > 0 时为 true) */
      public static boolean isTotemOnCooldown(Player player) {
          return player.level().getGameTime() < ModAttachments.getEnderDieTotemCooldownEnd(player);
      }
  图腾写入: event/EnderDiceHandler.java:175-177
      // 开始 5:00 冷却(以世界时间为准)
      ModAttachments.setEnderDieTotemCooldownEnd(player,
              player.level().getGameTime() + TOTEM_COOLDOWN_TICKS);
  赋能: item/EmpowerManager.java:38-46 / :59-84
      public static void addStacks(Player player, int stacks) {
          ...
          ModAttachments.setEmpowerDecayAt(player, player.level().getGameTime() + DECAY_INTERVAL_TICKS);
      }
      if (now < next) return;
      EmpowerEffect.consumeOne(player);
      int remain = EmpowerEffect.getStacks(player);
      ModAttachments.setEmpowerDecayAt(player, remain > 0 ? now + DECAY_INTERVAL_TICKS : 0);
B(forge-1.20.1) event/EnderDiceHandler.java:…(同);item/EmpowerManager.java(同)。
可达性: 可达。
修法: 无需修。
```

### 2.11 卡牌耐久 / 出牌数上限的联动

```text
ID: C-C11
核验: 证实"无联动"(原以为有联动的两个系统实际完全解耦)
现象: 效果牌出牌轮上限(`effect_card_play_count`,封顶 9)与战斗牌耐久(`card_uses` / `WeaponEnhancement.appliedStones[].uses`)**互不影响**:
  - 效果牌走 `EffectCardPeriod.registerPlay`(附件计数 + 30s 冷却);
  - 战斗牌耐久只在骰神赐福那次攻击 / 防御牌那次受击各减 1(`DiceCombatEvents.consumeAttackCardDurabilityOnce` / `consumeDefenseCardDurability`);
  - `CARD_USES` 数据组件只在 `ModItems` 注册与 `CardInventoryMenu` 镶嵌时写入,战斗消耗走 `WEAPON_ENHANCEMENT` 里的 `AppliedStone.uses`;
  - 因此**不存在**"耐久耗尽 ⇒ 出牌数上限变化"或反之的耦合。
顺序依赖: 无。
证据:
A(neoforge-1.21.1)
  效果牌上限: item/card/EffectCardPeriod.java:134-151(getMaxAllowed,封顶 `MAX_EFFECT_CARD_PLAYS`)
  攻击牌耐久: combat/DiceCombatEvents.java:697-734(尤其 :714 `int newUses = stone.uses() - 1;`)
  防御牌耐久: combat/DiceCombatEvents.java:736-766
  耐久组件: item/card/CardItem.java:17-22(`getMaxStackSize` 读 `CARD_USES`)
B(forge-1.20.1) item/card/EffectCardPeriod.java(【改动中】);combat/DiceCombatEvents.java(同)
可达性: 可达。
修法: 无需修。
```

---

## 3. 新发现的冷却时序缺陷(带 ID)

### P01 — `LuluSignItem` 减免夹底用 `0`(而非 `now`),使 `cdEnd == 0` 成为"第三种状态"

```text
ID: P1-LULU-CLAMP
严重度: high(玩家可见:冷却条与 tooltip 直接消失,可立即再次释放)
核验: 证实(与 `JasmineSignItem` 的 `Math.max(now, ...)` 形成**同一语义的两种实现**)
现象: 史莱姆立牌被动每次受击(1s 内限一次)从当前冷却剩余里扣 200t,并用 `Math.max(0, ...)` 夹底。当剩余 ≤ 200t 时 `cdEnd` 被写成 **0** —— 而 `sign_active_cooldown_end == 0` 在全部读取方那里表示"从未起过冷却":
  - 冷却闸 `if (cdEnd > 0 && now < cdEnd)` ⇒ 放行(可立即释放);
  - tooltip `addSignCooldownRemaining` 的 `cdEnd > 0 ? ... : 0` ⇒ **不显示冷却行**(不是显示 0 秒)。
  即:玩家看到"没有冷却",而不是"还剩一点点"。这与扫地机的 `Math.max(now, ...)`(写成 `now`,`now < cdEnd` 为假但 `cdEnd > 0` 为真 ⇒ tooltip 走 `remainingTicks/20 = 0` 也不显示)在**显示层等价**,但在**语义层不等价**:`0` 会被 `C-C5` 里"跨重登"路径当成"未起冷却",而 `now` 会随世界时间变成过去时刻。
顺序依赖: `BaseSignItem.invokeHurtHooks` 在 `LivingDamageEvent.Pre` 的**最前段**执行(A: combat/DiceCombatEvents.java:192-196),早于骰战结算,也早于任何"起冷却"。若同一次受击同时在别处起冷却,后写者覆盖(无契约)。
证据:
A(neoforge-1.21.1) item/sign/LuluSignItem.java:44-59
      @Override
      protected void onHurt(Player player, float amount) {
          long nowTick = player.level().getGameTime();
          if (nowTick - com.merlinkitsune.astral_dice.component.ModAttachments.getLuluLastHurtTick(player) < 20) {
              return;
          }
          com.merlinkitsune.astral_dice.component.ModAttachments.setLuluLastHurtTick(player, nowTick);
          // 主动技能冷却 -10 秒(200 tick)
          long cdEnd = com.merlinkitsune.astral_dice.component.ModAttachments.getSignActiveCooldownEnd(player);
          if (cdEnd > 0) {
              com.merlinkitsune.astral_dice.component.ModAttachments.setSignActiveCooldownEnd(player,
                      Math.max(0, cdEnd - 200));
          }
          // 治愈点 +1(上限为玩家最大生命值的一半,即 ♥ 数)
          HealingManager.add(player, 1);
      }
  对照(同一语义、不同夹底): item/sign/JasmineSignItem.java:88-91 `if (cdEnd > now) { ... Math.max(now, cdEnd - ...); }`
  闸门: item/sign/BaseSignItem.java:86-87 `long cdEnd = ...; if (cdEnd > 0 && now < cdEnd) {`
  tooltip: event/ModTooltipHandler.java:249-256 `int remainingTicks = cdEnd > 0 ? (int) (cdEnd - p.level().getGameTime()) : 0; if (remainingTicks > 0) {...}`
B(forge-1.20.1) item/sign/LuluSignItem.java:45-59(逐字相同);item/sign/JasmineSignItem.java:89-91;item/sign/BaseSignItem.java:85-86;event/ModTooltipHandler.java:247-252
可达性: 可达。最小实机: 装备史莱姆立牌 → 按主动起冷却(180s)→ 连续受击(每次间隔 >1s)4 次以上(4×10s=40s,远小于 180s;需剩余进入 200t 窗口)→ 观察立牌 tooltip 的"冷却中"行**提前消失**且可立即再次按主动。
修法: `Math.max(now, cdEnd - 200)`(与扫地机对齐);或让读取方把 `cdEnd == 0 || cdEnd <= now` 一律视为"无冷却"并在写 0 时同时清 `sign_ready`。**建议同时统一为"绝对值 `now + ...`"**,不要出现 0。
```

### P02 — `CurrentCoreChipItem` 用硬编码 180s 换算充能消耗,使"减免后的冷却"按原价收费(第二条并列"最大值常量")

```text
ID: P2-CORE-180
严重度: medium(玩家可见:同样长度的实际等待,充能花费差 6 倍)
核验: 证实(这是 checklist §C2 所说"两条并列的最大值函数/常量"的**常量版本**,与函数版并存)
现象: 电流核心筹码的"立即完成冷却"价格 = `ceil(剩余 / (180*20) * 6)`,分母是**硬编码 180s**,与玩家实际最大冷却无关。佩戴枪匠(120s)并配诡异骰子(60s)后再配充能(48s = 960t)时,满冷却只需 `ceil(960/3600*6) = ceil(1.6) = 2` 点充能即可秒清一次 48s 冷却;而默认 180s 立牌满冷却需 6 点。等价地:枪匠玩家的"每点充能买到的冷却时长"是默认立牌的 **2.5 倍**。文案/tooltip 未说明价格随冷却基准变化。
顺序依赖: 价格计算读"剩余 tick"(与减免基准无关),但**分母是另一套基准常量**。同一玩家在同一次冷却里,先被减免(剩余变小)再使用电流核心 ⇒ 价格按比例下降 —— 这是唯一"按剩余"的良性实现,但它与减免基准不共享数据。
证据:
A(neoforge-1.21.1) item/chip/CurrentCoreChipItem.java:26-30 + :61-70 + :78-95
      /** 消耗档位换算参考的最大冷却时长(秒) */
      public static final int MAX_COOLDOWN_SECONDS = 180;
      /** 立即完成冷却的充能消耗上限(按占比切成 6 档) */
      public static final int MAX_COOLDOWN_COST = 6;
      ...
      public static int instantCooldownCost(long remainingTicks) {
          if (remainingTicks <= 0) return 0;
          double ratio = Math.min(1.0, remainingTicks / (double) (MAX_COOLDOWN_SECONDS * 20L));
          int cost = (int) Math.ceil(ratio * MAX_COOLDOWN_COST);
          return Math.max(1, Math.min(MAX_COOLDOWN_COST, cost));
      }
      ...
      // 立即完成冷却:结束时刻置为当前时刻(后续判定 now < cdEnd 不再成立)
      ModAttachments.setSignActiveCooldownEnd(player, now);
  对照基准常量: component/GameplayConstants.java:42-45(180s)、item/sign/MosesSignItem.java:37-38(120s)
B(forge-1.20.1) item/chip/CurrentCoreChipItem.java:26-30,61-70,78-95 —— 逐字同构。
可达性: 可达。最小实机: 装备枪匠 + 诡异骰子 + 充能(≥2 层)+ 电流核心;按主动并命中(起 48s 冷却);再按 J —— 若提示"已消耗 2 点充能立即完成",即命中(默认立牌同长度应需 3 点以上)。
修法: 与 §7 统一修法建议同一修法 —— 读"本次最大冷却"附件后按其比例切档。
```

### P03 — 【过程记录·已消失】待命计时器分离改造在中间态曾"只定义不接线",构成反向 blocker

```text
ID: P3-READY-WIRING
严重度: **已消失(证伪)** —— 中间态为 high,保留记录供批次落地后回归
核验: **证伪**(接线已在 T2 落地:A `event/PlayerTickEvents.java:118`、B `:117`)
现象(中间态,`[T1]` 采样): `BaseSignItem.tickSignReadyTimeout` 已定义(仅负责归零状态+清计时器+移除提示效果),但 `PlayerTickEvents` 无调用点;同时起冷却闸已改看 `sign_ready_type`。该形态下"武装待命后卸下立牌且未死亡"的残留会让 `isSkillWaiting` 恒真 ⇒ 该玩家**所有**立牌主动按键永久无效。
为什么不再是缺陷(`[T2]` 采样): 两版本均已接线,且接线位置为 `PlayerTickEvent.Pre`(早于 `onPlayerTick` 的 20-tick 节流),与立牌是否在槽位无关 —— 正是 §C4 要求的形态。
证据(证伪侧):
A(neoforge-1.21.1) event/PlayerTickEvents.java:111-118
      public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
          Player player = event.getEntity();
          ...
          // 立牌"待命"状态与计时器分离(S6-C2,2026-09-15 用户裁决):计时器归 0/过期即自动重置待命状态。
          BaseSignItem.tickSignReadyTimeout(player);          // :118
B(forge-1.20.1) event/PlayerTickEvents.java:109-117(接线在 :117,`TickEvent.PlayerTickEvent`)
证据(中间态侧,仅存档): `git diff` 于 `[T1]` 时 `PlayerTickEvents.java` 不在改动清单内,而 `BaseSignItem.java` 已含 `tickSignReadyTimeout` 定义。
可达性: 修复后**不可达**。最小回归验证(期望"不命中"): 装备占星师/枪匠 → 按 J 武装待命 → 30s 内卸下立牌 → 装上任意其他立牌 → 等 30s 到期后按 J,应正常释放并起冷却。
修法: 无需修。**回归守卫**:批次落地后请复核 `grep -n tickSignReadyTimeout <...>/event/PlayerTickEvents.java` 在两版本各 **1 命中**;并确认 `tickSignReadyTimeout` 保持幂等(Pre/Post 两阶段都会经过)。
```

### P04 — `LuluSignItem` 减免的触发前置是"**任意立牌**起冷却",与立牌归属无绑定(设计歧义)

```text
ID: P4-LULU-ANY-SIGN
严重度: low(设计口径问题,不是崩溃;但会使"冷却 −10 秒"作用在完全无关的立牌上)
核验: 证实
现象: `onHurt` 是"佩戴史莱姆时受击"触发的,但它减免的是**玩家级共享冷却** —— 玩家可用史莱姆挨打来缩短**其他**立牌的冷却(例如先用枪匠武装待命,再换史莱姆挨打把枪匠冷却砍掉)。同一问题存在于扫地机(加急加快减免任意立牌冷却)与忍者(每 3 张效果牌减免任意立牌冷却)。
顺序依赖: `invokeHurtHooks` 遍历**当前佩戴**的立牌;减免目标与"是哪枚立牌起的冷却"无关联字段。
证据: A item/sign/LuluSignItem.java:44-59(item/sign/BaseSignItem.java:253-263 的 `invokeHurtHooks` 是唯一调用方);
      A item/sign/JasmineSignItem.java:83-92;A item/sign/KomachiSignItem.java:131-141 —— 三者都只读玩家级键。
B 同构。
可达性: 可达(立牌槽 1 格,换装即触发)。
修法: 若定夺为"只减本立牌的冷却",需要给冷却附件加"来源立牌 id";若定夺为"玩家级共享减免"则为设计,建议在 tooltip 里写明。
```

### P05 — 【已修复】待命超时清理的重复实现(立牌 `onCurioTick` vs 玩家级 tick)

```text
ID: P5-READY-DUP
严重度: **已修复**(修复前 low)
核验: 修正(审计过程中被同批改动消除)
现象: 修复前,枪匠/占星师/秘密侦探各自在 `onCurioTick` 里做"到期清两键 + 移除提示效果",与新建的 `BaseSignItem.tickSignReadyTimeout` **完全重复**。两处判据等价性还差一点(`onCurioTick` 版要求 `expire > 0 && now >= expire`;新方法用 `!(expire > 0 && now < expire)`,即把 `expire == 0` 也算到期)—— 只有新方法能救"计时器被写成 0 但状态未清"。
现状: 重复块已删除;`MosesSignItem.onCurioTick` 只剩"每 20 tick 重发待命提示"。
证据:
A(neoforge-1.21.1) —— 删除后的现存形态(仅提示):
      item/sign/MosesSignItem.java:44-58
          @Override
          protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
              if (!(slotContext.entity() instanceof Player player)) return;
              if (player.level().isClientSide()) return;
              // 防御力按弱点识破层数折算为真实护甲(1 防御力 = 2 护甲)
              DiceCombatModifiers.setDefenseArmorBonus(player, "moses_weakness_armor",
                      isEquipped(player) ? WeaknessRevealEffect.getStacks(player) : 0);
              // 主动技能等待期:超时清除已移到玩家级 tick(BaseSignItem#tickSignReadyTimeout,S6-C2 状态与计时器分离),
              // 不再依赖"立牌仍在饰品槽位"...
              long expire = ModAttachments.getSignReadyExpire(player);
              if (ModAttachments.getSignReadyType(player) == READY_TYPE && expire > 0
                      && player.tickCount % 20 == 0) {
                  sendReadyPrompt(player);
              }
          }
      (对应 `git diff` 删除行: `if (... getSignReadyType(player) == READY_TYPE && expire > 0 && player.level().getGameTime() >= expire) { setSignReadyType(0); setSignReadyExpire(0); ModEffectRemoval.remove(..., MOSES_READY); }`)
B(forge-1.20.1) item/sign/MosesSignItem.java 同一删除(diff 逐行对应)。
可达性: 不适用(已修复)。
修法: 无需修。**保留纪律**:此后新增待命类立牌不得再在 `onCurioTick` 里写超时清理(§D1 唯一入口)。
```

### P06 — 出牌轮"未打满也进冷却"新分支会让"剩余出牌数"静默作废(改动中,需复核语义)

```text
ID: P6-EFFECTCARD-CARRYOVER
严重度: medium
核验: 证实(代码行为确定;是否为缺陷取决于用户是否接受"作废"口径 —— 注释显示是裁决过的)
现象: `EffectCardPeriod.tick` 新增分支在"未打满且本轮所有计时器归零"时,先把 `play_count` **补齐到上限**,再起 30s 冷却。这让 `isBurstFull` 立刻为真,本轮剩余出牌数被永久丢弃。玩家可见后果:出了 3 张效果牌、效果自然结束后,剩余 6 张直接作废并被迫等 30s。
顺序依赖: 该分支只在 `cooldown <= 0` 时进入;20 tick 节流(`PlayerTickEvents` 的 `tickCount % 20`);`getRemainingBlockTicks` 依赖 `EFFECT_PENDING_SOURCES` 注册表(新增效果牌必须注册,否则该轮被判"无计时器"而提前收尾)。
证据: A item/card/EffectCardPeriod.java:323-343(【改动中】);B item/card/EffectCardPeriod.java 同 diff
可达性: 可达。最小实机: 出 1 张「对怪激光」(1:00 效果)→ 等效果结束(期间不出牌)→ 观察是否立刻进入 30s 冷却且出牌数显示已满。
修法: 若口径确实是"作废"则保留,但建议在 `ModTooltipHandler` 的剩余出牌数行同步说明;若希望保留剩余张数,应改为"到期只清冷却、不补齐计数"。
```

---

## 4. 单侧(仅一个版本存在)差异

| # | 差异 | A(neoforge-1.21.1) | B(forge-1.20.1) | 是否影响冷却语义 |
|---|---|---|---|---|
| D1 | `item/sign/BaseSignItem.java` 改动进度 | 已含 S6-C1/S6-C2 改造(266 行,含 `tickSignReadyTimeout` + `CurioSlotUtil.isIntentionalUnequip`) | 同步改动中(diff 量与 A 相同:68 行增删) | 两版本**同构**,未见语义差 |
| D2 | `item/sign/BonnieSignItem.java` / `HaiqingSignItem.java` | **在改动清单中** | **不在改动清单中** | 待命类立牌的最外层超时清理位置可能短暂不一致(不影响冷却值,只影响状态清理时机) |
| D3 | `component/ModAttachments.java` 冷却键注册机制 | `AttachmentType.builder().serialize().sync()`,无 `.copyOnDeath()` | `AttachedDataKey` + `AstralData` Capability + `SYNCED_KEYS` | **无行为差异**(C-C5 已证两侧都随死亡归零) |
| D4 | 附件总数 | 61 个 | 63 个(多 `damage_effect_bonus` / `curse_original_amount`) | 与冷却无关 |
| D5 | 冷却减免五个实现 | `WeirdDiceHandler` / `MosesSignItem` / `FateGuidanceCardItem` / `JasmineSignItem` / `LuluSignItem` / `KomachiSignItem` / `CurrentCoreChipItem` / `EffectCardPeriod` / `ChargeManager` 逐行同构 | 同 | **无单侧缺陷** |

**单侧结论**:本次 §C 范围内**未发现任何只存在于一侧的冷却缺陷**;所有缺陷 A/B 同时存在、成因相同、数值相同。

---

## 5. 不确定点(未核验 / 需用户定夺)

- **U1(需定夺)**:减免的正确口径是"**本次冷却的最大值 ÷ 2**"(需要新增附件记录基准)还是"**剩余时间 × 50%**"(不需要新状态,但会与 tooltip 的"最大冷却的一半"文字冲突)?两条路线都能修掉归零,但文案不同。**在定夺前不应改代码或文案**(checklist §C8)。
- **U2(未核验,影响 C-C2b 的最小验证步骤)**:`stand` 槽位注册为 **1 格**(AGENTS.md 记载 `dice=1/stand=1/chip=0`),故"同时戴枪匠立牌 + 扫地机立牌"不可行。C-C2b 的归零场景因此**不需要枪匠**:任意立牌 + 诡异骰子(1800t)即可被固定 1800t 夹到 0。C-C2a 的"归零"场景也不需要同时戴两枚立牌——只需要**先在戴枪匠时起冷却,再卸下诡异骰子后用命运指引**(立牌始终只有枪匠一枚)。已据此修正 §2.3 的数值表。
- **U3(已解决,保留为回归守卫)**:`BaseSignItem` / `EffectCardPeriod` / `PlayerTickEvents` / `HaiqingSignItem` / `BonnieSignItem` / `MosesSignItem` 属**改动中**文件。本次审计在三个采样点观察到 `tickSignReadyTimeout` 从"已定义未接线"变为"已接线(A :118 / B :117)",故 P03 定为**证伪**、C-C4 定为**证伪**、P05 定为**已修复**。**批次落地后请复核**:`grep -n tickSignReadyTimeout <子项目>/src/main/java/com/merlinkitsune/astral_dice/event/PlayerTickEvents.java` 在两版本各应有 **1 命中**;并确认 `MosesSignItem.onCurioTick` 中不再有超时清理块。
- **U4(未核验)**:`CurrentCoreChipItem` 的"档位"是否**有意**按 180s 定价(即"枪匠减免不该连带降价")。若是有意,则 P02 应降级为 `low` 并补文案。
- **U5(未核验)**:`ModTooltipHandler.addSignCooldownRemaining` 用 `remainingTicks/20`(向下取整)且 `remainingTicks > 0` 才显示,因此冷却 ≤ 19t 时立牌 tooltip **不显示冷却行** —— 与 P01 的 `cdEnd == 0` 在显示层不可区分,实机验证 P01 时需改看"能否立即再次按 J",不要只看 tooltip 行。
- **U6(未核验)**:`sign_active_cooldown_end` 是 `.sync()` 到客户端的;`CurrentCoreChipItem.tryFinishCooldown` 在**服务端**按键路径执行,客户端预检若也读该值,减免瞬间的显示可能与服务端差 1 tick。本次未追客户端消费点(§C 范围外)。

---

## 6. 与首轮报告的关系(修订/覆盖)

| 首轮条目 | 本次判定 | 说明 |
|---|---|---|
| `interaction-audit-1.2.1.md:105` "S4-C7 `sign_active_cooldown_end` 有 7 个写入方" | **修正为 8** | 漏计 `CurrentCoreChipItem.tryFinishCooldown`(:92);且 7/8 中有 5 个是"减免改写"而非"起点" |
| `audit-slices/S6-sign-active-dice.md:140` S6-C5(4 套口径 / 现有缺陷) | **证实并扩展** | 新增第 5 套(电流核心硬编码 180s,记为 P02);补充 token 级数值表与"归零"的完整组合清单;C-C2c 扩展为"减免取决于减免时刻的全部装备" |
| `audit-slices/S6-sign-active-dice.md:114` "冷却键死亡后归零(不随死亡复制)" | **证实**(用原版 `PlayerList.respawn` + `ServerPlayer.restoreFrom` 源码) | 同时**排除**了"死亡刷新末影骰图腾冷却"这一猜想 |
| `audit-slices/S6-sign-active-dice.md:190-194` S6-C2(残留正计时器 ⇒ 永不冷却) | **已完整修复**(两版本均接线) | 起冷却闸改为只看 `sign_ready_type`;`tickSignReadyTimeout` 定义 + 在 `PlayerTickEvent.Pre` 接线(A :118 / B :117);各立牌重复超时块已删。审计窗口内曾出现"只定义不接线"的中间态(P03),已随之消失 |
| `docs/execution-order-checklist.md:52-53` §C2/§C3 的枪匠三问(①过度减免 ②固定 90s 归零 ③减免随装备变化) | **①证实 ②证实(且触发面更宽)③证实(范围更宽)** | 见 §2.3;额外发现 ①在"诡异骰子"组合下不止"过度减免"而是"归零" |
| `docs/execution-order-checklist.md:50` §C1"7 个写入方" | 同 S4-C7,修正为 8 | — |

---

## 7. 结论汇总(按严重度)

| ID | 严重度 | 一句话 | 需用户定夺 |
|---|---|---|---|
| C-C2a | **blocker** | 命运指引按 `WeirdDiceHandler`(180s)基准减免,枪匠实际 120s/60s ⇒ 过度减免;配诡异骰子或冷却中卸骰子时**直接归零** | 是(口径) |
| C-C2b | **blocker** | 扫地机被动用**常量 1800t** 减免,任意立牌 + 诡异骰子(1800t)一次扣满 ⇒ **免费立即完成** | 是(口径) |
| P01 | high | 史莱姆减免夹底写 `0`,使 `cdEnd == 0`(表示"无冷却")成为可达状态 | 否(对齐 `now` 即可) |
| C-C2 / C-C3 | high | 两条并列"最大冷却"函数 + 5 套减免口径并存 | 是(合并方案) |
| C-C2c | high | 减免量取决于"减免那一刻"的装备(骰子/立牌/充能/扫地机) | 是(同 C-C2a) |
| P02 | medium | 电流核心按硬编码 180s 折算充能价格,枪匠玩家"每点充能买到的冷却"是默认的 2.5 倍 | 是(U4) |
| P06 | medium | 出牌轮"未打满也进冷却"分支会静默作废剩余出牌数 | 是(口径) |
| C-C8 | medium | 扫地机/命运指引文案写"最大冷却的 50%",实现取常量/通用最大值 | 是(先定夺再改文案) |
| P04 | low | 史莱姆/扫地机/忍者的减免作用于任意立牌的冷却(不绑归属) | 是(设计口径) |
| ~~P03~~ | ~~high~~ → **证伪** | 【过程记录】中间态"`tickSignReadyTimeout` 未接线";T2 采样确认已接线,非缺陷 | 否 |
| ~~C-C4~~ | ~~medium~~ → **证伪** | 【同上】待命状态/计时器分离已完整落地 | 否 |
| ~~P05~~ | ~~low~~ → **已修复** | 【同上】立牌 `onCurioTick` 的重复超时块已删除 | 否 |

**本次交付的核心缺陷 = 3 条**(C-C2a、C-C2b、P01)+ 1 条设计基准问题(C-C2/C-C3/C-C2c 同源,一次修法可覆盖)+ 1 条常量版同源缺陷(P02)。P03/C-C4/P05 为已消失的中间态,仅作过程记录。

**未发现的缺陷类别**(明确记"无"):筹码类 6 个冷却载体(§2.9)、末影骰图腾与赋能递减(§2.10)、出牌轮冷却载体本身(§2.6)、卡牌耐久与出牌数上限联动(§2.11) —— 全部为单基准常量、无减免接口、无"剩余 vs 最大"混用。

### 7.1 统一修法建议(C-C2a / C-C2b / C-C3 / P01 / P02 共用,需先定夺口径)

> 本小节即上文各处引用的"§7 统一修法建议"。

**路线 A(推荐:记录基准,语义最准,与现有 tooltip 文案兼容)**

1. 新增玩家级附件 `sign_active_max_cooldown`(`long`,带 `.serialize()`,**不要** `.sync()` —— 客户端不需要)。
2. 起冷却的四处写入点(A: `item/sign/BaseSignItem.java:121-122`、`combat/DiceCombatEvents.java:238-239 / 259-260 / 273-274`)在写 `sign_active_cooldown_end` 的同时把**同一个** `xxxCooldownTicks(player)` 回写到 `sign_active_max_cooldown`;起冷却值为 0(即"无需冷却")时一并写 0。
3. 五个减免方改为读 `sign_active_max_cooldown`:
   - 命运指引:`reduction = maxCooldown / 2`(基准来源换成附件);
   - 扫地机:`Math.max(now, cdEnd - maxCooldown / 2)`;
   - 史莱姆:`Math.max(now, cdEnd - 200)`(夹底一律 `now`,见 P01);
   - 忍者:保持 `now + remaining * 0.7`(本来就是按剩余,不需改;但需明确它与"max 口径"并存是否可接受);
   - 电流核心:`instantCooldownCost(remaining, sign_active_max_cooldown)` 用附件作分母。
4. 冷却正常结束/被清 0 时把附件也写 0(与 `sign_active_cooldown_end` 的清零点同处)。

**路线 B(最小 diff:全部改为"按剩余比例",不需要新状态)**

- 命运指引 → `now + remaining / 2`;扫地机 → `now + remaining / 2`;史莱姆保持 `−200t` 但夹底改 `now`;电流核心保持按剩余占比。
- 代价:**与现有 tooltip 文案("最大冷却的一半")不符**,必须同步改 lang(`zh_cn.json` + `en_us.json` 两边 + 帕秋莉手册),且"最大冷却"这个词在文案里要整体去掉。

**两条路线的公共项(无争议,可直接做)**

- P01:史莱姆夹底 `Math.max(0, ...)` → `Math.max(now, ...)`,并在所有减免后把 `cdEnd <= now` 归一化为 `now`(不要出现 `0`,避免与"无冷却"混淆)。
- 合并 §2.2 的两条"最大冷却"函数为**唯一入口**(建议放在新的 `SignCooldown` 工具类,或 `BaseSignItem` 的 `protected int activeCooldownBaseTicks()`),`MosesSignItem` 只提供 120s 基准,不再自带一整套"减半 + 充能"逻辑。
- 修复后必须同步:**§C8 文案** + 首轮报告 `S6-C5` 结论 + `docs/execution-order-checklist.md:52` 的实例描述(避免下一轮审计重复发现)。

---

## 8. 附:实测脚本线索(供后续自动化)

仓库已有探针脚本,可用于本报告的最小验证(本次**未执行**,仅记录位置):

- `scripts/test/resources/kubejs/1.21.1/server_scripts/astral_bugfix_probe.js:1062-1151` —— `setSignActiveCooldownEnd` / `getSignActiveCooldownEnd` 直读直写探针。
- `scripts/test/resources/kubejs/1.20.1/server_scripts/astral_bugfix_probe.js:1053-1142` —— 同上(行号 −9)。
- `scripts/test/resources/kubejs/*/server_scripts/astral_bugfix_probe.js:1308-1365` —— `effect_card_cooldown_end` 探针。
- 建议新增探针断言(本次未写入任何文件):① 戴枪匠 + 诡异骰子起冷却后读 `getSignActiveCooldownEnd - now`,期望 **1200**;② 使用命运指引后立即再读,期望 **300**(按 C-C2a 数值表);③ 使用加急加快后读,期望 **0**(当前实现)/ **600**(修正后)。
