package com.merlinkitsune.astral_dice.platform.event.level;

import com.merlinkitsune.astral_dice.platform.event.Cancelable;
import com.merlinkitsune.astral_dice.platform.event.Event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 方块事件(Fabric 侧最小实现)。
 *
 * <p>本模组只用到 {@link BreakEvent} 一个成员,故这里按 Forge 的类层次保留
 * {@code Event → BlockEvent → BreakEvent} 三段,并去掉 Forge 侧那些依赖
 * {@code BlockSnapshot} / {@code ToolAction} 的嵌套类(本模组零使用)。
 *
 * <p>⚠️ {@code BreakEvent} 的 {@code exp} 语义(Forge 用 {@code ForgeHooks.isCorrectToolForDrops}
 * 计算掉落经验)**未被搬运**:本模组只读 {@code getLevel/getPos/getState/getPlayer},
 * 从不读写 {@code getExpToDrop}。若将来要使用,必须补齐该计算,否则会拿到恒为 0 的值。
 */
public class BlockEvent extends Event {

    private final Level level;
    private final BlockPos pos;
    private final BlockState state;

    public BlockEvent(Level level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    public Level getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    /** 玩家破坏方块(可取消)。 */
    @Cancelable
    public static class BreakEvent extends BlockEvent {
        private final Player player;
        private int exp;

        public BreakEvent(Level level, BlockPos pos, BlockState state, Player player) {
            super(level, pos, state);
            this.player = player;
        }

        public Player getPlayer() {
            return player;
        }

        public int getExpToDrop() {
            return isCanceled() ? 0 : exp;
        }

        public void setExpToDrop(int exp) {
            this.exp = exp;
        }
    }
}
