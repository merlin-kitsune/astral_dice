// ════════════════════════════════════════════════════════════════════════════
//  astral_fixbatch_probe.js —— 1.21.1 (NeoForge)「近期改动」定向取证探针
//
//  服务于 scripts/test/cases/FIXBATCH-*-1.21.1.json 五条用例（2026-10-22 批，
//  2026-10-22 第二轮按源码事实修正）。
//  口径与既有探针完全一致：
//    · 有命令上下文 → ctx.source.sendFailure(Component) ⇒ 客户端 latest.log 的 [CHAT] 行；
//    · 无命令上下文（tick 回调）→ ServerPlayer#sendSystemMessage ⇒ 同一通道。
//  所有机器行一律 `AP_FX_<WHAT>:k=v:k=v`（前缀固定 AP_FX_，便于 grep 且不与
//  AP_CL_ / AP_<tag>_ 等既有前缀串台）。
//
//  命令一览（用例依赖这些名字与参数顺序，改动必须同步改用例）：
//    /astralfixbatch hostiles  <tag>        摆 1 对**敌对非亡灵**靶（pillager；主靶 +3 格、
//                                           邻靶 +1.5 格 ⇒ 必在电击 AOE_RADIUS=3 内），
//                                           并 setTarget(执行者) —— 强化拒止的吸附资格
//                                           （entity/RepulsionEntity#isAttachable）要求
//                                           `Enemy && getTarget()!=null`，无仇恨的敌对生物不吸附。
//    /astralfixbatch cows      <tag>        hostiles 的旧别名（保留兼容，行为同 hostiles）
//    /astralfixbatch cast      <tag>        服务端确认目标选择（主手持伤害效果牌）
//    /astralfixbatch gread     <tag>        只读：电击手套 冷却/充能/两靶掉血
//    /astralfixbatch repplace  <tag>        摆一座强化拒止力场（owner = 执行者）
//    /astralfixbatch rread     <tag>        只读：力场 chargeGiven / 吸附数 / 生物距离 / 仇恨 / 玩家充能
//    /astralfixbatch repclear  <tag>        清场：discard 玩家 32 格内全部 astral_dice:repulsion
//    /astralfixbatch endspawn  <tag>        摆 1 只末影人（tag afb_ender + setTarget(执行者)）
//    /astralfixbatch ewatch    <tag> <ticks> 监视末影人到力场的最大距离（默认 100t；
//                                           中途连打 3 次无来源伤害逼它闪现 ⇒ 判 RepulsionTeleportGuard）
//    /astralfixbatch asurre    <tag>        只读：附近 afb_target 的 isAlive / getTarget / 到力场距离
//    /astralfixbatch ticket    <tag>        使用 1 张事件卡（主手 event_ticket）
//    /astralfixbatch ticket2  <tag>         **同一 tick 内**连用事件卡两次（Δt=0 ⇒ 第二张必撞闸门）
//    /astralfixbatch gatereset <tag>        GlobalEventGate.reset()（等价服务器重启后的干净闸门）
//    /astralfixbatch gate0     <tag>        **时长为 0** 的瞬时事件三连（learn_from_others，NOOP 效果）：
//                                           t0 成功 → +20t 被拒 → +88t(=58+30) 成功
//    /astralfixbatch card3     <tag>        连发 3 张同款卡 ⇒ 报「占几格 / 共几张」
//    /astralfixbatch drawerblank <tag>      抽奖盒投入空白筹码两次 ⇒ 报手牌数与返回码
//
//  ⚠️ 本探针**只读为主**：除 `hostiles` / `endspawn` / `repplace` / `repclear` / `drawerblank`
//     （摆靶 / 摆力场 / 清场 / 摆方块，都是测试存档内的布景）与 `gatereset`（清闸门状态）外
//     不写任何产品状态；不改模组源码。
//  ⚠️ 1.20.1 差异：本文件由 1.21.1 逐字复制，用到的入口
//     （EntityType#create / ItemStack#use / ServerPlayerGameMode#useItemOn /
//      RepulsionEntity 访问器 / ChargeManager / TargetSelectionManager）两线签名一致；
//      p.level 两线都是**属性**。
//  ⚠️ 三个 Rhino 派发坑（2026-10-22 实机实证，已在本文件内绕开）：
//     · `player.getUUID()` → TypeError；改用 `getGameProfile().getId()`（力场 owner 必须能写进去，
//       否则 `awardCharge` 提前 return ⇒ cg 恒 0）；
//     · `BuiltInRegistries.ENTITY_TYPE.getKey(type)` → 恒 `minecraft:pig`；改用 `'' + type`；
//     · `EntityType#toShortString()` / `#getDescriptionId()` → TypeError。
//     ⚠️ 2026-10-22 第二轮只改了 1.21.1 这一份（本轮只复跑 1.21.1）；1.20.1 副本仍为旧版
//        （用 cows 摆 cow 靶 + 无 gate0/hostiles 命令），两线此刻**不逐字一致**，已登记。
// ════════════════════════════════════════════════════════════════════════════

function fbxTryClass(n) { try { return Java.loadClass(n); } catch (e) { return null; } }

var FBX_Component = fbxTryClass('net.minecraft.network.chat.Component');
var FBX_ItemStack = fbxTryClass('net.minecraft.world.item.ItemStack');
var FBX_StringArg = fbxTryClass('com.mojang.brigadier.arguments.StringArgumentType');
var FBX_IntArg = fbxTryClass('com.mojang.brigadier.arguments.IntegerArgumentType');
var FBX_Vec3 = fbxTryClass('net.minecraft.world.phys.Vec3');
var FBX_BlockHitResult = fbxTryClass('net.minecraft.world.phys.BlockHitResult');
var FBX_Direction = fbxTryClass('net.minecraft.core.Direction');
var FBX_Hand = fbxTryClass('net.minecraft.world.InteractionHand');
var FBX_EntityType = fbxTryClass('net.minecraft.world.entity.EntityType');
var FBX_Mob = fbxTryClass('net.minecraft.world.entity.Mob');
var FBX_Reg = fbxTryClass('net.minecraft.core.registries.BuiltInRegistries');

