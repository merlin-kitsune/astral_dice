package com.merlinkitsune.astral_dice.item.chip;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.compat.curios.SlotContext;

/**
 * 探天卫星筹码:
 * - 物品栏中"轨道炮"少于 6 张时,每 1:00 补充 1 张轨道炮;
 * - 使用一张"轨道炮"后,本轮出牌数 +1(每 1:00 至多触发一次);
 * - 佩戴即生效:效果牌的目标选择距离 +50%(2026-10-03 用户裁决,取代原「"轨道炮"生效期间,
 *   远程/魔法击杀敌方目标后获得一张随机效果牌」)。
 */
public class SatelliteChipItem extends BaseChipItem {
    /** 轨道炮库存目标数量 */
    public static final int TARGET_ORBITAL_STRIKE_COUNT = 6;
    /** 补充轨道炮间隔(tick,1 分钟) */
    public static final int GIVE_INTERVAL_TICKS = 1200;
    /** "使用轨道炮后出牌数+1"触发冷却(tick,1 分钟) */
    public static final int PLAY_BONUS_COOLDOWN_TICKS = 1200;

    /**
     * 「效果牌目标选择距离」加成(2026-10-03 用户裁决,取代原「击杀返还随机效果牌」):
     * **佩戴即生效**的常驻加成,对**全部效果牌**动作生效(立牌动作不受影响)。
     *
     * <p>唯一读取点 = {@code target/SelectorRangeModifiers}(与调查员立牌的「书页射程」状态按
     * **相加**合并,合计后按 {@code SelectorRangeModifiers#MAX_ENHANCED_RADIUS} = 64 格夹取)。
     * 0.5 = +50%。
     */
    public static final double EFFECT_CARD_RANGE_BONUS = 0.5D;

    public SatelliteChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴探天卫星
    public static boolean isEquipped(Player player) {
        // 总闸门(骰子装备和卸除调整):未佩戴骰子 ⇒ 本件功能一律不生效(数值保留)
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.SATELLITE_CHIP.get())).isPresent();
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 总闸门:未佩戴骰子 ⇒ 本筹码的持续效果不生效(数值保留)
        if (slotContext.entity() instanceof net.minecraft.world.entity.player.Player gatePlayer
                && !CurioSlotUtil.hasDiceEquipped(gatePlayer)) {
            return;
        }
        // 每 1:00 尝试补充轨道炮
        long now = player.level().getGameTime();
        long cdEnd = ModAttachments.getSatelliteGiveCooldownEnd(player);
        if (now < cdEnd) return;
        if (countOrbitalStrike(player) < TARGET_ORBITAL_STRIKE_COUNT) {
            ItemStack card = new ItemStack(ModItems.ORBITAL_STRIKE_CARD.get());
            // 统一经维生素药丸发牌路径(合成/获得卡牌联动)
            VitaminPillChipItem.giveCard(player, card);
        }
        ModAttachments.setSatelliteGiveCooldownEnd(player, now + GIVE_INTERVAL_TICKS);
    }

    // 使用一张"轨道炮"后调用:本轮出牌数 +1(每 1:00 至多触发一次;标记随效果牌周期归零清除)
    public static void onOrbitalStrikeUsed(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        long now = player.level().getGameTime();
        if (now < ModAttachments.getSatellitePlayBonusCooldownEnd(player)) return;
        if (ModAttachments.isSatellitePlayBonusActive(player)) return;
        ModAttachments.setSatellitePlayBonusActive(player, true);
        ModAttachments.setSatellitePlayBonusCooldownEnd(player, now + PLAY_BONUS_COOLDOWN_TICKS);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        // 卸下筹码:清除"本轮出牌数+1"标记(每 1:00 触发冷却保留,防装卸刷新)
        ModAttachments.setSatellitePlayBonusActive(player, false);
    }

    // 统计物品栏中轨道炮总数
    private static int countOrbitalStrike(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(ModItems.ORBITAL_STRIKE_CARD.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

}
