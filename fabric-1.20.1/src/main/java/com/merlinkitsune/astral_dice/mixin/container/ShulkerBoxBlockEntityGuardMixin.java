package com.merlinkitsune.astral_dice.mixin.container;

import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 临时牌:**潜影盒自动化(漏斗)面守卫** —— 1.20.1 专有(1.21.1 无此 mixin)。
 *
 * <h2>为什么需要</h2>
 * 1.20.1 Forge 已经把「潜影盒尊重 {@code canFitInsideContainerItems}」打在**同一个方法**里,
 * 但用的是**类型级**调用(实测 {@code ShulkerBoxBlockEntity.java:216-217}):
 * <pre>
 * public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, Direction direction) {
 *    return !(Block.byItem(itemStack.getItem()) instanceof ShulkerBoxBlock)
 *           && itemStack.getItem().canFitInsideContainerItems(); // 类型级
 * }
 * </pre>
 * {@code CardItem} 不是 {@code BlockItem},{@code Item#canFitInsideContainerItems()} 缺省返回
 * {@code true} ⇒ 漏斗可以把临时牌塞进潜影盒。1.21.1 的同一方法已是**栈级**
 * ({@code itemStack.canFitInsideContainerItems()},实测 {@code ShulkerBoxBlockEntity.java:236-239})
 * ⇒ 本 mixin 就是 1.20.1 侧的等价物:入参栈带临时标记 ⇒ 直接返回 {@code false}
 * (其余分支——例如「潜影盒不能装潜影盒」——原方法逐字不变)。
 *
 * <p>覆盖的路径:任何经 {@code WorldlyContainer#canPlaceItemThroughFace} 插入潜影盒的自动化
 * (原版漏斗/漏斗矿车/发射器投掷口等)。
 *
 * <p>判据仍走唯一入口 {@link TemporaryCardUtil#isPlacementBlocked(ItemStack, boolean)}。
 *
 * <p>⚠️ 已登记缺口(见交付报告):{@code Container#canPlaceItem(int, ItemStack)} 路径
 * (Forge {@code InvWrapper} 支撑的模组管道/自动化)在 1.20.1 **不**覆盖 ——
 * 1.21.1 的 {@code ShulkerBoxBlockEntity#canPlaceItem} 是 NeoForge 自己覆写的,
 * 而 1.20.1 的 {@code ShulkerBoxBlockEntity} **未**覆写该方法(继承
 * {@code BaseContainerBlockEntity#canPlaceItem} ⇒ 恒 true)。从子类 mixin 注入**继承来的**方法
 * 会把补丁落到父类、影响所有容器方块实体,风险远大于收益,故不做。
 */
@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerBoxBlockEntityGuardMixin {

    @Inject(method = "canPlaceItemThroughFace", at = @At("HEAD"), cancellable = true)
    private void astralDice$blockTemporaryCardThroughShulkerFace(int index, ItemStack itemStack,
                                                                Direction direction,
                                                                CallbackInfoReturnable<Boolean> cir) {
        if (itemStack == null || itemStack.isEmpty()) return;
        if (TemporaryCardUtil.isPlacementBlocked(itemStack, false)) {
            cir.setReturnValue(false);
        }
    }
}
