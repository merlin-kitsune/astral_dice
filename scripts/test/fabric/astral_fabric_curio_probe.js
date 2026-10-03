// ════════════════════════════════════════════════════════════════════════════
//  astral_fabric_curio_probe.js —— fabric-1.20.1 线「饰品后端」取证探针
//
//  目的：验证 compat/curios 门面在 **Trinkets** 与 **Accessories** 两个后端下行为等价
//        （2026-10-01 用户下达的「Trinkets 和 Accessories 实机验证」第二阶段）。
//
//  ── 为什么不能沿用生产线那支 astral_bugfix_probe.js ─────────────────────────
//   1) 它绑定 `top.theillusivec4.curios.api.CuriosApi`（Forge/NeoForge 的 Curios）；
//      fabric 侧的同类门面是 `com.merlinkitsune.astral_dice.compat.curios.CuriosApi`。
//   2) 它的读数出口是**聊天**（`ctx.source.sendFailure` → 客户端 ChatComponent →
//      latest.log 的 `[CHAT] AP_...`）；本台是**无客户端的专用服务端**，聊天没有落点。
//      故本探针的权威出口是 `console.info`（KubeJS → slf4j → `run/server/logs/latest.log`），
//      `sendFailure` 只作为 RCON 同步回显的**附带**通道（驱动方不依赖它）。
//   3) 它用 `ctx.source.getPlayerOrException()` 取玩家；本台由 RCON 驱动（执行者是控制台，
//      没有实体）⇒ 本探针**把玩家名当参数**，经 `PlayerList#getPlayerByName` 解析。
//      名字由 Carpet 的 `/player <name> spawn` 造出来的假玩家提供（实体类型 = minecraft:player，
//      槽位数据按实体类型绑定，故与真人同路）。
//
//  ── 命令一览（驱动方 = ft_inject.ps1 --channel rcon）───────────────────────
//    /astralfab env                                只读：两条通道是否在场
//    /astralfab groups   <player>                 只读：全部槽位组及槽数
//    /astralfab state    <player> <tag>           只读：骰子/筹码栏/闸门/背包 全量快照
//    /astralfab equip    <player> <slot> <item> <tag>   走产品入口 CurioSlotUtil#tryAutoEquip
//    /astralfab unequip  <player> <slot> <index> <tag>  直接清空某槽位（模拟玩家取下）
//    /astralfab clear    <player> <slot> <tag>          清空整组
//    /astralfab fillchip <player> <n> <tag>             往 chip 栏 0..n-1 塞 n 枚**互不相同**的筹码
//    /astralfab refresh  <player> <tag>                 调产品入口 DiceCurioItem#refreshChipSlotCount
//    /astralfab enforce  <player> <tag>                 调产品入口 DiceCurioItem#enforceNoDiceState
//    /astralfab clearinv <player> <tag>                 清空玩家背包（隔离「筹码是否退回背包」）
//
//  ── 判据形状 ───────────────────────────────────────────────────────────────
//    AP_<tag>_PROVIDER:  trinkets=0|1 accessories=0|1 main=<trinkets|accessories|both|none>
//    AP_<tag>_GROUPS:    <name>=<slots>,...            (按名排序)
//    AP_<tag>_DICE:      item=<id>|empty
//    AP_<tag>_CHIP:      slots=N mods=[<uuid>=<amt>,...] items=[<i>:<id>x<count>,...]
//    AP_<tag>_GATE:      hasDice=0|1                   (= CurioSlotUtil#hasDiceEquipped)
//    AP_<tag>_INV:       total=<n> chips=<k> bag=[<id>x<count>,...]
//    AP_<tag>_EQUIP:     slot=.. item=.. result=<..>
//    AP_<tag>_POST_*:    改动型子命令执行后的同名快照（CHIP / INV）
//
//  ── Rhino 兼容约束（沿用生产线探针的三条实测结论）────────────────────────
//   1) 顶层变量一律 `var`（`const` 会被提升到脚本全局，多次回调报 redeclaration）；
//   2) id 参数一律用 `StringArgumentType.string()`（`word()` 会吞掉冒号）；
//   3) 执行体一律包 `guard()`，异常落成 `AP_<tag>_EX:...`，否则被 brigadier 吞成「意外错误」。
//   4) **不用 `java.util.Optional` 的 `resolve()` 惯用法**：本探针改走
//      `CuriosApi#getCuriosMap`（直接返回 `Map<String, ICurioStacksHandler>`，无 Optional），
//      把「Optional 展开在 Rhino 下是否可用」这个与本任务无关的变量彻底排除。
// ════════════════════════════════════════════════════════════════════════════

