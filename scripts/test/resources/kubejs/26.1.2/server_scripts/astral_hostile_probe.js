// ════════════════════════════════════════════════════════════════════════════
//  astral_hostile_probe.js —— 26.1.2 (NeoForge)
//  「敌对目标」唯一入口的实机真值表取证
//
//  为什么单独成文件(而不是加进 astral_bugfix_probe.js):
//    · 后者头部明确写着「由一次性脚本生成,请勿手工分叉修改」,三线同改易踩坑;
//    · 本探针**只碰一个纯函数**(前置库 `combat.HostileTargets#isHostile(Entity)`),
//      不依赖骰战/赐福/Curios 任何玩法状态,可以独立演进。
//
//  被测对象(前置库 starengine_lib,2026-09-27 新增的第 3 条通道):
//    敌对目标 = 敌对生物(Enemy) ∪ 中立生物(宠物除外) ∪ **会被激怒的可驯服动物(宠物除外)**
//             ∪ 消费方额外声明的实体
//    第 3 条覆盖「可驯服、被打会还击,却没实现 NeutralMob」的动物 ——
//    原版唯一实例 = **羊驼 / 行商羊驼(Llama / TraderLlama)**。
//    判据 = `Mob` ∧ `OwnableEntity#getOwner()==null` ∧ 非已驯服 TamableAnimal
//           ∧ (`Mob#getTarget()!=null` ∨ `LivingEntity#getLastHurtByMob()!=null`)
//
//  为什么必须自建:全仓(含 scripts/test/resources/kubejs/**)此前**零处**引用
//  `HostileTargets`,该改动没有任何自动断言入口(2026-09-28 实测 grep)。
//
//  判据设计(反假绿):
//    · 正例:`llama` **已被激怒**(setTarget / setLastHurtByMob)⇒ 必须 1;`trader_llama` 同;
//    · 负例①`llama` **未被激怒** ⇒ 必须 0 —— 证明「会被激怒」这条门槛真的在判,
//      而不是「只要是 OwnableEntity 就算敌对」;
//    · 负例②`cow`(Mob 但非 OwnableEntity)⇒ 0;`villager` ⇒ 0;
//      **`goat` / `panda` + 激怒 ⇒ 也必须 0** —— 把「会被激怒」与「可驯服(OwnableEntity)」
//      两个条件解耦,是「判据没有退化成『只要被激怒就算敌对』」的唯一硬证据;
//    · 负例③**已驯服**的 `wolf` 走 `isTamedPet` 被排除 ⇒ 0,
//      而**未驯服**的 `wolf` 经 NeutralMob 通道 ⇒ 1(两条通道的对照);
//    · 正例对照 `zombie`(Enemy)⇒ 1。
//
//  平台差异(2026-09-28 三线 merged jar `javap` 实证;此前两格对照因此失效):
//    | 平台    | `TamableAnimal#setTame`          | 设 owner                              |
//    |---------|----------------------------------|---------------------------------------|
//    | 1.20.1  | `setTame(boolean)`               | `setOwnerUUID(java.util.UUID)`        |
//    | 1.21.1  | **仅** `setTame(boolean,boolean)` | `setOwnerUUID(java.util.UUID)`        |
//    | 26.1.2  | `setTame(boolean, boolean)`      | ⚠️ 已删 ⇒ `setOwner(LivingEntity)`   |
//    故:`setTame` **两参优先、单参兜底**;设 owner 由各线自己选形态。
//
//  ⚠️ 环境事实(2026-09-28 实机取证,对所有探针通用;读数见 `/astralhostile mprobe`):
//    **`Entity#getUUID()` / `#getStringUUID()` 在 KubeJS 里被整体挡掉** ——
//    对 `ServerPlayer` 报 `TypeError: Cannot find function getUUID in object ServerPlayer[...]`,
//    而对**新 spawn 的 `Cow`** 报**完全相同**的错 ⇒ 是这两个**成员名**被挡,与实体类型无关
//    (三线 merged jar 的 `javap` 证明二者在 `net.minecraft.world.entity.Entity` 上是 `public`
//     ⇒ 这是 KubeJS 侧的白名单限制,不是 Java 层缺失)。
//    可用的替代:
//      · **非玩家实体** → `getScoreboardName()` 返回该实体的 **UUID 字符串**
//        (原版 `Entity#getScoreboardName()` 默认即 `getStringUUID()`;实测 `Cow` ⇒ `38e24bf8-…`)。
//      · **玩家** → `getScoreboardName()` 被覆写为**玩家名**(实测 `Dev`)⇒ 取玩家 UUID
//        只能走 `getGameProfile().getId()`(authlib 的 `GameProfile`,不受 MC 类包装影响)。
//    ⇒ 需要玩家 UUID 时**必须**走 `htPlayerUUID()`,它的候选表与既有探针
//      `astral_bugfix_probe.js#playerUuid` 的「三级回退」同口径
//      (`getUUID()` → `p.uuid` → `UUIDUtil.uuidFromIntArray(p.nbt…)`;本探针另补 P4 authlib 路径),
//      并把**每一级的结果**回显 —— 绝不静默降级(实测 1.21.1 上 **P2 命中**)。
//
//  本探针不做静默降级 —— 任何调用失败都把**异常单行文本**打进读数(`ang=…[T=!java.lang…]`)。
//
//  读数(单行,确定性顺序):
//    AP_<tag>_HT:cls=<ok|ERR:..>:cow=<0|1|ERR>:llama_idle=<..>:llama_tgt=<..>:llama_hurt=<..>
//                :trader_tgt=<..>:horse_tgt=<..>:wolf=<..>:wolf_tame=<..>:zombie=<..>:villager=<..>
//                :goat_tgt=<..>:panda_tgt=<..>:llama_owned=<..>:detail=<...>
//    AP_<tag>_HTD:<id>:tgt=<0|1|err>:hurt=<0|1|err>[:ang=<机制回显>][:isTame=…][:hasOwner=…]
//    AP_<tag>_HTU:<htPlayerUUID 的候选表回显>
//    AP_<tag>_MP:<成员可见性诊断>
//    AP_<tag>_DONE
//
//  机制回显(ang=)语法:逐个调用的成功字母**连写**(如 `TH`),失败的单独展开为
//    `[T=!<异常单行>;H=!<异常单行>]`。⇒ 「没生效却以为生效了」在读数里一眼可辨。
// ════════════════════════════════════════════════════════════════════════════

