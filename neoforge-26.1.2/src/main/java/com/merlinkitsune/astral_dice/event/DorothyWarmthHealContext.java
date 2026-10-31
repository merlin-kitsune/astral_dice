package com.merlinkitsune.astral_dice.event;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 多萝西被动「温暖」的治疗来源上下文。
 *
 * <p>真正调用 {@code LivingEntity.heal} 的只有两类标注来源：瞬间治疗
 * （1.21.1 {@code HealOrHarmMobEffect}、1.20.1 {@code MobEffect}）与生命恢复
 * （{@code RegenerationMobEffect} / 1.20.1 {@code MobEffect}）。对应 Mixin 在
 * HEAD 压入「来源 + 效果等级」、RETURN 弹出；{@link NewSignEvents} 的
 * {@code LivingHealEvent} 监听据此决定本次治疗叠几层温暖。
 *
 * <p>ThreadLocal 栈支持嵌套（例如生命恢复 tick 内又被其他逻辑调用 heal）：
 * 内层弹出后外层来源仍然可见。栈空即 {@link ThreadLocal#remove()}，避免
 * 线程本地变量在服务端线程上留下空容器。
 *
 * <p>HEAD push 之后若走不到 RETURN（其他模组在本 Mixin 之后取消 / 监听器抛异常），
 * 栈会残留过期来源。为此 {@link #MAX_NESTING} 设保守上限：正常嵌套只有个位数深度，
 * 只有未配对增长才会触顶；触顶即清空旧帧后重新压入本帧，后续治疗退回「无来源 +1」。
 */
public final class DorothyWarmthHealContext {

    public enum Kind {
        INSTANT,
        REGENERATION
    }

    public static final class Source {
        private final Kind kind;
        private final int level;

        public Source(Kind kind, int level) {
            this.kind = kind;
            this.level = level;
        }

        public Kind kind() {
            return kind;
        }

        public int level() {
            return level;
        }
    }

    private static final ThreadLocal<Deque<Source>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 未配对压栈的保守上限。正常嵌套只有个位数深度；只有 HEAD 之后走不到 RETURN
     * （其他模组取消 / 监听器抛异常）时，栈才会持续增长到本上限。触顶即视为栈已失同步：
     * 清掉旧帧后重新压入本帧，后续治疗退回「无来源 +1」，不再沿用过期来源等级。
     */
    private static final int MAX_NESTING = 64;

    private DorothyWarmthHealContext() {
    }

    public static void push(Kind kind, int level) {
        Deque<Source> stack = STACK.get();
        if (stack.size() >= MAX_NESTING) {
            stack.clear();
        }
        stack.push(new Source(kind, level));
    }

    public static Source current() {
        return STACK.get().peek();
    }

    public static void pop() {
        Deque<Source> stack = STACK.get();
        if (stack.isEmpty()) return;
        stack.pop();
        if (stack.isEmpty()) STACK.remove();
    }
}
