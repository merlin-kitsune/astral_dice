package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.chip.AirbagChipItem;
import com.merlinkitsune.astral_dice.item.chip.WhetstoneChipItem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
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
 * <p><b>保命优先级(安全气囊 &gt; 不死图腾 &gt; 末影骰子)</b>:本处理器使用 {@link EventPriority#LOWEST},
 * 即排在所有同事件伤害修正(雨中/水下放大、狂暴/虚弱印记加成等)之后执行,保证按<b>最终伤害</b>
 * 判定是否致命;而原版不死图腾的保命判定(checkTotemDeathProtection)与末影骰子的伪图腾
 * (EnderDiceHandler,监听死亡事件)都发生在本次伤害落地之后 —— 只要安全气囊成功无效化伤害
 * (生命不会降到 0),两者均不会触发,气囊恒为第一顺位。
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
        // 与不死图腾/末影骰子一致:无视无敌(bypasses_invulnerability)的致死伤害不参与。
        if (damage >= player.getHealth()
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && AirbagChipItem.tryNegateFatal(player)) {
            event.setAmount(0.0F);
            return;
        }

        // 磨刀石:低血量减伤 + 血量 > 1 时不可被一次伤害击倒
        float modified = WhetstoneChipItem.modifyIncomingDamage(player, damage);
        if (modified != damage) {
            event.setAmount(modified);
        }
    }
}
