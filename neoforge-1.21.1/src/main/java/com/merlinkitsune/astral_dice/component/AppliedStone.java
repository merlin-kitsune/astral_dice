package com.merlinkitsune.astral_dice.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import com.merlinkitsune.astral_dice.combat.CardRegistry;

/**
 * 已插入骰子卡牌栏的卡牌(类型 + 剩余耐久 + 是否为「临时牌」)。
 * 类型定义(费用/耐久/攻防归属/掷骰)统一由 {@link com.merlinkitsune.astral_dice.combat.CardRegistry} 管理,
 * 本 record 仅作为数据载体。
 *
 * <h2>为什么「临时性」必须存在这里(绿洲女王 nardis 批,2026-09-27)</h2>
 * 「装配会**销毁**卡牌物品栈,卸除时由 {@code CardInventoryMenu#loadFromDice} 按 {@code (type, uses)}
 * 重建**全新**栈」—— 见 {@code screen/CardInventoryMenu#saveToDice} 内既有注释。任何**物品级**标记
 * (数据组件 / NBT)都不可能在「插入 → 卸除 → 再插入」之间存活 ⇒ 装配中的临时牌只能靠本 record 的
 * 第 3 个分量承载,并由 {@code CardInventoryMenu} 在**双向**回环里透传(重建栈时补回物品标记、
 * 写回时从栈读回标记),这样卡牌栏 UI 里也能一眼看出哪张是临时牌。
 *
 * <p>{@code CODEC} 的第 3 字段用 {@code optionalFieldOf("temporary", false)}(老存档仍可读);
 * {@code STREAM_CODEC} 追加一列(两线同版本,无跨版本兼容问题)。
 *
 * <p>2026-10-27 追加第 4 分量 {@code owner}(获得者 UUID 字符串):装配态不持有物品栈,获得者只能
 * 记在这里;老存档没有该字段 ⇒ 空串 = 无主 ⇒ 按「非本人持有」清掉
 * (见 {@code TemporaryCardUtil#purgeIllegalEquipped})。
 */
public record AppliedStone(String type, int uses, boolean temporary, String owner) {
    public static final Codec<AppliedStone> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("type").forGetter(AppliedStone::type),
                    Codec.INT.fieldOf("uses").forGetter(AppliedStone::uses),
                    Codec.BOOL.optionalFieldOf("temporary", false).forGetter(AppliedStone::temporary),
                    Codec.STRING.optionalFieldOf("owner", "").forGetter(AppliedStone::owner)
            ).apply(instance, AppliedStone::new));

    public static final StreamCodec<ByteBuf, AppliedStone> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AppliedStone::type,
            ByteBufCodecs.INT, AppliedStone::uses,
            ByteBufCodecs.BOOL, AppliedStone::temporary,
            ByteBufCodecs.STRING_UTF8, AppliedStone::owner,
            AppliedStone::new
    );

    /** 兼容构造器:既有调用点(非临时牌)一律落到 {@code temporary = false} / {@code owner = ""} */
    public AppliedStone(String type, int uses) {
        this(type, uses, false, "");
    }

    /** 兼容构造器:只有临时性、没有 owner 的调用点(owner 视为无主 ⇒ 会被当作非法持有清掉) */
    public AppliedStone(String type, int uses, boolean temporary) {
        this(type, uses, temporary, "");
    }

    /**
     * 获得者 UUID(2026-10-27 用户裁决:临时牌只能由获得者持有与使用)。
     *
     * <p>为什么装配态要自带一份:装配会销毁卡牌物品栈,物品上的
     * {@code ModDataComponents#TEMPORARY_CARD_OWNER} 就没了 ⇒ 获得者必须跟着本记录走,
     * 否则「把骰子转手给别人再卸下」就能把别人的牌洗成自己的。
     *
     * @return 空串 / 非法 UUID 串 ⇒ {@code null}(= 无主)
     */
    public java.util.UUID ownerId() {
        if (owner == null || owner.isBlank()) return null;
        try {
            return java.util.UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
