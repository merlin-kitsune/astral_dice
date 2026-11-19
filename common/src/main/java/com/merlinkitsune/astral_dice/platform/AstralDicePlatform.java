package com.merlinkitsune.astral_dice.platform;

import com.merlinkitsune.starengine.api.astral.combat.AttackPowerModifier;
import com.merlinkitsune.starengine.api.astral.combat.DefensePowerModifier;
import com.merlinkitsune.starengine.api.astral.combat.DiceCombatFactor;
import com.merlinkitsune.starengine.api.astral.combat.ExtraDamageModifier;
import com.merlinkitsune.starengine.api.astral.combat.SpellDamageMatcher;
import com.merlinkitsune.starengine.api.astral.combat.SpellDamageModifier;
import com.merlinkitsune.starengine.api.astral.combat.VictimDamageModifier;
import com.merlinkitsune.starengine.api.astral.playerlike.PlayerLikeHandler;
import com.merlinkitsune.starengine.api.astral.event.AstralEvent;
import com.merlinkitsune.starengine.api.astral.event.AstralEventTrigger;

import java.util.Set;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Astral Dice 的平台抽象:主项目 {@code common} 源码根里唯一允许出现的"平台身份"概念。
 *
 * <p>每个子项目(加载器线)在 {@code AstralDiceMod} 入口的初始化早期注册一份实现,
 * 之后 {@code common} 里的代码只通过 {@link AstralDicePlatforms} 取平台身份,
 * 不直接 import 任何加载器类(NeoForge / Forge / Fabric 的 API 一律不下沉到这里)。
 *
 * <p>实现方不得在 {@link #id()} / {@link #install()} 里 import 加载器类:
 * 该类会被五个子项目以同一份源码编译,任何加载器专属符号都会把其他线拖挂。
 *
 * <p>2026-11-18 起本接口承接三处"五线签名不一致、common 无法直接写"的桥:
 * 类玩家注册({@code PlayerLikeTargets.register} 在 26.1.2 线收 {@code Identifier})、
 * 立牌激活(forge 线是静态方法)、骰子祝福(五线效果注册器类型不同)。
 */
public interface AstralDicePlatform {

    /** 平台标识,五个实现分别返回 {@code neoforge} / {@code forge} / {@code fabric}。 */
    String id();

    /**
     * 平台装配钩子。{@link AstralDicePlatforms#install(AstralDicePlatform)} 设置完持有者后立即调用。
     *
     * <p>当前阶段为空实现(骨架):后续阶段若需要平台侧装配,在这里做,
     * 仍然不得把加载器类泄漏到调用方签名里。
     */
    default void install() {
    }

    /**
     * 注册一个类玩家生物处理器。
     *
     * <p>标识符按 {@code namespace} / {@code path} 两段传入,由各线自行构造
     * {@code ResourceLocation} 或 {@code Identifier} —— 这两者在 26.1.2 线已分家,
     * {@code common} 不能出现其中任何一个类型。
     */
    default void registerPlayerLike(String namespace, String path, PlayerLikeHandler handler) {
    }

    /**
     * 激活一件立牌。
     *
     * <p>四条线的立牌是实例方法 {@code BaseSignItem#activateFor(LivingEntity)};
     * forge-1.20.1 是静态方法且会自行从 curios {@code stand} 槽解析已装备的立牌,
     * 故该线只把 {@code stack} 用于类型判定。
     */
    default boolean activateSign(ItemStack stack, LivingEntity holder) {
        return false;
    }

    /** 实体身上是否有"骰子祝福"效果。 */
    default boolean hasDiceBlessing(LivingEntity entity) {
        return false;
    }

    // ------------------------------------------------------------- B2 战斗注册口

    /** 骰战攻击力修饰器。各线实现负责转调 {@code CombatApiAdapters}（内部上下文类型各线各一份）。 */
    default void registerAttackModifier(AttackPowerModifier modifier) {
    }

    /** 骰战防御力修饰器（仅战斗防御牌口径）。各线实现负责转调 {@code CombatApiAdapters}（内部上下文类型各线各一份）。 */
    default void registerDefenseModifier(DefensePowerModifier modifier) {
    }

    /** 命中后额外加伤修饰器。各线实现负责转调 {@code CombatApiAdapters}（内部上下文类型各线各一份）。 */
    default void registerExtraDamageModifier(ExtraDamageModifier modifier) {
    }

    /** 受击侧伤害倍率修饰器。各线实现负责转调 {@code CombatApiAdapters}（内部上下文类型各线各一份）。 */
    default void registerVictimDamageModifier(VictimDamageModifier modifier) {
    }

    /** 骰战最终伤害因子。各线实现负责转调 {@code CombatApiAdapters}（内部上下文类型各线各一份）。 */
    default void registerDiceCombatFactor(DiceCombatFactor modifier) {
    }

    /** 法伤作用域判定器。各线实现负责转调 {@code CombatApiAdapters}（内部上下文类型各线各一份）。 */
    default void registerSpellDamageMatcher(SpellDamageMatcher modifier) {
    }

    /** 法伤加成修饰器。各线实现负责转调 {@code CombatApiAdapters}（内部上下文类型各线各一份）。 */
    default void registerSpellDamageModifier(SpellDamageModifier modifier) {
    }

    // ------------------------------------------------------------- B3 效果 / 星界事件

    /** 读本模组效果的层数（amplifier + 1）；没有该效果返回 0。{@code effectId} 为完整 id 字符串。 */
    default int astralEffectLevel(LivingEntity entity, String effectId) {
        return 0;
    }

    /** 读本模组效果的剩余时长（tick）；没有该效果返回 0。 */
    default int astralEffectRemainingTicks(LivingEntity entity, String effectId) {
        return 0;
    }

    /** 施加本模组效果；id 不识别或 {@code durationTicks <= 0} 时返回 false。 */
    default boolean applyAstralEffect(LivingEntity entity, String effectId, int durationTicks, int amplifier) {
        return false;
    }

    /** 当前登记的全部本模组效果 id（完整字符串）。 */
    default Set<String> astralEffectIds() {
        return Set.of();
    }

    /** 按 id 查一条星界事件；未登记返回 {@code null}。 */
    default AstralEvent findAstralEvent(String eventId) {
        return null;
    }

    /**
     * 按 id 触发一条星界事件；{@code tierOwner} 可空（空走不带归因方的那条重载）。
     *
     * <p>返回是否真的触发；源不合法 / 事件未登记 / 触发器不在该事件的允许集合内都会返回 false。
     */
    default boolean triggerAstralEvent(Player triggerer, Player tierOwner, String eventId, AstralEventTrigger source) {
        return false;
    }

    // ------------------------------------------------------------- B4 经济识别 / 发放 / 配置只读

    /**
     * 星币面额：1 星币 = 1、1 星币袋 = 9，其它物品返回 0。
     *
     * <p><b>未接线的平台实现会静默返回 0</b>，门面因此无法区分「不是货币」与「这线没接线」。
     * 五线当前都已接线；若新增加载器线，必须实现本方法。
     */
    default long starCoinValue(ItemStack stack) {
        return 0L;
    }

    /**
     * 把玩家物品栏（36 格）与副手的全部星币/星币袋折算进钱包，并移除对应物品。
     *
     * <p>仅服务端有效；返回实际入账的面额合计，0 表示没有可存入货币或钱包不可用。
     */
    default long depositCarriedStarCoins(Player player) {
        return 0L;
    }

    /**
     * 用本模组的中央发放漏斗给玩家发一件物品：星币先进钱包（开关启用时），否则进物品栏，满则掉地。
     *
     * <p>仅服务端有效；返回是否已交给漏斗处理（false = 客户端 / 参数无效）。
     */
    default boolean giveItemToPlayer(Player player, ItemStack stack) {
        return false;
    }

    /** 星币钱包功能是否启用（读 COMMON 配置）。未接线返回 false。 */
    default boolean walletFeatureEnabled() {
        return false;
    }

    /** 「获得星币时直接存入钱包」是否生效（钱包关闭时恒 false）。未接线返回 false。 */
    default boolean autoDepositOnObtain() {
        return false;
    }

    /** 枪弹/炮弹类伤害是否计入法伤（读 COMMON 配置）。未接线返回 false。 */
    default boolean firearmDamageAllowed() {
        return false;
    }

    /** actionbar 消息显示总时长（tick）。未接线返回 0。 */
    default int actionBarDurationTicks() {
        return 0;
    }

    /** actionbar 消息淡出时长（tick）。未接线返回 0。 */
    default int actionBarFadeTicks() {
        return 0;
    }

    // ------------------------------------------------------------- B5 客户端面 / 界面入口

    /**
     * 为玩家打开卡牌栏（服务端）。
     *
     * <p>未佩戴骰子时由实现方向玩家推送 actionbar 提示且不打开界面。
     * 返回是否真的打开了界面；未接线返回 false。
     */
    default boolean openCardInventory(ServerPlayer player) {
        return false;
    }
}
