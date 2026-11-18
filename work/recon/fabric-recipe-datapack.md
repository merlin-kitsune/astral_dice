# Fabric 1.20.1「配置门控筹码配方」取证与方案设计报告

- 日期：2026-11-02
- 仓库：`F:/MCProject/astral_dice_multiloader`（分支 `multi-main`，HEAD `6fe38138`，本次实测确认）
- 目标线：`fabric-1.20.1`（Fabric 1.20.1，Loom）
- 对照线：`forge-1.20.1` / `neoforge-1.21.1` / `neoforge-26.1.2`
- 工作方式：**纯只读取证**。未修改任何产品文件、未构建、未启动游戏、未执行 git 写操作。本报告是本次任务唯一新增产物。
- 取证手段：`grep` / `find` / `sed` / `unzip` + `javap`（JDK 21，Zulu）读 Loom 缓存的**带 Mojang 官方映射**的 1.20.1 merged jar，以及 fabric-api 聚合 jar 的内嵌模块 jar。

## 0. 结论速览

| 问题 | 结论 |
|---|---|
| 控制键 | `enable_chip_recipes`（布尔，默认 `false`）。`chip_recipes_enabled` 是**配方条件 id**，不是配置键。 |
| Fabric 线有没有这个键 | **有**：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/config/ModCommonConfig.java:108-109`，与 forge / neoforge 同名同默认。 |
| 三条对照线怎么门控 | 配方 JSON 里带条件块：forge 用**顶层** `"conditions"`，两条 neoforge 线用**顶层** `"neoforge:conditions"`；条件 type 字符串三线相同 `astral_dice:chip_recipes_enabled`，实现类都是 `ICondition`（forge 走 `CraftingHelper#register`，neoforge 走 `NeoForgeRegistries.CONDITION_SERIALIZERS`）。 |
| Fabric 线为什么关不掉 | **现状**：Fabric 线 108 个生成的配方 JSON **一个条件块都没有**（实测 0/108），模组自己也没注册任何条件。但「Fabric 1.20.1 不具备配方条件能力」这个说法**不成立**：FAPI 的 `fabric-resource-conditions-api-v1`（随聚合 `fabric-api` 必带）会在 `SimpleJsonResourceReloadListener.apply` 前按对象里的 `fabric:load_conditions` 过滤，而 `RecipeManager` 正是它的子类 ⇒ 配方可以按条件被跳过（字节码见 §C0）。 |
| 受影响面 | 需要被门控的是 **67** 个 `*_chip.json`（+ 同名 67 条 advancement）。`blank_chip_duplicate.json` **不在** forge 已被门控的集合里（现状遗留不一致）。 |
| Fabric 有没有官方「服务端内建数据包」入口 | **有**：`ResourceManagerHelper.registerBuiltinResourcePack(Identifier, ModContainer, ResourcePackActivationType)`（fabric-resource-loader-v0）。它同时建客户端资源包 + **服务端数据包**，服务端那枚会被 Fabric 自动注入 `PackRepository`（专用服与单人内建服都成立）。 |
| 推荐落法 | 两条路径都给：**方案 D（datagen 写 `fabric:load_conditions` + 运行期 `ResourceConditions.register` 谓词）成本最低、与三条对照线语义最接近**；**方案 A（内嵌数据包 + 注册时机门控）是用户原始需求形态，可行性已由字节码判死/判活**（`NORMAL` 在服务端也会被自动加载 ⇒ 开关必须做在「是否注册」这一层）。见 §C9 / §C10。 |

---

## A. 现状：『禁用配方』现在的机制

### A1 配置键

命令与原始输出：

```bash
cd /f/MCProject/astral_dice_multiloader
grep -rn '"enable_chip_recipes"' fabric-1.20.1/src/main/java forge-1.20.1/src/main/java \
                                   neoforge-1.21.1/src/main/java neoforge-26.1.2/src/main/java
```

```
fabric-1.20.1/.../config/ModCommonConfig.java:109:                .define("enable_chip_recipes", false);
forge-1.20.1/.../config/ModCommonConfig.java:111:                .define("enable_chip_recipes", false);
neoforge-1.21.1/.../config/ModCommonConfig.java:111:                .define("enable_chip_recipes", false);
neoforge-26.1.2/.../config/ModCommonConfig.java:116:                .define("enable_chip_recipes", false);
```

（上面的 `...` 省略的都是 `src/main/java/com/merlinkitsune/astral_dice`。）

字段声明与注释原文：

| 线 | 文件:行 | 原文 |
|---|---|---|
| Fabric | `.../config/ModCommonConfig.java:52` | `public static final ForgeConfigSpec.BooleanValue ENABLE_CHIP_RECIPES;` |
| Fabric | 同文件 `:107-109` | `// 筹码合成配方总开关(默认 false;键名 / 注释 / 默认值与 forge 逐字对齐)。`<br>`ENABLE_CHIP_RECIPES = builder.comment("是否启用筹码合成配方(默认：false；关闭时筹码无法通过合成获得)").define("enable_chip_recipes", false);` |
| forge | `forge-1.20.1/.../config/ModCommonConfig.java:55` / `:110-111` | 同上，但注释写「**61 枚**筹码无法通过合成获得」 |
| neo 1.21.1 | `neoforge-1.21.1/.../config/ModCommonConfig.java:55` / `:110-111` | `ModConfigSpec.BooleanValue` |
| neo 26.1.2 | `neoforge-26.1.2/.../config/ModCommonConfig.java:67` / `:115-116` | 同上 |

**结论 1**：`enable_chip_recipes` 在 **Fabric 线已存在**，默认 `false`，与另外三条线**同名同默认**。配置文件是 `config/astral_dice-common.toml`（见 `AstralDiceMod.java:246`）。

**结论 2**：`chip_recipes_enabled` 是**配方条件的 id**，不是配置键 —— `ChipRecipesEnabledCondition.java:28-29` 的 `public static final ResourceLocation ID = new ResourceLocation(AstralDiceMod.MODID, "chip_recipes_enabled");`。二者关系：条件求值时 `return ModCommonConfig.ENABLE_CHIP_RECIPES.get();`。

**旁证（Fabric 线已按 67 计数写死默认值）**：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/shop/ChipShopCatalog.java:49-53`

```java
    /**
     * 筹码合成总开关的**权威默认值**(与 {@code config.ModCommonConfig#ENABLE_CHIP_RECIPES} 对应:
     * 该配置项以此为默认定义 —— 关闭时 67 枚筹码无法通过合成获得)。
     */
    public static final boolean CHIP_RECIPES_DEFAULT = false;
```

`ENABLE_CHIP_RECIPES` 在四线 `src/main` 的**全部引用点**（grep 实测）：`ModCommonConfig`（声明 + 定义）、各线 `recipe/condition/ChipRecipesEnabledCondition`（test / 注释）、`shop/ChipShopCatalog`（仅注释，`CHIP_RECIPES_DEFAULT` 是常量，不读配置）。**除此之外没有运行期读者** —— 即 Fabric 线目前该配置项在运行期无人消费。

### A2 三条非 Fabric 线怎么被配置门控

条件类型字符串三线一致：`astral_dice:chip_recipes_enabled`；区别只在**外层键名**与**条件注册机制**。

**(a) forge-1.20.1** —— 原文 `forge-1.20.1/src/generated/resources/data/astral_dice/recipes/cutter_chip.json`（35 行，完整）：

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "conditions": [
    {
      "type": "astral_dice:chip_recipes_enabled"
    }
  ],
  "key": {
    "B": {
      "item": "astral_dice:blank_chip"
    },
    "L": {
      "item": "minecraft:golden_apple"
    },
    "P": {
      "item": "astral_dice:star_plate"
    },
    "R": {
      "item": "astral_dice:regeneration_reagent"
    },
    "X": {
      "item": "minecraft:iron_sword"
    }
  },
  "pattern": [
    "XLX",
    "RBR",
    "PPP"
  ],
  "result": {
    "item": "astral_dice:cutter_chip"
  },
  "show_notification": true
}
```

- 条件实现类：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/recipe/condition/ChipRecipesEnabledCondition.java`
  - `:30` `public final class ChipRecipesEnabledCondition implements ICondition`（`net.minecraftforge.common.crafting.conditions.ICondition`）
  - `:48-50` `public boolean test(IContext context) { return ModCommonConfig.ENABLE_CHIP_RECIPES.get(); }`
  - `:58-60` `public static void register() { CraftingHelper.register(new Serializer()); }`
  - `:68-83` 内部 `Serializer implements IConditionSerializer<ChipRecipesEnabledCondition>`（write 空、read 返回 `INSTANCE`）
  - 类 javadoc `:18-28`：Forge 47.x **没有**注册表式条件序列化器，序列化器经 `CraftingHelper#register` 进内部 map；**条件 JSON 的键是顶层 `conditions`**；注册必须在模组构造器内（早于配方反序列化），否则报 `Unknown condition type: astral_dice:chip_recipes_enabled`。
