package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.item.sign.LinglingSignManager;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.item.ItemTossEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.EntityItemPickupEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 「怪力乱神立牌」(lingling)被动的事件接线(Fabric 1.20.1)。
 *
 * <p>蓝本 = {@code forge-1.20.1/.../event/LinglingSignEvents.java}(逐条语义一致)。
 *
 * <p><b>平台差异(Fabric 1.20.1 / 逐条理由)</b>:
 * <ul>
 *   <li><b>订阅者注解</b>:forge 的 {@code @Mod.EventBusSubscriber} 在 fabric 不存在 ⇒
 *       删注解,改由 {@code AstralDiceMod} 里 {@code LoaderBus.INSTANCE.register(LinglingSignEvents.class)}
 *       显式登记(漏登记 = 编译通过、运行无声);</li>
 *   <li><b>事件基类</b>:{@code net.minecraftforge.event.entity.item.ItemTossEvent} /
 *       {@code ...player.EntityItemPickupEvent} / {@code eventbus.api.SubscribeEvent}
 *       ⇒ 换成本线 {@code platform.event.*} 既有 shim(语义同 forge,经
 *       {@code mixin/bridge/ItemTossBridgeMixin} 与 {@code PuzzlesBridges} 派发);</li>
 *   <li><b>§4.2 拾取</b>:本线同样**没有** 1.21.1 的 {@code ItemEntityPickupEvent.Post},
 *       沿用 forge 的 {@code EntityItemPickupEvent}(拾取**之前**派发)。<b>只把掉落物本身换成
 *       强化拒止,不取消、不丢弃、不碰玩家背包</b> ⇒ 随后原版拾取照常把那 1 张强化拒止放进背包,
 *       数量守恒、不吞物品、不复制。</li>
 *   <li><b>§4.5 丢出</b>:{@code ItemTossEvent} 在掉落物**入世之前**派发(本线由
 *       {@code ItemTossBridgeMixin} 桥接),此时 {@code entity.setItem(...)} 即完成「入世前替换」。</li>
 *   <li><b>§4.7 攻击被吸附目标 +5</b>:<b>2026-10-27 用户裁决</b> ⇒ 该加成<b>只</b>在骰战结算里加
 *       ({@code combat/DiceCombatEvents} 玩家攻击分支的最终伤害段),法伤 / 技能 / 远程 / 环境等
 *       非骰战伤害一律不再叠加;本文件不挂任何伤害事件。</li>
 * </ul>
 */
public final class LinglingSignEvents {

    private LinglingSignEvents() {
    }

    // ── §4.2 拾取路障 ⇒ 自动转强化拒止 ─────────────────────────────────────────

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        // 2026-10-27 用户裁决：立牌离身时拾取到的强化拒止卡牌要立即变回路障（不等下一个 10t 对账）。
        // 本线 EntityItemPickupEvent 在**入包之前**派发：直接改掉落物内容，
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
        if (player == null || player.level().isClientSide()) return;
        if (!LinglingSignManager.isEquipped(player)) return;
        ItemEntity entity = event.getEntity();
        ItemStack stack = entity.getItem();
        if (!LinglingSignManager.isRepulsion(stack)) return;
        entity.setItem(LinglingSignManager.toBarricade(stack));
    }

    // ── §4.7 攻击被吸附目标 +5：2026-10-27 用户裁决后只在骰战结算里加 ─────────────
}
