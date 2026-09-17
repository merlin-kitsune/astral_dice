package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.SpellDamageContext;
import com.merlinkitsune.astral_dice.combat.SpellDamageModifier;
import com.merlinkitsune.astral_dice.combat.DiceCombatEvents;
import com.merlinkitsune.astral_dice.combat.SpellDamageRegistry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 法伤(远程/魔法伤害)结算主链路:判定作用域后,由注册表修饰器聚合加成并应用。
 * 作用域白名单与加成修饰器见 {@link SpellDamageRegistry};
 * 新增卡牌/筹码/立牌对法伤的作用只需注册修饰器,无需修改本类。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class DamageEffectCardHandler {

    /** 真伤加成结算的**重入闸门**:真伤伤害源会再次进入本处理器(伤害事件对每一次 hurt 都会触发),
     *  若将来某个作用域 matcher 把它判为法伤就会无限递归;闸门只覆盖"本处理器自己发起的那一次真伤结算"。 */
    private static final ThreadLocal<Boolean> APPLYING_TRUE_BONUS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        DamageSource source = event.getSource();
        // 施法者必须为玩家
        if (!(source.getEntity() instanceof Player player)) return;
        // 目标判定与骰神赐福一致
        if (!DiceCombatEvents.isBlessingTarget(target, player)) return;
        // 作用域判定(白名单 + 军火排除)
        Entity direct = source.getDirectEntity();
        if (!SpellDamageRegistry.isSpellDamage(source, direct)) return;

        SpellDamageContext ctx = new SpellDamageContext(player, target, event, source, direct);

        // 聚合加成(按注册顺序):单次遍历收集生效修饰器,避免 isActive 重复求值
        List<SpellDamageModifier> active = new ArrayList<>();
        double bonus = 0;
        for (SpellDamageModifier modifier : SpellDamageRegistry.modifiers()) {
            if (modifier.isActive(ctx)) {
                active.add(modifier);
                bonus = modifier.apply(ctx, bonus);
            }
        }

        // 应用加成并跳数字。
        // **法伤加成按「真伤」独立结算**(2026-09-14 用户裁决):不再并进本次伤害事件——
        // 并进去的话这份加成会连同武器伤害一起吃护甲值/盔甲韧性/保护附魔的减免,与
        // 「伤害效果牌伤害纳入真伤机制」的口径不符;改走 ModDamageTypes.trueDamage(...)
        // 独立结算:直接伤害实体为空 + 击杀归属施法者(与旧 explosion(null, player) 同形状,
        // 不会被本模组或其它模组当成"玩家的直接攻击"重走骰战)。
        if (bonus > 0 && !APPLYING_TRUE_BONUS.get()) {
            APPLYING_TRUE_BONUS.set(true);
            try {
                target.hurt(com.merlinkitsune.astral_dice.damage.ModDamageTypes
                        .trueDamage(target.level(), player), (float) bonus);
            } finally {
                APPLYING_TRUE_BONUS.set(false);
            }
            com.merlinkitsune.astral_dice.network.DamageNumberPayload.send(target, (int) bonus, 0x7CFC00);
        }

        // 命中副作用(施加标记/定向爆破 AOE 等)
        for (SpellDamageModifier modifier : active) {
            modifier.onHit(ctx, bonus);
        }
    }
}
