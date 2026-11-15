package com.merlinkitsune.astral_dice.api;

import com.merlinkitsune.astral_dice.item.dice.DiceTierRegistry;
import com.merlinkitsune.astral_dice.platform.AstralDicePlatform;
import com.merlinkitsune.astral_dice.platform.AstralDicePlatforms;
import com.merlinkitsune.starengine.api.combat.damage.DamageHook;
import com.merlinkitsune.starengine.api.combat.damage.DamageHooks;
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
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

/**
 * Astral Dice 对外的统一门面(主项目 {@code common} 源码根)。
 *
 * <p><b>两级注册表</b>:立牌 / 筹码 / 经济钱包 / 伤害钩子链由 StarEngine
 * ({@code com.merlinkitsune.starengine.api.*})持有;骰面阶级另有一份主项目注册表
 * ({@link DiceTierRegistry}),因为筹码栏 / 卡牌栏 / 星愿铁砧在游戏主流程里要按物品栈解析。
 * 带 {@link Supplier} 的 {@link #registerDiceTier} 会同时写两边,只写库侧的重载不会让骰子可玩。
 *
 * <p>生命周期约定:注册必须在内容冻结之前完成 —— 冻结后两级注册表都会拒绝写入。
 * 模组在内容注册完成后调用 {@link #freezeContent()}。
 *
 * <p>本类的全部方法都是静态且无状态的,可在任意线程调用(并发语义继承库侧实现)。
 */
public final class AstralDiceApi {

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

    // ------------------------------------------------------------------ 收口

    /** 冻结内容注册表(骰面阶级 / 立牌 / 筹码),冻结后再注册会被库侧拒绝。 */
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
