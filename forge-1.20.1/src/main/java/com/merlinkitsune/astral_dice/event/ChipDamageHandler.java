package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.chip.AirbagChipItem;
import com.merlinkitsune.astral_dice.item.chip.WhetstoneChipItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 筹码受击侧钩子(最终伤害阶段):
 * <ul>
 *   <li>安全气囊:受到致命伤害时消耗 6 点充能使本次伤害无效(1:00 冷却);</li>
 *   <li>磨刀石:低血量时受到伤害 -2;血量大于 1 点时伤害不超过剩余血量(不可被一次击倒)。</li>
 * </ul>
 *
 * 统一在 {@link LivingDamageEvent} 处理:该阶段护甲/药水减免已结算、生命扣减尚未发生,
 * 可直接改写最终伤害值(设置 0 即完全无效化)。
 *
 * <p><b>安全气囊对「无视无敌」的伤害源同样生效(2026-09-15 用户裁决)</b>:不再按
 * {@code DamageTypeTags.BYPASSES_INVULNERABILITY} 排除。原版源码依据:本事件由
 * {@code ForgeHooks.onLivingDamage} 在 {@code LivingEntity#actuallyHurt} 内派发,
 * 且 {@code actuallyHurt} 的 {@code isInvulnerableTo} 前置判定对 bypasses_invulnerability
 * 的伤害源为假 → 事件照常派发;把最终伤害改成 0 后 {@code if (f1 != 0.0F)} 分支跳过扣血,
 * 与普通致命伤完全等价。故 {@code /kill}(generic_kill)、虚空伤害与其它模组的真伤都会在
 * 气囊前止步。另有 {@link #onLivingDeath} 兜底:绕过伤害管线直接致死时仍由气囊接管。
 *
 * <p><b>保命优先级(安全气囊 &gt; 不死图腾 &gt; 末影骰子)</b>:本处理器使用 {@link EventPriority#LOWEST},
 * 即排在所有同事件伤害修正(雨中/水下放大、狂暴/虚弱印记加成等)之后执行,保证按<b>最终伤害</b>
 * 判定是否致命;而原版不死图腾的保命判定(checkTotemDeathProtection)与末影骰子的伪图腾
 * (EnderDiceHandler,监听死亡事件)都发生在本次伤害落地之后 —— 只要安全气囊成功无效化伤害
 * (生命不会降到 0),两者均不会触发,气囊恒为第一顺位。死亡事件侧的兜底用
 * {@link EventPriority#HIGHEST},同样早于末影骰子的死亡处理(默认优先级)。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class ChipDamageHandler {

    private ChipDamageHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;

        float damage = event.getAmount();
        if (damage <= 0.0F) return;

        // 安全气囊:致命伤害(伤害 ≥ 当前血量)时消耗 6 点充能使本次伤害无效。
        // 不排除「无视无敌」(bypasses_invulnerability)的伤害源:气囊必须始终有效(见类注释)。
        if (damage >= player.getHealth() && AirbagChipItem.tryNegateFatal(player)) {
            event.setAmount(0.0F);
            return;
        }

        // 磨刀石:低血量减伤 + 血量 > 1 时不可被一次伤害击倒
        float modified = WhetstoneChipItem.modifyIncomingDamage(player, damage);
        if (modified != damage) {
            event.setAmount(modified);
        }
    }

    /**
     * 安全气囊兜底:能走到死亡事件,说明这次致死<b>没有</b>经过 {@link LivingDamageEvent}
     * (例如外部直接把血量清零、或原版 {@code LivingEntity#tick} 因血量 ≤ 0 直接 {@code die()}),
     * 气囊同样必须有效 —— 取消死亡并把血量抬到至少 1(与不死图腾同款做法,但不清效果:
     * 气囊的口径是「本次伤害无效」,不是图腾的净化+回复)。
     * <p>若伤害管线已由上面无效化,则不会产生死亡事件,此处不会重复结算。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (event.isCanceled()) return;
        if (!AirbagChipItem.tryNegateFatal(player)) return;

        event.setCanceled(true);
        player.setHealth(Math.max(1.0F, player.getHealth()));
    }
}
