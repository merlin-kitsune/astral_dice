package com.merlinkitsune.astral_dice.api;

import com.merlinkitsune.astral_dice.api.combat.CombatStat;
import com.merlinkitsune.astral_dice.api.combat.CombatStatContext;
import com.merlinkitsune.astral_dice.api.combat.CombatStatHandle;
import com.merlinkitsune.astral_dice.api.combat.CombatStatHook;
import com.merlinkitsune.astral_dice.api.combat.CombatStatHooks;
import com.merlinkitsune.astral_dice.api.combat.CombatStatStage;
import com.merlinkitsune.astral_dice.api.combat.DamageCategory;
import com.merlinkitsune.astral_dice.api.combat.DamageCategoryResolver;
import com.merlinkitsune.astral_dice.item.dice.DiceTierRegistry;
import com.merlinkitsune.astral_dice.platform.AstralDicePlatform;
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
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
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
