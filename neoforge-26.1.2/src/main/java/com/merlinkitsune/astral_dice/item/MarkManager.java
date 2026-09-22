package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * "标记"管理器(目标身上的效果,与具体饰品/卡牌解耦)。
 * 数据存储于目标身上的 MARKED 效果(amplifier = 层数-1),本类统一提供施加(层数递增/上限)与读取。
 * 施加来源:普通瞄具/鹰眼瞄具攻击、标靶定时、活体书页远程伤害等。
 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class MarkManager {
    // 标记持续时间(tick):默认 60 秒
    public static final int MARK_DURATION_TICKS = 1200;

    /** 待补写的「标记减 1 层」:见 {@link #onMarkExpired} 注释,统一延到下一 tick 服务端 tick 末尾处理 */
    private record PendingDecay(LivingEntity entity, int amplifier) {
    }

    private static final List<PendingDecay> PENDING_DECAY = new ArrayList<>();

    private MarkManager() {
    }

    // 施加一层标记(层数+1,上限 MAX_MARKER),返回施加后的层数
    public static int apply(LivingEntity target) {
        return apply(target, MARK_DURATION_TICKS);
    }

    // 施加一层标记,指定持续时间;同时使目标获得"高亮"。
    // 发光与标记保持同一寿命(时长一致),不再使用无限时长——标记自然结束时发光随之一同消失,
    // 不依赖 Expired 事件链的清理(多层标记由 onMarkExpired 同步刷新);玩家目标经计时器守卫记录。
    // 发光效果 showIcon=false:不显示 HUD 效果标识器(仅保留轮廓视觉,不占效果图标栏)。
    public static int apply(LivingEntity target, int durationTicks) {
        var existing = target.getEffect(ModEffects.MARKED);
        int level = existing != null ? Math.min(existing.getAmplifier() + 1, GameplayConstants.MAX_MARKER - 1) : 0;
        target.addEffect(new MobEffectInstance(ModEffects.MARKED, durationTicks, level, false, true));
        com.merlinkitsune.astral_dice.event.EffectTimerGuard.apply(target,
                new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, durationTicks, 0, false, false));
        return level + 1;
    }

    // 读取目标当前标记层数(无标记返回 0)
    public static int getLevel(LivingEntity target) {
        var existing = target.getEffect(ModEffects.MARKED);
        return existing != null ? existing.getAmplifier() + 1 : 0;
    }

    // 标记效果自然结束时:每分钟减少 1 层标记(层数>1 时重新施加并重置计时,否则标记消失)
    //
    // ⚠️ 为什么不在本事件里直接补写层数:原版在 iterator.remove() **之前**发出 Expired
    //    (LivingEntity.tickEffects:`if (!post(Expired).isCanceled()) { iterator.remove(); onEffectRemoved(...); }`),
    //    此刻旧的高层实例仍在 activeEffects —— addEffect 一个低层实例会被 MobEffectInstance#update
    //    忽略(只进 hiddenEffect),随后又随旧实例一起被移除:表现为「标记整层消失」而不是减 1 层,
    //    而伴随的发光已被刷新到 1200 tick(留下「发光但无标记」)。
    //    也不能靠「取消该次移除 + forceAddEffect 原地替换」:1.21.1 的 Expired 实现 ICancellableEvent 可取消,
    //    但 1.20.1 Forge 的 MobEffectEvent.Expired 明确 not Cancelable —— 两版本写法必须通用。
    //    故统一登记到**下一 tick 的服务器 tick 末尾**补写:那时旧实例已移除,addEffect 正常生效。
    @SubscribeEvent
    public static void onMarkExpired(MobEffectEvent.Expired event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null
                || effect.getEffect().value() != ModEffects.MARKED.get()) {
            return;
        }
        if (effect.getAmplifier() > 0) {
            PENDING_DECAY.add(new PendingDecay(entity, effect.getAmplifier() - 1));
            // 同步刷新伴随的"高亮",与标记保持同一寿命(showIcon=false 不显示 HUD 效果标识器)
            com.merlinkitsune.astral_dice.event.EffectTimerGuard.apply(entity,
                    new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, MARK_DURATION_TICKS, 0, false, false));
        } else {
            // 标记层数归零:同时移除伴随的"高亮"效果(兜底;正常情况下发光随标记自然结束)
            entity.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
        }
    }

    // 下一 tick 补写「标记减 1 层」(见 onMarkExpired 注释)
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_DECAY.isEmpty()) return;
        List<PendingDecay> pending = new ArrayList<>(PENDING_DECAY);
        PENDING_DECAY.clear();
        for (PendingDecay p : pending) {
            LivingEntity entity = p.entity();
            if (entity == null || entity.isRemoved() || !entity.isAlive()) continue;
            if (entity.level().isClientSide()) continue;
            entity.addEffect(new MobEffectInstance(ModEffects.MARKED, MARK_DURATION_TICKS,
                    p.amplifier(), false, true));
        }
    }

    // 狂暴:受到任意来源伤害 +1×等级(非骰战伤害也生效,LOWEST 优先级在骰战结算后追加)
}
