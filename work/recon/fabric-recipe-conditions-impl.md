# fabric-1.20.1 筹码配方条件门控 —— 实施前取证报告

- 仓库：`D:/MCProject/astral_dice_multiloader`，分支 `multi-main`，目标线 `fabric-1.20.1`
- 性质：**只读取证**。未修改任何源码/资源/配置，未运行构建、未运行 datagen、未启动游戏、未做任何 git 写操作。本报告与 `work/recon/_scan*.py`（临时脚本）是本次唯一的落盘产物。
- 取证工具：`javap`（`/c/Program Files/Zulu/zulu-21/bin/javap`，Java 21.0.12）、`unzip`、`grep`、Windows 原生 Python 3.12（`/c/Users/xmace/.local/bin/python`）。
- 取证对象（实读，非记忆）：
  - loom 重映射后的 fabric-api 模块 jar：`.gradle/loom-cache/remapped_mods/remapped/net/fabricmc/fabric-api/`
    - `fabric-resource-conditions-api-v1-c2d2b86c/2.3.9+1802ada577/fabric-resource-conditions-api-v1-c2d2b86c-2.3.9+1802ada577.jar`（含同名 `-sources.jar`）
    - `fabric-data-generation-api-v1-c2d2b86c/12.4.0+238242b777/fabric-data-generation-api-v1-c2d2b86c-12.4.0+238242b777.jar`（含同名 `-sources.jar`）
    - `fabric-api-c2d2b86c/0.92.12+1.20.1/fabric-api-c2d2b86c-0.92.12+1.20.1.jar`（聚合包）
  - MC 具名合并 jar：`C:/Users/xmace/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar`
  - 本线源码与生成产物（见各节）

---

## 0. 结论速览

| 编号 | 待确证项 | 结论 | 依据 |
|---|---|---|---|
| 1 | advancement 是否与配方走同一条条件通道 | **是（同一通道、同一数组）**。`ModRecipeProvider` 根本没有独立 advancement 出口，全部 67 个 `*_chip` 都走 `RecipeBuilder.save(Consumer)`（源码里就是 `.save(output::accept)`）；`FabricRecipeProvider.run` 把 `consumeConditions(provider)` 拿到的**同一个** `ConditionJsonProvider[]` 既写进 recipe json 也写进 advancement json。 | 第 2 节 |
| 1b | 若 advancement 出口不吃 conditions 会怎样 | 该假设在本版本**不成立**。反事实推演：即使只门控配方，1.20.1 也**不会报错、不会崩服**——advancement 加载期不校验 `rewards.recipes` 指向的配方是否存在，完成奖励在 `awardRecipesByKey` 里 `Optional.ifPresent` 静默跳过。 | 2.7 节 |
| 2 | API 签名 + 聚合包 | `ResourceConditions` 6 个 public 成员（含 `register`/`objectMatchesConditions`/`conditionMatches`/`conditionsMatch`）签名已逐条 javap 取证；`FabricRecipeProvider.withConditions` **只有一个重载**（`ConditionJsonProvider...`，无 `ResourceLocation` 变体）；`fabric.mod.json` 的 depends **已含 `"fabric-api": "*"`**，聚合包 `META-INF/jars/` 里含 `fabric-resource-conditions-api-v1-0.92.12.jar`，**无需新增依赖**。 | 第 3 节 |
| 3 | 门控集合一致性 | 四线 recipe/advancement 总数与 `*_chip` 集合**完全一致**（各 108 / 108，chip 各 67 / 67）；仅 fabric 侧带条件数 = 0，其余三线 = 67。fabric 相对 neoforge-1.21.1 的集合差**只有条件标记，没有 id 差异**。`blank_chip_duplicate` 四线一致地**不带条件**，按对照线口径**不算进 67**。 | 第 4 节 |
| 4 | 配置开关运行期可变性 | **必须重启服务端**。`ConfigValue.get()` 读的是启动期缓存的字段（`ForgeConfigSpec.java` 自实现 shim），`/reload` 会重跑资源加载并重新求值条件，但取到的值不变。条件求值时机 = 每次资源加载、逐条目一次。 | 第 5 节 |

一句话：**技术上完全可行，且比预想的更干净** —— fabric 1.20.1 的 `fabric:load_conditions` 对 advancement 同样生效，advancement 与配方天然同进同出，不存在"关一半"的静默不一致风险。

---

## 1. 取证边界与判据声明

- 所有"结论"都附有实读命令与其输出片段；凡是推断而未实测的，一律进入第 7 节「未确证项」，不混入结论段。
- **测试/构建/实机一概未跑**（用户明确禁止）。因此本报告证明的是"代码路径与数据形态成立"，不是"运行期实测行为"。
- 判据口径：凡涉及"某文件带不带条件"，一律按 **JSON 顶层键**判定（`fabric:load_conditions` / `neoforge:conditions` / `forge:conditions` / `conditions`）。理由见 4.5 节的假阳性陷阱。

---

## 2. 第 1 问：advancement 与配方是否走同一条条件通道

### 2.1 `ModRecipeProvider` 的类声明与 `buildRecipes` 锚点

```
$ grep -n 'class ModRecipeProvider' fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java
19:public class ModRecipeProvider extends FabricRecipeProvider {

$ grep -n 'public ModRecipeProvider\|@Override\|public void buildRecipes' <同一文件>
28:    public ModRecipeProvider(FabricDataOutput output) {
36:    @Override
37:    public void buildRecipes(Consumer<FinishedRecipe> output) {

$ sed -n '28,45p' <同一文件>
    public ModRecipeProvider(FabricDataOutput output) {
        super(output);
    }

    // ⚠️ 2026-09-29(移植):可见性必须放宽为 public —— Fabric 的 `FabricRecipeProvider`
    //    把 `buildRecipes(Consumer<FinishedRecipe>)` 声明为 **public**(Forge 的 `RecipeProvider` 是
    //    protected),写成 protected 会报「无法覆盖…正在尝试分配更低的访问权限」。
    //    签名本身与 Forge 侧**逐字一致** ⇒ 1560 行配方代码零改动。
    @Override
    public void buildRecipes(Consumer<FinishedRecipe> output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.DICE.get())
```

- 类声明：`ModRecipeProvider.java:19` —— `public class ModRecipeProvider extends FabricRecipeProvider`（**是 `FabricRecipeProvider`，不是 `RecipeProvider`**）。
- 构造器：`ModRecipeProvider.java:28-30` —— `public ModRecipeProvider(FabricDataOutput output) { super(output); }`。
- `buildRecipes`：`ModRecipeProvider.java:36` 是 `@Override`，**:37 是方法签名行**（`public void buildRecipes(Consumer<FinishedRecipe> output)`，与用户给出的行号一致）。

### 2.2 全库唯一的 advancement 出口：`RecipeBuilder.save(Consumer)`

1.20.1 的 `RecipeBuilder` 接口**没有** `advancement(...)` / `advancementBuilder(...)` 之类方法：

```
$ javap -classpath <MC-merged-1.20.1.jar> net.minecraft.data.recipes.RecipeBuilder
public interface net.minecraft.data.recipes.RecipeBuilder {
  public static final net.minecraft.resources.ResourceLocation ROOT_RECIPE_ADVANCEMENT;
  public abstract net.minecraft.data.recipes.RecipeBuilder unlockedBy(java.lang.String, net.minecraft.advancements.CriterionTriggerInstance);
  public abstract net.minecraft.data.recipes.RecipeBuilder group(java.lang.String);
  public abstract net.minecraft.world.item.Item getResult();
  public abstract void save(java.util.function.Consumer<net.minecraft.data.recipes.FinishedRecipe>, net.minecraft.resources.ResourceLocation);
  public default void save(java.util.function.Consumer<net.minecraft.data.recipes.FinishedRecipe>);
  public default void save(java.util.function.Consumer<net.minecraft.data.recipes.FinishedRecipe>, java.lang.String);
  public static net.minecraft.resources.ResourceLocation getDefaultRecipeId(net.minecraft.world.item.ItemLike);
  static {};
}
```

