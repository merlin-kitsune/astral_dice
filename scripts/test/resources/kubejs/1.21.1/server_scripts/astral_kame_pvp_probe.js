// ════════════════════════════════════════════════════════════════════════════
//  astral_kame_pvp_probe.js —— TEMP 探针（2026-10-17 拆双 id PvP 回归批次专用，收尾删除）
//     astralpvp pvp <tag> <attacker> <victim>   真实近战一击；读数含双方 mark/indicator 与伤害
//     astralpvp seq <tag> <attacker> <victim>   同态两击（第 1 击无标记、第 2 击先给攻击方真标记）⇒ cutX100
//     astralpvp mark <tag> <who>                直接施加 kangel_fan 目标标记（等价于真实入口的 applyMark）
//  只读/只造测试状态，不触碰产品代码。
// ════════════════════════════════════════════════════════════════════════════
var KPTComponent = Java.loadClass("net.minecraft.network.chat.Component");
var KPTString = Java.loadClass("com.mojang.brigadier.arguments.StringArgumentType");
var KPTFx = Java.loadClass("com.merlinkitsune.astral_dice.effect.ModEffects");
var KPTMark = Java.loadClass("com.merlinkitsune.astral_dice.effect.KangelFanEffect");
var KPTSign = Java.loadClass("com.merlinkitsune.astral_dice.item.sign.KangelSignItem");
var KPRemoval = Java.loadClass("com.merlinkitsune.starengine.event.ModEffectRemoval");

function kptSend(ctx, text) {
    try { ctx.source.sendFailure(KPTComponent.literal(text)); } catch (e) { /* ignore */ }
}
function kptFxText(p, holder) {
    try {
        var inst = p.getEffect(holder);
        if (inst === null) return "none";
        return "amp=" + inst.getAmplifier() + ";dur=" + inst.getDuration()
            + ";icon=" + (inst.showIcon() ? 1 : 0) + ";vis=" + (inst.isVisible() ? 1 : 0);
    } catch (e) { return "ERR:" + e; }
}
function kptPlayer(ctx, name) {
    try { return ctx.source.getServer().getPlayerList().getPlayerByName(name); } catch (e) { return null; }
}
function kptPrepare(v) {
    v.setHealth(v.getMaxHealth());
    v.invulnerableTime = 0;
    v.hurtTime = 0;
    v.hurtMarked = false;
}
function kptLine(tag, a, v, dmg) {
    var out = "AP_" + tag + "_PVP:atk=" + a.getName().getString() + ":tgt=" + v.getName().getString();
    out += ":dmgX100=" + Math.round(dmg * 100);
    out += ":atkMark=" + kptFxText(a, KPTFx.KANGEL_FAN);
    out += ":atkInd=" + kptFxText(a, KPTFx.KANGEL_FAN_INDICATOR);
    out += ":tgtMark=" + kptFxText(v, KPTFx.KANGEL_FAN);
    out += ":tgtInd=" + kptFxText(v, KPTFx.KANGEL_FAN_INDICATOR);
    try { out += ":tick=" + a.level.getLevelData().getGameTime(); } catch (e2) { out += ":tick=ERR"; }
    return out;
}
function kptPvp(ctx, tag, an, vn) {
    var a = kptPlayer(ctx, an);
    var v = kptPlayer(ctx, vn);
    if (a === null || v === null) {
        kptSend(ctx, "AP_" + tag + "_PVP:atk=" + (a === null ? "missing" : an) + ":tgt=" + (v === null ? "missing" : vn));
        return;
    }
    kptPrepare(v);
    try { a.resetAttackStrengthTicker(); } catch (e1) { /* older mappings */ }
    try { a.attackStrengthTicker = 100; } catch (e1b) { /* ignore */ }
    var hp0 = v.getHealth();
    a.attack(v);
    var hp1 = v.getHealth();
    kptSend(ctx, kptLine(tag, a, v, hp0 - hp1));
}
function kptSeq(ctx, tag, an, vn) {
    var a = kptPlayer(ctx, an);
    var v = kptPlayer(ctx, vn);
    if (a === null || v === null) {
        kptSend(ctx, "AP_" + tag + "_SEQ:atk=" + (a === null ? "missing" : an) + ":tgt=" + (v === null ? "missing" : vn));
        return;
    }
    kptPrepare(v);
    try { a.resetAttackStrengthTicker(); } catch (e1) { /* ignore */ }
    try { a.attackStrengthTicker = 100; } catch (e1b) { /* ignore */ }
    var hp0 = v.getHealth();
    a.attack(v);
    var d1 = hp0 - v.getHealth();
    KPTMark.applyMark(a);
    kptPrepare(v);
    try { a.resetAttackStrengthTicker(); } catch (e2) { /* ignore */ }
    var hp2 = v.getHealth();
    a.attack(v);
    var d2 = hp2 - v.getHealth();
    var out = "AP_" + tag + "_SEQ:atk=" + an + ":tgt=" + vn;
    out += ":d1X100=" + Math.round(d1 * 100) + ":d2X100=" + Math.round(d2 * 100);
    out += ":cutX100=" + Math.round((d1 - d2) * 100);
    out += ":atkMark=" + kptFxText(a, KPTFx.KANGEL_FAN);
    kptSend(ctx, out);
}

