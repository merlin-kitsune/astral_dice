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
 * 临时牌:**潜影盒自动化(漏斗)面守卫**(目标 {@code ShulkerBoxBlockEntity#canPlaceItemThroughFace})。
 *
 * <h2>为什么需要</h2>
 * 1.21.1 实测字节码:原版该方法**只**排除潜影盒方块物品,完全不查
 * {@code canFitInsideContainerItems}(两种重载都不查):
 * <pre>
 * public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, Direction direction) {
 *     return !(Block.byItem(itemStack.getItem()) instanceof ShulkerBoxBlock);
 * }
 * </pre>
 * {@code CardItem} 不是 {@code BlockItem} ⇒ 原版逻辑放行 ⇒ 漏斗/发射器能把临时牌塞进潜影盒。
 * (NeoForge 1.21.1 在同一方法里追加了**栈级** {@code ItemStack#canFitInsideContainerItems()};
 * Fabric 侧没有这个补丁,故由本 mixin 按栈补一道:入参栈带临时标记 ⇒ 返回 {@code false},
 * 其余分支——例如「潜影盒不能装潜影盒」——原方法逐字不变。)
 *
 * <p>覆盖的路径:任何经 {@code WorldlyContainer#canPlaceItemThroughFace} 插入潜影盒的自动化
 * (原版漏斗/漏斗矿车/发射器等;{@code HopperBlockEntity} 对 {@code WorldlyContainer} 目标走的就是这条)。
 *
 * <p>判据仍走唯一入口 {@link TemporaryCardUtil#isPlacementBlocked(ItemStack, boolean)}。
 *
 * <p>⚠️ 已登记缺口(两线对等,见交付报告):{@code Container#canPlaceItem(int, ItemStack)} 路径不覆盖 ——
 * 1.21.1 的 {@code ShulkerBoxBlockEntity} **未**覆写 {@code canPlaceItem}(只声明
 * {@code canPlaceItemThroughFace} / {@code canTakeItemThroughFace}),继承的是
 * {@code Container#canPlaceItem} 的 {@code default} 实现(恒 {@code true})。从子类 mixin 注入**继承来的**
 * 方法会把补丁落到父类/接口、影响所有容器,风险远大于收益,故不做。
 * 该缺口同样涵盖 Fabric Transfer API 与第三方管道(箱子/木桶等非潜影盒容器本来就只能走到这里)。
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
