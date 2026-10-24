package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.shop.ChipShopCatalog;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class AnvilUpgradeHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnvilUpgradeHandler.class);

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (!isDice(left)) return;
        if (!right.is(ModItems.STAR_COIN.get())) return;
        WeaponEnhancement enhancement = left.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
        int req = starCoinCost(enhancement.starLevel());
        if (req < 0) return;
        if (right.getCount() < req) return;
        ItemStack output = left.copy();
        output.set(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                new WeaponEnhancement(
                        enhancement.usedCost(),
                        GameplayConstants.cardCostForStar(enhancement.starLevel() + 1),
                        enhancement.usedDefenseCost(),
                        GameplayConstants.cardCostForStar(enhancement.starLevel() + 1),
                        enhancement.starLevel() + 1,
                        // ⚠️ appliedStones 必须**原样透传**(不重建):临时牌(绿洲女王 nardis)的
                        // temporary 分量存在 AppliedStone 里,重建/拷贝时丢标记 = 该牌到期清不掉。
                        enhancement.appliedStones()
                ));
        event.setOutput(output);
        event.setMaterialCost(req);
        event.setCost(10);
    }

    /**
     * 从铁砧**取走**升星成品时才授予 1 次筹码商店刷新（2026-10-24 用户裁决）。
     *
     * <p><b>为什么必须挂在「取走」而不是预览</b>：{@link AnvilUpdateEvent} 在每次点击/改名/悬停重算时
     * 都会触发（装备查询也可能间接触发），只有 {@link AnvilRepairEvent} 对应玩家真的把成品从输出槽拿走
     * 这一次。若挂在预览上，反复点铁砧、换装备或复制高星骰子都能白刷次数。
     *
     * <p><b>校验</b>（任一不满足即不授予）：服务端玩家；左槽与产物是**同一种**本模组骰子（`output.is(left.getItem())`）、
     * 右槽是星币；星币数量达到该星阶成本；输入星阶在可升范围（0..2★）；产物星阶恰好比输入 +1。
     *
     * <p><b>授予语义</b> = {@link ChipShopState#withRefreshGranted()}：在存档现有次数上 +1 并封顶
     * {@value ChipShopCatalog#REFRESH_MAX}，不重置、不清零（已有存档次数照旧保留）。
     */
    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        ItemStack output = event.getOutput();
        if (!isDice(left) || !isDice(output) || !output.is(left.getItem())) {
            return;
        }
        if (!right.is(ModItems.STAR_COIN.get())) {
            return;
        }
        WeaponEnhancement input = left.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
        WeaponEnhancement upgraded = output.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
        int required = starCoinCost(input.starLevel());
        if (required < 0) {
            return;
        }
        if (upgraded.starLevel() != input.starLevel() + 1) {
            return;
        }
        if (right.getCount() < required) {
            return;
        }
        ChipShopState next = ChipShopState.get(player).withRefreshGranted();
        ChipShopState.store(player, next);
        LOGGER.info("[astral_dice] CHIP_SHOP_REFRESH_GRANTED player={} star={}->{} coins={} refreshes={}/{}",
                player.getGameProfile().getName(), input.starLevel(), upgraded.starLevel(), right.getCount(),
                next.refreshesLeft(), ChipShopCatalog.REFRESH_MAX);
    }

    /** 升星白名单：全部 13 种骰子（基础/黄金/玻璃/下界岩/钻石/绿宝石/黑曜石/诡异/紫晶/下界合金/绯红/末影/下界之星） */
    private static boolean isDice(ItemStack stack) {
        return stack.is(ModItems.DICE.get()) || stack.is(ModItems.GOLDEN_DICE.get()) || stack.is(ModItems.DIAMOND_DICE.get())
                || stack.is(ModItems.NETHERITE_DICE.get()) || stack.is(ModItems.EMERALD_DICE.get())
                || stack.is(ModItems.GLASS_DICE.get()) || stack.is(ModItems.NETHERRACK_DICE.get())
                || stack.is(ModItems.OBSIDIAN_DICE.get()) || stack.is(ModItems.WEIRD_DICE.get())
                || stack.is(ModItems.AMETHYST_DICE.get()) || stack.is(ModItems.CRIMSON_DICE.get())
                || stack.is(ModItems.ENDER_DICE.get()) || stack.is(ModItems.NETHER_STAR_DICE.get());
    }

    /**
     * 升星星币成本：0★→15 / 1★→20 / 2★→25；不可升（≥3★ 或非法星阶）⇒ -1。
     *
     * <p>**预览与授予共用这一份**（{@link #onAnvilUpdate} 与 {@link #onAnvilRepair} 都调它），
     * 不得各写一套数值 —— 两套数值一旦漂移，铁砧预览价与实际授予条件就会不一致。
     */
    private static int starCoinCost(int starLevel) {
        return switch (starLevel) {
            case 0 -> 15;
            case 1 -> 20;
            case 2 -> 25;
            default -> -1;
        };
    }

    // ============ 立牌 tooltip 统一格式辅助 ============
    // 格式模板:
    //   <按键提示>
    //
    //   主动技能（技能名）:
    //   <标准项>
    //   <带子项>:
    //   - <子项>
    //   被动技能（技能名）:
    //   <标准项>
    //   <带子项>:
    //   - <子项>
    //
    //   <备注信息(紫色,无符号)>
    //
    //   <立牌计数器>
    // 颜色约定:见 ModTooltipHandler 类头「物品 tooltip 统一染色规则」
    //   (数值=黄 §e、时间=蓝 §9、效果条目「名 (时间)」整段蓝 §9;§c 红色语义保留、§r/§f 例外)

    // 主动技能按键提示:置于 tooltip 最上方独立一行,并在末尾追加一个空行
}
