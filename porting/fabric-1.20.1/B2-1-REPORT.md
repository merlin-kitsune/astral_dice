# B2-1 报告：事件契约层（6 个纯数据类型）

批次 B2-1（事件体系移植第一块）。基线 `forge-1.20.1`（MC 1.20.1 + Forge 47.x，唯一蓝本）；目标 `fabric-1.20.1`（MC 1.20.1 + FAPI 0.92.12 + Loom，Mojang 映射，Java 17，`build.gradle:25` `JavaLanguageVersion.of(17)`）。仓库 `F:/MCProject/astral_dice_multiloader`，分支 `multi-main`；取证时点 `2026-11-01 18:1x +08:00`。
本批**未跑 gradle、未启动游戏、未 commit**（整线当前因 `item/sign/LinglingSignManager` 缺失而编译失败，不属本批）；设计依据 `porting/fabric-1.20.1/B2-EVENT-RECON.md` §1（阶段 A）、§2（本批 0 条适用）、§8（R5 `ResourceLocation` 工厂）。

## 1 新增文件清单

目标目录前缀 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`，与源 `forge-1.20.1/...` 同路径、同包名（`package` 行无需改动）。全部 CRLF（CRLF 计数 = 行数）、无 BOM、文件尾有换行，与本线既有源文件（`event/AstralEventSystem.java` 117 行全 CRLF）一致。

| # | 文件 | 行 | 字节 | sha256 前 12 | git 状态 |
|---|---|---|---|---|---|
| 1 | `event/api/AstralEvent.java` | 54 | 2427 | `1032b38fab02` | `??` 新增 |
| 2 | `event/api/AstralEventEffect.java` | 31 | 1213 | `3af30edd17d3` | `??` 新增 |
| 3 | `event/api/AstralEventPolarity.java` | 26 | 815 | `d030ddabda99` | `??` 新增 |
| 4 | `event/api/AstralEventTier.java` | 44 | 1485 | `4ffd7615c957` | `??` 新增 |
| 5 | `event/api/AstralEventTrigger.java` | 20 | 933 | `38ae02fc9bbf` | `??` 新增 |
| 6 | `event/AstralEventIds.java` | 39 | 2093 | `096f5f63ae45` | `??` 新增 |

## 2 逐文件 diff 结论（python difflib，剥掉 package/import 行后 unified diff）

| 文件 | 剥 import/package 后 unified diff | 说明 |
|---|---|---|
| `event/api/AstralEvent.java` | **0 行（空）** | 逐字节等价 |
| `event/api/AstralEventEffect.java` | **0 行（空）** | 逐字节等价 |
| `event/api/AstralEventPolarity.java` | **0 行（空）** | 逐字节等价 |
| `event/api/AstralEventTier.java` | **0 行（空）** | 逐字节等价 |
| `event/api/AstralEventTrigger.java` | **0 行（空）** | 逐字节等价 |
| `event/AstralEventIds.java` | **0 行（空）** | 逐字节等价 |

更强的判据：6 个文件 **sha256 与 forge 原文全等**（不只是剥 import 后等价），import 区本身也一字未改——两线包名相同，且这 6 个类只 import vanilla 与同包类：`ResourceLocation`、`Component`、`ChatFormatting`、`ServerLevel`、`Player`、`java.util.Set`，以及 `com.merlinkitsune.astral_dice.event.AstralEventSystem`（同包路径两线一致）。
- 对 net.minecraftforge / cpw.mods 的 import 命中 **0**；`fromNamespaceAndPath` / `withDefaultNamespace` 命中 **0**（`AstralEventIds.java:20` 原本就是 `new ResourceLocation(NAMESPACE, path)`，天然符合 §8-R5 的 1.20.1 口径，**无需改写**）。
- API 可用性核实（`javap` 打本线实际映射产物 `minecraft-merged-1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2.jar`）：`public ResourceLocation(String, String)` **存在**，同 class 只有 `tryBuild`、**无** `fromNamespaceAndPath`（与 §8-R5 一致）；`Component.translatable(String) -> MutableComponent` 与 `MutableComponent.withStyle(ChatFormatting|ChatFormatting...)` **存在**（`displayName()` 用到）；`ChatFormatting` / `ServerLevel` / `Player` 均存在。`AstralEventTier.of(int)` 用 switch 表达式，需 Java 14+，本线 toolchain 17 满足。

## 3 public 签名表（后续批次按此接线）
### event.api.AstralEvent — record（54 行）
```
public record AstralEvent(ResourceLocation id, String nameKey, ResourceLocation icon,
                          AstralEventPolarity polarity, boolean secretOnly, boolean inPublicPool,
                          Set<AstralEventTrigger> triggers, double radius,
                          AstralEventEffect effect, int defaultDurationTicks)
  // 规范构造器 AstralEvent(ResourceLocation, String, ResourceLocation, AstralEventPolarity,
  //                       boolean, boolean, Set<AstralEventTrigger>, double, AstralEventEffect, int)
  public Component displayName()                 // Component.translatable(nameKey).withStyle(polarity.color())
  public boolean  allows(AstralEventTrigger t)   // triggers.contains(t)
  public boolean  timed()                        // defaultDurationTicks > 0
  // 自动生成访问器：id() nameKey() icon() polarity() secretOnly() inPublicPool()
  //                 triggers() radius() effect() defaultDurationTicks()
