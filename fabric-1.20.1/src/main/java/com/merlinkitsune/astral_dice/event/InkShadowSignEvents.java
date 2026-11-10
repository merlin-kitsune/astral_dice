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
 * 小猎手立牌（inkshadow）的事件入口。
 *
 * <p>职责：
 * <ul>
 *   <li>服务端每 tick 驱动 {@link InkShadowSignManager#tickPlayer}（放置窗口超时 / 快捷栏锁定 /
 *       被动层数刷新）；</li>
 *   <li>第一次按技能键放下起始门后的 ActionBar 反馈：走公用通道 {@link SignActiveTriggeredEvent}
 *       （与其它立牌同一套「主动技能已启动」反馈，避免两条提示同 tick 抢同一槽位）；
 *       <b>第二次按键</b>（放终点门）由 {@code ModNetwork.SignActivateMessage} 在调用
 *       {@code performSkillForCurio} 之前的前置钩子 {@link InkShadowSignManager#tryPlaceEndFromKey}
 *       消费，本事件此时不会再触发；</li>
 *   <li>放置窗口内禁止攻击与一切交互（右键方块 / 右键物品 / 左键方块 / 实体交互）；不拦 ESC、
 *       不拦技能键本身；</li>
 *   <li>死亡 / 退出 ⇒ 本次放置作废、起始门消失。</li>
 * </ul>
 *
 * <p><b>本线（fabric-1.20.1）平台差异</b>：没有 Forge 的 {@code @Mod.EventBusSubscriber} 自动发现，
 * 本类必须在 {@code AstralDiceMod} 的 {@code LoaderBus.INSTANCE.register(...)} 块里显式登记；
 * 漏登记 = 编译通过但放置窗口的 tick / 交互拦截 / 死亡清理全部永不触发。
 * 事件类型改用本线垫片 {@code platform.event.*}（结构镜像 Forge 同名事件）。
 */
public final class InkShadowSignEvents {

    private InkShadowSignEvents() {
    }

    /**
     * 主动技能第一段反馈：起始门已放下，提示在 30 s 内放终点门。
     *
     * <p>放在这里而不是 {@code InkShadowSignItem.handleUse} 里，是因为 {@code handleUse} 返回成功时
     * {@code BaseSignItem} 也会发一条「主动技能已启动」的 ActionBar —— 两条同 tick 竞争同一槽位。
     * 走公用事件通道后，只有本立牌自己的处理器发言（且 {@code setHandled()} 抑制默认文案）。
     */
    @SubscribeEvent
    public static void onSignActiveTriggered(SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.INKSHADOW_SIGN.get())) {
            event.setHandled();
            if (event.getPlayer() instanceof ServerPlayer player && InkShadowSignManager.hasPending(player)) {
                SignActionBar.sendSignActionBar(player, "msg.astral_dice.inkshadow_place_start");
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        InkShadowSignManager.tickPlayer(player);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && InkShadowSignManager.hasPending(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        blockWhilePlacing(event);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        blockWhilePlacing(event);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        blockWhilePlacing(event);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        blockWhilePlacing(event);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        blockWhilePlacing(event);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InkShadowSignManager.voidPortal(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InkShadowSignManager.voidPortal(player);
        }
    }

    private static void blockWhilePlacing(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && InkShadowSignManager.hasPending(player)) {
            event.setCanceled(true);
        }
    }
}
