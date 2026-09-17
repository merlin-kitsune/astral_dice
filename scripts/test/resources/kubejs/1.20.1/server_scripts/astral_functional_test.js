// Functional-test helper (1.20.1 forge dev only).
//   /astraltest equip <0-3>  - put a dice with given star into Curios "dice" slot
//   /astraltest menuinfo     - report the open CardInventoryMenu slot counts + dice star
var ComponentClass = Java.loadClass("net.minecraft.network.chat.Component");
var BuiltInRegistries = Java.loadClass("net.minecraft.core.registries.BuiltInRegistries");
var ResourceLocation = Java.loadClass("net.minecraft.resources.ResourceLocation");
var ItemStack = Java.loadClass("net.minecraft.world.item.ItemStack");
var IntegerArg = Java.loadClass("com.mojang.brigadier.arguments.IntegerArgumentType");
var CuriosApi = Java.loadClass("top.theillusivec4.curios.api.CuriosApi");
var WeaponEnhancement = Java.loadClass("com.merlinkitsune.astral_dice.component.WeaponEnhancement");
var ModDataComponents = Java.loadClass("com.merlinkitsune.astral_dice.component.ModDataComponents");
var CollectionsClass = Java.loadClass("java.util.Collections");
var CardInventoryMenu = Java.loadClass("com.merlinkitsune.astral_dice.screen.CardInventoryMenu");

function send(ctx, text) {
    ctx.source.sendFailure(ComponentClass.literal(text));
}

function doEquipStar(ctx) {
    try {
        var p = ctx.source.getPlayerOrException();
        var star = IntegerArg.getInteger(ctx, "star");
        if (star < 0 || star > 3) { send(ctx, "ASTRALTEST_ERR:star_range"); return 0; }
        var item = BuiltInRegistries.ITEM.get(new ResourceLocation("astral_dice:dice"));
        if (item == null || item === BuiltInRegistries.ITEM.get(new ResourceLocation("air"))) {
            send(ctx, "ASTRALTEST_ERR:unknown_item"); return 0;
        }
        var opt = CuriosApi.getCuriosInventory(p);
        if (opt == null || !opt.isPresent()) { send(ctx, "ASTRALTEST_ERR:no_curios"); return 0; }
        var inv = opt.resolve().get();
        var diceOpt = inv.getStacksHandler("dice");
        if (diceOpt == null || !diceOpt.isPresent()) { send(ctx, "ASTRALTEST_ERR:no_dice_slot"); return 0; }
        var stacks = diceOpt.get().getStacks();
        stacks.setStackInSlot(0, ItemStack.EMPTY);
        stacks.setStackInSlot(0, new ItemStack(item, 1));
        var equipped = stacks.getStackInSlot(0);
        var enh = new WeaponEnhancement(0, 3, 0, 3, star, CollectionsClass.emptyList());
        ModDataComponents.WEAPON_ENHANCEMENT.set(equipped, enh);
        send(ctx, "ASTRALTEST_EQUIPPED:star=" + star);
        return 1;
    } catch (e) {
        send(ctx, "ASTRALTEST_ERR:" + e);
        return 0;
    }
}

function doMenuInfo(ctx) {
    try {
        var p = ctx.source.getPlayerOrException();
        var menu = p.containerMenu;
        if (menu == null || !CardInventoryMenu.isInstance(menu)) {
            send(ctx, "MENU:not_card:" + (menu == null ? "null" : menu.getClass().getName()));
            return 1;
        }
        var fCard = CardInventoryMenu.getDeclaredField("cardSlots"); fCard.setAccessible(true);
        var fAtk = CardInventoryMenu.getDeclaredField("attackSlots"); fAtk.setAccessible(true);
        var fDef = CardInventoryMenu.getDeclaredField("defenseSlots"); fDef.setAccessible(true);
        var card = fCard.getInt(menu);
        var atk = fAtk.getInt(menu);
        var def = fDef.getInt(menu);
        var opt = CuriosApi.getCuriosInventory(p);
        var star = -1;
        var hasDice = false;
        if (opt != null && opt.isPresent()) {
            var inv = opt.resolve().get();
            var dOpt = inv.getStacksHandler("dice");
            if (dOpt != null && dOpt.isPresent()) {
                var stack = dOpt.get().getStacks().getStackInSlot(0);
                hasDice = !stack.isEmpty();
                if (hasDice) {
                    var we = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(stack, WeaponEnhancement.EMPTY);
                    star = we.starLevel();
                }
            }
        }
        send(ctx, "MENU:card=" + card + " atk=" + atk + " def=" + def + " dice=" + hasDice + " star=" + star);
        return 1;
    } catch (e) {
        send(ctx, "MENU_ERR:" + e);
        return 0;
    }
}

