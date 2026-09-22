// Astral Dice 目标选择器自动化测试辅助脚本(仅测试环境使用)
// 用法(游戏中,OP level 2):
//   /astraldice_ts_count <半径>                → TS_COUNT:<半径>:<敌对生物数量>
//   /astraldice_ts_present <实体注册id> <半径>  → TS_PRESENT:<id>:<数量>
//   /astraldice_ts_equip <sign>               → TS_EQUIP_OK:<物品id> (把立牌装备到 stand 槽 0; sign=haiqing|bonnie)
//   /astraldice_ts_clearcd                    → TS_CD_CLEARED (清空立牌主动技能冷却,测试用)
//
// Rhino 注意事项(均已踩过):
// 1. 命令注册必须放在 ServerEvents.commandRegistry 回调内(注释文本勿包含该函数头字样,
//    避免脚本替换工具误匹配);
// 2. Rhino 中 const/var 提升行为异常,多个 executes 回调内声明变量可能 redeclaration;
//    因此把每个命令的执行体提取为顶层命名函数(doCount/doPresent/doEquip/doClearCd),函数内一律用 var;
// 3. KubeJS 2101 用 Java.loadClass(无 Java.type);
// 4. sendSuccess(Supplier) 不可靠,统一 sendFailure(Component);
// 5. 修改后执行 /kubejs reload server-scripts + /reload 快速重载(无需重启游戏),
//    并检查 run/logs/kubejs/server.log 为 0 errors(进入世界后第一步)。

var ComponentClass = Java.loadClass("net.minecraft.network.chat.Component");
var AABBClass = Java.loadClass("net.minecraft.world.phys.AABB");
var EnemyClass = Java.loadClass("net.minecraft.world.entity.monster.Enemy");
var EntityClass = Java.loadClass("net.minecraft.world.entity.Entity");
var BuiltInRegistries = Java.loadClass("net.minecraft.core.registries.BuiltInRegistries");
var StringArg = Java.loadClass("com.mojang.brigadier.arguments.StringArgumentType");
var IntegerArg = Java.loadClass("com.mojang.brigadier.arguments.IntegerArgumentType");

function send(ctx, text) {
    ctx.source.sendFailure(ComponentClass.literal(text));
}

function doCount(ctx) {
    try {
        var p = ctx.source.getPlayerOrException();
        var r = IntegerArg.getInteger(ctx, "radius");
        var list = p.level().getEntitiesOfClass(EnemyClass, AABBClass.ofSize(p.position(), r * 2, r * 2, r * 2));
        send(ctx, "TS_COUNT:" + r + ":" + list.size());
        return 1;
    } catch (e) {
        send(ctx, "TS_ERROR:" + e);
        return 0;
    }
}

function doPresent(ctx) {
    try {
        var p = ctx.source.getPlayerOrException();
        var typeId = StringArg.getString(ctx, "type");
        var r = IntegerArg.getInteger(ctx, "radius");
        var list = p.level().getEntitiesOfClass(EntityClass, AABBClass.ofSize(p.position(), r * 2, r * 2, r * 2));
        var n = 0;
        for (var i = 0; i < list.size(); i++) {
            var key = BuiltInRegistries.ENTITY_TYPE.getKey(list.get(i).getType()).toString();
            if (key === typeId) n++;
        }
        send(ctx, "TS_PRESENT:" + typeId + ":" + n);
        return 1;
    } catch (e) {
        send(ctx, "TS_ERROR:" + e);
        return 0;
    }
}

function doEquip(ctx) {
    try {
        var p = ctx.source.getPlayerOrException();
        var sign = StringArg.getString(ctx, "sign");
        var itemId = "astral_dice:" + sign + "_sign";
        var CuriosApi = Java.loadClass("top.theillusivec4.curios.api.CuriosApi");
        var ItemStack = Java.loadClass("net.minecraft.world.item.ItemStack");
        var IdentifierClass = Java.loadClass("net.minecraft.resources.Identifier");
        var item = BuiltInRegistries.ITEM.getValue(IdentifierClass.parse(itemId));
        if (item == null || item.toString() === "air") {
            send(ctx, "TS_EQUIP_ERR:unknown_item:" + itemId);
            return 0;
        }
        var opt = CuriosApi.getCuriosInventory(p);
        if (opt == null || !opt.isPresent()) { send(ctx, "TS_EQUIP_ERR:no_curios"); return 0; }
        var stacks = opt.get().getStacksHandler("stand").get().getStacks();
        stacks.setStackInSlot(0, ItemStack.EMPTY);
        stacks.setStackInSlot(0, new ItemStack(item));
        send(ctx, "TS_EQUIP_OK:" + itemId);
        return 1;
    } catch (e) {
        send(ctx, "TS_EQUIP_ERR:" + e);
        return 0;
    }
}

function doClearCd(ctx) {
    try {
        var p = ctx.source.getPlayerOrException();
        var ModAttachments = Java.loadClass("com.merlinkitsune.astral_dice.component.ModAttachments");
        ModAttachments.setSignActiveCooldownEnd(p, 0);
        send(ctx, "TS_CD_CLEARED");
        return 1;
    } catch (e) {
        send(ctx, "TS_ERROR:" + e);
        return 0;
    }
}

ServerEvents.commandRegistry(event => {
    var Commands = event.commands;
    event.register(
        Commands.literal("astraldice_ts_count")
            .requires(src => src.permissions().hasPermission(Java.loadClass("net.minecraft.server.permissions.Permissions").COMMANDS_GAMEMASTER))
            .then(Commands.argument("radius", IntegerArg.integer(1, 64))
                .executes(ctx => doCount(ctx)))
    );
    event.register(
        Commands.literal("astraldice_ts_present")
            .requires(src => src.permissions().hasPermission(Java.loadClass("net.minecraft.server.permissions.Permissions").COMMANDS_GAMEMASTER))
            .then(Commands.argument("type", StringArg.word())
                .then(Commands.argument("radius", IntegerArg.integer(1, 64))
                    .executes(ctx => doPresent(ctx))))
    );
    event.register(
        Commands.literal("astraldice_ts_equip")
            .requires(src => src.permissions().hasPermission(Java.loadClass("net.minecraft.server.permissions.Permissions").COMMANDS_GAMEMASTER))
            .then(Commands.argument("sign", StringArg.word())
                .executes(ctx => doEquip(ctx)))
    );
    event.register(
        Commands.literal("astraldice_ts_clearcd")
            .requires(src => src.permissions().hasPermission(Java.loadClass("net.minecraft.server.permissions.Permissions").COMMANDS_GAMEMASTER))
            .executes(ctx => doClearCd(ctx))
    );
});
