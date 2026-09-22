package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.WarpEngineChipItem;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
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
import net.minecraftforge.event.entity.living.LivingUseTotemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 末影骰子:与下界合金骰子同阶。
 *
 * 功能:
 * - 受到致命伤害时,触发一次"不死图腾"效果(恢复 1 生命 + 移除有害效果 + 生命恢复 II(0:45)
 *   + 伤害吸收 II(0:05) + 火焰抗性(0:40) + 图腾动画——手持高亮动画使用末影骰子图标),
 *   随后进入 5:00 冷却;
 * - 不死图腾触发后,会尝试瞬移到 16 格内一个安全地面位置(非空气/水面/船上),并播放末影粒子与传送音效;
 *   玩家在水中时不进行该传送;
 * - 除自身伪不死图腾触发外,主手/副手原版不死图腾触发保命时,若佩戴末影骰子也会执行同款瞬移(不消耗末影骰子冷却);
 * - 装备期间处于雨中/水下时,受到的伤害 +40%(注册为 {@link DiceCombatModifiers} 的受击侧伤害修饰器,
 *   由本类 HIGH 监听器与骰战路径共同消费;见 {@link #onLivingHurt})。
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

    // 装备期间处于雨中或水下:受到的伤害 +40%(受击侧伤害修饰器,注册于 DiceCombatModifiers)。
    // 本监听器是**修饰器的唯一应用点**(HIGH,覆盖环境/怪物/弹射物/非赐福攻击等全部来源),
    // 并把本次实例的倍率登记给骰战路径搬运 —— 骰战在 LivingDamageEvent(NORMAL)以 setAmount(finalDmg)
    // 覆盖式写入最终伤害,会整段替换此处乘出的那份值,故必须由骰战路径把同一倍率搬到它自己的最终值上
    // (见 DiceCombatModifiers.applyVictimDamageModifiers 的 A3 口径);两处不会叠加放大:
    // 修饰器在每个伤害实例内只求值一次,倍率只落在最终落地的那个值上。
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        // amount ≤ 0 也必须调用:注册表借此刷新"本次实例倍率"槽,避免骰战路径读到上一次实例的陈旧倍率
        event.setAmount((float) DiceCombatModifiers.applyVictimDamageModifiers(
                entity, event.getSource(), event.getAmount()));
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
        // 保命清效果(用户裁决,两版本统一为 HARMFUL-only):只移除**有害**效果,保留增益
        // (原 removeAllEffects() 会连增益一起清掉)。1.20.1 无 EffectCures 类;
        // 保持"先 setHealth(1.0F) 再清效果"的既有顺序,统一经 ModEffectRemoval 内部通道移除 ——
        // 它以内部标志放行,故 astral_dice: 命名空间的效果同样被移除
        // (该命名空间守卫只拦外部清除:牛奶/蜂蜜//effect clear 等,不要动那个守卫)。
        player.setHealth(1.0F);
        List<MobEffect> effectsToRemove = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (instance.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                effectsToRemove.add(instance.getEffect());
            }
        }
        // 显式白名单 = HARMFUL ∪ {@code GLOWING}(绝不用 removeAllEffects)。
        // 为什么必须补这一项:标记(MARKED,HARMFUL)被清后,标记随身的高亮(GLOWING = NEUTRAL,
        // 由 MarkManager.apply 与标记**一同施加**、同寿命)不在 HARMFUL 之列 ⇒ 只清 HARMFUL 会留下
        // 残留的发光轮廓,与守卫 ModEffectEvents「发光与标记同寿命」的口径不一致。
        // 只在目标**确实带着本模组标记**时才清:GLOWING 也可能来自原版光灵箭等其它来源,
        // 那种发光不属本模组,不能顺手清掉(见本批报告)。
        if (player.hasEffect(ModEffects.MARKED.get())) {
            effectsToRemove.add(MobEffects.GLOWING);
        }
        for (MobEffect effect : effectsToRemove) {
            ModEffectRemoval.remove(player, effect);
        }
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
            WarpEngineChipItem.onTeleport(player);
        }
        // 开始 5:00 冷却(以世界时间为准)
        ModAttachments.setEnderDieTotemCooldownEnd(player,
                player.level().getGameTime() + TOTEM_COOLDOWN_TICKS);
    }

    // 主手/副手原版不死图腾即将触发保命:佩戴末影骰子时附加同款安全瞬移(不进入末影骰子冷却)
    @SubscribeEvent
    public static void onLivingUseTotem(LivingUseTotemEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (event.isCanceled()) return;
        if (!(entity instanceof Player player)) return;
        if (!hasEnderDie(player)) return;
        Vec3 from = player.position();
        if (tryTeleportToSafeGround(player)) {
            playTeleportEffects((ServerLevel) player.level(), from, player.position());
            WarpEngineChipItem.onTeleport(player);
        }
    }
}
