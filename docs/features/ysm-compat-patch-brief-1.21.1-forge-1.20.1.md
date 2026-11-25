# YSM 半透明兼容：1.21.1 / 1.20.1 修补任务上下文

> 状态：**待实施**（26.1.2 已交付并实机验证，见 `docs/features/ysm-compat-err54-and-verification.md`）
> 范围：`neoforge-1.21.1`、`forge-1.20.1` 两条线。**fabric-1.20.1 不在本任务范围**（用户 2026-11-01 明确指示）。
> 不改动：`neoforge-26.1.2` 任何文件、fabric 线任何文件。

---

## 0. 任务卡（可直接拷给执行者）

```text
目标：让 neoforge-1.21.1 与 forge-1.20.1 两条线上，装了 YSM 的玩家在持有
      astral_dice:alana_iron_maiden / astral_dice:deepseek_fishing 时渲染为 50% 半透明，
      无效果时不透明，YSM 缺席时与改动前逐像素一致。

范围：neoforge-1.21.1/、forge-1.20.1/   （禁止改 neoforge-26.1.2/ 与 fabric-1.20.1/）
产物：client/ClientAlpha.java + mixin/client/DispatcherAlphaMixin.java
      + mixin/client/RenderTypeAlphaMixin.java + 改 SignTranslucencyMixin.java
      + 两个 astral_dice.mixins.json 的 client 数组各 +2 项
判据：有 YSM 时 on/off 两组截图 模型 bbox 内 diff>60 占比 >5% 且 luma 下降；
      无 YSM 时两组与改前逐像素一致；两线都要有证据。

硬约束：
  1) 注入点必须在 EntityRenderDispatcher（YSM 从调用点劫持，挂 LivingEntityRenderer 是死代码）
  2) 共享类严禁放 …astral_dice.mixin.* 包（会 IllegalClassLoadError，游戏起不来）
  3) alpha 只能一路来源（顶点 alpha 包装与 shader color 同开会 0.25）
  4) 绝不 hook YSM 的混淆类
  5) forge 线部署 build/devlibs（build/libs 是 reobf，会 NoSuchFieldError: f_256929_）
  6) 两线同步落地，不许先做完一条线再做另一条

入口：§5.1 下载 YSM → §4 照抄代码 → §5.2~5.5 实施/构建/部署/验证 → §5.7 两线同步断言 → §5.6 验收
```

---

## 1. 任务定义

让安装了 **YSM（Yes Steve Model，`yes_steve_model`）** 的玩家，在持有 `astral_dice:alana_iron_maiden`（铁处女）或 `astral_dice:deepseek_fishing`（摸鱼）效果时，被渲染成 **50% 半透明**；无效果时完全不透明；**YSM 缺席时行为必须与改动前完全一致**。

26.1.2 线已经做到（实机证据：模型 bbox 内 `diff>60` = 3668/35571 = 10.31%，luma 118.04 → 116.41）。本任务是把同一能力补到 1.21.1 与 1.20.1，但**渲染管线不同，不能照抄代码**。

---

## 2. 为什么 26.1.2 的实现不能照搬

| 维度 | 26.1.2 | 1.21.1 / 1.20.1 |
|---|---|---|
| 渲染模型 | 两段式：`extractRenderState` → `submit(state, PoseStack, SubmitNodeCollector, CameraRenderState)` | 即时模式：`render(entity, yaw, partial, PoseStack, MultiBufferSource, light)` |
| 几何下发 | `SubmitNodeCollection#submitCustomGeometry` + `CustomGeometryRenderer` | `MultiBufferSource#getBuffer(RenderType)` + `VertexConsumer` |
| 我们的注入点 | `EntityRenderDispatcher#submit`（开窗）+ `submitCustomGeometry`（包 renderer）+ `RenderTypes.entityCutout → entityTranslucent` | **两层都要换**，见 §4 |

