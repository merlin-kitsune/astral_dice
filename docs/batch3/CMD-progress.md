# 批次 3 调试命令 — 实施进度与决策台账

分支 `multi-1.20.1-1.21.1`,HEAD `7b9e711`(起点)。**本批已停在构建前(原因见 §0)。**

## 0. 构建/提交闸门(先判定,已生效)

侦察:`Get-CimInstance Win32_Process -Filter "Name='java.exe'"`

- PID 1624 = `net.caffeinemc.sodium / net.minecraft.client.main.Main /` → **Minecraft 客户端在跑**
- PID 2076 = `gradlew :neoforge-1.21.1:runClient`(run 任务)
- PID 5376 = Gradle daemon

⇒ 按任务要求 **只完成源码/资源/文档改动,不构建、不提交**(`gradlew build` 的 `pushToGame`
会把新 jar 推到 `run/*/mods`,污染正在跑的实机测试)。

已做的**非构建**校验(都不触碰 Gradle / `run/`,不产出 jar):
- `javac -XDrawDiagnostics -proc:none -nowarn`(空 classpath)**纯语法/解析体检** → 6 个改动文件
  **零 parse/syntax 诊断**(其余诊断全是预期内的 `cant.resolve.location` / `doesnt.exist`;
  `duplicate.class` 只因 N/F 两份源码同名同类被同批编译)。
- `tools/check_lang_sync.ps1` 双版本 **PASS**(621/621)。
- 自写脚本核对 4 份 lang:key 集合完全一致、LF、无尾换行、无 BOM、一行一条目。

## 1. 需求终版(用户两次追加口径后)

| 子命令 | 语义 |
|---|---|
| `cleareffect [目标]` | 清除**全部**本模组效果(`ModEffects.ALL`,33 个) |
| `clearcardeffect [目标]` | 只清除**效果牌施加的效果**(= `EffectCardPeriod.EFFECT_PENDING_SOURCES` 的 9 个) |
| `resetcardlock [目标]` | 重置**出牌锁的 ①②**:出牌数(含「每轮一次」标记)+ 出牌冷却;主字面量 |
| `resetcardcolddown` / `resetcardcooldown` | `resetcardlock` 的两个**别名**(同一构建器工厂 + 同一执行方法,无第二份逻辑) |

- 全部 `requires(src -> src.hasPermission(2))`;可选玩家参数(`EntityArgument.players()`);无参 → 执行者自己。
- 反馈一律 `Component.translatable`,7 个新 lang key。

## 2. 管理员功能原则(用户第三次追加,已落实)

- **定位**:正式管理员功能,**随 jar 发布**,无 dev/测试开关、无配置开关、无「仅调试模式注册」。
- **权限硬门槛**:根字面量 + 每个子命令都带 `hasPermission(2)`,**无任何降低门槛的旁路**。
- **服务端权威**:只在服务端注册与执行(`RegisterCommandsEvent` 服务端派发器 + `CommandSourceStack`
  的 `ServerPlayer` 路径),无仅客户端分支。
- **能力边界**:只做「重置 / 清除本模组自身状态」——不给资源、不设任意值、默认只作用执行者
  (给选择器才作用于该玩家)、不被玩法路径调用、不碰原版/其它模组状态。
- ⚠️ **因该原则删除的实现**:早期版本在 `cleareffect` 里连带移除 `marked` 的伴生**原版**
  `minecraft:glowing`(理由是模组自己把「发光但无标记」列为 BUG,见 `MarkManager.java:61-68`、
  `:83-86`)。用户明确把「清掉光灵箭给的发光」列为**功能越界红线** ⇒ **已从两版本彻底删除该分支**
  (并删掉随之无用的 `MobEffects` import),改为在代码注释里写明「刻意不动 + 已知后果」,
  防止后人又"顺手修"回来。

## 3. API 取证(本机 moddev artifacts,非记忆)

`temp/probe_sig.py` / `probe_dr.py` / `probe_dr2.py` / `probe_cmd.py` 读
`neoforge-21.1.235-sources.jar` 与 `forge-1.20.1-47.4.10-sources.jar`:

| 事项 | 1.21.1 (N) | 1.20.1 (F) |
|---|---|---|
| 全部效果只读集合 | `DeferredRegister.getEntries()` → `Collection<DeferredHolder<T,? extends T>>`(`entriesView = Collections.unmodifiableSet(entries.keySet())`,**活视图**,DR.java:190-191/326-328) | 同构,`Collection<RegistryObject<T>>`(DR.java:146-147/334-337) |
| 命令事件 | `net.neoforged.neoforge.event.RegisterCommandsEvent#getDispatcher()`(GAME/EVENT bus) | `net.minecraftforge.event.RegisterCommandsEvent#getDispatcher()`(FORGE bus) |
| 移除效果 | `LivingEntity.removeEffect(Holder<MobEffect>)`(LivingEntity.java:1035,内部 `EventHooks.onEffectRemoved`) | `LivingEntity.removeEffect(MobEffect)`(LivingEntity.java:1002,内部 post `MobEffectEvent.Remove`) |
| 权限 / 参数 / 反馈 | `hasPermission(int)`(:390)、`EntityArgument.players()/getPlayers()`(:84-89)、`sendSuccess(Supplier,boolean)`(:480)/`sendFailure(Component)`(:510) | :174 / :79-85 / :282 / :314 |
| `MobEffects.GLOWING` 类型 | `Holder<MobEffect>`(:96) | `MobEffect`(:37) |

