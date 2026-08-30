package com.merlinkitsune.astral_dice.item.card;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.FoodData;
import com.merlinkitsune.astral_dice.combat.DiceCombatEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 命运的指引(专属功能效果牌,击杀带虚弱印记的目标获取)。
 * 玩家可对自己使用,持续 5:00:
 * 1. 出牌数 +1(临时加成,周期归 0 时清除)
 * 2. 主动技能冷却时间减半(实时功能:立刻将当前最大冷却倒计时减少一半的时间)
 * 3. 对拥有"虚弱印记"的目标额外 +20% 伤害
 * 4. 所有食物提供的饱和度翻倍
 * 5. 神秘遗物+联动:装备七咒之戒时,第一诅咒(受到任何来源伤害加倍)影响 -50%
 * 6. Iron 的法术与魔法书联动:魔力消耗减半
 * 功能全部由 attachment(FATE_ACTIVE_UNTIL)驱动;FATE_GUIDANCE 效果仅作状态显示(5:00 倒计时图标)。
 * 专属牌:仅允许获得者使用;赠与他人的专属牌接收者无法使用。
 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class FateGuidanceCardItem extends BaseEffectCardItem {

    public FateGuidanceCardItem(Properties properties) {
        super(properties);
    }
    @Override
    protected String cardTypeId() {
        return "fate_guidance";
    }


    @Override
    protected boolean isExclusive() {
        return true;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        // 1. 出牌数 +1(临时,命运的指引效果期间由效果驱动提供)
        // 2. 主动技能冷却时间减半(实时功能:立刻减少当前最大冷却倒计时一半的时间)
        reduceActiveSkillCooldown(user);
        // 3~6. 功能激活 5:00:写入激活截止时刻(attachment 驱动功能),状态效果仅作显示
        ModAttachments.setFateActiveUntil(user, user.level().getGameTime() + 6000);
        user.addEffect(new MobEffectInstance(ModEffects.FATE_GUIDANCE, 6000, 0, false, false, true));
        // 专属牌:无所有者时绑定获得者
        ExclusiveCardUtil.bindIfAbsent(stack, user);
    }

    // 主动技能冷却时间减半(实时功能):冷却中则立刻把最大冷却倒计时剩余一半的时间
    // (剩余时间减半 = 最大冷却时长减半;玩家级冷却,不受立牌装卸影响)
    private static void reduceActiveSkillCooldown(Player player) {
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > 0) {
            long now = player.level().getGameTime();
            long remaining = cdEnd - now;
            ModAttachments.setSignActiveCooldownEnd(player, now + Math.max(0, remaining / 2));
        }
    }

    // 命运的指引是否激活(功能由 attachment 截止时刻驱动;FATE_GUIDANCE 效果仅作状态显示)
    public static boolean isFateGuidanceActive(Player player) {
        long until = com.merlinkitsune.astral_dice.component.ModAttachments.getFateActiveUntil(player);
        return until > 0 && player.level().getGameTime() < until;
    }


    // 七咒第一诅咒处理:神秘遗物+ 模组(默认 NORMAL 优先级)先应用其配置/修正物品后的伤害倍率,
    // 本处理器在 LOWEST 捕获该【实际倍率】(amount/original,动态适配 painMultiplier 配置、大地誓约、
    // 救赎之戒转换等模组内修正),而非固化倍率。
    // - 骰战攻击(攻击者赐福激活+骰子+近战):不修改模组倍率,仅捕获存至目标侧,由骰战最终伤害使用;
    // - 非骰战攻击:命运的指引激活时按加幅减半(第一诅咒影响 -50%),未激活则保持模组倍率。
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onCurseMitigation(
            net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!DiceCombatEvents.hasEnigmaticCurse(player)) {
            // 未佩戴七咒(含已转换为救赎之戒):清理捕获,保持无倍率
            com.merlinkitsune.astral_dice.component.ModAttachments.setDiceCurseRatio(player, 1.0f);
            return;
        }
        float original = event.getOriginalAmount();
        float current = event.getAmount();
        float ratio = current > original ? current / original : 1.0f;

        // 是否骰战攻击(骰战攻击的最终伤害由骰战接管,此处仅捕获倍率,不修改伤害链)
        boolean diceCombat = event.getSource().getEntity() instanceof Player attacker
                && attacker.hasEffect(ModEffects.DICE_BLESSING)
                && DiceCombatEvents.attackerHasDiceCurio(attacker)
                && DiceCombatEvents.isMeleeWeaponAttack(attacker);
        if (diceCombat) {
            com.merlinkitsune.astral_dice.component.ModAttachments.setDiceCurseRatio(player, ratio);
            return;
        }

        // 非骰战攻击:命运的指引激活时第一诅咒影响 -50%(加幅减半)
        if (ratio > 1.0f
                && event.getSource().getEntity() instanceof Player attacker2
                && isFateGuidanceActive(attacker2)) {
            event.setAmount(original + (current - original) * 0.5f);
        }
        // 非骰战攻击不产生骰战捕获,清理
        com.merlinkitsune.astral_dice.component.ModAttachments.setDiceCurseRatio(player, 1.0f);
    }


    // 命运的指引·福运:激活期间所有食物提供的饱和度翻倍。
    // Finish 事件在原版 eat(更新食物数据)之前触发,此处预先补一份饱和度增量。
    @SubscribeEvent
    public static void onEatSaturationDouble(
            net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!isFateGuidanceActive(player)) return;
        net.minecraft.world.item.ItemStack stack = event.getItem();
        if (stack.isEmpty()) return;
        net.minecraft.world.food.FoodProperties food = stack.getItem().getFoodProperties(stack, player);
        if (food == null) return;
        // 原版单次进食的饱和度增量 = 营养 × 饱食度修正 × 2
        float delta = food.nutrition() * food.saturation() * 2.0f;
        net.minecraft.world.food.FoodData foodData = player.getFoodData();
        float newSat = Math.min(foodData.getFoodLevel(), foodData.getSaturationLevel() + delta);
        foodData.setSaturation(newSat);
    }
}
