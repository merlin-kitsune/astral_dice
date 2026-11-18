# Astral Dice 1.5.0 四线对齐审计报告（fabric-1.20.1 移植线）

- 审计日期：2026-11-01
- 审计对象：`F:/MCProject/astral_dice_multiloader`（git-bash 路径 `/f/MCProject/astral_dice_multiloader`）
- 分支 / HEAD：`multi-main` / `fd990c15`（实读 git）
- 审计性质：**只读**。不修改源码/资源/脚本，不 commit / push / stash，不跑 gradle，不启动游戏。
- 唯一写入产物：`porting/fabric-1.20.1/ALIGNMENT-AUDIT-2026-11-01.md`（本文件）。

---

## §0 口径与方法

### 0.1 四线定义（自包含子项目，各自一份 `src/main/java` + `src/main/resources`）

| 代号 | 目录 | 角色 | MC / 加载器 | `src/main/java` 下 .java 文件数 |
|---|---|---|---|---|
| P0 / n121 | `neoforge-1.21.1` | 主基准 | 1.21.1 / NeoForge | 411 |
| FG / fg | `forge-1.20.1` | 次基准 | 1.20.1 / Forge (LexForge) | 403 |
| N26 / n26 | `neoforge-26.1.2` | 第三条 | 26.1.2 / NeoForge | 426 |
| FB / fb | `fabric-1.20.1` | 本次移植目标 | 1.20.1 / Fabric | 526 |

四条线的 Java 根包一致：`com/merlinkitsune/astral_dice/`。计数器：

```bash
cd /f/MCProject/astral_dice_multiloader
for L in neoforge-1.21.1 forge-1.20.1 neoforge-26.1.2 fabric-1.20.1; do
  printf '%-20s %s\n' "$L" "$(find $L/src/main/java -name '*.java' | wc -l)"
done
```
```
```text
neoforge-1.21.1      411
forge-1.20.1         403
neoforge-26.1.2      426
fabric-1.20.1        526
```

### 0.2 审计口径（用户裁决，本次强制遵守）

1. **2026-11-01 裁决「一律以最新主线版本为准」**：`neoforge-1.21.1` 为主基准、`forge-1.20.1` 为次基准；旧 1.3.2-hotfix 行为**全部放弃**，不作对齐依据。
2. **1.20.1 两线（forge + fabric）允许且必须剔除 1.21+ 才存在的原版内容**（例：复仇戟触发效果里的蓄风 / 盘丝 / 渗浆 / 寄生、袭击之兆 / 试炼之兆）。这类「少」**不算分叉**，但要求 Java 代码 + 三语 lang **同时**不含残留。
3. **Curios 在 fabric 走 `compat/curios` 门面**（Accessories 主源 + Trinkets 兜底）视为**等价实现**；饰品 validator 闸门比 P0 多一层属**有意保留**，不算分叉。

### 0.3 工作树状态（实读，非本次改动）

```bash
cd /f/MCProject/astral_dice_multiloader && git status --porcelain | head -30
```
```
```text
 M AGENTS.md
 M docs/AGENTS.md
 M docs/agent/AGENTS-dsh.md
 M fabric-1.20.1/build.gradle
 D fabric-1.20.1/src/generated/resources/data/astral_dice/recipes/... （多条配方 JSON）
```

开工时已存在的未提交改动**不属于本次审计**（另一批次正在做配方删除与文档同步）。本审计全程未触碰这些路径。

### 0.4 方法

- 环境：Windows git-bash + Windows CPython（`python3`）。**关键坑**：CPython 是 Windows 侧二进制，脚本里的路径必须写 `F:/MCProject/...`；bash 只用 `/f/MCProject/...`。
- 全部结论由**文本级静态取证**得出：`grep -rn` / `sed -n` / 自写 python 正则扫描（先剥离 `/* */` 与 `//` 注释，再计数与做集合比对）。
- 本次**没有** `javap` 字节码级取证，也**没有**构建与实机。涉及运行时语义的结论一律下沉到 §4「无法判定」。
- 跨线正文比对方法：剥离注释 / import / 空行后按行归一化再 diff，用于判断「逻辑是否同源」而不是「文本是否逐字节相同」。
- 显示坑：CJK 直接 print 到 Windows 控制台会乱码，故含中文的比较只在 python 内部做，屏上只打计数与行号。

### 0.5 结论口径定义

| 口径 | 含义 |
|---|---|
| **对齐** | 四线语义等价（允许平台 API 差异、映射名差异、行号不同）。 |
| **分叉(有理由)** | 与 P0 不同，但差异由平台限制或用户裁决明确授权（例：fabric 饰品门面、平台 lang 键）。 |
| **分叉(缺陷)** | 与 P0 不同且**无授权理由**，会造成行为 / 内容缺失。 |
| **本线特有** | 其他三条线没有、本线为实现等价性而新增的东西（例：fabric 的版本握手包）。 |
| **无法判定** | 静态取证不足以定性，需实机或字节码级证据（§4 单列）。 |

### 0.6 复跑模板

```bash
cd /f/MCProject/astral_dice_multiloader
cat <<'PYEOF' | PYTHONIOENCODING=utf-8 python3 -
import os, re
ROOT = 'F:/MCProject/astral_dice_multiloader'
LINES = {'n121':'neoforge-1.21.1','fg':'forge-1.20.1','n26':'neoforge-26.1.2','fb':'fabric-1.20.1'}
def strip(t):
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    return re.sub(r'//[^\n]*', '', t)
# 替换为各节给出的具体扫描逻辑
PYEOF
```

---

## §1 结论总表

