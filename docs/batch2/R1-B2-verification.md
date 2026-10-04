# R1 代码验证报告 — 第二批改动（提交 `216f6cc`「立牌主动技能三态化」）

- 验证者角色：独立验证者（与实现者无关）
- 仓库：`F:\MCProject\astral_dice_multiloader`
- 分支：`multi-1.20.1-1.21.1`，HEAD = `216f6cc`
- 被验证提交：`216f6cc`（`fix(1.2.1): 立牌主动技能三态化(双版本)`，41 files changed, +1180/−116）
- 双版本：`neoforge-1.21.1`（1.21.1/NeoForge/Java21）、`forge-1.20.1`（1.20.1/Forge/Java17）
- 写入白名单：仅本文件。其余全部只读（未修改任何源码/资源/lang/CHANGELOG/AGENTS；未执行 git add/commit/push）
- 状态：进行中（本报告分节增量落盘）

---

## 0. 验证范围与方法

| 手段 | 说明 |
|---|---|
| `git show 216f6cc -- <path>` | 逐文件读取本批 diff（两个子项目同路径各一份） |
| `read` / `grep` | 读取改动后的完整方法体与调用点，确认"登记发生在实际施加成功处"等语义 |
| `tools/check_lang_sync.ps1` | 语言文件键数/键集守门（两版本） |
| `scripts/audit/tooltip_color_audit.ps1` | tooltip 染色守门 |
| `scripts/verify/**` | 逐个运行全部验证脚本 |

---

## 1. 功能点逐条结论

> 表格格式：`# | 预期行为 | 代码证据(文件:行号 + ≤60 字) | 结论 | 备注`
> 结论取值：PASS / FAIL / 无法判定
> 路径前缀：`N` = `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/`，`F` = `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`（除另有说明外，两版本行号相差 ≤2）

### 功能点 1 — 三态状态机

**1a. `isSignActiveLocked` 等价于「硬上界未到 且 门控效果实例仍在」**

代码（`N item/sign/BaseSignItem.java:242-252`，`F:243-253`，两版本逐字相同）：

```java
242: public static boolean isSignActiveLocked(Player player) {
243:     if (player == null) return false;
244:     String signId = getSignActiveLockSignId(player);
245:     if (signId.isEmpty()) return false;
246:     long lockEnd = ModAttachments.getSignActiveLockEnd(player);
247:     if (lockEnd > 0 && player.level().getGameTime() >= lockEnd) return false;   // 硬上界
248:     BaseSignItem sign = lockSignItem(signId);
249:     if (sign == null) return lockEnd > 0;
250:     return sign.isGateEffectActive(player);                                      // 门控效果实例
251: }
```

- 判据 = 锁定标记非空 ∧（`lock_end>0 ⇒ now<lock_end`）∧ 立牌实例的 `isGateEffectActive` → **等价于**「硬上界未到 **且** 门控效果实例仍在」。**PASS**。
- 注（非缺陷，属实现取舍）：`lock_end==0` 且立牌实例解析失败时返回 `false`（避免永久锁死）；`lock_end>0` 解析失败时返回 `true`（沿用已判过的上界）。注释已明示。

**1b. 外部把同一效果刷新更长**是否真的不会延长（硬上界不随刷新重算）

- `lock_end` 只在触发时刻写一次（`beginActiveLock`，`N BaseSignItem.java:282-288`），此后**没有任何代码路径**重算它：全仓 `setSignActiveLockEnd` 的写入点仅 `beginActiveLock`（`N:284`）与 `endLockAndStartCooldown`（清零，`N:300`）两处。刷新效果只影响 `isGateEffectActive`（`hasEffect`），而它**只能让锁定提前结束**（硬上界判定在其之前 return false 早退）。**PASS**。
- 证据：`N:247` 先判上界；`N:250` 才问门控；门控为 `hasEffect`（只真/假，不含时长）。

**1c. `lock_end==0` 的忍者是否只按锁定标记**

- `N:247` 的 `lockEnd > 0` 前置条件使 `lock_end==0` 跳过硬上界判定；`N:249` 在实例解析成功时走 `isGateEffectActive`，而 `KomachiSignItem` **未覆写**该默认实现 → 默认 `return true`（`N BaseSignItem.java:266-268`）⇒ 「只按锁定标记（`sign_active_lock_sign == astral_dice:komachi_sign`）判定」。**PASS**。
- 忍者的两条结束出口：宽限到期未出牌（`N BaseSignItem.java:377-386`）、出牌轮完全重置（`N:400-404`）。

**1d. 锁定期内 `sign_active_cooldown_end` 保持 0（锁定期不进冷却）**

- 进冷却**只有** `endLockAndStartCooldown` 一条锁定相关出口（`N:293-305`）；`performSkill` 在锁定分支 `startActiveLockOnUse==true` 时**不写** `cooldown_end`（`N:133-137`）。锁定期间 `tickSignActiveLock` 也只在 `isSignActiveLocked==false` 时才迁移（`N:391`）。**PASS（功能语义）**。
- ⚠️ 备注（字面差异，非本批引入）：若**上一次**冷却已自然过期，`sign_active_cooldown_end` 里仍是那个**过去时刻**（全仓无任何"到期写 0"的清零点，唯一写 0 的是 `CurrentCoreChipItem:102` 与死亡/清理路径）。因此锁定期内该附件的**字面值不保证为 0**，但恒满足 `now >= cdEnd` ⇒ 冷却分支（`N:98` `cdEnd>0 && now<cdEnd`）不成立 ⇒ **功能上确实"未进冷却"**。
- 另：`max_cooldown` 在进入锁定时**先写基准**（`N:137`），锁定期间各减免方只读它并累加池；`cooldown_end` 直到 `endLockAndStartCooldown` 才写——与需求一致。

### 功能点 2 — 上锁范围 = 7 个

grep 两版本的 `item/sign/*.java` 的覆写点（`startActiveLockOnUse|isGateEffectActive|beginActiveLock`）结果：**恰好 8 个立牌文件有覆写**，其中 `komachi`（`lock_end==0` 专用路径）之外正是要求的 7 个。

