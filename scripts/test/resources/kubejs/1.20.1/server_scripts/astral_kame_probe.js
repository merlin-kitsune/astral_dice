// ════════════════════════════════════════════════════════════════════════════
//  astral_kame_probe.js —— 1.20.1 (Forge/LegacyForge) 超天酱(kangel)/地雷女(ame)立牌批(.128)实机探针
//
//  取证通道 = 聊天(sendFailure / sendSystemMessage) → latest.log 的 `[CHAT] AP_...` 行。
//  命令根:/astralkame
//    selftest <tag>                       类/方法可见性自检(一次跑完全部读写入口)
//    state <tag> [who]                    玩家状态读数(缺省=全部在线玩家,每人一行)
//    mobs <tag> [radius]                  附近生物读数(类型/id/hp/kangel_fan 标记)
//    give <itemId> <who>                  给物品(背包)
//    equip <slotId> <itemId|-> [who]      写饰品槽(stand/dice/chip; `-` = 清空)
//    skill <who>                          生产入口 BaseSignItem.performSkillForCurio(与按键同路)
//    set <field> <value> [who]            测试脚手架:fans|love|cap|hp|clearcd|cdend
//    smob <tag> [typeId]                  生成持久 noAI 非亡灵靶(默认 pillager),回显 id
//    hit <who> <mobId> <tag>              玩家真实近战命中该 mob(Player#attack)
//    dmghit <mobId> <who> <amount> <tag>  mob 对该玩家施加一次伤害(amount<=0 ⇒ 用 mob 自身近战)
//    ovd <who> <ticks> [amp] <tag>        直接施加 ame_overdose(仅供「自然到期结算」取时)
//    fx <who> <tag>                       效果明细(逐条 id/amp/dur/showIcon/visible)
//
//  Rhino 硬坑(照既有探针实测结论):
//    · 顶层/函数内一律用 var;命令注册必须在 ServerEvents.commandRegistry 内;
//    · 所有 id 参数用 StringArgumentType.string()(word() 会吞冒号);
//    · level.getGameTime() 直接成员查找不可见 ⇒ 用 level.getLevelData().getGameTime();
//    · Entity#moveTo 多参歧义 ⇒ 只用 setPos(3 参);
//    · 读数一律「机制回显」:任一调用失败把异常单行打进读数,绝不静默降级。
// ════════════════════════════════════════════════════════════════════════════

var KComponent = Java.loadClass("net.minecraft.network.chat.Component");
var KStringArg = Java.loadClass("com.mojang.brigadier.arguments.StringArgumentType");
var KIntegerArg = Java.loadClass("com.mojang.brigadier.arguments.IntegerArgumentType");
var KBuiltIn = Java.loadClass("net.minecraft.core.registries.BuiltInRegistries");
var KRL = Java.loadClass("net.minecraft.resources.ResourceLocation");
var KItemStack = Java.loadClass("net.minecraft.world.item.ItemStack");
var KCuriosApi = Java.loadClass("top.theillusivec4.curios.api.CuriosApi");
var KMobEffectInstance = Java.loadClass("net.minecraft.world.effect.MobEffectInstance");
var KAABB = Java.loadClass("net.minecraft.world.phys.AABB");
var KLivingEntity = Java.loadClass("net.minecraft.world.entity.LivingEntity");

var _kCache = {};
// smob 生成的靶按**槽位**登记(1 基):用例 JSON 是静态的,拿不到运行时实体 id ⇒ 用槽位引用
var kMobs = [];
function kLoad(name) {
    if (_kCache[name] !== undefined) return _kCache[name];
    var v = null;
    try { v = Java.loadClass(name); } catch (e) { v = null; }
    _kCache[name] = v;
    return v;
}
function kSign() { return kLoad("com.merlinkitsune.astral_dice.item.sign.KangelSignItem"); }
function kAme() { return kLoad("com.merlinkitsune.astral_dice.item.sign.AmeSignItem"); }
function kBase() { return kLoad("com.merlinkitsune.astral_dice.item.sign.BaseSignItem"); }
function kAtt() { return kLoad("com.merlinkitsune.astral_dice.component.ModAttachments"); }
function kFx() { return kLoad("com.merlinkitsune.astral_dice.effect.ModEffects"); }
function kDcm() { return kLoad("com.merlinkitsune.astral_dice.combat.DiceCombatModifiers"); }
function kParty() { return kLoad("com.merlinkitsune.astral_dice.combat.PartyRelations"); }
function kEcon() { return kLoad("com.merlinkitsune.starenginelib.economy.StarEngineEconomy"); }
function kAttrs() { return kLoad("net.minecraft.world.entity.ai.attributes.Attributes"); }

