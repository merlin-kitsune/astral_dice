// ============================================================================
// astral_batch3_probe.js - 2026-10-29 实机批（1.21.1）行为取证探针（独立文件）
// 覆盖：D1 事件闸门解绑 / D2 ESC 取消（配合真按键注入）/ D3 问号冷却 238t + 窗内拦截 /
//       D4 动画窗内四类入口拦截 + 直用型立牌放行 / D5 动画开始强关选择会话 /
//       筹码商人与卡牌商人价格区间 / 绿宝石骰星币折算（filteredFor + mixin getCostA）
// 命令根：/astralbatch3 <setup|d1|d2|d2after|d3|d4|d5|trade|star|state> <tag>
// 输出：AP_B3_<tag>_* ；全部经玩家聊天落到 latest.log（[CHAT] 行）
// 注意：本文件**不修改产品状态以外的任何东西**；所有写入都在测试存档的玩家身上。
// ============================================================================
function b3TryClass(n) { try { return Java.loadClass(n); } catch (e) { return null; } }

var FBX3_Component = b3TryClass('net.minecraft.network.chat.Component');
var FBX3_ItemStack = b3TryClass('net.minecraft.world.item.ItemStack');
var FBX3_StringArg = b3TryClass('com.mojang.brigadier.arguments.StringArgumentType');
var FBX3_Hand = b3TryClass('net.minecraft.world.InteractionHand');
var FBX3_GameType = b3TryClass('net.minecraft.world.level.GameType');
var FBX3_MerchantOffers = b3TryClass('net.minecraft.world.item.trading.MerchantOffers');
var FBX3_EntityType = b3TryClass('net.minecraft.world.entity.EntityType');
var FBX3_Villager = b3TryClass('net.minecraft.world.entity.npc.Villager');
var FBX3_VillagerData = b3TryClass('net.minecraft.world.entity.npc.VillagerData');
var FBX3_VillagerType = b3TryClass('net.minecraft.world.entity.npc.VillagerType');
var FBX3_VillagerTrades = b3TryClass('net.minecraft.world.entity.npc.VillagerTrades');
var FBX3_Array = b3TryClass('java.lang.reflect.Array');
var FBX3_Files = b3TryClass('java.nio.file.Files');
var FBX3_Paths = b3TryClass('java.nio.file.Paths');
var FBX3_Integer = b3TryClass('java.lang.Integer');

var FBX3_ModItems = b3TryClass('com.merlinkitsune.astral_dice.item.ModItems');
var FBX3_Anim = b3TryClass('com.merlinkitsune.astral_dice.event.EventAnimationLock');
var FBX3_Gate = b3TryClass('com.merlinkitsune.astral_dice.event.GlobalEventGate');
var FBX3_Choice = b3TryClass('com.merlinkitsune.astral_dice.event.EventChoiceManager');
var FBX3_Dispatcher = b3TryClass('com.merlinkitsune.astral_dice.event.AstralEventDispatcher');
var FBX3_EventIds = b3TryClass('com.merlinkitsune.astral_dice.event.AstralEventIds');
var FBX3_TriggerType = b3TryClass('com.merlinkitsune.astral_dice.event.api.AstralEventTrigger');
var FBX3_ModProfs = b3TryClass('com.merlinkitsune.astral_dice.world.ModVillagerProfessions');
var FBX3_EmeraldDiceTrade = b3TryClass('com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade');
var FBX3_CurioSlot = b3TryClass('com.merlinkitsune.astral_dice.item.CurioSlotUtil');
var FBX3_CuriosApi = b3TryClass('top.theillusivec4.curios.api.CuriosApi');
var FBX3_BaseSign = b3TryClass('com.merlinkitsune.astral_dice.item.sign.BaseSignItem');

/** 当前场景（同一时刻只跑一条；新命令覆盖旧场景） */
var FBX3_STATE = { scen: null };
var FBX3_REFL_ERR = '';

// ---- 基础输出 / 工具 --------------------------------------------------------
function b3Say(p, text) {
    if (p == null) return;
    try { p.sendSystemMessage(FBX3_Component.literal(text)); } catch (e) { /* 忽略 */ }
}

function b3Ex(e) {
    var s;
    try { s = '' + e; } catch (x) { s = '<unprintable>'; }
    return s.split('\n')[0];
}

function b3P(ctx) { return ctx.source.getPlayerOrException(); }

function b3Now(p) {
    try { return p.level.getLevelData().getGameTime(); } catch (e1) { /* 下一路 */ }
    try { return p.level.getGameTime(); } catch (e2) { /* 下一路 */ }
    return -1;
}