| 立牌 | 登记位置（文件:行） | 登记依据 | 结论 |
|---|---|---|---|
| 经商 parunan | `N ParunanSignItem.java:64-69`（`EffectTimerGuard.apply` 的**返回值** `applied` 为真才 `beginActiveLock(..., level.getGameTime()+effect.getDuration())`） | 本次实际施加成功 + 本次随机到的实例时长（**不回读**） | PASS |
| 扫地机 jasmine | `N JasmineSignItem.java:75-80`（`handleUse` 于 `:62` `addEffect(JASMINE_SWEEP,2400)` 后回读**模组自定义效果**实例时长） | 模组效果 `jasmine_sweep` 仅由本主动施加（grep 全仓：仅 `JasmineSignItem:62` 施加、`:76/:85` 读取） | PASS |
| 护法 misaki | `N MisakiSignItem.java:49-54`（`:27` `addEffect(MISAKI_BURST,2400)` 后回读） | 同上，`misaki_burst` 仅本主动施加 | PASS |
| 大侦探 fanny | `N FannySignItem.java:83-88`（各分支 `applyTimed(...)` 返回 `applied? now+ticks : 0`，最后 `if (lockEnd > now) beginActiveLock(...)`） | **仅实际施加成功**的效果取 max；只发物品的分支 3/4 不登记 ⇒ 不锁 | PASS |
| 吸血鬼 papara | `N PaparaSignItem.java:57-62`（`:35` `addEffect(PAPARA_BITE,3600)` 后回读） | 同 jasmine | PASS |
| 骇客 nancy_lu | `N NancyLuSignItem.java:131-136`（硬上界取**自己写入的附件** `getNancyLuHiddenUntil`，`:120` 与本技能隐身同刻写入） | **不回读隐身药水** ⇒ 外部隐身来源不会污染 | PASS |
| 大当家 fen | `N FenSignItem.java:86-91`（`:70` `addEffect(FEN_FRENZY, ...)` 后回读） | 同 jasmine | PASS |

**污染复核（用户点名的两个）**：
- parunan **未回读**同名效果：`apply` 返回 false（已有更长同类）时 `applied=false` ⇒ 不锁、立即起冷却；`isGateEffectActive`（`:86-93`）虽用 `hasEffect(SATURATION|LUCK|HERO_OF_THE_VILLAGE)` 宽判，但**门控只在上界之内提前结束锁定**（`N BaseSignItem.java:247` 先行早退）⇒ 袭击给的村庄英雄**不会延长**锁定。**不判 FAIL**。
- fanny 同样**未回读**：`applyTimed` 用 `EffectTimerGuard.apply` 的返回值（`:91-96`）；门控列表含 `REGENERATION`（可被金苹果污染），但同上只可能提前结束、不可能延长。**不判 FAIL**。
- 附注（可改进，但不违反本批需求）：fanny 分支 7（`HARM`，1 tick）会登记 `lockEnd=now+1`，即"瞬时伤害效果"也进 1 tick 锁定；功能等价，仅 1 tick 的按键无效窗口。

**其余 9 个立牌未被误改**：本批改动文件仅 9 个 sign 文件（`git show --stat`）：`Fanny/Fen/Jasmine/Komachi/Lulu/Misaki/NancyLu/Papara/Parunan`。`MimiSignItem`、`PadmanSignItem`、`RinSignItem`、`HaiqingSignItem`、`BonnieSignItem`、`MosesSignItem`、`PandamanSignItem` **完全未出现在本批 diff 中**（⇒ 不可能被误加上锁）；`LuluSignItem` 只改了被动 −200t 的入池分支，**未加锁**（符合"7 个"的界定）；`KomachiSignItem` 是忍者专用路径（不算这 7 个之内）。**PASS**。

### 功能点 3 — 30 秒待命窗口不算本技能计时器

> **行号口径（补写说明）**：以下各节行号均由当前工作区实读得出（`git status --porcelain` 为空 ⇒ 工作区 = HEAD `216f6cc`）。与上文个别小节引用的行号存在 ±3 偏移（例如 `isSignActiveLocked` 实为 `N:239-252`，非 242-252），**以本补写为准**；未改动上文任何内容。

**3a. 三个立牌的 `handleUse` 均未登记锁定（未调用 `beginActiveLock`，也未覆写 `startActiveLockOnUse`）**

全仓 grep（两子项目 `src/main/java`，模式 `beginActiveLock|startActiveLockOnUse|isGateEffectActive`）命中集：

| 版本 | 调用 `beginActiveLock` 的立牌文件 | 覆写 `startActiveLockOnUse` 的立牌文件 |
|---|---|---|
| N | 定义 `BaseSignItem.java:282`；Fanny:87、Fen:89、Jasmine:78、Komachi:99、Misaki:52、NancyLu:134、Papara:60、Parunan:68（共 8 个） | 同上 8 个（Fanny:109、Fen:86、Komachi:98、Jasmine:75、Misaki:49、NancyLu:131、Papara:57、Parunan:81） |
| F | 定义 `BaseSignItem.java:283`；Fanny:86、Fen:90、Jasmine:79、Komachi:99、Misaki:53、NancyLu:136、Papara:60、Parunan:68（共 8 个） | 同上 8 个（Fanny:107、Fen:87、Komachi:98、Jasmine:76、Misaki:50、NancyLu:133、Papara:57、Parunan:81） |

`HaiqingSignItem` / `BonnieSignItem` / `MosesSignItem` **在两版本中都完全不在命中集内** ⇒ 既无 `beginActiveLock` 调用，也未覆写 `startActiveLockOnUse`（基类默认实现 `return false`：`N BaseSignItem.java:277-279`、`F:278-280`）。

三个 `handleUse` 实际只写"待命"状态：

```java
N HaiqingSignItem.java:89-99            F HaiqingSignItem.java:88-98
 94: ModAttachments.setSignReadyType(player, READY_TYPE);                              // F:93
 95: ModAttachments.setSignReadyExpire(player, level.getGameTime() + SKILL_WAIT_SECONDS*20L); // F:94
 97: player.addEffect(new MobEffectInstance(ModEffects.HAIQING_READY, Integer.MAX_VALUE, ...)); // F:96(.get())
 98: return InteractionResultHolder.success(stack);                                    // F:97
```

