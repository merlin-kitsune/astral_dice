package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;
import com.merlinkitsune.astral_dice.combat.DamageCardFxScheduler;
import com.merlinkitsune.astral_dice.event.LivingPageFlightScheduler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 电击手套筹码:
 * <ul>
 *   <li>使用效果牌时,充能层数 +1;</li>
 *   <li>使用**伤害效果牌**且充能层数不少于 {@value #CHARGE_REQUIRED} 时,消耗 4 层充能 ⇒
 *       **该牌本次造成的**远程/魔法伤害会同时命中目标 {@value #AOE_RADIUS} 格范围内的其他目标
 *       (每张牌各自结算;期间其它来源的远程/魔法伤害不受影响)。</li>
 * </ul>
 *
 * <p><b>2026-10-05 重写(用户裁决)</b>:旧实现是「玩家级武装、跨伤害实例存活到效果牌周期结束」——
 * 武装期间**任意**一次远程/魔法伤害(含原版箭矢、别家法术)都会吃掉扩散 = **周期溅伤**;现已废弃。
 *
 * <p><b>现在的绑定方式</b>:充能在使用该牌时扣除,并把「本次可扩散」写进**那张牌自己的伤害载体**
 * ({@code DamageCardFxScheduler.Pending#glovePaid} / {@code LivingPageFlightScheduler.Pending#glovePaid});
 * 只有该载体在命中时点结算**主目标**伤害的那一瞬,法伤链里的电击手套修饰器才生效
 * (窗口见 {@code DiceCombatEvents#runAsGloveSplashInstance})。
 * ⇒ 定向爆破的 6 格 AOE 波及命中**不在**该窗口内,不会二次触发手套扩散。
 */
public class ElectricGloveChipItem extends BaseChipItem {
    /** 触发法伤扩散所需的最低充能层数 */
    public static final int CHARGE_REQUIRED = 4;
    /** 使用效果牌获得的充能层数 */
    public static final int CHARGE_GAIN_PER_CARD = 1;
    /** 法伤扩散的额外命中半径(格) */
    public static final int AOE_RADIUS = 3;

    public ElectricGloveChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴电击手套 */
    public static boolean isEquipped(Player player) {
        // 总闸门(骰子装备和卸除调整):未佩戴骰子 ⇒ 本件功能一律不生效(数值保留)
        if (player == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.ELECTRIC_GLOVE_CHIP.get())).isPresent();
    }

    /**
     * 该效果牌是否属于「伤害效果牌」——委托 {@link BaseEffectCardItem#isDamageEffectCard}
     * (单一事实源:魔法箭袋的「第一张效果牌」追踪用同一判定)。
     */
    public static boolean isDamageEffectCard(ItemStack cardStack) {
        return BaseEffectCardItem.isDamageEffectCard(cardStack);
    }

    /**
     * 使用效果牌时调用(所有效果牌):充能 +1;
     * 若为伤害效果牌且充能不少于 {@link #CHARGE_REQUIRED} 层,
     * 消耗 4 层充能并把「本次可扩散」绑定到**这张牌本次投出的那一次伤害**上。
     *
     * <p>⚠️ 调用点({@code BaseEffectCardItem})在 {@code applyEffect} **之后** ⇒ 此时该牌已经把自己的
     * 伤害载体登记进调度器,故这里能按牌种认领到它;认领失败(该牌本次没投出载体:目标在选中后被
     * 击杀 / 投出失败)则**不扣充能**。
     */
    public static void onEffectCardUsed(Player player, ItemStack cardStack) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ChargeManager.addStacks(player, CHARGE_GAIN_PER_CARD);

        if (!isDamageEffectCard(cardStack)) return;
        if (ChargeManager.getStacks(player) < CHARGE_REQUIRED) return;

        // 认领本次伤害载体(按牌种配对,不会认领到别的牌仍在飞行中的旧载体)
        boolean claimed = cardStack.is(ModItems.LIVING_PAGE.get())
                ? LivingPageFlightScheduler.markLatestGlovePaid(serverPlayer)
                : DamageCardFxScheduler.markLatestGlovePaid(serverPlayer, cardStack);
        if (!claimed) return;

        ChargeManager.consume(player, CHARGE_REQUIRED);
    }
}
