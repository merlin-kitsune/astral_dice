package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import com.merlinkitsune.astral_dice.event.api.AstralEventTrigger;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 12 个事件的**具体效果**（2026-10-10 用户逐条清单；作用对象由分发器按「64 格 ∪ 团队」或收窄口径给出）。
 *
 * <p>口径与暂缓项（如实登记，待用户补充细节）：
 * <ul>
 *   <li>「攻击力 / 防御力」按本模组口径映射：攻击力 = `Attributes.ATTACK_DAMAGE` 的 ADD_VALUE（与 `NetherStarDiceItem` 同款），
 *       防御力 = {@link DiceCombatModifiers#setDefenseArmorBonus}（1 防御 = 2 护甲）。</li>
 *   <li>带时长的效果用 {@link EventTimerStore} 登记（覆盖/重置语义）；**属性增益是瞬态修饰符**，
 *       到期由计时器回调移除，**不跨重登持久**。</li>
 *   <li>**状态指示器（取 images 同名图标）与「屏蔽中毒/饥饿指示器、显示专用指示器」暂缓** ——
 *       需客户端 HUD + 服务端→客户端同步，等用户补充细节后再做。</li>
 * </ul>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class EventEffects {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventEffects.class);

    private static final String ATK_KEY_PREFIX = "event_atk_";

    private EventEffects() {
    }

    // ────────────────────────── 属性增益工具 ──────────────────────────

    /** 施加/覆盖一个瞬态攻击力修饰符；amount <= 0 表示移除。 */
    private static void setAttack(Player player, String key, double amount) {
        AttributeInstance attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr == null) return;
        // ⚠️ 平台差异:1.20.1 的修饰符以 **UUID** 为键(1.21.1 已改为 ResourceLocation)⇒ 由稳定 key 派生 UUID
        java.util.UUID id = modifierId(key);
        AttributeModifier existing = attr.getModifier(id);
        if (amount <= 0) {
            if (existing != null) attr.removeModifier(id);
            return;
        }
        if (existing == null || existing.getAmount() != amount) {
            attr.removeModifier(id);
            attr.addTransientModifier(new AttributeModifier(id, key, amount, AttributeModifier.Operation.ADDITION));
        }
    }

    /** 由稳定 key 派生修饰符 UUID(1.20.1 口径)。 */
    private static java.util.UUID modifierId(String key) {
        return java.util.UUID.nameUUIDFromBytes(("astral_dice:" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 给生物加一个瞬态攻击力/护甲修饰符(敌对生物用;key 需稳定以便到期移除)。 */
    private static void setMobAttribute(LivingEntity mob, String key, net.minecraft.world.entity.ai.attributes.Attribute attribute, double amount) {
        AttributeInstance attr = mob.getAttribute(attribute);
        if (attr == null) return;
        java.util.UUID id = modifierId(key);
        AttributeModifier existing = attr.getModifier(id);
        if (amount <= 0) {
            if (existing != null) attr.removeModifier(id);
            return;
        }
        if (existing == null || existing.getAmount() != amount) {
            attr.removeModifier(id);
            attr.addTransientModifier(new AttributeModifier(id, key, amount, AttributeModifier.Operation.ADDITION));
        }
    }

    // ────────────────────────── 1. 红温警告:攻击力+5,3:00 ──────────────────────────

    public static void redHeatWarning(ServerLevel level, Player triggerer, Player target, AstralEvent event,
                                      AstralEventTrigger source, int tier) {
        // 指示器（用户裁决 1/2）：同名图标的自有 MobEffect ⇒ 原版效果 HUD 双端同步显示
        target.addEffect(new MobEffectInstance(com.merlinkitsune.astral_dice.effect.ModEffects.RED_HEAT_WARNING.get(),
                3600, 0, false, false, true));
        setAttack(target, ATK_KEY_PREFIX + "red_heat_warning", 5.0D);
        EventTimerStore.start(target, event.id(), 3600, p -> setAttack(p, ATK_KEY_PREFIX + "red_heat_warning", 0));
    }

    // ────────────────────────── 2. 疾行:迅捷 II (2:00) ──────────────────────────

    public static void swiftStride(ServerLevel level, Player triggerer, Player target, AstralEvent event,
                                   AstralEventTrigger source, int tier) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2400, 1, false, true));
        EventTimerStore.start(target, event.id(), 2400, null);
    }

    // ────────────────────────── 3. 雷暴将至:1 张随机卡牌 + 范围内敌对生物攻防 +2,2:00（夜间刷怪 ×2 暂缓） ──────────────────────────

    public static void stormApproaching(ServerLevel level, Player triggerer, Player target, AstralEvent event,
                                        AstralEventTrigger source, int tier) {
        // 指示器（用户裁决 1/2）：雷暴将至也会在效果栏显示同名图标；同时作为「夜间刷怪 ×2」钩子的门禁
        target.addEffect(new MobEffectInstance(com.merlinkitsune.astral_dice.effect.ModEffects.STORM_APPROACHING.get(),
                2400, 0, false, false, true));
        RandomCardHandler.giveCardTo(target, RandomCardHandler.CardCategory.ALL);
        AABB box = triggerer.getBoundingBox().inflate(event.radius());
        List<LivingEntity> mobs = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e instanceof Enemy) mobs.add(e);
        }
        for (LivingEntity mob : mobs) {
            // 攻击力 +2;防御力 +2(1 防御 = 2 护甲 ⇒ +4 护甲),持续 2:00 后由计时器回调移除
            setMobAttribute(mob, "event_storm_atk", Attributes.ATTACK_DAMAGE, 2.0D);
            setMobAttribute(mob, "event_storm_def", Attributes.ARMOR, 4.0D);
        }
        EventTimerStore.start(target, event.id(), 2400, p -> {
            for (LivingEntity mob : mobs) {
                setMobAttribute(mob, "event_storm_atk", Attributes.ATTACK_DAMAGE, 0);
                setMobAttribute(mob, "event_storm_def", Attributes.ARMOR, 0);
            }
        });
        LOGGER.info("AP_EVENT_EFFECT: storm_approaching:mobs={}:nightSpawnX2=hook", mobs.size());
    }

    // ────────────────────────── 4. 取长补短：血量最低者 +8、最高者 -8（集合层结算） ──────────────────────────

    /**
     * 集合层结算（由 {@link AstralEventDispatcher} 在逐目标施加之后调用）：
     * 取受影响玩家中生命值**最低**者 +8、**最高**者 -8（均以 0 为下限）。
     * 若受影响集合只有一人，只按最低结算（+8），不扣血。
     */
    public static void learnFromOthersApply(List<Player> targets) {
        if (targets.isEmpty()) return;
        Player lowest = null;
        Player highest = null;
        List<Player> group = List.copyOf(targets);
        for (Player p : group) {
            if (lowest == null || p.getHealth() < lowest.getHealth()) lowest = p;
            if (highest == null || p.getHealth() > highest.getHealth()) highest = p;
        }
        if (lowest == null || highest == null) return;
        if (group.size() == 1 || lowest == highest) {
            // 集合只有一人**或并列同一人**(血量完全相同):只按最低结算(+8),不扣血
            lowest.heal(8.0F);
        } else {
            lowest.heal(8.0F);
            highest.setHealth(Math.max(0.0F, highest.getHealth() - 8.0F));
        }
        LOGGER.info("AP_EVENT_EFFECT: learn_from_others:low={}:high={}", lowest.getName().getString(),
                (group.size() == 1 || lowest == highest ? "-" : highest.getName().getString()));
    }

    /** 分发器后置钩子：处理需要「整个受影响集合」才能结算的事件（目前只有取长补短）。 */
    public static void afterDispatch(AstralEvent event, List<Player> targets) {
        if (AstralEventIds.LEARN_FROM_OTHERS.equals(event.id())) {
            learnFromOthersApply(targets);
        }
    }

    // ────────────────────────── 「雷暴将至」夜间刷怪数量 ×2（用户裁决 3：直接建钩子） ──────────────────────────

    /**
     * 夜间、且**存在带「雷暴将至」指示器的玩家**时，把敌对生物的自然刷新**再来一只**（数量 ×2）。
     *
     * <p>用平台刷怪收尾事件实现（非 mixin）：1.20.1 = {@code MobSpawnEvent.FinalizeSpawn}。
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onFinalizeSpawn(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn event) {
        // 1.20.1 的 MobSpawnEvent.FinalizeSpawn#getEntity() 已声明为 Mob ⇒ 直接 instanceof Enemy 判定
        Mob mob = event.getEntity();
        if (!(mob instanceof Enemy)) return;
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (!level.isNight()) return;
        if (!hasStormWatcherNear(level, mob)) return;
        net.minecraft.world.entity.Entity copy = mob.getType().create(level);
        if (copy == null) return;
        copy.moveTo(mob.getX() + 1.0D, mob.getY(), mob.getZ() + 1.0D, mob.getYRot(), mob.getXRot());
        level.addFreshEntity(copy);
        LOGGER.info("AP_EVENT_EFFECT: storm_night_spawn_x2:type={}", mob.getType());
    }

    /** 附近（64 格内）是否有玩家正处于「雷暴将至」状态。 */
    private static boolean hasStormWatcherNear(ServerLevel level, LivingEntity mob) {
        for (Player p : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(64.0D))) {
            if (p.hasEffect(com.merlinkitsune.astral_dice.effect.ModEffects.STORM_APPROACHING.get())) return true;
        }
        return false;
    }
    // ────────────────────────── 计时器惰性到期(每 10 tick) ──────────────────────────

    /**
     * 服务端玩家 tick:每 10 tick 做一次**惰性到期检测** —— 到期回调(撤销属性增益、解除治疗封锁)在此触发。
     *
     * <p>平台差异:1.20.1 是 {@code TickEvent.PlayerTickEvent}(需判 {@code phase == END}),1.21.1 是 {@code PlayerTickEvent.Post}。
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        sweepTimers(player);
    }

    private static void sweepTimers(ServerPlayer player) {
        if (player.tickCount % 10 != 0) return;
        for (ResourceLocation id : EventTimerStore.activeEventIds(player)) {
            EventTimerStore.remaining(player, id);
        }
    }

    // ────────────────────────── 治疗失效拦截（问题外卖 15s） ──────────────────────────

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (event.getEntity() instanceof Player player && isHealBlocked(player)) {
            event.setCanceled(true);
        }
    }

    // ────────────────────────── 5. 商店大酬宾:商店出售卡牌 +2,5:00 ──────────────────────────

    public static void shopBonanza(ServerLevel level, Player triggerer, Player target, AstralEvent event,
                                   AstralEventTrigger source, int tier) {
        long now = level.getGameTime();
        ModAttachments.setShopPromotionUntil(target, now + 6000);
        LOGGER.info("AP_EVENT_EFFECT: shop_bonanza:until={}", now + 6000);
    }

    // ────────────────────────── 6. 手牌抹杀:随机丢弃物品栏里一张随机卡牌 ──────────────────────────

    public static void handErasure(ServerLevel level, Player triggerer, Player target, AstralEvent event,
                                   AstralEventTrigger source, int tier) {
        List<Integer> cardSlots = new ArrayList<>();
        for (int i = 0; i < target.getInventory().getContainerSize(); i++) {
            if (ModItems.isCardItem(target.getInventory().getItem(i))) cardSlots.add(i);
        }
        if (cardSlots.isEmpty()) {
            LOGGER.info("AP_EVENT_EFFECT: hand_erasure:no_card:target={}", target.getName().getString());
            return;
        }
        int slot = cardSlots.get(target.getRandom().nextInt(cardSlots.size()));
        ItemStack removed = target.getInventory().removeItemNoUpdate(slot);
        if (!removed.isEmpty()) {
            target.drop(removed, false);
            LOGGER.info("AP_EVENT_EFFECT: hand_erasure:dropped={}:target={}", removed.getItem(), target.getName().getString());
        }
    }

    // ────────────────────────── 7. 天崩地裂:立即 4 点伤害 ──────────────────────────

    public static void worldCollapse(ServerLevel level, Player triggerer, Player target, AstralEvent event,
                                     AstralEventTrigger source, int tier) {
        target.hurt(level.damageSources().generic(), 4.0F);
    }

    // ────────────────────────── 8. 天降之物:1 张随机卡牌 + 3 星币 ──────────────────────────

    public static void giftFromSky(ServerLevel level, Player triggerer, Player target, AstralEvent event,
                                   AstralEventTrigger source, int tier) {
        RandomCardHandler.giveCardTo(target, RandomCardHandler.CardCategory.ALL);
        giveCoins(target, 3);
    }

    // ────────────────────────── 9. 天使降临:4 点治疗 ──────────────────────────

    public static void angelDescent(ServerLevel level, Player triggerer, Player target, AstralEvent event,
                                    AstralEventTrigger source, int tier) {
        target.heal(4.0F);
    }

    // ────────────────────────── 10. 问题外卖:2 伤害 + 中毒/饥饿(0:15) + 15s 治疗失效 ──────────────────────────

    public static void problemDelivery(ServerLevel level, Player triggerer, Player target, AstralEvent event,
                                       AstralEventTrigger source, int tier) {
        target.hurt(level.damageSources().generic(), 2.0F);
        // 专用指示器（用户裁决 1/2）。注:原生中毒/饥饿以 visible=false 施加 ⇒ **HUD 侧已隐藏**;
        // 但物品栏「效果」面板走 isVisibleInInventory(默认 true) ⇒ 该面板仍会显示两行,待补客户端 mixin。
        target.addEffect(new MobEffectInstance(com.merlinkitsune.astral_dice.effect.ModEffects.PROBLEM_DELIVERY.get(),
                300, 0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 0, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300, 0, false, false));
        setHealBlocked(target, true);
        EventTimerStore.start(target, event.id(), 300, p -> setHealBlocked(p, false));
        LOGGER.info("AP_EVENT_EFFECT: problem_delivery:healBlocked=true:target={}", target.getName().getString());
    }

    // ────────────────────────── 11. 拥挤通道:所有受影响玩家被传送到触发者位置 ──────────────────────────

    public static void crowdedPassage(ServerLevel level, Player triggerer, Player target, AstralEvent event,
                                      AstralEventTrigger source, int tier) {
        if (target == triggerer) return;   // 触发者本身不动
        target.teleportTo(triggerer.getX(), triggerer.getY(), triggerer.getZ());
        LOGGER.info("AP_EVENT_EFFECT: crowded_passage:moved={}", target.getName().getString());
    }

    // ────────────────────────── 12. 战斗爽:2 张「攻击-特大」 ──────────────────────────

    public static void battleFrenzy(ServerLevel level, Player triggerer, Player target, AstralEvent event,
                                    AstralEventTrigger source, int tier) {
        for (int i = 0; i < 2; i++) {
            VitaminPillChipItem.giveCard(target, new ItemStack(ModItems.ATTACK_CARD_EPIC.get()));
        }
    }

    // ────────────────────────── 工具 ──────────────────────────

    private static void giveCoins(Player player, int count) {
        ItemStack coins = new ItemStack(ModItems.STAR_COIN.get(), count);
        if (!player.getInventory().add(coins)) player.drop(coins, false);
    }

    /** 治疗失效标记(15 秒;由 {@link #onHeal} 拦截)。 */
    private static final java.util.Set<UUID> HEAL_BLOCKED = new java.util.HashSet<>();

    static void setHealBlocked(Player player, boolean blocked) {
        if (blocked) {
            HEAL_BLOCKED.add(player.getUUID());
        } else {
            HEAL_BLOCKED.remove(player.getUUID());
        }
    }

    public static boolean isHealBlocked(Player player) {
        return HEAL_BLOCKED.contains(player.getUUID());
    }
}
