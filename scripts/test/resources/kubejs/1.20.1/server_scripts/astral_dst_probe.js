// ════════════════════════════════════════════════════════════════════════════
//  astral_dst_probe.js —— 1.20.1 (Forge/LegacyForge) 三批实机取证探针(2026-10-27)
//
//  背景:同一批三件事已在 1.21.1 用 AutoJS eval 验过(UNDERCOVER-DOUBLE / REPULSION-SIGN /
//  TEMPCARD-OWNER 三条用例全 PASS);1.20.1 线**未接入 AutoJS**(MT_ENV_AUTOJS 明确跳过),
//  故用本探针走 KubeJS 服务端脚本通道做对等复验。
//
//  取证通道 = 聊天(sendFailure) → latest.log 的 `[CHAT] AP_...` 行;用例侧一律 type:'log' 断言。
//  命令根: /astraldst <sub> <tag>   (tag = 读数前缀,大写短串)
//    ig <tag>    隐匿调查法伤击杀双罚:造 2HP pillager(隐匿调查 + 来源=自己 + stage=1 + 标记×2)
//                → 法伤一次(card_spell 1.0,内层加成致死) → 真伤一次(true_damage 5.0) 强制二次 die()
//                +80t 回 AP_<tag>_IG_DONE + AP_<tag>_IG_V:ok=1   (修好 = stage 只 +1)
//    tc <tag>    绿洲女王临时牌 owner:grantRandom 3 → 一张改成外主 → +40t 应只剩 2
//                → 内部移除 nardis_privilege(NOT /effect clear) → +60t 应为 0
//    rp <tag>    强化拒止五规则:无立牌拾卡→路障 → 装立牌发 2 卡 + 1 有主物品 + 1 有主场
//                → 卸立牌 → 应 cards=0/barricades>=1/items=0/fields=0;再验无立牌 use() 被拒
//    snap <tag>  一次性快照(三类计数 + stage)
//
//  Rhino 硬坑(照 astral_kame_probe.js / astral_bugfix_probe.js 实测结论):
//    · 一律 var;命令注册必须在 ServerEvents.commandRegistry 内;
//    · 物品比对**必须走注册名**(st.is(Item) 在 1.20.1 有 4 个同元重载 ⇒ Rhino 报 ambiguous);
//    · level.getGameTime() 成员查找不可见 ⇒ level.getLevelData().getGameTime();
//    · Entity#moveTo 多参歧义 ⇒ 只用 setPos(3 参);
//    · 读数一律「机制回显」:任一调用失败把异常单行进读数,绝不静默降级。
// ════════════════════════════════════════════════════════════════════════════

var DComponent = Java.loadClass("net.minecraft.network.chat.Component");
var DStringArg = Java.loadClass("com.mojang.brigadier.arguments.StringArgumentType");
var DItemStack = Java.loadClass("net.minecraft.world.item.ItemStack");
var KCuriosApi = Java.loadClass("top.theillusivec4.curios.api.CuriosApi");
var DMobEffectInstance = Java.loadClass("net.minecraft.world.effect.MobEffectInstance");
var DLivingEntity = Java.loadClass("net.minecraft.world.entity.LivingEntity");
var DEntityType = Java.loadClass("net.minecraft.world.entity.EntityType");
var DItemEntity = Java.loadClass("net.minecraft.world.entity.item.ItemEntity");
var DHand = Java.loadClass("net.minecraft.world.InteractionHand");
var DUUID = Java.loadClass("java.util.UUID");
var DOptional = Java.loadClass("java.util.Optional");
var DBuiltIn = Java.loadClass("net.minecraft.core.registries.BuiltInRegistries");

