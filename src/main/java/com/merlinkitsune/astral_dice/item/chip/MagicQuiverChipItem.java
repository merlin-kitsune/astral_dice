package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.combat.SpellDamageContext;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.combat.SpellDamageRegistry;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;

/**
 * 魔法箭袋筹码:若使用过效果牌,且对具有"标记"的目标造成了法伤(远程+魔法),
 * 则对该目标施加一层标记并返还第一张使用的效果牌。每分钟仅能触发一次。
 *
 * <p>追踪流程:使用效果牌(参与复制计数的功能效果牌)时由 {@link #onEffectCardUsed}
 * 记录第一张使用的效果牌;法伤命中带标记目标时由 {@link #tryProc} 触发返还并进入 1 分钟冷却。
 */
public class MagicQuiverChipItem extends BaseChipItem {
    /** 触发冷却时长(1 分钟) */
    public static final int COOLDOWN_TICKS = 1200;

    public MagicQuiverChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴魔法箭袋筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.MAGIC_QUIVER.get())).isPresent();
    }

    /**
     * 使用效果牌时调用(仅参与复制计数的功能效果牌):
     * 佩戴箭袋且冷却已结束时,记录第一张使用的效果牌类型。
     */
    public static void onEffectCardUsed(Player player, String cardType) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (ModAttachments.getMagicQuiverTracking(player)) return;
        if (player.level().getGameTime() < ModAttachments.getMagicQuiverCooldownEnd(player)) return;
        ModAttachments.setMagicQuiverTracking(player, true);
        ModAttachments.setMagicQuiverFirstCard(player, cardType);
    }

    /**
     * 法伤命中带标记目标时调用(由 SpellDamageRegistry 修饰器分发):
     * 满足全部条件(佩戴箭袋、已记录第一张效果牌、冷却结束、目标带标记)时,
     * 施加一层标记并返还第一张使用的效果牌,随后进入 1 分钟冷却并清除追踪。
     */
    public static boolean tryProc(SpellDamageContext ctx) {
        if (!isEquipped(ctx.attacker)) return false;
        if (!ModAttachments.getMagicQuiverTracking(ctx.attacker)) return false;
        long now = ctx.attacker.level().getGameTime();
        if (now < ModAttachments.getMagicQuiverCooldownEnd(ctx.attacker)) return false;
        if (MarkManager.getLevel(ctx.target) <= 0) return false;

        // 对该目标施加一层标记
        MarkManager.apply(ctx.target);

        // 返还第一张使用的效果牌
        ItemStack card = effectCardByType(ModAttachments.getMagicQuiverFirstCard(ctx.attacker));
        if (!card.isEmpty()) {
            if (!ctx.attacker.getInventory().add(card)) {
                ctx.attacker.drop(card, false);
            }
        }

        // 开始 1 分钟冷却并清除追踪
        ModAttachments.setMagicQuiverCooldownEnd(ctx.attacker, now + COOLDOWN_TICKS);
        ModAttachments.setMagicQuiverTracking(ctx.attacker, false);
        return true;
    }

    private static ItemStack effectCardByType(String cardType) {
        return switch (cardType) {
            case "berserk" -> new ItemStack(ModItems.EFFECT_CARD_BERSERK.get());
            case "unwavering" -> new ItemStack(ModItems.EFFECT_CARD_UNWAVERING.get());
            default -> new ItemStack(ModItems.EFFECT_CARD_KING_POWER.get());
        };
    }

    // 卸下筹码:清除已记录的第一张效果牌(下次装备重新追踪)
    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        ModAttachments.setMagicQuiverTracking(player, false);
    }
}
