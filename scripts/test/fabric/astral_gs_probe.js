// ════════════════════════════════════════════════════════════════════════════
//  astral_gs_probe.js —— 1.3.6「枪匠弱点识破叠层 + 王之力真扣 + 伤害增加真伤段 +
//                        减伤口径」专项取证探针（服务端，RCON 驱动）
//
//  ── 为什么用服务端 ──────────────────────────────────────────────────────────
//   2026-10-02 用户裁决：本次要验的四项**全部是服务端逻辑**（层数、伤害数值、减伤），
//   而线生产测试台（mt.ps1）走 Win32 PostMessage 注入**客户端聊天**，GLFW 忽略非前台
//   窗口按键 ⇒ 无人值守下不可靠（续 46 §C 实测）。故改为本台已验证的
//   「dev 服务端 + RCON + Carpet 假玩家 + KubeJS 探针」通道。
//
//  ── 命令一览（驱动方 = ft_inject.ps1 --channel rcon）───────────────────────
//    /astralgs env                              只读：四个类的目标方法是否可用
//    /astralgs stack  <player> <target> <n>     连续攻击带破绽目标 n 次 → 每次后的层数 + 剩余时长
//    /astralgs dodge  <player> <target> <n>     连续闪避/反击 n 次 → 每次后的层数
//    /astralgs cost   <player> [tag]            hurt(card_cost, 8) → 实扣血量
//    /astralgs true   <player> [tag]            hurt(unreducible_damage, 5) → 实扣血量
//    /astralgs berserk <player> <amp> [tag]     施加 BERSERK(amp) 后 hurt(true_damage, 10) → 实扣血量
//    /astralgs fn     <player> <dmg>            WhetstoneChipItem#modifyIncomingDamage 双档读数
//
//  ── 判据形状 ───────────────────────────────────────────────────────────────
//    AP_GS_ENV:         cost=.. unreducible=.. moses=.. whet=.. berserkFlag=..
//    AP_GS_STACK_<i>:   stacks=<n> duration=<ticks>
//    AP_GS_DODGE_<i>:   stacks=<n>
//    AP_GS_COST:        before=<h> after=<h> delta=<d> tag=<t>   （期望 delta≈8）
//    AP_GS_TRUE:        before=<h> after=<h> delta=<d> tag=<t>   （期望 delta≈5）
//    AP_GS_BERSERK:     amp=<a> before=<h> after=<h> delta=<d>   （期望 delta≈10+(a+1)）
//    AP_GS_FN:          flatOn=<v> flatOff=<v>                  （期望差 = 2，且 lowHealth 才生效）
// ════════════════════════════════════════════════════════════════════════════

var StringArg = Java.loadClass("com.mojang.brigadier.arguments.StringArgumentType");
var IntegerArg = Java.loadClass("com.mojang.brigadier.arguments.IntegerArgumentType");

var MosesSignItem = Java.loadClass("com.merlinkitsune.astral_dice.item.sign.MosesSignItem");
var WeaknessReveal = Java.loadClass("com.merlinkitsune.astral_dice.effect.WeaknessRevealEffect");
var ModDamageTypes = Java.loadClass("com.merlinkitsune.astral_dice.damage.ModDamageTypes");
var ModEffects = Java.loadClass("com.merlinkitsune.astral_dice.effect.ModEffects");
var Whetstone = Java.loadClass("com.merlinkitsune.astral_dice.item.chip.WhetstoneChipItem");
var MobEffectInstance = Java.loadClass("net.minecraft.world.effect.MobEffectInstance");

function out(key, text) {
    console.info("AP_GS_" + key + ": " + text);
}

function exText(e) {
    var m = e == null ? "null" : ("" + e);
    return m.substring(0, 220);
}

function findPlayer(ctx, name) {
    var pl = ctx.source.server.getPlayerList().getPlayerByName(name);
    return pl == null ? null : pl;
}

function stacks(p) {
    return WeaknessReveal.getStacks(p);
}

function durationOf(p) {
    // WEAKNESS_REVEAL 在 1.20.1 侧是 RegistryObject ⇒ .get()
    var eff = ModEffects.WEAKNESS_REVEAL.get();
    var inst = p.getEffect(eff);
    return inst == null ? -1 : inst.getDuration();
}

// 每次测量前把效果清干净，保证起点可复现
function resetStacks(p) {
    try { WeaknessReveal.removeAll(p); } catch (e) { }
}

