package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 事件类型注册表。
 * 立牌等模块通过 register 注册事件类型;init 在模组启动时调用。
 */
public final class AstralEvents {
    private static final Map<ResourceLocation, AstralEventType> EVENTS = new HashMap<>();

    private AstralEvents() {
    }

    public static void register(AstralEventType type) {
        EVENTS.put(type.id(), type);
    }

    public static AstralEventType get(ResourceLocation id) {
        return EVENTS.get(id);
    }

    // 模组启动时注册事件类型(后续新增立牌/事件内容在此接入)
    public static void init() {
        register(new AstralEventType(
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "random_buff"),
                ctx -> {
                    for (LivingEntity target : ctx.targets()) {
                        applyRandomBuff(target);
                    }
                }));
        // 扫地机立牌:减少当前 30% 护甲值(持续 60 秒),仅作用于触发者自身
        register(new AstralEventType(
                ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "reduce_armor"),
                ctx -> {
                    Player p = ctx.triggerer();
                    ArmorPenaltyHandler.apply(p);
                    ModAttachments.setArmorPenaltyEnd(p, p.level().getGameTime() + 1200);
                }));
    }

    // 示例:随机施加一种增益效果
    private static void applyRandomBuff(LivingEntity target) {
        int roll = ThreadLocalRandom.current().nextInt(4);
        MobEffectInstance effect = switch (roll) {
            case 0 -> new MobEffectInstance(MobEffects.HEALTH_BOOST, 600, 0);
            case 1 -> new MobEffectInstance(MobEffects.REGENERATION, 200, 0);
            case 2 -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0);
            default -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 0);
        };
        target.addEffect(effect);
    }

    // 便捷:以 modid 命名空间注册
    public static void register(String id, EventEffect effect) {
        register(new AstralEventType(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, id), effect));
    }
}