function b3Round(v) { return Math.round(v * 100) / 100; }

function b3Guard(ctx, tag, sub, fn) {
    try { return fn(); } catch (err) {
        try { ctx.source.sendFailure(FBX3_Component.literal('AP_B3_' + tag + '_EX:' + sub + ':' + b3Ex(err))); } catch (x) { }
        return 0;
    }
}

function b3ArrLen(a) {
    if (a == null) return 0;
    try { if (typeof a.length === 'number') return a.length; } catch (e) { /* 下一路 */ }
    try { return FBX3_Array.getLength(a); } catch (e2) { return 0; }
}

function b3ArrGet(a, i) {
    try { return a[i]; } catch (e) { /* 下一路 */ }
    try { return FBX3_Array.get(a, i); } catch (e2) { return null; }
}

// ---- 会话 token / 会话计数（不使用反射） ------------------------------------
// 事实（2026-10-29 实测）：Rhino 的 Java 类句柄不能调 Class 实例方法（InternalError:
// "has no public instance field or method named getDeclaredField"），而 Class.forName /
// ClassLoader.loadClass 走反射时被 JDK 的 caller-sensitive 限制拒（IllegalAccessException:
// "Attempt to lookup caller-sensitive method using restricted lookup object"）。
// 且 EventChoiceManager 没有公开的会话访问器（public 只有 start/confirm/cancel/cancelFor）。
// 故 token 一律从产品自己的机器行 AP_EVENT_CHOICE: open:<token>:... 读取（latest.log 可读，
// user.dir = run/1.21.1，已实测）；会话是否还在则用「再 start 一次是否成功」的行为判据。
function b3Token(p) { return b3TokenFromLog(); }

var FBX3_SESS = 0;
function b3SessSet(v) { FBX3_SESS = v; return FBX3_SESS; }
function b3SessionCount(p) { return FBX3_SESS; }

/** 兜底：从 latest.log 抓最后一条 AP_EVENT_CHOICE: open:<token> 的 token */
function b3TokenFromLog() {
    try {
        if (FBX3_Files == null || FBX3_Paths == null) return -1;
        var cands = ['logs/latest.log', 'run/1.21.1/logs/latest.log',
            'F:/MCProject/astral_dice_multiloader/run/1.21.1/logs/latest.log'];
        for (var i = 0; i < cands.length; i++) {
            var fp = FBX3_Paths.get(cands[i]);
            if (!FBX3_Files.exists(fp)) continue;
            var lines = FBX3_Files.readAllLines(fp);
            for (var j = lines.size() - 1; j >= 0; j--) {
                var ln = '' + lines.get(j);
                var k = ln.indexOf('AP_EVENT_CHOICE: open:');
                if (k < 0) continue;
                // 标记 'AP_EVENT_CHOICE: open:' 长度 = 22（'AP_EVENT_CHOICE:' 16 + ' ' 1 + 'open:' 5）
                var rest = ln.substring(k + 22);
                var c2 = rest.indexOf(':opt0=');
                if (c2 <= 0) continue;
                var tokStr = '' + rest.substring(0, c2);
                var okNum = true;
                for (var q = 0; q < tokStr.length; q++) {
                    var ch = tokStr.charAt(q);
                    if (ch < '0' || ch > '9') { okNum = false; break; }
                }
                if (!okNum || tokStr.length === 0) continue;
                return FBX3_Integer.parseInt(tokStr);
            }
        }
    } catch (e) { /* 忽略 */ }
    return -1;
}

// ---- 产品状态小工具 ---------------------------------------------------------
function b3AnimReset() { try { FBX3_Anim.reset(); } catch (e) { } }
function b3GateReset() { try { FBX3_Gate.reset(); } catch (e) { } }
function b3AnimOn(p) { try { return FBX3_Anim.isAnimating(p) ? 1 : 0; } catch (e) { return -1; } }
function b3GateRem(p) { try { return FBX3_Gate.gateUntilGameTime() - b3Now(p); } catch (e) { return -1; } }

function b3Cd(p) {
    try {
        var pct = p.getCooldowns().getCooldownPercent(FBX3_ModItems.EVENT_TICKET.get(), 0.0);
        return Math.round(pct * 238);
    } catch (e) { return -1; }
}

function b3CurioHandler(p, slotId) {
    try {
        if (FBX3_CuriosApi == null) return null;
        var inv = FBX3_CuriosApi.getCuriosInventory(p).orElse(null);
        if (inv == null) return null;
        return inv.getStacksHandler(slotId).orElse(null);
    } catch (e) { return null; }
}

