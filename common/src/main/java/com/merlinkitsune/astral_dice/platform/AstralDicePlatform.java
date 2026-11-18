package com.merlinkitsune.astral_dice.platform;

import com.merlinkitsune.astral_dice.api.playerlike.PlayerLikeHandler;
import net.minecraft.world.entity.LivingEntity;
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
}
