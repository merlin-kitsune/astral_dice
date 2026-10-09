// ════════════════════════════════════════════════════════════════════════════
//  astral_shop_vis.js —— 卡牌商店 实机取证探针 B(2026-10-09 任务G)
//  为什么另开一个文件: 首跑实测 KubeJS/Rhino 有「方法白名单」——
//    ServerPlayer#closeContainer / getClass / getYRot / VillagerProfession#acquirableJobSites
//    一律抛 TypeError: Cannot find function(证据: latest.log AP_D0_DIAG / AP_B0_*)。
//  本文件先把「哪些成员真的可见」一次性测出来(vis),再提供只依赖已验证可见成员的原语:
//    /astralvis vis <tag>            可见性矩阵(VIS1/VIS2)
//    /astralvis close <tag>          关菜单三件套(removed + 关闭包 + 复位)
//    /astralvis shop <tag> <place|poi|use|break>   工作方块四步(place 与 poi 必须跨 tick)
//    /astralvis vil <tag> <spawn|read|prof N|offers|kill>  探针村民(职业/等级/报价)
//  取证通道: ctx.source.sendFailure → latest.log 的 [CHAT] AP_<tag>_<KIND>: 行。
// ════════════════════════════════════════════════════════════════════════════

function av_cls(n) { try { return Java.loadClass(n); } catch (e) { return null; } }

var AV_Component = av_cls('net.minecraft.network.chat.Component');
var AV_RL = av_cls('net.minecraft.resources.ResourceLocation');
var AV_Reg = av_cls('net.minecraft.core.registries.BuiltInRegistries');
var AV_StringArg = av_cls('com.mojang.brigadier.arguments.StringArgumentType');
var AV_IntArg = av_cls('com.mojang.brigadier.arguments.IntegerArgumentType');
var AV_VillagerData = av_cls('net.minecraft.world.entity.npc.VillagerData');
var AV_VillagerProfession = av_cls('net.minecraft.world.entity.npc.VillagerProfession');
var AV_VillagerType = av_cls('net.minecraft.world.entity.npc.VillagerType');
var AV_ClosePacket = av_cls('net.minecraft.network.protocol.game.ClientboundContainerClosePacket');
var AV_PoiTypes = av_cls('net.minecraft.world.entity.ai.village.poi.PoiTypes');
var AV_ItemStack = av_cls('net.minecraft.world.item.ItemStack');
var AV_HitResult = av_cls('net.minecraft.world.phys.BlockHitResult');
var AV_Vec3 = av_cls('net.minecraft.world.phys.Vec3');
var AV_Direction = av_cls('net.minecraft.core.Direction');
var AV_ModBlocks = av_cls('com.merlinkitsune.astral_dice.block.ModBlocks');
var AV_ModPoiTypes = av_cls('com.merlinkitsune.astral_dice.world.ModPoiTypes');
var AV_ModProfessions = av_cls('com.merlinkitsune.astral_dice.world.ModVillagerProfessions');

var av_villager = null;
var av_pos = null;
var AV_Hand = av_cls('net.minecraft.world.InteractionHand');
var AV_Items = av_cls('net.minecraft.world.item.Items');
var AV_ItemCost = av_cls('net.minecraft.world.item.trading.ItemCost');
var AV_Curios = av_cls('top.theillusivec4.curios.api.CuriosApi');
var AV_EDice = av_cls('com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade');
var AV_Filter = av_cls('com.merlinkitsune.astral_dice.shop.CardTradeFilter');
var AV_Catalog = av_cls('com.merlinkitsune.astral_dice.shop.CardShopCatalog');

