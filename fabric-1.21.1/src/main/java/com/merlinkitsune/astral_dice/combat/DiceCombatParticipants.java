package com.merlinkitsune.astral_dice.combat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 「骰战参与者」注册表：允许非玩家实体（第三方模组的宠物等）参与骰战结算。
 *
 * <p>注册方在各自 Setup 期调用 registerExtraAttacker(Predicate)；本模组**不**依赖任何第三方模组。
 * 本模组**不提供**任何配置项、数据包标签、命令或自动发现入口；未显式注册的实体永远不是参与者。
 * 未注册任何判定器时 isParticipant(LivingEntity) 恒 false ⇒ 战斗行为与未引入本类时逐值一致。
 *
 * <p>判定器会在服务端伤害热路径上被频繁调用，因此**只允许做类型/身份判定**：不读存档、不掷骰、不发网络包。
 */
public final class DiceCombatParticipants {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiceCombatParticipants.class);

    /** 幂等注册（重复登记同一个谓词实例会被忽略），线程安全。 */
    private static final List<Predicate<LivingEntity>> EXTRA_ATTACKERS = new CopyOnWriteArrayList<>();

    private DiceCombatParticipants() {
    }

    /**
     * 注册「非玩家也能参与骰战」的判定器。消费方（如联动模组）在各自 Setup 期调用一次。
     *
     * <p>**只允许附属模组以代码显式注册**：本模组不提供配置 / 数据包 / 命令 / 自动发现入口，
     * 未注册的实体永远不是参与者（这条是「未注册 ⇒ 行为逐值不变」回归红线的一部分）。
     *
     * @param owner     注册方标识（如 modid），只用于调试日志
     * @param predicate 只做类型/身份判定的纯函数；null 会被忽略
     */
    public static void registerExtraAttacker(String owner, Predicate<LivingEntity> predicate) {
        if (predicate == null) return;
        for (Predicate<LivingEntity> existing : EXTRA_ATTACKERS) {
            if (existing == predicate) return;
        }
        EXTRA_ATTACKERS.add(predicate);
        LOGGER.debug("DiceCombatParticipants: extra attacker predicate registered by {}", owner);
    }

    /**
     * 兼容旧调用点：等价于 {@link #registerExtraAttacker(String, Predicate)} 且 owner 记为 unknown。
     */
    public static void registerExtraAttacker(Predicate<LivingEntity> predicate) {
        registerExtraAttacker("unknown", predicate);
    }

    /**
     * 供 DiceCombatEvents 使用：该攻击者是否走骰战结算。
     *
     * <p>判定器抛异常一律吞掉并 LOGGER.debug —— 一个坏消费方不能打崩伤害管线。
     */
    public static boolean isParticipant(LivingEntity attacker) {
        if (attacker == null || EXTRA_ATTACKERS.isEmpty()) return false;
        for (Predicate<LivingEntity> predicate : EXTRA_ATTACKERS) {
            try {
                if (predicate.test(attacker)) return true;
            } catch (Throwable t) {
                LOGGER.debug("DiceCombatParticipants: registered predicate threw, ignoring", t);
            }
        }
        return false;
    }

    /** 仅测试台 / 热重载使用：清空全部注册。 */
    public static void clearForTests() {
        EXTRA_ATTACKERS.clear();
    }
}