- `N BonnieSignItem.java:92-102` / `F:91-101`：同构（`READY_TYPE=2`、`ModEffects.BONNIE_READY`）。
- `N MosesSignItem.java:89-99` / `F:94-104`：同构（`READY_TYPE=3`、`ModEffects.MOSES_READY`）。
- 三者 `handleUse` 全文内**没有任何** `sign_active_lock_*` 写入。**⇒ 未被误登记锁定，不判 FAIL。**

**3b. 30 秒待命窗口不进冷却（不算本技能的计时器）**

`N BaseSignItem.java:130-145` / `F:131-146`：

```java
130: if (ModAttachments.getSignReadyType(player) <= 0) {          // F:131
133:     if (sign.startActiveLockOnUse(player, now)) { ...先写基准,不写 cooldown_end... }  // F:134
138:     else { ModAttachments.setSignActiveCooldownEnd(player, now + signCooldownTicks); } // F:140
```

`handleUse` 在返回 SUCCESS 前已把 `sign_ready_type` 写成 1/2/3（>0）⇒ 整个冷却块被跳过。**按下主动既不进冷却、也不进入锁定。PASS**。

**3c. 既有「未命中不消耗冷却」语义仍成立（冷却只在命中释放时写入）**

释放路径 `N combat/DiceCombatEvents.java:238-301` / `F:243-307`，整块位于外层 `if (... && isBlessingTarget(target, player))` 之内：

| 立牌 | 版本:行 | 代码原文摘要 |
|---|---|---|
| 占星师 | `N:240-253` / `F:245-258` | 先 `setSignReadyType(0)`/`setSignReadyExpire(0)`+施加 `WEAK_MARK`，再 `N:249-250` `setSignActiveCooldownEnd(player, getGameTime()+signCooldownTicks)`、`N:252` `setSignActiveMaxCooldown(...)` |
| 秘密侦探 | `N:257-277` / `F:262-282` | 同构（`N:273-276` 写冷却与基准） |
| 枪匠 | `N:283-295` / `F:289-301` | 更严：`if (MosesSignItem.applyBroken(player, target))` 为真才写 `N:290-293` |

- 三条路径**均未调用 `beginActiveLock`**（`DiceCombatEvents` 不在 3a 的命中集内）⇒ 30 秒窗口内命中释放是**直接起冷却**，不是进入锁定，与本技能语义一致。
- 未命中：外层 `isBlessingTarget` 不成立 ⇒ 整块不执行 ⇒ 不写冷却；窗口超时由玩家级 `tickSignReadyTimeout`（`N BaseSignItem.java:189-207`）归零状态，同样不写冷却。**PASS**。

**3d. 双版本结论**：`N` = PASS；`F` = PASS。结论依据两版本 `handleUse`、`startActiveLockOnUse` 默认实现、释放路径结构逐条一致（仅 `ModEffects.X` ↔ `.get()` 等平台 API 差异）。**未发现三个待命立牌被误登记锁定 ⇒ 不判 FAIL。**

### 功能点 4 — 锁定期提示

**4a. 锁定分支在冷却分支之前**：`N BaseSignItem.java:91-94`（锁定早退）在 `:97-107`（冷却分支）**之前**，两版本一致（`F:92-95` / `F:98-108`）。**PASS**。

```java
91:  if (isSignActiveLocked(player)) { notifyActionBar(player, "msg.astral_dice.sign_active_in_effect", ...); return; }
...
97:  long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
98:  if (cdEnd > 0 && now < cdEnd) { ... CurrentCoreChipItem.tryFinishCooldown(...) ... }
```

**4b. 新键含 `%s` 且四份 lang 均新增**：`assets/astral_dice/lang/{zh_cn,en_us}.json:417`（两子项目各一份，共 4 份）：
- `zh_cn`: `"msg.astral_dice.sign_active_in_effect": "%s:主动技能生效中!"`
- `en_us`: `"msg.astral_dice.sign_active_in_effect": "%s: active skill is still in effect!"`
⇒ **含 `%s`**。**PASS**。

**4c. 键数 614/614**：用 `ConvertFrom-Json` 统计顶层键数，四份文件均为 **614**。**PASS**。

### 功能点 5 — 减免入池

**5① 命运的指引 / 加急加快：锁定态把「基准 ÷ 2」累加进池，且不改 `sign_active_cooldown_end`**

命运的指引 `N item/card/FateGuidanceCardItem.java:65-84` / `F:64-83`：

```java
67: long maxCooldown = ModAttachments.getSignActiveMaxCooldown(player);   // F:66  ← 基准
72: long reduction = maxCooldown / 2;                                     // F:71  ← 基准÷2
73: if (BaseSignItem.isSignActiveLocked(player)) {                        // F:72
75:     ModAttachments.addSignActiveReductionPool(player, reduction);      // F:74  ← 只入池
76:     return;                                                           // F:75  ← 早退
78: long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);         // F:77  ← 锁定态不可达
```

- 锁定分支在读写 `sign_active_cooldown_end` 之前 `return` ⇒ **锁定态绝对不碰 `cooldown_end`**。基准缺失（`maxCooldown<=0`）时按既有兜底重算（`N:68-71`）。**PASS**。

加急加快 `JasmineSignItem.onExpressDeliveryUsed` `N item/sign/JasmineSignItem.java:100-120` / `F:101-121`：

```java
104: long maxCooldown = ModAttachments.getSignActiveMaxCooldown(player);   // F:105 基准(缺失兜底 SIGN_ACTIVE_COOLDOWN_TICKS)
110: if (BaseSignItem.isSignActiveLocked(player)) {                        // F:111
111:     ModAttachments.addSignActiveReductionPool(player, maxCooldown / 2); // F:112 基准÷2 入池
112:     return;                                                            // F:113 早退
114: long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);          // F:115 锁定态不可达
```

**PASS**。

**5② 史莱姆：锁定态累加 200t**

`N LuluSignItem.java:55-61` / `F:55-61`（`onHurt` 受击钩子内）：

```java
56: if (BaseSignItem.isSignActiveLocked(player)) {                      // F:56
57:     ModAttachments.addSignActiveReductionPool(player, 200L);         // F:57  = 10 秒,绝对量
58: } else if (cdEnd > nowTick) { ... setSignActiveCooldownEnd(max(nowTick, cdEnd - 200)); }
```

