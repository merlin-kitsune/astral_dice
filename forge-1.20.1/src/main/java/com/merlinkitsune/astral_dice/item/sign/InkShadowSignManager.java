package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.entity.InkShadowPortalEntity;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

/**
 * 小猎手立牌（inkshadow）的主动 / 被动结算中心。
 *
 * <p>职责划分（与规格 §3.2 对应）：
 * <ul>
 *   <li>放置：起始门（{@code PENDING}）与终点门，成对后置 {@code ACTIVE} + 5:00 存活；</li>
 *   <li>放置窗口：pending 附件、强制锁定快捷栏、+10% 移速；</li>
 *   <li>作废：死亡 / 退出 / 超窗 / 卸下立牌 ⇒ 起始门消失、窗口状态清零；</li>
 *   <li>被动：层数 + 攻击力修饰符（叠加、刷新不累加、到期移除）。</li>
 * </ul>
 *
 * <p>全部状态只写服务端；6 个附件键均不 sync（见 {@code ModAttachments}）。
 * 传送 / 灼伤 / 存活判定在实体 {@link InkShadowPortalEntity} 内。
 */
public final class InkShadowSignManager {

    /** 立牌物品注册 id（与 {@code ModItems.INKSHADOW_SIGN} 一致） */
    public static final String SIGN_ID = "astral_dice:inkshadow_sign";

    private InkShadowSignManager() {
    }

    // ===== 放置窗口 =====

    public static boolean hasPending(Player player) {
        return player != null && !ModAttachments.getInkShadowPendingUuid(player).isEmpty();
    }

    /** 放下起始门并进入放置窗口；返回 false = 准星未命中可放置面（本次主动失败，不进冷却）。 */
    public static boolean placeStart(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = resolvePlacePos(player);
        if (pos == null) {
            SignActionBar.sendSignActionBarColored(player, ChatFormatting.RED, "msg.astral_dice.inkshadow_no_ground");
            return false;
        }
        long now = level.getGameTime();
        InkShadowPortalEntity start = new InkShadowPortalEntity(level, player,
                InkShadowPortalEntity.STATE_PENDING, now + InkShadowSignItem.WINDOW_TICKS);
        start.setPos(pos.getX() + 0.5D, pos.getY() + 0.25D, pos.getZ() + 0.5D);
        start.setYRot(player.getYRot());
        level.addFreshEntity(start);

        ModAttachments.setInkShadowPendingUuid(player, start.getUUID().toString());
        ModAttachments.setInkShadowPendingDim(player, level.dimension().location().toString());
        ModAttachments.setInkShadowPendingDeadline(player, now + InkShadowSignItem.WINDOW_TICKS);
        int slot = player.getInventory().selected;
        ModAttachments.setInkShadowLockedSlot(player, slot >= 0 && slot < 9 ? slot : -1);
        applyPlaceSpeed(player);
        SignActionBar.sendSignActionBar(player, "msg.astral_dice.inkshadow_placed_start");
        return true;
    }

