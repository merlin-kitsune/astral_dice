# batch3 实施进度（增量落盘）

- 分支：`multi-1.20.1-1.21.1`，起点 HEAD `216f6cc`
- 规则：每完成一项立即写入本文件；N = `neoforge-1.21.1`，F = `forge-1.20.1`
- 注：`docs/scan2/*` 为他人正在维护的验证文档，本批**未修改**任何 `docs/scan2` 文件。

---

## ① D-B1 — GUI 卸下时物品组件归零丢失 —— **未改：论证 ① 不成立，按指令停下报告**

### 结论（字节码级，非猜测）

`isIntentionalUnequip` 的**判据前提**是对的（本批复核确认），**但"让 tick 路径操作槽内真实栈"在 GUI 卸下这一刻不存在可操作对象**：

1. Curios 1.21.1 / 5.14.1 的 tick 轮询（`CuriosEventHandler.lambda$tick$22` = indy #25，`javap -v` BootstrapMethods 已核对）对
   `ICurio.onUnequip(ctx, X)` 传入的 `X = 槽位**当前**内容（局部 14 = getStackInSlot(i)`）`，而 `prevCurio` 取自
   `getPreviousStackInSlot(i)`（局部 16，即 `setPreviousStackInSlot(i, 当前栈.copy())` 留下的 **copy 快照**）。
2. `ItemizedCurioCapability.onUnequip(ctx, X)` 的桥接是 `curioItem.onUnequip(ctx, X /*aload_2*/, this.getStack() /*快照*/)`。
   ⇒ 本模组 `onUnequip(slotContext, newStack, stack)` 在 tick 路径收到的 **第 2 参 = 槽位当前内容**、
   **第 3 参 = 上一刻的 copy 快照**。GUI 卸下时第 2 参已是 `EMPTY`（物品已离开槽位），第 3 参是副本。
3. ⇒ 此刻"槽内真实栈"**不存在**（槽位为空）；`clearSignData(player, 第3参)` 只能把归零写进副本 ⇒ 丢弃，
   而真正被卸下的那件（容器点击时被 `Slot#tryRemove → ItemStack#split → copyWithCount` **复制**后放入背包）
   在回调里既不能按引用拿到，也不能按槽位拿到。**因此"tick 路径操作槽内真实栈"无法实现 ①**。
   证据：`ContainerHelper.removeItem` / `Slot#safeInsert`（1.21.1 反编译源码）均经 `split()`/`copyWithCount()` 产副本；
   即便本模组自持上一 tick 的真实栈引用，也会在复制边界断链。
4. ②（自己的 `curioTick` 写组件不得被误判）**当前成立且未被本批改动**：写入当 tick 第 2 参 = 真实槽内栈、
   第 3 参 = 旧快照，同为该物品 ⇒ `!newStack.is(removedStack.getItem())` = false ⇒ 不清理。

### 备选机制（未实施，待裁定）

| 方案 | 能否满足「GUI 卸下后物品组件归零」 | 代价/风险 |
|---|---|---|
| A. 卸下前清理（`ICurio#canUnequip` / `CurioCanUnequipEvent`，`DynamicStackHandler#extractItem` 在真正提取**之前**回调） | ✅ 覆盖容器取出/快捷移动/丢弃（副本都产在清理之后） | 该回调在 `simulate=true` 的提取尝试中**也会**触发，可能把仍装备中的立牌累计值清零（灾难性）；查询语义里做写操作，需用户裁定 |
| B. 装备时清理（`onEquip` 的 tick 路径第 3 参 = 真实现槽内栈，可达） | ⚠️ 只保证"不会继承旧累计"，摘下的物品在背包里仍显示旧值 | 与"GUI 卸下后组件归零"的字面验收不等价 |
| C. 背包按值反查（卸下后按 item/组件在背包里找唯一候选清零） | ⚠️ 唯一候选时可清到 | 启发式；存在两件同物时要么歧义跳过、要么误清 |
| D. 本模组接管 Curios 的 `previousStacks`（公开 API `setPreviousStackInSlot` 写真实栈） | ⚠️ 依赖容器点击是否跨 tick | 会让 Curios 的差异检测（含客户端 `syncCurios`）对该槽位**永久静默**，计数器不再同步到客户端（tooltip 显示不刷新），副作用明确 |

