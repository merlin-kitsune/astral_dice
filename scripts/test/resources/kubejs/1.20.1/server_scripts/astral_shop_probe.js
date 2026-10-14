// ════════════════════════════════════════════════════════════════════════════
//  astral_shop_probe.js —— 卡牌商店 / 卡牌交易员 实机取证探针(forge-1.20.1,任务K 移植自 1.21.1 .56 批)
//
//  取证通道(唯一权威 = 聊天):有命令上下文 → ctx.source.sendFailure(Component)
//    ⇒ 客户端 ChatComponent 落到 logs/latest.log 的 [CHAT] AP_... 行。
//  异常一律落成 AP_<TAG>_EX:<文本>(guard),避免被 brigadier 吞成「意外错误」。
//
//  ── 命令一览(用例 JSON 依赖这些名字与参数顺序)──────────────────────────────
//    /astralshop probe <tag>                      只读体检(经济可用性/余额/星光/骰子/治愈/狐光)
//    /astralshop reset <tag>                      归零:冷却=0、余额=0、星光=0
//    /astralshop block <tag>                      在脚边放 astral_dice:card_shop → POI 存在性
//                                                 + 职业谓词/可认领标签 → 真实右键开店 → 破坏方块
//    /astralshop open <tag>                       直接 CardShopOpener.open(玩家)
//    /astralshop menu <tag>                       当前 CardShopMenu 状态汇总
//    /astralshop shelf <tag>                      货架逐格(物品/价格/售出/降价)
//    /astralshop buy <tag> <shelf> <slot>         服务端 handleBuy(与客户端拖拽同入口)
//    /astralshop buyseq <tag>                     六步边界序列(成功/已售/越界/落点占用/余额0/余额不足)
//    /astralshop bal <tag> <n>                    设余额(deposit/withdraw 到差值)
//    /astralshop star <tag> <n>                   设星光
//    /astralshop exchange <tag>                   点「星光兑换」(clickMenuButton 17)前后对照
//    /astralshop tlist <tag>                      转账候选名单(下单 + 服务端同序复算)
//    /astralshop transfer <tag> <name> <amount>   完整转账 + 会话锁二次尝试
//    /astralshop tbad <tag>                       非法金额(0/4/21/-1)拒绝取证
//    /astralshop close <tag>                      关菜单 → 冷却顺延(removed)
//    /astralshop reopen <tag>                     重开店 → 冷却/会话锁读数
//    /astralshop dice <tag> <on|off>              佩戴/摘下绿宝石骰子(Curios dice 槽)
//    /astralshop equip <tag> <slot> <itemId>      Curios 槽放物品(none = 清空)
//    /astralshop curio <tag>                      Curios dice/stand/chip 槽内容
//    /astralshop obtain <tag> <itemId>            直接 notifyCardObtained(治愈/狐光前后)
//    /astralshop pool <tag>                       商店池 22 张 isSellable + 排除清单取证
//    /astralshop roll <tag> <n>                   随机 rollShelf n 次抽样(排除项不得出现)
//    /astralshop vplan <tag>                      五级交易表:档位/价格区间/兑换条目/排除项
//    /astralshop villager <tag> <level>           造村民推到该级 → 实际报价逐条
//    /astralshop vtrade <tag> <level>             开真实交易界面 → 过滤后索引对齐 + 真实成交
//    /astralshop vclose <tag>                     关交易界面 + 移除探针村民
//
//  ── 筹码商店(2026-10-13 批,移植自 1.21.1,前缀 c*)────────────────────────────────────────
//    /astralshop cblock <tag>                     在脚边放 astral_dice:chip_shop → 真实右键开店
//    /astralshop cpool <tag>                      池 61 / 各档计数 / 已拥有 / ownsEverything / 星等
//    /astralshop cstate <tag>                     附件原文(offers/last/purchased/refreshes/anchor)
//    /astralshop copen <tag>                      直接 ChipShopOpener.open(玩家),读完即关屏
//    /astralshop copenkeep <tag>                  同上但**留屏**(给客户端探针驱动真点击)
//    /astralshop cmenu <tag>                      当前 ChipShopMenu 读数(价格/次数/拒绝码/三格)
//    /astralshop cbuy <tag> <index>               服务端 handleBuy(与客户端确认按钮同入口)
//    /astralshop crefresh <tag>                   服务端 handleRefresh(与刷新按钮同入口)
//    /astralshop cprice <tag> <n>                 置 purchasedCount=n → 公式值/态值/菜单值
//    /astralshop cregen <tag> <due|max|zero|first|pend>  摆刷新锚点形态,等 ticker 自己跑
//    /astralshop cinv <tag> <fill|clear>          物品栏 36 格填满/清空
//    /astralshop cdeny <tag>                      物品栏满时开店 ⇒ 期望无菜单 + OPEN_DENIED
//    /astralshop cstable <tag>                    关店重开候选恒定(needsReroll 为假)
//    /astralshop cseq <tag>                       边界序列(余额不足/越界×2/成功/重进核对)
//
//  ── 实现约束(与 astral_bugfix_probe.js 同口径)────────────────────────────
//   1) 命令注册必须在 ServerEvents.commandRegistry 回调内;执行体为顶层命名函数;
//      函数内一律 var(避免 KubeJS 严格模式歧义)。
//   2) 所有 id 参数用 StringArgumentType.string()(word() 吞冒号)。
//   3) Rhino(1.20.1/1.21.1 同):p.level 是**属性**;class 用 getClass().getSimpleName()。
//   4) 不改变任何产品代码;所有副作用仅限测试存档。
// ════════════════════════════════════════════════════════════════════════════

function spTryClass(n) { try { return Java.loadClass(n); } catch (e) { return null; } }

var SP_Component = spTryClass('net.minecraft.network.chat.Component');
var SP_ItemStack = spTryClass('net.minecraft.world.item.ItemStack');
var SP_Hand = spTryClass('net.minecraft.world.InteractionHand');
var SP_Items = spTryClass('net.minecraft.world.item.Items');
var SP_RL = spTryClass('net.minecraft.resources.ResourceLocation');
var SP_Reg = spTryClass('net.minecraft.core.registries.BuiltInRegistries');
var SP_StringArg = spTryClass('com.mojang.brigadier.arguments.StringArgumentType');
var SP_IntArg = spTryClass('com.mojang.brigadier.arguments.IntegerArgumentType');
var SP_Vec3 = spTryClass('net.minecraft.world.phys.Vec3');
var SP_BlockHitResult = spTryClass('net.minecraft.world.phys.BlockHitResult');
var SP_Direction = spTryClass('net.minecraft.core.Direction');
var SP_VillagerData = spTryClass('net.minecraft.world.entity.npc.VillagerData');
var SP_VillagerType = spTryClass('net.minecraft.world.entity.npc.VillagerType');
var SP_VillagerProfession = spTryClass('net.minecraft.world.entity.npc.VillagerProfession');
var SP_VillagerTrades = spTryClass('net.minecraft.world.entity.npc.VillagerTrades');
var SP_Curios = spTryClass('top.theillusivec4.curios.api.CuriosApi');
var SP_ModAttachments = spTryClass('com.merlinkitsune.astral_dice.component.ModAttachments');
var SP_ModBlocks = spTryClass('com.merlinkitsune.astral_dice.block.ModBlocks');
var SP_ModPoiTypes = spTryClass('com.merlinkitsune.astral_dice.world.ModPoiTypes');
var SP_ModProfessions = spTryClass('com.merlinkitsune.astral_dice.world.ModVillagerProfessions');
var SP_ModItems = spTryClass('com.merlinkitsune.astral_dice.item.ModItems');
var SP_Catalog = spTryClass('com.merlinkitsune.astral_dice.shop.CardShopCatalog');
// 2026-10-10 实机修复：1.20.1 侧顶层 var 类别名在命令回调作用域里取不到（ReferenceError: "SP_ShopMenu" is not defined）
//   ⇒ promo 入口改为**函数内内联解析**：spTryClass('com.merlinkitsune.astral_dice.shop.CardShopMenu').triggerPromotion(...)。
var SP_Filter = spTryClass('com.merlinkitsune.astral_dice.shop.CardTradeFilter');
var SP_Opener = spTryClass('com.merlinkitsune.astral_dice.shop.CardShopOpener');
var SP_DiceTrade = spTryClass('com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade');
var SP_ClosePacket = spTryClass('net.minecraft.network.protocol.game.ClientboundContainerClosePacket');
var SP_Econ = spTryClass('com.merlinkitsune.starenginelib.economy.StarEngineEconomy');
var SP_Healing = spTryClass('com.merlinkitsune.astral_dice.item.HealingManager');
var SP_Teru = spTryClass('com.merlinkitsune.astral_dice.item.sign.TeruSignItem');
var SP_DiceCurio = spTryClass('com.merlinkitsune.astral_dice.item.dice.DiceCurioItem');
var SP_Vitamin = spTryClass('com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem');
// 筹码商店(2026-10-13 移植自 1.21.1)
var SP_ChipCatalog = spTryClass('com.merlinkitsune.astral_dice.shop.ChipShopCatalog');
var SP_ChipRoller = spTryClass('com.merlinkitsune.astral_dice.shop.ChipShopRoller');
var SP_ChipState = spTryClass('com.merlinkitsune.astral_dice.shop.ChipShopState');
var SP_ChipOpener = spTryClass('com.merlinkitsune.astral_dice.shop.ChipShopOpener');
var SP_ChipMenu = spTryClass('com.merlinkitsune.astral_dice.shop.ChipShopMenu');
var SP_Optional = spTryClass('java.util.Optional');
var SP_RandomSource = spTryClass('net.minecraft.util.RandomSource');

/** 探针村民的自定义名(便于 /kill @e[name=...] 清场) */
var SP_VNAME = 'ASProbeVillager';
/** 上一次 vtrade 造的村民(供 vclose 用) */
var sp_lastVillager = null;

// ── 输出 ──────────────────────────────────────────────────────────────────
function sp_send(ctx, text) {
    try { ctx.source.sendFailure(SP_Component.literal(text)); } catch (e) { /* 忽略 */ }
}

function sp_exText(e) {
    var msg;
    try { msg = '' + e; } catch (x) { msg = '<unprintable>'; }
    return ('' + msg).replace(/[\r\n]+/g, ' ').substring(0, 500);
}

function sp_guard(ctx, tag, fn) {
    try {
        return fn();
    } catch (err) {
        sp_send(ctx, 'AP_' + tag + '_EX:' + sp_exText(err));
        return 0;
    }
}

/**
 * 需要「商店菜单已打开」的命令专用：
 * 单独注入的聊天命令**无法在界面开着时送达**（T 键被 GUI 吞掉，实测教训）
 * ⇒ 这里由服务端**自己的命令执行线程**先把菜单开出来，跑完再关掉（同一 tick 内完成），
 *   客户端只会看到 Open→Close 两个包，最终屏幕是关的 ⇒ 下一条命令照常注入。
 * 只包在需要的命令上（menu/buy/buyseq/exchange/tlist/transfer/tbad）。
 */
/** 关菜单三件套(astral_bugfix_probe.js#teruCloseMenu 同口径)。
 *  ⚠️ 白名单实测:ServerPlayer#closeContainer 在 KubeJS Rhino 里不可见(抛 TypeError),
 *    而它的两个副作用必须补上:① containerMenu.removed(player)(冷却顺延挂在里面)
 *    ② 给客户端发 ClientboundContainerClosePacket(否则客户端 GUI 一直挂着,吃掉后续聊天注入)。
 */
function sp_closeMenu(p) {
    var how = [];
    var id = -1;
    try { id = (p.containerMenu == null) ? -1 : p.containerMenu.containerId; } catch (e0) { }
    try { p.closeContainer(); how.push('closeContainer'); } catch (e1) { how.push('closeContainer=EX'); }
    try { p.containerMenu.removed(p); how.push('removed'); } catch (e2) { how.push('removed=EX'); }
    if (SP_ClosePacket != null && id >= 0) {
        try { p.connection.send(new SP_ClosePacket(id)); how.push('packet=' + id); } catch (e3) { how.push('packet=EX'); }
    } else { how.push('noPacketClsOrId'); }
    try { p.containerMenu = p.inventoryMenu; how.push('reset'); } catch (e4) { how.push('reset=EX'); }
    return how.join('+');
}

function sp_guardMenu(ctx, tag, fn) {
    var p = sp_p(ctx);
    var opened = 0;
    if (sp_shopMenuOf(p) == null) {
        try {
            SP_Opener.open(p);
            opened = 1;
        } catch (e) {
            sp_send(ctx, 'AP_' + tag + '_MENULEASE:open_ex=' + sp_exText(e));
        }
    }
    try {
        return sp_guard(ctx, tag, fn);
    } finally {
        if (opened) {
            try {
                sp_closeMenu(p);
                sp_send(ctx, 'AP_' + tag + '_MENULEASE:opened=1:closed=1');
            } catch (e2) {
                sp_send(ctx, 'AP_' + tag + '_MENULEASE:opened=1:close_ex=' + sp_exText(e2));
            }
        }
    }
}

/** 筹码商店版 sp_guardMenu：单独注入的聊天命令在界面开着时送不到（T 键被 GUI 吞掉，实测教训），
 *  而 cmenu / cbuy / crefresh 需要服务端手里有 ChipShopMenu ⇒ 由服务端自己开店、跑完同 tick 关掉。
 *  ⚠️ 不能复用 sp_guardMenu：它先用 SP_Opener（卡牌商店）开店、且只认 CardShopMenu（sp_shopMenuOf）。
 *  ⚠️ 只有**取不到菜单**时才由本函数开店；已开着（如 cblock 的右键路径）则原样跑，跑完不关。 */
function cp_guardMenu(ctx, tag, fn) {
    var p = sp_p(ctx);
    var opened = 0;
    if (cp_menuOf(p) == null) {
        try {
            SP_ChipOpener.open(p);
            opened = (cp_menuOf(p) == null) ? 0 : 1;
        } catch (e) {
            sp_send(ctx, 'AP_' + tag + '_CPMENULEASE:open_ex=' + sp_exText(e));
        }
    }
    try {
        return sp_guard(ctx, tag, fn);
    } finally {
        if (opened) {
            try {
                sp_closeMenu(p);
                sp_send(ctx, 'AP_' + tag + '_CPMENULEASE:opened=1:closed=1');
            } catch (e2) {
                sp_send(ctx, 'AP_' + tag + '_CPMENULEASE:opened=1:close_ex=' + sp_exText(e2));
            }
        }
    }
}

// ── 基础工具 ──────────────────────────────────────────────────────────────
function sp_p(ctx) { return ctx.source.getPlayer(); }

function sp_level(p) {
    try { var l = p.level; if (l != null) return l; } catch (e) { }
    try { return p.level(); } catch (e2) { }
    return null;
}

function sp_cls(o) {
    if (o == null) return 'null';
    // 1.21.1 Rhino 实测:o.getClass().getSimpleName() 在本运行时抛异常(Class 成员被过滤)
    // ⇒ 主路径走 toString(形如 com.x.y.CardShopMenu@1a2b3c),再退到 getClass().getName()。
    try {
        var s = '' + o;
        var m = /(?:^|[.\s])([A-Za-z0-9_$]+)@[0-9a-fA-F]+/.exec(s);
        if (m != null) return m[1];
    } catch (e) { }
    try {
        var n = '' + o.getClass().getName();
        var i = n.lastIndexOf('.');
        return i < 0 ? n : n.substring(i + 1);
    } catch (e2) { }
    return '<cls?>';
}

function sp_id(stack) {
    if (stack == null) return 'empty';
    try { if (stack.isEmpty()) return 'empty'; } catch (e) { return '<empty?>'; }
    var key = null;
    try { key = SP_Reg.ITEM.getKey(stack.getItem()); } catch (e2) { return '<id?>'; }
    if (key == null) return 'empty';
    var sid = '' + key;
    if (sid === 'null' || sid === 'minecraft:air' || sid === '') return 'empty';
    return sid;
}

function sp_itemIdOf(item) {
    try { return SP_Reg.ITEM.getKey(item).toString(); } catch (e) { return '<id?>'; }
}

function sp_item(idStr) {
    var it = SP_Reg.ITEM.get(new SP_RL(idStr));
    if (it == null || sp_itemIdOf(it) !== idStr) return null;
    return it;
}

function sp_stack(idStr) {
    var it = sp_item(idStr);
    return it == null ? null : new SP_ItemStack(it);
}

/** sp_id 读出的值是否等于"空"(兼容历史形态 null / air) */
function sp_isEmptyId(s) {
    return s === 'empty' || s === 'null' || s === '' || s === '<empty?>' || s === 'minecraft:air';
}

/** 命令参数兜底:剥掉 Brigadier greedyString 会一并交过来的包裹引号(greedyString 不吃引号语法) */
function sp_unq(s) {
    var t = '' + s;
    if (t.length >= 2) {
        var a = t.charAt(0); var b = t.charAt(t.length - 1);
        if ((a === '"' && b === '"') || (a === "'" && b === "'")) t = t.substring(1, t.length - 1);
    }
    return t.trim();
}

/** 长度:兼容 JS 数组 / Java List(Collection) —— KubeJS 里 Java List 的 .length 是 undefined */
function sp_len(x) {
    if (x == null) return -1;
    try { if (typeof x.length === 'number') return x.length; } catch (e0) { }
    try { var s = x.size(); if (typeof s === 'number') return s; } catch (e1) { }
    try { return Java.from(x).length; } catch (e2) { }
    return -1;
}

/** 取下标:兼容 JS 数组 / Java List */
function sp_at(x, i) {
    if (x == null) return null;
    try { var v = x[i]; if (v !== undefined && v !== null) return v; } catch (e0) { }
    try { return x.get(i); } catch (e1) { }
    return null;
}

/** 「可售」判据:走产品侧非重载出口 describe(stack),绕开 Rhino 对 isSellable(Item)/isSellable(ItemStack) 的重载消歧失败 */
function sp_describeSellable(st) {
    if (st == null) return false;
    try {
        var d = '' + SP_Filter.describe(st);
        var m = /可售=(true|false)/.exec(d);
        if (m != null) return m[1] === 'true';
        return d.indexOf('可售=true') >= 0;
    } catch (e) { return false; }
}

function sp_n(x) { return Math.round((x + 0.0) * 100) / 100; }

function sp_tick(p) {
    try { return sp_level(p).getLevelData().getGameTime(); } catch (e) { return -1; }
}

function sp_bal(p) {
    try { return SP_Econ.getBalance(p); } catch (e) { return -1; }
}

function sp_heal(p) { try { return SP_Healing.getPoints(p); } catch (e) { return -1; } }
function sp_teru(p) { try { return SP_Teru.getLayers(p); } catch (e) { return -1; } }
function sp_star(p) { try { return SP_ModAttachments.getStarlight(p); } catch (e) { return -1; } }
function sp_cd(p) { try { return SP_ModAttachments.getShopTransferCooldownEnd(p); } catch (e) { return -1; } }
function sp_diceOn(p) { try { return SP_DiceTrade.hasEmeraldDice(p) ? 1 : 0; } catch (e) { return -1; } }

function sp_setBal(p, want) {
    var cur = sp_bal(p);
    if (cur < 0) return -1;
    if (want > cur) { try { SP_Econ.deposit(p, want - cur); } catch (e) { return -2; } }
    else if (want < cur) { try { SP_Econ.withdraw(p, cur - want); } catch (e2) { return -3; } }
    return sp_bal(p);
}

function sp_menu(p) { return sp_shopMenuOf(p); }

function sp_shopMenuOf(p) {
    var m = null;
    try { m = p.containerMenu; } catch (e) { return null; }
    if (m == null) return null;
    if (sp_cls(m) !== 'CardShopMenu') return null;
    return m;
}

function sp_curioStack(p, slotId, index) {
    try {
        var opt = SP_Curios.getCuriosInventory(p);
        if (opt == null || !opt.isPresent()) return null;
        var h = opt.resolve().get().getStacksHandler(slotId);
        if (h == null || !h.isPresent()) return null;
        var stacks = h.get().getStacks();
        if (stacks.getSlots() <= index) return null;
        return stacks.getStackInSlot(index);
    } catch (e) { return null; }
}

