package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;

public class RinSignItem extends BaseSignItem {

    public RinSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 移除立牌时重置调查员(rin)已使用的活体书页数量
        ModAttachments.setRinPages(player, 0);
    }

    /**
     * 是否佩戴本立牌(饰品槽)。死亡保留的累计值只在佩戴时作为加成生效(2026-09-15 裁决)——
     * 判定入口统一在 {@code SpellDamageRegistry},禁止在别处直接读原附件值做加成或显示加成。
     */
    public static boolean isEquipped(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.RIN_SIGN.get())).isPresent();
    }

    @Override
    protected InteractionResult handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // 主动:获得一张"活体书页"(专属牌,绑定获得者);若使用前物品栏中无活体书页则共获得两张
        int giveCount = countLivingPages(player) == 0 ? 2 : 1;
        for (int i = 0; i < giveCount; i++) {
            ItemStack page = new ItemStack(ModItems.LIVING_PAGE.get());
            ExclusiveCardUtil.setOwner(page, player);
            VitaminPillChipItem.giveCard(player, page);
        }
        return InteractionResult.SUCCESS;
    }

    // 统计物品栏中的活体书页数量
    private static int countLivingPages(Player player) {
        int count = 0;
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (!s.isEmpty() && s.is(ModItems.LIVING_PAGE.get())) {
                count += s.getCount();
            }
        }
        return count;
    }
}
