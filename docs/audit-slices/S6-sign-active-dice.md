# S6 立牌主动/冷却/待命 + 骰子属性与槽位

> 审计基线:`multi-1.20.1-1.21.1` @ `d48a529`,工作区干净;只读审计,未修改任何源码/配置,仅新增本文件。
> 路径前缀:`N/` = `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/`;`F/` = `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`(资源文件与依赖 jar 另注全路径)。
> 结论一律以**代码/字节码**为准,不采信注释;注释与代码不一致处单独标注。
> Curios 侧行为(本仓外)经反编译依赖 jar 核对:`N` 侧 `maven.modrinth:curios:yohfFbgD`(=9.5.1+1.21.1,见 `neoforge-1.21.1/build.gradle:155`)、`F` 侧 `maven.modrinth:curios:IPQlZkz1`(=5.14.1+1.20.1,见 `forge-1.20.1/build.gradle:195`),方法用 `javap -p -l`(LocalVariableTable 取参数名)、`javap -c -p`(取派发实参),证据见 §1.9。

---

## 1. 参与方清单

| 对象 | 入口 | 共享状态键 | 1.21.1 file:line | 1.20.1 file:line |
|---|---|---|---|---|
| 立牌主动总闸 `BaseSignItem.performSkillForCurio` / `performSkill` | 快捷键 J → 网络包 | `sign_active_cooldown_end`、`sign_ready_type`、`sign_ready_expire` | `N/item/sign/BaseSignItem.java:61-119` | `F/item/sign/BaseSignItem.java:62-120` |
| 客户端按键 | `ClientTickEvent.Post` 内 `while (consumeClick())` 发一包/次 | — | `N/client/KeyBindingSetup.java:33-44`(发送 `:38-40`) | 同构(见 §4 表) |
| 网络载荷 | `SignActivatePayload.handle` → `enqueueWork(...)` | — | `N/network/SignActivatePayload.java:27-33` | `F/network/...`(同构) |
| 自定义事件 | `Event` 子类,无 priority 语义 | — | `N/event/SignActiveTriggeredEvent.java:15-41` | `F/event/SignActiveTriggeredEvent.java:15-41` |
| 冷却附件/数据键 | `AttachmentType<Long>` / `AttachedDataKey<Long>`,serialize + sync | `sign_active_cooldown_end` | `N/component/ModAttachments.java:400-412` | `F/component/ModAttachments.java:372-380`(+`SYNCED_KEYS` `:832`) |
| 待命等待器 | 三立牌共用单槽 | `sign_ready_type` / `sign_ready_expire` | `N/component/ModAttachments.java:443-471` | `F/component/ModAttachments.java:408-428`(+`SYNCED_KEYS` `:833-834`) |
| 待命立牌(占星师 type=1) | `handleUse` 武装 / `onCurioTick` 超时清理 / 释放 | 上两键 + `weak_mark_source` + 效果 `haiqing_ready` | `N/item/sign/HaiqingSignItem.java:41,47-104`(武装 `:99-102`) | `F/item/sign/HaiqingSignItem.java:40,47-102`(武装 `:98-99`) |
| 待命立牌(秘密侦探 type=2) | 同上 | 上两键 + `undercover_source` + 效果 `bonnie_ready` | `N/item/sign/BonnieSignItem.java:41,47-107`(武装 `:102-105`) | `F/item/sign/BonnieSignItem.java:40,47-104`(武装 `:101-102`) |
| 待命立牌(枪匠 type=3) | 同上 + 自定基准 120s | 上两键 + 效果 `moses_ready` + `moses_weakness_armor` | `N/item/sign/MosesSignItem.java:36,44-104,117-125`(武装 `:99-102`) | `F/item/sign/MosesSignItem.java:39,44-104`(武装 `:104-105`;冷却 `:122-130`) |
| 待命释放点 | 近战命中且 `isBlessingTarget` | 清 type/expire + 起冷却 + 充能 +1 | `N/combat/DiceCombatEvents.java:228-282` | `F/combat/DiceCombatEvents.java:224-279` |
| 冷却基准常量 | — | `SIGN_ACTIVE_COOLDOWN_TICKS`(=3600) | `N/component/GameplayConstants.java:43-47,104` | `F/component/GameplayConstants.java:43-47,104` |
| 诡异骰子减免 | 唯一「起冷却」取值函数 | — | `N/event/WeirdDiceHandler.java:36-42` | `F/event/WeirdDiceHandler.java:36-42` |
| 充能减冷却 | 拥有 ≥1 层充能 -20% | — | `N/item/ChargeManager.java:64-68`;常量 `N/component/GameplayConstants.java:32-34` | `F/item/ChargeManager.java:64-68` |
| 命运的指引减半 | 效果牌使用 | 改写 `sign_active_cooldown_end` | `N/item/card/FateGuidanceCardItem.java:49-71` | `F/item/card/FateGuidanceCardItem.java:51-71` |
| 忍者被动 -30% | 每第 3 张效果牌 | 同上 | `N/item/sign/KomachiSignItem.java:100-141` | `F/item/sign/KomachiSignItem.java:100-141` |
| 扫地机被动减半 | 「加急加快」效果牌 | 同上 | `N/item/sign/JasmineSignItem.java:83-92` | `F/item/sign/JasmineSignItem.java:84-93` |
| 史莱姆受击 -200t | `onHurt`(任意立牌冷却均被减) | 同上 | `N/item/sign/LuluSignItem.java:44-59` | `F/item/sign/LuluSignItem.java:45-59` |
| 电流核心筹码 | 冷却中按键清冷却 / 生效 +1 充能 | 同上 | `N/item/chip/CurrentCoreChipItem.java:55-95` | `F/item/chip/CurrentCoreChipItem.java:55-95` |
| 风扇筹码(大/小) | 主动成功后发牌/施标记 | — | `N/item/chip/FanBigChipItem.java:30-50`、`FanSmallChipItem.java:25-36` | `F/...`(同构) |
| 骰子基类(槽位/费用/属性) | Curios 装备生命周期 | `WEAPON_ENHANCEMENT`(item 组件)+ chip 槽位数(动态) | `N/item/dice/DiceCurioItem.java:18-190` | `F/item/dice/DiceCurioItem.java:19-191` |
| 阶层表 | 筹码栏基数 | — | `N/item/dice/DiceTier.java:26-38`、`N/item/dice/DiceTierRegistry.java:14-39`、注册 `N/item/ModItems.java:205-246` | `F/item/dice/DiceTier.java:26-38`、`DiceTierRegistry.java:14-43`、`F/item/ModItems.java:206-246` |
| 下界之星骰子 | 星级 → ARMOR/ATTACK_DAMAGE 瞬态修饰器 | 键 `dice_nether_star_dice_armor` / `_attack` | `N/item/dice/NetherStarDiceItem.java:33-93` | `F/item/dice/NetherStarDiceItem.java:33-94` |
| 黑曜石骰子 | 声明式 ARMOR 修饰器 + 火焰事件 | 键 `<ns>:dice_obsidian_dice_armor` | `N/item/dice/ObsidianDiceItem.java:27-57`;`N/event/ObsidianDiceHandler.java:23-37` | `F/item/dice/ObsidianDiceItem.java:...`;`F/event/ObsidianDiceHandler.java:22-36` |
| 筹码基类 | `onUnequip` → 「真卸下」守卫 → `onChipUnequip` | — | `N/item/chip/BaseChipItem.java:33-85`(守卫 `N/item/CurioSlotUtil.java:87-104`) | `F/item/chip/BaseChipItem.java:33-86`(守卫 `F/item/CurioSlotUtil.java:88-105`) |
| 属性折算统一入口 | `setDefenseArmorBonus(player,key,防御点)` | 玩家 ARMOR 瞬态修饰器,按 key | `N/combat/DiceCombatModifiers.java:82-102` | `F/combat/DiceCombatModifiers.java:83-103` |
| 摩托车头盔/速度轮滑/电击手套 | 声明式属性 / 无属性 | — | `N/item/chip/MotoHelmetChipItem.java:20-57`、`SpeedSkatesChipItem.java:...`、`ElectricGloveChipItem.java:57-81` | `F/...`(见 §4) |
| 铁砧升星 | `AnvilUpdateEvent` | `WEAPON_ENHANCEMENT` | `N/event/AnvilUpgradeHandler.java:110-147` | `F/event/AnvilUpgradeHandler.java:106-143` |
| 下界岩骰子 | `BlockEvent.BreakEvent` | — | `N/event/NetherrackDiceHandler.java:32-61` | `F/...`(同构) |
| 效果移除守卫 | 拦截本模组效果被移除 | — | `N/event/ModEffectEvents.java:110-131` | `F/event/ModEffectEvents.java`(同构) |
| 绿宝石骰子交易 | `ItemCost` 替换,与冷却/槽位/属性无关(仅 `transform/discount`) | — | `N/trade/EmeraldDiceTrade.java:58-65` | `F/trade/EmeraldDiceTrade.java` |