**但有一条跨版本不变的事实（本任务的设计基石）**：

> YSM 在**所有版本**都从 `EntityRenderDispatcher#render` 的**调用点**劫持渲染，取消对 `EntityRenderer#render` 的调用，再走它自己的渲染类。

因此：**任何挂在 `LivingEntityRenderer` 上的注入点，对 YSM 玩家都是死代码。**
26.1.2 上已经用调试探针实测确认过这一点（同样是「渲染器类就是原版 `AvatarRenderer`，不是 YSM 的类」）。

### 2.1 证据（javap -v 实读，非推断）

YSM 1.21.1（`D:\.minecraft\versions\狐の航空学\mods\[是，史蒂夫模型] ysm-2.6.5-neoforge+mc1.21.1-release.jar`）
配置 `yes_steve_model.mixins.json`：`required: true`、`plugin: com.elfmcys.yesstevemodel.mixin.plugin.MixinTweaker`、client 段含 `client.EntityRenderDispatcherMixin`、`client.LivingRendererMixin`、`client.BufferSourceMixin`、`client.WorldRendererMixin` 等。

`client.EntityRenderDispatcherMixin` 的注解（1.21.1，Mojmap）：

```text
com.llamalad7.mixinextras.injector.v2.WrapWithCondition(
  method = ["render"],
  at = @At(
    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
  )
)
```

YSM 1.20.1（**已下载并按实测复核**：`run/_dist/ysm/ysm-2.6.5-forge+mc1.20.1-release.jar`，sha1 `151ac7b2…af286`，63,269,843 B）
配置 `yes_steve_model.mixins.json`：`required: true`、`plugin: …mixin.plugin.MixinTweaker`、`refmap: yes_steve_model.refmap.json`、client 段 10 项含 `client.EntityRenderDispatcherMixin`、`client.LivingRendererMixin`、`client.BufferSourceMixin`、`client.WorldRendererMixin`。
其 `client.EntityRenderDispatcherMixin` 的注解（Mojmap 名 + refmap 运行期重映射到 SRG）：

```text
com.llamalad7.mixinextras.injector.v2.WrapWithCondition(
  method = ["render"],
  at = @At(
    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
  )
)
```

⇒ **1.20.1 Forge 与 1.21.1 的劫持点完全一致**（同为 dispatcher 层的 `EntityRenderer#render` 调用点），本文 §4 的方案对两条线通用，无需为 Forge 另设注入点。

参考旁证：1.20.1 **fabric** 版（`D:\.minecraft\versions\1.20.1-Fabric 模组测试\mods\`，intermediary 名）结构相同：
`WrapWithCondition(method=["method_3954(...)"], target="Lnet/minecraft/class_897;method_3936(...)")`，另有 `client.PlayerRendererMixin` 往 `PlayerRenderer#method_4215` / `LivingEntityRenderer#method_4054` 注入（YSM 自己的细节，与我们无关——**我们永远不 hook YSM 的类**，它们类名是混淆的）。

YSM 选择 RenderType 的两个类（1.21.1 jar 常量池扫描）：
- `com/elfmcys/yesstevemodel/O0oo0OOo0oOo00OO00oOO0oO` → 引用 `entityTranslucent`
- `com/elfmcys/yesstevemodel/ooo0000oO0O0ooOO0ooOO0o0` → 引用 `entityCutout`

即「按模型元数据在 cutout / translucent 之间二选一」，与 26.1.2 的 `private final boolean[]` 判定同构 ⇒ **我们必须在它选择之后还能改写渲染类型**。

### 2.2 已核对的 API（两条线一致，实读 merged jar）

来源：`neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235-merged.jar`、`forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-merged.jar`

