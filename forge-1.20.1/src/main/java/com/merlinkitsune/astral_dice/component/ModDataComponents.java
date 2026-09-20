package com.merlinkitsune.astral_dice.component;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

import com.merlinkitsune.starenginelib.component.ItemDataKey;
/**
 * 1.20.1 Forge 数据组件 shim:常量名与 1.21 分支的 {@code ModDataComponents} 一一对应,
 * 内部由 {@link ItemDataKey}(ItemStack NBT)承载;1.21 的 networkSynchronized 语义
 * 由 ItemStack NBT 自动同步承担(1.20.1 物品 NBT 随物品同步)。
 */
public class ModDataComponents {
    public static final ItemDataKey<WeaponEnhancement> WEAPON_ENHANCEMENT =
            ItemDataKey.create("weapon_enhancement", WeaponEnhancement.CODEC);



    // 护法立牌(misaki):触发骰神赐福累计的被动层数(最大 3 层)
    public static final ItemDataKey<Integer> MISAKI_SIGN_STACKS =
            ItemDataKey.create("misaki_sign_stacks", Codec.INT);

    // 战斗牌剩余次数:默认值按物品类型决定(对应 1.21 Item.Properties.component 注册的默认次数)
    public static final ItemDataKey<Integer> CARD_USES =
            ItemDataKey.withItemDefault("card_uses", Codec.INT,
                    item -> item instanceof com.merlinkitsune.astral_dice.item.card.CardItem cardItem
                            ? AppliedStone.defaultUses(cardItem.getCardType())
                            : null);


    public static final ItemDataKey<Integer> PADMAN_ATK_BONUS =
            ItemDataKey.create("padman_atk_bonus", Codec.INT);

    public static final ItemDataKey<Integer> PADMAN_DEF_BONUS =
            ItemDataKey.create("padman_def_bonus", Codec.INT);


    // 上班族立牌:被动攻防数值上次刷新的游戏时刻(用于主动重置计时器)
    public static final ItemDataKey<Long> PADMAN_LAST_REFRESH =
            ItemDataKey.create("padman_last_refresh", Codec.LONG);

    // 上班族立牌:赐福期间骰点为1时置位,下次攻击骰点必为6
    public static final ItemDataKey<Boolean> PADMAN_FORCE_SIX =
            ItemDataKey.create("padman_force_six", Codec.BOOL);

    public static final ItemDataKey<Integer> JASMINE_ATK_BONUS =
            ItemDataKey.create("jasmine_atk_bonus", Codec.INT);

    public static final ItemDataKey<Integer> JASMINE_DEF_BONUS =
            ItemDataKey.create("jasmine_def_bonus", Codec.INT);


    /**
     * 临时牌标记(绿洲女王 nardis 主动「女王特权」):带此 NBT 键的卡牌为「临时牌」——
     * 只有 3:00 有效期、不可丢弃、不可移入其它容器、带附魔光效,效果结束即整体清空。
     *
     * <p>装备进骰子后**不会**丢失临时性:装配会销毁物品栈,标记改由
     * {@link AppliedStone#temporary()} 承载,两者由 {@code screen/CardInventoryMenu} 双向透传
     * (见 {@link AppliedStone} 的类注释)。
     *
     * <p>1.20.1 落点:1.21 的 {@code DataComponentType<Boolean>} 在这里由 {@link ItemDataKey}
     * (ItemStack NBT)承载 —— 读写签名为 {@code get(stack)} / {@code set(stack, v)} /
     * {@code remove(stack)}(不是 1.21 的 {@code stack.get(KEY.get())} 形式)。
     * 未写入 NBT 前 {@code get} 返回 {@code null},故判定必须用 {@code Boolean.TRUE.equals(...)}。
     */
    public static final ItemDataKey<Boolean> TEMPORARY_CARD =
            ItemDataKey.create("temporary_card", Codec.BOOL);

    // 专属效果牌:获得者 UUID(空表示尚未绑定,首次使用时绑定)
    public static final ItemDataKey<Optional<UUID>> OWNER_UUID =
            ItemDataKey.create("owner_uuid", UUIDUtil.CODEC.optionalFieldOf("id").codec());




    private ModDataComponents() {
    }

    /** 兼容 1.21 调用面的快捷读取(存在 NBT 值则读,否则 null)。 */
    public static <T> T get(ItemDataKey<T> key, ItemStack stack) {
        return key.get(stack);
    }
}