### 1.9 Curios 派发契约(库外证据,决定 §3 的 C1/C7)

- 接口签名(两版本一致):`ICurioItem#onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack)`。`javap -p -l` 的 LocalVariableTable:`slot 2 = newStack`、`slot 3 = stack`;`onEquip(SlotContext, ItemStack prevStack, ItemStack stack)`(`slot 2 = prevStack`)。
- 派发点(1.21.1 curios 9.5.1)`CuriosEventHandler#tick`:`local14 = getStackInSlot(i)`(当前)、`local16 = getPreviousStackInSlot(i)`(上一 tick),`!ItemStack.matches(current, previous)` 时,先走卸下分支 `CuriosApi.getCurio(previous).ifPresent(curio -> curio.onUnequip(ctx, current))`,再走装备分支 `CuriosApi.getCurio(current).ifPresent(curio -> curio.onEquip(ctx, previous))`。
- `ItemizedCurioCapability#onUnequip(ctx, param2)` 转发为 `curioItem.onUnequip(ctx, param2, getStack())`,其中 `getStack()` 是该 capability 绑定的栈(即 `previous`)。
- **结论(字节码事实)**:`onUnequip` 的 **第 2 参 = 当前槽位内容(新栈;槽位被清空时为 `ItemStack.EMPTY`)**,**第 3 参 = 被卸下的旧栈**。1.20.1 curios 5.14.1 的 `CuriosEventHandler#tick` 结构相同(卸下 indy 捕获 `getStackInSlot` 的当前栈,装备 indy 捕获 `getPreviousStackInSlot` 的旧栈),语义一致。

---

## 2. 触发与清理顺序

### 2.1 场景 A:非待命立牌按一次主动(J)

1. 客户端 `while (consumeClick())` 发 `SignActivatePayload` — `N/client/KeyBindingSetup.java:38-40`。无客户端节流。
2. `handle` → `enqueueWork` → `performSkillForCurio` — `N/network/SignActivatePayload.java:28-31`。
3. 读 stand 槽 0 — `N/item/sign/BaseSignItem.java:64-71`。
4. **冷却闸**:`long cdEnd = ModAttachments.getSignActiveCooldownEnd(player); if (cdEnd > 0 && now < cdEnd) { 电流核心尝试立即完成; 否则红色提示; return; }` — `N/item/sign/BaseSignItem.java:84-94`。
5. **等待闸**:`if (isSkillWaiting(player)) return;`(静默,无任何 ActionBar)— `N/item/sign/BaseSignItem.java:96`,`isSkillWaiting` 定义 `:142-146`。
6. `sign.handleUse(...)`;返回非 `SUCCESS` 即终止 — `N/item/sign/BaseSignItem.java:98-99`。
7. **风扇筹码结算**(与冷却是否起无关)— `N/item/sign/BaseSignItem.java:101-102`:`FanBigChipItem.applyAfterSignSkill(player)`(发 1 张随机效果牌 + 16 格敌对标记,`N/item/chip/FanBigChipItem.java:37-49`)、`FanSmallChipItem.applyAfterSignSkill(player)`(16 格标记,`N/item/chip/FanSmallChipItem.java:31-35`)。
8. 发 `SignActiveTriggeredEvent`(游戏总线),无处理器响应则默认提示 — `N/item/sign/BaseSignItem.java:105-110`。
9. **起冷却闸**:`if (ModAttachments.getSignReadyExpire(player) <= 0) { setSignActiveCooldownEnd(now + WeirdDiceHandler.signCooldownTicks(player)); CurrentCoreChipItem.onActiveSkillUsed(player); }` — `N/item/sign/BaseSignItem.java:112-118`。

### 2.2 场景 B:待命立牌武装 → 释放

