# YSM 兼容层：`err: 54` 成因与半透明验证记录（26.1.2）

> 范围：**只涉及 `neoforge-26.1.2` 线与 `run/26.1.2/` 验证现场**。fabric 线本批不碰。
> 记录时间：2026-11-01。所有结论都有命令/日志证据，证据文件路径见各段。

## 1. 结论速览

| 问题 | 结论 |
|---|---|
| `[YSM] Unsatisfied runtime environment: err: 54` | **不是环境缺陷，是 YSM 原生库对「开发期启动」的主动拒绝**（dev/userdev 形态专属） |
| Linux 上同样的 `err: 54` | 同一原因（Linux 只能跑 userdev 形态），与 OS 无关 |
| 换 JVM（Zulu 21/25、Microsoft OpenJDK 25）能否绕过 | **不能**（官方 wiki 说「err 54 ⇒ 换 JVM」，在本机被实测证伪） |
| 低完整性级别（Low IL）沙箱是否是元凶 | **不是**（用户在正常终端跑同样的加载器，同样失败） |
| 需要脱壳 VMProtect 吗 | **不需要**：改用 production 形态启动即可，YSM 正常加载 |
| 半透明兼容层是否生效 | **生效**（production 形态 A/B 实测，模型区 10.31% 像素变化，见 §4） |

## 2. `err: 54` 的定位过程（可复用）

### 2.1 err 码语义（裸 JVM 矩阵）

`LoadDll.java`（临时加载器，`System.load` 直接吃 `META-INF/native/ysm-core.dll`）：

| 启动参数 | 结果 |
|---|---|
| 无 | `java.lang.RuntimeException: err: 56` |
| `-Dnet.minecraftforge.userdev=true` / `=1` / `=false` | **`err: 54`**（**只判属性是否存在**，不看值） |
| `-Duserdev` / `-Dfml.userdev` / `-Dneoforge.userdev` | `err: 56` |
| `-Dfml.modFolders=…` / `-DlegacyClassPath=…` | `err: 56` |

⇒ `54` = 「检测到 dev/userdev」，`56` = 更早的「宿主不可识别」。裸 JVM 缺 FML 类，永远先撞 56，
所以**裸 JVM 矩阵对「游戏内为什么 54」判别力有限**。

### 2.2 游戏内证据（dev 客户端）

- `ASTRAL_YSM_PROBE` 四模式探针（log / clear / diag / spoof）全部无效：属性 `net.minecraftforge.userdev`
  在 dev 客户端里**本来就不存在**，清空四个属性、把 `java.class.path` 与 `sun.java.command`
  伪装成 production 值，仍然是 `err: 54` ⇒ **判定不读 Java 层可见属性**。
- diag 输出的 dev 特征（可作为「dev 形态」判据参考）：`java.class.path` 含
  `…\neoforge-26.1.2\build\classes\java\main`、`…\build\moddev\artifacts\minecraft-patched-26.1.2.109.jar`、
  `net.neoforged\DevLaunch\1.0.2\…`；`sun.java.command = net.neoforged.devlaunch.Main @…`。
- 该探针代码已从 `AstralDiceMod.java` **删除**（定版不带测试钩子）。

### 2.3 反调试（脱壳尝试的副产物，已归档不续）

- frida attach 会让进程立刻 `__fastfail`（`exit value -1073740791` / NTSTATUS `0xC0000409`），
  且只在 `JNI_OnLoad` 期间；对已启动完成的进程 attach 则正常 ⇒ 壳有反调试。
- 同进程 `-agentpath` 原生 agent **不被拒**：`rustc --crate-type cdylib` 写的 agent 成功 dump 出
  已解壳镜像（41,267,200 B，`YSMS0/1/2` 节），产物与分析脚本留在 `run/26.1.2/.tmp/{rprobe,dumps}/`。
- 对已解壳代码段做 inline hook 会被完整性校验打死（游戏卡死在启动画面）⇒ 内存补丁路线不可行。
- 镜像里 `err: ` 格式串在 `0xcbaa58`，唯一 RIP 相对引用在 `0x2446c4`（函数体起 `0x2446b0`）；
  `net.minecraftforge.userdev` 在 late dump 里**无静态引用**。
