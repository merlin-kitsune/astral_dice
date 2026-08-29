package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.ModDataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 专属效果牌工具:仅允许获得者使用。
 * 玩家若将专属牌赠与其他玩家,接收者无法使用该牌(即使其拥有对应的立牌)。
 */
public final class ExclusiveCardUtil {
    private ExclusiveCardUtil() {
    }

    // 设置获得者
    public static void setOwner(ItemStack stack, Player player) {
        ModDataComponents.OWNER_UUID.set(stack,  Optional.of(player.getUUID()));
    }

    // 是否可用:无所有者则允许(首次使用时绑定),否则必须与所有者一致
    public static boolean canUse(Player player, ItemStack stack) {
        Optional<UUID> owner = ModDataComponents.OWNER_UUID.getOrDefault(stack,  Optional.empty());
        return owner.isEmpty() || owner.get().equals(player.getUUID());
    }

    // 是否为专属效果牌
    public static boolean isExclusive(ItemStack stack) {
        return stack.is(ModItems.LIVING_BOOK_PAGE.get()) || stack.is(ModItems.FATE_GUIDANCE_CARD.get());
    }

    // 无所有者时绑定为当前使用者
    public static void bindIfAbsent(ItemStack stack, Player player) {
        if (ModDataComponents.OWNER_UUID.getOrDefault(stack,  Optional.empty()).isEmpty()) {
            ModDataComponents.OWNER_UUID.set(stack,  Optional.of(player.getUUID()));
        }
    }
}
