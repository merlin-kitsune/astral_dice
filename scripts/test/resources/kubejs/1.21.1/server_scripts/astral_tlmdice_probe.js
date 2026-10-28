// ════════════════════════════════════════════════════════════════════════════
//  astral_tlmdice_probe.js —— 1.21.1 (NeoForge)
//  「骰战参与者」(DiceCombatParticipants) 的实机行为取证
//
//  被测对象: neoforge-1.21.1 线 combat/DiceCombatParticipants.java(2026-10-28 新增)
//    · registerExtraAttacker(java.util.function.Predicate<LivingEntity>)  注册判定器
//    · isParticipant(LivingEntity)                                        入口闸门
//    · clearForTests()                                                    清空(仅测试台)
//  入口闸门位置: combat/DiceCombatEvents.java:334-344 (LivingDamageEvent.Pre @NORMAL)
//    if (!(directEntity instanceof Player player)) {
//        if (directEntity instanceof Mob mob
//                && (target instanceof Player || DiceCombatParticipants.isParticipant(mob)))
//            resolveMobMeleeAttack(mob, target, event, source, victimFactor);
//        return;   // ← 未命中闸门时**不写值**,原版伤害链照常落地
//    }
//  ⇒ 「注册后走骰战公式 / 未注册走原版伤害」这两态必须能从读数里直接分辨。
//
//  ⚠️ 本探针最重要的一条口径(实测发现,与任务书预期不一致,详见读数):
//     未注册时**不是**「目标血量不变」—— resolveMobMeleeAttack 是**覆盖式写值**,
//     未命中时它根本不写值,于是原版 Mob#doHurtTarget 的伤害照常扣血。
//     实测(2026-10-28,D0/D2 读数):劫掠者 ATTACK_DAMAGE=5 ⇒ 原版 20→15(dmg=5);
//     注册后走骰战公式 baseAttack(敌对=4) + ATTACK_DAMAGE(5) + 骰点(0) = 9 ⇒ 20→11(dmg=9)。
//     故两态的真实差别是**伤害数值 5 vs 9**(都会扣血),不是「掉不掉血」。
//     故读数在 brief 规定字段之外**追加** part=(isParticipant 实测值) 与 atk=(ATTACK_DAMAGE),
//     让「闸门是否放行」与「伤害数值来自哪条公式」各自独立可辨。
//
//  读数(单行、确定性顺序、带 tag):
//    AP_<tag>_TLMREG:how=<fn|adapter|none>:ok=<1|0>[:err=<各条失败路径异常原文>]:pillager=<0|1|err>:cow=<0|1|err>
//    AP_<tag>_TLMUNREG:ok=<1|0>[:err=<异常>]:pillager=<0|1|err>
//    AP_<tag>_TLMB:verdict=<unchanged|damaged>:hp0=<x>:hp1=<y>:dmg=<z>:same=<1|0|->:part=<0|1|err>:atk=<v>:prev=<tag|none>:m=<机制回显>
//    AP_<tag>_TLMEND:ok=1:cleared=<n>
//    AP_<tag>_EX:<异常单行>             (guard 兜底,任何未捕获异常都在这里)
//
//  机制回显语法沿用 astral_hostile_probe.js:成功的字母连写(如 CNTM),
//  失败的展开为 [字母=!<异常单行>]。
//
//  平台注意:
//    · Entity#getUUID() / #getStringUUID() 在 KubeJS 被整体挡掉(见 astral_hostile_probe.js
//      文件头「环境事实」)⇒ 本探针只靠实体对象本身,不取 UUID。
//    · LivingEntity#setHealth 会按 getMaxHealth() 截断 ⇒ 想读到 hp0=20 必须先把
//      MAX_HEALTH 属性基值调到 20(本探针先设属性、再 setHealth,失败只影响 hp0 数值)。
//    · ⚠️ BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()) 在本运行时**返回 defaultKey**
//      (实测:真劫掠者读出 "minecraft:pig")⇒ 用它写的判定器恒 false 且**不抛异常**,
//      表现为「注册成功但 isParticipant 恒 0」。判据必须用 e.getType() === EntityType.PILLAGER
//      或 getId(e.getType()) == getId(PILLAGER)。另:EntityType 无 getDescriptionId,
//      实体对象的 getClass() 被 KubeJS 挡掉(TypeError: Cannot find function getClass)。
//    · JavaAdapter 两条写法实测:new JavaAdapter(java.util.function.Predicate, …) 必抛
//      ReferenceError: "java" is not defined.(本运行时没有 java 全局);
//      new JavaAdapter(Java.loadClass("java.util.function.Predicate"), {test: fn}) 可用。
// ════════════════════════════════════════════════════════════════════════════

