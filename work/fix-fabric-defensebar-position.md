# fabric-1.20.1 防御条（左列）行位缺陷 — 修复报告

- 仓库：F:/MCProject/astral_dice_multiloader（**只改 fabric-1.20.1 线**，只碰指定的三个文件）
- 作业时间：2026-11-02 19:04 → 19:20（Asia/Hong_Kong）
- 方案：**S1'**——保留「读 leftHeight 累加量 + 画完自增」的协作模型，只把每帧的**种子常量 49** 换成按原版心排数复算的值
- 实机：Fabric dev client（quickplay testworld，1920x1080，guiScale 4.0，H=270）+ AutoJS 客户端侧 eval；**六态读数 + 六张截图像素核验**
- 前序工作（右列攻击条 y 修复）**全部保留**，本轮未触碰 CombatPowerHud.java 的任何一行
- 最终结论见文末最后一行。

## 1. 改动清单

### 1.1 文件级概览（行尾纪律）

| 文件 | 行数 | CRLF | 裸 LF | 孤立 CR | 本轮是否改动 |
|---|---|---|---|---|---|
| platform/client/gui/overlay/ForgeGui.java | 135 | 135 | **0** | **0** | 改（左列种子） |
| platform/client/FabricClientBridges.java | 252 | 252 | **0** | **0** | 改（每帧复位调用） |
| client/CombatPowerHud.java | 229 | 229 | **0** | **0** | **未改（一行不动）** |

（路径均相对 fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/）

### 1.2 ForgeGui.java —— 常量换成函数

**删除**（原 :54-56，`RESET_LEFT` 定义）：

    /**
     * 左列每帧起点:原版已画「血量行」(height-39) ⇒ 防御条(顶替护甲条)取 height-49。
     * @see ForgeGui 类头
     */
    public static final int RESET_LEFT = 49;

**新增**：

- `:53-57` 两个累加量字段（未变）
- `:59-63` 原版左列基线常量（javadoc 59-62、常量 63）

        /**
         * 原版左列基线:血量行的 y 是 {@code screenHeight - 39}({@code Gui.java:867})。
         * 左列种子从这里往上叠加「心排数 × 行距」,见 {@link #leftSeed}。
         */
        private static final int VANILLA_HEALTH_BASE = 39;

- `:65-68` 行高常量（javadoc 65-67、常量 68）

        /**
         * 原版行高上限:护甲行在血量行之上 {@code 10} 像素({@code Gui.java:872} 末尾的 {@code - 10})。
         */
        private static final int VANILLA_ROW_HEIGHT = 10;

- `:70-119` `leftSeed(...)`（javadoc 70-112 含原版原文/字节码偏移/displayHealth 说明；方法体 113-119）

        public static int leftSeed(float maxHealthAttribute, int displayHealth, int health, float absorption) {
            float f = Math.max(maxHealthAttribute, (float) Math.max(displayHealth, health));
            int absorb = Mth.ceil(absorption);
            int rows = Mth.ceil((f + (float) absorb) / 2.0F / 10.0F);
            int rowHeight = Math.max(10 - (rows - 2), 3);
            return VANILLA_HEALTH_BASE + (rows - 1) * rowHeight + VANILLA_ROW_HEIGHT;
        }

- `:3` 新增 `import net.minecraft.util.Mth;`（原文件无 import）
- `:22-46` 类头 `<h2>Fabric 侧的差异…</h2>` 改写：从「起点不是 39」改为「左列种子必须每帧复算，不是常量」，并写清「写死 49 时吸收心那一排会被整排盖掉」；`RESET_RIGHT` / `RESET_RIGHT_NO_AIR` 两条 bullet 保持前序语义（气泡行由 `airRowShown` 判定）
- `:121-131` 两个右列常量的注释按「前序右列修复」同步（`RESET_RIGHT` 121-125、`RESET_RIGHT_NO_AIR` 127-131）