var _dCache = {};
function dLoad(name) {
    if (_dCache[name] !== undefined) return _dCache[name];
    var v = null;
    try { v = Java.loadClass(name); } catch (e) { v = null; }
    _dCache[name] = v;
    return v;
}
function dAtt() { return dLoad("com.merlinkitsune.astral_dice.component.ModAttachments"); }
function dFx() { return dLoad("com.merlinkitsune.astral_dice.effect.ModEffects"); }
function dDmg() { return dLoad("com.merlinkitsune.astral_dice.damage.ModDamageTypes"); }
function dTC() { return dLoad("com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil"); }
function dLSM() { return dLoad("com.merlinkitsune.astral_dice.item.sign.LinglingSignManager"); }
function dRep() { return dLoad("com.merlinkitsune.astral_dice.item.RepulsionItem"); }
function dItems() { return dLoad("com.merlinkitsune.astral_dice.item.ModItems"); }
function dEntTypes() { return dLoad("com.merlinkitsune.astral_dice.entity.ModEntityTypes"); }
function dEnt() { return dLoad("com.merlinkitsune.astral_dice.entity.RepulsionEntity"); }
function dXC() { return dLoad("com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil"); }
function dMark() { return dLoad("com.merlinkitsune.astral_dice.item.MarkManager"); }
function dMER() { return dLoad("com.merlinkitsune.starengine.event.ModEffectRemoval"); }
function dComp() { return dLoad("com.merlinkitsune.astral_dice.component.ModDataComponents"); }

/** 当前服务端(1.20.1 Rhino: tick 事件对象上取 server 不可靠 ⇒ 走 Forge 生命周期钩子) */
function dServer() {
    try { return Java.loadClass("net.minecraftforge.server.ServerLifecycleHooks").getCurrentServer(); }
    catch (e) { return null; }
}
/** 玩家 UUID(1.20.1 Rhino 里 Entity#getUUID 不可见 ⇒ 走 getStringUUID 解析) */
function dUuidOf(p) {
    try { return DUUID.fromString("" + p.getStringUUID()); }
    catch (e) {
        try { return DUUID.fromString("" + p.uuid); } catch (e2) { return null; }
    }
}
function dSend(ctx, text) {
    try { ctx.source.sendFailure(DComponent.literal(text)); } catch (e) { /* 回显失败不影响判定 */ }
}
function dEx(e) {
    var m = "" + e;
    m = m.replace(/\r?\n/g, " ");
    if (m.length > 150) m = m.substring(0, 150) + "...";
    return m;
}
/** 物品注册名(1.20.1 ItemStack#is(Item) 在 Rhino 里重载歧义 ⇒ 一律比注册名) */
function dId(st) {
    try { if (st == null || st.isEmpty()) return ""; return "" + DBuiltIn.ITEM.getKey(st.getItem()); }
    catch (e) { return "ERR"; }
}

// ── 异步阶段调度(命令执行完就返回,后续读数由 tick 驱动) ──────────────────
/** 当前刻(Rhino 下 MinecraftServer#getTickCount / Level#getGameTime 均不保证可见 ⇒ 三重回退) */
function dNowTicks() {
    var srv = dServer();
    if (srv == null) return -1;
    try { return srv.overworld().getLevelData().getGameTime(); } catch (e) { }
    try { return srv.getTickCount(); } catch (e) { }
    try { return srv.overworld().getDayTime(); } catch (e) { }
    return -1;
}
/** 以玩家为源执行一条原版命令(LivingEntity#hurt 在 Rhino 里不可见 ⇒ 伤害一律走 /damage) */
function dCmd(ctx, cmd) {
    var out = "ok";
    try {
        var p = ctx.source.getPlayerOrException();
        p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withSuppressedOutput(), cmd);
    } catch (e) { out = "ERR:" + dEx(e); }
    return out;
}

// ── 计数工具 ────────────────────────────────────────────────────────────
var D_ID_CARD = "astral_dice:effect_card_repulsion";
var D_ID_BAR = "astral_dice:effect_card_barricade";
var D_ID_ITEM = "astral_dice:repulsion";

