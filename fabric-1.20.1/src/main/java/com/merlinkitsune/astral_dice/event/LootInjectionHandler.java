package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.item.ModItems;

import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
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

        if (entity instanceof WitherBoss || entity instanceof Warden) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                    entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                    new ItemStack(ModItems.STAR_PLATE.get(), 1)));
        } else if (entity instanceof Monster) {
            if (ThreadLocalRandom.current().nextFloat() < 0.003f) {
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                        entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                        new ItemStack(ModItems.STAR_PLATE.get(), 1)));
            }
        }
    }
}
