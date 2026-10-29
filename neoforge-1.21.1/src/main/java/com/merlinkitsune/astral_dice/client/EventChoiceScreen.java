package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.EventChoiceCancelPayload;
import com.merlinkitsune.astral_dice.network.EventChoiceConfirmPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 大侦探立牌主动技能：<b>事件选择界面</b>（客户端 Screen，用户裁决 2026-10-10）。
 *
 * <p><b>布局（严格按参考图 images/shop/fanny_skill_preview.png，137x133；参考图本身不入库）</b>：
 * 面板以屏幕中心为原点整体居中，各部件坐标 = 参考图内相对坐标：
 * <ul>
 *   <li>fanny_skill_count（38x16）置于 (46,13)：其中居中显示**15 秒倒计时** {@code 0:15 → 0:00}
 *       （2026-10-11 用户裁决：删除原「选择事件」文本；倒计时与
 *       {@code EventChoiceManager#CHOICE_TIMEOUT_TICKS} **同源** —— 剩余 tick 由服务端权威下发，
 *       客户端不自行递减）；</li>
 *   <li>fanny_skill_event / fanny_skill_event_select（44x67）分别置于 (15,32) 与 (72,32)；</li>
 *   <li>fanny_skill_confirm（51x22）置于 (41,101)。</li>
 * </ul>
 *
 * <p><b>事件卡</b>沿用现有事件卡那套「框架图 + 事件图标 + MC 字体文本」的渲染口径
 * （直接复用 EventCardOverlay.drawCardAt，与弹卡覆盖层的 drawCard 逐字同源）。
 *
 * <p>交互：鼠标指向的事件卡显示高亮（fanny_skill_event_select），单击后底图替换为它；
 * 确认按钮三态 = fanny_skill_confirm / fanny_skill_confirm_highlight（指向）/ fanny_skill_confirm_push（按下）；
 * 必须**先选一个**再点确认才生效。
 *
 * <p>不暂停游戏：15 秒等待期由服务端 tick 驱动，暂停会冻住服务端 tick 让等待期永不到期。
 *
 * <p><b>ESC 两段式，退出即强制触发第一个事件</b>（2026-10-29 用户裁决 R1/R2）：第一次 ESC 只弹
 * actionbar 警告（{@code message.astral_dice.event_choice.esc_warning}），第二次 ESC 关界面并把
 * {@link EventChoiceCancelPayload} 当作「退出意图」发给服务端（服务端按强制第一个选项结算，不新增包类）；
 * 已确认后关界面不再发（会话已进入结算）。被别的界面顶掉（换维度 / 死亡 / 断线）由 {@link #removed()} 补发同一意图。
 */
public class EventChoiceScreen extends Screen {
    // ---- 参考图 images/shop/fanny_skill_preview.png（137x133）内的相对坐标 ----
    private static final int PANEL_W = 137;
    private static final int PANEL_H = 133;

    private static final int TITLE_X = 46;
    private static final int TITLE_Y = 13;
    private static final int TITLE_W = 38;
    private static final int TITLE_H = 16;
    /** 默认字体字形四边形的高（bitmap provider 的 {@code height:8}；{@code Font#lineHeight} 为 9）。 */
    private static final int GLYPH_QUAD_H = 8;

    private static final int CARD_Y = 32;
    private static final int CARD_FIRST_X = 15;
    private static final int CARD_SECOND_X = 72;

    private static final int CONFIRM_X = 41;
    private static final int CONFIRM_Y = 101;
    private static final int CONFIRM_W = 51;
    private static final int CONFIRM_H = 22;

    private static final ResourceLocation TITLE_TEX = tex("fanny_skill_count.png");
    private static final ResourceLocation CARD_TEX = tex("fanny_skill_event.png");
    private static final ResourceLocation CARD_SELECT_TEX = tex("fanny_skill_event_select.png");
    private static final ResourceLocation CONFIRM_TEX = tex("fanny_skill_confirm.png");
    private static final ResourceLocation CONFIRM_HIGHLIGHT_TEX = tex("fanny_skill_confirm_highlight.png");
    private static final ResourceLocation CONFIRM_PUSH_TEX = tex("fanny_skill_confirm_push.png");

    /** 仅作 Screen 的 title（无障碍/其它模组读它）；**不再**画在标题条上（标题条改为倒计时）。 */
    private static final Component TITLE = Component.translatable("gui.astral_dice.event_choice.title");

    private final int token;
    private final ResourceLocation[] options;
    private final int[] durations;
    /**
     * 服务端**权威**剩余 tick（标题条倒计时显示的唯一来源）。
     *
     * <p>2026-10-11 用户裁决「以服务端剩余 tick 为准，别各算一套」：客户端**不再**自己按 elapsed 递减，
     * 而是直接用服务端每秒下发的值渲染（{@code EventChoiceTickPayload}）；两次下发之间保持不变，
     * 正好是秒级显示的语义。初始值 = 开始包里的 {@code timeoutTicks}
     * （= {@code EventChoiceManager.CHOICE_TIMEOUT_TICKS} = 300t ⇒ 开局显示 {@code 0:15}）。
     */
    private int serverRemainingTicks;
    /** 已选中的选项索引（-1 = 尚未选择）。 */
    private int selectedIndex = -1;
    private boolean confirmPressed;
    private boolean confirmed;
    /** ESC 两段式：第一次按下只弹 actionbar 警告（{@code true} = 已警告过）。 */
    private boolean escalationWarned;
    /**
     * ESC 是否仍处于「按住」状态：原版会把 GLFW 的 REPEAT（{@code action==2}，按住 0.5s 后每帧一条）
     * 也派发给 {@link #keyPressed}，靠本标记把它们吞掉，否则按住不放会立刻越过第二段。
     */
    private boolean escHeld;
    /** 退出意图是否已发（同一会话只发一次，避免 ESC -> removed() 重复发）。 */
    private boolean exitSent;
    /** 服务端自己关的界面（{@code EventChoiceClientHandler#closeIfToken}）：此时 {@link #removed()} 不再发退出意图。 */
    private boolean closedByServer;

    public EventChoiceScreen(int token, ResourceLocation firstEvent, ResourceLocation secondEvent,
                             int firstDurationTicks, int secondDurationTicks, int timeoutTicks) {
        super(TITLE);
        this.token = token;
        this.options = new ResourceLocation[] { firstEvent, secondEvent };
        this.durations = new int[] { firstDurationTicks, secondDurationTicks };
        this.serverRemainingTicks = Math.max(0, timeoutTicks);
    }

    /** 会话 token（客户端只在「关闭包 token 一致」时关界面）。 */
    public int token() {
        return token;
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/shop/" + name);
    }

    private int originX() {
        return (this.width - PANEL_W) / 2;
    }

    private int originY() {
        return (this.height - PANEL_H) / 2;
    }

    private int cardX(int index) {
        return originX() + (index == 0 ? CARD_FIRST_X : CARD_SECOND_X);
    }

    private int cardY() {
        return originY() + CARD_Y;
    }

    private int confirmX() {
        return originX() + CONFIRM_X;
    }

    private int confirmY() {
        return originY() + CONFIRM_Y;
    }

    private boolean overCard(int index, double mx, double my) {
        int x = cardX(index);
        int y = cardY();
        return mx >= x && mx < x + EventCardOverlay.FRAME_W && my >= y && my < y + EventCardOverlay.FRAME_H;
    }

    private boolean overConfirm(double mx, double my) {
        int x = confirmX();
        int y = confirmY();
        return mx >= x && mx < x + CONFIRM_W && my >= y && my < y + CONFIRM_H;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int ox = originX();
        int oy = originY();
        // 1) 标题条 fanny_skill_count：**不画原「选择事件」文本**，改为 15 秒倒计时（m:ss，0:15 → 0:00）。
        //    同源：剩余 tick 由服务端权威下发（EventChoiceTickPayload，每秒一次），客户端**不再**自己递减
        //    （2026-10-11 用户裁决「以服务端剩余 tick 为准，别各算一套」；到点由服务端 forceFirst 结算并关界面）。
        guiGraphics.blit(TITLE_TEX, ox + TITLE_X, oy + TITLE_Y, 0.0F, 0.0F, TITLE_W, TITLE_H, TITLE_W, TITLE_H);
        // 2026-10-11 微调（用户裁决「顶部时间没完全居中」）：倒计时按**字体 advance 推出的墨迹 bbox** 居中，
        // 而不是 (条宽 - advance) / 2 的整数除 —— 后者按 advance（含末字符右侧 1px 字距）而非墨迹算，
        // 且末位小数被整数除截断：实测墨迹中心比标题条中心偏左上各 0.5 GUI px。
        // 依据（MC 1.21.1 源码，非推测）：
        //   * BitmapProvider#load 里每个字形的 advance = (int)(0.5 + inkWidth * scale) + 1 ⇒ advance = 墨迹宽 + 1；
        //   * SheetGlyphInfo#getBearingLeft() 默认 0，BitmapProvider 未覆写 ⇒ 字形四边形左 bearing = 0；
        //   ⇒ 一行文本的墨迹连续区间 = [x, x + advance - 1)（末字符右侧那 1px 字距不是墨迹）。
        // 让墨迹覆盖的像素列与标题条覆盖的像素列（TITLE_X .. TITLE_X + TITLE_W - 1）同中心
        //   ⇒ x = TITLE_X + (TITLE_W + 1 - advance) / 2（比按 advance 居中多 0.5，即半像素量）。
        // 倒计时只含 0-9 与 ':'，在 ascii.png 里墨迹都自格内第 0 列起（已用位图逐个核对），故墨迹起点 = 绘制起点。
        String countdown = formatCountdown(serverRemainingTicks);
        int advance = this.font.width(countdown);
        guiGraphics.drawString(this.font, countdown,
                ox + TITLE_X + (TITLE_W + 1 - advance) / 2.0F,
                oy + TITLE_Y + (TITLE_H + 1 - GLYPH_QUAD_H) / 2.0F, 0xFFFFFFFF, false);
        // 2) 两张事件卡（与现有事件卡同口径：框架图 + 事件图标 + MC 字体名称/计时器）
        for (int i = 0; i < 2; i++) {
            boolean lit = this.selectedIndex == i || this.overCard(i, mouseX, mouseY);
            EventCardOverlay.drawCardAt(guiGraphics, this.minecraft, lit ? CARD_SELECT_TEX : CARD_TEX,
                    this.options[i], this.durations[i], 0, cardX(i), cardY());
        }
        // 3) 确认按钮三态
        ResourceLocation buttonTex = CONFIRM_TEX;
        if (this.confirmed || this.confirmPressed) {
            buttonTex = CONFIRM_PUSH_TEX;
        } else if (this.overConfirm(mouseX, mouseY)) {
            buttonTex = CONFIRM_HIGHLIGHT_TEX;
        }
        guiGraphics.blit(buttonTex, confirmX(), confirmY(), 0.0F, 0.0F, CONFIRM_W, CONFIRM_H, CONFIRM_W, CONFIRM_H);
    }

    /** 服务端权威倒计时同步（{@code EventChoiceTickPayload}，剩余秒数变化时下发一次）。 */
    public void setServerRemainingTicks(int remainingTicks) {
        this.serverRemainingTicks = Math.max(0, remainingTicks);
    }

    /** 剩余 tick → {@code m:ss}（向上取整：开局先显示 0:15；不足 1 秒显示 0:00，不会出现负数）。 */
    private static String formatCountdown(int remainingTicks) {
        int sec = Math.max(0, remainingTicks + 19) / 20;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !this.confirmed) {
            // 单击事件卡 = 选中；底图随即替换为 fanny_skill_event_select（见 render）
            for (int i = 0; i < 2; i++) {
                if (overCard(i, mouseX, mouseY)) {
                    this.selectedIndex = i;
                    return true;
                }
            }
            if (overConfirm(mouseX, mouseY)) {
                this.confirmPressed = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.confirmPressed) {
            this.confirmPressed = false;
            // 必须**先选一个**再点确认；确认后界面保持打开，等服务端「等待 1 秒 -> 关闭包」收尾
            if (!this.confirmed && this.selectedIndex >= 0 && overConfirm(mouseX, mouseY)) {
                this.confirmed = true;
                PacketDistributor.sendToServer(new EventChoiceConfirmPayload(this.token, this.selectedIndex));
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 不暂停游戏：15 秒等待期由服务端 tick 推进，单机暂停会冻住服务端 tick 则等待期永不到期。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * ESC 两段式（2026-10-29 用户裁决 R2）：第一次只弹 actionbar 警告，第二次才真正退出。
     *
     * <p>本类的关闭开关恒为 {@code false}，原版因此不会替我们关界面，
     * ESC 的全部行为都在这里（{@code Screen#keyPressed} 在关闭开关为 false 时
     * 既不关窗也不给任何反馈，所以必须自己接管）；其它按键照常交给 {@code super}。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // 2026-10-29 收口修复：点过确认、而服务端 Close 包还没回来的 1~2 tick 窗口里按 ESC，
            // 结算已经发生，此时再弹「退出会强制触发第一个事件」与实际语义相反 ⇒ 原样交回 super。
            // 不弹警告、不 requestExit、不发包（requestExit / onClose / removed 各自另有 confirmed 闸门）。
            if (this.confirmed) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (this.escHeld) {
                // 同一次按住的 GLFW REPEAT ⇒ 吞掉，不算「第二次按下」
                return true;
            }
            this.escHeld = true;
            if (!this.escalationWarned) {
                this.escalationWarned = true;
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(
                            Component.translatable("message.astral_dice.event_choice.esc_warning"), true);
                }
                return true;
            }
            requestExit();
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 松开 ESC 时复位「按住」标记，让下一次按下重新算作一次独立的按下。 */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.escHeld = false;
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    /**
     * 窗口失焦兜底：失焦后 GLFW 不再派发按键回调，{@link #keyReleased} 收不到；而原版清键状态走的
     * {@code KeyMapping.releaseAll()}（1.21.1 里唯一调用点是 {@code Minecraft#setScreen}）
     * 同样不派发 {@code keyReleased}。于是 {@link #escHeld} 会卡在 {@code true}，
     * 吞掉恢复焦点后的第一次 ESC 按下。这里每 tick 复查窗口焦点，失焦即复位标记。
     */
    @Override
    public void tick() {
        super.tick();
        if (this.minecraft != null && !this.minecraft.isWindowActive()) {
            // 失焦期间 GLFW 不投递按键回调（KeyMapping.releaseAll() 的唯一调用点是 Minecraft#setScreen，
            // 与失焦无关），escHeld 会卡在 true；而回焦后若仍按住 ESC，REPEAT 会被当作一次 keyPressed。
            // 因此这里把「已警告」一并复位，保证任何退出路径之前都刚刚显示过警告。
            this.escHeld = false;
            this.escalationWarned = false;
        }
    }

    /** ESC 不再由原版直接关窗（2026-10-29 用户裁决 R2）：全部交给 {@link #keyPressed} 的两段式处理。 */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /**
     * 界面关闭回调（ESC 第二次按下 / 原版关屏路径走这里，是本界面唯一的**玩家主动**退出入口）。
     *
     * <p>2026-10-29 用户裁决 R1：<b>未确认</b>就退出 = 强制触发第一个事件 ⇒ 给服务端发退出意图
     * （复用取消包，服务端语义见 {@code EventChoiceManager}）。已确认（{@code confirmed}）时不发 ——
     * 服务端已进入结算流程；重复发送对服务端是幂等忽略的。
     */
    @Override
    public void onClose() {
        if (!this.confirmed) {
            requestExit();
        }
        super.onClose();
    }

    /**
     * 界面被移除（含被别的 Screen 顶掉：换维度 / 死亡 / 断线 / 其它模组抢屏）。
     *
     * <p>这些路径拿不到 {@link #onClose()}，所以在这里补发退出意图（2026-10-29 用户裁决 R1
     * 「任何形式的退出都强制触发第一个事件」）；服务端自己关的界面
     * （{@code EventChoiceClientHandler#closeIfToken} -> {@link #markClosedByServer()}）除外。
     */
    @Override
    public void removed() {
        if (!this.confirmed && !this.exitSent && !this.closedByServer) {
            requestExit();
        }
        super.removed();
    }

    /** 发退出意图（复用既有取消包，不新增包类）。幂等：已确认或已发过则直接返回。 */
    private void requestExit() {
        if (this.confirmed || this.exitSent) {
            return;
        }
        this.exitSent = true;
        PacketDistributor.sendToServer(new EventChoiceCancelPayload(this.token));
    }

    /** 服务端主动关屏：抑制 {@link #removed()} 里的退出意图（服务端已自行结算）。 */
    public void markClosedByServer() {
        this.closedByServer = true;
    }
}
