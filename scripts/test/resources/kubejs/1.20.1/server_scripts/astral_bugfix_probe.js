// ════════════════════════════════════════════════════════════════════════════
//  astral_bugfix_probe.js —— 1.20.1 (Forge) 五 bug 冒烟取证探针
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
//  ── 命令一览(用例 mt_case.py 依赖这些名字与参数顺序) ──────────────────────
//    /astralprobe diag <tag>                          环境自检:API 可见性 + 时间基准 + 雷击计数
//    /astralprobe status <tag>
//    /astralprobe setup <diceId> <weaponId> <tag>     骰子入 dice 槽 0 + 主手换武器
//    /astralprobe equipslot <slotId> <itemId> <tag>
//    /astralprobe attack <entityTypeId> <tag>
//    /astralprobe railtest <tag>
//    /astralprobe cdguard <tag>                        冷却期内二次攻击守卫(不得再触发雷击)
//    /astralprobe charge <n> <tag>
//    /astralprobe empower <n> <tag>
//    /astralprobe empowerclear <tag>
//    /astralprobe blessingclear <tag>
//    /astralprobe railguncd <tag>
//    /astralprobe decayinterval <tag>
//    /astralprobe decaydue <tag>
//    /astralprobe boltvis <ticks> <tag>
//    /astralprobe watch <ticks> <tag>
//    /astralprobe hud <ticks> <tag>
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
var EntityClass = Java.loadClass("net.minecraft.world.entity.Entity");
var LivingEntityClass = Java.loadClass("net.minecraft.world.entity.LivingEntity");
var LightningBoltClass = Java.loadClass("net.minecraft.world.entity.LightningBolt");
var BuiltInRegistries = Java.loadClass("net.minecraft.core.registries.BuiltInRegistries");
var ResourceLocation = Java.loadClass("net.minecraft.resources.ResourceLocation");
var StringArg = Java.loadClass("com.mojang.brigadier.arguments.StringArgumentType");
var IntegerArg = Java.loadClass("com.mojang.brigadier.arguments.IntegerArgumentType");
var ItemStack = Java.loadClass("net.minecraft.world.item.ItemStack");
var InteractionHand = Java.loadClass("net.minecraft.world.InteractionHand");
var CuriosApi = Java.loadClass("top.theillusivec4.curios.api.CuriosApi");
var ModAttachments = Java.loadClass("com.merlinkitsune.astral_dice.component.ModAttachments");
var ModEffects = Java.loadClass("com.merlinkitsune.astral_dice.effect.ModEffects");
var ModEffectRemoval = Java.loadClass("com.merlinkitsune.astral_dice.event.ModEffectRemoval");
var EmpowerManager = Java.loadClass("com.merlinkitsune.astral_dice.item.EmpowerManager");
var EmpowerEffect = Java.loadClass("com.merlinkitsune.astral_dice.effect.EmpowerEffect");
var ChargeManager = Java.loadClass("com.merlinkitsune.astral_dice.item.ChargeManager");

var DESC_BLESSING = "effect.astral_dice.dice_blessing";
var DESC_EMPOWER = "effect.astral_dice.empower";
var DESC_CHARGE = "effect.astral_dice.charge";
var RAILGUN_CHIP = "astral_dice:railgun_chip";
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

/** 电磁炮攻击力加成读口(装备且充能 ≥6 → +5):证明「筹码在槽里」而无需碰两版本不同的 Curios API */
function railgunAttackBonus(p) {
    try {
        var c = Java.loadClass("com.merlinkitsune.astral_dice.item.chip.RailgunChipItem");
        return c.getAttackBonus(p);
    } catch (e) {
        return -1;
    }
}

