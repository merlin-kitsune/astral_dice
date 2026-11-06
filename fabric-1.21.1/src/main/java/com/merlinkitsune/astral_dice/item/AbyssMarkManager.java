package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.AbyssMarkEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.TickEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.MobEffectEvent;
import com.merlinkitsune.astral_dice.platform.util.Effects;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 「渊蚀印记」管理器（fabric-1.21.1 线；机制照抄本线 {@link MarkManager} 的「每层独立计时 + 到期下一
 * tick 降 1 层」）。
 *
 * <p>数据只存在目标身上的 {@code abyss_mark} 效果里：amplifier = 层数 - 1。施加 = 层数 +1 并**重置**
 * 为完整 2:00；自然到期 = 层数 -1（>1 层时重新施加低一层、同样 2:00）。
 *
 * <h3>与 P0（neoforge-1.21.1）的两处平台差异</h3>
 * <ol>
 *   <li><b>施印冷却的存储</b>：P0 写 {@code wearer.getPersistentData()}（Forge/NeoForge 补丁，fabric
 *       没有这个方法）⇒ 本线改用 {@code component/ModAttachments} 的
 *       {@code ABYSS_MARK_LAST_APPLY} 持久附件（LONG，服务端权威、不 .sync()），语义等价：跨死亡 /
 *       重登保留，默认 0 = 从未施印。</li>
 *   <li><b>效果实例的 Holder 化</b>：1.21.1 的 {@code getEffect / addEffect / MobEffectInstance} 全收
 *       {@code Holder<MobEffect>} ⇒ 统一经 {@link Effects#holder} 转换（P0 该线同样如此，只是写法在
 *       neoforge 侧另有一套）。</li>
 *   <li><b>事件类型</b>：P0 用 {@code ServerTickEvent.Post}；本线 tick shim 是 1.20.1 单相风格
 *       （{@link TickEvent.ServerTickEvent} 带 phase 字段）⇒ 首行 {@code if (event.phase !=
 *       TickEvent.Phase.END) return;}（与 {@link MarkManager#onServerTick} 同款）。</li>
 * </ol>
 *
 * <p>为什么降层不在 {@code MobEffectEvent.Expired} 里直接写：原版在 {@code iterator.remove()}
 * 之前发 Expired，此刻旧的高层实例仍在 activeEffects —— 直接 addEffect 低层会被
 * {@code MobEffectInstance#update} 忽略（只进 hiddenEffect）随后随旧实例一起消失，
 * 表现为「印记整层消失」。故统一登记到下一 tick 的服务器 tick 末端补写。
 */
public final class AbyssMarkManager {

    /** 施加上限（与 {@link AbyssMarkEffect#MAX_STACKS} 同源）。 */
    public static final int MAX_STACKS = AbyssMarkEffect.MAX_STACKS;

    /** 每层持续时间：2:00。 */
    public static final int DURATION_TICKS = AbyssMarkEffect.DURATION_TICKS;

    /** 被动触发间隔：10 秒（同一佩戴者两次施印之间至少 10 秒）。 */
    public static final int APPLY_COOLDOWN_TICKS = 200;

    private record PendingDecay(LivingEntity entity, int amplifier) {
    }

    private static final List<PendingDecay> PENDING_DECAY = new ArrayList<>();

    private AbyssMarkManager() {
    }

    /** 施加 1 层（已满层则只重置时长）。返回施加后的层数。 */
    public static int apply(LivingEntity target) {
        MobEffectInstance existing = target.getEffect(Effects.holder(ModEffects.ABYSS_MARK.get()));
        int amplifier = existing != null ? Math.min(existing.getAmplifier() + 1, MAX_STACKS - 1) : 0;
        target.addEffect(new MobEffectInstance(Effects.holder(ModEffects.ABYSS_MARK.get()),
                DURATION_TICKS, amplifier, false, true));
        return amplifier + 1;
    }

    /** 当前层数（无印记返回 0）。 */
    public static int getStacks(LivingEntity target) {
        MobEffectInstance existing = target.getEffect(Effects.holder(ModEffects.ABYSS_MARK.get()));
        return existing != null ? Math.min(existing.getAmplifier() + 1, MAX_STACKS) : 0;
    }

    /** 每层 +1 点的额外伤害（魔渊幼体 / 魔渊触须共用）。 */
    public static int bonusDamage(LivingEntity target) {
        return Math.min(getStacks(target), MAX_STACKS);
    }

    /** 佩戴者的 10 秒施印冷却是否就绪。 */
    public static boolean readyToApply(Player wearer) {
        long last = ModAttachments.getAbyssMarkLastApplyTick(wearer);
        return last <= 0L || wearer.level().getGameTime() - last >= APPLY_COOLDOWN_TICKS;
    }

    /** 记一次施印（写佩戴者持久附件，跨死亡 / 重登保留）。 */
    public static void markApplied(Player wearer) {
        ModAttachments.setAbyssMarkLastApplyTick(wearer, wearer.level().getGameTime());
    }

    /**
     * 印记自然到期：层数 > 1 时登记「下一 tick 降一层」。
     *
     * <p>（{@code MobEffectEvent.Expired} 在本线由 {@code platform/PuzzlesBridges} 从
     * Puzzles 的 {@code MobEffectExpiredCallback} 派发，服务端才触发。）
     */
    @SubscribeEvent
    public static void onMarkExpired(MobEffectEvent.Expired event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null
                || effect.getEffect().value() != ModEffects.ABYSS_MARK.get()) {
            return;
        }
        if (effect.getAmplifier() > 0) {
            PENDING_DECAY.add(new PendingDecay(entity, effect.getAmplifier() - 1));
        }
    }

    /** 下一 tick 补写「印记减 1 层」（见类注释：原版 Expired 早于 iterator.remove）。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (PENDING_DECAY.isEmpty()) return;
        List<PendingDecay> pending = new ArrayList<>(PENDING_DECAY);
        PENDING_DECAY.clear();
        for (PendingDecay p : pending) {
            LivingEntity entity = p.entity();
            if (entity == null || entity.isRemoved() || !entity.isAlive()) continue;
            if (entity.level().isClientSide()) continue;
            entity.addEffect(new MobEffectInstance(Effects.holder(ModEffects.ABYSS_MARK.get()),
                    DURATION_TICKS, p.amplifier(), false, true));
        }
    }

    /** 调试/取证辅助：给目标施满 20 层（供探针使用，不在玩法路径上）。 */
    public static void fillStacks(LivingEntity target) {
        target.addEffect(new MobEffectInstance(Effects.holder(ModEffects.ABYSS_MARK.get()),
                DURATION_TICKS, MAX_STACKS - 1, false, true));
    }
}
