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

    // 设置获得者(2026-10-27:连带写入显示名,供 tooltip 直接渲染)
    public static void setOwner(ItemStack stack, Player player) {
        ModDataComponents.OWNER_UUID.set(stack, Optional.of(player.getUUID()));
        ModDataComponents.OWNER_NAME.set(stack, player.getGameProfile().getName());
    }

    // 获得者 UUID(空 = 尚未绑定)
    public static Optional<UUID> ownerOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        return ModDataComponents.OWNER_UUID.getOrDefault(stack, Optional.empty());
    }

    // 获得者显示名(空串 = 未绑定 / 名字缺失);**只用于 tooltip**,不参与任何判定
    public static String ownerName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        String name = ModDataComponents.OWNER_NAME.get(stack);
        return name == null ? "" : name;
    }

    // 是否可用:无所有者则允许(首次使用时绑定),否则必须与所有者一致
    public static boolean canUse(Player player, ItemStack stack) {
        Optional<UUID> owner = ownerOf(stack);
        return owner.isEmpty() || owner.get().equals(player.getUUID());
    }

    // 是否为专属效果牌
    public static boolean isExclusive(ItemStack stack) {
        return stack.is(ModItems.LIVING_PAGE.get()) || stack.is(ModItems.FATE_GUIDANCE_CARD.get())
                // 风水师立牌(zhao)的两张符卡:同样是绑定获得者的专属牌
                || stack.is(ModItems.FU_CARD.get()) || stack.is(ModItems.HUO_CARD.get())
                // 蛟龙立牌(mamushi)的两张专属战斗牌:绑定获得者(仅获得者可装备)
                || stack.is(ModItems.ATTACK_CARD_BITE.get()) || stack.is(ModItems.ATTACK_CARD_DRAGON_ROAR.get())
                // 怪力乱神立牌专属效果牌(2026-10-18;2026-10-18 移植到本线):强化拒止
                || stack.is(ModItems.EFFECT_CARD_REPULSION.get());
    }

    // 无所有者时绑定为当前使用者(uuid + 显示名一起写)
    public static void bindIfAbsent(ItemStack stack, Player player) {
        if (ownerOf(stack).isEmpty()) {
            setOwner(stack, player);
        }
    }
}
