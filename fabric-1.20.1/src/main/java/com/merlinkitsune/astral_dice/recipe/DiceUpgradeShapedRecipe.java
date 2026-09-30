package com.merlinkitsune.astral_dice.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 骰子升级有序合成配方:基础骰子/黄金骰子/钻石骰子按各自配方
 * (见 data/astral_dice/recipes/{golden_dice,diamond_dice,netherite_dice}.json)
 * 由骰子 + 材料(星币/星盘/黄金星盘/原版金属)合成出更高阶骰子。
 * 合成产物会完整继承输入骰子的 WeaponEnhancement(星级 + 已装配攻防牌配置 + cost)。
 */
public class DiceUpgradeShapedRecipe extends ShapedRecipe {
    private final ItemStack output;
    private final CraftingBookCategory category;
    private final boolean showNotification;

    public DiceUpgradeShapedRecipe(ResourceLocation id, String group, CraftingBookCategory category,
                                   int width, int height, NonNullList<Ingredient> recipeItems,
                                   ItemStack result, boolean showNotification) {
        super(id, group, category, width, height, recipeItems, result, showNotification);
        this.output = result;
        this.category = category;
        this.showNotification = showNotification;
    }

    public ItemStack output() {
        return output;
    }

    public CraftingBookCategory bookCategory() {
        return category;
    }

    public boolean showsNotification() {
        return showNotification;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.DICE_UPGRADE.get();
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        ItemStack resultStack = super.assemble(input, registries);
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            // 任意骰子(基础/黄金/钻石)都可作为升级母体,星级与已装配攻防牌配置原样继承
            if (DiceCurioItem.isDiceItem(stack)) {
                WeaponEnhancement enh = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(stack, WeaponEnhancement.EMPTY);
                ModDataComponents.WEAPON_ENHANCEMENT.set(resultStack, enh);
                break;
            }
        }
        return resultStack;
    }

    public static class Serializer implements RecipeSerializer<DiceUpgradeShapedRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public DiceUpgradeShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
            String group = GsonHelper.getAsString(json, "group", "");
            CraftingBookCategory category = CraftingBookCategory.CODEC.byName(
                    GsonHelper.getAsString(json, "category", null), CraftingBookCategory.MISC);
            Map<Character, Ingredient> key = keyFromJson(GsonHelper.getAsJsonObject(json, "key"));
            String[] rows = patternFromJson(GsonHelper.getAsJsonArray(json, "pattern"));
            int width = rows[0].length();
            int height = rows.length;
            NonNullList<Ingredient> items = dissolvePattern(key, rows, width, height);
            ItemStack result = itemFromJson(GsonHelper.getAsJsonObject(json, "result"));
            boolean showNotification = GsonHelper.getAsBoolean(json, "show_notification", true);
            return new DiceUpgradeShapedRecipe(id, group, category, width, height, items, result, showNotification);
        }

        @Override
        public DiceUpgradeShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            int width = buffer.readVarInt();
            int height = buffer.readVarInt();
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            NonNullList<Ingredient> items = NonNullList.createWithCapacity(width * height);
            for (int i = 0; i < width * height; i++) {
                items.add(Ingredient.fromNetwork(buffer));
            }
            ItemStack result = buffer.readItem();
            boolean showNotification = buffer.readBoolean();
            return new DiceUpgradeShapedRecipe(id, group, category, width, height, items, result, showNotification);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, DiceUpgradeShapedRecipe recipe) {
            buffer.writeVarInt(recipe.getWidth());
            buffer.writeVarInt(recipe.getHeight());
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.bookCategory());
            for (Ingredient ingredient : recipe.getIngredients()) {
                ingredient.toNetwork(buffer);
            }
            buffer.writeItem(recipe.output());
            buffer.writeBoolean(recipe.showsNotification());
        }

        // === 以下解析逻辑与原版 ShapedRecipe.Serializer 对齐(原版为私有,无法直接复用) ===

        private static Map<Character, Ingredient> keyFromJson(JsonObject keyJson) {
            Map<Character, Ingredient> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : keyJson.entrySet()) {
                if (entry.getKey().length() != 1) {
                    throw new JsonSyntaxException("Invalid key entry: '" + entry.getKey() + "' is an invalid symbol (must be 1 character only).");
                }
                if (" ".equals(entry.getKey())) {
                    throw new JsonSyntaxException("Invalid key entry: ' ' is a reserved symbol.");
                }
                map.put(entry.getKey().charAt(0), Ingredient.fromJson(entry.getValue()));
            }
            map.put(' ', Ingredient.EMPTY);
            return map;
        }

        private static String[] patternFromJson(JsonArray patternJson) {
            String[] rows = new String[patternJson.size()];
            if (rows.length > 3) {
                throw new JsonSyntaxException("Invalid pattern: too many rows, 3 is maximum");
            }
            if (rows.length == 0) {
                throw new JsonSyntaxException("Invalid pattern: empty pattern not allowed");
            }
            for (int i = 0; i < rows.length; i++) {
                String row = GsonHelper.convertToString(patternJson.get(i), "pattern[" + i + "]");
                if (row.length() > 3) {
                    throw new JsonSyntaxException("Invalid pattern: too many columns, 3 is maximum");
                }
                if (i > 0 && rows[0].length() != row.length()) {
                    throw new JsonSyntaxException("Invalid pattern: each row must be the same width");
                }
                rows[i] = row;
            }
            return rows;
        }

        private static NonNullList<Ingredient> dissolvePattern(Map<Character, Ingredient> key, String[] rows, int width, int height) {
            NonNullList<Ingredient> items = NonNullList.withSize(width * height, Ingredient.EMPTY);
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    items.set(col + row * width, key.get(rows[row].charAt(col)));
                }
            }
            return items;
        }

        private static ItemStack itemFromJson(JsonObject resultJson) {
            String itemId = GsonHelper.getAsString(resultJson, "item");
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getOptional(new ResourceLocation(itemId)).orElse(Items.AIR);
            ItemStack stack = new ItemStack(item);
            if (resultJson.has("count")) {
                stack.setCount(GsonHelper.getAsInt(resultJson, "count"));
            }
            return stack;
        }
    }
}
