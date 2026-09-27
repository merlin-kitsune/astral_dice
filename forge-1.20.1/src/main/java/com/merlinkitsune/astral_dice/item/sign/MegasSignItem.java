package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.combat.OrbitalBombardmentManager;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.starenginelib.target.TargetSelectionAction;
import com.merlinkitsune.astral_dice.target.TargetSelectionManager;
import com.merlinkitsune.starenginelib.target.TargetSelectionRegistry;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 机械师立牌(命名:megas,传奇)。
 *
 * <p><b>被动「物资补充」</b>：手牌 &lt; {@value #RESUPPLY_MAX_CARDS} 张时，每 1:00 自动获得 1 张随机卡牌。
 *
 * <p><b>主动「轨道轰炸」</b>：使用目标选择器指定 1 个敌对目标，立即消耗玩家物品栏内所有手牌，
 * 对指定目标及其周围 {@code OrbitalBombardmentManager#RADIUS} 格范围进行轨道轰炸：
 * 每消耗 2 张卡牌 +1 次轰炸（上限 {@code OrbitalBombardmentManager#MAX_STRIKES} 次）；
 * 单次轰炸命中范围内随机 1 个怪物并造成 2 点基础伤害；每消耗 1 张战斗牌，
 * 该次轰炸额外 + 该牌费用 ×2。轨道轰炸为技能伤害、无视防御（真伤）。
 *
 * <p><b>精准打击</b>：消耗 ≥ {@value OrbitalBombardmentManager#PRECISION_THRESHOLD} 张卡牌时触发，
 * 每次轰炸命中给目标 +1 层「精准打击」（每层使该目标受到的轨道轰炸伤害 +1，永久持续到死亡）。
 *
 * <p>主动为"目标选择器"类技能：触发后经 {@link TargetSelectionManager} 进入选择模式，
 * 确认时由 {@link TargetSelectionAction#apply} 执行轰炸并开始玩家级冷却；取消/超时不冷却。
 */
// 本类不注册任何 @SubscribeEvent，故**不得**标注事件订阅者注解。
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
        if (player == null) return false;
        return CuriosCompat.getCuriosInventory(player)
                .map(h -> h.findFirstCurio(s -> s.is(ModItems.MEGAS_SIGN.get())).isPresent())
                .orElse(false);
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

    private static void tickResupply(Player player) {
        long now = player.level().getGameTime();
        long next = ModAttachments.getMegasResupplyNextTick(player);
        if (next == 0L) {
            ModAttachments.setMegasResupplyNextTick(player, now + RESUPPLY_INTERVAL_TICKS);
            return;
        }
        if (now < next) return;
        if (countHandCards(player) >= RESUPPLY_MAX_CARDS) {
            ModAttachments.setMegasResupplyNextTick(player, now + RESUPPLY_INTERVAL_TICKS);
            return;
        }
        RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.ALL);
        ModAttachments.setMegasResupplyNextTick(player, now + RESUPPLY_INTERVAL_TICKS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动「轨道轰炸」
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 执行轨道轰炸：统计并消耗手牌，算次数与伤害，调度轰炸序列。
     *
     * @return 是否成功触发（手牌 &lt; 2 张 ⇒ 拒绝并提示，返回 false，不消耗、不冷却）
     */
    public static boolean performOrbitalBombardment(ServerPlayer player, LivingEntity primaryTarget) {
        if (player == null || primaryTarget == null || player.level().isClientSide()) return false;
        if (primaryTarget.isRemoved() || !primaryTarget.isAlive()) return false;

        List<ItemStack> handCards = collectHandCards(player);
        int totalCards = 0;
        int battleCostSum = 0;
        for (ItemStack stack : handCards) {
            totalCards += stack.getCount();
            String typeId = CardRegistry.itemToType(stack);
            if (typeId != null) {
                battleCostSum += CardRegistry.cost(typeId, player) * stack.getCount();
            }
        }

        if (totalCards < MIN_CARDS_TO_ACTIVATE) {
            // 兜底(正常已被前置门控 canBeginSelectorSession 拦下):同样走**红色**阻止色。
            sendSignActionBarColored(player, net.minecraft.ChatFormatting.RED,
                    "msg.astral_dice.megas_not_enough_cards");
            return false;
        }

        int strikes = Math.min(totalCards / 2, OrbitalBombardmentManager.MAX_STRIKES);
        float perStrikeDamage = OrbitalBombardmentManager.BASE_DAMAGE
                + (float) battleCostSum * OrbitalBombardmentManager.COST_DAMAGE_MULTIPLIER;
        boolean precision = totalCards >= OrbitalBombardmentManager.PRECISION_THRESHOLD;

        consumeAllHandCards(player);

        OrbitalBombardmentManager.schedule(player, primaryTarget, strikes, perStrikeDamage, precision);
        return true;
    }

    /** 统计玩家物品栏内手牌数（主物品栏 0-35 + 副手，判据 {@link ModItems#isCardItem}）。 */
    public static int countHandCards(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && ModItems.isCardItem(stack)) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (!stack.isEmpty() && ModItems.isCardItem(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 收集玩家物品栏内全部手牌栈（主物品栏 0-35 + 副手）。 */
    private static List<ItemStack> collectHandCards(Player player) {
        List<ItemStack> cards = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && ModItems.isCardItem(stack)) {
                cards.add(stack);
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (!stack.isEmpty() && ModItems.isCardItem(stack)) {
                cards.add(stack);
            }
        }
        return cards;
    }

    /**
     * 消耗玩家物品栏内全部手牌（主物品栏 0-35 + 副手）。
     *
     * <p>照 {@code TemporaryCardUtil.purgeInventory} 口径：主物品栏经 {@code setItem(i, EMPTY)} 清空、
     * 副手经其 List 逐位 {@code set(i, EMPTY)} 清空。
     */
    private static void consumeAllHandCards(Player player) {
        List<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && ModItems.isCardItem(stack)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        List<ItemStack> offhand = player.getInventory().offhand;
        for (int i = 0; i < offhand.size(); i++) {
            ItemStack stack = offhand.get(i);
            if (!stack.isEmpty() && ModItems.isCardItem(stack)) {
                offhand.set(i, ItemStack.EMPTY);
            }
        }
    }
}
