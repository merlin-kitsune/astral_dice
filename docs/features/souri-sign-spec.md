# 金鲤立牌（souri）规格与实现记录

> 本文是**实现记录 + 语义冻结**，不是前置设计稿：先有 2026-11-09 的代码与用户裁决，2026-11-10 补本文。
> **代码为真源**：本文任何数字若与代码不符，以代码为准，并回改本文。
> 基准：`multi-dev-next` @ `3a6df49c`；实机取证 2026-11-10（neoforge-1.21.1 + forge-1.20.1）。
> 落地线：**neoforge-1.21.1 / forge-1.20.1（P0，逐文件同构）**；P1 三线未移植（见 §6）。

## §0 定名与命名门禁

| 项 | 值 | 出处 |
|---|---|---|
| 中文名 | 金鲤立牌 | `lang/zh_cn.json` `item.astral_dice.souri_sign` |
| 英文名 | Amakawa Souri Sign | `lang/en_us.json:1171` |
| 日文名 | 天川蒼鯉のスタンド | `lang/ja_jp.json:1171` |
| 角色 | 天川蒼鯉（souri） | `tools/sign_display_names.json` |

英日名属门禁范围：改动后必须跑 `python3 tools/verify_sign_display_names.py`（2026-11-10 读数 `GATE: PASS`，108,811 项）。**禁机翻**，人名按 wiki 角色名表。

## §1 标识与常量（冻结值）

| 项 | 值 | 出处（neoforge-1.21.1，forge 同行号 ±1） |
|---|---|---|
| 注册 id | `astral_dice:souri_sign` | `item/ModItems.java:1152` |
| 稀有度 | `legendary`（传奇） | `item/ModItems.java:1154` |
| 堆叠 | `stacksTo(1)` | `item/ModItems.java:1153` |
| Curios 栏位 | `stand`（立牌） | `data/curios/tags/item/stand.json:36`；另见 `data/astral_dice/tags/item/signs.json:37` |
| 主动冷却 | `ACTIVE_COOLDOWN_TICKS = 1200`（1:00） | `item/sign/SouriSignItem.java:40` |
| 放置距离 | `PLACE_RANGE = 8.0D` | `item/sign/SouriSignItem.java:43` |
| 金鳞上限 | `GOLD_SCALE_MAX = 4`（=`SouriGoldScaleEffect.MAX_STACKS`） | `item/sign/SouriSignManager.java:53` |
| 每层金鳞防御 | `DEFENSE_PER_SCALE = 1` | `SouriSignManager.java:56` |
| 每层金鳞攻击（仅化龙后） | `ATTACK_PER_SCALE = 1` | `SouriSignManager.java:59` → `combat/DiceCombatModifiers.java:129-130` |
| 佩戴时反击上限 | `COUNTER_MAX = 4` | `SouriSignManager.java:62`；放宽点 `item/RenShieldManager.java:179` |
| 饮品实体存在时长 | `LIFETIME_TICKS = 6000`（5:00） | `entity/SouriDrinkEntity.java:50` |
| 拾取轮询 | 每 5 tick，`bbox.inflate(0.9)` | `SouriDrinkEntity.java:113-131` |
| 葫芦饮 | 1200t（1:00）／攻击 +3 | `effect/GourdDrinkEffect.java:21`、`:24` |
| 仙酿 | 2400t（2:00）／攻击 +4／拾取回 6 血／迅捷（amplifier 1） | `effect/ImmortalBrewEffect.java:22`、`:25`、`:28`、`:31` |

状态载体（**不要再造第二份真值**）：

- 金鳞层数 = 附件 `ModAttachments.SOURI_GOLD_SCALES`（0..4）。
- 化龙 = 锁存效果 `souri_dragon`（infinite duration，`PlayerEffectProtection` 保护，不被清除）。**不是**附件。
- 「反击」层 = 复用鼠鼠护盾的 `RenShieldManager` 池（`REN_COUNTER_CHARGES`）；金鲤不新建池，只放宽上限并允许**无盾**触发。
- HUD 图标 = 效果 `souri_gold_scale`，amplifier = 层数 - 1，infinite；**只是显示**，层数真值在附件。

## §2 语义