advancement 是**作为 `FinishedRecipe` 的一部分**被携带出来的：

```
$ javap -classpath <MC-merged-1.20.1.jar> net.minecraft.data.recipes.FinishedRecipe
public interface net.minecraft.data.recipes.FinishedRecipe {
  public abstract void serializeRecipeData(com.google.gson.JsonObject);
  public default com.google.gson.JsonObject serializeRecipe();
  public abstract net.minecraft.resources.ResourceLocation getId();
  public abstract net.minecraft.world.item.crafting.RecipeSerializer<?> getType();
  public abstract com.google.gson.JsonObject serializeAdvancement();
  public abstract net.minecraft.resources.ResourceLocation getAdvancementId();
}
```

`ShapedRecipeBuilder.save(Consumer, ResourceLocation)` 的字节码（`javap -p -c`，节选）证实 advancement 由 builder 在 save 时**内联构造**：

```
  2: invokevirtual  ensureValid:(Lnet/minecraft/resources/ResourceLocation;)V
  6: getfield       advancement:Lnet/minecraft/advancements/Advancement$Builder;   // 字段，不是局部 new
  9: getstatic      ROOT_RECIPE_ADVANCEMENT
 12: invokevirtual  Advancement$Builder.parent:(Lnet/minecraft/resources/ResourceLocation;)...
 15: ldc            String has_the_recipe
 17-21: RecipeUnlockedTrigger.unlocked(id) → Advancement$Builder.addCriterion("has_the_recipe", ...)
 24-28: AdvancementRewards$Builder.recipe(id) → Advancement$Builder.rewards(...)
 31-38: RequirementsStrategy.OR → Advancement$Builder.requirements(...)
 38-107: new ShapedRecipeBuilder$Result(id, result, count, group, bookCategory, rows, key,
                                        advancement, recipeId, category.getFolderName(), showNotification)
110: Consumer.accept(Result)
```

**关键点**：advancement 不是单独"提交"的，它被塞进 `Result`（即 `FinishedRecipe` 实例），随后整包交给 provider 的 `Consumer<FinishedRecipe>`。所以"advancement 出口"= `RecipeBuilder.save(...)` 本身。

### 2.3 `FabricRecipeProvider` 没有任何 advancement 相关方法

```
$ javap -p -classpath <fabric-data-generation-api-v1-12.4.0+238242b777.jar> \
        net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider
public abstract class ...FabricRecipeProvider extends net.minecraft.data.recipes.RecipeProvider {
  protected final net.fabricmc.fabric.api.datagen.v1.FabricDataOutput output;
  public ...FabricRecipeProvider(net.fabricmc.fabric.api.datagen.v1.FabricDataOutput);
  public abstract void buildRecipes(java.util.function.Consumer<net.minecraft.data.recipes.FinishedRecipe>);
  protected java.util.function.Consumer<net.minecraft.data.recipes.FinishedRecipe> withConditions(java.util.function.Consumer<net.minecraft.data.recipes.FinishedRecipe>, net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider...);
  public java.util.concurrent.CompletableFuture<?> run(net.minecraft.data.CachedOutput);
  protected net.minecraft.resources.ResourceLocation getRecipeIdentifier(net.minecraft.resources.ResourceLocation);
  private static java.util.concurrent.CompletableFuture[] lambda$run$2(int);
  private void lambda$run$1(java.util.Set, java.util.List, net.minecraft.data.CachedOutput, net.minecraft.data.recipes.FinishedRecipe);
  private static void lambda$withConditions$0(net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider[], java.util.function.Consumer, net.minecraft.data.recipes.FinishedRecipe);
}
```

对照：原版 `RecipeProvider` 才有 `buildAdvancement`，而 `FabricRecipeProvider` **整体重写了 `run`**，不走它：

```
$ javap -p -classpath <MC-merged-1.20.1.jar> net.minecraft.data.recipes.RecipeProvider | grep -n 'run\|buildAdvancement'
7:  public java.util.concurrent.CompletableFuture<?> run(net.minecraft.data.CachedOutput);
8:  protected java.util.concurrent.CompletableFuture<?> buildAdvancement(net.minecraft.data.CachedOutput, net.minecraft.resources.ResourceLocation, net.minecraft.advancements.Advancement$Builder);
```

（`FabricRecipeProvider` 的 javap 清单里**没有** `buildAdvancement`，说明它不是"覆盖了一点"，而是整个 `run` 换掉了。）

### 2.4 `FabricRecipeProvider.run` 源码：同一 conditions 数组写进 recipe 与 advancement

源码解自 `fabric-data-generation-api-v1-c2d2b86c-12.4.0+238242b777-sources.jar`
（`net/fabricmc/fabric/api/datagen/v1/provider/FabricRecipeProvider.java`）：

```java
@Override
public CompletableFuture<?> run(CachedOutput writer) {
    Set<ResourceLocation> generatedRecipes = Sets.newHashSet();
    List<CompletableFuture<?>> list = new ArrayList<>();
    buildRecipes(provider -> {
        ResourceLocation identifier = getRecipeIdentifier(provider.getId());
        if (!generatedRecipes.add(identifier)) {
            throw new IllegalStateException("Duplicate recipe " + identifier);
        }
        JsonObject recipeJson = provider.serializeRecipe();
        ConditionJsonProvider[] conditions = FabricDataGenHelper.consumeConditions(provider);
        ConditionJsonProvider.write(recipeJson, conditions);
        list.add(DataProvider.saveStable(writer, recipeJson, this.recipePathProvider.json(identifier)));
        JsonObject advancementJson = provider.serializeAdvancement();
        if (advancementJson != null) {
            ConditionJsonProvider.write(advancementJson, conditions);          // ← advancement 写同一份条件
            list.add(DataProvider.saveStable(writer, advancementJson,
                    this.advancementPathProvider.json(getRecipeIdentifier(provider.getAdvancementId()))));
        }
    });
    return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
}
```

`withConditions` 的实现只有一件事：把条件挂到"即将提交的 FinishedRecipe 对象"上（`IdentityHashMap` 按对象身份）：

```
$ javap -p -c ... FabricRecipeProvider
  protected ... withConditions(Consumer<FinishedRecipe>, ConditionJsonProvider...);
    Code:
       0: aload_2
       1: arraylength
       2: ifle          9
       5: iconst_1
       6: goto          10
       9: iconst_0
      10: ldc           #36     // String Must add at least one condition.
      12: invokestatic  #42     // Method com/google/common/base/Preconditions.checkArgument:(ZLjava/lang/Object;)V
      15: aload_2
      16: aload_1
      17: invokedynamic #62,  0 // InvokeDynamic #0:accept:(...)
      22: areturn

  private static void lambda$withConditions$0(ConditionJsonProvider[], Consumer, FinishedRecipe);
    Code:
       0: aload_2
       1: aload_0
       2: invokestatic  #232    // Method net/fabricmc/fabric/impl/datagen/FabricDataGenHelper.addConditions:(Ljava/lang/Object;[Lnet/fabricmc/fabric/api/resource/conditions/v1/ConditionJsonProvider;)V
       5: aload_1
       6: aload_2
       7: invokeinterface #236,  2 // InterfaceMethod java/util/function/Consumer.accept:(Ljava/lang/Object;)V
      12: return
```

`FabricDataGenHelper`（`net/fabricmc/fabric/impl/datagen/FabricDataGenHelper.java`，第 216-225 行，源码 jar）：