function sp_setCurio(p, slotId, idStr, index) {
    try {
        var opt = SP_Curios.getCuriosInventory(p);
        if (opt == null || !opt.isPresent()) return 'no_curios';
        var h = opt.resolve().get().getStacksHandler(slotId);
        if (h == null || !h.isPresent()) return 'no_slot';
        var stacks = h.get().getStacks();
        // 1.20.1 实测:未装骰子时 chip 槽 handler 的 getSlots()==0 ⇒ 归零方向本就无事可做,算成功
        if (stacks.getSlots() <= index) {
            if (idStr == null || idStr === 'none') return null;
            return 'slot_overflow';
        }
        if (idStr == null || idStr === 'none') {
            stacks.setStackInSlot(index, SP_ItemStack.EMPTY);
            return null;
        }
        var it = sp_item(idStr);
        if (it == null) return 'unknown_item';
        stacks.setStackInSlot(index, SP_ItemStack.EMPTY);
        stacks.setStackInSlot(index, new SP_ItemStack(it));
        return null;
    } catch (e) { return 'err:' + sp_exText(e); }
}

// ── 货架读数 ──────────────────────────────────────────────────────────────
function sp_shelfItems(menu) {
    var out = [];
    var n = 0;
    try { n = menu.shelfSize(); } catch (e) { n = 0; }
    for (var i = 0; i < n; i++) {
        var st = null;
        try { st = menu.getSlot(i).getItem(); } catch (e2) { st = null; }
        out.push({ idx: i, id: sp_id(st), price: sp_price(menu, i), sold: sp_bool(menu.isSold(i)), disc: sp_bool(menu.isDiscounted(i)) });
    }
    return out;
}

function sp_price(menu, i) { try { return menu.priceOf(i); } catch (e) { return -1; } }
function sp_bool(b) { try { return b ? 1 : 0; } catch (e) { return -1; } }

function sp_shelfCsv(items) {
    var parts = [];
    for (var i = 0; i < items.length; i++) {
        parts.push(items[i].idx + ':' + items[i].id + ':' + items[i].price + ':' + items[i].sold + ':' + items[i].disc);
    }
    return parts.join(',');
}

function sp_menuLine(ctx, tag, menu) {
    if (menu == null) { sp_send(ctx, 'AP_' + tag + '_MENU:cls=none'); return; }
    var items = sp_shelfItems(menu);
    var extra = '';
    try { extra = ':promo=' + sp_bool(menu.isPromotionActive()) + ':cd=' + menu.transferCooldownSeconds() + ':used=' + sp_bool(menu.isTransferUsedThisSession()); } catch (e) { extra = ':extra?'; }
    sp_send(ctx, 'AP_' + tag + '_MENU:cls=CardShopMenu:shelf=' + items.length + ':bal=' + sp_bal(sp_p(ctx)) + extra);
    for (var i = 0; i < items.length; i++) {
        sp_send(ctx, 'AP_' + tag + '_SHELF:' + items[i].idx + ':' + items[i].id + ':' + items[i].price + ':' + items[i].sold + ':' + items[i].disc);
    }
}

// ── probe / reset ─────────────────────────────────────────────────────────
function sp_doProbe(ctx, tag) {
    var p = sp_p(ctx);
    sp_send(ctx, 'AP_' + tag + '_PROBE:econ=' + (SP_Econ == null ? 'NOCLASS' : (SP_Econ.isAvailable() ? 1 : 0))
        + ':bal=' + sp_bal(p) + ':star=' + sp_star(p) + ':heal=' + sp_heal(p)
        + ':dice=' + sp_diceOn(p) + ':diceSlot=' + sp_id(sp_curioStack(p, 'dice', 0))
        + ':stand=' + sp_id(sp_curioStack(p, 'stand', 0)) + ':chip=' + sp_id(sp_curioStack(p, 'chip', 0))
        + ':menu=' + sp_cls(p.containerMenu) + ':tick=' + sp_tick(p));
    return 1;
}

function sp_doReset(ctx, tag) {
    var p = sp_p(ctx);
    try { SP_ModAttachments.setShopTransferCooldownEnd(p, 0); } catch (e) { }
    sp_setBal(p, 0);
    try { SP_ModAttachments.setStarlight(p, 0); } catch (e2) { }
    sp_send(ctx, 'AP_' + tag + '_RESET:bal=' + sp_bal(p) + ':star=' + sp_star(p) + ':cdEnd=' + sp_cd(p));
    return 1;
}

// ── 工作方块 / POI / 职业谓词 / 真实右键 ───────────────────────────────────
function sp_doBlock(ctx, tag) {
    var p = sp_p(ctx);
    var lvl = sp_level(p);
    // Rhino 实测(1.21.1 现象;1.20.1 同):p.getYRot() → TypeError: Cannot find function getYRot(Entity 方法表不可见)
    // ⇒ 不依赖玩家朝向,固定在脚边 +X 2 格,保证可复算。
    var pos = p.blockPosition().offset(2, 0, 0);
    var block = SP_ModBlocks.CARD_SHOP.get();
    var ok = lvl.setBlockAndUpdate(pos, block.defaultBlockState());
    var placed = sp_id(new SP_ItemStack(lvl.getBlockState(pos).getBlock().asItem()));
    var poi = -1;
    try { poi = lvl.getPoiManager().existsAtPosition(SP_ModPoiTypes.CARD_SHOP_KEY, pos) ? 1 : 0; } catch (e) { poi = -1; }
    var pred = -1;
    var acq = -1;
    try {
        var holder = SP_Reg.POINT_OF_INTEREST_TYPE.wrapAsHolder(SP_ModPoiTypes.CARD_SHOP.get());
        var prof = SP_ModProfessions.CARD_TRADER.get();
        pred = prof.heldJobSite().test(holder) ? 1 : 0;
        acq = prof.acquirableJobSite().test(holder) ? 1 : 0;
        sp_send(ctx, 'AP_' + tag + '_PROF:id=' + SP_Reg.VILLAGER_PROFESSION.getKey(prof).toString()
            + ':name=' + prof.name() + ':poiId=' + SP_Reg.POINT_OF_INTEREST_TYPE.getKey(SP_ModPoiTypes.CARD_SHOP.get()).toString());
    } catch (e2) { sp_send(ctx, 'AP_' + tag + '_PROF:ex=' + sp_exText(e2)); }
    sp_send(ctx, 'AP_' + tag + '_BLOCK:set=' + (ok ? 1 : 0) + ':place=' + placed + ':pos=' + pos.getX() + ',' + pos.getY() + ',' + pos.getZ()
        + ':poi=' + poi + ':pred=' + pred + ':acq=' + acq);

    // 真实右键:1.20.1 无 useWithoutItem ⇒ BlockState#use(Level, Player, InteractionHand, BlockHitResult)
    var res = 'n/a';
    try {
        var st = lvl.getBlockState(pos);
        var hit = new SP_BlockHitResult(new SP_Vec3(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5), SP_Direction.UP, pos, false);
        var r = st.use(lvl, p, SP_Hand.MAIN_HAND, hit);
        res = '' + r;
    } catch (e3) { res = 'EX:' + sp_exText(e3); }
    sp_send(ctx, 'AP_' + tag + '_USE:res=' + res + ':menu=' + sp_cls(p.containerMenu));
    sp_menuLine(ctx, tag, sp_shopMenuOf(p));

    // 破坏方块 → POI 记录应消失
    var broke = 0;
    try { broke = lvl.removeBlock(pos, false) ? 1 : 0; } catch (e4) { broke = -1; }
    var poi2 = -1;
    try { poi2 = lvl.getPoiManager().existsAtPosition(SP_ModPoiTypes.CARD_SHOP_KEY, pos) ? 1 : 0; } catch (e5) { poi2 = -1; }
    var pred2 = -1;
    try { pred2 = SP_ModProfessions.CARD_TRADER.get().heldJobSite().test(SP_Reg.POINT_OF_INTEREST_TYPE.wrapAsHolder(SP_ModPoiTypes.CARD_SHOP.get())) ? 1 : 0; } catch (e6) { pred2 = -1; }
    sp_send(ctx, 'AP_' + tag + '_BREAK:broke=' + broke + ':air=' + sp_id(new SP_ItemStack(lvl.getBlockState(pos).getBlock().asItem())) + ':poi2=' + poi2 + ':pred2=' + pred2);
    // ⚠️ 真实右键会把 CardShopScreen 开到客户端；界面开着时后续注入的聊天命令会被 GUI 吞掉
    //    （SHOP-GUI 首跑实测教训：11:23:42 open 之后 client 侧再无任何命令回显）⇒ 这里必须关掉。
    var closed = 'n/a';
    try { sp_closeMenu(p); closed = '1'; } catch (e7) { closed = 'EX:' + sp_exText(e7); }
    sp_send(ctx, 'AP_' + tag + '_CLOSED:by=block:res=' + closed);
    return 1;
}

function sp_doOpen(ctx, tag) {
    var p = sp_p(ctx);
    SP_Opener.open(p);
    sp_send(ctx, 'AP_' + tag + '_OPEN:menu=' + sp_cls(p.containerMenu));
    sp_menuLine(ctx, tag, sp_shopMenuOf(p));
    // 界面开着 ⇒ 后续注入的聊天命令会被 GUI 吞掉(首跑教训) ⇒ 立刻关掉
    var closed = 'n/a';
    try { sp_closeMenu(p); closed = '1'; } catch (e) { closed = 'EX:' + sp_exText(e); }
    sp_send(ctx, 'AP_' + tag + '_CLOSED:by=open:res=' + closed);
    return 1;
}

/** 开店但**不关**：留给客户端脚本(astral_shop_client.js)按绝对坐标驱动 GUI。
 *  只能作为用例的最后一条注入命令（界面开着 ⇒ 后续聊天命令到不了服务端）。 */
function sp_doOpenKeep(ctx, tag) {
    var p = sp_p(ctx);
    SP_Opener.open(p);
    sp_send(ctx, 'AP_' + tag + '_OPENKEEP:menu=' + sp_cls(p.containerMenu));
    sp_menuLine(ctx, tag, sp_shopMenuOf(p));
    return 1;
}

/** 任务V §1.2 实机取证（2026-10-10 本轮新增）：证明「菜单**已经开着**时也立即补差额牌」。
 *  · 开菜单那一刻货架 = 当前目标格数（无缺口）⇒ 开店那条 topUpShelf(reason=open) 不补；
 *  · 随后在**同一个 tick 内**把经商立牌戴上（目标格数只看立牌：teamHasParunan / hasParunan 不要求骰子）
 *    ⇒ 目标 +1、出现缺口；
 *  · 应由 CardShopCycleTicker 每 10 tick 的界面复检以 reason=tick 补上（服务端日志 CARD_SHOP_SHELF_TOPUP）。
 *  只能作为用例的最后一条注入命令（界面开着 ⇒ 后续聊天命令会被 GUI 吞掉）。
 *  ⚠️ 类别名一律在函数内内联解析（顶层 var 类别名在命令回调作用域取不到，见上方 promo 那条注记）。 */
function sp_doTickTest(ctx, tag) {
    var p = sp_p(ctx);
    SP_Opener.open(p);
    var menu = sp_shopMenuOf(p);
    sp_send(ctx, 'AP_' + tag + '_TICKTEST_OPEN:open=' + (menu != null ? 1 : 0) + ':shelf=' + sp_shelfCsvSafe(menu));
    var before = -1;
    var after = -1;
    try { before = spTryClass('com.merlinkitsune.astral_dice.shop.CardShopShelf').targetSize(p); } catch (eT1) { }
    var eq = sp_setCurio(p, 'stand', 'astral_dice:parunan_sign', 0);
    try { after = spTryClass('com.merlinkitsune.astral_dice.shop.CardShopShelf').targetSize(p); } catch (eT2) { }
    sp_send(ctx, 'AP_' + tag + '_TICKTEST:equip=' + (eq == null ? 'ok' : eq)
        + ':targetBefore=' + before + ':targetAfter=' + after
        + ':stand=' + sp_id(sp_curioStack(p, 'stand', 0)) + ':cd=' + sp_cd(p));
    return 1;
}

function sp_doMenu(ctx, tag) {
    sp_menuLine(ctx, tag, sp_shopMenuOf(sp_p(ctx)));
    return 1;
}

// ── 购买 ──────────────────────────────────────────────────────────────────
function sp_buyProbe(ctx, tag, menu, p, i, slot, label) {
    var items = sp_shelfItems(menu);
    var before = sp_bal(p);
    var invBefore = sp_id(p.getInventory().getItem(slot));
    var healBefore = sp_heal(p);
    var teruBefore = sp_teru(p);
    menu.handleBuy(p, i, slot);
    var after = sp_bal(p);
    var invAfter = sp_id(p.getInventory().getItem(slot));
    var soldLine = '';
    try { soldLine = ':sold' + i + '=' + sp_bool(menu.isSold(i)); } catch (e) { soldLine = ':sold?'; }
    var shelfAfter = '';
    try { shelfAfter = ':shelf' + i + '=' + sp_id(menu.getSlot(i).getItem()); } catch (e2) { shelfAfter = ''; }
    var price = (i >= 0 && i < items.length) ? items[i].price : -1;
    sp_send(ctx, 'AP_' + tag + '_BUY:step=' + label + ':i=' + i + ':slot=' + slot + ':price=' + price
        + ':bal=' + before + '->' + after + ':inv=' + invBefore + '->' + invAfter
        + soldLine + shelfAfter
        + ':heal=' + healBefore + '->' + sp_heal(p) + ':teru=' + teruBefore + '->' + sp_teru(p));
    return after !== before;
}

function sp_doBuy(ctx, tag, i, slot) {
    var p = sp_p(ctx);
    var menu = sp_shopMenuOf(p);
    if (menu == null) { sp_send(ctx, 'AP_' + tag + '_BUY:step=no_menu'); return 0; }
    sp_buyProbe(ctx, tag, menu, p, i, slot, 'single');
    return 1;
}

function sp_doBuySeq(ctx, tag) {
    var p = sp_p(ctx);
    var menu = sp_shopMenuOf(p);
    if (menu == null) { sp_send(ctx, 'AP_' + tag + '_BUYSEQ:no_menu=1'); return 0; }
    // 归一化:清空玩家 36 格
    for (var k = 0; k < 36; k++) { p.getInventory().setItem(k, SP_ItemStack.EMPTY); }
    sp_setBal(p, 20);
    sp_send(ctx, 'AP_' + tag + '_BUYSEQ:start=1:bal=' + sp_bal(p) + ':shelf=' + sp_shelfCsv(sp_shelfItems(menu)));
    var pass = 0; var total = 0;
    // S1 正常购买(shelf 0 → inv 20)
    total++;
    var s1before = sp_bal(p);
    sp_buyProbe(ctx, tag, menu, p, 0, 20, 's1_normal');
    if (sp_bal(p) < s1before && !sp_isEmptyId(sp_id(p.getInventory().getItem(20)))) pass++;
    // S2 已售出:同格再买(应无变化)
    total++;
    var s2before = sp_bal(p);
    sp_buyProbe(ctx, tag, menu, p, 0, 21, 's2_sold');
    if (sp_bal(p) === s2before && sp_isEmptyId(sp_id(p.getInventory().getItem(21)))) pass++;
    // S3 货架越界
    total++;
    var s3before = sp_bal(p);
    sp_buyProbe(ctx, tag, menu, p, 99, 21, 's3_range');
    if (sp_bal(p) === s3before) pass++;
    // S4 落点被占用(inv 22 放泥土)
    total++;
    p.getInventory().setItem(22, new SP_ItemStack(SP_Items.DIRT));
    var s4before = sp_bal(p);
    sp_buyProbe(ctx, tag, menu, p, 1, 22, 's4_occupied');
    if (sp_bal(p) === s4before && sp_id(p.getInventory().getItem(22)) === 'minecraft:dirt') pass++;
    // S5 余额 0
    total++;
    sp_setBal(p, 0);
    sp_buyProbe(ctx, tag, menu, p, 1, 23, 's5_zero');
    if (sp_bal(p) === 0 && sp_isEmptyId(sp_id(p.getInventory().getItem(23)))) pass++;
    // S6 余额不足(1 < 价格)
    total++;
    sp_setBal(p, 1);
    sp_buyProbe(ctx, tag, menu, p, 1, 24, 's6_short');
    if (sp_bal(p) === 1 && sp_isEmptyId(sp_id(p.getInventory().getItem(24)))) pass++;
    // S7 补足余额后成功(证明前序失败非不可逆)
    total++;
    sp_setBal(p, 20);
    var s7before = sp_bal(p);
    sp_buyProbe(ctx, tag, menu, p, 1, 24, 's7_retry');
    if (sp_bal(p) < s7before && !sp_isEmptyId(sp_id(p.getInventory().getItem(24)))) pass++;
    // S8 负数下标
    total++;
    var s8before = sp_bal(p);
    sp_buyProbe(ctx, tag, menu, p, -1, 25, 's8_negative');
    if (sp_bal(p) === s8before) pass++;
    // S9 目标槽越界
    total++;
    var s9before = sp_bal(p);
    sp_buyProbe(ctx, tag, menu, p, 2, 99, 's9_slot_range');
    if (sp_bal(p) === s9before) pass++;
    sp_send(ctx, 'AP_' + tag + '_BUYSUM:steps=' + total + ':pass=' + pass + ':inv20=' + sp_id(p.getInventory().getItem(20)) + ':inv24=' + sp_id(p.getInventory().getItem(24)) + ':bal=' + sp_bal(p));
    return 1;
}

function sp_doBal(ctx, tag, n) {
    var p = sp_p(ctx);
    var got = sp_setBal(p, n);
    sp_send(ctx, 'AP_' + tag + '_BAL:want=' + n + ':got=' + got);
    return 1;
}

function sp_doStar(ctx, tag, n) {
    var p = sp_p(ctx);
    SP_ModAttachments.setStarlight(p, n);
    sp_send(ctx, 'AP_' + tag + '_STAR:want=' + n + ':got=' + sp_star(p));
    return 1;
}

function sp_doExchange(ctx, tag) {
    var p = sp_p(ctx);
    var menu = sp_shopMenuOf(p);
    if (menu == null) { sp_send(ctx, 'AP_' + tag + '_EXCH:no_menu=1'); return 0; }
    var b0 = sp_bal(p); var s0 = sp_star(p);
    var r = false;
    try { r = menu.clickMenuButton(p, 17); } catch (e) { sp_send(ctx, 'AP_' + tag + '_EXCH:ex=' + sp_exText(e)); }
    sp_send(ctx, 'AP_' + tag + '_EXCH:ret=' + (r ? 1 : 0) + ':star=' + s0 + '->' + sp_star(p) + ':bal=' + b0 + '->' + sp_bal(p));
    return 1;
}

// ── 转账 ──────────────────────────────────────────────────────────────────
function sp_pl(p) {
    var s = null;
    try { s = p.server; } catch (e0) { }
    if (s == null) { try { s = p.getServer(); } catch (e1) { } }
    if (s == null) { try { s = sp_level(p).getServer(); } catch (e2) { } }
    if (s == null) return null;
    try { return s.getPlayerList(); } catch (e3) { return null; }
}

var sp_candErr = 'none';

function sp_candName(o) {
    try { return '' + o.getName().getString(); } catch (e1) { }
    try { return '' + o.name; } catch (e2) { }
    try { return '' + o.scoreboardName; } catch (e3) { }
    try { return '' + o.getScoreboardName(); } catch (e4) { }
    return '<noname>';
}

function sp_candUuid(o) {
    try { return '' + o.uuid; } catch (e1) { }
    try { return '' + o.getUUID(); } catch (e2) { }
    return null;
}

function sp_plList(p) {
    var pl = sp_pl(p);
    if (pl == null) { sp_candErr = 'no_playerlist'; return null; }
    var list = null;
    try { list = pl.getPlayers(); } catch (e) { sp_candErr = 'getPlayers:' + sp_exText(e); return null; }
    if (list == null) { sp_candErr = 'players_null'; return null; }
    return list;
}

function sp_candidates(p) {
    var out = [];
    sp_candErr = 'none';
    var list = sp_plList(p);
    if (list == null) return out;
    var n = 0;
    try { n = list.size(); } catch (e1) { sp_candErr = 'size:' + sp_exText(e1); return out; }
    var pu = null; try { pu = sp_candUuid(p); } catch (e2) { }
    for (var i = 0; i < n; i++) {
        var o = null;
        try { o = list.get(i); } catch (e3) { sp_candErr = 'get(' + i + '):' + sp_exText(e3); continue; }
        if (o == null) continue;
        try {
            var ou = sp_candUuid(o);
            if (ou != null && pu != null && ou === pu) continue;
            out.push({ name: sp_candName(o), bal: sp_bal(o), uuid: (ou == null ? '?' : ou) });
        } catch (e4) { sp_candErr = 'elem(' + i + '):' + sp_exText(e4); }
    }
    return out;
}

