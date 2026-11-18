# B10-EXPLOSION-BRIDGE 移植报告（forge-1.20.1 蓝本 → fabric-1.20.1）

批次 B10：补 `ExplosionEvent.Start` 桥，让「拒止装置防苦力怕爆炸」在 fabric 线真正生效。
蓝本 `forge-1.20.1/.../event/RepulsionCreeperGuard.java:76`；目标 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`（MC 1.20.1 + FAPI 0.92.12 + Loom，Mojmap，Java 17）。
约束执行：未跑 gradle、未启动游戏、未 commit、未删文件；改动全部 UTF-8 无 BOM + CRLF（逐字节核过）；本批未触碰 `fabric.mod.json` 与资源文件。

## 0. 结论

- 缺口关闭：`Level#explode` 上两条 `@WrapOperation` 派发垫片事件 ⇒ `RepulsionCreeperGuard.onExplosionStart` 经 `LoaderBus` 被调用，取消后 `Explosion#explode()` 与 `Explosion#finalizeExplosion(Z)` 都不执行。
- 交付：新建 2（`platform/event/level/ExplosionEvent.java` 56 行、`mixin/bridge/ExplosionStartBridgeMixin.java` 100 行）；编辑 3（`astral_dice.mixins.json` 追加 1 条、`event/RepulsionCreeperGuard.java` 加订阅入口、`AstralDiceMod.java` 纯追加 1 行）。
- javac 门禁：改动前 REAL=0 → 改动后 REAL=0（原始输出见 §5.1）。

## 1. 注入点取证（`javap -p -c` 原文，jar = Loom 的 `minecraft-merged-1.20.1` Mojmap 版）

**1.1 `Level` 有 5 个 `explode` 重载，终态是 10 参**（裸名 `"explode"` 会命中全部重载，故描述符写全）：
```
public Explosion explode(Entity, double, double, double, float, Level$ExplosionInteraction);
public Explosion explode(Entity, double, double, double, float, boolean, Level$ExplosionInteraction);
public Explosion explode(Entity, DamageSource, ExplosionDamageCalculator, Vec3, float, boolean, Level$ExplosionInteraction);
public Explosion explode(Entity, DamageSource, ExplosionDamageCalculator, double, double, double, float, boolean, Level$ExplosionInteraction);
public Explosion explode(Entity, DamageSource, ExplosionDamageCalculator, double, double, double, float, boolean, Level$ExplosionInteraction, boolean);  // ← 注入目标
// 描述符：explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Z)Lnet/minecraft/world/level/Explosion;
```
**1.2 终态重载字节码：`explode()` 与 `finalizeExplosion(Z)` 是两次独立调用**（Forge 的 patch 就在两者之间）：
```
     102: new           #35         // class net/minecraft/world/level/Explosion
     122: invokespecial #849        // Explosion."<init>"(Level,Entity,DamageSource,ExplosionDamageCalculator,DDDFZLBlockInteraction)V
     125: astore        15
     127: aload         15
     129: invokevirtual #851        // Method net/minecraft/world/level/Explosion.explode:()V
     132: aload         15
     134: iload         13
     136: invokevirtual #855        // Method net/minecraft/world/level/Explosion.finalizeExplosion:(Z)V
     139: aload         15 / 141: areturn
```
**1.3 全类唯一性**（`require = 1` 的依据）：`Explosion.explode:()V` = 1 次；`Explosion.finalizeExplosion:(Z)V` = 1 次；`new Explosion` = 1 次（均对整个 `Level` 类 `grep -c`）。
**1.4 打火石路径确实汇入该重载**（`Creeper.explodeCreeper()`）：
```
  24: iconst_1 / 25: putfield dead:Z                              // dead=true 在爆炸之前就置位
  55: invokevirtual Level.explode:(Lnet/minecraft/world/entity/Entity;DDDFLLevel$ExplosionInteraction;)LExplosion;
  60: invokevirtual discard:()V      64: invokevirtual spawnLingeringCloud:()V
```
6 参 → 8 参（`:1016`，`iconst_0`=无粒子）→ 终态 10 参。苦力怕 / TNT / 床 / 重生锚 / 末影水晶全部经终态重载。
**1.5 `ServerLevel` 覆写 8 参且仍委托终态**：方法体 = `iconst_0` → `invokevirtual explode(...,Z)Explosion` → `astore 13` → 按 `distanceToSqr < 4096.0d`（64 格）逐玩家发 `ClientboundExplodePacket(pos, power, getToBlow(), getHitPlayers().get(p))`。`ClientLevel` **无** `explode` 覆写。