1. 同上 1~5。
2. `handleUse`:写 `type`/`expire = now + SKILL_WAIT_SECONDS*20` 并施加**无限时长**待命效果 — `N/item/sign/HaiqingSignItem.java:99-102`(`player.addEffect(new MobEffectInstance(HAIQING_READY, Integer.MAX_VALUE, ...))`)。
3. 风扇筹码**立刻结算**(此时技能尚未释放)— `N/item/sign/BaseSignItem.java:101-102`。
4. 事件 → 待命提示并 `setHandled` — `N/item/sign/HaiqingSignItem.java:74-80`。
5. 起冷却闸:此时 `expire > 0` → **不起冷却、不给充能** — `N/item/sign/BaseSignItem.java:112-118`。
6. 每 tick:`expire` 到期且 `type` 匹配 → 清两键 + `ModEffectRemoval.remove(HAIQING_READY)`;未到期则每 20t 重发提示 — `N/item/sign/HaiqingSignItem.java:48-62`。
7. 释放(近战 + `isBlessingTarget`):清 `type`/`expire` → 施加效果 → 移除待命效果 → `setSignActiveCooldownEnd(now + WeirdDiceHandler.signCooldownTicks(player))` → `CurrentCoreChipItem.onActiveSkillUsed` — `N/combat/DiceCombatEvents.java:229-241`(占星师)、`:243-262`(秘密侦探)、`:266-277`(枪匠,`applyBroken` 返回 false 时**不清状态也不起冷却**,`N/item/sign/MosesSignItem.java:132-141`)。枪匠用自有基准 `MosesSignItem.signCooldownTicks`(`:117-125`,装备时 120s)。
8. 到期未释放:仅步骤 6 清状态,**不进入冷却**(玩家可立即再次武装)。

### 2.3 场景 C:同槽位直接换装(立牌 A → 立牌 B / 骰子 A → 骰子 B / 筹码 A → 筹码 B)

1. Curios `tick` 检测槽位内容变化:`getStackInSlot != getPreviousStackInSlot` → 先卸下后装备(§1.9)。
2. **立牌**:`BaseSignItem.onUnequip` 的守卫 —
```java
boolean stillInSlot = ... .map(h -> slotContext.index() < h.getSlots()
        && !h.getStacks().getStackInSlot(slotContext.index()).isEmpty()
        && h.getStacks().getStackInSlot(slotContext.index()).getItem() == stack.getItem())
        .orElse(false);
if (stillInSlot) { return; }      // 「重载」判定
clearSignData(player, stack);
```
`N/item/sign/BaseSignItem.java:168-179`(1.20.1 `:169-180`)。
其中 `stack` 是**第 2 参 = 新栈**(§1.9),而槽位此刻正好持有该新栈 ⇒ 比较恒真(槽位非空时) ⇒ **换装被误判为「Curios 重载」,`clearSignData` 不执行**。仅当槽位最终为空(`stack = EMPTY`,`!isEmpty()` 为假)才会执行清理。
3. **同一个守卫的通用副本**:`CurioSlotUtil.isRealUnequip` (`N/item/CurioSlotUtil.java:87-94`)= 同一表达式;`runOnRealUnequip`(`:97-104`)被 `BaseChipItem.onUnequip` 调用(`N/item/chip/BaseChipItem.java:78-84`),⇒ **换筹码同样不会触发 `onChipUnequip`**。
4. **被跳过的清理内容**(全部只在 `clearSignData`/`onChipUnequip` 里):见 §3 C1/C4 详表。
5. 骰子基类不受此守卫影响:`DiceCurioItem.onUnequip` 无条件调用 `tryRemoveChipBonus`(`N/item/dice/DiceCurioItem.java:80-85`)。

### 2.4 场景 D:真正卸下到空槽(A → 空)

1. 卸下分支派发 `onUnequip(ctx, EMPTY, A)`(§1.9)。
2. 守卫:`!h.getStacks().getStackInSlot(index).isEmpty()` 为假 ⇒ `stillInSlot=false` ⇒ 执行 `clearSignData(player, stack)`,**但 `stack` 此时是 `EMPTY`,不是被卸下的 A** — `N/item/sign/BaseSignItem.java:172-179`。
3. 后果:所有**按栈**清理的立牌(扫地机/上班族/护法)写到了 `ItemStack.EMPTY` 上 —
```java
stack.set(ModDataComponents.JASMINE_ATK_BONUS.get(), 0);   // N/item/sign/JasmineSignItem.java:51-52
stack.set(ModDataComponents.PADMAN_ATK_BONUS.get(), 0);    // N/item/sign/PadmanSignItem.java:50-52
stack.set(ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);  // N/item/sign/MisakiSignItem.java:44
```
   实际立牌栈上的 `jasmine_atk_bonus`/`jasmine_def_bonus`/`padman_*`/`misaki_sign_stacks` **保持原值**。
   写 `EMPTY` 不抛异常:`ItemStack.EMPTY`(`neoforge-21.1.235-sources.jar` `net/minecraft/world/item/ItemStack.java:180,271-274`,其 `components = new PatchedDataComponentMap(DataComponentMap.EMPTY)`),`ItemStack#set` 直转 `components.set`(`:715-718`),`PatchedDataComponentMap#set` 先 `ensureMapOwnership()` 再 `patch.put`(`:66-78,129-134`)。副作用是**全局单例 `ItemStack.EMPTY` 被写入组件**。
4. 按玩家/附件清理的部分(如 `ModAttachments.setRinPages(player,0)`、`setMimiReturnedCardCount(player,0)`、`ModEffectRemoval.remove`、`setDefenseArmorBonus(player,key,0)`)正常生效。

### 2.5 场景 E:死亡

1. `PlayerLifecycleHandler.onPlayerDeathClearEffects` 清 `sign_ready_type`/`sign_ready_expire`(`N/event/PlayerLifecycleHandler.java:132-133`),移除 `HAIQING_READY`/`BONNIE_READY`(`:172-173`),**未显式移除 `MOSES_READY`、未清 `sign_active_cooldown_end`**。
2. 冷却键**:死亡后归零**,因为该附件两版本都不随死亡复制 — 1.21.1 注册处无 `.copyOnDeath()`(`N/component/ModAttachments.java:400-404`;全仓仅 2 处 `copyOnDeath`:`,176`,`:280`);1.20.1 `AstralData.onPlayerClone` 死亡分支只保留 `rin_pages`/`komachi_damage_bonus`(`F/component/AstralData.java:74-99`)。
3. Curios 死亡掉落走 `loseStacks` 路径,`onUnequip(ctx, ItemStack.EMPTY)`,此时槽位已空 ⇒ 守卫放行 ⇒ `clearSignData` 执行(但栈参数仍是 EMPTY,见场景 D)。

