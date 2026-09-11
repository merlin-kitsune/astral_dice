package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.chip.AirbagChipItem;
import com.merlinkitsune.astral_dice.item.chip.WhetstoneChipItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 筹码受击侧钩子(最终伤害阶段):
 * <ul>
 *   <li>安全气囊:受到致命伤害时消耗 6 点充能使本次伤害无效(1:00 冷却);</li>
 *   <li>磨刀石:低血量时受到伤害 -2;血量大于 1 点时伤害不超过剩余血量(不可被一次击倒)。</li>
 * </ul>
 *
 * 统一在 {@link LivingDamageEvent.Pre} 处理:该阶段护甲/药水减免已结算、生命扣减尚未发生,
 * 可直接改写最终伤害值(设置 0 即完全无效化)。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class ChipDamageHandler {

    private ChipDamageHandler() {
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;

        float damage = event.getNewDamage();
        if (damage <= 0.0F) return;

        // 安全气囊:致命伤害(伤害 ≥ 当前血量)时消耗 6 点充能使本次伤害无效
        if (damage >= player.getHealth() && AirbagChipItem.tryNegateFatal(player)) {
            event.setNewDamage(0.0F);
            return;
        }

        // 磨刀石:低血量减伤 + 血量 > 1 时不可被一次伤害击倒
        float modified = WhetstoneChipItem.modifyIncomingDamage(player, damage);
        if (modified != damage) {
            event.setNewDamage(modified);
        }
    }
}
