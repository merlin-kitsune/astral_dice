# D 差异反馈表（第一遍验证汇总）

> 用途：把「二重验证」第一遍（R1）的全部发现合并成一张表，交用户定夺。
> **冻结声明**：本表定夺完成前 —— 不写 `AGENTS.md`、不进入第二轮（R2）、不做任何语义改动。
> 口径：`N` = `neoforge-1.21.1`（MC 1.21.1 / NeoForge 21.1.235 / Java 21）；`F` = `forge-1.20.1`（MC 1.20.1 / Forge 47.4.10 / Java 17）。
> 基线：分支 `multi-1.20.1-1.21.1`，HEAD = `216f6cc`（批次二「立牌主动技能三态化」）；批次一 = `6daebf9`。
> 验证者 ≠ 实现者：本表全部结论来自独立 subagent，未由实现者自证。

## 证据文件

| 线 | 范围 | 文件 |
|---|---|---|
| R1-a | 伤害事件映射 / 保命 / 减伤顺序 / 死亡清理 / 删除写入安全性（条目 1-7） | `docs/scan2/R1a-damage.md`（原文 1-103 行 + 补做 104-211 行） |
| R1-B2 | 批次二「立牌主动技能三态化」全部功能点 | `docs/batch2/R1-B2-verification.md`（489 行） |
| R1b | 冷却基准 / 敌对判定 / 装备路径 | `docs/scan2/R1b-state.md` |
| 文档勘误对象 | `onUnequip` 三参语义 | `docs/scan2/P5-equip-paths-linkage.md` §1.2 / §1.4 |

---

## 表 A：已判定通过（无需定夺）

