// ════════════════════════════════════════════════════════════════════════════
//  astral_cc2_probe.js —— 选卡界面（CardChoiceScreen）服务端探针：/astralcc2
//
//  【谁在用（1.20.1 线）】scripts/test/cases/ 下三个用例：
//    CC-FILTER-1.20.1.json      /astralcc2 prep filter → open 3 combat 2101 → close
//    CC-RIGHTCLICK-1.20.1.json  同上（复用 FILTER 的 2101 客户端序列，见用例 note）
//    CC-DBLCLICK-1.20.1.json    /astralcc2 prep dbl    → open 3 combat 2200 → close
//  界面打开后由 client_scripts/astral_cc2_client.js 逐 tick 演输入序列，
//  每阶段读数以 AP_CC2C:t=<gameTime>:phase=<阶段>:... 落 logs/latest.log。
//
//  【为什么由服务端开界面】1.20.1 的容器界面只能由服务端下发（NetworkHooks.openScreen
//  ⇒ MenuProvider）；客户端 setScreen 出来的只是空壳、menu 不同步 ⇒ 点击读数全空。
//  CardChoiceManager#start 是产品自己的公开入口（建会话 + 下发 + 回读校验），
//  用例要验的正是这条真链路 ⇒ 这里只调它，不复制它的实现。
//
//  【命令面】
//    /astralcc2 prep filter|dbl            备背包：清空 36 格后按用例 note 摆阵
//    /astralcc2 open <层数> <类型> <token>  CardChoiceManager.start(玩家, 层数, 类型, token, handler)
//    /astralcc2 close                      关界面 + 作废会话（用例末尾兜底；正常由客户端 ESC 关）
//    /astralcc2 state                      只读：当前菜单快照（不开不关，排查用）
//
//  【取证通道】
//    · 回显 = ctx.source.sendFailure(Component.literal(...))，照抄 astral_shop_probe.js 的 sp_send：
//      玩家源 ⇒ 客户端 latest.log 的 [CHAT] 行；console 源 ⇒ 服务端控制台。
//    · 同一行再走 console.info('AP_CC2:' + ...) ⇒ 无论命令源是谁都落 latest.log（KubeJS Server 通道）。
//    · 界面打开期间每 tick 一条 AP_CC2S:gt=<gameTime>:...（服务端整菜单快照，历史口径）。
//
//  【1.20.1 Rhino 口径（每条都在本仓实测过）】
//    · 顶层 var 类别名在命令回调里偶发取不到（astral_shop_probe.js L85 的教训）⇒
//      用到的类一律在函数内用 cc2Cls() 现取（Java.loadClass 有缓存，代价可忽略）。
//    · 一律 var，不用 const/let（分支里的 const 会抛 redeclaration）。
//    · ServerPlayer#closeContainer 在 Rhino 里不可见（抛 TypeError）⇒ 关界面走三件套。
//    · 菜单槽号（slotCount=3）：0..2 = 卡槽（隐藏坐标 -10000），3..29 = 主物品栏 27 格，
//      30/31/32 = 快捷栏第 0/1/2 格 ⇒ 用例里的 s30/s31/s32 就是快捷栏前三格。
// ════════════════════════════════════════════════════════════════════════════

// ──────────────────────────────── 基础工具 ────────────────────────────────

/** 类加载：取不到返回 null（KubeJS 类过滤器会拒 java.io 等包，探针必须能带病运行） */
function cc2Cls(n) {
    try { return Java.loadClass(n); } catch (e) { return null; }
}

/** 异常一行化（去换行、截 500 字），照抄 sp_exText 的口径 */
function cc2_exText(e) {
    var s = '';
    try { s = '' + e; } catch (e2) { s = 'unknown'; }
    try { s = s.replace(/[\r\n]+/g, ' '); } catch (e3) { }
    if (s.length > 500) s = s.substring(0, 500);
    return s;
}

/**
 * 回显一行：玩家源 ⇒ 聊天（落客户端 latest.log 的 [CHAT] 行）；console 源 ⇒ 服务端控制台。
 * 再补一条 console.info('AP_CC2:' + ...)，保证命令源无论是谁都能在 latest.log 里 grep 到。
 */
