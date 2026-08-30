package com.merlinkitsune.astral_dice.item.sign;

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
 *   <li>触发骰神赐福 → 养精蓄锐 -1 层;</li>
 *   <li>使用治疗类效果牌 → 养精蓄锐 +1 层(BaseEffectCardItem 钩子)。</li>
 * </ul>
 * <p>主动"战斗爽"(1:00):攻击力 +3;若拥有养精蓄锐则恢复 6 点血量并获得迅捷 1:00;
 * 若养精蓄锐已达 5 层,消耗 2 层,并在下次骰神赐福期间将每次攻击造成的总伤害的 80%
 * 施加给目标 6 格范围内其他敌对目标(战斗爽·扩散),持续到本次骰神赐福结束。
 */
public class FenSignItem extends BaseSignItem {
    /** 养精蓄锐上限 */
    public static final int MAX_RECHARGE = 5;
    /** 战斗爽持续时长(tick) */
    public static final int FRENZY_DURATION_TICKS = 1200;
    /** 主动恢复生命值 */
    public static final int ACTIVE_HEAL = 6;
    /** 扩散范围(格) */
    public static final double CLEAVE_RANGE = 6.0;
    /** 扩散伤害比例(每次攻击总伤害的 80%) */
    public static final double CLEAVE_RATIO = 0.8;

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
        player.addEffect(new MobEffectInstance(ModEffects.FEN_FRENZY,
                FRENZY_DURATION_TICKS, 0, false, true, true));

        int stacks = ModAttachments.getFenRecharge(player);
        // 若拥有养精蓄锐:恢复 6 点血量并获得迅捷 1:00
        if (stacks > 0) {
            player.heal(ACTIVE_HEAL);
            EffectTimerGuard.apply(player, new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    FRENZY_DURATION_TICKS, 0, false, true));
        }
        // 若养精蓄锐已达 5 层:消耗 2 层,下次骰神赐福期间触发"战斗爽·扩散"
        if (stacks >= MAX_RECHARGE) {
            ModAttachments.setFenRecharge(player, stacks - 2);
            ModAttachments.setFenCleavePending(player, true);
        }
        return InteractionResultHolder.success(stack);
    }

    // 玩家是否佩戴大当家立牌
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
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
     * 触发骰神赐福时调用(DiceCombatEvents triggeredBlessing 块):
     * 记录触发时刻;佩戴立牌时养精蓄锐 -1 层(下限 0);
     * 若"战斗爽·扩散"待命,则本次赐福启用(持续到赐福结束)。
     */
    public static void onBlessingTriggered(Player player) {
        if (player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        ModAttachments.setFenLastBlessingTick(player, now);
        if (!isEquipped(player)) return;
        int stacks = ModAttachments.getFenRecharge(player);
        if (stacks > 0) {
            ModAttachments.setFenRecharge(player, stacks - 1);
        }
        if (ModAttachments.isFenCleavePending(player)) {
            ModAttachments.setFenCleavePending(player, false);
            ModAttachments.setFenCleaveActive(player, true);
        }
    }

    /**
     * 每 20 tick 驱动(PlayerTickEvents.onPlayerTick):1 分钟内没有触发骰神赐福 → 养精蓄锐 +1 层。
     * 计时起点:装备立牌时(onEquip)或首次 tick 惰性初始化。
     */
    public static void tick(Player player) {
        if (player.level().isClientSide()) return;
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

    // 骰神赐福结束时调用(DiceCombatEvents.onDiceBlessingExpired):清除"战斗爽·扩散"生效状态
    public static void onBlessingEnd(Player player) {
        if (player.level().isClientSide()) return;
        ModAttachments.setFenCleaveActive(player, false);
    }

    // "战斗爽·扩散"是否在本次赐福期间生效(玩家级附件驱动,不随卸下立牌失效)
    public static boolean isCleaveActive(Player player) {
        return player != null && ModAttachments.isFenCleaveActive(player);
    }
}