```java
// net.minecraft.client.renderer.entity.EntityRenderDispatcher
public <E extends Entity> void render(E entity, double x, double y, double z,
                                      float entityYaw, float partialTick,
                                      PoseStack poseStack, MultiBufferSource bufferSource, int packedLight);

// net.minecraft.client.renderer.RenderType（两条线方法集相同）
public static RenderType entitySolid(ResourceLocation);
public static RenderType entityCutout(ResourceLocation);
public static RenderType entityCutoutNoCull(ResourceLocation);
public static RenderType entityCutoutNoCull(ResourceLocation, boolean);
public static RenderType entityTranslucent(ResourceLocation);
public static RenderType entityTranslucent(ResourceLocation, boolean);
public static RenderType entityTranslucentCull(ResourceLocation);
```

### 2.3 两条线的现状（实读源码）

`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/mixin/client/SignTranslucencyMixin.java` 与
`forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/client/SignTranslucencyMixin.java`
**内容逐字相同**，唯一差异是 forge 线 `ModEffects.X` 要 `.get()`：

- `getRenderType` HEAD → `RenderType.entityTranslucent(texture)`（对 YSM 无效，路径被劫持）
- `render` HEAD / TAIL → `AlphaContext.push/pop` + `RenderSystem.setShaderColor(1,1,1,alpha)`（对 YSM 无效，方法不执行）
- 内部类 `AlphaContext`（`ArrayDeque<Float>`）**只在本文件内使用**；javadoc 里提到的 `ModelPartAlphaMixin` **并不存在**（陈旧注释，实施时可顺手改正）

---

## 3. 设计原则（从 26.1.2 踩过的坑里提炼，必须遵守）

1. **注入点在 dispatcher 层，不在 renderer 层。**
2. **绝不 gate 在「渲染器类属于 YSM 包」**（26.1.2 实测渲染器就是原版 `AvatarRenderer`）。
3. **共享类不能放在 `…astral_dice.mixin.*` 包**，否则 `IllegalClassLoadError: … is in a defined mixin package … and cannot be referenced directly`，游戏直接起不来。
4. **alpha 只能有一路来源**：`RenderSystem.setShaderColor` 是**赋值**语义，重复设 0.5 不会变成 0.25；但**顶点 alpha 包装 + shader color 同时开就会 0.5×0.5=0.25**（26.1.2 吃过这个亏，最后用 `Math.min(...)` 兜住）。
5. **YSM 缺席必须零影响**：所有 hook 先判「当前实体是不是带效果的玩家」，不是就直接返回。
6. **不把 YSM jar 加进 `build.gradle`**（编译期不需要它，只在运行目录里做验证）。

---

## 4. 推荐补丁方案（三级，按序实施）

### Plan A —— dispatcher 开窗 + RenderType 重定向（首选，改动最小）

**新增共享类（每线一份）** `…/astral_dice/client/ClientAlpha.java`：

```java
package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.effect.ModEffects; // forge 线：ModEffects.X 需 .get()
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** 渲染线程专用的 50% 透明度窗口（与 26.1.2 的 AlphaContext 同构）。 */
public final class ClientAlpha {
    public static final float TRANSLUCENT_ALPHA = 0.5F;
    private static final java.util.ArrayDeque<Float> STACK = new java.util.ArrayDeque<>();

    private ClientAlpha() {}

    public static boolean isTranslucentPlayer(Entity e) {
        return e instanceof Player p && (p.hasEffect(ModEffects.ALANA_IRON_MAIDEN)
                                     || p.hasEffect(ModEffects.DEEPSEEK_FISHING));
    }
    public static void push(float a) { STACK.push(a); }
    public static void pop() { if (!STACK.isEmpty()) STACK.pop(); }
    public static float current() { return STACK.isEmpty() ? 1.0F : STACK.peek(); }
    public static boolean isActive() { return current() < 1.0F; }
}
```

**新增 `mixin/client/DispatcherAlphaMixin.java`**：