var TLM_DCP = "com.merlinkitsune.astral_dice.combat.DiceCombatParticipants";
var TLM_COMP = "net.minecraft.network.chat.Component";
var TLM_ARG = "com.mojang.brigadier.arguments.StringArgumentType";
var TLM_ATTR = "net.minecraft.world.entity.ai.attributes.Attributes";
var TLM_BIR = "net.minecraft.core.registries.BuiltInRegistries";
var TLM_RL = "net.minecraft.resources.ResourceLocation";
var TLM_ET = "net.minecraft.world.entity.EntityType";

var _tlmCache = {};
var TlmComponent = Java.loadClass(TLM_COMP);
var TlmStringArg = Java.loadClass(TLM_ARG);

// 上一次 tlmbattle 的 dmg(用于 same=);跨 tag 比较 —— 用例里 D2/D3 的 tag 本来就不同。
var _tlmLastDmg = null;
var _tlmLastTag = null;
var _tlmPred = null;

/** 延迟加载:任何一次 loadClass 失败都只影响该命令,不会让整个文件加载失败 */
function tlmLoad(name) {
    if (_tlmCache[name] !== undefined) return _tlmCache[name];
    var v = null;
    try { v = Java.loadClass(name); } catch (e) { v = null; }
    _tlmCache[name] = v;
    return v;
}

function tlmSend(ctx, text) {
    try { ctx.source.sendFailure(TlmComponent.literal(text)); } catch (e) { /* 回显失败不影响判定 */ }
}

/** 异常压成单行(读数必须是单行,且必须回显原文) */
function tlmEx(e) {
    var msg;
    try { msg = "" + e; } catch (x) { msg = "<unprintable>"; }
    msg = ("" + msg).replace(/\r?\n/g, " ");
    return (msg.length > 200) ? (msg.substring(0, 200) + "…") : msg;
}

function tlmGuard(ctx, tag, fn) {
    try {
        return fn();
    } catch (err) {
        tlmSend(ctx, "AP_" + tag + "_EX:" + tlmEx(err));
        return 0;
    }
}

/** 成功字母连写;仅当有失败时才展开 [字母=!异常] */
function tlmMark(ok, bad) {
    return ok.join("") + (bad.length ? "[" + bad.join(";") + "]" : "");
}

function tlmTry(fn, letter, ok, bad) {
    try { fn(); ok.push(letter); return true; }
    catch (err) { bad.push(letter + "=!" + tlmEx(err)); return false; }
}

/** 数值 → 定点短文本(读数稳定、可做**字符串完全相等**比较) */
function tlmNum(v) {
    if (v === null || v === undefined) return "null";
    var s = "" + (Math.round((0 + v) * 100) / 100);
    return s;
}

