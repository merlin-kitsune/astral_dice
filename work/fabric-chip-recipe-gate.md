# fabric-1.20.1 筹码配方条件门控 — 实施与自证报告

- 仓库：`D:/MCProject/astral_dice_multiloader`，分支 `multi-main`
- 只改 `fabric-1.20.1/`；未触碰 `astral_dice_multiloader-next`；未 `git add/commit/push`；未启动游戏
- 时间：2026-11-02（Asia/Hong_Kong）
- 互斥锁：`D:/MCProject/.agent-locks/dsh.lock`（原内容为 2026-10-31 的陈旧 Linux 锁 PID=26899 TASK=2612-live-verify，已改写为 TASK=fabric-chip-recipe-gate；仓库内无 kimi.lock）

## 0. 结论

| 项 | 结果 |
|---|---|
| 三处代码改动（A/B/C） | 已落地（见 §2） |
| datagen 真实入口任务 | `:fabric-1.20.1:runDatagen` |
| datagen 是否跑通 | **跑通了，但只能绕开一个与本次无关的既有缺陷**：临时停用 `ModItemModelProvider`，取到 **真实 datagen 产物**后逐字节还原（SHA256 3 次核对一致）。**没有**走「手工补 134 文件」的兜底方案 |
| 67 / 67 条件门控 | 达成（recipes 67、advancements 67，`blank_chip_duplicate` 无条件下） |
| 四线 id 集合差 | 与 forge-1.20.1 / neoforge-1.21.1 / neoforge-26.1.2 全部为空 |
| 构建 | `BUILD SUCCESSFUL in 3s`；jar = `fabric-1.20.1/build/libs/astral_dice-1.5.0+fabric_1.20.1.jar`，**2,944,296 B**，SHA256 `d185d7dd24e14dddbfb17486f405cddd77dd7891227876323ca52bad60c84b31` |
| 开 jar 核对 | 67 条 chip 配方含条件、`ChipRecipesEnabledCondition.class` 在 jar 内、`fabric.mod.json` 依赖集未变 |
| `enable_chip_recipes` 默认值 | 未改（仍 `false`；本批未触碰 `ModCommonConfig.java`） |
| 工作树最终 diff | **137 个文件**：134 个 generated JSON（每个 +5 行条件块）+ 3 个 java；`757 insertions(+), 25 deletions(-)` |

## 1. 机制（官方资源条件，不是自造）

Fabric 1.20.1 侧没有 forge 的 `conditions` / neo 的 `neoforge:conditions` 字段，等价的官方机制是 **fabric-resource-conditions-api-v1**：

- 条件 JSON 形态：`"fabric:load_conditions": [{"condition": "astral_dice:chip_recipes_enabled"}]`（字段名 `condition`，不是 `type`）
- 数据生成侧：`FabricRecipeProvider#withConditions(Consumer, ConditionJsonProvider...)` —— 同一份条件**同时**写进 recipe 与 advancement，因此两侧天然同进同出
- 运行期：`ResourceConditions.register(id, predicate)`；`objectMatchesConditions` 是 **fail-closed**（条件未注册 ⇒ 打一条 WARN 并丢弃该 JSON）
- 模块随 fabric-api 聚合包 jar-in-jar 自动加载，**fabric.mod.json 无需新增 depends**（已有 `"fabric-api": "*"`）

本仓**已有**同机制先例（本次未改）：`fabric-1.20.1/src/main/resources/data/astral_dice/recipes/astral_guide.json` 用内置条件 `fabric:all_mods_loaded` 门控 patchouli 手册配方。

## 2. 改动清单

### A. `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/recipe/condition/ChipRecipesEnabledCondition.java`（118 行 CRLF，bareLF=0，6438 B）

