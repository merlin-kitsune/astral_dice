package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.chip.RailgunChipItem;
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
 * 电磁炮雷击的延迟队列(精确 1 秒后触发)。
 *
 * <p><b>为什么不能用原版 {@code MinecraftServer#tell(new TickTask(tickCount + 20, ...))}:</b>
 * {@code MinecraftServer} 继承 {@code ReentrantBlockableEventLoop<TickTask>},其
 * {@code shouldRun(TickTask)} = {@code runnable.getTick() + 3 < tickCount || haveTime()},
 * 而 {@code haveTime()} 在一个 tick 的时间预算内通常为真;并且 {@code tell(...)} 内部会
 * {@code managedBlock(runAllTasks)} 立即排空队列。结果是"传入未来 tick"的任务会在**当 tick
 * 立即执行**,1 秒延迟塌缩为 0(已对 1.20.1 原版源码逐行核实)。
 *
 * <p>因此这里把待触发雷击挂在**服务端 tick 边界**上,按 gameTime 判到期,保证真实延迟 1 秒:
 * 仅服务端、纯内存状态(延迟仅 1 秒,无需持久化/同步)。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class RailgunStrikeScheduler {

    /**
     * 待触发雷击:触发瞬间优先跟随仍存活的原目标,否则回退到攻击时的位置快照。
     *
     * <p>伤害值**可变**:登记发生在伤害结算前段(骰战尚未算出最终伤害),先以攻击的即时伤害
     * 兜底,骰战结算完成后由 {@link #setDamage} 回填,故不能用 record。
     */
    public static final class Pending {
        private final ServerLevel level;
        private final LivingEntity target;
        private final Vec3 fallbackCenter;
        private final ServerPlayer cause;
        private final long fireAt;
        /** 本次雷击伤害(降雷时按此值 {@code LightningBolt#setDamage}) */
        private float damage;

        private Pending(ServerLevel level, LivingEntity target, Vec3 fallbackCenter,
                        ServerPlayer cause, long fireAt, float damage) {
            this.level = level;
            this.target = target;
            this.fallbackCenter = fallbackCenter;
            this.cause = cause;
            this.fireAt = fireAt;
            this.damage = Math.max(0.0F, damage);
        }

        /** 回填/覆盖本次雷击伤害(负数按 0 处理) */
        public void setDamage(float damage) {
            this.damage = Math.max(0.0F, damage);
        }

        private ServerLevel level() { return level; }

        private LivingEntity target() { return target; }

        private Vec3 fallbackCenter() { return fallbackCenter; }

        private ServerPlayer cause() { return cause; }

        private long fireAt() { return fireAt; }

        private float damage() { return damage; }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RailgunStrikeScheduler.class);

    private static final List<Pending> PENDING = new ArrayList<>();

    /** 超龄保护(tick):维度卸载等极端情形下丢弃滞留条目,避免无限堆积 */
    private static final long MAX_AGE_TICKS = 400L;

    private RailgunStrikeScheduler() {
    }

    /**
     * 登记一次延迟雷击。
     *
     * @param level        触发的服务端维度
     * @param target       原目标(存活则跟随其当前位置;可为 null)
     * @param fallbackCenter 目标不可用时的位置快照
     * @param cause        雷击来源玩家(可为 null)
     * @param delayTicks   延迟 tick 数(1 秒 = 20)
     * @param damage       初始雷击伤害(可随后经 {@link Pending#setDamage} 回填)
     * @return 本次登记的句柄(供回填最终伤害);{@code level} 为空时返回 {@code null}
     */
    public static Pending schedule(ServerLevel level, LivingEntity target, Vec3 fallbackCenter,
                               ServerPlayer cause, int delayTicks, float damage) {
        if (level == null) return null;
        Pending pending = new Pending(level, target, fallbackCenter, cause,
                level.getGameTime() + Math.max(0, delayTicks), damage);
        PENDING.add(pending);
        return pending;
    }

    /** 最早一个待触发雷击的剩余 tick 数;-1 表示该维度当前没有待触发雷击(测试/调试用) */
    public static long pendingRemainingTicks(ServerLevel level) {
        if (level == null || PENDING.isEmpty()) return -1L;
        long best = Long.MAX_VALUE;
        for (Pending p : PENDING) {
            if (p.level() == level) {
                best = Math.min(best, p.fireAt() - level.getGameTime());
            }
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
        // 先把到期条目整体摘出队列,再逐个处理:处理过程会触发伤害/击杀等联动,
        // 有可能回调 schedule 往队列里追加新条目 —— 边遍历边改列表会抛 ConcurrentModificationException。
        List<Pending> due = new ArrayList<>();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            long now = p.level().getGameTime();
            if (now - p.fireAt() > MAX_AGE_TICKS) {
                it.remove();
                continue;
            }
            if (now < p.fireAt()) continue;
            it.remove();
            due.add(p);
        }
        for (Pending p : due) {
            // 单条失败不牵连其它待触发雷击,也不让异常冒泡到服务端 tick 循环
            // (旧实现里异常会从这里抛出,表现为"继续触发雷击即报错")
            try {
                LivingEntity target = p.target();
                Vec3 center = (target != null && target.isAlive() && target.level() == p.level())
                        ? target.position() : p.fallbackCenter();
                if (center == null) continue;
                RailgunChipItem.executeStrike(p.level(), center, p.cause(), p.damage());
            } catch (Exception ex) {
                LOGGER.warn("[Astral Dice] 电磁炮雷击触发失败,已跳过本次", ex);
            }
        }
    }
}
