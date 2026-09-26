// ════════════════════════════════════════════════════════════════════════════
//  astral_test_noai.js —— 测试环境硬性要求：禁用生物 AI（2026-09-18 用户裁决）
//
//  【为什么必须有它 —— 这是流程缺陷，不是被测行为】
//    工具链此前只做两件事：① 进入世界后一次性 `/kill @e[type=!player,distance=..128]`；
//    ② 探针自己生成的靶子逐个 `setNoAi(true)`。**自然刷新的生物仍然带 AI** —— 它们会主动
//    接近/攻击/推挤玩家、投掷弹射物、踩压力板、引爆苦力怕，污染「世界级差值」读数
//    （`self` 施放者 HP 差值、`bolt_delta` 全局雷击计数、实体计数），甚至把玩家打死，
//    使用例随机失败或给出假读数。
//
//  【本脚本做什么】随 `mt_env` 的 kubejs 子命令同步到 `run/<版本>/kubejs/server_scripts/`，
//    世界一加载即生效：
//      · 每 2 tick 横扫每个玩家周围 R=128 格内的所有 Mob，强制 `setNoAi(true)`（幂等）；
//      · `EntityEvents.spawned` 上即时 `setNoAi(true)` —— 新刷的生物在第一次行动前就被停住；
//      · 前 30 秒每 5 秒回报一行 `AP_NOAI:mobs=..:noai=..:radius=..:forced=..:total=..:tick=..`
//        （走 `ServerPlayer#sendSystemMessage`，与探针同一通道、落到 logs/latest.log 的 `[CHAT]` 行），
//        供 `mt_launch` 的**进入世界硬闸门**判定；
//      · 之后**整个会话**每 30 秒一行同类心跳（`mt_report` 的收尾审计据此判定「整轮不存在带 AI 的
//        Mob」，避免只对刚进世界那 30 秒取样 —— 那时世界还是白天/空的，晚刷的生物会漏过去）；
//      · 一旦真的**新**强制了带 AI 的 Mob（正面对照证据），额外吐一行
//        `AP_NOAI_FORCED:new=..:mobs=..:noai=..:total=..:tick=..`（最快 1 秒一次，不刷屏）。
//        没有这行只说明「这一轮没遇到带 AI 的生物」，**不能**当成机制生效的证据（参见 TESTING-SPEC §2）。
//
//  【只禁 AI，不动刷怪规则】
//    · 史莱姆压制由「Superflat World No Slimes」模组负责（见 mt_launch 的 SLIMEGUARD 闸门）；
//    · **禁止**在此使用 `/gamerule doMobSpawning false` —— 那会让 `/astralprobe slimecheck`
//      的对照读数（未装=slimes=103 / 已装=slimes=0）恒为 0，把「刷怪压制」硬闸门变成假证。
//
//  【只读性】本脚本只写 `setNoAi`，不改本模组/世界的任何其它状态，也不参与任何用例断言。
// ════════════════════════════════════════════════════════════════════════════

var ComponentClass = Java.loadClass("net.minecraft.network.chat.Component");
var MobClass = Java.loadClass("net.minecraft.world.entity.Mob");
var AABBClass = Java.loadClass("net.minecraft.world.phys.AABB");

var NOAI_RADIUS = 128;                  // 与 mt_launch 清场半径一致
var NOAI_EVERY_TICKS = 2;               // 强制频率（2 tick = 0.1s）
var NOAI_REPORT_EARLY = 600;            // 前 30 秒：闸门取样窗口
var NOAI_REPORT_EVERY = 100;            //   窗口内每 5 秒一行
var NOAI_HEARTBEAT_EVERY = 600;         // 之后：整轮每 30 秒一行（收尾审计用）
var NOAI_FORCED_MIN_GAP = 20;           // 正面对照行的最小间隔（1 秒）

var noaiFirstTick = -1;
var noaiTotalForced = 0;
var noaiLastMobs = 0;
var noaiLastNoai = 0;
var noaiLastForcedTick = -100000;

function noaiServer(event) {
    try { var s = event.server; if (s != null) return s; } catch (e) { /* 落到下一路 */ }
    try { var s2 = event.getServer(); if (s2 != null) return s2; } catch (e2) { /* 落到下一路 */ }
    return null;
}

/** 回报一行 AP_NOAI（同一玩家多帧不重复刷屏：只发给在线玩家，每人一行） */
function noaiReport(server, text) {
    try {
        var players = null;
        try { players = server.getPlayerList().getPlayers(); } catch (e1) { players = null; }
        if (players == null) return;
        var it = players.iterator();
        while (it.hasNext()) {
            var p = it.next();
            try { p.sendSystemMessage(ComponentClass.literal(text)); } catch (e2) { /* 忽略 */ }
        }
    } catch (eOuter) { /* 回报失败不得影响强制逻辑 */ }
}

