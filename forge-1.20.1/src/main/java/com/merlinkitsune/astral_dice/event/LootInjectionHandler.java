package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
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

        rollKillStarCoin(event);
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        var name = event.getName();
        if (!name.getPath().startsWith("chests/")) return;

        // ⚠️ 自有命名空间的表必须早退（2026-09-27 修复「双通道重复注入」）：
        //    下方判据只按**路径前缀**匹配，而自有表 `astral_dice:chests/star_plate` 的 path 同样是 `chests/star_plate`
        //    ⇒ 它会被一并注入下面这一整套 astral_dice:* 池。偏偏 GLM `star_plate_all_chests` 又是**无条件**（100%）
        //    把该表挂到原版箱表上（`astral_dice:add_table`），于是「开一个箱子 = 主表 + 附加表各滚一遍」——
        //    四池被二次注入放大（两次独立判定 ⇒ 1−(1−p)²）：星币 5%→9.75%、空白筹码 3%→5.91%、玻璃骰子 2%→3.96%；星盘特例（本表另含 5% 星盘权重）⇒ 修复前 6.89%、修复后 5.95%。
        //    自有表由 GLM 自行引用，**不得**再由本 handler 注入。
        if (name.getNamespace().equals(com.merlinkitsune.astral_dice.AstralDiceMod.MODID)) return;

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

        // Blank Chip: 埋藏的宝藏 100% 必出;其余战利品箱子 3%
        // (2026-09-27 用户需求:略微提高空白筹码的战利品获取概率 —— 原先其它箱子 0%)
        if (isBuriedTreasure) {
            table.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(ModItems.BLANK_CHIP.get()))
                    .name("astral_dice:blank_chip")
                    .build());
        } else {
            table.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(0.03f))
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

        // Glass Dice: 末地城 5%;其余战利品箱子 2%(数量固定 1)
        // (2026-09-27 用户需求:战利品箱中增加玻璃骰子,并略微提高其掉落率 —— 原先战利品箱 0%)
        table.addPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(isEndCity ? 0.05f : 0.02f))
                .add(LootItem.lootTableItem(ModItems.GLASS_DICE.get()))
                .name("astral_dice:glass_dice")
                .build());
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
