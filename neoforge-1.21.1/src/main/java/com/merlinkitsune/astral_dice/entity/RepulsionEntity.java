package com.merlinkitsune.astral_dice.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 强化拒止实体（效果牌「强化拒止」的可见部分，2026-10-18 新牌；属「怪力乱神」立牌）。
 *
 * <p><b>形态</b>：只有实体，**没有任何碰撞方块**（实体本身仍有 1.6×2.4 的拾取 / 攻击判定框，
 * 但 {@link #hurt} **恒返回 false** ⇒ 攻击对你无效，见〈免疫攻击〉）；{@code noPhysics = true}
 * ⇒ 不参与任何方块碰撞 / 推动。{@code extends LivingEntity}（**不**用 Mob AI）只为拿到生命值
 * 同步通道：耐久直接用 {@link #getHealth()} 承载（{@code MAX_HEALTH = 100}，由
 * {@code event/ModEntityEvents} 在 {@code EntityAttributeCreationEvent} 注册）。
 *
 * <p><b>吸附（用户需求原文）</b>：「强化拒止会自动吸附8格范围内的敌对目标，并对其持续施加
 * 缓慢和虚弱效果。强化拒止每10秒消耗吸附目标数量的耐久度（单次最大消耗5点）」
 * ＋「会以1秒为频率将半径8格范围敌对目标向强化拒止的位置逐渐缓慢拉扯（但目标无法逃离
 * 强化拒止影响范围）」＋「强化拒止不会吸引仇恨，但会持续为怪物施加 缓慢 (0:10)和虚弱 (0:10)」。
 *
 * <p><b>三层节拍</b>：
 * <ol>
 *   <li><b>每 tick</b>（{@link #tickTrackedTargets()}）：维护「被吸附目标」集合 ——
 *       死亡 / 移除 / 距离 &gt; 32 格的目标剪掉；3D 距离 &gt; 8 格的目标**按 3D 方向拉回
 *       7.5 格处**并清零速度（「无法逃离影响范围」，见 {@link #clampInside(Mob)}）。
 *       本层**不再施加拉力**；</li>
 *   <li><b>每 20 tick（1 秒）</b>（{@link #scanAndApplyEffects()} ＋ {@link #pullTrackedTargets()}）：
 *       先扫描 {@code Mob} 里 {@code instanceof Enemy}、存活、{@code distanceToSqr <= 64} 的目标，
 *       加入集合并施加 缓慢 (0:10) / 虚弱 (0:10)（{@code amplifier = 0}）；随后对 8 格内被吸附目标
 *       施加**一次**向心冲量（{@code 归一化水平方向 × PULL_STRENGTH}，保留 y）——
 *       用户原文「会以1秒为频率……逐渐缓慢拉扯」，**每秒一跳、不是每 tick**；</li>
 *   <li><b>每 200 tick（10 秒）</b>（{@link #consumeDurability()}）：耐久
 *       {@code -= min(当前 8 格内被吸附目标数, 5)}；扣到 &lt;= 0 ⇒ 碎裂粒子 + 玻璃破碎音 +
 *       {@link #discard()}。</li>
 * </ol>
 *
 * <p><b>拉力机制（实证，订正此前「0.06 会被 aiStep 覆盖 ⇒ 拉力弱于预期」的错误说法）</b>：
 * <ul>
 *   <li>{@code LivingEntity#moveRelative} 是把输入向量**加到**当前 {@code deltaMovement} 上
 *       （{@code setDeltaMovement(getDeltaMovement().add(...))}），**不是覆盖**；</li>
 *   <li>{@code travel} 在落地（非水 / 非岩浆）时把水平速度乘约 **0.546**（地面方块摩擦
 *       0.6 × 0.91）⇒ 每秒写入的 0.22 会在随后约 20 tick 里按 0.22 × 0.546^n 等比衰减继续位移，
 *       累计约 **0.2–0.5 格/秒**（随地面摩擦与 AI 输入方向浮动）；</li>
 *   <li>{@code Mob#aiStep} **不会**把 {@code deltaMovement} 重置为 0（它只往 delta 上叠加自己的
 *       移动输入），所以每秒一次的冲量不会被「每 tick 重算」抹掉；</li>
 *   <li>真实副作用：每次冲量都用 {@code setDeltaMovement} **覆盖** delta ⇒ **每跳抹掉目标当时的
 *       水平动量**（对正在奔跑的目标表现为一次顿挫，而不是叠加加速）。这是有意的「拉扯手感」，
 *       登记在此以免被当成 bug。</li>
 * </ul>
 *
 * <p><b>不吸引仇恨</b>：全程**不调用任何 {@code setTarget}**，也不清除目标原有仇恨 ——
 * 与「路障」（{@code BarricadeEntity}，主动改写仇恨）是**相反**的语义，两者不得混用判据。
 *
 * <p><b>免疫攻击</b>：{@link #hurt} **恒返回 false**（不吃任何伤害、也不接受任何伤害来源）——
 * 用户需求「主动攻击并不会对其造成伤害」。因此也不覆写 {@code die} / 死亡清理：
 * 唯一的消亡路径是耐久耗尽（{@link #shatter()}）。
 *
 * <p><b>持久化</b>：被吸附目标集合以 {@code Set<UUID>} 存字段，NBT 落盘为
 * {@code long[]}（每目标 2 个 long = most/least significant bits，避免依赖
 * {@code NbtUtils} 的 UUID 包装类型）。
 * ⚠️ 落盘的只是 UUID 集合：{@link ServerLevel#getEntity(UUID)} 在**区块未加载 / 跨维度**时返回
 * {@code null} ⇒ 该目标会被剪枝（**不保证跨卸载保留**；卸载再加载后要等下一次 8 格扫描才会
 * 重新吸附，期间不施加 缓慢 / 虚弱、也不计耐久）。
 *
 * <p><b>⚠️ 实机未验证项</b>（本轮按纪律不启动游戏）：
 * <ul>
 *   <li>拉力手感：位移量「约 0.2–0.5 格/秒」是按 {@code travel} 阻尼**推算**的，未实机读数；
 *       若观感过快 / 过慢，只调 {@link #PULL_STRENGTH}（0.22）一个量即可；</li>
 *   <li>钳制是否会被 AI 覆盖：8 格外的「拉回 7.5 格」用 {@code teleportTo}，已改 3D 方向 ⇒
 *       拉回后 3D 距离 7.5 ≤ 8，**不会**再每 tick 反复瞬移；但击退 / 寻路仍可能在两 tick 之间
 *       把它推出 8 格（下一 tick 会再次拉回，属预期抖动）；</li>
 *   <li>模型朝向 / 贴图面序（见 {@code client/RepulsionModel} 的说明）。</li>
 * </ul>
 *
 * <p><b>已登记风险</b>：{@code hurt} 恒 false ⇒ 若被**第三方 goal** 选为目标，该 goal 会永远
 * 打不死本实体（原版敌对 goal 只选 {@code Player} / {@code Villager} / {@code Monster}，通常
 * 不命中本实体；第三方若点名 {@code LivingEntity} 才会出现，每 tick 的视线 / 距离判定开销极小，
 * 但如实登记在此）。
 */
public class RepulsionEntity extends LivingEntity {

    /** 吸附 / 影响半径（格，球形；用户需求「半径8格」）。 */
    public static final double AFFECT_RADIUS = 8.0D;

    /** 越界目标的「拉回」半径（格，**3D** 距离）：比 {@link #AFFECT_RADIUS} 略小，
     * 保证拉回后 3D 距离 7.5 ≤ 8，不会每 tick 反复瞬移。 */
    public static final double CLAMP_RADIUS = 7.5D;

    /** 遗忘半径（格）：超出即从被吸附集合里剪掉（用户需求「无法逃离影响范围」仍以 8 格为准，
     * 32 格只是「跑太远就彻底不管」的清理阈值）。 */
    public static final double FORGET_RADIUS = 32.0D;

    /** 8 格内目标的向心拉力（**每秒一跳**的冲量，不是每 tick；机制见类 javadoc〈拉力机制〉）。
     * 每秒一次的 0.22 配合地面 {@code travel} 阻尼（每 tick ×0.546）约合 0.2–0.5 格/秒的缓慢位移。 */
    public static final double PULL_STRENGTH = 0.22D;

    /** 向心拉力的中心死区（格，**水平**距离）：已贴到中心的目标不再施加冲量，
     * 否则每秒改写一次 delta 只会让它在中心来回抖。 */
    private static final double PULL_DEADZONE = 0.35D;

    /** 扫描 / 施加效果 / 施加拉力的周期（tick；用户需求「以1秒为频率」）。 */
    private static final int SCAN_INTERVAL = 20;

    /** 耐久结算周期（tick；用户需求「每10秒消耗」）。 */
    private static final int DURABILITY_INTERVAL = 200;

    /** 缓慢 / 虚弱 的时长（tick；用户需求「缓慢 (0:10)和虚弱 (0:10)」）。 */
    private static final int EFFECT_DURATION = 200;

    /** 单次耐久结算的最大消耗（点；用户需求「单次最大消耗5点」）。 */
    private static final int MAX_DURABILITY_COST = 5;

    /** 碎裂粒子数量（用户需求「耐久耗尽会有碎裂粒子效果」；设计要求 CRIT 至少 16 个）。 */
    private static final int SHATTER_CRIT_COUNT = 32;
    private static final int SHATTER_POOF_COUNT = 8;

    /** NBT 键：被吸附目标（long[]，每目标 2 个 long）。 */
    private static final String TAG_TARGETS = "RepulsionTargets";

    /** 被吸附目标（服务端权威）。⚠️ 落盘的是 UUID 集合；{@code ServerLevel#getEntity} 在未加载 /
     * 跨维度时返回 null ⇒ 该目标会被剪枝（**不保证跨卸载保留**）。 */
    private final Set<UUID> trackedTargets = new HashSet<>();

    public RepulsionEntity(EntityType<? extends RepulsionEntity> entityType, Level level) {
        super(entityType, level);
        // 强化拒止是「钉在地上的装置」：不参与物理、不受重力、不会被生物 / 爆炸 / 水流推动
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * 主手侧（{@link LivingEntity} 的抽象方法）。强化拒止没有手、也不持有物品，
     * 这里只是为了让类可实例化。
     */
    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    // ── 装备槽（{@link LivingEntity} 的三个抽象方法）：强化拒止没有装备 ─────────────

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
        // 强化拒止不持有任何物品
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        long[] ids = new long[this.trackedTargets.size() * 2];
        int i = 0;
        for (UUID id : this.trackedTargets) {
            ids[i++] = id.getMostSignificantBits();
            ids[i++] = id.getLeastSignificantBits();
        }
        tag.putLongArray(TAG_TARGETS, ids);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.trackedTargets.clear();
        long[] ids = tag.getLongArray(TAG_TARGETS);
        for (int i = 0; i + 1 < ids.length; i += 2) {
            this.trackedTargets.add(new UUID(ids[i], ids[i + 1]));
        }
    }

    // ── 每 tick：剪枝 + 钳制（不施力） ─────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (this.isRemoved() || !this.isAlive()) return;

        // 剪枝 + 越界钳制：每 tick 做（「目标无法逃离影响范围」需要即时生效）
        this.tickTrackedTargets();
        if (this.tickCount % SCAN_INTERVAL == 0) {
            // 每秒一次：先刷新吸附集合与 缓慢 / 虚弱，再对界内目标施加**一次**向心冲量
            this.scanAndApplyEffects();
            this.pullTrackedTargets();
        }
        if (this.tickCount % DURABILITY_INTERVAL == 0) {
            this.consumeDurability();
        }
    }

    /**
     * 维护被吸附目标集合：剪枝（死亡 / 移除 / &gt; 32 格）＋ 越界钳制。
     *
     * <p>⚠️ 这里**不再**施加向心拉力 —— 拉力已改为每秒一次（{@link #pullTrackedTargets()}），
     * 本方法只做每 tick 都必须生效的清理与钳制。
     *
     * <p>⚠️ 用 {@link ServerLevel#getEntity(UUID)} 取回实体（跨维度 / 未加载返回 null ⇒ 剪枝）。
     */
    private void tickTrackedTargets() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        Iterator<UUID> it = this.trackedTargets.iterator();
        while (it.hasNext()) {
            Entity entity = serverLevel.getEntity(it.next());
            if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) {
                it.remove();
                continue;
            }
            double distanceSqr = mob.distanceToSqr(this);
            if (distanceSqr > FORGET_RADIUS * FORGET_RADIUS) {
                it.remove();
                continue;
            }
            if (distanceSqr > AFFECT_RADIUS * AFFECT_RADIUS) {
                // 越界 ⇒ 按 3D 方向拉回 7.5 格处（用户需求「目标无法逃离强化拒止影响范围」）
                this.clampInside(mob);
            }
        }
    }

    /**
     * 把越界目标沿**3D 方向**拉回 {@link #CLAMP_RADIUS} 处并清零速度。
     *
     * <p>目标点 = 本实体位置 + 归一化 3D 向量 × 7.5（不再只钳水平、y 保持不变）。
     * 落点若不可用（{@code level.noCollision} 为假 ⇒ 该处有方块碰撞 / 其他实体碰撞 / 出世界边界），
     * 依次把 y 上抬 0.5 / 1.0 / 1.5 找无碰撞落点；仍失败则**回退**为「只拉水平到 7.5 格、y 保持」。
     *
     * <p>为什么 3D 钳制能消除反复瞬移：钳制后目标与本实体的 **3D** 距离正好 7.5 ≤ 8，下一 tick
     * 不会再被判为越界；此前只钳水平时，竖直偏移大的目标会被每 tick 反复瞬移。
     * 回退路径（只钳水平）本身**不做**无碰撞校验，若回退点也嵌在方块里由原版碰撞推挤兜底
     * （宁可留竖直偏移，也不主动塞墙）。
     */
    private void clampInside(Mob mob) {
        Vec3 dir = this.position().subtract(mob.position());
        if (dir.lengthSqr() < 1.0E-6D) {
            // 正上方 / 正下方重合：给一个确定方向，避免除零
            dir = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 target = this.position().add(dir.normalize().scale(CLAMP_RADIUS));

        Vec3 landing = this.findFreeLanding(mob, target);
        if (landing == null) {
            // 回退：只钳水平到 7.5 格，y 保持
            double dx = mob.getX() - this.getX();
            double dz = mob.getZ() - this.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal < 1.0E-4D) {
                dx = 1.0D;
                dz = 0.0D;
                horizontal = 1.0D;
            }
            landing = new Vec3(this.getX() + dx / horizontal * CLAMP_RADIUS, mob.getY(),
                    this.getZ() + dz / horizontal * CLAMP_RADIUS);
        }
        mob.teleportTo(landing.x, landing.y, landing.z);
        mob.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * 依次尝试 {@code target} / {@code target.y+0.5} / {@code +1.0} / {@code +1.5}，返回第一个
     * 「把包围盒挪过去不会碰撞」的落点；四者都不可用时返回 {@code null}。
     *
     * <p>判据用 {@code Level#noCollision(Entity, AABB)}（{@code CollisionGetter} 的默认实现，两线同签名）：
     * 它会同时检查**方块碰撞 + 其他实体碰撞 + 世界边界**（{@code getBlockCollisions} /
     * {@code getEntityCollisions} / {@code borderCollision} 三者都为空才返回 true）。
     * ⇒ 目标身边挤满其他生物时，3D 落点常被判不可用而走水平回退，这是**有意**的保守取舍。
     */
    private Vec3 findFreeLanding(Mob mob, Vec3 target) {
        Vec3 origin = mob.position();
        for (int i = 0; i < 4; i++) {
            Vec3 candidate = target.add(0.0D, 0.5D * i, 0.0D);
            if (this.level().noCollision(mob, mob.getBoundingBox().move(candidate.subtract(origin)))) {
                return candidate;
            }
        }
        return null;
    }

    // ── 每秒：扫描 + 施加 缓慢 / 虚弱 + 一次向心冲量 ───────────────────────────

    /**
     * 半径 8 格（球形）内的敌对目标：加入被吸附集合并持续施加 缓慢 / 虚弱。
     *
     * <p>只认 {@code Mob} 且 {@code instanceof Enemy}（= 本模组「敌对目标」口径里的敌对生物那一半；
     * 中立生物与玩家**不吸附**，因为用户需求写的是「敌对目标」）。刻意**不**排除精英 / Boss ——
     * 需求未要求排除，且本牌不拉仇恨，不会把 Boss 的仇恨从玩家身上抢走。
     */
    private void scanAndApplyEffects() {
        AABB area = this.getBoundingBox().inflate(AFFECT_RADIUS);
        for (Mob mob : this.level().getEntitiesOfClass(Mob.class, area)) {
            if (!(mob instanceof Enemy)) continue;
            if (!mob.isAlive() || mob.isRemoved()) continue;
            if (mob.distanceToSqr(this) > AFFECT_RADIUS * AFFECT_RADIUS) continue;
            this.trackedTargets.add(mob.getUUID());
            // 0:10 = 200 tick、I 级（amplifier 0）；ambient=false、visible=true、showIcon=true
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    EFFECT_DURATION, 0, false, true, true));
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                    EFFECT_DURATION, 0, false, true, true));
        }
    }

    /**
     * 每秒（{@link #SCAN_INTERVAL} tick）对 8 格内被吸附目标施加**一次**向心冲量。
     *
     * <p>只在 {@link #tick()} 的 {@code tickCount % 20 == 0} 分支里调用（用户原文
     * 「会以1秒为频率……逐渐缓慢拉扯」）；水平距离 &lt; {@link #PULL_DEADZONE} 时跳过，
     * 避免目标在中心左右来回抖。
     */
    private void pullTrackedTargets() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        for (UUID id : new ArrayList<>(this.trackedTargets)) {
            Entity entity = serverLevel.getEntity(id);
            if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) continue;
            if (mob.distanceToSqr(this) > AFFECT_RADIUS * AFFECT_RADIUS) continue;
            double dx = this.getX() - mob.getX();
            double dz = this.getZ() - mob.getZ();
            if (Math.sqrt(dx * dx + dz * dz) < PULL_DEADZONE) continue;
            this.applyPull(mob);
        }
    }

    /**
     * 对单个目标写入一次向心冲量：{@code setDeltaMovement(水平归一化 × PULL_STRENGTH, 保留 y, ...)}。
     *
     * <p>⚠️ 此处是**覆盖**写法（不是叠加）：真实副作用是「每跳抹掉目标当时的水平动量」，
     * 机制与「不会被 aiStep 重算抹掉」的论证见类 javadoc〈拉力机制〉。
     */
    private void applyPull(Mob mob) {
        double dx = this.getX() - mob.getX();
        double dz = this.getZ() - mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4D) return;
        double vy = mob.getDeltaMovement().y;
        mob.setDeltaMovement(dx / horizontal * PULL_STRENGTH, vy, dz / horizontal * PULL_STRENGTH);
    }

    // ── 每 10 秒：耐久结算 ────────────────────────────────────────────────────

    /**
     * 每 10 秒按「当前 8 格内被吸附目标数」扣耐久（单次最多 {@link #MAX_DURABILITY_COST} 点）。
     * 目标数为 0 时不扣（没有吸附对象就没有消耗）。
     */
    private void consumeDurability() {
        int attached = this.countTrackedWithinAura();
        if (attached <= 0) return;
        float cost = Math.min(attached, MAX_DURABILITY_COST);
        float next = this.getHealth() - cost;
        if (next <= 0.0F) {
            this.shatter();
        } else {
            this.setHealth(next);
        }
    }

    /** 当前仍在 8 格内的被吸附目标数量（耐久结算口径）。 */
    private int countTrackedWithinAura() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return 0;
        int count = 0;
        for (UUID id : new ArrayList<>(this.trackedTargets)) {
            Entity entity = serverLevel.getEntity(id);
            if (entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved()
                    && mob.distanceToSqr(this) <= AFFECT_RADIUS * AFFECT_RADIUS) {
                count++;
            }
        }
        return count;
    }

    /**
     * 耐久耗尽：碎裂粒子（{@code CRIT} + 少量 {@code POOF}）＋ 玻璃破碎音 ⇒ 直接 {@link #discard()}。
     *
     * <p>不走 {@code die}：本实体免疫一切伤害，{@code die} 只会在耐久耗尽时被这条路径手工触发；
     * 用 {@code discard} 可以跳过死亡动画 / 死亡音 / 战利品结算。
     */
    private void shatter() {
        double cx = this.getX();
        double cy = this.getY() + this.getBbHeight() * 0.5D;
        double cz = this.getZ();
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT, cx, cy, cz,
                    SHATTER_CRIT_COUNT, 0.6D, 0.9D, 0.6D, 0.12D);
            serverLevel.sendParticles(ParticleTypes.POOF, cx, cy, cz,
                    SHATTER_POOF_COUNT, 0.4D, 0.6D, 0.4D, 0.02D);
        }
        this.level().playSound(null, cx, cy, cz, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);
        this.setHealth(0.0F);
        this.trackedTargets.clear();
        this.discard();
    }

    // ── 免疫 / 钉地 ───────────────────────────────────────────────────────────

    /**
     * 主动攻击**不会造成任何伤害**（用户需求「主动攻击并不会对其造成伤害」）。
     *
     * <p>⚠️ 这是**恒 false** 的全量免疫：不吃生物伤害、不吃环境伤害、也不吃 {@code /kill}；
     * 唯一消亡路径是耐久耗尽（{@link #shatter()}）。代价与「路障」类似 ——
     * 绕过平台伤害事件与无敌帧，但本实体本来就不打算接受任何伤害，故无副作用。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 钉在地上：不吃爆炸 / 活塞 / 水流等一切外力推移。 */
    @Override
    public void push(double x, double y, double z) {
        // 故意空实现
    }

    /** 强化拒止不是战利品也不是经验来源（耐久耗尽即碎裂，不掉物品）。 */
    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }
}