## 2. 垫片 API 与 Forge 的差异表（`platform/event/level/ExplosionEvent.java`）

| 项 | Forge 1.20.1（`ref/minecraftforge/.../level/ExplosionEvent.java`，92 行） | 本线垫片 | 差异 |
|---|---|---|---|
| 类名/包 | `net.minecraftforge.event.level.ExplosionEvent` | `...astral_dice.platform.event.level.ExplosionEvent` | 仅包名 |
| 基类 | `net.minecraftforge.eventbus.api.Event` | `platform.event.Event`（本线 Forge 语义子集，`isCanceled/setCanceled/isCancelable` 同名） | 仅 import |
| 字段/构造器/访问器 | `Level level`、`Explosion explosion`；`ExplosionEvent(Level,Explosion)`；`getLevel()`、`getExplosion()` | 逐个 1:1 | 无 |
| `Start` | `@Cancelable public static class Start extends ExplosionEvent`，`Start(Level, Explosion)` | 同（注解换成本线 `@Cancelable`，`RUNTIME` 保留、直接标在 `Start` 上） | 仅注解类型 |
| `Detonate` | 有（不可取消，`getAffectedBlocks()=getExplosion().getToBlow()`、`getAffectedEntities()`） | **未搬运** | 全仓零使用；日后要用须补 `Explosion#onExplosionDetonate` 钩子，否则是永不派发的事件类（已写进 javadoc） |
| 蓝本用到的访问器 | `getLevel()` / `getExplosion()` / `getDirectSourceEntity()` / `getIndirectSourceEntity()` / `setCanceled()` | 全部 1:1 可用 | 无 |

**方法名 → 本线替代：无一条需要替代。** `Explosion#getDirectSourceEntity():Entity` 与 `Explosion#getIndirectSourceEntity():LivingEntity` 是 1.20.1 原版原生方法（`javap` 已核在本线 classpath 上），不是 Forge 扩展；本线不存在的 `Explosion#getPosition()/getExploder()` 蓝本并未使用。

## 3. 取消语义等价性论证

| | Forge 蓝本 | 本批实现 |
|---|---|---|
| 注入位置 | `Level#explode`（10 参）内、`new Explosion(...)` 之后、`explosion.explode()` 之前 | 同（`@At INVOKE Explosion.explode()V` = 字节码 129） |
| 取消后 `explode()` | 不执行（提前 `return explosion`） | 不执行（wrap 直接 `return`，不调 `original.call`） |
| 取消后 `finalizeExplosion(Z)` | 不执行（已 return） | 不执行（第二处 wrap 按 `ThreadLocal` 引用相等跳过） |
| 返回值 | `Level#explode` 仍 `areturn` 该 `Explosion`（`toBlow` 空、`hitPlayers` 空） | 同（只替换两次调用，不碰控制流） |
| 调用方后果 | `explodeCreeper()` 的 `discard()` / `spawnLingeringCloud()` 照跑 ⇒ 苦力怕无声消失 | 同（字节码 60/64 逐条对齐） |

唯一「非零副作用」：`ClientboundExplodePacket` 仍发给 64 格内玩家（`toBlow`/`hitPlayers` 皆空），客户端 `ClientPacketListener#handleExplode` 会自建 `Explosion` 并 `finalizeExplosion(true)` ⇒ 客户端**可能**仍听到爆音/看到粒子。**Forge 47.4.10 的 patch 位置与之完全相同，该现象是蓝本共有，不是本线偏差**（蓝本 javadoc「无爆炸粒子与音效」这句在两线都不准确，属文案问题，本批不改语义）。

## 4. 接线与登记位置

- `fabric-1.20.1/src/main/resources/astral_dice.mixins.json:20`：`"bridge.EntityDimensionTravelBridgeMixin",` 之后插入 `"bridge.ExplosionStartBridgeMixin",`（现共 31 条，JSON 可解析）。
- `AstralDiceMod.java:181`：纯追加单行 `LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.RepulsionCreeperGuard.class);`，位于 `:180 RailgunStrikeScheduler` 与 `:182 RepulsionTeleportGuard` 之间（既有字母序分组内），未动其它行。
- `event/RepulsionCreeperGuard.java:73-79`：新增 `@SubscribeEvent public static void onExplosionStart(ExplosionEvent.Start event)`，体只有 `if (handleExplosionStart(...)) event.setCanceled(true);`；判据体 `handleExplosionStart(...)`（`:93-107`）**一行未改**，仍 `public static` 保留（B3 历史 API，`B3-EFFECTCARDS.md` 已引用）。
- 选型：两条 `WrapOperation`（MixinExtras，同 `LivingHurtBridgeMixin`）而非 `@Inject`+局部变量捕获。`explode()` 无参、`finalizeExplosion(Z)` 仅 1 实参，`WrapOperation` 直接把**接收者**当第一个处理器参数给到 ⇒ 零 LVT 依赖（本仓 `mixin/client/EffectRenderingInventoryScreenMixin.java:39-53` 记过 LVT 教训）。两次调用用 `ThreadLocal<Explosion>`+引用相等配对；取消时第一处根本不调 `explode()`，两处之间只剩 `iload`（无重入点），残值会被下一次 `remove()` 覆盖。

