package com.merlinkitsune.astral_dice.crafting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;

/**
 * 单个配方格的「指定 NBT」约束。
 *
 * <h2>为什么需要它</h2>
 * 1.20.1 原版的 {@link net.minecraft.world.item.crafting.Ingredient} 对 NBT 是**完全无视**的：
 * 其 {@code test(ItemStack)} 的字节码只调 {@code ItemStack.is(Item)}（javap 实证 ⇒ 见
 * {@code KNOWN-ISSUES.md} KI-F1 的取证记录），连 {@code getItems()} 里携带的 NBT 都不参与比较。
 * 而 {@code Ingredient} 本身是 {@code final}、构造器 {@code private}、内部 {@code Value}
 * 接口包私有 —— **三个方向都封死**，无法在外部包扩展。
 *
 * <p>因此 Forge 侧靠自家 {@code StrictNBTIngredient} / {@code PartialNBTIngredient} 实现的
 * 「指定药水」匹配，在 Fabric 侧只能落到 {@link NbtShapedRecipe#matches} 这一层补齐，
 * 即本类 —— 由 {@link NbtShapedRecipeSerializer} 从配方 JSON 的
 * {@value NbtShapedRecipeSerializer#NBT_KEY} 字段反序列化而来。
 *
 * <h2>两种语义（逐条对齐 Forge 的两个实现）</h2>
 * <ul>
 *   <li>{@code strict = true} —— 对齐 {@code StrictNBTIngredient}：item 与 NBT 必须
 *       <b>完全一致</b>（{@link ItemStack#isSameItemSameTags(ItemStack, ItemStack)}）。</li>
 *   <li>{@code strict = false} —— 对齐 {@code PartialNBTIngredient}：只要求 item 相同，
 *       且模板 NBT 是实际 NBT 的<b>子集</b>
 *       （{@link NbtUtils#compareNbt(net.minecraft.nbt.Tag, net.minecraft.nbt.Tag, boolean)}
 *       传 {@code fuzzy = true}；其实现在字节码上是「Compound 逐 key 递归 / List 逐元素存在 即通过」，
 *       正是子集语义）。</li>
 * </ul>
 *
 * <p>本类不参与 {@code count} 比较 —— 与 Forge 两个实现一致（{@code Ingredient} 语义只看种类）。
 */
public final class StackConstraint {

    private final ItemStack template;
    private final boolean strict;

    public StackConstraint(ItemStack template, boolean strict) {
        ItemStack copy = template.copy();
        copy.setCount(1);
        this.template = copy;
        this.strict = strict;
    }

    /** 模板物品（含期望 NBT；count 恒为 1 —— 匹配不看数量）。 */
    public ItemStack template() {
        return template;
    }

    /** {@code true} = NBT 完全一致；{@code false} = NBT 子集。 */
    public boolean strict() {
        return strict;
    }

    /** 实际槽位物品是否满足本约束。 */
    public boolean test(ItemStack actual) {
        if (actual == null || actual.isEmpty()) {
            return false;
        }
        if (strict) {
            return ItemStack.isSameItemSameTags(template, actual);
        }
        if (!actual.is(template.getItem())) {
            return false;
        }
        CompoundTag expected = template.getTag();
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        // compareNbt 对「实际为 null」返回 false ⇒ 期望 NBT 非空时实际必须携带该 NBT。
        return NbtUtils.compareNbt(expected, actual.getTag(), true);
    }
}
