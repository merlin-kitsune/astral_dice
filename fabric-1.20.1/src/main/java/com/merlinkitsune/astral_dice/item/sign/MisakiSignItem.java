package com.merlinkitsune.astral_dice.item.sign;
import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;

import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;

public class MisakiSignItem extends BaseSignItem {
    public MisakiSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide) {
            // 主动技能:获得"爆发"效果 2:00(visible=true 使效果图标在 HUD 正常显示)
            player.addEffect(new MobEffectInstance(ModEffects.MISAKI_BURST.get(), 2400, 0, false, true, true));

            // 若被动层数已达 3 层:减少 2 层,并向物品栏增加一张"名刀嘎呜切"
            int stacks = ModDataComponents.MISAKI_SIGN_STACKS.getOrDefault(stack, 0);
            if (stacks >= 3) {
                ModDataComponents.MISAKI_SIGN_STACKS.set(stack, stacks - 2);
                ItemStack meito = new ItemStack(ModItems.ATTACK_CARD_MEITO.get());
                VitaminPillChipItem.giveCard(player, meito);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 清除剑气层数
        ModDataComponents.MISAKI_SIGN_STACKS.set(stack, 0);
    }

    // 第二批「三态化」:主动施加 "爆发" 2:00 ⇒ 进入锁定(生效中)态;锁定结束才起主动技能冷却
    @Override
    protected boolean startActiveLockOnUse(Player player, long now) {
        net.minecraft.world.effect.MobEffectInstance instance = player.getEffect(ModEffects.MISAKI_BURST.get());
        if (instance == null) return false;
        beginActiveLock(player, "astral_dice:misaki_sign", now + instance.getDuration());
        return true;
    }

    // 门控效果实例仍在:效果被外力提前移除时锁定提前结束(硬上界不延长)
    @Override
    protected boolean isGateEffectActive(Player player) {
        return player.hasEffect(ModEffects.MISAKI_BURST.get());
    }

    // 装备护法立牌时,"名刀嘎呜切"费用降低为 3 点
    public static int getMeitoCost(Player player) {
        return hasMisakiEquipped(player) ? 3 : AppliedStone.cost("meito");
    }

    // 名刀费用考虑护法立牌后的实际费用
    /** G6(2026-11-08)：非玩家宿主按基础费用结算（护法立牌折扣只对玩家生效）。 */
    public static int effectiveCost(net.minecraft.world.entity.LivingEntity holder, String type) {
        return effectiveCost(holder instanceof Player p ? p : null, type);
    }

    public static int effectiveCost(Player player, String type) {
        if ("meito".equals(type)) {
            return getMeitoCost(player);
        }
        return AppliedStone.cost(type);
    }

    public static boolean hasMisakiEquipped(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.MISAKI_SIGN.get())).isPresent();
    }

    // 2026-11-11 P1 补齐：爆发 2400t；层数 ≥3 时扣 2 层并补一张名刀（与 P0 同口径：非玩家走 PlayerLikeTargets.giveItem，玩家路径在 handleUse）。
    @Override
    protected boolean activateForEntity(net.minecraft.world.entity.LivingEntity owner) {
        if (owner.level().isClientSide()) {
            return com.merlinkitsune.astral_dice.item.sign.SignEntitySkills.deny("misaki", owner, "client_side");
        }
        owner.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.merlinkitsune.astral_dice.effect.ModEffects.MISAKI_BURST.get(), 2400, 0, false, true, true));
        com.merlinkitsune.astral_dice.item.CurioSlotUtil.mutateFirstCurioMatching(owner,
                s -> s.is(com.merlinkitsune.astral_dice.item.ModItems.MISAKI_SIGN.get()), live -> {
                    int stacks = com.merlinkitsune.astral_dice.component.ModDataComponents.MISAKI_SIGN_STACKS.getOrDefault(live, 0);
                    if (stacks >= 3) {
                        com.merlinkitsune.astral_dice.component.ModDataComponents.MISAKI_SIGN_STACKS.set(live, stacks - 2);
                        net.minecraft.world.item.ItemStack meito = new net.minecraft.world.item.ItemStack(
                                com.merlinkitsune.astral_dice.item.ModItems.ATTACK_CARD_MEITO.get());
                        com.merlinkitsune.astral_dice.target.PlayerLikeTargets.giveItem(owner, meito);
                    }
                });
        return com.merlinkitsune.astral_dice.item.sign.SignEntitySkills.ok("misaki", owner, 2400);
    }
}
