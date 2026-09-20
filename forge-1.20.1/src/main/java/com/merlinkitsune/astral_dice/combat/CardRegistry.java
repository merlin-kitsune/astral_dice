package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.MisakiSignItem;

/**
 * 卡牌类型注册表(战斗牌:攻击/防御)。
 *
 * 统一维护每张战斗牌的:
 * - 类型 id(typeId,存于 {@link AppliedStone#type()});
 * - 攻击/防御归属(isDefense);
 * - 默认耐久与费用;
 * - 对应的物品(用于卡牌栏 GUI 双向映射);
 * - 掷骰逻辑(战斗结算时由修饰器调用,返回该卡的骰点加成)。
 *
 * 新增战斗牌:注册一个 {@link CardType} 即可,GUI/战斗/费用/耐久全部自动适配,无需修改分发点。
 * 注意:护法立牌(misaki)的名刀费用折扣由 {@link #cost(String, Player)} 统一处理。
 */
public final class CardRegistry {

    /** 卡牌类型定义 */
    public record CardType(String typeId, boolean isDefense, int defaultUses, int cost,
                           Item item, DiceRoller roller) {
    }

    /** 掷骰逻辑:返回该卡在骰战中的骰点加成(可能为固定值或随机) */
    @FunctionalInterface
    public interface DiceRoller {
        int roll(DiceCombatContext ctx);
    }

    private static final Map<String, CardType> BY_ID = new LinkedHashMap<>();

    private CardRegistry() {
    }

    public static void register(CardType type) {
        BY_ID.put(type.typeId(), type);
    }

    public static CardType get(String typeId) {
        return BY_ID.get(typeId);
    }

    public static boolean exists(String typeId) {
        return BY_ID.containsKey(typeId);
    }

    public static boolean isDefense(String typeId) {
        CardType t = BY_ID.get(typeId);
        return t != null && t.isDefense();
    }

    public static int defaultUses(String typeId) {
        // 注意:ModItems 静态初始化阶段会调用本方法,而 CardRegistry.init() 在 FMLCommonSetup 才执行,
        // 因此这里不能依赖 BY_ID(否则所有卡牌默认耐久都会错误地回退为 10)。
        // 内置卡牌默认耐久在此集中维护,CardType 注册时也使用同一组数值。
        return switch (typeId) {
            case "medium", "large", "epic", "shadow_strike",
                 "defense_medium", "defense_large", "defense_epic" -> 10;
            case "meito" -> 5;
            case "charge" -> 1;
            case "full_power" -> 5;
            // 蛟龙立牌(mamushi)专属战斗牌(撕咬 1 / 龙之咆哮 5);缺此分支会静默回退 10
            case "bite" -> 1;
            case "dragon_roar" -> 5;
            default -> {
                CardType t = BY_ID.get(typeId);
                yield t != null ? t.defaultUses() : 10;
            }
        };
    }

    /**
     * 卡牌费用(考虑护法立牌 misaki 的名刀折扣:装备护法立牌时"名刀嘎呜切"费用降低为 3)。
     */
    public static int cost(String typeId, Player player) {
        CardType t = BY_ID.get(typeId);
        if (t == null) return 1;
        if ("meito".equals(typeId) && com.merlinkitsune.astral_dice.item.sign.MisakiSignItem.hasMisakiEquipped(player)) {
            return 3;
        }
        return t.cost();
    }

    /** 掷骰:类型不存在时返回 0 */
    public static int roll(String typeId, DiceCombatContext ctx) {
        return roll(typeId, ctx, false);
    }

    /**
     * 掷骰(maxMode=玻璃骰子:战斗牌点数始终取最大值)。
     * 仅随机骰牌(中/大/特大/名刀/防御牌)在 maxMode 下直接返回上限;
     * 固定值牌(暗影突袭/蓄力/全力攻击)仍走原 roller 以保留副作用(如 hasShadowStrike/hasFullPower)。
     */
    public static int roll(String typeId, DiceCombatContext ctx, boolean maxMode) {
        CardType t = BY_ID.get(typeId);
        if (t == null) return 0;
        if (maxMode && isRandomRollCard(typeId)) {
            return maxRoll(typeId);
        }
        return t.roller().roll(ctx);
    }

    /** 是否为随机骰点战斗牌(玻璃骰子取最大值仅作用于此类;固定值牌保留原掷骰副作用) */
    private static boolean isRandomRollCard(String typeId) {
        return switch (typeId) {
            case "medium", "large", "epic",
                 "defense_medium", "defense_large", "defense_epic", "meito" -> true;
            default -> false;
        };
    }

    /**
     * 卡牌点数上限(该卡掷骰可能达到的最大值;供闪避失败"攻击点数最大值"结算使用)。
     * 与攻击卡掷骰一致遍历 appliedStones,因此防御卡同样返回其点数上限(当前攻击点数结算包含全部已装卡)。
     */
    public static int maxRoll(String typeId) {
        return switch (typeId) {
            case "medium", "defense_medium" -> 3;
            case "large", "defense_large" -> 6;
            case "epic", "defense_epic" -> 10;
            case "shadow_strike" -> 3;
            case "meito" -> 20;
            case "charge" -> 5;
            case "full_power" -> 6;
            // 蛟龙立牌专属战斗牌:定值 3(与 roller 同值;供卡牌栏 "最低/最高" 显示与闪避失败上限结算)
            case "bite", "dragon_roar" -> 3;
            default -> 0;
        };
    }

