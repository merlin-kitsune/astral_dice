# TLMDice 接入「类玩家生物能力面」任务 prompt

> 用法：整段复制给 TLMDice 仓库（`F:/MCProject/touhou_little_maid_astraldice`）的会话即可。
> 上游（`astral_dice_multiloader-next`）本批落点：`34d2d3a3` / `831ec4d8` / `03ab1b86` / `842b17a8` / `83ac4ced`，
> 契约文档 `docs/features/player-like-entities-api.md`。

---

你是 `F:/MCProject/touhou_little_maid_astraldice`（TLMDice，modid `touhou_little_maid_astra_dice`，双线 `neoforge-1.21.1` + `forge-1.20.1`）的资深维护者。不要使用任何人设、语气修饰或昵称自称，按职业身份以中性技术口吻回答。

## 0. 背景（已完成，不需要你重做）

上游《星之骰戏》新增了「类玩家生物」能力面：附属可以把某类生物注册成「与玩家同级的目标」，并逐项打开能力位。与本仓直接相关的接口（全部在 `com.merlinkitsune.astral_dice`）：

```java
// target/PlayerLikeTargets
register(ResourceLocation id, Handler handler)
interface Handler {
    boolean matches(LivingEntity entity);
    boolean giveCard(ServerPlayer giver, LivingEntity receiver, ItemStack card);
    default boolean giveItem(LivingEntity receiver, ItemStack stack) { return false; }
    default boolean supportsSigns() { return false; }
    default boolean supportsChips() { return false; }
    default boolean supportsShop()  { return false; }
}
isPlayerLike(LivingEntity) / matchesSelection(TargetType, Player, LivingEntity) / giveCard(...) / giveItem(...)

// compat/playerlike/PlayerLikeWallets
register(ResourceLocation id, Predicate<LivingEntity> matches, PlayerLikeWallet wallet)
resolve(LivingEntity) -> PlayerLikeWallet { isAvailable(); long balance(LivingEntity); boolean withdraw(LivingEntity, int); }

// shop/ChipShopService
quote(LivingEntity) -> Quote
purchase(LivingEntity holder, int offerIndex) -> Result { OK, INVALID, NO_OFFER, NO_WALLET, NO_COINS, DELIVERY_FAILED }
grantRefresh(LivingEntity) -> boolean
grantDiscount(LivingEntity, int amount, long ticks)

// item/sign/BaseSignItem
activateFor(LivingEntity owner) -> boolean        // 非玩家主动入口；立牌侧需覆写 activateForEntity 才生效

// shop/ChipShopState（上游已放宽宿主）
get(LivingEntity) / store(LivingEntity, ChipShopState)
```

上游同时给 `touhou_little_maid:maid` 授予了 curios 的 `stand`（1 槽）与 `chip`（3 槽）——见上游数据文件 `data/astral_dice/curios/entities/player_like_maid.json`。本仓不需要重复授予。

## 1. 前置检查（先做，不要跳）

1. 上游依赖版本必须是 **`26.4-SNAPSHOT.7` 或更新**（本仓 `gradle.properties` 里 `astral_dice` 的引脚同步改；`.6` 没有这些类）。
2. 若本地 mavenLocal 没有 `.7` 的 neoforge/forge 工件，先在上游仓库跑
   `./gradlew :neoforge-1.21.1:publishToMavenLocal :forge-1.20.1:publishToMavenLocal`（只本地发布，不 push、不打 tag）。
3. 本次**只写代码**：不启实机、不部署、不 push；构建只允许 `compileJava`（必要时 `build`）。

## 2. 需要改的内容

### R1 注册类玩家 handler（新增，唯一入口）

新增 `com/merlinkitsune/tlmdice/compat/MaidPlayerLikeHandler.java`，在 `TLMDice#onCommonSetup` 的 `enqueueWork` 里注册（与 `MaidDiceParticipants.register()` 同处，只注册一次）：

- `matches(entity)`：`entity instanceof EntityMaid`。
- `giveCard(giver, receiver, card)`：把卡放进女仆**官方背包容器的空格**；放不下返回 `false`（上游据此掉地）。不要写 NBT 自定义存储。
- `giveItem(receiver, stack)`：同上，供筹码/奖励用。
- `supportsSigns()=true`、`supportsChips()=true`、`supportsShop()=true`。
- 注册 id 用 `ResourceLocation.fromNamespaceAndPath(MODID, "maid")`，日志加机器行 `AP_TLM_PLAYER_LIKE: registered`。

