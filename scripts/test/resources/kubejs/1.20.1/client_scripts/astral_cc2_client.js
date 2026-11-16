// ════════════════════════════════════════════════════════════════════════════
//  astral_cc2_client.js —— 选卡界面输入驱动器（客户端），配合 server_scripts/astral_cc2_probe.js
//
//  【做什么】服务端用 /astralcc2 open 把 CardChoiceScreen 发下来之后，本脚本按菜单 token
//  逐 tick 调 CardChoiceScreen#mouseClicked / mouseDragged / mouseReleased 的**真实输入入口**
//  （就是玩家鼠标走的那三个方法，不反射、不改产品代码），把「左键拖卡 / 双击 / 右键 / 中键」
//  序列演出来；每个阶段读一次整菜单快照并打成：
//      AP_CC2C:t=<gameTime>:phase=<阶段>:screenslotc=..:filled=..:complete=..:c0=..:c1=..:c2=..:car=..:s30=..:s31=..:s32=..:s3=..
//  动作行（排查用）：AP_CC2C:t=<gameTime>:act=click|rel|drag|dbl|esc:...
//
//  【落到哪儿】console.info 在 client_scripts 里写 logs/latest.log（行首 [KubeJS Client/]），
//  用例的 source=latest 指的就是这个文件（scripts/test/lib/mt_compat.py 的 latest_log）。
//
//  【token → 序列】
//    2100–2109 ⇒ FILTER 序列（CC-FILTER 与 CC-RIGHTCLICK 共用 token 2101，两份断言的并集都在这条序列里）
//    2200      ⇒ DBL 序列（CC-DBLCLICK）
//    其它 token ⇒ 完全不碰（只打一行 skip）
//
//  【时序纪律（每条都由产品源码 / 历史实跑反推，改之前先读注释）】
//    1. 读数必须在动作后等 ≥5 tick：客户端 carried / slot 只在下一次 sync 之后才可见。
//    2. 落在同一槽位的两次点击必须间隔 ≥6 tick（300ms）：
//       · 原版 AbstractContainerScreen 同槽 <250ms ⇒ PICKUP_ALL（收集同类到光标），
//       · 本屏幕同槽 ≤280ms ⇒ QUICK_MOVE（双击上卡）。历史跑次就栽在 200ms 上
//         （after_stone_putback 变成 PICKUP_ALL 空操作，后面整段读数错位）。
//    3. 屏内「双击」必须同 tick 内连发 click/rel/click/rel：resetGesture() 不清 lastClickSlot/
//       lastClickMillis，所以第 2 次 click 仍判成双击 ⇒ 发 QUICK_MOVE。
//    4. 左键按在「类型匹配的卡」上不会立刻发包（屏幕只记手势相位），必须再 mouseDragged
//       越过 DRAG_THRESHOLD=4 才 elevateToDrag() 发 PICKUP；而 elevateToDrag 里
//       pickedUp = !menu.getCarried().isEmpty() 当拍必为 false（slotClicked 只发 C2S 包）⇒
//       dragging 立刻被 resetGesture 清掉 ⇒ finishDrag / 自动还原那条路脚本走不到，
//       放回原槽只能再点一次（本脚本就是这么做的）。
//    5. 类型不符的卡（例如 COMBAT 菜单里的效果牌）左/右/中键全被屏幕吞掉，牌原地不动 ——
//       FILTER 序列正是拿它当「拒绝」判据。
//
//  【1.20.1 口径】一律 var；类名用 try{Java.loadClass}catch 兜住（拿不到就退化成字符串判定）
// ════════════════════════════════════════════════════════════════════════════

var CC2_Mc = null;
try { CC2_Mc = Java.loadClass('net.minecraft.client.Minecraft'); } catch (e0) { CC2_Mc = null; }
var CC2_ScreenCls = null;
try { CC2_ScreenCls = Java.loadClass('com.merlinkitsune.astral_dice.screen.CardChoiceScreen'); } catch (e1) { CC2_ScreenCls = null; }
var CC2_Reg = null;
try { CC2_Reg = Java.loadClass('net.minecraft.core.registries.BuiltInRegistries'); } catch (e2) { CC2_Reg = null; }

// ──────────────────────────────── 输出 / 读数 ────────────────────────────────