function kptMit(ctx, tag, who, attackerName) {
    var v = kptPlayer(ctx, who);
    var a = kptPlayer(ctx, attackerName);
    if (v === null || a === null) {
        kptSend(ctx, "AP_" + tag + "_MIT:who=" + (v === null ? "missing" : who) + ":attacker=" + (a === null ? "missing" : attackerName));
        return;
    }
    var out = "AP_" + tag + "_MIT:who=" + who + ":attacker=" + attackerName;
    try {
        var src = v.level.damageSources().playerAttack(a);
        out += ":active=" + (KPTSign.fanMitigationActive(v, src) ? 1 : 0);
    } catch (e) { out += ":active=ERR:" + e; }
    out += ":attackerMark=" + kptFxText(a, KPTFx.KANGEL_FAN);
    kptSend(ctx, out);
}


function kptPair(ctx, tag, a1n, a2n, vn) {
    var a1 = kptPlayer(ctx, a1n);
    var a2 = kptPlayer(ctx, a2n);
    var v = kptPlayer(ctx, vn);
    if (a1 === null || a2 === null || v === null) {
        kptSend(ctx, "AP_" + tag + "_PAIR:missing=" + (a1 === null ? a1n : "") + "," + (a2 === null ? a2n : "") + "," + (v === null ? vn : ""));
        return;
    }
    kptPrepare(v);
    try { a1.resetAttackStrengthTicker(); } catch (e1) { /* ignore */ }
    try { a1.attackStrengthTicker = 100; } catch (e1b) { /* ignore */ }
    var h1 = v.getHealth();
    a1.attack(v);
    var d1 = h1 - v.getHealth();
    var m1 = kptFxText(a1, KPTFx.KANGEL_FAN);
    kptPrepare(v);
    try { a2.resetAttackStrengthTicker(); } catch (e2) { /* ignore */ }
    try { a2.attackStrengthTicker = 100; } catch (e2b) { /* ignore */ }
    var h2 = v.getHealth();
    a2.attack(v);
    var d2 = h2 - v.getHealth();
    var out = "AP_" + tag + "_PAIR:a1=" + a1n + ":d1X100=" + Math.round(d1 * 100)
        + ":a2=" + a2n + ":d2X100=" + Math.round(d2 * 100)
        + ":diffX100=" + Math.round((d1 - d2) * 100)
        + ":a1MarkAfter=" + m1
        + ":tgtMarkAfter=" + kptFxText(v, KPTFx.KANGEL_FAN);
    kptSend(ctx, out);
}



function kptJoinTeam(ctx, tag, teamName, who) {
    var p = kptPlayer(ctx, who);
    if (p === null) { kptSend(ctx, "AP_" + tag + "_TEAM:missing=" + who); return; }
    var out = "AP_" + tag + "_TEAM:who=" + who;
    try {
        var sb = p.level.getServer().getScoreboard();
        var name = "" + p.getGameProfile().getName();
        var team = null;
        var it = sb.getPlayerTeams().iterator();
        while (it.hasNext()) { var tt = it.next(); if (("" + tt.getName()) === teamName) { team = tt; break; } }
        if (team === null) { kptSend(ctx, out + ":profileName=" + name + ":team=absent:" + teamName); return; }
        sb.addPlayerToTeam(name, team);
        out += ":profileName=" + name + ":joined=" + teamName;
    } catch (e) { out += ":ERR:" + e; }
    kptSend(ctx, out);
}
function kptLeaveTeam(ctx, tag, who) {
    var p = kptPlayer(ctx, who);
    if (p === null) { kptSend(ctx, "AP_" + tag + "_LTEAM:missing=" + who); return; }
    var out = "AP_" + tag + "_LTEAM:who=" + who;
    try {
        var sb = p.level.getServer().getScoreboard();
        var name = "" + p.getGameProfile().getName();
        sb.removePlayerFromTeam(name);
        out += ":profileName=" + name + ":left=1";
    } catch (e) { out += ":ERR:" + e; }
    kptSend(ctx, out);
}

