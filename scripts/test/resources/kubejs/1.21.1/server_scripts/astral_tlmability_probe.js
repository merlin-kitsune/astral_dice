// ════════════════════════════════════════════════════════════════════════════
//  astral_tlmability_probe.js —— 1.21.1 (NeoForge) 测试台探针
//  取证对象：非玩家骰子能力注册表 DiceCombatAbilities（2026-10-28 新增）
//
//  目的：证明「第三方可以把某颗骰子的掷点能力注册给 mob 持有者」这条链路
//  在真实伤害管线里成立，且注册表抛异常 / 掷点越界一律回退默认 1d6。
//
//  命令：/astraltlma run <tag>    （<tag> 为读数前缀，用例里用 A1）
//        /astraltlma clear
//
//  读数（每行一条，sendFailure 进聊天区 ⇒ latest.log）：
//    AP_<tag>_A_SETUP:reg=..:pred=..:part=..:slot=..:prod=..:abl=..:fn=..:reset=..
//    AP_<tag>_A_FN:n=3:dmg=15,15,15:abl=fn|adapter
//    AP_<tag>_A_CLR:n=8:min=..:max=..:in=1:lt=1
//    AP_<tag>_A_ONE:dmg=10
//    AP_<tag>_A_OOB:n=6:min=..:max=..:in=1
//    AP_<tag>_A_CRIM:n=120:dmg10=..:loss=..:rel=1:bad=0
//    AP_<tag>_A_WD:n=600:mean=2.7x:low=0.7x
//    AP_<tag>_A_CD:n=600:mean=4.2x:high=0.7x
//    AP_<tag>_A_END:ok=1:cleared=1:pillager=0
//
//  伤害口径（劫掠者 ATTACK_DAMAGE=5、敌对 baseAttack=4、牛 MAX_HEALTH=20）：
//    无骰 / 非参与者 ⇒ 原版 5（入口闸门直接 return，不结算）
//    有骰无能力     ⇒ 1d6 ⇒ 10..15
//    能力恒 6       ⇒ 15     能力恒 1 ⇒ 10     能力越界 99 ⇒ 回退 1d6（10..15）
//
//  骰子槽来源：kubejs/data/astral_dice/curios/entities/pillager.json
//  （data/<任意命名空间>/curios/entities/*.json 由 Curios 9 统一扫描）
// ════════════════════════════════════════════════════════════════════════════

var TLMA_DCP = "com.merlinkitsune.astral_dice.combat.DiceCombatParticipants";
var TLMA_DCA = "com.merlinkitsune.astral_dice.combat.DiceCombatAbilities";
var TLMA_CSU = "com.merlinkitsune.astral_dice.item.CurioSlotUtil";
var TLMA_CA = "top.theillusivec4.curios.api.CuriosApi";
var TLMA_IS = "net.minecraft.world.item.ItemStack";
var TLMA_ATTR = "net.minecraft.world.entity.ai.attributes.Attributes";
var TLMA_BIR = "net.minecraft.core.registries.BuiltInRegistries";
var TLMA_RL = "net.minecraft.resources.ResourceLocation";
var TLMA_ET = "net.minecraft.world.entity.EntityType";
var TLMA_WD = "com.merlinkitsune.astral_dice.event.WeirdDiceHandler";
var TLMA_CD = "com.merlinkitsune.astral_dice.event.CrimsonDiceHandler";
var TLMA_COMP = "net.minecraft.network.chat.Component";
var TLMA_ARG = "com.mojang.brigadier.arguments.StringArgumentType";

var TLMA_LF = String.fromCharCode(10);
var TLMA_CR = String.fromCharCode(13);
var _tlmaCache = {};
var TlmaComponent = null;
var TlmaStringArg = null;
try { TlmaComponent = Java.loadClass(TLMA_COMP); } catch (e) { TlmaComponent = null; }
try { TlmaStringArg = Java.loadClass(TLMA_ARG); } catch (e) { TlmaStringArg = null; }

function tlmaLoad(name) {
    if (_tlmaCache[name] !== undefined) { return _tlmaCache[name]; }
    var v = null;
    try { v = Java.loadClass(name); } catch (e) { v = null; }
    _tlmaCache[name] = v;
    return v;
}

function tlmaSend(ctx, text) {
    try { ctx.source.sendFailure(TlmaComponent.literal(text)); } catch (e) { }
}

