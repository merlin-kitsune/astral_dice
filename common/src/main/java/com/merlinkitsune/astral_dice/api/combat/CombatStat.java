package com.merlinkitsune.astral_dice.api.combat;

import java.util.Locale;

/** 骰战结算中的可调属性。 */
public enum CombatStat {
    /** 攻击力。 */
    ATTACK,
    /** 防御力。 */
    DEFENSE;

    /** 小写 id。 */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 按大小写不敏感的 id 查找；找不到返回 {@code null}。 */
    public static CombatStat byId(String id) {
        if (id == null) {
            return null;
        }
        for (CombatStat stat : values()) {
            if (stat.name().equalsIgnoreCase(id)) {
                return stat;
            }
        }
        return null;
    }
}