```
### event.api.AstralEventEffect — @FunctionalInterface interface（31 行）
```
public interface AstralEventEffect
  public static final AstralEventEffect NOOP     // 空壳，本轮所有正式事件都用它
  public abstract void apply(ServerLevel level, Player triggerer, Player target,
                             AstralEvent event, AstralEventTrigger source, int tier)
```
### event.api.AstralEventPolarity — enum（26 行）
```
public enum AstralEventPolarity { POSITIVE, NEUTRAL, NEGATIVE }   // 各自绑定 ChatFormatting GREEN/YELLOW/RED
  public ChatFormatting color()
```
### event.api.AstralEventTier — enum（44 行）
```
public enum AstralEventTier {
  NONE(0, ""), STAGE_I(1, "1"), STAGE_II(2, "2"), STAGE_III(3, "3"), REVELATION(4, "revelation") }
  public int    level()
  public String iconSuffix()                       // 图标后缀：_1 / _2 / _3 / _revelation
  public static AstralEventTier of(int stage)      // 钳制到 0..4，越界 -> NONE
```
### event.api.AstralEventTrigger — enum（20 行；末位常量后无分号，合法 Java，逐字节等价要求下未改）
```
public enum AstralEventTrigger { ACTIVE_SKILL, INVESTIGATION_KILL, ITEM_USE, BLOCK_USE, CUSTOM }
```
### event.AstralEventIds — final class + 私有构造器（39 行）
```
public static final String NAMESPACE = "astral_dice"
public static ResourceLocation id(String path)     // new ResourceLocation(NAMESPACE, path)
public static final ResourceLocation RED_HEAT_WARNING, SWIFT_STRIDE, STORM_APPROACHING,
  LEARN_FROM_OTHERS, SHOP_BONANZA, HAND_ERASURE, WORLD_COLLAPSE, GIFT_FROM_SKY, ANGEL_DESCENT,
  PROBLEM_DELIVERY, CROWDED_PASSAGE, BATTLE_FRENZY, INVESTIGATION_STAGE     // 共 13 条
```
## 4 阻塞项

**无硬阻塞**。这 6 个类不引用 ModItems / ModEffects / ModAttachments / ModNetwork，不含任何平台 import，也不参与注册（纯数据），故不依赖其它批次、不加剧整线编译失败，可独立成立。

软依赖 1 条（非阻塞）：`AstralEvent.java:4` import `AstralEventSystem`，仅用于 javadoc `{@link AstralEventSystem#EVENT_TARGET_RADIUS}`（`:23`）。本线 `event/AstralEventSystem.java:26` 已有 `public static final double EVENT_TARGET_RADIUS = 64.0D`，链接成立；若后续 B2-3 改该常量名/搬走，需同步本 javadoc（只影响 javadoc 生成，不影响 javac）。

## 5 需主 Agent 裁决项

1. **批次归属与 RECON §7 不一致**：`B2-EVENT-RECON.md` §7 把 `event/AstralEventIds` 排在 **B2-2**（「常量与存储」），本工单把它并入 **B2-1**。代码上无耦合（`AstralEventIds` 只依赖 ResourceLocation），不会与 B2-2 冲突，但**存在重复施工风险**：请在 B2-2 需求里注明「`AstralEventIds` 已由 B2-1 落地，勿重写」，或回填 §7 口径。
2. **包结构一致性**（仅登记）：本线 `event/` 下已有 `AstralEventSystem`，本批新增 `event/api/` 子包；后续 `AstralEventRegistry` / `AstralEventDispatcher` 落线时请沿用 `event/`（非 `event/api/`），与 forge 路径一致。
3. **本批未接线**：`AstralEventEffect.NOOP` 等目前**无任何消费者**（fabric 线零引用），属预期——由 B2-4（`AstralEventRegistry` / `EventEffects`）消费。

## 6 复跑命令（只读）
```bash
cd "F:/MCProject/astral_dice_multiloader"
# 1) 逐字节等价（期望 6 行 IDENTICAL）
for f in event/api/AstralEvent.java event/api/AstralEventEffect.java event/api/AstralEventPolarity.java \
         event/api/AstralEventTier.java event/api/AstralEventTrigger.java event/AstralEventIds.java; do
  cmp "forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/$f" \
      "fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/$f" && echo "IDENTICAL $f"; done
# 2) 平台 import 期望 0
grep -rn "net.minecraftforge" fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/api \
     fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/AstralEventIds.java || true
# 3) 1.20.1 ResourceLocation 口径期望 0（只允许 new ResourceLocation(ns, path)）
grep -rn "fromNamespaceAndPath" fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event || true
# 判据：上面三段无输出且无报错 = 逐字节等价 + 平台 import 0 + 1.20.1 口径 0（grep 无命中返回 1，故加 || true）
```

本批未触碰禁改清单（AstralDiceMod.java、network/ModNetwork.java、item/ModItems.java、init/ModCreativeTabs.java、三份 lang json、astral_dice.mixins.json、item/sign、component、shop、screen、client）；亦未改 AstralEventSystem / AstralEventDispatcher / EventEffects（后两者本线尚不存在）。同目录其它批次的未提交改动（M event/PlayerTickEvents.java 及若干 ?? 文件）保持原样。
