package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.starengine.combat.PartyRelations;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.MemberRecommendationChipItem;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.starengine.item.CuriosCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡牌商店「货架存档」（任务O §1.3；2026-10-09 用户裁决「重新进商店不得刷新卡牌」）。
 *
 * <p><b>为什么单独存一份</b>：改造前货架内容只活在 {@link CardShopMenu} 的字段里，菜单随界面关闭而销毁，
 * 开店时 initShelf() 又无条件重抽 ⇒「关店再开店 = 换一批卡」。用户裁决的唯一不变量是
 * 「卡牌只在计时器（沙漏）刚好归 0 的那一刻更换」，因此货架必须存到**不随界面关闭而消失**的地方：
 * 本记录落在玩家附件 {@link ModAttachments#SHOP_SHELF}（随玩家存档持久化），由服务端 tick 驱动重抽
 * （见 {@link CardShopCycleTicker}），开店只读。
 *
 * <p><b>多人口径</b>：每名玩家各持一份货架存档（抽架按玩家自己的骰子星级 / 团队立牌 / 会员推荐信），
 * 同一方块被多人使用互不影响；每人的 60 秒周期也各自独立（真值 = 附件 shop_transfer_cooldown_end）。
 *
 * <p><b>字段</b>：items = 最多 {@value CardShopMenu#SHELF_SIZE} 个卡牌物品注册名（顺序 = 货架格序）；
 * prices = 每格当时售价；discounted / sold = 降价位与已售位。售价随存档冻结，重开界面不会因装备变化而
 * 改写（只在重抽那一刻按当时口径重算）——与「同一批卡」的语义一致。
 *
 * <p><b>任务U′（2026-10-10 用户裁决）</b>：货架还会在「装备经商立牌 / 大酬宾窗口内」时**只增不减地补差额牌**
 * （{@link #topUp}）—— 已有格子的卡与价一律不动、60 秒计时器也不动；立牌取下 / 窗口过期**不缩架**，
 * 要等下一次重抽（沙漏归 0）才按当时口径重排。
 */
public record CardShopShelf(List<ResourceLocation> items, List<Integer> prices, List<Boolean> discounted,
                           List<Boolean> sold, boolean enterRewardPaid) {

    /** 空存档 = 从未抽过（首次使用 / 旧存档升级）⇒ 开店时抽一批并落盘 */
    public static final CardShopShelf EMPTY = new CardShopShelf(List.of(), List.of(), List.of(), List.of(), false);

    /** 物品注册名 codec（只存 id，不存 ItemStack 组件：货架卡牌一律是「白板」物品） */
    private static final Codec<ResourceLocation> ITEM_ID_CODEC =
            ResourceLocation.CODEC;

    public static final Codec<CardShopShelf> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ITEM_ID_CODEC.listOf().fieldOf("items").forGetter(CardShopShelf::items),
            Codec.INT.listOf().optionalFieldOf("prices", List.of()).forGetter(CardShopShelf::prices),
            Codec.BOOL.listOf().optionalFieldOf("discounted", List.of()).forGetter(CardShopShelf::discounted),
            Codec.BOOL.listOf().optionalFieldOf("sold", List.of()).forGetter(CardShopShelf::sold),
            // 任务Q §14：经商立牌「进店 +1 星币」是否已经在**本批卡**上发过。
            //   optionalFieldOf 默认 false ⇒ 旧存档（没有这个键）等同于「本批还没发过」，
            //   即升级后第一次进店仍能正常拿到 1 星币。
            Codec.BOOL.optionalFieldOf("enter_reward_paid", false).forGetter(CardShopShelf::enterRewardPaid)
    ).apply(instance, CardShopShelf::new));

    /** 是否已经抽过（空存档 ⇒ false；rollShelf 至少出 3 格，故非空即已抽过） */
    public boolean initialized() {
        return !items.isEmpty();
    }

    /** 上架格数（0..{@value CardShopMenu#SHELF_SIZE}） */
    public int size() {
        return items.size();
    }

    /**
     * 任务Q §14 取证用：**本批卡的稳定标识**（内容摘要：卡牌注册名 + 售价）。
     * <p>同批卡任意次读到的值相同，重抽后通常不同 ⇒ 机器行里可直接用它区分「同一批反复进店」
     * 与「沙漏归 0 换了新一批」。
     */
    public int batchId() {
        return 31 * items.hashCode() + prices.hashCode();
    }

    /** 第 index 格卡牌（注册名未知 ⇒ 空，避免旧存档引用已被移除的物品时建出空气卡） */
    public ItemStack stackAt(int index) {
        if (index < 0 || index >= items.size()) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(items.get(index)).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    public int priceAt(int index) {
        return index >= 0 && index < prices.size() ? prices.get(index) : 0;
    }

    public boolean discountedAt(int index) {
        return index >= 0 && index < discounted.size() && discounted.get(index);
    }

    public boolean soldAt(int index) {
        return index >= 0 && index < sold.size() && sold.get(index);
    }

    /**
     * 任务V §1.3-D3（2026-10-10 审计）：四张并行列表的**唯一对齐入口** —— 任何要在某一格下标上
     * {@code set(...)}、或往末尾追加之前，都必须先过这里。
     *
     * <p>畸形来源：{@code prices} / {@code discounted} / {@code sold} 在 {@link #CODEC} 里是
     * {@code optionalFieldOf}（旧存档或手改过的存档可能为空 / 比 items 短），旧实现直接在短列表上
     * {@code set(index, …)} ⇒ 抽到该格时抛 {@code IndexOutOfBoundsException}（审计 D3）。
     *
     * <p>规则（与任务V §1.3-D4 同口径）：**短则补默认值**（价 0 / 未降价 / 未售）、
     * **长则裁到 {@code items.size()}** —— 只补短不裁长会把畸形数据原样固化进新记录。
     *
     * @return 已对齐的记录；本来就等长时返回**同一实例**（调用方可用引用相等判断「无需改写」）
     */
    public CardShopShelf ensureLengths() {
        int size = items.size();
        if (prices.size() == size && discounted.size() == size && sold.size() == size) {
            return this;
        }
        List<Integer> alignedPrices = new ArrayList<>(prices);
        while (alignedPrices.size() < size) {
            alignedPrices.add(0);
        }
        List<Boolean> alignedDiscounted = new ArrayList<>(discounted);
        while (alignedDiscounted.size() < size) {
            alignedDiscounted.add(Boolean.FALSE);
        }
        List<Boolean> alignedSold = new ArrayList<>(sold);
        while (alignedSold.size() < size) {
            alignedSold.add(Boolean.FALSE);
        }
        return new CardShopShelf(items, List.copyOf(alignedPrices.subList(0, size)),
                List.copyOf(alignedDiscounted.subList(0, size)), List.copyOf(alignedSold.subList(0, size)),
                enterRewardPaid);
    }

    /** 第 index 格置「已售」（购买成功后落盘；记录不可变 ⇒ 返回新记录） */
    public CardShopShelf withSold(int index) {
        if (index < 0 || index >= items.size()) {
            return this;
        }
        // 任务V §1.3-D3：先对齐四张列表再 set，畸形存档不再 IOOBE（对齐结果一并落进新记录）
        CardShopShelf aligned = ensureLengths();
        List<Boolean> next = new ArrayList<>(aligned.sold);
        next.set(index, Boolean.TRUE);
        // 任务Q §14：买牌只改 sold 位，**本批的进店奖励状态原样带过去**（wither 不得把已发标记清掉）
        return new CardShopShelf(aligned.items, aligned.prices, aligned.discounted, List.copyOf(next),
                aligned.enterRewardPaid);
    }

    /** 第 index 格置「降价 + 新售价」（经商立牌转账后强制降价时落盘） */
    public CardShopShelf withDiscounted(int index, int price) {
        if (index < 0 || index >= items.size()) {
            return this;
        }
        // 任务V §1.3-D3：同上 —— 统一先对齐（原先只对 newPrices 补短，discounted 短时 set 会 IOOBE）
        CardShopShelf aligned = ensureLengths();
        List<Boolean> flags = new ArrayList<>(aligned.discounted);
        flags.set(index, Boolean.TRUE);
        List<Integer> newPrices = new ArrayList<>(aligned.prices);
        newPrices.set(index, price);
        return new CardShopShelf(aligned.items, List.copyOf(newPrices), List.copyOf(flags), aligned.sold,
                aligned.enterRewardPaid);
    }

    /**
     * 任务Q §14：把「本批卡的进店奖励已发」标记写进存档（记录不可变 ⇒ 返回新记录）。
     * <p>与 {@link #roll} 造的**新批次**配合构成完整的封堵：同一批卡期间该位恒为 true ⇒ 反复进出商店
     * 不会再触发返现；新一轮重抽造出的新记录该位为 false ⇒ 才允许再发 1 枚。
     */
    public CardShopShelf withEnterRewardPaid(boolean paid) {
        if (paid == this.enterRewardPaid) {
            return this;
        }
        return new CardShopShelf(items, prices, discounted, sold, paid);
    }

    // ────────────────────────────── 附件读写 ──────────────────────────────

    public static CardShopShelf get(Player player) {
        return ModAttachments.SHOP_SHELF.get(player);
    }

    public static void store(Player player, CardShopShelf shelf) {
        ModAttachments.SHOP_SHELF.set(player, shelf);
    }

    // ────────────────────────────── 抽架（纯计算，不改界面状态） ──────────────────────────────

    /**
     * 按「当前玩家口径」抽一批新货架并返回存档（**不落盘、不改菜单**；调用方决定何时 store）。
     * 口径与改造前 CardShopMenu#rerollShelf 完全一致：星级门槛 + 团队立牌加成 / 降价概率 +
     * 大酬宾窗口 + 会员推荐信恒减，且新架一律清空 sold（已售格重新上架）。
     */
    public static CardShopShelf roll(Player player) {
        RandomSource random = player.getRandom();
        int starLevel = currentStarLevel(player);
        boolean teamBonus = teamHasParunan(player);
        long promotionUntil = ModAttachments.getShopPromotionUntil(player);
        boolean promotion = player.level().getGameTime() < promotionUntil;
        // 会员推荐信筹码(2026-10-08 追加能力,仅装备者本人的商店):所有卡牌价格恒 -1(非随机;与降价叠加,最低 0)
        int memberReduction = MemberRecommendationChipItem.shopPriceReduction(player);

        List<Item> rolled = CardShopCatalog.rollShelf(random, starLevel, teamBonus, promotion);
        List<ResourceLocation> ids = new ArrayList<>(rolled.size());
        List<Integer> prices = new ArrayList<>(rolled.size());
        List<Boolean> discounts = new ArrayList<>(rolled.size());
        List<Boolean> sold = new ArrayList<>(rolled.size());
        for (Item item : rolled) {
            // 降价只在「团队内有经商立牌玩家」时才有可能（用户追加口径）
            boolean discount = teamBonus && CardShopCatalog.rollDiscount(random);
            ids.add(BuiltInRegistries.ITEM.getKey(item));
            prices.add(priceOf(discount, memberReduction));
            discounts.add(discount);
            sold.add(Boolean.FALSE);
        }
        // 任务Q §14：重抽 = **新一批卡** ⇒ 进店奖励标记一并归 false（沙漏归 0 / 大酬宾重抽后可再得 1 星币）
        return new CardShopShelf(List.copyOf(ids), List.copyOf(prices), List.copyOf(discounts), List.copyOf(sold), false);
    }

    /**
     * 当前口径下**应有**的货架格数（任务U′）：基础 {@value CardShopCatalog#BASE_SLOTS} 格 + 大酬宾窗口内
     * {@value CardShopCatalog#PROMOTION_BONUS_SLOTS} 格 + 团队内有经商立牌玩家 {@value CardShopCatalog#TEAM_BONUS_SLOTS} 格
     * —— 与 {@link CardShopCatalog#rollShelf} 的抽架口径同源（同一套判定：促销窗口附件 / 团队立牌）。
     */
    public static int targetSize(Player player) {
        int slots = CardShopCatalog.BASE_SLOTS;
        if (player.level().getGameTime() < ModAttachments.getShopPromotionUntil(player)) {
            slots += CardShopCatalog.PROMOTION_BONUS_SLOTS;
        }
        if (teamHasParunan(player)) {
            slots += CardShopCatalog.TEAM_BONUS_SLOTS;
        }
        return slots;
    }

    /**
     * 任务U′「补差额牌」（2026-10-10 用户裁决）：把货架**只增不减**地补到 {@link #targetSize} 的格数。
     *
     * <p>用户口径（原话「装备或触发大酬宾事件，原本牌和计时器不动，改为立即补差额牌（即补上牌缺口）。
     * 其他逻辑照旧」「如果玩家在一轮刷新之前移除了经商立牌或者效果到期，当前商店牌周期内不变化，变化
     * 应用到下次变牌」）：
     * <ul>
     *   <li>已有格子的卡牌、售价、降价 / 已售标记、{@code enterRewardPaid} 标记**全部原样保留**
     *       （本方法既不清 sold、也不换批）；</li>
     *   <li>缺口只可能落在「促销格（通用池）/ 团队加成格（只出战斗牌）」两段上，按
     *       {@link CardShopCatalog#rollShelf} 的抽架顺序补：先促销格、后团队格；</li>
     *   <li>立牌取下 / 大酬宾窗口过期 ⇒ **不缩架**（无缺口时原样返回），要等沙漏归 0 那次重抽
     *       （{@link CardShopCycleTicker}）才按当时口径重排。</li>
     * </ul>
     *
     * @return 补过差额的新记录；没有缺口（或存档还没抽过）时返回**传入的同一个实例**（调用方可用引用相等判断「没变」）
     */
    public static CardShopShelf topUp(Player player, CardShopShelf shelf) {
        if (!shelf.initialized()) {
            return shelf;
        }
        // 任务V §1.3-D3/D4：并行列表统一走 ensureLengths —— **短则补齐、长则裁到 items.size()**。
        // 旧实现只在追加前补短、不裁长（审计 D4）：列表比 items 长的畸形存档会被原样固化进新记录。
        // 无缺口分支要把它交回调用方（正常存档时 aligned == shelf 同一实例 ⇒ 不落盘）。
        CardShopShelf aligned = shelf.ensureLengths();
        boolean teamBonus = teamHasParunan(player);
        boolean promotion = player.level().getGameTime() < ModAttachments.getShopPromotionUntil(player);
        // 已有格数 − 基础格数 = 已存在的加成分段数：1 格 = 团队格、2 格 = 促销两格、3 格 = 两者都有
        // （促销恒为 2 格、团队恒为 1 格 ⇒ 分段与格数一一对应，不会出现「1 格促销」这种中间态）
        int extras = shelf.size() - CardShopCatalog.BASE_SLOTS;
        boolean hasTeamSlot = extras == 1 || extras >= 3;
        boolean hasPromotionSlots = extras >= 2;
        int promotionMissing = promotion && !hasPromotionSlots ? CardShopCatalog.PROMOTION_BONUS_SLOTS : 0;
        int teamMissing = teamBonus && !hasTeamSlot ? CardShopCatalog.TEAM_BONUS_SLOTS : 0;
        if (promotionMissing <= 0 && teamMissing <= 0) {
            // 无缺口：正常存档下 aligned == shelf（同一实例 ⇒ 调用方不落盘）；畸形存档则借这次调用
            // 把长度对齐后的记录交回去（任务V §1.3-D4：不把畸形数据固化下去）
            return aligned;
        }

        RandomSource random = player.getRandom();
        int starLevel = currentStarLevel(player);
        int memberReduction = MemberRecommendationChipItem.shopPriceReduction(player);
        List<ResourceLocation> ids = new ArrayList<>(aligned.items());
        List<Integer> prices = new ArrayList<>(aligned.prices());
        List<Boolean> discounts = new ArrayList<>(aligned.discounted());
        List<Boolean> sold = new ArrayList<>(aligned.sold());
        appendSlots(random, starLevel, teamBonus, memberReduction, false, promotionMissing, ids, prices, discounts, sold);
        appendSlots(random, starLevel, teamBonus, memberReduction, true, teamMissing, ids, prices, discounts, sold);
        return new CardShopShelf(List.copyOf(ids), List.copyOf(prices), List.copyOf(discounts), List.copyOf(sold),
                aligned.enterRewardPaid());
    }

    /** 把新抽的格子追加到四张并行列表末尾（售价 / 降价口径与 {@link #roll} 完全一致；新格一律未售出） */
    private static void appendSlots(RandomSource random, int starLevel, boolean teamBonus, int memberReduction,
                                    boolean combatOnly, int count, List<ResourceLocation> ids, List<Integer> prices,
                                    List<Boolean> discounts, List<Boolean> sold) {
        if (count <= 0) {
            return;
        }
        for (Item item : CardShopCatalog.drawAdditional(random, starLevel, combatOnly, count, ids)) {
            boolean discount = teamBonus && CardShopCatalog.rollDiscount(random);
            ids.add(BuiltInRegistries.ITEM.getKey(item));
            prices.add(priceOf(discount, memberReduction));
            discounts.add(discount);
            sold.add(Boolean.FALSE);
        }
    }

    /** 卡牌最终售价 = 基础价/降价价 − 会员推荐信筹码的恒减(下限 0;两线同口径) */
    static int priceOf(boolean discounted, int memberReduction) {
        int base = discounted ? CardShopCatalog.DISCOUNT_PRICE : CardShopCatalog.BASE_PRICE;
        return Math.max(0, base - memberReduction);
    }

    /** 装备骰子的星级（DiceCurioItem.configStarLevel：下界之星骰子恒 3，否则 clamp 0..3） */
    static int currentStarLevel(Player player) {
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) {
            return 0;
        }
        var dice = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        return dice.isPresent() ? DiceCurioItem.configStarLevel(dice.get().stack()) : 0;
    }

    /**
     * 团队内（**含自己 + 严格同队**，PartyRelations.isSameTeam）是否有玩家佩戴经商立牌。
     * 无队伍 ⇒ 只可能命中自己（isSameTeam(a, a) == true）。
     */
    static boolean teamHasParunan(Player player) {
        if (hasParunan(player)) {
            return true;
        }
        if (player.getServer() == null) {
            return false;
        }
        for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
            if (other != player && hasParunan(other) && PartyRelations.isSameTeam(player, other)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasParunan(Player player) {
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.PARUNAN_SIGN.get())).isPresent();
    }
}
