package com.merlinkitsune.astral_dice.api;

import com.merlinkitsune.astral_dice.api.combat.CombatStat;
import com.merlinkitsune.astral_dice.api.combat.CombatStatContext;
import com.merlinkitsune.astral_dice.api.combat.CombatStatHandle;
import com.merlinkitsune.astral_dice.api.combat.CombatStatHook;
import com.merlinkitsune.astral_dice.api.combat.CombatStatHooks;
import com.merlinkitsune.astral_dice.api.combat.CombatStatStage;
import com.merlinkitsune.astral_dice.api.combat.DamageCategory;
import com.merlinkitsune.astral_dice.api.combat.DamageCategoryResolver;
import com.merlinkitsune.astral_dice.api.playerlike.PlayerLikeCapability;
import com.merlinkitsune.astral_dice.api.playerlike.PlayerLikeHandler;
import com.merlinkitsune.astral_dice.api.playerlike.PlayerLikeWallet;
import com.merlinkitsune.astral_dice.combat.DiceCombatParticipants;
import com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem;
import com.merlinkitsune.astral_dice.item.card.CardItem;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.dice.DiceTierRegistry;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.platform.AstralDicePlatform;
import com.merlinkitsune.astral_dice.target.PlayerLikeTargets;
import com.merlinkitsune.starengine.combat.HostileTargets;
import com.merlinkitsune.astral_dice.platform.AstralDicePlatforms;
import com.merlinkitsune.starengine.api.combat.damage.DamageHook;
import com.merlinkitsune.starengine.api.combat.damage.DamageHooks;
import com.merlinkitsune.starengine.api.event.core.EventResult;
import com.merlinkitsune.starengine.api.combat.damage.DamagePhase;
import com.merlinkitsune.starengine.api.combat.damage.HookHandle;
import com.merlinkitsune.starengine.api.content.chip.ChipCategory;
import com.merlinkitsune.starengine.api.content.chip.ChipDescriptor;
import com.merlinkitsune.starengine.api.content.chip.ChipSchool;
import com.merlinkitsune.starengine.api.content.chip.Chips;
import com.merlinkitsune.starengine.api.content.dice.DiceTier;
import com.merlinkitsune.starengine.api.content.dice.DiceTiers;
import com.merlinkitsune.starengine.api.content.sign.SignDescriptor;
import com.merlinkitsune.starengine.api.content.sign.Signs;
import com.merlinkitsune.starengine.api.economy.EntityWallet;
import com.merlinkitsune.starengine.api.economy.WalletRegistry;
import com.merlinkitsune.starengine.target.TargetType;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Astral Dice 对外的统一门面(主项目 {@code common} 源码根)。
 *
 * <p><b>两级注册表</b>:立牌 / 筹码 / 经济钱包 / 伤害钩子链由 StarEngine
 * ({@code com.merlinkitsune.starengine.api.*})持有;骰面阶级另有一份主项目注册表
 * ({@link DiceTierRegistry}),因为筹码栏 / 卡牌栏 / 星愿铁砧在游戏主流程里要按物品栈解析。
 * 带 {@link Supplier} 的 {@link #registerDiceTier} 会同时写两边,只写库侧的重载不会让骰子可玩。
 *
 * <p>生命周期约定:注册必须在内容冻结之前完成 —— 冻结后两级注册表都会拒绝写入。
 * 主模组在服务器启动后({@code ServerStartedEvent}; Fabric 为 {@code SERVER_STARTED})调用
 * {@link #freezeContent()},该时机晚于 KubeJS 服务器脚本加载,脚本仍可在启动阶段注册内容。
 * 附属模组不要自行提前冻结,否则后续脚本注册会被拒绝;{@code /reload} 也不能再新增内容。
 *
 * <p>本类的全部方法都是静态且无状态的,可在任意线程调用(并发语义继承库侧实现)。
 */
public final class AstralDiceApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(AstralDiceApi.class);
    private static volatile DamageCategoryResolver damageCategoryResolver;

    private AstralDiceApi() {
    }

    // ---------------------------------------------------------------- 骰面阶级

    /**
     * 注册一个骰子阶层并绑定实际物品：这是附属新增可玩骰子的入口。
     *
     * <p>同时写入主模组 {@link DiceTierRegistry}（筹码栏 / 卡牌栏 / 星愿铁砧识别）
     * 与 StarEngine 的全局描述符表。{@code chipSlots} 返回该星级下的**绝对筹码格数**。
     */
    public static com.merlinkitsune.astral_dice.item.dice.DiceTier registerDiceTier(
            String id, Supplier<Item> item, int cardSlots, IntUnaryOperator chipSlots) {
        com.merlinkitsune.astral_dice.item.dice.DiceTier tier =
                new com.merlinkitsune.astral_dice.item.dice.DiceTier(id, item, chipSlots, cardSlots);
        DiceTierRegistry.register(tier);
        return tier;
    }

    /**
     * 只注册骰子描述符（不绑定物品）。
     *
     * <p>它不会让主模组的 {@code DiceCurioItem} / 铁砧识别该骰子；需要可玩物品时请用
     * {@link #registerDiceTier(String, Supplier, int, IntUnaryOperator)}。
     */
    public static DiceTier registerDiceTier(String id, int cardSlots, IntUnaryOperator chipSlots) {
        return DiceTiers.register(id, cardSlots, chipSlots);
    }

    /** 按 id 取骰面阶级;不存在返回 {@code null}。 */
    public static DiceTier diceTier(String id) {
        return DiceTiers.get(id);
    }

    /** 全部已注册骰面阶级(库侧返回快照,不受后续注册影响)。 */
    public static List<DiceTier> diceTiers() {
        return DiceTiers.all();
    }

    // ------------------------------------------------------------------ 立牌

    /** 注册一个立牌描述符。库侧只接受已构造好的描述符,故这里代为构造。 */
    public static SignDescriptor registerSign(String id, String school, int activeCooldownTicks) {
        return Signs.register(new SignDescriptor(id, school, activeCooldownTicks));
    }

    /** 按 id 取立牌描述符;不存在返回 {@code null}。 */
    public static SignDescriptor sign(String id) {
        return Signs.get(id);
    }

    // ------------------------------------------------------------------ 筹码

    /** 注册一个筹码描述符。库侧只接受已构造好的描述符,故这里代为构造。 */
    public static ChipDescriptor registerChip(String id, ChipSchool school, ChipCategory category,
                                              int price, int weight) {
        return Chips.register(new ChipDescriptor(id, school, category, price, weight));
    }

    /** 按 id 取筹码描述符;不存在返回 {@code null}。 */
    public static ChipDescriptor chip(String id) {
        return Chips.get(id);
    }

    // ------------------------------------------------------------------ 钱包

    /** 注册一个实体钱包:匹配谓词命中时由库侧钱包表解析到该钱包。 */
    public static void registerWallet(String id, Predicate<LivingEntity> matches, EntityWallet wallet) {
        WalletRegistry.register(id, matches, wallet);
    }

    // -------------------------------------------------------------- 伤害钩子

    /** 注册一个伤害钩子,返回的句柄可用于注销。 */
    public static HookHandle registerDamageHook(DamagePhase phase, int priority, DamageHook hook) {
        return DamageHooks.register(phase, priority, hook);
    }

    // ------------------------------------------------------------------ 查询

    /** 全部已注册立牌（库侧快照）。 */
    public static List<SignDescriptor> signs() {
        return Signs.all();
    }

    /** 全部已注册筹码（库侧快照）。 */
    public static List<ChipDescriptor> chips() {
        return Chips.all();
    }

    /** 全部立牌 id。 */
    public static List<String> signIds() {
        return Signs.all().stream().map(SignDescriptor::id).toList();
    }

    /** 全部筹码 id。 */
    public static List<String> chipIds() {
        return Chips.all().stream().map(ChipDescriptor::id).toList();
    }

    /** 全部骰面阶级 id。 */
    public static List<String> diceTierIds() {
        return DiceTiers.all().stream().map(DiceTier::id).toList();
    }

    /** 内容注册表是否已冻结。 */
    public static boolean isContentFrozen() {
        return Signs.isFrozen() && Chips.isFrozen() && DiceTiers.isFrozen() && DiceTierRegistry.isFrozen();
    }

    // ------------------------------------------------------------------ 钱包读写的便捷入口

    /** 该实体当前是否存在可用钱包。 */
    public static boolean walletAvailable(LivingEntity entity) {
        return resolveWallet(entity) != null;
    }

    /** 该实体钱包余额；没有钱包时返回 0。 */
    public static long walletBalance(LivingEntity entity) {
        EntityWallet wallet = resolveWallet(entity);
        return wallet == null ? 0L : wallet.balance(entity);
    }

    /** 该实体钱包余额是否不少于 {@code amount}（{@code amount < 0} 返回 false）。 */
    public static boolean walletHasBalance(LivingEntity entity, long amount) {
        if (amount < 0) {
            return false;
        }
        EntityWallet wallet = resolveWallet(entity);
        return wallet != null && wallet.balance(entity) >= amount;
    }

    /** 向该实体钱包存入；金额必须为正，失败返回 false。 */
    public static boolean walletDeposit(LivingEntity entity, long amount) {
        if (amount <= 0) {
            return false;
        }
        EntityWallet wallet = resolveWallet(entity);
        return wallet != null && wallet.deposit(entity, amount);
    }

    /** 从该实体钱包取出；金额必须为正，失败返回 false。 */
    public static boolean walletWithdraw(LivingEntity entity, long amount) {
        if (amount <= 0) {
            return false;
        }
        EntityWallet wallet = resolveWallet(entity);
        return wallet != null && wallet.withdraw(entity, amount);
    }

    /**
     * 钱包转账：先取出、再存入，存入失败时尽力退回。
     *
     * <p>库侧没有原子转账原语，因此这是**尽力而为**的两步操作；退回也失败时只记录日志并返回 false。
     */
    public static boolean walletTransfer(LivingEntity from, LivingEntity to, long amount) {
        if (from == null || to == null || from == to || amount <= 0) {
            return false;
        }
        EntityWallet fromWallet = resolveWallet(from);
        EntityWallet toWallet = resolveWallet(to);
        if (fromWallet == null || toWallet == null || !fromWallet.withdraw(from, amount)) {
            return false;
        }
        if (toWallet.deposit(to, amount)) {
            return true;
        }
        if (!fromWallet.deposit(from, amount)) {
            LOGGER.error("Wallet transfer refund failed: amount={}, from={}, to={}", amount, from, to);
        }
        return false;
    }

    private static EntityWallet resolveWallet(LivingEntity entity) {
        return entity == null ? null : WalletRegistry.resolve(entity);
    }

    // -------------------------------------------------------------- 伤害分类

    /** 安装本线伤害分类器（仅 NeoForge 1.21.1 / Forge 1.20.1 调用）。 */
    public static void installDamageCategoryResolver(DamageCategoryResolver resolver) {
        damageCategoryResolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** 当前线是否已安装伤害分类器。 */
    public static boolean isDamageCategorySupported() {
        return damageCategoryResolver != null;
    }

    /** 按当前线的分类器判定伤害大类；未安装时抛 {@link IllegalStateException}。 */
    public static DamageCategory damageCategory(DamageSource source) {
        DamageCategoryResolver resolver = damageCategoryResolver;
        if (resolver == null) {
            throw new IllegalStateException(
                    "Damage categories are only installed on NeoForge 1.21.1 / Forge 1.20.1.");
        }
        return resolver.classify(source);
    }

    /**
     * 注册只对某个大类生效的伤害钩子。
     *
     * <p>底层仍使用 StarEngine 的 {@link DamageHooks}：类别不匹配时返回 {@link EventResult#PASS}，
     * 不打断同阶段其它钩子。未安装分类器时立即抛异常，避免注册出永不触发的钩子。
     */
    public static HookHandle registerDamageHook(DamageCategory category, DamagePhase phase, int priority,
                                                DamageHook hook) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(hook, "hook");
        if (damageCategoryResolver == null) {
            throw new IllegalStateException(
                    "Damage categories are only installed on NeoForge 1.21.1 / Forge 1.20.1.");
        }
        return DamageHooks.register(phase, priority, context -> {
            if (context == null || category != damageCategory(context.source())) {
                return EventResult.PASS;
            }
            return hook.onDamage(context);
        });
    }

    // -------------------------------------------------------------- 骰战属性

    /** 安装本线骰战属性接线（仅 NeoForge 1.21.1 / Forge 1.20.1 调用）。 */
    public static void installCombatStatSupport() {
        CombatStatHooks.install();
    }

    /** 当前线是否已安装骰战属性接线。 */
    public static boolean isCombatStatSupported() {
        return CombatStatHooks.isInstalled();
    }

    /** 注册骰战属性钩子；未接线时抛 {@link IllegalStateException}。 */
    public static CombatStatHandle registerCombatStatHook(CombatStat stat, CombatStatStage stage, int priority,
                                                         CombatStatHook hook) {
        return CombatStatHooks.register(stat, stage, priority, hook);
    }

    // ---------------------------------------------------------------- 类玩家生物

    /**
     * 注册一个类玩家生物处理器（`id` 形如 `namespace:path`）。
     *
     * <p>注册后选牌 / 发牌 / 立牌受赠等走 {@code PlayerLikeTargets} 的主流程都会把它当玩家对待。
     * 玩家本身恒为类玩家，不需要注册。
     */
    public static void registerPlayerLike(String id, PlayerLikeHandler handler) {
        Objects.requireNonNull(id, "id");
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            throw new IllegalArgumentException("Player-like id must be namespace:path, got: " + id);
        }
        registerPlayerLike(id.substring(0, colon), id.substring(colon + 1), handler);
    }

    /** 按 namespace / path 两段注册（26.1.2 线标识符类型已分家，故门面只收两段字符串）。 */
    public static void registerPlayerLike(String namespace, String path, PlayerLikeHandler handler) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(handler, "handler");
        requirePlatform("registerPlayerLike").registerPlayerLike(namespace, path, handler);
    }

    /** 该实体是否被当作类玩家（玩家恒为 {@code true}）。 */
    public static boolean isPlayerLike(LivingEntity entity) {
        return entity != null && PlayerLikeTargets.isPlayerLike(entity);
    }

    /** 选择器能否选中该目标（与游戏内选牌口径一致）。 */
    public static boolean matchesPlayerSelection(TargetType type, Player selector, LivingEntity target) {
        return type != null && selector != null && target != null
                && PlayerLikeTargets.matchesSelection(type, selector, target);
    }

    /**
     * 能力位查询。
     *
     * <p>{@code SIGNS} / {@code CHIPS} 走内部注册表；{@code WALLET} 看门面钱包表是否已注册且可用；
     * 玩家三项恒真。
     */
    public static boolean supportsPlayerLikeCapability(LivingEntity entity, PlayerLikeCapability capability) {
        if (entity == null || capability == null) {
            return false;
        }
        if (entity instanceof Player) {
            return true;
        }
        if (capability == PlayerLikeCapability.WALLET) {
            return isPlayerLikeWalletAvailable(entity);
        }
        PlayerLikeTargets.Capability internal = capability == PlayerLikeCapability.SIGNS
                ? PlayerLikeTargets.Capability.SIGNS
                : PlayerLikeTargets.Capability.CHIPS;
        return PlayerLikeTargets.supports(entity, internal);
    }

    /**
     * 把卡牌发给类玩家目标（玩家走原版发牌路径）。
     *
     * <p>{@code giver} 允许 {@code null}：类玩家实体（女仆 / 召唤物等）本身无法充当
     * {@link ServerPlayer}，它们发牌时按「发牌者未知」处理 —— 卡牌照常交付，只是不结算
     * 依赖发牌者身份的被动（蛟龙「湖沼之王」等）。这与
     * {@code VitaminPillChipItem#giveCard(Player, Player, ItemStack)} 对 {@code null}
     * 的既有约定一致，也是附属「女仆发牌给玩家」这条路的前提。
     */
    public static boolean giveCardTo(ServerPlayer giver, LivingEntity receiver, ItemStack card) {
        return receiver != null && card != null
                && PlayerLikeTargets.giveCard(giver, receiver, card);
    }

    /**
     * 给目标发一件物品（服务端）：玩家走中央漏斗（星币入钱包 → 进包 → 掉地），
     * 类玩家生物走其注册 handler。
     *
     * <p>两类接收方同口径：{@code true} = 已交付；{@code false} 只表示接收方不是类玩家目标
     * （或调用发生在客户端），此时调用方自行处理（掉落 / 提示）。玩家分支恒 {@code true}，
     * 背包满会在漏斗内就地掉地，不会静默丢失。
     */
    public static boolean giveItemTo(LivingEntity receiver, ItemStack stack) {
        return receiver != null && stack != null && PlayerLikeTargets.giveItem(receiver, stack);
    }

    // ---------------------------------------------------------------- 骰子与立牌查询

    /** 是否是可装备的骰子物品。 */
    public static boolean isDiceItem(ItemStack stack) {
        return stack != null && DiceCurioItem.isDiceItem(stack);
    }

    /** 是否是卡牌（战斗牌 {@code CardItem} 或效果牌 {@code BaseEffectCardItem}）。 */
    public static boolean isCardItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof CardItem || stack.getItem() instanceof BaseEffectCardItem;
    }

    /** 骰子星级；非骰子返回 0。 */
    public static int diceStarLevel(ItemStack stack) {
        return stack == null ? 0 : DiceCurioItem.configStarLevel(stack);
    }

    /** 骰子提供的卡牌格数；非骰子返回 0。 */
    public static int diceCardSlots(ItemStack stack) {
        return stack == null ? 0 : DiceCurioItem.getCardSlots(stack);
    }

    /** 实体装备变化后刷新筹码格数（类玩家宿主必须由附属在换装时调用）。 */
    public static void refreshDiceChipSlots(LivingEntity holder) {
        if (holder != null) {
            DiceCurioItem.refreshChipSlotCountForEntity(holder);
        }
    }

    /** 是否是立牌物品。 */
    public static boolean isSignItem(ItemStack stack) {
        return stack != null && stack.getItem() instanceof BaseSignItem;
    }

    /**
     * 激活一件立牌（非玩家宿主的主动技能入口）。
     *
     * <p>玩家宿主请直接走立牌自身的玩家流程；本方法对玩家恒返回 {@code false}（与四条线语义一致）。
     * forge-1.20.1 线按 curios `stand` 槽解析已装备立牌，`stack` 只用于类型判定。
     */
    public static boolean activateSign(ItemStack stack, LivingEntity holder) {
        if (stack == null || holder == null) {
            return false;
        }
        AstralDicePlatform target = platform();
        return target != null && target.activateSign(stack, holder);
    }

    // ---------------------------------------------------------------- 骰子祝福

    /** 实体身上是否有"骰子祝福"（五线效果注册器类型不同，经平台层判定）。 */
    public static boolean hasDiceBlessing(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        AstralDicePlatform target = platform();
        return target != null && target.hasDiceBlessing(entity);
    }

    // ---------------------------------------------------------------- 骰战参与者

    /** 注册额外攻击方（骰战参与者）。`owner` 只用于日志溯源。 */
    public static void registerDiceCombatParticipant(String owner, Predicate<LivingEntity> predicate) {
        DiceCombatParticipants.registerExtraAttacker(owner, predicate);
    }

    /** 该实体是否已被登记为骰战参与者。 */
    public static boolean isDiceCombatParticipant(LivingEntity entity) {
        return entity != null && DiceCombatParticipants.isParticipant(entity);
    }

    /** 清空参与者登记（仅测试用）。 */
    public static void clearDiceCombatParticipantsForTests() {
        DiceCombatParticipants.clearForTests();
    }

    // ---------------------------------------------------------------- 类玩家钱包

    /** 门面自持的类玩家钱包表；与 StarEngine 的玩家账户表是两回事。 */
    private static final List<PlayerLikeWalletBinding> PLAYER_LIKE_WALLETS = new CopyOnWriteArrayList<>();

    private record PlayerLikeWalletBinding(String id, Predicate<LivingEntity> matches, PlayerLikeWallet wallet) {
    }

    /**
     * 注册类玩家生物的钱包（同一 `id` 重复注册时后者覆盖）。
     *
     * <p>玩家不需要注册：玩家账户走 {@link #walletBalance} 一族。本表只影响非玩家宿主，
     * 游戏内的钱包解析在内部绑定未命中后会回落到这里（neo-1.21.1 / forge-1.20.1 / neo-26.1.2）。
     */
    public static void registerPlayerLikeWallet(String id, Predicate<LivingEntity> matches, PlayerLikeWallet wallet) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(matches, "matches");
        Objects.requireNonNull(wallet, "wallet");
        synchronized (PLAYER_LIKE_WALLETS) {
            PLAYER_LIKE_WALLETS.removeIf(binding -> binding.id().equals(id));
            PLAYER_LIKE_WALLETS.add(new PlayerLikeWalletBinding(id, matches, wallet));
        }
        LOGGER.info("AP_PLAYER_LIKE_WALLET_API: registered id={}", id);
    }

    /** 解析类玩家生物的钱包；没有注册返回 {@code null}（玩家请用 {@link #walletBalance}）。 */
    public static PlayerLikeWallet playerLikeWallet(LivingEntity holder) {
        if (holder == null) {
            return null;
        }
        for (PlayerLikeWalletBinding binding : PLAYER_LIKE_WALLETS) {
            try {
                if (binding.matches().test(holder)) {
                    return binding.wallet();
                }
            } catch (RuntimeException e) {
                LOGGER.debug("Player-like wallet matcher failed: id={}, error={}", binding.id(), e.toString());
            }
        }
        return null;
    }

    /** 类玩家钱包是否可用。 */
    public static boolean isPlayerLikeWalletAvailable(LivingEntity holder) {
        PlayerLikeWallet wallet = playerLikeWallet(holder);
        return wallet != null && wallet.isAvailable(holder);
    }

    /** 类玩家生物余额；无钱包返回 0。 */
    public static long playerLikeWalletBalance(LivingEntity holder) {
        PlayerLikeWallet wallet = playerLikeWallet(holder);
        return wallet == null ? 0L : Math.max(0L, wallet.balance(holder));
    }

    /** 类玩家生物扣款；余额不足 / 无钱包返回 {@code false} 且无副作用。 */
    public static boolean playerLikeWalletWithdraw(LivingEntity holder, int amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerLikeWallet wallet = playerLikeWallet(holder);
        return wallet != null && wallet.isAvailable(holder) && wallet.withdraw(holder, amount);
    }

    /** 类玩家生物退款；默认钱包不支持时返回 {@code false}。 */
    public static boolean playerLikeWalletDeposit(LivingEntity holder, int amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerLikeWallet wallet = playerLikeWallet(holder);
        return wallet != null && wallet.deposit(holder, amount);
    }

    // ---------------------------------------------------------------- 敌对目标探针

    /** 门面持有的复合敌对探针表：库侧探针只允许一个，故由门面聚合后再一次性装给库。 */
    private static final List<Predicate<Entity>> HOSTILE_PROBES = new CopyOnWriteArrayList<>();
    private static volatile boolean hostileProbeInstalled;

    /**
     * 注册"额外敌对目标"探针（可多次注册，按注册顺序短路）。
     *
     * <p>直接调库的 {@code HostileTargets.installExtraHostileProbe} 是后装覆盖；本门面只装一次复合探针，
     * 之后所有注册都在复合探针内部聚合，互不顶掉。
     *
     * @return 复合探针是否已装到库上
     */
    public static boolean registerHostileProbe(Predicate<Entity> probe) {
        if (probe == null) {
            return false;
        }
        HOSTILE_PROBES.add(probe);
        return ensureHostileProbeInstalled();
    }

    /** 该目标是否敌对（含门面注册的额外探针）。 */
    public static boolean isHostileTarget(Entity target) {
        return target != null && HostileTargets.isHostile(target);
    }

    /** 该目标是否对指定观察者敌对（含"曾主动攻击观察者的玩家"口径）。 */
    public static boolean isHostileTarget(Entity viewer, Entity target) {
        return viewer != null && target != null && HostileTargets.isHostile(viewer, target);
    }

    private static boolean ensureHostileProbeInstalled() {
        if (hostileProbeInstalled) {
            return true;
        }
        synchronized (AstralDiceApi.class) {
            if (hostileProbeInstalled) {
                return true;
            }
            try {
                HostileTargets.installExtraHostileProbe(AstralDiceApi::anyHostileProbe);
                hostileProbeInstalled = true;
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to install composite hostile probe", e);
            }
            return hostileProbeInstalled;
        }
    }

    private static boolean anyHostileProbe(Entity target) {
        for (Predicate<Entity> probe : HOSTILE_PROBES) {
            try {
                if (probe.test(target)) {
                    return true;
                }
            } catch (RuntimeException e) {
                LOGGER.debug("Hostile probe failed: {}", e.toString());
            }
        }
        return false;
    }

    private static AstralDicePlatform requirePlatform(String operation) {
        AstralDicePlatform target = AstralDicePlatforms.get();
        if (target == null) {
            throw new IllegalStateException(operation
                    + " requires the Astral Dice platform to be installed; call it after mod construction.");
        }
        return target;
    }

    // ------------------------------------------------------------------ 收口

    /**
     * 冻结内容注册表(骰面阶级 / 立牌 / 筹码),冻结后再注册会被库侧拒绝。
     *
     * <p>由主模组在服务器启动后调用;可重复调用。附属模组应在此之前完成注册,
     * 不要自行提前冻结。
     */
    public static void freezeContent() {
        DiceTierRegistry.freeze();
        DiceTiers.freeze();
        Signs.freeze();
        Chips.freeze();
    }

    /** 当前平台实现;未安装时返回 {@code null}(判断平台请用 {@link AstralDicePlatforms#id()})。 */
    public static AstralDicePlatform platform() {
        return AstralDicePlatforms.get();
    }
}
