// ════════════════════════════════════════════════════════════════════════════
//  astral_shop_client.js —— 卡牌商店 GUI 的**客户端驱动 + 取证**探针（forge-1.20.1 版 = 任务K 移植自 1.21.1 任务G）
//  2026-10-09 新增（任务G 实机冒烟）。
//
//  【为什么必须是客户端脚本】
//    · 服务端探针 /astralshop 靠聊天注入驱动，而**界面开着时 T 键被 GUI 吞掉**
//      ⇒ 开店之后的任何命令都到不了服务端（SHOP-GUI 首跑实测教训）。
//    · mt_inject.ps1 的真鼠标注入只能打**窗口中心**（无坐标参数）⇒ 打不到卡框/转账按钮。
//    · 于是：先在服务端用 /astralshop openkeep 把界面**留在屏幕上**，再由本脚本
//      调 CardShopScreen#mouseClicked/mouseReleased 传**绝对 GUI 坐标**（leftPos+相对值）
//      来驱动真代码路径（起拖→落点→买；进转账面板→选行→选金额→确认）。
//
//  【只读性】不改任何游戏状态；副作用只有 console.info（→ logs/kubejs/client.log）。
//  除「关闭界面」外不做任何非 UI 调用；所有动作都走界面自己的公开事件入口。
//
//  【坐标口径】isHovering 内部加 leftPos/topPos ⇒ 传入绝对坐标 = getGuiLeft()/getGuiTop() + 相对值。
//    · 卡框0          = (29,58)          相对 (7+22, 28+30)
//    · 快捷栏第0格落点 = (125,168)        相对 (117+18*0+9, 160+8)
//    · 转账按钮        = (64,125)         相对 (48+16, 109+16)
//    · 返回键          = (183,34)         相对 (81+94+8, 26+0+8)
//    · 候选行r         = (121,59+18r)     相对 (81+2+38, 26+24+9+18r)
//    · 金额档i         = (187,93+15i)     相对 (81+94+12, 26+60+15i+7)
//    · 确认键          = (191,61)         相对 (81+94+16, 26+24+11)
// ════════════════════════════════════════════════════════════════════════════

