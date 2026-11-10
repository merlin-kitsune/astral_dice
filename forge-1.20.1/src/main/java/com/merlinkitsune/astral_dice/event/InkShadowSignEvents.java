package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignManager;
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
 *       被动层数到期）；</li>
 *   <li>放置窗口内禁止攻击与一切交互（右键方块 / 右键物品 / 左键方块 / 实体交互）；不拦 ESC、
 *       不拦主动技能键（主动键由 {@code ModNetwork.SignActivateMessage} 的前置钩子处理）；</li>
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
            InkShadowSignManager.onOwnerDeathOrLogout(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        InkShadowSignManager.onOwnerDeathOrLogout(event.getEntity());
    }

    private static void blockWhilePlacing(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && InkShadowSignManager.hasPending(player)) {
            event.setCanceled(true);
        }
    }
}
