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
 * (见 data/astral_dice/recipe/{golden_dice,diamond_dice,netherite_dice}.json)
 * 由骰子 + 材料(星币/星盘/黄金星盘/原版金属)合成出更高阶骰子。
 * 合成产物会完整继承输入骰子的 WeaponEnhancement(星级 + 已装配攻防牌配置 + cost)。
 */
public class DiceUpgradeShapedRecipe extends ShapedRecipe {
    private final String group;
    private final CraftingBookCategory category;
    private final ItemStack output;
    private final boolean showNotification;

    /**
     * <p><b>26.1.2 迁移说明</b>:{@code ShapedRecipe} 的构造器改为
     * {@code (Recipe.CommonInfo, CraftingRecipe.CraftingBookInfo, ShapedRecipePattern, ItemStackTemplate)},
     * 即「通知开关」与「分组/分类」被拆成两个记录入参,且结果改用 {@code ItemStackTemplate}。
     * 本类因此自己保留 group/category/output/showNotification 供 codec 使用。
     */
    public DiceUpgradeShapedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification) {
        super(new net.minecraft.world.item.crafting.Recipe.CommonInfo(showNotification),
                new net.minecraft.world.item.crafting.CraftingRecipe.CraftingBookInfo(category, group),
                pattern,
                net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(result));
        this.group = group;
        this.category = category;
        this.output = result;
        this.showNotification = showNotification;
    }

    public ItemStack output() {
        return output;
    }

    private String astralGroup() {
        return group;
    }

    private CraftingBookCategory astralCategory() {
        return category;
    }

    private boolean astralShowNotification() {
        return showNotification;
    }

    /**
     * <p><b>26.1.2 类型说明</b>:{@code ShapedRecipe#getSerializer()} 被固定为
     * {@code RecipeSerializer<ShapedRecipe>}(泛型不变 ⇒ 不能收窄成
     * {@code RecipeSerializer<DiceUpgradeShapedRecipe>}),故此处做一次**已证明安全**的强制转换:
     * 泛型在运行期擦除,而本序列化器的 codec 产出的对象本就一定是本类实例。
     */
    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return (RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>) ModRecipeSerializers.DICE_UPGRADE.get();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack resultStack = super.assemble(input);
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

    /**
     * 配方序列化器的编解码器持有者。
     *
     * <p><b>26.1.2 迁移说明</b>:1.21.1 的 {@code RecipeSerializer} 是接口,自定义配方用
     * {@code implements RecipeSerializer<T>} + 覆写 {@code codec()}/{@code streamCodec()};
     * 26.1.2 已把它改成 **record**(只承载 {@code MapCodec<T>} 与 {@code StreamCodec<RegistryFriendlyByteBuf,T>}),
     * 不能再被实现。故本类退化为纯 codec 容器,序列化器实例改为在类末尾直接 new 出来
     * ({@link #SERIALIZER}),注册处引用它。
     */
    public static class Serializer {

        public static final MapCodec<DiceUpgradeShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter(DiceUpgradeShapedRecipe::astralGroup),
                        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(DiceUpgradeShapedRecipe::astralCategory),
                        ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                        ItemStack.CODEC.fieldOf("result").forGetter(DiceUpgradeShapedRecipe::output),
                        Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(DiceUpgradeShapedRecipe::astralShowNotification)
                ).apply(instance, DiceUpgradeShapedRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, DiceUpgradeShapedRecipe> STREAM_CODEC = StreamCodec.of(
                Serializer::toNetwork, Serializer::fromNetwork);

        private static DiceUpgradeShapedRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            boolean showNotification = buffer.readBoolean();
            return new DiceUpgradeShapedRecipe(group, category, pattern, result, showNotification);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, DiceUpgradeShapedRecipe recipe) {
            buffer.writeUtf(recipe.astralGroup());
            buffer.writeEnum(recipe.astralCategory());
            ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.output());
            buffer.writeBoolean(recipe.astralShowNotification());
        }
    }

    /** 26.1.2:序列化器实例(record 构造,取代 1.21.1 的 {@code Serializer.INSTANCE})。 */
    public static final RecipeSerializer<DiceUpgradeShapedRecipe> SERIALIZER =
            new RecipeSerializer<>(Serializer.CODEC, Serializer.STREAM_CODEC);
}