- **这些工作因 §3 的结论已无必要**，仅作为档案保留。

## 3. 正确做法：production 形态启动（验证 YSM 的唯一可用通道）

引导器：`run/26.1.2/.tmp/prodlaunch.py`

```
python prodlaunch.py [--dry-run] [--quickplay testworld] --log ./x.log
```

- 读 `D:\.minecraft\versions\26.1.2-NeoForge 模组测试\26.1.2-NeoForge 模组测试.json`（合并版 manifest，
  133 libraries），按 `downloads.artifact.path` 拼 classpath（实测 102 条，missing=0）。
- natives 复用实例自带 `…-natives\`；assets 指向 `D:\.minecraft\assets`；
  java = `C:\Program Files\Zulu\zulu-25\bin\java.exe`；gameDir = **一次性目录** `run/26.1.2/prodgame`。
- env：`TEMP=TMP=TMPDIR=run/26.1.2/.tmp`，并 `pop("JAVA_TOOL_OPTIONS")`；`creationflags=0x200`（detached）。
- `--quickplay testworld` 等价 `--quickPlaySingleplayer=testworld`（**必须等号**，空格分隔会静默停在主菜单）。

实测结果（`run/26.1.2/.tmp/prod-final.log`）：

- `Total time to load game and open world was …`、`DevTester[local:E:…] logged in with entity id …`
- YSM 无 `Failed to load native lib` / `err: 5x` / `Unsatisfied runtime environment`；
  `prodgame/config/yes_steve_model/builtin/` 解出 63 MB 默认模型 ⇒ 原生核心真正跑起来。
- AutoJS 在生产实例同样可用（`http://127.0.0.1:25585/mcp`，server/client 双侧 available）。

**注意**：`JAVA_TOOL_OPTIONS` 会同时注入 Gradle launcher 与 daemon；任何会阻塞/崩溃的 agent 逻辑
都会静默搞死整个构建（日志只有两行、无 hs_err）。游戏侧实验一律绕开 Gradle。

## 4. 兼容层：实现与两处实测修正

设计（`astral_dice.ysm.mixins.json`，`required:false`，`AstralYsmMixinPlugin` 以
`yes_steve_model` 是否加载为闸门）——理由：三处接缝全是**原版**类，YSM 缺席时天天命中，
必须整组摘掉，否则 alpha 会二次相乘。

三个 mixin 与两个修正：

1. `YsmEntityRenderDispatcherMixin`（`EntityRenderDispatcher#submit` HEAD/RETURN 开窗）
   - ❌ 原判据含「该实体的渲染器类属于 `com.elfmcys.yesstevemodel` 包」。
   - ✅ **实测证伪**：26.1.2 下 YSM 玩家的渲染器就是原版
     `net.minecraft.client.renderer.entity.player.AvatarRenderer`（YSM 只换 model 并自己下发几何）。
     该判据已删除，alpha 仅由 render state 的半透明标记决定；二次相乘改由「取较小值」兜住。
2. `YsmGeometryMixin`（`SubmitNodeCollection#submitCustomGeometry` 的 `CustomGeometryRenderer` 包一层）
   - ✅ `alpha = Math.min(YsmRenderContext.current(), AlphaContext.current())` —— 两套窗口互不叠加，
     0.5×0.5=0.25 的 bug 结构性消失。
3. `YsmRenderTypeMixin`（`RenderTypes#entityCutout` → `entityTranslucent`）
   - ✅ 窗口生效「或」原版路径 alpha<1 时接管（同样的 min 语义）。理由：`ENTITY_CUTOUT` 管线没有 blend state。

**踩坑（必须记住）**：`AlphaContext` 原先是 `SignTranslucencyMixin` 的内部类，即落在
`com.merlinkitsune.astral_dice.mixin.*` 包 —— mixin 专属包里的类**不允许被其它类直接引用**，
会抛 `IllegalClassLoadError: … is in a defined mixin package … cannot be referenced directly`。
已移出为 `client/render/AlphaContext.java`（与 `client/render/TranslucentRenderState.java` 同一处理）。

