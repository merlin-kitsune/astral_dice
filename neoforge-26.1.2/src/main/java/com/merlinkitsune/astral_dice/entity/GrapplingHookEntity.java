package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.sign.Z3000SignItem;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * 抓钩拖拽实体（2026-11-13 Z3000 立牌主动「过来把你」）。
 *
 * <p>从玩家眼睛射出，追上活体目标后贴住目标碰撞箱中心，再把目标沿水平直线拖到玩家视线
 * 前方 1.5 格；到达后结算撞击伤害。没有方块钩取、没有玩家牵引。
 *
 * <p>实体自身的位置在飞行阶段是钩头位置；拖拽阶段每 tick 跟随目标，因此客户端只要拿玩家
 * 和本实体两个点就能画出整条管道。
 */
public class GrapplingHookEntity extends Projectile {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrapplingHookEntity.class);

    public static final double RANGE = 16.0D;
    public static final double SHOOT_SPEED = 0.75D;
    public static final double PULL_STEP = 0.175D;
    public static final double FRONT_OFFSET = 1.5D;
    public static final double STOP_DISTANCE = 1.25D;
    public static final int MAX_LIFE_TICKS = 240;

    private static final double ATTACH_DISTANCE = 0.6D;

    private static final EntityDataAccessor<Integer> DATA_TARGET_ID =
            SynchedEntityData.defineId(GrapplingHookEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
            SynchedEntityData.defineId(GrapplingHookEntity.class, EntityDataSerializers.INT);

    private int age;
    private boolean attached;
    private float impactDamage;
    private boolean grantCounterOnKill;

    public GrapplingHookEntity(EntityType<? extends GrapplingHookEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public GrapplingHookEntity(Level level, Player owner, LivingEntity target) {
        this(level, owner, target, 0.0F, false);
    }

    public GrapplingHookEntity(Level level, Player owner, LivingEntity target, float impactDamage, boolean grantCounterOnKill) {
        this(ModEntityTypes.GRAPPLING_HOOK.get(), level);
        this.impactDamage = impactDamage;
        this.grantCounterOnKill = grantCounterOnKill;
        this.setOwner(owner);
        this.entityData.set(DATA_OWNER_ID, owner.getId());
        this.entityData.set(DATA_TARGET_ID, target.getId());
        this.setPos(owner.getX(), owner.getEyeY() - 0.1D, owner.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TARGET_ID, -1);
        builder.define(DATA_OWNER_ID, -1);
    }

    /** 客户端渲染用；服务端仍可直接用 {@link #getOwner()}。 */
    public Entity getRenderOwner() {
        Entity synced = this.level().getEntity(this.entityData.get(DATA_OWNER_ID));
        return synced != null ? synced : this.getOwner();
    }

    public int getTargetId() {
        return this.entityData.get(DATA_TARGET_ID);
    }

    public LivingEntity getHookedTarget() {
        Entity entity = this.level().getEntity(this.getTargetId());
        return entity instanceof LivingEntity living ? living : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }

        Player owner = this.getOwner() instanceof Player player ? player : null;
        if (owner == null || owner.isRemoved() || !owner.isAlive()) {
            this.discard();
            return;
        }
        if (++this.age > MAX_LIFE_TICKS) {
            this.end(owner, "timeout");
            return;
        }

        LivingEntity target = this.getHookedTarget();
        if (target == null || target.isRemoved() || !target.isAlive() || target == owner) {
            this.end(owner, "target_lost");
            return;
        }

        if (this.attached) {
            this.pull(owner, target);
        } else {
            this.fly(owner, target);
        }
    }

    private void fly(Player owner, LivingEntity target) {
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        Vec3 delta = targetCenter.subtract(this.position());
        double distance = delta.length();
        if (distance <= ATTACH_DISTANCE) {
            this.attach(owner, target, targetCenter);
            return;
        }
        if (this.distanceToSqr(owner) > RANGE * RANGE) {
            this.end(owner, "out_of_range");
            return;
        }
        Vec3 step = delta.normalize().scale(Math.min(SHOOT_SPEED, distance));
        this.setPos(this.getX() + step.x, this.getY() + step.y, this.getZ() + step.z);
        this.setDeltaMovement(Vec3.ZERO);
    }

    private void attach(Player owner, LivingEntity target, Vec3 targetCenter) {
        this.setPos(targetCenter.x, targetCenter.y, targetCenter.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.attached = true;
        LOGGER.info("AP_GRAPPLE_ATTACH owner={} target={} dist={}",
                owner.getName().getString(), target.getName().getString(), fmt(target.distanceTo(owner)));
    }

    private void pull(Player owner, LivingEntity target) {
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        this.setPos(targetCenter.x, targetCenter.y, targetCenter.z);
        this.setDeltaMovement(Vec3.ZERO);

        Vec3 front = frontPoint(owner);
        Vec3 delta = front.subtract(target.position());
        double distance = delta.length();
        if (distance <= STOP_DISTANCE) {
            this.placeTarget(target, front.x, front.z);
            this.hitTarget(owner, target);
            this.end(owner, "arrived");
            return;
        }

        Vec3 step = delta.normalize().scale(Math.min(PULL_STEP, distance));
        this.placeTarget(target, target.getX() + step.x, target.getZ() + step.z);
        if (this.age % 20 == 0) {
            LOGGER.info("AP_GRAPPLE_PULL owner={} target={} dist={}",
                    owner.getName().getString(), target.getName().getString(), fmt(target.distanceTo(owner)));
        }
    }

    /** 到达后结算主动技能的撞击伤害；只有本次伤害直接击杀且由立牌发起时才加反击层。 */
    private void hitTarget(Player owner, LivingEntity target) {
        if (this.impactDamage <= 0.0F) return;
        boolean deadBefore = target.isDeadOrDying();
        target.hurt(ModDamageTypes.skillDamage(this.level(), owner), this.impactDamage);
        LOGGER.info("AP_GRAPPLE_DAMAGE owner={} target={} damage={} dead={}",
                owner.getName().getString(), target.getName().getString(), fmt(this.impactDamage),
                target.isDeadOrDying());
        if (this.grantCounterOnKill && !deadBefore && target.isDeadOrDying()
                && owner instanceof ServerPlayer serverPlayer) {
            Z3000SignItem.grantDraggedKillCounter(serverPlayer);
        }
    }

    /** 把目标放到指定水平位置；目标在地面时按目标列抬高到地面，避免拖进方块。 */
    private void placeTarget(LivingEntity target, double x, double z) {
        double y = target.getY();
        if (target.onGround()) {
            int ground = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(x), Mth.floor(z));
            y = Math.max(y, ground);
        }
        target.teleportTo(x, y, z);
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
    }

    private static Vec3 frontPoint(Player owner) {
        Vec3 look = owner.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        return owner.position().add(flat.normalize().scale(FRONT_OFFSET));
    }

    private void end(Player owner, String reason) {
        LOGGER.info("AP_GRAPPLE_END owner={} reason={} age={}",
                owner.getName().getString(), reason, this.age);
        this.discard();
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
