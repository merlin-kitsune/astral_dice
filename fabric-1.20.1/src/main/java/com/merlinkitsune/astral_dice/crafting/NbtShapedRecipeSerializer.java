package com.merlinkitsune.astral_dice.crafting;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * {@code astral_dice:nbt_shaped} 的序列化器 —— 原版有序合成 + 「指定 NBT」约束。
 *
 * <h2>做法：JSON 与网络都复用原版编解码，只额外追加一段</h2>
 * <ol>
 *   <li><b>JSON</b>：{@code pattern} / {@code key} / {@code result} 仍写标准形态，
 *       直接交给原版 {@link ShapedRecipe.Serializer} 解析（多余键会被忽略）；
 *       额外读一个 {@value #NBT_KEY} 对象：
 *       {@code { "<pattern 符号>": { "item": …, "nbt": "<SNBT>", "strict": <bool> } }}，
 *       把符号换算成 pattern 线性索引后构造 {@link StackConstraint}。</li>
 *   <li><b>网络</b>：先委托原版写/读，再追加「索引 + strict + 模板物品」段
 *       ⇒ 客户端拿到的约束与服务端一致（客户端配方书要用 {@code matches} 做 ghost 预览）。</li>
 * </ol>
 *
 * <p>⚠️ 不复用原版 pattern/key 的手工解析（原版 {@code patternFromJson} / {@code keyFromJson}
 * 是包私有 static，外部不可见）—— 委托 {@code ShapedRecipe.Serializer#fromJson} 是最稳的做法，
 * 也天然跟随版本补丁。
 *
 * <p>⚠️ {@value #NBT_KEY} 用 **pattern 符号** 定位，索引按「未收缩」pattern 计算。
 * 若 pattern 外围含全空行/列，原版会先 shrink（{@code ShapedRecipe.shrink}）导致索引错位
 * ⇒ 本实现**显式拒绝**这类 pattern（fail-loud），而不是静默匹配成别的物品。
 */
public final class NbtShapedRecipeSerializer implements RecipeSerializer<NbtShapedRecipe> {

    /** 追加字段名（与 datagen 侧 {@code NbtAugmentedRecipe} 必须保持一致）。 */
    public static final String NBT_KEY = "astral_nbt";

    /** 原版编解码器：pattern / key / result / 网络段全部复用，避免与版本补丁产生格式漂移。 */
    private static final ShapedRecipe.Serializer VANILLA = new ShapedRecipe.Serializer();

    @Override
    public NbtShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
        ShapedRecipe base = VANILLA.fromJson(id, json);
        return new NbtShapedRecipe(base, parseConstraints(base, json));
    }

    @Override
    public NbtShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
        ShapedRecipe base = VANILLA.fromNetwork(id, buffer);
        int size = buffer.readVarInt();
        Map<Integer, StackConstraint> constraints = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            int index = buffer.readVarInt();
            boolean strict = buffer.readBoolean();
            constraints.put(index, new StackConstraint(buffer.readItem(), strict));
        }
        return new NbtShapedRecipe(base, constraints);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, NbtShapedRecipe recipe) {
        VANILLA.toNetwork(buffer, recipe);
        buffer.writeVarInt(recipe.constraints().size());
        recipe.constraints().forEach((index, constraint) -> {
            buffer.writeVarInt(index);
            buffer.writeBoolean(constraint.strict());
            buffer.writeItem(constraint.template());
        });
    }

    private static Map<Integer, StackConstraint> parseConstraints(ShapedRecipe base, JsonObject json) {
        if (!json.has(NBT_KEY)) {
            return Map.of();
        }
        JsonObject declared = json.getAsJsonObject(NBT_KEY);
        if (declared.size() == 0) {
            return Map.of();
        }
        String[] pattern = readPattern(json, base);
        int width = base.getWidth();
        int height = base.getHeight();
        Map<Integer, StackConstraint> constraints = new LinkedHashMap<>();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                String symbol = String.valueOf(pattern[row].charAt(col));
                if (!declared.has(symbol)) {
                    continue;
                }
                constraints.put(col + row * width, buildConstraint(symbol, declared.get(symbol)));
            }
        }
        // 声明了却没出现在 pattern 里的符号 = 写错了（例如符号拼写不一致）。
        // 静默忽略会让「精确匹配」悄悄退化成「任意物品」，属本项目最忌的假绿 ⇒ fail-loud。
        for (String symbol : declared.keySet()) {
            boolean used = false;
            for (int row = 0; row < height && !used; row++) {
                used = pattern[row].contains(symbol);
            }
            if (!used) {
                throw new JsonSyntaxException("astral_dice:nbt_shaped 的 " + NBT_KEY + " 声明了符号 '"
                        + symbol + "'，但 pattern 里没有该符号");
            }
        }
        return constraints;
    }

    private static StackConstraint buildConstraint(String symbol, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonSyntaxException("astral_dice:nbt_shaped 的 " + NBT_KEY + "." + symbol + " 不是对象");
        }
        JsonObject obj = element.getAsJsonObject();
        // 与 key 里普通 ingredient 完全一致的写法：item / count / nbt(SNBT 字符串)
        ItemStack template = ShapedRecipe.itemStackFromJson(obj);
        boolean strict = obj.has("strict") && obj.get("strict").getAsBoolean();
        return new StackConstraint(template, strict);
    }

    private static String[] readPattern(JsonObject json, ShapedRecipe base) {
        JsonArray array = json.getAsJsonArray("pattern");
        String[] pattern = new String[array.size()];
        for (int i = 0; i < pattern.length; i++) {
            pattern[i] = array.get(i).getAsString();
        }
        for (int i = 0; i < pattern.length; i++) {
            if (isBlank(pattern[i])) {
                throw new JsonSyntaxException("astral_dice:nbt_shaped 的 pattern 第 " + i + " 行全空；"
                        + NBT_KEY + " 依赖符号定位，不接受需要 shrink 的 pattern");
            }
        }
        for (int col = 0; col < pattern[0].length(); col++) {
            boolean blank = true;
            for (String row : pattern) {
                if (col < row.length() && !isBlankChar(row.charAt(col))) {
                    blank = false;
                    break;
                }
            }
            if (blank) {
                throw new JsonSyntaxException("astral_dice:nbt_shaped 的 pattern 第 " + col + " 列全空；"
                        + NBT_KEY + " 依赖符号定位，不接受需要 shrink 的 pattern");
            }
        }
        if (base.getWidth() != pattern[0].length() || base.getHeight() != pattern.length) {
            throw new JsonSyntaxException("astral_dice:nbt_shaped 的 pattern 尺寸与解析结果不一致（pattern "
                    + pattern[0].length() + "x" + pattern.length + " vs " + base.getWidth() + "x"
                    + base.getHeight() + "）");
        }
        return pattern;
    }

    private static boolean isBlank(String row) {
        for (int i = 0; i < row.length(); i++) {
            if (!isBlankChar(row.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlankChar(char c) {
        return c == ' ' || c == '\u0000';
    }
}