function kSend(ctx, text) {
    try { ctx.source.sendFailure(KComponent.literal(text)); } catch (e) { /* 回显失败不影响判定 */ }
}
function kEx(e) {
    var m;
    try { m = "" + e; } catch (x) { m = "<unprintable>"; }
    m = ("" + m).replace(/\r?\n/g, " ");
    return (m.length > 140) ? (m.substring(0, 140) + "…") : m;
}
/** 每个读取点独立兜底:失败把 ERR:<异常> 写进字段(绝不静默) */
function kRd(fn, fallback) {
    try {
        var v = fn();
        if (v === null || v === undefined) return (fallback === undefined ? "null" : fallback);
        return "" + v;
    } catch (e) { return "ERR:" + kEx(e); }
}
function kGuard(ctx, tag, fn) {
    try { return fn(); } catch (e) { kSend(ctx, "AP_" + tag + "_EX:" + kEx(e)); return 0; }
}
function kTick(p) {
    try { return p.level.getLevelData().getGameTime(); } catch (e) { return -1; }
}
function kPlayers(ctx) {
    var out = [];
    try {
        var list = ctx.source.getServer().getPlayerList().getPlayers();
        var it = list.iterator();
        while (it.hasNext()) out.push(it.next());
    } catch (e) { /* 由调用方判空 */ }
    return out;
}
function kFind(ctx, name) {
    if (name === null || name === undefined) return null;
    var n = "" + name;
    if (n === "@s" || n === "self" || n === "me") {
        try { return ctx.source.getPlayerOrException(); } catch (e) { return null; }
    }
    var ps = kPlayers(ctx);
    var lower = n.toLowerCase();
    for (var i = 0; i < ps.length; i++) {
        var p = ps[i];
        var nm = kRd(function () { return p.getName().getString(); }, "").toLowerCase();
        if (nm === lower) return p;
        if (kRd(function () { return p.getScoreboardName(); }, "").toLowerCase() === lower) return p;
    }
    return null;
}
function kItemId(stack) {
    if (stack === null || stack.isEmpty()) return "-";
    try { return KBuiltIn.ITEM.getKey(stack.getItem()).toString(); } catch (e) { return "ERR"; }
}
function kSlotItem(p, slotId) {
    try {
        var opt = KCuriosApi.getCuriosInventory(p);
        if (opt == null || !opt.isPresent()) return "no_curios";
        var h = opt.resolve().get().getStacksHandler(slotId);
        if (h == null || !h.isPresent()) return "no_slot";
        var st = h.get().getStacks();
        if (st.getSlots() <= 0) return "empty_slot";
        return kItemId(st.getStackInSlot(0));
    } catch (e) { return "ERR:" + kEx(e); }
}
function kFxId(instance) {
    try { return instance.getDescriptionId(); } catch (e) { return "ERR"; }
}
function kFxText(p, effect) {
    try {
        var inst = p.getEffect(effect);
        if (inst === null) return "none";
        return "amp=" + inst.getAmplifier() + ";dur=" + inst.getDuration()
            + ";icon=" + (inst.showIcon() ? 1 : 0) + ";vis=" + (inst.isVisible() ? 1 : 0);
    } catch (e) { return "ERR:" + kEx(e); }
}
function kModAmount(p, attrId, modPath) {
    // 1.20.1:修饰器按 UUID 记账(无 ResourceLocation id)⇒ 不按 id 取,改按名字子串尽力匹配;绝不抛异常
    try {
        var attr = p.getAttribute(attrId);
        if (attr === null) return "noattr";
        var it = attr.getModifiers().iterator();
        while (it.hasNext()) {
            var m = it.next();
            var nm = "";
            try { nm = "" + m.getName(); } catch (e1) { nm = ""; }
            if (nm.indexOf(modPath) >= 0) return "" + m.getAmount();
        }
        return "none";
    } catch (e) { return "ERR:" + kEx(e); }
}

