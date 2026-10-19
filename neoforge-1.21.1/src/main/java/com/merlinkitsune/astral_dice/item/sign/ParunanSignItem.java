package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.shop.CardShopOpener;
import com.merlinkitsune.starengine.component.GameplayConstants;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;

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
 * <ol>
 *   <li>每次进入商店时获得 1 星币(**仅佩戴者本人**);</li>
 *   <li>自身与团队成员的商店中可购买战斗牌数量 +1(**全队光环**);</li>
 *   <li>自身与团队成员进入商店后,每张牌有 10% 概率降至 1 星币(**全队光环**);</li>
 *   <li>转账后使当前商店内第一张未降价卡牌降至 1 星币(**仅佩戴者本人**);</li>
 *   <li>星光兑换额外获得 50% 收入(**仅佩戴者本人**;截断后不足 1 枚时至少 +1)。</li>
 * </ol>
 *
 * <p>⚠️ 旧被动「每 60 秒 +1 星光 / 触发骰神赐福时获得首次骰点 ×2 星光」与
 * {@code gainStarlightOnBlessing} 已按同一次用户裁决**整条移除**,本类不再产出星光。
 *
 * <p>沿革:2026-10-06 用户裁决把原主动「星光兑换 + 三选一随机增益」整体迁入卡牌商店
 * (随机增益与锁定(生效中)态一并取消);2026-10-08 再按新裁决重写主动名与整条被动。
 */
public class ParunanSignItem extends BaseSignItem {

    /** 主动「网络购物」的玩家级冷却基数(秒)。 */
    private static final int ACTIVE_COOLDOWN_SECONDS = 120;

    public ParunanSignItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴经商立牌(未佩戴骰子 ⇒ 本件被动/主动一律不生效,数值保留)。 */
    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.PARUNAN_SIGN.get());
    }

    /**
     * 主动「网络购物」(服务端):打开 {@link com.merlinkitsune.astral_dice.shop.CardShopMenu},
     * 本身不消耗星光、不直接施加效果 ⇒ 不进锁定(生效中)态,{@code performSkill} 立即开始玩家级冷却。
     * 首次进入商店的「+1 星币」等被动由商店侧结算(见 {@link com.merlinkitsune.astral_dice.shop.CardShopMenu})；
     * 任务T §3 起**主动技能**这条路必定发 1 星币（方块交互仍为每批至多一次，两者不叠加）。
     */
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 任务T §3：主动技能进店 —— 商店侧必定发 1 星币（不看本批是否已领取）
        CardShopOpener.open(player, true);
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

