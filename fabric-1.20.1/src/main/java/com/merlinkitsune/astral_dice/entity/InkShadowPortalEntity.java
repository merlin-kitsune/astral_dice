package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.chip.WarpEngineChipItem;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignItem;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignManager;
import com.merlinkitsune.starengine.geckolib.animatable.GeoEntity;
import com.merlinkitsune.starengine.geckolib.core.animatable.instance.AnimatableInstanceCache;
import com.merlinkitsune.starengine.geckolib.core.animation.AnimatableManager;
import com.merlinkitsune.starengine.geckolib.core.animation.AnimationController;
import com.merlinkitsune.starengine.geckolib.core.animation.RawAnimation;
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
 * 小猎手立牌「暗影之门」的单扇传送门（1.20.1 等价实现）。
 *
 * <h2>状态</h2>
 * {@code PENDING}：起始门已放下、终点门还没放（不可穿越；渲染器画成缩小）；
 * {@code ACTIVE}：两门互指后的可用门（存活到 {@code expireTick}，到期两门一起消失）。
 *
 * <h2>穿门判定（2026-11-10 用户验收改版）</h2>
 * 服务端每 2t 扫描：必须落在门盘中心 {@link InkShadowSignItem#STAND_RADIUS} 之内、并累计
 * <b>站定</b> {@link InkShadowSignItem#STAND_TICKS}（1.5 s）才真正传送，离开即清零；每扇门对每名玩家另记
 * {@link InkShadowSignItem#TELEPORT_COOLDOWN_TICKS}（5 s）静默冷却，冷却中不传送也不给提示。
 * <p>门主人（放置者）在一对门刚成型时<b>不会</b>被自己的门立刻传送：必须先在水平方向走出
 * {@link InkShadowSignItem#OWNER_ARM_RADIUS}（4 格）把门「解除保险」（{@code ownerArmed}），
 * 之后才与普通玩家同规则。
 * <p>{@code visitedUuids} 只用于被动「逐门逐人各计一次」：同一扇门对同一名玩家最多给主人 +1 层，
 * 但传送本身可以反复使用。
 *
 * <h2>危险区</h2>
 * 每 20t 对 {@link Monster} 造成 {@link InkShadowSignItem#PORTAL_HAZARD_DAMAGE} 点技能伤害。
 *
 * <p><b>1.20.1 平台差异</b>：{@code defineSynchedData()} 无参；
 * {@code skipAttackInteraction(Entity)} 参与「不可攻击」判定；GeckoLib 走
 * {@code starengine.geckolib.core.*} 路径。
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
    private static final String TAG_ARRIVAL_LOCKED = "ArrivalLocked";
    private static final String TAG_OWNER_ARMED = "OwnerArmed";

    private static final String CONTROLLER_IDLE = "idle";
    private static final String ANIM_IDLE = "animation.inkshadoweffect.idle";

    /** 门本身的环境音间隔（tick）；约 4.5 s 一次。 */
    private static final int AMBIENT_INTERVAL_TICKS = 90;

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
    /** 本扇门已计过被动层数的玩家（逐门逐人各一次；不影响能否再传送） */
    private final Set<UUID> visitedUuids = new HashSet<>();
    /** 落地后尚未离开门盘中心的玩家（必须走远才解除，防止原地被弹回） */
    private final Set<UUID> arrivalLocked = new HashSet<>();
    /** 玩家级传送冷却截止刻（静默、无提示） */
    private final Map<UUID, Long> teleportCooldown = new HashMap<>();
    /** 玩家在本扇门内的累计站定 tick（离开即清零） */
    private final Map<UUID, Integer> standingTicks = new HashMap<>();
    /** 门主人是否已离开过保险半径（false = 主人还站在门口，暂不传送他） */
    private boolean ownerArmed;

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
    protected void defineSynchedData() {
        this.entityData.define(DATA_STATE, 0);
    }

    public boolean isActive() {
        return this.entityData.get(DATA_STATE) == 1;
    }

    private void setActiveState(boolean active) {
        this.entityData.set(DATA_STATE, active ? 1 : 0);
    }

    /** 起始门落地的状态：不可穿越、窗口结束仍未配对则自行消失。 */
    public void setPending(long expireTick) {
        setActiveState(false);
        this.expireTick = expireTick;
    }

    /** 终点门落地后双向配对：两门同刻到期；主人保险复位，必须先离开一次。 */
    public void activate(long expireTick, UUID pairUuid, String pairDim, Vec3 pairPos) {
        setActiveState(true);
        this.expireTick = expireTick;
        this.pairUuid = pairUuid;
        this.pairDim = pairDim == null ? "" : pairDim;
        this.pairX = pairPos.x;
        this.pairY = pairPos.y;
        this.pairZ = pairPos.z;
        this.ownerArmed = false;
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
    public boolean isAttackable() {
        return false;
    }

    /** 1.20.1 平台差异：本线签名是 {@code skipAttackInteraction(Entity)}（1.21 为 Player）。 */
    @Override
    public boolean skipAttackInteraction(Entity entity) {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /**
     * 本门所在区块是否已被本实体申请为常载。
     * <p>2026-11-11 实机定位：放下来的门如果所在区块被卸载，门实体不会被写进区块存档，
     * 回来时永远消失（{@code /summon} 出来的门则正常）。配对门必须在 3:00 内一直可用，
     * 所以本门自己把区块钉住，到期/作废时在 {@link #remove} 里释放；即便服务端崩溃，
     * 重开后门实体加载、读到过期刻会自行解锁。
     */
    private boolean chunkForced;

    private void forceOwnChunk() {
        if (!(this.level() instanceof ServerLevel serverLevel) || this.chunkForced) return;
        serverLevel.setChunkForced(((int) Math.floor(this.getX())) >> 4, ((int) Math.floor(this.getZ())) >> 4, true);
        this.chunkForced = true;
    }

    private void releaseOwnChunk() {
        if (!(this.level() instanceof ServerLevel serverLevel) || !this.chunkForced) return;
        serverLevel.setChunkForced(((int) Math.floor(this.getX())) >> 4, ((int) Math.floor(this.getZ())) >> 4, false);
        this.chunkForced = false;
    }

    @Override
    public void remove(RemovalReason reason) {
        releaseOwnChunk();
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        forceOwnChunk();
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        long now = serverLevel.getGameTime();
        // 1) 到期：自己与配对门一起消失（对方区块未加载时由对方自己的 tick 到期）
        if (this.expireTick > 0L && now >= this.expireTick) {
            discardPair(serverLevel);
            discard();
            return;
        }
        // 2) 门主人保险：走出半径后解除（放置完不会立刻把自己传走）
        armOwnerOnLeaving(serverLevel);
        // 3) 门本身的循环音效（下界传送门环境音，每 4.5 s 一次；起始门也算「门」）
        if (this.tickCount % AMBIENT_INTERVAL_TICKS == 0 && this.tickCount > 0) {
            serverLevel.playSound(null, this.getX(), this.getY() + 0.4D, this.getZ(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        if (!isActive()) return;
        // 4) 每 2t 扫玩家：站定 1.5s ⇒ 传送 + 门主人首次 +1 层 + 跃迁核心过门奖励
        if (this.tickCount % 2 == 0) {
            scanPlayers(serverLevel, now);
        }
        // 5) 每 20t 对门前敌对生物造成法术伤害
        if (this.tickCount % 20 == 0) {
            damageHostiles(serverLevel);
        }
    }

    /**
     * 玩家是否「踩在门正上方」：与门盘中心的水平距离 ≤ {@link InkShadowSignItem#STAND_RADIUS}，
     * 且脚底与门所在层相差不超过 ±0.75 格（避免隔着一格地面也被判定）。
     */
    private boolean isStandingOn(Player player) {
        double dx = player.getX() - this.getX();
        double dz = player.getZ() - this.getZ();
        double r = InkShadowSignItem.STAND_RADIUS;
        if (dx * dx + dz * dz > r * r) return false;
        double dy = player.getY() - this.getY();
        return dy >= -0.5D && dy <= 1.0D;
    }

    /** 有人踩在门上时每 2t 播一次门面粒子（踩上去就有反馈，不等 1.5s 站定完成）。 */
    private void spawnStandParticles(ServerLevel serverLevel) {
        serverLevel.sendParticles(ParticleTypes.PORTAL,
                this.getX(), this.getY() + 0.15D, this.getZ(),
                6, 0.32D, 0.05D, 0.32D, 0.02D);
    }

    /** 门主人离开保险半径后置 true；主人不在线则保持未解除。 */
    private void armOwnerOnLeaving(ServerLevel serverLevel) {
        if (this.ownerArmed || this.ownerUuid == null) return;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(this.ownerUuid);
        if (owner == null) return;
        double r = InkShadowSignItem.OWNER_ARM_RADIUS;
        if (owner.distanceToSqr(this) > r * r) {
            this.ownerArmed = true;
        }
    }

    private void scanPlayers(ServerLevel serverLevel, long now) {
        pruneArrivalLocks(serverLevel);
        Set<UUID> inside = new HashSet<>();
        for (Player player : serverLevel.getEntitiesOfClass(Player.class,
                this.getBoundingBox().inflate(1.0D))) {
            if (!(player instanceof ServerPlayer serverPlayer)) continue;
            if (!serverPlayer.isAlive() || serverPlayer.isSpectator()) continue;
            // 2026-11-10 用户裁决:必须踩在门正上方(水平半径内),贴着门边走过不算
            if (!isStandingOn(serverPlayer)) continue;
            UUID id = serverPlayer.getUUID();
            inside.add(id);
            // 踩上去就播粒子(与是否在 5s 冷却、门主人保险是否解除无关),给「正在生效」的即时反馈
            spawnStandParticles(serverLevel);
            // 刚落地还没走远：不累计站定，也不允许再次传送
            if (this.arrivalLocked.contains(id)) continue;
            Long cdUntil = this.teleportCooldown.get(id);
            if (cdUntil != null && now < cdUntil) continue;
            if (this.ownerUuid != null && this.ownerUuid.equals(id) && !this.ownerArmed) continue;
            // 进入时（本次站定的第一拍）播下界传送门「进入」音效 block.portal.trigger
            if (!this.standingTicks.containsKey(id)) {
                serverLevel.playSound(null, this.getX(), this.getY() + 0.1D, this.getZ(),
                        SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
            int stand = this.standingTicks.getOrDefault(id, 0) + 2;
            if (stand < InkShadowSignItem.STAND_TICKS) {
                this.standingTicks.put(id, stand);
                continue;
            }
            if (!teleportToPair(serverLevel, serverPlayer)) {
                this.standingTicks.remove(id);
                continue;
            }
            this.standingTicks.remove(id);
            this.teleportCooldown.put(id, now + InkShadowSignItem.TELEPORT_COOLDOWN_TICKS);
            markArrivalCooldown(serverLevel, id, now);
            if (this.visitedUuids.add(id)) {
                grantOwnerStack(serverLevel);
            }
            WarpEngineChipItem.onPortalOrWaystoneTeleport(serverPlayer);
        }
        // 离开门的玩家站定进度清零（下一次进来重新计时）
        this.standingTicks.keySet().retainAll(inside);
    }

    /** 传送到配对门坐标；对方维度解析失败 ⇒ false（不消耗冷却、不记已穿越）。 */
    private boolean teleportToPair(ServerLevel fromLevel, ServerPlayer player) {
        if (this.pairUuid == null || this.pairDim.isEmpty()) return false;
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        ServerLevel target = dimensionLevel(server, this.pairDim);
        if (target == null) return false;
        Vec3 from = player.position();
        Vec3 to = new Vec3(this.pairX, this.pairY + 0.05D, this.pairZ);
        boolean crossDimension = target != fromLevel;
        // 起点侧：跨维度时补一次「出下界传送门」音效（目标维度的声音传不过来）
        playTeleportParticles(fromLevel, from);
        if (crossDimension) {
            playPortalSound(fromLevel, from, SoundEvents.PORTAL_TRAVEL, 0.8F);
        }
        player.teleportTo(target, this.pairX, this.pairY + 0.05D, this.pairZ,
                player.getYRot(), player.getXRot());
        // 落点特效在传送之后播（跨维度时玩家此时才在目标维度里，包才送得到）
        playTeleportParticles(target, to);
        playPortalSound(target, to, SoundEvents.PORTAL_TRAVEL, 0.8F);
        return true;
    }

    /** 传送粒子：起落点各 40 粒 {@code PORTAL}。 */
    private static void playTeleportParticles(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.PORTAL,
                pos.x, pos.y + 1.0D, pos.z, 40, 0.5D, 0.5D, 0.5D, 0.05D);
    }

    /** 下界传送门音效（{@code block.portal.*}）：门本身环境音 / 进入 trigger / 传送 travel。 */
    private static void playPortalSound(ServerLevel level, Vec3 pos, net.minecraft.sounds.SoundEvent sound, float volume) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS, volume, 1.0F);
    }

    /** 到达对面门时给对面门也写一次本玩家的冷却，防止刚落脚就被弹回。 */
    private void markArrivalCooldown(ServerLevel serverLevel, UUID playerId, long now) {
        if (this.pairUuid == null || this.pairDim.isEmpty()) return;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;
        ServerLevel target = dimensionLevel(server, this.pairDim);
        if (target == null) return;
        if (target.getEntity(this.pairUuid) instanceof InkShadowPortalEntity pair) {
            pair.arrivalLocked.add(playerId);
            pair.teleportCooldown.put(playerId, now + InkShadowSignItem.TELEPORT_COOLDOWN_TICKS);
            pair.standingTicks.remove(playerId);
        }
    }

    /**
     * 落地锁清理：玩家离开门盘中心 {@link InkShadowSignItem#PORTAL_EXIT_RADIUS} 后解除，
     * 或离线/换维度时直接丢弃。锁在时 scanPlayers 不累计站定，避免到点原地被弹回。
     */
    private void pruneArrivalLocks(ServerLevel serverLevel) {
        if (this.arrivalLocked.isEmpty()) return;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;
        double r = InkShadowSignItem.PORTAL_EXIT_RADIUS;
        double r2 = r * r;
        this.arrivalLocked.removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            // 离线/换维度时保留锁：跨维度传送的落点可能还没切到目标维度，
            // 此时清锁会让落点门立刻重新可传送，正是「到点后被弹回」的来源。
            if (player == null) return false;
            if (player.level() != serverLevel) return false;
            double dx = player.getX() - this.getX();
            double dz = player.getZ() - this.getZ();
            return dx * dx + dz * dz > r2;
        });
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

    // ===== GeckoLib =====

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 只有一段循环待机动画（4 根骨头绕 Y 轴缓转），状态机无需切换
        controllers.add(new AnimationController<>(this, CONTROLLER_IDLE, 0,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop(ANIM_IDLE))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ===== 存档 =====

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
        this.ownerArmed = tag.getBoolean(TAG_OWNER_ARMED);
        this.visitedUuids.clear();
        ListTag visited = tag.getList(TAG_VISITED, Tag.TAG_STRING);
        for (int i = 0; i < visited.size(); i++) {
            try {
                this.visitedUuids.add(UUID.fromString(visited.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // 旧存档脏数据：忽略这一条即可，不影响其它玩家计数
            }
        }
        this.arrivalLocked.clear();
        ListTag lockedList = tag.getList(TAG_ARRIVAL_LOCKED, Tag.TAG_STRING);
        for (int i = 0; i < lockedList.size(); i++) {
            try {
                this.arrivalLocked.add(UUID.fromString(lockedList.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // 旧存档脏数据：忽略这一条即可
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
        tag.putBoolean(TAG_OWNER_ARMED, this.ownerArmed);
        ListTag visited = new ListTag();
        for (UUID id : this.visitedUuids) {
            visited.add(StringTag.valueOf(id.toString()));
        }
        tag.put(TAG_VISITED, visited);
        ListTag lockedList = new ListTag();
        for (UUID id : this.arrivalLocked) {
            lockedList.add(StringTag.valueOf(id.toString()));
        }
        tag.put(TAG_ARRIVAL_LOCKED, lockedList);
    }
}
