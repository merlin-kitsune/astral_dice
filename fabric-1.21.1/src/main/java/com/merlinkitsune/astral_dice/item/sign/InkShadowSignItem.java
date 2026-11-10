package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.compat.curios.SlotContext;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 小猎手立牌（inkshadow，史诗）。
 *
 * <h2>主动「暗影之门」</h2>
 * 第一次按键在准星所指地块放置<b>起始门</b>（未激活、不可穿越），{@link #WINDOW_TICKS}（30s）内
 * 第二次按键放置<b>终点门</b>；两门成对激活、跨维度互通，存活 {@link #PAIR_LIFETIME_TICKS}（5:00）。
 * 放置窗口内移速 +10%，且不能攻击 / 换武器 / 与方块实体交互（见 {@code InkShadowSignEvents}）。
 *
 * <h2>被动「双生之影」</h2>
 * 每名玩家首次穿过一扇门 ⇒ 门主人攻击 +{@link #STACK_ATTACK_BONUS}（2:00，可叠加，逐门逐人各计一次）；
 * 门周围 {@link #PORTAL_HAZARD_RADIUS} 格内的敌对生物每 20t 受 {@link #PORTAL_HAZARD_DAMAGE} 点法术伤害。
 *
 * <h2>与基类「三态化」的衔接</h2>
 * 起始门放下后由 {@link #startActiveLockOnUse} 以 {@code lockEnd = 放置截止刻} 进入锁定态
 * （{@code isGateEffectActive = hasPending}），故窗口期按 J 会被
 * {@code SignActiveLockMachine.isSignActiveLocked} 拦在 {@code performSkill} 之外。
 * 因此<b>第二次按键不经过 {@code performSkill}</b>：{@code network/SignActivatePayload} 先用
 * {@link InkShadowSignManager#tryPlaceEndFromKey} 这个前置钩子直接放终点门（与 forge-1.20.1 同构）；
 * pending 一清空，锁定机下一拍自然结束锁定，冷却走<b>全局立牌规则</b>
 * （{@code GameplayConstants.SIGN_ACTIVE_COOLDOWN_SECONDS}，默认 180 s，含充能封顶与电池减免）。
 */
public class InkShadowSignItem extends BaseSignItem {

    /** 立牌物品注册 id（与 {@link InkShadowSignManager#SIGN_ID} 同一字符串） */
    public static final String SIGN_ID = "astral_dice:inkshadow_sign";

    /** 起始门 → 终点门的放置窗口 30s */
    public static final int WINDOW_TICKS = 600;

    /** 一对门的存活时长 3:00（用户 2026-11-10 二次更正） */
    public static final int PAIR_LIFETIME_TICKS = 3600;

    /** 放置射程（格），与其它立牌同档 */
    public static final double PLACE_RANGE = 8.0D;

    /** 放置窗口内的移速加成：MOVEMENT_SPEED / ADD_MULTIPLIED_TOTAL +10%（待用户验收调整） */
    public static final double SPEED_AMPLIFIER = 0.10D;

    /** 被动每层攻击 +2（待用户验收调整） */
    public static final double STACK_ATTACK_BONUS = 2.0D;

    /** 门前危险区半径（格） */
    public static final double PORTAL_HAZARD_RADIUS = 2.5D;

    /** 门前危险区每 20t 的法术伤害 */
    public static final float PORTAL_HAZARD_DAMAGE = 2.0F;

    /** 同一玩家在同一扇门上的传送冷却 5s（不给任何提示，静默等待） */
    public static final int TELEPORT_COOLDOWN_TICKS = 100;

    /** 玩家站进门里多久才真正传送 1.5s（30t） */
    public static final int STAND_TICKS = 30;

    /** 门主人放置完一对门后，必须先离开这个半径（格）才会被自己的门传送 */
    public static final double OWNER_ARM_RADIUS = 4.0D;

    /**
     * 判定「踩在门正上方」的水平半径（格）。
     *
     * <p>2026-11-10 用户裁决：原实现用实体包围箱外扩 0.6 格判定，范围过大（贴着门边走过也会被传）。
     * 现在必须落在门盘中心 {@code STAND_RADIUS} 之内才算站上，配合 {@code STAND_TICKS} 站定后才传送。
     */
    public static final double STAND_RADIUS = 0.55D;

    /** 移速修饰符的固定 key（重复 apply 只改数值，不叠出多个修饰符） */
    public static final String SPEED_MODIFIER_KEY = "inkshadow_place_speed";

    /** 攻击修饰符的固定 key */
    public static final String ATTACK_MODIFIER_KEY = "inkshadow_stack_attack";

    public InkShadowSignItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴小猎手立牌（Curios stand 槽 + 骰子前置） */
    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.INKSHADOW_SIGN.get());
    }

    /** 放置窗口 = 主动「生效中」：锁定机据此在窗口结束/终点门落地后自动转入冷却。 */
    @Override
    protected boolean isGateEffectActive(Player player) {
        return InkShadowSignManager.hasPending(player);
    }

    /**
     * 起始门放下 ⇒ 以「放置截止刻」为锁上界进入锁定态；{@code performSkill} 的冷却分支在
     * {@link #handleUse} 之后执行，此刻 pending 已写入，故恒返回 true（不写 cooldown_end）。
     */
    @Override
    protected boolean startActiveLockOnUse(Player player, long now) {
        if (!InkShadowSignManager.hasPending(player)) return false;
        beginActiveLock(player, SIGN_ID, InkShadowSignManager.pendingDeadline(player));
        return true;
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.fail(stack);
        }
        BlockPos target = resolvePlacePos(player);
        if (target == null) {
            sendSignActionBar(player, "msg.astral_dice.inkshadow_no_ground");
            return InteractionResultHolder.fail(stack);
        }
        return InkShadowSignManager.placePortal(serverLevel, serverPlayer, target)
                ? InteractionResultHolder.success(stack)
                : InteractionResultHolder.fail(stack);
    }

    /** 准星命中方块的可替换邻格；命中液体/空气或超出射程 ⇒ null。 */
    public static BlockPos resolvePlacePos(Player player) {
        HitResult hit = player.pick(PLACE_RANGE, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = blockHit.getBlockPos().relative(blockHit.getDirection());
        if (!player.level().getBlockState(pos).canBeReplaced()) return null;
        return pos;
    }

    /**
     * 每 20t 刷新被动层数额度（fabric 钩子：基类 {@code curioTick} 已先做客户端/骰子门控，
     * 再回调本方法；与 P0 的 {@code curioTick} 覆写语义等价）。
     */
    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide()
                && player.tickCount % 20 == 0) {
            InkShadowSignManager.refreshStacks(player);
        }
    }

    /** 卸下 ⇒ 收起始门、清层数与攻击修饰符（fabric 钩子：基类 {@code onUnequip} 门控后回调本方法）。 */
    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        if (!player.level().isClientSide()) {
            InkShadowSignManager.onUnequip(player);
        }
    }
}
