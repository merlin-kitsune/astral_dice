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
 * - 伤害类效果牌伤害加成 +1(计数器"效果牌伤害增益",上限由配置 komachi_damage_bonus_max 控制,默认 10,最大 16)。
 * 计数期间显示"忍者立牌"效果图标,等级 = 当前第几张;第 3 张触发后计数归 0。
 * 主动:本轮出牌数 +1(累积到出牌数银行,按实际出牌消耗;跨周期保留至用尽,不随周期归零清除,
 * 不受出牌进度/冷却/满额影响;银行存储上限见 GameplayConstants.KOMACHI_EXTRA_PLAYS_CAP)。
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
        // 主动:效果牌出牌数 +1(累积到出牌数银行,按实际出牌消耗;不受出牌进度/冷却/满额影响;
        // 银行存储上限为独立常量,与效果牌出牌上限无关)
        ModAttachments.setKomachiExtraPlays(player,
                Math.min(ModAttachments.getKomachiExtraPlays(player) + 1,
                        com.merlinkitsune.astral_dice.component.GameplayConstants.KOMACHI_EXTRA_PLAYS_CAP));
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
            ItemStack card = BaseEffectCardItem.cardByTypeId(cardType);
            // 复制的专属效果牌绑定获得者(忍者)
            if (ExclusiveCardUtil.isExclusive(card)) {
                ExclusiveCardUtil.setOwner(card, player);
            }
            if (!card.isEmpty()) {
                VitaminPillChipItem.giveCard(player, card);
            }
            // 2. 主动技能冷却时间立即减少 30%(剩余部分)
            reduceSignCooldown(player);
            // 3. 伤害类效果牌伤害加成 +1(上限由配置 komachi_damage_bonus_max 控制)
            int bonus = ModAttachments.getKomachiDamageBonus(player);
            if (bonus < com.merlinkitsune.astral_dice.component.GameplayConstants.KOMACHI_DAMAGE_BONUS_MAX) {
                ModAttachments.setKomachiDamageBonus(player, bonus + 1);
            }
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
