package com.merlinkitsune.astral_dice.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.merlinkitsune.astral_dice.combat.CardRegistry;

/**
 * 已插入骰子卡牌栏的卡牌(类型 + 剩余耐久 + 是否为「临时牌」)。
 * 类型定义(费用/耐久/攻防归属/掷骰)统一由 {@link com.merlinkitsune.astral_dice.combat.CardRegistry} 管理,
 * 本 record 仅作为数据载体。
 *
 * <h2>为什么「临时性」必须存在这里(绿洲女王 nardis 批,2026-09-27)</h2>
 * 「装配会**销毁**卡牌物品栈,卸除时由 {@code CardInventoryMenu#loadFromDice} 按 {@code (type, uses)}
 * 重建**全新**栈」—— 见 {@code screen/CardInventoryMenu#saveToDice} 内既有注释。任何**物品级**标记
 * (1.20.1 这里是 {@code ItemDataKey} 的物品 NBT)都不可能在「插入 → 卸除 → 再插入」之间存活
 * ⇒ 装配中的临时牌只能靠本 record 的第 3 个分量承载,并由 {@code CardInventoryMenu} 在**双向**
 * 回环里透传(重建栈时补回物品标记、写回时从栈读回标记),这样卡牌栏 UI 里也能一眼看出哪张是临时牌。
 *
 * <p>{@code CODEC} 的第 3 字段用 {@code optionalFieldOf("temporary", false)}(老存档仍可读)。
 * 1.20.1 侧本 record **只有** {@code CODEC}(经 {@code ItemDataKey} 走 ItemStack NBT),
 * 没有 1.21 的 {@code STREAM_CODEC}(1.20.1 物品 NBT 随物品自动同步)。
 */
public record AppliedStone(String type, int uses, boolean temporary) {
    public static final Codec<AppliedStone> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("type").forGetter(AppliedStone::type),
                    Codec.INT.fieldOf("uses").forGetter(AppliedStone::uses),
                    Codec.BOOL.optionalFieldOf("temporary", false).forGetter(AppliedStone::temporary)
            ).apply(instance, AppliedStone::new));

    /** 兼容构造器:既有调用点(非临时牌)一律落到 {@code temporary = false} */
    public AppliedStone(String type, int uses) {
        this(type, uses, false);
    }

    public static AppliedStone of(String type) {
        return new AppliedStone(type, defaultUses(type));
    }

    // 默认耐久:委托 CardRegistry(未注册类型回退 10)
    public static int defaultUses(String type) {
        return com.merlinkitsune.astral_dice.combat.CardRegistry.defaultUses(type);
    }

    // 卡牌费用:委托 CardRegistry(未注册类型回退 1)
    public static int cost(String type) {
        return com.merlinkitsune.astral_dice.combat.CardRegistry.cost(type, null);
    }
}
