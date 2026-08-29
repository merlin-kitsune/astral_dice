package com.merlinkitsune.astral_dice.component;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * 1.20.1 Forge 数据组件 shim:常量名与 1.21 分支的 {@code ModDataComponents} 一一对应,
 * 内部由 {@link ItemDataKey}(ItemStack NBT)承载;1.21 的 networkSynchronized 语义
 * 由 ItemStack NBT 自动同步承担(1.20.1 物品 NBT 随物品同步)。
 */
public class ModDataComponents {
    public static final ItemDataKey<WeaponEnhancement> WEAPON_ENHANCEMENT =
            ItemDataKey.create("weapon_enhancement", WeaponEnhancement.CODEC);

    public static final ItemDataKey<Integer> COOLDOWN_REMAINING =
            ItemDataKey.create("cooldown_remaining", Codec.INT);

    public static final ItemDataKey<Integer> MISAKI_SIGN_CHARGE =
            ItemDataKey.create("misaki_sign_charge", Codec.INT);

    // 护法立牌(misaki):触发骰神赐福累计的被动层数(最大 3 层)
    public static final ItemDataKey<Integer> MISAKI_SIGN_STACKS =
            ItemDataKey.create("misaki_sign_stacks", Codec.INT);

    // 战斗牌剩余次数:默认值按物品类型决定(对应 1.21 Item.Properties.component 注册的默认次数)
    public static final ItemDataKey<Integer> CARD_USES =
            ItemDataKey.withItemDefault("card_uses", Codec.INT,
                    item -> item instanceof com.merlinkitsune.astral_dice.item.card.CardItem cardItem
                            ? AppliedStone.defaultUses(cardItem.getCardType())
                            : null);

    public static final ItemDataKey<Integer> KOMACHI_SIGN_CHARGE =
            ItemDataKey.create("komachi_sign_charge", Codec.INT);

    public static final ItemDataKey<Integer> PADMAN_ATK_BONUS =
            ItemDataKey.create("padman_atk_bonus", Codec.INT);

    public static final ItemDataKey<Integer> PADMAN_DEF_BONUS =
            ItemDataKey.create("padman_def_bonus", Codec.INT);

    public static final ItemDataKey<Integer> PADMAN_CHARGE =
            ItemDataKey.create("padman_charge", Codec.INT);

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

    public static final ItemDataKey<Long> JASMINE_ARMOR_PENALTY_END =
            ItemDataKey.create("jasmine_armor_penalty_end", Codec.LONG);

    // 专属效果牌:获得者 UUID(空表示尚未绑定,首次使用时绑定)
    public static final ItemDataKey<Optional<UUID>> OWNER_UUID =
            ItemDataKey.create("owner_uuid", UUIDUtil.CODEC.optionalFieldOf("id").codec());

    // 占星师立牌:主动技能已触发,下次攻击的第一个目标施加"虚弱印记"
    public static final ItemDataKey<Boolean> HAIQING_ACTIVE_PENDING =
            ItemDataKey.create("haiqing_active_pending", Codec.BOOL);

    // 秘密侦探立牌:主动技能已触发,下次攻击的第一个目标施加"隐匿调查"
    public static final ItemDataKey<Boolean> BONNIE_ACTIVE_PENDING =
            ItemDataKey.create("bonnie_active_pending", Codec.BOOL);

    // 立牌主动技能"待命"到期时刻(占星师/秘密侦探等需选择目标的技能:等待期内未释放则自动取消)
    public static final ItemDataKey<Long> SKILL_READY_EXPIRE =
            ItemDataKey.create("skill_ready_expire", Codec.LONG);

    private ModDataComponents() {
    }

    /** 兼容 1.21 调用面的快捷读取(存在 NBT 值则读,否则 null)。 */
    public static <T> T get(ItemDataKey<T> key, ItemStack stack) {
        return key.get(stack);
    }
}