function b3SetCurio(p, slotId, stack) {
    try {
        var h = b3CurioHandler(p, slotId);
        if (h == null) return 0;
        if (h.getStacks().getSlots() < 1) return 0;
        h.getStacks().setStackInSlot(0, stack);
        return 1;
    } catch (e) { return 0; }
}

function b3MainHand(p) { return p.getMainHandItem(); }

function b3SetHand(p, stack) {
    try { p.setItemInHand(FBX3_Hand.MAIN_HAND, stack); return 1; } catch (e) { return 0; }
}

function b3UseHand(p) {
    try { return '' + p.getMainHandItem().use(p.level, p, FBX3_Hand.MAIN_HAND); } catch (e) { return 'EX:' + b3Ex(e); }
}

function b3Trigger(p) {
    try {
        b3AnimReset();
        b3GateReset();
        return FBX3_Dispatcher.trigger(p, FBX3_EventIds.LEARN_FROM_OTHERS, FBX3_TriggerType.ITEM_USE) ? 1 : 0;
    } catch (e) { return -1; }
}

// ---- 场景引擎（tick 驱动；窗口 < 3s 的动作都必须在这里做） -------------------
function b3Start(tag, p, name, steps) {
    var now = b3Now(p);
    FBX3_STATE.scen = { tag: tag, p: p, t0: now, steps: steps, idx: 0, name: name };
    b3Say(p, 'AP_B3_' + tag + '_SCEN:' + name + ':t0=' + now);
}

ServerEvents.tick(function (event) {
    var s = FBX3_STATE.scen;
    if (s == null) return;
    var now;
    try { now = b3Now(s.p); } catch (e0) { FBX3_STATE.scen = null; return; }
    if (now < 0) return;
    var el = now - s.t0;
    var guard = 0;
    while (s.idx < s.steps.length && el >= s.steps[s.idx].at && guard < 32) {
        guard++;
        var step = s.steps[s.idx];
        s.idx++;
        try { step.fn(now, el); } catch (e) {
            b3Say(s.p, 'AP_B3_' + s.tag + '_STEPERR:' + step.name + ':' + b3Ex(e));
        }
    }
    if (s.idx >= s.steps.length) FBX3_STATE.scen = null;
});

// ---- setup ------------------------------------------------------------------
function b3Setup(p, tag) {
    var out = [];
    try { FBX3_Anim.reset(); out.push('anim=ok'); } catch (e) { out.push('anim=ERR'); }
    try { FBX3_Gate.reset(); out.push('gate=ok'); } catch (e) { out.push('gate=ERR'); }
    try { FBX3_Choice.cancelFor(p, 'probe_setup'); out.push('choice=ok'); } catch (e) { out.push('choice=ERR'); }
    try { FBX3_Anim.reset(); } catch (e) { }
    try { p.setItemInHand(FBX3_Hand.MAIN_HAND, FBX3_ItemStack.EMPTY); out.push('hand=ok'); } catch (e) { out.push('hand=ERR'); }
    try { p.getCooldowns().removeCooldown(FBX3_ModItems.EVENT_TICKET.get()); out.push('cd=ok'); } catch (e) { out.push('cd=ERR'); }
    b3SessSet(0);
    b3Say(p, 'AP_B3_' + tag + '_SETUP:' + out.join(':') + ':sessions=' + b3SessionCount(p) + (FBX3_REFL_ERR === '' ? '' : ':reflerr=' + FBX3_REFL_ERR));
}

// ---- D1：闸门锁定时大侦探立牌主动技能必须能开（解绑） -----------------------
function b3DoD1(ctx, tag) {
    var p = b3P(ctx);
    b3Setup(p, tag);
    var trig = b3Trigger(p);
    b3Say(p, 'AP_B3_' + tag + '_D1TRIG:trig=' + trig + ':t=' + b3Now(p) + ':anim=' + b3AnimOn(p) + ':gate_rem=' + b3GateRem(p));
    if (trig !== 1) { b3Say(p, 'AP_B3_' + tag + '_D1ABORT:trig=' + trig); return 0; }
    var st = { tok: -1 };
    b3Start(tag, p, 'd1', [
        { name: 'd1_start', at: 45, fn: function (n2) {
            var anim = b3AnimOn(p);
            var g = b3GateRem(p);
            var ok = -1; var err = '';
            try { ok = FBX3_Choice.start(p) ? 1 : 0; } catch (e) { err = b3Ex(e); }
            var tok = -1;
            if (ok === 1) { tok = b3Token(p); if (tok < 0) tok = b3TokenFromLog(); }
            st.tok = tok;
            b3SessSet(ok === 1 ? 1 : 0);
            b3Say(p, 'AP_B3_' + tag + '_D1START:anim=' + anim + ':gate_rem=' + g + ':start=' + ok
                + ':tok=' + tok + ':sessions=' + b3SessionCount(p) + (err === '' ? '' : ':err=' + err));
        } },
        { name: 'd1_confirm', at: 48, fn: function () {
            var sent = 0; var err = '';
            if (st.tok >= 0) { try { FBX3_Choice.confirm(p, st.tok, 0); sent = 1; b3SessSet(0); } catch (e) { err = b3Ex(e); } }
            b3Say(p, 'AP_B3_' + tag + '_D1CONF:tok=' + st.tok + ':sent=' + sent + ':sessions=' + b3SessionCount(p)
                + ':anim=' + b3AnimOn(p) + (err === '' ? '' : ':err=' + err));
        } }
    ]);
    return 1;
}

