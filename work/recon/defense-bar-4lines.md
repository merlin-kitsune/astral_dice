# 防御盾（防御力条）行位问题：四线取证与统一方案

- 目标：定位「攻击条定位」顺带发现的**防御力条（左列）行位**问题，给出四线（neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / fabric-1.20.1）语义一致的可执行方案。
- 性质：**只读调查 + 方案设计**。未改动任何源码、未构建、未 commit、未启动游戏客户端（25565/25585 由另一路任务占用），因此所有实机结论都标注为「未核验」。
- 事实源：仓库源码实读 + 各线 build/moddev/artifacts 下带 Mojang 映射的反编译 sources jar 实读（并用 .gradle/loom-cache 侧字节码交叉核过 fabric 实际运行映射）。
- 行号口径：**以当前工作区实读为准**。work/recon/attack-icon-position.md 里的行号是旧快照（例如它写 ForgeGui.java:51 / CombatPowerHud.java:102，现在分别是 ForgeGui.java:56 / CombatPowerHud.java:109），引用时已换算。

---

## 0. 结论先行

1. 四条线里**只有 fabric-1.20.1 的左列基准是写死常量**（ForgeGui.RESET_LEFT = 49），另三条线读的都是**平台真累加量**（1.21.1 / 26.1.2 读原版 Gui.leftHeight，forge-1.20.1 读真 ForgeGui.leftHeight）。
2. 原版 1.20.1 的护甲行是 **height - 39 - (心排数-1)*行距 - 10**（Gui.java:872），有吸收心（两排心）时是 H-59；fabric 的常量 49 在该状态下与**第二排心同 y**（Gui.java:981），因此**必然压心**，且正解应是 H-59（错行 10px）。
3. 另三条线因累计量已含吸收心排数（1.21.1 Gui.java:993、26.1.2 Gui.java:869、Forge ForgeGui.java:305-307 与 :872 代数恒等），在全部五个要求场景下都给出正确行位。
4. 取消原版护甲层**不会**留下「被占的行」：三条平台线取消后原版的 += 10 在同一个 body 里被跳过；1.20.1 原版根本没有累加量概念（护甲行只是局部量），fabric 桩件的 49 是用来顶替这一行的**常量占位**。
5. 推荐方案：**fabric 桩件的 leftHeight 从常量升级为「按原版心排数算出的种子」，保留「读累加量 + 画完自增」语义，另三线不动**（即 S1 的语义、S3 的改动面）。一句话理由：三条线已经是对的，问题只是 fabric 把「原版已占高度」写成了常量；复刻原版三行算术（并发/无 mixin）比新增 mixin 更小的面换同样的语义。

---

## 1. 四线逐条取证

### 1.1 一览表

| 线 | 防御条 y 表达式（原文行号） | 基准来源 | 取消/接管原版护甲 | 取消后原版是否仍占行 |
|---|---|---|---|---|
| fabric-1.20.1 | int y = height - gui.leftHeight;（client/CombatPowerHud.java:109） | **写死常量**：FabricClientBridges.java:135 → ForgeGui.RESET_LEFT = 49（platform/client/gui/overlay/ForgeGui.java:56） | mixin/client/ArmorBarCancelMixin.java:45-50（@Redirect Player#getArmorValue 返回 0） | **不占**（1.20.1 原版无累加量；取消 = 不画） |
| forge-1.20.1 | int y = height - gui.leftHeight;（client/CombatPowerHud.java:120） | **真累加量**：真 ForgeGui 每帧 :110 复位 39，健康段 :305-307 累加（健康段实测 :306 leftHeight += healthRows*rowHeight; :307 if (rowHeight != 10) leftHeight += 10 - rowHeight;） | @SubscribeEvent onRenderGuiOverlayPre（client/CombatPowerHud.java:143-149）取消 ARMOR_LEVEL + OAB | 不占（ForgeGui.java:125 if (pre(entry, guiGraphics)) return; ⇒ :225 leftHeight += 10 不执行） |
| neoforge-1.21.1 | int y = guiGraphics.guiHeight() - gui.leftHeight;（client/CombatPowerHud.java:148） | **真累加量**：原版 Gui，每帧 :259 复位 39，renderHealthLevel :993 leftHeight += (l1 - 1) * i2 + 10; | @SubscribeEvent onRenderGuiLayerPre（client/CombatPowerHud.java:135-141）取消 ARMOR_LEVEL + OAB | 不占（GuiLayerManager.java:68 判定取消 ⇒ 层 body 不执行 ⇒ Gui.java:1012 leftHeight += 10 不执行） |
| neoforge-26.1.2 | int y = guiGraphics.guiHeight() - gui.leftHeight;（client/CombatPowerHud.java:142） | **真累加量**：原版 Gui，每帧 :217 复位 39，extractHealthLevel :869 leftHeight += (numHealthRows - 1) * healthRowHeight + 10; | @SubscribeEvent onRenderGuiLayerPre（client/CombatPowerHud.java:129-135）取消 ARMOR_LEVEL + OAB | 不占（GuiLayerManager.java:75 同一判定 ⇒ Gui.java:891 leftHeight += 10 不执行） |

共同点（四线一致，非本问题）：防御条**无条件占一行**（fabric CombatPowerHud.java:111-112 注释「防御条恒显 ⇒ 无条件占一行」；neo/forge 三线同一口径），而原版只在 getArmorValue() > 0 时占行。

### 1.2 fabric-1.20.1（唯一有缺陷的一条）

**y 表达式与基准链**

- client/CombatPowerHud.java:105-113（DEFENSE_OVERLAY 全文）：
  - :106-107 LocalPlayer player = renderTarget(); if (player == null) return;
  - :109 int y = height - gui.leftHeight;
  - :110 drawRow(guiGraphics, DEFENSE_FULL, DEFENSE_HALF, width / 2 - 91, y, defense, false);
  - :111 注释「防御条恒显 ⇒ 无条件占一行（原版 renderArmor 的 += 10 已随护甲绘制被取消）」
  - :112 gui.leftHeight += ROW_HEIGHT;
