package com.merlinkitsune.astral_dice.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 路障的**隐形碰撞方块**（注册 id {@code astral_dice:barricade_collision}，**没有 BlockItem**）。
 *
 * <p>2026-10-17 随「路障」效果牌移植到 forge-1.20.1。
 *
 * <p>为什么需要它：1.20.1 的「实体挡路」只有两条路 —— ①实体自身参与碰撞（会被推动、
 * 会被挤出去、也挡不住跳跃）；②方块碰撞箱（原版栅栏就是这样挡人的）。用户要求
 * 「会像栅栏一样阻止玩家和生物越过」，故取 ②：碰撞箱 {@code (0,0,0)-(1,1.5,1)}
 * —— 高度 1.5 格，与栅栏同高 ⇒ **跳不过去**。
 *
 * <p>其余开关全部为「不可见、不参与其它系统」：
 * <ul>
 *   <li>{@link RenderShape#INVISIBLE}：不渲染（真正的视觉由实体模型提供）；</li>
 *   <li>{@code isPathfindable = false}：怪物寻路把该格视为不可通行 ⇒ 绕行 / 被挡在门外；</li>
 *   <li>{@code isSuffocating / isViewBlocking / isRedstoneConductor} 全 false；
 *       {@code noOcclusion}（不剔除相邻面）；{@code noLootTable}（破坏不掉东西）；</li>
 *   <li>{@code strength(-1.0F, 3600000.0F)}：**不可破坏**（生存玩家拆不掉方块本身；
 *       拆路障走的是打实体扣耐久那条路，见 {@code entity/BarricadeEntity#hurt}）。</li>
 * </ul>
 *
 * <p>生命周期由实体全权管理（放置 / 删除都在 {@code BarricadeEntity} 里），
 * 方块自己不持有任何状态、也没有 BlockEntity。
 *
 * <p><b>1.20.1 平台差异</b>（相对 neoforge-1.21.1 蓝本，逐条按本线源码核实）：
 * <ul>
 *   <li>{@code isPathfindable} 在 1.20.1 是**四参**
 *       {@code (BlockState, BlockGetter, BlockPos, PathComputationType)}
 *       （{@code BlockBehaviour.java:144}，1.21.1 才是两参 {@code (BlockState, PathComputationType)}）；</li>
 *   <li>{@code getCollisionShape} / {@code getRenderShape} 在 1.20.1 的 {@code BlockBehaviour}
 *       里是 **public**（{@code BlockBehaviour.java:432 / :229}，均标注 {@code @Deprecated} 但
 *       「Implementing/overriding is fine」）⇒ 覆写体用 public，不能用 protected（可见性不能收窄）。</li>
 * </ul>
 */
public class BarricadeBlock extends Block {

    /** 栅栏高度的碰撞箱：1.5 格高 ⇒ 跳不过去（与 {@code FenceBlock} 同口径）。 */
    private static final VoxelShape COLLISION_SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.5D, 1.0D);

    public BarricadeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return COLLISION_SHAPE;
    }

    /** 不渲染：可见部分全部由 {@code client/BarricadeRenderer} 的实体模型承担。 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * 寻路层「可走」+ 物理层仍挡路（2026-10-18 实机：旧口径 {@code isPathfindable=false} 会让
     * 怪物的 {@code PathNavigation} 规划不到「路障所占格」⇒ 只站远处盯目标、不靠近也不攻击）。
     * 现在规划层放行、碰撞箱照旧 1.5 格：怪物会走到贴脸处被挡住并进入近战距离攻击路障，
     * 同时**依旧越不过去**（碰撞箱不放行）。
     */
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos,
                                  PathComputationType pathComputationType) {
        return true;
    }

    /** 显式声明该格对寻路为可走（双保险，避免不同版本 WalkNodeEvaluator 的取值路径差异）。 */
    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, Mob mob) {
        return BlockPathTypes.WALKABLE;
    }
}
