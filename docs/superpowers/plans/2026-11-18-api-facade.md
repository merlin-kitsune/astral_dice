# 计划：AstralDiceApi 门面缺口补齐（26.4-SNAPSHOT.79）

来源：`docs/features/api-facade-gap.md`（设计）；用户裁决 m00403 / m00549（按文档实施、边做边提交）。
目标：把 TLMDice 需要的九类内部实现能力收进门面，使附属模组除 `astral_dice.api.` 外零 import。

## 硬约束（实读所得）

1. `common/` 是五线共享 srcDir（见各线 `build.gradle` 的 `sourceSets.main.java.srcDir`），common 里的代码必须五线同时编译。
2. 五线 `ModEffects.DICE_BLESSING` 注册器类型不同：neo-1.21.1 / neo-26.1.2 是 `DeferredHolder`，forge-1.20.1 / fabric-1.20.1 是 `RegistryObject`，fabric-1.21.1 还要 `Effects.holder(...)`。⇒ common 只能走平台默认方法。
3. neo-26.1.2 线 `ResourceLocation` 已改名 `Identifier`（`PlayerLikeTargets.register(Identifier, Handler)`）。⇒ common 不得出现这两个类型，标签统一用 `String`。
4. forge-1.20.1 的立牌激活是**静态** `BaseSignItem.activateFor(LivingEntity)`（自行从 curios `stand` 槽解析）；其余四线是实例方法 `stack.getItem() instanceof BaseSignItem sign && sign.activateFor(holder)`。⇒ 激活也走平台层。
5. 平台对象在 `AstralDiceMod.java:51` `AstralDicePlatforms.install(...)` 安装，早于脚本加载。

## 任务

### T1 新增 api/playerlike 类型
- `common/src/main/java/com/merlinkitsune/astral_dice/api/playerlike/PlayerLikeCapability.java`：`enum { SIGNS, CHIPS, WALLET }`。
- `.../PlayerLikeHandler.java`：`matches(LivingEntity)`、`giveCard(ServerPlayer, LivingEntity, ItemStack)`、`default giveItem(...)=false`、`default supports(PlayerLikeCapability)`（SIGNS/CHIPS 默认 true，WALLET 默认 false）。
- `.../PlayerLikeWallet.java`：`isAvailable()`、`default isAvailable(LivingEntity)`、`long balance(LivingEntity)`、`boolean withdraw(LivingEntity,int)`、`default boolean deposit(LivingEntity,int)=false`（金额用 int，与内部接口一致）。
- `.../PlayerLikeTargetsAdapter.java`：把 `PlayerLikeHandler` 包成内部 `PlayerLikeTargets.Handler`（`supportsSigns/Chips` 转发 `supports(...)`）。
验证：文件存在、无 loader import；T4 编译。

### T2 平台接口三项默认方法
`common/.../platform/AstralDicePlatform.java` 追加：
- `default void registerPlayerLike(String id, PlayerLikeHandler handler)`
- `default boolean activateSign(ItemStack stack, LivingEntity holder)` → false
- `default boolean hasDiceBlessing(LivingEntity entity)` → false

### T3 五线平台实现 override
每线：
- `registerPlayerLike`：`PlayerLikeTargets.register(<id 转换>, new PlayerLikeTargetsAdapter(handler))`
  - 1.21.1 三线：`ResourceLocation.tryParse(id)`；forge-1.20.1：`ResourceLocation.tryParse(id)`；neo-26.1.2：`Identifier.tryParse(id)`；null 时 log warn 并 return。
- `activateSign`：四线实例调用；forge 静态调用。
- `hasDiceBlessing`：转本线 `ModEffects.hasDiceBlessing(entity)`。

### T4 AstralDiceApi 新增面
追加七段（在「收口」前）：类玩家注册/查询、骰子与立牌查询、骰子祝福、骰战参与者、类玩家钱包（api 自持表）、复合敌对探针。逐条签名见设计文档 §3。

### T5 五线 DiceCombatEvents 改走门面
静态块把 `HostileTargets.installExtraHostileProbe(DiceCombatEvents::isTrainingDummy)` 换成
`AstralDiceApi.registerHostileProbe(DiceCombatEvents::isTrainingDummy)`，避免后装覆盖。

### T6 三线 PlayerLikeWallets.resolve 兜底
`PlayerLikeWallets.resolve` 在内部绑定未命中后追加 `AstralDiceApi.playerLikeWallet(holder)`（api 侧不回查内部，无递归）。

### T7 版本与文档
五线 `gradle.properties` `mod_version` → `26.4-SNAPSHOT.79(...)`；`CHANGELOG.md` / `CHANGELOG_ZH.md` 新增 .79 段；`docs/features/api-facade-gap.md` 回写实施偏差（String id、平台层激活）。

### T8 验证
- `cd /f/MCProject/astral_dice_multiloader-next && ./gradlew.bat assemble --offline --console=plain`，要求五线全绿。
- 门禁 grep：`com.merlinkitsune.astral_dice.(target|item|effect|combat|compat|shop)` 在 TLMDice（重写后）零命中。
- 既有 KubeJS 读数不回退：`signs=36;chips=70;diceTiers=13;frozen=true`。

### T9 提交
按「边做边提交」：T1–T6 一个提交（门面），T7 版本号可并入或单独。