- 接线点：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java:63`

```java
        // 条件序列化器(ChipRecipesEnabledCondition)不是注册表项,必须在此同步注册(详见该类 javadoc)。
        ChipRecipesEnabledCondition.register();
```

- datagen 侧：`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java:40` `cond.addProperty("type", "astral_dice:chip_recipes_enabled");`

**(b) neoforge-1.21.1** —— 原文 `neoforge-1.21.1/src/generated/resources/data/astral_dice/recipe/cutter_chip.json`（注意目录是**单数 `recipe`**；key 段为节省版面折叠成单行，其余逐字保留）：

```json
{
  "neoforge:conditions": [
    {
      "type": "astral_dice:chip_recipes_enabled"
    }
  ],
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "key": {
    "B": { "item": "astral_dice:blank_chip" },
    "L": { "item": "minecraft:golden_apple" },
    "P": { "item": "astral_dice:star_plate" },
    "R": { "item": "astral_dice:regeneration_reagent" },
    "X": { "item": "minecraft:iron_sword" }
  },
  "pattern": ["XLX", "RBR", "PPP"],
  "result": { "count": 1, "id": "astral_dice:cutter_chip" },
  "show_notification": true
}
```

- 条件实现类：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/recipe/condition/ChipRecipesEnabledCondition.java`
  - `public final class ChipRecipesEnabledCondition implements ICondition`（`net.neoforged.neoforge.common.conditions.ICondition`）
  - 单例 + `MapCodec`：`public static final MapCodec<ChipRecipesEnabledCondition> CODEC = MapCodec.unit(INSTANCE).stable();`
  - `:45-47` `public boolean test(IContext context) { return ModCommonConfig.ENABLE_CHIP_RECIPES.get(); }`
  - 注册（**不是** `CraftingHelper`）：`:55-60`

```java
    @SubscribeEvent
    public static void onRegisterConditionCodecs(RegisterEvent event) {
        event.register(NeoForgeRegistries.CONDITION_SERIALIZERS.key(),
                helper -> helper.register(ID, CODEC));
    }
```

    类头 `@EventBusSubscriber(modid = AstralDiceMod.MODID, bus = EventBusSubscriber.Bus.MOD)`；javadoc 说明注册表是 `NeoForgeRegistries.CONDITION_SERIALIZERS`、元素类型 `MapCodec<? extends ICondition>`、键为 `NeoForgeRegistries.Keys.CONDITION_CODECS`，21.1.235 已无 `IConditionSerializer`。
- `neoforge-26.1.2` 同构：`:45` test、`:56-57` `RegisterEvent` 注册；类注释指出 26.1.2 的 `@EventBusSubscriber` 已无 `bus` 参数。
- datagen 侧：`neoforge-1.21.1/.../datagen/ModRecipeProvider.java:221 / :234 / :246 / :260 / :283 / :295 / :307` 处 `.save(output.withConditions(ChipRecipesEnabledCondition.INSTANCE))`。

**⇒ 「平台差异」的准确表述**：条件**类型**三线通用是 `astral_dice:chip_recipes_enabled`，但**宿主解析面**各不相同（Forge 顶层 `conditions` + `CraftingHelper`；NeoForge 顶层 `neoforge:conditions` + 注册表）。**Fabric 1.20.1 根本没有第三方配方条件求值面** —— Fabric 线那份 `ChipRecipesEnabledCondition` 是空壳：

`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/recipe/condition/ChipRecipesEnabledCondition.java`

```java
:11-17  类注释：Fabric 1.20.1 没有任何对应机制；原版 RecipeManager 不支持外部条件；
        唯一近似物 ResourceCondition 是 1.21.2+ 才有。
:28-29  public static final ResourceLocation ID = new ResourceLocation(AstralDiceMod.MODID, "chip_recipes_enabled");
:51-53  public boolean isEnabled() { return ModCommonConfig.ENABLE_CHIP_RECIPES.get(); }
:58-63  public static void register() {
            /* TODO(CHIP-RECIPES-MECHANISM): fabric 无配方条件注册面,占位空实现 */
        }
:65-67  注释：本方法目前无调用方
```

该 TODO 已给出两条候选落法（与下文 §C9 的方案 C 同源）：① 启动期按配置把 `data/astral_dice/recipes/*.json` 从资源包内过滤掉；② 运行时标签门控。

### A3 受影响配方的数量与路径范围（实测读数）

统计口径注意三点，否则会数错：
1. **1.21+ 目录名已改单数**：`recipes` → `recipe`、`advancements` → `advancement`。
2. 条件键有两种写法：forge 顶层 `"conditions"`、neoforge `"neoforge:conditions"`；而 `grep 'forge:conditions'` 会作为子串命中 `neoforge`，必须写全。
3. **advancement 里的 `"conditions"` 是 criteria 的条件，不是配方条件** —— 会把 `advancements/recipes` 全量误命中（见下）。

```bash
# 配方目录：总数 / 两种条件写法各多少
for L in fabric-1.20.1 forge-1.20.1 neoforge-1.21.1 neoforge-26.1.2; do
  for D in recipes recipe; do
    P="$L/src/generated/resources/data/astral_dice/$D"
    [ -d "$P" ] && printf "%-22s %-8s total=%-4s conditions=%s  neoforge:conditions=%s\n" \
      "$L" "$D" "$(ls $P|wc -l)" \
      "$(grep -l '"conditions"' $P/*.json 2>/dev/null|wc -l)" \
      "$(grep -l '"neoforge:conditions"' $P/*.json 2>/dev/null|wc -l)"
  done
done
```

```
fabric-1.20.1    recipes  total=108  conditions=0    neoforge:conditions=0     ← 生成的 108 个，一个都关不掉
forge-1.20.1     recipes  total=108  conditions=67   neoforge:conditions=0
neoforge-1.21.1  recipe   total=108  conditions=0    neoforge:conditions=67
neoforge-26.1.2  recipe   total=108  conditions=0    neoforge:conditions=67
```

```bash
# 主资源目录（手写、非 datagen）
for L in fabric-1.20.1 forge-1.20.1 neoforge-1.21.1 neoforge-26.1.2; do
  for D in recipes recipe; do
    P="$L/src/main/resources/data/astral_dice/$D"
    [ -d "$P" ] && printf "%-22s %-8s total=%-4s conditions=%s\n" "$L" "$D" "$(ls $P|wc -l)" "$(grep -l '"conditions"' $P/*.json 2>/dev/null|wc -l)"
  done
done
```

```
fabric-1.20.1    recipes  total=17   conditions=0
forge-1.20.1     recipes  total=17   conditions=0
neoforge-1.21.1  recipe   total=17   conditions=0
neoforge-26.1.2  recipe   total=16   conditions=0
```

```bash
# advancements 的误命中（供对照，不要拿它当配方条件数）
fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes   total=108 with_"conditions"=108
```

**受影响集合（本任务的靶心）**：

```bash
cd fabric-1.20.1
ls src/generated/resources/data/astral_dice/recipes/ | wc -l                          # 108
ls src/generated/resources/data/astral_dice/recipes/ | grep -c '_chip\.json$'          # 67
find src/generated/resources/data/astral_dice/advancements -name '*.json' | wc -l      # 108
find src/generated/resources/data/astral_dice/advancements -name '*_chip.json' | wc -l # 67
# 配方 id 集合 与 advancement 文件名集合 的双向差集（都为空）
comm -23 <(ls .../recipes | sed 's/\.json$//' | sort) <(find .../advancements -printf '%f\n' | sed 's/\.json$//' | sort)
comm -13 <(ls .../recipes | sed 's/\.json$//' | sort) <(find .../advancements -printf '%f\n' | sed 's/\.json$//' | sort)
```

```
=== generated recipes total === 108
=== generated recipes *_chip === 67
=== generated advancements total === 108
=== generated advancements *_chip === 67
=== 名字集差集(recipes vs advancements basename) ===
only-in-recipes:
only-in-adv:
```

**⇒ 读数的含义**：
- Fabric 线需要被门控的是 **67 个 `*_chip.json`（+ 同名 67 条 advancement）**；其余 41 个生成配方（`*_sign` 立牌、`dice`、`star_coin_*`、`golden_star_plate_*`、`conductive_wire`、`mark_paint`、`regeneration_reagent`、`blank_chip_duplicate` 等）**不受该开关控制**。
- **Fabric 生成的 67 个 chip 配方名与 forge 那 67 个带条件配方名逐文件相同**（双向差集为空）⇒ 两条线配方内容同源，Fabric 侧只是缺条件层 ⇒ 内嵌数据包的 67 个文件可以从现有 datagen 产物派生，**不需要重写配方逻辑**。
- **两处现状不一致（顺带记录，不在本任务范围）**：
  1. `blank_chip_duplicate.json`（空白筹码复制）在三条对照线里**也没被门控** ⇒ 开关关闭时它仍可合成；
  2. forge / neoforge 配置注释写「**61 枚**筹码」，实测门控文件数是 **67**；Fabric 线 `ChipShopCatalog` 的注释已改成 67。属文档/注释口径漂移。