### 2.6 场景 F:退出 / 重新登录

`PlayerLoggedInEvent` 只清 `defense_card_consumed_blessing`、`EffectTimerGuard`、`DICE_BLESSING` 并刷新治愈 — `N/event/PlayerLifecycleHandler.java:194-208`(1.20.1 `:191-205`)。**不清 `sign_ready_*`、不清 `sign_active_cooldown_end`**;两者都 `serialize`/持久化(`N/component/ModAttachments.java:401-404,445-455`;`F/component/ModAttachments.java:372-380,408-413` 进入 `AstralData`),故异常状态**跨会话保留**。

### 2.7 场景 G:待命状态在到期边界被按键

1. 到期 tick:`type>0 && expire>0 && now < expire` 为假 ⇒ 等待闸放行 — `N/item/sign/BaseSignItem.java:142-146`。
2. `handleUse` 重新写 `expire = now + 600`(`N/item/sign/HaiqingSignItem.java:99-101`)。
3. 起冷却闸:新 `expire > 0` ⇒ **不起冷却** — `N/item/sign/BaseSignItem.java:112-118`;但步骤 7(风扇)已在 `:101-102` 结算完毕。
⇒ 玩家只要在每次 30 秒等待到期后按一次 J(不释放),即可**无限期保持待命且永不进入主动冷却**,每次按键都白拿一次风扇筹码奖励(30 秒 1 张随机效果牌)。

---

## 3. 冲突项

### 3.0 汇总表

| ID | 严重度 | 现象 | 关键证据 | 建议修法 | 两版本是否皆有 |
|---|---|---|---|---|---|
| S6-C1 | blocker | 同槽位换装(立牌/筹码)时 `clearSignData`/`onChipUnequip` 被误判为「重载」而跳过;真正卸下时又把 `EMPTY` 当作被卸栈传入,按栈清理全部落空 | `N/item/sign/BaseSignItem.java:168-179`;`N/item/CurioSlotUtil.java:87-94`;`N/item/sign/JasmineSignItem.java:51-52` | 守卫改用第 3 参(被卸栈)比较;`clearSignData(player, prevStack)` | 是 |
| S6-C2 | blocker | 残留的 `sign_ready_expire > 0` 使**所有非待命立牌**的主动永不进入冷却(可无限连发) | `N/item/sign/BaseSignItem.java:112-118` + 无人清理(C1 产生残留) | 起冷却判据改为「本次是否武装了等待器」而非读全局键;并为残留加统一清理 | 是 |
| S6-C3 | high | 待命到期边界按键可无冷却续期待命,且每次续期都结算风扇筹码奖励(30s/张) | `N/item/sign/BaseSignItem.java:96,101-102,112-118,142-146`;`N/item/sign/HaiqingSignItem.java:99-101` | 续期时也进入冷却,或把风扇结算移到「释放成功」处 | 是 |
| S6-C4 | high | 换装/换筹码后残留护甲/移速瞬态修饰器(如扫地机 +20 防御力=+40 护甲永久保留) | `N/item/sign/JasmineSignItem.java:43-44,55`;`N/item/chip/AdrenalineChipItem.java:66-67,71-74`;`N/item/chip/EnergyRecyclerChipItem.java:63-68,89-94` | 修 C1;并在 `curioTick` 之外加"离身即清"兜底(参照 `PlayerTickEvents:130-132`) | 是 |
| S6-C5 | medium | 「当前最大冷却÷2」等减免存在 4 套口径:命运指引用 180s 基准(枪匠实际 120s)、扫地机用裸常量、电流核心用固定 180s 换算;且减免量按"使用时佩戴"重算,可先起冷却再换骰子放大 | `N/item/card/FateGuidanceCardItem.java:66-69`;`N/item/sign/MosesSignItem.java:117-125`;`N/item/sign/JasmineSignItem.java:89-91`;`N/item/chip/CurrentCoreChipItem.java:28,65-70` | 起冷却时把「本次最大冷却」写入玩家附件,减免统一读它 | 是 |
| S6-C6 | medium | 筹码栏用**未钳制**的星级(`4+s` 等),卡牌栏/费用却钳到 [0,3]/[3,6];星级越界时筹码栏无界增长且 `grow` 无上限 | `N/item/dice/DiceCurioItem.java:109-114`;`N/component/GameplayConstants.java:86-88`;`N/item/ModItems.java:243-245` | 统一走 `configStarLevel` 或对 `targetChipSlots` 输入/输出双向钳制 | 是 |
| S6-C7 | medium | `DiceCurioItem.onEquip` 把第 2 参(旧骰子)当成"当前骰子"用:初始化 `WEAPON_ENHANCEMENT` 写到被卸下的旧栈、筹码栏按旧骰子计算 | `N/item/dice/DiceCurioItem.java:67-78,96-107` | 改用第 3 参(当前/已装备栈) | 是 |
| S6-C8 | medium | 「主动成功」的两个副作用时点不一致:风扇在**武装**时结算、电流核心充能在**释放**时结算;等待超时未释放则风扇奖励白拿 | `N/item/sign/BaseSignItem.java:101-102` vs `:112-118` | 两者统一到「释放成功」或「本次技能确实生效」 | 是 |
| S6-C9 | low | 卡牌费用上限只由卡牌 GUI 的 `mayPlace` 强制;上限有 4 种口径(NBT 持久值/铁砧写入/GUI 打开时重算/tooltip 用原始星级),出牌结算路径无校验 | `N/screen/CardInventoryMenu.java:444,472`;`:244-250,311-314`;`N/event/ModTooltipHandler.java:347-351` | 出牌结算处补一次上限校验;口径收敛为单一函数(含 T4) | 是 |
| S6-C10 | low | 黑曜石火焰减免的挂载点/优先级两版本不同:1.21.1 `LivingIncomingDamageEvent` + `HIGH`,1.20.1 `LivingHurtEvent` + 默认;1.21.1 注释写 `HIGHEST+1` 与代码不符 | `N/event/ObsidianDiceHandler.java:29-30`;`F/event/ObsidianDiceHandler.java:28-29` | 对齐优先级/事件选点,或按各版本伤害链规范明确记录差异 | 否(两版本实现不同) |
| S6-C11 | low | `MOSES_READY` 未列入死亡显式清理清单(依赖原版清效果);`sign_active_cooldown_end` 亦不在清单(靠"不随死亡复制"间接归零) | `N/event/PlayerLifecycleHandler.java:130-178`(172-173 只列 HAIQING/BONNIE) | 清单补齐三待命效果,避免将来引入 copyOnDeath 时静默失效 | 是 |