function tlmaClean(value) {
    var t = "" + value;
    t = t.split(TLMA_CR).join(" ");
    t = t.split(TLMA_LF).join(" ");
    return t.length > 240 ? t.substring(0, 240) + "…" : t;
}

function tlmaRound(v) { return Math.round((0 + v) * 100) / 100; }

function tlmaNum(v) {
    try { var n = 0 + v; if (isNaN(n)) { return -1; } return n; } catch (e) { return -1; }
}

function tlmaSpawn(player, id, dx, dy, dz) {
    var BIR = tlmaLoad(TLMA_BIR);
    var RL = tlmaLoad(TLMA_RL);
    if (BIR === null || RL === null) { return null; }
    var type = null;
    try { type = BIR.ENTITY_TYPE.get(RL.parse(id)); } catch (e) { type = null; }
    if (type === null) { return null; }
    var entity = null;
    try { entity = type.create(player.level); } catch (e2) { entity = null; }
    if (entity === null) { return null; }
    try {
        entity.setPos(player.getX() + dx, player.getY() + dy, player.getZ() + dz);
        entity.setPersistenceRequired();
        player.level.addFreshEntity(entity);
    } catch (e3) { return null; }
    return entity;
}

// 命中判定器：只认「类型恒等 / 注册表 id 字符串相等」。
// 实测坑：BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) 在 KubeJS 里会返回
// defaultKey（真劫掠者读出 minecraft:pig），故这里绝不使用 getKey。
function tlmaPillagerPredicate() {
    var ET = tlmaLoad(TLMA_ET);
    var BIR = tlmaLoad(TLMA_BIR);
    if (ET === null || BIR === null) { return null; }
    var want = ET.PILLAGER;
    var wantId = "" + BIR.ENTITY_TYPE.getKey(want);
    return function (entity) {
        try {
            if (entity === null) { return false; }
            var type = entity.getType();
            if (type === null) { return false; }
            if (type === want) { return true; }
            return ("" + BIR.ENTITY_TYPE.getKey(type)) === wantId;
        } catch (e) { return false; }
    };
}

function tlmaStackOf(id) {
    try {
        var st = Item.of(id);
        if (st !== null && !st.isEmpty()) { return st; }
    } catch (e) { }
    var BIR = tlmaLoad(TLMA_BIR);
    var RL = tlmaLoad(TLMA_RL);
    var IS = tlmaLoad(TLMA_IS);
    if (BIR === null || RL === null || IS === null) { return null; }
    try {
        var item = BIR.ITEM.get(RL.parse(id));
        if (item === null) { return null; }
        return new IS(item, 1);
    } catch (e2) { return null; }
}

function tlmaMatcher(item) {
    return function (stack) {
        try {
            if (stack === null || stack.isEmpty()) { return false; }
            return stack.getItem() === item;
        } catch (e) { return false; }
    };
}

// 一次「劫掠者近战打牛」：牛是全新实体（避开受击无敌帧），打完立即 discard。
function tlmaHit(player, mob) {
    var ATTR = tlmaLoad(TLMA_ATTR);
    if (ATTR === null) { return "err:no_attr"; }
    var cow = tlmaSpawn(player, "minecraft:cow", 3.0, 0.0, 2.0);
    if (cow === null) { return "err:spawn"; }
    var result = "err:hit";
    try {
        var inst = cow.getAttribute(ATTR.MAX_HEALTH);
        if (inst === null) {
            result = "err:no_inst";
        } else {
            inst.setBaseValue(20.0);
            cow.setHealth(20.0);
            cow.setNoAi(true);
            mob.setTarget(cow);
            var hp0 = cow.getHealth();
            mob.doHurtTarget(cow);
            var hp1 = cow.getHealth();
            result = tlmaRound(hp0 - hp1);
        }
    } catch (e) { result = "err:" + tlmaClean(e); }
    try { cow.discard(); } catch (d) { }
    return result;
}

function tlmaHits(player, mob, n) {
    var out = [];
    for (var i = 0; i < n; i++) { out.push(tlmaHit(player, mob)); }
    return out;
}

function tlmaAllEqual(values, expected) {
    if (values.length === 0) { return false; }
    for (var i = 0; i < values.length; i++) {
        if (tlmaNum(values[i]) !== expected) { return false; }
    }
    return true;
}