// ── 状态读数 ──────────────────────────────────────────────────────────────
function kStateLine(p, tag) {
    var t = kTick(p);
    try { return kStateLineBody(p, tag, t); } catch (e0) { return "AP_" + tag + "_ST:ERRBODY:" + kEx(e0); }
}
function kStateLineBody(p, tag, t) {
    var fx = kFx();
    var out = "AP_" + tag + "_ST:" + kRd(function () { return p.getName().getString(); }, "?");
    out += ":gm=" + kRd(function () { return p.isCreative() ? "c" : "s"; }, "?");
    out += ":team=" + kRd(function () { var t2 = kParty(); return (t2 && t2.hasTeam(p)) ? "y" : "n"; }, "ERR");
    out += ":tm=" + kRd(function () { var t3 = kParty(); return t3.collectTeamPlayers(p).size(); }, "ERR");
    out += ":dice=" + kSlotItem(p, "dice");
    out += ":sign=" + kSlotItem(p, "stand");
    out += ":fans=" + kRd(function () { return kSign().getFanCount(p); }, "ERR");
    out += ":ftg=" + kRd(function () {
        var raw = kAtt().getKangelFanTargets(p);
        if (raw === null || raw === "") return 0;
        return raw.split(",").length;
    }, "ERR");
    out += ":love=" + kRd(function () { return kAme().getLove(p); }, "ERR");
    out += ":cap=" + kRd(function () { return kAme().getLoveCap(p); }, "ERR");
    out += ":cdend=" + kRd(function () { return kAtt().getAmeLoveGainCdEnd(p); }, "ERR");
    out += ":hp=" + kRd(function () { return p.getHealth(); }, "ERR")
        + "/" + kRd(function () { return p.getMaxHealth(); }, "ERR");
    out += ":arm=" + kRd(function () { var A = kAttrs(); return p.getAttributeValue(A.ARMOR); }, "ERR");
    out += ":modL=" + kModAmount(p, kAttrs().ARMOR, "ame_love");
    out += ":modK=" + kModAmount(p, kAttrs().ARMOR, "ame_link");
    out += ":atk=" + kRd(function () { return kDcm().attackPowerOf(p); }, "ERR");
    out += ":atkd=" + kRd(function () { return kDcm().attackPowerDisplayOf(p); }, "ERR");
    out += ":def=" + kRd(function () { return kDcm().defensePowerOf(p); }, "ERR");
    out += ":fanE=" + kFxText(p, fx.KANGEL_FAN);
    out += ":loveE=" + kFxText(p, fx.AME_LOVE);
    out += ":ovdE=" + kFxText(p, fx.AME_OVERDOSE);
    out += ":lock=" + kRd(function () { return kBase().isSignActiveLocked(p) ? 1 : 0; }, "ERR");
    out += ":sco=" + kRd(function () { return kAtt().getSignActiveCooldownEnd(p); }, "ERR");
    out += ":spd=" + kRd(function () { var A2 = kAttrs(); return p.getAttributeValue(A2.MOVEMENT_SPEED); }, "ERR");
    out += ":spdm=" + kModAmount(p, kAttrs().MOVEMENT_SPEED, "ame_overdose_speed");
    out += ":hand=" + kRd(function () { return kItemId(p.getMainHandItem()); }, "ERR");
    out += ":off=" + kRd(function () { return kItemId(p.getOffhandItem()); }, "ERR");
    out += ":coin=" + kRd(function () { var E = kEcon(); return (E && E.isAvailable()) ? E.getBalance(p) : "no_econ"; }, "ERR");
    out += ":carry=" + kRd(function () {
        return kLoad("com.merlinkitsune.astral_dice.economy.StarCoinCurrency").countCarriedValue(p);
    }, "ERR");
    out += ":tick=" + t;
    return out;
}
function kDoState(ctx, tag, who) {
    var targets = [];
    if (who !== null && who !== undefined && "" + who !== "@a") {
        var one = kFind(ctx, who);
        if (one === null) { kSend(ctx, "AP_" + tag + "_NOPLAYER:" + who); return 0; }
        targets.push(one);
    } else {
        targets = kPlayers(ctx);
    }
    for (var i = 0; i < targets.length; i++) kSend(ctx, kStateLine(targets[i], tag));
    kSend(ctx, "AP_" + tag + "_STN:count=" + targets.length);
    return 1;
}
function kDoMobs(ctx, tag, radius) {
    var p = ctx.source.getPlayerOrException();
    var r = (radius === null || radius === undefined) ? 16 : radius;
    var list = p.level.getEntitiesOfClass(KLivingEntity, KAABB.ofSize(p.position(), r * 2, r * 2, r * 2));
    var n = 0;
    for (var i = 0; i < list.size(); i++) {
        var e = list.get(i);
        var type = kRd(function () { return KBuiltIn.ENTITY_TYPE.getKey(e.getType()).toString(); }, "?");
        var fan = "none";
        try {
            var inst = e.getEffect(kFx().KANGEL_FAN.get());
            if (inst !== null) fan = "amp=" + inst.getAmplifier() + ";dur=" + inst.getDuration();
        } catch (ex) { fan = "ERR:" + kEx(ex); }
        kSend(ctx, "AP_" + tag + "_MOB:id=" + kRd(function () { return e.getId(); }, "?")
            + ":type=" + type + ":hp=" + kRd(function () { return e.getHealth(); }, "?")
            + ":fan=" + fan);
        n++;
    }
    kSend(ctx, "AP_" + tag + "_MOBN:count=" + n + ":radius=" + r);
    return 1;
}
function kMobAt(slot) {
    if (slot < 1 || slot > kMobs.length) return null;
    var m = kMobs[slot - 1];
    if (m === null || m === undefined) return null;
    try { if (!m.isAlive()) return null; } catch (e) { /* 读不到存活就按可用处理 */ }
    return m;
}

