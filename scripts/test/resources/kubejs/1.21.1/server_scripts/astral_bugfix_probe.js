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
    return rc;
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

ServerEvents.commandRegistry(event => {
    var Commands = event.commands;
    event.register(
        Commands.literal("astralprobe")
            // ── B6(2026-09-15):OP 只读前置 + 只读读数统一出口(dump) ──
            .then(Commands.literal("opprobe")
                .executes(ctx => guard(ctx, "OP", function () {
                    return opprobe(ctx);
                })))
            .then(Commands.literal("dumpstate")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return dumpState(ctx, StringArg.getString(ctx, "tag"));
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
    );
});
