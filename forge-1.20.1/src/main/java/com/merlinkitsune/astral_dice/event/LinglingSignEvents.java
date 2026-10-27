package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.sign.LinglingSignManager;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
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
 *   <li><b>§4.7 攻击被吸附目标 +5</b>:1.20.1 Forge <b>没有</b> {@code LivingDamageEvent.Pre},
 *       只有单类 {@code LivingDamageEvent}(在 {@code actuallyHurt} 内、护甲/抗性/保护之后派发)。
 *       ⚠️ <b>不能挂 {@code LivingHurtEvent}</b>:骰战处理器
 *       ({@code combat/DiceCombatEvents.java:274 onLivingDamagePre}, 优先级 NORMAL)在
 *       {@code LivingDamageEvent} 上以 {@code event.setAmount((float) finalDmg)}(:822) <b>覆盖式</b>
 *       写入自算终值,任何一个更早发生的加法(护甲前的 {@code LivingHurtEvent} 也在其中)都会被整段丢弃。
 *       而本效果的前置恰是「佩戴骰子 + 立牌」⇒ 骰战路径必然生效 ⇒ 早期挂点等于从未生效
 *       (2026-10-27 实机取证:闸门全真 eq=true trk=true,目标却只掉骰战自算的 3 点,而非 1+5)。
 *       ⇒ 改挂 {@code LivingDamageEvent}(<b>LOWEST</b>,排在骰战的 NORMAL 之后),与 1.21.1 的
 *       {@code LivingDamageEvent.Pre} + LOWEST <b>同阶段同先后</b>。
 *       <b>判据集与蓝本逐条一致</b>:佩戴者<b>本人造成的任意伤害</b>(近战 / 远程 / 法术都算,
 *       <b>不做近战限定</b>)· 目标被<b>任意</b>归属的强化拒止吸附 · 排除 {@code EXTRA_DAMAGE} 防递归。
 *       落地方式 = <b>就在同一次伤害上做加法</b>({@code event.setAmount(event.getAmount() + 5)},
 *       照 {@code ChipDamageHandler} 先例):<b>不嵌套新伤害、不清 i-frame、不绕过护甲</b>。</li>
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

    // ── §4.7 攻击被吸附目标 +5(佩戴者本人造成的**任意**伤害,不限近战) ───────────

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.isCanceled()) return;
        LivingEntity target = event.getEntity();
        if (target == null || target.level().isClientSide) return;
        DamageSource source = event.getSource();
        if (source == null) return;
        // 防递归:本模组派生的额外伤害段不再叠加
        if (source.is(ModDamageTypes.EXTRA_DAMAGE)) return;
        // 防二次结算(2026-10-27):法伤加成走「独立真伤段」时，那一段 hurt 会再次进入本处理器 ⇒ 不再 +5。
        // 否则一次法伤命中在目标被强化拒止吸附时会 +10（基础命中 +5、嵌套的加成段又 +5）。
        if (DamageEffectCardHandler.isApplyingTrueBonus()) return;
        // 判据集与蓝本逐条一致:佩戴者「本人」造成的任意伤害(近战/远程/法术),不做近战限定
        if (!(source.getEntity() instanceof Player player)) return;
        if (!LinglingSignManager.isEquipped(player)) return;
        if (!LinglingSignManager.isTrackedByAny(target)) return;
        // 就在同一次伤害上做加法(不嵌套新伤害、不清 i-frame、不绕过护甲;照 ChipDamageHandler 先例)
        // 2026-10-27 修正挂点:必须挂**最终**的 LivingDamageEvent(LOWEST)。骰战在同类事件的 NORMAL
        // 以 setAmount **覆盖式**写入自算终值(combat/DiceCombatEvents.java:274/822),在 LivingHurtEvent
        // 里加的 +5 会被整段盖掉 —— 而本效果的前置就是「佩戴骰子 + 立牌」,骰战路径必然生效 ⇒ 旧挂点
        // 在 1.20.1 等于从未生效(实机取证见类头 §4.7)。1.21.1 侧挂 LivingDamageEvent.Pre(LOWEST) 同理。
        event.setAmount(event.getAmount() + LinglingSignManager.TRACKED_TARGET_BONUS_DAMAGE);
    }
}
