package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
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

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        var name = event.getName();
        if (!name.getPath().startsWith("chests/")) return;

        LootTable table = event.getTable();

        // Prevent duplicate pool addition on reload
        if (table.getPool("astral_dice:star_coin") != null) return;

        boolean isBuriedTreasure = name.toString().equals("minecraft:chests/buried_treasure");
        boolean isEndCity = name.toString().equals("minecraft:chests/end_city_treasure");

        // Star Coin: 5% all chests (1-2), 9% in end city
        table.addPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(isEndCity ? 0.09f : 0.05f))
                .add(LootItem.lootTableItem(ModItems.STAR_COIN.get())
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2))))
                .name("astral_dice:star_coin")
                .build());

        // Blank Chip: ONLY in buried treasure (always)
        if (isBuriedTreasure) {
            table.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(ModItems.BLANK_CHIP.get()))
                    .name("astral_dice:blank_chip")
                    .build());
        }

        // Star Plate: 1% all chests, 5% in end city
        table.addPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(isEndCity ? 0.05f : 0.01f))
                .add(LootItem.lootTableItem(ModItems.STAR_PLATE.get()))
                .name("astral_dice:star_plate")
                .build());
    }

}