**处置：按父代理指令「若论证①不成立立刻停下报告，不要硬改」——本项代码未改（两侧均未改），等待裁定。**

受影响文件（未改）：`item/CurioSlotUtil.java`、`item/sign/BaseSignItem.java`、`item/chip/BaseChipItem.java`、各 `*SignItem#clearSignData`。

---

## ② D-B2 — 电磁炮落雷对齐双参敌对口径 —— 已改（双版本对等）

- 改动文件：
  - N `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/damage/RailgunBolts.java`：`:38-42`（javadoc ①）、`:60-62`（判定主体）、`:75`（退化重载注释）
  - F `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/damage/RailgunBolts.java`：行号**逐行相同**（两文件改动后仍逐字一致）
- 关键代码：
  ```java
  // 「视谁为敌」的上下文 = 落雷来源玩家(无闪电实例时 viewer 为 null ⇒ 玩家一律不计入敌对)
  ServerPlayer cause = bolt == null ? null : bolt.getCause();
  if (!HostileTargets.isHostile(cause, target)) return false;   // 原为单参 isHostile(target)
  if (target instanceof OwnableEntity ownable) {
      if (cause != null && cause.getUUID().equals(ownable.getOwnerUUID())) return false;
  }
  ```
- `viewer` 来源核实：`RailgunChipItem#strike` 在 `level.addFreshEntity` 前调用 `bolt.setCause(cause)`（cause = 开炮的 `ServerPlayer`），
  且两个调用点（`LightningBoltStrikeScopeMixin` 的 `getEntities` 谓词、`EntityThunderHitMixin` 的 `thunderHit`）**都持有 bolt 实例**；
  `LightningBolt#getCause()` 在 1.20.1 与 1.21.1 反编译源码中均存在（`@Nullable ServerPlayer`）⇒ 两版本都拿得到来源玩家。
- 遗留疑点：无（退化重载 `isValidLightningTarget(target)` 无闪电实例 ⇒ viewer 为 null，玩家不计入敌对，已在注释中写明）。

---

## ③ C1 — 统一「轮次归零」清理口径 —— 已改（双版本对等）

- 采用**方案 ①（上移 disarmAoe 到 clearRoundBonuses）**。理由：`clearRoundBonuses(Player player)` 本身就有 `player` 形参、
  `ElectricGloveChipItem.disarmAoe(Player)` 是 `public static` ⇒ 无签名/可见性障碍；上移后
  `tick` 情形 1 / `registerPlay` 周期边界 / `forceResetRound`（忍者宽限期满）**三条路径天然共用同一口径**，
  不需要在每个调用点各写一遍（正是当年"各处各列一遍"导致残留的教训）。
- 改动文件：
  - N `item/card/EffectCardPeriod.java`：`:7`（新增 import）、`:178-200`（`clearRoundBonuses` 文档 + `ElectricGloveChipItem.disarmAoe(player)`）、`:402-404`（删除 tick 情形 1 的重复调用与注释）
  - F `item/card/EffectCardPeriod.java`：`:8`、`:179-201`、`:403-405`（逐行同构）
- 关键代码：`clearRoundBonuses` 末尾新增
  ```java
  // 周期归零:解除电击手套本周期已武装的法伤扩散(下个周期可重新武装)
  ElectricGloveChipItem.disarmAoe(player);
  ```
  同时 `tick` 情形 1 的原两行（注释 + 调用）已删除 ⇒ 全仓 `disarmAoe` 只剩 **1 处**调用（`grep` 已核对）。
- `clearRoundBonuses` 现有调用者（`grep` 核对）：`registerPlay` 周期边界块、`tick` 情形 1、`forceResetRound`。
  `registerPlay` 那处是**期望**的（轮次完全重置即解除武装）。
- 遗留疑点：`registerPlay` 边界现在也会调用一次 `disarmAoe`（写玩家附件，未做等值短路，与该方法原有 4 行写法一致），
  仅在"周期边界"发生，频率无实质变化。

---

## ④ C4 — 保命后 GLOWING 残留 —— 已改（双版本对等，含口径收窄）

- **先核实（要求项）**：`GLOWING` 确实是本模组**与标记一同施加**的 —— `item/MarkManager.apply(LivingEntity,int)` 在施加
  `ModEffects.MARKED` 的同时经 `EffectTimerGuard.apply` 施加 `MobEffects.GLOWING`（同 `durationTicks`），
  多层标记的 `onMarkExpired` 同步刷新，层数归零时移除（见 `MarkManager` 注释「发光与标记保持同一寿命」）。