git diff 关键 hunk（`git diff -U2` 原文摘录）：

    -    public static final int RESET_LEFT = 49;
    +    private static final int VANILLA_HEALTH_BASE = 39;
    +    private static final int VANILLA_ROW_HEIGHT = 10;
    +    public static int leftSeed(float maxHealthAttribute, int displayHealth, int health, float absorption) {
    +        float f = Math.max(maxHealthAttribute, (float) Math.max(displayHealth, health));
    +        int absorb = Mth.ceil(absorption);
    +        int rows = Mth.ceil((f + (float) absorb) / 2.0F / 10.0F);
    +        int rowHeight = Math.max(10 - (rows - 2), 3);
    +        return VANILLA_HEALTH_BASE + (rows - 1) * rowHeight + VANILLA_ROW_HEIGHT;
    +    }

### 1.3 FabricClientBridges.java —— 复位值改为复算

- `:26` `import net.minecraft.client.Minecraft;`
- `:30` `import net.minecraft.client.player.LocalPlayer;`
- `:31` `import net.minecraft.util.Mth;`
- `:32` `import net.minecraft.world.entity.ai.attributes.Attributes;`
- `:135-142` 复位注释改写（左列=复算种子、写死 49 会盖掉吸收心那排、右列上限 59、复位必须早于 overlay render）
- `:143` 复位语句：

        ForgeGui.INSTANCE.leftHeight = leftSeedOfCameraPlayer();
        ForgeGui.INSTANCE.rightHeight = ForgeGui.RESET_RIGHT;   // :144 不变（前序右列语义）

- `:155-176` 新增私有方法（`:166` 起）：

        private static int leftSeedOfCameraPlayer() {
            if (!(Minecraft.getInstance().getCameraEntity() instanceof LocalPlayer player)) {
                return ForgeGui.leftSeed(20.0F, 20, 20, 0.0F);          // 回退 = 49（主菜单/切维度那一帧）
            }
            int health = Mth.ceil(player.getHealth());                   // 原版 :843 的 i
            return ForgeGui.leftSeed((float) player.getAttributeValue(Attributes.MAX_HEALTH),
                    health, health, player.getAbsorptionAmount());
        }

git diff 关键 hunk：

    -            ForgeGui.INSTANCE.leftHeight = ForgeGui.RESET_LEFT;
    +            ForgeGui.INSTANCE.leftHeight = leftSeedOfCameraPlayer();

### 1.4 CombatPowerHud.java —— 一行未改（前序右列改动原样保留）

    :109        int y = height - gui.leftHeight;
    :110        drawRow(guiGraphics, DEFENSE_FULL, DEFENSE_HALF, width / 2 - 91, y, defense, false);
    :112        gui.leftHeight += 10;

`git diff` 该文件**只有前序右列修复的 hunk**（`:122` 攻击条 `int base = airRowShown(...)`、`:129` `gui.rightHeight = base + ROW_HEIGHT;`、`:158-191` 的 `airRowShown`），防御条这三行在 diff 里**零命中** ⇒ 前序工作未被我覆盖，左列仍走「读累加量 + 画完自增」模型。

### 1.5 全仓一致性

`RESET_LEFT` 在 fabric-1.20.1 / forge-1.20.1 / neoforge-1.21.1 / neoforge-26.1.2 四线 src 下检索 = **0 处**（旧常量的引用已彻底摘净）。

## 2. 原版取证（禁止凭记忆，逐行抄录）

### 2.1 反编译源码（Mojang 映射，本线 build/moddev 产物）