200L 与"主动冷却 −10 秒"的既有绝对量口径一致。**PASS**。

**5③ 未锁定态三者仍走原有逻辑（正常冷却期的减免不会变成入池）**

- 命运指引 `N:78-83`：`if (cdEnd > now) setSignActiveCooldownEnd(player, now + Math.max(0, remaining - reduction));`
- 加急加快 `N:114-119`：`if (cdEnd > now) setSignActiveCooldownEnd(player, Math.max(now, cdEnd - maxCooldown/2));`
- 史莱姆 `N:58-61`：`else if (cdEnd > nowTick) setSignActiveCooldownEnd(player, Math.max(nowTick, cdEnd - 200));`

⇒ 未锁定一律走原有 `cooldown_end` 抵扣。且 `sign_active_reduction_pool` 的**全部**写入点只有：`beginActiveLock` 归零（`N BaseSignItem.java:287`）、`endLockAndStartCooldown` 归零（`N:303`）、以及上述三个减免方（`N FateGuidanceCardItem:75` / `JasmineSignItem:111` / `LuluSignItem:57`）——**不存在"未锁定态入池"路径**。**PASS**。

**5④ 进入冷却时一次性抵扣：`effective = max(0, 基准 − 池)`、改写 `sign_active_max_cooldown`、池归零**

`N BaseSignItem.java:296-308` / `F:297-309`（唯一出口 `endLockAndStartCooldown`）：

```java
298: long base = ModAttachments.getSignActiveMaxCooldown(player);        // F:299
299: long pool = ModAttachments.getSignActiveReductionPool(player);      // F:300
300: long effective = Math.max(0L, base - pool);                        // F:301
301: ModAttachments.setSignActiveCooldownEnd(player, now + effective);   // F:302 一次性抵扣后起冷却
302: ModAttachments.setSignActiveMaxCooldown(player, effective);         // F:303 改写基准(电流核心档位分母)
303: ModAttachments.setSignActiveReductionPool(player, 0L);              // F:304 池归零
304-307: setSignActiveLockSign("") / LockEnd(0) / GraceEnd(0) / LockPlayed(false)   // F:305-308
```

⇒ 与需求逐字一致（含"改写基准"与"池归零"两点）。**PASS**。

**5⑤ 池的读写入口两版本一致**

| 版本 | 读 | 写（夹底） | 加法入口 |
|---|---|---|---|
| N `component/ModAttachments.java` | `:484-486` `player.getData(SIGN_ACTIVE_REDUCTION_POOL.get())` | `:488-490` `setData(..., Math.max(0L, value))` | `:492-494` `set(pool + delta)` |
| F `component/ModAttachments.java` | `:441-443` `SIGN_ACTIVE_REDUCTION_POOL.get(player)` | `:445-447` `set(player, Math.max(0L, value))` | `:449-451` `set(pool + delta)` |

`addSignActiveReductionPool` **两版本均存在**，语义一致（在 setter 的 `≥0` 夹底之上做累加）。**PASS**。

**5 结论**：`N` = PASS；`F` = PASS；**无 FAIL、无需「无法判定」**。

### 功能点 6 — 电流核心

`N item/chip/CurrentCoreChipItem.java:83-106`：

```java
83: public static int tryFinishCooldown(Player player, long cooldownEnd, long now) {
84:     if (player == null || player.level().isClientSide()) return FINISH_NONE;
88:     if (com.merlinkitsune.astral_dice.item.sign.BaseSignItem.isSignActiveLocked(player)) return FINISH_NONE;  // ← 早退
89:     if (!isEquipped(player)) return FINISH_NONE;
90:     long remaining = cooldownEnd - now;
91:     if (remaining <= 0) return FINISH_NONE;
93:     int cost = instantCooldownCost(remaining, ModAttachments.getSignActiveMaxCooldown(player));
94:     if (ChargeManager.getStacks(player) < cost) { ... return FINISH_NOT_ENOUGH; }
98:     for (int i = 0; i < cost; i++) ChargeManager.consumeOne(player);   // ← 扣充能
102:    ModAttachments.setSignActiveCooldownEnd(player, now);
103:    ModAttachments.setSignActiveMaxCooldown(player, 0);
```

- 锁定态在 `:88` 返回 `FINISH_NONE`，位于 `isEquipped`/档位计算/`ChargeManager.getStacks`/`consumeOne`/任何附件写入**之前** ⇒ **无任何扣充能或状态写入副作用**。**PASS**。
- 正常路径下根本到不了这里：`performSkill` 的锁定分支（`N BaseSignItem.java:91-94`）在冷却分支（`:98-104`）之前 return ⇒ 锁定期按键**只发一条**提示 `msg.astral_dice.sign_active_in_effect`（`:92`），与锁定分支同一条。**PASS**。
- 真实冷却态仍按占比耗充能立即完成：`:93 instantCooldownCost(...)`、`:98-100` 循环 `consumeOne`、`:102-104` 置 `cooldown_end=now` / `max_cooldown=0` 并提示 `hud...current_core_finish`。**PASS**。

### 功能点 7 — 忍者

（核实对象：`EffectCardPeriod.onRoundFullyReset` 的实际挂点）

**7a. 定义与被调用方**

- `N item/card/EffectCardPeriod.java:199-201` / `F:200-202`：`onRoundFullyReset(Player)` 唯一实现 = 转发 `BaseSignItem.onEffectCardRoundReset(player)`。
- 被调用方 `N item/sign/BaseSignItem.java:350-354` / `F:352-356`：`if (!KOMACHI_LOCK_ID.equals(getSignActiveLockSignId(player))) return; endLockAndStartCooldown(player);`（非忍者直接返回，不做事）。

**7b. 全仓调用点恰好 2 处（grep `onRoundFullyReset|onEffectCardRoundReset`，两子项目共 20 处命中，其中付费调用只有下列 2 处/版本）**

