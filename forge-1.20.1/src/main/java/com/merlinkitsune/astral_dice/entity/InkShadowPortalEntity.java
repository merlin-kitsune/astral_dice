package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignItem;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignManager;
import com.merlinkitsune.astral_dice.item.chip.WarpEngineChipItem;
import com.merlinkitsune.starengine.geckolib.animatable.GeoEntity;
import com.merlinkitsune.starengine.geckolib.core.animatable.instance.AnimatableInstanceCache;
import com.merlinkitsune.starengine.geckolib.core.animation.AnimatableManager;
import com.merlinkitsune.starengine.geckolib.core.animation.AnimationController;
import com.merlinkitsune.starengine.geckolib.core.animation.RawAnimation;
import com.merlinkitsune.starengine.geckolib.util.GeckoLibUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 小猎手立牌（inkshadow）的【传送门】实体。
 *
 * <h2>状态</h2>
 * <ul>
 *   <li>{@link #STATE_PENDING}（0）：起始门已放下、终点门未放下；不可穿越，渲染半透明；</li>
 *   <li>{@link #STATE_ACTIVE}（1）：两扇门成对；可双向跨维度传送，直到 {@link #expireTick}。</li>
 * </ul>
 * {@code state} 走 {@link SynchedEntityData}（客户端渲染需要）；主人 / 配对 / 到期刻 / 已触发玩家只在服务端。
 *
 * <h2>传送（规格 §3.3）</h2>
 * ACTIVE 门每 5 tick 扫描自身碰撞箱外扩 0.6 格内的玩家：未在本门 {@code visited} 中、且不在
 * {@link InkShadowSignItem#RETRIGGER_TICKS} 重触发冷却内 ⇒ 传送到配对门的坐标，并把本门记入
 * {@code visited}、门主人加 1 层被动、穿越者结算【跃迁核心】。
 *
 * <h2>灼伤</h2>
 * ACTIVE 门每 20 tick 对 {@link Enemy} 在 {@link InkShadowSignItem#PORTAL_HAZARD_RADIUS} 内造成
 * {@link InkShadowSignItem#PORTAL_HAZARD_DAMAGE} 点技能伤害。
 *
 * <h2>存活</h2>
 * 到期（或配对门到期被连带销毁）直接消失，不可被攻击 / 推动 / 破坏。
 */
public class InkShadowPortalEntity extends Entity implements GeoEntity {

    /** 起始门：等待终点门 */
    public static final int STATE_PENDING = 0;
    /** 成对门：可传送 */
    public static final int STATE_ACTIVE = 1;

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(InkShadowPortalEntity.class, EntityDataSerializers.INT);

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_PAIR = "Pair";
    private static final String TAG_STATE = "PortalState";
    private static final String TAG_EXPIRE = "Expire";
    private static final String TAG_VISITED = "Visited";

    private static final String ANIM_IDLE = "animation.inkshadoweffect.idle";

    private UUID ownerUuid;
    private UUID pairUuid;
    private long expireTick;
    private final Set<UUID> visited = new HashSet<>();
    private final Map<UUID, Long> retriggerUntil = new HashMap<>();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public InkShadowPortalEntity(EntityType<? extends InkShadowPortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public InkShadowPortalEntity(Level level, Player owner, int state, long expireTick) {
        this(ModEntityTypes.INKSHADOW_PORTAL.get(), level);
        this.ownerUuid = owner == null ? null : owner.getUUID();
        setState(state);
        this.expireTick = expireTick;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STATE, STATE_PENDING);
    }

    public int getState() {
        return this.entityData.get(DATA_STATE);
    }

    public void setState(int state) {
        this.entityData.set(DATA_STATE, state);
    }

    public boolean isActive() {
        return getState() == STATE_ACTIVE;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public UUID getPairUuid() {
        return pairUuid;
    }

    public void setPairUuid(UUID pairUuid) {
        this.pairUuid = pairUuid;
    }

    public long getExpireTick() {
        return expireTick;
    }

    public void setExpireTick(long expireTick) {
        this.expireTick = expireTick;
    }

    // ===== 物理：不可碰撞 / 不可攻击 / 不可推动 =====

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
    public boolean isAttackable() {
        return false;
    }

    /** 1.20.1 平台差异：本线签名是 {@code skipAttackInteraction(Entity)}（1.21 为 Player）。 */
    @Override
    public boolean skipAttackInteraction(net.minecraft.world.entity.Entity entity) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    // ===== tick =====

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        long now = this.level().getGameTime();
        if (this.expireTick > 0 && now >= this.expireTick) {
            discardPair();
            return;
        }
        if (!isActive()) return;
        if (this.tickCount % 5 == 0) {
            tryTeleport(now);
        }
        if (this.tickCount % 20 == 0) {
            scorchEnemies();
        }
    }

    private void tryTeleport(long now) {
        AABB box = this.getBoundingBox().inflate(0.6D);
        List<Player> players = this.level().getEntitiesOfClass(Player.class, box);
        for (Player player : players) {
            if (!(player instanceof ServerPlayer serverPlayer)) continue;
            if (!serverPlayer.isAlive() || serverPlayer.isSpectator()) continue;
            if (visited.contains(serverPlayer.getUUID())) continue;
            Long until = retriggerUntil.get(serverPlayer.getUUID());
            if (until != null && now < until) continue;
            InkShadowPortalEntity pair = InkShadowSignManager.findPortal(this.level(), this.pairUuid);
            if (pair == null || pair == this || !pair.isActive()) continue;

            // 本门对每名玩家只计一次；重触发冷却同时写在本门与配对门，避免落地即反弹
            visited.add(serverPlayer.getUUID());
            retriggerUntil.put(serverPlayer.getUUID(), now + InkShadowSignItem.RETRIGGER_TICKS);
            pair.retriggerUntil.put(serverPlayer.getUUID(), now + InkShadowSignItem.RETRIGGER_TICKS);

            if (pair.level() instanceof ServerLevel targetLevel) {
                serverPlayer.teleportTo(targetLevel, pair.getX(), pair.getY(), pair.getZ(),
                        serverPlayer.getYRot(), serverPlayer.getXRot());
            } else {
                serverPlayer.teleportTo(pair.getX(), pair.getY(), pair.getZ());
            }
            serverPlayer.resetFallDistance();
            onPlayerPassed(serverPlayer);
        }
    }

    /** 穿越结算：门主人 +1 层被动（离线 / 未佩戴则跳过）、穿越者结算【跃迁核心】。 */
    private void onPlayerPassed(ServerPlayer player) {
        InkShadowPortalEntity source = this;
        if (source.ownerUuid != null && this.level() instanceof ServerLevel serverLevel) {
            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(source.ownerUuid);
            if (owner != null) {
                InkShadowSignManager.addStack(owner);
            }
        }
        WarpEngineChipItem.onPortalOrWaystoneTeleport(player);
    }

    private void scorchEnemies() {
        double radius = InkShadowSignItem.PORTAL_HAZARD_RADIUS;
        AABB box = this.getBoundingBox().inflate(radius);
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity instanceof Enemy && entity.isAlive());
        for (LivingEntity target : targets) {
            target.hurt(ModDamageTypes.skillDamage(this.level(), this), InkShadowSignItem.PORTAL_HAZARD_DAMAGE);
        }
    }

    /** 到期 / 配对门失效：连同另一扇门一起消失（不可破坏、不可攻击）。 */
    public void discardPair() {
        if (this.isRemoved()) return;
        InkShadowPortalEntity pair = InkShadowSignManager.findPortal(this.level(), this.pairUuid);
        this.discard();
        if (pair != null && pair != this && !pair.isRemoved()) {
            pair.discard();
        }
    }

    // ===== GeckoLib =====

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 5,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop(ANIM_IDLE))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ===== 存档 =====

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(TAG_OWNER)) this.ownerUuid = tag.getUUID(TAG_OWNER);
        if (tag.hasUUID(TAG_PAIR)) this.pairUuid = tag.getUUID(TAG_PAIR);
        setState(tag.getInt(TAG_STATE));
        this.expireTick = tag.getLong(TAG_EXPIRE);
        this.visited.clear();
        ListTag visitedTag = tag.getList(TAG_VISITED, 10);
        for (int i = 0; i < visitedTag.size(); i++) {
            CompoundTag entry = visitedTag.getCompound(i);
            if (entry.hasUUID("Id")) this.visited.add(entry.getUUID("Id"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ownerUuid != null) tag.putUUID(TAG_OWNER, this.ownerUuid);
        if (this.pairUuid != null) tag.putUUID(TAG_PAIR, this.pairUuid);
        tag.putInt(TAG_STATE, getState());
        tag.putLong(TAG_EXPIRE, this.expireTick);
        ListTag visitedTag = new ListTag();
        for (UUID id : this.visited) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            visitedTag.add(entry);
        }
        tag.put(TAG_VISITED, visitedTag);
    }
}
