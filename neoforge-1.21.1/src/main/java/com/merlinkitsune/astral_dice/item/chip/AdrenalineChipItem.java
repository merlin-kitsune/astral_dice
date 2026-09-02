package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 肾上腺素-高效筹码:生命值低于最大生命值一半时,攻击力/防御力 +8。
 * - 攻击力经骰战攻击修饰器注册表(DiceCombatModifiers)计入;
 * - 防御力按「1 防御力 = 2 护甲值」折算为真实护甲(curioTick 维护,见防御力折算规范);
 * - 触发加成时被敌方攻击,掷 1d6——骰点 4-5 → 50% 概率闪避本次伤害,骰点 6 → 100% 闪避。
 * (「肾上腺素-一般」筹码已删除,其配方改造后直接合成高效,见 ModRecipeProvider)
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class AdrenalineChipItem extends BaseChipItem {
    /** 肾上腺素-高效攻防加成 */
    public static final int BONUS_HIGH = 8;

    private final int bonus;

    public AdrenalineChipItem(Properties properties, int bonus) {
        super(properties);
        this.bonus = bonus;
    }

    // 是否处于触发加成状态(生命值低于最大生命值一半)
    public static boolean isLowHp(Player player) {
        return player != null && player.getHealth() < player.getMaxHealth() / 2.0f;
    }

    public static boolean hasHighEquipped(Player player) {
        return hasCurio(player, ModItems.ADRENALINE_HIGH.get());
    }

    private static boolean hasCurio(Player player, net.minecraft.world.item.Item item) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(item)).isPresent();
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 防御力折算真实护甲(1 防御力 = 2 护甲值):触发加成状态生效,否则移除
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "adrenaline_def_armor" + bonus, isLowHp(player) ? bonus : 0);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "adrenaline_def_armor" + bonus, 0);
    }

    // 高效闪避掷骰:1d6——4-5 → 50% 概率,6 → 100%
    private static boolean tryDodge() {
        int roll = ThreadLocalRandom.current().nextInt(1, 7);
        if (roll == 6) return true;
        if (roll == 4 || roll == 5) return ThreadLocalRandom.current().nextBoolean();
        return false;
    }

    // 肾上腺素-高效:触发加成时被敌方攻击 → 按骰点概率闪避本次伤害
    @SubscribeEvent
    public static void onAdrenalineDodge(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!isLowHp(player)) return;
        if (!hasHighEquipped(player)) return;
        // 敌方攻击(来源为敌对生物;排除摔落/火焰等环境伤害)
        if (!(event.getSource().getEntity() instanceof Enemy)) return;
        if (tryDodge()) {
            event.setNewDamage(0);
        }
    }
}