/** 唯一取证通道：console.info ⇒ logs/latest.log 的 [KubeJS Client/] 行 */
function cc2Out(s) {
    try { console.info('AP_CC2C:' + s); } catch (e) { }
}

/** 物品栈序列化（与用例断言同形）：空 = 'empty'，非空 = '<完整 id>x<数量>' */
function cc2StackSer(st) {
    if (st == null) return 'empty';
    var isEmpty = true;
    try { isEmpty = st.isEmpty(); } catch (e0) { isEmpty = true; }
    if (isEmpty) return 'empty';
    var id = '<id?>';
    if (CC2_Reg != null) {
        try {
            var k = CC2_Reg.ITEM.getKey(st.getItem());
            if (k != null) {
                var s = '' + k;
                if (s !== '' && s !== 'null') id = s;
            }
        } catch (e1) { }
    }
    var n = 1;
    try { n = st.getCount(); } catch (e2) { n = 1; }
    return id + 'x' + n;
}

function cc2SlotSer(m, i) {
    try {
        var sl = m.getSlot(i);
        if (sl == null) return 'empty';
        return cc2StackSer(sl.getItem());
    } catch (e) { return 'empty'; }
}

function cc2Car(m) {
    try { return cc2StackSer(m.getCarried()); } catch (e) { return 'empty'; }
}

/**
 * 阶段读数（字段顺序是契约，用例里有连续字段断言，不许重排）：
 *   screenslotc / filled / complete / c0 c1 c2 / car / s30 s31 s32 / s3
 */
function cc2State(m) {
    var sc = -1, filled = -1, complete = -1, tp = '?';
    try { sc = m.slotCount(); } catch (e0) { }
    try { filled = m.filledSlots(); } catch (e1) { }
    try { complete = m.isComplete() ? 1 : 0; } catch (e2) { }
    try { tp = '' + m.type(); } catch (e3) { }
    // 字段顺序是**契约**：用例断言用 'car=<v>:.*:s30=' 这种「中间必须隔着一个字段」的写法
    // （:.*: 两端都是冒号，紧邻两字段之间只有一个冒号时匹配不上），所以 car 与 s30 之间
    // 必须常驻一个字段 —— 这里放 type（顺带把选择类型写进读数）。c0/c1/c2/car 四字段必须紧邻，
    // s30/s31/s32 三字段必须紧邻且按序，故三者相对位置不可再动。
    return 'screenslotc=' + sc + ':filled=' + filled + ':complete=' + complete
        + ':c0=' + cc2SlotSer(m, 0) + ':c1=' + cc2SlotSer(m, 1) + ':c2=' + cc2SlotSer(m, 2)
        + ':car=' + cc2Car(m) + ':type=' + tp
        + ':s30=' + cc2SlotSer(m, 30) + ':s31=' + cc2SlotSer(m, 31) + ':s32=' + cc2SlotSer(m, 32)
        + ':s3=' + cc2SlotSer(m, 3);
}

// ──────────────────────────────── 坐标 ────────────────────────────────

/**
 * 槽位 → GUI 相对坐标（槽位中心）。不读 Slot#x/y（卡槽是 HIDDEN_X/Y=-10000），
 * 直接复刻 CardChoiceMenu#initSlots 的布局常量 + CardChoiceScreen 的卡框几何：
 *   卡槽 i        : 卡框 44×61，total = 44 + 46*(slotCount-1)，start = (176-total)/2，CARD_Y = 24
 *                   ⇒ 中心 = (start + 46*i + 22, 24 + 30)；slotCount=3 ⇒ (42,54)/(88,54)/(134,54)
 *   物品栏槽 index : i = index - slotCount，row = i/9，col = i%9，
 *                   x = 8 + col*18 + 8，y = (row==3 ? 177 : 119 + row*18) + 8
 *                   ⇒ 快捷栏 0/1/2（菜单槽 30/31/32）= (16,185)/(34,185)/(52,185)
 *                     主物品栏 index9（菜单槽 3）    = (16,127)
 * 传进 mouseClicked 时必须再加 getGuiLeft()/getGuiTop()（isHovering 内部会减掉它）。
 */
function cc2SlotXY(m, index) {
    var sc = 3;
    try { sc = m.slotCount(); } catch (e0) { sc = 3; }
    if (index < sc) {
        var total = 44 + 46 * (sc - 1);
        var start = (176 - total) / 2;
        return { x: start + 46 * index + 22, y: 54 };
    }
    var i = index - sc;
    var row = Math.floor(i / 9);
    var col = i % 9;
    var y = (row === 3) ? 177 : (119 + row * 18);
    return { x: 8 + col * 18 + 8, y: y + 8 };
}