// ---- D2：开选择会话（ESC 由用例真按键注入），随后校验会话已关闭 --------------
function b3DoD2(ctx, tag) {
    var p = b3P(ctx);
    b3Setup(p, tag);
    var ok = -1;
    try { ok = FBX3_Choice.start(p) ? 1 : 0; } catch (e) { }
    var tok = ok === 1 ? b3Token(p) : -1;
    if (tok < 0 && ok === 1) tok = b3TokenFromLog();
    b3SessSet(ok === 1 ? 1 : 0);
    b3Say(p, 'AP_B3_' + tag + '_D2OPEN:start=' + ok + ':tok=' + tok + ':sessions=' + b3SessionCount(p));
    return ok;
}

function b3DoD2After(ctx, tag) {
    var p = b3P(ctx);
    // 行为判据：ESC 若真的关掉了旧会话，则此刻再 start 必须成功（旧会话仍在时 start 只会
    // 打 already_open 并返回 false）。随后 cancelFor 收掉这个新会话。
    var restart = -1; var rerr = '';
    try { restart = FBX3_Choice.start(p) ? 1 : 0; } catch (e) { rerr = b3Ex(e); }
    var sent = 0;
    try { FBX3_Choice.cancelFor(p, 'probe_after_esc'); sent = 1; } catch (e2) { }
    b3SessSet(0);
    b3Say(p, 'AP_B3_' + tag + '_D2AFTER:restart=' + restart + ':cancel_for_sent=' + sent
        + ':tok_restart=' + (restart === 1 ? b3TokenFromLog() : -1) + (rerr === '' ? '' : ':err=' + rerr));
    return 1;
}

// ---- D3：问号一次使用 -> 238t 冷却 + 窗内二次使用被拦 -----------------------
function b3DoD3(ctx, tag) {
    var p = b3P(ctx);
    b3Setup(p, tag);
    var err = '';
    try { p.setGameMode(FBX3_GameType.SURVIVAL); } catch (e) { err += '|gm:' + b3Ex(e); }
    var st = { count0: -1 };
    try {
        b3SetHand(p, new FBX3_ItemStack(FBX3_ModItems.EVENT_TICKET.get(), 2));
        st.count0 = b3MainHand(p).getCount();
    } catch (e2) { err += '|hand:' + b3Ex(e2); }
    b3Say(p, 'AP_B3_' + tag + '_D3PRE:count0=' + st.count0 + ':cd0=' + b3Cd(p) + ':gm=SURVIVAL' + (err === '' ? '' : ':err=' + err));
    b3Start(tag, p, 'd3', [
        { name: 'd3_use1', at: 1, fn: function () {
            var res = b3UseHand(p);
            b3Say(p, 'AP_B3_' + tag + '_D3A:use1=' + res + ':count1=' + b3MainHand(p).getCount());
        } },
        { name: 'd3_read', at: 3, fn: function () {
            b3Say(p, 'AP_B3_' + tag + '_D3B:cd1=' + b3Cd(p) + ':anim=' + b3AnimOn(p) + ':count1=' + b3MainHand(p).getCount());
        } },
        { name: 'd3_use2', at: 5, fn: function () {
            var res = b3UseHand(p);
            b3Say(p, 'AP_B3_' + tag + '_D3C:use2=' + res + ':count2=' + b3MainHand(p).getCount()
                + ':cd2=' + b3Cd(p) + ':anim2=' + b3AnimOn(p));
        } },
        { name: 'd3_restore', at: 14, fn: function () {
            try { p.setGameMode(FBX3_GameType.CREATIVE); } catch (e) { }
            b3SetHand(p, FBX3_ItemStack.EMPTY);
            try { p.getCooldowns().removeCooldown(FBX3_ModItems.EVENT_TICKET.get()); } catch (e2) { }
            b3AnimReset(); b3GateReset();
            b3Say(p, 'AP_B3_' + tag + '_D3D:restore=1:gm=CREATIVE:anim=' + b3AnimOn(p) + ':sessions=' + b3SessionCount(p));
        } }
    ]);
    return 1;
}