### 3.1 S6-C1 换装不清理 / 清理收到空栈(blocker,两版本皆有)

**证据 1(守卫恒真)** `N/item/sign/BaseSignItem.java:168-179`:

```java
168:        boolean stillInSlot = CuriosApi.getCuriosInventory(player)
169:                .flatMap(h -> h.getStacksHandler(slotContext.identifier()))
170:                .map(h -> slotContext.index() < h.getSlots()
171:                        && !h.getStacks().getStackInSlot(slotContext.index()).isEmpty()
172:                        && h.getStacks().getStackInSlot(slotContext.index()).getItem() == stack.getItem())
173:                .orElse(false);
174:        if (stillInSlot) {
175:            // 重载场景(物品仍在槽位):不清理数据
176:            return;
177:        }
178:        clearSignData(player, stack);
```
`stack` = 第 2 参 = 当前槽位内容(§1.9),所以 A→B 换装时 `getStackInSlot(index)` 与 `stack` 同一物品 ⇒ `stillInSlot == true` ⇒ 直接 `return`。
**证据 2(同一表达式被筹码复用)** `N/item/CurioSlotUtil.java:87-94` + `N/item/chip/BaseChipItem.java:78-84`:
```java
 87:    public static boolean isRealUnequip(SlotContext slotContext, ItemStack stack, LivingEntity entity) {
 88:        return !CuriosApi.getCuriosInventory(entity)
 91:                        && !h.getStacks().getStackInSlot(slotContext.index()).isEmpty()
 92:                        && h.getStacks().getStackInSlot(slotContext.index()).getItem() == stack.getItem())
```
```java
 83:        CurioSlotUtil.runOnRealUnequip(slotContext, curio, player, p -> onChipUnequip(p, curio));
```
**证据 3(空栈入参)** 槽位清空时第 2 参为 `ItemStack.EMPTY`,而 `clearSignData(player, stack)` 传的是第 2 参;真正被卸下的栈是第 3 参 `prevStack`(该参数在 `onUnequip` 内**从未被使用**,`N/item/sign/BaseSignItem.java:165-180` 无第三参引用)。按栈清理因此全部失效:`N/item/sign/JasmineSignItem.java:51-52`、`N/item/sign/PadmanSignItem.java:50-52`、`N/item/sign/MisakiSignItem.java:44`。
**影响面**:`clearSignData` 全仓唯一调用点是 `BaseSignItem.onUnequip`(`grep clearSignData` 结果:仅 `BaseSignItem.java:178` 调用);`onChipUnequip` 覆写共 14 个筹码类(扫地机外的 `Adrenaline/PrimordialCore/RevengeHalberd/EnergyRecycler/ElectricSword/CursedSword/Candy/Flashlight/MagicTome/MagicQuiver/Medkit*/PiggyBank/Satellite/StarCoinHammer/EightSided`),其中只要有基于「离身即清」的语义就会被跳过。
**建议修法**:`stillInSlot` 比较对象改为 `prevStack`(被卸栈);`clearSignData(player, prevStack)`。两者都以第 3 参为准即可同时修好 C1 与场景 D。

### 3.2 S6-C2 残留 `sign_ready_expire` ⇒ 非待命立牌主动永久免冷却(blocker,两版本皆有)

**机理**:起冷却判据读的是全局键而非"本次武装":
```java
112:        if (ModAttachments.getSignReadyExpire(player) <= 0) {
114:            ModAttachments.setSignActiveCooldownEnd(player,
115:                    now + com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player));
117:            com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.onActiveSkillUsed(player);
118:        }
```
`N/item/sign/BaseSignItem.java:112-118`。残留值是非零的**过去时刻**,`<= 0` 为假 ⇒ 之后每一次主动都跳过冷却与充能。
**残留如何产生**:唯一在 mod 内可达的路径就是 C1——武装待命立牌 A(`expire>0`)后直接换装成**非待命**立牌 B(Curios GUI 拖放/快捷移动同槽位替换)。B 的 `onCurioTick` 有类型守卫不会清(`N/item/sign/HaiqingSignItem.java:52`,`== READY_TYPE`),A 已不在身不再 tick,故无人清理。
**清理点清单(全仓穷举,`grep setSignReadyExpire`)**:三立牌自身 tick(类型守卫)、三立牌 `clearSignData`(类型守卫 + C1 不可达)、`DiceCombatEvents` 释放(类型匹配)、`PlayerLifecycleHandler:132-133`(死亡)。**没有任何「无关于当前立牌」的兜底清理**,登录也不清(`:194-208`)。
**次生表现**:等待未到期期间,换上的立牌按 J 被静默吞掉(`BaseSignItem.java:96` 无任何反馈);残留跨会话保留(§2.6)。
**建议修法**:(a) 起冷却判据改为 `boolean armedThisCall`(由 `handleUse` 返回或比较调用前后的 `expire`);(b) 在 `performSkill` 开头/玩家 tick 加统一兜底:若 `signReadyExpire <= now` 则无条件清零两键。

### 3.3 S6-C3 到期边界无冷却续期 + 风扇奖励重复结算(high,两版本皆有)

`isSkillWaiting` 要求 `now < expire`(`N/item/sign/BaseSignItem.java:142-146`),到期后闸门放行;`handleUse` 无条件重写 `expire`(`N/item/sign/HaiqingSignItem.java:99-101`),而风扇结算在 `handleUse` 成功之后、冷却闸之前(`:101-102`),冷却闸又因新 `expire>0` 不成立(`:112-118`)。⇒ 30 秒一次免费:随机效果牌 1 张 + 16 格敌对目标标记(`N/item/chip/FanBigChipItem.java:37-49`),且待命状态可无限保持(效果 `Integer.MAX_VALUE`,`N/event/ModEffectEvents.java:127-130` 还禁止牛奶/指令单独清除本模组效果)。
**建议修法**:续期同样起冷却(或把"续期"改为需要重新通过冷却闸);风扇结算移到「技能确实产生效果」的分支(`DiceCombatEvents` 释放点)。