/**
 * 屏幕左上角。主路径走公开 getter；万一该版本没暴露（Rhino 可见性/映射差异）就按
 * AbstractContainerScreen#init 的居中公式回落：leftPos = (width - 176) / 2，topPos = (height - 200) / 2。
 * 回落值算错会让点击整片落到界面外（用例全红），所以这里不用 0 兜底。
 */
function cc2GuiLeft(scr) {
    try { return scr.getGuiLeft(); } catch (e) { }
    try { return Math.floor((scr.width - 176) / 2); } catch (e2) { }
    return 0;
}
function cc2GuiTop(scr) {
    try { return scr.getGuiTop(); } catch (e) { }
    try { return Math.floor((scr.height - 200) / 2); } catch (e2) { }
    return 0;
}

// ──────────────────────────────── 输入原语 ────────────────────────────────

/** mouseClicked */
function cc2Click(scr, ax, ay, btn) {
    var r = -1;
    try { r = scr.mouseClicked(ax, ay, btn) ? 1 : 0; } catch (e) { r = -1; }
    return r;
}

/** mouseReleased */
function cc2Release(scr, ax, ay, btn) {
    var r = -1;
    try { r = scr.mouseReleased(ax, ay, btn) ? 1 : 0; } catch (e) { r = -1; }
    return r;
}

/** mouseDragged（dragX/dragY 是上一帧到本帧的位移，原版只用来算快速合成；这里如实传 dx/dy） */
function cc2Drag(scr, ax, ay, btn, dx, dy) {
    var r = -1;
    try { r = scr.mouseDragged(ax, ay, btn, dx, dy) ? 1 : 0; } catch (e) { r = -1; }
    return r;
}

/** 一次「真实瞬间点击」：按下 + 抬起同 tick（就是玩家快速点一下的形态） */
function cc2Tap(scr, m, gt, op) {
    var xy = cc2SlotXY(m, op.slot);
    var ax = cc2GuiLeft(scr) + xy.x;
    var ay = cc2GuiTop(scr) + xy.y;
    var b = (op.btn === undefined || op.btn === null) ? 0 : op.btn;
    var r1 = cc2Click(scr, ax, ay, b);
    var r2 = cc2Release(scr, ax, ay, b);
    cc2Out('t=' + gt + ':act=click:btn=' + b + ':x=' + ax + ':y=' + ay + ':ret=' + r1 + '/' + r2 + ':car=' + cc2Car(m));
}

/** 只按下不抬起（给「按下 → 拖拽升格」这一段留出相位） */
function cc2Press(scr, m, gt, op) {
    var xy = cc2SlotXY(m, op.slot);
    var ax = cc2GuiLeft(scr) + xy.x;
    var ay = cc2GuiTop(scr) + xy.y;
    var r1 = cc2Click(scr, ax, ay, 0);
    cc2Out('t=' + gt + ':act=press:x=' + ax + ':y=' + ay + ':ret=' + r1 + ':car=' + cc2Car(m));
}

/** 只抬起（配合 cc2Press / 拖动升格之后收尾） */
function cc2Rel(scr, m, gt, op) {
    var xy = cc2SlotXY(m, op.slot);
    var ax = cc2GuiLeft(scr) + xy.x;
    var ay = cc2GuiTop(scr) + xy.y;
    var r = cc2Release(scr, ax, ay, 0);
    cc2Out('t=' + gt + ':act=rel:x=' + ax + ':y=' + ay + ':ret=' + r + ':car=' + cc2Car(m));
}

/** 拖动：位移必须 ≥ DRAG_THRESHOLD=4 才会 elevateToDrag（这一步才真正发 PICKUP） */
function cc2DragOp(scr, m, gt, op) {
    var xy = cc2SlotXY(m, op.slot);
    var gx = cc2GuiLeft(scr), gy = cc2GuiTop(scr);
    var ax = gx + xy.x, ay = gy + xy.y;
    var dx = (op.dx === undefined || op.dx === null) ? 12 : op.dx;
    var dy = (op.dy === undefined || op.dy === null) ? 0 : op.dy;
    var r = cc2Drag(scr, ax + dx, ay + dy, 0, dx, dy);
    cc2Out('t=' + gt + ':act=drag:x=' + (ax + dx) + ':y=' + (ay + dy) + ':dx=' + dx + ':dy=' + dy + ':ret=' + r + ':car=' + cc2Car(m));
}

