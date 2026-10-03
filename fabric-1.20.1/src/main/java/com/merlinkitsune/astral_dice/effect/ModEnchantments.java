package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import com.merlinkitsune.astral_dice.platform.registry.DeferredRegister;
// TODO-PORT-IMPORT net.minecraft.core.registries.BuiltInRegistries
import com.merlinkitsune.astral_dice.platform.registry.RegistryObject;

/**
 * 附魔注册中心(1.20.1 Forge):1.21 的数据驱动附魔 curse_marker.json 改为代码注册。
 */
public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ENCHANTMENT, AstralDiceMod.MODID);

    /**
     * 青之诅咒:诅咒之剑筹码的标记诅咒附魔(仅用于被千咒卷轴识别为 1 点诅咒,无其他效果)。
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

        /**
         * 内部标记附魔:不出现在附魔台候选(原版 {@code isDiscoverable})。
         * 与「附魔书条目已从创造栏摘除」「{@code EnchantmentCategory.BREAKABLE} 既不含书、也不含无耐久的筹码」
         * 两条口径合起来,玩家无法通过附魔台或附魔书正常获得它。
         *
         * <p>⚠️ <b>本线只覆写这一个</b>:Forge 侧的 {@code isAllowedOnBooks} /
         * {@code canApplyAtEnchantingTable} 都是 Forge 扩展(`IForgeEnchantment`)才有的方法,
         * Fabric 线走原版 API,覆写它们会直接编译失败(实测)。
         *
         * <p>功能不受影响 —— 千咒卷轴按 {@code isCurse()} 计数。
         */
        @Override
        public boolean isDiscoverable() {
            return false;
        }
    }
}