来源：forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar → net/minecraft/client/gui/Gui.java

    :843          int i = Mth.ceil(player.getHealth());
    :861          int k = this.displayHealth;
    :867          int k1 = this.screenHeight - 39;
    :868          float f = Math.max((float)player.getAttributeValue(Attributes.MAX_HEALTH), (float)Math.max(k, i));
    :869          int l1 = Mth.ceil(player.getAbsorptionAmount());
    :870          int i2 = Mth.ceil((f + (float)l1) / 2.0F / 10.0F);          // 心排数
    :871          int j2 = Math.max(10 - (i2 - 2), 3);                       // 行距
    :872          int k2 = k1 - (i2 - 1) * j2 - 10;                          // 护甲行（左列要顶替的行）
    :873          int l2 = k1 - 10;                                           // 气泡行起点
    :930          l2 -= 10;                                                   // 在 if (k5 == 0) 饱食度块末尾
    :937          int j6 = this.getVisibleVehicleHeartRows(k5) - 1;
    :938          l2 -= j6 * 10;
    :944          ...blit(..., j1 - j5 * 8 - 9, l2, 16, 18, 9, 9);           // 气泡（满）
    :946          ...blit(..., j1 - j5 * 8 - 9, l2, 25, 18, 9, 9);           // 气泡（半）
    :971-975      renderHearts：j = ceil(maxHealth/2)、k = ceil(absorption/2)
    :977-984      心排循环 i2 = y - j1 * height；if (currentHealth + absorptionAmount <= 4) i2 += this.random.nextInt(2);
    :787-801      getPlayerVehicleWithHealth()：getVehicle() instanceof LivingEntity 才返回
    :810-822      getVehicleMaxHearts()：(int)(maxHealth + 0.5F)/2，上限 30；showVehicleHealth() 为假则 0
    :831-833      getVisibleVehicleHeartRows(v) = (int)Math.ceil((double)v / 10.0D)

### 2.2 fabric 线**实际运行**的那份合并 jar 的字节码复核

- 定位方式：`fabric-1.20.1/build/loom-cache/argFiles/runClient` 的 classpath 条目（runClient 实际加载的 jar），不是猜：

      .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-83c93f4af0/
      1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/minecraft-merged-83c93f4af0-....jar
      sha256 前缀 d9128e827b7c385c…（与 minecraft-merged-0b0f9d549f 逐字节相同：同 sha256、同 26,689,291 B）

- `javap -p -c -l net.minecraft.client.gui.Gui`（该 jar 内已是具名 Mojang 映射，非 class_329）→ `renderPlayerHealth` 偏移：

      229-236  k1 = screenHeight - 39
      238-256  f = max(attrMax, max(displayHealth, ceil(health)))
      258-265  l1 = ceil(absorption)
      267-282  i2 = ceil((f + l1) / 2 / 10)
      284-295  j2 = max(10 - (i2 - 2), 3)
      297-310  k2 = k1 - (i2 - 1) * j2 - 10
      312-317  l2 = k1 - 10
      752      iinc 18, -10                （饱食度块末尾 l2 -= 10）
      804-822  j6 = visibleRows(k5) - 1; l2 -= j6 * 10

  ⇒ 与 2.1 的源码**逐条对应**，公式取证成立。完整反汇编留档：work/evidence/fabric-defensebar/Gui.runtime-merged-jar.javap.txt（5369 行，`renderPlayerHealth` 从第 3009 行起）。

- 说明：代码注释中原稿把该 jar 写成 `minecraft-merged-2a6f3185c8`，复核发现那份（25,693,651 B）**不是** runClient classpath 里加载的那份；已按实测 classpath 条目订正为 `83c93f4af0`（同 sha256 的 `0b0f9d549f` 亦可），并附 sha256 前缀。

### 2.3 推导

    护甲行 k2 = (H - 39) - (心排数-1) * 行距 - 10
    ⇒ 距屏幕底偏移 seed = 39 + (心排数-1) * 行距 + 10        ← 即 leftSeed() 的返回值

    心排数 rows   = ceil((max(attrMax, max(displayHealth, ceil(health))) + ceil(absorption)) / 2 / 10)
    行距 rowHeight = max(10 - (rows - 2), 3)

取值表（与实现一一对应）：