| # | 项 | N | F | 关键证据 |
|---|---|---|---|---|
| A1 | `bypasses_cooldown` 数据包 + `invulnerableTime` 分支 | PASS | PASS | 两版本各含 52 B 的 `data/minecraft/tags/damage_type/bypasses_cooldown.json`，仅列 `astral_dice:true_damage`；原版**不提供**该文件，故为本模组新增 |
| A2 | 雨/水 ×1.4 落在受击方且**恰好一次** | PASS | PASS | 五条受伤路径逐一核对；非赐福伤害与环境伤害仍保持 ×1.4；`instanceVictimFactor` 只承载不重复施加 |
| A3 | 安全气囊致命判定改为吸收后 | PASS | PASS | 判据 `damage - player.getAbsorptionAmount()`（仅 1.21.1 侧）；磨刀石路径未被本批触碰；跨版本行为对等 |
| A4 | 虚空免死边界 | PASS | PASS | 仅 `DamageTypes.FELL_OUT_OF_WORLD` 跳过保命 ⇒ `/kill`（`generic_kill`）**仍可被保命**；`EnderDiceHandler` 的虚空检查未被 `6daebf9` 改动 |
| A5 | 冷却基准记录（枪匠 120s 不再被按 180s 过度减免） | PASS | PASS | 基准由「起冷却时写定」改为「上锁时先写、进入冷却时按 `max(0,基准−池)` 抵扣后改写」 |
| A6 | 三态判定与迁移入口 | PASS | PASS | `isSignActiveLocked` = 硬上界未到 且 门控效果仍在；`lock_end==0` 的忍者仅按锁定标记；`endLockAndStartCooldown` 一次性抵扣 |
| A7 | 7 个立牌登记锁定点 | PASS | PASS | parunan / jasmine / misaki / fanny / papara / nancy_lu / fen 均在**实际施加点**登记；nancy_lu 用自身附件；无名称冲突 |
| A8 | 锁定分支先于冷却分支 | PASS | PASS | `BaseSignItem` 锁定分支在冷却分支之前；锁定态按键无效并提示 `msg.astral_dice.sign_active_in_effect`（含 `%s`） |
| A9 | 电流核心互斥 | PASS | PASS | 上锁期按主动返回 `FINISH_NONE` 于任何充能/状态写入之前，且**恰好抛出一条**提示 ⇒ 不触发效果、不消耗充能 |
| A10 | 30 秒待命窗口**不算**本技能计时器 | PASS | PASS | 占星师/秘密侦探/枪匠不在 `beginActiveLock`/`startActiveLockOnUse` 全仓命中集内；`handleUse` 只写 `sign_ready_type/expire`；冷却块被 `getSignReadyType>0` 跳过；释放路径位于 `isBlessingTarget` 之内且不调 `beginActiveLock` ⇒ 「未命中不消耗冷却」仍成立 |
| A11 | 减免累计入池 | PASS | PASS | 命运的指引 / 加急加快锁定期写 `基准÷2`、史莱姆写 `200t`，三者都在读写 `cooldown_end` **之前** `return`；未锁定态仍走原冷却抵扣；池的全部写入点闭合（上锁归零 / 抵扣归零 / 三减免方） |
| A12 | 忍者周期钩子 | PASS | PASS | `onRoundFullyReset` 全仓**恰好 2 个**调用点（`tick` 情形 1「周期到期」+ `registerPlay` 周期边界块）；**未**挂进 `clearRoundBonuses`；`tick` 另两种情形在其之前全部早退；`registerPlay` 首行置 `sign_active_lock_played` 且为全仓唯一置真点；`forceResetRound` 复用 `clearRoundBonuses`，无第二份清单 |
| A13 | 忍者 1:00 宽限保险 | PASS | PASS | 判据读 `sign_active_lock_grace_end`（`LOCK_GRACE_TICKS` = 1200 = 1:00，两版本一致）；未出牌 ⇒ `forceResetRound` + 进冷却；出过牌 ⇒ 清宽限刻并保持锁定；**判据是 `lock_played` 而非 `play_count`**（周期边界会清 `count` 但不清该标记） |
| A14 | forge 每 tick 双触发幂等 | PASS | PASS | `PlayerTickEvents` 无相位过滤 ⇒ START+END 每 tick 调 2 次；逐状态展开「第 1 次做什么 / 第 2 次为何早退」；即便重复执行 `endLockAndStartCooldown`，第 2 次读到的基准已被改写为 `effective` 且池已归零 ⇒ 写出同一 `cooldown_end`，**无二次扣减** |
| A15 | 两版本功能对等 | PASS | PASS | 本批 15 个 Java 文件新增行归一化对称差比对：**11/15 逐行全等**，残差 4 个文件全部落在允许差异类（`AttachmentType` vs `AttachedDataKey`、`ModEffects.X` vs `.get()`/`Holder`、forge 双相位 tick、`CuriosApi` vs `CuriosCompat`、`ActionBarPayload` vs `ModNetwork.ActionBarMessage`、`EVENT_BUS`、`.sync(ByteBufCodecs)` vs `.sync()`、FQN/断行）⇒ **功能差异 = 0** |
| A16 | 新增 5 键的同步属性 | PASS | PASS | `sign_active_lock_sign/_lock_end/_reduction_pool/_lock_grace_end/_lock_played` 两版本同 id / 同默认值 / 同 Codec，**均未 `.sync()`**，且均**不在** F 的 28 条 `SYNCED_KEYS` 内 |
| A17 | 附件注册计数 | PASS | PASS | 逐条核计 `ModAttachments` 注册调用 ⇒ `N` = **67**、`F` = **69**（差异仅 `damage_effect_bonus` + `curse_original_amount`，本批新增 5 键两版本均有、不计入差异） |
| A18 | lang 四份一致性 | PASS | PASS | 同一新键 `msg.astral_dice.sign_active_in_effect` 落 4 份文件；中英文案两版本逐字相同；键数 **614 / 614** |
| A19 | 静态守门（附录 A） | 8/8 全绿 | 8/8 全绿 | `check_lang_sync`（neo/forge 各一次，zh/en 均 614 key 完全一致）、`tooltip_color_audit`（PASS 无违规）、`scripts/verify/` 全部 5 个脚本（chip_recipes / chip_acquisition / content_library / bountiful_pools / bountiful_instance_exclusions）退出码均为 **0** |
| A20 | R1b 其余 5 项 | PASS | PASS | 见 `docs/scan2/R1b-state.md` |
| A21 | 末影骰保命**只清有害效果（HARMFUL）** | PASS | PASS | N `EnderDiceHandler.java:175 setHealth(1.0F)` → `:176-181` 收集 `MobEffectCategory.HARMFUL` → `:182-184` `ModEffectRemoval.remove(player, Holder<MobEffect>)`；F `:174/:175-180/:181-183` 同构。原 `removeEffectsCuredBy(PROTECTED_BY_TOTEM)`（N）/ `removeAllEffects()`（F）已删。**旧写法确实会清增益**：`EffectCures.java:24 DEFAULT_CURES={MILK,PROTECTED_BY_TOTEM}` 且 javadoc 明写 "Cures any effect by default"。守卫 `ModEffectEvents.java:110-131`(N) / `:106-125`(F) 未被本提交改动，且其**内部通道是必需的**——先 `setHealth(1.0F)` 后 `isDeadOrDying()` 已为假，死亡放行分支不成立，而本模组有 6 个 HARMFUL 效果（两版本同名）只能靠 `isInternal()` 清掉 |
| A22 | 死亡清理降为 **LOWEST** 且保留 `isCanceled()` 早退 | PASS | PASS | 两版本 `onPlayerDeathClearEffects` 均为 `@SubscribeEvent(priority = EventPriority.LOWEST)`（N `:116/:117`、F `:113/:114`，改动前是裸 `@SubscribeEvent`）；`if (event.isCanceled()) return;` 保留（N `:120-121`、F `:117-118`，父提交已有且不在删除行内）。顺序已证：`LivingDeathEvent` 在 `LivingEntity.die()` 最顶部派发（N `:1409` / F `:1343`），早于 `dead=true` 与 `dropAllDeathLoot`；气囊 HIGHEST、末影骰 NORMAL 均早于 LOWEST。同文件另一 LOWEST 处理器挂在**不同事件**（`PlayerEvent.Clone`），共享的仅两个静态暂存表，写-读次序天然成立且幂等；跨文件同事件 LOWEST 的 `PlayerHostilityTracker.onLivingDeath` 状态不相交 |
| A23 | 删除的 23 行「仅默认值」附件写入 + `clearRoundBonuses` 调用是**安全**的 | PASS | PASS | diff 硬证两版本各删 23 行 `ModAttachments` 归零 + 1 行 `EffectCardPeriod.clearRoundBonuses` 调用（该方法只写 4 个附件默认值）；23 个 setter 实现体全部是单条 `player.setData(...)`。`copyOnDeath` 集合自核 = **2 个**（N `ModAttachments.java:176/280`、F `AstralData.java:79-82` 白名单 `rin_pages`+`komachi_damage_bonus`）；上游 `AttachmentInternals.java:52-53`（isDeath 只复制 copyOnDeath）与 `ServerPlayer.restoreFrom`（不复制 Capability）已核 ⇒ 删除对重生后玩家无副作用。必留项全在：①`MISAKI_SIGN_STACKS`（N `:147-153` / F `:144-149`），死亡事件早于 Curios `playerDrops`、`findFirstCurio` 返回**活栈引用**（字节码 `getStackInSlot`→`new SlotResult` 无复制）⇒ 写在仍佩戴的立牌上随掉落带走；②`JASMINE_`/`PADMAN_` 五件归零仍在（N `:157-174` / F `:153-165`），`onUnequip` 只在 tick lambda 内调用且传副本 ⇒ 死亡时唯此一路径有效；③11 处 `removeEffect`、`HealingManager.clear`、`EffectTimerGuard.clear`、`ChargeManager`/`DeathPreservedBonuses.preserveOnDeath`、`DiceCurioItem.removeGlassDiceOnDeath` 全部仍在 |

