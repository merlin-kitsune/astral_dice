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

/** 目标实体 tag：记录施印者 UUID（scoreboard tag 跨五线持久化）。 */
    private static final String OWNER_TAG_PREFIX = "astral_dice_abyss_owner:";

    /** 佩戴者攻击力修饰符的固定 key。 */
    private static final String ATTACK_BONUS_KEY = "sykes_abyss_attack";

    private record PendingDecay(LivingEntity entity, int amplifier) {
    }

    private static final List<PendingDecay> PENDING_DECAY = new ArrayList<>();

    private AbyssMarkManager() {
    }

    /** 施加 1 层（已满层则只重置时长）。返回施加后的层数。 */
    public static int apply(LivingEntity target) {
        return apply(target, null);
    }

    /**
     * 施加 1 层并绑定施印者。
     *
     * <p>绑定规则：目标还没有印记（或旧印记没有 owner）时，第一个施印者绑定成功；
     * 已有其他玩家的印记时**不改绑定**，但这次施加仍然照常 +1 层（区域/触须加层不会被吞）。
     */
    public static int apply(LivingEntity target, java.util.UUID owner) {
        MobEffectInstance existing = target.getEffect(Effects.holder(ModEffects.ABYSS_MARK.get()));
        int current = existing != null ? Math.min(existing.getAmplifier() + 1, MAX_STACKS) : 0;
        java.util.UUID bound = getOwner(target);
        if (owner != null && (current <= 0 || bound == null)) {
            setOwner(target, owner);
        }
        int amplifier = existing != null ? Math.min(existing.getAmplifier() + 1, MAX_STACKS - 1) : 0;
        target.addEffect(new MobEffectInstance(Effects.holder(ModEffects.ABYSS_MARK.get()), DURATION_TICKS, amplifier, false, true));
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

/**
     * 印记自然到期：层数 > 1 时登记「下一 tick 降一层」。
     *
     * <p>（{@code MobEffectEvent.Expired} 在本线由 {@code platform/StarengineBridges} 从
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
            return;
        }
        if (getStacks(entity) > 0) return;
        java.util.UUID owner = getOwner(entity);
        if (owner != null) {
            // 绑定持续到目标被击杀：印记自然到期只影响层数与攻击力加成，不解除绑定
            refreshOwner(entity, owner);
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

    /** 目标身上绑定的施印者（无绑定返回 null）。 */
    public static java.util.UUID getOwner(LivingEntity target) {
        for (String tag : target.getTags()) {
            if (tag.startsWith(OWNER_TAG_PREFIX)) {
                try {
                    return java.util.UUID.fromString(tag.substring(OWNER_TAG_PREFIX.length()));
                } catch (IllegalArgumentException ignored) {
                    // 脏 tag 当作没有绑定
                }
            }
        }
        return null;
    }

    /** 清掉目标身上所有施印者 tag（只动本机制自己的前缀）。 */
    public static void clearOwner(LivingEntity target) {
        for (String tag : new ArrayList<>(target.getTags())) {
            if (tag.startsWith(OWNER_TAG_PREFIX)) {
                target.removeTag(tag);
            }
        }
    }

    private static void setOwner(LivingEntity target, java.util.UUID owner) {
        clearOwner(target);
        target.addTag(OWNER_TAG_PREFIX + owner);
    }

    /** 当前玩家名下仍存活、仍带印记的敌对目标数（攻击力加成 = 这个数）。 */
    public static int countMarkedFor(net.minecraft.world.entity.player.Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return 0;
        int count = 0;
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity living) || !(living instanceof net.minecraft.world.entity.monster.Enemy)) continue;
            if (!living.isAlive() || living.isRemoved()) continue;
            if (getStacks(living) <= 0) continue;
            if (player.getUUID().equals(getOwner(living))) count++;
        }
        return count;
    }

    /** 按「名下带印记的存活目标数」刷新佩戴者攻击力（0 时自动移除修饰符）。 */
    public static void refreshAttackBonus(net.minecraft.world.entity.player.Player player) {
        int count = countMarkedFor(player);
        com.merlinkitsune.astral_dice.item.sign.SignAttributeUtil.apply(player,
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                ATTACK_BONUS_KEY, count, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
    }

    /** 卸下立牌 / 骰子失效时清掉攻击力加成。 */
    public static void clearAttackBonus(net.minecraft.world.entity.player.Player player) {
        com.merlinkitsune.astral_dice.item.sign.SignAttributeUtil.remove(player,
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, ATTACK_BONUS_KEY);
    }

    private static void refreshOwner(LivingEntity entity, java.util.UUID ownerId) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        net.minecraft.server.level.ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);
        if (player != null) refreshAttackBonus(player);
    }

    /** 目标死亡：解除绑定并立刻刷新施印者的攻击力（不必等下一次 curioTick）。 */
    @SubscribeEvent
    public static void onMarkedDeath(com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        java.util.UUID owner = getOwner(entity);
        if (owner == null) return;
        clearOwner(entity);
        refreshOwner(entity, owner);
    }

    /** 调试/取证辅助：给目标施满 20 层（供探针使用，不在玩法路径上）。 */
    public static void fillStacks(LivingEntity target) {
        target.addEffect(new MobEffectInstance(Effects.holder(ModEffects.ABYSS_MARK.get()),
                DURATION_TICKS, MAX_STACKS - 1, false, true));
    }
}
