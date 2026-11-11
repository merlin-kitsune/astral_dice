# 少主立牌（al）规格与实现记录

> 本文是**实现记录 + 语义冻结**，不是前置设计稿：P0 基准两线代码 2026-11-08 落地（用户裁决），2026-11-10 完成原 P1 三线（fabric 两线 + 26.1.2）移植后补本文；2026-11-11 用户划级后 26.1.2 归 P0。
> **代码为真源**：本文任何数字若与代码不符，以代码为准，并回改本文。
> 基准：`multi-dev-next` @ `5dbf39d4` 之后（P1 移植批）；P0 实机取证 2026-11-10 上午（neoforge-1.21.1 金鲤轮次顺带确认 al 注册）。
> 落地线：**五线全部落地**（P0 `neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`；P1 `fabric-1.20.1` / `fabric-1.21.1`。原 P1 三线于 2026-11-10 移植、编译、datagen 跑通；2026-11-11 起 26.1.2 归 P0；实机取证见 §7）。

## §0 定名与命名门禁

| 项 | 值 | 出处 |
|---|---|---|
| 中文名 | 少主立牌 | `lang/zh_cn.json` `item.astral_dice.al_sign` |
| 英文名 | A.L. Sign | `lang/en_us.json` |
| 日文名 | アルのスタンド | `lang/ja_jp.json` |
| wiki 页 | A.L.（Syndicate Prince） | `tools/sign_display_names.json` |
| 门禁 `lines` | `neoforge-1.21.1` / `forge-1.20.1` / `fabric-1.20.1` / `fabric-1.21.1` | 同上（26.1.2 尚未纳入门禁 LINES） |

门禁读数（2026-11-10 本批）：`python3 tools/verify_sign_display_names.py` ⇒ `GATE: PASS`，检查项 **110,663**。

## §1 标识与常量（冻结值）

| 项 | 值 | 出处（neoforge-1.21.1；forge 行号略异） |
|---|---|---|
| 注册 id | `astral_dice:al_sign` | `item/ModItems.java:1157`；其余线：forge 1135 / fabric-1.20.1 1044 / fabric-1.21.1 1188 / neo26 1042 |
| 稀有度 | `epic`（史诗） | `item/ModItems.java` 注册行（neo26 曾误写 legendary，已按 P0 改正） |
| 堆叠 | `stacksTo(1)` | 同上 |
| Curios 栏位 | `stand`（立牌） | `data/curios/tags/item/stand.json`；另见 `data/astral_dice/tags/item/signs.json` |
| 选择器动作 id | `ACTION_ID = "astral_dice:al_gift"` | `item/sign/AlSignItem.java:62` |
| 每张卡星币 | `COIN_PER_CARD = 2` | `:65` |
| 每次给牌双方星光 | `STARLIGHT_PER_GIFT = 1` | `:67` |
| 多少层星光换 1 攻/防 | `STARLIGHT_PER_ATTRIBUTE = 4` | `:69` |
| 属性修饰符 id | `al_starlight_attack` / `al_starlight_armor` | `:70-71` |
| 同 tick 去重窗口 | `BATCH_EXPIRE_TICKS = 20` | `:73` |
| 选卡下限 | `minSlots = 1`（槽位 3，即可给 1~3 张） | `AlSignItem.java:129` → `choice/CardChoiceManager.start(player, 3, 1, ANY, …)` |
| 折扣常量 | `ChipShopDiscounts.DISCOUNT` / `.DURATION_TICKS` | `shop/ChipShopDiscounts.java` |

状态载体：

- 待结算目标 = 附件 `AL_PENDING_TARGET`（UUID 字符串，结算后立即置空）。
- 限时折扣 = 附件 `CHIP_DISCOUNT_AMOUNT` + `CHIP_DISCOUNT_UNTIL`（tick），原 P1 三线（fabric 两线 + 26.1.2）本批新增，与 P0 同名同义。
- 冷却 = 玩家级附件 `SIGN_ACTIVE_COOLDOWN_END` / `SIGN_ACTIVE_MAX_COOLDOWN`（立牌统一口径，不新建）。

## §2 语义

### §2.1 主动「强者怜悯」

