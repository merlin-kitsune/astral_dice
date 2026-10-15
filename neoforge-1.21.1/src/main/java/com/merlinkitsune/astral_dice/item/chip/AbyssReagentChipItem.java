package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 试剂-深渊筹码(史诗 / 治愈)。
 *
 * <ul>
 *   <li><b>装备时治愈层数 +2</b>,卸下 -2(「卸除即扣除」);
 *       <b>触发骰神赐福时 / 每 1:00 治愈结算时各 +2</b>(2026-10-15 用户裁决,与医疗箱同口径
 *       —— 由 {@link HealingManager#addChipPoints} 在两条结算入口统一追加);</li>
 *   <li><b>触发治愈回血时</b>:获得「有效治疗量 × {@value #ABSORPTION_RATIO}」的伤害吸收(黄心);
 *       有效治疗量 &lt; 1 时至少给 {@value #MIN_ABSORPTION}(= 1 颗黄心)。</li>
 * </ul>
 *
 * <p><b>装备发放的防重</b>:Curios 只持久化 stacks、不持久化 previousStacks ⇒ 登录 / 重生 / 切维度后
 * 会**重放 onEquip** ⇒ 用玩家级持久化闸门 {@link ModAttachments#claimAbyssReagentGrant}
 * (照医疗箱的 {@code claimMedkitEquipGrant} / StarLightManager 的 GRANT_BIT 模式)。层数一律经
 * {@link HealingManager#add} / {@link HealingManager#spend} 改动(卸下时 {@code spend(2)},
 * spend 自带下限 0 ⇒ 不会扣成负数)。
 * <p>⚠️ 本条按需求为**名义值**增删(不做"实际抬升量"账本):治愈点已到上限时装备不涨、卸下仍扣 2 ——
 * 与医疗箱/星光的按账本扣回口径不同,是用户对本批的明确取舍。
 *
 * <p><b>死亡</b>:治愈点由 {@link HealingManager#clear} 清零(= 本次装备会话结束)⇒ 本类在死亡时
 * 释放闸门,使重生后 Curios 重放 {@code onEquip} 能重新发放那 2 点。
 *
 * <p><b>伤害吸收(吸收上限,1.21.1 实测)</b>:{@code LivingEntity#setAbsorptionAmount} 会把入参钳到
 * {@code [0, getMaxAbsorption()]},而 {@code generic.max_absorption} 的**基础值是 0**
 * ({@code Attributes.java}: {@code RangedAttribute("generic.max_absorption", 0.0, 0.0, 2048.0)});
 * 原版吸收效果{@code MobEffects.ABSORPTION} 注册时挂了 {@code MAX_ABSORPTION += 4.0}(ADD_VALUE),
 * 且 {@code AttributeTemplate.create(level) = amount * (level + 1)} ⇒ **N 级 = 上限 4×(N+1)**。
 * 更关键的是 {@code AbsorptionMobEffect#onEffectStarted} 会**直接把数值顶到**
 * {@code max(当前, 4×(1+级))},而 {@code LivingEntity#addEffect} 里
 * {@code effectInstance.onEffectStarted(this)} 位于 if/else **之外** ⇒ **每次 addEffect 都会顶一次**。
 * 因此:
 * <ol>
 *   <li>「当前吸收」必须在 {@code addEffect} **之前**读(否则读到的是被顶高后的值,发放量会被吞掉);</li>
 *   <li>层数必须按目标值反推 {@code 4×(级+1) ≥ 目标},否则 {@code setAbsorptionAmount} 会把超出部分
 *       **静默截断**(固定 0 级只有 4 点上限,而"治愈量×2"极易超过 4)。</li>
 * </ol>
 * 本类按上述两条实现:按目标值定层数(且不低于已有层数,以便同层时原版 {@code update} 能刷新时长)、
 * 先读当前值、最后显式写入精确目标值。时长 {@value #ABSORPTION_DURATION_TICKS} tick(60 秒),
 * 每次触发治愈都会重新施加/刷新 ⇒ 期间持续有效。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class AbyssReagentChipItem extends BaseChipItem {
    /** 装备时提供的治愈层数(卸下按名义值扣回)。 */
    public static final int EQUIP_POINTS = 2;

    /**
     * 触发骰神赐福 / 每 1:00 治愈结算时各追加的治愈层数(2026-10-15 用户裁决:与医疗箱同口径)。
     * 由 {@link HealingManager#addChipPoints} 在 {@code onBlessingTriggered} 与 {@code onTimerEnded}
     * 两条入口统一追加(加点时机与医疗箱的 {@code MEDKIT_*_POINTS} 完全一致)。
     */
    public static final int BOON_POINTS = 2;

    /** 触发治愈回血时:伤害吸收 = 有效治疗量 × 该系数。 */
    public static final float ABSORPTION_RATIO = 2.0F;

    /** 有效治疗量 &lt; 1 时至少发放的吸收量(= 1 颗黄心)。 */
    public static final float MIN_ABSORPTION = 2.0F;

    /** 撑起吸收上限的原版吸收效果时长(60 秒)。 */
    public static final int ABSORPTION_DURATION_TICKS = 1200;

    /** 原版吸收效果每级提供的 MAX_ABSORPTION 上限(1.21.1:{@code MobEffects.ABSORPTION} 注册时 4.0 ADD_VALUE,按 level+1 倍)。 */
    private static final double ABSORPTION_CAP_PER_LEVEL = 4.0D;

    public AbyssReagentChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴本筹码(总闸门:未佩戴骰子 ⇒ 本件功能一律不生效)。 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.ABYSS_REAGENT_CHIP.get())).isPresent();
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // ⚠️ 不能用 prevStack 判"是否真的新装上"(Curios 重放时它恰好就是空栈)⇒ 走玩家级持久化闸门。
        if (!ModAttachments.claimAbyssReagentGrant(player)) return;
        HealingManager.add(player, EQUIP_POINTS);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        // 「卸除即扣除」:释放闸门 + 扣回名义 2 点(spend 自带下限 0)
        ModAttachments.releaseAbyssReagentGrant(player);
        HealingManager.spend(player, EQUIP_POINTS);
    }

    /** 死亡 = 本次装备会话结束(治愈点已被 HealingManager#clear 清零)⇒ 释放闸门,重生后重新发放。 */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        ModAttachments.releaseAbyssReagentGrant(player);
    }

    /**
     * 触发治愈回血时调用(通知点 = {@code HealingManager#triggerHealing})。
     *
     * @param effectiveHeal 有效治疗量 = {@code min(请求量, 最大生命 − 当前生命)}(口径照
     *                      {@code ZhaoSignItem#onLivingHeal}:在读 {@code getHealth()} 的治疗前时点求值)
     */
    public static void onHealingTriggered(Player player, float effectiveHeal) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        float grant = Math.max(MIN_ABSORPTION, effectiveHeal * ABSORPTION_RATIO);
        float before = player.getAbsorptionAmount(); // ⚠️ 必须在 addEffect 之前读(见类注释)
        float target = before + grant;
        MobEffectInstance current = player.getEffect(MobEffects.ABSORPTION);
        // 4×(级+1) ≥ 目标的**最低**层数;同时不低于已有层数 ⇒ 同层时原版 update 才会刷新时长
        int needed = Math.max(0, (int) Math.ceil(target / ABSORPTION_CAP_PER_LEVEL) - 1);
        int amplifier = Math.max(current == null ? 0 : current.getAmplifier(), needed);
        // 六参构造 =(效果, 时长, 等级, ambient, visible, showIcon):关粒子、保留 HUD 图标(模组口径)
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
                ABSORPTION_DURATION_TICKS, amplifier, false, false, true));
        // 显式写入精确目标值(上一步的"效果自带顶值"只是中间态;上限已由本次层数撑起 ≥ 目标)
        player.setAbsorptionAmount(Math.min(target, player.getMaxAbsorption()));
    }
}
