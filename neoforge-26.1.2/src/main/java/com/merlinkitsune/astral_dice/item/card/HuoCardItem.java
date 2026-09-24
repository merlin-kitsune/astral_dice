package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.effect.MisfortuneEffect;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.merlinkitsune.starenginelib.combat.HostileTargets;
import com.merlinkitsune.starenginelib.target.SelectorTargets;

/**
 * 「符卡-祸」(风水师立牌 zhao 的专属伤害效果牌)。
 *
 * <h2>使用</h2>
 * 沿用效果牌基类的「手持即选择」路径:主手手持自动开启目标选择会话,目标类型
 * {@link TargetType#ENEMY_OR_RIVAL} —— 即本模组全局口径的**敌对目标**(敌对生物 ∪ 中立生物(宠物除外))
 * **并含「非同队伍的玩家」**({@code target/SelectorTargets} 把该类型并到
 * {@code combat/HostileTargets#isHostile(viewer,target)} 的上下文口径上;
 * 队友玩家 / 被动生物 / 自己**不可选**,{@code allowSelf=false})。确认后对目标造成 <b>1</b> 点伤害。
 *
 * <h2>持有代价(厄运)</h2>
 * <ul>
 *   <li><b>厄运效果层数 == 持有张数</b>:由 {@link #tick} 每 20 tick 把**主物品栏**
 *       ({@code player.getInventory().getNonEquipmentItems()},与 {@code SmartWatchChipItem#countCards} 同口径)
 *       的符卡-祸总张数镜像到 {@code astral_dice:misfortune}(张数 0 ⇒ 无该效果);</li>
 *   <li><b>每 2:00 周期伤害</b>:伤害 = 「**结算时刻**当前张数」;计时器(附件
 *       {@code ModAttachments#HUO_CARD_NEXT_DAMAGE_TICK},绝对刻)只在首次持有时起算一次,
 *       此后**只由结算推进**——发牌/用牌/丢弃都不改写它
 *       ⇒ 张数在 >0 区间的增减**不影响计时器**(计时器与结算分离)。</li>
 * </ul>
 *
 * <p><b>2026-09-20 变更</b>:原先的「持有期间禁止丢弃」(主动丢弃被拒 / 拖出 GUI 兜底退还 /
 * 潜影盒・收纳袋拒绝收纳)**已按用户指令整体移除** —— 本牌现在可以自由丢弃与收纳,
 * 持有代价只保留上面的厄运层数与 2:00 周期伤害。
 *
 * <p>图标 = {@code images/符卡-祸.png}(实装路径 {@code textures/item/huo_card.png});
 * 厄运图标 = {@code images/厄运.png}({@code textures/mob_effect/misfortune.png})。
 */
public class HuoCardItem extends BaseEffectCardItem {

    /** 目标选择器动作 id(注册表键;也是 actionbar 技能名 {@code msg.astral_dice.target_select.skill.<id>} 的来源) */
    public static final String ACTION_ID = "huo_card";

    /** 对敌对目标造成的伤害 */
    public static final float DAMAGE = 1.0F;

    /** 「厄运」周期伤害间隔:2:00 */
    public static final int CURSE_PERIOD_TICKS = 2400;

    static {
        // 敌对目标(含非同队玩家):TargetType.ENEMY_OR_RIVAL;不可对自己使用
        registerSelectorAction(ACTION_ID, TargetType.ENEMY_OR_RIVAL, false);
    }

    public HuoCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String cardTypeId() {
        return ACTION_ID;
    }

    @Override
    protected boolean isExclusive() {
        return true;
    }