1. 佩戴者按 J → `TargetSelectionRegistry` 的 `GiftAction`（`targetType=PLAYER`、`allowSelf=false`）开始选目标，动作栏 `msg.astral_dice.al_ready`。
2. 目标确认（`apply`）：`target == player`、`!PlayerLikeTargets.isPlayerLike(target)` 直接拒；背包无卡（`countCards < 1`，战斗牌 + 效果牌）⇒ `msg.astral_dice.al_no_cards`。
3. 记 `AL_PENDING_TARGET`，开选卡界面：3 槽、**最少 1 张**（`minSlots=1`）、类型 `ANY`。开界面失败 ⇒ 清 pending + 报「没有可赠予的卡牌」。
4. 结算 `resolve`（服务端）：逐张 `PlayerLikeTargets.giveCard(caster, target, card.copyWithCount(1))`；目标收不下（背包满 / 附属未实现）⇒ `target.spawnAtLocation(one)`，**仍计为已给**。
5. 星币：`given * 2`，`StarEngineEconomy.deposit(caster, coins)`（仅 `ServerLevel`）。
6. B 面：施放者与目标**各一次** `ChipShopDiscounts.grant(…, DISCOUNT, DURATION_TICKS)`；`grantRefresh` 只给玩家（`ChipShopState.withRefreshGranted()`，目标为 `ServerPlayer` 时再给一次）。
7. 冷却三件套：`setSignActiveCooldownEnd(now + WeirdDiceHandler.signCooldownTicks(caster))`、`setSignActiveMaxCooldown(…)`、`CurrentCoreChipItem.onActiveSkillUsed(caster)`；动作栏 `msg.astral_dice.al_done(given, coins)`；日志锚点 `AP_AL_GIFT`。
8. 取消（chosen 空）⇒ 不结算、不进冷却。

### §2.2 被动「双赢」A 面：给牌 ⇒ 双方各 +1 星光

- 挂点 = 发牌**唯一漏斗**：`VitaminPillChipItem.giveCard` 成功分支 + `PlayerLikeTargets.giveCard`（附属注册生物）→ `CardGivenHooks.fire(giver, receiver)` → `AlSignItem.onCardGivenToOther`。
- 门控：发牌方必须是 `ServerPlayer`（未知发牌方跳过并打 `AP_AL_GIFT_STARLIGHT: skipped=unknown_giver`）；两侧都不能在客户端；`giver == receiver` 不算；**发牌方必须佩戴本立牌**（`isEquipped`）。
- 去重：同一 tick、同一受益人只结算一次（`BATCHES`，20 tick 后回收）。

### §2.3 被动「星光」属性

- `steps = max(0, 星光层数 / 4)`；`SignAttributeUtil.apply(player, ATTACK_DAMAGE, "al_starlight_attack", steps, ADD_VALUE)`；护甲同 steps × `DiceCombatModifiers.PLAYER_ARMOR_PER_DEFENSE`。
- 逐 tick 在 `onCurioTick` 刷新（同 id 覆盖写，值不变时是纯查表）；`onUnequip` 移除两条。

### §2.4 限时折扣

- `ChipShopDiscounts.effectivePrice(player, base) = max(MIN_PRICE=5, base - amount)`，`until <= now` 视为无折扣。
- 筹码商店**三处必须同源**：`ChipShopMenu` 的 DataSlot、`price()`、`handleBuy` 扣款。原 P1 三线本批把 `int price = state.price()` 全部改为 `ChipShopDiscounts.effectivePrice(...)`。

## §3 落点清单

P0（各 4 个 Java 变体 + 集成点）：`item/sign/AlSignItem.java`、`item/sign/CardGivenHooks.java`、`shop/ChipShopDiscounts.java`、`target/PlayerLikeTargets.java`；集成 `item/ModItems.java`、`item/chip/VitaminPillChipItem.java`、`target/TargetSelectionManager.java`、`shop/ChipShopMenu.java`、`event/ModTooltipHandler.java`、`init/ModCreativeTabs.java`、`component/ModAttachments.java`、`datagen/ModItemModelProvider.java`、`datagen/ModRecipeProvider.java`。

原 P1 三线额外改了选卡链路（**本批新增的结构性基建**）：`choice/CardChoiceManager.java`（`minSlots` 变长下限 + clamp）、`choice/CardChoiceType.java`（`titleKey(slotCount, minSlots)` → `title_variable_*`）、`screen/CardChoiceMenu.java`（菜单内按 minSlots 判定可确认）；fabric 两线与 neo26 另动了 `client/TargetSelectionClient.java`（客户端放行同源）。

资源（各线）：
- 贴图 `assets/astral_dice/textures/item/al_sign.png`（五线同 md5 `f073b69e1a64ae4856bdc386f2892738`）
- 物品模型/物品定义：fabric 两线 `src/generated/resources/assets/astral_dice/models/item/al_sign.json`；neo26 `src/generated/clientResources/assets/astral_dice/items/al_sign.json` + `models/item/al_sign.json`
- 配方 `data/astral_dice/recipe(s)/al_sign.json`：`BXB/KCK/XDX`（B=black_dye、X=star_coin_dust、K=gold_block、C=blank_chip、D=diamond_dice）
- 进度 `advancement(s)/recipes/misc/al_sign.json`
- 手册 `patchouli_books/astral_guide/{en_us,zh_cn}/entries/signs/al_sign.json`