function cc2_send(ctx, text) {
    var line = '' + text;
    try { console.info('AP_CC2:' + line); } catch (e0) { }
    try {
        var C = cc2Cls('net.minecraft.network.chat.Component');
        if (C != null) ctx.source.sendFailure(C.literal(line));
    } catch (e1) { }
}

/** 命令体包一层：异常不冒到 brigadier（否则命令直接失败，日志里只剩一行堆栈） */
function cc2_guard(ctx, tag, fn) {
    try { return fn(); } catch (e) {
        cc2_send(ctx, 'AP_CC2_' + tag + '_EX:' + cc2_exText(e));
        return 0;
    }
}

/** Java 集合长度（List.copyOf 的运行时类是包私有实现，get/iterator 在 Rhino 里会炸；length/size 安全） */
function cc2_len(x) {
    if (x == null) return 0;
    try { var n1 = x.length; if (typeof n1 === 'number') return n1; } catch (e0) { }
    try { var n2 = x.size(); if (typeof n2 === 'number') return n2; } catch (e1) { }
    try { return Java.from(x).length; } catch (e2) { }
    return 0;
}

/** Java List 取第 i 个（左路 x[i] 在 Rhino 里常返回 null，右路 get(i) 兜底） */
function cc2_at(x, i) {
    if (x == null) return null;
    try { var v = x[i]; if (v != null) return v; } catch (e0) { }
    try { return x.get(i); } catch (e1) { }
    return null;
}

// ──────────────────────────────── 服务端对象 ────────────────────────────────

/** 命令玩家：玩家源直取；console 源回落「在线第一人」（用例走客户端通道，但命令本身不依赖玩家身份） */
function cc2_player(ctx) {
    var p = null;
    try { p = ctx.source.getPlayer(); } catch (e0) { p = null; }
    if (p != null) return p;
    try {
        var srv = ctx.source.getServer();
        var list = srv.getPlayerList().getPlayers();
        if (cc2_len(list) > 0) return cc2_at(list, 0);
    } catch (e1) { }
    return null;
}

function cc2_level(p) {
    if (p == null) return null;
    try { var l = p.level; if (l != null) return l; } catch (e0) { }
    try { return p.level(); } catch (e1) { }
    return null;
}

function cc2_tick(p) {
    var l = cc2_level(p);
    if (l == null) return -1;
    try { return l.getLevelData().getGameTime(); } catch (e) { return -1; }
}

function cc2_name(p) {
    try { return '' + p.getGameProfile().getName(); } catch (e0) { }
    try { return '' + p.getName().getString(); } catch (e1) { }
    return '?';
}

/** 物体类名（1.20.1 Rhino 过滤 Class 成员 ⇒ 用 toString 里的类名做二路判定） */
function cc2_cls(o) {
    try {
        var s = '' + o;
        var m = /class ([^ ]+)/.exec(s);
        if (m != null && m.length > 1) return m[1];
        return s;
    } catch (e) { return '?'; }
}

// ──────────────────────────────── 物品栈序列化 ────────────────────────────────

/** 物品 id（空 → 'empty'）：1.20.1 的 ''+ItemStack 只出裸名，必须走物品注册表 */
function cc2_id(stack) {
    if (stack == null) return 'empty';
    var isEmpty = true;
    try { isEmpty = stack.isEmpty(); } catch (e0) { isEmpty = true; }
    if (isEmpty) return 'empty';
    var Reg = cc2Cls('net.minecraft.core.registries.BuiltInRegistries');
    if (Reg != null) {
        try {
            var k = Reg.ITEM.getKey(stack.getItem());
            if (k != null) {
                var s = '' + k;
                if (s !== '' && s !== 'null') return s;
            }
        } catch (e1) { }
    }
    return '<id?>';
}

/** 序列化口径（与历史读数一致）：空 = 'empty'，非空 = '<完整 id>x<数量>'（无空格） */
function cc2_ser(stack) {
    if (stack == null) return 'empty';
    var id = cc2_id(stack);
    if (id === 'empty') return 'empty';
    var n = 1;
    try { n = stack.getCount(); } catch (e) { n = 1; }
    return id + 'x' + n;
}