| 心排数 | rowHeight | seed（距底） | 帧末 leftHeight（seed+10） | 防御条 y |
|---|---|---|---|---|
| 1（20 血，无吸收） | 10 | 49 | 59 | H-49 |
| 2（20 血 + 吸收 8） | 10 | 59 | 69 | H-59（= 原版护甲行） |
| 3（44 血） | 9 | 67 | 77 | H-67 |
| 4 | 8 | 73 | 83 | H-73 |

### 2.4 三条容易算错的边界（逐条核过）

1. **低血抖动不进公式**：`Gui.java:982-984` 的 `random.nextInt(2)` 只加在**心图标的 y** 上（字节码在 renderHearts 内部），`i2`/`j2` 早已在 :870/:871 算完 ⇒ 不参与行数。
2. **displayHealth 的语义与取不到的事实**：`Gui.displayHealth` 是 `:139` 的 `protected int`，1.20.1 无 getter（全 jar 检索 `getDisplayHealth` = 0 处），桩件也不在 `net.minecraft.client.gui` 包内 ⇒ 调用方以 `Mth.ceil(getHealth())` 代替。它只会在「最大生命被下调、且 1 秒内刚掉过血」的窗口里让 `f` 偏大，且必须跨过 20 点界才改变心排数（详见 7. 未核验项）。
3. **骑乘不影响左列**：`k2` 只依赖 `k1`/`i2`/`j2`，不含 `k5`、`l2`；载具血量只影响「饱食度行是否绘制」与「气泡行 l2 偏移」。
   - 顺带把气泡行的实测值也解释清了：非骑乘（k5=0）时 `getVisibleVehicleHeartRows(0) = 0 ⇒ j6 = -1 ⇒ l2 -= (-10)`，把 :930 减掉的 10 又加回来 ⇒ 气泡行实测在 **H-49 = 221**（不是 H-59）。骑乘 1 行载具心时 `j6 = 0`，饱食度块被跳过，l2 仍在 221。两种情形气泡行同为 H-49，与前序右列修复的判据一致。

## 3. 构建

### 3.1 首次构建（改码后）

    ./gradlew.bat :fabric-1.20.1:build --console=plain -x pushToGame
    > Task :fabric-1.20.1:compileJava / jar / remapJar / build
    pushToRootBuild: pushed astral_dice-1.5.0-hotfix+fabric_1.20.1.jar -> F:\MCProject\astral_dice_multiloader\build\libs
    pushToDevRun:    pushed astral_dice-1.5.0-hotfix+fabric_1.20.1.jar -> F:\MCProject\astral_dice_multiloader\run\fabric-1.20.1\mods
    BUILD SUCCESSFUL in 3s

产物：fabric-1.20.1/build/libs/astral_dice-1.5.0-hotfix+fabric_1.20.1.jar = **2,945,260 B**（2026-11-02 19:08）。同一目录下旧包 astral_dice-1.5.0+fabric_1.20.1.jar（2,944,296 B，14:14）未动。

### 3.2 构建后的注释订正 + 重建（全量可复现）

订正内容仅限**注释**（把 2.2 说的 jar 指纹改成实测 classpath 那份）：

    ./gradlew.bat :fabric-1.20.1:build --console=plain -x pushToGame
    > Task :fabric-1.20.1:compileJava / jar / remapJar / build / pushToRootBuild / pushToDevRun
    BUILD SUCCESSFUL in 8s

产物：**2,945,258 B**（19:17）。

「只改注释、不改字节码」的证明（不靠自述）：把订正前后的两个源文件分别用 javac（Zulu 21）对着同一份 merged jar 编译，比对 `javap -p -c` 输出：

    instr-equal: True   2218 == 2218 字符（两个变体的指令序列逐字相同）
    classfile sha a/b: bd6cd6f05f87da77 / c26e728952364ca8（整文件不同 = 仅调试行号表随注释行数位移）

jar 内 class 指纹（`work/evidence/fabric-defensebar/jar-class-sha.txt`）：