```java
private static final Map<Object, ConditionJsonProvider[]> CONDITIONS_MAP = new IdentityHashMap<>();

public static void addConditions(Object object, ConditionJsonProvider[] conditions) {
    CONDITIONS_MAP.merge(object, conditions, ArrayUtils::addAll);
}

@Nullable
public static ConditionJsonProvider[] consumeConditions(Object object) {
    return CONDITIONS_MAP.remove(object);
}
```

⇒ 绑定关系是 **`FinishedRecipe` 实例身份 → 条件数组**；`run` 里 `consumeConditions(provider)` 之后立刻把这个数组写进两份 JSON。

### 2.5 `ConditionJsonProvider` 的写入契约（字段名 = `condition`）

```
$ javap -p -classpath <fabric-resource-conditions-api-v1-2.3.9+1802ada577.jar> \
        net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider
public interface ...ConditionJsonProvider {
  public static void write(com.google.gson.JsonObject, net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider...);
  public default com.google.gson.JsonObject toJson();
  public abstract net.minecraft.resources.ResourceLocation getConditionId();
  public abstract void writeParameters(com.google.gson.JsonObject);
}
```

`toJson()` 字节码（决定写出的 JSON 形状）：

```
  public default com.google.gson.JsonObject toJson();
    Code:
       0: new  #27    // class com/google/gson/JsonObject
       7: astore_1
       8: aload_1
       9: ldc  #88    // String condition            ← 字段名是 "condition"，不是 forge 的 "type"
      11: aload_0
      12: invokeinterface  getConditionId:()Lnet/minecraft/resources/ResourceLocation;
      20: invokevirtual  JsonObject.addProperty:(Ljava/lang/String;Ljava/lang/String;)V
      23: aload_0
      25: invokeinterface  writeParameters:(Lcom/google/gson/JsonObject;)V
      31: areturn
```

`write(JsonObject, ConditionJsonProvider...)` 字节码（顶层键 + 三条硬约束）：

```
   5: arraylength / 15: ldc "Must write at least one condition."
  21: ldc "fabric:load_conditions"  → JsonObject.has(String)
  29-45: 若已存在该键 → new IllegalArgumentException + athrow
  46-98: 逐个 toJson() 塞进 JsonArray（每个元素 Objects.requireNonNull(..., "condition cannot be null")）
 101-108: JsonObject.add("fabric:load_conditions", array)
```

⇒ fabric 生成物形态为：

```json
"fabric:load_conditions": [ { "condition": "astral_dice:chip_recipes_enabled" } ]
```

**平台差异（必须写进 CHANGELOG/代码注释）**：对照三线是 `"conditions": [{"type": ...}]`（forge）/ `"neoforge:conditions": [{"type": ...}]`（neo），fabric 是 `"fabric:load_conditions": [{"condition": ...}]`。字段名与值键都不同，**不要追求四线 JSON 逐字节一致**；一致的判据只能是"同一个 67 条 id 集合"。

### 2.6 顺带确证：资源条件通道覆盖 advancement（不是只覆盖配方）

`fabric-resource-conditions-api-v1` 的 mixin 清单（jar 内 `fabric-resource-conditions-api-v1.mixins.json`）只有 6 个 mixin：`DataPackContentsMixin`, `DataProviderMixin`, `JsonDataLoaderMixin`, `LootManagerMixin`, `SinglePreparationResourceReloaderMixin`, `TagManagerLoaderMixin`（`required: true`，`compatibilityLevel: JAVA_16`）。

真正的注入点在父类 mixin（源码 jar，`net/fabricmc/fabric/mixin/resource/conditions/SinglePreparationResourceReloaderMixin.java`）：

```java
@Mixin(SimplePreparableReloadListener.class)
public class SinglePreparationResourceReloaderMixin {
    // thenAcceptAsync in reload
    @Inject(at = @At("HEAD"), method = "method_18790")
    private void applyResourceConditions(ResourceManager resourceManager, ProfilerFiller profiler, Object object, CallbackInfo ci) {
        fabric_applyResourceConditions(resourceManager, profiler, object);
    }
    protected void fabric_applyResourceConditions(ResourceManager resourceManager, ProfilerFiller profiler, Object object) { }
}
```

对应 1.20.1 具名 jar 的字节码（`method_18790` 就是 reload 的 thenAcceptAsync 回调）：

```
$ javap -p -c <MC-merged-1.20.1.jar> net.minecraft.server.packs.resources.SimplePreparableReloadListener
  private void method_18790(ResourceManager, ProfilerFiller, Object);
      aload_0 / aload_3 / aload_1 / aload_2
      invokevirtual apply:(Ljava/lang/Object;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V
      return
```

而两个数据管理器都继承同一条链：

```
$ javap -p <MC-merged-1.20.1.jar> net.minecraft.server.ServerAdvancementManager
public class net.minecraft.server.ServerAdvancementManager extends net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
  ...
$ javap -p <MC-merged-1.20.1.jar> net.minecraft.world.item.crafting.RecipeManager
public class net.minecraft.world.item.crafting.RecipeManager extends net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
  ...
  public RecipeManager();          // → super(GSON, "recipes")（ldc "recipes"）
  protected void apply(Map<ResourceLocation, JsonElement>, ResourceManager, ProfilerFiller);
$ javap -p <MC-merged-1.20.1.jar> net.minecraft.server.ServerAdvancementManager | grep apply
  protected void apply(java.util.Map<...>, ResourceManager, ProfilerFiller);   // super(GSON, "advancements")
```

`JsonDataLoaderMixin` 里的过滤逻辑（源码）：`obj.has(ResourceConditions.CONDITIONS_KEY)` → `matched = ResourceConditions.objectMatchesConditions(obj)` → `if (!matched) it.remove();`。

⇒ **advancement 与配方经过同一个 `SimpleJsonResourceReloadListener` 的 apply 前置过滤**，`advancements/**.json` 顶层写 `fabric:load_conditions` 在 1.20.1 上会被求值。这与 `fabric-resource-conditions-api-v1` 的官方定位一致（它是对"任意 json 数据"生效的通用通道，不限于配方）。

### 2.7 反事实：如果 advancement 不吃 conditions，会发生什么（1.20.1 的校验在哪一层）

结论：**不会报错、不会崩服、不会刷解析错误——只会静默少发奖励**。

证据链三层：

**(a) 条件被跳过 = 该 advancement 整体不加载**（同一通道，见 2.6）。所以这里讨论的是"advancement 保留、配方被跳过"的错配假设。

**(b) 加载期不校验 `rewards.recipes` 指向的配方是否存在。** `ServerAdvancementManager.apply` 只有一条解析失败日志路径（`Parsing error loading custom advancement {}: {}`），它的 codec 把 `rewards.recipes` 当纯 `ResourceLocation` 列表解析，没有任何对 `RecipeManager` 的反查（`ServerAdvancementManager` 的 javap 里没有 `RecipeManager` 字段/参数）。

**(c) 完成奖励发放期静默跳过。** `AdvancementRewards.grant` 字节码尾部：

```
$ javap -p -c <MC-merged-1.20.1.jar> net.minecraft.advancements.AdvancementRewards
  public void grant(net.minecraft.server.level.ServerPlayer);
     233: getfield      #46   // Field recipes:[Lnet/minecraft/resources/ResourceLocation;
     236: arraylength
     237: ifle          248
     240: aload_1
     242: getfield      #46   // Field recipes:[...
     245: invokevirtual #215  // Method net/minecraft/server/level/ServerPlayer.awardRecipesByKey:([Lnet/minecraft/resources/ResourceLocation;)V
```

`ServerPlayer.awardRecipesByKey`：

```
  26: aload_0
  27: getfield   #259  // Field server:Lnet/minecraft/server/MinecraftServer;
  30: invokevirtual MinecraftServer.getRecipeManager()
  35: invokevirtual RecipeManager.byKey:(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;
  49: invokevirtual java/util/Optional.ifPresent:(Ljava/util/function/Consumer;)V   ← 空 Optional 时什么都不做
  60: invokevirtual awardRecipes:(Ljava/util/Collection;)I
```