### 3.4 S6-C4 换装/换筹码后残留属性修饰器(high,两版本皆有)

`setDefenseArmorBonus` 是**玩家 ARMOR 上的瞬态修饰器**,按 key 增删(`N/combat/DiceCombatModifiers.java:87-102`),只有显式写 0 才会移除。以下 key 的"移除点"都在被 C1 跳过的清理里:

| key | 谁写(每 tick) | 谁清 | 残留条件 |
|---|---|---|---|
| `jasmine_def_armor` | `N/item/sign/JasmineSignItem.java:43-44` | `:55`(`clearSignData`) | 换出扫地机立牌 ⇒ 累积的 +2×defBonus 护甲永久保留(上限 +40) |
| `padman_def_armor` | `N/item/sign/PadmanSignItem.java:30-31` | `:53` | 同上 |
| `papara_def_armor` | `N/item/sign/PaparaSignItem.java:45-46` | `:52` | 同上 |
| `nancy_lu_def_armor` | `N/item/sign/NancyLuSignItem.java:85-86` | `:103` | 同上 |
| `moses_weakness_armor` | `N/item/sign/MosesSignItem.java:49-50,174` | `:90` | 同上 |
| `adrenaline_def_armor{3,8}` | `N/item/chip/AdrenalineChipItem.java:66-67` | `:71-74`(`onChipUnequip`) | 换筹码 ⇒ 残留;**无任何全局 tick 兜底**(`N/event/PlayerTickEvents.java:119-143` 未涉及) |
| `chip_*_charge_speed` | `N/item/chip/EnergyRecyclerChipItem.java:74-88` | `:89-94`(`onChipUnequip`) | 同上,+5% 移速残留 |

**不残留的对照(证明"全局 tick 兜底"有效)**:`revenge_halberd_def_armor` 每 tick 由 `N/event/PlayerTickEvents.java:130` 重算(未佩戴即写 0)、`primordial_core_def_armor` 同理 `:132`、`fen_def_armor` 在 `N/item/sign/FenSignItem.java:146-147` 用 `isEquipped(...) ? 2 : 0` 自清;`moto_helmet_*`/`speed_skates_*`/`obsidian_dice_*` 走声明式 `getAttributeModifiers`(`N/item/chip/MotoHelmetChipItem.java:42-56`、`N/item/dice/ObsidianDiceItem.java:37-45`),由 Curios 随槽位施加/撤销。
**残留寿命**:死亡/重生会清除瞬态修饰器(1.21.1 `ServerPlayer.restoreFrom` → `AttributeMap.assignBaseValues` 只复制 base value,`neoforge-21.1.235-sources.jar` `net/minecraft/server/level/ServerPlayer.java:1437-1442`、`net/minecraft/world/entity/ai/attributes/AttributeMap.java:107-114`)。
**建议修法**:修 C1;对"筹码/立牌离身即清"的 key 增加全局 tick 兜底(或统一由槽位声明式属性承载)。

### 3.5 S6-C5 冷却减免口径不一致(medium,两版本皆有)

起冷却值统一走 `WeirdDiceHandler.signCooldownTicks`(`N/item/sign/BaseSignItem.java:114-115`):
```java
36:    public static int signCooldownTicks(Player player) {
37:        int ticks = GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;      // 3600
38:        if (hasWeirdDice(player)) { ticks = Math.max(1, ticks / 2); }  // →1800
41:        return (int) ChargeManager.cooldownTicks(player, ticks);       // →1440(ceil(1800*0.8))
```
**枪匠例外**:`N/item/sign/MosesSignItem.java:117-125` 装备时基准 2400(120s)再 /2、再 ×0.8 ⇒ 实际最大 960。
**减免口径分歧**:
1. 命运的指引:`long maxCooldown = WeirdDiceHandler.signCooldownTicks(player); long reduction = maxCooldown / 2;` — `N/item/card/FateGuidanceCardItem.java:66-69`。对枪匠玩家用 180s 基准算出的减免(960 时约 720)可占实际最大的 **75%**,与 `文档/tooltip`「当前最大冷却 ÷ 2」不符;若在起冷却后把诡异骰子卸下再使用,`signCooldownTicks` 变回 3600 ⇒ 减免 1800 ≥ 剩余 ⇒ **直接清零**。
2. 扫地机被动:`Math.max(now, cdEnd - GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS / 2)` — `N/item/sign/JasmineSignItem.java:89-91`,固定扣 1800。对"诡异骰子 + 充能"玩家实际最大 1440 ⇒ **一次扣满(100%)**;且不检查立牌归属,可作用于任意立牌起的冷却。
3. 史莱姆受击:`Math.max(0, cdEnd - 200)` — `N/item/sign/LuluSignItem.java:52-56`(1s 内限一次,`:47-49`),固定 10s,同样与最大冷却无关,且可减少任意立牌的冷却。
4. 电流核心:档位按固定 180s 换算 `N/item/chip/CurrentCoreChipItem.java:28,65-70`,枪匠(960t)满冷却只需 `ceil(960/3600*6)=2` 点(而非 6 点)。
5. 忍者 -30% 是"剩余 ×0.7"(`N/item/sign/KomachiSignItem.java:132-141`),口径正确,可作为统一模板。
**建议修法**:起冷却时把本次「最终最大冷却 tick」写入玩家附件(`sign_active_max_cooldown`),命运指引/扫地机/电流核心一律读它;或统一改为按剩余比例减免。

### 3.6 S6-C6 筹码栏星级未钳制 / 增长无上限(medium,两版本皆有)

`N/item/dice/DiceCurioItem.java:109-118`:
```java
109:    private static int targetChipSlots(ItemStack stack) {
111:        DiceTier tier = DiceTierRegistry.get(stack);
112:        if (tier == null) return CHIP_NO_DICE_SLOTS;
113:        return tier.targetChipSlots(starLevel(stack));      // 原始星级,未钳制
...
116:    private static int starLevel(ItemStack stack) {
117:        return stack.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY).starLevel();
```
对比卡牌栏/费用的钳制:`N/item/dice/DiceCurioItem.java:38-47`(`Math.max(0, Math.min(3, ...))`、T4 强制 3)、`N/component/GameplayConstants.java:86-88`(`Math.min(6, 3 + Math.max(0, star))`)。阶层表含未设上限的加法:`N/item/ModItems.java:243-245` `s -> 4 + s`(T4,0★4…3★7);`DiceTier.targetChipSlots` 直通(`N/item/dice/DiceTier.java:29-32`);应用处 `handler.grow(target - current)` 无上限钳制(`N/item/dice/DiceCurioItem.java:168-171`)。`WeaponEnhancement` 的 `Codec.INT` 无范围校验(`N/component/WeaponEnhancement.java:21`);mod 内无降星路径(铁砧只做 `star+1` 且 `>=3 return`,`N/event/AnvilUpgradeHandler.java:124,139`),故本条需外部写入(NBT/指令/其它 mod)才可触发。
**建议修法**:`targetChipSlots` 输入改 `configStarLevel(stack)`(或用 `actualStar` 语义),并在 `setSlotCount` 对 target 做上限钳制。

