// ════════════════════════════════════════════════════════════════════════════
//  astral_wallet_vision.js —— 星币钱包控件的**客户端真值读数 + 自截图**探针
//  （26.1.2 专用；2026-09-22 新增，用于定位「钱包控件在物品栏界面显示异常」）
//
//  【为什么需要它】
//    钱包控件是**纯客户端即时模式绘制**（`StarCoinWalletButtons` 订阅
//    `ScreenEvent.Render.Foreground`）：服务端探针看不到、`latest.log` 里也没有任何痕迹。
//    而「截图 + 肉眼」有两个结构性缺口：
//      ① 物品栏开着时原版**不处理 F2 键绑定**（`handleKeybinds` 只在 `screen == null` 时跑）
//         ⇒ case 的 `screenshot` 若用 f2 模式会拿不到图；
//      ② 整屏抓取（window 模式）会把 WorkBuddy 窗口一起抓进来，且截到的是**窗口合成结果**，
//         无法区分「模组画的」与「原版/其它模组画的」。
//    故本探针做两件工具链做不到的事：把 `mc.screen` 的**真值**落盘（通道 `kubejs_client`），
//    并用原版 `Screenshot.grab` 直接取**游戏 framebuffer**（不含桌面）。
//
//  【读什么】
//    · `mc.screen` 的 `toString()` —— 直接给出**真实类名**（KubeJS 类过滤器禁 `java.lang.reflect`，
//      不能用 `getClass().getName()`；而 `Object.toString()` 未被 Screen 覆写 ⇒ 可用）；
//    · `instanceof InventoryScreen / CreativeModeInventoryScreen / AbstractContainerScreen`；
//    · `getLeftPos()/getTopPos()`（26.1.2 的现行访问器）与 `options.guiScale()`；
//    · `StarCoinCurrency.isWalletEnabled()` + `StarCoinWalletState.balance()`。
//    ⇒ 有了 (gl, gt, guiScale) 就能**自己算出**四个控件应有的像素矩形，与截图里的实际像素对比，
//      从而把「模组画的 / 不是模组画的」分开（这是本探针存在的主要理由）。
//
//  【自截图时序】
//    屏幕对象出现的那一拍记为 T ⇒ 排定 T+25 拍取帧（等界面稳定，且远早于用例的 2.5 s 等待）。
//
//  【只读性】
//    不改任何玩法状态；副作用只有：写 PNG 到 `run/<版本>/screenshots/` 与一行 console.info。
// ════════════════════════════════════════════════════════════════════════════

var Mc_CLASS = Java.loadClass("net.minecraft.client.Minecraft");
var Shot_CLASS = Java.loadClass("net.minecraft.client.Screenshot");
var AbsScreen_CLASS = Java.loadClass("net.minecraft.client.gui.screens.inventory.AbstractContainerScreen");
var InvScreen_CLASS = Java.loadClass("net.minecraft.client.gui.screens.inventory.InventoryScreen");
var CreativeScreen_CLASS = Java.loadClass("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen");
var Currency_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.economy.StarCoinCurrency");
var WalletState_CLASS = Java.loadClass("com.merlinkitsune.starenginelib.economy.StarCoinWalletState");

var wtTick = 0;
var wtLastScr = "__init__";
var wtDueShot = -1;
var wtBoot = 0;
var wtShotCount = 0;
var wtLastBal = "__init__";

/** 输出出口：KubeJS 客户端脚本的 console ⇒ `logs/kubejs/client.log` */
function wt(s) {
    try { console.info("AP_WALLET:" + s); } catch (e) { /* 不得因回报失败影响诊断 */ }
}

/** 屏幕真值：null / 类名（靠 Object.toString，不依赖反射） */
function wtScreen(mc) {
    try { return (mc.screen == null) ? "null" : ("" + mc.screen); } catch (e) { return "ERR:" + e; }
}

function wtBool1(mc, cls) {
    try { return (mc.screen != null && (mc.screen instanceof cls)) ? 1 : 0; } catch (e) { return -1; }
}

function wtNum(fn) {
    try { return fn(); } catch (e) { return -1; }
}

ClientEvents.tick(event => {
    wtTick++;
    var mc = null;
    try { mc = Mc_CLASS.getInstance(); } catch (e0) { return; }
    if (mc == null || mc.level == null) return;

    if (wtBoot === 0) {
        wtBoot = 1;
        wt("evt=boot:tick=" + wtTick
            + ":inv=" + (InvScreen_CLASS == null ? "null" : "ok")
            + ":creative=" + (CreativeScreen_CLASS == null ? "null" : "ok")
            + ":abs=" + (AbsScreen_CLASS == null ? "null" : "ok")
            + ":shot=" + (Shot_CLASS == null ? "null" : "ok"));
    }

    var scr = wtScreen(mc);

    // 屏幕变化沿：把真值与几何一次性落盘
    if (scr !== wtLastScr) {
        var isAbs = wtBool1(mc, AbsScreen_CLASS);
        var isInv = wtBool1(mc, InvScreen_CLASS);
        var isCre = wtBool1(mc, CreativeScreen_CLASS);
        var gl = -1, gt = -1, gs = -1, we = -1, bal = -1;
        if (mc.screen != null) {
            gl = wtNum(function () { return mc.screen.getLeftPos(); });
            gt = wtNum(function () { return mc.screen.getTopPos(); });
        }
        gs = wtNum(function () { return mc.options.guiScale().get(); });
        we = wtNum(function () { return Currency_CLASS.isWalletEnabled() ? 1 : 0; });
        bal = wtNum(function () { return WalletState_CLASS.balance(); });
        wt("evt=scr:tick=" + wtTick
            + ":class=" + scr
            + ":isAbs=" + isAbs + ":isInv=" + isInv + ":isCreative=" + isCre
            + ":gl=" + gl + ":gt=" + gt + ":gs=" + gs
            + ":wallet=" + we + ":bal=" + bal);
        // 界面出现后 25 拍自截图（framebuffer，不含桌面）
        if (mc.screen != null) wtDueShot = wtTick + 25;
        wtLastScr = scr;
    }

    if (wtDueShot > 0 && wtTick >= wtDueShot) {
        wtDueShot = -1;
        var res = "err";
        try {
            Shot_CLASS.grab(mc.gameDirectory, mc.getMainRenderTarget(), function (c) { });
            res = "ok";
        } catch (eSc) { res = "" + eSc; }
        wtShotCount++;
        wt("evt=shot:tick=" + wtTick + ":n=" + wtShotCount + ":res=" + res);
    }

    // 余额变化沿：验证「服务端改额 → 客户端读数跟进」的**时序**（屏幕不变时也要落盘）
    var balNow = wtNum(function () { return WalletState_CLASS.balance(); });
    if (balNow !== wtLastBal) {
        wt("evt=bal:tick=" + wtTick + ":bal=" + balNow + ":prev=" + wtLastBal + ":class=" + scr);
        wtLastBal = balNow;
    }

    if (wtTick % 100 === 0) {
        var hide = -1;
        try { hide = mc.options.hideGui ? 1 : 0; } catch (eH) { hide = -1; }
        wt("evt=hb:tick=" + wtTick + ":scr=" + scr + ":hide=" + hide);
    }
});
