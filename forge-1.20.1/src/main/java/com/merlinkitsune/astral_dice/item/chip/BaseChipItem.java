package com.merlinkitsune.astral_dice.item.chip;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import com.merlinkitsune.astral_dice.combat.SpellDamageRegistry;
import com.merlinkitsune.astral_dice.item.card.EffectCardPeriod;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

/**
 * 筹码基类:统一全部筹码的公共模板,新增筹码只需继承本类并实现业务钩子。
 *
 * 公共行为(所有筹码一致):
 * - {@link #canEquip}:仅允许放入 "chip" 饰品栏,并禁止重复装备相同筹码(服务端校验;客户端放行避免误判);
 * - {@link #use}:下蹲右键自动装备到 "chip" 饰品栏;
 * - {@link #curioTick}:默认空实现,子类可覆写;
 * - {@link #onUnequip}:默认处理"卸下时清理"的钩子 {@link #onChipUnequip}(子类可覆写),并调用
 *   {@link CurioSlotUtil#onChipUnequip} 完成通用清理(如八面骰累计点清空、魔法秘典计数重置等)。
 *
 * 新增筹码时:
 * 1. 继承本类,覆写业务钩子(如 {@link #curioTick} / {@link #onChipEquip} / {@link #onChipUnequip});
 * 2. 战斗/资源加成统一注册到对应修饰器注册表(DiceCombatModifiers / SpellDamageRegistry / EffectCardPeriod),
 *    不要散落硬编码在事件类中。
 */
public abstract class BaseChipItem extends Item implements ICurioItem {

    public BaseChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        // 筹码只能放入"chip"饰品栏
        if (!"chip".equals(slotContext.identifier())) return false;
        // 禁止重复装备相同的筹码(服务端校验;客户端直接放行避免误判)
        if (slotContext.entity().level().isClientSide()) return true;
        return !CurioSlotUtil.hasSameItemEquipped(slotContext.entity(), stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 下蹲右键:自动装备到"chip"饰品栏
        if (player.isShiftKeyDown()) {
            return CurioSlotUtil.tryAutoEquip(player, stack, "chip");
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
    }

    /**
     * 生成唯一的属性修饰器 id(按本物品注册名派生)。
     * 同一属性在不同筹码间必须使用不同修饰器 id,否则 Curios 应用属性时后装者会覆盖先装者。
     */
    // 1.20.1 AttributeModifier 以 UUID 标识(按本物品注册名+后缀稳定派生,对应 1.21 的 ResourceLocation id)
    protected java.util.UUID attributeModifierId(String suffix) {
        net.minecraft.resources.ResourceLocation key =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this);
        return java.util.UUID.nameUUIDFromBytes(
                (com.merlinkitsune.astral_dice.AstralDiceMod.MODID + ":chip_" + key.getPath() + "_" + suffix)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // 卸下时通用清理(空实现;子类若需在真正卸下时清理自身数据可覆写)
    protected void onChipUnequip(Player player, ItemStack stack) {
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack curio, ItemStack newStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 通用清理:排除 Curios 重载场景(from=to 同一物品仍在槽位),仅在真正卸下时调用
        CurioSlotUtil.runOnRealUnequip(slotContext, curio, player, p -> onChipUnequip(p, curio));
    }
}
