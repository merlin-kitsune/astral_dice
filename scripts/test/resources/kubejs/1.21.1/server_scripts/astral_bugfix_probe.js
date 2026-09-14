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
//   · 本文件由 temp/_adapt_probe_1211.py 从 1.20.1 版生成,请勿手工分叉修改
//
//  ── 命令一览(用例 mt_case.py 依赖这些名字与参数顺序) ──────────────────────
//    /astralprobe diag <tag>                          环境自检:API 可见性 + 时间基准 + 雷击计数
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
var CuriosApi = Java.loadClass("top.theillusivec4.curios.api.CuriosApi");
var ModAttachments = Java.loadClass("com.merlinkitsune.astral_dice.component.ModAttachments");
var ModEffects = Java.loadClass("com.merlinkitsune.astral_dice.effect.ModEffects");
var ModEffectRemoval = Java.loadClass("com.merlinkitsune.astral_dice.event.ModEffectRemoval");
var EmpowerManager = Java.loadClass("com.merlinkitsune.astral_dice.item.EmpowerManager");

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
    try { mob.damage(src, 1.0); if (ok()) return { api: "mob_damage", dealt: dealt() }; }
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
    function clear(holder) { try { p.removeEffect(holder); } catch (e) { /* 忽略 */ } }
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
 * 打开真实铁砧界面:放一个铁砧方块 + 走方块自带的 MenuProvider(与真人右键铁砧同源)。
 * 方块路径不可用时退回「直接构造 AnvilMenu」(同一原版菜单类,仅 ContainerLevelAccess 为空),
 * 走哪条由 AP_<TAG>_SRC 读数标明。
 */
function anvilOpenMenu(p) {
    anvilEnsureNoOpenMenu(p);
    var src = "direct";
    var provider = null;
    try {
        var pos = p.blockPosition().relative(p.getDirection());
        p.level.setBlockAndUpdate(pos, BlocksClass.ANVIL.defaultBlockState());
        provider = p.level.getBlockState(pos).getMenuProvider(p.level, pos);
    } catch (e1) { provider = null; }
    if (provider == null) {
        provider = new SimpleMenuProviderClass(function (id, inv, pl) {
            return new AnvilMenuClass(id, inv);
        }, ComponentClass.literal("astral_probe_anvil"));
    } else {
        src = "block";
    }
    var res = p.openMenu(provider);
    if (res == null || !res.isPresent()) return null;
    return { menu: p.containerMenu, src: src };
}

/** 真实搬运/取件:与原版服务端处理 ServerboundContainerClickPacket 同一入口 */
function anvilClick(p, menu, slot, button) {
    menu.clicked(slot, button, ClickTypeClass.PICKUP, p);
    try { menu.broadcastChanges(); } catch (e) { /* 同步失败不影响服务端权威状态 */ }
}