var ComponentClass = Java.loadClass("net.minecraft.network.chat.Component");
var BuiltInRegistries = Java.loadClass("net.minecraft.core.registries.BuiltInRegistries");
var ResourceLocation = Java.loadClass("net.minecraft.resources.ResourceLocation");
var StringArg = Java.loadClass("com.mojang.brigadier.arguments.StringArgumentType");
var IntegerArg = Java.loadClass("com.mojang.brigadier.arguments.IntegerArgumentType");
var ItemStack = Java.loadClass("net.minecraft.world.item.ItemStack");
// ⚠️ fabric 侧的门面（不是 top.theillusivec4.curios.api.CuriosApi）
var CuriosApi = Java.loadClass("com.merlinkitsune.astral_dice.compat.curios.CuriosApi");
var CurioSlotUtil = Java.loadClass("com.merlinkitsune.astral_dice.item.CurioSlotUtil");
var DiceCurioItem = Java.loadClass("com.merlinkitsune.astral_dice.item.dice.DiceCurioItem");
var ModCompat = Java.loadClass("com.merlinkitsune.astral_dice.init.ModCompatibilityCheck");

/** 筹码栏绝对尺寸修饰符的 UUID（与 DiceCurioItem#CHIP_SLOT_MODIFIER 同值；
 *  读数里出现它 = 本模组已接管该栏尺寸；出现 Curios legacy UUID = 旧累加器残留）。 */
var CHIP_MOD_UUID = "a5d1c9e2-6f34-4b7a-8c21-0e5d9b3f7a64";
var LEGACY_MOD_UUID = "0b0eabbd-4220-4e9f-bafb-34100da2bd7e";

/** fillchip 用的清单：必须**互不相同**（产品入口有「同种饰品不可重复装备」的限制）。 */
var FILL_CHIPS = [
    "astral_dice:flashlight_chip",
    "astral_dice:cutter_chip",
    "astral_dice:cutter_blade_chip",
    "astral_dice:scope_chip",
    "astral_dice:eagle_scope_chip",
    "astral_dice:medkit_emergency_chip",
    "astral_dice:medkit_complete_chip",
    "astral_dice:vitamin_pill_chip"
];

// ── 输出 ──────────────────────────────────────────────────────────────────
/** 权威出口：KubeJS console → run/<side>/logs/latest.log（无客户端也能落盘） */
function out(key, text) {
    try { console.info(key + ": " + text); } catch (e) { /* 忽略 */ }
}

/** 附带出口：命令上下文回显（RCON 驱动时由 ft_inject 同步打印） */
function send(ctx, text) {
    try { ctx.source.sendFailure(ComponentClass.literal(text)); } catch (e) { /* 忽略 */ }
}

/** 单行读数：key 形如 "<TAG>_CHIP"，落成 `AP_<key>: <val>` */
function line(ctx, key, val) {
    var s = "AP_" + key + ": " + val;
    try { console.info(s); } catch (e) { /* 忽略 */ }
    send(ctx, s);
    return s;
}