| 行 | 改动 |
|---|---|
| :9-43 | 类注释**订正**：删除「Fabric 1.20.1 没有任何对应机制 / ResourceCondition 是 1.21.2+」的错误断言，改写为真实机制（`fabric:load_conditions` + `withConditions` + `JsonDataLoaderMixin`/`SinglePreparationResourceReloaderMixin`，作用域=所有 `SimpleJsonResourceReloadListener` 数据）、四线 JSON 形态差异、现状=67 条 `*_chip` 带条件 |
| :44 | `public final class ChipRecipesEnabledCondition implements ConditionJsonProvider {` |
| :57-59 | `getID()` 改为 `return getConditionId();`（保留 forge 语义的旧出口） |
| :65-67 | 新增 `@Override public ResourceLocation getConditionId() { return ID; }` |
| :77-79 | 新增 `@Override public void writeParameters(JsonObject json) { /* 无参条件，不写额外字段 */ }` |
| :105-112 | `register()`：先 `if (ResourceConditions.get(ID) != null) return;` 再 `ResourceConditions.register(ID, json -> INSTANCE.isEnabled());`（`register` 对重复注册抛 `IllegalArgumentException`，字节码是 `Map.put(...) != null` 即抛） |

### B. `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java`（360 行 CRLF，bareLF=0）

| 行 | 改动 |
|---|---|
| :6 | `import com.merlinkitsune.astral_dice.recipe.condition.ChipRecipesEnabledCondition;` |
| :109-113 | 6 行注释：注册时机要求 + 「不能挪进 `AstralDiceClient`，否则专用服务器上未注册 ⇒ 67+67 个 JSON 被 fail-closed 静默丢弃」 |
| :114 | `ChipRecipesEnabledCondition.register();` —— 位于 `setupConfig();`(:108) 之后、`BUS.post(new FMLCommonSetupEvent());`(:116) 之前 |

`onInitialize()` 是客户端与专用服务器都会执行、且早于 datapack 装载的唯一合适时机。

### C. `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java`（1491 行 CRLF，bareLF=0）

| 行 | 改动 |
|---|---|
| :5 | `import com.merlinkitsune.astral_dice.recipe.condition.ChipRecipesEnabledCondition;` |
| :38 | `public void buildRecipes(Consumer<FinishedRecipe> rawOutput)`（形参 `signed` → `rawOutput`） |
| :39-45 | 注释：判据必须按 **id 后缀**（不能按产出物，否则 `blank_chip_duplicate` 也会被门控 ⇒ 68≠67）；不能给 `output` 重新赋值（102 处 `.save(output::accept)` 是捕获它的 lambda，需 effectively-final） |
| :46 | `Consumer<FinishedRecipe> gated = withConditions(rawOutput, ChipRecipesEnabledCondition.INSTANCE);` |
| :47 | `Consumer<FinishedRecipe> output = r -> (r.getId().getPath().endsWith("_chip") ? gated : rawOutput).accept(r);` |
| :55 起 | **102 处 `.save(output::accept)` 零改动** |

### 未改动的关键文件（用于「依赖未变」判据）

`git status --porcelain -- fabric-1.20.1/src/main/resources/fabric.mod.json fabric-1.20.1/build.gradle fabric-1.20.1/gradle.properties gradle.properties fabric-1.20.1/src/main/resources/data` → **空输出**（全部与 HEAD 一致）。

## 3. datagen：先失败、再取证、再还原

### 3.1 真实入口任务

`fabric-1.20.1/build.gradle` 用 `fabricApi.configureDataGeneration()`（Fabric Loom 1.17.21）产出 `:fabric-1.20.1:runDatagen`，产物目录由 `-Dfabric-api.datagen.output-dir` 指定为 `fabric-1.20.1/src/generated/resources`。

```powershell
./gradlew.bat :fabric-1.20.1:runDatagen --console=plain
```

### 3.2 首次运行：被既有缺陷挡住（与本次改动无关）

