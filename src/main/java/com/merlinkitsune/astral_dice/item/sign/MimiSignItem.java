package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;

public class MimiSignItem extends BaseSignItem {

    public MimiSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        // 星光关联内容已移除
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return InteractionResultHolder.fail(stack);
        var diceResult = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        if (diceResult.isEmpty()) return InteractionResultHolder.fail(stack);
        ItemStack dice = diceResult.get().stack();
        WeaponEnhancement enh = dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);

        List<AppliedStone> oldStones = enh.appliedStones();

        // 主动:随机变化已装备卡牌(攻击/防御可互变),但攻击总费用与防御总费用均不得超过对应上限
        List<AppliedStone> transformed = new ArrayList<>();
        int attempts = 0;
        do {
            transformed.clear();
            for (int i = 0; i < oldStones.size(); i++) {
                transformed.add(AppliedStone.of(getRandomCardType(true, true, false)));
            }
            attempts++;
        } while ((totalCost(transformed, true, player) > enh.maxCost()
                || totalCost(transformed, false, player) > enh.maxDefenseCost()) && attempts < 200);

        // 插入一张随机卡牌(可为攻击卡或防御卡,允许费用溢出)
        transformed.add(AppliedStone.of(getRandomCardType(true, true, true)));

        int attackCost = totalCost(transformed, true, player);
        int defenseCost = totalCost(transformed, false, player);

        dice.set(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                new WeaponEnhancement(attackCost, enh.maxCost(), defenseCost, enh.maxDefenseCost(), enh.starLevel(), transformed));

        // 被动:变换的每张战斗牌(含插入的 1 张)各获得 1 星币
        for (int i = 0; i < oldStones.size() + 1; i++) {
            giveStarCoin(player);
        }
        return InteractionResultHolder.success(stack);
    }

    // 被动:每获得 1 张战斗牌,获得 1 星币(佩戴看板立牌时;由 RandomCardHandler 发放战斗牌时调用)
    public static void onBattleCardGained(Player player) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        giveStarCoin(player);
    }

    // 玩家是否佩戴看板立牌
    private static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.MIMI_SIGN.get())).isPresent();
    }

    private static void giveStarCoin(Player player) {
        ItemStack coin = new ItemStack(ModItems.STAR_COIN.get());
        if (!player.getInventory().add(coin)) {
            player.drop(coin, false);
        }
    }

    // 计算攻击(attack=true)或防御(attack=false)卡牌的总费用
    private static int totalCost(List<AppliedStone> stones, boolean attack, Player player) {
        int total = 0;
        for (AppliedStone stone : stones) {
            boolean isDefense = stone.type().startsWith("defense_");
            if (attack && !isDefense) {
                total += MisakiSignItem.effectiveCost(player, stone.type());
            } else if (!attack && isDefense) {
                total += MisakiSignItem.effectiveCost(player, stone.type());
            }
        }
        return total;
    }

    private static String getRandomCardType(boolean includeAttack, boolean includeDefense, boolean includeFullPower) {
        List<String> pool = new ArrayList<>();
        if (includeAttack) {
            pool.add("medium");
            pool.add("large");
            pool.add("epic");
            pool.add("shadow_strike");
            pool.add("meito");
            pool.add("charge");
            if (includeFullPower) pool.add("full_power");
        }
        if (includeDefense) {
            pool.add("defense_medium");
            pool.add("defense_large");
            pool.add("defense_epic");
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