/** 异常 → 单行文本（含前若干栈帧），便于直接定位到具体调用 */
function exText(e) {
    var msg;
    try { msg = "" + e; } catch (x) { msg = "<unprintable>"; }
    try {
        if (e != null && typeof e.getStackTrace === "function") {
            var st = e.getStackTrace();
            var n = st.length < 4 ? st.length : 4;
            for (var i = 0; i < n; i++) msg += " @ " + st[i];
        }
    } catch (x2) { /* 忽略 */ }
    return ("" + msg).replace(/[\r\n]+/g, " ").substring(0, 600);
}

/** 异常守卫：任何执行体抛错都落成 AP_<tag>_EX:... 而不是被 brigadier 吞成「意外错误」 */
function guard(ctx, tag, fn) {
    try {
        return fn();
    } catch (err) {
        line(ctx, tag + "_EX", exText(err));
        return 0;
    }
}

// ── 基础解析 ──────────────────────────────────────────────────────────────
function resolveItem(itemId) {
    try {
        var item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
        return item;
    } catch (e) {
        return null;
    }
}

function itemIdOf(stack) {
    try {
        if (stack == null || stack.isEmpty()) return "empty";
        return "" + BuiltInRegistries.ITEM.getKey(stack.getItem());
    } catch (e) {
        return "<err:" + e + ">";
    }
}

/** 按名解析在线玩家（含 Carpet 假玩家 —— 它经 PlayerList#placeNewPlayer 入表，实测于 jar 字节码） */
function findPlayer(ctx, name) {
    try {
        return ctx.source.getServer().getPlayerList().getPlayerByName(name);
    } catch (e) {
        return null;
    }
}

// ── 读数原语 ──────────────────────────────────────────────────────────────
/** 全部槽位组 → "name=slots,..."（按名排序，便于 A/B 两后端逐字对比） */
function groupsText(player) {
    try {
        var map = CuriosApi.getCuriosMap(player);
        if (map == null) return "<null-map>";
        var out = [];
        var it = map.keySet().iterator();
        while (it.hasNext()) {
            var k = "" + it.next();
            var h = map.get(k);
            var n = "?";
            try { n = h.getSlots(); } catch (e1) {
                try { n = h.getStacks().getSlots(); } catch (e2) { n = "<err>"; }
            }
            out.push(k + "=" + n);
        }
        out.sort();
        return out.length === 0 ? "<empty>" : out.join(",");
    } catch (e) {
        return "<err:" + exText(e) + ">";
    }
}

function handlerOf(player, slotId) {
    try {
        var map = CuriosApi.getCuriosMap(player);
        if (map == null) return null;
        return map.get(slotId);
    } catch (e) {
        return null;
    }
}

/** 筹码栏决定性读数：槽数 + 槽位修饰符归属 + 槽内容 */
function chipText(player) {
    try {
        var h = handlerOf(player, "chip");
        if (h == null) return "no_chip_group";
        var stacks = h.getStacks();
        var n = stacks.getSlots();
        var mods = [];
        try {
            var m = h.getModifiers();
            var it = m.keySet().iterator();
            while (it.hasNext()) {
                // ⚠️ 必须保留**原始 key 对象**去取值:Map 的键是 java.util.UUID,
                //    用 `"" + key` 得到的字符串查 Map.get 恒为 null(2026-10-01 实测踩中,
                //    读数一度全是 `=?`)。
                var keyObj = it.next();
                var k = "" + keyObj;
                var mod = m.get(keyObj);
                var amt = "?";
                if (mod != null) {
                    try { amt = mod.getAmount(); } catch (e1) {
                        try { amt = mod.amount(); } catch (e2) {
                            try { amt = "" + mod; } catch (e3) { amt = "?"; }
                        }
                    }
                } else {
                    amt = "<null-mod>";
                }
                var mark = "";
                if (k === CHIP_MOD_UUID) mark = "*";
                if (k === LEGACY_MOD_UUID) mark = "!legacy";
                mods.push(k + "=" + amt + mark);
            }
        } catch (e3) { mods.push("<mods-err:" + exText(e3) + ">"); }
        mods.sort();
        var items = [];
        for (var i = 0; i < n; i++) {
            try {
                var s = stacks.getStackInSlot(i);
                if (s != null && !s.isEmpty()) items.push(i + ":" + itemIdOf(s) + "x" + s.getCount());
            } catch (e4) { items.push(i + ":<err>"); }
        }
        return "slots=" + n + " mods=[" + mods.join(",") + "] items=[" + items.join(",") + "]";
    } catch (e) {
        return "<err:" + exText(e) + ">";
    }
}

