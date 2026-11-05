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
 * 骰子升级有序合成配方:基础骰子/黄金骰子/钻石骰子按各自配方
 * (见 data/astral_dice/recipe/{golden_dice,diamond_dice,netherite_dice}.json —
 *  1.21.1 起数据包目录名由 recipes/ 变为单数 recipe/)
 * 由骰子 + 材料(星币/星盘/黄金星盘/原版金属)合成出更高阶骰子。
 * 合成产物会完整继承输入骰子的 WeaponEnhancement(星级 + 已装配攻防牌配置 + cost)。
 *
 * <p><b>1.21.1 形态</b>:原版 {@link ShapedRecipe} 的构造器已改成
 * {@code (String group, CraftingBookCategory, ShapedRecipePattern, ItemStack, boolean)}
 * (形状不在构造器里给 width/height/ingredient 列表,统一由 {@link ShapedRecipePattern} 承载);
 * {@code matches/assemble} 的输入也从 {@code CraftingContainer} 换成
 * {@code CraftingInput}({@code RecipeInput} 的子接口)。
 * 序列化侧 { RecipeSerializer} 只剩 {@code codec()} + {@code streamCodec()} 两个 Codec 出口,
 * 1.20.1 的 {@code fromJson/fromNetwork/toNetwork} 三个方法整体消失。
 */
public class DiceUpgradeShapedRecipe extends ShapedRecipe {
    private final ItemStack output;
    /**
     * 形状模式的本地缓存。
     * <p>原版 {@code ShapedRecipe} 把这四个字段({@code pattern/result/group/category/showNotification})
     * 存成**包私有**字段且没有 public 访问器取 {@code pattern}(javap 实证)
     * ⇒ 不在 {@code net.minecraft.world.item.crafting} 包内的子类读不到它,
     * 因此本类在构造时自存一份,供 CODEC / STREAM_CODEC 使用(语义与原版字段同源)。
     */
    private final ShapedRecipePattern pattern;
    private final CraftingBookCategory category;
    private final boolean showNotification;

    public DiceUpgradeShapedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
                                   ItemStack result, boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.output = result;
        this.pattern = pattern;
        this.category = category;
        this.showNotification = showNotification;
    }

    public ItemStack output() {
        return output;
    }

    /** 形状模式(CODEC / STREAM_CODEC 的读写源;原版字段为包私有,故自缓存)。 */
    public ShapedRecipePattern pattern() {
        return pattern;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.DICE_UPGRADE.get();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack resultStack = super.assemble(input, registries);
        // 1.21.1:输入是 CraftingInput,内部就是不可变的 ItemStack 列表(顺序 = 网格 + 网格外)
        for (ItemStack stack : input.items()) {
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

        /** JSON/数据包出口(1.21.1 的 RecipeSerializer 只留 Codec,不再有 fromJson/toJson)。 */
        public static final MapCodec<DiceUpgradeShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
                        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)
                                .forGetter(ShapedRecipe::category),
                        ShapedRecipePattern.MAP_CODEC.forGetter(DiceUpgradeShapedRecipe::pattern),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(DiceUpgradeShapedRecipe::output),
                        Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification)
                ).apply(instance, DiceUpgradeShapedRecipe::new));

        /** 网络出口(1.21.1 用 StreamCodec 取代手写 toNetwork/fromNetwork)。 */
        public static final StreamCodec<RegistryFriendlyByteBuf, DiceUpgradeShapedRecipe> STREAM_CODEC =
                StreamCodec.of(Serializer::toNetwork, Serializer::fromNetwork);

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
            ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern());
            ItemStack.STREAM_CODEC.encode(buffer, recipe.output());
            buffer.writeBoolean(recipe.showNotification());
        }
    }
}