**手写的 17 个主资源配方**（`fabric-1.20.1/src/main/resources/data/astral_dice/recipes/`）：

```
amethyst_dice.json  astral_guide.json  card_shop.json  chip_shop.json  crimson_dice.json
diamond_dice.json   emerald_dice.json  ender_dice.json event_drawer.json glass_dice.json
golden_dice.json    nether_star_dice.json netherite_dice.json netherrack_dice.json
obsidian_dice.json  suspicious_stew.json weird_dice.json
```

其中 `chip_shop.json` **只是卡牌商店的商店配方**（名字里带 chip），不是筹码合成配方 —— 门控时**不要**动它。

### A4 Fabric 线的配方 JSON 是手写还是 datagen 生成

**生成**（datagen），且生成目录进 `main` 资源集：

`fabric-1.20.1/build.gradle:31-43`

```groovy
sourceSets.main.resources {
    srcDir('src/generated/resources')
    exclude("**/*.bbmodel")
    exclude("**/.cache")
    exclude("INSTRUCTIONS.md")
    exclude("mc-src/**")
}
```

（注释 `:27-30` 记录搬自 forge：漏搬会导致 137 模型 + 118 配方 + 118 进度不进产物 jar。）

`fabric-1.20.1/build.gradle:272-289`

```groovy
datagen {
    server()
    name = "Data Generation"
    vmArg "-Dfabric-api.datagen"
    vmArg "-Dfabric-api.datagen.output-dir=${file('src/generated/resources').absolutePath}"
    vmArg "-Dfabric-api.datagen.modid=${mod_id}"
    vmArg "-Dfabric-api.datagen.strict-validation"
    runDir 'run/datagen'
}
```

- **datagen 入口**：`fabric.mod.json` 的 `fabric-datagen` entrypoint → `com.merlinkitsune.astral_dice.datagen.AstralDataGenerator`（`implements DataGeneratorEntrypoint`）；命令 `./gradlew :fabric-1.20.1:runDatagen`。该方法内只有：`Pack pack = g.createPack(); pack.addProvider(ModItemModelProvider::new); pack.addProvider(ModRecipeProvider::new);`。
- **配方生成器**：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java`，**1481 行**，单方法 `public void buildRecipes(Consumer<FinishedRecipe> output)`（`:37`）。datagen 包只有 4 个文件：`AstralDataGenerator` / `ModItemModelProvider` / `ModRecipeProvider` / `NbtAugmentedRecipe`。
- **chip 与非 chip 配方在代码里交错**（不是连续块）：`:217` CUTTER_CHIP、`:230` FLASHLIGHT_CHIP、`:257` GOLDEN_SHOOTING_STAR_CHIP、`:268` BLANK_CHIP（存档 id `blank_chip_duplicate`）、`:340` TARGET_CHIP，中间夹 `:353/:360` 金板、`:370` 八面骰、`:383/:392` 星币；`:404` 起又是筹码。**⇒ 不建议逐条改这 67 处 `.save(...)`。**
- `ModRecipeProvider.java:397-401` 注释称骰子升级配方定义在 `src/main/resources/data/astral_dice/recipe/`（**单数**），实测主资源目录是 `recipes/`（复数）—— 注释口径与实物不一致，仅记录。

## B. Fabric 1.20.1 上「内嵌数据包 + 配置开关」的可行通道

### B5. 版本面（实测读数）

`fabric-1.20.1/gradle.properties`：

```properties
minecraft_version=1.20.1                 # :10
minecraft_version_range=~1.20.1          # :16
loader_version=0.19.5                    # :17
fabric_version=0.92.12+1.20.1            # :18
mod_version=1.5.0+fabric_1.20.1          # :29
```

`fabric-1.20.1/build.gradle`：

```groovy
66:    minecraft "com.mojang:minecraft:${minecraft_version}"
69:    modImplementation "net.fabricmc:fabric-loader:${loader_version}"
70:    modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_version}"
82:        modRuntimeOnly "maven.modrinth:forge-config-api-port:${forgeconfigapiport_mr_version}"   // 仅 dev 运行期
```

`fabric-1.20.1/src/main/resources/fabric.mod.json`（关键行）：

```json
 3:  "id": "astral_dice",
15:  "main": ["com.merlinkitsune.astral_dice.AstralDiceMod"],
18:  "client": ["com.merlinkitsune.astral_dice.client.AstralDiceClient"],
21:  "fabric-datagen": ["com.merlinkitsune.astral_dice.datagen.AstralDataGenerator"],
33:  "mixins": ["astral_dice.mixins.json", "astral_dice.client.mixins.json"],
39:    "fabricloader": ">=${loader_version}",
40:    "minecraft": "${minecraft_version_range}",
41:    "fabric-api": "*"
```

**依赖里没有单列 `fabric-resource-loader-v0`**，只声明聚合 `fabric-api`。实测聚合 jar 内嵌模块含该子模块：

```
$ unzip -l fabric-api-0.92.12+1.20.1.jar | grep -E 'resource-loader|lifecycle-events'
META-INF/jars/fabric-resource-loader-v0-0.92.12.jar
META-INF/jars/fabric-lifecycle-events-v1-0.92.12.jar
META-INF/jars/fabric-events-lifecycle-v0-0.92.12.jar
```

⇒ 内建资源包/数据包 API 所在的子模块是 **`fabric-resource-loader-v0`**（`net.fabricmc.fabric.api.resource.*`），由聚合包带入，无需新增依赖声明。

### B6. 机读实证（1.20.1 Mojang-mapped merged jar + fabric-api 子模块 jar）

取证对象：

```
MC  : ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/
      1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/
      minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar
FAPI: ~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/
      0.92.12+1.20.1/3e9cdd3e2f827ca9a259df9eb8e31949437b6bd4/fabric-api-0.92.12+1.20.1.jar
      └ 内嵌 jar 已解到 /tmp/fapi_x/rl (fabric-resource-loader-v0), /tmp/fapi_x/lc (fabric-lifecycle-events-v1)
映射: ~/.gradle/caches/fabric-loom/1.20.1/loom.mappings.1_20_1.layered+hash.2198-v2/mappings.tiny
      (表头 tiny 2 0 official intermediary named)
javap: /c/Program Files/Zulu/zulu-21/bin/javap （JDK 21）
```

#### B6.1 `PackRepository`（yarn 旧名 `ResourcePackManager`）/ `Pack` / `PackSource`

```
$ javap -p -classpath <merged.jar> net.minecraft.server.packs.repository.PackRepository
public class server.packs.repository.PackRepository {
  private final Set<server.packs.repository.RepositorySource> sources;
  private Map<String, server.packs.repository.Pack> available;
  private List<server.packs.repository.Pack> selected;
  public PackRepository(RepositorySource...);
  public void reload();
  private Map<String, Pack> discoverAvailable();
  public void setSelected(Collection<String>);
  public boolean addPack(String);
  public boolean removePack(String);
  private List<Pack> rebuildSelected(Collection<String>);
  private Stream<Pack> getAvailablePacks(Collection<String>);
  public Collection<String> getAvailableIds();
  public Collection<Pack> getAvailablePacks();
  public Collection<String> getSelectedIds();
  public FeatureFlagSet getRequestedFeatureFlags();
  public Collection<Pack> getSelectedPacks();
  public Pack getPack(String);
  public boolean isAvailable(String);
  public List<PackResources> openAllSelected();
  private static void method_29484(Map, Pack);
}
```

```
$ javap -p <merged.jar> net.minecraft.server.packs.repository.Pack    （节选）
public static Pack readMetaAndCreate(String, Component, boolean, Pack$ResourcesSupplier,
                                     PackType, Pack$Position, PackSource);
public static Pack create(String, Component, boolean, Pack$ResourcesSupplier, Pack$Info,
                          PackType, Pack$Position, boolean, PackSource);
public static Pack$Info readPackInfo(String, Pack$ResourcesSupplier);
public String getId();  public boolean isRequired();  public boolean isFixedPosition();
public Pack$Position getDefaultPosition();  public PackSource getPackSource();
public FeatureFlagSet getRequestedFeatures();  public PackCompatibility getCompatibility();
public PackResources open();

