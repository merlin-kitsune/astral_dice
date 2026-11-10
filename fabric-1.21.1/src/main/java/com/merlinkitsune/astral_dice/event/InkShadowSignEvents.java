package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignManager;
import com.merlinkitsune.astral_dice.item.sign.SignActionBar;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.TickEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDeathEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.AttackEntityEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerInteractEvent;
import com.merlinkitsune.starengine.event.SignActiveTriggeredEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * 小猎手立牌的玩家级事件：窗口 tick、窗口内的行为封锁、死亡/登出作废。
 *
 * <p>窗口内（{@code pending} 存在）封锁：攻击、右键方块/物品、左键方块、与实体交互。
 * **不封锁**技能键本身与 ESC —— 放终点门走的是立牌技能键，菜单/存档退出是原版路径。
 * 维度切换**不**作废：两门本来就允许跨维度，玩家在窗口内换维度去放终点门是合法用法。
 *
 * <p>平台差异（fabric-1.21.1）：P0（neoforge）用 {@code @EventBusSubscriber} 自动订阅、
 * {@code PlayerTickEvent.Post} 单次回调；本线走 {@code platform.event} 兼容层，
 * 由 {@code AstralDiceMod} 显式 {@code LoaderBus.INSTANCE.register(InkShadowSignEvents.class)} 登记，
 * 且 {@link TickEvent.PlayerTickEvent} 每 tick 触发 START/END 两次 ⇒ 这里只处理 {@code Phase.END}，
 * 保证 {@link InkShadowSignManager#tickPlayer} 与 P0 一样每 tick 恰好一次。
 */
public final class InkShadowSignEvents {

    private InkShadowSignEvents() {
    }

    /**
     * 主动技能反馈：**只发一条**（2026-11-10 用户裁决 m03850）。
     *
     * <p>此前「起始之门已放置」由 {@link InkShadowSignManager#placePortal} 在 {@code handleUse} 里直接发，
     * 而 {@code BaseSignItem.performSkill} 第 5 步随后又发默认的「主动技能已启动」——客户端 actionbar 是
     * 单槽位，两条同 tick 竞争、看着像重叠。改为走**公用通道** {@link SignActiveTriggeredEvent}：
     * 本处理器认领该立牌（{@code setHandled()} 抑制默认提示），并在这里发唯一一条。
     * 终点门那一次按键走网络包前置钩子、不经本事件，仍由 {@code placeEndPortal} 自己发。
     */
    @SubscribeEvent
    public static void onSignActiveTriggered(SignActiveTriggeredEvent event) {
        if (!event.getSignStack().is(ModItems.INKSHADOW_SIGN.get())) return;
        event.setHandled();
        if (event.getPlayer() instanceof ServerPlayer player && InkShadowSignManager.hasPending(player)) {
            SignActionBar.sendSignActionBar(player, "msg.astral_dice.inkshadow_place_start");
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player instanceof ServerPlayer player) {
            InkShadowSignManager.tickPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (InkShadowSignManager.hasPending(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // 右键方块会连带打开方块实体界面（箱子/工作台等），窗口内必须一并挡住
        if (InkShadowSignManager.hasPending(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (InkShadowSignManager.hasPending(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (InkShadowSignManager.hasPending(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (InkShadowSignManager.hasPending(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (InkShadowSignManager.hasPending(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 放置窗口内死亡：起始门作废、移速/槽位锁解除（终点门不存在，无需配对清理）
        if (event.getEntity() instanceof ServerPlayer player) {
            InkShadowSignManager.voidPortal(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InkShadowSignManager.voidPortal(player);
        }
    }
}
