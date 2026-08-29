package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 附魔注册中心(1.20.1 Forge):1.21 的数据驱动附魔 curse_marker.json 改为代码注册。
 */
public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, AstralDiceMod.MODID);

    /**
     * 千咒刻印:诅咒之剑筹码的标记诅咒附魔(仅用于被千咒卷轴识别为 1 点诅咒,无其他效果)。
     * 仅由代码经 ItemStack.enchant 施加,不在附魔台/铁砧出现(BREAKABLE 类别对筹码不生效)。
     */
    public static final RegistryObject<Enchantment> CURSE_MARKER = ENCHANTMENTS.register("curse_marker",
            CurseMarkerEnchantment::new);

    public static class CurseMarkerEnchantment extends Enchantment {
        CurseMarkerEnchantment() {
            super(Rarity.VERY_RARE, EnchantmentCategory.BREAKABLE, EquipmentSlot.values());
        }

        @Override
        public boolean isCurse() {
            return true;
        }

        @Override
        public int getMinCost(int level) {
            return 1;
        }

        @Override
        public int getMaxCost(int level) {
            return 10;
        }
    }
}