## 5. 定版与 A/B 证据

- 产物：`neoforge-26.1.2/build/libs/astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar`
  - 2,875,013 B，`sha256 6b278f070dcd5f982028151e066a0da33fb52a98987a9879d08fe5966f0c49d1`
- 部署：`run/26.1.2/mods/`（旧包备份 `…jar.old-20261101-2240.bak`）与 `run/26.1.2/prodgame/mods/`
  （两处 sha256 一致）
- 生产形态 A/B（同世界、玩家 `tp 0 -60 0 0 0`、第三人称、同一 yaw）：

| 条件 | 结果 |
|---|---|
| `effect clear @a` | 模型完全不透明 |
| `effect give DevTester astral_dice:alana_iron_maiden 300 0` | 模型 50% 半透明（草地/箱子透过模型可见） |

像素判据（模型 bbox 167×213）：`diff>60` = **3668 / 35571（10.31%）**，区域 luma `118.04 → 116.41`。

证据图：

- `run/26.1.2/screenshots/ysm_final_effect_off.png`（不透明基线）
- `run/26.1.2/screenshots/ysm_final_effect_on.png`（半透明生效）

## 6. 未收口的现场（需要时再清）

| 路径 | 内容 | 建议 |
|---|---|---|
| `run/26.1.2/.tmp/prodlaunch.py` | production 形态引导器 | **保留**（后续验证都靠它） |
| `run/26.1.2/.tmp/dumps/`、`rprobe/` | 加壳镜像 dump、PE 工具、agent 源码 | 可归档或删（脱壳已无必要） |
| `run/26.1.2/.tmp/frida-pkg/`、`capstone-venv/`、`jdk/` | frida 17.23.2（135 MB binding）、capstone、MS OpenJDK 25（约 500 MB） | 可删 |
| `run/26.1.2/prodgame/` | 一次性生产形态实例（含 saves/testworld 副本） | 可删（要复跑就重建） |
| `run/26.1.2/mods/*.bak` | 历代 jar 备份 | 保留最近两代即可 |

## 7. 一句话交接

> YSM 在 dev 形态下必然 `err: 54`（壳主动拒绝），**别再去脱壳/换 JVM**；
> 要验证 YSM 相关渲染，用 `run/26.1.2/.tmp/prodlaunch.py` 起 production 形态实例，
> 兼容层（三个 ysm mixin）在那种形态下已验证半透明生效。

## 8. 1.21.1 / 1.20.1 双线补齐：设计与静态验证证据

> 范围：`neoforge-1.21.1` 与 `forge-1.20.1` 两条线（下文简称「两线」）；`neoforge-26.1.2` 与 `fabric-1.20.1` 本批不动。
> 记录时间：2026-11-01。本轮**只到静态验证**（构建 + 产物/字节码取证），**实机 A/B 未执行**，占位见 §8.7。

### 8.1 需求与判据

需求（与 26.1.2 同口径）：

- 装了 YSM 的玩家，持有 `astral_dice:alana_iron_maiden` 或 `astral_dice:deepseek_fishing` 效果时，玩家模型渲染为 **50% 半透明**；
- 无这两个效果 ⇒ 保持不透明，外观与未打补丁一致；
- YSM 缺席（对局里根本没装）⇒ 行为不变，且是**零写入**，不是「写了又被还原」。

像素判据（沿用 26.1.2 已定的口径）：模型 bbox 内 `diff > 60` 的像素占比 **> 5%**，且区域 luma **下降**。
26.1.2 的实测基线供对照：`3668 / 35571 = 10.31%`，luma `118.04 → 116.41`。

### 8.2 为什么不照搬 26.1.2 的两段式实现

| 维度 | 26.1.2 | 1.21.1 / 1.20.1 |
|---|---|---|
| 渲染管线 | 两段式：`extractRenderState` → `submit(state, PoseStack, SubmitNodeCollector, CameraRenderState)` | 即时模式：`render(entity, yaw, partial, PoseStack, MultiBufferSource, light)` |
| 几何下发 | `SubmitNodeCollector#submitCustomGeometry` | `MultiBufferSource#getBuffer(RenderType)` |
| 可复用代码 | — | **零**（除判据口径与 `0.5F` 常量） |

