package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.item.sign.RenSignItem;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 鼠鼠护盾(游戏大师立牌 ren)的服务端权威管理。
 *
 * <h2>语义(逐条)</h2>
 * <ul>
 *   <li><b>护盾</b> = 5 黄心({@link #SHIELD_ABSORPTION} = 10 点吸收)+ 抗性提升 +
 *       1 层一次性反击({@link #MAX_COUNTER_CHARGES});由 {@link com.merlinkitsune.astral_dice.effect.RenShieldEffect}
 *       的效果实例作为「是否持有护盾」的唯一真值;</li>
 *   <li><b>授予不叠加</b>:再次获得时把**护盾自己的那份**黄心补满(基线 + 10),不累加;层数上限 1;</li>
 *   <li><b>黄心被打空即清空</b>:每 tick 轮询 {@code getAbsorptionAmount() <= 基线} ⇒ 最多 1 tick 内清空
 *       护盾效果、我们施加的抗性提升与反击层(以及反击的 HUD 图标 {@code ren_counter})。
 *       用轮询而不是伤害事件是因为
 *       {@code /effect clear}、牛奶、死亡、他模组直改吸收值等路径**都不派发伤害事件**;</li>
 *   <li><b>无时限</b>:{@link #SHIELD_EFFECT_DURATION_TICKS}(20:00)只是刷新窗口,
 *       剩余低于 {@link #SHIELD_REFRESH_THRESHOLD_TICKS}(5:00)时自动延长回 20:00 ⇒ HUD 上的倒计时
 *       **不代表护盾到期**(护盾只由「黄心打空 / 玩家死亡 / 效果被移除」结束);</li>
 *   <li><b>被动「鼠鼠救我」只作用于佩戴者本人</b>:佩戴 ren 立牌且超过 {@link #PASSIVE_INTERVAL_TICKS}
 *       (5:00)没有护盾时自动获得 **1 张随机卡牌 + 鼠鼠护盾**(发奖在
 *       {@link RenSignItem#onPassiveTriggered},与主动同口径)。计时基准是**玩家级附件**(非同步 long)+
 *       {@code level.getGameTime()},该时钟存于 level.dat ⇒ 跨重登继续累加(关服期间不推进);</li>
 *   <li><b>卸下立牌不清盾</b>:护盾也可以来自别人的主动技能,其生命周期只由「被打空」等终结条件决定;</li>
 *   <li><b>HUD 图标口径</b>:护盾效果本身**保留图标**;我们施加的**抗性提升隐藏图标**
 *       ({@code showIcon=false},玩家可见的只有护盾 + 反击两个图标);反击层数由
 *       {@code ren_counter} 效果镜像成图标(层数 0 即移除,图标 = images/反击.png)。</li>
 * </ul>
 *
 * <p>驱动:自带 {@code TickEvent.PlayerTickEvent} 订阅(不改动热点文件 {@code event/PlayerTickEvents};
 * 该事件每 tick 被 START/END 两个阶段各调用一次 ⇒ 本类全部逻辑幂等)。黄心判定每 tick 做(只读一次吸收值,
 * 极廉价),被动计时/效果刷新每 20 tick 做一次。
 *
 * <p><b>平台差异</b>:1.20.1 无 {@code MAX_ABSORPTION} 属性、{@code setAbsorptionAmount} 不钳制,
 * 故本类不涉及吸收上限修饰器(1.21.1 侧由效果类自带)。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class RenShieldManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenShieldManager.class);

    /** 护盾提供的吸收量:5 黄心 = 10 点 */
    public static final float SHIELD_ABSORPTION = 10.0F;
    /** 护盾效果时长(仅作刷新窗口;护盾没有真实时限) */
    public static final int SHIELD_EFFECT_DURATION_TICKS = 20 * 60 * 20;
    /** 护盾效果剩余低于该值时延长回满 */
    public static final int SHIELD_REFRESH_THRESHOLD_TICKS = 20 * 60 * 5;
    /** 我们施加的抗性提升时长与刷新阈值 */
    public static final int RESISTANCE_DURATION_TICKS = 20 * 60 * 20;
    public static final int RESISTANCE_REFRESH_THRESHOLD_TICKS = 20 * 60 * 5;
    /** 被动「鼠鼠救我」:超过 5 分钟没有护盾则自动补一个(仅佩戴者本人) */
    public static final int PASSIVE_INTERVAL_TICKS = 20 * 60 * 5;
    /** 反击层上限(一次性充能) */
    public static final int MAX_COUNTER_CHARGES = 1;
    /** 被动/刷新类检查的间隔(tick) */
    private static final int SLOW_INTERVAL_TICKS = 20;

    private RenShieldManager() {
    }

    /** 玩家当前是否持有鼠鼠护盾(效果实例是唯一真值) */
    public static boolean isShielded(Player player) {
        return player != null && player.hasEffect(ModEffects.REN_SHIELD.get());
    }

    public static void grantShield(Player player) {
        grantShield(player, true);
    }

    /**
     * 授予(或补满)鼠鼠护盾。被动与主动技能的唯一入口。
     *
     * <p>吸收算术:首次授予记下当时的吸收值作为**基线**(可能是金苹果/末影骰不死图腾带来的),
     * 只在此基础上加我们那份 10 ⇒ 不会吞掉外部来源;重复授予时把总量拉回 {@code 基线 + 10}
     * (即「把黄心补满到 5 心」),因而**不叠加**。
     *
     * @param notify 是否给获得者发 actionbar「鼠鼠护盾已生效」提示(主动技能会另发一条汇总提示,
     *               故由调用方决定,避免同 tick 两条 actionbar 互相覆盖)
     */
    public static void grantShield(Player player, boolean notify) {
        if (player == null || player.level().isClientSide() || !player.isAlive()) return;
        boolean already = isShielded(player);
        float baseline = already
                ? ModAttachments.getRenShieldBaselineAbsorption(player)
                : player.getAbsorptionAmount();
        ModAttachments.setRenShieldBaselineAbsorption(player, baseline);

        // 先施加护盾效果再加吸收值(1.21.1 由效果提供 +10 的 MAX_ABSORPTION 上限,顺序在那边是硬要求;
        // 本条在 1.20.1 同样是安全的正确顺序)。
        // 六参构造 = (效果, 时长, 等级, ambient, visible, showIcon) ⇒ 关粒子、**保留 HUD 图标**(模组强制口径)
        MobEffectInstance shield = shieldInstance();
        player.addEffect(shield);
        player.setAbsorptionAmount(baseline + SHIELD_ABSORPTION);
        // 显式记一次计时守卫的结束刻(守卫按 max 取,延长语义不受影响)
        EffectTimerGuard.record(player, shield);

        applyOrRefreshResistance(player, true);

        ModAttachments.setRenCounterCharges(player,
                Math.min(MAX_COUNTER_CHARGES, ModAttachments.getRenCounterCharges(player) + 1));
        // 反击层数的可见载体(层数 > 0 才显示图标)
        syncCounterEffect(player);
        ModAttachments.setRenShieldLastSeenTick(player, player.level().getGameTime());

        if (notify) {
            notifyShieldGained(player);
        }
        LOGGER.debug("[Astral Dice][RenShield] granted player={} absorb={} baseline={} already={} notify={}",
                player.getName().getString(), player.getAbsorptionAmount(), baseline, already, notify);
    }

    /** 给获得者发 actionbar「鼠鼠护盾已生效」(主动技能对他人施放时由动作单独调用) */
    public static void notifyShieldGained(Player player) {
        sendActionBar(player, "msg.astral_dice.ren_shield_gained");
    }

    /**
     * 清空护盾(唯一清空入口):回收我们那份吸收、移除护盾效果与我们施加的抗性提升、清反击层。
     *
     * @param reason 仅用于日志(吸收打空 / 效果缺失清理等)
     */
    public static void voidShield(Player player, String reason) {
        if (player == null || player.level().isClientSide()) return;
        float before = player.getAbsorptionAmount();
        // 只回收「我们那份」:外部来源(金苹果等)的剩余吸收原样保留
        player.setAbsorptionAmount(Math.max(0.0F, before - SHIELD_ABSORPTION));
        // 必须是库的内部移除通道:event/ModEffectEvents 会取消一切对 astral_dice:* 效果的外部移除
        if (player.hasEffect(ModEffects.REN_SHIELD.get())) {
            ModEffectRemoval.remove(player, ModEffects.REN_SHIELD.get());
        }
        removeOwnResistance(player);
        ModAttachments.setRenCounterCharges(player, 0);
        removeCounterEffect(player);
        ModAttachments.setRenShieldBaselineAbsorption(player, 0.0F);
        LOGGER.debug("[Astral Dice][RenShield] voided player={} reason={} absorb={} -> {}",
                player.getName().getString(), reason, before, player.getAbsorptionAmount());
    }

    /**
     * 效果被外部路径抹掉(或玩家离线期间异常)时的残留簿记清理:不重复移除效果,
     * 但**要回收我们那份黄心** —— 否则 1.20.1 侧(无吸收上限钳制)会永久留下最多
     * {@link #SHIELD_ABSORPTION} 点「无来源的隐形黄心」;回收量以基线为下界,外部来源不动。
     */
    private static void cleanupStale(Player player) {
        removeOwnResistance(player);
        float baseline = Math.max(0.0F, ModAttachments.getRenShieldBaselineAbsorption(player));
        float current = player.getAbsorptionAmount();
        if (current > baseline) {
            player.setAbsorptionAmount(Math.max(baseline, current - SHIELD_ABSORPTION));
        }
        ModAttachments.setRenCounterCharges(player, 0);
        ModAttachments.setRenShieldBaselineAbsorption(player, 0.0F);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        tick(event.player);
    }

    /** 每 tick 维护(服务端;START/END 两个阶段各调用一次,本方法幂等) */
    public static void tick(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (player.isSpectator() || !player.isAlive()) return;

        if (isShielded(player)) {
            // ① 黄心被打空 ⇒ 立即清空(每 tick 判,最多 1 tick 延迟)
            float baseline = Math.max(0.0F, ModAttachments.getRenShieldBaselineAbsorption(player));
            if (player.getAbsorptionAmount() <= baseline) {
                voidShield(player, "absorption_empty");
                return;
            }
            if (player.tickCount % SLOW_INTERVAL_TICKS != 0) return;
            // ② 记录「最后一次持有护盾」的时刻(被动计时基准)并刷新效果时长
            ModAttachments.setRenShieldLastSeenTick(player, player.level().getGameTime());
            refreshShieldEffects(player);
            syncCounterEffect(player);
            return;
        }

        // 无护盾:清理残留簿记(效果被移除/离线期间异常等)+ 回收我们那份黄心
        if (ModAttachments.isRenShieldOwnResistance(player)
                || ModAttachments.getRenCounterCharges(player) != 0
                || ModAttachments.getRenShieldBaselineAbsorption(player) != 0.0F) {
            cleanupStale(player);
        }
        // 「反击」图标只跟随护盾:护盾不在就不该显示
        if (player.hasEffect(ModEffects.REN_COUNTER.get())) {
            removeCounterEffect(player);
        }

        if (player.tickCount % SLOW_INTERVAL_TICKS != 0) return;
        // ③ 被动「鼠鼠救我」:仅佩戴者本人;首次观察记为起点(装备后 5 分钟首次补盾)
        if (!RenSignItem.isEquipped(player)) return;
        long now = player.level().getGameTime();
        long last = ModAttachments.getRenShieldLastSeenTick(player);
        if (last <= 0L) {
            ModAttachments.setRenShieldLastSeenTick(player, now);
            return;
        }
        if (now - last < PASSIVE_INTERVAL_TICKS) return;
        // 2026-09-25 用户裁决:被动与主动口径一致 = 1 张随机卡牌 + 鼠鼠护盾
        RenSignItem.onPassiveTriggered(player);
    }

    /** 护盾存续期间维持护盾/抗性时长(剩余低于阈值时延长回满) */
    private static void refreshShieldEffects(Player player) {
        MobEffectInstance shield = player.getEffect(ModEffects.REN_SHIELD.get());
        if (shield != null && shield.getDuration() <= SHIELD_REFRESH_THRESHOLD_TICKS) {
            MobEffectInstance extended = shieldInstance();
            player.addEffect(extended);
            EffectTimerGuard.record(player, extended);
        }
        applyOrRefreshResistance(player, false);
    }

    private static MobEffectInstance shieldInstance() {
        return new MobEffectInstance(ModEffects.REN_SHIELD.get(),
                SHIELD_EFFECT_DURATION_TICKS, 0, false, false, true);
    }

    /** 「反击」层数的 HUD 图标实例(时长与护盾同一刷新窗口;反击没有独立时限) */
    private static MobEffectInstance counterInstance() {
        return new MobEffectInstance(ModEffects.REN_COUNTER.get(),
                SHIELD_EFFECT_DURATION_TICKS, 0, false, false, true);
    }

    /**
     * 把服务端的反击层数**镜像**到 HUD 图标上(层数是真值,效果只是可见载体):
     * 层数 > 0 ⇒ 缺图标就补、剩余时长低于阈值就刷新;层数 <= 0 ⇒ 立刻摘掉图标。
     * 由授予 / 每 20 tick 维护 / 反击消耗三处调用,故任何路径都不会出现「有层无图标」或反之。
     */
    public static void refreshCounterEffect(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (ModAttachments.getRenCounterCharges(player) <= 0) {
            removeCounterEffect(player);
            return;
        }
        MobEffectInstance current = player.getEffect(ModEffects.REN_COUNTER.get());
        if (current == null || current.getDuration() <= SHIELD_REFRESH_THRESHOLD_TICKS) {
            EffectTimerGuard.apply(player, counterInstance());
        }
    }

    private static void syncCounterEffect(Player player) {
        refreshCounterEffect(player);
    }

    private static void removeCounterEffect(Player player) {
        if (player.hasEffect(ModEffects.REN_COUNTER.get())) {
            ModEffectRemoval.remove(player, ModEffects.REN_COUNTER.get());
        }
    }

    /**
     * 施加或延长抗性提升。
     *
     * <p><b>图标口径(2026-09-25 用户裁决)</b>:我们施加的这份抗性提升 {@code showIcon=false}
     * —— 玩家可见的只有「鼠鼠护盾」与「反击」两个图标,抗性只作为数值生效。
     *
     * <p>只在玩家**当前没有**抗性提升时才由我们施加(并记 {@code ren_shield_own_resistance}),
     * 这样清空护盾时就能确定「这份抗性是我们给的」而不是玩家自己的药水;外部药水(或更高等级)
     * 覆盖我们时,放大器不再为 0,清空判据会据此放行、不误删。
     *
     * @param force 护盾刚获得时无条件检查一次(与 refresh 共用)
     */
    private static void applyOrRefreshResistance(Player player, boolean force) {
        MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance == null) {
            EffectTimerGuard.apply(player, new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                    RESISTANCE_DURATION_TICKS, 0, false, false, false));
            ModAttachments.setRenShieldOwnResistance(player, true);
            return;
        }
        if (force) return;
        if (ModAttachments.isRenShieldOwnResistance(player)
                && resistance.getDuration() <= RESISTANCE_REFRESH_THRESHOLD_TICKS) {
            EffectTimerGuard.apply(player, new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                    RESISTANCE_DURATION_TICKS, 0, false, false, false));
        }
    }

    /** 只移除「我们施加的那份」抗性提升(放大器 0 且带我方标记),玩家的药水/更高等级一律保留 */
    private static void removeOwnResistance(Player player) {
        if (!ModAttachments.isRenShieldOwnResistance(player)) return;
        MobEffectInstance resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance != null && resistance.getAmplifier() == 0) {
            player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }
        ModAttachments.setRenShieldOwnResistance(player, false);
    }

    private static void sendActionBar(Player player, String langKey, Object... args) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ModNetwork.sendToPlayer(serverPlayer, new ModNetwork.ActionBarMessage(
                Component.translatable(langKey, args).withStyle(ChatFormatting.YELLOW),
                GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }
}
