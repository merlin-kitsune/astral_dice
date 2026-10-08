package com.merlinkitsune.astral_dice.compat.jei;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.shop.CardShopCatalog;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * JEI 自定义类别「卡牌商店」（astral_dice:card_shop）。
 *
 * <p>每条记录 = 一张可售卡（见 {@link CardShopJeiRecipes}）。类别布局固定为：
 * <pre>
 *   [商店方块] --&gt; [卡牌]      说明文字：通过在卡牌商店进行交易获得
 *   [星币]                     价格文字：售价 %s 星币（有概率打折）
 * </pre>
 * 左侧方块/星币槽用真实物品栈绘制（JEI 自己画物品图标与 tooltip），
 * 右侧文字用 {@link Font#split} 手工折行后用 {@link GuiGraphics#drawString} 绘制。
 *
 * <p><b>为什么必须覆写 {@link #getWidth()} / {@link #getHeight()}</b>：
 * JEI 19.x 的 IRecipeCategory#getBackground() 默认实现返回 null
 * （javap 字节码 aconst_null; areturn），而默认宽高实现会在 background 为 null 时抛
 * IllegalStateException("getWidth() and getHeight() must be overridden if background is null")。
 */
public class CardShopCategory implements IRecipeCategory<CardShopRecipe> {

    /** 类别 id：astral_dice:card_shop。 */
    public static final RecipeType<CardShopRecipe> RECIPE_TYPE =
            RecipeType.create(AstralDiceMod.MODID, "card_shop", CardShopRecipe.class);

    /** 类别标题（JEI 标签页名）：卡牌商店。 */
    private static final String KEY_TITLE = "jei.astral_dice.card_shop";
    /** 获取方式说明：通过在卡牌商店进行交易获得。 */
    private static final String KEY_OBTAIN = "jei.astral_dice.card_shop.obtain";
    /** 价格说明，%s = 基础售价（星币）。 */
    private static final String KEY_PRICE = "jei.astral_dice.card_shop.price";

    /** 类别尺寸（background 为 null，必须自报宽高）。 */
    private static final int WIDTH = 210;
    private static final int HEIGHT = 64;

    /** 商店方块槽（催化剂）。 */
    private static final int SHOP_SLOT_X = 6;
    private static final int SHOP_SLOT_Y = 6;
    /** 星币槽（入场货币）。 */
    private static final int COIN_SLOT_X = 6;
    private static final int COIN_SLOT_Y = 32;
    /** 卡牌输出槽。 */
    private static final int CARD_SLOT_X = 54;
    private static final int CARD_SLOT_Y = 6;
    /** 商店方块到卡牌的箭头。 */
    private static final int ARROW_X = 28;
    private static final int ARROW_Y = 7;

    /** 文字区：x 起点与最大宽度（超宽自动折行）。 */
    private static final int TEXT_X = 80;
    private static final int TEXT_MAX_WIDTH = WIDTH - TEXT_X - 8;
    private static final int OBTAIN_TEXT_Y = 10;
    private static final int PRICE_TEXT_Y = 40;
    /** 与 JEI 默认文字色一致（深灰）。 */
    private static final int TEXT_COLOR = 0xFF404040;

    private final IDrawable icon;
    private final IDrawable arrow;

    public CardShopCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.CARD_SHOP.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<CardShopRecipe> getRecipeType() {
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

    /**
     * JEI 书签序列化用的稳定 id。
     *
     * <p>JEI 19.x 的默认实现只对 {@code RecipeHolder} 返回 id，本 record 不是 RecipeHolder
     * ⇒ 默认返回 null，玩家把这条配方加入 JEI 书签时会因「No registry name for recipe」
     * 保存失败（重进游戏后书签丢失）。这里用「card_shop/ + 卡牌注册名」给出稳定且唯一的 id。
     */
    @Override
    public ResourceLocation getRegistryName(CardShopRecipe recipe) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(recipe.card());
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID,
                "card_shop/" + (key == null ? "unknown" : key.getPath()));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CardShopRecipe recipe, IFocusGroup focuses) {
        // 催化剂：卡牌商店方块本体（JEI 会据此把本类别挂到方块物品的「用途」页）
        builder.addSlot(RecipeIngredientRole.CATALYST, SHOP_SLOT_X, SHOP_SLOT_Y)
                .addItemStack(new ItemStack(ModBlocks.CARD_SHOP.get()));
        // 输入：星币（交易用的货币）
        builder.addInputSlot(COIN_SLOT_X, COIN_SLOT_Y)
                .addItemStack(new ItemStack(ModItems.STAR_COIN.get()));
        // 输出：本记录对应的可售卡
        builder.addOutputSlot(CARD_SLOT_X, CARD_SLOT_Y)
                .addItemStack(new ItemStack(recipe.card()));
    }

    @Override
    public void draw(CardShopRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
            double mouseX, double mouseY) {
        this.arrow.draw(guiGraphics, ARROW_X, ARROW_Y);
        Font font = Minecraft.getInstance().font;
        drawWrapped(guiGraphics, font, Component.translatable(KEY_OBTAIN), TEXT_X, OBTAIN_TEXT_Y);
        drawWrapped(guiGraphics, font, Component.translatable(KEY_PRICE, CardShopCatalog.BASE_PRICE),
                TEXT_X, PRICE_TEXT_Y);
    }

    /** 把一段文本按 {@link #TEXT_MAX_WIDTH} 折行后绘制（行距 = 字高 + 2）。 */
    private static void drawWrapped(GuiGraphics guiGraphics, Font font, Component text, int x, int y) {
        List<FormattedCharSequence> lines = font.split(text, TEXT_MAX_WIDTH);
        int lineStep = font.lineHeight + 2;
        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.drawString(font, lines.get(i), x, y + i * lineStep, TEXT_COLOR, false);
        }
    }
}
