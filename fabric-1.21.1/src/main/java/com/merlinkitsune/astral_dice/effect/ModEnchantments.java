package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 「青之诅咒」标记附魔(注册名 {@code astral_dice:curse_marker})的<strong>权威资源键 + 查表入口</strong>。
 *
 * <p><b>1.21.1 起附魔是数据驱动注册表</b>:{@code Registries.ENCHANTMENT} 由数据包
 * {@code data/<ns>/enchantment/<id>.json} 填充 —— 不存在「代码注册附魔」这回事。
 * 1.20.1 线的 {@code DeferredRegister<Enchantment>} + {@code CurseMarkerEnchantment} 子类
 * ({@code Rarity} / {@code EnchantmentCategory} 构造参数,以及 {@code isCurse()} /
 * {@code getMinCost()} / {@code getMaxCost()} / {@code isDiscoverable()} 四个可覆写钩子)
 * 在 1.21.1 <b>全部不存在</b>:{@code Enchantment} 已无这些成员,诅咒语义改由
 * {@code #minecraft:curse} 标签承载。
 *
 * <p>因此本类只剩两件事:资源键常量 + 按注册表访问器查 {@link Holder}。
 * 数据包未装载 / 条目缺失时返回 {@code null},调用方按「没有标记」处理 ——
 * 与 1.20.1 的 {@code RegistryObject#get()} 取不到返回 null 同一容错口径。
 *
 * <p>数据文件(必须与 neoforge-1.21.1 逐字节一致):
 * {@code data/astral_dice/enchantment/curse_marker.json} +
 * {@code data/minecraft/tags/enchantment/curse.json}。
 */
public final class ModEnchantments {
    /** 「青之诅咒」附魔的资源键(全模组唯一权威:筹码侧与隐藏逻辑都从这里取)。 */
    public static final ResourceKey<Enchantment> CURSE_MARKER_KEY = ResourceKey.create(
            Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "curse_marker"));

    private ModEnchantments() {
    }

    /** 按注册表访问器查「青之诅咒」;数据包未装载 / 条目缺失时返回 {@code null}。 */
    public static Holder<Enchantment> curseMarker(RegistryAccess registries) {
        if (registries == null) return null;
        return registries.lookupOrThrow(Registries.ENCHANTMENT).get(CURSE_MARKER_KEY).orElse(null);
    }

    /** 从玩家所在世界查「青之诅咒」(客户端 / 服务端都可用)。 */
    public static Holder<Enchantment> curseMarker(Player player) {
        if (player == null || player.level() == null) return null;
        return curseMarker(player.level().registryAccess());
    }
}
