package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.entity.ModEntityTypes;
import com.merlinkitsune.astral_dice.entity.RepulsionEntity;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * 强化拒止（**物品形态**，2026-10-25 用户裁决新增）。
 *
 * <p><b>由来</b>：用户裁决「玩家不能直接对齐造成伤害，改为对其进行 5 次近战会重新掉落物品形式的
 * 强化拒止（不是效果牌）」。⇒ 实体 {@code entity/RepulsionEntity} 的 {@code hurt} 不再扣耐久，
 * 累计 5 次玩家近战命中即把自身**回收**成本物品（带耐久 / 剩余寿命 / 已提供充能三份状态）。
 *
 * <p><b>三份状态</b>（都是 DataComponent，随物品同步，客户端据此画耐久条与 tooltip）：
 * <ul>
 *   <li>{@link #getDurability(ItemStack)} = 关联血量（0..{@link #MAX_DURABILITY}，实体 MAX_HEALTH 同步）；</li>
 *   <li>{@link #getElapsedTicks(ItemStack)} = 已存活 tick 数（再次放置时按 {@code 剩余寿命 = 满寿命 - 已存活} 恢复）；</li>
 *   <li>{@link #getChargeGiven(ItemStack)} = 该座力场**已实际发放**给放置者的充能层数（0..5）。</li>
 * </ul>
 *
 * <p><b>放置</b>：落点判据与效果牌共用 {@link RepulsionPlacement}（用户裁决「行为与效果牌一致」），
 * 放置后 {@code RepulsionEntity#restoreState} 恢复血量 / 剩余寿命 / 已发放充能，并把放置者登记为力场 owner
 * （物品已绑定 owner 时保持原主 —— 专属语义与效果牌一致：非获得者不能放置）。
 */
public class RepulsionItem extends Item {

    /** 满耐久（点）：必须等于实体注册的 MAX_HEALTH（{@code event/ModEntityEvents} 的 50.0D）。 */
    public static final int MAX_DURABILITY = 50;

    /** 满寿命（tick）：24000 = 20:00，必须等于 {@code RepulsionEntity#LIFETIME_TICKS}。 */
    public static final long LIFETIME_TICKS = 24000L;

    public static final String MSG_INVALID = "msg.astral_dice.repulsion_invalid";
    public static final String MSG_PLACED = "msg.astral_dice.repulsion_placed";
    public static final String MSG_NOT_OWNER = "msg.astral_dice.repulsion_not_owner";
    /** 实体每次被玩家近战命中时的动作栏提示（参数：还需次数）。 */
    public static final String MSG_HIT = "msg.astral_dice.repulsion_hit";
    /** 回收成功时的动作栏提示（参数：耐久 / 满耐久）。 */
    public static final String MSG_RECOVERED = "msg.astral_dice.repulsion_recovered";

    public RepulsionItem(Properties properties) {
        super(properties);
    }

    // ── 状态读取 / 构造（实体与 tooltip 共用） ──────────────────────────────

    /** 关联血量（缺省 = 满耐久；夹在 0..{@link #MAX_DURABILITY}）。 */
    public static int getDurability(ItemStack stack) {
        int raw = stack.getOrDefault(ModDataComponents.REPULSION_HEALTH.get(), MAX_DURABILITY);
        return Math.max(0, Math.min(MAX_DURABILITY, raw));
    }

    /** 已存活 tick（缺省 0；夹在 0..{@link #LIFETIME_TICKS}）。 */
    public static int getElapsedTicks(ItemStack stack) {
        int raw = stack.getOrDefault(ModDataComponents.REPULSION_AGE.get(), 0);
        return (int) Math.max(0L, Math.min(LIFETIME_TICKS, raw));
    }

    /** 剩余寿命（tick）。 */
    public static long getRemainingTicks(ItemStack stack) {
        return Math.max(0L, LIFETIME_TICKS - getElapsedTicks(stack));
    }

    /** 已实际发放的充能层数（缺省 0；夹在 0..{@code RepulsionEntity#MAX_CHARGE_STACKS}）。 */
    public static int getChargeGiven(ItemStack stack) {
        int raw = stack.getOrDefault(ModDataComponents.REPULSION_CHARGE.get(), 0);
        return Math.max(0, Math.min(RepulsionEntity.MAX_CHARGE_STACKS, raw));
    }

    /**
     * 构造「回收得到」的物品栈：三份状态全部落进组件。
     *
     * @param health       实体当前血量（= 耐久）
     * @param elapsedTicks 实体已存活 tick
     * @param chargeGiven  该座力场已发放的充能层数
     * @param owner        力场放置者（{@code null} = 未登记 ⇒ 不写 owner 组件）
     */
    public static ItemStack createRecovered(float health, long elapsedTicks, int chargeGiven, UUID owner) {
        ItemStack stack = new ItemStack(ModItems.REPULSION.get());
        stack.set(ModDataComponents.REPULSION_HEALTH.get(),
                Math.max(0, Math.min(MAX_DURABILITY, Math.round(health))));
        stack.set(ModDataComponents.REPULSION_AGE.get(),
                (int) Math.max(0L, Math.min(LIFETIME_TICKS, elapsedTicks)));
        stack.set(ModDataComponents.REPULSION_CHARGE.get(),
                Math.max(0, Math.min(RepulsionEntity.MAX_CHARGE_STACKS, chargeGiven)));
        if (owner != null) {
            stack.set(ModDataComponents.OWNER_UUID.get(), Optional.of(owner));
        }
        return stack;
    }

    // ── 使用（放置） ─────────────────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 专属：只有获得者（或尚未绑定的持有者）能放置
        if (!ExclusiveCardUtil.canUse(player, stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable(MSG_NOT_OWNER).withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        // 与效果牌**同一份**落点判据
        BlockPos target = RepulsionPlacement.resolve(level, player);
        if (target == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable(MSG_INVALID).withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        place(level, player, stack, target);
        return InteractionResultHolder.success(stack);
    }

    /** 实际放置（**仅服务端**）：落位 / 朝向 / 状态恢复 / owner 登记 / 消耗 / 反馈。 */
    private static void place(Level level, Player player, ItemStack stack, BlockPos target) {
        RepulsionEntity repulsion = ModEntityTypes.REPULSION.get().create(level);
        if (repulsion == null) return;

        // 朝向：90° 的倍数（与效果牌一致；本模型是竖直薄片，朝向决定宽面朝哪边）
        float yaw = Math.round(player.getYRot() / 90.0F) * 90.0F;
        repulsion.moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, yaw, 0.0F);
        repulsion.setYHeadRot(yaw);
        repulsion.yBodyRot = yaw;
        // 状态恢复：血量 / 剩余寿命（按已存活 tick 折算）/ 已发放充能
        repulsion.restoreState(getDurability(stack), getElapsedTicks(stack), getChargeGiven(stack));

        // 专属：无所有者时绑定当前玩家（与效果牌 bindIfAbsent 同一语义）；已有所有者 ⇒ 保持原主
        ExclusiveCardUtil.bindIfAbsent(stack, player);
        Optional<UUID> owner = stack.getOrDefault(ModDataComponents.OWNER_UUID.get(), Optional.empty());
        repulsion.setOwnerUUID(owner.orElseGet(player::getUUID));

        if (!level.addFreshEntity(repulsion)) return;

        // 消耗（创造模式不消耗，与效果牌 BaseEffectCardItem#consume 同款判据）
        stack.consume(1, player);

        SoundPlayback.playAt(level, target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                SoundEvents.GLASS_PLACE);
        player.displayClientMessage(Component.translatable(MSG_PLACED).withStyle(ChatFormatting.GREEN), true);
    }

    // ── 耐久条（物品栏里显示关联血量；可见性/宽度/配色均按原版工具口径） ──────────

    @Override
    public boolean isBarVisible(ItemStack stack) {
        // 原版工具口径（2026-10-25 用户裁决）：满耐久不显示耐久条，掉血后才出现
        // （等价于 {@code Item#isBarVisible} 的默认实现 {@code stack.isDamaged()}，本物品的「伤害值」= 50 - 关联血量）
        return getDurability(stack) < MAX_DURABILITY;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.max(1, Math.round(13.0F * getDurability(stack) / MAX_DURABILITY));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = getDurability(stack) / (float) MAX_DURABILITY;
        // 与 ItemStack#getBarColor 同一套「红→青」渐变（色相 0..1/3）
        return Mth.hsvToRgb(ratio / 3.0F, 1.0F, 1.0F);
    }
}
