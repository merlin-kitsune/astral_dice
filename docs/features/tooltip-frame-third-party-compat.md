# tooltip 边框第三方兼容（Modern UI / JEI 查看器 / 其它接管渲染的模组）

> 适用：`astral_dice` 五条自包含线里的 **fabric 两条**（`fabric-1.20.1` / `fabric-1.21.1`）。
> NeoForge 线走官方 `RenderTooltipEvent.Color`，Forge 线走自绘 `TooltipRenderUtil` 覆盖，不在本文范围。
> 本文是 2026-11-07 两次崩溃 + 一次定论的**可复现取证记录**，不是设计文档。

## 1. 现象

| 场景 | 修复前 | 修复后 |
|---|---|---|
| 背包 / 容器里悬停物品 | 正常（档位色） | 正常 |
| **JEI 面板**里悬停物品 | 退回原版紫蓝框（不跟档位） | 跟档位 |
| 装了 **Modern UI** 且开「现代提示框」 | 换成第二个物品时颜色**滞留**上一个（背包与 JEI 都可能） | 跟随 |

判据不是「看起来好看」，而是：**边框颜色必须由物品稀有度档位决定，且换物品必须立刻重算**。

## 2. fabric 侧本来就有的基础设施

fabric 既没有 `RenderTooltipEvent.Color`，也没有 1.21.1 NeoForge 那种「带 ItemStack 的 tooltip 入口」，
所以本仓自建了一条栈 + 自绘的桥：

| 组件 | 位置 | 职责 |
|---|---|---|
| `TooltipFrameColors` | `<line>/src/main/java/com/merlinkitsune/astral_dice/platform/client/TooltipFrameColors.java` | `ThreadLocal` 栈：谁在画 tooltip 就把 `ItemStack` 压进去，下游用 `peek()` 取当前物品 |
| `ClientTooltipBridgeMixin` | `.../mixin/bridge/ClientTooltipBridgeMixin.java` | 从原版 `GuiGraphics#renderTooltip(Font, ItemStack, int, int)` 压栈 / 弹出 |
| `ContainerTooltipBridgeMixin` | `.../mixin/bridge/ContainerTooltipBridgeMixin.java` | 容器界面 `ContainerScreen#renderTooltip` 压栈 / 弹出 |
| `TooltipRenderUtilColorMixin` | `.../mixin/bridge/TooltipRenderUtilColorMixin.java` | 替换 `TooltipRenderUtil` 的底色 / 边框色查询为「按档位取色」 |
| `TooltipBackgroundSpriteMixin` | `.../mixin/bridge/TooltipBackgroundSpriteMixin.java` | 1.21.1 线：把逐条 `fill` 的直角双线框换成 **100×100 九宫格贴图**（与 26.1.2 线同观感），贴图在 `assets/astral_dice/textures/gui/sprites/tooltip/<tier>_{background,frame}.png(.mcmeta)` |
| `ModernUITooltipCompat` | `.../client/ModernUITooltipCompat.java` | 专门对付 Modern UI：反射写 `TooltipRenderer.sStrokeColor` 四槽 + 临时关 `sAdaptiveColors`；以及 `enterTooltipStack/exitTooltipStack` 写/还原 `GuiGraphics#modernUI_MC$tooltipStack` |

## 3. 根因一：JEI 在**调用点就把 ItemStack 丢掉了**

javap 实证（JEI `15.x` 的 fabric 实现）：

```
mezz/jei/fabric/platform/RenderHelper.renderTooltip(GuiGraphics, List, II, Font, ItemStack):
  ... createClientTooltipComponents(List, Font) -> List<ClientTooltipComponent>
  ... invokevirtual GuiGraphics#renderTooltipInternal(Font, List, int, int, ClientTooltipPositioner)
```

整条链路**没有物品栈**（1.21.1 `method_51435 = renderTooltipInternal` 同款），
所以 `TooltipFrameColors.pushStack` 根本不会发生 ⇒ 下游只能拿到默认色。

**对接方式**：在 JEI 的入口自己压栈（`mixin/jei/JeiTooltipStackMixin`，`@Pseudo` + `require = 0`），
配套 `astral_dice.jei.mixins.json`（`"required": false`，与 `jade` / `trinkets` 同范式）并登记进
`fabric.mod.json` 的 `mixins` 数组。

## 4. 根因二：Modern UI 以 `mixinPriority: 1` 抢在 HEAD 取消自绘

`mixins.modernui-fabric.json` 里 `"mixinPriority": 1`，在
`GuiGraphics#renderTooltipInternal` 的 **HEAD** 调 `CallbackInfo.cancel()`，改走
`UIManager#drawExtTooltip` → `TooltipRenderer#drawTooltip`。

