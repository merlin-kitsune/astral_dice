package com.merlinkitsune.astral_dice.api;

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
import net.minecraft.world.entity.LivingEntity;

/**
 * Astral Dice 对外的统一门面(主项目 {@code common} 源码根)。
 *
 * <p><b>纯委托,不复制注册表</b>:骰面阶级 / 立牌 / 筹码的注册表、经济钱包表、
 * 伤害钩子链全部由 StarEngine({@code com.merlinkitsune.starengine.api.*})持有,
 * 本类只把入口收敛到一处,让调用方不必直接依赖库的类名分布。
 *
 * <p>生命周期约定:注册必须在内容冻结之前完成 —— 冻结后库侧注册表会拒绝写入。
 * 冻结由模组自己在内容注册完成后调用 {@link #freezeContent()}(当前骨架阶段尚未接线)。
 *
 * <p>本类的全部方法都是静态且无状态的,可在任意线程调用(并发语义继承库侧实现)。
 */
public final class AstralDiceApi {

    private AstralDiceApi() {
    }

    // ---------------------------------------------------------------- 骰面阶级

    /** 注册一个骰面阶级。{@code chipBonus} 由库侧按 {@code 下标 -> 加成} 求值。 */
    public static DiceTier registerDiceTier(String id, int cardSlots, IntUnaryOperator chipBonus) {
        return DiceTiers.register(id, cardSlots, chipBonus);
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
        DiceTiers.freeze();
        Signs.freeze();
        Chips.freeze();
    }

    /** 当前平台实现;未安装时返回 {@code null}(判断平台请用 {@link AstralDicePlatforms#id()})。 */
    public static AstralDicePlatform platform() {
        return AstralDicePlatforms.get();
    }
}