```
> Task :fabric-1.20.1:runDatagen
[14:11:49] [main/INFO] (Minecraft) Starting provider: Astral Dice/Model Definitions
java.lang.RuntimeException: Failed to run data generator from mod (astral_dice)
Caused by: java.lang.IllegalStateException: Missing blockstate definitions for: [Block{astral_dice:card_shop}, Block{astral_dice:chip_shop}, Block{astral_dice:event_drawer}, Block{astral_dice:barricade_collision}]
FAILURE: Build failed with an exception.
BUILD FAILED in 8s
```

根因取证（两项都成立，不是猜测）：

1. `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModItemModelProvider.java:27-29` 的 `generateBlockStateModels(BlockModelGenerators)` 是**空实现**，注释写「本模组无方块模型(全部是物品)」——该注释已过时；
2. 但本模组确实注册了 4 个方块：`card_shop` `chip_shop` `event_drawer` `barricade_collision`，而它们的**资源其实早就存在**（手工维护在 `src/main/resources`，不在 generated 下）：
   ```
   assets/astral_dice/blockstates/{barricade_collision,card_shop,chip_shop,event_drawer}.json   4 个都存在
   assets/astral_dice/models/block/{barricade_collision,card_shop,chip_shop,event_drawer}.json   4 个都存在
   assets/astral_dice/textures/block/ 下 shop|drawer|barricade 贴图                   0 个
   ```
   vanilla `ModelProvider.run(ModelProvider.java:64)` 用的是「**已注册方块 − 生成期产出过 blockstate 的方块**」做差并抛异常，**不看磁盘上有没有同名 JSON** ⇒ 即使资源存在也照抛。

失败点在 `pack.addProvider(ModItemModelProvider::new)` 这个 provider 内、**早于** `ModRecipeProvider`（`AstralDataGenerator.java:34-36` 的注册顺序），所以本次改动在执行到之前就中断，generated 资源未被写入。

### 3.3 绕路：临时停用模型 provider 取真实产物（随后逐字节还原）

选择理由：模型 provider 与「配方条件」无关，且是唯一失败点；用它换真实 datagen 产物（键序/缩进/EOF 全部由生成器决定），比手工补 134 个文件更可信。

```
AstralDataGenerator.java:34
-        pack.addProvider(ModItemModelProvider::new);
+        // TEMP-CHIPGATE-DISABLED pack.addProvider(ModItemModelProvider::new);
```

临时改动前 SHA256（备份至 `C:/Users/xmace/AppData/Local/Temp/dsh-chipgate/AstralDataGenerator.java.bak`）：

```
2DEF1AEA22C4EEB8E887FBC2F026D6112783662EA8681099C4B4052A3842A2FE
```

datagen 结果（原始输出节选）：

```
[14:13:05] [main/INFO] (FabricDataGenHelper) Running data generator for astral_dice
[14:13:05] [main/INFO] (Minecraft) Starting provider: Astral Dice/Recipes
[14:13:05] [main/INFO] (Minecraft) Astral Dice/Recipes finished after 52 ms
[14:13:05] [main/INFO] (Minecraft) Caching: total files: 370, old count: 0, new count: 217, removed stale: 154, written: 216
BUILD SUCCESSFUL in 8s
```

还原后 SHA256（与原始一致）：

```
2DEF1AEA22C4EEB8E887FBC2F026D6112783662EA8681099C4B4052A3842A2FE
```

### 3.4 datagen 的两个副作用（已全部复原，未留在工作树）

1. **HashCache 删了 154 个 stale 文件**（`removed stale: 154`）——因为模型 provider 被停用，`src/generated/resources/assets/astral_dice/models/item/*.json` 被判为过期产物而删除。已 `git checkout -- fabric-1.20.1/src/generated/resources/assets` 全量复原（154 个，工作树与 HEAD 一致）。
2. **76 个「非 chip」generated 文件只差行尾**：生成器写 **LF**，而工作树里这些文件此前是按 `core.autocrlf` 落盘的 CRLF ⇒ `git status` 报 M，但 `git diff` 为**空**（逐字节比对：`work == HEAD` 为 True，640 B/640 B）。已对这批文件 `git checkout --` 复原，工作树只保留真正的语义改动。

