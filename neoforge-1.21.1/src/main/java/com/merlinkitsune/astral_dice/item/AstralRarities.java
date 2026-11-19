package com.merlinkitsune.astral_dice.item;

import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/**
 * 本模组稀有度等级的**平台接线** —— 把 {@link Rarity} 里的 5 个等级扩展进原版
 * {@code net.minecraft.world.item.Rarity}，并提供运行期取回扩展常量的访问器。
 *
 * <h2>等级名 / 颜色 / 序列化名都在 {@link Rarity}</h2>
 * <p>本类**不含任何颜色或等级定义**，只做两件事：① 把 {@link Rarity} 的序列化名与
 * {@link Rarity#styleModifier()} 交给平台扩展机制；② 把扩展结果交回消费方。
 *
 * ⚠️ 同包内的 {@code Rarity} 是本模组枚举 ⇒ 原版类型在本文件里**一律写全限定名**，不要 import。
 */

public final class AstralRarities {
    /*
     * ⚠️ 这 5 个字段名是**契约的一部分**：消费方 mod 的 META-INF/enumextensions.json 里
     *    {"class": "com/merlinkitsune/astral_dice/item/AstralRarities", "field": "RARE"} 按名引用它们，
     *    改名（或改类名/包名）必须同步改该 json，否则该条 entry 会变成「引用不存在的字段」= 启动期报错。
     *    参数顺序 = 原版 (int id, String name, UnaryOperator<Style>) 构造器的**声明参数**：
     *      索引 0 = id（FML 用真实序号覆盖，这里放 null 占位）；索引 1 = 序列化名；索引 2 = 染色函数。
     *
     * ⚠️ 静态初始化只构造 EnumProxy（**不得**在此调用 getValue()）：getValue() 会触发原版 Rarity 的
     *    初始化，而那一刻 FML 注入的代码正在读本类字段 ⇒ 会形成"自己初始化自己"的读序问题。
     *    取回常量一律走下面的访问器（懒解析）。
     */
    public static final EnumProxy<net.minecraft.world.item.Rarity> RARE = new EnumProxy<>(
            net.minecraft.world.item.Rarity.class, null,
            Rarity.RARE.serializedName(), Rarity.RARE.styleModifier());

    public static final EnumProxy<net.minecraft.world.item.Rarity> EPIC = new EnumProxy<>(
            net.minecraft.world.item.Rarity.class, null,
            Rarity.EPIC.serializedName(), Rarity.EPIC.styleModifier());

    public static final EnumProxy<net.minecraft.world.item.Rarity> LEGENDARY = new EnumProxy<>(
            net.minecraft.world.item.Rarity.class, null,
            Rarity.LEGENDARY.serializedName(), Rarity.LEGENDARY.styleModifier());

    public static final EnumProxy<net.minecraft.world.item.Rarity> PINNACLE = new EnumProxy<>(
            net.minecraft.world.item.Rarity.class, null,
            Rarity.PINNACLE.serializedName(), Rarity.PINNACLE.styleModifier());

    public static final EnumProxy<net.minecraft.world.item.Rarity> BIZARRE = new EnumProxy<>(
            net.minecraft.world.item.Rarity.class, null,
            Rarity.BIZARRE.serializedName(), Rarity.BIZARRE.styleModifier());

    private AstralRarities() {
    }

    /** 稀有（水蓝 = 原版 RARE 配色）—— 扩展后的原版 {@code Rarity} 常量。 */
    public static net.minecraft.world.item.Rarity rare() {
        return RARE.getValue();
    }

    /** 史诗（粉紫 = 原版 EPIC 配色）。 */
    public static net.minecraft.world.item.Rarity epic() {
        return EPIC.getValue();
    }

    /** 传奇（金）。 */
    public static net.minecraft.world.item.Rarity legendary() {
        return LEGENDARY.getValue();
    }

    /** 巅峰（亮红）。 */
    public static net.minecraft.world.item.Rarity pinnacle() {
        return PINNACLE.getValue();
    }

    /** 奇特（彩虹/流动）。 */
    public static net.minecraft.world.item.Rarity bizarre() {
        return BIZARRE.getValue();
    }

    /**
     * **反查**：把一个（可能已被本库扩展过的）原版 {@code Rarity} 常量还原成本库档位；
     * **不是本模组的档位时返回 {@code null}**（含原版自带的 COMMON / UNCOMMON / RARE / EPIC）。
     *
     * <p>用途：客户端渲染「按本模组档位生效」的表现（提示框边框染色）时，用它判断该物品归哪一档。
     * 消费方**不要**自己写 {@code ==} 链 —— 那等于把档位映射抄到了库外。
     */
    public static Rarity tierOf(net.minecraft.world.item.Rarity rarity) {
        if (rarity == null) {
            return null;
        }
        if (rarity == rare()) {
            return Rarity.RARE;
        }
        if (rarity == epic()) {
            return Rarity.EPIC;
        }
        if (rarity == legendary()) {
            return Rarity.LEGENDARY;
        }
        if (rarity == pinnacle()) {
            return Rarity.PINNACLE;
        }
        if (rarity == bizarre()) {
            return Rarity.BIZARRE;
        }
        return null;
    }
}
