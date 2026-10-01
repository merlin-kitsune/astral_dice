package com.merlinkitsune.astral_dice.datagen;

import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.merlinkitsune.astral_dice.crafting.AstralRecipeSerializers;
import com.merlinkitsune.astral_dice.crafting.NbtShapedRecipeSerializer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * 把 {@code ShapedRecipeBuilder} 产出的配方包一层，追加 {@value NbtShapedRecipeSerializer#NBT_KEY}
 * 约束字段，并把 {@code type} 换成 {@code astral_dice:nbt_shaped}。
 *
 * <h2>为什么用「包装」而不是自己造 builder</h2>
 * {@code ShapedRecipeBuilder} 已经负责 pattern 校验、group/category、以及
 * <b>进度（advancement）</b> 的生成（{@code unlockedBy} ⇒ {@code criteria}/{@code requirements}）。
 * 只覆写 {@code getType()} 与 {@code serializeRecipeData()}，其余全部代理
 * ⇒ 进度文件与其它 116 条配方走完全相同的生成路径，不会出现口径分叉。
 *
 * <p>用法：把原来的 {@code .save(output::accept)} 换成
 * {@code .save(NbtAugmentedRecipe.wrap(output, Map.of("Z", NbtAugmentedRecipe.nbtKey(stack, true))))}。
 */
public final class NbtAugmentedRecipe implements FinishedRecipe {

    private final FinishedRecipe delegate;
    private final Map<String, JsonObject> nbtKeys;

    private NbtAugmentedRecipe(FinishedRecipe delegate, Map<String, JsonObject> nbtKeys) {
        this.delegate = delegate;
        this.nbtKeys = Map.copyOf(nbtKeys);
    }

    /**
     * 包一层输出通道：此后所有配方都会带上同一组 {@code nbtKeys}。
     *
     * @param output  原 {@code save} 的接收器（通常 {@code output::accept}）
     * @param nbtKeys pattern 符号 → 该符号的 NBT 约束描述（{@link #nbtKey} 产出）
     */
    public static Consumer<FinishedRecipe> wrap(Consumer<FinishedRecipe> output,
                                                Map<String, JsonObject> nbtKeys) {
        return recipe -> output.accept(new NbtAugmentedRecipe(recipe, nbtKeys));
    }

    /**
     * 构造一条 pattern 符号的 NBT 约束描述。
     *
     * @param template 模板物品（自带 NBT；count 只会写 {1} 以外的值）
     * @param strict   {@code true} ⇒ 对齐 Forge 的 {@code StrictNBTIngredient}（NBT 完全一致）；
     *                 {@code false} ⇒ 对齐 {@code PartialNBTIngredient}（NBT 子集）
     */
    public static JsonObject nbtKey(ItemStack template, boolean strict) {
        JsonObject json = new JsonObject();
        json.addProperty("item", BuiltInRegistries.ITEM.getKey(template.getItem()).toString());
        if (template.getCount() > 1) {
            json.addProperty("count", template.getCount());
        }
        CompoundTag tag = template.getTag();
        if (tag != null && !tag.isEmpty()) {
            json.addProperty("nbt", tag.toString());
        }
        if (strict) {
            json.addProperty("strict", true);
        }
        return json;
    }

    @Override
    public void serializeRecipeData(JsonObject json) {
        delegate.serializeRecipeData(json);
        JsonObject declared = new JsonObject();
        nbtKeys.forEach(declared::add);
        json.add(NbtShapedRecipeSerializer.NBT_KEY, declared);
    }

    @Override
    public ResourceLocation getId() {
        return delegate.getId();
    }

    @Override
    public RecipeSerializer<?> getType() {
        return AstralRecipeSerializers.NBT_SHAPED;
    }

    @Override
    public JsonObject serializeAdvancement() {
        return delegate.serializeAdvancement();
    }

    @Override
    public ResourceLocation getAdvancementId() {
        return delegate.getAdvancementId();
    }
}