> 说明：`git status` 对本批 134 个 chip 文件会提示 `LF will be replaced by CRLF the next time Git touches it`。这是生成器写 LF、仓库 blob 也是 LF、而 checkout 侧会做 CRLF 转换的正常现象；对 blob 内容无影响（`git diff` 只显示 +5 行条件块）。

## 4. 自证（判据 + 原始输出）

### 4.1 判据 1：recipes 侧带条件数 = 67

```powershell
rg -l '"fabric:load_conditions"' fabric-1.20.1/src/generated/resources/data/astral_dice/recipes | Measure-Object
```
```
count = 67
（样例）fabric-1.20.1\src\generated\resources\data\astral_dice\recipes\advanced_peripherals_chip.json
```

### 4.2 判据 2：`*_chip.json` 总数 = 67；advancements 带条件 = 67

```
===== 2) rg -l fabric:load_conditions  advancements =====
count = 67
===== 3) *_chip.json totals =====
recipes/chip files: 67
advancements/chip files: 67
```

### 4.3 判据 3：`blank_chip_duplicate.json` 不带条件（两侧）

```
  FILE: fabric-1.20.1\src\generated\resources\data\astral_dice\recipes\blank_chip_duplicate.json
  has fabric:load_conditions = False
  FILE: fabric-1.20.1\src\generated\resources\data\astral_dice\advancements\recipes\misc\blank_chip_duplicate.json
  has fabric:load_conditions = False
```

### 4.4 判据 4：文件总数 108 / 108（含子目录口径）

```
===== 4) recipe json totals (recursive + per dir) =====
recipes total: 108
recipes top-level only: 108
advancements total: 108
  advancements/recipes = 108

（分目录细目）
== recipes ==
   .                        108          <- fabric 线的 recipes 是**平铺**的，没有 misc/tools 子目录
== advancements ==
   .                        0
   recipes                  0
   recipes\misc             107
   recipes\tools            1
```

> 任务书里提醒的 `recipes/misc`、`recipes/tools` 两个子目录出现在 **advancements** 侧（107 + 1 = 108）；fabric 的 recipes 侧 108 个文件全在顶层。

### 4.5 判据 5：与三条对照线的 id 集合差（only-here / only-ref 全空）

```
=== per-line stats (top-level condition key) ===
fabric-1.20.1    recipes        total=108  cond=67   chip=67   chip_cond=67   err=0
fabric-1.20.1    advancements   total=108  cond=67   chip=67   chip_cond=67   err=0
forge-1.20.1     recipes        total=108  cond=67   chip=67   chip_cond=67   err=0
forge-1.20.1     advancements   total=108  cond=67   chip=67   chip_cond=67   err=0
neoforge-1.21.1  recipes        total=108  cond=67   chip=67   chip_cond=67   err=0
neoforge-1.21.1  advancements   total=108  cond=67   chip=67   chip_cond=67   err=0
neoforge-26.1.2  recipes        total=108  cond=67   chip=67   chip_cond=67   err=0
neoforge-26.1.2  advancements   total=108  cond=67   chip=67   chip_cond=67   err=0

=== fabric detail ===
fabric chip recipe WITH condition: 67
fabric chip recipe WITHOUT condition: []
blank_chip_duplicate recipe cond: [None]
blank_chip_duplicate adv cond: [None]
fabric chip adv WITH condition: 67
fabric NON-chip files that got a condition: []

=== id set diffs vs reference lines (chip only) ===
vs forge-1.20.1      recipe chips only-here: []  only-ref: []   adv chips only-here: []  only-ref: []
vs neoforge-1.21.1   recipe chips only-here: []  only-ref: []   adv chips only-here: []  only-ref: []
vs neoforge-26.1.2   recipe chips only-here: []  only-ref: []   adv chips only-here: []  only-ref: []

=== full id-set diffs (all recipes/advancements, not just chips) ===
vs forge-1.20.1      recipe only-here=[] only-ref=[] | adv only-here=[] only-ref=[]
vs neoforge-1.21.1   recipe only-here=[] only-ref=[] | adv only-here=[] only-ref=[]
vs neoforge-26.1.2   recipe only-here=[] only-ref=[] | adv only-here=[] only-ref=[]
```

