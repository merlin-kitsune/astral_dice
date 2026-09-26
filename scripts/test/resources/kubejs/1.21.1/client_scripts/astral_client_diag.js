// ════════════════════════════════════════════════════════════════════════════
//  astral_client_diag.js —— 客户端侧读数探针（2026-09-22 新增）
//
//  【为什么需要「客户端」探针】
//    actionbar 是**纯客户端**状态：`ActionBarManager` 的 `message`/`endTick` 不写任何日志，
//    服务端探针 `astral_bugfix_probe.js` 完全看不到它。而「截图 + 肉眼」这条判据本身被三件事
//    污染/限制（2026-09-22 三次取证全部踩到）：
//      ① 同一条 y 上压着聊天区（`ActionBarManager.render` 用 `guiHeight-58`，聊天最新一行在
//         `guiHeight-48` ⇒ 相邻两行互相盖）；
//      ② 客户端 debug 通道不落盘，服务端日志里没有客户端状态；
//      ③ **`screenshot` 步骤自身有 ~1.9s 前置开销**（提窗 600ms + 另起 pwsh 进程注入 F2），
//         而产品瞬态提示只存在 `ACTIONBAR_TICKS`=40 tick = **2.0 秒** ⇒ 用 case 步骤去追这个
//         窗口结构性不可靠（实测两次分别差 0.3s / 0.5s 错过）。
//    故本脚本做两件工具链做不到的事：把客户端独有状态落盘（通道 `kubejs_client`），
//    以及**在正确的那一拍自己截图**（见「自截图时序」）。
//
//  【读什么】
//    · `TargetSelectionClient.isActive() / remainingSeconds() / currentTarget()` —— 公开只读 API；
//    · `Minecraft#screen / options#hideGui` —— 排除「界面打开 / F1 隐藏 HUD」这类假阴性；
//    · gameTime 与本次 tick 计数 —— 区分「状态没变」与「客户端卡住/暂停」。
//
//  【为什么不做反射读 `ActionBarManager.message`】
//    KubeJS 7 的类过滤器（`kubejs.classfilter.txt`，随 kubejs jar 打包）用 `- java.lang`
//    **前缀拒绝**，只放行 `java.lang.Object/String/Number/...` 白名单 ⇒ `java.lang.reflect.*`
//    整段不可见（`- java.io` / `- java.nio` 同理）。这解释了探针头部「反射四项全 ERR」的记载。
//    故改用下面两套不依赖反射的手段。
//
//  【注入对照法：判「模组 GUI layer 是否渲染」】
//    会话由 active→idle 后，注入两条**不同 y** 的消息，一张截图同时判读：
//      · `AP_CDIAG_VANILLA_68` —— 原版 `Gui#setOverlayMessage`（原版层，y = guiHeight-68）；
//      · `AP_CDIAG_LIB_58`     —— `ActionBarManager.show`（**与产品 actionbar 同层同坐标**，
//                                  y = guiHeight-58）。
//    两条都在 ⇒ 模组 layer 正常；只有 68 ⇒ 模组 layer 没渲染；都没有 ⇒ 更底层。
//
//  【自截图时序（为什么能拍到 2 秒窗口）】
//    观察到 `act` 由 1→0 的那一拍 T：
//      · T   ：产品 `cancel()` 已经跑完（它就在这一拍的 `TargetSelectionClient.tick()` 里），
//              `showPrompt(...)` 已把消息写进 `ActionBarManager`；
//      · T+1 ：这一帧**带着该提示**被画进 framebuffer；
//      · T+2 ：本探针 `Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), …)` 取帧
//              ⇒ 拿到的正是 T+1 那一帧（含提示）；
//      · T+6 ：**之后**才注入两条对照消息 —— 顺序不能反，否则 `ActionBarManager` 是单槽位，
//              注入会把产品刚写的提示覆盖掉，自截图就拍到对照文字而不是产品文案了。
//    ⚠️ `Screenshot.grab` 的第三个形参是 `Consumer<Component>`（SAM）⇒ 传一个空 JS 函数；
//       若该版本 Rhino 不接受，读数会落成 `evt=shot:res=<异常文本>`（调用方据此判断，不静默）。
//
//  【为什么顺手清空聊天】
//    `astral_test_noai.js` 前 30 秒每 5 秒往聊天写一行，与 actionbar 只差 10 GUI 像素
//    ⇒ 截图里互相盖住（污染源①）。（客户端侧清屏，不影响日志。）
//
//  【只读性】
//    不改任何玩法状态；副作用只有：两条覆盖层文字、清空客户端聊天显示、写 PNG 到 screenshots/。
// ════════════════════════════════════════════════════════════════════════════

