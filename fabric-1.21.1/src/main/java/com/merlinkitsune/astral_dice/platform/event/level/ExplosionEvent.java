package com.merlinkitsune.astral_dice.platform.event.level;

import com.merlinkitsune.astral_dice.platform.event.Cancelable;
import com.merlinkitsune.astral_dice.platform.event.Event;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

/**
 * 爆炸事件（Fabric 侧最小实现）。
 *
 * <p>逐字对照 {@code forge-1.20.1} 的 {@code net.minecraftforge.event.level.ExplosionEvent}
 * （Forge 47.4.10 sources jar 已核）保留 {@code Event → ExplosionEvent → Start} 三段式：
 * 类名、构造器、访问器与 Forge 完全一致 ⇒ 消费方（{@code event/RepulsionCreeperGuard}）
 * 的判据代码只换 {@code import} 即可，判据一行不动。
 *
 * <p><b>触发点</b>：{@code Level#explode} 的终态（10 参）重载里，{@code new Explosion(...)} 之后、
 * {@code explosion.explode()} <b>之前</b> —— 与 Forge 的 {@code Level.java.patch} 注入位置逐指令对齐
 * （1.20.1 字节码偏移 102 → 127）。派发方见
 * {@code com.merlinkitsune.astral_dice.mixin.bridge.ExplosionStartBridgeMixin}。
 *
 * <p><b>取消语义</b>：取消 ⇒ {@code Explosion#explode()}（实体伤害 / {@code GameEvent.EXPLODE}）与
 * {@code Explosion#finalizeExplosion(boolean)}（方块破坏 / 掉落 / 火 / 音效 / 粒子）<b>都不执行</b>，
 * {@code Level#explode} 仍照常返回该 {@code Explosion} 实例 ⇒ 与 Forge 的
 * {@code if (onExplosionStart(...)) return explosion;} 完全等价（含返回值）。
 *
 * <p>⚠️ 未搬运 Forge 的 {@code Detonate}（不可取消、携带 {@code entityList}）：本模组零使用；
 * 若将来要用，必须补上 {@code Explosion} 内 {@code onExplosionDetonate} 的钩子，否则会得到一个永不派发的事件类。
 */
public class ExplosionEvent extends Event {

    private final Level level;
    private final Explosion explosion;

    public ExplosionEvent(Level level, Explosion explosion) {
        this.level = level;
        this.explosion = explosion;
    }

    public Level getLevel() {
        return level;
    }

    public Explosion getExplosion() {
        return explosion;
    }

    /** 爆炸开始（可取消）。取消 = 这次爆炸整体不发生。 */
    @Cancelable
    public static class Start extends ExplosionEvent {

        public Start(Level level, Explosion explosion) {
            super(level, explosion);
        }
    }
}
