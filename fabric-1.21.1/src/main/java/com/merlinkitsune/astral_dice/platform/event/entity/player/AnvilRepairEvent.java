/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.merlinkitsune.astral_dice.platform.event.entity.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * AnvilRepairEvent is fired when the player <b>takes the output item</b> out of an anvil. <br>
 * It is called from {@code AnvilMenu#onTake}. <br>
 * <br>
 * {@link #left} contains the left input slot, {@link #right} the right (material) slot and
 * {@link #output} the item the player is taking. <br>
 * {@link #breakChance} is the chance the anvil is damaged after this repair (vanilla default 0.12);
 * it may be changed, and the bridge writes the final value back to the platform callback. <br>
 * <br>
 * <p><b>平台说明(fabric)</b>:这是 Forge 1.20.1 的原生事件;fabric 线由 Puzzles Lib 的
 * {@code AnvilRepairCallback} 转成同一事件后再经 {@link com.merlinkitsune.astral_dice.platform.event.LoaderBus}
 * 派发(注册点:{@code platform.StarengineBridges#installPlayers})。注入点是
 * Puzzles 的 {@code AnvilMenuFabricMixin#onTake$0}(原版 {@code AnvilMenu#onTake} 里
 * {@code Container#setItem} 之前)—— 与 Forge 用 {@code ForgeHooks.onAnvilRepair} 插在
 * 「扣材料 + 清空输入槽」之前的位置语义一致:此时左右输入槽与产物都还在。
 * <p>⚠️ 本事件只在「玩家真的取走成品」时派发一次;**不要**把授奖类逻辑挂到
 * {@code AnvilUpdateEvent}(它在每次点击 / 改名 / 悬停重算时都会触发)。
 * <p>本事件不可取消(Forge 侧亦如此)。
 */
public class AnvilRepairEvent extends PlayerEvent
{
    private final ItemStack left;
    private final ItemStack right;
    private final ItemStack output;
    private float breakChance;

    public AnvilRepairEvent(Player player, ItemStack left, ItemStack right, ItemStack output)
    {
        super(player);
        this.left = left;
        this.right = right;
        this.output = output;
        this.breakChance = 0.12F; // 原版铁砧损坏概率默认值
    }

    /**
     * @return The item in the left input (leftmost) anvil slot.
     */
    public ItemStack getLeft()
    {
        return left;
    }

    /**
     * @return The item in the right input (center) anvil slot.
     */
    public ItemStack getRight()
    {
        return right;
    }

    /**
     * @return The item the player is taking out of the anvil output slot.
     */
    public ItemStack getOutput()
    {
        return output;
    }

    /**
     * @return The chance the anvil is damaged after this repair.
     */
    public float getBreakChance()
    {
        return this.breakChance;
    }

    /**
     * Sets the chance the anvil is damaged after this repair.
     * @param breakChance The new chance, 0..1. Fabric 线的桥会把终值写回平台回调。
     */
    public void setBreakChance(float breakChance)
    {
        this.breakChance = breakChance;
    }
}