function tlmaStats(values, lo, hi) {
    var n = 0;
    var min = 9999;
    var max = -9999;
    var inRange = 0;
    var lt = 0;
    for (var i = 0; i < values.length; i++) {
        var v = tlmaNum(values[i]);
        if (v < 0) { continue; }
        n = n + 1;
        if (v < min) { min = v; }
        if (v > max) { max = v; }
        if (v >= lo && v <= hi) { inRange = inRange + 1; }
        if (v < hi) { lt = lt + 1; }
    }
    if (n === 0) { return { n: 0, min: "na", max: "na", "in": 0, lt: 0 }; }
    return { n: n, min: min, max: max, "in": inRange, lt: lt };
}

// 备用适配器路径：JS 函数直接传给 DiceRollAbility 形参，失败再走 JavaAdapter。
function tlmaNewAdapter(rollValue) {
    var ifaceName = "com.merlinkitsune.astral_dice.combat.DiceCombatAbilities" + "$" + "DiceRollAbility";
    var IFACE = tlmaLoad(ifaceName);
    if (IFACE === null) { return null; }
    var impl = { roll: function (context) { return rollValue; } };
    try { return new JavaAdapter(IFACE, impl); } catch (e1) {
        try { return new JavaAdapter(Java.loadClass(ifaceName), impl); } catch (e2) { return null; }
    }
}

