package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.starenginelib.event.EventTargetCollector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;

/**
 * 随机卡牌处理类:随机卡牌的类型筛选、专属牌强制排除与向其他玩家发放的统一逻辑。
 *
 * 设计:
 * - 卡牌类别:{@link CardCategory#ALL}(全部)/ {@link CardCategory#BATTLE}(仅战斗牌:攻击+防御)/
 *   {@link CardCategory#EFFECT}(仅效果牌:功能+伤害)。
 * - 专属牌强制排除:通过 {@link #registerExclusiveCard} 注册的卡牌不会出现在任何随机池中
 *   (当前:活体书页、命运的指引、符卡-福/祸、蛟龙立牌的两张专属战斗牌撕咬/龙之咆哮)。
 * - 发放逻辑:统一走 {@link #giveCards},作用域(范围/团队/发放人数/是否含自己)由
 *   {@link GiveoutScope} 描述;后续角色/卡牌能力(如蛟龙立牌赠卡)复用本类。
 */
public final class RandomCardHandler {

    // 卡牌类别
    public enum CardCategory {
        /** 全部卡牌(战斗牌 + 效果牌) */
        ALL,
        /** 仅战斗牌(攻击牌 + 防御牌) */
        BATTLE,
        /** 仅效果牌(功能效果牌 + 伤害效果牌) */
        EFFECT
    }

    // 发放作用域
    public static final class GiveoutScope {
        /** 发放范围(格);-1 = 不限距离(全服在线玩家) */
        public double range;
        /** 是否包含发放者自己 */
        public boolean includeSelf;
        /** 是否应用团队判定(MC 队伍/FTB Teams/OPAC,范围外队友也计入) */
        public boolean applyTeam;
        /** 最多发放人数;-1 = 不限 */
        public int maxTargets;

        public GiveoutScope(double range, boolean includeSelf, boolean applyTeam, int maxTargets) {
            this.range = range;
            this.includeSelf = includeSelf;
            this.applyTeam = applyTeam;
            this.maxTargets = maxTargets;
        }

        /** 全服在线玩家(不含自己) */
        public static GiveoutScope allPlayers() {
            return new GiveoutScope(-1, false, false, -1);
        }

        /** 指定范围内的玩家(不含自己) */
        public static GiveoutScope around(double range) {
            return new GiveoutScope(range, false, false, -1);
        }

        /** 指定范围内的玩家与团队队友(不含自己,不限人数) */
        public static GiveoutScope aroundWithTeam(double range) {
            return new GiveoutScope(range, false, true, -1);
        }

        /** 指定范围内的玩家与团队队友(不含自己;最多 maxTargets 人,按距离升序取最近者) */
        public static GiveoutScope aroundWithTeam(double range, int maxTargets) {
            return new GiveoutScope(range, false, true, maxTargets);
        }

        /**
         * 同维度全体玩家 + 团队队友(不含自己;最多 maxTargets 人,按距离升序取最近者)。
         * 蛟龙立牌(mamushi)「真龙形态」的主动技范围(取消 12 格限制,仍保留人数安全上限)。
         */
        public static GiveoutScope wholeDimensionWithTeam(int maxTargets) {
            return new GiveoutScope(-1, false, true, maxTargets);
        }
    }

    // 专属牌注册表:随机发放强制排除(活体书页/命运的指引/符卡-福祸/撕咬/龙之咆哮)
    // 存储 DeferredItem 引用,isExclusive 时延迟解析——避免静态初始化阶段调用 .get()
    private static final Set<net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item>> EXCLUSIVE_CARDS =
            new HashSet<>();

    private RandomCardHandler() {
    }

    // 注册专属牌(不参与任何随机卡牌池)
    public static void registerExclusiveCard(net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item> item) {
        EXCLUSIVE_CARDS.add(item);
    }

    // 是否专属牌(随机发放强制排除;运行时延迟解析)
    public static boolean isExclusive(ItemStack stack) {
        for (var ref : EXCLUSIVE_CARDS) {
            if (stack.is(ref.get())) {
                return true;
            }
        }
        return false;
    }

    static {
        // 当前专属效果牌(注册引用,运行时解析,避免静态初始化 .get())
        registerExclusiveCard(ModItems.LIVING_PAGE);   // 活体书页(调查员立牌专属-伤害)
        registerExclusiveCard(ModItems.FATE_GUIDANCE_CARD); // 命运的指引(专属-功能)
        registerExclusiveCard(ModItems.FU_CARD);       // 符卡-福(风水师立牌专属:绑定获得者)
        registerExclusiveCard(ModItems.HUO_CARD);      // 符卡-祸(风水师立牌专属:绑定获得者)
        // 蛟龙立牌(mamushi)的两张专属战斗牌:同样绑定获得者(仅获得者可装备),且不入任何随机池
        registerExclusiveCard(ModItems.ATTACK_CARD_BITE);        // 撕咬
        registerExclusiveCard(ModItems.ATTACK_CARD_DRAGON_ROAR); // 龙之咆哮
    }

    // === 卡牌池 ===

    // 全部攻击牌(全力攻击不在随机池:仅能通过消耗"蓄力"获得,见 DiceCombatEvents.onDiceBlessingExpired)
    private static List<Item> attackCards() {
        return List.of(
                ModItems.ATTACK_CARD_MEDIUM.get(),
                ModItems.ATTACK_CARD_LARGE.get(),
                ModItems.ATTACK_CARD_EPIC.get(),
                ModItems.ATTACK_CARD_SHADOW_STRIKE.get(),
                ModItems.ATTACK_CARD_MEITO.get(),
                ModItems.ATTACK_CARD_CHARGE.get());
    }

