package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.item.sign.SouriSignManager;
import com.merlinkitsune.starengine.geckolib.animatable.GeoEntity;
import com.merlinkitsune.starengine.geckolib.animatable.instance.AnimatableInstanceCache;
import com.merlinkitsune.starengine.geckolib.animation.AnimatableManager;
import com.merlinkitsune.starengine.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 金鲤立牌的漂浮饮品实体（【葫芦饮】/【仙酿】）。
 *
 * <h2>外观与物理</h2>
 * 无碰撞箱、无重力、不可推动；模型来自 Blockbench（{@code models/winegourd.bbmodel} /
 * {@code winegourdbetter.bbmodel}），旋转与上下浮动由客户端渲染器施加（不写动画文件）。
 *
 * <h2>拾取</h2>
 * 服务端每 5 tick 扫描自身碰撞箱外扩 0.9 格内的玩家：拾取方身上已有葫芦饮或仙酿时
 * **拒绝拾取**（用户需求：一次只能拾取一个）；拾取结算见
 * {@link SouriSignManager#onDrinkPicked}（效果 + 立牌持有者反击层 + 蛟龙联动 + 白色粒子）。
 *
 * <h2>生命周期</h2>
 * 放置后 {@link #LIFETIME_TICKS}（5:00）自动消失；【金鲤化龙】会把本人名下未拾取的葫芦饮
 * 就地转为仙酿（{@link #setVariant}）。
 */
public class SouriDrinkEntity extends Entity implements GeoEntity {

    /** 两种饮品 */
    public enum Variant {
        GOURD,
        BREW;

        public static Variant byId(int id) {
            return id == 1 ? BREW : GOURD;
        }
    }

    /** 放置后存活时长 5:00 */
    public static final int LIFETIME_TICKS = 6000;

    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(SouriDrinkEntity.class, EntityDataSerializers.INT);

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_VARIANT = "Variant";
    private static final String TAG_LIFE = "Life";

    private UUID owner;
    private int lifeTicks = LIFETIME_TICKS;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SouriDrinkEntity(EntityType<? extends SouriDrinkEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public SouriDrinkEntity(Level level, UUID owner, Variant variant) {
        this(ModEntityTypes.SOURI_DRINK.get(), level);
        this.owner = owner;
        setVariant(variant);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VARIANT, Variant.GOURD.ordinal());
    }

    public Variant getVariant() {
        return Variant.byId(this.entityData.get(DATA_VARIANT));
    }

    public void setVariant(Variant variant) {
        this.entityData.set(DATA_VARIANT, variant.ordinal());
    }

    public UUID getOwnerUUID() {
        return owner;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (--lifeTicks <= 0) {
            discard();
            return;
        }
        if (this.tickCount % 5 == 0) {
            tryPickup();
        }
    }

    private void tryPickup() {
        for (Player player : this.level().getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(0.9D))) {
            if (!player.isAlive() || player.isSpectator()) continue;
            if (SouriSignManager.isDrinkBlocked(player)) continue;
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        this.getX(), this.getY() + 0.4D, this.getZ(),
                        12, 0.25D, 0.25D, 0.25D, 0.02D);
            }
            SouriSignManager.onDrinkPicked(player, this);
            discard();
            return;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 纯展示实体：旋转与上下浮动由渲染器施加，不播放任何动画
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(TAG_OWNER)) {
            this.owner = tag.getUUID(TAG_OWNER);
        }
        setVariant(Variant.byId(tag.getInt(TAG_VARIANT)));
        this.lifeTicks = tag.contains(TAG_LIFE) ? tag.getInt(TAG_LIFE) : LIFETIME_TICKS;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.owner != null) {
            tag.putUUID(TAG_OWNER, this.owner);
        }
        tag.putInt(TAG_VARIANT, getVariant().ordinal());
        tag.putInt(TAG_LIFE, this.lifeTicks);
    }
}
