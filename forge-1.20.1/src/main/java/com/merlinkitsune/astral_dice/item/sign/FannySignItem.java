package com.merlinkitsune.astral_dice.item.sign;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 大侦探立牌。
 *
 * <p>2026-10-10 用户裁决:① <b>删除由本立牌注册的全部事件</b>(原 11 项随机事件)——事件改由统一事件体系
 * 承载({@code com.merlinkitsune.astral_dice.event.AstralEventRegistry});② 主动技能<b>清空(留空)</b>,技能待重做。
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
        // 主动技能留空(2026-10-10 用户裁决:技能待重做) —— 不再抽任何事件、不再广播、不再登记门控计时器。
        // 后续重做时请走 AstralEventDispatcher.trigger(player, <事件 id>, AstralEventTrigger.ACTIVE_SKILL)。
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
