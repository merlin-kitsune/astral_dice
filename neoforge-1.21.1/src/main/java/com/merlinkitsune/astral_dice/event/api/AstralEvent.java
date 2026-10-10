package com.merlinkitsune.astral_dice.event.api;

import com.merlinkitsune.astral_dice.event.AstralEventSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * 一条事件的注册项(元数据 + 触发器 + 效果 + 计时器)。
 *
 * <p>2026-10-10 事件体系重写:事件从「散在各立牌里的 switch 分支」提升为<b>可注册的数据</b>。
 * 字段口径见 {@code docs/specs/event-system-rewrite.md}。
 *
 * @param id                    事件 id(astral_dice:<ascii_snake_case>)
 * @param nameKey               三语名称键(event.astral_dice.<id>.name)
 * @param icon                  图标材质(astral_dice:textures/gui/event/<id>.png);有阶级的事件用主图,
 *                              阶内换图由 {@link AstralEventTier} 决定
 * @param polarity              事件类型(纯标签,仅界面染色)
 * @param secretOnly            「秘密侦探专属」阶级事件(目前仅 investigation_stage)
 * @param inPublicPool          是否进入公共事件池(可被「抽事件」物品/方块抽到)
 * @param triggers              允许的触发来源
 * @param radius                作用半径(格);默认 {@link AstralEventSystem#EVENT_TARGET_RADIUS}
 * @param effect                事件效果(本轮全部空壳)
 * @param defaultDurationTicks  默认持续时长(0 = 无计时器;本轮 13 条正式事件全部为 0)
 */
public record AstralEvent(
        ResourceLocation id,
        String nameKey,
        ResourceLocation icon,
        AstralEventPolarity polarity,
        boolean secretOnly,
        boolean inPublicPool,
        Set<AstralEventTrigger> triggers,
        double radius,
        AstralEventEffect effect,
        int defaultDurationTicks
) {

    /** 事件显示名(按类型染色 —— 事件类型是纯标签,只影响这里)。 */
    public Component displayName() {
        return Component.translatable(this.nameKey).withStyle(this.polarity.color());
    }

    /** 该事件是否允许由指定来源触发。 */
    public boolean allows(AstralEventTrigger trigger) {
        return this.triggers.contains(trigger);
    }

    /** 是否登记计时器(本轮正式事件全为 false;仅临时测试事件为 true)。 */
    public boolean timed() {
        return this.defaultDurationTicks > 0;
    }
}