var _htCache = {};

// 安全类(三线同名,直接顶层加载;取值方式沿用 astral_bugfix_probe.js 的 StringArg.getString)
var HtComponent = Java.loadClass("net.minecraft.network.chat.Component");
var HtStringArg = Java.loadClass("com.mojang.brigadier.arguments.StringArgumentType");

/** 延迟加载:任何一次 loadClass 失败都只影响该命令,不会让整个文件加载失败 */
function htLoad(name) {
    if (_htCache[name] !== undefined) return _htCache[name];
    var v = null;
    try { v = Java.loadClass(name); } catch (e) { v = null; }
    _htCache[name] = v;
    return v;
}

function htSend(ctx, text) {
    try { ctx.source.sendFailure(HtComponent.literal(text)); } catch (e) { /* 回显失败不影响判定 */ }
}

function htEx(e) {
    var msg;
    try { msg = "" + e; } catch (x) { msg = "<unprintable>"; }
    msg = ("" + msg).replace(/\r?\n/g, " ");
    return (msg.length > 160) ? (msg.substring(0, 160) + "…") : msg;
}

function htGuard(ctx, tag, fn) {
    try {
        return fn();
    } catch (err) {
        htSend(ctx, "AP_" + tag + "_EX:" + htEx(err));
        return 0;
    }
}

/** 唯一被测入口。返回 0 / 1 / "ERR:..." —— 异常绝不吞掉 */
function htIsHostile(entity, tag) {
    var HT = htLoad("com.merlinkitsune.starenginelib.combat.HostileTargets");
    if (HT === null) return "ERR:HT_class_not_found";
    try { return HT.isHostile(entity) ? 1 : 0; } catch (e) { return "ERR:" + htEx(e); }
}

