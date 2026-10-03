package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.combat.DiceCombatEvents;
import com.merlinkitsune.astral_dice.effect.WeaknessRevealEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.TickEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDeathEvent;
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
 * 枪匠立牌 × 神秘遗物 联动 —— 两条「诅咒修正」(2026-09-30 用户裁决)。
 *
 * <p>触发条件(两条修正共用):**佩戴七咒之戒 + 身上有「弱点识破」层数**
 * ({@link WeaknessRevealEffect#getStacks} &gt; 0)。缺少任一条件即完全不干预第三方行为。
 *
 * <ul>
 *   <li><b>修正第三诅咒(护甲降低)</b>:七咒之戒每 tick 通过 {@code CursedRing#curioTick} →
 *       {@code getAttributeModifiers} 往 {@code Attributes.ARMOR} 与 {@code Attributes.ARMOR_TOUGHNESS}
 *       上挂一个 {@code MULTIPLY_TOTAL} 的**瞬时修饰器**(默认 -30%)。⚠️ 与 1.21.1 移植版不同,
 *       **1.20.1 原版用的是固定 UUID**(见下方两个常量,均从 EnigmaticLegacy-2.30.1 的字节码取证),
 *       故本线按 UUID 精确摘除;服务器 tick 末尾执行,保证晚于 Curios 的 {@code curioTick}。</li>
 *   <li><b>修正第六诅咒(死亡灵魂破裂)</b>:第三方在 {@code LivingDropsEvent}(LOWEST) 里按
 *       {@code EnigmaticHandler#canDropSoulCrystal} 决定是否把玩家的灵魂水晶「撕下」——
 *       真正的剥离动作是 {@code SoulCrystal#createCrystalFrom(player)},它只做一件事:
 *       {@code setLostCrystals(player, getLostCrystals(player) + 1)} 并返回一枚水晶物品;
 *       随后该水晶被装进第三方的「永久掉落物」实体掉落 —— 🚨 该实体 {@code extends Entity}、
 *       **不是**原版 {@code ItemEntity},必须按第三方实体类检索(2026-10-03 修的缺陷就出在这里:
 *       旧实现按 {@code ItemEntity} 找,永远匹配不到 ⇒ 回收静默失效,水晶永久残留)。
 *       **纯本模组侧无法阻止第三方在最后优先级生成掉落物**,故改为「死后补偿」:
 *       死亡瞬间快照 {@code lostCrystals},此后 ① **每 tick** 回收死亡点附近「本次死亡新生成」的
 *       灵魂水晶、② 待玩家重生后把计数还原,等价于「没有掉落」。</li>
 * </ul>
 *
 * <p>⚠️ 两条线适配点(neoforge-1.21.1 版不同):
 * <ul>
 *   <li>模组 id / 物品 id:{@code enigmaticlegacy} ↔ {@code enigmaticlegacyplus};</li>
 *   <li>护甲诅咒修饰器:本线是**固定 UUID**,移植版是「物品注册 id」;</li>
 *   <li>第三方 {@code SoulCrystal} 的类名与调用形态(1.20.1 是**实例方法**)不同 ⇒ **一律反射**,
 *       失败即永久关闭(未安装该模组时不会有任何日志噪音/异常)。</li>
 * </ul>
 *
 * <p>本类不持有任何玩法状态(仅死亡待办 Map),所有判定都以第三方物品/效果为准。
 *
 * <p><b>Fabric 平台差异</b>:本线**没有** {@code @Mod.EventBusSubscriber} 自动注册机制
 * (见 {@code AstralDiceMod} 注释:注解在 Fabric 无对应物)⇒ 本类由
 * {@code AstralDiceMod#onInitialize} 里的 {@code LoaderBus.INSTANCE.register(...)} 显式登记。
 * 两个事件入口的签名/语义与 Forge 版一致:{@code TickEvent.ServerTickEvent} 的
 * {@code phase}/{@code getServer()}、以及本线平台层的 {@code LivingDeathEvent}。
 */
public final class MosesEnigmaticLink {
    private static final Logger LOGGER = LoggerFactory.getLogger(MosesEnigmaticLink.class);

    // ── 分线常量(neoforge-1.21.1 版不同) ─────────────────────────────────────

    /** 第三方模组 id(本线 = 神秘遗物) */
    private static final String SOUL_CRYSTAL_ID = "enigmaticlegacy:soul_crystal";

    /**
     * 护甲诅咒修饰器的 UUID(EnigmaticLegacy-2.30.1 {@code CursedRing#getAttributeModifiers} 内硬编码)。
     * ⚠️ 该 UUID 的 name 分别是 {@code enigmaticlegacy:armor_modifier} /
     * {@code enigmaticlegacy:armor_toughness_modifier};1.21.1 移植版改为「物品注册 id」= 两线必须分别适配。
     */
    private static final UUID ARMOR_CURSE_UUID = UUID.fromString("457d0ac3-69e4-482f-b636-22e0802da6bd");
    private static final UUID ARMOR_TOUGHNESS_CURSE_UUID = UUID.fromString("95e70d83-3d50-4241-a835-996e1ef039bb");

    /** 第三方「灵魂水晶」工具类(反射用,避免硬引用第三方内部类) */
    private static final String SOUL_CRYSTAL_CLASS = "com.aizistral.enigmaticlegacy.items.SoulCrystal";

    /**
     * 第三方「永久掉落物」实体类 —— 灵魂水晶的**载体**。
     *
     * <p>🚨 它 {@code extends Entity},**不是**原版 {@code ItemEntity}(已用实物 jar 的 {@code javap} 取证)。
     * 2026-10-03 修的缺陷正出在这里:旧实现按 {@code ItemEntity} 检索,永远匹配不到水晶 ⇒ 回收是静默的
     * no-op,水晶永久残留;又因为计数已被本类还原,{@code SoulCrystal#retrieveSoulFromCrystal} 会返回
     * {@code false} ⇒ **连主人也捡不起来**(实测症状:死亡点留一个无法拾取的灵魂水晶)。
     */
    private static final String PERMANENT_ITEM_ENTITY_CLASS =
            "com.aizistral.enigmaticlegacy.entities.PermanentItemEntity";

    /**
     * 只回收「本次死亡刚刚生成」的载体:实体自带的 {@code age} 超过该值即视为历史遗留,不动它。
     *
     * <p>⚠️ 防误伤:同一玩家早先(未触发本联动时)死在附近留下的灵魂水晶是**合法的灵魂回收物**,
     * 不该被本类删除。新生成的水晶 {@code age} 为个位数,故 40 tick(2 秒)足够区分。
     */
    private static final int FRESH_SOUL_CRYSTAL_MAX_AGE = 40;

    /** 回收掉落物时的搜索半径(格):掉落物就生成在死亡点原地,给足余量即可 */
    private static final double SOUL_CLEANUP_RADIUS = 12.0;

    /** 死亡待办:记录死亡瞬间的计数与坐标。回收在**死亡当 tick 起每 tick** 做;
     *  计数还原必须等玩家重生(PlayerEvent.Clone 会用旧计数重算最大生命)。 */
    private record Pending(int lostBefore, ServerLevel level, double x, double y, double z) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    // 反射句柄(懒初始化;失败即永久关闭,不对未装/改版的第三方报错)
    private static Method getLostMethod;
    private static Method setLostMethod;
    private static Method updateSoulMapMethod;
    /** 反射调用的目标:null = 第三方为 static 方法;否则为灵魂水晶物品实例(本线是实例方法) */
    private static Object soulApiTarget;
    private static boolean soulApiDisabled;

    // 「水晶载体」反射句柄 —— 与上面的灵魂水晶 API **相互独立**:
    // 解析失败只停用「回收」,不影响「计数还原」(避免因第三方改包名而整体失效)。
    private static Class<?> holderClass;
    private static Method holderGetItem;
    private static Method holderGetOwnerId;
    /** 可缺省:解析不到就不做「新生」过滤(退回「类 + 主人 + 物品」三重判定)。 */
    private static Method holderGetAge;
    private static boolean holderLookupDisabled;

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
     * 服务器 tick 末尾({@code Phase.END}):此时所有玩家 tick(含 Curios 的 {@code curioTick})
     * 都已跑完,第三方刚重新挂上的护甲诅咒修饰器会在本轮被摘掉,且不会被同 tick 重新加回。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
        removeCurseModifier(player.getAttribute(Attributes.ARMOR), ARMOR_CURSE_UUID);
        removeCurseModifier(player.getAttribute(Attributes.ARMOR_TOUGHNESS), ARMOR_TOUGHNESS_CURSE_UUID);
    }

    /**
     * 只摘**七咒之戒自己挂的那个**修饰器(按 UUID 精确匹配)。
     * ⚠️ 刻意不按 name 前缀批量删除:同一属性上该模组可能还挂着别的正向修饰器。
     */
    private static void removeCurseModifier(AttributeInstance attribute, UUID modifierId) {
        if (attribute == null) return;
        AttributeModifier existing = attribute.getModifier(modifierId);
        if (existing != null) {
            attribute.removeModifier(existing);
        }
    }

    // ── 修正第六诅咒:灵魂水晶 ────────────────────────────────────────────────

    /**
     * 每 tick 处理死亡待办:① 回收水晶(**死亡当 tick 就能做**)② 计数还原(**必须等重生**)。
     */
    private static void processPending(MinecraftServer server) {
        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, Pending> entry : PENDING.entrySet()) {
            Pending pending = entry.getValue();
            // ① 水晶在死亡当 tick 的 LivingDropsEvent(LOWEST) 里生成,而本方法跑在
            //    ServerTickEvent.Post(当 tick 末尾)⇒ **同一拍**就能看到并回收它。
            //    这样既不依赖「玩家一定会重生」(掉线/重启都不再留下水晶),也把残留窗口压到 0。
            discardDroppedCrystals(entry.getKey(), pending);
            // ② 计数还原必须等重生那一刻:PlayerEvent.Clone 会用「旧计数」重算最大生命修饰器。
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) continue;
            if (getLostCrystals(player) > pending.lostBefore()) {
                setLostCrystals(player, pending.lostBefore());
                refreshSoulMap(player);
                LOGGER.debug("[Astral Dice][Moses] 第六诅咒已修正:{} 的灵魂水晶计数还原为 {}",
                        player.getName().getString(), pending.lostBefore());
            }
            finished.add(entry.getKey());
        }
        for (UUID id : finished) {
            PENDING.remove(id);
        }
    }

    /**
     * 回收死亡点附近、**本次死亡新生成**的灵魂水晶载体。
     *
     * <p>判定四连(缺一不动):实体类 = 第三方「永久掉落物」· 主人 = 本次死亡的玩家 · 持有的物品 =
     * 灵魂水晶 · 实体 {@code age} 未超 {@link #FRESH_SOUL_CRYSTAL_MAX_AGE}(防误伤历史遗留的合法水晶)。
     *
     * <p>🚨 载体 {@code extends Entity}(不是 {@code ItemEntity})⇒ **必须按第三方实体类检索**;
     * 且 {@code discard()} 会走到它的 {@code remove(DISCARDED)} 覆写 ⇒ 自动从第三方
     * {@code SoulArchive} 注销(否则灵魂罗盘会指向一个不存在的水晶)。
     *
     * <p>只清**灵魂水晶**这一种物品;第三方在「飞升护符 + 存储水晶」分支里掉的是**存储水晶**
     * (物品不同),不会被误删 —— 那是它的「保住掉落物」功能,不该由本类干预。
     */
    private static void discardDroppedCrystals(UUID playerId, Pending pending) {
        ServerLevel level = pending.level();
        if (level == null) return;
        Class<?> holder = holderClass();
        if (holder == null) return;
        Item crystal = soulCrystalItem();
        if (crystal == Items.AIR) return;
        AABB box = new AABB(
                pending.x() - SOUL_CLEANUP_RADIUS, pending.y() - SOUL_CLEANUP_RADIUS, pending.z() - SOUL_CLEANUP_RADIUS,
                pending.x() + SOUL_CLEANUP_RADIUS, pending.y() + SOUL_CLEANUP_RADIUS, pending.z() + SOUL_CLEANUP_RADIUS);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
            if (!holder.isInstance(entity)) continue;
            if (!playerId.equals(holderOwnerId(entity))) continue;
            if (holderGetAge != null && holderAge(entity) > FRESH_SOUL_CRYSTAL_MAX_AGE) continue;
            ItemStack held = holderItem(entity);
            if (held.isEmpty() || !held.is(crystal)) continue;
            entity.discard();
            LOGGER.debug("[Astral Dice][Moses] 第六诅咒已修正:回收 {} 死亡点新生成的灵魂水晶", playerId);
        }
    }

    // ── 第三方反射桥(不可用时静默停用) ───────────────────────────────────────

    private static Item soulCrystalItem() {
        return BuiltInRegistries.ITEM.get(new ResourceLocation(SOUL_CRYSTAL_ID));
    }

    /**
     * 解析第三方「永久掉落物」实体类与其读值方法({@code getItem} / {@code getOwnerId} / {@code getAge})。
     *
     * <p>⚠️ 与 {@link #initSoulApi()} **相互独立**:这里失败只停用「水晶回收」,计数还原照常工作。
     * ⚠️ 全程反射(该类是第三方内部类),目标逐条取自实物 jar 的 {@code javap} 输出:
     * 1.21.1/26.1.2 移植版与 1.20.1 原版**包名不同**,故类名是分线常量。
     */
    private static synchronized Class<?> holderClass() {
        if (holderLookupDisabled) return null;
        if (holderClass != null) return holderClass;
        try {
            Class<?> type = Class.forName(PERMANENT_ITEM_ENTITY_CLASS);
            holderGetItem = type.getMethod("getItem");
            holderGetOwnerId = type.getMethod("getOwnerId");
            try {
                holderGetAge = type.getMethod("getAge");
            } catch (NoSuchMethodException ignored) {
                // 第三方没有 age 读值 ⇒ 退化为三重判定(类 + 主人 + 物品)
                holderGetAge = null;
            }
            holderClass = type;
            return type;
        } catch (Throwable t) {
            holderLookupDisabled = true;
            LOGGER.warn("[Astral Dice][Moses] 未找到神秘遗物(+)的永久掉落实体类,灵魂水晶回收停用: {}",
                    t.toString());
            return null;
        }
    }

    /** 读载体持有的物品;失败返回空栈(调用方按「不是灵魂水晶」处理)。 */
    private static ItemStack holderItem(Entity holder) {
        try {
            return (ItemStack) holderGetItem.invoke(holder);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 读载体的主人 UUID;失败返回 null(调用方按「不是本次死亡的玩家」处理)。 */
    private static UUID holderOwnerId(Entity holder) {
        try {
            return (UUID) holderGetOwnerId.invoke(holder);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int holderAge(Entity holder) {
        try {
            return (int) holderGetAge.invoke(holder);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static synchronized boolean initSoulApi() {
        if (soulApiDisabled) return false;
        if (getLostMethod != null) return true;
        try {
            Class<?> type = Class.forName(SOUL_CRYSTAL_CLASS);
            Method probe = type.getMethod("getLostCrystals", Player.class);
            if (!Modifier.isStatic(probe.getModifiers())) {
                // 本线的 Enigmatic Legacy 是实例方法 ⇒ 目标取该物品的单例实例
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
            LOGGER.warn("[Astral Dice][Moses] 未找到神秘遗物的灵魂水晶 API,第六诅咒修正停用: {}", t.toString());
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