/** 关界面:铁砧输入槽的剩余材料由 ItemCombinerMenu#removed → clearContainer 退回物品栏 */
function anvilCloseMenu(p) {
    var how = "unavailable";
    try { p.closeContainer(); how = "closeContainer"; }
    catch (e1) {
        try { p.containerMenu.removed(p); how = "menuRemoved"; } catch (e2) { /* 都不可用 */ }
    }
    return how;
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
    send(ctx, "AP_" + tag + "_SRC:" + opened.src);

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
    send(ctx, "AP_" + tag + "_CLOSE:" + anvilCloseMenu(p));
    send(ctx, "AP_" + tag + "_INV_AFTER_CLOSE:" + anvilCountItem(p, ANVIL_STAR_COIN));
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
    send(ctx, "AP_" + tag + "_BAG_CLOSE:" + anvilCloseMenu(p));

    var ds = anvilFindSlot(p, ANVIL_TEST_DICE);
    var fs = anvilFindSlot(p, ANVIL_STAR_COIN);
    if (ds < 0 || fs < 0) { send(ctx, "AP_" + tag + "_ERR:lost_after_close:" + ds + ":" + fs); return 0; }
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
    send(ctx, "AP_" + tag + "_CLOSE:" + anvilCloseMenu(p));
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

var GameplayConstantsClass = Java.loadClass("com.merlinkitsune.astral_dice.component.GameplayConstants");
var EffectCardPeriodClass = Java.loadClass("com.merlinkitsune.astral_dice.item.card.EffectCardPeriod");
var BaseSignItemClass = Java.loadClass("com.merlinkitsune.astral_dice.item.sign.BaseSignItem");
var NancyLuSignItemClass = Java.loadClass("com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem");
var HealingManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.item.HealingManager");
var MarkManagerClass = Java.loadClass("com.merlinkitsune.astral_dice.item.MarkManager");
var MobEffectsClass = Java.loadClass("net.minecraft.world.effect.MobEffects");
var ThrownEnderpearlClass = Java.loadClass("net.minecraft.world.entity.projectile.ThrownEnderpearl");

var DESC_INVIS = "effect.minecraft.invisibility";
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
    ModAttachments.setCandyChipPlayBonusActive(player, false);
    ModAttachments.setSatellitePlayBonusActive(player, false);
    ModAttachments.setLivingPageCycleBonus(player, 0);
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
 * 正常释放:归零基线 → 按主动。断言链 = 附件 0→1、出牌上限 +1、主动冷却开始。
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
    send(ctx, "AP_" + tag + "_AFTER:max=" + maxAfter + ":extra=" + extraAfter
        + ":cd=" + (cd > 0 ? 1 : 0));
    send(ctx, "AP_" + tag + "_DELTA:max=+" + (maxAfter - maxBefore)
        + ":extra=" + extraAfter + ":cd=" + (cd > 0 ? "started" : "none"));
    var ok = (extraAfter === 1) && (maxAfter === maxBefore + 1) && (cd > 0);
    send(ctx, "AP_" + tag + "_RELEASED:" + (ok ? 1 : 0));
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/**
 * 本周期已生效时再按主动:不得释放,且**不得进入冷却**。
 * 构造:附件先置 1(本周期已生效),并把主动冷却结束时刻置为「已过期但非 0」——
 * 既能越过 performSkill 的冷却分支(走到 handleUse 的 used 守卫),
 * 又能用「该字段是否被重新写成未来时刻」判定冷却有没有被起算。
 */
function doKomachiRepeat(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var err = equipSign(p, KOMACHI_SIGN_ID);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    clearExtraPlayEffects(p);
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
        // 未封顶的正向对照:必须释放(附件 0→1、上限 +1)且不得超过常量封顶
        ModAttachments.setEffectCardBonusPlays(p, 0);
        ModAttachments.setSignActiveCooldownEnd(p, 0);
        BaseSignItemClass.performSkillForCurio(p);
        var ex2 = EffectCardPeriodClass.getBonusPlays(p);
        var cd2 = signCooldownRemaining(p);
        var mx2 = EffectCardPeriodClass.getMaxAllowed(p);
        capOk = (ex2 === 1 && cd2 > 0 && mx2 === fill + 1
            && mx2 <= GameplayConstantsClass.MAX_EFFECT_CARD_PLAYS) ? 1 : 0;
        send(ctx, "AP_" + tag + "_BRANCH:uncapped_release");
        send(ctx, "AP_" + tag + "_AFTER:extra=" + ex2 + ":cd=" + (cd2 > 0 ? 1 : 0) + ":max=" + mx2);
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

/** 读「周期归零」结果:出牌轮一次性加成附件 effect_card_bonus_plays 与出牌数/冷却结束时刻都必须被清除 */
function doKomachiRead(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
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
 */
function applyFallDamage(p, amount) {
    var src = p.level.damageSources().fall();
    var before = p.getHealth();
    var tried = [];
    function dropped() { return p.getHealth() < before; }
    try { p.hurt(src, amount); if (dropped()) return "hurt"; }
    catch (e1) { tried.push("hurt:" + exText(e1)); }
    try { p.causeFallDamage(amount, 1.0, src); if (dropped()) return "causeFallDamage"; }
    catch (e2) { tried.push("causeFallDamage:" + exText(e2)); }
    try { p.damage(src, amount); if (dropped()) return "damage"; }
    catch (e3) { tried.push("damage:" + exText(e3)); }
    return "none[" + tried.join(" | ") + "]";
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

/** 确定几何:清出正前方口袋 + 脚下石台 + 正前方 3 格处的接珠柱(珍珠必须有确定落点) */
function nancyPearlArena(p) {
    var base = p.blockPosition();
    for (var dx = -1; dx <= 1; dx++) {
        for (var dy = 0; dy <= 2; dy++) {
            for (var dz = 0; dz <= 3; dz++) {
                try { p.level.setBlockAndUpdate(base.offset(dx, dy, dz), BlocksClass.AIR.defaultBlockState()); }
                catch (e1) { /* 忽略 */ }
            }
        }
    }
    for (var dx2 = -1; dx2 <= 1; dx2++) {
        for (var dz2 = 0; dz2 <= 3; dz2++) {
            try { p.level.setBlockAndUpdate(base.offset(dx2, -1, dz2), BlocksClass.STONE.defaultBlockState()); }
            catch (e2) { /* 忽略 */ }
        }
    }
    try { p.level.setBlockAndUpdate(base.offset(0, 1, 3), BlocksClass.STONE.defaultBlockState()); }
    catch (e3) { /* 忽略 */ }
    try { p.level.setBlockAndUpdate(base.offset(0, 2, 3), BlocksClass.STONE.defaultBlockState()); }
    catch (e4) { /* 忽略 */ }
    return base;
}

/** 在玩家正前方 1 格、眼睛高度生成一枚真珍珠,朝 +Z(与 aimPositiveZ 的朝向一致)飞出 */
function nancySpawnPearl(p) {
    var pearl = new ThrownEnderpearlClass(p.level, p);
    pearl.setPos(p.getX(), p.getY() + 1.5, p.getZ() + 1.0);
    pearl.setDeltaMovement(0.0, 0.0, 0.8);
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
    pearlWatchZ0 = p.getZ();
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
    send(ctx, "AP_" + tag + "_SETUP:heal=" + heal + ":mark=" + mark + ":emp=" + emp);
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
    if (pearlWatchRemaining <= 0) return;
    pearlWatchRemaining--;
    try {
        var p = pearlWatchPlayer;
        if (p == null) { pearlWatchRemaining = 0; return; }
        var pearl = pearlWatchPearl;
        var gone = false;
        try { gone = (pearl == null) || pearl.isRemoved() || !pearl.isAlive(); }
        catch (e0) { gone = false; }
        if (!gone) {
            if (pearlWatchRemaining <= 0) emitTo(p, "AP_" + pearlWatchTag + "_TIMEOUT");
            return;
        }
        var now = nowTick(p);
        var until = ModAttachments.getNancyLuEnderPearlImmuneUntil(p);
        var remain = until > 0 ? (until - now) : 0;
        var drop = Math.round((p.getMaxHealth() - p.getHealth()) * 100) / 100;
        // 传送位移:证明走的是原版 onHit(命中方块 → teleportTo 后才会 hurt),
        // 而不是「珍珠撞到玩家本体」那种不产生摔落伤害的退化路径。
        var tel = ((p.getZ() - pearlWatchZ0) > 1.0) ? 1 : 0;
        var api = "n/a";
        if (pearlWatchExpectImmune) {
            // 免疫相位:窗口内再施加一次 FALL 伤害,必须依旧毫无反馈
            api = applyFallDamage(p, FALL_DAMAGE_AMOUNT);
        }
        var ok;
        if (pearlWatchExpectImmune) {
            ok = (remain > 0) && (tel === 1) && (p.getHealth() >= p.getMaxHealth())
                && (p.hurtTime === 0) && (p.invulnerableTime === 0) && (p.hurtMarked === false);
        } else {
            // hurtMarked 由 ServerEntity#sendChanges 在同一 tick 内消费并复位,
            // 观察窗读到它时已不可靠 → 对照组只用「确实掉血 + hurtTime > 0」判定,
            // marked 值仍打印出来作为证据。
            ok = (remain === 0) && (tel === 1) && (p.getHealth() < p.getMaxHealth())
                && (p.hurtTime > 0);
        }
        emitTo(p, "AP_" + pearlWatchTag + "_PEARL:window=" + remain + ":tel=" + tel
            + ":drop=" + drop + ":hurt=" + p.hurtTime + ":invul=" + p.invulnerableTime
            + ":marked=" + p.hurtMarked + ":api=" + api);
        emitTo(p, "AP_" + pearlWatchTag + "_OK:" + (ok ? 1 : 0));
        emitTo(p, "AP_" + pearlWatchTag + "_DONE");
        pearlWatchRemaining = 0;
    } catch (err) {
        emitTo(pearlWatchPlayer, "AP_" + pearlWatchTag + "_EX:" + exText(err));
        pearlWatchRemaining = 0;
    }
});


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
    // 中立(牛)与友方(已驯服狼,主人=玩家)分列僵尸左右各 1.5 格,同样落在箱内。
    // 清场(2026-09-14 实测):自然刷新的苦力怕一旦落进雷击箱,会被劈成高压苦力怕并爆炸,
    // 污染所有差值读数(实测 bolt_delta=2、非靶实体凭空掉血),故先清掉附近的苦力怕再摆靶。
    runCmd(ctx, "kill @e[type=minecraft:creeper]");
    var enemy = spawnDummy(p, "minecraft:spider", 2);
    if (enemy == null) { send(ctx, "AP_" + tag + "_ERR:spawn_failed_enemy"); return 0; }
    var neutral = spawnDummy(p, "minecraft:polar_bear", 2);
    var friendly = spawnDummy(p, "minecraft:wolf", 2);
    var turtle = spawnDummy(p, "minecraft:turtle", 2);
    var villager = spawnDummy(p, "minecraft:villager", 2);
    if (neutral == null || friendly == null || turtle == null || villager == null) {
        send(ctx, "AP_" + tag + "_ERR:spawn_failed_side"); return 0;
    }
    try { placeAt(neutral, p.getX() + 1.5, p.getY(), p.getZ() + 2.0); } catch (e3) { /* 忽略 */ }
    try { placeAt(friendly, p.getX() - 1.5, p.getY(), p.getZ() + 2.0); } catch (e4) { /* 忽略 */ }
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
    runCmd(ctx, "data merge entity @e[type=minecraft:polar_bear,limit=1] {AngerTime:1200}");
    runCmd(ctx, "data merge entity @e[type=minecraft:wolf,limit=1] {AngerTime:1200}");
    function angerOf(e) { return (e == null) ? "?" : (e.isAlive() ? "alive" : "dead"); }
    function angryFlag(e) {
        try { return "" + e.isAngry(); } catch (e1) { return "ERR:" + exText(e1); }
    }
    send(ctx, "AP_" + tag + "_ANGER_PRE:neutral=" + angerOf(neutral) + ":friendly=" + angerOf(friendly)
        + ":nAngry=" + angryFlag(neutral) + ":fAngry=" + angryFlag(friendly));
    var mergeN = runCmd(ctx, "data merge entity @e[type=minecraft:polar_bear,limit=1] {AngerTime:1200}");
    var mergeF = runCmd(ctx, "data merge entity @e[type=minecraft:wolf,limit=1] {AngerTime:1200}");
    send(ctx, "AP_" + tag + "_ANGER_POST:mergeN=" + mergeN + ":mergeF=" + mergeF
        + ":nAngry=" + angryFlag(neutral) + ":fAngry=" + angryFlag(friendly));

    var boltBase = boltSpawnCount;
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
    /** 经 KubeJS 可见的伤害 API 施加(实测 1.21.1 `mob.attack(DamageSource,float)` 可用) */
    function applyDamage(ent, src, amount) {
        try { ent.attack(src, amount); return "attack"; } catch (ea) { /* 试下一个 */ }
        try { ent.damage(src, amount); return "damage"; } catch (eb) { /* 试下一个 */ }
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
    try { p.removeEffect(ModEffects.DICE_BLESSING); } catch (e2) { /* 忽略 */ }
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
    try { st.player.removeEffect(ModEffects.DICE_BLESSING); } catch (e2) { /* 忽略 */ }
    try { clearCurioSlots(st.player, "stand"); } catch (e3) { /* 忽略 */ }
    try { st.target.discard(); } catch (e4) { /* 忽略 */ }
    try { st.near.discard(); } catch (e5) { /* 忽略 */ }
    try { st.armd.discard(); } catch (e6) { /* 忽略 */ }
    try { st.far.discard(); } catch (e7) { /* 忽略 */ }
    fenState = null;
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

ServerEvents.commandRegistry(event => {
    var Commands = event.commands;
    event.register(
        Commands.literal("astralprobe")
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
    );
});