/**
 * 单次调用尝试:成功把 `letter` 记进 ok;失败把 `letter=!<异常单行>` 记进 bad。
 * 目的:① 吸收平台差异的方法重载(先试 A 再试 B);② 让「没生效」与「生效了」可区分。
 */
function htTry(fn, letter, ok, bad) {
    try { fn(); ok.push(letter); return true; }
    catch (err) { bad.push(letter + "=!" + htEx(err)); return false; }
}

/** ok 连写(如 `TH`);仅当有失败时才展开 `[…=!异常]`(与旧读数格式兼容)。 */
function htMark(ok, bad) {
    return ok.join("") + (bad.length ? "[" + bad.join(";") + "]" : "");
}

/**
 * 取玩家 UUID —— 绕开「KubeJS 不暴露 `Player#getUUID()`」的环境限制(见文件头)。
 *
 * <p>⚠️ **口径必须与既有探针一致**:`astral_bugfix_probe.js#playerUuid` 早已记录了同一个坑,
 * 并用「三级回退」解决 —— 本函数**逐级照抄该口径**(不另造新路),只在最后多补一条 authlib 路径。
 * 之所以不直接 `p.getUUID()`:KubeJS 的方法白名单里没有 `ServerPlayer#getUUID`,
 * 一旦真需要它就会抛 `Cannot find function getUUID`(2026-09-28 实测,曾在 bugfix 探针里
 * 造成 4 条断言假红)。
 *
 * <p>候选表(任一成功即停),返回 `{uuid, how}`:
 *   P1 `p.getUUID()`                                              —— 已知不可见,保留取证
 *   P2 `p.uuid`                                                   —— 既有回退主路径(1.21.1 实测命中)
 *   P3 `UUIDUtil.uuidFromIntArray(p.nbt.getIntArray("UUID"))`      —— 既有回退兜底
 *   P4 `p.getGameProfile().getId()`                                —— authlib 类(本探针新增)
 * `how` 恒带**每条候选的结果**(`P2` / `[P1=!…;P3=!…];P2=ok`),失败绝不静默 ——
 * 于是「哪一级命中」这一既有探针从未暴露的信息,在本探针的读数里是可见的。
 */
function htPlayerUUID(p) {
    var ok = [], bad = [];
    var got = null;

    function take(letter, fn) {
        if (got !== null) return;
        var v = null;
        try { v = fn(); } catch (err) { bad.push(letter + "=!" + htEx(err)); return; }
        if (v === null || v === undefined) { bad.push(letter + "=null"); return; }
        got = v; ok.push(letter);
    }

    take("P1", function () { return p.getUUID(); });
    take("P2", function () { return p.uuid; });
    take("P3", function () {
        return htLoad("net.minecraft.core.UUIDUtil").uuidFromIntArray(p.nbt.getIntArray("UUID"));
    });
    take("P4", function () { return p.getGameProfile().getId(); });

    return { uuid: got, how: htMark(ok, bad) };
}

/** 实体类型 → 实体实例(在玩家附近环形散开,避免互相挤压/碰撞剔除) */
function htSpawn(p, typeId, idx) {
    var BIR = htLoad("net.minecraft.core.registries.BuiltInRegistries");
    var RL = htLoad("net.minecraft.resources.Identifier");
    // 26.1.2:① ResourceLocation→Identifier(仅 FQN 变);
    //         ② Registry#get(id) 语义已变为 Optional<Holder.Reference<T>> ⇒ 必须用 getValue(id);
    //         ③ EntityType#create(Level) 单参重载已删除 ⇒ 必须带 EntitySpawnReason。
    var type = BIR.ENTITY_TYPE.getValue(RL.parse(typeId));
    var e = type.create(p.level, htLoad("net.minecraft.world.entity.EntitySpawnReason").MOB_SUMMONED);
    if (e == null) return null;
    var a = idx * 0.9;
    e.setPos(p.getX() + 3.0 + (idx % 3) * 1.6, p.getY(), p.getZ() + 2.0 + Math.floor(idx / 3) * 1.8 + a);
    try { e.setNoAi(true); } catch (e1) { /* 忽略 */ }
    try { e.setPersistenceRequired(); } catch (e2) { /* 忽略 */ }
    p.level.addFreshEntity(e);
    return e;
}

