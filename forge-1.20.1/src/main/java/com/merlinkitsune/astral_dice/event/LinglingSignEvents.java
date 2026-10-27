package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.sign.LinglingSignManager;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「怪力乱神立牌」(lingling)被动的事件接线(1.20.1 Forge 移植版)。
 *
 * <p>蓝本 = {@code neoforge-1.21.1/.../event/LinglingSignEvents.java}(只读)。
 *
 * <p><b>平台差异(1.20.1 Forge / 逐条理由)</b>:
 * <ul>
 *   <li><b>§4.2 拾取</b>:1.21.1 用 {@code ItemEntityPickupEvent.Post}(拾取<b>已完成</b>的回调);
 *       1.20.1 Forge <b>没有该事件</b> ⇒ 用 {@code EntityItemPickupEvent}(拾取<b>之前</b>派发,
 *       照 {@code economy/StarCoinPickupHandler} / {@code TeruSignItem} 先例)。<b>只把掉落物本身换成
 *       强化拒止,不取消、不丢弃、不碰玩家背包</b> ⇒ 随后原版拾取照常把那 1 张强化拒止放进背包,
 *       数量守恒、不吞物品、不复制。</li>
 *   <li><b>§4.5 丢出</b>:{@code ItemTossEvent} 在掉落物**入世之前**派发(照
 *       {@code event/TemporaryCardEvents} 用法),此时 {@code entity.setItem(...)} 即完成「入世前替换」。</li>
 *   <li><b>§4.7 攻击被吸附目标 +5</b>：<b>2026-10-27 用户裁决</b> ⇒ 该加成<b>只</b>在骰战结算里加
 *       ({@code combat/DiceCombatEvents} 玩家攻击分支的最终伤害段)，法伤 / 技能 / 远程 / 环境等
 *       非骰战伤害一律不再叠加；本文件不再挂任何伤害事件。</li>
 * </ul>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class LinglingSignEvents {

    private LinglingSignEvents() {
    }

    // ── §4.2 拾取路障 ⇒ 自动转强化拒止 ─────────────────────────────────────────

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        // 2026-10-27 用户裁决：立牌离身时拾取到的强化拒止卡牌要立即变回路障（不等下一个 10t 对账）。
        // Forge 的 EntityItemPickupEvent 在**入包之前**派发（见本类 §4.2 说明）：直接改掉落物内容，
        // 让它以路障形态入包，规则①的「立即」才算成立。
        LinglingSignManager.reconcileInventory(player);
        ItemEntity itemEntity = event.getItem();
        if (!LinglingSignManager.hasSign(player)) {
            ItemStack picked = itemEntity.getItem();
            if (LinglingSignManager.isRepulsion(picked)) {
                itemEntity.setItem(LinglingSignManager.toBarricade(picked));
            }
            return;
        }
        // 立牌在位仍需骰子才恢复「路障 ⇒ 强化拒止」的入包转换（与 neo 的同名判断一致，也和本文件
        // onItemToss 的 isEquipped 口径对齐）——本批只改「没有立牌」那一支，不放开骰子前提。
        if (!LinglingSignManager.isEquipped(player)) return;
        ItemStack stack = itemEntity.getItem();
        if (!LinglingSignManager.isBarricade(stack)) return;
        // 只换掉落物内容(数量不变);不取消拾取、不入背包、不丢弃 ⇒ 原版随后正常入包
        itemEntity.setItem(LinglingSignManager.toRepulsion(stack.copy(), player));
    }

    // ── §4.5 丢出强化拒止 ⇒ 入世前替换为路障 ───────────────────────────────────

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide) return;
        if (!LinglingSignManager.isEquipped(player)) return;
        ItemEntity entity = event.getEntity();
        ItemStack stack = entity.getItem();
        if (!LinglingSignManager.isRepulsion(stack)) return;
        entity.setItem(LinglingSignManager.toBarricade(stack));
    }

    // ── §4.7 攻击被吸附目标 +5：2026-10-27 用户裁决后只在骰战结算里加 ─────────────

    // 本文件只做拾取(§4.2)与丢出(§4.5)两条转换。骰战路径的 +5 已并入骰战终值，
    // 非骰战（法伤 / 技能 / 远程 / 环境）不再叠加（用户裁决：限定必须为骰战）。
}