- platform/client/FabricClientBridges.java:129-140（每帧）：
  - :135 ForgeGui.INSTANCE.leftHeight = ForgeGui.RESET_LEFT;
  - :136 rightHeight = ForgeGui.RESET_RIGHT;
  - :137-140 按 entries 注册顺序逐条 entry.overlay().render(ForgeGui.INSTANCE, guiGraphics, tickDelta, guiGraphics.guiWidth(), guiGraphics.guiHeight())
- platform/client/gui/overlay/ForgeGui.java（72 行，**不 extends Gui**，是本仓自建的桩件）：
  - :43 public final class ForgeGui；:44 static 实例字段 INSTANCE
  - :47 public int leftHeight；:50 public int rightHeight
  - :56 RESET_LEFT = 49（左列常量：顶替原版护甲那一行）
  - :62 RESET_RIGHT = 59；:68 RESET_RIGHT_NO_AIR = 49
  - 类头 :20-22 自述：「1.20.1 原版 Gui 没有 leftHeight/rightHeight，布局硬编码」

**结论**：fabric 的 leftHeight 从来不是累加量，而是「每帧被写成 49」的常量；真正的累加只在 :112 那一处发生（= 49 + 10 = 59 帧末）。因此它对原版心排数**完全不感知**。

**取消原版护甲的方式**：mixin/client/ArmorBarCancelMixin.java

- :36 @Mixin(Gui.class)
- :45-47 @Redirect(method = "renderPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getArmorValue()I"))
- :48-50 private int astralDice$suppressVanillaArmorBar(Player player) { return 0; }
- 类头 :18-20 记录了本仓的 javap 取证：「getArmorValue() 在 renderPlayerHealth 内恰好 1 次，字节码偏移 320」⇒ 该 Redirect 只命中护甲段（Gui.java:874），不影响心/空气/饱食。
- :28-31 明确 **OAB（overloadedarmorbar）未处理**（见 KNOWN-ISSUES fabric 条目）。
- :33-34 明确 Loom 1.14+ 的 method 必须写纯方法名（带描述符会让生产注入失败）。

**取消后原版是否仍占行**：1.20.1 原版没有 leftHeight/rightHeight 字段（Gui.java 里护甲行是局部量 k2，Gui.java:872），取消绘制即等于该行「空出来」。桩件的 49 只是「原版护甲行所处高度」的常量近似，不是从游戏读来的量。

**编译映射**：fabric-1.20.1/build.gradle:67 mappings loom.officialMojangMappings() ⇒ 该线用 **Mojang 官方映射**编译，类/方法名与 sources jar 一致（net.minecraft.client.gui.Gui / GuiGraphics / Player），所以 S1 若加 mixin 不需要 yarn→mojmap 的名字换算，但仍需 Loom 重映射到 intermediary（生产）。

### 1.3 forge-1.20.1

- forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/CombatPowerHud.java:116-124：
  - :117 LocalPlayer player = renderTarget();
  - :120 int y = height - gui.leftHeight;
  - :123 gui.leftHeight += ROW_HEIGHT;
  - 类头 :42-44 自述：「Forge 的 renderArmor 就是 top = height - leftHeight，本类补位保持同一行；画完无条件 leftHeight += 10（原版的 += 10 在 renderArmor 里，已被取消）」
- 取消原版护甲：同文件 :143-149 @SubscribeEvent onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre)，:146-148 VanillaGuiOverlay.ARMOR_LEVEL.id().equals(id) || OVERLOADED_ARMOR_BAR_LAYER.equals(id) → event.setCanceled(true)；OVERLOADED_ARMOR_BAR_LAYER 定义在 :73-74。
- 基准来源（真 ForgeGui，来自 forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar → net/minecraftforge/client/gui/overlay/ForgeGui.java）：
  - :49 class ForgeGui extends Gui；:60-61 public int leftHeight = 39; public int rightHeight = 39;
  - :103-135 render(...)：:109-110 每帧 rightHeight = 39; leftHeight = 39;；:112 RenderGuiEvent.Pre 取消即 return；:121-132 遍历 overlay：:125 if (pre(entry, guiGraphics)) return; :126 overlay.render(this, ...) :127 post(...)
  - :641-644 private boolean pre(...) { return MinecraftForge.EVENT_BUS.post(new RenderGuiOverlayEvent.Pre(...)); }
  - 健康段 :265-319 renderHealth(...)：:296 float healthMax = Math.max((float) attrMaxHealth.getValue(), Math.max(healthLast, health))（healthLast 即 Forge 版的 displayHealth）；:297 int absorb = Mth.ceil(player.getAbsorptionAmount())；:299 healthRows = ceil((healthMax + absorb)/2F/10F)；:300 rowHeight = max(10-(healthRows-2), 3)；:305 int top = height - leftHeight；:306 leftHeight += (healthRows * rowHeight); :307 if (rowHeight != 10) leftHeight += 10 - rowHeight;
  - 护甲段 :200-229 renderArmor(...)：:206 int top = height - leftHeight；:208 armor 值；:209 十格循环 blit；:225 leftHeight += 10;
- 为什么这条线「天然正确」：Forge 的健康段把**心排数**累加进去了（:306-307），其值与 1.20.1 原版护甲行完全等价（见 §1.6 的代数恒等）。取消护甲 overlay 后 leftHeight 停在「健康段末值」，正是护甲行应处的偏移。
- 运行时镜像：forge-1.20.1/build/moddev/artifacts 的 patches/net/minecraft/client/Minecraft.java.patch 含 + this.f_91065_ = new net.minecraftforge.client.gui.overlay.ForgeGui(this); ⇒ 运行时 Minecraft.getInstance().gui 的实例就是 ForgeGui；而 patches/net/minecraft/client/gui/Gui.java.patch（68 行）**完全不碰 renderPlayerHealth** ⇒ 原版 Gui.renderPlayerHealth 在 Forge 上永不执行，左列完全由 ForgeGui 决定。