    /** 放下终点门并让两扇门成对；返回 false = 本拍放不下（窗口保留，可再试）。 */
    public static boolean placeEnd(ServerPlayer player) {
        if (!hasPending(player)) return false;
        InkShadowPortalEntity start = findPendingStart(player);
        if (start == null || start.isRemoved()) {
            // 起始门已因超时 / 区块卸载以外的原因消失 ⇒ 本次作废
            clearPending(player);
            SignActionBar.sendSignActionBar(player, "msg.astral_dice.inkshadow_void");
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = resolvePlacePos(player);
        if (pos == null) {
            SignActionBar.sendSignActionBarColored(player, ChatFormatting.RED, "msg.astral_dice.inkshadow_no_ground");
            return false;
        }
        long now = level.getGameTime();
        InkShadowPortalEntity end = new InkShadowPortalEntity(level, player,
                InkShadowPortalEntity.STATE_PENDING, now + InkShadowSignItem.WINDOW_TICKS);
        end.setPos(pos.getX() + 0.5D, pos.getY() + 0.25D, pos.getZ() + 0.5D);
        end.setYRot(player.getYRot());
        level.addFreshEntity(end);

        long expire = now + InkShadowSignItem.PAIR_LIFETIME_TICKS;
        start.setState(InkShadowPortalEntity.STATE_ACTIVE);
        end.setState(InkShadowPortalEntity.STATE_ACTIVE);
        start.setPairUuid(end.getUUID());
        end.setPairUuid(start.getUUID());
        start.setExpireTick(expire);
        end.setExpireTick(expire);

        clearPending(player);
        SignActionBar.sendSignActionBar(player, "msg.astral_dice.inkshadow_placed_end");
        return true;
    }

    /**
     * 主动技能键的**前置钩子**（由 {@code ModNetwork.SignActivateMessage} 在
     * {@code BaseSignItem.performSkillForCurio} 之前调用）。
     *
     * <p>放置窗口内按第二下时，{@code isSignActiveLocked} 会因锁定态把按键拦死，
     * 故这里绕过整条 {@code performSkill} 直接放终点门；返回 true 表示本按键已被本立牌消费。
     */
    public static boolean tryPlaceEndFromKey(ServerPlayer player) {
        if (!hasPending(player)) return false;
        if (!InkShadowSignItem.isEquipped(player)) {
            voidPending(player);
            return true;
        }
        placeEnd(player);
        return true;
    }

    /** 死亡 / 退出 / 超窗 / 卸下：起始门消失，窗口状态清零（保留已积累的被动层数）。 */
    public static void voidPending(ServerPlayer player) {
        InkShadowPortalEntity start = findPendingStart(player);
        if (start != null && !start.isRemoved()) start.discard();
        clearPending(player);
        SignActionBar.sendSignActionBar(player, "msg.astral_dice.inkshadow_void");
    }

    /** 不发送提示的清场（卸下立牌 / 数据异常时使用）。 */
    public static void clearPending(Player player) {
        ModAttachments.setInkShadowPendingUuid(player, "");
        ModAttachments.setInkShadowPendingDim(player, "");
        ModAttachments.setInkShadowPendingDeadline(player, 0L);
        ModAttachments.setInkShadowLockedSlot(player, -1);
        removePlaceSpeed(player);
    }

    // ===== 每 tick 维护 =====

    /** 由 {@code InkShadowSignEvents}（服务端 PlayerTickEvent.END）与饰品 tick 调用，必须幂等。 */
    public static void tickPlayer(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (hasPending(player)) {
            if (!InkShadowSignItem.isEquipped(player)) {
                voidPending(player);
                return;
            }
            long deadline = ModAttachments.getInkShadowPendingDeadline(player);
            if (deadline > 0 && now > deadline) {
                voidPending(player);
                return;
            }
            lockSelectedSlot(player);
        }
        int stacks = ModAttachments.getInkShadowStacks(player);
        if (stacks > 0) {
            long expire = ModAttachments.getInkShadowStacksExpire(player);
            boolean expired = expire > 0 && now >= expire;
            boolean lostSign = !SignEquipCheck.hasSign(player, ModItems.INKSHADOW_SIGN.get());
            if (expired || lostSign) {
                resetStacks(player);
            }
        }
    }

    /** 饰品 tick 入口（仅服务端、已装备时才会被调用）；与事件入口互为兜底。 */
    public static void tickEquipped(Player player) {
        if (player instanceof ServerPlayer serverPlayer) tickPlayer(serverPlayer);
    }

    /** 放置窗口内禁止切换武器：每 tick 把快捷栏锁回起始门放下时的那一格。 */
    private static void lockSelectedSlot(ServerPlayer player) {
        int slot = ModAttachments.getInkShadowLockedSlot(player);
        if (slot < 0 || slot > 8) return;
        if (player.getInventory().selected != slot) {
            player.getInventory().selected = slot;
            player.connection.send(new ClientboundSetCarriedItemPacket(slot));
        }
    }

    // ===== 被动「双生之影」 =====

    /** 一名玩家首次穿过一扇门：层数 +1，攻击力修饰符按层数覆盖式重算（刷新不累加）。 */
    public static void addStack(Player owner) {
        if (owner == null || owner.level().isClientSide() || !owner.isAlive()) return;
        if (!InkShadowSignItem.isEquipped(owner)) return;
        int stacks = ModAttachments.getInkShadowStacks(owner) + 1;
        ModAttachments.setInkShadowStacks(owner, stacks);
        ModAttachments.setInkShadowStacksExpire(owner, owner.level().getGameTime() + InkShadowSignItem.STACK_DURATION_TICKS);
        // 1.20.1 平台差异：本线的加成档位是 ADDITION（1.21 的 ADD_VALUE 等价物）。
        SignAttributeUtil.apply(owner, Attributes.ATTACK_DAMAGE, "inkshadow_stack_attack",
                InkShadowSignItem.STACK_ATTACK_BONUS * stacks,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
    }

    /** 层数清零并撤销攻击力修饰符（幂等；到期 / 卸下 / 死亡后由 tick 收口）。 */
    public static void resetStacks(Player owner) {
        if (owner == null) return;
        ModAttachments.setInkShadowStacks(owner, 0);
        ModAttachments.setInkShadowStacksExpire(owner, 0L);
        // amount=0 ⇒ SignAttributeUtil 走「移除修饰符」分支，operation 参数只占位。
        SignAttributeUtil.apply(owner, Attributes.ATTACK_DAMAGE, "inkshadow_stack_attack", 0.0D,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
    }

    /** 主动卸下立牌：放置窗口作废 + 被动层数清零（当前层数是「门主人」身份绑定，离身即失效）。 */
    public static void onSignUnequipped(Player player) {
        if (player.level().isClientSide()) return;
        if (hasPending(player)) clearPending(player);
        if (ModAttachments.getInkShadowStacks(player) > 0) resetStacks(player);
    }

    /** 死亡 / 退出：窗口作废（被动层数保留到自然到期）。 */
    public static void onOwnerDeathOrLogout(Player player) {
        if (player.level().isClientSide()) return;
        if (player instanceof ServerPlayer serverPlayer) {
            voidPending(serverPlayer);
        } else if (hasPending(player)) {
            clearPending(player);
        }
    }

    // ===== 工具 =====

    /** 准星命中方块的上表面；命中液体/空气、超出射程或不可替换 ⇒ null。 */
    private static BlockPos resolvePlacePos(Player player) {
        HitResult hit = player.pick(InkShadowSignItem.PLACE_RANGE, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = blockHit.getBlockPos().relative(blockHit.getDirection());
        if (!player.level().getBlockState(pos).canBeReplaced()) return null;
        return pos;
    }

    /** 按 pending 附件找回起始门（可能已换维度）。 */
    private static InkShadowPortalEntity findPendingStart(ServerPlayer player) {
        String raw = ModAttachments.getInkShadowPendingUuid(player);
        if (raw.isEmpty()) return null;
        UUID id;
        try {
            id = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return findPortal(player, id);
    }

    public static InkShadowPortalEntity findPortal(ServerPlayer context, UUID id) {
        if (id == null) return null;
        ServerLevel level = (ServerLevel) context.level();
        for (ServerLevel candidate : level.getServer().getAllLevels()) {
            Entity entity = candidate.getEntity(id);
            if (entity instanceof InkShadowPortalEntity portal) return portal;
        }
        return null;
    }

    /** 给指定维度的任意位置找配对门（实体 tick 用，不依赖玩家）。 */
    public static InkShadowPortalEntity findPortal(Level level, UUID id) {
        if (id == null || level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return null;
        for (ServerLevel candidate : serverLevel.getServer().getAllLevels()) {
            Entity entity = candidate.getEntity(id);
            if (entity instanceof InkShadowPortalEntity portal) return portal;
        }
        return null;
    }

    private static void applyPlaceSpeed(Player player) {
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        if (attribute.getModifier(InkShadowSignItem.SPEED_AMPLIFIER.getId()) == null) {
            attribute.addTransientModifier(InkShadowSignItem.SPEED_AMPLIFIER);
        }
    }

    public static void removePlaceSpeed(Player player) {
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        if (attribute.getModifier(InkShadowSignItem.SPEED_AMPLIFIER.getId()) != null) {
            attribute.removeModifier(InkShadowSignItem.SPEED_AMPLIFIER);
        }
    }
}