### 3.7 S6-C7 `DiceCurioItem.onEquip` 参数语义反了(medium,两版本皆有)

§1.9 已证 `onEquip(ctx, prevStack, currentStack)`;而实现把第 2 参命名为"curio":
```java
 67:    public void onEquip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
 68:        if (!curio.has(ModDataComponents.WEAPON_ENHANCEMENT.get())) {
 69:            curio.set(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
...
 76:            tryApplyChipBonus(slotContext, curio, false);      // 用旧骰子算筹码栏
```
⇒ 换骰子(A→B)后,筹码栏目标按 **A** 计算(a 的阶层/星级),且 `WEAPON_ENHANCEMENT` 缺省值写到被卸下的 A 上;B 的实际槽位数要等 `curioTick`(`:88-93`,每 20t)才被纠正。反向(0★→T4)期间筹码栏会短暂保持 0。另:`onUnequip` 无条件 `tryRemoveChipBonus`(`:80-85`)与 onEquip 顺序为"先卸后装"(§1.9),所以最终以 B 的 tick 为准,属**瞬时错值**而非永久错误。
**建议修法**:`onEquip` 内改用第 3 参(当前栈);或直接删除该初始化(已有 `getOrDefault(..., EMPTY)` 兜底)。

### 3.8 S6-C8 「主动成功」两个副作用时点不一致(medium,两版本皆有)

风扇:`N/item/sign/BaseSignItem.java:101-102`(在事件前、冷却闸前,凡 `handleUse` 返回 SUCCESS 即结算);电流核心:`:112-118`(仅在真正起冷却时 `onActiveSkillUsed`)。⇒ 待命类技能"武装即结算风扇",而"释放才结算充能";等待超时未释放时风扇奖励白拿(与 C3 叠加)。此外 `DiceCombatEvents` 释放点也各自调用 `CurrentCoreChipItem.onActiveSkillUsed`(`:240,261,275`),与 `BaseSignItem` 不同路径,但不会双重记账(释放路径下 `BaseSignItem:112` 因 `expire>0` 不成立)。
**建议修法**:把风扇结算与充能记账放进同一个「技能确实生效」回调。

### 3.9 S6-C9 卡牌费用上限只由 GUI 校验 + 四套取值口径(low,两版本皆有)

唯一强制点:`N/screen/CardInventoryMenu.java:444`(及防御 `:472`)`return (usedWithoutThis + slotCost) <= maxAttackCost;`。而 `cardCostForStar` 的调用者只有 `CardInventoryMenu`(每版 2 处)、`AnvilUpgradeHandler`(`:136,138`)、`ModTooltipHandler`(`:348,351`);出牌与骰战结算路径**无上限校验**。口径并存:① NBT 持久 `maxCost`(`WeaponEnhancement.EMPTY = (0,3,0,3,0,[])`,N:12-13);② 铁砧写 `cardCostForStar(star+1)`(N:136,138);③ 打开界面时按 `cardCostForStar(configStarLevel)` 重算内存字段并在关闭时写回(N:244-250、258-264、311-314);④ tooltip 用 `enhancement.starLevel()` 原始值且 T4 不显示费用行(N:347-351、376-377)。
**建议修法**:结算侧补校验;口径收敛为单一函数(含 T4 语义),把 `maxCost` 字段降级为展示缓存。

### 3.10 S6-C10 黑曜石火焰减免两版本落点不同(low,仅 1.20.1 偏离)

1.21.1:`@SubscribeEvent(priority = EventPriority.HIGH)` + `LivingIncomingDamageEvent`(`N/event/ObsidianDiceHandler.java:29-36`);1.20.1:无 priority(默认)+ `LivingHurtEvent`(`F/event/ObsidianDiceHandler.java:28-35`)。同一句 `setAmount(amount * (1 - 0.7))`,但介入时机不同(1.20.1 晚于 `LivingHurtEvent` 之前的减免链,1.21.1 在 incoming 阶段),与其它伤害修正器(如 `DiceCombatEvents` 的骰战结算、`FateGuidanceCardItem` 的 LOWEST 捕获)的相对顺序因此不同。另:1.21.1 类注释写 `HIGHEST+1`,代码为 `HIGH`(注释与代码不符,以代码为准)。

### 3.11 S6-C11 死亡清理清单不完整(low,两版本皆有)

`N/event/PlayerLifecycleHandler.java:172-173` 只 `player.removeEffect(HAIQING_READY/BONNIE_READY)`,`MOSES_READY` 缺失(1.20.1 `:168-169` 同样缺失);`sign_active_cooldown_end` 也未显式清零(靠"不随死亡复制"间接归零,见 §2.5)。当前依赖原版死亡清效果,一旦将来给冷却键加 `.copyOnDeath()` 或改用别的存储,残留会静默复活。

---

## 4. 跨版本对等性差异

