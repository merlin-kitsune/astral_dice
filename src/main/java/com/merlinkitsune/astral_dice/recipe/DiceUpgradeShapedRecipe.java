package com.merlinkitsune.astral_dice.recipe;

import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * 骰子升级有序合成配方:基础骰子被 8 金锭包围合成出黄金骰子,
 * 黄金骰子被 8 钻石包围合成出钻石骰子。
 * 合成产物会完整继承输入骰子的 WeaponEnhancement(星级 + 已装配攻防牌配置 + cost)。
 */
public class DiceUpgradeShapedRecipe extends ShapedRecipe {
    private final ItemStack output;

    public DiceUpgradeShapedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.output = result;
    }

    public ItemStack output() {
        return output;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.DICE_UPGRADE.get();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack resultStack = super.assemble(input, registries);
        for (ItemStack stack : input.items()) {
            // 任意骰子(基础/黄金/钻石)都可作为升级母体,星级与已装配攻防牌配置原样继承
            if (DiceCurioItem.isDiceItem(stack)) {
                WeaponEnhancement enh = stack.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
                resultStack.set(ModDataComponents.WEAPON_ENHANCEMENT.get(), enh);
                break;
            }
        }
        return resultStack;
    }

    public static class Serializer implements RecipeSerializer<DiceUpgradeShapedRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        public static final MapCodec<DiceUpgradeShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
                        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(ShapedRecipe::category),
                        ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(DiceUpgradeShapedRecipe::output),
                        Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification)
                ).apply(instance, DiceUpgradeShapedRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, DiceUpgradeShapedRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<DiceUpgradeShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DiceUpgradeShapedRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static DiceUpgradeShapedRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            boolean showNotification = buffer.readBoolean();
            return new DiceUpgradeShapedRecipe(group, category, pattern, result, showNotification);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, DiceUpgradeShapedRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.output());
            buffer.writeBoolean(recipe.showNotification());
        }
    }
}