### 1.4 neoforge-1.21.1

- client/CombatPowerHud.java:143-152：
  - :144 LocalPlayer player = renderTarget();
  - :148 int y = guiGraphics.guiHeight() - gui.leftHeight;
  - :151 gui.leftHeight += ROW_HEIGHT;
- 取消原版护甲：同文件 :135-141 @SubscribeEvent onRenderGuiLayerPre(RenderGuiLayerEvent.Pre)，:138 VanillaGuiLayers.ARMOR_LEVEL.equals(name) || OVERLOADED_ARMOR_BAR_LAYER.equals(name) → setCanceled(true)；OAB 常量 :82-83。
- 基准来源：原版 Gui（neoforge-21.1.235-sources.jar → net/minecraft/client/gui/Gui.java）
  - :200 public int leftHeight; :204 public int rightHeight;
  - :259-260 每帧 leftHeight = 39; rightHeight = 39;
  - :987 int j1 = guiHeight - leftHeight; :990 int l1 = Mth.ceil((f + k1)/2F/10F); :991 int i2 = max(10-(l1-2),3); :993 leftHeight += (l1 - 1) * i2 + 10;
  - :1009 renderArmor(..., guiHeight - leftHeight + 10, 1, 0, l); :1011-1013 if (getArmorValue() > 0) leftHeight += 10;
  - :1067 int j = y - (heartRows - 1) * height - 10;（heartRows 实参恒为 1）
- 取消后原版不占行：GuiLayerManager.java（同 sources jar，net/neoforged/neoforge/client/gui/GuiLayerManager.java）:64-77，:68 if (!NeoForge.EVENT_BUS.post(new RenderGuiLayerEvent.Pre(...)).isCanceled()) { :69 渲染层 :70 post(Post) } ⇒ 取消即整段 body（含 :1012 的 += 10）不执行。
- 附带事实：leftHeight 在本线还被原版其它元素消费——:326 int yShift = Math.max(leftHeight, rightHeight) + (68 - 59)；:621 renderSelectedItemName(..., Math.max(leftHeight, rightHeight))。即「占行」语义真实存在，不是只在模组内部自娱自乐。

### 1.5 neoforge-26.1.2（LayeredDrawer）

- client/CombatPowerHud.java:137-146：
  - :138 LocalPlayer player = renderTarget();
  - :142 int y = guiGraphics.guiHeight() - gui.leftHeight;
  - :145 gui.leftHeight += ROW_HEIGHT;
- 取消原版护甲：同文件 :129-135 onRenderGuiLayerPre(Identifier name)，:132 同样两条件 setCanceled(true)（VanillaGuiLayers.ARMOR_LEVEL / OVERLOADED_ARMOR_BAR_LAYER，:77-78 Identifier.fromNamespaceAndPath）。
- 基准来源：原版 Gui（minecraft-patched-26.1.2.109-sources.jar → net/minecraft/client/gui/Gui.java，LayeredDrawer 结构）
  - :215-222 extractRenderState(...)：:217 leftHeight = 39; :218 rightHeight = 39; :220 layerManager.render(graphics, deltaTracker)
  - :224-240 registerVanillaLayers(...)：:225 guiVisible = () -> !options.hideGui；:226 survivalVisible = () -> gameMode.canHurtPlayer() && guiVisible.getAsBoolean()；:232 layerManager.add(PLAYER_HEALTH, (g,p) -> extractHealthLevel(g), survivalVisible)；:233 ARMOR_LEVEL → extractArmorLevel；:234 FOOD_LEVEL；:235 VEHICLE_HEALTH；:236 AIR_LEVEL（均 survivalVisible）
  - :839 extractHealthLevel：:863 int yLineBase = graphics.guiHeight() - leftHeight; :866 numHealthRows = ceil((maxHealth + totalAbsorption)/2F/10F); :867 healthRowHeight = max(10-(numHealthRows-2),3); **:869 leftHeight += (numHealthRows - 1) * healthRowHeight + 10;**
  - :883 extractArmorLevel：:888 extractArmor(..., guiHeight - leftHeight + 10, 1, 0, l); :890-891 if (getArmorValue() > 0) leftHeight += 10;
  - :923-943 extractArmor(..., int yLineBase, int numHealthRows, int healthRowHeight, int xLeft)：:926 int yLineArmor = yLineBase - (numHealthRows - 1) * healthRowHeight - 10;
- 取消后原版不占行：GuiLayerManager.java（~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.1.2.109/80f71ba…/neoforge-26.1.2.109-sources.jar）:73-80 renderInner，:75 同一 isCanceled 判定；:41-53 add(name, Consumer, BooleanSupplier) / add(name, GuiLayer, BooleanSupplier) 把 vanilla 层套上 shouldRender。
- LayeredDrawer 结构对本题的影响：**心行与护甲行的行基准都由 leftHeight 一个可变量串起来**（健康层写、护甲层读），所以「取消护甲层 → 读同一个 leftHeight」的语义与 1.21.1 完全同构；26.1.2 只是把 render 拆成 extract（记录层）而基准算法一字未变。
- 附带事实：leftHeight 还影响 :361 int yShift = Math.max(leftHeight, rightHeight) + (68 - 59)；:610 extractSelectedItemName(graphics, Math.max(leftHeight, rightHeight))。

### 1.6 行基准的三种形态 + Forge 的代数恒等（为什么 forge 也对）

三种形态：

1. **平台真累加量**（neo-1.21.1 / neo-26.1.2）：原版自己把 39 → 「健康段末值」写进 public 字段，模组只读。
2. **平台累加量（Forge 自己实现）**（forge-1.20.1）：原版 1.20.1 没有字段，但 ForgeGui 重新实现了一份累加。
3. **写死常量**（fabric-1.20.1）：只有 49（没有载具时右列 49/59 另说）。

Forge 与 1.20.1 原版护甲行的等价性（本条是「forge 为什么正确」的全部理由）：