$ javap -p 'net.minecraft.server.packs.repository.Pack$Position'
public final class Pack$Position extends Enum<Pack$Position> {
  public static final Pack$Position TOP;      // field_14280
  public static final Pack$Position BOTTOM;   // field_14281
  public <T> int insert(List<T>, T, Function<T,Pack>, boolean);
  public Pack$Position opposite();
}

$ javap -p net.minecraft.server.packs.repository.PackSource
public interface PackSource {
  UnaryOperator<Component> NO_DECORATION;  PackSource DEFAULT;  PackSource BUILT_IN;
  PackSource FEATURE;  PackSource WORLD;  PackSource SERVER;
  Component decorate(Component);
  boolean shouldAddAutomatically();
  static PackSource create(UnaryOperator<Component>, boolean);
}
```

字节码要点（`javap -c`）：

- 构造器只做 `selected = ImmutableList.of(); available = ImmutableMap.of(); sources = ImmutableSet.copyOf(srcs)` —— **构造时不发现任何包**。
- `reload()` = 取当前 `selected` 的 id 列表 → `discoverAvailable()` → `rebuildSelected(ids)`。
- `discoverAvailable()` = `Maps.newTreeMap()` 后对每个 source 调 `loadPacks(map::put)` ⇒ **available 按 pack id 字典序**，同 id 后写覆盖。
- `rebuildSelected` 末尾有一段：`if (pack.isRequired() && !list.contains(pack)) pack.getDefaultPosition().insert(list, pack, Functions.identity(), false)` ⇒ **required 包会被强制插回 selected**，位置由该包的 `defaultPosition`（TOP/BOTTOM）决定。

⇒ 结论：`PackRepository` 有公开的 `addPack(String)` / `setSelected(...)`，从 API 面看可以运行时增删包；但 **required 包无法被 `setSelected` 剔除**（会被 `rebuildSelected` 插回）。

#### B6.2 启动链：配方集合在哪一步定型

```
$ javap -p net.minecraft.server.MinecraftServer | grep -E 'PackRepository|initServer|loadLevel|runServer|reloadResources|configurePackRepository|getSelectedPacks|WorldStem|ReloadableResources'
  private final PackRepository packRepository;
  private MinecraftServer$ReloadableResources resources;
  public MinecraftServer(Thread, LevelStorageSource$LevelStorageAccess, PackRepository,
                         WorldStem, Proxy, DataFixer, Services, ChunkProgressListenerFactory);
  protected abstract boolean initServer() throws IOException;
  protected void loadLevel();
  protected void createLevels(ChunkProgressListener);
  protected void runServer();
  public CompletableFuture<Void> reloadResources(Collection<String>);
  public static WorldDataConfiguration configurePackRepository(PackRepository,
                              DataPackConfig, boolean, FeatureFlagSet);
  private static DataPackConfig getSelectedPacks(PackRepository);
  public PackRepository getPackRepository();
  public ResourceManager getResourceManager();

$ javap -p 'net.minecraft.server.WorldLoader$PackConfig'
record PackConfig(PackRepository packRepository, WorldDataConfiguration initialDataConfig,
                  boolean safeMode, boolean initMode) {
  public Pair<WorldDataConfiguration, CloseableResourceManager> createResourceManager();
}

$ javap -p net.minecraft.server.ReloadableServerResources      （节选）
  private final RecipeManager recipes;
  private final TagManager tagManager;
  ...
  public static CompletableFuture<ReloadableServerResources> loadResources(ResourceManager,
        RegistryAccess$Frozen, FeatureFlagSet, Commands$CommandSelection, int, Executor, Executor);
  public RecipeManager getRecipeManager();
  public List<PreparableReloadListener> listeners();

$ javap -p net.minecraft.world.item.crafting.RecipeManager
public class RecipeManager extends SimpleJsonResourceReloadListener {
  protected void apply(Map<ResourceLocation, JsonElement>, ResourceManager, ProfilerFiller);
  public boolean hadErrorsLoading();
  public Collection<Recipe<?>> getRecipes();
  public Stream<ResourceLocation> getRecipeIds();
  public Optional<? extends Recipe<?>> byKey(ResourceLocation);
  public void replaceRecipes(Iterable<Recipe<?>>);
  public static Recipe<?> fromJson(ResourceLocation, JsonObject);
  public static <T extends Recipe<?>> CachedCheck<Container> createCheck(RecipeType<T>);
}
```

链路与时机（逐条对上字节码/签名）：

1. `MinecraftServer` 的构造器**接收** `PackRepository` 与 `WorldStem`（已加载完的资源栈）⇒ **初始配方集合在 server 对象构造之前就已定型**。
2. `runServer()` 的第 1 条指令是 `invokevirtual net/minecraft/server/MinecraftServer.initServer:()Z`（`javap -c` 偏移 1），失败即抛 `IllegalStateException("Failed to initialize server")`。
3. `initServer()` 是 `protected abstract`，由 `DedicatedServer` / `IntegratedServer` 各自实现（`public boolean initServer()`；专用服实现里会做 `loadLevel()` 等）。
4. `WorldLoader$PackConfig.createResourceManager()` 是产生本次启动所用 `CloseableResourceManager` 的地方（`Pair<WorldDataConfiguration, CloseableResourceManager>`）。
5. `ReloadableServerResources.loadResources(...)` 用那个 `ResourceManager` 跑一遍 reload listener（含 `RecipeManager.apply`）。
6. `MinecraftServer.reloadResources(Collection<String>)` 是**运行期重载**入口：`/reload` 与「启用/停用数据包」都走它，内部重新 `configurePackRepository` + `loadResources` + 重推数据包内容。

#### B6.3 Fabric API 的官方内建包入口（含服务端）

```
$ javap -p net.fabricmc.fabric.api.resource.ResourceManagerHelper
public interface ResourceManagerHelper {
  public default void addReloadListener(IdentifiableResourceReloadListener);
  public abstract void registerReloadListener(IdentifiableResourceReloadListener);
  public static ResourceManagerHelper get(class_3264 /* PackType */);
  public static boolean registerBuiltinResourcePack(class_2960 /* Identifier */, ModContainer,
                                                   ResourcePackActivationType);
  public static boolean registerBuiltinResourcePack(Identifier, ModContainer, Component,
                                                   ResourcePackActivationType);
  public static boolean registerBuiltinResourcePack(Identifier, ModContainer, String,
                                                   ResourcePackActivationType);
  public static boolean registerBuiltinResourcePack(Identifier, String, ModContainer, boolean);
}
```

```
$ javap -p -c net.fabricmc.fabric.api.resource.ResourcePackActivationType | sed -n '/isEnabledByDefault/,+12p'
  public boolean isEnabledByDefault();
       0: aload_0
       1: getstatic  #38  // Field DEFAULT_ENABLED:...ResourcePackActivationType;
       4: if_acmpeq  14
       7: aload_0
       8: getstatic  #40  // Field ALWAYS_ENABLED:...ResourcePackActivationType;
       ...
```

⇒ `NORMAL.isEnabledByDefault() == false`，`DEFAULT_ENABLED` / `ALWAYS_ENABLED` 为 `true`。

`registerBuiltinResourcePack(Identifier, ModContainer, ResourcePackActivationType)` 字节码（关键三条）：

```
   ldc  #48            // String resourcepacks/\u0001     ← jar 内目录前缀
   invokestatic ...StringConcatFactory/或者 + 拼接 → path = "resourcepacks/" + id.getPath()
   invokestatic  ResourceManagerHelperImpl.registerBuiltinResourcePack:(Identifier,String,ModContainer,Component,Activation)Z
```

`ResourceManagerHelperImpl.registerBuiltinResourcePack(...)` 字节码：把 path 中的 `/` 换成平台分隔符，然后**造两个包**：

```
   ModNioResourcePack.create(id.toString(), container, path, PackType.field_14188 /*CLIENT_RESOURCES*/, activation)
   ModNioResourcePack.create(id.toString(), container, path, PackType.field_14190 /*SERVER_DATA*/,     activation)
   两个都为 null ⇒ return false
```

映射侧证据（`mappings.tiny`）：

```
18486 class_3264 net/minecraft/server/packs/PackType
18487   field_14188 CLIENT_RESOURCES
18488   field_14190 SERVER_DATA
```

⇒ **一次 `registerBuiltinResourcePack` 同时注册「客户端资源包」与「服务端数据包」**，官方支持服务端，不存在「只有客户端」的限制。

`ModNioResourcePack` 的三个硬约束（决定方案可行性）：

```
$ javap -p -c net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack | sed -n '/create(/,+40p'
  → 对每个 container.getRootPaths() 做 root.resolve(subPath).normalize()，
    只保留 startsWith(root) && Files.exists(...) 的；
    结果 list 为空 ⇒ return null（静默不注册，无日志）