var Mc_CLASS = Java.loadClass("net.minecraft.client.Minecraft");
var Component_CLASS = Java.loadClass("net.minecraft.network.chat.Component");
var TSC_CLASS = Java.loadClass("com.merlinkitsune.astral_dice.client.TargetSelectionClient");
var ABM_CLASS = Java.loadClass("com.merlinkitsune.starenginelib.client.ActionBarManager");
var GPC_CLASS = Java.loadClass("com.merlinkitsune.starenginelib.component.GameplayConstants");
var SHOT_CLASS = Java.loadClass("net.minecraft.client.Screenshot");

var CDIAG_HEARTBEAT_TICKS = 100;   // 5 秒一条心跳（证「探针活着」，同时给 gameTime 一个时间轴）
var CDIAG_CHATCLEAR_TICKS = 60;    // 3 秒清一次聊天显示（保证截图里 actionbar 那一行不被盖）
var CDIAG_SHOT_DELAY = 2;          // 观察到会话结束后的第 N 拍自截图（见文件头「自截图时序」）
var CDIAG_INJ_DELAY = 6;           // 自截图之后再注入对照消息（顺序不可颠倒）
// 注入的模组层消息总时长（10 秒）。⚠️ **不能只调一次 `show(msg, 200)`** ——
// `ActionBarManager.show` 内部会把时长截断到 `GameplayConstants.ACTIONBAR_DURATION_TICKS`
// （2026-09-22 实测 = 60 tick，即 3 秒，且该项由配置文件覆写），请求 200 只会得到 3 秒：
// 首跑本用例时截图 B 落在 +5s ⇒ 正好错过，白丢一次取证。
// 故改为**逐 tick 续期**（见下面的 `cdiagLibHold`），与产品自己刷新 actionbar 的手法一致。
var CDIAG_LIB_TICKS = 200;

var cdiagTick = 0;
var cdiagLastAct = -1;
var cdiagSeenActive = 0;
var cdiagEdgeCount = 0;
var cdiagBoot = 0;
var cdiagLibHold = 0;
var cdiagDueShot = -1;
var cdiagDueInj = -1;

/** 输出出口：KubeJS 客户端脚本的 console ⇒ `logs/kubejs/client.log` */
function cdiag(s) {
    try { console.info("AP_CDIAG:" + s); } catch (e) { /* 忽略：不得因回报失败影响诊断 */ }
}

/**
 * gameTime 读数。
 * ⚠️ 不走 `mc.level.getGameTime()` —— 真机实测该形式在 Rhino 直接成员查找下报
 *    "Cannot find function getGameTime"（1.21.1，见 astral_bugfix_probe.js 头部约束 ②），
 *    一律走 `getLevelData().getGameTime()`（与该实现同源）。
 */
function cdiagGameTime(mc) {
    try { return mc.level.getLevelData().getGameTime(); } catch (e) { return -1; }
}

/** 当前准星目标名（无目标时 `-`） */
function cdiagTargetName() {
    try {
        var t = TSC_CLASS.currentTarget();
        if (t == null) return "-";
        return t.getName().getString();
    } catch (e) { return "ERR"; }
}

/** 剩余秒数（非活动态抛异常 ⇒ 收敛成 -1） */
function cdiagRemain() {
    try { return TSC_CLASS.remainingSeconds(); } catch (e) { return -1; }
}

/** 清空客户端聊天显示（`clearSent=false`：只清显示，不动上箭头历史） */
function cdiagClearChat(mc) {
    try { mc.gui.getChat().clearMessages(false); } catch (e) { /* 忽略 */ }
}

/** 由本探针自己取帧（`f2` 步进式截图追不上 2 秒窗口，见文件头） */
function cdiagGrab(mc, t, why) {
    var res = "err";
    try {
        SHOT_CLASS.grab(mc.gameDirectory, mc.getMainRenderTarget(), function (c) { });
        res = "ok";
    } catch (eSc) { res = "" + eSc; }
    cdiag("evt=shot:t=" + t + ":tick=" + cdiagTick + ":why=" + why + ":res=" + res);
}

/** 注入两条对照覆盖层（原版层 / 模组层），并启动逐 tick 续期 */
function cdiagInjectRefs(mc, t) {
    cdiagClearChat(mc);
    var van = "err";
    var lib = "err";
    try {
        mc.gui.setOverlayMessage(Component_CLASS.literal("AP_CDIAG_VANILLA_68"), false);
        van = "ok";
    } catch (eV) { van = "" + eV; }
    try {
        ABM_CLASS.show(Component_CLASS.literal("AP_CDIAG_LIB_58"), CDIAG_LIB_TICKS);
        lib = "ok";
    } catch (eL) { lib = "" + eL; }
    cdiagLibHold = CDIAG_LIB_TICKS;
    cdiag("evt=inj:t=" + t + ":tick=" + cdiagTick + ":van=" + van + ":lib=" + lib
        + ":ab_dur=" + cdiagAbDur());
}

