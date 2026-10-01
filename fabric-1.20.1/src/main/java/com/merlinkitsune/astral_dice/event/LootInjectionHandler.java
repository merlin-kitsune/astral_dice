package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDropsEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 实体掉落注入(箱子战利品见 {@code loot/FabricLootInjector})。
 *
 * <h2>Fabric 1.20.1 移植说明</h2>
 * 本类在 Forge 侧有**两个**职责:① {@code LivingDropsEvent} 给凋灵/监守者必掉星盘、
 * 普通怪物 0.3% 掉星盘;② {@code LootTableLoadEvent} 往箱表注入四个池。
 * 职责 ② **已整体迁到** {@code loot/FabricLootInjector}(改用 FAPI 的
 * {@code LootTableEvents.MODIFY})——因为 Fabric 没有 {@code LootTableLoadEvent},
 * 而且那一侧还同时承担了原 Global Loot Modifier 的等价物。
 *
 * <p>职责 ① 保持原逻辑**零改动**:{@code LivingDropsEvent} 在 Fabric 侧无等价回调
 * (FAPI 的 {@code ServerLivingEntityEvents.AFTER_DEATH} 触发时掉落已经生成完毕),
 * 由 mixin 桥在 {@code LivingEntity#dropAllDeathLoot} 之后派发。
 */
public class LootInjectionHandler {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        // 星盘掉落(2026-09-30 用户指定:由「凋灵/监守者必掉 1 个 + 其余 Monster 0.3%」扩展为分档口径)
        // —— 档位、数量与判定顺序见 starPlateDropCount。
        int starPlates = starPlateDropCount(entity);
        if (starPlates > 0) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                    entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                    new ItemStack(ModItems.STAR_PLATE.get(), starPlates)));
        }

        rollKillStarCoin(event);
    }
    // ── 以下两方法由 1.3.5（multi-main）移植 ─────────────────────────────────────────
    // ⚠️ 上游同批新增的 `onLootTableLoad(LootTableLoadEvent)` **故意不移植**：
    //    那是 Forge 的箱子战利品事件，本线没有该事件 —— 箱子注入整体由
    //    `loot/FabricLootInjector`（Fabric API 的 `LootTableEvents.MODIFY`）承担（见本类顶部 javadoc）。

    /**
     * 星盘掉落档位(2026-09-30 用户指定扩展)。返回本次应掉落的星盘数量,{@code 0} = 不掉。
     *
     * <p>档位:凋灵 5 个;监守者 3-5 个;远古守卫者 1-3 个;恶魂 1 个;
     * 凋灵骷髅 / 守卫者 5% 掉 1 个;其余 {@link Monster} 0.3% 掉 1 个。
     *
     * <p>⚠️ **判定顺序即优先级,自上而下短路 —— 所有档位互斥**(同一只怪不会被判两次):
     * ① {@code ElderGuardian} **继承** {@code Guardian}(远古守卫者是守卫者的子类) ⇒ 必须先判远古守卫者,
     * 否则 100% 档会被后面的 5% 档吞掉;
     * ② 凋灵({@code WitherBoss})与凋灵骷髅({@code WitherSkeleton})是两个互不相干的类(无继承关系);
     * ③ 恶魂 / 凋灵骷髅 / 守卫者 / 远古守卫者 / 监守者**都是** {@link Monster} 子类 ⇒ 必须排在末尾的
     * 0.3% 兜底档**之前**,否则会被重复判定一次(合成概率 1-(1-p)^2,静默放大)。
     *
     * <p>⚠️ 本方法**不是**敌对判定入口,只是掉落池口径 —— 不得改调 {@code HostileTargets}
     * (见 AGENTS.md「敌对目标」纪律的「唯一豁免」条)。
     */
    private static int starPlateDropCount(LivingEntity entity) {
        if (entity instanceof WitherBoss) {
            return 5;
        }
        if (entity instanceof Warden) {
            return 3 + ThreadLocalRandom.current().nextInt(3);          // 3-5
        }
        if (entity instanceof ElderGuardian) {
            return 1 + ThreadLocalRandom.current().nextInt(3);          // 1-3
        }
        if (entity instanceof Ghast) {
            return 1;
        }
        if (entity instanceof WitherSkeleton || entity instanceof Guardian) {
            return ThreadLocalRandom.current().nextFloat() < 0.05f ? 1 : 0;
        }
        if (entity instanceof Monster) {
            return ThreadLocalRandom.current().nextFloat() < 0.003f ? 1 : 0;
        }
        return 0;
    }
    /**
     * 击杀者专属的星币掉落(2026-09-30 用户指定)。
     *
     * <p>仅当**击杀者是玩家**时判定(间接击杀如箭矢/投掷物同样算,{@code DamageSource#getEntity}
     * 返回的是施加者);被击杀生物按类别取概率:僵尸 / 僵尸猪灵 1%、末影人 / 猪灵 3%、猪灵蛮兵 20%,
     * 每次死亡独立判定一次,掉落 1 枚星币。
     *
     * <p>⚠️ 与上面的星盘掉落**互不影响** —— 星盘仍按原规则(凋灵/监守者必掉、其它 Monster 0.3%)
     * 独立判定,本方法只做叠加。
     *
     * <p>⚠️ 类别判定顺序有讲究:{@code PiglinBrute} **不是** {@code Piglin} 的子类(两者都直接继承
     * {@code AbstractPiglin}),所以必须先判蛮兵,否则会被 3% 档吞掉;
     * 「僵尸」按 {@link Zombie} 类族判定(含尸壳 / 溺尸 / 僵尸村民,僵尸猪灵也继承自 Zombie,
     * 三者的用户口径概率一致故可归并)。
     */
    private static void rollKillStarCoin(LivingDropsEvent event) {
        var entity = event.getEntity();
        if (!(event.getSource().getEntity() instanceof Player)) return;
        double chance;
        if (entity instanceof PiglinBrute) {
            chance = 0.20;
        } else if (entity instanceof Piglin || entity instanceof EnderMan) {
            chance = 0.03;
        } else if (entity instanceof Zombie) {
            chance = 0.01;
        } else {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                new ItemStack(ModItems.STAR_COIN.get(), 1)));
    }
}
