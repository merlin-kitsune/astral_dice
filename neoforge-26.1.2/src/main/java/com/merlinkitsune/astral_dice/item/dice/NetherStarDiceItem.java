package com.merlinkitsune.astral_dice.item.dice;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 下界之星骰子:全新的 T4 奇异品阶(dice_t4),由任意 T3 骰子(下界合金/绯红/末影)升级。
 *
 * 功能:
 * - 卡牌槽与费用点数始终按最高档(3★:卡牌栏 12 格、攻防费用上限各 6,见
 *   {@link DiceCurioItem#getCardSlots} 与 {@link com.merlinkitsune.starenginelib.component.GameplayConstants#cardCostForStar});
 * - 可装备筹码数量 +1(T4 筹码栏 = 4+s:0★4/1★5/2★6/3★7,见 ModItems 阶层注册);
 * - 骰子每提升 1 星级:攻击力 +2、防御力 +2(按 1 防御力 = 2 护甲折算为 +4 护甲),
 *   属性随星级变化实时刷新(经 curioTick 瞬态属性修饰器)。
 */
public class NetherStarDiceItem extends DiceCurioItem {

    /** 每星级攻击力加成 */
    public static final int ATTACK_PER_STAR = 2;
    /** 每星级防御力加成(1 防御力 = 2 护甲值,经 DiceCombatModifiers.setDefenseArmorBonus 折算) */
    public static final int DEFENSE_PER_STAR = 2;
    /** 星级上限(3★;卡牌槽/费用按该档配置,与最高档一致) */
    public static final int MAX_STAR = 3;

    private static final String ARMOR_MODIFIER_KEY = "dice_nether_star_dice_armor";
    private static final String ATTACK_MODIFIER_KEY = "dice_nether_star_dice_attack";

    public NetherStarDiceItem(Properties properties) {
        super(properties);
    }

    // 读取骰子实际星级(用于属性加成/筹码栏);星级 0-3 钳制
    private static int actualStar(ItemStack stack) {
        WeaponEnhancement enh = stack.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
        return Math.max(0, Math.min(MAX_STAR, enh.starLevel()));
    }

    // 附魔光效:奇异品阶的视觉标识(物品栏/掉落物/手持渲染均闪烁)
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        super.curioTick(slotContext, stack);
        if (slotContext.entity().level().isClientSide()) return;
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.tickCount % 20 != 0) return;
        applyStarAttributes(player, stack);
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        // Curios 官方签名:第 2 参 newStack = 将要占用槽位的栈,第 3 参 stack = 被卸下的那件骰子(按真实语义命名)
        super.onUnequip(slotContext, newStack, stack);
        if (slotContext.entity().level().isClientSide()) return;
        if (!(slotContext.entity() instanceof Player player)) return;
        // 清除瞬态星级属性,防止卸下后残留
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(player, ARMOR_MODIFIER_KEY, 0);
        setAttackBonus(player, 0);
    }

    private static void applyStarAttributes(Player player, ItemStack stack) {
        int star = actualStar(stack);
        // 防御力:每星 +DEFENSE_PER_STAR(1 防御 = 2 护甲),内部按防御点传入自动折算
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, ARMOR_MODIFIER_KEY, star * DEFENSE_PER_STAR);
        // 攻击力:每星 +ATTACK_PER_STAR
        setAttackBonus(player, star * ATTACK_PER_STAR);
    }

    private static void setAttackBonus(Player player, double amount) {
        AttributeInstance attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr == null) return;
        Identifier id = Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, ATTACK_MODIFIER_KEY);
        var existing = attr.getModifier(id);
        if (amount <= 0) {
            if (existing != null) attr.removeModifier(id);
            return;
        }
        if (existing == null || existing.amount() != amount) {
            attr.removeModifier(id);
            attr.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