## 5. 自检原始输出

**5.1 `python scripts/__javac_check.py`（唯一编译判据，只看 REAL）** — 改动前：
```
EXIT 1 ERRORS 170 jars 685 srcs 516
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  163  NOISE_AW /     7  NOISE_IFACE_INJECTION
=== REAL errors ===
```
改动后：
```
EXIT 1 ERRORS 171 jars 685 srcs 519
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW /     7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984
```
⇒ 两次 `REAL errors` 与 `REAL by file` **均为空**：REAL 0 → 0，未新增。`fabric-1.20.1/build/javac-check/errors.txt` 里 `ExplosionEvent|ExplosionStartBridgeMixin|RepulsionCreeperGuard` 零命中。ERRORS 170→171 / srcs 516→519 的增量来自同仓其它批次并发新增（`git status` 286 个未跟踪项，含 `client/BarricadeRenderer.java` 等），非本批产物。

**5.2 注入点复核（`javap` 对真实 class，非源码）**
```
$ javap -p -c -classpath <merged-1.20.1.jar> net.minecraft.world.level.Level > level-javap.txt
$ grep -c "Explosion.explode:()V"             level-javap.txt   →  1
$ grep -c "Explosion.finalizeExplosion:(Z)V"  level-javap.txt   →  1
$ grep -c "class net/minecraft/world/level/Explosion$" level-javap.txt   →  1
```
`@At target` 两串（`Lnet/minecraft/world/level/Explosion;explode()V` / `...;finalizeExplosion(Z)V`）与真实 `invokevirtual` 注释**逐字符一致**；`require = 1` 语义是「最少命中 1 次」，真实命中数为 1 ⇒ 不会在目标类加载时抛 `InjectionError`。此项编译与构建都不报错、只在类加载时炸，故必须人工核（已核）。

**5.3 遗留 TODO /「无调用方」**
```
$ grep -rn "TODO\|无调用方" fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/RepulsionCreeperGuard.java
(无输出，EXIT=1)
```
⇒ 零遗留（原 javadoc 里「本线没有 ExplosionEvent，属跨批次缺口 B-10」已改写为「B-10 已补齐」）。

## 6. 需要实机才能确认的项

1. **refmap 重映射**：源码写 Mojang 名，`remapJar` 须正确转 intermediary（`class_1937`/`class_1927`/`method_17742`）。只有构建+启动能证：日志应出现 `Mixin ExplosionStartBridgeMixin ... Level.class` 且**无** `InjectionError`。
2. **端到端行为**：被强化拒止吸附的苦力怕用打火石点燃 → 引信烧到底**不炸方块、不造成伤害**、苦力怕无声消失（无死亡动画、无掉落）+ 听到 `FIRE_EXTINGUISH`；未吸附的苦力怕 / TNT 照常爆炸。
3. **客户端观感**：按 §3 的 `ClientboundExplodePacket` 推断，可能出现一次「空爆炸」的音效与粒子，需实机确认可否接受（Forge 线同表现）。
4. **双栖加载**：mixin 打 `Level`（client+server 都注），客户端预测路径也会派发事件，但判据首行 `level.isClientSide` 直接放行 ⇒ 只多一次空派发；需实机确认无日志刷屏。

## 7. 有意不做的项

- 不搬 `ExplosionEvent.Detonate`；不改爆炸强度/半径/掉落/方块列表（蓝本 `:46-48` 明写只做两条判据）。
- 未采用 `fuzs.puzzleslib.api.event.v1.level.ExplosionEvents.START`（`ref/puzzles-lib/` 里有）：它返回 `EventResult` 而非可取消事件，注入点也不受本仓控制；为与 forge 蓝本 1:1 且保住「取消即 `Level#explode` 提前 return」的等价性，仍走自写 mixin。
- 本批无资源/语言改动 ⇒ 未跑 `tools/verify_fabric_assets.py` / `tools/check_lang_sync.py`（守门里 [1] 方块物品资源 / 缺模型 / 缺贴图三项 FAIL 属既有旧 jar 噪声）。