function dTally(out, st, p) {
    var id = dId(st);
    if (id === "") return;
    var c = 1;
    try { c = st.getCount(); } catch (e) { c = 1; }
    if (id === D_ID_CARD) { out.cards = out.cards + c; return; }
    if (id === D_ID_BAR) { out.barricades = out.barricades + c; return; }
    if (id === D_ID_ITEM) {
        out.items = out.items + c;
        var o = null;
        // ExclusiveCardUtil.ownerOf 返回 java.util.Optional<UUID> ⇒ 必须 orElse(null) 解包
        try { o = dXC().ownerOf(st).orElse(null); } catch (e) { o = null; }
        if (o == null) { out.noowner = out.noowner + c; }
        else if (("" + o) === ("" + dUuidOf(p))) { out.owned = out.owned + c; }
        else { out.other = out.other + c; }
    }
}
function dCounts(p) {
    var out = { cards: 0, barricades: 0, items: 0, owned: 0, other: 0, noowner: 0 };
    try {
        var items = p.getInventory().items;
        for (var i = 0; i < items.size(); i++) dTally(out, items.get(i), p);
        var off = p.getInventory().offhand;
        for (var j = 0; j < off.size(); j++) dTally(out, off.get(j), p);
    } catch (e) { out.err = dEx(e); }
    return out;
}
function dFields(p) {
    var n = 0;
    try {
        var cls = dEnt();
        if (cls == null) return -1;
        var list = p.level.getEntitiesOfClass(cls, p.getBoundingBox().inflate(64));
        for (var i = 0; i < list.size(); i++) { if (list.get(i).isAlive()) n++; }
    } catch (e) { return -1; }
    return n;
}
function dTemp(p) {
    var out = { n: 0, me: 0, other: 0 };
    try {
        var tc = dTC();
        var items = p.getInventory().items;
        var all = [];
        for (var i = 0; i < items.size(); i++) all.push(items.get(i));
        var off = p.getInventory().offhand;
        for (var j = 0; j < off.size(); j++) all.push(off.get(j));
        for (var k = 0; k < all.length; k++) {
            var st = all[k];
            if (st == null || st.isEmpty()) continue;
            if (!tc.isTemporary(st)) continue;
            var c = 1;
            try { c = st.getCount(); } catch (e) { c = 1; }
            out.n = out.n + c;
            var o = null;
            try { o = tc.ownerOf(st); } catch (e) { o = null; }
            if (o != null && ("" + o) === ("" + dUuidOf(p))) out.me = out.me + c;
            else out.other = out.other + c;
        }
    } catch (e) { out.err = dEx(e); }
    return out;
}
function dHasSign(p) {
    try { return dLSM().hasSign(p) ? 1 : 0; } catch (e) { return -1; }
}

// ── 装配工具 ────────────────────────────────────────────────────────────
/** 写 Curios 槽(statck=null ⇒ 清空);与既有探针 kDoEquip 同法 */
function dEquip(p, slotId, stack, index) {
    try {
        var opt = KCuriosApi.getCuriosInventory(p);
        if (opt == null || !opt.isPresent()) return "no_curios";
        var h = opt.resolve().get().getStacksHandler(slotId);
        if (h == null || !h.isPresent()) return "no_slot:" + slotId;
        var st = h.get().getStacks();
        if (index >= st.getSlots()) return "slot_overflow:" + st.getSlots();
        st.setStackInSlot(index, DItemStack.EMPTY);
        if (stack != null) st.setStackInSlot(index, stack);
        return "ok";
    } catch (e) { return "ERR:" + dEx(e); }
}
function dDrop(p, stack) {
    try {
        var ie = new DItemEntity(p.level, p.getX(), p.getY() + 0.4, p.getZ(), stack);
        ie.setPickUpDelay(0);
        p.level.addFreshEntity(ie);
        return 1;
    } catch (e) { return 0; }
}
function dMakeField(p) {
    try {
        var e = dEntTypes().REPULSION.get().create(p.level);
        if (e == null) return 0;
        e.setPos(p.getX() + 3.0, p.getY(), p.getZ());
        e.setOwnerUUID(dUuidOf(p));
        p.level.addFreshEntity(e);
        return 1;
    } catch (e) { return 0; }
}
function dOwnedItem(p) {
    try {
        return dRep().createRecovered(40.0, 0, 0, dUuidOf(p), "" + p.getGameProfile().getName());
    } catch (e) { return null; }
}
/** 无立牌时 use() 的硬门控:返回 InteractionResult 串(期望 FAIL) */
function dUseGate(p) {
    try {
        var stack = dOwnedItem(p);
        if (stack == null) return "ERR:no_stack";
        p.setItemInHand(DHand.MAIN_HAND, stack);
        var r = stack.getItem().use(p.level, p, DHand.MAIN_HAND);
        if (r == null) return "null";
        return "" + r.getResult();
    } catch (e) { return "ERR:" + dEx(e); }
}