/** 实体类型 → 生成实体(定点,不依赖 AI) */
function tlmSpawn(p, typeId, dx, dy, dz) {
    var BIR = tlmLoad(TLM_BIR);
    var RL = tlmLoad(TLM_RL);
    if (BIR === null || RL === null) return null;
    var type = BIR.ENTITY_TYPE.get(RL.parse(typeId));
    var e = type.create(p.level);
    if (e == null) return null;
    e.setPos(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
    p.level.addFreshEntity(e);
    return e;
}

/**
 * 注册用的判定器本体:『劫掠者即为骰战参与者』
 *
 * ⚠️ 判据**不能**用 BuiltInRegistries.ENTITY_TYPE.getKey(...) —— 实测该调用对
 * e.getType() 拿到的是注册表 defaultKey("minecraft:pig"),于是判定器恒 false、
 * 调用方却完全看不出错(registerExtraAttacker 不抛异常)。
 * 实测可用的三条:e.getType() === EntityType.PILLAGER、getId(e.getType()) === getId(PILLAGER)、
 * ("" + e.getType()) === "minecraft:pillager"。此处用「恒等 + id」双判据。
 *
 * @return 判定器函数;类加载失败时返回 **null**(调用方必须显式记进读数,不得当作已注册)
 */
function tlmMakePredicate() {
    var ET = tlmLoad(TLM_ET);
    var BIR = tlmLoad(TLM_BIR);
    if (ET === null || BIR === null) return null;
    var wantType = ET.PILLAGER;
    var wantId = BIR.ENTITY_TYPE.getId(wantType);
    return function (e) {
        if (e === null || e === undefined) return false;
        try {
            var t = e.getType();
            if (t === null || t === undefined) return false;
            if (t === wantType) return true;
            return BIR.ENTITY_TYPE.getId(t) === wantId;
        } catch (err) {
            return false;
        }
    };
}

/** isParticipant 实测(1 放行 / 0 未放行 / "err:<异常>") */
function tlmIsPart(DCP, e) {
    if (DCP === null) return "err:class_not_found";
    if (e === null) return "err:nospawn";
    try { return DCP.isParticipant(e) ? 1 : 0; } catch (err) { return "err:" + tlmEx(err); }
}

/**
 * 判定器活性自证:临时生成「劫掠者(应 1)」与「牛(应 0)」各一只,读 isParticipant 后清掉。
 * 这一步把「registerExtraAttacker 调用没抛异常」与「判定器真的生效」区分开 ——
 * 前者在 Rhino 里完全可能静默失败(例如函数被当成 Object 塞进去,test() 时才炸)。
 */
function tlmProof(p, DCP) {
    var out = { pillager: "err", cow: "err" };
    var r = null;
    var c = null;
    try {
        r = tlmSpawn(p, "minecraft:pillager", -6.0, 0.0, -6.0);
        out.pillager = tlmIsPart(DCP, r);
    } catch (e1) { out.pillager = "err:" + tlmEx(e1); }
    try {
        c = tlmSpawn(p, "minecraft:cow", -6.0, 0.0, -4.0);
        out.cow = tlmIsPart(DCP, c);
    } catch (e2) { out.cow = "err:" + tlmEx(e2); }
    try { if (r != null) r.discard(); } catch (d1) { /* 忽略 */ }
    try { if (c != null) c.discard(); } catch (d2) { /* 忽略 */ }
    return out;
}

// ── tlmreg ──────────────────────────────────────────────────────────────────
function tlmReg(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var DCP = tlmLoad(TLM_DCP);
    if (DCP === null) {
        tlmSend(ctx, "AP_" + tag + "_TLMREG:how=none:ok=0:err=class_not_found:" + TLM_DCP
                + ":pillager=err:cow=err");
        return 0;
    }

    var errs = [];
    var how = "none";
    var proof = { pillager: "err", cow: "err" };

    // ① 直接把 JS 函数传给 Predicate 形参(Rhino 对单抽象方法接口的自动适配)
    try {
        var fn = tlmMakePredicate();
        if (fn === null) {
            errs.push("fn!predicate_build_failed:" + TLM_ET);
        } else {
            DCP.registerExtraAttacker(fn);
            proof = tlmProof(p, DCP);
            if (proof.pillager === 1) {
                how = "fn";
                _tlmPred = fn;
            } else {
                // 调用没抛异常但判定器不生效 ⇒ 清掉这条死注册,继续试 adapter
                errs.push("fn!registered_but_proof=\"" + proof.pillager + "\"");
                try { DCP.clearForTests(); } catch (c1) { errs.push("fn!clear=!" + tlmEx(c1)); }
            }
        }
    } catch (e1) {
        errs.push("fn!" + tlmEx(e1));
    }

    // ② JavaAdapter(java.util.function.Predicate, {test: fn})
    //    实测本运行时**没有 java 全局** ⇒ 字面写法必抛 ReferenceError;
    //    保留字面写法(把原文写进读数),再退回 Java.loadClass 版。
    if (how === "none") {
        var fn2 = tlmMakePredicate();
        if (fn2 === null) {
            errs.push("adapter!predicate_build_failed");
        } else {
            var ad = null;
            try {
                ad = new JavaAdapter(java.util.function.Predicate, { test: fn2 });
            } catch (e2) {
                errs.push("adapter_java!" + tlmEx(e2));
                try {
                    var PIF = tlmLoad("java.util.function.Predicate");
                    if (PIF === null) {
                        errs.push("adapter_loadclass!class_not_found");
                    } else {
                        ad = new JavaAdapter(PIF, { test: fn2 });
                    }
                } catch (e3) {
                    errs.push("adapter_loadclass!" + tlmEx(e3));
                }
            }
            if (ad !== null) {
                try {
                    DCP.registerExtraAttacker(ad);
                    proof = tlmProof(p, DCP);
                    if (proof.pillager === 1) {
                        how = "adapter";
                        _tlmPred = ad;
                    } else {
                        errs.push("adapter!registered_but_proof=\"" + proof.pillager + "\"");
                        try { DCP.clearForTests(); } catch (c2) { errs.push("adapter!clear=!" + tlmEx(c2)); }
                    }
                } catch (e4) {
                    errs.push("adapter_register!" + tlmEx(e4));
                }
            }
        }
    }

    var line = "AP_" + tag + "_TLMREG:how=" + how + ":ok=" + (how === "none" ? 0 : 1);
    if (errs.length) line += ":err=" + errs.join(";");
    line += ":pillager=" + proof.pillager + ":cow=" + proof.cow;
    tlmSend(ctx, line);
    return (how === "none") ? 0 : 1;
}

// ── tlmunreg ────────────────────────────────────────────────────────────────
function tlmUnreg(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var DCP = tlmLoad(TLM_DCP);
    if (DCP === null) {
        tlmSend(ctx, "AP_" + tag + "_TLMUNREG:ok=0:err=class_not_found:" + TLM_DCP + ":pillager=err");
        return 0;
    }
    var line = "AP_" + tag + "_TLMUNREG:ok=1";
    try {
        DCP.clearForTests();
        _tlmPred = null;
    } catch (e1) {
        line = "AP_" + tag + "_TLMUNREG:ok=0:err=" + tlmEx(e1);
    }
    line += ":pillager=" + tlmProof(p, DCP).pillager;
    tlmSend(ctx, line);
    return 1;
}

// ── tlmbattle ───────────────────────────────────────────────────────────────
/**
 * 造一场「劫掠者近战打牛」并读血量前后差。
 *   · 牛:MAX_HEALTH 基值 → 20、setHealth(20)、setNoAi(true) 钉住(不回血不移动);
 *   · 劫掠者:setNoAi(true) + setTarget(牛),再用 **Mob#doHurtTarget** 打出一次近战
 *     (1.21.1 merged jar 实证:public boolean doHurtTarget(Entity),内部 = ATTACK_DAMAGE
 *      经附魔链后 target.hurt);
 *   · 前后 hp 差 = dmg;与上一次 tlmbattle 的 dmg 做**字符串完全相等**比较得 same。
 */
function tlmBattle(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var DCP = tlmLoad(TLM_DCP);
    var ATTR = tlmLoad(TLM_ATTR);
    var ok = [], bad = [];
    var cow = null;
    var raid = null;
    var part = "err";
    var atk = "err";
    var hp0 = "err";
    var hp1 = "err";
    var dmg = "err";

    try {
        cow = tlmSpawn(p, "minecraft:cow", 3.0, 0.0, 2.0);
        if (cow === null) bad.push("S=!cow_spawn_null");
    } catch (e1) { bad.push("S=!" + tlmEx(e1)); }

    if (cow !== null) {
        // MAX_HEALTH 先抬到 20:否则 setHealth(20) 会被 getMaxHealth()=10 截断
        tlmTry(function () {
            var inst = cow.getAttribute(ATTR.MAX_HEALTH);
            if (inst == null) throw "max_health_attribute_null";
            inst.setBaseValue(20.0);
            cow.setHealth(20.0);
        }, "M", ok, bad);
        tlmTry(function () { cow.setNoAi(true); }, "N", ok, bad);
        tlmTry(function () { cow.setPersistenceRequired(); }, "P", ok, bad);
    }

    try {
        raid = tlmSpawn(p, "minecraft:pillager", 4.2, 0.0, 2.0);
        if (raid === null) bad.push("R=!pillager_spawn_null");
    } catch (e2) { bad.push("R=!" + tlmEx(e2)); }

    if (raid !== null) {
        tlmTry(function () { raid.setNoAi(true); }, "I", ok, bad);
        tlmTry(function () { raid.setPersistenceRequired(); }, "K", ok, bad);
        if (cow !== null) {
            tlmTry(function () { raid.setTarget(cow); }, "T", ok, bad);
        }
        try { part = tlmIsPart(DCP, raid); } catch (e3) { part = "err:" + tlmEx(e3); }
        try { atk = tlmNum(raid.getAttributeValue(ATTR.ATTACK_DAMAGE)); } catch (e4) { atk = "err"; }
    }

    if (cow !== null && raid !== null) {
        try { hp0 = tlmNum(cow.getHealth()); } catch (e5) { hp0 = "err"; }
        var hit = tlmTry(function () { raid.doHurtTarget(cow); }, "H", ok, bad);
        if (!hit) { bad.push("H=!no_hit_call"); }
        try { hp1 = tlmNum(cow.getHealth()); } catch (e6) { hp1 = "err"; }
        if (hp0 !== "err" && hp1 !== "err") {
            dmg = tlmNum((0 + hp0) - (0 + hp1));
        }
    }

    // same = 与上一次 tlmbattle 的 dmg 是否**完全相等**(首次为 -)
    var same = "-";
    var prev = (_tlmLastTag === null) ? "none" : _tlmLastTag;
    if (dmg !== "err" && _tlmLastDmg !== null) {
        same = (("" + _tlmLastDmg) === ("" + dmg)) ? 1 : 0;
    }
    if (dmg !== "err") { _tlmLastDmg = dmg; _tlmLastTag = tag; }

    var verdict = "unchanged";
    if (dmg === "err") {
        verdict = "unchanged";   // 读不到差值时不假装 damaged;具体原因在 m= 与 EX 行
    } else if ((0 + dmg) > 0) {
        verdict = "damaged";
    }

    // 清理生成物(每次结束都清,不让实体留在世界里干扰下一步)
    try { if (raid != null) raid.discard(); } catch (d1) { bad.push("C=!" + tlmEx(d1)); }
    try { if (cow != null) cow.discard(); } catch (d2) { bad.push("C=!" + tlmEx(d2)); }

    tlmSend(ctx, "AP_" + tag + "_TLMB:verdict=" + verdict
            + ":hp0=" + hp0 + ":hp1=" + hp1 + ":dmg=" + dmg + ":same=" + same
            + ":part=" + part + ":atk=" + atk + ":prev=" + prev + ":m=" + tlmMark(ok, bad));
    return 1;
}

// ── tlmpbattle ──────────────────────────────────────────────────────────────
/**
 * 造一场「劫掠者近战打**玩家**」并读玩家血量前后差。
 *   为什么要它：验收 (a) 要求「未注册时生物打玩家与改动前逐值相同」。玩家目标分支
 *   本次只多了一道 attackerHasDice 短路(isParticipant && hasDice)，故「注册前后同值」
 *   就是最直接的控制组：D7(未注册) 与 D8(已注册) 的 pdmg 必须完全相等(same=1)。
 *   · 先把玩家血量与 MAX_HEALTH 基值都顶到 20，保证 php0=20 不被截断；
 *   · 劫掠者 setNoAi(true) + setTarget(玩家)，再 doHurtTarget 打出一次近战；
 *   · 同时记录 arm=(玩家护甲值) —— 玩家目标防御项走 playerDefense(护甲/韧性)，
 *     该字段能把「数值对不上」直接归因到护甲而不是公式。
 */
function tlmPlayerBattle(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var DCP = tlmLoad(TLM_DCP);
    var ATTR = tlmLoad(TLM_ATTR);
    var ok = [], bad = [];
    var raid = null;
    var part = "err";
    var atk = "err";
    var arm = "err";
    var hp0 = "err";
    var hp1 = "err";
    var dmg = "err";

    tlmTry(function () {
        var inst = p.getAttribute(ATTR.MAX_HEALTH);
        if (inst != null) inst.setBaseValue(20.0);
        p.setHealth(20.0);
    }, "V", ok, bad);

    try {
        raid = tlmSpawn(p, "minecraft:pillager", 1.2, 0.0, 1.2);
        if (raid === null) bad.push("R=!pillager_spawn_null");
    } catch (e2) { bad.push("R=!" + tlmEx(e2)); }

    if (raid !== null) {
        tlmTry(function () { raid.setNoAi(true); }, "I", ok, bad);
        tlmTry(function () { raid.setPersistenceRequired(); }, "K", ok, bad);
        tlmTry(function () { raid.setTarget(p); }, "T", ok, bad);
        try { part = tlmIsPart(DCP, raid); } catch (e3) { part = "err:" + tlmEx(e3); }
        try { atk = tlmNum(raid.getAttributeValue(ATTR.ATTACK_DAMAGE)); } catch (e4) { atk = "err"; }
        try { arm = tlmNum(p.getArmorValue()); } catch (e5) { arm = "err"; }

        try { hp0 = tlmNum(p.getHealth()); } catch (e6) { hp0 = "err"; }
        tlmTry(function () { raid.doHurtTarget(p); }, "H", ok, bad);
        try { hp1 = tlmNum(p.getHealth()); } catch (e7) { hp1 = "err"; }
        if (hp0 !== "err" && hp1 !== "err") {
            dmg = tlmNum((0 + hp0) - (0 + hp1));
        }
    }

    var same = "-";
    var prev = (_tlmLastTag === null) ? "none" : _tlmLastTag;
    if (dmg !== "err" && _tlmLastDmg !== null) {
        same = (("" + _tlmLastDmg) === ("" + dmg)) ? 1 : 0;
    }
    if (dmg !== "err") { _tlmLastDmg = dmg; _tlmLastTag = tag; }

    var verdict = "unchanged";
    if (dmg !== "err" && (0 + dmg) > 0) verdict = "damaged";

    try { if (raid != null) raid.discard(); } catch (d1) { bad.push("C=!" + tlmEx(d1)); }
    try { p.setHealth(20.0); } catch (d2) { bad.push("E=!" + tlmEx(d2)); }

    tlmSend(ctx, "AP_" + tag + "_TLMP:verdict=" + verdict
            + ":php0=" + hp0 + ":php1=" + hp1 + ":pdmg=" + dmg + ":same=" + same
            + ":part=" + part + ":atk=" + atk + ":arm=" + arm + ":prev=" + prev
            + ":m=" + tlmMark(ok, bad));
    return 1;
}

// ── tlmend ──────────────────────────────────────────────────────────────────
function tlmEnd(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var DCP = tlmLoad(TLM_DCP);
    var cleared = 0;
    if (DCP !== null) {
        try { DCP.clearForTests(); cleared = 1; } catch (e1) { /* 记录在下面 ok= 里 */ }
    }
    _tlmPred = null;
    _tlmLastDmg = null;
    _tlmLastTag = null;
    tlmSend(ctx, "AP_" + tag + "_TLMEND:ok=" + ((DCP !== null && cleared === 1) ? 1 : 0)
            + ":cleared=" + cleared + ":pillager=" + tlmProof(p, DCP).pillager);
    return 1;
}

ServerEvents.commandRegistry(event => {
    var Commands = event.commands;
    event.register(
        Commands.literal("astraltlm")
            .then(Commands.literal("tlmreg")
                .then(Commands.argument("tag", TlmStringArg.word())
                    .executes(ctx => tlmGuard(ctx, TlmStringArg.getString(ctx, "tag"), function () {
                        return tlmReg(ctx, TlmStringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("tlmunreg")
                .then(Commands.argument("tag", TlmStringArg.word())
                    .executes(ctx => tlmGuard(ctx, TlmStringArg.getString(ctx, "tag"), function () {
                        return tlmUnreg(ctx, TlmStringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("tlmbattle")
                .then(Commands.argument("tag", TlmStringArg.word())
                    .executes(ctx => tlmGuard(ctx, TlmStringArg.getString(ctx, "tag"), function () {
                        return tlmBattle(ctx, TlmStringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("tlmpbattle")
                .then(Commands.argument("tag", TlmStringArg.word())
                    .executes(ctx => tlmGuard(ctx, TlmStringArg.getString(ctx, "tag"), function () {
                        return tlmPlayerBattle(ctx, TlmStringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("tlmend")
                .then(Commands.argument("tag", TlmStringArg.word())
                    .executes(ctx => tlmGuard(ctx, TlmStringArg.getString(ctx, "tag"), function () {
                        return tlmEnd(ctx, TlmStringArg.getString(ctx, "tag"));
                    }))))
    );
});