function tlmaRun(ctx, tag) {
    var player = null;
    try { player = ctx.source.getPlayerOrException(); } catch (e0) {
        tlmaSend(ctx, "AP_" + tag + "_A_ERR:noplayer");
        return 0;
    }
    var DCP = tlmaLoad(TLMA_DCP);
    var DCA = tlmaLoad(TLMA_DCA);
    var CSU = tlmaLoad(TLMA_CSU);
    var CA = tlmaLoad(TLMA_CA);
    if (DCP === null || DCA === null || CSU === null || CA === null) {
        tlmaSend(ctx, "AP_" + tag + "_A_ERR:class_missing:dcp=" + (DCP !== null) + ":dca=" + (DCA !== null) + ":csu=" + (CSU !== null) + ":ca=" + (CA !== null));
        return 0;
    }

    var reset = 1;
    try { DCA.clearForTests(); } catch (e1) { reset = 0; }
    try { DCP.clearForTests(); } catch (e2) { reset = 0; }

    var reg = "none";
    var pred = null;
    try { pred = tlmaPillagerPredicate(); } catch (e3) { pred = null; }
    if (pred !== null) {
        try { DCP.registerExtraAttacker(pred); reg = "ok"; } catch (e4) { reg = "throw"; }
    }

    var predProbe = "err";
    try {
        var probeMob = tlmaSpawn(player, "minecraft:pillager", -6.0, 0.0, -6.0);
        if (probeMob !== null) {
            predProbe = DCP.isParticipant(probeMob) ? 1 : 0;
            probeMob.discard();
        }
    } catch (e5) { predProbe = "err"; }

    var mob = tlmaSpawn(player, "minecraft:pillager", 4.2, 0.0, 2.0);
    if (mob === null) {
        tlmaSend(ctx, "AP_" + tag + "_A_ERR:mob_spawn");
        return 0;
    }
    try { mob.setNoAi(true); } catch (e6) { }
    var part = "err";
    try { part = DCP.isParticipant(mob) ? 1 : 0; } catch (e7) { part = "err"; }

    var dieStack = tlmaStackOf("astral_dice:diamond_dice");
    var dieItem = dieStack === null ? null : dieStack.getItem();

    var slotHandler = null;
    var slot = dieStack === null ? "no_stack" : "err";
    if (dieStack !== null) {
        try {
            var opt = CA.getCuriosInventory(mob);
            if (opt.isEmpty()) {
                slot = "no_inv";
            } else {
                var curiosMap = opt.get().getCurios();
                slotHandler = curiosMap.get("dice");
                if (slotHandler === null) {
                    slot = "no_slot";
                } else {
                    slotHandler.getStacks().setStackInSlot(0, dieStack);
                    slot = slotHandler.getStacks().getStackInSlot(0).isEmpty() ? "empty" : "ok";
                }
            }
        } catch (e8) { slot = "err:" + tlmaClean(e8); }
    }

    var prod = "err";
    try {
        if (dieItem !== null) {
            var got = CSU.findFirstCurioMatching(mob, tlmaMatcher(dieItem));
            prod = got.isEmpty() ? 0 : 1;
        }
    } catch (e9) { prod = "err:" + tlmaClean(e9); }

    // ── A2/A3 能力注册：JS 函数直传 → JavaAdapter 兜底 ─────────────────────────
    var ablHow = "none";
    var fnDmg = "na";
    var first3 = [];
    if (slot === "ok" && dieItem !== null) {
        try { DCA.register(tlmaMatcher(dieItem), function (c) { return 6; }); } catch (e10) {
            fnDmg = "reg_throw:" + tlmaClean(e10);
        }
        first3 = tlmaHits(player, mob, 3);
        if (tlmaAllEqual(first3, 15)) {
            ablHow = "fn";
        } else {
            fnDmg = first3.join(",");
        }
    }
    if (slot === "ok" && dieItem !== null && ablHow === "none") {
        try { DCA.clearForTests(); } catch (e11) { }
        var adapter = tlmaNewAdapter(6);
        if (adapter !== null) {
            try { DCA.register(tlmaMatcher(dieItem), adapter); } catch (e12) { }
            first3 = tlmaHits(player, mob, 3);
            if (tlmaAllEqual(first3, 15)) {
                ablHow = "adapter";
            } else {
                fnDmg = fnDmg + "|" + first3.join(",");
            }
        } else {
            fnDmg = fnDmg + "|no_adapter";
        }
    }

    // ── A4 清空注册表 ⇒ 回到 1d6（10..15），且必须出现 <15 的样本 ────────────────
    var clr = "skip";
    if (slot === "ok") {
        try {
            DCA.clearForTests();
            var s4 = tlmaStats(tlmaHits(player, mob, 8), 10, 15);
            clr = "n=" + s4.n + ":min=" + s4.min + ":max=" + s4.max + ":in=" + s4["in"] + ":lt=" + s4.lt;
        } catch (e13) { clr = "err:" + tlmaClean(e13); }
    }

    // ── A5 能力恒 1 ⇒ 10 ────────────────────────────────────────────────────────
    var one = "skip";
    if (slot === "ok" && dieItem !== null) {
        try {
            DCA.register(tlmaMatcher(dieItem), function (c) { return 1; });
            one = "dmg=" + tlmaHit(player, mob);
        } catch (e14) { one = "err:" + tlmaClean(e14); }
    }

    // ── A6 能力越界 99 ⇒ 回退 1d6（绝不能出现 4+5+99）────────────────────────────
    var oob = "skip";
    if (slot === "ok" && dieItem !== null) {
        try {
            DCA.clearForTests();
            DCA.register(tlmaMatcher(dieItem), function (c) { return 99; });
            var s6 = tlmaStats(tlmaHits(player, mob, 6), 10, 15);
            oob = "n=" + s6.n + ":min=" + s6.min + ":max=" + s6.max + ":in=" + s6["in"];
        } catch (e15) { oob = "err:" + tlmaClean(e15); }
    }

    // ── A7 内置能力（绯红骰）端到端：掷 1 时对持有者自伤 6 ──────────────────────
    // dice_damage 已登记 minecraft:bypasses_cooldown ⇒ 自伤不受受击无敌帧约束，
    // 故 loss 必然等于 6 × (掷出 1 的次数)，而掷出 1 时对牛伤害恰为 10。
    var crim = "skip";
    if (slot === "ok" && slotHandler !== null) {
        try {
            DCA.clearForTests();
            var crimson = tlmaStackOf("astral_dice:crimson_dice");
            if (crimson === null) {
                crim = "no_stack";
            } else {
                slotHandler.getStacks().setStackInSlot(0, crimson);
                var ATTR6 = tlmaLoad(TLMA_ATTR);
                var inst6 = mob.getAttribute(ATTR6.MAX_HEALTH);
                inst6.setBaseValue(1000.0);
                mob.setHealth(1000.0);
                var hp6 = mob.getHealth();
                var n7 = 120;
                var count10 = 0;
                var bad6 = 0;
                for (var i7 = 0; i7 < n7; i7++) {
                    var dn = tlmaNum(tlmaHit(player, mob));
                    if (dn === 10) { count10 = count10 + 1; }
                    if (!(dn >= 10 && dn <= 15)) { bad6 = bad6 + 1; }
                }
                var loss = tlmaRound(hp6 - mob.getHealth());
                var rel = (Math.abs(loss - 6.0 * count10) < 0.01) ? 1 : 0;
                crim = "n=" + n7 + ":dmg10=" + count10 + ":loss=" + loss + ":rel=" + rel + ":bad=" + bad6;
            }
        } catch (e16) { crim = "err:" + tlmaClean(e16); }
    }

    // ── A8/A9 权重函数静态采样（内置能力的掷点实现本体）─────────────────────────
    var wd = "err";
    try {
        var WDH = tlmaLoad(TLMA_WD);
        if (WDH === null) {
            wd = "no_class";
        } else {
            var n8 = 600;
            var sum8 = 0;
            var low8 = 0;
            for (var i8 = 0; i8 < n8; i8++) {
                var r8 = WDH.rollWeightedD6();
                sum8 = sum8 + r8;
                if (r8 <= 3) { low8 = low8 + 1; }
            }
            wd = "n=" + n8 + ":mean=" + tlmaRound(sum8 / n8) + ":low=" + tlmaRound(low8 / n8);
        }
    } catch (e17) { wd = "err:" + tlmaClean(e17); }

    var cd = "err";
    try {
        var CDH = tlmaLoad(TLMA_CD);
        if (CDH === null) {
            cd = "no_class";
        } else {
            var n9 = 600;
            var sum9 = 0;
            var high9 = 0;
            for (var i9 = 0; i9 < n9; i9++) {
                var r9 = CDH.rollWeightedHighFaceD6();
                sum9 = sum9 + r9;
                if (r9 >= 4) { high9 = high9 + 1; }
            }
            cd = "n=" + n9 + ":mean=" + tlmaRound(sum9 / n9) + ":high=" + tlmaRound(high9 / n9);
        }
    } catch (e18) { cd = "err:" + tlmaClean(e18); }

    // ── 读数 ────────────────────────────────────────────────────────────────────
    tlmaSend(ctx, "AP_" + tag + "_A_SETUP:reg=" + reg + ":pred=" + predProbe + ":part=" + part + ":slot=" + slot + ":prod=" + prod + ":abl=" + ablHow + ":fn=" + fnDmg + ":reset=" + reset);
    tlmaSend(ctx, "AP_" + tag + "_A_FN:n=" + first3.length + ":dmg=" + first3.join(",") + ":abl=" + ablHow);
    tlmaSend(ctx, "AP_" + tag + "_A_CLR:" + clr);
    tlmaSend(ctx, "AP_" + tag + "_A_ONE:" + one);
    tlmaSend(ctx, "AP_" + tag + "_A_OOB:" + oob);
    tlmaSend(ctx, "AP_" + tag + "_A_CRIM:" + crim);
    tlmaSend(ctx, "AP_" + tag + "_A_WD:" + wd);
    tlmaSend(ctx, "AP_" + tag + "_A_CD:" + cd);

    // ── 清理：撤销注册后该劫掠者必须不再是参与者 ───────────────────────────────
    var cleared = 1;
    try { DCA.clearForTests(); } catch (e19) { cleared = 0; }
    try { DCP.clearForTests(); } catch (e20) { cleared = 0; }
    var partEnd = "err";
    try { partEnd = DCP.isParticipant(mob) ? 1 : 0; } catch (e21) { partEnd = "err"; }
    try { mob.discard(); } catch (e22) { }
    tlmaSend(ctx, "AP_" + tag + "_A_END:ok=" + cleared + ":cleared=" + cleared + ":pillager=" + partEnd);
    return 0;
}

