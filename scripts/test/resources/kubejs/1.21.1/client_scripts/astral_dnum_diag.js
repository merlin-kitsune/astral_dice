// ════════════════════════════════════════════════════════════════════════════
//  astral_dnum_diag.js —— 伤害跳字（DamageNumberStore）客户端侧读数探针
//                      （2026-09-26 新增；本文件是**模板**，由 mt_env.ps1 的
//                       Sync-MtEnvKubejs 同步到 run/1.21.1/kubejs/client_scripts/）
//
//  【为什么必须走客户端通道】
//    `DamageNumberStore` 是**纯客户端**存储层（服务端只有聚合器，发包后即清空）。
//    本功能四条判据里有三条只在客户端成立：
//      ① 红/绿**分组**   —— 颜色即分槽键（`Group.ATTACK=0xFF5555` / `SPELL=0x7CFC00`）；
//      ② **冻结坐标**    —— 坐标由服务端在命中那一刻下发，客户端不再每帧取实体位置
//                           ⇒ 同一条数字在它存活的多个 tick 里 `x/y/z` **完全相同**；
//      ③ **±30° 扩散**   —— 每条数字生成时抽一个自己的扩散角（`spreadRadians`）。
//    ④「最终值」的**服务端一侧**（实际扣血量）由 `server_scripts/astral_bugfix_probe.js`
//      的 dnum 系列命令给出；两边数值相等才是判据，故本探针只负责把原始读数打出来。
//
//  【输出格式】
//    AP_DNUM:evt=boot:...                              启动自检（类可见 / 常量）
//    AP_DNUM:evt=tick:t=<tick>:n=<条数>:items=[damage,COLOR,deg=..,x=..,y=..,z=..,rem=..]...
//      · COLOR 是 6 位十六进制（FF5555=攻击力/伤害加成类红；7CFC00=法伤/技能伤害类绿）
//      · deg  是扩散角（度，±30 以内）；两条不同数字的 deg 不同即证明**随机扩散**
//      · rem  是剩余 tick（同一条数字逐 tick 递减 ⇒ 可用它把连续行归并为同一条）
//
//  【只读性】
//    本探针**不写**任何游戏状态；副作用只有 `console.info`（落 `logs/kubejs/client.log`）。
// ════════════════════════════════════════════════════════════════════════════

var DNUM_Mc = Java.loadClass("net.minecraft.client.Minecraft");
// 仅为「类是否可见 / 常量是否正确」的自检（实际读数走实例字段）
var DNUM_Store = Java.loadClass("com.merlinkitsune.astral_dice.client.DamageNumberStore");

var DNUM_BOOT_WAIT = 40;   // 进世界后等 40 tick 再打首条（避开加载期的半初始化帧）
var DNUM_EVERY = 2;        // 每 2 tick 一条读数（数字存活 40 tick ⇒ 约 20 条/次）

var dnumTick = 0;
var dnumBoot = 0;
var dnumSeen = 0;          // 本会话是否已出现过数字（=0 时空态静默，不刷屏）

/** 输出出口：KubeJS 客户端脚本的 console ⇒ logs/kubejs/client.log */
function dn(s) {
    try { console.info("AP_DNUM:" + s); } catch (e) { /* 忽略：不得因回报失败影响取数 */ }
}

/** Java int 颜色 → 6 位大写十六进制 */
function dnHex(c) {
    try {
        var v = (c + 0) & 0xFFFFFF;
        var s = v.toString(16).toUpperCase();
        while (s.length < 6) s = "0" + s;
        return s;
    } catch (e) { return "ERR"; }
}

/** 保留 f 位小数的定点输出 */
function dnFix(v, f) {
    try { return Math.round((v + 0) * f) / f; } catch (e) { return "ERR"; }
}

/** 把当前在显的每条数字压成一行 `[damage,COLOR,deg=..,x=..,y=..,z=..,rem=..]` */
function dnDump() {
    var list = DNUM_Store.active();
    var n = list.size();
    if (n === 0) return { n: 0, s: "-" };
    var sb = [];
    var it = list.iterator();
    while (it.hasNext()) {
        var e = it.next();
        var deg = dnFix((e.spreadRadians + 0) * 57.2957795, 100);
        sb.push("[" + (e.damage + 0) + "," + dnHex(e.color)
            + ",deg=" + deg
            + ",x=" + dnFix(e.x, 1000) + ",y=" + dnFix(e.y, 1000) + ",z=" + dnFix(e.z, 1000)
            + ",rem=" + (e.remaining + 0) + "]");
    }
    return { n: n, s: sb.join("") };
}

ClientEvents.tick(event => {
    dnumTick++;
    var mc = null;
    try { mc = DNUM_Mc.getInstance(); } catch (e0) { return; }
    if (mc == null || mc.level == null) return;

    if (dnumBoot === 0) {
        dnumBoot = 1;
        var ok = "ERR", dur = "ERR", sp = "ERR";
        try { ok = (DNUM_Store == null) ? "null" : "ok"; } catch (eB) { ok = "" + eB; }
        try { dur = (DNUM_Store.DURATION + 0); } catch (eD) { dur = "ERR"; }
        try { sp = (DNUM_Store.MAX_SPREAD_DEGREES + 0); } catch (eS) { sp = "ERR"; }
        dn("evt=boot:tick=" + dnumTick + ":store=" + ok + ":dur=" + dur + ":spread=" + sp);
    }

    if (dnumTick < DNUM_BOOT_WAIT) return;
    if ((dnumTick - DNUM_BOOT_WAIT) % DNUM_EVERY !== 0) return;

    var out;
    try { out = dnDump(); } catch (e1) {
        dn("evt=tick:t=" + dnumTick + ":n=ERR:items=ERR:" + ("" + e1).substring(0, 140));
        return;
    }
    if (out.n === 0 && dnumSeen === 0) return;   // 从未出现过 ⇒ 静默
    if (out.n > 0) dnumSeen++;
    dn("evt=tick:t=" + dnumTick + ":n=" + out.n + ":items=" + out.s);
});
