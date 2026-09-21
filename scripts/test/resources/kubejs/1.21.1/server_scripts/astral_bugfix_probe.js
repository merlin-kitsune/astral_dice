// ════════════════════════════════════════════════════════════════════════════
//  astral_bugfix_probe.js —— 1.21.1 (NeoForge) 回归取证探针
//
//  取证通道(唯一权威 = 聊天):
//    · 有命令上下文      → ctx.source.sendFailure(Component)
//    · 无命令上下文(tick) → ServerPlayer#sendSystemMessage(Component)
//    两条路都由客户端 ChatComponent 落到 logs/latest.log 的 `[CHAT] AP_...` 行;
//    mt_launch.sh 每次运行前删除 latest.log → 天然就是「本轮增量」。
//
//  ⚠️ 已废弃的文件通道(run/<版本>/astral_probe.log):
//    KubeJS 的 Java 类过滤器拒绝 java.io / java.nio / java.lang.System / java.lang.reflect,
//    FileWriter 构造失败又被 try/catch 静默吞成「文件不存在」,把测试链故障伪装成修复无效。
//    2026-09-12 真机 diag 实证:runtime / fileWriter / nioFiles / reflectGGT 四项全 ERR。
//
//  ── 三条真机实证的 Rhino 兼容性约束 ─────────────────────────────────────────
//   1) Entity#moveTo(double,double,double) 与 moveTo(BlockPos,float,float) 在 Rhino 下
//      无法用 JS number 定序 → "ambiguous"。一律改用 setPos(double,double,double)(3 参唯一)。
//   2) level.getGameTime() 在 Rhino 直接成员查找下报 "Cannot find function getGameTime"
//      (原版源码确认 public 存在)。→ nowTick() 分层回退,**首选
//      level.getLevelData().getGameTime()**:该层与生产代码 ServerLevel#getGameTime()
//      完全同源(Level#getGameTime 实现就是 levelData.getGameTime()),
//      差值断言才有意义。若退到 server.getTickCount() 会引入 ≈世界年龄 的系统性偏差
//      (diag 实测:lvlDataGGT=14091 vs srvTickCount=427 → 赋能剩余被算成 14264 而非 600)。
//   3) mob.hurt(DamageSource,float) 在 Rhino 下报 "Cannot find function hurt"
//      (hurt 声明于 LivingEntity,直接成员查找不可见)。→ meleeHit() 链式回退,
//      首选**原版真实近战入口 Player#attack(Entity)**:与真人左键同源,内部即
//      damageSources().playerAttack(this),可完整驱动骰神赐福 / 骰战 / 跳字链。
//
//  ── 1.21.1 版本差异(仅 API 形态,取证逻辑与 1.20.1 逐条对等) ────────────────
//   · Level 访问器为**属性** p.level（与 1.20.1 相同）。
//     早期迁移假设「1.21.1 是方法 p.level()」并据此生成本文件，真机实证该形式抛
//     `TypeError: Cannot call property level in object ServerPlayer[...]. It is not a function, it is "object"`
//     —— 当时各命令链(含 diag)全部在断言前失败。2026-09-12 已全部改回属性形式。
//   · ResourceLocation.parse(...)(1.20.1 为 new ResourceLocation(...))
//   · Curios 直接 Optional#get()(1.20.1 经 LazyOptional#resolve())
//   · MobEffects 常量本身即 Holder,无 .get()
//   · 伤害跳字包为 network.DamageNumberPayload(1.20.1 为 ModNetwork$DamageNumberMessage)
//   · 本文件由一次性脚本从 1.20.1 版生成(该脚本已随 2026-09-15 的 temp/ 清理移除),请勿手工分叉修改
//
//  ── 命令一览(用例 mt_case.py 依赖这些名字与参数顺序) ──────────────────────
//    /astralprobe diag <tag>                          环境自检:API 可见性 + 时间基准 + 雷击计数
//    /astralprobe opprobe                             只读:打印 hasPermissions(2) 实测值(B6 ④)
//    /astralprobe dumpstate <tag>                     只读:转调 /astralparty dump(B6 ③)
//    /astralprobe equipslot <slotId> <itemId> <tag>
//    /astralprobe attack <entityTypeId> <tag>         生成靶子并真实近战命中(仍被 NANCY-LU-CLOAK 复用)
//    /astralprobe railguncd <tag>
//    /astralprobe railgunfriendly|railgunfriendlyread|railgunfriendlyend <tag>
//                                                    电磁炮雷击命中范围取证(自己/中立/友方/敌方)
//    ── 2026-09-14 追加(忍者主动 / 骇客末影珍珠免疫 / 骇客完全隐身 / 层数递减闪烁)──
//    /astralprobe komachicast|komachirepeat|komachicap|komachicooldown|komachicycle|komachiread <tag>
//    /astralprobe nancycloak|nancyexpire|nancystate|nancyfall <tag>
//    /astralprobe nancypearl|nancypearlctrl|nancypearlclose <tag>
//    /astralprobe decayflash|decayclear <tag>
//    ── 2026-09-15 追加(伤害效果牌法伤加成的真伤口径)──
//    /astralprobe spelltdsetup|spelltdhit <tag>
//    ── 2026-09-15 追加(《恋的规则书》「仅首次进入世界发放一次」守卫)──
//    /astralprobe guidebook <tag>                     只读:given(首登守卫附件)+ 背包内手册总本数
//    ── 2026-09-25 追加(KI-5:电击手套 3 格 AOE 伤害基准的双版本对等)──
//    /astralprobe glovebase <tag>                     生产同路读取 AOE 基准 + 护甲前/后客观对照
//
//  ── 实现约束 ─────────────────────────────────────────────────────────────
//   1. 命令注册必须在 ServerEvents.commandRegistry 回调内;执行体提取为顶层命名函数;
//      函数内一律用 var(避免 KubeJS 严格模式歧义)。
//   2. 所有 id 参数用 StringArgumentType.string()(word() 会吞掉冒号 → 命令解析失败);
//      用例侧对应写成带引号的 "astral_dice:dice"。
//   3. 执行体一律包 guard(),异常落成 AP_<TAG>_EX:...(否则被 brigadier 吞成「意外错误」)。
// ════════════════════════════════════════════════════════════════════════════

var ComponentClass = Java.loadClass("net.minecraft.network.chat.Component");
var AABBClass = Java.loadClass("net.minecraft.world.phys.AABB");
// B（2026-09-15 B2）：`Entity#setDeltaMovement` 在本版**必须**走 `Vec3` 重载 ——
// 三参 `(double,double,double)` 虽然存在于 1.21.1 的 `Entity`（反编译源 `Entity.java:3477`），
// 但 Rhino 在 `ThrownEnderpearl` 实例上按 `(number,number,number)` 解析时报
// `InternalError: Can't find method net.minecraft.world.entity.Entity.setDeltaMovement(number,number,number)`，
// 使 NANCY-LU-PEARL-IMMUNE 的 P2/P3 相位整段不执行（17/29，产品不可判）。
// `Vec3(double,double,double)` 两版本都存在且唯一（另一构造为 `Vec3(Vector3f)`，不会被 3 个 number 命中）。
var Vec3Class = Java.loadClass("net.minecraft.world.phys.Vec3");
var EntityClass = Java.loadClass("net.minecraft.world.entity.Entity");
var LivingEntityClass = Java.loadClass("net.minecraft.world.entity.LivingEntity");
var LightningBoltClass = Java.loadClass("net.minecraft.world.entity.LightningBolt");
var BuiltInRegistries = Java.loadClass("net.minecraft.core.registries.BuiltInRegistries");
var ResourceLocation = Java.loadClass("net.minecraft.resources.ResourceLocation");
var StringArg = Java.loadClass("com.mojang.brigadier.arguments.StringArgumentType");
var IntegerArg = Java.loadClass("com.mojang.brigadier.arguments.IntegerArgumentType");
var ItemStack = Java.loadClass("net.minecraft.world.item.ItemStack");
var CuriosApi = Java.loadClass("top.theillusivec4.curios.api.CuriosApi");
var ModAttachments = Java.loadClass("com.merlinkitsune.astral_dice.component.ModAttachments");
var ModEffects = Java.loadClass("com.merlinkitsune.astral_dice.effect.ModEffects");
var ModEffectRemoval = Java.loadClass(// 2026-09-17 前置库下沉:本模组效果移除通道已迁到 StarEngine Lib(消费方副本已删),故改引用库包名。
"com.merlinkitsune.starenginelib.event.ModEffectRemoval");
var EmpowerManager = Java.loadClass("com.merlinkitsune.astral_dice.item.EmpowerManager");
// 魔法箭袋(2026-09-19 验证「箭袋 × 活体书页」)：只读读取其佩戴/追踪/冷却/记录牌
var MagicQuiverChipItemClass = Java.loadClass("com.merlinkitsune.astral_dice.item.chip.MagicQuiverChipItem");

var DESC_EMPOWER = "effect.astral_dice.empower";
var BOLT_TYPE_ID = "minecraft:lightning_bolt";
/** 筹码槽默认 0,按骰子星级动态增长(AstralDiceMod: SlotTypeMessage.Builder("chip").size(0)) */
var CHIP_SLOT_MIN = 1;

// 反射句柄:getGameTime 在 Rhino 直接成员查找下不可见;java.lang.reflect 若被类过滤器
// 放行则仍能拿到同一方法(见 nowTick 回退链)。被拦时静默降级为 null。
var levelGetGameTimeMethod = null;
try {
    var LevelClass = Java.loadClass("net.minecraft.world.level.Level");
    levelGetGameTimeMethod = LevelClass.getMethod("getGameTime");
} catch (e) {
    levelGetGameTimeMethod = null;
}

// ── 输出 ──────────────────────────────────────────────────────────────────
/** 命令上下文的输出出口 */
function send(ctx, text) {
    try { ctx.source.sendFailure(ComponentClass.literal(text)); } catch (e) { /* 回显失败不影响判定 */ }
}

/** 无命令上下文(tick 回调)的输出出口:直发玩家,客户端同样记进 latest.log */
function emitTo(p, text) {
    if (p == null) return;
    try { p.sendSystemMessage(ComponentClass.literal(text)); } catch (e) { /* 忽略 */ }
}

/** 异常守卫:任何执行体抛错都落成 AP_<TAG>_EX:... 而不是被 brigadier 吞成「意外错误」 */
function guard(ctx, tag, fn) {
    try {
        return fn();
    } catch (err) {
        send(ctx, "AP_" + tag + "_EX:" + exText(err));
        return 0;
    }
}

/** 异常 → 单行文本(含前若干栈帧),便于直接定位到具体调用 */
function exText(e) {
    var msg;
    try { msg = "" + e; } catch (x) { msg = "<unprintable>"; }
    try {
        if (e != null && typeof e.getStackTrace === "function") {
            var st = e.getStackTrace();
            var n = st.length < 4 ? st.length : 4;
            for (var i = 0; i < n; i++) msg += " @ " + st[i];
        }
    } catch (x2) { /* 忽略栈获取失败 */ }
    return ("" + msg).replace(/[\r\n]+/g, " ").substring(0, 600);
}

// ── 时间基准(分层回退,首选与生产同源的那一层)────────────────────────────────
// 生产代码用 ServerLevel#getGameTime()(= levelData.getGameTime());探针必须用同一时基
// 算差值,否则 0:30 / 1:00 的断言失去意义。命中层记进 diag 的 nowTickSrc。
var nowTickSource = "unset";

function nowTickLevel(level) {
    if (level == null) { nowTickSource = "none"; return -1; }
    try { var d = level.getLevelData().getGameTime(); nowTickSource = "leveldata"; return d; } catch (e4) { }
    try {
        if (levelGetGameTimeMethod != null) {
            var b = levelGetGameTimeMethod.invoke(level);
            nowTickSource = "reflect";
            return b;
        }
    } catch (e2) { }
    try { var a = level.getGameTime(); nowTickSource = "level"; return a; } catch (e1) { }
    // 最后兜底:与生产不同源,数值含 ≈世界年龄 的偏差,diag 会以 nowTickSrc=server 明确告警
    try { var c = level.getServer().getTickCount(); nowTickSource = "server"; return c; } catch (e3) { }
    nowTickSource = "none";
    return -1;
}

function nowTick(p) {
    return p == null ? -1 : nowTickLevel(p.level);
}

// 修复后新增 API:防御式加载,缺失时返回 null(旧 jar 上脚本仍可用,只是相关指标退化为 -1)
function loadScheduler() {
    try { return Java.loadClass("com.merlinkitsune.astral_dice.event.RailgunStrikeScheduler"); }
    catch (e) { return null; }
}

function loadDamageNumberMessage() {
    try { return Java.loadClass("com.merlinkitsune.astral_dice.network.DamageNumberPayload"); }
    catch (e) { return null; }
}

// ── 通用工具 ──────────────────────────────────────────────────────────────
function itemIdOf(stack) {
    if (stack == null || stack.isEmpty()) return "empty";
    return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
}

function typeIdOf(entity) {
    if (entity == null) return "null";
    try { return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(); }
    catch (e) { return "<err>"; }
}

function findEffect(player, descId) {
    var map = player.getActiveEffectsMap();
    var it = map.values().iterator();
    while (it.hasNext()) {
        var inst = it.next();
        if (inst != null && inst.getDescriptionId() === descId) return inst;
    }
    return null;
}

/**
 * 粒子上报:0 = 该效果不产生原版药水粒子,1 = 会产生,-1 = 当前没有该效果,-2 = 读不到可见性。
 * 充能/赋能在生产代码里一律以 visible=false 构造(MobEffectInstance 第 5 参),故应为 0;
 * 图标/层数/倒计时的显示由 showIcon 决定,不受 visible 影响。
 */
function effectVis(p, descId) {
    var inst = findEffect(p, descId);
    if (inst == null) return -1;
    try { return inst.isVisible() ? 1 : 0; } catch (e) { return -2; }
}

/** 列出玩家全部 Curios 槽位 id 与槽数(诊断 chip 槽 size=0 这类「装备不进去」问题) */
function curioSlots(player) {
    try {
        var opt = CuriosApi.getCuriosInventory(player);
        if (opt == null || !opt.isPresent()) return "<no-curios>";
        var map = opt.get().getCurios();
        var out = [];
        var keys = map.keySet().iterator();
        while (keys.hasNext()) {
            var k = keys.next();
            out.push(k + "=" + map.get(k).getStacks().getSlots());
        }
        out.sort();
        return out.join(",");
    } catch (e) {
        return "<err:" + e + ">";
    }
}

function diceSlotItemId(player) {
    try {
        var opt = CuriosApi.getCuriosInventory(player);
        if (opt == null || !opt.isPresent()) return "<no-curios>";
        var handlerOpt = opt.get().getStacksHandler("dice");
        if (handlerOpt == null || !handlerOpt.isPresent()) return "<no-dice-slot>";
        var stacks = handlerOpt.get().getStacks();
        if (stacks.getSlots() <= 0) return "<empty-slot>";
        return itemIdOf(stacks.getStackInSlot(0));
    } catch (e) {
        return "<err:" + e + ">";
    }
}

function chipSlotCount(player) {
    try {
        var opt = CuriosApi.getCuriosInventory(player);
        if (opt == null || !opt.isPresent()) return -1;
        var handlerOpt = opt.get().getStacksHandler("chip");
        if (handlerOpt == null || !handlerOpt.isPresent()) return -1;
        return handlerOpt.get().getStacks().getSlots();
    } catch (e) {
        return -1;
    }
}

/**
 * 筹码栏状态的**决定性**读数:槽数 + 槽位修饰符归属 + 槽内物品 + gameTime。
 *
 * 生产侧筹码栏尺寸 = Curios 的 baseSize(本模组 chip 槽为 0)+ Σ槽位修饰符,而修饰符可能来自两个不同的写入者:
 *   - `astral_dice:chip_slots`(1.20.1 侧是固定 UUID a5d1c9e2-6f34-4b7a-8c21-0e5d9b3f7a64):
 *     本模组 2026-09-18 起的**绝对**修饰符(写入即覆盖,幂等、可自愈);
 *   - `curios:legacy`(1.20.1 侧是 UUID 0b0eabbd-4220-4e9f-bafb-34100da2bd7e):
 *     Curios `grow/shrink` 的**相对累加器**,旧实现与测试脚手架 ensureChipSlot 用它。
 * 只看槽数**无法区分「这些槽是谁给的」**(换实现前后都可能是 2),故这里把 id=amount 一并报出 ——
 * 修饰符归属是「装备骰子偶发不加筹码栏」这次修复的决定性证据。
 */
function chipStateText(player) {
    try {
        var opt = CuriosApi.getCuriosInventory(player);
        if (opt == null || !opt.isPresent()) return "no_curios";
        var handlerOpt = opt.get().getStacksHandler("chip");
        if (handlerOpt == null || !handlerOpt.isPresent()) return "no_chip_slot";
        var h = handlerOpt.get();
        var mods = [];
        var m = h.getModifiers();
        var it = m.keySet().iterator();
        while (it.hasNext()) {
            var k = it.next();
            var mod = m.get(k);
            var amt = "?";
            // 1.21.1 的 AttributeModifier 是 record(amount()),1.20.1 是 getAmount();两者都容错
            try { amt = mod.amount(); } catch (e1) {
                try { amt = mod.getAmount(); } catch (e2) {
                    try { amt = mod.amount; } catch (e3) { amt = "?"; }
                }
            }
            mods.push("" + k + "=" + amt);
        }
        mods.sort();
        var stacks = h.getStacks();
        var items = [];
        for (var i = 0; i < stacks.getSlots(); i++) {
            var s = stacks.getStackInSlot(i);
            if (!s.isEmpty()) items.push(i + ":" + itemIdOf(s) + "x" + s.getCount());
        }
        // ⚠️ 不要用 player.level.getGameTime():Rhino 在本版本派发不到该继承方法(实测
        //    `TypeError: Cannot find function getGameTime in object ServerLevel[...]`),
        //    必须走已在通过用例里验证过的 lvlDataGGT 路径(Level#getLevelData().getGameTime())。
        var gt = "?";
        try { gt = player.level.getLevelData().getGameTime(); }
        catch (e1) { try { gt = nowTick(player); } catch (e2) { gt = "?"; } }
        // 一并报出 dice 栏当前内容:筹码栏尺寸**只**由佩戴的骰子决定,复位/迁移后「0 格」到底是
        // 「没有骰子」还是「有骰子但没算出来」必须能一眼区分(二者修复方向完全相反)。
        var dice = "no_slot";
        try {
            var diceOpt = opt.get().getStacksHandler("dice");
            dice = (diceOpt != null && diceOpt.isPresent())
                ? itemIdOf(diceOpt.get().getStacks().getStackInSlot(0)) : "no_slot";
        } catch (e3) { dice = "<err>"; }
        return "gameTime=" + gt
            + ":slots=" + stacks.getSlots()
            + ":mods=[" + mods.join(",") + "]"
            + ":items=[" + items.join(",") + "]"
            + ":dice=" + dice;
    } catch (e) {
        return "<err:" + e + ">";
    }
}

/**
 * 保证 chip 槽至少有 need 个槽位。
 *
 * 生产机制:chip 槽注册为 size=0,装备骰子时由 DiceCurioItem#tryApplyChipBonus
 * 按骰子星级 grow 到目标值。测试里骰子只有基础 0 星 → 槽数为 0 → 筹码装不进去
 * (会落成 slot_overflow:chip:0)。此处按测试需要直接 grow,属**测试脚手架**,
 * 不改变被测逻辑(电磁炮的延迟与冷却判定与槽位如何获得无关)。
 */
function ensureChipSlot(player, need) {
    var opt = CuriosApi.getCuriosInventory(player);
    if (opt == null || !opt.isPresent()) return "no_curios";
    var handlerOpt = opt.get().getStacksHandler("chip");
    if (handlerOpt == null || !handlerOpt.isPresent()) return "no_chip_slot";
    var handler = handlerOpt.get();
    var cur = handler.getStacks().getSlots();
    if (cur >= need) return null;
    // ⚠️ 不要用 `handler.grow(n)`:Curios 15 已把 grow/shrink 从 ICurioStacksHandler 移除
    //    (实测 `Cannot find function grow`)⇒ 抛错后整条 `/astralprobe equipslot chip …`
    //    **一行读数都不出**(静默故障,极难定位;2026-09-20 实测)。
    //    改走**产品自己的公开入口**:它内部就是「removeModifier → addPermanentModifier → update()」,
    //    并且用**产品自己的修饰符 id** ⇒ 不会与产品每 tick 的尺寸重算叠加(用探针自有 id 会让尺寸翻倍)。
    try {
        Java.loadClass("com.merlinkitsune.astral_dice.item.dice.DiceCurioItem").refreshChipSlotCount(player);
    } catch (e0) {
        return "chip_slot_refresh:" + exText(e0);
    }
    var h2 = null;
    try { h2 = curioHandler(player, "chip"); } catch (e1) { h2 = null; }
    cur = (h2 == null) ? cur : h2.getStacks().getSlots();
    return cur >= need ? null : ("chip_slot_insufficient:" + cur + "/" + need);
}

/** 统计附近雷击实体数(辅助信号;主判据见 boltSpawnCount —— 实体存活仅数 tick,单点采样必漏) */
var lightningScanErr = "none";
function countLightning(p, radius) {
    var n = 0;
    try {
        var size = radius * 2;
        var list = p.level.getEntitiesOfClass(EntityClass,
            AABBClass.ofSize(p.position(), size, size, size));
        for (var i = 0; i < list.size(); i++) {
            if (typeIdOf(list.get(i)) === BOLT_TYPE_ID) n++;
        }
        lightningScanErr = "none";
    } catch (e) {
        lightningScanErr = exText(e);
    }
    return n;
}

function putInSlot(player, slotId, itemStack, index) {
    var opt = CuriosApi.getCuriosInventory(player);
    if (opt == null || !opt.isPresent()) return "no_curios";
    var handlerOpt = opt.get().getStacksHandler(slotId);
    if (handlerOpt == null || !handlerOpt.isPresent()) return "no_slot:" + slotId;
    var stacks = handlerOpt.get().getStacks();
    if (index >= stacks.getSlots()) return "slot_overflow:" + slotId + ":" + stacks.getSlots();
    stacks.setStackInSlot(index, ItemStack.EMPTY);
    stacks.setStackInSlot(index, new ItemStack(itemStack.getItem()));
    return null;
}

function resolveItem(itemId) {
    var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
    if (item == null || itemIdOf(new ItemStack(item)) !== itemId) return null;
    return item;
}

// ⚠️ 必须用 setPos:Entity#moveTo(double,double,double) 与 moveTo(BlockPos,float,float)
//    在 Rhino 下无法用 JS number 定序 → compile error: ambiguous。setPos 的 3 参重载唯一。
function placeAt(mob, x, y, z) {
    var nx = x + 0.0;
    var ny = y + 0.0;
    var nz = z + 0.0;
    mob.setPos(nx, ny, nz);
    return { x: nx, y: ny, z: nz };
}

/**
 * 把玩家朝向钉死为 yaw=0(朝 +Z)、pitch=0,并返回正前方 dist 格的坐标。
 *
 * 截图取证不依赖任何按键注入(服务端 teleport 会同步给客户端 → 摄像机朝向确定,
 * 正前方生成的目标必定在画面内)。
 * `aimed` 为位掩码:1=setYRot/setXRot 成功, 2=connection.teleport 成功。
 */
function aimPositiveZ(p, dist) {
    var x = p.getX() + 0.0;
    var y = p.getY() + 0.0;
    var z = p.getZ() + 0.0;
    var aimed = 0;
    try { p.setYRot(0.0); p.setXRot(0.0); aimed = aimed + 1; } catch (e1) { /* 忽略 */ }
    try { p.connection.teleport(x, y, z, 0.0, 0.0); aimed = aimed + 2; } catch (e2) { /* 忽略 */ }
    return { x: x, y: y, z: z + dist, aimed: aimed };
}

/**
 * 生成一只定点靶(无 AI、持久化),返回实体对象。
 * 靶子必须站定:按**触发瞬间目标位置**结算 3 格范围的攻击,
 * 目标一旦走开就会漏掉,导致「修复无效」的假结论。
 */
function spawnDummy(p, typeId, dist) {
    var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(typeId));
    var mob = type.create(p.level);
    if (mob == null) return null;
    var pos = aimPositiveZ(p, dist);
    placeAt(mob, pos.x, pos.y, pos.z);
    try { mob.setNoAi(true); } catch (e1) { /* 忽略 */ }
    try { mob.setPersistenceRequired(); } catch (e2) { /* 忽略 */ }
    p.level.addFreshEntity(mob);
    return mob;
}

/**
 * 造成一次「玩家近战」伤害,返回实际生效的 API 名。
 *
 * mob.hurt(DamageSource,float) 在 Rhino 下不可见(hurt 声明于 LivingEntity),
 * 故按优先级链式尝试。**判据是「目标血量真的掉了」而不是「没抛异常」**:
 * Player#attack 在攻击冷却未满时会按 f*f 缩放伤害,极端情况下伤害归零、
 * Forge 伤害事件不触发 —— 那时赐福链不会启动,必须继续回退而不是误报成功。
 * 首个真正造成伤害的路径即生效路径,后续不再尝试 → 不会重复结算。
 */
function meleeHit(p, mob) {
    var before = -1;
    try { before = mob.getHealth(); } catch (e) { before = -1; }
    function dealt() {
        if (before < 0) return -1;                 // 读不到血量:无法证伪,按成功处理
        try { return before - mob.getHealth(); } catch (e) { return -1; }
    }
    function ok() { return dealt() !== 0; }         // -1(未知)与 >0(已掉血)都算成功
    var tried = [];

    // 首选原版真实近战入口:与真人左键同源,内部走 damageSources().playerAttack(this),
    // 可完整驱动骰神赐福 / 骰战 / 跳字事件链。
    try { p.attack(mob); if (ok()) return { api: "player_attack", dealt: dealt() }; }
    catch (e1) { tried.push("player_attack:" + exText(e1)); }
    var src = p.level.damageSources().playerAttack(p);
    try { mob.hurt(src, 1.0); if (ok()) return { api: "mob_hurt", dealt: dealt() }; }
    catch (e2) { tried.push("mob_hurt:" + exText(e2)); }
    try { mob.attack(src, 1.0); if (ok()) return { api: "mob_attack", dealt: dealt() }; }
    catch (e3) { tried.push("mob_attack:" + exText(e3)); }
    try { mob.damage(1.0, src); if (ok()) return { api: "mob_damage", dealt: dealt() }; }
    catch (e4) { tried.push("mob_damage:" + exText(e4)); }
    throw new Error("no_melee_api:" + tried.join(" | "));
}

// ── 雷击生成计数(主判据)────────────────────────────────────────────────────
// 雷击实体存活仅数 tick,靠 AABB 单点采样必然漏检(历史轮次因此全判 0),故不以其瞬时数量为准。
// 改为挂钩「实体进入世界」事件做累积计数:窗口内增量 ≥1 即证明雷击**真的生成过**,
// 对「探针自己造的」与「生产代码延迟触发的」同样成立。
// KubeJS 事件 API 版本间存在差异,故三层防御式注册;失败也不影响脚本加载。
var boltSpawnCount = 0;
var boltSpawnHook = "none";
try {
    EntityEvents.spawned(BOLT_TYPE_ID, event => { boltSpawnCount++; });
    boltSpawnHook = "filtered";
} catch (e1) {
    try {
        EntityEvents.spawned(event => {
            try { if (typeIdOf(event.entity) === BOLT_TYPE_ID) boltSpawnCount++; } catch (e2) { /* 忽略 */ }
        });
        boltSpawnHook = "generic";
    } catch (e3) {
        boltSpawnHook = "failed:" + exText(e3);
    }
}

// ── 命令执行体 ────────────────────────────────────────────────────────────
function doDiag(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    function probe(name, fn) {
        var val;
        try { val = "" + fn(); } catch (e) { val = "ERR:" + exText(e); }
        send(ctx, "AP_" + tag + "_DIAG:" + name + "=" + val);
    }
    // ⚠️ 已知失败读数(2026-09-18,t23/F2 复核):`p.level.getClass()` 在 Rhino 成员查找下不可见,
    // 该读数**恒为 `ERR:Type`**(改 java.lang.Object#getClass 反射句柄亦拿不到 ⇒ `no-method`,
    // 实测两轮日志一致)。**不得**用它写断言 —— 它无法区分「探针坏了」与「探针正常」。
    // 需要「服务端层级可访问性」的成功读数请用 lvlDataGGT / getDayTime / srvTickCount。
    probe("levelClass", function () { return p.level.getClass().getName(); });
    probe("getGameTime", function () { return p.level.getGameTime(); });
    // 2026-09-18(t23, F3):选中热键栏位(0..8)。「选择期间拦截滚轮」的**唯一可观测面** ——
    // 拦截生效时滚轮不会改它;取消后滚轮会改它(正对照,见 SELECTOR-KEYS-1.21.1)。
    probe("selectedSlot", function () { return p.getInventory().selected; });
    probe("lvlDataGGT", function () { return p.level.getLevelData().getGameTime(); });
    probe("getDayTime", function () { return p.level.getDayTime(); });
    probe("reflectGGT", function () {
        return levelGetGameTimeMethod == null ? "no-method" : levelGetGameTimeMethod.invoke(p.level);
    });
    probe("srvTickCount", function () { return p.level.getServer().getTickCount(); });
    probe("nowTickSrc", function () { nowTick(p); return nowTickSource; });
    probe("nowTickVal", function () { return nowTick(p); });
    probe("damageSources", function () { return p.level.damageSources().playerAttack(p); });
    probe("fileWriter", function () { return "" + Java.loadClass("java.io.FileWriter"); });
    probe("nioFiles", function () { return "" + Java.loadClass("java.nio.file.Files"); });
    probe("consoleType", function () { return typeof console; });
    probe("curioSlots", function () { return curioSlots(p); });
    probe("diceSlot", function () { return diceSlotItemId(p); });
    probe("chipSlots", function () { return chipSlotCount(p); });
    probe("scheduler", function () { return loadScheduler() == null ? "null" : "ok"; });
    probe("dmgMessage", function () { return loadDamageNumberMessage() == null ? "null" : "ok"; });
    probe("boltSpawnHook", function () { return boltSpawnHook; });
    probe("setPosOk", function () {
        var mob = spawnDummy(p, "minecraft:pig", 2);
        return mob == null ? "spawn_failed" : "ok";
    });
    // 伤害 API 可用性:逐一在独立靶子上试,报告首个成功者(不重复结算)
    probe("meleeApi", function () {
        var mob = spawnDummy(p, "minecraft:pig", 3);
        if (mob == null) return "spawn_failed";
        var r = meleeHit(p, mob);
        return r.api + ":dealt=" + r.dealt;
    });
    // 雷击实体是否真的进入世界 + AABB 扫描是否可用
    probe("boltSpawnProbe", function () {
        var before = boltSpawnCount;
        var pos = aimPositiveZ(p, 6);
        var ok = spawnVisualBoltAt(p, pos);
        return ok + ":delta=" + (boltSpawnCount - before) + ":scan=" + countLightning(p, 16);
    });
    probe("nearbyTypes", function () {
        var list = p.level.getEntitiesOfClass(EntityClass,
            AABBClass.ofSize(p.position(), 32, 32, 32));
        var out = [];
        for (var i = 0; i < list.size(); i++) {
            var id = typeIdOf(list.get(i));
            if (out.indexOf(id) < 0) out.push(id);
        }
        out.sort();
        return out.join(",");
    });
    probe("lightningScanErr", function () { return lightningScanErr; });
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doEquipSlot(ctx, slotId, itemId, tag) {
    var p = ctx.source.getPlayerOrException();
    var item = resolveItem(itemId);
    // 清空槽位:`resolveItem("minecraft:air")` 恒为 null —— 其自检用 itemIdOf(new ItemStack(AIR)),
    // 而 itemIdOf 对空栈返回 "empty"(不是 "minecraft:air")。故 air/empty 走显式清空分支,
    // 写 ItemStack.EMPTY(等价于 putInSlot 里 new ItemStack(AIR))。回显改用 _CLEAR 以区分「装备」。
    var clearing = (item == null) && (itemId === "minecraft:air" || itemId === "air" || itemId === "empty");
    if (item == null && !clearing) { send(ctx, "AP_" + tag + "_ERR:unknown_item:" + itemId); return 0; }
    if (slotId === "chip") {
        var slotErr = ensureChipSlot(p, CHIP_SLOT_MIN);
        if (slotErr != null) { send(ctx, "AP_" + tag + "_ERR:" + slotErr); return 0; }
    }
    var err = putInSlot(p, slotId, clearing ? ItemStack.EMPTY : new ItemStack(item), 0);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    if (clearing) {
        send(ctx, "AP_" + tag + "_CLEAR:" + slotId);
    } else {
        send(ctx, "AP_" + tag + "_EQUIP:" + slotId + ":" + itemId);
    }
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 只读:打印筹码栏状态(槽数 + 槽位修饰符归属 + 槽内物品 + gameTime),见 chipStateText */
function doChipState(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    send(ctx, "AP_" + tag + "_CHIPSTATE:" + chipStateText(p));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/**
 * 主动调用 `/curios reset <自己>`(等价于管理员在 Curios 界面外重置饰品栏):它会**丢弃全部槽位修饰符**
 * —— `CurioStacksHandler#reset()` 只按数据包 base size 重建 handler,不复制修饰符。
 * 这是验证「修饰符被抹掉后能否自愈」的唯一入口:复位后由生产侧 curioTick(≤20 tick)对账恢复尺寸。
 * 本命令本身只发复位 + 前后两次读数,不写入任何尺寸。
 */
function doChipReset(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var name = "" + p.getGameProfile().getName();
    var rc = runCmd(ctx, "curios reset " + name);
    send(ctx, "AP_" + tag + "_RESET:" + rc);
    // ⚠️ 这行是**时机不确定**的快照,不得作为断言落点(平台差异,2026-09-18 两线各测一次):
    //    1.21.1 紧接 runCmd 之后的读数仍是**复位前**状态(复位效果要到下一次读数才可见);
    //    1.20.1 同一次读数**已经**是复位后状态(同步可见)。故标签取中性的 _SNAPSHOT,
    //    「复位后到底剩什么」一律以下一条独立的 chipstate 读数为准(两线都确定)。
    send(ctx, "AP_" + tag + "_SNAPSHOT:" + chipStateText(p));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doAttack(ctx, typeId, tag) {
    var p = ctx.source.getPlayerOrException();
    var mob = spawnDummy(p, typeId, 3);
    if (mob == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed:" + typeId); return 0; }
    send(ctx, "AP_" + tag + "_AIM:2");
    var hit = meleeHit(p, mob);
    send(ctx, "AP_" + tag + "_MELEE:" + hit.api + ":dealt=" + hit.dealt);
    send(ctx, "AP_" + tag + "_ATTACK:" + typeId + ":" + mob.getId());
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doRailgunCleared(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    ModAttachments.setRailgunCooldownEnd(p, 0);
    send(ctx, "AP_" + tag + "_RAILGUN_CD:0");
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 生成一枚 visualOnly(无伤害/不引燃)雷击实体:单点采样,供 diag 探针取证。 */
function spawnVisualBoltAt(p, pos) {
    try {
        var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(BOLT_TYPE_ID));
        var bolt = type.create(p.level);
        if (bolt == null) return false;
        placeAt(bolt, pos.x, pos.y, pos.z);
        bolt.setVisualOnly(true);       // 无伤害、不引燃:只验证渲染
        p.level.addFreshEntity(bolt);
        return true;
    } catch (e) {
        return false;
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  二重验证追加探针(2026-09-13):绿宝石骰子交易 + 定向爆破 AOE 口径
//    /astralprobe emeraldtrade <tag>   戴骰子 + 造流浪商人 + 复现客户端载荷 + 开交易界面
//    /astralprobe tradeclose  <tag>    关掉交易界面(取证结束后调用)
//    /astralprobe blastbonus  <tag>    定向爆破 AOE 唯一变量(效果牌伤害加成)读数
//
//  为什么这里用「流浪商人」:它天生带绿宝石报价且是 Merchant,可直接 openTradingScreen,
//  从而走真实的 sendMerchantOffers(被修复的发送路径),无需依赖村民职业/等级初始化。
// ════════════════════════════════════════════════════════════════════════════
var EmeraldDiceTradeClass = Java.loadClass("com.merlinkitsune.astral_dice.trade.EmeraldDiceTrade");
var SpellDamageRegistryClass = Java.loadClass("com.merlinkitsune.astral_dice.combat.SpellDamageRegistry");
var MobEffectInstanceClass = Java.loadClass("net.minecraft.world.effect.MobEffectInstance");
var EMERALD_DICE = "astral_dice:emerald_dice";

/** 在玩家正前方 2 格造一个流浪商人(无 AI、持久化),返回实体。 */
function spawnTrader(p) {
    var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("minecraft:wandering_trader"));
    var trader = type.create(p.level);
    if (trader == null) return null;
    var pos = aimPositiveZ(p, 2);
    placeAt(trader, pos.x, pos.y, pos.z);
    try { trader.setNoAi(true); } catch (e1) { /* 忽略 */ }
    try { trader.setPersistenceRequired(); } catch (e2) { /* 忽略 */ }
    p.level.addFreshEntity(trader);
    return trader;
}

/** 报价费用描述(1.21.1:ItemCost 形态)。 */
function costDesc(cost) {
    if (cost == null) return "null";
    try { return BuiltInRegistries.ITEM.getKey(cost.item().value()).toString() + ":" + cost.count(); }
    catch (e) { return "ERR:" + exText(e); }
}

/**
 * 绿宝石骰子交易:本体报价(绿宝石) vs 发给客户端的那一份(星币) + 成交后重发报价。
 *
 * <p>客户端载荷无法在服务端「读」,所以这里**复现生产路径**:把交换上下文打开后取
 * {@code offer.copy()} —— 这正是数据包构造时同步执行的那一步(见 MerchantOfferMixin 的复制构造注入)。
 */
function doEmeraldTrade(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var dice = resolveItem(EMERALD_DICE);
    if (dice == null) { send(ctx, "AP_" + tag + "_ERR:unknown_dice"); return 0; }
    var err = putInSlot(p, "dice", new ItemStack(dice), 0);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    send(ctx, "AP_" + tag + "_DICE:" + diceSlotItemId(p));
    var hasDice = -1;
    try { hasDice = EmeraldDiceTradeClass.hasEmeraldDice(p) ? 1 : 0; } catch (e0) { hasDice = -1; }
    send(ctx, "AP_" + tag + "_HASDICE:" + hasDice);

    var trader = spawnTrader(p);
    if (trader == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed"); return 0; }
    var offers = trader.getOffers();
    if (offers == null || offers.isEmpty()) { send(ctx, "AP_" + tag + "_ERR:no_offers"); return 0; }

    var index = -1;
    for (var i = 0; i < offers.size(); i++) {
        try {
            if (EmeraldDiceTradeClass.isEmerald(function (o) { return o.getItemCostA(); }(offers.get(i)))) { index = i; break; }
        } catch (e1) { /* 忽略 */ }
    }
    if (index < 0) { send(ctx, "AP_" + tag + "_ERR:no_emerald_offer:" + offers.size()); return 0; }
    var offer = offers.get(index);
    send(ctx, "AP_" + tag + "_OFFERS:" + offers.size() + ":idx=" + index);
    send(ctx, "AP_" + tag + "_SERVER_A:" + costDesc(function (o) { return o.getItemCostA(); }(offer)));

    var client = "err";
    EmeraldDiceTradeClass.begin(p);
    try {
        // 生产路径复现:数据包构造时同步执行的正是 offers.copy() → 每份报价的复制构造
        var copyOffer = offer.copy();
        client = costDesc(copyOffer.getItemCostA());
    } catch (e2) { client = "ERR:" + exText(e2); }
    EmeraldDiceTradeClass.end();
    send(ctx, "AP_" + tag + "_CLIENT_A:" + client);

    var xpBefore = -1; var xpAfter = -1; var offerXp = -1;
    try { xpBefore = trader.getVillagerXp(); } catch (e3) { /* 忽略 */ }
    try { offerXp = offer.getXp(); } catch (e4) { /* 忽略 */ }
    try { trader.notifyTrade(offer); } catch (e5) { send(ctx, "AP_" + tag + "_ERR:notify:" + exText(e5)); }
    try { xpAfter = trader.getVillagerXp(); } catch (e6) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_XP:" + xpBefore + ":" + xpAfter + ":offerxp=" + offerXp);
    try {
        EmeraldDiceTradeClass.resendOffers(p, trader);
        send(ctx, "AP_" + tag + "_RESEND:ok");
    } catch (e7) { send(ctx, "AP_" + tag + "_ERR:resend:" + exText(e7)); }
    try {
        trader.openTradingScreen(p, ComponentClass.literal("astral_probe_trade"), 1);
        send(ctx, "AP_" + tag + "_OPEN:ok");
    } catch (e8) { send(ctx, "AP_" + tag + "_ERR:open:" + exText(e8)); }
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 关闭交易界面(截图取证之后调用,避免界面残留在后续用例里)。 */
function doTradeClose(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    // Rhino 对部分继承来的方法查不到(实测 closeContainer 报 TypeError),按 API 链容错:
    // 关闭只是收尾动作、不构成被测行为,实际走到的路径记进读数。
    var how = "unavailable";
    try { p.closeContainer(); how = "closeContainer"; }
    catch (e1) {
        try { p.containerMenu.removed(p); how = "menuRemoved"; } catch (e2) { /* 都不可用 */ }
    }
    send(ctx, "AP_" + tag + "_CLOSE:" + how);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/**
 * 定向爆破 AOE 口径守卫:定向爆破对周围 6 格造成的伤害公式为「5 + 效果牌伤害加成」,
 * 而效果牌伤害加成只能由书签筹码与忍者立牌提供 —— **不得**被激光/板砖/轨道炮/活体书页
 * 这类「其它伤害效果牌」污染(用户 2026-09-13 裁决口径 B)。
 * 强断言:装齐其它伤害牌后 AOE_FORMULA 仍为 5;装上书签后变为 6。
 */
function doBlastBonus(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    function bonus() {
        try { return "" + SpellDamageRegistryClass.effectCardDamageBonus(p); }
        catch (e) { return "ERR:" + exText(e); }
    }
    // 本模组效果必须走 ModEffectRemoval:普通 removeEffect 会被 onModEffectRemovalPrevented 拦掉(见 doDecayFlash 注释)
    function clear(holder) { try { ModEffectRemoval.remove(p, holder); } catch (e) { /* 忽略 */ } }
    clear(ModEffects.MONSTER_LASER);
    clear(ModEffects.MONSTER_BRICK);
    clear(ModEffects.ORBITAL_STRIKE);
    clear(ModEffects.LIVING_PAGE);
    clear(ModEffects.DIRECTIONAL_BLAST);
    // 忍者立牌的「效果牌伤害增益」是附件(卸下不自动清) → 显式归零,BONUS_BASE 才确定
    try { ModAttachments.setKomachiDamageBonus(p, 0); } catch (e7) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_BONUS_BASE:" + bonus());
    try { p.addEffect(new MobEffectInstanceClass(ModEffects.DIRECTIONAL_BLAST, 2400, 0)); }
    catch (e1) { send(ctx, "AP_" + tag + "_ERR:blast:" + exText(e1)); }
    send(ctx, "AP_" + tag + "_BONUS_BLAST:" + bonus());
    try { p.addEffect(new MobEffectInstanceClass(ModEffects.MONSTER_LASER, 2400, 0)); } catch (e2) { /* 忽略 */ }
    try { p.addEffect(new MobEffectInstanceClass(ModEffects.MONSTER_BRICK, 2400, 0)); } catch (e3) { /* 忽略 */ }
    try { p.addEffect(new MobEffectInstanceClass(ModEffects.ORBITAL_STRIKE, 2400, 0)); } catch (e4) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_BONUS_OTHER_CARDS:" + bonus());
    var total = "ERR";
    try { total = "" + (5 + SpellDamageRegistryClass.effectCardDamageBonus(p)); }
    catch (e5) { total = "ERR:" + exText(e5); }
    send(ctx, "AP_" + tag + "_AOE_FORMULA:" + total);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ════════════════════════════════════════════════════════════════════════════
//  二重验证追加探针(2026-09-14):铁砧升星端到端扣费
//    /astralprobe anvilstar <diceId> <tag> [fresh]  走真实铁砧界面升 1 星并读数
//    /astralprobe anvilbags  <tag>                  袋装星币 / 数量不足 的拒收读数
//    /astralprobe anvilclose <tag>                  收尾:关掉铁砧界面
//
//  被测语义(AnvilUpgradeHandler#onAnvilUpdate;★0→★1=15、★1→★2=20、★2→★3=25 星币):
//    · 该事件只做 event.setOutput / setMaterialCost / setCost —— **真正的扣费是原版
//      AnvilMenu#onTake 干的**:材料按 repairItemCountCost 从**铁砧右槽**扣
//      (槽内数量 > 费用则 shrink,否则整槽清空),经验按 cost 扣。
//    · 所以「物品栏里的星币真的少了」只能靠「把物品栏的星币搬进右槽 → 取走结果」这条
//      真实链路证明。本探针用 player.openMenu(真实铁砧方块自带的 MenuProvider) 打开原版
//      铁砧界面,再用 AbstractContainerMenu#clicked(与原版服务端处理
//      ServerboundContainerClickPacket 的入口逐字相同)完成搬运与取件 ——
//      与真人 GUI 的唯一差别是「点击由服务端发起」,不经客户端网络包。
//    · 右槽刻意多放 3 枚(费用 + 3):既证明「只扣费用、多余原样留在右槽」,
//      也覆盖 onTake 的 shrink 分支;物品栏另留 7 枚作对照。
//    · 原版在创造模式(instabuild)下**跳过经验扣除**,故探针临时切生存走完整结算,
//      结束后还原(测试脚手架,不改被测逻辑)。
// ════════════════════════════════════════════════════════════════════════════
var ClickTypeClass = Java.loadClass("net.minecraft.world.inventory.ClickType");
var BlocksClass = Java.loadClass("net.minecraft.world.level.block.Blocks");
var GameTypeClass = Java.loadClass("net.minecraft.world.level.GameType");
var AnvilMenuClass = Java.loadClass("net.minecraft.world.inventory.AnvilMenu");
var SimpleMenuProviderClass = Java.loadClass("net.minecraft.world.SimpleMenuProvider");
// ANVIL 追加（2026-09-15 B2）：block 路径必须携带**真实方块访问**。
// `ItemCombinerMenu#removed` 的退回动作写在 `this.access.execute(() -> clearContainer(player, inputSlots))`
// 里，而 `ContainerLevelAccess.NULL` 覆写 `evaluate` 返回 Optional.empty、`execute` 是接口 default
// ⇒ `NULL.execute()` 静默 no-op（`AnvilMenu(id, inv)` 两参构造正是 NULL）。故兜底也必须用
// `ContainerLevelAccess.create(level, pos)`（与 `AnvilBlock#getMenuProvider` 同源），而不是两参构造。
var ContainerLevelAccessClass = Java.loadClass("net.minecraft.world.inventory.ContainerLevelAccess");
var ItemEntityClass = Java.loadClass("net.minecraft.world.entity.item.ItemEntity");
var ModDataComponentsClass = Java.loadClass("com.merlinkitsune.astral_dice.component.ModDataComponents");
var WeaponEnhancementClass = Java.loadClass("com.merlinkitsune.astral_dice.component.WeaponEnhancement");

var ANVIL_STAR_COIN = "astral_dice:star_coin";
var ANVIL_STAR_COIN_BAG = "astral_dice:star_coin_bag";
var ANVIL_TEST_DICE = "astral_dice:dice";
/** 与 AnvilUpgradeHandler 的 switch 逐字对应(下标 = 当前星级) */
var ANVIL_FEE_BY_STAR = [15, 20, 25];
/** 右槽多放的枚数:证「只扣费用」并覆盖 onTake 的 shrink 分支 */
var ANVIL_EXTRA_IN_SLOT = 3;
/** 物品栏对照堆:扣费后物品栏必须恰好剩这么多 */
var ANVIL_RESERVE = 7;
/** 原版铁砧菜单槽位:0/1 = 左右输入,2 = 结果(见 ItemCombinerMenuSlotDefinition) */
var ANVIL_SLOT_INPUT = 0;
var ANVIL_SLOT_ADDITIONAL = 1;
var ANVIL_SLOT_RESULT = 2;

/** 读 weapon_enhancement 星级;-1 = 空槽,-2 = 读不到 */
function anvilStarLevel(stack) {
    if (stack == null || stack.isEmpty()) return -1;
    try {
        return stack.getOrDefault(ModDataComponentsClass.WEAPON_ENHANCEMENT.get(),
            WeaponEnhancementClass.EMPTY).starLevel();
    } catch (e) { return -2; }
}

/** 骰子栈描述 "id:star"(空槽为 "empty:-1") */
function anvilDiceDesc(stack) {
    return itemIdOf(stack) + ":" + anvilStarLevel(stack);
}

/** 数量描述 "id:count"(空槽为 "empty:0") */
function anvilStackDesc(stack) {
    if (stack == null || stack.isEmpty()) return "empty:0";
    return itemIdOf(stack) + ":" + stack.getCount();
}

/** 物品栏内某 id 的总枚数 */
function anvilCountItem(p, itemId) {
    var inv = p.getInventory();
    var n = 0;
    for (var i = 0; i < inv.getContainerSize(); i++) {
        var s = inv.getItem(i);
        if (!s.isEmpty() && itemIdOf(s) === itemId) n += s.getCount();
    }
    return n;
}

/** 首个空槽:优先快捷栏(0..8),便于 HUD 截图取证;没有则 -1 */
function anvilFreeSlot(p) {
    var inv = p.getInventory();
    var i;
    for (i = 0; i < 9; i++) { if (inv.getItem(i).isEmpty()) return i; }
    for (i = 9; i < inv.getContainerSize(); i++) { if (inv.getItem(i).isEmpty()) return i; }
    return -1;
}

/** 物品栏内首个匹配 itemId 的槽;没有则 -1 */
function anvilFindSlot(p, itemId) {
    var inv = p.getInventory();
    for (var i = 0; i < inv.getContainerSize(); i++) {
        var s = inv.getItem(i);
        if (!s.isEmpty() && itemIdOf(s) === itemId) return i;
    }
    return -1;
}

/** 清掉物品栏内全部同类物品(测试脚手架:等价 give/clear,精确控制初始数量;不碰其它物品) */
function anvilClearItem(p, itemId) {
    var inv = p.getInventory();
    for (var i = 0; i < inv.getContainerSize(); i++) {
        var s = inv.getItem(i);
        if (!s.isEmpty() && itemIdOf(s) === itemId) inv.setItem(i, ItemStack.EMPTY);
    }
}

/**
 * 物品栏下标 → 铁砧菜单槽位下标。
 * 原版 ItemCombinerMenu#createInventorySlots:0/1 输入 + 2 结果,随后主物品栏 9..35 → 槽 3..29、
 * 快捷栏 0..8 → 槽 30..38。**不按 container 反查**:Rhino 下 Java 包装对象的 === 不保证引用同一性。
 */
function anvilMenuSlotOf(invIndex) {
    return invIndex < 9 ? 30 + invIndex : invIndex - 6;
}

/** 上一档若走 removed() 回退分支,containerMenu 仍指着旧铁砧菜单 → 先复位(否则 openMenu 内部 closeContainer 可能失败) */
function anvilEnsureNoOpenMenu(p) {
    try {
        if (p.containerMenu != p.inventoryMenu) {
            try { p.closeContainer(); } catch (e1) { p.containerMenu = p.inventoryMenu; }
        }
    } catch (e2) { /* 忽略 */ }
}

/**
 * 打开真实铁砧界面:放一个铁砧方块 + 走**真实方块访问**的 MenuProvider(与真人右键铁砧同源)。
 *
 * ANVIL 追加修复（2026-09-15 B2）：旧写法在一次 try 里做完「放方块 → 取 MenuProvider」，
 * 任何一步抛异常都 `provider = null` 且**不留任何原因**，实跑 100% 落到两参构造的 direct 菜单
 * （`ContainerLevelAccess.NULL`）⇒ 关界面时 `ItemCombinerMenu#removed` 的退回动作静默 no-op
 * （`ContainerLevelAccess.java:12-14` + `:32-37`），`_INV_AFTER_CLOSE` 永远 7。
 * 现在:① 每一步的失败原因都进读数 `AP_<TAG>_SRC:<src>:why=…`；
 *       ② 兜底改为 `ContainerLevelAccess.create(level,pos)`（**不是** NULL 两参构造），
 *          即便 MenuProvider 查询失败也保住 block 语义。
 */
function anvilOpenMenu(p) {
    anvilEnsureNoOpenMenu(p);
    var src = "direct";
    var why = "none";
    var provider = null;
    var pos = null;
    try {
        // ⚠️ 2026-09-15 B2 实测:Rhino 在 ServerPlayer 包装对象上**找不到 `getDirection`**
        // (`TypeError: Cannot find function getDirection`,KubeJS 2101.7.2 的方法白名单里没有它),
        // 旧写法 `p.blockPosition().relative(p.getDirection())` 因此**每次都抛异常**并静默落回
        // 两参构造(NULL)⇒ 关界面退回动作变 no-op、`_INV_AFTER_CLOSE` 永远 7。改用固定偏移坐标
        // (只依赖 BlockPos#offset,int 参数,Rhino 稳定可用)。
        pos = p.blockPosition().offset(2, 0, 0);
        p.level.setBlockAndUpdate(pos, BlocksClass.ANVIL.defaultBlockState());
        // ⚠️ 不要用 `BlockState#is(Block)` 做校验:Rhino 下 `is(Block)` 与 `is(HolderSet)`
        // 对 AnvilBlock 实参**歧义**(实测 `The choice of Java method … is … is ambiguous`)。
        // 放块成功与否由下一步的 MenuProvider 是否为空来判定即可。
        provider = p.level.getBlockState(pos).getMenuProvider(p.level, pos);
        if (provider == null && why === "none") { why = "provider_null"; }
    } catch (e1) { provider = null; why = "block_ex:" + exText(e1); }

    if (provider != null) {
        src = "block";
    } else if (pos != null) {
        // 兜底仍走真实方块访问(与 AnvilBlock 自带 MenuProvider 同源)
        try {
            var acc = ContainerLevelAccessClass.create(p.level, pos);
            provider = new SimpleMenuProviderClass(function (id, inv, pl) {
                return new AnvilMenuClass(id, inv, acc);
            }, ComponentClass.literal("astral_probe_anvil"));
            src = "block";
            why = why + "|access_create";
        } catch (e2) { provider = null; why = why + "|access_ex:" + exText(e2); }
    }

    if (provider == null) {
        provider = new SimpleMenuProviderClass(function (id, inv, pl) {
            return new AnvilMenuClass(id, inv);
        }, ComponentClass.literal("astral_probe_anvil"));
        why = why + "|null_access";
    }
    var res = p.openMenu(provider);
    if (res == null || !res.isPresent()) return null;
    // 开完再复核一次菜单真实状态:menu 的 access 类型无从直接读,故以 src 与后续关界面读数为准
    return { menu: p.containerMenu, src: src, why: why, pos: pos };
}

/** 真实搬运/取件:与原版服务端处理 ServerboundContainerClickPacket 同一入口 */
function anvilClick(p, menu, slot, button) {
    menu.clicked(slot, button, ClickTypeClass.PICKUP, p);
    try { menu.broadcastChanges(); } catch (e) { /* 同步失败不影响服务端权威状态 */ }
}

/** 关界面:铁砧输入槽的剩余材料由 ItemCombinerMenu#removed → clearContainer 退回物品栏
 *  ANVIL 追加修复（2026-09-15 B2）：**不再吞异常** —— 旧写法把 `p.closeContainer()` 的异常
 *  吞掉后只用 `menuRemoved` 标记，实跑 100% 是 `menuRemoved`，于是「哪条路径执行」永远不可判
 *  （归因文档 §4-6 指出这是修 block 路径的前置信息）。现在把异常类型/消息写进读数。 */
function anvilCloseMenu(p) {
    var how = "unavailable";
    try { p.closeContainer(); how = "closeContainer"; }
    catch (e1) {
        how = "closeContainer_ex:" + exText(e1);
        try { p.containerMenu.removed(p); how = how + "|menuRemoved"; }
        catch (e2) { how = how + "|removed_ex:" + exText(e2); }
    }
    return how;
}

/**
 * 关界面前后的**只读**诊断读数（ANVIL 追加，2026-09-15 B2；归因文档 §7-1）。
 * 打印:物品栏星币数 / 铁砧槽 0 与槽 1 的内容 / 玩家 8 格内的 ItemEntity 列表。
 * 目的:把「附加槽残留到底去哪了」从"静态无法判定"变成可读事实 —— 若关界面后有 star_coin
 * 掉落物 ⇒ 走的是 clearContainer 的 drop 分支;若槽内容仍在且物品栏没增加 ⇒ 随菜单丢弃(no-op)。
 * 纯只读,不改任何状态,也不引入新的被测逻辑。
 */
function anvilCloseState(ctx, tag, phase, p, menu) {
    var drops = [];
    try {
        var list = p.level.getEntitiesOfClass(ItemEntityClass,
            AABBClass.ofSize(p.position(), 16.0, 16.0, 16.0));
        for (var i = 0; i < list.size(); i++) {
            var e = list.get(i);
            drops.push(itemIdOf(e.getItem()) + "x" + e.getItem().getCount());
        }
    } catch (e0) { drops.push("<err:" + exText(e0) + ">"); }
    var s0 = "n/a";
    var s1 = "n/a";
    try { s0 = anvilStackDesc(menu.getSlot(ANVIL_SLOT_INPUT).getItem()); } catch (e1) { s0 = "<ex:" + exText(e1) + ">"; }
    try { s1 = anvilStackDesc(menu.getSlot(ANVIL_SLOT_ADDITIONAL).getItem()); } catch (e2) { s1 = "<ex:" + exText(e2) + ">"; }
    send(ctx, "AP_" + tag + "_" + phase + "_STATE:coins=" + anvilCountItem(p, ANVIL_STAR_COIN)
        + ":slot0=" + s0 + ":slot1=" + s1
        + ":drops=" + (drops.length === 0 ? "none" : drops.join(",")));
}

/**
 * 一档真实铁砧升星:清币 → 精确投料 → 真实点击搬运 → 真实取件 → 端到端读数。
 * fresh = 先清掉物品栏里同 id 的旧骰子(上一轮可能留下 ★3 的)并新建一颗 ★0,保证从 ★0 起测。
 */
function doAnvilStar(ctx, diceId, tag, fresh) {
    var p = ctx.source.getPlayerOrException();
    var diceItem = resolveItem(diceId);
    var coinItem = resolveItem(ANVIL_STAR_COIN);
    if (diceItem == null) { send(ctx, "AP_" + tag + "_ERR:unknown_dice:" + diceId); return 0; }
    if (coinItem == null) { send(ctx, "AP_" + tag + "_ERR:unknown_coin"); return 0; }

    anvilClearItem(p, ANVIL_STAR_COIN);
    anvilClearItem(p, ANVIL_STAR_COIN_BAG);

    if (fresh) anvilClearItem(p, diceId);
    var diceSlot = anvilFindSlot(p, diceId);
    if (diceSlot < 0) {
        diceSlot = anvilFreeSlot(p);
        if (diceSlot < 0) { send(ctx, "AP_" + tag + "_ERR:no_free_slot_dice"); return 0; }
        p.getInventory().setItem(diceSlot, new ItemStack(diceItem));
    }
    send(ctx, "AP_" + tag + "_DICE_IN:" + anvilDiceDesc(p.getInventory().getItem(diceSlot)));
    var starBefore = anvilStarLevel(p.getInventory().getItem(diceSlot));
    if (starBefore < 0 || starBefore > 2) { send(ctx, "AP_" + tag + "_ERR:bad_star:" + starBefore); return 0; }
    var fee = ANVIL_FEE_BY_STAR[starBefore];

    var feeSlot = anvilFreeSlot(p);
    if (feeSlot < 0) { send(ctx, "AP_" + tag + "_ERR:no_free_slot_fee"); return 0; }
    p.getInventory().setItem(feeSlot, new ItemStack(coinItem, fee + ANVIL_EXTRA_IN_SLOT));
    var reserveSlot = anvilFreeSlot(p);
    if (reserveSlot < 0) { send(ctx, "AP_" + tag + "_ERR:no_free_slot_reserve"); return 0; }
    p.getInventory().setItem(reserveSlot, new ItemStack(coinItem, ANVIL_RESERVE));

    send(ctx, "AP_" + tag + "_FEE:" + fee + ":" + starBefore + "->" + (starBefore + 1));
    send(ctx, "AP_" + tag + "_COINS_BEFORE:" + anvilCountItem(p, ANVIL_STAR_COIN));
    send(ctx, "AP_" + tag + "_BAGS:" + anvilCountItem(p, ANVIL_STAR_COIN_BAG));

    // 原版在创造模式(instabuild)下跳过经验扣除 → 临时切生存走完整结算,收尾还原
    var mode = "already_survival";
    if (p.getAbilities().instabuild) {
        try { p.setGameMode(GameTypeClass.SURVIVAL); mode = "forced_survival"; }
        catch (e1) { send(ctx, "AP_" + tag + "_ERR:gamemode:" + exText(e1)); return 0; }
    }
    try { p.setExperienceLevels(30); } catch (e2) { send(ctx, "AP_" + tag + "_ERR:setxp:" + exText(e2)); }

    var opened = anvilOpenMenu(p);
    if (opened == null) { send(ctx, "AP_" + tag + "_ERR:open_menu"); return 0; }
    var menu = opened.menu;
    send(ctx, "AP_" + tag + "_SRC:" + opened.src + ":why=" + opened.why);

    // 真实点击链:星币 → 右槽;骰子 → 左槽(每次 setChanged 都会重跑 createResult → AnvilUpdateEvent)
    anvilClick(p, menu, anvilMenuSlotOf(feeSlot), 0);
    anvilClick(p, menu, ANVIL_SLOT_ADDITIONAL, 0);
    anvilClick(p, menu, anvilMenuSlotOf(diceSlot), 0);
    anvilClick(p, menu, ANVIL_SLOT_INPUT, 0);
    send(ctx, "AP_" + tag + "_ANVIL_IN:" + anvilDiceDesc(menu.getSlot(ANVIL_SLOT_INPUT).getItem())
        + ":" + anvilStackDesc(menu.getSlot(ANVIL_SLOT_ADDITIONAL).getItem()));
    send(ctx, "AP_" + tag + "_RESULT:" + anvilDiceDesc(menu.getSlot(ANVIL_SLOT_RESULT).getItem()));

    // 取走结果 = 原版 AnvilMenu#onTake(按 repairItemCountCost 扣右槽 + 按 cost 扣经验)
    var xpBefore = p.experienceLevel;
    var takeHow = "clicked";
    try { anvilClick(p, menu, ANVIL_SLOT_RESULT, 0); }
    catch (e3) { takeHow = "failed"; send(ctx, "AP_" + tag + "_ERR:take:" + exText(e3)); }
    send(ctx, "AP_" + tag + "_TAKE:" + takeHow + ":" + xpBefore + ":" + p.experienceLevel);
    send(ctx, "AP_" + tag + "_RIGHT_LEFT:" + anvilStackDesc(menu.getSlot(ANVIL_SLOT_ADDITIONAL).getItem()));
    send(ctx, "AP_" + tag + "_INV_AFTER_TAKE:" + anvilCountItem(p, ANVIL_STAR_COIN));

    // 独立菜单的光标物品不会自动进物品栏 → 显式把升级后的骰子放回原槽
    var starAfter = anvilStarLevel(menu.getCarried());
    send(ctx, "AP_" + tag + "_CARRIED:" + anvilDiceDesc(menu.getCarried()));
    var putBack = "ok";
    try { anvilClick(p, menu, anvilMenuSlotOf(diceSlot), 0); }
    catch (e4) { putBack = "failed:" + exText(e4); }
    send(ctx, "AP_" + tag + "_PUTBACK:" + putBack + ":"
        + anvilDiceDesc(p.getInventory().getItem(diceSlot)));

    // 关界面:右槽剩余材料退回物品栏,物品栏星币数即「初始 − 费用」
    anvilCloseState(ctx, tag, "S" + (starBefore + 1) + "_PRE", p, menu);
    send(ctx, "AP_" + tag + "_CLOSE:" + anvilCloseMenu(p));
    send(ctx, "AP_" + tag + "_INV_AFTER_CLOSE:" + anvilCountItem(p, ANVIL_STAR_COIN));
    anvilCloseState(ctx, tag, "S" + (starBefore + 1) + "_POST", p, menu);
    send(ctx, "AP_" + tag + "_STAR:" + starBefore + ":" + starAfter);

    var restore = "n/a";
    if (mode === "forced_survival") {
        try { p.setGameMode(GameTypeClass.CREATIVE); restore = "ok"; }
        catch (e5) { restore = "failed"; }
    }
    send(ctx, "AP_" + tag + "_MODE:" + mode + ":" + restore);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/**
 * 袋装星币与「数量不足」的拒收读数:
 *   阶段 A:右槽放 1 个袋装星币 —— AnvilUpgradeHandler 只认 right.is(STAR_COIN),
 *           袋装星币不是升星材料 → 结果槽必须为空、袋一枚不扣、骰子不升星(9:1 须玩家自行转换)。
 *   阶段 B:右槽放 9 枚散装星币(< 15)→ 费用不足必须整次放弃(不是「有多少扣多少」)。
 */
function doAnvilBags(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var coinItem = resolveItem(ANVIL_STAR_COIN);
    var bagItem = resolveItem(ANVIL_STAR_COIN_BAG);
    var diceItem = resolveItem(ANVIL_TEST_DICE);
    if (coinItem == null || bagItem == null || diceItem == null) {
        send(ctx, "AP_" + tag + "_ERR:unknown_item"); return 0;
    }
    anvilClearItem(p, ANVIL_STAR_COIN);
    anvilClearItem(p, ANVIL_STAR_COIN_BAG);
    anvilClearItem(p, ANVIL_TEST_DICE);
    var diceSlot = anvilFreeSlot(p);
    if (diceSlot < 0) { send(ctx, "AP_" + tag + "_ERR:no_free_slot_dice"); return 0; }
    p.getInventory().setItem(diceSlot, new ItemStack(diceItem));
    var bagSlot = anvilFreeSlot(p);
    if (bagSlot < 0) { send(ctx, "AP_" + tag + "_ERR:no_free_slot_bag"); return 0; }
    p.getInventory().setItem(bagSlot, new ItemStack(bagItem));
    var fewSlot = anvilFreeSlot(p);
    if (fewSlot < 0) { send(ctx, "AP_" + tag + "_ERR:no_free_slot_few"); return 0; }
    p.getInventory().setItem(fewSlot, new ItemStack(coinItem, 9));
    send(ctx, "AP_" + tag + "_SETUP:" + anvilDiceDesc(p.getInventory().getItem(diceSlot))
        + ":coins=" + anvilCountItem(p, ANVIL_STAR_COIN)
        + ":bags=" + anvilCountItem(p, ANVIL_STAR_COIN_BAG));

    var a = anvilOpenMenu(p);
    if (a == null) { send(ctx, "AP_" + tag + "_ERR:open_menu_a"); return 0; }
    anvilClick(p, a.menu, anvilMenuSlotOf(bagSlot), 0);
    anvilClick(p, a.menu, ANVIL_SLOT_ADDITIONAL, 0);
    anvilClick(p, a.menu, anvilMenuSlotOf(diceSlot), 0);
    anvilClick(p, a.menu, ANVIL_SLOT_INPUT, 0);
    send(ctx, "AP_" + tag + "_BAG_IN:" + anvilStackDesc(a.menu.getSlot(ANVIL_SLOT_ADDITIONAL).getItem())
        + ":" + anvilDiceDesc(a.menu.getSlot(ANVIL_SLOT_INPUT).getItem()));
    send(ctx, "AP_" + tag + "_BAG_RESULT:" + itemIdOf(a.menu.getSlot(ANVIL_SLOT_RESULT).getItem()));
    send(ctx, "AP_" + tag + "_BAG_RIGHT:" + anvilStackDesc(a.menu.getSlot(ANVIL_SLOT_ADDITIONAL).getItem()));
    send(ctx, "AP_" + tag + "_BAG_DICE:" + anvilDiceDesc(a.menu.getSlot(ANVIL_SLOT_INPUT).getItem()));
    anvilCloseState(ctx, tag, "Z1_BAG_PRE", p, a.menu);
    send(ctx, "AP_" + tag + "_BAG_CLOSE:" + anvilCloseMenu(p));
    anvilCloseState(ctx, tag, "Z1_BAG_POST", p, a.menu);

    // ANVIL 追加修复（2026-09-15 B2 / 归因文档 §6-3）：旧写法把「物品栏里找不到骰子」直接判成
    // `lost_after_close` 并 return 0。这在 **direct 路径下必然触发**（骰子还在旧菜单的第一输入槽里，
    // 从未回到物品栏），于是 4 条 `AP_Z1_FEW_*` / `AP_Z1_DONE` 断言永远缺失 —— 那是探针自身的
    // 判据缺陷，不是产品丢物品。现在:先从上一菜单槽 0 把骰子取回（direct 认这条路），
    // 只有两条路都拿不到才报 ERR。
    var ds = anvilFindSlot(p, ANVIL_TEST_DICE);
    var recovered = "none";
    if (ds < 0) {
        try {
            var left0 = a.menu.getSlot(ANVIL_SLOT_INPUT).getItem();
            if (left0 != null && !left0.isEmpty() && itemIdOf(left0) === ANVIL_TEST_DICE) {
                var backSlot = anvilFreeSlot(p);
                if (backSlot >= 0) {
                    p.getInventory().setItem(backSlot, left0.copy());
                    ds = backSlot;
                    recovered = "from_menu_slot0";
                } else { recovered = "no_free_slot"; }
            } else { recovered = "slot0_empty"; }
        } catch (e3) { recovered = "ex:" + exText(e3); }
    }
    var fs = anvilFindSlot(p, ANVIL_STAR_COIN);
    // ⚠️ `fs`/`ds` 是**物品栏槽位下标**(anvilFindSlot)，不是枚数；星币枚数用 anvilCountItem 单独打印，
    //    避免「下标 5」被误读成「只剩 5 枚」（旧读数 AP_Z1_ERR:…:-1:5 就是这么被误读的）。
    send(ctx, "AP_" + tag + "_Z1_AFTER_CLOSE:ds=" + ds + ":fs=" + fs
        + ":coins=" + anvilCountItem(p, ANVIL_STAR_COIN)
        + ":recovered=" + recovered);
    if (ds < 0 || fs < 0) {
        send(ctx, "AP_" + tag + "_ERR:lost_after_close:" + ds + ":" + fs
            + ":coins=" + anvilCountItem(p, ANVIL_STAR_COIN) + ":recovered=" + recovered);
        return 0;
    }
    var b = anvilOpenMenu(p);
    if (b == null) { send(ctx, "AP_" + tag + "_ERR:open_menu_b"); return 0; }
    anvilClick(p, b.menu, anvilMenuSlotOf(fs), 0);
    anvilClick(p, b.menu, ANVIL_SLOT_ADDITIONAL, 0);
    anvilClick(p, b.menu, anvilMenuSlotOf(ds), 0);
    anvilClick(p, b.menu, ANVIL_SLOT_INPUT, 0);
    send(ctx, "AP_" + tag + "_FEW_IN:" + anvilStackDesc(b.menu.getSlot(ANVIL_SLOT_ADDITIONAL).getItem()));
    send(ctx, "AP_" + tag + "_FEW_RESULT:" + itemIdOf(b.menu.getSlot(ANVIL_SLOT_RESULT).getItem()));
    send(ctx, "AP_" + tag + "_FEW_TOTAL:" + (anvilCountItem(p, ANVIL_STAR_COIN)
        + b.menu.getSlot(ANVIL_SLOT_ADDITIONAL).getItem().getCount()));
    send(ctx, "AP_" + tag + "_FEW_DICE:" + anvilDiceDesc(b.menu.getSlot(ANVIL_SLOT_INPUT).getItem()));
    anvilCloseState(ctx, tag, "Z1_FEW_PRE", p, b.menu);
    send(ctx, "AP_" + tag + "_CLOSE:" + anvilCloseMenu(p));
    anvilCloseState(ctx, tag, "Z1_FEW_POST", p, b.menu);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 收尾:关闭可能残留的铁砧界面(供用例在截图取证之后调用) */
function doAnvilClose(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    send(ctx, "AP_" + tag + "_CLOSE:" + anvilCloseMenu(p));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ════════════════════════════════════════════════════════════════════════════
//  二重验证追加探针(2026-09-14):忍者主动「仅当前周期 +1」/ 骇客末影珍珠免疫 /
//  骇客主动完全隐身 / 层数递减类效果不闪烁
//
//  ⚠️ 探针改动必须**冷启动**(stop → launch)才生效 —— /kubejs reload server-scripts
//     不会重绑已注册命令的 lambda,只能用来确认脚本语法。
//
//  证据口径(全部经聊天栏 AP_<TAG>_…;输出通道见文件头):
//    · 忍者:EffectCardPeriod.getMaxAllowed/getPlayCount + 出牌轮一次性加成附件 effect_card_bonus_plays
//      + 玩家级主动冷却 sign_active_cooldown_end。「按主动」= BaseSignItem.performSkillForCurio
//      —— 与客户端按键经 SignActivatePayload(1.20.1 为 ModNetwork)的服务端处理同一入口。
//    · 骇客:附件 nancy_lu_ender_pearl_immune_until / nancy_lu_hidden_until
//      + 原版隐身实例 isVisible() + 原版受伤反馈字段 hurtTime / invulnerableTime / hurtMarked
//      (hurtMarked 即 markHurt 置位、供击退同步消费的标记)。
//    · 闪烁:治愈/标记/赋能三个效果实例的 amplifier/duration/ambient + endsWithin(200)
//      —— 原版 Gui#renderEffects 的闪烁条件恰为「非 ambient 且 endsWithin(200)」。
//    · 「主动不释放」的可观测结果是「附件不变 + 不进入主动冷却」;主动本身不消耗任何资源。
// ════════════════════════════════════════════════════════════════════════════

var GameplayConstantsClass = Java.loadClass(// 2026-09-17 前置库下沉:共享常量已迁到 StarEngine Lib(消费方副本已删),故改引用库包名。
"com.merlinkitsune.starenginelib.component.GameplayConstants");
var EffectCardPeriodClass = Java.loadClass("com.merlinkitsune.astral_dice.item.card.EffectCardPeriod");
var BaseSignItemClass = Java.loadClass("com.merlinkitsune.astral_dice.item.sign.BaseSignItem");
var NancyLuSignItemClass = Java.loadClass("com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem");
var HealingManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.item.HealingManager");
var MarkManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.item.MarkManager");
var MobEffectsClass = Java.loadClass("net.minecraft.world.effect.MobEffects");
var ThrownEnderpearlClass = Java.loadClass("net.minecraft.world.entity.projectile.ThrownEnderpearl");
// C1（2026-09-15 B2）：电击手套武装状态的生产入口
var ElectricGloveClass = Java.loadClass("com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem");
// C4（2026-09-15 B2）：末影骰保命入口（装备判定）
var EnderDiceHandlerClass = Java.loadClass("com.merlinkitsune.astral_dice.event.EnderDiceHandler");
// C4（2026-09-15 B3）：致死伤害源是否属 BYPASSES_INVULNERABILITY 的只读取证入口
var DamageTypeTagsClass = Java.loadClass("net.minecraft.tags.DamageTypeTags");

var DESC_INVIS = "effect.minecraft.invisibility";
var DESC_GLOW = "effect.minecraft.glowing";
var DESC_SPEED = "effect.minecraft.speed";
var DESC_LIVING = "effect.astral_dice.living_page";
var DESC_FATE = "effect.astral_dice.fate_guidance";
var DESC_HEAL = "effect.astral_dice.healing";
var DESC_MARK = "effect.astral_dice.marked";
var DESC_HACK = "effect.astral_dice.nancy_lu_hack";
var KOMACHI_SIGN_ID = "astral_dice:komachi_sign";
var NANCY_LU_SIGN_ID = "astral_dice:nancy_lu_sign";
/** 施加的 FALL 伤害点数(固定值,便于断言 delta) */
var FALL_DAMAGE_AMOUNT = 5.0;
/** 末影珍珠免疫窗口:读生产常量(20 tick)而非硬编码,防止两版本漂移 */
var PEARL_IMMUNE_TICKS = NancyLuSignItemClass.ENDER_PEARL_IMMUNE_TICKS;
/** 完全隐身持续(生产常量 600 tick = 30 秒) */
var NANCY_HIDDEN_TICKS = 600;
/** 闪烁窗口:原版 Gui#renderEffects 用 200 tick 判定「即将到期」的图标 alpha 脉冲 */
var FLICKER_WINDOW_TICKS = 200;

/** 取模组效果对象(1.21.1:效果常量本身就是 Holder,可直接用于 MobEffectInstance 构造) */
function fxLivingPage() { return ModEffects.LIVING_PAGE; }
function fxFateGuidance() { return ModEffects.FATE_GUIDANCE; }
function fxEmpower() { return ModEffects.EMPOWER; }
function fxHealing() { return ModEffects.HEALING; }
function fxMarked() { return ModEffects.MARKED; }
function fxHack() { return ModEffects.NANCY_LU_HACK; }

/** Curios 槽位 handler(1.21.1:getCuriosInventory 直接返回 Optional) */
function curioHandler(player, slotId) {
    try {
        var opt = CuriosApi.getCuriosInventory(player);
        if (opt == null || !opt.isPresent()) return null;
        var h = opt.get().getStacksHandler(slotId);
        if (h == null || !h.isPresent()) return null;
        return h.get();
    } catch (e) {
        return null;
    }
}

/** 清空某 Curios 槽位的全部格子(测试脚手架:消除上一条用例留下的筹码/立牌;不碰其它槽位) */
function clearCurioSlots(player, slotId) {
    var h = curioHandler(player, slotId);
    if (h == null) return "no_slot:" + slotId;
    var stacks = h.getStacks();
    for (var i = 0; i < stacks.getSlots(); i++) stacks.setStackInSlot(i, ItemStack.EMPTY);
    return null;
}

/** 把立牌放进 stand 槽(幂等:putInSlot 先清空再放,顺带走 onUnequip 的清理语义) */
function equipSign(player, itemId) {
    var item = resolveItem(itemId);
    if (item == null) return "unknown_item:" + itemId;
    return putInSlot(player, "stand", new ItemStack(item), 0);
}

/**
 * 出牌周期与忍者主动状态归零(每条命令都从同一基线起测)。
 * 必须与生产侧 EffectCardPeriod.clearRoundBonuses 的清理项**逐项对齐**(bonus / 可口糖果 /
 * 探天卫星 / 活体书页本周期累计),否则读数会依赖前序用例残留、绝对期望变得顺序相关。
 */
function resetEffectCardCycle(player) {
    ModAttachments.setEffectCardBonusPlays(player, 0);
    ModAttachments.setEffectCardPlayCount(player, 0);
    ModAttachments.setEffectCardCooldownEnd(player, 0);
    ModAttachments.setSignActiveCooldownEnd(player, 0);
    // 立牌主动"三态化"(第二批)新增的 5 个玩家级键:必须一并归零,否则锁定态会跨用例残留、
    // 使"按主动"走进锁定分支(读数变成顺序相关)
    resetActiveLock(player);
    ModAttachments.setCandyChipPlayBonusActive(player, false);
    ModAttachments.setSatellitePlayBonusActive(player, false);
    ModAttachments.setLivingPageCycleBonus(player, 0);
}

/** 清空立牌主动"锁定(生效中)"态与减免池(测试脚手架;不碰主动冷却本身) */
function resetActiveLock(player) {
    ModAttachments.setSignActiveLockSign(player, "");
    ModAttachments.setSignActiveLockEnd(player, 0);
    ModAttachments.setSignActiveReductionPool(player, 0);
    ModAttachments.setSignActiveLockGraceEnd(player, 0);
    ModAttachments.setSignActiveLockPlayed(player, false);
    // [SIGNLOCK-BASE-P1] 第 6 个键(2026-09-27 离线补偿):必须一并归零 ——
    // 否则「合成脚手架」直接写 sign_active_lock_end(不经 beginActiveLock)时,上一用例留下的
    // 陈值 lastSeen 会被下一拍当成一次离线 gap 误补偿,把上界凭空后移(假红/假绿)。
    // 真实施放路径不受影响(beginActiveLock 必写 now;endLockAndStartCooldown 必清 0)。
    ModAttachments.setSignActiveLockLastSeen(player, 0);
}

/** 当前锁定态标记("" = 未锁定) */
function lockSignId(player) {
    var id = ModAttachments.getSignActiveLockSign(player);
    return id == null ? "" : String(id);
}

/** 主动技能锁定(生效中)态剩余 tick(0 = 未锁定) */
function lockRemaining(player) {
    var end = ModAttachments.getSignActiveLockEnd(player);
    var now = nowTick(player);
    if (end <= 0 || now < 0) return 0;
    return end - now;
}

/** 忍者宽限剩余 tick(0 = 无宽限/已失效) */
function lockGraceRemaining(player) {
    var end = ModAttachments.getSignActiveLockGraceEnd(player);
    var now = nowTick(player);
    if (end <= 0 || now < 0) return 0;
    return end - now;
}

/** 摘下两个「临时出牌数来源」效果(附件已由 resetEffectCardCycle 归零) */
function clearExtraPlayEffects(player) {
    try { ModEffectRemoval.remove(player, fxLivingPage()); } catch (e1) { /* 忽略 */ }
    try { ModEffectRemoval.remove(player, fxFateGuidance()); } catch (e2) { /* 忽略 */ }
}

/** 主动技能冷却剩余 tick(0 = 未在冷却) */
function signCooldownRemaining(player) {
    var end = ModAttachments.getSignActiveCooldownEnd(player);
    var now = nowTick(player);
    if (end <= 0 || now < 0) return 0;
    return end - now;
}

// ── 忍者:主动「一次性 +1」(仅当前出牌轮;无银行、无来源注册)──────────────
/**
 * 正常释放(第二批「三态化」新语义):归零基线 → 按主动。断言链 = 附件 0→1、出牌上限 +1、
 * **进入锁定(生效中)态且不立即起主动冷却**(忍者的锁定跟随出牌周期,宽限 1:00 起算),
 * 再把宽限到期刻推成过期并驱动玩家级 tick ⇒ 期内未出任何效果牌 ⇒ 强制重置出牌状态并按基准起冷却。
 * 出牌上限读数用「前后差值」而非绝对值(不依赖其它用例是否留下固定来源筹码)。
 */
function doKomachiCast(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    clearCurioSlots(p, "chip");
    clearExtraPlayEffects(p);
    resetEffectCardCycle(p);
    var err = equipSign(p, KOMACHI_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    var maxBefore = EffectCardPeriodClass.getMaxAllowed(p);
    send(ctx, "AP_" + tag + "_BEFORE:max=" + maxBefore
        + ":extra=" + EffectCardPeriodClass.getBonusPlays(p)
        + ":cd=" + (signCooldownRemaining(p) > 0 ? 1 : 0)
        + ":count=" + EffectCardPeriodClass.getPlayCount(p)
        + ":living=" + (findEffect(p, DESC_LIVING) != null ? 1 : 0)
        + ":fate=" + (findEffect(p, DESC_FATE) != null ? 1 : 0));
    BaseSignItemClass.performSkillForCurio(p);
    var maxAfter = EffectCardPeriodClass.getMaxAllowed(p);
    var extraAfter = EffectCardPeriodClass.getBonusPlays(p);
    var cd = signCooldownRemaining(p);
    var lockSign = lockSignId(p);
    var graceLeft = lockGraceRemaining(p);
    send(ctx, "AP_" + tag + "_AFTER:max=" + maxAfter + ":extra=" + extraAfter
        + ":cd=" + (cd > 0 ? 1 : 0) + ":locked=" + (lockSign === KOMACHI_SIGN_ID ? 1 : 0));
    send(ctx, "AP_" + tag + "_DELTA:max=+" + (maxAfter - maxBefore)
        + ":extra=" + extraAfter + ":cd=" + (cd > 0 ? "started" : "none")
        + ":locked=" + (lockSign === "" ? "none" : lockSign));
    // 宽限 1:00 到点(期内未出任何效果牌)⇒ 玩家级 tick 强制重置出牌状态并起主动冷却
    var now = nowTick(p);
    ModAttachments.setSignActiveLockGraceEnd(p, (now > 1 ? now : 1) - 1);
    // [SIGNLOCK-BASE-P2] 把宽限写进过去前,先刷新离线补偿基准(保持「没有离线」的语义);
    // 真实离线补偿路径不经过本脚手架,故不改变被测语义。
    ModAttachments.setSignActiveLockLastSeen(p, now > 1 ? now : 1);
    BaseSignItemClass.tickSignActiveLock(p);
    var cdAfterGrace = signCooldownRemaining(p);
    var lockAfterGrace = lockSignId(p);
    send(ctx, "AP_" + tag + "_GRACE:cd=" + (cdAfterGrace > 0 ? 1 : 0)
        + ":locked=" + (lockAfterGrace === "" ? 0 : 1));
    var ok = (extraAfter === 1) && (maxAfter === maxBefore + 1) && (cd === 0)
        && (lockSign === KOMACHI_SIGN_ID) && (graceLeft > 0)
        && (cdAfterGrace > 0) && (lockAfterGrace === "");
    send(ctx, "AP_" + tag + "_RELEASED:" + (ok ? 1 : 0));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/**
 * 本周期已生效时再按主动:不得释放,且**不得进入冷却**。
 * 构造:先清掉上一条用例可能留下的**锁定(生效中)**态(新判定置于冷却分支之前,残留锁会让本用例
 * 走进锁定分支而测不到"已授予过"的守卫);再置附件 1(本周期已生效),并把主动冷却结束时刻置为
 * 「已过期但非 0」——既能越过 performSkill 的冷却分支(走到 handleUse 的 used 守卫),
 * 又能用「该字段是否被重新写成未来时刻」判定冷却有没有被起算。
 */
function doKomachiRepeat(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var err = equipSign(p, KOMACHI_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    clearExtraPlayEffects(p);
    resetActiveLock(p);
    var seed = 0;
    if (EffectCardPeriodClass.getBonusPlays(p) <= 0) {
        ModAttachments.setEffectCardBonusPlays(p, 1);
        seed = 1;
    }
    var now = nowTick(p);
    var past = (now > 1 ? now : 1) - 1;
    ModAttachments.setSignActiveCooldownEnd(p, past);
    ModAttachments.setEffectCardCooldownEnd(p, 0);
    ModAttachments.setEffectCardPlayCount(p, 0);
    var maxBefore = EffectCardPeriodClass.getMaxAllowed(p);
    send(ctx, "AP_" + tag + "_BEFORE:seed=" + seed
        + ":extra=" + EffectCardPeriodClass.getBonusPlays(p)
        + ":max=" + maxBefore + ":cd=" + past);
    BaseSignItemClass.performSkillForCurio(p);
    var extraAfter = EffectCardPeriodClass.getBonusPlays(p);
    var cdAfter = ModAttachments.getSignActiveCooldownEnd(p);
    var maxAfter = EffectCardPeriodClass.getMaxAllowed(p);
    send(ctx, "AP_" + tag + "_AFTER:extra=" + extraAfter + ":max=" + maxAfter
        + ":cd_same=" + (cdAfter === past ? 1 : 0)
        + ":cd_future=" + (cdAfter > nowTick(p) ? 1 : 0));
    var rejected = (extraAfter === 1) && (cdAfter === past) && (maxAfter === maxBefore);
    send(ctx, "AP_" + tag + "_REJECTED:" + (rejected ? 1 : 0));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ════════════════════════════════════════════════════════════════════════════
//  2026-09-17(t18 门控收口):目标选择器类立牌「前置门控」三态读数
//    · 门控态(按下主动键):只开启选择会话 + 登记待执行记录;不发牌 / 不进冷却 / 不施效
//    · 取消(未选)态      :会话与待执行记录一并清除 ⇒ 该次主动等同「未使用」(同上三项皆无)
//    · 确认(合法目标)态  :效果 + 玩家级冷却 + 电流核心充能 + 风扇筹码发牌(恢复点)
//  入口与生产同路:按主动 = BaseSignItem.performSkillForCurio;确认/取消 = TargetSelectionManager
//  {confirm,cancel}(即两个网络载荷处理器的同一入口);不依赖客户端按键与选择 UI。
// ════════════════════════════════════════════════════════════════════════════
var MOSES_SIGN_ID = "astral_dice:moses_sign";
var HAND_FAN_BIG_CHIP_ID = "astral_dice:hand_fan_big_chip";
var TargetSelectionManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.target.TargetSelectionManager");
var SignSelectionGateClass = Java.loadClass("com.merlinkitsune.astral_dice.target.SignSelectionGate");
var EffectCardUtilClass = Java.loadClass("com.merlinkitsune.astral_dice.item.card.EffectCardUtil");

// ⚠️ 池对象**必须先复制成 java.util.ArrayList** 再做任何成员调用（2026-09-18 t27，与 1.20.1 侧同形）：
// `RandomCardHandler.getCardPool` 的 `items.stream().map(ItemStack::new).toList()` 在 Java 16+ 返回 JDK
// **包私有**内部类 `java.util.ImmutableCollections$ListN`。1.20.1 侧的旧 Rhino（2001.2.3-build.10）成员
// 分派经 `MemberBox` 反射调用 ⇒ 直接抛 `IllegalAccessException`（本线 rhino-2101.2.8-build.91 已修该路径，
// 故本线原本就 PASS）；两侧探针保持同源，故此处**同样**先复制再读。修法见 1.20.1 侧的完整注释：
// `new ArrayList(pool)` 的构造函数声明在公开类上、复制在 Java 内部完成 ⇒ 不经 Rhino 成员分派。
// **不得**改成 `pool.toArray()/iterator()/get()`（同样声明在包私有类上）。
var ArrayListClass = Java.loadClass("java.util.ArrayList");

/** 背包内「随机效果牌池」的卡牌总数 —— 发牌(FanBigChip)的唯一观测口径,池取自生产同一入口 */
function countEffectCards(p) {
    var inv = p.getInventory();
    var pool = new ArrayListClass(EffectCardUtilClass.getRandomEffectCardPool());
    var n = 0;
    for (var i = 0; i < inv.getContainerSize(); i++) {
        var st = inv.getItem(i);
        if (st.isEmpty()) continue;
        // ⚠️ 池内物品比对**必须走注册名**，禁止写 `st.is(pool.get(j).getItem())`：
        // ItemStack 有 5 个同元 `is(...)` 重载（ItemStack.java:339 is(TagKey) / :343 is(Item) /
        // :347 is(Predicate) / :351 is(Holder) / :355 is(HolderSet)），Rhino 得靠 JS 侧类型信息挑一个，
        // 实测在 `is(HolderSet)` 与 `is(Item)` 之间判不出来 ⇒
        // `InternalError: The choice of Java method ItemStack.is matching JavaScript argument types
        // (com.merlinkitsune.astral_dice.item.card.EffectCardItem) is ambiguous`，
        // 该函数一抛，整段 AP_G1_* 读数缺失（SELECTOR-GATE-1.21.1 曾 9 断言仅 2 PASS；归因见
        // TESTING-SPEC 附录 A）。注册表 key 与 item 一一对应，故「同名」等价于
        // `is(Item)`（其方法体就是 `this.getItem() == item`，ItemStack.java:343-345）；
        // itemIdOf 是本文件既有工具（:182），比较的是字符串 ⇒ 零重载、零歧义。
        var stId = itemIdOf(st);
        for (var j = 0; j < pool.size(); j++) {
            if (stId === itemIdOf(pool.get(j))) { n += st.getCount(); break; }
        }
    }
    return n;
}

/**
 * 枪匠(选择器类)立牌门控三态一条龙:门控 → 取消 → 确认。
 * 断言读数(AP_<tag>_…):GATED{ session=1, cd=0, cards_delta=0, armed=1 }、
 * CANCEL{ session=0, cd=0, cards_delta=0, armed=0 }、
 * CONFIRM{ mob=1, session=0, broken=1, cd=1, cards_delta=1, armed=0 } ⇒ VERDICT:1。
 */
function doSignGate(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    clearCurioSlots(p, "chip");
    resetEffectCardCycle(p);                 // 主动冷却 + 锁定(生效中)态 + 出牌周期附件归零
    var slotErr = ensureChipSlot(p, 1);      // chip 槽 base=0(尺寸由骰子星级给出):测试脚手架直接给 1 格
    if (slotErr != null) { send(ctx, "AP_" + tag + "_ERR:" + slotErr); return 0; }
    var signErr = equipSign(p, MOSES_SIGN_ID);
    if (signErr != null) { send(ctx, "AP_" + tag + "_ERR:" + signErr); return 0; }
    var chipErr = putInSlot(p, "chip", new ItemStack(resolveItem(HAND_FAN_BIG_CHIP_ID)), 0);
    if (chipErr != null) { send(ctx, "AP_" + tag + "_ERR:" + chipErr); return 0; }
    var cards0 = countEffectCards(p);
    send(ctx, "AP_" + tag + "_PREP:chipSlots=" + chipSlotCount(p) + ":cards=" + cards0);

    // ① 门控态:按下主动键 —— 只开启会话 + 登记待执行记录
    BaseSignItemClass.performSkillForCurio(p);
    var gSession = TargetSelectionManagerClass.isSelecting(p) ? 1 : 0;
    var gArmed = SignSelectionGateClass.isArmed(p) ? 1 : 0;
    var gCd = signCooldownRemaining(p) > 0 ? 1 : 0;
    var gCards = countEffectCards(p) - cards0;
    send(ctx, "AP_" + tag + "_GATED:session=" + gSession + ":cd=" + gCd
        + ":cards_delta=" + gCards + ":armed=" + gArmed);

    // ② 取消(未选)⇒ 等同「未使用」:会话与记录都被清掉,冷却/发牌/施效一个都没有
    var token1 = TargetSelectionManagerClass.sessionTokenForTests(p);
    TargetSelectionManagerClass.cancel(p, token1);
    var cSession = TargetSelectionManagerClass.isSelecting(p) ? 1 : 0;
    var cArmed = SignSelectionGateClass.isArmed(p) ? 1 : 0;
    var cCd = signCooldownRemaining(p) > 0 ? 1 : 0;
    var cCards = countEffectCards(p) - cards0;
    send(ctx, "AP_" + tag + "_CANCEL:token_seen=" + (token1 > 0 ? 1 : 0) + ":session=" + cSession
        + ":cd=" + cCd + ":cards_delta=" + cCards + ":armed=" + cArmed);

    // ③ 确认合法敌对目标 ⇒ 施效 + 冷却 + 发牌(恢复点)
    BaseSignItemClass.performSkillForCurio(p);
    var mob = spawnDummy(p, "minecraft:zombie", 3);
    var mobId = (mob == null) ? -1 : mob.getId();
    TargetSelectionManagerClass.confirm(p, TargetSelectionManagerClass.sessionTokenForTests(p), mobId);
    var kSession = TargetSelectionManagerClass.isSelecting(p) ? 1 : 0;
    var kArmed = SignSelectionGateClass.isArmed(p) ? 1 : 0;
    var kBroken = (mob == null) ? -1 : (mob.hasEffect(ModEffects.MOSES_BROKEN) ? 1 : 0);
    var kCd = signCooldownRemaining(p);
    var kCards = countEffectCards(p) - cards0;
    send(ctx, "AP_" + tag + "_CONFIRM:mob=" + (mobId > 0 ? 1 : 0) + ":session=" + kSession
        + ":broken=" + kBroken + ":cd=" + (kCd > 0 ? 1 : 0) + ":cards_delta=" + kCards
        + ":armed=" + kArmed);

    var ok = (gSession === 1) && (gArmed === 1) && (gCd === 0) && (gCards === 0)
        && (cSession === 0) && (cArmed === 0) && (cCd === 0) && (cCards === 0)
        && (mobId > 0) && (kSession === 0) && (kArmed === 0) && (kBroken === 1)
        && (kCd > 0) && (kCards === 1);
    send(ctx, "AP_" + tag + "_VERDICT:" + (ok ? 1 : 0));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// 可选脚手架:注册一个「常态关闭」的临时出牌数来源,用来把出牌上限真正推到封顶 9。
// 只有在 Rhino 能实现该接口(JavaAdapter)且预检通过时才注册 —— 预检不通过就完全不注册,
// 绝不把可能抛错的实现放进全局来源表(那会污染后续所有出牌判定)。
var probeExtraSourceArmed = false;
var probeExtraSourceState = "not_tried";

/**
 * 尝试用 JS 对象实现 EffectCardPeriod$ExtraPlaySource(两种 Rhino 写法都试),
 * 返回通过预检的实现;两种都不可用则返回 null(调用方不注册任何东西)。
 */
function makeProbeExtraSource(p) {
    var body = { isActive: function (pl) { return probeExtraSourceArmed === true; } };
    var Iface = Java.loadClass("com.merlinkitsune.astral_dice.item.card.EffectCardPeriod$ExtraPlaySource");
    function checked(impl) {
        if (impl == null) return null;
        if (impl.isActive(p)) { probeExtraSourceState = "precheck_isActive_true"; return null; }
        if (impl.amount() !== 1) { probeExtraSourceState = "precheck_amount_not_1"; return null; }
        return impl;
    }
    try {
        var one = checked(new Iface(body));
        if (one != null) { probeExtraSourceState = "form:kubejs"; return one; }
        return null;
    } catch (e1) { /* 落到 Rhino 标准 JavaAdapter 写法 */ }
    try {
        if (typeof JavaAdapter === "function") {
            var two = checked(new JavaAdapter(Iface, body));
            if (two != null) { probeExtraSourceState = "form:javaadapter"; return two; }
        }
    } catch (e2) { probeExtraSourceState = "unavailable:" + exText(e2); }
    return null;
}

function tryInstallProbeSources(p) {
    var installed = 0;
    var form = "not_tried";
    for (var i = 0; i < 2; i++) {
        try {
            var impl = makeProbeExtraSource(p);
            if (impl == null) {
                if (probeExtraSourceState === "not_tried") probeExtraSourceState = "unavailable:no_adapter";
                return;
            }
            form = probeExtraSourceState;
            EffectCardPeriodClass.registerTemporarySource(impl);
            installed++;
        } catch (e) {
            probeExtraSourceState = "unavailable:" + exText(e);
            return;
        }
    }
    probeExtraSourceState = "ok:" + installed + ":" + form;
}

/**
 * 出牌上限已达封顶(9)时按主动:必须不释放且不进入冷却。
 * 构造与实测口径:挂上大背包 + 忍术飞镖(固定 2)与命运的指引效果 + 可口糖果 + 探天卫星
 * (临时 3)⇒ 干净基线 extra = 5 → 上限 6(_FILL:6;活体书页自改版起是"本周期累计"来源、
 * **不再是效果驱动的临时来源**,故探针即使加了活体书页效果也不再额外 +1)。
 * 再尝试注册两个「常态关闭」的探针临时来源,置位后 extra = 7 → getMaxAllowed() = 8,
 * **封顶 9 依旧不可达**(当前内容下满配也只能到 8),故本命令实机走的是
 * 「上限未达封顶时主动必须正常释放、且不得超过常量封顶 9」的正向对照分支;
 * 若将来内容变化使 9 可达,则自动走封顶拒绝分支。
 * 若 Rhino 无法实现该接口(预检不通过,不注册),则只有正向对照形态。
 * 两条分支各自的正确性由 _CAP_OK 判定,_BRANCH/_VERDICT 标明实际跑到哪条。
 */
function doKomachiCap(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var err = equipSign(p, KOMACHI_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    clearExtraPlayEffects(p);
    resetEffectCardCycle(p);
    var slotErr = ensureChipSlot(p, 2);
    if (slotErr != null) { send(ctx, "AP_" + tag + "_ERR:" + slotErr); return 0; }
    clearCurioSlots(p, "chip");
    var bp = resolveItem("astral_dice:big_backpack_chip");
    var ns = resolveItem("astral_dice:ninja_star_chip");
    if (bp == null || ns == null) { send(ctx, "AP_" + tag + "_ERR:unknown_chip"); return 0; }
    putInSlot(p, "chip", new ItemStack(bp), 0);
    putInSlot(p, "chip", new ItemStack(ns), 1);
    try {
        p.addEffect(new MobEffectInstanceClass(fxLivingPage(), 6000, 0, false, false, true));
        p.addEffect(new MobEffectInstanceClass(fxFateGuidance(), 6000, 0, false, false, true));
    } catch (e1) { send(ctx, "AP_" + tag + "_ERR:effect:" + exText(e1)); return 0; }
    ModAttachments.setCandyChipPlayBonusActive(p, true);
    ModAttachments.setSatellitePlayBonusActive(p, true);
    var fill = EffectCardPeriodClass.getMaxAllowed(p);
    send(ctx, "AP_" + tag + "_CONST:" + GameplayConstantsClass.MAX_EFFECT_CARD_PLAYS
        + ":bonus_one_shot=1");
    send(ctx, "AP_" + tag + "_FILL:" + fill);
    if (probeExtraSourceState === "not_tried") tryInstallProbeSources(p);
    send(ctx, "AP_" + tag + "_SRC:" + probeExtraSourceState);
    var armedMax = fill;
    if (probeExtraSourceState.indexOf("ok:2") === 0) {
        probeExtraSourceArmed = true;
        armedMax = EffectCardPeriodClass.getMaxAllowed(p);
        probeExtraSourceArmed = false;
    }
    send(ctx, "AP_" + tag + "_ARMED_MAX:" + armedMax);
    var capOk = 0;
    var branch = "uncapped_release";
    if (armedMax >= GameplayConstantsClass.MAX_EFFECT_CARD_PLAYS) {
        // 封顶分支:不释放(附件保持 0)且不进入冷却
        branch = "capped_reject";
        probeExtraSourceArmed = true;
        ModAttachments.setEffectCardBonusPlays(p, 0);
        ModAttachments.setSignActiveCooldownEnd(p, 0);
        BaseSignItemClass.performSkillForCurio(p);
        var ex = EffectCardPeriodClass.getBonusPlays(p);
        var cd = signCooldownRemaining(p);
        var mx = EffectCardPeriodClass.getMaxAllowed(p);
        probeExtraSourceArmed = false;
        capOk = (ex === 0 && cd === 0 && mx === armedMax) ? 1 : 0;
        send(ctx, "AP_" + tag + "_BRANCH:capped_reject");
        send(ctx, "AP_" + tag + "_AFTER:extra=" + ex + ":cd=" + (cd > 0 ? 1 : 0) + ":max=" + mx);
    } else {
        // 未封顶的正向对照:必须释放(附件 0→1、上限 +1)、**进入锁定(生效中)态且不立即起冷却**、
        // 且不得超过常量封顶(第二批「三态化」:忍者锁定跟随出牌周期,冷却从周期完全重置那一刻开始)
        ModAttachments.setEffectCardBonusPlays(p, 0);
        ModAttachments.setSignActiveCooldownEnd(p, 0);
        BaseSignItemClass.performSkillForCurio(p);
        var ex2 = EffectCardPeriodClass.getBonusPlays(p);
        var cd2 = signCooldownRemaining(p);
        var locked2 = (lockSignId(p) === KOMACHI_SIGN_ID) ? 1 : 0;
        var mx2 = EffectCardPeriodClass.getMaxAllowed(p);
        capOk = (ex2 === 1 && cd2 === 0 && locked2 === 1 && mx2 === fill + 1
            && mx2 <= GameplayConstantsClass.MAX_EFFECT_CARD_PLAYS) ? 1 : 0;
        send(ctx, "AP_" + tag + "_BRANCH:uncapped_release");
        send(ctx, "AP_" + tag + "_AFTER:extra=" + ex2 + ":cd=" + (cd2 > 0 ? 1 : 0)
            + ":locked=" + locked2 + ":max=" + mx2);
    }
    send(ctx, "AP_" + tag + "_CAP_OK:" + capOk);
    // 单行「联合判词」把三个读数绑在一起,便于用例用一条正则锁死两种合法形态
    send(ctx, "AP_" + tag + "_VERDICT:" + probeExtraSourceState + ":" + armedMax + ":"
        + branch + ":" + capOk);
    // 收尾:清场(不污染后续用例)
    clearExtraPlayEffects(p);
    clearCurioSlots(p, "chip");
    resetEffectCardCycle(p);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/**
 * 武装「周期归零」现场:附件 = 1、出牌数 = 当前上限、冷却结束时刻 = 已到期(非 0)。
 * 真正驱动归零的是 PlayerTickEvents.onPlayerTick(每 20 tick)→ EffectCardPeriod.tick,
 * 故本命令只武装、由后续 komachiread 读结果(不直接调用 tick,保证走真实驱动链)。
 */
function doKomachiCycle(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    resetEffectCardCycle(p);
    clearExtraPlayEffects(p);
    var err = equipSign(p, KOMACHI_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    ModAttachments.setEffectCardBonusPlays(p, 1);
    var max = EffectCardPeriodClass.getMaxAllowed(p);
    ModAttachments.setEffectCardPlayCount(p, max);
    var now = nowTick(p);
    ModAttachments.setEffectCardCooldownEnd(p, now > 1 ? now : 1);
    send(ctx, "AP_" + tag + "_ARMED:extra=" + EffectCardPeriodClass.getBonusPlays(p)
        + ":count=" + EffectCardPeriodClass.getPlayCount(p)
        + ":max=" + max
        + ":cd_end=" + ModAttachments.getEffectCardCooldownEnd(p)
        + ":now=" + now);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/**
 * 效果牌**冷却进行中**按主动:必须不释放、不改动本轮一次性 +1、不进入主动技能冷却。
 * 构造:主动技能自身冷却置零(可触发)+ 出牌轮冷却结束时刻置为未来(冷却进行中)+ 基线加成 0。
 * 判据:bonus 保持 0、上限不变、主动冷却未起算、出牌轮冷却未被改写。
 */
function doKomachiCooldown(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    clearCurioSlots(p, "chip");
    clearExtraPlayEffects(p);
    resetEffectCardCycle(p);
    var err = equipSign(p, KOMACHI_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    var now = nowTick(p);
    ModAttachments.setEffectCardCooldownEnd(p, now + 600);      // 出牌轮冷却:进行中(未来时刻)
    var maxBefore = EffectCardPeriodClass.getMaxAllowed(p);
    send(ctx, "AP_" + tag + "_BEFORE:bonus=" + EffectCardPeriodClass.getBonusPlays(p)
        + ":cd_active=" + (EffectCardPeriodClass.isCooldownActive(p) ? 1 : 0)
        + ":sign_cd=" + (signCooldownRemaining(p) > 0 ? 1 : 0)
        + ":max=" + maxBefore);
    BaseSignItemClass.performSkillForCurio(p);
    var bonusAfter = EffectCardPeriodClass.getBonusPlays(p);
    var maxAfter = EffectCardPeriodClass.getMaxAllowed(p);
    var signCd = signCooldownRemaining(p);
    var cdEnd = ModAttachments.getEffectCardCooldownEnd(p);
    send(ctx, "AP_" + tag + "_AFTER:bonus=" + bonusAfter + ":max=" + maxAfter
        + ":sign_cd=" + (signCd > 0 ? 1 : 0) + ":cd_kept=" + (cdEnd > now ? 1 : 0));
    var ok = (bonusAfter === 0) && (maxAfter === maxBefore) && (signCd <= 0) && (cdEnd > now);
    send(ctx, "AP_" + tag + "_REJECTED:" + (ok ? 1 : 0));
    resetEffectCardCycle(p);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/**
 * 读「周期归零」结果(2026-09-15 B6 ③:读数同时走 `/astralparty dump`)。
 *
 * 两件事**都要**,不是二选一(勿简化):
 *  ① 保留下面 tag 唯一的 `READ` / `CLEARED` —— 断言窗口是「自 launch 快照起的 latest.log
 *     增量」、**跨用例共享**(见 mt_assert.ps1 的 `Get-MtSnapFor`),不带 tag 的 APDUMP 行
 *     无法归属到某一条用例 ⇒ 拿通用 APDUMP 行顶替 tag 唯一的既有断言等于**弱化断言**
 *     (前序 AIRBAG 的 dump 就能把 `effect_card_play_count=0` 之类的行先喂饱)。
 *  ② **追加** `dumpState`:由 dump 的 `LOCKRAW` 组把 `effect_card_bonus_plays` /
 *     `effect_card_play_count` / `effect_card_cooldown_end` / `max_allowed` 按固定机器格式
 *     落 `latest.log`,用例断言**直接锚定这四行原始值**(红线:判定入口 LOCKDERIVED 禁止作落点)。
 *
 * 红线(见 dumpState 注释):dump 只作读数;本函数不写任何状态,真实出牌 / `registerPlay` /
 * `tick` 三条归零路径一字未动。dump 调用失败会额外落 `AP_<tag>_DUMP_ERR:`,用例对它做
 * absent 断言 ⇒ 落 FAIL,绝不静默降级。
 */
function doKomachiRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    dumpState(ctx, tag);
    var extra = EffectCardPeriodClass.getBonusPlays(p);
    var count = EffectCardPeriodClass.getPlayCount(p);
    var cdEnd = ModAttachments.getEffectCardCooldownEnd(p);
    send(ctx, "AP_" + tag + "_READ:extra=" + extra + ":count=" + count
        + ":cd=" + (cdEnd > 0 ? 1 : 0) + ":max=" + EffectCardPeriodClass.getMaxAllowed(p));
    var ok = (extra === 0 && count === 0 && cdEnd === 0) ? 1 : 0;
    send(ctx, "AP_" + tag + "_CLEARED:" + ok);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ── 骇客:完全隐身 / 末影珍珠免疫 ───────────────────────────────────────────
/** 隐身相关状态一行读数(附件 + 原版隐身实例 + 主动加成) */
function nancyStateText(p) {
    var now = nowTick(p);
    var hidden = ModAttachments.getNancyLuHiddenUntil(p);
    var remain = hidden > 0 ? (hidden - now) : 0;
    var inv = findEffect(p, DESC_INVIS);
    return "hidden_until=" + remain
        + ":effect=" + (inv != null ? 1 : 0)
        + ":vis=" + effectVis(p, DESC_INVIS)
        + ":dur=" + (inv != null ? inv.getDuration() : -1)
        + ":hack=" + (findEffect(p, DESC_HACK) != null ? 1 : 0)
        + ":bonus=" + ModAttachments.getNancyLuActiveBonus(p)
        + ":win=" + ((remain > 0 && remain <= NANCY_HIDDEN_TICKS) ? 1 : 0);
}

/** 按主动进入完全隐身 + 读数(hasEffect / 实例可见性 / 附件窗口 / 客户端抑制判定) */
function doNancyCloak(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    resetEffectCardCycle(p);
    ModAttachments.setNancyLuHiddenUntil(p, 0);
    ModAttachments.setNancyLuActiveBonus(p, 0);
    ModAttachments.setNancyLuActiveBonusUntil(p, 0);
    ModAttachments.setNancyLuEnderPearlImmuneUntil(p, 0);
    try { p.removeEffect(MobEffectsClass.INVISIBILITY); } catch (e0) { /* 忽略 */ }
    try { ModEffectRemoval.remove(p, fxHack()); } catch (e1) { /* 忽略 */ }
    var err = equipSign(p, NANCY_LU_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    send(ctx, "AP_" + tag + "_EQUIP:" + (NancyLuSignItemClass.isEquipped(p) ? 1 : 0));
    BaseSignItemClass.performSkillForCurio(p);
    send(ctx, "AP_" + tag + "_STATE:" + nancyStateText(p));
    send(ctx, "AP_" + tag + "_HIDDEN:" + (NancyLuSignItemClass.isHidden(p) ? 1 : 0)
        + ":" + (NancyLuSignItemClass.isHiddenClient(p) ? 1 : 0));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 武装「隐身到期」现场:附件置为已到期(非 0)+ 施加隐身实例,归零由 onCurioTick 每 tick 完成 */
function doNancyExpire(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var err = equipSign(p, NANCY_LU_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    try { p.addEffect(new MobEffectInstanceClass(MobEffectsClass.INVISIBILITY, 6000, 0, false, false, true)); }
    catch (e1) { send(ctx, "AP_" + tag + "_ERR:effect:" + exText(e1)); return 0; }
    var now = nowTick(p);
    ModAttachments.setNancyLuHiddenUntil(p, (now > 1 ? now : 1) - 1);
    send(ctx, "AP_" + tag + "_ARMED:" + nancyStateText(p));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 读隐身状态 + 判定「附件与效果是否都被清掉」 */
function doNancyState(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    // B6 ③:只读相位同时调 dump(只读),把本模组效果原始行(EFFECTS 组)落进本轮增量,
    // 断言可锚定 `APDUMP|EFFECTS|effect=astral_dice:...` 的**原始**存在性,而不是判定入口。
    // 特性特有的读数(hidden_until / vis / bonus)不在 dump 的能力边界内(dump 只输出本模组
    // 自身状态,且刻意不输出原版隐身实例),故下面的 nancyStateText 继续保留。
    dumpState(ctx, tag);
    send(ctx, "AP_" + tag + "_STATE:" + nancyStateText(p));
    var cleared = (ModAttachments.getNancyLuHiddenUntil(p) === 0 && findEffect(p, DESC_INVIS) == null) ? 1 : 0;
    send(ctx, "AP_" + tag + "_CLEARED:" + cleared);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 受伤反馈字段快照(hp/hurtTime/invulnerableTime/hurtMarked) */
function nancyHurtSnapshot(p) {
    return "hp=" + p.getHealth() + ":hurt=" + p.hurtTime
        + ":invul=" + p.invulnerableTime + ":marked=" + p.hurtMarked;
}

/** 归零受伤反馈字段(脚手架,避免上一条用例留下的无敌帧/受伤标记污染读数) */
function resetHurtFeedback(p) {
    // 只写**公有**字段:hurtTime/hurtDuration 在 LivingEntity,invulnerableTime/hurtMarked 在 Entity;
    // lastHurt 是 protected,Rhino 写不到 —— 也不必写:无敌帧分支要求 invulnerableTime > 10,
    // 归零 invulnerableTime 后 lastHurt 不参与判定。
    var bad = [];
    try { p.hurtTime = 0; } catch (e1) { bad.push("hurtTime"); }
    try { p.invulnerableTime = 0; } catch (e2) { bad.push("invulnerableTime"); }
    try { p.hurtMarked = false; } catch (e3) { bad.push("hurtMarked"); }
    return bad.length === 0 ? "ok" : ("partial:" + bad.join(","));
}

/**
 * 对玩家施加一次 FALL 伤害,返回**真正使生命值下降**的 API 名。
 * hurt 声明于 LivingEntity,Rhino 直接成员查找可能不可见(见文件头真机实证),
 * 故链式回退;判据是「血量真的掉了」而不是「没抛异常」。
 *
 * ⚠️ 2026-09-15 B2 加固(实测 NANCY-LU-PEARL-IMMUNE 首次真正跑到 P1 免疫相位时,
 * 本函数在**免疫相位**抛 `IllegalStateException: Missing key in ResourceKey[… damage_type]: …minecraft:5.0`
 * 并逃出命令执行体,表现为「试图执行该命令时出现意外错误」+ 后续读数整段缺失):
 * KubeJS 的 `DamageSource` 类型包装器(`DamageSourceWrapper.wrap`)把某个 **JS number(5.0)**
 * 当成伤害类型 id 解析了。属**测试探针侧**的 Rhino/KubeJS 调用形态问题,不是产品行为。
 * 加固三层:① `src` 的获取本身也进 try(此前它在所有 try 之外,是唯一能让异常逃逸的位置);
 *          ② 三次尝试各自独立 try;③ 全部失败时回退**原版命令入口** `/damage`
 *          (TESTING-SPEC 的既定口径:状态改写一律走原版命令,避开 Rhino 方法可见性坑)。
 * 返回语义保持:真掉血才返回 API 名;免疫相位必须返回 `none[...]`。
 */
var lastFallDiag = "";
function runCmdP(p, cmd) {
    try {
        var src = p.createCommandSourceStack();
        return "rc=" + p.level.getServer().getCommands().performPrefixedCommand(src, cmd);
    } catch (e) { return "ERR:" + exText(e); }
}

function applyFallDamage(p, amount) {
    var tried = [];
    var src = null;
    try { src = p.level.damageSources().fall(); }
    catch (e0) { tried.push("src:" + exText(e0)); }
    var before = -1;
    try { before = p.getHealth(); } catch (e00) { tried.push("health:" + exText(e00)); }
    function dropped() { try { return (before >= 0) && (p.getHealth() < before); } catch (e) { return false; } }
    function attempt(name, fn) {
        try { fn(); if (dropped()) return name; }
        catch (e) { tried.push(name + ":" + exText(e)); }
        return null;
    }
    // ⚠️ 2026-09-15 B3 更正(字节码取证见本批报告 §0.1):
    //   ① `p.damage(src, amount)` **参数顺序错** —— KubeJS 把 `EntityKJS` 的
    //      `kjs$damage(float, DamageSource)` 去前缀后暴露成 JS 的 `damage(amount, source)`
    //      (与 `kjs$attack(DamageSource, float)` = `attack(source, amount)` **相反**)。
    //      旧写法把 `amount`(5.0) 送进了 `DamageSource` 形参 ⇒ KubeJS 的
    //      `DamageSourceWrapper.wrap` 拿 `ID.mc(5.0)` 当伤害类型 id ⇒
    //      `IllegalStateException: … damage_type … minecraft:5.0`,并以**未捕获异常**形式
    //      污染 KubeJS server.log(连带 kubejs 断言 FAIL)。**B2 曾把这条误记为 `p.hurt`,
    //      实际 `hurt(DamageSource, float)` 顺序本就正确。** 现改正为 `damage(amount, src)`。
    //   ② `/damage` 命令**不能同步判定** —— `performPrefixedCommand` 在 1.21.1 上把命令
    //      推迟到本 tick 末执行,同一次调用里读 getHealth() 必然读到"还没打"。
    //   ③ `p.causeFallDamage(amount, 1.0, src)` / `p.damage(amount, src)` **同步生效且可用**
    //      (实测 CTRL 相位真实掉血),故本函数只走这两条;免疫窗口生效时两者都会被产品取消 ⇒ 不掉血。
    if (src != null) {
        var r = attempt("causeFallDamage", function () { p.causeFallDamage(amount, 1.0, src); });
        if (r != null) { lastFallDiag = r; return r; }
        r = attempt("damage", function () { p.damage(amount, src); });
        if (r != null) { lastFallDiag = r; return r; }
        // 兜底:KubeJS 的另一条同族包装 `attack(DamageSource, float)`(顺序与 hurt 相同)
        r = attempt("attack", function () { p.attack(src, amount); });
        if (r != null) { lastFallDiag = r; return r; }
    } else { tried.push("src_unavailable"); }
    // 回退:原版 /damage(与真人/命令同一入口)。免疫窗口生效时它同样会被产品取消 ⇒ 不掉血。
    // ⚠️ **不再回退 `/damage` 命令**:1.21.1 上 `performPrefixedCommand` 把命令**推迟到本 tick 末**
    // 才执行,同一次调用里读 `getHealth()` 必然读到「还没打」⇒ 该回退无法给出同步判据
    // (实测 api=none[] 且血量不变,纯属时序)。真需要命令通道时应拆成「下发 + 下一 tick 再读」两步。
    lastFallDiag = "none[" + tried.join(" | ") + "]";
    return lastFallDiag;
}

/**
 * 免疫窗口内施加 FALL 伤害 → 生命值不变 / hurtTime=0 / 未进无敌帧 / 未置 hurtMarked;
 * 紧接同一构造清掉免疫窗口做对照 → 必须真的受伤(证明伤害 API 可用、结论非「空跑」)。
 * 创造模式(abilities.invulnerable)下 Player#hurt 直接返回 false,故先临时切生存,收尾还原。
 */
function doNancyFall(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var err = equipSign(p, NANCY_LU_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    var mode = "already_survival";
    if (p.getAbilities().instabuild) {
        try { p.setGameMode(GameTypeClass.SURVIVAL); mode = "forced_survival"; }
        catch (e1) { send(ctx, "AP_" + tag + "_ERR:gamemode:" + exText(e1)); return 0; }
    }
    p.setHealth(p.getMaxHealth());
    send(ctx, "AP_" + tag + "_FIELDS:" + resetHurtFeedback(p));
    // ── 相位 A:对照(无免疫窗口)必须真的受伤 ──────────────────────────────
    ModAttachments.setNancyLuEnderPearlImmuneUntil(p, 0);
    p.setHealth(p.getMaxHealth());
    resetHurtFeedback(p);
    var b0 = nancyHurtSnapshot(p);
    var api = applyFallDamage(p, FALL_DAMAGE_AMOUNT);
    var a0 = nancyHurtSnapshot(p);
    send(ctx, "AP_" + tag + "_CTRL:" + b0 + "->" + a0);
    var ctrlOk = (p.getHealth() < p.getMaxHealth() && p.hurtTime > 0
        && p.invulnerableTime > 0 && p.hurtMarked === true) ? 1 : 0;
    send(ctx, "AP_" + tag + "_CTRL_OK:" + ctrlOk);
    send(ctx, "AP_" + tag + "_API:" + api);
    // ── 相位 B:免疫窗口内(修复点)必须毫无变化 ────────────────────────────
    p.setHealth(p.getMaxHealth());
    resetHurtFeedback(p);
    var now = nowTick(p);
    ModAttachments.setNancyLuEnderPearlImmuneUntil(p, (now > 0 ? now : 1) + PEARL_IMMUNE_TICKS * 10);
    var b1 = nancyHurtSnapshot(p);
    var apiImm = applyFallDamage(p, FALL_DAMAGE_AMOUNT);
    var a1 = nancyHurtSnapshot(p);
    send(ctx, "AP_" + tag + "_IMM:" + b1 + "->" + a1);
    var immOk = (b1 === a1) && (p.getHealth() >= p.getMaxHealth()
        && p.hurtTime === 0 && p.invulnerableTime === 0 && p.hurtMarked === false) ? 1 : 0;
    send(ctx, "AP_" + tag + "_IMM_OK:" + immOk);
    send(ctx, "AP_" + tag + "_IMM_API:" + apiImm);
    // 收尾
    p.setHealth(p.getMaxHealth());
    ModAttachments.setNancyLuEnderPearlImmuneUntil(p, 0);
    send(ctx, "AP_" + tag + "_MODE:" + mode);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// 真实末影珍珠:落地时生产代码先由 ProjectileImpactEvent 记下免疫窗口,
// 随后原版在同一个 onHit 里 teleport + hurt(damageSources().fall(), 5) —— 正是被修复的链路。
// 珍珠本体不指望探针调用 hurt,故即使 Rhino 看不到 hurt,本条仍能取证。
var pearlWatchTag = "";
var pearlWatchPlayer = null;
var pearlWatchPearl = null;
var pearlWatchRemaining = 0;
var pearlWatchExpectImmune = true;
var pearlWatchZ0 = 0.0;
/** 免疫窗口峰值(每 tick 采样;见 pearlTickBody 注释) */
var pearlWatchMaxWindow = 0;
/** B7 诊断:逐 tick 轨迹(玩家 z / 珍珠 z)与几何读数;观察窗内累积,一次性落 AP_<tag>_TRACE/_GEO/_POST */
var pearlTracePz = [];
var pearlTracePearlZ = [];
var pearlTraceDzMax = 0.0;
var pearlTracePost = 0;
var pearlTraceGeo = "";

/** 三位小数(读数用;避免浮点噪声把日志撑爆) */
function r3(v) { return Math.round(v * 1000) / 1000; }

/** 方块状态文本(读不到返回 '?') */
function blkAt(level, pos) {
    try { return String(level.getBlockState(pos)); } catch (e) { return "?"; }
}

/** 半径内非玩家实体摘要(type@x,y,z;最多 4 条;读不到返回 '?') */
function nearbyEntsText(p, radius) {
    try {
        var list = p.level.getEntitiesOfClass(EntityClass,
            AABBClass.ofSize(p.position(), radius, radius, radius));
        var out = [];
        for (var i = 0; i < list.size() && out.length < 4; i++) {
            var e = list.get(i);
            if (e.getId() === p.getId()) continue;
            out.push(typeIdOf(e) + "@" + r3(e.getX()) + "," + r3(e.getY()) + "," + r3(e.getZ()));
        }
        return "n=" + list.size() + "[" + out.join(";") + "]";
    } catch (e2) { return "?"; }
}

/**
 * 确定几何(B7 加固):先把玩家 x/z 吸到方块中心,再清出一条**足够长的无阻挡通道**,
 * 末端立接珠柱,并把通道内的非玩家实体一并清除。
 *
 * 为什么要加固(取证见 docs/batch3/B7-assert-window.md):
 *   旧几何只清 dx∈[-1,1] / dy∈[0,2] / dz∈[0,3],接珠柱立在 dz=3,珍珠从 p.z+1.0 起飞
 *   ⇒ **最早可能在第 0 tick 就命中**(原版 Projectile#tick 是"先按移动向量判命中、再移动",
 *   `ThrownEnderpearl#onHit` 用**移动前**的位置做 teleportTo)⇒ 位移恰好 = 起飞偏移 1.000 格;
 *   若第 1 tick 才命中则位移 = 1.8 格。用例判据是 `位移 > 1.0` ⇒ **刚好卡在临界值上**:
 *   1.20.1 实测一轮 P2 读到 tel=0(位移 1.000)、同构造的 P3 读到 tel=1(位移 1.8)——
 *   差别只是"命中发生在第 0 还是第 1 tick",与装不装立牌无关,即旧几何让 `tel` 变成掷硬币。
 *   方块清得掉、**实体清不掉**:残留实体同样会造成"起飞即命中"的退化路径,故必须一并清。
 *   加固后通道长 6 格、接珠柱在 dz=5 ⇒ 飞行 ≥4 格、位移期望 ≈4.15 格,判据留 3 格余量。
 */
function nancyPearlArena(p) {
    // ① 吸到方块中心:消除"站在方块边缘"造成的落点不确定性
    var b0 = p.blockPosition();
    try { p.connection.teleport(b0.getX() + 0.5, p.getY(), b0.getZ() + 0.5, 0.0, 0.0); } catch (e0) { /* 忽略 */ }
    var base = p.blockPosition();

    // ② 加固前读数(诊断:旧几何"起飞即命中"的成因;只落证据,不参与判定)
    var preEnts = nearbyEntsText(p, 6.0);
    var preBlocks = blkAt(p.level, base.offset(0, 1, 1)) + "|" + blkAt(p.level, base.offset(0, 1, 2));

    // ③ 通道:6 格长 × 4 格高的空腔 + 石地板
    for (var dx = -1; dx <= 1; dx++) {
        for (var dy = 0; dy <= 3; dy++) {
            for (var dz = 0; dz <= 6; dz++) {
                try { p.level.setBlockAndUpdate(base.offset(dx, dy, dz), BlocksClass.AIR.defaultBlockState()); }
                catch (e1) { /* 忽略 */ }
            }
        }
    }
    for (var dx2 = -1; dx2 <= 1; dx2++) {
        for (var dz2 = 0; dz2 <= 6; dz2++) {
            try { p.level.setBlockAndUpdate(base.offset(dx2, -1, dz2), BlocksClass.STONE.defaultBlockState()); }
            catch (e2) { /* 忽略 */ }
        }
    }
    // ④ 接珠柱:dz=5 处 3 格高(珍珠必须飞满 ~4 格才够得到)
    for (var py = 1; py <= 3; py++) {
        try { p.level.setBlockAndUpdate(base.offset(0, py, 5), BlocksClass.STONE.defaultBlockState()); }
        catch (e3) { /* 忽略 */ }
    }

    // ⑤ 清掉通道内的非玩家实体
    var removed = 0;
    try {
        var list = p.level.getEntitiesOfClass(EntityClass,
            AABBClass.ofSize(p.position(), 12.0, 12.0, 12.0));
        for (var i = 0; i < list.size(); i++) {
            var e = list.get(i);
            if (e.getId() === p.getId()) continue;
            try { e.discard(); removed++; } catch (e4) { /* 忽略 */ }
        }
    } catch (e5) { /* 忽略 */ }

    // ⑥ 加固后读数(诊断 + 证明通道确实建起来了)
    pearlTraceGeo = "base=" + base.getX() + "," + base.getY() + "," + base.getZ()
        + ":pre_ents=" + preEnts
        + ":pre_blk=" + preBlocks
        + ":post_blk=" + blkAt(p.level, base.offset(0, 1, 1)) + "|" + blkAt(p.level, base.offset(0, 1, 2))
        + "|" + blkAt(p.level, base.offset(0, 1, 5))
        + ":removed=" + removed
        + ":p=" + r3(p.getX()) + "," + r3(p.getY()) + "," + r3(p.getZ());
    return base;
}

/** 在玩家正前方 1 格、眼睛高度生成一枚真珍珠,朝 +Z(与 aimPositiveZ 的朝向一致)飞出 */
function nancySpawnPearl(p) {
    var pearl = new ThrownEnderpearlClass(p.level, p);
    pearl.setPos(p.getX(), p.getY() + 1.5, p.getZ() + 1.0);
    pearl.setDeltaMovement(new Vec3Class(0.0, 0.0, 0.8));
    p.level.addFreshEntity(pearl);
    return pearl;
}

/**
 * 真实珍珠相位:withSign=true → 装立牌(免疫);false → 卸下立牌(对照,必须受伤)。
 * 珍珠落地后由 tick 观察窗读数(含「在窗口内再施加一次 FALL 伤害」)。
 */
function doNancyPearl(ctx, tag, withSign) {
    var p = ctx.source.getPlayerOrException();
    var mode = "already_survival";
    if (p.getAbilities().instabuild) {
        try { p.setGameMode(GameTypeClass.SURVIVAL); mode = "forced_survival"; }
        catch (e1) { send(ctx, "AP_" + tag + "_ERR:gamemode:" + exText(e1)); return 0; }
    }
    if (withSign) {
        var err = equipSign(p, NANCY_LU_SIGN_ID);
        if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    } else {
        clearCurioSlots(p, "stand");
    }
    ModAttachments.setNancyLuEnderPearlImmuneUntil(p, 0);
    p.setHealth(p.getMaxHealth());
    var fields = resetHurtFeedback(p);
    send(ctx, "AP_" + tag + "_SETUP:equip=" + (NancyLuSignItemClass.isEquipped(p) ? 1 : 0)
        + ":mode=" + mode + ":hp=" + p.getHealth() + ":fields=" + fields);
    aimPositiveZ(p, 3);
    nancyPearlArena(p);
    var pearl = nancySpawnPearl(p);
    pearlWatchTag = tag;
    pearlWatchPlayer = p;
    pearlWatchPearl = pearl;
    pearlWatchExpectImmune = withSign;
    pearlWatchRemaining = 160;
    pearlWatchMaxWindow = 0;
    pearlWatchZ0 = p.getZ();
    pearlTracePz = [r3(p.getZ())];
    pearlTracePearlZ = [r3(pearl.getZ())];
    pearlTraceDzMax = 0.0;
    pearlTracePost = 0;
    send(ctx, "AP_" + tag + "_GEO:" + pearlTraceGeo);
    send(ctx, "AP_" + tag + "_SPAWN:" + pearl.getId());
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 收尾:回满血 / 清窗口与隐身附件 / 立牌离场 */
function doNancyPearlClose(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    pearlWatchRemaining = 0;
    p.setHealth(p.getMaxHealth());
    ModAttachments.setNancyLuEnderPearlImmuneUntil(p, 0);
    ModAttachments.setNancyLuHiddenUntil(p, 0);
    ModAttachments.setNancyLuActiveBonus(p, 0);
    ModAttachments.setNancyLuActiveBonusUntil(p, 0);
    try { p.removeEffect(MobEffectsClass.INVISIBILITY); } catch (e1) { /* 忽略 */ }
    try { ModEffectRemoval.remove(p, fxHack()); } catch (e2) { /* 忽略 */ }
    resetHurtFeedback(p);
    clearCurioSlots(p, "stand");
    clearCurioSlots(p, "chip");
    resetEffectCardCycle(p);
    var restore = "n/a";
    try {
        p.setGameMode(GameTypeClass.CREATIVE);
        restore = "creative";
    } catch (e3) { restore = "failed:" + exText(e3); }
    send(ctx, "AP_" + tag + "_RESTORE:" + restore);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ── 层数递减类效果:构造「闪烁窗口」状态(判定为人工,见 TESTING-SPEC)────────
/** 效果实例一行读数(amp/dur/amb/vis);absent = 当前没有该效果 */
function fxState(p, descId) {
    var inst = findEffect(p, descId);
    if (inst == null) return "absent";
    var amb = "?";
    var vis = "?";
    try { amb = inst.isAmbient() ? 1 : 0; } catch (e1) { amb = "?"; }
    try { vis = inst.isVisible() ? 1 : 0; } catch (e2) { vis = "?"; }
    return "amp=" + inst.getAmplifier() + ":dur=" + inst.getDuration() + ":amb=" + amb + ":vis=" + vis;
}

/** 是否落在原版闪烁条件里(非 ambient 且 endsWithin(200))—— 与 Gui#renderEffects 同表达式 */
function fxInWindow(p, descId) {
    var inst = findEffect(p, descId);
    if (inst == null) return false;
    try { return (!inst.isAmbient()) && inst.endsWithin(FLICKER_WINDOW_TICKS); }
    catch (e) { return false; }
}

/**
 * 让治愈 / 标记 / 赋能三个效果同时存在,且实例时长都 ≤ 200 tick(= 原版即将到期闪烁窗口)。
 * 治愈:先武装治愈计时器再 add(updateEffect 才会按计时器剩余时长施加图标);
 * 标记:MarkManager.apply 直接接受时长;
 * 赋能:addStacks 后把实例时长改写成 200(与其余两类口径一致)。
 */
function doDecayFlash(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    try { EmpowerManager.removeAll(p); } catch (e0) { /* 忽略 */ }
    try { ModEffectRemoval.remove(p, fxEmpower()); } catch (e1) { /* 忽略 */ }
    try { ModEffectRemoval.remove(p, fxHealing()); } catch (e2) { /* 忽略 */ }
    try { ModEffectRemoval.remove(p, fxMarked()); } catch (e3) { /* 忽略 */ }
    try { p.removeEffect(MobEffectsClass.GLOWING); } catch (e4) { /* 忽略 */ }
    // 骰神赐福在场时 HealingManager#updateEffect 会用「赐福剩余时长」覆盖治愈图标时长(必然 > 200 tick),
    // 闪烁窗口永远不可能成立 → 必须先清掉赐福再武装窗口,否则本用例前置在赐福残留时不可满足。
    // 且**必须走 ModEffectRemoval**:ModEffectEvents#onModEffectRemovalPrevented 以 HIGH 优先级
    // 取消玩家身上 astral_dice:* 的普通移除(直接 removeEffect 与 /effect clear 一律无效),
    // 只有 ModEffectRemoval(内部标志)/EffectTimerGuard(强制标志)/死亡 三条通道放行。
    var blessedBefore = "?";
    try { blessedBefore = p.hasEffect(ModEffects.DICE_BLESSING) ? 1 : 0; } catch (e10) { blessedBefore = "ERR:" + exText(e10); }
    try { ModEffectRemoval.remove(p, ModEffects.DICE_BLESSING); } catch (e11) { /* 忽略 */ }
    try { HealingManagerClass.clear(p); } catch (e5) { /* 忽略 */ }
    var now = nowTick(p);
    var heal = "err";
    var mark = "err";
    var emp = "err";
    try {
        ModAttachments.setHealingTimerEnd(p, now + FLICKER_WINDOW_TICKS);
        HealingManagerClass.add(p, 3);
        heal = "ok";
    } catch (e6) { heal = exText(e6); }
    try { MarkManagerClass.apply(p, FLICKER_WINDOW_TICKS); MarkManagerClass.apply(p, FLICKER_WINDOW_TICKS); mark = "ok"; }
    catch (e7) { mark = exText(e7); }
    try {
        EmpowerManager.addStacks(p, 3);
        var inst = findEffect(p, DESC_EMPOWER);
        if (inst != null) {
            var amp = inst.getAmplifier();
            ModEffectRemoval.remove(p, fxEmpower());
            p.addEffect(new MobEffectInstanceClass(fxEmpower(), FLICKER_WINDOW_TICKS, amp, false, false, true));
        }
        emp = "ok";
    } catch (e8) { emp = exText(e8); }
    send(ctx, "AP_" + tag + "_SETUP:heal=" + heal + ":mark=" + mark + ":emp=" + emp + ":blessed_before=" + blessedBefore);
    send(ctx, "AP_" + tag + "_STATE:heal=" + fxState(p, DESC_HEAL)
        + "|mark=" + fxState(p, DESC_MARK) + "|emp=" + fxState(p, DESC_EMPOWER));
    var win = (fxInWindow(p, DESC_HEAL) && fxInWindow(p, DESC_MARK) && fxInWindow(p, DESC_EMPOWER)) ? 1 : 0;
    send(ctx, "AP_" + tag + "_WINDOW:" + win + ":" + FLICKER_WINDOW_TICKS);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 闪烁取证的清场:清掉三类效果与治愈点数(避免污染后续用例) */
function doDecayClear(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    // B6 ②:原用例注入的 `/effect clear @s` 折叠到探针内(省一条 ≥2.65 s 的键盘注入)。
    // ⚠️ 它**只清原版效果** —— `astral_dice:*` 的移除会被 ModEffectEvents 取消(见用例 NOTE);
    //    本模组效果的真实清除由下面三行 `ModEffectRemoval.remove` 直接完成。
    var preClear = runCmd(ctx, "effect clear @s");
    send(ctx, "AP_" + tag + "_CLEAR:" + preClear);
    try { EmpowerManager.removeAll(p); } catch (e0) { /* 忽略 */ }
    try { HealingManagerClass.clear(p); } catch (e1) { /* 忽略 */ }
    try { ModAttachments.setHealingTimerEnd(p, 0); } catch (e2) { /* 忽略 */ }
    try { ModEffectRemoval.remove(p, fxHealing()); } catch (e3) { /* 忽略 */ }
    try { ModEffectRemoval.remove(p, fxMarked()); } catch (e4) { /* 忽略 */ }
    try { ModEffectRemoval.remove(p, fxEmpower()); } catch (e5) { /* 忽略 */ }
    try { p.removeEffect(MobEffectsClass.GLOWING); } catch (e6) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_STATE:heal=" + fxState(p, DESC_HEAL)
        + "|mark=" + fxState(p, DESC_MARK) + "|emp=" + fxState(p, DESC_EMPOWER));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// 珍珠落地观察窗(独立于 boltvis/hud/watch 的 tick 回调,非活跃时零开销)。
// 只在珍珠消失(命中)后读数一次,并在同一免疫窗口内再施加一次 FALL 伤害。
ServerEvents.tick(event => {
    // 外层兜底(B2 加固):此前任何逃出内层 try 的异常只会进 KubeJS server.log,
    // 既看不到是哪一步、也不进 latest.log ⇒ 用例只能看到「读数整段缺失」。
    // 这里额外把异常落到一条 AP_ 行(用独立标记 TICKDIAG,不污染各用例的 absent 断言)。
    try {
        pearlTickBody();
    } catch (eOuter) {
        try { emitTo(pearlWatchPlayer, "AP_TICKDIAG:" + pearlWatchTag + ":" + exText(eOuter)); } catch (e2) { /* 忽略 */ }
        pearlWatchRemaining = 0;
    }
});

function pearlTickBody() {
    if (pearlWatchRemaining <= 0) return;
    pearlWatchRemaining--;
    try {
        var p = pearlWatchPlayer;
        if (p == null) { pearlWatchRemaining = 0; return; }

        // ── B7 诊断尾迹:命中读数落盘之后再采 6 tick 的玩家 z ──────────────────
        // 目的:区分「传送还没被应用的时序问题」与「传送确实没发生(产品抑制)」。
        // 只落 AP_<tag>_POST 证据行,不参与任何判定。
        if (pearlTracePost > 0) {
            pearlTracePost--;
            pearlTracePz.push(r3(p.getZ()));
            var dzp = Math.abs(p.getZ() - pearlWatchZ0);
            if (dzp > pearlTraceDzMax) pearlTraceDzMax = dzp;
            if (pearlTracePost <= 0) {
                emitTo(p, "AP_" + pearlWatchTag + "_POST:z0=" + r3(pearlWatchZ0)
                    + ":dz_max=" + r3(pearlTraceDzMax)
                    + ":pz=" + pearlTracePz.join("|")
                    + ":pearlz=" + pearlTracePearlZ.join("|"));
                pearlWatchRemaining = 0;
            }
            return;
        }

        var pearl = pearlWatchPearl;
        var gone = false;
        try { gone = (pearl == null) || pearl.isRemoved() || !pearl.isAlive(); }
        catch (e0) { gone = false; }
        // 2026-09-15 B2:免疫窗口的**峰值**必须每 tick 采样一次。
        // 此前只在「珍珠消失」那一 tick 读一次 ⇒ 实测读到 window=0(珍珠落地后仍存活约 20 tick,
        // 20 tick 的窗口在读取时已耗尽),断言 `window∈[18,20]` 在时序上不可达(与 HOSTILE 同类缺陷)。
        // 峰值同样落在 latest.log,证据不弱化:它证明产品在落地那一刻确实写入了 ~20 tick 的窗口。
        var maxWin = 0;
        try {
            var u = ModAttachments.getNancyLuEnderPearlImmuneUntil(p);
            var n0 = nowTick(p);
            if (u > 0) { var r0 = u - n0; if (r0 > pearlWatchMaxWindow) pearlWatchMaxWindow = r0; }
            maxWin = pearlWatchMaxWindow;
        } catch (e0b) { /* 读不到就保持 0 */ }
        // B7 诊断:逐 tick 记录玩家 z / 珍珠 z,并累积**位移峰值**(见下方 tel 的说明)
        try {
            pearlTracePz.push(r3(p.getZ()));
            if (pearl != null) { pearlTracePearlZ.push(r3(pearl.getZ())); }
            var dzc = Math.abs(p.getZ() - pearlWatchZ0);
            if (dzc > pearlTraceDzMax) pearlTraceDzMax = dzc;
        } catch (e0c) { /* 忽略 */ }
        if (!gone) {
            if (pearlWatchRemaining <= 0) emitTo(p, "AP_" + pearlWatchTag + "_TIMEOUT:window_max=" + maxWin);
            return;
        }
        var now = nowTick(p);
        var until = ModAttachments.getNancyLuEnderPearlImmuneUntil(p);
        var remain = until > 0 ? (until - now) : 0;
        var drop = Math.round((p.getMaxHealth() - p.getHealth()) * 100) / 100;
        // 传送位移:证明走的是原版 onHit(命中方块 → teleportTo 后才会 hurt),
        // 而不是「珍珠撞到玩家本体」那种不产生摔落伤害的退化路径。
        //
        // B7:位移改取**窗口内峰值**(每 tick 采样,含命中后 6 tick 的尾迹),不再只读命中这一 tick。
        // 理由与原版时序有关:Projectile#tick 是"先判命中(用移动前的位置)、再移动",
        // 而观察窗的 tick 回调与弹射物自身的 tick 谁先谁后并无约定 ⇒ 单点采样会读到"传送尚未应用"
        // 的 0 值(与 B2 修过的 window 峰值是同一类缺陷)。**判据不放宽**:阈值取 2.0 格 ——
        // 加固前的退化路径位移是 1.000(第 0 tick 命中)或 1.8(第 1 tick 命中),都 < 2.0;
        // 加固后的真实方块命中位移期望 ≈4.15 格,留 2 格余量。
        var dzInst = p.getZ() - pearlWatchZ0;
        var TEL_MIN_DZ = 2.0;
        var tel = (pearlTraceDzMax >= TEL_MIN_DZ) ? 1 : 0;
        var api = "n/a";
        if (pearlWatchExpectImmune) {
            // 免疫相位:窗口**仍在**时再施加一次 FALL 伤害,必须依旧毫无反馈。
            // 窗口已耗尽时不再施加(那已不是被测语义,施加只会制造一个与产品无关的受伤读数)。
            api = (remain > 0) ? applyFallDamage(p, FALL_DAMAGE_AMOUNT) : "window_expired";
        }
        var ok;
        if (pearlWatchExpectImmune) {
            ok = (pearlWatchMaxWindow >= 18) && (pearlWatchMaxWindow <= 20) && (tel === 1)
                && (p.getHealth() >= p.getMaxHealth())
                && (p.hurtTime === 0) && (p.invulnerableTime === 0) && (p.hurtMarked === false);
        } else {
            // hurtMarked 由 ServerEntity#sendChanges 在同一 tick 内消费并复位,
            // 观察窗读到它时已不可靠 → 对照组只用「确实掉血 + hurtTime > 0」判定,
            // marked 值仍打印出来作为证据。
            ok = (pearlWatchMaxWindow === 0) && (tel === 1) && (p.getHealth() < p.getMaxHealth())
                && (p.hurtTime > 0);
        }
        emitTo(p, "AP_" + pearlWatchTag + "_TEL:dz=" + r3(dzInst) + ":dz_max=" + r3(pearlTraceDzMax)
            + ":thresh=" + TEL_MIN_DZ + ":ok=" + tel);
        emitTo(p, "AP_" + pearlWatchTag + "_PEARL:window=" + remain + ":window_max=" + pearlWatchMaxWindow
            + ":tel=" + tel + ":drop=" + drop + ":hurt=" + p.hurtTime + ":invul=" + p.invulnerableTime
            + ":marked=" + p.hurtMarked + ":api=" + api + ":dz=" + r3(dzInst) + ":dz_max=" + r3(pearlTraceDzMax));
        emitTo(p, "AP_" + pearlWatchTag + "_OK:" + (ok ? 1 : 0));
        emitTo(p, "AP_" + pearlWatchTag + "_DONE");
        // 命中后再采样 6 tick(诊断尾迹),随后由 POST 分支收窗
        pearlTracePost = 6;
        pearlWatchRemaining = 30;
    } catch (err) {
        emitTo(pearlWatchPlayer, "AP_" + pearlWatchTag + "_EX:" + exText(err));
        pearlWatchRemaining = 0;
    }
}


// ════════════════════════════════════════════════════════════════════════════
//  电磁炮雷击命中范围取证(railgunfriendly / railgunfriendlyread / railgunfriendlyend)
//
//  取证的是一条**确定性事实**,不是靠读代码推断:原版 LightningBolt#tick 在
//  `!visualOnly` 分支里对「以落点为中心 (x±3, y-3 .. y+9, z±3) 的箱内所有 isAlive 实体」
//  逐个调用 entity.thunderHit(...) → hurt(damageSources().lightningBolt(), getDamage()),
//  箱内**没有任何阵营过滤**(只排除 isAlive=false)。
//  而本模组是「每个可命中敌对目标处各生成一道雷击」(RailgunChipItem#executeStrike),
//  攻击者本人正处在近战距离(约 2 格),必然落在该箱内。
//  故本探针把四方靶子全部摆在落点箱内,同一 tick 记录血量基线 → 触发 → 1 秒后读差值。
//
//  对照条件:清天气(避免自然雷击污染计数)、强制生存(创造免伤读不到「打到自己」)、
//  清冷却 + 恰好 6 层充能 + 装备电磁炮筹码。
// ════════════════════════════════════════════════════════════════════════════
var rgfState = null;

function rghp(entity) {
    if (entity == null) return -1;
    try { return Math.round(entity.getHealth() * 100) / 100; } catch (e) { return -1; }
}

/**
 * 取玩家 UUID —— Rhino 对 KubeJS 过滤后的方法可见性有版本差异(实测 1.21.1 上
 * `ServerPlayer#getUUID` 抛 "Cannot find function getUUID"),故按三个取值器依次尝试,
 * 并把**成功的那一个**作为读数(src=1|2|3)暴露出去,避免"取不到就当没驯服"的静默降级。
 */
function playerUuid(p) {
    var tries = [
        function () { return p.getUUID(); },
        function () { return p.uuid; },
        function () {
            return Java.loadClass("net.minecraft.core.UUIDUtil").uuidFromIntArray(p.nbt.getIntArray("UUID"));
        }
    ];
    for (var i = 0; i < tries.length; i++) {
        try {
            var v = tries[i]();
            if (v != null) return { ok: true, value: v, src: (i + 1) };
        } catch (e) { /* 试下一个取值器 */ }
    }
    return { ok: false, value: null, src: "none" };
}

// ════════════════════════════════════════════════════════════════════════════
//  效果牌出牌状态机「永久锁死」回归(2026-09-14 严重 BUG 的外部汇报)
//    /astralprobe eccardlock <tag>
//  复现方式(等价于线上触发链的最末态):出牌数 = 当前上限,**且**冷却结束时间为 0。
//  该状态只可能由「上限在周期中途下降」造成(卸下大背包/忍术飞镖/可口糖果/探天卫星、
//  忍者主动 +1 标记被清、命运指引到期),旧实现下 tick 会因 cooldown <= 0 直接返回,
//  计数永远清不掉 ⇒ 效果牌永久不可用(界面:本轮出牌数已用完!剩余冷却 0 秒)。
//  断言:① 该状态被判为"已打满"且剩余 0 秒;② 修复后 tick 必须补上一轮冷却(结束时间落在未来);
//        ③ 该冷却到期后再 tick 一次,出牌数与冷却双双清零、出牌锁解除。
// ════════════════════════════════════════════════════════════════════════════
function doEffectCardLock(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    // 清掉全部药水效果:避免已生效的效果牌残效(效果待定源)干扰"剩余 0 秒"的断言
    runCmd(ctx, "effect clear @s");
    resetEffectCardCycle(p);
    var max = EffectCardPeriodClass.getMaxAllowed(p);
    var now = nowTickLevel(p.level);
    // 构造死状态:计数 = 上限,但没有冷却在跑
    ModAttachments.setEffectCardPlayCount(p, max);
    ModAttachments.setEffectCardCooldownEnd(p, 0);
    var blockedBefore = EffectCardPeriodClass.isBlocked(p);
    var remainBefore = EffectCardPeriodClass.getRemainingBlockSeconds(p);
    // ① 修复点:无冷却时的 tick 必须把这一轮冷却补上
    EffectCardPeriodClass.tick(p);
    var cdAfter = ModAttachments.getEffectCardCooldownEnd(p);
    var recovered = (cdAfter > now) ? 1 : 0;
    // ② 冷却到期 → tick 必须清空计数与冷却
    ModAttachments.setEffectCardCooldownEnd(p, now - 1);
    EffectCardPeriodClass.tick(p);
    var countCleared = ModAttachments.getEffectCardPlayCount(p);
    var cdCleared = ModAttachments.getEffectCardCooldownEnd(p);
    var blockedAfter = EffectCardPeriodClass.isBlocked(p);
    send(ctx, "AP_" + tag + "_LOCK:max=" + max + ":now=" + now + ":src=" + nowTickSource
        + ":blocked_before=" + (blockedBefore ? 1 : 0) + ":remain_before=" + remainBefore
        + ":cd_after_future=" + recovered + ":count_cleared=" + countCleared
        + ":cd_cleared=" + cdCleared + ":blocked_after=" + (blockedAfter ? 1 : 0));
    var ok = blockedBefore && remainBefore === 0 && recovered === 1
        && countCleared === 0 && cdCleared === 0 && !blockedAfter;
    send(ctx, "AP_" + tag + "_LOCK_OK:" + (ok ? 1 : 0));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doRailgunFriendly(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var ChargeManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.item.ChargeManager");

    // ── B6 ②(2026-09-15):原先由**用例注入**的两条原版准备命令改在探针内跑 ────────────
    //    `runCmd` 走 `performPrefixedCommand`(同进程、无键盘注入),每条省 ≥2.65 s。
    //    ① `gamerule doFireTick false` + ② 把脚下 y-1 那一层换成石头 —— 否则落雷点燃地面,
    //    **玩家自己的掉血读数会混入火焰伤害**(2026-09-15 实测 1.20.1 self=4.83~18.83 全部
    //    来自火焰、与雷击无关;石头地面 + 关火焰蔓延后 self=0)。
    //    两条都在**摆靶之前**下发;`fill` 只动 y-1 那一层地面,不碰摆在同一水平面的靶子。
    var prepRule = runCmd(ctx, "gamerule doFireTick false");
    var prepFill = runCmd(ctx, "fill ~-8 ~-1 ~-6 ~8 ~-1 ~14 minecraft:stone");
    send(ctx, "AP_" + tag + "_PREP:rule=" + prepRule + ":fill=" + prepFill);

    var weather = "skip";
    try { p.level.setWeatherParameters(6000, 0, false, false); weather = "clear"; }
    catch (e0) { weather = "err"; }
    // 生存模式:创造模式下玩家对雷击免伤,读不到「雷击是否打到自己」
    var mode = "already_survival";
    try { p.setGameMode(GameTypeClass.SURVIVAL); mode = "forced_survival"; } catch (e1) { /* 忽略 */ }
    try { p.setHealth(p.getMaxHealth()); } catch (e2) { /* 忽略 */ }

    ModAttachments.setRailgunCooldownEnd(p, 0);
    ChargeManagerClass.removeAll(p);
    ChargeManagerClass.addStacks(p, 6);
    var slotErr = ensureChipSlot(p, CHIP_SLOT_MIN);
    if (slotErr != null) { send(ctx, "AP_" + tag + "_ERR:" + slotErr); return 0; }
    var chip = resolveItem("astral_dice:railgun_chip");
    if (chip == null) { send(ctx, "AP_" + tag + "_ERR:unknown_chip"); return 0; }
    var putErr = putInSlot(p, "chip", new ItemStack(chip), 0);
    if (putErr != null) { send(ctx, "AP_" + tag + "_ERR:" + putErr); return 0; }

    // 敌方(僵尸)在正前方 2 格 —— 近战距离,攻击者本人必在同一雷击判定箱内;
    // 中立(北极熊)与友方(已驯服狼,主人=玩家)分列左右各 2.8 格:
    //   ⚠️ 2026-09-15 实测标定:原 1.5 格会落进**玩家近战横扫(sweep)判定盒**
    //   (目标 AABB 外扩 1.0 格),使「中立/友方掉血」读数被横扫污染(实测北极熊 24.5、
    //   狼 11 点伤害在**落雷之前**就已结算,而落雷本身 0 伤害)。2.8 格同时满足:
    //   ① 在雷击判定箱(落点 ±3 格)内 → 仍会被雷击命中;② 在横扫盒(≈1.7 格)之外 → 悬置。
    // 清场(2026-09-14 实测):自然刷新的苦力怕一旦落进雷击箱,会被劈成高压苦力怕并爆炸,
    // 污染所有差值读数(实测 bolt_delta=2、非靶实体凭空掉血),故先清掉附近的苦力怕再摆靶。
    // 环境清场(2026-09-15 补),两处都在**摆靶之前**下发:
    //   ① 苦力怕:落进雷击判定箱会被劈成高压苦力怕并爆炸,污染全部差值读数(原有步骤);
    //   ② 玩家 24 格内**预先存在**的敌对生物:实测 1.20.1 本轮玩家在落点被僵尸围殴致死
    //      (php 20→0、self=19),整轮读数随之作废。
    // ⚠️ **绝不能**把本探针自己摆的靶类型(北极熊/狼/海龟/村民)列进来:命令在 1.21.1 上是
    //    **延迟到本 tick 末**才执行的(见 runCmd 注释),而靶子是同一 tick 内
    //    addFreshEntity 直接入世的 → 开场清场会把刚摆下的靶一起杀掉(2026-09-15 实测:
    //    4 个靶全灭、valive=0:talive=0、bolt_delta=0、充能不消耗)。同类残留的清场改放
    //    doRailgunFriendlyEnd(那里本 tick 不再生成任何实体)。
    runCmd(ctx, "kill @e[type=minecraft:creeper]");
    ["minecraft:zombie", "minecraft:skeleton", "minecraft:husk", "minecraft:drowned"].forEach(function (t) {
        runCmd(ctx, "kill @e[type=" + t + ",distance=..24]");
    });
    var enemy = spawnDummy(p, "minecraft:spider", 2);
    if (enemy == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed_enemy"); return 0; }
    var neutral = spawnDummy(p, "minecraft:polar_bear", 2);
    var friendly = spawnDummy(p, "minecraft:wolf", 2);
    var turtle = spawnDummy(p, "minecraft:turtle", 2);
    var villager = spawnDummy(p, "minecraft:villager", 2);
    if (neutral == null || friendly == null || turtle == null || villager == null) {
        send(ctx, "AP_" + tag + "_ERR:spawn_failed_side"); return 0;
    }
    try { placeAt(neutral, p.getX() + 2.8, p.getY(), p.getZ() + 2.0); } catch (e3) { /* 忽略 */ }
    try { placeAt(friendly, p.getX() - 2.8, p.getY(), p.getZ() + 2.0); } catch (e4) { /* 忽略 */ }
    try { placeAt(turtle, p.getX() + 2.5, p.getY(), p.getZ() + 2.0); } catch (e4b) { /* 忽略 */ }
    try { placeAt(villager, p.getX() - 2.5, p.getY(), p.getZ() + 2.0); } catch (e4c) { /* 忽略 */ }
    var tameState = "skip";
    var ownerState = "skip";
    try { friendly.setTame(true, true); tameState = "1"; }
    catch (e5) { tameState = "ERR:" + exText(e5); }
    var puuid = playerUuid(p);
    if (puuid.ok) {
        try { friendly.setOwnerUUID(puuid.value); ownerState = "1"; }
        catch (e6) { ownerState = "ERR:" + exText(e6); }
    } else {
        ownerState = "0";
    }
    send(ctx, "AP_" + tag + "_TAME:tame=" + tameState + ":owner=" + ownerState + ":src=" + puuid.src);
    // 激怒两只中立生物:北极熊(非 Enemy)→ 应被计入敌对目标;已驯服狼(主人=攻击者)→ 应被排除。
    // NoAI 下 NeutralMob 的 anger 计时不会递减,足够撑到 1 秒后的落雷。
    //
    // ⚠️ 2026-09-15 修复(两轮迭代,第一轮走 UUID 失败记录在案):原写法 `@e[type=…,limit=1]`
    //    选中的是**全世界最近的同类实体**,不是本探针刚摆下的那只 —— 上一轮 railgunfriendlyend
    //    注入丢失留下的、已被激怒的残留靶会顶替成为目标(实测 1.20.1 的 ANGER_PRE 直接读到
    //    nAngry=true,而新摆的熊不可能自带愤怒),残留者又落在雷击判定箱内额外各生成一道雷击。
    //    第一轮改用 `e.getStringUUID()` 在**两个版本**都被 Rhino 拒绝
    //    (Cannot find function getStringUUID;同 playerUuid 注释里记的 getUUID 可见性问题),
    //    于是激怒步在两版都没执行。现在直接调 **Java API**(NeutralMob#setRemainingPersistentAngerTime
    //    —— 同一接口的 isAngry() 本文件一直在用,可见性没问题),不依赖选择器也不依赖 UUID;
    //    仅在 API 失败时回退到按类型选择器的命令,并把实际路径暴露在读数里
    //    (api / cmd:… / ERR:…)以免"取不到就当没驯服"式的静默降级。
    function angerCmd(e, typeId, ticks) {
        if (e == null) { return "null_entity"; }
        try { e.setRemainingPersistentAngerTime(ticks); return "api"; }
        catch (e9) {
            try { return "cmd:" + runCmd(ctx, "data merge entity @e[type=" + typeId + ",limit=1] {AngerTime:" + ticks + "}"); }
            catch (e10) { return "ERR:" + exText(e9); }
        }
    }
    angerCmd(neutral, "minecraft:polar_bear", 1200);
    angerCmd(friendly, "minecraft:wolf", 1200);
    function angerOf(e) { return (e == null) ? "?" : (e.isAlive() ? "alive" : "dead"); }
    function angryFlag(e) {
        try { return "" + e.isAngry(); } catch (e1) { return "ERR:" + exText(e1); }
    }
    send(ctx, "AP_" + tag + "_ANGER_PRE:neutral=" + angerOf(neutral) + ":friendly=" + angerOf(friendly)
        + ":nAngry=" + angryFlag(neutral) + ":fAngry=" + angryFlag(friendly));
    var mergeN = angerCmd(neutral, "minecraft:polar_bear", 1200);
    var mergeF = angerCmd(friendly, "minecraft:wolf", 1200);
    send(ctx, "AP_" + tag + "_ANGER_POST:mergeN=" + mergeN + ":mergeF=" + mergeF
        + ":nAngry=" + angryFlag(neutral) + ":fAngry=" + angryFlag(friendly));

    var boltBase = boltSpawnCount;
    // ── C0（2026-09-15 B2）：怒气前置的**原版 NBT 回读**必须在触发赐福/落雷**之前**取下 ──
    // 用例侧原先在臂装步之后注入同一条命令，但注入器每条命令的固定按键序列约 2.5–3 s，
    // 而本命令的 `meleeHit` 会立刻触发骰神赐福、落雷只等 ~1 s 就结算，30 HP 的北极熊
    // 在窗口内 hp 30→0 并从世界消失 ⇒ 那条 `/data get` 永远回「未找到实体」，
    // 断言 `北极熊拥有以下实体数据：\d+` **在时序上不可达**（B1 实测 25/26）。
    // 这里由探针**自己**在近战之前跑同一条原版命令：输出同样进聊天栏 → 落 latest.log，
    // 断言文本一字未改（不是弱化断言，也不是换成探针私有读数），只是把取证时刻前移。
    // 命令走 performPrefixedCommand，1.21.1 上延迟到本 tick 末执行，而落雷在 20 tick 后，
    // 故读数必然作用在**仍存活**的靶上。
    var angerNbt = runCmd(ctx, "data get entity @e[type=minecraft:polar_bear,limit=1] AngerTime");
    send(ctx, "AP_" + tag + "_ANGER_NBT:" + angerNbt);
    var php = rghp(p), ehp = rghp(enemy), nhp = rghp(neutral), fhp = rghp(friendly);
    var thp = rghp(turtle), vhp = rghp(villager);
    send(ctx, "AP_" + tag + "_BEFORE:php=" + php + ":ehp=" + ehp + ":nhp=" + nhp + ":fhp=" + fhp
        + ":thp=" + thp + ":vhp=" + vhp
        + ":charge=" + ChargeManagerClass.getStacks(p) + ":mode=" + mode + ":weather=" + weather);

    var hit = meleeHit(p, enemy);
    send(ctx, "AP_" + tag + "_MELEE:" + hit.api + ":dealt=" + hit.dealt);
    // 延迟结算口径复核:攻击瞬间充能**不应**被扣(充能与冷却都在雷击真正落下时才结算)
    send(ctx, "AP_" + tag + "_CHARGE_AT_ATTACK:" + ChargeManagerClass.getStacks(p));

    rgfState = {
        tag: tag, player: p, enemy: enemy, neutral: neutral, friendly: friendly,
        turtle: turtle, villager: villager,
        php: php, ehp: ehp, nhp: nhp, fhp: fhp, thp: thp, vhp: vhp, boltBase: boltBase
    };
    send(ctx, "AP_" + tag + "_ARMED");
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doRailgunFriendlyRead(ctx, tag) {
    var st = rgfState;
    if (st == null) { send(ctx, "AP_" + tag + "_ERR:no_state"); return 0; }
    // B6 ③:只读相位同时调 dump(只读) —— 出牌锁/立牌原始值由 LOCKRAW 与 SIGN 组给出。
    // B6 ②:原用例注入的 `/data get entity @e[type=minecraft:wolf,limit=1] Owner` 折叠到
    //   探针内(省一条 ≥2.65 s 的键盘注入)。`performPrefixedCommand` **不抑制输出**,
    //   同一条原版命令的同一句回显(「狼拥有以下实体数据：[I; …]」)照旧落 latest.log,
    //   用例断言文本一字未改(本文件上游 `data get … AngerTime` 已有同样的既成事实)。
    dumpState(ctx, tag);
    //   ⚠️ 只在 PET-EXCLUDE(tag=PE)下跑这条回读:它的回显文本(「狼拥有以下实体数据：[I; …]」)
    //   是 PET-EXCLUDE 的断言落点,而断言窗口跨用例共享 —— 若四条 railgun 用例都跑这条回读,
    //   按名字序先跑的 railgun-aoe / railgun-override 就会先把该断言**喂饱** ⇒ 等于把
    //   PET-EXCLUDE 的这条断言弱化成"任何一轮都能过"。故按 tag 收口,不做无差别折叠。
    if (tag === "PE") {
        var ownerRead = runCmd(ctx, "data get entity @e[type=minecraft:wolf,limit=1] Owner");
        send(ctx, "AP_" + tag + "_OWNER:" + ownerRead);
    }
    var ChargeManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.item.ChargeManager");
    function dealt(before, now) {
        if (before < 0 || now < 0) return -1;
        return Math.round((before - now) * 100) / 100;
    }
    var php = rghp(st.player), ehp = rghp(st.enemy), nhp = rghp(st.neutral), fhp = rghp(st.friendly);
    var thp = rghp(st.turtle), vhp = rghp(st.villager);
    var self = dealt(st.php, php), enemy = dealt(st.ehp, ehp);
    var neutral = dealt(st.nhp, nhp), friendly = dealt(st.fhp, fhp);
    var turtle = dealt(st.thp, thp), villager = dealt(st.vhp, vhp);
    var boltDelta = boltSpawnCount - st.boltBase;
    // 村民若被转成女巫,原实体会被移除 → isAlive() 变 false;这里必须是 1。
    var valive = (st.villager != null && st.villager.isAlive()) ? 1 : 0;
    var talive = (st.turtle != null && st.turtle.isAlive()) ? 1 : 0;
    send(ctx, "AP_" + tag + "_AFTER:php=" + php + ":ehp=" + ehp + ":nhp=" + nhp + ":fhp=" + fhp
        + ":thp=" + thp + ":vhp=" + vhp
        + ":self=" + self + ":enemy=" + enemy + ":neutral=" + neutral + ":friendly=" + friendly
        + ":turtle=" + turtle + ":villager=" + villager
        + ":valive=" + valive + ":talive=" + talive
        + ":bolt_delta=" + boltDelta + ":charge=" + ChargeManagerClass.getStacks(st.player));
    function hit(x) { return x > 0 ? 1 : 0; }
    send(ctx, "AP_" + tag + "_VERDICT:self=" + hit(self) + ":enemy=" + hit(enemy)
        + ":neutral=" + hit(neutral) + ":friendly=" + hit(friendly)
        + ":turtle=" + hit(turtle) + ":villager=" + hit(villager)
        + ":valive=" + valive + ":talive=" + talive);
    // 期望:self=0 enemy=1 neutral=1 friendly=0 turtle=0 villager=0 valive=1 talive=1
    var okScope = (hit(self) === 0 && hit(enemy) === 1 && hit(neutral) === 1
        && hit(friendly) === 0 && hit(turtle) === 0 && hit(villager) === 0
        && valive === 1 && talive === 1);
    send(ctx, "AP_" + tag + "_SCOPE_OK:" + (okScope ? 1 : 0));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doRailgunFriendlyEnd(ctx, tag) {
    var st = rgfState;
    var p = ctx.source.getPlayerOrException();
    if (st != null) {
        try { st.enemy.discard(); } catch (e1) { /* 忽略 */ }
        try { st.neutral.discard(); } catch (e2) { /* 忽略 */ }
        try { st.friendly.discard(); } catch (e3) { /* 忽略 */ }
        try { st.turtle.discard(); } catch (e3b) { /* 忽略 */ }
        try { st.villager.discard(); } catch (e3c) { /* 忽略 */ }
    }
    // 残留清场(2026-09-15 补):本函数是每轮收尾的唯一入口。若某一轮的 railgunfriendlyend
    // 注入丢失,被激怒的靶就会留在世界里 —— 下一轮它们落在雷击判定箱内会额外各生成一道雷击,
    // 污染全局雷击计数(实测 bolt_delta 1.20.1=5/6 ↔ 1.21.1=2)。放在收尾而不是开场:命令在
    // 1.21.1 上延迟到本 tick 末执行,开场清场会误杀同一 tick 新摆的靶(见 doRailgunFriendly 注释)。
    ["minecraft:polar_bear", "minecraft:wolf", "minecraft:turtle", "minecraft:villager"].forEach(function (t) {
        runCmd(ctx, "kill @e[type=" + t + ",distance=..16]");
    });
    rgfState = null;
    var restore = "skip";
    try { p.setHealth(p.getMaxHealth()); p.setGameMode(GameTypeClass.CREATIVE); restore = "creative"; }
    catch (e4) { restore = "ERR:" + exText(e4); }
    try {
        var ChargeManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.item.ChargeManager");
        ChargeManagerClass.removeAll(p);
    } catch (e5) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_RESTORE:" + restore);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ════════════════════════════════════════════════════════════════════════════
//  真伤验证(astral_dice:true_damage)
//    /astralprobe truedmg <tag>          护甲 20 / 韧性 8 的靶子上,对比本模组真伤
//                                        与可被护甲减免的 minecraft:mob_attack 的实扣
//    /astralprobe railtruedmg <tag>      装备电磁炮 + 6 充能,**空手**打一只无甲僵尸,
//                                        同时让一只重甲尸壳站在雷击判定箱内(不被近战命中)
//    /astralprobe railtruedmgread <tag>  读差值:空手基伤 1 → 雷击真伤全额 ≈5.5;
//                                        若雷击仍被护甲减免则 ≈1.2
//  状态改写一律走原版命令入口(/attribute、/damage、/item),避开 Rhino 方法可见性坑;
//  靶子用**不同实体类型**以便用类型选择器唯一定位(避免 @e[sort=nearest] 歧义)。
// ════════════════════════════════════════════════════════════════════════════

/** 跑一条原版命令;返回 rc=<performPrefixedCommand 返回值> 或 ERR:<异常>
 *  ⚠️ 不能用 `MinecraftServer#runCommandSilent` —— 实测(1.21.1/1.20.1, Rhino)它返回 undefined
 *  且**命令根本没有执行**(属性/伤害都静默丢失,读数会伪装成「伤害为 0」)。
 *  这里走 `getCommands().performPrefixedCommand(source, cmd)`:返回 1/0 = 成功/失败,
 *  并且**不抑制输出**,`data get ...` 之类的结果会进聊天栏 → 落日志,可当正向证据。
 *  以**玩家**为命令源(权限足够,且位置与靶子一致)。 */
function runCmd(ctx, cmd) {
    try {
        var p = ctx.source.getPlayerOrException();
        var src = p.createCommandSourceStack();
        return "rc=" + p.level.getServer().getCommands().performPrefixedCommand(src, cmd);
    } catch (e) { return "ERR:" + exText(e); }
}

// ════════════════════════════════════════════════════════════════════════════
//  B6(2026-09-15)新增:只读读数统一出口 + OP 前置只读断言
// ════════════════════════════════════════════════════════════════════════════

/**
 * 只读读数统一出口:跑 `/astralparty dump`(产品侧**只读**子命令,`hasPermission(2)`)。
 *
 * 为什么由探针调它:探针原来对同一批原始附件"每个特性自己发明读数口号",断言落点因此与
 * 原始值隔了一层派生写法。`dump` 把这些原始值按**固定机器格式** `APDUMP|<组>|<键>=<值>`
 * 同时打到聊天栏与 `LOGGER.info`(必然进 `latest.log`),用例断言可以直接锚定**原始值**
 * (`LOCKRAW` / `SIGN` / `EFFECTS` / `PENDING`)。
 *
 * 红线(必须遵守,勿删注释):
 *  ① `dump` 只用于**读数**。不得用它替代被测动作 —— 真实出牌 / `registerPlay` / `tick` /
 *     各相位里的真实生产入口一律照旧,本函数本身**不写任何状态**;
 *  ② 断言只许锚定**原始值组**:`LOCKDERIVED` 组与 `SIGN|is_sign_active_locked` 行尾带
 *     `|assert=forbidden`(它们是判定入口,断言它们等于拿被测功能验证自身);
 *  ③ 命令缺失/失败**必须**显式失败:返回串不是 `rc=1` 时另打一条
 *     `AP_<tag>_DUMP_ERR:`,用例侧对它做 absent 断言 ⇒ 落 FAIL/ERROR,**绝不静默降级**
 *     (旧版无此能力时,"读数缺失"会被当成"未命中",把工具链故障伪装成产品缺陷)。
 */
function dumpState(ctx, tag) {
    var rc = runCmd(ctx, "astralparty dump");
    var s = "" + rc;
    send(ctx, "AP_" + tag + "_DUMP:" + s);
    // ⚠️ **不能**用「期望 rc=1」作成功判据:1.21.1 的 Rhino 下 `performPrefixedCommand`
    // 返回 **undefined**(命令其实执行了),1.20.1 才返回 1/2(TESTING-SPEC §10-11 实测)。
    // 可靠判据是**解析失败面**:未知命令 / 语法错误时 `Commands#performPrefixedCommand`
    // 走 catch 分支返回 **rc=0**(两版本一致);探针自身异常则是 `ERR:` 前缀。
    // 真正的"dump 生效"证据是 `APDUMP|` 原始值行本身(用例侧断言锚定它)。
    if (s === "rc=0" || s.indexOf("ERR:") === 0) { send(ctx, "AP_" + tag + "_DUMP_ERR:" + s); }
    // ⚠️⚠️ **必须 `return 1;`**(2026-09-27 实机):Brigadier 的 `executes` 处理器必须返回 **int**,
    // 而 `runCmd` 返回的是**字符串**(1.21.1 的 `performPrefixedCommand` 返回 void ⇒ `"rc=undefined"`)。
    // 写 `return rc;` 会抛 `EvaluatorException: Cannot convert rc=undefined to int`,**且该异常同样
    // 逃出 `guard`/`try-catch`**;更严重的是它**打断同一 tick 里排队执行的命令** —— 实测
    // `/astralprobe dumpstate RA4` 那一拍 `astralparty dump` 根本没有运行(`APDUMP` 行数 = 0),
    // 表现为"dump 一条都没跑"(而不是"读数缺失")。读数只留在上面两条消息文本里。
    // (`return rc;` 是本文件第二个「处理器返回非 int」坑;第一个见块头「踩坑」注释。)
    return 1;
}

/**
 * OP 前置的**只读**断言:`hasPermissions(2)` 的实测值(2026-09-15 B6 ④)。
 *
 * 判据与 `/astralparty` **完全相同** —— 玩家命令源上的 `CommandSourceStack#hasPermission(2)`
 * (1.21.1 `CommandSourceStack.java:390` / 1.20.1 `:174`),也就是命令注册时
 * `requires(AstralPartyCommand::hasPermission)` 用的同一个入口。数值级另经
 * `MinecraftServer#getProfilePermissions`(1.21.1 `:1759` / 1.20.1 `:1516`)取,
 * 只为把"为什么够/不够"写清楚:单人 quickplay 集成服走 `isSingleplayerOwner` 分支 ⇒ **4**。
 *
 * 红线:本函数**只读**,不改任何状态;`mt_launch` 进入世界后据此判前置,**不满足记 BLOCKED**
 * (前置不足不是产品缺陷)。**禁止**为让测试通过而降低 `requires` 门槛 / 加测试专用开关 /
 * 绕开 OP 走客户端旁路 —— 测试必须走真实 OP 路径。
 */
function opprobe(ctx) {
    var p = ctx.source.getPlayerOrException();
    var has2 = -1, level = -1, src = "none";
    try {
        var cs = p.createCommandSourceStack();
        has2 = cs.hasPermission(2) ? 1 : 0;
        src = "cmdsource";
    } catch (e1) { /* 落到下面的数值级 */ }
    try {
        level = p.level.getServer().getProfilePermissions(p.getGameProfile());
        if (src === "none") { src = "profile"; } else { src = src + "+profile"; }
    } catch (e2) {
        try {
            level = p.level.getServer().getPlayerList().getProfilePermissions(p.getGameProfile());
            if (src === "none") { src = "list"; } else { src = src + "+list"; }
        } catch (e3) { /* level 保持 -1 */ }
    }
    // 同一读数里再报一次 `/astralparty dump` 的返回值:mt_launch 用它做第二道闸门 ——
    // 本批测试资产的只读断言锚定 APDUMP| 原始值行,命令缺失/失败必须显式失败(不静默降级)。
    var dumpRc = runCmd(ctx, "astralparty dump");
    send(ctx, "AP_OP_PERM:has2=" + has2 + ":level=" + level + ":src=" + src + ":dump=" + dumpRc);
    send(ctx, "AP_OP_DONE");
    return 1;
}

/** 给唯一一只 <typeId> 靶子挂护甲/韧性(类型选择器,不依赖距离排序)。
 *  设置完再跑一次 `data get entity ... Attributes`:它的**聊天输出**会进日志,
 *  作为「护甲真的挂上了」的正向证据(命令静默失败只靠 rc 判定不够直观)。
 *
 *  ⚠️ 两条实测结论(2026-09-14,1.21.1 Rhino):
 *   ① `MinecraftServer#runCommandSilent` 返回 undefined **且命令根本不执行** —— 属性/伤害会
 *      静默丢失,读数伪装成「伤害为 0」。故本文件统一走 `performPrefixedCommand`。
 *   ② 即便走 `performPrefixedCommand`,**属性类命令的生效时机晚于同一 tick 内的后续代码**:
 *      同一命令里「先 attribute、紧接着施加伤害」读到的仍是**旧护甲值**。
 *      因此单条 `truedmg` 的判据只看**真伤是否全额**(与护甲无关),穿甲对照必须像
 *      手工判据那样**分两步**:先 attribute,下一条命令再施加伤害(见 TESTING-SPEC「真伤判据」)。 */
function armorSingle(ctx, typeId, armor, toughness) {
    var sel = "@e[type=" + typeId + ",limit=1]";
    var a = runCmd(ctx, "attribute " + sel + " minecraft:generic.armor base set " + armor);
    var t = runCmd(ctx, "attribute " + sel + " minecraft:generic.armor_toughness base set " + toughness);
    var probe = runCmd(ctx, "data get entity " + sel + " Attributes");
    return "armor=" + a + ":tough=" + t + ":read=" + probe;
}

/** 真伤 vs 可减免伤害:同一只重甲靶子(护甲 20 / 韧性 8)上各打 10 点,对比实扣 */
function doTrueDmg(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var weather = "skip";
    try { p.level.setWeatherParameters(6000, 0, false, false); weather = "clear"; } catch (e0) { weather = "err"; }
    // 两只不同实体类型 → 类型选择器可唯一定位;真伤靶=僵尸,对照靶=尸壳(互不共享无敌帧)
    var mob = spawnDummy(p, "minecraft:spider", 2);
    if (mob == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed"); return 0; }
    var ctrl = spawnDummy(p, "minecraft:vindicator", 3);
    if (ctrl == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed_ctrl"); return 0; }
    try { placeAt(ctrl, p.getX() - 1.5, p.getY(), p.getZ() + 3.0); } catch (e9) { /* 忽略 */ }
    try { mob.setHealth(mob.getMaxHealth()); } catch (e1) { /* 忽略 */ }
    try { ctrl.setHealth(ctrl.getMaxHealth()); } catch (e1b) { /* 忽略 */ }

    var armorState = armorSingle(ctx, "minecraft:spider", 20, 8);
    var ctrlArmor = armorSingle(ctx, "minecraft:vindicator", 20, 8);

    var ResourceKey = Java.loadClass("net.minecraft.resources.ResourceKey");
    var Registries = Java.loadClass("net.minecraft.core.registries.Registries");
    /** 按注册 id 造伤害源 */
    function sourceOf(id) {
        return p.level.damageSources().source(ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.parse(id)));
    }
    /** 经 KubeJS 可见的伤害 API 施加(实测 `attack(DamageSource,float)` 可用;
     *  `damage` 的 KubeJS 参数顺序是 `(amount, source)`,见 applyFallDamage 的 B3 更正) */
    function applyDamage(ent, src, amount) {
        try { ent.attack(src, amount); return "attack"; } catch (ea) { /* 试下一个 */ }
        try { ent.damage(amount, src); return "damage"; } catch (eb) { /* 试下一个 */ }
        return "none";
    }
    function delta(a, b) { return (a < 0 || b < 0) ? -1 : Math.round((a - b) * 100) / 100; }

    // 1) 本模组真伤:期望**全额** 10 点(护甲 20/韧性 8 下若不穿甲只应掉约 3 点)
    var hp0 = rghp(mob), api1;
    try { api1 = applyDamage(mob, sourceOf("astral_dice:true_damage"), 10.0); }
    catch (e1a) { api1 = "ERR:" + exText(e1a); }
    var hp1 = rghp(mob);
    // 2) 对照组:minecraft:mob_attack 必须被护甲减免(护甲 20/韧性 8 下 10 点 ≈ 3 点)
    var chp0 = rghp(ctrl), api2;
    try { api2 = applyDamage(ctrl, sourceOf("minecraft:mob_attack"), 10.0); }
    catch (e2a) { api2 = "ERR:" + exText(e2a); }
    var chp1 = rghp(ctrl);

    var trueDealt = delta(hp0, hp1), vanillaDealt = delta(chp0, chp1);
    // 3) 诊断:命令路径(rc=1 才算真的执行过;runCommandSilent 会伪装成 undefined)
    var r1 = runCmd(ctx, "damage @e[type=minecraft:spider,limit=1] 10 astral_dice:true_damage");
    try { mob.discard(); } catch (e3) { /* 忽略 */ }
    try { ctrl.discard(); } catch (e4) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_ARMOR:" + armorState + ":weather=" + weather);
    send(ctx, "AP_" + tag + "_CTRL_ARMOR:" + ctrlArmor);
    send(ctx, "AP_" + tag + "_TRUE:api=" + api1 + ":hp=" + hp0 + "->" + hp1 + ":dealt=" + trueDealt);
    send(ctx, "AP_" + tag + "_VANILLA:api=" + api2 + ":hp=" + chp0 + "->" + chp1 + ":dealt=" + vanillaDealt);
    send(ctx, "AP_" + tag + "_CMD_DIAG:" + r1);
    var bypass = (trueDealt === 10 && vanillaDealt > 0 && vanillaDealt < 9.5) ? 1 : 0;
    send(ctx, "AP_" + tag + "_VERDICT:true=" + trueDealt + ":vanilla=" + vanillaDealt + ":bypass=" + bypass);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

var rtgState = null;

/** 电磁炮雷击真伤:重甲尸壳只吃雷击(不被近战命中),空手近战基伤固定 1 → 雷击 ≈5.5 */
function doRailTrueDmg(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var ChargeManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.item.ChargeManager");
    var weather = "skip";
    try { p.level.setWeatherParameters(6000, 0, false, false); weather = "clear"; } catch (e0) { weather = "err"; }
    var mode = "already_survival";
    try { p.setGameMode(GameTypeClass.SURVIVAL); mode = "forced_survival"; } catch (e1) { /* 忽略 */ }
    try { p.setHealth(p.getMaxHealth()); } catch (e2) { /* 忽略 */ }
    // 空手:近战基伤 1(空手不是近战武器 → 骰战不改写伤害,雷击直接按即时伤害登记)
    var hand = runCmd(ctx, "item replace entity @s weapon.mainhand with minecraft:air");
    var dice = "skip";
    try { clearCurioSlots(p, "dice"); dice = "cleared"; } catch (e3) { dice = "ERR:" + exText(e3); }

    ModAttachments.setRailgunCooldownEnd(p, 0);
    ChargeManagerClass.removeAll(p);
    ChargeManagerClass.addStacks(p, 6);
    var slotErr = ensureChipSlot(p, CHIP_SLOT_MIN);
    if (slotErr != null) { send(ctx, "AP_" + tag + "_ERR:" + slotErr); return 0; }
    var chip = resolveItem("astral_dice:railgun_chip");
    if (chip == null) { send(ctx, "AP_" + tag + "_ERR:unknown_chip"); return 0; }
    var putErr = putInSlot(p, "chip", new ItemStack(chip), 0);
    if (putErr != null) { send(ctx, "AP_" + tag + "_ERR:" + putErr); return 0; }

    // 近战目标:无甲僵尸(它的血量不参与判定);旁观靶:重甲尸壳(只吃雷击,无无敌帧干扰)
    var target = spawnDummy(p, "minecraft:spider", 2);
    if (target == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed_target"); return 0; }
    var probe = spawnDummy(p, "minecraft:vindicator", 2);
    if (probe == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed_probe"); return 0; }
    try { placeAt(probe, p.getX() + 1.0, p.getY(), p.getZ() + 2.0); } catch (e4) { /* 忽略 */ }
    try { probe.setHealth(probe.getMaxHealth()); } catch (e5) { /* 忽略 */ }
    var armorState = armorSingle(ctx, "minecraft:vindicator", 20, 8);

    var thp = rghp(target), php = rghp(probe);
    send(ctx, "AP_" + tag + "_BEFORE:thp=" + thp + ":php=" + php + ":" + armorState
        + ":hand=" + hand + ":dice=" + dice + ":charge=" + ChargeManagerClass.getStacks(p)
        + ":mode=" + mode + ":weather=" + weather + ":bolt_base=" + boltSpawnCount);
    var hit = meleeHit(p, target);
    send(ctx, "AP_" + tag + "_MELEE:" + hit.api + ":dealt=" + hit.dealt);
    rtgState = { tag: tag, target: target, probe: probe, thp: thp, php: php, player: p, boltBase: boltSpawnCount };
    send(ctx, "AP_" + tag + "_ARMED");
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doRailTrueDmgRead(ctx, tag) {
    var st = rtgState;
    if (st == null) { send(ctx, "AP_" + tag + "_ERR:no_state"); return 0; }
    // B6 ③:只读相位同时调 dump(只读)。靶子血量的差值读数不在 dump 的能力边界内
    // (dump 只输出本模组自身状态,不输出实体血量),故下面的 HP 差值逻辑保留。
    dumpState(ctx, tag);
    var ChargeManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.item.ChargeManager");
    var thp = rghp(st.target), php = rghp(st.probe);
    function delta(a, b) { return (a < 0 || b < 0) ? -1 : Math.round((a - b) * 100) / 100; }
    var probeDealt = delta(st.php, php), targetDealt = delta(st.thp, thp);
    // 重甲旁观靶只吃雷击:真伤 ≈5.5(空手基伤 1);若仍被护甲减免则 ≈1.2
    var boltTrue = (probeDealt >= 4.5) ? 1 : 0;
    send(ctx, "AP_" + tag + "_AFTER:thp=" + thp + ":php=" + php + ":probe_dealt=" + probeDealt
        + ":target_dealt=" + targetDealt + ":charge=" + ChargeManagerClass.getStacks(st.player)
        + ":bolt_delta=" + (boltSpawnCount - st.boltBase));
    send(ctx, "AP_" + tag + "_VERDICT:probe_dealt=" + probeDealt + ":bolt_true_damage=" + boltTrue);
    try { st.target.discard(); } catch (e1) { /* 忽略 */ }
    try { st.probe.discard(); } catch (e2) { /* 忽略 */ }
    rtgState = null;
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ════════════════════════════════════════════════════════════════════════════
//  大当家「战斗爽·溅射」取证:6 格 / 88% / 真伤(无视护甲值与盔甲韧性)
//    /astralprobe fensplash <tag>      阶段1:装备大当家立牌 + 骰子 + 铁剑,养精蓄锐置 5,
//                                      摆 4 只靶子并给重甲靶挂护甲 20/韧性 8
//    /astralprobe fensplashhit <tag>   阶段2(下一条命令):近战命中主靶 → 触发骰神赐福 →
//                                      满层溅射立即引爆
//    /astralprobe fensplashread <tag>  阶段3:读差值并判定,然后收尾
//
//  判据(全部在游戏内计算):
//    · 范围:4.5 格靶掉血(旧范围 3 格打不到)、8 格靶必须 0(新范围 6 格)
//    · 真伤:重甲靶与无甲靶**掉血相同**(若吃护甲,重甲靶只应掉约 1/4)
//    · 88%:溅射值 ÷ 近战部分 ≈ 0.88(近战部分 = 主靶掉血 − 溅射值)
//  靶子一律**非亡灵敌对生物**(蜘蛛/苦力怕/女巫/掠夺者):亡灵白天被太阳点燃会持续掉血,
//  污染差值读数(用户 2026-09-14 要求);且全部 setNoAi,不会反击/爆炸。
//  ⚠️ 属性命令的生效时机晚于同一 tick 内的后续代码,故护甲必须在**上一条命令**里挂好。
// ════════════════════════════════════════════════════════════════════════════

var fenState = null;

/** 阶段1:装备 + 摆靶(含重甲) */
function doFenSplash(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var FenSignItem = Java.loadClass("com.merlinkitsune.astral_dice.item.sign.FenSignItem");
    var weather = "skip";
    try { p.level.setWeatherParameters(6000, 0, false, false); weather = "clear"; } catch (e0) { weather = "err"; }
    try { p.setHealth(p.getMaxHealth()); } catch (e1) { /* 忽略 */ }

    clearCurioSlots(p, "stand");
    clearCurioSlots(p, "chip");
    var signErr = equipSign(p, "astral_dice:fen_sign");
    var diceItem = resolveItem("astral_dice:dice");
    var diceErr = diceItem == null ? "unknown_dice" : putInSlot(p, "dice", new ItemStack(diceItem), 0);
    var weapon = runCmd(ctx, "item replace entity @s weapon.mainhand with minecraft:iron_sword");
    try { ModEffectRemoval.remove(p, ModEffects.DICE_BLESSING); } catch (e2) { /* 忽略 */ }
    ModAttachments.setFenRecharge(p, 5);
    var equipped = "?";
    try { equipped = "" + FenSignItem.isEquipped(p); } catch (e3) { equipped = "ERR:" + exText(e3); }

    var target = spawnDummy(p, "minecraft:spider", 2);
    var near = spawnDummy(p, "minecraft:vindicator", 2);
    var armd = spawnDummy(p, "minecraft:pillager", 2);
    var far = spawnDummy(p, "minecraft:blaze", 2);
    if (target == null || near == null || armd == null || far == null) {
        send(ctx, "AP_" + tag + "_ERR:spawn_failed"); return 0;
    }
    try { placeAt(near, p.getX() - 4.5, p.getY(), p.getZ() + 2.0); } catch (e4) { /* 忽略 */ }
    try { placeAt(armd, p.getX() + 4.5, p.getY(), p.getZ() + 2.0); } catch (e5) { /* 忽略 */ }
    try { placeAt(far, p.getX(), p.getY(), p.getZ() + 11.0); } catch (e6) { /* 忽略 */ }
    try { target.setHealth(target.getMaxHealth()); } catch (e7) { /* 忽略 */ }
    try { near.setHealth(near.getMaxHealth()); } catch (e8) { /* 忽略 */ }
    try { armd.setHealth(armd.getMaxHealth()); } catch (e9) { /* 忽略 */ }
    try { far.setHealth(far.getMaxHealth()); } catch (e10) { /* 忽略 */ }
    var armorState = armorSingle(ctx, "minecraft:pillager", 20, 8);

    send(ctx, "AP_" + tag + "_EQUIP:sign=" + signErr + ":dice=" + diceErr + ":weapon=" + weapon
        + ":equipped=" + equipped + ":recharge=" + ModAttachments.getFenRecharge(p)
        + ":weather=" + weather);
    send(ctx, "AP_" + tag + "_ARMOR:" + armorState);
    send(ctx, "AP_" + tag + "_BEFORE:thp=" + rghp(target) + ":nhp=" + rghp(near)
        + ":ahp=" + rghp(armd) + ":fhp=" + rghp(far));
    fenState = { tag: tag, target: target, near: near, armd: armd, far: far, player: p,
                 thp: rghp(target), nhp: rghp(near), ahp: rghp(armd), fhp: rghp(far) };
    // B6 ②:原用例注入的 `/effect clear @s` 折叠到探针内(省一条 ≥2.65 s 的键盘注入)。
    // 刻意放在**本相位末尾**而不是命中相位开头:`performPrefixedCommand` 在 1.21.1 上被
    // **推迟到本 tick 末**执行(见本文件多处实测注释),若放在命中相位开头,清除会晚于
    // `meleeHit` ⇒ 赐福不会重新触发(实测现象:recharge 停在 5、in_range=0)。
    // 放在这里则被用例原有的 1500 ms 等待完全吸收。
    var preClear = runCmd(ctx, "effect clear @s");
    send(ctx, "AP_" + tag + "_CLEAR:" + preClear);
    send(ctx, "AP_" + tag + "_SETUP_DONE");
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 阶段2:近战命中主靶(触发赐福 → 满层溅射立即引爆) */
function doFenSplashHit(ctx, tag) {
    var st = fenState;
    if (st == null) { send(ctx, "AP_" + tag + "_ERR:no_state"); return 0; }
    var hit = meleeHit(st.player, st.target);
    // 命中瞬间快照:与读阶段的差值对比,可区分「溅射当场伤害」与「之后的漂移(如自愈/着火)」
    send(ctx, "AP_" + tag + "_SNAP:thp=" + rghp(st.target) + ":nhp=" + rghp(st.near)
        + ":ahp=" + rghp(st.armd) + ":fhp=" + rghp(st.far));
    var blessed = "?";
    try { blessed = "" + st.player.hasEffect(ModEffects.DICE_BLESSING); } catch (e1) { blessed = "ERR:" + exText(e1); }
    send(ctx, "AP_" + tag + "_MELEE:" + hit.api + ":dealt=" + hit.dealt
        + ":blessing=" + blessed + ":recharge=" + ModAttachments.getFenRecharge(st.player));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 阶段3:读差值 + 判定 + 收尾 */
function doFenSplashRead(ctx, tag) {
    var st = fenState;
    if (st == null) { send(ctx, "AP_" + tag + "_ERR:no_state"); return 0; }
    // B6 ③:只读相位同时调 dump(只读)。四只靶子的血量差值不在 dump 的能力边界内
    // (dump 只输出本模组自身状态,不输出实体血量),故下面的溅射差值逻辑保留。
    dumpState(ctx, tag);
    function delta(a, b) { return (a < 0 || b < 0) ? -1 : Math.round((a - b) * 100) / 100; }
    var tD = delta(st.thp, rghp(st.target));   // 主靶:近战 + 溅射
    var nD = delta(st.nhp, rghp(st.near));     // 4.5 格 无甲:只吃溅射
    var aD = delta(st.ahp, rghp(st.armd));     // 4.5 格 重甲:只吃溅射
    var fD = delta(st.fhp, rghp(st.far));      // 8 格:范围外
    var meleeEst = Math.round((tD - nD) * 100) / 100;
    var ratio = meleeEst > 0 ? Math.round((nD / meleeEst) * 1000) / 1000 : -1;
    var inRange = (nD > 0 && aD > 0) ? 1 : 0;
    var outRange = (fD === 0) ? 1 : 0;
    var trueDmg = (nD > 0 && Math.abs(nD - aD) <= 0.01) ? 1 : 0;
    // 溅射有下限 5 点(SPLASH_DAMAGE_MIN):近战伤害小时 88% 被下限托住,比值必然 > 0.88 →
    // 判定把「正好等于下限」也算通过,并单独回报下限标志与 88% 目标值(nD/0.88)。
    var atFloor = (nD === 5) ? 1 : 0;
    var ratioOk = (atFloor === 1 || (ratio >= 0.82 && ratio <= 0.94)) ? 1 : 0;
    send(ctx, "AP_" + tag + "_AFTER:tdealt=" + tD + ":near_dealt=" + nD + ":armored_dealt=" + aD
        + ":far_dealt=" + fD + ":melee_est=" + meleeEst + ":ratio=" + ratio);
    send(ctx, "AP_" + tag + "_VERDICT:in_range=" + inRange + ":out_range=" + outRange
        + ":true_damage=" + trueDmg + ":at_floor=" + atFloor + ":ratio_ok=" + ratioOk);
    try { ModAttachments.setFenRecharge(st.player, 0); } catch (e1) { /* 忽略 */ }
    try { ModEffectRemoval.remove(st.player, ModEffects.DICE_BLESSING); } catch (e2) { /* 忽略 */ }
    try { clearCurioSlots(st.player, "stand"); } catch (e3) { /* 忽略 */ }
    try { st.target.discard(); } catch (e4) { /* 忽略 */ }
    try { st.near.discard(); } catch (e5) { /* 忽略 */ }
    try { st.armd.discard(); } catch (e6) { /* 忽略 */ }
    try { st.far.discard(); } catch (e7) { /* 忽略 */ }
    fenState = null;
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ════════════════════════════════════════════════════════════════════════════
//  伤害效果牌(法伤)加成的「真伤」口径取证(SPELL-TRUE-DAMAGE)
//    /astralprobe spelltdsetup <tag>  阶段1:清效果/清 Curios + 摆 4 只非亡灵敌对靶
//                                     (ctrl_bare / test_bare 无甲;ctrl_arm / test_arm 护甲20·韧性8)
//    /astralprobe spelltdhit   <tag>  阶段2(下一条命令,**属性命令生效时机要求分两条**):
//                                     先无效果牌打两靶(控制相位),再加「对怪激光」打另两靶(试相位)
//
//  被测链路:DamageEffectCardHandler#onLivingDamagePre —— 玩家造成的「远程/魔法」伤害命中
//   骰神赐福目标时,聚合 SpellDamageRegistry 修饰器(bonus),再把 **bonus 这部分**以
//   ModDamageTypes.trueDamage(level, player) 独立 hurt 结算(不吃护甲值/盔甲韧性);
//   基础伤害本身仍走原版链路照旧吃护甲。
//
//  判据(全部在游戏内计算):
//    · 基础伤害照旧吃护甲:ctrl_arm 的掉血 **明显小于** ctrl_bare(护甲 20/韧性 8 → 约 30%)
//    · 效果牌那部分穿甲:armored_lift ≈ bare_lift(同一个 +4 加成,重甲靶与无甲靶掉血相同)
//  说明:`astral_dice:true_damage` 只登记 bypasses_armor(未登记 bypasses_cooldown),
//   故原版无敌帧的「amount − lastHurt」差额结算会体现在**绝对值**上;两靶同条件 ⇒ 差值可比。
// ════════════════════════════════════════════════════════════════════════════
var spellState = null;

/** 读回某个靶子**真实生效**的护甲/韧性值(直接问属性实例,不信命令返回值)。
 *
 *  ⚠️ 2026-09-15 实测:KubeJS/Rhino 下 `Commands#performPrefixedCommand` 返回
 *  **undefined**(命令其实执行了),所以 `armorSingle` 的 `armor=rc=1` 判据在本机永远落空。
 *  命令是否执行要看聊天回显;数值是否生效就用本函数在**同一实体实例**上直接读。 */
function spellArmorOf(ent) {
    try {
        var Attrs = Java.loadClass("net.minecraft.world.entity.ai.attributes.Attributes");
        return "armor=" + ent.getAttributeValue(Attrs.ARMOR)
            + ":tough=" + ent.getAttributeValue(Attrs.ARMOR_TOUGHNESS);
    } catch (e) { return "ERR:" + exText(e); }
}

/** 玩家造成的「远程/魔法」伤害源。
 *
 *  ⚠️ 2026-09-15 实测标定:不能用 `damageSources().indirectMagic(p, p)` ——
 *  `minecraft:magic` / `minecraft:indirect_magic` **本身就在原版
 *  `minecraft:bypasses_armor` 标签里**(魔法伤害按原版设计无视护甲),那样「基础伤害
 *  照旧吃护甲」的对照根本不成立(实测护甲 20·韧性 8 的靶子上 1.0 点伤害全额落地)。
 *  改用**实体弹射物**承载:`damageSources().arrow(arrowEntity, player)` ——
 *  ① `getDirectEntity()` 是 {@code AbstractArrow} → 命中 SpellDamageRegistry 的
 *  「原生弹射物」matcher;② `getEntity()` 是玩家 → 满足 DamageEffectCardHandler 的
 *  施法者要求;③ `minecraft:arrow` 不在 bypasses_armor 里 → 基础伤害照旧吃护甲。
 *  箭实体只作伤害源载体,无需入世界。 */
function spellSource(p) {
    var arrow = null;
    try { arrow = Java.loadClass("net.minecraft.world.entity.EntityType").ARROW.create(p.level); }
    catch (e1) { arrow = null; }
    if (arrow == null) return null;
    try { arrow.setOwner(p); } catch (e2) { /* 忽略 */ }
    return p.level.damageSources().arrow(arrow, p);
}

/** 经 KubeJS 可见的伤害 API 施加伤害,返回 {api, dealt};api=none 表示没有任何入口可用。 */
function applySpellDamage(ent, src, amount) {
    var before = -1, after = -1;
    try { before = ent.getHealth(); } catch (e) { before = -1; }
    var api = "none";
    var errText = "";
    var tries = [
        function () { ent.hurt(src, amount); return "hurt"; },
        function () { ent.attack(src, amount); return "attack"; },
        function () { ent.damage(amount, src); return "damage"; }
    ];
    for (var i = 0; i < tries.length; i++) {
        try { api = tries[i](); break; } catch (e2) { errText += "/" + exText(e2); }
    }
    try { after = ent.getHealth(); } catch (e3) { after = -1; }
    return {
        api: api, err: errText.substring(0, 200),
        dealt: (before < 0 || after < 0) ? -1 : Math.round((before - after) * 100) / 100,
        hp: (before < 0 || after < 0) ? "?" : (before + "->" + after)
    };
}

/** 阶段1:清场 + 摆靶(含重甲) */
function doSpellTdSetup(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var weather = "skip";
    try { p.level.setWeatherParameters(6000, 0, false, false); weather = "clear"; } catch (e0) { weather = "err"; }
    try { p.setHealth(p.getMaxHealth()); } catch (eh) { /* 忽略 */ }
    runCmd(ctx, "effect clear @s");
    try { clearCurioSlots(p, "dice"); } catch (e1) { /* 忽略 */ }
    try { clearCurioSlots(p, "chip"); } catch (e2) { /* 忽略 */ }
    try { clearCurioSlots(p, "stand"); } catch (e3) { /* 忽略 */ }
    // 忍者立牌「效果牌伤害增益」是附件(卸下不自动清) → 显式归零,否则 extra_bonus 不确定
    try { ModAttachments.setKomachiDamageBonus(p, 0); } catch (e4) { /* 忽略 */ }

    var ctrlBare = spawnDummy(p, "minecraft:spider", 2);
    var testBare = spawnDummy(p, "minecraft:spider", 2);
    var ctrlArm = spawnDummy(p, "minecraft:vindicator", 2);
    var testArm = spawnDummy(p, "minecraft:pillager", 2);
    if (ctrlBare == null || testBare == null || ctrlArm == null || testArm == null) {
        send(ctx, "AP_" + tag + "_ERR:spawn_failed"); return 0;
    }
    // 分散摆放(两两相距 ≥4 格):避免任何 AOE/连锁读数互相污染
    try { placeAt(ctrlBare, p.getX() - 6.0, p.getY(), p.getZ() + 3.0); } catch (e5) { /* 忽略 */ }
    try { placeAt(testBare, p.getX() - 2.0, p.getY(), p.getZ() + 3.0); } catch (e6) { /* 忽略 */ }
    try { placeAt(ctrlArm, p.getX() + 2.0, p.getY(), p.getZ() + 3.0); } catch (e7) { /* 忽略 */ }
    try { placeAt(testArm, p.getX() + 6.0, p.getY(), p.getZ() + 3.0); } catch (e8) { /* 忽略 */ }
    var all = [ctrlBare, testBare, ctrlArm, testArm];
    for (var i = 0; i < all.length; i++) {
        try { all[i].setHealth(all[i].getMaxHealth()); } catch (e9) { /* 忽略 */ }
        try { all[i].setNoAi(true); } catch (e10) { /* 忽略 */ }
    }
    var armCtrl = armorSingle(ctx, "minecraft:vindicator", 20, 8);
    var armTest = armorSingle(ctx, "minecraft:pillager", 20, 8);
    var bonus = "ERR";
    try { bonus = "" + SpellDamageRegistryClass.effectCardDamageBonus(p); } catch (e11) { bonus = "ERR:" + exText(e11); }
    send(ctx, "AP_" + tag + "_ARMOR_CTRL:" + armCtrl);
    send(ctx, "AP_" + tag + "_ARMOR_TEST:" + armTest);
    send(ctx, "AP_" + tag + "_ARMOR_EFFECTIVE:ctrl_arm=" + spellArmorOf(ctrlArm)
        + ":test_arm=" + spellArmorOf(testArm)
        + ":ctrl_bare=" + spellArmorOf(ctrlBare) + ":test_bare=" + spellArmorOf(testBare));
    send(ctx, "AP_" + tag + "_SETUP:ctrl_bare=" + rghp(ctrlBare) + ":test_bare=" + rghp(testBare)
        + ":ctrl_arm=" + rghp(ctrlArm) + ":test_arm=" + rghp(testArm)
        + ":extra_bonus=" + bonus + ":weather=" + weather);
    spellState = {
        tag: tag, player: p, ctrlBare: ctrlBare, testBare: testBare,
        ctrlArm: ctrlArm, testArm: testArm
    };
    send(ctx, "AP_" + tag + "_SETUP_DONE");
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 阶段2:控制相位(无效果牌) → 试相位(对怪激光 +4) → 读差值判定 → 收尾 */
function doSpellTdHit(ctx, tag) {
    var st = spellState;
    if (st == null) { send(ctx, "AP_" + tag + "_ERR:no_state"); return 0; }
    var p = st.player;
    // 干净基线:保证控制相位时身上没有任何「伤害效果牌」效果
    function clearCards() {
        var holders = [ModEffects.LIVING_PAGE, ModEffects.MONSTER_LASER, ModEffects.MONSTER_BRICK,
                       ModEffects.ORBITAL_STRIKE, ModEffects.DIRECTIONAL_BLAST];
        for (var i = 0; i < holders.length; i++) {
            try { ModEffectRemoval.remove(p, holders[i]); } catch (e) { /* 忽略 */ }
        }
    }
    clearCards();
    var src = spellSource(p);
    if (src == null) { send(ctx, "AP_" + tag + "_ERR:no_arrow_carrier"); return 0; }
    var cBare = applySpellDamage(st.ctrlBare, src, 1.0);
    var cArm = applySpellDamage(st.ctrlArm, src, 1.0);
    // 试相位:同一发伤害,但此时玩家身上有「对怪激光」(法伤 +4)
    var laser = "ok";
    try { p.addEffect(new MobEffectInstanceClass(ModEffects.MONSTER_LASER, 2400, 0)); }
    catch (e1) { laser = "ERR:" + exText(e1); }
    var bonusNow = "ERR";
    try { bonusNow = "" + SpellDamageRegistryClass.effectCardDamageBonus(p); }
    catch (e2) { bonusNow = "ERR:" + exText(e2); }
    var tBare = applySpellDamage(st.testBare, src, 1.0);
    var tArm = applySpellDamage(st.testArm, src, 1.0);

    function r2(x) { return Math.round(x * 100) / 100; }
    var bareLift = r2(tBare.dealt - cBare.dealt);
    var armLift = r2(tArm.dealt - cArm.dealt);
    var noApi = (cBare.api === "none" && tBare.api === "none") ? 1 : 0;
    // 基础伤害照旧吃护甲:重甲对照靶掉血必须明显小于无甲对照靶
    var baseArmored = (cBare.dealt > 0 && cArm.dealt > 0 && cArm.dealt < cBare.dealt) ? 1 : 0;
    // 效果牌那部分穿甲:两靶的加成掉血必须相同(且必须真的掉了血)
    var bonusImmune = (bareLift > 0 && Math.abs(bareLift - armLift) <= 0.01) ? 1 : 0;

    send(ctx, "AP_" + tag + "_API:ctrl_bare=" + cBare.api + ":ctrl_arm=" + cArm.api
        + ":test_bare=" + tBare.api + ":test_arm=" + tArm.api);
    send(ctx, "AP_" + tag + "_CTRL:bare=" + cBare.dealt + "(" + cBare.hp + "):arm=" + cArm.dealt + "(" + cArm.hp + ")");
    send(ctx, "AP_" + tag + "_TEST:bare=" + tBare.dealt + "(" + tBare.hp + "):arm=" + tArm.dealt + "(" + tArm.hp + ")");
    send(ctx, "AP_" + tag + "_CARD:laser=" + laser + ":extra_bonus=" + bonusNow);
    send(ctx, "AP_" + tag + "_AFTER:bare_lift=" + bareLift + ":armored_lift=" + armLift
        + ":base_armored=" + baseArmored + ":bonus_armor_immune=" + bonusImmune + ":no_api=" + noApi);
    send(ctx, "AP_" + tag + "_VERDICT:base_armor_effective=" + baseArmored
        + ":bonus_true_damage=" + bonusImmune + ":no_api=" + noApi);
    // 收尾
    clearCards();
    try { st.ctrlBare.discard(); } catch (e3) { /* 忽略 */ }
    try { st.testBare.discard(); } catch (e4) { /* 忽略 */ }
    try { st.ctrlArm.discard(); } catch (e5) { /* 忽略 */ }
    try { st.testArm.discard(); } catch (e6) { /* 忽略 */ }
    spellState = null;
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ════════════════════════════════════════════════════════════════════════════
// 安全气囊(airbag_chip):「无视无敌」的致死伤害必须同样被拦下(2026-09-15 用户裁决)
//   被测语义:`ChipDamageHandler` 的伤害阶段不再按 DamageTypeTags.BYPASSES_INVULNERABILITY
//   排除伤害源 —— `/kill`(= LivingEntity#kill() = hurt(generic_kill, Float.MAX_VALUE))、
//   虚空伤害与其它模组真伤在气囊前一律止步;代价仍是 6 层充能 + 1:00 冷却。
//   证据口径(四段):
//     ① `airbagprep` 造初态:强制生存 + 装备 airbag_chip + 清充能后加满 6 层 + 冷却归零 + 满血,
//        并读出全量状态(health/alive/equipped/chip/charge/cd_end/cd_left/gametime)。
//     ② `airbagkill` 用**与 /kill 完全同一条原版代码路径**施加致死:`player.kill()`
//        (LivingEntity#kill → hurt(damageSources().genericKill(), Float.MAX_VALUE));
//        若 Rhino 不暴露 kill() 则退化为显式 generic_kill 伤害源 + hurt(src, 1000),
//        实际走哪条由读数里的 path= 字段暴露。施放后**同一函数内立刻复读**。
//     ③ `airbaglethal` 同构造对照:用**同一个 kill() 调用**打一只新生成的猪(气囊只对玩家生效),
//        必须真的死亡 → 证明本局内该伤害源确实致命,排除「伤害根本没生效」的伪阳性。
//     ④ `airbagreset` 收尾:清充能/冷却、回满血、切回创造并复读。
//   ⚠️ 覆盖缺口(如实记录,见 TESTING-SPEC §10):「充能不足 / 冷却进行中时玩家真会死」这条
//   玩家侧对照**不在**本用例内 —— 玩家死亡会停在死亡界面,后续注入命令全部失效(工具链无自动重生),
//   故该分支目前只有代码层结论(charge < 6 或 isOnCooldown 时 tryNegateFatal 返回 false)。
// ════════════════════════════════════════════════════════════════════════════

/** 读 chip 槽第 0 格物品 id(核验气囊是否真的戴在身上) */
function chipSlotItemId(player) {
    try {
        var opt = CuriosApi.getCuriosInventory(player);
        if (opt == null || !opt.isPresent()) return "<no-curios>";
        var handlerOpt = opt.get().getStacksHandler("chip");
        if (handlerOpt == null || !handlerOpt.isPresent()) return "<no-chip-slot>";
        var stacks = handlerOpt.get().getStacks();
        if (stacks.getSlots() <= 0) return "<empty-slot>";
        return itemIdOf(stacks.getStackInSlot(0));
    } catch (e) { return "<err:" + exText(e) + ">"; }
}

function airbagClasses() {
    return {
        Airbag: Java.loadClass("com.merlinkitsune.astral_dice.item.chip.AirbagChipItem"),
        Charge: Java.loadClass("com.merlinkitsune.astral_dice.item.ChargeManager")
    };
}

/** 气囊全量读数(单行,正则友好) */
function airbagState(p) {
    var cls = airbagClasses();
    var equipped = -1, charge = -1, cdEnd = -1, cdLeft = -1;
    try { equipped = cls.Airbag.isEquipped(p) ? 1 : 0; } catch (e0) { equipped = -1; }
    try { charge = cls.Charge.getStacks(p); } catch (e1) { charge = -1; }
    try { cdEnd = ModAttachments.getAirbagCooldownEnd(p); } catch (e2) { cdEnd = -1; }
    try { cdLeft = cdEnd - nowTick(p); } catch (e3) { cdLeft = -2; }
    return "health=" + rghp(p) + ":alive=" + (p.isAlive() ? 1 : 0)
        + ":equipped=" + equipped + ":chip=" + chipSlotItemId(p)
        + ":charge=" + charge + ":cd_end=" + cdEnd + ":cd_left=" + cdLeft
        + ":gametime=" + nowTick(p);
}

/** 与 /kill 同路径的致死施加器:返回实际用到的路径名 */
function airbagApplyKill(entity) {
    try { entity.kill(); return "kill"; } catch (e0) { /* 退化到显式伤害源 */ }
    try {
        var ResourceKey = Java.loadClass("net.minecraft.resources.ResourceKey");
        var Registries = Java.loadClass("net.minecraft.core.registries.Registries");
        var src = entity.level.damageSources().source(ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.parse("minecraft:generic_kill")));
        entity.hurt(src, 1000.0);
        return "hurt:generic_kill";
    } catch (e1) { return "ERR:" + exText(e1); }
}

function doAirbagPrep(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var item = resolveItem("astral_dice:airbag_chip");
    if (item == null) { send(ctx, "AP_" + tag + "_ERR:unknown_item:airbag_chip"); return 0; }
    var mode = "keep";
    try { p.setGameMode(GameTypeClass.SURVIVAL); mode = "forced_survival"; } catch (e0) { mode = "err"; }
    var slotErr = ensureChipSlot(p, CHIP_SLOT_MIN);
    if (slotErr != null) { send(ctx, "AP_" + tag + "_ERR:" + slotErr); return 0; }
    var err = putInSlot(p, "chip", new ItemStack(item), 0);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    var cls = airbagClasses();
    try { cls.Charge.removeAll(p); } catch (e1) { send(ctx, "AP_" + tag + "_ERR:charge_remove:" + exText(e1)); return 0; }
    try { cls.Charge.addStacks(p, 6); } catch (e2) { send(ctx, "AP_" + tag + "_ERR:charge_add:" + exText(e2)); return 0; }
    try { ModAttachments.setAirbagCooldownEnd(p, 0); } catch (e3) { /* 忽略 */ }
    try { p.setHealth(p.getMaxHealth()); } catch (e4) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_PREP:mode=" + mode + ":" + airbagState(p));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doAirbagKill(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var before = rghp(p);
    var path = airbagApplyKill(p);
    send(ctx, "AP_" + tag + "_KILL:path=" + path + ":hp_before=" + before + ":" + airbagState(p));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doAirbagLethal(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var mob = spawnDummy(p, "minecraft:pig", 4);
    if (mob == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed"); return 0; }
    try { mob.setHealth(mob.getMaxHealth()); } catch (e0) { /* 忽略 */ }
    try { mob.invulnerableTime = 0; } catch (e1) { /* 忽略 */ }
    var before = rghp(mob);
    var path = airbagApplyKill(mob);
    send(ctx, "AP_" + tag + "_LETHAL:path=" + path + ":hp_before=" + before
        + ":hp_after=" + rghp(mob) + ":alive=" + (mob.isAlive() ? 1 : 0));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doAirbagRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    // B6 ③:只读相位同时调 dump(只读) —— 出牌锁原始值由 LOCKRAW 组给出。
    // 特性特有的读数(health / equipped / chip / charge / airbag cd)不在 dump 的能力边界内
    // (dump 只输出本模组自身状态,且刻意不输出血量、背包与其它模组的冷却),故保留。
    dumpState(ctx, tag);
    send(ctx, "AP_" + tag + "_STATE:" + airbagState(p));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doAirbagNoCharge(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var cls = airbagClasses();
    try { cls.Charge.removeAll(p); } catch (e0) { send(ctx, "AP_" + tag + "_ERR:charge_remove:" + exText(e0)); return 0; }
    try { ModAttachments.setAirbagCooldownEnd(p, 0); } catch (e1) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_NOCHARGE:" + airbagState(p));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doAirbagReset(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var cls = airbagClasses();
    try { cls.Charge.removeAll(p); } catch (e0) { /* 忽略 */ }
    try { ModAttachments.setAirbagCooldownEnd(p, 0); } catch (e1) { /* 忽略 */ }
    try { p.setHealth(p.getMaxHealth()); } catch (e2) { /* 忽略 */ }
    var mode = "keep";
    try { p.setGameMode(GameTypeClass.CREATIVE); mode = "creative"; } catch (e3) { mode = "err"; }
    send(ctx, "AP_" + tag + "_RESET:mode=" + mode + ":" + airbagState(p));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}
// ════════════════════════════════════════════════════════════════════════════
//  电击手套「轮次归零解除武装」(2026-09-15 B2 / 本批 C1)
//    /astralprobe gloveround <tag>
//
//  产品口径:出牌轮归零的**唯一入口** = EffectCardPeriod.clearRoundBonuses,
//  它现在统一调用 ElectricGloveChipItem.disarmAoe(player)(本批 C1 的改动点);
//  clearRoundBonuses 的调用点恰好三条 = tick 情形 1(周期正常到期) /
//  registerPlay 周期边界 / forceResetRound(忍者宽限强重置)。
//  本命令覆盖**三条全部** + 一条正对照(未归零时武装必须仍在),避免"只有一条路径对了"。
// ════════════════════════════════════════════════════════════════════════════

/** 武装读数:附件真值(disarmAoe 写的那个键) + 生产判定入口 isAoeArmed(= isEquipped && 附件) */
function gloveArmed(p) {
    var att = -1;
    try { att = ModAttachments.isElectricGloveAoe(p) ? 1 : 0; } catch (e0) { att = -1; }
    var armed = -1;
    try { armed = ElectricGloveClass.isAoeArmed(p) ? 1 : 0; } catch (e1) { armed = -1; }
    return "aoe=" + att + ":armed=" + armed;
}

function doGloveRound(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var mode = "already_survival";
    try { p.setGameMode(GameTypeClass.SURVIVAL); mode = "forced_survival"; } catch (e0) { /* 忽略 */ }
    try { p.setHealth(p.getMaxHealth()); } catch (e0b) { /* 忽略 */ }
    var slotErr = ensureChipSlot(p, CHIP_SLOT_MIN);
    if (slotErr != null) { send(ctx, "AP_" + tag + "_ERR:" + slotErr); return 0; }
    var chip = resolveItem("astral_dice:electric_glove_chip");
    if (chip == null) { send(ctx, "AP_" + tag + "_ERR:unknown_chip"); return 0; }
    clearCurioSlots(p, "chip");
    clearCurioSlots(p, "stand");
    var putErr = putInSlot(p, "chip", new ItemStack(chip), 0);
    if (putErr != null) { send(ctx, "AP_" + tag + "_ERR:" + putErr); return 0; }
    resetEffectCardCycle(p);
    ModAttachments.setElectricGloveAoe(p, false);
    send(ctx, "AP_" + tag + "_PREP:mode=" + mode
        + ":equipped=" + (ElectricGloveClass.isEquipped(p) ? 1 : 0)
        + ":" + gloveArmed(p));

    var now = nowTick(p);

    // ── 正对照 A:本轮**未**归零(冷却进行中)tick 必须不动武装 ────────────────
    ModAttachments.setElectricGloveAoe(p, true);
    ModAttachments.setEffectCardPlayCount(p, 1);
    ModAttachments.setEffectCardCooldownEnd(p, now + 200);   // 冷却进行中 ⇒ tick 首行直接返回
    EffectCardPeriodClass.tick(p);
    var aAoe = ModAttachments.isElectricGloveAoe(p);
    var aCd = ModAttachments.getEffectCardCooldownEnd(p);
    send(ctx, "AP_" + tag + "_A_HELD:count=" + ModAttachments.getEffectCardPlayCount(p)
        + ":cd_kept=" + (aCd > now ? 1 : 0) + ":" + gloveArmed(p));
    var aOk = aAoe && (aCd > now);

    // ── 路径 B:tick 情形 1(冷却到期 ⇒ 周期正常到期)归零 ───────────────────────
    ModAttachments.setElectricGloveAoe(p, true);
    ModAttachments.setEffectCardPlayCount(p, 2);
    ModAttachments.setEffectCardCooldownEnd(p, now - 1);
    EffectCardPeriodClass.tick(p);
    var bAoe = ModAttachments.isElectricGloveAoe(p);
    send(ctx, "AP_" + tag + "_B_TICK:count=" + ModAttachments.getEffectCardPlayCount(p)
        + ":cd=" + ModAttachments.getEffectCardCooldownEnd(p) + ":" + gloveArmed(p));
    var bOk = !bAoe;

    // ── 路径 C:registerPlay 周期边界(冷却已归 0 但 tick 尚未清理) ──────────────
    ModAttachments.setElectricGloveAoe(p, true);
    ModAttachments.setEffectCardPlayCount(p, 2);
    ModAttachments.setEffectCardCooldownEnd(p, now - 1);
    EffectCardPeriodClass.registerPlay(p);
    var cAoe = ModAttachments.isElectricGloveAoe(p);
    send(ctx, "AP_" + tag + "_C_REGISTER:count=" + ModAttachments.getEffectCardPlayCount(p)
        + ":cd_end=" + ModAttachments.getEffectCardCooldownEnd(p) + ":" + gloveArmed(p));
    var cOk = !cAoe;

    // ── 路径 D:忍者宽限强重置(forceResetRound) ────────────────────────────────
    // 走**真实入口** BaseSignItem.tickSignActiveLock:忍者锁定态 + 门控硬上界 0(忍者无自身计时器)
    // + 宽限已过期 + 期内未出任何效果牌 ⇒ 强制重置出牌状态并起主动冷却。
    var err = equipSign(p, KOMACHI_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    resetEffectCardCycle(p);
    var nowD = nowTick(p);
    ModAttachments.setElectricGloveAoe(p, true);
    ModAttachments.setEffectCardPlayCount(p, 1);
    ModAttachments.setSignActiveLockSign(p, KOMACHI_SIGN_ID);
    ModAttachments.setSignActiveLockEnd(p, 0);
    ModAttachments.setSignActiveLockGraceEnd(p, (nowD > 1 ? nowD : 1) - 1);
    ModAttachments.setSignActiveLockPlayed(p, false);
    // [SIGNLOCK-BASE-P3] 同上:合成锁定态的脚手架一并落笔补偿基准(见 resetActiveLock 注释)
    ModAttachments.setSignActiveLockLastSeen(p, nowD > 1 ? nowD : 1);
    // 测试脚手架:`endLockAndStartCooldown` 的基准读 `sign_active_max_cooldown`
    // (= "本次冷却实际使用的最大冷却",正常由各立牌释放主动时写定,如忍者 180 秒)。
    // 本相位不调 performSkillForCurio,故显式写一个非 0 基准,让"强重置后**确实起了冷却**"
    // 可判定;这只替换基准的写入者,不绕过被测路径(forceResetRound → clearRoundBonuses → disarmAoe)。
    ModAttachments.setSignActiveMaxCooldown(p, 3600);
    BaseSignItemClass.tickSignActiveLock(p);
    var dAoe = ModAttachments.isElectricGloveAoe(p);
    var dLock = lockSignId(p);
    var dCd = signCooldownRemaining(p);
    send(ctx, "AP_" + tag + "_D_GRACE:" + gloveArmed(p)
        + ":locked=" + (dLock === "" ? 0 : 1) + ":cd=" + (dCd > 0 ? 1 : 0));
    var dOk = (!dAoe) && (dLock === "") && (dCd > 0);

    send(ctx, "AP_" + tag + "_VERDICT:held=" + (aOk ? 1 : 0) + ":tick_reset=" + (bOk ? 1 : 0)
        + ":register_reset=" + (cOk ? 1 : 0) + ":force_reset=" + (dOk ? 1 : 0));
    send(ctx, "AP_" + tag + "_GLOVE_OK:" + ((aOk && bOk && cOk && dOk) ? 1 : 0));

    // 收尾:摘筹码/立牌、清周期与武装、回创造
    clearCurioSlots(p, "chip");
    clearCurioSlots(p, "stand");
    resetEffectCardCycle(p);
    ModAttachments.setElectricGloveAoe(p, false);
    try { p.setHealth(p.getMaxHealth()); } catch (e9) { /* 忽略 */ }
    try { p.setGameMode(GameTypeClass.CREATIVE); } catch (e10) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_RESTORE:creative");
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ════════════════════════════════════════════════════════════════════════════
//  末影骰「保命清 GLOWING」门控(2026-09-15 B2 / 本批 C4)
//    /astralprobe endertotem <tag>
//
//  产品口径(event/EnderDiceHandler#onLivingDeath):保命时移除集合 = HARMFUL ∪
//  {GLOWING 仅当玩家确实带着本模组 MARKED};绝不用 removeAllEffects。
//  两个相位:
//    P1 MARKED + GLOWING + 增益(速度) → 致命伤害 ⇒ MARKED 与 GLOWING 均被移除、增益仍在;
//    P2 只有 GLOWING(无 MARKED)      → 致命伤害 ⇒ GLOWING **被保留**(验证门控)。
//  ⚠️ 致死伤害必须**不绕过无敌**:`/kill` = minecraft:generic_kill 属
//  BYPASSES_INVULNERABILITY,产品(EnderDiceHandler#onLivingDeath:165)与原版不死图腾
//  都会**故意跳过**它 ⇒ `/kill` 打不出末影骰保命(玩家直接死亡),故本相位**不用 /kill**
//  (AIRBAG 用例用 /kill 是另一条口径:气囊改成了「连绕过无敌的伤害也拦」)。
//  本相位照抄 AIRBAG 已实证的**直接 Java 调用**路线(airbagApplyKill 的 hurt 分支),
//  伤害源换成不在该标签内的 `minecraft:generic`。
//  ⚠️ 2026-09-15 B5 更正:上句的「已实证」只对 1.21.1 的 `.source(ResourceKey)` 成立,
//  而 AIRBAG 用例总是先命中 `entity.kill()`,`hurt` 分支从未真正被执行过。
//  现两侧统一改走 `Entity#damageSources().generic()`(两版本都可见的公开工厂),
//  首条路线为 `causeFallDamage(1000, 1.0, fall())`(1.20.1 已实证可打出真实掉血)。
// ════════════════════════════════════════════════════════════════════════════

/** 保命相位前的干净基线(清 marked/glowing/speed、清无敌帧、回满血、冷却归零) */
function enderGlowReset(p) {
    try { ModEffectRemoval.remove(p, fxMarked()); } catch (e0) { /* 忽略 */ }
    try { p.removeEffect(MobEffectsClass.GLOWING); } catch (e1) { /* 忽略 */ }
    try { p.removeEffect(MobEffectsClass.MOVEMENT_SPEED); } catch (e2) { /* 忽略 */ }
    try { ModAttachments.setEnderDieTotemCooldownEnd(p, 0); } catch (e3) { /* 忽略 */ }
    try { p.setHealth(p.getMaxHealth()); } catch (e4) { /* 忽略 */ }
    resetHurtFeedback(p);
}

/** 保命读数:三个效果的存在性 + 血量 + 末影骰保命冷却剩余(>0 = 图腾确实触发过) */
function enderGlowState(p) {
    var now = nowTick(p);
    var cdLeft = -1;
    try { cdLeft = ModAttachments.getEnderDieTotemCooldownEnd(p) - now; } catch (e0) { cdLeft = -1; }
    return "marked=" + (findEffect(p, DESC_MARK) != null ? 1 : 0)
        + ":glow=" + (findEffect(p, DESC_GLOW) != null ? 1 : 0)
        + ":speed=" + (findEffect(p, DESC_SPEED) != null ? 1 : 0)
        + ":hp=" + rghp(p) + ":alive=" + (p.isAlive() ? 1 : 0)
        + ":invul=" + (function(){ try { return p.getAbilities().invulnerable ? 1 : 0; } catch (e) { return -1; } })() + ":totem_cd_left=" + cdLeft;
}

/** 保命是否真的触发(冷却被写入正数剩余)= 1/0/-1(读不到) */
function enderTotemFired(p) {
    try {
        var left = ModAttachments.getEnderDieTotemCooldownEnd(p) - nowTick(p);
        return left > 0 ? 1 : 0;
    } catch (e) { return -1; }
}

/** 致死相位实际用的伤害源是否属 BYPASSES_INVULNERABILITY(必须为 0 才可能触发保命) */
var lastLethalBypass = -2;
function srcBypassesInvuln(src) {
    try {
        return src.is(DamageTypeTagsClass.BYPASSES_INVULNERABILITY) ? 1 : 0;
    } catch (e) { return -1; }
}

/**
 * 致死相位的伤害源 —— **两版本都可见**的公开入口。
 *
 * 2026-09-15 B5(与 1.20.1 侧同步)。旧写法
 *     `p.level.damageSources().source(ResourceKey.create(Registries.DAMAGE_TYPE, …))`
 * 在本版本**恰好可用**,但在 1.20.1 **根本不存在**(1.20.1 的 `DamageSources#source(...)`
 * 三个重载全是 private,只供本类内部具名工厂调用;公开面只有 `generic()`/`fall()`/`magic()` …)。
 * 两侧探针必须同写法,否则 1.20.1 会在下一次同步时被重新引入该缺陷
 * (1.20.1 实跑读数:`src_ex:TypeError: Cannot find function source in object DamageSources@…`)。
 *
 * 两版本共同可见的等价入口 = `Entity#damageSources()`(**Entity 上 public**,两版本同签名)
 * → `DamageSources#generic()`(两版本均 public,返回 `minecraft:generic` 的 DamageSource)。
 * 已按两版本反编译源码逐条核对(`forge-1.20.1-47.4.10-sources.jar` /
 * `neoforge-21.1.235-sources.jar` 的 `net/minecraft/world/damagesource/DamageSources.java`),
 * **不是照搬**。
 *
 * `minecraft:generic` 不在 `bypasses_invulnerability` 内(该标签两版本都只含
 * `minecraft:out_of_world` 与 `minecraft:generic_kill`),故产品
 * `EnderDiceHandler#onLivingDeath:165` 的早退分支不会被走到 —— `/kill`
 * (=`minecraft:generic_kill`)则会,这正是本相位**不能用 `/kill`** 的原因。
 */
function enderLethalSource(p) {
    return p.damageSources().generic();
}

/**
 * 施加一次**不绕过无敌**的致死伤害。
 *
 * 2026-09-15 B3 重写。分步命令流(prep → hit → read)的原因:`/damage` 命令经
 * `performPrefixedCommand` 会被**推迟到本 tick 末**执行,同一次调用里读不到结果,
 * 故注入与读数必须拆成两条命令(相隔数秒),不能像旧版那样"一条命令内同步判定"。
 *
 * 路线(逐条尝试,任一条让保命触发即停,不再施加第二次致死 —— 否则冷却中的玩家会真死):
 *   ① `causeFallDamage(1000, 1.0, fall())`  —— 原版内部会调 `hurt(fall, 伤害)`,
 *      与 applyFallDamage 已实证可用的同一条调用;
 *   ② `p.damage(1000.0, src)`  → KubeJS `kjs$damage(float, DamageSource)` → `Entity.hurt(src, 1000)`;
 *   ③ 显式全名 `p["kjs$damage"](1000.0, src)`(若 KubeJS 未去前缀,②会落到别处);
 *   ④ `p.attack(src, 1000.0)` → `kjs$attack(DamageSource, float)` → 同一条 hurt;
 *   ⑤ 显式全名 `p["kjs$attack"](src, 1000.0)`;
 *   ⑥ 兜底:原版命令 `/damage @a 1000 minecraft:generic`(推迟到 tick 末 ⇒ 由下一步 `read` 判定)。
 * **绝不用 `p.hurt`** —— Rhino 把 `hurt` 解析成 `Player#isHurt()` 的 bean 属性
 * (`TypeError: … it is not a function, it is "boolean"`,本批实测);也**不用 `/kill`**
 * (`generic_kill` 属 BYPASSES_INVULNERABILITY,产品会故意跳过)。
 * 每一步都落成 `名字=返回值:hp前>hp后:f=保命是否触发` 的诊断串。
 *
 * 2026-09-15 B5(与 1.20.1 侧同步):**伤害源构造失败不再提前 return** ——
 * `src` 拿不到时只把原因写进诊断串并跳过 ②~⑤,① (`fall`,自带伤害源)与 ⑥
 * (原版 `/damage` 兜底)**照常执行**;`bypass` 只对「真正让保命触发的那条路线」的伤害源求值,
 * 不再固定读 `src`。两条硬化对 1.21.1 是纯防御(该版本 `source(ResourceKey)` 本就可见),
 * 但两版本探针必须保持同一份实现。
 */
var enderLastApi = { "1": "n/a", "2": "n/a" };
function enderLethalHit(p, phase) {
    var diag = [];
    if (p.getAbilities().invulnerable) return "not_damageable";
    // 伤害源:构造失败**不早退**(阻塞 A 的第二处缺陷),只记原因后继续走不依赖它的兜底路线
    var src = null;
    try { src = enderLethalSource(p); }
    catch (e0) { diag.push("src_ex=" + exText(e0)); }
    var srcFall = null;
    try { srcFall = p.level.damageSources().fall(); }
    catch (e1) { srcFall = null; }
    lastLethalBypass = -2;

    var done = "none";
    function step(name, fn, ds) {
        if (done !== "none" || !p.isAlive()) return;
        var before = rghp(p);
        var rv = "n/a";
        try { rv = String(fn()); } catch (e) { rv = "EX:" + exText(e); }
        var f = enderTotemFired(p);
        diag.push(name + "=" + rv + ":hp" + before + ">" + rghp(p) + ":f=" + f);
        if (f === 1) {
            done = name;
            if (ds != null) lastLethalBypass = srcBypassesInvuln(ds);
        }
    }
    step("fall", function () {
        var fs = srcFall;
        if (fs == null) fs = p.level.damageSources().fall();
        return p.causeFallDamage(1000.0, 1.0, fs);
    }, srcFall);
    if (src != null) {
        step("damage", function () { return p.damage(1000.0, src); }, src);
        step("kjs_damage", function () { return p["kjs$damage"](1000.0, src); }, src);
        step("attack", function () { return p.attack(src, 1000.0); }, src);
        step("kjs_attack", function () { return p["kjs$attack"](src, 1000.0); }, src);
    } else {
        diag.push("src_unavailable:damage/attack_routes_skipped");
    }
    if (done === "none" && p.isAlive()) {
        // 兜底:原版命令入口(与真人/命令块同一入口)。推迟到本 tick 末 ⇒ 由 read 判定。
        diag.push("cmd=" + runCmdP(p, "damage @a 1000 minecraft:generic"));
        done = "cmd";
        if (src != null) lastLethalBypass = srcBypassesInvuln(src);
    }
    enderLastApi[phase] = done;
    return diag.join("|");
}

/** prep<phase>:造相位初态(生存 + 末影骰 + 清其它饰品槽 + 干净基线 + 相位效果)并读 BEFORE */
function enderPrep(ctx, tag, p, phase) {
    var mode = "already_survival";
    try { p.setGameMode(GameTypeClass.SURVIVAL); mode = "forced_survival"; } catch (e0) { /* 忽略 */ }
    var diceItem = resolveItem("astral_dice:ender_dice");
    if (diceItem == null) { send(ctx, "AP_" + tag + "_ERR:unknown_item:ender_dice"); return 0; }
    var diceErr = putInSlot(p, "dice", new ItemStack(diceItem), 0);
    if (diceErr != null) { send(ctx, "AP_" + tag + "_ERR:" + diceErr); return 0; }
    var equipped = -1;
    try { equipped = EnderDiceHandlerClass.hasEnderDie(p) ? 1 : 0; } catch (e1) { equipped = -1; }
    if (equipped !== 1) {
        send(ctx, "AP_" + tag + "_ERR:no_ender_dice:equipped=" + equipped);
        try { p.setGameMode(GameTypeClass.CREATIVE); } catch (e2) { /* 忽略 */ }
        return 0;
    }
    // 清其它饰品槽:避免上一条用例残留的充能类筹码(安全气囊等)把致死伤害吃掉。
    clearCurioSlots(p, "chip");
    clearCurioSlots(p, "stand");
    enderGlowReset(p);
    if (phase === "1") {
        try { p.addEffect(new MobEffectInstanceClass(fxMarked(), 2400, 0)); }
        catch (e3) { send(ctx, "AP_" + tag + "_ERR:add_marked:" + exText(e3)); return 0; }
    }
    try { p.addEffect(new MobEffectInstanceClass(MobEffectsClass.GLOWING, 2400, 0)); }
    catch (e4) { send(ctx, "AP_" + tag + "_ERR:add_glow:" + exText(e4)); return 0; }
    try { p.addEffect(new MobEffectInstanceClass(MobEffectsClass.MOVEMENT_SPEED, 2400, 0)); }
    catch (e5) { send(ctx, "AP_" + tag + "_ERR:add_speed:" + exText(e5)); return 0; }
    lastLethalBypass = -2;
    enderLastApi[phase] = "n/a";
    send(ctx, "AP_" + tag + "_P" + phase + "_BEFORE:"
        + (phase === "1" ? ("mode=" + mode + ":") : "") + enderGlowState(p));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** read<phase>:读 AFTER + 判定(保命必须真触发:fired=1) */
var enderP1Ok = -1;
function enderRead(ctx, tag, p, phase) {
    var f = enderTotemFired(p);
    send(ctx, "AP_" + tag + "_P" + phase + "_AFTER:" + enderGlowState(p)
        + ":bypass=" + lastLethalBypass + ":fired=" + f + ":api=" + enderLastApi[phase]);
    var ok;
    if (phase === "1") {
        ok = (findEffect(p, DESC_MARK) == null) && (findEffect(p, DESC_GLOW) == null)
            && (findEffect(p, DESC_SPEED) != null) && p.isAlive() && (f === 1);
        enderP1Ok = ok ? 1 : 0;
    } else {
        ok = (findEffect(p, DESC_MARK) == null) && (findEffect(p, DESC_GLOW) != null)
            && (findEffect(p, DESC_SPEED) != null) && p.isAlive() && (f === 1);
    }
    send(ctx, "AP_" + tag + "_P" + phase + "_OK:" + (ok ? 1 : 0));
    if (!p.isAlive()) {
        send(ctx, "AP_" + tag + "_ERR:player_died_p" + phase + ":bypass=" + lastLethalBypass
            + ":api=" + enderLastApi[phase]);
        return 0;
    }
    if (phase === "2") {
        send(ctx, "AP_" + tag + "_VERDICT:gated_clear=" + (enderP1Ok === 1 ? 1 : 0)
            + ":control_keep=" + (ok ? 1 : 0)
            + ":marked_cleared=" + (findEffect(p, DESC_MARK) == null ? 1 : 0));
    }
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** done:收尾(清效果与冷却、摘骰子、回满血、回创造) */
function enderDone(ctx, tag, p) {
    enderGlowReset(p);
    try { p.removeEffect(MobEffectsClass.REGENERATION); } catch (e8) { /* 忽略 */ }
    try { p.removeEffect(MobEffectsClass.ABSORPTION); } catch (e9) { /* 忽略 */ }
    try { p.removeEffect(MobEffectsClass.FIRE_RESISTANCE); } catch (e10) { /* 忽略 */ }
    clearCurioSlots(p, "dice");
    try { p.setHealth(p.getMaxHealth()); } catch (e11) { /* 忽略 */ }
    try { p.setGameMode(GameTypeClass.CREATIVE); } catch (e12) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_RESTORE:creative:" + enderGlowState(p));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doEnderTotem(ctx, tag, sub) {
    var p = ctx.source.getPlayerOrException();
    if (sub === "done") return enderDone(ctx, tag, p);
    // 不用 `sub.charAt(sub.length - 1)`:Rhino 下 `length` 被解析成属性(非函数)⇒ 得到 NaN 而抛
    // `InternalError: Cannot convert NaN to int`(本批实测)。改为按全名比较,零字符串运算。
    var phase = (sub === "prep1" || sub === "hit1" || sub === "read1") ? "1" : "2";
    if (sub === "prep1" || sub === "prep2") return enderPrep(ctx, tag, p, phase);
    if (sub === "hit1" || sub === "hit2") {
        var routes = enderLethalHit(p, phase);
        send(ctx, "AP_" + tag + "_HIT" + phase + ":" + routes);
        send(ctx, "AP_" + tag + "_DONE");
        return 1;
    }
    if (sub === "read1" || sub === "read2") return enderRead(ctx, tag, p, phase);
    send(ctx, "AP_" + tag + "_ERR:unknown_sub:" + sub);
    return 0;
}

// ════════════════════════════════════════════════════════════════════════════
//  《恋的规则书》「仅首次进入世界发放一次」守卫读数(2026-09-15 追加)
//    /astralprobe guidebook <tag>
//
//  被测口径(用户裁定):手册**仅在玩家第一次进入世界时发放一次**,此后任何情况下都不再自动发放。
//  R1 独立代码验证发现并已修补的缺陷:守卫附件 `guide_book_given` 原先**不随死亡复制**
//  ⇒ 玩家死亡后新实体回默认 false,而发放挂在 PlayerLoggedInEvent(登录时)
//  ⇒ **死亡后重登会再发一本**。修补 = 让守卫随死亡保留
//  (1.21.1 侧 = `ModAttachments.GUIDE_BOOK_GIVEN` 加 `.copyOnDeath()`)。
//
//  读数(一行,落 chat 与 latest.log,与其它命令一致):
//    AP_<tag>_GUIDE:given=<0|1>:count=<n>
//      given = ModAttachments.isGuideBookGiven(p) 的读数(布尔直接转 0/1);
//      count = 玩家背包内《恋的规则书》的总数量(统计范围见 guideCountItem)。
//  用例 GUIDE-BOOK-FIRST-JOIN-ONLY-{1.21.1,1.20.1} 的判据:首登与「死亡+重登」两次运行
//  **都必须**读到 `given=1:count=1`;修补前「死亡后重生」相位读 given=0(重登后 count 变 2)。
// ════════════════════════════════════════════════════════════════════════════

/** 手册的**书籍 id**(Patchouli 书,定义在 data/astral_dice/patchouli_books/astral_guide) */
var GUIDE_BOOK_ID = "astral_dice:astral_guide";
/**
 * 手册的**物品注册 id**。⚠️ 不是 `astral_dice:astral_guide`:
 * 《恋的规则书》经 `ItemModBook.forBook(书籍 id)` 生成 = Patchouli 的 `patchouli:guide_book`
 * 物品 + 书籍 id 存在**数据组件**(1.20.1 为 NBT)(AGENTS.md「物品一览」亦记其为
 * 「帕秋莉书籍 id(**非 `ModItems` 注册物品**)」)。若拿 `astral_dice:astral_guide` 去查
 * `BuiltInRegistries.ITEM`,得到的是默认值 `minecraft:air` ⇒ `resolveItem` 返回 null
 * (TESTING-SPEC §10 第 19 条那个坑的另一面:**书籍 id ≠ 物品 id**)。
 */
var GUIDE_BOOK_ITEM_ID = "patchouli:guide_book";

/** Patchouli 书籍物品类(防御式加载:缺失时返回 null,由命令显式报 ERR,绝不静默降级) */
function loadItemModBook() {
    try { return Java.loadClass("vazkii.patchouli.common.item.ItemModBook"); }
    catch (e) { return null; }
}

/** 该栈是不是目标手册:物品 id 粗筛(字符串比较,避开 Rhino 里 Java 对象 `===` 不可靠)+ 书籍 id 精确比对 */
function guideIsTargetBook(stack, itemModBook) {
    if (stack == null || stack.isEmpty()) return false;
    if (itemIdOf(stack) !== GUIDE_BOOK_ITEM_ID) return false;
    var book = itemModBook.getBook(stack);   // 书籍未注册 / 组件缺失时返回 null
    if (book == null) return false;
    return ("" + book.id) === GUIDE_BOOK_ID; // Book.id 是 public final ResourceLocation
}

/**
 * 玩家背包内《恋的规则书》的总数量。
 * 范围 = **主物品栏 + 快捷栏**(0..8 快捷栏、9..35 主栏,共 36 格,与 `anvilCountItem` 同一遍历口径)
 *        + **副手**(`LivingEntity#getOffhandItem`);**不含**护甲槽与 Curios 饰品槽。
 * 为什么不直接复用 `anvilCountItem`:它按**物品注册 id** 求和,而手册的物品 id 是 Patchouli 的
 * `patchouli:guide_book`(`astral_dice:astral_guide` 只是书籍 id)⇒ 按 id 求和**恒为 0**;
 * 且它不覆盖副手。故沿用其风格另写本 helper(产品的发放路径 `player.getInventory().add(book)`
 * 只会落进主物品栏/快捷栏,副手是防御性纳入)。
 */
function guideCountItem(p, itemModBook) {
    var inv = p.getInventory();
    var n = 0;
    for (var i = 0; i < inv.getContainerSize(); i++) {
        var s = inv.getItem(i);
        if (guideIsTargetBook(s, itemModBook)) n += s.getCount();
    }
    var off = p.getOffhandItem();
    if (guideIsTargetBook(off, itemModBook)) n += off.getCount();
    return n;
}

/** 只读:首登发放守卫附件 + 背包内手册总本数(用例 GUIDE-BOOK-FIRST-JOIN-ONLY-*) */
function doGuidebook(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var item = resolveItem(GUIDE_BOOK_ITEM_ID);
    if (item == null) { send(ctx, "AP_" + tag + "_ERR:unknown_item:" + GUIDE_BOOK_ITEM_ID); return 0; }
    var itemModBook = loadItemModBook();
    if (itemModBook == null) { send(ctx, "AP_" + tag + "_ERR:patchouli_missing"); return 0; }
    var given = 0;
    try { given = ModAttachments.isGuideBookGiven(p) ? 1 : 0; }
    catch (e0) { send(ctx, "AP_" + tag + "_ERR:is_given:" + exText(e0)); return 0; }
    var count = 0;
    try { count = guideCountItem(p, itemModBook); }
    catch (e1) { send(ctx, "AP_" + tag + "_ERR:count:" + exText(e1)); return 0; }
    send(ctx, "AP_" + tag + "_GUIDE:given=" + given + ":count=" + count);
    send(ctx, "AP_" + tag + "_GUIDE_DONE");
    return 1;
}

// ════════════════════════════════════════════════════════════════════════════
//  电击手套 3 格 AOE 波及伤害的「基准口径」取证(KI-5:两版本 AOE 基准必须一致)
//    /astralprobe glovebase <tag>
//
//  被测唯一来源(读生产代码定死,不许另算):SpellDamageRegistry 里电击手套修饰器的
//  onHit —— `float total = ctx.event.<事件伤害读取>;` 随后对主目标 3 格内的敌对目标
//  `e.hurt(ModDamageTypes.trueDamage(...), total)`(真伤、不吃护甲)。
//  ⇒ AOE 基准 = **生产代码从 ctx.event 读出的那个值**。两版本差异只在
//    DamageEffectCardHandler 把 ctx.event 构造成哪个事件:
//      · 1.21.1 = LivingDamageEvent.Pre(#getNewDamage())  → 护甲/附魔减免**之后**
//      · 1.20.1 = 修补前 LivingHurtEvent(#getAmount())    → 护甲**之前**(缺陷)
//                 修补后 LivingDamageEvent(#getAmount())  → 护甲**之后**
//
//  取证方式:探针用 KubeJS 的接口实现(`new Iface({...})` —— 与本仓 komachicap 已实证的
//  `EffectCardPeriod$ExtraPlaySource`(`AP_K3_SRC:ok:2:form:kubejs`)同一机制)**自己实现一个
//  SpellDamageModifier**,并 `SpellDamageRegistry.registerModifier(...)` 注册进**生产同一张
//  修饰器表**;在它的 onHit 里用**与生产逐字相同的那一条读取表达式**读 ctx.event 的伤害值。
//  ⇒ 读数 `base=` 在任何事件挂载状态下都等于「生产当时真正用掉的那个基准」:既不另算,
//    也不会因为版本差异读错事件。生产修饰器注册在前 ⇒ 它的 onHit 先跑(先结算 AOE 再解除武装),
//    探针的 onHit 随后读**同一个事件对象的同一个字段**,两者必然相等。
//  读不到(接口实现失败 / 修饰器未被调用 / 读取抛错)一律落 `AP_<tag>_ERR:`,**绝不静默降级**。
//
//  构造(全在同一条命令内完成并自证;护甲**直写属性实例**而非 `attribute` 命令,
//  以规避 TESTING-SPEC §10 实测的「属性命令生效时机晚于同一 tick 内后续代码」):
//    · ref  = 无甲敌对靶(距 main ≥6 格)⇒ 同额伤害的掉血 = 「护甲前」客观参照 `raw`;
//    · main = 护甲 20 / 韧性 8 的敌对靶 = 主目标 ⇒ `self` = 其实掉血(护甲后客观值);
//    · nbr  = main 3 格内的敌对邻居 ⇒ `nbr` = AOE 实际造成的掉血(AOE 波及的客观结果);
//    · 玩家强制生存 + weather clear + 清空 5 张伤害效果牌效果 + 忍者伤害增益归零
//      ⇒ 本次事件 bonus=0(不产生第二发真伤),main 只吃这一发箭伤,`self` 无歧义;
//    · 武装 = 装电击手套 + 直接置位武装附件(与 ELECTRIC-GLOVE-ROUND-RESET 同一脚手架),
//      读数 `armed=` 取**生产判定入口** ElectricGloveChipItem.isAoeArmed。
//  读数一行:`AP_<tag>_GA:base=…:raw=…:self=…:nbr=…:armor=…:rarmor=…:mode=…:weather=…:armed=…:bonus=…:bsrc=…`
//    base 生产基准 / raw 护甲前客观参照 / self 主目标实掉血 / nbr 邻居实掉血 /
//    armor 主目标护甲(构造自证) / rarmor 参照靶护甲 / bsrc=modifier 表示 base 来自
//    上面那条生产同路修饰器(否则落 _ERR)。
// ════════════════════════════════════════════════════════════════════════════
var gloveBaseState = { armed: false, value: -1, calls: 0, err: null };
var gloveBaseModifierForm = "not_tried";

/** 与生产 SpellDamageRegistry 电击手套修饰器**逐字相同**的读取表达式(1.21.1 侧) */
function gloveBaseReadFromEvent(ctx) {
    return ctx.event.getNewDamage();
}

/** 把探针修饰器注册进生产修饰器表(脚本加载时注册一次;isActive 常态关闭) */
function installGloveBaseModifier() {
    try {
        var Iface = Java.loadClass("com.merlinkitsune.astral_dice.combat.SpellDamageModifier");
        var impl = new Iface({
            isActive: function (ctx) { return gloveBaseState.armed === true; },
            apply: function (ctx, bonus) { return bonus; },
            onHit: function (ctx, bonus) {
                gloveBaseState.calls = gloveBaseState.calls + 1;
                // 只认**第一条**命中的事件(= 本相位那一发);后续嵌套事件不覆盖读数
                if (gloveBaseState.calls > 1) return;
                try { gloveBaseState.value = gloveBaseReadFromEvent(ctx); }
                catch (e) { gloveBaseState.err = exText(e); }
            }
        });
        SpellDamageRegistryClass.registerModifier(impl);
        gloveBaseModifierForm = "modifier";
    } catch (e) {
        gloveBaseModifierForm = "unavailable:" + exText(e);
    }
}
installGloveBaseModifier();

/** 直写属性实例(不依赖 attribute 命令的下一 tick 生效);返回 null 表示成功 */
function gloveSetArmor(ent, armor, tough) {
    try {
        var Attrs = Java.loadClass("net.minecraft.world.entity.ai.attributes.Attributes");
        var a = ent.getAttribute(Attrs.ARMOR);
        var t = ent.getAttribute(Attrs.ARMOR_TOUGHNESS);
        if (a == null || t == null) return "no_attr_instance";
        a.setBaseValue(armor);
        t.setBaseValue(tough);
        return null;
    } catch (e) { return exText(e); }
}

/** 读回某靶**真实生效**的护甲值(构造自证:证明"真的挂了甲") */
function gloveArmorRead(ent, key) {
    try {
        var Attrs = Java.loadClass("net.minecraft.world.entity.ai.attributes.Attributes");
        return key + "=" + ent.getAttributeValue(Attrs.ARMOR);
    } catch (e) { return key + "=ERR"; }
}

function gloveR2(x) { return Math.round(x * 100) / 100; }

function doGloveBase(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var mode = "already_survival";
    try { p.setGameMode(GameTypeClass.SURVIVAL); mode = "forced_survival"; } catch (e0) { /* 忽略 */ }
    var weather = "skip";
    try { p.level.setWeatherParameters(6000, 0, false, false); weather = "clear"; } catch (e1) { weather = "err"; }
    try { p.setHealth(p.getMaxHealth()); } catch (e2) { /* 忽略 */ }

    // 外部加成归零:清 5 张伤害效果牌效果 + 忍者「效果牌伤害增益」+ 三个 Curios 槽
    var holders = [ModEffects.LIVING_PAGE, ModEffects.MONSTER_LASER, ModEffects.MONSTER_BRICK,
                   ModEffects.ORBITAL_STRIKE, ModEffects.DIRECTIONAL_BLAST];
    for (var i = 0; i < holders.length; i++) {
        try { ModEffectRemoval.remove(p, holders[i]); } catch (e3) { /* 忽略 */ }
    }
    try { ModAttachments.setKomachiDamageBonus(p, 0); } catch (e4) { /* 忽略 */ }
    try { clearCurioSlots(p, "dice"); } catch (e5) { /* 忽略 */ }
    try { clearCurioSlots(p, "chip"); } catch (e6) { /* 忽略 */ }
    try { clearCurioSlots(p, "stand"); } catch (e7) { /* 忽略 */ }
    try { resetEffectCardCycle(p); } catch (e8) { /* 忽略 */ }

    // 装电击手套(全限定 id;短 id 会被当成 minecraft: 前缀 → resolveItem 返 null)
    var slotErr = ensureChipSlot(p, CHIP_SLOT_MIN);
    if (slotErr != null) { send(ctx, "AP_" + tag + "_ERR:" + slotErr); return 0; }
    var chip = resolveItem("astral_dice:electric_glove_chip");
    if (chip == null) { send(ctx, "AP_" + tag + "_ERR:unknown_chip"); return 0; }
    var putErr = putInSlot(p, "chip", new ItemStack(chip), 0);
    if (putErr != null) { send(ctx, "AP_" + tag + "_ERR:" + putErr); return 0; }
    ModAttachments.setElectricGloveAoe(p, false);

    // 三只非亡灵敌对靶(spider 本身无甲,护甲由探针直写 ⇒ raw 参照干净)
    var ref = spawnDummy(p, "minecraft:spider", 2);
    var main = spawnDummy(p, "minecraft:spider", 3);
    var nbr = spawnDummy(p, "minecraft:spider", 2);
    if (ref == null || main == null || nbr == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed"); return 0; }
    try { placeAt(ref, p.getX() - 6.0, p.getY(), p.getZ()); } catch (e9) { /* 忽略 */ }
    try { placeAt(main, p.getX(), p.getY(), p.getZ() + 3.0); } catch (e10) { /* 忽略 */ }
    try { placeAt(nbr, p.getX() + 2.0, p.getY(), p.getZ() + 3.0); } catch (e11) { /* 忽略 */ }
    var all = [ref, main, nbr];
    for (var k = 0; k < all.length; k++) {
        try { all[k].setNoAi(true); } catch (e12) { /* 忽略 */ }
        try { all[k].setPersistenceRequired(); } catch (e13) { /* 忽略 */ }
        try { all[k].setHealth(all[k].getMaxHealth()); } catch (e14) { /* 忽略 */ }
    }
    var setMain = gloveSetArmor(main, 20.0, 8.0);
    var setRef = gloveSetArmor(ref, 0.0, 0.0);
    if (setMain != null || setRef != null) {
        send(ctx, "AP_" + tag + "_ERR:armor_set:" + setMain + "/" + setRef);
    }

    var src = spellSource(p);
    if (src == null) { send(ctx, "AP_" + tag + "_ERR:no_arrow_carrier"); return 0; }

    // 相位 1:无甲参照靶吃同一发伤害(= 「护甲前」客观参照;此时武装已清 ⇒ 不会触发 AOE)
    var refHit = applySpellDamage(ref, src, 8.0);

    // 相位 2:武装后主目标吃同一发伤害 ⇒ 生产在这一刻读出基准并对 nbr 结算 AOE
    ModAttachments.setElectricGloveAoe(p, true);
    var armed = -1;
    try { armed = ElectricGloveClass.isAoeArmed(p) ? 1 : 0; } catch (e20) { armed = -1; }
    var bonus = -1;
    try { bonus = SpellDamageRegistryClass.effectCardDamageBonus(p); } catch (e21) { bonus = -1; }
    var armorMain = gloveArmorRead(main, "armor");
    var armorRef = gloveArmorRead(ref, "rarmor");
    gloveBaseState.value = -1;
    gloveBaseState.calls = 0;
    gloveBaseState.err = null;
    var nbrBefore = -1;
    try { nbrBefore = nbr.getHealth(); } catch (e22) { nbrBefore = -1; }
    var mainHit = null;
    gloveBaseState.armed = true;
    try {
        mainHit = applySpellDamage(main, src, 8.0);
    } finally {
        gloveBaseState.armed = false;
    }
    var nbrAfter = -1;
    try { nbrAfter = nbr.getHealth(); } catch (e23) { nbrAfter = -1; }
    if (mainHit == null) mainHit = { dealt: -1, api: "none" };

    var raw = refHit.dealt;
    var self = mainHit.dealt;
    var nbrDealt = (nbrBefore < 0 || nbrAfter < 0) ? -1 : gloveR2(nbrBefore - nbrAfter);
    var base = (gloveBaseState.calls > 0 && gloveBaseState.err == null && gloveBaseState.value >= 0)
        ? gloveR2(gloveBaseState.value) : -1;
    var bsrc = gloveBaseModifierForm;
    if (bsrc === "modifier" && gloveBaseState.calls <= 0) bsrc = "no_read";
    if (bsrc === "modifier" && gloveBaseState.err != null) bsrc = "read_err";
    if (bsrc !== "modifier") {
        send(ctx, "AP_" + tag + "_ERR:base_source:" + bsrc + ":calls=" + gloveBaseState.calls);
    }

    send(ctx, "AP_" + tag + "_GA:base=" + base + ":raw=" + raw + ":self=" + self
        + ":nbr=" + nbrDealt + ":" + armorMain + ":" + armorRef
        + ":mode=" + mode + ":weather=" + weather
        + ":armed=" + armed + ":bonus=" + bonus + ":bsrc=" + bsrc);

    // 收尾:撤靶 / 撤武装 / 摘筹码 / 回创造
    for (var q = 0; q < all.length; q++) { try { all[q].discard(); } catch (e30) { /* 忽略 */ } }
    try { ModAttachments.setElectricGloveAoe(p, false); } catch (e31) { /* 忽略 */ }
    try { clearCurioSlots(p, "chip"); } catch (e32) { /* 忽略 */ }
    try { p.setGameMode(GameTypeClass.CREATIVE); } catch (e33) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_GA_DONE");
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════════
// === 游戏大师立牌(ren)「鼠鼠护盾」:REN-SHIELD-1.21.1 ===
// 被测语义:①被动「鼠鼠救我」= 佩戴 ren 且超过 5 分钟没有护盾 ⇒ 自动补一个(仅佩戴者本人);
// ②护盾 = 10 黄心(20 点吸收)+ 抗性提升 + 1 层一次性反击;③黄心被打空 ⇒ ≤1 tick 内清空;
// ④带盾被攻击 ⇒ 消耗 1 层并对攻击者注入一次现有反击伤害;⑤主动「熊孩子特权」= 选择器选
//   任意玩家或自身 ⇒ 目标 +1 张随机卡牌 + 鼠鼠护盾,且**确认前不进冷却**(前置门控)。
// 计时口径:rentimer 把「最后一次持有护盾」的时刻**回拨 6000 tick**,等价于"已过去 5 分钟",
// 免去真实等待(计时基准 = 玩家级附件 + level.getGameTime,回拨即同一语义)。
// ══════════════════════════════════════════════════════════════════════════════
var REN_SIGN_ID = "astral_dice:ren_sign";
var REN_PASSIVE_INTERVAL = 6000;
var RenSignItemClass = Java.loadClass("com.merlinkitsune.astral_dice.item.sign.RenSignItem");
var RenShieldManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.item.RenShieldManager");
var RenModItemsClass = Java.loadClass("com.merlinkitsune.astral_dice.item.ModItems");

function renEffectHolder() {
    return ModEffects.REN_SHIELD;
}

/** 「反击」层数图标(ren_counter):层数附件的可见镜像,用于断言图标与层数严格同步 */
function renCounterHolder() {
    return ModEffects.REN_COUNTER;
}

function round2(v) {
    return Math.round(v * 100) / 100;
}

/**
 * 施加一次伤害(本地自足版:`sourceOf`/`applyDamage` 都是 doTrueDmg 内部的嵌套函数,不可跨命令复用)。
 * 1.21.1 实测 `LivingEntity#attack(DamageSource,float)` 在 Rhino 下可用,`damage(amount, source)` 为备选。
 */
function renApplyDamage(ent, src, amount) {
    try { ent.attack(src, amount); return "attack"; } catch (ea) { /* 试下一个 */ }
    try { ent.damage(amount, src); return "damage"; } catch (eb) { /* 试下一个 */ }
    return "none";
}

/** 护盾全量读数:shield/absorb/counter/res/base/last/now/counterfx(反击图标) */
function renState(p) {
    var shield = -1;
    try { shield = p.hasEffect(renEffectHolder()) ? 1 : 0; } catch (e) { shield = -1; }
    var res = -1;
    try {
        var ri = p.getEffect(MobEffectsClass.DAMAGE_RESISTANCE);
        res = (ri == null) ? 0 : (1 + ri.getAmplifier());
    } catch (e) { res = -1; }
    var absorb = -1;
    try { absorb = round2(p.getAbsorptionAmount()); } catch (e) { absorb = -1; }
    var counter = -1, base = -1, last = -1, now = -1, ctrfx = -1;
    try { ctrfx = p.hasEffect(renCounterHolder()) ? 1 : 0; } catch (e) { ctrfx = -1; }
    try { counter = ModAttachments.getRenCounterCharges(p); } catch (e) { /* 忽略 */ }
    try { base = round2(ModAttachments.getRenShieldBaselineAbsorption(p)); } catch (e) { /* 忽略 */ }
    try { last = ModAttachments.getRenShieldLastSeenTick(p); } catch (e) { /* 忽略 */ }
    try { now = nowTick(p); } catch (e) { /* 忽略 */ }
    return "shield=" + shield + ":absorb=" + absorb + ":counter=" + counter
        + ":res=" + res + ":base=" + base + ":last=" + last + ":now=" + now + ":counterfx=" + ctrfx;
}

/** 把玩家拉回「从未持有护盾」的干净基线(护盾效果若因移除拦截残留,会在下一 tick 被黄心轮询自愈) */
function renReset(p) {
    // ⚠️ 护盾/抗性/反击图标都是 astral_dice:* 效果,`removeAllEffects()` 会被模组的移除拦截器**取消**
    //   (实测残留:上一会话的护盾会活到 renprep 之后,读数出现 shield=1、absorb 也是旧的基线 + 10)
    //   ⇒ 必须走产品的唯一清空入口 voidShield 才能回到确定基线。
    try { RenShieldManagerClass.voidShield(p, "probe_reset"); } catch (e) { /* 忽略 */ }
    try { ModAttachments.setRenShieldLastSeenTick(p, 0); } catch (e) { /* 忽略 */ }
    try { ModAttachments.setRenCounterCharges(p, 0); } catch (e) { /* 忽略 */ }
    try { ModAttachments.setRenShieldOwnResistance(p, false); } catch (e) { /* 忽略 */ }
    try { ModAttachments.setRenShieldBaselineAbsorption(p, 0.0); } catch (e) { /* 忽略 */ }
    try { ModAttachments.setSignActiveCooldownEnd(p, 0); } catch (e) { /* 忽略 */ }
    try { p.setAbsorptionAmount(0.0); } catch (e) { /* 忽略 */ }
    try { p.removeAllEffects(); } catch (e) { /* 忽略 */ }
    try { p.setHealth(p.getMaxHealth()); } catch (e) { /* 忽略 */ }
    try { p.setGameMode(GameTypeClass.SURVIVAL); } catch (e) { /* 忽略 */ }
}

/** 背包内「卡牌」总数(战斗牌 + 效果牌,经生产入口 ModItems#isCardItem) */
function countAllCards(p) {
    var inv = p.getInventory();
    var n = 0;
    for (var i = 0; i < inv.getContainerSize(); i++) {
        var st = inv.getItem(i);
        if (st.isEmpty()) continue;
        try { if (RenModItemsClass.isCardItem(st)) n += st.getCount(); } catch (e) { /* 忽略 */ }
    }
    return n;
}

function doRenPrep(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    clearCurioSlots(p, "chip");
    renReset(p);
    // 清空主背包:被动「鼠鼠救我」现在会发 1 张随机卡牌,读卡数必须从未持有卡牌起算
    runCmd(ctx, "clear @s");
    var err = equipSign(p, REN_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 1; }
    send(ctx, "AP_" + tag + "_PREP:armed=" + (RenSignItemClass.isEquipped(p) ? 1 : 0) + ":" + renState(p));
    return 1;
}

function doRenUnequip(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var slotErr = clearCurioSlots(p, "stand");
    renReset(p);
    send(ctx, "AP_" + tag + "_UNEQUIP:armed=" + (RenSignItemClass.isEquipped(p) ? 1 : 0)
        + (slotErr ? ":slot_err=" + slotErr : "") + ":" + renState(p));
    return 1;
}

/**
 * 把「最后一次持有护盾」的世界时刻回拨 ticks(默认 6000 = 5 分钟),等价于「已过去 ticks tick」。
 * 边界取证用:传 3000(不足 5 分钟)与 6000(恰好 5 分钟)各测一次,可把「间隔常量」证伪/证实。
 * ⚠️ **前提:世界时钟 gameTime 必须大于 ticks**(否则 now - ticks <= 0,会被产品按「从未持有」重置);
 * 用例 `REN-SHIELD-BOUNDARY-*` 用读数里的 `:now=` 把该前提写成硬断言,环境不满足即 FAIL(不假通过)。
 */
function doRenTimer(ctx, tag, ticksText) {
    var p = ctx.source.getPlayerOrException();
    var ticks = REN_PASSIVE_INTERVAL;
    if (ticksText != null && ticksText !== "") {
        var parsed = parseInt(ticksText, 10);
        if (!isNaN(parsed) && parsed > 0) ticks = parsed;
    }
    var now = nowTick(p);
    try {
        ModAttachments.setRenShieldLastSeenTick(p, now - ticks);
    } catch (e) {
        send(ctx, "AP_" + tag + "_ERR:" + exText(e));
        return 1;
    }
    send(ctx, "AP_" + tag + "_TIMER:armed=" + (RenSignItemClass.isEquipped(p) ? 1 : 0)
        + ":rewound=" + ticks + ":" + renState(p));
    return 1;
}

function doRenRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    // cards = 主背包内「卡牌」总数(战斗牌 + 效果牌):被动追加发牌后用它断言「卡牌确实到手」
    send(ctx, "AP_" + tag + "_STATE:" + renState(p) + ":cards=" + countAllCards(p));
    return 1;
}

/** 重复授予(补满、不叠加):总量必须仍为「基线 + 20」且反击层 ≤ 1 */
function doRenGrant(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var before = renState(p);
    try {
        RenShieldManagerClass.grantShield(p);
    } catch (e) {
        send(ctx, "AP_" + tag + "_ERR:" + exText(e));
        return 1;
    }
    send(ctx, "AP_" + tag + "_GRANT:before[" + before + "]:after[" + renState(p) + "]");
    return 1;
}

/** 带盾被敌对生物攻击一次:反击层 1→0,且攻击者掉血(反击伤害 > 0) */
function doRenHit(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    try { p.setGameMode(GameTypeClass.SURVIVAL); } catch (e) { /* 忽略 */ }
    try { p.setHealth(p.getMaxHealth()); } catch (e) { /* 忽略 */ }
    var dummy = spawnDummy(p, "minecraft:zombie", 4.0);
    if (dummy == null) { send(ctx, "AP_" + tag + "_ERR:no_dummy"); return 1; }
    try {
        var hp0 = rghp(dummy);
        var before = renState(p);
        var src = p.level.damageSources().mobAttack(dummy);
        var api = renApplyDamage(p, src, 6.0);
        var hp1 = rghp(dummy);
        var dealt = (hp0 < 0 || hp1 < 0) ? -1 : round2(hp0 - hp1);
        send(ctx, "AP_" + tag + "_HIT:api=" + api + ":atk_hp=" + hp0 + "->" + hp1
            + ":counter_dmg=" + dealt + ":before[" + before + "]:after[" + renState(p) + "]");
    } catch (e) {
        send(ctx, "AP_" + tag + "_ERR:" + exText(e));
    }
    return 1;
}

/** 一击打空黄心(16 点 generic 伤害;抗性提升后仍 ≥ 13 ⇒ 10 点吸收被吃光、玩家不会死) */
function doRenVoid(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    try { p.setGameMode(GameTypeClass.SURVIVAL); } catch (e) { /* 忽略 */ }
    try { p.setHealth(p.getMaxHealth()); } catch (e) { /* 忽略 */ }
    var before = renState(p);
    var api = "none";
    try {
        api = renApplyDamage(p, p.level.damageSources().generic(), 16.0);
    } catch (e) {
        send(ctx, "AP_" + tag + "_ERR:" + exText(e));
        return 1;
    }
    send(ctx, "AP_" + tag + "_VOIDHIT:api=" + api + ":hp=" + round2(p.getHealth())
        + ":before[" + before + "]:after[" + renState(p) + "]");
    return 1;
}

/**
 * 主动「熊孩子特权」全链路:按主动(只开会话) → 自选目标确认 ⇒ 目标(自己)获得 1 张卡牌 + 鼠鼠护盾,
 * 冷却写在确认之后。allowSelf=true 由服务端 DEBUG 行
 * `[TargetSelection] start ... action=ren_privilege ... allowSelf=true` 断言(与 t48 同一取证口径)。
 */
function doRenActive(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    renReset(p);
    // 清空主物品栏:发牌读数用「卡牌数量差值」,必须先把已有卡牌清零(背包满时 giveCardTo 会掉落到地上)
    runCmd(ctx, "clear @s");
    var err = equipSign(p, REN_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 1; }
    var cards0 = countAllCards(p);
    var cdBefore = signCooldownRemaining(p);
    BaseSignItemClass.performSkillForCurio(p);
    var session = TargetSelectionManagerClass.isSelecting(p) ? 1 : 0;
    var token = TargetSelectionManagerClass.sessionTokenForTests(p);
    var cdGated = signCooldownRemaining(p);
    var confirmed = 0, confirmErr = "";
    try {
        TargetSelectionManagerClass.confirm(p, token, p.getId());
        confirmed = 1;
    } catch (e) {
        confirmErr = exText(e);
    }
    var cards1 = countAllCards(p);
    var cdAfter = signCooldownRemaining(p);
    send(ctx, "AP_" + tag + "_ACTIVE:session=" + session + ":token_seen=" + (token > 0 ? 1 : 0)
        + ":cd_before=" + (cdBefore > 0 ? 1 : 0) + ":cd_gated=" + (cdGated > 0 ? 1 : 0)
        + ":confirmed=" + confirmed + ":cards_delta=" + (cards1 - cards0)
        + ":cd_after=" + (cdAfter > 0 ? 1 : 0) + ":" + renState(p)
        + (confirmErr ? ":err=" + confirmErr : ""));
    return 1;
}

// ── 史莱姆立牌(lulu):主动「治愈粘液」→ 目标选择器(2026-09-19 重写)─────────────
/**
 * 史莱姆立牌主动技能的游戏内取证(luluprep → luluactive → luluanchor 三条命令串成一条判据链):
 *
 * <p><b>luluprep</b>:基线(装 lulu 立牌 / 清治愈点 / 清主动冷却 / 丢弃附近实体)+ 摆两个判据靶:
 * 玩家自身先扣到 12 血(主体瞬间治疗可读)、6 格外一只**受损的猪**(可骑乘 ⇒ 范围治疗可读)、
 * 4 格外一只蜘蛛(范围缓慢可读)。
 *
 * <p><b>luluactive</b>(真实按键路径):按主动(= {@code BaseSignItem.performSkillForCurio},门控:只开会话)
 * → 读会话/令牌 → **对自身**确认 ⇒ 主体「+3 治愈 + 瞬间治疗」落在自己身上;两项范围能力以**自己**为中心
 * (猪被治疗、蜘蛛被缓慢);门控路径自己写冷却(cd_after=1)。
 *
 * <p><b>luluanchor</b>(**路由**判据,回答「另外 2 项范围能力是否作用于被指向的目标」):单人环境没有
 * 第二名玩家、而本技能的目标类型是 `PLAYER` ⇒ 按键路径无法选中「别的玩家」;故把治疗靶(猪)放
 * 24 格外,在**猪旁 4 格**放一只蜘蛛、在**玩家旁 4 格**放另一只,然后直接调用注册表里的
 * `TargetSelectionAction#apply(player, 猪)`:
 *   · 猪旁蜘蛛被缓慢      = 范围以**被指向的目标**为中心 ★决定性
 *   · 玩家旁蜘蛛**没有**缓慢 = 范围**不是**仍以施放者为中心 ★决定性
 *   · 猪被瞬间治疗(主体效果落在目标上)、玩家治愈点数不变(猪不是玩家 ⇒ 主体 +3 不加给施放者)
 */
var LULU_SIGN_ID = "astral_dice:lulu_sign";
var LULU_ACTION_ID = "lulu_healing_slime";
var TargetSelectionRegistryClass = Java.loadClass("com.merlinkitsune.starenginelib.target.TargetSelectionRegistry");

/** 治愈点数(读不到报 -1,便于用例区分「0 点」与「读失败」) */
function luluHealPoints(p) { try { return HealingManagerClass.getPoints(p); } catch (e) { return -1; } }

/** 生命值(保留两位;命中/治疗差值判据用) */
function luluHpR(e) { try { return Math.round(e.getHealth() * 100) / 100; } catch (e2) { return -1; } }

/** 效果读数,与既有读数同格式:"放大等级/剩余tick";无该效果 = "-",读失败 = "?" */
function luluFx(e, fx) {
    try {
        var inst = e.getEffect(fx);
        return inst == null ? "-" : (inst.getAmplifier() + "/" + inst.getDuration());
    } catch (e3) { return "?"; }
}

/**
 * 瞬间效果的**结算延迟**(2026-09-19 现场取证确立的读数口径)。
 *
 * <p>瞬间治疗效果(HEAL)经 {@code LivingEntity#addEffect} 只把实例放进 activeEffects,
 * 真正回血发生在**下一 tick** 的 {@code MobEffectInstance#tick → MobEffect#applyEffectTick}
 * (原版 {@code InstantenousMobEffect#shouldApplyEffectTickThisTick = duration >= 1};
 * 原版 {@code /effect give} 走的也是这条路径 —— NeoForge 的 addEffect 里没有 instant 分支)。
 * 故**同 tick** 读生命值必然读到旧值:现场实测施放前 {@code Dev 12.0f}、施放同 tick 仍 12.0f、
 * 下一 tick 才变 16.0f(猪 6.0f → 10.0f)。⇒ 「瞬间治疗是否落到目标」必须**延后读数**,
 * 否则用例只会拿到假 FAIL。
 */
var LULU_SETTLE_TICKS = 2;

/** 待读队列;无待读项时 tick 回调零开销 */
var luluAfter = [];

/**
 * 最近一次 {@code luluprep} 放下的判据靶句柄(**跨命令复用**)。
 *
 * <p><b>为什么不让 active 段自己按类型搜</b>:2026-09-19 实机两轮实测 —— 同一 ±8 盒内
 * {@code getEntitiesOfClass} 取到的实体本身是对的(旁证:`luluDiscardNearby` 靠它清场成功、
 * 诊断字段 {@code scan8} 报出真实条数 3),但**按 {@code typeIdOf(entity) === "minecraft:pig"} 取靶
 * 却取到了施放者自己**(读数 `pig_hp=12->16` = 玩家血量,而 prep 的猪是 6),且 `"minecraft:spider"`
 * 一条都匹配不到。两轮读数与该「按类型匹配不可靠」的解释完全一致 ⇒ 取靶改为**由 prep 发布句柄**,
 * 搜索只留作诊断({@code census} 字段把盒内每个实体的「类型串@血量」原样报出,便于日后定位)。
 */
var luluState = null;

/** 两点距离(保留两位;异常 -1)。锚点距离**实测**,不再硬编码字面量 */
function luluDistTo(a, b) {
    try { return Math.round(a.position().distanceTo(b.position()) * 100) / 100; }
    catch (e) { return -1; }
}

/** 实体是否仍存活于世界(1/0;异常 -1)。负对照必须带存在性读数,否则「空句柄/没进世界」会被读成「无效果」而假过 */
function luluPresent(e) {
    if (e == null) return 0;
    try { return e.isAlive() ? 1 : 0; } catch (e2) { return -1; }
}

/** 排一次延迟读数(kind = "self" / "anchor") */
function luluLater(kind, st) { st.kind = kind; st.left = LULU_SETTLE_TICKS; luluAfter.push(st); }

/**
 * 按类型在 AABB 内取第一只句柄 —— **只允许在 apply 之前**用。
 * 现场实测(2026-09-19):apply 之后在同一位置、同一 tick 再按 AABB 搜索会搜不到蜘蛛
 * (猪能搜到),原因未定 ⇒ 判据一律走句柄读数,施放后的搜索只作诊断字段(scan8/scan_err)。
 */
function luluFindOne(p, typeId, diameter) {
    try {
        var aabb = AABBClass.ofSize(p.position(), diameter, diameter, diameter);
        var list = p.level.getEntitiesOfClass(LivingEntityClass, aabb);
        for (var i = 0; i < list.size(); i++) {
            if (typeIdOf(list.get(i)) === typeId) return list.get(i);
        }
    } catch (e) { /* 取不到 ⇒ null,读数显式报 n/a */ }
    return null;
}

/** 延迟读数驱动(独立于珍珠观察窗的 tick 回调) */
function luluAfterTick() {
    if (luluAfter.length === 0) return;
    var keep = [];
    for (var i = 0; i < luluAfter.length; i++) {
        var st = luluAfter[i];
        st.left = st.left - 1;
        if (st.left > 0) { keep.push(st); continue; }
        try {
            var p = st.player, tag = st.tag;
            if (st.kind === "self") {
                emitTo(p, "AP_" + tag + "_AFTER:delay=" + LULU_SETTLE_TICKS
                    + ":hp=" + st.hp0 + "->" + luluHpR(p)
                    + ":pig_hp=" + (st.pig == null ? "n/a" : st.pigHp0 + "->" + luluHpR(st.pig))
                    + ":spider_slow=" + (st.spider == null ? "n/a" : luluFx(st.spider, MobEffectsClass.MOVEMENT_SLOWDOWN))
                    + ":cd_after=" + (signCooldownRemaining(p) > 0 ? 1 : 0));
            } else {
                emitTo(p, "AP_" + tag + "_AFTER:delay=" + LULU_SETTLE_TICKS
                    + ":which=" + st.kind
                    + ":target=" + st.botName
                    + ":anchor_dist=" + st.anchorDist
                    + ":bot_hp=" + st.botHp0 + "->" + (st.bot == null ? "n/a" : luluHpR(st.bot))
                    + ":spider_by_bot=" + (st.spiderByBot == null ? "?" : luluFx(st.spiderByBot, MobEffectsClass.MOVEMENT_SLOWDOWN))
                    + ":spider_by_bot_present=" + luluPresent(st.spiderByBot)
                    + ":spider_by_player=" + (st.spiderByPlayer == null ? "?" : luluFx(st.spiderByPlayer, MobEffectsClass.MOVEMENT_SLOWDOWN))
                    + ":spider_by_player_present=" + luluPresent(st.spiderByPlayer)
                    + ":caster_heal=" + st.heal0 + "->" + luluHealPoints(p));
            }
        } catch (e) { emitTo(st.player, "AP_" + st.tag + "_AFTER_EX:" + exText(e)); }
    }
    luluAfter = keep;
}

ServerEvents.tick(event => { try { luluAfterTick(); } catch (e) { /* 忽略 */ } });

/** 在 anchor 的相对偏移处生成一只无 AI 生物(纯 API;不用 runCmd,避免「下一 tick 才生效」) */
function luluSpawnAt(anchor, typeId, dx, dz) {
    var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(typeId));
    var mob = type.create(anchor.level);
    if (mob == null) return null;
    placeAt(mob, anchor.getX() + dx, anchor.getY(), anchor.getZ() + dz);
    try { mob.setNoAi(true); } catch (e1) { /* 忽略 */ }
    try { mob.setPersistenceRequired(); } catch (e2) { /* 忽略 */ }
    anchor.level.addFreshEntity(mob);
    return mob;
}

/** 丢弃半径内全部非玩家实体(基线归零;两参 getEntitiesOfClass + JS 侧过滤,与既有探针同写法)
 *  ⚠️ `AABB.ofSize` 传的是**直径** ⇒ 这里显式乘 2 才是「±radius 格」的半轴(与 countLightning 同口径) */
function luluDiscardNearby(p, radius) {
    var n = 0;
    try {
        var aabb = AABBClass.ofSize(p.position(), radius * 2, radius * 2, radius * 2);
        var list = p.level.getEntitiesOfClass(LivingEntityClass, aabb);
        var playerCls = null;
        try { playerCls = Java.loadClass("net.minecraft.world.entity.player.Player"); } catch (eP) { playerCls = null; }
        for (var i = 0; i < list.size(); i++) {
            var e = list.get(i);
            if (e == p) continue;
            // ⚠️ **必须保留玩家（含 Carpet 假人）** —— 函数头注释写的是「丢弃半径内全部**非玩家**实体」，
            //    但旧实现只跳过施法者自己 ⇒ 目标段会在 `confirm` 之前把**目标假人**一起 discard，
            //    随后 `TargetSelectionManager#confirm` 解析不到该实体 id ⇒
            //    `confirm FAIL: target_invalid`（实测 2026-09-21，目标 = Bot2 时 100% 复现；
            //    直接 `apply` 段不受影响，因为它用的是实体对象而不是 id）。
            if (playerCls != null) {
                try { if (e instanceof playerCls) continue; } catch (eI) { /* 判不出来就按旧口径处理 */ }
            }
            try { e.discard(); n = n + 1; } catch (e2) { /* 忽略 */ }
        }
    } catch (e3) { /* 忽略 */ }
    return n;
}

/** 基线 + 摆「自身段」判据靶:see 函数头注释 */
function doLuluPrep(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    try { HealingManagerClass.clear(p); } catch (e0) { /* 忽略 */ }
    try { ModAttachments.setSignActiveCooldownEnd(p, 0); } catch (e1) { /* 忽略 */ }
    try { ModAttachments.setSignActiveMaxCooldown(p, 0); } catch (e2) { /* 忽略 */ }
    // 清掉可能残留的选择会话:残留会让 performSkillForCurio 走「已在选择中」分支(⇒ session=0 假 FAIL)
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e2b) { /* 忽略 */ }
    var err = equipSign(p, LULU_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 1; }
    luluDiscardNearby(p, 48);
    // 自身先扣到 12 血:主体瞬间治疗(HEAL I = 4 点)可读
    try { p.setHealth(12); } catch (e3) { /* 忽略 */ }
    var pig = luluSpawnAt(p, "minecraft:pig", 0, 6);
    var spider = luluSpawnAt(p, "minecraft:spider", 0, 4);
    if (pig == null || spider == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed"); return 1; }
    try { pig.setHealth(6); } catch (e4) { /* 忽略 */ }   // 猪受损 ⇒ 范围治疗可读(上限 10)
    // 发布句柄给 active 段(见 luluState 注释:按类型搜索不可靠)
    luluState = { pig: pig, spider: spider, tag: tag };
    send(ctx, "AP_" + tag + "_PREP:heal=" + luluHealPoints(p) + ":hp=" + luluHpR(p)
        + ":pig_hp=" + luluHpR(pig) + ":spider_slow=" + luluFx(spider, MobEffectsClass.MOVEMENT_SLOWDOWN)
        + ":cd=" + (signCooldownRemaining(p) > 0 ? 1 : 0)
        + ":pig_eid=" + pig.getId() + ":spider_eid=" + spider.getId() + ":p_eid=" + p.getId());
    return 1;
}

/**
 * 「自身段」:按主动(门控)→ 对自身确认 → 读门控/冷却/光环;**生命值走延迟读数**。
 *
 * <p>靶子句柄必须在**技能施放前**取(见 {@link luluFindOne});本命令只报**同步可读**的项
 * (会话/令牌/冷却/治愈点/句柄上的缓慢),生命值与光环治疗结果由 {@code AP_<tag>_AFTER} 在
 * {@link LULU_SETTLE_TICKS} tick 后给出 —— 同 tick 读生命值只会得到假 FAIL。
 */
function doLuluActive(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    // 靶子句柄优先取 luluprep 留下的(见 luluState 注释);句柄已失效时才退回按类型搜
    var pig = null, spider = null;
    if (luluState != null && luluPresent(luluState.pig) === 1 && luluPresent(luluState.spider) === 1) {
        pig = luluState.pig;
        spider = luluState.spider;
    } else {
        pig = luluFindOne(p, "minecraft:pig", 16);
        spider = luluFindOne(p, "minecraft:spider", 16);
    }
    var heal0 = luluHealPoints(p);
    var hp0 = luluHpR(p);
    var pigHp0 = pig == null ? -1 : luluHpR(pig);
    var cdBefore = signCooldownRemaining(p);
    var session = 0, token = -1, cdGated = -1, confirmed = 0, confirmErr = "";
    try {
        BaseSignItemClass.performSkillForCurio(p);
        session = TargetSelectionManagerClass.isSelecting(p) ? 1 : 0;
        token = TargetSelectionManagerClass.sessionTokenForTests(p);
        cdGated = signCooldownRemaining(p);
        TargetSelectionManagerClass.confirm(p, token, p.getId());
        confirmed = 1;
    } catch (e5) { confirmErr = exText(e5); }
    // 诊断(不参与判定):施放后的同位置 AABB 搜索;值为 -2 表示搜索本身抛异常,此时附 scan_err
    var scan8 = -1, scanErr = "";
    try {
        scan8 = p.level.getEntitiesOfClass(LivingEntityClass,
            AABBClass.ofSize(p.position(), 16, 16, 16)).size();
    } catch (e6) { scan8 = -2; scanErr = exText(e6); }
    // 诊断(不参与判定):盒内每个实体的「类型串@血量」原样报出(见 luluState 注释)
    var census = "";
    try {
        var list0 = p.level.getEntitiesOfClass(LivingEntityClass, AABBClass.ofSize(p.position(), 16, 16, 16));
        for (var k = 0; k < list0.size() && k < 6; k++) {
            census = census + (k > 0 ? "|" : "") + typeIdOf(list0.get(k)) + "@" + luluHpR(list0.get(k));
        }
        if (census === "") census = "-";
    } catch (e7) { census = "<err>"; }
    send(ctx, "AP_" + tag + "_SELF:session=" + session + ":token_seen=" + (token > 0 ? 1 : 0)
        + ":cd_before=" + (cdBefore > 0 ? 1 : 0) + ":cd_gated=" + (cdGated > 0 ? 1 : 0)
        + ":confirmed=" + confirmed
        + ":heal=" + heal0 + "->" + luluHealPoints(p)
        + ":spider_slow=" + (spider == null ? "n/a" : luluFx(spider, MobEffectsClass.MOVEMENT_SLOWDOWN))
        + ":pig_eid=" + (pig == null ? -1 : pig.getId())
        + ":spider_eid=" + (spider == null ? -1 : spider.getId()) + ":p_eid=" + p.getId()
        + ":pig_present=" + luluPresent(pig) + ":spider_present=" + luluPresent(spider)
        + ":scan8=" + scan8
        + ":census=" + census
        + ":cd_after=" + (signCooldownRemaining(p) > 0 ? 1 : 0)
        + (scanErr ? ":scan_err=" + scanErr : "")
        + (confirmErr ? ":err=" + confirmErr : ""));
    luluLater("self", { player: p, tag: tag, hp0: hp0, pig: pig, pigHp0: pigHp0, spider: spider });
    return 1;
}

// ── 2026-09-28(C 批:单人专用测试项清理)目标段改造 ─────────────────────────────
// 旧「锚点段」用 `apply(player, 猪)` 绕过目标选择器,原因是单人世界没有第二名**真实**玩家
// (TargetType.PLAYER + allowSelf 的自选段只能覆盖「目标 = 自己」)。Carpet 的 `/player` bot 是
// 真 ServerPlayer ⇒ 「目标 = 另一名玩家」两条路径都能在游戏内走通:
//   · `luluconfirm` —— **真实链路**:按键开会话 → 服务端 confirm(bot.id) → 管理器校验 → action.apply;
//   · `luluanchor`  —— 直接 apply(bot)(路由判据:范围中心/受体跟着目标走,不再靠猪当替身)。
// 两条命令共用同一个 apply 实现体,故只保留一处判据代码。

/** 目标侧只读读数(真实玩家/Carpet bot):在场 + 名字 + 身份 + 血量 + 坐标 */
function luluTargetRead(t, wanted) {
    if (t == null) return "found=0:want=" + wanted;
    var name = wanted;
    try { name = "" + t.getName().getString(); } catch (e1) { /* 保留 want */ }
    return "found=1:want=" + wanted + ":name=" + name + ":bot_hp=" + luluHpR(t);
}

/** 目标名(把「谁被操作」写进字段名,便于两线逐字断言;取不到时用 `-`) */
function luluNameOf(t) {
    if (t == null) return "-";
    try { return "" + t.getName().getString(); } catch (e) { return "-"; }
}

/**
 * 目标段主体:把「被指向的目标」换成真实玩家(bot)。
 * @param which `confirm` = 走真实按键+服务端确认链路;`apply` = 直接调用注册表动作(路由判据)
 */
function luluRunTargetSegment(ctx, tag, t, which) {
    var p = ctx.source.getPlayerOrException();
    var botName = luluNameOf(t);
    // 目标的位置由**用例**用原版 `/tp <bot> …` 摆好(本函数不注入命令:探针内的 `runCmd` 走
    // `performPrefixedCommand`,对 Carpet bot 的 `tp` 实测不生效 —— 见 sp_cleanup_report 的说明)。
    luluDiscardNearby(p, 48);
    var spiderByBot = luluSpawnAt(t, "minecraft:spider", 0, 4);
    var spiderByPlayer = luluSpawnAt(p, "minecraft:spider", 0, 4);
    var heal0 = luluHealPoints(p);
    var botHp0 = luluHpR(t);
    var targetDist = luluDistTo(p, t);   // 实测距离
    var err = "", session = 0, token = -1, confirmCalled = 0;
    if (which === "confirm") {
        try {
            BaseSignItemClass.performSkillForCurio(p);
            session = TargetSelectionManagerClass.isSelecting(p) ? 1 : 0;
            token = TargetSelectionManagerClass.sessionTokenForTests(p);
            TargetSelectionManagerClass.confirm(p, token, t.getId());
            confirmCalled = 1;
        } catch (e2) { err = exText(e2); }
    } else {
        try {
            var action = TargetSelectionRegistryClass.get(LULU_ACTION_ID);
            action.apply(p, t);
        } catch (e2b) { err = exText(e2b); }
    }
    send(ctx, "AP_" + tag + "_ANCHOR:which=" + which + ":target=" + botName
        + ":target_dist=" + targetDist + ":bot_hp=" + botHp0 + "->" + luluHpR(t)
        + ":session=" + session + ":token_seen=" + (token > 0 ? 1 : 0) + ":confirm_called=" + confirmCalled
        + ":spider_by_bot=" + (spiderByBot == null ? "?" : luluFx(spiderByBot, MobEffectsClass.MOVEMENT_SLOWDOWN))
        + ":spider_by_player=" + (spiderByPlayer == null ? "?" : luluFx(spiderByPlayer, MobEffectsClass.MOVEMENT_SLOWDOWN))
        + ":caster_heal=" + heal0 + "->" + luluHealPoints(p)
        + ":cd_after=" + (signCooldownRemaining(p) > 0 ? 1 : 0)
        + (err ? ":err=" + err : ""));
    // 主体瞬间治疗落在**目标(真实玩家)**上:同 tick 读不到 ⇒ 延迟读数;两只蜘蛛各带存在性读数,
    // 杜绝「对象创建了却没进世界 ⇒ 读数 - 也算通过」的负对照假过
    luluLater(which, { player: p, tag: tag, bot: t, botName: botName, botHp0: botHp0, heal0: heal0,
        spiderByBot: spiderByBot, spiderByPlayer: spiderByPlayer, anchorDist: targetDist });
    return 1;
}

/** 「目标段」判据靶解析:按名字取真实玩家(找不到**不**静默降级成「没人」) */
function luluResolveTarget(ctx, tag, nameText) {
    var t = teruFindPlayer(ctx, nameText);
    if (t == null) { send(ctx, "AP_" + tag + "_ERR:no_player:" + nameText); return null; }
    return t;
}

/** 目标段·真实确认链路:按键开会话 → 服务端 confirm(bot) → 管理器校验 → action.apply */
function doLuluConfirm(ctx, tag, nameText) {
    var t = luluResolveTarget(ctx, tag, nameText);
    if (t == null) return 1;
    return luluRunTargetSegment(ctx, tag, t, "confirm");
}

/** 目标段·直接 apply(路由判据:范围中心与受体跟着目标走,不再用猪当玩家替身) */
function doLuluAnchor(ctx, tag, nameText) {
    var t = luluResolveTarget(ctx, tag, nameText);
    if (t == null) return 1;
    return luluRunTargetSegment(ctx, tag, t, "apply");
}

// ── 效果牌 → 目标选择器(2026-09-25)只读读数 ─────────────────────────────────
/**
 * 四张效果牌(加急加快 express_delivery / 奢华大餐 luxury_feast / 狂暴 berserk /
 * 你有我有 you_have_i_have)自 2026-09-25 起改走**目标选择器**(与立牌主动同一套
 * TargetSelectionManager + SignSelectionGate,取代旧的「右键自身 / 下蹲右键其他玩家 /
 * 点击玩家实体」)。本组命令把该流程里**截图与聊天都判不出**的服务端事实报全,
 * 供 CARD-SELECTOR-* 用例断言:
 *   · hand / handn —— 主手物品 id 与「该 id 在主背包内的总数」(卡牌是否被消耗的判据)
 *   · cards        —— 主背包全部卡牌数(「你有我有」给自身发牌的判据)
 *   · sel / token  —— 是否处于目标选择会话(TargetSelectionManager 的测试钩子;无会话 token=-1)
 *   · speed / berserk —— 迅捷 / 狂暴 的「等级/剩余tick」("-" = 当前无该效果)
 *   · hp / dhp     —— 当前生命/上限,以及相对最近一次 cardprep 的差值(治疗卡(奢华大餐)的
 *                    直接判据:先 /effect give instant_damage 受伤,确认后 dhp>0;无治疗则 dhp=0)
 *   · plays / max / blocked —— 出牌周期计数/上限/是否被出牌锁挡住(EffectCardPeriod)
 *
 * 会话的 action / type / radius / allowSelf 由服务端 DEBUG 行给出
 * (`[Astral Dice][TargetSelection] start ... action=... allowSelf=...`,落 logs/debug.log),
 * 客户端提示与「对自身使用」的按键口径由截图给出 —— 本读数不重复报这两类。
 *
 * 只读口径:cardread 一字不写;cardprep 只做出牌周期归零 + 清主背包 + 发 1 张(测试基线);
 * cardself 只调 TargetSelectionManager.confirm 的对自身确认入口(与客户端右键同路)。
 * **取消/超时/被拒绝均不消耗卡牌 ⇒ handn 与 plays 是这条红线的机器判据。**
 */
var DESC_BERSERK_CARD = "effect.astral_dice.berserk";

/** 最近一次 cardprep 时的生命值(供 cardread 的 "dhp" 报治疗差值;-1 = 本次未 prep) */
var cardHpBefore = -1;
// 充能冷却读数用:上一次读到的效果牌冷却到期 tick(判「进行中的倒计时是否被改动」)
var chargeLastCardEnd = -1;

/** 效果实例 → "等级/剩余tick"(无该效果 = "-") */
function effectAmpDur(inst) {
    if (inst == null) return "-";
    try { return inst.getAmplifier() + "/" + inst.getDuration(); } catch (e) { return "?"; }
}

/** 效果牌选择器读数(单行机器格式) */
function cardState(p) {
    var hand = itemIdOf(p.getMainHandItem());
    var handn = 0;
    var inv = p.getInventory();
    for (var i = 0; i < inv.getContainerSize(); i++) {
        var st = inv.getItem(i);
        if (st.isEmpty()) continue;
        if (itemIdOf(st) === hand) handn += st.getCount();
    }
    return "hand=" + hand + ":handn=" + handn + ":cards=" + countAllCards(p)
        + ":sel=" + (TargetSelectionManagerClass.isSelecting(p) ? 1 : 0)
        + ":token=" + TargetSelectionManagerClass.sessionTokenForTests(p)
        + ":speed=" + effectAmpDur(findEffect(p, DESC_SPEED))
        + ":berserk=" + effectAmpDur(findEffect(p, DESC_BERSERK_CARD))
        + ":hp=" + Math.round(p.getHealth() * 10) / 10 + "/" + Math.round(p.getMaxHealth() * 10) / 10
        + ":dhp=" + (cardHpBefore < 0 ? "-" : Math.round((p.getHealth() - cardHpBefore) * 10) / 10)
        + ":plays=" + EffectCardPeriodClass.getPlayCount(p)
        + ":max=" + EffectCardPeriodClass.getMaxAllowed(p)
        + ":blocked=" + (EffectCardPeriodClass.isBlocked(p) ? 1 : 0);
}

/**
 * 从同一基线起测:出牌周期归零 + 清空全部原版状态效果 + 清空主背包 + 把 1 张目标卡牌放进**主手**。
 * 用 `/item replace entity @s weapon.mainhand` 而不是「服务端改 selected 再 give」:selected 由客户端
 * 上报、服务端单方面改写不会同步回客户端,会出现「服务端以为在手里、客户端显示别的槽」的错位
 * (读数里的 hand= 会直接暴露这种错位)。
 *
 * ⚠️ 2026-09-25「手持即选择」后本命令会**顺带触发**服务端自动开局(下一个 tick):`_PREP` 那一行读的是
 * 命令生效前的状态,断言一律落在其后独立调用的 `<tag>_READ` 上。
 */
function doCardPrep(ctx, tag, itemId) {
    var p = ctx.source.getPlayerOrException();
    if (resolveItem(itemId) == null) {
        send(ctx, "AP_" + tag + "_ERR:unknown_item:" + itemId);
        return 1;
    }
    cardHpBefore = p.getHealth();
    resetEffectCardCycle(p);
    // 必须一并清掉状态效果:出牌锁的第三态是「上一张效果牌的效果仍在生效」
    // (EffectCardPeriod.isBlocked → isEffectPending,如 3 分钟的狂暴)⇒ 不清就会让各区块基线互相污染
    // (⚠️ /effect clear 只能清原版效果:本模组效果被 ModEffectEvents#onModEffectRemovalPrevented 保护)
    runCmd(ctx, "effect clear @s");
    runCmd(ctx, "clear @s");
    // 放进**主手**(weapon.mainhand):不能用 hotbar.<selected> —— selected 由客户端上报,
    // 服务端单方面改写不同步回客户端,会出现「服务端以为在手里、客户端显示别的槽」的错位
    runCmd(ctx, "item replace entity @s weapon.mainhand with " + itemId + " 1");
    send(ctx, "AP_" + tag + "_PREP:" + cardState(p));
    return 1;
}

/** 选择器读数(只读,不写任何状态) */
function doCardRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    send(ctx, "AP_" + tag + "_READ:" + cardState(p));
    return 1;
}

/**
 * 服务端直接提交「对自身」确认(与客户端右键同路,但绕开「准星下必须有合法目标」这一客户端前提)。
 * 单人测试环境没有第二个玩家,故:① 客户端左键确认(瞄准其他玩家)无法在此环境复现;
 * ② 「确认时卡牌已不在身上」这类门控只能用本入口驱动。confirm 是 void,失败原因在服务端日志
 * (`[TargetSelection] confirm FAIL: ...`)与读数(sel / handn / plays)里判。
 */
function doCardSelf(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var token = TargetSelectionManagerClass.sessionTokenForTests(p);
    var called = 0, err = "";
    try {
        TargetSelectionManagerClass.confirm(p, token, p.getId());
        called = 1;
    } catch (e2) {
        err = exText(e2);
    }
    send(ctx, "AP_" + tag + "_SELF:called=" + called + ":token=" + token
        + (err ? ":err=" + err : "") + ":" + cardState(p));
    return 1;
}

// ════════════════════════════════════════════════════════════════════════════
// 活体书页重写(2026-09-25 用户裁决):敌对目标选择器 + 飞行命中法伤 + 连续出牌规则修正
//   被测语义:
//     ① 仅敌对目标可选(ENEMY = 敌对生物 ∪ 已被激怒的中立生物;不含玩家、不可自用);
//     ② 使用后书页以箭矢 4/5(2.4 格/tick)飞向目标、逐 tick 跟踪修正、必定命中、可穿透方块;
//     ③ 命中伤害 = 基础 2 + 调查员已用页数(登记为法伤 ⇒ 受全部法伤加成影响),并施加 1 层标记;
//     ④ 仅命中**前**已有 ≥3 层标记的目标才补记本周期出牌数 +1,严格封顶 9。
//   命令(读数行一律 AP_<tag>_ 前缀;除测试基线重置外全部只读):
//     /astralprobe lpprep <tag> <type> <dist> <layers>  基线 + 摆靶 + 预置标记层数 + 牌进主手
//     /astralprobe lpnonhostile <tag>                   基线 + 摆被动生物 + 牌进主手(确认应被类型校验拒绝)
//     /astralprobe lpwall <tag> <dist>                  基线 + 摆敌对靶 + 中间砌墙 + 牌进主手(穿透方块)
//     /astralprobe lpshoot <tag>                        服务端确认当前靶(与客户端左键确认同路)
//     /astralprobe lpread <tag>                         只读读数(靶血/伤害/标记/出牌数/在飞)
//     /astralprobe lpcredit <tag> <value>               预置本周期活体书页出牌数加成(封顶用例)
//     /astralprobe lprin <tag> <value>                  预置「调查员已用页数」rin_pages(伤害口径用例)
//     /astralprobe lpchain <tag> <hits> <dist>          同一靶上连续出牌 hits 次(标记累积 → 首次补记)
//     /astralprobe lpreset <tag>                        归零出牌轮 + 牌回主手,**保留当前靶**(跨轮累积标记)
//     /astralprobe lpclean <tag>                        收尾:取消会话 + 清靶/拆墙 + 清主手 + 周期归零
//   ⚠️ 改探针后必须冷启动才生效。
//   ⚠️ **`type` 参数是 `StringArg.string()`(QUOTABLE_PHRASE),用例里必须加引号**:
//      `/astralprobe lpprep T "minecraft:spider" 6 0` —— 不加引号时 Brigadier 直接拒收(参数后应为空格),
//      命令整条不执行、读数行**一条都不会出现**(与 `equipslot` 的 `item` 参数同一坑,见 MIMI-RENAME 用例)。
//   ⚠️ **靶子选型**:白天地表会点燃僵尸(无 AI 也一样烧,实测 1.4 s 掉 4 血 ⇒ 污染 `dmg` 读数)。
//      用例统一用 **`minecraft:spider`**(非亡灵、不烧,最大生命 16)并先 `/time set midnight`。
//      `lpshoot` 另会在确认前把靶子回满血并重取基线,使 `dmg` 只反映本次书页命中。
//   ⚠️ **背包/方块一律走 API,不要用 `runCmd`**(2026-09-19 实测取证):
//      在**同一条探针命令内部**用 `runCmd` 改玩家背包(`/clear`、`/item replace`)或方块(`/fill`)时,
//      本命令内的读数与后续动作**都还看不到改动**;改动要到**下一个 tick**(即下一条注入命令)才生效。
//      取证:run 2 的 `AP_LP0_PREP` 里 `hand=` 仍是改动前的旧物(`patchouli:guide_book`),
//      而 900 ms 后的下一条命令读到的是 `astral_dice:effect_card_living_page`;
//      同一轮 `/fill` 后**同命令内**读方块仍是 `minecraft:air`(`LP_LW0_WALL:wall_block=minecraft:air`)。
//      ⇒ 需要「立即生效」的一律走 `lpSetHand` / `lpClearInventory` / `lpFillBox`(纯 API,同步),
//      命令只留给「晚一 tick 也无所谓」的世界/效果类操作(`/effect clear`)。
// ════════════════════════════════════════════════════════════════════════════

/** 活体书页飞行调度器(两发布线同名同包;取不到时读数退化为 na) */
function loadLivingPageFlightScheduler() {
    try { return Java.loadClass("com.merlinkitsune.astral_dice.event.LivingPageFlightScheduler"); }
    catch (e) { return null; }
}

/** 效果牌基类(用于直接驱动「手持即选择」入口;取不到时 lpchain 会报 ok=0) */
function loadBaseEffectCardItemClass() {
    try { return Java.loadClass("com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem"); }
    catch (e) { return null; }
}

var LivingPageFlightSchedulerClass = loadLivingPageFlightScheduler();
var LivingPageBaseCardClass = loadBaseEffectCardItemClass();

/** 活体书页探针状态(跨命令保持;lpclean 置空) */
var lpState = null;

function lpCardId() { return "astral_dice:effect_card_living_page"; }

/** 主手牌读数 + 背包内活体书页总数(判「确认后才消耗」:handn 1 → 0) */
function lpHandState(p) {
    return "hand=" + itemIdOf(p.getMainHandItem()) + ":handn=" + lpCardCount(p);
}

function lpCardCount(p) {
    var n = 0;
    try {
        var inv = p.getInventory();
        var id = lpCardId();
        for (var i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty() && itemIdOf(st) === id) n += st.getCount();
        }
    } catch (e) { return -1; }
    return n;
}

function lpMaxHp(d) { try { return Math.round(d.getMaxHealth() * 100) / 100; } catch (e) { return -1; } }

/**
 * 把一张牌放进主手(itemId 为空 ⇒ 清空主手)。**纯 API,同 tick 立即生效**;
 * 为什么不能用 `runCmd("item replace ...")` 见本段顶部「背包/方块一律走 API」。
 */
function lpSetHand(p, itemId) {
    try {
        var InteractionHandClass = Java.loadClass("net.minecraft.world.InteractionHand");
        var stack = (itemId == null || itemId === "") ? ItemStack.EMPTY : new ItemStack(resolveItem(itemId));
        p.setItemInHand(InteractionHandClass.MAIN_HAND, stack);
        return 1;
    } catch (e) { return "ERR:" + exText(e); }
}

/** 清空玩家**原版**背包(API;含护甲/副手,不含 Curios 饰品栏)。返回清掉的槽位数,-1 = 整体失败。 */
function lpClearInventory(p) {
    var n = 0;
    try {
        var inv = p.getInventory();
        for (var i = 0; i < inv.getContainerSize(); i++) {
            try { inv.setItem(i, ItemStack.EMPTY); n = n + 1; } catch (e1) { /* 单槽失败不影响其余 */ }
        }
    } catch (e) { return -1; }
    return n;
}

/**
 * 用 API 把一块长方体区域填成 blockId(纯 API,同 tick 立即生效;`/fill` 在本命令内读不到)。
 * 返回写入的方块数,失败返回 `ERR:...`。
 */
function lpFillBox(p, x1, y1, z1, x2, y2, z2, blockId) {
    var n = 0;
    try {
        var BlockPosClass = Java.loadClass("net.minecraft.core.BlockPos");
        var state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(blockId)).defaultBlockState();
        for (var x = x1; x <= x2; x++) {
            for (var y = y1; y <= y2; y++) {
                for (var z = z1; z <= z2; z++) {
                    try { p.level.setBlock(new BlockPosClass(x, y, z), state, 3); n = n + 1; }
                    catch (e1) { /* 单块失败不影响其余 */ }
                }
            }
        }
    } catch (e) { return "ERR:" + exText(e); }
    return n;
}

/**
 * 该坐标方块的注册 id(只读)——「穿透方块」用例**必须**用它取证:
 * `/fill` 的返回值经 Rhino 读出来是 `undefined`(实测),不能当判据;
 * 直接读方块状态才是「墙真的建好了」的硬证据。
 */
function lpBlockId(p, x, y, z) {
    try {
        var BlockPosClass = Java.loadClass("net.minecraft.core.BlockPos");
        var state = p.level.getBlockState(new BlockPosClass(x, y, z));
        return "" + BuiltInRegistries.BLOCK.getKey(state.getBlock());
    } catch (e) { return "err:" + exText(e); }
}
function lpMark(d) { try { return "" + MarkManagerClass.getLevel(d); } catch (e) { return "err"; } }
function lpCredit(p) { try { return "" + ModAttachments.getLivingPageCycleBonus(p); } catch (e) { return "err"; } }
function lpPlays(p) { try { return "" + EffectCardPeriodClass.getPlayCount(p); } catch (e) { return "err"; } }
function lpMaxPlays(p) { try { return "" + EffectCardPeriodClass.getMaxAllowed(p); } catch (e) { return "err"; } }
function lpBlocked(p) { try { return EffectCardPeriodClass.isBlocked(p) ? 1 : 0; } catch (e) { return "err"; } }
function lpSel(p) { try { return TargetSelectionManagerClass.isSelecting(p) ? 1 : 0; } catch (e) { return "err"; } }
function lpToken(p) { try { return TargetSelectionManagerClass.sessionTokenForTests(p); } catch (e) { return -1; } }

/** 靶读数:hp/最大血量 + 当前标记层数 */
function lpDummyState(d) {
    if (d == null) return "hp=-:max=-:mark=-";
    return "hp=" + rghp(d) + ":max=" + lpMaxHp(d) + ":mark=" + lpMark(d);
}

/** 在飞读数:数量/最早剩余 tick(-1 = 无;na = 类不可用) */
function lpFlight(p) {
    if (LivingPageFlightSchedulerClass == null) return "na";
    try {
        return "" + LivingPageFlightSchedulerClass.pendingCount(p.level)
            + "/" + LivingPageFlightSchedulerClass.pendingRemainingTicks(p.level);
    } catch (e) { return "err:" + exText(e); }
}

/** 最近一次飞行的「离施法者眼位最近的已发射粒子」距离(格;-1 = 本次未发射;na/err 同 lpFlight) */
function lpMinEye(p) {
    if (LivingPageFlightSchedulerClass == null) return "na";
    try {
        var v = LivingPageFlightSchedulerClass.lastFlightMinEyeDistance();
        if (v < 0) return "-1";
        var n = Math.round(v * 100);
        return Math.floor(n / 100) + "." + ("00" + (n % 100)).slice(-2);
    } catch (e) { return "err"; }
}

/** 活体书页相关全部原始值读数(单行机器格式) */
function lpReadout(p, d, dmg) {
    return lpHandState(p)
        + ":sel=" + lpSel(p) + ":token=" + lpToken(p)
        + ":dummy=" + lpDummyState(d) + ":dmg=" + dmg
        + ":credit=" + lpCredit(p) + ":plays=" + lpPlays(p)
        + ":max=" + lpMaxPlays(p) + ":blocked=" + lpBlocked(p)
        // cards = 主背包内**活体书页**张数(2026-09-19 追加)：效果牌确认时会消耗手牌,
        // 而「魔法箭袋」触发会**返还**第一张使用的效果牌 ⇒ 用它可以判定返还确实到手
        // (无箭袋/未触发时读数恒为 0,触发后为 1)。
        + ":cards=" + lpCardCount(p)
        + ":flight=" + lpFlight(p)
        // mineye = 最近一次飞行里「离施法者眼位最近的**已发射**拖尾粒子」距离(格;-1 = 本次未发射);
        // 判定用户要求「第一人称发射粒子不遮挡视野」:期望恒 >= 调度器 EYE_CLEAR_RADIUS(1.25)
        + ":mineye=" + lpMinEye(p);
}

/** 活体书页用例统一基线:出牌周期归零 + 清原版效果 + 摘掉「已使用伤害效果牌」标记 + 清空背包 */
function lpBaseline(ctx, p) {
    resetEffectCardCycle(p);
    runCmd(ctx, "effect clear @s");
    // 清背包走 API(立即生效);`/clear @s` 在本命令内读到的是旧背包,会把 PREP 读数与连续出牌带偏
    lpClearInventory(p);
    // 活体书页效果(1:00)会锁住出牌轮 ⇒ 脚手架显式摘掉(生产路径靠效果自然到期)
    try { clearExtraPlayEffects(p); } catch (e) { /* 忽略 */ }
}

/** 基线 + 摆靶(可选预置标记层数)+ 把 1 张活体书页放进主手 */
function doLpPrep(ctx, tag, typeId, distText, layersText) {
    var p = ctx.source.getPlayerOrException();
    var dist = 6, layers = 0;
    var pd = parseInt(distText, 10); if (!isNaN(pd) && pd > 0) dist = pd;
    var pl = parseInt(layersText, 10); if (!isNaN(pl) && pl > 0) layers = pl;
    lpBaseline(ctx, p);
    var d = spawnDummy(p, typeId, dist);
    if (d == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed:" + typeId); return 1; }
    try { d.setHealth(d.getMaxHealth()); } catch (e1) { /* 忽略 */ }
    try { d.setNoAi(true); } catch (e2) { /* 忽略 */ }
    var seeded = 0;
    for (var i = 0; i < layers; i++) {
        try { MarkManagerClass.apply(d); seeded = seeded + 1; } catch (e3) { break; }
    }
    var set = lpSetHand(p, lpCardId());
    lpState = { tag: tag, player: p, dummy: d, hpBefore: rghp(d) };
    send(ctx, "AP_" + tag + "_PREP:type=" + typeId + ":dist=" + dist + ":seed=" + seeded
        + ":set=" + set + ":" + lpReadout(p, d, "-"));
    return 1;
}

/** 被动生物版基线(确认应被 ENEMY 类型校验拒绝:会话保留、卡牌不消耗) */
function doLpNonHostile(ctx, tag) {
    return doLpPrep(ctx, tag, "minecraft:cow", "4", "0");
}

/** 基线 + 摆敌对靶 + 玩家与靶之间砌一堵实心墙(书页飞行不做方块碰撞 ⇒ 仍须命中) */
function doLpWall(ctx, tag, distText) {
    var p = ctx.source.getPlayerOrException();
    var dist = 8;
    var pd = parseInt(distText, 10); if (!isNaN(pd) && pd > 3) dist = pd;
    lpBaseline(ctx, p);
    // 靶子同 lpprep:统一用蜘蛛(非亡灵不烧,最大生命 16)
    var d = spawnDummy(p, "minecraft:spider", dist);
    if (d == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed:spider"); return 1; }
    try { d.setHealth(d.getMaxHealth()); } catch (e1) { /* 忽略 */ }
    try { d.setNoAi(true); } catch (e2) { /* 忽略 */ }
    var x1 = Math.floor(p.getX()) - 1, y1 = Math.floor(p.getY()), z1 = Math.floor(p.getZ() + dist / 2.0);
    var x2 = Math.floor(p.getX()) + 1, y2 = y1 + 2, z2 = z1;
    // 墙走 API:同命令内即可读回方块(硬证据),`/fill` 在本命令内读到的仍是 air
    var placed = lpFillBox(p, x1, y1, z1, x2, y2, z2, "minecraft:obsidian");
    var set = lpSetHand(p, lpCardId());
    lpState = {
        tag: tag, player: p, dummy: d, hpBefore: rghp(d),
        wall: [x1, y1, z1, x2, y2, z2]
    };
    send(ctx, "AP_" + tag + "_WALL:dist=" + dist + ":wall=" + x1 + "," + y1 + "," + z1
        + ":wall_block=" + lpBlockId(p, x1, y1, z1)
        + ":blocks=" + placed + ":set=" + set + ":" + lpReadout(p, d, "-"));
    return 1;
}

/** 服务端确认当前靶(与客户端左键确认同路;失败原因看服务端日志的 confirm FAIL 行) */
function doLpShoot(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    if (lpState == null || lpState.dummy == null) { send(ctx, "AP_" + tag + "_ERR:no_dummy"); return 1; }
    var d = lpState.dummy;
    // stale = 1:本次 tag 与当前 lpState 不匹配 ⇒ **上一条 prep 命令没落地**(注入丢键/chat 未提交),
    // 若不报出来,读数会指向上一只靶并伪装成「血量/最大血量不对」的假缺陷(2026-09-19 实测:lpnonhostile 丢了一条)
    var stale = (lpState.tag === tag) ? 0 : 1;
    var token = lpToken(p);
    // 命中前把靶子回满血并重取基线:**本次 dmg 只反映书页命中**,
    // 与"靶子在使用前已被环境/其它来源掉过血"解耦(否则读数不可判)。
    try { d.setHealth(d.getMaxHealth()); } catch (e0) { /* 忽略 */ }
    lpState.hpBefore = rghp(d);
    var called = 0, err = "";
    try {
        TargetSelectionManagerClass.confirm(p, token, d.getId());
        called = 1;
    } catch (e) { err = exText(e); }
    // mark_before = 命中**前**的标记层数(飞行尚未抵达,故此刻读到的就是判定用值)
    // 有墙的用例把**确认这一刻**的墙方块一并报出:证明"命中时墙确实存在"而不是建墙失败后的假命中
    var wall = "";
    if (lpState.wall != null) {
        wall = ":wall_block=" + lpBlockId(p, lpState.wall[0], lpState.wall[1], lpState.wall[2]);
    }
    send(ctx, "AP_" + tag + "_SHOOT:called=" + called + ":token=" + token
        + ":mark_before=" + lpMark(d) + ":" + lpReadout(p, d, "-") + wall
        + ":stale=" + stale
        + (err ? ":err=" + err : ""));
    return 1;
}

/** 只读读数:dmg = 基线血量 - 当前血量(命中造成的基础值;法伤加成另有绿色跳字) */
function doLpRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var d = lpState == null ? null : lpState.dummy;
    var stale = (lpState == null) ? 1 : ((lpState.tag === tag) ? 0 : 1);
    var dmg = "-";
    if (d != null && lpState.hpBefore != null && lpState.hpBefore >= 0) {
        dmg = Math.round((lpState.hpBefore - rghp(d)) * 100) / 100;
    }
    send(ctx, "AP_" + tag + "_READ:" + lpReadout(p, d, dmg) + ":stale=" + stale);
    return 1;
}

/**
 * 活体书页**分隔出牌**脚手架:归零出牌轮(等价于「冷却到期后的新一轮」)+ 把 1 张牌放回主手,
 * **保留当前靶** ⇒ 靶身上的标记**跨轮累积**。
 *
 * <p>用途:复现玩家真实节奏(每轮一张、间隔若干秒)下「第 4 次命中时命中前已有 3 层标记」的
 * 连续出牌判定。`lpchain` 是**同一 tick 连打**(同一出牌轮内多次确认),覆盖不到这条路径 ——
 * 两者在「出牌轮是否已归零」上完全不同,而补记入口正是以「本轮仍存活(`getPlayCount > 0`)」为前提。
 */
function doLpReset(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    if (lpState == null || lpState.dummy == null) { send(ctx, "AP_" + tag + "_ERR:no_dummy"); return 1; }
    var d = lpState.dummy;
    resetEffectCardCycle(p);
    var set = lpSetHand(p, lpCardId());
    lpState.tag = tag;
    lpState.hpBefore = rghp(d);
    send(ctx, "AP_" + tag + "_RESET:set=" + set + ":" + lpReadout(p, d, "-"));
    return 1;
}

/** 预置本周期活体书页出牌数加成(封顶 9 用例) */
function doLpCredit(ctx, tag, valueText) {
    var p = ctx.source.getPlayerOrException();
    var v = parseInt(valueText, 10); if (isNaN(v) || v < 0) v = 0;
    var err = "";
    try { ModAttachments.setLivingPageCycleBonus(p, v); } catch (e) { err = exText(e); }
    send(ctx, "AP_" + tag + "_CREDIT:set=" + v + ":credit=" + lpCredit(p)
        + ":plays=" + lpPlays(p) + ":max=" + lpMaxPlays(p) + (err ? ":err=" + err : ""));
    return 1;
}

/**
 * 同一靶上**连续出牌** hits 次(端到端验证标记累积与「首次 ≥3 层才补记」)。
 * 每次出牌前把出牌轮与「已使用伤害效果牌」标记恢复干净(脚手架;生产路径由 1:00 效果与冷却控制节奏),
 * 并直接调用生产侧的「手持即选择」入口 `BaseEffectCardItem#tickHeldSelector`(省去等一个 tick 的自动开局)。
 *
 * <p>牌进主手走 **API**(`lpSetHand`):用 `runCmd("item replace ...")` 时改动到下一 tick 才生效,
 * 而本函数在同一条命令里连做 hits 次 ⇒ 手牌永远读成空、`tickHeldSelector` 静默不开局(2026-09-19 实测)。
 * `ok` 只统计**确认后卡牌真的被消耗**的次数(确认失败时卡牌留在手里 ⇒ 记入 `miss`)。
 */
function doLpChain(ctx, tag, hitsText, distText) {
    var p = ctx.source.getPlayerOrException();
    var hits = 4, dist = 6;
    var ph = parseInt(hitsText, 10); if (!isNaN(ph) && ph > 0) hits = ph;
    var pd = parseInt(distText, 10); if (!isNaN(pd) && pd > 0) dist = pd;
    lpBaseline(ctx, p);
    // 与 lpprep 统一用蜘蛛(非亡灵不烧,最大生命 16)
    var d = spawnDummy(p, "minecraft:spider", dist);
    if (d == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed:spider"); return 1; }
    try { d.setHealth(d.getMaxHealth()); } catch (e1) { /* 忽略 */ }
    try { d.setNoAi(true); } catch (e2) { /* 忽略 */ }
    var ok = 0, miss = 0, errs = "";
    for (var i = 0; i < hits; i++) {
        try {
            resetEffectCardCycle(p);
            try { clearExtraPlayEffects(p); } catch (e3) { /* 忽略 */ }
            lpSetHand(p, lpCardId());
            LivingPageBaseCardClass.tickHeldSelector(p);
            TargetSelectionManagerClass.confirm(p,
                TargetSelectionManagerClass.sessionTokenForTests(p), d.getId());
            if (lpCardCount(p) === 0) ok = ok + 1; else miss = miss + 1;
        } catch (e4) { miss = miss + 1; errs = errs + "[" + i + "]" + exText(e4) + " "; }
    }
    lpState = { tag: tag, player: p, dummy: d, hpBefore: rghp(d) };
    send(ctx, "AP_" + tag + "_CHAIN:hits=" + hits + ":ok=" + ok + ":miss=" + miss
        + ":" + lpReadout(p, d, "-")
        + (errs !== "" ? ":err=" + errs : ""));
    return 1;
}

/** 收尾:取消会话 + 清靶/拆墙 + 主手清空 + 周期归零 */
function doLpClean(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var err = "";
    try { TargetSelectionManagerClass.cancel(p, lpToken(p)); } catch (e) { err = exText(e); }
    var discarded = 0;
    if (lpState != null && lpState.dummy != null) {
        try { lpState.dummy.discard(); discarded = 1; } catch (e2) { err = err + " discard:" + exText(e2); }
    }
    var unbuilt = 0;
    if (lpState != null && lpState.wall != null) {
        var w = lpState.wall;
        // 拆墙同样走 API(立即生效),`/fill ... air` 在下一 tick 才生效会污染后续用例的方块读数
        unbuilt = lpFillBox(p, w[0], w[1], w[2], w[3], w[4], w[5], "minecraft:air");
    }
    lpState = null;
    resetEffectCardCycle(p);
    lpSetHand(p, null);
    runCmd(ctx, "effect clear @s");
    try { clearExtraPlayEffects(p); } catch (e3) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_CLEAN:discarded=" + discarded + ":unbuilt=" + unbuilt + ":sel=" + lpSel(p)
        + ":credit=" + lpCredit(p) + ":plays=" + lpPlays(p) + ":cards=" + lpCardCount(p)
        + (err !== "" ? ":err=" + err : ""));
    return 1;
}

/** 预置「调查员已用页数」rin_pages(测试专用改写入口;命中伤害口径用例用它把加成归零,避免依赖历史出牌数) */
function doLpRin(ctx, tag, valueText) {
    var p = ctx.source.getPlayerOrException();
    var v = parseInt(valueText, 10); if (isNaN(v) || v < 0) v = 0;
    var err = "";
    var read = "err";
    try { ModAttachments.setRinPages(p, v); read = "" + ModAttachments.getRinPages(p); }
    catch (e) { err = exText(e); }
    send(ctx, "AP_" + tag + "_RIN:set=" + v + ":rin=" + read + (err ? ":err=" + err : ""));
    return 1;
}

/**
 * 魔法箭袋读数(只读;2026-09-19 验证「箭袋 × 活体书页」)。
 *
 * <p>字段：`equipped` = 是否佩戴箭袋筹码(`MagicQuiverChipItem#isEquipped`)；
 * `tracking` = `MAGIC_QUIVER_TRACKING` 是否已武装(使用效果牌时置位、触发时清除)；
 * `cd` = 距 `MAGIC_QUIVER_COOLDOWN_END` 的**剩余 tick**(0 = 可触发)；
 * `first` = 记录的第一张效果牌类型(触发时按它返还；空串 = 未记录)；
 * `mark` = 当前靶(`lpprep` 摆的那只)身上的标记层数。
 *
 * <p>为什么需要它：箭袋的**触发**在伤害事件里一次性完成(标记 +1、返还卡牌、进入冷却、清追踪)，
 * 只看靶子层数无法区分「箭袋触发」与「书页自己那一层」；`tracking`/`cd`/`first`/`cards` 四条合起来
 * 才能把「已武装但未触发」「已触发」与「被冷却挡住」三种状态分开。
 */
function doQuiverRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var equipped = "err", tracking = "err", cd = "err", first = "err";
    try { equipped = MagicQuiverChipItemClass.isEquipped(p) ? 1 : 0; } catch (e1) { equipped = "err:" + exText(e1); }
    try { tracking = ModAttachments.getMagicQuiverTracking(p) ? 1 : 0; } catch (e2) { tracking = "err:" + exText(e2); }
    try { cd = Math.max(0, ModAttachments.getMagicQuiverCooldownEnd(p) - nowTick(p)); } catch (e3) { cd = "err:" + exText(e3); }
    try { first = "" + ModAttachments.getMagicQuiverFirstCard(p); } catch (e4) { first = "err:" + exText(e4); }
    var d = lpState == null ? null : lpState.dummy;
    send(ctx, "AP_" + tag + "_QUIVER:equipped=" + equipped + ":tracking=" + tracking
        + ":cd=" + cd + ":first=" + first + ":mark=" + (d == null ? "-" : lpMark(d))
        + ":cards=" + lpCardCount(p));
    return 1;
}
/**
 * 充能冷却读数(只读)。2026-09-25 改口径:拥有充能时把**基础值**封顶 ——
 * 立牌主动至多 160 秒(sign=3200 tick,基础 3600)、效果牌至多 20 秒(card=400,基础 600);
 * 无充能时返回基础值(3600 / 600);佩戴诡异骰子再减半(有充能时 sign=1600)。
 * capSign / capCard 直接读共享库常量,避免把口径写死在用例里。
 */
function doChargeCd(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var Charge = Java.loadClass("com.merlinkitsune.astral_dice.item.ChargeManager");
    var Weird = Java.loadClass("com.merlinkitsune.astral_dice.event.WeirdDiceHandler");
    var GC = Java.loadClass("com.merlinkitsune.starenginelib.component.GameplayConstants");
    var now = nowTick(p);
    var cardBase = GC.EFFECT_CARD_COOLDOWN_SECONDS * 20;
    var cardEnd = ModAttachments.getEffectCardCooldownEnd(p);
    var cardEndSame = (chargeLastCardEnd >= 0 && (chargeLastCardEnd - cardEnd) === 0) ? 1 : 0;
    chargeLastCardEnd = cardEnd;
    var signEnd = ModAttachments.getSignActiveCooldownEnd(p);
    send(ctx, "AP_" + tag + "_CD:stacks=" + Charge.getStacks(p)
        + ":has=" + (Charge.hasCharge(p) ? 1 : 0)
        + ":weird=" + (Weird.hasWeirdDice(p) ? 1 : 0)
        + ":signBase=" + GC.SIGN_ACTIVE_COOLDOWN_TICKS
        + ":sign=" + Weird.signCooldownTicks(p)
        + ":card=" + Charge.effectCardCooldownTicks(p, cardBase)
        + ":capSign=" + GC.CHARGE_SIGN_COOLDOWN_CAP_SECONDS
        + ":capCard=" + GC.CHARGE_EFFECT_CARD_COOLDOWN_CAP_SECONDS
        + ":signEnd=" + signEnd
        + ":signMax=" + ModAttachments.getSignActiveMaxCooldown(p)
        + ":cardEnd=" + cardEnd
        + ":cardEndSame=" + cardEndSame
        + ":cardRemain=" + Math.max(0, cardEnd - now));
    return 1;
}

/** 设置充能层数(0 = 清空),随后打印同一份冷却读数(同一 tag) */
function doChargeSet(ctx, tag, n) {
    var p = ctx.source.getPlayerOrException();
    var Charge = Java.loadClass("com.merlinkitsune.astral_dice.item.ChargeManager");
    Charge.removeAll(p);
    if (n > 0) {
        Charge.addStacks(p, n);
    }
    return doChargeCd(ctx, tag);
}

// ════════════════════════════════════════════════════════════════════════════
// 教主立牌(teru)主动「降神」+ 被动「狐光」的游戏内取证(2026-09-27)
//   被测语义(docs/features/teru-sign-spec.md):
//     ① 主动只能指向**其它玩家**:不可自选、不可指向生物;
//     ② 施法瞬间快照:目标攻击力/防御力 ×50%(floor)加给施法者;狐光攻击基数
//        B = ⌊施法者攻击力⌋ + ⌊目标攻击力×50%⌋;主动施放 +3 层狐光;
//     ③ 降神持续到**下一次骰神赐福结束**(施加时目标已在赐福 ⇒ 跳过这一次结束,skip=1);
//     ④ 降神目标每攻击一个**新目标** ⇒ 消耗 1 层狐光 + 追加 (B + 消耗后的剩余层数) 攻击力;
//        同一目标只结算一次;层数为 0 ⇒ 不追加、不消耗;
//     ⑤ 两条防刷守卫:丢弃→捡起不计层;同一批牌「插入→卸除→再插入」不计层(玩家侧历史水位);
//     ⑥ 计层三点:合成攻击牌 / 模组发牌漏斗成功入包 / 装备攻击牌(受水位去重),掉落分支不计;
//     ⑦ 施法者侧加成是**每 tick 派生**:链接失效(目标效果结束/死亡/登出)⇒ 同 tick 归零;
//     ⑧ 显示/快照路径以 ctx.target == attacker 复用同一套攻击修饰器链 ⇒ 自目标防护必须生效
//        (打开卡牌栏看攻击力 / 施法瞬间快照**不得**消耗狐光)。
//   命令(读数行一律 AP_<tag>_ 前缀;除基线与脚手架归零外全部只读):
//     /astralprobe teruprep <tag>                        基线:装教主立牌+骰子+铁剑,清层数/水位/记录/效果
//     /astralprobe teruread <tag> <phase>                只读全量读数
//     /astralprobe terureg <tag>                         注册与冻结数值读数(物品/标签/效果/常量)
//     /astralprobe terucast <tag>                        主动门控与目标校验(自身/生物必须被拒)
//     /astralprobe terubot <tag> <name>                  只读:真实玩家(Carpet `/player` bot)是否在线 + 身份读数
//     /astralprobe teruequipbot <tag> <name>             给真实玩家(bot)装骰子(curios dice 槽;骰战链硬前提)
//     /astralprobe terublessreal <tag> <give|clear> <name>  给/撤真实玩家(bot)的骰神赐福(骰战链硬前提)
//     /astralprobe terucastreal <tag> <name>             真实施法指向**真实在线玩家**(Carpet bot):快照 + 双侧读数
//     /astralprobe terureadreal <tag> <phase> <name>     跨 tick 读双侧(链接存续 / 目标离线后的同 tick 自愈)
//     /astralprobe teruendreal <tag> <name>              endDescent(真实目标)并立刻读双侧归零
//     /astralprobe terulinkreal <tag> <name> <atk> <def> <base> <skip> <blessed> <layers> <wm>
//                                                        目标段:目标侧七键写在**真实玩家(bot)**身上、施法者侧给镜像/指针
//     /astralprobe terubless <tag> <give|clear>          骰神赐福 施加/移除(驱动下降沿)
//     /astralprobe terufx <tag> <name>                   外力移除降神效果实例(自检路径;`-` = 施法者自己)
//     /astralprobe terudie <tag> <name>                  死亡清场钩子读数(死亡边界;`-` = 施法者自己)
//     /astralprobe teruguard <tag>                       自目标防护:显示/快照路径不得消耗狐光
//     /astralprobe terudummy <tag> <d1|d2> <type> <dist> <hp>   摆一只高血量靶(句柄跨命令复用)
//     /astralprobe teruhitreal <tag> <d1|d2> <times> <name>   真实近战,**攻击者 = 真实玩家(bot)**(骰战链)
//     /astralprobe terucard <tag> <attack|defense|other|attack_drop> <n>   发牌漏斗计层(含掉落不计)
//     /astralprobe terudrop <tag>                        守卫①:地面攻击牌(拾取不计层)
//     /astralprobe teruequip <tag> <first|unequip|again> 守卫②:真实卡牌栏 插入/卸除/再插入
//     /astralprobe teruclear <tag>                       收尾:清效果/记录/饰品槽/主手
//   ✅ **2026-09-27 Carpet 玩家 bot 结束了「单人脚手架」时代**:`/player <name> spawn` 造出的是
//      **真 ServerPlayer**(在玩家列表里、参与 tick、可被选为目标、死亡走 disconnect) ⇒
//      「另一名玩家」不再需要 FakePlayer 或「自身当目标」的替身:
//        · 施法快照/双侧派生值/跨 tick 链接存续 → `terucastreal` / `terureadreal` / `teruendreal`;
//        · 降神目标侧状态机(下降沿 / 效果自检 / 死亡清场)→ `terulinkreal` / `terufx` / `terudie`;
//        · 狐光「新目标」消耗由被指定者真实近战触发 → `teruhitreal`(攻击者 = bot);
//        · 「生效中不可重复施放」第二道闸门 → `terugate <tag> <name>`。
//      —— C 批(2026-09-28)据此**删除**已被取代的单人专用命令:`terucastfake`(FakePlayer 真实施法)、
//      `teruhit`(攻击者只能是施法者自身)、`terulink`(自身同时当施法者与目标)。
//      用例:`cases/BOT-2P-<版本>.json`、`cases/TERU-SIGN-<版本>.json`、`cases/TERU-EXTRA-ATTACK-<版本>.json`。
//   ⚠️ 参数里的 type 是 StringArg.string():用例里必须加引号。
//   ⚠️ 改探针后必须**冷启动**(stop → launch):/kubejs reload server-scripts 不会重绑已注册命令。
// ════════════════════════════════════════════════════════════════════════════

function teruLoadCls(name) {
    try { return Java.loadClass(name); } catch (e) { return null; }
}

var TeruSignItemClass = teruLoadCls("com.merlinkitsune.astral_dice.item.sign.TeruSignItem");
var TeruDescentEffectClass = teruLoadCls("com.merlinkitsune.astral_dice.effect.TeruDescentEffect");
var HuguangEffectClass = teruLoadCls("com.merlinkitsune.astral_dice.effect.HuguangEffect");
var TeruDiceCombatModifiersClass = teruLoadCls("com.merlinkitsune.astral_dice.combat.DiceCombatModifiers");
var TeruCardInventoryMenuClass = teruLoadCls("com.merlinkitsune.astral_dice.screen.CardInventoryMenu");
var TeruClosePacketClass = teruLoadCls("net.minecraft.network.protocol.game.ClientboundContainerClosePacket");
var TeruVitaminPillChipItemClass = teruLoadCls("com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem");
var TeruSelectionRegistryClass = teruLoadCls("com.merlinkitsune.starenginelib.target.TargetSelectionRegistry");
var TeruTargetTypeClass = teruLoadCls("com.merlinkitsune.starenginelib.target.TargetType");
var OptionalClass = Java.loadClass("java.util.Optional");

var DESC_TERU_DESCENT = "effect.astral_dice.teru_descent";
var DESC_TERU_HUGUANG = "effect.astral_dice.teru_huguang";
var TERU_SIGN_ID = "astral_dice:teru_sign";
var TERU_DICE_ID = "astral_dice:dice";
var TERU_ATTACK_CARD_ID = "astral_dice:attack_card_medium";
var TERU_DEFENSE_CARD_ID = "astral_dice:defense_card_medium";
/** 降神效果的外部移除通道键名(spec §2 冻结值) */
var TERU_DEF_ARMOR_KEY = "teru_descent_def";

/** 骰神赐福效果(1.21.1:ModEffects 常量本身就是 Holder,可直接用于 MobEffectInstance 构造) */
function teruBlessing() { return ModEffects.DICE_BLESSING; }

/** 探针状态(跨命令复用;teruclear 置空)——d1/d2 = terudummy 摆下的高血量靶句柄 */
var teruState = null;

function teruInt(text, dflt) {
    var v = parseInt("" + text, 10);
    return (isNaN(v) || v === undefined) ? dflt : v;
}

function teruR1(x) { return Math.round(x * 10) / 10; }

/** 逗号分隔集合的元素个数("" = 0) */
function teruSetSize(set) {
    if (set == null || set === "") return 0;
    var parts = ("" + set).split(",");
    var n = 0;
    for (var i = 0; i < parts.length; i++) { if (parts[i] !== "") n = n + 1; }
    return n;
}

/** 主背包(0..35,与卡牌栏菜单的隐藏玩家槽同序)**指定物品**的总数 */
function teruInvCount(p, itemId) {
    var n = 0;
    try {
        var inv = p.getInventory();
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty() && itemIdOf(st) === itemId) n += st.getCount();
        }
    } catch (e) { return -1; }
    return n;
}

/** 主背包(0..35)里指定物品**首个**槽位下标(-1 = 没有) */
function teruInvIndex(p, itemId) {
    try {
        var inv = p.getInventory();
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty() && itemIdOf(st) === itemId) return i;
        }
    } catch (e) { return -1; }
    return -1;
}

/** 玩家 8 格内指定物品的地面掉落物数量(掉落分支的硬证据) */
function teruGroundCount(p, itemId, radius) {
    var n = 0;
    try {
        var list = p.level.getEntitiesOfClass(ItemEntityClass,
            AABBClass.ofSize(p.position(), radius, radius, radius));
        for (var i = 0; i < list.size(); i++) {
            var st = null;
            try { st = list.get(i).getItem(); } catch (e1) { st = null; }
            if (st != null && !st.isEmpty() && itemIdOf(st) === itemId) n = n + 1;
        }
    } catch (e) { return -1; }
    return n;
}

/** 效果实例的「放大等级/剩余tick」(无该效果 = "-") */
function teruFx(p, descId) { return effectAmpDur(findEffect(p, descId)); }

function teruFxOn(p, descId) { return findEffect(p, descId) == null ? 0 : 1; }

/**
 * 施法者加成折算出来的护甲修饰器(按 id 精确读;缺失 = 0)。
 * ⚠️ 两线 id 形态不同:1.21.1 = `ResourceLocation("astral_dice:teru_descent_def")`,
 *    1.20.1 = `UUID.nameUUIDFromBytes("astral_dice:teru_descent_def")`;两者都试,与
 *    本文件既有的 `mod.amount()` / `mod.getAmount()` 容错口径一致。
 */
function teruArmorModifier(p, attr) {
    var mod = null;
    try { mod = attr.getModifier(ResourceLocation.parse("astral_dice:" + TERU_DEF_ARMOR_KEY)); } catch (e1) { mod = null; }
    if (mod == null) {
        try {
            var UUIDClass = Java.loadClass("java.util.UUID");
            var CS = Java.loadClass("java.nio.charset.StandardCharsets");
            mod = attr.getModifier(UUIDClass.nameUUIDFromBytes(
                ("astral_dice:" + TERU_DEF_ARMOR_KEY).getBytes(CS.UTF_8)));
        } catch (e2) { mod = null; }
    }
    // 兜底:遍历修饰器清单按「id / 名字」串匹配(1.20.1 的 AttributeModifier 是 getName(),
    // 1.21.1 是 record 的 id();两条线上 `getModifier(UUID)` 的可用性并不一致 —— 1.20.1 实测取不到)
    if (mod == null) {
        try {
            var list = attr.getModifiers();
            var it = list.iterator();
            while (it.hasNext()) {
                var m2 = it.next();
                var id2 = "";
                try { id2 = "" + m2.id(); } catch (ea) {
                    try { id2 = "" + m2.getName(); } catch (eb) { id2 = ""; }
                }
                if (id2 === "astral_dice:" + TERU_DEF_ARMOR_KEY) { mod = m2; break; }
            }
        } catch (e5) { /* 忽略 */ }
    }
    if (mod == null) return 0;
    var amt = -1;
    try { amt = mod.amount(); } catch (e3) {
        try { amt = mod.getAmount(); } catch (e4) { return -1; }
    }
    return Math.round(amt * 100) / 100;
}

/** 全量机器读数(单行;字段顺序固定,用例按子串断言) */
function teruStateRead(p) {
    var caster = -1, target = -1, wm = "<err>";
    try { caster = ModAttachments.getTeruDescentCaster(p).isPresent() ? 1 : 0; } catch (e1) { caster = -2; }
    try { target = ModAttachments.getTeruDescentTarget(p).isPresent() ? 1 : 0; } catch (e2) { target = -2; }
    try { wm = ModAttachments.getTeruEquipWatermark(p); } catch (e3) { wm = "<err>"; }
    var armorAttr = 0, armorMod = -1;
    try {
        var Attrs = Java.loadClass("net.minecraft.world.entity.ai.attributes.Attributes");
        var a = p.getAttribute(Attrs.ARMOR);
        if (a != null) { armorAttr = Math.round(a.getValue() * 100) / 100; armorMod = teruArmorModifier(p, a); }
    } catch (e4) { /* 忽略 */ }
    return "layers=" + TeruSignItemClass.getLayers(p)
        + ":wm=" + (wm === "" ? "-" : wm)
        + ":caster=" + caster + ":target=" + target
        + ":cache=" + ModAttachments.getTeruAtkBonusCache(p)
        + ":atkbonus=" + ModAttachments.getTeruDescentAtkBonus(p)
        + ":defbonus=" + ModAttachments.getTeruDescentDefBonus(p)
        + ":base=" + ModAttachments.getTeruDescentAttackBase(p)
        + ":skip=" + ModAttachments.getTeruDescentSkipCycles(p)
        + ":prev=" + (ModAttachments.isTeruPrevBlessing(p) ? 1 : 0)
        + ":newt=" + teruSetSize(ModAttachments.getTeruDescentNewTargets(p))
        + ":bless=" + (p.hasEffect(teruBlessing()) ? 1 : 0)
        + ":fx_d=" + teruFx(p, DESC_TERU_DESCENT)
        + ":fx_d_on=" + teruFxOn(p, DESC_TERU_DESCENT)
        + ":fx_h=" + teruFx(p, DESC_TERU_HUGUANG)
        + ":fx_h_on=" + teruFxOn(p, DESC_TERU_HUGUANG)
        + ":ap=" + TeruDiceCombatModifiersClass.attackPowerOf(p)
        + ":dp=" + TeruDiceCombatModifiersClass.defensePowerOf(p)
        + ":armor=" + armorAttr + ":armor_mod=" + armorMod
        + ":sign=" + (TeruSignItemClass.isEquipped(p) ? 1 : 0)
        + ":hand=" + itemIdOf(p.getMainHandItem())
        + ":inv_atk=" + teruInvCount(p, TERU_ATTACK_CARD_ID)
        + ":ground_atk=" + teruGroundCount(p, TERU_ATTACK_CARD_ID, 8)
        + ":cd=" + (signCooldownRemaining(p) > 0 ? 1 : 0);
}

/** 归零降神/狐光的全部附件与效果实例(基线 + 收尾共用;纯写、不做任何判定) */
function teruClearState(p) {
    try { TeruDescentEffectClass.remove(p); } catch (e1) { /* 忽略 */ }
    try { HuguangEffectClass.clear(p); } catch (e2) { /* 忽略 */ }
    try { ModEffectRemoval.remove(p, teruBlessing()); } catch (e3) { /* 忽略 */ }
    try { ModAttachments.setTeruDescentCaster(p, OptionalClass.empty()); } catch (e4) { /* 忽略 */ }
    try { ModAttachments.setTeruDescentTarget(p, OptionalClass.empty()); } catch (e5) { /* 忽略 */ }
    try { ModAttachments.setTeruDescentAtkBonus(p, 0); } catch (e6) { /* 忽略 */ }
    try { ModAttachments.setTeruDescentDefBonus(p, 0); } catch (e7) { /* 忽略 */ }
    try { ModAttachments.setTeruDescentAttackBase(p, 0); } catch (e8) { /* 忽略 */ }
    try { ModAttachments.setTeruDescentSkipCycles(p, 0); } catch (e9) { /* 忽略 */ }
    try { ModAttachments.setTeruPrevBlessing(p, false); } catch (e10) { /* 忽略 */ }
    try { ModAttachments.setTeruDescentNewTargets(p, ""); } catch (e11) { /* 忽略 */ }
    try { ModAttachments.setTeruAtkBonusCache(p, 0); } catch (e12) { /* 忽略 */ }
    try { TeruDiceCombatModifiersClass.setDefenseArmorBonus(p, TERU_DEF_ARMOR_KEY, 0); } catch (e13) { /* 忽略 */ }
    try { TeruSignItemClass.setLayers(p, 0); } catch (e14) { /* 忽略 */ }
}

/** 引导检查:teru 相关类在当前 jar 上是否可见(旧 jar / 未冷启动时给出可归因读数) */
function teruClassGate(ctx, tag) {
    if (TeruSignItemClass == null) { send(ctx, "AP_" + tag + "_ERR:no_class:TeruSignItem"); return false; }
    if (TeruDescentEffectClass == null) { send(ctx, "AP_" + tag + "_ERR:no_class:TeruDescentEffect"); return false; }
    if (HuguangEffectClass == null) { send(ctx, "AP_" + tag + "_ERR:no_class:HuguangEffect"); return false; }
    if (TeruDiceCombatModifiersClass == null) { send(ctx, "AP_" + tag + "_ERR:no_class:DiceCombatModifiers"); return false; }
    return true;
}

/**
 * 基线:装教主立牌 + 骰子 + 主手铁剑,清层数/水位/记录/效果/背包。
 * ⚠️ 背包与主手一律走 API(lpClearInventory / lpSetHand):同一条命令里用 runCmd 改背包要
 *    下一个 tick 才生效(本文件 lulu/lp 段已实测取证)。
 * ⚠️ 骰子是后续三条链路的硬前提:卡牌栏菜单(findEquippedDice 为空直接 return)、
 *    骰战攻击修饰器链(diceStack == null 直接 return)。
 */
function doTeruPrep(ctx, tag, wmText) {
    var p = ctx.source.getPlayerOrException();
    try { resetEffectCardCycle(p); } catch (e0) { /* 忽略 */ }
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e1) { /* 忽略 */ }
    try { clearCurioSlots(p, "stand"); } catch (e2) { /* 忽略 */ }
    try { clearCurioSlots(p, "dice"); } catch (e3) { /* 忽略 */ }
    try { clearCurioSlots(p, "chip"); } catch (e4) { /* 忽略 */ }
    try { lpClearInventory(p); } catch (e5) { /* 忽略 */ }
    teruClearState(p);
    // 装备水位是**玩家侧持久**守卫(生产语义:只升不降、跨死亡保留,故 teruClearState 刻意不碰它)。
    // 测试要可重复,故 `teruprep <tag> clear` 允许显式归零;不传则保持原值(跨用例的持久性用例需要它)。
    if (("" + wmText) === "clear") {
        try { ModAttachments.setTeruEquipWatermark(p, ""); } catch (e5b) { /* 忽略 */ }
    }
    var err = equipSign(p, TERU_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 1; }
    var err2 = putInSlot(p, "dice", new ItemStack(resolveItem(TERU_DICE_ID)), 0);
    if (err2 != null) { send(ctx, "AP_" + tag + "_ERR:" + err2); return 1; }
    lpSetHand(p, "minecraft:iron_sword");
    teruState = { d1: null, d2: null };
    send(ctx, "AP_" + tag + "_PREP:" + teruStateRead(p));
    return 1;
}

/** 只读全量读数 */
function doTeruRead(ctx, tag, phase) {
    var p = ctx.source.getPlayerOrException();
    send(ctx, "AP_" + tag + "_" + phase + ":" + teruStateRead(p));
    return 1;
}

/** 注册落地与冻结数值(描述 id/稀有度/堆叠/标签/效果 id/四个公开常量) */
function doTeruReg(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var item = resolveItem(TERU_SIGN_ID);
    var desc = "-", rarity = "-", stackMax = -1, stackMaxSrc = "stack";
    if (item != null) {
        try { desc = item.getDescriptionId(); } catch (e1) { desc = "?"; }
        // 稀有度:优先 ItemStack#getRarity(1.21.1 起),退到 Item#getRarity(ItemStack)
        try { rarity = "" + new ItemStack(item).getRarity(); } catch (e2) {
            try { rarity = "" + item.getRarity(new ItemStack(item)); } catch (e3) { rarity = "?"; }
        }
        try { stackMax = new ItemStack(item).getMaxStackSize(); } catch (e4) { stackMax = -2; }
    }
    // ⚠️ 标签判定**不得**走 `ItemStack#is(TagKey)`:ItemStack 有 5 个同元 `is(...)` 重载
    //    (TagKey/Item/Predicate/Holder/HolderSet),Rhino 无法从 JS 实参判出目标重载 ⇒
    //    InternalError: ambiguous(本文件 :1480 已实测取证)。改用 `Holder#tags()` 拿「所属标签集合」
    //    并按 `TagKey#location()` 做**字符串**比对(零重载、零歧义),同时避免构造 TagKey。
    var signsTag = -1, curiosTag = -1, tagErr = "";
    try {
        var holders = BuiltInRegistries.ITEM.wrapAsHolder(item);
        var arr = holders.tags().toArray();
        signsTag = 0; curiosTag = 0;
        for (var i = 0; i < arr.length; i++) {
            var loc = "" + arr[i].location();
            if (loc === "astral_dice:signs") signsTag = 1;
            if (loc === "curios:stand") curiosTag = 1;
        }
    } catch (e5) { signsTag = -2; curiosTag = -2; tagErr = exText(e5); }
    var fxD = "-", fxH = "-";
    try { fxD = ModEffects.TERU_DESCENT.get().getDescriptionId(); } catch (e6) { fxD = "?"; }
    try { fxH = ModEffects.TERU_HUGUANG.get().getDescriptionId(); } catch (e7) { fxH = "?"; }
    var actionId = "-", signId = "-", maxLayers = -1, onActive = -1, ratio = -1;
    try {
        actionId = TeruSignItemClass.ACTION_ID;
        signId = TeruSignItemClass.SIGN_ID;
        maxLayers = TeruSignItemClass.MAX_HUGUANG;
        onActive = TeruSignItemClass.HUGUANG_ON_ACTIVE;
        ratio = TeruSignItemClass.DESCENT_RATIO;
    } catch (e8) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_REG:item=" + itemIdOf(new ItemStack(item))
        + ":desc=" + desc + ":rarity=" + rarity + ":stack=" + stackMax
        + ":signs_tag=" + signsTag + ":curios_stand_tag=" + curiosTag
        + ":fx_d=" + fxD + ":fx_h=" + fxH
        + ":action=" + actionId + ":sign_id=" + signId
        + ":max_layers=" + maxLayers + ":on_active=" + onActive + ":ratio=" + ratio
        + ":sign_equipped=" + (TeruSignItemClass.isEquipped(p) ? 1 : 0)
        + (tagErr === "" ? "" : ":tag_err=" + tagErr));
    return 1;
}

/**
 * 主动门控与目标校验(不产生任何成功施法):
 *   ① 注册表里必须有 teru_descent 动作且声明 PLAYER;
 *   ② 库内 TargetType.PLAYER.matches:自身 = false、生物 = false;
 *   ③ 直接调用 castDescent:自身 = false、生物 = false(服务端二次校验);
 *   ④ 真实按键路径:按主动 ⇒ 只开会话(会话读数 + debug 行 action=teru_descent type=PLAYER
 *      allowSelf=false),随后 confirm(自身) 与 confirm(生物) 必须被 target_type_mismatch 拒;
 *   ⑤ 全程不得施加降神、不得进冷却、不得加层(读数取证)。
 */
function doTeruCast(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    try { resetEffectCardCycle(p); } catch (e0) { /* 忽略 */ }
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e1) { /* 忽略 */ }
    teruClearState(p);

    var regId = "-", regType = "-";
    try {
        var action = TeruSelectionRegistryClass.get(TeruSignItemClass.ACTION_ID);
        if (action != null) { regId = "" + action.id(); regType = "" + action.targetType(); }
    } catch (e2) { regId = "?ex=" + exText(e2); }

    var selfMatch = -1, mobMatch = -1;
    var spider = spawnDummy(p, "minecraft:spider", 3);
    try {
        if (TeruTargetTypeClass != null) {
            selfMatch = TeruTargetTypeClass.PLAYER.matches(p, p) ? 1 : 0;
            if (spider != null) mobMatch = TeruTargetTypeClass.PLAYER.matches(p, spider) ? 1 : 0;
        }
    } catch (e3) { selfMatch = -2; mobMatch = -2; }

    var selfCast = -1, mobCast = -1;
    try { selfCast = TeruSignItemClass.castDescent(p, p) ? 1 : 0; } catch (e4) { selfCast = -2; }
    try { if (spider != null) mobCast = TeruSignItemClass.castDescent(p, spider) ? 1 : 0; } catch (e5) { mobCast = -2; }
    if (spider != null) { try { spider.discard(); } catch (e6) { /* 忽略 */ } }

    // ④ 真实按键路径:会话开启 + 两次非法确认
    var session = 0, token = -1, selfConfirm = -1, mobConfirm = -1, confirmErr = "";
    try {
        BaseSignItemClass.performSkillForCurio(p);
        session = TargetSelectionManagerClass.isSelecting(p) ? 1 : 0;
        token = TargetSelectionManagerClass.sessionTokenForTests(p);
        TargetSelectionManagerClass.confirm(p, token, p.getId());
        selfConfirm = 0;   // 走到这里说明没抛异常;是否被拒由下方读数与 debug 行判定
        try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e7) { /* 忽略 */ }
    } catch (e8) { confirmErr = exText(e8); }

    var spider2 = spawnDummy(p, "minecraft:spider", 3);
    var session2 = 0, token2 = -1;
    try {
        BaseSignItemClass.performSkillForCurio(p);
        session2 = TargetSelectionManagerClass.isSelecting(p) ? 1 : 0;
        token2 = TargetSelectionManagerClass.sessionTokenForTests(p);
        if (spider2 != null) TargetSelectionManagerClass.confirm(p, token2, spider2.getId());
        mobConfirm = 0;
    } catch (e9) { confirmErr = confirmErr + "|mob:" + exText(e9); }
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e10) { /* 忽略 */ }
    if (spider2 != null) { try { spider2.discard(); } catch (e11) { /* 忽略 */ } }

    send(ctx, "AP_" + tag + "_CAST:reg=" + regId + ":reg_type=" + regType
        + ":self_match=" + selfMatch + ":mob_match=" + mobMatch
        + ":self_cast=" + selfCast + ":mob_cast=" + mobCast
        + ":session=" + session + ":session2=" + session2
        + ":token_seen=" + (token > 0 ? 1 : 0) + ":token2_seen=" + (token2 > 0 ? 1 : 0)
        + ":self_confirm=" + selfConfirm + ":mob_confirm=" + mobConfirm
        + (confirmErr === "" ? "" : ":confirm_err=" + confirmErr)
        + ":" + teruStateRead(p));
    return 1;
}

/**
 * 对 FakePlayer 走**真实施法**(castDescent):快照 50% / 攻击基数 / +3 层 / 施法者侧派生值。
 * 目标属性由本命令先钉死(攻击力 20、护甲 20、韧性 0)⇒ 期望值可由「施法前同口径读数」直接算。
 * ⚠️ 全部读数取在**施法同一次调用内**(FakePlayer 不在玩家列表 ⇒ 下一 tick 施法者侧派生值
 *    会被 tickCasterSide 视为链接失效而清零,属脚手架边界,不是产品行为);收尾走 endDescent。
 */
function teruOwner(ctx, tag, who, nameText) {
    var p = ctx.source.getPlayerOrException();
    if (nameText == null || ("" + nameText) === "" || ("" + nameText) === "-") {
        return { ok: true, p: p, who: who + "=self" };
    }
    var t = teruFindPlayer(ctx, nameText);
    if (t == null) { send(ctx, "AP_" + tag + "_ERR:no_player:" + nameText); return { ok: false, p: null, who: "" }; }
    return { ok: true, p: t, who: who + "=" + nameText };
}

/**
 * 目标段产物的**生成入口**(真实玩家侧):`confirm` 走真实按键+服务端确认链路,
 * `apply` 直接调用注册表动作(路由判据)。两者共用同一个 apply 实现体。
 * —— C 批(2026-09-28)之前这里是「把**自身**同时写成降神目标与施法者」的单人脚手架。
 *
 * <p>先清施法者与目标两侧(上一段的残留在**目标**身上,`teruClearState(施法者)` 清不掉它;
 * 不清会让 `prev` 仍为 1、跳过本段应有的下降沿),再按段参数写入。
 *
 * @param wm 水位字符串(如 `medium=1`);`-` = 保持原值
 */
/**
 * 解析**真实在线玩家**（Carpet 的 `/player` bot 也是真 ServerPlayer ⇒ 同一入口）。
 * 只用 PlayerList API；Rhino 下 `ServerPlayer#getUUID`/`getClass` 不可用（见本文件既有踩坑），
 * 故不碰它们。取不到返回 null，由调用方给出可归因读数（**不**静默当成「没人」）。
 */
function teruFindPlayer(ctx, nameText) {
    var p = ctx.source.getPlayerOrException();
    var want = "" + nameText;
    var list = null;
    try { list = p.level.getServer().getPlayerList(); } catch (e0) { list = null; }
    if (list == null) return null;
    try {
        var byName = list.getPlayerByName(want);
        if (byName != null) return byName;
    } catch (e1) { /* 退到遍历 */ }
    try {
        var all = list.getPlayers();
        for (var i = 0; i < all.size(); i++) {
            var q = all.get(i);
            var qn = "";
            try { qn = "" + q.getName().getString(); }
            catch (e2) { try { qn = "" + q.getGameProfile().getName(); } catch (e3) { qn = ""; } }
            if (qn === want) return q;
        }
    } catch (e4) { /* 忽略 */ }
    return null;
}

/**
 * 玩家身份读数（只读）：在线与否 / 名字 / 连接类型（Carpet bot?）/ 维度 / 坐标 / 血量 / 游戏模式
 * + **在线名单**（用于断言「2 人及以上」的现场）。
 * ⚠️ 判定 bot 用 `"" + target.connection`（toString 里含 `NetHandlerPlayServerFake`）——
 *    Rhino 下 `obj.getClass()` 不可用（既有踩坑），toString 不抛且是稳定判据。
 */
function teruPlayerRead(ctx, target, wanted) {
    var p = ctx.source.getPlayerOrException();
    var listSize = -1, names = "-";
    try {
        var all = p.level.getServer().getPlayerList().getPlayers();
        listSize = all.size();
        var acc = "";
        for (var i = 0; i < all.size(); i++) {
            var q = all.get(i);
            var qn = "";
            try { qn = "" + q.getName().getString(); } catch (e1) { qn = "?"; }
            acc = (i === 0) ? qn : (acc + "," + qn);
        }
        names = (acc === "") ? "-" : acc;
    } catch (e2) { names = "?"; }

    if (target == null) {
        return "found=0:want=" + wanted + ":list=" + listSize + ":names=" + names;
    }
    var uuidInfo = playerUuid(target);
    var conn = "";
    try { conn = "" + target.connection; } catch (e3) { conn = "<err>"; }
    var isBot = conn.indexOf("NetHandlerPlayServerFake") >= 0 ? 1 : 0;
    var health = -1, pos = "-", dim = "-", mode = "-", name = "";
    try { health = teruR1(target.getHealth()); } catch (e4) { health = -1; }
    try { pos = teruR1(target.getX()) + "," + teruR1(target.getY()) + "," + teruR1(target.getZ()); }
    catch (e5) { pos = "-"; }
    try { dim = "" + target.level.dimension().location(); }
    catch (e6) { try { dim = "" + target.level.dimension; } catch (e7) { dim = "-"; } }
    try { mode = "" + target.gameMode.getGameModeForPlayer(); } catch (e8) { mode = "-"; }
    try { name = "" + target.getName().getString(); } catch (e9) { name = wanted; }
    return "found=1:want=" + wanted + ":name=" + name + ":bot=" + isBot
        + ":conn=" + (isBot ? "fake" : "real") + ":uuid_src=" + uuidInfo.src
        + ":health=" + health + ":pos=" + pos + ":dim=" + dim + ":mode=" + mode
        + ":list=" + listSize + ":names=" + names;
}

/**
 * **目标侧**读数（只读）：降神写在「那个玩家身上」的值与派生值。
 * 单人环境的 FakePlayer 路径无法跨 tick（不在玩家列表），故这一组读数只有真实玩家/bot 才有意义。
 */
function teruTargetRead(t) {
    var out = "t_target=" + (ModAttachments.getTeruDescentTarget(t).isPresent() ? 1 : 0)
        + ":t_caster=" + (ModAttachments.getTeruDescentCaster(t).isPresent() ? 1 : 0);
    try { out += ":t_atkbonus=" + ModAttachments.getTeruDescentAtkBonus(t); } catch (e1) { out += ":t_atkbonus=?"; }
    try { out += ":t_defbonus=" + ModAttachments.getTeruDescentDefBonus(t); } catch (e2) { out += ":t_defbonus=?"; }
    try { out += ":t_base=" + ModAttachments.getTeruDescentAttackBase(t); } catch (e3) { out += ":t_base=?"; }
    try { out += ":t_skip=" + ModAttachments.getTeruDescentSkipCycles(t); } catch (e4) { out += ":t_skip=?"; }
    try { out += ":t_prev=" + (ModAttachments.isTeruPrevBlessing(t) ? 1 : 0); } catch (e5) { out += ":t_prev=?"; }
    try { out += ":t_newt=" + teruSetSize(ModAttachments.getTeruDescentNewTargets(t)); } catch (e6) { out += ":t_newt=?"; }
    try { out += ":t_fx_d=" + teruFx(t, DESC_TERU_DESCENT) + ":t_fx_d_on=" + teruFxOn(t, DESC_TERU_DESCENT); }
    catch (e7) { out += ":t_fx_d=?"; }
    try { out += ":t_fx_h_on=" + teruFxOn(t, DESC_TERU_HUGUANG); } catch (e8) { out += ":t_fx_h_on=?"; }
    try { out += ":t_bless=" + (t.hasEffect(teruBlessing()) ? 1 : 0); } catch (e9) { out += ":t_bless=?"; }
    try { out += ":t_ap=" + TeruDiceCombatModifiersClass.attackPowerOf(t); } catch (e10) { out += ":t_ap=?"; }
    try { out += ":t_dp=" + TeruDiceCombatModifiersClass.defensePowerOf(t); } catch (e11) { out += ":t_dp=?"; }
    try { out += ":t_armor=" + t.getArmorValue(); } catch (e12) { out += ":t_armor=?"; }
    try { out += ":t_health=" + teruR1(t.getHealth()); } catch (e13) { out += ":t_health=?"; }
    return out;
}

/** 只读：解析真实玩家（Carpet bot）并给出身份读数 —— 断言「bot 在线／已退出」「在线人数」 */
function doTeruBot(ctx, tag, nameText) {
    var t = teruFindPlayer(ctx, nameText);
    send(ctx, "AP_" + tag + "_BOT:" + teruPlayerRead(ctx, t, "" + nameText));
    return 1;
}

/**
 * 给指定真实玩家（bot）装骰子（curios 的 `dice` 槽）。
 * 骰战链（含狐光追加攻击）的三条硬前提：**主手近战武器**（用例用 `/item replace` 给）、
 * **骰神赐福**（`terublessreal`）、**骰子**（本命令）—— 缺骰子时 `DiceCombatEvents` 在
 * `diceStack == null` 处直接 return，额外攻击永远不会被消费（读数为「没反应」的假阴性）。
 */
function doTeruEquipBot(ctx, tag, nameText) {
    var t = teruFindPlayer(ctx, nameText);
    if (t == null) { send(ctx, "AP_" + tag + "_ERR:no_player:" + nameText); return 1; }
    var dice = resolveItem(TERU_DICE_ID);
    if (dice == null) { send(ctx, "AP_" + tag + "_ERR:no_item:" + TERU_DICE_ID); return 1; }
    var err = "";
    try { err = putInSlot(t, "dice", new ItemStack(dice), 0); } catch (e1) { err = exText(e1); }
    if (err == null) err = "";
    var hand = "-";
    try { hand = itemIdOf(t.getMainHandItem()); } catch (e2) { hand = "?"; }
    send(ctx, "AP_" + tag + "_EQUIPBOT:name=" + nameText + ":dice_ok=" + (err === "" ? 1 : 0)
        + ":hand=" + hand + (err === "" ? "" : ":err=" + err));
    return 1;
}

/**
 * 给/撤指定真实玩家（bot）的**骰神赐福**（骰战链的硬前提之一）。
 * `give` 用 6000 tick 的长时长：骰战链在攻击瞬间还会自动补一次**短时长**赐福
 * （`DiceCombatEvents` 的 `!hasEffect` 分支），长时长可避免用例中途出现下降沿把降神提前收敛。
 */
function doTeruBlessReal(ctx, tag, mode, nameText) {
    var t = teruFindPlayer(ctx, nameText);
    if (t == null) { send(ctx, "AP_" + tag + "_ERR:no_player:" + nameText); return 1; }
    var how = "clear";
    if (("" + mode) === "give") {
        try { t.addEffect(new MobEffectInstanceClass(teruBlessing(), 6000, 0, false, false, true)); how = "give"; }
        catch (e1) { how = "give_ex:" + exText(e1); }
    } else {
        try { ModEffectRemoval.remove(t, teruBlessing()); } catch (e2) { how = "clear_ex:" + exText(e2); }
    }
    send(ctx, "AP_" + tag + "_BLESSREAL:" + how + ":" + teruTargetRead(t));
    return 1;
}

/**
 * **真实双人**施法：`castDescent(自身 → 指定真实玩家 / Carpet bot)`。
 * 与 terucastfake 的关键差别：目标在**玩家列表**里 ⇒ 施法者侧指针的 tick 解析成功、降神**跨 tick 存续**
 * ⇒ 本命令**不**在末尾 endDescent（收尾交给 teruendreal，或由用例杀掉 bot 触发「链接目标离线」自愈路径）。
 */
function doTeruCastReal(ctx, tag, nameText) {
    var p = ctx.source.getPlayerOrException();
    try { resetEffectCardCycle(p); } catch (e0) { /* 忽略 */ }
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e1) { /* 忽略 */ }
    var t = teruFindPlayer(ctx, nameText);
    if (t == null) { send(ctx, "AP_" + tag + "_ERR:no_player:" + nameText); return 1; }
    teruClearState(p);
    teruClearState(t);

    var note = "";
    try {
        var Attrs = Java.loadClass("net.minecraft.world.entity.ai.attributes.Attributes");
        var atk = t.getAttribute(Attrs.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(20.0);
        var arm = t.getAttribute(Attrs.ARMOR);
        if (arm != null) arm.setBaseValue(20.0);
        var tou = t.getAttribute(Attrs.ARMOR_TOUGHNESS);
        if (tou != null) tou.setBaseValue(0.0);
    } catch (e2) { note = note + "|attr:" + exText(e2); }

    var aT = -1, dT = -1, aC = -1, armorBefore = -1;
    try { aT = TeruDiceCombatModifiersClass.attackPowerOf(t); } catch (e3) { aT = -1; }
    try { dT = TeruDiceCombatModifiersClass.defensePowerOf(t); } catch (e4) { dT = -1; }
    try { aC = TeruDiceCombatModifiersClass.attackPowerOf(p); } catch (e5) { aC = -1; }
    try { armorBefore = p.getArmorValue(); } catch (e6) { armorBefore = -1; }

    var ok = -1;
    try { ok = TeruSignItemClass.castDescent(p, t) ? 1 : 0; }
    catch (e7) { ok = -2; note = note + "|cast:" + exText(e7); }

    var bonusAtk = 0, bonusDef = 0, base = 0, layersAfter = 0, cache = 0, armorAfter = 0, newt = -1;
    try { bonusAtk = ModAttachments.getTeruDescentAtkBonus(t); } catch (e8) { bonusAtk = -1; }
    try { bonusDef = ModAttachments.getTeruDescentDefBonus(t); } catch (e9) { bonusDef = -1; }
    try { base = ModAttachments.getTeruDescentAttackBase(t); } catch (e10) { base = -1; }
    try { layersAfter = TeruSignItemClass.getLayers(p); } catch (e11) { layersAfter = -1; }
    try { cache = ModAttachments.getTeruAtkBonusCache(p); } catch (e12) { cache = -1; }
    try { armorAfter = p.getArmorValue(); } catch (e13) { armorAfter = -1; }
    try { newt = teruSetSize(ModAttachments.getTeruDescentNewTargets(t)); } catch (e14) { newt = -1; }

    var expAtk = Math.floor(aT * 0.5), expDef = Math.floor(dT * 0.5);
    var expBase = Math.max(0, aC) + expAtk;
    send(ctx, "AP_" + tag + "_CASTREAL:cast=" + ok + ":target=" + nameText
        + ":A_t=" + aT + ":D_t=" + dT + ":A_c=" + aC
        + ":bonus_atk=" + bonusAtk + ":bonus_def=" + bonusDef + ":base=" + base
        + ":exp_atk=" + expAtk + ":exp_def=" + expDef + ":exp_base=" + expBase
        + ":atk_ok=" + (bonusAtk === expAtk ? 1 : 0)
        + ":def_ok=" + (bonusDef === expDef ? 1 : 0)
        + ":base_ok=" + (base === expBase ? 1 : 0)
        + ":layers=" + layersAfter + ":layers_ok=" + (layersAfter === 3 ? 1 : 0)
        + ":cache=" + cache + ":cache_ok=" + (cache === expAtk ? 1 : 0)
        + ":armor=" + armorBefore + ">" + armorAfter
        + ":armor_ok=" + ((armorAfter - armorBefore) === (expDef * 2) ? 1 : 0)
        + ":newt=" + newt
        + (note === "" ? "" : ":note=" + note));
    send(ctx, "AP_" + tag + "_CASTREAL_T:" + teruTargetRead(t));
    send(ctx, "AP_" + tag + "_CASTREAL_BOT:" + teruPlayerRead(ctx, t, "" + nameText));
    return 1;
}

/**
 * 跨 tick 读双侧（只读）：施法者侧（链接是否存续 ⇒ cache / 护甲折算还在不在）+ 目标侧。
 * **目标离线（bot 被 kill/登出）后 tickCasterSide 必须同 tick 自愈归零** —— 这一条正是单人环境
 * （FakePlayer 不在玩家列表）无法覆盖的。
 */
function doTeruReadReal(ctx, tag, phase, nameText) {
    var p = ctx.source.getPlayerOrException();
    var t = teruFindPlayer(ctx, nameText);
    send(ctx, "AP_" + tag + "_" + phase + ":" + teruStateRead(p));
    if (t == null) {
        send(ctx, "AP_" + tag + "_" + phase + "_T:found=0:want=" + nameText);
    } else {
        send(ctx, "AP_" + tag + "_" + phase + "_T:found=1:" + teruTargetRead(t));
        send(ctx, "AP_" + tag + "_" + phase + "_BOT:" + teruPlayerRead(ctx, t, "" + nameText));
    }
    return 1;
}

/** 收尾：`endDescent(真实目标)` 并立刻读双侧归零（施法者在线 ⇒ 镜像缓存/护甲折算同 tick 清零） */
function doTeruEndReal(ctx, tag, nameText) {
    var p = ctx.source.getPlayerOrException();
    var t = teruFindPlayer(ctx, nameText);
    if (t == null) { send(ctx, "AP_" + tag + "_ERR:no_player:" + nameText); return 1; }
    var err = "";
    try { TeruSignItemClass.endDescent(t); } catch (e1) { err = exText(e1); }
    send(ctx, "AP_" + tag + "_ENDREAL:called=" + (err === "" ? 1 : 0) + (err === "" ? "" : ":err=" + err));
    send(ctx, "AP_" + tag + "_ENDREAL_C:" + teruStateRead(p));
    send(ctx, "AP_" + tag + "_ENDREAL_T:" + teruTargetRead(t));
    try { teruClearState(p); } catch (e2) { /* 忽略 */ }
    try { teruClearState(t); } catch (e3) { /* 忽略 */ }
    return 1;
}

/**
 * **单人脚手架**:把「自身」写成降神目标(施法者 = 自身),驱动目标侧状态机与骰战追加攻击。
 * 逐项与 castDescent 的写入口径一一对应(快照值由参数直接给定,故本命令不验证施法算术)。
 * @param wm 水位字符串(如 `medium=1`);`-` = 保持原值
 */
function doTeruLinkReal(ctx, tag, nameText, atkText, defText, baseText, skipText, blessedText, layersText, wmText) {
    var p = ctx.source.getPlayerOrException();
    var t = teruFindPlayer(ctx, nameText);
    if (t == null) { send(ctx, "AP_" + tag + "_ERR:no_player:" + nameText); return 1; }
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e0) { /* 忽略 */ }
    // 收尾前先清**目标**再清施法者:施法者侧 tick 的「本次施法前是否已在赐福」判断读的是施法者
    // **自己**的 prev 标记,而该标记会被「指向该目标的链接仍生效」那几拍写成 1 ⇒ 不清会让本段的
    // 下降沿判据失真(实测:t_prev 已清为 0、施法者 prev 仍是 1)。
    teruClearState(t);
    teruClearState(p);
    teruClearState(t);
    var pInfo = playerUuid(p);
    if (!pInfo.ok) { send(ctx, "AP_" + tag + "_ERR:no_uuid:caster:" + pInfo.src); return 1; }
    var tInfo = playerUuid(t);
    if (!tInfo.ok) { send(ctx, "AP_" + tag + "_ERR:no_uuid:target:" + tInfo.src); return 1; }
    var uuid = pInfo.value, tUuid = tInfo.value;
    try {
        // 目标侧七键(= castDescent 写进 receiver 的那一组)
        ModAttachments.setTeruDescentCaster(t, OptionalClass.of(uuid));
        ModAttachments.setTeruDescentAtkBonus(t, teruInt(atkText, 0));
        ModAttachments.setTeruDescentDefBonus(t, teruInt(defText, 0));
        ModAttachments.setTeruDescentAttackBase(t, teruInt(baseText, 0));
        ModAttachments.setTeruDescentSkipCycles(t, teruInt(skipText, 0));
        ModAttachments.setTeruPrevBlessing(t, ("" + blessedText) === "1");
        ModAttachments.setTeruDescentNewTargets(t, "");
        // 施法者侧指针 + 镜像
        ModAttachments.setTeruDescentTarget(p, OptionalClass.of(tUuid));
        ModAttachments.setTeruAtkBonusCache(p, teruInt(atkText, 0));
        TeruDiceCombatModifiersClass.setDefenseArmorBonus(p, TERU_DEF_ARMOR_KEY, teruInt(defText, 0));
        TeruDescentEffectClass.apply(t);
        TeruSignItemClass.setLayers(p, teruInt(layersText, 0));
        if (wmText != null && wmText !== "-") ModAttachments.setTeruEquipWatermark(p, "" + wmText);
    } catch (e1) { send(ctx, "AP_" + tag + "_ERR:link_ex:" + exText(e1)); return 1; }
    if (("" + blessedText) === "1") {
        try { t.addEffect(new MobEffectInstanceClass(teruBlessing(), 6000, 0, false, false, true)); } catch (e2) { /* 忽略 */ }
    }
    send(ctx, "AP_" + tag + "_LINK:target=" + nameText + ":" + teruStateRead(p));
    send(ctx, "AP_" + tag + "_LINK_T:" + teruTargetRead(t));
    return 1;
}

/** 骰神赐福 施加/移除(驱动下降沿状态机) */
function doTeruBless(ctx, tag, mode) {
    var p = ctx.source.getPlayerOrException();
    var how = "clear";
    if (("" + mode) === "give") {
        try { p.addEffect(new MobEffectInstanceClass(teruBlessing(), 6000, 0, false, false, true)); how = "give"; }
        catch (e1) { how = "give_ex:" + exText(e1); }
    } else {
        try { ModEffectRemoval.remove(p, teruBlessing()); } catch (e2) { how = "clear_ex:" + exText(e2); }
    }
    send(ctx, "AP_" + tag + "_BLESS:" + how + ":" + teruStateRead(p));
    return 1;
}

/**
 * 外力移除降神效果实例(效果自检路径:真值仍在而实例没了 ⇒ 下一 tick 收敛到 endDescent)。
 * 操作对象 = `<name>`(真实玩家/bot);名字为 `-` = 施法者自己。
 * 读数同时给**被操作者**(`AP_<tag>_FX:<who>=…:fx_d_on=…`)与**施法者侧**全量(`…_FX_C:`),
 * 因为自检收敛的可见后果是「施法者的 cache / 护甲折算同 tick 归零」(endDescent 的施法者分支)。
 */
function doTeruFx(ctx, tag, nameText) {
    var own = teruOwner(ctx, tag, "who", nameText);
    if (!own.ok) return 1;
    var o = own.p;
    var err = "";
    try { TeruDescentEffectClass.remove(o); } catch (e1) { err = exText(e1); }
    send(ctx, "AP_" + tag + "_FX:" + own.who + ":removed=" + (err === "" ? 1 : 0)
        + ":fx_d_on=" + teruFxOn(o, DESC_TERU_DESCENT)
        + (err === "" ? "" : ":err=" + err));
    send(ctx, "AP_" + tag + "_FX_C:" + teruStateRead(ctx.source.getPlayerOrException()));
    return 1;
}

/** 死亡清场钩子(PlayerLifecycleHandler 死亡段调用的同一个产品入口);操作对象 = `<name>` */
function doTeruDie(ctx, tag, nameText) {
    var own = teruOwner(ctx, tag, "who_called", nameText);
    if (!own.ok) return 1;
    var o = own.p;
    var err = "";
    try { TeruSignItemClass.onOwnerDeathCleanup(o); } catch (e1) { err = exText(e1); }
    send(ctx, "AP_" + tag + "_DIE:called=" + (err === "" ? 1 : 0) + ":" + own.who
        + (err === "" ? "" : ":err=" + err));
    send(ctx, "AP_" + tag + "_DIE_T:" + teruTargetRead(o));
    send(ctx, "AP_" + tag + "_DIE_C:" + teruStateRead(ctx.source.getPlayerOrException()));
    return 1;
}

/**
 * 自目标防护(spec §6 的**必须保留**项):显示/快照路径以 ctx.target == attacker 复用同一套
 * 攻击修饰器链 ⇒ 这两条调用**不得**消耗狐光、不得把自身记进已攻击目标集。
 */
function doTeruGuard(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var layersBefore = TeruSignItemClass.getLayers(p);
    var newtBefore = teruSetSize(ModAttachments.getTeruDescentNewTargets(p));
    var ap = -1, dp = -1, range = "-", err = "";
    try { ap = TeruDiceCombatModifiersClass.attackPowerOf(p); } catch (e1) { err = err + "|ap:" + exText(e1); }
    try { dp = TeruDiceCombatModifiersClass.defensePowerOf(p); } catch (e2) { err = err + "|dp:" + exText(e2); }
    try {
        var diceStack = ItemStack.EMPTY;
        var h = curioHandler(p, "dice");
        if (h != null) diceStack = h.getStacks().getStackInSlot(0);
        // ⚠️ `ItemStack#getOrDefault` 在 1.20.1 的 KubeJS 白名单里不可见(实测
        //    `Cannot find function getOrDefault in object 1 dice`)⇒ 取不到增强就传 null,
        //    生产方法内部会把 null 当 `WeaponEnhancement.EMPTY` 处理(见 attackPowerBase)。
        var enh = null;
        try {
            if (!diceStack.isEmpty()) {
                enh = diceStack.getOrDefault(ModDataComponentsClass.WEAPON_ENHANCEMENT.get(),
                    WeaponEnhancementClass.EMPTY);
            }
        } catch (e3b) { enh = null; }
        var r = TeruDiceCombatModifiersClass.getDisplayAttackRange(p, diceStack, enh);
        range = r.min() + "-" + r.max();
    } catch (e3) { range = "ex:" + exText(e3); }
    var layersAfter = TeruSignItemClass.getLayers(p);
    var newtAfter = teruSetSize(ModAttachments.getTeruDescentNewTargets(p));
    send(ctx, "AP_" + tag + "_GUARD:ap=" + ap + ":dp=" + dp + ":dmg_range=" + range
        + ":layers=" + layersBefore + ">" + layersAfter
        + ":layers_ok=" + (layersBefore === layersAfter ? 1 : 0)
        + ":newt=" + newtBefore + ">" + newtAfter
        + ":newt_ok=" + (newtBefore === newtAfter ? 1 : 0)
        + (err === "" ? "" : ":err=" + err));
    return 1;
}

/**
 * 「降神生效中不可重复施放」的两道闸门(在**已建立降神链接**的前提下调用):
 *   ① 客户端按键入口 `BaseSignItem#performSkillForCurio` 应被 `canBeginSelectorSession` 拦下
 *      ⇒ 不开选择会话(session=0)、不进冷却、不加层;
 *   ② 服务端二次校验 `castDescent` 对**其它玩家**目标也应返回 false(生效中不可重复施放)。
 * 目标 = `<name>` 指定的**真实在线玩家**(Carpet bot 同入口;C 批之前这里用 FakePlayer ——
 * 它不在玩家列表,只能勉强当「另一名玩家」用)。读数里的 layers 不变即证明加成未被重复发放。
 */
function doTeruGate(ctx, tag, nameText) {
    var p = ctx.source.getPlayerOrException();
    var t = teruFindPlayer(ctx, nameText);
    if (t == null) { send(ctx, "AP_" + tag + "_ERR:no_player:" + nameText); return 1; }
    var layers0 = TeruSignItemClass.getLayers(p);
    var session = 0, token = -1;
    try {
        BaseSignItemClass.performSkillForCurio(p);
        session = TargetSelectionManagerClass.isSelecting(p) ? 1 : 0;
        token = TargetSelectionManagerClass.sessionTokenForTests(p);
    } catch (e1) { /* 忽略 */ }
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e2) { /* 忽略 */ }
    var recast = -1;
    try { recast = TeruSignItemClass.castDescent(p, t) ? 1 : 0; } catch (e4) { recast = -2; }
    var layers1 = TeruSignItemClass.getLayers(p);
    send(ctx, "AP_" + tag + "_GATE:target=" + nameText
        + ":session=" + session + ":token_seen=" + (token > 0 ? 1 : 0)
        + ":recast_other=" + recast + ":layers=" + layers0 + ">" + layers1
        + ":layers_ok=" + (layers0 === layers1 ? 1 : 0)
        + ":other_effect=" + teruFxOn(t, DESC_TERU_DESCENT)
        + ":cd=" + (signCooldownRemaining(p) > 0 ? 1 : 0)
        + ":" + teruStateRead(p));
    return 1;
}

/**
 * 实体类型 id → EntityType,并**拒绝静默回退**。
 *
 * ⚠️ `BuiltInRegistries.ENTITY_TYPE` 是 DefaultedRegistry,未知 key 会**静默返回默认值
 *    `minecraft:pig`**(1.21.1/1.20.1 同一行为)⇒ 「按 id 取类型」必须先校验 `getKey(get(id)) === id`,
 *    否则用例会对着「一只猪」跑完整条判据链还不报错(本用例首次实跑就是这样:type=minecraft:pig)。
 * ⚠️ 同时剥掉可能混进来的引号/空白(命令行的 `"minecraft:spider"` 在不同注入路径下可能带引号到达)。
 */
function teruResolveType(typeText) {
    var raw = ("" + typeText).replace(/["']/g, "").replace(/^\s+|\s+$/g, "");
    var type = null;
    try { type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(raw)); }
    catch (e1) { return { ok: false, raw: raw, why: "parse:" + exText(e1) }; }
    if (type == null) return { ok: false, raw: raw, why: "null_type" };
    var key = "?";
    try { key = "" + BuiltInRegistries.ENTITY_TYPE.getKey(type); } catch (e2) { key = "?"; }
    if (key !== raw) return { ok: false, raw: raw, why: "default_fallback:" + key };
    return { ok: true, raw: raw, type: type };
}

/** 摆一只高血量靶(无 AI、持久、正前方 dist 格),句柄存进 teruState */
function doTeruDummy(ctx, tag, whichText, typeText, distText, hpText) {
    var p = ctx.source.getPlayerOrException();
    var which = ("" + whichText) === "d2" ? "d2" : "d1";
    var dist = teruInt(distText, 4);
    var hp = teruInt(hpText, 1000);
    var resolved = teruResolveType(typeText);
    if (!resolved.ok) {
        send(ctx, "AP_" + tag + "_ERR:bad_type:req=" + resolved.raw + ":why=" + resolved.why);
        return 1;
    }
    var mob = spawnDummy(p, resolved.raw, dist);
    if (mob == null) { send(ctx, "AP_" + tag + "_ERR:dummy_null:" + resolved.raw); return 1; }
    var hpSet = -1;
    try {
        var Attrs = Java.loadClass("net.minecraft.world.entity.ai.attributes.Attributes");
        var a = mob.getAttribute(Attrs.MAX_HEALTH);
        if (a != null) a.setBaseValue(hp);
        mob.setHealth(hp);
        hpSet = mob.getHealth();
    } catch (e1) { hpSet = -1; }
    if (teruState == null) teruState = { d1: null, d2: null };
    if (which === "d2") teruState.d2 = mob; else teruState.d1 = mob;
    // ⚠️ 同时报两条**独立**的类型读数:`type` 走 `itemIdOf` 同族的 `getKey(getType())`(本文件既有的
    //    `typeIdOf`),`type_desc` 走 `EntityType#getDescriptionId()`。实机实测两者会不一致 —— 已知
    //    KubeJS 环境下 `BuiltInRegistries.ENTITY_TYPE.getKey(...)` 对**运行时生成的实体**会回落到
    //    DefaultedRegistry 的默认值 `minecraft:pig`(LULU 用例的注释里已记过同一现象:「按
    //    typeIdOf(entity) == minecraft:xxx 取靶不可靠」);判据一律以 `type_desc` + `type_req` 为准。
    var typeDesc = "-", typeHolder = "-", isSpider = -1;
    try { typeDesc = mob.getType().getDescriptionId(); } catch (e9) { typeDesc = "?"; }
    // 另一条独立路径:`EntityType#builtInRegistryHolder().key().location()`(不经过 DefaultedRegistry 的
    // byValue 反查)—— 若它与 `type`(getKey) 不一致,即可判定是 getKey 反查坏了、而不是实体真的不对。
    try { typeHolder = "" + mob.getType().builtInRegistryHolder().key().location(); } catch (e10) { typeHolder = "?"; }
    try { isSpider = (mob instanceof Java.loadClass("net.minecraft.world.entity.monster.Spider")) ? 1 : 0; }
    catch (e11) { isSpider = -2; }
    send(ctx, "AP_" + tag + "_DUMMY:which=" + which + ":type=" + typeIdOf(mob)
        + ":type_req=" + resolved.raw + ":type_desc=" + typeDesc
        + ":type_holder=" + typeHolder + ":is_spider=" + isSpider
        + ":eid=" + mob.getId() + ":hp=" + teruR1(hpSet) + ":dist=" + dist
        + ":pos=" + teruR1(mob.getX()) + "," + teruR1(mob.getY()) + "," + teruR1(mob.getZ())
        + ":layers=" + TeruSignItemClass.getLayers(p));
    return 1;
}

/**
 * 真实近战(走 Player#attack ⇒ 原版左键同源 ⇒ 骰战链 + 攻击修饰器链)。
 * 判据:每次命中的层数增量 / 已攻击目标集增量 / 掉血值(额外攻击力足够大时 dmg 会跃升)。
 */
function doTeruHitReal(ctx, tag, whichText, timesText, nameText) {
    var t = teruFindPlayer(ctx, nameText);
    if (t == null) { send(ctx, "AP_" + tag + "_ERR:no_player:" + nameText); return 1; }
    var which = ("" + whichText) === "d2" ? "d2" : "d1";
    var mob = (teruState == null) ? null : (which === "d2" ? teruState.d2 : teruState.d1);
    if (mob == null) { send(ctx, "AP_" + tag + "_ERR:no_dummy:" + which); return 1; }
    var times = teruInt(timesText, 1);
    var alive = 0;
    try { alive = mob.isAlive() ? 1 : 0; } catch (e0) { alive = -1; }
    var out = "attacker=" + nameText + ":which=" + which + ":eid=" + mob.getId()
        + ":type=" + typeIdOf(mob) + ":alive=" + alive
        + ":hand=" + itemIdOf(t.getMainHandItem())
        + ":t_newt0=" + teruSetSize(ModAttachments.getTeruDescentNewTargets(t));
    for (var i = 0; i < times; i++) {
        var hp0 = -1, hp1 = -1, dmg = -1, api = "-", err = "";
        try { hp0 = mob.getHealth(); } catch (e1) { hp0 = -1; }
        var r = null;
        try { r = meleeHit(t, mob); } catch (e3) { err = exText(e3); }
        try { hp1 = mob.getHealth(); } catch (e4) { hp1 = -1; }
        if (r != null) { dmg = teruR1(r.dealt); api = "" + r.api; }
        out = out + ":h" + i + "_hp=" + teruR1(hp0) + ">" + teruR1(hp1)
            + ":h" + i + "_dmg=" + dmg + ":h" + i + "_api=" + api
            + (err === "" ? "" : ":h" + i + "_err=" + err);
    }
    out = out + ":t_newt=" + teruSetSize(ModAttachments.getTeruDescentNewTargets(t))
        + ":" + teruTargetRead(t);
    send(ctx, "AP_" + tag + "_HITREAL:" + out);
    return 1;
}

/**
 * 模组发牌漏斗计层(VitaminPillChipItem#giveCard —— 全仓卡牌发放的**唯一**入口):
 *   attack       ⇒ 攻击牌成功入包 +n 层
 *   defense      ⇒ 防御牌 **不**计层(isAttackCard 判定)
 *   other        ⇒ 非卡牌物品 **不**计层
 *   attack_drop  ⇒ 先把 36 格塞满 ⇒ add 失败走 drop 分支 ⇒ 掉落**不**计层(ground 读数取证)
 */
function doTeruCard(ctx, tag, kindText, nText) {
    var p = ctx.source.getPlayerOrException();
    var kind = "" + kindText;
    var n = teruInt(nText, 1);
    var id = (kind === "attack" || kind === "attack_drop") ? TERU_ATTACK_CARD_ID
        : (kind === "defense" ? TERU_DEFENSE_CARD_ID : "minecraft:stone");
    var filled = 0;
    if (kind === "attack_drop") {
        var stone = resolveItem("minecraft:stone");
        for (var i = 0; i < 36; i++) {
            try { p.getInventory().setItem(i, new ItemStack(stone, 64)); filled = filled + 1; } catch (e0) { /* 忽略 */ }
        }
    }
    var item = resolveItem(id);
    if (item == null) { send(ctx, "AP_" + tag + "_ERR:unknown_item:" + id); return 1; }
    var probeStack = new ItemStack(item, n);
    var isAtk = TeruSignItemClass.isAttackCard(probeStack) ? 1 : 0;
    var l0 = TeruSignItemClass.getLayers(p);
    var inv0 = teruInvCount(p, id);
    var err = "";
    try { TeruVitaminPillChipItemClass.giveCard(p, probeStack); } catch (e1) { err = exText(e1); }
    var l1 = TeruSignItemClass.getLayers(p);
    var inv1 = teruInvCount(p, id);
    var ground = teruGroundCount(p, id, 8);
    send(ctx, "AP_" + tag + "_CARD:kind=" + kind + ":item=" + id + ":n=" + n + ":is_atk=" + isAtk
        + ":filled=" + filled + ":inv=" + inv0 + ">" + inv1 + ":ground=" + ground
        + ":layers=" + l0 + ">" + l1 + ":dl=" + (l1 - l0)
        + (err === "" ? "" : ":err=" + err));
    return 1;
}

/**
 * 守卫①:在玩家脚下生成一张**可立即拾取**的攻击牌(pickupDelay=0)。
 * 本命令只负责生成并给基线读数;拾取由原版 aiStep 在随后几 tick 内完成,
 * 拾取后的读数(背包 +1、层数不变)由后续 teruread 给出。
 */
function doTeruDrop(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var item = resolveItem(TERU_ATTACK_CARD_ID);
    if (item == null) { send(ctx, "AP_" + tag + "_ERR:unknown_item:" + TERU_ATTACK_CARD_ID); return 1; }
    var ent = null, err = "", delaySet = 0, added = 0;
    try {
        ent = new ItemEntityClass(p.level, p.getX(), p.getY() + 0.4, p.getZ(), new ItemStack(item, 1));
    } catch (e1) { err = "new:" + exText(e1); }
    if (ent != null) {
        // ⚠️ `ItemEntity#setPickupDelay` 也在 KubeJS 白名单之外(实测 Cannot find function setPickupDelay)。
        //    它**必须**单独 try:写在同一个 try 里会让异常跳过 addFreshEntity ⇒ 实体压根没进世界
        //    (实机取证:读数 spawn=1 但 ground=0 且后续永远捡不到)。构造器自带 10 tick 拾取延迟,足够本用例。
        try { ent.setPickupDelay(0); delaySet = 1; } catch (e2) { delaySet = 0; }
        try { p.level.addFreshEntity(ent); added = 1; } catch (e3) { err = err + "|add:" + exText(e3); }
    }
    send(ctx, "AP_" + tag + "_DROP:spawn=" + (ent == null ? 0 : 1) + ":added=" + added
        + ":delay_set=" + delaySet
        + ":eid=" + (ent == null ? -1 : ent.getId())
        + ":inv=" + teruInvCount(p, TERU_ATTACK_CARD_ID)
        + ":ground=" + teruGroundCount(p, TERU_ATTACK_CARD_ID, 8)
        + ":layers=" + TeruSignItemClass.getLayers(p)
        + ":newt=" + teruSetSize(ModAttachments.getTeruDescentNewTargets(p))
        + (err === "" ? "" : ":err=" + err));
    return 1;
}

/**
 * 关掉当前容器。
 *
 * ⚠️ `ServerPlayer#closeContainer()` 在 KubeJS 的方法白名单里**不可见**(实测
 *    `TypeError: Cannot find function closeContainer`)——而它内部才会 `doCloseContainer()`:
 *    ① 调 `containerMenu.removed(player)`(卡牌栏的 `saveToDice` 就挂在那里)② 给客户端发
 *    `ClientboundContainerClosePacket` 把 GUI 关掉。少了 ② 客户端会**一直停在卡牌栏界面**,
 *    后续注入的聊天命令全被 GUI 吃掉(实机取证:teruequip E1 之后整条链一条读数都没有)。
 *    故此处手动补齐这两个副作用,并按真实容器 id 发包。
 */
function teruCloseMenu(p) {
    var how = "closeContainer";
    var id = -1;
    try { id = (p.containerMenu == null) ? -1 : p.containerMenu.containerId; } catch (e0) { id = -1; }
    try { p.closeContainer(); return how; } catch (e1) { how = "packet"; }
    try { p.containerMenu.removed(p); } catch (e2) { how = how + "|removed_ex:" + exText(e2); }
    if (TeruClosePacketClass != null && id >= 0) {
        try { p.connection.send(new TeruClosePacketClass(id)); how = how + "|closePacket=" + id; }
        catch (e3) { how = how + "|packet_ex:" + exText(e3); }
    } else {
        how = how + "|no_packet_class_or_id";
    }
    try { p.containerMenu = p.inventoryMenu; } catch (e4) { how = how + "|reset_ex:" + exText(e4); }
    return how;
}

/**
 * 打开真实卡牌栏菜单(与真人按 K 键同源的服务端入口;骰子为空时 saveToDice 直接 return)。
 * ⚠️ 不能用 `p.closeContainer()` 做前置清理(白名单不可见,见 {@link teruCloseMenu});
 *    改为「removed + 关闭包 + 复位指针」,并且**必须**把 server 侧 `containerMenu` 复位 ——
 *    `ServerPlayer#openMenu` 在 `containerMenu != inventoryMenu` 时会先调 `closeContainer()`,
 *    那一步会直接抛异常、连带后面几次 teruequip 全部无声失败。
 */
function teruOpenCardMenu(p) {
    try {
        if (p.containerMenu != null && p.containerMenu != p.inventoryMenu) {
            var old = -1;
            try { old = p.containerMenu.containerId; } catch (e0) { old = -1; }
            try { p.containerMenu.removed(p); } catch (e1) { /* 忽略 */ }
            if (TeruClosePacketClass != null && old >= 0) {
                try { p.connection.send(new TeruClosePacketClass(old)); } catch (e2) { /* 忽略 */ }
            }
            try { p.containerMenu = p.inventoryMenu; } catch (e3) { /* 忽略 */ }
        }
    } catch (e4) { /* 忽略 */ }
    var provider = new SimpleMenuProviderClass(function (id, inv, pl) {
        return new TeruCardInventoryMenuClass(id, inv);
    }, ComponentClass.literal("astral_probe_teru_cards"));
    var res = p.openMenu(provider);
    if (res == null || !res.isPresent()) return null;
    return p.containerMenu;
}

/** 卡牌容器内容读数(关界面后容器会被清空 ⇒ 必须在 closeContainer 之前读) */
function teruCardSlotsRead(menu, cardSlots) {
    var s = "";
    for (var i = 0; i < cardSlots; i++) {
        var st = null;
        try { st = menu.getCardItem(i); } catch (e1) { st = null; }
        if (st != null && !st.isEmpty()) s = s + (s === "" ? "" : "|") + i + ":" + itemIdOf(st) + "x" + st.getCount();
    }
    return s === "" ? "-" : s;
}

/**
 * 守卫②:走**真实卡牌栏**的「插入 → 卸除 → 再插入」。
 *   first   :背包里放 1 张攻击牌 ⇒ 快捷移动进卡槽 ⇒ 关界面(saveToDice)
 *   unequip :开界面(loadFromDice 会把骰子里的牌重建回卡槽)⇒ 快捷移动回背包 ⇒ 关界面
 *   again   :开界面 ⇒ 把背包里那张同类型牌再插回卡槽 ⇒ 关界面(水位已到 1 ⇒ **不得**再计层)
 * 判据:watermark 只升不降 / 第二次插入的 layers 增量 = 0。
 */
function doTeruEquip(ctx, tag, modeText) {
    var p = ctx.source.getPlayerOrException();
    var mode = "" + modeText;
    // 骰神赐福期间卡牌栏被锁定(clicked/quickMoveStack 双双 return)⇒ 测试前置必须清掉
    try { ModEffectRemoval.remove(p, teruBlessing()); } catch (e0) { /* 忽略 */ }
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e1) { /* 忽略 */ }
    var wmBefore = ModAttachments.getTeruEquipWatermark(p);
    var l0 = TeruSignItemClass.getLayers(p);
    var invBefore = teruInvCount(p, TERU_ATTACK_CARD_ID);
    var added = 0;
    if (mode === "first") {
        try { p.getInventory().add(new ItemStack(resolveItem(TERU_ATTACK_CARD_ID), 1)); added = 1; } catch (e2) { /* 忽略 */ }
    }
    var menu = teruOpenCardMenu(p);
    if (menu == null) { send(ctx, "AP_" + tag + "_ERR:menu_null"); return 1; }
    var cardSlots = -1, attackSlots = -1;
    try { cardSlots = menu.getCardSlots(); attackSlots = menu.getAttackSlots(); } catch (e3) { cardSlots = -1; }
    var cardsBeforeMove = teruCardSlotsRead(menu, cardSlots < 0 ? 0 : cardSlots);
    var movedId = "-", movedN = -1, target = -1, err = "";
    try {
        if (mode === "first" || mode === "again") {
            var idx = teruInvIndex(p, TERU_ATTACK_CARD_ID);
            if (idx < 0) { err = "card_not_in_inv"; }
            else {
                target = cardSlots + idx;
                var r = menu.quickMoveStack(p, target);
                movedN = (r == null || r.isEmpty()) ? 0 : r.getCount();
                movedId = (r == null || r.isEmpty()) ? "-" : itemIdOf(r);
            }
        } else {
            target = 0;   // 攻击牌槽 0
            var r2 = menu.quickMoveStack(p, target);
            movedN = (r2 == null || r2.isEmpty()) ? 0 : r2.getCount();
            movedId = (r2 == null || r2.isEmpty()) ? "-" : itemIdOf(r2);
        }
    } catch (e4) { err = exText(e4); }
    var cardsAfterMove = teruCardSlotsRead(menu, cardSlots < 0 ? 0 : cardSlots);
    var close = teruCloseMenu(p);
    var wmAfter = ModAttachments.getTeruEquipWatermark(p);
    var l1 = TeruSignItemClass.getLayers(p);
    var invAfter = teruInvCount(p, TERU_ATTACK_CARD_ID);
    send(ctx, "AP_" + tag + "_EQ:mode=" + mode
        + ":menu_slots=" + cardSlots + ":attack_slots=" + attackSlots
        + ":added=" + added + ":inv=" + invBefore + ">" + invAfter
        + ":cards_before=" + cardsBeforeMove + ":cards_after=" + cardsAfterMove
        + ":move_target=" + target + ":moved=" + movedId + "x" + movedN
        + ":wm=" + (wmBefore === "" ? "-" : wmBefore) + ">" + (wmAfter === "" ? "-" : wmAfter)
        + ":layers=" + l0 + ">" + l1 + ":dl=" + (l1 - l0)
        + ":close=" + close
        + (err === "" ? "" : ":err=" + err));
    return 1;
}

/** 收尾:清效果/记录/层数/水位之外的派生值 + 清饰品槽与主手(用例末尾调用) */
function doTeruClear(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e0) { /* 忽略 */ }
    teruClearState(p);
    try { ModAttachments.setTeruEquipWatermark(p, ""); } catch (e1) { /* 忽略 */ }
    try { clearCurioSlots(p, "stand"); } catch (e2) { /* 忽略 */ }
    try { clearCurioSlots(p, "dice"); } catch (e3) { /* 忽略 */ }
    lpSetHand(p, "");
    if (teruState != null) { teruState.d1 = null; teruState.d2 = null; }
    send(ctx, "AP_" + tag + "_CLEAR:cleared=1:" + teruStateRead(p));
    return 1;
}
// ════════════════════════════════════════════════════════════════════════════
// ════════════════════════════════════════════════════════════════════════════
// ════════════════════════════════════════════════════════════════════════════
// ════════════════════════════════════════════════════════════════════════════
// ════════════════════════════════════════════════════════════════════════════
// ════════════════════════════════════════════════════════════════════════════
// ════════════════════════════════════════════════════════════════════════════
// ════════════════════════════════════════════════════════════════════════════
//  风水师立牌(zhao)+ 符卡-福/祸 游戏内取证(2026-09-27;双人用 Carpet /player bot)
//   被测语义(冻结件 docs/features/fengshui-sign-spec.md;§14 裁决已回填):
//     ① 主动「白泽赐福」(action id = zhao_blessing):选 16 格内**玩家或自身** ⇒ 目标获得白泽赐福;
//        施法者 +1 符卡-福 并把自身全部符卡-祸**就地转成符卡-福**;目标装备大当家立牌 ⇒ +1 层养精蓄锐
//        (完美帮手);非玩家目标(敌对生物)必须被拒;
//     ② 结束判定 = 玩家级 tick 的**下降沿**(施加时目标已在骰神赐福 ⇒ skip=1,跳过当前这一次结束);
//        效果被外力移除 ⇒ 下一 tick 自检复位真值并回收溢出加成;
//     ③ 溢出治疗 → 攻击力:赐福期内 heal() 的溢出量累加进攻击力(整数化 + 余数留档),
//        溢出 ≤ 0 不增加;结束/死亡/重登/外力移除 ⇒ 一并归零(不留残留);
//     ④ 被动「福祸相倚」:骰点 1 ⇒ +1 符卡-祸(厄运层数立即跟上);骰点 6 ⇒ +1 符卡-福;
//        同一 tick 的第二次判定必须被 tryClaimDiceJudgment 挡下(一次结算一次发牌);
//     ⑤ 符卡-福:主手手持即开启选择器(PLAYER + allowSelf),目标回复 2 点;
//        出牌数「消耗 1 / 返回 1」净 0(fu_card_cycle_bonus 计入 getMaxAllowed,受 min(9,1+extra) 封顶);
//        专属牌:非获得者使用时**选择器开局与服务端权威两处都被拒**;countFu() = 主物品栏口径(不含副手);
//     ⑥ 符卡-祸:选择器仅 ENEMY_OR_RIVAL(敌对生物 ∪ 非同队玩家,**不可自用**),命中 1 点真伤;
//        厄运层数 = count() = 主物品栏 **+ 副手**;每 2400 tick 按**结算时刻张数**受伤;计时器与张数解耦
//        (张数在 >0 区间内变化不重新起算);张数归 0 一并清效果与计时器;放进末影箱等容器不计;
//        **「禁止丢弃」已按用户指令整体移除** ⇒ 丢弃必须成功(ServerPlayer#drop 走 onDroppedByPlayer 真路径);
//     ⑦ 心意相连:大当家立牌主动时,同队且装备风水师立牌者各得 1 张符卡-福;未组队整体不生效;
//     ⑧ 注册冻结值:物品 id / 动作 id / 常量(HEAL_AMOUNT=2、DAMAGE=1.0、CURSE_PERIOD_TICKS=2400、
//        DURATION_TICKS=MAX_VALUE、MAX_RECHARGE=5)/ 标签(astral_dice:signs、curios:stand)/ cardByTypeId。
//
//   命令(读数行一律 AP_<tag>_ 前缀;除基线/脚手架外全部只读):
//     /astralprobe zhauprep <tag> [clear]               基线:风水师立牌+骰子+铁剑,清卡/状态/效果/计时器/出牌轮
//     /astralprobe zhauread <tag> <phase> [name]        只读全量读数(缺省=自身;可指真实玩家/bot)
//     /astralprobe zhaureg <tag>                        注册与冻结数值(物品/常量/效果/选择器动作/标签/byType)
//     /astralprobe zhaogive <tag> <fu|huo> <n> [name]   给自身/指定真实玩家 n 张(绑定受赠者)
//     /astralprobe zhaosethuo <tag> <n> [name]          符卡-祸张数设为 n(先全清后给)+ 立即镜像厄运 + 计时器解耦证据
//     /astralprobe zhaoclear <tag> [name]               清卡/白泽/厄运/计时器 + 复位出牌轮与养精蓄锐(脚手架)
//     /astralprobe zhaocast <tag> <self|name|mob> [name] 服务端权威入口 applyBlessing(自身/真实玩家/生物)
//     /astralprobe zhaogate <tag> <self|name> [name]    真实选择器路径:performSkillForCurio → confirm
//     /astralprobe zhaobless <tag> <give|clear> [name]  骰神赐福 施加(原版 /effect give)/移除(内部通道)
//     /astralprobe zhaosign <tag> <name> <zhao|fen|none> 给真实玩家(bot)装备风水师/大当家立牌或卸下
//     /astralprobe zhaodice <tag> <1|6> <times>         福祸相倚:直接走 onDiceRollResult
//     /astralprobe zhaodedup <tag> <1|6>                同一 tick 两次判定(tryClaimDiceJudgment 去重)
//     /astralprobe zhaoheal <tag> <amount> <gap> [name] 把 HP 压到 max-gap 再 heal(溢出→攻击力)
//     /astralprobe zhaoseq <tag> [name]                 溢出链整跑:10/0、6/4、0.5/0、0.5/0、4/4(一条读数)
//     /astralprobe zhaofu <tag> <times>                 符卡-福连用 times 次(逐次 ok/play/max/fubonus/cool)
//     /astralprobe zhaohuotick <tag> <now|force> [name] 立即跑一次 HuoCardItem#tick(起算 / 结算)
//     /astralprobe zhaohuobox <tag> <main|off|ender> [name] 把全部符卡-祸挪到 主手 / 副手 / 末影箱
//     /astralprobe zhaodrop <tag>                       符卡-祸丢弃(真 ServerPlayer#drop;必须成功)
//     /astralprobe zhaocard <tag> <fu|huo> <self|name|mob> [name] [owner] 主手→tickHeldSelector→confirm
//     /astralprobe zhaoplay <tag> <fu|huo> <self|name|mob> [name] [owner] 直接 playFromSelector(服务端权威)
//     /astralprobe zhaohand <tag> <fu|huo> <main|off>   §14.11:主手唤起选择器 / 副手不唤起
//     /astralprobe zhaofx <tag> [name]                  外力移除白泽赐福效果(下一 tick 自检路径)
//     /astralprobe zhaolife <tag> <die|relogin> [name]  死亡清场 / 重登复位(产品同一入口)
//     /astralprobe zhaolink <tag> <read|call>           心意相连:只读候选集 / 调 onAllyActiveSkill
//     /astralprobe zhaonuclear <tag>                    收尾:清卡/效果/状态/计时器 + 卸立牌 + 清主手
//
//   ⚠️ 改探针后必须**冷启动**(mt.ps1 --phase stop → --phase launch):
//      /kubejs reload server-scripts 不会重绑已注册的 Brigadier 命令。
//   ⚠️ 两线(1.21.1 / 1.20.1)本段逐字一致:只允许用两线同名 API(已核对访问器 14:14、常量 6:6、
//      类路径 11:11 全等;1.20.1 的 FenSignItem#addRecharge 形参名不同但签名相同)。
//   ⚠️ Rhino 白名单坑(本段已规避,勿改回):`ItemStack#is(TagKey)` 重载歧义 ⇒ 改读 Holder#tags();
//      `ItemEntity#setPickupDelay` 不可见 ⇒ 丢弃一律走 ServerPlayer#drop(boolean) 的原版路径。
// ════════════════════════════════════════════════════════════════════════════

function zhaoLoadCls(name) {
    try { return Java.loadClass(name); } catch (e) { return null; }
}

var ZhaoSignItemClass = zhaoLoadCls("com.merlinkitsune.astral_dice.item.sign.ZhaoSignItem");
var FuCardItemClass = zhaoLoadCls("com.merlinkitsune.astral_dice.item.card.FuCardItem");
var HuoCardItemClass = zhaoLoadCls("com.merlinkitsune.astral_dice.item.card.HuoCardItem");
var ZhaoBlessingEffectClass = zhaoLoadCls("com.merlinkitsune.astral_dice.effect.ZhaoBlessingEffect");
var MisfortuneEffectClass = zhaoLoadCls("com.merlinkitsune.astral_dice.effect.MisfortuneEffect");
var BaseEffectCardItemClass = zhaoLoadCls("com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem");
var ExclusiveCardUtilClass = zhaoLoadCls("com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil");
// ⚠️ `EffectCardPeriodClass` 已由本文件 :1228(1.20.1 :1209)声明 —— **不得**在此重复声明:
//    Rhino 对同一作用域的重复 `var` 抛 `TypeError: redeclaration of var`(整脚本加载失败、命令全无)。
var FenSignItemClass = zhaoLoadCls("com.merlinkitsune.astral_dice.item.sign.FenSignItem");
var ZhaoHandClass = zhaoLoadCls("net.minecraft.world.InteractionHand");

var ZHAO_SIGN_ID = "astral_dice:zhao_sign";
var ZHAO_FU_ID = "astral_dice:fu_card";
var ZHAO_HUO_ID = "astral_dice:huo_card";
var ZHAO_DICE_ID = "astral_dice:dice";
var ZHAO_FEN_SIGN_ID = "astral_dice:fen_sign";
var DESC_ZHAO_BLESSING = "effect.astral_dice.zhao_blessing";
var DESC_MISFORTUNE = "effect.astral_dice.misfortune";
// 骰神赐福 = 下降沿状态机的输入源(施加/移除见 doZhaoBless:走原版 /effect 命令)
var DESC_DICE_BLESSING = "effect.astral_dice.dice_blessing";

/** 数值容错取值(读不到给 -9;不抛) */
function zhaoNum(fn) {
    try { return fn(); } catch (e) { return -9; }
}

/** 布尔 → 1/0(读不到给 -9) */
function zhaoBool(fn) {
    try { return fn() ? 1 : 0; } catch (e) { return -9; }
}

/** 主背包(0..35)+ 副手里指定物品的张数(-1 = 读不到) */
function zhaoCountInvAndOff(p, itemId) {
    var n = 0;
    try {
        var inv = p.getInventory();
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty() && itemIdOf(st) === itemId) n = n + st.getCount();
        }
        var off = p.getOffhandItem();
        if (!off.isEmpty() && itemIdOf(off) === itemId) n = n + off.getCount();
    } catch (e) { return -1; }
    return n;
}

/**
 * 目标侧/自方统一全量读数(**单行**,字段顺序固定;用例按子串断言)。
 * 同时适用于自身、真实玩家与 Carpet bot(都是真 ServerPlayer ⇒ 每 tick 事件照跑)。
 */
function zhaoStateRead(p) {
    var fu = zhaoNum(function () { return FuCardItemClass.countFu(p); });
    var huo = zhaoNum(function () { return HuoCardItemClass.count(p); });
    var equipped = zhaoBool(function () { return ZhaoSignItemClass.isEquipped(p); });
    var active = zhaoBool(function () { return ModAttachments.isZhaoBlessingActive(p); });
    var skip = zhaoNum(function () { return ModAttachments.getZhaoBlessingSkipCycles(p); });
    var prev = zhaoBool(function () { return ModAttachments.isZhaoPrevBlessing(p); });
    var fx = effectAmpDur(findEffect(p, DESC_ZHAO_BLESSING));
    var fxOn = zhaoBool(function () { return ZhaoBlessingEffectClass.has(p); });
    var over = zhaoNum(function () { return ModAttachments.getZhaoOverflowBonus(p); });
    var rem = zhaoNum(function () { return ModAttachments.getZhaoOverflowRemainder(p); });
    var ap = zhaoNum(function () { return TeruDiceCombatModifiersClass.attackPowerOf(p); });
    var mis = zhaoNum(function () { return MisfortuneEffectClass.getStacks(p); });
    // ⚠️ `p.hasEffect(ModEffects.X)` 在 1.20.1 上会触发 KubeJS 注册表强转并抛异常(逃出 try/catch)
    //    ⇒ 一律用 findEffect(按 descriptionId 字符串匹配,两条线同源)。
    var misOn = (findEffect(p, DESC_MISFORTUNE) != null) ? 1 : 0;
    var misFx = effectAmpDur(findEffect(p, DESC_MISFORTUNE));
    var next = zhaoNum(function () { return ModAttachments.getHuoCardNextDamageTick(p); });
    var now = nowTickLevel(p.level);
    var nextDelta = (next > 0 && now > 0) ? (next - now) : -1;
    var play = zhaoNum(function () { return EffectCardPeriodClass.getPlayCount(p); });
    var max = zhaoNum(function () { return EffectCardPeriodClass.getMaxAllowed(p); });
    var bonus = zhaoNum(function () { return EffectCardPeriodClass.getBonusPlays(p); });
    var fuBonus = zhaoNum(function () { return ModAttachments.getFuCardCycleBonus(p); });
    var cool = zhaoBool(function () { return EffectCardPeriodClass.isCooldownActive(p); });
    var fen = zhaoNum(function () { return ModAttachments.getFenRecharge(p); });
    var team = zhaoBool(function () { return ZhaoSignItemClass.teamGateOpen(p); });
    var links = zhaoNum(function () { return ZhaoSignItemClass.linkedReceiverIds(p).size(); });
    var hp = zhaoNum(function () { return teruR1(p.getHealth()); });
    var maxHp = zhaoNum(function () { return teruR1(p.getMaxHealth()); });
    return "z_fu=" + fu + ":z_huo=" + huo + ":z_equipped=" + equipped
        + ":z_active=" + active + ":z_skip=" + skip + ":z_prev=" + prev
        + ":z_fx=" + fx + ":z_fx_on=" + fxOn
        + ":z_over=" + over + ":z_rem=" + teruR1(rem) + ":z_ap=" + ap
        + ":z_mis=" + mis + ":z_mis_on=" + misOn + ":z_mis_fx=" + misFx
        + ":z_next_delta=" + nextDelta
        + ":z_play=" + play + ":z_max=" + max + ":z_bonus=" + bonus + ":z_fubonus=" + fuBonus + ":z_cool=" + cool
        + ":z_fen=" + fen + ":z_team=" + team + ":z_links=" + links
        + ":z_hp=" + hp + ":z_maxhp=" + maxHp
        + ":z_bless=" + ((findEffect(p, DESC_DICE_BLESSING) != null) ? 1 : 0)
        + ":z_alive=" + zhaoBool(function () { return p.isAlive(); })
        + ":z_hand=" + itemIdOf(p.getMainHandItem()) + ":z_off=" + itemIdOf(p.getOffhandItem());
}

/** 全清主背包(0..35)+ 副手里的指定物品;返回移除张数(-1 = 读不到) */
function zhaoClearItem(p, itemId) {
    var removed = 0;
    try {
        var inv = p.getInventory();
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty() && itemIdOf(st) === itemId) { removed = removed + st.getCount(); inv.setItem(i, ItemStack.EMPTY); }
        }
        var off = p.getOffhandItem();
        if (!off.isEmpty() && itemIdOf(off) === itemId) {
            removed = removed + off.getCount();
            p.setItemInHand(ZhaoHandClass.OFF_HAND, ItemStack.EMPTY);
        }
    } catch (e) { return -1; }
    return removed;
}

/** 把 n 张指定物品放进**主手**(先清掉主背包+副手里的同物品,保证"手里只有这一叠") */
function zhaoHoldItem(p, itemId, n) {
    try { zhaoClearItem(p, itemId); } catch (e0) { /* 忽略 */ }
    var item = resolveItem(itemId);
    if (item == null) return "unknown_item:" + itemId;
    try { p.setItemInHand(ZhaoHandClass.MAIN_HAND, new ItemStack(item, n)); } catch (e1) { return exText(e1); }
    return "";
}

/** 装备/卸下 curios `stand` 槽(空 itemId = 卸下);返回 "" = 成功(复用既有 putInSlot/clearCurioSlots) */
function zhaoSetStand(p, itemId) {
    if (itemId == null || itemId === "") {
        var r = clearCurioSlots(p, "stand");
        return r == null ? "" : r;
    }
    var item = resolveItem(itemId);
    if (item == null) return "unknown_item:" + itemId;
    var r2 = putInSlot(p, "stand", new ItemStack(item), 0);
    return r2 == null ? "" : r2;
}

/**
 * 把主背包里**已存在**的那一叠指定物品整叠搬到主手选中槽(保留数据组件/获得者绑定)。
 * ⚠️ 必须搬**原栈**:新建 `new ItemStack(item)` 会丢掉专属牌的获得者组件,「非获得者被拒」就测不出来了。
 */
function zhaoMoveToMainHand(p, itemId) {
    try {
        var inv = p.getInventory();
        var keep = null;
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty() && itemIdOf(st) === itemId) { keep = st.copy(); break; }
        }
        if (keep == null) return "card_not_found:" + itemId;
        for (var j = 0; j < 36; j++) {
            var s2 = inv.getItem(j);
            if (!s2.isEmpty() && itemIdOf(s2) === itemId) inv.setItem(j, ItemStack.EMPTY);
        }
        p.setItemInHand(ZhaoHandClass.MAIN_HAND, keep);
        return "";
    } catch (e) { return exText(e); }
}

/** 清指定玩家的卡与白泽/厄运/骰神赐福 + 回满血(脚手架;不动立牌装备与出牌轮) */
function zhaoClearState(p) {
    var err = "";
    try { zhaoClearItem(p, ZHAO_FU_ID); } catch (e1) { err = err + "|fu:" + exText(e1); }
    try { zhaoClearItem(p, ZHAO_HUO_ID); } catch (e2) { err = err + "|huo:" + exText(e2); }
    try { ModAttachments.setZhaoBlessingActive(p, false); } catch (e3) { /* 忽略 */ }
    try { ModAttachments.setZhaoBlessingSkipCycles(p, 0); } catch (e4) { /* 忽略 */ }
    try { ModAttachments.setZhaoPrevBlessing(p, false); } catch (e5) { /* 忽略 */ }
    try { ModAttachments.clearZhaoOverflowBonus(p); } catch (e6) { /* 忽略 */ }
    try { ModAttachments.setHuoCardNextDamageTick(p, 0); } catch (e7) { /* 忽略 */ }
    try { ZhaoBlessingEffectClass.remove(p); } catch (e8) { /* 忽略 */ }
    try { MisfortuneEffectClass.clear(p); } catch (e9) { /* 忽略 */ }
    // 骰神赐福(下降沿的输入)与血量也必须回到基线,否则读数会依赖前序用例残留
    try { ModEffectRemoval.remove(p, teruBlessing()); } catch (e10) { /* 忽略 */ }
    try { p.setHealth(p.getMaxHealth()); } catch (e11) { /* 忽略 */ }
    return err;
}

/** 出牌轮 + 风水师状态的统一基线复位(与生产侧 clearRoundBonuses 逐项对齐;见用例前置) */
function zhaoResetRound(p) {
    try { resetEffectCardCycle(p); } catch (e1) { /* 忽略 */ }
    try { EffectCardPeriodClass.forceResetRound(p); } catch (e2) { /* 忽略 */ }
    try { ModAttachments.setFuCardCycleBonus(p, 0); } catch (e3) { /* 忽略 */ }
}

/** 给指定玩家 n 张(绑定 owner) */
function zhaoGiveCard(receiver, owner, kind, n) {
    if (kind === "huo") { HuoCardItemClass.give(receiver, owner, n); return; }
    FuCardItemClass.give(receiver, owner, n);
}

/** 把玩家 HP 压到 max - gap(gap <= 0 则回满;下界 1 点,避免判死) */
function zhaoSetGap(p, gap) {
    try {
        var maxHp = p.getMaxHealth();
        var target = gap > 0 ? (maxHp - gap) : maxHp;
        if (target < 1.0) target = 1.0;
        p.setHealth(target);
    } catch (e) { /* 忽略 */ }
}

/** 玩家 UUID 文本(只用本文件既有的多重容错取值器) */
function zhaoUuidText(p) {
    var u = playerUuid(p);
    return u.ok ? ("" + u.value) : "<no_uuid>";
}

/** 在线玩家名单(双人现场证据:list= 里应含施法者与 bot) */
function zhaoOnlineNames(ctx) {
    var p = ctx.source.getPlayerOrException();
    var out = "-";
    try {
        var all = p.level.getServer().getPlayerList().getPlayers();
        var acc = "";
        for (var i = 0; i < all.size(); i++) {
            var qn = "?";
            try { qn = "" + all.get(i).getName().getString(); } catch (e1) { qn = "?"; }
            acc = (i === 0) ? qn : (acc + "," + qn);
        }
        out = (acc === "") ? "-" : acc;
    } catch (e2) { out = "?"; }
    return out;
}

/** 类门禁:任一产品类缺失 ⇒ 报 ERR(负向断言会立刻抓出) */
function zhaoClassGate(ctx, tag) {
    var miss = "";
    if (ZhaoSignItemClass == null) miss = miss + "|ZhaoSignItem";
    if (FuCardItemClass == null) miss = miss + "|FuCardItem";
    if (HuoCardItemClass == null) miss = miss + "|HuoCardItem";
    if (ZhaoBlessingEffectClass == null) miss = miss + "|ZhaoBlessingEffect";
    if (MisfortuneEffectClass == null) miss = miss + "|MisfortuneEffect";
    if (EffectCardPeriodClass == null) miss = miss + "|EffectCardPeriod";
    if (FenSignItemClass == null) miss = miss + "|FenSignItem";
    if (ExclusiveCardUtilClass == null) miss = miss + "|ExclusiveCardUtil";
    if (miss !== "") { send(ctx, "AP_" + tag + "_ERR:no_class:" + miss); return false; }
    return true;
}

/** 找到目标玩家(自身 / 真实玩家 / bot);找不到时报 ERR 并返回 null */
function zhaoResolve(ctx, tag, p, nameText, what) {
    if (nameText == null || nameText === "" || nameText === "-") return p;
    var t = teruFindPlayer(ctx, nameText);
    if (t == null) { send(ctx, "AP_" + tag + "_ERR:" + (what == null ? "no_player" : what) + ":" + nameText); return null; }
    return t;
}

/** 基线:装风水师立牌 + 骰子 + 主手铁剑;清卡/白泽/厄运/计时器/出牌轮 */
function doZhaoPrep(ctx, tag, clearText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e0) { /* 忽略 */ }
    zhaoResetRound(p);
    var err = zhaoClearState(p);
    var signErr = equipSign(p, ZHAO_SIGN_ID);
    var diceErr = "unknown_item:" + ZHAO_DICE_ID;
    var diceItem = resolveItem(ZHAO_DICE_ID);
    if (diceItem != null) diceErr = putInSlot(p, "dice", new ItemStack(diceItem), 0);
    var swordErr = "";
    try { p.setItemInHand(ZhaoHandClass.MAIN_HAND, new ItemStack(resolveItem("minecraft:iron_sword"))); }
    catch (e1) { swordErr = exText(e1); }
    try { ModAttachments.setFenRecharge(p, 0); } catch (e2) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_PREP:sign_err=" + (signErr == null ? "" : signErr)
        + ":dice_err=" + (diceErr == null ? "" : diceErr)
        + (swordErr === "" ? "" : ":sword_err=" + swordErr)
        + ":clear=" + (("" + clearText) === "clear" ? 1 : 0)
        + ":nmobs=" + zhaoNum(function () { return p.level.getEntitiesOfClass(LivingEntityClass, AABBClass.ofSize(p.position(), 64, 64, 64)).size(); })
        + ":online=" + zhaoOnlineNames(ctx) + ":" + zhaoStateRead(p));
    return 1;
}

/** 只读全量读数(缺省自身;给名字则读那个真实玩家/bot;找不到 ⇒ found=0,不报 ERR) */
function doZhaoRead(ctx, tag, phase, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var listN = zhaoOnlineNames(ctx).split(",").length;
    var t = p;
    if (nameText != null && nameText !== "" && nameText !== "-") {
        t = teruFindPlayer(ctx, nameText);
        if (t == null) {
            send(ctx, "AP_" + tag + "_" + phase + ":found=0:who=" + nameText
                + ":list=" + listN + ":online=" + zhaoOnlineNames(ctx));
            return 1;
        }
    }
    send(ctx, "AP_" + tag + "_" + phase + ":found=1:who=" + (t === p ? "self" : nameText)
        + ":uuid=" + zhaoUuidText(t) + ":list=" + listN
        + ":online=" + zhaoOnlineNames(ctx) + ":" + zhaoStateRead(t));
    return 1;
}

/** 注册与冻结数值(物品/常量/效果/选择器动作/标签/cardByTypeId) */
function doZhaoReg(ctx, tag) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var signItem = resolveItem(ZHAO_SIGN_ID);
    var fuItem = resolveItem(ZHAO_FU_ID);
    var huoItem = resolveItem(ZHAO_HUO_ID);
    var items = ":zhao_item=" + (signItem == null ? "MISSING" : "ok")
        + ":fu_item=" + (fuItem == null ? "MISSING" : "ok")
        + ":huo_item=" + (huoItem == null ? "MISSING" : "ok");

    var cls = ":zhao_is=" + ((signItem != null && (signItem instanceof ZhaoSignItemClass)) ? 1 : 0)
        + ":fu_is=" + ((fuItem != null && (fuItem instanceof FuCardItemClass)) ? 1 : 0)
        + ":huo_is=" + ((huoItem != null && (huoItem instanceof HuoCardItemClass)) ? 1 : 0);

    // ⚠️ 标签判定不得走 ItemStack#is(TagKey)(Rhino 重载歧义)⇒ 读 Holder#tags() 按 location() 比字符串
    var tags = "", tagErr = "";
    try {
        var arr = BuiltInRegistries.ITEM.wrapAsHolder(signItem).tags().toArray();
        var signs = 0, stand = 0;
        for (var i = 0; i < arr.length; i++) {
            var loc = "" + arr[i].location();
            if (loc === "astral_dice:signs") signs = 1;
            if (loc === "curios:stand") stand = 1;
        }
        tags = ":signs_tag=" + signs + ":curios_stand_tag=" + stand;
    } catch (e1) { tags = ":tag_err=" + exText(e1); tagErr = exText(e1); }

    var byType = "", byTypeErr = "";
    try {
        var fuByType = BaseEffectCardItemClass.cardByTypeId("fu_card");
        var huoByType = BaseEffectCardItemClass.cardByTypeId("huo_card");
        // 反证:未识别类型必须回退成王之力 ⇒ 上面两条命中说明两张符卡已真正接入映射表(不是回退值)
        var unknownByType = BaseEffectCardItemClass.cardByTypeId("no_such_card_type");
        byType = ":byType_fu=" + itemIdOf(fuByType) + ":byType_huo=" + itemIdOf(huoByType)
            + ":byType_unknown=" + itemIdOf(unknownByType);
    } catch (e2) { byType = ":byType_err=" + exText(e2); byTypeErr = exText(e2); }

    var acts = "", actErr = "";
    try {
        var a1 = TargetSelectionRegistryClass.get(ZhaoSignItemClass.ACTION_ID);
        var a2 = TargetSelectionRegistryClass.get("fu_card");
        var a3 = TargetSelectionRegistryClass.get("huo_card");
        acts = ":zhao_type=" + ("" + a1.targetType()) + ":zhao_self=" + zhaoBool(function () { return a1.allowSelf(); })
            + ":zhao_r=" + teruR1(a1.radius())
            + ":fu_type=" + ("" + a2.targetType()) + ":fu_self=" + zhaoBool(function () { return a2.allowSelf(); })
            + ":fu_r=" + teruR1(a2.radius())
            + ":huo_type=" + ("" + a3.targetType()) + ":huo_self=" + zhaoBool(function () { return a3.allowSelf(); })
            + ":huo_r=" + teruR1(a3.radius());
    } catch (e3) { acts = ":act_err=" + exText(e3); actErr = exText(e3); }

    send(ctx, "AP_" + tag + "_REG:zhao_id=" + ZhaoSignItemClass.SIGN_ID
        + ":zhao_action=" + ZhaoSignItemClass.ACTION_ID
        + ":fu_action=" + FuCardItemClass.ACTION_ID + ":fu_heal=" + FuCardItemClass.HEAL_AMOUNT
        + ":huo_action=" + HuoCardItemClass.ACTION_ID + ":huo_dmg=" + HuoCardItemClass.DAMAGE
        + ":huo_period=" + HuoCardItemClass.CURSE_PERIOD_TICKS
        + ":zhao_fx=" + DESC_ZHAO_BLESSING + ":mis_fx=" + DESC_MISFORTUNE
        + ":zhao_dur=" + ZhaoBlessingEffectClass.DURATION_TICKS + ":mis_dur=" + MisfortuneEffectClass.DURATION_TICKS
        + ":fen_max=" + FenSignItemClass.MAX_RECHARGE
        + items + cls + tags + byType + acts
        + (tagErr === "" ? "" : ":tag_ex=1")
        + (byTypeErr === "" ? "" : ":bytype_ex=1")
        + (actErr === "" ? "" : ":act_ex=1")
        + ":" + zhaoStateRead(p));
    return 1;
}

/** 给自身/指定真实玩家 n 张(绑定受赠者) */
function doZhaoGive(ctx, tag, kindText, nText, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var kind = ("" + kindText) === "huo" ? "huo" : "fu";
    var n = teruInt(nText, 1);
    var t = zhaoResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var id = kind === "huo" ? ZHAO_HUO_ID : ZHAO_FU_ID;
    var before = zhaoCountInvAndOff(t, id);
    zhaoGiveCard(t, t, kind, n);
    send(ctx, "AP_" + tag + "_GIVE:kind=" + kind + ":who=" + (t === p ? "self" : nameText) + ":n=" + n
        + ":count=" + before + ">" + zhaoCountInvAndOff(t, id) + ":" + zhaoStateRead(t));
    return 1;
}

/**
 * 符卡-祸张数设为 n(主栏 + 副手全清后再给,并立即镜像厄运)。
 * 同时给出**计时器解耦**证据:`timer=<改前>><改后>` 与 `timer_same=1`(张数在 >0 区间内变化,
 * 周期伤害计时器必须原值不动 —— 起算/推进只由 HuoCardItem#tick 负责)。
 */
function doZhaoSetHuo(ctx, tag, nText, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = zhaoResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var n = teruInt(nText, 0);
    var timer0 = zhaoNum(function () { return ModAttachments.getHuoCardNextDamageTick(t); });
    var removed = zhaoClearItem(t, ZHAO_HUO_ID);
    if (n > 0) HuoCardItemClass.give(t, t, n);
    HuoCardItemClass.refreshCurseState(t);
    var timer1 = zhaoNum(function () { return ModAttachments.getHuoCardNextDamageTick(t); });
    send(ctx, "AP_" + tag + "_SETHUO:removed=" + removed + ":want=" + n
        + ":count=" + zhaoCountInvAndOff(t, ZHAO_HUO_ID) + ":who=" + (t === p ? "self" : nameText)
        + ":timer=" + timer0 + ">" + timer1 + ":timer_same=" + (timer0 === timer1 ? 1 : 0)
        + ":" + zhaoStateRead(t));
    return 1;
}

/** 清指定玩家的卡 + 白泽/厄运状态 + 计时器 + 出牌轮 + 养精蓄锐(脚手架:把该玩家拉回同一基线) */
function doZhaoClear(ctx, tag, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = zhaoResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var err = zhaoClearState(t);
    zhaoResetRound(t);
    try { ModAttachments.setFenRecharge(t, 0); } catch (e0) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_CLEAR:who=" + (t === p ? "self" : nameText)
        + (err === "" ? "" : ":err=" + err) + ":" + zhaoStateRead(t));
    return 1;
}

/**
 * 目标解析:`self` = 自身;`mob` = 现造一只敌对生物(spider);其余 = 玩家名。
 * 支持两种写法:`zhaocast <tag> <self|mob>` 与 `zhaocast <tag> <玩家名>`(第二参直接当目标名,
 * 因为 Brigadier 的 name 是可选参数,2 参写法下该位置必然是目标名)。返回 {t, label, isMob}。
 */
function zhaoResolveCastTarget(ctx, tag, p, modeText, nameText) {
    var mode = "" + modeText;
    var who = (nameText == null) ? "" : ("" + nameText);
    if (mode === "self") return { t: p, label: "self", isMob: false };
    if (mode === "mob") {
        var m = spawnDummy(p, "minecraft:spider", 3);
        return { t: m, label: "mob", isMob: true };
    }
    if (who === "") who = mode;   // 2 参写法:`zhaocast <tag> Bot1`
    var t = zhaoResolve(ctx, tag, p, who, "no_player");
    return { t: t, label: (t == null ? "-" : who), isMob: false };
}

/**
 * 服务端权威入口 applyBlessing(自身 / 真实玩家 / 生物)。
 * self ⇒ ok=1(自选放行);玩家名 ⇒ ok=1(真实第二名玩家);mob ⇒ ok=0(非玩家必须被拒)。
 * 完美帮手判据用 `t_fen_delta`(调用前后差值)而非绝对值 —— 大当家立牌的被动层数会自行增长。
 */
function doZhaoCast(ctx, tag, modeText, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var tt = zhaoResolveCastTarget(ctx, tag, p, modeText, nameText);
    if (tt.t == null) return 1;
    var target = tt.t;
    var fu0 = zhaoNum(function () { return FuCardItemClass.countFu(p); });
    var huo0 = zhaoNum(function () { return HuoCardItemClass.count(p); });
    var t0 = "", fen0 = -1;
    if (!tt.isMob) {
        t0 = zhaoStateRead(target);
        fen0 = zhaoNum(function () { return ModAttachments.getFenRecharge(target); });
    }
    var ok = -1, err = "";
    try { ok = ZhaoSignItemClass.applyBlessing(p, target) ? 1 : 0; } catch (e1) { ok = -2; err = exText(e1); }
    send(ctx, "AP_" + tag + "_CAST:mode=" + tt.label + ":ok=" + ok
        + ":caster_fu=" + fu0 + ">" + zhaoNum(function () { return FuCardItemClass.countFu(p); })
        + ":caster_huo=" + huo0 + ">" + zhaoNum(function () { return HuoCardItemClass.count(p); })
        + (err === "" ? "" : ":err=" + err) + ":self={" + zhaoStateRead(p) + "}");
    if (!tt.isMob && target !== p) {
        send(ctx, "AP_" + tag + "_CAST_T:mode=" + tt.label
            + ":t_fen_delta=" + (zhaoNum(function () { return ModAttachments.getFenRecharge(target); }) - fen0)
            + ":before={" + t0 + "}:after={" + zhaoStateRead(target) + "}");
    }
    if (tt.isMob) { try { target.discard(); } catch (e2) { /* 忽略 */ } }
    return 1;
}

/**
 * 真实主动键路径:performSkillForCurio(只开启选择会话)→ confirm(目标)。
 * 目标写法同 {@link doZhaoCast}(`self` / 玩家名)。两次调用之间取消会话,保证干净。
 */
function doZhaoGate(ctx, tag, modeText, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var tt = zhaoResolveCastTarget(ctx, tag, p, modeText, nameText);
    if (tt.t == null) return 1;
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e0) { /* 忽略 */ }
    var session = 0, token = -1, confirmErr = "";
    try {
        BaseSignItemClass.performSkillForCurio(p);
        session = TargetSelectionManagerClass.isSelecting(p) ? 1 : 0;
        token = TargetSelectionManagerClass.sessionTokenForTests(p);
        TargetSelectionManagerClass.confirm(p, token, tt.t.getId());
    } catch (e1) { confirmErr = exText(e1); }
    var tAfter = "";
    if (!tt.isMob && tt.t !== p) {
        tAfter = ":t_after={" + zhaoStateRead(tt.t) + "}";
    }
    send(ctx, "AP_" + tag + "_GATE:mode=" + tt.label + ":session=" + session + ":token_seen=" + (token > 0 ? 1 : 0)
        + (confirmErr === "" ? "" : ":confirm_err=" + confirmErr)
        + ":self={" + zhaoStateRead(p) + "}" + tAfter);
    if (tt.isMob) { try { tt.t.discard(); } catch (e2) { /* 忽略 */ } }
    return 1;
}

/** 骰神赐福 施加/移除(下降沿驱动源;name 缺省=自身)
 *
 *  ① 施加:走**原版命令** `/effect give <name> astral_dice:dice_blessing 6000 0`(经 runCmdP)——
 *     不得改用 `new MobEffectInstance(ModEffects.X, …)`:1.20.1 上 `ModEffects.X` 是 Forge
 *     `RegistryObject`,KubeJS 会对它做**注册表强转**(`RegistryInfo.wrap` → `UtilsJS.getMCID`)
 *     并抛 `ResourceLocationException` / `NPE: No such element with id null in registry
 *     minecraft:mob_effect` —— 该异常**逃出** JS 的 try/catch(2026-09-20 实测:整条读数丢失、
 *     命令报 Brigadier 异常),`guard`/`zhaoBool` 都兜不住。
 *  ② 移除:必须走**内部通道** `ModEffectRemoval.remove`(前置库)。实测产品侧
 *     `event/ModEffectEvents#onModEffectRemovalPrevented`(HIGH)会把一切**外部**移除
 *     `astral_dice:*` 效果的事件 `setCanceled(true)`(牛奶 / `/effect clear` 都清不掉)⇒
 *     `/effect clear` 在本模组效果上恒为空操作。该拦截本身由用例另行断言(读数仍 =1)。
 *  ③ 读数一律用 `findEffect(p, descId)`(纯字符串匹配):`p.hasEffect(ModEffects.X)` 在 1.20.1
 *     上会踩同一个注册表强转坑。
 */
function doZhaoBless(ctx, tag, modeText, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = zhaoResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var who = "?";
    try { who = "" + t.getName().getString(); } catch (e0) { who = "?"; }
    var mode = ("" + modeText) === "give" ? "give" : "clear";
    var how = mode, rc = "";
    if (mode === "give") {
        // 原版命令通道(读回的 rc=rc=1 是 runCmdP 自带前缀 + performPrefixedCommand 返回值)
        try { rc = "" + runCmdP(p, "effect give " + who + " astral_dice:dice_blessing 6000 0"); }
        catch (e1) { how = "give_ex:" + exText(e1); }
    } else {
        // 内部移除通道(外部 /effect clear 会被产品拦截器取消,见上方注释)
        try { ModEffectRemoval.remove(t, teruBlessing()); }
        catch (e2) { how = "clear_ex:" + exText(e2); }
    }
    send(ctx, "AP_" + tag + "_BLESS:how=" + how + ":who=" + (t === p ? "self" : nameText)
        + ":rc=" + rc + ":" + zhaoStateRead(t));
    return 1;
}

/** 给真实玩家(bot)装备风水师立牌 / 大当家立牌 / 卸下 stand 槽 */
function doZhaoSign(ctx, tag, nameText, modeText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = zhaoResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var mode = "" + modeText;
    var itemId = mode === "zhao" ? ZHAO_SIGN_ID : (mode === "fen" ? ZHAO_FEN_SIGN_ID : "");
    var itemErr = "";
    if (itemId !== "" && resolveItem(itemId) == null) itemErr = "unknown_item:" + itemId;
    var err = itemErr === "" ? zhaoSetStand(t, itemId) : itemErr;
    if (err == null) err = "";
    var zhaoEq = zhaoBool(function () { return ZhaoSignItemClass.isEquipped(t); });
    var fenEq = zhaoBool(function () { return FenSignItemClass.isEquipped(t); });
    send(ctx, "AP_" + tag + "_SIGN:who=" + nameText + ":mode=" + mode
        + (err === "" ? "" : ":err=" + err)
        + ":zhao_eq=" + zhaoEq + ":fen_eq=" + fenEq + ":" + zhaoStateRead(t));
    return 1;
}

/** 福祸相倚:直接走 onDiceRollResult(骰点 1/6)times 次(每次之间隔 1 个 tick 由用例的 wait 保证) */
function doZhaoDice(ctx, tag, diceText, timesText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var dice = teruInt(diceText, 1);
    var times = teruInt(timesText, 1);
    var fu0 = zhaoNum(function () { return FuCardItemClass.countFu(p); });
    var huo0 = zhaoNum(function () { return HuoCardItemClass.count(p); });
    var granted = "", err = "";
    for (var i = 0; i < times; i++) {
        var r = "null";
        try {
            // ⚠️ 生产链路在发牌前会先 tryClaimDiceJudgment(一次结算一次判定);此处逐次判定 ⇒ 每次都要重新占位,
            //    否则连续调用会被"同一 tick 去重"挡下(那是 zhaodedup 要测的语义,不能混进本命令)。
            ZhaoSignItemClass.tryClaimDiceJudgment(p);
            r = "" + ZhaoSignItemClass.onDiceRollResult(p, dice);
        } catch (e1) { r = "ex:" + exText(e1); err = exText(e1); }
        granted = granted + (i === 0 ? "" : ",") + r;
    }
    send(ctx, "AP_" + tag + "_DICE:dice=" + dice + ":times=" + times + ":granted=" + granted
        + ":fu=" + fu0 + ">" + zhaoNum(function () { return FuCardItemClass.countFu(p); })
        + ":huo=" + huo0 + ">" + zhaoNum(function () { return HuoCardItemClass.count(p); })
        + (err === "" ? "" : ":err=" + err) + ":" + zhaoStateRead(p));
    return 1;
}

/** 同一 tick 两次判定:第一次 tryClaimDiceJudgment=true,第二次必须 false(不再发牌) */
function doZhaoDedup(ctx, tag, diceText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var dice = teruInt(diceText, 1);
    var fu0 = zhaoNum(function () { return FuCardItemClass.countFu(p); });
    var huo0 = zhaoNum(function () { return HuoCardItemClass.count(p); });
    var c1 = -1, c2 = -1, g1 = "-", g2 = "-", err = "";
    try {
        c1 = ZhaoSignItemClass.tryClaimDiceJudgment(p) ? 1 : 0;
        if (c1 === 1) g1 = "" + ZhaoSignItemClass.onDiceRollResult(p, dice);
    } catch (e1) { g1 = "ex:" + exText(e1); err = exText(e1); }
    try {
        c2 = ZhaoSignItemClass.tryClaimDiceJudgment(p) ? 1 : 0;
        if (c2 === 1) g2 = "" + ZhaoSignItemClass.onDiceRollResult(p, dice);
    } catch (e2) { g2 = "ex:" + exText(e2); err = err + "|" + exText(e2); }
    send(ctx, "AP_" + tag + "_DEDUP:dice=" + dice + ":claim1=" + c1 + ":claim2=" + c2
        + ":g1=" + g1 + ":g2=" + g2
        + ":fu=" + fu0 + ">" + zhaoNum(function () { return FuCardItemClass.countFu(p); })
        + ":huo=" + huo0 + ">" + zhaoNum(function () { return HuoCardItemClass.count(p); })
        + (err === "" ? "" : ":err=" + err) + ":" + zhaoStateRead(p));
    return 1;
}

/**
 * 溢出治疗 → 攻击力:把 HP 压到 max-gap,再 heal(amount)。
 * exp_over = floor(max(0, amount - min(amount, 缺口)))(单次期望;余数由累加器跨次凑整,见 §14.6)。
 */
function doZhaoHeal(ctx, tag, amountText, gapText, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = zhaoResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var amount = Number("" + amountText);
    if (isNaN(amount)) amount = 0;
    var gap = Number("" + gapText);
    if (isNaN(gap)) gap = 0;
    zhaoSetGap(t, gap);
    var hp0 = teruR1(t.getHealth());
    var over0 = zhaoNum(function () { return ModAttachments.getZhaoOverflowBonus(t); });
    var rem0 = zhaoNum(function () { return ModAttachments.getZhaoOverflowRemainder(t); });
    var ap0 = zhaoNum(function () { return TeruDiceCombatModifiersClass.attackPowerOf(t); });
    var err = "";
    try { t.heal(amount); } catch (e1) { err = exText(e1); }
    var expOver = -1;
    try {
        var missing = Math.max(0, t.getMaxHealth() - hp0);
        var actual = Math.min(amount, missing);
        expOver = Math.floor(Math.max(0, amount - actual));
    } catch (e2) { expOver = -1; }
    var over1 = zhaoNum(function () { return ModAttachments.getZhaoOverflowBonus(t); });
    var rem1 = zhaoNum(function () { return ModAttachments.getZhaoOverflowRemainder(t); });
    var ap1 = zhaoNum(function () { return TeruDiceCombatModifiersClass.attackPowerOf(t); });
    send(ctx, "AP_" + tag + "_HEAL:who=" + (t === p ? "self" : nameText)
        + ":amount=" + amount + ":gap=" + gap + ":hp=" + hp0 + ">" + teruR1(t.getHealth())
        + ":exp_over=" + expOver
        + ":over=" + over0 + ">" + over1 + ":rem=" + teruR1(rem0) + ">" + teruR1(rem1)
        + ":ap=" + ap0 + ">" + ap1 + ":ap_delta=" + (ap1 - ap0)
        + (err === "" ? "" : ":err=" + err));
    return 1;
}

/**
 * 溢出治疗 → 攻击力**整条链**一次跑完(把 5 次事件压进一条命令,读数逐项可比):
 *   s1 = 满血 heal(10)      ⇒ 溢出 10(整数 +10)
 *   s2 = 缺口 4 时 heal(6)  ⇒ 溢出 2(只算超出缺口的部分)
 *   s3 = 满血 heal(0.5)     ⇒ 溢出 0.5 →余数 0.5(整数不动)
 *   s4 = 满血 heal(0.5)     ⇒ 余数凑整 ⇒ 整数 +1、余数归 0(§14.6 余数累加器)
 *   s5 = 缺口 4 时 heal(4)  ⇒ 刚好回满、溢出 0 ⇒ 不增加
 * 每项格式 `i:over:rem:ap_delta:exp_over`;末尾附目标全量读数。
 */
function doZhaoSeq(ctx, tag, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = zhaoResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var gaps = [0, 4, 0, 0, 4];
    var amounts = [10, 6, 0.5, 0.5, 4];
    var seq = "", err = "";
    for (var k = 0; k < 5; k++) {
        zhaoSetGap(t, gaps[k]);
        var hp0 = teruR1(t.getHealth());
        var over0 = zhaoNum(function () { return ModAttachments.getZhaoOverflowBonus(t); });
        var ap0 = zhaoNum(function () { return TeruDiceCombatModifiersClass.attackPowerOf(t); });
        try { t.heal(amounts[k]); } catch (e1) { err = err + "|s" + (k + 1) + ":" + exText(e1); }
        var exp = -1;
        try {
            var missing = Math.max(0, t.getMaxHealth() - hp0);
            exp = Math.floor(Math.max(0, amounts[k] - Math.min(amounts[k], missing)));
        } catch (e2) { exp = -1; }
        var ap1 = zhaoNum(function () { return TeruDiceCombatModifiersClass.attackPowerOf(t); });
        seq = seq + (k === 0 ? "" : ",") + (k + 1) + ":"
            + zhaoNum(function () { return ModAttachments.getZhaoOverflowBonus(t); }) + ":"
            + teruR1(zhaoNum(function () { return ModAttachments.getZhaoOverflowRemainder(t); })) + ":"
            + (ap1 - ap0) + ":" + exp;
    }
    send(ctx, "AP_" + tag + "_SEQ:who=" + (t === p ? "self" : nameText) + ":seq=" + seq
        + (err === "" ? "" : ":err=" + err) + ":" + zhaoStateRead(t));
    return 1;
}

/**
 * 符卡-福连续使用 times 次(每轮都重新发一张自绑定的牌,再走**服务端权威**入口 playFromSelector)。
 * 逐次读数 `ok/play/max/fubonus/cool` 串成一行 ⇒ 「消耗 1 / 返回 1」净 0、以及打满
 * `min(9, 1+extra)` 后进冷却、再多用必被拒,全部可在**一条读数**里核对。
 */
function doZhaoFuLoop(ctx, tag, timesText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var times = teruInt(timesText, 1);
    if (times < 1) times = 1;
    if (times > 15) times = 15;
    var seq = "", err = "";
    for (var i = 0; i < times; i++) {
        var ready = zhaoReadyCard(p, "fu", p);
        var ok = -1;
        try { ok = p.getMainHandItem().getItem().playFromSelector(p, p, p.getMainHandItem()) ? 1 : 0; }
        catch (e1) { ok = -2; err = err + "|i" + (i + 1) + ":" + exText(e1); }
        seq = seq + (i === 0 ? "" : ",") + (i + 1) + ":ok" + ok
            + "/p" + zhaoNum(function () { return EffectCardPeriodClass.getPlayCount(p); })
            + "/m" + zhaoNum(function () { return EffectCardPeriodClass.getMaxAllowed(p); })
            + "/f" + zhaoNum(function () { return ModAttachments.getFuCardCycleBonus(p); })
            + "/c" + zhaoBool(function () { return EffectCardPeriodClass.isCooldownActive(p); })
            + (ready === "" ? "" : "/r!" + ready);
    }
    send(ctx, "AP_" + tag + "_FULOOP:times=" + times + ":who=self:seq=" + seq
        + (err === "" ? "" : ":err=" + err) + ":" + zhaoStateRead(p));
    return 1;
}

/** 立即跑一次 HuoCardItem#tick(now = 原样;force = 先把到期刻推到上一刻以触发结算) */
function doZhaoHuoTick(ctx, tag, modeText, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = zhaoResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var mode = "" + modeText;
    var now = nowTickLevel(t.level);
    var next0 = zhaoNum(function () { return ModAttachments.getHuoCardNextDamageTick(t); });
    if (mode === "force") {
        try { ModAttachments.setHuoCardNextDamageTick(t, now - 1); } catch (e1) { /* 忽略 */ }
    }
    var hp0 = teruR1(t.getHealth());
    var err = "";
    try { HuoCardItemClass.tick(t); } catch (e2) { err = exText(e2); }
    var next1 = zhaoNum(function () { return ModAttachments.getHuoCardNextDamageTick(t); });
    send(ctx, "AP_" + tag + "_HUOTICK:mode=" + mode + ":who=" + (t === p ? "self" : nameText)
        + ":now=" + now + ":next=" + next0 + ">" + next1 + ":next_delta=" + (next1 > 0 ? (next1 - now) : -1)
        + ":hp=" + hp0 + ">" + teruR1(t.getHealth()) + ":dmg=" + teruR1(hp0 - t.getHealth())
        + (err === "" ? "" : ":err=" + err) + ":" + zhaoStateRead(t));
    return 1;
}

/** 把全部符卡-祸挪到 主手 / 副手 / 末影箱(容器口径实测) */
function doZhaoHuoBox(ctx, tag, modeText, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = zhaoResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var removed = zhaoClearItem(t, ZHAO_HUO_ID);
    var n = removed > 0 ? removed : 1;
    var mode = "" + modeText;
    var where = "", err = "";
    try {
        var item = resolveItem(ZHAO_HUO_ID);
        if (item == null) { err = "unknown_item"; }
        else if (mode === "off") {
            t.setItemInHand(ZhaoHandClass.OFF_HAND, new ItemStack(item, n));
            where = "offhand";
        } else if (mode === "ender") {
            t.getEnderChestInventory().setItem(0, new ItemStack(item, n));
            where = "enderchest";
        } else {
            t.setItemInHand(ZhaoHandClass.MAIN_HAND, new ItemStack(item, n));
            where = "mainhand";
        }
    } catch (e1) { err = exText(e1); }
    HuoCardItemClass.refreshCurseState(t);
    send(ctx, "AP_" + tag + "_HUOBOX:mode=" + mode + ":moved=" + removed + ":where=" + where
        + ":who=" + (t === p ? "self" : nameText)
        + (err === "" ? "" : ":err=" + err) + ":" + zhaoStateRead(t));
    return 1;
}

/**
 * 符卡-祸丢弃 —— 「禁止丢弃」已按用户指令整体移除 ⇒ **必须成功**。
 * 走 `ServerPlayer#drop(boolean)`(原版丢弃路径,内部正是 `selected.onDroppedByPlayer(this)` 的判定点):
 * 返回 true = 该次丢弃被放行,主手 −1、地面 +1(拾取延迟 40 tick,读数在同一命令内完成)。
 */
function doZhaoDrop(ctx, tag) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var hand0 = itemIdOf(p.getMainHandItem());
    var inv0 = zhaoCountInvAndOff(p, ZHAO_HUO_ID);
    var ground0 = teruGroundCount(p, ZHAO_HUO_ID, 8);
    var err = "";
    if (hand0 !== ZHAO_HUO_ID) { err = zhaoHoldItem(p, ZHAO_HUO_ID, 1); }
    var dropped = -1;
    if (err === "") {
        try { dropped = p.drop(true) ? 1 : 0; } catch (e1) { err = exText(e1); }
    }
    send(ctx, "AP_" + tag + "_DROP:hand_before=" + hand0 + ":inv=" + inv0 + ">" + zhaoCountInvAndOff(p, ZHAO_HUO_ID)
        + ":ground=" + ground0 + ">" + teruGroundCount(p, ZHAO_HUO_ID, 8)
        + ":dropped=" + dropped + (err === "" ? "" : ":err=" + err) + ":" + zhaoStateRead(p));
    return 1;
}

/** 选择器出牌前把牌备到主手(绑 owner;搬**原栈**以保留获得者组件);返回 "" = 就绪 */
function zhaoReadyCard(p, kind, owner) {
    var itemId = kind === "huo" ? ZHAO_HUO_ID : ZHAO_FU_ID;
    try { zhaoClearItem(p, itemId); } catch (e0) { /* 忽略 */ }
    if (kind === "huo") HuoCardItemClass.give(p, owner, 1); else FuCardItemClass.give(p, owner, 1);
    return zhaoMoveToMainHand(p, itemId);
}

/**
 * 出牌目标解析:`self` = 自身;`mob` = 现造一只敌对生物(spider,默认 16 血);其余 = 真实玩家/bot。
 * 返回 { t, label, isMob }(找不到玩家时 t = null)。
 */
function zhaoResolveTarget(ctx, tag, p, targetText, nameText) {
    var tt = "" + targetText;
    if (tt === "self") return { t: p, label: "self", isMob: false };
    if (tt === "mob") {
        var m = spawnDummy(p, "minecraft:spider", 3);
        return { t: m, label: "mob", isMob: true };
    }
    var t = zhaoResolve(ctx, tag, p, nameText, "no_player");
    return { t: t, label: (t == null ? "-" : "" + nameText), isMob: false };
}

/** 出牌 owner 解析(self = 自己;其余 = 真实玩家) */
function zhaoResolveOwner(ctx, tag, p, ownerText) {
    if (ownerText == null || ownerText === "" || ownerText === "self") return { o: p, label: "self" };
    var o = zhaoResolve(ctx, tag, p, ownerText, "no_owner");
    return { o: o, label: (o == null ? "-" : "" + ownerText) };
}

/**
 * 真实出牌路径:主手放牌 ⇒ tickHeldSelector(手持即开局)⇒ confirm(目标)。
 * owner 非本人 = 「非获得者使用」⇒ 开局前即被 ExclusiveCardUtil.canUse 拒(session=0、无治疗/伤害)。
 */
function doZhaoCard(ctx, tag, kindText, targetText, nameText, ownerText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var kind = ("" + kindText) === "huo" ? "huo" : "fu";
    var tt = zhaoResolveTarget(ctx, tag, p, targetText, nameText);
    if (tt.t == null) return 1;
    var target = tt.t;
    var oo = zhaoResolveOwner(ctx, tag, p, ownerText);
    if (oo.o == null) { if (tt.isMob) { try { target.discard(); } catch (e0) { /* 忽略 */ } } return 1; }
    var ready = zhaoReadyCard(p, kind, oo.o);
    var hand = itemIdOf(p.getMainHandItem());
    var canUse = zhaoBool(function () { return ExclusiveCardUtilClass.canUse(p, p.getMainHandItem()); });
    var play0 = zhaoNum(function () { return EffectCardPeriodClass.getPlayCount(p); });
    var max0 = zhaoNum(function () { return EffectCardPeriodClass.getMaxAllowed(p); });
    var fuB0 = zhaoNum(function () { return ModAttachments.getFuCardCycleBonus(p); });
    var cool0 = zhaoBool(function () { return EffectCardPeriodClass.isCooldownActive(p); });
    var hp0 = teruR1(target.getHealth());
    var fu0 = zhaoNum(function () { return FuCardItemClass.countFu(p); });
    var huo0 = zhaoNum(function () { return HuoCardItemClass.count(p); });
    var tState0 = tt.isMob ? "" : ("t0={" + zhaoStateRead(target) + "}");
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e1) { /* 忽略 */ }
    var session = 0, token = -1, confirmErr = "";
    try {
        BaseEffectCardItemClass.tickHeldSelector(p);
        session = TargetSelectionManagerClass.isSelecting(p) ? 1 : 0;
        token = TargetSelectionManagerClass.sessionTokenForTests(p);
        TargetSelectionManagerClass.confirm(p, token, target.getId());
    } catch (e2) { confirmErr = exText(e2); }
    var tState1 = tt.isMob ? "" : (":t_after={" + zhaoStateRead(target) + "}");
    send(ctx, "AP_" + tag + "_CARD:kind=" + kind + ":target=" + tt.label + ":owner=" + oo.label
        + ":hand=" + hand + ":can_use=" + canUse + (ready === "" ? "" : ":ready_err=" + ready)
        + ":session=" + session + ":token_seen=" + (token > 0 ? 1 : 0)
        + (confirmErr === "" ? "" : ":confirm_err=" + confirmErr)
        + ":t_hp=" + hp0 + ">" + teruR1(target.getHealth()) + ":t_alive=" + zhaoBool(function () { return target.isAlive(); })
        + ":fu=" + fu0 + ">" + zhaoNum(function () { return FuCardItemClass.countFu(p); })
        + ":huo=" + huo0 + ">" + zhaoNum(function () { return HuoCardItemClass.count(p); })
        + ":play=" + play0 + ">" + zhaoNum(function () { return EffectCardPeriodClass.getPlayCount(p); })
        + ":max=" + max0 + ">" + zhaoNum(function () { return EffectCardPeriodClass.getMaxAllowed(p); })
        + ":fubonus=" + fuB0 + ">" + zhaoNum(function () { return ModAttachments.getFuCardCycleBonus(p); })
        + ":cool=" + cool0 + ">" + zhaoBool(function () { return EffectCardPeriodClass.isCooldownActive(p); })
        + ":hand_after=" + itemIdOf(p.getMainHandItem())
        + (tState0 === "" ? "" : ":" + tState0) + tState1
        + ":self={" + zhaoStateRead(p) + "}");
    if (tt.isMob) { try { target.discard(); } catch (e3) { /* 忽略 */ } }
    return 1;
}

/**
 * 服务端权威出牌入口 `BaseEffectCardItem#playFromSelector`(不开会话、直连产物判定):
 * 专属校验 → 出牌锁 → 效果 → 出牌登记。非获得者 ⇒ ok=0 且目标无任何变化。
 */
function doZhaoPlay(ctx, tag, kindText, targetText, nameText, ownerText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var kind = ("" + kindText) === "huo" ? "huo" : "fu";
    var tt = zhaoResolveTarget(ctx, tag, p, targetText, nameText);
    if (tt.t == null) return 1;
    var target = tt.t;
    var oo = zhaoResolveOwner(ctx, tag, p, ownerText);
    if (oo.o == null) { if (tt.isMob) { try { target.discard(); } catch (e0) { /* 忽略 */ } } return 1; }
    var ready = zhaoReadyCard(p, kind, oo.o);
    var stack = p.getMainHandItem();
    var canUse = zhaoBool(function () { return ExclusiveCardUtilClass.canUse(p, stack); });
    var hp0 = teruR1(target.getHealth());
    var play0 = zhaoNum(function () { return EffectCardPeriodClass.getPlayCount(p); });
    var tState0 = tt.isMob ? "" : ("t0={" + zhaoStateRead(target) + "}");
    var ok = -1, err = "";
    // ⚠️ playFromSelector 是**实例**方法:必须经物品实例调用 —— 在类对象上调用会抛
    //    `InternalError: Java class "BaseEffectCardItem" has no public instance field or method named "playFromSelector"`
    //    (Rhino 只在类对象上查 static 方法)。
    try { ok = stack.getItem().playFromSelector(p, target, stack) ? 1 : 0; } catch (e1) { ok = -2; err = exText(e1); }
    send(ctx, "AP_" + tag + "_PLAY:kind=" + kind + ":target=" + tt.label + ":owner=" + oo.label
        + ":hand=" + itemIdOf(stack) + ":can_use=" + canUse + (ready === "" ? "" : ":ready_err=" + ready)
        + ":ok=" + ok
        + ":t_hp=" + hp0 + ">" + teruR1(target.getHealth())
        + ":play=" + play0 + ">" + zhaoNum(function () { return EffectCardPeriodClass.getPlayCount(p); })
        + ":max=" + zhaoNum(function () { return EffectCardPeriodClass.getMaxAllowed(p); })
        + ":fubonus=" + zhaoNum(function () { return ModAttachments.getFuCardCycleBonus(p); })
        + ":cool=" + zhaoBool(function () { return EffectCardPeriodClass.isCooldownActive(p); })
        + (err === "" ? "" : ":err=" + err)
        + (tState0 === "" ? "" : ":" + tState0)
        + (tt.isMob ? "" : (":t_after={" + zhaoStateRead(target) + "}")));
    if (tt.isMob) { try { target.discard(); } catch (e2) { /* 忽略 */ } }
    return 1;
}

/** §14.11:主手唤起选择器 / 副手不唤起(成对读数;末尾复位会话与手持) */
function doZhaoHand(ctx, tag, kindText, handText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var kind = ("" + kindText) === "huo" ? "huo" : "fu";
    var itemId = kind === "huo" ? ZHAO_HUO_ID : ZHAO_FU_ID;
    var hand = ("" + handText) === "off" ? "off" : "main";
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e0) { /* 忽略 */ }
    var err = "", ready = "";
    try {
        zhaoClearItem(p, itemId);
        if (hand === "off") {
            p.setItemInHand(ZhaoHandClass.MAIN_HAND, ItemStack.EMPTY);
            p.setItemInHand(ZhaoHandClass.OFF_HAND, new ItemStack(resolveItem(itemId)));
        } else {
            ready = zhaoHoldItem(p, itemId, 1);
        }
    } catch (e1) { err = exText(e1); }
    // 副手局:先把牌握在副手,**再**跑手持判定(主手为空 ⇒ 不应开局)
    var session = 0;
    try {
        BaseEffectCardItemClass.tickHeldSelector(p);
        session = TargetSelectionManagerClass.isSelecting(p) ? 1 : 0;
    } catch (e2) { err = err + "|tick:" + exText(e2); }
    send(ctx, "AP_" + tag + "_HAND:kind=" + kind + ":hand=" + hand
        + ":main=" + itemIdOf(p.getMainHandItem()) + ":off=" + itemIdOf(p.getOffhandItem())
        + ":session=" + session + (ready === "" ? "" : ":ready_err=" + ready)
        + (err === "" ? "" : ":err=" + err) + ":" + zhaoStateRead(p));
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e3) { /* 忽略 */ }
    try { zhaoClearItem(p, itemId); } catch (e4) { /* 忽略 */ }
    return 1;
}

/**
 * 外力移除白泽赐福效果(模拟 `/effect clear`)。
 * 效果被摘掉后,**下一 tick** 玩家级 tickBlessing 的自检分支应复位 active/skip 并回收溢出加成
 * (该 tick 由生产侧 PlayerTickEvents 驱动,探针不做任何补写 ⇒ 读数取自真实 tick 路径)。
 */
function doZhaoFx(ctx, tag, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = zhaoResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var err = "";
    try { ZhaoBlessingEffectClass.remove(t); } catch (e1) { err = exText(e1); }
    send(ctx, "AP_" + tag + "_FX:who=" + (t === p ? "self" : nameText) + ":removed=" + (err === "" ? 1 : 0)
        + ":fx_on_now=" + zhaoBool(function () { return ZhaoBlessingEffectClass.has(t); })
        + (err === "" ? "" : ":err=" + err) + ":" + zhaoStateRead(t));
    return 1;
}

/** 死亡清场 / 重登复位(产品同一入口;bot 的真实死亡另由用例 `/kill` 覆盖) */
function doZhaoLife(ctx, tag, modeText, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = zhaoResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var mode = "" + modeText;
    var err = "";
    try {
        if (mode === "relogin") ZhaoSignItemClass.onOwnerRelogin(t);
        else ZhaoSignItemClass.onOwnerDeathCleanup(t);
    } catch (e1) { err = exText(e1); }
    send(ctx, "AP_" + tag + "_LIFE:mode=" + mode + ":who=" + (t === p ? "self" : nameText)
        + ":called=" + (err === "" ? 1 : 0) + (err === "" ? "" : ":err=" + err) + ":" + zhaoStateRead(t));
    return 1;
}

/**
 * 心意相连:read = 只读候选集(未组队必须是 0);call = 调 onAllyActiveSkill(返回实际发牌人数)。
 * name 缺省 = 自身(施法者侧);给名字则把该真实玩家当作大当家立牌持有者。
 */
function doZhaoLink(ctx, tag, modeText, nameText) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var owner = zhaoResolve(ctx, tag, p, nameText, "no_player");
    if (owner == null) return 1;
    var mode = ("" + modeText) === "call" ? "call" : "read";
    var gate = zhaoBool(function () { return ZhaoSignItemClass.teamGateOpen(owner); });
    var ids = [];
    try { ids = ZhaoSignItemClass.linkedReceiverIds(owner); } catch (e1) { ids = []; }
    var granted = -1, err = "";
    if (mode === "call") {
        try { granted = ZhaoSignItemClass.onAllyActiveSkill(owner); } catch (e2) { granted = -2; err = exText(e2); }
    }
    // 只读:把候选 UUID 映射成在线玩家名
    var nameList = "-";
    try {
        var all = p.level.getServer().getPlayerList().getPlayers();
        var acc = "";
        for (var i = 0; i < all.size(); i++) {
            var q = all.get(i);
            var qid = zhaoUuidText(q);
            for (var j = 0; j < ids.size(); j++) {
                if (("" + ids.get(j)) === qid) {
                    var qn = "?";
                    try { qn = "" + q.getName().getString(); } catch (e3) { qn = "?"; }
                    acc = (acc === "") ? qn : (acc + "," + qn);
                }
            }
        }
        if (acc !== "") nameList = acc;
    } catch (e4) { nameList = "?"; }
    send(ctx, "AP_" + tag + "_LINK:mode=" + mode + ":who=" + (owner === p ? "self" : nameText)
        + ":team_gate=" + gate + ":links=" + ids.size() + ":link_names=" + nameList
        + ":granted=" + granted + (err === "" ? "" : ":err=" + err) + ":" + zhaoStateRead(owner));
    return 1;
}

/** 收尾:清卡/效果/状态/计时器 + 卸立牌 + 清主手 + 复位出牌轮 */
function doZhaoClearAll(ctx, tag) {
    if (!zhaoClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var err = zhaoClearState(p);
    var standErr = zhaoSetStand(p, "");
    try { TargetSelectionManagerClass.cancelSessionForTests(p); } catch (e1) { /* 忽略 */ }
    zhaoResetRound(p);
    try { ModAttachments.setFenRecharge(p, 0); } catch (e2) { /* 忽略 */ }
    try { p.setItemInHand(ZhaoHandClass.MAIN_HAND, ItemStack.EMPTY); } catch (e3) { /* 忽略 */ }
    send(ctx, "AP_" + tag + "_CLEARALL:cleared=1" + (err === "" ? "" : ":err=" + err)
        + (standErr === "" ? "" : ":stand_err=" + standErr) + ":" + zhaoStateRead(p));
    return 1;
}
// ════════════════════════════════════════════════════════════════════════════
//  绿洲女王立牌(nardis)主动「女王特权」+ 被动「威压」游戏内取证(2026-09-27)
//
//  规格:docs/features/nardis-sign-spec.md §7.1(命令表)/ §7.2(断言清单)/ §7.3(覆盖边界)。
//  ⚠️ 2026-09-27 用户**二次裁决**：主动不再进入冻结(无「使用中」锁定态)、再次释放**叠加**新牌
//     并**重置**效果时长为 3:00、「牌用光」**不再**提前结束效果。本文件的注释已按新语义改写；
//     旧冻结机制的读数(N1–N8)与用例已作废，现行断言契约见 spec §7.4 的 **M1–M8**
//     (M8 = 临时牌上限 9 张)。
//
//  命令一览(读数前缀 AP_<tag>_;读数一律**单行**、字段顺序固定,用例按子串断言):
//     /astralprobe nardiprep <tag> [clear] [name]     装绿洲女王立牌 + 骰子(**curios `dice` 槽**,见注 1)
//     /astralprobe nardiread <tag> <phase> [name]     全量只读读数
//     /astralprobe nardiarm <tag> <atk_n> <def_n> [where]  直接写骰子 applied_stones 并读 +ap/+dp
//                                                     (where = curio 缺省 / hand / both;atk_n<0 ⇒ 只读)
//     /astralprobe nardiarmclear <tag>                清空骰子卡牌栏(装配 0/0;两处骰子都清)
//     /astralprobe nardicast <tag> [name] [reset]     走**真实主动** BaseSignItem.performSkillForCurio
//                                                     (第 2 参 = 玩家名,第 3 参 = reset;见注 5)
//     /astralprobe nardigive <tag> <n> [name]         直接 TemporaryCardUtil.grantRandom(绕开冷却)+ 补效果
//     /astralprobe narditemp <tag> <inv|assemble|foil> [clear]  造/清临时牌
//                                                     (inv = 主手带标记卡;assemble = 骰子装一个 temporary 石;
//                                                      foil = 带标记牌在手上 + 同 id 普通牌在 0 号槽,一次读出两者光效)
//     /astralprobe nardifoil <tag>                    读主手光效真值(临时牌 1 / 普通牌 0)
//     /astralprobe nardidrop <tag>                    ServerPlayer#drop(true)(主手为临时牌)
//     /astralprobe nardislot <tag>                    直接验证 mixin 真身:Slot#mayPlace
//     /astralprobe nardiexpire <tag> [name]           移除 nardis_privilege(内部通道)并驱动一次自检
//     /astralprobe nardinuclear <tag>                 收尾:清临时牌 + 清效果 + 卸立牌 + 清主手 + 复位冷却
//
//  ── 立项期/本轮实测到的、**必须写进报告**的契约事实(以下均以「两线逐字节相同」为前提的规避) ──
//   1. **骰子的唯一有效位置 = curios `dice` 槽**(2026-09-20 实测纠正上一轮的写法):
//      两线的 `TemporaryCardUtil#findEquippedDice` / `NardisSignItem#equippedEnhancement` /
//      `DiceCombatModifiers#attackPowerOf|defensePowerOf` / `NardisSignItem#onCurioTick`
//      **全部、且只**查 Curios(两线都注册了独立的 curios `dice` 槽:1.21.1 走
//      `data/astral_dice/curios/slots/*.json`,1.20.1 走 `AstralDiceMod` 的 IMC `SlotTypeMessage`)。
//      ⇒ 上一轮把骰子放**主手**时:① 被动恒为 0(读数 `ap=1` 就是玩家属性原值);
//      ② 探针写进主手骰子的 `weapon_enhancement` 与产品清理的 curios 骰子**不是同一份**
//      (实测 `AP_ASM1_TEMP:asm_err=no_dice`)。⇒ 本段一律把骰子放 curios `dice` 槽
//      (与既有树探针 `teruprep` 同法),主手只用于「光效/丢弃」这类需要手上拿着牌的用例。
//   2. **效果判据**:一律 `findEffect(p, "effect.astral_dice.nardis_privilege")` 字符串匹配 ——
//      1.20.1 的 `ModEffects` 是 `RegistryObject`,把常量塞进 `hasEffect(Holder)` 会抛注册表强转异常
//      且逃出 try/catch(踩坑先例见 zhao 段)。本段把常量经 `domEffectHolder()` 归一为 `Holder`.
//   3. 本段**不含任何分支代码**:两线差异一律靠「字符串匹配 / 反射式调用 / 逐项 try」规避。
//   4. **物品数据键的两线形态不同,必须走双形态助手**(2026-09-27 主代理 `javap` 取证后追加):
//      1.21.1 的 `ModDataComponents.WEAPON_ENHANCEMENT` 是 `DeferredHolder<DataComponentType<?>,…>`
//      (组件键 = `.get()`,读法 = `ItemStack#getOrDefault(ComponentType, T)`);
//      1.20.1 的是前置库 `ItemDataKey<WeaponEnhancement>`(**只有** `get(ItemStack)` /
//      `getOrDefault(ItemStack,T)` / `set(ItemStack,T)` / `remove(ItemStack)`,**没有** 0 参 `get()`;
//      1.20.1 的 `ItemStack` 也没有组件版 `getOrDefault/set(ComponentType,T)` —— 那是 1.20.5+ 的 API)。
//      ⇒ 裸调用在 1.20.1 上会抛异常并被 try/catch 静默吞掉(读数恒 -1,断言全数失配、无任何报错线索)。
//      故本段一律走 {@link nardiEnhOf} / {@link nardiSetEnh} 两个双形态助手,并在读数里用
//      `set_ok=` 显式暴露写回是否成功 —— **禁止**在本段再写裸的 `.get()` / `ItemStack#set(...)`。
//   5. **命令参数位**(2026-09-20 实测纠正):`nardicast` 的签名是 **`<tag> [name] [reset]`**
//      —— 第 2 参是玩家名位。上一轮用例写成 `nardicast TRIGW reset` 把 `reset` 喂进了玩家名位,
//      于是实测 `AP_TRIGW_CAST:found=0:who=reset`。凡要复位冷却须写全三参:`nardicast <tag> self reset`。
//   6. **脚手架发牌必须自带效果**(2026-09-20 实测):产品 tick 自检口径是「有临时牌但无效果 ⇒ 清空」
//      (spec §4.4,正确设计);`nardigive` / `narditemp ... inv|foil` 是纯脚手架、原本不发效果
//      ⇒ 实测「给牌命令的读数里 `n_temp=3`,下一条读命令就 `n_temp=0`」。修法 = 发牌前调
//      {@link domEnsureEffect} 补一条与产品**逐字同参**的 3:00 效果(读数 `fx_made` 显式暴露)。
//   7. **异常文本必须纯字符串化**:共享段 `exText` 的 `"" + e` 在某些 KubeJS 异常上会**自身抛错**
//      (消息里嵌了不可 JSON 化的记录类型)⇒ 读数只发一半、`:err=` 泄漏成 KubeJS 的
//      `Unsure how to convert 'WeaponEnhancement[...]' to JSON!`。本段一律用 {@link domExText}。
// ════════════════════════════════════════════════════════════════════════════

var NardisSignItemClass = teruLoadCls("com.merlinkitsune.astral_dice.item.sign.NardisSignItem");
var NardisTemporaryCardUtilClass = teruLoadCls("com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil");
var NardisPrivilegeEffectClass = teruLoadCls("com.merlinkitsune.astral_dice.effect.NardisPrivilegeEffect");
var NardisCardItemClass = teruLoadCls("com.merlinkitsune.astral_dice.item.card.CardItem");
var NardisCardRegistryClass = teruLoadCls("com.merlinkitsune.astral_dice.combat.CardRegistry");
var NardisModDataComponentsClass = teruLoadCls("com.merlinkitsune.astral_dice.component.ModDataComponents");
var NardisWeaponEnhancementClass = teruLoadCls("com.merlinkitsune.astral_dice.component.WeaponEnhancement");
var NardisAppliedStoneClass = teruLoadCls("com.merlinkitsune.astral_dice.component.AppliedStone");
var NardisSlotClass = teruLoadCls("net.minecraft.world.inventory.Slot");
var NardisInventoryClass = teruLoadCls("net.minecraft.world.entity.player.Inventory");
var NardisSimpleContainerClass = teruLoadCls("net.minecraft.world.SimpleContainer");
var NardiHandClass = teruLoadCls("net.minecraft.world.InteractionHand");
/** 效果时长守卫(与产品 `NardisSignItem#handleUse` 同一入口);读不到时退回裸 `player.addEffect` */
var NardiEffectTimerGuardClass = teruLoadCls("com.merlinkitsune.astral_dice.event.EffectTimerGuard");
/**
 * `DataComponentPatch` / `DataComponentPatch.Builder` —— **写组件的唯一可用通道**。
 *
 * <p>⚠️ 为什么不能直接 `stack.set(key, value)`(2026-09-20 实测,证据见 `nardidbg`):
 * KubeJS 给 `ItemStack` 注册了一个**扩展方法 `set`**(签名 `(Context, DataComponentType, Object)`),
 * Rhino 在解析 `dice.set(key, 我的WeaponEnhancement)` 时会优先命中它,进而在
 * `kjs$set` 里对值做「Java 对象 → JSON」转换 —— 本模组的 `WeaponEnhancement` 是自研 record、
 * 不可 JSON 化 ⇒ 抛 `KubeRuntimeException: Unsure how to convert 'WeaponEnhancement[...]' to JSON!`,
 * 表现为 `set_ok=0` 且读数没有任何可归因信息(整个读数行还会被 KubeJS 重新包装)。
 * **产品代码不受影响**(Java 里 `ItemStack#set` 是直接调用,不经 Rhino)。
 * `applyComponents(DataComponentPatch)` 没有扩展方法覆盖,是干净通道。
 */
var NardisDataComponentPatchClass = teruLoadCls("net.minecraft.core.component.DataComponentPatch");
/** `CompoundTag` / `ListTag`:可 JSON 化的组件值形态(用于绕开 KubeJS 的 Java→JSON 转换失败) */
var NardisCompoundTagClass = teruLoadCls("net.minecraft.nbt.CompoundTag");
var NardisListTagClass = teruLoadCls("net.minecraft.nbt.ListTag");
/** R1/R2 回归取证(`nardiguard`)需要现场放一个真实潜影盒 */
var NardisBlockPosClass = teruLoadCls("net.minecraft.core.BlockPos");
var NardisDirectionClass = teruLoadCls("net.minecraft.core.Direction");
var NardisBlocksClass = teruLoadCls("net.minecraft.world.level.block.Blocks");
/** 专属牌判据(产品提供;N1「全部非专属」断言用) */
var NardisRandomCardHandlerClass = teruLoadCls("com.merlinkitsune.astral_dice.item.card.RandomCardHandler");

var NARDIS_SIGN_ID = "astral_dice:nardis_sign";
var NARDIS_DICE_ID = "astral_dice:dice";
var NARDIS_ATK_CARD_ID = "astral_dice:attack_card_medium";
var NARDIS_DEF_CARD_ID = "astral_dice:defense_card_medium";
/** R1/R2 回归取证用的**效果牌**(R1/R2 只在效果牌上暴露) */
var NARDIS_EFFECT_CARD_ID = "astral_dice:effect_card_king_power";
var NARDIS_PILL_CHIP_ID = "astral_dice:vitamin_pill_chip";
var NARDIS_TERU_SIGN_ID = "astral_dice:teru_sign";
/** 女王特权效果(唯一真值)的描述 id —— 只做字符串匹配 */
var DESC_NARDIS_PRIVILEGE = "effect.astral_dice.nardis_privilege";
/** 装配临时牌用的**真实**卡类型(已核对 CardRegistry 两线同名:`medium` / `defense_medium`) */
var NARDIS_TEMP_TYPE = "medium";
/** 装配栏「另一个」临时石用的人造类型名(未注册 ⇒ CardRegistry.cost 回退 1) */
var NARDIS_TEMP_TYPE2 = "nardis_probe_temp";
/** 立牌主动冷却键名(spec §6.2:玩家级 sign_active_cooldown_end) */
var NARDIS_SIGN_ID_TEXT = "astral_dice:nardis_sign";

/** 逐项 try 的数值读数("-1" = 该访问器在本线不可用,不冒充 0) */
function domNum(fn) {
    try { return fn(); } catch (e) { return -1; }
}

/** 逐项 try 的布尔读数(0/1) */
function domBool(fn) {
    try { return fn() ? 1 : 0; } catch (e) { return -1; }
}

/**
 * 异常 → **纯字符串**(与共享段 `exText` 的差别:本函数**保证**返回 string,绝不把异常对象泄漏给调用方)。
 *
 * <p>为什么必须是**纯的**:共享段 `exText` 是 `var msg; try { msg = "" + e } catch (x) { msg = "<unprintable>" }`
 * —— 若某个异常对象的 `toString()` **自身抛错**(KubeJS 的 `KubeRuntimeException` 在消息里嵌了不可 JSON 化的
 * 记录类型时会走这条),`"" + e` 抛出 ⇒ 整个 `exText` 抛出 ⇒ `doNardiArm` 的 `catch (e1) { err = domExText(e1); }`
 * **再次抛出**,读数只发到一半(`set_ok=` 还没拼进去),异常逃到 `send()` 里被 KubeJS 重新包装成
 * `Unsure how to convert 'WeaponEnhancement[...]' to JSON!`(2026-09-20 实测:1.21.1 的 `AP_ARM1_ARM`
 * 只发了 `:sign=1:err=JavaException: …KubeRuntimeException: Unsure how to convert …` 就断了,
 * `set_ok=`/`before=`/`after=` 全缺 ⇒ `/:err=/` 的 absent 断言被误伤)。
 */
function domExText(e) {
    if (e === null || e === undefined) return "null";
    var msg = "<unprintable>";
    var s = null;
    try { s = "" + e; } catch (x) { s = null; }
    if (s !== null && s !== undefined) { msg = "" + s; }
    var cls = "";
    try { var c = e.getClass(); if (c != null) cls = "" + c.getName(); } catch (x2) { cls = ""; }
    try {
        if (typeof e.getStackTrace === "function") {
            var st = e.getStackTrace();
            var n = st.length < 3 ? st.length : 3;
            for (var i = 0; i < n; i++) msg += " @ " + st[i];
        }
    } catch (x3) { /* 栈不可得就只留消息 */ }
    return ("[" + cls + "] " + msg).replace(/[\r\n\t]+/g, " ").substring(0, 600);
}

/** Curios `dice` 槽里的骰子栈(空栈返回 null;**只查 Curios**,与产品 `findEquippedDice` 同源) */
function domCurioDice(p) {
    if (p == null) return null;
    var h = null;
    try { h = curioHandler(p, "dice"); } catch (e0) { return null; }
    if (h == null) return null;
    try {
        var st = h.getStacks().getStackInSlot(0);
        if (st == null || st.isEmpty()) return null;
        return st;
    } catch (e1) { return null; }
}

/** 主手骰子栈(空栈返回 null) */
function domHandDice(p) {
    try {
        var st = p.getMainHandItem();
        if (st == null || st.isEmpty()) return null;
        return itemIdOf(st) === NARDIS_DICE_ID ? st : null;
    } catch (e) { return null; }
}

/** 读某个骰子栈的 enhancement(双形态容错;失败返回 null) */
function domEnhOf(dice) {
    if (dice == null || NardisModDataComponentsClass == null || NardisWeaponEnhancementClass == null) return null;
    return nardiEnhOf(dice);
}

/** `domStateRead` 的 `n_stones` 字段就是从这里算的 —— 显式暴露「读的是哪一份骰子」 */
function domEnhSrc(p) {
    if (domHandDice(p) != null) return "hand";
    if (domCurioDice(p) != null) return "curio";
    return "none";
}

/**
 * 读骰子上的 `weapon_enhancement` —— **双形态容错**(两线逐字节相同的前提)。
 *
 * <p>为什么不能用裸调用:两线的「物品数据键」形态根本不同,而探针段不许分线分支 ——
 * <ul>
 *   <li>1.21.1:`WEAPON_ENHANCEMENT` 是 `DeferredHolder<DataComponentType<?>,…>`,组件键 = `…get()`,
 *       读法是 `ItemStack#getOrDefault(ComponentType, T)`;</li>
 *   <li>1.20.1:`WEAPON_ENHANCEMENT` 是前置库的 `ItemDataKey<WeaponEnhancement>`
 *       (**只有** `get(ItemStack)` / `getOrDefault(ItemStack,T)` / `set(ItemStack,T)` / `remove(ItemStack)`,
 *       **没有** 0 参 `get()`),且 1.20.1 的 `ItemStack` 也没有组件版 `getOrDefault(ComponentType,T)`
 *       (那是 1.20.5+ 的 API)。</li>
 * </ul>
 * 故依次试两种形态,任一成功即返回;都失败返回 null(由调用方给出可归因读数,不静默)。
 */
function nardiEnhOf(dice) {
    var out = null;
    try { out = dice.getOrDefault(NardisModDataComponentsClass.WEAPON_ENHANCEMENT.get(), NardisWeaponEnhancementClass.EMPTY); }
    catch (e1) { out = null; }
    if (out == null) {
        try { out = NardisModDataComponentsClass.WEAPON_ENHANCEMENT.getOrDefault(dice, NardisWeaponEnhancementClass.EMPTY); }
        catch (e2) { out = null; }
    }
    return out;
}

/**
 * 把 `WeaponEnhancement` 序列化成**可 JSON 化的 JS 对象**(字段名 = 组件 CODEC 的 snake_case 键)。
 *
 * <p>为什么必须是这个形态见 {@link nardiSetEnh} 的注释。
 */
function domEnhToJs(enh) {
    var stones = [];
    try {
        var list = enh.appliedStones();
        if (list != null) {
            for (var i = 0; i < list.size(); i++) {
                var s = list.get(i);
                if (s == null) continue;
                stones.push({
                    type: "" + s.type(),
                    uses: (s.uses() - 0),
                    temporary: (s.temporary() ? true : false)
                });
            }
        }
    } catch (eS) { /* 拿不到就当空表(由调用方的 set_ok/读数暴露) */ }
    return {
        used_cost: (enh.usedCost() - 0),
        max_cost: (enh.maxCost() - 0),
        used_defense_cost: (enh.usedDefenseCost() - 0),
        max_defense_cost: (enh.maxDefenseCost() - 0),
        star_level: (enh.starLevel() - 0),
        applied_stones: stones
    };
}

/**
 * 写回骰子的 `weapon_enhancement` —— 双形态容错,**成功返回 true**(失败不静默:读数里的
 * `set_ok` / `enh_curio` / `n_stones` 会同时暴露)。
 *
 * <p>⚠️⚠️ **1.21.1 侧必须传「JS 对象」而不是 Java `WeaponEnhancement` 实例**(2026-09-20 逐项取证):
 * KubeJS 给 `ItemStack` 注册了一个**扩展方法 `set(Context, DataComponentType, Object)`**,
 * Rhino 在解析 `dice.set(key.get(), 我的record)` 时优先命中它,进而在 `kjs$set` 内部做
 * 「Java 对象 → JSON」转换 —— 本模组自研 record 不可 JSON 化 ⇒
 * `KubeRuntimeException: Unsure how to convert 'WeaponEnhancement[...]' to JSON!`。
 * 实测对照(`/astralprobe nardidbg <tag>` 的读数):
 * <ul>
 *   <li>`dice.set(key.get(), record)` ⇒ 抛该 KubeRuntimeException;</li>
 *   <li>`dice.set(key, record)`(key 不 `.get()`)⇒ 同错;</li>
 *   <li>`WEAPON_ENHANCEMENT.set(dice, record)` ⇒ `Cannot find function set`(DeferredHolder 上没有);</li>
 *   <li>`dice.kjs$set(...)` ⇒ `Cannot find function kjs$set`(Rhino 不暴露带 `$` 的方法名);</li>
 *   <li>`DataComponentPatch.Builder.set(...)` ⇒ 同 KubeRuntimeException(`set` 这个名字被劫持);</li>
 *   <li>`CompoundTag` 作值 ⇒ `Unsure how to convert '0' to JSON!`(int 也不能转);</li>
 *   <li><b>`dice.set(key.get(), {used_cost:…, applied_stones:[…]})` ⇒ **成功**</b>
 *       (KubeJS 把 JS 对象转 JSON 后按组件 CODEC 解码,写出真组件;实测 `read_back=used=1/n=0`)。</li>
 * </ul>
 * 结论:**产品代码不受影响**(Java 侧 `ItemStack#set` 是直接调用、不经 Rhino;已核对
 * `DiceCombatEvents:328/716/749/865`、`TemporaryCardUtil:284`、`AnvilUpgradeHandler:133` 全是直接调用),
 * 这是「KubeJS 探针写组件」的通道限制,与本模组功能无关。
 *
 * <p>1.20.1 侧照旧走 `ItemDataKey#set(ItemStack, T)`(纯 Java、无 JSON 转换);
 * 该分支上 0 参 `.get()` 不存在会先抛错,自然落到第二条。
 */
function nardiSetEnh(dice, enh) {
    if (dice == null || enh == null) return false;
    // ① 1.21.1:`ItemStack#set(ComponentType, <JS 对象>)` —— 唯一实测可用的写通道
    try { dice.set(NardisModDataComponentsClass.WEAPON_ENHANCEMENT.get(), domEnhToJs(enh)); return true; }
    catch (e1) { /* 落到 1.20.1 形态 */ }
    // ② 1.20.1:`ItemDataKey#set(ItemStack, T)`
    try { NardisModDataComponentsClass.WEAPON_ENHANCEMENT.set(dice, enh); return true; }
    catch (e2) { return false; }
}

/** 该玩家主手是否拿着骰子(读数里显式给出,替代「骰子到底在不在 curios 槽」的猜测) */
function domDiceInHand(p) {
    try { return itemIdOf(p.getMainHandItem()) === NARDIS_DICE_ID ? 1 : 0; } catch (e) { return -1; }
}

/**
 * 读取**产品语义上的**骰子栈:`Curios` 的 `dice` 槽优先(唯一退路 = 主手)。
 *
 * <p>⚠️ 为什么必须 **curio 优先**(2026-09-20 实测,上一轮探针的致命错误):
 * 两线的 `TemporaryCardUtil#findEquippedDice` / `NardisSignItem#equippedEnhancement` /
 * `DiceCombatModifiers#attackPowerOf|defensePowerOf` **全部只查 Curios**(1.20.1 的
 * `CuriosCompat.getCuriosInventory` 不是「主手」——两线都注册了独立的 curios `dice` 槽);
 * 把骰子放在**主手**时这些入口一律读不到(实测基线 `ap=1`=玩家属性原值、`n_stones` 恒 0),
 * 更糟的是探针写进主手骰子的 `weapon_enhancement` 与产品清理的 curios 骰子**根本不是同一份**
 * ⇒ 上一轮 `AP_ASM1_TEMP` 的 `asm_err=no_dice` 即由此而来(不是产品缺陷)。
 * 主手只作退路:让「骰子不在 curios 时的读数」仍然可归因,不静默变成 -1。
 */
function domDiceStack(p) {
    var c = domCurioDice(p);
    if (c != null && itemIdOf(c) === NARDIS_DICE_ID) return c;
    return domHandDice(p);
}

/** 骰子上当前的武器强化(WEAPON_ENHANCEMENT;缺失 = EMPTY 语义) */
function domEnh(p) {
    return domEnhOf(domDiceStack(p));
}

/** 已装配卡牌数(攻击 / 防御),全部从骰子组件现算(与产品同源) */
function domStoneCounts(p) {
    var enh = domEnh(p);
    var a = 0, d = 0, t = 0;
    if (enh == null || NardisCardRegistryClass == null) return { atk: -1, def: -1, total: -1, temp: -1 };
    try {
        var stones = enh.appliedStones();
        if (stones != null) {
            for (var i = 0; i < stones.size(); i++) {
                var s = stones.get(i);
                if (s == null || s.type() == null) continue;
                var defense = false;
                try { defense = NardisCardRegistryClass.isDefense(s.type()); } catch (e1) { defense = false; }
                if (defense) { d = d + 1; } else { a = a + 1; }
                var tmp = false;
                try { tmp = s.temporary(); } catch (e2) { tmp = false; }
                if (tmp) t = t + 1;
            }
        }
    } catch (e) { return { atk: -1, def: -1, total: -1, temp: -1 }; }
    return { atk: a, def: d, total: a + d, temp: t };
}

/** 骰子装配栏当前费用(攻击 used_cost / 防御 used_defense_cost) */
function domUsedCost(p) {
    var enh = domEnh(p);
    if (enh == null) return { atk: -1, def: -1 };
    try { return { atk: enh.usedCost(), def: enh.usedDefenseCost() }; } catch (e) { return { atk: -1, def: -1 }; }
}

/** 把 ModEffects 常量归一为 `Holder<MobEffect>`(1.21.1 = DeferredHolder 自身;1.20.1 = RegistryObject#get) */
function domEffectHolder() {
    var c = ModEffects.NARDIS_PRIVILEGE;
    try { var g = c.get(); if (g != null) return g; } catch (e1) { /* 落到下面 */ }
    return c;
}

/** 女王特权效果的「在不在 / 剩余 tick」(只读;判据 = 描述 id 字符串匹配) */
function domFx(p) {
    var inst = findEffect(p, DESC_NARDIS_PRIVILEGE);
    if (inst == null) return { on: 0, dur: -1 };
    try { return { on: 1, dur: inst.getDuration() }; } catch (e) { return { on: 1, dur: -1 }; }
}

/** 身上(主物品栏 0..35 + 副手)带临时标记的卡牌总张数 */
function domCountTemp(p) {
    var n = 0;
    try {
        var items = p.getInventory().items;
        for (var i = 0; i < items.size(); i++) {
            var st = items.get(i);
            if (st == null || st.isEmpty()) continue;
            if (NardisTemporaryCardUtilClass != null && NardisTemporaryCardUtilClass.isTemporary(st)) n = n + st.getCount();
        }
        var off = p.getInventory().offhand;
        for (var j = 0; j < off.size(); j++) {
            var st2 = off.get(j);
            if (st2 == null || st2.isEmpty()) continue;
            if (NardisTemporaryCardUtilClass != null && NardisTemporaryCardUtilClass.isTemporary(st2)) n = n + st2.getCount();
        }
    } catch (e) { return -1; }
    return n;
}

/**
 * 玩家 8 格范围内的**临时牌地面掉落物**数(N1「地面掉落物 0」的主判据)。
 *
 * <p>口径与既有的 `teruGroundCount`(teru 段,本文件之外)逐字一致,只把过滤条件换成
 * {@code TemporaryCardUtil.isTemporary(栈)} ——因为临时牌的 id 是**随机**的(9 张攻击 /
 * 若干防御 / 效果),按 id 逐个查不可行。
 *
 * <p>`ItemEntityClass` / `AABBClass` 是 teru 段在**同一脚本文件顶层**建立的全局类引用
 * (函数体在命令触发时才求值,顶层赋值早已完成)⇒ 这里直接用;为防将来段序调整,缺引用时返回
 * {@code -1}(读数里显式可见,不会被误当 0)。
 */
function domGroundTemp(p) {
    if (typeof ItemEntityClass === "undefined" || typeof AABBClass === "undefined") return -1;
    if (NardisTemporaryCardUtilClass == null) return -1;
    var n = 0;
    try {
        var list = p.level.getEntitiesOfClass(ItemEntityClass,
            AABBClass.ofSize(p.position(), 8, 8, 8));
        for (var i = 0; i < list.size(); i++) {
            var st = null;
            try { st = list.get(i).getItem(); } catch (e1) { st = null; }
            if (st == null || st.isEmpty()) continue;
            if (NardisTemporaryCardUtilClass.isTemporary(st)) n = n + st.getCount();
        }
    } catch (e) { return -1; }
    return n;
}

/** 骰子装配栏里的临时牌张数(产品侧现算) */
function domCountTempEquipped(p) {
    if (NardisTemporaryCardUtilClass == null) return -1;
    try { return NardisTemporaryCardUtilClass.countTemporaryEquipped(p); } catch (e) { return -1; }
}

/** 清空 curios `dice` 槽(返回错误串或 "") */
function domClearCurioDice(p) {
    var h = null;
    try { h = curioHandler(p, "dice"); } catch (e0) { return "curio_dice:" + domExText(e0); }
    if (h == null) return "no_slot:dice";
    try { h.getStacks().setStackInSlot(0, ItemStack.EMPTY); } catch (e1) { return "curio_dice_set:" + domExText(e1); }
    return "";
}

/** 把一张骰子放进 curios `dice` 槽(返回错误串或 "") */
function domPutDiceInCurio(p, diceItem) {
    var h = null;
    try { h = curioHandler(p, "dice"); } catch (e0) { return "curio_dice:" + domExText(e0); }
    if (h == null) return "no_slot:dice";
    try {
        h.getStacks().setStackInSlot(0, ItemStack.EMPTY);
        h.getStacks().setStackInSlot(0, new ItemStack(diceItem, 1));
    } catch (e1) { return "curio_dice_set:" + domExText(e1); }
    return "";
}

/**
 * 把 `enh` 写进指定位置的骰子。
 *
 * <p>`where`:`curio`(缺省,**产品语义上的「已装备/已佩戴」**)/ `hand`(对照:证明「主手骰子对产品不可见」)/
 * `both`(两处都写,防线漂移)。
 *
 * <p>⚠️ 两个必须做对的地方(2026-09-20 实测各踩一次):
 * <ol>
 *   <li>**curios 槽必须先取出来改、再 `setStackInSlot` 写回** —— `getStacks().getStackInSlot(0)`
 *       在 Curios 的 handler 上拿到的是**副本**(直接 `.set()` 只改副本,写回丢失),
 *       实测表现为 `set_ok_curio=0`、`n_stones` 恒 0、被动恒不生效;</li>
 *   <li>**写之前先清空另一处同 id 的骰子** —— 否则两份带 enhancement 的骰子并存,
 *       读数读哪一份取决于产品内部顺序 ⇒ 断言变成顺序相关。</li>
 * </ol>
 * 返回 `{curio, hand, err}`:1 = 写成功,0 = 尝试过但失败,-1 = 未尝试 / 该处没有骰子。
 */
function domWriteEnh(p, where, enh) {
    var diceItem = resolveItem(NARDIS_DICE_ID);
    var out = { curio: -1, hand: -1, err: "" };
    if (diceItem == null) { out.err = "unknown_item:" + NARDIS_DICE_ID; return out; }
    var wantCurio = (where === "curio" || where === "both");
    var wantHand = (where === "hand" || where === "both");
    if (!wantCurio) {
        var ec = domClearCurioDice(p);
        if (ec !== "" && ec !== "no_slot:dice") out.err = out.err + "|" + ec;
    }
    if (!wantHand) {
        try { if (itemIdOf(p.getMainHandItem()) === NARDIS_DICE_ID) p.setItemInHand(NardiHandClass.MAIN_HAND, ItemStack.EMPTY); } catch (e1) { out.err = out.err + "|hand_clear:" + domExText(e1); }
    }
    if (wantCurio) {
        try {
            var h = curioHandler(p, "dice");
            if (h == null) { out.curio = -1; out.err = out.err + "|no_slot:dice"; }
            else {
                var stacks = h.getStacks();
                var c = stacks.getStackInSlot(0);
                if (c == null || c.isEmpty() || itemIdOf(c) !== NARDIS_DICE_ID) {
                    var ep = domPutDiceInCurio(p, diceItem);
                    if (ep !== "") { out.err = out.err + "|" + ep; }
                    c = stacks.getStackInSlot(0);
                }
                if (c == null || c.isEmpty() || itemIdOf(c) !== NARDIS_DICE_ID) { out.curio = 0; }
                else {
                    var okC = nardiSetEnh(c, enh);
                    stacks.setStackInSlot(0, c); // ★ 关键:改完必须写回槽位(副本语义)
                    out.curio = okC ? 1 : 0;
                }
            }
        } catch (e2) { out.curio = 0; out.err = out.err + "|curio_write:" + domExText(e2); }
    }
    if (wantHand) {
        try {
            var hd = domHandDice(p);
            if (hd == null) { p.setItemInHand(NardiHandClass.MAIN_HAND, new ItemStack(diceItem, 1)); hd = domHandDice(p); }
            if (hd == null) { out.hand = 0; } else { out.hand = nardiSetEnh(hd, enh) ? 1 : 0; }
        } catch (e3) { out.hand = 0; out.err = out.err + "|hand_write:" + domExText(e3); }
    }
    return out;
}

/**
 * `/astralprobe nardidbg <tag>` —— **纯只读**诊断:`weapon_enhancement` 组件的键形态与
 * `ItemStack#set` 在 Rhino 下的实际行为(2026-09-20 定位「写回恒失败」时加入)。
 *
 * <p>为什么需要它:`nardiSetEnh` 的双形态 try/catch 会把**真实异常吞掉**并返回 false,
 * 表现为 `set_ok=0` + `n_stones` 恒 0,完全看不出是哪一步失败。本命令逐项打印:
 * `key_get`(`WEAPON_ENHANCEMENT.get()` 是否可用/是否为 null)、
 * `key_ns`(是否也有 0 参 `get()` 之外的形态)、
 * `has_before`/`has_after`(`ItemStack#has/get` 在写前后的真值)、
 * `set1_ok`/`set1_err`(1.21.1 形态 `dice.set(key.get(), enh)`)、
 * `set2_ok`/`set2_err`(1.20.1 形态 `KEY.set(dice, enh)`)、
 * `set3_ok`/`set3_err`(`dice.set(key, enh)` —— key 不 `.get()`,用于验证 Rhino 的重载解析)。
 */
function doNardiDbg(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var out = [];
    var dice = domDiceStack(p);
    out.push("dice=" + (dice == null ? "null" : itemIdOf(dice)));
    var keyGet = null, keyGetErr = "";
    try { keyGet = NardisModDataComponentsClass.WEAPON_ENHANCEMENT.get(); }
    catch (e0) { keyGetErr = domExText(e0); }
    out.push("key_get=" + (keyGet == null ? "null" : "ok") + (keyGetErr === "" ? "" : ":key_get_err=" + keyGetErr));
    var keyRaw = null;
    try { keyRaw = NardisModDataComponentsClass.WEAPON_ENHANCEMENT; } catch (e1) { }
    out.push("key_raw=" + (keyRaw == null ? "null" : "obj"));
    if (dice == null) { send(ctx, "AP_" + tag + "_DBG:" + out.join(":")); return 1; }
    var enhNew = new NardisWeaponEnhancementClass(1, 12, 0, 12, 3, new ArrayListClass());
    out.push("enh_new=" + (enhNew == null ? "null" : "ok"));
    // 写前真值
    var hasBefore = -1, getBefore = "n/a";
    try { hasBefore = dice.has(keyGet) ? 1 : 0; } catch (e2) { hasBefore = -2; }
    try { var gb = dice.get(keyGet); getBefore = (gb == null ? "null" : "ok"); } catch (e3) { getBefore = "ex:" + domExText(e3); }
    out.push("has_before=" + hasBefore + ":get_before=" + getBefore);
    // set1:1.21.1 形态
    var s1 = -1, s1e = "";
    try { dice.set(keyGet, enhNew); s1 = 1; } catch (e4) { s1 = 0; s1e = domExText(e4); }
    var hasA1 = -1;
    try { hasA1 = dice.has(keyGet) ? 1 : 0; } catch (e5) { hasA1 = -2; }
    out.push("set1_ok=" + s1 + ":has_after1=" + hasA1 + (s1e === "" ? "" : ":set1_err=" + s1e));
    // set3:key 不 .get()
    var s3 = -1, s3e = "";
    try { dice.set(keyRaw, enhNew); s3 = 1; } catch (e6) { s3 = 0; s3e = domExText(e6); }
    var hasA3 = -1;
    try { hasA3 = dice.has(keyGet) ? 1 : 0; } catch (e7) { hasA3 = -2; }
    out.push("set3_ok=" + s3 + ":has_after3=" + hasA3 + (s3e === "" ? "" : ":set3_err=" + s3e));
    // set2:1.20.1 形态
    var s2 = -1, s2e = "";
    try { NardisModDataComponentsClass.WEAPON_ENHANCEMENT.set(dice, enhNew); s2 = 1; } catch (e8) { s2 = 0; s2e = domExText(e8); }
    var hasA2 = -1;
    try { hasA2 = dice.has(keyGet) ? 1 : 0; } catch (e9) { hasA2 = -2; }
    out.push("set2_ok=" + s2 + ":has_after2=" + hasA2 + (s2e === "" ? "" : ":set2_err=" + s2e));
    // set4:KubeJS 自己的 kjs$set(Context, DataComponentType, Object) —— 形参是 Object ⇒ 不会走扩展方法解析
    var s4 = -1, s4e = "";
    try { dice.kjs$set(null, keyGet, enhNew); s4 = 1; } catch (e10) { s4 = 0; s4e = domExText(e10); }
    var hasA4 = -1;
    try { hasA4 = dice.has(keyGet) ? 1 : 0; } catch (e11) { hasA4 = -2; }
    out.push("set4_ok=" + s4 + ":has_after4=" + hasA4 + (s4e === "" ? "" : ":set4_err=" + s4e));
    // set5:传 JS 对象(走 KubeJS 的 JSON→组件解析)
    var s5 = -1, s5e = "";
    try {
        dice.kjs$set(null, keyGet, {
            used_cost: 1, max_cost: 12, used_defense_cost: 0, max_defense_cost: 12,
            star_level: 3, applied_stones: []
        });
        s5 = 1;
    } catch (e12) { s5 = 0; s5e = domExText(e12); }
    var hasA5 = -1;
    try { hasA5 = dice.has(keyGet) ? 1 : 0; } catch (e13) { hasA5 = -2; }
    out.push("set5_ok=" + s5 + ":has_after5=" + hasA5 + (s5e === "" ? "" : ":set5_err=" + s5e));
    // 读回
    var back = "n/a";
    try { var rb = nardiEnhOf(dice); back = (rb == null ? "null" : ("used=" + rb.usedCost() + "/n=" + rb.appliedStones().size())); }
    catch (e14) { back = "ex:" + domExText(e14); }
    out.push("read_back=" + back);
    // set6:DataComponentPatch.Builder + applyComponents(绕开被 KubeJS 扩展方法劫持的 `set`)
    var s6 = -1, s6e = "";
    try {
        var pb = NardisDataComponentPatchClass.builder();
        pb.set(keyGet, enhNew);
        dice.applyComponents(pb.build());
        s6 = 1;
    } catch (e15) { s6 = 0; s6e = domExText(e15); }
    var back6 = "n/a";
    try { var rb6 = nardiEnhOf(dice); back6 = (rb6 == null ? "null" : ("used=" + rb6.usedCost() + "/n=" + rb6.appliedStones().size())); }
    catch (e16) { back6 = "ex:" + domExText(e16); }
    out.push("set6_ok=" + s6 + ":read_back6=" + back6 + (s6e === "" ? "" : ":set6_err=" + s6e));
    // set7:值换成 **JS 对象** —— 让 KubeJS 的 kjs$set 走「JSON → 组件」的正常解析路径
    var s7 = -1, s7e = "", back7 = "n/a";
    try {
        dice.set(keyGet, {
            used_cost: 1, max_cost: 12, used_defense_cost: 0, max_defense_cost: 12,
            star_level: 3, applied_stones: []
        });
        s7 = 1;
    } catch (e17) { s7 = 0; s7e = domExText(e17); }
    try { var rb7 = nardiEnhOf(dice); back7 = (rb7 == null ? "null" : ("used=" + rb7.usedCost() + "/n=" + rb7.appliedStones().size())); }
    catch (e18) { back7 = "ex:" + domExText(e18); }
    out.push("set7_ok=" + s7 + ":read_back7=" + back7 + (s7e === "" ? "" : ":set7_err=" + s7e));
    // set8:值换成 **CompoundTag**(同样可 JSON 化;字段名 = 组件 codec 的 snake_case 键)
    var s8 = -1, s8e = "", back8 = "n/a";
    try {
        var tag = new NardisCompoundTagClass();
        tag.putInt("used_cost", 1);
        tag.putInt("max_cost", 12);
        tag.putInt("used_defense_cost", 0);
        tag.putInt("max_defense_cost", 12);
        tag.putInt("star_level", 3);
        tag.put("applied_stones", new NardisListTagClass());
        dice.set(keyGet, tag);
        s8 = 1;
    } catch (e19) { s8 = 0; s8e = domExText(e19); }
    try { var rb8 = nardiEnhOf(dice); back8 = (rb8 == null ? "null" : ("used=" + rb8.usedCost() + "/n=" + rb8.appliedStones().size())); }
    catch (e20) { back8 = "ex:" + domExText(e20); }
    out.push("set8_ok=" + s8 + ":read_back8=" + back8 + (s8e === "" ? "" : ":set8_err=" + s8e));
    out.push("curio_handler=" + (curioHandler(p, "dice") == null ? "null" : "ok"));
    send(ctx, "AP_" + tag + "_DBG:" + out.join(":"));
    return 1;
}

/**
 * 主手物品的光效真值(clamp 到 0/1;-1 = 读不到)。
 *
 * <p>⚠️ 1.21.1 的 `ItemStack` **只有 `hasFoil()`、没有 `isFoil()`**(实测 `javap`:栈上是
 * `hasFoil()`;`isFoil(ItemStack)` 是**物品级**方法,产品覆写的正是它)⇒ 上一轮探针写
 * `stack.isFoil()` 直接抛错,读数恒 `-1`(表现为 `foil=-1`/`t_foil_hand=-1`/`foil_t=-1`)。
 * 本函数逐层回退,任一可用即返回。
 */
function domFoil(stack) {
    if (stack == null || stack.isEmpty()) return 0;
    try { return stack.hasFoil() ? 1 : 0; } catch (e1) { /* 下一层 */ }
    try { return stack.isFoil() ? 1 : 0; } catch (e2) { /* 下一层 */ }
    var it = null;
    try { it = stack.getItem(); } catch (e3) { it = null; }
    if (it != null) {
        try { return it.isFoil(stack) ? 1 : 0; } catch (e4) { /* 下一层 */ }
        try { return it.isFoil() ? 1 : 0; } catch (e5) { /* 下一层 */ }
    }
    return -1;
}

/**
 * 取玩家所在 `Level`。
 *
 * <p>⚠️ **Rhino 下 `Player#level()` 是属性不是方法**(实测 `Cannot call property level … It is not a function`)
 * ⇒ 必须写 `p.level`(不带括号);本函数再退回其它访问器。
 */
function domLevel(p) {
    var lv = null;
    try { lv = p.level; } catch (e1) { lv = null; }
    if (lv != null) return lv;
    try { lv = p.level(); } catch (e2) { lv = null; }
    if (lv != null) return lv;
    try { lv = p.getCommandSenderWorld(); } catch (e3) { lv = null; }
    return lv;
}

/**
 * 现场放一个**真实潜影盒方块实体**并返回 `{level, pos, be}`(调用方负责读数后 `removeBlock` 还原)。
 *
 * <p>为什么不用 `new SimpleContainer(9)` 造人造容器(2026-09-20 实测):`SimpleContainer` 有两个
 * 构造器 `(int)` 与 `(ItemStack...)`,Rhino 判不出目标重载会抛 `InternalError: ambiguous`,
 * 而**该异常逃出 `guard` 的 try/catch** ⇒ 整条 `nardislot` 命令**一行读数都不出**
 * (上一轮 `AP_S1_SLOT` 永不出现、且没有任何 `_EX`/报错线索,就是这条)。
 * 真实潜影盒既是 `Container`、又更贴近真实入箱路径。
 */
function domPlaceShulker(p) {
    var out = { level: null, pos: null, be: null, err: "" };
    out.level = domLevel(p);
    if (out.level == null) { out.err = "no_level"; return out; }
    try {
        out.pos = new NardisBlockPosClass(p.getBlockX(), p.getBlockY() + 3, p.getBlockZ());
        out.level.setBlock(out.pos, NardisBlocksClass.SHULKER_BOX.defaultBlockState(), 3);
        out.be = out.level.getBlockEntity(out.pos);
        if (out.be == null) out.err = "no_block_entity";
    } catch (e0) { out.err = "shulker:" + domExText(e0); }
    return out;
}

/** 还原 `domPlaceShulker` 放的方块(幂等) */
function domRemoveShulker(placed) {
    try {
        if (placed != null && placed.level != null && placed.pos != null) placed.level.removeBlock(placed.pos, false);
    } catch (e) { /* 还原失败不影响读数 */ }
    return "";
}

/** 清空身上的全部临时牌(脚手架;产品侧的清理走 purgeAll / tick 自检) */
function domClearTemp(p) {
    if (NardisTemporaryCardUtilClass == null) return -1;
    try { return NardisTemporaryCardUtilClass.purgeAll(p); } catch (e) { return -1; }
}

/** 临时牌的类型构成(`t_atk`/`t_def`/`t_types`,另加 2026-09-27 新语义所需的分类计数） */
function domTempBreakdown(p) {
    var a = 0, d = 0, types = "", excl = 0, total = 0;
    var scan = function (st) {
        if (st == null || st.isEmpty()) return;
        if (NardisTemporaryCardUtilClass == null || !NardisTemporaryCardUtilClass.isTemporary(st)) return;
        var ty = domCardType(st);
        if (ty === "defense") { d = d + 1; } else if (ty === "attack") { a = a + 1; }
        types = (types === "") ? ty : (types + "," + ty);
        total = total + 1;
        // N1:「全部非专属」——专属牌判据由产品提供(`RandomCardHandler#isExclusive`)
        try { if (NardisRandomCardHandlerClass != null && NardisRandomCardHandlerClass.isExclusive(st)) excl = excl + 1; } catch (eX) { /* 读不到就不计 */ }
    };
    try {
        var items = p.getInventory().items;
        for (var i = 0; i < items.size(); i++) scan(items.get(i));
        var off = p.getInventory().offhand;
        for (var j = 0; j < off.size(); j++) scan(off.get(j));
    } catch (e) { return { atk: -1, def: -1, types: "?", battle: -1, effect: -1, excl: -1, total: -1 }; }
    var battle = a + d;
    return { atk: a, def: d, types: (types === "" ? "-" : types), battle: battle, effect: total - battle, excl: excl, total: total };
}

/** 一张卡牌物品栈的攻击/防御归属("attack" / "defense" / "other") */
function domCardType(stack) {
    if (stack == null || stack.isEmpty() || NardisCardRegistryClass == null) return "other";
    try {
        var ty = NardisCardRegistryClass.itemToType(stack);
        if (ty == null) return "other";
        return NardisCardRegistryClass.isDefense(ty) ? "defense" : "attack";
    } catch (e) { return "other"; }
}

/** 骰子装配栏里是否有探针人造的那颗临时石 */
function domHasProbeStone(p) {
    var enh = domEnh(p);
    if (enh == null) return 0;
    try {
        var stones = enh.appliedStones();
        for (var i = 0; i < stones.size(); i++) {
            var s = stones.get(i);
            if (s != null && s.type() != null && s.type() === NARDIS_TEMP_TYPE2) return 1;
        }
    } catch (e) { return -1; }
    return 0;
}

/** 从骰子装配栏移除探针人造的那颗临时石(幂等;用于重复 assemble 不累积) */
function domRemoveProbeStone(p) {
    var enh = domEnh(p);
    if (enh == null || NardisWeaponEnhancementClass == null) return 0;
    try {
        var stones = enh.appliedStones();
        var kept = new ArrayListClass();
        var removed = 0;
        for (var i = 0; i < stones.size(); i++) {
            var s = stones.get(i);
            if (s != null && s.type() != null && s.type() === NARDIS_TEMP_TYPE2) { removed = removed + 1; continue; }
            kept.add(s);
        }
        if (removed <= 0) return 0;
        var freed = removed * 1;
        // 走 domWriteEnh(而不是裸 nardiSetEnh):curios 槽的栈是副本,必须写回槽位才生效
        var w = domWriteEnh(p, "curio", new NardisWeaponEnhancementClass(
            Math.max(0, enh.usedCost() - freed), enh.maxCost(),
            enh.usedDefenseCost(), enh.maxDefenseCost(),
            enh.starLevel(), kept));
        return (w.curio === 1) ? removed : -1;
    } catch (e) { return -1; }
}

/** 造一张临时牌物品栈(带标记) */
function domMakeTempCard(itemId) {
    var item = resolveItem(itemId);
    if (item == null) return null;
    var stack = new ItemStack(item, 1);
    if (NardisTemporaryCardUtilClass != null) {
        try { NardisTemporaryCardUtilClass.mark(stack); } catch (e) { /* 交给调用方读标记 */ }
    }
    return stack;
}

/**
 * 往骰子装配栏塞一颗 `temporary = true` 的卡牌石(spec §6.1 方案第 3 分量的探针侧可测性)。
 *
 * 生产侧的实际入口是卡牌栏 UI(`CardInventoryMenu#saveToDice`),GUI 鼠标操作无法注入,
 * 故此处**直接构造** AppliedStone 三参构造器并写回组件 —— 断言口径 = 「到期清理能否过滤 `temporary()`
 * 并按被移除卡重算 used_cost」,与 UI 路径共用同一条 `TemporaryCardUtil#purgeEquipped`。
 *
 * <p>返回 `{ok, why, enh}`:`ok=1` 只表示 record 构造成功,**写回是否成功由调用方用
 * {@link nardiSetEnh} 单独判定并写进读数** —— 这样 1.20.1 上写回失败不会再表现为静默的 `-1`。
 */
function domBuildTempStone(p, typeStr) {
    var dice = domDiceStack(p);
    var enh = domEnh(p);
    if (dice == null) return { ok: 0, why: "no_dice", enh: null };
    if (enh == null) return { ok: 0, why: "no_enhancement", enh: null };
    var cost = 1;
    try { cost = NardisCardRegistryClass.cost(typeStr, p); } catch (eC) { cost = 1; }
    var uses = 5;
    try { uses = NardisCardRegistryClass.defaultUses(typeStr); } catch (eU) { uses = 5; }
    var stone = null, err = "";
    try {
        stone = new NardisAppliedStoneClass(typeStr, uses, true);
    } catch (e1) {
        err = "new3:" + domExText(e1);
        try {
            stone = new NardisAppliedStoneClass(typeStr, uses);
            err = err + "|fallback2(临时标记丢失)";
        } catch (e2) { err = err + "|new2:" + domExText(e2); }
    }
    if (stone == null) return { ok: 0, why: err, enh: null };
    try {
        var kept = new ArrayListClass();
        var stones = enh.appliedStones();
        for (var i = 0; i < stones.size(); i++) kept.add(stones.get(i));
        kept.add(stone);
        return { ok: 1, why: err, cost: cost, enh: new NardisWeaponEnhancementClass(
            enh.usedCost() + cost, enh.maxCost(),
            enh.usedDefenseCost(), enh.maxDefenseCost(), enh.starLevel(), kept) };
    } catch (e3) { return { ok: 0, why: "build:" + domExText(e3), enh: null }; }
}

/** 清空主背包 0..35(基线用;不动副手 —— 副手留给 in_hand/off 类读数) */
function domClearMain(p) {
    try {
        for (var i = 0; i < 36; i++) p.getInventory().setItem(i, ItemStack.EMPTY);
    } catch (e) { return "clear_main:" + domExText(e); }
    return "";
}

/** 主动冷却剩余 tick(0 = 未在冷却) */
function domCool(p) {
    try {
        var end = ModAttachments.getSignActiveCooldownEnd(p);
        var now = nowTick(p);
        if (end <= 0 || now < 0) return 0;
        return end - now;
    } catch (e) { return -1; }
}

/** 复位立牌主动态:玩家级冷却 + 锁定键(基线用;不碰效果)。
 *  ⚠️ nardis 新语义下**不再使用锁定态**(`lock`/`lock_end` 应恒为 0)⇒ 本函数对 nardis 实际只清冷却;
 *  叠加类用例(M2)请优先用作用域更小的 `nardicd`(只写冷却)。 */
function domResetActive(p) {
    try { ModAttachments.setSignActiveCooldownEnd(p, 0); } catch (e1) { /* 忽略 */ }
    try { resetActiveLock(p); } catch (e2) { /* 忽略 */ }
}

/**
 * nardis 全量只读读数(**单行**,字段顺序固定;用例按子串断言)。
 *
 * 字段顺序(不得重排 —— 用例的 `A.*B` 形态子串断言依赖它):
 *   n_eq,atk_cards,def_cards,n_temp,n_temp_eq,dice_hand,n_stones,enh_src,
 *   n_fx,n_fx_on,t_atk,t_def,t_types,t_foil_hand,
 *   ap,dp,armor,pill,teru,foil,cool
 */
function domStateRead(p) {
    var eq = 0;
    if (NardisSignItemClass != null) eq = domBool(function () { return NardisSignItemClass.isEquipped(p); });
    var ac = 0, dc = 0;
    if (NardisSignItemClass != null) {
        ac = domNum(function () { return NardisSignItemClass.equippedAttackCardCount(p); });
        dc = domNum(function () { return NardisSignItemClass.equippedDefenseCardCount(p); });
    } else {
        ac = -1; dc = -1;
    }
    var ns = domStoneCounts(p);
    var br = domTempBreakdown(p);
    var fx = domFx(p);
    var pill = -1;
    try { pill = HealingManagerClass.getPoints(p); } catch (eP) { pill = -1; }
    var teru = -1;
    if (TeruSignItemClass != null) {
        try { teru = TeruSignItemClass.getLayers(p); } catch (eT) { teru = -1; }
    }
    return "n_eq=" + eq
        + ":atk_cards=" + ac
        + ":def_cards=" + dc
        + ":n_temp=" + domCountTemp(p)
        + ":n_temp_eq=" + domCountTempEquipped(p)
        + ":dice_hand=" + domDiceInHand(p)
        + ":n_stones=" + ns.total
        + ":enh_src=" + domEnhSrc(p)
        + ":n_fx=" + fx.dur
        + ":n_fx_on=" + fx.on
        + ":t_atk=" + br.atk
        + ":t_def=" + br.def
        + ":t_types=" + br.types
        + ":t_foil_hand=" + domFoil(p.getMainHandItem())
        + ":ap=" + (TeruDiceCombatModifiersClass == null ? -1 : domNum(function () { return TeruDiceCombatModifiersClass.attackPowerOf(p); }))
        + ":dp=" + (TeruDiceCombatModifiersClass == null ? -1 : domNum(function () { return TeruDiceCombatModifiersClass.defensePowerOf(p); }))
        + ":armor=" + domNum(function () { return p.getArmorValue(); })
        + ":pill=" + pill
        + ":teru=" + teru
        + ":foil=" + domFoil(p.getMainHandItem())
        + ":cool=" + domCool(p)
        // ── 新语义(安全门 / 临时牌分类计数 / 锁定读数)所需的追加字段。
        //    锁定字段(`lock`/`lock_end`)在 nardis 新语义下**应恒为 0** —— 冻结机械已撤回,
        //    这里保留只读读数是为了让「无锁定」成为**可断言**的事实(而不是靠"没有这个字段")。
        //    ⚠️ **一律追加在末尾**:前面所有字段的顺序与含义保持不变,旧断言(子串/前缀)不受影响。
        + ":lock=" + domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); })
        + ":lock_end=" + domNum(function () { return ModAttachments.getSignActiveLockEnd(p); })
        + ":cd_end=" + domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); })
        + ":free=" + domFreeSlots(p)
        + ":t_battle=" + br.battle + ":t_effect=" + br.effect + ":t_excl=" + br.excl
        // 锁定标记**串**原始值(`sign_active_lock_sign`):空串用 `[]` 包裹 ⇒「空」与「非空」
        // 在子串断言里**无歧义**(直接写 `lock_sign=` 会被非空值前缀命中而假绿)。
        + ":lock_sign=[" + domLockSign(p) + "]";
}

/** 锁定标记串(`sign_active_lock_sign` 原始值;读不到返回 `?`;Java null 归一成空串) */
function domLockSign(p) {
    var v = "?";
    try { v = "" + ModAttachments.getSignActiveLockSign(p); } catch (e) { v = "?"; }
    if (v === "null") v = "";
    return v;
}

/** 主物品栏 0..35 的可用空槽数(安全门 M6 的判据;产品 `countFreeSlots` 同口径) */
function domFreeSlots(p) {
    var free = 0;
    try {
        var items = p.getInventory().items;
        for (var i = 0; i < items.size(); i++) {
            var st = items.get(i);
            if (st == null || st.isEmpty()) free = free + 1;
        }
    } catch (e) { return -1; }
    return free;
}

// ══════════════════════════════════════════════════════════════════════════
//  ① nardiprep —— 基线脚手架
// ══════════════════════════════════════════════════════════════════════════

/**
 * 基线:卸掉两线所有相关槽位 → 装绿洲女王立牌(stand 槽)+ 骰子(**curios `dice` 槽**,见段头注 1)
 * → 主手清空 + 背包 1 号位放铁剑 → `clear` 时额外清临时牌与效果、复位主动冷却。
 *
 * ⚠️ 本命令**不**做类存在性闸门:类缺失时相关读数自然退化为 -1/`n_eq=0`,用例据此判 FAIL,
 * 比「命令直接不出读数」更容易定位(旧 jar 的 KubeJS 加载失败会连读数一起吞掉)。
 */
function doNardiPrep(ctx, tag, clearText) {
    var p = ctx.source.getPlayerOrException();
    var missing = "";
    if (NardisSignItemClass == null) missing = missing + "|NardisSignItem";
    if (NardisTemporaryCardUtilClass == null) missing = missing + "|TemporaryCardUtil";
    if (NardisPrivilegeEffectClass == null) missing = missing + "|NardisPrivilegeEffect";
    if (NardisCardItemClass == null) missing = missing + "|CardItem";
    if (NardisCardRegistryClass == null) missing = missing + "|CardRegistry";
    if (NardisModDataComponentsClass == null) missing = missing + "|ModDataComponents";
    if (NardisWeaponEnhancementClass == null) missing = missing + "|WeaponEnhancement";
    if (NardisAppliedStoneClass == null) missing = missing + "|AppliedStone";
    if (NardisSlotClass == null) missing = missing + "|Slot";
    // 两线槽位差:1.21.1 骰子是 curios `dice` 真饰品槽;1.20.1 的 curios `dice` = 手持骰子。
    // 一律按「名字」逐项清,取不到就跳过(不抛、不静默)。
    var slotsToClear = ["dice", "chip", "hands"];
    for (var si = 0; si < slotsToClear.length; si++) {
        try { clearCurioSlots(p, slotsToClear[si]); } catch (e0) { /* 该线无此槽 */ }
    }
    var clearAll = domBool(function () { return ("" + clearText) === "clear"; });
    var purged = 0;
    if (clearAll === 1) {
        purged = domClearTemp(p);
        try { ModEffectRemoval.remove(p, domEffectHolder()); } catch (e1) { /* 此处只做尽力清场 */ }
        try { NardisPrivilegeEffectClass.remove(p); } catch (e2) { /* 1.20.1 名形不同则忽略 */ }
        domResetActive(p);
    }
    var signErr = equipSign(p, NARDIS_SIGN_ID);
    var diceItem = resolveItem(NARDIS_DICE_ID);
    var diceErr = (diceItem == null) ? ("unknown_item:" + NARDIS_DICE_ID) : "";
    var cleared = domClearMain(p);
    var diceWhere = "none";
    if (diceErr === "") {
        // ⚠️ 必须放 **curios `dice` 槽**(= 产品语义上的「已佩戴」):两线的 findEquippedDice /
        //    equippedEnhancement / attackPowerOf|defensePowerOf **只查 Curios**,放主手时
        //    被动读不到、且探针写进主手骰子的组件与产品清理的 curios 骰子不是同一份。
        var errCurio = domPutDiceInCurio(p, diceItem);
        if (errCurio === "") {
            diceWhere = "curio";
        } else {
            // 退路:该线没有 curios `dice` 槽时退回主手,并把失败原因显式写进读数(不静默)
            diceErr = errCurio;
            try { p.setItemInHand(NardiHandClass.MAIN_HAND, new ItemStack(diceItem, 1)); diceWhere = "hand"; } catch (e3) { diceErr = diceErr + "|hand:" + domExText(e3); }
        }
    }
    // 主手不再放任何东西(骰子已进 curios 槽)⇒ `dice_hand=0` 是**预期基线**,不是异常
    var sword = resolveItem("minecraft:iron_sword");
    if (sword != null) { try { p.getInventory().setItem(1, new ItemStack(sword, 1)); } catch (e4) { /* 忽略 */ } }
    send(ctx, "AP_" + tag + "_PREP:sign_err=" + (signErr === null ? "" : signErr)
        + ":dice_err=" + diceErr
        + ":dice_where=" + diceWhere
        + ":clear=" + clearAll
        + ":purged=" + purged
        + ":classes=" + (missing === "" ? "ok" : missing)
        + (cleared === "" ? "" : ":clear_err=" + cleared)
        + ":" + domStateRead(p));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ② nardiread —— 全量只读
// ══════════════════════════════════════════════════════════════════════════

function doNardiRead(ctx, tag, phaseText, nameText) {
    var p = ctx.source.getPlayerOrException();
    var who = ("" + nameText) === "" ? "" : ("" + nameText);
    var target = p;
    if (who !== "") {
        var t = teruFindPlayer(ctx, who);
        if (t == null) {
            send(ctx, "AP_" + tag + "_" + phaseText + ":found=0:who=" + who);
            return 1;
        }
        target = t;
    }
    var uuid = "-";
    try { var u = playerUuid(target); if (u != null && u.ok) uuid = "" + u.value; } catch (e0) { uuid = "-"; }
    send(ctx, "AP_" + tag + "_" + phaseText + ":found=1:who=" + (who === "" ? "self" : who)
        + ":uuid=" + uuid + ":" + domStateRead(target));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ③ nardiarm / nardiarmclear —— 被动「威压」的装配栏脚手架
// ══════════════════════════════════════════════════════════════════════════

/** 是否装着绿洲女王立牌(被动的前置;读数显式给出,避免「没装立牌却断言 +N」的假结论) */
function domSignEquipped(p) {
    if (NardisSignItemClass == null) return -1;
    return domBool(function () { return NardisSignItemClass.isEquipped(p); });
}

/**
 * 直接写骰子装配栏:`atk_n` 张攻击牌(`medium`,费用 1)+ `def_n` 张防御牌(`defense_medium`,费用 1)。
 * `atk_n < 0` ⇒ **只读**(不写),用于「装立牌 ⇒ 被动生效」的严格对照。
 *
 * <p>写回位置由第 4 参 `where` 指定(`curio` 缺省 / `hand` / `both`),见 {@link domWriteEnh}。
 * **写回前一律先清空两处骰子上的旧 enhancement**(不留上一轮的装配残留,读数与顺序无关)。
 *
 * <p>读数同时给出两处的写回结果,便于一眼分辨「产品读的是哪一份骰子」:
 * `set_ok_curio` / `set_ok_hand` / `enh_curio`(条数:-1 = 该处无骰子,0 = EMPTY)/
 * `enh_hand`;`set_ok` = 本次目标位置的写回结果。异常一律经 {@link domExText} **纯字符串化**
 * (绝不把异常对象拼进读数,避免 KubeJS 二次 JSON 化把整行读数吃掉)。
 */
function doNardiArm(ctx, tag, atkText, defText, whereText) {
    var p = ctx.source.getPlayerOrException();
    var where = ("" + whereText) === "" ? "curio" : ("" + whereText);
    var a0 = teruInt(atkText, 0);
    var d0 = teruInt(defText, 0);
    var before = domStateRead(p);
    var err = "";
    var setOk = -1, setOkCurio = -1, setOkHand = -1;
    var enhCurio = -1, enhHand = -1;
    if (a0 >= 0 && d0 >= 0) {
        try {
            var kept = new ArrayListClass();
            for (var i = 0; i < a0; i++) kept.add(new NardisAppliedStoneClass(NARDIS_TEMP_TYPE, 10, false));
            for (var j = 0; j < d0; j++) kept.add(new NardisAppliedStoneClass("defense_medium", 10, false));
            var cost = 1;
            try { cost = NardisCardRegistryClass.cost(NARDIS_TEMP_TYPE, p); } catch (eC) { cost = 1; }
            var costD = 1;
            try { costD = NardisCardRegistryClass.cost("defense_medium", p); } catch (eD) { costD = 1; }
            // ⚠️ 显式转成 JS number:Rhino 下 Java int 参与 `*` 后可能仍是 Java 类型,
            //    拼进字符串会走 KubeJS 的「Java 对象 → JSON」路径(实测炸过一次)
            var aCost = (a0 * cost) - 0;
            var dCost = (d0 * costD) - 0;
            var enh = new NardisWeaponEnhancementClass(aCost, 12, dCost, 12, 3, kept);
            var w = domWriteEnh(p, where, enh);
            setOkCurio = w.curio;
            setOkHand = w.hand;
            if (where === "hand") { setOk = w.hand; }
            else if (where === "both") { setOk = (w.curio === 1 && w.hand === 1) ? 1 : 0; }
            else { setOk = w.curio; }
            if (w.err !== "") err = w.err;
        } catch (e1) { err = domExText(e1); }
    }
    try {
        var s1 = domEnhOf(domCurioDice(p));
        if (s1 != null) enhCurio = domStoneCountsOf(s1);
    } catch (e2) { enhCurio = -1; }
    try {
        var s2 = domEnhOf(domHandDice(p));
        if (s2 != null) enhHand = domStoneCountsOf(s2);
    } catch (e3) { enhHand = -1; }
    send(ctx, "AP_" + tag + "_ARM:atk_n=" + a0 + ":def_n=" + d0
        + ":where=" + where
        + ":sign=" + domSignEquipped(p)
        + ":set_ok=" + setOk
        + ":set_ok_curio=" + setOkCurio + ":set_ok_hand=" + setOkHand
        + ":enh_curio=" + enhCurio + ":enh_hand=" + enhHand
        + (err === "" ? "" : ":err=" + err)
        + ":before={" + before + "}:after={" + domStateRead(p) + "}");
    return 1;
}

/** 某个 enhancement 的装配牌总数(与 {@link domStoneCounts} 同口径,供 `enh_curio`/`enh_hand` 用) */
function domStoneCountsOf(enh) {
    if (enh == null) return -1;
    try {
        var stones = enh.appliedStones();
        if (stones == null) return 0;
        return stones.size() - 0;
    } catch (e) { return -1; }
}

/**
 * 清空骰子装配栏(装配 0/0,费用归零)。
 *
 * <p>⚠️ 只写 **curios `dice` 槽**(不要用 `both`):`domWriteEnh(..., "both")` 会顺带把骰子**搬到主手**
 * (它要清掉另一处以保证唯一),那会让后续步骤里「往主手放临时牌」把骰子一起顶掉
 * (2026-09-20 实测:`AP_A5_ARMCLEAR:…dice_hand=1:enh_src=hand`,紧接着 `narditemp inv` 就丢了骰子)。
 */
function doNardiArmClear(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var err = "";
    var setOk = -1, setOkCurio = -1, setOkHand = -1;
    try {
        var enh = new NardisWeaponEnhancementClass(0, 12, 0, 12, 3, new ArrayListClass());
        var w = domWriteEnh(p, "curio", enh);
        setOkCurio = w.curio;
        setOkHand = w.hand;
        setOk = w.curio;
        if (w.err !== "") err = w.err;
    } catch (e1) { err = domExText(e1); }
    send(ctx, "AP_" + tag + "_ARMCLEAR:cleared=" + (setOk === 1 ? 1 : 0)
        + ":set_ok=" + setOk
        + ":set_ok_curio=" + setOkCurio + ":set_ok_hand=" + setOkHand
        + (err === "" ? "" : ":err=" + err) + ":" + domStateRead(p));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ④ nardicast / nardigive —— 主动发放
// ══════════════════════════════════════════════════════════════════════════

/**
 * **真实主动**:`BaseSignItem.performSkillForCurio`(客户端按键的服务端同一入口)。
 * 读数:`granted`(本次**净增**的临时牌张数)、`n_temp`、`fx_dur`(效果剩余 tick)、`cool`(冷却剩余 tick)。
 *
 * <p>**参数签名(2026-09-20 固定)**:`nardicast <tag> [name] [reset]`
 * —— 第 2 参是**玩家名位**(缺省/`self` = 自己),第 3 参才是 `reset`。
 * ⚠️ 曾把第 2 参当成 `reset` 开关(`/astralprobe nardicast TRIGW reset`),
 * 于是 `reset` 被当成玩家名去找 ⇒ `AP_TRIGW_CAST:found=0:who=reset`(实测)。
 * 第 3 参写 `reset` ⇒ **先复位玩家级主动冷却**(顺带清锁定键)再走同一条真实入口。
 *
 * <p>**2026-09-27 二次裁决后的语义(本文件注释已按此改写)**:主动**不进入冻结**(无「使用中」锁定态),
 * 效果生效期间**可以再次释放**,只受**冷却**限制;再次释放**不清空旧临时牌**(新牌**叠加**)、
 * 并把效果有效期**重置为 3:00**。⇒ 本命令的 `reset` 档现在只用于「把冷却这一项前置条件归零」,
 * 而**叠加/重置**类用例(M2)按契约要求改用**最小作用域**的 `nardicd <tag>`(只写冷却,
 * 不碰锁定键/效果/临时牌),以免 `reset` 的附带清理污染读数。
 */
/** 异常 → 纯字符串(本段内的**公开别名**,名字里不含 `dom` 前缀以便与共享段区分) */
function domExecText(e) { return domExText(e); }

/** 治愈点读数(`HealingManager#getPoints`;-1 = 读不到) */
function domPill(p) {
    var v = -1;
    try { v = HealingManagerClass.getPoints(p) - 0; } catch (e) { v = -1; }
    return v;
}

function doNardiCast(ctx, tag, nameText, coolText) {
    var p = ctx.source.getPlayerOrException();
    var who = ("" + nameText) === "" ? "" : ("" + nameText);
    var target = p;
    if (who !== "" && who !== "self") {
        var t = teruFindPlayer(ctx, who);
        if (t == null) { send(ctx, "AP_" + tag + "_CAST:found=0:who=" + who); return 1; }
        target = t;
    }
    var didReset = 0;
    if (("" + coolText) === "reset") { domResetActive(target); didReset = 1; }
    var before = domStateRead(target);
    var t0 = domCountTemp(target);
    var eq0 = domCountTempEquipped(target);
    var pill0 = domPill(target);
    var l0 = -1;
    if (TeruSignItemClass != null) { try { l0 = TeruSignItemClass.getLayers(target); } catch (eL) { l0 = -1; } }
    var err = "";
    // 门控诊断:`performSkill` 在真正触发前有四道门(锁定态 / 玩家级冷却 / 目标选择会话 / 选择器门控),
    // 任一被拦都会「静默无效果」—— 只读这四项才能把「没生效」归因到具体哪道门(2026-09-20 实测需要)。
    var lockBefore = -1, cdBefore = -1, selecting = -1, nowRaw = -1;
    try { lockBefore = BaseSignItemClass.isSignActiveLocked(target) ? 1 : 0; } catch (eG1) { lockBefore = -1; }
    try { cdBefore = ModAttachments.getSignActiveCooldownEnd(target) - 0; } catch (eG2) { cdBefore = -1; }
    try { selecting = com.merlinkitsune.astral_dice.target.TargetSelectionManager.isSelecting(target) ? 1 : 0; } catch (eG3) { selecting = -1; }
    try { nowRaw = nowTick(target) - 0; } catch (eG4) { nowRaw = -1; }
    var lockAfter = -1, cdAfter = -1, cdUnchanged = -1;
    try { BaseSignItemClass.performSkillForCurio(target); } catch (e1) { err = domExText(e1); }
    try { lockAfter = BaseSignItemClass.isSignActiveLocked(target) ? 1 : 0; } catch (eG5) { lockAfter = -1; }
    try {
        cdAfter = ModAttachments.getSignActiveCooldownEnd(target) - 0;
        if (cdBefore >= 0 && cdAfter >= 0) cdUnchanged = (cdBefore === cdAfter) ? 1 : 0;
    } catch (eG6) { cdAfter = -1; }
    var t1 = domCountTemp(target);
    var eq1 = domCountTempEquipped(target);
    var pill1 = domPill(target);
    var br1 = domTempBreakdown(target);
    var l1 = -1;
    if (TeruSignItemClass != null) { try { l1 = TeruSignItemClass.getLayers(target); } catch (eL2) { l1 = -1; } }
    var fx = domFx(target);
    send(ctx, "AP_" + tag + "_CAST:who=" + (who === "" ? "self" : who)
        + ":cool_reset=" + didReset
        + ":lock_before=" + lockBefore + ":lock_after=" + lockAfter
        + ":cd_before=" + cdBefore + ":cd_after=" + cdAfter + ":cd_unchanged=" + cdUnchanged
        + ":now=" + nowRaw + ":selecting=" + selecting
        + ":temp_before=" + t0 + ":temp_eq_before=" + eq0
        + ":granted=" + (t1 - t0)
        + ":n_temp=" + t1 + ":n_temp_eq=" + eq1
        + ":pill_before=" + pill0 + ":pill_after=" + pill1 + ":pill_delta=" + (pill1 - pill0)
        + ":fx_dur=" + fx.dur + ":n_fx_on=" + fx.on
        + ":cool=" + domCool(target)
        + ":teru_delta=" + (l1 - l0)
        + ":teru_eq_atk=" + (((l1 - l0) - 0) === (br1.atk - 0) ? 1 : 0)
        + ":ground_temp=" + domGroundTemp(target)
        + (err === "" ? "" : ":err=" + err)
        + ":before={" + before + "}:after={" + domStateRead(target) + "}");
    return 1;
}

/**
 * 在产品**没有**效果时给探针自己补一条 `nardis_privilege`(3:00,与主动发放**逐字同参**:
 * `EffectTimerGuard.apply` + 六参 `showIcon=true`)。
 *
 * <p>为什么需要:产品的玩家级 tick 自检(§4.4)口径是「**有临时牌但无效果 ⇒ 清空**」;
 * 而 `nardigive` / `narditemp ... inv` 是**纯脚手架**、不发效果 ⇒ 实测「给牌命令的读数里
 * `n_temp=3`,下一条读命令就是 `n_temp=0`」(tick 在两条命令之间清掉了它)。
 * 这不是产品缺陷(spec §4.4 明确如此),而是「脚手架发牌」与「真实主动」的差异 ⇒
 * 脚手架发牌时**必须**同时建立效果,否则用例断言的就不是产品语义。真实路径
 * (`nardicast`)不调用本函数,它的效果由 `handleUse` 自己施加。
 */
function domEnsureEffect(p) {
    try {
        if (p.hasEffect(domEffectHolder())) return 0;
    } catch (e0) { /* 落到下面照样补一次 */ }
    var dur = NardisPrivilegeDurationTicks();
    try {
        var inst = new MobEffectInstanceClass(domEffectHolder(), dur, 0, false, false, true);
        if (NardiEffectTimerGuardClass != null) {
            NardiEffectTimerGuardClass.apply(p, inst);
        } else {
            p.addEffect(inst);
        }
        return 1;
    } catch (e1) { return -1; }
}

/** `NardisPrivilegeEffect.DURATION_TICKS`(读不到时退回 spec 固定的 3600) */
function NardisPrivilegeDurationTicks() {
    try {
        var d = NardisPrivilegeEffectClass.DURATION_TICKS;
        if (d != null) return d - 0;
    } catch (e) { /* 落到 3600 */ }
    return 3600;
}

/**
 * 直接 `TemporaryCardUtil.grantRandom(owner, n)`(绕开冷却/门控,测「发牌本身」)。
 *
 * <p>⚠️ **发牌前先 {@link domEnsureEffect}**:产品 tick 自检的口径是「有临时牌但无效果 ⇒ 清空」
 * (spec §4.4),而本命令是纯脚手架、不发效果 ⇒ 上一轮实测「`AP_G3_GIVE:…n_temp=3` 的下一条
 * 读命令就变成 `n_temp=0`」(tick 在两条命令之间清掉了)。这不是产品缺陷:真实主动
 * (`nardicast`)必然带效果,所以脚手架发牌也必须同时建立效果,否则断言的对象不是产品语义。
 * 读数 `fx_made` 显式暴露本次是否补了效果(1 = 补了,0 = 本来就有,-1 = 补失败)。
 */
function doNardiGive(ctx, tag, nText, nameText) {
    var p = ctx.source.getPlayerOrException();
    var who = ("" + nameText) === "" ? "" : ("" + nameText);
    var target = p;
    if (who !== "") {
        var t = teruFindPlayer(ctx, who);
        if (t == null) { send(ctx, "AP_" + tag + "_GIVE:found=0:who=" + who); return 1; }
        target = t;
    }
    var n = teruInt(nText, 3);
    var free = -1, granted = -1, err = "";
    try { free = NardisTemporaryCardUtilClass.countFreeSlots(target); } catch (e0) { free = -1; }
    var fxMade = domEnsureEffect(target);
    var t0 = domCountTemp(target);
    var pill0 = domPill(target);
    var l0 = -1;
    if (TeruSignItemClass != null) { try { l0 = TeruSignItemClass.getLayers(target); } catch (eL) { l0 = -1; } }
    try { granted = NardisTemporaryCardUtilClass.grantRandom(target, n); } catch (e1) { err = domExText(e1); }
    var l1 = -1;
    if (TeruSignItemClass != null) { try { l1 = TeruSignItemClass.getLayers(target); } catch (eL2) { l1 = -1; } }
    var pill1 = domPill(target);
    var br1 = domTempBreakdown(target);
    send(ctx, "AP_" + tag + "_GIVE:who=" + (who === "" ? "self" : who) + ":n=" + n
        + ":granted=" + granted + ":slots_free=" + free
        + ":fx_made=" + fxMade
        + ":n_temp=" + domCountTemp(target)
        + ":pill_before=" + pill0 + ":pill_after=" + pill1 + ":pill_delta=" + (pill1 - pill0)
        + ":t_atk=" + br1.atk + ":t_def=" + br1.def
        + ":teru_delta=" + (l1 - l0)
        + ":teru_eq_atk=" + (((l1 - l0) - 0) === (br1.atk - 0) ? 1 : 0)
        + (err === "" ? "" : ":err=" + err)
        + ":" + domStateRead(target));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ⑤ narditemp —— 造/清临时牌(物品栈 / 装配栏)
// ══════════════════════════════════════════════════════════════════════════

/**
 * `clear` ⇒ 清空身上的临时牌(不走产品清理,纯脚手架);
 * `inv` ⇒ 主手塞一张带标记的攻击牌(同 id 普通牌由后续 `/item replace` 提供对照);
 * `assemble` ⇒ 往**产品语义上的骰子**(curios `dice` 槽)装配栏塞一颗 `temporary = true` 的攻击牌石(费用 +1)。
 *
 * <p>两种造牌模式都先 {@link domEnsureEffect} 补效果:否则产品的 tick 自检会立刻清掉刚造的临时牌
 * (「有临时牌但无效果 ⇒ 清空」,spec §4.4),读数就只剩 `n_temp=0`。
 */
function doNardiTemp(ctx, tag, modeText, clearText) {
    var p = ctx.source.getPlayerOrException();
    var mode = "" + modeText;
    var clearMode = domBool(function () { return ("" + clearText) === "clear"; });
    var err = "";
    var removed = 0;
    var cost = -1;
    if (clearMode === 1) {
        removed = domRemoveProbeStone(p);
        if (removed < 0) removed = 0;
        var purged = domClearTemp(p);
        send(ctx, "AP_" + tag + "_TEMP:mode=clear:where=all:purged=" + purged
            + ":probe_stone_removed=" + removed + ":" + domStateRead(p));
        return 1;
    }
    if (mode === "assemble") {
        var r0 = domRemoveProbeStone(p);
        if (r0 < 0) err = "probe_stone_remove_failed";
        var asm = domBuildTempStone(p, NARDIS_TEMP_TYPE2);
        var setOk = -1, setOkCurio = -1, setOkHand = -1, w = null;
        if (asm.ok === 1) {
            w = domWriteEnh(p, "curio", asm.enh);
            setOkCurio = w.curio;
            setOkHand = w.hand;
            setOk = w.curio;
            if (w.err !== "") err = (err === "" ? "" : err + "|") + w.err;
        }
        if (asm.ok !== 1) { err = (err === "" ? "" : err + "|") + "assemble:" + asm.why; }
        if (asm.ok === 1 && setOk !== 1) { err = (err === "" ? "" : err + "|") + "set_failed"; }
        cost = (asm.cost === undefined ? -1 : asm.cost);
        send(ctx, "AP_" + tag + "_TEMP:mode=assemble:where=dice"
            + ":asm_ok=" + asm.ok + ":set_ok=" + setOk
            + ":set_ok_curio=" + setOkCurio + ":set_ok_hand=" + setOkHand
            + ":type=" + NARDIS_TEMP_TYPE2
            + ((asm.why === "") ? "" : ":asm_err=" + asm.why)
            + ":used_cost=" + domUsedCost(p).atk
            + (err === "" ? "" : ":err=" + err)
            + ":" + domStateRead(p));
        return 1;
    }
    if (mode === "foil") {
        // ③ 光效对照:同一 id 的「带标记牌」与「无标记牌」**同时**存在,一次性读出两者真值。
        //    为什么要挤在一条命令里:两条命令之间会插入至少一个服务器 tick,而产品的
        //    玩家级自检(§4.4)在「有临时牌但无效果」时会清掉它 ⇒ 分两条命令做对照会变成竞态。
        domClearTemp(p);
        var fx = domEnsureEffect(p);
        var markedStack = domMakeTempCard(NARDIS_ATK_CARD_ID);
        var plainItem = resolveItem(NARDIS_ATK_CARD_ID);
        var plainStack = (plainItem == null) ? null : new ItemStack(plainItem, 1);
        var invErr = "";
        var handId = "-", plainId = "-", plainSlot = -1;
        try { p.setItemInHand(NardiHandClass.MAIN_HAND, markedStack); } catch (e0) { invErr = "hand:" + domExText(e0); }
        if (plainStack != null) {
            try {
                // ⚠️ 普通牌必须放**非手持**槽:热键栏是 0..8 且 `setItemInHand(MAIN_HAND, …)` 写的就是
                //    当前选中格 ⇒ 普通牌固定放 **9 号槽**(热键栏之外),绝不会顶掉手上的临时牌。
                //    (实测把普通牌写回 0 号槽会顶掉临时牌:`hand_mark=0`、`n_temp=0`,而 hand_id/plain_id
                //     同 id ⇒ 表面看不出异常,是典型的静默假绿。)
                var plainSlotIdx = 9;
                p.getInventory().setItem(plainSlotIdx, plainStack);
                plainId = itemIdOf(p.getInventory().getItem(plainSlotIdx));
                plainSlot = plainSlotIdx;
            } catch (e1) { invErr = invErr + "|inv_plain:" + domExText(e1); }
        } else { invErr = invErr + "|unknown_item:" + NARDIS_ATK_CARD_ID; }
        var handStack = null, plainRead = null;
        try { handStack = p.getMainHandItem(); } catch (e2) { handStack = null; }
        if (plainSlot >= 0) { try { plainRead = p.getInventory().getItem(plainSlot); } catch (e3) { plainRead = null; } }
        handId = itemIdOf(handStack);
        var mkHand = 0, mkPlain = 0;
        if (NardisTemporaryCardUtilClass != null) {
            mkHand = domBool(function () { return NardisTemporaryCardUtilClass.isTemporary(handStack); });
            mkPlain = domBool(function () { return NardisTemporaryCardUtilClass.isTemporary(plainRead); });
        }
        send(ctx, "AP_" + tag + "_TEMP:mode=foil:where=hand+inv:fx_made=" + fx
            + ":hand_id=" + handId + ":hand_n=" + ((handStack == null || handStack.isEmpty()) ? 0 : handStack.getCount())
            + ":hand_foil=" + domFoil(handStack) + ":hand_mark=" + mkHand
            + ":plain_slot=" + plainSlot + ":plain_id=" + plainId
            + ":plain_n=" + ((plainRead == null || plainRead.isEmpty()) ? 0 : plainRead.getCount())
            + ":plain_foil=" + domFoil(plainRead) + ":plain_mark=" + mkPlain
            + (invErr === "" ? "" : ":err=" + invErr)
            + ":" + domStateRead(p));
        return 1;
    }
    // 默认 inv:主手 = 临时牌(同 id 的**普通**牌由后续 `/item replace` 提供对照)
    domClearTemp(p);
    var fxMade = domEnsureEffect(p);
    var stack = domMakeTempCard(NARDIS_ATK_CARD_ID);    if (stack == null) { send(ctx, "AP_" + tag + "_TEMP:mode=inv:err=unknown_item:" + NARDIS_ATK_CARD_ID); return 1; }
    try { p.setItemInHand(NardiHandClass.MAIN_HAND, stack); } catch (e1) { err = domExText(e1); }
    send(ctx, "AP_" + tag + "_TEMP:mode=inv:where=hand:fx_made=" + fxMade
        + ":hand_id=" + itemIdOf(p.getMainHandItem())
        + ":used_cost=" + domUsedCost(p).atk
        + (err === "" ? "" : ":err=" + err) + ":" + domStateRead(p));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ⑥ nardifoil —— 光效真值(单层,用户已裁决)
// ══════════════════════════════════════════════════════════════════════════

/**
 * 光效读的是 `Item#isFoil(ItemStack)` 的真值(产品侧由 `CardItem#isFoil` 覆写实现),
 * **不是**截图/渲染 —— spec §7.3 明确「渲染层的可见性不做截图断言,以 hasFoil 真值 + 渲染器
 * 以 hasFoil 为门控的代码证据覆盖」。
 */
function doNardiFoil(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var hand = p.getMainHandItem();
    var marked = 0;
    if (NardisTemporaryCardUtilClass != null) {
        marked = domBool(function () { return NardisTemporaryCardUtilClass.isTemporary(hand); });
    }
    send(ctx, "AP_" + tag + "_FOIL:hand_id=" + itemIdOf(hand)
        + ":hand_n=" + (hand == null || hand.isEmpty() ? 0 : hand.getCount())
        + ":foil=" + domFoil(hand)
        + ":is_card=" + domBool(function () { return hand.getItem() instanceof NardisCardItemClass; })
        + ":temp_mark=" + marked
        + ":" + domStateRead(p));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ⑦ nardidrop —— 丢弃被拒(两重守卫)
// ══════════════════════════════════════════════════════════════════════════

/**
 * 主手为临时牌 ⇒ `ServerPlayer#drop(true)` **必须失败**(产品侧 `CardItem#onDroppedByPlayer`
 * 返回 false,`ServerPlayer#drop` 在移除前判定)。
 * 读数:`dropped`(drop 的返回值)、`hand_before`/`hand_after`(主手物品 id)、
 * `ground`(8 格内该物品的地面掉落物数 —— 必须为 0)。
 */
function doNardiDrop(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var item = resolveItem(NARDIS_ATK_CARD_ID);
    if (item == null) { send(ctx, "AP_" + tag + "_ERR:unknown_item:" + NARDIS_ATK_CARD_ID); return 1; }
    var stack = domMakeTempCard(NARDIS_ATK_CARD_ID);
    if (stack == null) { send(ctx, "AP_" + tag + "_ERR:make_temp_failed"); return 1; }
    try { p.setItemInHand(NardiHandClass.MAIN_HAND, stack); } catch (e0) { /* 交给读数 */ }
    var before = itemIdOf(p.getMainHandItem());
    var marked = 0;
    if (NardisTemporaryCardUtilClass != null) {
        marked = domBool(function () { return NardisTemporaryCardUtilClass.isTemporary(p.getMainHandItem()); });
    }
    var dropped = -1, err = "";
    try { dropped = p.drop(true) ? 1 : 0; } catch (e1) { dropped = -1; err = domExText(e1); }
    var after = itemIdOf(p.getMainHandItem());
    var still = 0;
    try { still = teruInvCount(p, NARDIS_ATK_CARD_ID); } catch (e2) { still = -1; }
    send(ctx, "AP_" + tag + "_DROP:temp_mark=" + marked
        + ":hand_before=" + before + ":hand_after=" + after
        + ":dropped=" + dropped
        + ":still_in_inv=" + still
        + ":ground=" + teruGroundCount(p, NARDIS_ATK_CARD_ID, 8)
        + (err === "" ? "" : ":err=" + err)
        + ":" + domStateRead(p));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ⑧ nardislot —— **直接验证 mixin 真身**(spec §7.3:GUI 鼠标点击无法注入)
// ══════════════════════════════════════════════════════════════════════════

/**
 * **非玩家容器**上的 `Slot#mayPlace(临时牌)` ⇒ 走真实 mixin `SlotPlaceGuardMixin`,必须被拒
 * (`blocked_container=1`);`Slot(player.getInventory(), …).mayPlace(临时牌)` ⇒ 玩家自己的槽必须放行
 * (`allow_inventory=1`);同一张牌换成**无标记**时两处都必须放行(`plain_container` /
 * `plain_inventory`)—— 这是「拦截只针对临时牌、不误伤普通牌」的反证。
 *
 * <p>⚠️ 「非玩家容器」用的是**现场放置的真实潜影盒方块实体**(`ShulkerBoxBlockEntity implements Container`),
 * **不是** `new SimpleContainer(9)`:后者在 Rhino 下有两个构造器 `(int)` / `(ItemStack...)` 无法定序,
 * 抛出的 `InternalError: ambiguous` **逃出 `guard` 的 try/catch** ⇒ 整条命令一行读数都不出
 * (上一轮 `AP_S1_SLOT` 永不出现、且没有任何 `_EX` 或报错线索,就是这个原因;2026-09-20 定位)。
 * 用真实方块实体同时更贴近「真的往箱子里放」这一入箱路径。
 *
 * <p>判据来源:`TemporaryCardUtil.isPlayerOwnedOrPermissive(slot)`(mixin 与本命令共用同一实现);
 * 该静态方法在旧 jar 上缺失时退回「mixin 内的等价内联判据」,读数 `au_src` 显式给出用了哪条。
 */
function doNardiSlot(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var err = "";
    var marked = domMakeTempCard(NARDIS_ATK_CARD_ID);
    var plainItem = resolveItem(NARDIS_ATK_CARD_ID);
    var plain = plainItem == null ? null : new ItemStack(plainItem, 1);
    var placed = domPlaceShulker(p);
    if (placed.err !== "") err = err + "|" + placed.err;
    var slotC = null, slotI = null, slotC2 = null, slotI2 = null;
    try { slotC = new NardisSlotClass(placed.be, 0, 0, 0); } catch (e1) { err = err + "|slot_container:" + domExText(e1); }
    try { slotI = new NardisSlotClass(p.getInventory(), 0, 0, 0); } catch (e2) { err = err + "|slot_inventory:" + domExText(e2); }
    try { slotC2 = new NardisSlotClass(placed.be, 1, 0, 0); } catch (e3) { /* 忽略 */ }
    try { slotI2 = new NardisSlotClass(p.getInventory(), 1, 0, 0); } catch (e4) { /* 忽略 */ }

    // 与 mixin 完全同源的判据
    function owned(s) {
        if (s == null) return -1;
        if (NardisTemporaryCardUtilClass != null) {
            try { return NardisTemporaryCardUtilClass.isPlayerOwnedOrPermissive(s) ? 1 : 0; } catch (e5) { /* 退回内联 */ }
        }
        try {
            if (s.container instanceof NardisInventoryClass) return 1;
        } catch (e6) { /* 忽略 */ }
        return 0;
    }
    var auSrc = (NardisTemporaryCardUtilClass == null) ? "inline" : "util";
    var oC = owned(slotC), oI = owned(slotI);
    var blocked = -1, allowInv = -1, plainC = -1, plainI = -1, blockedMove = -1;
    var markedFlag = 0;
    if (NardisTemporaryCardUtilClass != null) {
        markedFlag = domBool(function () { return NardisTemporaryCardUtilClass.isTemporary(marked); });
    }
    try { blocked = slotC == null || marked == null ? -1 : (slotC.mayPlace(marked) ? 0 : 1); } catch (e7) { blocked = -1; err = err + "|mayPlaceC:" + domExText(e7); }
    try { allowInv = slotI == null || marked == null ? -1 : (slotI.mayPlace(marked) ? 1 : 0); } catch (e8) { allowInv = -1; err = err + "|mayPlaceI:" + domExText(e8); }
    try { plainC = slotC2 == null || plain == null ? -1 : (slotC2.mayPlace(plain) ? 1 : 0); } catch (e9) { plainC = -1; }
    try { plainI = slotI2 == null || plain == null ? -1 : (slotI2.mayPlace(plain) ? 1 : 0); } catch (e10) { plainI = -1; }
    // 判据函数本身（mixin 与探针共用）:非玩家槽 ⇒ true(拦截),玩家槽 ⇒ false(放行)
    if (NardisTemporaryCardUtilClass != null && marked != null) {
        try {
            var b1 = NardisTemporaryCardUtilClass.isPlacementBlocked(marked, oC === 1) ? 1 : 0;
            var b2 = NardisTemporaryCardUtilClass.isPlacementBlocked(marked, oI === 1) ? 1 : 0;
            blockedMove = (b1 === 1 && b2 === 0) ? 1 : 0;
        } catch (e11) { blockedMove = -1; }
    }
    // 还原现场放置的潜影盒(幂等)
    var cleanErr = domRemoveShulker(placed);
    if (cleanErr !== "") err = err + "|" + cleanErr;
    send(ctx, "AP_" + tag + "_SLOT:temp_mark=" + markedFlag
        + ":au_src=" + auSrc
        + ":owned_container=" + oC + ":owned_inventory=" + oI
        + ":blocked_container=" + blocked
        + ":allow_inventory=" + allowInv
        + ":plain_container=" + plainC
        + ":plain_inventory=" + plainI
        + ":predicate_ok=" + blockedMove
        + (err === "" ? "" : ":err=" + err)
        + ":" + domStateRead(p));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ⑨ nardiexpire —— 到期/外力移除 + 自检收口
// ══════════════════════════════════════════════════════════════════════════

/**
 * 走**内部通道** `ModEffectRemoval.remove`(spec §7.1;产品侧 `ModEffectEvents` 会拦掉一切外部移除)
 * 移除 `nardis_privilege`,随后**驱动一次生产侧自检**(`TemporaryCardUtil.tick`)——
 * 「效果已不在而身上仍有临时牌 ⇒ 清空全部(含装配栏)」。
 *
 * 读数:`fx_before`(移除前效果剩余 tick)、`fx_on_now`(移除后立刻;0/1)、
 * `removed`(移除调用的返回)、`purged`(自检清掉的张数)、`purge_total`(产品侧实际清空总数)、
 * `n_temp`/`n_temp_eq`/`used_cost`(清理后的装配栏读数)。
 */
function doNardiExpire(ctx, tag, nameText) {
    var p = ctx.source.getPlayerOrException();
    var who = ("" + nameText) === "" ? "" : ("" + nameText);
    var target = p;
    if (who !== "") {
        var t = teruFindPlayer(ctx, who);
        if (t == null) { send(ctx, "AP_" + tag + "_EXPIRE:found=0:who=" + who); return 1; }
        target = t;
    }
    var fx0 = domFx(target);
    var temp0 = domCountTemp(target);
    var eq0 = domCountTempEquipped(target);
    var cost0 = domUsedCost(target).atk;
    var removedBy = "none";
    try { ModEffectRemoval.remove(target, domEffectHolder()); removedBy = "internal"; }
    catch (e1) {
        removedBy = "internal_ex:" + domExText(e1);
        try { NardisPrivilegeEffectClass.remove(target); removedBy = removedBy + "|effect_class"; }
        catch (e2) { removedBy = removedBy + "|effect_class_ex:" + domExText(e2); }
    }
    var fx1 = domFx(target);
    // ⚠️ `TemporaryCardUtil#tick(Player)` 返回 **void**(1.21.1 :359 / 1.20.1 :346 两线同)
    // ⇒ 不能当返回值读(上一轮读数恒 `tick_rc=undefined`)。这里只暴露「自检是否无异常跑过」,
    // 真正的清理证据由 `purge_total` / `n_temp` / `n_temp_eq` 给出。
    var tickRan = 1;
    var err = "";
    try { NardisTemporaryCardUtilClass.tick(target); }
    catch (e3) { tickRan = 0; err = domExText(e3); }
    var purgeTotal = -1;
    try { purgeTotal = NardisTemporaryCardUtilClass.purgeAll(target); } catch (e4) { purgeTotal = -1; }
    // ⚠️ 这里**不**补效果:本条命令的语义就是「效果已不在 ⇒ 自检清空」,补效果会让紧随其后的
    //    `nardiread` 读到一个仍在生效的效果(与 R7_EXPIRED 的断言直接矛盾)。
    send(ctx, "AP_" + tag + "_EXPIRE:who=" + (who === "" ? "self" : who)
        + ":how=" + removedBy
        + ":fx_before=" + fx0.dur + ":fx_on_before=" + fx0.on
        + ":fx_on_now=" + fx1.on
        + ":tick_ran=" + tickRan + ":purge_total=" + purgeTotal
        + ":temp_before=" + temp0 + ":temp_eq_before=" + eq0
        + ":used_cost_before=" + cost0
        + ":n_temp=" + domCountTemp(target) + ":n_temp_eq=" + domCountTempEquipped(target)
        + ":used_cost=" + domUsedCost(target).atk
        + (err === "" ? "" : ":err=" + err)
        + ":" + domStateRead(target));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ⑩ nardiguard —— R1/R2 回归取证(只读;spec §7.0)
// ══════════════════════════════════════════════════════════════════════════

/**
 * `/astralprobe nardiguard <tag> <effect|attack>` —— **只读**,对**同一 id** 的
 * 「临时牌」与「普通牌」各给一组读数(spec §7.0)。
 *
 * <p>为什么需要:原 `NARDIS-SIGN` 用例全程只用 `attack_card_medium`,看不见
 * **R1(效果牌的容器缺口)** 与 **R2(效果牌无光效)** —— 这两条只在**效果牌**上才暴露。
 *
 * <p>读数与判据:
 * <ul>
 *   <li>`foil_t=1` / `foil_n=0` —— **R2**:`ItemStack#isFoil()` 真值(临时牌发光、普通牌不发光);</li>
 *   <li>`guard_t=0` / `guard_n=1` —— **R1(1.21.1)**:栈级钩子
 *       `IItemStackExtension#canFitInsideContainerItems()`(五处容器入口的唯一判据);</li>
 *   <li>`face_t=0` / `face_n=1` —— **R1 行为面**:现场放置一个**真实潜影盒方块实体**,调
 *       `ShulkerBoxBlockEntity#canPlaceItemThroughFace(0, stack, UP)`(1.20.1 侧靠这条取证);</li>
 *   <li>`perm_t=1` / `perm_n=1` —— **不误伤**:把同一张牌放进**玩家自身槽位**时必须放行
 *       (`TemporaryCardUtil#isPlacementBlocked(stack, true) == false`),证明拦截是定向的、
 *       不存在第二个拦截点。⚠️ 规格表把这一对写成「永久牌不受影响」,本实现取的判据是
 *       「**玩家自身槽位是否放行**」(perm = permissive);`_t`/`_n` 仍分别为临时牌/普通牌。</li>
 * </ul>
 *
 * <p>**只读性**:命令只**读**物品/方块实体的判定方法,唯一的状态改动是临时放置的那一个潜影盒
 * (读数后立即 `removeBlock` 还原);不写任何物品组件、不改玩家状态。
 */
function doNardiGuard(ctx, tag, modeText) {
    var p = ctx.source.getPlayerOrException();
    var mode = ("" + modeText) === "attack" ? "attack" : "effect";
    var cardId = (mode === "attack") ? NARDIS_ATK_CARD_ID : NARDIS_EFFECT_CARD_ID;
    var out = ["mode=" + mode + ":card=" + cardId];
    var item = resolveItem(cardId);
    if (item == null) { send(ctx, "AP_" + tag + "_GUARD:err=unknown_item:" + cardId); return 1; }
    var tempStack = domMakeTempCard(cardId);
    var normStack = new ItemStack(item, 1);
    out.push("mark_t=" + domBool(function () { return NardisTemporaryCardUtilClass.isTemporary(tempStack); }));
    out.push("mark_n=" + domBool(function () { return NardisTemporaryCardUtilClass.isTemporary(normStack); }));
    // R2:光效真值
    out.push("foil_t=" + domFoil(tempStack));
    out.push("foil_n=" + domFoil(normStack));
    // R1(栈级)
    out.push("guard_t=" + domFitsContainer(tempStack));
    out.push("guard_n=" + domFitsContainer(normStack));
    // R1(行为面):真实潜影盒方块实体
    var faceT = -1, faceN = -1, faceErr = "";
    var placed = domPlaceShulker(p);
    if (placed.err !== "") faceErr = placed.err;
    var be = placed.be;
    if (be != null) {
        try { faceT = be.canPlaceItemThroughFace(0, tempStack, NardisDirectionClass.UP) ? 1 : 0; }
        catch (e1) { faceErr = faceErr + "|face_t:" + domExText(e1); }
        try { faceN = be.canPlaceItemThroughFace(0, normStack, NardisDirectionClass.UP) ? 1 : 0; }
        catch (e2) { faceErr = faceErr + "|face_n:" + domExText(e2); }
    }
    out.push("face_t=" + faceT);
    out.push("face_n=" + faceN);
    // 还原:移除临时放置的潜影盒(幂等;失败只记读数)
    domRemoveShulker(placed);
    // 不误伤:玩家自身槽位必须放行(perm = permissive)
    out.push("perm_t=" + domBool(function () { return NardisTemporaryCardUtilClass.isPlacementBlocked(tempStack, true) === false; }));
    out.push("perm_n=" + domBool(function () { return NardisTemporaryCardUtilClass.isPlacementBlocked(normStack, true) === false; }));
    if (faceErr !== "") out.push("face_err=" + faceErr);
    // 顺带把「玩家自身槽 mayPlace」也读一遍(与 nardislot 同源,反证不误伤)
    var slotPermT = -1, slotPermN = -1;
    try {
        var invSlot = new NardisSlotClass(p.getInventory(), 2, 0, 0);
        slotPermT = invSlot.mayPlace(tempStack) ? 1 : 0;
        slotPermN = invSlot.mayPlace(normStack) ? 1 : 0;
    } catch (e4) { /* 读数保持 -1 */ }
    out.push("slotperm_t=" + slotPermT + ":slotperm_n=" + slotPermN);
    send(ctx, "AP_" + tag + "_GUARD:" + out.join(":"));
    return 1;
}

/**
 * 栈级容器适配判据(逐层回退,**只读**):
 * ① 1.21.1 栈级扩展 `stack.canFitInsideContainerItems()`;
 * ② NeoForge 物品级 stack-aware `item.canFitInsideContainerItems(stack)`;
 * ③ 1.20.1 类型级 `item.canFitInsideContainerItems()`。
 * 全部不可用返回 -1(不冒充 0)。
 */
function domFitsContainer(stack) {
    if (stack == null || stack.isEmpty()) return 0;
    try { return stack.canFitInsideContainerItems() ? 1 : 0; } catch (e1) { /* 下一层 */ }
    var it = null;
    try { it = stack.getItem(); } catch (e2) { it = null; }
    if (it == null) return -1;
    try { return it.canFitInsideContainerItems(stack) ? 1 : 0; } catch (e3) { /* 下一层 */ }
    try { return it.canFitInsideContainerItems() ? 1 : 0; } catch (e4) { return -1; }
}

// ══════════════════════════════════════════════════════════════════════════
//  ⑩ nardiequip —— 佩戴触发器筹码/立牌(脚手架;spec §7.2 第 9 条的必需前置)
// ══════════════════════════════════════════════════════════════════════════

/**
 * `/astralprobe nardiequip <tag> <chip|teru>` —— 把**维生素药丸筹码**(chip 槽 0)或
 * **教主立牌**(stand 槽 0)直接写进 Curios 栏位。
 *
 * <p>为什么自带这条(2026-09-20 实测):
 * <ul>
 *   <li>`/curios replace @s chip 0 …` **静默无效** —— 本模组的 chip 槽数据包 base 尺寸是 0、
 *       实际尺寸由骰子星级的 per-slot 修饰符给出,而原版命令按 base 尺寸校验 ⇒ 写入被丢弃
 *       (实测 `chipstate` 的 `slots=3` 但 `items=[]`);</li>
 *   <li>共享段 `/astralprobe equipslot` 在本轮实测中**整条命令不出任何读数**
 *       (`doEquipSlot` 的 chip 分支调 `handler.grow(...)`,而 Curios 15 已把 `grow` 从接口移除
 *       ⇒ 抛错后读数一行都不落),不能拿它当基线。</li>
 * </ul>
 * 本命令只做「写栏位」这一步(`curioHandler` + `setStackInSlot`,与本段读写骰子同一套已验证通道),
 * **不碰任何产品状态**;读数显式回显两个槽位的实际内容,读不到就明说。
 */
function doNardiEquip(ctx, tag, whatText) {
    var p = ctx.source.getPlayerOrException();
    var what = ("" + whatText) === "teru" ? "teru" : "chip";
    var slotId = (what === "teru") ? "stand" : "chip";
    var itemId = (what === "teru") ? NARDIS_TERU_SIGN_ID : NARDIS_PILL_CHIP_ID;
    var item = resolveItem(itemId);
    var err = "";
    if (item == null) { send(ctx, "AP_" + tag + "_EQUIP:err=unknown_item:" + itemId); return 1; }
    var slots = -1, wrote = 0;
    try {
        var h = curioHandler(p, slotId);
        if (h == null) { err = "no_slot:" + slotId; }
        else {
            var stacks = h.getStacks();
            slots = stacks.getSlots() - 0;
            if (slots <= 0) { err = "slot_size_zero:" + slotId; }
            else {
                stacks.setStackInSlot(0, ItemStack.EMPTY);
                stacks.setStackInSlot(0, new ItemStack(item, 1));
                wrote = 1;
            }
        }
    } catch (e1) { err = domExecText(e1); }
    var back = "-";
    try {
        var h2 = curioHandler(p, slotId);
        if (h2 != null) back = itemIdOf(h2.getStacks().getStackInSlot(0));
    } catch (e2) { back = "?"; }
    send(ctx, "AP_" + tag + "_EQUIP:what=" + what + ":slot=" + slotId + ":slots=" + slots
        + ":wrote=" + wrote + ":back=" + back
        + (err === "" ? "" : ":err=" + err)
        + ":" + domStateRead(p));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ⑪ nardiinv / nardiuseup —— 新语义(N3/N5/N6)所需脚手架
// ══════════════════════════════════════════════════════════════════════════

/**
 * `/astralprobe nardiinv <tag> <fill|clear> [reserve]` —— 控制**主物品栏 0..35** 的可用格数。
 *
 * <p>`fill`(缺省 reserve=2)用 `minecraft:stone` 把空槽填到**恰好剩 `reserve` 格**;
 * `clear` 直接清空主物品栏。用途(= **M6** 安全门的三个对照态,阈值
 * `TemporaryCardUtil.MIN_FREE_SLOTS_TO_CAST = 2`,即「可用格 &lt; 2 才拒绝」):
 * <ul>
 *   <li>`fill 1` ⇒ 可用格 1 (**&lt; 2**) ⇒ **拒绝释放**且零消耗;</li>
 *   <li>`fill 2` ⇒ 可用格 2 (**= 2**) ⇒ **允许释放**(边界组:实发 2 或 3 张,见用例说明);</li>
 *   <li>`clear` ⇒ 腾空后**立刻可释放**。</li>
 * </ul>
 * 只动主物品栏(0..35),不碰副手/骰子/curios。
 */
function doNardiInv(ctx, tag, modeText, reserveText) {
    var p = ctx.source.getPlayerOrException();
    var mode = ("" + modeText) === "clear" ? "clear" : "fill";
    var reserve = teruInt(reserveText, 2);
    if (reserve < 0) reserve = 0;
    var freeBefore = domFreeSlots(p);
    var filler = resolveItem("minecraft:stone");
    var err = "";
    if (mode === "clear") {
        try {
            for (var i = 0; i < 36; i++) p.getInventory().setItem(i, ItemStack.EMPTY);
        } catch (e1) { err = domExText(e1); }
    } else {
        if (filler == null) { send(ctx, "AP_" + tag + "_INV:err=unknown_item:minecraft:stone"); return 1; }
        try {
            // 先清空,再按「保留 reserve 格」填充 —— 幂等、与进入时的状态无关
            for (var c = 0; c < 36; c++) p.getInventory().setItem(c, ItemStack.EMPTY);
            var toFill = 36 - reserve;
            for (var k = 0; k < toFill; k++) p.getInventory().setItem(k, new ItemStack(filler, 1));
        } catch (e2) { err = domExText(e2); }
    }
    var freeAfter = domFreeSlots(p);
    send(ctx, "AP_" + tag + "_INV:mode=" + mode + ":reserve=" + reserve
        + ":free_before=" + freeBefore + ":free_after=" + freeAfter
        + (err === "" ? "" : ":err=" + err)
        + ":" + domStateRead(p));
    return 1;
}

/**
 * `/astralprobe nardicd <tag>` —— **只复位玩家级主动冷却**（`sign_active_cooldown_end = 0`），别的一概不碰。
 *
 * <p>为什么需要它（新语义 **M2**「叠加再发」）：效果生效期间再次释放**只受冷却限制**，
 * 而探针要把「冷却」这一项前置条件归零才不会误判成「被冷却拒绝」。可选的两条路：
 * <ul>
 *   <li>`nardicast <tag> self reset` —— 走 `domResetActive`，会**同时**清冷却与锁定键
 *       （经共享段的 `resetActiveLock`）⇒ 作用域过大，可能顺带改掉与本用例无关的状态；</li>
 *   <li>**本命令** —— 只写 `sign_active_cooldown_end`，不碰锁定键 / 效果 / 临时牌，
 *       使 M2 的读数**只反映产品行为**（契约明确要求「用脚手架复位冷却后再释放，**不要传 reset**」）。</li>
 * </ul>
 * 读数：`cd_before`/`cd_after`/`cleared`。
 */
function doNardiCd(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var before = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var err = "";
    try { ModAttachments.setSignActiveCooldownEnd(p, 0); } catch (e1) { err = domExText(e1); }
    var after = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    send(ctx, "AP_" + tag + "_CD:cd_before=" + before + ":cd_after=" + after
        + ":cleared=" + ((after === 0) ? 1 : 0)
        + (err === "" ? "" : ":err=" + err)
        + ":" + domStateRead(p));
    return 1;
}

/**
 * `/astralprobe nardiuseup <tag> [drive]` —— 把身上的临时牌**清到 0**（脚手架）。
 *
 * <p>**语义澄清（回答主代理的问题）**：本命令是**「清空」而非「逐张消耗」** ——
 * 它调产品的 `TemporaryCardUtil#purgeAll(player)`（与产品清理同一入口），一次性移除
 * **主物品栏 0..35 + 副手 + 骰子 `weapon_enhancement` 里 `temporary=true` 的已装配项**。
 * 从 JS **无法**真实逐张「使用」卡牌（战斗牌要装备进骰子、效果牌要走各自的使用逻辑并结算），
 * 而契约只观测**张数**（`n_temp` / `n_temp_eq`），故「清空到 0」与「用光」在读数上等价。
 *
 * <p>为什么新语义下还需要它（**M3**「牌用光不再结束效果」）：产品本轮已把「无牌 ⇒ 移除效果」
 * 这条收口**删除**，只保留「无效果 ⇒ 清牌」⇒ 本命令正是验证「清到 0 之后**效果仍在**」的手段。
 *
 * <p>mode：
 * <ul>
 *   <li>缺省（`drive=0`）—— 只 `purgeAll`，**不碰效果**；随后由**自然 tick** 证明效果不被移除（M3 主判据）；</li>
 *   <li>`drive=1` —— 额外**显式驱动**产品自己的 `TemporaryCardUtil#tick`（双向收口入口，
 *       与玩家 tick 事件调的是同一份代码），把「产品自检在 0 张时**不得**移除效果」变成**同一行**的直接读数；
 *       再驱动 `BaseSignItem#tickSignActiveLock`（本主动已无锁定态，此调用是空操作，仅保证与玩家 tick 同形）。</li>
 * </ul>
 * 读数：`purged`（实际移除张数）、`temp_before`/`temp_after`、`temp_eq_*`、`fx_on_before`/`fx_on_after`、
 * `n_fx_after`、`cd_before_purge`/`cd_after`（清牌**不得**动冷却）、`lock_before`/`lock_after`（应恒为 0）。
 */
function doNardiUseUp(ctx, tag, modeText) {
    var p = ctx.source.getPlayerOrException();
    var drive = ("" + modeText) === "drive" ? 1 : 0;
    var before = domCountTemp(p);
    var beforeEq = domCountTempEquipped(p);
    // 清牌前的冷却/锁定/效果快照(用于证明「清牌」这一步不越界改别的状态)
    var cd0 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var lock0 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); });
    var fx0 = domFx(p);
    var purged = domClearTemp(p);
    // `drive` 模式:显式驱动**产品自己的**两个 tick 入口(与玩家 tick 事件调的是同一份代码,
    // 只是同步跑在命令里)⇒ 把「产品自检面对 0 张时的行为」放进**同一行**读数。
    //   · TemporaryCardUtil#tick = 双向收口;**新语义下**「无牌」**不得**再移除效果(只保留「无效果 ⇒ 清牌」);
    //   · BaseSignItem#tickSignActiveLock = 锁定→冷却迁移(本主动已无锁定态 ⇒ 空操作)。
    if (drive === 1) {
        try { NardisTemporaryCardUtilClass.tick(p); } catch (eD1) { /* 交给读数 */ }
        try { BaseSignItemClass.tickSignActiveLock(p); } catch (eD2) { /* 交给读数 */ }
    }
    var cd1 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var lock1 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); });
    var fx1 = domFx(p);
    send(ctx, "AP_" + tag + "_USEUP:drive=" + drive
        + ":purged=" + purged
        + ":temp_before=" + before + ":temp_after=" + domCountTemp(p)
        + ":temp_eq_before=" + beforeEq + ":temp_eq_after=" + domCountTempEquipped(p)
        + ":n_temp=" + domCountTemp(p) + ":n_temp_eq=" + domCountTempEquipped(p)
        + ":cd_before_purge=" + cd0 + ":cd_after=" + cd1
        + ":cd_unchanged=" + ((cd0 >= 0 && cd1 >= 0 && cd0 === cd1) ? 1 : 0)
        + ":lock_before=" + lock0 + ":lock_after=" + lock1
        + ":fx_on_before=" + fx0.on + ":fx_on_after=" + fx1.on
        + ":n_fx_before=" + fx0.dur + ":n_fx_after=" + fx1.dur
        + ":" + domStateRead(p));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ⑪c nardirecast —— M2「叠加再发 + 时长重置可见」的一键取证(含 +5/+10 tick 持续检查)
// ══════════════════════════════════════════════════════════════════════════

/**
 * M2 的**待检**状态(由下面的 `ServerEvents.tick` 消费)。
 * 为什么需要它:契约要求「重置必须**看得出来是重置**」—— 不仅释放**当刻**要读到 3600,
 * 还要证明**其后若干 tick 没有被 `EffectTimerGuard` 的 CLAMP(`CLAMP_TOLERANCE = 20`)裁回旧值**。
 * 跨行比较在断言层做不到,故把「+5 / +10 tick 时的效果剩余」直接落成**独立读数行**。
 */
var nardisRecastPending = null;

/**
 * tick 侧消费:在重发后第 5 / 第 10 个服务端 tick 各报一次读数(无命令上下文 ⇒ 走 `emitTo`)。
 * 只在 `nardisRecastPending != null` 时工作(平时是一条早退,零开销);异常自愈(清空待检)。
 */
ServerEvents.tick(event => {
    var pend = nardisRecastPending;
    if (pend == null) return;
    try {
        pend.elapsed = pend.elapsed + 1;
        if (pend.elapsed === 5 || pend.elapsed === 10) {
            var p = pend.player;
            var fx = domFx(p);
            emitTo(p, "AP_" + pend.tag + "_RECASTT" + pend.elapsed
                + ":elapsed=" + pend.elapsed
                + ":n_fx=" + fx.dur + ":n_fx_on=" + fx.on
                + ":n_temp=" + domCountTemp(p) + ":n_temp_eq=" + domCountTempEquipped(p)
                + ":now=" + (nowTick(p) - 0));
        }
        if (pend.elapsed >= 10) nardisRecastPending = null;
    } catch (e) {
        nardisRecastPending = null;
    }
});

/**
 * `/astralprobe nardirecast <tag>` —— M2 一键取证:读旧值 → **只复位冷却** → 真实释放 → 读新值。
 *
 * <p>为什么要合成一条命令(而不是「`nardicd` + `nardicast` + 两次 `nardiread`」):
 * 「重置」的判据是**旧剩余与新剩余的关系**(`fx_after_cast` 必须回到满值、且**跳变幅度** `fx_jump` 显著为正),
 * 而跨行比较在用例断言层无法做;把两侧放**同一行**后,M2 的「看得出来是重置」就是一条**单行等式/不等式**。
 *
 * <p>契约要求复位冷却时**不要传 `reset`** ⇒ 这里只写 `sign_active_cooldown_end = 0`
 * (不碰锁定键 / 效果 / 临时牌),等价于内联一次最小作用域的 `nardicd`。
 *
 * <p>读数:`fx_before`(旧剩余)、`fx_after_cast`(**必须 = 3600**)、`fx_jump`(= 新 − 旧,必须为正)、
 * `temp_before`/`temp_after`(叠加)、`temp_eq_*`、`cd_before`/`cd_after`、`lock_*`。
 * 随后由上面的 tick 钩子产出 `AP_<tag>_RECASTT5` / `_RECASTT10`(证明重置**持续**有效、未被 CLAMP 裁回)。
 */
function doNardiRecast(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var now0 = nowTick(p) - 0;
    var fx0 = domFx(p);
    var temp0 = domCountTemp(p);
    var eq0 = domCountTempEquipped(p);
    var cd0 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var cdErr = "";
    try { ModAttachments.setSignActiveCooldownEnd(p, 0); } catch (e0) { cdErr = domExText(e0); }
    var lock0 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); });
    var err = "";
    try { BaseSignItemClass.performSkillForCurio(p); } catch (e1) { err = domExText(e1); }
    var fx1 = domFx(p);
    var temp1 = domCountTemp(p);
    var eq1 = domCountTempEquipped(p);
    // 挂上持续检查(第 5 / 10 tick 各一行读数)
    nardisRecastPending = { player: p, tag: tag, elapsed: 0 };
    send(ctx, "AP_" + tag + "_RECAST:now=" + now0
        + ":fx_before=" + fx0.dur + ":fx_on_before=" + fx0.on
        + ":fx_after_cast=" + fx1.dur + ":fx_on_after=" + fx1.on
        + ":fx_jump=" + (((fx0.dur >= 0) && (fx1.dur >= 0)) ? (fx1.dur - fx0.dur) : -9999)
        + ":temp_before=" + temp0 + ":temp_after=" + temp1
        + ":temp_eq_before=" + eq0 + ":temp_eq_after=" + eq1
        + ":cd_before=" + cd0 + ":cd_after=" + domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); })
        + ":lock_before=" + lock0
        + ":lock_after=" + domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); })
        + (cdErr === "" ? "" : ":cd_err=" + cdErr)
        + (err === "" ? "" : ":err=" + err)
        + ":" + domStateRead(p));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ⑪b nardilockback —— **当前无任何用例使用**（保留的通用锁定态脚手架）
// ══════════════════════════════════════════════════════════════════════════

/**
 * `/astralprobe nardilockback <tag> [keep|noeffect]` —— 通用「锁定硬上界」脚手架。
 *
 * <p>⚠️ **状态：当前无用例使用（有意保留，不删）**。
 * 它原本是 nardis 「冻结（使用中）」机制的 **N8 / F1 重对齐**回归工具；2026-09-27 用户二次裁决
 * **撤回了 nardis 的整台冻结机械**（含 F1 的 `gateEffectRemainingTicks` / `realignLockEndToGateEffect`），
 * 故 N8 组已从 `NARDIS-SIGN-{1.21.1,1.20.1}.json` **整组删除**。
 *
 * <p>**保留理由**：本命令**不含任何 nardis 专属语义** —— 它只写**通用**的 `sign_active_lock_end`
 * 并驱动**通用**的 `BaseSignItem#tickSignActiveLock`，对任何使用 `sign_active_lock_*` 的立牌
 * （komachi / fen / jasmine 等）都成立；本条需求在本会话内已被**两次**推翻/重立，
 * 下一个「锁定硬上界」类回归（例如离线跨越、宽限期保险）仍然需要同一套手法，
 * 而删掉它要再付一次「探针改动 ⇒ 冷启动」的全量代价。保留成本 = 0（命令按需求值，用例不调用即不执行）。
 *
 * <p>本命令做两件事：
 * <ol>
 *   <li>把 `sign_active_lock_end` 写到**过去**（`max(1, now-200)`）—— **只改这一项**：
 *       `sign_active_lock_sign` / `grace_end` / `played` 与 `sign_active_cooldown_end` 一概不动；</li>
 *   <li>按 `mode` 可选地先移除门控效果（`noeffect`），再显式驱动
 *       `BaseSignItem#tickSignActiveLock`（与玩家 tick 事件调的是同一份代码），
 *       让「硬上界已过时产品怎么处理」在同一行读数里可判。</li>
 * </ol>
 *
 * <p>mode（缺省 `keep`）：
 * <ul>
 *   <li><b>`keep`</b> —— 效果与临时牌都保持不动；若产品侧存在「按门控效果剩余时长重对齐硬上界」
 *       的机制，则应观测到 `realigned=1` / `lock_end_after &gt; now`。</li>
 *   <li><b>`noeffect`</b> —— 先用内部通道 `ModEffectRemoval` 移除效果（与 3:00 到期同一条路）、
 *       再驱动 `TemporaryCardUtil#tick` 清牌，然后才写 `lock_end` 到过去并 tick
 *       ⇒ 用作任何「重对齐需要门控效果仍在」类断言的**对照侧**。</li>
 * </ul>
 */
function doNardiLockBack(ctx, tag, modeText) {
    var p = ctx.source.getPlayerOrException();
    var mode = ("" + modeText) === "noeffect" ? "noeffect" : "keep";
    var now = nowTick(p) - 0;
    var lockEnd0 = domNum(function () { return ModAttachments.getSignActiveLockEnd(p); });
    var cd0 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var lock0 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); });
    var fx0 = domFx(p);
    var temp0 = domCountTemp(p);
    var eq0 = domCountTempEquipped(p);
    // ⚠️ 必须写在**正数**的过去刻:任何「硬上界已过」判定通常以 `lockEnd <= 0` 表示「无硬上界」
    //    （忍者语义）⇒ 写成 0/负数会退化成「没有硬上界」而不是「上界已过」。本命令与 nardis 无关。
    var past = now - 200;
    if (past < 1) past = 1;
    var wrote = -1, err = "";
    try { ModAttachments.setSignActiveLockEnd(p, past); wrote = past; }
    catch (e1) { err = domExText(e1); }
    // 要求 2(2026-09-27):所有「直接把 sign_active_lock_end 写成合成值」的脚手架,都必须顺手把
    // **离线补偿基准**写成本刻 —— 否则上一用例把 lastSeen 写到过去之后,本命令构造的锁定态会被
    // 下一拍当成一次离线 gap 误补偿(把上界凭空后移 ⇒ 假红/假绿)。真实施放路径不受影响。
    try { ModAttachments.setSignActiveLockLastSeen(p, now); } catch (e1b) { err = err + "|seen:" + domExText(e1b); }
    if (mode === "noeffect") {
        try { ModEffectRemoval.remove(p, domEffectHolder()); } catch (e2) { err = err + "|fx:" + domExText(e2); }
        try { NardisTemporaryCardUtilClass.tick(p); } catch (e3) { err = err + "|purge:" + domExText(e3); }
    }
    var tickRan = 1;
    try { BaseSignItemClass.tickSignActiveLock(p); }
    catch (e4) { tickRan = 0; err = err + "|tick:" + domExText(e4); }
    var lockEnd1 = domNum(function () { return ModAttachments.getSignActiveLockEnd(p); });
    var cd1 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var lock1 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); });
    var fx1 = domFx(p);
    send(ctx, "AP_" + tag + "_LOCKBACK:mode=" + mode
        + ":now=" + now + ":wrote_lock_end=" + wrote
        + ":lock_end_before=" + lockEnd0 + ":lock_end_after=" + lockEnd1
        + ":realigned=" + ((lockEnd1 > now) ? 1 : 0)
        + ":lock_end_after_gt_now=" + ((lockEnd1 > now) ? 1 : 0)
        + ":lock_before=" + lock0 + ":lock_after=" + lock1
        + ":cd_before=" + cd0 + ":cd_after=" + cd1
        + ":cd_unchanged=" + ((cd0 >= 0 && cd1 >= 0 && cd0 === cd1) ? 1 : 0)
        + ":temp_before=" + temp0 + ":temp_after=" + domCountTemp(p)
        + ":temp_eq_before=" + eq0 + ":temp_eq_after=" + domCountTempEquipped(p)
        + ":fx_on_before=" + fx0.on + ":fx_on_after=" + fx1.on
        + ":n_fx_after=" + fx1.dur
        + ":tick_ran=" + tickRan
        + (err === "" ? "" : ":err=" + err)
        + ":" + domStateRead(p));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ⑫ nardinuclear —— 收尾归零
// ══════════════════════════════════════════════════════════════════════════

/** 收尾:清临时牌 + 清效果 + 卸立牌 + 清主手 + 复位冷却(全部幂等) */
function doNardiClearAll(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var err = "";
    var cleared = 0;
    try { cleared = NardisTemporaryCardUtilClass.purgeAll(p); } catch (e0) { cleared = -1; err = err + "purge:" + domExText(e0); }
    try { ModEffectRemoval.remove(p, domEffectHolder()); } catch (e1) { /* 已不在也可 */ }
    var standErr = "";
    try { standErr = equipSign(p, ""); } catch (e2) { standErr = domExText(e2); }
    try { clearCurioSlots(p, "stand"); } catch (e3) { /* 忽略 */ }
    try { p.setItemInHand(NardiHandClass.MAIN_HAND, ItemStack.EMPTY); } catch (e4) { /* 忽略 */ }
    var invErr = domClearMain(p);
    domResetActive(p);
    var signLeft = domSignEquipped(p);
    send(ctx, "AP_" + tag + "_CLEARALL:cleared=1"
        + ":purged=" + cleared
        + ":sign_left=" + signLeft
        + ":ok=" + ((signLeft === 0 && domCountTemp(p) === 0 && domCountTempEquipped(p) === 0) ? 1 : 0)
        + (err === "" ? "" : ":err=" + err)
        + (invErr === "" ? "" : ":inv_err=" + invErr)
        + ":" + domStateRead(p));
    return 1;
}

// ══════════════════════════════════════════════════════════════════════════
//  ⑬ 立牌锁定 · 离线时钟漂移补偿（signlag 套件）—— 2026-09-27 新增
// ══════════════════════════════════════════════════════════════════════════
//
//  【本轮取证对象】产品侧「锁定态离线时钟漂移」修复:
//    `sign_active_lock_end` / `sign_active_lock_grace_end` 是**绝对 gameTime**(离线照走),
//    而门控效果的剩余时长**离线冻结** ⇒ 多人服务器上「离线时长 > 上界剩余」后重登,
//    上界已过而效果仍在 ⇒ 锁定提前结束、主动技能提前可用(parunan 15 分钟村庄英雄最严重)。
//    修法:新增**服务端专用、不同步**的玩家附件 `sign_active_lock_last_seen`,
//    在 `tickSignActiveLock` **最前面**(早于锁定判定与任何早退)补偿:
//      gap = now − last_seen;仅当 gap > 1 时把 lock_end 与 grace_end(各自 >0 时)都 += gap;
//      随后把 last_seen 更新为 now(无锁定时置 0)。
//    ⚠️ **不读任何效果实例**(避免把袭击给的村庄英雄/别人泼的虚弱误算成本技能计时器)、
//    **不碰 `sign_active_cooldown_end`**(冷却照旧离线也流逝)、**无 per-sign 代码**。
//
//  【为什么写 / 为何 write 与 drive 必须在**同一次命令执行**内】
//    真实玩家的 `PlayerTickEvents` 每个 game tick 都会调 `tickSignActiveLock`。
//    若把「把 last_seen 写到过去」与「驱动 tick」拆成两条命令(相隔 ≥1 tick),
//    中间那些**真实 tick** 会先把补偿吃干(并刷新 last_seen)⇒ 读数无法归因。
//    ⇒ `signlag` 一律「写 → 驱动 → 读」压在一次执行里(服务端命令不与 tick 交错)。
//
//  【last_seen 附件访问:候选名自动绑定】
//    本体实现落地前无法确定访问器命名,故按候选表**运行时探测**并缓存,读数里回显
//    `ls_api=`(哪条绑上了),绑不上时 `ls_api=none` —— 用例据此**显式 FAIL**,而不是静默读到 -1。
//      · 方案 A(访问器): `ModAttachments.get|setSignActiveLockLastSeen[(Tick)]`
//      · 方案 B(附件常量): `ModAttachments.SIGN_ACTIVE_LOCK_LAST_SEEN[( _TICK)]` + `p.getData/setData`
//    `ins` = 候选绑定失败的原因文本(便于直接定位是命名不符还是附件缺失)。
//
//  【判据标签 L1–L7 与本段命令的对应】
//    L1 补偿生效      → `signlag <tag> 6000`          (lock_end_delta/grace_end_delta = 6000)
//    L1b 阈值边界     → `signlag <tag> 1` / `signlag <tag> 2`   (gap==1 不补 / gap==2 补 2)
//    L2 正常 tick 不动 → `signlag <tag> 1 triple`     (triple_stable=1)
//    L3 同 tick 幂等   → `signlag <tag> 6000 double`  (idem=1)
//    L4 不碰冷却      → 上述三条读数里的 `cd_unchanged=1`
//    L5 锁定仍会结束   → `signend <tag>`(内部通道移除门控效果 + 驱动 tick)
//    L6 忍者宽限同补偿 → `signprep … komachi_sign` → `signcast` → `signlag <tag> 6000`
//                        (lock_end_delta=0 且 grace_end_delta=6000)
//    L7 两线一致      → 两线跑同一用例后比对读数

/** 星光管理器(parunan 主动的前置;类缺失时静默降级为 null,读数里以 -1 体现) */
var SignLagStarLightClass = null;
try { SignLagStarLightClass = Java.loadClass("com.merlinkitsune.astral_dice.item.StarLightManager"); }
catch (eSl) { SignLagStarLightClass = null; }

/** last_seen 附件绑定的候选名(先访问器、后附件常量) */
var SIGNLAG_GETTERS = ["getSignActiveLockLastSeen", "getSignActiveLockLastSeenTick"];
var SIGNLAG_SETTERS = ["setSignActiveLockLastSeen", "setSignActiveLockLastSeenTick"];
var SIGNLAG_HOLDERS = ["SIGN_ACTIVE_LOCK_LAST_SEEN", "SIGN_ACTIVE_LOCK_LAST_SEEN_TICK"];
/** 绑定结果缓存(整个脚本只探测一次) */
var signLagBindCache = { tried: false, how: "none", get: "", set: "", holder: null, note: "", ins: "" };

/**
 * 探测 `sign_active_lock_last_seen` 的读写通道。
 * 返回缓存对象:`how` ∈ `accessor:<名>` | `attachment:<常量名>` | `none`。
 */
function signLagBind() {
    if (signLagBindCache.tried) return signLagBindCache;
    signLagBindCache.tried = true;
    // 方案 A:成对的 get/set 访问器
    for (var i = 0; i < SIGNLAG_GETTERS.length; i++) {
        var g = SIGNLAG_GETTERS[i], s = SIGNLAG_SETTERS[i];
        var okG = false, okS = false;
        try { okG = (typeof ModAttachments[g] === "function"); } catch (eG) { okG = false; }
        try { okS = (typeof ModAttachments[s] === "function"); } catch (eS) { okS = false; }
        if (okG && okS) {
            signLagBindCache.how = "accessor:" + g;
            signLagBindCache.get = g; signLagBindCache.set = s;
            return signLagBindCache;
        }
        signLagBindCache.ins = signLagBindCache.ins + "|no:" + g;
    }
    // 方案 B:附件常量 + 通用 getData/setData(与产品内 `player.getData(XXX.get())` 同形)
    for (var j = 0; j < SIGNLAG_HOLDERS.length; j++) {
        var hn = SIGNLAG_HOLDERS[j];
        try {
            var holder = ModAttachments[hn];
            if (holder != null) {
                signLagBindCache.how = "attachment:" + hn;
                signLagBindCache.holder = holder;
                return signLagBindCache;
            }
        } catch (eH) { signLagBindCache.ins = signLagBindCache.ins + "|holder_ex:" + domExText(eH); }
        signLagBindCache.ins = signLagBindCache.ins + "|no:" + hn;
    }
    signLagBindCache.how = "none";
    return signLagBindCache;
}

/** `sign_active_lock_last_seen` 读(-1 = 读不到;绑定失败时把原因记进 note) */
function domLastSeen(p) {
    var b = signLagBind();
    try {
        if (b.how.indexOf("accessor:") === 0) return ModAttachments[b.get](p) - 0;
        if (b.how.indexOf("attachment:") === 0) return p.getData(b.holder.get()) - 0;
    } catch (e1) { signLagBindCache.note = domExText(e1); return -1; }
    return -1;
}

/** `sign_active_lock_last_seen` 写(0 = 成功;-1 = 失败) */
function domSetLastSeen(p, v) {
    var b = signLagBind();
    try {
        if (b.how.indexOf("accessor:") === 0) { ModAttachments[b.set](p, v); return 0; }
        if (b.how.indexOf("attachment:") === 0) { p.setData(b.holder.get(), v); return 0; }
    } catch (e1) { signLagBindCache.note = domExText(e1); return -1; }
    return -1;
}

/** 忍者宽限刻(`sign_active_lock_grace_end`;-1 = 读不到) */
function domGraceEnd(p) {
    return domNum(function () { return ModAttachments.getSignActiveLockGraceEnd(p); });
}

/** 锁定减免池(`sign_active_reduction_pool`) */
function domReductionPool(p) {
    return domNum(function () { return ModAttachments.getSignActiveReductionPool(p); });
}

/** 本次冷却基准(`sign_active_max_cooldown`) */
function domMaxCooldown(p) {
    return domNum(function () { return ModAttachments.getSignActiveMaxCooldown(p); });
}

/** 忍者「宽限期内出过效果牌」标记(`sign_active_lock_played`) */
function domLockPlayed(p) {
    return domBool(function () { return ModAttachments.getSignActiveLockPlayed(p) ? 1 : 0; });
}

/**
 * 取一个 `MobEffects` 常量(**跨线命名安全**)。
 * ⚠️ 两线共用同一份探针源,而原版效果常量在 1.20.5+ 有改名(`CONFUSION`→`NAUSEA`、
 *    `DIG_SLOWDOWN`→`MINING_FATIGUE`)。本探针**不得**假定某个名字两线都存在:
 *    取不到(抛错或 `null`)一律返回 `null`,由调用方过滤掉 ⇒ 表退化为「该效果不参与清理」,
 *    绝不会让整条读数链因一个常量名而崩掉(与「新 jar 未落地时读数退化为 -1」同一策略)。
 */
function signLagHold(name) {
    try {
        var v = MobEffectsClass[name];
        return (v == null) ? null : v;
    } catch (e) { return null; }
}

/** 过滤掉取不到的 holder(保留顺序) */
function signLagCompact(list) {
    var out = [];
    for (var i = 0; i < list.length; i++) { if (list[i] != null) out.push(list[i]); }
    return out;
}

/**
 * 各锁定类立牌的**门控效果**表(逐条读产品源码得来;探针侧脚手架,产品无此表)。
 * 用途:`signend`(L5)走内部通道移除门控效果 ⇒ 下一拍锁定应结束;
 *        `signcast` / `signstate` 的 `gate_on` 读数。
 * ⚠️ 表是 `signId → 效果 holder 列表`;`komachi` 无门控效果(锁定只跟随出牌周期)。
 *
 * 来源(1.21.1):
 *   parunan  ParunanSignItem.java:76-77   SATURATION / LUCK / HERO_OF_THE_VILLAGE
 *   jasmine  JasmineSignItem.java:86      ModEffects.JASMINE_SWEEP
 *   fen      FenSignItem.java:117         ModEffects.FEN_FRENZY
 *   fanny    FannySignItem.java:101-105   12 个原版效果(再生/力量/迅捷/瞬间伤害/中毒/饥饿/反胃/凋零/黑暗/虚弱/挖掘疲劳/饱和)
 *   misaki   MisakiSignItem.java:59       ModEffects.MISAKI_BURST
 *   papara   PaparaSignItem.java:67       ModEffects.PAPARA_BITE
 *   nancy_lu NancyLuSignItem.java:141     MobEffects.INVISIBILITY
 *   komachi  —(无)
 *
 * ⚠️ **本用例只对 parunan / jasmine 实跑**;其余条目是「同一条共享代码路径」的登记,
 *    表本身按产品源码逐条核对(行号如上),但**未经实机双验**(见用例说明的覆盖清单)。
 *    fanny 的表项经 `signLagHold` 跨线安全取值(`CONFUSION`/`DIG_SLOWDOWN` 两线命名有别)。
 */

/**
 * 把**立牌专属效果常量**归一为 `Holder<MobEffect>`(本文件既有口径;与 `domEffectHolder`/`mamuEffectHolder` 同形)。
 *
 * <p>⚠️ **必须归一,不得裸传**(2026-09-27 实机,1.20.1 整线全灭):`ModEffects.X` 在 1.21.1 是
 * `DeferredHolder`(**本身就是 `Holder`**),在 1.20.1 是 `RegistryObject`(**不是** `Holder`)⇒
 * 1.20.1 上把它喂给 `ModEffectRemoval.remove(p, holder)` 会在 **Rhino 参数转换**阶段抛
 * `ResourceLocationException: Non [a-z0-9/._-] character in path of location:
 * minecraft:net.minecraftforge.registries`,而**参数转换错误逃得出 JS `try/catch` 与 `guard`**
 * (与本文件既有的 `p.hasEffect(ModEffects.X)` 同型)⇒ `signClearAllGateEffects` 中断 ⇒
 * `doSignReset`/`doSignPrep` **在 `send(...)` 之前退出** ⇒ **整条用例零读数**
 * (实测 `LOCK-OFFLINE-1.20.1` 30 条断言全 FAIL、`AP_L0_SIGNRESET` 命中 0)。
 * `c.get()` 两线都可用(`DeferredHolder` / `RegistryObject` 都实现 `get()`);**注册未完成**(取到 `null`)
 * 时返回 `null`(由调用方经 `signLagCompact` 过滤掉)而**不是**回落成 `RegistryObject` 裸传 —— 只有
 * `get()` **抛错**(= `c` 本身已是 `Holder`,即 1.21.1 的 `DeferredHolder`)才原样返回。
 */
function signLagFxHolder(c) {
    try {
        var g = c.get();
        if (g != null) return g;
        return null;   // 1.20.1:RegistryObject 未绑定 ⇒ 绝不能回落成 RegistryObject(裸传会打死整条命令)
    } catch (e1) { /* 落到下面 */ }
    return c;          // 1.21.1:DeferredHolder 自身已经是 Holder
}

function signGateHolders(signId) {
    var id = "" + signId;
    try {
        if (id === "astral_dice:parunan_sign") {
            return signLagCompact([signLagHold("SATURATION"), signLagHold("LUCK"), signLagHold("HERO_OF_THE_VILLAGE")]);
        }
        if (id === "astral_dice:jasmine_sign") return signLagCompact([signLagFxHolder(ModEffects.JASMINE_SWEEP)]);
        if (id === "astral_dice:fen_sign") return signLagCompact([signLagFxHolder(ModEffects.FEN_FRENZY)]);
        if (id === "astral_dice:misaki_sign") return signLagCompact([signLagFxHolder(ModEffects.MISAKI_BURST)]);
        if (id === "astral_dice:papara_sign") return signLagCompact([signLagFxHolder(ModEffects.PAPARA_BITE)]);
        if (id === "astral_dice:nancy_lu_sign") return signLagCompact([signLagHold("INVISIBILITY")]);
        if (id === "astral_dice:fanny_sign") {
            return signLagCompact([signLagHold("REGENERATION"), signLagHold("DAMAGE_BOOST"),
                signLagHold("MOVEMENT_SPEED"), signLagHold("HARM"), signLagHold("POISON"),
                signLagHold("HUNGER"), signLagHold("CONFUSION"), signLagHold("WITHER"),
                signLagHold("DARKNESS"), signLagHold("WEAKNESS"),
                signLagHold("DIG_SLOWDOWN"), signLagHold("SATURATION")]);
        }
    } catch (eT) { return []; }
    return [];   // komachi 与未登记立牌
}

/** 当前门控效果是否仍在(表内任一在册即为 1;表为空时返回 0 ⇒ 表外立牌不参与该读数) */
function domGateOn(p, signId) {
    var hs = signGateHolders(signId);
    var n = 0;
    for (var i = 0; i < hs.length; i++) {
        try { if (p.hasEffect(hs[i])) n++; } catch (eH) { /* 忽略 */ }
    }
    return n > 0 ? 1 : 0;
}

/** 表内门控效果的「仍在」计数(用于 signend 后确认已清干净) */
function domGateCount(p, signId) {
    var hs = signGateHolders(signId);
    var n = 0;
    for (var i = 0; i < hs.length; i++) {
        try { if (p.hasEffect(hs[i])) n++; } catch (eH) { /* 忽略 */ }
    }
    return n;
}

/**
 * 走**内部通道**(`ModEffectRemoval`)移除某立牌的全部门控效果。
 * ⚠️ 必须走内部通道:产品 `ModEffectEvents#onModEffectRemovalPrevented` 会拦掉一切**外部**
 *    移除(`/effect clear` 对本模组效果无效,已实测);放行的只有内部移除 / EffectTimerGuard / 死亡。
 * 返回「实际尝试移除的条数」;失败原因写进 `errOut`(对象,按引用回填)。
 */
function signClearGateEffects(p, signId, errOut) {
    var hs = signGateHolders(signId);
    var n = 0;
    for (var i = 0; i < hs.length; i++) {
        try { ModEffectRemoval.remove(p, hs[i]); n++; } catch (eR) { if (errOut) errOut.err = errOut.err + "|rm:" + domExText(eR); }
    }
    return n;
}

/** 清掉全部**已登记**立牌的门控效果(基线用:避免上一个立牌的效果让当前立牌误判为「仍在锁定」) */
function signClearAllGateEffects(p) {
    var ids = ["astral_dice:parunan_sign", "astral_dice:jasmine_sign", "astral_dice:fen_sign",
        "astral_dice:fanny_sign", "astral_dice:misaki_sign", "astral_dice:papara_sign",
        "astral_dice:nancy_lu_sign"];
    var n = 0;
    for (var i = 0; i < ids.length; i++) {
        var hs = signGateHolders(ids[i]);
        for (var j = 0; j < hs.length; j++) {
            try { ModEffectRemoval.remove(p, hs[j]); n++; } catch (eA) { /* 忽略 */ }
        }
    }
    return n;
}

/**
 * 锁定态全量只读快照(**单行**,字段顺序固定;用例按子串断言)。
 * 字段:`now,sign,lock,lock_end,grace_end,cd_end,max_cd,pool,played,gate_on,gate_n,last_seen,ls_api,stand`
 */
function domLockStateRead(p) {
    var signId = domLockSign(p);
    var stand = 0;
    try { stand = domSignEquipped(p); } catch (eS) { stand = -1; }
    return "now=" + (nowTick(p) - 0)
        + ":sign=[" + signId + "]"
        + ":lock=" + domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); })
        + ":lock_end=" + domNum(function () { return ModAttachments.getSignActiveLockEnd(p); })
        + ":grace_end=" + domGraceEnd(p)
        + ":cd_end=" + domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); })
        + ":max_cd=" + domMaxCooldown(p)
        + ":pool=" + domReductionPool(p)
        + ":played=" + domLockPlayed(p)
        + ":gate_on=" + domGateOn(p, signId)
        + ":gate_n=" + domGateCount(p, signId)
        + ":last_seen=" + domLastSeen(p)
        + ":ls_api=" + signLagBind().how
        + ":stand=" + stand;
}

// ──────────────────────────────────────────────────────────────────────────
//  signreset —— 锁定态归零(每条 L 组从同一基线起测)
// ──────────────────────────────────────────────────────────────────────────
/**
 * 归零:stand 槽清空 + 出牌周期/主动冷却/全部锁定键归零(产品既有 5 键,经 `resetEffectCardCycle`)
 * + `last_seen` 置 0(**与产品「无锁定时置 0」同口径**) + 清掉全部已登记立牌的门控效果。
 * 读数:`gate_cleared,ls_wrote,ls_after,ls_api` + `domLockStateRead`。
 */
function doSignReset(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var err = "";
    var lsWrote = -1, lsAfter = -1, gateCleared = 0;
    try { clearCurioSlots(p, "stand"); } catch (e0) { err = err + "|stand:" + domExText(e0); }
    try { resetEffectCardCycle(p); } catch (e1) { err = err + "|cycle:" + domExText(e1); }
    gateCleared = signClearAllGateEffects(p);
    lsWrote = domSetLastSeen(p, 0);
    lsAfter = domLastSeen(p);
    send(ctx, "AP_" + tag + "_SIGNRESET:gate_cleared=" + gateCleared
        + ":ls_wrote=" + lsWrote + ":ls_after=" + lsAfter
        + ":ls_api=" + signLagBind().how
        + (signLagBind().ins === "" ? "" : ":ls_ins=" + signLagBind().ins)
        + (signLagBind().note === "" ? "" : ":ls_note=" + signLagBind().note)
        + (err === "" ? "" : ":err=" + err)
        + ":" + domLockStateRead(p));
    return 1;
}

// ──────────────────────────────────────────────────────────────────────────
//  signprep —— 装指定立牌 + 基线(按 **sign id 参数化**,无 per-sign 分支)
// ──────────────────────────────────────────────────────────────────────────
/**
 * 基线:stand 槽清空 → 出牌周期/主动冷却/锁定键归零 → 清全部已登记门控效果 →
 * `last_seen` 置 0 → 装**参数指定的**立牌 → 可选写星光(第 3 参)。
 *
 * 参数契约:`signprep <tag> <signId> [starlight]`
 *   · `signId` 用 `StringArgumentType.string()`(含冒号 ⇒ 用例侧必须带引号);
 *   · `starlight` 缺省 = 不写;给了数值就 `StarLightManager.set(p, n)` ——
 *     这是为了 parunan(主动要求星光 > 0 才生效)**不把 per-sign 逻辑写进探针**:
 *     前置条件由用例显式给,探针只提供通用的「写星光」能力。
 * 读数:`sign,stand,gate_cleared,star,ls_api` + `domLockStateRead`。
 */
/**
 * 只读快照:`stand`(立牌)饰品槽的**实际内容** —— 与具体立牌无关的通用装配证据。
 *
 * <p>⚠️ 为什么单独加这条:老读数 `domSignEquipped` 的实现是 `NardisSignItem.isEquipped`
 * (**只查 `NARDIS_SIGN`**),对 sherry / hanna 等其它立牌**恒为 0**,不能当"是否装上"的判据
 * (2026-09-21 实测:装配调用已成功、`sign_err` 为空,而 `stand` 仍读 0)。本命令直接回读槽内容,
 * 任何立牌都适用。
 *
 * <p>读数:`slot` = 槽内第一件物品的注册 id(`none` = 空)、`slots` = 槽位数、`used` = 非空槽数、
 * `ids` = 槽内全部物品 id 列表。
 */
/**
 * 把物品写进指定饰品槽的**指定序号**（`equipslot` 固定写 0 号槽，多件同类饰品会互相覆盖）。
 *
 * <p>需求来源：飞星「两枚筹码同装」的用例必须把紫色飞星与金色飞星分别放进 chip 槽的 0 / 1 号，
 * 而 `equipslot` 两次调用都写 0 号 ⇒ 只剩后写的那枚（2026-09-21 实证：SS-SHARED 靶子只掉 2 点）。
 *
 * <p>`/astralprobe slotput <tag> <slot> <index> <item>`；`item` 为注册 id（含 `:` ⇒ 必须加引号）。
 * 读数与 `equipslot` 同形：`AP_<tag>_SLOTPUT:slot=..:index=..:item=..`。
 */
function doSlotPut(ctx, tag, slotId, index, itemId) {
    var p = ctx.source.getPlayerOrException();
    var item = resolveItem(itemId);
    if (item == null) { send(ctx, "AP_" + tag + "_ERR:unknown_item:" + itemId); return 0; }
    var idx = index - 0;
    if (idx < 0) idx = 0;
    if (slotId === "chip") {
        var slotErr = ensureChipSlot(p, CHIP_SLOT_MIN);
        if (slotErr != null) { send(ctx, "AP_" + tag + "_ERR:" + slotErr); return 0; }
    }
    var err = putInSlot(p, slotId, new ItemStack(item), idx);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    send(ctx, "AP_" + tag + "_SLOTPUT:slot=" + slotId + ":index=" + idx + ":item=" + itemId);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doSignSlot(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var slots = -1, used = -1, item = "", ids = "", err = "";
    try {
        var opt = CuriosApi.getCuriosInventory(p);
        if (opt != null && opt.isPresent()) {
            var h = opt.get().getStacksHandler("stand");
            if (h != null && h.isPresent()) {
                var st = h.get().getStacks();
                slots = st.getSlots();
                used = 0;
                var acc = [];
                for (var i = 0; i < slots; i++) {
                    var s = st.getStackInSlot(i);
                    if (s != null && !s.isEmpty()) {
                        used = used + 1;
                        acc.push(itemIdOf(s));
                    }
                }
                item = (acc.length > 0) ? acc[0] : "none";
                ids = "[" + acc.join(",") + "]";
            } else { err = "no_slot:stand"; }
        } else { err = "no_curios"; }
    } catch (e1) { err = exText(e1); }
    send(ctx, "AP_" + tag + "_SIGNSLOT:slot=" + item + ":slots=" + slots + ":used=" + used + ":ids=" + ids
        + (err === "" ? "" : ":err=" + err));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doSignPrep(ctx, tag, signIdText, starText) {
    var p = ctx.source.getPlayerOrException();
    var signId = "" + signIdText;
    var err = "";
    try { clearCurioSlots(p, "stand"); } catch (e0) { err = err + "|stand:" + domExText(e0); }
    try { resetEffectCardCycle(p); } catch (e1) { err = err + "|cycle:" + domExText(e1); }
    var gateCleared = signClearAllGateEffects(p);
    var lsWrote = domSetLastSeen(p, 0);
    var signErr = "";
    try { signErr = equipSign(p, signId); } catch (e2) { signErr = domExText(e2); }
    var star = -1;
    if (("" + starText) !== "") {
        if (SignLagStarLightClass == null) { star = -2; }
        else {
            try { SignLagStarLightClass.set(p, starText - 0); star = SignLagStarLightClass.get(p) - 0; }
            catch (e3) { star = -1; err = err + "|star:" + domExText(e3); }
        }
    }
    var stand = -1;
    try { stand = domSignEquipped(p); } catch (e4) { stand = -1; }
    send(ctx, "AP_" + tag + "_SIGNPREP:sign=" + signId
        + ":sign_err=" + (signErr === null ? "" : signErr)
        + ":stand=" + stand
        + ":gate_cleared=" + gateCleared
        + ":star=" + star
        + ":ls_wrote=" + lsWrote + ":ls_after=" + domLastSeen(p)
        + ":ls_api=" + signLagBind().how
        + (err === "" ? "" : ":err=" + err)
        + ":" + domLockStateRead(p));
    return 1;
}

// ──────────────────────────────────────────────────────────────────────────
//  signcast —— 走**真实主动路径**释放当前 stand 槽立牌的主动
// ──────────────────────────────────────────────────────────────────────────
/**
 * 真实主动:`BaseSignItem.performSkillForCurio`(客户端按键的服务端同一入口;门控顺序 =
 * 锁定态 → 冷却 → 选择会话 → 选择器门控)。本命令**不**做任何 per-sign 前置(由 `signprep` 备好)。
 * 读数:`sign_before,cd_before,lock_before,gate_before,sign_after,lock_after,lock_end,grace_end,cd_end,
 *       cd_started,gate_on,last_seen,err` + `domLockStateRead`。
 */
function doSignCast(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var sign0 = domLockSign(p);
    var lock0 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); });
    var cd0 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var gate0 = domGateOn(p, sign0);
    var ls0 = domLastSeen(p);
    var err = "";
    try { BaseSignItemClass.performSkillForCurio(p); } catch (e1) { err = domExText(e1); }
    var sign1 = domLockSign(p);
    var lock1 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); });
    var cd1 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var lockEnd = domNum(function () { return ModAttachments.getSignActiveLockEnd(p); });
    var gateOn = domGateOn(p, sign1);
    send(ctx, "AP_" + tag + "_SIGNCAST:sign_before=[" + sign0 + "]"
        + ":lock_before=" + lock0 + ":cd_before=" + cd0 + ":gate_before=" + gate0
        + ":sign_after=[" + sign1 + "]"
        + ":lock_after=" + lock1
        + ":lock_end=" + lockEnd
        + ":grace_end=" + domGraceEnd(p)
        + ":cd_after=" + cd1
        + ":cd_started=" + ((cd1 > 0 && (nowTick(p) - 0) < cd1) ? 1 : 0)
        + ":gate_on=" + gateOn
        + ":last_seen_before=" + ls0 + ":last_seen=" + domLastSeen(p)
        + ":ls_api=" + signLagBind().how
        + (err === "" ? "" : ":err=" + err)
        + ":" + domLockStateRead(p));
    return 1;
}

// ──────────────────────────────────────────────────────────────────────────
//  signlag —— 离线时钟补偿的一键取证（L1 / L1b / L2 / L3 / L4）
// ──────────────────────────────────────────────────────────────────────────
/**
 * **write → drive → read 压在一次执行内**(理由见段头「为什么」)。
 *
 * 参数契约:`signlag <tag> <ticks> [mode]`
 *   · `ticks`  = 把 `last_seen` 写到 `max(1, now − ticks)`(ticks=0 ⇒ 写到 now,即「刚见过」);
 *   · `mode`   = `once`(缺省,写 1 次 + 驱动 1 次)
 *              | `double`(写 1 次 + 驱动 2 次,报 `idem`=第二拍是否与第一拍相同 ⇒ L3)
 *              | `triple`(写 1 次 + 驱动 3 次,报 `triple_stable` ⇒ L2;配合 `ticks=0` 即
 *                「基准刚被刷成 now ⇒ 连续三拍都不得移动」,**确定性**不依赖真实 tick 相位)
 *              | `nowrite`(不写 + 驱动 1 次,纯自然拍快照;慎用:真实 tick 相位会让 gap ∈ {0,1})。
 *
 * 读数(字段顺序固定,不得重排):
 *   `mode,ticks,sign,now,last_seen_before,wrote,gap,
 *    lock_end_before,lock_end_mid,lock_end_after,lock_end_delta,
 *    grace_end_before,grace_end_mid,grace_end_after,grace_end_delta,
 *    cd_before,cd_after,cd_unchanged,
 *    lock_before,lock_after,lock_sign_after,lock_sign_unchanged,
 *    last_seen_after,delta_eq_gap,grace_delta_eq_gap,seen_refreshed,idem,triple_stable,drives,tick_ran,ls_api` + `domLockStateRead`
 *
 * `gap` = `now − wrote`(**与产品同式**,便于直接对照「仅当 gap > 1 才补偿」的阈值)。
 * `delta_eq_gap` = 上界位移**恰好等于** gap(0/1,探针算);`seen_refreshed` = 驱动后基准已刷新成 `now`。
 * 这两条是**跨重登取证**(RELOG-B:不写基准、直接驱动那一拍)的主判据 —— 断言层无算术能力。
 */
function doSignLag(ctx, tag, ticksText, modeText) {
    var p = ctx.source.getPlayerOrException();
    var mode = ("" + modeText) === "" ? "once" : ("" + modeText);
    var ticks = ticksText - 0;
    if (isNaN(ticks) || ticks < 0) ticks = 0;
    var now = nowTick(p) - 0;
    var sign0 = domLockSign(p);
    var lsBefore = domLastSeen(p);
    var lockEnd0 = domNum(function () { return ModAttachments.getSignActiveLockEnd(p); });
    var grace0 = domGraceEnd(p);
    var cd0 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var lock0 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); });
    var err = "";
    // ① 写 last_seen 到过去(仅 nowrite 不写;triple 配合 ticks=0 = 「刚见过」的确定性基准)
    var wrote = lsBefore;
    var doWrite = (mode !== "nowrite");
    if (doWrite) {
        var target = now - ticks;
        if (target < 1) target = 1;
        var w = domSetLastSeen(p, target);
        if (w === 0) wrote = target; else err = err + "|write:" + (signLagBind().note === "" ? "fail" : signLagBind().note);
    }
    var lockEndMid = -1, graceMid = -1;
    // ② 驱动产品 tick(与玩家 tick 事件调的是同一份代码)
    var drives = 1, tickRan = 1;
    if (mode === "double") drives = 2;
    if (mode === "triple") drives = 3;
    for (var d = 1; d <= drives; d++) {
        try { BaseSignItemClass.tickSignActiveLock(p); }
        catch (eT) { tickRan = 0; err = err + "|tick" + d + ":" + domExText(eT); }
        if (d === 1) {
            lockEndMid = domNum(function () { return ModAttachments.getSignActiveLockEnd(p); });
            graceMid = domGraceEnd(p);
        }
    }
    // ③ 读回
    var lockEnd1 = domNum(function () { return ModAttachments.getSignActiveLockEnd(p); });
    var grace1 = domGraceEnd(p);
    var cd1 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var lock1 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); });
    var sign1 = domLockSign(p);
    var gap = now - wrote;
    var lockDelta = ((lockEnd0 < 0 || lockEnd1 < 0) ? -999 : (lockEnd1 - lockEnd0));
    var graceDelta = ((grace0 < 0 || grace1 < 0) ? -999 : (grace1 - grace0));
    // 「补偿量 == gap」与「基准被刷新成 now」都由探针算成 0/1 标志(断言层无算术、正则也不能比较两行数值)。
    // `delta_eq_gap` 是**跨重登取证**(RELOG-B:不写基准、直接驱动那一拍)的主判据。
    var deltaEqGap = ((lockEnd0 > 0 && gap > 1 && lockDelta === gap) ? 1 : 0);
    var graceDeltaEqGap = ((grace0 > 0 && gap > 1 && graceDelta === gap) ? 1 : 0);
    var seenAfter = domLastSeen(p);
    var seenRefreshed = (seenAfter === now) ? 1 : 0;
    // 三拍稳定(仅 triple 有意义):三拍之间是否**逐拍不变**
    var tripleStable = ((lockEnd0 === lockEndMid) && (lockEndMid === lockEnd1)) ? 1 : 0;
    if (mode !== "triple") tripleStable = -1;
    // 同 tick 幂等(仅 double 有意义):第二拍是否**没有再动**
    var idem = ((lockEndMid === lockEnd1) && (graceMid === grace1)) ? 1 : 0;
    if (mode !== "double") idem = -1;
    send(ctx, "AP_" + tag + "_SIGNLAG:mode=" + mode + ":ticks=" + ticks
        + ":sign=[" + sign0 + "]:now=" + now
        + ":last_seen_before=" + lsBefore + ":wrote=" + wrote + ":gap=" + gap
        + ":lock_end_before=" + lockEnd0 + ":lock_end_mid=" + lockEndMid + ":lock_end_after=" + lockEnd1
        + ":lock_end_delta=" + lockDelta
        + ":grace_end_before=" + grace0 + ":grace_end_mid=" + graceMid + ":grace_end_after=" + grace1
        + ":grace_end_delta=" + graceDelta
        + ":cd_before=" + cd0 + ":cd_after=" + cd1
        + ":cd_unchanged=" + ((cd0 >= 0 && cd0 === cd1) ? 1 : 0)
        + ":lock_before=" + lock0 + ":lock_after=" + lock1
        + ":lock_sign_after=[" + sign1 + "]"
        + ":lock_sign_unchanged=" + ((sign0 === sign1) ? 1 : 0)
        + ":last_seen_after=" + seenAfter
        + ":delta_eq_gap=" + deltaEqGap
        + ":grace_delta_eq_gap=" + graceDeltaEqGap
        + ":seen_refreshed=" + seenRefreshed
        + ":idem=" + idem + ":triple_stable=" + tripleStable
        + ":drives=" + drives + ":tick_ran=" + tickRan
        + ":ls_api=" + signLagBind().how
        + (err === "" ? "" : ":err=" + err)
        + ":" + domLockStateRead(p));
    return 1;
}

// ──────────────────────────────────────────────────────────────────────────
//  signend —— L5:门控效果消失 ⇒ 锁定应正常结束并迁移到冷却
// ──────────────────────────────────────────────────────────────────────────
/**
 * 步骤:读基线 → 走**内部通道**移除当前立牌的全部门控效果 → 驱动产品 tick → 读回。
 * 期望(非忍者):`isSignActiveLocked` 转 false ⇒ `endLockAndStartCooldown` ⇒
 *   锁定标记清空、`lock_end=0`、`grace_end=0`、`cd_end = now + max(0, max_cd − pool) > 0`。
 * 判据用**探针算出的标志**表达(正则不能比较数值):`ended` = 锁定标记已空 且 `lock_after=0`;
 *   `cd_started` = `cd_after > now`;`cd_delta` = `cd_after − cd_before`。
 * 读数:`sign_before,gate_before,removed,gate_after,sign_after,lock_before,lock_after,lock_end_after,
 *       grace_after,cd_before,cd_after,cd_started,max_cd,pool,ended,tick_ran,err`。
 */
function doSignEnd(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var now = nowTick(p) - 0;
    var sign0 = domLockSign(p);
    var gate0 = domGateOn(p, sign0);
    var lock0 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); });
    var cd0 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var errBox = { err: "" };
    var removed = signClearGateEffects(p, sign0, errBox);
    var tickRan = 1;
    try { BaseSignItemClass.tickSignActiveLock(p); }
    catch (e1) { tickRan = 0; errBox.err = errBox.err + "|tick:" + domExText(e1); }
    var sign1 = domLockSign(p);
    var lock1 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); });
    var lockEnd1 = domNum(function () { return ModAttachments.getSignActiveLockEnd(p); });
    var grace1 = domGraceEnd(p);
    var cd1 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var cdStarted = (cd1 > now) ? 1 : 0;
    var ended = ((sign1 === "") && (lock1 === 0)) ? 1 : 0;
    send(ctx, "AP_" + tag + "_SIGNEND:sign_before=[" + sign0 + "]"
        + ":gate_before=" + gate0 + ":gate_removed=" + removed + ":gate_after=" + domGateCount(p, sign0)
        + ":sign_after=[" + sign1 + "]"
        + ":lock_before=" + lock0 + ":lock_after=" + lock1
        + ":lock_end_after=" + lockEnd1 + ":grace_after=" + grace1
        + ":cd_before=" + cd0 + ":cd_after=" + cd1
        + ":cd_started=" + cdStarted
        + ":max_cd=" + domMaxCooldown(p) + ":pool=" + domReductionPool(p)
        + ":ended=" + ended + ":tick_ran=" + tickRan
        + ":now=" + now
        + (errBox.err === "" ? "" : ":err=" + errBox.err)
        + ":" + domLockStateRead(p));
    return 1;
}

// ──────────────────────────────────────────────────────────────────────────
//  signstate / signplayed —— 只读快照 / 忍者出牌标记
// ──────────────────────────────────────────────────────────────────────────
/** `signstate … mark` 存下的基线(供下一次 `signstate` 算差值;正则无法比较两行数值,故由探针算) */
var signLagMark = null;

/**
 * 纯只读快照(零写入)。
 *
 * 参数契约:`signstate <tag> [mark]`
 *   · 带 `mark` ⇒ 把当前 `lock_end/grace_end/cd_end/lock_sign` 存为**基线**;
 *   · 每次都对比基线并报差值:`lock_end_vs_mark/grace_end_vs_mark/cd_vs_mark/lock_sign_same`
 *     (`-999` = 无基线或读不到)。
 *
 * <p>**为什么需要它**:L2 的「正常 tick 不得移动上界」若只用 `signlag` 手动驱动,测的是
 * 「我把同一份代码调 3 次」;而**真实玩家 tick**(生产路径 `PlayerTickEvents`)在两条命令之间
 * 已经跑了 ~N 拍 —— 用本命令在两拍之间取值判等,才是对**生产路径**的直接取证
 * (`lock_end_vs_mark=0` 同时覆盖「首拍不得凭空补偿」这一初始化边界)。
 */
function doSignState(ctx, tag, markText) {
    var p = ctx.source.getPlayerOrException();
    var lockEnd = domNum(function () { return ModAttachments.getSignActiveLockEnd(p); });
    var grace = domGraceEnd(p);
    var cd = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var sign = domLockSign(p);
    var isMark = (("" + markText) !== "") ? 1 : 0;
    var dLockEnd = -999, dGrace = -999, dCd = -999, signSame = -999;
    if (signLagMark != null) {
        if (lockEnd >= 0 && signLagMark.lockEnd >= 0) dLockEnd = lockEnd - signLagMark.lockEnd;
        if (grace >= 0 && signLagMark.grace >= 0) dGrace = grace - signLagMark.grace;
        if (cd >= 0 && signLagMark.cd >= 0) dCd = cd - signLagMark.cd;
        signSame = (sign === signLagMark.sign) ? 1 : 0;
    }
    if (isMark === 1) {
        signLagMark = { lockEnd: lockEnd, grace: grace, cd: cd, sign: sign, now: nowTick(p) - 0 };
    }
    send(ctx, "AP_" + tag + "_SIGNSTATE:mark=" + isMark
        + ":lock_end_vs_mark=" + dLockEnd
        + ":grace_end_vs_mark=" + dGrace
        + ":cd_vs_mark=" + dCd
        + ":lock_sign_same=" + signSame
        + (signLagMark == null ? "" : ":mark_now=" + signLagMark.now)
        + ":" + domLockStateRead(p));
    return 1;
}

/**
 * 写忍者「宽限期内出过效果牌」标记(`sign_active_lock_played`)+ 读回。
 * 用于 L6 的两条出口:宽限到期时 `played=0` ⇒ 强制重置出牌状态并起冷却(锁定结束);
 * `played=1` ⇒ 只清宽限刻、**保持锁定**(等出牌周期完全重置)。
 * 参数:`signplayed <tag> <0|1>`。
 */
function doSignPlayed(ctx, tag, valText) {
    var p = ctx.source.getPlayerOrException();
    var want = ("" + valText) === "1" ? true : false;
    var err = "";
    try { ModAttachments.setSignActiveLockPlayed(p, want); }
    catch (e1) { err = domExText(e1); }
    send(ctx, "AP_" + tag + "_SIGNPLAYED:wrote=" + (want ? 1 : 0)
        + ":played=" + domLockPlayed(p)
        + ":now=" + (nowTick(p) - 0)
        + ":grace_end=" + domGraceEnd(p)
        + (err === "" ? "" : ":err=" + err)
        + ":" + domLockStateRead(p));
    return 1;
}

// ──────────────────────────────────────────────────────────────────────────
//  signroundreset —— 忍者「出牌周期完全重置」出口(L6 第三出口 / 非忍者负控)
// ──────────────────────────────────────────────────────────────────────────
/**
 * 走产品的**真实回调入口** `EffectCardPeriod.onRoundFullyReset`(内部只调
 * `BaseSignItem.onEffectCardRoundReset`)⇒ 忍者应**结束锁定并起冷却**;
 * 非忍者(如 parunan)在该回调里**首行早退** ⇒ 锁定必须**原样不动**(负控,证明无 per-sign 泄漏)。
 * 注意:`EffectCardPeriod.forceResetRound`(宽限期满用的强重置)**刻意不**回调本入口
 * (产品注释:`不回调 onRoundFullyReset:调用方自行决定后续迁移`),故两条路径分开取证。
 * 读数:`sign_before,gate_on,cd_before,sign_after,lock_after,grace_after,cd_after,cd_started,ended,
 *       sign_unchanged,tick_ran,err`。
 */
function doSignRoundReset(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var now = nowTick(p) - 0;
    var sign0 = domLockSign(p);
    var cd0 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var err = "";
    var called = 0;
    try { EffectCardPeriodClass.onRoundFullyReset(p); called = 1; }
    catch (e1) { err = domExText(e1); }
    var tickRan = 1;
    try { BaseSignItemClass.tickSignActiveLock(p); } catch (e2) { tickRan = 0; err = err + "|tick:" + domExText(e2); }
    var sign1 = domLockSign(p);
    var lock1 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(p); });
    var grace1 = domGraceEnd(p);
    var cd1 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    send(ctx, "AP_" + tag + "_SIGNROUNDRST:sign_before=[" + sign0 + "]"
        + ":gate_on=" + domGateOn(p, sign0)
        + ":called=" + called
        + ":sign_after=[" + sign1 + "]"
        + ":sign_unchanged=" + ((sign0 === sign1) ? 1 : 0)
        + ":lock_after=" + lock1
        + ":grace_after=" + grace1
        + ":cd_before=" + cd0 + ":cd_after=" + cd1 + ":cd_started=" + ((cd1 > now) ? 1 : 0)
        + ":ended=" + (((sign1 === "") && (grace1 === 0)) ? 1 : 0)
        + ":now=" + now + ":tick_ran=" + tickRan
        + (err === "" ? "" : ":err=" + err)
        + ":" + domLockStateRead(p));
    return 1;
}

// ──────────────────────────────────────────────────────────────────────────
//  signexpiregrace —— 把忍者宽限刻推到「已过期」(L6 两条出口的构造)
// ──────────────────────────────────────────────────────────────────────────
/**
 * 把 `grace_end` 写成 `max(1, now − 1)`(**正数**的过去刻:0 会被当成「无宽限」而早退,
 * 与 `nardilockback` 同一个坑),随后驱动产品 tick,读回两条出口的可观测结果:
 *   · `played=0` ⇒ `forceResetRound` + `endLockAndStartCooldown` ⇒ 锁定清空 + 冷却起算;
 *   · `played=1` ⇒ 只把 `grace_end` 清 0,锁定标记**保持不变**(等周期完全重置)。
 * 参数:`signexpiregrace <tag>`。
 */
function doSignExpireGrace(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var now = nowTick(p) - 0;
    var past = now - 1;
    if (past < 1) past = 1;
    var sign0 = domLockSign(p);
    var played0 = domLockPlayed(p);
    var cd0 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    var err = "";
    try { ModAttachments.setSignActiveLockGraceEnd(p, past); } catch (e1) { err = domExText(e1); }
    // 直接把宽限写进过去之前,先把**离线补偿基准**刷成 now(要求 2:所有「合成锁定态」的脚手架
    // 都要保持「没有离线」的语义)。否则上一用例留下的陈值会被本拍当成一次离线 gap 误补偿。
    try { ModAttachments.setSignActiveLockLastSeen(p, now); } catch (e1b) { err = err + "|seen:" + domExText(e1b); }
    var tickRan = 1;
    try { BaseSignItemClass.tickSignActiveLock(p); } catch (e2) { tickRan = 0; err = err + "|tick:" + domExText(e2); }
    var sign1 = domLockSign(p);
    var grace1 = domGraceEnd(p);
    var cd1 = domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); });
    send(ctx, "AP_" + tag + "_SIGNGRACE:sign_before=[" + sign0 + "]:played=" + played0
        + ":wrote_grace=" + past + ":now=" + now
        + ":sign_after=[" + sign1 + "]"
        + ":grace_after=" + grace1
        + ":cd_before=" + cd0 + ":cd_after=" + cd1 + ":cd_started=" + ((cd1 > now) ? 1 : 0)
        + ":ended=" + (((sign1 === "") && (grace1 === 0)) ? 1 : 0)
        + ":still_locked=" + ((sign1 === "") ? 0 : 1)
        + ":tick_ran=" + tickRan
        + (err === "" ? "" : ":err=" + err)
        + ":" + domLockStateRead(p));
    return 1;
}

// NARDIS-IMPL-END(插入器用:重跑 build_nardi_block.ps1 时靠这一行定位旧块并整段替换)
// ════════════════════════════════════════════════════════════════════════════
//  蛟龙立牌(mamushi)游戏内取证段(2026-09-27;双人/多人用 Carpet `/player` bot)
//    语义基准 = docs/features/mamushi-sign-spec.md(§1 冻结常量 / §2 技能语义 /
//    §3 实现落点 / §6 交叉验证清单 / §7 的「R1 自证边界(需游戏内读数)」6 项)。
//
//  命令(tag 一律 word();item/effect id 一律 string() ⇒ 用例侧必须带引号):
//    /astralprobe mamuclear <tag>                            收尾:清卡/立牌/效果/附件 + 归零冷却(脚手架)
//    /astralprobe mamuprep <tag> [bot]                       基线:装立牌 + 骰子 + 铁剑 + 全量读数(+可选对 bot 同基线)
//    /astralprobe mamureg <tag>                              注册与冻结数值(物品/效果/常量/标签)
//    /astralprobe mamuread <tag> <phase> [name]              只读全量快照(缺省自身;可指真实玩家/bot)
//    /astralprobe mamuawake <tag> <mode> <n> [name]          mode: set(直写) | add(onCardGivenToOther 自身←自身,即 −0 负控)
//    /astralprobe mamugive <tag> <giver> <receiver>          **一次发牌事件**:受益人去重(调用一次只 +1 层)
//    /astralprobe mamubatch <tag> <mode> <g1> <g2> <g3>      mode: cap(1 受益人上界) | three(3 受益人 ⇒ +3) |
//    /astralprobe mamuwatch <tag> <mode> [name]              mode: count | self | give15 | random | random2 |
//                                                            active(真实主动路径 + 逐目标行,唯一能测 D1 的档) | selfonly
//    /astralprobe mamuform <tag> <mode> [name]               mode: mark(只读) | on(直写 8 + 幂等转换 + 真龙 tick 刷新)
//    /astralprobe mamuconv <tag> <mode> [arg] [name]         mode: inv 3 张撕咬(主栏 2 + 副手 1) |
//                                                            dice <maxCost> 骰子侧 3 个 bite |
//                                                            arm [张数] **把 dragon_roar 写进骰子卡牌栏**
//                                                            (打通「命中 ⇒ 破防」端到端链的唯一路径)
//    /astralprobe mamucast <tag> [noop] [name]               真实主动路径 performSkillForCurio + 前后全量读数
//    /astralprobe mamucd <tag> <mode>                        冷却闸门读数 / 负控构造(mode 见函数头)
//    /astralprobe mamucore <tag> <mode>                      mode: ready(充能备齐→被拒且不扣充能) | nocharge(负控:有充能会被扣)
//                                                            | charge(无强制冷却 ⇒ 电流核心正常完成冷却)
//                                                            **前置自足**:内部先把骰子换成 golden_dice 拿 1 个筹码栏
//    /astralprobe mamujump <tag>                             攻击力/破防数值落地(真实攻击次数 + 客观组 + 实扣)
//    /astralprobe mamuroar <tag> <mode> [name]               mode: read(目标侧只读) | apply(直调 applyRoarDebuff;第 3 参 = 目标)
//    /astralprobe mamubite <tag> <mode>                      mode: clean | light(1 张撕咬 + 触发赐福 = 层 0→1) |
//                                                            three(3 张 ⇒ +3) | dragon(7 层 + 1 张 ⇒ 立即真龙)
//    /astralprobe mamuguard <tag> <mode>                     seven(7 层) | mark(只读:两张牌专属 + 随机池 + 标签 + mayPlace 装备入口)
//    /astralprobe mamudie <tag> <keepinv>                    真死亡 + **服务端真重生**(PlayerList#respawn;
//                                                            0 = keepInventory=false 场景,1 = keepEverything)
//
//  ⚠️ **`[name]` 参数不接受字面量 `self`**(2026-09-27 实机踩坑):凡是「目标名字」位(`mamuread` 第 3 参、
//     `mamuawake` 第 4 参、`mamugive` 的 giver/receiver、`mamuwatch` 第 4 参、`mamuform` 第 4 参、
//     `mamuconv` 的 `[name]`、`mamucast` 的 `<name>`、`mamuroar` 的 `[name]`)都经 `mamuResolve`
//     → `zhaoResolve`,而后者**只把「缺省」与 `-` 当自身**;传 `self` 会去 `PlayerList` 里找名叫
//     `self` 的玩家 ⇒ 报 `AP_<tag>_ERR:no_target:self`(实测)。
//     ⇒ **要指自身:省略该参数或写 `-`**。
//     ⚠️ **例外(这两个 `self` 是"模式",合法,别改)**:`mamucast <tag> [self|noop] [name]` 的**第 3 参**
//     (模式位,`self` = 自身目标)与 `mamuwatch <tag> <mode>` 的**模式**取值(`self` / `selfonly`)。
//
//  ⚠️ **两线本段逐字一致**(除函数注释里显式标注的平台形态差异);唯一平台分叉收敛进
//     {@link mamuEffectHolder}(1.21.1 = DeferredHolder 自身 / 1.20.1 = RegistryObject#get)
//     与 {@link mamuBiteBonusActive}(1.21.1 = isMamushiBiteBonusActive / 1.20.1 = getMamushiBiteBonusActive)
//     两个助手。产品 API 一律走**两线同名同签名**的入口(所以转换/持有判据一律用
//     `MamushiSignItem` 的包装器,而不是两线签名的 `DragonCardUtil`)。
//
//  ⚠️ 踩坑(勿改回):
//    1. `p.hasEffect(ModEffects.X)` 在 1.20.1 上会触发 KubeJS 注册表强转并抛异常(逃出 try/catch)
//       ⇒ 效果判据一律 `findEffect(p, "effect.astral_dice.<x>")` 字符串匹配;
//       `MobEffectInstance` 的构造/施加一律经 {@link mamuEffectHolder} 归一后的 Holder。
//    2. 外部 `/effect clear` 会被产品 `ModEffectEvents#onModEffectRemovalPrevented` 取消 ⇒
//       移除本模组效果必须走 `ModEffectRemoval.remove`(同 zhao 段)。
//    3. curios 槽的 `getStackInSlot` 拿到的是**副本** ⇒ 改 `weapon_enhancement` 必须写回槽位
//       (已由既有 `domWriteEnh` 处理)。
//    4. 属性/伤害类命令的生效**晚于同一 tick 内的后续代码** ⇒ 探针一律**直写属性实例**
//       (`Attribute#setBaseValue`),并把「写 → 攻击 → 读」压在一次执行内;用例侧只在跨相位时给 wait。
//    5. 1.21.1 的 `dice.set(key.get(), 我的record)` 会被 KubeJS 的 `set` 扩展方法劫持并抛
//       `Unsure how to convert … to JSON` ⇒ 写组件一律走既有 `nardiSetEnh`(传 JS 对象)。
//    6. **函数声明一律放「函数体顶层」或「文件顶层」,绝不放 `try {}` / `if {}` 等块内**
//       (2026-09-27 实机):Rhino/KubeJS 下**块内**函数声明在块内调用得到 `undefined` ⇒
//       `TypeError: f is not a function`(实机唯一一条错误 = `REG_TAG` 整行只剩
//       `tag_err=[] TypeError: hasTag is not a function, it is undefined.:tag_ex=1`)。
//       `guard`/`try-catch` **捕不到**这类错误(它发生在 JS 求值层，读数会缺字段)。
//       函数**体**顶层的声明没问题(同段 `flag`/`hit`/`giveOne`/`snapRow` 实机均正常)。
//    7. 脚手架里「成/败都是正常结果」的操作,读数字段一律用 {@link mamuStatus} 的
//       `ok` / `fail:<原因>`,**不要**再用 `*_err=` 拼空串 —— 全成功时那种写法也是非空的
//       (`bot_err=|sign:|dice:`),实机被读成了"失败"。
//    8. **每个 `.executes` 处理器的最终返回值必须是 `1`(数字字面量)**,绝不能是变量/字符串。
//       Brigadier 要求返回 `int`;本文件既有的 `dumpState` 曾以 `return rc;`(`rc` = `"rc=undefined"`)
//       收尾,实机抛 `EvaluatorException: Cannot convert rc=undefined to int` —— 该异常**逃出
//       `guard`/`try-catch`**,而且**打断同一 tick 里排队执行的命令**(实测那一拍的 `astralparty dump`
//       根本没运行、`APDUMP` 行数 = 0)。本段 18 条命令全部 `return 1;`,读数只进消息文本。
//    9. **复用助手时不要动既有调用点**:本段的 `mamuRead` 多了第 5 参 `emitTarget`(重生后直发新实体),
//       其余调用点**不传**即行为不变;批量替换时务必只改 `mamuRead` 函数体内部。
//   10. **读数自己写下的值再拿来比,等于没比**(2026-09-27 实机):`MU_CD` 的 `mfu_is_1200` 原定义是
//       `(mfu − 本命令刚写入的 base) === 1200` ⇒ **恒为 0**(含 `mode=present` 那一拍)。凡「写入 →
//       读回 → 判等」一律要拿**产品口径的不变量**去比(这里是 `mfu − now == ACTIVE_FORCED_COOLDOWN_TICKS`,
//       由 `mfu_remain_is_cfg` 给出),或用**进入命令时**的值(`mfu_entry`/`mfu_shift`)当基准。
//   11. **"什么都没变"必须先能归因**:`mamucast`/`mamuwatch active` 走的真实主动路径有四道前置闸门
//       (立牌在不在 curios `stand` 槽 / 锁定态 / **强制冷却** / 普通冷却),任一挡下都"毫无变化"。
//       实测 `mamucast … self` 的 `bite_slots=->-` 就是被上一步 `mamucd … cast` 写下的强制冷却挡的
//       (同拍 `mfu_remain=1200`)⇒ 读数必须有 `gate` / `sign` / `locked_before` / `cd_before` / `fired`。
//   11b. **`bite_slots`/`roar_slots` 只数主物品栏 + 副手**(`槽号:张数`),**不含骰子卡牌栏**;主动自身
//       那张若进的是骰子侧装配项,这两个字段恒为 `->-` —— 不是"没发牌",看 `stones_*`/`card_total=`。
//   12. **攻击力加成的口径要按"这一拍到底叠加了几段"算**:`mamubite dragon` 档里 7 + 1 = 8 层会在
//       **同一拍**跨过真龙阈值 ⇒ 攻击力同时吃 `min(觉醒,4)`(=4)**和** `DRAGON_FORM_ATTACK_BONUS`(=5)。
//       旧口径只拿 `min(awk,4)` 比 ⇒ 该档 `ap_cap_ok` 恒 0(实机 `ap=6>15>15`,实际 +9)。现读数为
//       `ap_cap_delta`(撕咬部分)+ `ap_df_bonus`(形态跃迁部分)= `ap_expect`,与其比对得 `ap_ok`。
//   13. **`owner_uuid` 的"无主"与"读不到"必须分开**(2026-09-27 实机):`ItemStack#getOrDefault(type, null)`
//       在**组件不存在**时返回 `null`**而不抛错** ⇒ 旧代码接着去试 1.20.1 形态(必然抛错)并把整件事
//       记成 `err`,实测表现为 `CONV` 的 `owner_before=err:owner_after=err`(其实是**无主**/或没放进去)。
//       {@link mamuOwnerUuid} 现在逐路径记录"成功返回 / 抛错",只有**全抛错**才是 `err`;闭集仍是
//       `self|other|none|err`,原因另置 `owner_api=`。
//   14. **脚手架放不进去东西要自报**(2026-09-27 实机):`mamuconv inv` 旧写法把 `placed=N` 拼进 `err=`
//       串里 ⇒ `placed=0`(主栏满、一张都没放)被读成"转换失效"。现在 `placed`/`inv_free`/`place_mode`
//       是**独立字段**,且放不下会走 `Inventory#add` → `drop` 兜底。
//   15. **平台常量"裸传"会打死整条用例,而且捕不到**(2026-09-27 实机,`signGateHolders`)。
//       `ModEffects.X` 在 1.21.1 = `DeferredHolder`(**本身就是 Holder**),在 1.20.1 = `RegistryObject`
//       (**不是** Holder)⇒ 1.20.1 上把它喂给 `ModEffectRemoval.remove(p, holder)` 会在 **Rhino 参数
//       转换**阶段抛 `ResourceLocationException: Non [a-z0-9/._-] character in path of location:
//       minecraft:net.minecraftforge.registries`,**该异常逃得出 JS `try/catch` 与 `guard`** ⇒
//       `signClearAllGateEffects` 中断 ⇒ `doSignReset`/`doSignPrep` **在 `send(...)` 之前退出** ⇒
//       **整条用例零读数**(实测 `LOCK-OFFLINE-1.20.1` 30 条断言全 FAIL、`AP_L0_SIGNRESET` 命中 0)。
//       ⇒ 凡把平台注册常量交给 Java API,一律先过**单行归一助手**(`c.get()` 优先、失败回落到 `c`):
//       本段用 {@link mamuEffectHolder},sign 段用 `signLagFxHolder`,nardis 段用 `domEffectHolder`。
//       **不得**直接 `return [ModEffects.X]` / `remove(p, ModEffects.X)`(1.20.1 侧)。
// ════════════════════════════════════════════════════════════════════════════

function mamuLoadCls(name) {
    try { return Java.loadClass(name); } catch (e) { return null; }
}

var MamuSignItemClass = mamuLoadCls("com.merlinkitsune.astral_dice.item.sign.MamushiSignItem");
var MamuCardUtilClass = mamuLoadCls("com.merlinkitsune.astral_dice.item.card.DragonCardUtil");
var MamuDragonEffectClass = mamuLoadCls("com.merlinkitsune.astral_dice.effect.MamushiDragonEffect");
var MamuBreakEffectClass = mamuLoadCls("com.merlinkitsune.astral_dice.effect.DragonRoarBreakEffect");
var MamuItemsClass = mamuLoadCls("com.merlinkitsune.astral_dice.item.ModItems");
var MamuCurrentCoreClass = mamuLoadCls("com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem");
var MamuChargeManagerClass = mamuLoadCls("com.merlinkitsune.astral_dice.item.ChargeManager");
var MamuRandomCardHandlerClass = mamuLoadCls("com.merlinkitsune.astral_dice.item.card.RandomCardHandler");
var MamuVitaminPillClass = mamuLoadCls("com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem");

/** 蛟龙立牌 id / 两张专属战斗牌 id(规格 §1 冻结值;物品 id 与 typeId 同名部分刻意分开写) */
var MAMU_SIGN_ID = "astral_dice:mamushi_sign";
var MAMU_BITE_ID = "astral_dice:attack_card_bite";
var MAMU_ROAR_ID = "astral_dice:attack_card_dragon_roar";
var MAMU_BITE_TYPE = "bite";
var MAMU_ROAR_TYPE = "dragon_roar";
var MAMU_DICE_ID = "astral_dice:dice";
/** 效果描述 id(只做字符串匹配;两线同源,见踩坑 1) */
var DESC_MAMUSHI_DRAGON = "effect.astral_dice.mamushi_dragon";
var DESC_DRAGON_ROAR_BREAK = "effect.astral_dice.dragon_roar_break";
/** 破防效果的护甲修饰器语义名(1.21.1 的 ResourceLocation path;**仅用于读数标注**,见 mamuBreakArmor) */
var MAMU_BREAK_ARMOR_KEY = "dragon_roar_break_armor";

/**
 * `ModEffects` 常量 → `Holder<MobEffect>`(唯一平台分叉点):
 * 1.21.1 = `DeferredHolder` **自身**即可作 Holder;1.20.1 = `RegistryObject`,必须先 `.get()`。
 * 先试 `.get()`(1.20.1 形态),取到就用;失败/返回 null 则回落常量自身(1.21.1 形态)。
 * `which`:0 = 真龙形态;1 = 破防。
 */
function mamuEffectHolder(which) {
    var c = (which === 1) ? ModEffects.DRAGON_ROAR_BREAK : ModEffects.MAMUSHI_DRAGON;
    try { var g = c.get(); if (g != null) return g; } catch (e1) { /* 落到下面 */ }
    return c;
}

/** 撕咬加成锁存(唯一平台函数名分叉点:1.21.1 = is… / 1.20.1 = get…) */
function mamuBiteBonusActive(p) {
    if (p == null) return -9;
    try { return ModAttachments.isMamushiBiteBonusActive(p) ? 1 : 0; } catch (e1) { /* 落到 1.20.1 形态 */ }
    try { return ModAttachments.getMamushiBiteBonusActive(p) ? 1 : 0; } catch (e2) { return -9; }
}

/** 立牌装备判定(产品入口;读不到给 -1) */
function mamuSignEquipped(p) {
    if (MamuSignItemClass == null || p == null) return -1;
    return zhaoBool(function () { return MamuSignItemClass.isEquipped(p); });
}

/** 觉醒层数(读不到给 -1;不冒充 0) */
function mamuAwaken(p) {
    if (MamuSignItemClass == null || p == null) return -1;
    return zhaoNum(function () { return MamuSignItemClass.getAwakening(p); });
}

/** 真龙形态判定(产品入口;读不到给 -1) */
function mamuDragonFlag(p) {
    if (MamuSignItemClass == null || p == null) return -1;
    return zhaoBool(function () { return MamuSignItemClass.isDragonForm(p); });
}

/** 骰子卡牌栏指定 typeId 的装配张数(产品入口;读不到给 -1) */
function mamuEquippedCount(p, typeId) {
    if (MamuSignItemClass == null || p == null) return -1;
    return zhaoNum(function () { return MamuSignItemClass.countEquippedType(p, typeId); });
}

/** D1 持有判据:主物品栏 + 双手 + 骰子卡牌栏内是否有任何本模组卡牌(产品入口) */
function mamuHasAnyCard(p) {
    if (MamuSignItemClass == null || p == null) return -1;
    return zhaoBool(function () { return MamuSignItemClass.hasAnyCard(p); });
}

/**
 * **产品口径**的本模组卡牌总张数(主物品栏 0..35 + 副手 + 骰子卡牌栏装配项;读不到给 -1)。
 *
 * <p>为什么要它:`WATCH` 行里的 `cards*` 只数**背包/副手**里的撕咬+龙之咆哮两张专属牌,
 * 而 D1 的追加判据 `hasAnyCard` 的计数域是**背包 + 副手 + 骰子卡牌栏**、且 D1 追加的那张是
 * **随机牌**(不是撕咬/咆哮)⇒ 只看 `cards` 永远看不到 D1 的效果。本字段按 `ModItems.isCardItem`
 * (战斗牌 ∪ 效果牌标签)逐格计数,骰子侧按 `CardRegistry.typeToItem(type)` 还原成卡牌物品再计
 * —— 与 `DragonCardUtil#hasAnyCard` **同一计数域**(只是从"有没有"升级成"有几张")。
 */
function mamuCardTotal(p) {
    if (p == null) return -1;
    var n = 0;
    try {
        var inv = p.getInventory();
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (st == null || st.isEmpty()) continue;
            try { if (MamuItemsClass.isCardItem(st)) n = n + (st.getCount() - 0); } catch (e0) { /* 非卡牌 */ }
        }
        var off = p.getOffhandItem();
        if (off != null && !off.isEmpty()) {
            try { if (MamuItemsClass.isCardItem(off)) n = n + (off.getCount() - 0); } catch (e1) { /* 忽略 */ }
        }
    } catch (eA) { return -1; }
    // 骰子卡牌栏:每个装配项 → 卡牌物品 → 卡牌标签(与产品同一个还原口径)
    try {
        var enh = domEnh(p);
        if (enh != null) {
            var list = enh.appliedStones();
            if (list != null) {
                for (var k = 0; k < list.size(); k++) {
                    var s = list.get(k);
                    if (s == null || s.type() == null) continue;
                    try {
                        var card = NardisCardRegistryClass.typeToItem(s.type());
                        if (card != null && !card.isEmpty() && MamuItemsClass.isCardItem(card)) n = n + 1;
                    } catch (e2) { /* 未登记的 typeId ⇒ 不算 */ }
                }
            }
        }
    } catch (eB) { /* 无骰子/无装配栏 ⇒ 只算背包侧 */ }
    return n;
}

/** 强制冷却截止刻(绝对 gameTime;0 = 无;读不到给 -1) */
function mamuForcedUntil(p) {
    if (MamuSignItemClass == null || p == null) return -1;
    return zhaoNum(function () { return MamuSignItemClass.getForcedCooldownUntil(p); });
}

/** 本次冷却实际使用的上界(`sign_active_max_cooldown`;路线 A 各减免方共读的那一份) */
function mamuMaxCd(p) { return domNum(function () { return ModAttachments.getSignActiveMaxCooldown(p); }); }

/** 主动冷却截止刻(`sign_active_cooldown_end`) */
function mamuCdEnd(p) { return domNum(function () { return ModAttachments.getSignActiveCooldownEnd(p); }); }

/** 主物品栏(0..35)+ 副手里指定物品的张数(同 zhaoCountInvAndOff 口径;读不到 -1) */
function mamuInvCount(p, itemId) { return zhaoCountInvAndOff(p, itemId); }

/** 主物品栏 + 副手里的卡牌构成(`id xN` 用 `|` 连接;无卡 = `-`;读不到 = `?`) */
function mamuInvCards(p, itemId) {
    var out = "";
    try {
        var inv = p.getInventory();
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (st == null || st.isEmpty()) continue;
            var id = itemIdOf(st);
            if (id !== itemId) continue;
            out = (out === "") ? (id + "x" + st.getCount()) : (out + "|" + id + "x" + st.getCount());
        }
        var off = p.getOffhandItem();
        if (off != null && !off.isEmpty() && itemIdOf(off) === itemId) {
            out = (out === "") ? ("off:" + itemIdOf(off) + "x" + off.getCount()) : (out + "|off:" + itemIdOf(off) + "x" + off.getCount());
        }
    } catch (e) { return "?"; }
    return (out === "") ? "-" : out;
}

/** 骰子 `weapon_enhancement` 的 `appliedStones` 构成(`type:uses:temp` 用 `|` 连接;无 = `-`) */
function mamuDiceStones(p) {
    var out = "";
    try {
        var enh = domEnh(p);
        if (enh == null) return "none";
        var list = enh.appliedStones();
        if (list == null) return "none";
        for (var i = 0; i < list.size(); i++) {
            var s = list.get(i);
            if (s == null) continue;
            out = (out === "") ? (s.type() + ":" + s.uses() + ":" + (s.temporary() ? 1 : 0))
                : (out + "|" + s.type() + ":" + s.uses() + ":" + (s.temporary() ? 1 : 0));
        }
    } catch (e) { return "?"; }
    return (out === "") ? "-" : out;
}

/** 骰子装配栏费用读数(`used/max`;读不到 = `?/0`) */
function mamuDiceCosts(p) {
    var c = domUsedCost(p);
    var enh = domEnh(p);
    var m = "0";
    try { m = "" + enh.maxCost(); } catch (e) { m = "0"; }
    return c.atk + "/" + m;
}

/**
 * 读一张卡牌栈的 `owner_uuid` 组件(跨线容错)。返回 `{ uuid, api }`:
 * `uuid == null` 且 `api == ""` ⇒ **组件确实不存在**(= 无主);`api != ""` ⇒ **两条读取路径都抛错**
 * (探针侧 API 不可用,与"无主"是两回事,绝不混用)。
 *
 * <p>⚠️ **2026-09-27 实机踩坑(读数缺陷)**:旧写法只用「`opt == null`」判断,而
 * `ItemStack#getOrDefault(type, null)` 在**组件不存在**时**返回 `null`(不抛错)** ⇒ 落进第二条
 * (1.20.1 形态,该线才有)必然抛错 ⇒ 把「无主」误报成 `err`。实测表现:`CONV` 行的
 * `owner_before=err:owner_after=err`(栈上根本没绑定),读起来像"读 owner 的路径坏了"。
 * 现在**分别记录每条路径是「返回成功」还是「抛错」**,只有全抛错才判 `err`。
 *
 * <p>两条路径(与 `nardiEnhOf` / `README` 记录的两线形态一致,顺序固定):
 * ① 1.21.1 = `ItemStack#getOrDefault(DataComponentType, T)`(先 `.get()` 解 `DeferredHolder`);
 * ② 1.20.1 = `ItemDataKey#getOrDefault(ItemStack, T)`。
 * 组件值两线都是 `Optional<UUID>`;若 Rhino 直接给出 UUID(自动解包),按"有主"处理。
 */
function mamuOwnerUuid(stack) {
    var out = { uuid: null, api: "" };
    if (stack == null || stack.isEmpty()) return out;
    var opt = null, got = false, why = "";
    // ① 1.21.1:ItemStack#getOrDefault(DataComponentType, T);组件不存在 ⇒ 返回默认值 null(**不抛错**)
    try { opt = stack.getOrDefault(ModDataComponentsClass.OWNER_UUID.get(), null); got = true; }
    catch (e1) { why = why + "|a:" + domExText(e1); }
    // ② 1.20.1:ItemDataKey#getOrDefault(ItemStack, T)
    if (!got || opt == null) {
        try { opt = ModDataComponentsClass.OWNER_UUID.getOrDefault(stack, null); got = true; }
        catch (e2) { why = why + "|b:" + domExText(e2); }
    }
    if (opt == null) { out.api = got ? "" : why; return out; }
    // 组件值形态:Optional<UUID>(首选)或已被 Rhino 解包的裸 UUID
    var hasFn = false;
    try { hasFn = (typeof opt.isPresent === "function"); } catch (e3) { hasFn = false; }
    if (hasFn) {
        var has = false;
        try { has = opt.isPresent(); } catch (e4) { out.api = "|c:" + domExText(e4); return out; }
        if (!has) return out;
        try { out.uuid = "" + opt.get(); } catch (e5) { out.api = "|d:" + domExText(e5); }
        return out;
    }
    try { out.uuid = "" + opt; } catch (e6) { out.api = "|e:" + domExText(e6); }
    return out;
}

/**
 * 一张卡牌栈的获得者绑定(`owner_uuid`)。
 * 返回**闭集** `self`(绑定=本人) / `other`(绑定=他人) / `none`(无主) / `err`(探针读不到)。
 * `err` 的具体原因由调用方另置一个 `owner_api=` 字段给出(**不塞进本字段**,保持闭集可断言)。
 */
function mamuOwnerTag(stack, p) {
    if (stack == null || stack.isEmpty()) return "none";
    var r = mamuOwnerUuid(stack);
    if (r.api !== "") return "err";
    if (r.uuid == null) return "none";
    // ⚠️ 2026-09-28 实测缺陷(读数自相矛盾,害 4 条断言假红):原写法直接 `p.getUUID()`,
    //    而 **KubeJS 的方法白名单里没有 `ServerPlayer#getUUID`**(本文件既有踩坑,见 `playerUuid`
    //    的头注与 `zhaoUuidText` 的写法)⇒ 只要卡牌**真的有主**,这里就抛 `Cannot find function getUUID`
    //    ⇒ 本函数落进 `err`,而 `owner_api_*` 仍是 `ok`(那条路径没抛)⇒ 实测读数
    //    `owner_after=err:owner_api_after=ok` / `bound_foreign=owner=err:owner_api=ok`,
    //    看起来像「转换后绑定丢失」「有主栈被当成读不到」——**假缺陷**。
    //    注意 `owner_before=none` 那一拍不会暴露它(无主在更早的 `r.uuid == null` 就返回了),
    //    所以「转换前 none、转换后 err」恰好只会在**绑定成功**时出现。
    //    一律走既有容错取值器(它按 `getUUID()` → `p.uuid` → `UUIDUtil` 三级回退)。
    var uu = playerUuid(p);
    if (!uu.ok) return "err";
    return (r.uuid === ("" + uu.value)) ? "self" : "other";
}

/**
 * 立牌主动强制冷却的**读数**(规格 §3.4):证明「不可被任何减免绕过」需要三个数一起看 ——
 * `mfu`(强制截止刻)、`cdend`(`sign_active_cooldown_end`)、`maxc`(`sign_active_max_cooldown`)。
 * 三者一次给出,断言层无需算术(差值标志由本函数算成 0/1)。
 *
 * <p>⚠️ **2026-09-27 修正(`mfu_is_1200` 恒为 0 是读数缺陷,不是产品缺陷)**:旧定义是
 * `(until − base) === 1200`,而 `base` 是**本命令刚刚写进去的那个值** ⇒ 差值恒为 0,
 * 该字段**永远不可能为 1**(实机含 `mode=present` 那一拍也是 0)。现改为按**产品口径**判:
 * 写入口 `MamushiSignItem#handleUse` 是 `level.getGameTime() + ACTIVE_FORCED_COOLDOWN_TICKS`,
 * 故不变量是「`mfu − now` 恰好等于产品常量」——
 *   `mfu_is_1200`       = 距 `now` 恰好 **1200**(字面量,留着便于人读);
 *   `mfu_remain_is_cfg` = 距 `now` 恰好等于**产品常量** `ACTIVE_FORCED_COOLDOWN_TICKS`(推荐断言项,
 *                         常量若被改动该标志自动跟随);`mfu_cfg` 把常量本身也打出来。
 * 另补 `mfu_entry`(进入本命令时、**任何探针写入之前**的值)与 `mfu_shift`(= 现值 − `mfu_entry`),
 * 用于区分「本次真的写了」与「值没动」;`mfu_delta` 保持旧义(= 现值 − **本命令写入值**),
 * 但已在文档里写明它在 `present`/`cast` 档恒为 0(写入值就是 `base`)。
 */
function mamuCdRead(p, baseUntil, entryUntil) {
    var now = nowTick(p) - 0;
    var until = mamuForcedUntil(p);
    var cdEnd = mamuCdEnd(p);
    var maxc = mamuMaxCd(p);
    var cfg = -1;
    try { cfg = MamuSignItemClass.ACTIVE_FORCED_COOLDOWN_TICKS - 0; } catch (eC) { cfg = -1; }
    var dUntil = (baseUntil === null || baseUntil === undefined || until < 0) ? -999 : (until - baseUntil);
    var remain = (until > 0 && now > 0) ? (until - now) : -1;
    var entry = (entryUntil === null || entryUntil === undefined) ? -1 : entryUntil;
    var shift = (entry < 0 || until < 0) ? -999 : (until - entry);
    return "now=" + now
        + ":mfu=" + until + ":mfu_delta=" + dUntil + ":mfu_remain=" + remain
        + ":cd_end=" + cdEnd + ":cd_remain=" + ((cdEnd > 0 && now > 0) ? (cdEnd - now) : -1)
        + ":maxc=" + maxc
        + ":mfu_cfg=" + cfg
        + ":mfu_is_1200=" + (((until > 0) && (remain === 1200)) ? 1 : 0)
        + ":mfu_remain_is_cfg=" + (((until > 0) && (cfg > 0) && (remain === cfg)) ? 1 : 0)
        + ":mfu_entry=" + entry + ":mfu_shift=" + shift
        + ":maxc_is_1200=" + ((maxc === 1200) ? 1 : 0)
        + ":cd_eq_maxc=" + ((cdEnd > 0 && maxc > 0 && (cdEnd - now) === maxc) ? 1 : 0)
        + ":forced_active=" + ((until > 0 && now < until) ? 1 : 0);
}

/**
 * 真龙形态/破防两个效果的 amp/dur(`fx_d=amp/dur`、`fx_d_on` 的既有读法)。
 * `which`:0 = 真龙形态(MAMUSHI_DRAGON);1 = 破防(DRAGON_ROAR_BREAK)。
 */
function mamuFx(p, which) {
    var desc = (which === 1) ? DESC_DRAGON_ROAR_BREAK : DESC_MAMUSHI_DRAGON;
    var inst = null;
    try { inst = findEffect(p, desc); } catch (e) { inst = null; }
    var amp = -1, dur = -1;
    try { if (inst != null) { amp = inst.getAmplifier(); dur = inst.getDuration(); } } catch (e2) { amp = -1; dur = -1; }
    return { amp: amp, dur: dur, on: (inst != null) ? 1 : 0 };
}

/**
 * 破防效果挂在**实体护甲属性**上的实际修饰量(规格 §7 边界:「要能读到破防对实际减伤的影响」)。
 *
 * <p>修饰器 id 两线**形态不同**(1.21.1 = `ResourceLocation("astral_dice:dragon_roar_break_armor")`;
 * 1.20.1 = 同一语义名的 UUIDv5 `188e1666-…`),故**不按 id 取值**,而是遍历 `Attribute#getModifiers()`
 * 并按三个客观条件挑出**那一个**:`amount == ARMOR_DELTA(-8.0)`、`operation == ADDITION`、
 * 且 id/名字串里不含其它立牌的键名(`unwavering` / `blue_curse` / `teru_descent` —— 它们同样
 * 走 ARMOR 修饰器,是本实体上最可能的同名干扰项)。命中 ⇒ `amt=-8`、`n=1`;
 * 未命中但存在 `-8.0` 的修饰器 ⇒ `n=1, amt=<原值>`(由断言按 `-8` 判)。
 * 返回 `break_amt` / `break_mods` / `break_armor` / `break_base`。
 */
function mamuBreakArmor(ent) {
    var out = { amt: 0, mods: 0, armor: -9, base: -9, op: "-", id: "-" };
    if (ent == null) return out;
    try {
        var Attrs = Java.loadClass("net.minecraft.world.entity.ai.attributes.Attributes");
        var a = ent.getAttribute(Attrs.ARMOR);
        if (a == null) return out;
        out.armor = Math.round(a.getValue() * 100) / 100;
        out.base = Math.round(a.getBaseValue() * 100) / 100;
        var delta = -8.0;
        try { if (MamuBreakEffectClass != null) delta = MamuBreakEffectClass.ARMOR_DELTA - 0; } catch (e0) { /* 用冻结值 */ }
        var list = a.getModifiers();
        var it = list.iterator();
        while (it.hasNext()) {
            var m = it.next();
            if (m == null) continue;
            var amt = -999;
            try { amt = m.amount() - 0; } catch (e1) { try { amt = m.getAmount() - 0; } catch (e2) { amt = -999; } }
            if (Math.abs(amt - delta) > 0.0001) continue;
            var id = "";
            try { id = "" + m.id(); } catch (e3) { try { id = "" + m.getName(); } catch (e4) { id = ""; } }
            var other = (id.indexOf("unwavering") >= 0) || (id.indexOf("blue_curse") >= 0)
                || (id.indexOf("teru_descent") >= 0);
            if (other) continue;
            out.amt = Math.round(amt * 100) / 100;
            out.mods = out.mods + 1;
            // 修饰器 id 的**两线形态差异**在这里显式暴露(1.21.1 = ResourceLocation、
            // 1.20.1 = UUIDv5 字符串);按语义名匹配只对 1.21.1 成立,故只做**读数标注**、不作断言依据。
            out.op = (id.indexOf(MAMU_BREAK_ARMOR_KEY) >= 0) ? "rl" : ((id === "") ? "?" : "uuid");
            out.id = (id.length > 48) ? (id.substring(0, 48) + "…") : id;
        }
    } catch (e5) { /* 读不到就保持 -9/-9 的显式失败值 */ }
    return out;
}

/** 主物品栏 + 副手 + 骰子卡牌栏里的撕咬/龙之咆哮张数(转换前后对照用) */
function mamuCardCounts(p) {
    return "inv_bite=" + mamuInvCount(p, MAMU_BITE_ID)
        + ":inv_roar=" + mamuInvCount(p, MAMU_ROAR_ID)
        + ":eq_bite=" + mamuEquippedCount(p, MAMU_BITE_TYPE)
        + ":eq_roar=" + mamuEquippedCount(p, MAMU_ROAR_TYPE);
}

/** 只读:当前环境里是否在**某处**存在指定 id 的实体(生成失败的可归因读数,不静默) */
function mamuEntText(ent) {
    if (ent == null) return "none";
    try { return typeIdOf(ent) + "#" + ent.getId(); } catch (e) { return "?"; }
}

/** 该玩家当前所有效果的 `id/amp/dur`(短名单:只取本段关心的 3 个;避免整表噪声) */
function mamuEffects(p) {
    return "dragon=" + mamuFx(p, 0).amp + "/" + mamuFx(p, 0).dur
        + ":dragon_on=" + mamuFx(p, 0).on
        + ":break=" + mamuFx(p, 1).amp + "/" + mamuFx(p, 1).dur
        + ":break_on=" + mamuFx(p, 1).on;
}

/** 世界 `keepInventory` 游戏规则的当前值(0/1;-1 = 读不到)。死亡保留用例必须显式报告它 */
function mamuKeepInvRule(p) {
    try {
        var GR = Java.loadClass("net.minecraft.world.level.GameRules");
        var lvl = p.level;
        var srv = lvl.getServer();
        var src = (srv != null) ? srv.getGameRules() : lvl.getGameRules();
        return src.getBoolean(GR.RULE_KEEPINVENTORY) ? 1 : 0;
    } catch (e) { return -1; }
}

/**
 * 让玩家**真死亡**(等价原版 `/kill`:`LivingEntity#kill` → `hurt(damageSources().genericKill(), Float.MAX_VALUE)`;
 * 1.21.1 `LivingEntity#kill:302` / 1.20.1 `:266`,两线均 public)。
 *
 * <p>**判据是「真的死了」而不是「没抛异常」**:`hurt(DamageSource,float)` 在 Rhino 下对部分实体
 * 不可见(见 `meleeHit` 的既有注释)⇒ 逐条回退,并把**生效路径**写进读数(不静默)。
 * 返回 `kill` / `hurt_genericKill` / `damage_genericKill` / `cmd:<rc>` / `fail` / `unknown`。
 *
 * <p>⚠️ 命令通道(`cmd:`)列在**最后**:既有 `applyFallDamage` 的注释证明 `/damage` 在
 * `performPrefixedCommand` 上会被**推迟到本 tick 末**,同一次调用里读不到结果;
 * 故它只作兜底,真死判据仍由本函数的 `dead()` 现读决定。
 */
function mamuKill(p) {
    function dead() {
        try { if (p.isDeadOrDying()) return true; } catch (e) { /* 落到下一判据 */ }
        try { return (p.getHealth() - 0) <= 0; } catch (e) { return false; }
    }
    try { p.kill(); if (dead()) return "kill"; } catch (e1) { /* 回退 */ }
    var src = null;
    try { src = p.damageSources().genericKill(); } catch (e2) { src = null; }
    if (src != null) {
        try { p.hurt(src, 1.0e9); if (dead()) return "hurt_genericKill"; } catch (e3) { /* 回退 */ }
        try { p.damage(1.0e9, src); if (dead()) return "damage_genericKill"; } catch (e4) { /* 回退 */ }
    }
    try { var rc = "" + runCmdP(p, "kill @s"); if (dead()) return "cmd:" + rc; } catch (e5) { /* 回退 */ }
    return dead() ? "unknown" : "fail";
}

/**
 * 服务端**真重生**:`PlayerList#respawn(...)`,与原版玩家点「重生」走的是**同一条**原版代码路径
 * (`ServerGamePacketListenerImpl:1676` → `PlayerList#respawn`)。客户端会收到
 * `ClientboundRespawnPacket`(`PlayerList.java:490`)。
 *
 * <p>⚠️ **别指望它自动关掉死亡界面**(2026-09-28 实测推翻旧注释):`ClientPacketListener#handleRespawn`
 * 只在「当前界面已是 `DeathScreen`」时才 `setScreen(null)`(`:1230-1232`),而死亡包更晚到时会**新建**
 * 界面 ⇒ 界面永久驻留、聊天注入全被吞(详见 {@link doMamuDie} 头注的取证与正确口径:
 * 由 `mt_launch.ps1` 的 `gamerule doImmediateRespawn true` 环境不变量保证从不创建该界面)。
 *
 * <p>⚠️⚠️ **签名两线不同**(2026-09-27 反编译源码核对,必须双形态):
 * <ul>
 *   <li>1.21.1 `PlayerList.java:456`:`respawn(ServerPlayer, boolean keepInventory, Entity.RemovalReason)`
 *       —— **没有** 2 参重载;原版死亡重生调用点传 `Entity.RemovalReason.KILLED`(`:1676`);</li>
 *   <li>1.20.1 `PlayerList.java:437`:`respawn(ServerPlayer, boolean keepEverything)`。</li>
 * </ul>
 * 故先试 3 参(1.21.1),失败再试 2 参(1.20.1);`RemovalReason` 也走两条取值路径
 * (`Java.loadClass("…Entity$RemovalReason")` → `EntityClass.RemovalReason`)。两条都失败时
 * **不静默**:`how=fail` 且 `err=` 给出真实异常文本。
 *
 * <p>返回 `{player, how, rr, err}`;`how` = `playlist3` | `playlist2` | `fail` | `no_server` | `no_playerlist`。
 * **调用方必须改用返回的新实体**:旧 `ServerPlayer` 已在 `respawn` 里被 `removePlayerImmediately` 移除。
 */
function mamuRespawn(p, keepInv) {
    var out = { player: null, how: "fail", rr: "none", err: "" };
    var srv = null;
    try { srv = p.level.getServer(); } catch (e0) { srv = null; }
    if (srv == null) { out.how = "no_server"; return out; }
    var list = null;
    try { list = srv.getPlayerList(); } catch (e1) { list = null; }
    if (list == null) { out.how = "no_playerlist"; return out; }
    var rr = null;
    try { rr = Java.loadClass("net.minecraft.world.entity.Entity$RemovalReason").KILLED; out.rr = "cls"; }
    catch (e2) {
        try { rr = EntityClass.RemovalReason.KILLED; out.rr = "outer"; }
        catch (e3) { rr = null; out.rr = "none"; }
    }
    if (rr != null) {
        try {
            var np = list.respawn(p, keepInv, rr);
            if (np != null) { out.player = np; out.how = "playlist3"; return out; }
        } catch (e4) { out.err = domExText(e4); }
    }
    try {
        var np2 = list.respawn(p, keepInv);
        if (np2 != null) { out.player = np2; out.how = "playlist2"; return out; }
    } catch (e5) { out.err = out.err + "|" + domExText(e5); }
    out.how = "fail";
    return out;
}

/**
 * 读数出口:优先直发指定玩家(`emitTo`),否则走命令上下文(`send`)。
 *
 * <p>为什么需要它:`mamudie` 重生后**旧 `ServerPlayer` 已被 `removePlayerImmediately` 移除**
 * (新实体由 `PlayerList#respawn` 返回)⇒ 命令上下文持有的那个旧引用不再是权威出口。
 * 传新玩家可保证读数一定送达。其余调用点不传第三参 ⇒ 行为与原来**逐字相同**。
 */
function mamuEmit(ctx, target, text) {
    if (target != null && target !== undefined) { emitTo(target, text); return; }
    send(ctx, text);
}

/**
 * 全量快照(**多行**,每行一个稳定标签;字段只追加不得改名改序):
 *   `AP_<tag>_MAW`  觉醒/真龙/佩戴/攻击力/撕咬
 *   `AP_<tag>_MCARD` 主栏 + 副手 + 骰子卡牌栏的撕咬/龙之咆哮
 *   `AP_<tag>_MDICE` 骰子装配项与费用
 *   `AP_<tag>_MFX`  真龙形态/破防效果
 *   `AP_<tag>_MCD`  强制冷却三件套
 *   `AP_<tag>_MBASE` 宿主读数(既有 domStateRead,整段包在 `mamu=[…]` 内)
 *
 * <p>形态成立却缺效果时补一次**幂等**转换(与产品 `MamushiSignItem#onCurioTick` 每 20 tick 的
 * 幂等保底同一入口)—— 否则「直写 8 层」的脚手架会读到 `dragon_on=0`,把脚手架差异误判成缺陷。
 *
 * @param emitTarget 可选;非空时所有读数行经 {@link mamuEmit} 直发该玩家(重生后的新实体)。
 */
function mamuRead(ctx, tag, p, phase, emitTarget) {
    if (p == null) { mamuEmit(ctx, emitTarget, "AP_" + tag + "_" + phase + ":found=0"); return 1; }
    var df = mamuDragonFlag(p);
    var fxD = mamuFx(p, 0);
    var healed = 0;
    if (df === 1 && fxD.on === 0) {
        try { MamuSignItemClass.transformToDragon(p); healed = 1; } catch (e0) { healed = -1; }
        fxD = mamuFx(p, 0);
    }
    mamuEmit(ctx, emitTarget, "AP_" + tag + "_" + phase + "_MAW:sign=" + mamuSignEquipped(p)
        + ":awk=" + mamuAwaken(p)
        + ":df=" + df
        + ":ap=" + (TeruDiceCombatModifiersClass == null ? -1 : domNum(function () { return TeruDiceCombatModifiersClass.attackPowerOf(p); }))
        + ":dp=" + (TeruDiceCombatModifiersClass == null ? -1 : domNum(function () { return TeruDiceCombatModifiersClass.defensePowerOf(p); }))
        + ":bite_latch=" + mamuBiteBonusActive(p)
        + ":inv_bite=" + mamuInvCount(p, MAMU_BITE_ID)
        + ":inv_roar=" + mamuInvCount(p, MAMU_ROAR_ID)
        + ":inv_bite_slots=[" + mamuInvCards(p, MAMU_BITE_ID) + "]"
        + ":inv_roar_slots=[" + mamuInvCards(p, MAMU_ROAR_ID) + "]"
        + ":fx_healed=" + healed);
    mamuEmit(ctx, emitTarget, "AP_" + tag + "_" + phase + "_MCARD:" + mamuCardCounts(p)
        + ":any_card=" + mamuHasAnyCard(p));
    mamuEmit(ctx, emitTarget, "AP_" + tag + "_" + phase + "_MDICE:stones=[" + mamuDiceStones(p) + "]"
        + ":cost=" + mamuDiceCosts(p)
        + ":dice_slot=" + diceSlotItemId(p));
    mamuEmit(ctx, emitTarget, "AP_" + tag + "_" + phase + "_MFX:" + mamuEffects(p));
    var bd = mamuBreakArmor(p);
    mamuEmit(ctx, emitTarget, "AP_" + tag + "_" + phase + "_MCD:" + mamuCdRead(p, null)
        + ":p_break_amt=" + bd.amt + ":p_break_armor=" + bd.armor);
    mamuEmit(ctx, emitTarget, "AP_" + tag + "_" + phase + "_MBASE:mamu=[" + domStateRead(p) + "]");
    return 1;
}

/** 类门禁:任一产品类缺失 ⇒ 报 ERR(负向断言会立刻抓出) */
function mamuClassGate(ctx, tag) {
    var miss = "";
    if (MamuSignItemClass == null) miss = miss + "|MamushiSignItem";
    if (MamuCardUtilClass == null) miss = miss + "|DragonCardUtil";
    if (MamuDragonEffectClass == null) miss = miss + "|MamushiDragonEffect";
    if (MamuBreakEffectClass == null) miss = miss + "|DragonRoarBreakEffect";
    if (MamuCurrentCoreClass == null) miss = miss + "|CurrentCoreChipItem";
    if (MamuChargeManagerClass == null) miss = miss + "|ChargeManager";
    if (ExclusiveCardUtilClass == null) miss = miss + "|ExclusiveCardUtil";
    if (miss !== "") { send(ctx, "AP_" + tag + "_ERR:no_class:" + miss); return false; }
    return true;
}

/** 解析目标(bot / 真实玩家);缺省/`-` ⇒ 自身。找不到 ⇒ ERR(可归因,不静默) */
function mamuResolve(ctx, tag, p, nameText, what) {
    return zhaoResolve(ctx, tag, p, nameText, what);
}

/** 把玩家拉回同一基线:清两张专属牌 / 立牌 / 骰子 / 效果 / 锁存 / 冷却 / 出牌轮 / 测试筹码 */
function mamuClearState(p) {
    var err = "";
    try { mamuClearItem(p, MAMU_BITE_ID); } catch (e1) { err = err + "|bite:" + domExText(e1); }
    try { mamuClearItem(p, MAMU_ROAR_ID); } catch (e2) { err = err + "|roar:" + domExText(e2); }
    try { clearCurioSlots(p, "stand"); } catch (e3) { err = err + "|stand:" + domExText(e3); }
    try { clearCurioSlots(p, "dice"); } catch (e4) { err = err + "|dice:" + domExText(e4); }
    try { clearCurioSlots(p, "chip"); } catch (e5) { err = err + "|chip:" + domExText(e5); }
    try { MamuSignItemClass.setAwakening(p, 0); } catch (e6) { err = err + "|awk:" + domExText(e6); }
    try { MamuSignItemClass.setBiteBonusActive(p, false); } catch (e7) { err = err + "|latch:" + domExText(e7); }
    try { MamuSignItemClass.transformToDragon(p); } catch (e8) { /* 无卡即空操作 */ }
    try { ModAttachments.setMamushiForcedCooldownUntil(p, 0); } catch (e9) { err = err + "|mfu:" + domExText(e9); }
    try { ModEffectRemoval.remove(p, mamuEffectHolder(0)); } catch (e10) { /* 无效果即空操作 */ }
    try { resetEffectCardCycle(p); } catch (e11) { /* 忽略 */ }
    try { ModAttachments.setFenRecharge(p, 0); } catch (e12) { /* 忽略 */ }
    try { mamuClearItem(p, "minecraft:iron_sword"); } catch (e13) { /* 忽略 */ }
    try { mamuClearItem(p, MAMU_DICE_ID); } catch (e14) { /* 忽略 */ }
    return err;
}

/**
 * 清主物品栏(0..35)+ 副手里指定物品;**先搬到主手再清**会带来"手里仍有牌"的假读数,
 * 故只在**不动手**的前提下逐格清(与 zhaoClearItem 同法)。
 */
function mamuClearItem(p, itemId) {
    return zhaoClearItem(p, itemId);
}

/**
 * 脚手架操作的返回值 → **语义明确**的状态串:`ok`(null / undefined / 空串 / "null")或 `fail:<原因>`。
 *
 * <p>为什么需要它:本文件既有的 `*_err=` 读法是「空串 = 成功」,拼进一行里肉眼可分但**语义反直觉**
 * —— 2026-09-27 实机就出现 `bot_err=|sign:|dice:`(三项全成功)被读成"bot 侧装配失败"。
 * 凡**成/败都是正常结果**的脚手架一律用它,不要再用 `*_err=` 拼空串。
 */
function mamuStatus(x) {
    if (x === null || x === undefined) return "ok";
    var s = "" + x;
    if (s === "" || s === "null") return "ok";
    return "fail:" + s;
}

/**
 * 物品是否带某标签(`astral_dice:signs` / `curios:stand` / `astral_dice:is_exclusive` /
 * `astral_dice:combat_cards`);返回 `1` / `0` / `-1`(读不到)。
 *
 * <p>⚠️ **必须是顶层函数**:Rhino/KubeJS 下**块内**函数声明(`try { function f(){} }`)在块内调用
 * 会得到 `undefined` ⇒ `TypeError: f is not a function`。2026-09-27 实机踩坑:整个 `REG_TAG` 行
 * 只输出 `tag_err=[] TypeError: hasTag is not a function, it is undefined.:tag_ex=1`。
 * (函数体**顶层**的函数声明没问题 —— 同段的 `flag` / `hit` / `giveOne` / `snapRow` 等实机均正常。)
 *
 * <p>⚠️ 不得走 `ItemStack#is(TagKey)`(Rhino 重载歧义)⇒ 读 `Holder#tags()` 按 `location()` 比字符串
 * (与 zhao 段 `doZhaoReg` 同法)。首选 `Registry#wrapAsHolder(item)`,失败退回 `Item#builtInRegistryHolder()`。
 */
function mamuHasTag(item, want) {
    if (item == null) return 0;
    var arr = null;
    try { arr = BuiltInRegistries.ITEM.wrapAsHolder(item).tags().toArray(); } catch (e1) { arr = null; }
    if (arr == null) {
        try { arr = item.builtInRegistryHolder().tags().toArray(); } catch (e2) { arr = null; }
    }
    if (arr == null) return -1;
    for (var i = 0; i < arr.length; i++) {
        var loc = "";
        try { loc = "" + arr[i].location(); } catch (e3) { loc = ""; }
        if (loc === want) return 1;
    }
    return 0;
}

/**
 * 基线:`mamuclear` + 装立牌 + 骰子入 curios + 主手铁剑;可选把对 bot 也拉回同一基线。
 * 读数:立牌/骰子/铁剑的装配结果 + 全量快照(便于前置断言直接锚定)。
 *
 * <p>【2026-09-27 实机修正】bot 侧原来只输出一个 `bot_err=|sign:|dice:` —— 三个子操作**全部成功**
 * 时该串也是 `|sign:|dice:`(错误文本为空),字段名却叫 `err`,把"成功"读成了"失败"(假红)。
 * 现改为**语义明确的逐项状态** `bot_clear` / `bot_sign` / `bot_dice`(值 = `ok` | `fail:<原因>`)
 * 外加**回读证据** `bot_stand`(= `MamushiSignItem.isEquipped(bot)` 的 0/1/-1):
 * 对 bot 装立牌/骰子**是必要的**(`mamubatch other` 需要"另一名佩戴立牌的发牌者"),故保留该动作,
 * 只是不再把它记成错误。无 bot 参数时这四个字段**不出现**(保持原形态不变)。
 *
 * <p>`sword_err` 的**唯一**出现条件(2026-09-27 澄清):`p.setItemInHand(MAIN_HAND, new ItemStack(<铁剑>))`
 * 抛异常 —— 即 ①`ZhaoHandClass.MAIN_HAND` 取不到,或 ②`resolveItem("minecraft:iron_sword")` 返回
 * `null` ⇒ `new ItemStack(null)` 构造抛错。正常环境下该字段**不出现**(不是空串,是整段不拼接)。
 */
function doMamuPrep(ctx, tag, botText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var err = mamuClearState(p);
    var signErr = equipSign(p, MAMU_SIGN_ID);
    if (signErr == null) signErr = "";
    var diceErr = "unknown_item:" + MAMU_DICE_ID;
    var diceItem = resolveItem(MAMU_DICE_ID);
    if (diceItem != null) diceErr = putInSlot(p, "dice", new ItemStack(diceItem), 0);
    if (diceErr == null) diceErr = "";
    var swordErr = "";
    try { p.setItemInHand(ZhaoHandClass.MAIN_HAND, new ItemStack(resolveItem("minecraft:iron_sword"))); }
    catch (e1) { swordErr = domExText(e1); }
    var bot = "-", botClear = "-", botSign = "-", botDice = "-", botStand = -1;
    if (("" + botText) !== "") {
        var t = mamuResolve(ctx, tag, p, botText, "no_player");
        if (t == null) return 1;
        bot = "" + botText;
        botClear = mamuStatus(mamuClearState(t));
        botSign = mamuStatus(equipSign(t, MAMU_SIGN_ID));
        var bDiceItem = resolveItem(MAMU_DICE_ID);
        botDice = (bDiceItem == null) ? ("fail:unknown_item:" + MAMU_DICE_ID)
            : mamuStatus(putInSlot(t, "dice", new ItemStack(bDiceItem), 0));
        // 回读证据:立牌真的进了 bot 的 curios `stand` 槽(空串状态串不足以证明)
        botStand = mamuSignEquipped(t);
    }
    send(ctx, "AP_" + tag + "_MU_PREP:sign_err=" + signErr + ":dice_err=" + diceErr
        + (swordErr === "" ? "" : ":sword_err=" + swordErr)
        + ":bot=" + bot
        + (bot === "-" ? "" : (":bot_clear=" + botClear + ":bot_sign=" + botSign
            + ":bot_dice=" + botDice + ":bot_stand=" + botStand))
        + ":online=" + zhaoOnlineNames(ctx)
        + ":nmobs=" + zhaoNum(function () { return p.level.getEntitiesOfClass(LivingEntityClass, AABBClass.ofSize(p.position(), 64, 64, 64)).size(); }));
    return mamuRead(ctx, tag, p, "BASE");
}

/** 收尾:全清 + 全量读数 */
function doMamuClear(ctx, tag) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var err = mamuClearState(p);
    send(ctx, "AP_" + tag + "_MU_CLEAR:cleared=1" + (err === "" ? "" : ":err=" + err));
    return mamuRead(ctx, tag, p, "ZERO");
}

/**
 * 注册与冻结数值(规格 §1);全部走**只读**判定。
 * 读数:
 *   `AP_<tag>_REG_SRC`  物品/类的存在与身份
 *   `AP_<tag>_REG_CONST` 常量(逐条 产品常量 → 期望值 → 相等标志,断言层无算术)
 *   `AP_<tag>_REG_TAG`  标签(`astral_dice:signs` / `curios:stand` / `is_exclusive` / `combat_cards`)
 *   `AP_<tag>_REG_TYPE` `CardRegistry` 的 typeId ↔ 物品 双向映射(费用/耐久)
 */
function doMamuReg(ctx, tag) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var signItem = resolveItem(MAMU_SIGN_ID);
    var biteItem = resolveItem(MAMU_BITE_ID);
    var roarItem = resolveItem(MAMU_ROAR_ID);
    send(ctx, "AP_" + tag + "_REG_SRC:sign_item=" + (signItem == null ? "MISSING" : "ok")
        + ":bite_item=" + (biteItem == null ? "MISSING" : "ok")
        + ":roar_item=" + (roarItem == null ? "MISSING" : "ok")
        + ":sign_is=" + ((signItem != null && (signItem instanceof MamuSignItemClass)) ? 1 : 0)
        + ":dragon_fx_is=" + (MamuDragonEffectClass == null ? -1 : 1)
        + ":break_fx_is=" + (MamuBreakEffectClass == null ? -1 : 1)
        + ":core_is=" + (MamuCurrentCoreClass == null ? -1 : 1)
        + ":vitamin_is=" + (MamuVitaminPillClass == null ? -1 : 1)
        + ":moditems_is=" + (MamuItemsClass == null ? -1 : 1)
        + ":break_mod=" + (MamuBreakEffectClass == null ? "n/a" : MAMU_BREAK_ARMOR_KEY));

    var C = MamuSignItemClass;
    function flag(actual, want) { return ((actual - 0) === (want - 0)) ? 1 : 0; }
    send(ctx, "AP_" + tag + "_REG_CONST:awk_max=" + C.AWAKEN_MAX + ":awk_max_ok=" + flag(C.AWAKEN_MAX, 8)
        + ":cap=" + C.AWAKEN_GRANT_CAP_PER_EVENT + ":cap_ok=" + flag(C.AWAKEN_GRANT_CAP_PER_EVENT, 3)
        + ":bite_cap=" + C.BITE_BONUS_CAP + ":bite_cap_ok=" + flag(C.BITE_BONUS_CAP, 4)
        + ":dragon_bonus=" + C.DRAGON_FORM_ATTACK_BONUS + ":dragon_bonus_ok=" + flag(C.DRAGON_FORM_ATTACK_BONUS, 5)
        + ":range=" + C.ACTIVE_RANGE + ":range_ok=" + flag(C.ACTIVE_RANGE, 12)
        + ":max_t=" + C.ACTIVE_MAX_TARGETS + ":max_t_ok=" + flag(C.ACTIVE_MAX_TARGETS, 32)
        + ":forced=" + C.ACTIVE_FORCED_COOLDOWN_TICKS + ":forced_ok=" + flag(C.ACTIVE_FORCED_COOLDOWN_TICKS, 1200)
        + ":bite_cost=" + C.BITE_COST + ":bite_cost_ok=" + flag(C.BITE_COST, 2)
        + ":bite_uses=" + C.BITE_USES + ":bite_uses_ok=" + flag(C.BITE_USES, 1)
        + ":bite_atk=" + C.BITE_ATTACK + ":bite_atk_ok=" + flag(C.BITE_ATTACK, 3)
        + ":roar_cost=" + C.ROAR_COST + ":roar_cost_ok=" + flag(C.ROAR_COST, 3)
        + ":roar_uses=" + C.ROAR_USES + ":roar_uses_ok=" + flag(C.ROAR_USES, 5)
        + ":roar_atk=" + C.ROAR_ATTACK + ":roar_atk_ok=" + flag(C.ROAR_ATTACK, 3)
        + ":roar_ticks=" + C.ROAR_DEBUFF_TICKS + ":roar_ticks_ok=" + flag(C.ROAR_DEBUFF_TICKS, 1200)
        + ":roar_amp=" + C.ROAR_SLOW_AMPLIFIER + ":roar_amp_ok=" + flag(C.ROAR_SLOW_AMPLIFIER, 2)
        + ":roar_armor=" + C.ROAR_ARMOR_DELTA + ":roar_armor_ok=" + flag(C.ROAR_ARMOR_DELTA, -8)
        + ":break_delta=" + MamuBreakEffectClass.ARMOR_DELTA + ":break_delta_ok=" + flag(MamuBreakEffectClass.ARMOR_DELTA, -8)
        + ":type_bite=" + MamuCardUtilClass.TYPE_BITE + ":type_bite_ok=" + ((MamuCardUtilClass.TYPE_BITE === "bite") ? 1 : 0)
        + ":type_roar=" + MamuCardUtilClass.TYPE_DRAGON_ROAR + ":type_roar_ok=" + ((MamuCardUtilClass.TYPE_DRAGON_ROAR === "dragon_roar") ? 1 : 0));

    // ⚠️ 标签判定不得走 ItemStack#is(TagKey)(Rhino 重载歧义)⇒ `mamuHasTag` 读 Holder#tags()
    //    按 location() 比字符串。**该助手必须是顶层函数**(块内函数声明在 Rhino 下不可见,见其注释)。
    var tagTxt = "", tagErr = "";
    try {
        tagTxt = ":sign_tag=" + mamuHasTag(signItem, "astral_dice:signs")
            + ":stand_tag=" + mamuHasTag(signItem, "curios:stand")
            + ":bite_excl=" + mamuHasTag(biteItem, "astral_dice:is_exclusive")
            + ":roar_excl=" + mamuHasTag(roarItem, "astral_dice:is_exclusive")
            + ":bite_combat=" + mamuHasTag(biteItem, "astral_dice:combat_cards")
            + ":roar_combat=" + mamuHasTag(roarItem, "astral_dice:combat_cards");
    } catch (e1) { tagTxt = ":tag_err=" + domExText(e1); tagErr = "1"; }
    send(ctx, "AP_" + tag + "_REG_TAG" + tagTxt + (tagErr === "" ? "" : ":tag_ex=1"));

    var typeTxt = "", typeErr = "";
    try {
        var biteByType = NardisCardRegistryClass.typeToItem(MAMU_BITE_TYPE);
        var roarByType = NardisCardRegistryClass.typeToItem(MAMU_ROAR_TYPE);
        typeTxt = ":bite_by_type=" + itemIdOf(biteByType) + ":roar_by_type=" + itemIdOf(roarByType)
            + ":bite_reg_cost=" + NardisCardRegistryClass.cost(MAMU_BITE_TYPE, p)
            + ":roar_reg_cost=" + NardisCardRegistryClass.cost(MAMU_ROAR_TYPE, p)
            + ":bite_defense=" + zhaoBool(function () { return NardisCardRegistryClass.isDefense(MAMU_BITE_TYPE); })
            + ":roar_defense=" + zhaoBool(function () { return NardisCardRegistryClass.isDefense(MAMU_ROAR_TYPE); });
    } catch (e2) { typeTxt = ":type_err=" + domExText(e2); typeErr = "1"; }
    send(ctx, "AP_" + tag + "_REG_TYPE" + typeTxt + (typeErr === "" ? "" : ":type_ex=1"));
    return 1;
}

/** 只读全量快照(缺省自身;给名字则读 bot/真实玩家;找不到 ⇒ `found=0`,不报 ERR) */
function doMamuReadCmd(ctx, tag, phase, nameText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    if (("" + nameText) === "" || ("" + nameText) === "-") return mamuRead(ctx, tag, p, phase);
    var t = teruFindPlayer(ctx, nameText);
    if (t == null) { send(ctx, "AP_" + tag + "_" + phase + ":found=0:who=" + nameText + ":online=" + zhaoOnlineNames(ctx)); return 1; }
    send(ctx, "AP_" + tag + "_" + phase + "_P:" + teruPlayerRead(ctx, t, "" + nameText));
    return mamuRead(ctx, tag, t, phase);
}

/**
 * 觉醒层数脚手架/计数负控。
 *   `set` ⇒ `setAwakening(p, n)`(**产品入口直写**;不钳制非负、不触发形态转换);
 *   `add` ⇒ `onCardGivenToOther(p, p)`(自己给自己:**不计层**,是「自己给自己 +0」的正控)。
 */
function doMamuAwake(ctx, tag, modeText, nText, nameText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = mamuResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var mode = "" + modeText;
    var n = teruInt(nText, 0);
    var before = mamuAwaken(t);
    var how = "none", err = "";
    try {
        if (mode === "add") { MamuSignItemClass.onCardGivenToOther(t, t); how = "self_self"; }
        else { MamuSignItemClass.setAwakening(t, n); how = "set"; }
    } catch (e1) { err = domExText(e1); how = "ex"; }
    var after = mamuAwaken(t);
    send(ctx, "AP_" + tag + "_AWAKE:mode=" + mode + ":want=" + n + ":who=" + ((t === p) ? "self" : ("" + nameText))
        + ":how=" + how
        + ":awk=" + before + ">" + after
        + ":delta=" + ((before < 0 || after < 0) ? -999 : (after - before))
        + ":delta_ok=" + ((mode === "add") ? (((before >= 0) && (after === before)) ? 1 : 0) : 1)
        + (err === "" ? "" : ":err=" + err));
    return mamuRead(ctx, tag, t, "AW");
}

/**
 * **一次发牌事件**内给一名受益人发牌(`giveExclusiveCard` → 唯一漏斗 → `onCardGivenToOther`)。
 *
 * <p>`count` 恒为 1:产品的去重单位是**受益人**(不是张数),故「同一人 2 张只 +1」由
 * 「同一事件内对同一受益人调用两次」表达 —— 见 {@link doMamuBatch} 的 `twice` 档。
 *
 * 读数:`AP_<tag>_GIVE`(含提前 `hasAnyCard` 值 ⇒ 覆盖 D1 判据)。
 */
function doMamuGive(ctx, tag, giverText, receiverText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var g = mamuResolve(ctx, tag, p, giverText, "no_giver");
    if (g == null) return 1;
    var r = mamuResolve(ctx, tag, p, receiverText, "no_receiver");
    if (r == null) return 1;
    var awkBefore = mamuAwaken(g);
    var anyBefore = mamuHasAnyCard(r);
    var err = "";
    try { MamuSignItemClass.giveExclusiveCard(g, r, false, 1); } catch (e1) { err = domExText(e1); }
    send(ctx, "AP_" + tag + "_MU_GIVE:giver=" + ((g === p) ? "self" : ("" + giverText))
        + ":receiver=" + ((r === p) ? "self" : ("" + receiverText))
        + ":giver_eq=" + mamuSignEquipped(g)
        + ":giver_awk=" + awkBefore + ">" + mamuAwaken(g)
        + ":giver_delta=" + ((awkBefore < 0) ? -999 : (mamuAwaken(g) - awkBefore))
        + ":recv_any_before=" + anyBefore + ":recv_any_after=" + mamuHasAnyCard(r)
        + ":recv_bite=" + mamuInvCount(r, MAMU_BITE_ID)
        + (err === "" ? "" : ":err=" + err));
    return 1;
}

/**
 * 批次语义的**完整取证**(全部压在一次命令执行内 ⇒ 同一 gameTime):
 *   `cap`   ⇒ `self`(佩戴者)**不是**受益人(自身自身不计层),只对 Bot1 的价值在读数里;
 *             真值由 `three` 档给出。
 *   `three` ⇒ 对 Bot1/Bot2/Bot3 各一次 ⇒ 期望 `+3`(单次封顶 3 层的**上界命中**);
 *   `four`  ⇒ 对 Bot1/Bot2/Bot3/Bot4 各一次 ⇒ 期望**仍 +3**(封顶生效,第 4 人被挡下);
 *   `twice` ⇒ 对 Bot1 连续两次 ⇒ 期望 **+1**(按受益人去重)+ `inv_bite` 增 2 张(张数真的发了 2);
 *   `other` ⇒ Bot1 给 Bot2(发牌者 = 另一名佩戴立牌者)
 *   `nosign`⇒ Bot1(无立牌)给 Bot2 ⇒ 期望 **+0**(非佩戴者发牌不计)。
 *
 * 读数:`AP_<tag>_BATCH`(逐受益人 delta 串 + 自身 df/latch 的后续影响)。
 */
function doMamuBatch(ctx, tag, modeText, n1, n2, n3) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var mode = "" + modeText;
    var err = "";
    var sent = "";
    var selfBefore = mamuAwaken(p);
    var selfAny = mamuHasAnyCard(p);

    function hit(target, label) {
        var b0 = mamuAwaken(target);
        try { MamuSignItemClass.onCardGivenToOther(p, target); }
        catch (e1) { err = err + "|" + label + ":" + domExText(e1); }
        var d = (b0 < 0) ? -999 : (mamuAwaken(target) - b0);
        sent = (sent === "") ? (label + ":" + b0 + ">" + mamuAwaken(target) + ":" + d)
            : (sent + "," + label + ":" + b0 + ">" + mamuAwaken(target) + ":" + d);
        return target;
    }
    function giveOne(target, label, times) {
        var b0 = mamuAwaken(p);
        var cards0 = mamuInvCount(target, MAMU_BITE_ID);
        for (var i = 0; i < times; i++) {
            try { MamuSignItemClass.giveExclusiveCard(p, target, false, 1); }
            catch (e2) { err = err + "|give:" + label + ":" + domExText(e2); }
        }
        var d = (b0 < 0) ? -999 : (mamuAwaken(p) - b0);
        sent = (sent === "") ? (label + ":" + b0 + ">" + mamuAwaken(p) + ":" + d)
            : (sent + "," + label + ":" + b0 + ">" + mamuAwaken(p) + ":" + d);
        return cards0;
    }
    var cardsBefore = -1, cardsAfter = -1;
    var t1 = null, t2 = null, t3 = null, t4 = null;
    if (("" + n1) !== "-" && ("" + n1) !== "") {
        t1 = mamuResolve(ctx, tag, p, n1, "no_bot1");
        if (t1 == null) return 1;
    }
    if (mode === "three" || mode === "four" || mode === "other" || mode === "nosign") {
        t2 = mamuResolve(ctx, tag, p, n2, "no_bot2");
        if (t2 == null) return 1;
    }
    if (mode === "three" || mode === "four") {
        t3 = mamuResolve(ctx, tag, p, n3, "no_bot3");
        if (t3 == null) return 1;
    }
    if (mode === "four") {
        var all = p.level.getServer().getPlayerList().getPlayers();
        for (var i = 0; i < all.size(); i++) {
            var q = all.get(i);
            var qn = "";
            try { qn = "" + q.getName().getString(); } catch (e9) { qn = ""; }
            if (qn !== "" && qn !== ("" + n1) && qn !== ("" + n2) && qn !== ("" + n3)) {
                var isSelf = false;
                try { isSelf = (q === p); } catch (e9b) { isSelf = false; }
                if (!isSelf) { t4 = q; break; }
            }
        }
    }
    if (mode === "cap") {
        if (t1 != null) hit(t1, "b1");
    } else if (mode === "three") {
        hit(t1, "b1"); hit(t2, "b2"); hit(t3, "b3");
    } else if (mode === "four") {
        hit(t1, "b1"); hit(t2, "b2"); hit(t3, "b3");
        if (t4 == null) { err = err + "|four:no_bot4"; }
        else { hit(t4, "b4"); }
    } else if (mode === "twice") {
        if (t1 != null) {
            cardsBefore = mamuInvCount(t1, MAMU_BITE_ID);
            var r1 = giveOne(t1, "g1", 1);
            var r2 = giveOne(t1, "g2", 1);
            cardsAfter = mamuInvCount(t1, MAMU_BITE_ID);
            if (r1 < 0 || r2 < 0) err = err + "|twice:no_before";
        }
    } else if (mode === "other") {
        if (t1 != null) {
            var b0 = mamuAwaken(t1);
            try { MamuSignItemClass.giveExclusiveCard(t1, t2, false, 1); }
            catch (e3) { err = err + "|other:" + domExText(e3); }
            sent = "b1:" + b0 + ">" + mamuAwaken(t1) + ":" + ((b0 < 0) ? -999 : (mamuAwaken(t1) - b0));
        }
    } else if (mode === "nosign") {
        if (t1 != null) {
            var s0 = mamuAwaken(t1);
            try { MamuSignItemClass.giveExclusiveCard(t1, t2, false, 1); }
            catch (e4) { err = err + "|nosign:" + domExText(e4); }
            sent = "nosign_b1:" + s0 + ">" + mamuAwaken(t1) + ":" + ((s0 < 0) ? -999 : (mamuAwaken(t1) - s0));
        }
    } else {
        err = err + "|unknown_mode:" + mode;
    }
    send(ctx, "AP_" + tag + "_BATCH:mode=" + mode
        + ":self=" + selfBefore + ">" + mamuAwaken(p)
        + ":self_delta=" + ((selfBefore < 0) ? -999 : (mamuAwaken(p) - selfBefore))
        + ":self_any=" + selfAny
        + ":byself=[" + sent + "]"
        + ":bot1_inv_bite=" + cardsBefore + ">" + cardsAfter
        + ":bot1_inv_delta=" + ((cardsBefore < 0 || cardsAfter < 0) ? -999 : (cardsAfter - cardsBefore))
        + ":online=" + zhaoOnlineNames(ctx)
        + (err === "" ? "" : ":err=" + err));
    return mamuRead(ctx, tag, p, "BATCH");
}

/**
 * 佩戴者主动技**发放面**(§2.4 ①②)+ 目标集合(§3.3)的只读/写读混合取证。
 *
 * <p>⚠️ **第 4 参 `[name]` 不接受字面量 `self`**(2026-09-27 实机踩坑):本参数经 `mamuResolve`
 * → `zhaoResolve`,**只有缺省 / `-` 才是"自身"**;传 `self` 会被当成**玩家名**去查 ⇒
 * `AP_<tag>_ERR:no_target:self`。要测自身请**省略第 4 参**。
 * (第 3 参是本命令的**模式**,`self` / `selfonly` / `active` 等模式名合法 —— 两者别混。)
 *
 * <p>模式:
 *   `count`   ⇒ **只读**:`n_others`(佩戴者以外的在线玩家数)+ 12 格内的人数;
 *   `self`    ⇒ 佩戴者对**自身** `giveExclusiveCard(p, p, dragon, 1)`(自己给自己 ⇒ 觉醒 +0);
 *   `give15`  ⇒ 对**全部在线他人**各 `giveExclusiveCard` 一次(同 tick ⇒ 同一事件,按受益人去重封顶 3)
 *               —— **不经过** `handleUse` ⇒ **测不到 D1**;
 *   `random`  ⇒ 对**全部在线他人**各 `RandomCardHandler.giveCardTo(p, t, ALL)` 1 次
 *               —— 同样**不经过** `handleUse` ⇒ **测不到 D1**;
 *   `random2` ⇒ 同 `random`,但**先清掉目标背包/副手的撕咬+龙之咆哮**再发(观察 `hasAnyCard` 变化);
 *   `active`  ⇒ 【2026-09-27 新增】**真实主动路径**(`performSkillForCurio`)一次 + **逐目标建行** ——
 *               `handleUse` 是 D1 的**唯一**所在(`自身 1 张 + 每个合格目标 1 张 + 原无卡者追加 1 张`);
 *   `selfonly`⇒ 真实主动路径,但**只给自身那一组读数**(不建 per-target 行);单机无目标时也必须成功。
 *
 * <p>读数:`AP_<tag>_WATCH`(集合计数 + 闸门 + 逐目标行)。
 *   `gate`/`fired` = 仅 `active` 档有意义(其余档 `n/a` / `-1`),语义同 `mamucast`。
 *
 * <p>**`rows` 是模式相关的,不要用同一套解析器**:
 *   `give15`  ⇒ `self:<awk0>><awk1>:<delta>|<目标行>…`(佩戴者自己的觉醒 前>后 伪行在最前);
 *   `random`/`random2`/`active` ⇒ 只有 `<目标行>…`;
 *   `self`    ⇒ `self:<awk0>><awk1>:<delta>`;
 *   `selfonly`⇒ `cast:<awk0>><awk1>:inv_bite=<b>><a>:inv_roar=<b>><a>:mfu_wrote=<0/1>`;
 *   `count`   ⇒ 空串。
 * 目标行的字段(固定顺序、只追加):
 *   `<名字>:<df0>><df1>:<awk0>><awk1>:<any0>><any1>:<cards0>><cards1>:dist2=<平方距离>:df_same=<0/1>`
 *   `:has_any_before=<0/1>:tot=<b>><a>:granted_cards=<n>`
 *   其中
 *     `df*`  = `MamushiSignItem.isDragonForm`(需**佩戴立牌**且觉醒 ≥ 8)前/后;
 *     `awk*` = `getAwakening` 前/后(只有**佩戴者**会因"给别人发牌"涨);
 *     `any*` = **产品 D1 判据** `hasAnyCard`(主物品栏 + 副手 + **骰子卡牌栏**)前/后;
 *     `cards*` = **只数背包/副手**里的 `撕咬 + 龙之咆哮` 合计张数(探针口径,**不含**骰子装配项);
 *     `tot*` = 与 `any` **同计数域**的**总张数**(`mamuCardTotal`);
 *     `granted_cards` = 本拍**实际发给该目标**的张数(`tot` 的差);`-999` = 读数失败。
 *   ⇒ ⚠️ `cards` 与 `hasAnyCard`/`tot` 是**两个不同计数域**,D1 只能用 `tot`/`granted_cards` 判
 *      (D1 追加的是**随机牌**,`cards` 看不见它);`has_any_before` 是"发牌前"那一刻的产品取值
 *      (snapshot 于 `collect()`,`random2` 档在**清卡之后**重读)。
 *   ⇒ **D1 的可断言判据**:`has_any_before=0` ⇒ `granted_cards=2`;`has_any_before=1` ⇒ `granted_cards=1`。
 *      且**只在 `active` 档成立**(`handleUse` 是 D1 的唯一所在);`give15`/`random` 档恒为 1。
 *      ⚠️ 边界:目标背包满时产品走 `drop(...)`(掉落),那张不计入 ⇒ 如实读成少 1,不冒充成功。
 */
function doMamuWatch(ctx, tag, modeText, nameText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = mamuResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var mode = "" + modeText;
    var err = "";
    var others = mamuOnlineOthers(p);
    var near12 = mamuNearCount(p, 12.0);
    var nearAll = mamuNearCount(p, -1.0);
    var dragon = mamuDragonFlag(t);
    var rows = "";
    // 仅 `active` 档使用(真实主动路径的闸门归因,语义与 `mamucast` 的同名字段一致)
    var gate = "n/a", fired = -1;

    function snapRow(q, label) {
        var d0 = mamuDragonFlag(q);
        var a0 = mamuAwaken(q);
        var any0 = mamuHasAnyCard(q);
        var cards0 = mamuInvCount(q, MAMU_BITE_ID) + mamuInvCount(q, MAMU_ROAR_ID);
        var tot0 = mamuCardTotal(q);
        var dist = -1;
        try { dist = Math.round(q.distanceToSqr(t) * 100) / 100; } catch (e0) { dist = -1; }
        return { q: q, label: label, d0: d0, a0: a0, any0: any0, cards0: cards0, tot0: tot0, dist: dist };
    }
    function emitRow(row) {
        var any1 = mamuHasAnyCard(row.q);
        var cards1 = mamuInvCount(row.q, MAMU_BITE_ID) + mamuInvCount(row.q, MAMU_ROAR_ID);
        var tot1 = mamuCardTotal(row.q);
        var granted = ((row.tot0 < 0 || tot1 < 0) ? -999 : (tot1 - row.tot0));
        var dd = (row.d0 === row.d0 && mamuDragonFlag(row.q) === row.d0) ? 1 : 0;
        var rowTxt = row.label + ":" + row.d0 + ">" + mamuDragonFlag(row.q)
            + ":" + row.a0 + ">" + mamuAwaken(row.q)
            + ":" + row.any0 + ">" + any1
            + ":" + row.cards0 + ">" + cards1
            + ":dist2=" + row.dist
            + ":df_same=" + dd
            // 【2026-09-27 追加(位置固定在 df_same 之后,既有字段名/顺序一律不动)】
            // `has_any_before` = **D1 的产品判据**(`MamushiSignItem.hasAnyCard`)在**发牌之前**的取值:
            //   计数域 = 主物品栏 + 副手 + **骰子卡牌栏**(按 CardRegistry 还原装配项)。
            // `tot` = 同口径的**总张数**(`mamuCardTotal`):D1 追加的那张是**随机牌**(不是撕咬/咆哮),
            //   故 `cards*` 看不到它 ⇒ 判 D1 只能看 `tot`:`has_any_before=0` 且 `tot` +2 = D1 命中。
            //   空背包但骰子里装着牌 ⇒ `has_any_before=1`、`tot` 只 +1(正确,不是缺陷)。
            + ":has_any_before=" + row.any0
            + ":tot=" + row.tot0 + ">" + tot1
            // `granted_cards` = 本次**实际发给该目标**的张数(= `tot` 的差;`-999` = 读数失败)。
            // 判据(用例可直接断言):`has_any_before=0` ⇒ `granted_cards=2`(1 张 + D1 追加 1 张);
            // `has_any_before=1` ⇒ `granted_cards=1`。
            // ⚠️ 口径边界:计数只覆盖**背包侧 + 骰子装配项**;目标背包满时产品走 `drop(...)`(掉落),
            //    那一张不计入 ⇒ 会读成少 1(如实反映"没进包",不冒充成功)。
            + ":granted_cards=" + granted;
        rows = (rows === "") ? rowTxt : (rows + "|" + rowTxt);
    }
    function collect() {
        var rowsOut = [];
        var all = mamuOnlineList(p);
        for (var i = 0; i < all.size(); i++) {
            var q = all.get(i);
            if (q === t) continue;
            rowsOut.push(snapRow(q, mamuNameOf(q)));
        }
        return rowsOut;
    }
    function drive(modeName) {
        var list = collect();
        for (var i = 0; i < list.length; i++) {
            var row = list[i];
            try {
                if (modeName === "random" || modeName === "random2") {
                    if (modeName === "random2") {
                        mamuClearItem(row.q, MAMU_BITE_ID);
                        mamuClearItem(row.q, MAMU_ROAR_ID);
                        // **重新按产品口径读一次**(不写死 0):清的是背包/副手的撕咬+咆哮,
                        // 骰子卡牌栏里的牌不受影响 ⇒ `hasAnyCard` 仍可能为 1,那正是 D1 的真实前置。
                        row.cards0 = 0;
                        row.any0 = mamuHasAnyCard(row.q);
                        row.tot0 = mamuCardTotal(row.q);
                    }
                    MamuRandomCardHandlerClass.giveCardTo(t, row.q, MamuRandomCardHandlerClass.CardCategory.ALL);
                } else {
                    MamuSignItemClass.giveExclusiveCard(t, row.q, (dragon === 1), 1);
                }
            } catch (e1) { err = err + "|" + row.label + ":" + domExText(e1); }
            emitRow(row);
        }
        return list.length;
    }
    var touched = 0;
    if (mode === "count") {
        touched = 0;
    } else if (mode === "self") {
        var sb = mamuAwaken(t);
        try { MamuSignItemClass.giveExclusiveCard(t, t, (dragon === 1), 1); }
        catch (e2) { err = err + "|self:" + domExText(e2); }
        rows = "self:" + sb + ">" + mamuAwaken(t) + ":" + ((sb < 0) ? -999 : (mamuAwaken(t) - sb));
        touched = 1;
    } else if (mode === "give15") {
        // ⚠️ 这里的 `self:` 伪行是**佩戴者自己**的觉醒 前>后(单次事件按受益人去重、封顶 3),
        //    旧写法把两个值都在 `drive` **之前**读 ⇒ 恒为 `x>x`(读数缺陷,已修)
        var self0 = mamuAwaken(t);
        touched = drive("give");
        var selfRow = "self:" + self0 + ">" + mamuAwaken(t) + ":" + ((self0 < 0) ? -999 : (mamuAwaken(t) - self0));
        rows = (rows === "") ? selfRow : (selfRow + "|" + rows);
    } else if (mode === "random") {
        touched = drive("random");
    } else if (mode === "random2") {
        touched = drive("random2");
    } else if (mode === "active") {
        // 【2026-09-27 新增】**真实主动路径**的收牌观测:`handleUse` 一次调用覆盖
        //   「自身 1 张专属牌 + 每个合格目标 1 张随机牌 + D1 追加」——D1 只在这条路径里存在
        //   (`give15`/`random` 直调发牌函数,**不经过** `handleUse`,永远测不到 D1)。
        // 与 `selfonly` 的差别:`selfonly` 不建 per-target 行、只给自身那一组读数;本档**建行**。
        // `gate`/`fired` 与 `mamucast` 同义(主动可能被 锁定/强制冷却/普通冷却 挡下)。
        var gateNow = nowTick(t) - 0;
        var lk0 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(t); });
        var sg0 = mamuSignEquipped(t);
        var mfuB = mamuForcedUntil(t);
        var cdB = mamuCdEnd(t);
        if (sg0 !== 1) gate = "no_sign";
        else if (lk0 === 1) gate = "locked";
        else if (mfuB > 0 && gateNow < mfuB) gate = "forced_cd";
        else if (cdB > 0 && gateNow < cdB) gate = "normal_cd";
        var selfA0 = mamuAwaken(t);
        var invB0 = mamuInvCount(t, MAMU_BITE_ID) + mamuInvCount(t, MAMU_ROAR_ID);
        var list2 = collect();
        touched = list2.length;
        try { BaseSignItemClass.performSkillForCurio(t); }
        catch (e3b) { err = err + "|active:" + domExText(e3b); }
        fired = ((mamuForcedUntil(t) > 0) && (mamuForcedUntil(t) !== mfuB)) ? 1 : 0;
        rows = "self:" + selfA0 + ">" + mamuAwaken(t)
            + ":inv_total=" + invB0 + ">" + (mamuInvCount(t, MAMU_BITE_ID) + mamuInvCount(t, MAMU_ROAR_ID));
        for (var ai = 0; ai < list2.length; ai++) { emitRow(list2[ai]); }
    } else if (mode === "selfonly") {
        var s0 = mamuAwaken(t);
        var invB0 = mamuInvCount(t, MAMU_BITE_ID);
        var invR0 = mamuInvCount(t, MAMU_ROAR_ID);
        var cd0 = mamuForcedUntil(t);
        try { BaseSignItemClass.performSkillForCurio(t); }
        catch (e3) { err = err + "|cast:" + domExText(e3); }
        rows = "cast:" + s0 + ">" + mamuAwaken(t)
            + ":inv_bite=" + invB0 + ">" + mamuInvCount(t, MAMU_BITE_ID)
            + ":inv_roar=" + invR0 + ">" + mamuInvCount(t, MAMU_ROAR_ID)
            + ":mfu_wrote=" + ((mamuForcedUntil(t) > cd0) ? 1 : 0);
        touched = 1;
    } else {
        err = err + "|unknown_mode:" + mode;
    }
    send(ctx, "AP_" + tag + "_WATCH:mode=" + mode
        + ":who=" + ((t === p) ? "self" : ("" + nameText))
        + ":df_wearer=" + dragon
        + ":online=" + zhaoOnlineNames(ctx)
        + ":n_others=" + others
        + ":n_near12=" + near12 + ":n_near_all=" + nearAll
        + ":touched=" + touched
        + ":gate=" + gate + ":fired=" + fired
        + ":rows=[" + rows + "]"
        + (err === "" ? "" : ":err=" + err));
    return mamuRead(ctx, tag, t, "WATCH");
}

/** 佩戴者以外的在线玩家数 */
function mamuOnlineOthers(p) {
    var n = 0;
    try {
        var all = mamuOnlineList(p);
        for (var i = 0; i < all.size(); i++) { if (all.get(i) !== p) n = n + 1; }
    } catch (e) { return -1; }
    return n;
}

/** 同维度在线玩家列表(读不到给空表,由调用方的 -1 读数暴露) */
function mamuOnlineList(p) {
    try {
        var srv = p.level.getServer();
        if (srv == null) return new ArrayListClass();
        var lvl = p.level;
        var all = srv.getPlayerList().getPlayers();
        var out = new ArrayListClass();
        for (var i = 0; i < all.size(); i++) {
            var q = all.get(i);
            var same = false;
            try { same = (q.level === lvl); } catch (e1) { same = false; }
            if (same) out.add(q);
        }
        return out;
    } catch (e2) { return new ArrayListClass(); }
}

/** 半径 r 内的同维度在线玩家数(r < 0 ⇒ 不限距离) */
function mamuNearCount(p, r) {
    var n = 0;
    try {
        var all = mamuOnlineList(p);
        for (var i = 0; i < all.size(); i++) {
            var q = all.get(i);
            if (r < 0) { n = n + 1; continue; }
            var d2 = q.distanceToSqr(p);
            if (d2 <= r * r) n = n + 1;
        }
    } catch (e) { return -1; }
    return n;
}

/** 玩家名(取不到给 `?`;不改动任何状态) */
function mamuNameOf(q) {
    try { return "" + q.getName().getString(); } catch (e) { return "?"; }
}

/**
 * 真龙形态的**成立条件与收益**。
 *   `mark` ⇒ 只读(直写 8 层后由 `mamuRead` 的幂等保底补效果);
 *   `on`   ⇒ `setAwakening(AWAKEN_MAX)` + `transformToDragon`(幂等转换)+
 *            驱动一次产品 tick 路径的形态刷新(`MamushiDragonEffect.refresh`);
 *   `off`  ⇒ `setAwakening(0)`(形态必须立刻消失:下一个读命令走 `remove`)。
 *
 * 读数:`AP_<tag>_FORM`(awaken/dragon 标志/效果/攻击力 **三个数一起给** ⇒
 * 「+5 是否真的进了攻击力」可由 `ap` 与 `ap_base` 的差值直接断言)。
 */
function doMamuForm(ctx, tag, modeText, nameText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var t = mamuResolve(ctx, tag, p, nameText, "no_player");
    if (t == null) return 1;
    var mode = "" + modeText;
    var apBase = mamuAp(t);
    var err = "";
    if (mode === "on") {
        try {
            MamuSignItemClass.setAwakening(t, MamuSignItemClass.AWAKEN_MAX - 0);
            MamuSignItemClass.transformToDragon(t);
            MamuDragonEffectClass.refresh(t);
        } catch (e1) { err = domExText(e1); }
    } else if (mode === "off") {
        try {
            MamuSignItemClass.setAwakening(t, 0);
            MamuDragonEffectClass.remove(t);
        } catch (e2) { err = domExText(e2); }
    }
    var df = mamuDragonFlag(t);
    var fxD = mamuFx(t, 0);
    var apAfter = mamuAp(t);
    send(ctx, "AP_" + tag + "_FORM:mode=" + mode
        + ":who=" + ((t === p) ? "self" : ("" + nameText))
        + ":sign=" + mamuSignEquipped(t)
        + ":awk=" + mamuAwaken(t)
        + ":df=" + df
        + ":fx_d_on=" + fxD.on + ":fx_d=" + fxD.amp + "/" + fxD.dur
        + ":ap_before=" + apBase + ":ap=" + apAfter
        + ":ap_delta=" + ((apBase < 0 || apAfter < 0) ? -999 : (apAfter - apBase))
        + ":dragon_bonus=" + MamuSignItemClass.DRAGON_FORM_ATTACK_BONUS
        + ":bonus_ok=" + (((mode === "on") && (apBase >= 0) && (apAfter >= 0) && (df === 1)
            && ((apAfter - apBase) === (MamuSignItemClass.DRAGON_FORM_ATTACK_BONUS - 0))) ? 1 : 0)
        + ":off_ok=" + ((mode === "off") ? (((df === 0) && (fxD.on === 0)) ? 1 : 0) : -1)
        + (err === "" ? "" : ":err=" + err));
    return mamuRead(ctx, tag, t, "FORM");
}

/** 攻击力(骰战修饰器链全量;读不到 -1) */
function mamuAp(p) {
    if (TeruDiceCombatModifiersClass == null) return -1;
    return domNum(function () { return TeruDiceCombatModifiersClass.attackPowerOf(p); });
}

/**
 * 牌转换(规格 §2.3 / §7 边界「背包/副手/骰子栏撕咬全部转换、溢出丢弃、绑定保留」)。
 *   `inv [name]`      ⇒ 发给目标 3 张撕咬(背包 2 + 副手 1,均带 `owner_uuid` 绑定),再 `transformToDragon`:
 *                       期望背包侧 3 张全部变龙之咆哮、张数不变、槽位数量不变、绑定保持 `self`。
 *                       ⚠️ 装配过程自报 `placed` / `inv_free` / `place_mode`(`setitem`|`add`|`drop`|`fail`)
 *                       / `off_ok` —— 2026-09-27 实机出现过 `placed=0`(主栏已满 ⇒ 一张都没放进),
 *                       那次 `owner_before` 读的是"没有栈"⇒ 无从判绑定;现在放不下会走 `add`/`drop` 兜底。
 *   `dice <maxCost>`  ⇒ 直写骰子装配栏 3 个 `bite`(`uses=1`、费用 2 各),再 `transformToDragon`:
 *                       期望 `eq_bite=0`;`maxCost=7` 时 `eq_roar=2`(**第 3 个因费用溢出被丢弃**);
 *                       `maxCost=9` 时 `eq_roar=3`;
 *                       `maxCost=4` 时 `eq_roar=1`(**第 1 项 3 ≤ 4 入栏、第 2 项起连续丢弃**;
 *                       证明「丢弃」是逐项费用判定而非无条件清空)。
 *                       ⚠️ 2026-09-28 修正:本条原写「`maxCost=4` ⇒ `eq_bite` 保持 3 且 `eq_roar=0`
 *                       (要么全转要么全不转)」—— 那是**旧口径**,与规格 §2.3 及两线实现都不符
 *                       (1.20.1 侧当时另有「投影后从后往前多丢」的算法缺陷,已按 1.21.1 改写;
 *                       修正后两线同为 3/2/1)。
 *                       ⚠️ `maxCost` 是**第 3 参(arg)**,**不是**目标玩家位;`[name]` 是第 4 参。
 *                       2026-09-27 修复:旧写法把 `arg` 当玩家名解析 ⇒ `mamuconv C2 dice 9` 报
 *                       `AP_C2_ERR:no_player:9`,骰子侧三个验证点全部取证不到。
 *   `arm [张数]`      ⇒ **把 `dragon_roar` 写进骰子卡牌栏**(`appliedStones`)—— 这是打通
 *                       「命中 ⇒ 缓慢 III + 破防」端到端链的**唯一**路径(产品触发条件 =
 *                       `countEquippedType(attacker,"dragon_roar") > 0`)。缺省 1 张;
 *                       `uses` = 产品 `ROAR_USES`、费用 = `CardRegistry.cost("dragon_roar")`。
 *                       读数 `arm_count` 与 `eq_bite`/`eq_roar`/`stones_after`/`cost` 即"已装进栏"的证据。
 *
 * 读数:`AP_<tag>_CONV`(转换前后张数 + 槽位构成 + 装配栏构成 + 费用 + 绑定 + `arm_count`)。
 *  ⚠️ `owner_before`/`owner_after` 的取值是**闭集** `self|other|none|err`;判定"绑定是否保留"请用
 *     `owner_before=self` 且 `owner_after=self`,并同时确认 `owner_api_before/after=ok`
 *     (`err` = 探针两条组件读取路径都不可用,**不是**"无主";无主读作 `none`)。
 */
function doMamuConv(ctx, tag, modeText, argText, nameText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var mode = "" + modeText;
    // ⚠️ **参数位不得混用**(2026-09-27 修):`arg` 是**数值/类型位**(`dice` 档 = `maxCost`、`arm` 档 = 装配张数),
    //    `[name]`(第 4 参)才是**目标玩家位**。旧写法在 `dice` 档执行 `who = arg` 再喂给 `mamuResolve`
    //    ⇒ `mamuconv C2 dice 9` 直接报 `AP_C2_ERR:no_player:9` 并 `return`(骰子侧三个验证点全部取证不到)。
    var arg = (argText == null) ? "" : ("" + argText);
    var who = (nameText == null) ? "" : ("" + nameText);
    var t = p;
    if (who !== "" && who !== "-") {
        t = mamuResolve(ctx, tag, p, who, "no_player");
        if (t == null) return 1;
    }
    var err = "";
    // 【2026-09-27 补】`inv` 档的装配过程必须**自报成败**(旧写法把 `placed=N` 拼进 `err=` 串里,
    // 结果 `placed=0`(= 主栏已满、一张都没放进去)被读成"转换没生效"。现在三项都是**独立字段**:
    // `placed`(成功放进主栏的张数)/`inv_free`(放之前主栏空格数)/`place_mode`(`setitem`|`add`|`drop`|`fail`)。
    var placed = -1, invFree = -1, placeMode = "n/a", offOk = -1, armCount = -1;
    var stonesBefore = "[" + mamuDiceStones(t) + "]";
    var costBefore = mamuDiceCosts(t);
    var invBefore = mamuInvCount(t, MAMU_BITE_ID) + mamuInvCount(t, MAMU_ROAR_ID);
    var biteSlotsBefore = mamuSlotText(t, MAMU_BITE_ID);
    if (mode === "inv") {
        try {
            mamuClearItem(t, MAMU_BITE_ID);
            mamuClearItem(t, MAMU_ROAR_ID);
            var bite = resolveItem(MAMU_BITE_ID);
            if (bite == null) { send(ctx, "AP_" + tag + "_ERR:no_item:" + MAMU_BITE_ID); return 1; }
            var inv = t.getInventory();
            placed = 0;
            invFree = 0;
            for (var i = 0; i < 36; i++) {
                var st = inv.getItem(i);
                if (st == null || st.isEmpty()) invFree = invFree + 1;
            }
            placeMode = "setitem";
            for (var k2 = 0; k2 < 36 && placed < 2; k2++) {
                var st2 = inv.getItem(k2);
                if (st2 != null && st2.isEmpty()) { inv.setItem(k2, new ItemStack(bite, 1)); placed = placed + 1; }
            }
            // 主栏满(实机出现过 `placed=0`)⇒ 退到 `Inventory#add`(会自己找可叠/空位),再退到掉落:
            // 绝不能"静默一张都没放"⇒ 后续 `owner_before=none` 会被误读成绑定丢了。
            if (placed < 2) {
                var need = 2 - placed;
                var added = false;
                try { added = inv.add(new ItemStack(bite, need)); } catch (eAd) { added = false; }
                if (added) { placed = 2; placeMode = "add"; }
                else {
                    placeMode = "drop";
                    try { t.drop(new ItemStack(bite, need), false); placed = 2; } catch (eDr) { placeMode = "fail"; }
                }
            }
            try { t.setItemInHand(ZhaoHandClass.OFF_HAND, new ItemStack(bite, 1)); offOk = 1; }
            catch (eOff) { offOk = 0; err = err + "|off:" + domExText(eOff); }
        } catch (e1) { err = err + "|setup:" + domExText(e1); }
        invBefore = mamuInvCount(t, MAMU_BITE_ID) + mamuInvCount(t, MAMU_ROAR_ID);
        biteSlotsBefore = mamuSlotText(t, MAMU_BITE_ID);
    } else if (mode === "dice") {
        var maxCost = 9;
        if (arg !== "") {
            if (!(/^[0-9]+$/).test(arg)) { send(ctx, "AP_" + tag + "_ERR:no_maxcost:" + arg); return 1; }
            maxCost = teruInt(arg, 9);
        }
        try {
            mamuClearItem(t, MAMU_BITE_ID);
            mamuClearItem(t, MAMU_ROAR_ID);
            var kept = new ArrayListClass();
            for (var k = 0; k < 3; k++) kept.add(new NardisAppliedStoneClass(MAMU_BITE_TYPE, 1, false));
            var biteCost = 2;
            try { biteCost = NardisCardRegistryClass.cost(MAMU_BITE_TYPE, t) - 0; } catch (eC) { biteCost = 2; }
            var enh = new NardisWeaponEnhancementClass(3 * biteCost, maxCost, 0, maxCost, 0, kept);
            var w = domWriteEnh(t, "curio", enh);
            if (w.err !== "") err = err + "|write:" + w.err;
            err = err + "|set_ok_curio=" + w.curio + ":set_ok_hand=" + w.hand;
        } catch (e2) { err = err + "|setup:" + domExText(e2); }
        stonesBefore = "[" + mamuDiceStones(t) + "]";
        costBefore = mamuDiceCosts(t);
    } else if (mode === "arm") {
        // 【2026-09-27 新增】**把 `dragon_roar` 写进骰子卡牌栏**(`appliedStones`)的**唯一**路径。
        // 为什么必须有:产品的「命中 ⇒ 缓慢 III + 破防」触发条件正是
        // `MamushiSignItem.countEquippedType(attacker, "dragon_roar") > 0`(规格 §3.6),
        // 而此前**没有任何探针路径**能把咆哮装进去(`mamubite` 写死 `bite`、
        // `domBuildTempStone` 写死 nardis 类型、`mamuconv dice` 参数坏了、`cardprep` 只进主手)
        // ⇒ 「命中触发 ⇒ 破防」的端到端链**不可达**。本档即补上这条路径(脚手架写装配项,不做任何产品逻辑)。
        // 参数:`mamuconv <tag> arm [张数] [name]`(缺省 1 张;uses = 产品 `ROAR_USES`,费用 = `CardRegistry` 的咆哮费用)。
        var armN = 1;
        if (arg !== "") {
            if (!(/^[0-9]+$/).test(arg)) { send(ctx, "AP_" + tag + "_ERR:no_arm_count:" + arg); return 1; }
            armN = teruInt(arg, 1);
        }
        var roarUses = 5, roarCost = 3;
        try { roarUses = MamuSignItemClass.ROAR_USES - 0; } catch (eRU) { roarUses = 5; }
        try { roarCost = NardisCardRegistryClass.cost(MAMU_ROAR_TYPE, t) - 0; } catch (eRC) { roarCost = 3; }
        try {
            mamuClearItem(t, MAMU_BITE_ID);
            mamuClearItem(t, MAMU_ROAR_ID);
            var keptR = new ArrayListClass();
            for (var k3 = 0; k3 < armN; k3++) keptR.add(new NardisAppliedStoneClass(MAMU_ROAR_TYPE, roarUses, false));
            var enhR = new NardisWeaponEnhancementClass(armN * roarCost, armN * roarCost, 0, armN * roarCost, 0, keptR);
            var wR = domWriteEnh(t, "curio", enhR);
            if (wR.err !== "") err = err + "|write:" + wR.err;
            err = err + "|set_ok_curio=" + wR.curio + ":set_ok_hand=" + wR.hand;
        } catch (e2b) { err = err + "|setup:" + domExText(e2b); }
        stonesBefore = "[" + mamuDiceStones(t) + "]";
        costBefore = mamuDiceCosts(t);
        armCount = armN;
    } else {
        err = err + "|unknown_mode:" + mode;
    }
    var ownerBefore = "none", ownerApiB = "ok";
    try {
        var stB = mamuInvStack(t, MAMU_BITE_ID);
        ownerBefore = mamuOwnerTag(stB, t);
        if (stB != null) { var rB = mamuOwnerUuid(stB); if (rB.api !== "") ownerApiB = rB.api; }
    } catch (e3) { ownerBefore = "err"; ownerApiB = "|probe:" + domExText(e3); }
    try { MamuSignItemClass.transformToDragon(t); } catch (e4) { err = err + "|conv:" + domExText(e4); }
    var ownerAfter = "none", ownerApiA = "ok";
    try {
        var stA = mamuInvStack(t, MAMU_ROAR_ID);
        ownerAfter = mamuOwnerTag(stA, t);
        if (stA != null) { var rA = mamuOwnerUuid(stA); if (rA.api !== "") ownerApiA = rA.api; }
    } catch (e5) { ownerAfter = "err"; ownerApiA = "|probe:" + domExText(e5); }
    var invAfter = mamuInvCount(t, MAMU_BITE_ID) + mamuInvCount(t, MAMU_ROAR_ID);
    send(ctx, "AP_" + tag + "_CONV:mode=" + mode + ":who=" + ((t === p) ? "self" : who)
        + ":inv_total=" + invBefore + ">" + invAfter + ":inv_same=" + ((invBefore === invAfter) ? 1 : 0)
        + ":inv_bite_slots_before=[" + biteSlotsBefore + "]"
        + ":inv_bite_slots_after=[" + mamuSlotText(t, MAMU_BITE_ID) + "]"
        + ":stones_before=" + stonesBefore + ":stones_after=[" + mamuDiceStones(t) + "]"
        + ":cost=" + costBefore + ">" + mamuDiceCosts(t)
        + ":placed=" + placed + ":inv_free=" + invFree + ":place_mode=" + placeMode + ":off_ok=" + offOk
        + ":arm_count=" + armCount
        + ":eq_bite=" + mamuEquippedCount(t, MAMU_BITE_TYPE) + ":eq_roar=" + mamuEquippedCount(t, MAMU_ROAR_TYPE)
        + ":owner_before=" + ownerBefore + ":owner_after=" + ownerAfter
        + ":owner_api_before=" + ((ownerApiB === "") ? "ok" : ownerApiB)
        + ":owner_api_after=" + ((ownerApiA === "") ? "ok" : ownerApiA)
        + ":df=" + mamuDragonFlag(t)
        + ":fx_d_on=" + mamuFx(t, 0).on
        + (err === "" ? "" : ":err=" + err));
    return mamuRead(ctx, tag, t, "CONV");
}

/** 主物品栏(0..35)+ 副手里指定物品的 `槽号:张数` 串(无 = `-`) */
function mamuSlotText(p, itemId) {
    var out = "";
    try {
        var inv = p.getInventory();
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (st == null || st.isEmpty() || itemIdOf(st) !== itemId) continue;
            out = (out === "") ? (i + ":" + st.getCount()) : (out + "|" + i + ":" + st.getCount());
        }
        var off = p.getOffhandItem();
        if (off != null && !off.isEmpty() && itemIdOf(off) === itemId) {
            out = (out === "") ? ("off:" + off.getCount()) : (out + "|off:" + off.getCount());
        }
    } catch (e) { return "?"; }
    return (out === "") ? "-" : out;
}

/** 主物品栏(0..35)里第一个指定 id 的栈(找不到给 null) */
function mamuInvStack(p, itemId) {
    try {
        var inv = p.getInventory();
        for (var i = 0; i < 36; i++) {
            var st = inv.getItem(i);
            if (st != null && !st.isEmpty() && itemIdOf(st) === itemId) return st;
        }
        var off = p.getOffhandItem();
        if (off != null && !off.isEmpty() && itemIdOf(off) === itemId) return off;
    } catch (e) { return null; }
    return null;
}

/**
 * **真实主动路径**(`BaseSignItem.performSkillForCurio`;与客户端按键的服务端同一入口)。
 * 本命令**不**做任何 per-sign 前置(由 `mamuprep` / `mamuform` 备好)。
 *
 * <p>参数位:`mamucast <tag>` = 自身 / `mamucast <tag> self` = 自身(**此处的 `self` 是"模式"位,
 * 合法**)/ `mamucast <tag> noop <name>` = 目标为 `<name>`。
 *
 * 读数:`AP_<tag>_MU_CAST`
 *   `gate=` 四道前置闸门的一行归因 —— `open`(放行)/`no_sign`(立牌不在 curios `stand` 槽)/
 *   `locked`(锁定态生效中)/`forced_cd`(**强制冷却窗口内**)/`normal_cd`(普通冷却中);
 *   伴随 `sign=` / `locked_before=` / `cd_before=` + `cd_before` 是否生效(0/1);
 *   `fired=` = 本次**真的执行了**主动(判据 = `mfu` 被写成新的正数;产品在 `handleUse` 末尾落笔);
 *   `bite_slots=` / `roar_slots=` = **主物品栏(0..35)+ 副手**里该物品的 `槽号:张数`(`|` 连接;
 *   无 = `-`;`off:张数` = 副手)**前>后**。
 *   ⚠️ 该字段**不含骰子卡牌栏** —— 主动自身发的那张若进了骰子卡牌栏(装配项),这里恒为 `->-`;
 *      要看"牌到底进哪了"请同时读 `stones_before/stones_after`、`card_total=` 与后续 `_MCARD`/`_MDICE`。
 *   ⚠️ `fired=0` 时后面所有字段都是"没动过"的原值,不要当成"主动没发牌"的缺陷证据 ——
 *      先看 `gate=`。
 * 非自身目标由目标侧的 `AP_<tag>_WATCH` 或收牌计数给出。
 */
function doMamuCast(ctx, tag, nameTextOrNoop, maybeName) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    // 参数位:`mamucast <tag>` / `mamucast <tag> self` / `mamucast <tag> noop <name>`
    var a1 = ("" + (nameTextOrNoop == null ? "" : nameTextOrNoop));
    var targetName = a1;
    if (a1 === "" || a1 === "-" || a1 === "self") targetName = "";
    else if (a1 === "noop") targetName = ("" + (maybeName == null ? "" : maybeName));
    var t = mamuResolve(ctx, tag, p, targetName, "no_player");
    if (t == null) return 1;
    var biteSlots0 = mamuSlotText(t, MAMU_BITE_ID);
    var roarSlots0 = mamuSlotText(t, MAMU_ROAR_ID);
    var stones0 = "[" + mamuDiceStones(t) + "]";
    var awk0 = mamuAwaken(t);
    var until0 = mamuForcedUntil(t);
    var cd0 = mamuCdEnd(t);
    var maxc0 = mamuMaxCd(t);
    var df0 = mamuDragonFlag(t);
    // 【2026-09-27 补】主动路径的**四道前置闸门**(与 `BaseSignItem#performSkill` 的判定顺序逐条对应):
    //   ⓪ 立牌必须在 curios `stand` 槽(不在 ⇒ 第 81 行就 `return`);① 锁定态;② 强制冷却;③ 普通冷却。
    //   ⚠️ 没有这几个字段时,「什么都没变」的读数**无法归因** —— 2026-09-27 实机就出现过
    //   `mamucast … self` 的 `bite_slots=->-` / `roar_slots=1:1>1:1`(全无变化),看着像"主动没发牌",
    //   实际是被**上一步 `mamucd … cast` 写下的强制冷却**挡下(`mfu_remain` 同拍为 1200)。
    //   故本命令现在**逐闸门自报**:`sign` / `locked_before` / `cd_before` / `gate` / `fired`。
    var entryNow = nowTick(t) - 0;
    var sign0 = mamuSignEquipped(t);
    var locked0 = domBool(function () { return BaseSignItemClass.isSignActiveLocked(t); });
    var cardTotal0 = mamuCardTotal(t);
    var gate = "open";
    if (sign0 !== 1) gate = "no_sign";
    else if (locked0 === 1) gate = "locked";
    else if (until0 > 0 && entryNow < until0) gate = "forced_cd";
    else if (cd0 > 0 && entryNow < cd0) gate = "normal_cd";
    var err = "";
    try { BaseSignItemClass.performSkillForCurio(t); } catch (e1) { err = domExText(e1); }
    var until1 = mamuForcedUntil(t);
    var fired = ((until1 > 0) && (until1 !== until0)) ? 1 : 0;
    send(ctx, "AP_" + tag + "_MU_CAST:who=" + ((t === p) ? "self" : ("" + targetName))
        + ":df_before=" + df0
        + ":sign=" + sign0 + ":locked_before=" + locked0
        + ":cd_before=" + cd0 + ":" + ((cd0 > 0 && entryNow < cd0) ? 1 : 0)
        + ":gate=" + gate + ":fired=" + fired
        + ":card_total=" + cardTotal0 + ">" + mamuCardTotal(t)
        + ":awk=" + awk0 + ">" + mamuAwaken(t)
        + ":bite_slots=" + biteSlots0 + ">" + mamuSlotText(t, MAMU_BITE_ID)
        + ":roar_slots=" + roarSlots0 + ">" + mamuSlotText(t, MAMU_ROAR_ID)
        + ":stones_before=" + stones0 + ":stones_after=[" + mamuDiceStones(t) + "]"
        + ":mfu=" + until0 + ">" + until1
        + ":mfu_wrote=" + ((until1 > 0 && until1 !== until0) ? 1 : 0)
        + ":cd=" + cd0 + ">" + mamuCdEnd(t)
        + ":maxc=" + maxc0 + ">" + mamuMaxCd(t)
        + ":lock_after=" + domBool(function () { return BaseSignItemClass.isSignActiveLocked(t); })
        + (err === "" ? "" : ":err=" + err));
    return mamuRead(ctx, tag, t, "CAST");
}

/**
 * 强制冷却闸门(规格 §3.4)。`mode`:
 *   `read`    ⇒ 只读三件套(不写任何状态);
 *   `fresh`   ⇒ **负控构造**:清两个冷却键 ⇒ `forced_active` 必须为 0
 *               (证明后面的「有冷却」不是残留造成的);
 *   `past`    ⇒ 把 `mfu` 写到 `now − 1`(**正数**的过去刻:0 会被当成「无强制」而早退,
 *               与 nardis `signexpiregrace` 同一个坑)⇒ `forced_active` 必须为 0;
 *   `present` ⇒ 把 `mfu` 写到 `now + ACTIVE_FORCED_COOLDOWN_TICKS` ⇒ `mfu_remain == 1200`、
 *               `mfu_is_1200=1` **且** `mfu_remain_is_cfg=1`(后者按**产品常量**判,推荐断言项);
 *   `busy`    ⇒ 把 `cd_end` 写到 `now + 1200`、`maxc` 写到 1200、`mfu` 清 0 ⇒ 普通冷却生效;
 *   `cast`    ⇒ 只写 `mfu = now + 1200`,**再走真实主动路径** ⇒ 必须被强制冷却挡下
 *               (读数 `cast_rejected` 用「自身什么都没变」判:牌种/张数/觉醒前后相同);
 *   `recast`  ⇒ 与 `cast` 同法但**先清 `cd_end`/`maxc`** ⇒ 证明挡下它的是强制闸门而非普通冷却。
 *
 * 读数:`AP_<tag>_CD`(`mfu_delta` / `mfu_is_1200` / `maxc_is_1200` / `cd_eq_maxc` /
 * `forced_active` / `cast_rejected` / `cd_unchanged`)。
 */
function doMamuCd(ctx, tag, modeText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var mode = "" + modeText;
    var err = "";
    var now = nowTick(p) - 0;
    // `entryMfu` = 进入本命令时、**任何探针写入之前**的 `mfu`(mamuCdRead 的 `mfu_entry`/`mfu_shift` 用它)
    var entryMfu = mamuForcedUntil(p);
    var base = entryMfu;
    var invBefore = mamuInvCount(p, MAMU_BITE_ID) + mamuInvCount(p, MAMU_ROAR_ID);
    var awkBefore = mamuAwaken(p);
    var maxcBefore = mamuMaxCd(p);
    var cdBefore = mamuCdEnd(p);
    var castRejected = -1;
    if (mode === "fresh" || mode === "past" || mode === "present" || mode === "busy" || mode === "cast" || mode === "recast") {
        try { ModAttachments.setMamushiForcedCooldownUntil(p, 0); } catch (e1) { err = err + "|mfu0:" + domExText(e1); }
        try { ModAttachments.setSignActiveCooldownEnd(p, 0); } catch (e2) { err = err + "|cd0:" + domExText(e2); }
        try { ModAttachments.setSignActiveMaxCooldown(p, 0); } catch (e3) { err = err + "|maxc0:" + domExText(e3); }
    }
    if (mode === "past") {
        var past = now - 1;
        if (past < 1) past = 1;
        try { ModAttachments.setMamushiForcedCooldownUntil(p, past); } catch (e4) { err = err + "|past:" + domExText(e4); }
        base = past;
    } else if (mode === "present" || mode === "cast") {
        var want = now + (MamuSignItemClass.ACTIVE_FORCED_COOLDOWN_TICKS - 0);
        try { ModAttachments.setMamushiForcedCooldownUntil(p, want); } catch (e5) { err = err + "|present:" + domExText(e5); }
        base = want;
    } else if (mode === "recast") {
        var want2 = now + (MamuSignItemClass.ACTIVE_FORCED_COOLDOWN_TICKS - 0);
        try { ModAttachments.setMamushiForcedCooldownUntil(p, want2); } catch (e6) { err = err + "|recast:" + domExText(e6); }
        base = want2;
    } else if (mode === "busy") {
        try {
            ModAttachments.setSignActiveCooldownEnd(p, now + 1200);
            ModAttachments.setSignActiveMaxCooldown(p, 1200);
        } catch (e7) { err = err + "|busy:" + domExText(e7); }
    }
    if (mode === "cast" || mode === "recast") {
        try { BaseSignItemClass.performSkillForCurio(p); } catch (e8) { err = err + "|cast:" + domExText(e8); }
        var invAfter = mamuInvCount(p, MAMU_BITE_ID) + mamuInvCount(p, MAMU_ROAR_ID);
        var awkAfter = mamuAwaken(p);
        castRejected = ((invBefore === invAfter) && (awkBefore === awkAfter)) ? 1 : 0;
    }
    send(ctx, "AP_" + tag + "_MU_CD:mode=" + mode + ":" + mamuCdRead(p, base, entryMfu)
        + ":cast_rejected=" + castRejected
        + ":mfu_unchanged=" + ((mamuForcedUntil(p) === base) ? 1 : 0)
        + ":cd_unchanged=" + ((mamuCdEnd(p) === cdBefore) ? 1 : 0)
        + ":maxc_unchanged=" + ((mamuMaxCd(p) === maxcBefore) ? 1 : 0)
        + ":inv_total=" + invBefore + ">" + (mamuInvCount(p, MAMU_BITE_ID) + mamuInvCount(p, MAMU_ROAR_ID))
        + ":awk=" + awkBefore + ">" + mamuAwaken(p)
        + (err === "" ? "" : ":err=" + err));
    return 1;
}

/**
 * 电流核心筹码(`CurrentCoreChipItem.tryFinishCooldown`)的强制冷却拒绝(规格 §3.4)。
 *   `ready`    ⇒ 装筹码 + 充能 6 + `mfu = now + 1200` + `cd_end = now + 600`
 *                ⇒ `rv` 必须 = `FINISH_NOT_ENOUGH(-1)`、充能**不扣**、`cd_end` **不变**
 *                (证明不是"没筹码/没充能"造成的假拒);
 *   `nocharge` ⇒ 与 `ready` 同构但**清掉强制冷却**、充能 0 ⇒ `rv` 必须 = NONE 或 NOT_ENOUGH
 *                且**不使用**强制冷却分支(负控:同一命令路径在无强制冷却时行为不同)。
 *   `charge`   ⇒ 装筹码 + 充能 6 + 普通冷却 + `mfu = 0` ⇒ 电流核心**正常完成冷却**
 *                (`rv = FINISH_DONE(1)`、充能确实减少)⇒ 证明「拒绝」专属于强制冷却窗口。
 *
 * <p>**前置自足(2026-09-27 修正)**:命令内部先把骰子槽换成 `astral_dice:golden_dice`
 * (0★ 即 1 个筹码栏;`mamuprep` 默认装的 `astral_dice:dice` 是 0★ 0 槽)⇒ **不需要**用例侧
 * 额外准备筹码栏。⚠️ 副作用:本命令会**改掉骰子槽**(`dice_before`/`dice_after` 留痕),
 * 若后续步骤依赖原来的骰子,请在之后重跑 `mamuprep`。
 *
 * 读数:`AP_<tag>_CORE`(rv / 充能 前>后 / cd_end 前>后 / 各标志 / 筹码栏前置证据)。
 */
function doMamuCore(ctx, tag, modeText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var mode = "" + modeText;
    var err = "";
    var now = nowTick(p) - 0;
    try { clearCurioSlots(p, "chip"); } catch (e0) { err = err + "|chip0:" + domExText(e0); }
    // 【2026-09-27 修正(前置自足)】筹码栏格数由**骰子品阶公式**给出(`ModItems.java:215-243`):
    //   `dice` = `s -> s`      ⇒ 0★ 时 **0 槽**(`mamuprep` 装的正是它)
    //   `golden_dice`          ⇒ 0★ 时 **1 槽**(= `CHIP_SLOT_MIN`)
    // 旧写法直接 `ensureChipSlot`,在 `mamuprep` 之后必然拿不到筹码栏 ⇒ 实测报
    // `AP_<tag>_ERR:chip_slot_insufficient:0/1`(三条模式全废)。现在**本命令自己把骰子换成
    // `astral_dice:golden_dice`**(读数 `dice_before`/`dice_for_chip`/`dice_after` 三处留痕),
    // 不再依赖用例侧的前置;`ensureChipSlot` 仍保留为兜底(骰子已被换掉时它是空操作)。
    var diceBefore = diceSlotItemId(p);
    var diceSetErr = "-";
    var golden = resolveItem("astral_dice:golden_dice");
    if (golden == null) { send(ctx, "AP_" + tag + "_ERR:no_item:astral_dice:golden_dice"); return 1; }
    try {
        var dErr = putInSlot(p, "dice", new ItemStack(golden), 0);
        diceSetErr = (dErr == null) ? "ok" : ("" + dErr);
    } catch (eD) { diceSetErr = "ex:" + domExText(eD); }
    var chipErr = ensureChipSlot(p, CHIP_SLOT_MIN);
    if (chipErr != null) { send(ctx, "AP_" + tag + "_ERR:" + chipErr); return 1; }
    var chip = resolveItem("astral_dice:current_core_chip");
    if (chip == null) { send(ctx, "AP_" + tag + "_ERR:no_item:current_core_chip"); return 1; }
    var putErr = putInSlot(p, "chip", new ItemStack(chip), 0);
    if (putErr != null) err = err + "|chip:" + putErr;
    try { MamuChargeManagerClass.removeAll(p); } catch (e1) { err = err + "|charge0:" + domExText(e1); }
    if (mode === "nocharge") {
        try { MamuChargeManagerClass.addStacks(p, 0); } catch (e2) { err = err + "|charge:" + domExText(e2); }
        try { ModAttachments.setMamushiForcedCooldownUntil(p, 0); } catch (e3) { err = err + "|mfu:" + domExText(e3); }
        try { ModAttachments.setSignActiveCooldownEnd(p, now + 600); } catch (e4) { err = err + "|cd:" + domExText(e4); }
        try { ModAttachments.setSignActiveMaxCooldown(p, 1200); } catch (e5) { err = err + "|maxc:" + domExText(e5); }
    } else {
        try { MamuChargeManagerClass.addStacks(p, 6); } catch (e6) { err = err + "|charge:" + domExText(e6); }
        try { ModAttachments.setSignActiveCooldownEnd(p, now + 600); } catch (e7) { err = err + "|cd:" + domExText(e7); }
        try { ModAttachments.setSignActiveMaxCooldown(p, 1200); } catch (e8) { err = err + "|maxc:" + domExText(e8); }
        if (mode === "ready") {
            try { ModAttachments.setMamushiForcedCooldownUntil(p, now + 1200); } catch (e9) { err = err + "|mfu:" + domExText(e9); }
        } else {
            try { ModAttachments.setMamushiForcedCooldownUntil(p, 0); } catch (e10) { err = err + "|mfu0:" + domExText(e10); }
        }
    }
    var charge0 = zhaoNum(function () { return MamuChargeManagerClass.getStacks(p); });
    var cd0 = mamuCdEnd(p);
    var mfu0 = mamuForcedUntil(p);
    var equipped = zhaoBool(function () { return MamuCurrentCoreClass.isEquipped(p); });
    var rv = -99;
    try { rv = MamuCurrentCoreClass.tryFinishCooldown(p, mamuCdEnd(p), now) - 0; } catch (e11) { rv = -98; err = err + "|call:" + domExText(e11); }
    var charge1 = zhaoNum(function () { return MamuChargeManagerClass.getStacks(p); });
    var cd1 = mamuCdEnd(p);
    send(ctx, "AP_" + tag + "_CORE:mode=" + mode
        + ":core_eq=" + equipped
        + ":dice_before=" + diceBefore + ":dice_for_chip=golden_dice:dice_set=" + diceSetErr
        + ":dice_after=" + diceSlotItemId(p) + ":chip_slots=" + domNum(function () { return curioHandler(p, "chip").getStacks().getSlots(); })
        + ":rv=" + rv + ":finish_none=" + (MamuCurrentCoreClass.FINISH_NONE - 0)
        + ":finish_done=" + (MamuCurrentCoreClass.FINISH_DONE - 0)
        + ":finish_not_enough=" + (MamuCurrentCoreClass.FINISH_NOT_ENOUGH - 0)
        + ":charge=" + charge0 + ">" + charge1
        + ":charge_kept=" + ((charge0 === charge1) ? 1 : 0)
        + ":cd=" + cd0 + ">" + cd1 + ":cd_kept=" + ((cd0 === cd1) ? 1 : 0)
        + ":cd_finished=" + ((cd1 <= now) ? 1 : 0)
        + ":rejected_ok=" + ((mfu0 > 0 && now < mfu0) ? ((rv === (MamuCurrentCoreClass.FINISH_NOT_ENOUGH - 0)) ? 1 : 0) : -1)
        + ":drained_ok=" + ((mfu0 === 0) ? ((rv === (MamuCurrentCoreClass.FINISH_DONE - 0)) ? 1 : 0) : -1)
        + (err === "" ? "" : ":err=" + err));
    return 1;
}

/**
 * 攻击力数值落地 + 破防对实际减伤的影响(规格 §7 边界「要能读到实际进入伤害的数值」)。
 *
 * 四只同型靶(`minecraft:vindicator`,最大生命 24;护甲由**直写属性实例**给出 ⇒ 无命令延迟):
 *   `t0` 无甲**参照靶**(只吃 1 次攻击 ⇒ 近战裸值 `raw`)、
 *   `t1` 甲 16 / 韧性 0(**应用破防**)、`t3` 甲 16 / 韧性 0(**对照**,不加破防)、
 *   `t2` 无甲**备用靶**(不参与断言,只保证攻击目标彼此不同 —— 同实体连击会撞无敌帧,读数变 0)。
 * 步骤(与既往 `glovebase` 同法,**全部压在一次执行内**):
 *   ① 直写护甲 ⇒ 读 4 靶护甲;
 *   ② `attack(t0)` ⇒ `raw`(裸值客观参照);
 *   ③ `attack(t1)`(**第一击**,双方都还没破防)⇒ `hit_break_first`;此时跑产品条件块
 *      (攻击方骰子栏装备了 `dragon_roar` ⇒ `applyRoarDebuff`)⇒ 读 t1 的护甲与破防修饰量;
 *   ④ 走**内部通道**清掉 t1 的破防,再直调 `applyRoarDebuff(t1)`(可复现、不依赖触发),
 *      并在 t3 上手动应用**同参数**的破防后**立刻清掉** ⇒ t3 全程无破防(对照臂);
 *   ⑤ `attack(t1)`(**第二击**,有破防)与 `attack(t3)`(**第二击**,无破防)⇒ `hit_break` / `hit_plain`;
 *      `diff = hit_plain − hit_break` 即**破防带来的额外实扣**。
 *
 * 读数:
 *   `AP_<tag>_JUMP`  攻击力 前>后 + delta(证明「真龙/撕咬加成进了攻击力」)
 *   `AP_<tag>_JR`    四次攻击的实扣与生效 API
 *   `AP_<tag>_JARM`  护甲与破防修饰量(**真实进入伤害的护甲**)
 *   `AP_<tag>_JGUARD` 真实减伤口径对照(raw / hit_plain / hit_break / diff / min(护甲,20) 口径的期望减伤)
 */
function doMamuJump(ctx, tag) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var err = "";
    var mode = "already_survival";
    try { p.setGameMode(GameTypeClass.SURVIVAL); mode = "forced_survival"; } catch (e0) { /* 忽略 */ }
    var weather = "skip";
    try { p.level.setWeatherParameters(6000, 0, false, false); weather = "clear"; } catch (e1) { weather = "err"; }
    try { p.setHealth(p.getMaxHealth()); } catch (e2) { /* 忽略 */ }
    var t0 = spawnDummy(p, "minecraft:vindicator", 3);
    var t1 = spawnDummy(p, "minecraft:vindicator", 3);
    var t2 = spawnDummy(p, "minecraft:vindicator", 3);
    var t3 = spawnDummy(p, "minecraft:vindicator", 3);
    if (t0 == null || t1 == null || t2 == null || t3 == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed"); return 1; }
    try { placeAt(t0, p.getX() - 1.2, p.getY(), p.getZ() + 3.0); } catch (e3) { /* 忽略 */ }
    try { placeAt(t1, p.getX() - 0.4, p.getY(), p.getZ() + 3.0); } catch (e4) { /* 忽略 */ }
    try { placeAt(t2, p.getX() + 0.4, p.getY(), p.getZ() + 3.0); } catch (e5) { /* 忽略 */ }
    try { placeAt(t3, p.getX() + 1.2, p.getY(), p.getZ() + 3.0); } catch (e6) { /* 忽略 */ }
    var all = [t0, t1, t2, t3];
    for (var i = 0; i < all.length; i++) {
        try { all[i].setNoAi(true); } catch (e7) { /* 忽略 */ }
        try { all[i].setPersistenceRequired(); } catch (e8) { /* 忽略 */ }
        try { all[i].setHealth(all[i].getMaxHealth()); } catch (e9) { /* 忽略 */ }
    }
    var aRef = mamuSetArmor(t0, 0.0, 0.0);
    var aBrk = mamuSetArmor(t1, 16.0, 0.0);
    var aCtl = mamuSetArmor(t2, 0.0, 0.0);
    var aPln = mamuSetArmor(t3, 16.0, 0.0);
    if (aRef !== null || aBrk !== null || aCtl !== null || aPln !== null) {
        err = err + "|armor_set:" + aRef + "/" + aBrk + "/" + aCtl + "/" + aPln;
    }
    var arm0 = mamuBreakArmor(t0);
    var arm1 = mamuBreakArmor(t1);
    var arm3 = mamuBreakArmor(t3);
    var apBefore = mamuAp(p);
    // ② 裸值参照
    var raw = mamuAttack(p, t0);
    // ③ 第一击(双方都还没有破防)
    var firstBreak = mamuAttack(p, t1);
    var arm1Hit = mamuBreakArmor(t1);
    var fxFirst = mamuFx(t1, 1);
    // ④ 构造:清 t1 的破防后直调产品入口(可复现);t3 全程不加破防
    try { ModEffectRemoval.remove(t1, mamuEffectHolder(1)); } catch (e10) { /* 无效果即空操作 */ }
    try { MamuSignItemClass.applyRoarDebuff(t1); } catch (e12) { err = err + "|roar:" + domExText(e12); }
    var arm1After = mamuBreakArmor(t1);
    // ⑤ 第二击对照
    var secondBreak = mamuAttack(p, t1);
    var secondPlain = mamuAttack(p, t3);
    var apAfter = mamuAp(p);
    // 「期望减伤」按 §7 的 min(护甲,20) 口径算成**纯数字**(断言层无算术能力)
    var expReduction = -1;
    try { expReduction = Math.round(Math.min(arm1After.base, 20.0) / 25.0 * 10000) / 10000; } catch (e13) { expReduction = -1; }
    var diff = ((secondBreak < 0) || (secondPlain < 0)) ? -999 : (secondPlain - secondBreak);
    send(ctx, "AP_" + tag + "_JUMP:mode=" + mode + ":weather=" + weather
        + ":ap_before=" + apBefore + ":ap=" + apAfter
        + ":ap_delta=" + ((apBefore < 0 || apAfter < 0) ? -999 : (apAfter - apBefore))
        + ":t0=" + mamuEntText(t0) + ":t1=" + mamuEntText(t1) + ":t2=" + mamuEntText(t2) + ":t3=" + mamuEntText(t3));
    send(ctx, "AP_" + tag + "_JR:raw=" + raw.dmg + ":" + raw.api
        + ":hit_break_first=" + firstBreak.dmg + ":" + firstBreak.api
        + ":hit_break=" + secondBreak.dmg + ":" + secondBreak.api
        + ":hit_plain=" + secondPlain.dmg + ":" + secondPlain.api);
    send(ctx, "AP_" + tag + "_JARM:armor_ref=" + arm0.armor + ":armor_break=" + arm1.armor + ":armor_plain=" + arm3.armor
        + ":break_first=" + arm1Hit.amt + "/" + arm1Hit.mods
        + ":break_after=" + arm1After.amt + "/" + arm1After.mods
        + ":break_base=" + arm1After.base
        + ":break_fx=" + fxFirst.amp + "/" + fxFirst.dur
        + ":break_mod_kind=" + arm1After.op
        + ":break_mod_id=[" + arm1After.id + "]"
        + ":roar_matched=" + mamuEquippedCount(p, MAMU_ROAR_TYPE));
    send(ctx, "AP_" + tag + "_JGUARD:raw=" + raw.dmg
        + ":hit_plain=" + secondPlain.dmg + ":hit_break=" + secondBreak.dmg
        + ":diff=" + diff
        + ":exp_reduction=" + expReduction
        + ":break_helps=" + ((diff > 0) ? 1 : 0)
        + ":break_amt_ok=" + ((arm1After.amt === -8) ? 1 : 0)
        + ":roar_eq_gt0=" + ((mamuEquippedCount(p, MAMU_ROAR_TYPE) > 0) ? 1 : 0)
        + (err === "" ? "" : ":err=" + err));
    for (var q = 0; q < all.length; q++) { try { all[q].discard(); } catch (e14) { /* 忽略 */ } }
    try { clearCurioSlots(p, "chip"); } catch (e15) { /* 忽略 */ }
    try { p.setGameMode(GameTypeClass.CREATIVE); } catch (e16) { /* 忽略 */ }
    return 1;
}

/** 一次真实近战,返回 `{dmg, api}`(dmg < 0 或 0 = 本次没有有效输出,读数里显式可见) */
function mamuAttack(attacker, victim) {
    try {
        var hp0 = victim.getHealth();
        var hit = meleeHit(attacker, victim);
        var hp1 = victim.getHealth();
        var api = "?";
        try { api = "" + hit.api; } catch (eA) { api = "?"; }
        return { dmg: Math.round((hp0 - hp1) * 100) / 100, api: api };
    } catch (e) { return { dmg: -1, api: "ex:" + domExText(e) }; }
}

/** 直写实体护甲/韧性(返回 null = 成功;不依赖命令的下一 tick 生效) */
function mamuSetArmor(ent, armor, tough) {
    try {
        var Attrs = Java.loadClass("net.minecraft.world.entity.ai.attributes.Attributes");
        var a = ent.getAttribute(Attrs.ARMOR);
        var t = ent.getAttribute(Attrs.ARMOR_TOUGHNESS);
        if (a == null || t == null) return "no_attr_instance";
        a.setBaseValue(armor);
        t.setBaseValue(tough);
        return null;
    } catch (e) { return domExText(e); }
}

/**
 * 龙之咆哮(规格 §2.7)。`mode`:
 *   `read`  ⇒ 目标侧只读(缓慢 III 的 amp/dur + 破防的 修饰量/armor);
 *   `apply` ⇒ **直调产品入口** `applyRoarDebuff(t)`(可复现、不依赖触发条件),
 *             第 3 参是目标名(`read` 档第 3 参同义)。
 *
 * 读数:`AP_<tag>_ROAR`(缓慢 `fx_slow=amp/dur` + `break_amt` / `break_armor` / `break_base` /
 * 命中次数代理 = 破防修饰器个数 `break_mods`)+ `AP_<tag>_ROAR_CARD`(攻击方装备的 `dragon_roar` 张数)。
 */
function doMamuRoar(ctx, tag, modeText, nameText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var mode = "" + modeText;
    var t = mamuResolve(ctx, tag, p, nameText, "no_target");
    if (t == null) return 1;
    var err = "";
    var bd0 = mamuBreakArmor(t);
    var slow0 = mamuSlowFx(t);
    if (mode === "apply") {
        try { MamuSignItemClass.applyRoarDebuff(t); } catch (e1) { err = domExText(e1); }
    }
    var bd1 = mamuBreakArmor(t);
    var slow1 = mamuSlowFx(t);
    send(ctx, "AP_" + tag + "_ROAR:mode=" + mode + ":who=" + ((t === p) ? "self" : ("" + nameText))
        + ":type=" + mamuEntText(t)
        + ":slow_before=" + slow0.amp + "/" + slow0.dur + ":slow_on_before=" + slow0.on
        + ":slow=" + slow1.amp + "/" + slow1.dur + ":slow_on=" + slow1.on
        + ":slow_amp_ok=" + ((slow1.on === 1 && slow1.amp === (MamuSignItemClass.ROAR_SLOW_AMPLIFIER - 0)) ? 1 : 0)
        + ":break_before=" + bd0.amt + "/" + bd0.mods
        + ":break_amt=" + bd1.amt + ":break_mods=" + bd1.mods
        + ":break_armor=" + bd1.armor + ":break_base=" + bd1.base
        + ":break_ok=" + ((bd1.amt === -8) ? 1 : 0)
        + (err === "" ? "" : ":err=" + err));
    send(ctx, "AP_" + tag + "_ROAR_CARD:attacker_eq_roar=" + mamuEquippedCount(p, MAMU_ROAR_TYPE)
        + ":attacker_eq_bite=" + mamuEquippedCount(p, MAMU_BITE_TYPE));
    return 1;
}

/**
 * 缓慢效果的 amp/dur(`MOVEMENT_SLOWDOWN`;缺 = `on 0`)。
 * 用 `Entity#getEffect(Holder)` 直取(`MobEffects.MOVEMENT_SLOWDOWN` 两线都是 Holder,
 * 与本文件既有的 `luluFx(…, MobEffectsClass.MOVEMENT_SLOWDOWN)` 同源),避免再猜描述 id。
 */
function mamuSlowFx(ent) {
    var inst = null;
    try { inst = ent.getEffect(MobEffectsClass.MOVEMENT_SLOWDOWN); } catch (e1) { inst = null; }
    var amp = -1, dur = -1;
    try { if (inst != null) { amp = inst.getAmplifier(); dur = inst.getDuration(); } } catch (e3) { amp = -1; dur = -1; }
    return { amp: amp, dur: dur, on: (inst != null) ? 1 : 0 };
}

/**
 * 撕咬(规格 §2.6)。`mode`:
 *   `clean`  ⇒ 清两张牌 + 清锁存(基线;由 `mamuprep` 顺带完成,此档供单独复位用);
 *   `light`  ⇒ **最小自证**(1 张撕咬、觉醒 0):直调真实挂点入口
 *              `MamushiSignItem.onDiceBlessingTriggered(p)` ⇒ 必须 `awk 0→1`、
 *              `bite_latch` 0→1、`ap` **+1**(= `min(觉醒, 4)`,裁决 2/4)。
 *              随后清掉骰子装配栏里的撕咬(模拟耐久 1 被消耗)并读回 ⇒ `latch` **仍为 1**、
 *              `ap` 仍含加成(**锁存的唯一硬判据**:不锁存则同一击内加成即失效)。
 *   `three`  ⇒ 3 张撕咬 ⇒ 一次触发 `+3` 层(裁决 2:不受"给他人发牌"的 3 层封顶约束),
 *              `ap_delta` 同为 +3(3 ≤ BITE_BONUS_CAP=4);
 *   `dragon` ⇒ 觉醒先置 **7** + 1 张撕咬 + 触发 ⇒ 8 层并**立即**进真龙形态
 *              (`df` 0→1、`fx_d_on` 1);攻击力同时吃到两段 ⇒ `ap_expect = min(8,4) + 5 = 9`
 *              (`ap_cap_delta=4` 只是**撕咬部分**,`ap_df_bonus=5` 是形态跃迁那一拍叠加的,
 *              `ap_cap_ok` 按两段之和判 ⇒ 必须为 1)。
 *
 * 读数:`AP_<tag>_BITE`(awake/latch/ap 的三段读数与该档的 0/1 判定标志;
 * `ap_cap_delta` = 撕咬部分,`ap_df_bonus` = 形态跃迁部分,`ap_expect` = 两者之和,`ap_ok`/`ap_cap_ok` 同值)。
 */
function doMamuBite(ctx, tag, modeText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var mode = "" + modeText;
    var err = "";
    var bites = (mode === "three") ? 3 : 1;
    var awk0 = (mode === "dragon") ? 7 : 0;
    try {
        mamuClearItem(p, MAMU_BITE_ID);
        mamuClearItem(p, MAMU_ROAR_ID);
        clearCurioSlots(p, "chip");
        MamuSignItemClass.setAwakening(p, awk0);
        MamuSignItemClass.setBiteBonusActive(p, false);
        ModEffectRemoval.remove(p, teruBlessing());
        MamuDragonEffectClass.remove(p);
        ModAttachments.setMamushiForcedCooldownUntil(p, 0);
    } catch (e0) { err = err + "|reset:" + domExText(e0); }
    var keep = [];
    for (var i = 0; i < bites; i++) keep.push(new NardisAppliedStoneClass(MAMU_BITE_TYPE, 1, false));
    try {
        var biteCost = 2;
        try { biteCost = NardisCardRegistryClass.cost(MAMU_BITE_TYPE, p) - 0; } catch (eC) { biteCost = 2; }
        var enh = new NardisWeaponEnhancementClass(bites * biteCost, 10, 0, 10, 0, keep);
        var w = domWriteEnh(p, "curio", enh);
        if (w.err !== "") err = err + "|write:" + w.err;
        err = err + "|set_ok_curio=" + w.curio + ":set_ok_hand=" + w.hand;
    } catch (e1) { err = err + "|enh:" + domExText(e1); }
    // 骰神赐福(立牌 tick 的兜底会清锁存 ⇒ 必须在触发**之前**给上)
    try { p.addEffect(new MobEffectInstanceClass(teruBlessing(), 6000, 0, false, false, true)); }
    catch (e2) { err = err + "|bless:" + domExText(e2); }
    // 驱动一次产品 tick(与玩家 tick 事件调的是同一份代码)⇒ 形态失效/锁存兜底都走真实入口
    try { BaseSignItemClass.tickSignActiveLock(p); } catch (e3) { err = err + "|tick:" + domExText(e3); }
    var eqBite = mamuEquippedCount(p, MAMU_BITE_TYPE);
    var latch0 = mamuBiteBonusActive(p);
    var ap0 = mamuAp(p);
    var fxD0 = mamuFx(p, 0);
    var df0 = mamuDragonFlag(p);
    try { MamuSignItemClass.onDiceBlessingTriggered(p); } catch (e4) { err = err + "|trigger:" + domExText(e4); }
    var latch1 = mamuBiteBonusActive(p);
    var ap1 = mamuAp(p);
    var awk1 = mamuAwaken(p);
    var df1 = mamuDragonFlag(p);
    // 「耐久 1 被消耗 ⇒ 撕咬离身」:直接清空装配栏骰子上的撕咬,再读回锁存与攻击力
    var consumeErr = "none";
    try { consumeErr = domClearCurioDice(p); } catch (e5) { consumeErr = domExText(e5); }
    var eqAfter = mamuEquippedCount(p, MAMU_BITE_TYPE);
    var latch2 = mamuBiteBonusActive(p);
    var ap2 = mamuAp(p);
    // 撕咬部分 = `min(觉醒层数, BITE_BONUS_CAP)`(裁决 4:实时;不落地状态)
    var capDelta = Math.min(awk1, (MamuSignItemClass.BITE_BONUS_CAP - 0));
    // 【2026-09-27 修正】`dragon` 档里 7 + 1 = 8 层是**同一拍跨过真龙阈值** ⇒ 攻击力同时还叠加
    // `DRAGON_FORM_ATTACK_BONUS(+5)`(由 `isDragonForm` 实时谓词给出)。旧口径只拿"撕咬部分"比,
    // ⇒ `dragon` 档必然 `ap_cap_ok=0`(实机 `ap=6>15>15`:实际 +9,`ap_cap_delta` 只给 4)
    // —— 那是**读数口径缺陷**,不是产品缺陷。现在把形态跃迁的 +N 显式算进 `ap_expect`。
    var dfBonus = ((df0 === 0 && df1 === 1) ? (MamuSignItemClass.DRAGON_FORM_ATTACK_BONUS - 0) : 0);
    var apExpect = capDelta + dfBonus;
    var apDelta = ((ap0 < 0 || ap1 < 0) ? -999 : (ap1 - ap0));
    var apOk = (((ap0 >= 0) && (ap1 >= 0) && (apDelta === apExpect)) ? 1 : 0);
    send(ctx, "AP_" + tag + "_BITE:mode=" + mode
        + ":bites=" + bites + ":eq_bite=" + eqBite
        + ":awk=" + awk0 + ">" + awk1 + ":awk_delta=" + (awk1 - awk0)
        + ":awk_ok=" + ((awk1 - awk0) === bites ? 1 : 0)
        + ":latch=" + latch0 + ">" + latch1 + ">" + latch2
        + ":latch_set=" + ((latch1 === 1) ? 1 : 0)
        + ":latch_kept_after_consume=" + ((latch2 === 1) ? 1 : 0)
        + ":ap=" + ap0 + ">" + ap1 + ">" + ap2
        + ":ap_delta=" + apDelta
        + ":ap_cap_delta=" + capDelta
        + ":ap_df_bonus=" + dfBonus
        + ":ap_expect=" + apExpect
        + ":ap_cap_ok=" + apOk
        + ":ap_ok=" + apOk
        + ":ap_kept=" + ((ap1 >= 0 && ap2 === ap1) ? 1 : 0)
        + ":eq_after=" + eqAfter + ":eq_cleared=" + ((eqAfter <= 0) ? 1 : 0)
        + ":df=" + df0 + ">" + df1 + ":df_on_ok=" + ((mode === "dragon") ? ((df1 === 1) ? 1 : 0) : -1)
        + ":fx_d_on=" + mamuFx(p, 0).on + ":fx_d_before=" + fxD0.on
        + ":consume_err=" + consumeErr
        + (err === "" ? "" : ":err=" + err));
    return mamuRead(ctx, tag, p, "BITE");
}

/**
 * 专属守门(规格 §3.5 / §6 第 5 条 / §7 边界)。
 *   `mark`  ⇒ 只读:两张牌的 `isExclusive`、两张牌**不在**随机池
 *              (`RandomCardHandler.attackCards()` 逐个比对 id)、装备入口 `mayPlace` 门控
 *              (**真 ServerPlayer + 真 `CardInventoryMenu`**:开菜单 → `slots.get(i).mayPlace(stack)`);
 *   `seven` ⇒ 把觉醒直写到 7(配合 `mamubite dragon` 做 7→8 的构造)
 *
 * 读数:`AP_<tag>_MU_GUARD`(excl/pool/menu 三组 + **三种绑定分别读**的 canUse 组)。
 *  ⚠️ **`canuse_own=1/1` 不是守门失效**(2026-09-27 澄清):该字段用的是**无主**新栈
 *     (`new ItemStack(...)`,没有 `owner_uuid`),而规格 §3.5 明确「`canUse` 无主时放行并首次绑定」
 *     ⇒ 无主栈对**任何**玩家都是 1。要判守门必须看**有主**栈:
 *       `canuse_unowned_bite/roar` = 无主栈(期望 1/1;**证明不了守门**)
 *       `canuse_bound_self`        = 绑定本人(`setOwner(selfStack, p)`;期望 **1**)
 *       `canuse_bound_foreign`     = 绑定**另一名在线玩家**(期望 **0**;现场无他人 ⇒ `-1` + `bound_foreign=no_other_player`)
 *       装备入口侧同理:`mayplace_bite_self`(期望 1)/ `mayplace_bite_foreign`(期望 0)
 *       / `mayplace_bite_own`(= 无主栈,期望 1)。
 *  ⚠️ `mayPlace` 只能由**服务端**经 `menu.slots.get(idx).mayPlace(stack)` 触发(产品实现正是
 *     读 `Slot` 参数里的玩家)**不可达**的部分:客户端点击的真实鼠标路径 —— 已如实登记。
 *  ⚠️ 传副本:产品对一个**无主**栈入槽时会先 `ExclusiveCardUtil.canUse` 但**不写绑定**
 *     (`CardInventoryMenu` 只调 `canUse`,不调 `bindIfAbsent`),故 `mamu_place` 不会污染
 *     后续读数 —— 读数里仍显式给出 `owner_tag` 与该栈操作前后的 `owner` 一致标志。
 *  ⚠️ `owner_*` 是**闭集** `self|other|none|err`;`owner_api=ok` 表示探针的两条组件读取路径
 *     至少有一条可用(`err` 才是"读不到",见 {@link mamuOwnerUuid})。
 */
function doMamuGuard(ctx, tag, modeText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var mode = "" + modeText;
    var err = "";
    var bite = new ItemStack(resolveItem(MAMU_BITE_ID), 1);
    var roar = new ItemStack(resolveItem(MAMU_ROAR_ID), 1);
    var exclBite = zhaoBool(function () { return ExclusiveCardUtilClass.isExclusive(bite); });
    var exclRoar = zhaoBool(function () { return ExclusiveCardUtilClass.isExclusive(roar); });
    // 【2026-09-27 补】"获得者 / 非获得者 / 无主"三种绑定必须**分别**读,否则 `canuse_own=1/1`
    // 会被误读成"守门失效"。规格 §3.5 的语义是:**无主栈**对任何人放行并在入槽时首次绑定;
    // 有主栈只有获得者能用。故三组读数缺一不可:
    //   `canuse_unowned_bite/roar`  = 无主栈(期望 **1/1**,证明不了守门)
    //   `canuse_bound_self`         = 绑定到本人(期望 **1**)
    //   `canuse_bound_foreign`      = 绑定到**另一名在线玩家**(期望 **0**;现场无他人 ⇒ -1 并记 `no_other_player`)
    var canOwnBite = zhaoBool(function () { return ExclusiveCardUtilClass.canUse(p, bite); });
    var canOwnRoar = zhaoBool(function () { return ExclusiveCardUtilClass.canUse(p, roar); });
    var selfStack = new ItemStack(resolveItem(MAMU_BITE_ID), 1);
    var canSelf = -1;
    try {
        ExclusiveCardUtilClass.setOwner(selfStack, p);
        canSelf = zhaoBool(function () { return ExclusiveCardUtilClass.canUse(p, selfStack); });
    } catch (eS) { err = err + "|selfbind:" + domExText(eS); }
    var other = null;
    try {
        var allPre = mamuOnlineList(p);
        for (var jp = 0; jp < allPre.size(); jp++) { if (allPre.get(jp) !== p) { other = allPre.get(jp); break; } }
    } catch (eOp) { other = null; }
    var foreignStack = new ItemStack(resolveItem(MAMU_BITE_ID), 1);
    var canForeign = -1, foreignTxt = "n/a";
    if (other == null) { foreignTxt = "no_other_player"; }
    else {
        try {
            ExclusiveCardUtilClass.setOwner(foreignStack, other);
            foreignTxt = "owner=" + mamuOwnerTag(foreignStack, other);
            canForeign = zhaoBool(function () { return ExclusiveCardUtilClass.canUse(p, foreignStack); });
        } catch (eF) { err = err + "|foreignbind:" + domExText(eF); }
    }
    var ownerTagBefore = mamuOwnerTag(bite, p);
    var ownerUuidApi = "";
    try { ownerUuidApi = mamuOwnerUuid(bite).api; } catch (eU) { ownerUuidApi = "|probe:" + domExText(eU); }
    var pool = "-", poolHit = -1, poolN = -1;
    // 诊断字段(2026-09-28 回填,用于定位 1.20.1 侧那次 `pool_n=0:pool=[-]` 且**零异常文本**):
    //   · `pool_rawn`  = `getCardPool(ALL)` **原对象**的 `size()`(见下方"为什么必须先复制")
    //   · `pool_null`  = 池调用返回 null 的次数
    //   · `pool_err`   = 首个访问异常文本(分类前缀 outer/call/size/get),文本已净化
    var poolRawN = -1, poolNulls = 0, poolErr = "";
    // ⚠️ `RandomCardHandler#attackCards()` 是 **private**(两线一致)⇒ 只能走**公开**的
    //    `getCardPool(CardCategory)`:取 `ALL ∪ BATTLE` 的并集 —— 两张专属战斗牌若混进随机池,
    //    必然出现在这两个池中的至少一个(专属牌不进池 ⇒ 两个池都不应出现它们)。
    try {
        var acc = "";
        var hit = 0;
        var seen = {};
        var n = 0;
        var cats = [MamuRandomCardHandlerClass.CardCategory.ALL, MamuRandomCardHandlerClass.CardCategory.BATTLE];
        for (var ci = 0; ci < cats.length; ci++) {
            var raw = null;
            try { raw = MamuRandomCardHandlerClass.getCardPool(cats[ci]); }
            catch (eR) { if (poolErr === "") poolErr = "call:" + domExText(eR); continue; }
            if (raw == null) { poolNulls = poolNulls + 1; continue; }
            // ⚠️ 池对象是 JDK **包私有**内部类 `java.util.ImmutableCollections$ListN`,必须先复制再读:
            //    `RandomCardHandler#getCardPool` 尾句是 `items.stream().map(ItemStack::new).toList()`
            //    (Java 16+ ⇒ `ImmutableCollections$ListN`)。
            //    **实测矩阵(2026-09-28 独立 Rhino 验证,两线真 jar)**:
            //      `size()` / `isEmpty()` **可用** —— 它们由**公开**类 `java.util.AbstractCollection`
            //        实现,`MemberBox` 走公开超类型即可;
            //      `iterator()` / `toArray()` 亦可用;
            //      `get(int)` **抛** `IllegalAccessException: class dev.latvian.mods.rhino.MemberBox
            //        cannot access a member of class java.util.ImmutableCollections$ListN
            //        (in module java.base) with modifiers "public"` —— 它声明在包私有
            //        `ImmutableCollections$AbstractImmutableList` 上。
            //    ⇒ 这正好解释修好之前那个症状:`list.size()` 读到 22(循环正常跑 22 轮),而每次
            //       `list.get(i)` 都抛进**内层** catch 被吞掉(⇒ `st=null` ⇒ `continue`)⇒ 读数恰好是
            //       `pool_n=0:pool=[-]` 且外层零异常文本(不是"池是空的")。
            //    ⇒ **必须先复制**:`new ArrayListClass(raw)` —— 构造函数声明在**公开**类
            //       `java.util.ArrayList` 上(可访问),复制由 Java 侧在构造函数内部完成
            //       (`c.toArray()`),不经 Rhino 成员分派。**不得**改成 `raw.size()/raw.get(i)`
            //       (size 只是侥幸可用,`get` 必抛;不要把"侥幸"写进契约)。
            try { if (ci === 0) poolRawN = raw.size() - 0; } catch (eS) { if (poolErr === "") poolErr = "size:" + domExText(eS); }
            var list = new ArrayListClass(raw);
            for (var i = 0; i < list.size(); i++) {
                var st = null;
                try { st = list.get(i); } catch (e1) { st = null; if (poolErr === "") poolErr = "get:" + domExText(e1); }
                if (st == null || st.isEmpty()) continue;
                var id = itemIdOf(st);
                if (seen[id] === 1) continue;
                seen[id] = 1;
                acc = (acc === "") ? id : (acc + "," + id);
                n = n + 1;
                if (id === MAMU_BITE_ID || id === MAMU_ROAR_ID) hit = hit + 1;
            }
        }
        pool = (acc === "") ? "-" : acc;
        poolHit = hit;
        poolN = n;
    } catch (e2) {
        // 外层 catch 也必须留痕:否则 `pool=[?:…]` 与 `pool_err=-` 同时出现会自相矛盾
        if (poolErr === "") poolErr = "outer:" + domExText(e2);
        pool = "?:" + domExText(e2); poolHit = -1; poolN = -1;
    }
    // 装备入口:`Slot#mayPlace`(真菜单 + 真 ServerPlayer)
    var menuTxt = "menu=none", placeBiteOwn = -1, placeRoarOwn = -1, placeBiteForeign = -1, placeBiteSelf = -1;
    try {
        var menu = teruOpenCardMenu(p);
        if (menu != null) {
            var atk = menu.getAttackSlots() - 0;
            menuTxt = "menu=ok:slots=" + (menu.getCardSlots() - 0) + ":attack=" + atk;
            if (atk > 0) {
                var s0 = menu.slots.get(0);
                placeBiteOwn = zhaoBool(function () { return s0.mayPlace(bite); });
                placeRoarOwn = zhaoBool(function () { return s0.mayPlace(roar); });
                // 获得者(绑定本人)⇒ 期望放行
                placeBiteSelf = zhaoBool(function () { return s0.mayPlace(selfStack); });
                // 非获得者(绑定另一名在线玩家)⇒ 期望拒绝;现场没有第二名玩家时保持 -1 并如实标注
                if (other != null) {
                    placeBiteForeign = zhaoBool(function () { return s0.mayPlace(foreignStack); });
                }
            } else { menuTxt = "menu=ok:attack=0"; }
        }
        try { teruCloseMenu(p); } catch (e4) { err = err + "|close:" + domExText(e4); }
    } catch (e5) { menuTxt = "menu=ex"; err = err + "|menu:" + domExText(e5); }
    if (mode === "seven") {
        try { MamuSignItemClass.setAwakening(p, MamuSignItemClass.AWAKEN_MAX - 1); } catch (e6) { err = err + "|seven:" + domExText(e6); }
    }
    send(ctx, "AP_" + tag + "_MU_GUARD:mode=" + mode
        + ":excl_bite=" + exclBite + ":excl_roar=" + exclRoar
        + ":canuse_own=" + canOwnBite + "/" + canOwnRoar
        + ":canuse_unowned_bite=" + canOwnBite + ":canuse_unowned_roar=" + canOwnRoar
        + ":canuse_bound_self=" + canSelf + ":canuse_bound_foreign=" + canForeign
        + ":bound_foreign=" + foreignTxt
        + ":owner_tag=" + ownerTagBefore + ":owner_after=" + mamuOwnerTag(bite, p)
        + ":owner_unchanged=" + ((ownerTagBefore === mamuOwnerTag(bite, p)) ? 1 : 0)
        + ":owner_api=" + ((ownerUuidApi === "") ? "ok" : ownerUuidApi)
        + ":pool_hits=" + poolHit + ":pool_n=" + poolN
        + ":pool=[" + pool + "]"
        + ":pool_rawn=" + poolRawN + ":pool_null=" + poolNulls
        + ":pool_err=" + (poolErr === "" ? "-" : ("" + poolErr).replace(/[:|\r\n]/g, "/"))
        + ":mayplace_bite_own=" + placeBiteOwn + ":mayplace_roar_own=" + placeRoarOwn
        + ":mayplace_bite_self=" + placeBiteSelf
        + ":mayplace_bite_foreign=" + placeBiteForeign
        + ":foreign=" + foreignTxt
        + ":" + menuTxt
        + ":awk=" + mamuAwaken(p)
        + (err === "" ? "" : ":err=" + err));
    return 1;
}

/**
 * **真死亡 + 服务端真重生**(规格 §2.5 / §6-6 的「死亡后觉醒保留」)。`mamudie <tag> <keepinv>`。
 *
 * <p>**为什么需要它**(2026-09-27 实机踩坑):用例里用原版 `/kill @s` 杀真人,会让客户端停在
 * **死亡界面**,之后所有聊天注入(命令)都被吞 —— 实测采集用例的 `/kill @s` 之后 `latest.log`
 * 完全静默(`AP_CL10_MU_CLEAR` 不存在),且紧随其后的 `LOCK-OFFLINE-RELOG-A` 8 条断言全 FAIL、
 * `AP_RA*` 命中 0 次。本命令把 **kill + respawn 压在一次执行内**,走
 * {@link mamuRespawn} 的**原版重生链**(服务端 `PlayerList#respawn`)。
 *
 * <p>⚠️⚠️ **「压在一次执行内」并**不能**保证客户端不留在死亡界面(2026-09-28 实测推翻旧结论)**:
 * 实测 `mamudie` 之后客户端**仍停在「你死了!」界面**(截图确认),而同一刻服务端读数是
 * `respawn_ok=1:p_alive=1`、HUD 满血 ⇒ 玩家其实活着,只是**纯客户端界面卡死**,其后所有聊天注入
 * 被吞(01:42 之后 `latest.log` 只剩 `AP_NOAI` 心跳、0 条用例读数),把紧随的
 * `LOCK-OFFLINE-1.21.1`(47 断言)与 `LOCK-OFFLINE-RELOG-A-1.21.1`(16 断言)整片染红。
 * **根因**(源码级):`ServerPlayer#die` 的死亡包带 `PacketSendListener`
 * (`exceptionallySend`,1.21.1 `ServerPlayer.java:691-706`),其写盘走事件循环任务 ⇒ **可能晚于**
 * 同一次执行内 `PlayerList#respawn` 的 `ClientboundRespawnPacket`(`:490`,普通写)到达客户端;
 * 客户端 `handleRespawn` **只在「当前界面已是 DeathScreen」时**才 `setScreen(null)`
 * (`ClientPacketListener.java:1230-1232`),此刻界面还没创建 ⇒ 不关;随后迟到的死亡包**新建**
 * 死亡界面(`:1715-1716`),而玩家已被重生为满血 ⇒ 界面永久驻留。
 * ⇒ **正确口径由环境不变量提供**:测试启动期 `mt_launch.ps1` 注入 `gamerule doImmediateRespawn true`
 * (硬闸门 + 游戏内回显自证),客户端因此 `showDeathScreen=false`
 * (`ClientPacketListener:1491`),死亡包改走 `player.respawn()` 分支(`:1717`)⇒
 * **从不创建死亡界面**;客户端自发 `PERFORM_RESPAWN` 被 `ServerGamePacketListenerImpl:1672` 的
 * `getHealth() > 0.0F` 守卫早退,不会与本命令的服务端重生竞争(实测 `respawn_ok=1:p_alive=1`)。
 * **本命令自身不再假设客户端状态**;若绕过 `mt_launch` 手工启动客户端,必须自行打开该规则。
 *
 * <p>`keepinv`:传 `0` 对应需求里的「`keepInventory=false` 场景由两层机制保证」
 * (`.copyOnDeath()` + `DeathPreservedBonuses`);传 `1` 走 `keepEverything=true` 分支
 * (此时 `PlayerEvent.Clone` 的 `wasDeath=false`,但 `PlayerRespawnEvent` 侧仍会补一次)。
 *
 * <p>⚠️ **世界的 `keepInventory` 游戏规则会在 `keepEverything=false` 时覆盖物品保留**
 * (`ServerPlayer#restoreFrom` 1.21.1 `:1458` / 1.20.1 `:1166`)⇒ `keepinv=0` 时本命令会**显式把
 * 该规则置 false**(否则"死亡掉落 → 立牌离身 → `clearSignData` 清零"这条链根本不会发生,
 * 测出来的"觉醒保留"是假绿)。`gamerule_ki=<置前>><置后>` 与 `gamerule_set=<命令 rc>` 都进读数。
 *
 * <p>读数:
 *   `AP_<tag>_MU_DIE:`(主行)
 *   `AP_<tag>_DIE_MAW/_DIE_MCARD/_DIE_MDICE/_DIE_MFX/_DIE_MCD/_DIE_MBASE`(**重生后的新实体**全量快照,
 *   字段顺序与 `ZERO_*` / `BASE_*` 等各族**完全一致**,同出 {@link mamuRead})。
 *   读数直发**新玩家**(`via=player`)以避开"旧引用已移除"的出口风险。
 *   主行尾部三个**只追加**字段(2026-09-28 新增,见下两条 ⚠️):
 *   `revive_hp=<补血前>><补血后>`、`pos_err=<坐标/朝向读取失败原因>`(成功时不出现)、
 *   `resp_err=<重生失败原因>`(成功时不出现)。
 *
 * <p>⚠️ **重生后 curios 槽会因 `keepinv=0` 掉光** ⇒ 本命令**不假设**立牌还在(`sign=b>a` 与
 * `df=b>a` 都只是原样读数);要接着测后续语义,用例侧需再跑一次 `mamuprep`。
 * ⚠️ `latch_kept` 的**期望值是 0**:规格 §1 附件表明确 `mamushi_bite_bonus_active`
 * **不** `.copyOnDeath()` ⇒ 死亡后回默认 false(`-1` 表示前置 `latch0 != 1`,该项不适用)。
 * ⚠️ 重生会把玩家放到**世界出生点**(通常离测试区很远)⇒ 本命令在重生后把**死亡前的坐标/朝向**
 * 还原回去(`pos_restored=1`),否则用例尾部的 `/kill @e[type=!player,distance=..64]` 会打空、
 * bot 也会落到 12 格外。这是**位置**的还原,与死亡保留语义无关(不影响 `awake_kept` 等判据)。
 * 实测注记:朝向 `getYRot()/getXRot()` 在本环境**不可见**(Rhino 解析失败)⇒ 回退读裸字段
 * `p.yRot/p.xRot`,并在 `pos_how` 后缀 `_yawfield` 留痕(`pos_restored` 仍为 1);
 * 坐标三项 `getX/getY/getZ` 与朝向已**拆成两个 try**,朝向失败不再连坐标一起丢(旧写法实测
 * `pos_restored` 恒 0 / `pos_how=no_pos`)。
 * ⚠️⚠️ **`keepinv=1` 的重生会连血量一起复制**:`PlayerList#respawn(p, true, rr)` 走
 * `keepEverything=true` ⇒ `ServerPlayer#restoreFrom`(1.21.1 `:1443` 先满血,`:1446` 再
 * `setHealth(that.getHealth())`;**1.20.1 `:1157-1159` 直接 `setHealth(that.getHealth())`**,
 * 连前置满血都没有)**把死亡玩家的 0 血复制给新实体** ⇒ 新玩家 0 血濒死:
 * `LivingEntity#isAlive()`(1.21.1 `:1632` / 1.20.1 `:1545` 同为 `!isRemoved() && getHealth() > 0`)
 * 读成 0(`hp=20>0:p_alive=0`),且不放任会被 `tickDeath` 真移除 ⇒
 * **本命令在读数前显式补满血**(仅当读到 0 才动手),并把前后血量写进 `revive_hp=0>20`。
 * 这是**本命令的契约**(结束时玩家活着)而不是产品语义,不影响 `awake_kept`/`copied_ok` 判据。
 */
function doMamuDie(ctx, tag, keepText) {
    if (!mamuClassGate(ctx, tag)) return 1;
    var p = ctx.source.getPlayerOrException();
    var keepArg = "" + keepText;
    var keepInv = (keepArg === "1" || keepArg === "true") ? true : false;
    // 0. 前置:生存模式 + 满血(创造模式不会死;血量必须为正,否则 respawn 的 wasDeath 语义不清)
    var gm = "already_survival";
    try { p.setGameMode(GameTypeClass.SURVIVAL); gm = "forced_survival"; } catch (e0) { gm = "err:" + domExText(e0); }
    try { p.setHealth(p.getMaxHealth()); } catch (e1) { /* 忽略 */ }
    // 死亡前坐标/朝向(重生后还原;见函数头说明)
    // ⚠️ 2026-09-28 实测:`pos_restored` 此前**恒为 0**(`pos_how=no_pos`)—— 坐标与朝向挤在同一个
    //    try 里,任一项抛异常就把**整段坐标**一起丢掉(而坐标还原是**功能前置**:重生会把玩家丢回
    //    世界出生点,用例尾部的 `/kill @e[type=!player,distance=..64]` 清场与 bot 距离判据都依赖
    //    玩家回到测试区)。坐标三项 `getX/getY/getZ` 在探针里被大量使用且从未出问题,而
    //    `getYRot()/getXRot()` **全探针只出现在这一处**(两线各一行、此前从未真正执行过)⇒
    //    拆成两个 try:坐标独立成败;朝向退化为「getter → 裸字段 → 0」三级,失败原因进 `pos_err`
    //    只读读数(不静默)。朝向只是还原观感,失败**不得**连坐标一起牺牲。
    var px = 0.0, py = 0.0, pz = 0.0, pyaw = 0.0, ppitch = 0.0, posOk = 0, posErr = "", posRotViaField = 0;
    try {
        px = p.getX() - 0; py = p.getY() - 0; pz = p.getZ() - 0;
        posOk = 1;
    } catch (eP) { posOk = 0; posErr = "xyz:" + domExText(eP); }
    if (posOk === 1) {
        try { pyaw = p.getYRot() - 0; ppitch = p.getXRot() - 0; }
        catch (eY1) {
            try { pyaw = p.yRot - 0; ppitch = p.xRot - 0; posRotViaField = 1; }
            catch (eY2) { pyaw = 0.0; ppitch = 0.0; posErr = posErr + "|rot:" + domExText(eY1); }
        }
    }
    // keepInventory=false 场景:显式关掉世界规则(否则 restoreFrom 会照常保留物品)
    var kiBefore = mamuKeepInvRule(p);
    var kiSet = "-";
    if (!keepInv) {
        try { kiSet = "" + runCmdP(p, "gamerule keepInventory false"); }
        catch (e2) { kiSet = "err:" + domExText(e2); }
    }
    var kiAfter = mamuKeepInvRule(p);
    // 1. 前置读数
    var awk0 = mamuAwaken(p), latch0 = mamuBiteBonusActive(p), df0 = mamuDragonFlag(p);
    var sign0 = mamuSignEquipped(p), fx0 = mamuFx(p, 0).on;
    var hp0 = zhaoNum(function () { return teruR1(p.getHealth()); });
    // 2. 真死亡(等价 /kill)
    var killHow = mamuKill(p);
    var died = 0;
    try { died = (p.isDeadOrDying() || (p.getHealth() - 0) <= 0) ? 1 : 0; } catch (e3) { died = -1; }
    // 3. 服务端真重生(**必须改用返回的新实体**);没真死就不重生(否则测的是"活人克隆",`copied_ok` 无意义)
    var r = { player: null, how: "skipped", rr: "-", err: "" };
    if (died === 1) { r = mamuRespawn(p, keepInv); }
    var np = r.player;
    // 3b. 位置/朝向还原(重生把玩家丢到**世界出生点**;见函数头说明)。
    //     首选 `ServerPlayer#connection#teleport(x,y,z,yaw,pitch)`(与客户端同步、不会穿墙瞬移丢包),
    //     退路 `ServerPlayer#setPos(x,y,z)`。**不用 `moveTo`**(Rhino 下 4 参/5 参重载有歧义,已踩坑)。
    var posRestored = -1, posHow = "n/a";
    if (np != null) {
        if (posOk === 1) {
            posHow = "fail";
            try {
                np.connection.teleport(px, py, pz, pyaw, ppitch);
                posHow = "teleport";
            } catch (eT1) {
                try { np.setPos(px, py, pz); posHow = "setpos"; }
                catch (eT2) { posHow = "err:" + domExText(eT2); }
            }
            try { np.setYRot(pyaw); np.setXRot(ppitch); } catch (eR) { /* 朝向尽力而为,失败不影响判据 */ }
            posRestored = ((posHow === "teleport" || posHow === "setpos") ? 1 : 0);
            // 朝向读数走了裸字段回退 ⇒ 在 `pos_how` 上留痕(`getYRot/getXRot` 在本环境**不可见**,
            // 实测两线同一处;不让它静默变成"看起来正常"的一行)。
            if (posRotViaField === 1 && posRestored === 1) { posHow = posHow + "_yawfield"; }
        } else { posHow = "no_pos"; posRestored = 0; }
    }
    // 3c. 保证「命令结束时玩家活着」(本命令的契约,见函数头说明)。
    //     ⚠️ 2026-09-28 实测缺陷:`keepinv=1` 走 `respawn(p, true, rr)`(keepEverything=true)时,
    //     原版 `ServerPlayer#restoreFrom` **会连血量一起复制** —— 1.21.1 `:1443` 先
    //     `setHealth(getMaxHealth())`,紧接着 `:1446` 在 keepEverything 分支里
    //     `setHealth(that.getHealth())` = **0**(死亡玩家)⇒ 新实体是「0 血濒死」态:
    //     `LivingEntity#isAlive()`(`:1632` = `!isRemoved() && getHealth() > 0`)读成 **0**
    //     (实测 `AP_GDS_MU_DIE:…:hp=20>0:p_alive=0`),且若不放任不管,`tickDeath` 会在若干 tick 后
    //     **真把玩家移除** ⇒ 用例后续步骤全部落空、且下一个用例拿到「玩家不存在」的现场。
    //     这里在读数**之前**显式补满血并把「发生过补血」写进读数(`revive_hp`,只读、不静默);
    //     `keepinv=0` 分支由原版 `:1443` 决定(keepEverything=false ⇒ 不被覆盖),故实测 `p_alive=1`,
    //     本分支只在读数为 0 时才动手 ⇒ 对正常路径零影响。
    var reviveBefore = -1, reviveAfter = -1;
    if (np != null) {
        reviveBefore = zhaoNum(function () { return teruR1(np.getHealth()); });
        reviveAfter = reviveBefore;
        if (reviveBefore === 0) {
            try {
                np.setHealth(np.getMaxHealth());
                reviveAfter = zhaoNum(function () { return teruR1(np.getHealth()); });
            } catch (eH) { reviveAfter = -2; }
        }
    }
    // 4. 后置读数(全在新实体上;读不到就给 -1,不冒充 0)
    var awk1 = (np == null) ? -1 : mamuAwaken(np);
    var latch1 = (np == null) ? -1 : mamuBiteBonusActive(np);
    var df1 = (np == null) ? -1 : mamuDragonFlag(np);
    var sign1 = (np == null) ? -1 : mamuSignEquipped(np);
    var fx1 = (np == null) ? -1 : mamuFx(np, 0).on;
    var hp1 = (np == null) ? -1 : zhaoNum(function () { return teruR1(np.getHealth()); });
    var alive = (np == null) ? -1 : zhaoBool(function () { return np.isAlive(); });
    var respawnOk = ((np != null) && (np !== p)) ? 1 : 0;
    var awakeKept = ((awk0 >= 0 && awk1 >= 0 && awk1 >= awk0) ? 1 : 0);
    var latchKept = ((latch0 === 1) ? ((latch1 === 1) ? 1 : 0) : -1);
    mamuEmit(ctx, np, "AP_" + tag + "_MU_DIE:keepinv=" + (keepInv ? 1 : 0)
        + ":keep_arg=" + keepArg
        + ":gm=" + gm
        + ":gamerule_ki=" + kiBefore + ">" + kiAfter + ":gamerule_set=" + kiSet
        + ":how=" + r.how + ":rr=" + r.rr
        + ":kill=" + killHow + ":died=" + died
        + ":respawn_ok=" + respawnOk
        + ":same_entity=" + ((np === p) ? 1 : 0)
        + ":pos_restored=" + posRestored + ":pos_how=" + posHow
        + ":p_alive=" + alive + ":hp=" + hp0 + ">" + hp1
        + ":awk=" + awk0 + ">" + awk1 + ":awake_kept=" + awakeKept + ":copied_ok=" + awakeKept
        + ":latch=" + latch0 + ">" + latch1 + ":latch_kept=" + latchKept
        + ":sign=" + sign0 + ">" + sign1
        + ":df=" + df0 + ">" + df1
        + ":fx_d_on=" + fx0 + ">" + fx1
        + ":via=" + ((np != null) ? "player" : "ctx")
        + ":revive_hp=" + reviveBefore + ">" + reviveAfter
        + (posErr === "" ? "" : ":pos_err=" + posErr)
        + (r.err === "" ? "" : ":resp_err=" + r.err));
    // 全量快照(字段顺序与 ZERO_* 逐字一致;第 5 参 = 直发新玩家)
    return mamuRead(ctx, tag, (np != null) ? np : p, "DIE", np);
}
// MAMUSHI-IMPL-END(插入器用:重跑 build_mamushi_block.ps1 时靠这一行定位旧块并整段替换)

// ══════════════════════════════════════════════════════════════════════════════
//  飞星筹码 / 精英-神化判定 / sherry / hanna —— 冒烟段(2026-09-21 新增)
//    /astralprobe ssspawn <tag> <mode> [entityId]  生成带精英特征的靶子并回读产品判定
//    /astralprobe ssfire  <tag>                    清共享冷却 + 直接触发一次飞星
//    /astralprobe ssread  <tag>                    只读:星光 / 共享冷却 / 靶子血量与精英判定
//    /astralprobe ssgeom  <tag>                    只读:落体几何(行程/起点/命中点/1.2 倍校核)
//    /astralprobe eliteread <tag>                  只读:靶子的精英判定分项(阈值/boss/神化 NBT)
//    /astralprobe sherryread <tag>                 只读:推理时间层数 + 效果镜像
//    /astralprobe hannaread  <tag>                 只读:人偶制作 / 人偶完成 / 魔女漂浮 / 两条冷却
//  mode: plain | apoth | apothmini | hp | armor
//    plain     = 普通僵尸(20 血 2 甲 ⇒ 非精英)
//    apoth     = 写实体持久化 NBT apoth.boss     = 1b(模拟「神化 Boss / Apothic Invader」)
//    apothmini = 写实体持久化 NBT apoth.miniboss = 1b(模拟「神化精英 / Apothic Elite」)
//    hp        = 最大生命调到 60(> 40 ⇒ 精英阈值)
//    armor     = 护甲调到 25(> 20 ⇒ 精英阈值)
// ══════════════════════════════════════════════════════════════════════════════
var EliteTargetsClass = Java.loadClass("com.merlinkitsune.astral_dice.combat.EliteTargets");
var ShootingStarManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.combat.ShootingStarManager");
var StarLightManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.item.StarLightManager");
var BossEntityUtilClass = Java.loadClass("com.merlinkitsune.starenginelib.item.BossEntityUtil");
var AttributesClass = Java.loadClass("net.minecraft.world.entity.ai.attributes.Attributes");

var SS_TARGET_TAG = "astral_ss_target";
/** 实体持久化 NBT 的容器键(1.20.1 由 patch() 替换为 ForgeData) */
var SS_NBT_ROOT = "NeoForgeData";
/** 冻结用的共享冷却时长(tick):足够长以阻止产品 curioTick 的自动触发,隔离读数 */
var SS_FREEZE_TICKS = 100000;

/** 统计半径内带 tag 的靶子数量(诊断用)。 */
function ssCountTargets(p, radius) {
    var n = 0;
    try {
        var aabb = AABBClass(p.getX() - radius, p.getY() - radius, p.getZ() - radius,
            p.getX() + radius, p.getY() + radius, p.getZ() + radius);
        var list = p.level.getEntitiesOfClass(LivingEntityClass, aabb);
        for (var i = 0; i < list.size(); i++) {
            var e = list.get(i);
            try { if (e.getTags() != null && e.getTags().contains(SS_TARGET_TAG)) n = n + 1; } catch (t1) { }
        }
    } catch (e0) { }
    return n;
}

/** 把共享冷却推到很远的将来 —— 阻止产品 curioTick 自动触发,让读数只反映 ss* 命令的动作。 */
function ssFreeze(p) {
    try { ModAttachments.setShootingStarCooldownEnd(p, (nowTick(p) - 0) + SS_FREEZE_TICKS); } catch (e) { }
}

/** 找最近的飞星靶子(按实体 tag 过滤,跨命令调用可用)。 */
function ssFindTarget(p, radius) {
    var best = null;
    var bestD = 1.0e9;
    try {
        var aabb = AABBClass(p.getX() - radius, p.getY() - radius, p.getZ() - radius,
            p.getX() + radius, p.getY() + radius, p.getZ() + radius);
        var list = p.level.getEntitiesOfClass(LivingEntityClass, aabb);
        for (var i = 0; i < list.size(); i++) {
            var e = list.get(i);
            var hit = false;
            try { hit = (e.getTags() != null) && e.getTags().contains(SS_TARGET_TAG); }
            catch (t1) { try { hit = ("" + e.getTags()).indexOf(SS_TARGET_TAG) >= 0; } catch (t2) { hit = false; } }
            if (!hit) continue;
            var d = 0.0;
            try { d = e.distanceToSqr(p); } catch (t3) { d = 0.0; }
            if (d < bestD) { bestD = d; best = e; }
        }
    } catch (e0) { /* 查找失败按无目标处理 */ }
    return best;
}

/**
 * 按 mode 给靶子"精英化",返回实际写入的项目列表文本。
 *
 * ⚠️ 神化标记必须走**原版 `/data merge entity`**,不能用 KubeJS 的 `entity.getPersistentData()`:
 * 2026-09-21 实测 —— KubeJS 侧 `pd.putBoolean("apoth.boss", true)` 后，探针自己能读回 1，
 * 但**产品** `EliteTargets.isEliteOrBoss` 对同一实体仍返回 false（`apoth_boss=1 / elite=0` 并存）。
 * 说明 KubeJS 的 `getPersistentData()` 与 Java 的 `Entity#getPersistentData()` **不同源**
 * （前者写进了 KubeJS 自己的脚本数据通道）。原版 `/data merge` 走实体 NBT 的官方通道，
 * 与 NeoForge/Forge 的持久化标签一致 ⇒ 产品可见。
 */
function ssMakeElite(ctx, p, mob, mode) {
    var done = [];
    var m = "" + mode;
    try {
        if (m === "apoth" || m === "apothmini") {
            var key = (m === "apoth") ? "apoth.boss" : "apoth.miniboss";
            var nbt = "{" + SS_NBT_ROOT + ":{\"" + key + "\":1b}}";
            var rc = runCmd(ctx, "data merge entity @e[tag=" + SS_TARGET_TAG + ",limit=1] " + nbt);
            done.push(m);
            if (("" + rc).indexOf("rc=") !== 0) { done.push("merge:" + rc); }
        } else if (m === "hp") {
            var hAttr = mob.getAttribute(AttributesClass.MAX_HEALTH);
            hAttr.setBaseValue(60.0);
            mob.setHealth(60.0);
            done.push("hp60");
        } else if (m === "armor") {
            var aAttr = mob.getAttribute(AttributesClass.ARMOR);
            aAttr.setBaseValue(25.0);
            done.push("armor25");
        } else {
            done.push("plain");
        }
    } catch (e1) { done.push("ERR:" + exText(e1)); }
    return done.join(",");
}

/** 生成一只飞星靶子;mode 决定精英化手段。 */
function doSsSpawn(ctx, tag, mode, entityId) {
    var p = ctx.source.getPlayerOrException();
    var id = ("" + entityId) === "" ? "minecraft:zombie" : ("" + entityId);
    var mob = spawnDummy(p, id, 2.5);
    if (mob == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed:" + id); return 0; }
    try { mob.addTag(SS_TARGET_TAG); } catch (e1) { /* 标签失败则后续读数找不到靶子 */ }
    var applied = ssMakeElite(ctx, p, mob, mode);
    var elite = -1, maxHp = -1, armor = -1, hp = -1;
    try { elite = EliteTargetsClass.isEliteOrBoss(mob) ? 1 : 0; } catch (e2) { }
    try { maxHp = mob.getMaxHealth() - 0; } catch (e3) { }
    try { armor = mob.getArmorValue() - 0; } catch (e4) { }
    try { hp = mob.getHealth() - 0; } catch (e5) { }
    send(ctx, "AP_" + tag + "_SSSPAWN:mode=" + mode + ":applied=" + applied
        + ":max_hp=" + maxHp + ":armor=" + armor + ":hp=" + hp + ":elite=" + elite
        + ":t_count=" + ssCountTargets(p, 8));
    ssFreeze(p);   // 生成后立即冻结:避免产品自动触发在断言前改动靶子血量
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/**
 * 清共享冷却后**直接调用产品入口**触发一次飞星(绕过 tick 时序,验证触发逻辑本身)。
 *
 * 读数: `cd_product` = 产品写入的冷却截止刻(应为 now+200 ⇒ `cd_delta=200`,验证 10 秒共享计时器);
 * 随后立即 `ssFreeze` 把冷却推到远处,使**产品 curioTick 的自动触发**不再干扰后续靶子血量读数。
 */
function doSsFire(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var now = nowTick(p) - 0;
    var err = "";
    try { ModAttachments.setShootingStarCooldownEnd(p, 0); } catch (e0) { err = exText(e0); }
    try { ShootingStarManagerClass.tick(p); } catch (e1) { err = err + "|tick:" + exText(e1); }
    var cdProduct = -1;
    try { cdProduct = ModAttachments.getShootingStarCooldownEnd(p) - 0; } catch (e2) { }
    ssFreeze(p);
    send(ctx, "AP_" + tag + "_SSFIRE:now=" + now + ":cd_product=" + cdProduct
        + ":cd_delta=" + (cdProduct - now)
        + ":cd_started=" + ((cdProduct > now) ? 1 : 0)
        + (err === "" ? "" : ":err=" + err));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 只读写:把玩家「星光」设为 n —— 用于精确控制金色飞星的**精英额外伤害**。
 *
 * 为什么不用 `signprep` 的 star 参数:那个参数依赖 `SignLagStarLightClass`,
 * 该句柄为 null 时**静默不设置**(只回显 star=-2),实测三次都没生效 ⇒ 自带一条。 */
function doSsStar(ctx, tag, n) {
    var p = ctx.source.getPlayerOrException();
    var want = n - 0;
    var before = -1, after = -1;
    var err = "";
    try { before = StarLightManagerClass.get(p) - 0; } catch (e1) { err = exText(e1); }
    try { StarLightManagerClass.set(p, want); after = StarLightManagerClass.get(p) - 0; }
    catch (e2) { err = err + "|set:" + exText(e2); }
    send(ctx, "AP_" + tag + "_SSSTAR:want=" + want + ":before=" + before + ":after=" + after
        + (err === "" ? "" : ":err=" + err));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 只读快照:星光 / 共享冷却 / 两枚筹码是否在装 / 靶子血量与精英判定。 */
function doSsRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var star = -1, cap = -1, cdEnd = -1, cdLeft = -1;
    var err = "";
    try { star = StarLightManagerClass.get(p) - 0; } catch (e1) { err = err + "|star:" + exText(e1); }
    try { cap = StarLightManagerClass.getCap() - 0; } catch (e2) { }
    try {
        cdEnd = ModAttachments.getShootingStarCooldownEnd(p) - 0;
        cdLeft = cdEnd - (nowTick(p) - 0);
        if (cdLeft < 0) cdLeft = 0;
    } catch (e3) { err = err + "|cd:" + exText(e3); }
    var t = ssFindTarget(p, 8);
    var tHp = -1, tAlive = -1, tElite = -1, tMax = -1, tArmor = -1;
    if (t != null) {
        try { tHp = t.getHealth() - 0; } catch (e4) { }
        try { tAlive = t.isAlive() ? 1 : 0; } catch (e5) { }
        try { tMax = t.getMaxHealth() - 0; } catch (e6) { }
        try { tArmor = t.getArmorValue() - 0; } catch (e7) { }
        try { tElite = EliteTargetsClass.isEliteOrBoss(t) ? 1 : 0; } catch (e8) { err = err + "|elite:" + exText(e8); }
    }
    send(ctx, "AP_" + tag + "_SSREAD:star=" + star + ":cap=" + cap
        + ":cd_end=" + cdEnd + ":cd_left=" + cdLeft
        + ":t_found=" + ((t == null) ? 0 : 1)
        + ":t_count=" + ssCountTargets(p, 8)
        + ":t_hp=" + tHp + ":t_max_hp=" + tMax + ":t_armor=" + tArmor
        + ":t_alive=" + tAlive + ":t_elite=" + tElite
        + ":" + chipStateText(p)
        + (err === "" ? "" : ":err=" + err));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 清场:移除全部飞星靶子 + 复位共享冷却。 */
function doSsClear(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var removed = 0;
    try {
        var aabb = AABBClass(p.getX() - 16, p.getY() - 8, p.getZ() - 16,
            p.getX() + 16, p.getY() + 8, p.getZ() + 16);
        var list = p.level.getEntitiesOfClass(LivingEntityClass, aabb);
        for (var i = 0; i < list.size(); i++) {
            var e = list.get(i);
            var hit = false;
            try { hit = (e.getTags() != null) && e.getTags().contains(SS_TARGET_TAG); }
            catch (t1) { hit = false; }
            if (hit) { try { e.discard(); removed = removed + 1; } catch (t2) { } }
        }
    } catch (e0) { }
    try { ModAttachments.setShootingStarCooldownEnd(p, 0); } catch (e1) { }
    send(ctx, "AP_" + tag + "_SSCLEAR:removed=" + removed);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 保留 2 / 3 / 4 位小数的本地格式化（KubeJS 侧没有现成的 toFixed 封装）。 */
function ssNum(x, digits) {
    var m = Math.pow(10, digits);
    return Math.round(x * m) / m;
}

/**
 * 只读快照:飞星**落体几何** —— 核对 2026-09-21 用户裁决的口径。
 *
 * 期望(探针侧按规范公式**独立**推导,不复用产品代码):
 *   legacy = 3.0 - 碰撞箱高/2            // 旧口径行程(脚底上方 3.0 → 碰撞箱中心)
 *   dist   = legacy * 1.2                // 新口径行程(下落速度 ×1.2、时长仍 1 秒 ⇒ 行程 ×1.2)
 *   speed  = dist / FALL_TICKS           // 每 tick 下落格数
 *   rise   = origin_y - (feet_y + 3.0)   // 起点相对旧起点的抬升量(应 > 0)
 *   head_match = 产品 impactPoint.y == 目标碰撞箱上沿 y   // 「落到目标头顶即视为命中」
 */
function doSsGeom(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var t = ssFindTarget(p, 8);
    var found = (t == null) ? 0 : 1;
    var h = -1, feetY = 0, headY = -1, originY = -1, impY = -1;
    var dist = -1, legacy = -1, ratio = -1, speed = -1, rise = -1;
    var ft = 20, headMatch = 0, originAbove = 0;
    var err = "";
    try { ft = ShootingStarManagerClass.FALL_TICKS - 0; } catch (e0) { }
    if (t != null) {
        try { h = t.getBbHeight() - 0; } catch (e1) { err = err + "|h:" + exText(e1); }
        try { feetY = t.getY() - 0; } catch (e2) { err = err + "|y:" + exText(e2); }
        try { headY = t.getBoundingBox().maxY - 0; } catch (e3) { err = err + "|maxY:" + exText(e3); }
        try { originY = ShootingStarManagerClass.launchOrigin(t).y() - 0; } catch (e4) { err = err + "|origin:" + exText(e4); }
        try { impY = ShootingStarManagerClass.impactPoint(t).y() - 0; } catch (e5) { err = err + "|imp:" + exText(e5); }
        try { dist = ShootingStarManagerClass.fallDistance(t) - 0; } catch (e6) { err = err + "|dist:" + exText(e6); }
        legacy = 3.0 - h * 0.5;
        ratio = (legacy !== 0) ? dist / legacy : -1;
        speed = (dist >= 0 && ft > 0) ? dist / ft : -1;
        rise = originY - (feetY + 3.0);
        headMatch = (ssNum(impY, 2) === ssNum(headY, 2)) ? 1 : 0;
        originAbove = (originY > impY) ? 1 : 0;
    }
    send(ctx, "AP_" + tag + "_SSGEOM:found=" + found
        + ":height=" + ssNum(h, 2)
        + ":feet_y=" + ssNum(feetY, 2)
        + ":head_y=" + ssNum(headY, 3)
        + ":origin_y=" + ssNum(originY, 3)
        + ":imp_y=" + ssNum(impY, 3)
        + ":dist=" + ssNum(dist, 2)
        + ":legacy=" + ssNum(legacy, 3)
        + ":ratio=" + ssNum(ratio, 2)
        + ":speed=" + ssNum(speed, 4)
        + ":fall_ticks=" + ft
        + ":rise=" + ssNum(rise, 2)
        + ":head_match=" + headMatch
        + ":origin_above=" + originAbove
        + (err === "" ? "" : ":err=" + err));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 只读:靶子的精英判定**分项**(用于定位是哪一路命中)。 */
function doEliteRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var t = ssFindTarget(p, 8);
    if (t == null) { send(ctx, "AP_" + tag + "_ELITE:no_target"); send(ctx, "AP_" + tag + "_DONE"); return 0; }
    var maxHp = -1, armor = -1, boss = -1, ab = -1, am = -1, elite = -1;
    var err = "";
    try { maxHp = t.getMaxHealth() - 0; } catch (e1) { }
    try { armor = t.getArmorValue() - 0; } catch (e2) { }
    try { boss = BossEntityUtilClass.isBossEntity(t) ? 1 : 0; } catch (e3) { err = err + "|boss:" + exText(e3); }
    try {
        var pd = t.getPersistentData();
        ab = pd.getBoolean("apoth.boss") ? 1 : 0;
        am = pd.getBoolean("apoth.miniboss") ? 1 : 0;
    } catch (e4) { err = err + "|pd:" + exText(e4); }
    try { elite = EliteTargetsClass.isEliteOrBoss(t) ? 1 : 0; } catch (e5) { err = err + "|elite:" + exText(e5); }
    send(ctx, "AP_" + tag + "_ELITE:max_hp=" + maxHp + ":armor=" + armor
        + ":boss=" + boss + ":apoth_boss=" + ab + ":apoth_mini=" + am
        + ":elite=" + elite + (err === "" ? "" : ":err=" + err));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 只读:怪力侦探(sherry)「推理时间」层数 + 效果镜像 + stand 槽。 */
function doSherryRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var layers = -1, mirror = -1, stand = -1;
    var err = "";
    try { layers = ModAttachments.getSherryReasoningLayers(p) - 0; } catch (e1) { err = err + "|layers:" + exText(e1); }
    try { mirror = p.hasEffect(ModEffects.SHERRY_REASONING) ? 1 : 0; } catch (e2) { err = err + "|eff:" + exText(e2); }
    try { stand = domSignEquipped(p); } catch (e3) { }
    send(ctx, "AP_" + tag + "_SHERRY:layers=" + layers + ":mirror=" + mirror + ":stand=" + stand
        + (err === "" ? "" : ":err=" + err));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 只读:人偶师(hanna)「人偶制作 / 人偶完成 / 魔女漂浮」与两条被动冷却。 */
function doHannaRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var craft = -1, complete = -1, floatOn = -1, cdF = -1, cdB = -1, stand = -1;
    var err = "";
    try { craft = ModAttachments.getHannaDollCraftLayers(p) - 0; } catch (e1) { err = err + "|craft:" + exText(e1); }
    try { complete = ModAttachments.getHannaDollComplete(p) ? 1 : 0; } catch (e2) { }
    try { floatOn = p.hasEffect(ModEffects.HANNA_FLOAT) ? 1 : 0; } catch (e3) { err = err + "|float:" + exText(e3); }
    try { cdF = ModAttachments.getHannaFantasyCooldownEnd(p) - 0; } catch (e4) { }
    try { cdB = ModAttachments.getHannaBlessingCooldownEnd(p) - 0; } catch (e5) { }
    try { stand = domSignEquipped(p); } catch (e6) { }
    var now = nowTick(p) - 0;
    send(ctx, "AP_" + tag + "_HANNA:craft=" + craft + ":complete=" + complete
        + ":float=" + floatOn + ":cd_fantasy=" + cdF + ":cd_blessing=" + cdB
        + ":now=" + now + ":stand=" + stand
        + (err === "" ? "" : ":err=" + err));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

ServerEvents.commandRegistry(event => {
    var Commands = event.commands;
    event.register(
        Commands.literal("astralprobe")
            // ── B6(2026-09-15):OP 只读前置 + 只读读数统一出口(dump) ──
            .then(Commands.literal("opprobe")
                .executes(ctx => guard(ctx, "OP", function () {
                    return opprobe(ctx);
                })))
            .then(Commands.literal("chargeset")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("n", IntegerArg.integer(0, 20))
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doChargeSet(ctx, StringArg.getString(ctx, "tag"),
                                IntegerArg.getInteger(ctx, "n"));
                        })))))
            .then(Commands.literal("chargecd")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doChargeCd(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("dumpstate")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return dumpState(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("cardprep")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("item", StringArg.string())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doCardPrep(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "item"));
                        })))))
            .then(Commands.literal("cardread")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doCardRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("cardself")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doCardSelf(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            // ── 2026-09-25:活体书页重写(敌对目标选择器 + 飞行命中法伤 + 连续出牌规则)──
            .then(Commands.literal("lpprep")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("type", StringArg.string())
                        .then(Commands.argument("dist", StringArg.word())
                            .then(Commands.argument("layers", StringArg.word())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doLpPrep(ctx, StringArg.getString(ctx, "tag"),
                                        StringArg.getString(ctx, "type"),
                                        StringArg.getString(ctx, "dist"),
                                        StringArg.getString(ctx, "layers"));
                                })))))))
            .then(Commands.literal("lpnonhostile")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doLpNonHostile(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("lpwall")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("dist", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doLpWall(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "dist"));
                        })))))
            .then(Commands.literal("lpshoot")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doLpShoot(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("lpread")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doLpRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("lpreset")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doLpReset(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("lpcredit")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("value", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doLpCredit(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "value"));
                        })))))
            .then(Commands.literal("lprin")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("value", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doLpRin(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "value"));
                        })))))
            .then(Commands.literal("lpchain")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("hits", StringArg.word())
                        .then(Commands.argument("dist", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doLpChain(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "hits"),
                                    StringArg.getString(ctx, "dist"));
                            }))))))
            .then(Commands.literal("lpclean")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doLpClean(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("quiver")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doQuiverRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("glovebase")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doGloveBase(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("spelltdsetup")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSpellTdSetup(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("spelltdhit")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSpellTdHit(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("fensplash")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doFenSplash(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("fensplashhit")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doFenSplashHit(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("fensplashread")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doFenSplashRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("truedmg")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doTrueDmg(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("railtruedmg")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRailTrueDmg(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("railtruedmgread")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRailTrueDmgRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("diag")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doDiag(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("equipslot")
                .then(Commands.argument("slot", StringArg.word())
                    .then(Commands.argument("item", StringArg.string())
                        .then(Commands.argument("tag", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doEquipSlot(ctx, StringArg.getString(ctx, "slot"),
                                    StringArg.getString(ctx, "item"), StringArg.getString(ctx, "tag"));
                            }))))))
            .then(Commands.literal("chipstate")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doChipState(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("chipreset")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doChipReset(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("attack")
                .then(Commands.argument("type", StringArg.string())
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doAttack(ctx, StringArg.getString(ctx, "type"),
                                StringArg.getString(ctx, "tag"));
                        })))))
            .then(Commands.literal("railguncd")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRailgunCleared(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("railgunfriendly")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRailgunFriendly(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("railgunfriendlyread")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRailgunFriendlyRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("railgunfriendlyend")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRailgunFriendlyEnd(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("eccardlock")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doEffectCardLock(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("emeraldtrade")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doEmeraldTrade(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("tradeclose")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doTradeClose(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("blastbonus")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doBlastBonus(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("anvilstar")
                .then(Commands.argument("dice", StringArg.string())
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doAnvilStar(ctx, StringArg.getString(ctx, "dice"),
                                StringArg.getString(ctx, "tag"), false);
                        }))
                        .then(Commands.literal("fresh")
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doAnvilStar(ctx, StringArg.getString(ctx, "dice"),
                                    StringArg.getString(ctx, "tag"), true);
                            }))))))
            .then(Commands.literal("anvilbags")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doAnvilBags(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("anvilclose")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doAnvilClose(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("komachicast")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doKomachiCast(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("komachirepeat")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doKomachiRepeat(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("komachicap")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doKomachiCap(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("komachicycle")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doKomachiCycle(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("komachicooldown")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doKomachiCooldown(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("komachiread")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doKomachiRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("nancycloak")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNancyCloak(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("nancyexpire")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNancyExpire(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("nancystate")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNancyState(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("nancyfall")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNancyFall(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("nancypearl")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNancyPearl(ctx, StringArg.getString(ctx, "tag"), true);
                    }))))
            .then(Commands.literal("nancypearlctrl")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNancyPearl(ctx, StringArg.getString(ctx, "tag"), false);
                    }))))
            .then(Commands.literal("nancypearlclose")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNancyPearlClose(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("decayflash")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doDecayFlash(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("decayclear")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doDecayClear(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("airbagprep")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doAirbagPrep(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("airbagkill")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doAirbagKill(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("airbaglethal")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doAirbagLethal(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("airbagread")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doAirbagRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("airbagnocharge")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doAirbagNoCharge(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("airbagreset")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doAirbagReset(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            // ── 2026-09-15 B2 追加：C1 电击手套轮次归零解除武装 / C4 末影骰保命清 GLOWING ──
            .then(Commands.literal("gloveround")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doGloveRound(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("endertotem")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("sub", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doEnderTotem(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "sub"));
                        })))))
            // ── 2026-09-15 追加：《恋的规则书》「仅首次进入世界发放一次」守卫读数 ──
            .then(Commands.literal("guidebook")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doGuidebook(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            // ── 2026-09-17(t18 门控收口)：选择器立牌「前置门控」门控 / 取消 / 确认三态读数 ──
            .then(Commands.literal("sggate")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSignGate(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            // ── 2026-09-25：游戏大师立牌(ren)「鼠鼠护盾」读数（REN-SHIELD-1.21.1）──
            .then(Commands.literal("renprep")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRenPrep(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("renunequip")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRenUnequip(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("rentimer")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRenTimer(ctx, StringArg.getString(ctx, "tag"), "");
                    }))
                    .then(Commands.argument("ticks", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doRenTimer(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "ticks"));
                        })))))
            .then(Commands.literal("rengrant")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRenGrant(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("renhit")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRenHit(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("renvoid")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRenVoid(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("renactive")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRenActive(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("renread")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRenRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            // 史莱姆立牌主动「治愈粘液」→ 目标选择器(2026-09-19 重写):prep / active / anchor 三连
            .then(Commands.literal("luluprep")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doLuluPrep(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("luluactive")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doLuluActive(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("luluanchor")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doLuluAnchor(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "name"));
                        })))))
            .then(Commands.literal("luluconfirm")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doLuluConfirm(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "name"));
                        })))))
            // ── 教主立牌(teru)「降神」+「狐光」(2026-09-27)────────────────────────
            .then(Commands.literal("teruprep")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doTeruPrep(ctx, StringArg.getString(ctx, "tag"), "");
                    }))
                    .then(Commands.argument("wm", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doTeruPrep(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "wm"));
                        })))))
            .then(Commands.literal("teruread")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("phase", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doTeruRead(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "phase"));
                        })))))
            .then(Commands.literal("terureg")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doTeruReg(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("terucast")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doTeruCast(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("terubot")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doTeruBot(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "name"));
                        })))))
            .then(Commands.literal("teruequipbot")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doTeruEquipBot(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "name"));
                        })))))
            .then(Commands.literal("terublessreal")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doTeruBlessReal(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "mode"),
                                    StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("terucastreal")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doTeruCastReal(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "name"));
                        })))))
            .then(Commands.literal("terureadreal")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("phase", StringArg.word())
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doTeruReadReal(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "phase"),
                                    StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("teruendreal")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doTeruEndReal(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "name"));
                        })))))
            .then(Commands.literal("terulinkreal")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("name", StringArg.word())
                        .then(Commands.argument("atk", StringArg.word())
                            .then(Commands.argument("def", StringArg.word())
                                .then(Commands.argument("base", StringArg.word())
                                    .then(Commands.argument("skip", StringArg.word())
                                        .then(Commands.argument("blessed", StringArg.word())
                                            .then(Commands.argument("layers", StringArg.word())
                                                .then(Commands.argument("wm", StringArg.word())
                                                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                                        return doTeruLinkReal(ctx, StringArg.getString(ctx, "tag"),
                                                            StringArg.getString(ctx, "name"),
                                                            StringArg.getString(ctx, "atk"),
                                                            StringArg.getString(ctx, "def"),
                                                            StringArg.getString(ctx, "base"),
                                                            StringArg.getString(ctx, "skip"),
                                                            StringArg.getString(ctx, "blessed"),
                                                            StringArg.getString(ctx, "layers"),
                                                            StringArg.getString(ctx, "wm"));
                                                    }))))))))))))
            .then(Commands.literal("terubless")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doTeruBless(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "mode"));
                        })))))
            .then(Commands.literal("terufx")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doTeruFx(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "name"));
                        })))))
            .then(Commands.literal("terudie")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doTeruDie(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "name"));
                        })))))
            .then(Commands.literal("teruguard")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doTeruGuard(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("terugate")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doTeruGate(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "name"));
                        })))))
            .then(Commands.literal("terudummy")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("which", StringArg.word())
                        .then(Commands.argument("type", StringArg.string())
                            .then(Commands.argument("dist", StringArg.word())
                                .then(Commands.argument("hp", StringArg.word())
                                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                        return doTeruDummy(ctx, StringArg.getString(ctx, "tag"),
                                            StringArg.getString(ctx, "which"),
                                            StringArg.getString(ctx, "type"),
                                            StringArg.getString(ctx, "dist"),
                                            StringArg.getString(ctx, "hp"));
                                    }))))))))
            
.then(Commands.literal("teruhitreal")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("which", StringArg.word())
                        .then(Commands.argument("times", StringArg.word())
                            .then(Commands.argument("name", StringArg.word())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doTeruHitReal(ctx, StringArg.getString(ctx, "tag"),
                                        StringArg.getString(ctx, "which"),
                                        StringArg.getString(ctx, "times"),
                                        StringArg.getString(ctx, "name"));
                                })))))))
            .then(Commands.literal("terucard")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("kind", StringArg.word())
                        .then(Commands.argument("n", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doTeruCard(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "kind"),
                                    StringArg.getString(ctx, "n"));
                            }))))))
            .then(Commands.literal("terudrop")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doTeruDrop(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("teruequip")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doTeruEquip(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "mode"));
                        })))))
            .then(Commands.literal("teruclear")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doTeruClear(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            // ── 2026-09-27:绿洲女王立牌(nardis)主动「女王特权」+ 被动「威压」游戏内取证 ──
            .then(Commands.literal("nardiprep")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiPrep(ctx, StringArg.getString(ctx, "tag"), "");
                    }))
                    .then(Commands.argument("clear", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doNardiPrep(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "clear"));
                        })))))
            .then(Commands.literal("nardiread")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("phase", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doNardiRead(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "phase"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doNardiRead(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "phase"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("nardiarm")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("atk_n", StringArg.word())
                        .then(Commands.argument("def_n", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doNardiArm(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "atk_n"), StringArg.getString(ctx, "def_n"), "curio");
                            }))
                            .then(Commands.argument("where", StringArg.word())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doNardiArm(ctx, StringArg.getString(ctx, "tag"),
                                        StringArg.getString(ctx, "atk_n"), StringArg.getString(ctx, "def_n"),
                                        StringArg.getString(ctx, "where"));
                                })))))))
            .then(Commands.literal("nardiarmclear")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiArmClear(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("nardicast")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiCast(ctx, StringArg.getString(ctx, "tag"), "", "");
                    }))
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doNardiCast(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "name"), "");
                        }))
                        .then(Commands.argument("cool", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doNardiCast(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "name"), StringArg.getString(ctx, "cool"));
                            }))))))
            .then(Commands.literal("nardigive")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("n", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doNardiGive(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "n"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doNardiGive(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "n"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("narditemp")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doNardiTemp(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "");
                        }))
                        .then(Commands.argument("clear", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doNardiTemp(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "clear"));
                            }))))))
            .then(Commands.literal("nardifoil")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiFoil(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("nardidrop")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiDrop(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("nardislot")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiSlot(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("nardiexpire")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiExpire(ctx, StringArg.getString(ctx, "tag"), "");
                    }))
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doNardiExpire(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "name"));
                        })))))
            .then(Commands.literal("nardinuclear")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiClearAll(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            // ── 新语义脚手架(M2/M3/M6;见 impl 块同名函数):
            //    nardicd    = **只**复位玩家级主动冷却(不碰锁定键/效果/临时牌)⇒ M2/M8 的前置
            //                 (契约要求复位冷却时**不要传 `reset`**,故用它而不是 `nardicast … self reset`);
            //    nardirecast = M2 一键取证:读旧剩余 → 内联复位冷却 → 真实释放 → 读新剩余,两侧同**一行**,
            //                 并挂 +5/+10 tick 的持续检查(`AP_<tag>_RECASTT5/T10`,证明重置未被 CLAMP 裁回);
            //    nardiuseup = 把身上临时牌**清到 0**(purgeAll;含装配栏)但**不碰效果** ⇒ M3 主判据
            //                 (新语义下「牌用光」**不得**再结束效果);`drive` 档额外显式驱动产品自检;
            //    nardiinv   = 主物品栏填充到「恰好剩 reserve 格」/ 清空 ⇒ M6 安全门前置
            //                 (阈值 = TemporaryCardUtil.MIN_FREE_SLOTS_TO_CAST = 2:
            //                  可用格 < 2 ⇒ 拒绝;= 2 ⇒ 允许;clear ⇒ 腾空后立刻可释放);
            //    nardilockback = 通用锁定硬上界脚手架 —— **当前无用例使用**(冻结机械已撤回),
            //                 保留给其它立牌/后续锁定类回归,详见 impl 块的函数注释。
            .then(Commands.literal("nardicd")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiCd(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("nardirecast")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiRecast(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("nardiuseup")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiUseUp(ctx, StringArg.getString(ctx, "tag"), "");
                    }))
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doNardiUseUp(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"));
                        })))))
            .then(Commands.literal("nardiinv")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doNardiInv(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "2");
                        }))
                        .then(Commands.argument("reserve", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doNardiInv(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "reserve"));
                            }))))))
            .then(Commands.literal("nardilockback")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiLockBack(ctx, StringArg.getString(ctx, "tag"), "keep");
                    }))
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doNardiLockBack(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"));
                        })))))
            .then(Commands.literal("nardidbg")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doNardiDbg(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("nardiguard")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doNardiGuard(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"));
                        })))))
            .then(Commands.literal("nardiequip")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("what", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doNardiEquip(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "what"));
                        })))))
            // ── 2026-09-27(第二批):立牌锁定「离线时钟漂移」修复的取证套件(signlag)──
            //    产品:新增服务端专用附件 `sign_active_lock_last_seen`,在 `tickSignActiveLock`
            //    最前面做 `gap = now − last_seen` 补偿(仅 gap>1;lock_end/grace_end 各自 >0 时才加),
            //    不读效果实例、不碰冷却、无 per-sign 代码。
            //      signreset  = 锁定态全量归零 + `last_seen` 置 0(基线;也是「无锁定时置 0」的显式构造);
            //      signprep   = **按 sign id 参数化**装立牌 + 基线(第 3 参可选写星光,供 parunan 前置,
            //                   探针内**不做** per-sign 分支);
            //      signcast   = 真实主动路径 `performSkillForCurio` + 锁定态读回(含 `gate_on`);
            //      signlag    = 核心:**写 last_seen → 驱动 tick → 读回**压在一次执行内(拆开会撞真实 tick);
            //                   mode = once(缺省) | double(同 tick 幂等) | triple(正常拍不漂移) | nowrite;
            //      signend    = 内部通道移除门控效果 ⇒ 锁定应正常结束并起冷却(L5);
            //      signexpiregrace = 把忍者宽限刻推到已过期 ⇒ 走两条既有出口(L6);
            //      signplayed = 写忍者「宽限期内出过效果牌」标记(L6 出口二);
            //      signstate  = 纯只读快照(等待真实 tick 后复查用)。
            .then(Commands.literal("signreset")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSignReset(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("signprep")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("signId", StringArg.string())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doSignPrep(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "signId"), "");
                        }))
                        .then(Commands.argument("starlight", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doSignPrep(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "signId"), StringArg.getString(ctx, "starlight"));
                            }))))))
            .then(Commands.literal("signcast")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSignCast(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("signslot")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSignSlot(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("slotput")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("slot", StringArg.word())
                        .then(Commands.argument("index", IntegerArg.integer(0, 8))
                            .then(Commands.argument("item", StringArg.string())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doSlotPut(ctx, StringArg.getString(ctx, "tag"),
                                        StringArg.getString(ctx, "slot"), IntegerArg.getInteger(ctx, "index"),
                                        StringArg.getString(ctx, "item"));
                                })))))))
            .then(Commands.literal("signlag")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("ticks", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doSignLag(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "ticks"), "");
                        }))
                        .then(Commands.argument("mode", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doSignLag(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "ticks"), StringArg.getString(ctx, "mode"));
                            }))))))
            .then(Commands.literal("signend")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSignEnd(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("signroundreset")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSignRoundReset(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("signexpiregrace")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSignExpireGrace(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("signplayed")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("val", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doSignPlayed(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "val"));
                        })))))
            .then(Commands.literal("signstate")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSignState(ctx, StringArg.getString(ctx, "tag"), "");
                    }))
                    .then(Commands.argument("mark", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doSignState(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mark"));
                        })))))
            // NARDIS-DISP-END(插入器用)
            // ── 2026-09-27:蛟龙立牌(mamushi)觉醒/真龙/转换/主动/冷却/撕咬/咆哮/守门游戏内取证 ──
            .then(Commands.literal("mamuclear")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doMamuClear(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("mamuprep")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doMamuPrep(ctx, StringArg.getString(ctx, "tag"), "");
                    }))
                    .then(Commands.argument("bot", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doMamuPrep(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "bot"));
                        })))))
            .then(Commands.literal("mamureg")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doMamuReg(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("mamuread")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("phase", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doMamuReadCmd(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "phase"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doMamuReadCmd(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "phase"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("mamuawake")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .then(Commands.argument("n", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doMamuAwake(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "n"), "");
                            }))
                            .then(Commands.argument("name", StringArg.word())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doMamuAwake(ctx, StringArg.getString(ctx, "tag"),
                                        StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "n"),
                                        StringArg.getString(ctx, "name"));
                                })))))))
            .then(Commands.literal("mamugive")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("giver", StringArg.word())
                        .then(Commands.argument("receiver", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doMamuGive(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "giver"), StringArg.getString(ctx, "receiver"));
                            }))))))
            .then(Commands.literal("mamubatch")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .then(Commands.argument("n1", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doMamuBatch(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"),
                                    StringArg.getString(ctx, "n1"), "-", "-");
                            }))
                            .then(Commands.argument("n2", StringArg.word())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doMamuBatch(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"),
                                        StringArg.getString(ctx, "n1"), StringArg.getString(ctx, "n2"), "-");
                                }))
                                .then(Commands.argument("n3", StringArg.word())
                                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                        return doMamuBatch(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"),
                                            StringArg.getString(ctx, "n1"), StringArg.getString(ctx, "n2"),
                                            StringArg.getString(ctx, "n3"));
                                    }))))))))
            .then(Commands.literal("mamuwatch")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doMamuWatch(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doMamuWatch(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("mamuform")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doMamuForm(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doMamuForm(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("mamuconv")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doMamuConv(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "", "");
                        }))
                        .then(Commands.argument("arg", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doMamuConv(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "arg"), "");
                            }))
                            .then(Commands.argument("name", StringArg.word())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doMamuConv(ctx, StringArg.getString(ctx, "tag"),
                                        StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "arg"),
                                        StringArg.getString(ctx, "name"));
                                })))))))
            .then(Commands.literal("mamucast")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doMamuCast(ctx, StringArg.getString(ctx, "tag"), "", "");
                    }))
                    .then(Commands.argument("a1", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doMamuCast(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "a1"), "");
                        }))
                        .then(Commands.argument("a2", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doMamuCast(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "a1"), StringArg.getString(ctx, "a2"));
                            }))))))
            .then(Commands.literal("mamucd")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doMamuCd(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"));
                        })))))
            .then(Commands.literal("mamucore")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doMamuCore(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"));
                        })))))
            .then(Commands.literal("mamujump")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doMamuJump(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("mamuroar")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doMamuRoar(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doMamuRoar(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("mamubite")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doMamuBite(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"));
                        })))))
            .then(Commands.literal("mamuguard")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doMamuGuard(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"));
                        })))))
            .then(Commands.literal("mamudie")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("keepinv", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doMamuDie(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "keepinv"));
                        })))))
            // MAMUSHI-DISP-END(插入器用)
            // ── 2026-09-27:风水师立牌(zhao)+ 符卡-福/祸 游戏内取证(双人用 Carpet /player bot)──
            .then(Commands.literal("zhauprep")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doZhaoPrep(ctx, StringArg.getString(ctx, "tag"), "");
                    }))
                    .then(Commands.argument("clear", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoPrep(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "clear"));
                        })))))
            .then(Commands.literal("zhauread")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("phase", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoRead(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "phase"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoRead(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "phase"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("zhaureg")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doZhaoReg(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("zhaogive")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("kind", StringArg.word())
                        .then(Commands.argument("n", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoGive(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "kind"), StringArg.getString(ctx, "n"), "");
                            }))
                            .then(Commands.argument("name", StringArg.word())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doZhaoGive(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "kind"), StringArg.getString(ctx, "n"), StringArg.getString(ctx, "name"));
                                })))))))
            .then(Commands.literal("zhaosethuo")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("n", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoSetHuo(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "n"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoSetHuo(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "n"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("zhaoclear")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doZhaoClear(ctx, StringArg.getString(ctx, "tag"), "");
                    }))
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoClear(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "name"));
                        })))))
            .then(Commands.literal("zhaocast")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoCast(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoCast(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("zhaogate")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoGate(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoGate(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("zhaobless")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoBless(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoBless(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("zhaosign")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("name", StringArg.word())
                        .then(Commands.argument("mode", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoSign(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "name"), StringArg.getString(ctx, "mode"));
                            }))))))
            .then(Commands.literal("zhaodice")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("dice", StringArg.word())
                        .then(Commands.argument("times", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoDice(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "dice"), StringArg.getString(ctx, "times"));
                            }))))))
            .then(Commands.literal("zhaodedup")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("dice", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoDedup(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "dice"));
                        })))))
            .then(Commands.literal("zhaoheal")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("amount", StringArg.word())
                        .then(Commands.argument("gap", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoHeal(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "amount"), StringArg.getString(ctx, "gap"), "");
                            }))
                            .then(Commands.argument("name", StringArg.word())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doZhaoHeal(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "amount"), StringArg.getString(ctx, "gap"), StringArg.getString(ctx, "name"));
                                })))))))
            .then(Commands.literal("zhaohuotick")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoHuoTick(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoHuoTick(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("zhaohuobox")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoHuoBox(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoHuoBox(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("zhaodrop")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doZhaoDrop(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("zhaocard")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("kind", StringArg.word())
                        .then(Commands.argument("target", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoCard(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "kind"), StringArg.getString(ctx, "target"), "", "");
                            }))
                            .then(Commands.argument("name", StringArg.word())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doZhaoCard(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "kind"), StringArg.getString(ctx, "target"), StringArg.getString(ctx, "name"), "");
                                }))
                                .then(Commands.argument("owner", StringArg.word())
                                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                        return doZhaoCard(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "kind"), StringArg.getString(ctx, "target"), StringArg.getString(ctx, "name"), StringArg.getString(ctx, "owner"));
                                    }))))))))
            .then(Commands.literal("zhaoplay")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("kind", StringArg.word())
                        .then(Commands.argument("target", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoPlay(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "kind"), StringArg.getString(ctx, "target"), "", "");
                            }))
                            .then(Commands.argument("name", StringArg.word())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doZhaoPlay(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "kind"), StringArg.getString(ctx, "target"), StringArg.getString(ctx, "name"), "");
                                }))
                                .then(Commands.argument("owner", StringArg.word())
                                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                        return doZhaoPlay(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "kind"), StringArg.getString(ctx, "target"), StringArg.getString(ctx, "name"), StringArg.getString(ctx, "owner"));
                                    }))))))))
            .then(Commands.literal("zhaohand")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("kind", StringArg.word())
                        .then(Commands.argument("hand", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoHand(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "kind"), StringArg.getString(ctx, "hand"));
                            }))))))
            .then(Commands.literal("zhaofx")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doZhaoFx(ctx, StringArg.getString(ctx, "tag"), "");
                    }))
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoFx(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "name"));
                        })))))
            .then(Commands.literal("zhaolife")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoLife(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoLife(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("zhaolink")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoLink(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), "");
                        }))
                        .then(Commands.argument("name", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doZhaoLink(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "name"));
                            }))))))
            .then(Commands.literal("zhaoseq")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doZhaoSeq(ctx, StringArg.getString(ctx, "tag"), "");
                    }))
                    .then(Commands.argument("name", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoSeq(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "name"));
                        })))))
            .then(Commands.literal("zhaofu")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("times", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doZhaoFuLoop(ctx, StringArg.getString(ctx, "tag"), StringArg.getString(ctx, "times"));
                        })))))
            .then(Commands.literal("zhaonuclear")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doZhaoClearAll(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("ssspawn")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("mode", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doSsSpawn(ctx, StringArg.getString(ctx, "tag"),
                                StringArg.getString(ctx, "mode"), "");
                        }))
                        .then(Commands.argument("entity", StringArg.string())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doSsSpawn(ctx, StringArg.getString(ctx, "tag"),
                                    StringArg.getString(ctx, "mode"), StringArg.getString(ctx, "entity"));
                            }))))))
            .then(Commands.literal("ssstar")
                .then(Commands.argument("tag", StringArg.word())
                    .then(Commands.argument("n", IntegerArg.integer(0, 32))
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doSsStar(ctx, StringArg.getString(ctx, "tag"),
                                IntegerArg.getInteger(ctx, "n"));
                        })))))
            .then(Commands.literal("ssfire")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSsFire(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("ssread")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSsRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("ssclear")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSsClear(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("ssgeom")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSsGeom(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("eliteread")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doEliteRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("sherryread")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doSherryRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("hannaread")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doHannaRead(ctx, StringArg.getString(ctx, "tag"));
                    }))))
    );
});
