package com.merlinkitsune.astral_dice.compat.curios;

/** 单个槽位组的句柄(Curios {@code ICurioStacksHandler} 的子集)。 */
public interface ICurioStacksHandler {

    /** 该组的内容容器。 */
    IItemHandler getStacks();

    /** 该组的槽位数。 */
    int getSlots();
}
