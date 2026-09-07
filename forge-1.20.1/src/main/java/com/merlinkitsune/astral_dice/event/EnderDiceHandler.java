package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 末影骰子:与下界合金骰子同阶。
 *
 * 功能:
 * - 受到致命伤害时,触发一次"不死图腾"效果(恢复 1 生命 + 移除全部效果 + 生命恢复 II(0:45)
 *   + 伤害吸收 II(0:05) + 火焰抗性(0:40) + 图腾动画——手持高亮动画使用末影骰子图标),
 *   随后进入 5:00 冷却;
 * - 不死图腾触发后,会尝试瞬移到 16 格内一个安全地面位置(非空气/水面/船上),并播放末影粒子与传送音效;
 *   玩家在水中时不进行该传送;
 * - 装备期间处于雨中/水下时,受到的伤害 +40%。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class EnderDiceHandler {

    /** 不死图腾效果冷却:5:00 = 6000 tick */
    public static final int TOTEM_COOLDOWN_TICKS = 20 * 60 * 5;
    /** 雨中/水下受到伤害倍率(+40%) */
    public static final float RAIN_WATER_DAMAGE_MULTIPLIER = 1.4F;
    /** 不死图腾触发后的瞬移搜索半径 */
    public static final int TELEPORT_RANGE = 16;
    /** 安全落点搜索尝试次数 */
    private static final int TELEPORT_ATTEMPTS = 64;

    // === 不死图腾效果参数(与原版一致) ===
    private static final int REGEN_DURATION_TICKS = 900;   // 生命恢复 II,0:45
    private static final int REGEN_AMPLIFIER = 1;
    private static final int ABSORPTION_DURATION_TICKS = 100; // 伤害吸收 II,0:05
    private static final int ABSORPTION_AMPLIFIER = 1;
    private static final int FIRE_RESIST_DURATION_TICKS = 800; // 火焰抗性,0:40
    private static final int FIRE_RESIST_AMPLIFIER = 0;

    private EnderDiceHandler() {
    }

    public static boolean hasEnderDie(Player player) {
        return CuriosCompat.getCuriosInventory(player)
                .map(h -> h.findFirstCurio(s -> s.is(ModItems.ENDER_DICE.get())).isPresent())
                .orElse(false);
    }

    /** 不死图腾冷却是否仍在进行(剩余 > 0 时为 true) */
    public static boolean isTotemOnCooldown(Player player) {
        return player.level().getGameTime() < ModAttachments.getEnderDieTotemCooldownEnd(player);
    }

    /**
     * 在不死图腾触发后尝试瞬移到 16 格内安全地面。
     * 安全条件:脚下为可站立方块(非空气/水面),落点无方块碰撞、无液体、且不在船上;玩家在水中不触发。
     */
    private static boolean tryTeleportToSafeGround(Player player) {
        if (player.level().isClientSide()) return false;
        // 玩家在水中时无法触发传送(不死图腾仍正常生效)
        if (player.isInWater()) return false;

        ServerLevel level = (ServerLevel) player.level();
        BlockPos origin = player.blockPosition();
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
            double distance = player.getRandom().nextDouble() * TELEPORT_RANGE;
            int dx = (int) Math.round(Math.cos(angle) * distance);
            int dz = (int) Math.round(Math.sin(angle) * distance);
            int x = origin.getX() + dx;
            int z = origin.getZ() + dz;
            if (!level.hasChunkAt(new BlockPos(x, origin.getY(), z))) continue;

            // 从上方到下方搜索 16 格垂直范围,优先选择更高的安全落点
            for (int dy = TELEPORT_RANGE; dy >= -TELEPORT_RANGE; dy--) {
                int y = origin.getY() + dy;
                BlockPos targetPos = new BlockPos(x, y, z);
                BlockState ground = level.getBlockState(targetPos.below());
                if (!ground.blocksMotion() || !ground.getFluidState().isEmpty()) continue;

                BlockState feetBlock = level.getBlockState(targetPos);
                if (feetBlock.blocksMotion() || !feetBlock.getFluidState().isEmpty()) continue;

                AABB targetBox = player.getDimensions(Pose.STANDING)
                        .makeBoundingBox(new Vec3(x + 0.5, y, z + 0.5));
                if (level.containsAnyLiquid(targetBox)) continue;
                if (!level.noCollision(player, targetBox)) continue;
                // 避免传送到船等实体占用的位置
                if (!level.getEntitiesOfClass(Boat.class, targetBox.inflate(0.25D), e -> e.isAlive()).isEmpty()) continue;

                player.teleportTo(x + 0.5, y, z + 0.5);
                player.resetFallDistance();
                return true;
            }
        }
        return false;
    }

    /** 播放末影传送粒子与音效(在原位置与落点均播放) */
    private static void playTeleportEffects(ServerLevel level, Vec3 from, Vec3 to) {
        level.sendParticles(ParticleTypes.PORTAL,
                from.x, from.y + 1.0D, from.z, 40, 0.5D, 0.5D, 0.5D, 0.05D);
        level.sendParticles(ParticleTypes.PORTAL,
                to.x, to.y + 1.0D, to.z, 40, 0.5D, 0.5D, 0.5D, 0.05D);
        level.playSound(null, from.x, from.y, from.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, to.x, to.y, to.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // 装备期间处于雨中或水下:受到的伤害 +40%
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (!hasEnderDie(player)) return;
        // 雨(含被雨淋到)/水下/气泡柱都满足"雨中或水下"
        if (!player.isInWaterRainOrBubble()) return;
        event.setAmount(event.getAmount() * RAIN_WATER_DAMAGE_MULTIPLIER);
    }

    // 受到致命伤害:未处于冷却时触发一次不死图腾效果并进入 5:00 冷却
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (event.isCanceled()) return;
        // 与原版不死图腾一致:无视无敌(bypasses_invulnerability)的致死伤害不触发
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (!hasEnderDie(player)) return;
        if (isTotemOnCooldown(player)) return;

        event.setCanceled(true);
        // 与原版图腾一致:清除全部效果(死亡后不掉落/不触发死亡逻辑,由取消死亡保证)
        player.setHealth(1.0F);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                REGEN_DURATION_TICKS, REGEN_AMPLIFIER, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
                ABSORPTION_DURATION_TICKS, ABSORPTION_AMPLIFIER, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                FIRE_RESIST_DURATION_TICKS, FIRE_RESIST_AMPLIFIER, false, true));
        // 客户端播放不死图腾动画:粒子 + 音效 + 手持高亮(图标为末影骰子)
        ModNetwork.EnderDieTotemMessage.send(player);
        // 不死图腾触发后尝试瞬移到 16 格内安全地面(玩家在水中时跳过)
        Vec3 from = player.position();
        if (tryTeleportToSafeGround(player)) {
            playTeleportEffects((ServerLevel) player.level(), from, player.position());
        }
        // 开始 5:00 冷却(以世界时间为准)
        ModAttachments.setEnderDieTotemCooldownEnd(player,
                player.level().getGameTime() + TOTEM_COOLDOWN_TICKS);
    }
}