function kFindMobById(p, id, radius) {
    var r = (radius === null || radius === undefined) ? 48 : radius;
    var list = p.level.getEntitiesOfClass(KLivingEntity, KAABB.ofSize(p.position(), r * 2, r * 2, r * 2));
    for (var i = 0; i < list.size(); i++) if (list.get(i).getId() === id) return list.get(i);
    return null;
}
function kDoSmob(ctx, tag, typeId) {
    var p = ctx.source.getPlayerOrException();
    var tid = (typeId === null || typeId === undefined || typeId === "") ? "minecraft:pillager" : "" + typeId;
    var type = KBuiltIn.ENTITY_TYPE.get(new KRL(tid));
    if (type === null) { kSend(ctx, "AP_" + tag + "_SMOB:ERR:unknown_type:" + tid); return 0; }
    var resolved = kRd(function () { return KBuiltIn.ENTITY_TYPE.getKey(type).toString(); }, "?");
    var mob = type.create(p.level);
    if (mob === null) { kSend(ctx, "AP_" + tag + "_SMOB:ERR:create_null"); return 0; }
    try { p.setYRot(0.0); p.setXRot(0.0); } catch (e1) { /* 忽略 */ }
    try { p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0.0, 0.0); } catch (e2) { /* 忽略 */ }
    try { mob.setPos(p.getX() + 0.0, p.getY() + 0.0, p.getZ() + 3.0); } catch (e3) { /* 忽略 */ }
    try { mob.setNoAi(true); } catch (e4) { /* 忽略 */ }
    try { mob.setPersistenceRequired(); } catch (e5) { /* 忽略 */ }
    p.level.addFreshEntity(mob);
    kMobs.push(mob);
    var hostile = kRd(function () { return kParty().isHostileTo(p, mob) ? 1 : 0; }, "ERR");
    kSend(ctx, "AP_" + tag + "_SMOB:slot=" + kMobs.length + ":id=" + kRd(function () { return mob.getId(); }, "?")
        + ":type=" + tid + ":resolved=" + resolved + ":hostile=" + hostile
        + ":tp=" + kRd(function () { return mob.getX() + "," + mob.getY() + "," + mob.getZ(); }, "?"));
    return 1;
}
/** 玩家真实近战命中(enemy target = mob) */
function kDoHit(ctx, tag, who, mobId) {
    var p = kFind(ctx, who);
    if (p === null) { kSend(ctx, "AP_" + tag + "_HIT:ERR:no_player:" + who); return 0; }
    var mob = kMobAt(mobId);
    if (mob === null) { kSend(ctx, "AP_" + tag + "_HIT:ERR:no_mob_slot:" + mobId + ":have=" + kMobs.length); return 0; }
    var h0 = kRd(function () { return mob.getHealth(); }, "-1");
    var api = "none", err = "-";
    try { p.attack(mob); api = "player_attack"; }
    catch (e1) {
        try { p.doHurtTarget(mob); api = "doHurtTarget"; }
        catch (e2) { api = "ERR"; err = kEx(e2); }
    }
    var h1 = kRd(function () { return mob.getHealth(); }, "-1");
    var fan = "none";
    try {
        var inst = mob.getEffect(kFx().KANGEL_FAN.get());
        if (inst !== null) fan = "amp=" + inst.getAmplifier() + ";dur=" + inst.getDuration();
    } catch (ex) { fan = "ERR:" + kEx(ex); }
    kSend(ctx, "AP_" + tag + "_HIT:who=" + who + ":slot=" + mobId + ":api=" + api
        + ":h0=" + h0 + ":h1=" + h1 + ":dealt=" + ((h0 === "-1" || h1 === "-1") ? -1 : ("" + (h0 - h1)))
        + ":fan=" + fan + ":err=" + err);
    return 1;
}
/** mob 对玩家施加一次伤害 */
function kDoDmgHit(ctx, tag, mobId, who, amount) {
    var p = kFind(ctx, who);
    if (p === null) { kSend(ctx, "AP_" + tag + "_DMG:ERR:no_player:" + who); return 0; }
    var mob = kMobAt(mobId);
    if (mob === null) { kSend(ctx, "AP_" + tag + "_DMG:ERR:no_mob_slot:" + mobId + ":have=" + kMobs.length); return 0; }
    try { p.invulnerableTime = 0; } catch (e0) { /* 忽略 */ }
    var h0 = kRd(function () { return p.getHealth(); }, "-1");
    var src = null;
    try { src = p.level.damageSources().mobAttack(mob); } catch (e1) { src = null; }
    var api = "none", err = "-";
    if (src !== null && amount > 0) {
        try { p.damage(amount, src); api = "damage"; }
        catch (e2) {
            try { p.hurt(src, amount); api = "hurt"; }
            catch (e3) { api = "ERR"; err = kEx(e3); }
        }
    }
    if (api !== "damage" && api !== "hurt") {
        try { mob.doHurtTarget(p); api = (api === "none" ? "doHurtTarget" : api + "+doHurtTarget"); }
        catch (e4) { err = (err === "-" ? "" : err + " ") + kEx(e4); api = (api === "none") ? "ERR" : api; }
    }
    var h1 = kRd(function () { return p.getHealth(); }, "-1");
    var fanOnMob = "none";
    try {
        var inst = mob.getEffect(kFx().KANGEL_FAN.get());
        if (inst !== null) fanOnMob = "amp=" + inst.getAmplifier();
    } catch (ex) { fanOnMob = "ERR:" + kEx(ex); }
    kSend(ctx, "AP_" + tag + "_DMG:who=" + who + ":slot=" + mobId + ":api=" + api
        + ":amt=" + amount + ":h0=" + h0 + ":h1=" + h1
        + ":dealt=" + ((h0 === "-1" || h1 === "-1") ? -1 : ("" + (h0 - h1)))
        + ":mobfan=" + fanOnMob
        + ":hostileToVictim=" + kRd(function () { return kParty().isHostileTo(p, mob) ? 1 : 0; }, "ERR")
        + ":err=" + err);
    return 1;
}
function kDoFx(ctx, tag, who) {
    var p = kFind(ctx, who);
    if (p === null) { kSend(ctx, "AP_" + tag + "_FX:ERR:no_player:" + who); return 0; }
    var map = null;
    try { map = p.getActiveEffectsMap(); } catch (e) { kSend(ctx, "AP_" + tag + "_FX:ERR:" + kEx(e)); return 0; }
    var n = 0;
    var it = map.values().iterator();
    while (it.hasNext()) {
        var inst = it.next();
        if (inst === null) continue;
        kSend(ctx, "AP_" + tag + "_FX1:id=" + kFxId(inst)
            + ":amp=" + kRd(function () { return inst.getAmplifier(); }, "?")
            + ":dur=" + kRd(function () { return inst.getDuration(); }, "?")
            + ":icon=" + kRd(function () { return inst.showIcon() ? 1 : 0; }, "?")
            + ":vis=" + kRd(function () { return inst.isVisible() ? 1 : 0; }, "?"));
        n++;
    }
    kSend(ctx, "AP_" + tag + "_FX:who=" + who + ":n=" + n);
    return 1;
}
function kDoGive(ctx, tag, itemId, who) {
    var p = kFind(ctx, who);
    if (p === null) { kSend(ctx, "AP_" + tag + "_GIVE:ERR:no_player:" + who); return 0; }
    var item = KBuiltIn.ITEM.get(new KRL(itemId));
    if (item === null || kItemId(new KItemStack(item)) !== itemId) {
        kSend(ctx, "AP_" + tag + "_GIVE:ERR:unknown_item:" + itemId); return 0;
    }
    var ok = "?";
    try { ok = p.getInventory().add(new KItemStack(item)) ? 1 : 0; } catch (e) { ok = "ERR:" + kEx(e); }
    kSend(ctx, "AP_" + tag + "_GIVE:who=" + who + ":item=" + itemId + ":ok=" + ok);
    return 1;
}
function kDoEquip(ctx, tag, slotId, itemId, who, index) {
    var p = kFind(ctx, who);
    if (p === null) { kSend(ctx, "AP_" + tag + "_EQ:ERR:no_player:" + who); return 0; }
    var stack = KItemStack.EMPTY;
    var clearing = (itemId === "-" || itemId === "empty" || itemId === "air");
    if (!clearing) {
        var item = KBuiltIn.ITEM.get(new KRL(itemId));
        if (item === null || kItemId(new KItemStack(item)) !== itemId) {
            kSend(ctx, "AP_" + tag + "_EQ:ERR:unknown_item:" + itemId); return 0;
        }
        stack = new KItemStack(item);
    }
    var res = "-";
    try {
        var opt = KCuriosApi.getCuriosInventory(p);
        if (opt == null || !opt.isPresent()) res = "no_curios";
        else {
            var h = opt.resolve().get().getStacksHandler(slotId);
            if (h == null || !h.isPresent()) res = "no_slot";
            else {
                var st = h.get().getStacks();
                if (index >= st.getSlots()) res = "slot_overflow:" + st.getSlots();
                else {
                    st.setStackInSlot(index, KItemStack.EMPTY);
                    if (!clearing) st.setStackInSlot(index, stack);
                    res = "ok";
                }
            }
        }
    } catch (e) { res = "ERR:" + kEx(e); }
    kSend(ctx, "AP_" + tag + "_EQ:who=" + who + ":slot=" + slotId + ":item=" + (clearing ? "-" : itemId)
        + ":res=" + res + ":now=" + kSlotItem(p, slotId));
    return 1;
}
function kDoSkill(ctx, tag, who) {
    var p = kFind(ctx, who);
    if (p === null) { kSend(ctx, "AP_" + tag + "_SKILL:ERR:no_player:" + who); return 0; }
    var S = kBase();
    if (S === null) { kSend(ctx, "AP_" + tag + "_SKILL:ERR:no_class"); return 0; }
    var err = "-";
    try { S.performSkillForCurio(p); } catch (e) { err = kEx(e); }
    kSend(ctx, "AP_" + tag + "_SKILL:who=" + who + ":err=" + err + ":" + kStateLine(p, tag + "_AFT").substring(("AP_" + tag + "_AFT_ST:").length));
    return 1;
}
/** 清空粉丝计数 + 已登记目标集 + 指示器(用例可重复执行所必需:set fans 0 只清计数,不清目标集) */
function kDoResetFans(ctx, tag, who) {
    var p = kFind(ctx, who);
    if (p === null) { kSend(ctx, "AP_" + tag + "_RF:ERR:no_player:" + who); return 0; }
    var res = "-";
    try { kSign().setFanCount(p, 0); kSign().clearFanTargets(p); res = "ok"; }
    catch (e) { res = "ERR:" + kEx(e); }
    kSend(ctx, "AP_" + tag + "_RF:who=" + who + ":res=" + res
        + ":fans=" + kRd(function () { return kSign().getFanCount(p); }, "ERR")
        + ":ftg=" + kRd(function () { var raw = kAtt().getKangelFanTargets(p); return (raw === null || raw === "") ? 0 : raw.split(",").length; }, "ERR"));
    return 1;
}

