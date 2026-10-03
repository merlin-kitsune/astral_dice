package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import com.merlinkitsune.starenginelib.event.EventTargetCollector;
import com.merlinkitsune.starenginelib.target.TargetType;
import com.merlinkitsune.astral_dice.item.chip.FriendshipBadgeChipItem;
import com.merlinkitsune.astral_dice.combat.PartyRelations;

/**
 * 奢华大餐(治疗效果牌):**目标选择器类(手持即选择)** —— 主手手持本牌即自动进入目标选择模式(移出手持立即退出),瞄准非敌对生物后左键确认,
 * 或按下鼠标右键对自身使用;此类会话**没有倒计时**,取消/移出手持不消耗卡牌(2026-09-25 用户裁决,取代旧的
 * 「右键自身 / 下蹲右键对其他玩家」两段式)。
 * 生效后:治疗目标及周围 6 格范围内的**友方玩家**——已加入队伍时仅同队/队友;
 * 未加入任何队伍时目标为全服在线玩家(统一经 {@link EventTargetCollector#collectTeamPlayers}),
 * 各恢复使用者最大生命值 30% 的血量。
 * 治疗类效果牌:使用后触发大当家立牌被动"养精蓄锐 +1 层"。
 */
public class LuxuryFeastCardItem extends BaseEffectCardItem {
    /** 恢复比例 */
    public static final float HEAL_RATIO = 0.3f;
    /** 治疗扩散半径(格) */
    public static final double RANGE = 6.0;
    /** 目标选择器动作 id(skill 名与动作注册键共用) */
    public static final String ACTION_ID = "luxury_feast";

    static {
        // 目标 = 非敌对生物(敌对生物不可选) ∪ 自身;允许对自身使用(旧方案的「右键-自身使用」)
        registerSelectorAction(ACTION_ID, TargetType.NON_HOSTILE, true);
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
        int amount = Math.max(1, (int) (user.getMaxHealth() * HEAL_RATIO));
        // 主目标必受效 —— 目标选择器类效果牌的目标**可能不是玩家**(2026-10-03 起为「非敌对生物」),
        // 旧的「只遍历 Player」实现会让「对生物使用」变成空放,故主目标单列一次。
        applyFeast(user, applyTo, amount);
        // 扩散:目标周围 RANGE 格内的友方玩家(已加入队伍时仅同队/队友;未加入任何队伍时为全服在线玩家)
        AABB aabb = applyTo.getBoundingBox().inflate(RANGE);
        java.util.List<Player> allies = PartyRelations.collectTeamPlayers(user);
        for (Player p : level.getEntitiesOfClass(Player.class, aabb, p -> p.isAlive())) {
            if (p == applyTo) continue;                 // 主目标已处理
            if (p != user && !allies.contains(p)) continue;
            applyFeast(user, p, amount);
        }
    }

    /**
     * 对单个目标施加「大餐」:常规生物**治疗** {@code amount} 点,**亡灵生物**改为承受等量**魔法伤害**。
     *
     * <p>亡灵受伤而非治疗 = **原版设定**(原版治疗药水对亡灵生效为伤害,见
     * {@code MobEffect.INSTANT_HEALTH} 的 {@code applyEffectTick}),且**与阵营无关** ——
     * 已驯服的亡灵宠物、队伍里的亡灵玩家同样受伤(2026-10-03 用户裁决)。
     */
    private static void applyFeast(Player user, LivingEntity target, int amount) {
        if (target == null || !target.isAlive()) return;
        if (isUndead(target)) {
            target.hurt(target.damageSources().magic(), amount);
            return;
        }
        target.heal(amount);
        // 友情徽章:对**友方玩家**施加治疗时,双方各获得 2 点治愈
        if (target instanceof Player other && other != user) {
            FriendshipBadgeChipItem.onHealApplied(user, other);
        }
    }

    /** 是否亡灵生物(口径 = 原版 {@code #minecraft:undead}:僵尸 / 骷髅 / 凋灵 / 幻翼 …)。 */
    private static boolean isUndead(LivingEntity entity) {
        return entity.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
    }
}