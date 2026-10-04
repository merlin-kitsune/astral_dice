# R1b 独立验证状态（冷却/装卸/兑换/敌对）

- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-1.20.1-1.21.1`，HEAD=`216f6cc`
- 被验提交：`6daebf9`（fix(1.2.1): 交互时序审计裁决落地(双版本)）
- 验证者：独立 subagent（与实现者无关）；只读仓库，仅追加本文件。
- 判据：代码是否真的实现了用户要求；每条给 `文件:行号`；无法判定就写「无法判定」。

## 状态条目（增量追加）

### [1] 枪匠冷却基准（路线 A） — **PASS**（HEAD=216f6cc 三态化后语义仍成立）

新增附件：
- `neoforge-1.21.1/.../component/ModAttachments.java:419-429`（`sign_active_max_cooldown`，`Codec.LONG`，默认 0，**未 `.sync()`**）
- `forge-1.20.1/.../component/ModAttachments.java:388-396`（同，`AttachedDataKey`，未 `.sync()`、未进 `SYNCED_KEYS`）

写入点（"起冷却/锁定"两路都写）：
- `neoforge-1.21.1/.../item/sign/BaseSignItem.java:130-145`：锁定路 `setSignActiveMaxCooldown(player, signCooldownTicks)`（:137）；立即起冷却路（:139 写 `cooldown_end`、:141 写基准）。
- 等待类技能（占星师/秘密侦探/枪匠）成功释放时同样成对写：`neoforge-1.21.1/.../combat/DiceCombatEvents.java:248-252`（haiqing）、`:272-276`（bonnie）、`:289-293`（moses，取 `MosesSignItem.signCooldownTicks`）；forge 对应 `DiceCombatEvents.java:253-257 / 277-281 / 295-299`。
- 三态化（HEAD 216f6cc）追加：`endLockAndStartCooldown` 抵扣后把基准改写为 `effective`（neoforge `BaseSignItem.java:296-308`、forge `:294-...`），电流核心完成冷却时把基准置 0（`CurrentCoreChipItem.java:103` 两版本）。

四个减免方均读实际基准：
| 减免方 | neoforge | forge | 基准来源 |
|---|---|---|---|
| 命运的指引 | `item/card/FateGuidanceCardItem.java:67-83` | `:66-82` | `getSignActiveMaxCooldown`，缺失才回退 `WeirdDiceHandler.signCooldownTicks`（:70） |
| 加急加快（扫地机被动） | `item/sign/JasmineSignItem.java:100-120`（:104 读基准，:106 回退常量） | 同（:99-119） | 实际基准；`maxCooldown/2` 不再用 `SIGN_ACTIVE_COOLDOWN_TICKS` |
| 史莱姆 −10 秒 | `item/sign/LuluSignItem.java:51-61` | 同（:51-61） | **绝对量 200 tick**（与基准无关，符合要求） |
| 电流核心分母 | `item/chip/CurrentCoreChipItem.java:63-72, 93`（`base = max>0? max : 180*20`） | 同（:63-72, 93） | 实际基准（缺失回退 180s） |

四个"特别核实"：
1. **加急加快不再免费立即完成** ✅ 新式 `Math.max(now, cdEnd - maxCooldown/2)`（JasmineSignItem.java:117-118）以实际基准的一半为扣减量；旧式用常量 180s/2=1800 tick，对 60s 基准（诡异骰子）会一次扣穿 ⇒ 旧行为确为"免费立即完成"。
2. **命运指引不再按 180s 过度减免** ✅ `FateGuidanceCardItem.java:72` 改 `maxCooldown/2`。
3. **史莱姆不再写 0 哨兵** ✅ `LuluSignItem.java:58-60`：判据由 `cdEnd > 0` 改为 `cdEnd > nowTick`，夹底由 `Math.max(0, ...)` 改为 `Math.max(nowTick, ...)`（0 是"无冷却"哨兵值）。
4. **电流核心分母不再硬编码 180s** ✅ `CurrentCoreChipItem.java:93` 传 `getSignActiveMaxCooldown(player)`；`:65` 分母 `maxCooldownTicks > 0 ? ... : MAX_COOLDOWN_SECONDS*20L`。

三组合 改前→改后（**命运的指引**，扣减 = 基准÷2；基准由 `WeirdDiceHandler.signCooldownTicks` = 诡异减半→充能再 ×0.8 上取整，枪匠走 `MosesSignItem.signCooldownTicks`:112-120 基础 120s）：

| 组合 | 本次基准 | 旧扣减（按 180s 基准） | 旧剩余 | 新扣减（基准÷2） | 新剩余 |
|---|---|---|---|---|---|
| 枪匠单独 | 2400t / 120s | 1800t / 90s | 600t / **30s** | 1200t / 60s | 1200t / **60s** |
| 枪匠+诡异 | 1200t / 60s | 900t / 45s | 300t / **15s** | 600t / 30s | 600t / **30s** |
| 枪匠+诡异+充能 | 960t / 48s | 720t / 36s | 240t / **12s** | 480t / 24s | 480t / **24s** |

同一三组合的**加急加快**（扣减同为基准÷2，旧法扣常量 1800t）：旧剩余 30s/0s（`Math.max(now,…)` 夹到 now ⇒ **免费立即完成**）/0s ⇒ 新剩余 60s/30s/24s。
电流核心档位：如"枪匠单独、剩满 2400t"旧 cost = ceil(min(1,2400/3600)*6)=4，新 = ceil(1.0*6)=6（分母改为 2400）。
常量佐证：`GameplayConstants.java:34` `CHARGE_COOLDOWN_REDUCTION=0.2`、`:43` `SIGN_ACTIVE_COOLDOWN_SECONDS=180`；`MosesSignItem.java:38` `ACTIVE_COOLDOWN_SECONDS=120`；`ChargeManager.cooldownTicks` = `ceil(base*0.8)`。

两版本对等：上述 6 个文件在 forge 侧逐行同构（含注释），无单侧遗漏。
备注：`6daebf9` 的注释写"起冷却时与 cooldown_end 成对写入"，HEAD 的后续提交 `216f6cc`（三态化）已把"锁定期"改为只写基准 + 减免池，语义要求（按实际基准计算）仍然成立，故判 PASS。

### [2] A9 出牌轮 — **PASS**

- 未打满的一轮在所有计时器结束后进冷却：`neoforge-1.21.1/.../item/card/EffectCardPeriod.java:369-388`（`tick`：`cooldown<=0 && played>0 && played<maxAllowed` → `getRemainingBlockTicks(player) > 0` 则等待，否则 `setEffectCardCooldownEnd(now + recoverTicks)`）。forge 同构：`forge-1.20.1/.../EffectCardPeriod.java:383`（等待分支）+ `:336`（打满/收尾统一写入点）。
- **不作废剩余出牌数**：收尾分支**没有**任何 `setEffectCardPlayCount(player, maxAllowed)` 之类补齐（全文件仅 `:323` 出牌 +1、`:315`/`:390` 归零）；forge 同（grep `setEffectCardPlayCount(player, ` 仅见 +1 与 0）。
- `played >= max` 仍立即冷却：同 `tick` 的 `if (played < maxAllowed)` 仅对未打满分支做"等计时器"处理，`played >= maxAllowed` 直接落到 `:384` 起冷却。✅
- 冷却不被补牌重启/延长：`registerPlay` 唯一写 `cooldown_end` 处为条件式 `if (cooldown <= now) { setEffectCardCooldownEnd(now + cooldownTicks); }`（neoforge `:332-338`，forge `:336`）。✅
- `git grep -n "Math.max(cooldown" -- "*.java"` → **0 命中，exit=1**（已记命令与退出码）。✅

### [3] Curios `onUnequip` 三参语义 — **判据 PASS / 归零落地 FAIL（GUI 路径）**

**独立反编译裁定（我自己跑的命令，非引用仓内注释）**：缓存 jar 两个：
`C:\Users\xmace\.gradle\caches\modules-2\files-2.1\maven.modrinth\curios\IPQlZkz1\452175b95ad3db6ff58bb8968f6bf7a9d1e0f480\curios-IPQlZkz1.jar`（forge，`META-INF/mods.toml`/javafml loaderVersion `[41,)`）
与 `...\yohfFbgD\418fcd42e3a7844c9bdc71c9b6401fdb3894e0c4\curios-yohfFbgD.jar`（neoforge，`neoforge.mods.toml`）。命令与关键字节码：

1. `javap -p -l -cp <jar> top.theillusivec4.curios.api.type.capability.ICurioItem`
   → `onUnequip(SlotContext, ItemStack, ItemStack)` 的 LocalVariableTable：`slot2 = newStack`、`slot3 = stack`（**两版本一致**）。接口命名只说明"第 3 参叫 stack"，不足以定谁是被卸下者。
2. `javap -c -p -l -cp <jar> ...common.capability.ItemizedCurioCapability`（**枢轴**）：
   ```
   public void onUnequip(SlotContext, ItemStack);
      4: aload_1        // slotContext
      5: aload_2        // ← 第 2 参（LVT 名 newStack）
      6: aload_0
      7: invokevirtual getStack()
     10: invokeinterface ICurioItem.onUnequip:(SlotContext;ItemStack;ItemStack;)V
   ```
   ⇒ **第 2 参 = 调用方传给 `ICurio.onUnequip(ctx, X)` 的 X；第 3 参 = 该 capability 自己持有的栈 `getStack()`**。两版本字节码相同。
3. `javap -p -c` `CurioItemCapability$Provider` + `CuriosImplMixinHooks.getCurio` + `CuriosEventHandler.attachStackCapabilities`：
   `getCurio(stack) = stack.getCapability(CuriosCapability.ITEM)`，而 capability 在
   `CuriosEventHandler.attachStackCapabilities(AttachCapabilitiesEvent<ItemStack>)` 里以
   `new ItemizedCurioCapability(curioItem, event.getObject() 的 ItemStack)` 构造
   （`64: new ItemizedCurioCapability / 68: aload 4 / 70: aload_2 / 71: invokespecial`）
   ⇒ `getStack()` 返回**该 capability 所绑定的那个 ItemStack 实例**（不是"槽位当前内容"）。
4. `javap -c -p` `CuriosEventHandler.tick` 的三个关键偏移：
   - `163-172`：`current = getStackInSlot(i)`（局部 12）；`174-179`：`cap_current = getCurio(current)`（局部 13）
   - `161-214`：**先** `cap_current.ifPresent(curioTick)`（InvokeDynamic #27 → `ICurio.curioTick(ctx)`），
     `217-239` 再 `isEquipped`/`curioAnimate`；**之后**才在 `242-270` 做 `!ItemStack.matches(current, prev)` 闸门
     ⇒ 仓内注释"curioTick 先跑、matches 后比"**成立**（本模组在 curioTick 写装备中栈的组件，故"同物品但组件变化"这一支**可达**）。
   - `252-278`：`prev = getPreviousStackInSlot(i)`（局部 15），`cap_prev = getCurio(prev)`（局部 16）
   - `533-552`：`cap_prev.ifPresent(InvokeDynamic #29)`，捕获参数 = `(aload 11 = SlotContext, aload 12 = 当前槽位栈)`
     → `lambda$tick$31` → `ICurio.onUnequip(ctx, 当前槽位栈)` ⇒ **第 2 参 = 槽内新内容（真卸下时为空），第 3 参 = prev（被卸下者）**
   - `735-746`：`cap_current.ifPresent(#30)`，捕获 `(ctx, aload 15 = prev)` → `onEquip(ctx, prev)`（对称）
   - `795-798 / 863-869`：`setPreviousStackInSlot(i, 当前栈.copy())`（`m_41777_` = `copy()`）
   - `CurioStacksHandler.loseStacks`：`301-313` `getCurio(被移除栈).ifPresent(...)` → `lambda$loseStacks$4`：
     `1: aload_0(ctx) / 2: getstatic ItemStack.f_41583_(EMPTY)` → `onUnequip(ctx, EMPTY)`；`316-322` 之后才 `setStackInSlot(i, EMPTY)`（先回调后清槽）
   - `CPacketDestroy`：`282-289` `getCurio(槽位栈).ifPresent(#3)`，捕获 `(ctx, 同一个栈)` → `onUnequip(ctx, 同栈)`（第 2 参 == 第 3 参，同物品同实例）

**⇒ 裁定**：第 3 参 = 被卸下/被移除的那件；第 2 参 = 调用方传入的"将要占槽"的栈。
**本仓注释（`CurioSlotUtil.java:86-121`、`BaseSignItem.java:367-377`、AGENTS.md）与字节码一致**；
**`docs/scan2/P5-equip-paths-linkage.md` §1.2 的主张相反且不成立**：
- 该文 `:109`"第 2 参 = `getStack()`；第 3 参 = X"**与 `ItemizedCurioCapability.onUnequip` 字节码相反**（实为 第2参=X、第3参=getStack()）；
- 该文 `:117` 写 tick 路径第 2 参 = `previousStack`，实际 = 槽内当前栈（`aload 12`），previous 是**第 3 参**；
- 该文 `:174`/`:572` 断言"`ItemStack.EMPTY` **永远不会**作为 `onUnequip` 实参出现"**被证伪**：`loseStacks` 显式传 `ItemStack.EMPTY`（`lambda$loseStacks$4` 第 2 条指令即 `getstatic ItemStack.f_41583_`）；tick 路径真卸下时第 2 参也是空。
  ⇒ P5 §1.4/H2 的"EMPTY 不可达"结论错误；本批实现的 `isIntentionalUnequip`（依赖"第 2 参为空 ⇒ 真实移除"）与字节码**一致**。

判据实现（两版本逐字相同）：`neoforge-1.21.1/.../item/CurioSlotUtil.java:122-130` / `forge-1.20.1/.../CurioSlotUtil.java:123-131`
（`:125` 第 3 参空 ⇒ false；`:127` 第 2 参空 ⇒ true；`:129` 同物品 ⇒ false、异物品 ⇒ true）。三调用点覆盖：① tick 真卸下(第2参空)/换装(第2参=新件)/同物品组件变更(不清理)；② `loseStacks`(第2参空)；③ `CPacketDestroy`(同物品 ⇒ 不清理，与"物品随即销毁"一致)。

清理对象：`BaseSignItem.onUnequip(ctx, newStack, stack)` → `clearSignData(player, stack)`（第 3 参）
neoforge `item/sign/BaseSignItem.java:379-384`、forge `:381-386`；`BaseChipItem.java:79-87`(neoforge)/`:80-88`(forge) 经 `runOnIntentionalUnequip(newStack, stack, ...)`；
`DiceCurioItem.java:81-88`(neoforge)/`:82-89`(forge) `tryRemoveChipBonus(slotContext, stack)`；
各立牌归零确实写在传入栈上：`MisakiSignItem.java:44`、`JasmineSignItem.java:51-52`、`PadmanSignItem.java:50-51`（forge 同）。
`NetherStarDiceItem` 两版本形参名仍为 `curio/prevStack`（误导），但体内只用 `slotContext.entity()`（forge `:62-70` / neoforge `:62-70`）⇒ 无行为后果。

**唯一 FAIL 子项（落地，不是判据）**：tick 路径第 3 参来自 `getPreviousStackInSlot`，而 `DynamicStackHandler.previousStacks` 只在
`setPreviousStackInSlot(i, 当前栈.copy())` 处被写入（`DynamicStackHandler` 构造函数把它初始化为全 `ItemStack.EMPTY`；`CuriosEventHandler.tick` 的 `795-798`/`863-869` 两处均为 `m_41777_ copy()`）
⇒ **GUI 卸下 / 换装时，`MISAKI_SIGN_STACKS`/`JASMINE_*`/`PADMAN_*` 的归零写进的是 Curios 的 copy 快照，玩家物品栏里那件不会被清零**（效果丢失）。
这一点与本仓 `CurioSlotUtil.java:120-121`、AGENTS.md「已知缺口」的自我记录一致（死亡路径由 `PlayerLifecycleHandler` 按 LOWEST 直接清装备中的栈覆盖，见 [6] 相邻证据）。
两版本一致：上述文件 forge/neoforge 逐行同构，无单侧差异。

### [4] 待命状态/计时器分离 — **PASS**

- 冷却门槛只看 `sign_ready_type`：`neoforge-1.21.1/.../item/sign/BaseSignItem.java:130` `if (ModAttachments.getSignReadyType(player) <= 0) {`；forge 同：`forge-1.20.1/.../BaseSignItem.java:131`（`sign_ready_expire > 0` 的旧门槛已无处残留——`isSkillWaiting`（neoforge `:171-175` / forge `:172-176`）仍额外要求"未过期"，但那只用于"等待期按键无效"，不参与冷却写入）。
- 计时器归零/过期即重置状态 + 清计时器 + 移除提示效果：`BaseSignItem.tickSignReadyTimeout`（neoforge `:189-207`、forge `:190-208`）。
- 挂在**玩家级 tick**：`event/PlayerTickEvents.java:118`（neoforge，`PlayerTickEvent.Pre`）/ `forge .../PlayerTickEvents.java:117`（`TickEvent.PlayerTickEvent`，无 phase 判断 ⇒ 每 tick START/END 各一次）。方法体内只读玩家附件与效果，与立牌是否在槽位无关 ✅；三个"等待类"立牌原先写在 `onCurioTick` 里的超时清除已被删除（`6daebf9` diff：Bonnie/Haiqing/Moses 各删去 `getSignReadyType(player)==READY_TYPE && getSignReadyExtire…过期 ⇒ 归零` 段）。
- 幂等：`tickSignReadyTimeout` 先把 `sign_ready_type` 置 0（`:192-193` 早退），第二次调用直接返回；同一玩家级 tick 的另一半 `tickSignActiveLock`（neoforge `:320-343`）在迁移后清空 `sign_active_lock_sign`，第二次 `signId.isEmpty()` 早退 ⇒ **forge 每 tick 触发两次不会双写冷却** ✅（forge 源码注释 `:116`/`:121` 亦声明该幂等性）。

### [5] 星光兑换 — **PASS**

- 只兑换超过基础值的部分：`neoforge-1.21.1/.../resource/ResourceConversion.java:33` `int spendable = Math.max(0, StarLightManager.get(player) - StarLightManager.getBasePoints(player));`，`:34` `use = amount<0 ? spendable : min(amount, spendable)`（`getBasePoints` = 银行卡筹码提供下限，`item/StarLightManager.java:38-50`）。
- 按**实际扣除量**发币：`:38-40` `before = get(player) → spend(coins*2) → gained = max(0, before - get(player)) / 2`（不再使用 `spend` 的返回值；`spend` 的 `set` 下限抬回问题被这一口径消掉）。
- 无实际扣除 ⇒ 整个主动技能作废：`item/sign/ParunanSignItem.java:47-51` `if (gained <= 0) { sendSignActionBar("msg.astral_dice.parunan_active_no_starlight"); return InteractionResultHolder.fail(stack); }`；`BaseSignItem.performSkill` 在 `result.getResult() != SUCCESS` 时于 `:112` 直接 return ⇒ **不写冷却、不进锁定、不发 3 选 1、不发 SignActiveTriggeredEvent** ✅。
- 两版本一致：forge `resource/ResourceConversion.java:29-50` 与 `item/sign/ParunanSignItem.java` 逐行同构（forge Parunan 行号 44-50 同结构）。

### [6] 敌对玩家全局规则 — **PASS**（含 2 处需裁决的单参残留）

- 双参重载：`combat/HostileTargets.java:52-63`（neoforge）/ `:52-63`（forge）—— `敌对生物 ∪ 被激怒中立生物 ∪ 「非同队且曾主动攻击过 viewer 的玩家」`；**同队才豁免**：`:61` `if (viewerPlayer.getTeam() != null && viewerPlayer.isAlliedTo(targetPlayer)) return false;` ⇒ 双方无队伍（getTeam()==null）**可**互为敌对 ✅；viewer 非玩家 ⇒ 玩家不计入 ✅；`viewer==target` ⇒ false ✅。
- 记录（`combat/PlayerHostilityTracker.java`）：neoforge 挂 `LivingDamageEvent.Pre`（`:56`），先 `if (DiceCombatEvents.isInternalAoe() || DiceCombatEvents.isInCounterChain()) return;`（`:60`）——**内部 AOE/溅射/反击被排除**；`:63-66` 非玩家（任一侧）与**自伤**排除；forge 挂可取消的 `LivingDamageEvent`（`:56`）并显式 `if (event.isCanceled()) return;`（`:62`）⇒「已取消」被排除。
  两个 AOE 窗口的实装核对：大当家溅射 `DiceCombatEvents.java:654-702`（`aoeProcessing = true` → try{ `victim.hurt(...)` } finally{ `= false` }，neoforge；forge `:660-707`）；法伤 AOE `SpellDamageRegistry.java:256-263` 与 `:374-381`（两版本同）；反击链 `DiceCombatEvents.java:1170-1180`（`counterDepth++` → try{ `attacker.hurt(...)` } finally{ `counterDepth--` }，且 `computeCounterDamage` 内的绯红自伤也在窗口内）⇒ **`LivingDamageEvent(.Pre)` 触发时窗口仍闭合，自动溅射不会自建敌对**，不构成 FAIL。
  neoforge 无需 `isCanceled` 的佐证：`javap` NeoForge 21.1.235 `LivingDamageEvent$Pre` **不是** `ICancellableEvent`（无 `isCanceled/setCanceled`，只有 `setNewDamage`）⇒ 最前置取消（`LivingIncomingDamageEvent`）的攻击根本不会派发该事件。
- 双向清除：`forget(UUID)`（neoforge `:100-106`）先 `HOSTILE_ATTACKERS.remove(uuid)`（作为目标的记录）再遍历所有集合 `attackers.remove(uuid)`（作为攻击者的记录）✅；触发点 = 死亡（LOWEST + `isCanceled` 守卫，`:72-77`）、死亡克隆（`PlayerEvent.Clone#wasDeath`，`:80-84`）、登出（`:87-90`），forge 同（`:74-92`）。
- 全部 `HostileTargets.isHostile` 调用点：每版本 25 处代码调用（另 2 处为 javadoc 引用），其中 **22 处为两参重载**（与提交信息一致），**3 处仍为单参**：
  1. `combat/DiceCombatEvents.java:1042`（forge `:1044`）`isBlessingTarget` 内 —— **理由成立**：玩家目标已在上方 `:1039-1041` 用队伍口径单独处理，该行只处理"非玩家生物"，玩家根本走不到这里。
  2. `item/sign/PandamanSignItem.java:107`（forge `:108`）—— **理由成立**：嘲讽"永不对玩家施加"是既有约定，表达式里**显式** `&& !(e instanceof Player)`，玩家再被算作敌对也不会进入嘲讽名单（源码注释 `:105-106` 已写明该例外不随本次全局规则改变）。
  3. `damage/RailgunBolts.java:58`（forge 同）`isValidLightningTarget` —— **理由不成立/未文档化**（潜在缺口，非本次提交引入）：该方法在 `:60-61` 就拿得到 `bolt.getCause()`（施放者），具备两参上下文，却仍用单参 ⇒ 敌对玩家**不会**被本模组电磁炮雷击命中；`:38-54` 的注释只解释"白名单上移到 mixin、排除施放者宠物"，未说明"玩家一律不挨雷击"是有意设计。是否应改两参属玩法裁决，我**无法判定**，故仅记为不一致项。

## 静态守门（命令 + 退出码，全部实测）

| # | 命令 | 结果 / exit |
|---|---|---|
| 1 | `pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir neoforge-1.21.1\src\main\resources\assets\astral_dice\lang` | `OK: zh_cn.json(614 keys) 与 en_us.json(614 keys) key 完全一致。` **exit=0** |
| 2 | 同上 `-LangDir forge-1.20.1\src\main\resources\assets\astral_dice\lang` | `OK: 614/614 一致` **exit=0** |
| 3 | `pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1` | `tooltip 染色审计： PASS（无违规）` **exit=0** |
| 4 | `pwsh -NoProfile -File scripts/verify/verify_bountiful_instance_exclusions.ps1` | `汇总: 目标条目 31｜已覆盖 31｜泄漏 0｜错误 0` / `RESULT: ALL CLEAR` **exit=0** |
| 5 | `pwsh -NoProfile -File scripts/verify/verify_bountiful_pools.ps1` | `结果: ALL OK`（objs 13 / rews 92，双版本 md5 一致，价值平衡 PASS） **exit=0** |
| 6 | `pwsh -NoProfile -File scripts/verify/verify_chip_acquisition.ps1` | `RESULT: PASS（致命项 0）`，双版本对等 ✅ **exit=0** |
| 7 | `pwsh -NoProfile -File scripts/verify/verify_chip_recipes.ps1` | `RESULT: ALL OK`（java/gen/jar 三档 × 双版本，59 筹码全一致） **exit=0** |
| 8 | `pwsh -NoProfile -File scripts/verify/verify_content_library.ps1` | `ALL OK —— 内容库与工程实际一致（33 物品 / 6 效果 / 双版本）` **exit=0** |
| 9 | `git grep -n "Math.max(cooldown" -- "*.java"` | 0 命中 **exit=1**（期望无命中） |

## 结论汇总

| 条目 | 结论 |
|---|---|
| 1 枪匠冷却基准（路线 A） | **PASS**（四减免方均按实际基准；① 加急加快 旧 30s/**0s**→新 60s/30s/24s；② 命运指引 旧 30s→新 60s（枪匠单独）；③ 史莱姆不再写 0；④ 电流核心分母取基准） |
| 2 A9 出牌轮 | **PASS** |
| 3 Curios `onUnequip` 三参 | **PASS（判据/传参，字节码独立裁定与仓内注释一致，P5 §1.2 主张与字节码相反）** / **FAIL（GUI 卸下时组件归零落在 Curios `getPreviousStackInSlot` 的 copy 快照上，玩家手上那件不清零；仓内已自认该缺口）** |
| 4 待命状态/计时器分离 | **PASS**（门槛只看 `sign_ready_type`；玩家级 tick；两版本幂等） |
| 5 星光兑换 | **PASS** |
| 6 敌对玩家全局规则 | **PASS**（AOE/反击/自伤/非玩家/已取消均排除；双向清除；同队才豁免）；单参残留 3 处：DiceCombatEvents:1042 与 PandamanSignItem:107 **理由成立**，`RailgunBolts:58` **理由不成立（上下文可得却不传）— 是否应修无法判定** |
| 静态守门 9 项 | 全部 **exit=0**（唯一 exit=1 是"应 0 命中"的 grep） |

