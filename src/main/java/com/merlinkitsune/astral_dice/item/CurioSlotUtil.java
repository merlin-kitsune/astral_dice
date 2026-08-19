package com.merlinkitsune.astral_dice.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;

/**
 * 栏位校验与装备工具:立牌/筹码/骰子只能放入各自原本的饰品栏,
 * 提供下蹲右键自动装备与重复装备限制的通用逻辑。
 */
public final class CurioSlotUtil {
    private CurioSlotUtil() {
    }

    // 该物品是否允许放入指定栏位;非本模组物品一律放行
    public static boolean isAllowedInSlot(ItemStack stack, String slotId) {
        if (DiceCurioItem.isDiceItem(stack)) return "dice".equals(slotId);
        if (stack.getItem() instanceof BaseSignItem) return "stand".equals(slotId);
        if (ModItems.isChipItem(stack)) return "chip".equals(slotId);
        return true;
    }

    // 是否已装备了与给定物品相同的物品(遍历玩家全部 Curios 槽位)
    // 排除"与传入栈引用相同"的槽位物品:Curios 对已装备物品重新校验 canEquip 时,
    // 传入的栈就是槽位中的栈本身,若不排除会误判"重复装备"导致物品被 Curios 弹出。
    public static boolean hasSameItemEquipped(LivingEntity entity, ItemStack stack) {
        var curios = CuriosApi.getCuriosInventory(entity);
        if (curios.isEmpty()) return false;
        var handler = curios.get();
        var curiosMap = handler.getCurios();
        for (var key : curiosMap.keySet()) {
            var stacks = curiosMap.get(key).getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                var s = stacks.getStackInSlot(i);
                if (!s.isEmpty() && s != stack && s.is(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }

    // 下蹲右键自动装备:将手中物品放入指定饰品栏的第一个空槽位(仅服务端执行)
    public static InteractionResultHolder<ItemStack> tryAutoEquip(Player player, ItemStack stack, String slotId) {
        if (player.level().isClientSide()) {
            return InteractionResultHolder.pass(stack);
        }
        // 重复装备限制:同类型饰品已装备时不允许自动装备
        if (hasSameItemEquipped(player, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }
        var handlerOpt = curios.get().getStacksHandler(slotId);
        if (handlerOpt.isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }
        var handler = handlerOpt.get();
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStacks().getStackInSlot(i).isEmpty()) {
                handler.getStacks().setStackInSlot(i, stack.copy());
                stack.shrink(1);
                return InteractionResultHolder.success(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    // 是否"真正卸下"物品(而非 Curios 重载 from=to 同一物品仍留在槽位)
    public static boolean isRealUnequip(SlotContext slotContext, ItemStack stack, LivingEntity entity) {
        return !CuriosApi.getCuriosInventory(entity)
                .flatMap(h -> h.getStacksHandler(slotContext.identifier()))
                .map(h -> slotContext.index() < h.getSlots()
                        && !h.getStacks().getStackInSlot(slotContext.index()).isEmpty()
                        && h.getStacks().getStackInSlot(slotContext.index()).getItem() == stack.getItem())
                .orElse(false);
    }

    // 仅在"真正卸下"时执行动作(排除 Curios 重载场景,防止累计值被反复清零)
    public static void runOnRealUnequip(SlotContext slotContext, ItemStack stack, LivingEntity entity,
                                        java.util.function.Consumer<Player> action) {
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (isRealUnequip(slotContext, stack, entity)) {
            action.accept(player);
        }
    }
}
