package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;
import com.merlinkitsune.astral_dice.item.card.EffectCardPeriod;
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
 * 被动计数只保存在附件 {@code komachi_use_count} 中,<b>不再用任何效果承载/显示</b>
 * (原「忍者立牌出牌」计数效果 komachi_count 已删除)。
 *
 * <p>主动(忍术连击)= <b>一次性</b>:只把<b>当前出牌轮</b>的可出牌数 +1
 * ({@link EffectCardPeriod#grantBonusPlay});不累积、不跨轮保留、不产生任何常驻状态,
 * 周期结束时由 {@link EffectCardPeriod} 的出牌轮清理统一归零。释放前置(任一不满足即不释放,
 * 且<b>不消耗</b>主动技能冷却 —— performSkill 以 SUCCESS 判定是否起冷却):
 * <ol>
 *   <li>效果牌已进入冷却({@link EffectCardPeriod#isCooldownActive})→ 拒绝;</li>
 *   <li>出牌数上限已达封顶 {@link GameplayConstants#MAX_EFFECT_CARD_PLAYS} → 拒绝(+1 无意义,且不得绕过封顶);</li>
 *   <li>本轮已授予过这次 +1 → 拒绝(一次性;同一轮内不叠加)。</li>
 * </ol>
 * 主动技能自身冷却中的拒绝仍由 {@link BaseSignItem#performSkillForCurio} 统一处理。
 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class KomachiSignItem extends BaseSignItem {
    public KomachiSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 卸下立牌:重置被动计数与效果牌伤害增益(计数只存附件,无效果需要移除)。
        // 注意:出牌轮的一次性 +1 属于**出牌轮状态**(授予即已消耗),不随立牌装卸回收——
        // 若在此清除,会造成"上限在周期中途下降"的不变量违例(见 EffectCardPeriod#tick)。
        ModAttachments.setKomachiUseCount(player, 0);
        ModAttachments.setKomachiDamageBonus(player, 0);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动(忍术连击):一次性 —— 仅当前出牌轮 +1 张出牌数。释放前置见类注释(三条)。
        // 效果牌冷却中(本周期已打满并进入 30 秒冷却)时不释放:此时 +1 已无意义。
        if (EffectCardPeriod.isCooldownActive(player)) {
            sendSignActionBar(player, "msg.astral_dice.komachi_active_cooldown");
            return InteractionResultHolder.fail(stack);
        }
        if (EffectCardPeriod.getMaxAllowed(player) >= GameplayConstants.MAX_EFFECT_CARD_PLAYS) {
            sendSignActionBar(player, "msg.astral_dice.komachi_active_capped",
                    GameplayConstants.MAX_EFFECT_CARD_PLAYS);
            return InteractionResultHolder.fail(stack);
        }
        // 一次性授予:本轮已授予过则不再释放(不消耗主动技能冷却)
        if (!EffectCardPeriod.grantBonusPlay(player)) {
            sendSignActionBar(player, "msg.astral_dice.komachi_active_used");
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.success(stack);
    }

    // 主动技能 ActionBar:出牌数+1 与剩余出牌数(注册到主动技能响应事件)
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.astral_dice.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.KOMACHI_SIGN.get())) {
            Player player = event.getPlayer();
            int remaining = Math.max(0,
                    EffectCardPeriod.getMaxAllowed(player) - EffectCardPeriod.getPlayCount(player));
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

}
