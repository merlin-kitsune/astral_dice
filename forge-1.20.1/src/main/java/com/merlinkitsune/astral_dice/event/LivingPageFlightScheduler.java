package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.LivingPageImpact;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 「活体书页」飞行打击调度器(2026-09-25 重写)。
 *
 * <p><b>被测语义(用户裁决)</b>:书页使用后从玩家飞向被选定的敌对目标,
 * ① 速度 = **箭矢的 4/5**({@link #SPEED_BLOCKS_PER_TICK});② 飞行时长由目标距离推导
 * ({@code ceil(距离 / 2.4)} tick);③ 每 tick 朝目标中心重新取向量(**跟踪修正**,不是直线弹道);
 * ④ 抵达即命中(最后一步吸附到目标中心)⇒ **必定命中**;⑤ 全程**不做任何方块/碰撞判定** ⇒ 可穿透方块;
 * ⑥ 视觉 = 高速飞行的**连续**发光粒子带({@code END_ROD},每 tick 沿位移线段插值投放;2026-09-19
 * 按要求加密并移除原先的深绿色 {@code GLOW} 粒子,见 {@link #emitTrail});⑦ 命中结算见
 * {@link LivingPageImpact}(伤害登记为法伤 + 施加 1 层标记 + 连续出牌补记)。
 *
 * <p><b>为什么挂在服务端 tick 边界上(与 {@link RailgunStrikeScheduler} 同一范式)</b>:
 * {@code MinecraftServer#tell(...)} 的延迟任务会在当 tick 被立即排空,真实延迟会塌缩为 0;
 * 且飞行是**纯内存瞬态状态**(最长 1 秒级),无需持久化/同步。
 *
 * <p><b>1.20.1 平台差异</b>:Forge 的 {@code TickEvent.ServerTickEvent} **每 tick 派发 START/END 两次**,
 * 故必须判 {@code phase == END}(与 {@code RailgunStrikeScheduler} 同写法);其余逻辑与 1.21.1 逐字等价。
 *
 * <p><b>失效即丢弃(fizzle,不给任何结算)</b>:目标死亡/被移除/不在同一维度、施法者死亡/被移除、
 * 超龄(维度卸载等极端情形,{@link #MAX_AGE_TICKS})。卡牌此前已在确认目标时消耗,故表现为「法术散失」。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class LivingPageFlightScheduler {

    /**
     * 飞行速度(格/tick)= **箭矢的 4/5**。
     * <p>依据:原版满蓄力弓 {@code BowItem#shoot} 传入 {@code f * 3.0F},而 {@code getPowerForTime}
     * 的上限为 1.0 ⇒ 箭矢速度 = 3.0 格/tick;4/5 × 3.0 = **2.4** 格/tick。
     * 距离只决定**飞行时长**({@code ceil(距离 / 2.4)}),每 tick 步长恒定。
     */
    public static final double SPEED_BLOCKS_PER_TICK = 2.4D;

    /** 命中吸附阈值(格):与目标中心的距离小于该值即视为抵达(保证「必定命中」) */
    private static final double SNAP_DISTANCE = 0.4D;

    /** 超龄保护(tick):维度卸载/区块异常等极端情形下丢弃滞留条目,避免无限堆积 */
    private static final long MAX_AGE_TICKS = 400L;

    /** 轨迹粒子:核心发光粒子(END_ROD) —— 每 tick 沿线**插值**投放,见 {@link #emitTrail} */
    private static final int TRAIL_PARTICLES_PER_STEP = 2;
    /**
     * 轨迹插值间距(格)。速度 2.4 格/tick,若只在每 tick 的采样点投放粒子,相邻两团之间会空出
     * 2.4 格 ⇒ 肉眼可见「分段」;按本间距把本 tick 的位移线段切段逐点投放即可连成光带。
     * 0.45 ⇒ 每 tick 至多 6 个投放点(约 12 个粒子),密度足够且开销可忽略。
     */
    private static final double TRAIL_SPACING = 0.45D;
    /** 命中爆发粒子 */
    private static final int IMPACT_BURST_PARTICLES = 20;

    private static final Logger LOGGER = LoggerFactory.getLogger(LivingPageFlightScheduler.class);

    private static final List<Pending> PENDING = new ArrayList<>();

    private static final class Pending {
        private final ServerLevel level;
        private final ServerPlayer caster;
        private final LivingEntity target;
        private final long startTick;
        /** 由登记时距离推导的飞行 tick 数(下界 1;亦作为「最迟抵达」的硬上界) */
        private final int flightTicks;
        private Vec3 pos;
        private int elapsed;

        private Pending(ServerLevel level, ServerPlayer caster, LivingEntity target, Vec3 pos, int flightTicks) {
            this.level = level;
            this.caster = caster;
            this.target = target;
            this.pos = pos;
            this.flightTicks = flightTicks;
            this.startTick = level.getGameTime();
        }
    }

    private LivingPageFlightScheduler() {
    }

    /**
     * 登记一次飞行打击。
     *
     * @param level  施法者所在的服务端维度
     * @param caster 施法者(命中归属;飞行中死亡/登出 ⇒ 整次飞行作废)
     * @param target 确认的敌对目标
     * @return 是否成功登记(false = 入参不可用,调用方无需补偿——卡牌仍由选择器路径消耗)
     */
    public static boolean launch(ServerLevel level, ServerPlayer caster, LivingEntity target) {
        if (level == null || caster == null || target == null) return false;
        Vec3 start = caster.getEyePosition();
        double distance = start.distanceTo(target.getBoundingBox().getCenter());
        int flightTicks = (int) Math.max(1L, (long) Math.ceil(distance / SPEED_BLOCKS_PER_TICK));
        PENDING.add(new Pending(level, caster, target, start, flightTicks));
        return true;
    }

    /** 该维度当前在飞的活体书页数量(测试/调试只读入口) */
    public static int pendingCount(ServerLevel level) {
        if (level == null) return 0;
        int count = 0;
        for (Pending p : PENDING) {
            if (p.level == level) count++;
        }
        return count;
    }

    /** 最早抵达的一个待结算书页的剩余 tick 数;-1 = 该维度当前没有在飞的书页(测试/调试只读入口) */
    public static long pendingRemainingTicks(ServerLevel level) {
        if (level == null) return -1L;
        long best = Long.MAX_VALUE;
        for (Pending p : PENDING) {
            if (p.level != level) continue;
            best = Math.min(best, Math.max(0L, (long) p.flightTicks - p.elapsed));
        }
        return best == Long.MAX_VALUE ? -1L : best;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (PENDING.isEmpty()) return;
        process(event.getServer());
    }

    private static void process(MinecraftServer server) {
        if (server == null || PENDING.isEmpty()) return;
        // 先把在飞条目整体摘出队列,再逐个推进:推进过程会触发伤害/击杀等联动,有可能回调 launch
        // 往队列里追加新条目 —— 边遍历边改列表会抛 ConcurrentModificationException。
        List<Pending> due = new ArrayList<>();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            due.add(it.next());
            it.remove();
        }
        for (Pending p : due) {
            // 单条失败不牵连其它飞行,也不让异常冒泡到服务端 tick 循环
            try {
                advance(p);
            } catch (Exception ex) {
                LOGGER.warn("[Astral Dice] 活体书页飞行结算失败,已丢弃本次飞行", ex);
            }
        }
    }

    private static void advance(Pending p) {
        if (!stillValid(p)) return;                    // fizzle:目标/施法者/维度失效
        if (p.level.getGameTime() - p.startTick > MAX_AGE_TICKS) return;   // fizzle:超龄
        Vec3 dest = p.target.getBoundingBox().getCenter();
        Vec3 delta = dest.subtract(p.pos);
        double remaining = delta.length();
        double step = Math.min(SPEED_BLOCKS_PER_TICK, remaining);
        Vec3 prev = p.pos;
        if (remaining > 1.0E-4D) {
            p.pos = p.pos.add(delta.normalize().scale(step));
        }
        p.elapsed++;
        // 轨迹粒子(服务端下发即可,不需要客户端代码);沿线插值投放,见 emitTrail 的说明
        emitTrail(p, prev, p.pos);
        // 抵达判定:进入吸附阈值,或已达「由距离推导的飞行 tick 数」硬上界 ⇒ 吸附到目标中心并命中
        if (p.pos.distanceToSqr(dest) <= SNAP_DISTANCE * SNAP_DISTANCE || p.elapsed >= p.flightTicks) {
            p.pos = dest;
            impact(p, dest);
            return;                                    // 已结算 ⇒ 不再放回队列
        }
        PENDING.add(p);                                // 仍在飞行
    }

    /**
     * 沿本 tick 的位移线段**插值投放**轨迹粒子(2026-09-19 用户要求「增加飞行粒子密度和流畅度,
     * 避免视觉上有可视分段」)。
     *
     * <p>旧写法每个服务端 tick 只在**采样点**放一次粒子,而速度是 {@value #SPEED_BLOCKS_PER_TICK} 格/tick
     * ⇒ 相邻两团之间空出 2.4 格,肉眼能看到明显「分段」。现按 {@link #TRAIL_SPACING} 把线段切成
     * 若干段逐点投放(段间无空档);粒子速度参数仍为 0(无随机漂移),整体呈连续高速光带。
     *
     * <p>⚠️ 只发 {@code END_ROD}(白/发光),**不再发 {@code GLOW}** —— 用户报告「使用活体书页出现了
     * 未预期的深绿色粒子」,而本调度器一共只发过三种粒子(END_ROD / GLOW / FLASH),其中 GLOW 是
     * 发光鱿鱼墨那种**深青绿**小点 ⇒ 即该报告的来源,2026-09-19 一并移除。
     */
    private static void emitTrail(Pending p, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(length / TRAIL_SPACING));
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.lerp(to, (double) i / (double) steps);
            p.level.sendParticles(ParticleTypes.END_ROD,
                    at.x, at.y, at.z, TRAIL_PARTICLES_PER_STEP, 0.03D, 0.03D, 0.03D, 0.0D);
        }
    }

    /** 飞行是否仍可继续(目标与施法者都在、且都在同一维度) */
    private static boolean stillValid(Pending p) {
        if (p.target == null || p.target.isRemoved() || !p.target.isAlive()) return false;
        if (p.target.level() != p.level) return false;
        if (p.caster == null || p.caster.isRemoved() || !p.caster.isAlive()) return false;
        return p.caster.level() == p.level;
    }

    private static void impact(Pending p, Vec3 center) {
        // 命中爆发粒子(视觉收束:发光粒子团在目标身上炸开)
        p.level.sendParticles(ParticleTypes.END_ROD,
                center.x, center.y, center.z, IMPACT_BURST_PARTICLES, 0.25D, 0.25D, 0.25D, 0.02D);
        p.level.sendParticles(ParticleTypes.FLASH,
                center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        LivingPageImpact.resolve(p.level, p.caster, p.target);
    }
}
