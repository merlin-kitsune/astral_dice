# 跨线本地 combat 副本的漂移风险（26.1.2 现 P0；登记，不立刻收编）

> 裁决来源：用户 2026-11-02「先清理孤儿代码，P1记录漂移风险」。2026-11-11 重新划级后，26.1.2 归 P0、fabric 两线归 P1；本文标题与历史段落中的「P1 两线」保留当时的 26.1.2 + fabric 口径。
> 本文只登记事实、风险与检测方法；**不**把本地副本收编到 starengine 库，也**不**改 P0 基准两线（n21/forge）。26.1.2 的本地副本风险现按 P0 跟踪。
> 事实取自 2026-11-02 实读（HEAD `6a1c8d4b` 之后的工作树）；任何数字若与现读不符，以命令输出为准。

## 1. 事实：26.1.2（现 P0）与 fabric-1.20.1（P1）各留一份 `combat/{PartyRelations,EliteTargets}`

| 线 | 本地文件（行） | 同包解析到本地副本的消费者 |
|---|---|---|
| `neoforge-26.1.2` | `combat/PartyRelations.java` 582、`combat/EliteTargets.java` 68 | `combat/DiceCombatEvents.java`、`combat/OrbitalBombardmentManager.java`、`combat/ShootingStarManager.java` |
| `fabric-1.20.1` | `combat/PartyRelations.java` 572、`combat/EliteTargets.java` 70 | 同上三个 |
| `neoforge-1.21.1` / `forge-1.20.1`（P0 基准） | **无**本地副本 | —— 全部 `import com.merlinkitsune.starengine.combat.*` |

补充事实：

- 26.1.2 与 fabric 不是「整线都不用库」：n26 有 42 个、fabric 有 44 个 java 文件触及这两个名字，其中绝大多数是 `import com.merlinkitsune.starengine.combat.*` 绑库版本（n26 132 处 / P0 基准 172 处 `starengine.combat` import）。
- **fabric 的 `AstralDiceMod.java:352` 显式写全限定名 `com.merlinkitsune.astral_dice.combat.PartyRelations.reportBackends()`（调本地副本）**；n21 与 n26 同一行调的是 `com.merlinkitsune.starengine.combat.PartyRelations.reportBackends()`（库版本）。⇒ fabric 不是「忘了改 import」，是刻意/遗留地指向本地副本。
- 三个消费者（`DiceCombatEvents` / `OrbitalBombardmentManager` / `ShootingStarManager`）本身就在 `com.merlinkitsune.astral_dice.combat` 包里，**裸名字优先解析同包类**，所以即便它们 import 了 starengine 的别的类，只要没单独 import 这两个，绑定的仍是本地副本。

## 2. 当前等价性证据（javap 实测，2026-11-02）

对比对象：库 jar `~/.m2/repository/com/merlinkitsune/starengine/starengine-<loader>/26.4-SNAPSHOT.1/*.jar`（本线引脚）vs 各线 `build/classes/java/main` 里的本地类。

- **n26**：`PartyRelations` 方法面 9 vs 9、`EliteTargets` 2 vs 2，**除构造器里的包名外完全相同**（onlyLib / onlyLocal 各 1，就是构造器本身）。
- **fabric**：方法数同为 9 / 2，差异**只是映射名**——库 jar 里是 intermediary（`net.minecraft.class_1657`、`class_1309`），本地编译产物是 Mojmap（`Player`、`LivingEntity`）。方法名、参数位序、返回类型、可见性一一对应。

结论（措辞限定）：**截至本次对比，两者公开 API 形状一致、行为等价**；这不等于「以后永远一致」，也不等于两边的私有实现逐行相同。

## 3. 为什么仍然是风险

1. **同包解析优先于 import**：谁在 `astral_dice.combat` 包里新增代码并裸写 `PartyRelations.xxx()`，就静默绑到本地副本，编译期零提示。
2. **库升级不传导**：`starengine 26.4-SNAPSHOT.1` 之后若修正 `isHostileTo` / `collectEventTargets` 的判定，P0 基准两线立即受益，26.1.2 与 fabric 的三个消费者仍跑旧实现 ⇒ 同一场战斗在 P0 基准与 26.1.2/fabric 上判定可能不同，且只有跑到对应场景才暴露。
3. **fabric 是显式 FQN 调本地**，即使把 import 清干净也不会自动切换。
4. **本地副本还会漂向新平台 API**：26.1.2 的 `LivingEntity` 签名与 1.20.1 不同，本地副本一旦为目标平台改过一行，就再也无法与库逐字节对照。

## 4. 漂移检测程序（可复制）

```bash
cd F:/MCProject/astral_dice_multiloader-next

# ① 谁绑本地副本（同包裸名）／谁绑库（import 或 FQN）
python temp/p0/dupdrift.py

# ② 本地副本 vs 库 jar 的 API 形状对比（javap）
python temp/p0/javap_dupdrift.py

# ③ 结构面：P0 基准与 26.1.2/fabric 的 combat 包文件清单
git ls-tree -r --name-only HEAD -- neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat \
  forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat \
  neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/combat \
  fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat
```

判定：② 里 `onlyLib` / `onlyLocal` **去掉构造器与映射名差异后应为空**。一旦出现真实方法差异（新增/删除/改签名），即**漂移已发生**，按第 5 节处置。

## 5. 收编触发条件（现在是「只记录」，满足任一条件再动手）

- 库 `26.4-SNAPSHOT.*` 对 `PartyRelations` / `EliteTargets` 做了**行为性**修改（判定口径、目标集、友军识别），而 26.1.2 / fabric 未同步；
- 26.1.2 或 fabric 需要向这两个类**新增**平台专用逻辑（那时应改为「库提供扩展点」而不是复制）；
- 出现「同一战斗在 P0 基准与 26.1.2/fabric 判定不一致」的现场证据。

处置方向（届时）：把 26.1.2 与 fabric 的三个消费者改成 import 库版本 → 删本地两份 → 跑各线构建 + 相关用例；不要反过来把 P0 基准两线改成用本地副本。

## 6. 附：本轮孤儿代码清理（同批）

`neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/fixes/{DamageStackSanitizer,NeoForgeFixesLog}.java` **已删除**（`git rm`，共 7,670 + 1,618 B）。

判据（删除前实读）：n26 **没有** `mixin/fixes/` 目录、`src/main/resources` 里也只有 `astral_dice.{jade,mixins,ysm}.mixins.json`（无 `neoforge_fixes` 配置），全仓对这两个类的 java 引用只落在它们彼此与 n21 的同名文件内 ⇒ n26 侧是死代码。删除后 n26 构建 rc=0，产物 jar 内 `/fixes/` 条目 = 0。

对照：`neoforge-1.21.1` 的同名两个类**必须保留**——`mixin/fixes/LivingEntityDamageContainerMixin.java:5-6` import 它们，`astral_dice.neoforge_fixes.mixins.json` 声明了该 mixin 包（26.1.2 上游已修复对应的 DamageContainer 问题，所以只有 n21 需要这套补丁）。
