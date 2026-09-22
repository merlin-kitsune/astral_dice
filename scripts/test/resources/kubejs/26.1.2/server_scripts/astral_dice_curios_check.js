// Astral Dice 自动化测试辅助脚本 - 最终版
// 功能: 检查玩家 Curios 饰品栏是否仍装备指定物品(用于验证攻击时饰品不被意外弹出/掉落)
// 使用方式: 游戏中执行 /astralcurios (需要 OP 权限 level 2)
// 输出:
//   ASTRAL_CURIOS_OK:<slot>:<index>       —— 该槽位指定物品仍在身上
//   ASTRAL_CURIOS_MISSING:<slot>:<index>  —— 该槽位指定物品未装备(可能被弹出/掉落)
//
// 注意:
// 1. KubeJS 无内置 CuriosHelper,须用 Java.loadClass 加载 Curios API;
// 2. 遍历 CuriosInventory.getCurios() 中所有槽位栈,按槽位索引+物品注册 ID 匹配;
// 3. sendSuccess 的 Supplier 参数在 Rhino 中转换不可靠,统一使用 sendFailure(Component 直传);
// 4. Rhino 中 const 为函数作用域,循环体内禁止重复声明 const(用 var);
// 5. 修改脚本后须依次执行 /kubejs reload server-scripts 与 /reload 命令才会生效
//    (仅 reload server-scripts 不会更新命令闭包,必须再执行 /reload 重新注册命令)。

ServerEvents.commandRegistry(event => {
    const { commands: Commands } = event;
    const CuriosApi = Java.loadClass("top.theillusivec4.curios.api.CuriosApi");
    const BuiltInRegistries = Java.loadClass("net.minecraft.core.registries.BuiltInRegistries");
    const ComponentClass = Java.loadClass("net.minecraft.network.chat.Component");

    event.register(
        Commands.literal("astralcurios")
            .requires(src => src.permissions().hasPermission(Java.loadClass("net.minecraft.server.permissions.Permissions").COMMANDS_GAMEMASTER))
            .executes(ctx => {
                const player = ctx.source.getPlayerOrException();

                // 检查指定槽位索引中的物品是否匹配
                function checkSlot(slotId, index, itemId) {
                    const opt = CuriosApi.getCuriosInventory(player);
                    if (opt == null || !opt.isPresent()) return false;
                    const handler = opt.get();
                    const stacks = handler.getStacksHandler(slotId);
                    if (stacks == null || !stacks.isPresent()) return false;
                    const h = stacks.get();
                    if (index < 0 || index >= h.getSlots()) return false;
                    const stack = h.getStacks().getStackInSlot(index);
                    return !stack.isEmpty()
                        && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() === itemId;
                }

                // 待检查的槽位与对应物品(下界合金骰子:2 立牌槽 + 2 筹码槽)
                // 注意: checks 必须与当前实际装备一致! 第1段 = lulu_sign, 第2段换随机立牌后须同步修改
                var checks = [
                    ["dice",  0, "astral_dice:netherite_dice"],
                    ["stand", 0, "astral_dice:bonnie_sign"],
                    ["stand", 1, "astral_dice:fanny_sign"],
                    ["chip",  0, "astral_dice:cutter_chip"],
                    ["chip",  1, "astral_dice:scope_chip"]
                ];

                for (var c = 0; c < checks.length; c++) {
                    var entry = checks[c];
                    var slot = entry[0];
                    var index = entry[1];
                    var itemId = entry[2];
                    var msg = ComponentClass.literal(
                        checkSlot(slot, index, itemId)
                            ? "ASTRAL_CURIOS_OK:" + slot + ":" + index
                            : "ASTRAL_CURIOS_MISSING:" + slot + ":" + index);
                    ctx.source.sendFailure(msg);
                }
                return 1;
            })
    );
});