~~~
原版 1.20.1 Gui.java:872  护甲行 = k1 - (i2 - 1) * j2 - 10，k1 = H - 39
                          ⇒ 距底偏移 = 39 + (rows - 1) * rh + 10
Forge ForgeGui.java:306-307  leftHeight += rows * rh; if (rh != 10) leftHeight += 10 - rh;
                          ⇒ 距底偏移 = 39 + rows * rh + (10 - rh)
恒等：rows * rh + (10 - rh) === (rows - 1) * rh + 10        （对任意 rows、rh 成立）
数值：rows=1, rh=10 → 49    rows=2, rh=10 → 59    rows=3, rh=9 → 67
~~~

同一条公式也是 neo-1.21.1 Gui.java:993、neo-26.1.2 Gui.java:869 的形式（(rows-1)*rh+10）。因此**四线的「正确值」是同一个数**：左列种子 = 39 + (rows-1)*rh + 10。

---

## 2. 原版行为取证（原文抄录）

### 2.1 Minecraft 1.20.1：net/minecraft/client/gui/Gui.java#renderPlayerHealth（:840-953）

护甲行 / 空气行 / 心排数的决定性行：

~~~
:867  int k1 = this.screenHeight - 39;
:868  float f = Math.max((float)player.getAttributeValue(Attributes.MAX_HEALTH), (float)Math.max(k, i));
:869  int l1 = Mth.ceil(player.getAbsorptionAmount());
:870  int i2 = Mth.ceil((f + (float)l1) / 2.0F / 10.0F);        // 心排数
:871  int j2 = Math.max(10 - (i2 - 2), 3);                       // 行距
:872  int k2 = k1 - (i2 - 1) * j2 - 10;                          // 护甲行
:873  int l2 = k1 - 10;                                          // 空气行基准
:874  int i3 = player.getArmorValue();
:882-897  for (int k3 = 0; k3 < 10; ++k3) { if (i3 > 0) { int l3 = i1 + k3 * 8; ... blit(..., l3, k2, ...) } }
:900  this.renderHearts(guiGraphics, player, i1, k1, j2, j3, f, i, k, l1, flag);
~~~

吸收心（第二排心）的 y（Gui.java#renderHearts :970-1010）：

~~~
:970  protected void renderHearts(GuiGraphics guiGraphics, Player player, int x, int y, int height, int offsetHeartIndex,
                               float maxHealth, int currentHealth, int displayHealth, int absorptionAmount, boolean renderHighlight)