function equipStar(p, star) {
    var item = BuiltInRegistries.ITEM.get(new ResourceLocation("astral_dice:dice"));
    if (item == null || item === BuiltInRegistries.ITEM.get(new ResourceLocation("air"))) throw new Error("unknown_item");
    var opt = CuriosApi.getCuriosInventory(p);
    if (opt == null || !opt.isPresent()) throw new Error("no_curios");
    var inv = opt.resolve().get();
    var diceOpt = inv.getStacksHandler("dice");
    if (diceOpt == null || !diceOpt.isPresent()) throw new Error("no_dice_slot");
    var stacks = diceOpt.get().getStacks();
    stacks.setStackInSlot(0, ItemStack.EMPTY);
    stacks.setStackInSlot(0, new ItemStack(item, 1));
    var equipped = stacks.getStackInSlot(0);
    var enh = new WeaponEnhancement(0, 3, 0, 3, star, CollectionsClass.emptyList());
    ModDataComponents.WEAPON_ENHANCEMENT.set(equipped, enh);
}

function readCardMenuFields(menu) {
    var fCard = CardInventoryMenu.getDeclaredField("cardSlots"); fCard.setAccessible(true);
    var fAtk = CardInventoryMenu.getDeclaredField("attackSlots"); fAtk.setAccessible(true);
    var fDef = CardInventoryMenu.getDeclaredField("defenseSlots"); fDef.setAccessible(true);
    return { card: fCard.getInt(menu), atk: fAtk.getInt(menu), def: fDef.getInt(menu) };
}

function doSlotCheck(ctx, star) {
    try {
        var p = ctx.source.getPlayerOrException();
        if (star < 0 || star > 3) { send(ctx, "SLOTCHECK_ERR:star_range"); return 0; }
        equipStar(p, star);
        // same construction path used by openCardInventory -> CardInventoryMenu
        var menu = new CardInventoryMenu(1, p.getInventory());
        // count visible card slots via public AbstractContainerMenu.slots (hidden inventory slots use x=-10000)
        var total = 0, atk = 0, def = 0;
        var slots = menu.slots;
        for (var i = 0; i < slots.size(); i++) {
            var s = slots.get(i);
            var x = s.x;
            if (x >= 0) {
                total++;
                if (s.y < 20) { atk++; } else { def++; }
            }
        }
        send(ctx, "SLOTCHECK:star=" + star + " card=" + total + " atk=" + atk + " def=" + def);
        return 1;
    } catch (e) {
        send(ctx, "SLOTCHECK_ERR:" + e);
        return 0;
    }
}

ServerEvents.commandRegistry(event => {
    var Commands = event.commands;
    event.register(
        Commands.literal("astraltest")
            .requires(src => src.hasPermission(2))
            .then(Commands.literal("equip")
                .then(Commands.argument("star", IntegerArg.integer(0, 3))
                    .executes(ctx => doEquipStar(ctx))))
            .then(Commands.literal("slotcheck0").executes(ctx => doSlotCheck(ctx, 0)))
            .then(Commands.literal("slotcheck1").executes(ctx => doSlotCheck(ctx, 1)))
            .then(Commands.literal("slotcheck2").executes(ctx => doSlotCheck(ctx, 2)))
            .then(Commands.literal("slotcheck3").executes(ctx => doSlotCheck(ctx, 3)))
            .then(Commands.literal("menuinfo")
                .executes(ctx => doMenuInfo(ctx))));
});