| # | 项目 | 四线状态 | 判据 | 是否需要修 |
|---|---|---|---|---|
| R1 | 轨道炮 / 激光 FX 下发链路（`sendBeamFx`） | 四线同源，均走 `BeamFxPayload` + `BeamFxRenderer` | §2.1.1 | 否 |
| R2 | Beam FX 五个常量 | 四线**逐值相同** | §2.1.2 | 否 |
| R3 | `BeamFxPayload` 字段与序列化语义 | 四线同构：6×double + int + float + varInt | §2.1.3 | 否 |
| R4 | 客户端 `BeamFxRenderer` 渲染管线 | 观感常量相同，API 实现按平台分支 | §2.1.4 | 否（观感待 U1） |
| R5 | 死常量 `BEAM_PARTICLES = 84` | **仅 n26 存在**，全仓 0 处代码引用 | §2.1.5 | 是（第三线 P-4，低） |
| R6 | 复仇戟 `hasAttackTriggerEffect` / `hasDefenseTriggerEffect` 效果集 | P0 = 10 + 7 项；fg / fb = 6 + 5 项（剔除 1.21+ 效果），两线互相逐字节一致 | §2.2.1 | 否（允许剔除） |
| R7 | 1.21+ 原版效果残留（java / 三语 lang / 帕秋莉手册三处） | fg / fb **三处均 0 命中** | §2.2.2–2.2.4 | 否 |
| R8 | 本批常量与公式（11 个文件） | 四线**逐值相同**（行号见 §2.3） | §2.3 | 否 |
| R9 | `BankCardUnlimitedChipItem.ALLY_RANGE` 与发奖范围 | n121 / fg / fb 有 32.0 且做距离过滤；**n26 无该常量、无距离判定** | §2.3.9 | 是（第三线 P-1，高） |
| R10 | 内容面数量（物品/方块/效果/音效/粒子/实体/创造栏/lang） | 物品 157 四线同；方块 4；音效 23；粒子 2；实体 2；效果 n26 +1；创造栏 n26 −2；lang fabric +7 平台键 | §2.4 | fabric 否；n26 见 P-5 |
| R11 | 饰品等价面（fabric `compat/curios` 门面） | 调用面 100% 覆盖；槽位尺寸同口径；validator 多一层为有意 | §2.5 | 否（分叉(有理由)） |
| R12 | 事件总线接线（fabric `LoaderBus` vs P0 `@EventBusSubscriber`） | 88 个含 `@SubscribeEvent` 的 fabric 类全部在 104 处 `register` 里 | §2.6.1 | 否 |
| R13 | 网络消息 id 分配 | fg 29 个（0–28）、fb 30 个（0–29），`id++` 单调、**无冲突**；fb 相对顺序与 fg 的 21/22/23/26 一致 | §2.6.3 | 否 |
| R14 | 已定义未注册 / 已注册未使用 | fb 整类缺 `TargetSelectCloseMessage`；n26 的 `CardChoice*Payload` 有类未注册却被发送 | §2.6.3 | 是（fb 缺陷 C-2 / n26 缺陷 P-2） |
| R15 | 阿兰娜立牌被动「每次赐福触发 +5 攻击」 | n121 / fg 经 `fireBlessingTriggered` 生效；**fabric 触发块内无任何 Alana 调用** | §2.6.2 | 是（fabric C-1，高） |
| R16 | `SignHookDispatcher` 活性与 `SignHooks` 实现面 | n121 / fg 11 个 `fire*` 均有调用者；fabric / n26 **0 个调用者（整类死代码）** | §2.6.2 | 是（fabric C-3，中） |

**计数**：对齐 **10** 项（R1–R4、R6–R8、R10–R13）；分叉(有理由) **1** 项（R11）；缺陷 **5** 项（R5、R9、R14、R15、R16，展开为 §3 的 9 条：fabric 4 条 + 第三线 5 条）；无法判定 **9** 项（§4）。


---

## §2 逐项证据

### 2.1 轨道炮与激光 FX（必查项 1）

#### 2.1.1 下发链路：四线都走 `sendBeamFx` → `BeamFxPayload` → `BeamFxRenderer`

判据命令：

```bash
cd /f/MCProject/astral_dice_multiloader
for L in neoforge-1.21.1 forge-1.20.1 neoforge-26.1.2 fabric-1.20.1; do
  echo "=== $L"
  grep -n 'sendBeamFx\|new BeamFxPayload' $L/src/main/java/com/merlinkitsune/astral_dice/combat/DamageCardFxScheduler.java
done
```
```text
=== neoforge-1.21.1
340:        sendBeamFx(p, color);
376:    private static void sendBeamFx(Pending p, int color) {
381:        BeamFxPayload payload = new BeamFxPayload(
=== forge-1.20.1
342:        sendBeamFx(p, color);
378:    private static void sendBeamFx(Pending p, int color) {
383:        BeamFxPayload payload = new BeamFxPayload(
=== neoforge-26.1.2
346:        sendBeamFx(p, color);
382:    private static void sendBeamFx(Pending p, int color) {
387:        BeamFxPayload payload = new BeamFxPayload(
=== fabric-1.20.1
339:        sendBeamFx(p, color);
379:    private static void sendBeamFx(Pending p, int color) {
384:        BeamFxPayload payload = new BeamFxPayload(
```

归一化 diff（剥离注释 / import / 空行后与 P0 对比）的**残余差异行数**：

| 文件 | n121 vs fg | n121 vs n26 | n121 vs fb |
|---|---|---|---|
| `combat/DamageCardFxScheduler.java` | 22 | 38 | 27 |
| `network/BeamFxPayload.java` | 65 | 2 | 65 |
| `client/BeamFxRenderer.java` | 62 | 91 | 61 |

差异逐条归属（全部为平台 API / 类形态差异，**无逻辑差异**）：

- `DamageCardFxScheduler`：订阅注解写法（`@EventBusSubscriber` vs `@Mod.EventBusSubscriber`）、常量块在源码中的**排列位置**（值相同，见 2.1.2）、`Pending` 构造器换行、以及 26.1.2 把 `ParticleTypes.FLASH` 换成可染色形态 `ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF)`（同一白色闪光）。
- `BeamFxPayload`：1.21+ 是 `record` + `StreamCodec`，1.20.1 两线是 `final class` + 静态 `encode/decode/handle`；n26 与 n121 只差 `ResourceLocation` → `Identifier`。
- `BeamFxRenderer`：资源 id 构造（`fromNamespaceAndPath` vs `new ResourceLocation`）、1.20.1 的 `RenderStateShard` 常量受保护需包一层 `States`、26.1.2 的 `RenderPipeline` 改写。

fabric 侧 2026-11-01 的对齐记录（该文件自身 javadoc）：

```bash
sed -n '370,392p' fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DamageCardFxScheduler.java
```

结论：**R1 对齐**。fabric 与 forge 同源（同一 `sendBeamFx` 语义），此前 fabric 曾每 tick 撒 84 枚尘且不发 `BeamFxPayload`（导致 `BeamFxRenderer` 成为死代码）的问题已在本批修掉。

#### 2.1.2 五个常量：四线逐值相同

```bash
for L in neoforge-1.21.1 forge-1.20.1 neoforge-26.1.2 fabric-1.20.1; do
  echo "=== $L"
  grep -nE '(LASER_WIDTH|BEACON_WIDTH|BEAM_TTL_TICKS|BEAM_HEIGHT|BEAM_TICKS)\s*=' \
    $L/src/main/java/com/merlinkitsune/astral_dice/combat/DamageCardFxScheduler.java
done
```
```text
=== neoforge-1.21.1
103:    public static final int BEAM_TICKS = 20;
123:    private static final double BEAM_HEIGHT = 96.0D;
125:    private static final float LASER_WIDTH = 0.05F;
127:    private static final float BEACON_WIDTH = 0.45F;
129:    private static final int BEAM_TTL_TICKS = 3;
=== forge-1.20.1
103:    public static final int BEAM_TICKS = 20;
108:    private static final double BEAM_HEIGHT = 96.0D;
110:    private static final float LASER_WIDTH = 0.05F;
112:    private static final float BEACON_WIDTH = 0.45F;
114:    private static final int BEAM_TTL_TICKS = 3;
=== neoforge-26.1.2
107:    public static final int BEAM_TICKS = 20;
112:    private static final double BEAM_HEIGHT = 96.0D;
132:    private static final float LASER_WIDTH = 0.05F;
134:    private static final float BEACON_WIDTH = 0.45F;
136:    private static final int BEAM_TTL_TICKS = 3;
=== fabric-1.20.1
103:    public static final int BEAM_TICKS = 20;
108:    private static final double BEAM_HEIGHT = 96.0D;
110:    private static final float LASER_WIDTH = 0.05F;
112:    private static final float BEACON_WIDTH = 0.45F;
114:    private static final int BEAM_TTL_TICKS = 3;
```

行号对照（完整路径）：