### §2.1 主动「把酒言欢」（瞬时型，无锁定态）

`SouriSignItem.handleUse` → `resolvePlacePos`（`player.pick(8.0, …)` 取命中方块上表面；液体／空气／超程 ⇒ `null`，发 actionbar `msg.astral_dice.souri_no_ground` 并**不进冷却**）：

1. 在落点生成 `SouriDrinkEntity`：已化龙 ⇒ `BREW`（仙酿），否则 `GOURD`（葫芦饮）；坐标 `+0.5/+0.25/+0.5`。
2. 施放者 `addGoldScale(player)`（+1 金鳞，满 4 触发化龙）**且** `addCounter(player, 1)`（+1 反击）。
3. actionbar：`msg.astral_dice.souri_place_gourd` / `..._place_brew`。
4. 走立牌统一冷却写入口（玩家级附件 `SIGN_ACTIVE_COOLDOWN_END`，见 `item/sign/BaseSignItem.java` 冷却段），1:00。

### §2.2 被动「不醉不归」（两条独立触发）

1. **反击出口**：`combat/DiceCombatEvents.java:2192` 起，门控由「有 `REN_SHIELD`」改为「有 `REN_SHIELD` **或** 佩戴金鲤」（`:2197`）。只对 `PartyRelations.isHostileTo` 的敌对攻击生效（`:2202`），逐层消耗 1 层（`:2205`），然后 `if (souri) addGoldScale(player)`（`:2209-2211`）。
2. **饮品被拾取**：任意角色拾取后，**放置者**（仍佩戴金鲤时）+1 反击（`SouriSignManager.onDrinkPicked`，`onDrinkPicked` 内的 owner 分支）。放置者已卸下 ⇒ 不发放。

### §2.3 金鳞与化龙

- `addGoldScale`（`SouriSignManager.java:112`）：0 → 4 递增；到 4 层 → `grantDragon`（`:160`）。
- **化龙**：授 `souri_dragon`（infinite，受保护）+ `convertFieldGourds`（`:177`）——把**全服所有维度**里尚未拾取的 `GOURD` 就地转成 `BREW`；发 `msg.astral_dice.souri_dragon_gained` / `..._gourd_converted`。
- 化龙后：每层金鳞 +1 攻击（§1）；主动改放仙酿；tooltip 状态行跳过（`event/ModTooltipHandler.java:1649`）。
- **HUD**：`refreshGoldScaleHud`（`:132`）在化龙后**删掉**金鳞图标（层数仍在附件、收益照旧）；层数为 0 也删；否则以 `amplifier = 层数 - 1` 重挂。撤销 fd23fa35 之前「化龙后图标仍显示」的行为，属**预期**。
- `resync`（`:215`，由 `SouriSignItem.java:118` 的 `curioTick` 周期性调用）重挂防御／HUD／化龙锁存态，是自愈入口，不在 tick 里写层数。

### §2.4 饮品与拾取

- 实体 `astral_dice:souri_drink`（`entity/SouriDrinkEntity.java`，`GeoEntity`，纯展示：渲染器施加旋转与浮动，不播动画）。
- 每 5 tick 在 `inflate(0.9)` 内找玩家；跳过旁观／死亡；`isDrinkBlocked`（`:230`）= 拾取方已有 `GOURD_DRINK` 或 `IMMORTAL_BREW` ⇒ 本次拒拾（对应文案「一次只能拾取一个饮品」）。
- 拾取即生效并 `discard()`：葫芦饮 ⇒ `GOURD_DRINK`；仙酿 ⇒ `IMMORTAL_BREW` + 迅捷 + `heal(6.0F)`。附 CLOUD×16 + END_ROD×6 粒子。
- 5:00 未被拾取 ⇒ 自灭（不退还、不转层）。

### §2.5 联动「留给你的」

拾取者**佩戴蛟龙立牌**时，`MamushiSignItem.addAwakening(picker, BREW ? 2 : 1)`：葫芦饮 +1 层觉醒，仙酿 +2 层。**觉醒由蛟龙侧发放，金鲤自身不产生觉醒**（2026-11-10 裁决）。

### §2.6 卸下与死亡