| # | 版本:行 | 所在分支（代码原文摘要） |
|---|---|---|
| ① 周期边界块 | `N EffectCardPeriod.java:319`（`F:320`） | `registerPlay` 内 `N:312-321`（`F:313-322`）：`if ((cooldown > 0 && now >= cooldown) \|\| (cooldown <= 0 && played > 0 && played >= getMaxAllowed(player))) { setCooldownEnd(0); setPlayCount(0); clearRoundBonuses(player); onRoundFullyReset(player); cooldown = 0; }` |
| ② 冷却到期归零分支 | `N EffectCardPeriod.java:395`（`F:396`） | `tick` 内 `N:389-397`（`F:390-398`）：`setEffectCardCooldownEnd(0); setEffectCardPlayCount(0); clearRoundBonuses(player); onRoundFullyReset(player); disarmAoe(player);` |

- **未挂在 `clearRoundBonuses` 内部**：`N:184-189` / `F:185-190` 仅 4 条归零写入（`EffectCardBonusPlays` / `CandyChipPlayBonusActive` / `SatellitePlayBonusActive` / `LivingPageCycleBonus`），无任何回调。**不判 FAIL**。
- **不属于"挂在 tick 末尾 / 多个 return 之后"的误触发形态**：`tick` 的另两种情形都在 `:395` **之前** `return`——情形 3「不变量违例」与情形 2 共用 `cooldown <= 0` 分支（`N:374-388`：`played<=0` 直接 return；未打满且 `getRemainingBlockTicks>0` 直接 return；否则补一轮冷却后 `return`）。只有 `cooldown > 0 && now >= cooldown`（冷却到期）能落到 `:389-395` ⇒ 该行虽位于方法体尾部，**结构上仅情形 1 可达**，不会误触发。**PASS**。

**7c. `registerPlay` 开头置 `sign_active_lock_played`（且全仓唯一置真点）**

`N EffectCardPeriod.java:300-303` / `F:301-304`（`registerPlay` 的**第一条**语句，在周期边界块 `:312` / `F:313` **之前**）：

```java
300: if (!player.level().isClientSide() && BaseSignItem.isSignActiveLocked(player)) {   // F:301
302:     ModAttachments.setSignActiveLockPlayed(player, true);                          // F:303
```

- 先置真后判边界 ⇒ 即使本次出牌本身触发周期边界（回调 → 忍者起冷却），"出过牌"也已记录。
- **全仓唯一置真点**：grep `setSignActiveLockPlayed` 的全部写入 = `N EffectCardPeriod:302`（true，唯一）、`N BaseSignItem:286`/`:307`（false，`beginActiveLock`/`endLockAndStartCooldown`）；F 对应 `:303` / `:287` / `:308`。
- `registerPlay` 全仓唯一调用点 = `N/F item/card/BaseEffectCardItem.java:220`（`tryUseCard`，`use` 与 `interactLivingEntity` 共用的服务端出牌流程，`N:200-220` 注释明示）。**PASS**。

**7d. `forceResetRound` 复用既有唯一清理入口 `clearRoundBonuses`，未另列清理项**

`N EffectCardPeriod.java:211-215` / `F:212-216`：

```java
212: ModAttachments.setEffectCardCooldownEnd(player, 0);
213: ModAttachments.setEffectCardPlayCount(player, 0);
214: clearRoundBonuses(player);            // 既有唯一清理入口,无第二份"每轮一次"标记清单
```

且其 javadoc（`N:203-210` / `F:204-211`）明确"**不**回调 `onRoundFullyReset`"。**PASS**。

- 备注（观察项，**非 FAIL**，不违反本点判据）：`tick` 情形 1 在 `clearRoundBonuses` 之外还多调 `ElectricGloveChipItem.disarmAoe(player)`（`N:397` / `F:398`），而 `forceResetRound` 没有 ⇒ "宽限强重置"不解除电击手套本周期已武装的法伤扩散，"周期正常归零"会。判据只要求复用 `clearRoundBonuses`，故不判 FAIL，但两处清理口径不完全等价，建议后续对齐。

**7e. 双版本结论**：`N` = PASS；`F` = PASS（`F` 仅整体行号 +1，代码逐行同构）。

### 功能点 8 — 忍者 1:00 宽限

**8① 宽限到期判定读 `sign_active_lock_grace_end`**

`N BaseSignItem.java:329-332` / `F:331-334`：

```java
329: long graceEnd = ModAttachments.getSignActiveLockGraceEnd(player);   // F:331
330: if (graceEnd <= 0 || now < graceEnd) return;                        // F:332
```

宽限刻只在忍者上锁时写入：`N/F item/sign/KomachiSignItem.java:98-101` = `beginActiveLock(player, KOMACHI_LOCK_ID, 0L); ModAttachments.setSignActiveLockGraceEnd(player, now + LOCK_GRACE_TICKS);`，`LOCK_GRACE_TICKS = 1200`（`N/F:41`，20t/s ⇒ **1:00**）。**PASS**。

**8② 期内自始至终未出效果牌 ⇒ `forceResetRound` + 进入冷却**

`N BaseSignItem.java:336-339` / `F:338-341`：

```java
337: com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.forceResetRound(player);  // F:339 强制重置出牌状态
338: endLockAndStartCooldown(player);                                                   // F:340 进入冷却
```

`forceResetRound`（`N EffectCardPeriod:211-215`）归零出牌数 / 出牌冷却 / 每轮标记；随后由唯一出口 `endLockAndStartCooldown` 抵扣减免池并起冷却。**PASS**。

**8③ 一旦出过牌 ⇒ 保险失效、改为遵循出牌周期**

`N BaseSignItem.java:331-335` / `F:333-337`：

```java
331: if (ModAttachments.getSignActiveLockPlayed(player)) {                 // F:333
333:     ModAttachments.setSignActiveLockGraceEnd(player, 0L);             // F:335 清宽限刻
334:     return;                                                          // F:336 保持锁定
```

此后 lock 只能由周期完全重置出口结束（`onEffectCardRoundReset` → `endLockAndStartCooldown`，`N:350-354`）。**PASS**。

**8④「未出牌」判据不是 `play_count == 0`**