| class | 首次构建（19:08） | 重建（19:17） |
|---|---|---|
| client/CombatPowerHud.class | 09e9a2a728abe27e65f7f6d3 | 09e9a2a728abe27e65f7f6d3（未变） |
| platform/client/FabricClientBridges.class | 04072c7a91de7fd50d35909f | 04072c7a91de7fd50d35909f（未变） |
| platform/client/gui/overlay/ForgeGui.class | 88f7423e92ab53c7a9c409a2 | 98aa6f3e865e676b53f37a95（仅调试行号位移） |

### 3.3 实机取证对应的构建

第 4/5 节的读数与截图取自 **19:08 那次构建**（客户端进程 38684 的 `:fabric-1.20.1:runClient`，19:08:51 启动）。19:17 的重建为注释订正，指令序列逐字相同（见 3.2），故取证对当前工作树仍然成立；**但当前 jar 未再跑一遍实机**（见 7. 未核验项）。

## 4. AutoJS 读数（客户端侧 eval，六态）

采集方式（AutoJS `http://127.0.0.1:25585/mcp`，tools/call `eval_js` side=client）：

- 状态驱动：客户端侧 `Minecraft.getInstance().getSingleplayerServer()` → `createCommandSourceStack()` → `getCommands().performPrefixedCommand(ss, "...")`（返回值 1 = 成功；注：`/attribute @a …` 在该运行时返回 0，`@p` 返回 1，故统一走 `@p`/`@a` 实测选可用的那个）
- 读数：`ForgeGui.INSTANCE.leftHeight` / `rightHeight` **帧末**（即防御条 `+= 10`、攻击条写回之后），并同时读 `seed`、`defenseY = H - (leftHeight-10)`、`attackY = H - (rightHeight-10)`、`health`/`attrMax`/`absorption`/`defense`/`attack`/`air`/`eyeInWater`/`vehicle`
- 屏幕：H = 270（guiScale 4.0，1920x1080 ⇒ 480x270 gui）

| # | 状态 | 触发 | attrMax/health | absorption | seed | **leftHeight（帧末）** | 防御条 y | rightHeight（帧末） | 攻击条 y | defense/attack |
|---|---|---|---|---|---|---|---|---|---|---|
| ① | 单排心 | 默认 | 20/20 | 0.0 | 49 | **59** | **221** = H-49 | 59 | 221 | 2 / 1 |
| ② | 吸收心两排 | effect give @a absorption 3600 1 | 20/20 | 8.0 | 59 | **69** | **211** = H-59 | 59 | 221 | 2 / 1 |
| ③ | 低血（抖动窗口） | max_health 4 + setHealth(4) | 4/4 | 0.0 | 49 | **59** | **221** | 59 | 221 | 2 / 1 |
| ④ | 骑乘 | summon horse + mount | 20/20 | 0.0 | 49 | **59** | **221** | 59 | 221 | 2 / 1 |
| ⑤ | 三排心（额外态） | max_health 44 | 44/44 | 0.0 | **67** | **77** | **203** = H-67 | 59 | 221 | 2 / 1 |
| ⑥ | 水下（气泡行显示） | 玻璃盒注水，eyeInWater=true，air 165/300 | 20/20 | 0.0 | 49 | **59** | **221** | **69** | **211** = H-59 | 2 / 1 |

对照任务给定期望（帧末）：

- 单排心 `leftHeight = 59`（防御条 H-49）⇒ **实测 59 / 221 ✔**
- 两排吸收心 `leftHeight = 69`（防御条 H-59 = 原版护甲行）⇒ **实测 69 / 211 ✔**
- 「现状两态都是 59 ⇒ 修复后必须能区分」⇒ **实测 59 vs 69，已区分 ✔**
- 额外态 ⑤ 用于验证 `rowHeight = max(10-(rows-2),3)` 的另一分支：rows=3 ⇒ 行距 9 ⇒ seed 67 ⇒ leftHeight 77 / y 203（若行距仍写死 10 应为 69/211）⇒ **行距分支也走对了**
- `defense = 2` 的来源：`DiceCombatModifiers.defensePowerOf` = floor(2 + 有效护甲×0.30 + 0.85×韧性)（DiceCombatModifiers.java:850），裸装即 2 ⇒ 始终 ≥1，绘制条件成立；`attack = 1` 同理。

