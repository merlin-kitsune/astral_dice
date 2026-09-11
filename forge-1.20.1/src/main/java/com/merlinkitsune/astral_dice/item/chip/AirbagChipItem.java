package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.CuriosCompat;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * 安全气囊筹码:受到致命伤害时,消耗 6 点充能使本次伤害无效。技能冷却时间 1:00。
 *
 * <p>触发判定与结算由事件类在最终伤害阶段执行(见 ChipDamageHandler),
 * 本类只负责条件判定与资源/冷却变更。
 */
public class AirbagChipItem extends BaseChipItem {
    /** 每次触发消耗的充能层数 */
    public static final int CHARGE_COST = 6;
    /** 触发冷却(1:00) */
    public static final int COOLDOWN_TICKS = 20 * 60;

    public AirbagChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴安全气囊 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.AIRBAG_CHIP.get())).isPresent();
    }

    /** 是否处于触发冷却中 */
    public static boolean isOnCooldown(Player player) {
        if (player == null) return false;
        return player.level().getGameTime() < ModAttachments.getAirbagCooldownEnd(player);
    }

    /**
     * 受到致命伤害时调用:满足条件则消耗 6 点充能、进入 1:00 冷却,返回 true(本次伤害应被无效化)。
     */
    public static boolean tryNegateFatal(Player player) {
        if (player == null || player.level().isClientSide()) return false;
        if (!isEquipped(player)) return false;
        if (isOnCooldown(player)) return false;
        if (ChargeManager.getStacks(player) < CHARGE_COST) return false;

        ChargeManager.consume(player, CHARGE_COST);
        ModAttachments.setAirbagCooldownEnd(player, player.level().getGameTime() + COOLDOWN_TICKS);
        player.displayClientMessage(
                Component.translatable("hud.astral_dice.airbag_trigger", CHARGE_COST), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);
        return true;
    }
}
