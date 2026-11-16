package com.merlinkitsune.astral_dice.mixin.container;

import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 临时牌:**收纳袋(bundle)插入守卫**(目标 {@code BundleContents$Mutable})。
 *
 * <p>1.21.1 的收纳袋插入有**两个**汇聚点,都必须拦(实测
 * {@code javap -p -c net.minecraft.world.item.component.BundleContents$Mutable}):
 * <ol>
 *   <li>{@code tryInsert(ItemStack)}(手持栈直接塞进袋):入口先判
 *       {@code stack.getItem().canFitInsideContainerItems()} —— **类型级**,
 *       {@code CardItem} 缺省恒 {@code true} ⇒ 不挡临时牌;</li>
 *   <li>{@code tryTransfer(Slot, Player)}(GUI 里把槽内栈搬进袋):先
 *       {@code slot.safeTake(count, getMaxAmountToAdd(stack), player)} **摘栈**,再交给
 *       {@code tryInsert} ⇒ 只在 {@code tryInsert} 里拦**会丢物品**(已被摘出的那一份无处可去)。</li>
 * </ol>
 * NeoForge 1.21.1 之所以不需要本 mixin,是因为它把 {@code tryInsert} 里的调用点改成了**栈级**
 * {@code ItemStack#canFitInsideContainerItems()};Fabric 侧没有该补丁 ⇒ 按栈补两道。
 *
 * <p>为什么拦 {@code tryTransfer} 的 HEAD(而不是让 {@code getMaxAmountToAdd} 返回 0):
 * 后者依赖「第三方容器 {@code removeItem(amount=0)} 无副作用」这一未被保证的前提;
 * 而在 HEAD 直接返回 0 既不改动原方法其余分支,也不触碰槽内容。
 *
 * <p>判据仍走唯一入口 {@link TemporaryCardUtil#isPlacementBlocked(ItemStack, boolean)}
 * (收纳袋永远不是「玩家自己的物品栏槽」⇒ 传 {@code targetIsPlayerOwnedSlot=false})。
 */
@Mixin(BundleContents.Mutable.class)
public abstract class BundleInsertGuardMixin {

    @Inject(method = "tryTransfer", at = @At("HEAD"), cancellable = true)
    private void astralDice$blockTemporaryCardBundleTransfer(Slot slot, Player player,
                                                             CallbackInfoReturnable<Integer> cir) {
        if (slot == null) return;
        ItemStack stack = slot.getItem();
        if (stack == null || stack.isEmpty()) return;
        if (TemporaryCardUtil.isPlacementBlocked(stack, false)) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "tryInsert", at = @At("HEAD"), cancellable = true)
    private void astralDice$blockTemporaryCardBundleInsert(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (stack == null || stack.isEmpty()) return;
        if (TemporaryCardUtil.isPlacementBlocked(stack, false)) {
            cir.setReturnValue(0);
        }
    }
}