// ---- D4：动画窗内四类入口拦截 + 直用型立牌放行 + 窗后放行 -------------------
function b3DoD4(ctx, tag) {
    var p = b3P(ctx);
    b3Setup(p, tag);
    var diceSlots = -1; var standSlots = -1; var hasDice = 0;
    try {
        var hd = b3CurioHandler(p, 'dice');
        if (hd != null) {
            diceSlots = hd.getStacks().getSlots();
            if (diceSlots >= 1) hd.getStacks().setStackInSlot(0, new FBX3_ItemStack(FBX3_ModItems.DICE.get(), 1));
        }
        var hs = b3CurioHandler(p, 'stand');
        if (hs != null) standSlots = hs.getStacks().getSlots();
        hasDice = FBX3_CurioSlot.hasDiceEquipped(p) ? 1 : 0;
    } catch (e) { }
    b3Say(p, 'AP_B3_' + tag + '_D4PRE:dice_slots=' + diceSlots + ':stand_slots=' + standSlots + ':hasdice=' + hasDice);

    var st = { win: [] };

    function win(n) {
        var trig = b3Trigger(p);
        st.win.push('w' + n + '=' + trig);
        b3Say(p, 'AP_B3_' + tag + '_D4WIN:win=' + n + ':trig=' + trig + ':anim=' + b3AnimOn(p)
            + ':gate_rem=' + b3GateRem(p));
    }

    b3Start(tag, p, 'd4', [
        { name: 'd4_w1', at: 0, fn: function () { win(1); } },
        { name: 'd4_op1', at: 2, fn: function () {
            var ok = -1; var err = '';
            try { ok = FBX3_Choice.start(p) ? 1 : 0; } catch (e) { err = b3Ex(e); }
            b3Say(p, 'AP_B3_' + tag + '_D4OP:win=1:action=event_choice_start:ok=' + ok + ':anim=' + b3AnimOn(p) + (err === '' ? '' : ':err=' + err));
            if (ok === 1) { try { FBX3_Choice.cancelFor(p, 'probe_d4_op1_unexpected'); } catch (e2) { } }
        } },
        { name: 'd4_w2', at: 60, fn: function () { win(2); } },
        { name: 'd4_op2', at: 62, fn: function () {
            b3SetHand(p, new FBX3_ItemStack(FBX3_ModItems.EVENT_TICKET.get(), 2));
            var res = b3UseHand(p);
            b3Say(p, 'AP_B3_' + tag + '_D4OP:win=2:action=event_draw:ok=' + res + ':count=' + b3MainHand(p).getCount() + ':anim=' + b3AnimOn(p));
            b3SetHand(p, FBX3_ItemStack.EMPTY);
        } },
        { name: 'd4_w3', at: 120, fn: function () { win(3); } },
        { name: 'd4_op3', at: 122, fn: function () {
            var set = b3SetCurio(p, 'stand', new FBX3_ItemStack(FBX3_ModItems.JILL_SIGN.get(), 1));
            var err = '';
            try { FBX3_BaseSign.performSkillForCurio(p); } catch (e) { err = b3Ex(e); }
            b3Say(p, 'AP_B3_' + tag + '_D4OP:win=3:action=sign_active_skill:setcurio=' + set + ':anim=' + b3AnimOn(p) + (err === '' ? '' : ':err=' + err));
        } },
        { name: 'd4_w4', at: 180, fn: function () { win(4); } },
        { name: 'd4_op4', at: 182, fn: function () {
            b3SetHand(p, new FBX3_ItemStack(FBX3_ModItems.EFFECT_CARD_BERSERK.get(), 1));
            var res = b3UseHand(p);
            b3Say(p, 'AP_B3_' + tag + '_D4OP:win=4:action=effect_card_selector:ok=' + res + ':anim=' + b3AnimOn(p));
            b3SetHand(p, FBX3_ItemStack.EMPTY);
        } },
        { name: 'd4_w5', at: 240, fn: function () { win(5); } },
        { name: 'd4_op5', at: 242, fn: function () {
            var set = b3SetCurio(p, 'stand', new FBX3_ItemStack(FBX3_ModItems.KOMACHI_SIGN.get(), 1));
            var err = '';
            try { FBX3_BaseSign.performSkillForCurio(p); } catch (e) { err = b3Ex(e); }
            b3Say(p, 'AP_B3_' + tag + '_D4OP:win=5:action=sign_direct:setcurio=' + set + ':anim=' + b3AnimOn(p) + (err === '' ? '' : ':err=' + err));
        } },
        { name: 'd4_final', at: 290, fn: function () {
            var anim = b3AnimOn(p);
            var ok = -1;
            try { ok = FBX3_Choice.start(p) ? 1 : 0; } catch (e) { }
            b3SessSet(ok === 1 ? 1 : 0);
            var c = 0;
            if (ok === 1) { try { FBX3_Choice.cancelFor(p, 'probe_d4_end'); c = 1; b3SessSet(0); } catch (e2) { } }
            b3Say(p, 'AP_B3_' + tag + '_D4END:anim=' + anim + ':final_start=' + ok + ':final_cancel=' + c
                + ':wins=' + st.win.join(',') + ':sessions=' + b3SessionCount(p));
        } },
        { name: 'd4_cleanup', at: 292, fn: function () {
            b3SetCurio(p, 'stand', FBX3_ItemStack.EMPTY);
            b3AnimReset(); b3GateReset();
            b3Say(p, 'AP_B3_' + tag + '_D4CLEAN:anim=' + b3AnimOn(p) + ':sessions=' + b3SessionCount(p));
        } }
    ]);
    return 1;
}

