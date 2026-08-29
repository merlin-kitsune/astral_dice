package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;

import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 骇客立牌(命名:nancy_lu)。
 *
 * <p>被动"网络防火墙":
 * - 免疫末影珍珠传送时的伤害;
 * - 骰神赐福结束后,若周围 6 格内没有敌对生物:攻击力 +3 并获得一张随机战斗牌;
 *   否则防御力 +3。被动类型每次刷新覆盖旧类型,不能叠加。
 *
 * <p>主动"远程骇入":
 * - 立即进入完全隐身状态(最多持续 30 秒);
 * - 攻击敌对目标或玩家时解除隐身,并消耗一张随机战斗牌;
 * - 按该牌费用 ×2 提升攻击力,持续 2:00;若无战斗牌可用则不触发加成。
 */
public class NancyLuSignItem extends BaseSignItem {
    public static final int PASSIVE_NONE = 0;
    public static final int PASSIVE_ATTACK = 1;
    public static final int PASSIVE_DEFENSE = 2;
    public static final int PASSIVE_BONUS = 3;
    public static final double PASSIVE_RANGE = 6.0;
    public static final double ACTIVE_RANGE = 32.0;
    public static final int ACTIVE_DURATION_TICKS = 2400;
    public static final int INVULNERABLE_TICKS = 60;
    public static final int HIDDEN_DURATION_TICKS = 600;
    public static final int ACTIVE_BONUS_MULTIPLIER = 2;
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
        // 主动完全隐身到期
        if (now >= ModAttachments.getNancyLuHiddenUntil(player)) {
            ModAttachments.setNancyLuHiddenUntil(player, 0);
            player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        }
        // 主动攻击力加成到期
        if (now >= ModAttachments.getNancyLuActiveBonusUntil(player)) {
            ModAttachments.setNancyLuActiveBonus(player, 0);
            ModAttachments.setNancyLuActiveBonusUntil(player, 0);
            player.removeEffect(ModEffects.NANCY_LU_HACK);
        }
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        ModAttachments.setNancyLuPassiveType(player, PASSIVE_NONE);
        ModAttachments.setNancyLuActiveBonus(player, 0);
        ModAttachments.setNancyLuActiveBonusUntil(player, 0);
        ModAttachments.setNancyLuInvulnerableUntil(player, 0);
        ModAttachments.setNancyLuHiddenUntil(player, 0);
        ModAttachments.setNancyLuEnderPearlImmuneUntil(player, 0);
        player.setInvulnerable(false);
        player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        player.removeEffect(ModEffects.NANCY_LU_HACK);
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

        // 消耗一张随机战斗牌;若无牌可用则不触发攻击力加成
        ItemStack consumed = findAndConsumeRandomBattleCard(player);
        if (consumed == null) return;
        String typeId = CardRegistry.itemToType(consumed);
        int cost = typeId != null ? CardRegistry.cost(typeId, player) : 1;
        ModAttachments.setNancyLuActiveBonus(player, cost * ACTIVE_BONUS_MULTIPLIER);
        ModAttachments.setNancyLuActiveBonusUntil(player, now + ACTIVE_DURATION_TICKS);
        player.addEffect(new MobEffectInstance(ModEffects.NANCY_LU_HACK,
                ACTIVE_DURATION_TICKS, 0, false, true, true));
    }

    // === 被动 ===

    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
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
        if (player.hasEffect(ModEffects.NANCY_LU_HACK)) {
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

    private static LivingEntity findHighestHealthTarget(Player player) {
        List<LivingEntity> candidates = new ArrayList<>();
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(ACTIVE_RANGE), e -> e.isAlive() && e != player)) {
            if (e instanceof Enemy || e instanceof Player) {
                candidates.add(e);
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Float.compare(b.getHealth(), a.getHealth()));
        return candidates.get(0);
    }

    private static void teleportNear(Player player, LivingEntity target) {
        Level level = player.level();
        BlockPos center = target.blockPosition();
        for (int radius = 1; radius <= 3; radius++) {
            List<BlockPos> offsets = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius && (dx != 0 || dz != 0)) {
                        offsets.add(center.offset(dx, 0, dz));
                    }
                }
            }
            // 随机顺序尝试
            while (!offsets.isEmpty()) {
                int idx = ThreadLocalRandom.current().nextInt(offsets.size());
                BlockPos candidate = offsets.remove(idx);
                BlockPos feet = findSafeStandingPos(level, candidate);
                if (feet == null) continue;
                AABB aabb = new AABB(feet).inflate(0.3);
                if (!level.noCollision(player, aabb)) continue;
                player.teleportTo(feet.getX() + 0.5, feet.getY() + 0.1, feet.getZ() + 0.5);
                return;
            }
        }
    }

    private static BlockPos findSafeStandingPos(Level level, BlockPos pos) {
        for (int y = Math.min(pos.getY() + 2, level.getMaxBuildHeight() - 1); y > level.getMinBuildHeight(); y--) {
            BlockPos feet = new BlockPos(pos.getX(), y, pos.getZ());
            BlockState below = level.getBlockState(feet.below());
            BlockState at = level.getBlockState(feet);
            BlockState above = level.getBlockState(feet.above());
            boolean belowSolid = !below.isAir() || !below.getFluidState().isEmpty();
            boolean feetOk = at.isAir() || !at.getFluidState().isEmpty();
            boolean aboveOk = above.isAir() || !above.getFluidState().isEmpty();
            if (belowSolid && feetOk && aboveOk) {
                return feet;
            }
        }
        return null;
    }

    private static ItemStack findAndConsumeRandomBattleCard(Player player) {
        List<ItemStack> candidates = new ArrayList<>();
        // 物品栏
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && CardRegistry.itemToType(stack) != null) {
                candidates.add(stack);
            }
        }
        // 末影箱
        var enderChest = player.getEnderChestInventory();
        for (int i = 0; i < enderChest.getContainerSize(); i++) {
            ItemStack stack = enderChest.getItem(i);
            if (!stack.isEmpty() && CardRegistry.itemToType(stack) != null) {
                candidates.add(stack);
            }
        }
        // 精妙背包等可打开物品栏的饰品(通过物品容器能力读取)
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isPresent()) {
            var equipped = curios.get().getEquippedCurios();
            for (int i = 0; i < equipped.getSlots(); i++) {
                ItemStack stack = equipped.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                IItemHandler inv = stack.getCapability(Capabilities.ItemHandler.ITEM);
                if (inv == null) continue;
                for (int slot = 0; slot < inv.getSlots(); slot++) {
                    ItemStack inner = inv.getStackInSlot(slot);
                    if (!inner.isEmpty() && CardRegistry.itemToType(inner) != null) {
                        candidates.add(inner);
                    }
                }
            }
        }
        if (candidates.isEmpty()) return null;
        ItemStack chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        chosen.shrink(1);
        return chosen.copy();
    }
}