- `clearAll`（`:197`）：卸下时清金鳞／化龙／反击／修饰符。
- `if (player.isDeadOrDying()) return;`（`:199`）：**死亡掉落的卸下不清层**（2026-11-10 裁决）——复活重新佩戴后层数仍在。
- 遗留：`:201` 仍有 `ModAttachments.setSouriAwakening(player, 0)`，是「觉醒曾在金鲤」时期的清理残留，现属无操作（觉醒已不归金鲤），见 §7 欠账。

## §3 落点清单

Java（P0 两线各 7 个，逐文件同构）：

- `item/ModItems.java`（注册）、`item/sign/SouriSignItem.java`、`item/sign/SouriSignManager.java`
- `entity/SouriDrinkEntity.java` + 其客户端渲染/模型（`client/` 下 souri 相关 2 个文件）
- `effect/GourdDrinkEffect.java`、`effect/ImmortalBrewEffect.java`
- 接触点：`combat/DiceCombatEvents.java`、`combat/DiceCombatModifiers.java`、`item/RenShieldManager.java`、`event/ModTooltipHandler.java`

资源（neoforge-1.21.1）：

- 物品模型 `src/generated/resources/assets/astral_dice/models/item/souri_sign.json`
- 配方 `.../data/astral_dice/recipe/souri_sign.json`：3×3 `BYB/ZCZ/PDP` = star_coin_dust / tropical_fish / clock / blank_chip / golden_star_plate / diamond_dice
- 进度 `.../advancement/recipes/misc/souri_sign.json`
- 手册 `assets/astral_dice/patchouli_books/astral_guide/en_us/entries/signs/souri_sign.json`（spotlight + 3 页 text + crafting，sortnum 27）
- 贴图 `textures/item/souri_sign.png`、`textures/mob_effect/souri_gold_scale.png`、`textures/mob_effect/souri_dragon.png`
- 动画 `animations/souridrink.animation.json` + `geo/souridrink.geo.json`

## §4 文本（23 个 souri 键，zh/en/ja 三语齐）

物品名；`tooltip.astral_dice.sign.souri.{active,active_desc,passive,passive_desc,dragon_form,gold_scale,note,link}`；效果 `effect.astral_dice.{gourd_drink,immortal_brew,souri_gold_scale,souri_dragon}` + 各自 `.description`；消息 `msg.astral_dice.{souri_place_gourd,souri_place_brew,souri_gourd_drink,souri_immortal_brew,souri_dragon_gained,souri_gourd_converted,souri_no_ground}`；手册 `astral_dice.guide.entry.souri_sign.{1,2,3}`。

tooltip 结构（自上而下）：名称／栏位／按键提示／主动段／被动段／综合说明 `souri_note`（一段内含金鳞、化龙、葫芦饮、仙酿、一次一个）／联动 `souri_link`／状态行「金鳞：x / 4」／模组名。

## §5 边界（明确不做）

1. 金鲤**不发放觉醒**；觉醒只从蛟龙侧联动来（§2.5）。
2. 化龙是**锁存**的：不因金鳞回落而消失（层数只增不减，唯一的减法是卸下清空）。
3. 饮品不能被自己「预拾取」：放置的同一 tick 若站在 0.9 格内，会在下一次轮询被拾取——这是允许的，不做 owner 保护。
4. 不做第二次「金鲤化龙」：已化龙时 `grantDragon` 幂等（重复授同一效果不叠加）。

## §6 跨线现状与欠账

| 线 | Souri\*.java | lang 键 | 状态 |
|---|---|---|---|
| neoforge-1.21.1 | 7 | 23 | ✅ 实机通过 |
| forge-1.20.1 | 7 | 23 | ✅ 实机通过 |
| neoforge-26.1.2 | 0 | 0 | ❌ 未移植（缺 5 类基础设施 + 本体） |
| fabric-1.20.1 | 0 | 0 | ❌ 未移植 |
| fabric-1.21.1 | 0 | 0 | ❌ 未移植 |

P1 移植前需先补齐的共享基建（两条 fabric 线各缺 9 项）：`SignHooks`、`SignHookDispatcher`、`SignActiveLockMachine`、`SignEntitySkills`、`CardGivenHooks`、`MosesEnigmaticLink`、`AlSignItem`、`SouriSignItem`、`SouriSignManager`。