⇒ 配方不存在时 `byKey` 返回 `Optional.empty()`，`ifPresent` 不执行，**无异常、无日志**。

**(d) 而且 advancement 仍可能被解锁**：本仓的 advancement `requirements` 是 OR 语义（见 4.4 的 JSON：`"requirements": [["has_blank_chip","has_the_recipe"]]`），`has_blank_chip` 这种 `inventory_changed` 条件与配方是否存在无关。于是最坏结果是：**advancement 弹出来了、解锁提示照发，但"解锁配方"奖励静默落空**。

⇒ 这正好说明"只门控一侧"是应当避免的形态；而 fabric 的 `FabricRecipeProvider` 天然把条件同时写两边，**从机制上排除了这种错配**。

---

## 3. 第 2 问：API 签名核对 + 聚合包可用性

### 3.1 `ResourceConditions` 全部 public 成员与描述符

```
$ javap -classpath .../fabric-resource-conditions-api-v1-c2d2b86c-2.3.9+1802ada577.jar \
        net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions
public final class net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions {
  public static final java.lang.String CONDITIONS_KEY;      // 值 = "fabric:load_conditions"（ldc #25）
  public static final java.lang.String CONDITION_ID_KEY;     // 值 = "condition"
  public static void register(net.minecraft.resources.ResourceLocation, java.util.function.Predicate<com.google.gson.JsonObject>);
  public static java.util.function.Predicate<com.google.gson.JsonObject> get(net.minecraft.resources.ResourceLocation);
  public static boolean objectMatchesConditions(com.google.gson.JsonObject);
  public static boolean conditionsMatch(com.google.gson.JsonArray, boolean) throws java.lang.RuntimeException;
  public static boolean conditionMatches(com.google.gson.JsonObject) throws java.lang.RuntimeException;
  static {};
}
```

`register` 的字节码（**重复注册抛 `IllegalArgumentException`**，这是实现时必须处理的一点）：

```
  public static void register(ResourceLocation, Predicate<JsonObject>);
       0: aload_0
       1: ldc  #26  // String Identifier may not be null.
       3: invokestatic Objects.requireNonNull
       7: aload_1
       8: ldc  #34  // String Condition may not be null.
      10: invokestatic Objects.requireNonNull
      14: getstatic    #36  // Field REGISTERED_CONDITIONS:Ljava/util/Map;
      19: invokeinterface java/util/Map.put
      24: ifnull       44
      27: new  #44  // class java/lang/IllegalArgumentException
      43: athrow
      44: return
```

`objectMatchesConditions` 的字节码（**fail-closed**：任何 RuntimeException → warn + 返回 false → 该对象被丢弃）：

```
  public static boolean objectMatchesConditions(JsonObject);
       0: aload_0
       1: ldc  #17  // String fabric:load_conditions
       4: invokestatic GsonHelper.getAsJsonArray:(Lcom/google/gson/JsonObject;Ljava/lang/String;Lcom/google/gson/JsonArray;)...
       8: aload_1
       9: ifnonnull     14
      12: iconst_1          // 没有该键 → true（无条件对象照常加载）
      14: invokestatic conditionsMatch:(Lcom/google/gson/JsonArray;Z)Z
      20: astore_1
      21..38: LOGGER.warn("Skipping object %s. Failed to parse resource conditions", ...)
      43: iconst_0          // → 丢弃
    Exception table: RuntimeException → 20
```

`conditionMatches`：读 `"condition"` 字段 → `ResourceLocation` → `get(...)`；未注册 → `JsonParseException("Unknown condition ...")`；注册则 `predicate.test(jsonObject)`。
`conditionsMatch(arr, true)`：AND 语义、遇 false 立刻短路返回 false；元素不是 `JsonObject` → `JsonParseException("Cannot parse condition entry %s")`。

> **实现风险**：条件 id 拼错或**忘记注册**，不会崩，但 67 个配方与 67 个 advancement 会一起被静默丢弃（只有一条 WARN）。因此 `register()` 必须在模组初始化阶段无条件执行。

### 3.2 `FabricRecipeProvider.withConditions` 重载清单

```
$ javap -p ... FabricRecipeProvider   （见 2.3 全量输出）
protected java.util.function.Consumer<net.minecraft.data.recipes.FinishedRecipe>
    withConditions(java.util.function.Consumer<net.minecraft.data.recipes.FinishedRecipe>,
                   net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider...);
```

- **只有一个重载**（`javap -p` 全量清单里没有 `ResourceLocation` 变体，也没有别的 `withConditions`）。
- 访问级 `protected` ⇒ 从 `ModRecipeProvider`（子类）内可直接调用。
- 失败条件：`Preconditions.checkArgument(conditions.length > 0, "Must add at least one condition.")`。我们每次都传 1 个，成立。

### 3.3 `fabric.mod.json` 的 depends 与聚合包内容

本线自身 `fabric-1.20.1/src/main/resources/fabric.mod.json`（`:38` 起实读，原文含构建期占位符）：

```json
  "depends": {
    "fabricloader": ">=${loader_version}",
    "minecraft": "${minecraft_version_range}",
    "fabric-api": "*",
    "fabric-data-attachment-api-v1": "*",
    "puzzleslib": "${puzzleslib_version_range}",
    "starengine": "${lib_version_range}"
  },
```

- `"fabric-api": "*"` 落在 **`fabric-1.20.1/src/main/resources/fabric.mod.json:41`** —— `fabric-api` **已经是硬前置**，且**本身就是通配**（`"*"`），不需要为 resource-conditions 模块新增任何 depends 条目。
- 其余依赖的版本写的是 `${...}` 占位符，由构建期 `processResources` 展开（不是字面值）；这不影响本次结论（`fabric-api` 那条不带占位符）。

聚合 jar 的元数据：

```
fabric-api-c2d2b86c-0.92.12+1.20.1.jar → fabric.mod.json:
  "id": "fabric-api", "version": "0.92.12+1.20.1",
  "provides": ["fabric"],
  "depends": { "fabricloader": ">=0.16.10", "java": ">=17", "minecraft": ">=1.20 <1.20.2-" }
```

聚合 jar 内 `META-INF/jars/` 下共 **53** 个嵌套模块 jar（`unzip -l ... | grep -c 'META-INF/jars/'` 得到 54，是因为把 `META-INF/jars/` **目录条目本身**也计入了；只数 `.jar` 为 53），其中含：

```
$ unzip -l fabric-api-c2d2b86c-0.92.12+1.20.1.jar | grep -E 'resource-conditions|data-generation'
   ...  META-INF/jars/fabric-resource-conditions-api-v1-0.92.12.jar
   ...  META-INF/jars/fabric-data-generation-api-v1-0.92.12.jar
```

内层模块元数据（解出 `META-INF/jars/fabric-resource-conditions-api-v1-0.92.12.jar` 后读其 `fabric.mod.json`）：

```json
{ "id": "fabric-resource-conditions-api-v1",
  "version": "2.3.9+1802ada577",
  "depends": { "fabricloader": ">=0.9.2" },
  "mixins": ["fabric-resource-conditions-api-v1.mixins.json"],
  "custom": { "fabric-api:module-lifecycle": "stable" } }
```

⇒ **`fabric-resource-conditions-api-v1` 随聚合包 jar-in-jar 自动加载**，运行期无需新增 depends；编译期它也在 loom 的 classpath 上（`.gradle/loom-cache/remapped_mods/...` 里就是按模块解出的重映射 jar，正是本次 javap 的对象）。

> 附带确认：`build.gradle` 里的 `-Dfabric-api.datagen.strict-validation` 只被 `FabricDataGenHelper.STRICT_VALIDATION` 传给 `FabricDataGenerator`（"ensure all registry entries have data generated for them"），与"是否允许写条件"无关；datagen 不会因为配方带条件而报错。