if (TlmaStringArg !== null && TlmaComponent !== null) {
    ServerEvents.commandRegistry(function (event) {
        var Commands = event.commands;
        var root = Commands.literal("astraltlma");
        root.then(Commands.literal("run").then(Commands.argument("tag", TlmaStringArg.word()).executes(function (ctx) {
            var tag = "A";
            try { tag = TlmaStringArg.getString(ctx, "tag"); } catch (e) { tag = "A"; }
            try {
                return tlmaRun(ctx, tag);
            } catch (e) {
                try { ctx.source.sendFailure(TlmaComponent.literal("AP_" + tag + "_A_EX:" + tlmaClean(e))); } catch (e2) { }
                return 0;
            }
        })));
        root.then(Commands.literal("clear").executes(function (ctx) {
            var ok = 0;
            try {
                var DCA = tlmaLoad(TLMA_DCA);
                var DCP = tlmaLoad(TLMA_DCP);
                if (DCA !== null) { DCA.clearForTests(); }
                if (DCP !== null) { DCP.clearForTests(); }
                ok = 1;
            } catch (e) { ok = 0; }
            try { ctx.source.sendFailure(TlmaComponent.literal("AP_CLR:ok=" + ok)); } catch (e2) { }
            return ok;
        }));
        event.register(root);
    });
}