### 4.6 附加判据：条件块形态（键序 / 缩进 / 行尾 / 可解析）

```
== recipes chips: 67
  bad condition block / EOL: count 0        （除 EOF 无换行外，见下）
  parse errors: []
  first-two-key patterns: {('fabric:load_conditions', 'type'): 67}
  eol pattern (hasCRLF, hasBareLF): {(False, True): 67}
== advancements chips: 67
  first-two-key patterns: {('fabric:load_conditions', 'parent'): 67}
  eol pattern (hasCRLF, hasBareLF): {(False, True): 67}
non-chip files carrying condition block: 0

  sample: recipes/abyss_reagent_chip.json
   |{
   |  "fabric:load_conditions": [
   |    {
   |      "condition": "astral_dice:chip_recipes_enabled"
   |    }
   |  ],
   |  "type": "minecraft:crafting_shaped",
```

要点：

- 条件块是 JSON 的**第一个键**（`{` 之后立即出现），不是文末追加
- 缩进 2 空格，与生成器其余输出一致；行尾 **LF**（生成器行为，且 HEAD 里这批文件的 blob 也是 LF）
- 生成器写出的文件**末尾不带换行**（它与 HEAD 的既有文件一致：非 chip 文件重新生成后与 HEAD 逐字节相等可以侧证）

## 5. 构建

```powershell
./gradlew.bat :fabric-1.20.1:build --console=plain -x pushToGame
```

```
> Task :fabric-1.20.1:compileJava FROM-CACHE
> Task :fabric-1.20.1:processResources
> Task :fabric-1.20.1:classes
> Task :fabric-1.20.1:jar
> Task :fabric-1.20.1:remapJar
> Task :fabric-1.20.1:assemble
> Task :fabric-1.20.1:build

> Task :fabric-1.20.1:pushToDevRun
pushToDevRun: pushed astral_dice-1.5.0+fabric_1.20.1.jar -> D:\MCProject\astral_dice_multiloader\run\fabric-1.20.1\mods

> Task :fabric-1.20.1:pushToRootBuild
pushToRootBuild: pushed astral_dice-1.5.0+fabric_1.20.1.jar -> D:\MCProject\astral_dice_multiloader\build\libs

BUILD SUCCESSFUL in 3s
8 actionable tasks: 5 executed, 1 from cache, 2 up-to-date
```

产物：

```
JAR: D:/MCProject/astral_dice_multiloader/fabric-1.20.1/build/libs/astral_dice-1.5.0+fabric_1.20.1.jar
  bytes=2944296 (2,944,296)
  sha256=d185d7dd24e14dddbfb17486f405cddd77dd7891227876323ca52bad60c84b31
```

> `-x pushToGame` 只排除了「推整合包」那一步；`pushToDevRun`（`run/fabric-1.20.1/mods`）与 `pushToRootBuild`（`build/libs`）是 `build` 的既有依赖任务，仍照原样执行并覆盖同名 jar。

## 6. 开 jar 核对