function sp_findPlayer(p, name) {
    var list = sp_plList(p);
    if (list == null) return null;
    var n = 0;
    try { n = list.size(); } catch (e1) { return null; }
    for (var i = 0; i < n; i++) {
        var o = null;
        try { o = list.get(i); } catch (e2) { continue; }
        if (o == null) continue;
        if (sp_candName(o) === name) return o;
    }
    return null;
}

function sp_doTList(ctx, tag) {
    var p = sp_p(ctx);
    var menu = sp_shopMenuOf(p);
    var names = [];
    var cs = sp_candidates(p);
    for (var i = 0; i < cs.length; i++) names.push(i + '=' + cs[i].name + '@' + cs[i].bal);
    sp_send(ctx, 'AP_' + tag + '_TCAND:n=' + cs.length + ':list=' + names.join('|'));
    if (cs.length === 0) sp_send(ctx, 'AP_' + tag + '_TCANDERR:err=' + sp_candErr + ':pl=' + sp_cls(sp_pl(p)));
    if (menu != null) {
        try { menu.sendTransferCandidates(p); } catch (e) { sp_send(ctx, 'AP_' + tag + '_TCAND:ex=' + sp_exText(e)); }
        sp_send(ctx, 'AP_' + tag + '_TSENT:n=' + cs.length + ':used=' + sp_bool(menu.isTransferUsedThisSession()));
    } else {
        sp_send(ctx, 'AP_' + tag + '_TSENT:no_menu=1');
    }
    return 1;
}

function sp_doTransfer(ctx, tag, name, amount) {
    var p = sp_p(ctx);
    var menu = sp_shopMenuOf(p);
    if (menu == null) { sp_send(ctx, 'AP_' + tag + '_TX:no_menu=1'); return 0; }
    var target = sp_findPlayer(p, name);
    if (target == null) { sp_send(ctx, 'AP_' + tag + '_TX:no_target=' + name); return 0; }
    var cs = sp_candidates(p);
    var idx = -1;
    for (var i = 0; i < cs.length; i++) { if (cs[i].name === name) idx = i; }
    try { menu.sendTransferCandidates(p); } catch (e) { }
    var b0 = sp_bal(p); var c0 = sp_bal(target);
    var cd0 = sp_cd(p); var n0 = sp_tick(p);
    menu.handleTransfer(p, idx, amount);
    var b1 = sp_bal(p); var c1 = sp_bal(target);
    var cd1 = sp_cd(p); var n1 = sp_tick(p);
    var used1 = sp_bool(menu.isTransferUsedThisSession());
    sp_send(ctx, 'AP_' + tag + '_TX:target=' + name + ':idx=' + idx + ':cand=' + cs.length + ':amount=' + amount
        + ':bal=' + b0 + '->' + b1 + ':tbal=' + c0 + '->' + c1
        + ':used=' + used1 + ':cdBefore=' + cd0 + ':cdAfter=' + cd1 + ':now=' + n0 + '->' + n1
        + ':applied=' + ((b1 !== b0) ? 1 : 0) + ':cdSec=' + menu.transferCooldownSeconds());
    // 会话锁:同店二次转账(应被静默拒绝)
    menu.handleTransfer(p, idx, amount);
    sp_send(ctx, 'AP_' + tag + '_TX2:bal=' + b1 + '->' + sp_bal(p) + ':tbal=' + c1 + '->' + sp_bal(target)
        + ':used=' + sp_bool(menu.isTransferUsedThisSession()) + ':applied=' + ((sp_bal(p) !== b1) ? 1 : 0));
    return 1;
}

/** 顺延自证:店内转账后**故意不关菜单**(绕过 guardMenu 的自动收屏),让后续 close 在**不同 tick** 观察 removed() 顺延 */
function sp_doTxKeep(ctx, tag, name, amount) {
    var p = sp_p(ctx);
    var menu = sp_shopMenuOf(p);
    if (menu != null) { try { sp_closeMenu(p); } catch (eC) { } }
    try { SP_ModAttachments.setShopTransferCooldownEnd(p, 0); } catch (eCD) { }
    try { SP_Opener.open(p); } catch (eO) { sp_send(ctx, 'AP_' + tag + '_TXKEEP:open_ex=' + sp_exText(eO)); return 0; }
    menu = sp_shopMenuOf(p);
    if (menu == null) { sp_send(ctx, 'AP_' + tag + '_TXKEEP:no_menu=1'); return 0; }
    var target = sp_findPlayer(p, name);
    var cs = sp_candidates(p);
    var idx = -1;
    for (var i = 0; i < cs.length; i++) { if (cs[i].name === name) idx = i; }
    try { menu.sendTransferCandidates(p); } catch (eS) { }
    var b0 = sp_bal(p); var c0 = target == null ? -1 : sp_bal(target);
    var n0 = sp_tick(p); var cd0 = sp_cd(p);
    try { menu.handleTransfer(p, idx, amount); } catch (eT) { sp_send(ctx, 'AP_' + tag + '_TXKEEP:handle_ex=' + sp_exText(eT)); return 0; }
    sp_send(ctx, 'AP_' + tag + '_TXKEEP:target=' + name + ':idx=' + idx + ':cand=' + cs.length + ':amount=' + amount
        + ':bal=' + b0 + '->' + sp_bal(p) + ':tbal=' + c0 + '->' + (target == null ? -1 : sp_bal(target))
        + ':applied=' + ((sp_bal(p) !== b0) ? 1 : 0) + ':cdBefore=' + cd0 + ':cdAfter=' + sp_cd(p)
        + ':now=' + n0 + ':used=' + sp_bool(menu.isTransferUsedThisSession()) + ':menuOpen=' + (sp_shopMenuOf(p) == null ? 0 : 1));
    return 1;
}

function sp_doTBad(ctx, tag) {
    var p = sp_p(ctx);
    var menu = sp_shopMenuOf(p);
    if (menu == null) { sp_send(ctx, 'AP_' + tag + '_TBAD:no_menu=1'); return 0; }
    var cs = sp_candidates(p);
    if (cs.length === 0) { sp_send(ctx, 'AP_' + tag + '_TBAD:no_cand=1'); return 0; }
    var target = sp_findPlayer(p, cs[0].name);
    var amounts = [0, 4, 21, -1];
    for (var i = 0; i < amounts.length; i++) {
        var b0 = sp_bal(p); var c0 = target == null ? -1 : sp_bal(target);
        menu.handleTransfer(p, 0, amounts[i]);
        sp_send(ctx, 'AP_' + tag + '_TBAD:amount=' + amounts[i] + ':bal=' + b0 + '->' + sp_bal(p)
            + ':tbal=' + c0 + '->' + (target == null ? -1 : sp_bal(target))
            + ':applied=' + ((sp_bal(p) !== b0) ? 1 : 0) + ':used=' + sp_bool(menu.isTransferUsedThisSession()));
    }
    return 1;
}

function sp_doClose(ctx, tag) {
    var p = sp_p(ctx);
    var menu = sp_shopMenuOf(p);
    var opened = 0;
    var tApplied = -1;
    // 语义:自证「店内转账 → 出店」全过程(前序命令的 guardMenu 会把菜单收掉,故必要时先重开)
    if (menu == null) {
        try { SP_Opener.open(p); } catch (eO) { }
        menu = sp_shopMenuOf(p);
        if (menu != null) opened = 1;
    }
    if (opened === 1 && menu != null) {
        try {
            menu.sendTransferCandidates(p);
            var cs = sp_candidates(p);
            if (cs.length === 0) tApplied = 0;
            else {
                var idx = 0;
                for (var i = 0; i < cs.length; i++) { if (cs[i].name === 'Bot1') idx = i; }
                menu.handleTransfer(p, idx, 10);
                tApplied = 1;
            }
        } catch (eT) { tApplied = -2; }
    }
    var cd0 = sp_cd(p); var n0 = sp_tick(p);
    var used = menu == null ? -1 : sp_bool(menu.isTransferUsedThisSession());
    var cdMid = sp_cd(p);
    var how = 'closeContainer';
    try { sp_closeMenu(p); } catch (e) {
        how = 'menu.removed';
        if (menu != null) { try { menu.removed(p); } catch (e2) { how = 'failed:' + sp_exText(e2); } }
    }
    var cdAfter = sp_cd(p);
    sp_send(ctx, 'AP_' + tag + '_CLOSE:how=' + how + ':opened=' + opened + ':used=' + used + ':tApplied=' + tApplied
        + ':cdBefore=' + cd0 + ':cdMid=' + cdMid + ':cdAfter=' + cdAfter
        + ':now=' + n0 + '->' + sp_tick(p) + ':delta=' + (cdAfter - cd0) + ':deltaMid=' + (cdAfter - cdMid) + ':menu=' + sp_cls(p.containerMenu));
    return 1;
}

function sp_doReopen(ctx, tag) {
    var p = sp_p(ctx);
    SP_Opener.open(p);
    var menu = sp_shopMenuOf(p);
    var cdEndR = sp_cd(p); var nowR = sp_tick(p);
    var cdLeftSec = (cdEndR <= nowR) ? 0 : Math.floor((cdEndR - nowR + 19) / 20);
    sp_send(ctx, 'AP_' + tag + '_REOPEN:menu=' + sp_cls(p.containerMenu) + ':cdLeftSec=' + cdLeftSec + ':cdSec=' + (menu == null ? -1 : menu.transferCooldownSeconds())
        + ':cdEnd=' + sp_cd(p) + ':now=' + sp_tick(p) + ':used=' + (menu == null ? -1 : sp_bool(menu.isTransferUsedThisSession()))
        + ':bal=' + sp_bal(p));
    sp_menuLine(ctx, tag, menu);
    var closedR = 'n/a';
    try { sp_closeMenu(p); closedR = '1'; } catch (eR) { closedR = 'EX:' + sp_exText(eR); }
    sp_send(ctx, 'AP_' + tag + '_CLOSED:by=reopen:res=' + closedR);
    return 1;
}

// ── 骰子 / 立牌 / 筹码 ────────────────────────────────────────────────────
function sp_doDice(ctx, tag, mode) {
    var p = sp_p(ctx);
    var want = ('' + mode) === 'on';
    var err = sp_setCurio(p, 'dice', want ? 'astral_dice:emerald_dice' : 'none', 0);
    sp_send(ctx, 'AP_' + tag + '_DICE:want=' + (want ? 1 : 0) + ':err=' + (err == null ? 'none' : err)
        + ':slot=' + sp_curioStackText(p, 'dice') + ':on=' + sp_diceOn(p));
    return 1;
}

function sp_curioStackText(p, slot) {
    var st = sp_curioStack(p, slot, 0);
    return st == null ? '<no_slot>' : sp_id(st);
}

function sp_doEquip(ctx, tag, slot, idStr) {
    idStr = sp_unq(idStr);
    var p = sp_p(ctx);
    // chip 栏位尺寸由**当前佩戴的骰子**决定(curios/slots/chip.json size=0 ⇒ 未佩戴骰子时 0 格):
    // 直接调产品侧公开出口按 dice 槽现值重算,免去等 curioTick 的 20 tick 兜底。
    var chipSlots = -1;
    if (slot === 'chip') {
        try { SP_DiceCurio.refreshChipSlotCount(p); } catch (eR) { }
        try { var hh = SP_Curios.getCuriosInventory(p).resolve().get().getStacksHandler('chip');
              chipSlots = hh.isPresent() ? hh.get().getStacks().getSlots() : -2; } catch (eH) { }
    }
    var err = sp_setCurio(p, slot, idStr, 0);
    sp_send(ctx, 'AP_' + tag + '_EQUIP:slot=' + slot + ':want=' + idStr + ':err=' + (err == null ? 'none' : err)
        + ':got=' + sp_curioStackText(p, slot) + ':heal=' + sp_heal(p) + ':teru=' + sp_teru(p)
        + ':dice=' + sp_curioStackText(p, 'dice') + ':chipSlots=' + chipSlots);
    return 1;
}


/** 现场归零: 清三个 curio 槽 + 治愈点数 + 提尔狐光层数(存档会跨 launch 保留, 冷实例的 P0/OB2 断言必须靠它保证基线) */
function sp_doCurioReset(ctx, tag) {
    var p = sp_p(ctx);
    var eChip = sp_setCurio(p, 'chip', 'none', 0);
    var eStand = sp_setCurio(p, 'stand', 'none', 0);
    var eDice = sp_setCurio(p, 'dice', 'none', 0);
    var healHow = 'ok', teruHow = 'ok';
    try { SP_Healing.clear(p); } catch (eh) { healHow = sp_exText(eh); }
    try { SP_Teru.setLayers(p, 0); } catch (et) { teruHow = sp_exText(et); }
    sp_send(ctx, 'AP_' + tag + '_CRESET:chip=' + (eChip == null ? 'ok' : eChip) + ':stand=' + (eStand == null ? 'ok' : eStand)
        + ':dice=' + (eDice == null ? 'ok' : eDice) + ':heal=' + healHow + ':teru=' + teruHow
        + ':now=' + sp_curioStackText(p, 'dice') + '/' + sp_curioStackText(p, 'stand') + '/' + sp_curioStackText(p, 'chip')
        + ':on=' + sp_diceOn(p) + ':hp=' + sp_heal(p) + ':teruN=' + sp_teru(p));
    return 1;
}

function sp_doCurio(ctx, tag) {
    var p = sp_p(ctx);
    sp_send(ctx, 'AP_' + tag + '_CURIO:dice=' + sp_curioStackText(p, 'dice') + ':stand=' + sp_curioStackText(p, 'stand')
        + ':chip=' + sp_curioStackText(p, 'chip') + ':on=' + sp_diceOn(p) + ':heal=' + sp_heal(p) + ':teru=' + sp_teru(p));
    return 1;
}

/** 直接调产品侧「获得卡牌」出口:治愈 +1 / 提尔狐光层数 */
function sp_doObtain(ctx, tag, idStr) {
    idStr = sp_unq(idStr);
    var p = sp_p(ctx);
    var st = sp_stack(idStr);
    if (st == null) { sp_send(ctx, 'AP_' + tag + '_OBTAIN:bad_item=' + idStr); return 0; }
    var h0 = sp_heal(p); var t0 = sp_teru(p);
    SP_Vitamin.notifyCardObtained(p, st);
    sp_send(ctx, 'AP_' + tag + '_OBTAIN:item=' + idStr + ':heal=' + h0 + '->' + sp_heal(p) + ':teru=' + t0 + '->' + sp_teru(p)
        + ':dice=' + sp_diceOn(p) + ':stand=' + sp_curioStackText(p, 'stand') + ':chip=' + sp_curioStackText(p, 'chip'));
    return 1;
}

// ── 大酬宾事件探针（2026-10-10 夜，Forge 端实机验收新增；与 1.21.1 侧同口径）──────
function sp_shelfNSafe(menu) { if (menu == null) return -1; try { return sp_shelfItems(menu).length; } catch (e) { return -2; } }

function sp_shelfCsvSafe(menu) { if (menu == null) return 'none'; try { return sp_shelfCsv(sp_shelfItems(menu)); } catch (e) { return 'EX'; } }

function sp_soldCsvSafe(menu) {
    if (menu == null) return 'none';
    var n = sp_shelfNSafe(menu);
    if (n < 0) return 'EX';
    var out = [];
    try { for (var i = 0; i < n; i++) { out.push(i + ':' + sp_bool(menu.isSold(i))); } } catch (e) { return 'EX'; }
    return out.join(',');
}

/** 「商店大酬宾」事件探针：走产品入口 CardShopMenu.triggerPromotion ⇒ 立即**补差额牌**（+2 促销格），
 *  不重抽、不换批、不动计时器（任务U′ 口径）。机器行字段与 1.21.1 侧 AP_<tag>_PROMO 完全一致。 */
function sp_doPromo(ctx, tag, windowTicks) {
    var p = sp_p(ctx);
    var menu0 = sp_shopMenuOf(p);
    var sold0 = sp_soldCsvSafe(menu0);
    var shelf0 = sp_shelfCsvSafe(menu0);
    var cd0 = (menu0 == null ? -1 : menu0.transferCooldownSeconds());
    var cdEnd0 = sp_cd(p);
    var res = 'ok';
    try { spTryClass('com.merlinkitsune.astral_dice.shop.CardShopMenu').triggerPromotion(p, windowTicks); } catch (e) { res = 'EX:' + sp_exText(e); }
    var menu = sp_shopMenuOf(p);
    sp_send(ctx, 'AP_' + tag + '_PROMO:win=' + windowTicks + ':res=' + res
        + ':promo=' + (menu == null ? -1 : sp_bool(menu.isPromotionActive()))
        + ':cdBefore=' + cd0 + ':cdSec=' + (menu == null ? -1 : menu.transferCooldownSeconds())
        + ':cdEndBefore=' + cdEnd0 + ':cdEndAfter=' + sp_cd(p)
        + ':cdSame=' + ((cd0 === (menu == null ? -1 : menu.transferCooldownSeconds())) ? 1 : 0)
        + ':shelfNBefore=' + sp_shelfNSafe(menu0) + ':shelfNAfter=' + sp_shelfNSafe(menu)
        + ':used=' + (menu == null ? -1 : sp_bool(menu.isTransferUsedThisSession()))
        + ':soldBefore=' + sold0 + ':soldAfter=' + sp_soldCsvSafe(menu)
        + ':shelfBefore=' + shelf0 + ':shelfAfter=' + sp_shelfCsvSafe(menu));
    return 1;
}

// ── 卡池/排除项 ───────────────────────────────────────────────────────────
var SP_EXCLUSIVE = ['astral_dice:effect_card_living_page', 'astral_dice:effect_card_fate_guidance', 'astral_dice:fu_card',
    'astral_dice:huo_card', 'astral_dice:attack_card_bite', 'astral_dice:attack_card_dragon_roar'];
var SP_EXTRA_EXCLUDED = ['astral_dice:attack_card_full_power'];

function sp_inList(list, v) { for (var i = 0; i < list.length; i++) { if (list[i] === v) return true; } return false; }

// 2026-10-09 1.20.1 实机教训:这里原先读回 0 条且**无异常可见**(catch 静默吞),
// 而同一份代码在 1.21.1 正常 ⇒ 改走本会话已验证可用的 sp_len/sp_at(与 sp_doVPlan 取交易表同口径),
// 每处失败写成 :perr= 上报;若 catalog 路径整体不可用,回退到 rollShelf 抽样(与 AP_*_ROLL 同路径)。
var sp_poolSrc = 'catalog';
var sp_poolErr = 'none';

function sp_tierOfDesc(st) {
    try {
        var m = /档位=([A-Z]+)/.exec('' + SP_Filter.describe(st));
        return m == null ? 'null' : m[1];
    } catch (e) { return 'null'; }
}

function sp_poolSample() {
    var out = [];
    var seen = {};
    try {
        var rnd = SP_RandomSource.create();
        for (var i = 0; i < 200; i++) {
            var list = SP_Catalog.rollShelf(rnd, 5, true, true);
            var n = sp_len(list);
            for (var k = 0; k < n; k++) {
                var it = sp_at(list, k);
                var id = sp_itemIdOf(it);
                if (id === '<id?>' || seen[id] === 1) continue;
                seen[id] = 1;
                var tier = sp_tierOfDesc(new SP_ItemStack(it));
                out.push({ id: id, tier: tier,
                    weight: tier === 'RARE' ? 6 : (tier === 'EPIC' ? 3 : (tier === 'LEGENDARY' ? 2 : 0)),
                    minStar: id === 'astral_dice:attack_card_charge' ? 2 : 0,
                    combat: (id.indexOf('astral_dice:attack_card_') === 0 || id.indexOf('astral_dice:defense_card_') === 0) });
            }
        }
    } catch (e1) { }
    return out;
}

function sp_poolEntries() {
    var out = [];
    var errs = [];
    var es = null;
    try { es = SP_Catalog.entries(); } catch (e0) { errs.push('entries:' + sp_exText(e0)); }
    var n = sp_len(es);
    if (n < 0) errs.push('len');
    for (var i = 0; i < (n < 0 ? 0 : n); i++) {
        try {
            var e = sp_at(es, i);
            if (e == null) { errs.push('e' + i + ':null'); continue; }
            var it = e.item().get();
            out.push({ id: sp_itemIdOf(it), tier: '' + SP_Catalog.tierOf(e), weight: e.weight(), minStar: e.minStarLevel(), combat: e.combat() });
        } catch (e4) { errs.push('e' + i + ':' + sp_exText(e4)); }
    }
    sp_poolErr = (errs.length === 0 ? 'none' : errs.join(' / ')).replace(/\|/g, '/').substring(0, 240);
    if (out.length >= 22) { sp_poolSrc = 'catalog'; return out; }
    var fb = sp_poolSample();
    if (fb.length > out.length) { sp_poolSrc = 'roll'; return fb; }
    sp_poolSrc = 'catalog';
    return out;
}