/**
 * 把动物置成「会被激怒」。
 * `setTarget` 与 `setLastHurtByMob` 都声明在父类(Mob / LivingEntity)上,而本探针已在
 * `astral_bugfix_probe.js` 的 meleeHit 注释里实证「声明在父类的方法在 Rhino 直接成员查找下
 * 可能不可见」⇒ 两条都试,并把**实际生效的机制**回显出来(ang=…)。
 * 判据仍以 `getTarget`/`getLastHurtByMob` 的**读回值**为准(见 htAngerRead)。
 */
function htAnger(p, mob) {
    var ok = [], bad = [];
    htTry(function () { mob.setTarget(p); }, "T", ok, bad);
    htTry(function () { mob.setLastHurtByMob(p); }, "H", ok, bad);
    return htMark(ok, bad);
}

function htAngerRead(mob) {
    var t = "err", h = "err";
    try { t = (mob.getTarget() == null) ? 0 : 1; } catch (e1) { t = "err"; }
    try { h = (mob.getLastHurtByMob() == null) ? 0 : 1; } catch (e2) { h = "err"; }
    return { t: t, h: h };
}

/**
 * 成员可见性诊断 —— 一次性回答「KubeJS 在这条线上到底暴露了哪些成员」。
 * 纯诊断,不参与任何断言;用于定位「同类限制」的边界(Player 专属? 还是成员名被全局屏蔽?)。
 */
function htMProbe(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var e = htSpawn(p, "minecraft:cow", 0);
    var out = [];

    function vis(letter, fn) {
        try {
            var v = fn();
            if (v === null || v === undefined) { out.push(letter + "=null"); return; }
            var s;
            try { s = "" + v; } catch (x) { s = "<unprintable>"; }
            s = s.replace(/\r?\n/g, " ");
            if (s.length > 48) s = s.substring(0, 48) + "…";
            out.push(letter + "=ok(" + s + ")");
        } catch (err) { out.push(letter + "=!" + htEx(err)); }
    }

    vis("p_getUUID", function () { return p.getUUID(); });
    vis("p_getStringUUID", function () { return p.getStringUUID(); });
    vis("p_getScoreboardName", function () { return p.getScoreboardName(); });
    vis("p_getGameProfile", function () { return p.getGameProfile(); });
    vis("p_gp_getId", function () { return p.getGameProfile().getId(); });
    vis("p_getName", function () { return p.getName().getString(); });
    vis("p_level_getServer", function () { return p.level.getServer(); });
    vis("p_getX", function () { return p.getX(); });
    vis("e_getUUID", function () { return (e == null) ? "NOSPAWN" : e.getUUID(); });
    vis("e_getStringUUID", function () { return (e == null) ? "NOSPAWN" : e.getStringUUID(); });
    vis("e_getScoreboardName", function () { return (e == null) ? "NOSPAWN" : e.getScoreboardName(); });
    vis("uuidc", function () { return (htLoad("java.util.UUID") === null) ? "NULL" : "loaded"; });
    vis("pu", function () { return htPlayerUUID(p).how; });

    try { if (e != null) e.discard(); } catch (d) { /* 忽略 */ }
    htSend(ctx, "AP_" + tag + "_MP:" + out.join(":"));
    return 1;
}

/**
 * 主命令:一次跑完整张真值表。
 *
 * 顺序刻意固定(断言依赖):cow → llama_idle → llama_tgt → llama_hurt → trader_tgt →
 *                              horse_tgt → wolf → wolf_tame → zombie → villager → goat → panda → llama_owned
 */