/**
 * 横扫所有维度里玩家周围 NOAI_RADIUS 格内的 Mob，强制 setNoAi(true)。
 * 按 UUID 去重（多玩家框重叠时同一生物只计一次）。
 * 返回 [唯一Mob数, 其中已 noAi 数, 本次新强制数]；取不到维度时返回 null。
 */
function noaiSweep(server) {
    var levels = null;
    try { levels = server.getAllLevels(); } catch (e0) { levels = null; }
    if (levels == null) return null;
    var seen = {};
    var mobs = 0;
    var noai = 0;
    var forced = 0;
    var li = levels.iterator();
    while (li.hasNext()) {
        var level = li.next();
        if (level == null) continue;
        var players = null;
        try { players = level.players(); } catch (e1) { players = null; }
        if (players == null || players.isEmpty()) continue;
        var pi = players.iterator();
        while (pi.hasNext()) {
            var p = pi.next();
            if (p == null) continue;
            var list = null;
            try {
                list = level.getEntitiesOfClass(MobClass,
                    AABBClass.ofSize(p.position(), NOAI_RADIUS, NOAI_RADIUS, NOAI_RADIUS));
            } catch (e2) { list = null; }
            if (list == null) continue;
            var mi = list.iterator();
            while (mi.hasNext()) {
                var m = mi.next();
                if (m == null) continue;
                var key = null;
                try { key = "" + m.getUUID(); } catch (e3) { key = null; }
                if (key != null) {
                    if (seen[key] === true) continue;
                    seen[key] = true;
                }
                mobs++;
                var isNo = false;
                try { isNo = m.isNoAi(); } catch (e4) { isNo = false; }
                if (isNo) { noai++; continue; }
                try { m.setNoAi(true); forced++; noai++; } catch (e5) { /* 忽略 */ }
            }
        }
    }
    return [mobs, noai, forced];
}

// 新刷出的生物：在第一次行动之前就停住（比「每 2 tick 横扫」更早一步）
try {
    EntityEvents.spawned(event => {
        try {
            var e = event.entity;
            if (e == null) return;
            if (e instanceof MobClass) e.setNoAi(true);
        } catch (eInner) { /* 忽略 */ }
    });
} catch (eHook) {
    console.warn("[astral_test_noai] EntityEvents.spawned 钩子注册失败（不影响每 2 tick 横扫）: " + eHook);
}

ServerEvents.tick(event => {
    try {
        var server = noaiServer(event);
        if (server == null) {
            // 明确的失败信号：让 mt_launch 的硬闸门报出根因，而不是静默等 30 秒超时
            if (noaiTotalForced >= 0 && noaiFirstTick !== -2) {
                noaiFirstTick = -2;
                console.error("[astral_test_noai] 取不到 MinecraftServer（event.server / event.getServer 均不可用）");
            }
            return;
        }
        var tick = 0;
        try { tick = server.getTickCount(); } catch (e0) { tick = 0; }
        if (noaiFirstTick < 0) noaiFirstTick = tick;
        var age = tick - noaiFirstTick;
        var sweepDue = (tick % NOAI_EVERY_TICKS) === 0;
        var earlyDue = age <= NOAI_REPORT_EARLY && (age % NOAI_REPORT_EVERY) === 0;
        var beatDue = age > NOAI_REPORT_EARLY && (tick % NOAI_HEARTBEAT_EVERY) === 0;
        var reportDue = earlyDue || beatDue;
        if (!sweepDue && !reportDue) return;

        var st = noaiSweep(server);
        if (st == null) {
            if (reportDue) noaiReport(server, "AP_NOAI:ERR:no-levels:tick=" + tick);
            return;
        }
        noaiTotalForced += st[2];
        noaiLastMobs = st[0];
        noaiLastNoai = st[1];
        if (reportDue) {
            noaiReport(server, "AP_NOAI:mobs=" + st[0] + ":noai=" + st[1]
                + ":radius=" + NOAI_RADIUS + ":forced=" + st[2]
                + ":total=" + noaiTotalForced + ":tick=" + tick);
        } else if (st[2] > 0 && (tick - noaiLastForcedTick) >= NOAI_FORCED_MIN_GAP) {
            // 正面对照：真的存在带 AI 的自然刷怪，且本脚本把它停住了
            noaiLastForcedTick = tick;
            noaiReport(server, "AP_NOAI_FORCED:new=" + st[2] + ":mobs=" + st[0]
                + ":noai=" + st[1] + ":total=" + noaiTotalForced + ":tick=" + tick);
        }
    } catch (eOuter) {
        // 绝不让本脚本把测试流程带崩；异常只报一次
        try {
            if (noaiFirstTick !== -3) {
                noaiFirstTick = -3;
                console.error("[astral_test_noai] tick 体异常: " + eOuter);
            }
        } catch (e2) { /* 忽略 */ }
    }
});