function cc2_item(idStr) {
    var Reg = cc2Cls('net.minecraft.core.registries.BuiltInRegistries');
    var RL = cc2Cls('net.minecraft.resources.ResourceLocation');
    if (Reg == null || RL == null) return null;
    try { return Reg.ITEM.get(new RL('' + idStr)); } catch (e) { return null; }
}

function cc2_stack(idStr, n) {
    var IS = cc2Cls('net.minecraft.world.item.ItemStack');
    if (IS == null) return null;
    var it = cc2_item(idStr);
    if (it == null) return IS.EMPTY;
    var st = new IS(it);
    try { st.setCount(n); } catch (e) { }
    return st;
}

/** 背包里某 id 的总数量（0..35，快捷栏 0/1/2 也在这个区间） */
function cc2_invCount(inv, idStr) {
    var n = 0;
    for (var i = 0; i < 36; i++) {
        try {
            var st = inv.getItem(i);
            if (st == null || st.isEmpty()) continue;
            if (cc2_id(st) === idStr) n += st.getCount();
        } catch (e) { }
    }
    return n;
}

// ──────────────────────────────── 菜单快照 ────────────────────────────────

/** 当前玩家的选卡菜单；不是 CardChoiceMenu 一律 null */
function cc2_menuOf(p) {
    if (p == null) return null;
    var m = null;
    try { m = p.containerMenu; } catch (e0) { return null; }
    if (m == null) return null;
    var C = cc2Cls('com.merlinkitsune.astral_dice.screen.CardChoiceMenu');
    if (C != null) {
        var ok = false;
        try { ok = (m instanceof C); } catch (e1) { ok = false; }
        return ok ? m : null;
    }
    try { return (('' + m).indexOf('CardChoiceMenu') >= 0) ? m : null; } catch (e2) { return null; }
}

function cc2_slotSer(m, i) {
    try {
        var sl = m.getSlot(i);
        if (sl == null) return 'empty';
        return cc2_ser(sl.getItem());
    } catch (e) { return 'empty'; }
}

/**
 * 整菜单快照（历史口径）：tok / slots / type / filled / complete / c0..c2 / car /
 * s30..s32 / s3 / 背包四项计数。
 * ⚠️ 字段顺序与客户端的 phase 行一致（用例里有连续字段断言，不许重排）。
 * 注：slotCount=1 时 c1/c2 会读成玩家槽，本仓用例只用 slotCount=3。
 */
function cc2_stateParts(m, p) {
    var tok = -1, sc = -1, filled = -1, complete = -1, typ = '?';
    try { tok = m.token(); } catch (e0) { }
    try { sc = m.slotCount(); } catch (e1) { }
    try { filled = m.filledSlots(); } catch (e2) { }
    try { complete = m.isComplete() ? 1 : 0; } catch (e3) { }
    try { typ = '' + m.type(); } catch (e4) { }
    var car = 'empty';
    try { car = cc2_ser(m.getCarried()); } catch (e5) { }
    var tail = '';
    var inv = null;
    try { inv = p.getInventory(); } catch (e6) { inv = null; }
    if (inv != null) {
        tail = ':am=' + cc2_invCount(inv, 'astral_dice:attack_card_medium')
             + ':al=' + cc2_invCount(inv, 'astral_dice:attack_card_large')
             + ':eb=' + cc2_invCount(inv, 'astral_dice:effect_card_berserk')
             + ':st=' + cc2_invCount(inv, 'minecraft:stone');
    }
    return 'tok=' + tok + ':slots=' + sc + ':type=' + typ + ':filled=' + filled
        + ':complete=' + complete
        + ':c0=' + cc2_slotSer(m, 0) + ':c1=' + cc2_slotSer(m, 1) + ':c2=' + cc2_slotSer(m, 2)
        + ':car=' + car
        + ':s30=' + cc2_slotSer(m, 30) + ':s31=' + cc2_slotSer(m, 31) + ':s32=' + cc2_slotSer(m, 32)
        + ':s3=' + cc2_slotSer(m, 3)
        + tail;
}