---

## 表 B：需用户定夺的差异（**本轮重点**）

> ✅ **已定夺（2026-09-15）** —— 见下方「裁定记录」，本节保留原始描述备查。

| # | 级别 | 项 | 现象 | 影响面 | 可选处置 |
|---|---|---|---|---|---|
| **D-B1** | 中 | **GUI 卸下时物品组件归零丢失** | Curios 的 **tick 轮询**路径把 `getPreviousStackInSlot` 的 **`copy()` 快照**交给清理逻辑，于是经 **GUI 卸下**时「护法剑气 / 扫地机移动累计 / 上班族攻防」的**物品组件**归零写进了副本而丢失。**死亡路径已覆盖**（`PlayerLifecycleHandler` 在 LOWEST 按 `findFirstCurio` 直接清装备中的栈） | 玩家经 GUI 摘立牌后，这三项**累计值残留**在物品上，重新戴上会继承旧累计；不影响对局正确性，属数值继承类瑕疵 | ①**修代码**：tick 路径改为操作槽内真实栈（而非 `copy()` 快照）；②**接受该缺口**，仅记录到 `AGENTS.md` 已知缺口 |
| **D-B2** | 中 | **`RailgunBolts:58` 用单参 `isHostile(Entity)`** | 电磁炮落雷的敌对判定走**单参**重载 = 「非玩家即敌对」或「命中玩家即视为敌对」，**未接**本批统一的「双向意图记录 + 仅同队豁免」口径（`PlayerHostilityTracker` / `HostileTargets.isHostile(viewer,target)`） | 电磁炮的落雷**打不打你视为敌对的玩家**，与其它 AOE 口径可能不一致；PVP 场景可感知 | ①**对齐**：改用双参重载（需 `viewer` 可用）；②**保持现状**：电磁炮明确定义为「落雷只打非玩家」，把该口径写进文档；③明确「电磁炮落雷**应当**打敌对玩家」并据此改 |
| **D-B3** | 低（文档） | **`docs/scan2/P5-equip-paths-linkage.md` §1.2 / §1.4 结论错误** | 该文档称 `onUnequip` 第 2 参 ≡ 第 3 参恒等 ⇒ 清理逻辑永不执行。经**三次独立字节码核验**（`javap` LVT + `ItemizedCurioCapability.onUnequip` 调用链）：`slot3 = stack` 才是被卸下的饰品，**仓库实现与代码注释是正确的一方** | 仅本地未入库文档（`docs/` 被 `.gitignore:58` 忽略），不影响构建产物；但后续若有人照该文档改代码会**改错** | ①写勘误并修正该文档 §1.2/§1.4；②删除该文档对应小节 |

---

## 表 C：观察项 / 建议对齐（不构成 FAIL）

| # | 项 | 现象 | 建议 |
|---|---|---|---|
| C1 | **「轮次归零」两处清理口径不等价** | `EffectCardPeriod.tick` **情形 1**（周期正常到期）额外调用 `ElectricGloveChipItem.disarmAoe(player)`（`N:397` / `F:398`），而忍者宽限期满走的 **`forceResetRound` 不调** ⇒ **宽限强重置不解除电击手套本周期已武装的法伤扩散**，正常归零会解除 | 二选一对齐：把 `disarmAoe` 上移到 `clearRoundBonuses`（两路径共用），或让 `forceResetRound` 一并调用 |
| C2 | 验证文档行号偏移 | `R1-B2-verification.md` 上文个别小节引用的 `BaseSignItem` 行号有 **±3** 偏移（如 `isSignActiveLocked` 实为 `N:239-252`） | 该文件已在功能点 3 开头加「行号口径」说明并以**实读行号**为准；后续以实读为准 |
| C3 | 代码注释与实际不符（R1-a 注 1） | `EffectTimerGuard.clear` 只写 `effect_timer_ends` 一个附件默认值（`ModAttachments.java:951-959`，**无 `copyOnDeath`**）⇒ 与那 23 个被删项**同类**，其可观察作用仅在「死亡 → 实体替换」窗口内。因此 `PlayerLifecycleHandler.java:137-138`（F `:134-135`）注释「下面保留下来的调用都带有附件之外的真实副作用」**对这一行不成立** | 用户要求「调用必须仍在」已满足，故不判 FAIL；建议**改写该注释**（改为「保留的调用中，`EffectTimerGuard.clear` 仅写默认值、可观察作用限于死亡窗口」），或按需一并删除该行 |
| C4 | 保命后中性效果残留（R1-a 注 2） | 1.21.1 保命后 `MARKED`(HARMFUL) 被清但 `GLOWING`(NEUTRAL，`MobEffects.java:96`) **不清** ⇒ 可能残留发光轮廓，与守卫 `:122-126`「发光与标记同寿命」口径不一致（按"只清 HARMFUL"字面**属预期**）。另 F `ModEffectRemoval.java:9` javadoc 指向不存在的 `ModEventHandlers`（**既有笔误，非本次引入**） | ①若要「发光与标记同寿命」严格成立 ⇒ 让保命清理额外去掉 `GLOWING`；②若坚持「只清 HARMFUL」字面 ⇒ 保持现状并把口径写进文档；③顺手修 forge 的 javadoc 笔误 |