// ---- D5：已有选择会话时再开事件 -> 会话被强关，随后 confirm 无效 -------------
function b3DoD5(ctx, tag) {
    var p = b3P(ctx);
    b3Setup(p, tag);
    var ok = -1;
    try { ok = FBX3_Choice.start(p) ? 1 : 0; } catch (e) { }
    var tok = ok === 1 ? b3Token(p) : -1;
    if (tok < 0 && ok === 1) tok = b3TokenFromLog();
    b3SessSet(ok === 1 ? 1 : 0);
    b3Say(p, 'AP_B3_' + tag + '_D5OPEN:start=' + ok + ':tok=' + tok + ':sessions=' + b3SessionCount(p));
    if (ok !== 1) return 0;
    b3Start(tag, p, 'd5', [
        { name: 'd5_trig', at: 5, fn: function () {
            var trig = b3Trigger(p);
            if (trig === 1) { b3SessSet(0); }
            b3Say(p, 'AP_B3_' + tag + '_D5TRIG:trig=' + trig + ':anim=' + b3AnimOn(p)
                + ':sessions=' + b3SessionCount(p));
        } },
        { name: 'd5_confirm', at: 45, fn: function () {
            var err = '';
            try { FBX3_Choice.confirm(p, tok, 0); } catch (e) { err = b3Ex(e); }
            var after = b3SessionCount(p);
            try { FBX3_Choice.cancelFor(p, 'probe_d5_after'); } catch (e2) { }
            b3SessSet(0);
            b3AnimReset(); b3GateReset();
            b3Say(p, 'AP_B3_' + tag + '_D5AFTER:sessions=' + after + ':tok=' + tok + ':anim=' + b3AnimOn(p)
                + (err === '' ? '' : ':err=' + err));
        } }
    ]);
    return 1;
}

// ---- 商人价格采样 -----------------------------------------------------------
function b3PricesStr(prices) {
    var keys = [];
    for (var k in prices) { if (prices.hasOwnProperty(k)) keys.push(Number(k)); }
    keys.sort(function (a, b) { return a - b; });
    return keys.join(',');
}

// 2026-10-29 修：Rhino 里 VillagerTrades.TRADES 被当普通对象（无 .get），改走
// 「新建内存村民 -> setVillagerData(profession, level) -> getOffers()」这条等价路径
// （AbstractVillager#getOffers 在 offers==null 时惰性生成该等级 listing，AutoJS 侧已实测）。
function b3FreshOffers(lvlObj, holder, level) {
    var v = new FBX3_Villager(FBX3_EntityType.VILLAGER, lvlObj);
    var set = 0;
    try { v.setVillagerData(new FBX3_VillagerData(FBX3_VillagerType.PLAINS, holder, level)); set = 1; } catch (e1) { }
    if (set === 0) { v.setVillagerData(new FBX3_VillagerData(FBX3_VillagerType.PLAINS, holder.get(), level)); }
    return v.getOffers();
}