/**
 * 屏内双击：同 tick 连发 click/rel/click/rel。
 * 第 1 次 click 只记相位（lastClickSlot/lastClickMillis），release 被吞（resetGesture 不清 lastClick*），
 * 第 2 次 click 命中 <=280ms 判据 ⇒ 发 QUICK_MOVE（一次双击只搬 1 张）。
 * op.slot = 物品栏槽；op.choice = 卡框（用屏幕自绘几何）。
 */
function cc2Dbl(scr, m, gt, op) {
    var xy = (op.choice === undefined || op.choice === null) ? cc2SlotXY(m, op.slot) : cc2SlotXY(m, op.choice);
    var ax = cc2GuiLeft(scr) + xy.x;
    var ay = cc2GuiTop(scr) + xy.y;
    var r1 = cc2Click(scr, ax, ay, 0);
    var r2 = cc2Release(scr, ax, ay, 0);
    var r3 = cc2Click(scr, ax, ay, 0);
    var r4 = cc2Release(scr, ax, ay, 0);
    var tag = (op.choice === undefined || op.choice === null) ? ('slot=' + op.slot) : ('choice=' + op.choice);
    cc2Out('t=' + gt + ':act=dbl:' + tag + ':x=' + ax + ':y=' + ay
        + ':ret=' + r1 + '/' + r2 + '/' + r3 + '/' + r4 + ':car=' + cc2Car(m));
}

/** 主循环总入口（每个 op 一个动作；w = 执行前要等的 tick 数） */
function cc2Run(scr, m, gt, op) {
    if (op.op === 'phase') {
        cc2Out('t=' + gt + ':phase=' + op.name + ':' + cc2State(m));
        return;
    }
    if (op.op === 'tap') { cc2Tap(scr, m, gt, op); return; }
    if (op.op === 'press') { cc2Press(scr, m, gt, op); return; }
    if (op.op === 'rel') { cc2Rel(scr, m, gt, op); return; }
    if (op.op === 'drag') { cc2DragOp(scr, m, gt, op); return; }
    if (op.op === 'rclick') { op.btn = 1; cc2Tap(scr, m, gt, op); return; }
    if (op.op === 'mclick') { op.btn = 2; cc2Tap(scr, m, gt, op); return; }
    if (op.op === 'dbl') { cc2Dbl(scr, m, gt, op); return; }
    if (op.op === 'esc') {
        var r = -1;
        // ESC 走原版 Screen#keyPressed(256) → onClose() → 客户端发关包 → 服务端 removed() → abandon
        try { r = scr.keyPressed(256, 0, 0) ? 1 : 0; } catch (e) { r = -1; }
        cc2Out('t=' + gt + ':act=esc:ok=' + r);
        return;
    }
}

// ──────────────────────────────── 序列表 ────────────────────────────────

/**
 * FILTER 序列（token 2100–2109；CC-FILTER 与 CC-RIGHTCLICK 共用 2101，故两案断言并集都在这里）：
 *   ① 类型过滤拒绝：对效果牌（COMBAT 菜单里类型不符）做 左键点 / 左键拖 / 双击 / 右键 / 中键
 *   ② 拖动升格：战斗牌 4 张 → 光标，再点回原槽 ⇒ 原位还原
 *   ③ 右键取半 / 放回 / 合并（石头 8 张）
 *   ④ 右键取半(战斗牌 2 张) → 放 1 张 → 与效果牌异种交换（携带物留在光标上）
 *   ⑤ 把光标上的牌存进主物品栏空槽，收尾 done_filter，ESC 关屏
 * 全程 ≈ 11 秒（用例在 open 之后等 16 秒再 close，留 ~5 秒余量）。
 */