function av_p(ctx) { try { return ctx.source.getPlayerOrException(); } catch (e) { return ctx.source.getPlayer(); } }
function av_send(ctx, tag, text) { try { ctx.source.sendFailure(AV_Component.literal('AP_' + tag + '_' + text)); } catch (e) { } }
function av_ex(e) { var s; try { s = '' + e; } catch (x) { s = 'ex'; } return s.replace(/[\r\n]+/g, ' ').substring(0, 90); }
function av_s(v, max) { var s; try { s = (v === null || v === undefined) ? 'null' : ('' + v); } catch (e) { s = 'unprintable'; } return s.replace(/[\r\n]+/g, ' ').substring(0, max || 70); }
function av_mark(name, fn, out) { try { out.push(name + '=' + av_s(fn())); } catch (e) { out.push(name + '=EX:' + av_ex(e)); } }
function av_id(stack) { try { if (stack == null || stack.isEmpty()) return 'empty'; return '' + AV_Reg.ITEM.getKey(stack.getItem()); } catch (e) { return 'ex'; } }
function av_clsName(o) {
    if (o == null) return 'null';
    try { var s = '' + o; var m = /(?:^|[.\s])([A-Za-z0-9_]+)@[0-9a-fA-F]+/.exec(s); if (m != null) return m[1]; } catch (e) { }
    return 'cls?';
}
/** 关菜单三件套(与 astral_bugfix_probe.js#teruCloseMenu 同口径,见文件头白名单说明) */
function av_closeMenu(p) {
    var how = [];
    var id = -1;
    try { id = (p.containerMenu == null) ? -1 : p.containerMenu.containerId; } catch (e0) { }
    try { p.closeContainer(); how.push('closeContainer'); } catch (e1) { how.push('closeContainer=EX'); }
    try { p.containerMenu.removed(p); how.push('removed'); } catch (e2) { how.push('removed=EX'); }
    if (AV_ClosePacket != null && id >= 0) {
        try { p.connection.send(new AV_ClosePacket(id)); how.push('packet=' + id); } catch (e3) { how.push('packet=EX'); }
    } else { how.push('noPacketClsOrId'); }
    try { p.containerMenu = p.inventoryMenu; how.push('reset'); } catch (e4) { how.push('reset=EX'); }
    return how.join('+');
}
// ── 可见性矩阵 ──────────────────────────────────────────────────────────────
function av_doVis(ctx, tag) {
    var p = av_p(ctx);
    var lvl = p.level;
    var pos = p.blockPosition().offset(6, 0, 0);
    var o1 = [];
    var o2 = [];
    av_mark('p_blockPosition', function () { return p.blockPosition(); }, o1);
    av_mark('p_getX', function () { return p.getX(); }, o1);
    av_mark('p_yRotProp', function () { return p.yRot; }, o1);
    av_mark('p_getYRot', function () { return p.getYRot(); }, o1);
    av_mark('p_getDirection', function () { return p.getDirection(); }, o1);
    av_mark('p_uuidProp', function () { return p.uuid; }, o1);
    av_mark('p_getUUID', function () { return p.getUUID(); }, o1);
    av_mark('p_levelProp', function () { return av_clsName(p.level); }, o1);
    av_mark('p_getServer', function () { return av_clsName(p.getServer()); }, o1);
    av_mark('p_containerMenu', function () { return av_clsName(p.containerMenu); }, o1);
    av_mark('p_inventoryMenu', function () { return av_clsName(p.inventoryMenu); }, o1);
    av_mark('p_nbtProp', function () { return p.nbt.size(); }, o1);
    av_mark('p_connection', function () { return av_clsName(p.connection); }, o1);
    av_mark('p_createCmdSrc', function () { return av_clsName(p.createCommandSourceStack()); }, o1);
    av_mark('p_closeContainer', function () { p.closeContainer(); return 'ok'; }, o1);
    av_mark('src_performCmd', function () { return ctx.source.getServer().getCommands().performPrefixedCommand(ctx.source, '/say AVC'); }, o1);
    av_mark('lvl_setBlock', function () { return lvl.setBlock(pos, AV_ModBlocks.CARD_SHOP.get().defaultBlockState(), 3) ? 1 : 0; }, o2);
    av_mark('lvl_getBlockState', function () { return av_id(new AV_ItemStack(lvl.getBlockState(pos).getBlock().asItem())); }, o2);
    av_mark('poi_forState', function () { var op = AV_PoiTypes.forState(lvl.getBlockState(pos)); return op.isEmpty() ? 'empty' : ('' + AV_Reg.POINT_OF_INTEREST_TYPE.getKey(op.get().value())); }, o2);
    av_mark('poi_existsAt', function () { return lvl.getPoiManager().existsAtPosition(AV_ModPoiTypes.CARD_SHOP_KEY, pos) ? 1 : 0; }, o2);
    av_mark('poi_getInSquare', function () { return lvl.getPoiManager().getInSquare(function (h) { return true; }, pos, 3, 1).count(); }, o2);
    av_mark('lvl_removeBlock', function () { return lvl.removeBlock(pos, false) ? 1 : 0; }, o2);
    av_mark('prof_heldJobSite', function () { return AV_ModProfessions.CARD_TRADER.get().heldJobSite().test(AV_Reg.POINT_OF_INTEREST_TYPE.wrapAsHolder(AV_ModPoiTypes.CARD_SHOP.get())) ? 1 : 0; }, o2);
    av_mark('prof_acquirable', function () { return AV_ModProfessions.CARD_TRADER.get().acquirableJobSite().test(AV_Reg.POINT_OF_INTEREST_TYPE.wrapAsHolder(AV_ModPoiTypes.CARD_SHOP.get())) ? 1 : 0; }, o2);
    av_mark('ent_create', function () { return av_clsName(AV_Reg.ENTITY_TYPE.get(AV_RL.parse('minecraft:villager')).create(lvl)); }, o2);
    av_mark('vdata_ctor', function () { return av_s(new AV_VillagerData(AV_VillagerType.PLAINS, AV_VillagerProfession.NONE, 1)); }, o2);
    av_mark('reg_profKey', function () { return '' + AV_Reg.VILLAGER_PROFESSION.getKey(AV_ModProfessions.CARD_TRADER.get()); }, o2);
    av_mark('closePacket_ctor', function () { return av_s(new AV_ClosePacket(0)); }, o2);
    av_send(ctx, tag, 'VIS1:' + o1.join('|'));
    av_send(ctx, tag, 'VIS2:' + o2.join('|'));
    return 1;
}
// ── 工作方块四步 ────────────────────────────────────────────────────────────
/** POI 台账由 ServerLevel.onBlockStateChange 的 server.execute 排队 ⇒ 同 tick 读不到(首跑 poi=0 真因)
 *  故 place 与 poi/break 必须分属不同 tick(用例里用 wait 步隔开)。 */