/**
 * 关界面三件套（照抄 astral_shop_probe.js#sp_closeMenu）：
 * closeContainer（Rhino 里不可见、抛 TypeError，仍先试）→ containerMenu.removed(p) → 关包 → 复位到 inventoryMenu。
 * 返回实际做成了哪几步（'none' = 本来就没开）。
 */
function cc2_closeMenu(p) {
    var m = cc2_menuOf(p);
    if (m == null) return 'none';
    var how = '';
    var cid = 0;
    try { cid = m.containerId; } catch (e0) { cid = 0; }
    try { p.closeContainer(); how += 'closeContainer'; } catch (e1) { how += 'closeContainer=EX'; }
    try { m.removed(p); how += '+removed'; } catch (e2) { how += '+removed=EX'; }
    try {
        var CP = cc2Cls('net.minecraft.network.protocol.game.ClientboundContainerClosePacket');
        if (CP != null && p.connection != null) {
            p.connection.send(new CP(cid));
            how += '+packet=' + cid;
        }
    } catch (e3) { how += '+packet=EX'; }
    try {
        var PB = cc2Cls('net.minecraft.world.inventory.InventoryMenu');
        if (PB != null && p.inventoryMenu != null) { p.containerMenu = p.inventoryMenu; how += '+reset'; }
    } catch (e4) { how += '+reset=EX'; }
    return how;
}

// ──────────────────────────────── 子命令实现 ────────────────────────────────

/**
 * prep：清空 36 格后按用例 note 摆阵（服务端权威写入，客户端下一次 sync 才看得见）。
 *   filter: hotbar0 = 战斗牌(中)x4 / hotbar1 = 效果牌(狂暴)x3 / hotbar2 = 石头x8
 *   dbl   : hotbar0 = 战斗牌(中)x4 / hotbar1 = 战斗牌(大)x2
 */
function cc2_doPrep(ctx, kind) {
    var p = cc2_player(ctx);
    if (p == null) { cc2_send(ctx, 'AP_CC2:prep:fail:reason=no_player'); return 0; }
    var IS = cc2Cls('net.minecraft.world.item.ItemStack');
    var inv = p.getInventory();
    for (var k = 0; k < 36; k++) {
        try { inv.setItem(k, IS.EMPTY); } catch (e0) { }
    }
    var k2 = '' + kind;
    if (k2 === 'filter' || k2 === 'f') {
        inv.setItem(0, cc2_stack('astral_dice:attack_card_medium', 4));
        inv.setItem(1, cc2_stack('astral_dice:effect_card_berserk', 3));
        inv.setItem(2, cc2_stack('minecraft:stone', 8));
    } else if (k2 === 'dbl' || k2 === 'd') {
        inv.setItem(0, cc2_stack('astral_dice:attack_card_medium', 4));
        inv.setItem(1, cc2_stack('astral_dice:attack_card_large', 2));
    } else {
        cc2_send(ctx, 'AP_CC2:prep:fail:reason=bad_kind:' + k2);
        return 0;
    }
    // 光标 / 选中格归零：上一次跑完若残留携带物，会把后续读数全带偏
    try { p.containerMenu.setCarried(IS.EMPTY); } catch (e1) { }
    try { inv.selected = 0; } catch (e2) { }
    try { p.inventoryMenu.broadcastChanges(); } catch (e3) { }
    cc2_send(ctx, 'AP_CC2:prep:kind=' + k2 + ':player=' + cc2_name(p)
        + ':hb0=' + cc2_ser(inv.getItem(0))
        + ':hb1=' + cc2_ser(inv.getItem(1))
        + ':hb2=' + cc2_ser(inv.getItem(2)));
    return 1;
}

/**
 * open：走产品公开入口 CardChoiceManager.start(player, slotCount, type, token, handler)。
 * handler 必须非 null（start 里 handler == null 直接 return false）⇒ 传一个只记日志的 lambda；
 * Rhino 的 SAM 化优先，失败再回退 JavaAdapter(Consumer)。
 */