// ════════════════════════════════════════════════════════════════════════════
//  【任务 CHIP 移植（2026-10-13，自 1.21.1 蓝本 astral_shop_client.js 筹码段）】
//   筹码商店（ChipShopScreen / ChipShopClientState / ChipShopMenu）与卡牌商店**不同屏不同态**，
//   故独立成一套；三个**余额哨兵** 770 / 771 / 772（与既有 777 及 MCP 手动带 [700,709] 互不重叠）：
//     · 770 = 筹码购买（选 0 号格 → 确认键 → 服务端扣款发货并关店）
//     · 771 = 刷新（次数 1→0）+ 次数 0 红闪（第二次点刷新键只红闪、不发包）
//     · 772 = 物品栏满拒绝（先由客户端发 `astralshop cinv CHIPD fill` 填满 36 格 → 选格 → 确认）
//   坐标口径（绝对坐标 = getGuiLeft()/getGuiTop() + **已含 STRIP 偏移 (34,6) 的** GUI 相对值）：
//     确认键 (57,54) / 刷新键 (227,54) / 筹码格 i (99/142/185, 45)（与 1.21.1 蓝本逐值一致）。
//   客户端只驱动界面 mouseClicked（购买/刷新包由 ChipShopScreen 内部自发），不引用任何网络类。
// ════════════════════════════════════════════════════════════════════════════
var Mc_CLASS = Java.loadClass("net.minecraft.client.Minecraft");
var ShopScreen_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.screen.CardShopScreen");
var ClientState_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.client.CardShopClientState");
var WalletState_CLASS = Java.loadClass("com.merlinkitsune.starenginelib.economy.StarCoinWalletState");
var ItemStack_CLASS = Java.loadClass("net.minecraft.world.item.ItemStack");
var Reg_CLASS = null;
try { Reg_CLASS = Java.loadClass("net.minecraft.core.registries.BuiltInRegistries"); } catch (eR0) { Reg_CLASS = null; }
var BuyPayload_CLASS = null;
var PacketDist_CLASS = null;
// 1.20.1: 无 CardShopBuyPayload / 无 NeoForge PacketDistributor;
//  购买包 = ModNetwork 的嵌套类 CardShopBuyMessage, 发送 = ModNetwork.sendToServer(Object)
var Net_CLASS = null;
try { Net_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.network.ModNetwork"); } catch (eB1) { }
try { BuyPayload_CLASS = Net_CLASS.CardShopBuyMessage; } catch (eB1b) { BuyPayload_CLASS = null; }
var JeiInternal_CLASS = null;
var JeiCategory_CLASS = null;
try { JeiInternal_CLASS = Java.loadClass("mezz.jei.common.Internal"); } catch (eJ1) { }
try { JeiCategory_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.compat.jei.CardShopCategory"); } catch (eJ2) { }
var ShopMenu_CLASS = null;
try { ShopMenu_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.shop.CardShopMenu"); } catch (eS1) { }
// 任务 CHIP 移植（2026-10-13，自 1.21.1 蓝本 astral_shop_client.js 77-81 行同口径）：
//   筹码商店屏与客户端态（可选类，取不到时筹码场景自动不生效，不影响既有场景）
var ChipShopScreen_CLASS = null;
var ChipShopClientState_CLASS = null;
try { ChipShopScreen_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.screen.ChipShopScreen"); } catch (eChipS) { }
try { ChipShopClientState_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.client.ChipShopClientState"); } catch (eChipC) { }
var csLastDst = -1;

var csTick = 0;
var csArmed = false;
var csRun = false;              // 哨兵闸门: 只有余额==777 的那次开店才跑脚本化序列
var csGate = 777;
var csJeiLast = -9999;
var csStep = 0;
var csOpens = 0;
var csSlot0Before = "?";
var csBalBefore = -1;

// 任务 CHIP 移植（2026-10-13，自 1.21.1 蓝本 114-123 行）：筹码商店三场景
//   770 = 筹码购买 / 771 = 刷新+次数 0 红闪 / 772 = 物品栏满拒绝
//   （与既有 csGate=777 及 MCP 手动带 [700,709] 互不重叠）
var csChipGate = 770;
var csChipRefreshGate = 771;
var csChipDenyGate = 772;
var csChip = false;             // 本次开屏是筹码商店屏
var csChipBuy = false;
var csChipRefresh = false;
var csChipDeny = false;
var csChipAfterLogged = false;  // 购买场景的「确认后读数」是否已落（购买成功时服务端同一拍关店 ⇒ step45 常来不及跑，由关屏分支补落）

// ── MCP 手动模式（2026-10-10 夜，用户四步任务里的「MCP 实机测试」专用）──────────────
// 触发口径：**钱包余额落在 [700,709]**（客户端 WalletState 读得到，无需文件/IO 类：KubeJS 类过滤器
//   会拒 java.io/java.nio，JsonIO 通道在 1.20.1 不可靠 ⇒ 改用余额带，最直观也最好驱动）。
// 生效时：本脚本**不再兜底关屏**（janitor 跳过）、不再跑脚本化序列，但保留 open/hold 只读机器行
//   ⇒ MCP 可以用真实鼠标/键盘驱动界面并截图。
// ⚠️ 为什么需要：openkeep 开出的界面服务端不会关，本脚本的 janitor 会在 step60(≈3s) 兜底关掉
//   （实证 2026-10-10 02:23:01 AP_CSC:evt=janitor:opens=1:step=60:how=closeContainer=EX+setScreen），
//   MCP 根本来不及操作。余额不落在该带内时行为与改动前完全一致（默认关闭，不影响既有用例）。
var csMcpManual = false, csMcpLogged = 0, csMcpLastBal = -1;
var CS_MCP_LO = 700, CS_MCP_HI = 709;

/** 每 tick 求值（余额带），状态翻转时各落一行日志（带余额，便于判通道是否生效） */
function csMcpLive(mc) {
    var bal = csNum(function () { return WalletState_CLASS.balance(); });
    csMcpLastBal = bal;
    csMcpManual = (bal >= CS_MCP_LO && bal <= CS_MCP_HI);
    if (csMcpManual && csMcpLogged !== 1) { csMcpLogged = 1; cs("evt=mcpmanual:on:tick=" + csTick + ":bal=" + bal); }
    // MCP 手动模式下每 40t 落一行**客户端实际看到的鼠标位置**（GUI 单位，0..480/0..270）：
    // 用于判定 MCP 的 move_mouse/click_screen 是否真的被 MC 收到（raw input / 坐标空间问题）。
    if (csMcpManual && (csTick % 40 === 0)) {
        var mx = -1, my = -1, sw = -1, sh = -1;
        try { mx = mc.mouseHandler.xpos(); my = mc.mouseHandler.ypos(); } catch (eM) { }
        try { sw = mc.getWindow().getGuiScaledWidth(); sh = mc.getWindow().getGuiScaledHeight(); } catch (eW) { }
        cs("evt=mcpmouse:tick=" + csTick + ":gx=" + Math.round(mx) + ":gy=" + Math.round(my) + ":gw=" + sw + ":gh=" + sh + ":scr=" + (mc.screen == null ? "null" : ("" + mc.screen).substring(0, 60)));
    }
    else if (!csMcpManual && csMcpLogged === 1) { csMcpLogged = 0; cs("evt=mcpmanual:off:tick=" + csTick + ":bal=" + bal); }
    return csMcpManual;
}

function cs(s) {
    try { console.info("AP_CSC:" + s); } catch (e) { }
}

function csNum(fn) { try { return fn(); } catch (e) { return -999; } }

function csId(stack) {
    try {
        if (stack == null || stack.isEmpty()) return "empty";
        // 1.20.1 实测:""+ItemStack 不带命名空间(只出 attack_card_large/emerald 这类裸名)
        // ⇒ 主路径改走物品注册表(与服务端探针 sp_id 同口径),正则仅作回退。
        try {
            var k = Reg_CLASS.ITEM.getKey(stack.getItem());
            if (k != null) {
                var ks = "" + k;
                if (ks !== "" && ks !== "null" && ks !== "minecraft:air") return ks;
            }
        } catch (eReg) { }
        var n = "" + stack.getItem();
        var m = /(?:^|[.\s])([A-Za-z0-9_$]+)@[0-9a-fA-F]+/.exec(n);
        var raw = (m != null) ? m[1] : n;
        var mm = /([a-z0-9_]+:[a-z0-9_\/]+)/.exec("" + stack);
        return (mm != null) ? mm[1] : raw;
    } catch (e) { return "<ex>"; }
}

function csCnt(stack) { try { return stack.getCount(); } catch (e) { return -1; } }

/** 商店界面状态一行（货架/余额/会话位/冷却/候选名单） */
function csState(mc) {
    var scr = mc.screen;
    var gx = csNum(function () { return scr.getGuiLeft(); });
    var gy = csNum(function () { return scr.getGuiTop(); });
    var bal = csNum(function () { return WalletState_CLASS.balance(); });
    var menu = null;
    try { menu = scr.getMenu(); } catch (e) { menu = null; }
    var shelf = "";
    var used = -1;
    var cd = -1;
    if (menu != null) {
        for (var i = 0; i < 3; i++) {
            var id = "<ex>"; var pr = -1; var sold = -1;
            try { id = csId(menu.getSlot(i).getItem()); } catch (e1) { id = "<ex>"; }
            try { pr = menu.priceOf(i); } catch (e2) { pr = -1; }
            try { sold = menu.isSold(i) ? 1 : 0; } catch (e3) { sold = -1; }
            shelf += (i > 0 ? "," : "") + i + "=" + id + "@" + pr + (sold === 1 ? "(sold)" : "");
        }
        used = csNum(function () { return menu.isTransferUsedThisSession() ? 1 : 0; });
        cd = csNum(function () { return menu.transferCooldownSeconds(); });
    }
    var cands = csNum(function () { return ClientState_CLASS.transferCandidates().size(); });
    var cand0 = "-";
    // 1.20.1 实测:list.get(0) 抛异常,而 list.size() 正常 —— 候选表来自 CardShopClientState 的
    // List.copyOf(entries)(1.20.1 产品侧 CardShopClientState.java:30),单元素时运行时类是包私有的
    // java.util.ImmutableCollections$List12 ⇒ Rhino 看不见 .get()/.iterator()(与 Curios LazyOptional.get() 同类)。
    // 分层:直接成员 → 反射 List#get(经接口解析,避开包私有类)→ Java.from → 整表 toString 正则。
    try {
        var list = ClientState_CLASS.transferCandidates();
        var e0 = null;
        try { if (list.size() > 0) e0 = list.get(0); } catch (eA) { }
        if (e0 == null) {
            try {
                var iface = list.getClass().getInterfaces()[0];
                var mGet = iface.getMethod('get', Java.type('java.lang.Integer').TYPE);
                if (list.size() > 0) e0 = mGet.invoke(list, 0);
            } catch (eR) { }
        }
        if (e0 == null) { try { var arr = Java.from(list); if (arr.length > 0) e0 = arr[0]; } catch (eB) { } }
        if (e0 != null) {
            var nm = null; var bl = null;
            try { nm = "" + e0.name(); } catch (eC) { }
            try { bl = "" + e0.balance(); } catch (eD) { }
            if (nm != null && bl != null) cand0 = nm + "/" + bl;
            else {
                var ts = "" + e0;
                var mn = /name=([^,\]]+)/.exec(ts);
                var mb = /balance=([0-9-]+)/.exec(ts);
                if (mn != null) cand0 = "" + mn[1] + "/" + (mb != null ? mb[1] : "?");
                else cand0 = "<ex:" + ts.replace(/[\r\n]+/g, " ").substring(0, 60) + ">";
            }
        } else {
            // 兜底:整表 toString(AbstractCollection → Entry.toString(),不经 Rhino 成员解析)
            var lts = "" + list;
            var mn2 = /name=([^,\]]+)/.exec(lts);
            var mb2 = /balance=([0-9-]+)/.exec(lts);
            if (mn2 != null) cand0 = "" + mn2[1] + "/" + (mb2 != null ? mb2[1] : "?");
            else cand0 = "<none:" + lts.replace(/[\r\n]+/g, " ").substring(0, 60) + ">";
        }
    } catch (e4) { cand0 = "<ex>:" + ("" + e4).substring(0, 60); }
    var inv0 = "<ex>";
    try { inv0 = csId(mc.player.getInventory().getItem(0)) + "x" + csCnt(mc.player.getInventory().getItem(0)); } catch (e5) { }
    var inv1 = "<ex>";
    try { inv1 = csId(mc.player.getInventory().getItem(1)) + "x" + csCnt(mc.player.getInventory().getItem(1)); } catch (e6) { }
    var scrCls = (mc.screen == null) ? "null" : ("" + mc.screen);
    return "gx=" + gx + ":gy=" + gy + ":bal=" + bal + ":used=" + used + ":cd=" + cd
        + ":shelf=[" + shelf + "]:cands=" + cands + ":cand0=" + cand0
        + ":inv0=" + inv0 + ":inv1=" + inv1 + ":scr=" + scrCls;
}

/** 点一下（绝对坐标 = gx+dx, gy+dy）；返回 mouseClicked 的布尔。每一步都落一行日志 */
function csClick(mc, dx, dy, what, step) {
    var scr = mc.screen;
    var gx = csNum(function () { return scr.getGuiLeft(); });
    var gy = csNum(function () { return scr.getGuiTop(); });
    var ret = "?";
    try { ret = scr.mouseClicked(gx + dx, gy + dy, 0) ? 1 : 0; }
    catch (e) { ret = "EX:" + e; }
    cs("evt=click:step=" + step + ":what=" + what + ":abs=" + (gx + dx) + "," + (gy + dy) + ":ret=" + ret);
    return ret;
}

function csRelease(mc, dx, dy, what, step) {
    var scr = mc.screen;
    var gx = csNum(function () { return scr.getGuiLeft(); });
    var gy = csNum(function () { return scr.getGuiTop(); });
    var ret = "?";
    try { ret = scr.mouseReleased(gx + dx, gy + dy, 0) ? 1 : 0; }
    catch (e) { ret = "EX:" + e; }
    cs("evt=release:step=" + step + ":what=" + what + ":abs=" + (gx + dx) + "," + (gy + dy) + ":ret=" + ret);
    return ret;
}
/** 找一个空的玩家物品栏槽(服务端菜单里 Slot.index/坐标), 返回 [slotIdx, gx+中心x, gy+中心y] 或 null=没找到 */
function csEmptyInvSlot(mc) {
    var out = null;
    try {
        var m = mc.screen.getMenu();
        var n = 0;
        try { n = m.slots.size(); } catch (e0) { try { n = m.slots.length; } catch (e1) { n = 0; } }
        for (var i = 0; i < n; i++) {
            try {
                var s = m.slots.get(i);
                var st = s.getItem();
                if (st == null || !st.isEmpty()) { continue; }
                var ccl = "" + s.container;
                if (ccl.indexOf("Inventory") < 0 && ccl.indexOf("Player") < 0) { continue; }
                var idx = -1;
                try { idx = s.index; } catch (e2) { idx = -1; }
                if (idx < 0) { continue; }
                var sx = csNum(function () { return s.x; });
                var sy = csNum(function () { return s.y; });
                if (sx < 0 || sy < 0) { continue; }
                var gx = csNum(function () { return mc.screen.getGuiLeft(); });
                var gy = csNum(function () { return mc.screen.getGuiTop(); });
                out = [idx, gx + sx + 8, gy + sy + 8];
                break;
            } catch (e3) { }
        }
    } catch (e) { out = null; }
    return out;
}

/** 按 CardShopMenu/Screen 的官方网格常量找一个**空的玩家物品栏格**; 返回 [invIndex, relX, relY] 或 null。
 *  返回值是**绝对屏幕坐标**(鼠标事件用)。口径与 CardShopScreen#inventoryCellIndexAt 同源: 主物品栏 3 行 (inv 9..35) + 快捷栏 1 行 (inv 0..8);
 *  槽序号 = SHELF_SIZE + CardShopMenu.playerSlotOffset(inv)。避开 getClass/container 等白名单外方法。 */
function csEmptyInvCell(mc) {
    var IX = 117, IY = 102, IHY = 160, PITCH = 18, CELL = 16;
    var m = null;
    try { m = mc.screen.getMenu(); } catch (e0) { return null; }
    if (m == null) { return null; }
    var shelf = 3;
    try { shelf = ShopMenu_CLASS.SHELF_SIZE; } catch (e1) { shelf = 3; }
    var gx = 0, gy = 0;
    try { gx = csNum(function () { return mc.screen.getGuiLeft(); }); } catch (eG1) { gx = 0; }
    try { gy = csNum(function () { return mc.screen.getGuiTop(); }); } catch (eG2) { gy = 0; }
    for (var row = 0; row < 4; row++) {
        for (var col = 0; col < 9; col++) {
            var inv = (row === 3) ? col : (9 + row * 9 + col);
            var slotIdx = -1;
            try { slotIdx = shelf + ShopMenu_CLASS.playerSlotOffset(inv); } catch (e2) { continue; }
            var empty = false;
            try { var st = m.slots.get(slotIdx).getItem(); empty = (st == null) || st.isEmpty(); } catch (e3) { continue; }
            if (!empty) { continue; }
            var cellY = (row === 3) ? IHY : (IY + row * PITCH);
            var cellX = IX + col * PITCH;
            return [inv, gx + cellX + 8, gy + cellY + 8];
        }
    }
    return null;
}
/** 读客户端物品栏第 i 格 (0..8 快捷栏 / 9..35 主物品栏) */
function csInvItem(mc, i) {
    try { if (i < 0) { return "<na>"; } var st = mc.player.getInventory().getItem(i); return csId(st) + "x" + csCnt(st); } catch (e) { return "<ex>"; }
}

/** 真·拖拽: 按下卡框后先把鼠标拖到空的物品栏槽(this = 幽灵跟手), 再松手 → 屏内 mouseReleased 发 CardShopBuyPayload */
function csDragSlot(mc, what, step) {
    var t = csEmptyInvCell(mc);
    var ret = "?";
    if (t == null) { cs("evt=drag:step=" + step + ":what=" + what + ":target=none"); return; }
    try {
        var scr = mc.screen;
        ret = scr.mouseDragged(t[1], t[2], 0, t[1] - 0, t[2] - 0) ? 1 : 0;
    } catch (e) { ret = "EX:" + e; }
    cs("evt=drag:step=" + step + ":what=" + what + ":target=inv" + t[0] + ":abs=" + t[1] + "," + t[2] + ":ret=" + ret);
}

/** 在**计算出的空物品栏槽**上松手(而不是硬编码坐标) */
function csReleaseSlot(mc, what, step) {
    var t = csEmptyInvCell(mc);
    csLastDst = (t == null) ? -1 : t[0];
    var ret = "?";
    var abs = "none";
    try {
        var scr = mc.screen;
        var gx = csNum(function () { return scr.getGuiLeft(); });
        var gy = csNum(function () { return scr.getGuiTop(); });
        var px = (t == null) ? (gx + 125) : t[1];
        var py = (t == null) ? (gy + 168) : t[2];
        abs = px + "," + py;
        ret = scr.mouseReleased(px, py, 0) ? 1 : 0;
    } catch (e) { ret = "EX:" + e; }
    cs("evt=release:step=" + step + ":what=" + what + ":target=" + ((t == null) ? "fallback" : ("inv" + t[0])) + ":abs=" + abs + ":ret=" + ret);
    return ret;
}

/** 拖拽落点诊断: 客户端菜单 slot3(主物品栏首格) 与玩家背包 inv9 的实际内容 + 屏内 dragIndex 可读性 */
function csDropDiag(mc, phase) {
    var out = [];
    function t(n, fn) { try { var v = fn(); out.push(n + "=" + ((v === null || v === undefined) ? "null" : ("" + v).replace(/[\r\n]+/g, " ").substring(0, 40))); } catch (e) { out.push(n + "=EX:" + ("" + e).substring(0, 40)); } }
    t("slot3", function () { return csId(mc.screen.getMenu().getSlot(3).getItem()); });
    t("inv9", function () { return csId(mc.player.getInventory().getItem(9)); });
    t("dragIndex", function () { return mc.screen.dragIndex; });
    t("bal", function () { return WalletState_CLASS.balance(); });
    cs("evt=dropdiag:" + phase + ":" + out.join(":"));
}

/** 二分取证: 绕开屏幕拖拽, 直接从客户端发一次购买载荷(判定失败点在拖拽计算还是载荷/handleBuy 链路) */
function csDirectBuy(mc, shelf, slotL, step) {
    var ret = "?";
    try {
        if (BuyPayload_CLASS == null || Net_CLASS == null) { ret = "cls=null"; }
        else { Net_CLASS.sendToServer(new BuyPayload_CLASS(shelf, slotL)); ret = "sent"; }
    } catch (e) { ret = "EX:" + e; }
    cs("evt=bissect:step=" + step + ":how=directBuyPayload:shelf=" + shelf + ":slot=" + slotL + ":ret=" + ret);
}

/** JEI 运行时取证: 类别是否注册 + 记录数 + 是否混入排除项 */
function csJei() {
    if (JeiInternal_CLASS == null || JeiCategory_CLASS == null) { cs("evt=jei:cls=null"); return; }
    var rt = null;
    try { rt = JeiInternal_CLASS.getJeiRuntime(); } catch (e0) { cs("evt=jei:rt=EX:" + e0); return; }
    if (rt == null) { cs("evt=jei:rt=null"); return; }
    var rm = null;
    try { rm = rt.getRecipeManager(); } catch (e1) { cs("evt=jei:rm=EX:" + e1); return; }
    var type = JeiCategory_CLASS.RECIPE_TYPE;
    var uid = "?";
    try { uid = "" + type.getUid(); } catch (e2) { uid = "EX:" + e2; }
    var cat = "none";
    try { cat = (rm.getRecipeCategory(type) == null) ? "null" : "ok"; } catch (e3) { cat = "EX:" + e3; }
    var n = -1;
    var ids = "none";
    var bad = "none";
    var EXCL = ["astral_dice:effect_card_living_page", "astral_dice:effect_card_fate_guidance", "astral_dice:fu_card", "astral_dice:huo_card", "astral_dice:attack_card_bite", "astral_dice:attack_card_dragon_roar", "astral_dice:attack_card_full_power"];
    try {
        var list = rm.createRecipeLookup(type).get().toList();
        n = list.size();
        var arr = [];
        var badArr = [];
        for (var i = 0; i < n; i++) {
            var cid = "?";
            try { cid = csId(new ItemStack_CLASS(list.get(i).card())); } catch (e4) { cid = "<ex>"; }
            arr.push(cid);
            for (var j = 0; j < EXCL.length; j++) { if (EXCL[j] === cid) { badArr.push(cid); } }
        }
        ids = arr.join("|");
        bad = (badArr.length === 0) ? "none" : badArr.join("|");
    } catch (e5) { ids = "EX:" + e5; }
    cs("evt=jei:cat=" + cat + ":uid=" + uid + ":recipes=" + n + ":excluded=" + bad + ":ids=" + ids);
}

/** 关掉当前屏幕(客户端侧): closeContainer 会发关闭包给服务端(正确路径), setScreen(null) 是兜底。 */
function csClose(mc) {
    var how = [];
    try { mc.player.closeContainer(); how.push('closeContainer'); } catch (e) { how.push('closeContainer=EX'); }
    try { mc.setScreen(null); how.push('setScreen'); } catch (e2) { how.push('setScreen=EX'); }
    return how.join('+');
}

/** 客户端可见性一次性体检(tick 60 跑一次;此时未开任何容器,两处调用都是无害空操作)。 */
function csVis(mc) {
    var out = [];
    function t(n, fn) { try { var v = fn(); out.push(n + '=' + ((v === null || v === undefined) ? 'null' : ('' + v).replace(/[\r\n]+/g, ' ').substring(0, 56))); } catch (e) { out.push(n + '=EX:' + ('' + e).substring(0, 56)); } }
    t('mc', function () { return av1(mc); });
    // 2026-10-10 复核 I-1：这两项原先**无条件改游戏状态**（setScreen(null)/closeContainer），已改为纯读。
    t('screen', function () { return mc.screen == null ? 'noscreen' : ('' + mc.screen); });
    t('p_menu', function () { return '' + mc.player.containerMenu; });
    t('p_getInv', function () { return csId(mc.player.getInventory().getItem(0)); });
    t('p_containerMenu', function () { return '' + mc.player.containerMenu; });
    t('gameMode', function () { return mc.gameMode == null ? 'null' : 'ok'; });
    t('jeiRt', function () { return (JeiInternal_CLASS == null) ? 'clsnull' : ((JeiInternal_CLASS.getJeiRuntime() == null) ? 'null' : 'ok'); });
    t('shopCls', function () { return ShopScreen_CLASS == null ? 'null' : 'ok'; });
    t('getGuiLeft', function () { return 'M=' + (mc.screen == null ? 'noscreen' : 'has'); });
    cs('evt=cvis:' + out.join('|'));
}
function av1(mc) { try { return '' + mc; } catch (e) { return 'ex'; } }

/** opens==1 的脚本化序列（步号 = tick 计数，给服务端往返留时间） */
function csRunFirst(mc, step) {
    if (step === 4) {
        cs("evt=stateA:" + csState(mc));
        csSlot0Before = "<read>";
        csBalBefore = csNum(function () { return WalletState_CLASS.balance(); });
    } else if (step === 8) {
        // ⚠️ 必须在**同一 tick** 内 click→drag→release: CardShopScreen#containerTick(:364-368) 有「物理左键未按下 ⇒ dragIndex 复位」兜底,
        // 跨 tick 的合成手势会被它清掉(实测 v15: ret=1 但从不发载荷)。同 tick 时序与真人「按下-拖动-松开」经过的屏内代码完全一致。
        csDropDiag(mc, "before");                       // 起拖前: 空槽/屏内状态诊断
        csClick(mc, 29, 58, "card0_frame", step);       // 起拖第 1 个卡框(dragIndex=0)
        csDragSlot(mc, "invEmpty", step);               // 幽灵跟手到空物品栏槽
        csReleaseSlot(mc, "invEmpty", step);            // 同一 tick 松手 ⇒ 屏内发 CardShopBuyPayload
        csDropDiag(mc, "after");
    } else if (step === 10) {
        cs("evt=stateG_onetickdrag:" + csState(mc) + ":inv9=" + csInvItem(mc, 9));  // 同 tick 手势后立刻取状态
    } else if (step === 12) {
        cs("evt=stateA1_afterdrag:" + csState(mc));     // 拖拽后立刻取状态(判定拖拽是否结算)
    } else if (step === 14) {
        csDirectBuy(mc, 1, 1, step);                    // 直接发载荷: 货架1 -> 快捷栏1
    } else if (step === 18) {
        cs("evt=stateA2_afterdirect:" + csState(mc));   // 直接载荷后取状态
    } else if (step === 20) {
        cs("evt=stateB_afterbuy:" + csState(mc) + ":dst=" + csLastDst + ":dstItem=" + csInvItem(mc, csLastDst));
    } else if (step === 22) {
        // 货架0 已由同 tick 手势买走 ⇒ 这里不再发替代载荷, 只留观察点(证明手势结算落到了主物品栏首格)
        cs("evt=stateF_afterdrag:" + csState(mc) + ":inv9=" + csInvItem(mc, 9));
    } else if (step === 26) {
        csClick(mc, 64, 125, "transfer_btn", step);        // 进二级转账面板（服务端回发候选名单）
    } else if (step === 36) {
        cs("evt=stateC_panel:" + csState(mc));
    } else if (step === 40) {
        csClick(mc, 121, 59, "row0", step);                // 选中第 1 行候选
    } else if (step === 44) {
        csClick(mc, 187, 108, "limit10", step);            // 金额档 LIMITS[1]=10
    } else if (step === 48) {
        csClick(mc, 191, 61, "confirm", step);             // 确认转账
    } else if (step === 62) {
        cs("evt=stateD_afterxfer:" + csState(mc));
    } else if (step === 66) {
        csClick(mc, 183, 34, "back_btn", step);            // 若有面板则退回主视图（主视图点此处无副作用）
    } else if (step === 70) {
        csClick(mc, 64, 125, "transfer_btn_again", step);  // 会话已用 ⇒ 不应进面板
    } else if (step === 76) {
        csClick(mc, 191, 61, "confirm_again", step);       // 不应产生第二次转账
    } else if (step === 86) {
        cs("evt=stateE_repeat:" + csState(mc));
    } else if (step === 90) {
        cs("evt=leaving:how=" + csClose(mc));              // 离开商店 ⇒ 服务端 removed() 顺延冷却
    }
}

/** 外来容器屏兜底关闭: 服务端探针若开了村民交易界面而没关, 之后所有注入都会被屏吞掉。
 *  只认 MerchantScreen(绝不能关 ChatScreen —— 聊天注入全靠它)。 */
function csForeignJanitor(mc) {
    // ⚠️ 护栏（2026-10-10 独立复核 I-1）：探针**默认惰性** —— 未武装（非 MCP 手动模式）时绝不关任何屏幕。
    //    本函数历史上被 tick 无条件调用，会替玩家关掉村民交易界面（与 1.21.1 侧 csLive 护栏同义）。
    if (!csMcpLive(mc)) { return; }
    try {
        var s = mc.screen;
        if (s == null) return;
        var n = "" + s;
        if (n.indexOf("MerchantScreen") < 0) return;
        cs("evt=foreign:cls=MerchantScreen:how=" + csClose(mc));
    } catch (e) { cs("evt=foreign:ex=" + e); }
}

// ══════════════════ 任务 CHIP 移植（2026-10-13，自 1.21.1 蓝本 astral_shop_client.js 筹码段）══════════════════
//  背景：筹码商店（ChipShopScreen / ChipShopClientState / ChipShopMenu）与卡牌商店**不同屏不同态**，
//  故独立成一套；哨兵只用 770/771/772（避开既有 777 与 MCP 手动带 [700,709]）。
//  坐标口径（⚠️ 与卡牌屏同源，但筹码屏多一层「条带 STRIP 偏移」）：
//    ChipShopScreen 的 CELL_/LABEL_/CONFIRM_/REFRESH_ 常量是**条带内相对**坐标，命中判定走
//    isHoveringStrip → stripX/stripY（再叠加 STRIP_X=34 / STRIP_Y=6 与 leftPos/topPos）
//    ⇒ 探针必须把 STRIP 偏移一并算进去，否则点不中（1.21.1 首跑实测：sel 恒 -1、确认键空转但返回 true）。
//    换算依据（forge 版 ChipShopScreen 常量与 1.21.1 **逐值相同**，已开包取证：
//      STRIP_X=34 / STRIP_Y=6 / CELL_X={3,45,88,131,174} / CELL_W=40 / CELL_H=STRIP_H=78 / CELL_Y=0 /
//      CONFIRM_X=11 / CONFIRM_Y=36 / REFRESH_X=181 / REFRESH_Y=36 / BUTTON_SIZE=24）：
//      确认键中心 = (STRIP_X 34 + CONFIRM_X 11 + 12, STRIP_Y 6 + CONFIRM_Y 36 + 12) = (57,54)
//      刷新键中心 = (34 + REFRESH_X 181 + 12, 6 + 36 + 12)                          = (227,54)
//      筹码格 i 中心 = (34 + CELL_X[i+1] + CELL_W/2 20, 6 + CELL_Y 0 + CELL_H/2 39)  = (99,45)/(142,45)/(185,45)
//    ⚠️ 传给 csClick 的就是**已含 STRIP 偏移的 GUI 相对值**（如 99,45），csClick 内部再加 getGuiLeft/getGuiTop
//      ⇒ 落到 absolute = leftPos+99, topPos+45，命中 isHoveringStrip(CELL_X[1],0,40,78) 的绝对框。**勿重复加偏移**。
//  客户端只驱动界面 mouseClicked（购买/刷新包由 ChipShopScreen#onConfirmPressed / #onRefreshPressed 内部自发），
//    不引用任何网络类（1.20.1 无 NeoForge PacketDistributor，且本场景根本不需要）。

/** 类名：Rhino 里 `obj.getClass()` 不可用（TypeError: Cannot find function getClass）⇒ 用 toString 的 "全类名@hash" 解析（自 1.21.1 蓝本原样移植） */
function csClsName(o) {
    try {
        if (o == null) { return "null"; }
        var s = "" + o;
        var m = /([A-Za-z_$][A-Za-z0-9_$.]*)@[0-9a-fA-F]+/.exec(s);
        if (m != null) { var full = m[1]; return full.substring(full.lastIndexOf(".") + 1); }
        return s.substring(0, 60);
    } catch (e) { return "?"; }
}

/** 客户端侧发命令（GUI 开着时 T 键被吞，只能用这条通道；⚠️ 不要带前导斜杠）。自 1.21.1 蓝本原样移植 */
function csCmd(mc, cmd, what, step) {
    var ret = "?";
    try { mc.player.connection.sendCommand(cmd); ret = "sent"; }
    catch (e) { ret = "EX:" + ("" + e).substring(0, 90); }
    cs("evt=cmd:step=" + step + ":what=" + what + ":cmd=" + cmd + ":ret=" + ret);
}

/** 当前屏是不是筹码商店屏（类取不到时恒 0） */
function csChipScreen(mc) {
    try {
        if (mc.screen == null || ChipShopScreen_CLASS == null) { return 0; }
        return (mc.screen instanceof ChipShopScreen_CLASS) ? 1 : 0;
    } catch (e) { return -1; }
}

/** 筹码屏客户端态单行（全部走 ChipShopClientState 的公开只读接口；键序与 1.21.1 蓝本逐字节一致） */
function csChipState(mc) {
    var sel = csNum(function () { return ChipShopClientState_CLASS.selectedIndex(); });
    var cost = csNum(function () { return ChipShopClientState_CLASS.cost(); });
    var rl = csNum(function () { return ChipShopClientState_CLASS.remainingRefreshes(); });
    var rt = csNum(function () { return ChipShopClientState_CLASS.totalRefreshes(); });
    var deny = csNum(function () { return ChipShopClientState_CLASS.denyReason(); });
    var ticks = csNum(function () { return ChipShopClientState_CLASS.denyTicks(); });
    var red = -1;
    try { red = ChipShopClientState_CLASS.denyBlinkRed() ? 1 : 0; } catch (eR) { red = -1; }
    var offers = "";
    try {
        var n = ChipShopClientState_CLASS.offerCount();
        for (var i = 0; i < n; i++) {
            var st = ChipShopClientState_CLASS.offer(i);
            var oid = (st == null || st.isEmpty()) ? "-" : csId(st);
            var q = csNum(function () { return ChipShopClientState_CLASS.qualityCode(i); });
            offers += (i > 0 ? "," : "") + i + "=" + oid + "@t" + q;
        }
    } catch (eO) { offers = "<ex>"; }
    var scr = (mc.screen == null) ? "null" : csClsName(mc.screen);
    return "sel=" + sel + ":cost=" + cost + ":refreshes=" + rl + "/" + rt + ":deny=" + deny
        + ":dticks=" + ticks + ":red=" + red + ":offers=[" + offers + "]:scr=" + scr;
}

/** 770 = 筹码购买：选 0 号格 → 点确认键 → 服务端扣款发货并关店（关店后由 not-in-shop 分支落 evt=closed） */
function csRunChipBuy(mc, step) {
    if (step === 1) { cs("evt=chip_buy:open:bal=" + csNum(function () { return WalletState_CLASS.balance(); }) + ":" + csChipState(mc)); return; }
    if (step === 20) { csClick(mc, 99, 45, "chip_cell0", step); return; }
    if (step === 25) { cs("evt=chip_buy:selected:" + csChipState(mc)); return; }
    if (step === 40) { csClick(mc, 57, 54, "chip_confirm", step); return; }
    if (step === 45) { csChipAfterLogged = true; cs("evt=chip_buy:after_confirm:" + csChipState(mc)); return; }
    if (step === 200) { cs("evt=chip_buy:still_open:" + csChipState(mc)); return; }
    return;
}

/** 771 = 刷新：次数 1 → 点刷新键（真发包，服务端 1→0）→ 再点一次（次数 0 ⇒ 只红闪、不发包） */
function csRunChipRefresh(mc, step) {
    if (step === 1) { cs("evt=chip_ref:open:bal=" + csNum(function () { return WalletState_CLASS.balance(); }) + ":" + csChipState(mc)); return; }
    if (step === 20) { csClick(mc, 227, 54, "chip_refresh", step); return; }
    if (step === 30) { cs("evt=chip_ref:after_refresh:" + csChipState(mc)); return; }
    if (step === 40) { csClick(mc, 227, 54, "chip_refresh_zero", step); return; }
    if (step >= 43 && step <= 85 && step % 3 === 0) { cs("evt=chip_ref:blink:step=" + step + ":" + csChipState(mc)); return; }
    if (step === 100) { cs("evt=chip_ref:end:" + csChipState(mc)); csClose(mc); return; }
    return;
}

/** 772 = 物品栏满拒绝：屏内先由客户端发 `astralshop cinv CHIPD fill` 填满 36 格（GUI 开着时 T 键被吞，只能走 csCmd），
 *  再选 0 号格 → 点确认 → 服务端 findEmptyInventorySlot<0 ⇒ DENY_INVENTORY_FULL(2) 经 S2C 包回客户端（红闪 30t、不扣款、不重抽）。
 *  ⚠️ 不能「先填满物品栏再开屏」：ChipShopOpener.open 自己就会以 inventory_full 拒绝开店，屏根本不会出现。 */
function csRunChipDeny(mc, step) {
    if (step === 1) { cs("evt=chip_deny:open:bal=" + csNum(function () { return WalletState_CLASS.balance(); }) + ":" + csChipState(mc)); return; }
    if (step === 8) { csCmd(mc, 'astralshop cinv CHIPD fill', "chip_deny_fill", step); return; }
    if (step === 20) { csClick(mc, 99, 45, "chip_cell0", step); return; }
    if (step === 25) { cs("evt=chip_deny:selected:" + csChipState(mc)); return; }
    if (step === 40) { csClick(mc, 57, 54, "chip_confirm_full", step); return; }
    if (step === 45) { cs("evt=chip_deny:after_confirm:" + csChipState(mc)); return; }
    if (step >= 48 && step <= 90 && step % 3 === 0) { cs("evt=chip_deny:blink:step=" + step + ":" + csChipState(mc)); return; }
    if (step === 110) { cs("evt=chip_deny:end:" + csChipState(mc)); csClose(mc); return; }
    return;
}

ClientEvents.tick(event => {
    csTick++;
    var mc = null;
    try { mc = Mc_CLASS.getInstance(); } catch (e0) { return; }
    if (mc == null || mc.level == null) return;
    var mcpManual = csMcpLive(mc);   // MCP 手动模式（默认关）：开了就只做只读诊断，绝不关屏/跑场景
    // 2026-10-10 复核 I-1：**不再自动关任何屏幕**（探针必须惰性；外来屏只在显式场景步骤里由场景自己收掉）。
    // if (!mcpManual) { csForeignJanitor(mc); }
    if (csTick === 40) { cs("evt=boot:tick=" + csTick + ":shopCls=" + (ShopScreen_CLASS == null ? "null" : "ok")); }
    if ((csTick === 60 || csTick % 600 === 0) && mc.screen == null) { csVis(mc); }
    if (csTick === 200 || (csTick - csJeiLast) >= 1200) { csJeiLast = csTick; csJei(); }
    var inShop = 0;
    try { inShop = (mc.screen != null && (mc.screen instanceof ShopScreen_CLASS)) ? 1 : 0; } catch (e1) { inShop = -1; }
    // 任务 CHIP 移植（2026-10-13）：筹码商店屏也算「在店内」（否则筹码屏一开就被当关屏、场景机立刻被复位）
    var inChip = csChipScreen(mc);
    if (inShop !== 1 && inChip !== 1) {
        if (csArmed) {
            // 任务 CHIP 移植：购买成功时服务端**同一拍关店** ⇒ step45 的 after_confirm 常来不及落；
            // 客户端态（ChipShopClientState）是类级静态、不随屏销毁 ⇒ 在关屏这一拍补落同一条机器行。
            if (csChipBuy && !csChipAfterLogged && csStep >= 40) { csChipAfterLogged = true; cs("evt=chip_buy:after_confirm:" + csChipState(mc)); }
            csArmed = false; csRun = false;
            csChipBuy = false; csChipRefresh = false; csChipDeny = false; csChip = false;
            cs("evt=closed:tick=" + csTick + ":step=" + csStep + ":opens=" + csOpens);
        }
        return;
    }
    if (!csArmed) {
        csArmed = true;
        csOpens++;
        csStep = 0;
        var gbal = csNum(function () { return WalletState_CLASS.balance(); });
        csRun = (gbal === csGate);
        // 任务 CHIP 移植（2026-10-13）：筹码屏上的三个哨兵（卡牌屏永不匹配；等值匹配即可，筹码商店不产生余额增量）
        csChipBuy = (inChip === 1 && gbal === csChipGate);
        csChipRefresh = (inChip === 1 && gbal === csChipRefreshGate);
        csChipDeny = (inChip === 1 && gbal === csChipDenyGate);
        csChip = (csChipBuy || csChipRefresh || csChipDeny);
        csChipAfterLogged = false;
        cs("evt=open:tick=" + csTick + ":opens=" + csOpens + ":gate=" + gbal + ":run=" + (csRun ? 1 : 0)
            + ":chip=" + (csChip ? 1 : 0) + ":" + (csChip ? csChipState(mc) : csState(mc)));
    }
    csStep++;
    if (csChip) {
        // 任务 CHIP 移植（2026-10-13）：筹码三场景（与卡牌 csRun / janitor / hold 分支互斥）
        if (csChipBuy) { csRunChipBuy(mc, csStep); }
        else if (csChipRefresh) { csRunChipRefresh(mc, csStep); }
        else if (csChipDeny) { csRunChipDeny(mc, csStep); }
    } else if (csRun && !mcpManual) {
        csRunFirst(mc, csStep);
        if (csStep === 112) { cs("evt=leaveauto:how=" + csClose(mc)); }
    } else if (csStep === 60 && !mcpManual) {
        // 屏幕管理员: 非武装的商店界面(服务端已在下单 tick 用关闭包关掉),这里兜底再关一次
        cs("evt=janitor:opens=" + csOpens + ":step=" + csStep + ":how=" + csClose(mc));
    } else if (csStep % 40 === 0) {
        cs("evt=hold:opens=" + csOpens + ":step=" + csStep + ":" + csState(mc));
    }
});
