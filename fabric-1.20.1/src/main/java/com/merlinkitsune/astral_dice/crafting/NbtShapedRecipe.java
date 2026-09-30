package com.merlinkitsune.astral_dice.crafting;

import java.util.Map;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/**
 * 在**原版有序合成**之上叠加「指定 NBT」约束的配方。
 *
 * <h2>为什么覆写 {@code matches} 而不是替换 {@code Ingredient}</h2>
 * 原版 {@code Ingredient} 无法扩展（{@code final} + {@code private} 构造器 + 包私有
 * {@code Value} 接口，javap 实证），且其 {@code test} 根本不看 NBT。而
 * {@link ShapedRecipe} 是 {@code public class}、构造器 public、{@code matches} 非 final
 * ⇒ 只需在匹配阶段补一道 NBT 校验。
 *
 * <p>约束按 <b>pattern 线性索引</b>（{@code col + row * width}，即未镜像的 pattern 位）存放；
 * 镜像分支下 pattern 位仍然用同一索引查约束，而 ingredient 才按镜像公式取
 * —— 这样「同一格」的语义在两种摆放方式下保持稳定。
 *
 * <h2>与原版匹配的等价性</h2>
 * {@link #matches} 先调 {@code super.matches(...)} 做形状 + 种类级筛选，再按原版同一套
 * offset / 镜像枚举复算一遍并附加 NBT 校验；无约束时直接短路回原版实现，
 * 因此普通配方与本类配方的行为差异**仅**来自 NBT 约束本身。
 */
public class NbtShapedRecipe extends ShapedRecipe {

    /** pattern 位线性索引 → 该位的 NBT 约束（无约束的位不出现在表里）。 */
    private final Map<Integer, StackConstraint> constraints;

    /** 由原版解析结果 + 约束表构造（{@code fromJson} / {@code fromNetwork} 共用）。 */
    public NbtShapedRecipe(ShapedRecipe base, Map<Integer, StackConstraint> constraints) {
        this(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(),
                base.getIngredients(), base.getResultItem(RegistryAccess.EMPTY), base.showNotification(),
                constraints);
    }

    public NbtShapedRecipe(ResourceLocation id, String group, CraftingBookCategory category,
                           int width, int height, NonNullList<Ingredient> items, ItemStack result,
                           boolean showNotification, Map<Integer, StackConstraint> constraints) {
        super(id, group, category, width, height, items, result, showNotification);
        this.constraints = Map.copyOf(constraints);
    }

    public Map<Integer, StackConstraint> constraints() {
        return constraints;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AstralRecipeSerializers.NBT_SHAPED;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        if (constraints.isEmpty()) {
            return super.matches(container, level);
        }
        // 形状 + 种类级先过筛（原版全量遍历，含镜像），失败即无约束可救
        if (!super.matches(container, level)) {
            return false;
        }
        int width = getWidth();
        int height = getHeight();
        NonNullList<Ingredient> items = getIngredients();
        // 形状成立只说明「存在某个 offset 使种类匹配」；NBT 约束需要在**同一 offset** 下成立，
        // 故这里按原版同一套枚举顺序重算，找到第一个形状与约束同时成立的摆放。
        for (int offsetX = 0; offsetX <= container.getWidth() - width; offsetX++) {
            for (int offsetY = 0; offsetY <= container.getHeight() - height; offsetY++) {
                if (matchesAt(container, items, width, height, offsetX, offsetY, true)) {
                    return true;
                }
                if (matchesAt(container, items, width, height, offsetX, offsetY, false)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 与 {@code ShapedRecipe#matches(CraftingContainer, int, int, boolean)} 等价的复算，
     * 并在每个 pattern 位上追加 NBT 约束校验。
     *
     * <p>逻辑严格照抄原版字节码：offset 之外的容器格必须为空（原版靠
     * {@code Ingredient.EMPTY.test(stack)} ⇒ {@code stack.isEmpty()}），
     * pattern 位按 {@code mirror ? width - col - 1 + row * width : col + row * width} 取 ingredient。
     */
    private boolean matchesAt(CraftingContainer container, NonNullList<Ingredient> items,
                              int width, int height, int offsetX, int offsetY, boolean mirror) {
        int containerWidth = container.getWidth();
        int containerHeight = container.getHeight();
        for (int x = 0; x < containerWidth; x++) {
            for (int y = 0; y < containerHeight; y++) {
                int col = x - offsetX;
                int row = y - offsetY;
                ItemStack actual = container.getItem(x + y * containerWidth);
                if (col < 0 || row < 0 || col >= width || row >= height) {
                    if (!actual.isEmpty()) {
                        return false;
                    }
                    continue;
                }
                int patternIndex = col + row * width;
                Ingredient ingredient = mirror
                        ? items.get(width - col - 1 + row * width)
                        : items.get(patternIndex);
                if (!ingredient.test(actual)) {
                    return false;
                }
                StackConstraint constraint = constraints.get(patternIndex);
                if (constraint != null && !constraint.test(actual)) {
                    return false;
                }
            }
        }
        return true;
    }
}
