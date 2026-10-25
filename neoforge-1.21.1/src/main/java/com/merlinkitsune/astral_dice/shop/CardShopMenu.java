package com.merlinkitsune.astral_dice.shop;

import com.merlinkitsune.astral_dice.audio.ModSounds;
import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.economy.StarCoinBalanceSync;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.item.chip.AtmChipItem;
import com.merlinkitsune.astral_dice.item.chip.MemberRecommendationChipItem;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import com.merlinkitsune.astral_dice.network.CardShopDenyPayload;
import com.merlinkitsune.astral_dice.network.CardShopTransferListPayload;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p><b>兑换/转账走「菜单按钮」</b>（原版 {@code clickMenuButton}，客户端
 * {@code gameMode.handleInventoryButtonClick}）；<b>购买走拖拽</b>（2026-10-08 v4：客户端把卡面拖到玩家
 * 物品格后发 {@code CardShopBuyPayload}，服务端只在 {@link #handleBuy} 内一次性校验与结算）——
 * 玩家的物品栏操作仍走原版槽位协议，二者不混用。
 *
 * <p><b>限购</b>：每个货架格 1 次（{@link #sold} 本次上架标记）—— 由 60 秒轮询重新抽架重置，
 * 不落盘（用户裁决：非永久限购；2026-10-09 用户追加裁决：**买牌数量与次数不限**，限购只针对「同一张已售出的格」）。
 *
 * <p><b>反刷取</b>：货架格 {@link ShelfSlot#mayPickup} 恒 false ⇒ 卡牌**永不**进入原版「光标携带」机制，
 * 只在 {@link #handleBuy} 扣款成功后由服务端直接写入目标玩家槽；关界面 / 丢出界面都无法白拿。
 *
 * <p><b>C2S 载荷</b>：转账需要「目标索引 + 金额」、购买需要「货架索引 + 目标格索引」，原版按钮包只有
 * 一个 int，故各自单独走 {@code CardShopTransferPayload} / {@code CardShopBuyPayload}（其余按钮仍用原版按钮包）；
 * 双击「快速移入物品栏」复用 {@code CardShopBuyPayload}，{@code targetSlot ==}{@link #QUICK_MOVE_SLOT}(-1) 表示
 * 由服务端**先并入同类未满栈、再**挑第一个空物品栏格（口径与拖拽落点完全一致）。
 *
 * <p><b>60 秒轮询（2026-10-09 用户裁决）</b>：全店只有**一个**共享计数器（{@link #TRANSFER_COOLDOWN_SECONDS} 秒），
 * 到 0 的那一刻同时 ① 转账恢复可用 ② 重新抽架上新牌；计数器由「就绪态下的购买成功 / 转账成功」开启，
 * **进行中不重开** ⇒ 到点后必定出现「就绪」状态并一直保持，直到下一次购买或转账。关闭 / 重开界面
 * **不影响**计数（{@link #removed} 不再顺延，见其注释）。触发「商店大酬宾」时**只补差额牌
 * （+{@value CardShopCatalog#PROMOTION_BONUS_SLOTS} 促销格）、完全不动计数器**（{@link #triggerPromotion}）
 * —— ⚠️ 任务V（2026-10-10 审计 D1/D2 订正）：本类头原先写的是「触发大酬宾时立即抽架并把计数器归 0」，
 * 那是已被推翻的旧口径（任务U′ 起大酬宾不换批、不归零）。计数器真值是玩家附件 {@code shop_transfer_cooldown_end}，
 * 客户端只读 DataSlot[6] / [8] 的同步值，**不得自行计时**。
 *
 * <p><b>补差额牌（任务U′，2026-10-10 用户裁决）</b>：装备经商立牌、或正处于「商店大酬宾」窗口时，
 * **不重抽、不换批、也不动 60 秒计时器**，只把货架**补足**到当前口径应有的格数（基础
 * {@value CardShopCatalog#BASE_SLOTS} + 促销 {@value CardShopCatalog#PROMOTION_BONUS_SLOTS} + 团队
 * {@value CardShopCatalog#TEAM_BONUS_SLOTS}，见 {@link CardShopShelf#topUp}）—— 已有卡牌、售价、已售标记与
 * 「本批进店奖励已发」标记全部原样保留。立牌取下 / 窗口过期**不缩架**，要等沙漏归 0 那次重抽才按当时
 * 口径重排。
 *
 * <p><b>三条补差入口，同一条判定</b>（任务V §1.2，2026-10-10 用户裁决「全做成立即补差也无妨（代码层面更统一，
 * 避免 2 种不同判定）」）：开店（{@link #initShelf}）、大酬宾事件触发（{@link #triggerPromotion}）、
 * **界面已经开着时的 tick 复检**（{@link #topUpIfNeeded}，由 {@link CardShopCycleTicker} 每
 * {@value CardShopCycleTicker#TOPUP_CHECK_INTERVAL_TICKS} tick 调一次）—— 三者都只走
 * {@link #topUpShelf}，缺口判定唯一（{@link CardShopShelf#targetSize} 与当前格数之差），幂等且无缺口不写盘。
 */
public class CardShopMenu extends AbstractContainerMenu {

    public static final int SHELF_SIZE = CardShopCatalog.MAX_SLOTS;

    private static final int HIDDEN_X = -10000;
    private static final int HIDDEN_Y = -10000;

    /**
     * 玩家物品格第一格（主物品栏第 1 行第 1 列）槽位坐标 = 物品图标与槽位高亮的绘制点。
     * 2026-10-08 v4 修正：原值 (115,100) 与背景贴图实测内格 (117+18j, 102+18r) 差 2px，改为实测值，
     * 使「渲染出的物品」正好落在 shop_item_inventory.png 的实测内格里（拖拽落点判定同口径）。
     */
    public static final int INVENTORY_X = 117;
    public static final int INVENTORY_Y = 102;
    /** 快捷栏（物品格第 4 行）y：实测第 3 行与快捷栏之间有 5px 间隙（138+16 → 160），故单独给常量 */
    public static final int INVENTORY_HOTBAR_Y = 160;
    /** 货架格内卡面的绘制坐标（屏幕层用，菜单层只作常量声明；bg 绝对 (10+46i, 31) 的 i=0 即 (10,31)） */
    public static final int SHELF_ITEM_X = 10;
    public static final int SHELF_ITEM_Y = 31;

    public static final int BUTTON_TRANSFER = 16;             // 打开二级转账视图
    public static final int BUTTON_EXCHANGE = 17;             // 星光兑换星币（原经商立牌主动技能）
    public static final int BUTTON_REFRESH_TRANSFER = 18;     // 刷新候选玩家名单

    public static final int MIN_TRANSFER = 5;
    public static final int MAX_TRANSFER = 20;
    /** 本轮 60 秒轮询的长度（秒）：到 0 时 转账解锁 + 重新抽架 */
    public static final int TRANSFER_COOLDOWN_SECONDS = 60;

    /** 双击「快速移入物品栏」哨兵：{@code CardShopBuyPayload#targetSlot} 取它 ⇒ 服务端先并同类未满栈、再自动挑第一个空物品栏格 */
    public static final int QUICK_MOVE_SLOT = -1;
    /** {@code CardShopDenyPayload#reason}：余额不足（买牌 / 转账被拒 ⇒ 客户端数字红白闪 3 次） */
    public static final int DENY_NO_COIN = 0;
    /** {@code CardShopDenyPayload#reason}：物品栏已满（双击快速移入失败） */
    public static final int DENY_INVENTORY_FULL = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger(CardShopMenu.class);
    /** 经商立牌被动第 1 条(2026-10-08):佩戴者每次进入商店获得的星币数。任务Q §14 起方块路径**每批卡至多一次**；
     *  任务T §3 起经商立牌主动技能路径**必定**发 1 枚（两条路径互斥，不叠加） */
    private static final int PARUNAN_ENTER_COIN = 1;
    /** 经商立牌被动第 5 条(2026-10-08;用户当日更正 +20% → **+50%**):星光兑换的额外收入比例(下限为 1 枚) */
    private static final float PARUNAN_EXCHANGE_BONUS_RATE = 0.50F;
    /** ATM 机筹码(2026-10-08 重写):转账发起者装备时,双方各获得的星光 / 星币 */
    private static final int ATM_TRANSFER_STARLIGHT = 1;
    private static final int ATM_TRANSFER_COINS = 2;

    /**
     * DataSlot[0..5] = 货架格显示码；DataSlot[6] = 本轮剩余秒数（0 = 就绪）；
     * DataSlot[7] = 大酬宾窗口是否激活；DataSlot[8] = 本轮 60 秒轮询是否进行中（= 转账锁）。
     */

    private final Player player;
    /** 任务T §3：本次开店是否由经商立牌主动技能打开（true ⇒ 无视本批已领取标记，必发 1 枚并置为已领取） */
    private final boolean viaActiveSkill;
    private final Inventory playerInventory;
    private final SimpleContainer shelfContainer = new SimpleContainer(SHELF_SIZE);
    private final int[] prices = new int[SHELF_SIZE];
    private final boolean[] discounted = new boolean[SHELF_SIZE];
    private final boolean[] sold = new boolean[SHELF_SIZE];
    private int shelfSize = 0;
    /** 客户端读到的本轮剩余秒数（服务端在 {@link #computeTransferCooldownSeconds()} 现算；0 = 就绪） */
    private int transferCooldownSeconds = 0;
    /** 客户端读到的「商店大酬宾」窗口状态（决定标题带是否播 8t 流光；服务端 DataSlot[7] 同步） */
    private boolean promotionActive = false;
    private List<UUID> candidateIds = List.of();
    /**
     * 客户端读到的「本轮 60 秒轮询是否进行中」（服务端真值 = 附件终点 &gt; 当前 gameTime，
     * 由 {@link #computeCycleRunning()} 现算并经 DataSlot[8] 同步）⇒ 中按钮立刻灰化、二级视图自动退回。
     * 2026-10-09 用户裁决：语义由「本店会话是否用过转账」改写为「本轮轮询进行中」——
     * 购买成功同样开启本轮（买牌不限次数，但**一轮只能用一次转账**）。
     */
    private boolean cycleRunning = false;

    /** 2 参构造 = {@link ModMenuTypes#CARD_SHOP} 的工厂入口（客户端实例的 viaActiveSkill 无意义：initShelf 只在服务端执行） */
    public CardShopMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, false);
    }

    /**
     * @param viaActiveSkill true = 由经商立牌主动技能「网络购物」打开（任务T §3）
     */
    public CardShopMenu(int containerId, Inventory playerInventory, boolean viaActiveSkill) {
        super(ModMenuTypes.CARD_SHOP.get(), containerId);
        this.player = playerInventory.player;
        this.playerInventory = playerInventory;
        this.viaActiveSkill = viaActiveSkill;

        // 货架格（隐藏坐标；贴图与卡面位置由屏幕层负责绘制）
        for (int i = 0; i < SHELF_SIZE; i++) {
            addSlot(new ShelfSlot(shelfContainer, i, HIDDEN_X, HIDDEN_Y));
        }
        // 玩家物品格 9x4：r = 0..2 主物品栏（槽 9..35）、r = 3 快捷栏（槽 0..8）
        // 添加顺序 = 主物品栏 3 行 → 快捷栏 1 行；物品栏索引 ↔ 菜单槽序号的换算见 {@link #playerSlotOffset(int)}
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int x = INVENTORY_X + col * 18;
                int y = row == 3 ? INVENTORY_HOTBAR_Y : INVENTORY_Y + row * 18;
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
        // 第 8 个 DataSlot：大酬宾窗口是否激活（2026-10-08 v4 新增——原实现把该布尔留在服务端，
        // 客户端无从判断 ⇒ 标题带流光会一直在非活动期播放）
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return computePromotionActive();
            }

            @Override
            public void set(int value) {
                promotionActive = value != 0;
            }
        });
        // 第 9 个 DataSlot：本轮 60 秒轮询是否进行中（2026-10-09 用户裁决改写；客户端靠它
        // 在成功转账/购买后把中按钮画成不可用态、让二级视图自动退回主界面；0 = 就绪、1 = 进行中）
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return computeCycleRunning() ? 1 : 0;
            }

            @Override
            public void set(int value) {
                cycleRunning = value != 0;
            }
        });

        if (!player.level().isClientSide()) {
            if (player instanceof ServerPlayer serverPlayer) {
                StarCoinBalanceSync.forceResend(serverPlayer);
            }
            initShelf();
        }
    }

    // ────────────────────────────── 开店抽池 / 轮询上新 ──────────────────────────────

    /**
     * 开店初始化（任务O §1.3）：载入玩家货架存档、按需**补差额牌**，再结算「经商立牌进店 +1 星币」。
     * **常规绝不重抽货架** —— 换牌的唯一常规时机是服务端 tick 在「沙漏归 0」那一刻的重抽
     * （{@link CardShopCycleTicker}）。存档缺失（首次使用 / 旧存档升级）时补抽一次并落盘，此后每次开店
     * 都读到同一批卡。
     *
     * <p>⚠️ 2026-10-09 用户裁决（原话「重新进商店仍然会刷新卡牌……换牌时机永远只能是沙漏刚好归 0 那一刻」）：
     * 本方法不得调用 {@link #rerollShelf()}。
     *
     * <p>⚠️ 任务U′（2026-10-10 用户裁决）：装备经商立牌 / 处于大酬宾窗口时，开店只**补差额牌**
     * （{@link #topUpShelf}）—— 已有牌与 60 秒计时器一律不动，与「换牌时机」的裁决不冲突。
     */
    private void initShelf() {
        CardShopShelf shelf = CardShopShelf.get(player);
        if (!shelf.initialized()) {
            shelf = CardShopShelf.roll(player);
            CardShopShelf.store(player, shelf);
        } else {
            // 任务U′（用户裁决）：装备经商立牌 / 大酬宾窗口 ⇒ **只补上牌缺口**（已有牌与计时器一律不动）
            shelf = topUpShelf(shelf, "open");
        }
        applyShelf(shelf);
        // 经商立牌被动「传奇商人」第 1 条(2026-10-08 用户裁决,**仅佩戴者本人**):进入商店时获得 1 星币
        // ⚠️ 任务T §3（用户裁决）追加例外：**主动技能**进店（viaActiveSkill=true）必定发 1 枚，不看本批是否已领过。
        // ⚠️ 任务Q §14 封堵（用户报「经商立牌反复进店刷钱」）：**同一批卡只能发一次**。
        //   真值存放在货架存档的 enterRewardPaid 位上（随玩家存档持久化），**不是**「本次开店」这种
        //   随界面销毁的一次性状态：
        //     · 同一批卡期间反复开关界面 ⇒ 第二次起这里直接不发 —— 原先每次开店都无条件 +1 星币，
        //       正是「反复进出店刷钱」的漏洞；
        //     · 沙漏归 0 后由 CardShopCycleTicker 重抽 ⇒ CardShopShelf.roll 造出**新记录**
        //       （该位 = false）⇒ 只有换了新一批卡，进店才允许再得 1 枚；
        //     · 大酬宾补差额牌（topUpShelf）**不换批** ⇒ 本批标记原样保留，促销不会多发一枚。
        //   两条固定格式的机器行（granted=1 / granted=0）供实机断言读取，batch 即本批卡的标识。
        if (ParunanSignItem.isEquipped(player)) {
            String name = player.getName().getString();
            if (viaActiveSkill) {
                // 任务T §3（用户裁决，原话「使用主动技能进商店必定获得 1 星币（不与商店本身叠加），同时将商店
                // 当前状态改为已经领取」）：主动技能这条路径**不看** enterRewardPaid —— 之前用方块进店领过也照发；
                // 发完置位，避免随后再开界面重复领取。与下方 else-if 分支互斥 ⇒ 一次开店只发 1 枚，绝不叠加。
                ResourceConversion.giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), PARUNAN_ENTER_COIN));
                actionBar(player, ChatFormatting.GOLD, "msg.astral_dice.card_shop_parunan_enter", PARUNAN_ENTER_COIN);
                CardShopShelf.store(player, shelf.withEnterRewardPaid(true));
                LOGGER.info("[astral_dice] CARD_SHOP_ENTER_REWARD player={} granted={} reason=active_skill"
                        + " batch={} balance={}", name, PARUNAN_ENTER_COIN, shelf.batchId(),
                        StarEngineEconomy.getBalance(player));
            } else if (shelf.enterRewardPaid()) {
                LOGGER.info("[astral_dice] CARD_SHOP_ENTER_REWARD player={} granted=0 reason=already_paid_this_batch"
                        + " batch={} balance={}", name, shelf.batchId(), StarEngineEconomy.getBalance(player));
            } else {
                ResourceConversion.giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), PARUNAN_ENTER_COIN));
                actionBar(player, ChatFormatting.GOLD, "msg.astral_dice.card_shop_parunan_enter", PARUNAN_ENTER_COIN);
                CardShopShelf.store(player, shelf.withEnterRewardPaid(true));
                LOGGER.info("[astral_dice] CARD_SHOP_ENTER_REWARD player={} granted={} reason=first_of_batch"
                        + " batch={} balance={}", name, PARUNAN_ENTER_COIN, shelf.batchId(),
                        StarEngineEconomy.getBalance(player));
            }
        }
    }

    /**
     * 把货架存档载入本菜单视图（**只读**：不改存档、不动随机数）。
     * 存档格数可能少于 {@link #SHELF_SIZE}（大酬宾/队伍加成的格数是动态的）⇒ 其余格一律清空。
     */
    private void applyShelf(CardShopShelf shelf) {
        shelfSize = shelf.size();
        for (int i = 0; i < SHELF_SIZE; i++) {
            shelfContainer.setItem(i, shelf.stackAt(i));
            sold[i] = shelf.soldAt(i);
            discounted[i] = shelf.discountedAt(i);
            prices[i] = i < shelfSize ? shelf.priceAt(i) : 0;
        }
    }

    /**
     * 立即重抽 + **落盘**（清 {@link #sold} ⇒ 上一轮买走的格子重新上架）+ 应用到本菜单视图。
     *
     * <p>⚠️ 任务U′（2026-10-10 用户裁决）起**已无调用点**：大酬宾不再换批（改走 {@link #topUpShelf}），
     * 开店也只补差额（同左），常规换牌由服务端 tick 在「沙漏归 0」那一刻驱动（{@link CardShopCycleTicker}）。
     * 保留本方法作为「换一批新牌」的唯一显式入口（调试 / 未来事件卡真要重抽时用）。
     */
    public void rerollShelf() {
        CardShopShelf shelf = CardShopShelf.roll(player);
        CardShopShelf.store(player, shelf);
        applyShelf(shelf);
    }

    /**
     * 服务端 ticker 在「沙漏归 0」重抽并落盘之后调用：把本菜单视图切到新货架（**不重抽**）。
     * 界面开着时客户端会在下一次槽位广播后看到新牌；界面关着时不影响任何事。
     */
    public void reloadShelf() {
        CardShopShelf shelf = CardShopShelf.get(player);
        if (shelf.initialized()) {
            applyShelf(shelf);
        }
    }

    // ────────────────────────── 补差额牌（任务U′，2026-10-10 用户裁决） ──────────────────────────

    /**
     * 把货架补足到当前口径应有的格数（装备经商立牌 +1 / 大酬宾窗口 +2）并落盘。
     *
     * <p><b>与已被推翻的「强制刷新」的区别</b>（用户原话「原本牌和计时器不动，改为立即补差额牌
     * （即补上牌缺口）。其他逻辑照旧」）：不重抽、不换批 ⇒ 已有卡牌、售价、已售标记与「本批进店奖励已发」
     * 标记全部原样保留，60 秒计时器**完全不动**；立牌取下 / 窗口过期也不缩架，要等下一次重抽（沙漏归 0，
     * {@link CardShopCycleTicker}）才按当时口径重排。
     *
     * @param reason 机器行里的来源标记（{@code open} = 开店 / {@code promotion} = 大酬宾事件触发）
     * @return 补过差额的货架；没有缺口时原样返回（同一实例）
     */
    private CardShopShelf topUpShelf(CardShopShelf shelf, String reason) {
        CardShopShelf topped = CardShopShelf.topUp(player, shelf);
        if (topped == shelf) {
            return shelf;
        }
        CardShopShelf.store(player, topped);
        LOGGER.info("[astral_dice] CARD_SHOP_SHELF_TOPUP player={} reason={} tick={} slots_before={} slots_after={}"
                        + " target={} batch={} prices={}",
                player.getName().getString(), reason, player.level().getGameTime(), shelf.size(), topped.size(),
                CardShopShelf.targetSize(player), topped.batchId(), topped.prices());
        return topped;
    }

    /**
     * 任务V §1.2（2026-10-10 用户裁决）：**界面已经开着**时也要立即补差额牌。
     *
     * <p>用户原话「当然要覆盖，因为『你不可能开商店的同时去装备会拆下立牌』。但是要注意，你开商店的状态下
     * 是极有可能获得大酬宾事件的。所以全做成立即补差也无妨（代码层面更统一，避免 2 种不同判定）」——
     * 因此这里**复用**开店 / 大酬宾那条 {@link #topUpShelf} 路径：全代码只有一种「补差」判定
     * （{@link CardShopShelf#targetSize} 与当前格数的缺口），不存在「开店才补 / 界面开着不补」的分支。
     *
     * <p>幂等：没有缺口时 {@link CardShopShelf#topUp} 返回同一实例 ⇒ 不落盘、不打机器行；有缺口时才落盘
     * （机器行 reason = {@code tick}）并把本菜单视图切到新货架。调用点只有服务端 tick
     * （{@link CardShopCycleTicker}，每 {@code TOPUP_CHECK_INTERVAL_TICKS} tick 复检）⇒ 自己或队友在界面开着时
     * 装备 / 卸下经商立牌、或大酬宾窗口到来，都会在半秒内补齐；卸下立牌 / 窗口过期**不缩架**（无缺口即不动）。
     *
     * @param reason 机器行里的来源标记（见 {@link #topUpShelf}）
     */
    public void topUpIfNeeded(String reason) {
        if (player.containerMenu != this) {
            return; // 本菜单已不是该玩家的当前菜单（正在关闭 / 已切换）⇒ 什么都不做
        }
        CardShopShelf shelf = CardShopShelf.get(player);
        if (!shelf.initialized()) {
            return; // 还没抽过（首次使用）：交给开店流程抽整批并落盘
        }
        CardShopShelf topped = topUpShelf(shelf, reason);
        if (topped != shelf) {
            applyShelf(topped);
        }
    }

    // ────────────────────────────── 按钮入口 ──────────────────────────────

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player != this.player) {
            return false;
        }
        if (id == BUTTON_EXCHANGE) {
            if (player instanceof ServerPlayer serverPlayer) {
                tryExchange(serverPlayer);
            }
            return true;
        }
        if (id == BUTTON_TRANSFER) {
            // 打开二级转账视图：现算并下发候选名单（UUID + 名 + 余额）。
            // ⚠️ 旧实现漏了这一支（clickMenuButton 只认 17/18）⇒ 客户端进转账视图后名单恒为空，
            // 必须靠点搜索框触发 BUTTON_REFRESH_TRANSFER 才有人，本次补齐。
            if (player instanceof ServerPlayer serverPlayer) {
                sendTransferCandidates(serverPlayer);
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

    /**
     * 拖拽购买落定（服务端权威）：玩家把货架第 {@code shelfIndex} 格的卡拖到**玩家物品栏第
     * {@code targetSlot} 格**并松手时调用（客户端 {@code CardShopBuyPayload} → 本方法）。
     *
     * <p><b>校验顺序</b>（任一失败即中止，**不消耗任何资源**）：
     * <ol>
     *   <li>菜单归属（{@code buyer == this.player}）且非客户端；</li>
     *   <li>货架索引范围 + 该槽本次开店真的有货（{@code shelfIndex < shelfSize}）且未售出；</li>
     *   <li>目标格索引范围（0..35），或 {@link #QUICK_MOVE_SLOT}(-1) = 双击快速移入（服务端先挑可并入的同类栈，
     *       再挑第一个空物品栏格）；</li>
     *   <li>槽内确实还有卡；</li>
     *   <li>**装得下**（2026-10-21 卡牌自动堆叠）：按 {@link #planPlacement} 的顺序（目标格同类栈 → 其余同类未满栈
     *       → 空格）能容纳整张卡的张数；目标格是**不同物品**且无处可并 ⇒ 仍按既有的「目标格被占用」拒绝；</li>
     *   <li>余额 ≥ 本槽当前价（价格只取服务端 {@link #prices}，**忽略客户端任何数值**）。</li>
     * </ol>
     *
     * <p><b>结算</b>：扣款 → 按落位计划写入玩家物品栏（同类栈 {@code grow} 补满 / 空格 {@link Slot#set} 开新栈，
     * 不经原版「光标携带」机制，见 {@link ShelfSlot#mayPickup}）→ 清空货架槽并置 {@link #sold}（该格本轮已售）
     * → 就绪态下开启本轮 60 秒轮询 → 双端刷新余额 → actionbar 提示。
     * 重复包 / 并发包由第 2 步的 {@code sold[]} 幂等挡住（同槽只会成功一次）。
     */
    public void handleBuy(ServerPlayer buyer, int shelfIndex, int targetSlot) {
        if (buyer != this.player || buyer.level().isClientSide()) {
            return;
        }
        if (shelfIndex < 0 || shelfIndex >= SHELF_SIZE || shelfIndex >= shelfSize || sold[shelfIndex]) {
            return;
        }
        boolean quickMove = targetSlot == QUICK_MOVE_SLOT;
        if (!quickMove && (targetSlot < 0 || targetSlot >= 36)) {
            return;
        }
        ItemStack card = shelfContainer.getItem(shelfIndex);
        if (card.isEmpty()) {
            return;
        }
        // 落位时**真正入包**的物品(§4.3 怪力乱神佩戴时的「路障 ⇒ 强化拒止」)统一由
        // LinglingSignManager#resolvePlacedItem 解析:客户端 CardShopScreen#canAcceptDrop 调的是同一个方法
        // ⇒ 「拖到已有强化拒止栈上」不再被客户端按货架原物误拦,转换判定只有一份。
        // (2026-10-21 卡牌自动堆叠:转换在落位判定之前 ⇒ 同类比较用的是真正入包的那张牌。)
        ItemStack bought = com.merlinkitsune.astral_dice.item.sign.LinglingSignManager
                .resolvePlacedItem(card, buyer).copy();
        if (quickMove) {
            // 双击「快速移入物品栏」（2026-10-09 §3）：由**服务端**挑落点 —— 先并同类未满栈，再取第一个空格；
            // 满包 ⇒ 返回 DENY_INVENTORY_FULL 并触发「背包已满」提示（2026-10-21 D2 起不再静默拒绝），
            // 同时让客户端闪余额数字（§4 同一条拒绝通道）。
            targetSlot = findQuickMoveSlot(bought);
            if (targetSlot < 0) {
                notifyDeny(buyer, DENY_INVENTORY_FULL);
                return;
            }
        }
        // 2026-10-21 卡牌自动堆叠：**扣款前**先算完整落位计划（判定与写入共用同一套顺序），
        // 装不下 ⇒ 直接拒绝（绝不先扣钱再丢卡）。
        List<int[]> plan = planPlacement(bought, targetSlot, bought.getCount());
        if (plan == null) {
            // 2026-10-21 D2:区分两种拒绝 —— 目标格被**不同物品**占着 ⇒ 既有「目标格被占用」文本;
            // 其余装不下的情形(目标格是**已满同类栈**且别处无空间 / 空格用尽)都是**背包已满**,
            // 复用既有 DENY_INVENTORY_FULL 拒绝通道(不新增 lang key)。
            if (quickMove || !isTargetSlotOccupied(bought, targetSlot)) {
                notifyDeny(buyer, DENY_INVENTORY_FULL);
            } else {
                actionBar(buyer, ChatFormatting.RED, "msg.astral_dice.card_shop_slot_occupied");
            }
            return;
        }
        int price = prices[shelfIndex];
        if (price < 0) {
            return;
        }
        // 价格可为 0(会员推荐信 −1 与降价叠加;用户 2026-10-08 明确「价格降低至 0」)⇒ 不走经济接口,直接发货
        if (price > 0) {
            if (!StarEngineEconomy.isAvailable() || !StarEngineEconomy.hasBalance(buyer, price)) {
                actionBar(buyer, ChatFormatting.RED, "msg.astral_dice.card_shop_no_coin", price);
                notifyDeny(buyer, DENY_NO_COIN);
                return;
            }
            if (!StarEngineEconomy.withdraw(buyer, price)) {
                actionBar(buyer, ChatFormatting.RED, "msg.astral_dice.card_shop_no_coin", price);
                notifyDeny(buyer, DENY_NO_COIN);
                return;
            }
        }
        // 2026-10-21 卡牌自动堆叠：按计划「先并入同类栈（补满到各自上限）、再逐格开新栈」；不再直接写死目标格。
        ItemStack boughtForNotify = bought.copy();
        applyPlacement(plan, bought);
        // 2026-10-09 用户裁决「从商店购买视为获得」：卡牌已写入玩家槽 ⇒ 触发维生素药丸/教主立牌等「获得」语义
        com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem.notifyCardObtained(buyer, boughtForNotify);
        shelfContainer.setItem(shelfIndex, ItemStack.EMPTY);
        sold[shelfIndex] = true;
        // 任务O §1.3：售出状态**落盘**——货架不再随菜单生命周期存在，重开界面必须仍是「这格已售」
        CardShopShelf.store(buyer, CardShopShelf.get(buyer).withSold(shelfIndex));
        // 2026-10-09 用户裁决 §2：购买成功即开启本轮 60 秒轮询（就绪态下开新一轮，进行中不重开——
        // 否则连续购买会把沙漏永远推回起点，「就绪」状态永不出现）。
        startCycleIfReady();
        StarCoinBalanceSync.notifyChanged(buyer);
        actionBar(buyer, ChatFormatting.GOLD, "msg.astral_dice.card_shop_bought", bought.getHoverName(), price);
        // 2026-10-22 用户裁决：卡牌购买**成功**也播钱包音效（wallet_deposit）——与转账成功 / 抽奖盒购买成功同口径；
        // 只在这一处（发货 + 扣款都已完成之后）播，任何拒绝分支都不响。
        SoundPlayback.playTo(buyer, ModSounds.WALLET_DEPOSIT.get());
    }

    /**
     * 玩家物品栏索引（0..8 = 快捷栏、9..35 = 主物品栏）→ 本菜单玩家区相对槽序号（0..35）。
     * 菜单按「主物品栏 3 行 → 快捷栏 1 行」添加槽位 ⇒ 0..26 ↔ 主物品栏、27..35 ↔ 快捷栏；
     * 屏幕层的拖拽落点判定与 {@code CardShopBuyPayload#targetSlot} 一律用**物品栏索引**口径。
     */
    public static int playerSlotOffset(int inventoryIndex) {
        return inventoryIndex < 9 ? 27 + inventoryIndex : inventoryIndex - 9;
    }

    /**
     * 星光兑换星币（原经商立牌主动技能整体迁入）。
     *
     * <p>任务O §1.4（2026-10-09 用户裁决「星光兑换也需要加冷却，补上」）：与转账**共用同一个 60 秒周期**
     * （{@link ModAttachments#getShopTransferCooldownEnd}），不引入第二个计时器 —— 周期内兑换一次后即进入冷却。
     * 冷却中**静默拒绝**（客户端已画灰且点击不发包，这里只是服务端兜底，不新增任何提示文本）。
     * 兑换成功同样 {@link #startCycleIfReady()}：就绪态下开启新一轮 60 秒，与买牌/转账口径一致。
     */
    private void tryExchange(ServerPlayer exchanger) {
        long now = exchanger.level().getGameTime();
        if (now < ModAttachments.getShopTransferCooldownEnd(exchanger)) {
            return;
        }
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
        // 任务O §1.4：兑换成功即进入/开启同一 60 秒周期（就绪态下开新一轮；进行中不重开）
        startCycleIfReady();
        actionBar(exchanger, ChatFormatting.GOLD, "msg.astral_dice.card_shop_exchange_done", gained + bonus);
    }

    // ────────────────────────────── 二级转账 ──────────────────────────────

    /** 重发候选玩家名单（打开转账视图 / 点放大镜刷新 / 目标玩家已下线时；**转账成功后不重发**） */
    public void sendTransferCandidates(ServerPlayer owner) {
        if (owner.getServer() == null) {
            return;
        }
        List<CardShopTransferListPayload.Entry> entries = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        for (ServerPlayer other : owner.getServer().getPlayerList().getPlayers()) {
            if (other == owner) {
                continue;
            }
            entries.add(new CardShopTransferListPayload.Entry(other.getUUID(), other.getName().getString(),
                    StarEngineEconomy.getBalance(other)));
            ids.add(other.getUUID());
        }
        candidateIds = List.copyOf(ids);
        PacketDistributor.sendToPlayer(owner, new CardShopTransferListPayload(List.copyOf(entries)));
    }

    /**
     * 确认转账（服务端二次校验：菜单归属 / 本轮轮询状态 / 目标有效性 / 余额）。
     * 2026-10-09 用户裁决 §2.4：**一轮 60 秒只能用一次转账** —— 由「本轮进行中」统一挡住
     * （进行中 ⇒ {@code card_shop_transfer_cooling} 提示，不再单独维护「本店会话位」）。
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
            notifyDeny(owner, DENY_NO_COIN);
            return;
        }
        // 2026-10-21 用户裁决:转账**成功**即播放钱包音效(wallet_deposit = 「钱包存入」语义)。
        // 失败 / 余额不足在上方 early-return,不会响 ⇒ 天然只走成功路径。
        SoundPlayback.playTo(owner, ModSounds.WALLET_DEPOSIT.get());
        // 成功 ⇒ 开启本轮 60 秒轮询（此刻必为就绪态：上面已挡掉进行中）；本轮内不可再转账。
        startCycleIfReady();
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
                prices[i] = CardShopShelf.priceOf(true, memberReduction);
                // 任务O §1.3：降价结果同样落盘（重开界面必须仍是这个价，不得回退成未降价）
                CardShopShelf.store(player, CardShopShelf.get(player).withDiscounted(i, prices[i]));
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
     * {@link #clickMenuButton}、{@link #handleBuy} 与 {@link #handleTransfer} 内完成(服务端权威)。
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

    /** 大酬宾窗口是否激活（服务端现算，客户端读 DataSlot 同步值） */
    private int computePromotionActive() {
        if (player.level().isClientSide()) {
            return promotionActive ? 1 : 0;
        }
        return player.level().getGameTime() < ModAttachments.getShopPromotionUntil(player) ? 1 : 0;
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

    /** 本轮剩余秒数（0 = 就绪；客户端读 DataSlot[6] 同步值）——沙漏倒序 5→1→0 的**唯一**数据源 */
    public int transferCooldownSeconds() {
        return transferCooldownSeconds;
    }

    /** 本轮 60 秒轮询是否进行中（客户端读 DataSlot[8]）：为 true ⇒ 中按钮不可用、二级视图自动退回 */
    public boolean isCycleRunning() {
        return computeCycleRunning();
    }

    /**
     * 兼容别名：1.21.1 侧 KubeJS 探针（{@code astral_shop_probe.js} / {@code astral_shop_client.js}）
     * 与屏幕层仍按旧名调用；语义已改写为 {@link #isCycleRunning()}（本轮 60 秒轮询进行中）。
     */
    public boolean isTransferUsedThisSession() {
        return isCycleRunning();
    }

    /** 「商店大酬宾」窗口是否激活（客户端读 DataSlot[7]；标题带 8t 流光仅在此为 true 时播放） */
    public boolean isPromotionActive() {
        return promotionActive;
    }

    /**
     * 菜单关闭（出店）：**不再做任何事**。2026-10-09 用户裁决 §2.3：轮询是「固定 1 分钟一轮，与开关界面无关」，
     * 原「出店时刻 + 60 秒」顺延已删除 ⇒ 出店既不重置也不延长本轮，再进店时沙漏继续按同一终点走
     * （{@code CardShopCatalog} 的抽架与附件真值都由服务端持有，关界面不产生任何副作用）。
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
    }

    // ────────────────────────── 60 秒轮询（服务端权威） ──────────────────────────

    /**
     * 本轮是否进行中：客户端读 DataSlot[8] 同步值；服务端按附件终点**现算**
     * （{@code getGameTime() < shop_transfer_cooldown_end}）。
     */
    private boolean computeCycleRunning() {
        if (player.level().isClientSide()) {
            return cycleRunning;
        }
        return player.level().getGameTime() < ModAttachments.getShopTransferCooldownEnd(player);
    }

    /**
     * 就绪态下开启新一轮 60 秒轮询（购买成功 / 转账成功调用）；**进行中不重开**。
     * 真值写在玩家附件 {@code shop_transfer_cooldown_end} 上 ⇒ 客户端无法伪造、掉线后依然计时。
     */
    private void startCycleIfReady() {
        if (player.level().isClientSide()) {
            return;
        }
        long now = player.level().getGameTime();
        if (now < ModAttachments.getShopTransferCooldownEnd(player)) {
            return;
        }
        ModAttachments.setShopTransferCooldownEnd(player, now + TRANSFER_COOLDOWN_SECONDS * 20L);
    }


    /**
     * 双击快速移入的落点（0..8 快捷栏 / 9..35 主物品栏）：2026-10-21「卡牌自动堆叠」后**先找可并入的同类未满栈**，
     * 再取第一个空格；两者都没有 ⇒ -1（调用方返回 DENY_INVENTORY_FULL 并触发「背包已满」提示）。
     */
    private int findQuickMoveSlot(ItemStack incoming) {
        for (int inv = 0; inv < 36; inv++) {
            Slot slot = invSlot(inv);
            if (slot.hasItem() && sameCard(slot.getItem(), incoming)
                    && slot.getItem().getCount() < maxStackSize(slot.getItem())) {
                return inv;
            }
        }
        for (int inv = 0; inv < 36; inv++) {
            if (!invSlot(inv).hasItem()) {
                return inv;
            }
        }
        return -1;
    }

    /**
     * 目标格是否被**不同物品**占着 —— 即 {@code planPlacement} 里**唯一**仍回报「目标格被占用」的情形;
     * 其余装不下(目标格是已满同类栈、空格用尽)一律按「背包已满」走 {@link #DENY_INVENTORY_FULL}。
     */
    private boolean isTargetSlotOccupied(ItemStack incoming, int targetSlot) {
        return targetSlot >= 0 && invSlot(targetSlot).hasItem()
                && !sameCard(invSlot(targetSlot).getItem(), incoming);
    }

    /**
     * 落位计划（2026-10-21 卡牌自动堆叠）：返回「{@code [物品栏索引, 张数]}」序列（**空表 = 全部并入已有栈**）；
     * 装不下 {@code amount} 张 ⇒ {@code null}（调用方据此在**扣款前**拒绝）。
     *
     * <p>顺序与 {@link #applyPlacement} 的写入顺序**完全一致**（判定与写入不得分叉）：
     * <ol>
     *   <li>目标格自身是同类未满栈 ⇒ 最先补它（玩家把卡拖到已有的同类栈上应当直接并进去）；</li>
     *   <li>其余同类未满栈按物品栏顺序补满；</li>
     *   <li>剩余的逐格开新栈：目标格为空则先用目标格，再按物品栏顺序取空格；</li>
     *   <li>目标格被**不同物品**占着且无处可并 ⇒ 不另找空格（保持「拖到别的东西上不放」的既有语义）。</li>
     * </ol>
     */
    private List<int[]> planPlacement(ItemStack incoming, int targetSlot, int amount) {
        List<int[]> plan = new ArrayList<>();
        int remaining = amount;
        if (targetSlot >= 0) {
            remaining -= mergeInto(plan, targetSlot, incoming, remaining);
        }
        for (int inv = 0; inv < 36 && remaining > 0; inv++) {
            if (inv == targetSlot) {
                continue;
            }
            remaining -= mergeInto(plan, inv, incoming, remaining);
        }
        if (remaining <= 0) {
            return plan;
        }
        if (targetSlot >= 0 && plan.isEmpty() && invSlot(targetSlot).hasItem()
                && !sameCard(invSlot(targetSlot).getItem(), incoming)) {
            // 目标格是别的东西、且**一处都并不进去** ⇒ 维持既有拒绝（不静默换格）
            return null;
        }
        if (targetSlot >= 0 && !invSlot(targetSlot).hasItem()) {
            int n = Math.min(maxStackSize(incoming), remaining);
            plan.add(new int[]{targetSlot, n});
            remaining -= n;
        }
        for (int inv = 0; inv < 36 && remaining > 0; inv++) {
            if (inv == targetSlot || invSlot(inv).hasItem()) {
                continue;
            }
            int n = Math.min(maxStackSize(incoming), remaining);
            plan.add(new int[]{inv, n});
            remaining -= n;
        }
        return remaining > 0 ? null : plan;
    }

    /** 把至多 {@code amount} 张同类卡补进第 {@code inventoryIndex} 格；不同类 / 已满 ⇒ 返回 0 且不写计划 */
    private int mergeInto(List<int[]> plan, int inventoryIndex, ItemStack incoming, int amount) {
        Slot slot = invSlot(inventoryIndex);
        if (!slot.hasItem() || !sameCard(slot.getItem(), incoming)) {
            return 0;
        }
        int space = maxStackSize(slot.getItem()) - slot.getItem().getCount();
        if (space <= 0) {
            return 0;
        }
        int n = Math.min(space, amount);
        plan.add(new int[]{inventoryIndex, n});
        return n;
    }

    /** 按 {@link #planPlacement} 的计划写入：已有同类栈用 {@code grow} 补满，空格用 {@code set} 开新栈 */
    private void applyPlacement(List<int[]> plan, ItemStack bought) {
        for (int[] entry : plan) {
            Slot slot = invSlot(entry[0]);
            if (slot.hasItem()) {
                slot.getItem().grow(entry[1]);
                slot.setChanged();
            } else {
                ItemStack fresh = bought.copy();
                fresh.setCount(entry[1]);
                slot.set(fresh);
                slot.setChanged();
            }
        }
    }

    /** 物品栏索引（0..8 快捷栏 / 9..35 主物品栏）→ 菜单里对应的玩家物品栏槽 */
    private Slot invSlot(int inventoryIndex) {
        return this.slots.get(SHELF_SIZE + playerSlotOffset(inventoryIndex));
    }

    /** 同类判定（1.21.1：同一物品 + 同一组件；卡牌按 CARD_USES 等组件区分栈） */
    private static boolean sameCard(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
    }

    /** 该栈上限（卡牌按剩余次数覆写：满次数 16、已消耗 1） */
    private static int maxStackSize(ItemStack stack) {
        return stack.getItem().getMaxStackSize(stack);
    }

    /** 拒绝信号（S2C，2026-10-09 §4）：余额不足 / 背包已满 ⇒ 客户端把钱包数字红白闪 3 次（服务端权威触发） */
    private void notifyDeny(ServerPlayer target, int reason) {
        PacketDistributor.sendToPlayer(target, new CardShopDenyPayload(reason));
    }

    /**
     * 「商店大酬宾」触发入口（2026-10-09 用户裁决 §2.2；任务U′ 2026-10-10 改写）：开启大酬宾窗口，并把该玩家
     * 货架**补足**促销格（+{@value CardShopCatalog#PROMOTION_BONUS_SLOTS} 格）。
     *
     * <p>⚠️ 任务U′（用户裁决，原话「装备或触发大酬宾事件，原本牌和计时器不动，改为立即补差额牌
     * （即补上牌缺口）。其他逻辑照旧」）：**不再重抽货架、不再把 60 秒计时器归 0** —— 已经在卖的那批牌
     * 继续保留（含已售格），只把缺的促销格补上；界面开着时立刻可见（{@link #reloadShelf()}），关着界面时
     * 下次开店可见。事件卡系统尚未实装（本批只留钩子，见 {@link CardShopCatalog}），未来实装时只需调用
     * 本方法；测试探针 {@code /astralshop promo} 也走这里。
     *
     * @param windowTicks 大酬宾窗口长度（tick）；≤0 ⇒ 只补差额、不开启窗口
     */
    public static void triggerPromotion(ServerPlayer player, long windowTicks) {
        long now = player.level().getGameTime();
        long window = Math.max(0L, windowTicks);
        ModAttachments.setShopPromotionUntil(player, now + window);
        // 任务U′：只补差额 —— 不重抽（不换批）、不清 sold、不继承/不重置「本批进店奖励已发」标记，
        // 也**不碰** 60 秒计时器（用户裁决：计时器不动）。
        CardShopShelf before = CardShopShelf.get(player);
        CardShopShelf topped = CardShopShelf.topUp(player, before);
        boolean changed = topped != before;
        if (changed) {
            CardShopShelf.store(player, topped);
        }
        CardShopMenu open = player.containerMenu instanceof CardShopMenu menu && menu.player == player ? menu : null;
        if (open != null) {
            open.reloadShelf();
        }
        LOGGER.info("[astral_dice] CARD_SHOP_PROMOTION player={} tick={} window={} menu={} slots_before={}"
                        + " slots_after={} topped={} prices={}",
                player.getName().getString(), now, window, open != null ? 1 : 0, before.size(), topped.size(),
                changed ? 1 : 0, topped.prices());
    }

    private void actionBar(Player target, ChatFormatting color, String key, Object... args) {
        if (!(target instanceof ServerPlayer serverPlayer)) {
            return;
        }
        PacketDistributor.sendToPlayer(serverPlayer, new ActionBarPayload(
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