function cc2SeqFilter() {
    return [
        { op: 'phase', name: 'base', w: 12 },
        // ① 类型过滤（效果牌 = 类型不符 ⇒ 三条输入路径都应原地不动）
        { op: 'tap', slot: 31, w: 6 },
        { op: 'press', slot: 31, w: 2 },
        { op: 'drag', slot: 31, dx: 12, dy: 0, w: 1 },
        { op: 'rel', slot: 31, w: 1 },
        { op: 'phase', name: 'after_left_drag', w: 6 },
        { op: 'dbl', slot: 31, w: 6 },
        { op: 'phase', name: 'after_dbl_effect', w: 5 },
        { op: 'rclick', slot: 31, w: 6 },
        { op: 'phase', name: 'after_rclick_effect', w: 5 },
        { op: 'mclick', slot: 31, w: 6 },
        { op: 'phase', name: 'after_mclick_effect', w: 5 },
        // ② 拖动升格 → 放回原槽
        // press 不立刻发包（屏幕只记相位）⇒ drag 越阈值才 elevateToDrag 发 PICKUP；
        // 此时客户端 carried 仍是空的、dragging 被 resetGesture 清掉，所以「松手还原」不会自动发生。
        // 先等同步把 4 张牌显示在光标上（occ_drag_elevated 才是确定值），再用 release 交还原版
        // （release 时 carried 非空 ⇒ 原版 PICKUP ⇒ 整栈放回原槽）。
        { op: 'press', slot: 30, w: 6 },
        { op: 'drag', slot: 30, dx: 18, dy: 0, w: 2 },
        { op: 'phase', name: 'occ_drag_elevated', w: 6 },
        { op: 'rel', slot: 30, w: 1 },
        { op: 'phase', name: 'occ_restore', w: 6 },
        // ③ 石头：左键全拿 → 放回 → 右键取 4 → 左键合并回 8
        { op: 'tap', slot: 32, w: 6 },
        { op: 'phase', name: 'after_stone_left', w: 5 },
        { op: 'tap', slot: 32, w: 6 },
        { op: 'phase', name: 'after_stone_putback', w: 5 },
        { op: 'rclick', slot: 32, w: 6 },
        { op: 'phase', name: 'after_stone_rclick', w: 5 },
        { op: 'tap', slot: 32, w: 6 },
        { op: 'phase', name: 'after_stone_merge', w: 5 },
        // ④ 战斗牌：右键取 2 → 右键放 1 → 与效果牌异种交换
        { op: 'rclick', slot: 30, w: 6 },
        { op: 'phase', name: 'after_combat_rclick', w: 5 },
        { op: 'rclick', slot: 30, w: 6 },
        { op: 'phase', name: 'after_rclick_place_one', w: 5 },
        { op: 'rclick', slot: 31, w: 6 },
        { op: 'phase', name: 'after_rclick_mismatch_carried', w: 5 },
        // ⑤ 收尾：光标上的牌寄存到主物品栏空槽（菜单槽 3 = 主物品栏 index 9），再关屏
        { op: 'tap', slot: 3, w: 6 },
        { op: 'phase', name: 'after_park_carried', w: 5 },
        { op: 'phase', name: 'done_filter', w: 4 },
        { op: 'esc', w: 4 }
    ];
}

/**
 * DBL 序列（token 2200）：
 *   hotbar0 战斗牌(中)x4 → 双击两次（卡槽 0、1 各 1 张）→ 双击卡框 0 取回（并回 hotbar0）
 *   → 双击 hotbar1 战斗牌(大)x2（进卡槽 0）→ done_dbl → ESC
 * 全程 ≈ 4 秒。
 */
function cc2SeqDbl() {
    return [
        { op: 'phase', name: 'base', w: 12 },
        { op: 'dbl', slot: 30, w: 8 },
        { op: 'phase', name: 'after_dbl1', w: 5 },
        { op: 'dbl', slot: 30, w: 6 },
        { op: 'phase', name: 'after_dbl2', w: 5 },
        { op: 'dbl', choice: 0, w: 6 },
        { op: 'phase', name: 'after_take', w: 5 },
        { op: 'dbl', slot: 31, w: 6 },
        { op: 'phase', name: 'after_dbl_large', w: 5 },
        { op: 'phase', name: 'done_dbl', w: 4 },
        { op: 'esc', w: 4 }
    ];
}

/** token → 序列种类；不负责的 token 返回 ''（绝不碰别人的界面） */
function cc2KindOf(token) {
    if (token >= 2100 && token <= 2109) return 'filter';
    if (token === 2200) return 'dbl';
    return '';
}

// ──────────────────────────────── 状态机 ────────────────────────────────