$ javap -p -c ...ModNioResourcePack | sed -n '/method_14407/,+6p'
  public <T> T method_14407(class_3270<T>);   // = getMetadataSection
       0: aload_0
       1: ldc_w   #430  // String pack.mcmeta      ← 必须有 pack.mcmeta
       4: invokevirtual #388 // Method openFile:(Ljava/lang/String;)...
```

`ResourceManagerHelperImpl.registerBuiltinResourcePacks(PackType, Consumer<Pack>)` —— 把「内置包列表」翻译成 `Pack` 的地方，**这是本报告最关键的一段字节码**：

```
遍历 builtin 包:
    Set<String> namespaces = pack.getNamespaces(type);
    if (namespaces.isEmpty()) continue;                      ← ★ 没有 data/<ns>/ 的包直接跳过
    Pack p = Pack.readMetaAndCreate(id.toString(), title,
                required = (activationType == ALWAYS_ENABLED),
                supplier, type, Pack$Position.TOP,
                new BuiltinModResourcePackSource(modName));
    consumer.accept(p);
```

- `BuiltinModResourcePackSource implements PackSource`：`shouldAddAutomatically()` 的字节码就是 `iconst_1; ireturn` ⇒ **恒 true**；`decorate()` 用 `pack.nameAndSource` + `pack.source.builtinMod`。
- `ModResourcePackCreator implements RepositorySource`：`loadPacks(Consumer<Pack>)` 先把模组自身 `data/`+`assets/` 聚成一个 **id = `fabric` 的 required 包**（`Pack.readMetaAndCreate("fabric", translatable("pack.name.fabricMods"), true, supplier, type, Pack$Position.TOP, RESOURCE_PACK_SOURCE)`），再调 `registerBuiltinResourcePacks(type, consumer)`。
- `ResourcePackManagerMixin`（target `class_3288` = `PackRepository`，`@Inject` 到构造器 `TAIL`）：扫描 `sources`，若存在 `FolderRepositorySource`（`class_3279`）且其 `packSource` 字段（`field_25345`）等于 `PackSource.WORLD`（`field_25349`）或 `PackSource.SERVER`（`field_25350`），就 `sources.add(new ModResourcePackCreator(PackType.SERVER_DATA))`。

⇒ **服务端（专用服 + 单人内建服）的 `PackRepository` 只要带「世界/服务器目录源」就会自动获得模组内建数据包源** —— 无需任何 mixin。

#### B6.4 三态真值表（activationType × 端）—— 用 `configurePackRepository` 字节码定死

```
$ javap -p -c net.minecraft.server.MinecraftServer | sed -n '/configurePackRepository/,/^$/p'   （要点摘录）
  ① packRepository.reload()
  ② if (initMode) { packRepository.setSelected(singleton("vanilla")); return WorldDataConfiguration.DEFAULT; }
  ③ Set<String> set = new LinkedHashSet<>();
     for (id : dataPackConfig.getEnabled()) if (packRepository.isAvailable(id)) set.add(id);
                                          else LOGGER.warn("Missing data pack {}", id);
  ④ for (Pack pack : packRepository.getAvailablePacks()) {
        if (dataPackConfig.getDisabled().contains(pack.getId())) continue;
        if (!set.contains(pack.getId())
            && pack.getPackSource().shouldAddAutomatically()
            && pack.getRequestedFeatures().isSubsetOf(enabledFeatures)) {
            LOGGER.info("Found new data pack {}, loading it automatically", pack.getId());
            set.add(pack.getId());
        } else if (set.contains(pack.getId()) && !subset) {
            LOGGER.warn("Pack {} requires features {} that are not enabled for this world, disabling pack.", ...);
            set.remove(pack.getId());
        }
     }
  ⑤ if (set.isEmpty()) { set.add("vanilla"); LOGGER.info("No datapacks selected, forcing vanilla"); }
  ⑥ packRepository.setSelected(set);
     return new WorldDataConfiguration(getSelectedPacks(packRepository), packRepository.getRequestedFeatureFlags());
```

结合 B6.3 的 `shouldAddAutomatically() == true`：

| activationType | `isRequired()` | 服务端（专用服/单人内建服）行为 | 客户端 `Options` 行为 |
|---|---|---|---|
| `NORMAL` | false | **仍会被自动加载**（④ 判定通过，日志 `Found new data pack ..., loading it automatically`）；只有 `level.dat` 的 `disabled` 列表能挡住 | **默认不启用**（`Options.loadSelectedResourcePacks` 只按 `options.txt` 里 `resourcePacks` 列表 `setSelected`） |
| `DEFAULT_ENABLED` | false | 同上（服务端 `isEnabledByDefault()` 根本没被读） | 默认启用 |
| `ALWAYS_ENABLED` | **true** | `rebuildSelected` 强制插入 + ④ 通过 ⇒ **无法被 `setSelected` / `/datapack disable` 关掉** | 默认启用、且不可在界面关闭 |

⇒ **重要结论（推翻「用 activationType 当开关」的直觉）：服务端上 `NORMAL` 也会被自动加载。** 因此「配置控制是否加载」不能靠 activationType 表达，必须落在**是否注册这个内置包**这一步（注册时机门控）。

#### B6.5 生命周期事件能不能用来注入

```
$ javap -p net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
  Event<ServerStarting>  SERVER_STARTING;      // onServerStarting(MinecraftServer)
  Event<ServerStarted>   SERVER_STARTED;
  Event<ServerStopping>  SERVER_STOPPING;
  Event<ServerStopped>   SERVER_STOPPED;
  Event<SyncDataPackContents> SYNC_DATA_PACK_CONTENTS;  // onSyncDataPackContents(ServerPlayer, boolean)
  Event<StartDataPackReload>  START_DATA_PACK_RELOAD;
  Event<EndDataPackReload>    END_DATA_PACK_RELOAD;

$ javap -p -c net.fabricmc.fabric.mixin.event.lifecycle.MinecraftServerMixin   (常量池)
  → "setupServer" / "()Z" / "runServer" / "TAIL" / "HEAD"