```
entries: 2307
data/astral_dice/recipes/*.json: 125
data/astral_dice/advancements/**.json: 109
recipes with fabric:load_conditions: 68
chip recipes: 67 | chip recipes with condition: 67
non-chip recipes carrying condition: ['data/astral_dice/recipes/astral_guide.json']
advancements with condition: 67 | chip advancements: 67 with cond: 67

condition class in jar: ['com/merlinkitsune/astral_dice/recipe/condition/ChipRecipesEnabledCondition.class']  (size 2736)
datagen entry class in jar: ['com/merlinkitsune/astral_dice/datagen/AstralDataGenerator.class']

fabric.mod.json version: 1.5.0+fabric_1.20.1
depends: {"fabric-api": "*", "fabric-data-attachment-api-v1": "*", "fabricloader": ">=0.19.5", "minecraft": "~1.20.1", "puzzleslib": ">=8.1.33", "starengine": ">=1.1.0 <2.0.0"}
recommends: {"accessories": ">=1.0.0-beta.48", "jei": ">=15.62.0", "patchouli": "*", "trinkets": ">=3.7.2"}
entrypoints: {"main": ["...AstralDiceMod"], "client": ["...AstralDiceClient"], "fabric-datagen": ["...AstralDataGenerator"], "jade": ["...AstralDiceJadePlugin"], "jei_mod_plugin": ["...CardShopJeiPlugin", "...ChipShopJeiPlugin"]}

generated chips: 67 jar chips: 67 diff: set()
chip json file bytes: 627 | jar entry bytes: 627 | identical: True
```

**125 / 109 / 68 的口径解释**（不是超发）：jar = `src/main/resources`（手工维护的 17 个配方 + 1 个进度）+ `src/generated/resources`（datagen 108 + 108）。逐条列出后「不在 generated 里的」恰好是这 17 + 1 个：

```
jar recipes count 125 | not from generated: 17
  amethyst_dice / astral_guide / card_shop / chip_shop / crimson_dice / diamond_dice / emerald_dice /
  ender_dice / event_drawer / glass_dice / golden_dice / nether_star_dice / netherite_dice /
  netherrack_dice / obsidian_dice / suspicious_stew / weird_dice
jar advancements 109 | not from generated: ['data/astral_dice/advancements/recipes/misc/suspicious_stew.json']
```

其中 `astral_guide.json` 就是那个**既有的** `fabric:load_conditions` 用法（`fabric:all_mods_loaded` + `patchouli`），因此得 68 = 67（本次 chip）+ 1（既有）。

**依赖未变**：源 `fabric.mod.json` 与 jar 内 `fabric.mod.json` 的 `depends` 仅差占位符展开（`${loader_version}` → `0.19.5`、`${minecraft_version_range}` → `~1.20.1`、`${puzzleslib_version_range}` → `>=8.1.33`、`${lib_version_range}` → `>=1.1.0 <2.0.0`），与 `fabric-1.20.1/gradle.properties` 的键值逐一对应；`fabric.mod.json`、`build.gradle`、`gradle.properties` 均未出现在 `git status` 里。

## 7. 工作树最终状态

```
$ git -C D:\MCProject\astral_dice_multiloader status --porcelain -- fabric-1.20.1
（137 项）' M' x 137 + '??' x 1（.tmp-runclient.log，非本批产生，未动）

$ git ... diff --stat -- fabric-1.20.1   →  137 files changed, 757 insertions(+), 25 deletions(-)
   .../AstralDiceMod.java                        |  7 ++
   .../datagen/ModRecipeProvider.java            | 12 ++-
   .../condition/ChipRecipesEnabledCondition.java| 93 ++++++++++++++++------
   .../data/astral_dice/{recipes,advancements}/…_chip.json | 5 ++  （共 134 个）
```

Java 文件行尾纪律（`open(p, newline='')` → 归一 LF → 打补丁 → 写回恢复 CRLF 后复读）：

```
AstralDiceMod.java                      bytes=28041   CRLF=360   bareLF=0
ModRecipeProvider.java                  bytes=85166   CRLF=1491  bareLF=0
ChipRecipesEnabledCondition.java        bytes=6438    CRLF=118   bareLF=0
```

