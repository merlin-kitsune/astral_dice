package com.merlinkitsune.astral_dice.client.gui;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.astral_dice.economy.StarCoinCurrency;
import com.merlinkitsune.astral_dice.economy.StarCoinWalletActions;
import com.merlinkitsune.astral_dice.network.StarCoinWalletPayload;
import com.merlinkitsune.starenginelib.economy.StarCoinWalletState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * 星币钱包的物品栏控件（NeoForge 26.1.2 客户端）：**一条余额条 + 三个按钮**。
 *
 * <p>不建独立界面：沿用「把控件叠加到原版物品栏界面上」的做法，但**不再使用 widget 体系** ——
 * 点击只发**意图包**（C2S），一切判定在服务端（见 {@link StarCoinWalletActions}）。
 *
 * <h2>26.1.2 重写说明（与 1.21.1 实现的结构性差异）</h2>
 * 26.1.2（Mojang 2026 界面重组）删除了整个 retained-mode widget 体系，本类因此**整体重写**：
 * <table border="1">
 *   <tr><th>1.21.1 做法</th><th>26.1.2 现实</th><th>本类重写为</th></tr>
 *   <tr><td>{@code AbstractButton} 子类 + {@code renderWidget(GuiGraphics,...)}</td>
 *       <td>{@code AbstractButton}/{@code AbstractWidget} 已删除（{@code javap} 实证：{@code AbstractButton}
 *           无任何 {@code render*} 方法；{@code components} 包只剩 {@code events.*}）</td>
 *       <td>不建任何 widget，改在 {@link ContainerScreenEvent.Render.Foreground} <b>即时模式</b>直接绘制</td></tr>
 *   <tr><td>{@code ScreenEvent.Init.Post#addListener(AbstractWidget)}</td>
 *       <td>{@code addListener} 现收 {@code GuiEventListener}；控件需自行实现命中测试</td>
 *       <td>改用 {@link ScreenEvent.MouseButtonPressed.Pre} 自算命中矩形</td></tr>
 *   <tr><td>{@code GuiGraphics.blit/drawString}</td>
 *       <td>{@code GuiGraphics} 已删除 ⇒ {@link GuiGraphicsExtractor}；{@code drawString} 改名 {@code text}</td>
 *       <td>{@code extractor.blit(Identifier,IIIIFFFF)} / {@code extractor.text(Font,String,IIIZ)}</td></tr>
 * </table>
 * 位置改为**每帧现算**（原实现在 {@code renderWidget} 里 {@code setX/setY}）——即时模式下天然每帧重算，
 * 反而更直接地满足了「创造栏切标签时 {@code guiLeft/guiTop} 会变」的要求。
 *
 * <p><b>视觉与布局对齐 Magic Coins + SG-Economy 的同类界面</b>（尺寸/坐标逐像素一致），
 * 但**贴图全部自绘**（不复制对方任何像素）：
 * <ul>
 *   <li><b>余额条</b> 96x24，深色圆角面板，左端由钱包按钮压住、右端右对齐显示当前余额；</li>
 *   <li><b>钱包按钮</b> 18x20，裸美术（同 MC 的 collect 按钮），点击 = 存入全部；</li>
 *   <li><b>星币 / 星币袋按钮</b> 13x13，**原版槽位风格边框**，中心 9x9 放货币图标；</li>
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
 * 余额数字**右对齐**，右端固定在「钱包按钮 x + 88」，y 为「钱包按钮 y + 7」，白字**无阴影**。
 * 玩家可用配置项再叠加 x/y 偏移（{@code star_coin_*_offset_x/y}）；余额条与数字跟随钱包按钮一起动。
 *
 * <p><b>绘制顺序</b>：余额条 → 钱包按钮 → 其余两个按钮 → 余额数字。
 * 数字最后画，保证不被任何贴图压住；条先画，保证钱包图标完整露出。
 *
 * <p>钱包功能关闭（{@code enable_star_coin_wallet = false}）时**一个控件都不画**，
 * 界面与改动前完全一致。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class StarCoinWalletButtons {

    /** 星币钱包按钮（左上角）—— 点击 = 把物品栏与副手的全部星币/星币袋存入钱包。 */
    private static final Identifier WALLET_TEXTURE = id("textures/gui/star_coin_wallet_button.png");
    private static final Identifier WALLET_HOVER_TEXTURE = id("textures/gui/star_coin_wallet_button_highlighted.png");
    /** 星币兑换按钮 —— 左键取 1 枚、Shift+左键取尽可能多。 */
    private static final Identifier COIN_TEXTURE = id("textures/gui/star_coin_button.png");
    private static final Identifier COIN_HOVER_TEXTURE = id("textures/gui/star_coin_button_highlighted.png");
    /** 星币袋兑换按钮 —— 同上，按袋取。 */
    private static final Identifier BAG_TEXTURE = id("textures/gui/star_coin_bag_button.png");
    private static final Identifier BAG_HOVER_TEXTURE = id("textures/gui/star_coin_bag_button_highlighted.png");
    /** 余额条底图（深色圆角面板）。 */
    private static final Identifier BALANCE_BAR_TEXTURE = id("textures/gui/star_coin_wallet_bar.png");

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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, path);
    }

    /**
     * 目标界面：原版生存物品栏，或创造物品栏。
     *
     * <p>创造栏额外要求当前停在「物品栏」标签页 —— 切到建筑/装饰等标签页时整组控件隐藏
     * （余额条一并不显示），与 1.21.1 行为一致。
     */
    private static AbstractContainerScreen<?> resolveTarget(net.minecraft.client.gui.screens.Screen screen) {
        if (screen instanceof CreativeModeInventoryScreen creative) {
            return creative.isInventoryOpen() ? creative : null;
        }
        return (screen instanceof InventoryScreen inv) ? inv : null;
    }

    /**
     * 一个控件的几何与语义（即时模式下的「虚拟按钮」）。
     *
     * <p>{@code yBase} 由 {@code creative} 决定取生存还是创造那一套；{@code offsetX/offsetY}
     * 是配置偏移。命中测试与绘制共用同一份计算，两者不可能漂移。
     */
    private record Hit(int x, int y, int width, int height,
                       Identifier texture, Identifier hoverTexture,
                       StarCoinWalletActions.Action primaryAction, StarCoinWalletActions.Action shiftAction,
                       boolean withBalanceBar) {

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }
    }

    /**
     * 算出当前界面上全部控件的几何（相对屏幕坐标，已含 guiLeft/guiTop 与配置偏移）。
     *
     * <p>顺序即绘制顺序：余额条（在钱包按钮内部先画）→ 钱包按钮 → 星币 → 星币袋。
     */
    private static java.util.List<Hit> layout(AbstractContainerScreen<?> screen) {
        // getLeftPos()/getTopPos() 是 26.1.2 的现行访问器；1.21.1 用的 getGuiLeft()/getGuiTop()
        // 在本版本已被标注 @Deprecated(forRemoval=true)（编译期 warning 实证），故改用新名。
        int guiLeft = screen.getLeftPos();
        int guiTop = screen.getTopPos();
        boolean creative = screen instanceof CreativeModeInventoryScreen;

        int walletX = guiLeft + WALLET_X + walletOffsetX();
        int walletY = guiTop + (creative ? WALLET_Y_CREATIVE : WALLET_Y_SURVIVAL) + walletOffsetY();

        int convertX = guiLeft + (creative ? CONVERT_X_CREATIVE : CONVERT_X_SURVIVAL) + convertOffsetX();
        int coinY = guiTop + (creative ? COIN_Y_CREATIVE : COIN_Y_SURVIVAL) + convertOffsetY();
        int bagY = guiTop + (creative ? BAG_Y_CREATIVE : BAG_Y_SURVIVAL) + bagOffsetY();

        return java.util.List.of(
                new Hit(walletX, walletY, WALLET_WIDTH, WALLET_HEIGHT,
                        WALLET_TEXTURE, WALLET_HOVER_TEXTURE,
                        StarCoinWalletActions.Action.DEPOSIT_ALL, null, true),
                new Hit(convertX, coinY, CONVERT_SIZE, CONVERT_SIZE,
                        COIN_TEXTURE, COIN_HOVER_TEXTURE,
                        StarCoinWalletActions.Action.WITHDRAW_COIN_ONE,
                        StarCoinWalletActions.Action.WITHDRAW_COIN_ALL, false),
                new Hit(convertX, bagY, CONVERT_SIZE, CONVERT_SIZE,
                        BAG_TEXTURE, BAG_HOVER_TEXTURE,
                        StarCoinWalletActions.Action.WITHDRAW_BAG_ONE,
                        StarCoinWalletActions.Action.WITHDRAW_BAG_ALL, false));
    }

    /**
     * 绘制：{@link ContainerScreenEvent.Render.Foreground} 在原版界面内容之后派发，
     * 携带 {@link GuiGraphicsExtractor} 与当前鼠标坐标 —— 正好是「叠加控件」的落点。
     */
    @SubscribeEvent
    // 2026-09-22（26.1.2 冒烟测试）修正：原先订阅的是 ContainerScreenEvent.Render.Foreground，
    // 该事件在 NeoForge 26.1.2 已被 @Deprecated(since="26.1.2", forRemoval=true) 取代
    // （源码 net/neoforged/neoforge/client/event/ContainerScreenEvent.java 原文：
    //   "@deprecated Use {@link ScreenEvent.Render.Foreground} instead."）。
    // ⚠️ 类仍在 ⇒ **能编译**，但 NeoForge 已不再派发它 ⇒ 钱包控件在实机上**一个都不渲染**
    // （余额条 / 钱包按钮 / 星币 / 星币袋全无），且**不报任何错** —— 典型的
    // 「编译期存在、运行期静默失效」。现改用新事件 ScreenEvent.Render.Foreground。
    public static void onContainerScreenRender(ScreenEvent.Render.Foreground event) {
        if (!StarCoinCurrency.isWalletEnabled()) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (resolveTarget(screen) == null) return;

        GuiGraphicsExtractor gfx = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        for (Hit hit : layout(screen)) {
            if (hit.withBalanceBar()) {
                // 先铺余额条底图：面板压在按钮之下，按钮图标自然完整露出
                gfx.blit(BALANCE_BAR_TEXTURE, hit.x() - BAR_INSET, hit.y() - BAR_INSET,
                        BAR_WIDTH, BAR_HEIGHT, 0.0F, 0.0F, (float) BAR_WIDTH, (float) BAR_HEIGHT);
            }
            boolean hovered = hit.contains(mouseX, mouseY);
            // 26.1.2 的 blit(Identifier, int x, int y, int w, int h, float u, float v, float texW, float texH)
            // ——注意与 1.21.1 的 blit(Identifier, int,int, float u,float v, int w,int h, int tw,int th)
            //   参数顺序不同：w/h 提前到第 4/5 位，UV 与贴图尺寸后移且为 float。
            gfx.blit(hovered ? hit.hoverTexture() : hit.texture(), hit.x(), hit.y(),
                    hit.width(), hit.height(), 0.0F, 0.0F, (float) hit.width(), (float) hit.height());
            if (hit.withBalanceBar()) {
                drawBalance(gfx, hit.x(), hit.y());
            }
        }
    }

    /** 余额数字：右对齐到「按钮 x + 88」，y = 按钮 y + 7，白字无阴影。 */
    private static void drawBalance(GuiGraphicsExtractor gfx, int walletX, int walletY) {
        Font font = Minecraft.getInstance().font;
        String text = Long.toString(StarCoinWalletState.balance());
        int x = walletX + BALANCE_TEXT_RIGHT - font.width(text);
        int y = walletY + BALANCE_TEXT_DY;
        gfx.text(font, text, x, y, BALANCE_TEXT_COLOR, false);
    }

    /**
     * 点击：即时模式下没有控件去消费鼠标事件，故自行做命中测试后发意图包。
     *
     * <p>只认**左键**（{@code button == 0}），与 1.21.1 的 {@code AbstractButton} 默认
     * {@code isValidClickButton} 口径一致；右键无动作。命中即 {@code setCanceled}，
     * 避免同时穿透到原版槽位（点击落在物品栏槽位区域之外时本不会被原版消费，
     * 但显式取消可让语义更干净、也不受未来原版改动影响）。
     *
     * <p>Shift 判定走 {@code MouseButtonEvent#hasShiftDown()}（{@code InputWithModifiers} 的默认方法）——
     * 26.1.2 已删除 {@code Screen.hasShiftDown()} 这个静态便捷入口，修饰键状态改为随输入事件一起传递
     * （{@code javap} 实证：{@code Screen} 无任何 {@code *Shift*} 成员）。
     *
     * <p>Shift+左键 = 「尽可能多」动作，与「兑换 = 取出」的口径一致。
     */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!StarCoinCurrency.isWalletEnabled()) return;
        AbstractContainerScreen<?> screen = resolveTarget(event.getScreen());
        if (screen == null) return;
        if (event.getButton() != 0) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        boolean shift = event.getMouseButtonEvent().hasShiftDown();
        for (Hit hit : layout(screen)) {
            if (!hit.contains(mouseX, mouseY)) continue;
            StarCoinWalletActions.Action action = shift ? hit.shiftAction() : hit.primaryAction();
            if (action == null) return;
            ClientPacketDistributor.sendToServer(new StarCoinWalletPayload(action.ordinal()));
            event.setCanceled(true);
            return;
        }
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
}
