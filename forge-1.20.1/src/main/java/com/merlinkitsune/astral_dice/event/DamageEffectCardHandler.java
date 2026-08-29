package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.SpellDamageContext;
import com.merlinkitsune.astral_dice.combat.SpellDamageModifier;
import com.merlinkitsune.astral_dice.combat.SpellDamageRegistry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import com.merlinkitsune.astral_dice.network.ModNetwork.DamageNumberMessage;

/**
 * 法伤(远程/魔法伤害)结算主链路:判定作用域后,由注册表修饰器聚合加成并应用。
 * 作用域白名单与加成修饰器见 {@link SpellDamageRegistry};
 * 新增卡牌/筹码/立牌对法伤的作用只需注册修饰器,无需修改本类。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class DamageEffectCardHandler {

    @SubscribeEvent
    public static void onLivingDamagePre(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageSource source = event.getSource();
        // 施法者必须为玩家
        if (!(source.getEntity() instanceof Player player)) return;
        // 目标判定与骰神赐福一致
        if (!ModEventHandlers.isBlessingTarget(target, player)) return;
        // 作用域判定(白名单 + 军火排除)
        Entity direct = source.getDirectEntity();
        if (!SpellDamageRegistry.isSpellDamage(source, direct)) return;

        SpellDamageContext ctx = new SpellDamageContext(player, target, event, source, direct);

        // 聚合加成(按注册顺序)
        double bonus = 0;
        for (SpellDamageModifier modifier : SpellDamageRegistry.modifiers()) {
            if (modifier.isActive(ctx)) {
                bonus = modifier.apply(ctx, bonus);
            }
        }

        // 应用加成并跳数字
        if (bonus > 0) {
            event.setAmount(event.getAmount() + (float) bonus);
            sendBonusDamageNumber(target, (int) bonus);
        }

        // 命中副作用(施加标记/定向爆破 AOE 等)
        for (SpellDamageModifier modifier : SpellDamageRegistry.modifiers()) {
            if (modifier.isActive(ctx)) {
                modifier.onHit(ctx, bonus);
            }
        }
    }

    // 法伤加成跳数字(草绿色 0x7CFC00)
    private static void sendBonusDamageNumber(net.minecraft.world.entity.LivingEntity target, int bonus) {
        if (target.level().isClientSide()) return;
        var packet = new com.merlinkitsune.astral_dice.network.ModNetwork.DamageNumberMessage(
                target.getId(), bonus, 0x7CFC00);
        com.merlinkitsune.astral_dice.network.ModNetwork.sendToPlayersTrackingEntity(target, packet);
        if (target instanceof net.minecraft.server.level.ServerPlayer serverTarget) {
            com.merlinkitsune.astral_dice.network.ModNetwork.sendToPlayer(serverTarget, packet);
        }
    }
}
