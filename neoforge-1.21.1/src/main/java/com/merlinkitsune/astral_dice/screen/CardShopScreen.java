package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.client.CardShopClientState;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.item.sign.LinglingSignManager;
import com.merlinkitsune.astral_dice.item.sign.MisakiSignItem;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.merlinkitsune.astral_dice.network.CardShopBuyPayload;
import com.merlinkitsune.astral_dice.network.CardShopTransferListPayload;
import com.merlinkitsune.astral_dice.network.CardShopTransferPayload;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import com.merlinkitsune.astral_dice.shop.CardShopMenu;
import com.merlinkitsune.starengine.economy.StarCoinWalletState;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 卡牌商店界面（2026-10-08 v4 定稿：坐标以 temp/t80/UI-DESIGN.md 的 bg 口径为准）。
 *
 * <p><b>主视图</b> = 贴图 {@code textures/gui/shop/card_shop_inventory_bg.png}(288x187) 整幅背景；
 * 所有像素坐标都取自该背景贴图坐标系（左上为原点）：
 * <ul>
 *   <li>标题带 {@code shop_title.png}(274x19) @ (7,3)，品红区内居中显示「卡牌商店」；
 *       大酬宾窗口内改播 {@code shop_title_light_1..3.png}（8 tick/帧循环）；</li>
 *   <li>6 个卡框 {@code shop_card(.select/.soldout).png}(44x61) @ (7+46i, 28)；卡面 38x38 @ 框内 (+3,+3)；
 *       卡名在名称框内区（实测填充区 x∈[2,41] / y∈[43,58]，取左右各内缩 1px 的 **38px** 可绘制区）内居中显示；
 *       超宽改为**左右滚动（marquee）、不缩放不截断**（2026-10-09 用户裁决）；折扣角标 17x14 贴卡面右下角；</li>
 *   <li>左按钮 (11,109) = 星光兑换星币；中按钮 (48,109) = 进入二级转账界面（星币 &lt; 5 或冷却中 ⇒ 灰化）；
 *       沙漏 (86,109) = 转账冷却倒计时；</li>
 *   <li>余额钱包 {@code shop_wallet.png}(96x24) @ (11,145)（**条内填充透明** ⇒ 与主背景花纹逐像素重叠；
 *       素材**自带左格星币图标** ink x∈[5,18]/y∈[5,18]，故不再单独画 {@code shop_coin_icon.png}）；
 *       数字锚点/颜色/阴影与玩家物品栏余额栏（{@code StarCoinWalletButtons#drawBalance}）
 *       **同口径（条坐标 +90 / +9）**；</li>
 *   <li>物品栏底图 {@code shop_item_inventory.png}(166x80) @ (114,99)，槽位坐标与
 *       {@link CardShopMenu#INVENTORY_X}/{@link CardShopMenu#INVENTORY_Y}/{@link CardShopMenu#INVENTORY_HOTBAR_Y} 同口径。</li>
 * </ul>
 *
 * <p><b>购买路径（2026-10-09 §3 用户裁决：单击只选中、双击快速移入、拖拽购买）</b>：
 * <ul>
 *   <li><b>单击</b> = 只做选择：卡框边框变 {@code shop_card_select.png}（{@link #selectedIndex}），**不发任何包**；
 *       空 / 已售牌位点击一律无效；</li>
 *   <li><b>双击</b>（同一框 {@link #DOUBLE_CLICK_MS}=280ms 内第二次按下）= 快速移入物品栏：发
 *       {@link CardShopBuyPayload}(货架格, {@link CardShopMenu#QUICK_MOVE_SLOT}) ⇒ 服务端**先并同类未满栈、再**挑第一个空格并结算；</li>
 *   <li><b>拖拽</b>：按下后位移超过 {@link #DRAG_THRESHOLD} ⇒ 由 {@link #mouseDragged} 升级为拖拽（余额不足则拒绝起拖
 *       + actionbar 提示）→ {@link #mouseReleased} 判定落点：命中玩家物品栏空格 ⇒ 发 {@link CardShopBuyPayload}
 *       （服务端权威结算）；落点无效或该格被占用 ⇒ 仅清本地拖动态（卡牌原地移回，服务端全程无感知）。</li>
 * </ul>
 *
 * <p><b>二级「转账」视图</b> = 126x135 子面板，锚在主面板 (81,26)（2026-10-08 v4 定稿，
 * 权威口径 = temp/t81/UI-TRANSFER-DESIGN.md）：搜索框与金额框是**原版 {@link EditBox}**（输入即本地过滤 /
 * 纯数字 5..20），滚动条是**原版 {@link AbstractSliderButton} 子类**（{@link TransferScrollBar}）——
 * 三者都不进 {@code renderables}，一律在 {@link #renderTransferPanel} 内手工 render、在鼠标/键盘回调里手工转发
 * （原因：{@link AbstractContainerScreen#render} 先画 renderables，而本面板在 render 末尾画，进去会被面板底图盖住）。
 *
 * <p><b>下标口径</b>：界面上的「行号」是**搜索过滤后**的行号，而载荷要发**完整候选名单**的下标 ⇒ 二者之间由
 * {@link #transferFiltered}（过滤后行号 → 完整名单下标）与 {@link #transferOriginalIndex(int)} 做映射，
 * 发送前一律过一遍映射，**绝不下发过滤后的行号**。
 */
public class CardShopScreen extends AbstractContainerScreen<CardShopMenu> {

    // ── 主面板 ────────────────────────────────────────────────
    private static final int GUI_WIDTH = 288;
    private static final int GUI_HEIGHT = 187;

    private static final int FRAME_X0 = 7;
    private static final int FRAME_PITCH = 46;
    private static final int FRAME_Y = 28;
    private static final int FRAME_W = 44;
    private static final int FRAME_H = 61;
    /** 卡面安全区 38x38（框内偏移 +3,+3 ⇒ bg (10+46i,31)-(47+46i,68)） */
    private static final int FACE_SIZE = 38;
    private static final int FACE_X_OFF = 3;
    private static final int FACE_Y_OFF = 3;
    /**
     * 名称框可绘制区（相对卡框）：实测 {@code shop_card.png} 名称框填充区 x∈[2,41]（列 1/42 是边框内缘暗线），
     * 左右各内缩 1px ⇒ x∈[3,40] = **38px**（与设计稿「名称框 38x15 @ (3,44)-(40,58)」一致）：
     * 文本宽 ≤ 38 ⇒ 居中；&gt; 38 ⇒ 左右滚动（**禁止缩放/截断**，2026-10-09 用户裁决）。
     */
    private static final int NAME_BOX_X0 = 3;
    private static final int NAME_BOX_W = 38;
    /** 名称框纵向裁剪区（scissor 用）：实测填充行 y∈[43,58] ⇒ 高 16 */
    private static final int NAME_BOX_Y0 = 43;
    private static final int NAME_BOX_H = 16;
    /** 名称水平锚点 = 名称框内区中心：内区中心 21.5，取 22 传入可得到 21.5 的真实居中 */
    private static final int NAME_CENTER_X_OFF = 22;
    /**
     * 名称绘制顶边：名称框填充行 y∈[43,58]（垂直中心 50.5）；MC 文本墨迹行 = {@code y..y+6}
     * （{@code SheetGlyphInfo#getTop/getBottom}，ascent=7 / height=8）。
     * <p>任务Q §10（用户：「卡牌名称位置略微下移（视觉居中）」）：46 → **47** —— 墨迹中心 47+3 = 50，
     * 与内区中心 50.5 只差 0.5px（数学最优 47.5 不可达：字格步进 8px，y 只能取整，0.5px 即最小可达误差）。
     */
    private static final int NAME_Y_OFF = 47;
    private static final int PROMO_W = 17;
    private static final int PROMO_H = 14;

    private static final int EXCHANGE_X = 11;
    private static final int EXCHANGE_Y = 109;
    private static final int BUTTON_SIZE = 32;
    private static final int TRANSFER_X = 48;
    private static final int TRANSFER_Y = 109;
    private static final int HOURGLASS_X = 86;
    private static final int HOURGLASS_Y = 109;
    private static final int HOURGLASS_W = 20;
    private static final int HOURGLASS_H = 32;

    private static final int BALANCE_X = 11;
    private static final int BALANCE_Y = 145;
    private static final int BALANCE_W = 96;
    private static final int BALANCE_H = 24;
    /**
     * 余额数字锚点（**条坐标**）：钱包栏 {@code StarCoinWalletButtons#drawBalance} 的文字锚点是「按钮坐标」
     * {@code BALANCE_TEXT_RIGHT = 88} / {@code BALANCE_TEXT_DY = 7}，而钱包条画在
     * {@code getX()-BAR_INSET(2), getY()-BAR_INSET(2)} ⇒ 换算到**条坐标** = 右对齐 {@code 88+2=90}、
     * 顶边 {@code 7+2=9}。2026-10-09 修正：旧值 88/7 把「按钮坐标」直接套进条坐标 ⇒ 数字比钱包口径
     * 左 2px、上 2px（用户实报「星币余额数字与星币图标没有水平对齐」）。
     * <p>2026-10-09 二轮：底图换 {@code shop_wallet.png} 后**重新实测**（掩膜模板匹配 + 逐像素 alpha），
     * 结论＝**两个锚点均保持不变**：
     * <ul>
     *   <li>新素材(96x24)可写区（alpha==0）：左格 x∈[3,20]/y∈[3,20]、数字格 x∈[24,92]/y∈[3,20]；
     *       左格**自带**星币图标，ink x∈[5,18]/y∈[5,18]（14x14，上/下/左/右各留 2px），
     *       **ink 垂直中心 11.5**（＝条高 24 的中心）⇒ 不再单独绘制 {@code shop_coin_icon.png}；</li>
     *   <li>水平：锚点 90（条内坐标）⇒ 数字串 advance 右端 90；每枚数字实测 ink 宽 5 / advance 6
     *       （末位数字 ink 右端 = 90-2 = 88）⇒ 距数字格可写区右界 92 尚余 4px、距内阴影环(93) 5px，
     *       完全落在可写区内，
     *       且仍是钱包栏的原生口径（钱包栏同样 +90）；</li>
     *   <li>垂直：MC 默认字体 '0'..'9' 的字格 ink 行 = 0..6 ⇒ 屏幕墨迹行 = 绘制 y..y+6（7 行，**中心 y+3**）；
     *       取 {@code BALANCE_TEXT_DY=9} ⇒ 墨迹行 9..15（**中心 12**），与自带图标 ink 中心 11.5 差 0.5px
     *       —— 8px 字格下的最小粒度。**不取 8 的理由**：钱包栏自身的「文字中心 12 vs 图标中心 11.5」就是
     *       这 +0.5px，取 9 才与钱包栏**关系完全一致**（取 8 同样差 0.5px，却与钱包栏差 1px）。
     *       墨迹中心严格相等需 y=8.5，整数锚点在数学上不可达。</li>
     * </ul>
     */
    private static final int BALANCE_TEXT_RIGHT = 90;
    private static final int BALANCE_TEXT_DY = 9;

    private static final int GRID_X = 114;
    private static final int GRID_Y = 99;
    private static final int GRID_W = 166;
    private static final int GRID_H = 80;
    /** 物品格 pitch / 格内边长（槽位坐标一律由 CardShopMenu 的常量派生，保证「物品落在实测内格」） */
    private static final int GRID_PITCH = 18;
    private static final int CELL_SIZE = 16;

    private static final int TITLE_X = 7;
    private static final int TITLE_Y = 3;
    private static final int TITLE_W = 274;
    private static final int TITLE_H = 19;
    /** 标题文字锚点 = 整张面板中心 x=144（品红区中心 143.5，差 0.5px 可忽略） */
    private static final int TITLE_CENTER_X = GUI_WIDTH / 2;
    /** 标题文字顶边 = 品红区垂直中心 12(bg) 上移 4px ⇒ TITLE_Y+6 */
    private static final int TITLE_TEXT_Y = TITLE_Y + 6;
    /** 大酬宾流光换帧速率：8 tick/帧（用户口径「按照 8t 的速率循环播放」） */
    private static final int TITLE_FRAME_TICKS = 8;

    // ── 二级转账面板（v4 定稿；坐标为**面板内相对坐标**，面板原点 = (TP_X, TP_Y)） ──
    // 任务O §1.1（2026-10-09 用户裁决「转账界面2倍放大效果太差，回滚」）：2× 放大已**回滚**。
    //   · TP_SCALE 恒为 1：设计包围盒现为 **141x183**（任务P 换成加大版素材）⇒ 原点 TP_X=(288-141)/2=73、TP_Y=(187-183)/2=2，
    //     ⚠️ **不是**回滚时的 81/26 —— 凡写「面板内坐标」的调用点与探针都必须基于 73/2（绘制/控件/hit-test 尺寸则确实回到放大前）；
    //   · tpX()/tpY()/tpS()/tpHover() 一套换算**保留**（scale=1 时退化为恒等），全仓仍只有一套坐标口径；
    //   · 面板局部坐标系：pushPose → translate(leftPos+TP_X, topPos+TP_Y) → scale(TP_SCALE=1)，等价于直接平移。
    private static final int TP_SCALE = 1;
    /**
     * 面板设计包围盒（面板局部坐标 x∈[0,141)、y∈[0,183)）。
     * <p>任务P §2.2（用户裁决 2026-10-09「更新了新的 shop_transfer 贴图文件（背景、搜索条、滚动条、滚动条按钮），
     * 全部为加大版」）：底图 92x135 → <b>107x183</b>、搜索条 82x12 → <b>99x14</b>、滑轨 6x111 → <b>6x154</b>、
     * 滑块 12x16 → <b>12x17</b> ⇒ 包围盒 = 底图 107 宽 + 右侧按钮列 34 宽 = 141，高取底图 183。
     * <p>⚠️ 用户同时更正（BRIEF-P §0.1）：「按原有布局重新绘制控件位置，文本大小、玩家头像大小，
     * 旧控件大小和相对位置不变（保持原有布局效果）」⇒ 本次**只重排位置**：所有按钮/滚动条/头像/字号
     * 一律保持旧尺寸，只把坐标搬到新底板上并与底图内的凹槽对齐。
     */
    private static final int TP_PANEL_W = 141;
    private static final int TP_PANEL_H = 183;
    /** 面板原点（居中）：(288-141*1)/2 = 73；(187-183*1)/2 = 2 */
    private static final int TP_X = (GUI_WIDTH - TP_PANEL_W * TP_SCALE) / 2;
    private static final int TP_Y = (GUI_HEIGHT - TP_PANEL_H * TP_SCALE) / 2;
    /** 面板底图 shop_transfer_bg.png（任务P：92x135 → 107x183） */
    private static final int TP_BG_W = 107;
    private static final int TP_BG_H = 183;
    // 搜索框：素材自带放大镜 ⇒ 右端是「刷新名单」热区，左侧才是输入框本体（原版 EditBox）。
    // ⚠️ TP_SEARCH_FIELD_* / TP_SEARCH_REFRESH_X 都是**相对 TP_SEARCH_X/Y 的素材内偏移**，
    //    控件构造时必须写成 panelX + TP_SEARCH_X + TP_SEARCH_FIELD_X（漏掉素材原点会把框画到面板左上角）。
    // 任务P：新搜索条 99x14（旧 82x12）⇒ 底图宽 107 下 x = (107-99)/2 = 4 居中、y = 4；
    //   条内输入凹槽 = (1,1)-(97,12)，放大镜白像素实测 bbox = 条内 x88..95 ⇒ 输入框右界取条内 88。
    private static final int TP_SEARCH_X = 4;
    private static final int TP_SEARCH_Y = 4;
    private static final int TP_SEARCH_W = 99;
    private static final int TP_SEARCH_H = 14;
    private static final int TP_SEARCH_FIELD_X = 3;
    /** 输入框在新条内**垂直居中**（条高 14 − 框高 10 ⇒ 上边距 2；旧条同口径为 (12−10)/2 = 1） */
    private static final int TP_SEARCH_FIELD_Y = 2;
    /** 输入框宽度**保持旧值 65**（BRIEF-P §0.1：只重排位置、旧控件尺寸不变）；放大镜在条内 x88..95，
     *  中间留白由刷新区补上，输入文字仍从条内 x3 起。 */
    private static final int TP_SEARCH_FIELD_W = 65;
    private static final int TP_SEARCH_FIELD_H = 10;
    /** 放大镜热区左缘（**面板内坐标**）= 条内 86 ⇒ 4+86 = 90（放大镜白像素起于条内 x88，留 2px 余量） */
    private static final int TP_SEARCH_REFRESH_X = TP_SEARCH_X + 88;
    /** 右列按钮列 x：底图右缘 107 再留 2px 缝（旧版 92+2 = 94，同一构图口径） */
    private static final int TP_BACK_X = 109;
    /**
     * 任务Q §9（用户：「返回按钮没对齐输入框，要向下移动对齐」）：0 → **3**。
     * <p>同一水平基准 = 搜索条中心：搜索条 shop_transfer_search.png 占面板局部 y∈[TP_SEARCH_Y, +14)
     * = [4,18) ⇒ 中心 11；返回键高 16 ⇒ y = 11 − 8 = 3 时其中心恰为 11。绘制（renderTransferPanel）
     * 与命中判定（mouseClicked）同用本常量 ⇒ 画在哪就点在哪。
     */
    private static final int TP_BACK_Y = 3;
    private static final int TP_BACK_W = 16;
    private static final int TP_BACK_H = 16;
    private static final int TP_CONFIRM_X = 109;
    private static final int TP_CONFIRM_Y = 24;
    private static final int TP_CONFIRM_W = 32;
    private static final int TP_CONFIRM_H = 22;
    // 金额显示框：素材底图 32x14（任务P §2.4 起**只作只读显示**：金额输入框已按用户裁决整体删除，
    //   本矩形只用来画当前档位数字，不再注册任何 EditBox）
    private static final int TP_AMOUNT_X = 109;
    private static final int TP_AMOUNT_Y = 46;
    private static final int TP_AMOUNT_W = 32;
    private static final int TP_AMOUNT_H = 14;
    // 四档 5/10/15/20（尺寸 24x15、间距 15 均**不变**，只随底图宽度右移 15px）
    private static final int TP_LIMIT_X = 109;
    private static final int TP_LIMIT_Y0 = 60;
    private static final int TP_LIMIT_PITCH = 15;
    private static final int TP_LIMIT_W = 24;
    private static final int TP_LIMIT_H = 15;
    /**
     * 滚动条：滑轨素材只有 6px 宽 ⇒ **控件矩形放宽到 12px**（原版命中判定用的就是控件矩形，
     * 见 {@link TransferScrollBar}），贴图仍按「滑轨居中 6px、滑块 12x17」原位绘制。
     * <p>任务P：新滑轨 6x154（旧 6x111）⇒ x = 底图宽 107 − 6 − 5 = 96、y = 183 − 4 − 154 = 25，
     * 沿用旧版「滑轨距底图右缘 5px、距底边 4px」的构图关系。
     */
    private static final int TP_RAIL_X = 96;
    private static final int TP_RAIL_Y = 25;
    private static final int TP_RAIL_W = 6;
    private static final int TP_RAIL_H = 154;
    private static final int TP_SLIDE_X = 93;
    private static final int TP_SLIDE_W = 12;
    private static final int TP_SLIDE_H = 17;
    /**
     * 列表区：行高 18 **不变**，可见行数按新滑轨长度重算 ⇒ 154/18 = 8 行（旧 111/18 = 6 行）；
     * y 从 29 起（= 滑轨顶 25 + 4，沿用旧版「滑轨顶 20 / 行区顶 24」的 4px 关系），
     * 右界 93 = 滑块左缘（旧版 78 = 滑块左缘，同一构图口径）。
     */
    private static final int TP_ROW_X = 2;
    private static final int TP_ROW_RIGHT = 93;
    private static final int TP_ROW_Y0 = 29;
    private static final int TP_ROW_H = 18;
    private static final int TP_ROWS = 8;
    // 行内布局：头像 / 玩家名 / 星币标 / 余额 / 选中指示
    private static final int TP_FACE_X = 6;
    /**
     * 头像相对行顶的 Y 偏移（任务P §2.5 微调：1 → 0）。
     * <p>口径（沿用本项目既有「MC 文本墨迹 = 顶边..顶边+6，ascent=7/height=8」）：玩家名画在 rowY，
     * 墨迹中心 rowY+3.5；星币/余额画在 rowY+9，墨迹中心 rowY+12.5 ⇒ **两行文本整体的墨迹中心 = rowY+8**。
     * 头像 16px 高，要让其垂直中心落在 rowY+8 ⇒ 头像顶 = rowY+8-8 = **rowY+0**（旧值 1 会把头像中心压到
     * rowY+9，比两行文本的中心低 1px —— 即用户看到的「没有完全水平对齐」）。
     */
    private static final int TP_FACE_Y_OFF = 0;
    private static final int TP_FACE_SIZE = 16;
    private static final int TP_NAME_X = 24;
    private static final int TP_NAME_MAX_W = 42;
    private static final int TP_COIN_X = 24;
    /** 星币标 Y 偏移：与余额文本**顶边对齐**（增补二 §2 口径 coinY = balanceTextY + 0） */
    private static final int TP_COIN_Y_OFF = 9;
    private static final int TP_COIN_SIZE = 7;
    /** star_coin_small.png 是 13x13，取 (3,3) 处的 7x7 子区 */
    private static final int TP_COIN_U = 3;
    private static final int TP_COIN_SRC = 13;
    private static final int TP_BALANCE_X = 33;
    private static final int TP_BALANCE_Y_OFF = 9;
    /**
     * 选中指示（粉色箭头）= shop_transfer_select.png **原生 13x12、不缩放**，画在**玩家名那一行**上：
     * x = 66 固定（紧贴名称列右界 x24..66 ⇒ 永不与名字重叠），y = rowY + 1。
     * 2026-10-08 增补二 §1 覆盖旧口径（旧口径：画在金额行、用已删除的 shop_transfer_select_small.png）。
     */
    private static final int TP_SELECT_X = 66;
    private static final int TP_SELECT_Y_OFF = 1;
    private static final int TP_SELECT_W = 13;
    private static final int TP_SELECT_H = 12;
    /** 玩家名 marquee（增补 §1）：起始停 20 tick → 每 2 tick 移 1px → 到尾反向（纯视觉，不影响命中区） */
    private static final int TP_NAME_HOLD_TICKS = 20;
    private static final int TP_NAME_STEP_TICKS = 2;
    /** 悬停行的半透明灰底（设计文档 (170,170,170,α≈0x66)） */
    private static final int COLOR_ROW_HOVER = 0x66AAAAAA;
    /** 四档固定金额 */
    private static final int[] LIMITS = {5, 10, 15, 20};

    private static final int COLOR_GOLD = 0xFFFFD700;
    private static final int COLOR_GREEN = 0xFF55FF55;
    /** 拒绝信号（§4）闪烁用的红相颜色（与白字交替 ⇒ 红白闪） */
    private static final int COLOR_RED = 0xFFFF5555;
    private static final int COLOR_GRAY = 0xFFB0B0B0;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    private boolean transferView = false;
    /** 任务P §2.3 探针：本帧是否绘制了主视图（转账视图激活时必须为 false，供实机断言「无下层透出」） */
    private boolean lastMainViewPainted = true;
    /** 已选中的目标在**完整候选名单**中的下标（-1 = 未选中）——载荷发的就是这个下标 */
    private int transferSelectedOriginal = -1;
    /** 列表窗口顶端行号（口径 = **过滤后**的行号；映射见 {@link #transferOriginalIndex(int)}） */
    private int transferScroll = 0;
    /** 金额权威值（恒 5..20；金额框只是它的一个视图） */
    private int transferAmount = LIMITS[0];
    /** 过滤结果：元素 = 该行在**完整名单**中的下标（过滤后行号 → 完整名单下标） */
    private final List<Integer> transferFiltered = new ArrayList<>();
    /** 生成 transferFiltered 时的名单引用：**引用变了才重建**（服务端每次重发都是新 List） */
    private List<CardShopTransferListPayload.Entry> transferFilteredFrom = List.of();
    /** 搜索框（原版 EditBox；手工 render，不进 renderables） */
    private EditBox transferSearchBox;
    // 任务P §2.4（用户裁决）：金额**输入框**已整体删除 —— 金额只由 4 个档位按钮决定，
    //   面板上的金额框（shop_transfer_limit.png + 居中白字）从此是**只读显示**。
    /** 滚动条（原版 AbstractSliderButton 子类；滑轨 6px ⇒ 控件矩形 12px 宽） */
    private TransferScrollBar transferScrollBar;
    /**
     * 名称 marquee 的**共享相位**（卡名与转账面板玩家名共用）：{@code containerTick} 里无条件 +1，
     * 切换视图（{@link #openTransferView()}/{@link #closeTransferView()}）时归零 ⇒ 同一界面内的所有
     * 滚动共用同一时钟，且每次进入视图都从「停顿」相位起步。
     */
    private int nameMarqueeTick = 0;
    /** 确认转账按钮按下反馈剩余 tick（画 shop_transfer_export_push.png） */
    private int transferConfirmPressTicks = 0;
    /** 四档金额按钮按下反馈剩余 tick 与其下标（-1 = 无；画 shop_transfer_button_limit_{i+1}_1.png） */
    private int transferLimitPressTicks = 0;
    private int transferLimitPressIndex = -1;
    /** 左按钮（星光兑换）按下反馈剩余 tick */
    private int exchangePressTicks = 0;
    /** 中按钮（进入转账界面）按下反馈剩余 tick（不可用时也置位 ⇒ 画 _push_1） */
    private int transferPressTicks = 0;

    // ── 拖拽购买状态（纯客户端；服务端无感知，故「未拖到物品栏 ⇒ 卡牌移回」天然成立） ──
    /** 正在被拖动的货架格索引（-1 = 未拖动） */
    private int dragIndex = -1;
    /** 跟手幽灵中心（界面坐标系，未加 leftPos/topPos） */
    private double dragCenterX = 0.0D;
    private double dragCenterY = 0.0D;
    /** 双击判定窗口（毫秒；2026-10-09 §3 用户口径 250–300ms，取中值 **280ms**） */
    private static final long DOUBLE_CLICK_MS = 280L;
    /** 拖拽升格阈值（像素）：按下后位移超过它才算拖拽，否则只当「单击选中」 */
    private static final double DRAG_THRESHOLD = 4.0D;

    // ── 单击选中 / 双击快速移入（2026-10-09 §3 用户裁决；纯客户端交互态，服务端只收意图） ──
    /** 单击选中的卡框（-1 = 无）：边框画 shop_card_select.png；空 / 已售牌位点击不改变它 */
    private int selectedIndex = -1;
    /** 双击判定：上一次落在卡框上的按下（框索引 + 毫秒时刻；-1 = 无双击相位） */
    private int lastClickIndex = -1;
    private long lastClickMillis = 0L;
    /** 本次按下的卡框与按下点（-1 = 没按在卡框上）—— 位移超过阈值才升级成拖拽 */
    private int pressedIndex = -1;
    private double pressX = 0.0D;
    private double pressY = 0.0D;
    /** 本次按下是否已经就「余额不足」提示过一次（拖拽升格失败时只提示一次，避免逐帧刷屏） */
    private boolean dragGateWarned = false;

    // ── 任务N′ §2 探针：最近一帧**实际画到屏幕上**的底图/图标（仅供测试读取；赋值点就在渲染同一处） ──
    /** 货架底图种类（{@link #shelfTexKind} 的取值）：售罄 / 选中 / 悬停 / 普通 */
    private static final String SHELF_TEX_SOLDOUT = "soldout";
    private static final String SHELF_TEX_SELECT = "select";
    private static final String SHELF_TEX_CURSOR = "cursor";
    private static final String SHELF_TEX_NORMAL = "normal";
    /** 每个卡框最近一帧实际画的底图种类（见 {@link #renderShelf} 的赋值点）；null = 还没渲染过 */
    private final String[] shelfTexKind = new String[CardShopMenu.SHELF_SIZE];
    /** 最近一帧实际画的两个主视图图标贴图名（见 {@link #renderMainButtons} 的赋值点） */
    private String lastExchangeTex = "starlight_exchange.png";
    private String lastTransferTex = "shop_transfer_button.png";
    /**
     * 任务N′ §2 探针：最近一帧**真实悬停**命中的主按钮与卡框（-1 = 无）。
     * <p>hover 判定吃的是 {@code render} 传入的鼠标坐标 = **真实 OS 光标**（GLFW 换算），
     * 而点击吃的是事件坐标 ⇒ 只有读到这两个字段才能证明「外部把光标停在控件上」这件事真的发生了。
     */
    private boolean lastHoverExchange = false;
    private boolean lastHoverTransfer = false;
    // 任务O 第 6 轮诊断：本帧 render 收到的 GUI 空间鼠标坐标（真光标驱动的悬停取证必须能读到它，
    // 否则「游戏到底有没有收到这次移动」无法判定）。
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private int lastHoverCard = -1;
    /**
     * 任务O §1.2 探针：本次转账面板渲染**实际绘制**的说明性翻译键（{@code none} = 一个都没画）。
     * <p>只记录面板局部绘制的那几处 translatable（空名单提示 / 确认键文字），不含每行的玩家名与余额数字。
     * 期望：**不含** {@code gui.astral_dice.card_shop.cooldown}（任务M §7 已删的倒计时红字，任务O §1.2 复核）。
     */
    private final List<String> transferTextKeys = new ArrayList<>();
    /** 任务O §1.4 探针：星光兑换「画灰 ⇒ 点击早退、不发包」的次数（见 mouseClicked 的 canExchangeNow 分支）。 */
    private int exchangeRejectClicks = 0;

    // ── 任务Q §1/§2/§3 探针：转账视图「完全独立」与本帧文本落点的取证（赋值点全部与渲染同源）──
    /** 本帧 renderBg 是否画了商店底图（转账视图下必须为 false）。 */
    private boolean lastBgPainted = false;
    /** 本帧容器槽位物品的绘制 / 跳过次数（转账视图下 draw 必须为 0 且 skip > 0）。 */
    private int lastSlotDraws = 0;
    private int lastSlotSkips = 0;
    /** 本帧是否走了「允许画幽灵卡」的分支（转账视图下必须为 false）。 */
    private boolean lastGhostAllowed = false;
    /** 占位文案「搜索玩家名」本帧的居中锚点与文本宽（未画时 = -1 / 0）。 */
    private int lastPlaceholderX = -1;
    private int lastPlaceholderW = 0;
    /** 空名单提示本帧的居中锚点与文本宽（未画时 = -1 / 0）。 */
    private int lastEmptyX = -1;
    private int lastEmptyW = 0;

    /**
     * 任务M §8：界面**自己**跟踪的「左键仍按住」标志（mouseClicked(button=0) 置位、mouseReleased(button=0) 清除）。
     * <p>⚠️ 绝不能用 {@code minecraft.mouseHandler.isLeftPressed()} 代替：1.21.1 的 MouseHandler 只在
     * <b>没有屏幕</b>时才更新该字段（{@code MouseHandler.java:119} 的 {@code this.minecraft.screen == null} 条件），
     * 商店界面开着时它恒为 false ⇒ 任何基于它的「物理左键兜底」都会在真人拖拽的下一 tick 把 dragIndex 清掉。
     */
    private boolean leftDragHeld = false;

    /** 卡面素材存在性缓存（避免每帧对资源管理器做最多 7 次查询；界面重建时清空） */
    private final Map<ResourceLocation, Boolean> faceCache = new HashMap<>();

    public CardShopScreen(CardShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.titleLabelX = -10000;
        this.titleLabelY = -10000;
        this.inventoryLabelX = -10000;
        this.inventoryLabelY = -10000;
        CardShopClientState.clear();
    }

    @Override
    protected void init() {
        super.init();
        faceCache.clear();
        // 控件坐标依赖 leftPos/topPos ⇒ 必须在 super.init() 之后建
        buildTransferWidgets();
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "textures/gui/shop/" + name);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 任务Q §1/§3 探针：本帧「转账视图隔离」计数从底图这一步起算（renderBg 恰好跑在容器槽位循环之前）
        this.lastSlotDraws = 0;
        this.lastSlotSkips = 0;
        // 任务Q §3（用户口径「转账页面必须完全独立：完全隐藏商店页面（直接不渲染），只有点返回才重新显示商店」）：
        //   转账视图激活时**商店两张底图一张都不画**。上一版是靠面板自己的深灰底去盖住下层，用户实报
        //   「商店背景在这里纯累赘、叠加难看、图标透传依旧存在」⇒ 现在直接不画，屏幕上只剩面板自身。
        if (transferView) {
            this.lastBgPainted = false;
            return;
        }
        this.lastBgPainted = true;
        blit(guiGraphics, tex("card_shop_inventory_bg.png"), 0, 0, GUI_WIDTH, GUI_HEIGHT);
        // 物品格底纹（槽位坐标 = 实测内格起点，见 CardShopMenu.INVENTORY_X/Y/HOTBAR_Y）
        blit(guiGraphics, tex("shop_item_inventory.png"), GRID_X, GRID_Y, GRID_W, GRID_H);
    }

    /**
     * 任务Q §3 覆写：转账视图下**容器槽位物品一律不画**。
     * <p>原版 {@link AbstractContainerScreen#render} 的槽位循环跑在 renderLabels 之前、也就是本类 render 的
     * 面板绘制之前 ⇒ 面板底图只有 107px 宽，其右列 34px 与下方物品栏区域原本会把卡牌/物品图标透出来
     * （用户实报的「图标透传依旧存在」）。这里连物品栏一起跳过：转账面板是**完全独立**的一页。
     */
    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (transferView) {
            this.lastSlotSkips++;
            return;
        }
        this.lastSlotDraws++;
        super.renderSlot(guiGraphics, slot);
    }

    /** 任务Q §3 覆写：转账视图下**不画槽位悬停高亮**（否则半透明白块会浮在暗化后的世界上）。 */
    @Override
    protected void renderSlotHighlight(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (transferView) {
            return;
        }
        super.renderSlotHighlight(guiGraphics, slot, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 原版标签一律不画（标题与物品栏标签坐标已挪到屏幕外）
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (exchangePressTicks > 0) {
            exchangePressTicks--;
        }
        if (transferPressTicks > 0) {
            transferPressTicks--;
        }
        if (transferConfirmPressTicks > 0) {
            transferConfirmPressTicks--;
        }
        if (transferLimitPressTicks > 0) {
            transferLimitPressTicks--;
        }
        // 名称 marquee 共享相位：主视图（卡名）与二级视图（玩家名）都靠它推进（见 nameMarqueeTick）
        nameMarqueeTick++;
        // 拒绝信号（§4）倒计时：由服务端的 CardShopDenyPayload 置位 ⇒ 每 tick 递减，渲染层据此染红数字
        CardShopClientState.tickDeny();
        if (transferView) {
            // 服务端把「本轮 60 秒轮询进行中」置位后（DataSlot[8] 同步）自动退回商店主界面
            // （2026-10-09 §2 改写：买牌或转账成功都会开启本轮 ⇒ 二级视图不再停留在「已用」状态上）
            if (this.menu.isTransferUsedThisSession()) {
                closeTransferView();
            }
        }
        // 拖拽兜底（2026-10-09 任务M §8 **根因修复**）：原先这里读 minecraft.mouseHandler.isLeftPressed()，
        //   而屏幕开着时该字段恒为 false（见 leftDragHeld 字段注释）⇒ 真人按下后 mouseDragged 刚把 dragIndex
        //   立起来，本兜底**下一 tick 立刻清成 -1**（且 pressedIndex 已被置 -1，无法再次升格）⇒ 松手时一个购买
        //   意图都不发 = 用户报的「拖拽不生效」。现在只依据界面自己收到的真实左键事件（leftDragHeld）判断：
        //   ① 左键已抬起却没收到 mouseReleased（事件丢失 / 光标出窗）⇒ 本地解锁，卡牌移回；
        //   ② 窗口失去焦点 ⇒ 同样解锁（真人切窗时不该留着幽灵）。
        if (dragIndex >= 0 && !leftDragHeld) {
            dragIndex = -1;
        }
        if (dragIndex >= 0 && this.minecraft != null && !this.minecraft.isWindowActive()) {
            dragIndex = -1;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // 任务P §2.3（用户报「转账界面显示时，下层商店文本、卡牌以及某些控件层级太高，会浮现到转账界面上」）：
        //   根因 = 主视图（标题/卡框/余额条/主按钮）画在本方法上半段、容器槽位物品由 super.render 画，
        //   二者都在**面板之前**；而面板底图 shop_transfer_bg.png 只有 107px 宽 —— 面板右列 34px
        //   （面板内 x107..141）没有任何底图像素 ⇒ 下层内容从这条**透明缝**里透出来，视觉上就是
        //   「浮在转账界面之上」。
        //   处置（任务书 §2.3 方案①+②结合）：转账视图激活时**整块跳过主视图绘制**（最干净），
        //   同时 renderTransferPanel 先用不透明底色铺满整个包围盒（含右列）把 super.render 画下的
        //   槽位物品一并盖住 ⇒ 转账视图下不存在任何主视图/槽位内容可见。
        this.lastMainViewPainted = !transferView;
        if (!transferView) {
            renderTitle(guiGraphics);
            renderShelf(guiGraphics, mouseX, mouseY);
            renderBalance(guiGraphics);
            renderMainButtons(guiGraphics, mouseX, mouseY);
        } else {
            renderTransferPanel(guiGraphics, mouseX, mouseY);
        }
        // 幽灵卡牌画在最上层（在物品栏、余额条、按钮之上；tooltip 的 z 更高）
        // 任务Q §3：转账视图下不画幽灵卡（完全独立的一页；拖拽状态在打开面板的那次点击里就已清掉，
        //   这里把「不画」写死，堵住任何残留路径把卡面透到面板上）。
        this.lastGhostAllowed = !transferView;
        if (!transferView) {
            renderDragGhost(guiGraphics);
        }
        if (!transferView) {
            // 转账视图下也不再画容器槽位的悬停 tooltip（否则会浮到转账面板之上）
            this.renderTooltip(guiGraphics, mouseX, mouseY);
        }
        renderShopTooltips(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void removed() {
        super.removed();
        dragIndex = -1;
        leftDragHeld = false;
        CardShopClientState.clear();
    }

    // ────────────────────────────── 主视图绘制 ──────────────────────────────

    private void renderTitle(GuiGraphics guiGraphics) {
        if (this.menu.isPromotionActive()) {
            int frame = titleLightFrame() + 1;
            blit(guiGraphics, tex("shop_title_light_" + frame + ".png"), TITLE_X, TITLE_Y, TITLE_W, TITLE_H);
        } else {
            blit(guiGraphics, tex("shop_title.png"), TITLE_X, TITLE_Y, TITLE_W, TITLE_H);
        }
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.astral_dice.card_shop"),
                this.leftPos + TITLE_CENTER_X, this.topPos + TITLE_TEXT_Y, COLOR_WHITE);
    }

    /** 流光帧 = 全局 gameTick / 8 mod 3（全局刻 ⇒ 同服两人看到的配色一致）；播不播由 DataSlot[7] 决定 */
    private int titleLightFrame() {
        long tick = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L;
        return (int) ((tick / TITLE_FRAME_TICKS) % 3L);
    }

    private void renderShelf(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        this.lastHoverCard = -1; // 任务N′ §2 探针：每帧重算悬停卡框（售罄牌位不算悬停目标）
        for (int i = 0; i < CardShopMenu.SHELF_SIZE; i++) {
            int x = this.leftPos + FRAME_X0 + FRAME_PITCH * i;
            int y = this.topPos + FRAME_Y;
            ItemStack stack = this.menu.getSlot(i).getItem();
            // **槽内无卡一律用售罄底图**（2026-10-09 用户裁决）：已售出、正被拖动（卡已离开货架）、
            // 以及本次开店本就空着的槽，底框全部画 shop_card_soldout.png，且不叠卡面与名称；
            // 有卡才画底图（选中 / 悬停 / 普通三种，见下）。卡框恒为 6 个，不随上架数量增减。
            if (this.menu.isSold(i) || i == dragIndex || stack.isEmpty()) {
                blitAbs(guiGraphics, tex("shop_card_soldout.png"), x, y, FRAME_W, FRAME_H);
                this.shelfTexKind[i] = SHELF_TEX_SOLDOUT;
                continue;
            }
            // 任务N′ §2.1（2026-10-09 用户裁决）：「鼠标指向（悬停）」与「点击/选中」拆成两张底图，
            //   优先级 **选中 > 悬停 > 普通** ⇒ 已选中的卡框恒画 shop_card_select.png（点击/选中的
            //   视觉承诺不变），仅「未选中但鼠标指向」画 shop_card_cursor.png，两者都不满足画 shop_card.png。
            //   拖动中的跟手幽灵**不属于这里**（见 renderDragGhost）：它仍是原版 16×16 小图标。
            boolean hovered = isHoveringAbs(mouseX, mouseY, x, y, FRAME_W, FRAME_H);
            if (hovered) {
                this.lastHoverCard = i;
            }
            String cardTex;
            if (i == selectedIndex) {
                cardTex = "shop_card_select.png";
                this.shelfTexKind[i] = SHELF_TEX_SELECT;
            } else if (hovered) {
                cardTex = "shop_card_cursor.png";
                this.shelfTexKind[i] = SHELF_TEX_CURSOR;
            } else {
                cardTex = "shop_card.png";
                this.shelfTexKind[i] = SHELF_TEX_NORMAL;
            }
            blitAbs(guiGraphics, tex(cardTex), x, y, FRAME_W, FRAME_H);
            drawCardFace(guiGraphics, stack, x + FACE_X_OFF, y + FACE_Y_OFF);
            drawCardName(guiGraphics, stack, x, y);
            if (this.menu.isDiscounted(i)) {
                // 任务P §2.1（用户裁决 2026-10-09）：「经商被动图标」= 折扣角标，由**卡面右下角**改贴**卡面右上角**。
                //   原位置正好压住卡面自带的「费用点数」（卡面素材 64x64 的右下角画着红/蓝费用点，渲染时按
                //   FACE_SIZE/64.0F 缩到 38x38 ⇒ 17x14 的角标把费用点数完全盖住）⇒ 用户报「遮挡费用」。
                //   新位置只改 y（横向仍右对齐），角标贴卡面内区**顶边**，不再压住任何费用信息。
                blitAbs(guiGraphics, tex("shop_promotion.png"),
                        x + FACE_X_OFF + FACE_SIZE - PROMO_W, y + FACE_Y_OFF, PROMO_W, PROMO_H);
            }
        }
    }

    /**
     * 跟手幽灵：**原版 16×16 小图标**（与 vanilla 携带物同款画法：{@code renderItem(stack, mouseX-8, mouseY-8)}），
     * 不再画大卡框/卡面/卡名 —— 2026-10-09 用户裁决「把拖拽内容改为原版小图标」。
     * <p>拖拽全程为**纯客户端**行为：这里只画跟随光标的小图标，不触碰原版携带物({@code getCarried()})，
     * 也不向服务端发送任何「拖拽中」状态；松手时由 {@link #mouseReleased} 只判一次「落点是否在玩家物品栏格内」。
     */
    private void renderDragGhost(GuiGraphics guiGraphics) {
        if (dragIndex < 0 || dragIndex >= CardShopMenu.SHELF_SIZE) {
            return;
        }
        ItemStack stack = this.menu.getSlot(dragIndex).getItem();
        stack = displayStack(stack);
        if (stack.isEmpty()) {
            return;
        }
        int x = (int) Math.round(dragCenterX) - 8;
        int y = (int) Math.round(dragCenterY) - 8;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 300.0F);
        guiGraphics.renderItem(stack, x, y);
        guiGraphics.renderItemDecorations(this.font, stack, x, y);
        guiGraphics.pose().popPose();
    }

    /**
     * 「怪力乱神」佩戴时，货架上的路障**视觉上**显示为强化拒止（真实购买落地的仍是路障，转换由服务端 {@code CardShopMenu} 负责）。
     * <p>纯客户端只读：{@link LinglingSignManager#isEquipped} 只读 Curios **双端同步**缓存（同本类 {@code ParunanSignItem.isEquipped}
     * 的既有先例），不写任何状态、不伪造物品 ⇒ 拖拽 / 购买 / 扣费的既有链路完全不变。
     */
    private ItemStack displayStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(ModItems.EFFECT_CARD_BARRICADE.get())) return stack;
        if (this.minecraft == null || this.minecraft.player == null) return stack;
        if (!LinglingSignManager.isEquipped(this.minecraft.player)) return stack;
        return LinglingSignManager.displayAsRepulsion(stack);
    }

    /**
     * 卡面贴图：默认 {@code textures/gui/shop/card/(物品注册名).png}。
     * <p><b>护法立牌（{@code misaki_sign}）佩戴时换「减费版」卡面</b>：该立牌被动把「名刀·嘎呜切」的战斗费用
     * 从 4 点降到 3 点（服务端 {@code MisakiSignItem#getMeitoCost}），而卡面 64×64 素材右下角**画着费用点**，
     * 照原样显示会让玩家看到 4 点、与真实结算不符 ⇒ 佩戴时改画减费素材
     * {@code attack_card_meito_discount.png}（源素材 {@code images/shop/card+event/嘎呜切减费.png}）。
     * <p>纯客户端只读：{@link MisakiSignItem#hasMisakiEquipped} 只读 Curios **双端同步**缓存（同本类
     * {@code LinglingSignManager.isEquipped} 的既有先例），不写状态、不伪造物品 ⇒ 拖拽 / 购买 / 扣费链路不变。
     * 减费素材缺失（资源包覆盖等）时自动回退原卡面，不崩。
     */
    private ResourceLocation cardFaceTexture(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ResourceLocation base = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                "textures/gui/shop/card/" + id.getPath() + ".png");
        if (!stack.is(ModItems.ATTACK_CARD_MEITO.get())) {
            return base;
        }
        if (this.minecraft == null || this.minecraft.player == null
                || !MisakiSignItem.hasMisakiEquipped(this.minecraft.player)) {
            return base;
        }
        ResourceLocation discount = ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                "textures/gui/shop/card/" + id.getPath() + "_discount.png");
        return faceExists(discount) ? discount : base;
    }

    /** 卡面图标：astral_dice:textures/gui/shop/card/(物品注册名).png（64x64 整体等比缩放到 38x38） */
    private void drawCardFace(GuiGraphics guiGraphics, ItemStack stack, int x, int y) {
        stack = displayStack(stack);
        ResourceLocation face = cardFaceTexture(stack);
        if (!faceExists(face)) {
            // 缺素材 ⇒ 画占位（半透明底 + 问号），不崩
            guiGraphics.fill(x, y, x + FACE_SIZE, y + FACE_SIZE, 0x66000000);
            guiGraphics.drawCenteredString(this.font, "?", x + FACE_SIZE / 2, y + FACE_SIZE / 2 - 4, COLOR_GRAY);
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        float scale = FACE_SIZE / 64.0F;
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.blit(face, 0, 0, 0, 0.0F, 0.0F, 64, 64, 64, 64);
        guiGraphics.pose().popPose();
    }

    private boolean faceExists(ResourceLocation face) {
        return faceCache.computeIfAbsent(face, key ->
                this.minecraft != null && this.minecraft.getResourceManager().getResource(key).isPresent());
    }


    /**
     * 卡名绘制（2026-10-09 用户裁决：**禁止缩放字号**，宽度超过名称框即改为**左右滚动**）：
     * <ul>
       <li>文本宽 ≤ {@link #NAME_BOX_W}(38) ⇒ 水平居中于名称框内区中心（{@link #NAME_CENTER_X_OFF}），不滚动；</li>
       <li>文本宽 &gt; 38 ⇒ 左对齐到内区左界（{@link #NAME_BOX_X0}），再按 {@link #marqueeOffset(int)} 逐像素
     *       左移（相位与转账面板玩家名**共用** {@link #nameMarqueeTick}）⇒ 完整可见且**不缩字号**；</li>
       <li>滚动时用 scissor 裁到名称框内区 [X0, X0+W) × [Y0, Y0+H) ⇒ 文字**不会溢出到卡框边框外**；</li>
       <li>垂直锚点 {@link #NAME_Y_OFF}；文本样式对齐玩家物品栏余额栏（白字、**无阴影**）。</li>
     * </ul>
     * <p>scissor 用屏幕绝对坐标，而 {@code frameX}/{@code frameY} 传入时已是绝对坐标（货架与拖拽幽灵都如此）。
     */
    private void drawCardName(GuiGraphics guiGraphics, ItemStack stack, int frameX, int frameY) {
        stack = displayStack(stack);
        String name = cardDisplayName(stack);
        int textWidth = this.font.width(name);
        int overflow = textWidth - NAME_BOX_W;
        guiGraphics.enableScissor(frameX + NAME_BOX_X0, frameY + NAME_BOX_Y0,
                frameX + NAME_BOX_X0 + NAME_BOX_W, frameY + NAME_BOX_Y0 + NAME_BOX_H);
        int drawX = overflow > 0
                ? frameX + NAME_BOX_X0 - marqueeOffset(overflow)   // 超宽：左对齐 + 共享相位滚动
                : frameX + NAME_CENTER_X_OFF - textWidth / 2;      // 不超宽：居中（保持原样）
        guiGraphics.drawString(this.font, name, drawX, frameY + NAME_Y_OFF, COLOR_WHITE, false);
        guiGraphics.disableScissor();
    }

    /**
     * 卡名文案：优先取可选的商店专用短名 {@code item.<命名空间>.<注册名>.short}（有键才用，当前 lang 未提供）；
     * **否则直接返回完整名**（禁止截断/缩放 —— 2026-10-09 用户裁决；超宽由 {@link #drawCardName} 左右滚动解决）。
     */
    private String cardDisplayName(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String shortKey = "item." + id.getNamespace() + "." + id.getPath() + ".short";
        Language language = Language.getInstance();
        if (language.has(shortKey)) {
            String shortName = language.getOrDefault(shortKey);
            if (!shortName.isEmpty()) {
                return shortName;
            }
        }
        return stack.getHoverName().getString();
    }


    private void renderBalance(GuiGraphics guiGraphics) {
        // 余额钱包底图（96x24）：2026-10-09 换新素材 shop_wallet.png —— 条内填充透明（与主背景花纹逐像素
        // 重叠，与旧 shop_coin_bar.png 同口径），且**左格自带星币图标**，故不再单独 blit shop_coin_icon.png
        blit(guiGraphics, tex("shop_wallet.png"), BALANCE_X, BALANCE_Y, BALANCE_W, BALANCE_H);
        String text = String.valueOf(StarCoinWalletState.balance());
        // 与玩家物品栏余额栏同口径（换算到条坐标）：右对齐锚点 +90、top +9、白字、无阴影（StarCoinWalletButtons#drawBalance）
        // 2026-10-09 §4：服务端下发「余额不足 / 背包已满」被拒信号后，数字红白交替闪 3 次（5t 一相，共 30t）
        int color = CardShopClientState.denyBlinkRed() ? COLOR_RED : COLOR_WHITE;
        guiGraphics.drawString(this.font, text,
                this.leftPos + BALANCE_X + BALANCE_TEXT_RIGHT - this.font.width(text),
                this.topPos + BALANCE_Y + BALANCE_TEXT_DY, color, false);
    }

    /**
     * 当前选中（单击选中的卡框索引；-1 = 无）。**仅供测试探针读取**：真实交互一律走
     * {@link #mouseClicked} → {@link #onCardFramePressed}，本方法不改变任何状态。
     */
    public int selectedCardIndex() {
        return selectedIndex;
    }

    /** 当前是否正在拖拽（-1 = 否）；仅供测试探针读取 */
    public int draggingCardIndex() {
        return dragIndex;
    }

    /** 当前是否处于二级转账面板；仅供测试探针读取 */
    public boolean transferViewVisible() {
        return transferView;
    }

    /**
     * 任务P §2.3 探针：**最近一帧 render 是否绘制了主视图**（标题/货架/余额条/主按钮）。
     * <p>赋值点就在 {@link #render} 的分支上（= 「画出来的」），转账视图激活时必须为 {@code false}
     * ⇒ 与截图互为独立证据，供实机断言「转账面板下不存在主视图文本/卡牌/按钮」。
     */
    public boolean mainViewPainted() {
        return this.lastMainViewPainted;
    }

    /**
     * 任务Q §3 探针：转账视图「完全独立」的取证 —— 本帧 renderBg / 容器槽位 / 幽灵卡的真实绘制决策。
     * <p>期望：转账视图激活时 {@code bg=0,slotDraw=0,slotSkip>0,ghost=0}；回到商店主视图时
     * {@code bg=1,slotDraw>0,ghost=1}。返回串由渲染路径直接赋值，与截图互为独立证据。
     */
    public String transferIsolationProbe() {
        return "bg=" + (this.lastBgPainted ? 1 : 0)
                + ":slotDraw=" + this.lastSlotDraws
                + ":slotSkip=" + this.lastSlotSkips
                + ":ghost=" + (this.lastGhostAllowed ? 1 : 0);
    }

    /**
     * 任务Q §8/§9/§10 探针：居中/对齐类改动**画出来的**坐标（不是设计意图）——
     * 返回键纵向中心 vs 搜索条中心、占位与空名单提示的居中锚点+文本宽、列表行区左右界、卡名绘制顶边。
     */
    public String transferLayoutProbe() {
        return "backY=" + TP_BACK_Y
                + ":backCenterY=" + (TP_BACK_Y + TP_BACK_H / 2)
                + ":searchCenterY=" + (TP_SEARCH_Y + TP_SEARCH_H / 2)
                + ":phX=" + this.lastPlaceholderX
                + ":phW=" + this.lastPlaceholderW
                + ":emptyX=" + this.lastEmptyX
                + ":emptyW=" + this.lastEmptyW
                + ":rowL=" + TP_ROW_X
                + ":rowR=" + TP_ROW_RIGHT
                + ":nameY=" + NAME_Y_OFF;
    }

    /**
     * 任务P §2.1 探针：指定卡框最近一帧折扣角标（{@code shop_promotion.png}）的**角标左上角**相对该
     * 卡框的偏移（{@code x,y}；无折扣时返回 {@code none}）。
     * <p>与 {@link #renderShelf} 的绘制点**同源**（直接用同一组常量与同一段算式），供实机断言
     * 「角标已从卡面右下角移到右上角」：新位置 y 必须等于 {@link #FACE_Y_OFF}（旧位置是
     * {@code FACE_Y_OFF + FACE_SIZE - PROMO_H}）。
     */
    public String promotionOffset(int index) {
        if (index < 0 || index >= CardShopMenu.SHELF_SIZE || !this.menu.isDiscounted(index)) {
            return "none";
        }
        return (FACE_X_OFF + FACE_SIZE - PROMO_W) + "," + FACE_Y_OFF;
    }

    /** 当前中按钮是否可用（余额够 + 本轮轮询就绪）；仅供测试探针读取，与渲染同源 */
    public boolean transferReady() {
        return canOpenTransfer();
    }

    /**
     * 当前沙漏帧号（0 = 就绪并保持、4..1 = 倒序冷却）；仅供测试探针读取。
     * 与 {@link #renderMainButtons} 走同一个 {@link #hourglassFrameOf(int)} ⇒ 探针读到的就是画出来的。
     */
    public int hourglassFrame() {
        return hourglassFrameOf(this.menu.transferCooldownSeconds());
    }

    /**
     * 沙漏帧算法（任务N′ §2.2，2026-10-09 用户裁决：**倒序 4→1，到点后 0 并保持**）：
     * 60 秒均分 **4 份、每份 15 秒**，剩余 60..46 ⇒ 帧 4、45..31 ⇒ 3、30..16 ⇒ 2、15..1 ⇒ 1，
     * 到点（剩余 ≤ 0）⇒ 帧 0 = 就绪，并一直保持到下一轮开启。
     * <p>帧 0 是「就绪」而不是「冷却第一格」：素材家族里 0 号帧表示完整/就绪，故倒序到 1 之后归 0。
     */
    private static int hourglassFrameOf(int remainSeconds) {
        int frame = 0;
        if (remainSeconds > 0) {
            frame = Math.max(1, Math.min(4, (int) Math.ceil(remainSeconds / 15.0D)));
        }
        return frame;
    }

    /**
     * 指定卡框**最近一帧实际画到屏幕上**的底图种类：soldout / select / cursor / normal；仅供测试探针读取。
     * 与 {@link #renderShelf} 的赋值点同源 ⇒ 截图证据与探针读数必然一致（「画出来的」就是「读到的」）。
     */
    public String shelfTextureKind(int index) {
        if (index < 0 || index >= this.shelfTexKind.length || this.shelfTexKind[index] == null) {
            return SHELF_TEX_NORMAL;
        }
        return this.shelfTexKind[index];
    }

    /**
     * 最近一帧实际画的两个主视图图标贴图名 + 沙漏帧号；仅供测试探针读取。
     * 格式：exchange=xxx.png:transfer=yyy.png:hourglass=N（与 {@link #renderMainButtons} 同源）。
     */
    public String mainIconTextures() {
        return "exchange=" + this.lastExchangeTex
                + ":transfer=" + this.lastTransferTex
                + ":hourglass=" + hourglassFrame();
    }

    /**
     * 左按钮（星光兑换）当前是否可用（可支配星光 ≥ 2 **且**本轮 60 秒周期已到点）；
     * 仅供测试探针读取，与渲染/hit-test 的 {@link #canExchangeNow()} **同源**（任务O §1.4）。
     */
    public boolean exchangeReady() {
        return canExchangeNow();
    }

    /**
     * 最近一帧**真实悬停**命中的控件（1 = 命中 / 0 = 未命中）：{@code ex=..:tp=..:card=..}；仅供测试探针读取。
     * <p>来源是 {@link #renderShelf}/{@link #renderMainButtons} 的赋值点 ⇒ 与「画出来的悬停态贴图」同源；
     * 外部 OS 驱动脚本据此判定「真实光标已停到目标控件上」。
     */
    public String hoverState() {
        return "ex=" + (this.lastHoverExchange ? 1 : 0)
                + ":tp=" + (this.lastHoverTransfer ? 1 : 0)
                + ":card=" + this.lastHoverCard;
    }

    /**
     * 本帧 render 收到的 GUI 空间鼠标坐标（{@code mx,my}）；仅供测试探针读取。
     * <p>与 {@link #hoverState()} 同帧同源 ⇒ 两者对照即可判定「光标移动有没有被游戏侧收到」
     * （任务O 第 6 轮：真光标停在兑换键上却 ex=0 时，必须区分「游戏没收到」与「收到但不在键上」）。
     */
    public String mouseArgs() {
        return this.lastMouseX + "," + this.lastMouseY;
    }

    /**
     * 星光兑换的真实数值证据：{@code get=..:base=..:spendable=..:cd=..:ready=..}；仅供测试探针读取。
     * <p>{@code get/spendable} 均取完整当前星光（两者相等，与 {@link #canExchange()} 同源）；
     * {@code base} 仅为探针兼容字段：就绪路径恒 {@code 0}，未就绪沿用哨兵 {@code -1}。
     * <p>{@code cd} = 本轮剩余秒数（DataSlot[6]）、{@code ready} = {@link #canExchangeNow()}
     * （任务O §1.4 起含冷却）⇒ 机器行可自证「为何不可用」。
     */
    public String starlightState() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return "get=-1:base=-1:spendable=-1:cd=-1:ready=-1";
        }
        int get = StarLightManager.get(this.minecraft.player);
        return "get=" + get + ":base=0:spendable=" + get
                + ":cd=" + this.menu.transferCooldownSeconds()
                + ":ready=" + (canExchangeNow() ? 1 : 0);
    }

    /**
     * {@link #transferTextKeys} 的 CSV（{@code none} = 本帧转账面板没画任何说明性文本）；仅供测试探针读取。
     * <p>与渲染同源（赋值点就在空名单提示那处 drawCenteredString 旁）⇒ 探针读到的就是屏幕上画出来的。
     * <p>任务Q §1 起：确认键与四档按钮上的自绘文字已删 ⇒ 本表只可能含空名单提示键（或 none）。
     */
    public String transferTextKeysCsv() {
        return this.transferTextKeys.isEmpty() ? "none" : String.join(",", this.transferTextKeys);
    }

    /** {@link #exchangeRejectClicks}；仅供测试探针读取（证明画灰时点击在客户端就早退了，没有发包）。 */
    public int exchangeRejectClicks() {
        return this.exchangeRejectClicks;
    }

    /** 当前转账面板选中的候选**原下标**（-1 = 未选）；仅供测试探针读取，与渲染的选中箭头同源 */    public int transferSelectedIndex() {
        return transferSelectedOriginal;
    }

    /**
     * 当前焦点控件身份（none/search/amount/other:类名）；仅供测试探针读取。
     * 真实 EditBox 的矩形在屏幕坐标系里是「设计坐标 × TP_SCALE + 面板原点」的框（2× 已回滚 ⇒ 现为 1×），
     * 本方法让探针能证明
     * 「点在设计框里 ⇒ 焦点落在同一个控件上」，即真实控件与绘制用的是同一套换算。
     */
    public String transferFocusKind() {
        var focused = this.getFocused();
        if (focused == null) {
            return "none";
        }
        if (focused == this.transferSearchBox) {
            return "search";
        }
        // 任务P §2.4：金额框已删除 ⇒ 不再有 "amount" 焦点类型
        return "other:" + focused.getClass().getSimpleName();
    }

    /** 当前转账金额（设计档位值，5/10/15/20）；仅供测试探针读取，与金额框/确认按钮同源 */
    public int transferAmountValue() {
        return transferAmount;
    }

    /**
     * 面板几何自检（任务N §1.2）：把「面板局部设计坐标 → 屏幕坐标」的关键矩形一次性报给测试探针。
     * 探针侧用同一套公式（屏幕 = 面板原点 + 设计坐标 × TP_SCALE）独立复算后逐项比对 ⇒ 证明绘制
     * （pose 缩放）与命中判定/真实控件（tpX/tpY/tpS）用的是**同一个换算**，即「画在哪就点在哪」。
     * 仅供测试探针读取；返回格式 scale=1:panel=81,26:back=175,34:...（任务O §1.1：2× 回滚 ⇒ 原尺寸）
     */
    public String transferGeometry() {
        // mod 段 = **屏幕绝对坐标**（含 GUI 原点，供人读/报告）；rel 段 = **GUI 相对坐标**（与探针自算的
        // calc 段逐字符可比）⇒ 两侧独立复算后相等，才算证明「画/点同一套换算」。
        return transferGeometryAt(0, 0) + ":rel=" + transferGeometryAt(-this.leftPos, -this.topPos);
    }

    /** {@link #transferGeometry()} 的实现：把 tpX/tpY 的结果再平移 (ox, oy)。 */
    private String transferGeometryAt(int ox, int oy) {
        StringBuilder sb = new StringBuilder();
        sb.append("scale=").append(TP_SCALE);
        sb.append(":panel=").append(TP_X).append(',').append(TP_Y);
        sb.append(":back=").append(tpX(TP_BACK_X + TP_BACK_W / 2) + ox).append(',')
                .append(tpY(TP_BACK_Y + TP_BACK_H / 2) + oy);
        sb.append(":confirm=").append(tpX(TP_CONFIRM_X + TP_CONFIRM_W / 2) + ox).append(',')
                .append(tpY(TP_CONFIRM_Y + TP_CONFIRM_H / 2) + oy);
        sb.append(":limit0=").append(tpX(TP_LIMIT_X + TP_LIMIT_W / 2) + ox).append(',')
                .append(tpY(TP_LIMIT_Y0 + TP_LIMIT_H / 2) + oy);
        sb.append(":limit3=").append(tpX(TP_LIMIT_X + TP_LIMIT_W / 2) + ox).append(',')
                .append(tpY(TP_LIMIT_Y0 + 3 * TP_LIMIT_PITCH + TP_LIMIT_H / 2) + oy);
        sb.append(":row0=").append(tpX(TP_ROW_X + (TP_ROW_RIGHT - TP_ROW_X) / 2) + ox).append(',')
                .append(tpY(TP_ROW_Y0 + TP_ROW_H / 2) + oy);
        sb.append(":search=").append(tpX(TP_SEARCH_X + TP_SEARCH_FIELD_X + TP_SEARCH_FIELD_W / 2) + ox).append(',')
                .append(tpY(TP_SEARCH_Y + TP_SEARCH_FIELD_Y + TP_SEARCH_FIELD_H / 2) + oy);
        sb.append(":refresh=").append(tpX(TP_SEARCH_REFRESH_X) + ox).append(',')
                .append(tpY(TP_SEARCH_Y + TP_SEARCH_H / 2) + oy);
        // 任务P §2.4：金额输入框已删 ⇒ amount 段报的是**只读金额显示框**的中心（= 底图矩形中心）
        sb.append(":amount=").append(tpX(TP_AMOUNT_X + TP_AMOUNT_W / 2) + ox).append(',')
                .append(tpY(TP_AMOUNT_Y + TP_AMOUNT_H / 2) + oy);
        sb.append(":slide=").append(tpX(TP_SLIDE_X + TP_SLIDE_W / 2) + ox).append(',')
                .append(tpY(TP_RAIL_Y + TP_SLIDE_H / 2) + oy);
        sb.append(":bg=").append(TP_BG_W * TP_SCALE).append('x').append(TP_BG_H * TP_SCALE);
        return sb.toString();
    }

    /**
     * 中按钮可用条件（2026-10-09 §2 改写）= 星币 >= CardShopMenu.MIN_TRANSFER 且 **本轮 60 秒轮询已到点（就绪）**。
     * 「本轮进行中」与「剩余秒数 &gt; 0」是同一个服务端状态的两个视图（DataSlot[8] / DataSlot[6]），
     * 故不再需要单独的「本店会话位」判据。
     */
    private boolean canOpenTransfer() {
        return StarCoinWalletState.balance() >= CardShopMenu.MIN_TRANSFER
                && this.menu.transferCooldownSeconds() <= 0;
    }

    /**
     * 左按钮（星光兑换星币）可用条件 —— 任务N′ §2.4。
     * <p>与服务器 {@code CardShopMenu#tryExchange} → {@code ResourceConversion.starlightToStarCoins} 的
     * 拒绝口径**同源**：当前星光 ≥ {@code ResourceConversion.STARLIGHT_PER_COIN}(2) ⇒ 至少能换到 1 枚星币。
     * <p>两端判定必然一致：星光 attachment 已 sync 到客户端（见 {@code ModAttachments.PLAYER_STARLIGHT}）。
     * <p>玩家尚未就绪时**不**判不可用（返回 true）：那只是「还读不到数据」，不该把按钮画灰。
     */
    private boolean canExchange() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return true;
        }
        int spendable = StarLightManager.get(this.minecraft.player);
        return spendable >= ResourceConversion.STARLIGHT_PER_COIN;
    }

    /**
     * 左按钮（星光兑换）的整体可用性 —— 任务O §1.4：星光够换（{@link #canExchange()}）**且**
     * 本轮 60 秒周期已到点（与「进入转账」共用同一份服务端冷却，不引入第二个计时器）。
     * <p>任务O §1.2（2026-10-09 用户裁决「转账界面莫名其妙加了一堆文本…拒绝再胡乱加文本 HUD」）：
     * 本轮新增的 {@code showExchangeDisabledHint()} 已**整段删除** —— 按钮的灰态贴图本身已说明不可用，
     * 点击时不再弹任何 actionbar 说明文本。
     */
    private boolean canExchangeNow() {
        return canExchange() && this.menu.transferCooldownSeconds() <= 0;
    }

    private void renderMainButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean exchangeHover = isHovering(mouseX, mouseY, EXCHANGE_X, EXCHANGE_Y, BUTTON_SIZE, BUTTON_SIZE);
        boolean transferHover = isHovering(mouseX, mouseY, TRANSFER_X, TRANSFER_Y, BUTTON_SIZE, BUTTON_SIZE);
        this.lastHoverExchange = exchangeHover; // 任务N′ §2 探针：记录本帧真实悬停（供外部真光标驱动脚本回读）
        this.lastHoverTransfer = transferHover;
        // ══ 任务N §1.1（2026-10-09 用户报「转账和星光兑换按钮位置颠倒了」）══
        //   判定：**功能 / hit-test / tooltip / 冷却判定一律留在原地**（左 = 星光兑换星币、中 = 进入转账，
        //   与 temp/t80/UI-DESIGN.md v4 定稿、本类 Javadoc 一致），只把两个槽位的**贴图家族对调** ——
        //   原先左槽位（星光兑换）画的是 shop_transfer_button*（转账美术），中槽位（转账）画的是
        //   starlight_exchange*（星光兑换美术），用户按图标识别 ⇒ 他看到的正是「两个按钮换位了」。
        //   t80 的未决项 U1「两按钮共用同一张图」在新素材到位后变成「两按钮交叉用图」，本次对调即修复。
        // 左（11,109）= 星光兑换星币：可用 / 不可用 × 普通 / 按下 四种态。
        //   可用 = starlight_exchange(.push / 经商立牌 .push_2)，不可用 = starlight_exchange_unavailable(.push)。
        //   任务O §1.4：星光兑换**新增冷却**（与本轮 60 秒周期同源）⇒「不可用」现有两种原因
        //   （可支配星光 < 2 / 本轮进行中），两者共用同一套不可用贴图（素材已有，无需新增）。
        //   经商立牌专属按下态 starlight_exchange_push_2 属于**星光兑换家族**的美术，随家族一起留在左槽位。
        boolean parunan = this.minecraft != null && this.minecraft.player != null
                && ParunanSignItem.isEquipped(this.minecraft.player);
        boolean exchangePressed = exchangeHover && exchangePressTicks > 0;
        boolean exchangeReady = canExchangeNow();
        String exchangeTex;
        if (!exchangeReady) {
            // 任务N′ §2.4（2026-10-09 用户裁决）：**不可用态换图**（不再用灰调），按下态与 hit-test 同源。
            //   任务O §1.4 起「不可用」有两种原因，且都与服务端拒绝口径同源：
            //     ① 可支配星光 < ResourceConversion.STARLIGHT_PER_COIN(2)（换了也等于没换）；
            //     ② 本轮 60 秒周期进行中（CardShopMenu#tryExchange 已加同一份冷却闸门）。
            //   ⇒ 「画不可用」与「点了不发包」严格一致。
            exchangeTex = exchangePressed
                    ? "starlight_exchange_unavailable_push.png"
                    : "starlight_exchange_unavailable.png";
        } else {
            exchangeTex = exchangePressed
                    ? (parunan ? "starlight_exchange_push_2.png" : "starlight_exchange_push.png")
                    : "starlight_exchange.png";
        }
        this.lastExchangeTex = exchangeTex;
        blit(guiGraphics, tex(exchangeTex), EXCHANGE_X, EXCHANGE_Y, BUTTON_SIZE, BUTTON_SIZE);
        // 中（48,109）= 进入二级转账界面：可用 / 不可用 × 按下（任务N′ §2.3：不可用态换图，不再灰调）
        boolean ready = canOpenTransfer();
        boolean pressed = transferHover && transferPressTicks > 0;
        String transferTex = ready
                ? (pressed ? "shop_transfer_button_push.png" : "shop_transfer_button.png")
                : (pressed ? "shop_transfer_button_unavailable_push.png" : "shop_transfer_button_unavailable.png");
        this.lastTransferTex = transferTex;
        blit(guiGraphics, tex(transferTex), TRANSFER_X, TRANSFER_Y, BUTTON_SIZE, BUTTON_SIZE);

        // 沙漏（任务N′ §2.2，2026-10-09 用户裁决：**倒序 4→1→0**）：60 秒均分 4 份、每份 15 秒，
        //   剩余 60..46 ⇒ 帧 4、45..31 ⇒ 3、30..16 ⇒ 2、15..1 ⇒ 1，到点（剩余 ≤ 0）⇒ 帧 0 = 就绪；
        //   就绪后帧恒为 0 并**一直保持**，直到玩家再次购买/转账开启新一轮。数据源 = 服务端同步的剩余秒数。
        int remain = this.menu.transferCooldownSeconds();
        int frame = hourglassFrameOf(remain);
        blit(guiGraphics, tex("transfer_cooling_" + frame + ".png"),
                HOURGLASS_X, HOURGLASS_Y, HOURGLASS_W, HOURGLASS_H);
        // 任务M §7（2026-10-09 用户裁决）：**删除计时器数字** —— 沙漏只保留图形状态
        //   （倒序 4→1→0 与就绪保持），主视图不得再绘制任何倒计时数字/秒数文本。
        //   服务端同步的剩余秒数照旧（仍用于选帧、转账判定与提示文案）。
    }

    // ────────────────── 二级转账面板（v4 定稿；控件手工 render/转发） ──────────────────

    /**
     * 转账面板整体绘制。**绘制顺序 = 控件必须手工 render 的原因**：
     * {@link AbstractContainerScreen#render} 的顺序是「底图 → renderables（原版控件都在这）→ renderLabels →
     * 本类 render 的其余部分」，而本面板在主面板之后才画 ⇒ 控件若进 renderables 会被面板底图盖住。
     */
    private void renderTransferPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 名单可能刚被服务端重发（打开视图 / 点放大镜 / 转账落定）⇒ 先按最新名单重建过滤结果
        refreshTransferFilterIfNeeded();
        // 任务O §1.2 探针：本帧面板画的说明性翻译键从这里重新开始累计（见 transferTextKeysCsv）
        this.transferTextKeys.clear();
        List<CardShopTransferListPayload.Entry> candidates = CardShopClientState.transferCandidates();
        // ══ 面板绘制口径（任务N §1.2 建立；任务O §1.1 回滚 2× ⇒ 当前 1×）══
        //   下面整块面板在**面板局部设计坐标系**里绘制：一次 pushPose + translate(面板原点) + scale(TP_SCALE)
        //   ⇒ 全部坐标都是面板局部设计坐标（不再出现 leftPos/topPos + TP_X/TP_Y 的加法），贴图/fill/文本
        //   随 TP_SCALE 一起缩放（当前 TP_SCALE=1 ⇒ 原尺寸）；坐标常量（TP_*）保持**设计值不变**。
        //   ⚠️ 唯一例外：enableScissor **不吃 pose**（见 renderTransferRow）⇒ 那里用 tpX()/tpY() 换回屏幕坐标。
        // 任务Q §2（用户：「不要在右侧加深灰色底」）：任务P §2.3 铺的 0xFF242424 遮罩**整行删除** ——
        //   现在 renderBg 在转账视图下根本不画商店底图、renderSlot 也不画槽位物品（见 renderBg），
        //   底色再无用武之地，右侧那圈深灰底与底图主色 (36,36,36) 无关，属于纯累赘。
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float) (this.leftPos + TP_X), (float) (this.topPos + TP_Y), 0.0F);
        guiGraphics.pose().scale((float) TP_SCALE, (float) TP_SCALE, 1.0F);
        blitAbs(guiGraphics, tex("shop_transfer_bg.png"), 0, 0, TP_BG_W, TP_BG_H);
        // 搜索框 = 素材底图（右侧放大镜是刷新热区）+ 原版 EditBox 本体（无边框，只画文本与光标；
        //   控件本身在 popPose 之后统一单独绘制，见本方法末尾）
        blitAbs(guiGraphics, tex("shop_transfer_search.png"), TP_SEARCH_X, TP_SEARCH_Y, TP_SEARCH_W, TP_SEARCH_H);
        // 任务S §1（2026-10-10 用户裁决）：「搜索玩家名」占位必须与**输入框左对齐**——
        //   上一轮的「按可见搜索框中轴居中」被用户明确否定（「我从来没让你把文本和文本框居中，所以你还是改回去吧」）⇒ 恢复左对齐。
        //   锚点 = 输入文本自身的起点 TP_SEARCH_X + TP_SEARCH_FIELD_X = 4 + 3 = 7：
        //   EditBox 就建在这个点上（buildTransferWidgets 里 tpX(TP_SEARCH_X + TP_SEARCH_FIELD_X)），
        //   且 setBordered(false) ⇒ 文本原点即控件原点，占位与玩家输入的字符左端严格重合，输入后文字不跳动。
        //   原版 EditBox 自身的 hint 不会被画（本类从不调用 setHint，EditBox#renderWidget:459 只在 hint != null 时才画），
        //   所以这里自绘的是屏幕上唯一的一份占位文本。
        //   纵坐标保持 TP_SEARCH_Y + TP_SEARCH_FIELD_Y = 6 不动。
        //   用 drawString（5 参带阴影重载）绘制，与 EditBox 画文本的方式一致。
        if (this.transferSearchBox != null && this.transferSearchBox.getValue().isEmpty()) {
            Component hint = Component.translatable("gui.astral_dice.card_shop.transfer_search_hint");
            this.lastPlaceholderX = TP_SEARCH_X + TP_SEARCH_FIELD_X;
            this.lastPlaceholderW = this.font.width(hint);
            guiGraphics.drawString(this.font, hint, this.lastPlaceholderX,
                    TP_SEARCH_Y + TP_SEARCH_FIELD_Y, COLOR_WHITE);
        } else {
            this.lastPlaceholderX = -1;
            this.lastPlaceholderW = 0;
        }
        // 返回：只切视图，不发包。
        //   任务N §1.3（用户报「返回按钮多了一个 < 符号」）：原先这里额外 drawCenteredString("<")，而返回素材
        //   shop_transfer_button_back.png **自己就画着一个 `<`**（逐像素取证：亮色像素即 `<` 形）⇒ 屏幕上是两个 `<`。
        //   处置：整行删除；为不丢悬停反馈，改为悬停时叠一层半透明白高亮（原反馈只是把那个 `<` 变金色）。
        //   三语 lang 键（transfer_back = 返回/Back/戻る）本就干净、无需改动，全仓再无第二处 `<` 硬编码。
        boolean backHover = tpHover(mouseX, mouseY, TP_BACK_X, TP_BACK_Y, TP_BACK_W, TP_BACK_H);
        blitAbs(guiGraphics, tex("shop_transfer_button_back.png"), TP_BACK_X, TP_BACK_Y, TP_BACK_W, TP_BACK_H);
        if (backHover) {
            guiGraphics.fill(TP_BACK_X, TP_BACK_Y, TP_BACK_X + TP_BACK_W, TP_BACK_Y + TP_BACK_H, 0x33FFFFFF);
        }
        // 名单：空 ⇒ 列表区**居中**提示（整份名单为空 与 搜索无匹配 用不同键）
        if (transferFiltered.isEmpty()) {
            boolean noMatch = !candidates.isEmpty();
            Component empty = Component.translatable(noMatch
                    ? "gui.astral_dice.card_shop.transfer_no_match"
                    : "gui.astral_dice.card_shop.transfer_empty");
            this.transferTextKeys.add(noMatch
                    ? "gui.astral_dice.card_shop.transfer_no_match"
                    : "gui.astral_dice.card_shop.transfer_empty");
            // 任务Q §8：改锚到**列表行区中轴**（行区 = [TP_ROW_X, TP_ROW_RIGHT] ⇒ (2+93)/2 = 47）。
            //   原来锚在 TP_BG_W/2 = 53（= 底图中轴），可底图右半 34px 全是按钮列 ⇒ 视觉上明显偏右，
            //   也正是用户报的「不居中」。锚 47 后该文案恰好占满行区（宽 90 ⇒ 2..92）。
            this.lastEmptyX = (TP_ROW_X + TP_ROW_RIGHT) / 2;
            this.lastEmptyW = this.font.width(empty);
            guiGraphics.drawCenteredString(this.font, empty, this.lastEmptyX,
                    TP_ROW_Y0 + (TP_ROWS * TP_ROW_H) / 2 - 4, COLOR_GRAY);
        }
        for (int row = 0; row < TP_ROWS; row++) {
            int filteredRow = transferScroll + row;
            if (filteredRow >= transferFiltered.size()) {
                break;
            }
            int original = transferFiltered.get(filteredRow);
            renderTransferRow(guiGraphics, row, original, candidates.get(original), mouseX, mouseY);
        }
        // 滚动条（AbstractSliderButton 子类）与搜索框的渲染统一挪到本方法末尾（popPose 之后），
        //   原因见末尾注释：它们在屏幕坐标系里画，不能吃面板的缩放 pose（TP_SCALE）。
        // 确认转账：不可用（未选目标 / 金额 > 余额 / 冷却中）⇒ 压暗 + 灰字，点击只给 actionbar 说明
        boolean confirmable = canConfirmTransfer();
        boolean confirmHover = tpHover(mouseX, mouseY, TP_CONFIRM_X, TP_CONFIRM_Y, TP_CONFIRM_W, TP_CONFIRM_H);
        // 按下态素材 shop_transfer_export_push.png（增补 §3）：命中 + 刚点过 ⇒ 画按下图
        String confirmTex = confirmHover && transferConfirmPressTicks > 0
                ? "shop_transfer_export_push.png"
                : "shop_transfer_export.png";
        blitAbs(guiGraphics, tex(confirmTex), TP_CONFIRM_X, TP_CONFIRM_Y, TP_CONFIRM_W, TP_CONFIRM_H);
        if (!confirmable) {
            guiGraphics.fill(TP_CONFIRM_X, TP_CONFIRM_Y, TP_CONFIRM_X + TP_CONFIRM_W,
                    TP_CONFIRM_Y + TP_CONFIRM_H, 0x99000000);
        }
        // 任务Q §1（用户：「右侧按钮上不得出现文字 HUD」）：确认键上原来那行「转账」二字**整段删除**。
        //   素材 shop_transfer_export.png / shop_transfer_export_push.png 本身就是「金币 + 箭头」图标，
        //   里面一个字母都没有（×4 放大逐像素取证：temp/t94/montage_buttons.png）⇒ 按钮美术已足够。
        //   三语键 gui.astral_dice.card_shop.transfer_confirm 保留：它仍被转账视图的确认键 tooltip 使用。
        // 任务M §7：此处原先画「冷却 N 秒」红色倒计时文本，按用户裁决**整段删除**（转账面板同样只留图形/按钮态）。
        // 金额显示框（任务P §2.4：**只读**）—— 素材底图 + 居中的档位数字。
        //   用户裁决「直接删除金额输入功能，只保留 4 个按钮决定转账金额，同时将金额数字改回白色」：
        //   原来的 EditBox（setTextColor(COLOR_GOLD)）已整体删除 ⇒ 这里用 COLOR_WHITE 画权威值 transferAmount，
        //   数字即最终发出的数字，不存在「输入文本与权威值不一致」的可能。
        blitAbs(guiGraphics, tex("shop_transfer_limit.png"), TP_AMOUNT_X, TP_AMOUNT_Y, TP_AMOUNT_W, TP_AMOUNT_H);
        guiGraphics.drawCenteredString(this.font, String.valueOf(this.transferAmount),
                TP_AMOUNT_X + TP_AMOUNT_W / 2, TP_AMOUNT_Y + 3, COLOR_WHITE);
        // 四档 5/10/15/20：点击直接**决定**金额（当前档位金色；这 4 个按钮是唯一的金额来源）
        for (int i = 0; i < LIMITS.length; i++) {
            int y = TP_LIMIT_Y0 + i * TP_LIMIT_PITCH;
            boolean hover = tpHover(mouseX, mouseY, TP_LIMIT_X, y, TP_LIMIT_W, TP_LIMIT_H);
            // 按下态素材 shop_transfer_button_limit_{i+1}_1.png（增补 §3；_1 后缀 = 按下，**不是**禁用态）
            boolean pressed = hover && transferLimitPressTicks > 0 && transferLimitPressIndex == i;
            blitAbs(guiGraphics, tex("shop_transfer_button_limit_" + (i + 1) + (pressed ? "_1" : "") + ".png"),
                    TP_LIMIT_X, y, TP_LIMIT_W, TP_LIMIT_H);
            // 任务Q §1：档位数字也不再自绘 —— 四张素材 shop_transfer_button_limit_{1..4}.png **每张自带**
            //   数字 5/10/15/20（_1 后缀 = 同数字的按下态）⇒ 自绘数字是重复 HUD。当前档位仍可从右列
            //   金额显示框（只读白字，见上）读到。
        }
        guiGraphics.pose().popPose();
        // 两个原版控件在**屏幕坐标系**里绘制（各自绕自身原点按 TP_SCALE 缩放：控件矩形已在
        //   buildTransferWidgets 里按 tpX/tpY/tpS 换算成屏幕坐标，其内部文本/光标/贴图再缩放一次才对得上）。
        //   原先它们夹在面板绘制序列中间，现统一放到末尾 —— 两者都在各自的底图内，且与确认键(x≥109)、
        //   四档(x≥109)、列表(x≤93) 不重叠 ⇒ 层次与修前一致。
        //   任务P §2.4：金额 EditBox 已删除 ⇒ 这里只剩搜索框与滚动条两个控件。
        renderWidgetScaled(guiGraphics, transferSearchBox, mouseX, mouseY);
        if (transferScrollBar != null) {
            // 滚动条自身内部已带 TP_SCALE 缩放（见 TransferScrollBar#renderWidget）⇒ 这里不再套
            //   renderWidgetScaled，否则会放大 4 倍
            transferScrollBar.render(guiGraphics, mouseX, mouseY, 0.0F);
        }
    }

    /**
     * 名单单行（设计文档「行内布局」）：
     * 皮肤头像 16x16 @ (+6,+1)（脸 (8,8,8,8) + 帽子层 (40,8,8,8) 合成 — 即原版
     * {@link PlayerFaceRenderer#draw}）· 玩家名白色 @ (+24,+0)（宽 42px：**禁止缩小字号**，超宽时左右滚动
     * marquee，见 {@link #marqueeOffset(int)}）· 星币标 7x7 @ (+24,+9)（{@code star_coin_small.png} 的
     * (3,3) 子区，与余额文本顶边对齐）· 余额金色 @ (+33,+9)（千分位）。
     * 悬停行画半透明灰底 (2,+0)-(78,+18)；选中行画 {@code shop_transfer_select.png}(13x12) @ (+66,+1)
     * ——箭头指向**玩家名那一行**（增补二 §1）。
     *
     * @param originalIndex 该行在**完整名单**中的下标
     */
    private void renderTransferRow(GuiGraphics guiGraphics, int row, int originalIndex,
            CardShopTransferListPayload.Entry entry, int mouseX, int mouseY) {
        // 任务N §1.2：rowY 是**面板局部设计坐标**（调用方已 pushPose + scale(2) ⇒ 不再加 TP_Y）
        int rowY = TP_ROW_Y0 + row * TP_ROW_H;
        boolean selected = originalIndex == transferSelectedOriginal;
        if (!selected && tpHover(mouseX, mouseY, TP_ROW_X, rowY, TP_ROW_RIGHT - TP_ROW_X, TP_ROW_H)) {
            guiGraphics.fill(TP_ROW_X, rowY, TP_ROW_RIGHT, rowY + TP_ROW_H, COLOR_ROW_HOVER);
        }
        PlayerFaceRenderer.draw(guiGraphics, transferSkin(entry.id()),
                TP_FACE_X, rowY + TP_FACE_Y_OFF, TP_FACE_SIZE);
        // 玩家名：**不缩字号、不截断**，超 42px ⇒ 按共享相位左右滚动（marquee，增补 §1）；用 scissor
        // 裁到名称列（面板内 x 24..66）⇒ 文字不会溢出到箭头/金额列，命中区与点击不受影响
        Component name = Component.literal(entry.name());
        int nameWidth = this.font.width(name);
        int nameOffset = marqueeOffset(Math.max(0, nameWidth - TP_NAME_MAX_W));
        // ⚠️ enableScissor **不吃 pose**（走原始屏幕坐标）⇒ 这里必须用 tpX()/tpY() 换回屏幕坐标
        guiGraphics.enableScissor(tpX(TP_NAME_X), tpY(rowY),
                tpX(TP_NAME_X + TP_NAME_MAX_W), tpY(rowY + TP_ROW_H));
        guiGraphics.drawString(this.font, name, TP_NAME_X - nameOffset,
                rowY, COLOR_WHITE, false);
        guiGraphics.disableScissor();
        blitSubAbs(guiGraphics, tex("star_coin_small.png"), TP_COIN_X, rowY + TP_COIN_Y_OFF,
                TP_COIN_U, TP_COIN_U, TP_COIN_SIZE, TP_COIN_SIZE, TP_COIN_SRC);
        guiGraphics.drawString(this.font, String.format(Locale.ROOT, "%,d", entry.balance()),
                TP_BALANCE_X, rowY + TP_BALANCE_Y_OFF, COLOR_GOLD, false);
        if (selected) {
            // 选中指示 = shop_transfer_select.png **原生 13x12** @ (66, rowY+1)：指向玩家名那一行
            // （增补二 §1；已删除的 shop_transfer_select_small.png 与旧 16x14 回落逻辑一并作废）
            blitAbs(guiGraphics, tex("shop_transfer_select.png"), TP_SELECT_X, rowY + TP_SELECT_Y_OFF,
                    TP_SELECT_W, TP_SELECT_H);
        }
    }

    /**
     * 行头像用的皮肤：优先**真实皮肤**（{@code Minecraft#getConnection()#getPlayerInfo(uuid)#getSkin()}），
     * 离线 / 查无此人（PlayerInfo 为 null，例如刚进服的玩家）⇒ 原版 {@link DefaultPlayerSkin#get(UUID)}。
     */
    private PlayerSkin transferSkin(UUID id) {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            PlayerInfo info = this.minecraft.getConnection().getPlayerInfo(id);
            if (info != null) {
                return info.getSkin();
            }
        }
        return DefaultPlayerSkin.get(id);
    }

    // ────────────────────────────── 提示 ──────────────────────────────

    private void renderShopTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!transferView) {
            for (int i = 0; i < CardShopMenu.SHELF_SIZE; i++) {
                ItemStack stack = this.menu.getSlot(i).getItem();
                int x = this.leftPos + FRAME_X0 + FRAME_PITCH * i;
                int y = this.topPos + FRAME_Y;
                if (!isHoveringAbs(mouseX, mouseY, x, y, FRAME_W, FRAME_H)) {
                    continue;
                }
                if (this.menu.isSold(i)) {
                    // 任务Q §4（用户：「删掉『已售出』的 hover 提示，多此一举」）：原来这里弹
                    //   gui.astral_dice.card_shop.sold_out，现整段删除；已售出的卡框本来就已经换成
                    //   soldout 素材（见 shelfTextureKind），再弹一行文字纯属多余。
                    //   注意：这里必须直接 return —— 不能落到下面那条物品 tooltip 上
                    //   （已售出的槽位物品仍在，会显示出「售价」一行，反而更啰嗦）。
                    return;
                }
                if (!stack.isEmpty()) {
                    // 原有物品信息全保留，另补「折扣 / 售价 / 拖拽购买」三行
                    // （完整卡名 = 物品名，不受名称框截断影响）
                    stack = displayStack(stack);
                    List<Component> lines = new ArrayList<>(this.getTooltipFromContainerItem(stack));
                    boolean discounted = this.menu.isDiscounted(i);
                    if (discounted) {
                        lines.add(Component.translatable("gui.astral_dice.card_shop.discount")
                                .withStyle(ChatFormatting.GOLD));
                    }
                    lines.add(Component.translatable("gui.astral_dice.card_shop.price", this.menu.priceOf(i))
                            .withStyle(discounted ? ChatFormatting.GREEN : ChatFormatting.GOLD));
                    guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                }
                return;
            }
            if (isHovering(mouseX, mouseY, EXCHANGE_X, EXCHANGE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                guiGraphics.renderTooltip(this.font,
                        Component.translatable("gui.astral_dice.card_shop.exchange_hint"), mouseX, mouseY);
            } else if (isHovering(mouseX, mouseY, TRANSFER_X, TRANSFER_Y, BUTTON_SIZE, BUTTON_SIZE)) {
                guiGraphics.renderTooltip(this.font,
                        Component.translatable("gui.astral_dice.card_shop.transfer_hint"), mouseX, mouseY);
            }
            // BRIEF-P §0.2-1：沙漏是纯状态显示（不可点击）⇒ 其「解释行」tooltip 已删除。
            return;
        }
        // 任务N §1.2：面板内命中判定一律走 tpHover（= 面板局部设计坐标 ⇒ 屏幕矩形），
        //   与 mouseClicked/mouseScrolled 同源，绝不出现「画在这、点在那」。
        // 任务P §2.4 + BRIEF-P §0.2-1：金额框已是**只读显示**、不再是可交互控件 ⇒ 删掉它的
        //   「输入金额」提示（连同三语里已无引用的 transfer_amount_field_hint 键一并清理），
        //   只保留「按钮类」tooltip（下方搜索/返回/确认三个照旧）。
        if (tpHover(mouseX, mouseY, TP_SEARCH_X, TP_SEARCH_Y, TP_SEARCH_W, TP_SEARCH_H)) {
            // 搜索框素材右侧自带的放大镜 = 刷新名单热区；左侧才是搜索输入区
            boolean refreshZone = (mouseX - tpX(0)) >= (double) (TP_SEARCH_REFRESH_X * TP_SCALE);
            guiGraphics.renderTooltip(this.font, Component.translatable(refreshZone
                    ? "gui.astral_dice.card_shop.transfer_refresh"
                    : "gui.astral_dice.card_shop.transfer_search_hint"), mouseX, mouseY);
        } else if (tpHover(mouseX, mouseY, TP_BACK_X, TP_BACK_Y, TP_BACK_W, TP_BACK_H)) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.astral_dice.card_shop.transfer_back"), mouseX, mouseY);
        } else if (tpHover(mouseX, mouseY, TP_CONFIRM_X, TP_CONFIRM_Y, TP_CONFIRM_W, TP_CONFIRM_H)) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.astral_dice.card_shop.transfer_confirm_hint"), mouseX, mouseY);
        }
    }

    // ────────────────────────────── 输入 ──────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        final int mx = (int) mouseX;
        final int my = (int) mouseY;
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        leftDragHeld = true;      // 任务M §8：左键真实按下（真人鼠标与真实屏幕事件派发都走这里）
        if (transferView) {
            // ⚠️ 本类覆写了 mouseClicked/Dragged/Released/Scrolled ⇒ 默认的子控件派发**不会**发生，
            // 两个 EditBox 与滚动条都得在这里手工转发（keyPressed/charTyped 例外，见 keyPressed）。
            // 服务端包在两次渲染之间到达过 ⇒ 点击前先按最新名单对齐一次过滤/选中（保证行的下标一定有效）
            refreshTransferFilterIfNeeded();
            if (tpHover(mx, my, TP_BACK_X, TP_BACK_Y, TP_BACK_W, TP_BACK_H)) {
                closeTransferView();
                return true;
            }
            if (tpHover(mx, my, TP_SEARCH_X, TP_SEARCH_Y, TP_SEARCH_W, TP_SEARCH_H)) {
                if (mx >= tpX(TP_SEARCH_REFRESH_X)) {
                    this.sendButton(CardShopMenu.BUTTON_REFRESH_TRANSFER);
                } else if (transferSearchBox != null) {
                    this.setFocused(transferSearchBox);
                    transferSearchBox.mouseClicked(mouseX, mouseY, button);
                }
                return true;
            }
            // 任务P §2.4：金额框已删除 ⇒ 本矩形**不再是 hit-test 目标**（点击只读金额显示框 = 点空白，
            //   落到方法末尾的 blurTransferWidgets()，不改金额、不聚焦任何控件）。
            if (transferScrollBar != null && transferScrollBar.mouseClicked(mouseX, mouseY, button)) {
                // 滚动条不抢输入焦点（否则 EditBox 的光标会消失）
                this.setFocused(null);
                return true;
            }
            if (tpHover(mx, my, TP_CONFIRM_X, TP_CONFIRM_Y, TP_CONFIRM_W, TP_CONFIRM_H)) {
                transferConfirmPressTicks = 6;
                confirmTransfer();
                return true;
            }
            for (int i = 0; i < LIMITS.length; i++) {
                if (tpHover(mx, my, TP_LIMIT_X, TP_LIMIT_Y0 + i * TP_LIMIT_PITCH,
                        TP_LIMIT_W, TP_LIMIT_H)) {
                    setTransferAmount(LIMITS[i]);
                    transferLimitPressIndex = i;
                    transferLimitPressTicks = 6;
                    return true;
                }
            }
            for (int row = 0; row < TP_ROWS; row++) {
                int filteredRow = transferScroll + row;
                if (filteredRow >= transferFiltered.size()) {
                    break;
                }
                if (tpHover(mx, my, TP_ROW_X, TP_ROW_Y0 + row * TP_ROW_H,
                        TP_ROW_RIGHT - TP_ROW_X, TP_ROW_H)) {
                    transferSelectedOriginal = transferFiltered.get(filteredRow);
                    return true;
                }
            }
            // 点空白：收起输入焦点（金额框文本在此落定），不改选中项
            blurTransferWidgets();
            return true;
        }
        // 卡框优先：单击只选中、双击快速移入、按住拖动才升级为拖拽购买（2026-10-09 §3；
        // 按下瞬间**不**起拖，否则单击与拖拽无法区分）——全程不触发任何原版槽位交互。
        int cardIndex = cardFrameIndexAt(mx, my);
        if (cardIndex >= 0) {
            onCardFramePressed(cardIndex, mouseX, mouseY);
            return true;
        }
        if (isHovering(mx, my, EXCHANGE_X, EXCHANGE_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            exchangePressTicks = 6;
            if (!canExchangeNow()) {
                // 任务O §1.4 探针：记录「画灰时点击 ⇒ 客户端早退」的次数（服务端另有一道同样的闸门兜底）。
                this.exchangeRejectClicks++;
                // 任务N′ §2.4：**画不可用 = 点了不发包**（与中按钮同一口径）。
                //   任务O §1.2：这里**不再**弹任何说明文本（showExchangeDisabledHint 已删除），
                //   只保留「按下反馈 + 不发包」——按钮自身的不可用贴图已足够说明。
                //   任务O §1.4：冷却中同样走这一条（两种不可用原因共用同一处理，且都不发包）。
                return true;
            }
            this.sendButton(CardShopMenu.BUTTON_EXCHANGE);
            return true;
        }
        if (isHovering(mx, my, TRANSFER_X, TRANSFER_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            if (!canOpenTransfer()) {
                // 不可用：不进入转账视图，只给按压反馈 + actionbar 说明原因
                transferPressTicks = 6;
                showTransferDisabledHint();
                return true;
            }
            openTransferView();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 卡框按下（2026-10-09 §3 用户裁决）——**单击只做选择**，不在按下瞬间起拖：
     * <ul>
     *   <li>空 / 已售牌位：点击一律无效（连选中都不算）；</li>
     *   <li>同一框在 {@link #DOUBLE_CLICK_MS} 内被按下第二次 = <b>双击 ⇒ 快速移入物品栏</b>：
     *       发 {@link CardShopBuyPayload}(货架格, {@link CardShopMenu#QUICK_MOVE_SLOT})，由服务端先并同类未满栈、再挑第一个空格
     *       并走与拖拽完全相同的六步校验（余额不足 / 满包 ⇒ 服务端拒绝 + 余额数字闪烁）；</li>
     *   <li>否则 = 单击：记选中（边框变 shop_card_select.png）与按下点，等 {@link #mouseDragged} 决定是否升格为拖拽。</li>
     * </ul>
     */
    private void onCardFramePressed(int frame, double mouseX, double mouseY) {
        if (this.menu.isSold(frame) || this.menu.getSlot(frame).getItem().isEmpty()) {
            return;
        }
        dragGateWarned = false;
        long now = Util.getMillis();
        if (frame == lastClickIndex && now - lastClickMillis <= DOUBLE_CLICK_MS) {
            // 双击：快速移入（复位双击相位，避免三连击被当成第二组双击）
            lastClickIndex = -1;
            lastClickMillis = 0L;
            pressedIndex = -1;
            selectedIndex = frame;
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new CardShopBuyPayload(frame, CardShopMenu.QUICK_MOVE_SLOT));
            return;
        }
        lastClickIndex = frame;
        lastClickMillis = now;
        selectedIndex = frame;
        pressedIndex = frame;
        pressX = mouseX;
        pressY = mouseY;
    }

    /**
     * 拖拽升格门槛（UI-DESIGN 4.2.1 原口径不变）：该槽有货、未售出、且余额 &gt;= 售价；
     * 余额不足 ⇒ 拒绝起拖 + actionbar 提示（同一次按下只提示一次，避免逐帧刷屏）。
     */
    private void elevateToDrag(double mouseX, double mouseY) {
        int index = pressedIndex;
        if (index < 0 || this.menu.isSold(index) || this.menu.getSlot(index).getItem().isEmpty()) {
            return;
        }
        int price = this.menu.priceOf(index);
        if (StarCoinWalletState.balance() < price) {
            if (!dragGateWarned) {
                dragGateWarned = true;
                overlayMessage(Component.translatable("msg.astral_dice.card_shop_no_coin", price)
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        dragIndex = index;
        pressedIndex = -1;
        dragCenterX = mouseX;
        dragCenterY = mouseY;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (transferView && transferScrollBar != null && transferScrollBar.isHandleDragging()) {
            return transferScrollBar.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        // 单击 → 拖拽的升格判定（2026-10-09 §3）：位移超过阈值才起拖，否则保持「单击选中」语义
        if (button == 0 && !transferView && dragIndex < 0 && pressedIndex >= 0) {
            double dx = mouseX - pressX;
            double dy = mouseY - pressY;
            if (dx * dx + dy * dy >= DRAG_THRESHOLD * DRAG_THRESHOLD) {
                elevateToDrag(mouseX, mouseY);
            }
        }
        if (dragIndex >= 0 && button == 0) {
            dragCenterX = mouseX;
            dragCenterY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (transferView && transferScrollBar != null && transferScrollBar.isHandleDragging()) {
            return transferScrollBar.mouseReleased(mouseX, mouseY, button);
        }
        if (button == 0) {
            // 松开即结束本次「单击/双击/拖拽」手势（§3）：清按下态与一次性提示标记
            pressedIndex = -1;
            dragGateWarned = false;
            leftDragHeld = false;   // 任务M §8：左键真实抬起（与 mouseClicked 的置位成对）
        }
        if (button == 0 && dragIndex >= 0) {
            int index = dragIndex;
            dragIndex = -1;
            if (!transferView) {
                int invIndex = inventoryCellIndexAt((int) mouseX, (int) mouseY);
                if (invIndex >= 0) {
                    if (canAcceptDrop(index, invIndex)) {
                        // 合法落点（空 / 同类卡 / 背包有可并入的同类栈）：只发购买意图，扣款与发货由服务端 handleBuy 决定
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                new CardShopBuyPayload(index, invIndex));
                    } else {
                        overlayMessage(Component.translatable("msg.astral_dice.card_shop_slot_occupied")
                                .withStyle(ChatFormatting.RED));
                    }
                }
                // 落点不在玩家物品栏格内 ⇒ 什么都不发，卡牌原地移回（服务端全程无感知）
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 落点受理判定（2026-10-21 卡牌自动堆叠）：目标格为空 / 目标格是同类卡（含已满，服务端会另开新格）/
     * 背包里存在可并入的同类未满栈 ⇒ 允许发包；其余仍按「目标格被占用」拦下并弹既有文本。
     * 同类口径与服务端一致（同一物品 + 同一组件）；服务端仍是唯一权威，这里只放宽拦截。
     */
    private boolean canAcceptDrop(int shelfIndex, int invIndex) {
        // 2026-10-21 D1:可受理判定按**转换后**的物品比较(佩戴怪力乱神时 货架路障 ⇒ 强化拒止),
        // 与服务端 handleBuy 共用 LinglingSignManager#resolvePlacedItem —— 不复制第二份转换逻辑。
        ItemStack incoming = LinglingSignManager.resolvePlacedItem(
                this.menu.getSlot(shelfIndex).getItem(),
                this.minecraft == null ? null : this.minecraft.player);
        if (incoming.isEmpty()) {
            // 货架格已空（客户端视图滞后）⇒ 交给服务端幂等拒绝，这里不拦
            return true;
        }
        ItemStack target = this.menu.getSlot(CardShopMenu.SHELF_SIZE
                + CardShopMenu.playerSlotOffset(invIndex)).getItem();
        if (target.isEmpty() || sameCard(target, incoming)) {
            return true;
        }
        return hasMergeableStack(incoming);
    }

    /** 玩家物品栏里是否存在可并入 {@code incoming} 的同类未满栈（服务端「先并栈」落位的前置条件） */
    private boolean hasMergeableStack(ItemStack incoming) {
        for (int inv = 0; inv < 36; inv++) {
            ItemStack candidate = this.menu.getSlot(CardShopMenu.SHELF_SIZE
                    + CardShopMenu.playerSlotOffset(inv)).getItem();
            if (!candidate.isEmpty() && sameCard(candidate, incoming)
                    && candidate.getCount() < candidate.getItem().getMaxStackSize(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** 同类判定（1.21.1：同一物品 + 同一组件；卡牌按 CARD_USES 等组件区分栈） */
    private static boolean sameCard(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        final int mx = (int) mouseX;
        final int my = (int) mouseY;
        if (transferView) {
            // 金额框上滚轮 ⇒ 金额 ±1（夹在 5..20）；其余位置 ⇒ 列表逐行滚动（与原版滑条滚轮一致）
            if (tpHover(mx, my, TP_AMOUNT_X, TP_AMOUNT_Y, TP_AMOUNT_W, TP_AMOUNT_H)) {
                setTransferAmount(transferAmount + (verticalAmount > 0 ? 1 : -1));
                return true;
            }
            scrollTransferBy(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * 确认转账（设计文档 ⑨ 的**下标口径**）：这里发出的必须是选中项在**完整候选名单**中的下标，
     * 而不是界面上的过滤后行号 —— 二者由 {@link #transferOriginalIndex(int)} 转换。
     * 不可用时的点击全部转成 actionbar 说明（未选目标 / 冷却 / 余额不足）。
     */
    private void confirmTransfer() {
        // 任务P §2.4：金额输入框已删除 ⇒ 不再有「发送前与金额框对账」这一步 —— 金额权威值 transferAmount
        //   现在**只**由 4 个档位按钮写入（setTransferAmount），天然恒在 5..20 内，画出来的数字即发出的数字。
        int original = transferOriginalIndex(transferSelectedRow());
        if (original < 0) {
            overlayMessage(Component.translatable("gui.astral_dice.card_shop.transfer_select_hint")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (!canConfirmTransfer()) {
            if (this.menu.transferCooldownSeconds() <= 0 && transferAmount > StarCoinWalletState.balance()) {
                overlayMessage(Component.translatable("msg.astral_dice.card_shop_no_coin", transferAmount)
                        .withStyle(ChatFormatting.RED));
            } else {
                showTransferDisabledHint();
            }
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new CardShopTransferPayload(original, transferAmount));
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    /**
     * 中按钮不可用时的 actionbar 说明（按真实原因分档，避免误报「星币不足」）：
     * ①/② 本轮 60 秒周期进行中 —— 「剩余秒数 &gt; 0」与「本轮已用过」是同一服务端状态的两个视图，
     *        统一走既有的 {@code msg.astral_dice.card_shop_transfer_used}（任务O §1.2 已删掉本轮新增的文案键
     *        {@code gui.astral_dice.card_shop.cooldown_hint} 与其绘制/弹提示代码，不再多一句说明文本）；
     * ③ 否则报星币门槛（余额 &lt; 5）。
     */
    private void showTransferDisabledHint() {
        Component message;
        if (this.menu.transferCooldownSeconds() > 0 || this.menu.isTransferUsedThisSession()) {
            message = Component.translatable("msg.astral_dice.card_shop_transfer_used");
        } else {
            message = Component.translatable("msg.astral_dice.card_shop_no_coin", CardShopMenu.MIN_TRANSFER);
        }
        overlayMessage(message.copy().withStyle(ChatFormatting.RED));
    }

    private void overlayMessage(Component message) {
        if (this.minecraft != null && this.minecraft.gui != null) {
            this.minecraft.gui.setOverlayMessage(message, false);
        }
    }

    // ────────────────────────────── 坐标工具 ──────────────────────────────

    private boolean isHovering(int mouseX, int mouseY, int x, int y, int w, int h) {
        return isHoveringAbs(mouseX, mouseY, this.leftPos + x, this.topPos + y, w, h);
    }

    private boolean isHoveringAbs(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ── 二级转账面板的**唯一换算口径**（任务N §1.2 建立；任务O §1.1 回滚 2× ⇒ TP_SCALE=1 时退化为恒等） ──
    //   参数一律是**面板局部设计坐标**（= 与 TP_* 常量同一套数字，和绘制时 pose 里的坐标系一致）。
    //   绘制：pushPose + translate(leftPos+TP_X, topPos+TP_Y) + scale(TP_SCALE) ⇒ pose 内的设计坐标自动就位；
    //   控件与命中判定（不吃 pose）：屏幕坐标 = 面板原点 + 设计坐标 × TP_SCALE。两套口径只差这一个函数，
    //   因此不存在「画在这、点在那」。

    /** 面板局部设计坐标 x ⇒ 屏幕 x */
    private int tpX(int designX) {
        return this.leftPos + TP_X + designX * TP_SCALE;
    }

    /** 面板局部设计坐标 y ⇒ 屏幕 y */
    private int tpY(int designY) {
        return this.topPos + TP_Y + designY * TP_SCALE;
    }

    /** 设计尺寸 ⇒ 屏幕尺寸 */
    private int tpS(int designSize) {
        return designSize * TP_SCALE;
    }

    /** 面板内命中判定：鼠标（屏幕坐标）+ 设计坐标矩形 ⇒ 是否命中 */
    private boolean tpHover(double mouseX, double mouseY, int designX, int designY, int designW, int designH) {
        return isHoveringAbs((int) mouseX, (int) mouseY, tpX(designX), tpY(designY), tpS(designW), tpS(designH));
    }

    /**
     * 画一个**原版控件**（EditBox）：它自己的矩形已按 TP_SCALE 换算成屏幕坐标，但内部文字/光标/背景仍按 1× 画 ⇒
     * 这里绕控件左上角再按 TP_SCALE 缩放一次，文字与光标才和同比例的底图对齐（当前 TP_SCALE=1 ⇒ 恒等）。
     */
    private void renderWidgetScaled(GuiGraphics guiGraphics, AbstractWidget widget, int mouseX, int mouseY) {
        if (widget == null || !widget.visible) {
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float) widget.getX(), (float) widget.getY(), 0.0F);
        guiGraphics.pose().scale((float) TP_SCALE, (float) TP_SCALE, 1.0F);
        guiGraphics.pose().translate((float) (-widget.getX()), (float) (-widget.getY()), 0.0F);
        widget.render(guiGraphics, mouseX, mouseY, 0.0F);
        guiGraphics.pose().popPose();
    }

    /** 命中的卡框索引（-1 = 没命中）；判定区 = 整个卡框 44x61（UI-DESIGN U4 建议口径） */
    private int cardFrameIndexAt(int mouseX, int mouseY) {
        for (int i = 0; i < CardShopMenu.SHELF_SIZE; i++) {
            if (isHovering(mouseX, mouseY, FRAME_X0 + FRAME_PITCH * i, FRAME_Y, FRAME_W, FRAME_H)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 命中的玩家物品栏格 ⇒ 物品栏索引（0..8 快捷栏、9..35 主物品栏）；没命中返回 -1。
     * 格内矩形口径 = 槽位坐标的 16x16 内格（x = INVENTORY_X+18j；主物品栏 y = INVENTORY_Y+18r、
     * 快捷栏 y = INVENTORY_HOTBAR_Y），与 CardShopMenu 的槽位坐标严格同源。
     */
    private int inventoryCellIndexAt(int mouseX, int mouseY) {
        int guiX = mouseX - this.leftPos;
        int guiY = mouseY - this.topPos;
        for (int row = 0; row < 4; row++) {
            int cellY = row == 3
                    ? CardShopMenu.INVENTORY_HOTBAR_Y
                    : CardShopMenu.INVENTORY_Y + row * GRID_PITCH;
            if (guiY < cellY || guiY > cellY + CELL_SIZE - 1) {
                continue;
            }
            for (int col = 0; col < 9; col++) {
                int cellX = CardShopMenu.INVENTORY_X + col * GRID_PITCH;
                if (guiX >= cellX && guiX <= cellX + CELL_SIZE - 1) {
                    return row == 3 ? col : 9 + row * 9 + col;
                }
            }
        }
        return -1;
    }

    // ───────────────── 二级转账面板：控件构建 / 过滤 / 状态机 ─────────────────

    /**
     * 建三个**原版控件**（坐标依赖 leftPos/topPos ⇒ 只在 {@link #init()} 里调一次）：
     * 搜索框（{@link EditBox}）· 金额框（{@link EditBox}）· 滚动条（{@link TransferScrollBar}）。
     * 它们都**不进 renderables**（会被本面板底图盖住），改由 {@link #renderTransferPanel} 手工 render。
     */
    private void buildTransferWidgets() {
        // 任务N §1.2：三个原版控件的**屏幕坐标**一律由 tpX/tpY/tpS 换算（面板局部设计坐标 × TP_SCALE + 面板原点），
        //   尺寸同样 ×TP_SCALE；渲染时各自再绕自身原点放大一次（renderWidgetScaled / TransferScrollBar 内部）。
        //   鼠标事件依旧传原始屏幕坐标 ⇒ 原版命中判定天然与绘制对齐。
        // 搜索框：无边框（底图已给框），输入即本地过滤（setResponder 才是输入回调，setFilter 只是闸门）
        this.transferSearchBox = new EditBox(this.font, tpX(TP_SEARCH_X + TP_SEARCH_FIELD_X),
                tpY(TP_SEARCH_Y + TP_SEARCH_FIELD_Y),
                tpS(TP_SEARCH_FIELD_W), tpS(TP_SEARCH_FIELD_H),
                Component.translatable("gui.astral_dice.card_shop.transfer_search_hint"));
        this.transferSearchBox.setBordered(false);
        this.transferSearchBox.setMaxLength(16);
        // 任务Q §8：这里**不再 setHint** —— 原版 hint 恒左对齐（EditBox#renderWidget），居中的占位文案
        //   改由 renderTransferPanel 自绘（锚点 = 搜索输入区中轴 48）。三语键仍复用同一个
        // 任务S §1：这里**不再 setHint**（原版 hint 恒左对齐且只画在控件原点，本类改为自绘）——
        //   占位文案由 renderTransferPanel 自绘，锚点 = 输入文本原点 TP_SEARCH_X + TP_SEARCH_FIELD_X = 7。
        //   三语键仍复用同一个 gui.astral_dice.card_shop.transfer_search_hint。
        // 任务P §2.4（用户裁决）：「手动修改金额会因为限制范围导致无法正确输入价格，因此直接删除金额输入功能，
        //   只保留 4 个按钮决定转账金额」⇒ 金额 EditBox 整体删除（连同 TP_AMOUNT_FIELD_* 常量、焦点分支、
        //   输入回调与三处兜底）。金额权威值 transferAmount 只由四档按钮写入，面板上只画只读白字。
        // 滚动条：控件矩形 12x111（滑轨素材只有 6px 宽 ⇒ 命中区刻意放宽到 12px）；TP_SCALE=1 ⇒ 屏幕像素即 12x111
        this.transferScrollBar = new TransferScrollBar(this, tpX(TP_SLIDE_X), tpY(TP_RAIL_Y));
        setTransferAmount(this.transferAmount);
        this.transferSearchBox.setValue("");
        refreshTransferFilter();
        updateTransferWidgets();
    }

    /** 打开二级视图：只在本地切状态 + 请求服务端下发候选名单，不发转账包 */
    private void openTransferView() {
        this.transferView = true;
        // 增补二 §3：进入视图时**不预选任何行**（transferSelectedOriginal = -1 ⇒ 无箭头、确认不可用）
        this.transferSelectedOriginal = -1;
        this.transferScroll = 0;
        this.nameMarqueeTick = 0;
        this.setFocused(null);
        if (this.transferSearchBox != null) {
            this.transferSearchBox.setValue("");
            this.transferSearchBox.setFocused(false);
        }
        setTransferAmount(LIMITS[0]);
        refreshTransferFilter();
        updateTransferWidgets();
        this.sendButton(CardShopMenu.BUTTON_TRANSFER);
    }

    /** 返回商店主界面：只切视图（面板内不留任何服务端副作用） */
    private void closeTransferView() {
        this.transferView = false;
        // 回主视图：marquee 相位归零 ⇒ 卡名从「停顿」相位起步（与进入二级视图同口径）
        this.nameMarqueeTick = 0;
        blurTransferWidgets();
        updateTransferWidgets();
    }

    /** 控件的 visible/active 随 transferView 走；滚动条额外要求「确实有可滚动的行」 */
    private void updateTransferWidgets() {
        if (this.transferSearchBox == null || this.transferScrollBar == null) {
            return;
        }
        // 1.21.1 的 AbstractWidget 把 active/visible 暴露为 public 字段（没有 setter）⇒ 直接赋值
        this.transferSearchBox.visible = this.transferView;
        this.transferSearchBox.active = this.transferView;
        // 任务P §2.4：金额输入框已删除 ⇒ 不再同步它的 visible/active（只读显示无需控件）
        this.transferScrollBar.visible = this.transferView;
        this.transferScrollBar.active = this.transferView && transferMaxScroll() > 0;
        if (!this.transferView) {
            // 任务P §2.4：金额 EditBox 已删除 ⇒ 退出视图时只剩搜索框与滚动条需要收焦点
            this.transferSearchBox.setFocused(false);
            this.transferScrollBar.setFocused(false);
        }
        syncTransferScrollBar();
    }

    /** 名单被服务端重发时（每次都是新 List 实例）才重建过滤结果，避免每帧重复过滤 */
    private void refreshTransferFilterIfNeeded() {
        if (CardShopClientState.transferCandidates() != this.transferFilteredFrom) {
            refreshTransferFilter();
        }
    }

    /**
     * 本地过滤（大小写不敏感子串匹配玩家名），并重建 {@link #transferFiltered}
     * （过滤后行号 → **完整名单**下标）。过滤只影响可见窗口，不影响下发索引。
     * 选中项若被过滤掉 ⇒ 置为 -1（**取消「默认选中第一个」**，增补二 §3：只有玩家点了某一行，
     * 箭头才出现；列表空 / 搜索无匹配 ⇒ 无选中、确认按钮不可用）。
     */
    private void refreshTransferFilter() {
        List<CardShopTransferListPayload.Entry> candidates = CardShopClientState.transferCandidates();
        String query = this.transferSearchBox == null
                ? "" : this.transferSearchBox.getValue().trim().toLowerCase(Locale.ROOT);
        this.transferFiltered.clear();
        for (int i = 0; i < candidates.size(); i++) {
            if (query.isEmpty()
                    || candidates.get(i).name().toLowerCase(Locale.ROOT).contains(query)) {
                this.transferFiltered.add(i);
            }
        }
        this.transferFilteredFrom = candidates;
        if (this.transferSelectedOriginal >= 0
                && !this.transferFiltered.contains(this.transferSelectedOriginal)) {
            this.transferSelectedOriginal = -1;
            this.transferScroll = 0;
        }
        setTransferScroll(this.transferScroll);
        updateTransferWidgets();
    }

    /**
     * 名称 marquee 的横向滚动偏移（**卡名与转账面板玩家名共用**）：可绘制宽 W，超宽部分
     * overflow = 文本宽 - W（卡名 W = {@link #NAME_BOX_W}，玩家名 W = {@link #TP_NAME_MAX_W}）。
     * 轨迹 = 停 {@link #TP_NAME_HOLD_TICKS} tick → 每 {@link #TP_NAME_STEP_TICKS} tick 移 1px 到尾
     * → 再停同样 tick → 反向移回 → 循环；相位取 {@link #nameMarqueeTick}（**同一界面内所有滚动共享**，
     * 各自按自身 overflow 映射，故同时起步、不会各自错乱）。
     */
    private int marqueeOffset(int overflow) {
        if (overflow <= 0) {
            return 0;
        }
        int travel = overflow * TP_NAME_STEP_TICKS;
        int cycle = 2 * (TP_NAME_HOLD_TICKS + travel);
        int t = this.nameMarqueeTick % cycle;
        if (t < TP_NAME_HOLD_TICKS) {
            return 0;
        }
        t -= TP_NAME_HOLD_TICKS;
        if (t < travel) {
            return t / TP_NAME_STEP_TICKS;
        }
        t -= travel;
        if (t < TP_NAME_HOLD_TICKS) {
            return overflow;
        }
        t -= TP_NAME_HOLD_TICKS;
        return overflow - t / TP_NAME_STEP_TICKS;
    }

    /** 已选中项在**过滤后**列表中的行号（-1 = 未选中 / 已被过滤掉） */
    private int transferSelectedRow() {
        return this.transferSelectedOriginal < 0 ? -1 : this.transferFiltered.indexOf(this.transferSelectedOriginal);
    }

    /**
     * **过滤后行号 → 完整候选名单下标**（载荷口径的唯一转换点；越界/未选中 ⇒ -1）。
     * 这是本面板最容易错的一处：界面上看到的是过滤后行号，服务端要的是完整名单下标。
     */
    private int transferOriginalIndex(int filteredRow) {
        if (filteredRow < 0 || filteredRow >= this.transferFiltered.size()) {
            return -1;
        }
        return this.transferFiltered.get(filteredRow);
    }

    private int transferMaxScroll() {
        return Math.max(0, this.transferFiltered.size() - TP_ROWS);
    }

    private void setTransferScroll(int row) {
        this.transferScroll = Math.max(0, Math.min(transferMaxScroll(), row));
        syncTransferScrollBar();
    }

    private void scrollTransferBy(int delta) {
        setTransferScroll(this.transferScroll + delta);
    }

    private void syncTransferScrollBar() {
        if (this.transferScrollBar != null) {
            this.transferScrollBar.syncFromScreen(this.transferScroll, transferMaxScroll());
        }
    }

    /**
     * 写金额（四档按钮 / 滚轮 / 打开视图都走这里）：先夹到 5..20 再落库。
     * <p>任务P §2.4：金额输入框已删除 ⇒ 这里不再回写任何控件文本，transferAmount 就是唯一权威值，
     * 面板上的金额数字是它的**只读投影**（见 renderTransferPanel）。
     */
    private void setTransferAmount(int amount) {
        this.transferAmount = Math.max(CardShopMenu.MIN_TRANSFER, Math.min(CardShopMenu.MAX_TRANSFER, amount));
    }

    /** 收起输入焦点（任务P §2.4：金额框已删 ⇒ 只剩搜索框一个可聚焦控件） */
    private void blurTransferWidgets() {
        if (this.transferSearchBox != null) {
            this.transferSearchBox.setFocused(false);
        }
        this.setFocused(null);
    }

    /** 确认可用条件：已选中合法目标 且 金额 5..20 且 ≤ 持有余额 且 冷却结束（设计文档 ⑥） */
    private boolean canConfirmTransfer() {
        if (transferSelectedRow() < 0) {
            return false;
        }
        if (this.menu.transferCooldownSeconds() > 0) {
            return false;
        }
        return this.transferAmount >= CardShopMenu.MIN_TRANSFER
                && this.transferAmount <= CardShopMenu.MAX_TRANSFER
                && this.transferAmount <= StarCoinWalletState.balance();
    }

    /**
     * 键盘：一律交给 super（任务P §2.4：金额框及其「回车落定文本」分支已删除，只剩搜索框一个 EditBox）
     * ⇒ {@code Screen.keyPressed → ContainerEventHandler 默认}把按键派发给 {@link #getFocused()}，
     * EditBox 的退格/左右/Ctrl+A/C/V 与 ESC 关屏、Tab 焦点导航都保持原版行为。
     * （charTyped 同理：本类不覆写 ⇒ 默认派发给聚焦控件，输入不会被容器吞掉。）
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean textFocused = this.transferView
                && (this.transferSearchBox != null && this.transferSearchBox.isFocused());
        // 输入框聚焦时按键**只交给 EditBox**、不再冒泡：AbstractContainerScreen.keyPressed 会在 super 返回 false
        // 之后把同一个按键当容器热键处理 —— 于是输入 "e" 会直接关掉商店、输入 1..9 会给鼠标下的槽位换快捷栏、
        // 输入 Q 会丢物品。ESC(256) 与 Tab(258) 例外，保持原版「关屏 / 焦点导航」。charTyped 本类不覆写，
        // 走 ContainerEventHandler 默认派发给焦点控件，所以字符照常输入。
        if (textFocused && keyCode != 256 && keyCode != 258) {
            if (this.getFocused() != null) {
                this.getFocused().keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void blit(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int w, int h) {
        blitAbs(guiGraphics, texture, this.leftPos + x, this.topPos + y, w, h);
    }

    /** 贴图子区绘制（u/v/w/h + 源图边长 src；坐标为面板内相对坐标） */
    private void blitSub(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y,
            int u, int v, int w, int h, int src) {
        guiGraphics.blit(texture, this.leftPos + x, this.topPos + y, 0, (float) u, (float) v, w, h, src, src);
    }

    private static void blitAbs(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int w, int h) {
        guiGraphics.blit(texture, x, y, 0, 0.0F, 0.0F, w, h, w, h);
    }

    /** 贴图子区绘制的**绝对坐标**版本（不加 leftPos/topPos）：供已 pushPose 的面板局部坐标系使用 */
    private static void blitSubAbs(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y,
            int u, int v, int w, int h, int src) {
        guiGraphics.blit(texture, x, y, 0, (float) u, (float) v, w, h, src, src);
    }
    /**
     * 转账列表的滚动条 —— 用**原版 {@link AbstractSliderButton} 逻辑**（拖拽手柄 / 点轨道翻页 / 滚轮逐行），
     * 只把外观换成素材，并把命中区放宽：
     *
     * <ul>
     *   <li>滑轨素材 {@code shop_transfer_slide_rail.png} 只有 6px 宽，太细按不中 ⇒ **控件矩形做成 12x111**（任务N §1.2
     *       建立；TP_SCALE=1 ⇒ 屏幕像素同为 12x111，若日后调整比例本矩形随 tpS() 同步缩放）
     *       （原版命中判定用的就是控件矩形，见 {@link net.minecraft.client.gui.components.AbstractWidget#isMouseOver}），
     *       贴图仍按滑轨居中 6px 绘制。</li>
     *   <li>贴图是自有素材 ⇒ 整体覆写 {@link #renderWidget}，不用原版 slider sprite。</li>
     *   <li>原版 {@code setValue} 是 private ⇒ 子类只能直接写 {@link #value} 再自行
     *       {@link #applyValue()} / {@link #updateMessage()}（本类封装成 {@link #syncFromScreen}）。</li>
     *   <li>{@code CardShopScreen} 自己吞掉了鼠标事件 ⇒ 拖拽/释放由宿主界面在
     *       {@code mouseDragged/mouseReleased} 里转发，{@link #isHandleDragging()} 是转发开关。</li>
     * </ul>
     */
    private static final class TransferScrollBar extends AbstractSliderButton {

        /** 宿主界面：applyValue / 点轨道翻页都要回调它 */
        private final CardShopScreen screen;
        /** 可滚动的行数（= 过滤后名单条数 − TP_ROWS；0 ⇒ 不可滚动） */
        private int maxRow;
        /** 是否正拖着手柄（宿主据此转发拖动/释放事件） */
        private boolean handleDragging;

        private TransferScrollBar(CardShopScreen screen, int x, int y) {
            // 任务N §1.2：x/y 已是**屏幕坐标**（宿主用 tpX/tpY 算好），控件矩形尺寸同样 × TP_SCALE
            super(x, y, TP_SLIDE_W * TP_SCALE, TP_RAIL_H * TP_SCALE, Component.empty(), 0.0D);
            this.screen = screen;
        }

        /** 由宿主同步显示状态：直接写 value（绕开原版 setValue）⇒ 不会回调 applyValue，避免回环 */
        private void syncFromScreen(int row, int maxRow) {
            this.maxRow = maxRow;
            this.value = maxRow <= 0 ? 0.0D : Math.max(0.0D, Math.min(1.0D, row / (double) maxRow));
            this.active = maxRow > 0;
            this.visible = screen.transferView;
        }

        private boolean isHandleDragging() {
            return this.handleDragging;
        }

        @Override
        protected void updateMessage() {
            // 列表滚动条不显示数值
        }

        @Override
        protected void applyValue() {
            if (this.maxRow > 0) {
                this.screen.setTransferScroll((int) Math.round(this.value * this.maxRow));
            }
        }

        // AbstractSliderButton 在 1.21.1 把 renderWidget 提升为 public ⇒ 覆写也必须 public
        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) {
                return;
            }
            // 任务N §1.2：绕控件左上角缩放 TP_SCALE 倍，内部一律用**面板局部设计坐标**绘制
            //   （TP_* 常量保持设计值）⇒ 宿主**不要**再对本控件套 renderWidgetScaled（会变 4 倍）。
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate((float) this.getX(), (float) this.getY(), 0.0F);
            guiGraphics.pose().scale((float) TP_SCALE, (float) TP_SCALE, 1.0F);
            blitAbs(guiGraphics, tex("shop_transfer_slide_rail.png"),
                    (TP_SLIDE_W - TP_RAIL_W) / 2, 0, TP_RAIL_W, TP_RAIL_H);
            int travel = TP_RAIL_H - TP_SLIDE_H;
            blitAbs(guiGraphics, tex("shop_transfer_slide_button.png"), 0,
                    (int) Math.round(travel * this.value), TP_SLIDE_W, TP_SLIDE_H);
            guiGraphics.pose().popPose();
        }

        /** 按在手柄上 ⇒ 进入拖拽；按在轨道上 ⇒ 与 MC 默认滑条同款「按页翻」（一次一屏 = TP_ROWS 行） */
        @Override
        public void onClick(double mouseX, double mouseY) {
            // 手柄命中一律按**屏幕像素**算（控件矩形已按 TP_SCALE 换算，不能再用设计值）
            final int handleH = TP_SLIDE_H * TP_SCALE;
            int handleY = this.getY() + (int) Math.round((this.getHeight() - handleH) * this.value);
            if (mouseY >= handleY && mouseY < handleY + handleH) {
                this.handleDragging = true;
                return;
            }
            this.screen.scrollTransferBy(mouseY < handleY ? -TP_ROWS : TP_ROWS);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (!this.handleDragging) {
                return;
            }
            final double handleH = TP_SLIDE_H * (double) TP_SCALE;
            double ratio = (mouseY - (this.getY() + handleH / 2.0D)) / (this.getHeight() - handleH);
            this.value = Math.max(0.0D, Math.min(1.0D, ratio));
            applyValue();
            updateMessage();
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            this.handleDragging = false;
        }
    }
}

