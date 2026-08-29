package com.merlinkitsune.astral_dice.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;
import com.merlinkitsune.astral_dice.event.DamageEffectCardHandler;

/**
 * 一次法伤(远程/魔法伤害)结算的上下文:由 DamageEffectCardHandler 在作用域判定通过后构建,
 * 供 {@link SpellDamageModifier} 读取。curios 查询带惰性缓存,避免每次伤害重复获取。
 */
public class SpellDamageContext {

    /** 施法者(玩家) */
    public final Player attacker;
    /** 伤害目标 */
    public final LivingEntity target;
    /** 本次伤害事件 */
    public final LivingHurtEvent event;
    /** 伤害来源 */
    public final DamageSource source;
    /** 直接伤害实体(弹射物/施法者等) */
    public final Entity directEntity;

    private Optional<ICuriosItemHandler> curiosCache = null;

    public SpellDamageContext(Player attacker, LivingEntity target, LivingHurtEvent event,
                              DamageSource source, Entity directEntity) {
        this.attacker = attacker;
        this.target = target;
        this.event = event;
        this.source = source;
        this.directEntity = directEntity;
    }

    // 攻击者 curios(惰性缓存)
    public Optional<ICuriosItemHandler> curios() {
        if (curiosCache == null) {
            curiosCache = CuriosCompat.getCuriosInventory(attacker);
        }
        return curiosCache;
    }

    // 攻击者是否佩戴指定物品(curio)
    public boolean hasCurio(Item item) {
        return curios().map(h -> h.findFirstCurio(s -> s.is(item)).isPresent()).orElse(false);
    }
}
