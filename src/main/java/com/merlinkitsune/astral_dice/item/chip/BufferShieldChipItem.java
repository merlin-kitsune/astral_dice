package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.event.ModEventHandlers;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 缓冲盾牌筹码:受到攻击时,增加 2 点治愈与 3 星币,每分钟只能触发一次。
 * 受击钩子由 {@link com.merlinkitsune.astral_dice.event.ModEventHandlers} 在伤害事件中调用 {@link #onHurt}。
 */
public class BufferShieldChipItem extends BaseChipItem {
    /** 触发冷却时长(1 分钟) */
    public static final int COOLDOWN_TICKS = 1200;
    /** 每次触发的治愈点数 */
    public static final int HEALING_GAIN = 2;
    /** 每次触发的星币数量 */
    public static final int COIN_GAIN = 3;

    public BufferShieldChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴缓冲盾牌筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.BUFFER_SHIELD.get())).isPresent();
    }

    /**
     * 受到攻击时调用:冷却已结束时,获得 2 点治愈与 3 星币,并进入 1 分钟冷却。
     * 任何来源的伤害均触发(近战/远程/环境等)。
     */
    public static void onHurt(Player player, float amount) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        long now = player.level().getGameTime();
        if (now < ModAttachments.getBufferShieldCooldownEnd(player)) return;
        ModAttachments.setBufferShieldCooldownEnd(player, now + COOLDOWN_TICKS);

        HealingManager.add(player, HEALING_GAIN);
        ResourceConversion.giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), COIN_GAIN));
    }
}