⇒ 两版本平台差异**只有三处**:效果条目类型、`removeEffect` 参数类型、命令事件包名/bus。

## 4. 效果移除通道(关键,勿改)

- `ModEffectEvents.onModEffectRemovalPrevented`(N:111-131 / F:107-127)会**拦截并取消全部
  `astral_dice:` 命名空间效果**的外部移除(牛奶/`/effect clear`)⇒ 命令里**必须**走
  `ModEffectRemoval.remove(...)`,否则 `cleareffect` **完全无效**。
- 走内部通道的**附带收益**(无需额外代码):
  - `onEffectTimerForget`(N:149-159 / F:146-155)遗忘 `effect_timer_ends`;
    **否则** `EffectTimerGuard.tick` 的 `inst == null` 分支(N:126-129 / F:125-128)
    会把效果**重新施加回来**。
  - `InvestigationEventUtil.onUndercoverRemoved`(N:122-131 / F:120-128)清 `undercover_source`。

## 5. `clearcardeffect` 的权威来源与覆盖核对

- `EFFECT_PENDING_SOURCES`(N:77 / F:78)原本 `private` ⇒ 新增只读入口
  `EffectCardPeriod.effectPendingEffects()`(N:109-127 / F:118-136):逐条取 `effect()`、
  去重、剔 null,返回 `List.copyOf` 不可变副本。**全仓 `registerEffectPendingSource` 只有
  `EffectCardPeriod` 静态块内 9 次注册(N:124-132 / F:125-133),且全部经 `Holder`/`MobEffect`
  重载(N:95-107 / F:96-108)⇒ 每个 `isActive` 都是 `player.hasEffect(effect)`,纯效果实例驱动,
  不依赖任何其它附件** ⇒ ③ 只要移除效果实例即可解除,无需清额外状态。
- 效果牌施加的效果清单(N `item/card/`):

| 效果 | 施加点 | 在 `EFFECT_PENDING_SOURCES` | 处置 |
|---|---|---|---|
| `king_power` | EffectCardItem.java:36 | ✅ | 清 |
| `berserk` | BerserkCardItem.java:37 | ✅ | 清 |
| `unwavering` | UnwaveringCardItem.java:40 | ✅ | 清 |
| `living_page` | LivingPageItem.java:49 | ✅ | 清 |
| `monster_laser` | MonsterLaserCardItem.java:20 | ✅ | 清 |
| `monster_brick` | MonsterBrickCardItem.java:20 | ✅ | 清 |
| `orbital_strike` | OrbitalStrikeCardItem.java:20 | ✅ | 清 |
| `directional_blast` | DirectionalBlastCardItem.java:20 | ✅ | 清 |
| `fate_guidance` | FateGuidanceCardItem.java:55 | ✅ | 清 |
| `movement_speed`(加急加快) | ExpressDeliveryCardItem.java:36 | ❌ 原版 | **不清** |
| `poison` / `regeneration`(以毒攻毒) | FightPoisonWithPoisonCardItem.java:39/55 | ❌ 原版 | **不清** |
| `damage_resistance`(岿然不动附赠) | UnwaveringCardItem.java:42 | ❌ 原版 | **不清** |
| `marked`(+`glowing`;活体书页/瞄具/标靶) | MarkManager.java:44-49 | ❌ 非出牌锁来源 | **不清** |

不清原版 rider 的理由:不参与出牌锁 ③、来源众多(药水/信标/其它牌),清掉会波及与效果牌无关的增益;
不清 `marked` 的理由:它不参与出牌锁,且**并非效果牌独有**(`MarkManager` 类注释:普通瞄具/鹰眼瞄具
攻击、标靶定时、活体书页远程伤害都会施加)。

## 6. `cleareffect` 耦合附件逐条裁决(最终)

**一并清理(同生共死)**

