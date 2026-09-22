package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 怪力侦探立牌(sherry)主动「怪力投掷」的**投掷飞行**执行器(1.20.1 Forge 移植版)。
 *
 * <p><b>口径(2026-09-21 用户裁决)</b>:目标怪物以**抛物线**方式从原始位置被投掷到落点;
 * 落点必须在**玩家面前 2 格内**(天然避免穿墙);**落地之后**才结算受伤与标记。
 *
 * <p><b>实现方式 = 逐 tick 插值(用户裁决,非真物理抛射)</b>:服务端每 tick 直接改写实体位置
 * (水平线性插值 + 竖直抛物线抬升),客户端按位置同步包平滑呈现。之所以不用
 * {@code setDeltaMovement} 交物理引擎:后者会撞墙卡停、被地形/实体挤飞、并在落地时触发原版
 * **坠落伤害**(不可接受 —— 伤害必须由本立牌按固定点数发放)。
 *
 * <p><b>飞行期间</b>:目标 {@code setNoAi(true)}(不行动、不索敌、不攻击)、每 tick 归零速度与
 * {@code fallDistance};落点那一 tick 恢复原 AI 状态并结算。
 *
 * <p><b>维度</b>:作业记录 {@code ResourceKey<Level>},按维度取 {@link ServerLevel} ——
 * 目标只可能与施法者同维度(收集时已限定),但不可假设是主世界。
 *
 * <p><b>状态不持久化</b>:投掷是 0.5 秒级的瞬态过程,作业表是**服务端内存表**(服务器重启/玩家
 * 重登都不会留下"飞到一半的怪"—— 那一 tick 未结算则整次投掷作废,目标留在原地)。
 *
 * <p><b>平台差异(与 1.21.1 逐字等价)</b>:本线用 {@code TickEvent.ServerTickEvent} + {@code phase == END}
 * 早退(1.21.1 为 {@code ServerTickEvent.Post});调度器写法照 {@code event/LivingPageFlightScheduler}。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class SherryThrowManager {

    /** 单次飞行时长(tick) —— 0.5 秒,足以看清抛物线又不拖沓 */
    public static final int FLIGHT_TICKS = 10;
    /** 抛物线相对起点/落点连线的最大抬升高度(格) */
    public static final double ARC_HEIGHT = 1.6D;

    /**
     * 一次投掷作业。
     *
     * @param entityId    被投掷实体
     * @param dimension   所在维度
     * @param from        起点(施放瞬间位置)
     * @param to          落点(玩家面前 2 格)
     * @param playerId    施法者 UUID
     * @param bonusDamage 落地额外伤害(推理时间满 5 层 ⇒ 5,否则 0)
     * @param hadNoAi     起点时该实体是否已是 noAi(落地据此恢复)
     * @param elapsed     已飞行 tick
     */
    private record Job(UUID entityId, ResourceKey<Level> dimension, Vec3 from, Vec3 to,
                       UUID playerId, int bonusDamage, boolean hadNoAi, int elapsed) {
        Job advance() {
            return new Job(entityId, dimension, from, to, playerId, bonusDamage, hadNoAi, elapsed + 1);
        }
    }

    private static final List<Job> JOBS = new ArrayList<>();

    private SherryThrowManager() {
    }

    /**
     * 登记一次投掷。调用方(立牌主动)负责收集目标、算出落点与额外伤害。
     *
     * @param entity      被投掷的敌对目标
     * @param destination 落点坐标(已由调用方夹取到「玩家面前 2 格内」)
     * @param caster      施法者
     * @param bonusDamage 落地时的额外伤害
     */
    public static void schedule(LivingEntity entity, Vec3 destination, Player caster, int bonusDamage) {
        if (entity == null || caster == null) return;
        if (entity.level().isClientSide()) return;
        if (entity.isRemoved() || !entity.isAlive()) return;
        boolean hadNoAi = entity instanceof Mob mob && mob.isNoAi();
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        entity.setDeltaMovement(Vec3.ZERO);
        JOBS.add(new Job(entity.getUUID(), entity.level().dimension(), entity.position(),
                destination, caster.getUUID(), bonusDamage, hadNoAi, 0));
    }

    /** 推进全部作业;到达 {@link #FLIGHT_TICKS} 的那一 tick 落地并结算。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (JOBS.isEmpty()) return;
        List<Job> snapshot = new ArrayList<>(JOBS);
        JOBS.clear();
        for (Job job : snapshot) {
            ServerLevel level = event.getServer().getLevel(job.dimension());
            if (level == null) continue;
            if (!(level.getEntity(job.entityId()) instanceof LivingEntity living)
                    || living.isRemoved() || !living.isAlive()) {
                continue;   // 目标已消失 ⇒ 本次投掷作废(不结算)
            }
            if (job.elapsed() >= FLIGHT_TICKS) {
                // ---- 落地 ----
                living.teleportTo(job.to().x, job.to().y, job.to().z);
                living.setDeltaMovement(Vec3.ZERO);
                living.fallDistance = 0.0F;
                if (living instanceof Mob mob && !job.hadNoAi()) {
                    mob.setNoAi(false);
                }
                settle(living, level.getPlayerByUUID(job.playerId()), job.bonusDamage());
                continue;
            }
            // ---- 飞行中:水平线性插值 + 竖直抛物线抬升 ----
            double t = (double) job.elapsed() / FLIGHT_TICKS;
            Vec3 from = job.from();
            Vec3 to = job.to();
            double x = from.x + (to.x - from.x) * t;
            double z = from.z + (to.z - from.z) * t;
            double y = from.y + (to.y - from.y) * t + ARC_HEIGHT * 4.0D * t * (1.0D - t);
            living.teleportTo(x, y, z);
            living.setDeltaMovement(Vec3.ZERO);
            living.fallDistance = 0.0F;   // 逐 tick 传送会累加坠落距离,归零以免落地吃原版摔落伤害
            JOBS.add(job.advance());
        }
    }

    /** 落地结算:固定伤害(2 点 + 额外) + 1 层「标记」。 */
    private static void settle(LivingEntity target, Player caster, int bonusDamage) {
        float damage = 2.0F + bonusDamage;
        if (caster != null && !caster.level().isClientSide()) {
            target.hurt(caster.damageSources().playerAttack(caster), damage);
        } else {
            target.hurt(target.damageSources().generic(), damage);
        }
        if (target.isAlive()) {
            com.merlinkitsune.astral_dice.item.MarkManager.apply(target);
        }
    }

    /** 清空全部待处理作业(测试脚手架/关服收敛用) */
    public static void clearAll() {
        JOBS.clear();
    }
}