    /** 卡牌点数下限(固定伤害牌返回其固定值,随机骰牌返回 1) */
    public static int minRoll(String typeId) {
        return switch (typeId) {
            case "shadow_strike", "bite", "dragon_roar" -> 3;
            case "charge" -> 5;
            case "full_power" -> 6;
            default -> 1;
        };
    }

    /** 卡牌点数范围文本,格式:最低/最高 */
    public static String rangeText(String typeId) {
        return minRoll(typeId) + "/" + maxRoll(typeId);
    }
    /** 物品 → 类型 id;非战斗牌返回 null */
    public static String itemToType(ItemStack stack) {
        for (CardType t : BY_ID.values()) {
            if (t.item() != null && stack.is(t.item())) {
                return t.typeId();
            }
        }
        return null;
    }

    /** 类型 id → 物品;未知类型回退到攻击(中) */
    public static ItemStack typeToItem(String typeId) {
        CardType t = BY_ID.get(typeId);
        if (t != null && t.item() != null) {
            return new ItemStack(t.item());
        }
        CardType fallback = BY_ID.get("medium");
        return fallback != null && fallback.item() != null ? new ItemStack(fallback.item()) : ItemStack.EMPTY;
    }

    // === 内置掷骰:取两次随机最大值 ===
    private static int rollTwoMax(int max, DiceCombatContext ctx) {
        int a = DiceCombatModifiers.rollDice(max);
        int b = DiceCombatModifiers.rollDice(max);
        return Math.max(a, b);
    }

    // 注册全部内置战斗牌
    public static void init() {
        // 攻击牌
        register(new CardType("medium", false, 10, 1,
                com.merlinkitsune.astral_dice.item.ModItems.ATTACK_CARD_MEDIUM.get(),
                ctx -> rollTwoMax(3, ctx)));
        register(new CardType("large", false, 10, 2,
                com.merlinkitsune.astral_dice.item.ModItems.ATTACK_CARD_LARGE.get(),
                ctx -> rollTwoMax(6, ctx)));
        register(new CardType("epic", false, 10, 3,
                com.merlinkitsune.astral_dice.item.ModItems.ATTACK_CARD_EPIC.get(),
                ctx -> rollTwoMax(10, ctx)));
        register(new CardType("shadow_strike", false, 10, 2,
                com.merlinkitsune.astral_dice.item.ModItems.ATTACK_CARD_SHADOW_STRIKE.get(),
                ctx -> {
                    ctx.hasShadowStrike = true;
                    return 3;
                }));
        register(new CardType("meito", false, 5, 4,
                com.merlinkitsune.astral_dice.item.ModItems.ATTACK_CARD_MEITO.get(),
                ctx -> {
                    // 护法立牌(misaki):爆发期间名刀伤害加成下限按星级增加(1星+2,2星+3,3星+5)
                    int min = 1;
                    if (ctx.misakiBurst) {
                        min += switch (ctx.misakiStar) {
                            case 1 -> 2;
                            case 2 -> 3;
                            case 3 -> 5;
                            default -> 0;
                        };
                    }
                    return Math.max(DiceCombatModifiers.rollDice(min, 20), DiceCombatModifiers.rollDice(min, 20));
                }));
        register(new CardType("charge", false, 1, 5,
                com.merlinkitsune.astral_dice.item.ModItems.ATTACK_CARD_CHARGE.get(),
                ctx -> 5));
        register(new CardType("full_power", false, 5, 3,
                com.merlinkitsune.astral_dice.item.ModItems.ATTACK_CARD_FULL_POWER.get(),
                ctx -> {
                    // 全力攻击:先提供固定 +6 攻击力,再令本次攻击的最终攻击力 +50%
                    ctx.hasFullPower = true;
                    return 6;
                }));

        // 蛟龙立牌(mamushi)专属战斗牌:攻击贡献为**定值 3**(撕咬 / 龙之咆哮同值)。
        // 两张牌不入任何随机池(RandomCardHandler.attackCards 不含它们),只能由真龙形态主动技发放或撕咬转换获得。
        register(new CardType("bite", false, 1, 2,
                com.merlinkitsune.astral_dice.item.ModItems.ATTACK_CARD_BITE.get(),
                ctx -> 3));
        register(new CardType("dragon_roar", false, 5, 3,
                com.merlinkitsune.astral_dice.item.ModItems.ATTACK_CARD_DRAGON_ROAR.get(),
                ctx -> 3));

        // 防御牌
        register(new CardType("defense_medium", true, 10, 1,
                com.merlinkitsune.astral_dice.item.ModItems.DEFENSE_CARD_MEDIUM.get(),
                ctx -> rollTwoMax(3, ctx)));
        register(new CardType("defense_large", true, 10, 2,
                com.merlinkitsune.astral_dice.item.ModItems.DEFENSE_CARD_LARGE.get(),
                ctx -> rollTwoMax(6, ctx)));
        register(new CardType("defense_epic", true, 10, 3,
                com.merlinkitsune.astral_dice.item.ModItems.DEFENSE_CARD_EPIC.get(),
                ctx -> rollTwoMax(10, ctx)));
    }
}
