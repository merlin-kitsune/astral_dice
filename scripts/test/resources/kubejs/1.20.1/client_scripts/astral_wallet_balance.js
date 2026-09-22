// ════════════════════════════════════════════════════════════════════════════
//  astral_wallet_balance.js —— 星币钱包的**客户端真值读数**探针（1.20.1 / 1.21.1 通用；本副本供 1.20.1 线使用）
//  2026-09-22 新增。用途 = 验证「服务端改余额 → 客户端读数是否跟上」这条链路，
//  它是纯客户端状态（`StarCoinWalletState` 是库里的客户端缓存），服务端日志看不到。
//
//  【为什么另起一个而不是扩 26.1.2 的 astral_wallet_vision.js】
//    本探针只取「屏幕真值 + 余额」，不碰任何界面几何访问器 ⇒ 1.20.1 与 1.21.1 可共用同一份实现。
//
//  【读什么】
//    · `mc.screen` 的 `toString()`（真实类名；KubeJS 禁反射，而 Screen 未覆写 toString）
//    · `instanceof InventoryScreen / CreativeModeInventoryScreen`
//    · `StarCoinWalletState.balance()`（库的客户端缓存，余额条显示的正是它）
//    · `StarCoinCurrency.isWalletEnabled()`
//  变化沿：屏幕变一行（evt=scr）、**余额变一行（evt=bal）** —— 后者是「实时性」的判据。
//
//  【只读性】不改任何状态；副作用只有一行 console.info（→ logs/kubejs/client.log）。
// ════════════════════════════════════════════════════════════════════════════

var Mc_CLASS = Java.loadClass("net.minecraft.client.Minecraft");
var InvScreen_CLASS = Java.loadClass("net.minecraft.client.gui.screens.inventory.InventoryScreen");
var CreativeScreen_CLASS = Java.loadClass("net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen");
var Currency_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.economy.StarCoinCurrency");
var WalletState_CLASS = Java.loadClass("com.merlinkitsune.starenginelib.economy.StarCoinWalletState");

var wbTick = 0;
var wbLastScr = "__init__";
var wbLastBal = "__init__";
var wbBoot = 0;

function wb(s) {
    try { console.info("AP_WALLETBAL:" + s); } catch (e) { /* 回报失败不得影响游戏 */ }
}

function wbBool1(mc, cls) {
    try { return (mc.screen != null && (mc.screen instanceof cls)) ? 1 : 0; } catch (e) { return -1; }
}

function wbNum(fn) {
    try { return fn(); } catch (e) { return -1; }
}

ClientEvents.tick(event => {
    wbTick++;
    var mc = null;
    try { mc = Mc_CLASS.getInstance(); } catch (e0) { return; }
    if (mc == null || mc.level == null) return;

    if (wbBoot === 0) {
        wbBoot = 1;
        wb("evt=boot:tick=" + wbTick
            + ":cur=" + (Currency_CLASS == null ? "null" : "ok")
            + ":state=" + (WalletState_CLASS == null ? "null" : "ok"));
    }

    var scr = (mc.screen == null) ? "null" : ("" + mc.screen);
    var bal = wbNum(function () { return WalletState_CLASS.balance(); });
    var we = wbNum(function () { return Currency_CLASS.isWalletEnabled() ? 1 : 0; });

    if (scr !== wbLastScr) {
        wb("evt=scr:tick=" + wbTick + ":class=" + scr
            + ":isInv=" + wbBool1(mc, InvScreen_CLASS)
            + ":isCreative=" + wbBool1(mc, CreativeScreen_CLASS)
            + ":wallet=" + we + ":bal=" + bal);
        wbLastScr = scr;
    }

    // 余额变化沿：判「服务端改额 ⇒ 客户端读数跟进」的实时性（屏幕不变时也要落盘）
    if (bal !== wbLastBal) {
        wb("evt=bal:tick=" + wbTick + ":bal=" + bal + ":prev=" + wbLastBal + ":class=" + scr);
        wbLastBal = bal;
    }
});