⇒ 两条管线的注入面、可改写对象、乃至「有没有单独的几何提交阶段」都不同：26.1.2 的 `YsmGeometryMixin`（包 `submitCustomGeometry`）在两线上**没有对应物**，只能按即时模式的接缝重写。

**跨版本不变的事实（本轮结论的承重墙）**：YSM 在**所有版本**都用 `@WrapWithCondition` 挂在 `EntityRenderDispatcher#render` 对 `EntityRenderer#render` 的**调用点**上取消原版调用。因此**挂在 `LivingEntityRenderer` 上的注入点对 YSM 玩家是死代码** —— 必须挂 dispatcher 层。

- 取证（独立审查员本轮亲自拆包复核）：`run/_dist/ysm/ysm-2.6.5-forge+mc1.20.1-release.jar` 内 `com/elfmcys/yesstevemodel/mixin/client/EntityRenderDispatcherMixin` 的 `@WrapWithCondition` 目标是 `INVOKE "…/EntityRenderer;render(…)V"`；`EntityRenderDispatcher#render` 本体照常执行（不是整体取消）。
- 推论：YSM 自己会调 `RenderType.m_110458_`（= `entityCutoutNoCull(RL)`）并命中缓存 `m_110473_`（= `entityTranslucent`）⇒ 半透明**必须换 RenderType 开混合**；只压 shader color 的 alpha 只会让模型「变暗」，不会透。

### 8.3 实现：每线 5 个文件（两线同构）

两线路径只差首段目录（`neoforge-1.21.1/` 与 `forge-1.20.1/`），其余完全一致：

1. `<线>/src/main/java/com/merlinkitsune/astral_dice/client/ClientAlpha.java`（**新建**，58 行）
   - 静态 `ArrayDeque<Float>` 栈 + `TRANSLUCENT_ALPHA = 0.5F` + `MAX_DEPTH = 64` 自愈（超深即丢弃，防异常路径无限增长）。
   - `isTranslucentPlayer(Entity)`：判 `Player` 且 `hasEffect` 两个效果之一（forge 线是 `ModEffects.X.get()`）。
   - **必须放 `…astral_dice.client` 包**：放 `…astral_dice.mixin.*` 会 `IllegalClassLoadError`（mixin 专属包内的类不允许被直接引用，同 26.1.2 踩过的坑）。
2. `<线>/…/mixin/client/DispatcherAlphaMixin.java`（**新建**，52 行）
   - `@Mixin(EntityRenderDispatcher.class)`；`render` 的 `HEAD` 压栈（`alpha < 1` 才写 shader color），`RETURN` 弹栈（**只有开过窗才还原颜色** ⇒ YSM 缺席时零写入）。
3. `<线>/…/mixin/client/RenderTypeAlphaMixin.java`（**新建**，86 行）
   - `@Mixin(RenderType.class)`，6 个 `@Inject(at = @At("HEAD"), cancellable = true)`，把 `entityCutout(RL)`、`entityCutoutNoCull(RL)`、`entityCutoutNoCull(RL,Z)`、`entityCutoutNoCullZOffset(RL)`、`entityCutoutNoCullZOffset(RL,Z)`、`entitySolid(RL)` 在窗口内改写成 `entityTranslucent`。
   - `method` **必须写全描述符**：同名重载（`entityCutoutNoCull(RL)` 与 `entityCutoutNoCull(RL,Z)`）只靠末尾 `Z` 区分。
   - **不写 `remap = false`**：目标全是原版成员，forge 靠 refmap 映射到 SRG。
