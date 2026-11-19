# API 归属：主模组只做实现（26.4-SNAPSHOT.94 设计）

日期：2026-11-19　分支：multi-dev-next　状态：已定稿执行

## 1. 约束

用户约定（m01848）：main 线跟随 main 库，dev-next 线跟随 next 库，禁止跨区调用；调用 API 一律走 StarEngine 库，禁止再把 api 放在主模组；主模组必须是库 API 的实现方。

## 2. 目标形态

- 库（starengine_next）新增命名空间 `com.merlinkitsune.starengine.api.astral.*`，承载全部对外契约：36 个接口/枚举 + 门面 + SPI。
- 主模组删除 `com.merlinkitsune.astral_dice.api.*` 整包（38 类），只保留实现：`apiimpl.AstralDiceServiceImpl`、各 loader 的平台实现、内部管理器。
- 附属（TLMDice）import 从 `astral_dice.api.*` 改为 `starengine.api.astral.*`，方法名与签名不变。

## 3. 门面与 SPI

- `AstralDiceApi`（库）：102 个静态方法中保留 100 个；摘掉 `platform()`（全仓零调用）与已弃用的 `registerDiceTier(String,Supplier,IntUnaryOperator)`（返回模组内部 `item.dice.DiceTier`，签名无法进库）。
- `AstralDiceService`（库）：上述 100 个方法的实例版接口，是唯一实现契约。
- `AstralDiceApi.install(service)` / `service()`：未安装时所有门面调用抛 `IllegalStateException`，避免静默空实现。
- 主模组 `AstralDiceMod` 初始化处（五线，平台 install 之后）安装唯一实例。
- `freezeContent()` 仍是门面静态方法（主模组在服务器启动后调用），委托给 Service。

## 4. 类型搬迁映射

| 旧（主模组） | 新（库） |
|---|---|
| api.AstralDiceApi | api.astral.AstralDiceApi（门面）+ api.astral.AstralDiceService |
| api.combat.*（20） | api.astral.combat.* |
| api.data.*（3） | api.astral.data.* |
| api.client.*（3） | api.astral.client.* |
| api.playerlike.*（4） | api.astral.playerlike.* |
| api.platform.AstralDicePlatform | api.astral.platform.AstralDicePlatform |
| api.choice.CardChoiceType | api.astral.choice.CardChoiceType |
| api.event.*（5） | api.astral.event.* |

## 5. 处理掉的两处模组耦合

- `CardChoiceType.matches(ItemStack)` 原直接调 `item.ModItems`：改为委托 `AstralDiceApi.matchesCardChoice(this, stack)`，由主模组 Service 用 ModItems 实现。
- `AstralEvent` 的 javadoc `{@link AstralEventSystem#EVENT_TARGET_RADIUS}` 改为纯文本，去掉对模组类的文档期引用。

## 6. 版本与验证

- 库 lib_version 26.4-SNAPSHOT.14 → .15；主模组 mod_version .93 → .94；TLMDice .88 → .89。
- 验证：五线 assemble 成功；`starengine-*.jar` 含 `api/astral/**`；`astral_dice-*.jar` 不含 `com/merlinkitsune/astral_dice/api/`；TLMDice 两线 assemble 成功；mods.toml / fabric.mod.json 区间下限 .15。
- 主线（multi-main + starengine main）本批不动，dev 验证通过后再以 1.2.1 搬。