---

## 4. 第 3 问：门控集合一致性

### 4.1 方法与判据

`work/recon/_scan2.py`（临时脚本，可删）：递归遍历四线的

- recipe 目录：`fabric-1.20.1/forge-1.20.1` → `src/generated/resources/data/astral_dice/recipes`；`neoforge-1.21.1/neoforge-26.1.2` → `.../recipe`
- advancement 目录：`fabric-1.20.1/forge-1.20.1` → `.../advancements`；`neoforge-1.21.1/neoforge-26.1.2` → `.../advancement`

对每个 json 读 **顶层键** 判定是否带条件（`fabric:load_conditions` / `neoforge:conditions` / `forge:conditions` / `conditions`），并统计文件名以 `_chip` 结尾的条目。

### 4.2 四线统计（全部实测）

| 线 | recipe 总数 / 带条件 | chip 配方 / 其中带条件 | advancement 总数 / 带条件 | chip advancement / 其中带条件 |
|---|---|---|---|---|
| `fabric-1.20.1` | 108 / **0** | 67 / **0** | 108 / **0** | 67 / **0** |
| `forge-1.20.1` | 108 / **67** | 67 / **67** | 108 / **67** | 67 / **67** |
| `neoforge-1.21.1` | 108 / **67** | 67 / **67** | 108 / **67** | 67 / **67** |
| `neoforge-26.1.2` | 108 / **67** | 67 / **67** | 108 / **67** | 67 / **67** |

（advancement 总数 108 而不是 107，是因为 `advancements` 下有 `recipes/misc` **与** `recipes/tools` 两个子目录——`dice` 的 advancement 在 `recipes/tools/dice.json`，与配方 `category: "equipment"` 对应。只数 `recipes/misc` 会得到 107 这个看似"少一条"的假象。）

### 4.3 集合差（脚本输出原文）

```
== diff vs neoforge-1.21.1: fabric-1.20.1
  recipe chips only-here: []
  recipe chips only-ref : []
  adv chips only-here   : []
  adv chips only-ref    : []
== diff vs neoforge-1.21.1: forge-1.20.1
  （同上，四行全空）
== diff vs neoforge-1.21.1: neoforge-26.1.2
  （同上，四行全空）
== chip count equality across lines: {'fabric-1.20.1': (67, 67), 'forge-1.20.1': (67, 67),
                                     'neoforge-1.21.1': (67, 67), 'neoforge-26.1.2': (67, 67)}
```

⇒ **四线的 chips id 集合逐字相同（67 = 67 = 67 = 67），差异只在"带不带条件"这一维**：
「仅在 fabric 有」= ∅；「仅在对线有」= ∅。**不存在需要补/删的配方条目**，只存在"给 fabric 的这 67 条加上条件标记"这一件事。

### 4.4 `blank_chip_duplicate.json` 该不该算进门控集合

**不该（按三条对照线的一致口径）。**

- 四线都存在该文件，且四线**都不带条件**（`exists=True, conditioned=False` ×4）。
- 该配方由源码显式命名生成：`ModRecipeProvider.java:276`
  `.save(output::accept, new ResourceLocation(AstralDiceMod.MODID, "blank_chip_duplicate"))`
  内容是一条 **1 × `astral_dice:blank_chip` → 2 × `astral_dice:blank_chip`** 的复制配方（`SBS/SCS/SSS`，B=`blank_chip`，C=`iron_block`，S=`star_coin`，`result.count = 2`）。
- 也就是说：即使 `enable_chip_recipes=false`，玩家仍能通过这条配方"复制"空白筹码。这是**四线共有的历史行为**，不是 fabric 特有的漏口。
- 结论：**按"与对照线保持一致"的判据，本次不动它**（67 而非 68）。若业务上要求"关掉筹码就彻底做不出来"，那是另一个跨四线的独立决策，应单独立项、四线同改——本次不夹带。

> 但也必须写明：**它是唯一一条"关掉开关后仍可产出筹码"的路径**。如果验收标准是"关掉后筹码完全不可获得"，这条会被判失败。

### 4.5 advancement 侧计数与对照线是否也有条件

- fabric：67 个 chip advancement，**0** 条带条件。
- 其余三线：67 个 chip advancement，**67** 条带条件；条件在 advancement 顶层与 recipe 顶层逐条对应（样本见下）。

forge-1.20.1 的 `advancements/recipes/misc/abyss_reagent_chip.json`（实读，节选）：

```json
{
  "parent": "minecraft:recipes/root",
  "conditions": [ { "type": "astral_dice:chip_recipes_enabled" } ],
  "criteria": { "has_blank_chip": {...}, "has_the_recipe": { "conditions": { "recipe": "astral_dice:abyss_reagent_chip" }, ... } },
  "requirements": [ [ "has_blank_chip", "has_the_recipe" ] ],
  "rewards": { "recipes": [ "astral_dice:abyss_reagent_chip" ] },
  "sends_telemetry_event": false
}
```

fabric-1.20.1 同 id 的 advancement（实读）与它逐字段相同，**唯一差别就是没有 `conditions` 那段**——这直接印证 2.4 的源码结论：条件是 `run` 在 `serializeAdvancement()` 之后附加的，其余内容完全由原版 builder 生成。

### 4.6 ⚠️ 假阳性陷阱（本次差点踩到）

advancement JSON 里**本来就存在**嵌套的 `"conditions"` 键：

- criterion `has_blank_chip` 的 play：`"conditions": { "items": [ ... ] }`（`inventory_changed` 触发器的物品条件）
- criterion `has_the_recipe` 的 play：`"conditions": { "recipe": "astral_dice:..." }`（`recipe_unlocked` 触发器的配方 id）

因此 `grep -l '"conditions"' advancement/**/*.json` 会得到 **108/108 全部命中**的**错误**结论。本报告的统计全部按顶层键判定，脚本逻辑见 `work/recon/_scan2.py`。

---

## 5. 第 4 问：配置开关的运行期语义

### 5.1 `ENABLE_CHIP_RECIPES` 的值从哪来（读的是内存字段，不是文件）

`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/config/ModCommonConfig.java`：

```java
// :52 字段
public static final ForgeConfigSpec.BooleanValue ENABLE_CHIP_RECIPES;      // ModCommonConfig.java:52
// :109 定义（键名 / 注释 / 默认值与 forge 逐字对齐）
ENABLE_CHIP_RECIPES = builder.comment("是否启用筹码合成配方(默认：false；关闭时筹码无法通过合成获得)")
        .define("enable_chip_recipes", false);                             // ModCommonConfig.java:109-110
```

`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/config/ForgeConfigSpec.java`（自实现 shim，280 行）关键实现：

```java
public T get() { return value; }                    // 读内存字段
void setRaw(Object raw) { this.value = coerce(raw); }
public boolean load(Path file) { /* Files.readAllLines 逐行解析 → v.setRaw(value) */ }
public boolean save(Path file) { /* render() 写回 */ }
```

类注释（原文）：

> ⚠️ 与 Forge 的差异(已登记):不支持 `worldRestart`/热重载回调(**本模组的配置在启动时读一次**,与既有行为一致)

### 5.2 唯一的加载点

```
$ grep -n 'setupConfig();\|private static void setupConfig\|public void onInitialize' \
        fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java
46:    public void onInitialize()
107:        setupConfig();
244:    private static void setupConfig()
```

`setupConfig()` 内部：`backupOldConfigIfNeeded(configPath, CONFIG_VERSION)` → `SPEC.load(configPath)` → `SPEC.save(configPath)` → `GameplayConstants.applyConfig(ModCommonConfig.snapshot())`。
配置文件路径：`FMLPaths.CONFIGDIR/astral_dice-common.toml`。

全仓检索确认**没有**任何配置热重载订阅：