function diceText(player) {
    try {
        var h = handlerOf(player, "dice");
        if (h == null) return "no_dice_group";
        var stacks = h.getStacks();
        if (stacks.getSlots() <= 0) return "no_slot";
        return itemIdOf(stacks.getStackInSlot(0));
    } catch (e) {
        return "<err:" + exText(e) + ">";
    }
}

/** 背包内的物品统计（用于验证「强制收缩把筹码退回物品栏」） */
function invText(player) {
    try {
        var inv = player.getInventory();
        var size = inv.getContainerSize();
        var total = 0;
        var chips = 0;
        var bag = [];
        for (var i = 0; i < size; i++) {
            var s = inv.getItem(i);
            if (s == null || s.isEmpty()) continue;
            total += s.getCount();
            var id = itemIdOf(s);
            if (id.indexOf("_chip") >= 0) chips += s.getCount();
            bag.push(id + "x" + s.getCount());
        }
        return "total=" + total + " chips=" + chips + " bag=[" + bag.join(",") + "]";
    } catch (e) {
        return "<err:" + exText(e) + ">";
    }
}

// ── 子命令执行体 ──────────────────────────────────────────────────────────
function doEnv(ctx) {
    var t = "0"; try { t = CuriosApi.isTrinketsPresent() ? "1" : "0"; } catch (e) { t = "err"; }
    var a = "0"; try { a = CuriosApi.isAccessoriesPresent() ? "1" : "0"; } catch (e) { a = "err"; }
    var main = "none";
    if (t === "1" && a === "1") main = "both";
    else if (t === "1") main = "trinkets";
    else if (a === "1") main = "accessories";
    line(ctx, "ENV_PROVIDER", "trinkets=" + t + " accessories=" + a + " main=" + main);
    return 1;
}

/**
 * 逐源读数：**双装态下聚合视图不可分辨来源**（并集后同名槽位被合并），
 * 故这里绕过 CurioApi 门面，直接问两条通道各自的原始 API：
 *   · Trinkets  —— 前置库门面 `TrinketsCompat.getCuriosMap(entity)`（返回 Map，无 Optional）
 *   · Accessories —— `AccessoriesAPI.getUsedSlotsFor(player)`（返回 Collection，无 Optional）
 * 两者都用 `Java.loadClass` 惰性加载 + try/catch：缺席时**整个类都加载不了**，
 * 这里必须捕获并落成 `err:` 前缀，而不是让脚本加载期就炸掉。
 */