function b3SampleLevel(p, tag, prof, holder, level, out) {
    var samples = 0; var exch = 0; var mn = -1; var mx = -1;
    var prices = {}; var err = ''; var vtries = 0;
    for (vtries = 0; vtries < 160; vtries++) {
        var offers = null;
        try { offers = b3FreshOffers(p.level, holder, level); } catch (e0) { if (err === '') err = b3Ex(e0); }
        if (offers == null) break;
        var n = offers.size();
        for (var i = 0; i < n; i++) {
            var off = null;
            try { off = offers.get(i); } catch (e1) { continue; }
            if (off == null) continue;
            var cost = off.getBaseCostA();
            var resStack = off.getResult();
            var isExch = 0;
            try { isExch = (resStack.getItem() == FBX3_ModItems.STAR_COIN.get()) ? 1 : 0; } catch (e3) { isExch = 0; }
            if (isExch === 1) { exch++; continue; }
            var c = cost.getCount();
            samples++;
            if (mn < 0 || c < mn) mn = c;
            if (c > mx) mx = c;
            prices['' + c] = 1;
        }
    }
    b3Say(p, 'AP_B3_' + tag + '_TRADE:prof=' + prof + ':level=' + level + ':villagers=' + vtries + ':samples=' + samples
        + ':exch=' + exch + ':min=' + mn + ':max=' + mx + ':prices=' + b3PricesStr(prices) + (err === '' ? '' : ':err=' + err));
    out[prof + ':' + level] = { n: vtries, samples: samples, exch: exch, min: mn, max: mx };
    return out[prof + ':' + level];
}

function b3DoTrade(ctx, tag) {
    var p = b3P(ctx);
    var out = {};
    var err = '';
    var chipH = null; var cardH = null;
    try { chipH = FBX3_ModProfs.CHIP_TRADER; cardH = FBX3_ModProfs.CARD_TRADER; } catch (e) { err = b3Ex(e); }
    if (chipH == null) { b3Say(p, 'AP_B3_' + tag + '_TRADE_MISS:prof=chip' + (err === '' ? '' : ':err=' + err)); }
    if (cardH == null) { b3Say(p, 'AP_B3_' + tag + '_TRADE_MISS:prof=card' + (err === '' ? '' : ':err=' + err)); }
    if (chipH != null) { for (var lv = 1; lv <= 5; lv++) { b3SampleLevel(p, tag, 'chip', chipH, lv, out); } }
    if (cardH != null) { for (var lv2 = 1; lv2 <= 5; lv2++) { b3SampleLevel(p, tag, 'card', cardH, lv2, out); } }
    var chipMin = -1; var chipMax = -1; var cardMin = -1; var cardMax = -1;
    for (var k in out) {
        if (!out.hasOwnProperty(k)) continue;
        var r = out[k];
        if (r.samples <= 0) continue;
        if (k.indexOf('chip:') === 0) {
            if (chipMin < 0 || r.min < chipMin) chipMin = r.min;
            if (r.max > chipMax) chipMax = r.max;
        } else {
            if (cardMin < 0 || r.min < cardMin) cardMin = r.min;
            if (r.max > cardMax) cardMax = r.max;
        }
    }
    b3Say(p, 'AP_B3_' + tag + '_TRADEGLOBAL:chip_min=' + chipMin + ':chip_max=' + chipMax
        + ':card_min=' + cardMin + ':card_max=' + cardMax + (err === '' ? '' : ':err=' + err));
    return 1;
}