function sp_doPool(ctx, tag) {
    var entries = sp_poolEntries();
    var counts = { RARE: 0, EPIC: 0, LEGENDARY: 0 };
    var bad = [];
    var ids = [];
    var exclHit = [];
    for (var i = 0; i < entries.length; i++) {
        var e = entries[i];
        ids.push(e.id);
        if (counts[e.tier] != null) counts[e.tier] = counts[e.tier] + 1;
        var st = sp_stack(e.id);
        var sellable = sp_describeSellable(st);
        if (!sellable) bad.push(e.id);
        if (sp_inList(SP_EXCLUSIVE, e.id) || sp_inList(SP_EXTRA_EXCLUDED, e.id)) exclHit.push(e.id);
    }
    sp_send(ctx, 'AP_' + tag + '_POOL:n=' + entries.length + ':rare=' + counts.RARE + ':epic=' + counts.EPIC
        + ':legend=' + counts.LEGENDARY + ':src=' + sp_poolSrc + ':perr=' + sp_poolErr
        + ':notSellable=' + (bad.length === 0 ? 'none' : bad.join('|'))
        + ':excludedInPool=' + (exclHit.length === 0 ? 'none' : exclHit.join('|')));
    for (var k = 0; k < entries.length; k++) {
        var st2 = sp_stack(entries[k].id);
        var desc = '<n/a>';
        try { desc = SP_Filter.describe(st2); } catch (e3) { desc = '<ex>'; }
        sp_send(ctx, 'AP_' + tag + '_POOLENTRY:' + entries[k].id + ':tier=' + entries[k].tier + ':w=' + entries[k].weight
            + ':minStar=' + entries[k].minStar + ':combat=' + (entries[k].combat ? 1 : 0) + ':desc=' + desc);
    }
    // 7 张排除项在物品注册表里的实际可售性(供交叉核对)
    var all = SP_EXCLUSIVE.concat(SP_EXTRA_EXCLUDED);
    for (var j = 0; j < all.length; j++) {
        var st3 = sp_stack(all[j]);
        var d3 = '<no_item>';
        var s3 = -1;
        if (st3 != null) { try { d3 = SP_Filter.describe(st3); } catch (e4) { d3 = '<ex>'; } s3 = sp_describeSellable(st3) ? 1 : 0; }
        sp_send(ctx, 'AP_' + tag + '_EXCL:' + all[j] + ':exists=' + (st3 == null ? 0 : 1) + ':sellable=' + s3 + ':desc=' + d3);
    }
    return 1;
}

function sp_doRoll(ctx, tag, n) {
    var p = sp_p(ctx);
    var rnd = sp_level(p).getRandom();
    var seen = {};
    var exclHit = {};
    var bad = {};
    for (var i = 0; i < n; i++) {
        var list = SP_Catalog.rollShelf(rnd, 5, true, true);
        for (var k = 0; k < list.size(); k++) {
            var id = sp_itemIdOf(list.get(k));
            seen[id] = 1;
            if (sp_inList(SP_EXCLUSIVE, id) || sp_inList(SP_EXTRA_EXCLUDED, id)) exclHit[id] = 1;
            var st = new SP_ItemStack(list.get(k));
            if (!sp_describeSellable(st)) bad[id] = 1;
        }
    }
    sp_send(ctx, 'AP_' + tag + '_ROLL:rolls=' + n + ':distinct=' + Object.keys(seen).length
        + ':excludedSeen=' + (Object.keys(exclHit).length === 0 ? 'none' : Object.keys(exclHit).join('|'))
        + ':notSellable=' + (Object.keys(bad).length === 0 ? 'none' : Object.keys(bad).join('|'))
        + ':ids=' + Object.keys(seen).sort().join('|'));
    return 1;
}

// ── 五级交易表 ────────────────────────────────────────────────────────────
var SP_SAMPLES = 60;

var sp_listingErr = 'none';

function sp_listings(level) {
    sp_listingErr = 'none';
    var prof = null;
    try { prof = SP_ModProfessions.CARD_TRADER.get(); } catch (e0) { sp_listingErr = 'prof:' + sp_exText(e0); }
    var map = SP_VillagerTrades.TRADES;
    var inner = null;
    try { inner = map.get(prof); } catch (e1) { sp_listingErr = 'get:' + sp_exText(e1); }
    if (inner == null) { try { inner = map['card_trader']; } catch (e2) { } }
    if (inner == null) {
        try {
            var ks = Object.keys(map);
            for (var i = 0; i < ks.length; i++) {
                if (ks[i] === 'card_trader' || ks[i].indexOf('card_trader') >= 0) { inner = map[ks[i]]; break; }
            }
            if (inner == null && sp_listingErr === 'none') sp_listingErr = 'keys=' + ks.join('|');
        } catch (e3) { }
    }
    if (inner == null) {
        try {
            var it = map.entrySet().iterator();
            while (it.hasNext()) {
                var en = it.next();
                var ek = '' + en.getKey();
                if (ek === 'card_trader' || ek === '' + prof) { inner = en.getValue(); break; }
            }
        } catch (e4) { }
    }
    if (inner == null) return null;
    var arr = null;
    try { arr = inner[level]; } catch (e5) { arr = null; }
    if (arr == null) { try { arr = inner.get(level); } catch (e6) { arr = null; } }
    if (arr == null) { try { arr = inner.get('' + level); } catch (e7) { arr = null; } }
    return arr;
}

/** 诊断:静态交易表 TRADES 的结构(Object.keys 在 Java Map 上不可靠,这里逐层 entrySet 走一遍) */
function sp_doListDbg(ctx, tag) {
    var klist = [];
    try {
        var map = SP_VillagerTrades.TRADES;
        klist.push('map=' + sp_cls(map) + '/' + sp_len(map));
        var it = map.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            klist.push('k=' + sp_cls(en.getKey()) + ':' + en.getKey() + '=>' + sp_cls(en.getValue()) + '/' + sp_len(en.getValue()));
        }
    } catch (e) { klist.push('EX:' + sp_exText(e)); }
    sp_send(ctx, 'AP_' + tag + '_LDBG:' + klist.join('|'));
    try {
        var it2 = SP_VillagerTrades.TRADES.entrySet().iterator();
        while (it2.hasNext()) {
            var en2 = it2.next();
            var subs = [];
            try {
                var it3 = en2.getValue().entrySet().iterator();
                while (it3.hasNext()) {
                    var e3 = it3.next();
                    var vv = e3.getValue();
                    subs.push('lv=' + sp_cls(e3.getKey()) + ':' + e3.getKey() + '=>' + sp_cls(vv) + '/' + sp_len(vv));
                }
            } catch (e5) { subs.push('EX:' + sp_exText(e5)); }
            sp_send(ctx, 'AP_' + tag + '_LDBGENT:key=' + en2.getKey() + ':' + subs.join(','));
        }
    } catch (e6) { sp_send(ctx, 'AP_' + tag + '_LDBGENT:EX:' + sp_exText(e6)); }
    return 1;
}

function sp_doVPlan(ctx, tag) {
    var p = sp_p(ctx);
    var rnd = sp_level(p).getRandom();
    var entryTier = {};
    var entries = sp_poolEntries();
    for (var i = 0; i < entries.length; i++) entryTier[entries[i].id] = entries[i].tier;
    var ALL = SP_EXCLUSIVE.concat(SP_EXTRA_EXCLUDED);
    var exclSeen = {};
    var notSellable = {};
    for (var lv = 1; lv <= 5; lv++) {
        var arr = sp_listings(lv);
        if (arr == null) { sp_send(ctx, 'AP_' + tag + '_LVL:n=' + lv + ':listings=null:reason=' + sp_listingErr); continue; }
        var exch = 0;
        var minp = 999; var maxp = -1;
        var items = {};
        var tiers = {};
        var nulls = 0;
        var exchIdx = [];
        var arrLen = sp_len(arr);
        var arrCls = sp_cls(arr);
        for (var li = 0; li < arrLen; li++) {
            var lst = sp_at(arr, li);
            var localExch = 0;
            for (var k = 0; k < SP_SAMPLES; k++) {
                var off = null;
                try { off = lst.getOffer(p, rnd); } catch (e) { off = null; }
                if (off == null) { nulls++; continue; }
                var ca = off.getBaseCostA();
                var caId = sp_itemIdOf(ca.getItem());
                var caN = ca.getCount();
                var rid = sp_id(off.getResult());
                items[rid] = 1;
                var t = entryTier[rid];
                if (t == null) t = 'NOT_IN_POOL';
                tiers[t] = 1;
                if (SP_DiceTrade.isEmeraldToStarCoin(off)) { localExch = 1; exch++; }
                else {
                    if (caId === 'minecraft:emerald') { if (caN < minp) minp = caN; if (caN > maxp) maxp = caN; }
                }
                if (sp_inList(ALL, rid)) exclSeen[rid] = 1;
                var st = sp_stack(rid);
                if (st != null && !sp_describeSellable(st)) notSellable[rid] = 1;
            }
            if (localExch) exchIdx.push(li);
        }
        sp_send(ctx, 'AP_' + tag + '_LVL:n=' + lv + ':listings=' + arrLen + ':arrCls=' + arrCls + ':samples=' + SP_SAMPLES
            + ':exch=' + (exch > 0 ? 1 : 0) + ':exchListings=' + (exchIdx.length === 0 ? 'none' : exchIdx.join('|'))
            + ':minp=' + (maxp < 0 ? -1 : minp) + ':maxp=' + maxp + ':nulls=' + nulls
            + ':tiers=' + Object.keys(tiers).sort().join('|') + ':items=' + Object.keys(items).sort().join('|'));
    }
    sp_send(ctx, 'AP_' + tag + '_VPLAN:excludedSeen=' + (Object.keys(exclSeen).length === 0 ? 'none' : Object.keys(exclSeen).join('|'))
        + ':notSellable=' + (Object.keys(notSellable).length === 0 ? 'none' : Object.keys(notSellable).join('|'))
        + ':poolSize=' + entries.length + ':src=' + sp_poolSrc + ':perr=' + sp_poolErr);
    return 1;
}

function sp_spawnVillager(p, level) {
    var lvl = sp_level(p);
    var type = SP_Reg.ENTITY_TYPE.get(new SP_RL('minecraft:villager'));
    var v = type.create(lvl);
    if (v == null) return null;
    // 同 sp_doBlock:1.21.1 Rhino 拿不到 Entity#getYRot ⇒ 固定玩家 -X 侧 3 格(与工作方块 +X 2 格不重叠)
    var bp = p.blockPosition().offset(-3, 0, 0);
    v.setPos(bp.getX() + 0.5, p.getY(), bp.getZ() + 0.5);
    // 不再关 AI：认领工作方块(AssignProfessionFromJobSite)挂在 brain 上，NoAI 村民永不认领(2026-10-09 实机教训)
    try { v.setPersistenceRequired(); } catch (e2) { }
    try { v.setCustomName(SP_Component.literal(SP_VNAME)); } catch (e3) { }
    try {
        var vt = SP_Reg.VILLAGER_TYPE.get(new SP_RL('minecraft:plains'));
        var prof = SP_ModProfessions.CARD_TRADER.get();
        // 先置 NONE(触发 offers=null),再置目标职业 ⇒ getOffers() 会按新等级重建
        v.setVillagerData(new SP_VillagerData(vt, SP_VillagerProfession.NONE, level));
        v.setVillagerData(new SP_VillagerData(vt, prof, level));
    } catch (e4) { }
    lvl.addFreshEntity(v);
    // 测试台 astral_test_noai.js 的 EntityEvents.spawned 会在 add 时把 NoAi 顶回 true ⇒ add 之后再清一次
    try { v.setNoAi(false); } catch (e5) { }
    return v;
}

function sp_offerLine(off) {
    var ca = off.getBaseCostA();
    var s = sp_itemIdOf(ca.getItem()) + 'x' + ca.getCount();
    try { var b = off.getCostB(); if (b != null && !b.isEmpty()) { s = s + '+' + sp_itemIdOf(b.getItem()) + 'x' + b.getCount(); } } catch (e) { }
    return s + '->' + sp_id(off.getResult()) + 'x' + off.getResult().getCount() + ':xp=' + off.getXp() + ':maxUses=' + off.getMaxUses();
}

function sp_doVillager(ctx, tag, level) {
    var p = sp_p(ctx);
    var v = sp_spawnVillager(p, level);
    if (v == null) { sp_send(ctx, 'AP_' + tag + '_VIL:spawn_failed=1'); return 0; }
    var offers = null;
    try { offers = v.getOffers(); } catch (e) { sp_send(ctx, 'AP_' + tag + '_VIL:offers_ex=' + sp_exText(e)); }
    var prof = 'n/a';
    try { prof = SP_Reg.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString(); } catch (e2) { }
    sp_send(ctx, 'AP_' + tag + '_VIL:level=' + level + ':prof=' + prof + ':profLevel=' + v.getVillagerData().getLevel()
        + ':size=' + (offers == null ? -1 : offers.size()) + ':exch=' + sp_countExch(offers) + ':pos=' + sp_n(v.getX()) + ',' + sp_n(v.getY()) + ',' + sp_n(v.getZ()));
    if (offers != null) {
        for (var i = 0; i < offers.size(); i++) {
            sp_send(ctx, 'AP_' + tag + '_VILOFFER:i=' + i + ':' + sp_offerLine(offers.get(i)));
        }
    }
    try { v.discard(); } catch (e3) { }
    return 1;
}

function sp_countExch(offers) {
    if (offers == null) return -1;
    var n = 0;
    for (var i = 0; i < offers.size(); i++) { try { if (SP_DiceTrade.isEmeraldToStarCoin(offers.get(i))) n++; } catch (e) { } }
    return n;
}

// ── 真实交易界面:过滤后索引对齐 + 真实成交 ───────────────────────────────
function sp_doVTrade(ctx, tag, level) {
    var p = sp_p(ctx);
    var v = sp_spawnVillager(p, level);
    if (v == null) { sp_send(ctx, 'AP_' + tag + '_VT:spawn_failed=1'); return 0; }
    sp_lastVillager = v;
    var raw = null;
    try { raw = v.getOffers(); } catch (e) { }
    sp_send(ctx, 'AP_' + tag + '_VT:level=' + level + ':raw=' + (raw == null ? -1 : raw.size()) + ':rawExch=' + sp_countExch(raw)
        + ':diceOn=' + sp_diceOn(p));
    // 原版 Villager#mobInteract 先 setTradingPlayer 再开屏;EmeraldDiceTrade.filteredFor(merchant) 依赖 merchant.getTradingPlayer()
    // ⇒ 探针必须复刻这一步,否则 filteredFor 恒返回 null(既不过滤也不换绿宝石→星币条目)
    try { v.setTradingPlayer(p); } catch (eSP) { sp_send(ctx, 'AP_' + tag + '_VT:setplayer_ex=' + sp_exText(eSP)); }
    try { v.openTradingScreen(p, SP_Component.literal('astral_shop_probe'), level); } catch (e2) { sp_send(ctx, 'AP_' + tag + '_VT:open_ex=' + sp_exText(e2)); }
    var menu = null;
    try { menu = p.containerMenu; } catch (e3) { }
    var mcls = sp_cls(menu);
    var filtered = null;
    try { filtered = menu.getOffers(); } catch (e4) { }
    var fn = filtered == null ? -1 : filtered.size();
    sp_send(ctx, 'AP_' + tag + '_VTMENU:cls=' + mcls + ':filtered=' + fn + ':filteredExch=' + sp_countExch(filtered)
        + ':rawMinusFiltered=' + ((raw == null || filtered == null) ? -1 : (raw.size() - fn)));
    if (filtered != null) {
        for (var i = 0; i < fn; i++) { sp_send(ctx, 'AP_' + tag + '_VTFILTER:i=' + i + ':' + sp_offerLine(filtered.get(i))); }
    }
    // 子序列对齐:filtered 必须是 raw 的顺序子序列(只删不重排)
    var subseq = -1;
    if (raw != null && filtered != null) {
        subseq = 1; var j = 0;
        for (var k = 0; k < fn; k++) {
            var target = sp_offerLine(filtered.get(k));
            var found = 0;
            while (j < raw.size()) {
                if (sp_offerLine(raw.get(j)) === target) { found = 1; j++; break; }
                j++;
            }
            if (!found) { subseq = 0; break; }
        }
    }
    sp_send(ctx, 'AP_' + tag + '_VTSEQ:subsequence=' + subseq);
    // 索引对齐:给足付货款,逐条 setSelectionHint(i) + tryMoveItems(i) → 付款槽/结果槽
    sp_addPayment(p);
    for (var t = 0; t < fn; t++) {
        try { menu.getSlot(0).set(SP_ItemStack.EMPTY); menu.getSlot(1).set(SP_ItemStack.EMPTY); } catch (e5) { }
        var want = null;
        try { want = filtered.get(t); } catch (e6) { }
        if (want == null) continue;
        var wantA = sp_itemIdOf(want.getBaseCostA().getItem());
        var wantR = sp_id(want.getResult());
        var movedA = 'empty';
        try { menu.setSelectionHint(t); menu.tryMoveItems(t); movedA = sp_id(menu.getSlot(0).getItem()); } catch (e7) { movedA = 'EX:' + sp_exText(e7); }
        var resItem = 'empty';
        try { resItem = sp_id(menu.getSlot(2).getItem()); } catch (e8) { }
        var movedN = -1;
        try { movedN = menu.getSlot(0).getItem().getCount(); } catch (e8b) { }
        // 骰子换币:绿宝石费用在 CONTEXT 生效期间被换成星币并打 8 折 ⇒ 用 begin/end 读**有效费用**
        var effA = 'n/a'; var effN = -1;
        try {
            SP_DiceTrade.begin(p);
            effA = sp_itemIdOf(want.getBaseCostA().getItem());
            effN = want.getBaseCostA().getCount();
            SP_DiceTrade.end();
        } catch (e8c) { try { SP_DiceTrade.end(); } catch (e8d) { } }
        sp_send(ctx, 'AP_' + tag + '_VTIDX:i=' + t + ':wantA=' + wantA + ':movedA=' + movedA + ':aMatch=' + ((movedA === wantA) ? 1 : 0)
            + ':movedN=' + movedN + ':effA=' + effA + ':effN=' + effN
            + ':wantResult=' + wantR + ':slotResult=' + resItem + ':rMatch=' + ((resItem === wantR) ? 1 : 0));
    }
    // 真实成交:最后一条报价(过滤后)**原样**拿结果槽 → onTake(与玩家点该条同入口)
    var last = fn - 1;
    var resultItem = 'empty';
    var wantLast = 'n/a';
    var wantId = 'n/a';
    try {
        menu.setSelectionHint(last);
        menu.getSlot(0).set(SP_ItemStack.EMPTY);
        menu.getSlot(1).set(SP_ItemStack.EMPTY);
        menu.tryMoveItems(last);
        var offLast = menu.getOffers().get(last);
        wantLast = sp_id(offLast.getResult()) + 'x' + offLast.getResult().getCount();
        wantId = sp_id(offLast.getResult());
        var invBefore = sp_countItem(p, sp_id(offLast.getResult()));
        var usesBefore = offLast.getUses();
        var slotStack = menu.getSlot(2).getItem();
        resultItem = sp_id(slotStack);
        var takeStack = slotStack;
        var takenId = sp_id(takeStack);
        var takeHow = 'realStack';
        try { menu.getSlot(2).onTake(p, takeStack); } catch (eT1) {
            takeHow = 'copyFallback';
            try { takeStack = slotStack.copy(); menu.getSlot(2).onTake(p, takeStack); } catch (eT2) { takeHow = 'EX:' + sp_exText(eT2); }
        }
        var slotAfter = 'n/a';
        try { slotAfter = sp_id(menu.getSlot(2).getItem()); } catch (eSA) { }
        var carried = 'n/a';
        try { carried = sp_id(p.containerMenu.getCarried()); } catch (eCR) { }
        var freeSlot = -1;
        try { freeSlot = p.getInventory().getFreeSlot(); } catch (eFR) { }
        sp_send(ctx, 'AP_' + tag + '_VTTAKE:i=' + last + ':want=' + wantLast + ':wantId=' + wantId
            + ':slotBefore=' + resultItem + ':sameSlot=' + ((wantId === resultItem) ? 1 : 0)
            + ':got=' + takenId + ':gotId=' + takenId + ':same=' + ((wantId === takenId) ? 1 : 0)
            + ':takeHow=' + takeHow + ':slotAfter=' + slotAfter + ':carried=' + carried + ':free=' + freeSlot
            + ':inv=' + invBefore + '->' + sp_countItem(p, wantId)
            + ':uses=' + usesBefore + '->' + offLast.getUses()
            + ':xp=' + v.getVillagerXp() + ':pay0=' + sp_id(menu.getSlot(0).getItem()) + ':payN0=' + sp_slotCount(menu, 0) + ':pay1=' + sp_id(menu.getSlot(1).getItem()));
    } catch (e9) { sp_send(ctx, 'AP_' + tag + '_VTTAKE:ex=' + sp_exText(e9)); }
    // 成交后(戴骰子)兑换条目仍应缺席
    var after = null;
    try { after = menu.getOffers(); } catch (e10) { }
    sp_send(ctx, 'AP_' + tag + '_VTAFTER:size=' + (after == null ? -1 : after.size()) + ':exch=' + sp_countExch(after) + ':diceOn=' + sp_diceOn(p));
    // 交易界面同样会吞掉后续聊天命令 ⇒ 用例内自关(村民仍留着,由 vclose 弃置)
    var closedV = 'n/a';
    try { sp_closeMenu(p); closedV = '1'; } catch (eV) { closedV = 'EX:' + sp_exText(eV); }
    sp_send(ctx, 'AP_' + tag + '_VTCLOSED:res=' + closedV);
    try { v.setTradingPlayer(null); } catch (eSP2) { }
    return 1;
}