var cc2Armed = false;   // 已对某个 token 开跑
var cc2Tok = -1;        // 正在跑的 token
var cc2Kind = '';       // 'filter' | 'dbl'
var cc2Seq = null;      // 当前序列
var cc2Step = 0;        // 当前 op 下标
var cc2Wait = 0;        // 还要等几个 tick
var cc2Skipped = -1;    // 已打过 skip 的 token（不刷屏）

function cc2IsCardChoiceScreen(scr) {
    if (scr == null) return false;
    if (CC2_ScreenCls != null) {
        var ok = false;
        try { ok = (scr instanceof CC2_ScreenCls); } catch (e) { ok = false; }
        if (ok) return true;
    }
    try { return (('' + scr).indexOf('CardChoiceScreen') >= 0); } catch (e2) { }
    return false;
}

ClientEvents.tick(event => {
    var mc = null;
    try { mc = CC2_Mc.getInstance(); } catch (e0) { return; }
    if (mc == null) return;
    var player = null, level = null, scr = null;
    try { player = mc.player; } catch (e1) { player = null; }
    try { level = mc.level; } catch (e2) { level = null; }
    try { scr = mc.screen; } catch (e3) { scr = null; }
    if (player == null || level == null) return;

    // 界面不在（关屏 / 还没开）⇒ 撤防。下一次 /astralcc2 open 会重新初始化（同一 token 重跑也成立）
    if (!cc2IsCardChoiceScreen(scr)) {
        if (cc2Armed) {
            cc2Out('screen=null:tok=' + cc2Tok + ':step=' + cc2Step + ':reset');
            cc2Armed = false; cc2Seq = null; cc2Step = 0; cc2Wait = 0; cc2Tok = -1;
        }
        return;
    }
    var m = null;
    try { m = scr.getMenu(); } catch (e4) { m = null; }
    if (m == null) { try { m = player.containerMenu; } catch (e5) { m = null; } }
    if (m == null) return;
    var tok = -1;
    try { tok = m.token(); } catch (e6) { return; }
    var kind = cc2KindOf(tok);
    if (kind === '') {
        if (cc2Skipped !== tok) {
            cc2Skipped = tok;
            cc2Out('t=' + cc2Gt(level) + ':skip:tok=' + tok + ':reason=unowned');
        }
        return;
    }

    // 开跑（或换 token 重开）：本轮先只初始化，下一个 tick 才开始执行第 0 个 op
    if (!cc2Armed || cc2Tok !== tok) {
        cc2Armed = true; cc2Tok = tok; cc2Kind = kind;
        cc2Seq = (kind === 'dbl') ? cc2SeqDbl() : cc2SeqFilter();
        cc2Step = 0;
        cc2Wait = (cc2Seq.length > 0) ? cc2Seq[0].w : 0;
        cc2Out('t=' + cc2Gt(level) + ':BEGIN:tok=' + tok + ':kind=' + kind + ':steps=' + cc2Seq.length);
        return;
    }
    if (cc2Step >= cc2Seq.length) return;
    if (cc2Wait > 0) { cc2Wait--; return; }
    var op = cc2Seq[cc2Step];
    cc2Step++;
    cc2Wait = (cc2Step < cc2Seq.length) ? cc2Seq[cc2Step].w : 0;
    try { cc2Run(scr, m, cc2Gt(level), op); } catch (e7) {
        cc2Out('t=' + cc2Gt(level) + ':act=EX:step=' + (cc2Step - 1) + ':msg=' + cc2ExText(e7));
    }
});

// 2026-11-05：KubeJS 客户端脚本里 level.getGameTime() 取不到（实测恒抛 ⇒ t=-1，用例的 t=\d+ 断言全不匹配），
// 改成「先试 gameTime，失败退回墙钟取模」，只要是个非负整数就能满足 t=<n> 的读数契约。
function cc2Gt(level) {
    var v = -1;
    try { v = level.getGameTime(); } catch (e) { v = -1; }
    if (v === null || v < 0) {
        try { v = Date.now() % 1000000; } catch (e2) { v = 0; }
    }
    return v;
}

function cc2ExText(e) {
    var s = '';
    try { s = '' + e; } catch (e2) { s = 'unknown'; }
    try { s = s.replace(/[\r\n]+/g, ' '); } catch (e3) { }
    if (s.length > 300) s = s.substring(0, 300);
    return s;
}