function cc2_doOpen(ctx, slots, typeArg, token) {
    var p = cc2_player(ctx);
    if (p == null) { cc2_send(ctx, 'AP_CC2:open:fail:reason=no_player'); return 0; }
    var Mgr = cc2Cls('com.merlinkitsune.astral_dice.choice.CardChoiceManager');
    var T = cc2Cls('com.merlinkitsune.astral_dice.choice.CardChoiceType');
    if (Mgr == null || T == null) {
        cc2_send(ctx, 'AP_CC2:open:fail:reason=class_missing:' + (Mgr == null ? 'CardChoiceManager' : 'CardChoiceType'));
        return 0;
    }
    var type = null;
    try { type = T.valueOf(('' + typeArg).toUpperCase()); } catch (eT1) { type = null; }
    if (type == null) {
        try { type = T.byId(parseInt('' + typeArg, 10)); } catch (eT2) { type = null; }
    }
    if (type == null) { cc2_send(ctx, 'AP_CC2:open:fail:reason=bad_type:' + typeArg); return 0; }
    var handler = function (list) {
        try { cc2_send(ctx, 'AP_CC2:handler:tok=' + token + ':picked=' + cc2_len(list)); } catch (e0) { }
    };
    var ok = false;
    var how = 'lambda';
    var err = '';
    try {
        ok = Mgr.start(p, slots, type, token, handler) ? true : false;
    } catch (e1) {
        how = 'adapter';
        err = cc2_exText(e1);
        try {
            var CN = cc2Cls('java.util.function.Consumer');
            var ad = new JavaAdapter(CN, { accept: handler });
            ok = Mgr.start(p, slots, type, token, ad) ? true : false;
        } catch (e2) {
            ok = false;
            err = err + ' | adapter=' + cc2_exText(e2);
        }
    }
    var m = cc2_menuOf(p);
    var sess = 0;
    try { sess = Mgr.hasOpenSession(p) ? 1 : 0; } catch (e3) { sess = -1; }
    cc2_send(ctx, 'AP_CC2:open:tok=' + token + ':slots=' + slots + ':type=' + ('' + type)
        + ':ok=' + (ok ? 1 : 0) + ':handler=' + how + ':session=' + sess
        + ':menu=' + (m == null ? 'none' : 'ok')
        + (err === '' ? '' : ':err=' + err));
    return ok ? 1 : 0;
}

/** close：关界面 + 作废会话（用例末尾的兜底；ESC 已经关掉时这里应报 menu=none/session=0） */
function cc2_doClose(ctx) {
    var p = cc2_player(ctx);
    if (p == null) { cc2_send(ctx, 'AP_CC2:close:fail:reason=no_player'); return 0; }
    var Mgr = cc2Cls('com.merlinkitsune.astral_dice.choice.CardChoiceManager');
    var m = cc2_menuOf(p);
    var tok = -1;
    if (m != null) { try { tok = m.token(); } catch (e0) { tok = -1; } }
    var before = 0;
    if (Mgr != null) { try { before = Mgr.hasOpenSession(p) ? 1 : 0; } catch (e1) { before = -1; } }
    var how = cc2_closeMenu(p);
    if (Mgr != null && before === 1 && tok > 0) {
        try { Mgr.abandon(p, tok); how += '+abandon'; } catch (e2) { how += '+abandon=EX'; }
    }
    var after = 0;
    if (Mgr != null) { try { after = Mgr.hasOpenSession(p) ? 1 : 0; } catch (e3) { after = -1; } }
    cc2_send(ctx, 'AP_CC2:close:tok=' + tok + ':how=' + how
        + ':session_before=' + before + ':session_after=' + after
        + ':car=' + cc2_ser(p.containerMenu.getCarried())
        + ':s30=' + cc2_ser(p.getInventory().getItem(0)));
    return 1;
}

