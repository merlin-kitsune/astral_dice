package com.merlinkitsune.astral_dice.platform.event;

/**
 * 事件基类(Fabric 侧自建) —— 语义与 Forge 1.20.1 的
 * {@code com.merlinkitsune.astral_dice.platform.event.Event} 对齐。
 *
 * <h2>为什么自建而不是用 FAPI 回调</h2>
 * 1.20.1 的 Fabric API **没有任何「可修改伤害值」的事件**({@code ServerLivingEntityEvents.ALLOW_DAMAGE}
 * 只能取消,不能改 amount),而本模组的伤害修正链严重依赖
 * {@link EventPriority} 的**排序语义** —— 例如 {@code ChipDamageHandler} 用 {@code LOWEST}
 * 保证自己在「最终伤害阶段」执行(护甲/药水/吸收结算之后),用 {@code HIGHEST} 的死亡事件抢在
 * 末影骰子的保命逻辑之前。改写成 FAPI 回调会**丢失排序**,行为不可保真。
 * 2. 事件类本身是纯 POJO(已从 Forge sources jar 逐字转译,类名/构造器/访问器**完全一致**)
 * ⇒ 消费方 125 个 {@code @SubscribeEvent} 处理器只需换 import。
 *
 * <p>取消语义用 {@link Cancelable} 注解标记,与 Forge 的 {@code isCancelable()} 判据一致。
 */
public class Event {

    /** 事件结果(与 Forge {@code Event.Result} 同名同义)。 */
    public enum Result {
        DENY,
        DEFAULT,
        ALLOW
    }

    private boolean canceled = false;
    private Result result = Result.DEFAULT;

    /** 本事件是否可被取消(由 {@link Cancelable} 注解决定)。 */
    public boolean isCancelable() {
        return getClass().isAnnotationPresent(Cancelable.class);
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        if (canceled && !isCancelable()) {
            throw new UnsupportedOperationException(
                    "Attempted to cancel a non-cancelable event: " + getClass().getName());
        }
        this.canceled = canceled;
    }

    /** 本事件是否携带结果(由 {@link HasResult} 注解决定)。 */
    public boolean hasResult() {
        return getClass().isAnnotationPresent(HasResult.class);
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        if (!hasResult()) {
            throw new UnsupportedOperationException(
                    "Attempted to set result on a result-less event: " + getClass().getName());
        }
        this.result = result;
    }

    public String getEventName() {
        return getClass().getName();
    }
}