```
$ grep -rn 'ConfigReload|configReload|Reloading' --include=*.java fabric-1.20.1/src/main/java
（0 命中）
```

### 5.3 1.20.1 的配方/advancement 重载触发点

| 触发 | 证据 |
|---|---|
| `/reload` 命令 | `ReloadCommand.reloadPacks(Collection<String>, CommandSourceStack)` → `MinecraftServer.reloadResources(Collection)`（bytecode: `getServer()` → `invokevirtual reloadResources` → `CompletableFuture.exceptionally`） |
| `MinecraftServer.reloadResources` | `public java.util.concurrent.CompletableFuture<java.lang.Void> reloadResources(java.util.Collection<java.lang.String>)`（javap） |
| 谁被重载 | `ReloadableServerResources.listeners()` 字节码 = `List.of(this.tagManager, this.lootData, this.recipes, this.functionLibrary, this.advancements)` ⇒ **RecipeManager 与 ServerAdvancementManager 都在列** |
| Fabric 事件 | `fabric-lifecycle-events-v1`（聚合包内真实版本 `2.2.23+1802ada577`）的 `MinecraftServerMixin`：`@Inject(method="reloadResources", at=@At("HEAD"))` → `ServerLifecycleEvents.START_DATA_PACK_RELOAD.invoker().startDataPackReload(server, resourceManager)`；`at=@At("TAIL")` → 在 `cir.getReturnValue().handleAsync(...)` 里发 `END_DATA_PACK_RELOAD`（javap -v 的 RuntimeVisibleAnnotations 实测两条） |

### 5.4 条件求值时机

- 求值发生在 **reload listener 的 apply 前置阶段**：`SinglePreparationResourceReloaderMixin` 在 `method_18790` **HEAD** 注入（= 每个 listener 的 thenAcceptAsync 回调入口）。
- 粒度：**每次资源加载、逐条目一次**。`JsonDataLoaderMixin` 对 `Map<ResourceLocation, JsonElement>` 逐 entry 判断 `has(CONDITIONS_KEY)` → `objectMatchesConditions(obj)`；没有条件的对象直接跳过（不调用 predicate）。
- 服务端启动时同样走这条路径（首次 `ReloadableServerResources` 装载也是 reload），所以**开服有一次求值，每次 `/reload` 再各求一次**。

### 5.5 结论

**运行期改 `enable_chip_recipes` 必须重启服务端。**

推理链（全部有据）：
1. `ChipRecipesEnabledCondition.isEnabled()` → `ModCommonConfig.ENABLE_CHIP_RECIPES.get()` → shim 的 `ConfigValue.get()` 返回**内存字段**（5.1）。
2. 该字段只在 `setupConfig()`（`AstralDiceMod.java:107`，`onInitialize` 内）里被 `SPEC.load(...)` 写一次（5.2）。
3. 没有任何配置重载监听器会再次调用 `SPEC.load(...)`（5.2 的 0 命中）。
4. `/reload` 会重新求值条件（5.4），但 `get()` 读到的仍是启动时那份缓存 ⇒ **求值结果不变**。

⇒ 玩家改 `config/astral_dice-common.toml` 后，**必须重启**才生效；`/reload` 不够（但也不会出错，只是不变）。

> 与 forge/neoforge 线是否支持配置热重载的对照，**不在本次取证范围**（未读数、未验证），见第 7 节。

---

## 6. 实施清单（按文件 + 精确锚点）

> 以下均为**待实施**内容，本次未执行任何改动。所有路径相对仓库根 `D:/MCProject/astral_dice_multiloader`。

### 6.1 文件 A：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/recipe/condition/ChipRecipesEnabledCondition.java`

现状：`final class`（不实现任何接口），`register()` 是空实现，类注释断言"Fabric 1.20.1 没有任何对应机制"——**该断言已被本报告第 2/3 节推翻**。

改动点：

1. **类声明**（当前 `:25`）：
   `public final class ChipRecipesEnabledCondition` → `public final class ChipRecipesEnabledCondition implements net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider`
2. **新增两个接口实现**（放在 `getID()` 附近）：
   ```java
   @Override
   public ResourceLocation getConditionId() { return ID; }   // 复用现有 ID 常量

   @Override
   public void writeParameters(com.google.gson.JsonObject json) {
       // 无参数条件：保持 JSON 只有 {"condition": "astral_dice:chip_recipes_enabled"}
   }
   ```
3. **重写 `register()`**（当前 `:87` 附近，空实现）：
   ```java
   private static boolean registered = false;

   public static void register() {
       if (registered) return;                     // ResourceConditions.register 对重复注册抛 IllegalArgumentException
       net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions.register(
               ID, json -> INSTANCE.isEnabled());
       registered = true;
   }
   ```
4. **订正类注释**：删除/改写"Fabric 1.20.1 没有任何对应机制""ResourceCondition 是 1.21.2+ 才有"这两句（与事实相反），改为记录真实机制：`fabric:load_conditions` + `FabricRecipeProvider.withConditions` + `JsonDataLoaderMixin`/`SinglePreparationResourceReloaderMixin`，并写明"条件作用域 = 所有 SimpleJsonResourceReloadListener 类型的数据（配方、advancement、战利品表、标签…）"。
5. 保留 `isEnabled()` / `ID` / `INSTANCE` / `toString()` 不变（与 forge 线 seam 对齐）。

> `getID()` 与 `getConditionId()` 并存没问题（前者是 forge seam，后者是 Fabric 接口）；如果不想保留两套，可让 `getID()` 直接委托 `getConditionId()`。

### 6.2 文件 B：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceMod.java`

- 锚点：`onInitialize()`（`:46`）内，`setupConfig();`（**`:107`**）之后、`BUS.post(new FMLCommonSetupEvent());`（`:109`）之前。
- 插入一行：
  ```java
  com.merlinkitsune.astral_dice.recipe.condition.ChipRecipesEnabledCondition.register();
  ```
- 理由：`register` 必须在**任何数据包加载之前**完成；`onInitialize()` 在客户端与专用服务器两侧都会执行，是唯一合适的时机。放在 `setupConfig()` 之后只是为了让日志/配置顺序可读，功能上先后无影响（predicate 在求值时才读值）。
- 注意：**不能**放到客户端专属路径（`AstralDiceClient`），否则专用服务器上条件未注册 → 67+67 个 JSON 被 warn 丢弃。

### 6.3 文件 C：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java`

- 锚点：`buildRecipes` 签名 **`:37`**（`public void buildRecipes(Consumer<FinishedRecipe> output)`）。
- 改动（**只有签名 + 方法体前 3 行**；102 处 `.save(output::accept)` 一行不动）：
  ```java
  @Override
  public void buildRecipes(Consumer<FinishedRecipe> rawOutput) {                     // 原形参 output → rawOutput
      // 只有 id 以 _chip 结尾的配方带 astral_dice:chip_recipes_enabled 条件；
      // blank_chip_duplicate 等其余配方保持无条件，与 neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 保持一致。
      Consumer<FinishedRecipe> gatedOutput = withConditions(rawOutput, ChipRecipesEnabledCondition.INSTANCE);
      Consumer<FinishedRecipe> output = recipe ->
              (recipe.getId().getPath().endsWith("_chip") ? gatedOutput : rawOutput).accept(recipe);
      ...
  ```
- 需要的 import：`com.merlinkitsune.astral_dice.recipe.condition.ChipRecipesEnabledCondition`（`Consumer` 已在用）。
- 为什么必须这样改（**两个不可行方案，已排除**）：
  - 「在方法体内给 `output` 重新赋值」→ **编译不过**：`.save(output::accept)` 是捕获 `output` 的 lambda，Java 要求捕获变量 effectively-final，102 处会一起报错。
  - 「改成 `withConditions` 门控整个 provider（把所有 108 条都包进去）」→ 会把 40 条非筹码配方也门控，与三条对照线偏离。
