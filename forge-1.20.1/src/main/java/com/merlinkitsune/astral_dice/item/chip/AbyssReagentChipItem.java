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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 试剂-深渊筹码(史诗 / 治愈)。
 *
 * <ul>
 *   <li><b>装备时治愈层数 +2</b>,卸下 -2(「卸除即扣除」);</li>
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
 * <p><b>伤害吸收(1.20.1 Forge 实测;与 1.21.1 的差异见下)</b>:1.21.1 侧的
 * {@code LivingEntity#setAbsorptionAmount} 会把入参钳到 {@code [0, getMaxAbsorption()]},
 * 而 {@code generic.max_absorption} 的**基础值是 0**、原版吸收效果注册时挂 {@code MAX_ABSORPTION += 4.0}
 * 并按 {@code (level + 1)} 倍放大 ⇒ 那一侧必须「按目标值反推层数」并「最后写入 {@code min(目标, 上限)}」。
 * <p>⚠️ <b>本线(forge-1.20.1)没有该上限,故简化</b>。取证(只读开包,来源 =
 * {@code forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-sources.jar}):
 * <ol>
 *   <li>{@code net/minecraft/world/entity/LivingEntity.java} 的
 *       {@code setAbsorptionAmount(float)} 只做 {@code if (absorptionAmount < 0.0F) absorptionAmount = 0.0F;}
 *       后直接写字段 ⇒ **不读任何上限**;</li>
 *   <li>{@code net/minecraft/world/entity/ai/attributes/Attributes.java} **没有** {@code MAX_ABSORPTION} 属性
 *       ⇒ 1.20.1 也没有 {@code getMaxAbsorption()} 可调用(本类因此不再做上限钳制);</li>
 *   <li>原版 {@code AbsoptionMobEffect} 经 {@code addAttributeModifiers} / {@code removeAttributeModifiers}
 *       直接 {@code ±4×(amplifier + 1)} 改数值,且 {@code LivingEntity#addEffect} 的新增分支
 *       ({@code onEffectAdded}) 会先加一次 ⇒ **「先读当前值 → addEffect → 最后显式写入精确值」的顺序仍必须保留**
 *       (否则本次 addEffect 加进来的 {@code 4×(级+1)} 会被当成玩家原有吸收,发放量被吞掉)。</li>
 * </ol>
 * 与 1.21.1 的唯一差别 = 最后一步**不必**再与上限取 min:直接 {@code setAbsorptionAmount(目标值)}。
 * 层数仍按目标值反推(作用变为:让效果到期时 {@code removeAttributeModifiers} 的扣减量与本次发放量相当;
 * 且不低于已有层数 ⇒ 同层时原版 {@code MobEffectInstance#update} 才会刷新时长)。
 * 时长 {@value #ABSORPTION_DURATION_TICKS} tick(60 秒),每次触发治愈都会重新施加/刷新 ⇒ 期间持续有效。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class AbyssReagentChipItem extends BaseChipItem {
    /** 装备时提供的治愈层数(卸下按名义值扣回)。 */
    public static final int EQUIP_POINTS = 2;

    /** 触发治愈回血时:伤害吸收 = 有效治疗量 × 该系数。 */
    public static final float ABSORPTION_RATIO = 2.0F;

    /** 有效治疗量 &lt; 1 时至少发放的吸收量(= 1 颗黄心)。 */
    public static final float MIN_ABSORPTION = 2.0F;

    /** 撑起吸收上限的原版吸收效果时长(60 秒)。 */
    public static final int ABSORPTION_DURATION_TICKS = 1200;

    /** 原版吸收效果每级对应的数值增量(1.21.1 = MAX_ABSORPTION 上限;1.20.1 = {@code AbsoptionMobEffect} 的 {@code ±4×(级+1)})。 */
    private static final double ABSORPTION_CAP_PER_LEVEL = 4.0D;

    public AbyssReagentChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴本筹码(总闸门:未佩戴骰子 ⇒ 本件功能一律不生效)。 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
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
        // 显式写入精确目标值(上一步 addEffect 自带的 +4×(级+1) 只是中间态)
        // ⚠️ 平台差异:1.20.1 **没有** MAX_ABSORPTION 上限(setAbsorptionAmount 只把负数钳到 0)⇒ 直接写入精确值,
        //    不再做 1.21.1 那条 min(目标, getMaxAbsorption()) 的钳制(该 API 在 1.20.1 不存在)。
        player.setAbsorptionAmount(target);
    }
}
