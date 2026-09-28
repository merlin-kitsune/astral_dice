package com.merlinkitsune.astral_dice.item.chip;
import com.merlinkitsune.starenginelib.item.CuriosCompat;

import com.merlinkitsune.astral_dice.combat.SpellDamageContext;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.combat.SpellDamageRegistry;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;

/**
 * 魔法箭袋筹码:**使用过伤害效果牌**,并对**已有标记**的目标造成**远程 / 法术伤害**后,
 * 对该目标施加一层标记并返还**第一张使用的效果牌**。每 30 秒仅能触发一次。
 *
 * <p>追踪流程:使用**伤害效果牌**(对怪激光 / 对怪板砖 / 轨道炮 / 定向爆破 / 活体书页,
 * 判定见 {@link BaseEffectCardItem#isDamageEffectCard})时由 {@link #onEffectCardUsed}
 * 记录**第一张**(已有记录则不覆盖);此后任意一次法伤命中**带标记**目标时,由
 * {@link #tryProc} 施加标记 + 返还并进入 30 秒冷却。
 *
 * <p><b>2026-09-24 用户裁决(第二版)</b>:取消「活体书页命中必定触发」的例外 —— 它同样必须
 * 先使用过一张伤害效果牌(活体书页**本身**即计入该集合);且记录**只统计伤害效果牌**
 * (王之力 / 狂暴等非伤害类效果牌不再参与追踪)。
 */
public class MagicQuiverChipItem extends BaseChipItem {
    /** 触发冷却时长(30 秒;2026-09-24 用户裁决由 1 分钟下调) */
    public static final int COOLDOWN_TICKS = 600;

    public MagicQuiverChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴魔法箭袋筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.MAGIC_QUIVER.get())).isPresent();
    }

    /**
     * 使用效果牌时调用:**仅伤害效果牌**(含活体书页)参与追踪。
     * 佩戴箭袋、冷却已结束、且尚未记录时,记录**第一张**使用的伤害效果牌类型。
     */
    public static void onEffectCardUsed(Player player, String cardType, ItemStack cardStack) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (!BaseEffectCardItem.isDamageEffectCard(cardStack)) return;
        if (ModAttachments.getMagicQuiverTracking(player)) return;
        if (player.level().getGameTime() < ModAttachments.getMagicQuiverCooldownEnd(player)) return;
        ModAttachments.setMagicQuiverTracking(player, true);
        ModAttachments.setMagicQuiverFirstCard(player, cardType);
    }

    /**
     * 法伤命中带标记目标时调用(由 SpellDamageRegistry 修饰器分发):
     * 满足全部条件(佩戴箭袋、**已记录第一张伤害效果牌**、冷却结束、目标带标记)时,
     * 施加一层标记并返还第一张使用的效果牌,随后进入 30 秒冷却并清除追踪。
     *
     * <p>「必须为远程 / 法术伤害」由入口保证:{@code DamageEffectCardHandler} 已用
     * {@code SpellDamageRegistry.isSpellDamage} 筛过作用域,故进入本方法即等价于本次是法伤。
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
        ItemStack card = BaseEffectCardItem.cardByTypeId(ModAttachments.getMagicQuiverFirstCard(ctx.attacker));
        if (!card.isEmpty()) {
            VitaminPillChipItem.giveCard(ctx.attacker, card);
        }

        // 开始 30 秒冷却并清除追踪
        ModAttachments.setMagicQuiverCooldownEnd(ctx.attacker, now + COOLDOWN_TICKS);
        ModAttachments.setMagicQuiverTracking(ctx.attacker, false);
        return true;
    }

    // 卸下筹码:清除已记录的第一张效果牌(下次装备重新追踪)
    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        ModAttachments.setMagicQuiverTracking(player, false);
    }
}