## §4 文本（13 键 × zh/en/ja 三语，五线齐）

`item.astral_dice.al_sign`；`tooltip.astral_dice.sign.al.{active,passive}`；`tooltip.astral_dice.sign.al_{active,passive}`；`msg.astral_dice.{al_ready,al_done,al_no_cards}`；`astral_dice.guide.entry.al_sign.{1,2}`；`gui.astral_dice.card_choice.title_variable_{any,combat,effect}`（minSlots 变长标题，P0 已有，原 P1 三线本批补齐）。

tooltip 结构（自上而下）：名称／栏位／按键提示／主动段「强者怜悯」／被动段「双赢」／状态行（冷却剩余）／模组名。实现位置 `event/ModTooltipHandler.java:1717`（P0 neo21）。

## §5 边界（明确不做）

1. 主动**不可对自己**（`allowSelf()=false`），且目标必须过 `PlayerLikeTargets.isPlayerLike`。
2. 「给牌」以**送出**为准，不看目标是否真的进背包（掉地也算）；`chosen` 为空不算技能使用（不进冷却）。
3. 折扣**不叠加**（attachment 覆盖写），刷新每次最多 +1 次。
4. 原 P1 三线已落地 `activateForEntity`：`AlSignItem` 覆写后以 `needs_player_session` 拒绝非玩家（与 P0 同口径），缺口不在入口而在会话依赖。

## §6 跨线现状与语义差异

| 线 | 新增 Java | 新增资源 | lang al 键 | 编译 | datagen | 状态 |
|---|---|---|---|---|---|---|
| neoforge-1.21.1 | 4 | 既有 | 13 | ✅ | — | ✅ 实机（P0） |
| forge-1.20.1 | 4 | 既有 | 13 | ✅ | — | ✅ 实机（P0） |
| fabric-1.20.1 | 4 | 10 | 13 | ✅ | ✅ | ✅ 实机（P1，§7） |
| fabric-1.21.1 | 4 | 10 | 13 | ✅ | ✅ | ✅ 实机（P1，§7） |
| neoforge-26.1.2 | 4 | 11 | 13 | ✅ | ✅ | ✅ 实机（P0，§7） |

**原 P1 三线的语义差异（2026-11-10 移植、2026-11-11 补实体级钩子；2026-11-11 划级后 26.1.2 归 P0、fabric 两线仍 P1，逐条经源码实读）**：

1. `activateForEntity(LivingEntity)` 已移植 —— 原 P1 三线 `BaseSignItem` 均有该钩子、`SignEntitySkills` 已落地；`AlSignItem` 显式覆写并恒 `deny("al", …, "needs_player_session")`，玩家路径等价。
2. 刷新只发给 `ServerPlayer` 目标：原 P1 的 `ChipShopState.get/store` 形参是 `Player`（P0 neo21 是 `LivingEntity`），非玩家类玩家生物拿不到刷新（折扣仍发）。
3. fabric-1.20.1 的 `CardChoiceType` 原来只有单参 `titleKey`，本批补了两参版本以承载 `title_variable_*`。
4. neo26 的动作 id 按 26.1.2 惯例写成 `al_gift`（与 P0 `astral_dice:al_gift` 同值）；`Inventory#items` 私有 ⇒ 用 `getNonEquipmentItems()` + 副手索引；`spawnAtLocation(ServerLevel, ItemStack)`；附件序列化补 `fieldOf("value")`。
5. 原 P1 三线 datagen 已于 2026-11-10 全部跑通，手写 generated 产物被 datagen 覆盖/确认为真值（无删除、无纯格式漂移）。

## §7 实机取证

2026-11-10 14:11–14:24 串行三线冒烟（客户端内置服务器 + AutoJS 探针；每条线 `stop.sh` 后端口空才起下一条）。报告：`temp/live-al/report.md`（163 行）；截图 `temp/live-al/{fabric-1.20.1,fabric-1.21.1,26.1.2}.png`。**三线全 PASS，无新增 crash-report**（计数 4 / 6 / 3 = 起测前基线）。