---

## 表 D：已补齐（无遗留）

R1-a 条目 5/6/7 已由**第二位独立验证者**补做完成并双版本 PASS（见 A21-A23），**表内无待补项**。

> 口径更正：R1-a 的条目 5/6/7 实为「末影骰保命只清 HARMFUL / 死亡清理降 LOWEST 及排序 / 被删的 23 行默认值写入安全性」，此前本表曾误标为「减免汇总 / 星币兑换 / 待命立牌」，现已更正 —— 减免累计入池见 A11、忍者周期与宽限见 A12-A14、三个待命立牌见 A10。

---

## 表 E：运行期待验（B1 游戏内行为验证）

代码层可判定的项已全部落表；以下只能在游戏内确认：

| # | 项 | 备注 |
|---|---|---|
| B1-1 | 忍者主动后「1:00 内不出牌」路径：宽限期满 ⇒ 强重置出牌状态 + 进入主动冷却 | 幂等结论为纯代码论证（静态可证），实测用以消除运行期假设 |
| B1-2 | 忍者主动后「出过牌」路径：宽限失效、锁定跟随效果牌周期、周期完全重置才进冷却 | 同上 |
| B1-3 | 三态迁移实测：上锁 ⇒ 提示「主动技能生效中!」、按键无效、`cooldown_end` 保持 0；解锁 ⇒ 抵扣后起冷却 | 涉及 7 个立牌 |
| B1-4 | 锁定期间使用加急加快 / 命运的指引 ⇒ 只入池、不改当前冷却；解锁时一次性抵扣 | 池行为在**未锁定态**仍需实测确认未回归 |
| B1-5 | 电击手套 AOE 在「宽限强重置」后是否仍武装（C1 的运行期症状） | 与 C1 判定配套 |

环境已就绪：`mt.conf`、33 个用例（含 `AIRBAG-BYPASS-KILL` / `FEN-SPLASH-MAIN-TARGET` / `KOMACHI-EXTRA-PLAY` / `SPELL-TRUE-DAMAGE`），分离式启动器，双版本 `run/*/mods` jar 为今日 16:45。

---

## 裁定记录（用户，2026-09-15）

| # | 项 | 裁定 | 实施状态 |
|---|---|---|---|
| D-B1 | GUI 卸下组件归零丢失 | **修代码**：tick 路径操作槽内**真实栈**（不再传 `copy()` 快照） | 🔧 实施中 |
| D-B2 | `RailgunBolts` 单参 `isHostile` | **对齐双参口径**：电磁炮落雷**应打**你视为敌对的玩家（改用 `HostileTargets.isHostile(viewer,target)`） | 🔧 实施中 |
| D-B3 | `docs/scan2/P5-equip-paths-linkage.md` §1.2/§1.4 结论错误 | **写勘误并修正该文档** §1.2 / §1.4 | 🔧 实施中 |
| C1 | 「轮次归零」两处清理口径不等价 | **做**：`disarmAoe` 上移到 `clearRoundBonuses`，两条归零路径共用同一清理 | 🔧 实施中 |
| C2 | 验证文档行号偏移 | 无需裁定（已在 R1-B2 文档内加「行号口径」说明，后续以实读为准） | ✅ 已处理 |
| C3 | `PlayerLifecycleHandler` 注释与实际不符 | **不做**（注释保持原样） | ⏸ 保持现状 |
| C4 | 保命后 `GLOWING` 残留 | **做**：保命清理时一并移除 `GLOWING`，使发光与标记同寿命 | 🔧 实施中 |
| C4b | forge `ModEffectRemoval` javadoc 笔误 | **做**：修正 javadoc 指向的类名 | 🔧 实施中 |

> **冻结解除**：裁定已下，允许写 `AGENTS.md`、允许改动语义、允许进入实施与提交。
> **实施范围**：`neoforge-1.21.1` + `forge-1.20.1` 双版本同步；收尾含双 CHANGELOG 同步、双版本构建 + 自动部署、本地提交（**不 `git push`**）。
> **实施后**：进入 **R2 回归验证**（由实现者之外的独立验证者执行；1.21.1 通过后解除 1.20.1 的 `GATED`），再跑 **B1 游戏内行为验证**（表 E）。

### 实施进展（commit `027e1e9`，本地已提交未 push）

