package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 忍者立牌。
 * 被动:每使用 3 张效果牌时(独立计数,与魔法秘典互不关联):
 * - 复制最后一张使用的效果牌并返回到物品栏;
 * - 主动技能冷却时间立即减少 30%;
 * - 伤害类效果牌伤害加成 +1(计数器"效果牌伤害增益",无上限,卸下立牌重置)。
 * 计数期间显示"忍者立牌"效果图标,等级 = 当前第几张;第 3 张触发后计数归 0。
 * 主动(忍术连击):本轮出牌数 +1——仅当前出牌周期有效、每周期至多一次,不跨周期累积。
 * 冷却中不可触发(BaseSignItem.performSkill 统一拦截);出牌数已达封顶 MAX_EFFECT_CARD_PLAYS
 * 或本周期已生效时不释放,且不进入主动技能冷却(见 handleUse)。
 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class KomachiSignItem extends BaseSignItem {
    public KomachiSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 卸下立牌:重置效果牌计数、效果牌伤害增益、移除计数效果与临时出牌数+1 标记
        ModAttachments.setKomachiUseCount(player, 0);
        ModAttachments.setKomachiDamageBonus(player, 0);
        ModAttachments.setKomachiExtraPlays(player, 0);
        ModEffectRemoval.remove(player, ModEffects.KOMACHI_COUNT);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动(忍术连击):本轮出牌数 +1——仅当前出牌周期有效,每周期至多一次,不跨周期累积。
        // 释放条件(任一不满足即不释放,且不消耗主动技能冷却,由 performSkill 的返回值判定):
        //   1. 当前出牌数上限未达封顶(MAX_EFFECT_CARD_PLAYS = 9),否则 +1 无任何意义;
        //   2. 本周期尚未由忍者主动 +1。
        // 冷却中的拒绝由 BaseSignItem.performSkill 统一处理,此处不重复判定。
        if (com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.getMaxAllowed(player)
                >= com.merlinkitsune.astral_dice.component.GameplayConstants.MAX_EFFECT_CARD_PLAYS) {
            sendSignActionBar(player, "msg.astral_dice.komachi_active_capped",
                    com.merlinkitsune.astral_dice.component.GameplayConstants.MAX_EFFECT_CARD_PLAYS);
            return InteractionResultHolder.fail(stack);
        }
        if (ModAttachments.getKomachiExtraPlays(player) > 0) {
            sendSignActionBar(player, "msg.astral_dice.komachi_active_used");
            return InteractionResultHolder.fail(stack);
        }
        ModAttachments.setKomachiExtraPlays(player,
                com.merlinkitsune.astral_dice.component.GameplayConstants.KOMACHI_EXTRA_PLAYS_CAP);
        return InteractionResultHolder.success(stack);
    }

    // 主动技能 ActionBar:出牌数+1 与剩余出牌数(注册到主动技能响应事件)
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.astral_dice.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.KOMACHI_SIGN.get())) {
            Player player = event.getPlayer();
            int remaining = Math.max(0,
                    com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.getMaxAllowed(player)
                            - com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.getPlayCount(player));
            sendSignActionBar(player, "msg.astral_dice.komachi_active", remaining);
            event.setHandled();
        }
    }

    // 被动:每使用第 3 张效果牌时触发(独立计数)——复制最后一张效果牌 + 主动技能冷却 -30% + 伤害类效果牌伤害加成 +1
    public static void onEffectCardUsed(Player player, String cardType) {
        if (player.level().isClientSide()) return;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        if (curios.get().findFirstCurio(s -> s.is(ModItems.KOMACHI_SIGN.get())).isEmpty()) return;

        int count = ModAttachments.getKomachiUseCount(player) + 1;
        ModAttachments.setKomachiUseCount(player, count);
        ModAttachments.setKomachiLastCard(player, cardType);
        updateCountEffect(player);
        if (count >= 3) {
            // 1. 复制最后一张使用的效果牌并返回到物品栏
            // 读回附件中的「最后一张效果牌」记录作为唯一来源(方法参数仅作兜底),保证跨周期/跨会话一致
            String lastCardType = ModAttachments.getKomachiLastCard(player);
            if (lastCardType == null || lastCardType.isEmpty()) lastCardType = cardType;
            ItemStack card = BaseEffectCardItem.cardByTypeId(lastCardType);
            // 复制的专属效果牌绑定获得者(忍者)
            if (ExclusiveCardUtil.isExclusive(card)) {
                ExclusiveCardUtil.setOwner(card, player);
            }
            if (!card.isEmpty()) {
                VitaminPillChipItem.giveCard(player, card);
            }
            // 2. 主动技能冷却时间立即减少 30%(剩余部分)
            reduceSignCooldown(player);
            // 3. 伤害类效果牌伤害加成 +1(无上限,卸下立牌重置)
            ModAttachments.setKomachiDamageBonus(player,
                    ModAttachments.getKomachiDamageBonus(player) + 1);
            ModAttachments.setKomachiUseCount(player, 0);
            updateCountEffect(player);
        }
    }

    // 主动技能冷却时间立即减少 30%(剩余部分;玩家级冷却,不受立牌装卸影响)
    private static void reduceSignCooldown(Player player) {
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > 0) {
            long now = player.level().getGameTime();
            long remaining = cdEnd - now;
            if (remaining > 0) {
                ModAttachments.setSignActiveCooldownEnd(player, now + (long) (remaining * 0.7));
            }
        }
    }

    // 刷新计数效果:等级 = 当前计数(第几张);计数归 0 时移除效果
    public static void updateCountEffect(Player player) {
        if (player.level().isClientSide()) return;
        int count = ModAttachments.getKomachiUseCount(player);
        if (count <= 0) {
            ModEffectRemoval.remove(player, ModEffects.KOMACHI_COUNT);
            return;
        }
        player.addEffect(new MobEffectInstance(ModEffects.KOMACHI_COUNT, 10000, count - 1, false, true, true));
    }
}
