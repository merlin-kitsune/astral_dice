package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 筹码商店的**玩家级存档**（判据正本 = {@code docs/chip-shop-logic-plan.md} §3）。
 *
 * <p><b>为什么要落盘</b>（用户裁决 Q9/R16）：菜单随界面关闭而销毁 —— 若候选只活在菜单字段里，
 * 「没买就退出再进店」就会换一批，而用户要求**未购买退出时候选逐字不变**。因此候选与全部
 * 运行态一律存进玩家附件 {@link ModAttachments#CHIP_SHOP_STATE}（随玩家存档持久化），
 * 菜单构造只做**只读载入**。
 *
 * <p><b>字段</b>：
 * <ul>
 *   <li>{@code offers} = 当前候选筹码 id 列表（长度 0..{@value ChipShopCatalog#OFFER_COUNT}）；
 *       <b>缺口只会出现在尾部</b>（抽不满时先缺第 3 格、再缺第 2 格）⇒ 尾部缺失即空槽，
 *       不需要额外的「空槽标记」；</li>
 *   <li>{@code lastPickCategory} = 上次**购买**筹码的类别（R10；空 ⇒ 无类别偏置）；</li>
 *   <li>{@code purchasedCount} = 累计购买次数 ⇒ 价格 {@link ChipShopCatalog#priceOf(int)}（永久持久化）；</li>
 *   <li>{@code refreshesLeft} = 剩余刷新次数（初值 {@value ChipShopCatalog#REFRESH_INITIAL}、
 *       上限 {@value ChipShopCatalog#REFRESH_MAX}）；**不再随时间恢复** —— 只有两条获取路径：
 *       ① 真实铁砧**取走**升星成品（{@code event/AnvilUpgradeHandler#onAnvilRepair} 调用
 *       {@link #withRefreshGranted()} 授予 1 次）；② 方块投入空白筹码（{@link #withBlankInsert}）。
 *       换装备 / 换骰子**不再**触发任何刷新，死亡也不重置（附件随死亡复制）；</li>
 *   <li>{@code regenAnchor} = 原「刷新恢复锚点」的**存档 / 冻结测试兼容字段**（世界 gameTime；
 *       <b>0 = 从未刷新过</b>）。2026-10-24 起**已无产品内的自动恢复消费者**（原时间驱动 ticker 已删除），
 *       写入点仅保留在 {@link ChipShopMenu#handleRefresh}，不再有任何读取方据此 +1；</li>
 *   <li>{@code cooldownUntil} = 购买后的**禁止使用**截止时刻（世界 gameTime）；<b>0 = 无冷却</b>；</li>
 *   <li>{@code lastBlankInsertAt} = 最近一次**空白筹码投入**的时刻（世界 gameTime）；<b>0 = 从未投入</b>；</li>
 *   <li>{@code rolledDiceKey} = 上次掷骰时的骰子签名（{@code <物品 id>|<星阶>}）。**仅作诊断留痕**：
 *       2026-10-24 起不再参与 {@link #needsReroll} 判定（旧行为「换骰/升星即作废批次」会让玩家
 *       靠换装备白嫖刷新），字段 / Codec / 写入一律保留以兼容旧存档与冻结测试资产。</li>
 * </ul>
 *
 * <p><b>重抽的唯一三个入口</b>（R16/R17/G19）：① {@link ChipShopOpener#ensureOffers} 的进店补抽
 * （仅在「无候选」「候选含已拥有项」或「候选含未知 id」时，免费）；② {@link ChipShopMenu#handleRefresh}；
 * ③ 购买成功分支。其余任何路径都**不得**掷骰。
 *
 * <p><b>Codec 容错</b>：全部字段 {@code optionalFieldOf} —— 旧存档 / 裁剪线缺少某个键时按默认值读入，
 * 不抛异常（与 {@link CardShopShelf} 同口径）。
 */
public record ChipShopState(List<String> offers, Optional<String> lastPickCategory, int purchasedCount,
                            int refreshesLeft, long regenAnchor, long cooldownUntil, long lastBlankInsertAt,
                            String rolledDiceKey) {

    /** 全新状态：无候选、无上次类别、0 次购买、{@value ChipShopCatalog#REFRESH_INITIAL} 次刷新、无锚点 */
    public static final ChipShopState EMPTY = new ChipShopState(
            List.of(), Optional.empty(), 0, ChipShopCatalog.REFRESH_INITIAL, 0L, 0L, 0L, "");

    public static final Codec<ChipShopState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("offers", List.of()).forGetter(ChipShopState::offers),
            Codec.STRING.optionalFieldOf("last_pick").forGetter(ChipShopState::lastPickCategory),
            Codec.INT.optionalFieldOf("purchased", 0).forGetter(ChipShopState::purchasedCount),
            Codec.INT.optionalFieldOf("refreshes", ChipShopCatalog.REFRESH_INITIAL)
                    .forGetter(ChipShopState::refreshesLeft),
            Codec.LONG.optionalFieldOf("regen_anchor", 0L).forGetter(ChipShopState::regenAnchor),
            Codec.LONG.optionalFieldOf("cooldown_until", 0L).forGetter(ChipShopState::cooldownUntil),
            Codec.LONG.optionalFieldOf("last_blank_insert", 0L).forGetter(ChipShopState::lastBlankInsertAt),
            Codec.STRING.optionalFieldOf("rolled_dice", "").forGetter(ChipShopState::rolledDiceKey)
    ).apply(instance, ChipShopState::new));

    /** 玩家存档读取（客户端读到的是一份「服务端同步值」——本模组不使用附件 sync，客户端不读它） */
    public static ChipShopState get(Player player) {
        return player.getData(ModAttachments.CHIP_SHOP_STATE.get());
    }

    public static void store(Player player, ChipShopState state) {
        player.setData(ModAttachments.CHIP_SHOP_STATE.get(), state);
    }

    /** 候选（解析成池内条目；未知 id 会被跳过 —— 对应「换过版本 / 条目被移除」的旧存档） */
    public List<ChipShopCatalog.Entry> entries() {
        List<ChipShopCatalog.Entry> list = new ArrayList<>(offers.size());
        for (String id : offers) {
            ChipShopCatalog.byId(id).ifPresent(list::add);
        }
        return list;
    }

    public Optional<ChipShopCatalog.Category> lastCategory() {
        return lastPickCategory.flatMap(ChipShopCatalog.Category::byKey);
    }

    /** 当前单价（三格同价；无任何折扣路径 —— Q13） */
    public int price() {
        return ChipShopCatalog.priceOf(purchasedCount);
    }

    /** 第 index 格候选（越界 / 空槽 ⇒ 空栈） */
    public ItemStack offerStack(int index) {
        List<ChipShopCatalog.Entry> list = entries();
        return index >= 0 && index < list.size() ? list.get(index).stack() : ItemStack.EMPTY;
    }

    /** 第 index 格的品质码（1/2/3；空槽 ⇒ 0） */
    public int offerTier(int index) {
        List<ChipShopCatalog.Entry> list = entries();
        return index >= 0 && index < list.size() ? list.get(index).tier() : 0;
    }

    /** 是否已抽过（有候选 ⇒ 已抽过；空列表 ⇒ 首次使用 / 已清空） */
    public boolean initialized() {
        return !offers.isEmpty();
    }

    /**
     * 是否需要在开店时**免费补抽**（Q15 + G19 的唯一判定）：
     * ① 从未抽过 / 候选为空；② 候选里含**已拥有**筹码（含「收藏后又买到同款」的场景）；
     * ③ 候选引用了未知筹码 id（旧存档 / 条目被移除）。
     *
     * <p>⚠️ 只按「候选本身」判断，**不因池不够造成的尾部空槽重抽** —— 否则池少时每次进店都会重掷，
     * 与「未购买退出候选不变」冲突。
     *
     * <p>⚠️ 2026-10-24 用户裁决：**已删除「骰子签名变化即重抽」的比对** —— 换装备 / 换骰子 / 升星
     * 都不得再免费刷新（旧比对让换一次骰子就等于白送一批候选）。{@code rolledDiceKey} 仅作诊断留痕。
     */
    public boolean needsReroll(Player player) {
        List<ChipShopCatalog.Entry> list = entries();
        if (list.isEmpty()) {
            return true;
        }
        if (list.size() != offers.size()) {
            return true;
        }
        for (ChipShopCatalog.Entry entry : list) {
            if (ChipShopRoller.isOwned(player, entry)) {
                return true;
            }
        }
        return false;
    }

    /** 用新掷出的候选替换当前候选（长度可变：池不够时尾部留空） */
    public ChipShopState withOffers(List<ChipShopCatalog.Entry> picked, String diceKey) {
        List<String> ids = new ArrayList<>(picked.size());
        for (ChipShopCatalog.Entry entry : picked) {
            ids.add(entry.id());
        }
        return new ChipShopState(List.copyOf(ids), lastPickCategory, purchasedCount, refreshesLeft, regenAnchor,
                cooldownUntil, lastBlankInsertAt, diceKey);
    }

    /** 购买成功：累计次数 +1、记录本次类别（R18）；候选由调用方紧接着重抽 */
    public ChipShopState withPurchased(ChipShopCatalog.Category category) {
        return new ChipShopState(offers,
                category == null ? lastPickCategory : Optional.of(category.key()),
                purchasedCount + 1, refreshesLeft, regenAnchor, cooldownUntil, lastBlankInsertAt, rolledDiceKey);
    }

    /** 刷新次数与锚点（首次成功刷新时由调用方把 0 锚点写成 now + REFRESH_REGEN_TICKS） */
    public ChipShopState withRefreshes(int left, long anchor) {
        return new ChipShopState(offers, lastPickCategory, purchasedCount,
                Math.max(0, Math.min(ChipShopCatalog.REFRESH_MAX, left)), anchor, cooldownUntil, lastBlankInsertAt, rolledDiceKey);
    }

    /**
     * 授予 **1 次**刷新（2026-10-24 用户裁决：真实铁砧取走升星成品才发券）。
     *
     * <p>语义 = 存档现值 {@code refreshesLeft} **+1** 并夹在 {@code 0..REFRESH_MAX}，
     * 其余 record 字段原样保留 —— **不是**重置、也不回填 {@code REFRESH_INITIAL}，
     * 因此已有存档次数（含旧版本攒下的 >初值 次数）不会被清掉。
     *
     * <p>唯一调用点 = {@code event/AnvilUpgradeHandler#onAnvilRepair}（取走分支）；
     * 预览（{@code AnvilUpdateEvent}）/ 装备查询 / 抽取器一律不得调用。
     */
    public ChipShopState withRefreshGranted() {
        return new ChipShopState(offers, lastPickCategory, purchasedCount,
                Math.max(0, Math.min(ChipShopCatalog.REFRESH_MAX, refreshesLeft + 1)), regenAnchor, cooldownUntil,
                lastBlankInsertAt, rolledDiceKey);
    }

    /**
     * 写购买冷却截止时刻（世界 gameTime；<b>0 = 无冷却</b>）。
     *
     * <p>购买成功后由 {@link ChipShopMenu#handleBuy} 置为 {@code now + BUY_COOLDOWN_TICKS}（5:00）——
     * 冷却期内 {@link ChipShopOpener#open} 直接拒绝开店（需求 4）。
     */
    public ChipShopState withCooldown(long until) {
        return new ChipShopState(offers, lastPickCategory, purchasedCount, refreshesLeft, regenAnchor, until,
                lastBlankInsertAt, rolledDiceKey);
    }

    /** 是否处于购买冷却中（{@code now} = 当前世界 gameTime；{@code cooldownUntil == 0} ⇒ 永不冷却） */
    public boolean inCooldown(long now) {
        return cooldownUntil > now;
    }

    /**
     * 投入一枚空白筹码（需求 5）：记录投入时刻、刷新次数 +1（封顶 {@value ChipShopCatalog#REFRESH_MAX}）、
     * 并**清零购买冷却**（投入即可立刻再用）。
     *
     * <p>⚠️ 本方法**不**重抽候选（重抽入口只有三处，见类头）——它只发一张「重抽券」，
     * 由玩家在界面里自行用；调用方负责扣减手持物与校验投入冷却。
     */
    public ChipShopState withBlankInsert(long now, int newRefreshes, long cooldownUntil) {
        return new ChipShopState(offers, lastPickCategory, purchasedCount,
                Math.max(0, Math.min(ChipShopCatalog.REFRESH_MAX, newRefreshes)), regenAnchor, cooldownUntil, now,
                rolledDiceKey);
    }

    /** 机器行 / 取证用：候选的稳定摘要（同一批任意次读到相同值） */
    public String offersKey() {
        return offers.isEmpty() ? "-" : String.join("+", offers);
    }
}
