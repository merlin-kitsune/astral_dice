package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class AnvilUpgradeHandler {
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        // 升星白名单覆盖全部 13 种骰子(基础/黄金/玻璃/下界岩/钻石/绿宝石/黑曜石/诡异/紫晶/下界合金/绯红/末影/下界之星)
        if (left.is(ModItems.DICE.get()) || left.is(ModItems.GOLDEN_DICE.get()) || left.is(ModItems.DIAMOND_DICE.get())
                || left.is(ModItems.NETHERITE_DICE.get()) || left.is(ModItems.EMERALD_DICE.get())
                || left.is(ModItems.GLASS_DICE.get()) || left.is(ModItems.NETHERRACK_DICE.get())
                || left.is(ModItems.OBSIDIAN_DICE.get()) || left.is(ModItems.WEIRD_DICE.get())
                || left.is(ModItems.AMETHYST_DICE.get()) || left.is(ModItems.CRIMSON_DICE.get())
                || left.is(ModItems.ENDER_DICE.get()) || left.is(ModItems.NETHER_STAR_DICE.get())) {
            if (!right.is(ModItems.STAR_COIN.get())) return;
            WeaponEnhancement enhancement = left.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
            if (enhancement.starLevel() >= 3) return;
            int req = switch (enhancement.starLevel()) {
                case 0 -> 15;
                case 1 -> 20;
                case 2 -> 25;
                default -> -1;
            };
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
            return;
        }
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