4. `<线>/…/mixin/client/SignTranslucencyMixin.java`（**改动**，72 行）
   - 删掉嵌套的 `AlphaContext` 内部类，改用 `ClientAlpha`（嵌套类落在 mixin 包里同样不可被外部引用）。
   - 判定改为委托 `ClientAlpha.isTranslucentPlayer` —— 消掉两处各写一份的重复判定。
   - **弹栈从 `@At("TAIL")` 改成 `@At("RETURN")`**：`LivingEntityRenderer#render` 有**两个**返回点（neo `33: return` 受 `RenderLivingEvent` 闸门 `ifeq` 门控 + `733: return`；forge `27:` + `702:`）。`TAIL` 只覆盖方法尾，早退时那一栈项不被弹出 ⇒ 0.5 **永久泄漏在栈底**，于是 `RenderTypeAlphaMixin` 的全局改写被永久打开（整局不自愈）。`RETURN` 在每个返回点前都执行，路径对称。
5. `<线>/src/main/resources/astral_dice.mixins.json`（**改动**）
   - `client` 数组 6 → 8，新增 `client.DispatcherAlphaMixin`、`client.RenderTypeAlphaMixin`。
   - neo 线无 refmap 键；forge 线保留 `"refmap": "astral_dice.refmap.json"`。

### 8.4 静态验证证据（本轮实跑，逐条）

1. **两线构建**：`:neoforge-1.21.1:jar` / `:forge-1.20.1:jar` 均 BUILD SUCCESSFUL；日志无 `InjectionError`、无 `Multiple candidates`、无 `Unable to locate obfuscation mapping`。
2. **注入签名唯一性**：`EntityRenderDispatcher#render` 在两份 merged jar 中**唯一重载**，描述符 `(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V`；6 个 RenderType 目标的 nameHits / exact 全为 **1**（靠全描述符消歧，不靠 `require` 猜）。
3. **forge refmap 9/9 对账**：对 `forge-1.20.1/build/moddev/artifacts/namedToIntermediate.tsrg`（7436 类）按「named 名 + 描述符」查候选，要求 SRG 全一致 —— 6 条 `entity*` + `dispatcher.render`（`m_114384_`）+ 既有 2 条（`getRenderType` → `m_7225_`、`render` → `m_7392_`），每条 `cands = 1`。
   - 顺带确认改名映射：`entityTranslucent` = `m_110473_`(RL) / `m_110454_`(RL,Z)。
   - forge 产物内注解仍是 **Mojmap 全描述符**、`remap=` 出现 **0** 次、方法体已 reobf 成 `invokestatic RenderType.m_110473_`（说明 refmap 生效，且没写成 `remap = false` 这种「跳过重映射」的错法）。
4. **两线逐字节同步**：`DispatcherAlphaMixin` / `RenderTypeAlphaMixin` / `SignTranslucencyMixin` 三件两线 sha256 **完全相同**；`ClientAlpha` 仅 `.get()` 两处差异（去 `.get()` 后逐字节相等，len 2140；带 `.get()` 的 forge 版 2152 B）。
5. **产物**：两线 jar 内均有 `ClientAlpha.class` / `DispatcherAlphaMixin.class` / `RenderTypeAlphaMixin.class`；`SignTranslucencyMixin$AlphaContext.class` **不存在**（嵌套类确已删除）。产物内 class 的注入点是 `HEAD × 1 + RETURN × 1`，`TAIL` **0 次**。
6. **无 0.25 双路 alpha**：实体帧内 `setShaderColor` 仅 2 个写点且全为绝对赋值；`multiplyAlpha` **0 命中**、`mixin/client` 内 `VertexConsumer` **0 命中** ⇒ 不存在 YSM 自带 alpha 与本层 alpha 相乘的路径。
7. **越界面**：改动仅两线共 **10 个文件**（5 × 2）；`neoforge-26.1.2/`、`fabric-1.20.1/`、`models/`、`images/`、`sound/` 无本轮条目。

### 8.5 已知取舍（**是有意的，不是 bug**）