### R2 钱包（新增）

新增 `com/merlinkitsune/tlmdice/compat/MaidWallet.java`，实现 `PlayerLikeWallet`，并在同一 Setup 期用 `PlayerLikeWallets.register(...)` 绑定：

- `balance(maid)` / `withdraw(maid, amount)`：**从女仆主人（`EntityMaid#getOwner()`，离线则视为无钱包）的星币账户代扣**；主人不在线或余额不足一律返回 `false`（`withdraw` 前先 `StarEngineEconomy.hasBalance(owner, amount)`）。
- `isAvailable()`：主人解析器可用即为 `true`。
- 口径：**女仆不自建账户**，不允许「免费购买」兜底。

### R3 商店意图实装（替换 B-07 桩）

`intent/MaidShopIntent.java` 现在 `isAvailable()` 恒 `false`、`BLOCKED_BY="B-07"`。改为：

- 记录 `int offerIndex`（替代 `cardTypeId` 字符串；上游服务面按候选下标购买）。
- 新增执行方法：`apply()` → `ChipShopService.purchase(maid, offerIndex)`；结果 `Result` 映射为日志 `AP_TLM_SHOP: maid=…:index=…:result=…`，并删掉 `BLOCKED_BY` 常量与 `unavailableReason()`（或改成空实现并标注已接通）。
- 购买后**不要在 TLMDice 侧重抽候选**（上游服务只推进购买次数；换批在 UI 生命周期里做）。

### R4 主动技能意图实装（替换 B-03 桩）

`intent/MaidActiveSkillIntent.java` 改为调用上游立牌主动入口：

- `apply()` → 找到女仆 `stand` 槽里第一枚 `BaseSignItem`，调 `sign.activateFor(maid)`；返回 `false` 时记 `AP_TLM_SIGN_ACTIVE: result=false:reason=unsupported`。
- 注意：上游当前只有**已覆写 `activateForEntity`** 的立牌才真正支持非玩家主动；本仓只负责调用与日志，**不要**在上游代码里加 hack。

### R5 转移/发牌意图实装（替换 B-07 桩）

`intent/MaidTransferIntent.java` 改为调用 `PlayerLikeTargets.giveCard(...)` / `giveItem(...)`：

- 女仆作为**发出方**时 `giver` 传 `null`（上游签名允许）；女仆作为**接收方**时由上游 handler 落背包。
- 返回 `false` ⇒ 掉地由调用方处理（与玩家路径同口径）。

### R6 文档与门禁

- 更新本仓 `docs/status/2026-11-08-progress-report.md`：B-03 / B-07 状态从「阻塞」改为「已接通（上游 .7）」。
- 本仓 `AGENTS.md` 的已知限制里，把「女仆不能装立牌/筹码」一条改成「依赖上游 curios 槽位授予（如需自查，读 `data/astral_dice/curios/entities/player_like_maid.json`）」。

## 3. 验收（不做实机，全部静态）

1. `./gradlew :neoforge-1.21.1:compileJava :forge-1.20.1:compileJava` 两条线通过。
2. `grep -rn "BLOCKED_BY" src/main/java` = 0 命中（三个意图桩全部摘掉）。
3. `grep -rn "PlayerLikeTargets.register\|PlayerLikeWallets.register" src/main/java` 各 ≥1，且都在 `onCommonSetup` 的 `enqueueWork` 内。
4. 行尾复核：Java 与 json CRLF、`bareLF=0`。
5. 输出一份「改了什么 / 未改什么 / 待上游补什么」的清单；**不要**改上游仓库、不要 push。

## 4. 硬约束

- 不启实机、不部署、不 `git push`；本地提交按「早提交勤提交」，每个 R 一项。
- 上游 `astral_dice` 只作为依赖使用，**不得**直接改其源码来绕过能力位。
- 不新增第三方前置；不删除既有 `curios` 依赖。
- 若某条 R 因上游尚未支持而无法落地（例如没有立牌覆写 `activateForEntity`），把它写成「待上游」条目，而不是在 TLMDice 里伪造效果。

---

（结束。按 R1→R6 顺序做，每完成一项立刻本地提交。）