| 线 | 结论 | 世界就绪 | 日志 | LINUX_STOP |
|---|---|---|---|---|
| fabric-1.20.1 | PASS | 16.220 s | `scripts/test/linux/logs/fabric-1.20.1-20261110-141246.log` | OK |
| fabric-1.21.1 | PASS | 18.137 s | `scripts/test/linux/logs/fabric-1.21.1-20261110-141619.log` | OK |
| neoforge-26.1.2 | PASS | 12.184 s | `scripts/test/linux/logs/26.1.2-20261110-142245.log` | OK |

三线共同读数（逐项实测）：

1. 注册：`astral_dice:al_sign` 存在，`getDescriptionId()=item.astral_dice.al_sign`，显示名「少主立牌」，`maxStackSize=1`，`rarity=ASTRAL_DICE_EPIC`。
2. tooltip 运行时：**1.21.1 与 26.1.2 全行取证成功**，四段齐全（名称 / 槽位行 / 按键提示 / 主动「强者怜悯」/ 被动「双赢」/ 模组名）；1.21.1 的槽位行是 Trinkets 文案「可装备于 立牌 饰品槽」，26.1.2 是「栏位: 立牌」。**1.20.1 未能运行时取证**（该线 KubeJS Rhino 对 `TooltipFlag$Default` 这类嵌套枚举不暴露任何成员，`.NORMAL`/`.values()` 均报 has no public instance field or method）⇒ 改按 lang 键 `tooltip.astral_dice.sign.al_active`/`al_passive` + `event/ModTooltipHandler.java:1560/1562` 静态取证。
3. minSlots 选卡会话：三线均用 6 参 token 重载 `start(p, 3, 1, CardChoiceType.ANY, token, handler)` ⇒ `startRet=true`、`hasOpen=true`、abandon 后 `hasOpen=false`（无残留会话）。注：5 参重载在 1.20.1 的 Rhino 下对 JavaAdapter 二义，日志会列出候选，属内核限制。
4. 折扣：1.21.1 / 26.1.2 `before=100 → grant(5, 6000) → 95`，`amount=5`、`until > gameTime`（115820 > 109820 / 94122 > 88122）、`remaining=5`、`effectivePrice(8)=5`（下限）；1.20.1 同会话先前探针已 grant，故读数 95→95（原价 100），算术一致。
5. 星光：三线 `StarLightManager.get` 0 → `add(1)` 返回 1 → after=1。
6. 赠卡 no-op（未佩戴立牌时）：三线 `onCardGivenToOther` 对牛 / 猪 / 僵尸均 before=after、无 `AP_AL_GIFT_STARLIGHT` 锚点 —— 与源码 `!isEquipped(giver) ⇒ 静默返回` 一致。
7. souri 回归：三线 `souri_sign` 仍注册、`getGoldScales=0`、`hasDragon=false`、`GOLD_SCALE_MAX=4`。
8. 资源：三线 client assets `textures/item/al_sign.png` 与 `models/item/al_sign.json` 均 `isPresent()=true`；数据侧 1.20.1 复数 `recipes/`、26.1.2 单数 `recipe/` 命中，26.1.2 另有 `items/al_sign.json`。
9. 日志：ERROR 三线分别为 3 / 5 / 78 行，**去重后全部是既有噪声**（Sodium NVIDIA workaround、`No data fixer registered for …`、Iris uniform、KubeJS 脚本缺类 `StarCoinWalletState`/`ModEffectRemoval`），无 astral_dice 回归。

**明确未验证**（沿用报告）：① 1.20.1 tooltip 运行时文本（Rhino 限制，只有 lang + 源码取证）；② 星光被动的实际战斗数值（testworld 无战斗设置，只验到层数递增）；③ 主动技能完整赠卡链路（选人→选卡→赠出→双方星光 + 星币）——testworld 背包无卡，只验到会话开关与 UI 打开；④ RCON / 专用服务器（本轮为客户端内置服务器）；⑤ 三张截图截在「选择三张卡片」UI 前台时（服务端会话已 abandon、客户端 UI 残留），不是纯世界视角。

## §8 复跑清单（改本立牌必做）

1. `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --no-daemon :fabric-1.20.1:compileJava :fabric-1.21.1:compileJava :neoforge-26.1.2:compileJava`
2. datagen 四条任务（fabric 两线 `runDatagen` + neo26 `runServerData`/`runClientData`，跑前移出 sodium+iris）。
3. `python3 tools/verify_sign_display_names.py` + `python3 -B docs/agent/verify-split.py` + `python3 tools/gen_agents_toc.py --check`。
4. 三线实机串行冒烟（测试台 `launch.sh --version <线>` → `stop.sh` 必须 `LINUX_STOP: OK`）。
