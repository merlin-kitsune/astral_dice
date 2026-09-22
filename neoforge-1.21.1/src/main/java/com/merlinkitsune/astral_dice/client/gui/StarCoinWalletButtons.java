package com.merlinkitsune.astral_dice.client.gui;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.astral_dice.economy.StarCoinCurrency;
import com.merlinkitsune.astral_dice.economy.StarCoinWalletActions;
import com.merlinkitsune.starenginelib.economy.StarCoinWalletState;
import com.merlinkitsune.astral_dice.network.StarCoinWalletPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 星币钱包的物品栏控件（NeoForge 1.21.1 客户端）：**一条余额条 + 三个按钮**。
 *
 * <p>不建独立界面：沿用「把控件注入原版物品栏界面」的做法，在 {@link ScreenEvent.Init.Post}
 * 时给 {@link InventoryScreen} 与 {@link CreativeModeInventoryScreen} 各挂一次；
 * 点击只发**意图包**（C2S），一切判定在服务端（见 {@link StarCoinWalletActions}）。
 *
 * <p><b>视觉与布局对齐 Magic Coins + SG-Economy 的同类界面</b>（尺寸/坐标逐像素一致），
 * 但**贴图全部自绘**（不复制对方任何像素）：
 * <ul>
 *   <li><b>余额条</b> 96x24，深色圆角面板（黑描边 + 浅灰内圈 + 深灰填充），
 *       左端由钱包按钮压住、右端右对齐显示当前余额；</li>
 *   <li><b>钱包按钮</b> 18x20，裸美术（同 MC 的 collect 按钮），点击 = 存入全部；</li>
 *   <li><b>星币 / 星币袋按钮</b> 13x13，**原版槽位风格边框**（黑描边 + 左上白高光 +
 *       {@code C6C6C6} 填充 + 右下暗边，圆角 2），中心 9x9 放货币图标；</li>
 *   <li>三个按钮各有一张 {@code _highlighted} 悬停贴图（钱包 = 暖金描边光晕，
 *       取款按钮 = 描边/填充转蓝），**不用白色蒙版**。</li>
 * </ul>
 *
 * <p>坐标都是相对界面左上角（{@code guiLeft/guiTop}）的偏移；基座**生存与创造各一套**：
 * <pre>
 *   控件              尺寸    生存 (x, y)   创造 (x, y)
 *   余额条            96x24   (0, -26)      (0, -76)      ← 钱包按钮位置再 (-2, -2)
 *   星币钱包          18x20   (2, -24)      (2, -74)
 *   星币              13x13   (77, 7)       (127, 5)
 *   星币袋            13x13   (77, 23)      (127, 21)
 * </pre>
 * 余额数字**右对齐**，右端固定在「钱包按钮 x + 88」（= 余额条左端 + 90），y 为「钱包按钮 y + 7」。
 * 玩家可用配置项再叠加 x/y 偏移（{@code star_coin_*_offset_x/y}）；余额条与数字跟随钱包按钮的偏移一起动。
 *
 * <p><b>创造栏的动态处理</b>：只有「物品栏」标签页显示（切到建筑/装饰等标签页即隐藏，
 * 余额条一并不显示），且 {@code guiLeft/guiTop} 随标签页变化 ⇒ 位置在**每帧渲染时**重算。
 *
 * <p>钱包功能关闭（{@code enable_star_coin_wallet = false}）时**一个控件都不创建**，
 * 界面与改动前完全一致。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class StarCoinWalletButtons {

    /** 星币钱包按钮（左上角）—— 点击 = 把物品栏与副手的全部星币/星币袋存入钱包。 */
    private static final ResourceLocation WALLET_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/star_coin_wallet_button.png");
    private static final ResourceLocation WALLET_HOVER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/star_coin_wallet_button_highlighted.png");
    /** 星币兑换按钮 —— 左键取 1 枚、Shift+左键取尽可能多。 */
    private static final ResourceLocation COIN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/star_coin_button.png");
    private static final ResourceLocation COIN_HOVER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/star_coin_button_highlighted.png");
    /** 星币袋兑换按钮 —— 同上，按袋取。 */
    private static final ResourceLocation BAG_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/star_coin_bag_button.png");
    private static final ResourceLocation BAG_HOVER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/star_coin_bag_button_highlighted.png");
    /** 余额条底图（深色圆角面板）。 */
    private static final ResourceLocation BALANCE_BAR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/star_coin_wallet_bar.png");

    private static final int WALLET_WIDTH = 18;
    private static final int WALLET_HEIGHT = 20;
    private static final int WALLET_X = 2;
    private static final int WALLET_Y_SURVIVAL = -24;
    private static final int WALLET_Y_CREATIVE = -74;

    /** 余额条相对钱包按钮左上角的偏移（按钮 (2,-24) → 条 (0,-26)）。 */
    private static final int BAR_INSET = 2;
    private static final int BAR_WIDTH = 96;
    private static final int BAR_HEIGHT = 24;

    /** 余额数字右端相对钱包按钮 x 的偏移（条左端 + 90），y 相对按钮 y 的偏移。 */
    private static final int BALANCE_TEXT_RIGHT = 88;
    private static final int BALANCE_TEXT_DY = 7;
    /** 数字颜色 = 纯白，**不带阴影**（与参考实现一致）。 */
    private static final int BALANCE_TEXT_COLOR = 0xFFFFFF;

    private static final int CONVERT_SIZE = 13;
    private static final int CONVERT_X_SURVIVAL = 77;
    private static final int CONVERT_X_CREATIVE = 127;
    private static final int COIN_Y_SURVIVAL = 7;
    private static final int COIN_Y_CREATIVE = 5;
    private static final int BAG_Y_SURVIVAL = 23;
    private static final int BAG_Y_CREATIVE = 21;

    private StarCoinWalletButtons() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!StarCoinCurrency.isWalletEnabled()) return;
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) {
            return;
        }
        AbstractContainerScreen<?> parent = (AbstractContainerScreen<?>) screen;
        boolean creative = screen instanceof CreativeModeInventoryScreen;

        event.addListener(new WalletButton(parent,
                WALLET_X + walletOffsetX(),
                (creative ? WALLET_Y_CREATIVE : WALLET_Y_SURVIVAL) + walletOffsetY(),
                WALLET_WIDTH, WALLET_HEIGHT, WALLET_TEXTURE, WALLET_HOVER_TEXTURE,
                StarCoinWalletActions.Action.DEPOSIT_ALL, null, true));

        int convertX = (creative ? CONVERT_X_CREATIVE : CONVERT_X_SURVIVAL) + convertOffsetX();
        event.addListener(new WalletButton(parent, convertX,
                (creative ? COIN_Y_CREATIVE : COIN_Y_SURVIVAL) + convertOffsetY(),
                CONVERT_SIZE, CONVERT_SIZE, COIN_TEXTURE, COIN_HOVER_TEXTURE,
                StarCoinWalletActions.Action.WITHDRAW_COIN_ONE, StarCoinWalletActions.Action.WITHDRAW_COIN_ALL,
                false));

        event.addListener(new WalletButton(parent, convertX,
                (creative ? BAG_Y_CREATIVE : BAG_Y_SURVIVAL) + bagOffsetY(),
                CONVERT_SIZE, CONVERT_SIZE, BAG_TEXTURE, BAG_HOVER_TEXTURE,
                StarCoinWalletActions.Action.WITHDRAW_BAG_ONE, StarCoinWalletActions.Action.WITHDRAW_BAG_ALL,
                false));
    }

    // === 配置偏移（配置未就绪时退回 0，不让渲染路径因配置异常而崩） ===

    private static int walletOffsetX() {
        try {
            return ModCommonConfig.STAR_COIN_WALLET_OFFSET_X.get();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int walletOffsetY() {
        try {
            return ModCommonConfig.STAR_COIN_WALLET_OFFSET_Y.get();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int convertOffsetX() {
        try {
            return ModCommonConfig.STAR_COIN_CONVERT_OFFSET_X.get();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int convertOffsetY() {
        try {
            return ModCommonConfig.STAR_COIN_CONVERT_OFFSET_Y.get();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int bagOffsetY() {
        try {
            return ModCommonConfig.STAR_COIN_BAG_CONVERT_OFFSET_Y.get();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * 跟随父界面定位的图标按钮（常态 / 悬停两张贴图各一张，不用白色蒙版）。
     *
     * <p>位置在**渲染时**按 {@code guiLeft/guiTop + 偏移} 重算（创造栏切标签会移动界面）。
     * 悬停高亮自行判定而不用 {@code isHovered()}：后者由 {@code AbstractWidget#render} 在
     * 进入 {@code renderWidget} **之前**按当时的 x/y 算好，而本按钮的位置恰恰是在
     * {@code renderWidget} 里才更新的 —— 用它会差一帧（创造栏切标签时肉眼可见）。
     *
     * <p>带余额条的那一枚（钱包按钮）在**自己身上**先画条、再画按钮本体、最后画数字：
     * 这样条一定在按钮之下（图标不会被面板压住），也天然跟随同一个 y 偏移一起移动。
     *
     * <p>点击语义：左键 = 主动作，Shift + 左键 = 「尽可能多」动作（未配置时退回主动作）。
     * 右键无动作（{@code AbstractButton} 默认只认左键），与「兑换 = 取出」的口径一致。
     */
    private static final class WalletButton extends AbstractButton {
        private final AbstractContainerScreen<?> parent;
        private final int offsetX;
        private final int offsetY;
        private final ResourceLocation texture;
        private final ResourceLocation hoverTexture;
        private final StarCoinWalletActions.Action primaryAction;
        private final StarCoinWalletActions.Action shiftAction;
        private final boolean withBalanceBar;

        WalletButton(AbstractContainerScreen<?> parent, int offsetX, int offsetY, int width, int height,
                     ResourceLocation texture, ResourceLocation hoverTexture,
                     StarCoinWalletActions.Action primaryAction, StarCoinWalletActions.Action shiftAction,
                     boolean withBalanceBar) {
            super(0, 0, width, height, Component.empty());
            this.parent = parent;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.texture = texture;
            this.hoverTexture = hoverTexture;
            this.primaryAction = primaryAction;
            this.shiftAction = shiftAction == null ? primaryAction : shiftAction;
            this.withBalanceBar = withBalanceBar;
        }

        /**
         * 创造模式下的隐藏判据。
         *
         * <p>**只有两个兑换按钮**受「物品栏」标签页限制：它们落在面板内右侧，切到建筑方块等
         * 标签页会压住原版物品格子并把点击抢走。余额条与钱包按钮位于面板**上方界外**，
         * 任何标签页都不遮挡原版元素 ⇒ 常显（「切游戏模式后整条钱包栏消失」正是原先
         * 把两类控件一起判定造成的）。
         */
        private boolean hiddenByCreativeTab() {
            return !withBalanceBar
                    && parent instanceof CreativeModeInventoryScreen creative
                    && !creative.isInventoryOpen();
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hidden = hiddenByCreativeTab();
            // ⚠️ 只写 active，**绝不写 visible**：AbstractWidget#render 仅在 visible 为真时才调用
            //    renderWidget ⇒ 一旦把 visible 置 false，本方法再也不会被调用，「隐藏」就变成
            //    **永久**（切回「物品栏」标签页也不恢复，必须关掉界面重开才回来）。
            //    active 只参与鼠标命中（AbstractWidget#mouseClicked 检查 active && visible），
            //    正是「隐藏即不可点」所需要的语义。
            this.active = !hidden;
            if (hidden) return;
            setX(parent.getGuiLeft() + offsetX);
            setY(parent.getGuiTop() + offsetY);
            if (withBalanceBar) {
                // 先铺余额条底图：面板压在按钮之下，按钮图标自然完整露出
                guiGraphics.blit(BALANCE_BAR_TEXTURE, getX() - BAR_INSET, getY() - BAR_INSET,
                        0.0F, 0.0F, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
            }
            boolean hovered = mouseX >= getX() && mouseY >= getY()
                    && mouseX < getX() + getWidth() && mouseY < getY() + getHeight();
            guiGraphics.blit(hovered ? hoverTexture : texture, getX(), getY(), 0.0F, 0.0F,
                    getWidth(), getHeight(), getWidth(), getHeight());
            if (withBalanceBar) {
                drawBalance(guiGraphics);
            }
        }

        /** 余额数字：右对齐到「按钮 x + 88」，y = 按钮 y + 7，白字无阴影。 */
        private void drawBalance(GuiGraphics guiGraphics) {
            Font font = Minecraft.getInstance().font;
            String text = Long.toString(StarCoinWalletState.balance());
            int x = getX() + BALANCE_TEXT_RIGHT - font.width(text);
            int y = getY() + BALANCE_TEXT_DY;
            guiGraphics.drawString(font, text, x, y, BALANCE_TEXT_COLOR, false);
        }

        /**
         * 按钮被按下 —— {@code AbstractButton} 的唯一抽象回调（**基类声明为 public**，
         * 覆盖时不能降可见性）。{@code AbstractWidget#mouseClicked} 只对左键走到这里
         * （{@code isValidClickButton} 的默认实现即 {@code button == 0}），右键不触发。
         */
        @Override
        public void onPress() {
            sendClick(Screen.hasShiftDown() ? shiftAction : primaryAction);
        }

        private void sendClick(StarCoinWalletActions.Action action) {
            if (action == null) return;
            PacketDistributor.sendToServer(new StarCoinWalletPayload(action.ordinal()));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
