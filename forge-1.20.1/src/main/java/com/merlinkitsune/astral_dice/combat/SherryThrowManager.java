// SHERRY_AIM_PATCH 2026-09-22（怪力侦探：结算 / 准星落点 / 隔墙过滤 / 落地冻结）
// 2026-09-24：落地结算的伤害类型由 card_spell 改为 skill_damage（技能类伤害与法伤分离）
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
     * 落地后「保险冻结」时长(tick) —— 用户 2026-09-22 裁决:被投出的怪 **2 秒**内不索敌、不攻击。
     *
     * <p>为什么需要:落点就在玩家面前 1–6 格,若落地即恢复 AI,怪会立刻反手攻击 —— 实测能把玩家打死。
     * 因此落地**不立即**恢复 AI,而是登记一次延迟解冻(见 {@link #RELEASES});解冻时同时清掉索敌目标。
     */
    public static final int LANDING_FREEZE_TICKS = 40;

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

    /** 待解冻的目标(落地时登记、到点恢复 AI) —— 保险冻结的收尾队列。 */
    private record Release(UUID entityId, ResourceKey<Level> dimension, long releaseTick) {
    }

    private static final List<Release> RELEASES = new ArrayList<>();

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
        releaseDue(event.getServer());
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
                // 原本就 noAi 的目标保持原状;否则**延迟** LANDING_FREEZE_TICKS 再恢复 AI(落地保险冻结)
                if (living instanceof Mob mob && !job.hadNoAi()) {
                    RELEASES.add(new Release(living.getUUID(), job.dimension(),
                            level.getGameTime() + LANDING_FREEZE_TICKS));
                }
                settle(living, level, level.getPlayerByUUID(job.playerId()), job.bonusDamage());
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

    /**
     * 落地结算:**技能类伤害**(2 点 + 额外) + 1 层「标记」,并显示伤害数字。
     *
     * <p>⚠️ **必须走 {@code astral_dice:skill_damage}**(专门的「技能类伤害」类型),
     * 并以 {@link DiceCombatEvents#aoeProcessing} 包裹。两条排除项都是踩过的坑:
     * <ul>
     *   <li>不能用 {@code damageSources().playerAttack(...)} —— 会被当作**玩家近战**,
     *       意外吃骰伤、并触发「战斗伤害类」筹码(用户 2026-09-22 裁决);</li>
     *   <li>也不能用 {@code astral_dice:card_spell} —— 它是 {@code SpellDamageRegistry}
     *       法伤白名单的第 4 条 matcher ⇒ 立牌的固定点数会被忍术飞镖 / 贯穿之铳 / 紫晶骰子 /
     *       标记喷罐 / 魔法箭袋 / 效果牌加成层层放大,还会触发电击手套的范围波及
     *       (**用户 2026-09-24 裁决「为技能类伤害创建单独的伤害标签,避免与法伤混用」**)。</li>
     * </ul>
     * 该类型同样登记于 {@code bypasses_armor}(无视护甲值与盔甲韧性),但不在法伤白名单内
     * ⇒ **只结算自身点数**。伤害数字沿用「活体书页」同款配色(视觉不变)。
     */
    private static void settle(LivingEntity target, ServerLevel level, Player caster, int bonusDamage) {
        float damage = 2.0F + bonusDamage;
        if (caster != null && !caster.level().isClientSide()) {
            DiceCombatEvents.aoeProcessing = true;
            try {
                target.hurt(com.merlinkitsune.astral_dice.damage.ModDamageTypes.skillDamage(level, caster), damage);
            } finally {
                DiceCombatEvents.aoeProcessing = false;
            }
            sendSkillDamageNumber(target, (int) damage);
        } else {
            target.hurt(target.damageSources().generic(), damage);
        }
        if (target.isAlive()) {
            com.merlinkitsune.astral_dice.item.MarkManager.apply(target);
        }
    }

    /**
     * 发技能伤害数字(**配色沿用「活体书页」同款**,视觉维持不变) ——
     * 1.21.1/26.1.2 走 {@code DamageNumberPayload},1.20.1 走 {@code ModNetwork.DamageNumberMessage}。
     */
    private static void sendSkillDamageNumber(LivingEntity target, int amount) {
        com.merlinkitsune.astral_dice.network.ModNetwork.DamageNumberMessage.send(target, amount, LivingPageImpact.SPELL_DAMAGE_COLOR);
    }

    /** 到点解冻(落地保险的收尾):恢复 AI 并**清掉索敌目标**,避免解冻瞬间立刻追杀玩家。 */
    private static void releaseDue(net.minecraft.server.MinecraftServer server) {
        if (RELEASES.isEmpty() || server == null) return;
        List<Release> pending = new ArrayList<>(RELEASES);
        RELEASES.clear();
        for (Release r : pending) {
            ServerLevel level = server.getLevel(r.dimension());
            if (level == null) continue;
            if (level.getGameTime() < r.releaseTick()) {
                RELEASES.add(r);          // 未到点:留在队列里
                continue;
            }
            if (level.getEntity(r.entityId()) instanceof Mob mob && !mob.isRemoved() && mob.isAlive()) {
                mob.setNoAi(false);
                mob.setTarget(null);
            }
        }
    }

    /** 清空全部待处理作业与待解冻记录(测试脚手架/关服收敛用) */
    public static void clearAll() {
        JOBS.clear();
        RELEASES.clear();
    }
}