/** `ACTIONBAR_DURATION_TICKS` 实测值（`show` 的时长上限，用来解释「请求 200 只得 3 秒」） */
function cdiagAbDur() {
    try { return GPC_CLASS.ACTIONBAR_DURATION_TICKS; } catch (e) { return -1; }
}

ClientEvents.tick(event => {
    cdiagTick++;
    var mc = null;
    try { mc = Mc_CLASS.getInstance(); } catch (e0) { return; }
    if (mc == null || mc.level == null) return;

    var t = cdiagGameTime(mc);

    // 进世界首拍：报一次环境事实，便于确认「客户端探针真的起来了」
    if (cdiagBoot === 0) {
        cdiagBoot = 1;
        var fade = -1;
        try { fade = GPC_CLASS.ACTIONBAR_FADE_TICKS; } catch (eF) { fade = -1; }
        try {
            cdiag("evt=boot:t=" + t + ":tsc=" + (TSC_CLASS == null ? "null" : "ok")
                + ":abm=" + (ABM_CLASS == null ? "null" : "ok")
                + ":shot=" + (SHOT_CLASS == null ? "null" : "ok")
                + ":ab_dur=" + cdiagAbDur() + ":ab_fade=" + fade);
        } catch (eB) { cdiag("evt=boot:ERR=" + eB); }
    }

    var act = 0;
    try { act = TSC_CLASS.isActive() ? 1 : 0; } catch (eA) { act = -1; }
    if (act === 1) cdiagSeenActive = 1;

    if (act !== cdiagLastAct) {
        var scr = "-";
        try { scr = (mc.screen == null) ? "-" : "open"; } catch (eS) { scr = "ERR"; }
        var hide = 0;
        try { hide = mc.options.hideGui ? 1 : 0; } catch (eH) { hide = -1; }
        cdiag("evt=act:t=" + t + ":tick=" + cdiagTick + ":from=" + cdiagLastAct + ":to=" + act
            + ":rem=" + cdiagRemain() + ":tg=" + cdiagTargetName()
            + ":scr=" + scr + ":hide=" + hide);
        // 1→0：产品刚在这一拍写完瞬态提示 ⇒ 排定「延后 2 拍自截图」与「延后 6 拍注入对照」
        if (cdiagLastAct === 1 && act === 0) {
            cdiagEdgeCount++;
            cdiagDueShot = cdiagTick + CDIAG_SHOT_DELAY;
            cdiagDueInj = cdiagTick + CDIAG_INJ_DELAY;
            cdiag("evt=edge:t=" + t + ":tick=" + cdiagTick + ":n=" + cdiagEdgeCount
                + ":shot_at=" + cdiagDueShot + ":inj_at=" + cdiagDueInj);
        }
    } else if (cdiagTick % CDIAG_HEARTBEAT_TICKS === 0) {
        var hide2 = 0;
        try { hide2 = mc.options.hideGui ? 1 : 0; } catch (eH2) { hide2 = -1; }
        cdiag("evt=hb:t=" + t + ":tick=" + cdiagTick + ":act=" + act
            + ":rem=" + cdiagRemain() + ":tg=" + cdiagTargetName()
            + ":hide=" + hide2 + ":seen=" + cdiagSeenActive + ":edges=" + cdiagEdgeCount);
    }

    // T+2：自截图 —— 抓到的正是 T+1 那一帧（含产品刚写的瞬态提示）
    if (cdiagDueShot > 0 && cdiagTick >= cdiagDueShot) {
        cdiagDueShot = -1;
        cdiagGrab(mc, t, "post-idle");
    }

    // T+6：注入两条对照（顺序必须在自截图之后，否则会覆盖产品提示）
    if (cdiagDueInj > 0 && cdiagTick >= cdiagDueInj) {
        cdiagDueInj = -1;
        cdiagInjectRefs(mc, t);
    }

    // 注入的模组层消息逐 tick 续期（见 CDIAG_LIB_TICKS 的注释）
    if (cdiagLibHold > 0) {
        cdiagLibHold--;
        try { ABM_CLASS.show(Component_CLASS.literal("AP_CDIAG_LIB_58"), 60); } catch (eR) { /* 忽略 */ }
        if (cdiagLibHold === 0) cdiag("evt=libend:t=" + t + ":tick=" + cdiagTick);
    }

    // 周期性清聊天显示：让 actionbar 那一行在截图里始终干净
    if (cdiagTick % CDIAG_CHATCLEAR_TICKS === 0) cdiagClearChat(mc);

    cdiagLastAct = act;
});