function sp_addPayment(p) {
    try { p.getInventory().add(new SP_ItemStack(SP_Items.EMERALD, 64)); } catch (e) { }
    try { p.getInventory().add(new SP_ItemStack(SP_Items.EMERALD, 64)); } catch (e2) { }
    try {
        var coin = sp_item('astral_dice:star_coin');
        if (coin != null) { p.getInventory().add(new SP_ItemStack(coin, 64)); p.getInventory().add(new SP_ItemStack(coin, 64)); }
    } catch (e3) { }
}

function sp_slotCount(menu, i) {
    try { return menu.getSlot(i).getItem().getCount(); } catch (e) { return -1; }
}

function sp_countItem(p, idStr) {
    var n = 0;
    try {
        for (var i = 0; i < 36; i++) {
            var st = p.getInventory().getItem(i);
            if (st != null && !st.isEmpty() && sp_id(st) === idStr) n += st.getCount();
        }
    } catch (e) { }
    return n;
}

function sp_doVClose(ctx, tag) {
    var p = sp_p(ctx);
    var how = 'closeContainer';
    try { sp_closeMenu(p); } catch (e) { how = 'fail:' + sp_exText(e); }
    if (sp_lastVillager != null) { try { sp_lastVillager.discard(); } catch (e2) { } sp_lastVillager = null; }
    sp_send(ctx, 'AP_' + tag + '_VCLOSE:how=' + how + ':menu=' + sp_cls(p.containerMenu));
    return 1;
}

// ── 兼容性体检(Rhino 成员可见性)────────────────────────────────────────────
function sp_doDiag(ctx, tag) {
    // 2026-10-09 1.20.1 实测:本命令曾**整条无输出**(客户端只显示 brigadier「意外错误」,
    // 服务端 latest/debug 均无堆栈)⇒ 现在:①先发 BEGIN 标记;②入口整体 try 上报;
    // ③每条读数单独发一条消息(避免超长行被丢),再发聚合行保证断言 pattern 命中。
    sp_send(ctx, 'AP_' + tag + '_DIAGBEGIN');
    // 2026-10-09 1.20.1 实测:DIAGBEGIN 之后**再无任何输出**,且 _EX/_OUTER/_DIAGERR 全无
    // ⇒ 抛点不在 JS 可捕获层(或发生在集合阶段之前) ⇒ 逐项 flush 到 console(与聊天通道独立)
    // 定位抛点;并把聚合 _DIAG 行提前到 t() 之后发,保证断言不依赖后半段。
    var sp_dbg = function (txt) { try { console.info('AP_' + tag + '_C:' + txt); } catch (eC) { } };
    sp_dbg('begin');
    var p = null;
    try { p = sp_p(ctx); } catch (eP) { sp_send(ctx, 'AP_' + tag + '_DIAGERR:nop:' + sp_exText(eP)); return 0; }
    var out = [];
    var items = [];
    function t(name, fn) {
        var v;
        try { v = ('' + fn()).replace(/[\r\n]+/g, ' ').substring(0, 110); }
        catch (e) { v = 'EX(' + sp_exText(e) + ')'; }
        out.push(name + '=' + v); items.push(name + '=' + v);
        sp_dbg(name + '=' + v);
    }
    t('cls', function () { return sp_cls(p); });
    t('clsStrict', function () { return p.getClass().getSimpleName(); });
    t('clsName', function () { return p.getClass().getName(); });
    t('yRot', function () { return p.getYRot(); });
    t('dir', function () { return p.getDirection(); });
    t('bp', function () { return p.blockPosition(); });
    t('menuRaw', function () { return '' + p.containerMenu; });
    t('menuCls', function () { return sp_cls(p.containerMenu); });
    t('menuInv', function () { return '' + p.inventoryMenu; });
    t('level', function () { return sp_cls(sp_level(p)); });
    t('block', function () { return '' + SP_ModBlocks.CARD_SHOP.get(); });
    t('poiKey', function () { return '' + SP_ModPoiTypes.CARD_SHOP_KEY; });
    t('blockPos', function () { return p.blockPosition().offset(2, 0, 0); });
    t('hit', function () { return new SP_BlockHitResult(new SP_Vec3(p.getX() + 2.5, p.getY() + 0.9, p.getZ() + 0.5), SP_Direction.UP, p.blockPosition().offset(2, 0, 0), false); });
    sp_dbg('t-done n=' + out.length);
    sp_send(ctx, 'AP_' + tag + '_DIAG:' + out.join('|'));
    sp_dbg('diagline-sent');
    // ── 探针自检:Rhino 成员可见性 / 重载消歧 / 集合访问(2026-10-09 实机教训)──────
    var d = [];
    function d1(name, fn) {
        var v;
        try { v = ('' + fn()).replace(/[\r\n]+/g, ' ').substring(0, 160); }
        catch (e) { v = 'EX(' + sp_exText(e) + ')'; }
        d.push(name + '=' + v); items.push(name + '=' + v);
        sp_dbg('d:' + name + '=' + v);
    }
    var inv = null; try { inv = p.getInventory(); } catch (eI) { }
    var s0 = null; try { s0 = inv.getItem(0); } catch (e0) { }
    var dirt = null; try { dirt = sp_stack('minecraft:dirt'); } catch (eD) { }
    var card = null; try { card = sp_stack('astral_dice:attack_card_medium'); } catch (eC) { }
    d1('slot0Cls', function () { return sp_cls(s0); });
    d1('slot0Id', function () { return sp_id(s0); });
    d1('slot0IsEmpty', function () { return '' + s0.isEmpty(); });
    d1('dirtCls', function () { return sp_cls(dirt); });
    d1('dirtId', function () { return sp_id(dirt); });
    d1('dirtGetItemCls', function () { return sp_cls(dirt.getItem()); });
    d1('dirtGetItemId', function () { return sp_id(dirt.getItem()); });
    d1('keyOfDirtItem', function () { return '' + SP_Reg.ITEM.getKey(sp_item('minecraft:dirt')); });
    d1('filterByItem', function () { return SP_Filter.isSellable(sp_item('astral_dice:attack_card_medium')) ? 1 : 0; });
    d1('filterByStack', function () { return SP_Filter.isSellable(card) ? 1 : 0; });
    d1('tradesCls', function () { return sp_cls(SP_VillagerTrades.TRADES); });
    d1('tradesKeys', function () { return Object.keys(SP_VillagerTrades.TRADES).join('|'); });
    d1('profCls', function () { return sp_cls(SP_ModProfessions.CARD_TRADER); });
    d1('profGetCls', function () { return sp_cls(SP_ModProfessions.CARD_TRADER.get()); });
    d1('tradesGetCls', function () { return sp_cls(SP_VillagerTrades.TRADES.get(SP_ModProfessions.CARD_TRADER.get())); });
    // 2026-10-09 1.20.1 实测:Java.from(Set) 会抛出 JS try/catch 接不住的 Throwable ⇒ 整条 brigadier
    // 命令以原版 commands.generic.exception 结束(聊天报「试图执行该命令时出现意外错误」、日志无堆栈)。
    // 这里改用纯 JS 键枚举(Object.keys 在 1.20.1 可用,历史上 tradesKeys 已证)。
    d1('tradesJsKeysN', function () { return 'jsKeys:' + Object.keys(SP_VillagerTrades.TRADES).length; });
    var pl = null; try { pl = p.getServer().getPlayerList(); } catch (eP) { }
    d1('plCls', function () { return sp_cls(pl); });
    d1('plGetPlayersCls', function () { return sp_cls(pl.getPlayers()); });
    d1('plGetPlayersN', function () { return pl.getPlayers().size(); });
    d1('plIteratorN', function () { var l = pl.getPlayers(); var it = l.iterator(); var n = 0; while (it.hasNext()) { it.next(); n++; } return n; });
    // 同上:Java.from(Collection) 风险高,plIteratorN 已覆盖计数语义。
    d1('plSizeN', function () { return 'size:' + pl.getPlayers().size(); });
    d1('plPropN', function () { return p.server.playerList.players.size(); });
    d1('plNames', function () { var l = pl.getPlayers(); var it = l.iterator(); var nm = []; while (it.hasNext()) { var o = it.next(); nm.push('' + o.getName().getString()); } return nm.join('|'); });
    // 逐条发(每条 ≤ ~180 字符,避免超长被丢),再发聚合行
    for (var ii = 0; ii < items.length; ii++) { sp_send(ctx, 'AP_' + tag + '_I:' + items[ii]); }
    sp_send(ctx, 'AP_' + tag + '_DBG1:' + d.slice(0, 8).join('|'));
    sp_send(ctx, 'AP_' + tag + '_DBG2:' + d.slice(8, 16).join('|'));
    sp_send(ctx, 'AP_' + tag + '_DBG3:' + d.slice(16).join('|'));
    sp_send(ctx, 'AP_' + tag + '_DIAG2:' + out.join('|') + '|' + d.join('|'));
    sp_send(ctx, 'AP_' + tag + '_DIAGEND:n=' + (out.length + d.length));
    sp_dbg('end n=' + (out.length + d.length));
    return 1;
}

/** 配方层取证: 目标物品的配方条目数 + 全部卡牌物品的配方条目数(应为 0)。 */
function sp_doRecipes(ctx, tag, idStr) {
    idStr = sp_unq(idStr);
    var p = sp_p(ctx);
    var lvl = sp_level(p);
    var acc = null;
    try { acc = lvl.registryAccess(); } catch (e0) { acc = null; }
    var holders = null;
    try { holders = lvl.getRecipeManager().getRecipes(); } catch (e1) { sp_send(ctx, 'AP_' + tag + '_RECIPES:ex=' + sp_exText(e1)); return 0; }
    var cards = {};
    var entries = sp_poolEntries();
    for (var i = 0; i < entries.length; i++) { cards[entries[i].id] = 1; }
    var ALL = SP_EXCLUSIVE.concat(SP_EXTRA_EXCLUDED);
    for (var j = 0; j < ALL.length; j++) { cards[ALL[j]] = 1; }
    var hit = [];
    var cardHits = [];
    var total = 0;
    var it = holders.iterator();
    while (it.hasNext()) {
        var h = it.next();
        total++;
        var st = null;
        try { st = h.value().getResultItem(acc); } catch (e2) { st = null; }
        if (st == null || st.isEmpty()) continue;
        var rid = sp_id(st);
        if (rid === idStr) hit.push('' + h.id());
        if (cards[rid] === 1) cardHits.push(rid + '@' + h.id());
    }
    sp_send(ctx, 'AP_' + tag + '_RECIPES:total=' + total + ':want=' + idStr + ':hits=' + hit.length
        + ':list=' + (hit.length === 0 ? 'none' : hit.join('|'))
        + ':cardResultRecipes=' + cardHits.length
        + ':cards=' + (cardHits.length === 0 ? 'none' : cardHits.join('|')));
    return 1;
}

// ══ 筹码商店（Chip Shop）—— 2026-10-13 移植自 1.21.1 ═══════════════════════════════
// 本批落地的第 2 座商店。全部驱动真代码路径：BlockState#use → ChipShopOpener.open
// → ChipShopMenu.handleBuy / handleRefresh → ChipShopState 附件原文。
// 机器行前缀 AP_<tag>_C*；购买与刷新另有产品侧自带行 CHIP_SHOP_BUY / CHIP_SHOP_REFRESH。
// ⚠️ 三种拒绝（余额不足 / 次数为 0 / 物品栏满）**只有 actionbar + S2C 包、没有服务端日志**
// ⚠️ ChipShopState 自 `.114` 起为 **7 分量** record（+cooldownUntil / +lastBlankInsertAt）
//    ⇒ 探针**不得**直调构造器（Rhino 会 InternalError: constructor not found），一律 `EMPTY` / `with*` 派生。
//    ⇒ 拒绝的判据是「状态未变 + 无 CHIP_SHOP_BUY 行」，不是某条 deny 日志行。

function cp_menuOf(p) {
    var m = null;
    try { m = p.containerMenu; } catch (e) { return null; }
    if (m == null) return null;
    if (sp_cls(m) !== 'ChipShopMenu') return null;
    return m;
}

function cp_state(p) {
    try { return SP_ChipState.get(p); } catch (e) { return null; }
}

/** offers 列表 → 'a+b+c'（空列表 → '-'） */
function cp_offersCsv(s) {
    if (s == null) return '?';
    var raw = null;
    try { raw = s.offers(); } catch (e) { return '?'; }
    var n = sp_len(raw);
    if (n <= 0) return '-';
    var out = [];
    for (var i = 0; i < n; i++) { var v = sp_at(raw, i); out.push(v == null ? '_' : '' + v); }
    return out.join('+');
}

function cp_lastCat(s) {
    try {
        var o = s.lastCategory();
        return (o != null && o.isPresent()) ? '' + o.get() : '-';
    } catch (e) { return '?'; }
}

/** 附件原文一条行（offers / last / purchased / refreshes / anchor / price / key） */
function cp_stateLine(ctx, tag, s) {
    if (s == null) { sp_send(ctx, 'AP_' + tag + '_CST:ex=state-null'); return; }
    sp_send(ctx, 'AP_' + tag + '_CST:offers=' + cp_offersCsv(s) + ':last=' + cp_lastCat(s)
        + ':purchased=' + s.purchasedCount() + ':refreshes=' + s.refreshesLeft() + '/' + SP_ChipCatalog.REFRESH_MAX
        + ':anchor=' + s.regenAnchor() + ':price=' + s.price() + ':key=' + s.offersKey());
}

/** 菜单读数一条行（价格 / 次数 / 拒绝码 / 三格 id+tier / 候选键） */
function cp_menuLine(ctx, tag, menu) {
    if (menu == null) { sp_send(ctx, 'AP_' + tag + '_CMENU:null=1'); return; }
    var cells = [];
    for (var i = 0; i < 3; i++) {
        var id = '?'; var tier = -99;
        try { id = sp_id(menu.offerStack(i)); } catch (e) { id = '<ex>'; }
        try { tier = menu.offerTier(i); } catch (e2) { tier = -98; }
        cells.push(i + ':' + id + ':t' + tier);
    }
    var price = -1; var rl = -1; var rt = -1; var key = '?'; var deny = -99;
    try { price = menu.price(); } catch (e3) { price = -2; }
    try { rl = menu.remainingRefreshes(); } catch (e4) { rl = -2; }
    try { rt = menu.totalRefreshes(); } catch (e5) { rt = -2; }
    try { key = menu.offersKey(); } catch (e6) { key = '<ex>'; }
    try { deny = menu.denyReason(); } catch (e7) { deny = -3; }
    sp_send(ctx, 'AP_' + tag + '_CMENU:price=' + price + ':refreshes=' + rl + '/' + rt
        + ':deny=' + deny + ':cells=' + cells.join(',') + ':key=' + key);
}

/** 物品栏 36 格 id 快照（0..8 快捷栏 / 9..35 主物品栏，不含副手） */
function cp_invIds(p) {
    var out = [];
    try {
        var inv = p.inventory;
        for (var i = 0; i < 36; i++) { out.push(sp_id(inv.getItem(i))); }
    } catch (e) { out.push('<?>'); }
    return out;
}

function cp_invCount(p) {
    var ids = cp_invIds(p); var n = 0;
    for (var i = 0; i < ids.length; i++) { if (!sp_isEmptyId(ids[i])) { n++; } }
    return n;
}

function cp_invDelta(before, after) {
    var out = [];
    for (var i = 0; i < before.length && i < after.length; i++) {
        if (before[i] !== after[i]) { out.push(i + ':' + before[i] + '>' + after[i]); }
    }
    return out.length ? out.join(',') : '-';
}

/**
 * 保留候选与次数、只改 purchasedCount 的新态（价格曲线用）。
 *
 * ⚠️ ChipShopState 自 `.114` 起为 **7 分量** record（offers / lastPickCategory / purchasedCount /
 * refreshesLeft / regenAnchor / cooldownUntil / lastBlankInsertAt）⇒ **5 参构造器已不存在**，
 * Rhino 直调旧写法会抛 `InternalError: constructor not found`（原 5 参直调写法即此坑）⇒ 只用产品侧公开的
 * `with*` 派生：`EMPTY` ⇒ `withOffers(s.entries())`（复刻候选 ⇒ `offersKeep=1`；未知 id 按产品
 * `entries()` 口径丢弃 —— 用例里的候选均来自真实掷骰，无未知 id）
 * ⇒ `withRefreshes(s.refreshesLeft(), s.regenAnchor())`（复刻次数 / 锚点）
 * ⇒ 逐次 `withPurchased(null)` 累加到 n。
 * ⚠️ `withPurchased` 只能 +1 ⇒ **n 小于当前次数**（用例里的 `cprice 30` ⇒ `cprice 0` 回落）靠
 * 「从 EMPTY 重建」实现。未复刻 lastPickCategory / cooldownUntil / lastBlankInsertAt（置空 / 0；
 * 与 `.114` 前的 5 分量口径一致）；本用例断言面（formula / state / menu / offersKeep）不含这三项。
 */
function cp_stateWith(s, purchased) {
    var cur = SP_ChipState.EMPTY.withOffers(s.entries()).withRefreshes(s.refreshesLeft(), s.regenAnchor());
    for (var i = 0; i < purchased; i++) { cur = cur.withPurchased(null); }
    return cur;
}

function sp_doChipBlock(ctx, tag) {
    var p = sp_p(ctx);
    var lvl = sp_level(p);
    var pos = p.blockPosition().offset(2, 0, 0);
    var block = SP_ModBlocks.CHIP_SHOP.get();
    var ok = lvl.setBlockAndUpdate(pos, block.defaultBlockState());
    var facing = '?';
    try {
        var prop = block.getStateDefinition().getProperty('facing');
        facing = (prop == null) ? 'null' : ('' + lvl.getBlockState(pos).getValue(prop));
    } catch (e0) { facing = 'ex:' + sp_exText(e0); }
    var drop = sp_id(new SP_ItemStack(lvl.getBlockState(pos).getBlock().asItem()));
    sp_send(ctx, 'AP_' + tag + '_CBLOCK:set=' + (ok ? 1 : 0) + ':pos=' + pos.getX() + ',' + pos.getY() + ',' + pos.getZ()
        + ':facing=' + facing + ':item=' + drop);
    // 真实右键（与 card_shop 探针同法：1.20.1 无 useWithoutItem ⇒ BlockState#use(Level, Player, InteractionHand, BlockHitResult)）
    var res = 'n/a';
    try {
        var bs = lvl.getBlockState(pos);
        var hit = new SP_BlockHitResult(new SP_Vec3(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5), SP_Direction.UP, pos, false);
        var r = bs.use(lvl, p, SP_Hand.MAIN_HAND, hit);
        res = '' + r;
    } catch (e1) { res = 'EX:' + sp_exText(e1); }
    sp_send(ctx, 'AP_' + tag + '_CUSE:res=' + res + ':menu=' + sp_cls(p.containerMenu));
    cp_menuLine(ctx, tag, cp_menuOf(p));
    cp_stateLine(ctx, tag, cp_state(p));
    // 界面开着时后续注入的聊天命令会被 GUI 吞掉（SHOP-GUI 首跑教训）⇒ 立刻关掉
    try { sp_closeMenu(p); } catch (e2) { }
    sp_send(ctx, 'AP_' + tag + '_CCLOSE:by=block');
    return 1;
}