| 维度 | 1.21.1 | 1.20.1 | 是否影响本切片行为 |
|---|---|---|---|
| Curios 派发契约 | `onUnequip(SlotContext, newStack, stack)`(9.5.1) | 同签名(5.14.1) | 否(两侧同样踩 C1) |
| 冷却/待命存储 | `AttachmentType.builder().serialize(...).sync(...)`(`N/component/ModAttachments.java:400-404,444-455`) | `AttachedDataKey` + `AstralData`(Capability)+ `SYNCED_KEYS`(`F/component/ModAttachments.java:372-380,408-413,832-834`) | 否 |
| 死亡保留键 | 仅 2 处 `.copyOnDeath()`(`N/component/ModAttachments.java:176,280`) | `AstralData.onPlayerClone` 只保留 2 键(`F/component/AstralData.java:74-99`) | 否(冷却/待命均归零) |
| `performSkill`/`onUnequip` 逻辑 | `N/item/sign/BaseSignItem.java:61-180` | `F/...:62-180`(整体 +1 行,`CuriosApi`→`CuriosCompat`) | 否(行号偏移,语义相同) |
| 待命释放点 | `N/combat/DiceCombatEvents.java:228-282` | `F/...:224-279`(`ModEffects.X.get()`、`ModDataComponents.getOrDefault(stack,...)`) | 否 |
| 属性修饰器 id | `ResourceLocation.fromNamespaceAndPath(ns, key)`(`N/combat/DiceCombatModifiers.java:91`) | `UUID.nameUUIDFromBytes("astral_dice:" + key)`(`F/combat/DiceCombatModifiers.java:92-93`) | 否 |
| 属性修饰器构造/枚举 | `(id, amount, Operation)` / `ADD_VALUE` / `ADD_MULTIPLIED_TOTAL` | `(id, name, amount, Operation)` / `ADDITION` / `MULTIPLY_TOTAL` | 否 |
| 骰子槽声明 | 数据驱动 JSON:`data/astral_dice/curios/slots/{dice,stand,chip}.json` + `entities/player.json:3`;**dice.json 无 `size`** ⇒ 取 Curios 默认 1;stand=1、chip=0 | IMC 显式注册:`F/AstralDiceMod.java:98-106` dice `.size(1)`、stand `.size(1)`、chip `.size(0)` | 潜在(1.21.1 依赖库默认值;数据包/第三方可覆盖) |
| 物品标签目录 | `data/curios/tags/item/*` | `data/curios/tags/items/*` | 否 |
| 骰子组件 API | 数据组件 `persistent + networkSynchronized`(`N/component/ModDataComponents.java:19-23`) | `ItemDataKey`(NBT,自动同步,无 `STREAM_CODEC`) | 否 |
| `CardInventoryMenu` 同步 | 3 个 `DataSlot`(含 `starLevel`,`N/screen/CardInventoryMenu.java:84-95`) | 无 starLevel DataSlot(第二个是 `maxDefenseCost`,`F/...:91-96`),两版都在界面内本地算 | 否(显示同步差异) |
| 黑曜石火焰减免 | `LivingIncomingDamageEvent` + `HIGH` | `LivingHurtEvent` + 默认 | **是**(见 C10) |
| 死代码 | — | `DiceTierRegistry.all()`(`F/item/dice/DiceTierRegistry.java:40-42`)无调用者 | 否 |
| 额外附件键 | — | 多 `damage_effect_bonus`/`curse_original_amount`(本切片无关) | 否 |

**对等性结论**:S6 的核心状态机(冷却闸、等待闸、待命三立牌、释放点、清理点)两版本逐行同构,§3 的 C1/C2/C3/C4/C6/C7/C8/C9 在两侧**同时存在且成因相同**;唯一行为性差异是 C10(黑曜石火焰减免落点)。

---

## 5. 未能判定 / 需人工确认

1. **`SignActiveTriggeredEvent` 订阅者的确切执行顺序**:8 个订阅者全部通过 `@EventBusSubscriber` 注解扫描注册、均为默认优先级(`N/item/sign/HaiqingSignItem.java:74`、`BonnieSignItem.java:74`、`MosesSignItem.java:71`、`MimiSignItem.java:96`、`KomachiSignItem.java:88`、`FannySignItem.java:125`、`NancyLuSignItem.java:154`、`PandamanSignItem.java:118`;1.20.1 依次 `74/74/77/98/89/125/157/120`)。源码未声明顺序,`@EventBusSubscriber` 扫描次序由 mod 文件类清单决定(不在本仓);**但由于每个处理器都按各自物品 id 命中,同一次触发最多只有一个处理器生效,顺序对视听反馈无影响**。需实测仅当将来出现"同一次主动命中两个处理器"的新订阅者。
2. **`consumeClick()` 是否会在同一客户端 tick 返回多次**(GLFW 自动重复输入):已确认 `KeyboardHandler.keyPress` 对 `action == 1 || action == 2` 都进入处理分支(`neoforge-21.1.235-sources.jar` `net/minecraft/client/KeyboardHandler.java:408`)且存在 `KeyMapping.click(...)`(`:481`),但"repeat 是否最终入队为 click"未逐行核验。这决定**同一 tick 内是否存在多次主动**(在 C2 的免冷却状态下会放大为多次技能生效)。
3. **Curios 内部对声明式 `getAttributeModifiers` 的施加/撤销时点**(库代码,本仓无源码):据其自身注释被其它筹码引用(`N/item/chip/EnergyRecyclerChipItem.java:25-28`「仅在装备变更时求值」),判定为声明式安全路径;精确时点需运行期验证。
4. **残留瞬态修饰器在"退出重进"后的存续**:已证实死亡/重生清除(`ServerPlayer.restoreFrom` → `assignBaseValues`);但同一实体重登时 `AttributeInstance` 的修饰器是否随实体 NBT 持久化未逐行核验。
5. **星级的 mod 外写入路径**:铁砧只增不减(`N/event/AnvilUpgradeHandler.java:124,139`),mod 内无降星/越界路径;C6 的无界筹码栏、以及"卡牌栏收缩时 `saveToDice` 只收集 `cardSlots` 个已装配卡牌并直接丢弃其余"(`N/screen/CardInventoryMenu.java:265-284,287-315`,不退还背包,与筹码栏 `forceRemove=true` 归还背包的处理不对称)都需外部写入(指令/NBT/其它 mod)才可达,故未列为冲突项。
6. **1.20.1 IMC 槽位与 Curios `slots` 配置同时命中时的最终优先级**(`run/1.20.1/config/curios-common.toml:4`):逻辑在依赖 jar 内,需运行期实测。
7. **`onEquip` 的其它派发路径**:§1.9 的实参结论基于 `CuriosEventHandler#tick` 的槽位差异检测;`onEquipFromUse` / 容器直插等路径未逐一核验,若其中某条传入顺序不同,C7 的表现形式(瞬时错值 vs 永久错值)会不同。
8. **`ItemStack.EMPTY` 被写入自定义组件后的可见影响**(§2.4 场景 D):不抛异常已由 vanilla 源码确认,但"全局 EMPTY 携带 `jasmine_atk_bonus=0` 等组件"是否会让任何读取方误判,未穷举。