// ── ig: 隐匿调查法伤击杀双罚(第 1 步:布场 + 法伤一击) ─────────────────
function dDoIg(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var level = p.level;
    var t = null;
    try {
        var old = level.getEntitiesOfClass(DLivingEntity, p.getBoundingBox().inflate(64));
        for (var i = 0; i < old.size(); i++) {
            if (old.get(i).getTags().contains(tag + "_T")) old.get(i).discard();
        }
        t = DEntityType.PILLAGER.create(level);
        t.setPos(p.getX() + 2.5, p.getY(), p.getZ() + 0.5);
        t.setNoAi(true);
        t.addTag(tag + "_T");
        level.addFreshEntity(t);
        t.setHealth(2.0);
        t.addEffect(new DMobEffectInstance(dFx().UNDERCOVER_INVESTIGATION.get(), 2400, 0, false, false, true));
        dAtt().setUndercoverSource(t, DOptional.of(dUuidOf(p)));
        dAtt().setInvestigationStage(p, 1);
        dMark().apply(t);
        dMark().apply(t);
    } catch (e) { dSend(ctx, "AP_" + tag + "_IG:ERR:setup:" + dEx(e)); return 0; }
    // bonus 源:紫晶骰子(SpellDamageRegistry: hasCurio(AMETHYST_DICE) ⇒ bonus += rollD6 >= 1) ⇒ 内层 true_damage 致死
    var mi = dItems();
    var eq = "-";
    try { eq = dEquip(p, "dice", new DItemStack(mi.AMETHYST_DICE.get()), 0); } catch (e) { eq = "ERR:" + dEx(e); }
    var s0 = dAtt().getInvestigationStage(p);
    var r1 = dCmd(ctx, "damage @e[tag=" + tag + "_T,limit=1] 1 astral_dice:card_spell by @s");
    var hp1 = "?";
    var dead1 = -1;
    try { hp1 = "" + t.getHealth(); dead1 = t.isDeadOrDying() ? 1 : 0; } catch (e) { }
    dSend(ctx, "AP_" + tag + "_IG:stage0=" + s0 + ":dice=" + eq + ":dmg=" + r1 + ":hp=" + hp1 + ":dead=" + dead1);
    return 1;
}

/** 第 2 步:读调查阶段(事件卡 40t 延迟后才升阶 ⇒ 调用方先 wait >=3s) */
function dDoIgRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var s0 = 1;
    var s1 = -1;
    try { s1 = dAtt().getInvestigationStage(p); } catch (e) { s1 = -2; }
    var ok = (s1 === s0 + 1) ? 1 : 0;
    dSend(ctx, "AP_" + tag + "_IG_DONE:stage0=" + s0 + ":stage1=" + s1);
    dSend(ctx, "AP_" + tag + "_IG_V:ok=" + ok);
    return 1;
}

