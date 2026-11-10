package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignManager;
import com.merlinkitsune.astral_dice.item.sign.SignActionBar;
import com.merlinkitsune.starengine.event.SignActiveTriggeredEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
 * <p><b>1.20.1 平台差异</b>：本线没有 {@code TickEvent.PlayerTickEvent.Post}，必须判
 * {@code phase == TickEvent.Phase.END}（START/END 每 tick 各派发一次）。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
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