```

- FAPI 该 mixin 注解目标用的是 yarn 名 `setupServer()` = Mojang 名 `initServer()Z`（运行时靠 refmap 重映射）。
- 结合 B6.2：`initServer()` 在 `runServer()` 之后才跑，而 `WorldStem`/资源管理器是构造参数 ⇒ **在 `SERVER_STARTING` 里改 `PackRepository` 影响不到本次启动的初始配方集合**（只能影响后续 `/reload`）。
- 因此「在 SERVER_STARTING 里 addPack」属于**方案 C 一类**（要么靠一次 reload，要么靠 `reloadResources`），不是干净的方案 A。

### B7. 现有 mixin 清单（是否已有可复用注入点）

```
$ find fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin -name '*.java' | wc -l
49
$ grep -rln 'PackRepository\|ReloadableServerResources\|WorldLoader\|ResourceManagerHelper' fabric-1.20.1/src/main/java
fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/event/RegisterCommandsEvent.java   (无关误命中)
```

49 个 mixin 的目标类（按目录）：

| 目录 | 文件 → 目标 |
|---|---|
| 根 | `EntityThunderHitMixin→Entity`、`HealOrHarmMobEffectMixin→MobEffect`、`ItemStackMaxCountMixin→ItemStack`、`LightningBoltStrikeScopeMixin→LightningBolt`、`PiglinAiMixin→PiglinAi`、`PlayerItemDropGuardMixin→Player`、`RegenerationMobEffectMixin→MobEffect` |
| `bridge/`（18） | `ClientEntityRenderBridgeMixin→LivingEntityRenderer`、`ClientHandBridgeMixin→ItemInHandRenderer`、`ClientMouseBridgeMixin→MouseHandler`、`ClientScreenBridgeMixin→Minecraft`、`ClientTooltipBridgeMixin→GuiGraphics`、`EnderPearlTeleportBridgeMixin→ThrownEnderpearl`、`EntityDimensionTravelBridgeMixin→Entity`、`ExplosionStartBridgeMixin→Level`、`ItemCraftedBridgeMixin→ResultSlot`、`ItemTossBridgeMixin→Player`、`LivingHealBridgeMixin / LivingHurtBridgeMixin / LivingUseItemFinishBridgeMixin / LivingUseTotemBridgeMixin→LivingEntity`、`MobFinalizeSpawnBridgeMixin→Mob`、`PlayerAttackBridgeMixin→Player`、`ProjectileImpactBridgeMixin→Projectile`、`ScreenInvokerMixin→Screen`、`ServerPlayerDimensionTravelBridgeMixin→ServerPlayer`、`TooltipRenderUtilColorMixin→TooltipRenderUtil` |
| `client/` | `ArmorBarCancelMixin→Gui`、`AstralUseItemGuardMixin→Minecraft`、`EffectRenderingInventoryScreenMixin→EffectRenderingInventoryScreen`、`EntityRendererInvoker→EntityRenderer`、`GuiMixin→Gui`、`LocalPlayerDropGuardMixin→LocalPlayer`、`SignTranslucencyMixin→LivingEntityRenderer` |
| `container/` | `BundleInsertGuardMixin→BundleItem`、`ContainerClickGuardMixin / ContainerMoveGuardMixin→AbstractContainerMenu`、`ShulkerBoxBlockEntityGuardMixin→ShulkerBoxBlockEntity`、`ShulkerBoxSlotGuardMixin→ShulkerBoxSlot`、`SlotPlaceGuardMixin→Slot` |
| `jade/` | `ArmorElementIconMixin→snownee.jade.impl.ui.ArmorElement`、`ArmorValueProviderMixin→snownee.jade.addon.vanilla.EntityArmorProvider` |
| `server/` | `ServerPlayerDropGuardMixin→ServerPlayer` |
| `trade/` | `MerchantContainerMixin→MerchantContainer`、`MerchantMenuMixin→MerchantMenu`、`MerchantOfferMixin→MerchantOffer`、`MerchantResultSlotMixin→MerchantResultSlot`、`ServerPlayerMixin→ServerPlayer` |
| `trinkets/` | `TrinketInventoryBoundsMixin→TrinketInventory` |

⇒ **没有任何现成可复用的注入点**（无人碰 `PackRepository` / `RecipeManager` / `ReloadableServerResources` / `WorldLoader`）。方案 A 完全不需要新增 mixin。

### B8. 可选机制逐项盘点

**(a) 数据包覆写（同 id 配方覆盖成不可合成形式）—— 该版本可用，但不合需求**

1.20.1 的 `MultiPackResourceManager` 把每个包按顺序压成 `FallbackResourceManager.PackEntry`，`getResource(id)` **反向扫描**（高优先级先查）；先命中的返回，因此高优先级包里的同名 `data/astral_dice/recipes/x.json` 会覆盖低优先级包。⇒ 「把 `*_chip.json` 覆盖成不可合成形式」技术上成立。

但本模组的主资源已经在 id = `fabric` 的 required 聚合包里（见 B6.3），要覆盖必须让新包排在它**前面**（更高优先级）。两个包的相对顺序本轮**未能确证**（`available` 是 TreeMap 字典序，而 `rebuildSelected` 只用 `defaultPosition` 处理 required 包的插入位置），⇒ 风险不可估。且覆盖法要求为 67 个配方各准备一份「不可合成」替身 JSON，体积与漂移成本都高于方案 A。

**(b) 运行时按配置过滤 `RecipeManager` —— 无过滤 API，只能整体替换**

`RecipeManager` 的公开面只有 `apply(...)`（protected）/ `getRecipes()` / `getRecipeIds()` / `byKey(...)` / `replaceRecipes(Iterable<Recipe<?>>)` / `hadErrorsLoading()`（B6.2 的 javap 原文）。**没有 `removeByPredicate` / filter 入口**。`replaceRecipes` 可整体换掉配方表，但：① 必须在配方表建好之后（`ReloadableServerResources.loadResources` 之后），② 换完还要走 `MinecraftServer.reloadResources` 一类路径把结果推给客户端，否则客户端仍按旧表显示（配方书/JEI）。⇒ 属于「方案 C」的组件，可作为兜底，不适合做主方案。

**(c) 自建 `Pack` 实现 + `RepositorySource` —— 运行时可做，但注入点都在「资源已定型之后」**

`Pack.create(...)` / `Pack.readMetaAndCreate(...)` 都是 public，`RepositorySource` 是 `@FunctionalInterface` 形态的 `loadPacks(Consumer<Pack>)`；但 `PackRepository` 的 `sources` 是 `private final Set`（B6.1），从外部只能通过**在构造时传入**或 mixin 注入。FAPI 已经用 `ResourcePackManagerMixin` 做了这件事（B6.3），自己再写一个等价 mixin 只会与它抢同一个注入点，收益为零。

**(d) `fabric-resource-loader-v0` 是否支持「按配置隐藏资源」—— 无此能力**

该模块的公开 API 只有 `ResourceManagerHelper.get(PackType)` / `addReloadListener` / `registerReloadListener` / 4 个 `registerBuiltinResourcePack`（B6.3 的 javap 原文）。**没有任何「运行时按条件禁用/隐藏某个包」的入口**；唯一与「开关」相关的语义是 `ResourcePackActivationType`，而它在服务端被 `shouldAddAutomatically()==true` 抹平（B6.4）。⇒ 配置开关只能实现为「注册 / 不注册」。

**(e) 客户端 vs 服务端差异**

- 专用服：`ServerPacksSource` + 世界目录源 → 触发 `ResourcePackManagerMixin` → 内建数据包源存在 → 方案 A 生效。
- 单人内建服（Integrated Server）：同样经过 `MinecraftServer` + 同一个 `configurePackRepository`，FAPI 的注入条件（`FolderRepositorySource` 且 `PackSource.WORLD`/`SERVER`）同样满足 ⇒ 生效；客户端侧的内建**资源**包由 `DefaultClientResourcePackProviderMixin` / `VanillaResourcePackProviderMixin` 提供。
- 客户端以 `options.txt` 的 `resourcePacks` 为准（B6.4 第 2 列），因此 `NORMAL` 的客户端资源包默认不启用 —— 本方案只关心服务端数据包，不受此影响。

**(f) 数据包 overlay —— 该版本无此能力**

```
$ unzip -l <merged.jar> | grep -i overlay | head
  → 只命中 assets/minecraft/textures/... 里名字带 _overlay 的贴图（armor/potion/spawn_egg/...）
  → 没有任何 server/packs/metadata/**Overlay** 类
```

⇒ 1.20.1 没有 `OverlayMetadataSection`（pack overlay 是后续版本引入的能力），**不能**用「pack.mcmeta 里声明 overlays」的方式做条件加载。


## C. 方案设计（不写代码，只给结构、路径与判据）

### C0 前提更正：1.20.1 Fabric 原生具备「配方条件」能力

用户需求的前提是「1.20.1 Fabric 由于平台差异无法禁用配方」。**这个前提不成立。** 本线确实没有 forge/neoforge 那套 `ICondition` 加载器机制，但 Fabric API 的 `fabric-resource-conditions-api-v1` 提供了等价物，且它在 1.20.1 上**已经过滤配方**。以下全部是实测字节码，不是记忆。

**(1) 模块确实在运行时依赖图里。** 聚合包 `fabric-api-0.92.12+1.20.1.jar` 内嵌子模块：

```
$ J=$(ls ~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.92.12+1.20.1/*/fabric-api-0.92.12+1.20.1.jar)
$ unzip -l "$J" | grep resource-conditions
    43811  1980-01-01 08:00   META-INF/jars/fabric-resource-conditions-api-v1-0.92.12.jar
```

**(2) 它的 mixin 配置注入了数据加载链。** `fabric-resource-conditions-api-v1.mixins.json`：

```json
"mixins": [
  "DataPackContentsMixin", "DataProviderMixin", "JsonDataLoaderMixin",
  "LootManagerMixin", "SinglePreparationResourceReloaderMixin", "TagManagerLoaderMixin"
],
"injectors": { "defaultRequire": 1 }
```

**(3) 注入点 = 所有 JSON 数据加载器的公共基类。** javap（`-p -v`）读该模块 `SinglePreparationResourceReloaderMixin.class`：

```
@Mixin(value = Lnet/minecraft/class_4080; )
private void applyResourceConditions(class_3300 ResourceManager, class_3695 ProfilerFiller, Object, CallbackInfo)
```

映射核对（`mappings.tiny`）：

```
19030: c  alc  net/minecraft/class_4080  net/minecraft/server/packs/resources/SimplePreparableReloadListener
19032: m  (Lakx;Lban;Ljava/lang/Object;)V  a  method_18790  method_18790
```

即注入 `SimplePreparableReloadListener.apply(Object, ResourceManager, ProfilerFiller)` 的 HEAD。

**(4) 过滤发生在 `RecipeManager` 继承到的那处。** javap 实测类头：

```
public class net.minecraft.world.item.crafting.RecipeManager
        extends net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
```

`JsonDataLoaderMixin` 的 `@Mixin` 目标是 `Lnet/minecraft/class_4309;`（mappings.tiny:19022 = `SimpleJsonResourceReloadListener`），它 **override** 了上面那个 no-op 钩子，对 prepared 的 `Map<ResourceLocation, JsonElement>` 逐条过滤：

```
 96: aload_3  ... invokeinterface java/util/Map.entrySet:()Ljava/util/Set;
