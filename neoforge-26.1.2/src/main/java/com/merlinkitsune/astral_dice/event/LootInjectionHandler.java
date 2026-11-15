package com.merlinkitsune.astral_dice.event;


import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.data.LootInjectionData;
import com.merlinkitsune.astral_dice.data.LootRule;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.network.DamageNumberPayload;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.item.sign.BonnieSignItem;
import com.merlinkitsune.starengine.item.BossEntityUtil;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.sign.HaiqingSignItem;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.InvestigationEventUtil;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.item.sign.MisakiSignItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.PadmanSignItem;
import com.merlinkitsune.astral_dice.item.sign.JasmineSignItem;
import com.merlinkitsune.astral_dice.item.sign.LuluSignItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;   // 26.1.2 起 WitherSkeleton 移入 monster.skeleton 子包
import net.minecraft.world.entity.monster.zombie.Zombie;   // 26.1.2 起 Zombie 移入 monster.zombie 子包
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.merlinkitsune.astral_dice.item.chip.StarCoinHammerChipItem;
import com.merlinkitsune.astral_dice.item.chip.BufferShieldChipItem;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.client.KeyBindingSetup;
import com.merlinkitsune.astral_dice.combat.DiceCombatContext;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.sign.FenSignItem;
import com.merlinkitsune.astral_dice.item.card.EffectCardPeriod;
import com.merlinkitsune.astral_dice.item.chip.BankCardUnlimitedChipItem;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.item.chip.CursedSwordChipItem;
import com.merlinkitsune.astral_dice.item.chip.FriendshipBadgeChipItem;
import com.merlinkitsune.astral_dice.item.chip.RevengeHalberdChipItem;
import com.merlinkitsune.astral_dice.item.chip.SatelliteChipItem;
import com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class LootInjectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LootInjectionHandler.class);
    private static final String VANILLA_NAMESPACE = "minecraft";
    private static final List<String> CHEST_KINDS = List.of(LootRule.KIND_CHEST);
    private static final List<String> ENTITY_KINDS = List.of(LootRule.KIND_ENTITY, LootRule.KIND_MONSTER);
    private static final List<PendingTable> PENDING = Collections.synchronizedList(new ArrayList<>());
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        Identifier name = event.getName();
        if (name == null || !VANILLA_NAMESPACE.equals(name.getNamespace())) {
            return;
        }
        if (kindsFor(name).isEmpty()) {
            return;
        }
        enqueue(name, event.getTable());
    }

    public static void enqueue(Identifier id, LootTable table) {
        if (id != null && table != null) {
            PENDING.add(new PendingTable(id, table));
        }
    }

    public static List<PendingTable> drainPending() {
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return List.of();
            }
            List<PendingTable> drained = new ArrayList<>(PENDING);
            PENDING.clear();
            return drained;
        }
    }

    public static void injectPending(List<PendingTable> pending, LootInjectionData data) {
        if (pending == null || pending.isEmpty() || data == null || data.isEmpty()) {
            return;
        }
        for (PendingTable entry : pending) {
            try {
                List<String> kinds = kindsFor(entry.id());
                if (kinds.isEmpty()) {
                    continue;
                }
                boolean monster = isMonsterTable(entry.id());
                for (String kind : kinds) {
                    LootRule rule = data.bestRule(kind, entry.id().toString(), monster);
                    if (rule != null) {
                        inject(entry.table(), rule);
                    }
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("[astral_dice] 战利品注入失败 {}: {}", entry.id(), ex.getMessage());
            }
        }
    }

    private static List<String> kindsFor(Identifier tableId) {
        String path = tableId.getPath();
        if (path.startsWith("chests/")) {
            return CHEST_KINDS;
        }
        if (path.startsWith("entities/")) {
            return ENTITY_KINDS;
        }
        return List.of();
    }

    private static boolean isMonsterTable(Identifier tableId) {
        String path = tableId.getPath();
        if (!path.startsWith("entities/")) {
            return false;
        }
        Identifier entityId = Identifier.fromNamespaceAndPath(
                tableId.getNamespace(), path.substring("entities/".length()));
        return BuiltInRegistries.ENTITY_TYPE.get(entityId)
                .map(ref -> ref.value().getCategory() == MobCategory.MONSTER)
                .orElse(false);
    }

    private static void inject(LootTable table, LootRule rule) {
        String poolName = AstralDiceMod.MODID + ":" + rule.id();
        if (table.getPool(poolName) != null) {
            return;
        }
        Identifier target = Identifier.tryParse(rule.table());
        if (target == null) {
            warnOnce(rule.id(), "战利品规则 {} 的目标表 id 非法: {}，已跳过", rule.id(), rule.table());
            return;
        }
        String expectedPrefix = LootRule.KIND_CHEST.equals(rule.targetKind()) ? "chests/" : "entities/";
        if (!target.getPath().startsWith(expectedPrefix)) {
            warnOnce(rule.id(), "战利品规则 {} 的目标表 {} 与 target_kind={} 不匹配，已跳过", rule.id(), target, rule.targetKind());
            return;
        }
        LootPool.Builder pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(NestedLootTable.lootTableReference(ResourceKey.create(Registries.LOOT_TABLE, target)))
                .name(poolName);
        if (rule.requirePlayerKill()) {
            pool.when(LootItemKilledByPlayerCondition.killedByPlayer());
        }
        table.addPool(pool.build());
    }

    private static void warnOnce(String key, String format, Object... args) {
        if (WARNED.add(key)) {
            LOGGER.warn("[astral_dice] " + format, args);
        }
    }

    public static final class PendingTable {
        private final Identifier id;
        private final LootTable table;

        private PendingTable(Identifier id, LootTable table) {
            this.id = id;
            this.table = table;
        }

        public Identifier id() {
            return this.id;
        }

        public LootTable table() {
            return this.table;
        }
    }


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
