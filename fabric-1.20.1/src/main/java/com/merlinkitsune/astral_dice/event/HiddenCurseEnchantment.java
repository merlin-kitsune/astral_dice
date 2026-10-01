package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

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
 *   <li><b>附魔台 / 铁砧</b>:本线为代码注册附魔:{@code ModEnchantments.CurseMarkerEnchantment} 已把 {@code isDiscoverable} / {@code isAllowedOnBooks} / {@code canApplyAtEnchantingTable} 统一覆写为 false ⇒ 附魔台与附魔书两条获得途径都被关掉。公开入口 {@link #register()} 由模组初始化调用。</li>
 * </ol>
 *
 * <p><b>隐藏只影响「可见 / 可获得」,不影响功能</b>:{@code data/minecraft/tags/enchantment/curse.json}
 * 里仍然保留本附魔(1.21 线千咒卷轴按该标签计数、1.20.1 线按 {@code Enchantment#isCurse()}),
 * 任何一处都不能删。同理,本类也不得改动附魔的注册名与数据文件。
 */

public final class HiddenCurseEnchantment {
    /** 与 {@code CursedSwordChipItem#CURSE_MARKER_KEY} 同源的资源键(此处独立持有,避免反向依赖筹码类)。 */
    public static final ResourceKey<Enchantment> CURSE_MARKER_KEY =
            ResourceKey.create(Registries.ENCHANTMENT,
                    new ResourceLocation(AstralDiceMod.MODID, "curse_marker"));

    private HiddenCurseEnchantment() {
    }

    /** 该附魔是否为「青之诅咒」附魔(1.20.1 为代码注册,直接比对注册对象)。 */
    public static boolean isCurseMarker(Enchantment enchantment) {
        Enchantment curse = com.merlinkitsune.astral_dice.effect.ModEnchantments.CURSE_MARKER.get();
        return curse != null && enchantment == curse;
    }

    /**
     * 该栈是否为「青之诅咒」附魔书。
     * <p>{@code EnchantmentHelper#getEnchantments} 对附魔书读 {@code StoredEnchantments}、
     * 对普通物品读 {@code Enchantments},故必须再判物品本身。
     */
    private static boolean isCurseBook(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return false;
        for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
            if (isCurseMarker(enchantment)) return true;
        }
        return false;
    }

    /**
     * 抹掉 tooltip 里由原版自动追加的「青之诅咒」附魔行。
     * <p>行内容用与原版 {@code ItemStack#appendEnchantmentNames} 同一个函数
     * ({@code Enchantment#getFullname})构造;比对用纯文本以兼容第三方 tooltip 模组。
     */
    public static void stripFromTooltip(ItemStack stack, List<Component> tooltip) {
        if (tooltip == null || tooltip.isEmpty()) return;
        java.util.Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        if (enchantments.isEmpty()) return;
        Set<String> hidden = null;
        for (java.util.Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            if (!isCurseMarker(entry.getKey()) || entry.getValue() <= 0) continue;
            if (hidden == null) hidden = new HashSet<>();
            // 1.20.1 的 Enchantment#getFullname 是**实例**方法(1.21 起改为静态 Holder 版)
            hidden.add(entry.getKey().getFullname(entry.getValue()).getString());
        }
        if (hidden == null) return;
        final Set<String> lines = hidden;
        tooltip.removeIf(line -> lines.contains(line.getString()));
    }

    /**
     * 创造栏去条目:fabric 没有 Forge 的创造栏事件,改用 Fabric API 的
     * {@code ItemGroupEvents#modifyEntriesEvent} —— 它对**原版**标签页同样生效,且在
     * {@code ItemGroup#updateEntries} 内、搜索树重建之前触发,故摘掉即真正生效。
     * 对全部已注册标签页注册(附魔书的两份条目分属不同标签页:最大等级 → 材料页,全部等级 → 搜索页)。
     */
    public static void register() {
        // ⚠️ 1.20.1 的 Registry#keySet() 返回的是 Set<ResourceLocation>(不是 ResourceKey),
        // 故这里走 entrySet() 直接取 ResourceKey。
        for (java.util.Map.Entry<ResourceKey<CreativeModeTab>, CreativeModeTab> tab
                : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
            ItemGroupEvents.modifyEntriesEvent(tab.getKey()).register(entries -> {
                strip(entries.getDisplayStacks());
                strip(entries.getSearchTabStacks());
            });
        }
    }

    private static void strip(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return;
        List<ItemStack> victims = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (isCurseBook(stack)) victims.add(stack);
        }
        // 用**实际存在的那个栈**删除:1.20.1 的 ItemStack 不覆写 equals,引用语义最稳。
        for (ItemStack stack : victims) {
            stacks.remove(stack);
        }
    }
}
