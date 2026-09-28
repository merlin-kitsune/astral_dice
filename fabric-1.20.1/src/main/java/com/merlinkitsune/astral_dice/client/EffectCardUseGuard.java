package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * 效果牌「一次按下只出一张」的输入层守卫(2026-09-14 严重 BUG 修复)。
 *
 * <p><b>为什么必须在输入层拦</b>:客户端 {@code MultiPlayerGameMode#useItem}
 * (1.21.1 反编译源码 {@code MultiPlayerGameMode.java:380-408})在 {@code startPrediction} 里
 * <b>无论</b> {@code Item#use} 返回什么都会把 {@code ServerboundUseItemPacket} 发出去
 * (连 {@code ItemCooldowns} 命中的分支也照样返回该包),所以靠 {@code Item#use} 里返回
 * {@code fail} 或做客户端预检<b>都拦不住服务端</b>——服务端仍会收到包并照常出牌。
 * 唯一的干净位置是 {@code Minecraft#startUseItem}(按下与「长按自动重复」的共同入口,
 * 由 {@code Minecraft#handleKeybinds} 在右键按住时每次 {@code rightClickDelay} 归零后调用):
 * 在 HEAD 取消它,客户端就<b>不会调用 use、也不会发包</b>,服务端自然一张都不会多出。
 *
 * <p>触发背景(外部 BUG 汇报):玩家在冷却结束/效果结束后<b>长按右键</b>,原版每 4 tick 自动重复一次
 * {@code startUseItem} ⇒ 连续出牌,把"出牌数/上限"链一路推爆,最终撞上
 * {@code EffectCardPeriod} 的不变量违例(见 {@code EffectCardPeriod#tick})而永久锁死。
 *
 * <p>规则:同一次「按住」只允许出第一张牌;必须<b>松开</b>再按才算新的出牌意图。
 * 只对手持效果牌时生效,不影响放置方块/进食/弓箭等其它右键行为。
 */
public final class EffectCardUseGuard {
    /** 本次「按住」是否已经出过(或尝试出过)一张效果牌。 */
    private static boolean playedDuringHold;

    private EffectCardUseGuard() {
    }

    /**
     * 客户端 tick(见 {@code ClientTickHandler}):松开右键即复位 —— 保证"松开再按"是新的出牌意图。
     * 必须每 tick 检查,不能在 {@link #beginOrSuppressUse} 里懒复位:长按时该方法每 tick 都会被调用,
     * 懒复位会把"松手瞬间的下一次按下"误判为同一次长按。
     */
    public static void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || minecraft.options.keyUse == null) return;
        if (!minecraft.options.keyUse.isDown()) {
            playedDuringHold = false;
        }
    }

    /**
     * {@code Minecraft#startUseItem} 的 HEAD 调用:返回 true 表示"这是长按产生的自动重复",应整段取消。
     * 顺带在"本次按下第一次用到效果牌"时打上标记。
     */
    public static boolean beginOrSuppressUse() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null
                || minecraft.options == null || minecraft.options.keyUse == null) {
            return false;
        }
        if (!minecraft.options.keyUse.isDown()) {
            playedDuringHold = false;
            return false;
        }
        if (!holdsEffectCard(minecraft)) {
            return false;                        // 不是效果牌:完全不干预其它物品的右键
        }
        if (playedDuringHold) {
            return true;                         // 长按自动重复:抑制
        }
        playedDuringHold = true;                 // 本次按下已使用(或尝试使用)效果牌
        return false;
    }

    private static boolean holdsEffectCard(Minecraft minecraft) {
        return isEffectCard(minecraft.player.getMainHandItem())
                || isEffectCard(minecraft.player.getOffhandItem());
    }

    private static boolean isEffectCard(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof BaseEffectCardItem;
    }
}