function kDoSet(ctx, tag, field, value, who) {
    var p = kFind(ctx, who);
    if (p === null) { kSend(ctx, "AP_" + tag + "_SET:ERR:no_player:" + who); return 0; }
    var res = "-";
    try {
        if (field === "fans") { kSign().setFanCount(p, value); res = "fans"; }
        else if (field === "love") { kAme().setLove(p, value); res = "love"; }
        else if (field === "cap") { kAtt().setAmeLoveCap(p, value); res = "cap"; }
        else if (field === "hp") { p.setHealth(value + 0.0); res = "hp"; }
        else if (field === "clearcd") { kAtt().setAmeLoveGainCdEnd(p, 0); res = "clearcd"; }
        else if (field === "cdend") { kAtt().setAmeLoveGainCdEnd(p, value); res = "cdend"; }
        else if (field === "signcd") { kAtt().setSignActiveCooldownEnd(p, value); res = "signcd"; }
        else if (field === "coin") {
            var E = kEcon();
            var rc = E.setBalance(p, value);
            res = "coin:" + rc;
        }
        else res = "unknown_field:" + field;
    } catch (e) { res = "ERR:" + kEx(e); }
    kSend(ctx, "AP_" + tag + "_SET:field=" + field + ":value=" + value + ":who=" + who + ":res=" + res
        + ":love=" + kRd(function () { return kAme().getLove(p); }, "ERR")
        + ":cap=" + kRd(function () { return kAme().getLoveCap(p); }, "ERR")
        + ":fans=" + kRd(function () { return kSign().getFanCount(p); }, "ERR")
        + ":hp=" + kRd(function () { return p.getHealth(); }, "ERR")
        + "/" + kRd(function () { return p.getMaxHealth(); }, "ERR")
        + ":atk=" + kRd(function () { return kDcm().attackPowerOf(p); }, "ERR")
        + ":def=" + kRd(function () { return kDcm().defensePowerOf(p); }, "ERR")
        + ":modL=" + kModAmount(p, kAttrs().ARMOR, "ame_love"));
    return 1;
}
function kDoOvd(ctx, tag, who, ticks, amp) {
    var p = kFind(ctx, who);
    if (p === null) { kSend(ctx, "AP_" + tag + "_OVD:ERR:no_player:" + who); return 0; }
    var res = "-";
    try {
        p.addEffect(new KMobEffectInstance(kFx().AME_OVERDOSE.get(), ticks, amp, false, true, true));
        res = "ok";
    } catch (e) { res = "ERR:" + kEx(e); }
    kSend(ctx, "AP_" + tag + "_OVD:who=" + who + ":ticks=" + ticks + ":amp=" + amp + ":res=" + res
        + ":ovdE=" + kFxText(p, kFx().AME_OVERDOSE.get()) + ":love=" + kRd(function () { return kAme().getLove(p); }, "ERR"));
    return 1;
}
function kDoSelftest(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var out = [];
    function vis(letter, fn) {
        try {
            var v = fn();
            if (v === null || v === undefined) { out.push(letter + "=null"); return; }
            var s = "" + v;
            s = s.replace(/\r?\n/g, " ");
            if (s.length > 40) s = s.substring(0, 40) + "…";
            out.push(letter + "=ok(" + s + ")");
        } catch (e) { out.push(letter + "=!" + kEx(e)); }
    }
    vis("kSign", function () { return kSign() === null ? "NULL" : "loaded"; });
    vis("kAme", function () { return kAme() === null ? "NULL" : "loaded"; });
    vis("kBase", function () { return kBase() === null ? "NULL" : "loaded"; });
    vis("kAtt", function () { return kAtt() === null ? "NULL" : "loaded"; });
    vis("kDcm", function () { return kDcm() === null ? "NULL" : "loaded"; });
    vis("kParty", function () { return kParty() === null ? "NULL" : "loaded"; });
    vis("kEcon", function () { return kEcon() === null ? "NULL" : "loaded"; });
    vis("fans", function () { return kSign().getFanCount(p); });
    vis("love", function () { return kAme().getLove(p); });
    vis("cap", function () { return kAme().getLoveCap(p); });
    vis("cdend", function () { return kAtt().getAmeLoveGainCdEnd(p); });
    vis("atk", function () { return kDcm().attackPowerOf(p); });
    vis("atkd", function () { return kDcm().attackPowerDisplayOf(p); });
    vis("def", function () { return kDcm().defensePowerOf(p); });
    vis("hasTeam", function () { return kParty().hasTeam(p) ? 1 : 0; });
    vis("teamN", function () { return kParty().collectTeamPlayers(p).size(); });
    vis("econAvail", function () { return kEcon().isAvailable() ? 1 : 0; });
    vis("balance", function () { return kEcon().getBalance(p); });
    vis("tick", function () { return kTick(p); });
    vis("armorAttr", function () { var A = kAttrs(); return p.getAttributeValue(A.ARMOR); });
    vis("fxOverdose", function () { return kFx().AME_OVERDOSE.get() === null ? "NULL" : "loaded"; });
    vis("curioDice", function () { return kSlotItem(p, "dice"); });
    vis("curioStand", function () { return kSlotItem(p, "stand"); });
    vis("dmgSrcMob", function () { return p.level.damageSources().mobAttack(p) === null ? "NULL" : "ok"; });
    kSend(ctx, "AP_" + tag + "_SELF:" + out.join(":"));
    return 1;
}