两条后果：

1. **本模组默认优先级（1000）的同类注入永不执行** —— `cancel()` 之后注册更晚的 handler 全部跳过。
   实测整场 0 次。
2. `TooltipRenderer#drawTooltip` 首段是：

```java
if (itemStack != this.mLastSeenItem) {   // 按「物品栈实例」比较，不是 equals
    this.mLastSeenItem = itemStack;
    this.computeWorkingColor(itemStack); // 只有这里才会重算颜色
}
```

而 JEI 从不给 `GuiGraphics#modernUI_MC$tooltipStack` 赋值 ⇒ 字段恒为
`ItemStack.EMPTY` **单例** ⇒ 恒等 ⇒ **颜色永不重算**。
表现就是「JEI 不染色」+「换物品颜色滞留」。

**对接方式**（`ModernUITooltipCompat`，全部反射、失败即永久关闭）：

- `beginRender(stack)`：按档位把 `TooltipRenderer.sStrokeColor` 四个槽写成同一色，并把
  `sAdaptiveColors` 临时置 false（否则 Modern UI 会按背景再调一次亮度，把档位色洗掉）。
- `enterTooltipStack(graphics, stack)` / `exitTooltipStack(graphics)`：写 / 还原
  `modernUI_MC$tooltipStack`（写入前把旧值压进 `ThreadLocal<ArrayDeque>`，退出时还原），
  这样 Modern UI 的实例缓存会被打断，颜色按当前物品重算。
  字段类型不是 `ItemStack` 时**静默降级**，不抛。

## 5. 根因三（1.20.1 专属）：JEI 的方法用 `astore_0` 复用 this 槽 ⇒ Mixin 注入必炸

这是本次最贵的一课。**JEI 15.74 的若干方法会把 this 槽复用成别的类型**，vanilla 跑得动，
但 Mixin 注入时会按局部变量表（LVT）声明类型**重算栈帧**，验证器直接拒绝，且 **`@At("HEAD")` 也连带触发**。

第一版（照抄 1.21.1 的做法）注入 `mezz.jei.fabric.platform.RenderHelper#renderTooltip`：

```
java.lang.VerifyError: Bad type on operand stack
  Location: mezz/jei/fabric/platform/RenderHelper.renderTooltip(GuiGraphics, List, II, Font, ItemStack)V @47
  Reason: Type 'ClientTooltipPositioner' (current frame, stack[0]) is not assignable to 'mezz/jei/fabric/platform/RenderHelper'
  locals: { ClientTooltipPositioner, GuiGraphics, List, int, int, Font, ItemStack }
```

对应字节码（`getstatic DefaultTooltipPositioner.INSTANCE; astore_0`）：

```
aload_1; aload_0; aload_2; aload 5; invokevirtual createClientTooltipComponents(List,Font)List;
getstatic DefaultTooltipPositioner.INSTANCE; astore_0; aload 5; swap;
iload_3; iload 4; aload_0; invokevirtual GuiGraphics.method_51435(...)V
```

第二版改挂「调用侧」`mezz.jei.common.gui.JeiTooltip#renderTooltip`，**同样炸**：

```
VerifyError: Bad type on operand stack
  Location: mezz/jei/common/gui/JeiTooltip.renderTooltip(IPlatformRenderHelper, GuiGraphics, II, Font, ItemStack, ClientTooltipPositioner)V @54
  Reason: Type 'java/util/List' (current frame, stack[0]) is not assignable to 'mezz/jei/common/gui/JeiTooltip'
  locals: { 'java/util/List', 'IPlatformRenderHelper', ... }
```

对应字节码（把 `lines` 字段塞进 this 槽）：`aload 7; aload_0; getfield lines; astore_0; ifnonnull`。

**第三版（现行）**：挂
`mezz.jei.common.gui.JeiTooltip#draw(GuiGraphics, int, int, ITypedIngredient, IIngredientRenderer, IIngredientManager, ClientTooltipPositioner)`。

选它的三个理由：

1. 逐方法扫过 `astore_0`，这一层**没有** slot 复用（同类的 `addDebugInfo` / 私有 `draw` / `createKeyUsageComponent` 都有，所以没选它们）；
2. 它同时持有 `GuiGraphics` 与 `ITypedIngredient`，物品栈用 `ITypedIngredient#getItemStack()`（返回 `Optional<ItemStack>`）；
3. 它内部按 `positioner == null` 分派到 6 参 / 7 参两个平台重载，
   所以**一对 HEAD/RETURN 覆盖两条绘制路径**（不用分别挂两个重载，也就不会再踩 6 参的坑）。

## 6. 落地清单