function av_doShop(ctx, tag, op) {
    var p = av_p(ctx);
    var lvl = p.level;
    if (op === 'place') {
        var ps = p.blockPosition().offset(2, 0, 0);
        av_pos = ps;
        var pre = '?';
        try { pre = av_id(new AV_ItemStack(lvl.getBlockState(ps).getBlock().asItem())); } catch (e0) { pre = 'EX'; }
        // 幂等: 世界跨轮保留, 该位可能已有方块 ⇒ setBlockAndUpdate 会返回 false。先清位再放。
        try { lvl.removeBlock(ps, false); } catch (e0b) { }
        var ok = -1;
        try { ok = lvl.setBlockAndUpdate(ps, AV_ModBlocks.CARD_SHOP.get().defaultBlockState()) ? 1 : 0; } catch (e1) { ok = 'EX:' + av_ex(e1); }
        var got = '?';
        try { got = av_id(new AV_ItemStack(lvl.getBlockState(ps).getBlock().asItem())); } catch (e2) { got = 'EX:' + av_ex(e2); }
        av_send(ctx, tag, 'PLACE:set=' + ok + ':place=' + got + ':pos=' + ps.getX() + ',' + ps.getY() + ',' + ps.getZ() + ':pre=' + pre);
        return 1;
    }
    if (op === 'poi') {
        if (av_pos == null) { av_send(ctx, tag, 'POI:ex=no_pos'); return 0; }
        var poi = -1;
        var fs = 'EX';
        var pred = -1;
        var acq = -1;
        try { poi = lvl.getPoiManager().existsAtPosition(AV_ModPoiTypes.CARD_SHOP_KEY, av_pos) ? 1 : 0; } catch (e3) { poi = 'EX:' + av_ex(e3); }
        try { var opo = AV_PoiTypes.forState(lvl.getBlockState(av_pos)); fs = opo.isEmpty() ? 'empty' : ('' + AV_Reg.POINT_OF_INTEREST_TYPE.getKey(opo.get().value())); } catch (e4) { fs = 'EX:' + av_ex(e4); }
        try { pred = AV_ModProfessions.CARD_TRADER.get().heldJobSite().test(AV_Reg.POINT_OF_INTEREST_TYPE.wrapAsHolder(AV_ModPoiTypes.CARD_SHOP.get())) ? 1 : 0; } catch (e5) { pred = 'EX'; }
        try { acq = AV_ModProfessions.CARD_TRADER.get().acquirableJobSite().test(AV_Reg.POINT_OF_INTEREST_TYPE.wrapAsHolder(AV_ModPoiTypes.CARD_SHOP.get())) ? 1 : 0; } catch (e6) { acq = 'EX'; }
        av_send(ctx, tag, 'POI:existsAt=' + poi + ':forState=' + fs + ':held=' + pred + ':acquirable=' + acq + ':pos=' + av_pos.getX() + ',' + av_pos.getY() + ',' + av_pos.getZ());
        return 1;
    }
    if (op === 'use') {
        if (av_pos == null) { av_send(ctx, tag, 'USE:ex=no_pos'); return 0; }
        var res = 'n/a';
        try {
            var st = lvl.getBlockState(av_pos);
            var hit = new AV_HitResult(new AV_Vec3(av_pos.getX() + 0.5, av_pos.getY() + 0.9, av_pos.getZ() + 0.5), AV_Direction.UP, av_pos, false);
            res = '' + st.useWithoutItem(lvl, p, hit);
        } catch (e7) { res = 'EX:' + av_ex(e7); }
        var menuCls = av_clsName(p.containerMenu);
        var how = av_closeMenu(p);
        av_send(ctx, tag, 'USE:res=' + res + ':menu=' + menuCls + ':close=' + how + ':menuAfter=' + av_clsName(p.containerMenu));
        return 1;
    }
    if (op === 'break') {
        if (av_pos == null) { av_send(ctx, tag, 'BREAK:ex=no_pos'); return 0; }
        var broke = -1;
        try { broke = lvl.removeBlock(av_pos, false) ? 1 : 0; } catch (e8) { broke = 'EX:' + av_ex(e8); }
        av_send(ctx, tag, 'BREAK:broke=' + broke + ':air=' + av_id(new AV_ItemStack(lvl.getBlockState(av_pos).getBlock().asItem())));
        return 1;
    }
    av_send(ctx, tag, 'SHOP:ex=unknown_op:' + op);
    return 0;
}
// ── 探针村民 ────────────────────────────────────────────────────────────────
function av_profOf(v) {
    var out = {};
    try { out.api = '' + AV_Reg.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()); } catch (e1) { out.api = 'EX:' + av_ex(e1); }
    try { out.lvl = '' + v.getVillagerData().getLevel(); } catch (e2) { out.lvl = 'EX:' + av_ex(e2); }
    try { var c = v.nbt.getCompound('VillagerData'); out.nbt = c.getString('profession') + '@' + c.getInt('level'); } catch (e3) { out.nbt = 'EX:' + av_ex(e3); }
    try { out.offers = v.getOffers().size(); } catch (e4) { out.offers = 'EX:' + av_ex(e4); }
    return out;
}
function av_offersText(v) {
    var out = [];
    try {
        var offs = v.getOffers();
        var n = offs.size();
        for (var i = 0; i < n; i++) {
            var o = offs.get(i);
            var r = '?';
            var c = '?';
            var mu = '?';
            try { r = av_id(o.getResult()) + 'x' + o.getResult().getCount(); } catch (e1) { r = 'EX'; }
            try { c = av_id(o.getCostA()) + 'x' + o.getCostA().getCount(); } catch (e2) { c = 'EX'; }
            try { mu = '' + o.getMaxUses(); } catch (e3) { mu = 'EX'; }
            out.push(i + ':' + c + '->' + r + ':uses=' + mu);
        }
    } catch (e) { return 'EX:' + av_ex(e); }
    return (out.length === 0) ? 'empty' : out.join(';');
}
function av_doVil(ctx, tag, op, arg) {
    var p = av_p(ctx);
    var lvl = p.level;
    if (op === 'spawn') {
        var bp = p.blockPosition().offset(1, 0, 0);
        var out = [];
        var v = null;
        try { v = AV_Reg.ENTITY_TYPE.get(AV_RL.parse('minecraft:villager')).create(lvl); out.push('create=' + av_clsName(v)); } catch (e1) { out.push('create=EX:' + av_ex(e1)); }
        if (v != null) {
            try { v.setPos(bp.getX() + 0.5, p.getY(), bp.getZ() + 0.5); out.push('setPos=ok'); } catch (e2) { out.push('setPos=EX'); }
            try { v.setPersistenceRequired(); out.push('persist=ok'); } catch (e3) { out.push('persist=EX'); }
            try { lvl.addFreshEntity(v); out.push('add=ok'); } catch (e4) { out.push('add=EX:' + av_ex(e4)); }
            av_villager = v;
            // 认领工作方块挂在 brain 上 ⇒ 村民必须有 AI。测试台 astral_test_noai.js 的
            // EntityEvents.spawned 会在 add 时把 NoAi 顶回 true，所以 add 完再清一次并回读实测值。
            try { v.setNoAi(false); out.push('clearAi=ok'); } catch (e4b) { out.push('clearAi=EX:' + av_ex(e4b)); }
            try { out.push('noai=' + (v.isNoAi() ? 1 : 0)); } catch (e4c) { out.push('noai=EX'); }
        }
        out.push('pos=' + bp.getX() + ',' + bp.getY() + ',' + bp.getZ());
        av_send(ctx, tag, 'VSPAWN:' + out.join(':'));
        return 1;
    }
    if (op === 'read') {
        if (av_villager == null) { av_send(ctx, tag, 'VREAD:ex=no_villager'); return 0; }
        var q = av_profOf(av_villager);
        var pos = 'n/a';
        var alive = 'n/a';
        try { pos = av_villager.blockPosition().getX() + ',' + av_villager.blockPosition().getY() + ',' + av_villager.blockPosition().getZ(); } catch (e5) { pos = 'EX'; }
        try { alive = av_villager.isAlive() ? 1 : 0; } catch (e6) { alive = 'EX'; }
        var noai = 'n/a';
        try { noai = av_villager.isNoAi() ? 1 : 0; } catch (e6b) { noai = 'EX'; }
        var vxp = 'n/a';
        try { vxp = '' + av_villager.getVillagerXp(); } catch (e6c) { vxp = 'EX'; }
        av_send(ctx, tag, 'VREAD:api=' + q.api + ':apiLevel=' + q.lvl + ':nbt=' + q.nbt + ':offers=' + q.offers + ':xp=' + vxp + ':noai=' + noai + ':alive=' + alive + ':pos=' + pos);
        return 1;
    }
    if (op === 'prof') {
        if (av_villager == null) { av_send(ctx, tag, 'VPROF:ex=no_villager'); return 0; }
        var level = (arg == null) ? 1 : (arg - 0);
        var setRes = 'ok';
        try {
            av_villager.setVillagerData(new AV_VillagerData(AV_VillagerType.PLAINS, AV_VillagerProfession.NONE, 1));
            av_villager.setVillagerData(new AV_VillagerData(AV_VillagerType.PLAINS, AV_ModProfessions.CARD_TRADER.get(), level));
        } catch (e8) { setRes = 'EX:' + av_ex(e8); }
        var q2 = av_profOf(av_villager);
        av_send(ctx, tag, 'VPROF:want=' + level + ':set=' + setRes + ':api=' + q2.api + ':apiLevel=' + q2.lvl + ':nbt=' + q2.nbt);
        av_send(ctx, tag, 'VOFFERS:level=' + level + ':' + av_offersText(av_villager));
        return 1;
    }
    if (op === 'offers') {
        if (av_villager == null) { av_send(ctx, tag, 'VOFFERS:ex=no_villager'); return 0; }
        var q3 = av_profOf(av_villager);
        av_send(ctx, tag, 'VOFFERS:level=' + q3.lvl + ':' + av_offersText(av_villager));
        return 1;
    }
    if (op === 'kill') {
        var how = 'n/a';
        try { av_villager.discard(); how = 'discard'; } catch (e9) { how = 'EX:' + av_ex(e9); }
        av_villager = null;
        av_send(ctx, tag, 'VKILL:how=' + how);
        return 1;
    }
    av_send(ctx, tag, 'VIL:ex=unknown_op:' + op);
    return 0;
}