function htTable(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var BIR = htLoad("net.minecraft.core.registries.BuiltInRegistries");
    var RL = htLoad("net.minecraft.resources.Identifier");
    var out = [];
    var detail = [];
    var spawned = [];
    var idx = 0;

    function cell(key, v) { out.push(key + "=" + v); }

    function add(entity, id) {
        spawned.push(entity);
        detail.push("AP_" + tag + "_HTD:" + id + ":" + htAngerReadText(entity));
        return entity;
    }

    function htAngerReadText(mob) {
        var r = htAngerRead(mob);
        return "tgt=" + r.t + ":hurt=" + r.h;
    }

    // 0) 类可达性(最先:类加载失败时后续全部 ERR,一眼可辨)
    var HT = htLoad("com.merlinkitsune.starenginelib.combat.HostileTargets");
    cell("cls", (HT === null) ? "ERR:not_found" : "ok");

    // 1) cow —— Mob 但不是 OwnableEntity ⇒ 负例
    var e = null;
    try {
        e = htSpawn(p, "minecraft:cow", idx++);
        cell("cow", (e == null) ? "ERR:spawn" : htIsHostile(e, tag));
        if (e != null) add(e, "cow");
    } catch (er) { cell("cow", "ERR:" + htEx(er)); }

    // 2) llama 未被激怒 ⇒ **负例①(本轮改动最关键的反假绿)**
    try {
        e = htSpawn(p, "minecraft:llama", idx++);
        cell("llama_idle", (e == null) ? "ERR:spawn" : htIsHostile(e, tag));
        if (e != null) add(e, "llama_idle");
        if (e != null) { try { e.discard(); } catch (d1) { /* 忽略 */ } }
    } catch (er) { cell("llama_idle", "ERR:" + htEx(er)); }

    // 3) llama + setTarget ⇒ 正例(第 3 条通道)
    try {
        e = htSpawn(p, "minecraft:llama", idx++);
        if (e == null) { cell("llama_tgt", "ERR:spawn"); }
        else {
            var how = htAnger(p, e);
            var r = htAngerRead(e);
            cell("llama_tgt", htIsHostile(e, tag));
            detail.push("AP_" + tag + "_HTD:llama_tgt:tgt=" + r.t + ":hurt=" + r.h + ":ang=" + how);
        }
        if (e != null) { spawned.push(e); try { e.discard(); } catch (d2) { /* 忽略 */ } }
    } catch (er) { cell("llama_tgt", "ERR:" + htEx(er)); }

    // 4) llama + setLastHurtByMob ⇒ 正例(同一判据的另一半)
    try {
        e = htSpawn(p, "minecraft:llama", idx++);
        if (e == null) { cell("llama_hurt", "ERR:spawn"); }
        else {
            var okH = [], badH = [];
            htTry(function () { e.setLastHurtByMob(p); }, "H", okH, badH);
            var r2 = htAngerRead(e);
            cell("llama_hurt", htIsHostile(e, tag));
            detail.push("AP_" + tag + "_HTD:llama_hurt:tgt=" + r2.t + ":hurt=" + r2.h + ":ang=" + htMark(okH, badH));
        }
        if (e != null) { spawned.push(e); try { e.discard(); } catch (d3) { /* 忽略 */ } }
    } catch (er) { cell("llama_hurt", "ERR:" + htEx(er)); }

    // 5) trader_llama + 激怒 ⇒ 正例(行商羊驼同属第 3 条)
    try {
        e = htSpawn(p, "minecraft:trader_llama", idx++);
        if (e == null) { cell("trader_tgt", "ERR:spawn"); }
        else {
            var how3 = htAnger(p, e);
            var r3 = htAngerRead(e);
            cell("trader_tgt", htIsHostile(e, tag));
            detail.push("AP_" + tag + "_HTD:trader_tgt:tgt=" + r3.t + ":hurt=" + r3.h + ":ang=" + how3);
        }
        if (e != null) { spawned.push(e); try { e.discard(); } catch (d4) { /* 忽略 */ } }
    } catch (er) { cell("trader_tgt", "ERR:" + htEx(er)); }

    // 6) horse + 激怒 ⇒ **信息性**(AbstractHorse 也实现 OwnableEntity ⇒ 会被同一判据连带纳入,
    //    属设计备注而非缺陷;此处只记录实际值,不断言)
    try {
        e = htSpawn(p, "minecraft:horse", idx++);
        if (e == null) { cell("horse_tgt", "ERR:spawn"); }
        else {
            var how4 = htAnger(p, e);
            var r4 = htAngerRead(e);
            cell("horse_tgt", htIsHostile(e, tag));
            detail.push("AP_" + tag + "_HTD:horse_tgt:tgt=" + r4.t + ":hurt=" + r4.h + ":ang=" + how4);
        }
        if (e != null) { spawned.push(e); try { e.discard(); } catch (d5) { /* 忽略 */ } }
    } catch (er) { cell("horse_tgt", "ERR:" + htEx(er)); }

    // 7) wolf 未驯服 ⇒ NeutralMob 通道 ⇒ 1
    try {
        e = htSpawn(p, "minecraft:wolf", idx++);
        cell("wolf", (e == null) ? "ERR:spawn" : htIsHostile(e, tag));
        if (e != null) { spawned.push(e); try { e.discard(); } catch (d6) { /* 忽略 */ } }
    } catch (er) { cell("wolf", "ERR:" + htEx(er)); }

    // 8) wolf 已驯服 ⇒ isTamedPet 排除 ⇒ 0(反假绿:证明「宠物除外」真的在判)
    //    ⚠️ 平台差异:1.20.1 有 `setTame(boolean)`;1.21.1 / 26.1.2 **只剩** 两参重载。
    //       故两参优先、单参兜底,并把实际生效的那条回显出来(ang=T2 / T1)。
    try {
        e = htSpawn(p, "minecraft:wolf", idx++);
        if (e == null) { cell("wolf_tame", "ERR:spawn"); }
        else {
            var okT = [], badT = [];
            if (!htTry(function () { e.setTame(true, true); }, "T2", okT, badT)) {
                htTry(function () { e.setTame(true); }, "T1", okT, badT);
            }
            var isTameNow = "err";
            try { isTameNow = e.isTame() ? 1 : 0; } catch (t3) { isTameNow = "err:" + htEx(t3); }
            cell("wolf_tame", htIsHostile(e, tag));
            detail.push("AP_" + tag + "_HTD:wolf_tame:isTame=" + isTameNow + ":ang=" + htMark(okT, badT));
        }
        if (e != null) { spawned.push(e); try { e.discard(); } catch (d7) { /* 忽略 */ } }
    } catch (er) { cell("wolf_tame", "ERR:" + htEx(er)); }

    // 9) zombie ⇒ Enemy 通道 ⇒ 1(正对照)
    try {
        e = htSpawn(p, "minecraft:zombie", idx++);
        cell("zombie", (e == null) ? "ERR:spawn" : htIsHostile(e, tag));
        if (e != null) { spawned.push(e); try { e.discard(); } catch (d8) { /* 忽略 */ } }
    } catch (er) { cell("zombie", "ERR:" + htEx(er)); }

    // 10) villager ⇒ 0(负例:既非 Enemy 亦非以上任何一条)
    try {
        e = htSpawn(p, "minecraft:villager", idx++);
        cell("villager", (e == null) ? "ERR:spawn" : htIsHostile(e, tag));
        if (e != null) { spawned.push(e); try { e.discard(); } catch (d9) { /* 忽略 */ } }
    } catch (er) { cell("villager", "ERR:" + htEx(er)); }

    // 11) goat / panda 也被激怒 ⇒ **必须 0**。这是本探针最关键的反假绿:
    //     它把「会被激怒」与「OwnableEntity(可驯服)」两个条件**解耦** ——
    //     若判据退化成「只要被激怒就算敌对」,这两格会变成 1 而立刻暴露。
    //     (库口径明确把 熊猫/骆驼/山羊/海豚/狐狸 排除在外,见 HostileTargets 类注释。)
    try {
        e = htSpawn(p, "minecraft:goat", idx++);
        if (e == null) { cell("goat_tgt", "ERR:spawn"); }
        else {
            var howG = htAnger(p, e);
            var rG = htAngerRead(e);
            cell("goat_tgt", htIsHostile(e, tag));
            detail.push("AP_" + tag + "_HTD:goat_tgt:tgt=" + rG.t + ":hurt=" + rG.h + ":ang=" + howG);
        }
        if (e != null) { spawned.push(e); try { e.discard(); } catch (dG) { /* 忽略 */ } }
    } catch (er) { cell("goat_tgt", "ERR:" + htEx(er)); }

    try {
        e = htSpawn(p, "minecraft:panda", idx++);
        if (e == null) { cell("panda_tgt", "ERR:spawn"); }
        else {
            var howP = htAnger(p, e);
            var rP = htAngerRead(e);
            cell("panda_tgt", htIsHostile(e, tag));
            detail.push("AP_" + tag + "_HTD:panda_tgt:tgt=" + rP.t + ":hurt=" + rP.h + ":ang=" + howP);
        }
        if (e != null) { spawned.push(e); try { e.discard(); } catch (dP) { /* 忽略 */ } }
    } catch (er) { cell("panda_tgt", "ERR:" + htEx(er)); }

    // 12) llama 已驯服(设 owner) ⇒ **信息性**:期望 0
    //     (第 3 条通道靠 `OwnableEntity#getOwner() != null` 排除,与 `TamableAnimal#isTame()` 是两套)
    //     ⚠️ 平台差异:1.20.1 / 1.21.1 用 `setOwnerUUID(UUID)`;26.1.2 已删该方法
    //        (OwnableEntity → EntityReference 体系)⇒ 本线用 `setOwner(LivingEntity)`,
    //        **直接传玩家对象、无需 UUID** ⇒ 天然绕开文件头「环境事实」里的方法白名单限制。
    try {
        e = htSpawn(p, "minecraft:llama", idx++);
        if (e == null) { cell("llama_owned", "ERR:spawn"); }
        else {
            var okO = [], badO = [];
            htTry(function () { e.setOwner(p); }, "O", okO, badO);
            var owned = "err";
            try { owned = (e.getOwner() == null) ? 0 : 1; } catch (o2) { owned = "err:" + htEx(o2); }
            cell("llama_owned", htIsHostile(e, tag));
            detail.push("AP_" + tag + "_HTD:llama_owned:hasOwner=" + owned + ":ang=" + htMark(okO, badO));
        }
        if (e != null) { spawned.push(e); try { e.discard(); } catch (d10) { /* 忽略 */ } }
    } catch (er) { cell("llama_owned", "ERR:" + htEx(er)); }

    var killed = 0;
    for (var i = 0; i < spawned.length; i++) {
        try { spawned[i].discard(); killed++; } catch (k1) { /* 忽略 */ }
    }
    cell("detail", "spawned=" + idx + ":killed=" + killed);
    htSend(ctx, "AP_" + tag + "_HT:" + out.join(":"));
    for (var j = 0; j < detail.length; j++) htSend(ctx, detail[j]);
    htSend(ctx, "AP_" + tag + "_DONE");
    return 1;
}

ServerEvents.commandRegistry(event => {
    var Commands = event.commands;
    event.register(
        Commands.literal("astralhostile")
            .then(Commands.literal("tbl")
                .then(Commands.argument("tag", HtStringArg.word())
                    .executes(ctx => htGuard(ctx, HtStringArg.getString(ctx, "tag"), function () {
                        return htTable(ctx, HtStringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("mprobe")
                .then(Commands.argument("tag", HtStringArg.word())
                    .executes(ctx => htGuard(ctx, HtStringArg.getString(ctx, "tag"), function () {
                        return htMProbe(ctx, HtStringArg.getString(ctx, "tag"));
                    }))))
    );
});