function doStack(ctx, player, target, times) {
    resetStacks(player);
    var applied = MosesSignItem.applyBroken(player, target);
    out("STACK_APPLY", "applied=" + applied + " targetHasBroken=" + target.hasEffect(ModEffects.MOSES_BROKEN.get()));
    for (var i = 1; i <= times; i++) {
        MosesSignItem.onAttackBrokenTarget(player, target);
        out("STACK_" + i, "stacks=" + stacks(player) + " duration=" + durationOf(player));
    }
    return 1;
}

function doDodge(ctx, player, target, times) {
    resetStacks(player);
    for (var i = 1; i <= times; i++) {
        MosesSignItem.onDodgeCounter(player, target);
        out("DODGE_" + i, "stacks=" + stacks(player));
    }
    return 1;
}

function damageOnce(ctx, player, sourceFactory, key, amount, tag) {
    var before = player.getHealth();
    var src = sourceFactory(player);
    var ok = player.hurt(src, amount);
    var after = player.getHealth();
    out(key, "before=" + before + " after=" + after + " delta=" + (before - after)
        + " hurtOk=" + ok + " tag=" + (tag == null ? "-" : tag));
    return 1;
}

function doCost(ctx, player, tag) {
    return damageOnce(ctx, player, function (p) { return ModDamageTypes.cardCost(p.level, p); },
        "COST", 8.0, tag);
}

function doTrue(ctx, player, tag) {
    return damageOnce(ctx, player, function (p) { return ModDamageTypes.unreducibleDamage(p.level, p); },
        "TRUE", 5.0, tag);
}

function doBerserk(ctx, player, amp, tag) {
    // 施加 BERSERK（层数 = amp + 1）
    player.addEffect(new MobEffectInstance(ModEffects.BERSERK.get(), 3600, amp, false, false, true));
    // 本体用 true_damage（不吃护甲，扣血清晰），狂暴真伤段应再叠 (amp+1)
    var before = player.getHealth();
    var src = ModDamageTypes.trueDamage(player.level, player);
    player.hurt(src, 10.0);
    var after = player.getHealth();
    out("BERSERK", "amp=" + amp + " before=" + before + " after=" + after
        + " delta=" + (before - after) + " expected≈" + (10 + amp + 1) + " tag=" + (tag == null ? "-" : tag));
    return 1;
}

function doFn(ctx, player, dmg, hp) {
    if (hp > 0) { player.setHealth(hp); }
    var h = player.getHealth();
    var on = Whetstone.modifyIncomingDamage(player, dmg, true);
    var off = Whetstone.modifyIncomingDamage(player, dmg, false);
    out("FN", "hp=" + h + "/" + player.getMaxHealth()
        + " lowHealth=" + (h <= player.getMaxHealth() * 0.5)
        + " flatOn=" + on + " flatOff=" + off);
    return 1;
}

function doHp(ctx, player, value) {
    player.setHealth(value);
    out("HP", "set=" + value + " now=" + player.getHealth() + " max=" + player.getMaxHealth());
    return 1;
}

function doSrc(ctx, player) {
    var costSrc = ModDamageTypes.cardCost(player.level, player);
    var trueSrc = ModDamageTypes.unreducibleDamage(player.level, player);
    var diceSrc = ModDamageTypes.diceDamage(player.level, player);
    out("SRC", "costIsCardCost=" + costSrc.is(ModDamageTypes.CARD_COST)
        + " trueIsUnreducible=" + trueSrc.is(ModDamageTypes.UNREDUCIBLE_DAMAGE)
        + " diceIsUnreducible=" + diceSrc.is(ModDamageTypes.UNREDUCIBLE_DAMAGE)
        + " diceIsCardCost=" + diceSrc.is(ModDamageTypes.CARD_COST));
    return 1;
}

function doEnv(ctx) {
    var parts = [];
    function probe(label, fn) {
        try { parts.push(label + "=" + fn()); } catch (e) { parts.push(label + "=ERR(" + exText(e) + ")"); }
    }
    probe("cost", function () { return ModDamageTypes.CARD_COST != null; });
    probe("unreducible", function () { return ModDamageTypes.UNREDUCIBLE_DAMAGE != null; });
    probe("moses", function () { return typeof MosesSignItem.onAttackBrokenTarget === "function"; });
    probe("whet", function () { return typeof Whetstone.modifyIncomingDamage === "function"; });
    out("ENV", parts.join(" "));
    return 1;
}