function sp_doChipPool(ctx, tag) {
    var p = sp_p(ctx);
    var pool = SP_ChipCatalog.POOL;
    var n = sp_len(pool);
    var t1 = 0; var t2 = 0; var t3 = 0;
    var head = [];
    for (var i = 0; i < n; i++) {
        var e = sp_at(pool, i);
        var t = e.tier();
        if (t === 1) { t1++; } else if (t === 2) { t2++; } else if (t === 3) { t3++; }
        if (i < 3) { head.push(e.id() + '/t' + t); }
    }
    var avail = -1; var owned = -1; var all = -1; var star = -1;
    try { avail = sp_len(SP_ChipRoller.availablePool(p)); } catch (e2) { avail = -2; }
    try { owned = sp_len(SP_ChipRoller.ownedChipItems(p)); } catch (e3) { owned = -2; }
    try { all = SP_ChipRoller.ownsEverything(p) ? 1 : 0; } catch (e4) { all = -1; }
    try { star = SP_ChipRoller.starLevel(p); } catch (e5) { star = -2; }
    sp_send(ctx, 'AP_' + tag + '_CPOOL:pool=' + n + ':tiers=' + t1 + '/' + t2 + '/' + t3
        + ':avail=' + avail + ':owned=' + owned + ':all=' + all + ':star=' + star + ':head=' + head.join('+'));
    return 1;
}

function sp_doChipState(ctx, tag) {
    var p = sp_p(ctx);
    var s = cp_state(p);
    cp_stateLine(ctx, tag, s);
    var need = -1;
    try { need = s.needsReroll(p) ? 1 : 0; } catch (e) { need = -2; }
    sp_send(ctx, 'AP_' + tag + '_CST2:need=' + need + ':init=' + (s != null && s.initialized() ? 1 : 0)
        + ':menu=' + sp_cls(p.containerMenu) + ':bal=' + sp_bal(p) + ':inv=' + cp_invCount(p) + '/36');
    return 1;
}

function sp_doChipOpen(ctx, tag, keep) {
    var p = sp_p(ctx);
    SP_ChipOpener.open(p);
    sp_send(ctx, 'AP_' + tag + '_COPEN:menu=' + sp_cls(p.containerMenu) + ':bal=' + sp_bal(p));
    cp_menuLine(ctx, tag, cp_menuOf(p));
    cp_stateLine(ctx, tag, cp_state(p));
    if (!keep) {
        try { sp_closeMenu(p); } catch (e) { }
        sp_send(ctx, 'AP_' + tag + '_CCLOSE:by=open');
    }
    return 1;
}

function sp_doChipOpen0(ctx, tag) { return sp_doChipOpen(ctx, tag, false); }
function sp_doChipOpen1(ctx, tag) { return sp_doChipOpen(ctx, tag, true); }

function sp_doChipMenu(ctx, tag) {
    var p = sp_p(ctx);
    sp_send(ctx, 'AP_' + tag + '_CMENUCHK:cls=' + sp_cls(p.containerMenu));
    cp_menuLine(ctx, tag, cp_menuOf(p));
    cp_stateLine(ctx, tag, cp_state(p));
    return 1;
}

/** 真买一格：状态 / 余额 / 物品栏差集 全量前后对比（成功时产品侧另打 CHIP_SHOP_BUY） */
function sp_doChipBuy(ctx, tag, idx) {
    var p = sp_p(ctx);
    var m = cp_menuOf(p);
    if (m == null) { sp_send(ctx, 'AP_' + tag + '_CBUY:menu=null'); return 0; }
    var s0 = cp_state(p);
    var bal0 = sp_bal(p); var ids0 = cp_invIds(p);
    var ex = '-';
    try { m.handleBuy(p, idx); } catch (e) { ex = 'EX:' + sp_exText(e); }
    var bal1 = sp_bal(p); var ids1 = cp_invIds(p);
    sp_send(ctx, 'AP_' + tag + '_CBUY:idx=' + idx + ':ex=' + ex + ':bal=' + bal0 + '->' + bal1
        + ':inv=' + cp_invCount(p) + '/36:menu=' + sp_cls(p.containerMenu) + ':deny=' + m.denyReason());
    sp_send(ctx, 'AP_' + tag + '_CBUYINV:delta=' + cp_invDelta(ids0, ids1));
    sp_send(ctx, 'AP_' + tag + '_CBUYOFF:before=' + cp_offersCsv(s0) + ':after=' + cp_offersCsv(cp_state(p)));
    cp_stateLine(ctx, tag, cp_state(p));
    cp_menuLine(ctx, tag, cp_menuOf(p));
    return 1;
}

/** 真刷新：次数 / 锚点（含「锚点 − now」= 距下次恢复的 tick 数）/ 候选 前后对比 */
function sp_doChipRefresh(ctx, tag) {
    var p = sp_p(ctx);
    var m = cp_menuOf(p);
    if (m == null) { sp_send(ctx, 'AP_' + tag + '_CREFRESH:menu=null'); return 0; }
    var s0 = cp_state(p);
    var ex = '-';
    try { m.handleRefresh(p); } catch (e) { ex = 'EX:' + sp_exText(e); }
    var s1 = cp_state(p);
    sp_send(ctx, 'AP_' + tag + '_CREFRESH:ex=' + ex
        + ':refreshes=' + s0.refreshesLeft() + '->' + s1.refreshesLeft()
        + ':anchor=' + s0.regenAnchor() + '->' + s1.regenAnchor()
        + ':after=' + (s1.regenAnchor() - sp_tick(p)) + 't'
        + ':offers=' + cp_offersCsv(s0) + '->' + cp_offersCsv(s1)
        + ':deny=' + m.denyReason());
    cp_menuLine(ctx, tag, cp_menuOf(p));
    return 1;
}

/** 价格曲线：把 purchasedCount 设成 n（候选与次数原样保留），读公式值 / 态值 / 菜单值 */
function sp_doChipPrice(ctx, tag, n) {
    var p = sp_p(ctx);
    var s = cp_state(p);
    if (s == null) { sp_send(ctx, 'AP_' + tag + '_CPRICE:ex=state-null'); return 0; }
    var cur = cp_stateWith(s, n);
    SP_ChipState.store(p, cur);
    var formula = -1;
    try { formula = SP_ChipCatalog.priceOf(n); } catch (e) { formula = -2; }
    var menuPrice = -1;
    var m = cp_menuOf(p);
    if (m != null) { try { menuPrice = m.price(); } catch (e2) { menuPrice = -2; } }
    sp_send(ctx, 'AP_' + tag + '_CPRICE:n=' + n + ':formula=' + formula + ':state=' + cur.price()
        + ':menu=' + menuPrice + ':offersKeep=' + (cp_offersCsv(cur) === cp_offersCsv(s) ? 1 : 0));
    return 1;
}

/** 归一化：把筹码商店附件重置为**全新初始态**（EMPTY）—— 跨用例状态泄漏的清理口 */
function sp_doChipReset(ctx, tag) {
    var p = sp_p(ctx);
    var via = '?'; var st = null;
    // ⚠️ 全新态只能取类上的 EMPTY 常量：ChipShopState 自 `.114` 起为 7 分量 record，
    //    5 参构造器已不存在（原兜底直调构造器会抛 InternalError ⇒ 已删）。
    try { st = SP_ChipState.EMPTY; via = 'EMPTY'; } catch (e0) { st = null; }
    if (st == null) { sp_send(ctx, 'AP_' + tag + '_CRESET:ex=unavailable:via=none'); return 0; }
    var ex = '-';
    try { SP_ChipState.store(p, st); } catch (e2) { ex = 'EX:' + sp_exText(e2); }
    if (ex !== '-') { sp_send(ctx, 'AP_' + tag + '_CRESET:ex=' + ex + ':via=' + via); return 0; }
    var back = cp_state(p);
    sp_send(ctx, 'AP_' + tag + '_CRESET:ex=-:via=' + via
        + ':offers=' + cp_offersCsv(back) + ':last=' + cp_lastCat(back)
        + ':purchased=' + back.purchasedCount() + ':refreshes=' + back.refreshesLeft() + '/' + SP_ChipCatalog.REFRESH_MAX
        + ':anchor=' + back.regenAnchor() + ':price=' + back.price());
    return 1;
}

/** 刷新恢复前置：把附件摆成指定形态，等 ticker 自己跑（配合 wait + cstate 读数） */
function sp_doChipRegen(ctx, tag, mode) {
    var p = sp_p(ctx);
    var s = cp_state(p);
    if (s == null) { sp_send(ctx, 'AP_' + tag + '_CREGEN:ex=state-null'); return 0; }
    var now = sp_tick(p);
    var max = SP_ChipCatalog.REFRESH_MAX;
    var regen = SP_ChipCatalog.REFRESH_REGEN_TICKS;
    var next = null; var expect = '';
    if (mode === 'due') {
        next = s.withRefreshes(Math.min(s.refreshesLeft(), max - 1), now - 1);
        expect = 'tick+1（refreshes+1、anchor=' + (now - 1 + regen) + '）';
    } else if (mode === 'max') {
        next = s.withRefreshes(max, now - 1);
        expect = 'NO-regen（已到上限 ⇒ 锚点不顺延，仍为 ' + (now - 1) + '）';
    } else if (mode === 'zero') {
        next = s.withRefreshes(1, 0);
        expect = 'NO-regen（从未刷新过 ⇒ anchor=0 不恢复）';
    } else if (mode === 'first') {
        next = s.withRefreshes(SP_ChipCatalog.REFRESH_INITIAL, 0);
        expect = 'crefresh 成功后 anchor=now+' + regen + '（首次消耗才起算）';
    } else if (mode === 'pend') {
        next = s.withRefreshes(max, now - 600);
        expect = 'crefresh 花 1 次（→' + (max - 1) + '）后下一 tick 立即补回 ' + max + '（已知 P2 口径，待用户裁决）';
    } else {
        sp_send(ctx, 'AP_' + tag + '_CREGEN:bad-mode=' + mode);
        return 0;
    }
    SP_ChipState.store(p, next);
    sp_send(ctx, 'AP_' + tag + '_CREGEN:mode=' + mode + ':refreshes=' + next.refreshesLeft() + '/' + max
        + ':anchor=' + next.regenAnchor() + ':delta=' + (next.regenAnchor() - now) + ':now=' + now + ':expect=' + expect);
    return 1;
}

/** 物品栏填满 / 清空（36 格，不含副手）—— 造「物品栏满」拒绝与腾空位 */
function sp_doChipInv(ctx, tag, mode) {
    var p = sp_p(ctx);
    var inv = p.inventory;
    var n = 0;
    if (mode === 'fill') {
        for (var i = 0; i < 36; i++) {
            var cur = inv.getItem(i);
            if (cur == null || cur.isEmpty()) { inv.setItem(i, new SP_ItemStack(SP_Items.STONE)); n++; }
        }
    } else if (mode === 'clear') {
        var air = new SP_ItemStack(SP_Items.AIR);
        for (var j = 0; j < 36; j++) {
            var c2 = inv.getItem(j);
            if (c2 != null && !c2.isEmpty()) { inv.setItem(j, air); n++; }
        }
    } else {
        sp_send(ctx, 'AP_' + tag + '_CINV:bad-mode=' + mode);
        return 0;
    }
    sp_send(ctx, 'AP_' + tag + '_CINV:mode=' + mode + ':changed=' + n + ':inv=' + cp_invCount(p) + '/36');
    return 1;
}

/** 物品栏满时开店 ⇒ 期望「无菜单 + CHIP_SHOP_OPEN_DENIED reason=inventory_full」 */
function sp_doChipDeny(ctx, tag) {
    var p = sp_p(ctx);
    var before = sp_cls(p.containerMenu);
    var all = -1;
    try { all = SP_ChipRoller.ownsEverything(p) ? 1 : 0; } catch (e0) { all = -1; }
    SP_ChipOpener.open(p);
    sp_send(ctx, 'AP_' + tag + '_CDENY:before=' + before + ':after=' + sp_cls(p.containerMenu)
        + ':inv=' + cp_invCount(p) + '/36:all=' + all);
    cp_stateLine(ctx, tag, cp_state(p));
    try { sp_closeMenu(p); } catch (e) { }
    return 1;
}

/** 关店重开候选恒定（未拥有时 needsReroll 为假 ⇒ 不重抽） */
function sp_doChipStable(ctx, tag) {
    var p = sp_p(ctx);
    try { sp_closeMenu(p); } catch (e0) { }
    SP_ChipOpener.open(p);
    var m1 = cp_menuOf(p);
    var s1 = cp_state(p);
    var key1 = m1 == null ? '?' : m1.offersKey();
    try { sp_closeMenu(p); } catch (e1) { }
    SP_ChipOpener.open(p);
    var m2 = cp_menuOf(p);
    var s2 = cp_state(p);
    var key2 = m2 == null ? '?' : m2.offersKey();
    sp_send(ctx, 'AP_' + tag + '_CSTABLE:key1=' + key1 + ':key2=' + key2 + ':same=' + (key1 === key2 ? 1 : 0)
        + ':offers=' + cp_offersCsv(s1) + '|' + cp_offersCsv(s2)
        + ':refreshes=' + s1.refreshesLeft() + '|' + s2.refreshesLeft()
        + ':need=' + (s1.needsReroll(p) ? 1 : 0) + '|' + (s2.needsReroll(p) ? 1 : 0));
    try { sp_closeMenu(p); } catch (e2) { }
    return 1;
}

/** 边界序列：余额不足 / 索引越界 ×2 / 成功购买 / 关店重进核对 */
function sp_doChipSeq(ctx, tag) {
    var p = sp_p(ctx);
    var max = SP_ChipCatalog.REFRESH_MAX;
    try { sp_closeMenu(p); } catch (e00) { }

    // ⓪ 基线
    sp_setBal(p, 1000);
    var s0 = cp_state(p);
    sp_send(ctx, 'AP_' + tag + '_CSEQ0:bal=' + sp_bal(p) + ':inv=' + cp_invCount(p) + '/36'
        + ':purchased=' + s0.purchasedCount() + ':refreshes=' + s0.refreshesLeft() + ':offers=' + cp_offersCsv(s0));

    // ① 余额不足：开店 → 买 0 号格 ⇒ 拒绝（余额 / 物品栏 / purchasedCount / offers 全不动）
    sp_setBal(p, 0);
    SP_ChipOpener.open(p);
    var m1 = cp_menuOf(p);
    if (m1 == null) { sp_send(ctx, 'AP_' + tag + '_CSEQ1:menu=null'); return 0; }
    var s1a = cp_state(p);
    var ex1 = '-';
    try { m1.handleBuy(p, 0); } catch (e1) { ex1 = 'EX:' + sp_exText(e1); }
    var s1b = cp_state(p);
    sp_send(ctx, 'AP_' + tag + '_CSEQ1:poor:ex=' + ex1 + ':menu=' + sp_cls(p.containerMenu) + ':bal=' + sp_bal(p)
        + ':inv=' + cp_invCount(p) + '/36:purchased=' + s1a.purchasedCount() + '->' + s1b.purchasedCount()
        + ':offers=' + (cp_offersCsv(s1a) === cp_offersCsv(s1b) ? 'same' : 'CHANGED')
        + ':deny=' + m1.denyReason());

    // ②③ 索引越界（−1 / 3）：静默拒绝，状态不变
    sp_setBal(p, 1000);
    var s2a = cp_state(p);
    try { m1.handleBuy(p, -1); } catch (e2) { }
    try { m1.handleBuy(p, 3); } catch (e3) { }
    var s2b = cp_state(p);
    sp_send(ctx, 'AP_' + tag + '_CSEQ23:bal=' + sp_bal(p) + ':inv=' + cp_invCount(p) + '/36'
        + ':purchased=' + s2a.purchasedCount() + '->' + s2b.purchasedCount()
        + ':offers=' + (cp_offersCsv(s2a) === cp_offersCsv(s2b) ? 'same' : 'CHANGED'));

    // ④ 成功购买：余额 −price、物品栏 +1 枚筹码、purchasedCount +1、offers 重抽、refreshes 不变、关店
    var preBal = sp_bal(p); var preIds = cp_invIds(p); var sPre = cp_state(p);
    var ex4 = '-';
    try { m1.handleBuy(p, 0); } catch (e4) { ex4 = 'EX:' + sp_exText(e4); }
    var sPost = cp_state(p);
    sp_send(ctx, 'AP_' + tag + '_CSEQ4:ex=' + ex4 + ':bal=' + preBal + '->' + sp_bal(p)
        + ':invDelta=' + cp_invDelta(preIds, cp_invIds(p))
        + ':purchased=' + sPre.purchasedCount() + '->' + sPost.purchasedCount()
        + ':refreshes=' + sPre.refreshesLeft() + '->' + sPost.refreshesLeft()
        + ':offers=' + cp_offersCsv(sPre) + '->' + cp_offersCsv(sPost)
        + ':menu=' + sp_cls(p.containerMenu) + ':lastCat=' + cp_lastCat(sPost));

    // ⑤ 关店重进：价格 = 上次 + 5、次数不变、三格候选全为未拥有
    SP_ChipOpener.open(p);
    var m5 = cp_menuOf(p);
    var s5 = cp_state(p);
    cp_menuLine(ctx, tag, m5);
    sp_send(ctx, 'AP_' + tag + '_CSEQ5:price=' + (m5 == null ? -1 : m5.price()) + ':purchased=' + s5.purchasedCount()
        + ':refreshes=' + s5.refreshesLeft() + '/' + max + ':owned=' + sp_len(SP_ChipRoller.ownedChipItems(p)));
    var flags = [];
    for (var i = 0; i < 3; i++) {
        var st = null;
        try { st = (m5 == null) ? null : m5.offerStack(i); } catch (e5) { st = null; }
        var sid = sp_id(st);
        if (sp_isEmptyId(sid)) { flags.push(i + ':empty'); continue; }
        var ent = null;
        try {
            var opt = SP_ChipCatalog.byStack(st);
            ent = (opt != null && opt.isPresent()) ? opt.get() : null;
        } catch (e6) { ent = null; }
        if (ent == null) { flags.push(i + ':' + sid + ':byStack=null'); continue; }
        var owned = -2;
        try { owned = SP_ChipRoller.isOwned(p, ent) ? 1 : 0; } catch (e7) { owned = -3; }
        flags.push(i + ':' + sid + ':owned=' + owned);
    }
    sp_send(ctx, 'AP_' + tag + '_CSEQ5OWN:' + flags.join(','));
    try { sp_closeMenu(p); } catch (e8) { }
    return 1;
}

// ════════════════════════════════════════════════════════════════════════════
//  CHIP-ECON 批(2026-10-14,筹码经济调整)取证 —— 机器行前缀 AP_<tag>_C*
//    ① crecipes   配方总开关:astral_dice:*_chip 配方条数 + ENABLE_CHIP_RECIPES 值
//    ② cblank     空白筹码投入:同 tick 投两次 ⇒ ①成功(刷新+1、清购买冷却) ②撞 2:00 冷却被拒且不消耗
//    ③ ccardstack 卡牌物品 maxStackSize 是否全为 16
//    ④ cmimi      看板娘立牌阈值常量 + 「累计返还战斗牌」计数器(99 ⇒ 不发;再 +2 ⇒ 发 1 个、余 1)
//    ⑤ cloot      战利品注入:埋藏宝藏 100% 必出空白筹码 / 地牢箱 200 次抽样(1%)
//  纪律:① 不改产品代码,副作用仅限测试存档;② 每个入口 sp_guard + try/catch,异常落机器行;
//        ③ 需要的 MC 类一律函数内 ce_cls() 解析(1.20.1 顶层类别名在命令回调作用域不可靠)。
//        ④ 判空一律用真值性(if (!x) / if (x)):本运行时 ResourceKey / ResourceLocation / ItemStack
//           都实现 SpecialEquality,与 null 比较会走 specialEquals(ResourceKey 的 specialEquals 首句
//           即 requireNonNull ⇒ 直接 NPE);字符串 / 数字之间的 ===/!== 不受影响。
// ════════════════════════════════════════════════════════════════════════════

/** 函数内惰性解析 Java 类(失败 ⇒ null) */
function ce_cls(n) { try { return spTryClass(n); } catch (e) { return null; } }

/** 主手 InteractionHand(两版同名) */
function ce_handMain() { try { return ce_cls('net.minecraft.world.InteractionHand').MAIN_HAND; } catch (e) { return null; } }