    @Override
    public String selectorActionId() {
        return ACTION_ID;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        // 对敌对目标造成 1 点伤害:真伤类型(直接伤害实体为空 ⇒ 不会被当成玩家的直接攻击重走命中判定),
        // 击杀归属仍记在施放者身上(与「战斗爽·溅射」同一形状,见 damage/ModDamageTypes#trueDamage(Level,Entity))。
        if (applyTo != null && applyTo.isAlive()) {
            applyTo.hurt(ModDamageTypes.trueDamage(level, user), DAMAGE);
        }
        ExclusiveCardUtil.bindIfAbsent(stack, user);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  厄运:层数镜像 + 每 2:00 周期伤害(计时器与结算分离)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 玩家当前持有的符卡-祸总张数(**主物品栏 + 副手**,唯一的"持有张数"真值来源)。
     *
     * <p>口径 = 主物品栏 {@code player.getInventory().getNonEquipmentItems()}(与
     * {@code item/chip/SmartWatchChipItem#countCards} 一致)**加上副手**
     * {@code player.getOffhandItem()} —— 2026-09-19 用户裁决:副手持有的符卡-祸**同样计入**
     * 厄运层数与周期伤害(此前只算主物品栏属缺陷)。**放进普通容器后仍不计入**
     * (容器标签页/末影箱等都不算"持有"),这正是"持有代价"这条机制的既有形状。
     *
     * <p>⚠️ 副手必须**单独**读一次:{@code Inventory#getContainerSize()} 在 1.20.1 含护甲与副手槽,
     * 用它遍历会重复计数/误计护甲,故本方法只遍历 {@code items} + 单独读一次副手。
     */
    public static int count(Player player) {
        if (player == null) return 0;
        int total = 0;
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (!s.isEmpty() && s.is(ModItems.HUO_CARD.get())) total += s.getCount();
        }
        // 副手:2026-09-19 用户裁决「副手持有也要加厄运层数」⇒ 单独计入(不遍历 getContainerSize)
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(ModItems.HUO_CARD.get())) total += off.getCount();
        return total;
    }

    /**
     * 移除玩家身上的**全部**符卡-祸(主物品栏 + 副手),返回移除的总张数。
     *
     * <p>与 {@link #count} 是**同一持有集合**口径(主物品栏 + 副手);差异只在"移除"与"计数"——
     * 本方法的消费方是"把自身**全部**符卡-祸转换为符卡-福"(一张都不能漏)。
     * 不触碰「厄运」计时器(计时器与持有张数解耦)。
     */
    public static int removeAll(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int removed = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.is(ModItems.HUO_CARD.get())) continue;
            removed += s.getCount();
            inv.setItem(i, ItemStack.EMPTY);
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(ModItems.HUO_CARD.get())) {
            removed += off.getCount();
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
        return removed;
    }

    /**
     * 把玩家的全部符卡-祸转换为等量的符卡-福(风水师立牌主动「白泽赐福」的施法者侧效果)。
     *
     * @return 实际转换的张数(0 = 一张符卡-祸都没有)
     */
    public static int convertAllToFu(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int moved = removeAll(player);
        if (moved <= 0) return 0;
        FuCardItem.give(player, player, moved);
        refreshCurseState(player);
        return moved;
    }

    /**
     * 发放 {@code count} 张**已绑定获得者**的符卡-祸(风水师立牌被动「福祸相倚」的发放入口)。
     *
     * <p>发放统一走 {@code VitaminPillChipItem#giveCard}(既有发牌入口:成功入包时触发维生素药丸;
     * 背包满则掉落、不丢弃不销毁)。**不触碰「厄运」计时器** —— 发牌只改张数,计时器与结算分离。
     */
    public static void give(Player receiver, Player owner, int count) {
        if (receiver == null || owner == null || count <= 0) return;
        if (receiver.level().isClientSide()) return;
        ItemStack stack = new ItemStack(ModItems.HUO_CARD.get(), count);
        ExclusiveCardUtil.setOwner(stack, owner);
        com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem.giveCard(receiver, stack);
    }

    /** 发放 1 张已绑定获得者的符卡-祸(便捷重载) */
    public static void give(Player receiver, Player owner) {
        give(receiver, owner, 1);
    }

    /** 立即把「厄运」层数镜像成当前张数(不做结算、不动计时器) */
    public static void refreshCurseState(Player player) {
        if (player == null || player.level().isClientSide()) return;
        int count = count(player);
        if (count <= 0) {
            MisfortuneEffect.clear(player);
            return;
        }
        MisfortuneEffect.mirror(player, count);
    }

    /**
     * 每 20 tick 驱动(由 {@code event/PlayerTickEvents#onPlayerTick} 调用):
     * ① 到期则按**当前张数**结算一次周期伤害;② 把「厄运」层数镜像成当前张数。
     *
     * <p><b>计时器与结算分离</b>的证据就是本方法里的三处写法:计时器只在
     * 「张数 0 → &gt;0 且当前未起算」时写一次({@code next = now + 2:00}),
     * 之后**只做 {@code next += 2:00} 的推进**,不因为张数变化重新起算(张数在 &gt;0 区间内
     * 从 1 变 5、从 5 变 2 都不改写计时器)。张数归 0 = 整段清除(§9.2):既无厄运效果、也无周期伤害,
     * 计时器一并归 0(下一次持有时重新起算),故本方法对所有分支的写入点都可枚举。
     *
     * <p><b>追赶上限</b>:玩家离线/卡顿导致到期刻已过去很久时,只补结算**一次**并把计时器
     * 重定到 {@code now + 2:00}(不按离线时长累加伤害,避免登录即被秒)。
     */
    public static void tick(Player player) {
        if (player == null || player.level().isClientSide()) return;
        int count = count(player);
        long now = player.level().getGameTime();
        long next = ModAttachments.getHuoCardNextDamageTick(player);
        if (count <= 0) {
            // N=0:既无该效果、也无周期伤害;计时器一并归 0(下一次持有时重新起算)
            MisfortuneEffect.clear(player);
            if (next != 0L) {
                ModAttachments.setHuoCardNextDamageTick(player, 0L);
            }
            return;
        }
        if (next <= 0) {
            // 首次持有:起算一次(此后只由结算推进)
            ModAttachments.setHuoCardNextDamageTick(player, now + CURSE_PERIOD_TICKS);
        } else if (now >= next) {
            // 结算:伤害 = 结算时刻当前张数
            applyCurseDamage(player, count);
            long advanced = next + CURSE_PERIOD_TICKS;
            if (advanced <= now) advanced = now + CURSE_PERIOD_TICKS;
            ModAttachments.setHuoCardNextDamageTick(player, advanced);
        }
        MisfortuneEffect.mirror(player, count);
    }

    /** 结算一次「厄运」周期伤害:真伤、无来源实体(不是任何人的直接攻击),数值 = 结算时刻当前张数 */
    private static void applyCurseDamage(Player player, int amount) {
        if (amount <= 0) return;
        player.hurt(ModDamageTypes.trueDamage(player.level()), (float) amount);
    }

}
