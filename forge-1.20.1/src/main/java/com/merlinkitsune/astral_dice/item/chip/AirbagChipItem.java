package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.combat.DiceCombatEvents;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
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
        // 总闸门(骰子装备和卸除调整):未佩戴骰子 ⇒ 本件功能一律不生效(数值保留)
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
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
     *
     * <p>2026-10-08 用户裁决追加能力:无效化成功后,对「造成该次伤害的施加者」反击一次。
     * 伤害口径与鼠鼠护盾 / 肉弹战车嘲讽 / 摩西破绽闪避的反击完全同源(见
     * {@link DiceCombatEvents#injectCounterDamage});施加者取伤害源的击杀归属实体,
     * 自伤(施加者即本人)与无归属来源(例如 {@code /kill} 的 generic_kill)一律不反击。
     */
    public static boolean tryNegateFatal(Player player, DamageSource source) {
        if (player == null || player.level().isClientSide()) return false;
        if (!isEquipped(player)) return false;
        if (isOnCooldown(player)) return false;
        if (ChargeManager.getStacks(player) < CHARGE_COST) return false;

        ChargeManager.consume(player, CHARGE_COST);
        ModAttachments.setAirbagCooldownEnd(player, player.level().getGameTime() + COOLDOWN_TICKS);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.merlinkitsune.astral_dice.network.ModNetwork.sendToPlayer(sp,
                    new com.merlinkitsune.astral_dice.network.ModNetwork.ActionBarMessage(
                            Component.translatable("hud.astral_dice.airbag_trigger", CHARGE_COST).withStyle(net.minecraft.ChatFormatting.YELLOW),
                            com.merlinkitsune.starenginelib.component.GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);
        counterAttacker(player, source);
        return true;
    }

    /**
     * 2026-10-08 用户裁决:安全气囊追加能力「对造成该伤害的施加者反击」。
     *
     * <p>与既有反击同源(复用 {@link DiceCombatEvents#injectCounterDamage}):伤害 = 手持最高近战武器
     * 基础伤害 + 骰战攻击力加成链 + 已装备攻击牌随机掷骰;徒手按空手 1 点起算。
     * 反击链内不会再反击 —— 递归由 {@code DiceCombatEvents} 的 counterDepth 守卫兜住。
     */
    private static void counterAttacker(Player player, DamageSource source) {
        if (source == null) return;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == player) return;
        DiceCombatEvents.injectCounterDamage(player, attacker);
    }
}