| # | 项 | 结果 |
|---|---|---|
| D-B1 | GUI 卸下组件归零 | ⛔ **结案:接受现状,零 mixin,只写文档**（用户 2026-09-15 最终裁定）。经过三轮取证:① 原裁定方案「tick 路径操作槽内真实栈」**不可行** —— tick 回调触发时槽位已空(第 2 参 `EMPTY`、第 3 参 = `getPreviousStackInSlot` 的 copy 快照),被卸下的那件已在容器点击中经 `split()/copyWithCount()` 复制进背包,**既不能按引用也不能按槽位取到**;② 备选方案 A「卸下前清理」(`ICurio#canUnequip`/`CurioCanUnequipEvent`)**已证不安全且不可用** —— `simulate` 形参直到 `extractItem` 末尾才被消费(N 偏移 177 / F 174)、事件在其**之前无条件触发**、`SlotContext` 与事件载荷**都不含** simulate,而 Curios 自身即把 `simulate=true` 当查询(`SlotItemHandler#mayPickup` N L65-66 / F L102-104,原版 `doClick` 六处调用)⇒ 点一下饰品格即误清;③ 可行的组合(B `DynamicStackHandler#extractItem` super 前 / C `CurioSlot#set` HEAD / D `ItemStackHandler#setStackInSlot` HEAD + `instanceof` 守卫 / E `AbstractContainerMenu#moveItemStackTo` HEAD)需 **2~3 处 mixin 深入 Curios 与原版容器内部**,且**即便全上 `CPacketDestroy` 仍漏**(判「同物品」而跳过,已记于 `CurioSlotUtil:120-121`)。**缺口影响**:仅为「经 GUI 摘下后旧累计值留在物品上、重戴继承」,**不影响对局正确性** ⇒ 不值得以 3 处 mixin 换取。两侧代码**均未改动**。论证全文:`docs/batch3/D-B1-optionA-proof.md`、`docs/batch3/D-B1-BC-inject-proof.md`;`AGENTS.md` 卸下条已固化裁定与理由。 |
| D-B2 | 电磁炮双参口径 | ✅ 已实施：N/F `damage/RailgunBolts.java:64` 改 `HostileTargets.isHostile(cause, target)`，`cause = bolt.getCause()`；两文件逐字一致。⚠️ 待验：`getCause()` 为 null（非本模组雷击）时的行为 |
| D-B3 | P5 §1.2/§1.4 勘误 | ✅ 已完成：先 `javap` 重新取证（两 jar 全部 `.class` 穷举），顶部勘误块 + 18 处 `🔴 勘误(2026-09-15)` 标注；**§2 装卸五路径矩阵受影响且已修正**（①②④ 由「❌ 跳过」改为「✅ 判据通过」、⑤「handleDrops→onUnequip」不成立、E1 blocker 作废）；原字节码 dump 全部保留 |
| C1 | 统一轮次归零口径 | ✅ 已实施：N `EffectCardPeriod.java:199` / F `:200` 在 `clearRoundBonuses` 内调 `disarmAoe`，`tick` 情形 1 的原调用已删（**`EffectCardPeriod` 内仅剩 1 处**）；三条归零路径共用同一口径。⚠️ 属**有意行为变更**（`registerPlay` 边界与 `forceResetRound` 原先不解除武装），并**顺带修掉**「武装态跨周期带到新周期、白嫖一次扩散」的漏洞。⚠️ **口径更正（R2 指出）**：「全仓恰 1 处 `disarmAoe` 调用点」**不实** —— 实为 **2 处/版本**（`EffectCardPeriod:199/200` + **改前既有**的 `SpellDamageRegistry:384`「触发即消耗」，非归零路径；该行的不实措辞亦出现在提交信息 `027e1e9` 中，代码与行为不受影响） |
| C2 | 文档行号偏移 | ✅ 已在 R1-B2 文档内加「行号口径」说明 |
| C3 | `PlayerLifecycleHandler` 注释 | ⏸ **不做**（按裁定保持原样） |
| C4 | 保命后 `GLOWING` 残留 | ✅ 已实施：N `EnderDiceHandler.java:189-190` / F `:188-189`，显式白名单 HARMFUL + 额外 `GLOWING`，**未**退化 `removeAllEffects()`。已核实 `GLOWING` 确由 `MarkManager.apply` 与标记同寿命施加，但**另有原版来源**（光灵箭/指令/其它模组）⇒ 加**门控**（仅当玩家确实带本模组 `MARKED` 时才追加，收集阶段先于任何移除）。**用户裁定：保留门控** |
| C4b | forge javadoc 笔误 | ✅ 已实施：F `event/ModEffectRemoval.java:9` → `ModEffectEvents#onModEffectRemovalPrevented`（N 原本正确、未改动） |

**收尾**：双 CHANGELOG 已合并进现有未发布条目；1.2.1 段条目数自检 **47 ↔ 47**；`AGENTS.md` 四处已同步；两版本 `BUILD SUCCESSFUL` 且 jar 更新 + 已部署整合包；**未 `git push`**。

**验证状态**：
- **R1**（差异来源）：`docs/batch2/R1-B2-verification.md`、`docs/scan2/R1a-damage.md`、`docs/scan2/R1b-state.md`
- **R2 回归验证（`027e1e9`，已完成）**：`docs/batch3/R2-verification.md` —— ②③④⑤ **全 PASS、FAIL 0 / 无法判定 0**；回归面全绿（lang **614/614 ×2**、`tooltip_color_audit` PASS、`scripts/verify` **5/5** 全 exit 0、CHANGELOG 1.2.1 段 **47↔47**、`AGENTS.md` 四处与代码相符、`git show --stat` **10 文件与应改文件吻合、无夹带**）；产物 `javap` 证实四项改动均已入 jar
  - ② 关键澄清：`bolt.getCause()` 为 null ⇒ `HostileTargets.java:56` 首行 `if (!(viewer instanceof Player viewerPlayer)) return false;` **不 NPE、不走宽判**，退化为改前单参语义；且自然雷/其它模组闪电被 `isRailgunBolt` 早退挡在判定之外 ⇒ **生产路径不可达**
  - ③ 新证据：三条归零路径均同时清零出牌数与冷却 ⇒ **无**「周期未结束却解除武装」的新路径
  - ④ 顺序正确：收集（`:178-182`）→ 门控（`:189`）→ 移除（`:192-194`），增益全保留
  - R2 新指出（非本次引入）：CHANGELOG 1.2.1 段**重复 `### 工程`/`### Project` 标题**（父提交 `216f6cc` 已存在、中英一致，可选清理）