// ── 命令注册(逐节点建树,便于核括号)──────────────────────────────────────
// ── 第二轮取证:API 矩阵 #2 / 交易菜单索引对齐 / 绿宝石骰子过滤 ────────────
function av_doApi2(ctx, tag) {
    var p = av_p(ctx);
    var v = av_villager;
    var o1 = [], o2 = [], o3 = [];
    av_mark('hand_main', function () { return av_s(AV_Hand.MAIN_HAND); }, o1);
    av_mark('p_getItemInHand', function () { return av_id(p.getItemInHand(AV_Hand.MAIN_HAND)); }, o1);
    av_mark('vil_interact', function () { if (v == null) return 'novillager'; return (typeof v.interact); }, o1);
    av_mark('vil_mobInteract', function () { if (v == null) return 'novillager'; return (typeof v.mobInteract); }, o1);
    av_mark('vil_openTrading', function () { if (v == null) return 'novillager'; return (typeof v.openTradingScreen); }, o1);
    av_mark('vil_isTrading', function () { if (v == null) return 'novillager'; return v.isTrading() ? 1 : 0; }, o1);
    av_mark('vil_getOffers', function () { if (v == null) return 'novillager'; return v.getOffers().size(); }, o1);
    av_mark('vil_setXp', function () { if (v == null) return 'novillager'; v.setVillagerXp(30); return v.getVillagerXp(); }, o1);
    av_mark('men_setSelectionHint', function () { return (typeof p.containerMenu.setSelectionHint); }, o2);
    av_mark('men_getOffers', function () { var f = p.containerMenu.getOffers(); return f == null ? -1 : f.size(); }, o2);
    av_mark('curios_api', function () { var ip = AV_Curios.getCuriosInventory(p); return (ip == null) ? 'null' : (ip.isPresent() ? 'present' : 'empty'); }, o3);
    av_mark('ed_hasDice', function () { return AV_EDice.hasEmeraldDice(p) ? 1 : 0; }, o3);
    av_mark('ed_filteredFor', function () { if (v == null) return 'novillager'; var f = AV_EDice.filteredFor(p, v.getOffers()); return (f == null) ? 'null' : f.size(); }, o3);
    av_mark('ed_isEmerald', function () { if (v == null) return 'novillager'; var offs = v.getOffers(); if (offs.size() === 0) return 'nooffer'; return AV_EDice.isEmeraldToStarCoin(offs.get(0)) ? 1 : 0; }, o3);
    av_mark('filter_sellable_emerald', function () { return AV_Filter.isSellable(new AV_ItemStack(AV_Items.EMERALD)) ? 1 : 0; }, o3);
    av_mark('catalog_entries', function () { return AV_Catalog.entries().size(); }, o3);
    av_mark('cost_ctor', function () { return av_s(new AV_ItemCost(AV_Items.EMERALD, 6)); }, o3);
    av_mark('closeAfterProbe', function () { return av_closeMenu(p); }, o2);
    av_send(ctx, tag, 'API1:' + o1.join('|'));
    av_send(ctx, tag, 'API2:' + o2.join('|'));
    av_send(ctx, tag, 'API3:' + o3.join('|'));
    return 1;
}
function av_dice2(ctx, tag, op) {
    var p = av_p(ctx);
    var want = (op === 'on');
    var res = 'n/a';
    try {
        var ip = AV_Curios.getCuriosInventory(p);
        if (ip == null || !ip.isPresent()) res = 'no_curios';
        else {
            var stacks = ip.get().getStacksHandler('dice').get().getStacks();
            if (want) stacks.setStackInSlot(0, new AV_ItemStack(AV_Reg.ITEM.get(AV_RL.parse('astral_dice:emerald_dice'))));
            else stacks.setStackInSlot(0, AV_ItemStack.EMPTY);
            res = 'ok';
        }
    } catch (e) { res = 'EX:' + av_ex(e); }
    var on = 'EX';
    try { on = AV_EDice.hasEmeraldDice(p) ? 1 : 0; } catch (e2) { on = 'EX:' + av_ex(e2); }
    av_send(ctx, tag, 'DICE2:want=' + (want ? 1 : 0) + ':set=' + res + ':on=' + on);
    return 1;
}
function av_edf(ctx, tag) {
    var p = av_p(ctx);
    var v = av_villager;
    var out = [];
    try { out.push('diceOn=' + (AV_EDice.hasEmeraldDice(p) ? 1 : 0)); } catch (e0) { out.push('diceOn=EX'); }
    if (v == null) { out.push('ex=no_villager'); av_send(ctx, tag, 'EDF:' + out.join(':')); return 0; }
    try {
        var raw = v.getOffers();
        out.push('raw=' + raw.size());
        var f = AV_EDice.filteredFor(p, raw);
        out.push('filtered=' + (f == null ? 'null' : f.size()));
        if (f != null) {
            var ex = 0;
            for (var i = 0; i < f.size(); i++) { try { if (AV_EDice.isEmeraldToStarCoin(f.get(i))) ex++; } catch (ei) { } }
            out.push('filteredExch=' + ex);
            var same = 1;
            for (var k = 0; k < f.size(); k++) {
                var found = 0;
                for (var j = 0; j < raw.size(); j++) { if (f.get(k) === raw.get(j)) { found = 1; break; } }
                if (!found) same = 0;
            }
            out.push('sameInstance=' + same);
        }
    } catch (e1) { out.push('ex=' + av_ex(e1)); }
    av_send(ctx, tag, 'EDF:' + out.join(':'));
    return 1;
}
function av_doTrade(ctx, tag, op) {
    var p = av_p(ctx);
    var v = av_villager;
    if (v == null) { av_send(ctx, tag, 'TRADE:ex=no_villager'); return 0; }
    if (op === 'close') { av_send(ctx, tag, 'TRADECLOSE:how=' + av_closeMenu(p)); return 1; }
    var how = [];
    var mcl = av_clsName(p.containerMenu);
    if (mcl !== 'MerchantMenu') {
        var r1 = 'skip';
        try { r1 = av_s(v.interact(p, AV_Hand.MAIN_HAND)); } catch (e1) { r1 = 'EX:' + av_ex(e1); }
        how.push('interact=' + r1);
        mcl = av_clsName(p.containerMenu);
    }
    if (mcl !== 'MerchantMenu') {
        var r2 = 'skip';
        try { r2 = av_s(v.mobInteract(p, AV_Hand.MAIN_HAND)); } catch (e2) { r2 = 'EX:' + av_ex(e2); }
        how.push('mobInteract=' + r2);
        mcl = av_clsName(p.containerMenu);
    }
    if (mcl !== 'MerchantMenu') {
        var r3 = 'skip';
        try { v.openTradingScreen(p, AV_Component.literal('astralvis_probe'), 1); r3 = 'ok'; } catch (e3) { r3 = 'EX:' + av_ex(e3); }
        how.push('openTradingScreen=' + r3);
        mcl = av_clsName(p.containerMenu);
    }
    av_send(ctx, tag, 'TRADEOPEN:path=' + how.join('|') + ':menu=' + mcl);
    var m = p.containerMenu;
    var n = -1, ex = -1;
    var offs = null;
    try { offs = m.getOffers(); n = offs.size(); } catch (e4) { }
    if (offs != null) {
        ex = 0;
        for (var i = 0; i < offs.size(); i++) { try { if (AV_EDice.isEmeraldToStarCoin(offs.get(i))) ex++; } catch (ei) { } }
    }
    var diceOn = 'EX';
    try { diceOn = AV_EDice.hasEmeraldDice(p) ? 1 : 0; } catch (e5) { }
    av_send(ctx, tag, 'TRADEOFFERS:size=' + n + ':exch=' + ex + ':diceOn=' + diceOn);
    if (op === 'reads') { av_send(ctx, tag, 'TRADECLOSE:how=' + av_closeMenu(p)); return 1; }
    for (var t = 0; t < n; t++) {
        var wantA = 'n/a', wantR = 'n/a', c0 = 'empty', r2i = 'empty', setHow = 'ok';
        try {
            var off = offs.get(t);
            try { wantA = '' + AV_Reg.ITEM.getKey(off.getItemCostA().item().value()); } catch (ea) { wantA = 'EX'; }
            try { wantR = av_id(off.getResult()); } catch (eb) { wantR = 'EX'; }
            try { m.getSlot(0).set(AV_ItemStack.EMPTY); m.getSlot(1).set(AV_ItemStack.EMPTY); } catch (ec) { }
            try { m.setSelectionHint(t); m.tryMoveItems(t); } catch (ed) { setHow = 'EX:' + av_ex(ed); }
            try { c0 = av_id(m.getSlot(0).getItem()); } catch (ee) { c0 = 'EX'; }
            try { r2i = av_id(m.getSlot(2).getItem()); } catch (ef) { r2i = 'EX'; }
        } catch (eg) { setHow = 'outerEX:' + av_ex(eg); }
        av_send(ctx, tag, 'TRADEIDX:i=' + t + ':set=' + setHow + ':wantA=' + wantA + ':slot0=' + c0 + ':aMatch=' + ((c0 === wantA) ? 1 : 0) + ':wantR=' + wantR + ':slot2=' + r2i + ':rMatch=' + ((r2i === wantR) ? 1 : 0));
        var preId = 'n/a';
        try { preId = '' + av_id(m.getSlot(2).getItem()); } catch (ep) { preId = 'EX'; }
        var wantS = '' + wantR;
        var sameFlag = (preId === wantS && preId !== 'empty' && preId !== 'EX') ? 1 : 0;
        if (sameFlag === 1) {
            var invB = -1, invA = -1, usesB = -1, usesA = -1;
            try { invB = av_countItem(p, wantS); } catch (eB) { }
            try { usesB = off.getUses(); } catch (eB2) { }
            try { m.getSlot(2).onTake(p, m.getSlot(2).getItem().copy()); } catch (eB3) { }
            try { invA = av_countItem(p, wantS); } catch (eA) { }
            try { usesA = off.getUses(); } catch (eA2) { }
            var postId = 'n/a';
            try { postId = '' + av_id(m.getSlot(2).getItem()); } catch (eP2) { postId = 'EX'; }
            av_send(ctx, tag, 'TRADETAKE:i=' + t + ':wantId=' + wantS + ':preId=' + preId + ':same=1:postId=' + postId + ':inv=' + invB + '->' + invA + ':uses=' + usesB + '->' + usesA);
        } else {
            av_send(ctx, tag, 'TRADETAKE:i=' + t + ':wantId=' + wantS + ':preId=' + preId + ':same=0:postId=skip:inv=skip:uses=skip');
        }
    }
    var after = null, an = -1, aex = -1;
    try {
        after = m.getOffers();
        an = after.size();
        aex = 0;
        for (var q = 0; q < after.size(); q++) { try { if (AV_EDice.isEmeraldToStarCoin(after.get(q))) aex++; } catch (eq) { } }
    } catch (e6) { }
    av_send(ctx, tag, 'TRADEAFTER:size=' + an + ':exch=' + aex + ':diceOn=' + diceOn);
    av_send(ctx, tag, 'TRADECLOSE:how=' + av_closeMenu(p));
    return 1;
}
function av_countItem(p, idStr) {
    var c = 0;
    try {
        for (var i = 0; i < 36; i++) {
            var st = p.getInventory().getItem(i);
            if (st != null && !st.isEmpty() && av_id(st) === idStr) c += st.getCount();
        }
    } catch (e) { }
    return c;
}

