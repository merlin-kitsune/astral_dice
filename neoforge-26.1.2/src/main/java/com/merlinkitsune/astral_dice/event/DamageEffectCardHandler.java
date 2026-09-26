package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.audio.ModSounds;
import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.combat.DamageNumberAggregator;
import com.merlinkitsune.astral_dice.combat.SpellDamageContext;
import com.merlinkitsune.astral_dice.combat.SpellDamageModifier;
import com.merlinkitsune.astral_dice.combat.DiceCombatEvents;
import com.merlinkitsune.astral_dice.combat.SpellDamageRegistry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
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

    /** 活体书页命中的「重击」音效门槛:本次**完整伤害**(与 HUD 跳字同值) >= 该值即播 bighit。 */
    private static final int LIVING_PAGE_BIG_HIT_THRESHOLD = 8;

    @SubscribeEvent(priority = EventPriority.NORMAL)
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
        // 2026-09-26:本处原有的「HUD 数显 = getNewDamage() + bonus」直发已移除 —— 显示值**同源**,
        // 但改由 combat/DamageNumberAggregator 在伤害管线终值(Post#getHealthDamage())上取,
        // 并与基础伤害累加成一个法伤总值(旧写法在「吸收(黄心)」一环与真实掉血对不上)。
        if (bonus > 0 && !APPLYING_TRUE_BONUS.get()) {
            // ① 本次命中的**基础伤害**也归入法伤组。必须显式登记:这条命中绝大多数是**原版**伤害源
            //    (箭矢 arrow / 投掷物 / 其它模组法术),聚合器的类型回落认不出来。
            //    登记后,聚合器会把它与 ② 的法伤加成累加成一个「法伤总值」——
            //    与旧实现「发 getNewDamage() + bonus」的**显示值同源**,但改用了伤害管线终值。
            DamageNumberAggregator.tag(target, source, DamageNumberAggregator.Group.SPELL);
            APPLYING_TRUE_BONUS.set(true);
            try {
                var bonusSource = com.merlinkitsune.astral_dice.damage.ModDamageTypes
                        .trueDamage(target.level(), player);
                // ② 法伤加成段(独立真伤)。组别同样必须显式登记:astral_dice:true_damage 被多路复用
                //    (大当家溅射=攻击力类红 / 这里与效果牌范围波及=法伤类绿),只看类型区分不出来。
                DamageNumberAggregator.tag(target, bonusSource, DamageNumberAggregator.Group.SPELL);
                target.hurt(bonusSource, (float) bonus);
            } finally {
                APPLYING_TRUE_BONUS.set(false);
            }
        }

        // 活体书页命中音效:**按本次命中的完整伤害分档**，取值与上面的 HUD 跳字完全同源
        // （原始基础伤害 + 法伤加成，取整）⇒ 玩家听到的强弱与看到的数字一致。
        // 判据是伤害类型本身：astral_dice:card_spell 目前的唯一产出路径就是活体书页命中
        // （见 damage/ModDamageTypes#cardSpell 与 combat/LivingPageImpact#resolve）。
        if (source.is(com.merlinkitsune.astral_dice.damage.ModDamageTypes.CARD_SPELL)) {
            int dealt = (int) Math.round(event.getNewDamage() + bonus);
            SoundPlayback.playAt(target.level(), target.getX(), target.getY(), target.getZ(),
                    dealt >= LIVING_PAGE_BIG_HIT_THRESHOLD
                            ? ModSounds.DAMAGE_EFFECT_CARD_BIGHIT.get()
                            : ModSounds.DAMAGE_EFFECT_CARD_HIT.get());
        }

        // 命中副作用(施加标记/定向爆破 AOE 等)
        for (SpellDamageModifier modifier : active) {
            modifier.onHit(ctx, bonus);
        }
    }
}
