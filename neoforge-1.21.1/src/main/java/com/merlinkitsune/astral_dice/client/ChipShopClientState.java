package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.starengine.item.AstralRarities;
import com.merlinkitsune.starengine.item.Rarity;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * 筹码商店的客户端态镜像（**逻辑批已完整接通**：候选/价格/刷新次数/品质码/拒绝红闪；判据正本
 * {@code docs/chip-shop-ui-spec.md} §6）。
 *
 * <p>与 {@link CardShopClientState} 同款：全静态、无实例、由屏幕类在**构造**与 **removed()** 两处
 * {@link #clear()}，并由 {@code ChipShopScreen#containerTick} 每 tick 调一次 {@link #tick()}。
 *
 * <p><b>数据来源（逻辑批已接通）</b>：{@link #setOffers}/{@link #setCost}/{@link #setRefreshes}/
 * {@link #setQualityCodes} 的唯一调用方是 {@code ChipShopScreen#containerTick}，它每 tick 从
 * {@code ChipShopMenu} 的**隐藏候选槽同步内容 + DataSlot 同步值**读出（服务端权威 → 原版菜单同步 →
 * 客户端镜像，**没有**为数量类读数新增 S2C 包）。失败提示走 S2C
 * {@code ChipShopDenyPayload} → {@link #notifyDeny(int)}（起 30t 红闪倒计时）。
 *
 * <p><b>品质 → 品质外框序号</b>：{@link #qualityFrameIndexOf(ItemStack)} 的返回值直接拼进贴图名
 * （1 ⇒ {@code chipshop_quality_1.png}、2 ⇒ {@code _2}、3 ⇒ {@code _3}；{@code 0} ⇒ 不画外框）。
 * 档位反查一律走库的 {@link AstralRarities#tierOf}，**不在本类里写 {@code ==} 链** —— 库的 {@code Rarity}
 * 是**扩展过的原版枚举常量**（RARE/EPIC/LEGENDARY/PINNACLE/BIZARRE），与原版自带的 COMMON/UNCOMMON/RARE/EPIC
 * **不是同一组常量**（{@code tierOf} 对原版自带的档位返回 null，见该方法的契约）。
 *
 * <p>线程口径：只在客户端渲染线程读写（屏幕生命周期内），故与 {@link CardShopClientState} 的
 * volatile 不同，这里用普通静态字段 + 数组整体替换（{@link #setOffers} 换新数组，不就地改旧数组）。
 */
public final class ChipShopClientState {

    /** 筹码货位数（条带 cell1..cell3，规范 §3） */
    public static final int OFFER_COUNT = 3;

    /** 品质外框序号：0 = 不画外框（空筹码） */
    public static final int QUALITY_FRAME_NONE = 0;
    /**
     * 未识别档位（{@code tierOf} 返回 null，即**非本模组档位**）的兜底外框序号。
     * 与 pinnacle / bizarre 走同一条兜底（规范 §6「pinnacle / bizarre 暂用 quality_3」，待用户裁决）。
     */
    public static final int QUALITY_FRAME_FALLBACK = 3;

    private static ItemStack[] offers = emptyOffers();
    private static int cost = 0;
    private static int remainingRefreshes = 0;
    private static int totalRefreshes = 0;
    private static int selectedIndex = -1;
    private static int ticks = 0;
    /** 三格品质码（服务端 DataSlot[4..6] 同步值；1/2/3 = rare/epic/legendary、0 = 空槽） */
    private static int[] qualityCodes = new int[OFFER_COUNT];

    /** 拒绝红闪总时长（tick）：3 次「红 5t / 白 5t」交替（与卡牌商店 {@code DENY_BLINK_TICKS} 同口径） */
    public static final int DENY_BLINK_TICKS = 30;
    /** 拒绝原因：无（初值 / 未被拒） */
    public static final int DENY_NONE = -1;
    /** 红闪剩余 tick（0 = 不在闪） */
    private static int denyTicks = 0;
    /** 最近一次拒绝原因（{@link #DENY_NONE} = 无） */
    private static int denyReason = DENY_NONE;

    private ChipShopClientState() {}

    // ────────────────────────────── 生命周期 ──────────────────────────────

    /** 复位成「刚开店」的初值（构造器与本界面 removed() 各调一次，避免下次打开闪光上一次的读数） */
    public static void clear() {
        offers = emptyOffers();
        cost = 0;
        remainingRefreshes = 0;
        totalRefreshes = 0;
        selectedIndex = -1;
        ticks = 0;
        qualityCodes = new int[OFFER_COUNT];
        denyTicks = 0;
        denyReason = DENY_NONE;
    }

    /** 屏幕每 tick 调用一次：累加存活 tick 数 + 推进拒绝红闪倒计时 */
    public static void tick() {
        ticks++;
        tickDeny();
    }

    // ────────────────────────────── 写入（逻辑批的接入点） ──────────────────────────────

    /**
     * 载入筹码列表：长度按 {@link #OFFER_COUNT} 截断、不足补空；{@code null} 元素与 {@code null} 数组
     * 一律按空筹码处理（=> 该格不画品质外框与图标）。
     */
    public static void setOffers(ItemStack[] newOffers) {
        ItemStack[] copy = emptyOffers();
        if (newOffers != null) {
            int n = Math.min(newOffers.length, OFFER_COUNT);
            for (int i = 0; i < n; i++) {
                copy[i] = newOffers[i] == null ? ItemStack.EMPTY : newOffers[i];
            }
        }
        offers = copy;
    }

    /** 花费（确认按钮下方框内的读数）；负数按 0 处理 */
    public static void setCost(int value) {
        cost = Math.max(0, value);
    }

    /**
     * 刷新次数读数（刷新按钮下方框内只显示 {@code 剩余} 次数；总次数不再显示）。
     * 两者都夹到 &ge; 0；{@code total &gt; 0} 时再把 {@code remaining} 夹到 &le; total，
     * 避免画出 {@code 7/5} 这种越界读数（{@code total == 0} = 总数未知，此时只保证非负）。
     */
    public static void setRefreshes(int remaining, int total) {
        totalRefreshes = Math.max(0, total);
        int rem = Math.max(0, remaining);
        remainingRefreshes = totalRefreshes > 0 ? Math.min(rem, totalRefreshes) : rem;
    }

    /** 选中筹码位（0..2）；越界一律记为「未选中」（-1），与规范 §6 的 selectedIndex 口径一致 */
    public static void select(int index) {
        selectedIndex = index >= 0 && index < OFFER_COUNT ? index : -1;
    }

    // ────────────────────────────── 读取 ──────────────────────────────

    /** 单个筹码位（越界返回 {@link ItemStack#EMPTY}） */
    public static ItemStack offer(int index) {
        return index >= 0 && index < OFFER_COUNT ? offers[index] : ItemStack.EMPTY;
    }

    /** 筹码位快照（**防御性拷贝**：调用方改不动本类的内部数组） */
    public static ItemStack[] offers() {
        return offers.clone();
    }

    /** 已上架筹码数 = **非空**筹码位数（0..{@link #OFFER_COUNT}；探针 {@code offers=<n>} 用的就是它） */
    public static int offerCount() {
        int n = 0;
        for (ItemStack stack : offers) {
            if (!stack.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public static int cost() {
        return cost;
    }

    public static int remainingRefreshes() {
        return remainingRefreshes;
    }

    public static int totalRefreshes() {
        return totalRefreshes;
    }

    /** 当前选中筹码位（0..2；-1 = 未选中） */
    public static int selectedIndex() {
        return selectedIndex;
    }

    /** 界面存活 tick 数（逻辑批做动画/超时用；本批只有累加） */
    public static int ticks() {
        return ticks;
    }

    // ────────────────────────────── 品质 → 品质外框序号 ──────────────────────────────

    /**
     * 筹码物品 → 品质外框序号（规范 §6：rare → 1、epic → 2、legendary → 3；pinnacle / bizarre 暂用 3）。
     *
     * @return 0 = 不画外框（空筹码 / null，规范 §4 第 4 步「无筹码则不画」）；1..3 = quality_1..quality_3
     */
    public static int qualityFrameIndexOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return QUALITY_FRAME_NONE;
        }
        return qualityFrameIndexOf(AstralRarities.tierOf(stack.getRarity()));
    }

    /**
     * 库档位 → 品质外框序号。{@code null}（= 非本模组档位，见 {@link AstralRarities#tierOf} 的契约）
     * 与 pinnacle / bizarre 同处理：**暂用 3**（规范 §6 的兜底口径，待用户裁决）。
     */
    public static int qualityFrameIndexOf(Rarity tier) {
        if (tier == Rarity.RARE) {
            return 1;
        }
        if (tier == Rarity.EPIC) {
            return 2;
        }
        // LEGENDARY / PINNACLE / BIZARRE / null（非本模组档位）
        return QUALITY_FRAME_FALLBACK;
    }

    // ────────────────────────────── 品质码（服务端同步值） ──────────────────────────────

    /**
     * 载入三格品质码（服务端 {@code ChipShopMenu} 的 DataSlot[4..6]）。
     * 长度不足按 0（空槽）补，越界的值夹到 0..3。
     */
    public static void setQualityCodes(int[] codes) {
        int[] copy = new int[OFFER_COUNT];
        if (codes != null) {
            int n = Math.min(codes.length, OFFER_COUNT);
            for (int i = 0; i < n; i++) {
                copy[i] = Math.max(0, Math.min(3, codes[i]));
            }
        }
        qualityCodes = copy;
    }

    /** 第 index 格品质码（0 = 空槽；越界同 0） */
    public static int qualityCode(int index) {
        return index >= 0 && index < OFFER_COUNT ? qualityCodes[index] : 0;
    }

    // ────────────────────────────── 拒绝红闪（S2C ChipShopDenyPayload） ──────────────────────────────

    /**
     * 服务端拒绝了本次请求（余额不足 / 刷新次数为 0）⇒ 起一段 {@value #DENY_BLINK_TICKS} tick 的红闪。
     *
     * <p>闪哪一处由 {@code ChipShopScreen} 按 {@link #denyReason()} 决定：余额不足闪**花费文本**、
     * 刷新次数为 0 闪 **N/N 文本**（计划表 §4）。
     * ⚠️ 2026-10-14（需求 2）：码 2（物品栏已满）**不再是购买路径的结果**（满包改为照常成交 + 装不下的筹码
     * 掉落）⇒ 本方法实际只会收到 0 / 1；屏幕侧的花费文本仍按 0/2 两码判定（码 2 为保留码，守门 G23 按字面校验）。
     */
    public static void notifyDeny(int reason) {
        denyReason = reason;
        denyTicks = DENY_BLINK_TICKS;
    }

    private static void tickDeny() {
        if (denyTicks > 0) {
            denyTicks--;
        }
    }

    /** 红闪剩余 tick（0 = 不在闪） */
    public static int denyTicks() {
        return denyTicks;
    }

    /** 最近一次拒绝原因（{@link #DENY_NONE} = 无） */
    public static int denyReason() {
        return denyReason;
    }

    /**
     * 本 tick 是否应画成**红字**：剩余 tick 内每 5t 一相、奇数相为红（3 次红/白交替）。
     * 与卡牌商店 {@code CardShopClientState#denyBlinkRed()} 的相位算法逐字一致。
     */
    public static boolean denyBlinkRed() {
        return denyTicks > 0 && ((denyTicks - 1) / 5) % 2 == 1;
    }

    private static ItemStack[] emptyOffers() {
        ItemStack[] arr = new ItemStack[OFFER_COUNT];
        Arrays.fill(arr, ItemStack.EMPTY);
        return arr;
    }
}