```java
@Mixin(EntityRenderDispatcher.class)
public abstract class DispatcherAlphaMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void astral$alphaOn(Entity entity, double x, double y, double z, float yaw, float pt,
                                PoseStack pose, MultiBufferSource buffer, int light, CallbackInfo ci) {
        float a = ClientAlpha.isTranslucentPlayer(entity) ? ClientAlpha.TRANSLUCENT_ALPHA : 1.0F;
        ClientAlpha.push(a);
        if (a < 1.0F) RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, a);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void astral$alphaOff(Entity entity, double x, double y, double z, float yaw, float pt,
                                 PoseStack pose, MultiBufferSource buffer, int light, CallbackInfo ci) {
        try { ClientAlpha.pop(); }
        finally { RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, ClientAlpha.current()); }
    }
}
```

**新增 `mixin/client/RenderTypeAlphaMixin.java`**（把 YSM 选中的 cutout 改写成 translucent）：

```java
@Mixin(RenderType.class)
public abstract class RenderTypeAlphaMixin {
    @Inject(method = "entityCutout", at = @At("HEAD"), cancellable = true)
    private static void astral$cutout(ResourceLocation tex, CallbackInfoReturnable<RenderType> cir) {
        if (ClientAlpha.isActive()) cir.setReturnValue(RenderType.entityTranslucent(tex));
    }
    @Inject(method = "entityCutoutNoCull", at = @At("HEAD"), cancellable = true)
    private static void astral$cutoutNoCull(ResourceLocation tex, CallbackInfoReturnable<RenderType> cir) {
        if (ClientAlpha.isActive()) cir.setReturnValue(RenderType.entityTranslucent(tex));
    }
    @Inject(method = "entitySolid", at = @At("HEAD"), cancellable = true)
    private static void astral$solid(ResourceLocation tex, CallbackInfoReturnable<RenderType> cir) {
        if (ClientAlpha.isActive()) cir.setReturnValue(RenderType.entityTranslucent(tex));
    }
}
```

> ⚠️ 重载注意：`entityCutoutNoCull` 有 `(ResourceLocation)` 与 `(ResourceLocation, boolean)` 两个重载，`entityTranslucent` 同理；两个重载都要处理（`@Inject` 的 `method` 写全描述符更安全）。

**同时改**：把两条线 `SignTranslucencyMixin` 的内部 `AlphaContext` 删掉，改调 `ClientAlpha`（保持 vanilla 行为不变），并把 `render` HEAD 里的 `setShaderColor` 改成 `ClientAlpha.current()` 派生值，避免两处语义分叉。

**新增 mixin 注册**：把 `client.DispatcherAlphaMixin`、`client.RenderTypeAlphaMixin` 加进
- `neoforge-1.21.1/src/main/resources/astral_dice.mixins.json` 的 `client` 数组（现为 6 项）
- `forge-1.20.1/src/main/resources/astral_dice.mixins.json` 的 `client` 数组（现为 6 项，内容相同）

### Plan B —— 包装 `MultiBufferSource`（Plan A 视觉不达标时启用）

若 A 只让模型「变暗」而不是「透出背景」（说明 YSM 的几何不读 `ColorModulator`，或它自己覆盖了 shader color），则在 dispatcher 上再包一层：

```java
// 局部变量槽位（实例方法）：0=this, 1=entity, 2..3=x(double), 4..5=y, 6..7=z,
// 8=yaw(float), 9=partial(float), 10=poseStack, 11=bufferSource, 12=packedLight
@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, index = 11)
private MultiBufferSource astral$wrapBuffer(MultiBufferSource original) {
    return ClientAlpha.isActive() ? new AlphaBufferSource(original, ClientAlpha.current()) : original;
}
```

> `index` 一定按上面的槽位表核对（double 占 2 槽）；不确定时先用 `@Inject` 打印 `Arrays.toString(...)` 确认参数位置，再写 `@ModifyVariable`。

`AlphaBufferSource#getBuffer(RenderType)` 返回 `AlphaVertexConsumer`（`VertexConsumer` 全量委托，只改 `color(int,int,int,int)` 与 `color(int)`：alpha 乘 `ClientAlpha.current()`，ARGB 用 `ARGB.multiplyAlpha`）。