/** ResourceLocation 兜底构造(1.21.1 = parse;1.20.1 = new) */
function ce_rl(full) {
    var RL = ce_cls('net.minecraft.resources.ResourceLocation');
    if (!RL) return null;
    try { var a = RL.parse(full); if (a) return a; } catch (e0) { }
    try { return new RL(full); } catch (e1) { }
    return null;
}

/** ResourceKey<LootTable> 构造(1.21.1 走它;失败 ⇒ null) */
function ce_lootKey(tableId) {
    try {
        var rl = ce_rl(tableId);
        if (!rl) return null; // 真值性判空:ResourceLocation 是 SpecialEquality,禁止与 null 比较
        return ce_cls('net.minecraft.resources.ResourceKey')
            .create(ce_cls('net.minecraft.core.registries.Registries').LOOT_TABLE, rl);
    } catch (e) { return null; }
}

/** 给方块实体设战利品表:① 1.21.1 =(ResourceKey<LootTable>,long);② 1.20.1 =(ResourceLocation,long) */
function ce_setLootTable(be, tableId, seed) {
    try {
        // 不写 key !== null:本运行时 ResourceKey 实现了 SpecialEquality,与 null 比较会走
        // specialEquals ⇒ requireNonNull 抛 NPE(2026-10-14 实机栈)。失败/重载不适用改用异常分流。
        var key = ce_lootKey(tableId);
        be.setLootTable(key, seed);
        return 'key';
    } catch (e0) { }
    var rl = ce_rl(tableId);
    be.setLootTable(rl, seed);
    return 'rl';
}

/** RecipeHolder id 兼容取值(1.21.1 = id();1.20.1 亦可能是 getId()) */
function ce_recipeId(h) {
    try { var a = h.id(); if (a) return '' + a; } catch (e0) { }
    try { var b = h.getId(); if (b) return '' + b; } catch (e1) { }
    return '';
}

/** 物品 id 是否「筹码」口径(astral_dice 命名空间 + `_chip` 结尾) */
function ce_isChipId(id) {
    if (id === null) return false;
    if (id.indexOf('astral_dice:') !== 0) return false;
    return id.substring(id.length - 5) === '_chip';
}

/** 物品栏 36 格中筹码总枚数(astral_dice:*_chip;blank_chip 不计) */
function ce_chipInvCount(p) {
    var n = 0;
    try {
        var inv = p.inventory;
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (!st) continue; // 真值性判空(ItemStack 是 SpecialEquality)
            if (st.isEmpty()) continue;
            var id = sp_id(st);
            if (ce_isChipId(id) && id !== 'astral_dice:blank_chip') { n += st.getCount(); }
        }
    } catch (e) { return -1; }
    return n;
}

/** 清空物品栏 36 格(不含副手) */
function ce_clearInv(p) {
    var n = 0;
    try {
        var inv = p.inventory;
        var air = new SP_ItemStack(SP_Items.AIR);
        for (var i = 0; i < 36; i++) {
            var cur = inv.getItem(i);
            if (cur && !cur.isEmpty()) { inv.setItem(i, air); n++; }
        }
    } catch (e) { return -1; }
    return n;
}

/** ① /astralshop crecipes <tag> ⇒ AP_<tag>_CRECIPES:chips=<n>:config=<0|1>:ex=<…>
 *  n = recipeManager 中 namespace==astral_dice 且 path 以 `_chip` 结尾的配方条数 */
function sp_doEconRecipes(ctx, tag) {
    var p = sp_p(ctx);
    var lvl = sp_level(p);
    var chips = -1; var ex = '-';
    try {
        var holders = lvl.getRecipeManager().getRecipes();
        var it = holders.iterator();
        var n = 0;
        while (it.hasNext()) {
            var rid = ce_recipeId(it.next());
            if (rid.indexOf('astral_dice:') !== 0) continue;
            if (rid.substring(rid.length - 5) !== '_chip') continue;
            n++;
        }
        chips = n;
    } catch (e0) { ex = sp_exText(e0); }
    var cfg = -1;
    try {
        var MCC = ce_cls('com.merlinkitsune.astral_dice.config.ModCommonConfig');
        cfg = MCC.ENABLE_CHIP_RECIPES.get() ? 1 : 0;
    } catch (e1) { cfg = -2; }
    sp_send(ctx, 'AP_' + tag + '_CRECIPES:chips=' + chips + ':config=' + cfg + ':ex=' + ex);
    return 1;
}

/** ② /astralshop cblank <tag> —— 空白筹码投入(同 tick 两次)
 *  期望:res1/res2 均为 sidedSuccess(false);refreshes +1(只一次);blankAt 0 -> 非 0;hand=1;verdict=PASS */
function sp_doEconBlank(ctx, tag) {
    var p = sp_p(ctx);
    var lvl = sp_level(p);
    var pos = p.blockPosition().offset(2, 0, 0);
    var ModBlocks = ce_cls('com.merlinkitsune.astral_dice.block.ModBlocks');
    var ModItems = ce_cls('com.merlinkitsune.astral_dice.item.ModItems');
    if (!ModBlocks || !ModItems) { sp_send(ctx, 'AP_' + tag + '_CBLANK:ex=no_class'); return 0; }
    // 确保脚边 chip_shop 方块在位(没有就放;已在位则不覆盖 —— 跨用例残留不影响本取证)
    var placed = 0;
    try {
        var want = ModBlocks.CHIP_SHOP.get();
        // 方块身份改用注册名比较(两端 Java 对象 `!==` 会走 Rhino specialEquals / 非 JS 对象警告通道)
        var RegB = ce_cls('net.minecraft.core.registries.BuiltInRegistries');
        var wantId = ''; var curId = '';
        try { wantId = '' + RegB.BLOCK.getKey(want); } catch (eW) { wantId = ''; }
        try { curId = '' + RegB.BLOCK.getKey(lvl.getBlockState(pos).getBlock()); } catch (eC) { curId = ''; }
        if (!wantId || curId !== wantId) { lvl.setBlockAndUpdate(pos, want.defaultBlockState()); placed = 1; }
    } catch (e0) { sp_send(ctx, 'AP_' + tag + '_CBLANK:ex=place:' + sp_exText(e0)); return 0; }
    // 主手持 2 枚空白筹码
    try { p.setItemInHand(ce_handMain(), new SP_ItemStack(ModItems.BLANK_CHIP.get(), 2)); }
    catch (e1) { sp_send(ctx, 'AP_' + tag + '_CBLANK:ex=hand:' + sp_exText(e1)); return 0; }
    var s0 = cp_state(p);
    var r0 = s0 ? s0.refreshesLeft() : -1;
    var b0 = s0 ? s0.lastBlankInsertAt() : -1;
    var r1 = 'n/a'; var r2 = 'n/a'; var exU = '-';
    try {
        var bs = lvl.getBlockState(pos);
        var hit = new SP_BlockHitResult(new SP_Vec3(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5),
            SP_Direction.UP, pos, false);
        r1 = '' + ce_useBlank(bs, p, lvl, hit);
        r2 = '' + ce_useBlank(bs, p, lvl, hit);
    } catch (e2) { exU = sp_exText(e2); }
    var s1 = cp_state(p);
    var rf = s1 ? s1.refreshesLeft() : -1;
    var bf = s1 ? s1.lastBlankInsertAt() : -1;
    var hand = -1;
    try { hand = p.getItemInHand(ce_handMain()).getCount(); } catch (e3) { hand = -2; }
    var ok = (r0 >= 0 && rf === r0 + 1 && b0 === 0 && bf !== 0 && hand === 1 && exU === '-') ? 'PASS' : 'FAIL';
    sp_send(ctx, 'AP_' + tag + '_CBLANK:res1=' + r1 + ':res2=' + r2 + ':hand=' + hand
        + ':refreshes=' + r0 + '->' + rf + ':blankAt=' + b0 + '->' + bf
        + ':placed=' + placed + ':verdict=' + ok + ':ex=' + exU);
    return 1;
}

/** ③ /astralshop ccardstack <tag> ⇒ AP_<tag>_CCARDS:cards=<n>:all16=<0|1>:bad=<ids|->:via=…:n=…
 *  遍历模组物品注册项 → isCardItem 过滤 → maxStackSize 必须全为 16 */
function sp_doEconCards(ctx, tag) {
    var ModItems = ce_cls('com.merlinkitsune.astral_dice.item.ModItems');
    var Reg = ce_cls('net.minecraft.core.registries.BuiltInRegistries');
    var items = [];
    var via = 'none';
    // ① DeferredRegister.getEntries()(NeoForge DeferredRegister.Items / Forge DeferredRegister 均有)
    try {
        if (ModItems) {
            var ents = ModItems.ITEMS.getEntries();
            var it = ents.iterator();
            while (it.hasNext()) {
                var item = null;
                try { item = it.next().get(); } catch (eG) { item = null; }
                if (item) items.push(item);
            }
            if (items.length > 0) via = 'entries';
        }
    } catch (e0) { items = []; }
    // ② 兜底:遍历物品注册表(仅 astral_dice 命名空间)
    if (items.length === 0 && Reg) {
        try {
            var it2 = Reg.ITEM.iterator();
            while (it2.hasNext()) {
                var item2 = it2.next();
                var id2 = '';
                try { id2 = '' + Reg.ITEM.getKey(item2); } catch (eK) { id2 = ''; }
                if (id2.indexOf('astral_dice:') === 0) items.push(item2);
            }
            via = 'registry';
        } catch (e1) { }
    }
    var cards = 0; var bad = [];
    try {
        for (var i = 0; i < items.length; i++) {
            var st = new SP_ItemStack(items[i]);
            if (!ModItems.isCardItem(st)) continue;
            cards++;
            var mx = -1;
            try { mx = st.getMaxStackSize(); } catch (eM) { mx = -1; }
            if (mx !== 16) bad.push(sp_itemIdOf(items[i]) + '/x' + mx);
        }
    } catch (e2) { bad.push('ex:' + sp_exText(e2)); }
    sp_send(ctx, 'AP_' + tag + '_CCARDS:cards=' + cards + ':all16=' + (bad.length === 0 ? 1 : 0)
        + ':bad=' + (bad.length === 0 ? '-' : bad.join(',')) + ':via=' + via + ':n=' + items.length);
    return 1;
}

/** ④ /astralshop cmimi <tag> —— 阈值常量 + 「累计返还战斗牌」计数器行为
 *  期望:threshold=100;a 段回归 99 张不发(chips=0:counter=99);b 段再 +2 ⇒ 发 1 个、余 1(chips=1:counter=1) */
function sp_doEconMimi(ctx, tag) {
    var p = sp_p(ctx);
    var mimi = ce_cls('com.merlinkitsune.astral_dice.item.sign.MimiSignItem');
    var att = ce_cls('com.merlinkitsune.astral_dice.component.ModAttachments');
    var th = -1;
    try { th = mimi.RETURNED_CARD_THRESHOLD; } catch (e0) { th = -1; }
    sp_send(ctx, 'AP_' + tag + '_CMIMI:threshold=' + th);
    if (!mimi || !att) { sp_send(ctx, 'AP_' + tag + '_CMIMI:equip=FAIL:dice=-:stand=-:is=-3'); return 0; }
    // 装备:骰子(dice 槽)+ 看板娘立牌(stand 槽) —— isEquipped 要求两者齐备
    var eD = sp_setCurio(p, 'dice', 'astral_dice:emerald_dice', 0);
    var eS = sp_setCurio(p, 'stand', 'astral_dice:mimi_sign', 0);
    var eq = -1;
    try { eq = mimi.isEquipped(p) ? 1 : 0; } catch (e1) { eq = -2; }
    if (eq !== 1) {
        sp_send(ctx, 'AP_' + tag + '_CMIMI:equip=FAIL:dice=' + (eD ? eD : 'ok')
            + ':stand=' + (eS ? eS : 'ok') + ':is=' + eq);
        return 0;
    }
    var cleared = ce_clearInv(p);
    var z = '-';
    try { att.setMimiReturnedCardCount(p, 0); } catch (e2) { z = sp_exText(e2); }
    var exA = '-';
    try { mimi.onBattleCardsReturned(p, 99); } catch (e3) { exA = sp_exText(e3); }
    var cA = -1;
    try { cA = att.getMimiReturnedCardCount(p); } catch (e4) { cA = -2; }
    sp_send(ctx, 'AP_' + tag + '_CMIMI:a:chips=' + ce_chipInvCount(p) + ':counter=' + cA
        + ':cleared=' + cleared + ':zero=' + z + ':ex=' + exA);
    var exB = '-';
    try { mimi.onBattleCardsReturned(p, 2); } catch (e5) { exB = sp_exText(e5); }
    var cB = -1;
    try { cB = att.getMimiReturnedCardCount(p); } catch (e6) { cB = -2; }
    sp_send(ctx, 'AP_' + tag + '_CMIMI:b:chips=' + ce_chipInvCount(p) + ':counter=' + cB + ':ex=' + exB);
    return 1;
}

/** 战利品表单次实验:放箱子 → 设表+种子 → 开箱 → 查目标 id → 清箱挖箱;返回 1=命中 / 0=未命中 */
function ce_lootTrial(lvl, p, pos, chestBlock, tableId, seed, wantId, info) {
    lvl.setBlockAndUpdate(pos, chestBlock.defaultBlockState());
    var be = null;
    try { be = lvl.getBlockEntity(pos); } catch (e0) { be = null; }
    if (!be) { info.via = 'no_be'; return 0; }
    info.via = ce_setLootTable(be, tableId, seed);
    try { be.unpackLootTable(p); }
    catch (e1) { try { be.unpackLootTable(null); } catch (e2) { info.via = info.via + '+unpackEX:' + sp_exText(e2); } }
    var size = 27;
    try { size = be.getContainerSize(); } catch (e3) { size = 27; }
    var out = 0;
    for (var i = 0; i < size; i++) {
        var st = null;
        try { st = be.getItem(i); } catch (e4) { st = null; }
        if (!st) continue; // 真值性判空(ItemStack 是 SpecialEquality)
        if (st.isEmpty()) continue;
        if (sp_id(st) === wantId) { out = 1; break; }
    }
    try { be.clearContent(); } catch (e5) { }
    try { lvl.setBlockAndUpdate(pos, ce_cls('net.minecraft.world.level.block.Blocks').AIR.defaultBlockState()); } catch (e6) { }
    return out;
}

/** ⑤ /astralshop cloot <tag> —— 战利品注入定性/定量取证(箱子放在脚边 offset(2,0,2)) */
function sp_doEconLoot(ctx, tag) {
    var p = sp_p(ctx);
    var lvl = sp_level(p);
    var Blocks = ce_cls('net.minecraft.world.level.block.Blocks');
    if (!Blocks) { sp_send(ctx, 'AP_' + tag + '_CLOOT:ex=no_class'); return 0; }
    var pos = p.blockPosition().offset(2, 0, 2);
    var chest = Blocks.CHEST;
    // ① 埋藏的宝藏:空白筹码 100% 必出
    var has = -1; var ex1 = '-'; var i1 = { via: '-' };
    try { has = ce_lootTrial(lvl, p, pos, chest, 'minecraft:chests/buried_treasure', 20261014, 'astral_dice:blank_chip', i1); }
    catch (e0) { ex1 = sp_exText(e0); }
    sp_send(ctx, 'AP_' + tag + '_CLOOT:treasure:has=' + has + ':via=' + i1.via + ':ex=' + ex1);
    // ② 地牢箱 200 次(每次换种子):1% 期望 ≈2 ⇒ n<=10 判 PASS
    var n = 0; var fails = 0; var TRIALS = 200; var i2 = { via: '-' };
    for (var i = 0; i < TRIALS; i++) {
        try {
            if (ce_lootTrial(lvl, p, pos, chest, 'minecraft:chests/simple_dungeon', 910000 + i * 7919, 'astral_dice:blank_chip', i2) === 1) n++;
        } catch (e1) { fails++; }
    }
    sp_send(ctx, 'AP_' + tag + '_CLOOT:dungeon:n=' + n + ':verdict=' + (n <= 10 ? 'PASS' : 'FAIL')
        + ':trials=' + TRIALS + ':fails=' + fails + ':via=' + i2.via);
    return 1;
}

/** 版本口径(1.20.1):BlockState#use(level, player, hand, hit) */
function ce_useBlank(bs, p, lvl, hit) {
    return bs.use(lvl, p, ce_handMain(), hit);
}

// ── 命令注册 ──────────────────────────────────────────────────────────────

// ══ 事件抽奖盒 / 奇怪的问号（2026-10-14 .116/.117 批）══════════════════════════
//  取证口径：① 本段探针机器行 AP_<tag>_EV*；② 产品侧行（都在 logs/latest.log）——
//    · AP_EVENT_DRAWER: player=<n>:buy=ok:bal=A->B        （购买成功）
//    · AP_EVENT_DRAWER: player=<n>:denied=cooldown:remain=N（冷却中拒绝）
//    · AP_EVENT_DRAWER: player=<n>:denied=insufficient:bal=N（余额不足拒绝）
//    · AP_EVENT_DRAW: src=ITEM_USE:id=<id>                 （奇怪的问号抽到的事件）
//    · AP_EVENT_FIRE: id=<id>:src=ITEM_USE:tier=0:targetsFinal=<n>（事件真正生效；弹卡后 40t）
//  ⚠️ 三种拒绝**只有 actionbar + 产品行**，没有 S2C 之外的通道 ⇒ 判据 = 状态未变 + 产品拒绝行。
//  行差（与 cblock 同口径）：1.21.1 = BlockState#useWithoutItem(Level,Player,BlockHitResult)；
//                            1.20.1  = BlockState#use(Level,Player,InteractionHand,BlockHitResult)。
//  ⚠️ 类名一律**在函数内 spTryClass 解析**（不引顶层 var 别名）：1.20.1 侧顶层 var 别名在命令回调里
//     曾取不到（ReferenceError，见 sp_doPromo 注记），内联解析是已验证的写法。
//  ⚠️ 冷却记在玩家附件里（跨 launch 保留）⇒ 用例开头必须 `evcd <tag> zero` 先清冷却。
//  ⚠️ 副作用边界：evblock/evuse 只放方块与右键；estack 会清空物品栏（用例须在 /clear 之后调用）。

/** 上一次 evblock 放的抽奖盒位置（evuse 用；顶层 var 跨命令保留，与 sp_lastVillager 同口径） */
var ev_lastPos = null;

/** 物品栏 36 格内 astral_dice:event_ticket 的总张数（不含副手） */
function ev_tickets(p) {
    var n = 0;
    try {
        var inv = p.getInventory();
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (st == null || st.isEmpty()) { continue; }
            if (sp_id(st) === 'astral_dice:event_ticket') { n += st.getCount(); }
        }
    } catch (e) { return -1; }
    return n;
}

/** 抽奖盒冷却附件（-1 = 取不到） */
function ev_cd(p) {
    var M = spTryClass('com.merlinkitsune.astral_dice.component.ModAttachments');
    if (M == null) { return -1; }
    try { return M.getEventDrawerCooldownUntil(p); } catch (e) { return -1; }
}

/** 物品栏 36 格非空清单（'i:id xN' 逗号连接；空 → 'none'） */
function ev_invList(p) {
    var out = [];
    try {
        var inv = p.getInventory();
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (st == null || st.isEmpty()) { continue; }
            out.push(i + ':' + sp_id(st) + 'x' + st.getCount());
        }
    } catch (e) { return '<ex>'; }
    return out.length ? out.join(',') : 'none';
}

/** 物品栏 36 格清空（返回清除格数） */
function ev_clearInv(p) {
    var n = 0;
    try {
        var inv = p.getInventory();
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (st != null && !st.isEmpty()) { inv.setItem(i, new SP_ItemStack(SP_Items.AIR)); n++; }
        }
    } catch (e) { return -1; }
    return n;
}

/** 脚边 12 格内的 ItemEntity 清单（'id xN'） */
function ev_drops(p) {
    var out = [];
    try {
        var IE = spTryClass('net.minecraft.world.entity.item.ItemEntity');
        var AABB = spTryClass('net.minecraft.world.phys.AABB');
        var list = sp_level(p).getEntitiesOfClass(IE, AABB.ofSize(p.position(), 12.0, 12.0, 12.0));
        for (var i = 0; i < sp_len(list); i++) {
            var e = sp_at(list, i);
            var st = e.getItem();
            out.push(sp_id(st) + 'x' + st.getCount());
        }
    } catch (e0) { return ['<err:' + sp_exText(e0) + '>']; }
    return out;
}

