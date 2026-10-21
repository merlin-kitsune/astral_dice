package com.merlinkitsune.astral_dice.compat.jei;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.shop.ChipShopCatalog;
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
 * JEI 自定义类别「筹码商店」（astral_dice:chip_shop）。
 *
 * <p>与 {@link CardShopCategory}（astral_dice:card_shop）完全对称：每条记录 = 一枚可售筹码
 * （见 {@link ChipShopJeiRecipes}）。类别布局固定为：
 * <pre>
 *   [商店方块] --&gt; [筹码]      说明文字：通过在筹码商店进行交易获得
 *   [星币]                     价格文字：起售价 / 每次购买涨幅 / 上限
 * </pre>
 * 左侧方块/星币槽用真实物品栈绘制（JEI 自己画物品图标与 tooltip），
 * 右侧文字用 {@link Font#split} 手工折行后用 {@link GuiGraphics#drawString} 绘制。
 *
 * <p><b>价格口径</b>：全部取自 {@link ChipShopCatalog} 的冻结常量
 * （{@link ChipShopCatalog#PRICE_INITIAL}=10 / {@link ChipShopCatalog#PRICE_STEP}=5 /
 * {@link ChipShopCatalog#PRICE_MAX}=100），不另写公式 —— 与
 * {@link ChipShopCatalog#priceOf(int)} 同源。
 *
 * <p><b>为什么必须覆写 {@link #getWidth()} / {@link #getHeight()}</b>：
 * JEI 的 IRecipeCategory#getBackground() 默认实现返回 null
 * （15.56.0.205 与 19.39.0.372 的 javap 字节码均为 {@code aconst_null; areturn}），
 * 而默认宽高实现会在 background 为 null 时抛
 * IllegalStateException("getWidth() and getHeight() must be overridden if background is null")。
 * 高度比卡牌类别（64）略高，是因为价格文案要同时说明「起售价 / 涨幅 / 上限」，
 * 在 {@link #TEXT_MAX_WIDTH} 下会折成三行。
 *
 * <p><b>1.20.1 移植说明</b>：本文件由 neoforge-1.21.1 的同路径文件镜像而来，
 * 目标 JEI 为 15.56.0.205。类别用到的 JEI API（IRecipeCategory / RecipeType /
 * IGuiHelper / IRecipeLayoutBuilder / IRecipeSlotBuilder / IDrawable / RecipeIngredientRole）
 * 在两版签名逐条一致；与 MC 版本相关的差异只有一处写法：本类的两处 id 拼装
 * （类别 id 由 RecipeType.create 自建、书签 id 在本类 getRegistryName 内）
 * 都用 {@code new ResourceLocation(ns, path)}，与仓库既有写法保持一致
 * （Forge 1.20.1 同样提供回移的 {@code ResourceLocation.fromNamespaceAndPath}，两者等价可互换）。
 */
public class ChipShopCategory implements IRecipeCategory<ChipShopRecipe> {

    /** 类别 id：astral_dice:chip_shop。 */
    public static final RecipeType<ChipShopRecipe> RECIPE_TYPE =
            RecipeType.create(AstralDiceMod.MODID, "chip_shop", ChipShopRecipe.class);

    /** 类别标题（JEI 标签页名）：筹码商店。 */
    private static final String KEY_TITLE = "jei.astral_dice.chip_shop";
    /** 获取方式说明：通过在筹码商店进行交易获得。 */
    private static final String KEY_OBTAIN = "jei.astral_dice.chip_shop.obtain";
    /** 价格说明：%s = 起售价 / 每次购买涨幅 / 上限（星币）。 */
    private static final String KEY_PRICE = "jei.astral_dice.chip_shop.price";

    /** 类别尺寸（background 为 null，必须自报宽高）。 */
    private static final int WIDTH = 210;
    private static final int HEIGHT = 84;

    /** 商店方块槽（催化剂）。 */
    private static final int SHOP_SLOT_X = 6;
    private static final int SHOP_SLOT_Y = 6;
    /** 星币槽（入场货币）。 */
    private static final int COIN_SLOT_X = 6;
    private static final int COIN_SLOT_Y = 32;
    /** 筹码输出槽。 */
    private static final int CHIP_SLOT_X = 54;
    private static final int CHIP_SLOT_Y = 6;
    /** 商店方块到筹码的箭头。 */
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

    public ChipShopCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.CHIP_SHOP.get()));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<ChipShopRecipe> getRecipeType() {
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
     * <p>JEI 的默认实现只对 {@code RecipeHolder} 返回 id，本 record 不是 RecipeHolder
     * ⇒ 默认返回 null，玩家把这条配方加入 JEI 书签时会因「No registry name for recipe」
     * 保存失败（重进游戏后书签丢失）。这里用「chip_shop/ + 筹码注册名」给出稳定且唯一的 id。
     */
    @Override
    public ResourceLocation getRegistryName(ChipShopRecipe recipe) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(recipe.chip());
        return new ResourceLocation(AstralDiceMod.MODID,
                "chip_shop/" + (key == null ? "unknown" : key.getPath()));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ChipShopRecipe recipe, IFocusGroup focuses) {
        // 催化剂：筹码商店方块本体（JEI 会据此把本类别挂到方块物品的「用途」页）
        builder.addSlot(RecipeIngredientRole.CATALYST, SHOP_SLOT_X, SHOP_SLOT_Y)
                .addItemStack(new ItemStack(ModBlocks.CHIP_SHOP.get()));
        // 输入：星币（交易用的货币）
        builder.addInputSlot(COIN_SLOT_X, COIN_SLOT_Y)
                .addItemStack(new ItemStack(ModItems.STAR_COIN.get()));
        // 输出：本记录对应的筹码
        builder.addOutputSlot(CHIP_SLOT_X, CHIP_SLOT_Y)
                .addItemStack(new ItemStack(recipe.chip()));
    }

    @Override
    public void draw(ChipShopRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
            double mouseX, double mouseY) {
        this.arrow.draw(guiGraphics, ARROW_X, ARROW_Y);
        Font font = Minecraft.getInstance().font;
        drawWrapped(guiGraphics, font, Component.translatable(KEY_OBTAIN), TEXT_X, OBTAIN_TEXT_Y);
        drawWrapped(guiGraphics, font, Component.translatable(KEY_PRICE,
                        ChipShopCatalog.PRICE_INITIAL, ChipShopCatalog.PRICE_STEP, ChipShopCatalog.PRICE_MAX),
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