function guard(ctx, tag, fn) {
    try {
        return fn();
    } catch (e) {
        out("ERR", "tag=" + tag + " ex=" + exText(e));
        return 0;
    }
}

ServerEvents.commandRegistry(event => {
    const { commands: Commands } = event;
    event.register(
        Commands.literal("astralgs")
            .then(Commands.literal("env")
                .executes(ctx => guard(ctx, "env", function () { return doEnv(ctx); })))
            .then(Commands.literal("stack")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("target", StringArg.word())
                        .then(Commands.argument("n", IntegerArg.integer(1, 20))
                            .executes(ctx => guard(ctx, "stack", function () {
                                var p = findPlayer(ctx, StringArg.getString(ctx, "player"));
                                var t = findPlayer(ctx, StringArg.getString(ctx, "target"));
                                if (p == null || t == null) { out("MISS", "player/target not found"); return 0; }
                                return doStack(ctx, p, t, IntegerArg.getInteger(ctx, "n"));
                            }))))))
            .then(Commands.literal("dodge")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("target", StringArg.word())
                        .then(Commands.argument("n", IntegerArg.integer(1, 20))
                            .executes(ctx => guard(ctx, "dodge", function () {
                                var p = findPlayer(ctx, StringArg.getString(ctx, "player"));
                                var t = findPlayer(ctx, StringArg.getString(ctx, "target"));
                                if (p == null || t == null) { out("MISS", "player/target not found"); return 0; }
                                return doDodge(ctx, p, t, IntegerArg.getInteger(ctx, "n"));
                            }))))))
            .then(Commands.literal("cost")
                .then(Commands.argument("player", StringArg.word())
                    .executes(ctx => guard(ctx, "cost", function () {
                        var p = findPlayer(ctx, StringArg.getString(ctx, "player"));
                        if (p == null) { out("MISS", "player not found"); return 0; }
                        return doCost(ctx, p, "plain");
                    }))
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, "cost", function () {
                            var p = findPlayer(ctx, StringArg.getString(ctx, "player"));
                            if (p == null) { out("MISS", "player not found"); return 0; }
                            return doCost(ctx, p, StringArg.getString(ctx, "tag"));
                        })))))
            .then(Commands.literal("true")
                .then(Commands.argument("player", StringArg.word())
                    .executes(ctx => guard(ctx, "true", function () {
                        var p = findPlayer(ctx, StringArg.getString(ctx, "player"));
                        if (p == null) { out("MISS", "player not found"); return 0; }
                        return doTrue(ctx, p, "plain");
                    }))))
            .then(Commands.literal("berserk")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("amp", IntegerArg.integer(0, 2))
                        .executes(ctx => guard(ctx, "berserk", function () {
                            var p = findPlayer(ctx, StringArg.getString(ctx, "player"));
                            if (p == null) { out("MISS", "player not found"); return 0; }
                            return doBerserk(ctx, p, IntegerArg.getInteger(ctx, "amp"), "plain");
                        })))))
            .then(Commands.literal("src")
                .then(Commands.argument("player", StringArg.word())
                    .executes(ctx => guard(ctx, "src", function () {
                        var p = findPlayer(ctx, StringArg.getString(ctx, "player"));
                        if (p == null) { out("MISS", "player not found"); return 0; }
                        return doSrc(ctx, p);
                    }))))
            .then(Commands.literal("hp")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("value", IntegerArg.integer(1, 40))
                        .executes(ctx => guard(ctx, "hp", function () {
                            var p = findPlayer(ctx, StringArg.getString(ctx, "player"));
                            if (p == null) { out("MISS", "player not found"); return 0; }
                            return doHp(ctx, p, IntegerArg.getInteger(ctx, "value"));
                        })))))
            .then(Commands.literal("fn")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("dmg", IntegerArg.integer(1, 40))
                        .then(Commands.argument("hp", IntegerArg.integer(0, 40))
                            .executes(ctx => guard(ctx, "fn", function () {
                                var p = findPlayer(ctx, StringArg.getString(ctx, "player"));
                                if (p == null) { out("MISS", "player not found"); return 0; }
                                return doFn(ctx, p, IntegerArg.getInteger(ctx, "dmg"),
                                    IntegerArg.getInteger(ctx, "hp"));
                            }))))))
    );
});