| 线 | 文件 | 说明 |
|---|---|---|
| fabric-1.21.1 | `mixin/jei/JeiTooltipStackMixin.java` | `@Mixin(targets="mezz.jei.fabric.platform.RenderHelper")`，6 参 / 7 参各一对 HEAD/RETURN；1.21.1 的字节码没有 slot 复用，可挂 |
| fabric-1.21.1 | `mixin/bridge/TooltipBackgroundSpriteMixin.java` | 九宫格贴图背景（本模组档位专属） |
| fabric-1.21.1 | `client/ModernUITooltipCompat.java` | `beginRender/endRender` + `enterTooltipStack/exitTooltipStack` |
| fabric-1.20.1 | `mixin/jei/JeiTooltipStackMixin.java` | **挂 `JeiTooltip#draw`（不是 RenderHelper）**，理由见 §5 |
| fabric-1.20.1 | `resources/astral_dice.jei.mixins.json` | `required:false` / `minVersion 0.8` / `compatibilityLevel JAVA_17` / `injectors.defaultRequire 0` |
| fabric-1.20.1 | `resources/fabric.mod.json` | `mixins` 数组插入 `astral_dice.jei.mixins.json`（现 4 项） |
| fabric-1.20.1 | `client/ModernUITooltipCompat.java` | 与 1.21.1 同一套 API（1.20.1 的 Modern UI 3.12.0.1 字段与方法同构） |

版本事实：`fabric-1.20.1` 的 JEI 是 `maven.modrinth:jei:bTwbXhIB`
（= `jei-1.20.1-fabric-15.74.0.234.jar`，`gradle.properties` 的 `jei_version=15.74.0.234`；
forge 变体 id 是 `6E4ps2ER`，两者不可混用）。

## 7. 验收

**机器判据（优先，不用等人悬停）**

1. 类能加载 = 没有 `VerifyError`。这是最快的判据：`VerifyError` 在**类加载**时就抛，
   不需要真的画出 tooltip。AutoJS 客户端侧：

   ```js
   Java.loadClass("java.lang.Class").forName("mezz.jei.common.gui.JeiTooltip")
   ```

   注意 Rhino 里 `Java.loadClass("...").class` 取不到 `.class`（会报
   `has no public instance field or method named "class"`），必须用 `java.lang.Class.forName`。
2. 注入器挂上了：

   ```js
   var ms = Java.loadClass("java.lang.Class").forName("mezz.jei.common.gui.JeiTooltip").getDeclaredMethods();
   // 期望出现 handler$xxx$astral_dice$astralDice$pushStack / ...$popStack（各 8 参）
   ```

   本次实测输出：`handler$fgi000$astral_dice$astralDice$popStack/8`、`...pushStack/8`（类共 32 个方法）。
3. 日志里 `Reference map 'astral_dice.refmap.json' for astral_dice.jei.mixins.json could not be read`
   是 **dev 常态**（Loom 1.x 在 `remapJar` 阶段重映射，不产 refmap），只要没有
   `MixinApplyError` / `InvalidMixin` 就算配置被读取。

**肉眼判据**（机器判不了颜色）

背包悬停 → 换物品颜色跟着变；JEI 面板悬停 → 跟档位色；来回切几次不串色。

## 8. 移植到其它线 / 其它第三方查看器时的检查清单

1. 先确认那家**是不是自己发起绘制**（`mixinPriority` + HEAD `cancel()`，或直接订阅事件）——
   是的话本模组在同一个注入点的 handler 会被短路。
2. 确认它的绘制入口**带不带 ItemStack**。不带就得像 JEI 这样在它内部再往回找一层带栈的方法。
3. 在挂任何 mixin 之前，对目标类跑一次
   `javap -p -c | awk '/^  [a-z].*\(/{m=$0} /astore_0|astore_1|astore_2/{print m}'`，
   看有没有 slot 复用；**有就换方法，别硬挂**（编译与构建都不会报错，只在类加载时炸）。
4. 注入后**先跑类加载判据**再启动实机，省一轮崩溃与重开。
5. 反射写字段一律「类型校验不过就静默降级 + 永久关闭」，别让兼容层变成新的崩溃源。

## 9. 本次踩过、值得记住的三条

- **`require` 只管「匹配次数」，不管字节码合法性**：`require = 0` 不能救 `astore_0` 那类 `VerifyError`。
- **同一个仓库里两条 fabric 线的 JEI 版本不同（15.74 vs 19.x），字节码可能一个能挂一个不能挂**，
  所以 f1211 的挂法不能无脑复制给 f1201。
- **dev run 的类加载是懒的**：tooltip 没画出来之前 `VerifyError` 不会出现，
  只看「客户端起来了」会误判为修好了 —— 必须显式 `Class.forName`。