    // 全部防御牌
    private static List<Item> defenseCards() {
        return List.of(
                ModItems.DEFENSE_CARD_MEDIUM.get(),
                ModItems.DEFENSE_CARD_LARGE.get(),
                ModItems.DEFENSE_CARD_EPIC.get());
    }

    // 全部效果牌(功能 + 伤害 + 治疗)
    private static List<Item> effectCards() {
        return List.of(
                ModItems.EFFECT_CARD_KING_POWER.get(),
                ModItems.EFFECT_CARD_BERSERK.get(),
                ModItems.EFFECT_CARD_UNWAVERING.get(),
                ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get(),
                ModItems.MONSTER_LASER_CARD.get(),
                ModItems.MONSTER_BRICK_CARD.get(),
                ModItems.ORBITAL_STRIKE_CARD.get(),
                ModItems.DIRECTIONAL_BLAST_CARD.get(),
                ModItems.CHOCOLATE_CAKE.get(),
                ModItems.HAMBURGER.get(),
                ModItems.LUXURY_FEAST.get(),
                ModItems.YOU_HAVE_I_HAVE.get(),
                ModItems.EXPRESS_DELIVERY.get());
    }

    /**
     * 按类别获取随机卡牌池(已强制排除全部专属牌)。
     * 池由统一的攻击/防御/效果列表组合而成,避免内联重复列表漂移。
     */
    public static List<ItemStack> getCardPool(CardCategory category) {
        List<Item> items = switch (category) {
            case ALL -> {
                List<Item> all = new ArrayList<>();
                all.addAll(attackCards());
                all.addAll(defenseCards());
                all.addAll(effectCards());
                yield all;
            }
            case BATTLE -> {
                List<Item> battle = new ArrayList<>();
                battle.addAll(attackCards());
                battle.addAll(defenseCards());
                yield battle;
            }
            case EFFECT -> new ArrayList<>(effectCards());
        };
        items.removeIf(item -> {
            for (var ref : EXCLUSIVE_CARDS) {
                if (item == ref.get()) return true;
            }
            return false;
        });
        return items.stream().map(ItemStack::new).toList();
    }

    // 随机抽取一张(池为空返回空栈)
    public static ItemStack randomCard(CardCategory category) {
        List<ItemStack> pool = getCardPool(category);
        if (pool.isEmpty()) return ItemStack.EMPTY;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    // === 发放逻辑 ===

    // 给指定玩家随机一张卡(背包满则掉落;无发放者)
    public static void giveCardTo(Player receiver, CardCategory category) {
        giveCardTo(null, receiver, category);
    }

    /**
     * 给指定玩家随机一张卡,并把**发放者**透传进发牌漏斗(背包满则掉落)。
     * 发放者用于立牌被动计数(蛟龙立牌 mamushi「湖沼之王」:佩戴者使其他角色获得卡牌时计觉醒层数)。
     */
    public static void giveCardTo(Player giver, Player receiver, CardCategory category) {
        ItemStack card = randomCard(category);
        if (card.isEmpty()) return;
        // 维生素药丸发牌统一入口(治愈联动;看板娘立牌被动不再随奖励/复制/返还触发,仅合成与主动返还显式触发)
        VitaminPillChipItem.giveCard(giver, receiver, card);
    }

    // 收集发放目标:范围玩家 + (可选)团队队友;排除自己(按作用域);超上限时**按距离升序取最近 N 人**
    public static List<Player> collectTargets(Player giver, GiveoutScope scope) {
        if (giver.level().isClientSide()) return List.of();
        List<Player> targets = new ArrayList<>();
        if (scope.range < 0) {
            if (giver.level() instanceof ServerLevel serverLevel) {
                targets.addAll(serverLevel.players());
            }
        } else {
            targets.addAll(giver.level().getEntitiesOfClass(Player.class,
                    giver.getBoundingBox().inflate(scope.range)));
        }
        if (scope.applyTeam) {
            targets.addAll(EventTargetCollector.collectTeamPlayers(giver));
        }
        if (!scope.includeSelf) {
            targets.remove(giver);
        }
        List<Player> distinct = targets.stream().distinct().filter(Player::isAlive).toList();
        if (scope.maxTargets >= 0 && distinct.size() > scope.maxTargets) {
            // 人数安全上限:按与发放者的距离**升序**取最近 N 人(不再是随机抽样——
            // 蛟龙立牌「真龙形态」的 32 人上限要求"距离近者优先";平局按 UUID 稳定排序,
            // 团队队友一并参与同一排序。口径与 neoforge-1.21.1 线逐字一致)。
            List<Player> copy = new ArrayList<>(distinct);
            copy.sort(java.util.Comparator
                    .comparingDouble((Player p) -> p.distanceToSqr(giver))
                    .thenComparing((Player p) -> p.getUUID().toString()));
            return copy.subList(0, scope.maxTargets);
        }
        return distinct;
    }

    // 按作用域向玩家发放随机卡(返回实际发放数量;发放者透传给发牌漏斗用于立牌被动计数)
    public static int giveCards(Player giver, CardCategory category, GiveoutScope scope) {
        List<Player> targets = collectTargets(giver, scope);
        for (Player target : targets) {
            giveCardTo(giver, target, category);
        }
        return targets.size();
    }
}
