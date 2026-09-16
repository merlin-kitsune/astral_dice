// ════════════════════════════════════════════════════════════════════════════
//  astral_probe.js —— 26.1.2 (NeoForge 26.1.2.109 / KubeJS 8.x) 探针
//
//  取证通道(唯一权威 = 聊天):命令上下文统一用 ctx.source.sendFailure(Component),
//  客户端 ChatComponent 会把它落到 logs/latest.log 的 `[CHAT] AP_...` 行;
//  mt_launch 每次启动前删除 latest.log → 天然是「本轮增量」。
//
//  ── 与 1.21.1 探针(scripts/test/resources/kubejs/1.21.1)的关系 ──────────────
//  26.1.2 是**迁移期**，本文件只覆盖工具链门控与迁移冒烟所需的最小子集:
//    /astralprobe diag <tag>       环境自检:模组类可见性 / 时间基准 / 注册表物品数
//    /astralprobe itemcount <tag>  只读:astral_dice 命名空间的注册物品数(应为 123)
//    /astralprobe opprobe          mt_launch 的 OP 前置闸门(AP_OP_PERM 固定格式)
//    /astralprobe dumpstate <tag>  只读:转调 /astralparty dump(B6 ③ 统一出口)
//    /astralprobe give <itemId> <count> <tag>
//    /astralprobe equipslot <slotId> <itemId> <tag>   Curios 槽位(证明 Curios 15 集成可用)
//    /astralprobe readstate <tag>  只读:附件读数 + 骰子饰品在位情况
//  1.21.1 的 4088 行完整回归探针(忍者/骇客/电磁炮/法伤口径等)尚未迁移到 26.1.2 ——
//  迁移完成后的回归批次再按本文件已固化的 API 形态逐条补齐(见 docs/compat-26.1.2-neoforge.md §8)。
//
//  ── 26.1.2 API 形态(与本仓产品代码同源,勿凭 1.21.1 记忆改写)────────────────
//   · ResourceLocation → **Identifier**(Identifier.parse(id))
//   · BuiltInRegistries.ITEM.get(Identifier) 返回 **Optional<Holder<Item>>**(1.21.1 直接返回 Item)
//   · 权限:`ctx.source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`
//     (1.21.1 的 `CommandSourceStack#hasPermission(int)` 已删除;产品侧命令用同一判据)
//   · `MinecraftServer#getProfilePermissions(NameAndId)`,`NameAndId` 由 GameProfile 构造
//   · `Commands#performPrefixedCommand` 返回 **void**(1.21.1 返回 int)⇒ 只报「已投递/异常」
//   · Curios 15:`CuriosApi.getCuriosInventory(LivingEntity)` 返回 `Optional<ICuriosItemHandler>`
//     (与 1.21.1 同为 Optional#get());`ICurioStacksHandler` 无 `grow`(用 setStackInSlot 直写)
// ════════════════════════════════════════════════════════════════════════════

var ComponentClass = Java.loadClass("net.minecraft.network.chat.Component");
var IdentifierClass = Java.loadClass("net.minecraft.resources.Identifier");
var BuiltInRegistries = Java.loadClass("net.minecraft.core.registries.BuiltInRegistries");
var ItemStackClass = Java.loadClass("net.minecraft.world.item.ItemStack");
var StringArg = Java.loadClass("com.mojang.brigadier.arguments.StringArgumentType");
var IntegerArg = Java.loadClass("com.mojang.brigadier.arguments.IntegerArgumentType");
var CuriosApi = Java.loadClass("top.theillusivec4.curios.api.CuriosApi");
var PermissionsClass = Java.loadClass("net.minecraft.server.permissions.Permissions");
var NameAndIdClass = Java.loadClass("net.minecraft.server.players.NameAndId");
var ModAttachments = Java.loadClass("com.merlinkitsune.astral_dice.component.ModAttachments");
var ModEffects = Java.loadClass("com.merlinkitsune.astral_dice.effect.ModEffects");

var MODID = "astral_dice";