// ── 结算采样器:love / cap / ame_overdose 剩余变化时落一行(带新刻,可数「一次自然到期结算了几次」) ──
var kPollOn = false;
var kPollTag = "POLL";
var kPollPrev = {};

function kPollKey(name) { return "k" + name; }

ServerEvents.tick(event => {
    if (!kPollOn) return;
    try {
        var server = null;
        try { server = event.server; } catch (e0) { server = null; }
        if (server == null) { try { server = event.getServer(); } catch (e1) { server = null; } }
        if (server == null) return;
        var tick = -1;
        try { tick = server.getTickCount(); } catch (e2) { tick = -1; }
        var ps = server.getPlayerList().getPlayers();
        var it = ps.iterator();
        while (it.hasNext()) {
            var p = it.next();
            var name = kRd(function () { return p.getName().getString(); }, "?");
            var love = kRd(function () { return kAme().getLove(p); }, "ERR");
            var cap = kRd(function () { return kAme().getLoveCap(p); }, "ERR");
            var dur = "-";
            try {
                var inst = p.getEffect(kFx().AME_OVERDOSE.get());
                dur = (inst === null) ? "none" : ("" + inst.getDuration());
            } catch (e3) { dur = "ERR"; }
            var sig = love + "|" + cap + "|" + dur;
            var key = kPollKey(name);
            if (kPollPrev[key] !== sig) {
                kPollPrev[key] = sig;
                try {
                    p.sendSystemMessage(KComponent.literal("AP_" + kPollTag + ":tick=" + tick + ":who=" + name
                        + ":love=" + love + ":cap=" + cap + ":ovdDur=" + dur));
                } catch (e4) { /* 忽略 */ }
            }
        }
    } catch (eOuter) { /* 采样器自身异常不得影响游戏 */ }
});

