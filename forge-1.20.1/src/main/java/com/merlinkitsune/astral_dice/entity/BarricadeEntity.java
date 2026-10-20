package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.starengine.combat.EliteTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 路障实体（效果牌「路障」的可见部分，2026-10-17 新牌；2026-10-17 移植到 forge-1.20.1）。
 *
 * <p><b>形态</b>：可见实体 + 隐形碰撞方块（{@link com.merlinkitsune.astral_dice.block.BarricadeBlock}）的混合体。
 * 实体本身 {@code extends LivingEntity}（**不**用 Mob AI）：只负责渲染、承受攻击、拉仇恨；
 * 「像栅栏一样挡路」由同格的隐形碰撞方块提供（方块高 1.5 格、不可破坏、不参与寻路）。
 * 两者**成对存在**：牌放下时同时生成；实体死亡 / 移除（KILLED / DISCARDED）时删方块；
 * 方块被外力清掉时实体自毁（见 {@link #tick()} 的 100 tick 校验）。
 *
 * <p><b>耐久</b>：最大生命 30（属性由 {@code event/ModEntityEvents} 经
 * {@code EntityAttributeCreationEvent} 注册）。受伤逻辑**完全覆写** {@link #hurt}：
 * 只接受「有生物来源」的伤害（{@code source.getEntity() instanceof LivingEntity}，
 * 怪物 / 玩家 / 其发射物都算），并且**无视伤害数值、每次固定扣 1 点**（用户需求原文
 * 「被怪物攻击会降低一点」）。无来源伤害（坠落 / 冻结 / {@code /kill}）一律不吃，
 * 避免路障被环境伤害白嫖耐久。
 *
 * <p><b>拉仇恨</b>：服务端每 4 tick 对 {@link Mob} 做一次「球形 6 格」扫描，过滤
 * 「敌对（{@link Enemy}）+ 非精英非 Boss（{@link EliteTargets#isEliteOrBoss}）+ 存活」，
 * 命中即 {@code mob.setTarget(this)}。每 4 tick 重设是为了对抗怪物自带 AI 的目标覆盖
 * （{@code TargetGoal} 会在同一 tick 内改回原目标）；两个轮询周期之间的「改走」由
 * {@code event/BarricadeAggroHandler} 在 {@code LivingChangeTargetEvent} 上当场改回（见该类注释）。
 *
 * <p><b>受击反馈</b>：{@link #hurt} 里调 {@code level().broadcastDamageEvent(this, source)} ⇒
 * 客户端收到 {@code ClientboundDamageEventPacket} 后走 {@code LivingEntity#handleDamageEvent}
 * （置 {@code hurtTime / hurtDuration = 10} = 红闪，并由客户端侧播受击音）。
 * ⚠️ **不能用「2 号实体事件」**：1.20.1 / 1.21.1 的 {@code LivingEntity#handleEntityEvent}
 * 已**没有** 2 号分支（只剩 3 / 29 / 30 / 46 …），发 {@code (byte) 2} 纯属空转。
 *
 * <p><b>⚠️ 全覆写 {@link #hurt} 的代价（如实披露，不是遗漏）</b>：
 * <ul>
 *   <li><b>绕过平台伤害事件</b>：NeoForge 的 {@code LivingIncomingDamageEvent} /
 *       {@code LivingDamageEvent}、Forge 的 {@code LivingAttackEvent} / {@code LivingHurtEvent} /
 *       {@code LivingDamageEvent} 都不会为路障派发 ⇒ 第三方（或本模组）的伤害修正、免疫、反伤
 *       **不会**作用于路障；</li>
 *   <li><b>绕过格挡与无敌帧</b>：原版 {@code LivingEntity#hurt} 里的盾牌 / 圣盾格挡判定与
 *       {@code invulnerableTime} 冷却全部跳过 —— 这正是「每下固定 −1」所需；</li>
 *   <li><b>没有击杀归因</b>：不写 {@code setLastHurtByMob} / {@code lastHurtByPlayer} ⇒
 *       击杀统计与掉落归属不会把路障记到攻击者名下。</li>
 * </ul>
 * 以上都是「每次固定 −1、无视伤害数值」这一需求的**直接代价**。
 *
 * <p><b>不掉落</b>：{@link #shouldDropLoot()} / {@link #shouldDropExperience()} 恒 false
 * ⇒ 死亡时既不产战利品也不给经验（用户需求未要求掉落，且路障是「工具」不是战利品）。
 *
 * <p><b>1.20.1 平台差异</b>（相对 neoforge-1.21.1 蓝本）：
 * <ul>
 *   <li>{@code addAdditionalSaveData} / {@code readAdditionalSaveData}：{@code Entity} 里是
 *       {@code protected abstract}，但 **{@code LivingEntity} 已放宽为 {@code public}**
 *       （1.20.1 源码 {@code LivingEntity.java:680 / :712}，javac 实测「正在尝试分配更低的访问权限;
 *       以前为public」）⇒ 覆写体必须写 {@code public}，与 1.21.1 蓝本同形；</li>
 *   <li>{@code shouldDropExperience()} 在 1.20.1 的 {@code LivingEntity} 里**同样可覆写**
 *       （源码 {@code LivingEntity.java:561} 为 {@code public boolean}）⇒ 掉落口径无需改走
 *       {@code getExperienceReward()} / {@code dropCustomDeathLoot} 兜底；</li>
 *   <li>{@code defineSynchedData()} 由 {@code LivingEntity} 实现（{@code protected void}），
 *       本类不需要覆写 —— 要实现的抽象方法仍是 4 个（装备槽 ×3 + {@code getMainArm}）。</li>
 * </ul>
 */
public class BarricadeEntity extends LivingEntity {

    /** 每次被生物命中固定扣掉的耐久（无视伤害数值，用户需求「被怪物攻击会降低一点」）。 */
    public static final float DAMAGE_PER_HIT = 1.0F;

    /** 拉仇恨半径（格，球形；用户需求「6 格范围内」）。 */
    public static final double AGGRO_RADIUS = 6.0D;

    /** 死亡 / 移除时解除仇恨的半径（格；比仇恨半径大，保证漏网的目标也被清掉）。 */
    public static final double RELEASE_RADIUS = 8.0D;

    /** 拉仇恨/重设目标的周期（tick）。 */
    private static final int AGGRO_INTERVAL = 4;

    /** 校验碰撞方块仍在的周期（tick）：被 {@code /setblock} 等外力清掉时自毁。 */
    private static final int BLOCK_CHECK_INTERVAL = 100;

    /** NBT 键：碰撞方块坐标（{@link BlockPos#asLong()}）。 */
    private static final String TAG_BLOCK_POS = "BarricadeBlockPos";

    /** 碰撞方块坐标；null = 尚未放置（理论上不会出现在已生成的路障上）。 */
    private BlockPos collisionPos;

    /** 碰撞方块与仇恨是否已释放（幂等闩：die / remove 两条路径只会真正清理一次）。 */
    private boolean released;

    /** 加载后首 tick 是否还要做一次方块校验（存档重载 ⇒ 立刻判一次方块是否还在）。 */
    private boolean needsBlockCheck;

    public BarricadeEntity(EntityType<? extends BarricadeEntity> entityType, Level level) {
        super(entityType, level);
        // 路障是「钉在地上的方块状实体」：不参与物理、不受重力、不会被生物推动
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * 主手侧（{@link LivingEntity} 的抽象方法）。路障没有手、也不持有物品，
     * 这里只是为了让类可实例化；任何读取它的逻辑（装备渲染 / 盾牌判定）对路障都不生效。
     */
    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    // ── 装备槽（{@link LivingEntity} 的三个抽象方法）：路障没有装备 ─────────────────

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
        // 路障不持有任何物品（掉落 / 装备同步逻辑对它全部空转）
    }

    // ── 放置 / NBT ────────────────────────────────────────────────────────────

    /** 记录它占用的碰撞方块坐标（放置时由效果牌调用）。 */
    public void setCollisionPos(BlockPos pos) {
        this.collisionPos = pos;
    }

    public BlockPos getCollisionPos() {
        return this.collisionPos;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.collisionPos != null) {
            tag.putLong(TAG_BLOCK_POS, this.collisionPos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_BLOCK_POS)) {
            this.collisionPos = BlockPos.of(tag.getLong(TAG_BLOCK_POS));
            // 存档重载：首 tick 立刻校验方块是否还在（不在则自毁，见 tick）
            this.needsBlockCheck = true;
        }
    }

    // ── 每 tick：拉仇恨 + 方块校验 ────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        // 没有碰撞方块的路障无法存在（裸 /summon 或旧存档残缺）：
        // 2026-10-21 实测裸召唤的实体会一路下坠到 y=-245 才被清理 ⇒ 直接自毁，避免「自己掉下去」。
        if (this.collisionPos == null) {
            this.discard();
            return;
        }

        // 钉死在本方块中心：任何外力（爆炸 / 活塞 / 位移效果）把实体推离碰撞方块后
        // 下一 tick 立刻拉回，避免「视觉与碰撞脱节」（2026-10-18 实机：苦力怕爆炸把位置
        // 推到 y=105.75，方块仍在 y=96）。
        if (this.collisionPos != null) {
            double cx = this.collisionPos.getX() + 0.5D;
            double cy = this.collisionPos.getY();
            double cz = this.collisionPos.getZ() + 0.5D;
            if (Math.abs(this.getX() - cx) > 1.0E-3D
                    || Math.abs(this.getY() - cy) > 1.0E-3D
                    || Math.abs(this.getZ() - cz) > 1.0E-3D) {
                this.setPos(cx, cy, cz);
                this.setDeltaMovement(0.0D, 0.0D, 0.0D);
            }
        }

        if (this.needsBlockCheck) {
            this.needsBlockCheck = false;
            if (!this.hasCollisionBlock()) {
                this.discard();
                return;
            }
        }
        if (!this.isAlive()) return;

        if (this.tickCount % AGGRO_INTERVAL == 0) {
            this.pullAggro();
        }
        if (this.tickCount % BLOCK_CHECK_INTERVAL == 0 && !this.hasCollisionBlock()) {
            // 碰撞方块被外力移除（/setblock、活塞、别的模组）⇒ 路障自毁
            this.discard();
        }
    }

    /** 碰撞方块是否仍在原位（且仍是本模组的方块）。 */
    private boolean hasCollisionBlock() {
        if (this.collisionPos == null) return false;
        return this.level().getBlockState(this.collisionPos).is(ModBlocks.BARRICADE_COLLISION.get());
    }

    /**
     * 把半径 6 格（球形）内的非精英 / 非 Boss 敌对生物的仇恨转移到自己身上。
     *
     * <p>用 {@code distanceToSqr <= AGGRO_RADIUS^2} 做**球形**判定（用户需求「半径6格（球形）」），
     * 而不是按方块距离；{@code getBoundingBox().inflate(...)} 只是粗筛（AABB 比球大，交给距离判据收口）。
     */
    private void pullAggro() {
        AABB area = this.getBoundingBox().inflate(AGGRO_RADIUS);
        double radiusSqr = AGGRO_RADIUS * AGGRO_RADIUS;
        for (Mob mob : this.level().getEntitiesOfClass(Mob.class, area)) {
            if (!(mob instanceof Enemy)) continue;
            if (!mob.isAlive()) continue;
            if (EliteTargets.isEliteOrBoss(mob)) continue;
            if (mob.distanceToSqr(this) > radiusSqr) continue;
            if (mob.getTarget() != this) {
                mob.setTarget(this);
            }
        }
    }

    // ── 受伤：固定 -1 点耐久 ──────────────────────────────────────────────────

    /**
     * 受伤入口（**服务端唯一判定**）。
     *
     * <p>只接受生物来源（怪物 / 玩家 / 其发射物）⇒ 玩家可以用手或武器拆掉它；
     * 环境伤害（坠落、冻结、挤压、{@code /kill} 的 genericKill）被忽略。
     * 命中一律扣 {@link #DAMAGE_PER_HIT} 点，**与传入的 {@code amount} 无关**。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) return false;
        if (this.isRemoved() || this.isDeadOrDying()) return false;
        if (!(source.getEntity() instanceof LivingEntity)) return false;

        // 每下固定 −1 由本覆写保证（全覆写后不经过原版 hurt ⇒ 不依赖无敌帧冷却，故无需再动
        // invulnerableTime —— 该字段在覆写体里从来没人读，写它只是死代码）
        this.markHurt();
        // 客户端红闪 + 受击音：走 damage 事件包（ClientboundDamageEventPacket →
        // LivingEntity#handleDamageEvent 置 hurtTime = hurtDuration = 10）；受击音也由客户端侧播，
        // 故此处不再重复 playSound（否则一次受击会播两遍 WOOD_HIT）
        this.level().broadcastDamageEvent(this, source);

        float newHealth = this.getHealth() - DAMAGE_PER_HIT;
        if (newHealth <= 0.0F) {
            this.setHealth(0.0F);
            this.die(source);
        } else {
            this.setHealth(newHealth);
        }
        return true;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOOD_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOOD_BREAK;
    }

    // ── 死亡 / 移除：清场 + 删方块 ────────────────────────────────────────────

    @Override
    public boolean isPushable() {
        return false;
    }

    /** 水流 / 岩浆流也不得推移（{@code Entity} 默认返回 true）。 */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /**
     * 钉在地上：不吃爆炸 / 活塞 / 水流等一切外力推移（2026-10-18 实机：苦力怕爆炸把路障从
     * y=96 推到 y=105.75，而碰撞方块还在原地 ⇒ 视觉与碰撞脱节）。实体的位移只允许由
     * 「放置」与「方块校验自毁」两条路径决定。
     */
    @Override
    public void push(double x, double y, double z) {
        // 故意空实现
    }


    /** 路障不是战利品也不是经验来源（用户需求未要求掉落）。 */
    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide) {
            this.release(false);
        }
    }

    /**
     * 移除入口。
     *
     * <p>⚠️ 只对 {@code KILLED} / {@code DISCARDED} 做清理 —— {@code UNLOADED_TO_CHUNK} /
     * {@code CHANGED_DIMENSION} 等「搬运式移除」若也删方块，会导致**区块卸载一次路障就消失**
     * （实体与方块是持久化的两半，卸载后必须还原样）。
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide
                && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            this.release(true);
        }
        super.remove(reason);
    }

    /**
     * 释放：解除范围内怪物对自己的仇恨、删掉碰撞方块、播破碎音 + 少量暴击粒子。
     *
     * @param playSoundFromHere true = 由这里播死亡音（从 {@link #remove} 进来时没有
     *                          {@code handleEntityEvent(3)} 的死亡音，需要自己补）；false = 由
     *                          {@code super.die} 广播的实体事件播放（避免同一死亡播两次）
     */
    private void release(boolean playSoundFromHere) {
        if (this.released) return;
        this.released = true;

        // ① 解除仇恨：半径 8 内以自己为目标的怪全部清空（防止怪物对着空气继续打）
        AABB area = this.getBoundingBox().inflate(RELEASE_RADIUS);
        for (Mob mob : this.level().getEntitiesOfClass(Mob.class, area)) {
            if (mob.getTarget() == this) {
                mob.setTarget(null);
            }
        }

        // ② 删掉碰撞方块（走到这里代表路障真的没了）
        if (this.collisionPos != null) {
            if (this.level().getBlockState(this.collisionPos).is(ModBlocks.BARRICADE_COLLISION.get())) {
                this.level().removeBlock(this.collisionPos, false);
            }
        }

        // ③ 破碎反馈：音效 + 少量暴击粒子
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    this.getX(), this.getY() + 0.5D, this.getZ(),
                    10, 0.3D, 0.3D, 0.3D, 0.05D);
        }
        if (playSoundFromHere) {
            this.level().playSound(null, this.getX(), this.getY() + 0.75D, this.getZ(),
                    SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }
}
