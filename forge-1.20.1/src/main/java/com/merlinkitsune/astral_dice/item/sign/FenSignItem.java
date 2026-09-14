package com.merlinkitsune.astral_dice.item.sign;
import com.merlinkitsune.astral_dice.item.CuriosCompat;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 大当家立牌(命名:fen)。
 * <p>计数器:养精蓄锐(玩家级附件 fen_recharge,上限 {@value #MAX_RECHARGE} 层)。
 * <p>被动:
 * <ul>
 *   <li>拥有养精蓄锐(层数 &gt; 0)时,攻击力 +2、防御力 +2(DiceCombatModifiers 修饰器,动态判断);</li>
 *   <li>1 分钟内没有触发骰神赐福 → 养精蓄锐 +1 层(tick 驱动);</li>
 *   <li>触发骰神赐福 → 养精蓄锐 -1 层;若触发时已满 {@value #MAX_RECHARGE} 层,则改为
 *       **消耗 {@value #SPLASH_COST} 层**,并在**本次攻击结算时立即**引爆一次
 *       "战斗爽·溅射"(**单次效果**):对目标及其 {@value #SPLASH_RANGE} 格范围内的敌对目标
 *       (**含主目标**)造成本次攻击伤害 80%(**下限 5 点**)的爆炸伤害;</li>
 *   <li>使用治疗类效果牌 → 养精蓄锐 +1 层(BaseEffectCardItem 钩子)。</li>
 * </ul>
 * <p>主动"战斗爽"(1:00):攻击力 +3;若拥有养精蓄锐则恢复 6 点血量并获得迅捷 1:00。
 * <p>旧的"主动消耗 2 层 → 下次骰神赐福期间持续扩散"已移除:溅射改为**被动触发 + 单次生效**。
 */
public class FenSignItem extends BaseSignItem {
    /** 养精蓄锐上限 */
    public static final int MAX_RECHARGE = 5;
    /** 战斗爽持续时长(tick) */
    public static final int FRENZY_DURATION_TICKS = 1200;
    /** 主动恢复生命值 */
    public static final int ACTIVE_HEAL = 6;
    /** 战斗爽·溅射消耗的养精蓄锐层数(满层触发时替代常规的 -1 层) */
    public static final int SPLASH_COST = 2;
    /** 溅射范围(格):目标及其附近,含主目标本身 */
    public static final double SPLASH_RANGE = 3.0;
    /** 溅射伤害比例(本次攻击伤害的 80%) */
    public static final double SPLASH_RATIO = 0.8;
    /** 溅射伤害下限(点):本次攻击伤害过低时按此值结算(高于全局"不足 1 按 1"的兜底) */
    public static final double SPLASH_DAMAGE_MIN = 5.0;

    public FenSignItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!prevStack.isEmpty()) return;
        // 装备时重置"1 分钟未触发赐福"计时起点
        ModAttachments.setFenLastBlessingTick(player, player.level().getGameTime());
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动"战斗爽":攻击力 +3,持续 1:00(visible=true 使效果图标在 HUD 正常显示)
        player.addEffect(new MobEffectInstance(ModEffects.FEN_FRENZY.get(),
                FRENZY_DURATION_TICKS, 0, false, true, true));

        int stacks = ModAttachments.getFenRecharge(player);
        // 若拥有养精蓄锐:恢复 6 点血量并获得迅捷 1:00
        if (stacks > 0) {
            player.heal(ACTIVE_HEAL);
            EffectTimerGuard.apply(player, new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    FRENZY_DURATION_TICKS, 0, false, true));
        }
        // 注:"战斗爽·溅射"已移至被动(触发骰神赐福且满层时消耗 2 层),主动不再消耗层数
        return InteractionResultHolder.success(stack);
    }

    // 玩家是否佩戴大当家立牌
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.FEN_SIGN.get())).isPresent();
    }

    // 使用治疗类效果牌时调用(佩戴立牌且未满上限时):养精蓄锐 +1 层
    public static void onHealingCardUsed(Player player) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (ModAttachments.getFenRecharge(player) >= MAX_RECHARGE) return;
        ModAttachments.setFenRecharge(player, ModAttachments.getFenRecharge(player) + 1);
    }

    /**
     * 触发骰神赐福时调用(DiceCombatEvents triggeredBlessing 块):记录触发时刻;
     * 佩戴立牌时养精蓄锐 -1 层(下限 0);**已满 {@value #MAX_RECHARGE} 层时返回 true 且不在此扣层**,
     * 表示本次攻击应在伤害定稿后立即引爆一次溅射——代价由 {@link #consumeSplashCost} 在
     * 溅射真正生效时扣除(攻击若在结算前被取消,例如 PvP 闪避导致事件提前 return,层数不会被白扣)。
     *
     * <p>返回值由调用方(DiceCombatEvents)保存在**本次事件的局部变量**里,不落附件:
     * 溅射天生只属于"触发它的这一次攻击",用局部变量表达"单次效果"最直接,
     * 也不会出现跨攻击残留(旧实现的"待命/生效"两个附件已删除)。
     *
     * @return 本次攻击是否应引爆"战斗爽·溅射"
     */
    public static boolean onBlessingTriggered(Player player) {
        if (player.level().isClientSide()) return false;
        long now = player.level().getGameTime();
        ModAttachments.setFenLastBlessingTick(player, now);
        if (!isEquipped(player)) return false;
        int stacks = ModAttachments.getFenRecharge(player);
        if (stacks >= MAX_RECHARGE) {
            return true;
        }
        if (stacks > 0) {
            ModAttachments.setFenRecharge(player, stacks - 1);
        }
        return false;
    }

    /**
     * 扣除"战斗爽·溅射"的养精蓄锐代价({@value #SPLASH_COST} 层,下限 0),
     * 由 DiceCombatEvents 在**真正引爆溅射之前**调用。
     *
     * <p>与 {@link #onBlessingTriggered} 分开的原因与电磁炮一致:只有效果真正发生才付代价——
     * 攻击若在结算前被取消(如 PvP 闪避提前 return),层数不会被白白扣掉。
     */
    public static void consumeSplashCost(Player player) {
        if (player == null || player.level().isClientSide()) return;
        int stacks = ModAttachments.getFenRecharge(player);
        ModAttachments.setFenRecharge(player, Math.max(0, stacks - SPLASH_COST));
    }

    /**
     * 每 20 tick 驱动(PlayerTickEvents.onPlayerTick):1 分钟内没有触发骰神赐福 → 养精蓄锐 +1 层。
     * 计时起点:装备立牌时(onEquip)或首次 tick 惰性初始化。
     */
    public static void tick(Player player) {
        if (player.level().isClientSide()) return;
        // 被动:拥有养精蓄锐层数时防御力 +2 → 护甲 +4(经 ARMOR 属性;层数/卸下后自动移除)
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "fen_def_armor", isEquipped(player) && ModAttachments.getFenRecharge(player) > 0 ? 2 : 0);
        long now = player.level().getGameTime();
        long last = ModAttachments.getFenLastBlessingTick(player);
        if (last <= 0) {
            ModAttachments.setFenLastBlessingTick(player, now);
            return;
        }
        if (!isEquipped(player)) return;
        if (now - last < 1200) return;
        if (ModAttachments.getFenRecharge(player) >= MAX_RECHARGE) return;
        ModAttachments.setFenRecharge(player, ModAttachments.getFenRecharge(player) + 1);
        // 重新计时
        ModAttachments.setFenLastBlessingTick(player, now);
    }
}
