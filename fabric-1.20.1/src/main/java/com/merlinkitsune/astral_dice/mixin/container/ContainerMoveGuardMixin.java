package com.merlinkitsune.astral_dice.mixin.container;

import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 临时牌:**快捷移动守卫**(目标 {@code AbstractContainerMenu#moveItemStackTo})。
 *
 * <h2>为什么必须与 {@link SlotPlaceGuardMixin} 成对存在</h2>
 * {@code moveItemStackTo} 分两段:
 * <ol>
 *   <li><b>与同类栈合并</b>(1.20.1 实测 {@code AbstractContainerMenu.java:631-687})——
 *       **完全不查 {@code mayPlace}**。只改 {@code mayPlace} 时,「Shift 点击把临时牌并进箱子里
 *       已有的同类栈」这条路径会**绕过**全部槽位校验 ⇒ 临时牌被悄悄搬进容器;</li>
 *   <li>找空槽(1.20.1 实测 {@code :690})—— 这里查 {@code mayPlace},已被 {@code SlotPlaceGuardMixin} 挡住。</li>
 * </ol>
 * 故本 mixin 在方法 HEAD 处整体判定**目标槽区间** {@code [startIndex, endIndex)}:
 * 只要该区间**全部**是「玩家自己的物品栏槽」或「本模组卡牌栏槽」就放行,否则取消整次移动
 * (返回 {@code false} = 一个都没移动,与「移动失败」的既有语义一致)。
 *
 * <p>1.20.1 目标方法签名与 1.21.1 **逐字相同**:
 * {@code protected boolean moveItemStackTo(ItemStack, int, int, boolean)}
 * (1.20.1 实测声明于 {@code AbstractContainerMenu.java:629})⇒ 本 mixin 为纯镜像。
 *
 * <p>判据同样只有一个入口:{@link TemporaryCardUtil#isPlacementBlocked(ItemStack, boolean)},
 * 区间归属由 {@link TemporaryCardUtil#isRangePlayerOwnedOrPermissive} 计算。
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMoveGuardMixin {

    @Inject(method = "moveItemStackTo", at = @At("HEAD"), cancellable = true)
    private void astralDice$blockTemporaryCardMove(ItemStack stack, int startIndex, int endIndex,
                                                   boolean reverseDirection,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) return;
        if (!TemporaryCardUtil.isTemporary(stack)) return;
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        boolean playerOwnedOrPermissive =
                TemporaryCardUtil.isRangePlayerOwnedOrPermissive(self.slots, startIndex, endIndex);
        if (TemporaryCardUtil.isPlacementBlocked(stack, playerOwnedOrPermissive)) {
            cir.setReturnValue(false);
        }
    }
}
