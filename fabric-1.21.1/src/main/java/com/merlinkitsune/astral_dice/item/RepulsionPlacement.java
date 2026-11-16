package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.entity.RepulsionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 「强化拒止」的落点判据（**唯一一份**）。
 *
 * <p>2026-10-25 用户裁决：物品形态「强化拒止」再次放置的行为必须与效果牌完全一致 ⇒ 判据不能复制两份。
 * 本类由 {@code item/card/RepulsionCardItem}（效果牌）与 {@link RepulsionItem}（回收后的物品形态）共用：
 * 任何一条规则改动都只改这里，两处自动同步。
 *
 * <p>判据顺序（任一条不满足即 {@code null}）：视线抛物线命中方块面 → 世界内 → 世界边界 → 交互权限
 * → 落点可替换 → 下方是朝上的实心面 → 同格没有存活的强化拒止。
 *
 * <p>1.20.1 平台差异：本类用到的成员（{@code Level#clip} / {@code isInWorldBounds} /
 * {@code getWorldBorder().isWithinBounds} / {@code mayInteract} / {@code BlockState#canBeReplaced} /
 * {@code isFaceSturdy} / {@code getEntitiesOfClass}）两线同签名，**代码逐字相同**。
 */
public final class RepulsionPlacement {

    /** 放置距离（格）：原版方块交互距离。 */
    public static final double PLACE_REACH = 4.5D;

    private RepulsionPlacement() {
    }

    /**
     * 落点解析（客户端 / 服务端**同一份**判据）。
     *
     * @return 可放置的方块坐标；{@code null} = 当前视线落点不合法
     */
    public static BlockPos resolve(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(PLACE_REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;

        BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        if (!level.isInWorldBounds(target)) return null;

        // ① 世界边界 + 交互权限（ServerLevel#mayInteract = 出生点保护 + 世界边界）
        if (!level.getWorldBorder().isWithinBounds(target)) return null;
        if (!level.mayInteract(player, target)) return null;

        // ② 落点本身可替换（空气 / 水 / 草…）
        BlockState atTarget = level.getBlockState(target);
        if (!atTarget.canBeReplaced()) return null;

        // ③ 下方必须是「朝上的实心面」= 原版「放在方块面上」的等价判据
        BlockPos below = target.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return null;

        // ④ 同格已有存活的强化拒止 ⇒ 放不下（本装置不落方块，没有别的办法占住这一格）。
        //    只比 blockPosition，不用 AABB（1.6 宽会溢出到邻格，导致相邻格放不下第二座）
        AABB cell = new AABB(target.getX(), target.getY(), target.getZ(),
                target.getX() + 1.0D, target.getY() + 1.0D, target.getZ() + 1.0D);
        for (Entity entity : level.getEntitiesOfClass(RepulsionEntity.class, cell)) {
            if (entity.isAlive() && !entity.isRemoved() && entity.blockPosition().equals(target)) {
                return null;
            }
        }
        return target;
    }
}