## 8. 未确证项 / 遗留风险

1. **运行期未实测**：本次按纪律未启动游戏。因此「`enable_chip_recipes=false` 时 67+67 个 JSON 真被丢弃 / `=true` 时真被加载」只有**机制层证据**（recon 文档 §2 的字节码取证 + fail-closed 分支），没有实测日志。条件注册时机（`onInitialize` 早于 datapack 装载）也只做了代码层推理。
2. **datagen 仍然「原样」跑不通**：本次是靠**临时停用** `ModItemModelProvider` 才拿到产物，跑完已逐字节还原（SHA256 一致）。也就是说：**任何人下次跑 `:fabric-1.20.1:runDatagen` 都会在同一处失败**。这是既有缺陷（`ModItemModelProvider.java:27-29` 的空 `generateBlockStateModels` vs 4 个已注册方块），本次按「不改他人内容」的纪律**没有修**。
3. 因此本批的 134 个 generated JSON 是 **真实 datagen 产物**（不是手工补的），但它们**不可由当前仓库状态一键复现**——必须复现时得重复上面的临时停用步骤。
4. **`blank_chip_duplicate` 的业务裁决**：它按 id（不以 `_chip` 结尾）保持无条件，与三条对照线一致；但它的产出物确实是筹码，「开关关掉后这条配方仍可解锁」是不是期望行为，属于业务面未裁决项。
5. **行尾**：134 个 generated 文件是 LF（生成器行为，HEAD blob 也是 LF）；Java 文件是 CRLF。若仓库后续引入 `git diff --check` 或统一的 EOL 门禁，需要单独确认口径。
6. **四线逐字节一致未做**：条件字段名在 fabric 侧是 `condition`（forge 是 `type` / neo 是 `type`），所以四线 JSON **本来就不该逐字节相同**；本次只核了「id 集合 + 条件条数」一致，没做逐字节 diff。
7. **客户端侧加载**：67 条 advancement 的条件同样由客户端资源重载路径加载，未实测（未起客户端）。数据生成侧 `withConditions` 同时写 recipe 与 advancement 这一点，由产物双向各 67 条侧证。
8. **`removed stale: 154`** 说明「停用某个 provider 会让 HashCache 删掉它原本负责的产物」。复原用的是 `git checkout`，依赖这些文件在 git 里被跟踪（确实被跟踪）。若将来 generated 目录里出现未跟踪产物，这个副作用会**直接删掉不可恢复的文件**——这是 datagen 工具链的既有风险，不是本次引入。

## 9. 命令清单（可复跑）

```powershell
# 0) 锁
#    D:/MCProject/.agent-locks/dsh.lock 已改写为 TASK=fabric-chip-recipe-gate

# 1) 编译
Set-Location D:\MCProject\astral_dice_multiloader
.\gradlew.bat :fabric-1.20.1:compileJava --console=plain

# 2) datagen（当前仓库状态会失败；需先临时停用 ModItemModelProvider::new）
.\gradlew.bat :fabric-1.20.1:runDatagen --console=plain

# 3) 判据
rg -l '"fabric:load_conditions"' fabric-1.20.1/src/generated/resources/data/astral_dice/recipes | Measure-Object
Get-ChildItem -Recurse -File fabric-1.20.1\src\generated\resources\data\astral_dice\recipes -Filter '*_chip.json' | Measure-Object
python C:/Users/xmace/AppData/Local/Temp/dsh-chipgate/verify.py       # 四线对照 + id 集合差

# 4) 构建
.\gradlew.bat :fabric-1.20.1:build --console=plain -x pushToGame
```

临时脚本（均在仓库外）：`C:/Users/xmace/AppData/Local/Temp/dsh-chipgate/`（`verify.py` 四线核对、`_jsoncheck.py` 条件块形态、`_jarcheck.py` 产物核对、`AstralDataGenerator.java.bak` 原始备份）。