ServerEvents.commandRegistry(event => {
    var C = event.commands;
    var root = C.literal("astralkame");

    root.then(C.literal("selftest").then(C.argument("tag", KStringArg.word()).executes(function (ctx) {
        var tag = KStringArg.getString(ctx, "tag");
        return kGuard(ctx, tag, function () { return kDoSelftest(ctx, tag); });
    })));

    root.then(C.literal("state").executes(function (ctx) {
        return kGuard(ctx, "KST", function () { return kDoState(ctx, "KST", "@a"); });
    }).then(C.argument("tag", KStringArg.word()).executes(function (ctx) {
        var tag = KStringArg.getString(ctx, "tag");
        return kGuard(ctx, tag, function () { return kDoState(ctx, tag, "@a"); });
    }).then(C.argument("who", KStringArg.word()).executes(function (ctx) {
        var tag = KStringArg.getString(ctx, "tag");
        var who = KStringArg.getString(ctx, "who");
        return kGuard(ctx, tag, function () { return kDoState(ctx, tag, who); });
    }))));

    root.then(C.literal("mobs").then(C.argument("tag", KStringArg.word()).executes(function (ctx) {
        var tag = KStringArg.getString(ctx, "tag");
        return kGuard(ctx, tag, function () { return kDoMobs(ctx, tag, 16); });
    }).then(C.argument("radius", KIntegerArg.integer()).executes(function (ctx) {
        var tag = KStringArg.getString(ctx, "tag");
        var r = KIntegerArg.getInteger(ctx, "radius");
        return kGuard(ctx, tag, function () { return kDoMobs(ctx, tag, r); });
    }))));

    root.then(C.literal("fx").then(C.argument("tag", KStringArg.word()).then(C.argument("who", KStringArg.word())
        .executes(function (ctx) {
            var tag = KStringArg.getString(ctx, "tag");
            var who = KStringArg.getString(ctx, "who");
            return kGuard(ctx, tag, function () { return kDoFx(ctx, tag, who); });
        }))));

    root.then(C.literal("give").then(C.argument("item", KStringArg.string()).then(C.argument("who", KStringArg.word())
        .executes(function (ctx) {
            var it = KStringArg.getString(ctx, "item");
            var who = KStringArg.getString(ctx, "who");
            return kGuard(ctx, "KGIVE", function () { return kDoGive(ctx, "KGIVE", it, who); });
        }))));

    root.then(C.literal("equip").then(C.argument("slot", KStringArg.word()).then(C.argument("item", KStringArg.string())
        .then(C.argument("who", KStringArg.word()).executes(function (ctx) {
            var slot = KStringArg.getString(ctx, "slot");
            var it = KStringArg.getString(ctx, "item");
            var who = KStringArg.getString(ctx, "who");
            return kGuard(ctx, "KEQ", function () { return kDoEquip(ctx, "KEQ", slot, it, who, 0); });
        })))));

    root.then(C.literal("skill").then(C.argument("who", KStringArg.word()).executes(function (ctx) {
        var who = KStringArg.getString(ctx, "who");
        return kGuard(ctx, "KSK", function () { return kDoSkill(ctx, "KSK", who); });
    })));

    root.then(C.literal("set").then(C.argument("field", KStringArg.word()).then(C.argument("value", KIntegerArg.integer())
        .then(C.argument("who", KStringArg.word()).executes(function (ctx) {
            var field = KStringArg.getString(ctx, "field");
            var v = KIntegerArg.getInteger(ctx, "value");
            var who = KStringArg.getString(ctx, "who");
            return kGuard(ctx, "KSET", function () { return kDoSet(ctx, "KSET", field, v, who); });
        })))));

    root.then(C.literal("smob").then(C.argument("tag", KStringArg.word()).executes(function (ctx) {
        var tag = KStringArg.getString(ctx, "tag");
        return kGuard(ctx, tag, function () { return kDoSmob(ctx, tag, "minecraft:pillager"); });
    }).then(C.argument("type", KStringArg.word()).executes(function (ctx) {
        var tag = KStringArg.getString(ctx, "tag");
        var ty = KStringArg.getString(ctx, "type");
        return kGuard(ctx, tag, function () { return kDoSmob(ctx, tag, ty); });
    }))));

    root.then(C.literal("hit").then(C.argument("who", KStringArg.word()).then(C.argument("mob", KIntegerArg.integer())
        .then(C.argument("tag", KStringArg.word()).executes(function (ctx) {
            var who = KStringArg.getString(ctx, "who");
            var id = KIntegerArg.getInteger(ctx, "mob");
            var tag = KStringArg.getString(ctx, "tag");
            return kGuard(ctx, tag, function () { return kDoHit(ctx, tag, who, id); });
        })))));

    root.then(C.literal("dmghit").then(C.argument("mob", KIntegerArg.integer()).then(C.argument("who", KStringArg.word())
        .then(C.argument("amount", KIntegerArg.integer()).then(C.argument("tag", KStringArg.word()).executes(function (ctx) {
            var id = KIntegerArg.getInteger(ctx, "mob");
            var who = KStringArg.getString(ctx, "who");
            var amt = KIntegerArg.getInteger(ctx, "amount");
            var tag = KStringArg.getString(ctx, "tag");
            return kGuard(ctx, tag, function () { return kDoDmgHit(ctx, tag, id, who, amt); });
        }))))));

    root.then(C.literal("ovd").then(C.argument("who", KStringArg.word()).then(C.argument("ticks", KIntegerArg.integer())
        .then(C.argument("amp", KIntegerArg.integer()).then(C.argument("tag", KStringArg.word()).executes(function (ctx) {
            var who = KStringArg.getString(ctx, "who");
            var ticks = KIntegerArg.getInteger(ctx, "ticks");
            var amp = KIntegerArg.getInteger(ctx, "amp");
            var tag = KStringArg.getString(ctx, "tag");
            return kGuard(ctx, tag, function () { return kDoOvd(ctx, tag, who, ticks, amp); });
        }))).then(C.argument("tag", KStringArg.word()).executes(function (ctx) {
            var who = KStringArg.getString(ctx, "who");
            var ticks = KIntegerArg.getInteger(ctx, "ticks");
            var tag = KStringArg.getString(ctx, "tag");
            return kGuard(ctx, tag, function () { return kDoOvd(ctx, tag, who, ticks, 0); });
        })))));

    root.then(C.literal("mobsreset").executes(function (ctx) {
        kMobs = [];
        kSend(ctx, "AP_MOBSRESET:have=" + kMobs.length);
        return 1;
    }));

    root.then(C.literal("resetfans").then(C.argument("who", KStringArg.word()).executes(function (ctx) {
        var who = KStringArg.getString(ctx, "who");
        return kGuard(ctx, "KRF", function () { return kDoResetFans(ctx, "KRF", who); });
    })));

    root.then(C.literal("poll").then(C.argument("mode", KStringArg.word()).executes(function (ctx) {
        var mode = KStringArg.getString(ctx, "mode").toLowerCase();
        if (mode === "on") { kPollOn = true; kPollPrev = {}; kSend(ctx, "AP_POLL:on=1"); }
        else if (mode === "off") { kPollOn = false; kSend(ctx, "AP_POLL:on=0"); }
        else { kPollTag = mode; kPollOn = true; kPollPrev = {}; kSend(ctx, "AP_POLL:tag=" + mode); }
        return 1;
    })));

    event.register(root);
});