| 常量 | neoforge-1.21.1 | forge-1.20.1 | neoforge-26.1.2 | fabric-1.20.1 |
|---|---|---|---|---|
| BEAM_TICKS = 20 | `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DamageCardFxScheduler.java:103` | `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DamageCardFxScheduler.java:103` | `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/combat/DamageCardFxScheduler.java:107` | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DamageCardFxScheduler.java:103` |
| BEAM_HEIGHT = 96.0D | …/DamageCardFxScheduler.java:123 | …:108 | …:112 | …:108 |
| LASER_WIDTH = 0.05F | …:125 | …:110 | …:132 | …:110 |
| BEACON_WIDTH = 0.45F | …:127 | …:112 | …:134 | …:112 |
| BEAM_TTL_TICKS = 3 | …:129 | …:114 | …:136 | …:114 |

（表格中「`…`」表示与同列首行相同的目录前缀。）

结论：**R2 对齐**。

#### 2.1.3 `BeamFxPayload` 字段与序列化语义

```bash
for L in neoforge-1.21.1 forge-1.20.1 neoforge-26.1.2 fabric-1.20.1; do
  echo "--- $L"
  grep -n 'record BeamFxPayload\|writeDouble\|writeFloat\|writeVarInt' \
    $L/src/main/java/com/merlinkitsune/astral_dice/network/BeamFxPayload.java | head -12
