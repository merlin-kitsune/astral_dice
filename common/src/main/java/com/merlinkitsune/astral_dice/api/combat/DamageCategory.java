package com.merlinkitsune.astral_dice.api.combat;

import java.util.Locale;

/**
 * 伤害大类：供附属 / KubeJS 按类别注册伤害链钩子。
 *
 * <p>分类只依据 {@link net.minecraft.world.damagesource.DamageSource} 与直接实体，
 * 由具体加载器线安装的 {@link DamageCategoryResolver} 给出。分类顺序固定为
 * {@code SKILL -> GUN -> MAGIC -> RANGED -> DICE_MELEE -> OTHER}：技能伤害优先于枪弹，
 * 枪弹优先于魔法/远程，避免第三方弹丸同时命中多个白名单。
 */
public enum DamageCategory {
    /** 骰战 / 近战：直接实体为生物的攻击。 */
    DICE_MELEE,
    /** 远程：箭矢、投掷物等非枪械弹射物。 */
    RANGED,
    /** 魔法 / 法术：原版 magic、法术模组伤害类型、本模组 card_spell。 */
    MAGIC,
    /** 枪弹 / 火炮：第三方枪械与火炮伤害。 */
    GUN,
    /** 技能：本模组 {@code astral_dice:skill_damage} 固定点数伤害。 */
    SKILL,
    /** 无法归类。 */
    OTHER;

    /** 小写 id，便于脚本与配置按字符串匹配。 */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 按大小写不敏感的 id 查找；找不到返回 {@code null}。 */
    public static DamageCategory byId(String id) {
        if (id == null) {
            return null;
        }
        for (DamageCategory category : values()) {
            if (category.name().equalsIgnoreCase(id)) {
                return category;
            }
        }
        return null;
    }
}
