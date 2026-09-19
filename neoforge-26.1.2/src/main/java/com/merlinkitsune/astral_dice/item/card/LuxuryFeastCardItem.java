package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import com.merlinkitsune.starenginelib.event.EventTargetCollector;
import com.merlinkitsune.astral_dice.item.chip.FriendshipBadgeChipItem;

/**
 * 奢华大餐(治疗效果牌):**目标选择器类(手持即选择)** —— 主手手持本牌即自动进入目标选择模式(移出手持立即退出),瞄准玩家后左键确认,
 * 或按下鼠标右键对自身使用;此类会话**没有倒计时**,取消/移出手持不消耗卡牌(2026-09-25 用户裁决,取代旧的
 * 「右键自身 / 下蹲右键对其他玩家」两段式)。
 * 生效后:治疗目标及周围 6 格范围内的**友方玩家**——已加入队伍时仅同队/队友;
 * 未加入任何队伍时目标为全服在线玩家(统一经 {@link EventTargetCollector#collectTeamPlayers}),
 * 各恢复使用者最大生命值 30% 的血量。
 * 治疗类效果牌:使用后触发大当家立牌被动"养精蓄锐 +1 层"。
 *
 * <p><b>26.1.2 移植说明</b>：与 1.21.1 基准逐字同形（本牌无任何平台 API 触点：
 * {@code getEntitiesOfClass} / {@code AABB#inflate} / {@code EventTargetCollector} 三线一致）。
 */
public class LuxuryFeastCardItem extends BaseEffectCardItem {
    /** 恢复比例 */
    public static final float HEAL_RATIO = 0.3f;
    /** 治疗扩散半径(格) */
    public static final double RANGE = 6.0;
    /** 目标选择器动作 id(skill 名与动作注册键共用) */
    public static final String ACTION_ID = "luxury_feast";

    static {
        // 可对自身使用(旧方案的「右键-自身使用」)
        registerSelectorAction(ACTION_ID, true);
    }

    public LuxuryFeastCardItem(Properties properties) {
        super(properties);
    }
    @Override
    protected String cardTypeId() {
        return "luxury_feast";
    }

    @Override
    public String selectorActionId() {
        return ACTION_ID;
    }

    @Override
    protected boolean isHealingCard() {
        return true;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        int heal = Math.max(1, (int) (user.getMaxHealth() * HEAL_RATIO));
        AABB aabb = applyTo.getBoundingBox().inflate(RANGE);
        // 队伍过滤:统一经 EventTargetCollector.collectTeamPlayers ——
        // 已加入队伍时仅影响同队/队友;未加入任何队伍时目标为全服在线玩家
        java.util.List<Player> allies = EventTargetCollector.collectTeamPlayers(user);
        var nearby = level.getEntitiesOfClass(Player.class, aabb, p -> p.isAlive());
        for (Player p : nearby) {
            if (p != user && !allies.contains(p)) continue;
            p.heal(heal);
            // 友情徽章:对友方玩家施加治疗时,双方各获得 2 点治愈
            if (p != user) {
                FriendshipBadgeChipItem.onHealApplied(user, p);
            }
        }
    }
}
