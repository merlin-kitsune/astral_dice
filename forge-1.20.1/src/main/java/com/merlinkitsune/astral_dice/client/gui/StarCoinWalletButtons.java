package com.merlinkitsune.astral_dice.client.gui;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.astral_dice.economy.StarCoinCurrency;
import com.merlinkitsune.astral_dice.economy.StarCoinWalletActions;
import com.merlinkitsune.astral_dice.network.ModNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 星币钱包的三个物品栏按钮（Forge 1.20.1 客户端）。
 *
 * <p>语义与 1.21.1 侧 {@code StarCoinWalletButtons} 逐字等价，平台差异只有三处：
 * 事件注解（{@code @Mod.EventBusSubscriber}）、{@link ResourceLocation} 的构造方式，
 * 以及走 {@link ModNetwork} 的 {@code SimpleChannel} 发 C2S 意图包。
 *
 * <p>不建独立界面：在 {@link ScreenEvent.Init.Post} 时给 {@link InventoryScreen} 与
 * {@link CreativeModeInventoryScreen} 各挂三个按钮；点击只发意图包（C2S），
 * 一切判定在服务端（见 {@link StarCoinWalletActions}）。
 *
 * <p><b>基座坐标与尺寸与 Magic Coins 的同类按钮逐像素一致</b>，且**生存与创造各一套**基座。
 * 坐标都是相对界面左上角（{@code guiLeft/guiTop}）的偏移：
 * <pre>
 *   按钮        尺寸    生存 (x, y)   创造 (x, y)
 *   星币钱包    18x20   (2, -24)      (2, -74)
 *   星币        13x13   (77, 7)       (127, 5)
 *   星币袋      13x13   (77, 23)      (127, 21)
 * </pre>
 * 玩家可用配置项再叠加 x/y 偏移（{@code star_coin_*_offset_x/y}）。
 *
 * <p>创造栏只有「物品栏」标签页显示按钮；位置在每帧渲染时重算（{@code guiLeft/guiTop}
 * 随标签页变化）。钱包功能关闭时一个按钮都不创建。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class StarCoinWalletButtons {

    /** 星币钱包按钮（左上角）—— 点击 = 把物品栏与副手的全部星币/星币袋存入钱包。 */
    private static final ResourceLocation WALLET_TEXTURE =
            new ResourceLocation(AstralDiceMod.MODID, "textures/gui/star_coin_wallet_button.png");
    /** 星币兑换按钮 —— 左键取 1 枚、Shift+左键取尽可能多。 */
    private static final ResourceLocation COIN_TEXTURE =
            new ResourceLocation(AstralDiceMod.MODID, "textures/gui/star_coin_button.png");
    /** 星币袋兑换按钮 —— 同上，按袋取。 */
    private static final ResourceLocation BAG_TEXTURE =
            new ResourceLocation(AstralDiceMod.MODID, "textures/gui/star_coin_bag_button.png");

    private static final int WALLET_WIDTH = 18;
    private static final int WALLET_HEIGHT = 20;
    private static final int WALLET_X = 2;
    private static final int WALLET_Y_SURVIVAL = -24;
    private static final int WALLET_Y_CREATIVE = -74;

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
                WALLET_WIDTH, WALLET_HEIGHT, WALLET_TEXTURE,
                StarCoinWalletActions.Action.DEPOSIT_ALL, null));

        int convertX = (creative ? CONVERT_X_CREATIVE : CONVERT_X_SURVIVAL) + convertOffsetX();
        event.addListener(new WalletButton(parent, convertX,
                (creative ? COIN_Y_CREATIVE : COIN_Y_SURVIVAL) + convertOffsetY(),
                CONVERT_SIZE, CONVERT_SIZE, COIN_TEXTURE,
                StarCoinWalletActions.Action.WITHDRAW_COIN_ONE, StarCoinWalletActions.Action.WITHDRAW_COIN_ALL));

        event.addListener(new WalletButton(parent, convertX,
                (creative ? BAG_Y_CREATIVE : BAG_Y_SURVIVAL) + bagOffsetY(),
                CONVERT_SIZE, CONVERT_SIZE, BAG_TEXTURE,
                StarCoinWalletActions.Action.WITHDRAW_BAG_ONE, StarCoinWalletActions.Action.WITHDRAW_BAG_ALL));
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
     * 跟随父界面定位的图标按钮。
     *
     * <p>位置在**渲染时**按 {@code guiLeft/guiTop + 偏移} 重算（创造栏切标签会移动界面）。
     * 悬停高亮自行判定而不用 {@code isHovered()}：后者由 {@code AbstractWidget#render} 在
     * 进入 {@code renderWidget} **之前**按当时的 x/y 算好，而本按钮的位置恰恰是在
     * {@code renderWidget} 里才更新的 —— 用它会差一帧。
     *
     * <p>点击语义：左键 = 主动作，Shift + 左键 = 「尽可能多」动作（未配置时退回主动作）。
     * 右键无动作（{@code AbstractButton} 默认只认左键），与「兑换 = 取出」的口径一致。
     */
    private static final class WalletButton extends AbstractButton {
        private final AbstractContainerScreen<?> parent;
        private final int offsetX;
        private final int offsetY;
        private final ResourceLocation texture;
        private final StarCoinWalletActions.Action primaryAction;
        private final StarCoinWalletActions.Action shiftAction;

        WalletButton(AbstractContainerScreen<?> parent, int offsetX, int offsetY, int width, int height,
                     ResourceLocation texture,
                     StarCoinWalletActions.Action primaryAction, StarCoinWalletActions.Action shiftAction) {
            super(0, 0, width, height, Component.empty());
            this.parent = parent;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.texture = texture;
            this.primaryAction = primaryAction;
            this.shiftAction = shiftAction == null ? primaryAction : shiftAction;
        }

        /** 创造模式：只有「物品栏」标签页显示（切到其它标签页时隐藏且不可点）。 */
        private boolean hiddenByCreativeTab() {
            return parent instanceof CreativeModeInventoryScreen creative && !creative.isInventoryOpen();
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hidden = hiddenByCreativeTab();
            this.visible = !hidden;
            this.active = !hidden;
            if (hidden) return;
            setX(parent.getGuiLeft() + offsetX);
            setY(parent.getGuiTop() + offsetY);
            guiGraphics.blit(texture, getX(), getY(), 0, 0.0F, 0.0F,
                    getWidth(), getHeight(), getWidth(), getHeight());
            boolean hovered = mouseX >= getX() && mouseY >= getY()
                    && mouseX < getX() + getWidth() && mouseY < getY() + getHeight();
            if (hovered) {
                guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x55FFFFFF);
            }
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
            ModNetwork.sendToServer(new ModNetwork.StarCoinWalletMessage(action.ordinal()));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