function doSources(ctx, playerName, tag) {
    var p = findPlayer(ctx, playerName);
    if (p == null) { line(ctx, tag + "_MISS", "player=" + playerName + " not_found"); return 0; }
    // ⚠️ 顺序纪律:**先用本模组自己的在场判定**（ModCompatibilityCheck，只读 FabricLoader），
    //    再去触碰对应类。缺席方直接标 `off` —— 实测教训（2026-10-01）：
    //    Accessories 缺席时调 `AccessoriesAPI.getUsedSlotsFor` 会抛 java.lang.Error
    //    （NoClassDefFoundError: io/wispforest/accessories/api/Accessory），
    //    而 java.lang.Error **不会**被这里的 `catch` 接住 ⇒ 整条命令硬中止、一行读数都不出。
    var trkOn = "?";
    try { trkOn = ModCompat.isTrinketsPresent() ? "1" : "0"; } catch (e0) { trkOn = "err"; }
    var accOn = "?";
    try { accOn = ModCompat.isAccessoriesPresent() ? "1" : "0"; } catch (e0b) { accOn = "err"; }

    var trk = "off";
    if (trkOn === "1") {
        try {
            var TC = Java.loadClass("com.merlinkitsune.starenginelib.item.TrinketsCompat");
            trk = "" + TC.getCuriosMap(p).size();
        } catch (e1) { trk = "err:" + exText(e1).substring(0, 140); }
    }
    var acc = "off";
    var accGroups = "off";
    if (accOn === "1") {
        try {
            var AAPI = Java.loadClass("io.wispforest.accessories.api.AccessoriesAPI");
            acc = "" + AAPI.getUsedSlotsFor(p).size();
        } catch (e2) { acc = "err:" + exText(e2).substring(0, 140); }
        try {
            var AC = Java.loadClass("com.merlinkitsune.astral_dice.compat.accessories.AccessoriesCompat");
            accGroups = "" + AC.getInventory(p).get().getCurios().size();
        } catch (e3) { accGroups = "err:" + exText(e3).substring(0, 140); }
    }
    line(ctx, tag + "_SRC", "trinkets_on=" + trkOn + " accessories_on=" + accOn
        + " trinkets_groups=" + trk + " accessories_used_slots=" + acc
        + " accessories_groups=" + accGroups);
    return 1;
}

function doGroups(ctx, playerName) {
    var p = findPlayer(ctx, playerName);
    if (p == null) { line(ctx, "GRPS_MISS", "player=" + playerName + " not_found"); return 0; }
    line(ctx, "GRPS", "player=" + playerName + " groups=" + groupsText(p));
    return 1;
}

/** 全量快照（后缀区分 NOW / POST） */
function snapshot(ctx, tag, suffix, playerName) {
    var p = findPlayer(ctx, playerName);
    if (p == null) { line(ctx, tag + "_MISS", "player=" + playerName + " not_found"); return null; }
    line(ctx, tag + "_GROUPS" + suffix, groupsText(p));
    line(ctx, tag + "_DICE" + suffix, diceText(p));
    line(ctx, tag + "_CHIP" + suffix, chipText(p));
    var gate = "?";
    try { gate = CurioSlotUtil.hasDiceEquipped(p) ? "1" : "0"; } catch (e) { gate = "<err:" + exText(e) + ">"; }
    line(ctx, tag + "_GATE" + suffix, "hasDice=" + gate);
    line(ctx, tag + "_INV" + suffix, invText(p));
    return p;
}

function doState(ctx, playerName, tag) {
    snapshot(ctx, tag, "", playerName);
    return 1;
}

/** 走**产品入口**（等价于真人下蹲右键自动装备，仅少一层按键判定） */
function doEquip(ctx, playerName, slotId, itemId, tag) {
    var p = findPlayer(ctx, playerName);
    if (p == null) { line(ctx, tag + "_MISS", "player=" + playerName + " not_found"); return 0; }
    var item = resolveItem(itemId);
    if (item == null) { line(ctx, tag + "_ERR", "unknown_item:" + itemId); return 0; }
    var res = "?";
    try {
        var holder = CurioSlotUtil.tryAutoEquip(p, new ItemStack(item), slotId);
        try { res = "" + holder.getResult(); } catch (e1) { res = "<no-result>"; }
    } catch (e) {
        line(ctx, tag + "_EX", "tryAutoEquip:" + exText(e));
        return 0;
    }
    line(ctx, tag + "_EQUIP", "slot=" + slotId + " item=" + itemId + " result=" + res);
    snapshot(ctx, tag, "_POST", playerName);
    return 1;
}