## 5. 像素核验（不是只看数值）

方法：`Screenshot.grab(mc.gameDirectory, "<name>.png", mc.getMainRenderTarget(), c)`（**文件名必须自带 .png**——该方法原样当文件名用，不加扩展名；grab 后等 ~2s 再复制，否则 0 字节）→ 模板匹配：取 `defense_full` 等 PNG 的**中心 4x4 采样**，每通道阈值 ≤10/255，扫描整屏命中点；再按命中点算 gui 坐标（dev/4）。行带重叠用 `_bands.py` 逐像素比较 alpha>0 的 y 区间。

| 截图 | defense_full（gui 坐标 / 命中像素） | 心行 | 其它 | 结论 |
|---|---|---|---|---|
| single_defense.png | (149, **221**) 66/66 | heart_full ×10 y=**231** | food ×10 y=231；attack_half (322,**221**) 45/45 | 防御条 y = 饱食度 y **-10** ✔（差一行）；右列陆地回归 H-49 ✔ |
| absorb2_defense.png | (149, **211**) 66/66 | heart_absorb_full ×4 **y=221**（x=149/157/165/173）＋ heart_full ×10 y=231 | attack_half (322,221) | 防御条比单排心**再上移 10 gui px** ✔（221→211）；见下「零交叠」 |
| lowhp_defense.png | (149, **221**) 66/66 | heart_full ×2 y=231 | attack_half (322,221) | 低血抖动不改行位 ✔（与 ① 同 y） |
| rows3_defense.png | (149, **203**) 66/66 | heart_full 三行 y=**213 / 222 / 231** | attack_half (322,221) | 防御条在最高心行 213 上方 10 gui px ✔ |
| ride_defense.png | (149, **221**) 66/66 | heart_full ×10 y=231 | food_full / air_full / attack_half **NO MATCH** | 骑乘：原版不画饱食度行（改画载具心），右列攻击条按前序设计不画；防御条仍 H-49 ✔ |
| underwater_attackbar.png | (149, **221**) 66/66 | heart_full ×10 y=231 | air_full ×6 **y=221**；food ×10 y=231；attack_half (322,**211**) 45/45 | **右列回归：水下（气泡行显示）攻击条 H-59 = 211** ✔；气泡行实测 H-49 = 221 |

**零交叠判据（② 两排吸收心）**，逐行带（gui px）：

    defense        gui_y [211, 220)
    heart_absorb   gui_y [221, 230)
    heart_full     gui_y [231, 240)
    defense vs heart_absorb  y_overlap_dev = 0 px  ⇒ ZERO-OVERLAP（间隔 4 设备 px = 1 gui px）

**攻击条回归（前序修复不被破坏）**：陆地 `attack_half = (322,221) = H-49`；水下 `attack_half = (322,211) = H-59`；与 19:10/19:12 的既有对照截图（work/shots/fabric-attackbar/underwater_air_row.png）重测结果一致（attack 211、air 221、defense 221）。

## 6. 截图清单（work/shots/fabric-defensebar/）

| 文件 | 字节 | 状态 |
|---|---|---|
| single_defense.png | 1,141,920 | ① 单排心 |
| absorb2_defense.png | 1,119,981 | ② 吸收心两排（abs 8.0） |
| lowhp_defense.png | 1,143,670 | ③ 低血 4/4 |
| rows3_defense.png | 1,169,800 | ⑤ 三排心（max 44） |
| ride_defense.png | 1,337,175 | ④ 骑乘（horse） |
| underwater_attackbar.png | 1,047,814 | ⑥ 水下（air 165/300） |

（源目录 fabric-1.20.1/run/client/screenshots/ 保留同名文件，便于复跑比对。）

## 7. 未核验项（据实列出）