- **但核实也发现**：`GLOWING` 有**本模组之外**的来源（原版光灵箭 `spectral_arrow` 命中、`/effect`、其它模组）。
  按父代理"若可能来自其它来源而移除会造成额外副作用则报告"的要求，实现上**只清"标记随身的那份发光"**：
  仅当玩家**确实带着本模组 `MARKED`** 时才把 `GLOWING` 加进移除白名单 ⇒ 无标记时不动原版来源的发光，
  有标记时发光与标记**同寿命**（口径严格成立）。若用户希望"无条件清 GLOWING"，删掉该 `if` 判断即可（1 行）。
- 改动文件：
  - N `event/EnderDiceHandler.java`：`:6`（新增 `effect.ModEffects` import）、`:175-197`（保命清理块）
  - F `event/EnderDiceHandler.java`：`:8`（同 import）、`:174-196`（同构，`ModEffects.MARKED.get()` + `player.hasEffect(MobEffect)`）
- 关键代码（N）：
  ```java
  List<Holder<MobEffect>> effectsToRemove = new ArrayList<>();   // 原 harmfulEffects
  for (... HARMFUL ...) effectsToRemove.add(instance.getEffect());
  if (player.hasEffect(ModEffects.MARKED)) effectsToRemove.add(MobEffects.GLOWING);
  for (Holder<MobEffect> effect : effectsToRemove) ModEffectRemoval.remove(player, effect);
  ```
- **白名单保持显式**（HARMFUL + 额外的 GLOWING），未退化为 `removeAllEffects()`；
  先 `player.setHealth(1.0F)` 再清效果的既有顺序未变；收集阶段先于任何移除（`hasEffect(MARKED)` 判定不会被同批清除影响）。
- 遗留疑点：无（唯一的取舍是"有标记时原版光灵箭的发光会随标记一起消失"，这是单实例效果的固有边界，需用户确认可接受）。

---

## ⑤ C4b — forge javadoc 笔误 —— 已改（仅 F 有该笔误）

- 改动文件：F `event/ModEffectRemoval.java:9`：`{@link ModEventHandlers#onModEffectRemovalPrevented}` → `{@link ModEffectEvents#onModEffectRemovalPrevented}`。
- N 侧本已是正确类名（`ModEffectEvents`），无需改；两版本该类其余内容一致（仅 `MobEffect` vs `Holder<MobEffect>` 的 API 差异）。
- 遗留疑点：无。

---

## 项 × 版本 勾选表

| 项 | N `neoforge-1.21.1` | F `forge-1.20.1` | 功能对等 |
|---|---|---|---|
| ① D-B1 GUI 卸下组件归零 | ❌ 未改（论证 ① 不成立，停下报告） | ❌ 未改（同上） | 一致（两侧均维持现状） |
| ② D-B2 电磁炮双参敌对 | ✅ | ✅ | ✅ 逐行一致 |
| ③ C1 disarmAoe 上移 | ✅ | ✅ | ✅ 逐行同构 |
| ④ C4 保命额外清 GLOWING | ✅ | ✅ | ✅ 同构（平台 API 差异处按各自写法） |
| ⑤ C4b forge javadoc | ✅（无此笔误） | ✅ | ✅ |

---

## 收尾进度

### CHANGELOG 同步（已做）

- `CHANGELOG_ZH.md` 与 `CHANGELOG.md` 均**合并进现有未发布条目**（无「再次修改」类追加）：
  ① **电磁炮**条（Content & Balance）尾部追加「命中范围的敌对判定已对齐双参口径（viewer = `bolt.getCause()`）」（D-B2）；
  ② **出牌轮收尾口径**条：调用点由「两处」改为「**三处**」并写明电击手套武装统一在 `clearRoundBonuses` 解除（C1）；
  ③ **敌对玩家规则**条：消费者加入「电磁炮落雷」（D-B2）；
  ④ **末影骰子保命只清有害效果**条：补记「额外显式追加 `GLOWING`」及"只在带本模组标记时生效"的口径收窄（C4）；
  ⑤ 第二段 `### 工程` / `### Project` 新增 **1 条**工程条目（forge javadoc 类名笔误 + `AGENTS.md` 三处口径同步 + 卸下缺口如实记录；C4b）。