// ── tc: 绿洲女王临时牌 owner(第 1 步:发 3 张 + 篡改 1 张) ───────────────
function dDoTc(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var tc = dTC();
    // 正面控制:先让「女王特权」在位 —— 否则 TemporaryCardUtil#tick 的「无效果 ⇒ purgeAll」会把
    // 全部临时牌（含被篡改的那张）一起清空，使「他人 owner 被清」这一判据退化成恒绿。
    // 正面控制:真的把「女王特权」效果挂上 —— grantNardisPrivilege() 只发牌、不挂效果,
    // 若只调它,TemporaryCardUtil#tick 的「无效果 ⇒ purgeAll」会把全部临时牌(含被篡改那张)一起清掉,
    // 「他人 owner 被清」的判据就退化成恒绿。
    var priv = "ERR";
    try {
        p.addEffect(new DMobEffectInstance(dFx().NARDIS_PRIVILEGE.get(), 12000, 0, false, false, true));
        priv = "applied";
    } catch (e) { dSend(ctx, "AP_" + tag + "_TC:ERR:priv:" + dEx(e)); return 0; }
    var fxOn0 = 0;
    try { fxOn0 = p.hasEffect(dFx().NARDIS_PRIVILEGE.get()) ? 1 : 0; } catch (e) { fxOn0 = -1; }
    if (fxOn0 !== 1) { dSend(ctx, "AP_" + tag + "_TC:ERR:fx_not_applied:" + fxOn0); return 0; }
    var granted = -1;
    try { granted = tc.grantRandom(p, 3); } catch (e) { dSend(ctx, "AP_" + tag + "_TC:ERR:grant:" + dEx(e)); return 0; }
    var a = dTemp(p);
    var tampered = 0;
    try {
        var items = p.getInventory().items;
        for (var i = 0; i < items.size(); i++) {
            var st = items.get(i);
            if (st == null || st.isEmpty() || !tc.isTemporary(st)) continue;
            var exp = 0;
            try { exp = dComp().TEMPORARY_CARD_EXPIRES.get(st); } catch (e) { exp = 0; }
            if (exp == null) exp = 0;
            // 只篡改**一张**:临时牌可堆叠(stacksTo 16),整栈 mark 会把同栈的同伴一起换成他人 UUID,
            // 清理后会多掉一张 ⇒ 读数忽 2 忽 1。这里先拆出单张副本,再对副本 mark 并把原栈减 1。
            var single = st.copy();
            single.setCount(1);
            tc.mark(single, 6000 + exp, DUUID.randomUUID());
            if (st.getCount() > 1) { st.setCount(st.getCount() - 1); } else { st.setCount(0); }
            p.getInventory().add(single);
            tampered = 1;
            break;
        }
    } catch (e) { dSend(ctx, "AP_" + tag + "_TC:ERR:tamper:" + dEx(e)); }
    var a2 = dTemp(p);
    dSend(ctx, "AP_" + tag + "_TC_GRANT:granted=" + granted + ":n=" + a.n + ":me=" + a.me + ":other=" + a.other + ":tampered=" + tampered + ":priv=" + priv + ":fx_on0=" + fxOn0
        + ":n2=" + a2.n + ":me2=" + a2.me + ":other2=" + a2.other);
    return 1;
}

/** 第 2 步:读清理结果,然后走内部通道移除女王特权(外部 /effect clear 会被拦回) */
function dDoTcRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var b = dTemp(p);
    // 判据收紧:他人 owner 的牌被清、本人牌仍在、且仍在 >=2 张 ⇒ 排除「无特权被 purgeAll 清空」的恒绿
    var ok = (b.other === 0 && b.n === b.me && b.n >= 2) ? 1 : 0;
    dSend(ctx, "AP_" + tag + "_TC_PURGE:n=" + b.n + ":me=" + b.me + ":other=" + b.other);
    dSend(ctx, "AP_" + tag + "_TC_PURGE_V:ok=" + ok);
    var pr = "-";
    try { dMER().remove(p, dFx().NARDIS_PRIVILEGE.get()); pr = "removed"; } catch (e) { pr = "ERR:" + dEx(e); }
    var fxOn = 0;
    try { fxOn = p.hasEffect(dFx().NARDIS_PRIVILEGE.get()) ? 1 : 0; } catch (e) { fxOn = -1; }
    dSend(ctx, "AP_" + tag + "_TC_NOPRIV:res=" + pr + ":fx_on=" + fxOn);
    return 1;
}

/** 第 3 步:无特权后应清空全部临时牌 */
function dDoTcRead2(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var c = dTemp(p);
    var okNo = (c.n === 0) ? 1 : 0;
    try { if (dTC().countTemporaryTotal(p) !== 0) okNo = 0; } catch (e) { okNo = 0; }
    dSend(ctx, "AP_" + tag + "_TC_DONE:n=" + c.n + ":me=" + c.me + ":other=" + c.other);
    dSend(ctx, "AP_" + tag + "_TC_V:purge=" + (c.other === 0 ? 1 : 0) + ":nopriv=" + okNo);
    return 1;
}