1. **displayHealth 的窗口**：本线桩件读不到 `Gui.displayHealth`（`protected`、无 getter、包外不可见），调用方以 `ceil(getHealth())` 代入。理论差异只在「最大生命被下调到当前生命以下 + 1 秒内刚掉过血」这一窗口，且需跨 20 点界才改变心排数。**未构造该窗口实测**。探针建议：`max_health` 10 → 先满血 20（此刻 attrMax=10 < health=20 不成立，需反向：先 20 血再把 max 下调到 10，然后 1 秒内 `/damage` 到 19），在同一 tick 内读 `leftHeight` 与 `health`；若出现「health=19、displayHealth=20」则本线会取 rows=1（种子 49），原版取 rows=2（种子 59）——差异 10 px。
2. **骑乘 + 多排载具心**：只实测了 1 行载具心（马 20 血）。2 行载具心（载具 max ≥ 20 心，如高血马/其他生物）会改 `j6` 与气泡行、**不改 k2**；该结论由源码 + 字节码得出，**未实机**。探针建议：`/summon minecraft:horse` 后 `attribute <uuid> minecraft:generic.max_health base set 40`（或给 biolume/其他高血载具），骑乘后读 `leftHeight`（应仍 59）与气泡行 y。
3. **旁观 / 第三人称相机实体非 LocalPlayer**：走回退分支（种子 49，不画防御条的那一帧也无害），**未实测**。
4. **水下 + 吸收心同帧**：两态分别验过（② / ⑥），**组合态未实测**；按公式二者独立（左列只看 i2/j2，右列只看气泡行），风险低。
5. **19:17 重建后的 jar 未再跑实机**：重建仅注释订正，指令序列逐字相同（3.2 的双编译证明），故未重复整套实机。
6. **guiScale 只验了 4.0**：H/行位结论与 guiScale 无关（全部是 gui 坐标），但其他 guiScale 的像素证据未采集。

## 8. 收尾证据

- 端口：取证前 `25565/25585` 均无监听（PORTS FREE）；结束后 `netstat -ano | grep LISTENING` 对 25565/25585 = **NONE**（仅剩 25585 的 TIME_WAIT 残留连接）
- 进程：`Stop-Process -Force` 停 **38684**（net.fabricmc.devlaunchinjector.Main，即 dev client）与 **3280**（gradle wrapper `:fabric-1.20.1:runClient`）；`jcmd -l` 复核只剩 **35180 GradleDaemon 9.5.1**——按纪律**未杀 daemon**
- 锁：本轮创建的 F:/MCProject/.agent-locks/dsh.lock 已删除（目录内只剩他人的 `_b1_*.py`）
- 工作树：三文件仍为 `M`（未 commit / 未 push / 未打 tag）；其余仓库改动（docs、tools、models 等）与本轮无关，未触碰
- 临时文件：取证用的 `.tmp/` 已清理，关键证据移入 work/evidence/fabric-defensebar/（Gui 反汇编 5369 行 + jar 内 class 指纹）
- 实机副作用已复原：水下玻璃盒 fill air（36 + 114 格）、马已 kill、`max_health` 回 20、health 回 20

## 9. 结论

**修复成立**：S1' 已落地——左列种子由写死 49 改为按 1.20.1 `Gui#renderPlayerHealth` 逐字等价复算（源码与 fabric 实跑合并 jar 字节码双证）；六态实机读数证明 ① 单排心 leftHeight=59 / 防御条 H-49、② 吸收心两排 leftHeight=69 / 防御条 H-59 且与吸收心像素**零交叠**、⑤ 三排心 67/77 证实行距分支，右列攻击条陆地 H-49 / 水下 H-59 回归通过，构建通过（2,945,258 B），前序右列改动原样保留、CombatPowerHud.java 一行未改。**未证**：displayHealth 抖动窗口、骑乘多排载具心、旁观相机实体、水下+吸收心组合、19:17 重建后 jar 的实机复跑（重建经双编译证明指令逐字相同）、guiScale≠4.0 的像素证据。