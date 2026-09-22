package com.merlinkitsune.astral_dice.mixin.container;

import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 临时牌:**收纳袋(bundle)插入守卫** —— 1.20.1 专有(1.21.1 无此 mixin)。
 *
 * <h2>为什么需要,以及为什么注入点是干净的</h2>
 * 1.20.1 的收纳袋插入把「能不能装」写在两处,但都是**类型级**调用
 * (实测 {@code BundleItem.java:52} 与 {@code :113}):
 * <pre>
 * itemstack.getItem().canFitInsideContainerItems()          // :52  overrideStackedOnOther
 * insertedStack.getItem().canFitInsideContainerItems()      // :113 private static add(...)
 * </pre>
 * 1.21.1 的两处(NeoForge 打完补丁后)都是**栈级**:{@code BundleItem.java:57} 与
 * {@code BundleContents.java:144}(1.20.1 没有 {@code BundleContents} 这个类)。
 *
 * <p>好消息是 1.20.1 的 {@code BundleItem#add(ItemStack bundleStack, ItemStack insertedStack)}
 * 是一个 **private static** 方法,**直接拿到被插入的栈**,并且是**两条插入路径的唯一汇聚点**:
 * <ul>
 *   <li>{@code overrideStackedOnOther}(右键把槽里的栈放进收纳袋,{@code :54})→ 调 {@code add};</li>
 *   <li>{@code overrideOtherStackedOnMe}(手持栈右键点到收纳袋上,{@code :73})→ 调 {@code add};</li>
 *   <li>{@code add} 返回 {@code 0} 时,两条路径都不会 {@code shrink} 手持/槽内物品、不会写入 NBT
 *       (第二处的 {@code other.shrink(i)} 中 {@code i==0})。</li>
 * </ul>
 * ⇒ 在 {@code add} 的 HEAD 处对带临时标记的栈返回 {@code 0},即可**完整**封堵收纳袋,
 * 且 1.21.1 的第三个同类点({@code BundleContents#add})在 1.20.1 不存在,无需额外处理。
 *
 * <p><b>结论:收纳袋不是缺口</b>(§2-6② 的「无干净注入点 ⇒ 登记缺口」前提不成立)。
 *
 * <p>判据仍走唯一入口 {@link TemporaryCardUtil#isPlacementBlocked(ItemStack, boolean)}。
 * 注:收纳袋内容物是 NBT 列表,不构成「槽」,故 {@code targetIsPlayerOwnedSlot} 恒传 {@code false}。
 */
@Mixin(BundleItem.class)
public abstract class BundleInsertGuardMixin {

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private static void astralDice$blockTemporaryCardBundleInsert(ItemStack bundleStack, ItemStack insertedStack,
                                                                  CallbackInfoReturnable<Integer> cir) {
        if (insertedStack == null || insertedStack.isEmpty()) return;
        if (TemporaryCardUtil.isPlacementBlocked(insertedStack, false)) {
            // 0 = 「一个都没装进去」,与 add 的既有失败语义一致(不 shrink、不写 NBT)
            cir.setReturnValue(0);
        }
    }
}
