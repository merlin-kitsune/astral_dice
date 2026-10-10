package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.combat.PartyRelations;
import com.merlinkitsune.astral_dice.event.api.AstralEvent;
import com.merlinkitsune.astral_dice.event.api.AstralEventTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 事件分发器(2026-10-10 事件体系重写)。
 *
 * <p>所有事件触发的<b>唯一入口</b>,固定五步:
 * ① 客户端守卫(客户端调用一律 no-op);
 * ② 解析注册项(未注册 ⇒ warn 跳过);
 * ③ <b>阶级闸门</b> —— {@code secretOnly} 事件读当前阶级,且<b>只有</b> {@link AstralEventTrigger#INVESTIGATION_KILL}
 *    允许写回 {@code min(当前+1, 4)}(用户 2026-10-10 裁决:其它任何触发方式只能触发当前阶级);
 * ④ 作用域 = {@link PartyRelations#collectEventTargets}(64 格 ∪ 团队全体);
 * ⑤ 计时器(该事件登记了 {@code defaultDurationTicks} 时,对每个目标覆盖式 start)→ 逐目标施加效果 →
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
    public static void trigger(Player triggerer, ResourceLocation eventId, AstralEventTrigger source) {
        trigger(triggerer, triggerer, eventId, source);
    }

    /**
     * 触发一条事件,并指定<b>阶级归属者</b>(默认 = 触发者)。
     *
     * <p>用途:秘密侦探的「调查阶段」事件由<b>击杀隐匿调查目标</b>触发,但阶级进度属于<b>施加者</b>
     * (佩戴秘密侦探立牌、施加了「隐匿调查」的那名玩家) —— 因此触发者与阶级归属者可能不同。
     * 作用域仍以<b>触发者</b>为中心(沿用既有口径)。
     */
    public static void trigger(Player triggerer, Player tierOwner, ResourceLocation eventId, AstralEventTrigger source) {
        // ① 客户端守卫:客户端调用必须 no-op(不崩、不写数据)
        if (triggerer.level().isClientSide()) {
            return;
        }
        // ② 注册项
        Optional<AstralEvent> found = AstralEventRegistry.get(eventId);
        if (found.isEmpty()) {
            LOGGER.warn("AP_EVENT_UNKNOWN: {}:src={}", eventId, source);
            return;
        }
        AstralEvent event = found.get();
        if (!event.allows(source)) {
            LOGGER.warn("AP_EVENT_SRC_DENIED: {}:src={}", eventId, source);
            return;
        }
        // ③ 阶级闸门:只有击杀「隐匿调查」允许提升;其它来源只读当前阶级
        int tier = 0;
        if (event.secretOnly()) {
            int current = ModAttachments.getInvestigationStage(tierOwner);
            if (source == AstralEventTrigger.INVESTIGATION_KILL) {
                current = Math.min(current + 1, 4);
                ModAttachments.setInvestigationStage(tierOwner, current);
            }
            tier = current;
        }
        // ④ 作用域:64 格 ∪ 团队全体(口径见 PartyRelations#collectEventTargets)
        List<Player> targets = PartyRelations.collectEventTargets(triggerer, event.radius());
        // ⑤ 计时器(覆盖式)+ 效果 + 附加效果层
        if (event.timed()) {
            for (Player target : targets) {
                EventTimerStore.start(target, event.id(), event.defaultDurationTicks());
            }
        }
        if (triggerer.level() instanceof ServerLevel serverLevel) {
            for (Player target : targets) {
                event.effect().apply(serverLevel, triggerer, target, event, source, tier);
            }
        }
        AstralEventSystem.applyEventBonus(triggerer, event.id().toString(), targets);
        LOGGER.info("AP_EVENT_FIRE: id={}:src={}:tier={}:targets={}", event.id(), source, tier, targets.size());
    }

    /**
     * 便捷入口:按 id 常量触发(来源默认 {@link AstralEventTrigger#CUSTOM})。
     */
    public static void trigger(Player triggerer, ResourceLocation eventId) {
        trigger(triggerer, eventId, AstralEventTrigger.CUSTOM);
    }

    /** 该 id 是否属于历史遗留(不由注册表管理)。 */
    public static boolean isLegacy(String eventId) {
        return LEGACY_IDS.contains(eventId);
    }
}