/** state：只读快照（不开不关） */
function cc2_doState(ctx) {
    var p = cc2_player(ctx);
    if (p == null) { cc2_send(ctx, 'AP_CC2:state:fail:reason=no_player'); return 0; }
    var m = cc2_menuOf(p);
    if (m == null) {
        var Mgr = cc2Cls('com.merlinkitsune.astral_dice.choice.CardChoiceManager');
        var sess = 0;
        if (Mgr != null) { try { sess = Mgr.hasOpenSession(p) ? 1 : 0; } catch (e0) { sess = -1; } }
        cc2_send(ctx, 'AP_CC2:state:menu=none:session=' + sess + ':menucls=' + cc2_cls(p.containerMenu));
        return 0;
    }
    cc2_send(ctx, 'AP_CC2:state:' + cc2_stateParts(m, p));
    return 1;
}

// ──────────────────────────────── 每 tick 服务端快照 ────────────────────────────────

/** 界面打开期间每 tick 一条 AP_CC2S:gt=<gameTime>:...（服务端权威读数，排查用；用例断言只认客户端的 AP_CC2C） */
function cc2_tickDump(event) {
    var srv = null;
    try { var s1 = event.server; if (s1 != null) srv = s1; } catch (e0) { srv = null; }
    if (srv == null) { try { srv = event.getServer(); } catch (e1) { srv = null; } }
    if (srv == null) return;
    var list = null;
    try { list = srv.getPlayerList().getPlayers(); } catch (e2) { return; }
    var n = cc2_len(list);
    for (var i = 0; i < n; i++) {
        var p = cc2_at(list, i);
        if (p == null) continue;
        var m = cc2_menuOf(p);
        if (m == null) continue;
        try { console.info('AP_CC2S:gt=' + cc2_tick(p) + ':' + cc2_stateParts(m, p)); } catch (e3) { }
    }
}

ServerEvents.tick(event => {
    // 每 tick 抛异常会刷屏并把状态机卡死 ⇒ 一律吞掉（astral_bugfix_probe.js L16770 的教训）
    try { cc2_tickDump(event); } catch (e) { }
});

// ──────────────────────────────── 命令注册 ────────────────────────────────

ServerEvents.commandRegistry(event => {
    var C = event.commands;
    var SArg = cc2Cls('com.mojang.brigadier.arguments.StringArgumentType');
    var IArg = cc2Cls('com.mojang.brigadier.arguments.IntegerArgumentType');
    if (C == null || SArg == null || IArg == null) {
        console.error('AP_CC2_INIT_EX:brigadier argument types unavailable');
        return;
    }
    event.register(
        C.literal('astralcc2')
            .executes(ctx => { cc2_guard(ctx, 'CC2HELP', function () {
                    cc2_send(ctx, 'AP_CC2:help:use=prep <filter|dbl> | open <slots> <type> <token> | close | state');
                    return 1; }); return 1; })
            .then(C.literal('prep')
                .then(C.argument('kind', SArg.word())
                    .executes(ctx => cc2_guard(ctx, 'CC2PREP', function () {
                        return cc2_doPrep(ctx, SArg.getString(ctx, 'kind'));
                    }))))
            .then(C.literal('open')
                .then(C.argument('slots', IArg.integer())
                    .then(C.argument('type', SArg.word())
                        .then(C.argument('token', IArg.integer())
                            .executes(ctx => cc2_guard(ctx, 'CC2OPEN', function () {
                                return cc2_doOpen(ctx,
                                    IArg.getInteger(ctx, 'slots'),
                                    SArg.getString(ctx, 'type'),
                                    IArg.getInteger(ctx, 'token'));
                            }))))))
            .then(C.literal('close')
                .executes(ctx => cc2_guard(ctx, 'CC2CLOSE', function () { return cc2_doClose(ctx); })))
            .then(C.literal('state')
                .executes(ctx => cc2_guard(ctx, 'CC2STATE', function () { return cc2_doState(ctx); })))
            .then(C.literal('help')
                .executes(ctx => { cc2_guard(ctx, 'CC2HELP', function () {
                    cc2_send(ctx, 'AP_CC2:help:use=prep <filter|dbl> | open <slots> <type> <token> | close | state');
                    return 1; }); return 1; }))
    );
    try { console.info('AP_CC2:registered:cmd=/astralcc2'); } catch (e0) { }
});