1. `entityCutout` / `entitySolid` 原本是 CULL + 深度写；映射到 NO_CULL 的 `entityTranslucent` 后，会开始画背面，半透明重叠处颜色加重 —— 与 YSM 自身的选择对齐。
2. `entityCutoutNoCullZOffset` 映射会**丢掉 Z 偏移**；原版唯一使用者 `SkullBlockRenderer` 不在实体渲染窗口内，本轮不受影响。
3. 窗口粒度是「实体渲染期间」而非「只改目标实体」，因此带效果玩家的披风 / 耳朵等外层也会进半透明族 —— 预期行为（整个模型一起透明）。
4. 目标方法抛异常时 `RETURN` 不执行，会泄漏 1 条栈项；`MAX_DEPTH = 64` 只防无限增长，不负责清空，判为低概率设计取舍。
5. 需求写的是「持有…效果」，实现判 `hasEffect`（与 26.1.2 已交付口径一致）；效果由立牌施放（`AlanaSignItem` 右键 / `DeepseekSignItem` 静止满 `IDLE_TICKS`），与「手持物品」**不等价**。

### 8.6 与 §1–§7（26.1.2）记录的差异（本节不改原文，供人工裁定）

1. **标题与范围声明已滞后**：§1 标题为「（26.1.2）」、§3 开头写「只涉及 `neoforge-26.1.2` 线与 `run/26.1.2/` 验证现场」。本轮把 1.21.1 / 1.20.1 补进来后，这两句话的范围声明不再覆盖全文（是否改标题 / 加范围括注，待定）。
2. **闸门策略两套并存**：§4 记录 26.1.2 用 `astral_dice.ysm.mixins.json`（`required: false` + `AstralYsmMixinPlugin` 以 `yes_steve_model` 是否加载为闸门），理由是「三处接缝全是原版类、YSM 缺席时天天命中，必须整组摘掉」。两线没有该 plugin / 该 json，`RenderTypeAlphaMixin` 同样挂原版 `RenderType`，改用**运行时窗口**隔离（无窗口即零写入）。两种口径是否统一，待定。
3. **同类不同名 / 不同位置**：26.1.2 的 alpha 上下文是 `client/render/AlphaContext.java`；两线是 `client/ClientAlpha.java`（并承担了 `isTranslucentPlayer`）。跨线阅读时不要按名字对照。
4. §5 的 A/B 结论（`10.31%` / luma `118.04 → 116.41`）**只对 26.1.2 成立**；§1 速览行「半透明兼容层是否生效 → 生效」现在读来像全线结论，而两线实机尚未验证。

### 8.7 待补：实机 A/B（**未执行**）

| 线 | 条件 | 截图路径 | `diff > 60` 占比 | luma 变化 | 结论 |
|---|---|---|---|---|---|
| 1.21.1 | 有 YSM · off（`effect clear @a`） | 待实测 | 待实测 | 待实测 | 待实测 |
| 1.21.1 | 有 YSM · on（`effect give …`） | 待实测 | 待实测 | 待实测 | 待实测 |
| 1.21.1 | 无 YSM · off | 待实测 | 待实测 | 待实测 | 待实测 |
| 1.21.1 | 无 YSM · on | 待实测 | 待实测 | 待实测 | 待实测 |
| 1.20.1 | 有 YSM · off（`effect clear @a`） | 待实测 | 待实测 | 待实测 | 待实测 |
| 1.20.1 | 有 YSM · on（`effect give …`） | 待实测 | 待实测 | 待实测 | 待实测 |
| 1.20.1 | 无 YSM · off | 待实测 | 待实测 | 待实测 | 待实测 |
| 1.20.1 | 无 YSM · on | 待实测 | 待实测 | 待实测 | 待实测 |

**未执行**：实机验证尚未做，需用户授权后按 26.1.2 同一套判据执行 —— 第三人称固定坐标 `tp`、`effect clear @a` 截 off、`effect give …` 截 on、截图落 `run/1.21.1/screenshots/` 与 `run/1.20.1/screenshots/`（两目录均已存在）、命令 `pwsh -NoProfile -File scripts/test/mt_capture.ps1 capture -Version <ver> -Tag <tag> -Mode window`（`<ver>` 取 `1.21.1` / `1.20.1`）。

dev 形态可能撞 YSM `err: 54`（§2）；届时改用 production 形态，参考 `run/26.1.2/.tmp/prodlaunch.py` 与 `D:\.minecraft\versions\狐の航空学\狐の航空学.json` / `1.20.1-Forge 模组测试` 的 manifest。