- **B1 游戏内行为验证（已完成，1.21.1）**：正式轮（新探针 `82BA36BC` + 冷启动）**PASS 12 / FAIL 3**，1.21.1 全 15 条跑完；逐条证据 `temp/b1_cases/*.log`，汇总 `docs/batch3/B1-in-game-results.md`
  - **上一轮 `cases` 全线 ERROR 的真因（已更正；此前「存活探测误判」的判断是错的）**：`mt.ps1:65-68` 的 `Start-Process -Wait` 会等**整棵进程树**，而 `mt_launch` 以 `-NoNewWindow` 起 `cmd→gradlew→客户端` ⇒ 全流程与 `--phase launch` **都会阻塞到客户端退出**（`run_state`：launch PASS `ts=18:41:03.8`、cases `ts=18:41:18.2`，仅隔 14.4s，即客户端退出后才进 cases）。**探测本身正确**：正控实测 `Alive=True`、`Titles=[Minecraft NeoForge* 1.21.1 - 单人游戏]`
  - **可行用法**：`Start-MtDetached.ps1` 让 launch 脱离（轮询 `MT_LAUNCH: OK`）+ 前台逐条 cases，且**每条前须手工 `mt_assert snapshot`**（单阶段 launch 不写快照，否则用陈旧 offset 出假 FAIL）
  - **三条基线 FAIL 全部转 PASS**：`SPELL-TRUE-DAMAGE` 17/17、`RAILGUN-AOE-SCOPE` 30/30、`FEN-SPLASH-MAIN-TARGET` 19/19（复跑；首轮 `ratio_ok` 不稳定）
  - **三条 FAIL**：① `HOSTILE-TARGET-NEUTRAL` 25/26 = **用例时序缺陷**（臂装步自己触发的落雷 ~1s 先劈死北极熊，注入器每条 ~2.5-3s，NBT 回读断言**永不可达**；产品面 25 条全绿）② `NANCY-LU-PEARL-IMMUNE` 17/29 = **ERROR**（探针 `astral_bugfix_probe.js:1613` 的 `setDeltaMovement(0.0,0.0,0.8)` 在 1.21.1 **无该重载**）⇒ **产品不可判** ③ `ANVIL-STAR-UPGRADE` 45/53 = **真失守**（`AP_Z1_ERR:lost_after_close:-1:5`；`AP_S{1,2,3}_INV_AFTER_CLOSE:7` 期望 10 ⇒ **铁砧附加槽残留 3 星币未回背包**）。**归因完成（`docs/batch3/ANVIL-attribution.md`）：判定为「用例/探针缺陷」，非产品缺陷 —— 那 3 枚星币是「没退回」而非「被吞」**：原版 `ItemCombinerMenu#removed` → `access.execute(clearContainer(player, inputSlots))` 覆盖**整个 2 格输入区**（`ItemCombinerMenu.java:120-124` + `AbstractContainerMenu.java:585-590`），槽0/槽1 行为相同；但 `ContainerLevelAccess.NULL` 覆写 `evaluate` 恒返回 empty 而 `execute` 是接口 default ⇒ **`NULL.execute()` 永不执行**（`ContainerLevelAccess.java:10-15/32-37`）；两参构造 `new AnvilMenu(id,inv)` 即 `NULL`（`AnvilMenu.java:48-55`），实跑 `AP_S*_SRC:direct` 证实探针 `getMenuProvider` 返回 null 后兜底走两参构造 ⇒ `removed()` 静默 no-op、3 枚随菜单对象丢弃。算术守恒：25 = 7(对照) + 18(槽1)，18−15 = 3 = 用例刻意多放的 `ANVIL_EXTRA_IN_SLOT`，S2/S3 逐条复现 ⇒ **扣费正确**（`AnvilUpgradeHandler` 只写 `setOutput/setMaterialCost/setCost`，从不碰槽；扣费在原版 `AnvilMenu#onTake:84-95`）。**非本模组引入**：mixin 清单无 `AnvilMenu/ItemCombinerMenu/AbstractContainerMenu/ContainerLevelAccess`，全仓无 `repairItemCountCost`。**唯一未闭合项**：`AP_Z1_ERR:lost_after_close:-1:5` 的 9→5 差额（探针吞掉 `closeContainer()` 异常）需加只读读数实跑闭合；**判定性检查：`_SRC:block` 时 `_INV_AFTER_CLOSE` 必须为 10，若仍为 7 则本结论作废**
  - ⚠️ **本批两处改动无游戏内证据**：电击手套「轮次归零解除武装」(C1) 与末影骰「保命清 `GLOWING`」(C4) 在全部 1.21.1 用例/探针里 **0 命中** ⇒ **需补用例**
  - **未跑**：1.20.1 的 16 条（超 B1 范围）
  - 收尾：java 进程 0 条、git 仍 `7b9e711` CLEAN、未改任何产品源码或工具链脚本

