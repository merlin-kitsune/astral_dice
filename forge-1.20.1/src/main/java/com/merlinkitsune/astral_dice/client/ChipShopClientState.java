package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.starenginelib.item.AstralRarities;
import com.merlinkitsune.starenginelib.item.Rarity;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

/**
 * 筹码商店的**客户端态占位**（本批只做界面骨架，业务逻辑待用户后续下达；判据正本
 * {@code docs/chip-shop-ui-spec.md} §6）。
 *
 * <p>与 {@link CardShopClientState} 同款：全静态、无实例、由屏幕类在**构造**与 **removed()** 两处
 * {@link #clear()}，并由 {@code ChipShopScreen#containerTick} 每 tick 调一次 {@link #tick()}。
 *
 * <p><b>本批没有任何数据来源</b>：{@link #setOffers}/{@link #setCost}/{@link #setRefreshes} 三个 setter
 * 目前的调用方**只有屏幕类自己的构造路径**（保持初值），逻辑批要接的是「服务端权威 → S2C 包 → 这三个 setter」
 * （见规范 §9 的输入清单：价格与折扣、刷新次数的来源与上限、是否限购、失败提示）。
 *
 * <p><b>品质 → 品质外框序号</b>：{@link #qualityFrameIndexOf(ItemStack)} 的返回值直接拼进贴图名
 * （1 ⇒ {@code chipshop_quality_1.png}、2 ⇒ {@code _2}、3 ⇒ {@code _3}；{@code 0} ⇒ 不画外框）。
 * 档位反查一律走库的 {@link AstralRarities#tierOf}，**不在本类里写 {@code ==} 链** —— 库的 {@code Rarity}
 * 是**扩展过的原版枚举常量**（RARE/EPIC/LEGENDARY/PINNACLE/BIZARRE），与原版自带的 COMMON/UNCOMMON/RARE/EPIC
 * **不是同一组常量**（{@code tierOf} 对原版自带的档位返回 null，见该方法的契约）。
 *
 * <p>线程口径：只在客户端渲染线程读写（屏幕生命周期内），故与 {@link CardShopClientState} 的
 * volatile 不同，这里用普通静态字段 + 数组整体替换（{@link #setOffers} 换新数组，不就地改旧数组）。
 *
 * <p><b>平台差异（forge-1.20.1）</b>：与 1.21.1 蓝本**逐行一致**，无需改写 —— 库的
 * {@code AstralRarities}/{@code Rarity}（{@code com.merlinkitsune.starenginelib.item}）在 forge 线同名同义
 * （对照本线 {@code shop/CardTradeFilter.java} 的 {@code AstralRarities.tierOf(...)} 用法）。
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
    }

    /** 屏幕每 tick 调用一次（本批只累加存活 tick 数；逻辑批可在此做超时/动画） */
    public static void tick() {
        ticks++;
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
     * 刷新次数读数（刷新按钮下方框内显示 {@code 剩余/总}）。
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

    private static ItemStack[] emptyOffers() {
        ItemStack[] arr = new ItemStack[OFFER_COUNT];
        Arrays.fill(arr, ItemStack.EMPTY);
        return arr;
    }
}