done
```
```text
--- neoforge-1.21.1
14:public record BeamFxPayload(double fromX, double fromY, double fromZ,
26:        buf.writeDouble(p.fromX);
31:        buf.writeDouble(p.toZ);
33:        buf.writeFloat(p.widthBlocks);
34:        buf.writeVarInt(p.ttlTicks);
--- neoforge-26.1.2
14:public record BeamFxPayload(double fromX, double fromY, double fromZ,
26:        buf.writeDouble(p.fromX);
31:        buf.writeDouble(p.toZ);
33:        buf.writeFloat(p.widthBlocks);
34:        buf.writeVarInt(p.ttlTicks);
--- forge-1.20.1
19:    private final double fromX;      (…to ttlTicks: 19–27 逐字段)
29:    public BeamFxPayload(double fromX, double fromY, double fromZ,
44:        buf.writeDouble(msg.fromX);
--- fabric-1.20.1
25:    private final double fromX;      (…to ttlTicks: 25–33 逐字段)
35:    public BeamFxPayload(double fromX, double fromY, double fromZ,
50:        buf.writeDouble(msg.fromX);
```

字段顺序四线一致：`fromX, fromY, fromZ, toX, toY, toZ, colorArgb(int), widthBlocks(float), ttlTicks(int)`；编码 = 6×`writeDouble` + `writeInt` + `writeFloat` + `writeVarInt`，解码同序。归一化 diff：n121 vs n26 仅 2 行（`ResourceLocation`→`Identifier`）；n121 vs fg / fb 各 65 行，全部是 record → final class 的结构改写与访问器命名（`p.fromX()` → `msg.fromX`）。

1.20.1 两线额外带客户端侧守卫后再调渲染器（同形）：

```bash
grep -n 'FMLEnvironment.dist\|BeamFxRenderer.add' \
  fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/network/BeamFxPayload.java
```

结论：**R3 对齐**（线格式逐字节等价，跨线可互解）。

#### 2.1.4 `BeamFxRenderer` 渲染管线：只影响实现，不影响观感常量

四线相同的观感常量：`LASER_TEXTURE = astral_dice:textures/special/blank.png`、`BEACON_TEXTURE = minecraft:textures/entity/beacon_beam.png`、`BEACON_COLOR = 0xFFCF4A`、`BEACON_V_TILE = 8.0D`、`RenderType` 名 `astral_dice_laser_beam` / `astral_dice_orbital_beam`；几何算法（轴叉乘视图方向求侧向偏移、`side1/side2 = width/2`、alpha ≤ 0.01F 兜底为 1.0F、`v1 = beacon ? len / BEACON_V_TILE : 1.0F`、顶点序 `a0,a1,b1,b0`、u = 0/1）四线文本一致。

挂载方式（平台差异，非观感差异）：

```bash
grep -n 'onRenderLevelStage\|AFTER_ENTITIES' neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/client/BeamFxRenderer.java
grep -n 'onRenderLevelStage\|AFTER_ENTITIES' forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/BeamFxRenderer.java
grep -n 'onRenderLevelStage\|AFTER_ENTITIES' fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/BeamFxRenderer.java
grep -n 'RegisterRenderPipelinesEvent\|SubmitCustomGeometryEvent' neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/client/BeamFxRenderer.java
grep -n 'BeamFxRenderer' fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/AstralDiceClient.java
```
```text
n121  :107  public static void onRenderLevelStage(RenderLevelStageEvent event)
n121  :108  if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
fg    :132  public static void onRenderLevelStage(RenderLevelStageEvent event)
fg    :133  if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
fb    :137  public static void onRenderLevelStage(RenderLevelStageEvent event)
fb    :138  if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
n26   :106  public static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event)
n26   :134  public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event)

---

### 2.3 本批已落地的常量与公式（必查项 3）

取证方式：每个文件用同一正则抽取常量声明行，四线并排；下表「行号」列即各线该常量所在行。列标题为**相对仓库根的完整路径**。

#### 2.3.1 `item/chip/WarpEngineChipItem.java`（传送门冷却 / 充能 / 加速时长）

| 常量 | 值 | neoforge-1.21.1/…/item/chip/WarpEngineChipItem.java | forge-1.20.1/… | neoforge-26.1.2/… | fabric-1.20.1/… |
|---|---|---|---|---|---|
| `STACK_GAIN` | 2 | :36 | :36 | :36 | :34 |
| `SPEED_DURATION_TICKS` | 200 | :38 | :38 | :38 | :36 |
| `PORTAL_WAYSTONE_COOLDOWN_TICKS` | `20 * 60 * 3` | :40 | :40 | :40 | :39 |

使用点四线同构：`ModAttachments.setWarpEnginePortalCooldownEnd(player, now + PORTAL_WAYSTONE_COOLDOWN_TICKS)` 与 `ChargeManager.addStacks(player, STACK_GAIN)`（fabric 对应 :66 / :71，forge 与 n121 对应 :67 / :72，n26 :67 / :72）。

#### 2.3.2 `item/chip/BookmarkChipItem.java`（`DAMAGE_BONUS`）

| `DAMAGE_BONUS = 2` | n121 :17 | fg :17 | n26 :17 | fb :18 |
|---|---|---|---|---|

#### 2.3.3 `trade/EmeraldDiceTrade.java`（绿宝石骰子商店折扣 → 1:1）

```bash
cd /f/MCProject/astral_dice_multiloader
grep -n 'Math.max(1' neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java \
    forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java \
    fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java
```
```text
neoforge-1.21.1/…/trade/EmeraldDiceTrade.java:67: return new ItemCost(ModItems.STAR_COIN.get(), Math.max(1, cost.count()));
forge-1.20.1/…/trade/EmeraldDiceTrade.java:67:      return new ItemStack(ModItems.STAR_COIN.get(), Math.max(1, stack.getCount()));
fabric-1.20.1/…/trade/EmeraldDiceTrade.java:67:      return new ItemStack(ModItems.STAR_COIN.get(), Math.max(1, stack.getCount()));
```

形式差异有明确理由：n121/n26 有 `ItemCost` + `MerchantOfferMixin`，1.20.1 两线必须用 `MerchantOffers` + NBT `transformOffers`（`ClientboundMerchantOffersPacket` 在 context 关闭后才编码）。数值语义四线同一：**1 绿宝石 = 1 星币**。fabric 该行上方带溯源 javadoc（`对齐 forge-1.20.1/…/EmeraldDiceTrade.java:67(2026-10-29 用户裁决:0.8 折扣 → 1:1)`，见 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java:65`）。

#### 2.3.4 `item/sign/NancyLuSignItem.java`（主动技：固定 +6、不消耗卡牌）

| 常量 | 值 | n121 | fg | n26 | fb |
|---|---|---|---|---|---|
| `ACTIVE_ATTACK_BONUS` | 6 | :54 | :55 | :54 | :58 |
| `ACTIVE_DURATION_TICKS` | 2400 | 同段 | 同段 | 同段 | 同段 |
| `HIDDEN_DURATION_TICKS` | 600 | 同段 | 同段 | 同段 | 同段 |
| `ENDER_PEARL_IMMUNE_TICKS` | 20 | 同段 | 同段 | 同段 | 同段 |
| `PASSIVE_BONUS` / `PASSIVE_RANGE` | 3 / 3.0 | 同段 | 同段 | 同段 | 同段 |

施加点（同一行文本，仅行号不同）：

```bash
grep -n 'setNancyLuActiveBonus(player, ACTIVE_ATTACK_BONUS)' \
  */src/main/java/com/merlinkitsune/astral_dice/item/sign/NancyLuSignItem.java
```
```text
neoforge-1.21.1/…/item/sign/NancyLuSignItem.java:162
forge-1.20.1/…/item/sign/NancyLuSignItem.java:164
neoforge-26.1.2/…/item/sign/NancyLuSignItem.java:154
fabric-1.20.1/…/item/sign/NancyLuSignItem.java:165
```

不消耗卡牌这一点用反向判据确认（四线均 0 命中）：

```bash
for L in neoforge-1.21.1 forge-1.20.1 neoforge-26.1.2 fabric-1.20.1; do
  printf '%-18s %s\n' "$L" "$(grep -cE 'consume|shrink|removeItem' \
     $L/src/main/java/com/merlinkitsune/astral_dice/item/sign/NancyLuSignItem.java)"
done
```
```text
neoforge-1.21.1     0
forge-1.20.1        0
neoforge-26.1.2     0
fabric-1.20.1       0
```

#### 2.3.5 `item/dice/DiceTier.java` 的 `cardSlots` 四分量

`DiceTier` 是 `public record DiceTier(String id, Supplier<Item> item, IntUnaryOperator chipBonus, int cardSlots)`，具体值落在 13 条 `DiceTierRegistry.register(new DiceTier(...))`（在 `item/ModItems.java` 内）。四线逐一取值相同：

| 骰子 | cardSlots | chipBonus |
|---|---|---|
| dice | 4 | `s -> s` |
| golden_dice | 8 | `1 + s` |
| glass_dice | 8 | `1 + s` |
| diamond_dice | 10 | `2 + s` |
| netherite_dice | 12 | `3 + s` |
| emerald_dice | 10 | `2 + s` |
| obsidian_dice | 10 | `2 + s` |
| netherrack_dice | 8 | `1 + s` |
| weird_dice | 10 | `2 + s` |
| crimson_dice | 12 | `3 + s` |
| amethyst_dice | 10 | `2 + s` |
| ender_dice | 12 | `3 + s` |
| nether_star_dice | 12 | `4 + s` |

注册块行号：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java:246-283`、`forge-1.20.1/…/item/ModItems.java:248-285`、`neoforge-26.1.2/…/item/ModItems.java:247-284`、`fabric-1.20.1/…/item/ModItems.java:245-282`。fabric 的 `item/dice/DiceTier.java` 带溯源 javadoc（cardSlots 分量对齐 forge `DiceTier.java:30`，13 条注册对齐 forge `ModItems.java:246`）。

#### 2.3.6 `item/chip/RailgunChipItem.java`（全套 8 个常量）

四线**行号完全相同**：

```bash
for L in neoforge-1.21.1 forge-1.20.1 neoforge-26.1.2 fabric-1.20.1; do
  echo "=== $L"
  grep -nE '(CHARGE_REQUIRED|ATTACK_BONUS|AOE_RADIUS|STRIKE_DELAY_TICKS|COOLDOWN_TICKS|STRIKE_DAMAGE_BASE|STRIKE_DAMAGE_RATIO|STRIKE_DAMAGE_MIN) =' \
    $L/src/main/java/com/merlinkitsune/astral_dice/item/chip/RailgunChipItem.java
done
```
```text
（四线输出逐字节相同）
47:    public static final int CHARGE_REQUIRED = 6;
49:    public static final int ATTACK_BONUS = 5;
51:    public static final int AOE_RADIUS = 3;
53:    public static final int STRIKE_DELAY_TICKS = 20;
55:    public static final int COOLDOWN_TICKS = 20 * 60;
57:    public static final float STRIKE_DAMAGE_BASE = 5.0F;
59:    public static final float STRIKE_DAMAGE_RATIO = 0.5F;
61:    public static final float STRIKE_DAMAGE_MIN = 5.0F;
```

#### 2.3.7 `item/chip/AirbagChipItem.java`

| 常量 | 值 | n121 | fg | n26 | fb |
|---|---|---|---|---|---|
| `CHARGE_COST` | 6 | :32 | :32 | :29 | :33 |
| `COOLDOWN_TICKS` | `20 * 60` | :34 | :34 | :31 | :35 |
| `HIT_CHARGE_GAIN` | 1 | :36 | :36 | :26 | :37 |
| `HIT_CHARGE_COOLDOWN_TICKS` | `20 * 15` | :38 | :38 | :27 | :39 |

（n26 的两条 `HIT_*` 排在其他常量之前，属源码排布差异；值为同。）

#### 2.3.8 星光学常量族（EagleScope / MemberRecommendation / StarCoinHammer / BankCardUnlimited）

| 文件 | 常量 | 值 | n121 | fg | n26 | fb |
|---|---|---|---|---|---|---|
| `item/chip/EagleScopeChipItem.java` | `DAMAGE_PER_MARK` | 2 | :17 | :17 | :17 | :17 |
| `item/chip/MemberRecommendationChipItem.java` | `HAND_CARD_LIMIT` | 5 | :21 | :21 | :21 | :21 |
| `item/chip/StarCoinHammerChipItem.java` | `THRESHOLD_COINS` | 32 | :25 | :26 | :25 | :26 |
| 同上 | `CONSUME_COINS` | 18 | :27 | :28 | :27 | :28 |
| 同上 | `ATTACK_RATIO` | `0.30` | :29 | :30 | :29 | :30 |
| 同上 | `MAX_ATTACK_BONUS` | 100 | :32 | :33 | :32 | :33 |
| 同上 | `COINS_PER_BAG` | 9 | :34 | :35 | :34 | :35 |
| 同上 | `STARLIGHT_ON_EQUIP` | 5 | :36 | :37 | :36 | :37 |
| `item/chip/BankCardUnlimitedChipItem.java` | `STARLIGHT_ON_EQUIP` | 3 | :24 | :25 | :23 | :24 |
| 同上 | `REWARD_COINS` | 3 | :27 | :28 | :26 | :27 |
| 同上 | `ALLY_RANGE` | `32.0` | :30 | :31 | **缺失** | :30 |

#### 2.3.9 `BankCardUnlimitedChipItem` 的 `ALLY_RANGE`：n26 独自分叉（缺陷 P-1）

```bash
grep -n 'ALLY_RANGE' neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BankCardUnlimitedChipItem.java \
  forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BankCardUnlimitedChipItem.java \
  neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/item/chip/BankCardUnlimitedChipItem.java \
  fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/chip/BankCardUnlimitedChipItem.java
```
```text
neoforge-1.21.1/…/BankCardUnlimitedChipItem.java:30:    public static final double ALLY_RANGE = 32.0;
forge-1.20.1/…/BankCardUnlimitedChipItem.java:31:       public static final double ALLY_RANGE = 32.0;
fabric-1.20.1/…/BankCardUnlimitedChipItem.java:30:      public static final double ALLY_RANGE = 32.0;
（neoforge-26.1.2：0 命中）
```

`onBlessingEnd` 主体：n121 `:75-95` / forge `:76-96` / fabric `:75-95` 三份**正文相同**（有队伍时取队伍成员，无队伍时按 `sp.distanceToSqr(player) <= ALLY_RANGE * ALLY_RANGE` 判距离，自己永远在内）；n26 改为 `sp == player || allies.contains(sp)`，**无距离判定**，其 javadoc 明写「未组队时为全服在线玩家」。

结论：**R9 = 分叉(缺陷) P-1（高，第三线）**；fabric 与本线基准一致，**无需修改**。

---

### 2.4 内容面数量（必查项 4）

计数脚本（剥离注释后统计注册调用字面量）：

```bash
cat <<'PYEOF' | PYTHONIOENCODING=utf-8 python3 -
import os, re
ROOT='F:/MCProject/astral_dice_multiloader'
L={'n121':'neoforge-1.21.1','fg':'forge-1.20.1','n26':'neoforge-26.1.2','fb':'fabric-1.20.1'}
def strip(t):
    t=re.sub(r'/\*.*?\*/','',t,flags=re.S)
    return re.sub(r'//[^\n]*','',t)
def read(line, rel):
    p='%s/%s/src/main/java/com/merlinkitsune/astral_dice/%s'%(ROOT,line,rel)
    return s

---

### 2.5 饰品等价面（必查项 5）

#### 2.5.1 fabric 门面的形状

```bash
ls -l fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/compat/curios/ \
      fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/compat/accessories/
wc -l fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/compat/curios/*.java
```
```text
CuriosApi.java 410   ICurioItem.java 62   ICurioStacksHandler.java 37   ICursiosItemHandler.java 29
IItemHandler.java 12  SlotContext.java 25  SlotResult.java 7  TrinketBridge.java 115   （合计 697 行）
compat/accessories/AccessoriesCompat.java  compat/accessories/AccessoriesClientIconCheck.java
```

门面契约（类头 javadoc 自述 + 代码核对）：**读** = 两个源按优先级取并集（Accessories 优先，Trinkets 兜底）；**写** = `getStacksHandler(id)` 在同名槽位两边都存在时返回 `MergedHandler`，读/写优先作用于「当前持有内容的一边」，而**槽位修饰符两侧同写**，保证两边槽位数一致；只有一边存在该槽位时直接透传。

```bash
grep -n 'public static Optional<ICursiosItemHandler> getCuriosInventory\|public static Optional<ICurioStacksHandler> getStacksHandler\|public static Map<String, ICurioStacksHandler> getCuriosMap' -A6 \
  fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/compat/curios/CuriosApi.java | head -40
```
```text
89:    public static Optional<ICursiosItemHandler> getCuriosInventory(LivingEntity entity) {
94:        if (ACCESSORIES_LOADED) { AccessoriesCompat.getInventory(entity).ifPresent(sources::add); }
99:        if (TRINKETS_LOADED) { TrinketsCompat.getCuriosInventory(entity).map(View::new).ifPresent(sources::add); }
103:        if (sources.isEmpty()) return Optional.empty();
106:        return Optional.of(sources.size() == 1 ? sources.get(0) : new Aggregate(sources));
110:    public static Optional<ICurioStacksHandler> getStacksHandler(LivingEntity entity, String identifier)
115:    public static Map<String, ICurioStacksHandler> getCuriosMap(LivingEntity entity)
```

「两个饰品前置至少装一个」由 `init/ModCompatibilityCheck#verifyAccessoryProviderOrThrow()` 在 mod 初始化最早期强制（Fabric 的 `depends` 是 AND 语义，两个都只能进 `recommends`）。类加载隔离：`ACCESSORIES_LOADED` / `TRINKETS_LOADED` 是静态布尔，门面签名里**不出现任何** Accessories/Trinkets 类型。

#### 2.5.2 调用面覆盖

```bash
for L in neoforge-1.21.1 forge-1.20.1 fabric-1.20.1; do
  echo "=== $L"
  grep -rho 'CuriosApi\.[a-zA-Z]*\|CuriosCompat\.[a-zA-Z]*' $L/src/main/java --include=*.java | sort | uniq -c | sort -rn
done
```
```text
n121: 95 CuriosApi.getCuriosInventory ; 23 CuriosApi.registerCurio
fg  : 96 CuriosCompat.getCuriosInventory
fb  : 113 CuriosApi.getCuriosInventory ; 1 CuriosApi.getStacksHandler
```

P0 实际只用 `getCuriosInventory` + `registerCurio`；fabric 门面覆盖这两者，并额外提供 `getStacksHandler` / `getCuriosMap` / `findFirstCurio` 等。**fabric 消费方没有调用任何门面未实现的 API**。

#### 2.5.3 动态槽位尺寸（首饰/筹码/骰子栏随物品增减）

两侧同口径：绝对 `AttributeModifier` + `update()`（不是 Curios 的 `grow/shrink` 相对累加）。

```bash
sed -n '256,275p' neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceCurioItem.java
sed -n '248,276p' fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/dice/DiceCurioItem.java
```
```text
n121:270  handler.removeModifier(CHIP_SLOT_MODIFIER);
n121:271  handler.addPermanentModifier(new AttributeModifier(CHIP_SLOT_MODIFIER, wanted,
n121:272          AttributeModifier.Operation.ADD_VALUE));
n121:273  handler.update();

fb:29   private static final UUID CHIP_SLOT_MODIFIER = UUID.fromString("a5d1c9e2-6f34-4b7a-8c21-0e5d9b3f7a64");
fb:30   private static final String CHIP_SLOT_MODIFIER_NAME = "astral_dice:chip_slots";
fb:271  handler.removeModifier(CHIP_SLOT_MODIFIER);
fb:272  handler.addPermanentModifier(new AttributeModifier(CHIP_SLOT_MODIFIER, CHIP_SLOT_MODIFIER_NAME, wanted,
fb:273          AttributeModifier.Operation.ADD_VALUE));
fb:274  handler.update();
```

差异仅在 1.20.1 的 `AttributeModifier` 需要 `UUID + name`（1.21+ 用 `ResourceLocation` id）。`CURIO_LEGACY_MODIFIER` 清理（旧 grow/shrink 累加器残留迁移）四线都有：n121 `:260-261`、fabric `:261-262`。

槽位收缩归还（`forceRemove = true` 时把槽内物品退回背包）与「无骰子强制复位」`enforceNoDiceState` 在四线同一个方法体内（n121 与 fabric 的 `setSlotCount(..., forceRemove)` / `enforceNoDiceState` 结构一致）。

#### 2.5.4 行为特征指纹（整行命中数：n121 / fg / n26 / fb）

| 特征 | n121 | fg | n26 | fb | 说明 |
|---|---|---|---|---|---|
| 蹲 + 右键自动装备 | 4 | 4 | 4 | 4 | `BaseChipItem / DiceCurioItem / TargetSelectionClient` 同点 |
| 自动装备调用面 | 45 | 45 | 45 | 55 | fabric 多出的 10 处在 `compat/accessories/AccessoriesCompat` 与 `compat/curios/TrinketBridge`（双通道适配成本） |
| 死亡不掉落 | 2 | 2 | 2 | 3 | 四线都有 `event/CurioDeathKeepHandler.java` |
| 槽位图标 | 4 | 7 | 4 | 7 | fabric 经 `compat/accessories/AccessoriesClientIconCheck`（Accessories 侧）+ `event/AstralEventRegistry` + 平台 lang 键 |

结论：**R11 = 分叉(有理由)** —— fabric 用门面等价实现，且**多一层饰品 validator 闸门属有意保留**（用户 2026-11-01 裁决）。静态可判定的等价性见上；运行时等价性见 §4-U3…U6。

---

### 2.6 事件与网络接线（必查项 6）

#### 2.6.1 fabric 的 LoaderBus 显式登记 vs P0 的 `@EventBusSubscriber`

判据（先剥注释再匹配，避免 javadoc 里的注解字样污染计数）：

```bash
cat <<'PYEOF' | PYTHONIOENCODING=utf-8 python3 -
import os, re
ROOT='F:/MCProject/astral_dice_multiloader'
def strip(t):
    t=re.sub(r'/\*.*?\*/','',t,flags=re.S); return re.sub(r'//[^\n]*','',t)
def scan(line):
    base='%s/%s/src/main/java'%(ROOT,line); ann=set(); reg=set()
    for dp,dn,fn in os.walk(base):
        for f in fn:
            if not f.endswith('.java'): continue
            p=os.path.join(dp,f); t=strip(open(p,encoding='utf-8',errors='replace').read())
            cls=f[:-5]
            if re.search(r'@(Mod\.)?EventBusSubscriber',t): ann.add(cls)
            for m in re.finditer(r'LoaderBus\.INSTANCE\.register\(\s*([A-Za-z0-9_.]+)\.class',t):
                reg.add(m.group(1).rsplit('.',1)[-1])
    return ann,reg
a0,_=scan('neoforge-1.21.1'); af,rf=scan('fabric-1.20.1')
print('n121 annotated=%d fabric registered=%d'%(len(a0),len(rf)))
print('P0-only:',sorted(a0-rf)); print('fabric-only:',sorted(rf-a0))
PYEOF
```
```text
n121 annotated=86 fabric registered=86
P0-only: ['CardTraderTrades','ChipRecipesEnabledCondition','ChipTraderTrades','CombatPowerHud','DataGenerators',
          'EffectTooltipDescriptions','GlobalEventGate','HiddenCurseEnchantment','IcebergTooltipCacheGuard',
          'ModEntityEvents','ModPayloads','ModernUITooltipCompat','MosesEnigmaticLink']
fabric-only: ['AlanaSignItem','AstralDiceMod','ClientEvents','ClientKeyNames','ClientSessionEvents','DeepseekSignItem',
              'DorothySignItem','EventEffects','FannySignItem','JillSignItem','NetherrackDiceHandler',
              'ObsidianDiceHandler','VitaminPillChipItem']
```

**两个方向的差集都必须逐类归因**，否则「86 vs 86」只是数字巧合。逐类核对结果：

| P0 有注解但 fabric 未登记 | fabric 侧的实际接线 | 是否丢失 |
|---|---|---|
| `CardTraderTrades` / `ChipTraderTrades` | `AstralDiceMod` 显式调 `CardTraderTrades.register()` / `ChipTraderTrades.register()`（该两文件 0 个 `@SubscribeEvent`） | 否 |
| `HiddenCurseEnchantment` | `AstralDiceMod` 调 `HiddenCurseEnchantment.register()` | 否 |
| `ModEntityEvents` | `AstralDiceMod` 调 `ModEntityEvents.bootstrap()`（`FabricDefaultAttributeRegistry`） | 否 |
| `ModPayloads` | fabric 无此类；对应物是 `network/ModNetwork.register()`，由 `AstralDiceMod` 调用 | 否 |
| `DataGenerators` | fabric 无此类；对应物 `datagen/AstralDataGenerator.java` | 否 |
| `CombatPowerHud` | 0 个 `@SubscribeEvent`；由 `client/ModClientEvents.java:56/58` 以 overlay 注册（`"defense_bar", CombatPowerHud.DEFENSE_OVERLAY` / `"attack_bar", CombatPowerHud.ATTACK_OVERLAY`） | 否 |
| `IcebergTooltipCacheGuard` / `ModernUITooltipCompat` | 0 个 `@SubscribeEvent`；由 `mixin/bridge/ClientTooltipBridgeMixin` 直调（`beginRender/endRender/reset`） | 否 |
| `GlobalEventGate` | 0 个 `@SubscribeEvent`；由 `event/AstralEventDispatcher` + `platform/FabricBridges` 直调 | 否 |
| `EffectTooltipDescriptions` | fabric 无此类；`mixin/client/EffectRenderingInventoryScreenMixin.java:34` 注明它只存在于 1.21.1 / 26.1.2 | 否 |
| `MosesEnigmaticLink` | fabric 无此类；`enigmatic` 在 fabric 只出现在 `combat/DiceCombatEvents.java`（逻辑内联） | 否 |
| `ChipRecipesEnabledCondition` | fabric 只有占位类，0 处引用；javadoc 明写 Fabric 1.20.1 无配方条件基础设施（`ResourceCondition` 是 1.21.2+），且四条线的配方 JSON 里 `"conditions"` 命中为 0 ⇒ 行为无差 | 否 |

| fabric 登记但 P0 无注解 | 实情 |
|---|---|
| `ClientSessionEvents` / `EventEffects` / `NetherrackDiceHandler` / `ObsidianDiceHandler` / `VitaminPillChipItem` | fabric 侧各有 1–3 个真实 `@SubscribeEvent` 处理器，登记有效（属平台事件面不同，不是缺陷

---

## §3 缺陷清单（按严重度）

> 分级：**高** = 玩家可感知的行为/内容缺失或可能卡死操作；**中** = 结构性死代码，当前不发作但会持续误导后续移植；**低** = 清洁度问题。
> 「范围」列区分 **fabric 本线**（本次移植要修）与 **第三线 26.1.2**（旁证，不属本次 fabric 范围，但同一模式会回流）。

| 编号 | 严重度 | 范围 | 一句话 | 证据锚点 |
|---|---|---|---|---|
| C-1 | 高 | fabric | 阿兰娜立牌「每次赐福 +5 攻击」完全不可达 | §2.6.2 |
| C-2 | 高 | fabric | 缺 `TargetSelectClose` 关闭包与 `cancel(ServerPlayer, String)` 重载 ⇒ 事件卡动画窗后客户端可能仍停在选择模式 | §2.6.3 |
| C-3 | 中 | fabric | `SignHookDispatcher` 11 个 `fire*` 全无调用者（整类死代码），`SignHooks` 只剩 1 个实现者且接不上 | §2.6.2 |
| C-4 | 低 | fabric | 8 个类被 `LoaderBus` 登记但零 `@SubscribeEvent` 处理器（空登记） | §2.6.1 |
| P-1 | 高 | 26.1.2 | `BankCardUnlimitedChipItem` 缺 `ALLY_RANGE`/距离判定 ⇒ 未组队时**全服在线玩家**都发奖励 | §2.3.9 |
| P-2 | 高 | 26.1.2 | `CardChoiceConfirmPayload` / `CardChoiceClosePayload` **有类未注册**却被发送 | §2.6.3 |
| P-3 | 中 | 26.1.2 | 与 C-1/C-3 同源：阿兰娜被动同样不可达 + dispatcher 死代码 | §2.6.2 |
| P-4 | 低 | 26.1.2 | `BEAM_PARTICLES = 84` 死常量 | §2.1.5 |
| P-5 | 低 | 26.1.2 | 创造栏缺 `ATTACK_CARD_BITE`/`ATTACK_CARD_DRAGON_ROAR`、效果多 `railgun_ready`（需确认是「领先」还是「缺口」） | §2.4 |

### C-1（高，fabric）阿兰娜立牌被动不可达

- **现象**：`AlanaSignItem.BONUS_PER_TRIGGER = 5` 的累加只在 `onBlessingTriggered` 内，而 fabric 的赐福触发块不调用它，`SignHookDispatcher` 也无调用者。
- **复跑**：`grep -rn 'AlanaSignItem\.\|SignHookDispatcher\.fire' fabric-1.20.1/src/main/java` ⇒ 只有 `clearBonus` 与 `new AlanaSignItem(...)`，无触发调用。
- **影响**：佩戴阿兰娜立牌时「鼓起勇气」被动永不累加 ⇒ 该立牌在 fabric 变成纯无被动装备（数值直接弱于 P0/fg）。
- **修法**：在 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:594-599` 触发块内补一行 `AlanaSignItem.onBlessingTriggered(player);`（最小改动），或整体改回 `SignHookDispatcher.fireBlessingTriggered(player)`（见 B3）。
- **复验判据**：`grep -c 'AlanaSignItem.onBlessingTriggered' fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java` = 1；实机：戴立牌触发一次赐福后攻击力 +5，`SignAttachments` 的 alana bonus +5。

### C-2（高，fabric）缺 `TargetSelectClose` 关闭包

- **现象**：fabric 的 `target/TargetSelectionManager` 只有 `cancel(ServerPlayer, int token)`（:292）与 `cancelSessionForTests(Player)`（:158），没有 `cancel(ServerPlayer, String reason)`；网络层也没有 `TargetSelectCloseMessage`（forge 侧 `ModNetwork.java:119-120` / 类体 `:471`）。
- **调用点**：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/EventCardService.java:88-95` 用 `cancelSessionForTests(sp)` 顶替，注释自称「不发关闭包 ⇒ 客户端可能仍停在选择模式」。
- **影响**：事件卡动画窗开始时，服务端清掉会话但**不通知客户端**；客户端 `TargetSelectionClient` 仍自认在选择模式 ⇒ 吞鼠标 / 左键被当成确认，直到玩家自行点击或本地超时。基准线在 2026-10-29 专门补过这个包，说明是真实踩过的坑。
- **修法**：见 B2（补包 + 补重载 + 替换调用点 + 客户端处理）。
- **复验判据**：`grep -rn 'TargetSelectClose' fabric-1.20.1/src/main/java` 应出现 ≥3 处（类定义 / 注册 / 发送 / 处理）；实机：触发事件卡动画窗后立刻左键，不应被吞。

### C-3（中，fabric）`SignHookDispatcher` 整类死代码

- **现象**：11 个 `fire*` 方法四线签名一致，但 `SignHookDispatcher.fire*` 在 fabric 与 n26 **0 处调用**；`SignHooks` 接口在 fabric 只剩 `AlanaSignItem` 一个实现者（forge 有 7 个实现者：`AlanaSignItem:25 / FenSignItem:38 / HannaSignItem:73 / MamushiSignItem:116 / MosesSignItem:46 / NancyLuSignItem:49 / ZhaoSignItem:103`）。
- **影响**：不是当前可见 bug，但它使「立牌钩子」这套机制在 fabric 名存实亡：新增钩子时会照着接口写实现却永远不触发（C-1 就是这么发生的）。
- **修法**：二选一 —— **恢复分发**（把 fabric 的直调改回 `fire*`，回归面最小且能顺带修掉 C-1）或**删干净**（删 dispatcher + 接口 + 各立牌的 `implements`）。建议前者。
- **复验判据**：python 断言每个 `public static .* fire[A-Z][A-Za-z]*\(` 在 `SignHookDispatcher` 之外至少有 1 处调用。

### C-4（低，fabric）零处理器空登记

- **名单**：`AlanaSignItem / AstralDiceMod / ClientEvents / ClientKeyNames / DeepseekSignItem / DorothySignItem / FannySignItem / JillSignItem`。
- **影响**：`LoaderBus.register` 对「无 `@SubscribeEvent` 的类」不抛错（这点比 NeoForge 21.1 宽松，后者会拒绝），所以不崩、只是空转；但它掩盖了「我以为登记了就生效」的错觉 —— `AlanaSignItem` 正是典型。
- **修法**：清掉空登记；或让 `LoaderBus.register` 在 `SubscriptionAudit` 里对「登记但零处理器」发出 WARN。

### P-1（高，26.1.2）`BankCardUnlimitedChipItem` 缺距离过滤

- **证据**：`grep -n 'ALLY_RANGE' neoforge-26.1.2/…/chip/BankCardUnlimitedChipItem.java` ⇒ 0；其 `onBlessingEnd` 只判 `sp == player || allies.contains(sp)`。
- **影响**：未组队时**全服在线玩家**都能拿到该奖励（`STARLIGHT_ON_EQUIP = 3` / `REWARD_COINS = 3`），是经济与体验双重问题。
- **修法**：补 `ALLY_RANGE = 32.0` + `distanceToSqr` 判定，与另三线同构。

### P-2（高，26.1.2）选卡界面两个包未注册

- **证据**：`network/CardChoiceConfirmPayload.java` / `CardChoiceClosePayload.java` 存在；`grep -c 'CardChoice' neoforge-26.1.2/…/network/ModPayloads.java` = **0**；发送点 `choice/CardChoiceManager.java:199`、`screen/CardChoiceScreen.java:304` 仍在。
- **影响**：NeoForge 的 payload 必须在注册表登记后才能 send，未注册即抛异常 ⇒ 选卡界面的「确认」与「服务端关闭」在 26.1.2 上应不可用（具体抛点需实机确认，见 §4-U9）。
- **修法**：在 `ModPayloads.register` 里补这两条（对照 n121 `ModPayloads.java:64-66 / :70-72`）。

### P-3（中，26.1.2）与 C-1 同源的钩子丢失

- n26 的 `DiceCombatEvents` 同样只直调个别立牌，`SignHookDispatcher` 0 调用者 ⇒ 阿兰娜被动在 26.1.2 同样不可达。fabric 的写法是**从 n26 抄来的**（同一模式），这也是它为何会漏。

### P-4（低，26.1.2）`BEAM_PARTICLES` 死常量

- 见 §2.1.5：唯一声明在 `neoforge-26.1.2/…/combat/DamageCardFxScheduler.java:114`，无引用。

### P-5（低，26.1.2）内容面差额

- 创造栏少 `ATTACK_CARD_BITE` / `ATTACK_CARD_DRAGON_ROAR`；效果多 `railgun_ready`；lang 多 24 键（含村民/番尼事件/3 条手册页）。
- 需要产品口径确认：若 26.1.2 是「领先线」，创造栏缺两张攻击卡就是缺口；若两张卡在该线被有意下线，需在 CHANGELOG 记一笔。

---

## §4 无法判定（需实机）

> 下列项**静态取证不足**：或依赖 GPU/渲染管线行为，或依赖第三方前置（Accessories / Trinkets）的运行时语义，或依赖包注册失败时的抛点。每项给出「实机判据」，便于后续实机批次直接照做。

| 编号 | 无法判定的问题 | 为什么静态判不了 | 实机判据 |
|---|---|---|---|
| U1 | 激光/轨道炮观感是否与 P0 一致（1.20.1 `RenderType` 加法混合 vs 1.21.1 同名管线 vs 26.1.2 `RenderPipeline`） | 混合/深度/裁剪参数文本相同，但两代渲染栈的 alpha 与 mip 行为不由常量决定 | 四线各开一炮，同角度同距离截图对比：光柱宽度占比、信标纹理纵向平铺（`BEACON_V_TILE = 8.0`）、边缘是否硬切 |
| U2 | 光束去重与过期（`sameAs` 去重、`expireTick`、`ttlTicks = 3`）的实际表现 | 依赖同 tick 多次下发与帧率 | 连续点击两次同一目标，观察是否只出一条光柱；连续开火观察光柱是否闪烁/残影 |
| U3 | 动态槽位尺寸在 Accessories / Trinkets 两侧**是否真的即时 resize** | `AttributeModifier` 语义由前置实现，1.20.1 的 `update()` 触发链不同 | 分别只装 Accessories / 只装 Trinkets：换骰子（4→12 槽）后不重登，槽位数应立即变化；重登后仍为目标值 |
| U4 | `MergedHandler` 双源并存时「谁是持有方」的读写归属 | 需要同一槽位两边都有内容的运行时状态 | 两前置都装：把筹码分别放进 Accessories 侧与 Trinkets 侧，验证功能都生效、且槽位数一致 |
| U5 | 蹲右键自动装备的界面落点 | 门面 javadoc 声称「主源方向取 Accessories」，实际由前置 UI 决定 | 只装 Accessories 时蹲右键芯片 ⇒ 应进 Accessories 界面；只装 Trinkets 时 ⇒ 进 Trinkets 界面 |
| U6 | 死亡不掉落（`CurioDeathKeepHandler`）在两套前置下的实际保留 | 前置对死亡掉落有自己的处理顺序 | 戴骰子+筹码死亡一次：物品应保留（四线同口径）；再验证玻璃骰子的死亡惩罚仍生效 |
| U7 | C-2 场景下客户端是否真的卡在选择模式 | 依赖客户端状态机与输入接管时序 | 触发事件卡动画窗（`EventCardService` 路径）后立刻左键：不应被吞；再对照修前版本复现「被吞」 |
| U8 | 跨线协议兼容（id 顺序 / `VersionGate` 拒绝路径） | 需要双端实际握手 | 旧版 fabric 客户端连新版服务端（以及反向）：应被 `VersionGate` 明确拒绝而不是错包；fabric↔forge 不互通（协议不同，属预期） |
| U9 | n26 发送未注册 payload 时**是否真的抛异常**、抛在哪一层 | 需要 NeoForge 运行时或反编译注册表校验逻辑 | 在 26.1.2 dev 环境点一次「确认选卡」，看是否抛 payload 未注册类异常；若仅告警则 P-2 降级为「功能静默失效」 |

---

## §5 建议修复批次

| 批次 | 范围 | 内容 | 前置 | 完成判据 |
|---|---|---|---|---|
| **B1** | fabric | 修 C-1：在赐福触发块补阿兰娜调用（最小改动） | 无 | `grep -c 'AlanaSignItem.onBlessingTriggered' …/combat/DiceCombatEvents.java` = 1；实机 +5 攻击 |
| **B2** | fabric | 修 C-2：新增 `TargetSelectCloseMessage`（append 到 id 30）+ `TargetSelectionManager.cancel(ServerPlayer, String)` + 替换 `EventCardService:88-95` 的 TODO-BRIDGE + 客户端处理 | B1 无依赖，可与 B1 同批 | `grep -rn 'TargetSelectClose' fabric-1.20.1/src/main/java` ≥3 处；实机 U7 判据通过 |
| **B3** | fabric | 清 C-3：**推荐**把 fabric 的直调改回 `SignHookDispatcher.fire*`（顺带抵消 C-1 的手工补丁，二选一实施）；或删干净 dispatcher + 接口 | B1 落地后再做（避免同处二次改） | python 断言每个 `fire*` ≥1 调用者；`grep -rn 'implements .*SignHooks' ` 与 P0 实现者数量一致 |
| **B4** | fabric | 清 C-4：删 8 处空登记；`SubscriptionAudit` 增加「登记但零处理器」WARN | 无 | 启动日志无该 WARN；`LoaderBus` 登记类全部有 ≥1 处理器 |
| **B5** | 26.1.2（另开批次） | P-1 / P-2 / P-3 / P-4 / P-5：补 `ALLY_RANGE`、注册 `CardChoice*` 两包、清 dispatcher 死代码、删 `BEAM_PARTICLES`、确认创造栏两张卡 | 与 fabric 批次**解耦**，避免同批四线互踩 | 各条复跑命令归零；26.1.2 实机选卡界面可用 |

**批次排序理由**：B1/B2 都是「玩家可感知」的行为缺口且互相独立，必须先修；B3 是同一处代码的两种修法（手工补 vs 恢复分发），**不要与 B1 同批**以免同一段代码被改两次；B5 属第三线，本批 fabric 审计只作旁证登记，不并入 fabric 批次。

---

## 附：本报告的取证边界

- 全部结论来自**文本级静态取证**（grep / sed / 自写 python 扫描）。没有 javap 字节码、没有构建、没有实机。
- 因此：§4 的 9 项**不作结论**；§3 的 P-2 抛点、§2.5 的运行时等价性、§2.1 的观感一致性均待实机。
- 报告未修改仓库任何文件，唯一写入为 `porting/fabric-1.20.1/ALIGNMENT-AUDIT-2026-11-01.md`。

