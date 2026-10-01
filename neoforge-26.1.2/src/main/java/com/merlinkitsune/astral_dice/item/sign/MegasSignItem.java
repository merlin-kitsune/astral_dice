package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.combat.OrbitalBombardmentManager;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.starenginelib.target.TargetSelectionAction;
import com.merlinkitsune.astral_dice.target.TargetSelectionManager;
import com.merlinkitsune.starenginelib.target.TargetSelectionRegistry;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 机械师立牌(命名:megas,传奇)。
 *
 * <p><b>被动「物资补充」</b>：手牌 &lt; {@value #RESUPPLY_MAX_CARDS} 张时，每 1:00 自动获得 1 张随机卡牌。
 *
 * <p><b>主动「轨道轰炸」</b>：使用目标选择器指定 1 个敌对目标，随后按
 * <b>快捷栏 → 副手 → 背包</b>的顺序取用卡牌（取至基础总伤害达单轮上限即停，<b>多余卡牌保留</b>），
 * 对指定目标及其周围 {@code OrbitalBombardmentManager#RADIUS} 格范围进行轨道轰炸：
 * 每取用 2 张卡牌 +1 次轰炸（上限 {@code OrbitalBombardmentManager#MAX_STRIKES} 次）；
 * 单次轰炸命中范围内随机 1 个怪物并造成 2 点基础伤害；每取用 1 张战斗牌，该次轰炸额外 +
 * 该牌费用 × {@code OrbitalBombardmentManager#COST_DAMAGE_MULTIPLIER}。轨道轰炸为技能伤害、无视防御（真伤）。
 *
 * <p>2026-09-28 用户平衡性调整：取牌由「无脑消耗全部手牌」改为「按顺序取至伤害上限即停」，
 * 避免为了打满伤害而无谓浪费卡牌（详见 {@link #performOrbitalBombardment}）。
 *
 * <p><b>精准打击</b>：消耗 ≥ {@value OrbitalBombardmentManager#PRECISION_THRESHOLD} 张卡牌时触发，
 * 每次轰炸命中给目标 +1 层「精准打击」（每层使该目标受到的轨道轰炸伤害 +1，永久持续到死亡）。
 *
 * <p>主动为"目标选择器"类技能：触发后经 {@link TargetSelectionManager} 进入选择模式，
 * 确认时由 {@link TargetSelectionAction#apply} 执行轰炸并开始玩家级冷却；取消/超时不冷却。
 */
// 本类不注册任何 @SubscribeEvent，故**不得**标注 @EventBusSubscriber
// （NeoForge 21.1 对「无 @SubscribeEvent 方法的订阅者类」直接抛异常，见 MosesSignItem 同类注释）。
public class MegasSignItem extends BaseSignItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(MegasSignItem.class);

    /** 主动冷却基础秒数。 */
    public static final int ACTIVE_COOLDOWN_SECONDS = 120;

    /** 被动「物资补充」的发放间隔 tick = 1:00。 */
    public static final int RESUPPLY_INTERVAL_TICKS = 1200;

    /** 被动「物资补充」的触发阈值：手牌数**小于**该值时才会补充。 */
    public static final int RESUPPLY_MAX_CARDS = 6;

    /**
     * 主动「轨道轰炸」的目标选择器半径（格）。
     *
     * <p>2026-09-27 用户平衡性调整：**16 → 32**（前置库契约上限即 32，
     * {@code TargetSelectionManager} 会按 {@code MAX_SELECT_RADIUS} 夹取）。
     * 客户端半径由服务端随 StartPayload 下发 ⇒ 覆写本值即三侧一致。
     */
    public static final double SELECTOR_RADIUS = 32.0D;

    /** 主动「轨道轰炸」的激活门槛：物品栏手牌数**至少**该值才能发起。 */
    public static final int MIN_CARDS_TO_ACTIVATE = 2;

    static {
        TargetSelectionRegistry.register(new TargetSelectionAction() {
            @Override
            public String id() {
                return "megas_orbital_bombardment";
            }

            @Override
            public TargetType targetType() {
                return TargetType.ENEMY;
            }

            @Override
            public double radius() {
                return SELECTOR_RADIUS;
            }

            @Override
            public void onStarted(ServerPlayer player) {
                sendReadyPrompt(player);
            }

            @Override
            public void apply(ServerPlayer player, LivingEntity target) {
                // 执行轨道轰炸：收集手牌 → 算次数/伤害 → 调度 → 消耗手牌
                if (!performOrbitalBombardment(player, target)) return;
                // 主动成功触发：开始玩家级冷却并计入「电流核心」充能
                ModAttachments.setSignActiveCooldownEnd(player,
                        player.level().getGameTime() + signCooldownTicks(player));
                CurrentCoreChipItem.onActiveSkillUsed(player);
                // 玩家可见反馈(与其他立牌的专属提示同款;行为口径见
                // TargetSelectionManager#confirm 的注释:actionbar 单槽位 ⇒ 这里发的会覆盖
                // 通用「已确认」提示,正是设计意图 —— 玩家只看得到专属提示)
                sendSignActionBar(player, "msg.astral_dice.megas_cast");
                LOGGER.debug("[Astral Dice][TargetSelection] megas_orbital_bombardment by {} -> {}({})",
                        player.getName().getString(), target.getId(), target.getName().getString());
            }
        });
    }

    public MegasSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        tickResupply(player);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 卸下立牌：被动计时基准归零
        ModAttachments.setMegasResupplyNextTick(player, 0L);
    }

    /**
     * 前置门控（{@code BaseSignItem#performSkill} 第 2.4 步）：手牌不足 {@value #MIN_CARDS_TO_ACTIVATE} 张时**直接拒绝**。
     *
     * <p>2026-09-27 用户裁决：原实现把「手牌不足」校验放在 {@code TargetSelectionAction#apply} 里 ⇒
     * 玩家**先进入目标选择界面、确认目标之后**才被告知不足 ⇒ 程序顺序颠倒。现提前到门控：
     * 不足即发**红色**提示并返回 false（不开选择会话、不发牌、不写冷却/锁定、不充能）。
     *
     * @return true = 允许开启选择会话
     */
    @Override
    protected boolean canBeginSelectorSession(Player player) {
        if (countHandCards(player) >= MIN_CARDS_TO_ACTIVATE) return true;
        sendSignActionBarColored(player, net.minecraft.ChatFormatting.RED,
                "msg.astral_dice.megas_not_enough_cards");
        return false;
    }

    /** 发送「请选择敌对目标」提示。 */
    public static void sendReadyPrompt(Player player) {
        sendSignActionBar(player, "msg.astral_dice.megas_ready");
    }

    @Override
    protected String selectorActionId() {
        return "megas_orbital_bombardment";
    }

    /** 玩家是否佩戴机械师立牌。 */
    public static boolean isEquipped(Player player) {
        // 总闸门(骰子装备和卸除调整):未佩戴骰子 ⇒ 本件功能一律不生效(数值保留)
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.MEGAS_SIGN.get())).isPresent();
    }

    /**
     * 立牌主动冷却 tick：佩戴时基础 120 秒（与枪匠/怪力侦探同款链：先按充能上限封顶，再按诡异骰子减半）。
     */
    public static int signCooldownTicks(Player player) {
        int ticks = isEquipped(player)
                ? ACTIVE_COOLDOWN_SECONDS * 20
                : GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
        ticks = (int) ChargeManager.signCooldownTicks(player, ticks);
        if (WeirdDiceHandler.hasWeirdDice(player)) {
            ticks = Math.max(1, ticks / 2);
        }
        return ticks;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「物资补充」
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 被动「物资补充」：手牌 &lt; {@value #RESUPPLY_MAX_CARDS} 张时，每 1:00 自动获得 1 张随机卡牌。
     * 仅服务端、每 tick 判定（计时基准存附件 {@code ModAttachments#MEGAS_RESUPPLY_NEXT_TICK}）。
     */
    private static void tickResupply(Player player) {
        long now = player.level().getGameTime();
        long next = ModAttachments.getMegasResupplyNextTick(player);
        if (next == 0L) {
            // 首次：登记下一发放刻
            ModAttachments.setMegasResupplyNextTick(player, now + RESUPPLY_INTERVAL_TICKS);
            return;
        }
        if (now < next) return;
        if (countHandCards(player) >= RESUPPLY_MAX_CARDS) {
            // 手牌已达阈值：只推进计时基准，不发放
            ModAttachments.setMegasResupplyNextTick(player, now + RESUPPLY_INTERVAL_TICKS);
            return;
        }
        // 发放 1 张随机卡牌（走统一发牌漏斗，触发维生素药丸等钩子）
        RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.ALL);
        ModAttachments.setMegasResupplyNextTick(player, now + RESUPPLY_INTERVAL_TICKS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动「轨道轰炸」
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 执行轨道轰炸：**按顺序取用卡牌至伤害上限即停**，算次数与伤害，调度轰炸序列。
     *
     * <p><b>取牌口径</b>（2026-09-28 用户平衡性调整）：不再无脑消耗全部手牌，而是按
     * <b>快捷栏(0-8) → 副手 → 主背包(9-35)</b> 的顺序逐张累加，一旦「估算基础总伤害」达到单轮上限
     * {@link OrbitalBombardmentManager#MAX_TOTAL_DAMAGE_PER_CAST}（800）立即停止取牌，
     * <b>多余卡牌原样留在物品栏</b>；若把全部卡牌取完仍达不到上限（例如清一色 0 费牌），
     * 则按原口径把卡牌全部用掉。
     *
     * <p>「估算」只算<b>基础部分</b>（次数 = min(张数 ÷ 2, 10)，单次 = min(基础 2 + 费用总和, 80)），
     * 不含「精准打击」加伤 —— 该加伤独立于单轮上限（见 {@code OrbitalBombardmentManager#impact}），
     * 若算进来会「提前停手」。
     *
     * @return 是否成功触发（取到的卡牌 &lt; {@value #MIN_CARDS_TO_ACTIVATE} 张 ⇒ 拒绝并提示，返回 false，不消耗、不冷却）
     */
    public static boolean performOrbitalBombardment(ServerPlayer player, LivingEntity primaryTarget) {
        if (player == null || primaryTarget == null || player.level().isClientSide()) return false;
        if (primaryTarget.isRemoved() || !primaryTarget.isAlive()) return false;

        List<Integer> order = orderedCardSlots();

        // 按顺序逐张累加，直到估算总伤害触顶（未触顶 = 手上卡牌不足以打满上限 ⇒ 全部取用）
        int taken = 0;
        int costSum = 0;
        boolean capped = false;
        for (int slot : order) {
            ItemStack stack = slotStack(player, slot);
            if (stack.isEmpty() || !ModItems.isCardItem(stack)) continue;
            // 同一栈内各张卡牌等价（同类型、同费用）⇒ 按张数累加即可
            int perCardCost = cardCost(stack, player);
            for (int i = 0; i < stack.getCount(); i++) {
                taken++;
                costSum += perCardCost;
                if (estimateDamage(taken, costSum) >= OrbitalBombardmentManager.MAX_TOTAL_DAMAGE_PER_CAST) {
                    capped = true;
                    break;
                }
            }
            if (capped) break;
        }

        // 至少 2 张卡牌才能激活
        if (taken < MIN_CARDS_TO_ACTIVATE) {
            // 兜底(正常已被前置门控 canBeginSelectorSession 拦下):同样走**红色**阻止色。
            sendSignActionBarColored(player, net.minecraft.ChatFormatting.RED,
                    "msg.astral_dice.megas_not_enough_cards");
            return false;
        }

        // 轰炸次数 = min(取用卡牌数 ÷ 2, 10)；单次伤害 = 基础 2 + 取用卡牌费用总和 × COST_DAMAGE_MULTIPLIER(=1)
        // ⚠️ 单次伤害另受 MAX_SINGLE_STRIKE_DAMAGE(80) 封顶、单轮累计受 MAX_TOTAL_DAMAGE_PER_CAST(800) 封顶，
        //    夹取在唯一执行器 OrbitalBombardmentManager#impact 内完成（此处只传未夹取的 perStrikeDamage）。
        // ⚠️ 「精准打击」的触发判据也改为**实际取用张数**（不再是物品栏手牌总数）—— 否则「保留多余卡牌」
        //    与「消耗 ≥6 张才触发」两条口径会互相打架（明明只取用 2 张却被算作耗尽 20 张）。
        int strikes = Math.min(taken / 2, OrbitalBombardmentManager.MAX_STRIKES);
        float perStrikeDamage = OrbitalBombardmentManager.BASE_DAMAGE
                + (float) costSum * OrbitalBombardmentManager.COST_DAMAGE_MULTIPLIER;
        boolean precision = taken >= OrbitalBombardmentManager.PRECISION_THRESHOLD;

        // 只消耗取到的那几张（同栈多张时只扣走所需张数，其余留在原位）
        consumeCards(player, order, taken);

        OrbitalBombardmentManager.schedule(player, primaryTarget, strikes, perStrikeDamage, precision);
        return true;
    }

    /**
     * 统计玩家物品栏内手牌数（主物品栏 0-35 + 副手，判据 {@link ModItems#isCardItem}）。
     */
    public static int countHandCards(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && ModItems.isCardItem(stack)) {
                count += stack.getCount();
            }
        }
        // 26.1.2 平台差异:副手已并入 EntityEquipment,Inventory#offhand 字段已删 ⇒
        // 改走 Container 索引(Inventory#getItem(int) 在 i >= items.size() 时回落到 EQUIPMENT_SLOT_MAPPING)
        ItemStack offhand = player.getInventory().getItem(Inventory.SLOT_OFFHAND);
        if (!offhand.isEmpty() && ModItems.isCardItem(offhand)) {
            count += offhand.getCount();
        }
        return count;
    }


    /** 快捷栏槽位数（索引 0-8）。 */
    private static final int HOTBAR_SIZE = 9;

    /** 主背包槽位数（快捷栏 9 + 背包 27 = 36）。 */
    private static final int INVENTORY_SIZE = 36;

    /**
     * 「副手」在取牌顺序里的**哨兵索引**（不是真实 Container 索引）。
     *
     * <p>副手的容器位置三线并不一致，故不硬编码数字，改为走 {@link #offhandStack}
     * 这一层平台访问器（各线各自实现）。
     */
    private static final int OFFHAND_SLOT = -1;

    /**
     * 取牌 / 扣牌的**统一槽位顺序**：快捷栏(0-8) → 副手 → 主背包(9-35)。
     *
     * <p>2026-09-28 用户裁决：「先抽取玩家手上和口袋的卡牌，然后按背包内顺序抽取」——
     * 即快捷栏优先、副手（口袋）次之、主背包按索引序收尾。
     *
     * <p>用**槽位索引**而非 {@link ItemStack} 引用表达顺序：卡牌可堆叠（{@code stacksTo(64)}）
     * ⇒ 需要「只扣走其中几张」，必须能按索引回写容器，不能只留 stack 引用。
     */
    private static List<Integer> orderedCardSlots() {
        List<Integer> order = new ArrayList<>(INVENTORY_SIZE + 1);
        for (int i = 0; i < HOTBAR_SIZE; i++) order.add(i);
        order.add(OFFHAND_SLOT);
        for (int i = HOTBAR_SIZE; i < INVENTORY_SIZE; i++) order.add(i);
        return order;
    }

    /** 读槽位内容：主物品栏 0-35 走容器索引，{@link #OFFHAND_SLOT} 走副手平台访问器。 */
    private static ItemStack slotStack(Player player, int slot) {
        return slot == OFFHAND_SLOT
                ? offhandStack(player)
                : player.getInventory().getItem(slot);
    }

    /** 清空槽位（仅用于整栈扣空）。 */
    private static void slotClear(Player player, int slot) {
        if (slot == OFFHAND_SLOT) {
            setOffhandStack(player, ItemStack.EMPTY);
        } else {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }

    /**
     * 副手内容。
     *
     * <p>26.1.2：副手已并入 EntityEquipment、{@code Inventory#offhand} 字段已删 ⇒
     * 走容器索引 {@link Inventory#SLOT_OFFHAND}（与 {@code countHandCards} 同口径）。
     */
    private static ItemStack offhandStack(Player player) {
        return player.getInventory().getItem(Inventory.SLOT_OFFHAND);
    }

    private static void setOffhandStack(Player player, ItemStack stack) {
        player.getInventory().setItem(Inventory.SLOT_OFFHAND, stack);
    }

    /** 单张卡牌的费用（解析不出卡牌类型 ⇒ 0）。 */
    private static int cardCost(ItemStack stack, Player player) {
        String typeId = CardRegistry.itemToType(stack);
        return typeId != null ? CardRegistry.cost(typeId, player) : 0;
    }

    /**
     * 估算「取用 {@code cards} 张卡牌（费用总和 {@code costSum}）」时的**基础伤害总量**：
     * 轰炸次数 = min(cards ÷ 2, 10)，单次伤害 = min(基础 2 + costSum, 80)，两者相乘。
     *
     * <p>⚠️ **不含**「精准打击」加伤 —— 该加伤独立于单轮上限（见
     * {@code OrbitalBombardmentManager#impact}），若算进来会「提前停手」。
     *
     * <p>本函数与执行器 {@code OrbitalBombardmentManager#impact} 的两级封顶同源，但**不替代**它：
     * 取牌只是按估算停手，实际伤害仍由执行器逐次夹取。
     */
    private static float estimateDamage(int cards, int costSum) {
        int strikes = Math.min(cards / 2, OrbitalBombardmentManager.MAX_STRIKES);
        float perStrike = OrbitalBombardmentManager.BASE_DAMAGE
                + (float) costSum * OrbitalBombardmentManager.COST_DAMAGE_MULTIPLIER;
        perStrike = Math.min(perStrike, OrbitalBombardmentManager.MAX_SINGLE_STRIKE_DAMAGE);
        return (float) strikes * perStrike;
    }

    /**
     * 按 {@link #orderedCardSlots} 的顺序扣减 {@code count} 张卡牌。
     *
     * <p>同栈卡牌多于所需时**只扣走所需张数**（{@code ItemStack#shrink} 直改容器内那份对象，
     * 与 {@code StarCoinHammerChipItem#consumeLooseStarCoins} 同款写法），扣空的槽位才置空。
     */
    private static void consumeCards(Player player, List<Integer> order, int count) {
        int remaining = count;
        for (int slot : order) {
            if (remaining <= 0) return;
            ItemStack stack = slotStack(player, slot);
            if (stack.isEmpty() || !ModItems.isCardItem(stack)) continue;
            int take = Math.min(stack.getCount(), remaining);
            remaining -= take;
            if (take >= stack.getCount()) {
                slotClear(player, slot);
            } else {
                stack.shrink(take);
            }
        }
    }
}
