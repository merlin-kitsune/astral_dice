// ════════════════════════════════════════════════════════════════════════════
//  astral_kame_client.js —— 1.21.1 客户端侧读数：超天酱/地雷女立牌批指示器(.128)
//
//  【为什么必须走客户端通道】
//    HUD 效果图标与「等级（罗马数字）」由**客户端**从同步过来的 MobEffectInstance 渲染:
//    等级 = amplifier + 1、图标是否显示 = showIcon()、计时条 = duration(∞ ⇒ 不画)。
//    服务端探针只能读服务端实例;本脚本读**客户端实际持有的那份实例**(即 HUD 渲染源)。
//
//  输出:logs/kubejs/client.log 的 `AP_KAMEC:` 行(断言 source = kubejs_client)
//    · 效果集变化时立刻一行(签名变化才打,避免刷屏):
//      AP_KAMEC:evt=chg:tick=<t>:n=<个数>:list=<id:amp:dur:icon:vis,...>
//    · 每 200 tick 一行心跳(证明脚本活着,避免「没变过」与「脚本没跑」混淆):
//      AP_KAMEC:evt=beat:tick=<t>:n=<个数>
//  只读:不写任何游戏状态。
// ════════════════════════════════════════════════════════════════════════════

var KAMEC_Mc = Java.loadClass("net.minecraft.client.Minecraft");

function kcOut(s) {
    try { console.info("AP_KAMEC:" + s); } catch (e) { /* 忽略 */ }
}

function kcTickOf(mc) {
    try { return mc.level.getLevelData().getGameTime(); } catch (e) { return -1; }
}

/** 客户端实际持有的效果签名(排序后拼接)：id:amp:dur:icon:vis */
function kcFxList(mc) {
    var items = [];
    var err = "-";
    try {
        var p = mc.player;
        var map = p.getActiveEffectsMap();
        var it = map.values().iterator();
        while (it.hasNext()) {
            var inst = it.next();
            if (inst == null) continue;
            var id = "-";
            try { id = "" + inst.getDescriptionId(); } catch (e1) { id = "ERR"; }
            var amp = "?", dur = "?", icon = "?", vis = "?";
            try { amp = inst.getAmplifier(); } catch (e2) { /**/ }
            try { dur = inst.getDuration(); } catch (e3) { /**/ }
            try { icon = inst.showIcon() ? 1 : 0; } catch (e4) { /**/ }
            try { vis = inst.isVisible() ? 1 : 0; } catch (e5) { /**/ }
            items.push(id + ":" + amp + ":" + dur + ":" + icon + ":" + vis);
        }
    } catch (eOuter) { err = ("" + eOuter).substring(0, 100); }
    try { items.sort(); } catch (eSort) { /* 忽略 */ }
    return { list: items.join(","), n: items.length, err: err };
}

var kcSig = "unset";
var kcTick = 0;
var kcBoot = 0;

ClientEvents.tick(event => {
    kcTick++;
    var mc = null;
    try { mc = KAMEC_Mc.getInstance(); } catch (e0) { return; }
    if (mc == null || mc.player == null || mc.level == null) return;
    var t = kcTickOf(mc);

    if (kcBoot === 0) {
        kcBoot = 1;
        kcOut("evt=boot:tick=" + t + ":ctick=" + kcTick);
    }

    var r = kcFxList(mc);
    var sig = r.list + "|err=" + r.err;
    if (sig !== kcSig) {
        kcSig = sig;
        kcOut("evt=chg:tick=" + t + ":n=" + r.n + ":err=" + r.err + ":list=" + r.list);
    } else if ((kcTick % 200) === 0) {
        kcOut("evt=beat:tick=" + t + ":n=" + r.n);
    }
});
