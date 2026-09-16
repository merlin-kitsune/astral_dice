package com.merlinkitsune.astral_dice.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

/**
 * 立牌主动技能触发成功事件(游戏总线)。
 *
 * <p>各立牌类通过订阅本事件注册自身主动技能的 ActionBar 反馈(在立牌类中):
 * 处理器按 {@link #getSignStack()} 匹配自身立牌,发送专属提示并调用
 * {@link #setHandled()}。若没有任何处理器响应(该立牌未注册反馈),
 * 由 {@code BaseSignItem.performSkill} 发送默认提示"xxx立牌:主动技能已启动!"。
 */
public class SignActiveTriggeredEvent extends Event {
    private final Player player;
    private final ItemStack signStack;
    private boolean handled = false;

    public SignActiveTriggeredEvent(Player player, ItemStack signStack) {
        this.player = player;
        this.signStack = signStack;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getSignStack() {
        return signStack;
    }

    /** 立牌处理器调用:表示该立牌已提供(或即将提供)自身 ActionBar 反馈,阻止默认提示 */
    public void setHandled() {
        handled = true;
    }

    public boolean isHandled() {
        return handled;
    }
}
