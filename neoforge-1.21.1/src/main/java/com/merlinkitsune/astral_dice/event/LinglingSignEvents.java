package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.DiceCombatEvents;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.sign.LinglingSignManager;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * 怪力乱神立牌（{@code lingling}）的被动事件面：拾取转换（§4.2）、丢出转换（§4.5）、
 * 「攻击被吸附目标 +5 伤害」（§4.7）。购买转换（§4.3）在 {@code CardShopMenu}、
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
    //  §4.7 攻击被吸附目标 +5 伤害
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 佩戴者**本人**造成的任意伤害（近战 / 远程 / 法术）命中「正被任意强化拒止吸附」的目标时 +5。
     *
     * <p>三条判据：① {@code source.getEntity()} 必须是佩戴 lingling 的玩家（宠物 / 荆棘 / 反伤不算）；
     * ② 目标被**任意**归属的强化拒止吸附即可（不分归属）；③ 排除本模组的
     * {@code ExtraDamage} 类型以防自递归加伤。{@code LOWEST} 优先级 ⇒ 最后加，其它系统的
     * 倍率类改动都已结算完。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target == null || target.level().isClientSide()) return;
        DamageSource source = event.getSource();
        if (source == null) return;
        // 防递归：本模组派生的额外伤害段不再叠加
        if (source.is(ModDamageTypes.EXTRA_DAMAGE)) return;
        // 法伤加成已于 2026-10-27(方案 A)折叠进本次伤害事件 ⇒ 同一击不再派发第二段 hurt,
        // 原先这里用于排除「加成段」的 `DamageEffectCardHandler.isApplyingTrueBonus()` 闸门已删除。
        // 旧行为订正(2026-10-27,与 forge 线核对):那道闸门**正好排除**了嵌套的加成段,
        // 所以旧实现本来就是「+5 只加一次」,并非「基础段 +5 + 加成段 +5」。折叠前后均为 +5 一次。
        // A4(2026-10-27 近战审计):骰战路径的 +5 已并入骰战终值(DiceCombatEvents 玩家路径),
        //   这里必须跳过,否则与 ChipDamageHandler(@LOWEST) 的先后不可依赖(气囊归零后仍 +5 /
        //   +5 先加则气囊白烧充能)。
        if (DiceCombatEvents.isDiceBattleSettledDamage(source, target)) return;
        if (!(source.getEntity() instanceof Player player)) return;
        if (!LinglingSignManager.isEquipped(player)) return;
        if (!LinglingSignManager.isTrackedByAny(target)) return;
        // 已被其它系统减到 0 / 负值(如安全气囊归零)时不再追加,否则会把「免死」变成「掉 5 点」。
        if (event.getNewDamage() <= 0.0F) return;
        event.setNewDamage(event.getNewDamage() + LinglingSignManager.TRACKED_TARGET_BONUS_DAMAGE);
    }
}