- 判据选择说明：按 **`recipe.getId().getPath().endsWith("_chip")`** 判定，而不是按产出物 `ModItems.*_CHIP` 判定。原因：`blank_chip` 配方的产出物是筹码（`blank_chip`），但它被显式命名为 `blank_chip_duplicate`，按产出物判定会**多门控 1 条**（68 ≠ 67）。按 id 判定恰好得到 67 条，与对照线一致（源码侧实测：68 个 chip 产出块的保存点里，只有 `:276` 是显式改名的那一条）。

### 6.4 生成产物（本次未执行，仅给出预期）

实施后需重跑 fabric datagen（`build.gradle:282-287` 已配好 `-Dfabric-api.datagen.output-dir=src/generated/resources`、`-Dfabric-api.datagen.modid=astral_dice`、`-Dfabric-api.datagen.strict-validation`）。

预期 diff（**67 + 67 = 134 个文件**）：

- `fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/*_chip.json` —— 67 个文件各多一段顶层键：
  ```json
  "fabric:load_conditions": [ { "condition": "astral_dice:chip_recipes_enabled" } ]
  ```
- `fabric-1.20.1/src/generated/resources/data/astral_dice/advancements/recipes/misc/*_chip.json` —— 同样 67 个文件各多同一段。
- **不应**变化的文件：`blank_chip_duplicate.json`（recipe 与 advancement）、29 个 sign 配方、`dice`、`conductive_wire` 等其余 40 条。
- 键的位置取决于 gson 序列化顺序（fabric 是在 `serializeRecipe()/serializeAdvancement()` 之后附加），**本报告不对键序作断言**；判据只认"顶层键存在且值为该条件"。

### 6.5 落地顺序与自检

1. A → B → C 三处改完，先跑编译（本次未跑）。
2. 重跑 datagen，用 4.1 的顶层键脚本复核：fabric 应为 recipe 67/108、advancement 67/108，且 chip 集合与 neoforge-1.21.1 的完全相等。
3. 反向验证（值得做）：把 `enable_chip_recipes` 设为 false 启动一次，确认启动日志里 **没有** `Skipping object ... Failed to parse resource conditions` 这类 WARN（该 WARN 出现即代表条件未注册/拼错，属于故障而非正常路径）。

---

## 7. 未确证项 / 风险（拿不到证据或超出本次边界）

1. **运行期实测未做**（用户明确禁止构建/实机）。"67+67 个 JSON 在 `enable_chip_recipes=false` 时被真实丢弃、`=true` 时全部加载"属于**源码与字节码层面的成立**，不是实测结论。
2. **datagen 产物未生成**。6.4 的 diff 是从 `FabricRecipeProvider.run` 源码推导的**预期**，未经逐字节验证；尤其 `DataProvider.saveStable` 的键序无法从字节码断言。
3. **另外三线（forge-1.20.1 / neoforge-1.21.1 / neoforge-26.1.2）的配置热重载语义未取证**。第 5 节只证明了 fabric 侧"必须重启"；三条对照线是否支持热重载、是否同一行为，**无法判定**（未读它们的 config 实现与事件订阅）。
4. **客户端侧数据包加载是否也会求值该条件，未实测**。推理上：专用服务器的 `RecipeManager`/`ServerAdvancementManager` 在服务端；客户端通过 `ClientPacketListener` 接收已过滤的配方/advancement 同步数据，不自行加载 `data/astral_dice/recipes`。单人存档由集成服务器走同一条服务端路径。但本次未做客户端侧取证，**标为未确证**。
5. **`blank_chip_duplicate` 是否应被门控属于业务裁决，不是技术事实**。本报告按"与三条对照线一致"的口径判定不纳入；若产品口径是"关掉开关后筹码彻底不可获得"，则该条会成为唯一漏口（见 4.4），需另立跨四线决策。
6. **loom 重映射 jar 与生产环境实际加载的 jar-in-jar 模块的字节一致性未比对**。两者模块版本号一致（`2.3.9+1802ada577` / `12.4.0+238242b777`），但未做哈希比对；生产环境加载的是聚合 jar 内嵌的那份。
7. **`ResourceConditions.register` 的注册时序边界未实测**：本报告只主张"在 `onInitialize()` 内注册即可在任何数据包加载之前完成"，未实测"如果在 `SERVER_STARTED` 才注册会怎样"（推演上会因资源已加载而迟到，但未验证）。
8. **未验证 `/reload` 对 `ChipRecipesEnabledCondition` 之外还有无副作用**沿该线（本线原本没有任何 `START_DATA_PACK_RELOAD` 订阅，新增注册不涉及事件面）。

---

## 附录 A：67 个 `*_chip` 配方的 advancement 出口逐条对照

**出口方法对全部 67 条相同**：`net.minecraft.data.recipes.ShapedRecipeBuilder`（`RecipeBuilder` 实现）的
`save(java.util.function.Consumer<net.minecraft.data.recipes.FinishedRecipe>)`
——源码表现为 `.save(output::accept)`。
advancement 由该 `save` 内部构造并挂在 `FinishedRecipe` 上，最终由
`FabricRecipeProvider.run(CachedOutput)` 写出（并把条件同时写进两份 JSON，见 2.4）。

统计：`ModRecipeProvider.java` 共 102 处 `.save(output::accept)` + 4 处显式命名 `.save(output::accept, new ResourceLocation(...))`；其中结果物为筹码的构建块共 **68** 个，**1 个**（`blank_chip` → 显式命名成 `blank_chip_duplicate`，`:276`）的 id 不以 `_chip` 结尾，故纳入门控的是 **67** 个。

