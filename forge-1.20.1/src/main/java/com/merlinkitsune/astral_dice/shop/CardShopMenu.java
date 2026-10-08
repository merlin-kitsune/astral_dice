package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.combat.PartyRelations;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.economy.StarCoinBalanceSync;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.item.chip.AtmChipItem;
import com.merlinkitsune.astral_dice.item.chip.MemberRecommendationChipItem;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import com.merlinkitsune.astral_dice.screen.ModMenuTypes;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.starenginelib.economy.StarEngineEconomy;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 卡牌商店菜单（2026-10-08 v3 计划；主视图 + 二级转账视图共用同一菜单）。
 *
 * <p><b>布局</b>（贴图 card_shop_inventory_bg.png 288x187，坐标为 GUI 内绝对坐标，见 CardShopScreen）：
 * 6 个货架格（贴图位置由屏幕层按 x = 7+46i / y = 28 画，格内物品由原版物品渲染在
 * {@link #SHELF_ITEM_X}/{@link #SHELF_ITEM_Y}）+ 底部右侧 9x4 玩家物品格。
 * 不覆写任何 GUI 常量：屏幕层自持 288x187。
 *
 * <p><b>购买/兑换/转账全部走「菜单按钮」</b>（原版 {@code clickMenuButton}，客户端
 * {@code gameMode.handleInventoryButtonClick}）—— 服务端只在 {@link #clickMenuButton} 内结算，
 * 玩家的物品栏操作仍走原版槽位协议，二者不混用。
 *
 * <p><b>限购</b>：每玩家 × 每次打开商店限购 1 次（关掉再开即重置）—— 由 {@link #sold} 本次会话标记实现，
 * 不落盘（用户裁决：非永久限购）。
 *
 * <p><b>C2S 载荷</b>：转账需要「目标索引 + 金额」两个参数，原版按钮包只有一个 int，
 * 故单独走 {@code ModNetwork.CardShopTransferMessage}（其余按钮一律用原版按钮包）。
 *
 * <p><b>1.20.1 Forge 移植差异</b>（其余逻辑与 1.21.1 版逐字相同）：
 * ① 菜单类型取 {@code ModMenuTypes.CARD_SHOP.get()}（1.20.1 是 {@code RegistryObject}，1.21.1 是 {@code Supplier}）；
 * ② S2C 名单与 ActionBar 提示改走本线 {@code ModNetwork.sendToPlayer(ServerPlayer, Object)}
 *    （1.21.1 是 {@code PacketDistributor.sendToPlayer}），载荷类型由 {@code ActionBarPayload} 换成
 *    {@code ModNetwork.ActionBarMessage}；③ 其余（DataSlot/Slot/clickMenuButton/stillValid/quickMoveStack/
 *    Component.translatable/ItemStack#getHoverName/CuriosApi/StarEngineEconomy/ResourceConversion）两线同名同签名。
 */
public class CardShopMenu extends AbstractContainerMenu {

    public static final int SHELF_SIZE = CardShopCatalog.MAX_SLOTS;

    private static final int HIDDEN_X = -10000;
    private static final int HIDDEN_Y = -10000;

    /** 玩家物品格左上角（= 其中第一格图标的绘制坐标；见 CardShopScreen） */
    public static final int INVENTORY_X = 115;
    public static final int INVENTORY_Y = 100;
    /** 货架格内卡面的绘制坐标（屏幕层用，菜单层只作常量声明） */
    public static final int SHELF_ITEM_X = 9;
    public static final int SHELF_ITEM_Y = 30;

    public static final int BUTTON_BUY_BASE = 0;              // 0..5 = 货架第 i 格（购买）
    public static final int BUTTON_TRANSFER = 16;             // 打开二级转账视图
    public static final int BUTTON_EXCHANGE = 17;             // 星光兑换星币（原经商立牌主动技能）
    public static final int BUTTON_REFRESH_TRANSFER = 18;     // 刷新候选玩家名单

    public static final int MIN_TRANSFER = 5;
    public static final int MAX_TRANSFER = 20;
    public static final int TRANSFER_COOLDOWN_SECONDS = 60;

    /** 经商立牌被动第 1 条(2026-10-08):佩戴者每次进入商店获得的星币数 */
    private static final int PARUNAN_ENTER_COIN = 1;
    /** 经商立牌被动第 5 条(2026-10-08;用户当日更正 +20% → **+50%**):星光兑换的额外收入比例(下限为 1 枚) */
    private static final float PARUNAN_EXCHANGE_BONUS_RATE = 0.50F;
    /** ATM 机筹码(2026-10-08 重写):转账发起者装备时,双方各获得的星光 / 星币 */
    private static final int ATM_TRANSFER_STARLIGHT = 1;
    private static final int ATM_TRANSFER_COINS = 2;

    /** DataSlot[0..5] = 货架格显示码；DataSlot[6] = 转账剩余冷却秒数 */

    private final Player player;
    private final Inventory playerInventory;
    private final SimpleContainer shelfContainer = new SimpleContainer(SHELF_SIZE);
    private final int[] prices = new int[SHELF_SIZE];
    private final boolean[] discounted = new boolean[SHELF_SIZE];
    private final boolean[] sold = new boolean[SHELF_SIZE];
    private int shelfSize = 0;
    /** 客户端读到的冷却剩余秒数（服务端在 {@link #computeTransferCooldownSeconds()} 现算） */
    private int transferCooldownSeconds = 0;
    private List<UUID> candidateIds = List.of();

    public CardShopMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.CARD_SHOP.get(), containerId);
        this.player = playerInventory.player;
        this.playerInventory = playerInventory;

        // 货架格（隐藏坐标；贴图与卡面位置由屏幕层负责绘制）
        for (int i = 0; i < SHELF_SIZE; i++) {
            addSlot(new ShelfSlot(shelfContainer, i, HIDDEN_X, HIDDEN_Y));
        }
        // 玩家物品格 9x4：r = 0..2 主物品栏（槽 9..35）、r = 3 快捷栏（槽 0..8）
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int x = INVENTORY_X + col * 18;
                int y = INVENTORY_Y + row * 18;
                int index = row == 3 ? col : 9 + row * 9 + col;
                addSlot(new Slot(playerInventory, index, x, y));
            }
        }
        // 同步：货架显示码 6 个 + 冷却秒数 1 个
        for (int i = 0; i < SHELF_SIZE; i++) {
            final int index = i;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return encodeShelf(index);
                }

                @Override
                public void set(int value) {
                    sold[index] = (value & 1) != 0;
                    discounted[index] = (value & 2) != 0;
                    prices[index] = (value >> 8) & 0xFF;
                }
            });
        }
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return computeTransferCooldownSeconds();
            }

            @Override
            public void set(int value) {
                transferCooldownSeconds = value;
            }
        });

        if (!player.level().isClientSide()) {
            if (player instanceof ServerPlayer serverPlayer) {
                StarCoinBalanceSync.forceResend(serverPlayer);
            }
            initShelf();
        }
    }

    // ────────────────────────────── 开店抽池 ──────────────────────────────

    private void initShelf() {
        RandomSource random = player.getRandom();
        int starLevel = currentStarLevel(player);
        boolean teamBonus = teamHasParunan(player);
        long promotionUntil = ModAttachments.getShopPromotionUntil(player);
        boolean promotion = player.level().getGameTime() < promotionUntil;
        // 会员推荐信筹码(2026-10-08 追加能力,仅装备者本人的商店):所有卡牌价格恒 -1(非随机;与降价叠加,最低 0)
        int memberReduction = MemberRecommendationChipItem.shopPriceReduction(player);

        List<Item> rolled = CardShopCatalog.rollShelf(random, starLevel, teamBonus, promotion);
        shelfSize = rolled.size();
        for (int i = 0; i < SHELF_SIZE; i++) {
            sold[i] = false;
            discounted[i] = false;
            if (i < rolled.size()) {
                shelfContainer.setItem(i, new ItemStack(rolled.get(i)));
                // 降价只在「团队内有经商立牌玩家」时才有可能（用户追加口径）
                boolean discount = teamBonus && CardShopCatalog.rollDiscount(random);
                discounted[i] = discount;
                prices[i] = priceOf(discount, memberReduction);
            } else {
                shelfContainer.setItem(i, ItemStack.EMPTY);
                prices[i] = 0;
            }
        }
        // 经商立牌被动「传奇商人」第 1 条(2026-10-08 用户裁决,**仅佩戴者本人**):每次进入商店时获得 1 星币
        if (ParunanSignItem.isEquipped(player)) {
            ResourceConversion.giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), PARUNAN_ENTER_COIN));
            actionBar(player, ChatFormatting.GOLD, "msg.astral_dice.card_shop_parunan_enter", PARUNAN_ENTER_COIN);
        }
    }

    /** 卡牌最终售价 = 基础价/降价价 − 会员推荐信筹码的恒减(下限 0;两线同口径) */
    private static int priceOf(boolean discounted, int memberReduction) {
        int base = discounted ? CardShopCatalog.DISCOUNT_PRICE : CardShopCatalog.BASE_PRICE;
        return Math.max(0, base - memberReduction);
    }

    /** 装备骰子的星级（{@code DiceCurioItem.configStarLevel}：下界之星骰子恒 3，否则 clamp 0..3） */
    private static int currentStarLevel(Player player) {
        // ⚠️ 平台差异:1.20.1 的 Curios 入口收敛在库 shim CuriosCompat(与 item/CurioSlotUtil 同口径),
        //    原版 CuriosApi.getCuriosInventory 在本线返回 LazyOptional(无 isEmpty/get 的同名用法)。
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) {
            return 0;
        }
        var dice = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        return dice.isPresent() ? DiceCurioItem.configStarLevel(dice.get().stack()) : 0;
    }

    /**
     * 团队内（**含自己 + 严格同队**，{@code PartyRelations.isSameTeam}）是否有玩家佩戴经商立牌。
     * 无队伍 ⇒ 只可能命中自己（{@code isSameTeam(a, a) == true}）。
     */
    private static boolean teamHasParunan(Player player) {
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

    private static boolean hasParunan(Player player) {
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.PARUNAN_SIGN.get())).isPresent();
    }

    // ────────────────────────────── 按钮入口 ──────────────────────────────

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player != this.player) {
            return false;
        }
        if (id >= BUTTON_BUY_BASE && id < BUTTON_BUY_BASE + SHELF_SIZE) {
            return tryBuy(player, id - BUTTON_BUY_BASE);
        }
        if (id == BUTTON_EXCHANGE) {
            if (player instanceof ServerPlayer serverPlayer) {
                tryExchange(serverPlayer);
            }
            return true;
        }
        if (id == BUTTON_REFRESH_TRANSFER) {
            if (player instanceof ServerPlayer serverPlayer) {
                sendTransferCandidates(serverPlayer);
            }
            return true;
        }
        return false;
    }

    /** 购买货架第 {@code index} 格（价格取本店当前价；限购 1 次/店） */
    private boolean tryBuy(Player buyer, int index) {
        if (buyer.level().isClientSide() || index < 0 || index >= SHELF_SIZE) {
            return false;
        }
        if (index >= shelfSize || sold[index]) {
            return false;
        }
        int price = prices[index];
        if (price < 0) {
            return false;
        }
        // 价格可为 0(会员推荐信 −1 与降价叠加;用户 2026-10-08 明确「价格降低至 0」)⇒ 不走经济接口,直接发货
        if (price > 0) {
            if (!StarEngineEconomy.isAvailable() || !StarEngineEconomy.hasBalance(buyer, price)) {
                actionBar(buyer, ChatFormatting.RED, "msg.astral_dice.card_shop_no_coin", price);
                return true;
            }
            if (!StarEngineEconomy.withdraw(buyer, price)) {
                actionBar(buyer, ChatFormatting.RED, "msg.astral_dice.card_shop_no_coin", price);
                return true;
            }
        }
        ItemStack card = shelfContainer.getItem(index).copy();
        shelfContainer.setItem(index, ItemStack.EMPTY);
        sold[index] = true;
        if (!playerInventory.add(card)) {
            buyer.drop(card, false);
        }
        StarCoinBalanceSync.notifyChanged(buyer);
        actionBar(buyer, ChatFormatting.GOLD, "msg.astral_dice.card_shop_bought", card.getHoverName(), price);
        return true;
    }

    /** 星光兑换星币（原经商立牌主动技能整体迁入；**无冷却**，纯净兑换） */
    private void tryExchange(ServerPlayer exchanger) {
        int gained = ResourceConversion.starlightToStarCoins(exchanger, -1);
        if (gained <= 0) {
            actionBar(exchanger, ChatFormatting.RED, "msg.astral_dice.card_shop_no_starlight");
            return;
        }
        // 经商立牌被动「传奇商人」第 5 条(2026-10-08 用户裁决,**仅佩戴者本人**;当日更正为 +50%):星光兑换额外 +50% 收入。
        // 取整沿用旧 ATM 的「下限为 1」策略:截断后不足 1 枚时至少 +1(避免小额兑换加成为 0)。
        int bonus = 0;
        if (ParunanSignItem.isEquipped(exchanger)) {
            bonus = Math.max(1, (int) (gained * PARUNAN_EXCHANGE_BONUS_RATE));
            ResourceConversion.giveItem(exchanger, new ItemStack(ModItems.STAR_COIN.get(), bonus));
        }
        StarCoinBalanceSync.notifyChanged(exchanger);
        actionBar(exchanger, ChatFormatting.GOLD, "msg.astral_dice.card_shop_exchange_done", gained + bonus);
    }

    // ────────────────────────────── 二级转账 ──────────────────────────────

    /** 重发候选玩家名单（打开转账视图 / 点刷新 / 转账落定后） */
    public void sendTransferCandidates(ServerPlayer owner) {
        if (owner.getServer() == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (ServerPlayer other : owner.getServer().getPlayerList().getPlayers()) {
            if (other == owner) {
                continue;
            }
            names.add(other.getName().getString());
            ids.add(other.getUUID());
        }
        candidateIds = List.copyOf(ids);
        ModNetwork.sendToPlayer(owner, new ModNetwork.CardShopTransferListMessage(names));
    }

    /**
     * 确认转账（服务端二次校验：菜单归属 / 冷却 / 目标有效性 / 余额）。
     *
     * <p>成功后按 D6 给**转账者自己**的当前货架强制一张降价牌（顺延到第一张未降价的未售出格），
     * 并双端刷新星币余额显示。
     */
    public void handleTransfer(ServerPlayer owner, int targetIndex, int amount) {
        if (owner != this.player || owner.level().isClientSide()) {
            return;
        }
        if (amount < MIN_TRANSFER || amount > MAX_TRANSFER) {
            return;
        }
        long now = owner.level().getGameTime();
        long cooldownEnd = ModAttachments.getShopTransferCooldownEnd(owner);
        if (now < cooldownEnd) {
            actionBar(owner, ChatFormatting.RED, "msg.astral_dice.card_shop_transfer_cooling",
                    (int) ((cooldownEnd - now + 19L) / 20L));
            return;
        }
        if (targetIndex < 0 || targetIndex >= candidateIds.size()) {
            return;
        }
        ServerPlayer target = owner.getServer() == null
                ? null
                : owner.getServer().getPlayerList().getPlayer(candidateIds.get(targetIndex));
        if (target == null || target == owner) {
            actionBar(owner, ChatFormatting.RED, "msg.astral_dice.card_shop_transfer_target_lost");
            sendTransferCandidates(owner);
            return;
        }
        if (!StarEngineEconomy.transfer(owner, target, amount)) {
            actionBar(owner, ChatFormatting.RED, "msg.astral_dice.card_shop_no_coin", amount);
            return;
        }
        ModAttachments.setShopTransferCooldownEnd(owner, now + TRANSFER_COOLDOWN_SECONDS * 20L);
        forceDiscountOnOwnShelf();
        // ATM 机筹码(2026-10-08 重写):**转账发起者**装备时,转账双方各额外获得 1 星光与 2 星币
        if (AtmChipItem.isEquipped(owner)) {
            StarLightManager.add(owner, ATM_TRANSFER_STARLIGHT);
            StarLightManager.add(target, ATM_TRANSFER_STARLIGHT);
            ResourceConversion.giveItem(owner, new ItemStack(ModItems.STAR_COIN.get(), ATM_TRANSFER_COINS));
            ResourceConversion.giveItem(target, new ItemStack(ModItems.STAR_COIN.get(), ATM_TRANSFER_COINS));
        }
        StarCoinBalanceSync.notifyChanged(owner);
        StarCoinBalanceSync.notifyChanged(target);
        actionBar(owner, ChatFormatting.GOLD, "msg.astral_dice.card_shop_transfer_done",
                target.getName().getString(), amount);
        actionBar(target, ChatFormatting.GOLD, "msg.astral_dice.card_shop_transfer_received",
                owner.getName().getString(), amount);
    }

    /**
     * 经商立牌被动「传奇商人」第 4 条(2026-10-08 用户裁决,**仅佩戴者本人**):佩戴者转账后,
     * 自己货架上「顺序第一张」未售出且未降价的牌强制降价至 {@link CardShopCatalog#DISCOUNT_PRICE}
     * (再叠加会员推荐信筹码的恒减 ⇒ 可为 0 星币)。
     */
    private void forceDiscountOnOwnShelf() {
        if (!ParunanSignItem.isEquipped(player)) {
            return;
        }
        int memberReduction = MemberRecommendationChipItem.shopPriceReduction(player);
        for (int i = 0; i < shelfSize; i++) {
            if (!sold[i] && !discounted[i]) {
                discounted[i] = true;
                prices[i] = priceOf(true, memberReduction);
                return;
            }
        }
    }

    // ────────────────────────────── 菜单基类实现 ──────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (slot instanceof ShelfSlot || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int playerStart = SHELF_SIZE;
        int playerEnd = SHELF_SIZE + 36;
        // 玩家区布局 = 主物品栏 27 格(playerStart..playerStart+27) + 快捷栏 9 格(playerStart+27..playerEnd)
        // ⚠️ 2026-10-08 修正:原判据 index < playerStart + 9 实际指的是**主物品栏第一行**,
        //    会把「主物品栏→快捷栏」的 shift-click 路由反着做(快捷栏物品反而被送进主物品栏第一行)。
        boolean fromHotbar = index >= playerStart + 27;
        boolean moved = fromHotbar
                ? moveItemStackTo(stack, playerStart, playerStart + 27, false)
                : moveItemStackTo(stack, playerStart + 27, playerEnd, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    /**
     * 恒 true:本菜单有**两个来源**(方块右键 / 经商立牌主动技能),立牌来源没有可判距的方块坐标,
     * 故不设方块型菜单的距离契约;余额/冷却/目标/限购的二次校验全部在
     * {@link #clickMenuButton} 与 {@link #handleTransfer} 内完成(服务端权威)。
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ────────────────────────────── 客户端读取 ──────────────────────────────

    private int encodeShelf(int index) {
        int code = 0;
        if (sold[index]) {
            code |= 1;
        }
        if (discounted[index]) {
            code |= 2;
        }
        return code | ((prices[index] & 0xFF) << 8);
    }

    private int computeTransferCooldownSeconds() {
        if (player.level().isClientSide()) {
            return transferCooldownSeconds;
        }
        long now = player.level().getGameTime();
        long end = ModAttachments.getShopTransferCooldownEnd(player);
        if (end <= now) {
            return 0;
        }
        return (int) ((end - now + 19L) / 20L);
    }

    /** 货架已上架格数（客户端按货架槽位物品是否为空推断；原注释指向的 CardShopScreen#shelfFilled 并不存在） */
    public int shelfSize() {
        return shelfSize;
    }

    public boolean isSold(int index) {
        return index >= 0 && index < SHELF_SIZE && sold[index];
    }

    public boolean isDiscounted(int index) {
        return index >= 0 && index < SHELF_SIZE && discounted[index];
    }

    public int priceOf(int index) {
        return index >= 0 && index < SHELF_SIZE ? prices[index] : 0;
    }

    /** 转账剩余冷却秒数（0 = 就绪；客户端读 DataSlot 同步值） */
    public int transferCooldownSeconds() {
        return transferCooldownSeconds;
    }

    private void actionBar(Player target, ChatFormatting color, String key, Object... args) {
        if (!(target instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ModNetwork.sendToPlayer(serverPlayer, new ModNetwork.ActionBarMessage(
                Component.translatable(key, args).withStyle(color),
                GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    /** 货架格：只读展示（不可拿取、不可放置、单格 1 张） */
    private static class ShelfSlot extends Slot {

        ShelfSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