**开关纪律**：启用 Plan B 时**必须关掉 Plan A 的 `setShaderColor`**（否则 0.5×0.5=0.25）。在 `ClientAlpha` 里加一个 `boolean VERTEX_MODE` 常量即可。

### Plan C —— 明确降级（兜底）

A/B 都不成立时：只保证「无 YSM 时行为不变」，把「YSM 玩家在 1.21.1 / 1.20.1 上半透明不生效」写进 `CHANGELOG` 与发布说明，不清空已交付内容。

---

## 5. 实施 checklist（两条线各走一遍）

### 5.1 前置：下载 YSM（两条线各一份，先做这一步）

YSM 在 Modrinth 上，项目 `yes-steve-model`（project id `86xjpqqS`）。**两条线要的 jar 不是同一个文件**：

| 线 | Modrinth 版本 | 文件名 | 大小 | sha1 |
|---|---|---|---|---|
| `neoforge-1.21.1` | [2.6.5-neoforge+mc1.21.1](https://modrinth.com/mod/yes-steve-model/version/2.6.5-neoforge+mc1.21.1) | `ysm-2.6.5-neoforge+mc1.21.1-release.jar` | 63,463,229 B | `18bec74a5b7f3778ce162ddbbbd5555aff0eda2e` |
| `forge-1.20.1` | [2.6.5-forge+mc1.20.1](https://modrinth.com/mod/yes-steve-model/version/2.6.5-forge+mc1.20.1) | `ysm-2.6.5-forge+mc1.20.1-release.jar` | 63,269,843 B | `151ac7b24da8beeca1a20864565743cfd77af286` |

直链（CDN，已实测可下，**慢**，务必断点续传）：

```bash
mkdir -p run/_dist/ysm && cd run/_dist/ysm
curl -sSL --noproxy '*' -C - --max-time 600 -o "ysm-2.6.5-neoforge+mc1.21.1-release.jar" \
  "https://cdn.modrinth.com/data/86xjpqqS/versions/HZWaR0LY/ysm-2.6.5-neoforge%2Bmc1.21.1-release.jar"
curl -sSL --noproxy '*' -C - --max-time 600 -o "ysm-2.6.5-forge+mc1.20.1-release.jar" \
  "https://cdn.modrinth.com/data/86xjpqqS/versions/Zqooxsd2/ysm-2.6.5-forge%2Bmc1.20.1-release.jar"
sha1sum *.jar   # 必须与上表一致
```

> 现成脚本：`run/_dist/ysm/fetch.sh`（带 6 次重试 + 断点续传 + sha1 校验），直接 `bash run/_dist/ysm/fetch.sh`。
> 注意：`curl` 必须带 `--noproxy '*'`（本机系统代理会把 Modrinth CDN 打成 502）；**实测这条 CDN 只有 ~60 KB/s**，单个 63 MB 文件要 15–20 分钟，别用短超时、别重复从头下。
>
> **1.21.1 那份不必下载**：本机 `D:\.minecraft\versions\狐の航空学\mods\[是，史蒂夫模型] ysm-2.6.5-neoforge+mc1.21.1-release.jar`
> 已校验 sha1 = `18bec74a…0eda2e`（与 Modrinth 官方值一致），直接拷即可：
> `cp "D:/.minecraft/versions/狐の航空学/mods/[是，史蒂夫模型] ysm-2.6.5-neoforge+mc1.21.1-release.jar" run/_dist/ysm/`
>
> 当前暂存状态（2026-11-01 23:06，**两份都已就位并通过 sha1 校验**）：
> - `run/_dist/ysm/ysm-2.6.5-neoforge+mc1.21.1-release.jar` — 63,463,229 B，sha1 `18bec74a…0eda2e` ✔（取自本机 `狐の航空学` 实例）
> - `run/_dist/ysm/ysm-2.6.5-forge+mc1.20.1-release.jar` — 63,269,843 B，sha1 `151ac7b2…af286` ✔（断点续传两次下完）
> 复跑校验：`cd run/_dist/ysm && sha1sum *.jar`。

放置（**不要**把 fabric 版放进 forge 线；fabric jar 只作渲染路径参考）：

```bash
cp "run/_dist/ysm/ysm-2.6.5-neoforge+mc1.21.1-release.jar" run/1.21.1/mods/
cp "run/_dist/ysm/ysm-2.6.5-forge+mc1.20.1-release.jar"     run/1.20.1/mods/
```

**劫持点已复核完成（可以跳过重测，但保留命令以便回归）**：1.20.1 Forge 版与 1.21.1 一致（`method=["render"]` + `target=EntityRenderer#render(...)V`，见 §2.1）。
若怀疑版本差异（换过 YSM 版本时），用下面的命令重跑一遍：

```bash
PY="C:/Users/xmace/.dsh/dsh-runtimes/dsh-primary-runtime/dependencies/python/python.exe"
"$PY" - <<'PY'
import zipfile, os
p = r"run/_dist/ysm/ysm-2.6.5-forge+mc1.20.1-release.jar"
z = zipfile.ZipFile(p); out = r"run/26.1.2/.tmp/ysm120forge"; os.makedirs(out, exist_ok=True)
for n in z.namelist():
    if n.endswith(".mixins.json"):
        print("==", n); print(z.read(n).decode("utf-8")[:800])
    if n.startswith("com/elfmcys/yesstevemodel/mixin/client/") and "EntityRenderDispatcher" in n:
        open(os.path.join(out, os.path.basename(n)), "wb").write(z.read(n)); print("extracted", n)
PY
"/c/Program Files/Zulu/zulu-25/bin/javap.exe" -v -p run/26.1.2/.tmp/ysm120forge/EntityRenderDispatcherMixin.class \
  | grep -E "WrapWithCondition|method=|target=" | head -8
```

若 1.20.1 Forge 版的劫持点与预期**不同**（例如改挂 `PlayerRenderer#render` 而不是 dispatcher），
先按实测结果调整 §4 的注入点，再动代码——**不要假设**，26.1.2 上就是靠 `javap` 才发现原设计前提是错的。

### 5.2 代码（两线同构）

1. 新建 `…/astral_dice/client/ClientAlpha.java`（§4 代码，注意 forge 线 `.get()`）
2. 新建 `…/mixin/client/DispatcherAlphaMixin.java`
3. 新建 `…/mixin/client/RenderTypeAlphaMixin.java`
4. 改 `SignTranslucencyMixin.java`：删内部类 `AlphaContext` → 引用 `ClientAlpha`；顺手修正 javadoc 里不存在的 `ModelPartAlphaMixin` 说法
5. 两个 `astral_dice.mixins.json` 的 `client` 数组各加 2 项
6. 断言：`grep -rn "AlphaContext" <line>/src/main/java` 应只剩 `ClientAlpha` 相关；`…mixin.*` 包内不得有被外部引用的普通类

### 5.3 构建

```bash
# 1.21.1
cmd //c "gradlew.bat :neoforge-1.21.1:jar"
# 1.20.1（部署必须用 devlibs！build/libs 是 reobf，会 NoSuchFieldError: f_256929_）
cmd //c "gradlew.bat :forge-1.20.1:jar"
```

产物：`neoforge-1.21.1/build/libs/*.jar`、`forge-1.20.1/build/devlibs/*.jar`

### 5.4 部署

```bash
cp neoforge-1.21.1/build/libs/astral_dice-*.jar run/1.21.1/mods/
cp forge-1.20.1/build/devlibs/astral_dice-*.jar  run/1.20.1/mods/
```

### 5.5 验证（关键：先判 dev 形态能不能跑 YSM）

1. 先用 dev client 跑一次（`mt.ps1 --phase env|run` 或 `gradlew :<line>:runClient`），看有没有
   `[YSM] Unsatisfied runtime environment: err: 54`。
2. **若出现 err:54**：这是 YSM 对 dev/userdev 形态的拒绝（26.1.2 已定性，与 OS、JVM 厂商、权限无关）。
   改用 **production 形态**启动：`run/26.1.2/.tmp/prodlaunch.py` 已经是通用实现，只需把 manifest 换成
   `D:\.minecraft\versions\狐の航空学\狐の航空学.json`（1.21.1）或 `…\1.20.1-Forge 模组测试\<同名>.json`（1.20.1）。
3. 进世界后按 26.1.2 的同一套判据做 A/B：
   - 第三人称、`tp` 到固定坐标与朝向、同一世界
   - `effect clear @a` → 截图 off；`effect give <player> astral_dice:alana_iron_maiden 300 0` → 截图 on
   - 像素判据：模型 bbox 内 `diff>60` 占比 **> 5%** 且 luma 下降（26.1.2 实测 10.31%）
   - 截图落 `run/<ver>/screenshots/`，命令：`pwsh -NoProfile -File scripts/test/mt_capture.ps1 capture -Version <ver> -Tag <tag> -Mode window`
4. **回归**：把 YSM 从 mods 移走再跑一遍 off/on —— 两组必须与改动前**逐像素一致**（无 YSM 时零影响）。

### 5.6 验收（Definition of Done）

- [ ] 两线 `jar` 构建通过，Mixin 无 refmap / IllegalClassLoadError
- [ ] 有 YSM：效果开 → 50% 半透明（透出背景）；效果关 → 完全恢复不透明，alpha 不泄漏到其它实体
- [ ] 无 YSM：与改动前逐像素一致
- [ ] 每线 4 张证据图 + `diff>60` 百分比写进 `docs/features/ysm-compat-err54-and-verification.md` 的同构小节
- [ ] 未改动 `neoforge-26.1.2` 与 `fabric-1.20.1` 的任何文件

### 5.7 两线同步纪律（**一个改动集，两条线一起落地**）

两条线的相关 API **逐字节相同**（§2.2 已实读核对：`EntityRenderDispatcher#render` 九参签名、`RenderType` 七个工厂方法），
现存 `SignTranslucencyMixin` 也**逐字相同**（唯一差异是 forge 线 `ModEffects.X` 要 `.get()`）。
所以这是「一份改动、两个落点」，**禁止**先只做 1.21.1、隔一阵再做 1.20.1 —— 分两批必然漂移，且下次维护要重新对账。

**同步步骤**

| # | 动作 | 判据 |
|---|---|---|
| 0 | 两份 YSM jar 到位（§5.1） | `sha1sum` 与表中一致；`run/1.21.1/mods`、`run/1.20.1/mods` 各有一份 |
| 1 | 两条线写同一套文件：`client/ClientAlpha.java`、`mixin/client/DispatcherAlphaMixin.java`、`mixin/client/RenderTypeAlphaMixin.java`、改 `SignTranslucencyMixin.java`、两个 `astral_dice.mixins.json` 的 `client` 数组 | 五个文件 × 两线都在 |
| 2 | **同步断言**（关键，防手工漂移） | 见下方命令，输出为空 |
| 3 | 两线各构建一次 | `:neoforge-1.21.1:jar`、`:forge-1.20.1:jar` 都 BUILD SUCCESSFUL |
| 4 | 分别部署（forge 用 `build/devlibs`） | 落盘 sha256 记录在案 |
| 5 | 两条线各自验收（§5.5） | 每线都要有 on/off 两张图 + 数值 |
| 6 | 结果落表 | 见下方模板 |

**同步断言命令**（forge 线把 `.get()` 去掉后，应与 1.21.1 线逐字相同）：

```bash
cd /d/MCProject/astral_dice_multiloader
for f in client/ClientAlpha.java mixin/client/DispatcherAlphaMixin.java mixin/client/RenderTypeAlphaMixin.java; do
  echo "== $f"
  diff <(sed 's/\.get()//g' "forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/$f") \
       "neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/$f" && echo "   SYNCED"
done
# mixin 注册数组也要一致（两边都是 8 项，含新加的两项）
grep -A2 '"client"' neoforge-1.21.1/src/main/resources/astral_dice.mixins.json forge-1.20.1/src/main/resources/astral_dice.mixins.json
```

**结果表模板**（填完贴进 `docs/features/ysm-compat-err54-and-verification.md`）：

| 线 | YSM | 效果 | 截图 | `diff>60` | luma 变化 | 结论 |
|---|---|---|---|---|---|---|
| neoforge-1.21.1 | 有 | off | `run/1.21.1/screenshots/ysm_off_*.png` | — | — | 不透明 |
| neoforge-1.21.1 | 有 | on | `run/1.21.1/screenshots/ysm_on_*.png` | 待填 % | 待填 | 半透明 |
| neoforge-1.21.1 | 无 | off/on | 同上两份对照 | ≈0 % | ≈0 | 零影响 |
| forge-1.20.1 | 有 | off | `run/1.20.1/screenshots/ysm_off_*.png` | — | — | 不透明 |
| forge-1.20.1 | 有 | on | `run/1.20.1/screenshots/ysm_on_*.png` | 待填 % | 待填 | 半透明 |
| forge-1.20.1 | 无 | off/on | 同上两份对照 | ≈0 % | ≈0 | 零影响 |

**失败处理**：若某条线 Plan A 只「变暗」不透背景，**只对该线**启 Plan B（顶点 alpha 包装），并在文档里标注「本线用 B、另一线用 A」的差异与原因；
**不允许**因为一条线不达标就把另一条线回退成未修改状态。

---

## 6. 已知坑（都是 26.1.2 上真踩过的，直接照抄结论）

| 坑 | 现象 | 结论 |
|---|---|---|
| 拿渲染器类判断 YSM | 探针打出 `renderer=…player.AvatarRenderer` | 不要 gate 渲染器类 |
| 共享类放 mixin 包 | `IllegalClassLoadError … cannot be referenced directly`，游戏起不来 | 共享类放 `client/render` 或 `client` 包 |
| alpha 二次相乘 | 模型只有 25% 不透明度 | 只用一路 alpha，或用 `Math.min` |
| `JAVA_TOOL_OPTIONS` 调 debug | Gradle daemon 也被注入，构建静默卡死/无日志 | 调试开关用 `_JAVA_OPTIONS` |
| forge 线部署 reobf 产物 | `NoSuchFieldError: f_256929_` | 部署 `build/devlibs` |
| dev 形态跑 YSM | `err: 54` | 换 production 形态（见 §5.5） |

---

## 7. 参考

- 已交付与定性：`docs/features/ysm-compat-err54-and-verification.md`
- YSM 发行件（已暂存、sha1 已核）：`run/_dist/ysm/`（`ysm-2.6.5-neoforge+mc1.21.1-release.jar`、`ysm-2.6.5-forge+mc1.20.1-release.jar`）+ `fetch.sh`（断点续传 + 校验，换版本时复用）
- 26.1.2 实现（作为对照，**不要复制**）：`neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/mixin/client/ysm/`、`client/render/AlphaContext.java`、`client/render/ysm/`
- 26.1.2 的 mixin 配置：`neoforge-26.1.2/src/main/resources/astral_dice.ysm.mixins.json`（独立配置 + `IMixinConfigPlugin` 闸门；1.21.1/1.20.1 **不需要**独立配置，直接进主 `astral_dice.mixins.json` 即可，因为没有新增「YSM 专属注入点」）