判据是 `sign_active_lock_played`（`N:331` / `F:333`）。周期性归零确实会清掉计数——`registerPlay` 边界块 `N:314-315`、`tick` 情形 1 `N:389-390`、`forceResetRound` `N:212-213` 都执行 `setEffectCardPlayCount(player, 0)`；而该标记在 `registerPlay` **首行**（`N:300-303`，位于边界块之前）置真，且 `clearRoundBonuses`（`N:184-189`）**不含**它 ⇒ **跨周期边界保持**，不会被误判成"未出牌"。**PASS**。

**8⑤ forge 侧每 tick 触发两次时的幂等性论证**

触发路径：`F event/PlayerTickEvents.java:108-123` 的 `onPlayerTickPre(TickEvent.PlayerTickEvent event)` —— **未做相位过滤**（方法体无 `event.phase == START` 判定，`F:110-122`），Forge 的 `TickEvent.PlayerTickEvent` 每 tick 派发 **START + END 两次**，故 `BaseSignItem.tickSignActiveLock(player)` 每 tick 执行 **2 次**（两次共享同一 `level().getGameTime()`）。对照：`N event/PlayerTickEvents.java:110-111` 只订阅 `PlayerTickEvent.Pre`（每 tick 1 次）。

逐次展开（同 tick `now = t`），**三种状态各写一遍两次调用的路径**：

1. **宽限到期且期内未出牌**（`played == false`，`0 < graceEnd <= t`）
   - 第 1 次：首行 `signId = getSignActiveLockSignId(player)` = `"astral_dice:komachi_sign"`（非空，`N:323-324`）→ 忍者分支（`N:326`）→ `graceEnd` 已到期（`N:330` 不早退）→ `played` 为假（`N:331`）⇒ `forceResetRound`（清出牌数/出牌冷却/每轮标记）→ `endLockAndStartCooldown`：写 `cooldown_end = t + effective`（`effective = max(0, base − pool)`）、`max_cooldown = effective`、`pool = 0`、`lock_sign = ""`、`lock_end = 0`、`grace_end = 0`、`played = false`。
   - 第 2 次：首行 `signId` 已是 `""` ⇒ `if (signId.isEmpty()) return;`（`N:324`）**立即早退**。
   - ⇒ **不重复强重置、不重复起冷却、不重复扣减**。
2. **宽限期内出过牌**（`played == true`，`0 < graceEnd <= t`）
   - 第 1 次：`setSignActiveLockGraceEnd(player, 0L)` → `return`（**不**强重置、**不**起冷却，保持锁定等周期边界）。
   - 第 2 次：`graceEnd <= 0` ⇒ `N:330` 首个 `if` 即 `return`。
3. **其余立牌（门控结束）**：第 1 次 `endLockAndStartCooldown` 清空 `lock_sign`；第 2 次首行早退（`N:341-342` 不可达）。

**通用论证**：`tickSignActiveLock` 中每个会产生副作用的分支，在执行时都**必然销毁其自身前提**（清 `lock_sign` 或清 `grace_end`），而方法首行即以该前提做守卫 ⇒ 第二次调用不可能再进入同一分支（严格单调推进的守卫）。

**数值层面的二次保险**：即便假设守卫失效重复执行 `endLockAndStartCooldown`，第 2 次读到的 `base` 已被第 1 次改写成 `effective`、`pool` 已为 `0` ⇒ 仍写出同一个 `cooldown_end = now + effective`，**不会**二次扣减或累加。

⇒ **能够论证幂等 ⇒ PASS（不判「无法判定」）**。同一论证对 `tickSignReadyTimeout` 同构成立。

**8f. 双版本结论**：`N` = PASS；`F` = PASS（`F` 的每 tick 两次由 ⑤ 覆盖）。

### 功能点 9 — 两版本对等

**9a. 机械比对方法与结果**

对本批 `git show --stat 216f6cc` 列出的 15 个 Java 文件，取 `git diff 216f6cc^ 216f6cc` 的**新增行**，剥注释、去缩进、归一化平台 API 拼写后逐文件做对称差集比对（归一化项：`com.merlinkitsune.astral_dice.` 前缀；`ModEffects.X.get()`→`ModEffects.X`；`CuriosCompat.`→`CuriosApi.`；`PacketDistributor/ModNetwork.sendToPlayer`→同一记号；`new ActionBarPayload/new ModNetwork.ActionBarMessage`→同一记号；`NeoForge/MinecraftForge.EVENT_BUS`→同一记号；`AttachmentType.builder(...).serialize(...)` ↔ `AttachedDataKey.builder(...)` → 同一记号；`player.getData(K.get())` ↔ `K.get(player)` → 同一记号）。

结果：**11/15 文件归一化后新增行逐行全等** —— `Fen` / `Jasmine` / `Komachi` / `Lulu` / `Misaki` / `NancyLu` / `Papara`、`EffectCardPeriod`、`FateGuidanceCardItem`、`CurrentCoreChipItem`、`PlayerTickEvents`。余下 4 个文件的残差**全部落在下表 D1–D9 允许差异**，逐条人工分类后**无功能性残差**。

**9b. 允许的实现差异（显式声明）**

| # | 差异项 | N（neoforge-1.21.1） | F（forge-1.20.1） | 影响语义? |
|---|---|---|---|---|
| D1 | 附件定义/注册 API | `ATTACHMENTS.register("id", () -> AttachmentType.builder(() -> v).serialize(Codec.X).build())`；类型 `DeferredHolder<AttachmentType<?>, AttachmentType<T>>`（`ModAttachments.java:438-466`） | `register(AttachedDataKey.builder("id", Codec.X, () -> v).build())`；类型 `AttachedDataKey<T>`（`:405-423`） | 否（5 新键**同 id / 同默认值 / 同 Codec**） |
| D2 | 效果引用形态 | `ModEffects.X`（`Holder<MobEffect>`；如 `List<Holder<MobEffect>> LOCK_GATE_EFFECTS`，Fanny/Parunan） | `ModEffects.X.get()`（`MobEffect`） | 否 |
| D3 | 玩家 tick 事件与调用次数 | `PlayerTickEvent.Pre`（`PlayerTickEvents.java:110-111`）⇒ `tickSignActiveLock` **每 tick 1 次** | `TickEvent.PlayerTickEvent`（`:108-109`，START+END）⇒ **每 tick 2 次**；幂等性已由功能点 8⑤ 论证 | 否 |
| D4 | Curios 取用 | `CuriosApi.getCuriosInventory` | `CuriosCompat.getCuriosInventory`（LazyOptional 包装） | 否 |
| D5 | 网络反馈 | `PacketDistributor.sendToPlayer` + `ActionBarPayload` | `ModNetwork.sendToPlayer` + `ModNetwork.ActionBarMessage` | 否 |
| D6 | 事件总线 | `NeoForge.EVENT_BUS.post` | `MinecraftForge.EVENT_BUS.post` | 否 |
| D7 | 附件读写形态 | `player.getData(K.get())` / `player.setData(K.get(), v)` | `K.get(player)` / `K.set(player, v)` | 否 |
| D8 | 已有键的 sync 形式 | `.sync(ByteBufCodecs.VAR_LONG)`（如 `sign_active_cooldown_end`） | `.sync()` | 否（**本批 5 个新键两版本均不 sync**） |
| D9 | 局部变量 FQN / 换行宽度 | 部分用简单名（`Item item = ...`） | 部分用全限定名（`net.minecraft.world.item.Item item = ...`）；`Holder` 差异导致断行位置不同 | 否 |

