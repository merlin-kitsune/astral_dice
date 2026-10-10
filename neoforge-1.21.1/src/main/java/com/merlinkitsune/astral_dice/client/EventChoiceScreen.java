package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.EventChoiceConfirmPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

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
 * <p>不暂停游戏、ESC 不可关闭：15 秒等待期由服务端 tick 驱动，客户端主动关界面不会结束会话
 * （结算由服务端 EventChoiceManager 权威决定）。
 */
public class EventChoiceScreen extends Screen {
    // ---- 参考图 images/shop/fanny_skill_preview.png（137x133）内的相对坐标 ----
    private static final int PANEL_W = 137;
    private static final int PANEL_H = 133;

    private static final int TITLE_X = 46;
    private static final int TITLE_Y = 13;
    private static final int TITLE_W = 38;
    private static final int TITLE_H = 16;

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
        Component countdown = Component.literal(formatCountdown(serverRemainingTicks));
        guiGraphics.drawString(this.font, countdown,
                ox + TITLE_X + (TITLE_W - this.font.width(countdown)) / 2,
                oy + TITLE_Y + (TITLE_H - 8) / 2, 0xFFFFFFFF, false);
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

    /** ESC 不可关闭：必须选一个（或等服务端强制结算）。 */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
