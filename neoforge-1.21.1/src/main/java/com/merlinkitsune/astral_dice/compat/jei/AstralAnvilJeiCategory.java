package com.merlinkitsune.astral_dice.compat.jei;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.item.DiceStarUpgrade;
import com.merlinkitsune.astral_dice.item.ModItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * JEI 自定义类别「星愿铁砧」（astral_dice:astral_anvil）。
 *
 * <p>星愿铁砧的升星不是数据包配方，而是 {@link DiceStarUpgrade} 的代码规则；本类别把
 * 13 种骰子 × 0/1/2 星的 39 条规则转成 JEI 展示。界面箭头点击时由
 * {@code AstralAnvilJeiPlugin} 直接跳到与输入骰子完全匹配的那一条。
 */
public final class AstralAnvilJeiCategory implements IRecipeCategory<AstralAnvilJeiCategory.Recipe> {

    public static final RecipeType<Recipe> RECIPE_TYPE =
            RecipeType.create(AstralDiceMod.MODID, "astral_anvil", Recipe.class);

    private static final String KEY_TITLE = "jei.astral_dice.astral_anvil";
    private static final String KEY_STAR_COST = "jei.astral_dice.astral_anvil.star_cost";
    private static final String KEY_XP_COST = "jei.astral_dice.astral_anvil.xp_cost";
    private static final String KEY_STAR_RESET = "jei.astral_dice.astral_anvil.star_reset";

    private static final int WIDTH = 180;
    private static final int HEIGHT = 52;
    private static final int ANVIL_X = 6;
    private static final int ANVIL_Y = 6;
    private static final int DICE_X = 30;
    private static final int DICE_Y = 6;
    private static final int COIN_X = 52;
    private static final int COIN_Y = 6;
    private static final int ARROW_X = 74;
    private static final int ARROW_Y = 7;
    private static final int OUT_X = 106;
    private static final int OUT_Y = 6;
    private static final int TEXT_X = 6;
    private static final int TEXT_Y = 30;
    private static final int TEXT_COLOR = 0xFF404040;

    private static final List<Item> DICE_ITEMS = List.of(
            ModItems.DICE.get(),
            ModItems.GOLDEN_DICE.get(),
            ModItems.DIAMOND_DICE.get(),
            ModItems.NETHERITE_DICE.get(),
            ModItems.EMERALD_DICE.get(),
            ModItems.GLASS_DICE.get(),
            ModItems.NETHERRACK_DICE.get(),
            ModItems.OBSIDIAN_DICE.get(),
            ModItems.WEIRD_DICE.get(),
            ModItems.AMETHYST_DICE.get(),
            ModItems.CRIMSON_DICE.get(),
            ModItems.ENDER_DICE.get(),
            ModItems.NETHER_STAR_DICE.get());

    private final IDrawable icon;
    private final IDrawable arrow;

    public AstralAnvilJeiCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.ASTRAL_ANVIL.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<Recipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(KEY_TITLE);
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public ResourceLocation getRegistryName(Recipe recipe) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(recipe.dice());
        String path = key == null ? "unknown" : key.getPath();
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "astral_anvil/" + path + "_" + recipe.starLevel());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, ANVIL_X, ANVIL_Y)
                .addItemStack(new ItemStack(ModBlocks.ASTRAL_ANVIL.get()));
        builder.addInputSlot(DICE_X, DICE_Y)
                .addItemStack(new ItemStack(recipe.dice()));
        builder.addInputSlot(COIN_X, COIN_Y)
                .addItemStack(new ItemStack(ModItems.STAR_COIN.get(), recipe.coinCost()));
        builder.addOutputSlot(OUT_X, OUT_Y)
                .addItemStack(new ItemStack(recipe.dice()));
    }

    @Override
    public void draw(Recipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
            double mouseX, double mouseY) {
        this.arrow.draw(guiGraphics, ARROW_X, ARROW_Y);
        Font font = Minecraft.getInstance().font;
                    guiGraphics.drawString(font,
                DiceStarUpgrade.experimentalEnabled()
                        ? Component.translatable(KEY_STAR_RESET, recipe.starLevel())
                        : Component.translatable(KEY_STAR_COST, recipe.starLevel(),
                                recipe.starLevel() + 1, recipe.coinCost()),
                TEXT_X, TEXT_Y, TEXT_COLOR, false);
            guiGraphics.drawString(font, Component.translatable(KEY_XP_COST, recipe.xpCost()), TEXT_X, TEXT_Y + 12, TEXT_COLOR, false);
    }

    /** 全部展示记录：13 种骰子 × 0/1/2 星。星币成本与经验成本都取自 {@link DiceStarUpgrade}。 */
    public static List<Recipe> recipes() {
        List<Recipe> list = new ArrayList<>();
        for (Item dice : DICE_ITEMS) {
            for (int star = 0; star < 3; star++) {
                list.add(new Recipe(dice, star));
            }
        }
        return List.copyOf(list);
    }

    /** 按输入骰子找完全匹配的记录；非骰子或不可升星返回 null。 */
    public static Recipe find(ItemStack input) {
        if (!DiceStarUpgrade.isDice(input)) {
            return null;
        }
        int star = DiceStarUpgrade.starLevel(input);
        for (Recipe recipe : recipes()) {
            if (recipe.dice() == input.getItem() && recipe.starLevel() == star) {
                return recipe;
            }
        }
        return null;
    }

    /** 一条展示记录：骰子物品 + 起始星阶（0/1/2）。 */
    public record Recipe(Item dice, int starLevel) {
        public int coinCost() {
            return DiceStarUpgrade.starCoinCost(this.starLevel);
        }

        public int xpCost() {
            return DiceStarUpgrade.xpCostFor(new ItemStack(this.dice));
        }
    }
}