// ---- 绿宝石骰 -> 星币折算（filteredFor + mixin getCostA） -------------------
function b3DoStar(ctx, tag) {
    var p = b3P(ctx);
    var err = '';
    var diceSlots = -1; var hasBefore = 0; var hasAfter = 0;
    try {
        hasBefore = FBX3_CurioSlot.hasDiceEquipped(p) ? 1 : 0;
        var h = b3CurioHandler(p, 'dice');
        if (h != null) {
            diceSlots = h.getStacks().getSlots();
            if (hasBefore === 0 && diceSlots >= 1) {
                h.getStacks().setStackInSlot(0, new FBX3_ItemStack(FBX3_ModItems.EMERALD_DICE.get(), 1));
            }
        }
        hasAfter = FBX3_CurioSlot.hasDiceEquipped(p) ? 1 : 0;
    } catch (e) { err = b3Ex(e); }
    if (hasAfter !== 1) {
        b3Say(p, 'AP_B3_' + tag + '_STAR_SKIP:dice_slots=' + diceSlots + ':hasdice=' + hasAfter + ':before=' + hasBefore
            + (err === '' ? '' : ':err=' + err));
        return 0;
    }
    var orig = 0; var filt = -1; var exchRemoved = 0; var starOk = 0; var minStar = -1; var maxStar = -1;
    var vtry = -1;
    var costs = [];
    try {
        // 同上修：内存村民取 L3 卡商 offers（含 1 绿宝石->星币 兑换项）；重试到 size>=2
        var offers = null;
        for (vtry = 0; vtry < 80; vtry++) {
            var o0 = null;
            try { o0 = b3FreshOffers(p.level, FBX3_ModProfs.CARD_TRADER, 3); } catch (e6) { }
            if (o0 != null && o0.size() >= 2) { offers = o0; break; }
        }
        if (offers == null) throw new Error('no_L3_card_offers:' + vtry);
        orig = offers.size();
        var filtered = FBX3_EmeraldDiceTrade.filteredFor(p, offers);
        filt = filtered == null ? -1 : filtered.size();
        if (filt >= 0) {
            exchRemoved = orig - filt;
            FBX3_EmeraldDiceTrade.begin(p);
            try {
                for (var j = 0; j < filtered.size(); j++) {
                    var o = filtered.get(j);
                    var base = o.getBaseCostA();
                    var c = o.getCostA();
                    var isStar = 0;
                    try { isStar = (c.getItem() == FBX3_ModItems.STAR_COIN.get()) ? 1 : 0; } catch (e5) { isStar = 0; }
                    if (isStar === 1) {
                        starOk++;
                        var cc = c.getCount();
                        costs.push(base.getCount() + '->' + cc);
                        if (minStar < 0 || cc < minStar) minStar = cc;
                        if (cc > maxStar) maxStar = cc;
                    } else {
                        costs.push(base.getCount() + '->EMERALD:' + c.getCount());
                    }
                }
            } finally { FBX3_EmeraldDiceTrade.end(); }
        }
    } catch (e2) { err = (err === '' ? '' : err + '|') + b3Ex(e2); }
    var d5 = -1;
    try { d5 = FBX3_EmeraldDiceTrade.discount(5); } catch (e3) { }
    b3Say(p, 'AP_B3_' + tag + '_STAR:dice_slots=' + diceSlots + ':hasdice=' + hasAfter + ':orig=' + orig + ':filtered=' + filt
        + ':exch_removed=' + exchRemoved + ':star_ok=' + starOk + ':min_star=' + minStar + ':max_star=' + maxStar + ':vtry=' + vtry
        + ':costs=' + costs.join(',') + ':discount5=' + d5 + (err === '' ? '' : ':err=' + err));
    return 1;
}

// ---- state ------------------------------------------------------------------
function b3DoState(ctx, tag) {
    var p = b3P(ctx);
    var diceSlots = -1; var standSlots = -1;
    try { var hd = b3CurioHandler(p, 'dice'); if (hd != null) diceSlots = hd.getStacks().getSlots(); } catch (e) { }
    try { var hs = b3CurioHandler(p, 'stand'); if (hs != null) standSlots = hs.getStacks().getSlots(); } catch (e) { }
    b3Say(p, 'AP_B3_' + tag + '_STATE:anim=' + b3AnimOn(p) + ':gate_rem=' + b3GateRem(p) + ':sessions=' + b3SessionCount(p)
        + ':tok=' + b3Token(p) + ':cd=' + b3Cd(p) + ':dice_slots=' + diceSlots + ':stand_slots=' + standSlots
        + ':hasdice=' + (FBX3_CurioSlot.hasDiceEquipped(p) ? 1 : 0) + ':now=' + b3Now(p));
    return 1;
}

// ---- 命令注册 ---------------------------------------------------------------
ServerEvents.commandRegistry(function (event) {
    var Commands = event.commands;
    var root = Commands.literal('astralbatch3');
    function sub(name, fn) {
        root = root.then(Commands.literal(name)
            .then(Commands.argument('tag', FBX3_StringArg.word())
                .executes(function (ctx) {
                    var tag = FBX3_StringArg.getString(ctx, 'tag');
                    return b3Guard(ctx, tag, name, function () { return fn(ctx, tag); });
                })));
    }
    sub('setup', function (ctx, tag) { b3Setup(b3P(ctx), tag); return 1; });
    sub('d1', b3DoD1);
    sub('d2', b3DoD2);
    sub('d2after', b3DoD2After);
    sub('d3', b3DoD3);
    sub('d4', b3DoD4);
    sub('d5', b3DoD5);
    sub('trade', b3DoTrade);
    sub('star', b3DoStar);
    sub('state', b3DoState);
    event.register(root);
});
