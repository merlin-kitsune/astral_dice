package com.merlinkitsune.astral_dice.item.sign;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 大侦探立牌。
 *
 * <p>2026-10-10 用户裁决:① <b>删除由本立牌注册的全部事件</b>(原 11 项随机事件)——事件改由统一事件体系
 * 承载({@code com.merlinkitsune.astral_dice.event.AstralEventRegistry});② 主动技能<b>重做</b>为
 * 「<b>事件选择界面</b>」——见 {@code com.merlinkitsune.astral_dice.event.EventChoiceManager} 与
 * {@code com.merlinkitsune.astral_dice.client.EventChoiceScreen}:从事件池独立抽 2 个选项,玩家 15 秒内选一个
 * 并点确认(或超时/死亡/登出强制第一个)后,经 {@code AstralEventDispatcher#trigger(..., ACTIVE_SKILL)}
 * 进入既有事件触发流程(弹卡 → 结算/音效)。
 *
 * <p>因此本类现在只保留:物品本体、主动技能挂载点({@link #handleUse})、冷却/锁定框架(恒不锁定)。
 * 删除的内容(留档):生命恢复/力量(0:30)、攻击-特大、随机效果牌+3 星币、滋养+饱和、迅捷、瞬间伤害、
 * 中毒、饥饿+反胃、凋灵+黑暗、虚弱+挖掘疲劳,以及配套的 actionbar 提示与「抽到事件」广播循环。
 */
public class FannySignItem extends BaseSignItem {
    public FannySignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动技能（2026-10-10 重做，用户需求）：开出「事件选择界面」——
        // 从现有事件池 AstralEventRegistry#publicPool(Player)（含「队内必须有秘密侦探」门禁）**独立抽 2 个**选项
        // （允许相同/任意正负中性组合），玩家须在 15 秒内选一个并点确认。
        // 触发流程与既有事件触发流程完全一致：AstralEventDispatcher.trigger(..., ACTIVE_SKILL)
        //   -> EventCardService.showThenRun（弹卡 -> 1.4s 动画 + 0.5s -> 结算/音效）。
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // 返回值必须**显式**处理（2026-10-11 code_review 非阻塞项）：false = 事件池为空，
            // 或同玩家已有未结算会话 —— start() 内部已打印 `AP_EVENT_CHOICE: pool_empty` /
            // `already_open` 告警；本次主动技能空放（不打开选择界面、不触发事件），
            // 与其它立牌「无可用目标 ⇒ 空放」的口径一致，无需补偿，也不叠加第二次结算。
            boolean opened = com.merlinkitsune.astral_dice.event.EventChoiceManager.start(serverPlayer);
            if (!opened) {
                return InteractionResultHolder.success(stack);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    /** 主动技能不再产生任何可门控效果 ⇒ 不使用锁定态(冷却由基类框架按默认口径推进)。 */
    @Override
    protected boolean startActiveLockOnUse(Player player, long now) {
        return false;
    }

    /** 门控效果:11 项事件已删除 ⇒ 不存在门控效果。 */
    @Override
    protected boolean isGateEffectActive(Player player) {
        return false;
    }
}
