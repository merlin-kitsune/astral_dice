package com.merlinkitsune.astral_dice.item.card;
import com.merlinkitsune.astral_dice.item.CuriosCompat;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;


import java.util.ArrayList;
import java.util.List;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 效果牌出牌冷却(窗口)核心逻辑。
 *
 * 规则(冷却与效果判定分离):
 * - 基础出牌数固定为 1(游戏设计决定,不可配置)。
 * - 固定出牌数加成(佩戴即提供,不卸载一直有效):大背包 +1、忍术飞镖 +1。
 * - 临时出牌数加成(效果驱动,效果结束自动清除):活体书页效果 +1、命运的指引效果 +1、
 *   忍者立牌(komachi)主动技能 +1(出牌数银行,按实际出牌消耗,跨周期保留至用尽)。
 * - 出牌数无绝对上限:1 + 固定 + 临时 + 忍者银行 实时计算,加成来源可无限叠加
 *   (不再有 MAX_EFFECT_CARD_PLAYS=9 上限)。
 * - 只要出效果牌就立即开始冷却倒计时(30 秒);冷却归零时出牌数归零。
 *   效果牌本身的效果单独计算;单个轮询内所有已出效果牌的效果全部结束后才可重新出牌
 *   (冷却已归零但效果仍在生效时,出牌被锁定)。
 * - 效果牌轮次(出牌周期)定义:指当前出牌周期——不论出牌数是否已达上限——只要仍有
 *   效果牌的"能力/效果"或"出牌冷却"未结束,周期即未结束。当所有效果牌进度走完
 *   **且** 出牌冷却时间走完后,这一周期才算结束;周期边界用于"每轮一次"类计数清理
 *   与新一轮开牌锁判定。
 * - 冷却期间允许继续出牌累积出牌数,每次出牌将冷却重置为 30 秒(从最后一张起算)。
 * - 出牌数来源与上限全部实时计算(不缓存),卸载大背包/忍术飞镖立即生效,
 *   更换立牌/骰子无法刷新出牌锁。
 * - 出牌数/冷却/忍者临时出牌附件均已 .sync() 到客户端,客户端可执行与
 *   服务端一致的 isBlocked 预检(BaseEffectCardItem 在 use/interactLivingEntity
 *   中先做客户端预检,被阻止时不消耗卡片)。
 * - 冷却倒计时归 0 但 tick 尚未清零(每 20 tick 清理一次)的窗口内,旧计数
 *   视为新窗口(见 {@link #isBurstFull}),不会误锁新一轮。
 * - 效果待定判定通过 {@link #registerEffectPendingSource} 注册表统一提供(替代硬编码效果列表),
 *   新增效果牌时注册其"效果是否在生效"的判定即可。
 */
public final class EffectCardPeriod {
    private EffectCardPeriod() {
    }

    // === 出牌数来源(可扩展) ===
    @FunctionalInterface
    public interface ExtraPlaySource {
        boolean isActive(Player player);

        // 出牌数加成(默认 +1;需要其他数值时匿名类覆写)
        default int amount() {
            return 1;
        }
    }

    // === 效果待定来源(可扩展):效果牌使用后是否仍在生效 ===
    @FunctionalInterface
    public interface EffectPendingSource {
        boolean isActive(Player player);

        // 该待定来源对应的效果(用于计算剩余被锁时长;纯逻辑判定可返回 null 不参与时长计算)
        default MobEffect effect() {
            return null;
        }
    }

    private static final List<ExtraPlaySource> FIXED_SOURCES = new ArrayList<>();
    private static final List<ExtraPlaySource> TEMPORARY_SOURCES = new ArrayList<>();
    private static final List<EffectPendingSource> EFFECT_PENDING_SOURCES = new ArrayList<>();

    // 注册固定出牌数来源(佩戴即提供)
    public static void registerFixedSource(ExtraPlaySource source) {
        FIXED_SOURCES.add(source);
    }

    // 注册临时出牌数来源(效果/技能状态驱动)
    public static void registerTemporarySource(ExtraPlaySource source) {
        TEMPORARY_SOURCES.add(source);
    }

    // 注册效果待定来源(效果牌使用后判定其效果是否仍在生效)
    public static void registerEffectPendingSource(EffectPendingSource source) {
        EFFECT_PENDING_SOURCES.add(source);
    }

    // 注册"某效果生效期间出牌被锁"的来源(效果驱动,自动提供剩余被锁时长)
    public static void registerEffectPendingSource(MobEffect effect) {
        registerEffectPendingSource(new EffectPendingSource() {
            @Override
            public boolean isActive(Player player) {
                return player.hasEffect(effect);
            }

            @Override
            public MobEffect effect() {
                return effect;
            }
        });
    }

    static {
        // 固定来源:大背包 +1、忍术飞镖 +1(不卸载持续提供)
        registerFixedSource(p -> hasCurio(p, ModItems.BIG_BACKPACK_CHIP.get()));
        registerFixedSource(p -> hasCurio(p, ModItems.NINJA_STAR_CHIP.get()));
        // 临时来源(效果驱动,效果结束自动清除):
        registerTemporarySource(p -> p.hasEffect(ModEffects.LIVING_PAGE.get())); // 活体书页效果
        registerTemporarySource(p -> p.hasEffect(ModEffects.FATE_GUIDANCE.get()));     // 命运的指引效果
        registerTemporarySource(p -> ModAttachments.isCandyChipPlayBonusActive(p)); // 可口糖果:满血使用效果牌触发(每轮一次)
        registerTemporarySource(p -> ModAttachments.isSatellitePlayBonusActive(p)); // 探天卫星:使用轨道炮后触发(每 1:00 一次)

        // 效果待定来源(全部效果牌统一注册;新增效果牌在此追加或调用 registerEffectPendingSource)
        registerEffectPendingSource(ModEffects.LIVING_PAGE.get());
        registerEffectPendingSource(ModEffects.MONSTER_LASER.get());
        registerEffectPendingSource(ModEffects.MONSTER_BRICK.get());
        registerEffectPendingSource(ModEffects.ORBITAL_STRIKE.get());
        registerEffectPendingSource(ModEffects.DIRECTIONAL_BLAST.get());
        registerEffectPendingSource(ModEffects.FATE_GUIDANCE.get());
        registerEffectPendingSource(ModEffects.KING_POWER.get());
        registerEffectPendingSource(ModEffects.BERSERK.get());
        registerEffectPendingSource(ModEffects.UNWAVERING.get());
    }

    // 当前出牌数上限 = 基础 1 + 固定 + 临时 + 忍者银行(实时计算,无绝对上限)
    public static int getMaxAllowed(Player player) {
        int extra = 0;
        for (ExtraPlaySource source : FIXED_SOURCES) {
            if (source.isActive(player)) extra += source.amount();
        }
        for (ExtraPlaySource source : TEMPORARY_SOURCES) {
            if (source.isActive(player)) extra += source.amount();
        }
        // 忍者立牌(komachi)主动的出牌数银行:按实际出牌消耗,跨周期保留至用尽
        extra += ModAttachments.getKomachiExtraPlays(player);
        return 1 + extra;
    }

    // 本轮已出牌数
    public static int getPlayCount(Player player) {
        return ModAttachments.getEffectCardPlayCount(player);
    }

    // 周期是否已满(出牌数达到上限)
    // 注意:冷却倒计时已归 0 但 tick 尚未清零(每 20 tick 才清理一次)时,旧计数不再视为"满"——
    // 视为新窗口,避免冷却结束瞬间误锁新一轮(registerPlay 内部有同样的 stale 处理)。
    // 本方法保持只读,客户端(附件已同步)可安全调用做预检。
    public static boolean isBurstFull(Player player) {
        long cdEnd = ModAttachments.getEffectCardCooldownEnd(player);
        if (cdEnd > 0 && player.level().getGameTime() >= cdEnd) return false;
        int count = getPlayCount(player);
        if (count <= 0) return false;
        return count >= getMaxAllowed(player);
    }

    // 冷却是否进行中
    public static boolean isCooldownActive(Player player) {
        long cdEnd = ModAttachments.getEffectCardCooldownEnd(player);
        return cdEnd > 0 && player.level().getGameTime() < cdEnd;
    }

    // 是否仍有效果牌效果在生效(单个轮询内所有已出效果牌的效果结束后才可重新出牌)
    // 通过 EffectPendingSource 注册表统一判定,新增效果牌注册后自动生效
    public static boolean isEffectPending(Player player) {
        for (EffectPendingSource source : EFFECT_PENDING_SOURCES) {
            if (source.isActive(player)) {
                return true;
            }
        }
        return false;
    }

    // 剩余被锁 tick:取“全局冷却结束时间”与“所有效果牌效果中最长的结束时间”的较大值
    // (遍历 EFFECT_PENDING_SOURCES,由各来源的 effect() 推导剩余时长,与出牌锁判定保持单一注册源)
    public static long getRemainingBlockTicks(Player player) {
        long now = player.level().getGameTime();
        long maxEnd = ModAttachments.getEffectCardCooldownEnd(player);
        for (EffectPendingSource source : EFFECT_PENDING_SOURCES) {
            MobEffect effect = source.effect();
            if (effect != null) {
                maxEnd = Math.max(maxEnd, now + remainingEffectTicks(player, effect));
            }
        }
        return Math.max(0, maxEnd - now);
    }

    // 剩余被锁秒数(向上取整)
    public static int getRemainingBlockSeconds(Player player) {
        return (int) Math.ceil(getRemainingBlockTicks(player) / 20.0);
    }

    private static int remainingEffectTicks(Player player, MobEffect effect) {
        MobEffectInstance instance = player.getEffect(effect);
        return instance != null ? instance.getDuration() : 0;
    }


    /**
     * 出牌锁判定:
     * 1. 本轮出牌数已达上限 → 阻止;
     * 2. 冷却进行中 → 允许继续出牌累积(上限内);
     * 3. 冷却已归零(或未开始)但效果牌效果仍在生效 → 阻止开始新一轮(效果结束后才可重新出牌)。
     */
    public static boolean isBlocked(Player player) {
        if (isBurstFull(player)) return true;
        if (isCooldownActive(player)) return false;
        return isEffectPending(player);
    }

    /**
     * 出牌登记:出牌数 +1(调用前需通过 {@link #isBlocked} 校验),并立即开始/重置冷却倒计时。
     * 所有效果牌(功能/伤害/专属)出牌均立即开始冷却,效果与冷却分离计算。
     */
    public static void registerPlay(Player player) {
        long now = player.level().getGameTime();
        long cooldown = ModAttachments.getEffectCardCooldownEnd(player);
        // 冷却倒计时已归 0 未及清理:先恢复计数再登记(避免跨窗口残留计数)
        if (cooldown > 0 && now >= cooldown) {
            ModAttachments.setEffectCardCooldownEnd(player, 0);
            ModAttachments.setEffectCardPlayCount(player, 0);
            ModAttachments.setCandyChipPlayBonusActive(player, false);
            ModAttachments.setSatellitePlayBonusActive(player, false);
            cooldown = 0;
        }
        int count = ModAttachments.getEffectCardPlayCount(player) + 1;
        ModAttachments.setEffectCardPlayCount(player, count);
        // 消耗一张忍者立牌主动的出牌数银行(若有),使 +1 恰好对应一次实际出牌
        int komachiBank = ModAttachments.getKomachiExtraPlays(player);
        if (komachiBank > 0) {
            ModAttachments.setKomachiExtraPlays(player, komachiBank - 1);
        }
        // 立即开始/重置冷却倒计时(从最后一张出牌起算)
        ModAttachments.setEffectCardCooldownEnd(player,
                now + GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS * 20L);
    }

    // 每 tick 调用:冷却倒计时归 0 时出牌数归零
    public static void tick(Player player) {
        long now = player.level().getGameTime();
        long cooldown = ModAttachments.getEffectCardCooldownEnd(player);
        if (cooldown <= 0) return;
        if (now < cooldown) return;
        ModAttachments.setEffectCardCooldownEnd(player, 0);
        ModAttachments.setEffectCardPlayCount(player, 0);
        // 周期归零:忍者立牌主动的出牌数银行保留(跨周期有效,按实际出牌消耗)
        // 周期归零:清除可口糖果的"满血出牌数+1"(每个轮次最多一次)
        ModAttachments.setCandyChipPlayBonusActive(player, false);
        ModAttachments.setSatellitePlayBonusActive(player, false);
    }

    private static boolean hasCurio(Player player, net.minecraft.world.item.Item item) {
        var curios = com.merlinkitsune.astral_dice.item.CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(item)).isPresent();
    }
}
