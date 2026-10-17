package com.merlinkitsune.astral_dice.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 路障的**隐形碰撞方块**（注册 id {@code astral_dice:barricade_collision}，**没有 BlockItem**）。
 *
 * <p>为什么需要它：1.21.1 的「实体挡路」只有两条路 —— ①实体自身参与碰撞（会被推动、
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
 */
public class BarricadeBlock extends Block {

    /** 栅栏高度的碰撞箱：1.5 格高 ⇒ 跳不过去（与 {@code FenceBlock} 同口径）。 */
    private static final VoxelShape COLLISION_SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.5D, 1.0D);

    public BarricadeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return COLLISION_SHAPE;
    }

    /** 不渲染：可见部分全部由 {@code client/BarricadeRenderer} 的实体模型承担。 */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** 不参与寻路：怪物把这一格当成墙，于是绕行 / 停在门外，而不是「穿过去」。 */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
