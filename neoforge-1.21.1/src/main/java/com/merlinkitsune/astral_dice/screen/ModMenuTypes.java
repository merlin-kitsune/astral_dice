package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.shop.CardShopMenu;
import com.merlinkitsune.astral_dice.shop.ChipShopMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.function.Supplier;

public class ModMenuTypes {
        public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU,
                        AstralDiceMod.MODID);

        public static final Supplier<MenuType<CardInventoryMenu>> CARD_INVENTORY = MENU_TYPES.register("card_inventory",
                        () -> new MenuType<>(CardInventoryMenu::new, FeatureFlags.DEFAULT_FLAGS));

        public static final Supplier<MenuType<CardShopMenu>> CARD_SHOP = MENU_TYPES.register("card_shop",
                        () -> new MenuType<>(CardShopMenu::new, FeatureFlags.DEFAULT_FLAGS));

        public static final Supplier<MenuType<ChipShopMenu>> CHIP_SHOP = MENU_TYPES.register("chip_shop",
                        () -> new MenuType<>(ChipShopMenu::new, FeatureFlags.DEFAULT_FLAGS));

        /** 星愿铁砧(2026-11-15):三输入 + 结果,无打开参数,2 参 MenuType 即可。 */
        public static final Supplier<MenuType<AstralAnvilMenu>> ASTRAL_ANVIL = MENU_TYPES.register("astral_anvil",
                        () -> new MenuType<>(AstralAnvilMenu::new, FeatureFlags.DEFAULT_FLAGS));

        /**
         * 通用「选卡界面」菜单（2026-10-17）：槽位数（1/3）与卡牌类型由**打开参数**决定，
         * 因此必须用 {@link IMenuTypeExtension#create} 走 extra data（token + slotCount + type.ordinal()），
         * 不能用 2 参 {@code MenuType::new}（那会让客户端构造出 0 槽 / 类型未知的菜单）。
         */
        public static final Supplier<MenuType<CardChoiceMenu>> CARD_CHOICE =
                        MENU_TYPES.register("card_choice",
                                        () -> IMenuTypeExtension.create(CardChoiceMenu::new));

    /**
     * 服务端打开卡牌栏（原 {@code OpenCardInventoryPayload} 的服务端逻辑；需佩戴骰子）。
     *
     * @return 是否打开了界面；未佩戴骰子时已向玩家提示并返回 false
     */
    public static boolean openCardInventory(ServerPlayer serverPlayer) {
        var curios = CuriosApi.getCuriosInventory(serverPlayer);
        if (curios.isEmpty() || curios.get().findFirstCurio(DiceCurioItem::isDiceItem).isEmpty()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                    new com.merlinkitsune.astral_dice.network.ActionBarPayload(
                            Component.translatable("msg.astral_dice.no_dice_equipped").withStyle(net.minecraft.ChatFormatting.RED),
                            com.merlinkitsune.starengine.component.GameplayConstants.ACTIONBAR_DURATION_TICKS));
            return false;
        }
        serverPlayer.openMenu(new SimpleMenuProvider(
                (containerId, inventory, player) -> new CardInventoryMenu(containerId, inventory),
                Component.translatable("gui.astral_dice.card_inventory")
        ));
        return true;
    }
}