// ── 输出 ──────────────────────────────────────────────────────────────────
function send(ctx, text) {
    try { ctx.source.sendFailure(ComponentClass.literal(text)); } catch (e) { /* 回显失败不影响判定 */ }
}

function exText(e) {
    var msg;
    try { msg = "" + e; } catch (x) { msg = "<unprintable>"; }
    try {
        if (e != null && typeof e.getStackTrace === "function") {
            var st = e.getStackTrace();
            var n = st.length < 3 ? st.length : 3;
            for (var i = 0; i < n; i++) msg += " @ " + st[i];
        }
    } catch (x2) { /* 忽略 */ }
    return ("" + msg).replace(/[\r\n]+/g, " ").substring(0, 400);
}

function guard(ctx, tag, fn) {
    try { return fn(); }
    catch (err) { send(ctx, "AP_" + tag + "_EX:" + exText(err)); return 0; }
}

// ── 基础读数 ──────────────────────────────────────────────────────────────
function nowTick(p) {
    try { return p.level.getLevelData().getGameTime(); } catch (e1) { }
    try { return p.level.getGameTime(); } catch (e2) { }
    try { return p.level.getServer().getTickCount(); } catch (e3) { }
    return -1;
}

/** Identifier 解析(26.1.2 形态);失败返回 null */
function parseId(id) {
    try { return IdentifierClass.parse(id); } catch (e) { return null; }
}

/** 注册表物品:26.1.2 返回 Optional<Holder<Item>> */
function resolveItem(itemId) {
    try {
        var opt = BuiltInRegistries.ITEM.get(parseId(itemId));
        if (opt == null || !opt.isPresent()) return null;
        return opt.get().value();
    } catch (e) { return null; }
}

function itemIdOf(stack) {
    try {
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "null" : ("" + key);
    } catch (e) { return "err:" + exText(e); }
}

/** astral_dice 命名空间的注册物品数(迁移口径:应为 123) */
function countModItems() {
    var n = 0;
    try {
        var keys = BuiltInRegistries.ITEM.keySet();
        var it = keys.iterator();
        while (it.hasNext()) {
            var k = it.next();
            if (("" + k.getNamespace()) === MODID) n++;
        }
    } catch (e) { return -1; }
    return n;
}

function curioHandler(player, slotId) {
    try {
        var opt = CuriosApi.getCuriosInventory(player);
        if (opt == null || !opt.isPresent()) return null;
        var h = opt.get().getStacksHandler(slotId);
        if (h == null || !h.isPresent()) return null;
        return h.get();
    } catch (e) { return null; }
}

/** 把物品放进 Curios 指定槽位(先清空再写,顺带走 onUnequip/onEquip 语义) */
function putInSlot(player, slotId, item, index) {
    var h = curioHandler(player, slotId);
    if (h == null) return "no_slot:" + slotId;
    var stacks = h.getStacks();
    if (index >= stacks.getSlots()) return "slot_overflow:" + slotId + ":" + stacks.getSlots();
    stacks.setStackInSlot(index, ItemStackClass.EMPTY);
    stacks.setStackInSlot(index, new ItemStackClass(item));
    return null;
}

/** 是否存在任意骰子饰品(只读)。
 *
 *  ⚠️ 2026-09-16 实测教训(KubeJS 8 + Curios 15,**移植 1.21.1 完整探针时必须注意**):
 *  **不要**用 `ICuriosItemHandler#findFirstCurio(...)` —— Curios 15 多了一个
 *  `findFirstCurio(Item)` 重载,而 KubeJS 8 的 `ItemWrapper` 类型包装器会替 Rhino 解析
 *  参数类型:传 JS 函数时它按 `Item` 重载去 wrap,抛
 *    `KubeRuntimeException: Failed to read item stack from Unknown: Could not parse input Unknown for item stack`
 *  且该异常**不会被 JS 侧 try/catch/我们的 guard 捕获**(它是 KubeJS 自己的运行时错误类型,
 *  直接冒泡成 Brigadier 的 "threw an exception")⇒ 读数全无、还查不到 AP_*_EX。
 *  故这里改为直接遍历槽位(只用 getStackInSlot/isEmpty/getItem,全部无重载歧义)。
 */
