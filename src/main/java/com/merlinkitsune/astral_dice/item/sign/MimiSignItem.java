package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
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
        int totalCardSlots = DiceCurioItem.getCardSlots(dice);
        int perSideSlots = totalCardSlots / 2;

        List<AppliedStone> oldStones = enh.appliedStones();
        List<AppliedStone> transformed = new ArrayList<>();
        int maxCost = GameplayConstants.cardCostForStar(enh.starLevel());

        // 1) 随机变化已有卡牌,但必须满足:每侧不超过当前骰子槽位、攻击/防御费用 ≤6
        int attempts = 0;
        do {
            transformed.clear();
            for (AppliedStone ignored : oldStones) {
                transformed.add(AppliedStone.of(getRandomCardType(true, true, false)));
            }
            attempts++;
        } while (!isValidLayout(transformed, player, maxCost, perSideSlots) && attempts < 200);

        // 若随机始终不合法(如旧数据异常),回退为原卡牌,避免数据丢失
        if (!isValidLayout(transformed, player, maxCost, perSideSlots)) {
            transformed = new ArrayList<>(oldStones);
        }

        // 2) 仅在仍有空槽时尝试插入 1 张新卡,并且插入后仍必须满足费用/数量上限
        if (transformed.size() < totalCardSlots) {
            for (int insertAttempt = 0; insertAttempt < 20; insertAttempt++) {
                List<AppliedStone> withNew = new ArrayList<>(transformed);
                withNew.add(AppliedStone.of(getRandomCardType(true, true, true)));
                if (isValidLayout(withNew, player, maxCost, perSideSlots)) {
                    transformed = withNew;
                    break;
                }
            }
        }

        int attackCost = totalCost(transformed, true, player);
        int defenseCost = totalCost(transformed, false, player);

        dice.set(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                new WeaponEnhancement(attackCost, maxCost,
                        defenseCost, maxCost, enh.starLevel(), transformed));

        // 被动:按最终实际存在的卡牌数量发放星币,避免多给
        for (AppliedStone ignored : transformed) {
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

    // 校验布局:攻击/防御每侧数量不超过当前骰子槽位,且攻击/防御费用不超过 6
    private static boolean isValidLayout(List<AppliedStone> stones, Player player, int maxCost, int perSideSlots) {
        int attackCount = 0;
        int defenseCount = 0;
        for (AppliedStone stone : stones) {
            if (stone.type().startsWith("defense_")) {
                defenseCount++;
            } else {
                attackCount++;
            }
        }
        return attackCount <= perSideSlots
                && defenseCount <= perSideSlots
                && totalCost(stones, true, player) <= maxCost
                && totalCost(stones, false, player) <= maxCost;
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