**9c. 功能差异（应为空）——核对结果：空**

| 本批语义点 | N 证据 | F 证据 | 差异 |
|---|---|---|---|
| 三态判定 `isSignActiveLocked` | `BaseSignItem.java:239-252` | `:240-253` | 无（逐行同构） |
| 上锁范围 7 个立牌 + 忍者 | 3a 的 grep 命中集 | 同一命中集 | 无 |
| 锁定期不写 `cooldown_end`、基准先写 | `BaseSignItem.java:130-145` | `:131-146` | 无 |
| 减免入池 3 处 + 抵扣 1 处 | 功能点 5 各行 | 同上（行号 +1） | 无 |
| 忍者周期钩子两处挂点 | `EffectCardPeriod.java:319` / `:395` | `:320` / `:396` | 无 |
| 忍者宽限保险 | `BaseSignItem.java:323-342` | `:325-344` | 无 |
| 电流核心锁定早退 | `CurrentCoreChipItem.java:88` | `:88` | 无 |
| lang（4 份新增 1 键） | `zh "%s:主动技能生效中!"` / `en "%s: active skill is still in effect!"` | 两语言文案**逐字相同** | 无 |

**9d. 5 个新键的登记与同步状态**

| 键 | N 注册 | F 注册 | N `.sync()` | F `.sync()` | 在 F `SYNCED_KEYS` |
|---|---|---|---|---|---|
| `sign_active_lock_sign` | `:438-441` | `:405-406` | **无**（仅 `.serialize(Codec.STRING)`） | **无** | **否** |
| `sign_active_lock_end` | `:445-448` | `:410-411` | **无** | **无** | **否** |
| `sign_active_reduction_pool` | `:451-454` | `:414-415` | **无** | **无** | **否** |
| `sign_active_lock_grace_end` | `:457-460` | `:418-419` | **无** | **无** | **否** |
| `sign_active_lock_played` | `:463-466` | `:422-423` | **无** | **无** | **否** |

- F 的 `syncedKeys()`（`component/ModAttachments.java:906-938`）快照共 **28** 条（`:908-935`），逐条核对**不含**上述 5 键（也不含 `sign_active_max_cooldown`）。N 侧不 `.sync()` ⇒ 不进同步集。
- 佐证：5 键注册语句内均无 `.sync`（`Select-String 'sign_active_lock|sign_active_reduction_pool'` 原样核对 2×11 行）；`isSignActiveLocked` 的全部调用点（`performSkill`、`CurrentCoreChipItem:88`、`FateGuidanceCardItem`、`JasmineSignItem`、`LuluSignItem`、`EffectCardPeriod.registerPlay`）均为服务端路径，**无客户端/tooltip 消费者** ⇒ 不 sync 不产生客户端行为缺口。**PASS**。

**9 结论**：`N` 与 `F` 本批语义对等 —— **允许差异 9 类（D1–D9）、功能差异 0**。**PASS**。

### 功能点 10 — 附件键数 67 / 69

**计数口径（明确说明数的是什么）**：
- 1.21.1：`Select-String -Path neoforge-1.21.1/.../component/ModAttachments.java -Pattern 'ATTACHMENTS\.register\('` → **67**（每个附件注册恰好一次 `ATTACHMENTS.register("<id>", ...)`；`ATTACHMENTS.register(` 亦等于实际附件常量数）。
- 1.20.1：`Select-String ... -Pattern 'register\(AttachedDataKey\.builder\('` → **69**（1.20.1 无 DeferredRegister，每个附件 = 一条 `register(AttachedDataKey.builder("<id>", Codec, () -> default).build())`；`AttachedDataKey.builder(` 出现次数同为 69，两法一致）。

⇒ **1.21.1 = 67、1.20.1 = 69，与预期一致**，差值 2 为 `damage_effect_bonus`、`curse_original_amount`（仅 1.20.1，本批之前已存在）。**PASS**。

**5 个新键**（两版本同名）：`sign_active_lock_sign` / `sign_active_lock_end` / `sign_active_reduction_pool` / `sign_active_lock_grace_end` / `sign_active_lock_played`
- 1.21.1 注册：`N component/ModAttachments.java:438-465`；1.20.1：`F component/ModAttachments.java:405-423`。
- **均未 `.sync()`**（两条注册语句内无 `.sync()`，已逐行核对）。
- **1.20.1 均未加入 `SYNCED_KEYS`**：清单在 `F component/ModAttachments.java:907-935`（28 条），5 个新键均不在其中。

---

## 附录 A：静态守门命令 | 退出码 | 关键输出行

执行环境：仓库根 `F:\MCProject\astral_dice_multiloader`，`pwsh -NoProfile`。`scripts/verify/` 下**共 5 个脚本，全部存在并逐个执行**（无缺失项）。全部命令均在**只读**前提下运行，未改动任何文件。

