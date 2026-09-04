package com.merlinkitsune.astral_dice.item.sign;
import com.merlinkitsune.astral_dice.item.CuriosCompat;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.event.ModEffectRemoval;

import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 骇客立牌(命名:nancy_lu)。
 *
 * <p>被动"网络防火墙":
 * - 免疫末影珍珠传送时的伤害;
 * - 骰神赐福结束后,若周围 6 格内没有敌对生物:攻击力 +3 并获得一张随机战斗牌;
 *   否则防御力 +3。被动类型每次刷新覆盖旧类型,不能叠加。
 *
 * <p>主动"远程侵入":
 * - 立即进入完全隐身状态(最多持续 30 秒);
 * - 攻击敌对目标或玩家时解除隐身,并消耗一张随机战斗牌;
 * - 按该牌费用 ×2 提升攻击力,持续 2:00;攻击力加成最低 +2,
 *   主物品栏无战斗牌可消耗时同样获得保底 +2。
 */
@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class NancyLuSignItem extends BaseSignItem {
    public static final int PASSIVE_NONE = 0;
    public static final int PASSIVE_ATTACK = 1;
    public static final int PASSIVE_DEFENSE = 2;
    public static final int PASSIVE_BONUS = 3;
    public static final double PASSIVE_RANGE = 6.0;
    public static final int ACTIVE_DURATION_TICKS = 2400;
    public static final int INVULNERABLE_TICKS = 60;
    public static final int HIDDEN_DURATION_TICKS = 600;
    public static final int ACTIVE_BONUS_MULTIPLIER = 2;
    public static final int ACTIVE_MIN_BONUS = 2;
    public static final int ENDER_PEARL_IMMUNE_TICKS = 20;

    public NancyLuSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        long now = player.level().getGameTime();

        // 主动无敌到期
        if (player.isInvulnerable() && now >= ModAttachments.getNancyLuInvulnerableUntil(player)) {
            player.setInvulnerable(false);
            ModAttachments.setNancyLuInvulnerableUntil(player, 0);
        }
        // 主动完全隐身到期(仅立牌自身授予的隐身到期时才移除,避免误清其他来源的隐身)
        long hiddenUntil = ModAttachments.getNancyLuHiddenUntil(player);
        if (hiddenUntil > 0 && now >= hiddenUntil) {
            ModAttachments.setNancyLuHiddenUntil(player, 0);
            player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        }
        // 主动攻击力加成到期
        if (now >= ModAttachments.getNancyLuActiveBonusUntil(player)) {
            ModAttachments.setNancyLuActiveBonus(player, 0);
            ModAttachments.setNancyLuActiveBonusUntil(player, 0);
            ModEffectRemoval.remove(player, ModEffects.NANCY_LU_HACK.get());
        }
        // 防御力折算为真实护甲(1 防御力 = 2 护甲值;被动类型为防御时护甲 +6)
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "nancy_lu_def_armor", getDefenseBonus(player));
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 仅清除立牌自身授予的状态(附件标记仍有效时),不触碰其他来源的公共数值:
        // 无敌/隐身可能由其他模组或原版机制授予,卸载立牌不得一并清除
        if (ModAttachments.getNancyLuInvulnerableUntil(player) > 0) {
            player.setInvulnerable(false);
        }
        if (ModAttachments.getNancyLuHiddenUntil(player) > 0) {
            player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        }
        ModAttachments.setNancyLuPassiveType(player, PASSIVE_NONE);
        ModAttachments.setNancyLuActiveBonus(player, 0);
        ModAttachments.setNancyLuActiveBonusUntil(player, 0);
        ModAttachments.setNancyLuInvulnerableUntil(player, 0);
        ModAttachments.setNancyLuHiddenUntil(player, 0);
        ModAttachments.setNancyLuEnderPearlImmuneUntil(player, 0);
        ModEffectRemoval.remove(player, ModEffects.NANCY_LU_HACK.get());
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(player, "nancy_lu_def_armor", 0);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        long now = level.getGameTime();

        // 立即进入完全隐身状态(最多持续 30 秒)
        EffectTimerGuard.apply(player, new MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY,
                HIDDEN_DURATION_TICKS, 0, false, true, true));
        ModAttachments.setNancyLuHiddenUntil(player, now + HIDDEN_DURATION_TICKS);
        // 清除附近已经锁定该玩家的生物目标,确保“绝对无法被生物索敌”
        clearNearbyMobTargets(player);

        return InteractionResultHolder.success(stack);
    }

    // 隐身状态下攻击敌对目标/玩家时调用:解除隐身,尝试消耗战斗牌并按费用*2提升攻击力
    public static void onAttackWhileHidden(Player player) {
        if (player == null || player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        if (now >= ModAttachments.getNancyLuHiddenUntil(player)) return;
        if (!player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) return;

        // 解除完全隐身
        ModAttachments.setNancyLuHiddenUntil(player, 0);
        player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);

        // 消耗一张随机战斗牌并按费用×2 提升攻击力;攻击力加成最低 +2。
        // 主物品栏无战斗牌可消耗时,同样获得保底 +2 攻击力加成。
        ItemStack consumed = findAndConsumeRandomBattleCard(player);
        int bonus = ACTIVE_MIN_BONUS;
        if (consumed != null) {
            String typeId = CardRegistry.itemToType(consumed);
            int cost = typeId != null ? CardRegistry.cost(typeId, player) : 1;
            bonus = Math.max(ACTIVE_MIN_BONUS, cost * ACTIVE_BONUS_MULTIPLIER);
        }
        ModAttachments.setNancyLuActiveBonus(player, bonus);
        ModAttachments.setNancyLuActiveBonusUntil(player, now + ACTIVE_DURATION_TICKS);
        player.addEffect(new MobEffectInstance(ModEffects.NANCY_LU_HACK.get(),
                ACTIVE_DURATION_TICKS, 0, false, true, true));
    }

    // 主动技能 ActionBar:完全隐身提示(注册到主动技能响应事件)
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.astral_dice.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.NANCY_LU_SIGN.get())) {
            sendSignActionBar(event.getPlayer(), "msg.astral_dice.nancy_lu_active", HIDDEN_DURATION_TICKS / 20);
            event.setHandled();
        }
    }

    // === 被动 ===

    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.NANCY_LU_SIGN.get())).isPresent();
    }

    // 是否处于完全隐身(绝对无法被生物索敌)状态
    public static boolean isHidden(Player player) {
        if (player == null || player.level().isClientSide()) return false;
        if (!isEquipped(player)) return false;
        if (!player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) return false;
        return player.level().getGameTime() < ModAttachments.getNancyLuHiddenUntil(player);
    }

    public static int getAttackBonus(Player player) {
        return isEquipped(player) && ModAttachments.getNancyLuPassiveType(player) == PASSIVE_ATTACK
                ? PASSIVE_BONUS : 0;
    }

    public static int getDefenseBonus(Player player) {
        return isEquipped(player) && ModAttachments.getNancyLuPassiveType(player) == PASSIVE_DEFENSE
                ? PASSIVE_BONUS : 0;
    }

    public static int getActiveAttackBonus(Player player) {
        if (!isEquipped(player)) return 0;
        if (player.hasEffect(ModEffects.NANCY_LU_HACK.get())) {
            return ModAttachments.getNancyLuActiveBonus(player);
        }
        return 0;
    }

    // 骰神赐福结束时调用:刷新被动类型(覆盖旧类型,不能叠加)
    public static void onDiceBlessingEnded(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        boolean hostileNearby = !player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(PASSIVE_RANGE),
                e -> e instanceof Enemy && e.isAlive()).isEmpty();
        if (hostileNearby) {
            ModAttachments.setNancyLuPassiveType(player, PASSIVE_DEFENSE);
        } else {
            ModAttachments.setNancyLuPassiveType(player, PASSIVE_ATTACK);
            RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.BATTLE);
        }
    }

    // === 主动辅助 ===

    // 清除附近已把该玩家设为目标生物的仇恨
    private static void clearNearbyMobTargets(Player player) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(64.0), mob -> mob.getTarget() == player)) {
            mob.setTarget(null);
        }
    }

    private static ItemStack findAndConsumeRandomBattleCard(Player player) {
        List<ItemStack> candidates = new ArrayList<>();
        // 仅主物品栏(平衡调整:不再从末影箱/精妙背包等容器能力中消耗)
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && CardRegistry.itemToType(stack) != null) {
                candidates.add(stack);
            }
        }
        if (candidates.isEmpty()) return null;
        ItemStack chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        chosen.shrink(1);
        return chosen.copy();
    }

    // 骇客立牌:完全隐身状态下攻击敌对目标/玩家 → 解除隐身并触发战斗牌加成
    @SubscribeEvent
    public static void onNancyLuAttackWhileHidden(
            net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!NancyLuSignItem.isEquipped(player)) return;
        net.minecraft.world.entity.Entity target = event.getTarget();
        if (!(target instanceof net.minecraft.world.entity.monster.Enemy)
                && !(target instanceof Player)) return;
        NancyLuSignItem.onAttackWhileHidden(player);
    }


    // 骇客立牌:末影珍珠落地时记录短时免疫窗口,免疫随后的传送摔落伤害
    @SubscribeEvent
    public static void onNancyLuEnderPearlImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl pearl
                && pearl.getOwner() instanceof Player player
                && NancyLuSignItem.isEquipped(player)) {
            ModAttachments.setNancyLuEnderPearlImmuneUntil(player,
                    player.level().getGameTime() + com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem.ENDER_PEARL_IMMUNE_TICKS);
        }
    }


    // 骇客立牌:免疫末影珍珠传送产生的摔落伤害
    @SubscribeEvent
    public static void onNancyLuEnderPearlDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!NancyLuSignItem.isEquipped(player)) return;
        if (!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) return;
        if (player.level().getGameTime() < ModAttachments.getNancyLuEnderPearlImmuneUntil(player)) {
            event.setAmount(0);
        }
    }

}