// ── rp: 强化拒止五规则(第 1 步:卸立牌 + 无立牌时丢卡) ──────────────────
function dDoRp(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var mi = dItems();
    var un = "-";
    try { un = dEquip(p, "stand", null, 0); } catch (e) { un = "ERR:" + dEx(e); }
    var drop = dDrop(p, new DItemStack(mi.EFFECT_CARD_REPULSION.get()));
    dSend(ctx, "AP_" + tag + "_RP_PRE:unequip=" + un + ":hasSign=" + dHasSign(p) + ":drop=" + drop);
    return 1;
}

/** 第 2 步:读拾取结果(卡应已变路障),再装立牌 + 发卡/发有主物品/造有主场 */
function dDoRpRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var mi = dItems();
    var a = dCounts(p);
    var okPick = (a.cards === 0 && a.barricades >= 1) ? 1 : 0;
    dSend(ctx, "AP_" + tag + "_RP_PICK:cards=" + a.cards + ":barricades=" + a.barricades + ":items=" + a.items);
    dSend(ctx, "AP_" + tag + "_RP_PICK_V:ok=" + okPick);
    var eq = "-";
    try { eq = dEquip(p, "stand", new DItemStack(mi.LINGLING_SIGN.get()), 0); } catch (e) { eq = "ERR:" + dEx(e); }
    var gave = "-";
    try { dLSM().giveRepulsion(p, 2); gave = "ok"; } catch (e) { gave = "ERR:" + dEx(e); }
    var itemOk = 0;
    var st = dOwnedItem(p);
    if (st != null) { try { itemOk = p.getInventory().add(st) ? 1 : 0; } catch (e) { itemOk = 0; } }
    var fieldOk = dMakeField(p);
    dSend(ctx, "AP_" + tag + "_RP_EQUIP:eq=" + eq + ":give=" + gave + ":item=" + itemOk + ":field=" + fieldOk + ":hasSign=" + dHasSign(p));
    return 1;
}

/** 第 3 步:读绑定结果,然后主动卸立牌 */
function dDoRpRead2(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var b = dCounts(p);
    var bf = dFields(p);
    var okBound = (b.cards >= 2 && b.items >= 1 && b.owned >= 1 && bf >= 1) ? 1 : 0;
    dSend(ctx, "AP_" + tag + "_RP_BOUND:cards=" + b.cards + ":barricades=" + b.barricades + ":items=" + b.items + ":owned=" + b.owned + ":other=" + b.other + ":noowner=" + b.noowner + ":fields=" + bf);
    dSend(ctx, "AP_" + tag + "_RP_BOUND_V:ok=" + okBound);
    var un = "-";
    try { un = dEquip(p, "stand", null, 0); } catch (e) { un = "ERR:" + dEx(e); }
    dSend(ctx, "AP_" + tag + "_RP_UNEQUIP:res=" + un + ":hasSign=" + dHasSign(p));
    return 1;
}

/** 第 4 步:卸牌后卡应变路障、物品与已放置场应销毁;再验无立牌 use() 硬拒 */
function dDoRpRead3(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var c = dCounts(p);
    var cf = dFields(p);
    var gate = dUseGate(p);
    var okDone = (c.cards === 0 && c.barricades >= 1 && c.items === 0 && cf === 0) ? 1 : 0;
    var okGate = ("" + gate).indexOf("FAIL") >= 0 ? 1 : 0;
    dSend(ctx, "AP_" + tag + "_RP_DONE:cards=" + c.cards + ":barricades=" + c.barricades + ":items=" + c.items + ":fields=" + cf + ":use=" + gate);
    dSend(ctx, "AP_" + tag + "_RP_V:lost=" + okDone + ":gate=" + okGate);
    return 1;
}

function dDoSnap(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var a = dCounts(p);
    var b = dTemp(p);
    var stage = -1;
    try { stage = dAtt().getInvestigationStage(p); } catch (e) { stage = -2; }
    dSend(ctx, "AP_" + tag + "_SNAP:now=" + dNowTicks() + ":hasSign=" + dHasSign(p) + ":cards=" + a.cards + ":barricades=" + a.barricades
        + ":items=" + a.items + ":owned=" + a.owned + ":fields=" + dFields(p) + ":temp=" + b.n + ":stage=" + stage);
    return 1;
}