var FBX_ModEntityTypes = fbxTryClass('com.merlinkitsune.astral_dice.entity.ModEntityTypes');
var FBX_RepulsionEntity = fbxTryClass('com.merlinkitsune.astral_dice.entity.RepulsionEntity');
var FBX_ChargeManager = fbxTryClass('com.merlinkitsune.astral_dice.item.ChargeManager');
var FBX_Glove = fbxTryClass('com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem');
var FBX_TargetSel = fbxTryClass('com.merlinkitsune.astral_dice.target.TargetSelectionManager');
var FBX_ModItems = fbxTryClass('com.merlinkitsune.astral_dice.item.ModItems');
var FBX_ModBlocks = fbxTryClass('com.merlinkitsune.astral_dice.block.ModBlocks');

// 事件闸门三连（gate0）用到的入口
var FBX_GlobalEventGate = fbxTryClass('com.merlinkitsune.astral_dice.event.GlobalEventGate');
var FBX_Dispatcher = fbxTryClass('com.merlinkitsune.astral_dice.event.AstralEventDispatcher');
var FBX_EventIds = fbxTryClass('com.merlinkitsune.astral_dice.event.AstralEventIds');
var FBX_TriggerType = fbxTryClass('com.merlinkitsune.astral_dice.event.api.AstralEventTrigger');

/** 靶子自定义 tag（用例里的 /kill @e[tag=...] 收尾用同一串） */
var FBX_TARGET_TAG = 'afb_target';
var FBX_END_TAG = 'afb_ender';

/** 力场实体注册名（entity/ModEntityTypes 的 `repulsion`）—— 清场命令用的同一串 */
var FBX_REP_TYPE_ID = 'astral_dice:repulsion';

/** gate0 时序（tick）：t0 成功；+20t 被拒；+88t = (DELAY_TICKS 38 + GAP_TICKS 20) + 30t(1.5s 余量) 成功 */
var FBX_GATE0_ATTEMPT2 = 20;
var FBX_GATE0_ATTEMPT3 = 88;

/** ewatch 中途「逼闪现」的取样点（第 40/41/42 个样本）与伤害点数 */
var FBX_EWATCH_HIT_AT = 40;
var FBX_EWATCH_HIT_N = 3;

/** 模块级基线（一次用例内有效；跨用例会互相覆盖 ⇒ 每条用例开头都要重跑 hostiles） */
var FBX_STATE = { targets: [], hp0: [], castTag: '', watch: null, gate0: null };

// ── 输出 ──────────────────────────────────────────────────────────────────
function fbxSend(ctx, text) {
    try { ctx.source.sendFailure(FBX_Component.literal(text)); } catch (e) { /* 回显失败不影响判定 */ }
}

function fbxEmit(p, text) {
    if (p == null) return;
    try { p.sendSystemMessage(FBX_Component.literal(text)); } catch (e) { /* 忽略 */ }
}

function fbxEx(e) {
    var s;
    try { s = '' + e; } catch (x) { s = '<unprintable>'; }
    return s.split('\n')[0];
}

function fbxGuard(ctx, tag, fn) {
    try { return fn(); } catch (err) {
        fbxSend(ctx, 'AP_FX_' + tag + '_EX:' + fbxEx(err));
        return 0;
    }
}

function fbxP(ctx) { return ctx.source.getPlayerOrException(); }

function fbxRound(v) { return Math.round(v * 100) / 100; }

/**
 * 两点距离（sqrt 版）。⚠️ 2026-10-22 1.20.1 实机实证：`Entity#distanceToSqr(double,double,double)`
 * 在 forge-1.20.1 的 Rhino 里派发不到（`InternalError: Can't find method … distanceToSqr(number,number,number)`；
 * 1.21.1 侧同一写法可用）⇒ 两线统一改为手算 dx/dy/dz：否则 `ewatch` 的监视器每 tick 抛异常被
 * catch 静默吞掉（samples=0 / left 不推进），只在实体死亡时吐一行 alive=0，判据全废。
 */
