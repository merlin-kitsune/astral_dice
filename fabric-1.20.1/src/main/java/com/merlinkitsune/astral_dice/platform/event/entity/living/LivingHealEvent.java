/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package com.merlinkitsune.astral_dice.platform.event.entity.living;

// DROPPED-FORGE-IMPORT common.MinecraftForge
// (无对应物:Forge 内部工具类 / 嵌套枚举 Event.Result)
import com.merlinkitsune.astral_dice.platform.event.Cancelable;
import net.minecraft.world.entity.LivingEntity;

/**
 * LivingHealEvent is fired when an Entity is set to be healed. <br>
 * This event is fired whenever an Entity is healed in {@link LivingEntity#heal(float)}<br>
 * <br>
 * This event is fired via the {@link ForgeEventFactory#onLivingHeal(LivingEntity, float)}.<br>
 * <br>
 * {@link #amount} contains the amount of healing done to the Entity that was healed. <br>
 * <br>
 * This event is {@link eventbus.api.Cancelable}.<br>
 * If this event is canceled, the Entity is not healed.<br>
 * <br>
 * This event does not have a result. {@link HasResult}<br>
 * <br>
 * This event is fired on the {@link MinecraftForge#EVENT_BUS}.
 **/
@Cancelable
public class LivingHealEvent extends LivingEvent
{
    private float amount;
    public LivingHealEvent(LivingEntity entity, float amount)
    {
        super(entity);
        this.setAmount(amount);
    }

    public float getAmount()
    {
        return amount;
    }

    public void setAmount(float amount)
    {
        this.amount = amount;
    }
}