109: invokestatic  // Method net/fabricmc/fabric/api/resource/conditions/v1/ResourceConditions.objectMatchesConditions:(Lcom/google/gson/JsonObject;)Z
121: invokeinterface java/util/Iterator.remove:()V
```

`RecipeManager` 没有覆盖 `fabric_applyResourceConditions` ⇒ `RecipeManager.apply(Map, ...)` 拿到的 map 已经是过滤后的。

**(5) 条件写在什么键上。** `ResourceConditions.objectMatchesConditions` 字节码：

```
ldc  #17   // String fabric:load_conditions
invokestatic GsonHelper.getAsJsonArray
ifnull -> iconst_1; ireturn        // 没有该键 = 放行
```

单个条件对象的 id 键是 `condition`（`conditionMatches` 字节码 `ldc #20 // String condition` + `new ResourceLocation(...)`）。数组语义为 AND（`conditionsMatch(JsonArray, true)`）。

**未注册的条件 id 不是抛异常中断加载，而是逐对象跳过**：`conditionMatches` 抛 `JsonParseException` → `objectMatchesConditions` 的 catch 记 WARN（`Skipping object %s. Failed to parse resource conditions`）并返回 `false` ⇒ **该配方静默消失**。实施时这是双刃剑：注册漏了 = 配方全没了；但不会炸服。

**(6) 官方注册面与 datagen 面。**

```
public static void  ResourceConditions.register(ResourceLocation, Predicate<JsonObject>)
public static Predicate<JsonObject> ResourceConditions.get(ResourceLocation)
public class  net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider
              extends net.minecraft.data.recipes.RecipeProvider
  protected Consumer<FinishedRecipe> withConditions(Consumer<FinishedRecipe>, ConditionJsonProvider...)
  public abstract void method_10419(Consumer<class_2444>)   // = buildRecipes
```

⇒ 三条对照线里的 `chip_recipes_enabled` 条件，在 Fabric 线的等价实现是 **一行谓词注册 + 生成时写条件块**，不需要数据包、不需要 Mixin、不需要 access widener。

**结论：禁用配方的正确实现是「条件配方」，内嵌数据包是可选包装形式，不是必要手段。** 下面仍把两条路都设计完整，因为用户明确要数据包形态。

---

### C9 候选方案

先固定三个**所有方案共用的事实**（§B6 已取证，这里直接引用，用于判定哪些做法根本不可行）：

- `ResourceManagerHelper.registerBuiltinResourcePack(Identifier, ModContainer, ResourcePackActivationType)` 会**同时**产出客户端资源包与服务端数据包；`NORMAL` 在服务端是 **DEFAULT_ENABLED**，即**注册了就等于加载**。⇒ 想在「注册了但不开」的前提下关掉配方，**开关必须做在「是否注册」这一层**，不能靠 activation type。
- `ALWAYS_ENABLED == REQUIRED`。选 `REQUIRED` 的世界会往 `level.dat` 的 `DataPacks` 里写死这个 id；之后关掉开关再进同一个存档，控制台会出现 `Missing data pack astral_dice:chip_recipes`（不崩，但会刷屏），且 `/datapack disable` 会被拒。
- 内嵌包的 namespace 来自 `pack.mcmeta` 所在路径（`mods/<modid>/<subPath>`），**无 namespace 的包会被 FAPI 跳过**。所以「纯 filter 包」这条路在 1.20.1 上不成立。

#### 方案 D（推荐，最小改动）：datagen 写 `fabric:load_conditions` + 运行期注册谓词

- **实现要点**
  1. 新增条件 id `astral_dice:chip_recipes_enabled`，谓词体就是读配置：`json -> ModCommonConfig.ENABLE_CHIP_RECIPES.get()`。必须挂在 **mod 初始化期**（早于任何数据加载），否则命中「未注册 id」分支 = 67 个配方全部消失。
  2. 生成端把这 67 对（配方 + 对应 advancement）包一层带条件的 Consumer，其余 41 个配方原样输出。
