package com.merlinkitsune.astral_dice.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * 「敌对目标」判定的**唯一入口**(2026-09-14 用户裁决,必须遵守)。
 *
 * <p>口径:{@code 敌对目标 = 敌对生物 ∪ 已被激怒的中立生物}
 * <ul>
 *   <li><b>敌对生物</b>:{@link Enemy} 实例 —— 含 {@code Monster} 全部子类,以及
 *       {@code Ghast}/{@code Phantom}/{@code EnderDragon}/{@code Slime}(含岩浆怪)/
 *       {@code Shulker}/{@code Zoglin}/{@code Hoglin}。**平静的敌对类中立生物
 *       (末影人/僵尸猪灵/猪灵/猪灵蛮兵/疣猪兽)同样计入** —— 它们本身就是 {@link Enemy}。</li>
 *   <li><b>已被激怒的中立生物</b>:{@link NeutralMob} 实例且 {@code isAngry()}
 *       (= {@code getRemainingPersistentAngerTime() > 0},被激怒后 20~39 秒)为真。
 *       全原版 {@code NeutralMob} 直接实现者仅 6 个:{@code EnderMan}/{@code ZombifiedPiglin}
 *       (二者即 {@link Enemy})与 <b>狼 / 铁傀儡 / 北极熊 / 蜜蜂</b>(仅这 4 个靠 anger 判定进入敌对集合)。</li>
 * </ul>
 *
 * <p>熊猫/骆驼/山羊/羊驼/行商羊驼/海豚/狐狸等**不是** {@code NeutralMob},永不视为敌对目标。
 *
 * <p><b>禁止</b>在玩法代码里再写裸的 {@code instanceof Enemy} 来判定敌对目标 —— 那会漏掉
 * 被激怒的狼/铁傀儡/北极熊/蜜蜂。新增判定一律调用本类。
 */
public final class HostileTargets {
    private HostileTargets() {
    }

    /** 该实体是否为「敌对目标」(敌对生物,或已被激怒的中立生物)。 */
    public static boolean isHostile(Entity entity) {
        if (entity == null) return false;
        if (entity instanceof Enemy) return true;
        return entity instanceof NeutralMob neutral && neutral.isAngry();
    }
}