function doUnequip(ctx, playerName, slotId, index, tag) {
    var p = findPlayer(ctx, playerName);
    if (p == null) { line(ctx, tag + "_MISS", "player=" + playerName + " not_found"); return 0; }
    var h = handlerOf(p, slotId);
    if (h == null) { line(ctx, tag + "_ERR", "no_group:" + slotId); return 0; }
    var stacks = h.getStacks();
    var before = "<oob>";
    if (index < 0 || index >= stacks.getSlots()) {
        line(ctx, tag + "_ERR", "index_oob:" + index + "/" + stacks.getSlots());
        return 0;
    }
    before = itemIdOf(stacks.getStackInSlot(index));
    stacks.setStackInSlot(index, ItemStack.EMPTY);
    line(ctx, tag + "_UNEQUIP", "slot=" + slotId + " index=" + index + " before=" + before);
    snapshot(ctx, tag, "_POST", playerName);
    return 1;
}

function doClear(ctx, playerName, slotId, tag) {
    var p = findPlayer(ctx, playerName);
    if (p == null) { line(ctx, tag + "_MISS", "player=" + playerName + " not_found"); return 0; }
    var h = handlerOf(p, slotId);
    if (h == null) { line(ctx, tag + "_ERR", "no_group:" + slotId); return 0; }
    var stacks = h.getStacks();
    var removed = 0;
    for (var i = 0; i < stacks.getSlots(); i++) {
        if (!stacks.getStackInSlot(i).isEmpty()) {
            stacks.setStackInSlot(i, ItemStack.EMPTY);
            removed++;
        }
    }
    line(ctx, tag + "_CLEAR", "slot=" + slotId + " removed=" + removed);
    snapshot(ctx, tag, "_POST", playerName);
    return 1;
}

/** 测试脚手架：把 n 枚互不相同的筹码直接放进 chip 栏（不改变被测的尺寸逻辑） */
function doFillChip(ctx, playerName, n, tag) {
    var p = findPlayer(ctx, playerName);
    if (p == null) { line(ctx, tag + "_MISS", "player=" + playerName + " not_found"); return 0; }
    var h = handlerOf(p, "chip");
    if (h == null) { line(ctx, tag + "_ERR", "no_group:chip"); return 0; }
    var stacks = h.getStacks();
    var cap = stacks.getSlots();
    var filled = 0;
    for (var i = 0; i < n && i < cap && i < FILL_CHIPS.length; i++) {
        var item = resolveItem(FILL_CHIPS[i]);
        if (item == null) { line(ctx, tag + "_WARN", "unknown_item:" + FILL_CHIPS[i]); continue; }
        stacks.setStackInSlot(i, new ItemStack(item));
        filled++;
    }
    line(ctx, tag + "_FILLCHIP", "want=" + n + " filled=" + filled + " cap=" + cap);
    snapshot(ctx, tag, "_POST", playerName);
    return 1;
}

function doRefresh(ctx, playerName, tag) {
    var p = findPlayer(ctx, playerName);
    if (p == null) { line(ctx, tag + "_MISS", "player=" + playerName + " not_found"); return 0; }
    try { DiceCurioItem.refreshChipSlotCount(p); } catch (e) {
        line(ctx, tag + "_EX", "refreshChipSlotCount:" + exText(e));
        return 0;
    }
    line(ctx, tag + "_REFRESH", "called=1");
    snapshot(ctx, tag, "_POST", playerName);
    return 1;
}

function doEnforce(ctx, playerName, tag) {
    var p = findPlayer(ctx, playerName);
    if (p == null) { line(ctx, tag + "_MISS", "player=" + playerName + " not_found"); return 0; }
    try { DiceCurioItem.enforceNoDiceState(p); } catch (e) {
        line(ctx, tag + "_EX", "enforceNoDiceState:" + exText(e));
        return 0;
    }
    line(ctx, tag + "_ENFORCE", "called=1");
    snapshot(ctx, tag, "_POST", playerName);
    return 1;
}