function hasDiceEquipped(player) {
    var h = curioHandler(player, "dice");
    if (h == null) return 0;
    try {
        var stacks = h.getStacks();
        var n = stacks.getSlots();
        for (var i = 0; i < n; i++) {
            var s = stacks.getStackInSlot(i);
            if (s == null || s.isEmpty()) continue;
            if (itemIdOf(s).indexOf(MODID + ":") === 0) return 1;
        }
        return 0;
    } catch (e) { return -1; }
}

// ── 命令实现 ──────────────────────────────────────────────────────────────
/** 维度 id(只读)。⚠️ Rhino 下 `Level#dimension()` 在 ServerLevel 上是**属性**而非方法
 *  (2026-09-16 实测:`Cannot call property dimension ... It is not a function`);
 *  两种写法都试,都失败就报 unknown。 */
function dimId(p) {
    try { return "" + p.level.dimension.identifier(); } catch (e1) { }
    try { return "" + p.level.dimension().identifier(); } catch (e2) { }
    return "unknown";
}

/** 环境自检:类可见性 + 时间基准 + 注册表计数(迁移期最有用的一条) */
function doDiag(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var att = 0, eff = 0, heal = -1;
    try { heal = ModAttachments.getHealingPoints(p); att = 1; } catch (e1) { att = -1; }
    try { eff = (ModEffects.KING_POWER != null) ? 1 : 0; } catch (e2) { eff = -1; }
    var items = countModItems();
    var dq = ctx.source.permissions().hasPermission(PermissionsClass.COMMANDS_GAMEMASTER) ? 1 : 0;
    send(ctx, "AP_" + tag + "_DIAG:mod=1:att=" + att + ":eff=" + eff + ":items=" + items
        + ":tick=" + nowTick(p) + ":op=" + dq + ":dim=" + dimId(p));
    send(ctx, "AP_" + tag + "_DIAG_DONE");
    return 1;
}

function doItemCount(ctx, tag) {
    var n = countModItems();
    var total = -1;
    try { total = BuiltInRegistries.ITEM.size(); } catch (e) { total = -1; }
    send(ctx, "AP_" + tag + "_ITEMCOUNT:" + MODID + "=" + n + ":total=" + total);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** mt_launch 的 OP 前置闸门:格式固定 AP_OP_PERM:has2=..:level=..:src=..:dump=.. */
function opprobe(ctx) {
    var p = ctx.source.getPlayerOrException();
    var has2 = -1, level = -1, src = "none";
    try {
        has2 = ctx.source.permissions().hasPermission(PermissionsClass.COMMANDS_GAMEMASTER) ? 1 : 0;
        src = "cmdsource";
    } catch (e1) { /* 落到下面的数值级 */ }
    try {
        // 26.1.2 的 `getProfilePermissions` 收 `NameAndId` 记录(UUID, String)。
        // 直接传 GameProfile 的便捷构造在 Rhino 下不总可用,故先试记录构造器,
        // 再退回 GameProfile 构造;两者都失败时 level 保持 -1(mt_launch 只判 has2)。
        var prof = p.getGameProfile();
        var na = new NameAndIdClass(p.getUUID(), prof.name());
        var perms = p.level.getServer().getProfilePermissions(na);
        // 26.1.2 的权限级是枚举(ALL/MODERATORS/GAMEMASTERS/ADMINS/OWNERS),
        // ordinal 与原版 0..4 数值级同序(GAMEMASTERS→2),故直接报 ordinal。
        level = perms.level().ordinal();
        src = (src === "none") ? "profile" : (src + "+profile");
    } catch (e2) { /* level 保持 -1 */ }

    var dumpRc = "na";
    try {
        var server = p.level.getServer();
        server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "astralparty dump");
        dumpRc = "sent";
    } catch (e3) { dumpRc = "ERR:" + exText(e3); }

    send(ctx, "AP_OP_PERM:has2=" + has2 + ":level=" + level + ":src=" + src + ":dump=" + dumpRc);
    send(ctx, "AP_OP_DONE");
    return 1;
}