| # | 命令 | 退出码 | 关键输出行 |
|---|---|---|---|
| A1 | `pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir neoforge-1.21.1/src/main/resources/assets/astral_dice/lang` | **0** | `OK: zh_cn.json(614 keys) 与 en_us.json(614 keys) key 完全一致。` |
| A2 | `pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir forge-1.20.1/src/main/resources/assets/astral_dice/lang` | **0** | `OK: zh_cn.json(614 keys) 与 en_us.json(614 keys) key 完全一致。` |
| A3 | `pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1` | **0** | `tooltip 染色审计： PASS（无违规）` |
| A4 | `pwsh -NoProfile -File scripts/verify/verify_chip_recipes.ps1` | **0** | N/F 各一行 `[java] 筹码 59 \| 一致 59 \| 不一致 0 \| 缺配方 0`；`[gen] 配方文件 108 \| 一致 59 \| 缺文件 0`；`[jar] 配方文件 122 \| 一致 59 \| 缺文件 0`；末行 `RESULT: ALL OK` |
| A5 | `pwsh -NoProfile -File scripts/verify/verify_chip_acquisition.ps1` | **0** | `✅ 无致命问题`；`chips / no_rec / unreach / no_page` 四项均 `一致=True 差异=[]`；`RESULT: PASS（致命项 0）` |
| A6 | `pwsh -NoProfile -File scripts/verify/verify_content_library.ps1` | **0** | `[OK] 双版本对等：汇总标签 / curios 槽 / 手册条目`；`ALL OK —— 内容库与工程实际一致（33 物品 / 6 效果 / 双版本）` |
| A7 | `pwsh -NoProfile -File scripts/verify/verify_bountiful_pools.ps1` | **0** | `OK 双版本一致 astral_objs.json md5 1a0656e6ca2f784402f17cb6c62f3c28`、`astral_rews.json md5 c656ca8d7f5820a88b97f8473bbe96fc`；`OK 价值平衡: objs 顶值 16000 ≥ rews 顶值2和 17000 × 0.9 = 15300 → PASS`；`结果: ALL OK` |
| A8 | `pwsh -NoProfile -File scripts/verify/verify_bountiful_instance_exclusions.ps1` | **0** | `汇总: 目标条目 31 \| 已覆盖 31 \| 泄漏 0 \| 错误 0`；`RESULT: ALL CLEAR`（注：默认 `-Target supplementaries`；共扫描 6 个实例 —— 5 个"无 bountiful jar，跳过"（FTB Skies 2 / FTB Skies 2 Aero / 狐の新冒险 + 本仓 `run/1.20.1`、`run/1.21.1`），命中 jar 的 1 个实例 `狐の航空学 Voxy Edition` → `目标命名空间条目总数 31 / 已覆盖 31 / 仍会出现在赏金板 0` → CLEAR） |

**A 结论：8/8 全部退出码 0，无违规、无告警级失败 ⇒ 全绿。**

## 附录 B：双版本对等声明

**声明**：对提交 `216f6cc`（立牌主动技能三态化）本批涉及的 **15 个 Java 文件 + 4 份 lang** 逐条比对后，`neoforge-1.21.1`(N) 与 `forge-1.20.1`(F) 在语义上对等。下表左列为**允许的实现差异**（平台机制所致，非语义差异），右列为**功能差异**（应为空）。

### B-1 允许差异（Allowlist，9 项）

| # | 允许差异 | N | F |
|---|---|---|---|
| D1 | 附件 API | `AttachmentType` + `DeferredRegister`（`DeferredHolder`） | `AttachedDataKey` + `AstralData` Capability |
| D2 | 效果引用 | `ModEffects.X` = `Holder<MobEffect>` | `ModEffects.X.get()` = `MobEffect` |
| D3 | 玩家 tick | `PlayerTickEvent.Pre`，每 tick 调用 1 次 | `TickEvent.PlayerTickEvent`（START/END），每 tick 2 次（幂等已论证，见功能点 8⑤） |
| D4 | Curios 取用 | `CuriosApi.getCuriosInventory`（`Optional`） | `CuriosCompat.getCuriosInventory`（LazyOptional → `Optional`） |
| D5 | ActionBar 发包 | `PacketDistributor.sendToPlayer` + `ActionBarPayload` | `ModNetwork.sendToPlayer` + `ModNetwork.ActionBarMessage` |
| D6 | 事件总线 | `NeoForge.EVENT_BUS` | `MinecraftForge.EVENT_BUS` |
| D7 | 附件读写 | `player.getData(K.get())` / `setData` | `K.get(player)` / `K.set(player, v)` |
| D8 | 已有键 `.sync` 形式 | `.sync(ByteBufCodecs.VAR_LONG)` | `.sync()`（无参） |
| D9 | FQN / 断行 | 简单名优先 | 全限定名优先（`net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)` 等），断行位置不同 |

> **本批 5 个新键（`sign_active_lock_sign` / `lock_end` / `reduction_pool` / `lock_grace_end` / `lock_played`）不属于 D8：两版本均不 `.sync()`（N 无 `.sync(`、F 无 `.sync()`），且 1.20.1 未登记进 `SYNCED_KEYS`（28 条清单内不含）。两版本同 id、同默认值、同 Codec。**

### B-2 功能差异（应为空）

| # | 功能点 | N | F | 功能差异 |
|---|---|---|---|---|
| 1 | 三态判定 `isSignActiveLocked`（硬上界 + 门控实例） | 已实现 | 已实现 | **无** |
| 2 | 上锁范围（7 个带时长效果立牌 + 忍者专用路径） | 7+1 | 7+1 | **无** |
| 3 | 30 秒待命窗口不算本技能计时器（占星师/秘密侦探/枪匠不上锁） | PASS | PASS | **无** |
| 4 | 锁定期提示与电流核心早退 | PASS | PASS | **无** |
| 5 | 减免累计入池 / 一次性抵扣 | PASS | PASS | **无** |
| 6 | 忍者周期钩子两处挂点 | PASS | PASS | **无** |
| 7 | 忍者 1:00 宽限保险 | PASS | PASS | **无** |
| 8 | 5 个新键的登记与不同步 | 一致 | 一致 | **无** |
| 9 | lang（4 份各 +1 键，中英文案逐字相同；键数 614/614） | 一致 | 一致 | **无** |

**B 结论：功能差异 0 项；允许差异 9 项已显式声明。**