function doClearInv(ctx, playerName, tag) {
    var p = findPlayer(ctx, playerName);
    if (p == null) { line(ctx, tag + "_MISS", "player=" + playerName + " not_found"); return 0; }
    var removed = 0;
    try {
        var inv = p.getInventory();
        for (var i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
                removed++;
            }
        }
    } catch (e) {
        line(ctx, tag + "_EX", "clearInv:" + exText(e));
        return 0;
    }
    line(ctx, tag + "_CLEARINV", "removed=" + removed);
    return 1;
}

// ── 命令注册 ──────────────────────────────────────────────────────────────
ServerEvents.commandRegistry(event => {
    var Commands = event.commands;
    event.register(
        Commands.literal("astralfab")
            .then(Commands.literal("env")
                .executes(ctx => guard(ctx, "ENV", function () {
                    return doEnv(ctx);
                })))
            .then(Commands.literal("groups")
                .then(Commands.argument("player", StringArg.word())
                    .executes(ctx => guard(ctx, "GRPS", function () {
                        return doGroups(ctx, StringArg.getString(ctx, "player"));
                    }))))
            .then(Commands.literal("sources")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doSources(ctx, StringArg.getString(ctx, "player"),
                                StringArg.getString(ctx, "tag"));
                        })))))
            .then(Commands.literal("state")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doState(ctx, StringArg.getString(ctx, "player"),
                                StringArg.getString(ctx, "tag"));
                        })))))
            .then(Commands.literal("equip")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("slot", StringArg.word())
                        .then(Commands.argument("item", StringArg.string())
                            .then(Commands.argument("tag", StringArg.word())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doEquip(ctx, StringArg.getString(ctx, "player"),
                                        StringArg.getString(ctx, "slot"),
                                        StringArg.getString(ctx, "item"),
                                        StringArg.getString(ctx, "tag"));
                                })))))))
            .then(Commands.literal("unequip")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("slot", StringArg.word())
                        .then(Commands.argument("index", IntegerArg.integer(0, 16))
                            .then(Commands.argument("tag", StringArg.word())
                                .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                    return doUnequip(ctx, StringArg.getString(ctx, "player"),
                                        StringArg.getString(ctx, "slot"),
                                        IntegerArg.getInteger(ctx, "index"),
                                        StringArg.getString(ctx, "tag"));
                                })))))))
            .then(Commands.literal("clear")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("slot", StringArg.word())
                        .then(Commands.argument("tag", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doClear(ctx, StringArg.getString(ctx, "player"),
                                    StringArg.getString(ctx, "slot"),
                                    StringArg.getString(ctx, "tag"));
                            }))))))
            .then(Commands.literal("fillchip")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("n", IntegerArg.integer(0, 16))
                        .then(Commands.argument("tag", StringArg.word())
                            .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                                return doFillChip(ctx, StringArg.getString(ctx, "player"),
                                    IntegerArg.getInteger(ctx, "n"),
                                    StringArg.getString(ctx, "tag"));
                            }))))))
            .then(Commands.literal("refresh")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doRefresh(ctx, StringArg.getString(ctx, "player"),
                                StringArg.getString(ctx, "tag"));
                        })))))
            .then(Commands.literal("enforce")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doEnforce(ctx, StringArg.getString(ctx, "player"),
                                StringArg.getString(ctx, "tag"));
                        })))))
            .then(Commands.literal("clearinv")
                .then(Commands.argument("player", StringArg.word())
                    .then(Commands.argument("tag", StringArg.word())
                        .executes(ctx => guard(ctx, StringArg.getString(ctx, "tag"), function () {
                            return doClearInv(ctx, StringArg.getString(ctx, "player"),
                                StringArg.getString(ctx, "tag"));
                        })))))
    );
});

console.info("[astralfab] 探针脚本已加载：命令 /astralfab <env|groups|state|equip|unequip|clear|fillchip|refresh|enforce|clearinv>");
