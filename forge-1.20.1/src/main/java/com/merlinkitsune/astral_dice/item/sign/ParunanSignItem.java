package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.shop.CardShopOpener;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 经商立牌(parunan,史诗)。
 *
 * <h3>主动「网络购物」</h3>
 * 右键 / 立牌栏按键:**远程打开卡牌商店**界面(购买卡牌、星光兑换星币、向其他在线玩家转账星币
 * 全部在界面内完成),本身不施加任何效果、不消耗星光。玩家级冷却 {@value #ACTIVE_COOLDOWN_SECONDS} 秒
 * ——与其余立牌的冷却链同款(充能上限封顶 → 诡异骰子 -50%)。
 *
 * <h3>被动「传奇商人」(2026-10-08 用户裁决,整条重写)</h3>
 * 五条内容**全部结算在** {@link com.merlinkitsune.astral_dice.shop.CardShopMenu}:
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
 * 开店入口 + {@code activeCooldownBaseTicks} 冷却链。
 *
 * <ol>
 *   <li>每次进入商店时获得 1 星币(**仅佩戴者本人**);</li>
 *   <li>自身与团队成员的商店中可购买战斗牌数量 +1(**全队光环**);</li>
 *   <li>自身与团队成员进入商店后,每张牌有 10%% 概率降至 1 星币(**全队光环**);</li>
 *   <li>转账后使当前商店内第一张未降价卡牌降至 1 星币(**仅佩戴者本人**);</li>
 *   <li>星光兑换额外获得 50%% 收入(**仅佩戴者本人**;截断后不足 1 枚时至少 +1)。</li>
 * </ol>
 *
 * <p>2026-10-08 用户裁决「重写经商立牌」:主动更名为**网络购物**(远程打开商店,冷却 120 秒不变);
 * 旧被动(每 60 秒 +1 星光、触发赐福时获得首次骰点 ×2 星光)与 {@code gainStarlightOnBlessing}
 * **已整条移除**,被动「传奇商人」改为商店/兑换向五条(进入商店 +1 星币、战斗牌 +1、10%% 降价、
 * 转账强制降价、星光兑换 +50%%),全部结算在 {@link com.merlinkitsune.astral_dice.shop.CardShopMenu}。
 */
public class ParunanSignItem extends BaseSignItem {

    /** 主动「网络购物」的玩家级冷却基数(秒)。 */
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

}

