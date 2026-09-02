package com.merlinkitsune.astral_dice.item.chip;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 夹心饼干筹码(一般/可口/美味):最大生命值 +4/+8/+12(属性修饰器,装备期间生效)。
 * 夹心饼干-美味额外被动:生命值低于最大生命值一半时,每 1:00 获得 1 层「反击」
 * (触发冷却见附件 sandwich_high_counter_cooldown_end)。
 */
public class SandwichChipItem extends BaseChipItem {
    /** 夹心饼干-一般最大生命加成 */
    public static final int HEALTH_LOW = 4;
    /** 夹心饼干-可口最大生命加成 */
    public static final int HEALTH_MEDIUM = 8;
    /** 夹心饼干-美味最大生命加成 */
    public static final int HEALTH_HIGH = 12;
    /** 夹心饼干-美味:低生命值反击触发间隔(tick,1 分钟) */
    public static final int COUNTER_COOLDOWN_TICKS = 1200;

    private final int healthBonus;
    /** 是否为夹心饼干-美味(低生命值时获得反击层数) */
    private final boolean grantCounterOnLowHp;

    public SandwichChipItem(Properties properties, int healthBonus, boolean grantCounterOnLowHp) {
        super(properties);
        this.healthBonus = healthBonus;
        this.grantCounterOnLowHp = grantCounterOnLowHp;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, java.util.UUID id, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = HashMultimap.create();
        map.put(Attributes.MAX_HEALTH,
                new AttributeModifier(attributeModifierId("health"), "health", healthBonus,
                        AttributeModifier.Operation.ADDITION));
        return map;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 夹心饼干-美味被动:生命值低于最大生命值一半时,每 1:00 获得 1 层「反击」
        if (!grantCounterOnLowHp) return;
        long now = player.level().getGameTime();
        if (now < ModAttachments.getSandwichHighCounterCooldownEnd(player)) return;
        if (player.getHealth() >= player.getMaxHealth() / 2.0f) return;
        com.merlinkitsune.astral_dice.effect.CounterattackEffect.addStacks(player, 1);
        ModAttachments.setSandwichHighCounterCooldownEnd(player, now + COUNTER_COOLDOWN_TICKS);
    }
}
