package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.combat.DiceCombatEvents;
import com.merlinkitsune.astral_dice.effect.WeaknessRevealEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 枪匠立牌 × 神秘遗物+ 联动 —— 两条「诅咒修正」(2026-09-30 用户裁决)。
 *
 * <p>触发条件(两条修正共用):**佩戴七咒之戒 + 身上有「弱点识破」层数**
 * ({@link WeaknessRevealEffect#getStacks} &gt; 0)。缺少任一条件即完全不干预第三方行为。
 *
 * <ul>
 *   <li><b>修正第三诅咒(护甲降低)</b>:七咒之戒每 tick 通过
 *       {@code CursedRing#curioTick} → {@code getArmorModifiers} 往 {@code Attributes.ARMOR} 与
 *       {@code Attributes.ARMOR_TOUGHNESS} 上挂一个 {@code ADD_MULTIPLIED_TOTAL} 的**瞬时修饰器**
 *       (默认 -30%%),其 id 就是七咒之戒自己的物品注册 id(移植版用
 *       {@code IItemHelper.getLocation(item)} 生成)。本类在**服务器 tick 末尾**
 *       (晚于 Curios 的 {@code curioTick})按该 id 精确摘掉它 ⇒ 修正即生效且不误伤该模组其它修饰器。</li>
 *   <li><b>修正第六诅咒(死亡灵魂破裂)</b>:第三方在 {@code LivingDropsEvent}(LOWEST) 里按
 *       {@code EnigmaticHandler#canDropSoulCrystal} 决定是否把玩家的灵魂水晶「撕下」——
 *       真正的剥离动作是 {@code SoulCrystal#createCrystalFrom(player)},它只做一件事:
 *       {@code setLostCrystals(player, getLostCrystals(player) + 1)} 并返回一枚水晶物品;
 *       随后该水晶被塞进 {@code PermanentItemEntity} 掉落。**纯本模组侧无法阻止第三方在最后
 *       优先级生成掉落物**,故改为「死后补偿」:死亡瞬间快照 {@code lostCrystals},待玩家重生后
 *       (下一 tick)① 把计数还原、② 回收死亡点附近的灵魂水晶掉落物,等价于「没有掉落」。</li>
 * </ul>
 *
 * <p>⚠️ 两条线适配点(forge-1.20.1 版不同):
 * <ul>
 *   <li>模组 id / 物品 id:{@code enigmaticlegacyplus} ↔ {@code enigmaticlegacy};</li>
 *   <li>护甲诅咒修饰器:<b>1.21.1 移植版</b>用「物品注册 id」作 modifier id
 *       ⇒ 可直接按 {@link ResourceLocation} 匹配;</li>
 *   <li>第三方 {@code SoulCrystal} 的类名与调用形态(static ↔ 实例)不同 ⇒ **一律反射**
 *       (⚠️ 但**反射目标——类名/方法名/参数与返回类型/static-还是-实例/包路径——必须逐条来自实物**,
 *       禁止凭记忆或推测;见 AGENTS《第三方联动取源与反射纪律》),失败即永久关闭
 *       (未安装该模组时不会有任何日志噪音/异常)。</li>
 * </ul>
 *
 * <p>本类不持有任何玩法状态(仅死亡待办 Map),所有判定都以第三方物品/效果为准。
 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class MosesEnigmaticLink {
    private static final Logger LOGGER = LoggerFactory.getLogger(MosesEnigmaticLink.class);

    // ── 分线常量(forge-1.20.1 版不同) ────────────────────────────────────────

    /** 第三方模组 id(本线 = 神秘遗物+) */
    private static final String SOUL_CRYSTAL_ID = "enigmaticlegacyplus:soul_crystal";
    /** 七咒之戒的物品注册 id;移植版把它同时用作护甲诅咒修饰器的 id */
    private static final ResourceLocation ARMOR_CURSE_MODIFIER =
            ResourceLocation.parse("enigmaticlegacyplus:cursed_ring");
    /** 第三方「灵魂水晶」工具类(反射用,避免硬引用第三方内部类) */
    private static final String SOUL_CRYSTAL_CLASS =
            "auviotre.enigmatic.legacy.contents.item.misc.SoulCrystal";

    /** 回收掉落物时的搜索半径(格):掉落物就生成在死亡点原地,给足余量即可 */
    private static final double SOUL_CLEANUP_RADIUS = 12.0;

    /** 死亡待办:玩家跨过死亡→重生的那几 tick,重生后按此补偿 */
    private record Pending(int lostBefore, ServerLevel level, double x, double y, double z) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    // 反射句柄(懒初始化;失败即永久关闭,不对未装/改版的第三方报错)
    private static Method getLostMethod;
    private static Method setLostMethod;
    private static Method updateSoulMapMethod;
    /** 反射调用的目标:null = 第三方为 static 方法;否则为灵魂水晶物品实例(1.20.1 是实例方法) */
    private static Object soulApiTarget;
    private static boolean soulApiDisabled;

    private MosesEnigmaticLink() {
    }

    // ── 触发条件 ──────────────────────────────────────────────────────────────

    /**
     * 联动是否生效:佩戴七咒之戒 **且** 拥有「弱点识破」层数。
     */
    public static boolean isActive(Player player) {
        if (player == null) return false;
        if (WeaknessRevealEffect.getStacks(player) <= 0) return false;
        return DiceCombatEvents.hasEnigmaticCurse(player);
    }

    // ── 事件入口 ──────────────────────────────────────────────────────────────

    /**
     * 服务器 tick 末尾:此时所有玩家 tick(含 Curios 的 {@code curioTick})都已跑完,
     * 第三方刚重新挂上的护甲诅咒修饰器会在本轮被摘掉,且不会被同 tick 重新加回。
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isActive(player)) correctArmorCurse(player);
        }
        if (!PENDING.isEmpty()) processPending(server);
    }

    /** 死亡瞬间:快照 {@code lostCrystals}(第三方随后会在掉落事件里把它 +1) */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!isActive(player)) return;
        if (!initSoulApi()) return;
        int lost = getLostCrystals(player);
        if (lost < 0) return;
        PENDING.put(player.getUUID(), new Pending(lost, (ServerLevel) player.level(),
                player.getX(), player.getY(), player.getZ()));
    }

    // ── 修正第三诅咒:护甲 ────────────────────────────────────────────────────

    private static void correctArmorCurse(Player player) {
        removeCurseModifier(player.getAttribute(Attributes.ARMOR));
        removeCurseModifier(player.getAttribute(Attributes.ARMOR_TOUGHNESS));
    }

    /**
     * 只摘**七咒之戒自己挂的那个**修饰器(按 id 精确匹配)。
     * ⚠️ 刻意不遍历命名空间删除:同一属性上该模组可能还挂着别的正向修饰器。
     */
    private static void removeCurseModifier(AttributeInstance attribute) {
        if (attribute == null) return;
        if (attribute.getModifier(ARMOR_CURSE_MODIFIER) != null) {
            attribute.removeModifier(ARMOR_CURSE_MODIFIER);
        }
    }

    // ── 修正第六诅咒:灵魂水晶 ────────────────────────────────────────────────

    /** 玩家重生(下一 tick 起)后:还原丢失计数 + 回收死亡点的灵魂水晶掉落物 */
    private static void processPending(MinecraftServer server) {
        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, Pending> entry : PENDING.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            // 死亡到重生之间玩家不是 alive;等重生那一 tick 再处理
            if (player == null || !player.isAlive()) continue;
            Pending pending = entry.getValue();
            if (getLostCrystals(player) > pending.lostBefore()) {
                setLostCrystals(player, pending.lostBefore());
                refreshSoulMap(player);
                LOGGER.debug("[Astral Dice][Moses] 第六诅咒已修正:{} 的灵魂水晶计数还原为 {}",
                        player.getName().getString(), pending.lostBefore());
            }
            discardDroppedCrystals(pending);
            finished.add(entry.getKey());
        }
        for (UUID id : finished) {
            PENDING.remove(id);
        }
    }

    /**
     * 回收死亡点附近的灵魂水晶掉落物。
     * ⚠️ 纯本模组侧无法阻止第三方在 {@code LivingDropsEvent}(LOWEST) 里生成它,故在重生后回收;
     * 只清**灵魂水晶**这一种物品,不动玩家其余掉落。
     */
    private static void discardDroppedCrystals(Pending pending) {
        ServerLevel level = pending.level();
        if (level == null) return;
        Item crystal = soulCrystalItem();
        if (crystal == Items.AIR) return;
        AABB box = new AABB(
                pending.x() - SOUL_CLEANUP_RADIUS, pending.y() - SOUL_CLEANUP_RADIUS, pending.z() - SOUL_CLEANUP_RADIUS,
                pending.x() + SOUL_CLEANUP_RADIUS, pending.y() + SOUL_CLEANUP_RADIUS, pending.z() + SOUL_CLEANUP_RADIUS);
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (drop.getItem().is(crystal)) {
                drop.discard();
            }
        }
    }

    // ── 第三方反射桥(不可用时静默停用) ───────────────────────────────────────

    private static Item soulCrystalItem() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(SOUL_CRYSTAL_ID));
    }

    private static synchronized boolean initSoulApi() {
        if (soulApiDisabled) return false;
        if (getLostMethod != null) return true;
        try {
            Class<?> type = Class.forName(SOUL_CRYSTAL_CLASS);
            Method probe = type.getMethod("getLostCrystals", Player.class);
            if (!Modifier.isStatic(probe.getModifiers())) {
                // 1.20.1 的 Enigmatic Legacy 是实例方法 ⇒ 目标取该物品的单例实例
                Item item = soulCrystalItem();
                if (item == Items.AIR) {
                    soulApiDisabled = true;
                    return false;
                }
                soulApiTarget = item;
            }
            setLostMethod = type.getMethod("setLostCrystals", Player.class, int.class);
            updateSoulMapMethod = type.getMethod("updatePlayerSoulMap", Player.class);
            getLostMethod = probe;
            return true;
        } catch (Throwable t) {
            soulApiDisabled = true;
            LOGGER.warn("[Astral Dice][Moses] 未找到神秘遗物(+)的灵魂水晶 API,第六诅咒修正停用: {}", t.toString());
            return false;
        }
    }

    /** 读取玩家的「已丢失灵魂水晶」计数;反射不可用时返回 -1 */
    private static int getLostCrystals(Player player) {
        if (!initSoulApi()) return -1;
        try {
            return (int) getLostMethod.invoke(soulApiTarget, player);
        } catch (Throwable t) {
            soulApiDisabled = true;
            LOGGER.warn("[Astral Dice][Moses] 读取灵魂水晶计数失败,第六诅咒修正停用: {}", t.toString());
            return -1;
        }
    }

    private static void setLostCrystals(Player player, int value) {
        if (!initSoulApi()) return;
        try {
            setLostMethod.invoke(soulApiTarget, player, value);
        } catch (Throwable t) {
            soulApiDisabled = true;
            LOGGER.warn("[Astral Dice][Moses] 写回灵魂水晶计数失败,第六诅咒修正停用: {}", t.toString());
        }
    }

    /** 计数变化后让第三方按新计数重算玩家的最大生命修饰器 */
    private static void refreshSoulMap(Player player) {
        if (!initSoulApi()) return;
        try {
            updateSoulMapMethod.invoke(soulApiTarget, player);
        } catch (Throwable t) {
            LOGGER.warn("[Astral Dice][Moses] 刷新灵魂映射失败: {}", t.toString());
        }
    }

    // 供命令/调试查询:本次会话内是否还有待补偿的死亡
    public static boolean hasPending(UUID playerId) {
        return PENDING.containsKey(playerId);
    }
}
