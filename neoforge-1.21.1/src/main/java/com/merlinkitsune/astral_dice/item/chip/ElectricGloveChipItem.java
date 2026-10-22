package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;
import com.merlinkitsune.astral_dice.combat.DamageCardFxScheduler;
import com.merlinkitsune.astral_dice.combat.ElectricShockFx;
import com.merlinkitsune.astral_dice.combat.SpellDamageRegistry;
import com.merlinkitsune.astral_dice.event.RailgunStrikeScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 电击手套筹码（2026-10-21 语义定稿，用户裁决）：
 * <ul>
 *   <li>使用<b>伤害效果牌</b>攻击目标时，令<b>目标 {@value #AOE_RADIUS} 格内所有目标</b>（<b>含主目标</b>）
 *       受到 {@value #SHOCK_DAMAGE} 点魔法伤害；</li>
 *   <li>伤害延迟 {@value #SHOCK_DELAY_TICKS} tick（1 秒）后落地；</li>
 *   <li>每使一个目标受到该效果伤害，获得 {@value #CHARGE_PER_HIT} 层【充能】；</li>
 *   <li>冷却 {@value #COOLDOWN_TICKS} tick（1:00），在<b>调度时</b>检查并置位。</li>
 * </ul>
 *
 * <p><b>与旧实现的区别（重要）</b>：旧实现是「充能 ≥ 4 时消耗 4 层 ⇒ 把该牌本次主目标命中的法伤
 * 扩散到周围目标」，靠 {@code SpellDamageRegistry} 里的一个 {@code SpellDamageModifier} 在
 * {@code DiceCombatEvents#runAsGloveSplashInstance} 窗口内同步触发。
 * 自本版起改为<b>独立延迟结算</b>：出牌时只登记一次延迟动作
 * （{@link RailgunStrikeScheduler}，与电磁炮雷击同一队列），到期后由 {@link #executeShock} 自己枚举
 * 受害者、自己走独立伤害通道结算 ⇒ <b>不再依赖</b>法伤链窗口，天然不可能递归。
 * <b>不消耗充能、无充能门槛、也不是「每张牌 +1 充能」</b>；充能改为<b>每命中一个目标 +1</b>。
 *
 * <p>调度时刻的主目标来自 {@link DamageCardFxScheduler#latestGloveTarget}（peek，无副作用）；
 * 取不到（该牌本次没投出 FX 载体）⇒ 不做任何事、也不进入冷却。
 */
public class ElectricGloveChipItem extends BaseChipItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElectricGloveChipItem.class);

    /** 电击半径（格）：以主目标为圆心，**包含主目标本身** */
    public static final int AOE_RADIUS = 3;
    /** 每次电击命中的固定伤害（点，魔法伤害） */
    public static final float SHOCK_DAMAGE = 2.0F;
    /** 电击延迟（20 tick = 1 秒） */
    public static final int SHOCK_DELAY_TICKS = 20;
    /** 触发冷却（20 * 60 tick = 1:00） */
    public static final int COOLDOWN_TICKS = 20 * 60;
    /** 每使一个目标受到电击伤害获得的充能层数 */
    public static final int CHARGE_PER_HIT = 1;

    /**
     * 玩家 UUID → 冷却结束的 gameTime。
     *
     * <p>纯服务端内存态（与本模组其它短周期调度器同口径：延迟/冷却都只在内存里，重启即清空）。
     * 之所以不放进 {@code component/ModAttachments}：本阶段只允许改少数几个文件，
     * 且该冷却不参与跨存档/跨维度持久化语义。
     */
    private static final Map<UUID, Long> COOLDOWN_END = new HashMap<>();

    public ElectricGloveChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴电击手套 */
    public static boolean isEquipped(Player player) {
        // 总闸门(骰子装备和卸除调整):未佩戴骰子 ⇒ 本件功能一律不生效(数值保留)
        if (player == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.ELECTRIC_GLOVE_CHIP.get())).isPresent();
    }

    /** 玩家是否处于电击手套冷却中（只读；顺带清理已过期的条目）。 */
    public static boolean isOnCooldown(Player player) {
        if (player == null) return false;
        final UUID id = player.getUUID();
        final Long end = COOLDOWN_END.get(id);
        if (end == null) return false;
        if (player.level().getGameTime() >= end) {
            COOLDOWN_END.remove(id);
            return false;
        }
        return true;
    }

    /**
     * 该效果牌是否属于「伤害效果牌」——委托 {@link BaseEffectCardItem#isDamageEffectCard}
     * (单一事实源:魔法箭袋的「本次使用的效果牌」追踪用同一判定)。
     */
    public static boolean isDamageEffectCard(ItemStack cardStack) {
        return BaseEffectCardItem.isDamageEffectCard(cardStack);
    }

    /**
     * 使用效果牌时调用（所有效果牌）。只有<b>伤害效果牌</b>且不在冷却中才会安排电击。
     *
     * <p>顺序（不可调换）：
     * <ol>
     *   <li>客户端 / 未佩戴 / 非服务端玩家 ⇒ 直接返回；</li>
     *   <li>非伤害效果牌 ⇒ 返回；</li>
     *   <li>冷却中 ⇒ 返回（<b>冷却在调度时就检查并置位</b>，避免连打重复调度）；</li>
     *   <li>peek 本次这张牌的主目标（无副作用）⇒ 取不到就返回，<b>不置冷却</b>；</li>
     *   <li>登记一次 {@value #SHOCK_DELAY_TICKS} tick 后的延迟动作；</li>
     *   <li>置冷却 {@value #COOLDOWN_TICKS} tick。</li>
     * </ol>
     *
     * <p>⚠️ 调用点（{@code BaseEffectCardItem}）在 {@code applyEffect} <b>之后</b> ⇒ 此时该牌已经把
     * FX 载体登记进 {@link DamageCardFxScheduler}，故这里能 peek 到它自己那一条。
     */
    public static void onEffectCardUsed(Player player, ItemStack cardStack) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!isDamageEffectCard(cardStack)) return;
        if (isOnCooldown(player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // peek：本次这张牌的主目标（不置 glovePaid、不从队列移除）
        final LivingEntity target = DamageCardFxScheduler.latestGloveTarget(serverPlayer, cardStack);
        if (target == null) return;

        RailgunStrikeScheduler.Pending pending = RailgunStrikeScheduler.schedule(
                level, target, target.position(), serverPlayer, SHOCK_DELAY_TICKS,
                ElectricGloveChipItem::executeShock);
        if (pending == null) return;

        COOLDOWN_END.put(serverPlayer.getUUID(), level.getGameTime() + COOLDOWN_TICKS);
    }

    /**
     * 延迟到期后的电击结算（由 {@link RailgunStrikeScheduler} 在服务端 tick 边界调用）。
     *
     * <p>顺序固定：
     * <ol>
     *   <li>取 {@code center} 半径 {@value #AOE_RADIUS} 格内的受害者（**含主目标**、排除施法者）；</li>
     *   <li>逐个走效果牌同一条<b>独立法术伤害通道</b>（{@code astral_dice:card_spell}）结算固定
     *       {@value #SHOCK_DAMAGE} 点；</li>
     *   <li>每<b>成功</b>造成一次伤害 ⇒ 施法者 +{@value #CHARGE_PER_HIT} 层【充能】（含主目标）；</li>
     *   <li>每个真正吃到伤害的受害者 ⇒ 在其躯干位置撒电火花（粒子库，见 {@link ElectricShockFx}）；</li>
     *   <li>本次只要命中至少一个目标 ⇒ 在圆心播<b>一次</b>电击音效（不按受害者叠加）。</li>
     * </ol>
     *
     * <p>⚠️ 这里<b>不</b>包裹 {@code DiceCombatEvents#runAsGloveSplashInstance}：新路径是独立结算，
     * 不依赖法伤链窗口，天然不可能递归。异常一律吞掉（只 warn），避免污染 tick 调度器。
     *
     * @return 是否至少命中一个目标
     */
    public static boolean executeShock(ServerLevel level, Vec3 center, ServerPlayer cause) {
        if (level == null || center == null) return false;
        try {
            final List<LivingEntity> victims = SpellDamageRegistry.collectGloveShockTargets(
                    level, center, cause, AOE_RADIUS);
            if (victims.isEmpty()) return false;

            boolean hitAny = false;
            for (LivingEntity victim : victims) {
                if (victim == null || !victim.isAlive()) continue;
                // 同一条独立伤害通道：card_spell（魔法/法术档）+ 法伤组跳字 + 内部 AOE 窗口（不进骰战）
                final boolean dealt = DamageCardFxScheduler.applySpellDamageAmount(
                        level, cause, victim, (int) SHOCK_DAMAGE, false);
                if (!dealt) continue;
                hitAny = true;
                // 观感：只给真正吃到伤害的目标放电火花（未受伤的目标不该有电光）
                ElectricShockFx.sparksAt(level, ElectricShockFx.victimCenter(victim));
                if (cause != null) {
                    ChargeManager.addStacks(cause, CHARGE_PER_HIT);
                }
            }
            if (hitAny) {
                // 音效只播一次：半径 3 格可能同时命中多个目标，按受害者叠加会变成轰鸣
                ElectricShockFx.shockSound(level, center.add(0.0D, 1.0D, 0.0D));
            }
            return hitAny;
        } catch (Exception ex) {
            LOGGER.warn("[Astral Dice] 电击手套结算失败，已跳过本次", ex);
            return false;
        }
    }
}
