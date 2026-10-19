package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.economy.StarCoinBalanceSync;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.network.ChipShopDenyPayload;
import com.merlinkitsune.astral_dice.screen.ModMenuTypes;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.starengine.economy.StarEngineEconomy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 筹码商店菜单（**逻辑批已完整实现** = 服务端结算正本，界面骨架见 {@code docs/chip-shop-ui-spec.md}，
 * 买卖逻辑判据正本 = {@code docs/chip-shop-logic-plan.md}）。
 *
 * <p><b>槽位构成</b>：先 3 个**隐藏候选槽**（{@link #HIDDEN_X}/{@link #HIDDEN_Y}，{@link OfferSlot}），
 * 再玩家物品栏 36 格 —— 主栏 9x3（物品栏槽 9..35）+ 快捷栏 9x1（槽 0..8）。
 * 玩家槽位的相对序号（0..26 = 主栏、27..35 = 快捷栏）由菜单的添加顺序决定，
 * 绝对槽序号 = {@link #OFFER_SLOTS} + 相对序号。
 *
 * <p><b>坐标口径</b>（界面规范 §3 末条）：界面侧面板格网原点 = (61, 94)、格距 18、快捷栏行 y = 152；
 * 「槽内物品图标内缩 1 px」⇒ 本类的槽位坐标 = 格网起点 **+1 px**。原版 {@code Slot} 的 x/y 同时是
 * **物品绘制点**与**命中判定点**，故内缩位移放在槽位坐标里一次做对，屏幕层不再逐格修正。
 *
 * <p><b>为什么 36 条玩家槽构造是逐条字面量</b>：守门脚本 {@code scripts/verify/verify_chip_shop_ui.ps1}
 * （G6）按字面统计槽位数（应恰为 36），循环写法会让这条判据失去意义 —— 保持逐条可核对。
 *
 * <p><b>反刷取</b>（计划表 §5）：候选槽 {@link OfferSlot#mayPickup} 恒 false ⇒ 候选**永不**进入原版
 * 「光标携带」机制（拖拽 / 双击都拿不走）；购买**唯一**入口是
 * {@link com.merlinkitsune.astral_dice.network.ChipShopBuyPayload} → {@link #handleBuy}，
 * 客户端界面里只有「点确认按钮」一条路径能发这个包。
 *
 * <p><b>服务端权威</b>：价格、刷新次数、余额、落点（先物品栏、装不下则掉落在玩家身边）一律在服务端现算（{@link ChipShopState} 附件 + 库经济），
 * 客户端只读 DataSlot 的同步值做显示；任何校验失败都回 {@link ChipShopDenyPayload} 让界面红闪。
 *
 * <p><b>响应包口径</b>：{@link #handleBuy} / {@link #handleRefresh} 都从**附件现读**状态（不复用菜单字段），
 * 因此重复包 / 并发包天然幂等（次数已耗尽 ⇒ 拒绝；槽已空 ⇒ 无候选 ⇒ 直接返回）。
 * <p><b>平台差异（forge-1.20.1）</b>：发包不用 1.21.1 的 {@code PacketDistributor.sendToPlayer}（NeoForge 网络层），
 * 一律走本线既有的 {@code SimpleChannel} 通道 —— {@code ModNetwork.sendToPlayer(target, new ChipShopDenyPayload(reason))}
 * （顶层类，协议 id 23）与 {@code ModNetwork.ActionBarMessage}（与 {@code shop/CardShopMenu#actionBar} 同款）。
 * 菜单构造、DataSlot 顺序、扣款/关店次序与 1.21.1 蓝本逐字一致（守门 G17/G19/G20/G23 按字面校验）。
 */
public class ChipShopMenu extends AbstractContainerMenu {

    /** 隐藏候选槽数量（= 条带里的三个筹码格） */
    public static final int OFFER_SLOTS = ChipShopCatalog.OFFER_COUNT;

    /** 玩家槽总数（9x3 主栏 + 9x1 快捷栏） */
    public static final int PLAYER_SLOT_COUNT = 36;
    /** 主栏格数（物品栏槽 9..35；快捷栏 = 其后的 9 格） */
    public static final int MAIN_SIZE = 27;

    /** 物品栏面板格网原点（界面侧常量同口径；槽位坐标另加 {@link #ITEM_INSET}） */
    public static final int GRID_X = 61;
    public static final int GRID_Y = 94;
    /** 格距（18x18 格） */
    public static final int GRID_PITCH = 18;
    /** 快捷栏行的格网 y（与主栏第 3 行之间留 4 px 间隙：94 + 2*18 = 130，快捷栏 152） */
    public static final int HOTBAR_GRID_Y = 152;
    /** 槽内物品内缩（界面规范 §3：16x16 物品在 18x18 格内居中 ⇒ 每边 1 px） */
    public static final int ITEM_INSET = 1;

    /** 候选槽的隐藏坐标（不参与绘制；候选图标由屏幕层按条带坐标 blit） */
    private static final int HIDDEN_X = -10000;
    private static final int HIDDEN_Y = -10000;

    /** DataSlot[0] = 单价花费（三格同价） */
    public static final int DATA_PRICE = 0;
    /** DataSlot[1] = 剩余刷新次数 */
    public static final int DATA_REFRESH_LEFT = 1;
    /** DataSlot[2] = 刷新次数上限（= {@value ChipShopCatalog#REFRESH_MAX}） */
    public static final int DATA_REFRESH_TOTAL = 2;
    /** DataSlot[3] = 最近一次拒绝原因（{@link #DENY_NONE} = 无） */
    public static final int DATA_DENY = 3;
    /** DataSlot[4..6] = 三格品质码（1/2/3；空槽 0） */
    public static final int DATA_TIER_FIRST = 4;
    /** DataSlot 总数（3 格品质码 ⇒ 4 + 3） */
    public static final int DATA_SLOT_COUNT = DATA_TIER_FIRST + OFFER_SLOTS;

    /** {@code ChipShopDenyPayload#reason}：无（初始态 / 未被拒） */
    public static final int DENY_NONE = -1;
    /** {@code ChipShopDenyPayload#reason}：余额不足（⇒ 客户端红闪**花费文本**） */
    public static final int DENY_NO_COIN = 0;
    /** {@code ChipShopDenyPayload#reason}：刷新次数为 0（⇒ 客户端红闪 **N/N 文本**） */
    public static final int DENY_NO_REFRESH = 1;
    /**
     * {@code ChipShopDenyPayload#reason}：物品栏已满（⇒ 客户端红闪**花费文本**）。
     * <p>⚠️ 2026-10-14（需求 2）：**购买路径不再使用本码** —— 满包购买改为「照常成交 + 装不下的筹码掉落
     * 在玩家身边」，故 {@link #handleBuy} 不会再下发本码。常量保留是因为：① 屏幕侧「花费文本」仍按 0/2
     * 两码判定（与 {@link #DENY_NO_COIN} 同相，守门 G23 按字面校验）；② 开店入口的「物品栏已满」拒绝
     * 是 {@code ChipShopOpener#hasNoRoom} 的**独立**分支，不经过本码。
     */
    public static final int DENY_INVENTORY_FULL = 2;

    /** 购买后的**禁止使用**冷却时长：6000 ticks = 5:00（需求 4） */
    private static final long BUY_COOLDOWN_TICKS = 6000L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ChipShopMenu.class);

    private final Player player;
    private final Inventory playerInventory;
    /** 隐藏候选容器（内容 = 附件候选的投影，靠原版槽位同步送到客户端） */
    private final SimpleContainer offerContainer = new SimpleContainer(OFFER_SLOTS);

    // ── 客户端镜像（服务端在 DataSlot#get 现算；客户端只读 set 同步下来的值）──
    private int clientPrice = ChipShopCatalog.PRICE_INITIAL;
    private int clientRefreshesLeft = ChipShopCatalog.REFRESH_INITIAL;
    private int clientTotalRefreshes = ChipShopCatalog.REFRESH_MAX;
    private int clientDenyReason = DENY_NONE;
    private final int[] clientTiers = new int[OFFER_SLOTS];

    /** 2 参构造 = {@link ModMenuTypes#CHIP_SHOP} 的工厂入口；服务端在此**只读**载入附件（不掷骰） */
    public ChipShopMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.CHIP_SHOP.get(), containerId);
        this.player = playerInventory.player;
        this.playerInventory = playerInventory;

        // ── 隐藏候选槽 3 个（先加：槽序号 0..2）──
        for (int i = 0; i < OFFER_SLOTS; i++) {
            addSlot(new OfferSlot(offerContainer, i, HIDDEN_X, HIDDEN_Y));
        }

        // ── 主栏 9x3（物品栏槽 9..35）：格网 (61 + 18i, 94 + 18j) + 1 px 内缩 ──
        addSlot(new Slot(playerInventory, 9, GRID_X + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 10, GRID_X + GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 11, GRID_X + 2 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 12, GRID_X + 3 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 13, GRID_X + 4 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 14, GRID_X + 5 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 15, GRID_X + 6 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 16, GRID_X + 7 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 17, GRID_X + 8 * GRID_PITCH + ITEM_INSET, GRID_Y + ITEM_INSET));
        // 第 1 行（物品栏槽 18..26）
        addSlot(new Slot(playerInventory, 18, GRID_X + ITEM_INSET, GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 19, GRID_X + GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 20, GRID_X + 2 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 21, GRID_X + 3 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 22, GRID_X + 4 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 23, GRID_X + 5 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 24, GRID_X + 6 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 25, GRID_X + 7 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 26, GRID_X + 8 * GRID_PITCH + ITEM_INSET,
                GRID_Y + GRID_PITCH + ITEM_INSET));
        // 第 2 行（物品栏槽 27..35）
        addSlot(new Slot(playerInventory, 27, GRID_X + ITEM_INSET, GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 28, GRID_X + GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 29, GRID_X + 2 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 30, GRID_X + 3 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 31, GRID_X + 4 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 32, GRID_X + 5 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 33, GRID_X + 6 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 34, GRID_X + 7 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));
        addSlot(new Slot(playerInventory, 35, GRID_X + 8 * GRID_PITCH + ITEM_INSET,
                GRID_Y + 2 * GRID_PITCH + ITEM_INSET));

        // ── 快捷栏 9x1（物品栏槽 0..8）：格网 y = 152 + 1 px 内缩 ──
        addSlot(new Slot(playerInventory, 0, GRID_X + ITEM_INSET, HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 1, GRID_X + GRID_PITCH + ITEM_INSET, HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 2, GRID_X + 2 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 3, GRID_X + 3 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 4, GRID_X + 4 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 5, GRID_X + 5 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 6, GRID_X + 6 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 7, GRID_X + 7 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));
        addSlot(new Slot(playerInventory, 8, GRID_X + 8 * GRID_PITCH + ITEM_INSET,
                HOTBAR_GRID_Y + ITEM_INSET));

        // ── 同步：DataSlot[0..6]（价格 / 剩余刷新 / 刷新上限 / 拒绝原因 / 三格品质码）──
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return player.level().isClientSide() ? clientPrice : ChipShopState.get(player).price();
            }

            @Override
            public void set(int value) {
                clientPrice = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return player.level().isClientSide() ? clientRefreshesLeft
                        : ChipShopState.get(player).refreshesLeft();
            }

            @Override
            public void set(int value) {
                clientRefreshesLeft = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return ChipShopCatalog.REFRESH_MAX;
            }

            @Override
            public void set(int value) {
                clientTotalRefreshes = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return clientDenyReason;
            }

            @Override
            public void set(int value) {
                clientDenyReason = value;
            }
        });
        for (int i = 0; i < OFFER_SLOTS; i++) {
            final int index = i;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return player.level().isClientSide() ? clientTiers[index]
                            : ChipShopState.get(player).offerTier(index);
                }

                @Override
                public void set(int value) {
                    clientTiers[index] = value;
                }
            });
        }

        if (!player.level().isClientSide()) {
            // 只读载入附件（**不掷骰**：重抽入口只有三处，见类头 / 计划表 §3）
            applyState(ChipShopState.get(player));
            if (player instanceof ServerPlayer serverPlayer) {
                StarCoinBalanceSync.forceResend(serverPlayer);
            }
        }
    }

    /**
     * 把附件状态投影到本菜单视图（**只读**：不掷骰、不改存档）：候选写入隐藏容器，
     * 价格 / 刷新次数 / 品质码由 DataSlot 现算（服务端）或同步值（客户端）读出。
     */
    private void applyState(ChipShopState state) {
        List<ChipShopCatalog.Entry> entries = state.entries();
        for (int i = 0; i < OFFER_SLOTS; i++) {
            offerContainer.setItem(i, i < entries.size() ? entries.get(i).stack() : ItemStack.EMPTY);
        }
    }

    // ────────────────────────────── 客户端只读接口（屏幕层用） ──────────────────────────────

    /** 单价（三格同价） */
    public int price() {
        return player.level().isClientSide() ? clientPrice : ChipShopState.get(player).price();
    }

    /** 剩余刷新次数 */
    public int remainingRefreshes() {
        return player.level().isClientSide() ? clientRefreshesLeft : ChipShopState.get(player).refreshesLeft();
    }

    /** 刷新次数上限（界面不再显示；仅作恢复逻辑的上限口径） */
    public int totalRefreshes() {
        return ChipShopCatalog.REFRESH_MAX;
    }

    /** 最近一次拒绝原因（{@link #DENY_NONE} = 无） */
    public int denyReason() {
        return player.level().isClientSide() ? clientDenyReason : DENY_NONE;
    }

    /** 第 index 格候选（越界 / 空槽 ⇒ 空栈；客户端读隐藏槽的同步内容） */
    public ItemStack offerStack(int index) {
        if (index < 0 || index >= OFFER_SLOTS) {
            return ItemStack.EMPTY;
        }
        return offerContainer.getItem(index);
    }

    /** 第 index 格品质码（服务端由候选现算、客户端读 DataSlot[4..6]；空槽 ⇒ 0） */
    public int offerTier(int index) {
        if (index < 0 || index >= OFFER_SLOTS) {
            return 0;
        }
        return player.level().isClientSide() ? clientTiers[index] : ChipShopState.get(player).offerTier(index);
    }

    /** 机器行 / 取证用：当前候选摘要（同一批任意次读到相同值） */
    public String offersKey() {
        return ChipShopState.get(player).offersKey();
    }

    // ────────────────────────────── 服务端结算 ──────────────────────────────

    /**
     * 购买（唯一入口，来自 {@code ChipShopBuyPayload}）。
     *
     * <p><b>校验顺序</b>（照抄 {@code CardShopMenu#handleBuy} 的口径，服务端为唯一权威）：
     * 菜单归属 → 索引范围 → 该格有候选 → 余额 → 扣款 → 发货（**先灌物品栏、装不下的剩余部分掉落在玩家身边**）→
     * **落在盘上的新状态（+1 次购买 / 记录类别 / 重抽候选）** → 关店。
     *
     * <p><b>需求 2（2026-10-14）</b>：物品栏装不下时**不再拒绝**（{@link #DENY_INVENTORY_FULL} 不再是购买路径的结果）——
     * 扣款、价格 +5（下次单价由购买次数现算）、购买次数 +1、重抽候选与关店一律照常，装不下的筹码用
     * {@code player.drop(...)} 弹到玩家身边。
     *
     * <p><b>满包判定的口径（2026-10-14 裁决）</b>：**生存 / 冒险模式**先逐格自判主物品栏（{@code items}，36 格）
     * **有无空格**，有空位才调 {@code Inventory#add} 并信任其落位，无空位则整枚 {@code player.drop(...)} 到玩家身边；
     * **创造模式遵循原版默认规则**（{@code buyer.getAbilities().instabuild} 为真 ⇒ 直接调 {@code Inventory#add}）：
     * 原版 {@code add} 在找不到空位时会走 {@code stack.setCount(0); return true;} 分支把整栈**吞掉**
     * （1.21.1 {@code Inventory#add} 的 {@code hasInfiniteMaterials()} 分支 / 1.20.1 的 {@code abilities.instabuild} 分支），
     * 这正是创造模式应有的语义。⇒ **创造满包：被吞、不掉落、无提示**（{@code dropped=false}）；
     * **生存满包：掉落 + 提示**（{@code dropped=true}）；有空位时两模式都绝不掉落。
     *
     * <p><b>R18</b>：购买后**自动关店**（{@link Player#closeContainer()}），筹码优先直接进物品栏
     * （不含副手 —— 用户裁决 Q10）；装不下时落在地上。
     */
    public void handleBuy(ServerPlayer buyer, int offerIndex) {
        if (buyer != this.player || buyer.level().isClientSide()) {
            return;
        }
        if (offerIndex < 0 || offerIndex >= OFFER_SLOTS) {
            return;
        }
        ChipShopState state = ChipShopState.get(buyer);
        ItemStack chip = offerContainer.getItem(offerIndex);
        if (chip.isEmpty() || offerIndex >= state.offers().size()) {
            return; // 该格无候选（越界包 / 重复包 / 已重抽）⇒ 静默拒绝
        }
        int price = state.price();
        if (!StarEngineEconomy.isAvailable() || !StarEngineEconomy.hasBalance(buyer, price)) {
            deny(buyer, DENY_NO_COIN, "msg.astral_dice.chip_shop_no_coin", price);
            return;
        }
        if (!StarEngineEconomy.withdraw(buyer, price)) {
            deny(buyer, DENY_NO_COIN, "msg.astral_dice.chip_shop_no_coin", price);
            return;
        }
        ItemStack bought = chip.copy();
        // 需求 2（2026-10-14）：物品栏放不下**不再拒绝**。**创造模式遵循原版默认规则**（用户裁决）：
        //   直接走原版 `add` —— `instabuild` 下找不到空位时它走 `stack.setCount(0); return true;` 把整栈**吞掉**
        //   （不掉落、无提示），这正是创造模式应有的语义 ⇒ 创造模式**不**做自判、**不**掉落。
        //   **生存 / 冒险模式**：先自判主物品栏有无空格（原版 `add` 会先把同 id 的未满栈补满、再找空格），
        //   有空位走 `add`，无空位则整枚 `drop` 到玩家身边。
        // ⚠️ 生存模式**不能**用 `add` 的返回值判空位（创造模式的吞栈分支恒返回 `true`）；筹码一律 `stacksTo(1)`
        //   ⇒「存在空格」等价于「装得下」。
        boolean dropped;
        if (buyer.getAbilities().instabuild) {
            // 创造模式：原版默认语义（满包时被吞 ⇒ 不掉落、不发提示）
            playerInventory.add(bought);
            dropped = false;
        } else {
            boolean hasRoom = false;
            for (ItemStack slotStack : playerInventory.items) {
                if (slotStack.isEmpty()) {
                    hasRoom = true;
                    break;
                }
            }
            if (hasRoom) {
                playerInventory.add(bought);
                dropped = false;
            } else {
                buyer.drop(bought, false);
                dropped = true;
            }
        }

        // 落盘：购买次数 +1、记录本次类别（下次抽取的类别偏置来源），并**重抽候选**
        // ⚠️ 类别取 `chip`（未被 add 消耗）而非 `bought`：`add` 成功时 `bought` 已 setCount(0) ⇒ isEmpty，
        //   而 ChipShopCatalog.byStack 对空栈返 empty ⇒ 用 bought 会**丢掉**这次购买的类别记录。
        ChipShopState next = state.withPurchased(ChipShopCatalog.categoryOf(chip).orElse(null));
        next = next.withOffers(ChipShopRoller.roll(buyer, next.lastCategory().orElse(null)));
        // 需求 4：购买成功后进入 5:00 冷却（世界 gameTime 现算），冷却期内禁止再开店
        next = next.withCooldown(buyer.level().getGameTime() + BUY_COOLDOWN_TICKS);
        ChipShopState.store(buyer, next);
        applyState(next);
        StarCoinBalanceSync.notifyChanged(buyer);
        actionBar(buyer, ChatFormatting.GOLD, "msg.astral_dice.chip_shop_bought", chip.getHoverName());
        if (dropped) {
            // 需求 2：满包成交时补一条提示（同一时刻**后发** ⇒ 覆盖「已购买」成为玩家可见的 actionbar）
            actionBar(buyer, ChatFormatting.YELLOW, "msg.astral_dice.chip_shop_chip_dropped");
        }
        LOGGER.info("[astral_dice] CHIP_SHOP_BUY player={} offer={} chip={} price={} purchased={} refreshes={}"
                        + " offers={} dropped={} cooldownUntil={}",
                buyer.getName().getString(), offerIndex, chip.getItem(), price, next.purchasedCount(),
                next.refreshesLeft(), next.offersKey(), dropped, next.cooldownUntil());
        // R18：购买成功后自动关店（关店后玩家看到的是重抽后的新一批，下次进店一致）
        buyer.closeContainer();
    }

    /**
     * 刷新候选（唯一入口，来自 {@code ChipShopRefreshPayload}）：次数 −1 并重抽，候选与次数一起落盘。
     *
     * <p><b>锚点</b>（用户裁决 2）：**第一次成功刷新**时若锚点还是 0 ⇒ 记 {@code now + 36000}，
     * 之后由 {@link ChipShopRegenTicker} 每 36000 tick +1（上限 {@value ChipShopCatalog#REFRESH_MAX}）。
     * 次数为 0 时**静默拒绝 + 红闪**（客户端同样不发包，这里是服务端兜底）。
     */
    public void handleRefresh(ServerPlayer buyer) {
        if (buyer != this.player || buyer.level().isClientSide()) {
            return;
        }
        ChipShopState state = ChipShopState.get(buyer);
        if (state.refreshesLeft() <= 0) {
            deny(buyer, DENY_NO_REFRESH, "msg.astral_dice.chip_shop_no_refresh");
            return;
        }
        long now = buyer.level().getGameTime();
        long anchor = state.regenAnchor() == 0L ? now + ChipShopCatalog.REFRESH_REGEN_TICKS : state.regenAnchor();
        ChipShopState next = state.withRefreshes(state.refreshesLeft() - 1, anchor);
        next = next.withOffers(ChipShopRoller.roll(buyer, next.lastCategory().orElse(null)));
        ChipShopState.store(buyer, next);
        applyState(next);
        LOGGER.info("[astral_dice] CHIP_SHOP_REFRESH player={} refreshes={}/{} anchor={} offers={}",
                buyer.getName().getString(), next.refreshesLeft(), ChipShopCatalog.REFRESH_MAX, next.regenAnchor(),
                next.offersKey());
    }

    /** 拒绝信号：actionbar 红字 + S2C 拒绝包（客户端据此红闪 30t；服务端为唯一权威） */
    private void deny(ServerPlayer target, int reason, String key, Object... args) {
        this.clientDenyReason = reason;
        if (key != null) {
            actionBar(target, ChatFormatting.RED, key, args);
        }
        ModNetwork.sendToPlayer(target, new ChipShopDenyPayload(reason));
    }

    private void actionBar(Player target, ChatFormatting color, String key, Object... args) {
        if (!(target instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ModNetwork.sendToPlayer(serverPlayer, new ModNetwork.ActionBarMessage(
                Component.translatable(key, args).withStyle(color),
                GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    // ────────────────────────────── 菜单基类实现 ──────────────────────────────

    /**
     * Shift-click：候选槽（0..2）一律不参与；玩家 36 格 ⇒ 主栏（相对 0..26）与快捷栏（相对 27..35）对倒。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < OFFER_SLOTS || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int relative = index - OFFER_SLOTS;
        boolean fromHotbar = relative >= MAIN_SIZE;
        boolean moved = fromHotbar
                ? moveItemStackTo(stack, OFFER_SLOTS, OFFER_SLOTS + MAIN_SIZE, false)
                : moveItemStackTo(stack, OFFER_SLOTS + MAIN_SIZE, OFFER_SLOTS + PLAYER_SLOT_COUNT, false);
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
     * 恒 true：本菜单没有可判距的方块来源（入口方块由 {@link ChipShopOpener} 负责校验），
     * 故不设方块型菜单的距离契约；购买 / 刷新的二次校验一律在服务端回调里做（服务端权威）。
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** 候选槽：只读展示（不可拿取、不可放置、单格 1 枚）—— 拖拽 / 双击都拿不走候选（计划表 §5） */
    private static class OfferSlot extends Slot {

        OfferSlot(SimpleContainer container, int index, int x, int y) {
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