function fbxDist(e, x, y, z) {
    var dx = e.getX() - x; var dy = e.getY() - y; var dz = e.getZ() - z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

/**
 * 实体类型 id。⚠️ 2026-10-22 实测：`BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())`
 * 在 Rhino 里对**任何**实体都返回 `minecraft:pig`（同一次实机里 `/summon minecraft:enderman`
 * 与 `EntityType.ENDERMAN.create` 两条路径都读到 pig）⇒ 该路径不可用于断言。
 * 改用 `EntityType#toShortString()`（原版就返回 `minecraft:xxx`），保留旧路径做对照回显。
 */
function fbxId(e) {
    // ⚠️ 实测（2026-10-22）：`BuiltInRegistries.ENTITY_TYPE.getKey(type)` 恒返回 `minecraft:pig`
    //    （末影人也被读成 pig）；`EntityType#toShortString` / `getDescriptionId` 在 Rhino 里
    //    派发不到（TypeError）。唯一可信路径 = `EntityType#toString()`，实测返回 `minecraft:enderman`。
    try { return ('' + e.getType()).replace(/\.$/, ''); } catch (x) { /* 下一路 */ }
    try { return '' + FBX_Reg.ENTITY_TYPE.getKey(e.getType()); } catch (x2) { return '?'; }
}

/** 诊断用：把四种取 id 的路径一起回显（确认哪一路可信） */
function fbxIdAll(e) {
    var a = 'EX'; var b = 'EX';
    try { a = '' + e.getType(); } catch (x1) { a = 'EX:' + fbxEx(x1); }
    try { b = '' + FBX_Reg.ENTITY_TYPE.getKey(e.getType()); } catch (x2) { b = 'EX:' + fbxEx(x2); }
    return 'ts=' + a + ',reg=' + b;
}

/**
 * 服务端 gameTime。与 GlobalEventGate / EventDrawBlock 同一口径（`level.getGameTime()`）。
 * ⚠️ Rhino 在本版本对 `ServerLevel#getGameTime` 的继承派发有历史坑（见 astral_bugfix_probe.js
 *    的 lvlDataGGT 注释）⇒ 先走 `getLevelData().getGameTime()`，再逐级回退。
 */
function fbxNow(p) {
    try { return p.level.getLevelData().getGameTime(); } catch (e1) { /* 下一路 */ }
    try { return p.level.getGameTime(); } catch (e2) { /* 下一路 */ }
    return -1;
}

/** 半径内带指定 scoreboard tag 的活体（按距离升序） */
function fbxNear(level, center, tag, radius, cls) {
    var box = center.getBoundingBox().inflate(radius);
    var list = level.getEntitiesOfClass(cls, box);
    var out = [];
    for (var i = 0; i < list.size(); i++) {
        var e = list.get(i);
        try {
            if (e.getTags().contains(tag)) out.push(e);
        } catch (x) { /* 忽略 */ }
    }
    return out;
}

function fbxCharge(p) {
    try { return FBX_ChargeManager.getStacks(p); } catch (e) { return -1; }
}

function fbxHp(e) {
    try { return fbxRound(e.getHealth()); } catch (x) { return -1; }
}

/** 该生物的仇恨目标是否非空（= RepulsionEntity#isAttachable 的第 ③ 条判据） */
function fbxHasTarget(e) {
    try { return e.getTarget() != null ? 1 : 0; } catch (x) { return -1; }
}

/** 清掉上一轮留下的靶（tag afb_target，24 格内） */
function fbxClearTargets(level, center) {
    var list = fbxNear(level, center, FBX_TARGET_TAG, 24, FBX_Mob);
    for (var j = 0; j < list.length; j++) { try { list[j].discard(); } catch (x1) { /* 忽略 */ } }
    FBX_STATE.targets = [];
    FBX_STATE.hp0 = [];
    return list.length;
}

// ── ① 摆靶：1 对 pillager（敌对非亡灵；主靶 +3 格，邻靶再 +1.5 格）────────────
//
//  为什么必须 setTarget：`entity/RepulsionEntity#isAttachable` 的判据是
//    `Enemy || NeutralMob` **且** `getTarget()!=null`（或中立生物 isAngry()）
//  ⇒ 没有仇恨目标的敌对生物（原版游荡僵尸 / 未激怒末影人）**不会被吸附**。
//  pillager = Monster → Enemy ✅；cow / pig = Animal ⇒ 既非 Enemy 也非 NeutralMob ⇒ 永不吸附。
function fbxDoHostiles(ctx, tag) {
    var p = fbxP(ctx);
    var level = p.level;
    fbxClearTargets(level, p);

    var main = null; var nbr = null;
    try {
        main = FBX_EntityType.PILLAGER.create(level);
        nbr = FBX_EntityType.PILLAGER.create(level);
    } catch (e1) { fbxSend(ctx, 'AP_FX_' + tag + '_EX:create:' + fbxEx(e1)); return 0; }
    if (main == null || nbr == null) { fbxSend(ctx, 'AP_FX_' + tag + '_EX:create-null'); return 0; }

    var mx = p.getX(); var my = p.getY(); var mz = p.getZ() + 3.0;
    try { main.setPos(mx, my, mz); } catch (e2) { /* 忽略 */ }
    try { nbr.setPos(mx + 1.5, my, mz); } catch (e3) { /* 忽略 */ }
    var pair = [main, nbr];
    for (var i = 0; i < pair.length; i++) {
        try { pair[i].setNoAi(true); } catch (e4) { /* 忽略 */ }
        try { pair[i].setPersistenceRequired(); } catch (e5) { /* 忽略 */ }
        try { pair[i].setHealth(pair[i].getMaxHealth()); } catch (e6) { /* 忽略 */ }
        try { pair[i].addTag(FBX_TARGET_TAG); } catch (e7) { /* 忽略 */ }
        // 仇恨：直接写 target（测试脚手架；NoAI 下不会被 goal 改写/清除）
        try { pair[i].setTarget(p); } catch (e9) { /* 忽略 */ }
        try { level.addFreshEntity(pair[i]); } catch (e8) { /* 忽略 */ }
        FBX_STATE.targets.push(pair[i]);
        FBX_STATE.hp0.push(fbxHp(pair[i]));
    }
    var dist = fbxRound(Math.sqrt((main.getX() - nbr.getX()) * (main.getX() - nbr.getX())
        + (main.getZ() - nbr.getZ()) * (main.getZ() - nbr.getZ())));
    fbxSend(ctx, 'AP_FX_' + tag + '_HOSTILES:main=' + main.getId() + ':nbr=' + nbr.getId()
        + ':dist=' + dist + ':mrk=' + FBX_STATE.targets.length
        + ':hp=' + FBX_STATE.hp0.join(',') + ':type=' + fbxId(main)
        + ':tg=' + fbxHasTarget(main) + ',' + fbxHasTarget(nbr));
    return 1;
}

// ── ② 出牌：服务端确认目标选择（主手伤害效果牌 ⇒ 会触发 ElectricGloveChipItem 调度）──
function fbxDoCast(ctx, tag) {
    var p = fbxP(ctx);
    var target = null;
    for (var i = 0; i < FBX_STATE.targets.length; i++) {
        var c = FBX_STATE.targets[i];
        if (c != null && c.isAlive()) { target = c; break; }
    }
    if (target == null) { fbxSend(ctx, 'AP_FX_' + tag + '_CAST:ex=no_target'); return 0; }
    var hand = '';
    try { hand = '' + FBX_Reg.ITEM.getKey(p.getMainHandItem().getItem()); } catch (e0) { hand = '?'; }
    var token = -1; var sel0 = -1; var called = 0; var err = '';
    try {
        token = FBX_TargetSel.sessionTokenForTests(p);
        sel0 = FBX_TargetSel.isSelecting(p) ? 1 : 0;
        FBX_TargetSel.confirm(p, token, target.getId());
        called = 1;
    } catch (e1) { err = fbxEx(e1); }
    FBX_STATE.castTag = tag;
    // cd=：`confirm` 与 `ElectricGloveChipItem#onEffectCardUsed` 同一次同步调用 ⇒ 这里读到的
    // 冷却状态就是「**调度时**置位」的证据（电击本身 20t 后才落地，此处 charge 仍为 0）
    var cdNow = -1;
    try { cdNow = FBX_Glove.isOnCooldown(p) ? 1 : 0; } catch (e9) { cdNow = -1; }
    fbxSend(ctx, 'AP_FX_' + tag + '_CAST:called=' + called + ':tok=' + token + ':sel0=' + sel0
        + ':tid=' + target.getId() + ':hand=' + hand + ':cd=' + cdNow + ':charge=' + fbxCharge(p)
        + (err ? ':err=' + err : ''));
    return 1;
}

// ── ③ 电击手套读数：冷却 / 充能 / 两靶各掉多少血 ─────────────────────────────
function fbxDoGread(ctx, tag) {
    var p = fbxP(ctx);
    var equip = -1; var cd = -1;
    try { equip = FBX_Glove.isEquipped(p) ? 1 : 0; } catch (e0) { equip = -1; }
    try { cd = FBX_Glove.isOnCooldown(p) ? 1 : 0; } catch (e1) { cd = -1; }
    var dmgs = [];
    for (var i = 0; i < FBX_STATE.targets.length; i++) {
        var c = FBX_STATE.targets[i];
        var hp = (c == null) ? -1 : fbxHp(c);
        var d = (i < FBX_STATE.hp0.length && FBX_STATE.hp0[i] >= 0 && hp >= 0)
            ? fbxRound(FBX_STATE.hp0[i] - hp) : -1;
        dmgs.push(d);
    }
    fbxSend(ctx, 'AP_FX_' + tag + '_GREAD:equip=' + equip + ':cd=' + cd + ':charge=' + fbxCharge(p)
        + ':dmg=' + (dmgs.length ? dmgs.join(',') : '-')
        + ':n=' + FBX_STATE.targets.length);
    return 1;
}

// ── ④ 强化拒止：摆一座力场（owner = 执行者，chargeGiven 才会记到玩家头上）──
function fbxDoRepPlace(ctx, tag) {
    var p = fbxP(ctx);
    var level = p.level;
    var rep = null;
    try {
        rep = FBX_ModEntityTypes.REPULSION.get().create(level);
    } catch (e0) { fbxSend(ctx, 'AP_FX_' + tag + '_EX:rep-create:' + fbxEx(e0)); return 0; }
    if (rep == null) { fbxSend(ctx, 'AP_FX_' + tag + '_EX:rep-null'); return 0; }
    var x = p.getX() + 3.0; var y = p.getY(); var z = p.getZ();
    var ownErr = ''; var ownBack = 'null';
    try { rep.setPos(x, y, z); } catch (e1) { /* 忽略 */ }
    // ⚠️ `p.getUUID()` 在 Rhino 里派发不到（实测 `TypeError: Cannot find function getUUID in object
    //    ServerPlayer[...]`，2026-10-22）⇒ 走已验证可用的 `getGameProfile().getId()`。
    try { rep.setOwnerUUID(p.getGameProfile().getId()); } catch (e2) { ownErr = fbxEx(e2); }
    try {
        var ob = rep.getOwnerUUID();
        ownBack = (ob == null) ? 'null' : ('' + ob);
    } catch (e2b) { ownBack = 'EX:' + fbxEx(e2b); }
    var added = 0;
    try { added = level.addFreshEntity(rep) ? 1 : 0; } catch (e3) { fbxSend(ctx, 'AP_FX_' + tag + '_EX:rep-add:' + fbxEx(e3)); return 0; }
    var selfId = 'EX';
    try { selfId = '' + p.getGameProfile().getId(); } catch (e4) { selfId = 'EX:' + fbxEx(e4); }
    fbxSend(ctx, 'AP_FX_' + tag + '_REP_PLACE:id=' + rep.getId() + ':added=' + added
        + ':at=' + fbxRound(x) + ',' + fbxRound(y) + ',' + fbxRound(z)
        + ':owner=' + (ownBack === 'null' ? 0 : 1)
        + ':ownerSet=' + (ownBack === 'null' ? 0 : (ownBack === selfId ? 1 : 2))
        + (ownErr ? ':ownEX=' + ownErr : ''));
    return 1;
}

// ── ⑤ 力场读数：本座 chargeGiven / 吸附数 / 最近靶到力场距离 / 靶仇恨 / 玩家充能 ──
function fbxDoRepRead(ctx, tag) {
    var p = fbxP(ctx);
    var level = p.level;
    var reps = null;
    try { reps = level.getEntitiesOfClass(FBX_RepulsionEntity, p.getBoundingBox().inflate(20.0)); }
    catch (e0) { fbxSend(ctx, 'AP_FX_' + tag + '_REP_READ:ex=' + fbxEx(e0)); return 0; }
    var n = reps.size();
    var parts = [];
    for (var i = 0; i < n; i++) {
        var r = reps.get(i);
        var cg = -1; var tr = -1;
        try { cg = r.chargeGiven(); } catch (e1) { cg = -1; }
        try { tr = r.getTrackedTargets().size(); } catch (e2) { tr = -1; }
        var d = -1;
        for (var j = 0; j < FBX_STATE.targets.length; j++) {
            var c = FBX_STATE.targets[j];
            if (c == null || !c.isAlive()) continue;
            var dd = fbxRound(fbxDist(r, c.getX(), c.getY(), c.getZ()));
            if (d < 0 || dd < d) d = dd;
        }
        // 诊断：12 格内**全部** Mob（不看 tag）= 判 cg/tr 是否被环境里的野怪污染
        var near = '';
        try {
            var box = r.getBoundingBox().inflate(12.0);
            var ms = level.getEntitiesOfClass(FBX_Mob, box);
            var bits = [];
            for (var k = 0; k < ms.size() && bits.length < 6; k++) {
                var m = ms.get(k);
                if (m == null) continue;
                var alive = 0;
                try { alive = m.isAlive() ? 1 : 0; } catch (ea) { alive = -1; }
                bits.push(fbxId(m) + '@' + fbxRound(fbxDist(r, m.getX(), m.getY(), m.getZ()))
                    + ':al=' + alive + ':tg=' + fbxHasTarget(m));
            }
            near = bits.length ? bits.join('|') : '-';
        } catch (e9) { near = 'EX:' + fbxEx(e9); }
        parts.push('id=' + r.getId() + ':cg=' + cg + ':tr=' + tr + ':mob=' + d
            + ':tg=' + fbxAliveTargetsWithTarget() + ':near=[' + near + ']');
    }
    fbxSend(ctx, 'AP_FX_' + tag + '_REP_READ:n=' + n + ':' + (parts.length ? parts.join('|') : '-')
        + ':charge=' + fbxCharge(p));
    return 1;
}

/** 存活的 afb_target 里「仇恨目标非空」的个数（= isAttachable 第 ③ 条的实际满足数） */
function fbxAliveTargetsWithTarget() {
    var n = 0;
    for (var i = 0; i < FBX_STATE.targets.length; i++) {
        var c = FBX_STATE.targets[i];
        if (c == null || !c.isAlive()) continue;
        if (fbxHasTarget(c) === 1) n++;
    }
    return n;
}

// ── ⑥ 清场：discard 玩家 32 格内全部强化拒止力场 ─────────────────────────────
function fbxDoRepClear(ctx, tag) {
    var p = fbxP(ctx);
    var level = p.level;
    var before = 0; var after = 0;
    try {
        var reps = level.getEntitiesOfClass(FBX_RepulsionEntity, p.getBoundingBox().inflate(32.0));
        for (var b = 0; b < reps.size(); b++) {
            var rb = false;
            try { rb = reps.get(b).isRemoved(); } catch (eb) { rb = false; }
            if (!rb) before++;
        }
        for (var i = 0; i < reps.size(); i++) { try { reps.get(i).discard(); } catch (e1) { /* 忽略 */ } }
        // 复扫 + 只数**未移除**的：EntitySection 的列表要到 tick 末才真正摘掉被 discard 的实体
        var reps2 = level.getEntitiesOfClass(FBX_RepulsionEntity, p.getBoundingBox().inflate(32.0));
        for (var a = 0; a < reps2.size(); a++) {
            var rm = false;
            try { rm = reps2.get(a).isRemoved(); } catch (ea) { rm = false; }
            if (!rm) after++;
        }
    } catch (e0) { fbxSend(ctx, 'AP_FX_' + tag + '_REP_CLEAR:ex=' + fbxEx(e0)); return 0; }
    fbxSend(ctx, 'AP_FX_' + tag + '_REP_CLEAR:id=' + FBX_REP_TYPE_ID + ':before=' + before + ':after=' + after);
    return 1;
}

// ── ⑦ 末影人：tag afb_ender + 仇恨（isAttachable 需要 getTarget()!=null）────────
function fbxDoEndSpawn(ctx, tag) {
    var p = fbxP(ctx);
    var level = p.level;
    var old = fbxNear(level, p, FBX_END_TAG, 24, FBX_Mob);
    for (var k = 0; k < old.length; k++) { try { old[k].discard(); } catch (x0) { /* 忽略 */ } }

    var ender = null;
    try { ender = FBX_EntityType.ENDERMAN.create(level); }
    catch (e0) { fbxSend(ctx, 'AP_FX_' + tag + '_EX:end-create:' + fbxEx(e0)); return 0; }
    if (ender == null) { fbxSend(ctx, 'AP_FX_' + tag + '_EX:end-null'); return 0; }
    try { ender.setPos(p.getX() + 2.0, p.getY(), p.getZ() + 3.0); } catch (e1) { /* 忽略 */ }
    try { ender.setPersistenceRequired(); } catch (e2) { /* 忽略 */ }
    try { ender.addTag(FBX_END_TAG); } catch (e3) { /* 忽略 */ }
    try { ender.setTarget(p); } catch (e4) { /* 忽略 */ }
    try { level.addFreshEntity(ender); } catch (e5) { fbxSend(ctx, 'AP_FX_' + tag + '_EX:end-add:' + fbxEx(e5)); return 0; }

    var dmin = -1;
    try {
        var reps = level.getEntitiesOfClass(FBX_RepulsionEntity, p.getBoundingBox().inflate(32.0));
        for (var i = 0; i < reps.size(); i++) {
            var dd = fbxRound(fbxDist(reps.get(i), ender.getX(), ender.getY(), ender.getZ()));
            if (dmin < 0 || dd < dmin) dmin = dd;
        }
    } catch (e6) { dmin = -1; }
    fbxSend(ctx, 'AP_FX_' + tag + '_END_SPAWN:type=' + fbxId(ender) + ':id=' + ender.getId()
        + ':dist=' + dmin + ':tg=' + fbxHasTarget(ender) + ':' + fbxIdAll(ender));
    return 1;
}

// ── ⑧ 末影人监视：连续 N tick 取「到最近力场的距离」，报最大值 ────────────────
//
//  ⚠️ 2026-10-22 实测：Rhino 派发不到 `LivingEntity#hurt`（`TypeError: Cannot find function hurt`），
//  故「逼闪现」改由用例侧注入原版 `/damage <sel> 1 minecraft:magic`（该命令的 DamageSource
//  **无实体来源**，正是触发 `EnderMan#hurt` 里 `randomTeleport` 的分支）。
//  本监视器只负责客观读数：`over=`（是否越出 11.5）与 `jumps=`（单 tick 位移 > 2 的次数）。
//
//  历史说明（poke 路线）：`EnderMan#hurt` 的字节码是
//    `if (!isClientSide && !(source.getEntity() instanceof LivingEntity) && random.nextInt(10)!=0) teleport()`
//  ⇒ 无实体来源 90% 触发 `randomTeleport` ⇒ 命中 `EntityTeleportEvent.EnderEntity` ⇒
//  被 `event/RepulsionTeleportGuard` 取消（被吸附目标）。**NoAI 不影响这条路径**（它不在 AI 里）。
function fbxDoEWatch(ctx, tag, ticks) {
    var p = fbxP(ctx);
    var level = p.level;
    var list = fbxNear(level, p, FBX_END_TAG, 24, FBX_Mob);
    if (list.length === 0) { fbxSend(ctx, 'AP_FX_' + tag + '_END_WATCH:ex=no_enderman'); return 0; }
    FBX_STATE.watch = {
        tag: tag, player: p, ender: list[0], left: ticks, max: 0, samples: 0,
        // 「单 tick 位移 > 2 格」计数 = 未被 RepulsionTeleportGuard 拦下的闪现次数（0 ⇒ 全被取消）
        jumps: 0, px: list[0].getX(), py: list[0].getY(), pz: list[0].getZ(), dmax: 0
    };
    fbxSend(ctx, 'AP_FX_' + tag + '_END_START:ticks=' + ticks + ':id=' + list[0].getId()
        + ':type=' + fbxId(list[0]) + ':tg=' + fbxHasTarget(list[0]));
    return 1;
}

/** 对末影人施加一次「无实体来源」的伤害，逼原版 `randomTeleport`（被守卫取消则完全不动） */
var FBX_POKE_ERR = '';
function fbxPokeEnder(ender) {
    var src = null;
    try { src = ender.damageSources().magic(); } catch (e0) { FBX_POKE_ERR = 'magic:' + fbxEx(e0); }
    if (src == null) {
        try { src = ender.damageSources().generic(); } catch (e1) { FBX_POKE_ERR += '|generic:' + fbxEx(e1); }
    }
    if (src == null) return 0;
    try { ender.hurt(src, 1.0); return 1; } catch (e2) { FBX_POKE_ERR += '|hurt:' + fbxEx(e2); return 0; }
}

ServerEvents.tick(event => {
    var w = FBX_STATE.watch;
    if (w != null) {
        try {
            if (w.ender == null || !w.ender.isAlive()) {
                fbxEmit(w.player, 'AP_FX_' + w.tag + '_END_WATCH:ticks=' + w.left + ':max=' + fbxRound(w.max)
                    + ':samples=' + w.samples + ':alive=0:jumps=' + w.jumps);
                FBX_STATE.watch = null;
            } else {
                var reps = w.ender.level.getEntitiesOfClass(FBX_RepulsionEntity,
                    w.ender.getBoundingBox().inflate(32.0));
                var dmin = -1;
                for (var i = 0; i < reps.size(); i++) {
                    var dd = fbxDist(reps.get(i), w.ender.getX(), w.ender.getY(), w.ender.getZ());
                    if (dmin < 0 || dd < dmin) dmin = dd;
                }
                if (dmin >= 0) { if (dmin > w.max) w.max = dmin; w.samples++; }
                // 闪现检测：与上一采样位置比，单 tick 位移 > 2 格 ⇒ 有一次没被守卫取消的传送
                var ddx = w.ender.getX() - w.px;
                var ddy = w.ender.getY() - w.py;
                var ddz = w.ender.getZ() - w.pz;
                var jump = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                if (jump > 2.0) w.jumps++;
                w.px = w.ender.getX(); w.py = w.ender.getY(); w.pz = w.ender.getZ();
                if (w.max > w.dmax) w.dmax = w.max;
                w.left--;
                if (w.left <= 0) {
                    fbxEmit(w.player, 'AP_FX_' + w.tag + '_END_WATCH:ticks=0:max=' + fbxRound(w.max)
                        + ':samples=' + w.samples + ':alive=1:over=' + (w.max > 11.5 ? 1 : 0)
                        + ':jumps=' + w.jumps);
                    FBX_STATE.watch = null;
                }
            }
        } catch (e) {
            // ⚠️ 不得静默：2026-10-22 1.20.1 实测「distanceToSqr 派发失败」被这个 catch 吞掉，
            // 监视器整段空转（samples=0 / left 不推进），只在实体死亡时才输出一行 alive=0。
            // ⇒ 首个异常就把原因打进聊天通道（每条 watch 只报一次），便于下一轮一眼定位。
            if (w != null && !w.errReported) {
                w.errReported = true;
                fbxEmit(w.player, 'AP_FX_' + w.tag + '_END_WATCH_ERR:' + fbxEx(e));
            }
        }
    }

    var g = FBX_STATE.gate0;
    if (g != null) {
        try {
            g.elapsed++;
            if (g.phase === 1 && g.elapsed >= FBX_GATE0_ATTEMPT2) {
                g.a2 = FBX_Dispatcher.trigger(g.player, g.id, FBX_TriggerType.ITEM_USE) ? 1 : 0;
                var gu = -1;
                try { gu = FBX_GlobalEventGate.gateUntilGameTime(); } catch (e1) { gu = -1; }
                var now = fbxNow(g.player);
                g.remain2 = (gu >= 0 && now >= 0) ? (gu - now) : -1;
                g.phase = 2;
            } else if (g.phase === 2 && g.elapsed >= FBX_GATE0_ATTEMPT3) {
                g.a3 = FBX_Dispatcher.trigger(g.player, g.id, FBX_TriggerType.ITEM_USE) ? 1 : 0;
                var gu3 = -1;
                try { gu3 = FBX_GlobalEventGate.gateUntilGameTime(); } catch (e2) { gu3 = -1; }
                fbxEmit(g.player, 'AP_FX_' + g.tag + '_GATE0:a1=' + g.a1 + ':a2=' + g.a2
                    + ':remain2=' + g.remain2 + ':t2=' + FBX_GATE0_ATTEMPT2
                    + ':a3=' + g.a3 + ':t3=' + FBX_GATE0_ATTEMPT3
                    + ':gate_until=' + gu3 + ':dur=0');
                FBX_STATE.gate0 = null;
            }
        } catch (e) { /* 忽略 */ }
    }
});

// ── ⑨ 事件卡：使用主手 event_ticket（走产品 Item#use ⇒ 会撞 GlobalEventGate）──
function fbxDoTicket(ctx, tag) {
    var p = fbxP(ctx);
    var before = -1; var after = -1; var res = 'n/a'; var err = '';
    var at = fbxNow(p);
    try { before = p.getMainHandItem().getCount(); } catch (e0) { before = -2; }
    try {
        var st = p.getMainHandItem();
        res = '' + st.use(p.level, p, FBX_Hand.MAIN_HAND);
    } catch (e1) { err = fbxEx(e1); }
    try { after = p.getMainHandItem().getCount(); } catch (e2) { after = -2; }
    var gu = -1;
    try { gu = FBX_GlobalEventGate.gateUntilGameTime(); } catch (e3) { gu = -1; }
    fbxSend(ctx, 'AP_FX_' + tag + '_TICKET:before=' + before + ':after=' + after
        + ':res=' + res + ':at=' + at + ':gate_until=' + gu + (err ? ':err=' + err : ''));
    return 1;
}

// ── ⑨b 事件卡双发（**同一 tick 内**连用两次）────────────────────────────────
//
//  为什么必须同一 tick：`/astralfixbatch ticket` 两次之间的**注入开销**实测 ≈ 2.3–2.8s
//  （每次 inject 都要做 layout 探针 + 前台/焦点夺取 + 逐字键入），而 duration=0 的事件的
//  闸门窗口只有 58t = 2.9s ⇒ 两次独立命令**踩在窗口边缘**（2026-10-22 实测：G4A 成功后
//  3.81s 的第二张已被放行并消耗，是把用例判成假 FAIL 的唯一原因）。
//  本条命令在同一 tick 里连调两次 `ItemStack#use` ⇒ Δt=0，窗口恒成立，与随机抽中的事件
//  时长（0/300/1200/2400/3600/6000）无关。
function fbxDoTicket2(ctx, tag) {
    var p = fbxP(ctx);
    var st = null;
    var before = -1; var after1 = -1; var after2 = -1; var err = '';
    try { st = p.getMainHandItem(); before = st.getCount(); } catch (e0) { before = -2; }
    var r1 = 'n/a'; var r2 = 'n/a';
    try {
        r1 = '' + st.use(p.level, p, FBX_Hand.MAIN_HAND);
        after1 = st.getCount();
        r2 = '' + st.use(p.level, p, FBX_Hand.MAIN_HAND);
        after2 = st.getCount();
    } catch (e1) { err = fbxEx(e1); }
    var gu = -1; var now = fbxNow(p); var remain = -1;
    try { gu = FBX_GlobalEventGate.gateUntilGameTime(); } catch (e2) { gu = -1; }
    if (gu >= 0 && now >= 0) remain = gu - now;
    fbxSend(ctx, 'AP_FX_' + tag + '_TICKET2:before=' + before + ':after1=' + after1
        + ':after2=' + after2 + ':remain=' + remain + ':at=' + now + ':gate_until=' + gu
        + ':r1=' + r1 + ':r2=' + r2 + (err ? ':err=' + err : ''));
    return 1;
}

// ── ⑩ 闸门复位（等价服务器重启后的干净状态；GlobalEventGate#reset 是公开入口）────
function fbxDoGateReset(ctx, tag) {
    var p = fbxP(ctx);
    var err = '';
    try { FBX_GlobalEventGate.reset(); } catch (e0) { err = fbxEx(e0); }
    var gu = -1;
    try { gu = FBX_GlobalEventGate.gateUntilGameTime(); } catch (e1) { gu = -1; }
    fbxSend(ctx, 'AP_FX_' + tag + '_GATE_RESET:gate_until=' + gu + ':now=' + fbxNow(p)
        + (err ? ':err=' + err : ''));
    return 1;
}

// ── ⑪ 时长 0 的瞬时事件三连（判 GlobalEventGate 的 38+20 算术）───────────────────
function fbxDoGate0(ctx, tag) {
    var p = fbxP(ctx);
    var id = null;
    try { id = FBX_EventIds.LEARN_FROM_OTHERS; } catch (e0) { id = null; }
    if (id == null) { fbxSend(ctx, 'AP_FX_' + tag + '_GATE0:ex=no_id'); return 0; }
    try { FBX_GlobalEventGate.reset(); } catch (e1) { /* 忽略：reset 只为把窗口钉在 t0 */ }
    var a1 = 0;
    try { a1 = FBX_Dispatcher.trigger(p, id, FBX_TriggerType.ITEM_USE) ? 1 : 0; }
    catch (e2) { fbxSend(ctx, 'AP_FX_' + tag + '_GATE0:ex=a1:' + fbxEx(e2)); return 0; }
    var gu = -1;
    try { gu = FBX_GlobalEventGate.gateUntilGameTime(); } catch (e3) { gu = -1; }
    FBX_STATE.gate0 = { tag: tag, player: p, id: id, elapsed: 0, phase: 1, a1: a1, a2: -1, a3: -1, remain2: -1 };
    fbxSend(ctx, 'AP_FX_' + tag + '_GATE0_START:a1=' + a1 + ':dur=0:id=' + id
        + ':t0=' + fbxNow(p) + ':gate_until=' + gu);
    return 1;
}

// ── ⑫ 并栈：连发 3 张同款卡 ⇒ 「占几格 / 共几张」─────────────────────────────
function fbxDoCard3(ctx, tag) {
    var p = fbxP(ctx);
    var item = null;
    try { item = FBX_ModItems.EFFECT_CARD_KING_POWER.get(); } catch (e0) { item = null; }
    if (item == null) { fbxSend(ctx, 'AP_FX_' + tag + '_CARDSLOT:ex=no_item'); return 0; }
    var added = 0;
    for (var i = 0; i < 3; i++) {
        try { if (p.getInventory().add(new FBX_ItemStack(item, 1))) added++; }
        catch (e1) { fbxSend(ctx, 'AP_FX_' + tag + '_CARDSLOT:ex=add:' + fbxEx(e1)); return 0; }
    }
    var want = ''; try { want = '' + FBX_Reg.ITEM.getKey(item); } catch (e2) { want = '?'; }
    var inv = p.getInventory(); var slots = 0; var count = 0;
    for (var k = 0; k < inv.getContainerSize(); k++) {
        var st = inv.getItem(k);
        if (st == null || st.isEmpty()) continue;
        var id = ''; try { id = '' + FBX_Reg.ITEM.getKey(st.getItem()); } catch (e3) { id = '?'; }
        if (id !== want) continue;
        slots++; count += st.getCount();
    }
    fbxSend(ctx, 'AP_FX_' + tag + '_CARDSLOT:item=' + want + ':added=' + added
        + ':slots=' + slots + ':count=' + count);
    return 1;
}

// ── ⑬ 抽奖盒投入空白筹码：连投两次（第二次应被 2:00 冷却拒绝且不消耗）──────────
//
//  `placed` 口径修正（2026-10-22）：旧实现直接回显 `setBlockAndUpdate` 的布尔值 ⇒
//  第二次调用（方块的**已经是**同一个抽奖盒）恒为 false，与「能不能用」无关。
//  新口径 = 「调用后该坐标上确实是抽奖盒」⇒ 首投 1、次投也 1（且**不重放方块**，
//  重放会重建 BlockEntity 并抹掉 2:00 投入冷却，把第二段判据变成假通过）。
function fbxDoDrawerBlank(ctx, tag) {
    var p = fbxP(ctx);
    var level = p.level;
    var pos = p.blockPosition().offset(2, 0, 0);
    var drawer = null;
    try { drawer = FBX_ModBlocks.EVENT_DRAWER.get(); } catch (eB) { drawer = null; }
    if (drawer == null) { fbxSend(ctx, 'AP_FX_' + tag + '_DRAWER:ex=no_block'); return 0; }
    var pre = 0; var set = 0;
    // ⚠️ `BlockState#is(drawer)` 在 Rhino 里**重载二义**（实测 InternalError: The choice of Java method
    //    BlockStateBase.is matching argument types …）⇒ 改用 `getBlock()` 的字符串身份比较。
    var want = '' + drawer;
    try {
        pre = ('' + level.getBlockState(pos).getBlock()) === want ? 1 : 0;
        if (pre === 0) set = level.setBlockAndUpdate(pos, drawer.defaultBlockState()) ? 1 : 0;
    } catch (e0) { fbxSend(ctx, 'AP_FX_' + tag + '_DRAWER:ex=place:' + fbxEx(e0)); return 0; }
    var post = 0;
    try { post = ('' + level.getBlockState(pos).getBlock()) === want ? 1 : 0; } catch (e0b) { post = 0; }
    var placed = post;

    var blank = null;
    try { blank = FBX_ModItems.BLANK_CHIP.get(); } catch (e1) { blank = null; }
    if (blank == null) { fbxSend(ctx, 'AP_FX_' + tag + '_DRAWER:ex=no_blank'); return 0; }
    try { p.setItemInHand(FBX_Hand.MAIN_HAND, new FBX_ItemStack(blank, 3)); }
    catch (e2) { fbxSend(ctx, 'AP_FX_' + tag + '_DRAWER:ex=hand:' + fbxEx(e2)); return 0; }

    var hit = new FBX_BlockHitResult(
        new FBX_Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), FBX_Direction.UP, pos, false);
    var res1 = 'n/a'; var res2 = 'n/a'; var err = '';
    var h1 = -1; var h2 = -1;
    try {
        res1 = '' + p.gameMode.useItemOn(p, level, p.getMainHandItem(), FBX_Hand.MAIN_HAND, hit);
        h1 = p.getMainHandItem().getCount();
        res2 = '' + p.gameMode.useItemOn(p, level, p.getMainHandItem(), FBX_Hand.MAIN_HAND, hit);
        h2 = p.getMainHandItem().getCount();
    } catch (e3) { err = fbxEx(e3); }
    fbxSend(ctx, 'AP_FX_' + tag + '_DRAWER:placed=' + placed + ':pre=' + pre + ':set=' + set
        + ':post=' + post + ':res1=' + res1 + ':res2=' + res2
        + ':hand1=' + h1 + ':hand2=' + h2 + (err ? ':err=' + err : ''));
    return 1;
}