/** 收尾:清掉本探针留下的状态（立牌/骰子槽、调查阶段、女王特权、附近的强化拒止） */
function dDoClean(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var sign = "ERR";
    var dice = "ERR";
    var stage = "ERR";
    var priv = "ERR";
    var fields = -1;
    // dEquip 失败是**返回值**(no_curios / no_slot:<id>)而不是异常 ⇒ 必须记录返回值,否则读数恒真
    try { sign = "eq:" + dEquip(p, "stand", null, 0); } catch (e) { sign = "ERR:" + dEx(e); }
    try { dice = "eq:" + dEquip(p, "dice", null, 0); } catch (e) { dice = "ERR:" + dEx(e); }
    // 注意:ModAttachments.setInvestigationStage 是 Math.max(1, value) ⇒ 地板值恒为 1,归零不可达。
    try { dAtt().setInvestigationStage(p, 1); stage = "" + dAtt().getInvestigationStage(p); } catch (e) { stage = "ERR:" + dEx(e); }
    // remove 被外部清除护栏拦回时同样不抛 ⇒ 追加 hasEffect 读回
    try {
        dMER().remove(p, dFx().NARDIS_PRIVILEGE.get());
        var fxLeft = -1;
        try { fxLeft = p.hasEffect(dFx().NARDIS_PRIVILEGE.get()) ? 1 : 0; } catch (e2) { fxLeft = -2; }
        priv = "removed:fx=" + fxLeft;
    } catch (e) { priv = "ERR:" + dEx(e); }
    try { dTC().purgeAll(p); } catch (e) { }
    var purged = 0;
    try {
        var inv = p.getInventory();
        var items = inv.items;
        for (var i = 0; i < items.size(); i++) {
            var id = dId(items.get(i));
            if (id === D_ID_ITEM || id === D_ID_CARD || id === D_ID_BAR) { inv.setItem(i, DItemStack.EMPTY); purged++; }
        }
        var off = inv.offhand;
        for (var j = 0; j < off.size(); j++) {
            var id2 = dId(off.get(j));
            if (id2 === D_ID_ITEM || id2 === D_ID_CARD || id2 === D_ID_BAR) { inv.setItem(40, DItemStack.EMPTY); purged++; }
        }
    } catch (e) { }
    try {
        var level = p.getLevel();
        var list = level.getEntitiesOfClass(dEnt(), p.getBoundingBox().inflate(48));
        for (var i = 0; i < list.size(); i++) { try { list.get(i).discard(); } catch (e2) { } }
        fields = 0;
    } catch (e) { fields = -1; }
    dSend(ctx, "AP_" + tag + "_CLEAN:sign=" + sign + ":dice=" + dice + ":stage=" + stage + ":priv=" + priv + ":fields=" + fields + ":purged=" + purged);
    return 1;
}

ServerEvents.commandRegistry(event => {
    var C = event.commands;
    var root = C.literal("astraldst");
    var subs = [
        ["ig", dDoIg], ["igread", dDoIgRead],
        ["tc", dDoTc], ["tcread", dDoTcRead], ["tcread2", dDoTcRead2],
        ["rp", dDoRp], ["rpread", dDoRpRead], ["rpread2", dDoRpRead2], ["rpread3", dDoRpRead3],
        ["snap", dDoSnap], ["clean", dDoClean]
    ];
    for (var i = 0; i < subs.length; i++) {
        var name = subs[i][0];
        var fn = subs[i][1];
        root.then(C.literal(name).then((function (n, f) {
            return C.argument("tag", DStringArg.word()).executes(function (ctx) {
                var tag = DStringArg.getString(ctx, "tag");
                try { return f(ctx, tag); } catch (e) { dSend(ctx, "AP_" + tag + "_" + n.toUpperCase() + "_EX:" + dEx(e)); return 0; }
            });
        })(name, fn)));
    }
    event.register(root);
});
