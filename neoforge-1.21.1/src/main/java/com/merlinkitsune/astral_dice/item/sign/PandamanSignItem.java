package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.FriendshipBadgeChipItem;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 肉弹战车立牌(命名:pandaman,稀有)。
 *
 * <p>被动「有好有坏」:
 * <ul>
 *   <li>使用「汉堡」后获得 2 点最大生命上限(总最大生命不超过 100 点,卸下清除);</li>
 *   <li>使用「汉堡」或「巧克力蛋糕」后,使 8 格内友方玩家获得 2 点治疗与 1 点治愈。</li>
 * </ul>
 *
 * <p>主动「大吃特吃」(默认 180 秒冷却):
 * <ul>
 *   <li>获得一张随机治疗类效果牌(汉堡/巧克力蛋糕/奢华大餐);</li>
 *   <li>对 16 格内所有敌对目标施加「嘲讽」(1:00);</li>
 *   <li>被「嘲讽」目标攻击施加者时,触发反击(不消耗反击层数);</li>
 *   <li>反击时若生命值未满,额外增加缺失生命值等值的伤害(常驻被动)。</li>
 * </ul>
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class PandamanSignItem extends BaseSignItem {
    /** 被动队友范围 */
    public static final double FRIENDLY_RANGE = 8.0;
    /** 主动嘲讽范围 */
    public static final double TAUNT_RANGE = 16.0;
    /** 嘲讽时长(tick) */
    public static final int TAUNT_DURATION_TICKS = 1200;
    /** 最大生命值硬上限(点) */
    public static final double MAX_HEALTH_CAP = 100.0;
    /** 每次汉堡增加的最大生命值(点) */
    public static final int MAX_HEALTH_PER_HAMBURGER = 2;
    /** 友方直接治疗量(点) */
    public static final float FRIENDLY_HEAL = 2.0f;
    /** 友方治愈点数 */
    public static final int FRIENDLY_HEALING_POINTS = 1;
    /** 最大生命加成修饰器 key */
    private static final String MAX_HEALTH_MODIFIER_KEY = "pandaman_max_health";

    public PandamanSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        refreshMaxHealthBonus(player);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        ModAttachments.setPandamanMaxHealthBonus(player, 0);
        refreshMaxHealthBonus(player);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 1. 获得随机治疗类效果牌
        ItemStack randomHealingCard = switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> new ItemStack(ModItems.HAMBURGER.get());
            case 1 -> new ItemStack(ModItems.CHOCOLATE_CAKE.get());
            default -> new ItemStack(ModItems.LUXURY_FEAST.get());
        };
        VitaminPillChipItem.giveCard(player, randomHealingCard);

        // 2. 嘲讽 16 格内所有敌对目标(含 Boss)
        AABB aabb = player.getBoundingBox().inflate(TAUNT_RANGE);
        List<LivingEntity> nearby = player.level().getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e instanceof Enemy && e.isAlive());
        for (LivingEntity target : nearby) {
            target.addEffect(new MobEffectInstance(ModEffects.PANDAMAN_TAUNT,
                    TAUNT_DURATION_TICKS, 0, false, true));
            ModAttachments.setPandamanTauntSource(target, Optional.of(player.getUUID()));
            if (target instanceof Mob mob) {
                mob.setTarget(player);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    // 主动技能 ActionBar 反馈
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.starenginelib.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.PANDAMAN_SIGN.get())) {
            sendSignActionBar(event.getPlayer(), "msg.astral_dice.pandaman_active_used");
            event.setHandled();
        }
    }

    // 嘲讽自然结束时清除来源
    @SubscribeEvent
    public static void onPandamanTauntExpired(MobEffectEvent.Expired event) {
        if (event.getEntity().level().isClientSide()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null) return;
        if (effect.getEffect().value() != ModEffects.PANDAMAN_TAUNT.get()) return;
        ModAttachments.setPandamanTauntSource(event.getEntity(), Optional.empty());
    }

    // 玩家是否佩戴肉弹战车立牌
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.PANDAMAN_SIGN.get())).isPresent();
    }

    // 使用汉堡/巧克力蛋糕后触发被动
    public static void onHealingFoodUsed(Player player, boolean isHamburger) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;

        // 使用汉堡:获得 2 点最大生命值上限(不超过 100,卸下清除)
        if (isHamburger) {
            addMaxHealthBonus(player);
        }

        // 使用汉堡/巧克力蛋糕:使 8 格内友方玩家获得 2 点治疗和 1 点治愈
        AABB aabb = player.getBoundingBox().inflate(FRIENDLY_RANGE);
        List<Player> nearby = player.level().getEntitiesOfClass(Player.class, aabb,
                p -> p.isAlive() && isFriendly(player, p));
        for (Player friendly : nearby) {
            friendly.heal(FRIENDLY_HEAL);
            HealingManager.add(friendly, FRIENDLY_HEALING_POINTS);
            if (friendly != player) {
                FriendshipBadgeChipItem.onHealApplied(player, friendly);
            }
        }
    }

    // 友方判定:自己 / 双方无队伍 / 同队(与奢华大餐、史莱姆立牌规则一致)
    private static boolean isFriendly(Player self, Player other) {
        return self == other || self.getTeam() == null || other.getTeam() == null
                || self.getTeam() == other.getTeam();
    }

    // 吃汉堡累计最大生命加成
    private static void addMaxHealthBonus(Player player) {
        int currentBonus = ModAttachments.getPandamanMaxHealthBonus(player);
        if (player.getMaxHealth() >= MAX_HEALTH_CAP) return;
        int currentMax = (int) Math.floor(player.getMaxHealth());
        int room = (int) Math.floor(MAX_HEALTH_CAP) - currentMax;
        if (room <= 0) return;
        int gain = Math.min(MAX_HEALTH_PER_HAMBURGER, room);
        ModAttachments.setPandamanMaxHealthBonus(player, currentBonus + gain);
        refreshMaxHealthBonus(player);
    }

    // 刷新 MAX_HEALTH 瞬态修饰器:数值为当前累计加成,0 时移除
    public static void refreshMaxHealthBonus(Player player) {
        if (player == null || player.level().isClientSide()) return;
        AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        int bonus = ModAttachments.getPandamanMaxHealthBonus(player);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                AstralDiceMod.MODID, MAX_HEALTH_MODIFIER_KEY);
        AttributeModifier existing = attr.getModifier(id);
        if (bonus <= 0) {
            if (existing != null) attr.removeModifier(id);
            return;
        }
        if (existing == null || existing.amount() != bonus) {
            attr.removeModifier(id);
            attr.addTransientModifier(new AttributeModifier(id, bonus,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
