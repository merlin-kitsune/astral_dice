package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.AbyssMarkManager;
import com.merlinkitsune.astral_dice.platform.util.Effects;
import com.merlinkitsune.starengine.geckolib.animatable.GeoEntity;
import com.merlinkitsune.starengine.geckolib.animatable.instance.AnimatableInstanceCache;
import com.merlinkitsune.starengine.geckolib.animation.AnimatableManager;
import com.merlinkitsune.starengine.geckolib.animation.AnimationController;
import com.merlinkitsune.starengine.geckolib.animation.PlayState;
import com.merlinkitsune.starengine.geckolib.animation.RawAnimation;
import com.merlinkitsune.starengine.geckolib.util.GeckoLibUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「魔渊触须」（原 Syke 触手，2026-11-06 由立牌「魔渊幼体」的主动技能生成）—— fabric-1.21.1 移植版。
 *
 * <p>行为（用户裁决，与 neoforge-1.21.1 逐条一致）：
 * <ul>
 *   <li>存活 10 分钟；到期播「缩入地下」动画后消失，并移除自身的占格方块；</li>
 *   <li>每 6 秒对进入「魔渊区域」（触须居中的 5×5 格地表，含四角）内的敌对生物发动一次抽打：
 *       4 点技能伤害 + 印记层数 + 缓慢 II (0:30)，并附带玩家近战同款击退；命中时机 = 动画
 *       {@value #ATTACK_HIT_DELAY_TICKS} tick 之后（真的抽到身上才结算）；</li>
 *   <li>吸引 {@value #AGGRO_RADIUS} 格内敌对生物仇恨；被攻击（虽然免疫）⇒ 攻击者受到 1 点技能伤害；</li>
 *   <li>完全免疫伤害 / 治疗 / 状态效果，不会被推动、击退或传送；</li>
 *   <li>场上有多个触须时按「出生先后」依次播放攻击动画（共享 {@link #STRIKE_BUSY_UNTIL} 时间片）；</li>
 *   <li>不显示名牌、不显示血量（{@link #shouldShowName()} 恒 false）。</li>
 * </ul>
 *
 * <p>动画资源沿用 {@code geckolib/…/sykestentacle.*}，新增 {@code animation.tentaclehide} 作消失动画
 * （对 {@code root} 骨做 Y 位移关键帧，整体沉入地下）。
 *
 * <h3>与 P0（neoforge-1.21.1）的平台差异</h3>
 * <ol>
 *   <li><b>效果实例 Holder 化</b>：1.21.1 的 {@code MobEffectInstance} 收 {@code Holder<MobEffect>}
 *       ⇒ 缓慢效果统一经 {@link Effects#holder} 构造（P0 该线是直接传裸 {@code MobEffects.X}）。</li>
 *   <li><b>状态效果免疫</b>：P0 用 {@code MobEffectEvent.Applicable} 一句 {@code DO_NOT_APPLY} 挡住
 *       「触须吃任何状态效果」；本线于 2026-11-07 已接线同一事件
 *       （{@code event/NewSignEvents#onEffectApplicable} 对触须全拒，{@code platform/StarengineBridges} 桥
 *       {@code MobEffectEvents.AFFECTS} 到 {@code LivingEntity#canBeAffected} HEAD）。
 *       原「每 tick {@link #removeAllEffects()} 兜底」<b>保留</b>（判定路径不变：直接
 *       {@code addEffect} / 已有效果的路径不走 {@code canBeAffected}）。</li>
 *   <li><b>技能伤害源</b>：与 P0 同一个本线实现 {@link ModDamageTypes#skillDamage(Level, Entity)}
 *       （{@code astral_dice:skill_damage}），不使用 {@code hurt(source, 2.0F)} 之类直伤。</li>
 *   <li><b>几何/常量</b>：{@code ZONE_HALF=2.5 / ATTACK_INTERVAL_TICKS=120 /
 *       ATTACK_HIT_DELAY_TICKS=12 / STRIKE_DAMAGE=4.0 / SLOW_TICKS=600 / LIFETIME_TICKS=12000 /
 *       HIDE_TICKS=24 / AGGRO_RADIUS=4.0 / STRIKE_SLOT_TICKS=24} 全部逐字不变（用户裁决）。</li>
 * </ol>
 */
public class SykeTentacleEntity extends LivingEntity implements GeoEntity {

    private static final String CONTROLLER_IDLE = "idle";
    private static final String CONTROLLER_ACTION = "action";
    private static final String CONTROLLER_HIDE = "hide";

    private static final String ANIM_IDLE = "animation.tentacleidle";
    private static final String ANIM_ATTACK = "animation.tentacleattack";
    private static final String ANIM_HIDE = "animation.tentaclehide";

    private static final String TAG_ANCHOR_X = "AbyssAnchorX";
    private static final String TAG_ANCHOR_Y = "AbyssAnchorY";
    private static final String TAG_ANCHOR_Z = "AbyssAnchorZ";
    private static final String TAG_BIRTH = "AbyssBirthTick";
    private static final String TAG_OWNER = "AbyssOwner";

    /** 攻击间隔：6 秒。 */
    public static final int ATTACK_INTERVAL_TICKS = 120;
    /** 动画起手到真正抽到的延迟（tick）：与 {@code animation.tentacleattack} 的挥击帧对齐。 */
    public static final int ATTACK_HIT_DELAY_TICKS = 12;
    /** 消失动画时长（tick）：播完才移除实体与占格方块。 */
    public static final int HIDE_TICKS = 24;
    /** 存活时长：10 分钟。 */
    public static final int LIFETIME_TICKS = 12000;
    /**
     * 「魔渊区域」半边长（格）：以触须所在格中心为原点，x/z 各 ±2.5 格 ⇒ 恰好覆盖完整的 5×5 地表
     * （含四角；用户 2026-11-06 裁决：区域就是 5×5，触手居中、能打到其它每一格）。
     */
    public static final double ZONE_HALF = 2.5D;
    /** 吸引仇恨的半径（格）。 */
    public static final double AGGRO_RADIUS = 4.0D;
    /** 单次抽打的基础技能伤害（点）。 */
    public static final float STRIKE_DAMAGE = 4.0F;
    /** 缓慢 II 时长：0:30。 */
    public static final int SLOW_TICKS = 600;

    /** 依次播攻击动画：同一维度同一时刻只允许一根触须处于抽打时间片里。 */
    private static final Map<ResourceKey<Level>, Long> STRIKE_BUSY_UNTIL = new HashMap<>();
    private static final int STRIKE_SLOT_TICKS = 24;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private BlockPos anchor = BlockPos.ZERO;
    private long birthTick = 0L;
    private long nextAttackTick = 0L;
    private long strikeTick = -1L;
    private long hideStartTick = -1L;
    private UUID ownerId;

    public SykeTentacleEntity(EntityType<? extends SykeTentacleEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setInvulnerable(true);
    }

    /** 由立牌主动技能调用：绑定锚点（占格坐标）与召唤者。 */
    public void bindTo(BlockPos anchorPos, UUID owner) {
        this.anchor = anchorPos.immutable();
        this.ownerId = owner;
        this.birthTick = level().getGameTime();
        this.nextAttackTick = this.birthTick + ATTACK_INTERVAL_TICKS;
        setPos(anchorPos.getX() + 0.5D, anchorPos.getY(), anchorPos.getZ() + 0.5D);
    }

    public BlockPos getAnchor() {
        return anchor;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER_IDLE, 5,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop(ANIM_IDLE))));
        controllers.add(new AnimationController<>(this, CONTROLLER_ACTION, state -> PlayState.STOP)
                .triggerableAnim("attack", RawAnimation.begin().thenPlay(ANIM_ATTACK)));
        controllers.add(new AnimationController<>(this, CONTROLLER_HIDE, state -> PlayState.STOP)
                .triggerableAnim("hide", RawAnimation.begin().thenPlay(ANIM_HIDE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        if (birthTick <= 0L) {
            birthTick = level().getGameTime();
            nextAttackTick = birthTick + ATTACK_INTERVAL_TICKS;
        }
        lockPosition();
        removeAllEffects();
        astralClearFire();

        long now = level().getGameTime();

        if (hideStartTick < 0L && now - birthTick >= LIFETIME_TICKS) {
            startHide(now);
        }
        if (hideStartTick >= 0L) {
            if (now - hideStartTick >= HIDE_TICKS) {
                removeCollisionBlock();
                discard();
            }
            return;
        }

        if (now % 20L == 0L) aggroNearbyEnemies();
        if (now % 10L == 0L) emitZoneParticles();

        if (strikeTick >= 0L && now >= strikeTick) {
            performStrike();
            strikeTick = -1L;
        }
        if (now >= nextAttackTick) {
            tryStartAttack(now);
        }
    }

    /** 钉死位置 / 朝向：不受重力、爆炸、活塞、实体挤压与传送影响。 */
    private void lockPosition() {
        setDeltaMovement(0.0D, 0.0D, 0.0D);
        double px = anchor.getX() + 0.5D;
        double py = anchor.getY();
        double pz = anchor.getZ() + 0.5D;
        if (Math.abs(getX() - px) > 1.0E-4D || Math.abs(getY() - py) > 1.0E-4D || Math.abs(getZ() - pz) > 1.0E-4D) {
            setPos(px, py, pz);
        }
        setXRot(0.0F);
        fallDistance = 0.0F;
    }

    /** 灭火（父类 clearFire 是 public，改名避免降低可见性）。 */
    private void astralClearFire() {
        if (getRemainingFireTicks() > 0) setRemainingFireTicks(0);
    }

    private void startHide(long now) {
        hideStartTick = now;
        triggerAnim(CONTROLLER_HIDE, "hide");
        level().playSound(null, blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    /** 吸引 4 格内敌对生物的仇恨（已有更高优先级目标时不抢）。 */
    private void aggroNearbyEnemies() {
        AABB box = new AABB(anchor).inflate(AGGRO_RADIUS);
        for (LivingEntity mob : level().getEntitiesOfClass(LivingEntity.class, box, this::isEnemyTarget)) {
            if (mob instanceof Mob attacker) {
                LivingEntity current = attacker.getTarget();
                if (current == null || !current.isAlive() || current == this) {
                    attacker.setTarget(this);
                }
            }
        }
    }

    /** 「魔渊区域」的地面暗紫粒子（纯视觉，覆盖 5×5 格）。 */
    private void emitZoneParticles() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        for (int i = 0; i < 4; i++) {
            double x = anchor.getX() + 0.5D + (random.nextDouble() * 2.0D - 1.0D) * ZONE_HALF;
            double z = anchor.getZ() + 0.5D + (random.nextDouble() * 2.0D - 1.0D) * ZONE_HALF;
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, anchor.getY() + 0.15D, z,
                    1, 0.0D, 0.02D, 0.0D, 0.0D);
        }
    }

    private boolean isEnemyTarget(LivingEntity candidate) {
        if (candidate == this || !candidate.isAlive() || candidate.isSpectator() || !(candidate instanceof Enemy)) {
            return false;
        }
        // 5×5 地表判定：以触须所在格中心为原点，x/z 各 ±2.5 格（四角也算在区域内），纵向 -1..+3 格
        double dx = Math.abs(candidate.getX() - (anchor.getX() + 0.5D));
        double dz = Math.abs(candidate.getZ() - (anchor.getZ() + 0.5D));
        double dy = candidate.getY() - anchor.getY();
        return dx <= ZONE_HALF && dz <= ZONE_HALF && dy >= -1.0D && dy <= 3.0D;
    }

    /** 区域内有敌对目标 ⇒ 抢一个抽打时间片并起手（同步只播动画，12 tick 后才结算）。 */
    private void tryStartAttack(long now) {
        AABB box = new AABB(anchor).inflate(ZONE_HALF, 3.0D, ZONE_HALF);
        boolean hasTarget = !level().getEntitiesOfClass(LivingEntity.class, box, this::isEnemyTarget).isEmpty();
        if (!hasTarget) {
            nextAttackTick = now + 20L;
            return;
        }
        Long busyUntil = STRIKE_BUSY_UNTIL.get(level().dimension());
        if (busyUntil != null && now < busyUntil) {
            nextAttackTick = now + 2L;
            return;
        }
        STRIKE_BUSY_UNTIL.put(level().dimension(), now + STRIKE_SLOT_TICKS);
        triggerAnim(CONTROLLER_ACTION, "attack");
        strikeTick = now + ATTACK_HIT_DELAY_TICKS;
        nextAttackTick = now + ATTACK_INTERVAL_TICKS;
    }

    /** 真正的抽打：命中区域内（5×5 地表）全部敌对目标 —— 4 点技能伤害 + 每层印记 +1 + 缓慢 II + 玩家近战同款击退。 */
    private void performStrike() {
        AABB box = new AABB(anchor).inflate(ZONE_HALF, 3.0D, ZONE_HALF);
        List<LivingEntity> victims = level().getEntitiesOfClass(LivingEntity.class, box, this::isEnemyTarget);
        for (LivingEntity victim : victims) {
            float damage = STRIKE_DAMAGE + AbyssMarkManager.bonusDamage(victim);
            victim.hurt(ModDamageTypes.skillDamage(level(), this), damage);
            victim.addEffect(new MobEffectInstance(Effects.holder(MobEffects.MOVEMENT_SLOWDOWN),
                    SLOW_TICKS, 1, false, true));
            double dx = victim.getX() - getX();
            double dz = victim.getZ() - getZ();
            double len = Math.max(Math.sqrt(dx * dx + dz * dz), 1.0E-4D);
            victim.knockback(0.4D, dx / len, dz / len);
        }
    }

    /**
     * 免疫伤害，并对「攻击者是敌对生物」反打 1 点技能伤害。
     *
     * <p>注意：本线 {@code LivingHurtEvent} 由 {@code mixin/bridge/LivingHurtBridgeMixin} 挂在
     * {@code LivingEntity#hurt} 内部，而本类覆写 {@code hurt} 且<b>不调 super</b> ⇒ 触须自身不会
     * 派发任何受伤事件（这正是「完全免疫」想要的效果），反伤由这里直接结算。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide()) {
            Entity attacker = source.getEntity();
            if (attacker instanceof Enemy && attacker instanceof LivingEntity living
                    && living != this && living.isAlive()) {
                living.hurt(ModDamageTypes.skillDamage(level(), this), 1.0F);
            }
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public void heal(float amount) {
        // 免疫治疗：治疗量一律忽略（血量恒定）
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
        // 免疫推动
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // 免疫击退
    }

    @Override
    public void teleportTo(double x, double y, double z) {
        // 免疫传送
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean isCustomNameVisible() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            removeCollisionBlock();
        }
        super.remove(reason);
    }

    private void removeCollisionBlock() {
        if (level().isClientSide()) return;
        if (level().getBlockState(anchor).is(ModBlocks.ABYSS_TENTACLE_COLLISION.get())) {
            level().removeBlock(anchor, false);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_ANCHOR_X, anchor.getX());
        tag.putInt(TAG_ANCHOR_Y, anchor.getY());
        tag.putInt(TAG_ANCHOR_Z, anchor.getZ());
        tag.putLong(TAG_BIRTH, birthTick);
        if (ownerId != null) {
            tag.putUUID(TAG_OWNER, ownerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_ANCHOR_X)) {
            anchor = new BlockPos(tag.getInt(TAG_ANCHOR_X), tag.getInt(TAG_ANCHOR_Y), tag.getInt(TAG_ANCHOR_Z));
        }
        birthTick = tag.getLong(TAG_BIRTH);
        if (tag.hasUUID(TAG_OWNER)) {
            ownerId = tag.getUUID(TAG_OWNER);
        }
    }

    /** 供探针 / 立牌判定使用的召唤者 UUID。 */
    public UUID getOwnerId() {
        return ownerId;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        // 没有装备栏
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    /** 预留：让子类/探针能读到「现在是不是消失阶段」。 */
    public boolean isVanishing() {
        return hideStartTick >= 0L;
    }
}
