package com.merlinkitsune.astral_dice.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.Tags;

import java.lang.reflect.Method;

/**
 * Boss 战判定工具。
 * 判定规则:
 * 1. 生物具有 c:bosses 实体标签(Common Tags,覆盖末影龙/凋灵/监守者及灾变等模组的 boss);
 * 2. 生物暴露了返回非 null 的 boss 事件(屏幕上方会出现 boss 血条)。
 */
public final class BossEntityUtil {
    private BossEntityUtil() {
    }

    public static boolean isBossEntity(LivingEntity entity) {
        if (entity.getType().is(Tags.EntityTypes.BOSSES)) return true;
        if (!(entity instanceof Mob mob)) return false;
        // 屏幕上方 boss 血条:实体暴露 getBossEvent()/getBossBar() 且返回非 null
        for (String methodName : new String[]{"getBossEvent", "getBossBar"}) {
            try {
                Method m = mob.getClass().getMethod(methodName);
                if (m.invoke(mob) != null) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
