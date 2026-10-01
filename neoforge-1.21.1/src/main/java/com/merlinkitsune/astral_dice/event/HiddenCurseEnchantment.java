package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 「青之诅咒」附魔(注册名 {@code astral_dice:curse_marker})的隐藏口径。
 *
 * <p>该附魔是本模组的**内部标记**:由 {@code CursedSwordChipItem} 在装备/每 tick 时经
 * {@code ItemStack#enchant} 写入,唯一用途是让「千咒卷轴」把它计入 1 点诅咒(见 AGENTS.md
 * 「青之诅咒计数口径」)。它不对玩家展示,也不应被玩家获得,故做三处隐藏:
 *
 * <ol>
 *   <li><b>创造栏 / JEI</b>:原版会为**每一个**附魔自动生成附魔书条目(1.21.1 见
 *       {@code CreativeModeTabs#generateEnchantmentBookTypesOnlyMaxLevel} 与
 *       {@code ...#generateEnchantmentBookTypesAllLevels},分别以 PARENT_TAB_ONLY 与
 *       SEARCH_TAB_ONLY 加入到「材料」页与「搜索」页;1.20.1 同构),JEI 又按创造栏索引物品
 *       ⇒ 两份条目都要摘掉,缺一个都会在对应界面留下残影。</li>
 *   <li><b>tooltip</b>:{@code ModTooltipHandler#onItemTooltip} 调用
 *       {@link #stripFromTooltip},抹掉原版自动追加的附魔行。</li>
 *   <li><b>附魔台 / 铁砧</b>:本线为**数据驱动**附魔:它既不在 {@code #minecraft:in_enchanting_table} 标签内,其 {@code supported_items}({@code #astral_dice:chips})也不含书 —— 附魔台对「筹码」与「书」两条通道都不产出;铁砧又只能从附魔书得到,而附魔书已被本类摘掉 ⇒ 无需额外代码。</li>
 * </ol>
 *
 * <p><b>隐藏只影响「可见 / 可获得」,不影响功能</b>:{@code data/minecraft/tags/enchantment/curse.json}
 * 里仍然保留本附魔(1.21 线千咒卷轴按该标签计数、1.20.1 线按 {@code Enchantment#isCurse()}),
 * 任何一处都不能删。同理,本类也不得改动附魔的注册名与数据文件。
 */

@EventBusSubscriber(modid = AstralDiceMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class HiddenCurseEnchantment {
    /** 与 {@code CursedSwordChipItem#CURSE_MARKER_KEY} 同源的资源键。 */
    public static final ResourceKey<Enchantment> CURSE_MARKER_KEY =
            ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "curse_marker"));

    private HiddenCurseEnchantment() {
    }

    /** 该 Holder 是否为「青之诅咒」附魔。 */
    public static boolean isCurseMarker(Holder<Enchantment> holder) {
        return holder.unwrapKey().map(key -> key.equals(CURSE_MARKER_KEY)).orElse(false);
    }

    /**
     * 该栈是否为「青之诅咒」附魔书。
     * <p>判据只认 {@code STORED_ENCHANTMENTS} 组件 —— 该组件**仅附魔书会有**,故无需再判物品,
     * 也就避开了各版本附魔书物品/类名的漂移(26.1.2 已无 {@code EnchantedBookItem})。
     */
    private static boolean isCurseBook(ItemStack stack) {
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return false;
        for (Holder<Enchantment> holder : stored.keySet()) {
            if (isCurseMarker(holder) && stored.getLevel(holder) > 0) return true;
        }
        return false;
    }

    /**
     * 抹掉 tooltip 里由原版自动追加的「青之诅咒」附魔行。
     * <p>行内容用与原版 {@code ItemEnchantments#addToTooltip} **同一个函数**
     * ({@code Enchantment#getFullname})构造,因此内容与样式一致;比对用纯文本,
     * 以兼容第三方 tooltip 模组对组件结构的改写。
     */
    public static void stripFromTooltip(ItemStack stack, List<Component> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) return;
        ItemEnchantments enchantments = stack.getEnchantments();
        if (enchantments.isEmpty()) return;
        Set<String> hidden = null;
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (!isCurseMarker(holder)) continue;
            int level = enchantments.getLevel(holder);
            if (level <= 0) continue;
            if (hidden == null) hidden = new HashSet<>();
            hidden.add(Enchantment.getFullname(holder, level).getString());
        }
        if (hidden == null) return;
        final Set<String> lines = hidden;
        tooltip.removeIf(line -> lines.contains(line.getString()));
    }

    /**
     * 创造栏去条目(同时是 JEI 的物品索引源)。
     * <p>只处理 parent / search 两份集合:{@code remove} 对不存在的条目是空操作,因此在**所有**
     * 标签页上统一执行是安全的,不必依赖「附魔书归属于哪个标签页」这一原版内部细节。
     */
    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        List<ItemStack> fromParent = new ArrayList<>();
        List<ItemStack> fromSearch = new ArrayList<>();
        for (ItemStack stack : event.getParentEntries()) {
            if (isCurseBook(stack)) fromParent.add(stack);
        }
        for (ItemStack stack : event.getSearchEntries()) {
            if (isCurseBook(stack)) fromSearch.add(stack);
        }
        for (ItemStack stack : fromParent) {
            event.remove(stack, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        }
        for (ItemStack stack : fromSearch) {
            event.remove(stack, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
        }
    }
}