- **需要改哪些文件（路径级，本轮未改，仅设计）**
  - `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java`：在 `buildRecipes` 的 `Consumer<FinishedRecipe>` 入口处包一层 wrapper —— 判据用 `FinishedRecipe.getId()` 的 path 是否以 `_chip` 结尾（或维护显式集合），命中则追加 `fabric:load_conditions`。也可对 67 处逐一改用现成的 `FabricRecipeProvider.withConditions(...)`，但**前者只动 1 处**，建议前者。
  - 新增 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ChipConditionJsonProvider.java`：实现 `ConditionJsonProvider`，产出 `{"condition":"astral_dice:chip_recipes_enabled"}`。走 wrapper 路线时此类可省，直接在 wrapper 里拼 JSON。
  - `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java`：在 `setupConfig()` 调用之后（同一个 `onInitialize` 内）加 `ResourceConditions.register(...)`。
  - `fabric-1.20.1/src/main/resources/fabric.mod.json`：`depends` 加 `"fabric-resource-conditions-api-v1": "*"`（聚合包已带，显式声明可防有人用裁剪版 FAPI）。
  - 重新跑 datagen，落盘到 `fabric-1.20.1/src/generated/resources/data/astral_dice/{recipes,advancements}/`。
- **是否需要重启**：配置值在内存里，改 `config/*.toml` 后**需要重启游戏/服务器**；重启后的 `/reload` 会重新求值并生效（与三条对照线行为一致）。
- **JEI / 配方书**：条件不满足 ⇒ 服务端不加载该配方 ⇒ 下发给客户端的配方集里没有 ⇒ 客户端 JEI 与配方书都看不到。语义与 forge/neoforge 三线**完全一致**，这是它最大的优点。
- **存档影响**：**零**。不写 `level.dat`，不出现 missing pack。
- **单人内建服 + 专用服**：都成立（条件在服务端数据加载时求值）。
- **风险**：
  - 条件 id 注册晚了/命名空间写错 ⇒ 67 个配方静默全消失（有 WARN 日志可查，不是崩服）。
  - 只给配方加条件而漏了 advancement，会留下引用不存在配方的进度；本线 67 条 advancement 与配方同名同批，**必须同批处理**（`FabricRecipeProvider` 的 advancement 输出是否吃同一个 Consumer，本轮未逐字节核对，见未确证项 #3）。
  - 编译期依赖 FAPI 子模块（若依赖写的是 `fabric-api` 聚合则本来就有）。
- **回退方式**：删掉条件键（datagen 重跑即回到 108 个无条件配方）+ 删谓词注册；或临时把谓词恒返回 `true`。

#### 方案 A（用户原始需求形态）：datagen 生成内嵌数据包 + 注册时机门控

- **实现要点**
  1. `AstralDataGenerator` 里再调 `createBuiltinResourcePack(new ResourceLocation(MODID, "chip_recipes"))`，得到输出到 `src/generated/resources/resourcepacks/chip_recipes/` 的 Pack，挂一个 provider 专产 67 对 chip 文件。
  2. 主包 provider 只产剩下 41 对。
  3. 运行期：开关为 true 才 `registerBuiltinResourcePack`，为 false 时**根本不注册** —— 只有这样才能真的「关掉」；`ALWAYS_ENABLED`/`REQUIRED` 只在希望「关不掉」时才需要。
  4. **必须手写 `pack.mcmeta`**：datagen 不生成它（实测在 `fabric-data-generation-api-v1` 解包目录里搜 `pack.mcmeta` 零命中）。内容用实测常量：`{"pack":{"pack_format":15,"description":"Astral Dice chip recipes"}}`（`SharedConstants.DATA_PACK_FORMAT = 15`，1.20.1 数据版本 3465）。
- **需要改哪些文件（路径级）**
  - `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/AstralDataGenerator.java`：加 `createBuiltinResourcePack` + 第二个 provider 注册。
  - `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java`：加「是否含 chip」的构造参数/过滤谓词，按 `FinishedRecipe.getId()` 分流到两个 Consumer。
  - 新增 `fabric-1.20.1/src/generated/resources/resourcepacks/chip_recipes/pack.mcmeta`（手写，pack_format 15）。
  - `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java`：`onInitialize` 里按配置决定是否 `registerBuiltinResourcePack`。
  - **人工清理**：`fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/*_chip.json` 与 `.../advancements/*_chip.json` 里残留的 67+67 个文件（datagen 只写不删，重跑后旧文件仍在并且照样进 jar）。
- **是否需要重启**：**是**，而且比 D 更硬 —— 内建包的注册发生在初始化期，`/reload` 改不了「注册了没有」这件事。
- **JEI / 配方书**：与 D 等价（服务端没有该配方就没有），但多一个副作用：客户端也会拿到同名资源包（内容仅 pack.mcmeta + 数据，客户端用不上），无害。
- **存档影响**：
  - 用 `ALWAYS_ENABLED`/`REQUIRED`：id 写进 `level.dat` 的 `DataPacks`，之后关开关会有 `Missing data pack` 告警且 `/datapack disable` 被拒。
  - 用 `NORMAL` + 不注册来关：DEFAULT_ENABLED 的包同样会被记录进 `DataPacks`，关掉后也出现 missing 告警。
  - ⇒ **只要用过数据包形态，存档就会留下 id 痕迹**，这是 A 相对 D 的实质代价。
- **单人内建服 + 专用服**：都成立（§B6 已验证服务端也会被注入）。
- **风险**：missing pack 告警；残留文件；`pack.mcmeta` 忘写导致 `Pack.readMetaAndCreate` 取不到 metadata（包被视为不存在）；两个 pack 的加载顺序（内建包与聚合 `fabric` 包谁先）本轮未确证，但本方案不依赖顺序（只新增、不覆盖）。
- **回退方式**：摘掉 `createBuiltinResourcePack` 调用 + 把 chip provider 塞回主包 + 删 `resourcepacks/chip_recipes/` 整个目录。

#### 方案 B（不推荐）：纯数据包覆写 / filter 包

- 思路：不搬配方，用一个高优先级包把 67 个 `*_chip.json` 覆写成不可合成形态（或写 `pack.mcmeta` 的 `filter` 段屏蔽它们）。
- **判死依据**：FAPI 注册内建包要求包有 namespace（取自 `mods/<modid>/<subPath>`），无 namespace 的包会被跳过；而 `pack.mcmeta` 的 `filter` 段在 1.20.1 是否被 `ModNioResourcePack` 透传给 vanilla 过滤逻辑，本轮未取证。
- 即使可行：要维护 67 份替身 JSON、依赖包顺序（未确证）、任何一次配方 id 改名就静默失配。**性价比最差。**
- 唯一优点：不注册包则完全无存档痕迹。

#### 方案 C（兜底，不作主路径）：运行期过滤 `RecipeManager`

- 思路：`SERVER_STARTED` / `START_DATA_PACK_RELOAD` 后取 `server.getRecipeManager()`，按配置调 `replaceRecipes(filtered)`（§B6 已确认该方法存在且可调用），再向所有玩家发 `ClientboundUpdateRecipesPacket(...)` 同步。
- 缺点：① 存在「已加载 → 被替换」的窗口期；② 每次 `/reload` 都要重跑（FAPI 的 `SYNC_DATA_PACK_CONTENTS` 通道会重放）；③ 与配方书/进度的既有引用仍是「配方存在但不可用」的不一致态；④ 需要自己处理客户端同步包与包顺序。
- 用途：只在 D/A 都不可行时作为补丁手段。

---

### C10 推荐与复核判据

**推荐：方案 D 为主。** 理由按重要性排序：

1. **语义与三条对照线完全对齐** —— 同一份条件 id、同一份配置键、同一处「配方消失」的效果；日后合并分支时四条线读起来是同一件事。
2. **零存档副作用** —— 不写 `level.dat`，不产生 `Missing data pack`，玩家来回切开关不脏档（A 做不到）。
3. **改动面最小** —— `ModRecipeProvider` 只动入口 1 处 + `AstralDiceMod` 加 1 行注册；不需要搬 134 个文件、不需要手写 `pack.mcmeta`、不需要人工清理残留。
4. **不需要碰 Mixin / access widener**，也不依赖任何未确证的加载顺序。

**如果用户坚持「数据包形态」**（例如要求「关掉后连配方文件都不在 jar 里」，或将来想整包给整合包用），**方案 A 是可行且已取证的**，但要接受三条代价：存档里的 pack id 痕迹、必须重启才生效、datagen 残留文件要人工清理。

**合并后必须复核的判据（可执行）：**

```bash
# 1) 方案 D：108 个配方里 67 个带条件、41 个不带
grep -rl '"fabric:load_conditions"' data/astral_dice/recipes | wc -l      # 期望 67
ls data/astral_dice/recipes/*.json | wc -l                                 # 期望 108
grep -rl '"fabric:load_conditions"' data/astral_dice/advancements | wc -l  # 期望 67（与配方同批）
#    以上在 `unzip -d` 出来的 jar 解包目录里执行

# 2) 方案 A：jar 内必须同时有内建包 67 个 + pack.mcmeta，且主包不能残留 chip
unzip -l build/libs/astral_dice-*.jar | grep -c 'resourcepacks/chip_recipes/.*_chip.json'   # 期望 67
unzip -p build/libs/astral_dice-*.jar resourcepacks/chip_recipes/pack.mcmeta                 # 期望 pack_format 15
unzip -l build/libs/astral_dice-*.jar | grep -c 'data/astral_dice/recipes/.*_chip.json'      # 期望 0

# 3) 条件 id 字符串确实进了 jar
unzip -p build/libs/astral_dice-*.jar com/merlinkitsune/astral_dice/AstralDiceMod.class | strings | grep chip_recipes_enabled
```

游戏内观察点（每项都要在「开关 true / false」两态各看一次）：

| 观察点 | 命令 / 位置 | 期望 |
|---|---|---|
| 配方是否真的消失 | 配方书/JEI 里找该 chip 物品，或 `/recipe give @s astral_dice:<某 chip 物品>` | 开关 false 时找不到该配方 |
| 数据包是否加载（A） | `/datapack list` | 开关 true 时出现 `astral_dice:chip_recipes`；false 时**完全不出现** |
| 是否有 missing 告警（A） | `logs/latest.log` 搜 `Missing data pack` | 纯 D 方案应为 0；A 方案在「曾启用后关闭」时出现，属预期 |
| 条件注册是否生效 | `logs/latest.log` 搜 `Skipping object` / `Failed to parse resource conditions` | 应为 0（>0 说明 id 没注册上，67 个配方全丢） |
| JEI | 游戏内 JEI 搜 chip 物品 | 开关 false 时无配方页 |
| 单人内建服 | 单人存档 → Esc → 对局域网开放 → `/datapack list` + JEI | 与专用服一致 |
| 专用服 | 独立 server 起一次，`/datapack list` + 客户端 JEI | 与单人一致 |
| 存档不脏 | 开关切换前后各进一次同一存档 | 不崩、无 `Failed to load datapack` |

---

## 未确证项

| # | 项 | 状态 |
|---|---|---|
| 1 | JEI 侧取数口径（服务端下发 vs 客户端自扫 jar） | **无法判定**：缓存里没有 `mezz.jei`，无法 javap。但「服务端不下发就不显示」是三条对照线的既成事实，可作旁证。 |
| 2 | advancement 引用「已不存在的配方」是否绝对不崩 | **无法判定**：只在 1.20.1 逻辑上推断为不崩（advancement 的 recipe criterion 只是触发器，缺配方不会 NPE），未实测。方案 D 把 67 条 advancement 与配方同批处理可规避。 |
| 3 | `FabricRecipeProvider` 的 advancement 输出分支是否吃同一个 Consumer | **无法判定**：`withConditions` 只包 `Consumer<FinishedRecipe>`；advancement 是否经同一 Consumer 输出未逐字节核对 `method_10319`。实施前必须核。 |
| 4 | `REQUIRED` 包被 `/datapack disable` 时的确切报错文案 | **无法判定**：只确认「会被拒」，未取到字符串常量。 |
| 5 | lifecycle mixin 的 refmap 映射链（`ServerLifecycleEvents` 在 1.20.1 的切入签名） | **无法判定**：mixin JSON 与 handler 面已取证，refmap 未逐条核对。本方案 D 不依赖它，A 只依赖 `onInitialize`。 |
| 6 | strict-validation 对「同一 provider 输出到两个 pack」的反应 | **无法判定**：`isStrictValidationEnabled()` 存在，但对跨 pack 同名输出的行为未取证。 |
| 7 | datagen 不清理残留旧文件 | **高置信但未实测**：datagen 只写不删是通用行为，未在本仓实跑验证。方案 A 必须当成「会残留」处理。 |
| 8 | 方案 A 内建包与聚合 `fabric` 包的 selected 顺序 | **无法判定**：两者都进 `selected`，先后顺序未取证。方案 A 不依赖顺序，方案 B 才依赖。 |
| 9 | `pack.mcmeta` 的 `filter` 段在 1.20.1 `ModNioResourcePack` 下是否生效 | **无法判定**：这决定方案 B 是否可能可行，未取证 `ModNioResourcePack.getMetadataSection` 对 `filter` 的透传。 |
| 10 | 客户端 `options.txt` 对 `NORMAL` 内建资源包的覆盖 | **无法判定**：本方案不依赖它（不注册即关闭），改用 activation type 门控才会踩到。 |

（`pack_format` 一项原为未确证，已由 `javap -constants net.minecraft.SharedConstants` 实测 `DATA_PACK_FORMAT = 15` 关闭。）