function doDumpState(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    try {
        p.level.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), "astralparty dump");
    } catch (e) {
        send(ctx, "AP_" + tag + "_ERR:dump:" + exText(e));
        return 0;
    }
    send(ctx, "AP_" + tag + "_DUMP_SENT");
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doGive(ctx, itemId, count, tag) {
    var p = ctx.source.getPlayerOrException();
    var item = resolveItem(itemId);
    if (item == null) { send(ctx, "AP_" + tag + "_ERR:unknown_item:" + itemId); return 0; }
    try {
        var stack = new ItemStackClass(item, count);
        p.getInventory().add(stack);
    } catch (e) {
        send(ctx, "AP_" + tag + "_ERR:give:" + exText(e));
        return 0;
    }
    send(ctx, "AP_" + tag + "_GIVE:" + itemId + ":" + count);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

function doEquipSlot(ctx, slotId, itemId, tag) {
    var p = ctx.source.getPlayerOrException();
    var item = resolveItem(itemId);
    if (item == null) { send(ctx, "AP_" + tag + "_ERR:unknown_item:" + itemId); return 0; }
    var err = putInSlot(p, slotId, item, 0);
    if (err != null) { send(ctx, "AP_" + tag + "_ERR:" + err); return 0; }
    send(ctx, "AP_" + tag + "_EQUIP:" + slotId + ":" + itemId);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

/** 只读:附件读数 + 骰子饰品在位 + 槽位规模 */
function doReadState(ctx, tag) {
    var p = ctx.source.getPlayerOrException();
    var heal = -1, pages = -1, cd = -1;
    try { heal = ModAttachments.getHealingPoints(p); } catch (e1) { }
    try { pages = ModAttachments.getRinPages(p); } catch (e2) { }
    try { cd = ModAttachments.getSignActiveCooldownEnd(p); } catch (e3) { }
    var diceSlots = -1, chipSlots = -1;
    try { var dh = curioHandler(p, "dice"); diceSlots = (dh == null) ? -1 : dh.getStacks().getSlots(); } catch (e4) { }
    try { var ch = curioHandler(p, "chip"); chipSlots = (ch == null) ? -1 : ch.getStacks().getSlots(); } catch (e5) { }
    send(ctx, "AP_" + tag + "_STATE:healing=" + heal + ":pages=" + pages + ":signcd=" + cd
        + ":dice=" + hasDiceEquipped(p) + ":diceSlots=" + diceSlots + ":chipSlots=" + chipSlots);
    send(ctx, "AP_" + tag + "_DONE");
    return 1;
}

ServerEvents.commandRegistry(event => {
    var Commands = event.commands;
    event.register(
        Commands.literal("astralprobe")
            .then(Commands.literal("diag")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doDiag(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("itemcount")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doItemCount(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("opprobe")
                .executes(ctx => guard(ctx, "OP", function () { return opprobe(ctx); })))
            .then(Commands.literal("dumpstate")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doDumpState(ctx, StringArg.getString(ctx, "tag"));
                    }))))
            .then(Commands.literal("give")
                .then(Commands.argument("itemId", StringArg.string())
                    .then(Commands.argument("count", IntegerArg.integer(1, 64))
                        .then(Commands.argument("tag", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doGive(ctx, StringArg.getString(ctx, "itemId"),
                                    IntegerArg.getInteger(ctx, "count"), StringArg.getString(ctx, "tag"));
                            }))))))
            .then(Commands.literal("equipslot")
                .then(Commands.argument("slotId", StringArg.word())
                    .then(Commands.argument("itemId", StringArg.string())
                        .then(Commands.argument("tag", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doEquipSlot(ctx, StringArg.getString(ctx, "slotId"),
                                    StringArg.getString(ctx, "itemId"), StringArg.getString(ctx, "tag"));
                            }))))))
            .then(Commands.literal("readstate")
                .then(Commands.argument("tag", StringArg.word())
                    .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                        return doReadState(ctx, StringArg.getString(ctx, "tag"));
                    }))))
    );
});
