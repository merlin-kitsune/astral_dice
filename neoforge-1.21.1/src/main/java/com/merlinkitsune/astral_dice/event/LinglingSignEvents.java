package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.sign.LinglingSignManager;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * 怪力乱神立牌（{@code lingling}）的被动事件面：拾取转换（§4.2）、丢出转换（§4.5）、
 * 「骰战攻击被吸附目标 +5 伤害」（§4.7，2026-10-27 起只在骰战结算里加，非骰战不再叠加）。购买转换（§4.3）在 {@code CardShopMenu}、
 * 手持转换与 10t 被动刷新在 {@code PlayerTickEvents} / {@code LinglingSignItem#onCurioTick}。
 *
 * <p>全部只在服务端落地且幂等；Curios 会重放 equip / 登录，故任何「发牌」都不挂在这里。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class LinglingSignEvents {

    private LinglingSignEvents() {}

    // ══════════════════════════════════════════════════════════════════════
    //  §4.2 拾取转换：佩戴时到手 stack 换成等量的强化拒止
    // ══════════════════════════════════════════════════════════════════════

    /**
     * {@code Post} 在物品**已进背包之后**才触发，且 NeoForge 明确禁止在事件里调
     * {@code ItemEntity#setItem}（未定义行为）⇒ 这里不改实体、只把玩家的持有集合扫一遍
     * （主物品栏 + 副手），把路障全部换成等张数强化拒止。幂等，且遗留在地上的
     * 那部分会在下一次拾取时自行转换。
     */
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) return;
        // 2026-10-27 用户裁决：拾取后先对账 —— 未佩戴立牌时捡到的强化拒止**卡牌**必须立刻变回路障
        //（物品形态不转换：它可保留，只在使用时被立牌闸门拦住）
        LinglingSignManager.reconcileInventory(player);
        if (!LinglingSignManager.isEquipped(player)) return;
        LinglingSignManager.convertBarricadesInInventory(player);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  §4.5 丢出转换：强化拒止离手即变回普通路障
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 实体入世**之前**把掉落物 stack 换成等量路障（不是取消丢弃事件）；
     * 只做服务端替换，客户端由实体元数据同步拿到正确 stack。
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getEntity() == null) return;
        ItemEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        Player player = event.getPlayer();
        if (player == null || !LinglingSignManager.isEquipped(player)) return;
        ItemStack stack = entity.getItem();
        if (!LinglingSignManager.isRepulsion(stack)) return;
        entity.setItem(LinglingSignManager.toBarricade(stack));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  §4.7 攻击被吸附目标 +5 伤害 —— 2026-10-27 用户裁决：限定骰战
    // ══════════════════════════════════════════════════════════════════════

    // 该 +5 现在**只**在骰战结算里加（见 combat/DiceCombatEvents 玩家攻击分支的最终伤害段）。
    // 法伤 / 技能 / 远程 / 环境等非骰战伤害一律不再叠加（2026-10-27 用户裁决：限定必须为骰战，法术、技能等排除）。
    // 理由：① 骰战路径的 +5 已并入终值，事件侧再加就是 +10（A4 近战审计结论）；
    //       ② 非骰战叠加与规格不符 —— 三语 tooltip / 手册口径已同步改为「骰战攻击被吸附目标 +5」。

}
