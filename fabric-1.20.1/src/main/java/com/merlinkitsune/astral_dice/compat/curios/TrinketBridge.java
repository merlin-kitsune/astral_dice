package com.merlinkitsune.astral_dice.compat.curios;

import com.merlinkitsune.astral_dice.AstralDiceMod;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.Trinket;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * {@link ICurioItem} → Trinkets {@code Trinket} 的桥。
 *
 * <p>在 mod 初始化阶段调用 {@link #registerAll()}:遍历本模组命名空间下的全部物品,
 * 把实现了 {@link ICurioItem} 的逐个注册进 Trinkets。**只需一处调用**,
 * 137 件物品各自的方法体不用重复写 Trinkets 样板。
 *
 * <p>回调映射与「易错的参数语义」见 {@link ICurioItem} 的类注释
 * (核心:{@code onEquip} 的 prevStack、{@code onUnequip} 的 newStack 在 Trinkets 侧一律补
 * {@link ItemStack#EMPTY},以保持本模组既有判据的语义)。
 */
public final class TrinketBridge {

    private static boolean registered = false;

    /** 为所有本模组的 {@link ICurioItem} 注册 Trinkets 适配器(幂等)。 */
    public static void registerAll() {
        if (registered) {
            return;
        }
        registered = true;
        int count = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!AstralDiceMod.MODID.equals(id.getNamespace())) {
                continue;
            }
            if (item instanceof ICurioItem curio) {
                TrinketsApi.registerTrinket(item, new Adapter(curio));
                count++;
            }
        }
        AstralDiceMod.LOGGER.info("[Astral Dice] 已为 {} 件饰品物品注册 Trinkets 适配器", count);
    }

    private static SlotContext context(SlotReference reference, LivingEntity entity) {
        String slotName = reference.inventory().getSlotType().getName();
        return new SlotContext(entity, slotName, reference.index());
    }

    private record Adapter(ICurioItem delegate) implements Trinket {

        @Override
        public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
            delegate.curioTick(context(slot, entity), stack);
        }

        @Override
        public void onEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
            // Trinkets 只给「刚装上的物品」⇒ prevStack 补 EMPTY(Curios 的槽位原内容语义)
            delegate.onEquip(context(slot, entity), ItemStack.EMPTY, stack);
        }

        @Override
        public void onUnequip(ItemStack stack, SlotReference slot, LivingEntity entity) {
            // Trinkets 只给「被卸下的那件」⇒ newStack 补 EMPTY(Curios 的「将要占用槽位的栈」语义)
            delegate.onUnequip(context(slot, entity), ItemStack.EMPTY, stack);
        }

        @Override
        public boolean canEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
            return delegate.canEquip(context(slot, entity), stack);
        }

        @Override
        public boolean canUnequip(ItemStack stack, SlotReference slot, LivingEntity entity) {
            return delegate.canUnequip(context(slot, entity), stack);
        }
    }

    private TrinketBridge() {
    }
}