- **自检（按版本比对 `- ` 条目数）**：`未发布(1.2.1)` 两文件均 **47 条**，子节一一对应（新内容 0 / 内容与平衡性调整 17 / 已修复BUG 16 / 工程 3）；英文侧同名同数（New Content 0 / Content & Balance 17 / Bug Fixes 16 / Project 3）。
- 说明：D-B1（①）**未修复**，故 CHANGELOG **未**新增"已修复"条目；其机制与候选修法记录在 `AGENTS.md` 与本进度文件。

### AGENTS.md 更新（已做，仅本批相关段落）

1. 「饰品卸下(`onUnequip`)的参数语义与已知缺口」段：已知缺口按字节码**如实改写**（tick 路径第 2 参 = 槽位当前内容、第 3 参 = copy 快照 ⇒ GUI 卸下组件归零丢失；并写明"让 tick 路径操作槽内真实栈"不可行及三种候选修法，待裁定）。
2. 「敌对判定 → 统一现状」段：调用点统计 22→**23** 两参 / 3→**2** 单参，并从单参清单移除 `RailgunBolts`；「敌对玩家规则」段消费者加入电磁炮落雷；「电磁炮雷击命中范围」段改为双参口径（viewer = `bolt.getCause()`）。
3. 「效果牌轮次定义」段：补「轮次归零统一清理口径 = `clearRoundBonuses`」，含 `disarmAoe` 与三条归零路径（`tick` 情形 1 / `registerPlay` 边界 / `forceResetRound`）；「立牌主动技能」条里的「仅两处调用」改为「三处」。
4. 「保命一律按『吸收之后』判定」段：补「额外移除 `GLOWING`」及其门控理由与来源边界。

### 双版本构建（已做，以「日志 + 产物」双重验证判定）

| 版本 | 命令 | 日志 | 产物 | 字节码核对 |
|---|---|---|---|---|
| 1.21.1 | `scripts/test/mt_build.ps1 --version 1.21.1 --timeout 60 --retries 3` | `temp/mt_build_1.21.1_1789465115.log`：**BUILD SUCCESSFUL in 2s** | `astral_dice-1.2.1+neoforge_1.21.1.jar` 更新（17:38:38），已随 `pushToGame` 部署到 `run/1.21.1/mods` | `RailgunBolts` → `isHostile(Entity,Entity)`；`EffectCardPeriod` → `ElectricGloveChipItem.disarmAoe`；`EnderDiceHandler` → `hasEffect(MARKED)` + `MobEffects.GLOWING` |
| 1.20.1 | `scripts/test/mt_build.ps1 --version 1.20.1 --timeout 60 --retries 3` | `temp/mt_build_1.20.1_1789465131.log`：**BUILD SUCCESSFUL in 3s** | `astral_dice-1.2.1+forge_1.20.1.jar` 更新（17:38:55），已部署到 `run/1.20.1/mods` | 同上（`ModEffects.MARKED.get()` / `Player.hasEffect(MobEffect)` 为 1.20.1 写法） |

### 本地提交（已做，未 push）

- commit **027e1e9** `fix(1.2.1): 验证差异裁定落地第二批(双版本)`，10 个文件（AGENTS.md、CHANGELOG.md、CHANGELOG_ZH.md + N/F 各 4 个/3 个 java）。
- 提交前 `git status` 核对：仅上述 10 个文件为 M；`docs/` 与 `temp/` 均在 `.gitignore` 内（本进度文件按仓库既有约定**不入库**，与他人维护的 `docs/scan2/*` 同）。
- 未执行 `git push`。

### 最终行号速查（本批新增/修改）

| 项 | N | F |
|---|---|---|
| ② 双参判定主体 | `damage/RailgunBolts.java:64` | 同行号 64（两文件逐字一致） |
| ③ `clearRoundBonuses` 内 `disarmAoe` | `item/card/EffectCardPeriod.java:199` | `:200` |
| ③ tick 情形 1 重复调用已删 | `:401-403` | `:402-404` |
| ④ GLOWING 追加 | `event/EnderDiceHandler.java:189-190` | `:188-189` |
| ⑤ javadoc 类名 | —（本就正确） | `event/ModEffectRemoval.java:9` |