// ── POI 台账 / 失业对照实验(场景2:破坏工作方块 ⇒ 村民失业)────────────────────
var av_ctl = null;
var av_ctlLectern = null;
function av_stateKey(lvl, bp) {
    var out = 'none';
    try {
        var opo = AV_PoiTypes.forState(lvl.getBlockState(bp));
        if (!opo.isEmpty()) out = '' + AV_Reg.POINT_OF_INTEREST_TYPE.getKey(opo.get().value());
    } catch (e) { out = 'EX:' + av_ex(e); }
    return out;
}
/** 扫描 ±r 格、±dy 层内的 card_shop / lectern 工作站点(世界跨轮保留 ⇒ 必须能查台账) */
// 原版 POI type 名 = 职业名:讲台 = minecraft:librarian(实测 AP_P4_CTLREAD:...:lectern=minecraft:librarian);
// 兼容按方块名注册的情况,统一归一化成 shop / lectern 两个标签。
function av_poiLabel(k) {
    if (k === 'astral_dice:card_shop') return 'shop';
    if (k === 'minecraft:librarian' || k === 'minecraft:lectern') return 'lectern';
    return null;
}
function av_poiBox(p, lvl, r, dy) {
    var hits = [];
    var b = p.blockPosition();
    for (var dx = -r; dx <= r; dx++) {
        for (var dz = -r; dz <= r; dz++) {
            for (var y = -dy; y <= dy; y++) {
                var bp = b.offset(dx, y, dz);
                var k = av_stateKey(lvl, bp);
                var lab = av_poiLabel(k);
                if (lab !== null) {
                    hits.push(lab + '@' + bp.getX() + ',' + bp.getY() + ',' + bp.getZ());
                }
            }
        }
    }
    return hits;
}
function av_doPoi(ctx, tag, op) {
    var p = av_p(ctx);
    var lvl = p.level;
    if (op === 'scan') {
        var hits = av_poiBox(p, lvl, 10, 2);
        av_send(ctx, tag, 'POISCAN:n=' + hits.length + ':list=' + (hits.length === 0 ? 'none' : hits.join('|')));
        return 1;
    }
    if (op === 'clear') {
        var b = p.blockPosition();
        var removed = 0;
        for (var dx = -10; dx <= 10; dx++) {
            for (var dz = -10; dz <= 10; dz++) {
                for (var y = -2; y <= 2; y++) {
                    var bp = b.offset(dx, y, dz);
                    if (av_stateKey(lvl, bp) === 'astral_dice:card_shop') {
                        try { if (lvl.removeBlock(bp, false)) removed++; } catch (e1) { }
                    }
                }
            }
        }
        var left = av_poiBox(p, lvl, 10, 2);
        av_send(ctx, tag, 'POICLEAR:removed=' + removed + ':left=' + left.length + ':list=' + (left.length === 0 ? 'none' : left.join('|')));
        return 1;
    }
    if (op === 'ctlspawn') {
        var out = [];
        var lb = p.blockPosition().offset(11, 0, 0);
        var okL = -1;
        try { lvl.removeBlock(lb, false); } catch (e2) { }
        try {
            var lecternState = AV_Reg.BLOCK.get(AV_RL.parse('minecraft:lectern')).defaultBlockState();
            okL = lvl.setBlockAndUpdate(lb, lecternState) ? 1 : 0;
        } catch (e3) { okL = 'EX:' + av_ex(e3); }
        av_ctlLectern = lb;
        var vb = p.blockPosition().offset(12, 0, 0);
        var v = null;
        try { v = AV_Reg.ENTITY_TYPE.get(AV_RL.parse('minecraft:villager')).create(lvl); } catch (e4) { out.push('create=EX:' + av_ex(e4)); }
        if (v != null) {
            try { v.setPos(vb.getX() + 0.5, p.getY(), vb.getZ() + 0.5); } catch (e5) { out.push('setPos=EX'); }
            try { v.setPersistenceRequired(); } catch (e6) { }
            try { lvl.addFreshEntity(v); } catch (e7) { out.push('add=EX:' + av_ex(e7)); }
            // 与 vil spawn 同口径: 测试台 astral_test_noai.js 的 EntityEvents.spawned 会顶 NoAi=true,
            // add 之后必须再清一次,否则村民无 brain ⇒ 永不认领工作站点(对照组会得到假阴性)。
            try { v.setNoAi(false); } catch (e8) { }
            av_ctl = v;
            try { out.push('noai=' + (v.isNoAi() ? 1 : 0)); } catch (e9) { out.push('noai=EX'); }
        }
        av_send(ctx, tag, 'CTLSPAWN:lectern=' + okL + ':at=' + lb.getX() + ',' + lb.getY() + ',' + lb.getZ() + ':vpos=' + vb.getX() + ',' + vb.getY() + ',' + vb.getZ() + ':' + out.join(':'));
        return 1;
    }
    if (op === 'ctlread') {
        if (av_ctl == null) { av_send(ctx, tag, 'CTLREAD:ex=no_villager'); return 0; }
        var q = av_profOf(av_ctl);
        var noai = 'n/a', alive = 'n/a', pos = 'n/a', lect = 'n/a';
        try { noai = av_ctl.isNoAi() ? 1 : 0; } catch (e10) { noai = 'EX'; }
        try { alive = av_ctl.isAlive() ? 1 : 0; } catch (e11) { alive = 'EX'; }
        try { var b3 = av_ctl.blockPosition(); pos = b3.getX() + ',' + b3.getY() + ',' + b3.getZ(); } catch (e12) { pos = 'EX'; }
        try { lect = av_stateKey(lvl, av_ctlLectern); } catch (e13) { lect = 'EX'; }
        av_send(ctx, tag, 'CTLREAD:api=' + q.api + ':apiLevel=' + q.lvl + ':nbt=' + q.nbt + ':offers=' + q.offers + ':noai=' + noai + ':alive=' + alive + ':vpos=' + pos + ':lectern=' + lect);
        return 1;
    }
    if (op === 'ctlbreak') {
        if (av_ctlLectern == null) { av_send(ctx, tag, 'CTLBREAK:ex=no_lectern'); return 0; }
        var before = av_stateKey(lvl, av_ctlLectern);
        var broke = -1;
        try { broke = lvl.removeBlock(av_ctlLectern, false) ? 1 : 0; } catch (e14) { broke = 'EX:' + av_ex(e14); }
        av_send(ctx, tag, 'CTLBREAK:broke=' + broke + ':before=' + before + ':after=' + av_stateKey(lvl, av_ctlLectern));
        return 1;
    }
    if (op === 'ctlkill') {
        var how = 'n/a';
        try { if (av_ctl != null) { av_ctl.discard(); how = 'discard'; } } catch (e15) { how = 'EX:' + av_ex(e15); }
        av_ctl = null;
        av_send(ctx, tag, 'CTLKILL:how=' + how);
        return 1;
    }
    av_send(ctx, tag, 'POI:ex=unknown_op:' + op);
    return 0;
}
ServerEvents.commandRegistry(event => {
    var C = event.commands;
    var visNode = C.literal('vis').then(C.argument('tag', AV_StringArg.word())
        .executes(function (ctx) { return av_doVis(ctx, AV_StringArg.getString(ctx, 'tag')); }));
    var closeNode = C.literal('close').then(C.argument('tag', AV_StringArg.word())
        .executes(function (ctx) {
            var pp = av_p(ctx);
            var how2 = av_closeMenu(pp);
            av_send(ctx, AV_StringArg.getString(ctx, 'tag'), 'CLOSE:how=' + how2 + ':menu=' + av_clsName(pp.containerMenu));
            return 1;
        }));
    var shopNode = C.literal('shop').then(C.argument('tag', AV_StringArg.word())
        .then(C.argument('op', AV_StringArg.word())
            .executes(function (ctx) { return av_doShop(ctx, AV_StringArg.getString(ctx, 'tag'), AV_StringArg.getString(ctx, 'op')); })));
    var vilOp = C.argument('op', AV_StringArg.word())
        .executes(function (ctx) { return av_doVil(ctx, AV_StringArg.getString(ctx, 'tag'), AV_StringArg.getString(ctx, 'op'), null); })
        .then(C.argument('n', AV_IntArg.integer())
            .executes(function (ctx) { return av_doVil(ctx, AV_StringArg.getString(ctx, 'tag'), AV_StringArg.getString(ctx, 'op'), AV_IntArg.getInteger(ctx, 'n')); }));
    var vilNode = C.literal('vil').then(C.argument('tag', AV_StringArg.word()).then(vilOp));
    var poiNode = C.literal('poi').then(C.argument('tag', AV_StringArg.word())
        .then(C.argument('op', AV_StringArg.word())
            .executes(function (ctx) { return av_doPoi(ctx, AV_StringArg.getString(ctx, 'tag'), AV_StringArg.getString(ctx, 'op')); })));
    var api2Node = C.literal('api2').then(C.argument('tag', AV_StringArg.word())
        .executes(function (ctx) { return av_doApi2(ctx, AV_StringArg.getString(ctx, 'tag')); }));
    var tradeNode = C.literal('trade').then(C.argument('tag', AV_StringArg.word())
        .then(C.argument('op', AV_StringArg.word())
            .executes(function (ctx) { return av_doTrade(ctx, AV_StringArg.getString(ctx, 'tag'), AV_StringArg.getString(ctx, 'op')); })));
    var dice2Node = C.literal('dice2').then(C.argument('tag', AV_StringArg.word())
        .then(C.argument('op', AV_StringArg.word())
            .executes(function (ctx) { return av_dice2(ctx, AV_StringArg.getString(ctx, 'tag'), AV_StringArg.getString(ctx, 'op')); })));
    var edfNode = C.literal('edf').then(C.argument('tag', AV_StringArg.word())
        .executes(function (ctx) { return av_edf(ctx, AV_StringArg.getString(ctx, 'tag')); }));
    event.register(C.literal('astralvis').then(visNode).then(closeNode).then(shopNode).then(vilNode).then(api2Node).then(tradeNode).then(dice2Node).then(edfNode).then(poiNode));
});
