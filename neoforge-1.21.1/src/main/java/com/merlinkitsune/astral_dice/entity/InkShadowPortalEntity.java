package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.chip.WarpEngineChipItem;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignItem;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignManager;
import com.merlinkitsune.starengine.geckolib.animatable.GeoEntity;
import com.merlinkitsune.starengine.geckolib.animatable.instance.AnimatableInstanceCache;
import com.merlinkitsune.starengine.geckolib.animation.AnimatableManager;
import com.merlinkitsune.starengine.geckolib.animation.AnimationController;
import com.merlinkitsune.starengine.geckolib.animation.RawAnimation;
import com.merlinkitsune.starengine.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 小猎手立牌「暗影之门」的单扇传送门。
 *
 * <h2>状态</h2>
 * {@code PENDING}：起始门已放下、终点门还没放（不可穿越；渲染器画成缩小半透明）；
 * {@code ACTIVE}：两门互指后的可用门（存活到 {@code expireTick}，到期两门一起消失）。
 *
 * <h2>穿门结算</h2>
 * 服务端每 5t 扫描自身碰撞箱外扩 0.6 格内的玩家：按「本扇门 + 该玩家」去重（{@code visitedUuids}，
 * 逐门逐人各计一次），并受 {@code retrigger} 与实体原版传送冷却双重防抖（否则会被对面门立刻弹回）。
 * 穿越 = 传送到配对门的坐标（可跨维度；对方区块未必加载，故存坐标而非实体引用）+ 门主人 +1 层被动 +
 * 【跃迁核心】的过门奖励（其自身冷却在芯片内部判定）。
 *
 * <h2>危险区</h2>
 * 每 20t 对 {@link Monster} 造成 {@link InkShadowSignItem#PORTAL_HAZARD_DAMAGE} 点技能伤害。
 */
public class InkShadowPortalEntity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(InkShadowPortalEntity.class, EntityDataSerializers.INT);

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_STATE = "State";
    private static final String TAG_PAIR = "Pair";
    private static final String TAG_PAIR_DIM = "PairDim";
    private static final String TAG_PAIR_X = "PairX";
    private static final String TAG_PAIR_Y = "PairY";
    private static final String TAG_PAIR_Z = "PairZ";
    private static final String TAG_EXPIRE = "Expire";
    private static final String TAG_VISITED = "Visited";

    private static final String CONTROLLER_IDLE = "idle";
    private static final String ANIM_IDLE = "animation.inkshadoweffect.idle";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /** 门主人（被动层数记在他身上） */
    private UUID ownerUuid;
    /** 配对门 uuid（无配对方时为 null） */
    private UUID pairUuid;
    /** 配对门维度（跨维度传送必须存维度字符串） */
    private String pairDim = "";
    /** 配对门坐标：对方区块未加载时仍能落地 */
    private double pairX;
    private double pairY;
    private double pairZ;
    /** 到期刻（0 = 未设置） */
    private long expireTick;
    /** 本扇门已计过被动层数的玩家（逐门逐人各一次） */
    private final Set<UUID> visitedUuids = new HashSet<>();
    /** 玩家级防抖：同一玩家在该刻之前不再触发本扇门（不持久化，30s 内跨重启丢失可接受） */
    private final Map<UUID, Long> retrigger = new HashMap<>();

    public InkShadowPortalEntity(EntityType<? extends InkShadowPortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public InkShadowPortalEntity(Level level, UUID ownerUuid) {
        this(ModEntityTypes.INKSHADOW_PORTAL.get(), level);
        this.ownerUuid = ownerUuid;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, 0);
    }

    public boolean isActive() {
        return this.entityData.get(DATA_STATE) == 1;
    }

    private void setActiveState(boolean active) {
        this.entityData.set(DATA_STATE, active ? 1 : 0);
    }

    /** 起始门落地的状态：不可穿越、30s 后若仍未配对则自行消失。 */
    public void setPending(long expireTick) {
        setActiveState(false);
        this.expireTick = expireTick;
    }

    /** 终点门落地后双向配对：两门同刻到期。 */
    public void activate(long expireTick, UUID pairUuid, String pairDim, Vec3 pairPos) {
        setActiveState(true);
        this.expireTick = expireTick;
        this.pairUuid = pairUuid;
        this.pairDim = pairDim == null ? "" : pairDim;
        this.pairX = pairPos.x;
        this.pairY = pairPos.y;
        this.pairZ = pairPos.z;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
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
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        long now = serverLevel.getGameTime();
        // 1) 到期：自己与配对门一起消失（对方区块未加载时由对方自己的 tick 到期）
        if (this.expireTick > 0L && now >= this.expireTick) {
            discardPair(serverLevel);
            discard();
            return;
        }
        if (!isActive()) return;
        // 2) 每 5t 扫玩家：首次穿越 ⇒ 传送 + 门主人 +1 层 + 跃迁核心过门奖励
        if (this.tickCount % 5 == 0) {
            scanPlayers(serverLevel, now);
        }
        // 3) 每 20t 对门前敌对生物造成法术伤害
        if (this.tickCount % 20 == 0) {
            damageHostiles(serverLevel);
        }
    }

    private void scanPlayers(ServerLevel serverLevel, long now) {
        for (Player player : serverLevel.getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(0.6D))) {
            if (!(player instanceof ServerPlayer serverPlayer)) continue;
            if (!serverPlayer.isAlive() || serverPlayer.isSpectator()) continue;
            UUID id = serverPlayer.getUUID();
            if (this.visitedUuids.contains(id)) continue;
            if (serverPlayer.getPortalCooldown() > 0) continue;
            Long retriggerAt = this.retrigger.get(id);
            if (retriggerAt != null && now < retriggerAt) continue;
            if (!teleportToPair(serverLevel, serverPlayer)) continue;
            this.visitedUuids.add(id);
            this.retrigger.put(id, now + InkShadowSignItem.RETRIGGER_TICKS);
            serverPlayer.setPortalCooldown(InkShadowSignItem.RETRIGGER_TICKS);
            markArrivalCooldown(serverLevel, id, now);
            grantOwnerStack(serverLevel);
            WarpEngineChipItem.onPortalOrWaystoneTeleport(serverPlayer);
        }
    }

    /** 传送到配对门坐标；对方维度解析失败 ⇒ false（不记“已穿越”）。 */
    private boolean teleportToPair(ServerLevel fromLevel, ServerPlayer player) {
        if (this.pairUuid == null || this.pairDim.isEmpty()) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        ServerLevel target = dimensionLevel(server, this.pairDim);
        if (target == null) return false;
        Vec3 from = player.position();
        Vec3 to = new Vec3(this.pairX, this.pairY + 0.1D, this.pairZ);
        playTeleportEffects(fromLevel, from);
        player.teleportTo(target, this.pairX, this.pairY + 0.1D, this.pairZ,
                player.getYRot(), player.getXRot());
        // 落点特效在传送之后播（跨维度时玩家此时才在目标维度里，包才送得到）
        playTeleportEffects(target, to);
        return true;
    }

    /**
     * 末影传送视觉/听觉：`PORTAL` 粒子 + `SoundEvents.ENDERMAN_TELEPORT`
     * （与 {@code event/EnderDiceHandler#playTeleportEffects} 同源同参数：掷末影珍珠的传送音效）。
     */
    private static void playTeleportEffects(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.PORTAL,
                pos.x, pos.y + 1.0D, pos.z, 40, 0.5D, 0.5D, 0.5D, 0.05D);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * 到达对面门口时给对面门也写一次防抖：对面门若已加载，下一 tick 不会把玩家立刻弹回。
     * 玩家级 {@code setPortalCooldown} 已兜底，这里只是把逐门去重语义补齐。
     */
    private void markArrivalCooldown(ServerLevel serverLevel, UUID playerId, long now) {
        if (this.pairUuid == null || this.pairDim.isEmpty()) return;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;
        ServerLevel target = dimensionLevel(server, this.pairDim);
        if (target == null) return;
        if (target.getEntity(this.pairUuid) instanceof InkShadowPortalEntity pair) {
            pair.retrigger.put(playerId, now + InkShadowSignItem.RETRIGGER_TICKS);
        }
    }

    private void grantOwnerStack(ServerLevel serverLevel) {
        if (this.ownerUuid == null) return;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(this.ownerUuid);
        if (owner != null) {
            InkShadowSignManager.addStack(owner);
        }
    }

    private void damageHostiles(ServerLevel serverLevel) {
        for (Monster monster : serverLevel.getEntitiesOfClass(Monster.class,
                this.getBoundingBox().inflate(InkShadowSignItem.PORTAL_HAZARD_RADIUS))) {
            if (!monster.isAlive() || monster.isInvulnerable()) continue;
            monster.hurt(ModDamageTypes.skillDamage(serverLevel, this), InkShadowSignItem.PORTAL_HAZARD_DAMAGE);
        }
    }

    private void discardPair(ServerLevel serverLevel) {
        if (this.pairUuid == null || this.pairDim.isEmpty()) return;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;
        ServerLevel target = dimensionLevel(server, this.pairDim);
        if (target == null) return;
        if (target.getEntity(this.pairUuid) instanceof InkShadowPortalEntity pair && !pair.isRemoved()) {
            pair.discard();
        }
    }

    private static ServerLevel dimensionLevel(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 只有一段循环待机动画（4 根骨头绕 Y 轴缓转），状态机无需切换
        controllers.add(new AnimationController<>(this, CONTROLLER_IDLE, 0,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop(ANIM_IDLE))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(TAG_OWNER)) {
            this.ownerUuid = tag.getUUID(TAG_OWNER);
        }
        setActiveState(tag.getInt(TAG_STATE) == 1);
        if (tag.hasUUID(TAG_PAIR)) {
            this.pairUuid = tag.getUUID(TAG_PAIR);
        }
        this.pairDim = tag.getString(TAG_PAIR_DIM);
        this.pairX = tag.getDouble(TAG_PAIR_X);
        this.pairY = tag.getDouble(TAG_PAIR_Y);
        this.pairZ = tag.getDouble(TAG_PAIR_Z);
        this.expireTick = tag.getLong(TAG_EXPIRE);
        this.visitedUuids.clear();
        ListTag visited = tag.getList(TAG_VISITED, Tag.TAG_STRING);
        for (int i = 0; i < visited.size(); i++) {
            try {
                this.visitedUuids.add(UUID.fromString(visited.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // 旧存档脏数据：忽略这一条即可，不影响其它玩家计数
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ownerUuid != null) tag.putUUID(TAG_OWNER, this.ownerUuid);
        tag.putInt(TAG_STATE, isActive() ? 1 : 0);
        if (this.pairUuid != null) tag.putUUID(TAG_PAIR, this.pairUuid);
        tag.putString(TAG_PAIR_DIM, this.pairDim);
        tag.putDouble(TAG_PAIR_X, this.pairX);
        tag.putDouble(TAG_PAIR_Y, this.pairY);
        tag.putDouble(TAG_PAIR_Z, this.pairZ);
        tag.putLong(TAG_EXPIRE, this.expireTick);
        ListTag visited = new ListTag();
        for (UUID id : this.visitedUuids) {
            visited.add(StringTag.valueOf(id.toString()));
        }
        tag.put(TAG_VISITED, visited);
    }
}
