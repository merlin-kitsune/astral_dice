// ════════════════════════════════════════════════════════════════════════════
//  astral_catchup_probe.js —— fabric-1.20.1「跨线追平批 1~8」专项取证探针
//                            （服务端，RCON 驱动；无需任何第三方模组）
//
//  ── 为什么需要它 ────────────────────────────────────────────────────────────
//   本线此前没有与三条 P0 线 `/astralprobe` 等价的通用探针（KI-E3 同族的**测试资产**缺口）：
//   追平批 1~8 的功能只做过「代码层 + 开包符号」验证，**无法实机断言**。
//   本探针覆盖那几批里**最能被机器断言**的五组事实，全部**只读或自恢复**。
//
//  ⚠️ **必须整体包在 IIFE 里（2026-10-04 实测踩坑）**：KubeJS 的各 server_scripts 共享**同一全局作用域**；
//   本目录另外两个探针（`astral_gs_probe.js` / `astral_fabric_curio_probe.js`）也定义了同名的顶层 helper
//   （`guard` / `out` / `exText`）⇒ **后加载者覆盖先加载者**，导致 `/astralcatchup` 的处理器实际调用到
//   `astral_gs_probe.js` 的 `guard(ctx, tag, fn)`，实测报 `astral_gs_probe.js#41: AP_GS_ERR: tag=Function
//   ex=TypeError: fn is not a function, it is undefined.` 且**本探针一行都不输出**。
//   ⇒ 规约：**探针脚本一律用 IIFE 包裹**（或给每个顶层名加唯一前缀），不得依赖全局名。
//   ⚠️ **探针清效果必须走库内部通道**（2026-10-04 实测）：本模组拦截 `astral_dice:` 效果的**外部**移除
//   （牛奶 / `/effect clear` / 裸 `removeEffect`），裸调会被拦下并抛 `CancellationException:
//   The call removeEffect is not cancellable` ⇒ 清效果一律用 `ModEffectRemoval.remove(player, effect)`
//   （库侧「内部通道」，正是为放行本模组自己的移除而存在）。

