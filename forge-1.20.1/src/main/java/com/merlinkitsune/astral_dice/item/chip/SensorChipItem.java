package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.merlinkitsune.starenginelib.item.CuriosCompat;

/**
 * 感测装置筹码(史诗 / 标记)。
 *
 * <ul>
 *   <li><b>效果A</b>:佩戴者攻击的目标每有 {@value #MARK_PER_DICE_MALUS} 层标记,该目标的
 *       <b>战斗骰点 -1</b>(最多 {@value #MAX_DICE_MALUS}),下限 0 —— 只作用于**目标方骰点**,
 *       不影响攻击方({@link #reduceTargetDice} 在 {@code DiceCombatEvents} 的"目标掷骰"处调用);</li>
 *   <li><b>效果B</b>:佩戴者**闪避成功**后,攻击者(若为 {@code LivingEntity})获得 1 层标记
 *       ({@link #onDodgeSuccess} 由 {@code DiceCombatEvents#applyDodgeCancel} 这一**统一取消入口**调用
 *       ⇒ 破绽闪避 / 肾上腺素闪避 / 魔女漂浮闪避等全部来源一并覆盖)。</li>
 * </ul>
 */
public class SensorChipItem extends BaseChipItem {
    /** 每多少层标记 ⇒ 目标战斗骰点 -1(2026-10-15 用户裁决:4 → 3)。 */
    public static final int MARK_PER_DICE_MALUS = 3;

    /** 目标战斗骰点扣减上限。 */
    public static final int MAX_DICE_MALUS = 4;

    public SensorChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴本筹码(总闸门:未佩戴骰子 ⇒ 本件功能一律不生效)。 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.SENSOR_CHIP.get())).isPresent();
    }

    /**
     * 效果A:按目标当前标记层数扣减**目标方**战斗骰点(下限 0)。
     *
     * @param attacker   攻击方(须佩戴本筹码)
     * @param target     防守方(标记层数取自其身上的"标记"效果)
     * @param targetDice 目标本次掷出的战斗骰点
     * @return 扣减后的骰点
     */
    public static int reduceTargetDice(Player attacker, LivingEntity target, int targetDice) {
        if (targetDice <= 0) return targetDice;
        if (attacker == null || target == null) return targetDice;
        if (!isEquipped(attacker)) return targetDice;
        int malus = Math.min(MAX_DICE_MALUS, MarkManager.getLevel(target) / MARK_PER_DICE_MALUS);
        return Math.max(0, targetDice - malus);
    }

    /**
     * 效果B:闪避成功后给攻击者施加 1 层标记。
     *
     * @param victim 闪避成功的一方(须佩戴本筹码)
     * @param source 本次被闪避的伤害来源(取 {@code getEntity()} 作为攻击者;
     *               ⚠️ 用 {@code getEntity()} 而不是 {@code getDirectEntity()} ⇒ 箭矢等弹射物
     *               同样记在发射者头上,与枪匠"破绽闪避"的判定口径一致)
     */
    public static void onDodgeSuccess(Player victim, DamageSource source) {
        if (victim == null || source == null) return;
        if (victim.level().isClientSide()) return;
        if (!isEquipped(victim)) return;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == victim) return;
        MarkManager.apply(attacker);
    }
}
