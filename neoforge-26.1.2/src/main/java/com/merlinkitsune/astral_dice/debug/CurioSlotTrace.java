package com.merlinkitsune.astral_dice.debug;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosSlotTypes;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioCanEquipEvent;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * 「重登后筹码被移出筹码栏」的**只读诊断插桩**（2026-09-17）。
 *
 * <h2>为什么需要它</h2>
 * 现象：26.1.2（Curios 15.0.0）上，槽内的**合法**筹码在重登后被搬进玩家背包（物品不丢、槽位数正常）。
 * 源码侧的可疑链路是 Curios 的登录迁移 {@code CurioInventory#loadInventoryConfiguration()}：
 * 它按数据包重建默认栏位后，用一个
 * {@code while (index < 新栏位.getSlots() && index < 旧栏位.getSlots())} 逐槽搬移物品，
 * 而 {@code getSlots()} 在 {@code setDataLoaded()} 之前**不会**触发 {@code update()→resize()}
 * （见 {@code CurioStacksHandler#update()} 首行的 {@code if (this.dataLoaded)}），
 * 于是它读到的是**数据包原始尺寸**。本模组的筹码栏数据包尺寸是 {@code 0}
 * （尺寸完全由骰子经槽位修饰符给出），因此该循环在 0 处即终止，随后
 * 的 {@code while (index < 旧栏位.getSlots())} 把槽内物品全部塞进 {@code invalidStacks}
 * → 由 {@code CurioInventoryCapability#handleInvalidStacks()}（每 tick 调）交还玩家背包。
 *
 * <h2>本插桩做什么</h2>
 * 只在**登录/数据包同步**这一个窗口把筹码栏的状态原样打出来，用于判定「搬运循环没跑」
 * 还是「校验器拒绝了物品」：
 * <ul>
 *   <li>{@code SYNC_BEFORE}（{@link EventPriority#HIGHEST}，先于 Curios 的默认优先级处理器）
 *       —— Curios 迁移**之前**的快照；</li>
 *   <li>{@code SYNC_AFTER}（{@link EventPriority#LOWEST}，后于 Curios）
 *       —— 迁移**之后**的快照（槽位数/修饰符/槽内物品/背包里有没有筹码）；</li>
 *   <li>{@code LOGIN} 与 {@code LOGIN_TICK1..3} —— 登录完成及随后 3 tick
 *       （Curios 的 {@code handleInvalidStacks()} 在实体 tick 末才把物品交还玩家，
 *       故必须多看几 tick 才能看到「筹码出现在背包里」）；</li>
 *   <li>{@code CAN_EQUIP} —— 每次 {@code DynamicStackHandler#isItemValid} 的真实调用点。
 *       <b>这是判定性证据</b>：若登录迁移期间**没有**出现 {@code slot=chip} 的 CAN_EQUIP 行，
 *       就说明搬运循环根本没执行（= 尺寸读成 0 的分支），而不是校验器/标签把物品挡下。</li>
 * </ul>
 *
 * <h2>安全约束（必须保持）</h2>
 * <ul>
 *   <li><b>只读</b>：不写槽位、不加/删修饰符、不改玩家数据，也不取消/改写任何事件结果；
 *       {@code CurioCanEquipEvent} 处理器只读取结果字段。</li>
 *   <li><b>不抛异常</b>：每段都 try/catch(Throwable)，异常只写日志 —— 插桩绝不能改变流程，
 *       否则会把「诊断」变成新的缺陷来源。</li>
 *   <li><b>可关</b>：{@code -Dastral_dice.curioTrace=false} 可强制关闭；默认
 *       「开发环境（{@code !FMLEnvironment.production}）打开、生产环境关闭」，生产排障可加
 *       {@code -Dastral_dice.curioTrace=true} 打开。生产默认关闭 ⇒ 玩家侧零日志噪声。</li>
 *   <li>调用 {@code getSlots()}/{@code getStacks()} 会让 Curios 顺带跑一次它自己的
 *       {@code update()}（这是 Curios 公开 API 的正常语义，任何 mod 读尺寸都会触发），
 *       不额外修改状态。</li>
 * </ul>
 *
 * <p>日志前缀统一为 {@code AP_CURIOTRACE|}，便于 {@code scripts/test} 的断言按行抓取。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class CurioSlotTrace {

    private static final Logger LOGGER = LoggerFactory.getLogger("AstralDice/CurioTrace");
    private static final String P = "AP_CURIOTRACE|";
    /** 筹码栏的槽位 id（与 data/astral_dice/curios/slots/chip.json 同名）。 */
    private static final String CHIP = "chip";
    /** Curios 的 tag 校验器查的就是 {@code #curios:<槽位 id>}。 */
    private static final TagKey<Item> CHIP_TAG =
            ItemTags.create(Identifier.fromNamespaceAndPath("curios", CHIP));
    /** 登录后继续观察的 tick 数（Curios 在实体 tick 末交还未通过校验的物品）。 */
    private static final int WATCH_TICKS = 40;
    /** 在观察窗内的这些 tick 上打点（够看清「迁移后 4 → 若干 tick 后 2」的回落过程）。 */
    private static final java.util.Set<Integer> WATCH_LOG_TICKS = java.util.Set.of(1, 2, 3, 10, 20, 40);

    private static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty("astral_dice.curioTrace",
                    Boolean.toString(devEnvironment())));

    /**
     * 是否开发环境（决定插桩默认开关）。
     *
     * <p>用 {@code FMLLoader.isProduction()}：开发运行（MDG {@code runClient}）为 false，玩家/整合包为
     * true。⚠️ 判定本身**不得**抛异常：任何失败都按「生产」处理（= 不插桩），
     * 这样最坏情况只是少日志，绝不会因为诊断代码影响玩家。
     */
    private static boolean devEnvironment() {
        try {
            FMLLoader loader = FMLLoader.getCurrent();
            return loader != null && !loader.isProduction();
        } catch (Throwable t) {
            return false;
        }
    }

    /** player -> 剩余观察 tick 数。 */
    private static final Map<UUID, Integer> WATCH = new ConcurrentHashMap<>();

    /**
     * 「Curios 登录迁移进行中」的玩家集合：{@code SYNC_BEFORE} 加入、{@code SYNC_AFTER} 移除。
     *
     * <p>{@code CAN_EQUIP} 只在窗口内打日志 —— 槽位校验在正常游玩（打开 Curios 界面、
     * 每 tick 的快速移动检查）里会被高频调用，无条件记录会把日志冲爆；而诊断只需要
     * 「迁移期间有没有校验调用」这一条信息。
     */
    private static final java.util.Set<UUID> SYNC_WINDOW = ConcurrentHashMap.newKeySet();

    private CurioSlotTrace() {
    }

    // ══ 数据包同步：Curios 登录迁移的唯一入口事件 ══════════════════════════════

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDatapackSyncBefore(OnDatapackSyncEvent event) {
        if (!ENABLED) {
            return;
        }
        forEachSyncedPlayer(event, player -> {
            SYNC_WINDOW.add(player.getUUID());
            logChipState("SYNC_BEFORE", player);
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackSyncAfter(OnDatapackSyncEvent event) {
        if (!ENABLED) {
            return;
        }
        forEachSyncedPlayer(event, player -> {
            logChipState("SYNC_AFTER", player);
            SYNC_WINDOW.remove(player.getUUID());
        });
    }

    // ══ 登录与随后若干 tick ══════════════════════════════════════════════════

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ENABLED) {
            return;
        }
        try {
            if (event.getEntity() instanceof ServerPlayer player) {
                logChipState("LOGIN", player);
                WATCH.put(player.getUUID(), WATCH_TICKS);
            }
        } catch (Throwable t) {
            LOGGER.warn("{}LOGIN|ERR|{}", P, t);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            Integer left = WATCH.get(player.getUUID());
            if (left == null) {
                return;
            }
            if (left <= 0) {
                WATCH.remove(player.getUUID());
                return;
            }
            int elapsed = WATCH_TICKS - left + 1;
            WATCH.put(player.getUUID(), left - 1);
            if (WATCH_LOG_TICKS.contains(elapsed)) {
                logChipState("LOGIN_TICK" + elapsed, player);
            }
        } catch (Throwable t) {
            LOGGER.warn("{}TICK|ERR|{}", P, t);
        }
    }

    // ══ 槽位校验的真实调用点（判定性证据）════════════════════════════════════

    @SubscribeEvent
    public static void onCurioCanEquip(CurioCanEquipEvent event) {
        if (!ENABLED) {
            return;
        }
        try {
            SlotContext ctx = event.getSlotContext();
            LivingEntity owner = ctx.entity();
            // 只在「登录迁移窗口」内记录：正常游玩里槽位校验会被高频调用（见 SYNC_WINDOW 注释）
            if (owner == null || !SYNC_WINDOW.contains(owner.getUUID())) {
                return;
            }
            LOGGER.info("{}CAN_EQUIP|player={}|slot={}|index={}|cosmetic={}|stack={}|original={}|verdict={}",
                    P, nameOf(owner), ctx.identifier(), ctx.index(), ctx.cosmetic(),
                    idOf(event.getStack()), event.getOriginalEquipResult(), event.getEquipResult());
        } catch (Throwable t) {
            LOGGER.warn("{}CAN_EQUIP|ERR|{}", P, t);
        }
    }

    // ══ 给产品代码用的打点入口（本模组自己改尺寸时必须留痕）═════════════════════

    /**
     * 记录一次「本模组改写筹码栏尺寸」的动作及其结果（只读快照）。
     *
     * <p>由 {@code DiceCurioItem#applySlotCount} 调用：它每 20 tick 会按骰子星级重写修饰符，
     * 是「登录后槽位数从迁移瞬时值回落到目标值」的最可能来源，必须在证据里与 Curios 侧的动作
     * 区分开。关闭插桩时**必须是纯空操作**（不构造任何对象），以免影响产品性能与行为。
     */
    public static void noteSlotCount(String where, ICurioStacksHandler handler, int target) {
        if (!ENABLED || handler == null) {
            return;
        }
        try {
            LOGGER.info("{}NOTE|{}|target={}|chipSlots={}|chipBase={}|chipMods={}",
                    P, where, target, handler.getSlots(), handler.getBaseSize(),
                    describeModifiers(handler.getModifiers().values()));
        } catch (Throwable t) {
            LOGGER.warn("{}NOTE|{}|ERR|{}", P, where, t);
        }
    }

    // ══ 实现 ════════════════════════════════════════════════════════════════

    /** 对事件涉及的每个玩家执行动作（单人登录 = getPlayer()；批量同步 = getPlayerList()）。 */
    private static void forEachSyncedPlayer(OnDatapackSyncEvent event,
                                            java.util.function.Consumer<ServerPlayer> action) {
        try {
            if (event.getPlayer() != null) {
                action.accept(event.getPlayer());
                return;
            }
            if (event.getPlayerList() != null) {
                for (ServerPlayer player : event.getPlayerList().getPlayers()) {
                    action.accept(player);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("{}|SYNC|ERR|{}", P, t);
        }
    }

    /**
     * 打印筹码栏的完整可判定状态（只读）。
     */
    private static void logChipState(String phase, ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            var inventory = CuriosApi.getCuriosInventory(player).orElse(null);
            if (inventory == null) {
                LOGGER.info("{}|{}|player={}|no-curios-inventory", P, phase, nameOf(player));
                return;
            }
            ICurioStacksHandler handler = inventory.getStacksHandler(CHIP).orElse(null);
            if (handler == null) {
                LOGGER.info("{}|{}|player={}|no-chip-handler|slotIds={}", P, phase, nameOf(player),
                        String.join(",", inventory.getCurios().keySet()));
                return;
            }

            IDynamicStackHandler stacks = handler.getStacks();
            IDynamicStackHandler cosmetics = handler.getCosmeticStacks();

            LOGGER.info("{}|{}|player={}|chipSlots={}|chipBase={}|chipMods={}|chipPerms={}|chipStacks={}|chipCosmetic={}|chipRenders={}",
                    P, phase, nameOf(player), handler.getSlots(), handler.getBaseSize(),
                    describeModifiers(handler.getModifiers().values()),
                    describeModifiers(handler.getPermanentModifiers()),
                    describeStacks(stacks), describeStacks(cosmetics),
                    handler.getRenders().size());

            // 逐槽：当前物品 + Curios 的槽位校验结论 + #curios:chip 标签结论。
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                SlotContext ctx = new SlotContext(CHIP, player, i, false, true);
                boolean valid;
                String validExtra = "";
                try {
                    valid = stacks.isItemValid(i, stack);
                } catch (Throwable t) {
                    valid = false;
                    validExtra = "|validErr=" + t.getClass().getSimpleName();
                }
                String slotTypes = "";
                try {
                    slotTypes = String.join(",", CuriosSlotTypes.getItemSlotTypes(stack, player).keySet());
                } catch (Throwable t) {
                    slotTypes = "ERR:" + t.getClass().getSimpleName();
                }
                LOGGER.info("{}|{}|player={}|chipSlot{}|stack={}|isItemValid={}|stackIsStackValid={}|itemSlotTypes=[{}]|inChipTag={}{}",
                        P, phase, nameOf(player), i, idOf(stack), valid,
                        CuriosApi.isStackValid(ctx, stack), slotTypes, stack.is(CHIP_TAG), validExtra);
            }

            List<String> inventoryChips = new ArrayList<>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(CHIP_TAG)) {
                    inventoryChips.add(i + ":" + idOf(stack) + "x" + stack.getCount());
                }
            }
            List<String> groundChips = new ArrayList<>();
            try {
                for (net.minecraft.world.entity.item.ItemEntity drop :
                        player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                player.getBoundingBox().inflate(16.0D))) {
                    ItemStack stack = drop.getItem();
                    if (!stack.isEmpty() && stack.is(CHIP_TAG)) {
                        groundChips.add(idOf(stack) + "x" + stack.getCount() + "@" + drop.blockPosition().toShortString());
                    }
                }
            } catch (Throwable t) {
                groundChips.add("ERR:" + t.getClass().getSimpleName());
            }
            ItemStack offhand = player.getOffhandItem();
            LOGGER.info("{}|{}|player={}|invChips=[{}]|offhand={}|groundChips=[{}]", P, phase,
                    nameOf(player), String.join(",", inventoryChips), idOf(offhand),
                    String.join(",", groundChips));
        } catch (Throwable t) {
            LOGGER.warn("{}|{}|player={}|ERR|{}", P, phase, nameOf(player), t);
        }
    }

    private static String describeModifiers(Iterable<AttributeModifier> modifiers) {
        List<String> out = new ArrayList<>();
        for (AttributeModifier modifier : modifiers) {
            if (modifier == null) {
                continue;
            }
            out.add(modifier.id() + "=" + modifier.amount() + ":" + modifier.operation().name());
        }
        out.sort(String::compareTo);
        return "[" + String.join(",", out) + "]";
    }

    private static String describeStacks(IDynamicStackHandler handler) {
        List<String> out = new ArrayList<>();
        try {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                out.add(i + ":" + (stack.isEmpty() ? "empty" : idOf(stack) + "x" + stack.getCount()));
            }
        } catch (Throwable t) {
            return "[ERR:" + t.getClass().getSimpleName() + "]";
        }
        return "[" + String.join(",", out) + "]";
    }

    private static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "unknown" : id.toString();
    }

    private static String nameOf(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getGameProfile().name();
        }
        return entity == null ? "null" : entity.getName().getString();
    }
}