//  ── 无人值守的关键：Fabric API 自带 FakePlayer ─────────────────────────────
//   本仓此前引用的「Carpet 假玩家」是**手工投放**的外部依赖；而 Fabric API 的
//   `fabric-events-interaction-v0` 模块自带 `net.fabricmc.fabric.api.entity.FakePlayer`
//   （实测存在于 `.gradle/loom-cache` 的 Fabric API 模块 jar）⇒ 探针**无需任何前置**即可
//   在纯 dev 服务端里拿到一个 `ServerPlayer` 做读数。传了玩家名则用真玩家，否则用 FakePlayer。
//
//  ── 命令一览（驱动方 = ft_inject.ps1 --channel rcon）────────────────────────
//    /astralcatchup env                         只读：注册面 / 类 / 常量 / 方法可用性
//    /astralcatchup melee  [player]              近战判定：空手/剪刀/钓竿/打火石/刷子/铁剑 六态
//    /astralcatchup range  [player]              射程两段夹取：无加成 / 书页射程 / 上限夹取
//    /astralcatchup conc   [player]              隐匿：clean / apply / break 三态
//    /astralcatchup legacydur [player]           常驻时长归一：造 12e8 tick → 直调归一 → 读 -1
//    /astralcatchup effremove [player]           **Q3**：效果移除拦截契约（无效果→放行 / 有效果→拦截 /
//                                                 LoaderBus 直投事件判「是否被取消」/ 内部通道可清）
//    /astralcatchup medkit    [player]           **Q2**：登录 / 切维度**不再**触发医疗箱装备治愈
//                                                 （投递两个平台事件 → 读装备闸门与治愈点是否被动过）
//
//  ── 判据形状（机器行）──────────────────────────────────────────────────────
//    AP_CATCHUP_ENV:    rin=1 conc=1 maxrad=64 melee=1 concbreak=1 range=1 thr=1073741823
//    AP_CATCHUP_MELEE:  empty=0 shears=0 rod=0 flint=0 brush=0 sword=1
//    AP_CATCHUP_RANGE:  max=64 noeff=32 waneff=48 clamp=64 zero=0
//    AP_CATCHUP_CONC:   before=0 applied=1 broke=0
//    AP_CATCHUP_LEGACY: thr=1073741823 dur0=1200000000 gt_thr=1 ticked=24 dur_after=-1 amp=5 ext=<…> ext_kept=1
//    AP_CATCHUP_EFFREM: absent0=0 fired_absent=0 r_absent=clean absent1=0 present0=1 r_present=clean
//                       fired_present=1 kept=1 cleaned=0 cA0=<n> has_at_throw=-1
//                       （`fired_absent` = 裸 removeEffect 前后 `Remove` 事件派发次数差值；
//                         `has_at_throw` = 抛出 CancellationException 时该效果**是否已在身上**）
//                       ⚠️ 本子命令跑完后**必须**保证计时守卫无残留记录，否则读数会非确定：
//                          实测症状 = `fired_absent=1` 且 `has_at_throw=0` 且 `r_absent` 抛
//                          `CancellationException`（守卫把记录的效果重新施加回来造成的假象）。
//    AP_CATCHUP_MEDKIT: f0=3 p0=7 login=ok flags_login=3 pts_login=7 dim=ok flags_dim=3 pts_dim=7
//                       released_login=0 released_dim=0
//    AP_CATCHUP_ERR:    tag=<t> ex=<…>            （任何异常都落这一行，便于断言 absent）
//
//  ── 与产品代码的对应关系（判据锚点，改动请同步）────────────────────────────
//    melee     → combat/DiceCombatEvents#isMeleeWeaponAttack（近战黑名单四项，批 7）
//    range     → target/SelectorRangeModifiers#apply + MAX_ENHANCED_RADIUS（批 3）
//    conc      → effect/ConcealmentEffect#apply/has/breakOnAttack（批 5）
//    legacydur → event/PlayerTickEvents#normalizeLegacyInfiniteDurations（批 1）
// ════════════════════════════════════════════════════════════════════════════

