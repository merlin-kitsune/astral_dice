package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.AbyssMarkManager;
import com.merlinkitsune.starengine.geckolib.animatable.GeoEntity;
import com.merlinkitsune.starengine.geckolib.animatable.instance.AnimatableInstanceCache;
import com.merlinkitsune.starengine.geckolib.animatable.manager.AnimatableManager;
import com.merlinkitsune.starengine.geckolib.animation.AnimationController;
import com.merlinkitsune.starengine.geckolib.animation.RawAnimation;
import com.merlinkitsune.starengine.geckolib.animation.object.PlayState;
import com.merlinkitsune.starengine.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 「魔渊触须」（{@code astral_dice:sykestentacle}）：魔渊幼体立牌（sykes）主动技能「魔域转化」放下的召唤物。
 *
 * <p><b>它是什么</b>：{@code extends LivingEntity} 但不带 Mob AI —— 位置、仇恨、攻击、寿命全部由本类自己驱动，
 * 行为规格（用户 2026-11-06 裁决，原 P0 两线已实机验证，本线逐条等价移植；2026-11-11 起本线归 P0）：
 * <ul>
 *   <li><b>魔渊区域</b> = 以触须所在格中心为原点、x/z 各 ±{@link #ZONE_HALF} 格的 <b>5×5 地表</b>（含四角）；</li>
 *   <li>每 {@link #ATTACK_INTERVAL_TICKS} tick 抽打一次区域内的<b>全部</b>敌对目标：
 *       {@link #STRIKE_DAMAGE} 点技能伤害 + 每层「渊蚀印记」+1 + 缓慢 II（{@link #SLOW_TICKS}）+ 玩家近战同款击退；</li>
 *   <li>动画起手 {@link #ATTACK_HIT_DELAY_TICKS} tick 后才结算伤害（「抽到身上才算」）；
 *       多根在场时按 {@link #STRIKE_SLOT_TICKS} 时间片<b>依次</b>播放，不叠在一起；</li>
 *   <li>每 20 tick 吸引 {@link #AGGRO_RADIUS} 格内敌对生物的仇恨；</li>
 *   <li>存活 {@link #LIFETIME_TICKS}（10 分钟）后播「缩入地下」动画并移除，同时移除占格方块；</li>
 *   <li>不受任何伤害 / 治疗 / 状态效果、不被推动 / 击退 / 传送、不显示名字与血条；
 *       敌对生物每次攻击它会受到 1 点技能伤害（反伤）。</li>
 * </ul>
 *
 * <p><b>26.1.2 与 1.21.1 的四处平台差异</b>（本线实装 jar / 源码 jar 取证）：
 * <ul>
 *   <li>存档读写从 {@code CompoundTag} 换成 {@code ValueOutput} / {@code ValueInput}
 *       （{@code input.getLong(name)} 返回 {@code Optional<Long>}；没有 UUID 读写方法 ⇒ 召唤者按字符串存）；</li>
 *   <li>{@code Entity#hurt} 已 final ⇒ 受伤钩子必须覆写 {@code hurtServer(ServerLevel, DamageSource, float)}；</li>
 *   <li>无敌判定签名变为 {@code isInvulnerableTo(ServerLevel, DamageSource)}；</li>
 *   <li>效果常量按 26.x 映射：{@code MobEffects.MOVEMENT_SLOWDOWN} → {@link MobEffects#SLOWNESS}。</li>
 * </ul>
 *
 * <p>动画资源：{@code assets/astral_dice/geckolib/animations/sykestentacle.animation.json}
 * （{@code animation.tentacleidle} / {@code animation.tentacleattack} / {@code animation.tentaclehide}）。
 */
public class SykeTentacleEntity extends LivingEntity implements GeoEntity {

    private static final String CONTROLLER_IDLE = "idle";
    private static final String CONTROLLER_ACTION = "action";
    private static final String CONTROLLER_HIDE = "hide";

    private static final String ANIM_IDLE = "animation.tentacleidle";
    private static final String ANIM_ATTACK = "animation.tentacleattack";
    private static final String ANIM_HIDE = "animation.tentaclehide";

    private static final String TRIGGER_ATTACK = "attack";
    private static final String TRIGGER_HIDE = "hide";

    private static final String TAG_ANCHOR = "AbyssAnchor";
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
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Long> STRIKE_BUSY_UNTIL = new HashMap<>();
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

    /** 供探针 / 立牌判定使用的召唤者 UUID。 */
    public UUID getOwnerId() {
        return ownerId;
    }

    /** 让子类 / 探针能读到「现在是不是消失阶段」。 */
    public boolean isVanishing() {
        return hideStartTick >= 0L;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // idle：过渡 5 tick，基础状态固定续播循环动画。
        controllers.add(new AnimationController<SykeTentacleEntity>(CONTROLLER_IDLE, 5,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop(ANIM_IDLE))));

        // action / hide：基础状态 STOP（不占基础层），只登记可触发动画。
        controllers.add(new AnimationController<SykeTentacleEntity>(CONTROLLER_ACTION,
                state -> PlayState.STOP)
                .triggerableAnim(TRIGGER_ATTACK, RawAnimation.begin().thenPlay(ANIM_ATTACK)));
        controllers.add(new AnimationController<SykeTentacleEntity>(CONTROLLER_HIDE,
                state -> PlayState.STOP)
                .triggerableAnim(TRIGGER_HIDE, RawAnimation.begin().thenPlay(ANIM_HIDE)));
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
        clearFire();

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


    private void startHide(long now) {
        hideStartTick = now;
        triggerAnim(CONTROLLER_HIDE, TRIGGER_HIDE);
        level().playSound(null, blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.HOSTILE, 1.0F, 0.6F);
    }

    /** 吸引 4 格内敌对生物的仇恨（已有更高优先级目标时不抢）。 */
    private void aggroNearbyEnemies() {
        AABB box = new AABB(anchor).inflate(AGGRO_RADIUS);
        for (LivingEntity mob : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!(mob instanceof Mob attacker) || !isEnemyTarget(mob)) continue;
            LivingEntity current = attacker.getTarget();
            if (current == null || !current.isAlive() || current == this) {
                attacker.setTarget(this);
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

    private List<LivingEntity> targetsInZone() {
        AABB box = new AABB(anchor).inflate(ZONE_HALF, 3.0D, ZONE_HALF);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (isEnemyTarget(candidate)) out.add(candidate);
        }
        return out;
    }

    /** 区域内有敌对目标 ⇒ 抢一个抽打时间片并起手（同步只播动画，12 tick 后才结算）。 */
    private void tryStartAttack(long now) {
        if (targetsInZone().isEmpty()) {
            nextAttackTick = now + 20L;
            return;
        }
        Long busyUntil = STRIKE_BUSY_UNTIL.get(level().dimension());
        if (busyUntil != null && now < busyUntil) {
            nextAttackTick = now + 2L;
            return;
        }
        STRIKE_BUSY_UNTIL.put(level().dimension(), now + STRIKE_SLOT_TICKS);
        triggerAnim(CONTROLLER_ACTION, TRIGGER_ATTACK);
        strikeTick = now + ATTACK_HIT_DELAY_TICKS;
        nextAttackTick = now + ATTACK_INTERVAL_TICKS;
    }

    /** 真正的抽打：命中区域内（5×5 地表）全部敌对目标 —— 4 点技能伤害 + 每层印记 +1 + 缓慢 II + 玩家近战同款击退。 */
    private void performStrike() {
        for (LivingEntity victim : targetsInZone()) {
            float damage = STRIKE_DAMAGE + AbyssMarkManager.bonusDamage(victim);
            victim.hurt(ModDamageTypes.skillDamage(level(), this), damage);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 1, false, true));
            double dx = victim.getX() - getX();
            double dz = victim.getZ() - getZ();
            double len = Math.max(Math.sqrt(dx * dx + dz * dz), 1.0E-4D);
            victim.knockback(0.4D, dx / len, dz / len);
        }
    }

    /**
     * 受伤入口（26.1.2：{@code hurt} 已 final，钩子在此）。
     *
     * <p>触须本身不受任何伤害；但敌对生物每次攻击它会受到 1 点技能伤害（反伤）。
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living && living != this && living instanceof Enemy) {
            living.hurt(ModDamageTypes.skillDamage(level, this), 1.0F);
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
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
    public void remove(Entity.RemovalReason reason) {
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
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putLong(TAG_ANCHOR, anchor.asLong());
        output.putLong(TAG_BIRTH, birthTick);
        if (ownerId != null) {
            output.putString(TAG_OWNER, ownerId.toString());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        Optional<Long> anchorRead = input.getLong(TAG_ANCHOR);
        anchorRead.ifPresent(value -> anchor = BlockPos.of(value));
        Optional<Long> birthRead = input.getLong(TAG_BIRTH);
        birthRead.ifPresent(value -> birthTick = value);
        Optional<String> ownerRead = input.getString(TAG_OWNER);
        ownerRead.ifPresent(value -> {
            try {
                ownerId = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                ownerId = null;
            }
        });
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
