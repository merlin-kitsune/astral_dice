package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.shop.CardShopOpener;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 经商立牌(parunan,史诗)。
 *
 * <h3>被动「星光进项」</h3>
 * 佩戴期间每 {@link GameplayConstants#PARUNAN_PASSIVE_INTERVAL_SECONDS} 秒获得 1 点星光(上限由
 * {@link StarLightManager} 统一管理)。
 *
 * <h3>主动「开设卡牌商店」</h3>
 * 立牌栏按键:为自己打开卡牌商店界面(购买卡牌、星光兑换星币、向其他在线玩家转账星币
 * 全部在界面内完成),不再直接产出随机增益。玩家级冷却 {@value #ACTIVE_COOLDOWN_SECONDS} 秒
 * ——与其余立牌的冷却链同款(充能上限封顶 → 诡异骰子 -50%)。
 *
 * <p>2026-10-06 用户裁决:经商立牌原主动「星光兑换 + 三选一随机增益」整体迁入卡牌商店——
 * 星光兑换成为商店内的「星光兑换」按钮(2 点星光换 1 星币、**无冷却**),随机增益与
 * 锁定(生效中)态一并取消;立牌本身改为开店入口 + 120 秒冷却。
 *
 * <p><b>1.20.1 Forge 移植（按 1.21.1 新版语义整体重写本文件）</b>：删除了旧版主动的
 * 「星光兑换 + 三选一随机增益（饱和/幸运/村庄英雄）+ 锁定(生效中)态」全部相关覆写
 * （{@code LOCK_GATE_EFFECTS} / {@code startActiveLockOnUse} / {@code isGateEffectActive} /
 * {@code beginActiveLock} 调用 / {@code ResourceConversion} / {@code EffectTimerGuard} /
 * {@code ThreadLocalRandom} 等 import 一并移除），改为与 1.21.1 逐字同构的
 * 开店入口 + {@code activeCooldownBaseTicks} 冷却链；被动与
 * {@code gainStarlightOnBlessing} 原样保留。
 */
public class ParunanSignItem extends BaseSignItem {

    /** 主动「开设卡牌商店」的玩家级冷却基数(秒)。 */
    private static final int ACTIVE_COOLDOWN_SECONDS = 120;

    public ParunanSignItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴经商立牌(未佩戴骰子 ⇒ 本件被动/主动一律不生效,数值保留)。 */
    public static boolean isEquipped(Player player) {
        if (player == null || !CurioSlotUtil.hasDiceEquipped(player)) return false;
        // ⚠️ 平台差异:1.20.1 走库 shim CuriosCompat(返回 Optional),不用 CuriosApi(本线是 LazyOptional)
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.PARUNAN_SIGN.get())).isPresent();
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        long gameTime = player.level().getGameTime();
        if (gameTime % (GameplayConstants.PARUNAN_PASSIVE_INTERVAL_SECONDS * 20L) == 0) {
            // 被动:每 N 秒星光 +1(上限由 StarLightManager 统一管理)
            StarLightManager.add(player, 1);
        }
    }

    /**
     * 主动「开设卡牌商店」(服务端):打开 {@link com.merlinkitsune.astral_dice.shop.CardShopMenu},
     * 本身不消耗星光、不直接施加效果 ⇒ 不进锁定(生效中)态,{@code performSkill} 立即开始玩家级冷却。
     */
    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        CardShopOpener.open(player);
        return InteractionResultHolder.success(stack);
    }

    /**
     * 主动冷却 tick:佩戴时基础 {@value #ACTIVE_COOLDOWN_SECONDS} 秒 —— 与怪力侦探/枪匠立牌
     * **同款链**(先按充能上限封顶,再按诡异骰子减半)。
     */
    @Override
    protected int activeCooldownBaseTicks(Player player) {
        int ticks = isEquipped(player)
                ? ACTIVE_COOLDOWN_SECONDS * 20
                : GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
        ticks = (int) ChargeManager.signCooldownTicks(player, ticks);
        if (WeirdDiceHandler.hasWeirdDice(player)) {
            ticks = Math.max(1, ticks / 2);
        }
        return ticks;
    }

    // 触发骰神赐福后立即获得 骰点*2 星光(上限由 StarLightManager 统一管理)
    public static void gainStarlightOnBlessing(Player player, int dicePoint) {
        if (player.level().isClientSide()) return;
        StarLightManager.add(player, dicePoint * 2);
    }
}