(function () {
  "use strict";

  var StringArg = Java.loadClass("com.mojang.brigadier.arguments.StringArgumentType");

  var Items = Java.loadClass("net.minecraft.world.item.Items");
  var ItemStack = Java.loadClass("net.minecraft.world.item.ItemStack");
  var InteractionHand = Java.loadClass("net.minecraft.world.InteractionHand");
  var MobEffectInstance = Java.loadClass("net.minecraft.world.effect.MobEffectInstance");
  var PlayerCls = Java.loadClass("net.minecraft.world.entity.player.Player");
  var FakePlayer = Java.loadClass("net.fabricmc.fabric.api.entity.FakePlayer");

  var ModEffects = Java.loadClass("com.merlinkitsune.astral_dice.effect.ModEffects");
  var DiceCombatEvents = Java.loadClass("com.merlinkitsune.astral_dice.combat.DiceCombatEvents");
  var SelectorRangeModifiers = Java.loadClass("com.merlinkitsune.astral_dice.target.SelectorRangeModifiers");
  var ConcealmentEffect = Java.loadClass("com.merlinkitsune.astral_dice.effect.ConcealmentEffect");
  var PlayerTickEvents = Java.loadClass("com.merlinkitsune.astral_dice.event.PlayerTickEvents");
  var ModEffectRemoval = Java.loadClass("com.merlinkitsune.starenginelib.event.ModEffectRemoval");
  var EffectTimerGuard = Java.loadClass("com.merlinkitsune.astral_dice.event.EffectTimerGuard");

  // ── Q2 / Q3 验证专用（2026-10-04）────────────────────────────────────────
  //   Q3 = 「效果移除拦截」在**目标已无该效果**时不得再走「阻止移除」路径
  //        （fabric 侧该路径 = Puzzles `EventResult.INTERRUPT`，注入点不可取消时抛
  //         `CancellationException: The call removeEffect is not cancellable`）。
  //   Q2 = 「登录 / 切换维度」**不再**触发医疗箱装备治愈（可被反复重登无限刷血）。
  //   ⇒ 两者都能用「把平台事件直接投进 LoaderBus」的方式在**无人值守**下拿到判别性读数。
  var PlayerLoggedInEvent = Java.loadClass(
      "com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerEvent$PlayerLoggedInEvent");
  var PlayerChangedDimensionEvent = Java.loadClass(
      "com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerEvent$PlayerChangedDimensionEvent");
  var PlayerRespawnEvent = Java.loadClass(
      "com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerEvent$PlayerRespawnEvent");
  var LoaderBus = Java.loadClass("com.merlinkitsune.astral_dice.platform.event.LoaderBus");
  var ModAttachments = Java.loadClass("com.merlinkitsune.astral_dice.component.ModAttachments");
  var HealingManager = Java.loadClass("com.merlinkitsune.astral_dice.item.HealingManager");
  var LevelCls = Java.loadClass("net.minecraft.world.level.Level");

  function out(key, text) {
      console.info("AP_CATCHUP_" + key + ": " + text);
  }

  function exText(e) {
      var m = e == null ? "null" : ("" + e);
      return m.replace(/[\r\n]+/g, " ").substring(0, 220);
  }

  function guard(tag, fn) {
      try {
          return fn();
      } catch (e) {
          out("ERR", "tag=" + tag + " ex=" + exText(e));
          return 0;
      }
  }

  // 传了名字用真玩家；否则用 Fabric API 的 FakePlayer（**本探针无人值守的关键**）。
  function resolvePlayer(ctx, name) {
      if (name != null && name !== "") {
          var pl = ctx.source.server.getPlayerList().getPlayerByName(name);
          return pl == null ? null : pl;
      }
      return FakePlayer.get(ctx.source.server.overworld());
  }

  // ══════════════ env ══════════════
  function doEnv(ctx) {
      var parts = [];
      function probe(label, fn) {
          try { parts.push(label + "=" + fn()); } catch (e) { parts.push(label + "=ERR(" + exText(e) + ")"); }
      }
      probe("rin", function () { return ModEffects.RIN_PAGE_RANGE.get() != null ? 1 : 0; });
      probe("conc", function () { return ModEffects.CONCEALMENT.get() != null ? 1 : 0; });
      probe("maxrad", function () { return SelectorRangeModifiers.MAX_ENHANCED_RADIUS; });
      probe("melee", function () { return typeof DiceCombatEvents.isMeleeWeaponAttack === "function" ? 1 : 0; });
      probe("concbreak", function () { return typeof ConcealmentEffect.breakOnAttack === "function" ? 1 : 0; });
      probe("range", function () { return typeof SelectorRangeModifiers.apply === "function" ? 1 : 0; });
      probe("thr", function () { return EffectTimerGuard.INFINITE_THRESHOLD; });
      out("ENV", parts.join(" "));
      return 1;
  }

  // ══════════════ melee：六态主手读数 ══════════════
  function doMelee(ctx, p) {
      var cases = [
          ["empty", null], ["shears", Items.SHEARS], ["rod", Items.FISHING_ROD],
          ["flint", Items.FLINT_AND_STEEL], ["brush", Items.BRUSH], ["sword", Items.IRON_SWORD]
      ];
      var parts = [];
      for (var i = 0; i < cases.length; i++) {
          var item = cases[i][1];
          p.setItemInHand(InteractionHand.MAIN_HAND,
              item == null ? ItemStack.EMPTY : new ItemStack(item));
          parts.push(cases[i][0] + "=" + (DiceCombatEvents.isMeleeWeaponAttack(p) ? 1 : 0));
      }
      p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      out("MELEE", parts.join(" "));
      return 1;
  }

  // ══════════════ range：两段夹取 ══════════════
  function doRange(ctx, p) {
      var eff = ModEffects.RIN_PAGE_RANGE.get();
      p.removeEffect(eff);
      var noeff = SelectorRangeModifiers.apply(p, "living_page", 32.0);
      p.addEffect(new MobEffectInstance(eff, 2400, 0, false, true, true));
      var waneff = SelectorRangeModifiers.apply(p, "living_page", 32.0);
      var clamp = SelectorRangeModifiers.apply(p, "living_page", 64.0);
      var zero = SelectorRangeModifiers.apply(p, "living_page", 0.0);
      p.removeEffect(eff);
      out("RANGE", "max=" + SelectorRangeModifiers.MAX_ENHANCED_RADIUS
          + " noeff=" + noeff + " waneff=" + waneff + " clamp=" + clamp + " zero=" + zero);
      return 1;
  }

  // ══════════════ conc：隐匿三态 ══════════════
  function doConc(ctx, p) {
      ConcealmentEffect.breakOnAttack(p);
      var before = ConcealmentEffect.has(p) ? 1 : 0;
      ConcealmentEffect.apply(p, 1200);
      var applied = ConcealmentEffect.has(p) ? 1 : 0;
      ConcealmentEffect.breakOnAttack(p);
      var broke = ConcealmentEffect.has(p) ? 1 : 0;
      out("CONC", "before=" + before + " applied=" + applied + " broke=" + broke);
      return 1;
  }

  // ══════════════ legacydur：常驻时长归一（直调归一方法，无需等 tick）══════════════
  function doLegacyDur(ctx, p) {
      var eff = ModEffects.CHARGE.get();
      // ⚠️ 清效果**必须**走库内部通道：本模组拦截 `astral_dice:` 效果的**外部**移除
      // （牛奶 / `/effect clear` / 裸 removeEffect）—— 裸调会被拦下，实测还会向调用方抛
      // `CancellationException: The call removeEffect is not cancellable`。
      ModEffectRemoval.remove(p, eff);
      p.addEffect(new MobEffectInstance(eff, 1200000000, 5, false, true, true));
      var inst = p.getEffect(eff);
      var dur0 = inst == null ? -999 : inst.getDuration();
      var thr = EffectTimerGuard.INFINITE_THRESHOLD;
      // 端到端：真实 tick 驱动（归一挂在 PlayerTickEvents 的 `tickCount % 20 == 0` 之后）。
      // ⚠️ KubeJS/Rhino **不允许反射 JDK 的 Class 成员**（`getDeclaredMethod` 直接报
      //    `InternalError: Java class … has no public instance field or method named "getDeclaredMethod"`）
      //    ⇒ 不能「直调私有方法」，只能靠真 tick；FakePlayer 不进世界 tick 循环，故这里**手动**调 tick()。
      var ticked = 0;
      try { for (var i = 0; i < 24; i++) { p.tick(); ticked++; } } catch (e) { ticked = -1; }
      var inst2 = p.getEffect(eff);
      var durAfter = inst2 == null ? -999 : inst2.getDuration();
      var amp = inst2 == null ? -1 : inst2.getAmplifier();
      // 旁证：外部移除被拦截 ⇒ 效果仍在（ext=clean 表示没抛异常，ext=<…> 表示抛了什么）
      var ext = "clean";
      try { p.removeEffect(eff); } catch (e) { ext = exText(e).replace(/ /g, "_").substring(0, 60); }
      var kept = p.hasEffect(eff) ? 1 : 0;
      ModEffectRemoval.remove(p, eff);
      out("LEGACY", "thr=" + thr + " dur0=" + dur0 + " gt_thr=" + (dur0 >= thr ? 1 : 0)
          + " ticked=" + ticked + " dur_after=" + durAfter + " amp=" + amp
          + " ext=" + ext + " ext_kept=" + kept);
      return 1;
  }

  // ══════════════ effremove：Q3 效果移除拦截契约（2026-10-04）══════════════
  //   判据（判别性来自「目标**无**该效果」那一支）：
  //     absent0=0            进入判别支前确实没有该效果
  //     fired_absent=1       裸 removeEffect **把 Remove 事件派发到了桥上**
  //                          （LoaderBus 派发计数差值；=1 说明桥回调被触发过）
  //     has_at_throw=-1      未抛异常时为 -1；若抛了，该值 = **抛出时该效果是否已在身上**
  //     r_absent=clean       裸 removeEffect（效果不存在）**不抛异常**   ← Q3 修复点
  //     absent1=0            调用后仍然没有该效果（无副作用）
  //     present0=1           装上后确实有
  //     fired_present=1      同上（事件确实派发）
  //     r_present=clean      裸 removeEffect（效果存在）不抛异常
  //     kept=1               ……且**被拦截**（效果仍在）—— 拦截能力未被削弱
  //     cleaned=0            库内部通道 `ModEffectRemoval` 仍可清（契约未被破坏）
  function doEffRemove(ctx, p) {
      var eff = ModEffects.CHARGE.get();

      function tryBareRemove() {
          try { p.removeEffect(eff); return "clean"; }
          catch (e) { return exText(e).replace(/[\s]+/g, "_").substring(0, 200); }
      }
      // 派发计数：证明「目标本就没有该效果」的移除**也真的走到了桥上**。
      //   `LoaderBus#dispatchReport()` 的「已派发」段形如 `… Remove=12 …`
      //   （`MobEffectEvent$Remove` 的 `getSimpleName()` = `Remove`）。
      //   ⇒ 裸 removeEffect 前后的差值 > 0 = 桥回调真的被触发过（而不是「事件根本没派发」）。
      function removeCount() {
          try {
              var rep = LoaderBus.INSTANCE.dispatchReport();
              var m = rep.match(/(?:^| )Remove=(\d+)/);
              return m ? parseInt(m[1], 10) : -1;
          } catch (e) { return -1; }
      }
      function delta(a, b) { return (a < 0 || b < 0) ? -1 : (b - a); }

      // 基线：先让 `Remove` 至少派发过一次（否则它落在报告里是「无 =n」的未派发段）
      p.addEffect(new MobEffectInstance(eff, 1200, 0, false, true, true));
      ModEffectRemoval.remove(p, eff);
      // ⚠️ 必须顺手**清掉计时守卫的记录**（2026-10-04 实测踩坑）：上面那次 addEffect 会经
      //    `ModEffectEvents.onEffectTimerRecord` 在 `EffectTimerGuard` 里留下一条 CHARGE 计时记录；
      //    若记录残留，守卫在后续 tick 见到「有记录、无效果」就会**把效果重新施加回来**
      //    （`EffectTimerGuard#tick` 的 `inst == null` 分支）⇒ 目标身上会**凭空出现** CHARGE，
      //    让「效果不存在」这一支的读数变得非确定（实测：间隔数十秒再跑时会命中该支）。
      try { EffectTimerGuard.forget(p, "astral_dice:charge"); } catch (e) { }

      // ① 目标身上**没有**该效果（Q3 的判别支）
      var absent0 = p.hasEffect(eff) ? 1 : 0;
      var cA0 = removeCount();
      var rAbsent = "clean";
      var hasAtThrow = -1;
      try {
          p.removeEffect(eff);
      } catch (e) {
          rAbsent = exText(e).replace(/[\s]+/g, "_").substring(0, 200);
          // ⚠️ 关键诊断：抛出时该效果**是不是已经在身上**。
          //    若 =1 ⇒ 说明这次移除根本不是「无效移除」，而是**有该效果**时被拦截
          //    （Puzzles 的 `cir.setReturnValue(false)` → Mixin `CallbackInfo.cancel()`
          //     在该注入不可取消时抛 `CancellationException`）。
          try { hasAtThrow = p.hasEffect(eff) ? 1 : 0; } catch (e2) { hasAtThrow = -2; }
      }
      var cA1 = removeCount();
      var absent1 = p.hasEffect(eff) ? 1 : 0;
      var firedAbsent = delta(cA0, cA1);

      // ② 目标身上**有**该效果（对照组：拦截能力必须仍在）
      p.addEffect(new MobEffectInstance(eff, 1200, 0, false, true, true));
      var present0 = p.hasEffect(eff) ? 1 : 0;
      var cP0 = removeCount();
      var rPresent = tryBareRemove();
      var cP1 = removeCount();
      var kept = p.hasEffect(eff) ? 1 : 0;
      var firedPresent = delta(cP0, cP1);

      // ③ 库内部通道仍可清（契约）
      ModEffectRemoval.remove(p, eff);
      var cleaned = p.hasEffect(eff) ? 1 : 0;

      out("EFFREM", "absent0=" + absent0 + " fired_absent=" + firedAbsent + " r_absent=" + rAbsent
          + " absent1=" + absent1 + " present0=" + present0 + " r_present=" + rPresent
          + " fired_present=" + firedPresent + " kept=" + kept + " cleaned=" + cleaned
          + " cA0=" + cA0 + " has_at_throw=" + hasAtThrow);
      return 1;
  }

  // ══════════════ medkit：Q2 登录/切维度不再触发医疗箱治愈（2026-10-04）══════
  //   判别性：`HealingManager.refreshMedkitEquipSession`（释放装备触发闸门）**只**被
  //   登录 / 切维度处理器调用（`clear()` 另有一处）。修复前：这两个事件一投递，闸门就被
  //   释放（`flags_login=0`），下一拍 Curios 重放 onEquip ⇒ `triggerMedkitOnEquip` 申领成功
  //   ⇒ **又加一次治愈点并按层数×2 回血** ⇒ 反复重登 / 过门 = 无限刷血。
  //   修复后：两个事件都不再触碰闸门与治愈点。
  //     判据：released_login=0 且 released_dim=0，且 pts_login==p0、pts_dim==p0。
  //   ⚠️ 本探针在 Fabric API 的 FakePlayer 上跑，**无法伪造 Curios 装备**（筹码栏需要先佩戴骰子，
  //      且 `findFirstCurio` 依赖饰品后端）⇒ 这里断言的是**回归被删掉的那两个调用**本身，
  //      而不是「装了医疗箱之后回血数值」。后者属人工实机项。
  function doMedkit(ctx, p) {
      var F_E = HealingManager.GRANT_BIT_MEDKIT_EMERGENCY;
      var F_C = HealingManager.GRANT_BIT_MEDKIT_COMPLETE;
      var ALL = F_E | F_C;
      function flags() { return ModAttachments.getMedkitEquipGrantFlags(p); }
      function pts() { return ModAttachments.getHealingPoints(p); }
      function post(ev) {
          try { LoaderBus.INSTANCE.post(ev); return "ok"; }
          catch (e) { return exText(e).replace(/[\s]+/g, "_").substring(0, 70); }
      }

      // 造一个「装备会话进行中」的状态：闸门已申领、治愈点 = 7
      ModAttachments.setMedkitEquipGrantAmounts(p, 0);
      ModAttachments.setHealingPoints(p, 7);
      ModAttachments.setMedkitEquipGrantFlags(p, ALL);
      var f0 = flags();
      var p0 = pts();

      // ① 登录事件
      var rLogin = post(new PlayerLoggedInEvent(p));
      var fLogin = flags();
      var pLogin = pts();

      // ② 切换维度事件（ResourceKey<Level> 取原版常量）
      var rDim = post(new PlayerChangedDimensionEvent(p, LevelCls.OVERWORLD, LevelCls.NETHER));
      var fDim = flags();
      var pDim = pts();

      out("MEDKIT", "f0=" + f0 + " p0=" + p0
          + " login=" + rLogin + " flags_login=" + fLogin + " pts_login=" + pLogin
          + " dim=" + rDim + " flags_dim=" + fDim + " pts_dim=" + pDim
          + " released_login=" + (fLogin !== f0 ? 1 : 0)
          + " released_dim=" + (fDim !== f0 ? 1 : 0));

      // 复位（只读原则的落点：不把测试状态留给世界）
      ModAttachments.setHealingPoints(p, 0);
      ModAttachments.setMedkitEquipGrantFlags(p, 0);
      ModAttachments.setMedkitEquipGrantAmounts(p, 0);
      return 1;
  }

  ServerEvents.commandRegistry(event => {
      const { commands: Commands } = event;
      event.register(
          Commands.literal("astralcatchup")
              .then(Commands.literal("env")
                  .executes(ctx => guard("env", function () { return doEnv(ctx); })))
              .then(Commands.literal("melee")
                  .executes(ctx => guard("melee", function () { return doMelee(ctx, resolvePlayer(ctx, "")); }))
                  .then(Commands.argument("player", StringArg.word())
                      .executes(ctx => guard("melee", function () {
                          var p = resolvePlayer(ctx, StringArg.getString(ctx, "player"));
                          if (p == null) { out("MISS", "melee player not found"); return 0; }
                          return doMelee(ctx, p);
                      }))))
              .then(Commands.literal("range")
                  .executes(ctx => guard("range", function () { return doRange(ctx, resolvePlayer(ctx, "")); }))
                  .then(Commands.argument("player", StringArg.word())
                      .executes(ctx => guard("range", function () {
                          var p = resolvePlayer(ctx, StringArg.getString(ctx, "player"));
                          if (p == null) { out("MISS", "range player not found"); return 0; }
                          return doRange(ctx, p);
                      }))))
              .then(Commands.literal("conc")
                  .executes(ctx => guard("conc", function () { return doConc(ctx, resolvePlayer(ctx, "")); }))
                  .then(Commands.argument("player", StringArg.word())
                      .executes(ctx => guard("conc", function () {
                          var p = resolvePlayer(ctx, StringArg.getString(ctx, "player"));
                          if (p == null) { out("MISS", "conc player not found"); return 0; }
                          return doConc(ctx, p);
                      }))))
              .then(Commands.literal("legacydur")
                  .executes(ctx => guard("legacydur", function () {
                      return doLegacyDur(ctx, resolvePlayer(ctx, ""));
                  }))
                  .then(Commands.argument("player", StringArg.word())
                      .executes(ctx => guard("legacydur", function () {
                          var p = resolvePlayer(ctx, StringArg.getString(ctx, "player"));
                          if (p == null) { out("MISS", "legacydur player not found"); return 0; }
                          return doLegacyDur(ctx, p);
                      }))))
              .then(Commands.literal("effremove")
                  .executes(ctx => guard("effremove", function () { return doEffRemove(ctx, resolvePlayer(ctx, "")); }))
                  .then(Commands.argument("player", StringArg.word())
                      .executes(ctx => guard("effremove", function () {
                          var p = resolvePlayer(ctx, StringArg.getString(ctx, "player"));
                          if (p == null) { out("MISS", "effremove player not found"); return 0; }
                          return doEffRemove(ctx, p);
                      }))))
              .then(Commands.literal("medkit")
                  .executes(ctx => guard("medkit", function () { return doMedkit(ctx, resolvePlayer(ctx, "")); }))
                  .then(Commands.argument("player", StringArg.word())
                      .executes(ctx => guard("medkit", function () {
                          var p = resolvePlayer(ctx, StringArg.getString(ctx, "player"));
                          if (p == null) { out("MISS", "medkit player not found"); return 0; }
                          return doMedkit(ctx, p);
                      }))))
      );
  });
})();