### B2（测试资产修复 + 补用例 + 实跑，1.21.1）

| 用例 | 判定 | 结论 |
|---|---|---|
| `ANVIL-STAR-UPGRADE` | ✅ **PASS 53/53** | `_SRC:block:why=none`、`_INV_AFTER_CLOSE:10` ⇒ **ANVIL 归因成立且判定性检查通过**（3 枚星币确为「没退回」，修复后按原版语义退回）。真因是 Rhino **找不到 `p.getDirection()`** ⇒ block 路径恒落 `direct`（NULL access） |
| `ELECTRIC-GLOVE-ROUND-RESET`（新增） | ✅ **PASS 18/18** | **C1 取得游戏内证据**：三条轮次归零路径**全部**解除电击手套武装 |
| `HOSTILE-TARGET-NEUTRAL` | ✅ **PASS 29/29** | 时序缺陷已修，产品面全绿 |
| `NANCY-LU-PEARL-IMMUNE` | ⚠️ 数据断言全 PASS | 真实珍珠相位首次跑通（`window_max=19/drop=0` 对 `drop=5`）；仅 `kubejs` 断言因**探针侧 Rhino DamageSource 异常** FAIL |
| `ENDER-DICE-TOTEM-GLOWING`（新增） | ❌ **FAIL 4/12** | 环境内**打不出致死伤害**（`p.hurt` 参数错配、`/damage` 推迟到 tick 末、`causeFallDamage(100)` 未掉血）⇒ 测试资产不可用、**C4 仍无游戏内证据** |

- 工具链 4 项已修并各有实跑证据（`launch` 改脱离式、单阶段写快照、用例扫描排除 `.mt_*`、探针自动部署 + SHA256 复核；5 轮冷启动均 `MT_LAUNCH: OK (40s)`）。**残留**：用管道捕获包装调用时包装进程仍被子进程句柄拖住（非 `-Wait` 等树），前台直调不受影响
- **口径更正（推翻我此前的汇报）**：`AP_Z1_ERR:lost_after_close:-1:5` 里的 **`5` 是物品栏槽位下标、不是枚数** ⇒ **不存在「9→5 的 4 枚差额」**（新读数 `coins=9` 自证）
- **无产品级 FAIL**；断言未弱化
- 构建：1.21.1 `MT_BUILD: OK (4s)` / 1.20.1 `MT_BUILD: OK (18s)`，均 exit 0 + jar 更新 + 已部署
- 提交：**`517954f`**（测试资产 15 文件）+ **`a67caa7`**（产品 13 文件），**无混提**，工作树干净，**未 push**
- 真整合包目录与 `build/libs` 哈希一致（1.21.1 `977B1439` / 1.20.1 `60F83938`）
- ⚠️ **待查异常（非本批引入）**：`run/1.20.1/mods` 的 jar（`961403 B` / `F5918EE5`）与 `build/libs`（`979097 B` / `60F83938`）**不同**，1.21.1 两侧一致 ⇒ 经 `runClient` 的 1.20.1 测试**可能测的不是要发货的字节码**（整合包目标正确，不影响发布，但影响 1.20.1 测试可信度）
- **未覆盖**：1.20.1 见下节 B4；1.21.1 其余回归条目本轮未复跑

### B4（1.20.1 全量实跑，commit `9d0ea49`）

**18 条：16 PASS / 2 FAIL / 0 ERROR / 0 SKIP，无产品级 FAIL**（未触发卡死护栏）

- ✅ **`damage(amount, source)` 修复被证实有效**：18 条中 **17 条带 kubejs 断言，17/17 全 PASS**（此前因 `minecraft:5.0` 异常 FAIL）；`NANCY-LU-PEARL-IMMUNE` 的**产品面读数全部正常**（`window=20/20`，drop/hurt/invul/marked 均达标）
- ❌ `ENDER-DICE-TOTEM-GLOWING-1.20.1` —— **探针 API 可见性**：`astral_bugfix_probe.js:3148-3153` 的 `damageSources().source()` 在 1.20.1 不可见，`catch` 后**直接 return**、跳过全部兜底（含原版 `/damage`）⇒ 致死伤害**从未施加**（`hp=20 / fired=0 / api=n/a`）⇒ **C4 的 GLOWING 门控本轮无读数，应视为未验证**（非产品失败）
- ❌ `NANCY-LU-PEARL-IMMUNE` —— 仅用例要求的 `tel=1` 在 **P2 实测 0**（P3 对照 `tel=1`）⇒ **珍珠命中几何/判据问题**，非产品问题
- ⚠️ **工具链坑（新发现）**：**全流程分支忽略 `--version` 且受 1.21.1 门控**（`mt.ps1:339/379`）⇒ `mt.ps1 --version 1.20.1` 并不会只跑 1.20.1，必须走**分步路线**（`--phase build/env/launch/cases`）。这解释了首次全流程跑 1.20.1 时的 `GATED`
- 提交：`9d0ea49`（仅 4 个测试资产，+445/−179）；产品文件全程干净；`--phase stop --force` 后无 java/gradle/客户端残留
- 运行记录：`docs/batch3/B4-1201-run.md`（`docs/` 被 `.gitignore` 忽略，未入库）

### B5（C4 收口，commit `4e1581c`）—— **本批最后一个缺口闭合**

