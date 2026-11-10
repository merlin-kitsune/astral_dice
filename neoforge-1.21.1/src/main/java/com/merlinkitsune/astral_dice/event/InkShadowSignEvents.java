package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 小猎手立牌的玩家级事件：窗口 tick、窗口内的行为封锁、死亡/登出作废。
 *
 * <p>窗口内（{@code pending} 存在）封锁：攻击、右键方块/物品、左键方块、与实体交互。
 * **不封锁**技能键本身与 ESC —— 放终点门走的是立牌技能键，菜单/存档退出是原版路径。
 * 维度切换**不**作废：两门本来就允许跨维度，玩家在窗口内换维度去放终点门是合法用法。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class InkShadowSignEvents {

    private InkShadowSignEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
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