// ── 命令注册 ──────────────────────────────────────────────────────────────
ServerEvents.commandRegistry(event => {
    var Commands = event.commands;
    event.register(
        Commands.literal('astralfixbatch')
            .then(Commands.literal('hostiles')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoHostiles(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cows')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoHostiles(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cast')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoCast(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('gread')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoGread(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('repplace')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoRepPlace(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('rread')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoRepRead(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('repclear')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoRepClear(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('endspawn')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoEndSpawn(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('ewatch')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .then(Commands.argument('ticks', FBX_IntArg.integer(1, 1200))
                        .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                            return fbxDoEWatch(ctx, FBX_StringArg.getString(ctx, 'tag'),
                                FBX_IntArg.getInteger(ctx, 'ticks'));
                        })))))
            .then(Commands.literal('ticket')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoTicket(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('ticket2')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoTicket2(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('gatereset')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoGateReset(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('gate0')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoGate0(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('card3')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoCard3(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('drawerblank')
                .then(Commands.argument('tag', FBX_StringArg.word())
                    .executes(ctx => fbxGuard(ctx, FBX_StringArg.getString(ctx, 'tag'), function () {
                        return fbxDoDrawerBlank(ctx, FBX_StringArg.getString(ctx, 'tag'));
                    }))))
    );
});