| # | 配方 id | 构建器起始行 | `.save(` 行 | 构建器 |
|---|---|---|---|---|
| 1 | `abyss_reagent_chip` | :1002 | :1012 | ShapedRecipeBuilder |
| 2 | `adrenaline_high_chip` | :831 | :840 | ShapedRecipeBuilder |
| 3 | `adrenaline_low_chip` | :812 | :826 | ShapedRecipeBuilder |
| 4 | `advanced_peripherals_chip` | :882 | :892 | ShapedRecipeBuilder |
| 5 | `airbag_chip` | :936 | :946 | ShapedRecipeBuilder |
| 6 | `atm_chip` | :493 | :502 | ShapedRecipeBuilder |
| 7 | `bank_card_high_chip` | :518 | :527 | ShapedRecipeBuilder |
| 8 | `bank_card_low_chip` | :505 | :515 | ShapedRecipeBuilder |
| 9 | `bank_card_unlimited_chip` | :530 | :539 | ShapedRecipeBuilder |
| 10 | `big_backpack_chip` | :442 | :451 | ShapedRecipeBuilder |
| 11 | `big_bowl_stew_chip` | :989 | :999 | ShapedRecipeBuilder |
| 12 | `bookmark_chip` | :1057 | :1067 | ShapedRecipeBuilder |
| 13 | `boxing_gloves_high_chip` | :567 | :576 | ShapedRecipeBuilder |
| 14 | `boxing_gloves_low_chip` | :543 | :552 | ShapedRecipeBuilder |
| 15 | `boxing_gloves_medium_chip` | :555 | :564 | ShapedRecipeBuilder |
| 16 | `buffer_shield_chip` | :706 | :716 | ShapedRecipeBuilder |
| 17 | `candy_chip` | :770 | :780 | ShapedRecipeBuilder |
| 18 | `current_core_chip` | :908 | :918 | ShapedRecipeBuilder |
| 19 | `cursed_sword_chip` | :732 | :742 | ShapedRecipeBuilder |
| 20 | `cutter_blade_chip` | :404 | :413 | ShapedRecipeBuilder |
| 21 | `cutter_chip` | :217 | :227 | ShapedRecipeBuilder |
| 22 | `eagle_scope_chip` | :292 | :301 | ShapedRecipeBuilder |
| 23 | `eight_sided_dice_chip` | :370 | :380 | ShapedRecipeBuilder |
| 24 | `electric_glove_chip` | :923 | :933 | ShapedRecipeBuilder |
| 25 | `electric_sword_chip` | :869 | :879 | ShapedRecipeBuilder |
| 26 | `energy_recycler_chip` | :856 | :866 | ShapedRecipeBuilder |
| 27 | `extra_battery_chip` | :1122 | :1131 | ShapedRecipeBuilder |
| 28 | `flashlight_burst_chip` | :1029 | :1039 | ShapedRecipeBuilder |
| 29 | `flashlight_chip` | :230 | :240 | ShapedRecipeBuilder |
| 30 | `fortune_stick_chip` | :1016 | :1026 | ShapedRecipeBuilder |
| 31 | `friendship_badge_chip` | :783 | :796 | ShapedRecipeBuilder |
| 32 | `golden_shooting_star_chip` | :257 | :266 | ShapedRecipeBuilder |
| 33 | `hand_fan_big_chip` | :480 | :489 | ShapedRecipeBuilder |
| 34 | `hand_fan_small_chip` | :467 | :477 | ShapedRecipeBuilder |
| 35 | `infinite_grid_chip` | :975 | :985 | ShapedRecipeBuilder |
| 36 | `magic_quiver_chip` | :693 | :703 | ShapedRecipeBuilder |
| 37 | `magic_tome_chip` | :429 | :439 | ShapedRecipeBuilder |
| 38 | `marker_sprayer_chip` | :416 | :426 | ShapedRecipeBuilder |
| 39 | `medkit_complete_chip` | :316 | :325 | ShapedRecipeBuilder |
| 40 | `medkit_emergency_chip` | :304 | :313 | ShapedRecipeBuilder |
| 41 | `member_recommendation_chip` | :1070 | :1080 | ShapedRecipeBuilder |
| 42 | `moto_helmet_high_chip` | :643 | :652 | ShapedRecipeBuilder |
| 43 | `moto_helmet_low_chip` | :618 | :628 | ShapedRecipeBuilder |
| 44 | `moto_helmet_medium_chip` | :631 | :640 | ShapedRecipeBuilder |
| 45 | `ninja_star_chip` | :454 | :464 | ShapedRecipeBuilder |
| 46 | `perpetual_motion_chip` | :895 | :905 | ShapedRecipeBuilder |
| 47 | `piercing_gun_chip` | :757 | :767 | ShapedRecipeBuilder |
| 48 | `piggy_bank_chip` | :1083 | :1093 | ShapedRecipeBuilder |
| 49 | `primordial_core_chip` | :962 | :972 | ShapedRecipeBuilder |
| 50 | `purple_shooting_star_chip` | :243 | :252 | ShapedRecipeBuilder |
| 51 | `railgun_chip` | :949 | :959 | ShapedRecipeBuilder |
| 52 | `revenge_halberd_chip` | :745 | :754 | ShapedRecipeBuilder |
| 53 | `sandwich_high_chip` | :681 | :690 | ShapedRecipeBuilder |
| 54 | `sandwich_low_chip` | :656 | :666 | ShapedRecipeBuilder |
| 55 | `sandwich_medium_chip` | :669 | :678 | ShapedRecipeBuilder |
| 56 | `satellite_chip` | :800 | :809 | ShapedRecipeBuilder |
| 57 | `scope_chip` | :279 | :289 | ShapedRecipeBuilder |
| 58 | `sensor_chip` | :1043 | :1053 | ShapedRecipeBuilder |
| 59 | `smart_watch_chip` | :1096 | :1106 | ShapedRecipeBuilder |
| 60 | `speed_skates_high_chip` | :605 | :614 | ShapedRecipeBuilder |
| 61 | `speed_skates_low_chip` | :580 | :590 | ShapedRecipeBuilder |
| 62 | `speed_skates_medium_chip` | :593 | :602 | ShapedRecipeBuilder |
| 63 | `star_coin_hammer_chip` | :719 | :729 | ShapedRecipeBuilder |
| 64 | `target_chip` | :340 | :349 | ShapedRecipeBuilder |
| 65 | `vitamin_pill_chip` | :328 | :337 | ShapedRecipeBuilder |
| 66 | `warp_engine_chip` | :844 | :853 | ShapedRecipeBuilder |
| 67 | `whetstone_chip` | :1109 | :1119 | ShapedRecipeBuilder |

---

## 附录 B：可复现命令清单（只读）

```bash
# 0) 变量
cd /d/MCProject/astral_dice_multiloader
JAVAP='/c/Program Files/Zulu/zulu-21/bin/javap'
MC="C:/Users/xmace/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar"
RC=$(ls .gradle/loom-cache/remapped_mods/remapped/net/fabricmc/fabric-api/fabric-resource-conditions-api-v1-c2d2b86c/*/*.jar | grep -v sources | head -1)
DG=$(ls .gradle/loom-cache/remapped_mods/remapped/net/fabricmc/fabric-api/fabric-data-generation-api-v1-c2d2b86c/*/*.jar | grep -v sources | head -1)
API=$(ls .gradle/loom-cache/remapped_mods/remapped/net/fabricmc/fabric-api/fabric-api-c2d2b86c/*/*.jar | grep -v sources | head -1)

# 1) 资源条件 API
"$JAVAP" -p -classpath "$RC" net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions
"$JAVAP" -p -classpath "$RC" net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider
"$JAVAP" -p -c -classpath "$RC" net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions   # register / objectMatchesConditions
"$JAVAP" -p -c -classpath "$RC" net.fabricmc.fabric.api.resource.conditions.v1.ConditionJsonProvider # toJson / write

# 2) datagen API
"$JAVAP" -p -classpath "$DG" net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider
"$JAVAP" -p -c -classpath "$DG" net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider | sed -n '/withConditions/,/^$/p'
unzip -p "$DG" net/fabricmc/fabric/api/datagen/v1/provider/FabricRecipeProvider.java   # 需 -sources.jar 版本
unzip -p "$DG" net/fabricmc/fabric/impl/datagen/FabricDataGenHelper.java              # 需 -sources.jar 版本

# 3) 聚合包与模块
unzip -l "$API" | grep 'META-INF/jars/'          # 54 个嵌套模块
unzip -p "$API" fabric.mod.json
unzip -p "$API" META-INF/jars/fabric-resource-conditions-api-v1-0.92.12.jar > /tmp/rc.jar && unzip -p /tmp/rc.jar fabric.mod.json

# 4) 原版侧（重载链路 / advancement 奖励）
"$JAVAP" -p -classpath "$MC" net.minecraft.server.MinecraftServer | grep reloadResources
"$JAVAP" -p -c -classpath "$MC" net.minecraft.server.ReloadableServerResources | sed -n '/listeners/,/^$/p'
"$JAVAP" -p -classpath "$MC" net.minecraft.data.recipes.RecipeBuilder
"$JAVAP" -p -classpath "$MC" net.minecraft.data.recipes.FinishedRecipe
"$JAVAP" -p -c -classpath "$MC" net.minecraft.server.level.ServerPlayer | sed -n '/awardRecipesByKey/,/^$/p'
"$JAVAP" -p -c -classpath "$MC" net.minecraft.advancements.AdvancementRewards | grep -A6 -B6 awardRecipesByKey

# 5) 四线条件集合统计
/c/Users/xmace/.local/bin/python work/recon/_scan2.py        # 顶层键统计 + 集合差
/c/Users/xmace/.local/bin/python work/recon/_scan3.py        # 67 条 chip 的 save 行
```

（`work/recon/_scan*.py` 是本次为取证临时写的小脚本；如需保持 `work/` 干净可删除，报告不依赖它们运行。）
