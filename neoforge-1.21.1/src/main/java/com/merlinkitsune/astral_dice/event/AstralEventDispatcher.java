package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.starengine.combat.PartyRelations;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import com.merlinkitsune.astral_dice.event.api.AstralEventTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 事件分发器(2026-10-10 事件体系重写)。
 *
 * <p>所有事件触发的<b>唯一入口</b>,固定六步:
 * ① 客户端守卫(客户端调用一律 no-op);
 * ② 解析注册项(未注册 ⇒ warn 跳过);
 * ③ <b>阶级闸门</b> —— {@code secretOnly} 事件读当前阶级,且<b>只有</b> {@link AstralEventTrigger#INVESTIGATION_KILL}
 *    允许写回 {@code min(当前+1, 4)}(用户 2026-10-10 裁决:其它任何触发方式只能触发当前阶级);
 * ④ 作用域:秘密侦探专属事件**收窄** —— 击杀且 {@code tier < 4} ⇒ {@code twoPlayers(击杀者, 标记施加者)};
 *    非击杀来源 ⇒ {@code twoPlayers(原触发者, 抽中的最高阶段侦探)};其余情形(含 {@code tier == 4})⇒
 *    非击杀来源 ⇒ {@code twoPlayers(原触发者, 抽中的最高阶段侦探)};其余情形(含 {@code tier == 4})⇒
 * ⑤ <b>全局硬间隔闸门</b>({@link GlobalEventGate}):仅事件卡入口({@code ITEM_USE} /
 *     {@code ACTIVE_SKILL})占用并受此闸门约束 —— 上一事件进行中或结束后 20t 内一律拒绝;
 * ⑥ 计时器(该事件登记了 {@code defaultDurationTicks} 时,对每个目标覆盖式 start)→ 逐目标施加效果 →
 *    收尾走既有「附加效果层」({@link AstralEventSystem#applyEventBonus});
 * 最后打机器行 {@code AP_EVENT_FIRE: …} 供测试断言。
 */
public final class AstralEventDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AstralEventDispatcher.class);

    /** 历史遗留事件 id(不由注册表管理,只走附加效果层 —— 兼容入口用)。 */
    private static final Set<String> LEGACY_IDS = Set.of("investigation", "sign_effect");

    private AstralEventDispatcher() {
    }

    /**
     * 触发一条事件。
     *
     * @param triggerer 触发者
     * @param eventId   事件 id(必须已注册)
     * @param source    触发来源
     */
    public static boolean trigger(Player triggerer, ResourceLocation eventId, AstralEventTrigger source) {
        return trigger(triggerer, triggerer, eventId, source);
    }

    /**
     * 触发一条事件,并指定<b>阶级归属者</b>(默认 = 触发者)。
     *
     * <p>用途:秘密侦探的「调查阶段」事件由<b>击杀隐匿调查目标</b>触发,但阶级进度属于<b>施加者</b>
     * (佩戴秘密侦探立牌、施加了「隐匿调查」的那名玩家) —— 因此触发者与阶级归属者可能不同。
     * 作用域仍以<b>触发者</b>为中心(沿用既有口径)。
     */
    public static boolean trigger(Player triggerer, Player tierOwner, ResourceLocation eventId, AstralEventTrigger source) {
        // ① 客户端守卫:客户端调用必须 no-op(不崩、不写数据)
        if (triggerer.level().isClientSide()) {
            return false;
        }
        // ② 注册项
        Optional<AstralEvent> found = AstralEventRegistry.get(eventId);
        if (found.isEmpty()) {
            LOGGER.warn("AP_EVENT_UNKNOWN: {}:src={}", eventId, source);
            return false;
        }
        AstralEvent event = found.get();
        if (!event.allows(source)) {
            LOGGER.warn("AP_EVENT_SRC_DENIED: {}:src={}", eventId, source);
            return false;
        }
        // ③ 阶级(2026-10-10 用户裁决 R1:先按<b>当前</b>阶级触发,触发之后再升阶)+ 多侦探仲裁
        int tier = 0;
        Player bonusTriggerer = triggerer;
        Player involvedDetective = null;
        if (event.secretOnly()) {
            if (source == AstralEventTrigger.INVESTIGATION_KILL) {
                tier = ModAttachments.getInvestigationStage(tierOwner);
            } else {
                // 无击杀者的其它来源:取同队阶段最高者;并列则随机抽一个作为触发者(用户裁决 Q1/Q6)
                Optional<Player> picked = InvestigationArbitration.pickHighestTier(triggerer);
                if (picked.isEmpty()) {
                    LOGGER.warn("AP_EVENT_NO_DETECTIVE: {}:src={}", eventId, source);
                    return false;
                }
                involvedDetective = picked.get();
                tier = ModAttachments.getInvestigationStage(involvedDetective);
                bonusTriggerer = involvedDetective;
            }
        }
        // ④ 作用域:秘密侦探专属事件按用户裁决收窄(真相揭露级别恢复常规 64 格 ∪ 团队)
        List<Player> targets;
        if (event.secretOnly() && source == AstralEventTrigger.INVESTIGATION_KILL && tier < 4) {
            // 击杀路径:除「真相揭露」外,只影响「该标记的施加者」与「击杀者」
            targets = twoPlayers(triggerer, tierOwner);
        } else if (event.secretOnly() && source != AstralEventTrigger.INVESTIGATION_KILL) {
            // 其它来源:影响「原触发者」与「抽中的最高阶段侦探」
            targets = twoPlayers(triggerer, involvedDetective);
        } else {
            targets = PartyRelations.collectEventTargets(triggerer, event.radius());
        }
        // ⑤ 全局硬间隔闸门(2026-10-21):「事件卡 / 立牌主动技能」这两条「开新事件」的入口
        //    只在这里拦一次(不在各效果里重复拦)。击杀驱动的 INVESTIGATION_KILL 与外部 CUSTOM
        //    不占用闸门,也不被闸门拒绝。
        if ((source == AstralEventTrigger.ITEM_USE || source == AstralEventTrigger.ACTIVE_SKILL)
                && triggerer instanceof ServerPlayer gatePlayer
                && !GlobalEventGate.tryAcquire(gatePlayer, event.defaultDurationTicks())) {
            LOGGER.info("AP_EVENT_GATE_BLOCK: id={}:src={}", eventId, source);
            return false;
        }
        // ⑥ 计时器(覆盖式)+ 效果 + 附加效果层
        final Player bonusFinal = bonusTriggerer;
        final java.util.List<Player> targetsFinal = targets;
        final int tierFinal = tier;
        if (triggerer.level() instanceof ServerLevel serverLevel) {
            // 用户裁决 2026-10-10：先弹「事件效果卡」，弹卡开始后 40t（2.0s）才真正执行事件并播放音效。
            EventCardService.showThenRun(serverLevel, targetsFinal, event, tierFinal, lvl -> {
                // 用户裁决 2026-10-10（P3 修复）：计时器与事件效果**同刻**登记 —— 原先在弹卡前登记，
                // 会让计时器在动画播放期间就开始倒数（比动画结束早 38t），违背「等待动画规则」。
                if (event.timed()) {
                    for (Player target : targetsFinal) {
                        EventTimerStore.start(target, event.id(), event.defaultDurationTicks());
                    }
                }
                for (Player target : targetsFinal) {
                    event.effect().apply(lvl, bonusFinal, target, event, source, tierFinal);
                }
                EventEffects.afterDispatch(event, targetsFinal);
                AstralEventSystem.applyEventBonus(bonusFinal, event.id().toString(), targetsFinal);
                EventCardService.playTriggerSounds(targetsFinal, event);
                LOGGER.info("AP_EVENT_FIRE: id={}:src={}:tier={}:targetsFinal={}", event.id(), source, tierFinal, targetsFinal.size());
                if (event.secretOnly() && source == AstralEventTrigger.INVESTIGATION_KILL) {
                    ModAttachments.setInvestigationStage(tierOwner, Math.min(tierFinal + 1, 4));
                }
            });
        }
        return true;
    }
    /**
     * 便捷入口:按 id 常量触发(来源默认 {@link AstralEventTrigger#CUSTOM})。
     */
    public static boolean trigger(Player triggerer, ResourceLocation eventId) {
        return trigger(triggerer, eventId, AstralEventTrigger.CUSTOM);
    }

    /** 去重后取两名玩家(任一为 null 时只取另一名)。 */
    private static List<Player> twoPlayers(Player a, Player b) {
        java.util.LinkedHashSet<Player> set = new java.util.LinkedHashSet<>();
        if (a != null) set.add(a);
        if (b != null) set.add(b);
        return List.copyOf(set);
    }

    /** 该 id 是否属于历史遗留(不由注册表管理)。 */
    public static boolean isLegacy(String eventId) {
        return LEGACY_IDS.contains(eventId);
    }
}