已知代码欠账（**不要当成本文写错**）：

1. `SouriSignItem.java:21`（forge `:20`）Javadoc 仍写「随后自身 +1 层【觉醒】、+1 层【反击】」——实际是 `addGoldScale`（金鳞）；同段「配方待定」也已过期（配方已生成）。
2. `SouriSignManager.java:201` 残留 `setSouriAwakening(player, 0)`（见 §2.6）。
3. 金鲤缺 P1 移植排期，属未排期欠账，不属本规格范围。

## §7 实机取证（2026-11-10，P0 双线）

**neoforge-1.21.1**（`launch.sh --version 1.21.1 --quickplay testworld`，log `scripts/test/linux/logs/1.21.1-20261110-111530.log`）：

- 注册表里 souri 相关物品 id **只有** `astral_dice:souri_sign`；`BR.ITEM.get(new RL("astral_dice:souri_sign")).getDefaultInstance()` ⇒ desc `item.astral_dice.souri_sign`，name「金鲤立牌」。
  ⚠️ `Item.of("astral_dice:souri_sign")` 会静默返回 air，取证必须走 `BR.ITEM.get(...)`。
- tooltip 全 16 行实读成功（服务端）：最后状态行为 `金鳞：2 / 4`——该行读的是**观看者**附件（`ModTooltipHandler.java:1646-1656`），Dev 当时佩戴且有 2 层，故新造 stack 也显示 2/4，属预期。
- `getGoldScales=2`、`isEquipped=true`、`hasDragon=false`、`GOLD_SCALE_MAX=4`；`souri_gold_scale`/`souri_dragon` 效果与 `souri_drink` 实体均已注册。

**forge-1.20.1**（log `scripts/test/linux/logs/1.20.1-20261110-112805.log`，3,756 行）：

- `LAUNCH: READY` + `WORLD_READY`；`BR.ITEM` 1,593 项，souri 相关 id 同样只有 `astral_dice:souri_sign`；`getGoldScales=2`、`isEquipped=true`。
- tooltip 文本在该线**未**取证：1.20.1 的 Rhino 够不到枚举静态量（`TooltipFlag$Default.NORMAL` 报 `InternalError`，且无 `Java.type`）。判定改为「读 lang 键 + 1.21.1 tooltip 全行」两条合并取证。

## §8 复跑清单

```bash
cd /mnt/f/MCProject/astral_dice_multiloader-next
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
bash scripts/test/linux/env.sh --quiet            # 期望 ENV_RESULT: WARN，0 blocking
bash scripts/test/linux/launch.sh --version 1.21.1 --quickplay testworld --timeout 900 --wait-world 300
# 期望 LAUNCH: READY + LAUNCH: WORLD_READY，autojs health=ok 200
```

AutoJS 验收（`~/.dsh` 之外的 MCP 端点 `http://127.0.0.1:25585/mcp`，`tools` = `status` / `eval_js`，参数 `{side:"server", code}`）：

1. `BR.ITEM.keySet().filter(k => String(k).includes("souri"))` ⇒ 恰好 1 个 id。
2. `BR.ITEM.get(new RL("astral_dice:souri_sign")).getDefaultInstance()` ⇒ 名称「金鲤立牌」。
3. `SouriSignManager.GOLD_SCALE_MAX` ⇒ 4；`getGoldScales(p)` 与佩戴状态一致。
4. tooltip：`stack.getTooltipLines(Java.loadClass("net.minecraft.world.item.Item$TooltipContext").of(p.serverLevel()), p, TooltipFlag.NORMAL)`（`p.level()` 是属性不是方法）。

收尾必须 `bash scripts/test/linux/stop.sh --version 1.21.1` ⇒ `LINUX_STOP: OK`，并确认 25585/25565/25575 无监听。

## §9 索引登记

- 立牌规格同类：`docs/features/{fengshui,mamushi,nardis,teru,kangel-ame,lingling}-sign-spec.md`
- P1 移植可行性：`docs/features/fabric-1211-port-feasibility-plan.md`
- 规则正本（本机）：`docs/agent/AGENTS-dsh-linux.md`