function kptClear(ctx, tag, who) {
    var p = kptPlayer(ctx, who);
    if (p === null) { kptSend(ctx, "AP_" + tag + "_CLEAR:missing=" + who); return; }
    try {
        KPRemoval.remove(p, KPTFx.KANGEL_FAN);
        KPRemoval.remove(p, KPTFx.KANGEL_FAN_INDICATOR);
    } catch (e) { kptSend(ctx, "AP_" + tag + "_CLEAR:ERR:" + e); return; }
    kptSend(ctx, "AP_" + tag + "_CLEAR:who=" + who + ":mark=" + kptFxText(p, KPTFx.KANGEL_FAN)
        + ":ind=" + kptFxText(p, KPTFx.KANGEL_FAN_INDICATOR));
}

function kptMark(ctx, tag, who) {
    var p = kptPlayer(ctx, who);
    if (p === null) { kptSend(ctx, "AP_" + tag + "_MARK:missing=" + who); return; }
    KPTMark.applyMark(p);
    kptSend(ctx, "AP_" + tag + "_MARK:who=" + who + ":mark=" + kptFxText(p, KPTFx.KANGEL_FAN)
        + ":ind=" + kptFxText(p, KPTFx.KANGEL_FAN_INDICATOR));
}

ServerEvents.commandRegistry(function (event) {
    var C = event.commands;
    var root = C.literal("astralpvp");
    root.then(C.literal("pvp")
        .then(C.argument("tag", KPTString.word())
            .then(C.argument("attacker", KPTString.word())
                .then(C.argument("victim", KPTString.word())
                    .executes(function (ctx) {
                        kptPvp(ctx, KPTString.getString(ctx, "tag"), KPTString.getString(ctx, "attacker"), KPTString.getString(ctx, "victim"));
                        return 1;
                    })))));
    root.then(C.literal("seq")
        .then(C.argument("tag", KPTString.word())
            .then(C.argument("attacker", KPTString.word())
                .then(C.argument("victim", KPTString.word())
                    .executes(function (ctx) {
                        kptSeq(ctx, KPTString.getString(ctx, "tag"), KPTString.getString(ctx, "attacker"), KPTString.getString(ctx, "victim"));
                        return 1;
                    })))));
    root.then(C.literal("jointeam")
        .then(C.argument("tag", KPTString.word())
            .then(C.argument("team", KPTString.word())
                .then(C.argument("who", KPTString.word())
                    .executes(function (ctx) {
                        kptJoinTeam(ctx, KPTString.getString(ctx, "tag"), KPTString.getString(ctx, "team"), KPTString.getString(ctx, "who"));
                        return 1;
                    })))));
    root.then(C.literal("leaveteam")
        .then(C.argument("tag", KPTString.word())
            .then(C.argument("who", KPTString.word())
                .executes(function (ctx) {
                    kptLeaveTeam(ctx, KPTString.getString(ctx, "tag"), KPTString.getString(ctx, "who"));
                    return 1;
                }))));
    root.then(C.literal("clearfx")
        .then(C.argument("tag", KPTString.word())
            .then(C.argument("who", KPTString.word())
                .executes(function (ctx) {
                    kptClear(ctx, KPTString.getString(ctx, "tag"), KPTString.getString(ctx, "who"));
                    return 1;
                }))));
    root.then(C.literal("pair")
        .then(C.argument("tag", KPTString.word())
            .then(C.argument("a1", KPTString.word())
                .then(C.argument("a2", KPTString.word())
                    .then(C.argument("victim", KPTString.word())
                        .executes(function (ctx) {
                            kptPair(ctx, KPTString.getString(ctx, "tag"), KPTString.getString(ctx, "a1"), KPTString.getString(ctx, "a2"), KPTString.getString(ctx, "victim"));
                            return 1;
                        }))))));
    root.then(C.literal("mit")
        .then(C.argument("tag", KPTString.word())
            .then(C.argument("who", KPTString.word())
                .then(C.argument("attacker", KPTString.word())
                    .executes(function (ctx) {
                        kptMit(ctx, KPTString.getString(ctx, "tag"), KPTString.getString(ctx, "who"), KPTString.getString(ctx, "attacker"));
                        return 1;
                    })))));
    root.then(C.literal("mark")
        .then(C.argument("tag", KPTString.word())
            .then(C.argument("who", KPTString.word())
                .executes(function (ctx) {
                    kptMark(ctx, KPTString.getString(ctx, "tag"), KPTString.getString(ctx, "who"));
                    return 1;
                }))));
    event.register(root);
});