**C4「保命只清 HARMFUL + 带标记时额外清 `GLOWING`、增益全留」= 双版本 PASS 29/29，断言零弱化，产品缺陷候选：无。**

| 相位 | 1.21.1 读数 | 1.20.1 读数 | 含义 |
|---|---|---|---|
| P1（带 `MARKED`+`GLOWING`+增益） | `marked=0:glow=0:speed=1:hp=8.17:alive=1:totem_cd_left=5921:bypass=0:fired=1:api=fall` | **逐字段一致** | `MARKED` 与 `GLOWING` 均被移除、增益全留；`fired=1` 证明保命真的触发 |
| P2（带 `GLOWING` 无 `MARKED`） | `marked=0:glow=1:speed=1:hp=5` | **逐字段一致** | `GLOWING` **被保留** ⇒ **门控成立** |
| VERDICT | `gated_clear=1:control_keep=1:marked_cleared=1` | **逐字段一致** | 三条判据全中 |

- **阻塞 A 的根因（可复用的平台差异）**：`DamageSources#source(ResourceKey)` 在 **1.21.1 是 public**，但在 **1.20.1 整个方法都不存在**（三个重载全 private）⇒ 1.20.1 侧必须用公开工厂 `Entity#damageSources()` → `DamageSources#generic()`；**且 `catch` 里过早 `return` 会吞掉整条兜底链**（B4 的 `hp=20 / fired=0 / api=n/a` 即由此产生）
- **归因更正**：此前「`ENDER-DICE-TOTEM-GLOWING-1.21.1` FAIL 4/12」出自 **B3 之前的旧探针**（`p.hurt` + `length()` NaN + `p.damage(src,n)` 顺序），**不是产品问题**
- 1.20.1 修复效果：`src_ex:TypeError` → `fall=true`；`hp 20→8.17`；`fired 0→1`；`api n/a→fall`；`bypass -2→0`；用例 **FAIL → PASS 29/29**
- 提交 `4e1581c`（仅 `scripts/test/**` 两个探针，+137/−38）；产品源码/lang/CHANGELOG/AGENTS.md/build.gradle/tools **零改动**；**未 push**
- 阶段：1.21.1 build OK(3s)/launch OK(40s)/cases PASS；1.20.1 build OK/env OK/launch OK(39s)/cases PASS；**卡死护栏未触发**（单条用例 <100s）；收尾 `--phase stop --force` 后无残留
- 运行记录：`docs/batch3/B5-c4.md`（`docs/` 被 `.gitignore` 忽略，未入库）

### ✅ 验证闭环总表

| 验证轮 | 覆盖 | 结果 |
|---|---|---|
| R1 | 7 条目（伤害映射/保命/死亡清理/被删写入）+ 10 功能点（批次二三态化）+ 8 项（冷却基准/敌对判定/装备路径） | 全 PASS；FAIL 0、无法判定 0（唯一无法判定已裁定） |
| R2 | `027e1e9` 的 ②③④⑤ + 回归面 | 全 PASS；lang 614/614×2、`scripts/verify` 5/5、CHANGELOG 47↔47、`git show --stat` 无夹带 |
| B1 | 1.21.1 全 15 条 | PASS 12 / FAIL 3（3 条旧基线全部转 PASS） |
| B2 | ANVIL + ELECTRIC-GLOVE + HOSTILE + NANCY + ENDER | ANVIL 53/53（判定性检查通过）、ELECTRIC-GLOVE 18/18、HOSTILE 29/29 |
| B4 | 1.20.1 全 18 条 | 16 PASS / 2 FAIL / 0 ERROR，无产品级 FAIL |
| B5 | C4（`ENDER-DICE-TOTEM-GLOWING`）双版本 | **PASS 29/29 ×2，产品缺陷 0** |

**⇒ 代码层与游戏内均未发现功能性失败；本批全部裁定项均已闭环。**
- **D-B1 方案取证**：`docs/batch3/D-B1-optionA-proof.md`（A 不安全已否）；B/C 注入点补证进行中（`docs/batch3/D-B1-BC-inject-proof.md`）

---

## 后续流程

1. 用户对**表 B** 三项定夺（`D-B1` / `D-B2` / `D-B3`），并顺带对**表 C** 四项（`C1`-`C4`）给出「改 / 不改」。
2. 按定夺结论修复（表 C 的对齐项可一并处理）。
3. 表 E 运行期实测（B1）。
4. 进入 **R2 回归验证**（1.21.1 → 1.20.1，后者的 `GATED` 状态在 1.21.1 PASS 后解除）。

## 第一遍（R1）总结

| 线 | 覆盖 | 结果 |
|---|---|---|
| R1-a | 7 个条目（伤害映射 1-4、保命只清 HARMFUL、死亡清理 LOWEST、被删写入安全性） | **7/7 PASS**（双版本） |
| R1-B2 | 10 个功能点（批次二「立牌主动技能三态化」） | **10/10 PASS**（双版本） |
| R1b | 8 项（冷却基准 / 敌对判定 / 装备路径 / 文档勘误） | 5 PASS + 1 部分符合(D-B1) + 1 无法判定(D-B2) + 1 文档勘误(D-B3) |
| 静态守门 | lang ×2、tooltip 染色、`scripts/verify/` ×5 | **8/8 退出码 0** |

⇒ **代码层未发现功能性失败**；3 项需定夺（D-B1 部分符合 / D-B2 无法判定 / D-B3 文档错误）+ 4 项建议对齐（C1-C4）。
