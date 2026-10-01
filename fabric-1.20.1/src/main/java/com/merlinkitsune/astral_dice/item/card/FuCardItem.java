package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 「符卡-福」(风水师立牌 zhao 的专属功能效果牌)。
 *
 * <p><b>使用流程</b>沿用效果牌基类 {@link BaseEffectCardItem} 的「手持即选择」路径
 * (主手手持自动开启目标选择会话;目标类型 {@link TargetType#PLAYER} 且 {@code allowSelf=true}
 * ⇒ 可选**任意玩家(不限队伍)或自身**),效果、出牌登记、各类使用钩子与卡牌消耗全部推迟到确认目标之后。
 *
 * <ul>
 *   <li><b>治疗</b>:对确认的目标(玩家或自身)恢复 <b>2</b> 点生命值;</li>
 *   <li><b>出牌数 +1</b>:走**单点入口** {@link EffectCardPeriod#grantFuCardBonusPlay}(方案 B = 每张各 +1:
 *     本出牌周期计数器累加,计入 {@code getMaxAllowed} 的 extra,受 {@code min(9, 1+extra)} 全局封顶;
 *     与忍者立牌主动的 {@code grantBonusPlay} 二进制槽位**彻底解耦**,两者可叠加);</li>
 *   <li><b>专属绑定</b>:{@code ModDataComponents.OWNER_UUID} + {@link ExclusiveCardUtil};
 *     由本模组发放时即绑定获得者({@link #give}),缺省在首次使用时兜底绑定
 *     ({@link ExclusiveCardUtil#bindIfAbsent});</li>
 *   <li><b>非获得人使用被拒绝</b>:客户端预检与**服务端权威**两处都由基类按
 *     {@link ExclusiveCardUtil#canUse} 拒绝(与既有专属牌同形,本类不额外开分支)。</li>
 * </ul>
 *
 * <p>图标 = {@code images/符卡-福.png}(实装路径 {@code textures/item/fu_card.png})。
 */
public class FuCardItem extends BaseEffectCardItem {
    /** 目标选择器动作 id(注册表键;也是 actionbar 技能名 {@code msg.astral_dice.target_select.skill.<id>} 的来源) */
    public static final String ACTION_ID = "fu_card";

    /** 使用后对目标恢复的生命值 */
    public static final int HEAL_AMOUNT = 2;

    static {
        // 可选「玩家(不限队伍)或自身」:TargetType.PLAYER 排除选择者自身,自用由 allowSelf 打开
        registerSelectorAction(ACTION_ID, TargetType.PLAYER, true);
    }

    public FuCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String cardTypeId() {
        return ACTION_ID;
    }

    // 专属效果牌:仅允许获得者使用(基类的客户端预检与服务端权威判定共用此开关)
    @Override
    protected boolean isExclusive() {
        return true;
    }

    // 目标选择器类:主手手持即自动开启选择会话(无倒计时,移出主手即关闭)
    @Override
    public String selectorActionId() {
        return ACTION_ID;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        // 1. 目标获得 2 点治疗(目标可能是使用者自身)
        if (applyTo != null) {
            applyTo.heal(HEAL_AMOUNT);
        }
        // 2. 出牌数 +1:单点入口(方案 B = 每张各 +1:本周期计数器累加,受 min(9, 1+extra) 封顶)。
        // ⚠️ 必须在本方法(applyEffect)内、且早于 BaseEffectCardItem#registerPlay:后者按"当时的上限"
        //    判定是否打满,若本授予晚于它,上限仍是旧值 ⇒ 本次出牌立刻进入 30 秒冷却(净 0 不成立)。
        EffectCardPeriod.grantFuCardBonusPlay(user);
        // 3. 专属牌:无所有者时绑定获得者(发放路径已绑定;此处只兜底)
        ExclusiveCardUtil.bindIfAbsent(stack, user);
    }

    /**
     * 发放 {@code count} 张**已绑定获得者**的符卡-福。
     *
     * <p>发放统一走 {@link VitaminPillChipItem#giveCard}(既有发牌入口:成功入包时触发维生素药丸;
     * 背包满则掉落、不丢失、不销毁)。{@code owner} 即获得者本人 ⇒ 「非获得人使用被拒绝」
     * 在发放那一刻就已成立。
     */
    public static void give(Player receiver, Player owner, int count) {
        if (receiver == null || owner == null || count <= 0) return;
        if (receiver.level().isClientSide()) return;
        ItemStack stack = new ItemStack(ModItems.FU_CARD.get(), count);
        ExclusiveCardUtil.setOwner(stack, owner);
        VitaminPillChipItem.giveCard(receiver, stack);
    }

    /** 发放 1 张已绑定获得者的符卡-福(便捷重载) */
    public static void give(Player receiver, Player owner) {
        give(receiver, owner, 1);
    }

    /**
     * 玩家当前持有的符卡-福总张数(**主物品栏**口径;只读,tooltip/探针读数用)。
     *
     * <p>⚠️ **与 {@link HuoCardItem#count} 口径不同 —— 此为 2026-09-19 用户裁决的定案口径**:
     * 本方法只算主物品栏 {@code player.getInventory().items};而 {@code HuoCardItem#count} 同日起
     * 为「主物品栏 **+ 副手**」。立牌 tooltip 的「符卡-福 / 符卡-祸」两栏**同屏显示**
     * ({@code tooltip.astral_dice.sign.zhao_cards});裁决原话:「符卡-福在副手计入的意义是?
     * **本身持有并无特殊效果,因此是否计入没有意义**。而目标选择器**只有置于主手才应该生效**」
     * ⇒ 符卡-福**保持主栏口径**,**副手不计入**("牌放进副手"时两栏不一致是**预期表现**,
     * 不是待修的对称性缺陷)。**本方法逻辑不得改动**(要改必须先拿到新的用户裁决)。
     * ⚠️ 1.20.1 的 {@code Inventory#getContainerSize()} 含护甲与副手槽 ⇒ 不得用它遍历。
     */
    public static int countFu(Player player) {
        if (player == null) return 0;
        int total = 0;
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && s.is(ModItems.FU_CARD.get())) total += s.getCount();
        }
        return total;
    }
}
