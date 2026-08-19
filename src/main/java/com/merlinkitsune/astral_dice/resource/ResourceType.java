package com.merlinkitsune.astral_dice.resource;
import com.merlinkitsune.astral_dice.item.MarkManager;

/**
 * 点数流派类型。
 * 当前实现:治愈(效果器,随时间衰减)、星光(固定,不衰减);
 * 标记为目标侧效果器(独立于玩家资源,由 MarkManager 管理);
 * 未来流派(如反击)在此扩展枚举并实现 {@link PlayerResource} 后注册。
 */
public enum ResourceType {
    /** 治愈:玩家级,周期结算衰减(效果器;基础点由医疗箱提供,结算点每周期减半) */
    HEALING,
    /** 星光:玩家级,固定不衰减 */
    STARLIGHT,
    /** 反击:未来流派(预留接入接口) */
    COUNTER
}
