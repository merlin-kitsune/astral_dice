package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 小猎手立牌（inkshadow，史诗）：主动「暗影之门」+ 被动「双生之影」。
 *
 * <h2>主动「暗影之门」</h2>
 * 在准星命中的地块上方放下<b>起始【传送门】</b>；{@link #WINDOW_TICKS}（30 秒）内再次按下主动技能键，
 * 可在<b>另一处</b>放下<b>终点【传送门】</b>，两扇门成对（{@code ACTIVE}）并开始计时
 * {@link #PAIR_LIFETIME_TICKS}（5:00）。放置窗口期间：+{@code 10%} 移动速度、禁止攻击 /
 * 切换武器 / 与方块实体交互；成对或作废后立即恢复。窗口内死亡 / 退出 / 被击杀 ⇒ 本次作废、
 * 起始门消失（见 {@link InkShadowSignManager#voidPending}）。
 *
 * <p><b>与基类「三态化」的衔接</b>：起始门放下后由 {@link #startActiveLockOnUse} 以
 * {@code lockEnd = 放置截止刻} 进入锁定态（{@code isGateEffectActive = hasPending}）。
 * 第二次按键并不经过 {@code BaseSignItem#performSkill}（锁定闸门会早退），
 * 而是由 {@code ModNetwork.SignActivateMessage} 前置钩子
 * {@link InkShadowSignManager#tryPlaceEndFromKey} 直接放终点门；pending 一清空，
 * {@code SignActiveLockMachine} 下一拍自然结束锁定并按 {@link #ACTIVE_COOLDOWN_TICKS} 起冷却。
 *
 * <h2>被动「双生之影」</h2>
 * 每名玩家首次穿过某一扇【传送门】时，门主人攻击力 +{@code 2}（{@link #STACK_ATTACK_BONUS}），
 * 持续 {@link #STACK_DURATION_TICKS}（2:00），可叠加、刷新不累加；每扇门对每名玩家各计一次。
 * 敌对目标靠近【传送门】受到 {@link #PORTAL_HAZARD_DAMAGE} 点魔法（技能）伤害。
 *
 * <p><b>显示 / 手感常量</b>均为第一版取值，注释「待用户验收调整」。
 */
public class InkShadowSignItem extends BaseSignItem {

    /** 立牌物品注册 id */
    public static final String SIGN_ID = InkShadowSignManager.SIGN_ID;

    /** 放置窗口：第一扇门放下后，第二扇门必须在此时长内放下（30 秒）——待用户验收调整 */
    public static final int WINDOW_TICKS = 600;

    /** 成对传送门的存活时长（5:00）——待用户验收调整 */
    public static final int PAIR_LIFETIME_TICKS = 6000;

    /** 放置射程（格），与金鲤立牌同档 */
    public static final double PLACE_RANGE = 8.0D;

    /**
     * 放置窗口内的移动速度加成（+10%，乘算）——待用户验收调整。
     *
     * <p><b>1.20.1 平台差异</b>：本线 {@link AttributeModifier.Operation} 只有
     * {@code ADDITION / MULTIPLY_BASE / MULTIPLY_TOTAL} 三档；1.21 的
     * {@code ADD_MULTIPLIED_TOTAL} 语义对应本线的 {@code MULTIPLY_TOTAL}（在最终值上乘算）。
     * 1.20.1 的 {@link AttributeModifier} 用 UUID 作 id，故 id 由固定 key 派生（与
     * {@code SignAttributeUtil} 同一口径），同一 key 重复添加只覆盖不叠加。
     */
    public static final AttributeModifier SPEED_AMPLIFIER = new AttributeModifier(
            UUID.nameUUIDFromBytes("astral_dice:inkshadow_place_speed"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            "inkshadow_place_speed",
            0.10D,
            AttributeModifier.Operation.MULTIPLY_TOTAL);

    /** 主动冷却（2:00）——待用户验收调整 */
    public static final int ACTIVE_COOLDOWN_TICKS = 2400;

    /** 被动每层攻击力加成（+2，固定值）——待用户验收调整 */
    public static final double STACK_ATTACK_BONUS = 2.0D;

    /** 被动单层持续时间（2:00）——待用户验收调整 */
    public static final int STACK_DURATION_TICKS = 2400;

    /** 敌对目标灼伤半径（格）——待用户验收调整 */
    public static final double PORTAL_HAZARD_RADIUS = 2.5D;

    /** 敌对目标灼伤伤害（魔法/技能伤害，2 点 = 1 心）——待用户验收调整 */
    public static final float PORTAL_HAZARD_DAMAGE = 2.0F;

    /** 同一玩家两次穿越同一扇门的最小间隔（60 tick = 3 秒，防同 tick 反复触发）——待用户验收调整 */
    public static final int RETRIGGER_TICKS = 60;

    public InkShadowSignItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴小猎手立牌（含骰子总闸门） */
    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.INKSHADOW_SIGN.get());
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        if (InkShadowSignManager.hasPending(player)) {
            return InkShadowSignManager.placeEnd(serverPlayer)
                    ? InteractionResultHolder.success(stack)
                    : InteractionResultHolder.fail(stack);
        }
        return InkShadowSignManager.placeStart(serverPlayer)
                ? InteractionResultHolder.success(stack)
                : InteractionResultHolder.fail(stack);
    }

    /**
     * 起始门放下 ⇒ 进入锁定态，锁定时长 = 本次放置窗口剩余时长。
     *
     * <p>{@code performSkill} 的冷却分支在 {@code handleUse} 之后执行：此刻 pending 已写入，
     * 故这里恒返回 true，既不写 {@code sign_active_cooldown_end}，也让第二次按键的锁定闸门生效。
     */
    @Override
    protected boolean startActiveLockOnUse(Player player, long now) {
        if (!InkShadowSignManager.hasPending(player)) return false;
        long deadline = ModAttachments.getInkShadowPendingDeadline(player);
        beginActiveLock(player, SIGN_ID, deadline);
        return true;
    }

    /** 锁定只在放置窗口内有效：pending 一清空（成对 / 作废），既有锁定机下一拍即收尾并起冷却。 */
    @Override
    protected boolean isGateEffectActive(Player player) {
        return InkShadowSignManager.hasPending(player);
    }

    @Override
    protected int activeCooldownBaseTicks(Player player) {
        return ACTIVE_COOLDOWN_TICKS;
    }

    @Override
    protected int activeCooldownBaseTicksNoBattery(Player player) {
        return ACTIVE_COOLDOWN_TICKS;
    }

    @Override
    protected void onCurioTick(top.theillusivec4.curios.api.SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide()) {
            InkShadowSignManager.tickEquipped(player);
        }
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        InkShadowSignManager.onSignUnequipped(player);
    }
}
