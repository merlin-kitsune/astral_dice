package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.chip.WarpEngineChipItem;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignItem;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignManager;
import com.merlinkitsune.starengine.geckolib.animatable.GeoEntity;
import com.merlinkitsune.starengine.geckolib.animatable.instance.AnimatableInstanceCache;
import com.merlinkitsune.starengine.geckolib.animatable.manager.AnimatableManager;
import com.merlinkitsune.starengine.geckolib.animation.AnimationController;
import com.merlinkitsune.starengine.geckolib.animation.RawAnimation;
import com.merlinkitsune.starengine.geckolib.util.GeckoLibUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import com.mojang.serialization.Codec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 小猎手立牌「暗影之门」的单扇传送门。
 *
 * <h2>状态</h2>
 * {@code PENDING}：起始门已放下、终点门还没放（不可穿越；渲染器画成缩小）；
 * {@code ACTIVE}：两门互指后的可用门（存活到 {@code expireTick}，到期两门一起消失）。
 *
 * <h2>穿门判定（2026-11-10 用户验收改版）</h2>
 * 服务端每 2t 扫描碰撞箱外扩 0.6 格内的玩家：<b>站定</b>累计 {@link InkShadowSignItem#STAND_TICKS}
 * （1.5 s）才真正传送，离开碰撞箱即清零；每扇门对每名玩家另记
 * {@link InkShadowSignItem#TELEPORT_COOLDOWN_TICKS}（5 s）静默冷却，冷却中不传送也不给提示。
 * <p>门主人（放置者）在一对门刚成型时<b>不会</b>被自己的门立刻传送：必须先在水平方向走出
 * {@link InkShadowSignItem#OWNER_ARM_RADIUS}（4 格）把门「解除保险」（{@code ownerArmed}），
 * 之后才与普通玩家同规则。
 * <p>{@code visitedUuids} 只用于被动「逐门逐人各计一次」：同一扇门对同一名玩家最多给主人 +1 层，
 * 但传送本身可以反复使用。
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
    /** 本扇门已计过被动层数的玩家（逐门逐人各一次；不影响能否再传送） */
    private final Set<UUID> visitedUuids = new HashSet<>();
    /** 玩家级传送冷却截止刻（静默、无提示） */
    private final Map<UUID, Long> teleportCooldown = new HashMap<>();
    /** 玩家在本扇门内的累计站定 tick（离开即清零） */
    /** 门本身的环境音间隔（tick）；约 4.5 s 一次。 */
    private static final int AMBIENT_INTERVAL_TICKS = 90;

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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, 0);
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
    public boolean isPushable() {
        return false;
    }

    /**
     * 26.1.2 适配：{@code Entity#hurt} 已 final，攻击入口改为 {@code hurtServer}；
     * 传送门不可被伤害（同时 {@code setInvulnerable(true)}），恒返回 false。
     * 原 1.21.1 的 {@code isInvulnerableTo(DamageSource)} 与无参 {@code canBeCollidedWith()}
     * 在 26.1.2 的 Entity 上已无对应签名，故删除（不可碰撞仍由 {@code noPhysics} + 碰撞箱无效果保证）。
     */
    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float amount) {
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
        // 2) 门主人保险：走出半径后解除（放置完不会立刻把自己传走）
        armOwnerOnLeaving(serverLevel);
        // 2.5) 门本身的循环音效（下界传送门环境音，每 4.5 s 一次；起始门也算「门」）
        if (this.tickCount % AMBIENT_INTERVAL_TICKS == 0 && this.tickCount > 0) {
            serverLevel.playSound(null, this.getX(), this.getY() + 0.4D, this.getZ(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        if (!isActive()) return;
        // 3) 每 2t 扫玩家：站定 1.5s ⇒ 传送 + 门主人首次 +1 层 + 跃迁核心过门奖励
        if (this.tickCount % 2 == 0) {
            scanPlayers(serverLevel, now);
        }
        // 4) 每 20t 对门前敌对生物造成法术伤害
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
        MinecraftServer server = player.level().getServer();
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
        // 26.1.2：teleportTo 增加了 Set<Relative> 与「是否保持原朝向」标记，语义按旧 6 参调用平移
        player.teleportTo(target, this.pairX, this.pairY + 0.05D, this.pairZ,
                java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        // 落点特效在传送之后播（跨维度时玩家此时才在目标维度里，包才送得到）
        playTeleportParticles(target, to);
        playPortalSound(target, to, SoundEvents.PORTAL_TRAVEL, 0.8F);
        return true;
    }

    /** 传送粒子：起落点各 40 粒 `PORTAL`。 */
    private static void playTeleportParticles(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.PORTAL,
                pos.x, pos.y + 1.0D, pos.z, 40, 0.5D, 0.5D, 0.5D, 0.05D);
    }

    /** 下界传送门音效（`block.portal.*`）：门本身环境音 / 进入 trigger / 传送 travel。 */
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
            pair.teleportCooldown.put(playerId, now + InkShadowSignItem.TELEPORT_COOLDOWN_TICKS);
            pair.standingTicks.remove(playerId);
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
        Identifier id = Identifier.tryParse(dimension);
        if (id == null) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 只有一段循环待机动画（4 根骨头绕 Y 轴缓转），状态机无需切换
        // 26.1.2（GeckoLib 5）签名 = (id, transitionTicks, stateController)，不再把 animatable 作为首参
        controllers.add(new AnimationController<InkShadowPortalEntity>(CONTROLLER_IDLE, 0,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop(ANIM_IDLE))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /**
     * 26.1.2 适配：存档读写由 {@code CompoundTag} 换成 {@link ValueInput}/{@link ValueOutput}；
     * 后者没有 UUID 读写，UUID 一律存字符串（同 {@code SykeTentacleEntity} 的做法），
     * visited 集合改用 {@code Codec.STRING} 的 typed list（元素仍为 UUID 字符串）。
     *
     * <p>⚠️ 不为 {@code super.readAdditionalSaveData}/{@code super.addAdditionalSaveData}：
     * 26.1.2 的 Entity 把这两个方法声明为 {@code protected abstract}，直接调 {@code super.} 抽象方法
     * 是编译错误（{@code SouriDrinkEntity}/{@code SykeTentacleEntity} 均为纯覆写、不调 super）。
     */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.getString(TAG_OWNER).ifPresent(value -> this.ownerUuid = parseUuid(value));
        setActiveState(input.getIntOr(TAG_STATE, 0) == 1);
        input.getString(TAG_PAIR).ifPresent(value -> this.pairUuid = parseUuid(value));
        this.pairDim = input.getStringOr(TAG_PAIR_DIM, "");
        this.pairX = input.getDoubleOr(TAG_PAIR_X, 0.0D);
        this.pairY = input.getDoubleOr(TAG_PAIR_Y, 0.0D);
        this.pairZ = input.getDoubleOr(TAG_PAIR_Z, 0.0D);
        this.expireTick = input.getLongOr(TAG_EXPIRE, 0L);
        this.ownerArmed = input.getBooleanOr("OwnerArmed", false);
        this.visitedUuids.clear();
        for (String raw : input.listOrEmpty(TAG_VISITED, Codec.STRING)) {
            UUID id = parseUuid(raw);
            // 旧存档脏数据：忽略这一条即可，不影响其它玩家计数
            if (id != null) this.visitedUuids.add(id);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (this.ownerUuid != null) output.putString(TAG_OWNER, this.ownerUuid.toString());
        output.putInt(TAG_STATE, isActive() ? 1 : 0);
        if (this.pairUuid != null) output.putString(TAG_PAIR, this.pairUuid.toString());
        output.putString(TAG_PAIR_DIM, this.pairDim);
        output.putDouble(TAG_PAIR_X, this.pairX);
        output.putDouble(TAG_PAIR_Y, this.pairY);
        output.putDouble(TAG_PAIR_Z, this.pairZ);
        output.putLong(TAG_EXPIRE, this.expireTick);
        output.putBoolean("OwnerArmed", this.ownerArmed);
        ValueOutput.TypedOutputList<String> visited = output.list(TAG_VISITED, Codec.STRING);
        for (UUID id : this.visitedUuids) {
            visited.add(id.toString());
        }
    }

    /** 宽容解析 UUID 字符串；非法/空值返回 null（存档自愈，不抛异常）。 */
    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