function loadDamageNumberMessage() {
    try { return Java.loadClass("com.merlinkitsune.astral_dice.network.ModNetwork$DamageNumberMessage"); }
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
        var map = opt.resolve().get().getCurios();
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
        var handlerOpt = opt.resolve().get().getStacksHandler("dice");
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
        var handlerOpt = opt.resolve().get().getStacksHandler("chip");
        if (handlerOpt == null || !handlerOpt.isPresent()) return -1;
        return handlerOpt.get().getStacks().getSlots();
    } catch (e) {
        return -1;
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
    var handlerOpt = opt.resolve().get().getStacksHandler("chip");
    if (handlerOpt == null || !handlerOpt.isPresent()) return "no_chip_slot";
    var handler = handlerOpt.get();
    var cur = handler.getStacks().getSlots();
    if (cur < need) handler.grow(need - cur);
    return null;
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

function railgunCooldownRemaining(p) {
    try {
        var end = ModAttachments.getRailgunCooldownEnd(p);
        if (end <= 0) return 0;
        var now = nowTick(p);
        return now < 0 ? -1 : (end - now);
    } catch (e) {
        return -1;
    }
}

function railgunPending(p) {
    var sched = loadScheduler();
    if (sched == null) return -1;
    try { return sched.pendingRemainingTicks(p.level); } catch (e) { return -1; }
}

function putInSlot(player, slotId, itemStack, index) {
    var opt = CuriosApi.getCuriosInventory(player);
    if (opt == null || !opt.isPresent()) return "no_curios";
    var handlerOpt = opt.resolve().get().getStacksHandler(slotId);
    if (handlerOpt == null || !handlerOpt.isPresent()) return "no_slot:" + slotId;
    var stacks = handlerOpt.get().getStacks();
    if (index >= stacks.getSlots()) return "slot_overflow:" + slotId + ":" + stacks.getSlots();
    stacks.setStackInSlot(index, ItemStack.EMPTY);
    stacks.setStackInSlot(index, new ItemStack(itemStack.getItem()));
    return null;
}

function resolveItem(itemId) {
    var item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
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
 * 靶子必须站定:bug5 的延迟雷击按**触发瞬间目标位置**结算 3 格范围,
 * 目标一旦走开就会漏掉,导致「修复无效」的假结论。
 */
function spawnDummy(p, typeId, dist) {
    var type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(typeId));
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
    try { mob.damage(src, 1.0); if (ok()) return { api: "mob_damage", dealt: dealt() }; }
    catch (e4) { tried.push("mob_damage:" + exText(e4)); }
    throw new Error("no_melee_api:" + tried.join(" | "));
}

// ── 雷击生成计数(主判据)────────────────────────────────────────────────────
// 雷击实体存活仅数 tick,靠 AABB 单点采样必然漏检(上一轮 bug4/bug5 因此全判 0)。
// 改为挂钩「实体进入世界」事件做累积计数:窗口内增量 ≥1 即证明雷击**真的生成过**,
// 对「探针自己造的」(bug4 渲染取证)与「生产代码延迟触发的」(bug5 延迟验证)同样成立。
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
    probe("levelClass", function () { return p.level.getClass().getName(); });
    probe("getGameTime", function () { return p.level.getGameTime(); });
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

function doStatus(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var now = nowTick(p);
    send(ctx, "AP_" + tag + "_NOWTICK:" + now + ":" + nowTickSource);
    send(ctx, "AP_" + tag + "_DICE:" + diceSlotItemId(p));
    send(ctx, "AP_" + tag + "_MAINHAND:" + itemIdOf(p.getMainHandItem()));
    var bless = findEffect(p, DESC_BLESSING);
    send(ctx, "AP_" + tag + "_BLESSING:" + (bless != null ? "1" : "0") + ":"
        + (bless != null ? bless.getAmplifier() : -1) + ":"
        + (bless != null ? bless.getDuration() : -1));
    var empower = findEffect(p, DESC_EMPOWER);
    var decayAt = ModAttachments.getEmpowerDecayAt(p);
    var decayIn = (empower != null && decayAt > 0 && now >= 0) ? (decayAt - now) : -1;
    send(ctx, "AP_" + tag + "_EMPOWER:" + EmpowerEffect.getStacks(p) + ":" + decayIn);
    // 效果实例时长 = 面板/悬停提示显示的倒计时来源(原实现为 Integer.MAX_VALUE → 面板永不倒数)
    send(ctx, "AP_" + tag + "_EMPOWER_DUR:" + (empower != null ? empower.getDuration() : -1));
    // 粒子开关:0 = 不产生原版药水粒子(充能/赋能已禁用粒子)
    send(ctx, "AP_" + tag + "_EMPOWER_VIS:" + effectVis(p, DESC_EMPOWER));
    send(ctx, "AP_" + tag + "_CHARGE:" + ChargeManager.getStacks(p));
    send(ctx, "AP_" + tag + "_CHARGE_VIS:" + effectVis(p, DESC_CHARGE));
    send(ctx, "AP_" + tag + "_RAILGUN_CD:" + railgunCooldownRemaining(p));
    send(ctx, "AP_" + tag + "_RAILGUN_PENDING:" + railgunPending(p));
    send(ctx, "AP_" + tag + "_CHIPSLOTS:" + chipSlotCount(p));
    send(ctx, "AP_" + tag + "_LIGHTNING:" + countLightning(p, 16));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doSetup(ctx, diceId, weaponId, tag) {
    var p = ctx.source.getPlayerOrException();
    var diceItem = resolveItem(diceId);
    if (diceItem == null) { send(ctx, "AP_" + tag + "_ERR:unknown_dice:" + diceId); return 0; }
    var weaponItem = resolveItem(weaponId);
    if (weaponItem == null) { send(ctx, "AP_" + tag + "_ERR:unknown_weapon:" + weaponId); return 0; }
    var err = putInSlot(p, "dice", new ItemStack(diceItem), 0);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weaponItem));
    // 回读真实装配结果:避免"命令返回了但状态没变"的假通过
    send(ctx, "AP_" + tag + "_SETUP:" + diceId + ":" + weaponId);
    send(ctx, "AP_" + tag + "_DICE:" + diceSlotItemId(p));
    send(ctx, "AP_" + tag + "_MAINHAND:" + itemIdOf(p.getMainHandItem()));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doEquipSlot(ctx, slotId, itemId, tag) {
    var p = ctx.source.getPlayerOrException();
    var item = resolveItem(itemId);
    if (item == null) { send(ctx, "AP_" + tag + "_ERR:unknown_item:" + itemId); return 0; }
    if (slotId === "chip") {
        var slotErr = ensureChipSlot(p, CHIP_SLOT_MIN);
        if (slotErr != null) { send(ctx, "AP_" + tag + "_ERR:" + slotErr); return 0; }
    }
    var err = putInSlot(p, slotId, new ItemStack(item), 0);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    send(ctx, "AP_" + tag + "_EQUIP:" + slotId + ":" + itemId);
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

function doRailTest(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    // 预置:清冷却 + 清空后恰好补 6 层充能(使 CHARGE_AFTER 断言确定)
    ModAttachments.setRailgunCooldownEnd(p, 0);
    ChargeManager.removeAll(p);
    ChargeManager.addStacks(p, 6);
    // chip 槽生产上按骰子星级动态增长(基础骰子 → 0 槽)→ 测试侧先补齐槽位
    var slotErr = ensureChipSlot(p, CHIP_SLOT_MIN);
    if (slotErr != null) { send(ctx, "AP_" + tag + "_ERR:" + slotErr); return 0; }
    var chip = resolveItem(RAILGUN_CHIP);
    if (chip == null) { send(ctx, "AP_" + tag + "_ERR:unknown_chip:" + RAILGUN_CHIP); return 0; }
    var putErr = putInSlot(p, "chip", new ItemStack(chip), 0);
    if (putErr != null) { send(ctx, "AP_" + tag + "_ERR:" + putErr); return 0; }
    send(ctx, "AP_" + tag + "_CHIP:ok");
    send(ctx, "AP_" + tag + "_CHIPSLOTS:" + chipSlotCount(p));

    // 基线必须在攻击**之前**取:延迟雷击由服务端 tick 边界队列在 20 tick 后生成,
    // 窗口内首个新增雷击就是这次攻击的落点,据此量出**真实延迟**(见 rgWatch)。
    var boltBase = boltSpawnCount;
    var mob = spawnDummy(p, "minecraft:zombie", 3);
    if (mob == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed"); return 0; }
    send(ctx, "AP_" + tag + "_AIM:2");
    var hit = meleeHit(p, mob);
    send(ctx, "AP_" + tag + "_MELEE:" + hit.api + ":dealt=" + hit.dealt);

    // 同一 tick 内立刻采样:这是区分"真 1 秒延迟"与"延迟塌缩"的判据
    send(ctx, "AP_" + tag + "_DELAY:" + railgunPending(p));
    send(ctx, "AP_" + tag + "_LIGHTNING_NOW:" + countLightning(p, 16));
    send(ctx, "AP_" + tag + "_CD_AFTER:" + railgunCooldownRemaining(p));
    send(ctx, "AP_" + tag + "_CHARGE_AFTER:" + ChargeManager.getStacks(p));

    // 延迟量测窗口(120 tick):首个新增雷击相对本次攻击的 tick 偏移
    // → 真 1 秒 = 20/21 tick;塌缩为 0 或来自上一用例的残留雷击会给出 0~3。
    rgWatchTag = tag;
    rgWatchBase = boltBase;
    rgWatchStart = nowTick(p);
    rgWatchRemaining = 120;
    send(ctx, "AP_" + tag + "_RG_WATCH:start:" + boltBase);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ── 冷却守卫验证:冷却期内再次命中敌对目标**不得**再触发雷击 ─────────────────
// 判据全部与天气无关(确定性强):
//   · 充能不被消耗 —— 冷却分支在 ChargeManager.consume 之前就 return
//   · 调度队列无待触发雷击(pendingRemainingTicks == -1)
//   · 同 tick 雷击实体增量为 0(boltSpawnCount 基线差)
//   · 冷却剩余未被重置(前后差值只应等于本命令自身耗时)
function doCdGuard(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    // 清天气:雷暴会自造雷击实体,污染雷击计数的负向断言(此项失败不影响其它判据)
    var weather = "skip";
    try { p.level.setWeatherParameters(6000, 0, false, false); weather = "clear"; } catch (e0) { weather = "err"; }
    send(ctx, "AP_" + tag + "_WEATHER:" + weather);

    var cdBefore = railgunCooldownRemaining(p);
    send(ctx, "AP_" + tag + "_CD_BEFORE:" + cdBefore);
    if (cdBefore <= 0) { send(ctx, "AP_" + tag + "_ERR:not_on_cooldown:" + cdBefore); return 0; }

    // 充能恢复为恰好 6 层:冷却期攻击不应消耗它
    ChargeManager.removeAll(p);
    ChargeManager.addStacks(p, 6);
    send(ctx, "AP_" + tag + "_CHARGE_BEFORE:" + ChargeManager.getStacks(p));
    // 「筹码真的装在槽里 + 充能 ≥6」的生产读口(不用 Curios API:两版本 API 形态不同)
    send(ctx, "AP_" + tag + "_BONUS_BEFORE:" + railgunAttackBonus(p));

    var boltBase = boltSpawnCount;
    var mob = spawnDummy(p, "minecraft:zombie", 3);
    if (mob == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed"); return 0; }
    var hit = meleeHit(p, mob);
    send(ctx, "AP_" + tag + "_MELEE:" + hit.api + ":dealt=" + hit.dealt);

    var chargeAfter = ChargeManager.getStacks(p);
    var pending = railgunPending(p);
    var boltDelta = boltSpawnCount - boltBase;
    var cdAfter = railgunCooldownRemaining(p);
    send(ctx, "AP_" + tag + "_CDGUARD:" + chargeAfter + ":" + pending + ":" + boltDelta
        + ":" + railgunAttackBonus(p));
    send(ctx, "AP_" + tag + "_CD_AFTER:" + cdAfter);
    send(ctx, "AP_" + tag + "_CD_DELTA:" + (cdBefore - cdAfter));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doCharge(ctx, n, tag) {
    var p = ctx.source.getPlayerOrException();
    ChargeManager.addStacks(p, n);
    send(ctx, "AP_" + tag + "_CHARGE:" + ChargeManager.getStacks(p));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doEmpower(ctx, n, tag) {
    var p = ctx.source.getPlayerOrException();
    send(ctx, "AP_" + tag + "_EMPOWER_STEP:before");
    EmpowerManager.addStacks(p, n);
    send(ctx, "AP_" + tag + "_EMPOWER_STEP:added");
    var stacks = EmpowerEffect.getStacks(p);
    send(ctx, "AP_" + tag + "_EMPOWER_STEP:read:" + stacks);
    var decayAt = ModAttachments.getEmpowerDecayAt(p);
    var now = nowTick(p);
    send(ctx, "AP_" + tag + "_EMPOWER:" + stacks + ":"
        + ((decayAt > 0 && now >= 0) ? (decayAt - now) : -1));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doEmpowerClear(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    EmpowerManager.removeAll(p);
    send(ctx, "AP_" + tag + "_EMPOWER:" + EmpowerEffect.getStacks(p) + ":-1");
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doBlessingClear(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    ModEffectRemoval.remove(p, ModEffects.DICE_BLESSING.get());
    send(ctx, "AP_" + tag + "_BLESSING:0:-1:-1");
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

// 赋能递减间隔常量:直接读生产代码,验证"每 0:30"规格(600 tick)
function doDecayInterval(ctx, tag) {
    send(ctx, "AP_" + tag + "_DECAY_INTERVAL:" + EmpowerManager.DECAY_INTERVAL_TICKS);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// 把赋能递减计时器置为"已到期":驱动链(PlayerTickEvents 每 20 tick → EmpowerManager.tick
// → EmpowerEffect.consumeOne)应在 ≤1 秒内真实减 1 层 —— 无需等 30 秒
function doDecayDue(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var now = nowTick(p);
    send(ctx, "AP_" + tag + "_DECAYDUE_STEP:before:" + now);
    ModAttachments.setEmpowerDecayAt(p, now > 0 ? now : 1);
    send(ctx, "AP_" + tag + "_DECAYDUE_STEP:set");
    var stacks = EmpowerEffect.getStacks(p);
    send(ctx, "AP_" + tag + "_EMPOWER:" + stacks + ":0");
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ── 渲染验证:每 tick 生成 1 枚 visualOnly(无伤害/不引燃)雷击实体,持续 N tick ──
var boltVisRemaining = 0;
var boltVisTag = "S";
var boltVisPos = null;

function spawnVisualBoltAt(p, pos) {
    try {
        var type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(BOLT_TYPE_ID));
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

function doBoltVis(ctx, ticks, tag) {
    var p = ctx.source.getPlayerOrException();
    // 朝向**只在命令时钉一次**:每 tick 重复 teleport 会让摄像机抖动并刷屏位置包
    var pos = aimPositiveZ(p, 6);
    boltVisPos = pos;
    var ok = spawnVisualBoltAt(p, pos);
    boltVisRemaining = ok ? ticks : 0;
    boltVisTag = tag;
    send(ctx, "AP_" + tag + "_AIM:" + pos.aimed);
    send(ctx, "AP_" + tag + "_BOLT:" + (ok ? "1" : "0") + ":" + ticks);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ── HUD 隔离验证:生成 1 只僵尸,每 tick 直发一次伤害跳字包 ─────────────────
// (跳字存活数十 tick)持续 N tick —— 期间任意截图都应能看到跳字,且与骰战/赐福
// 是否触发**无关**。这样 bug1 的两个症状可被分别定位。
// 靶子直接持有实体引用(不再按 id 回查):避免目标死亡/移除后 getEntity 返回 null
// 导致的 HUD_ABORT 误报。靶子设为无 AI + 无敌 + 持久化,保证窗口内必定存活。
var hudRemaining = 0;
var hudTag = "H";
var hudMob = null;
var hudSent = 0;

function doHud(ctx, ticks, tag) {
    var p = ctx.source.getPlayerOrException();
    var type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("minecraft:zombie"));
    var mob = type.create(p.level);
    if (mob == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed"); return 0; }
    var pos = aimPositiveZ(p, 4);
    send(ctx, "AP_" + tag + "_AIM:" + pos.aimed);
    placeAt(mob, pos.x, pos.y, pos.z);
    try { mob.setNoAi(true); } catch (e1) { /* 忽略 */ }
    try { mob.setInvulnerable(true); } catch (e2) { /* 忽略 */ }
    try { mob.setPersistenceRequired(); } catch (e3) { /* 忽略 */ }
    p.level.addFreshEntity(mob);
    hudMob = mob;
    hudRemaining = ticks;
    hudTag = tag;
    hudSent = 0;
    var dn = loadDamageNumberMessage();
    send(ctx, "AP_" + tag + "_HUD:" + mob.getId() + ":" + ticks + ":" + (dn != null ? "1" : "0"));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// ── 雷击窗口观测:统计窗口内**新增**雷击实体数(主判据见 boltSpawnCount 说明)────
// 用途:bug4「雷击实体确实进入世界」与 bug5「1 秒后雷击真的落下」的服务端确定性证据。
var boltWatchRemaining = 0;
var boltWatchTag = "W";
var boltWatchMax = 0;
var boltWatchBase = 0;

// 电磁炮延迟量测窗口:窗口内首个新增雷击相对攻击时刻的 tick 偏移(= 真实延迟)
var rgWatchTag = "";
var rgWatchBase = 0;
var rgWatchStart = 0;
var rgWatchRemaining = 0;

function doWatch(ctx, ticks, tag) {
    var p = ctx.source.getPlayerOrException();
    boltWatchTag = tag;
    boltWatchRemaining = ticks;              // ticks = 最长窗口:见到「新增」雷击即刻定论
    boltWatchBase = boltSpawnCount;          // 基线:只有比基线更多的雷击才算事件,避免旧计数假通过
    boltWatchMax = 0;
    send(ctx, "AP_" + tag + "_WATCH:start:" + ticks + ":base=" + boltWatchBase);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

// 渲染/事件窗口驱动:仅在 boltvis / hud / watch 期间活跃,非活跃时零开销。
// 整体 try/catch —— tick 回调抛错会被 KubeJS 吞掉,必须显式落标记才能定位。
var tickErrorReported = false;
ServerEvents.tick(event => {
    if (boltVisRemaining <= 0 && hudRemaining <= 0 && boltWatchRemaining <= 0 && rgWatchRemaining <= 0) return;
    try {
        var players = event.server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            boltVisRemaining = 0; hudRemaining = 0; boltWatchRemaining = 0; rgWatchRemaining = 0;
            return;
        }
        var p = players.get(0);

        if (boltVisRemaining > 0) {
            var bpos = boltVisPos != null
                ? boltVisPos
                : { x: p.getX() + 0.0, y: p.getY() + 0.0, z: p.getZ() + 6.0 };
            spawnVisualBoltAt(p, bpos);
            boltVisRemaining--;
            if (boltVisRemaining <= 0) {
                emitTo(p, "AP_" + boltVisTag + "_BOLT_END");
            }
        }

        if (boltWatchRemaining > 0) {
            var seen = boltSpawnCount - boltWatchBase;
            if (seen > boltWatchMax) boltWatchMax = seen;
            boltWatchRemaining--;
            // 见到「比基线更多」的雷击即刻定论(不必等满窗口),保证 bug5 快速判正;
            // 窗口耗尽仍为 0 才报超时,并把绝对值一并落盘便于定位。
            if (boltWatchMax > 0) {
                emitTo(p, "AP_" + boltWatchTag + "_LIGHTNING_MAX:" + boltWatchMax);
                boltWatchRemaining = 0;
            } else if (boltWatchRemaining <= 0) {
                emitTo(p, "AP_" + boltWatchTag + "_LIGHTNING_MAX:0");
                emitTo(p, "AP_" + boltWatchTag + "_WATCH_TIMEOUT");
            }
        }

        if (rgWatchRemaining > 0) {
            if (boltSpawnCount > rgWatchBase) {
                emitTo(p, "AP_" + rgWatchTag + "_DELAY_MEASURED:" + (nowTick(p) - rgWatchStart));
                rgWatchRemaining = 0;
            } else {
                rgWatchRemaining--;
                if (rgWatchRemaining <= 0) {
                    emitTo(p, "AP_" + rgWatchTag + "_DELAY_TIMEOUT");
                }
            }
        }

        if (hudRemaining > 0) {
            var target = hudMob;
            var alive = false;
            try { alive = target != null && target.isAlive(); } catch (e0) { alive = target != null; }
            var dn = loadDamageNumberMessage();
            if (!alive || dn == null) {
                hudRemaining = 0;
                emitTo(p, "AP_" + hudTag + "_HUD_ABORT:"
                    + (!alive ? "no_entity" : "no_packet_class"));
            } else {
                dn.send(target, 777, 0xFF5555);
                hudSent++;
                hudRemaining--;
                if (hudRemaining <= 0) {
                    emitTo(p, "AP_" + hudTag + "_HUD_END:" + hudSent);
                }
            }
        }
    } catch (err) {
        if (!tickErrorReported) {
            tickErrorReported = true;
            emitTo(players.get(0), "AP_TICK_EX:" + exText(err));
        }
        boltVisRemaining = 0; hudRemaining = 0; boltWatchRemaining = 0; rgWatchRemaining = 0;
    }
});

ServerEvents.commandRegistry(event => {
    var Commands = event.commands;
    event.register(
        Commands.literal("astralprobe")
            .then(Commands.literal("diag")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doDiag(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("status")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doStatus(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("setup")
                .then(Commands.argument("dice", StringArg.string())
                    .then(Commands.argument("weapon", StringArg.string())
                        .then(Commands.argument("tag", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doSetup(ctx, StringArg.getString(ctx, "dice"),
                                    StringArg.getString(ctx, "weapon"), StringArg.getString(ctx, "tag"));
                            }))))))
            .then(Commands.literal("equipslot")
                .then(Commands.argument("slot", StringArg.word())
                    .then(Commands.argument("item", StringArg.string())
                        .then(Commands.argument("tag", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doEquipSlot(ctx, StringArg.getString(ctx, "slot"),
                                    StringArg.getString(ctx, "item"), StringArg.getString(ctx, "tag"));
                            }))))))
            .then(Commands.literal("attack")
                .then(Commands.argument("type", StringArg.string())
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doAttack(ctx, StringArg.getString(ctx, "type"),
                                StringArg.getString(ctx, "tag"));
                        })))))
            .then(Commands.literal("railtest")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRailTest(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("cdguard")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doCdGuard(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("charge")
                .then(Commands.argument("n", IntegerArg.integer(1, 64))
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doCharge(ctx, IntegerArg.getInteger(ctx, "n"),
                                StringArg.getString(ctx, "tag"));
                        })))))
            .then(Commands.literal("empower")
                .then(Commands.argument("n", IntegerArg.integer(1, 20))
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doEmpower(ctx, IntegerArg.getInteger(ctx, "n"),
                                StringArg.getString(ctx, "tag"));
                        })))))
            .then(Commands.literal("empowerclear")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doEmpowerClear(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("blessingclear")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doBlessingClear(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("railguncd")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doRailgunCleared(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("decayinterval")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doDecayInterval(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("decaydue")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doDecayDue(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("boltvis")
                .then(Commands.argument("ticks", IntegerArg.integer(1, 400))
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doBoltVis(ctx, IntegerArg.getInteger(ctx, "ticks"),
                                StringArg.getString(ctx, "tag"));
                        })))))
            .then(Commands.literal("watch")
                .then(Commands.argument("ticks", IntegerArg.integer(1, 400))
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doWatch(ctx, IntegerArg.getInteger(ctx, "ticks"),
                                StringArg.getString(ctx, "tag"));
                        })))))
            .then(Commands.literal("hud")
                .then(Commands.argument("ticks", IntegerArg.integer(1, 400))
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doHud(ctx, IntegerArg.getInteger(ctx, "ticks"),
                                StringArg.getString(ctx, "tag"));
                        })))))
    );
});
