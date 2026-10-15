package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.combat.DiceCombatEvents;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 安全气囊筹码:
 * <ul>
 *   <li><b>保命</b>:受到致命伤害时,消耗 6 点充能使本次伤害无效(冷却 1:00);
 *       触发判定与结算由 {@code ChipDamageHandler} 在最终伤害阶段执行,本类只负责条件判定与资源/冷却变更;</li>
 *   <li><b>受击充能</b>(2026-10-15 用户裁决追加能力):**受到伤害时充能 +1**(冷却 15 秒)。
 *       与保命冷却**完全独立**(两个附件键各记各的),故保命不可用时受击照样攒充能。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class AirbagChipItem extends BaseChipItem {
    /** 每次触发消耗的充能层数 */
    public static final int CHARGE_COST = 6;
    /** 触发冷却(1:00) */
    public static final int COOLDOWN_TICKS = 20 * 60;
    /** 受击充能:每次受到伤害获得的充能层数(2026-10-15 用户裁决) */
    public static final int HIT_CHARGE_GAIN = 1;
    /** 受击充能冷却(15 秒;与保命冷却 {@link #COOLDOWN_TICKS} 各自独立,互不干扰) */
    public static final int HIT_CHARGE_COOLDOWN_TICKS = 20 * 15;

    public AirbagChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴安全气囊 */
    public static boolean isEquipped(Player player) {
        // 总闸门(骰子装备和卸除调整):未佩戴骰子 ⇒ 本件功能一律不生效(数值保留)
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.AIRBAG_CHIP.get())).isPresent();
    }

    /** 是否处于触发冷却中 */
    public static boolean isOnCooldown(Player player) {
        if (player == null) return false;
        return player.level().getGameTime() < ModAttachments.getAirbagCooldownEnd(player);
    }

    /**
     * 受到致命伤害时调用:满足条件则消耗 6 点充能、进入 1:00 冷却,返回 true(本次伤害应被无效化)。
     *
     * <p>2026-10-08 用户裁决追加能力:无效化成功后,对「造成该次伤害的施加者」反击一次。
     * 伤害口径与鼠鼠护盾 / 肉弹战车嘲讽 / 摩西破绽闪避的反击完全同源(见
     * {@link DiceCombatEvents#injectCounterDamage});施加者取伤害源的击杀归属实体,
     * 自伤(施加者即本人)与无归属来源(例如 {@code /kill} 的 generic_kill)一律不反击。
     */
    public static boolean tryNegateFatal(Player player, DamageSource source) {
        if (player == null || player.level().isClientSide()) return false;
        if (!isEquipped(player)) return false;
        if (isOnCooldown(player)) return false;
        if (ChargeManager.getStacks(player) < CHARGE_COST) return false;

        ChargeManager.consume(player, CHARGE_COST);
        ModAttachments.setAirbagCooldownEnd(player, player.level().getGameTime() + COOLDOWN_TICKS);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.merlinkitsune.astral_dice.network.ModNetwork.sendToPlayer(sp,
                    new com.merlinkitsune.astral_dice.network.ModNetwork.ActionBarMessage(
                            Component.translatable("hud.astral_dice.airbag_trigger", CHARGE_COST).withStyle(net.minecraft.ChatFormatting.YELLOW),
                            com.merlinkitsune.starenginelib.component.GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);
        counterAttacker(player, source);
        return true;
    }

    /**
     * 2026-10-08 用户裁决:安全气囊追加能力「对造成该伤害的施加者反击」。
     *
     * <p>与既有反击同源(复用 {@link DiceCombatEvents#injectCounterDamage}):伤害 = 手持最高近战武器
     * 基础伤害 + 骰战攻击力加成链 + 已装备攻击牌随机掷骰;徒手按空手 1 点起算。
     * 反击链内不会再反击 —— 递归由 {@code DiceCombatEvents} 的 counterDepth 守卫兜住。
     */
    private static void counterAttacker(Player player, DamageSource source) {
        if (source == null) return;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == player) return;
        DiceCombatEvents.injectCounterDamage(player, attacker);
    }

    /**
     * 受击充能(2026-10-15 用户裁决追加能力):**受到伤害时充能 +1**,冷却 15 秒。
     *
     * <p>挂点 = {@code LivingHurtEvent}(⚠️ 平台差异:1.21.1 侧是 {@code LivingDamageEvent.Pre},
     * 1.20.1 Forge 没有该形态,按 {@code docs/compat-1.20.1-forge.md} 的事件重映射改用
     * {@code LivingHurtEvent})。取证(只读开包,来源 =
     * {@code forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar}):
     * {@code net/minecraftforge/common/ForgeHooks.java:292} 的 {@code onLivingHurt} 由
     * {@code net/minecraft/world/entity/LivingEntity.java:1665} 的 {@code actuallyHurt} 调用
     * ⇒ 与蓝图同为「事件已确认(过了无敌帧/无敌判定)、尚未扣血」的挂点,但位于**护甲结算之前**。
     * 任意伤害来源都算,含环境伤害与自伤 —— 文案即「受到伤害时」,刻意不做敌对/来源收紧。
     * 与保命无效化的 {@code ChipDamageHandler}(本线用 Forge 的 {@code LivingDamageEvent} + @LOWEST,
     * 最终伤害阶段)**互不干扰**:本方法只读事件、不改写伤害,冷却也记在独立附件
     * {@code airbag_hit_charge_cooldown_end} 上。
     * <p>⚠️ 由此产生的**唯一**语义偏差(如实登记):本线按「护甲结算前」判「是否受到伤害」
     * ⇒ 被护甲结算**完全抵消**的那一击(最终伤害 0)同样算「受到伤害」并 +1(1.21.1 侧 Pre 在护甲之后派发,该形态只计数
     * 已确认落地的伤害)。需要与蓝图逐字等价时,可改挂 Forge 最终阶段的 {@code LivingDamageEvent}
     * (同 {@code ChipDamageHandler});本批按任务口径保持 {@code LivingHurtEvent}。
     * <p>⚠️ 若这一击随后被气囊自己无效化(消耗 6 充能),本次 +1 照常发放 —— 两条能力各自结算,
     * 净变化为 -5 充能 + 两个冷却各自起算。
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (event.getAmount() <= 0.0F) return;
        if (!isEquipped(player)) return;
        long now = player.level().getGameTime();
        if (now < ModAttachments.getAirbagHitChargeCooldownEnd(player)) return;
        ModAttachments.setAirbagHitChargeCooldownEnd(player, now + HIT_CHARGE_COOLDOWN_TICKS);
        ChargeManager.addStacks(player, HIT_CHARGE_GAIN);
    }
}