| 附件 | 理由 + 证据 |
|---|---|
| `effect_timer_ends` | 计时器守卫记录;不清则 `EffectTimerGuard.tick` 的 `inst==null` 分支把效果**重新施加**。**经内部通道自动清**(N `ModEffectEvents:149-159` / F `:146-155`)。 |
| `fate_active_until` | `FATE_GUIDANCE` 效果**只是显示**,功能由该附件驱动(N `FateGuidanceCardItem:54` 写入、`:87-90` 判定;F `:53`/`:86-89`)。只清效果 → 图标消失但「七咒减伤减半」**隐藏生效最多 5 分钟**。显式置 0。 |
| `weak_mark_source` | 印记来源归属;施加(N `DiceCombatEvents:243` / F `:248`)与到期清理(N `HaiqingSignItem:143-150` / F `:143-150`)与效果同生共死。唯一读取点被 `hasEffect(WEAK_MARK)` 守卫 ⇒ 清理**可证行为中性**。显式置空。 |
| `sign_ready_type` / `sign_ready_expire` | `*_ready` 三个提示效果是「待命」窗口的**显示**,权威态在这两个键;一起清理见 N `BaseSignItem:189-207` / F `:190-208`。只清效果 → 窗口仍在,按主动键被 `isSkillWaiting`(N `:171-175` / F `:172-176`)**静默拒绝**最多 30 秒且**无任何提示**。显式置 0。 |
| `undercover_source` | 与 `UNDERCOVER_INVESTIGATION` 同生共死,**经内部通道自动清**(N `InvestigationEventUtil:122-131` / F `:120-128`)。 |

**明确不动(后果已核)**

| 附件 | 后果 |
|---|---|
| `healing_points` / `healing_timer_end` | 是**资源池**(N `HealingManager:44-46`,供回血/美工刀增伤),`HEALING` 只是其显示(`:216-250`)。后果:治愈点 >0 且赐福/计时器仍在时,`HealingManager.tick`(`:207`)每 20 tick 重刷 ⇒ 图标**可能 ≤20 tick 内重现**。清资源池＝销毁玩家进度,不做。 |
| `investigation_stage` | 调查阶段**永久进度**(仅卸下立牌时清,N `BonnieSignItem:86`);与 `INVESTIGATION_BONUS` 效果不同寿命。保留。 |
| `cursed_sword_bonus` / `cursed_sword_blessing_triggered` | 属诅咒之剑**筹码**与赐福周期,非 `BLUE_CURSE` 寿命(N `CursedSwordChipItem:70-75`、`:83-96`);新赐福会重置该标记(N `DiceCombatEvents:315/325` / F `:321/331`)⇒ **自愈**。 |
| `empower_decay_at` | `EMPOWER` 层数在效果 amplifier 内;`EmpowerManager.tick` 在层数 ≤0 时自动归 0(N `:59-67` / F `:61-64`)⇒ **自愈**。 |
| `sign_active_lock_*` | 锁定判定要求「门控效果实例仍在」(N `BaseSignItem:239-252`);效果被移除即提前结束,玩家级 tick `tickSignActiveLock`(N `:320-343`,迁移在 `:341-342`)自动**转为冷却** ⇒ 无空档、无残留。 |
| `nancy_lu_hidden_until` | 驱动的是**原版** `INVISIBILITY`(N `NancyLuSignItem:70-77`),不是本模组效果 ⇒ 命令不该碰。 |
| `komachi_damage_bonus` / `rin_pages` / 星光星币 | 与效果无关的计数器,按用户口径不动。 |
| `minecraft:glowing`(原版) | **红线,绝不清**(见 §2)。后果:清掉 `marked` 后发光按自己的计时自然结束,期间可能短暂「发光但无标记」。 |

## 7. 落盘清单(全部完成)

- [x] N/F `effect/ModEffects.java`:末尾加 `public static final Collection<...> ALL = EFFECTS.getEntries();`
      (N:143-153 / F:142-152)+ `java.util.Collection` import
- [x] N/F `item/card/EffectCardPeriod.java`:加 `effectPendingEffects()`(N:109-127 / F:118-136)
- [x] N/F `command/AstralPartyCommand.java`:**新建**(N 300 行 / F 303 行)
- [x] 4 份 lang 各 +7 key(614 → **621**;`check_lang_sync.ps1` 双版本 PASS)
- [x] `CHANGELOG_ZH.md` / `CHANGELOG.md`:合并进未发布(1.2.1)的「新内容 / New Content」**各 +1 条**
      ⇒ 该节 **48 / 48**,全文件 **324 / 324**,12 个版本小节逐节相等
- [x] `AGENTS.md`:新增 `## 管理员调试命令 /astralparty（Admin Debug Commands）— 必须遵守`
      (插在 `## 版本历史与发布记录` 之前,**未改动其它任何内容**)
- [ ] 构建(N) / 构建(F) / 本地提交 —— **因客户端在跑,按任务要求全部停在构建前**

## 8. 未决点 / 建议下一步

1. **构建与提交**:等客户端退出后跑
   `pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1 --timeout 60 --retries 3`,
   再 `--version 1.20.1`;两版日志见 `BUILD SUCCESSFUL` 或产物 jar 时间戳更新后做中文信息本地提交(**不 push**)。
2. **`weak_mark_source` / `sign_ready_*` 两项清理属"从严"裁决**:若用户认为超出「同生共死」边界,
   删掉对应两个分支即可(不影响其余功能),但会留下 §6 所述的隐性状态。
3. **`glowing` 的处置已按红线删除**;若日后用户改判可恢复(证据见 §2/§6)。