:973  int j = Mth.ceil((double)maxHealth / 2.0D);
:974  int k = Mth.ceil((double)absorptionAmount / 2.0D);
:977  for (int i1 = j + k - 1; i1 >= 0; --i1) {
:978     int j1 = i1 / 10;
:979     int k1 = i1 % 10;
:980     int l1 = x + k1 * 8;
:981     int i2 = y - j1 * height;              // 第 j1 排心 ⇒ y - j1*行距
:982-984 if (currentHealth + absorptionAmount <= 4) { i2 += this.random.nextInt(2); }   // 低血量抖动
:992     boolean flag = i1 >= j;                 // 吸收心
~~~

推论（可直接用于判据）：

- 1 排心：护甲行 k2 = H-49，吸收心不存在；
- 2 排心（吸收 > 0）：吸收心 y = k1 - 1*j2 = **H-49**，护甲行 k2 = **H-59**；
- 3 排心（maxHealth+absorption > 40）：rh = 9，护甲行 = H-67，心行 H-39 / H-48 / H-57；
- 低血量抖动（:982-984）只改心图标 y（±1），**不改护甲行 k2**；
- 载具（:901-902 getVehicleMaxHearts）只影响右列（饱食/空气），**不碰 k2**；
- 气泡行是 l2（右列），与 k2 无关。

### 2.2 Minecraft 1.21.1：Gui.java（渲染层化）

~~~
:259-260  leftHeight = 39; rightHeight = 39;                     // 每帧复位
:962  renderHealthLevel(...)
:987    int j1 = p_283143_.guiHeight() - leftHeight;               // 心行基准
:990    int l1 = Mth.ceil((f + (float)k1) / 2.0F / 10.0F);        // 心排数
:991    int i2 = Math.max(10 - (l1 - 2), 3);                      // 行距
:993    leftHeight += (l1 - 1) * i2 + 10;                         // ← 真累加量（含吸收心排）
:1004 renderArmorLevel(...)
:1009   renderArmor(p_283143_, player, p_283143_.guiHeight() - leftHeight + 10, 1, 0, l);
:1011-1013  if (player.getArmorValue() > 0) { leftHeight += 10; }
:1063 renderArmor(GuiGraphics guiGraphics, Player player, int y, int heartRows, int height, int x)
:1067   int j = y - (heartRows - 1) * height - 10;                 // heartRows 实参 = 1 ⇒ j = guiHeight - leftHeight
:1118-1129 renderHearts(...)：:1126 int l1 = y - i1 * height; :1127-1129 低血抖动 l1 += random.nextInt(2)
:326   int yShift = Math.max(leftHeight, rightHeight) + (68 - 59);
:621   this.renderSelectedItemName(p_316628_, Math.max(this.leftHeight, this.rightHeight));
~~~

### 2.3 Minecraft 26.1.2：LayeredDrawer + GuiGraphicsExtractor

结构（Gui.java）：

~~~
:217-218  leftHeight = 39; rightHeight = 39;                      // extractRenderState
:220      layerManager.render(graphics, deltaTracker);
:225-226  guiVisible = () -> !options.hideGui;
          survivalVisible = () -> gameMode.canHurtPlayer() && guiVisible.getAsBoolean();
:232-236  layerManager.add(PLAYER_HEALTH, (g,p) -> extractHealthLevel(g), survivalVisible);
          ARMOR_LEVEL → extractArmorLevel；FOOD_LEVEL；VEHICLE_HEALTH；AIR_LEVEL
~~~

行基准（Gui.java）：

~~~
:863  int yLineBase = graphics.guiHeight() - leftHeight;
:866  int numHealthRows = Mth.ceil((maxHealth + totalAbsorption) / 2.0F / 10.0F);
:867  int healthRowHeight = Math.max(10 - (numHealthRows - 2), 3);
:868  int yLineAir = yLineBase - 10;
:869  leftHeight += (numHealthRows - 1) * healthRowHeight + 10;
:888  extractArmor(graphics, player, graphics.guiHeight() - leftHeight + 10, 1, 0, l);
:890-891  if (player.getArmorValue() > 0) { leftHeight += 10; }
:923  private static void extractArmor(GuiGraphicsExtractor graphics, Player player, int yLineBase, int numHealthRows, int healthRowHeight, int xLeft)
:926     int yLineArmor = yLineBase - (numHealthRows - 1) * healthRowHeight - 10;
:964-971 extractHearts(...)：for (int containerIndex = healthContainerCount + absorptionContainerCount - 1; containerIndex >= 0; containerIndex--)
          :965 int row = containerIndex / 10;  :968 int yo = yLineBase - row * healthRowHeight;
          :969-971 低血抖动 yo += random.nextInt(2)
:361  int yShift = Math.max(leftHeight, rightHeight) + (68 - 59);
:610  extractSelectedItemName(graphics, Math.max(this.leftHeight, this.rightHeight));
~~~

### 2.4 「取消了一层」≠「那一行空出来了」（平台语义取证）

- NeoForge 1.21.1：net/neoforged/neoforge/client/gui/GuiLayerManager.java:64-77，:68 取消判定包住 :69-70 ⇒ 层 body 整段不执行 ⇒ Gui.java:1012 的 += 10 不执行；net/neoforged/neoforge/client/event/RenderGuiLayerEvent.java:58-75（Pre 可取消、取消则 Post 不发）。
- NeoForge 26.1.2：GuiLayerManager.java:73-80，:75 同一判定；:41-53 的 add(...) 用 BooleanSupplier 把 vanilla 层的 shouldRender 一并包裹。
- Forge 1.20.1：ForgeGui.java:125 if (pre(entry, guiGraphics)) return; + :641-644 ⇒ :225 leftHeight += 10 不执行。
- Fabric 1.20.1：原版无累加量（护甲行是局部 k2），取消绘制即「不占行」；桩件常量 49 是**快照**而非读数。

也正因为如此，四条线的「补位」写法才都成立：模组接在被取消的那一层之后，自己画 + 自己 += 10，恰好把原版少掉的那一行补回去。fabric 的差别只在于：它补位的**起点**是常量 49，而不是「原版本帧已占高度」。

### 2.5 fabric 的绘制时序（压心是字面覆盖）

Fabric API 的 HudRenderCallback 注入在 Gui#render 的最末：fabric-rendering-v1-3.0.10+…-sources.jar → net/fabricmc/fabric/mixin/client/rendering/InGameHudMixin.java，对 Gui#render 用 @At("TAIL")（slice 自 PlayerListHud#render），HudRenderCallback.java 文档同样写 "Called after rendering the whole hud"。而 1.20.1 Gui#render 的心/护甲段在 :219-227（if (!this.minecraft.options.hideGui) { ... if (this.minecraft.gameMode.canHurtPlayer()) this.renderPlayerHealth(guiGraphics); }）。

⇒ 模组的防御条画在原版心**之后**，两者同 y 时是后画的覆盖前画的 —— 「压心」不是错位叠影，是实打实盖掉。

---

## 3. 失效矩阵

判定口径：**正确** = 防御条落在原版这一帧「护甲行」应在的位置且不与任何心图标/气泡重叠；**压心** = 与某排心同 y 且 x 区间相交（覆盖）；**错行** = 位置与护甲行不同（多数为偏下 10 或 18px）；**未核验** = 无实机证据。

| # | 场景 | fabric-1.20.1 | forge-1.20.1 | neoforge-1.21.1 | neoforge-26.1.2 | 证据 |
|---|---|---|---|---|---|---|
| ① | 单排心（无吸收，maxHealth ≤ 20，护甲 > 0） | **正确** | 正确 | 正确 | 正确 | fabric 常量 49 == Gui.java:872 单排值；另三线累加量 = 49（ForgeGui.java:306-307 / Gui.java:993 / Gui.java:869） |
| ② | 双排吸收心（吸收 I = 4 点） | **压心 + 错行** | 正确 | 正确 | 正确 | 吸收心 y = H-49（Gui.java:981），防御条 y = H-49（CombatPowerHud.java:109），正解 H-59（Gui.java:872）；三线累加量 = 59 |
| ③ | 三排心（maxHealth + absorption > 40，rh = 9） | **压心 + 错行** | 正确 | 正确 | 正确 | 正解 H-67；fabric 仍 H-49（偏下 18px）；Forge 306-307 = 39+27+1 = 67，neo = 39+18+10 = 67 |
| ④ | 骑乘坐骑（mount.showVehicleHealth()） | 正确（左列本就不让位） | 正确 | 正确 | 正确 | 载具只影响右列（1.20.1 Gui.java:901-902 后接饱食/空气；ForgeGui.java:594-638 只动 rightHeight）⇒ 左列基准与①相同；四线的防御条都**不**让位（fabric 只有 ATTACK_OVERLAY 让位，CombatPowerHud.java:118-120） |
| ⑤ | 低血量抖动（currentHealth + absorption ≤ 4） | 正确 | 正确 | 正确 | 正确 | 抖动只改心图标 y（Gui.java:982-984 / 1.21.1:1127-1129 / 26.1.2:969-971），不改护甲行；防御条与心最近距离 1px 且行距不变 |
| ⑥ | 潜水（气泡行出现） | 正确 | 正确 | 正确 | 正确 | 气泡是右列（1.20.1 l2 = k1-10 起、Gui.java:934-948），与本问题左列无交集；fabric 的右列已用 airRowShown 自行判断（CombatPowerHud.java:126、:186-190） |
| ⑦ | F1 隐藏 HUD / 旁观（canHurtPlayer() = false） | 正确（不画） | 正确 | 正确 | 正确 | 四线统一走 renderTarget() 门控（fabric :143-148、forge :158-162、neo-1.21.1 :175-179、neo-26.1.2 :169-173），条件与 1.20.1 Gui.java:219/:225 一致 |
| ⑧ | 护甲 = 0（原版不占行，模组仍占） | 与 vanilla 差 1 行（有意，四线一致） | 同左 | 同左 | 同左 | 原版仅 armor > 0 占行（Gui.java:882 / 1.21.1:1011-1013 / 26.1.2:890-891）；模组无条件 += 10（fabric CombatPowerHud.java:111-112） |
| ⑨ | 装了 OverloadedArmorBar | **未核验**（mixin 只压原版，OAB 会另画一条护甲行） | 正确（OAB 层被取消 :146-148） | 正确（:138） | 正确（:132） | fabric ArmorBarCancelMixin.java:28-31 自述未处理；实测 fabric 测试包 46 个 jar 无 OAB（已列目录核） |

矩阵里唯一的红格是 ②③（②是用户报告的现象，③是同一机制外推的更大破坏面）。

---

## 4. 方案对比与推荐

四线「正确值」唯一（见 §1.6）：左列种子 = 39 + (rows-1) * rh + 10，其中 rows = ceil((max(最大生命, 当前生命) + ceil(吸收)) / 2 / 10)，rh = max(10 - (rows-2), 3)。

### 4.1 S1：让 fabric 桩件的 leftHeight 变成真累加量（mixin 在 renderPlayerHealth 里回填 / @Redirect 读局部）

- 改动面：新增 fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/client/HealthRowSeedMixin.java（约 35-45 行）+ src/main/resources/astral_dice.mixins.json 增加 1 条 mixins 条目 + 保留 ForgeGui.RESET_LEFT 作兜底。CombatPowerHud.java / FabricClientBridges.java 不动。
- 注入点风险（逐条）：
  1. **与既有 ArmorBarCancelMixin 抢同一个方法**（Gui#renderPlayerHealth 上再加一个 @Inject/@Redirect）——两条注入可共存，但顺序与 require 命中数要各自核；
  2. **require 语义**：astral_dice.mixins.json 有 injectors.defaultRequire = 1 ⇒ 目标方法一旦改名/消失，是**启动崩**而不是静默降级；若改成 require = 0 则反过来会静默失效（两害，需明确取舍）；
  3. **读局部变量（@Redirect / @ModifyVariable 拿 i2、j2）会随字节码局部索引漂移**，且必须写纯方法名（ArmorBarCancelMixin.java:33-34 已踩过 Loom 1.14+ 的坑）；
  4. **映射**：该线是 loom.officialMojangMappings()（build.gradle:67），源码名与 sources jar 一致，但生产要经 Loom 重映射到 intermediary；
  5. **与第三方 mixin 抢方法**：1.20.1 上 OAB 之类会 @Redirect blit，同方法多 mixin 顺序不确定（今天测试包没有 OAB，见 ⑨）。
- 额外事实：**S1 若选择「自己按玩家属性算」（HEAD 注入 + 自算）而不是读局部变量，那么它算的公式与 S1' 完全一样**——多出来的只有 mixin 面，没有语义收益（1.20.1 没有任何别的消费者读这个 leftHeight，见 §1.2）。
- 跨版本可行性：仅 fabric-1.20.1 有意义。若 fabric 线将来升到 1.21.1+，原版已有 public Gui.leftHeight，届时**应删掉整个桩件改读真字段**（与另三线同构），mixin 也随之作废。
- 可否被单条实机判据验证：可以（C1 数值判据，见 §5.1）。

### 4.2 S1'：同一语义、不动注入点（**推荐**）

- 做法：把「每帧写入的 49」换成「按原版心排数算出的种子」，其余一字不改：
  - platform/client/gui/overlay/ForgeGui.java 增加一个纯函数（约 14-18 行），例如
    ~~~java
    /** 原版本帧左列「已占高度」= 39 + (心排数-1)*行距 + 10（Gui.java:872 同式）。 */
    public static int leftSeed(int maxHealth, int health, float absorption) {
        int abs = Mth.ceil(absorption);
        int rows = Mth.ceil((Math.max(maxHealth, health) + abs) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        return 39 + (rows - 1) * rowHeight + 10;
    }
    ~~~
  - FabricClientBridges.java:135 改为按当前相机玩家取种子，取不到玩家时退回 RESET_LEFT。
- 改动面：**2 个文件、约 18 行**（外加注释里写死取证行号）。
- 优点：语义与另三线完全一致（读累加量 + 画完自增）；不新增 mixin、不碰注入点、不需要 refmap、不引入 require/映射漂移风险；纯函数可单测/可被游戏内脚本直接调用核对。
- 风险：**公式复刻漂移**——若未来原版改行基准，fabric 不会自动跟上（另三线会）。缓解：注释写死 Gui.java:869-872 取证 + 用 C1 判据做回归（吸收态读值必须是 69/77）。
- 跨版本：同 S1（fabric 升 1.21+ 时整块桩件删除）。
- 可验证性：可，且与 S1 判据完全相同（C1）。

### 4.3 S2：四线都改成自己算 base（leftBase(healthRows, absorption, vehicle)），不读平台字段

- 改动面：4 个 CombatPowerHud.java 各 + 一个 helper，约 4 × 20 = 80 行；四份公式副本。
- **否决理由（有证据）**：三条 neo/forge 线**已经**是真累加量，换成自算会**丢掉跨模组链式**。证据：neoforge-1.21.1/client/CombatPowerHud.java:50-51 自述「因为用的是原版累加量而非屏幕常量，其它模组（已实证：Iron's 法术书的 mana bar 锚 Hunger 时会 rightHeight += 10）」。同理左列若有第三方在健康层之上插一行（或改 rows），读累加量能自动让位，自算不能。
- 另外：S2 把「1 份公式」变成「4 份公式」，漂移面 ×4，还要求四线同批改；跨版本可行性最差（1.21.1 与 26.1.2 的字段消费点 :326/:621 与 :361/:610 会被绕开）。
- 唯一可取之处：完全不依赖平台字段，理论上对「平台字段被第三方污染」免疫——但本仓现实中不存在这种污染。
- 可验证性：可（同一判据），但会把三条**当前正确**的线置于回归风险中。

### 4.4 S3：只在 fabric 打补丁（把 49 换成按心排数动态算的 y），另三线不动

- 改动面：fabric client/CombatPowerHud.java:109 附近（约 8-10 行 helper + 1 行改 y）。
- 问题：只修「症状」——桩件字段仍是常量，语义仍撒谎：将来本线任何新 overlay（或复用桩件画第三行）读到 leftHeight 会再次踩坑；四线语义仍不统一（三线「读累加量」、fabric「读局部常量」）；且它把「占一行」的链式（:112 的 += 10）与基准计算拆成两套口径。
- 跨版本 / 可验证：都可（同判据）。
- 定位：**可作最小热修**（如果必须在当天出一个仅 fabric 的补丁），但不作为正解。

### 4.5 对比与推荐

| 维度 | S1（mixin 回填） | **S1'（桥接层自算种子）** | S2（四线自算） | S3（仅改 y） |
|---|---|---|---|---|
| 改动文件/行数 | 2 文件 +1 新文件，~45 行 | **2 文件，~18 行** | 4 文件，~80 行 | 1 文件，~10 行 |
| 语义一致性（四线） | 一致 | **一致** | 一致 | 不一致 |
| 新增注入点风险 | 有（require/映射/抢方法/局部索引） | **无** | 无 | 无 |
| 丢第三方链式 | 不丢 | **不丢**（右列照旧、左列仍是累加量） | **丢**（有实证反例） | 不丢但语义割裂 |
| 跨版本维护 | 升版即作废 | 升版即作废（同） | 四份副本 | 一份 |
| 单条实机判据可验 | 可（C1） | **可（C1）** | 可（C1） | 可（C1） |
| 结论 | 语义对，改动面偏大 | **推荐** | 否决 | 仅热修 |

推荐：**S1'**。它不是「另一个方案」，而是 S1 的语义（leftHeight 成为真正的「原版本帧已占高度 + 本模组已占行」）用 S3 的改动面实现；三条 neo/forge 线一行不动。

---

## 5. 可执行验收判据（能一次性证伪「压心」）

前置：窗口与 GUI 缩放固定（记下 mc.getWindow().getGuiScale()＝s，屏幕坐标 = gui 坐标 × s），世界内、无任何界面打开、hideGui = false、聊天区无新增消息，血量稳定至少 2 秒。

### 5.1 C1 · 数值判据（首选，一条数就能证伪）

原理：帧末 leftHeight = 种子 + 10（种子 = 39+(rows-1)*rh+10；本模组叠一行 +10）。该值与「防御条顶边距底距离」等价（y = H - (值 - 10)）。期望：

| 状态 | rows | rh | 期望帧末 leftHeight |
|---|---|---|---|
| 单排心 | 1 | 10 | **59** |
| 双排吸收心（吸收 I = 4 点） | 2 | 10 | **69** |
| 三排心（maxHealth+吸收 > 40） | 3 | 9 | **77** |

证伪线：吸收 I 下读到 **59** ⇒ 基准仍是常量 49 ⇒ ②压心成立。
读法（按线选一个能读的）：

- fabric-1.20.1：Java.loadClass('com.merlinkitsune.astral_dice.platform.client.gui.overlay.ForgeGui').INSTANCE.leftHeight
- neoforge-1.21.1 / neoforge-26.1.2：Minecraft.getInstance().gui.leftHeight（Gui.leftHeight 是 public int 字段：1.21.1 Gui.java:200 声明、每帧 :259 复位；26.1.2 由 extractRenderState 每帧 :217 复位，字段本身由本模组类注释与 Gui 类结构确认为 public）
- forge-1.20.1：运行时实例是 ForgeGui（Minecraft.java.patch 替换 f_91065_），字段只声明在 ForgeGui.java:60；若 Rhino 按静态类型解析失败，先 getClass().getName() 确认实例，再 cast 后读；仍失败则退回 C2/C3（像素判据，三线通用）。

注意：这是**帧末**值，必须在渲染过至少一帧后读（不要在同一 tick 内建/改状态后立刻读）；不要读读成中间态（HudRenderCallback 里读是种子值，见桥接层 :135）。

### 5.2 C2 · 像素差分判据（跨四线通用，不依赖字段可读性）

同一 GUI scale 下，先「无吸收」截图 A，再「吸收 I」截图 B（其余状态不变）：

- 防御条图标外接框上沿 y（gui 坐标）必须从 H-49 变为 **H-59**，即 **Δ = 10 gui-px**（= 10 × s 真实像素，方向向上）。
- 证伪：Δ = 0 ⇒ 不受心排数影响 ⇒ 压心。
- 提高可识别度：把防御值推到 ≥ 19 点让 10 个图标全画满（defense = 2 + 护甲×0.30 + 韧性×0.85 向下取整，见 combat/DiceCombatModifiers.java:844-860、:984-1000）⇒ /attribute 把 armor 设到 60 即约 20 点。

### 5.3 C3 · 重叠判据（一次性决定判据）

双排吸收心态（吸收 I = 4 点 ⇒ 正好 2 颗吸收心，位于该排最左两格：Gui.java:977-981 的 i1 = j+k-1 → j1 = 1、k1 = 0/1 ⇒ x = x+0、x+8）：

- 取 H-49 这一 10px 高的行带，x ∈ [w/2-91, w/2-11]；
- **正确**：该带内非透明像素的 x 跨度 ≤ 20 gui-px（只有左侧 2 颗黄心，其余透明）；
- **压心**：该带内出现 8~10 个 9x9 图标（跨度 ≈ 80 gui-px）⇒ 防御条与本排心重叠。
- 该判据不依赖任何字段读数、不依赖防御贴图配色，只要一帧截图 + 一个区间统计即可；**四线都能跑**，是「一次性证伪压心」的最强判据。

### 5.4 C4 · 五态组合矩阵（四线各跑一遍）

| 态 | 造法（命令） | 期望 rows/rh | 期望 leftHeight 帧末 | 期望像素关系 |
|---|---|---|---|---|
| ① 单排心 | 默认（maxHealth 20、无吸收） | 1 / 10 | 59 | y_def = H-49 = y_heart - 10 |
| ② 双排吸收心 | /effect give @s minecraft:absorption 30 0 | 2 / 10 | 69 | y_def = H-59 = y_heart - 10 |
| ③ 三排心 | /attribute @s <max_health> base set 44 + 吸收 I | 3 / 9 | 77 | y_def = H-67 = y_heart - 2*9 |
| ④ 骑乘 | ② + 骑一匹马/猪（showVehicleHealth） | 2 / 10 | 69 | 同②；左列不让位（原版载具只吃右列） |
| ⑤ 低血量抖动 | ② + health 打到 ≤ 4（吸收仍在） | 2 / 10 | 69 | 心 y ∈ {H-49, H-48}；y_def 恒 H-59 |
| ⑥ 潜水气泡 | ② + 潜入水中（气泡行出现） | 2 / 10 | 69 | 左列同②；右列气泡在 H-59 起 |

补充（可选）态 ⑦：F1 隐藏 → 四线都必须**不画**（renderTarget 门控）；态 ⑧：护甲 0 + 2 排心 → 防御条仍占 H-59（设计如此）。

统一像素关系式（写进断言最省事）：

~~~
y_heart_top = H - 39 - (rows - 1) * rh
y_def_top   = H - (39 + (rows - 1) * rh + 10) = y_heart_top - rh
断言：y_def_top == y_heart_top - rh  且  y_def_top + 9 <= y_heart_top - 1（不重叠）
~~~

### 5.5 探针建议（未执行：本轮不启动客户端）

AutoJS 客户端侧（读值 + 算期望，一次调用即可判定）：

~~~js
var mc = Java.loadClass('net.minecraft.client.Minecraft').getInstance();
var Mth = Java.loadClass('net.minecraft.util.Mth');
var A = Java.loadClass('net.minecraft.world.entity.ai.attributes.Attributes');
var p = mc.player;
var maxH = p.getAttributeValue(A.MAX_HEALTH);
var rows = Mth.ceil((Math.max(maxH, p.getHealth()) + Mth.ceil(p.getAbsorptionAmount())) / 2 / 10);
var rh = Math.max(10 - (rows - 2), 3);
var expect = 39 + (rows - 1) * rh + 10 + 10;      // 帧末值
// fabric: Java.loadClass('com.merlinkitsune.astral_dice.platform.client.gui.overlay.ForgeGui').INSTANCE.leftHeight
// neo/forge: mc.gui.leftHeight
JSON.stringify({ rows: rows, rh: rh, expectEnd: expect });   // 与实读比对
~~~

截图侧：两张图 + 上面 §5.3 的区间统计（Python/Pillow 一次跑完）；四线各 6 态 = 24 张，判据只需「y_def 与 y_heart 的关系式」和「H-49 带内跨度」，无需人工看图。

---

## 6. 未核验清单

1. **实机数值/像素全未核验**（本轮不启动客户端）：§5 的 C1-C4 都是设计好的判据，不是观测结果。
2. fabric 线用户侧若装 OverloadedArmorBar：ArmorBarCancelMixin 只压原版 getArmorValue，OAB 可能另画一条护甲行与防御条抢同一行。已核 fabric 测试包（D:/.minecraft/versions/1.20.1-Fabric 模组测试/mods，46 个 jar）**无 OAB**，故当前测试环境不触发；用户侧未核验。
3. 三排心（rows = 3、rh = 9）在 fabric 的实机像素：数学上应为 H-67/H-77（§1.6），未截图核。
4. 骑乘时防御条**是否需要**让位，属产品口径（现有四线都不让位，与原版左列行为一致）；未获用户裁决。
5. 低血量抖动 ±1 与防御条仅 1px 间隙的观感（是否视觉贴边）：未核验。
6. fabric 线是否存在第三方 mod 读取/写入桩件 leftHeight（本仓自建类，外部几乎不可能，但 Iron's 法术书那类在 1.21.1 已实证会动 rightHeight）——fabric 测试包无此类 mod，未核验用户侧。
7. S1' 公式对 **displayHealth** 的近似：原版是 max(attrMax, max(displayHealth, health))（1.20.1 Gui.java:868），displayHealth 是 Gui 的 private 字段，桩件取不到；用 max(attrMax, health) 替代（Forge 侧对应字段是 ForgeGui.healthLast，见 ForgeGui.java:296），只有在「最大生命属性被下调到当前生命以下」的罕见场景会少算 1 排。未核验该场景是否需要补。
8. 26.1.2 的 mod 层是否被 survivalVisible 一并门控（NeoForge 对 registerAbove 注册的自定义层是否套 shouldRender）：不影响本问题（四条线都有 renderTarget 自守），但未核验。
9. NeoForge 1.21.1 / 26.1.2 上，「第三方在健康层与护甲层之间插行」时防御条是否正确让位：机制上应当正确（读累加量），未实机核验。

---

## 7. 一句话结论

**三条 neo/forge 线已经正确，缺陷只在 fabric 桩件把「原版本帧左列已占高度」写成了常量 49；把该常量换成按原版心排数算出的种子（39 + (rows-1)·rh + 10，即 1.20.1 Gui.java:872 同式），保留「读累加量 + 画完自增」，另三线一行不动 —— 这就是推荐方案。**