/** 公共事件池快照：玩家条件版（publicPool(Player)）+ 静态版（publicPool()） */
function ev_pool(p) {
    var out = { ids: [], n: -1, hasStage: -1, statN: -1, statStage: -1, err: '-' };
    try {
        var R = spTryClass('com.merlinkitsune.astral_dice.event.AstralEventRegistry');
        if (R == null) { out.err = 'no_registry'; return out; }
        var list = R.publicPool(p);
        out.n = sp_len(list);
        for (var i = 0; i < out.n; i++) { out.ids.push('' + sp_at(list, i).id()); }
        out.hasStage = 0;
        for (var j = 0; j < out.ids.length; j++) {
            if (out.ids[j] === 'astral_dice:investigation_stage') { out.hasStage = 1; }
        }
        var stat = R.publicPool();
        out.statN = sp_len(stat);
        out.statStage = 0;
        for (var k = 0; k < out.statN; k++) {
            if (('' + sp_at(stat, k).id()) === 'astral_dice:investigation_stage') { out.statStage = 1; }
        }
    } catch (e) { out.err = sp_exText(e); }
    return out;
}

/** ① /astralshop evblock <tag> —— 脚边 +X2 放 astral_dice:event_drawer；读方块/物品的 key 与显示名 */
function ev_doBlock(ctx, tag) {
    var p = sp_p(ctx);
    var lvl = sp_level(p);
    var MB = spTryClass('com.merlinkitsune.astral_dice.block.ModBlocks');
    var MI = spTryClass('com.merlinkitsune.astral_dice.item.ModItems');
    if (MB == null || MI == null) { sp_send(ctx, 'AP_' + tag + '_EVBLOCK:ex=no_class'); return 0; }
    var pos = p.blockPosition().offset(2, 0, 0);
    var ok = 0;
    try { ok = lvl.setBlockAndUpdate(pos, MB.EVENT_DRAWER.get().defaultBlockState()) ? 1 : 0; }
    catch (e) { sp_send(ctx, 'AP_' + tag + '_EVBLOCK:ex=' + sp_exText(e)); return 0; }
    ev_lastPos = pos;
    var bid = sp_id(new SP_ItemStack(lvl.getBlockState(pos).getBlock().asItem()));
    var bkey = '?'; var bname = '?'; var ikey = '?'; var iname = '?';
    try {
        var bi = MB.EVENT_DRAWER.get().asItem();
        bkey = '' + bi.getDescriptionId();
        bname = '' + new SP_ItemStack(bi).getHoverName().getString();
    } catch (e1) { bkey = 'EX:' + sp_exText(e1); }
    try {
        var ii = MI.EVENT_TICKET.get();
        ikey = '' + ii.getDescriptionId();
        iname = '' + new SP_ItemStack(ii).getHoverName().getString();
    } catch (e2) { ikey = 'EX:' + sp_exText(e2); }
    sp_send(ctx, 'AP_' + tag + '_EVBLOCK:set=' + ok + ':pos=' + pos.getX() + ',' + pos.getY() + ',' + pos.getZ()
        + ':block=' + bid + ':bal=' + sp_bal(p) + ':tickets=' + ev_tickets(p) + ':cd=' + ev_cd(p) + ':now=' + sp_tick(p));
    sp_send(ctx, 'AP_' + tag + '_EVKEY:blockKey=' + bkey + ':itemKey=' + ikey
        + ':blockName=' + bname + ':itemName=' + iname);
    return 1;
}

/** ② /astralshop evuse <tag> —— 真实右键抽奖盒（与玩家右键同入口）→ 余额/票据/冷却前后差 + 剩余冷却 */
function ev_doUse(ctx, tag) {
    var p = sp_p(ctx);
    var lvl = sp_level(p);
    var pos = ev_lastPos;
    if (pos == null) { pos = p.blockPosition().offset(2, 0, 0); }
    var b0 = sp_bal(p); var t0 = ev_tickets(p); var c0 = ev_cd(p); var now0 = sp_tick(p);
    var res = 'n/a';
    try {
        var st = lvl.getBlockState(pos);
        var hit = new SP_BlockHitResult(new SP_Vec3(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5), SP_Direction.UP, pos, false);
        res = '' + st.use(lvl, p, SP_Hand.MAIN_HAND, hit);
    } catch (e1) { res = 'EX:' + sp_exText(e1); }
    var c1 = ev_cd(p); var now1 = sp_tick(p);
    sp_send(ctx, 'AP_' + tag + '_EVUSE:pos=' + pos.getX() + ',' + pos.getY() + ',' + pos.getZ()
        + ':res=' + res + ':bal=' + b0 + '->' + sp_bal(p)
        + ':tickets=' + t0 + '->' + ev_tickets(p)
        + ':cd=' + c0 + '->' + c1 + ':delta=' + (c1 - now0) + ':remain=' + (c1 - now1) + ':now=' + now1);
    return 1;
}

/** ③ /astralshop evcd <tag> <read|zero> —— 冷却附件读 / 清零（跨 launch 保留 ⇒ 用例开头必清） */
function ev_doCd(ctx, tag, mode) {
    var p = sp_p(ctx);
    var M = spTryClass('com.merlinkitsune.astral_dice.component.ModAttachments');
    var before = -1; var ex = '-';
    try { before = M.getEventDrawerCooldownUntil(p); } catch (e0) { ex = 'get:' + sp_exText(e0); }
    if (mode === 'zero') {
        try { M.setEventDrawerCooldownUntil(p, 0); } catch (e1) { ex = 'set:' + sp_exText(e1); }
    } else if (mode !== 'read') {
        sp_send(ctx, 'AP_' + tag + '_EVCD:bad-mode=' + mode); return 0;
    }
    var after = -1;
    try { after = M.getEventDrawerCooldownUntil(p); } catch (e2) { ex = 'get2:' + sp_exText(e2); }
    var now = sp_tick(p);
    sp_send(ctx, 'AP_' + tag + '_EVCD:mode=' + mode + ':ex=' + ex + ':cd=' + before + '->' + after
        + ':remain=' + (after - now) + ':now=' + now + ':bal=' + sp_bal(p) + ':tickets=' + ev_tickets(p));
    return 1;
}

/** ④ /astralshop evticket <tag> —— 主手放 1 张奇怪的问号 → ItemStack#use（= 服务端 useItem 同一入口）
 *      → 读数：手牌 1→0（生存消耗 / 创造不消耗）+ 事件池快照（无秘密侦探 12 条、有则 13 条） */
function ev_doTicket(ctx, tag) {
    var p = sp_p(ctx);
    var lvl = sp_level(p);
    var MI = spTryClass('com.merlinkitsune.astral_dice.item.ModItems');
    var Hand = spTryClass('net.minecraft.world.InteractionHand');
    if (MI == null || Hand == null) { sp_send(ctx, 'AP_' + tag + '_EVTICKET:ex=no_class'); return 0; }
    var hand = Hand.MAIN_HAND;
    var put = '-';
    try { p.setItemInHand(hand, new SP_ItemStack(MI.EVENT_TICKET.get())); put = 'ok'; }
    catch (e0) { put = 'EX:' + sp_exText(e0); }
    var stack = null;
    try { stack = p.getItemInHand(hand); } catch (e1) { }
    var before = (stack == null) ? -1 : stack.getCount();
    var pool = ev_pool(p);
    var res = 'n/a'; var ex = '-';
    try { res = '' + stack.use(lvl, p, hand); } catch (e2) { ex = sp_exText(e2); }
    var after = -1; var handId = '?';
    try { var h2 = p.getItemInHand(hand); handId = sp_id(h2); after = h2.getCount(); } catch (e3) { }
    sp_send(ctx, 'AP_' + tag + '_EVTICKET:put=' + put + ':ex=' + ex + ':res=' + res
        + ':hand=' + handId + ':before=' + before + ':after=' + after
        + ':pool=' + pool.n + ':hasStage=' + pool.hasStage + ':static=' + pool.statN + ':staticStage=' + pool.statStage
        + ':ids=' + pool.ids.join('|') + ':poolErr=' + pool.err);
    return 1;
}

/** ⑤ /astralshop evpool <tag> —— 秘密侦探闸门（三步同命令内、自还原）：
 *      无立牌 ⇒ pool=12/hasStage=0；stand 槽佩 astral_dice:bonnie_sign ⇒ pool=13/hasStage=1；
 *      随后**还原 stand 槽原内容**（避免污染卡牌商店的经商立牌加成）；静态池恒 13/含 survey 事件 */
function ev_doPool(ctx, tag) {
    var p = sp_p(ctx);
    var before = sp_curioStackText(p, 'stand');
    var off = ev_pool(p);
    var e1 = sp_setCurio(p, 'stand', 'astral_dice:bonnie_sign', 0);
    var on = ev_pool(p);
    var restore = (before === 'empty' || before === '<no_slot>') ? 'none' : before;
    var e2 = sp_setCurio(p, 'stand', restore, 0);
    var back = ev_pool(p);
    sp_send(ctx, 'AP_' + tag + '_EPOOL:mode=none:secret=0:pool=' + off.n + ':hasStage=' + off.hasStage
        + ':static=' + off.statN + ':staticStage=' + off.statStage + ':err=' + off.err + ':ids=' + off.ids.join('|'));
    sp_send(ctx, 'AP_' + tag + '_EPOOL:mode=sign:setErr=' + (e1 == null ? 'none' : e1)
        + ':secret=1:pool=' + on.n + ':hasStage=' + on.hasStage + ':ids=' + on.ids.join('|'));
    sp_send(ctx, 'AP_' + tag + '_EPOOL:restoreErr=' + (e2 == null ? 'none' : e2) + ':stand=' + sp_curioStackText(p, 'stand')
        + ':pool=' + back.n + ':hasStage=' + back.hasStage);
    return 1;
}

/** ⑥ /astralshop estack <tag> —— 三商店方块 stacksTo(1) + 奇怪的问号 stacksTo(16) 的**端到端**判据：
 *      清空 36 格 → **逐个** add N 次（每次一个单件栈）→ 逐格读数（stacksTo(1) ⇒ N 格各 1；stacksTo(16) ⇒ 16+4 两格）。
 *      ⚠️ 必须**逐个单件** add：实机现象——单次 add N 件（N>1）在 1.20.1 与 1.21.1 读数不一致
 *         （1.20.1 下 8 件落进**同一格**）；逐件 add 则每次只提交 1 件，两线口径必然统一。
 *      ⚠️ 会清空物品栏（用例须在 /clear @s 之后调用）。 */
function ev_doStacks(ctx, tag) {
    var p = sp_p(ctx);
    var MB = spTryClass('com.merlinkitsune.astral_dice.block.ModBlocks');
    var MI = spTryClass('com.merlinkitsune.astral_dice.item.ModItems');
    if (MB == null || MI == null) { sp_send(ctx, 'AP_' + tag + '_ESTACK:ex=no_class'); return 0; }
    var rows = [
        ['card_shop', MB.CARD_SHOP.get().asItem(), 8],
        ['chip_shop', MB.CHIP_SHOP.get().asItem(), 8],
        ['event_drawer', MB.EVENT_DRAWER.get().asItem(), 8],
        ['event_ticket', MI.EVENT_TICKET.get(), 20]
    ];
    for (var i = 0; i < rows.length; i++) {
        ev_clearInv(p);
        var max = -1;
        try { max = new SP_ItemStack(rows[i][1]).getMaxStackSize(); } catch (e0) { max = -2; }
        var want = rows[i][2];
        var addRet = 1;
        var remain = 0;
        for (var k = 0; k < want; k++) {
            var one = new SP_ItemStack(rows[i][1], 1);
            try {
                if (!p.getInventory().add(one)) { addRet = 0; }
            } catch (e1) { addRet = -2; break; }
            remain += one.getCount();
        }
        var slots = 0;
        try {
            var inv2 = p.getInventory();
            for (var s = 0; s < 36; s++) {
                var cur = inv2.getItem(s);
                if (cur != null && !cur.isEmpty()) { slots++; }
            }
        } catch (e2) { slots = -1; }
        sp_send(ctx, 'AP_' + tag + '_ESTACK:id=' + rows[i][0] + ':want=' + want + ':max=' + max
            + ':slots=' + slots + ':addRet=' + addRet + ':remain=' + remain + ':inv=' + ev_invList(p));
    }
    ev_clearInv(p);
    sp_send(ctx, 'AP_' + tag + '_ESTACK:done=1:inv=' + ev_invList(p));
    return 1;
}

/** ⑦ /astralshop edrop <tag> —— 满包购买落点取证：物品栏（不得含筹码）+ 脚边 ItemEntity 清单 */
function ev_doDrop(ctx, tag) {
    var p = sp_p(ctx);
    var inv = ev_invList(p);
    var chips = 0;
    if (inv !== 'none' && inv !== '<ex>') {
        var parts = inv.split(',');
        for (var i = 0; i < parts.length; i++) {
            if (/astral_dice:[a-z0-9_]*_chipx/.test(parts[i])) { chips++; }
        }
    }
    var drops = ev_drops(p);
    sp_send(ctx, 'AP_' + tag + '_EDROP:chips=' + chips + ':ndrops=' + drops.length
        + ':drops=' + drops.join(',') + ':inv=' + inv + ':bal=' + sp_bal(p) + ':tickets=' + ev_tickets(p));
    return 1;
}

ServerEvents.commandRegistry(event => {
    var Commands = event.commands;
    event.register(
        Commands.literal('astralshop')
            .then(Commands.literal('diag')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => {
                        var tg = 'D?';
                        try {
                            tg = SP_StringArg.getString(ctx, 'tag');
                            return sp_guard(ctx, tg, function () { return sp_doDiag(ctx, tg); });
                        } catch (eD) {
                            sp_send(ctx, 'AP_' + tg + '_OUTER:' + sp_exText(eD));
                            return 0;
                        }
                    })))
            .then(Commands.literal('recipes')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('item', SP_StringArg.string())
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doRecipes(ctx, SP_StringArg.getString(ctx, 'tag'), SP_StringArg.getString(ctx, 'item'));
                        })))))
            .then(Commands.literal('probe')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doProbe(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('reset')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doReset(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('block')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doBlock(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('open')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doOpen(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('openkeep')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doOpenKeep(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('menu')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guardMenu(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doMenu(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('buy')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('shelf', SP_IntArg.integer(-1, 99))
                        .then(Commands.argument('slot', SP_IntArg.integer(-1, 99))
                            .executes(ctx => sp_guardMenu(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                                return sp_doBuy(ctx, SP_StringArg.getString(ctx, 'tag'),
                                    SP_IntArg.getInteger(ctx, 'shelf'), SP_IntArg.getInteger(ctx, 'slot'));
                            }))))))
            .then(Commands.literal('buyseq')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guardMenu(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doBuySeq(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('bal')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('n', SP_IntArg.integer(0, 100000))
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doBal(ctx, SP_StringArg.getString(ctx, 'tag'), SP_IntArg.getInteger(ctx, 'n'));
                        })))))
            .then(Commands.literal('star')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('n', SP_IntArg.integer(0, 100000))
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doStar(ctx, SP_StringArg.getString(ctx, 'tag'), SP_IntArg.getInteger(ctx, 'n'));
                        })))))
            .then(Commands.literal('exchange')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guardMenu(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doExchange(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('tlist')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guardMenu(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doTList(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('transfer')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('name', SP_StringArg.string())
                        .then(Commands.argument('amount', SP_IntArg.integer(-10, 1000))
                            .executes(ctx => sp_guardMenu(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                                return sp_doTransfer(ctx, SP_StringArg.getString(ctx, 'tag'),
                                    SP_StringArg.getString(ctx, 'name'), SP_IntArg.getInteger(ctx, 'amount'));
                            }))))))
            .then(Commands.literal('ldbg')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doListDbg(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('txkeep')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('name', SP_StringArg.string())
                        .then(Commands.argument('amount', SP_IntArg.integer(-10, 1000))
                            .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                                return sp_doTxKeep(ctx, SP_StringArg.getString(ctx, 'tag'),
                                    SP_StringArg.getString(ctx, 'name'), SP_IntArg.getInteger(ctx, 'amount'));
                            }))))))
            .then(Commands.literal('tbad')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guardMenu(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doTBad(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('close')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doClose(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('reopen')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doReopen(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('dice')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('mode', SP_StringArg.word())
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doDice(ctx, SP_StringArg.getString(ctx, 'tag'), SP_StringArg.getString(ctx, 'mode'));
                        })))))
            .then(Commands.literal('equip')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('slot', SP_StringArg.word())
                        .then(Commands.argument('item', SP_StringArg.string())
                            .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                                return sp_doEquip(ctx, SP_StringArg.getString(ctx, 'tag'),
                                    SP_StringArg.getString(ctx, 'slot'), SP_StringArg.getString(ctx, 'item'));
                            }))))))
            .then(Commands.literal('ticktest')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doTickTest(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('curioreset')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doCurioReset(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('curio')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doCurio(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('obtain')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('item', SP_StringArg.string())
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doObtain(ctx, SP_StringArg.getString(ctx, 'tag'), SP_StringArg.getString(ctx, 'item'));
                        })))))
            .then(Commands.literal('pool')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doPool(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('promo')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('win', SP_IntArg.integer(0, 24000))
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doPromo(ctx, SP_StringArg.getString(ctx, 'tag'), SP_IntArg.getInteger(ctx, 'win'));
                        })))))
            .then(Commands.literal('roll')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('n', SP_IntArg.integer(1, 500))
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doRoll(ctx, SP_StringArg.getString(ctx, 'tag'), SP_IntArg.getInteger(ctx, 'n'));
                        })))))
            .then(Commands.literal('vplan')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doVPlan(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('villager')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('level', SP_IntArg.integer(1, 5))
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doVillager(ctx, SP_StringArg.getString(ctx, 'tag'), SP_IntArg.getInteger(ctx, 'level'));
                        })))))
            .then(Commands.literal('vtrade')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('level', SP_IntArg.integer(1, 5))
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doVTrade(ctx, SP_StringArg.getString(ctx, 'tag'), SP_IntArg.getInteger(ctx, 'level'));
                        })))))
            .then(Commands.literal('vclose')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doVClose(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cblock')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doChipBlock(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cpool')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doChipPool(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cstate')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doChipState(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('copen')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doChipOpen0(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('copenkeep')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doChipOpen1(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cmenu')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => cp_guardMenu(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doChipMenu(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('crefresh')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => cp_guardMenu(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doChipRefresh(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cdeny')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doChipDeny(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cstable')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doChipStable(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cseq')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doChipSeq(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cregen')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('mode', SP_StringArg.word())
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doChipRegen(ctx, SP_StringArg.getString(ctx, 'tag'), SP_StringArg.getString(ctx, 'mode'));
                        })))))
            .then(Commands.literal('cinv')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('mode', SP_StringArg.word())
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doChipInv(ctx, SP_StringArg.getString(ctx, 'tag'), SP_StringArg.getString(ctx, 'mode'));
                        })))))
            .then(Commands.literal('cbuy')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('index', SP_IntArg.integer(-1, 3))
                        .executes(ctx => cp_guardMenu(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doChipBuy(ctx, SP_StringArg.getString(ctx, 'tag'), SP_IntArg.getInteger(ctx, 'index'));
                        })))))
            .then(Commands.literal('cprice')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('n', SP_IntArg.integer(0, 4096))
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return sp_doChipPrice(ctx, SP_StringArg.getString(ctx, 'tag'), SP_IntArg.getInteger(ctx, 'n'));
                        })))))
            .then(Commands.literal('creset')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doChipReset(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            // ── 2026-10-14 CHIP-ECON 批(筹码经济调整) ──
            .then(Commands.literal('crecipes')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doEconRecipes(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cblank')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doEconBlank(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('ccardstack')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doEconCards(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cmimi')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doEconMimi(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('cloot')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return sp_doEconLoot(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            // ── 2026-10-14 .116/.117 批(抽奖盒售卖机 / 奇怪的问号 / 满包落点) ──
            .then(Commands.literal('evblock')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return ev_doBlock(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('evuse')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return ev_doUse(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('evcd')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .then(Commands.argument('mode', SP_StringArg.word())
                        .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                            return ev_doCd(ctx, SP_StringArg.getString(ctx, 'tag'), SP_StringArg.getString(ctx, 'mode'));
                        })))))
            .then(Commands.literal('evticket')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return ev_doTicket(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('evpool')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return ev_doPool(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('estack')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return ev_doStacks(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))
            .then(Commands.literal('edrop')
                .then(Commands.argument('tag', SP_StringArg.word())
                    .executes(ctx => sp_guard(ctx, SP_StringArg.getString(ctx, 'tag'), function () {
                        return ev_doDrop(ctx, SP_StringArg.getString(ctx, 'tag'));
                    }))))

    );
})