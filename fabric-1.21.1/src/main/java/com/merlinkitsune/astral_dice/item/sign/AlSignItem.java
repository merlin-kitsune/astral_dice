package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.choice.CardChoiceManager;
import com.merlinkitsune.astral_dice.choice.CardChoiceType;
import com.merlinkitsune.astral_dice.compat.curios.SlotContext;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.shop.ChipShopDiscounts;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import com.merlinkitsune.astral_dice.target.PlayerLikeTargets;
import com.merlinkitsune.astral_dice.target.SelfTargetable;
import com.merlinkitsune.starengine.economy.StarEngineEconomy;
import com.merlinkitsune.starengine.target.TargetSelectionAction;
import com.merlinkitsune.starengine.target.TargetSelectionRegistry;
import com.merlinkitsune.starengine.target.TargetType;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * 少主立牌（al，史诗）。
 *
 * <p><b>主动「强者怜悯」</b>：目标选择器选一名类玩家目标 → 选卡界面选 <b>1~3 张</b>（放 1 张即可确认，
 * 上限 3 张；背包没有牌直接取消、不进冷却）→ 逐张交给对方（玩家走唯一发牌漏斗
 * {@link VitaminPillChipItem#giveCard}，附属注册生物走 {@link PlayerLikeTargets#giveCard}），
 * 对方物品栏满则掉在对方脚下；随后自身获得「送出张数 × 2」星币。
 *
 * <p><b>被动「双赢」</b>分两个<b>严格分离</b>的触发面（2026-11-08 用户裁决）：
 * <ul>
 *   <li><b>A. 给牌</b>（主动发卡、「你有我有」等一切走发牌漏斗的行为）：佩戴者使**其他**角色获得卡牌时，
 *       双方各 +{@link #STARLIGHT_PER_GIFT} 层星光。挂点是发牌漏斗成功分支的
 *       {@link #onCardGivenToOther}，同 tick 同受益人只结算一次。</li>
 *   <li><b>B. 对目标使用主动技能</b>（只在本立牌 {@link #resolve} 内）：双方各获得一次
 *       「筹码单价 -5（下限 5）」与一次刷新次数。**折扣绝不由 A 面触发**。</li>
 * </ul>
 *
 * <p><b>被动「星光」</b>：自身每 {@link #STARLIGHT_PER_ATTRIBUTE} 层星光 ⇒ 攻击力 +1、防御力 +1
 * （护甲按 {@code DiceCombatModifiers.PLAYER_ARMOR_PER_DEFENSE} 折算）；每 tick 按当前星光重算，
 * {@link SignAttributeUtil} 同 id 覆盖式写入 ⇒ 不会叠加出多个修饰符；卸下即移除。
 *
 * <p>星币账户只对玩家生效（{@code StarEngineEconomy} 是前置库的玩家账户）；附属注册生物同样持有星光、
 * 卡牌与折扣，但不参与星币记账。
 */
public class AlSignItem extends BaseSignItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlSignItem.class);

    public static final String ACTION_ID = "astral_dice:al_gift";

    /** 每送出一张卡的星币收益。 */
    private static final int COIN_PER_CARD = 2;
    /** A 面：一次给牌事件双方各获得的星光层数。 */
    private static final int STARLIGHT_PER_GIFT = 1;
    /** 每多少层星光 +1 攻/+1 防。 */
    private static final int STARLIGHT_PER_ATTRIBUTE = 4;
    private static final String ATTACK_KEY = "al_starlight_attack";
    private static final String ARMOR_KEY = "al_starlight_armor";
    /** 同 tick 去重窗口（tick）。 */
    private static final long BATCH_EXPIRE_TICKS = 20L;

    public AlSignItem(Properties properties) {
        super(properties);
    }

    static {
        TargetSelectionRegistry.register(new GiftAction());
        CardGivenHooks.subscribe(AlSignItem::onCardGivenToOther);
    }

    @Override
    protected String selectorActionId() {
        return ACTION_ID;
    }

    /** 背包里可用的卡牌数量（战斗牌 + 效果牌）。 */
    public static int countCards(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ModItems.isCardItem(stack)) count += stack.getCount();
        }
        return count;
    }

    private static final class GiftAction implements TargetSelectionAction, SelfTargetable {
        @Override
        public String id() {
            return ACTION_ID;
        }

        @Override
        public TargetType targetType() {
            return TargetType.PLAYER;
        }

        /** 主动技能不可对自己使用（用户需求：选择「一个玩家」作为给予对象）。 */
        @Override
        public boolean allowSelf() {
            return false;
        }

        @Override
        public void onStarted(ServerPlayer player) {
            sendSignActionBar(player, "msg.astral_dice.al_ready");
        }

        @Override
        public void apply(ServerPlayer player, LivingEntity target) {
            // 选择器已按 TargetType.PLAYER + PlayerLikeTargets 过滤，这里再兜一层（防止别的入口直接调用）
            if (target == null || target == player || !PlayerLikeTargets.isPlayerLike(target)) return;
            if (countCards(player) < 1) {
                sendSignActionBarColored(player, ChatFormatting.RED, "msg.astral_dice.al_no_cards");
                return;
            }
            ModAttachments.setAlPendingTarget(player, target.getUUID().toString());
            boolean opened = CardChoiceManager.start(player, CardChoiceManager.SLOTS_TRIPLE, 1,
                    CardChoiceType.ANY, chosen -> resolve(player, chosen)) > 0;
            if (!opened) {
                ModAttachments.setAlPendingTarget(player, "");
                sendSignActionBarColored(player, ChatFormatting.RED, "msg.astral_dice.al_no_cards");
            }
        }
    }

    /** 选卡确认后的结算（服务端）。chosen 为空 ⇒ 视为取消：不结算、不进冷却。 */
    private static void resolve(ServerPlayer caster, List<ItemStack> chosen) {
        String uuid = ModAttachments.getAlPendingTarget(caster);
        ModAttachments.setAlPendingTarget(caster, "");
        if (chosen == null || chosen.isEmpty() || uuid.isEmpty()) return;
        LivingEntity target = resolveTarget(caster, uuid);
        if (target == null || target == caster) return;

        int given = 0;
        for (ItemStack card : chosen) {
            if (card == null || card.isEmpty()) continue;
            ItemStack one = card.copyWithCount(1);
            if (PlayerLikeTargets.giveCard(caster, target, one)) {
                given++;
            } else {
                // 目标无法接收（背包满 / 附属未实现）⇒ 掉在目标脚下，卡牌仍然「已送出」
                target.spawnAtLocation(one);
                given++;
            }
        }
        if (given <= 0) return;

        // ── 星币收益 ──
        int coins = given * COIN_PER_CARD;
        if (caster.level() instanceof ServerLevel) {
            StarEngineEconomy.deposit(caster, coins);
        }

        // ── B 面：对目标使用主动技能 ⇒ 双方各一次折扣 + 一次刷新 ──
        ChipShopDiscounts.grant(caster, ChipShopDiscounts.DISCOUNT, ChipShopDiscounts.DURATION_TICKS);
        ChipShopDiscounts.grant(target, ChipShopDiscounts.DISCOUNT, ChipShopDiscounts.DURATION_TICKS);
        grantRefresh(caster);
        if (target instanceof ServerPlayer other) {
            grantRefresh(other);
        }

        // ── 冷却三件套 + 提示 ──
        long now = caster.level().getGameTime();
        ModAttachments.setSignActiveCooldownEnd(caster, now + WeirdDiceHandler.signCooldownTicks(caster));
        ModAttachments.setSignActiveMaxCooldown(caster, (long) WeirdDiceHandler.signCooldownTicks(caster));
        CurrentCoreChipItem.onActiveSkillUsed(caster);
        sendSignActionBar(caster, "msg.astral_dice.al_done", given, coins);
        LOGGER.info("AP_AL_GIFT: player={}:target={}:given={}:coins={}", caster.getName().getString(),
                target.getName().getString(), given, coins);
    }

    /** 授予一次筹码商店刷新（玩家专属：{@link ChipShopState} 是玩家存档态）。 */
    private static void grantRefresh(Player player) {
        if (player == null) return;
        ChipShopState next = ChipShopState.get(player).withRefreshGranted();
        ChipShopState.store(player, next);
    }

    /** 按 UUID 找任意活体（先查在线玩家，再扫各维度；跨维度给予也要成立）。 */
    private static LivingEntity resolveTarget(ServerPlayer caster, String uuid) {
        MinecraftServer server = caster.getServer();
        if (server == null) return null;
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  A 面：给牌 ⇒ 双方各 +1 星光（唯一挂点 = 发牌漏斗成功分支）
    // ══════════════════════════════════════════════════════════════════════════

    /** 同 tick 已结算过的「给牌事件」（giver → receivers），防同一动作多次发牌刷星光。 */
    private static final java.util.Map<UUID, Batch> BATCHES = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class Batch {
        private final long tick;
        private final java.util.Set<UUID> receivers = java.util.concurrent.ConcurrentHashMap.newKeySet();

        private Batch(long tick) {
            this.tick = tick;
        }
    }

    /** 佩戴者是否装备了本立牌。 */
    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.AL_SIGN.get());
    }

    /**
     * 被动「双赢」A 面入口：**佩戴者**使其他角色获得卡牌时，双方各 +1 层星光。
     *
     * <p>由发牌唯一漏斗 {@link VitaminPillChipItem#giveCard} 的成功分支与
     * {@link PlayerLikeTargets#giveCard}（附属注册生物）调用；同 tick 同受益人只结算一次。
     */
    public static void onCardGivenToOther(LivingEntity rawGiver, LivingEntity receiver) {
        // 2026-11-09（C）：中立发牌方；未知发牌方无法校验佩戴者的「双赢」被动，显式跳过并留锚点。
        ServerPlayer giver = rawGiver instanceof ServerPlayer sp ? sp : null;
        if (giver == null) {
            if (receiver != null) {
                LOGGER.info("AP_AL_GIFT_STARLIGHT: skipped=unknown_giver:receiver={}", receiver.getName().getString());
            }
            return;
        }
        if (receiver == null) return;
        if (giver.level().isClientSide() || receiver.level().isClientSide()) return;
        if (giver == receiver) return; // 自己给自己不算「对方」
        if (!isEquipped(giver)) return;
        long now = giver.level().getGameTime();
        cleanupBatches(now);
        Batch batch = BATCHES.compute(giver.getUUID(),
                (id, previous) -> (previous == null || previous.tick != now) ? new Batch(now) : previous);
        if (!batch.receivers.add(receiver.getUUID())) return;
        StarLightManager.add(giver, STARLIGHT_PER_GIFT);
        StarLightManager.add(receiver, STARLIGHT_PER_GIFT);
        LOGGER.info("AP_AL_GIFT_STARLIGHT: giver={}:receiver={}:giverLight={}:receiverLight={}",
                giver.getName().getString(), receiver.getName().getString(),
                StarLightManager.get(giver), StarLightManager.get(receiver));
    }

    private static void cleanupBatches(long now) {
        if (BATCHES.isEmpty()) return;
        BATCHES.entrySet().removeIf(entry -> now - entry.getValue().tick > BATCH_EXPIRE_TICKS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「星光」：每 4 层星光 +1 攻/+1 防
    // ══════════════════════════════════════════════════════════════════════════

    private static void refreshStarlightAttributes(Player player) {
        int stars = StarLightManager.get(player);
        int steps = Math.max(0, stars / STARLIGHT_PER_ATTRIBUTE);
        SignAttributeUtil.apply(player, Attributes.ATTACK_DAMAGE, ATTACK_KEY, steps,
                AttributeModifier.Operation.ADD_VALUE);
        SignAttributeUtil.apply(player, Attributes.ARMOR, ARMOR_KEY,
                steps * com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.PLAYER_ARMOR_PER_DEFENSE,
                AttributeModifier.Operation.ADD_VALUE);
    }

    private static void clearStarlightAttributes(Player player) {
        SignAttributeUtil.remove(player, Attributes.ATTACK_DAMAGE, ATTACK_KEY);
        SignAttributeUtil.remove(player, Attributes.ARMOR, ARMOR_KEY);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // SignAttributeUtil 同 id 覆盖式写入：值没变时是纯查表，逐 tick 调用可接受
        refreshStarlightAttributes(player);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        clearStarlightAttributes(player);
    }

    // 2026-11-09（A）/ P1 移植注：类玩家宿主的主动入口（1.21.1 侧 activateForEntity）依赖
    // SignEntitySkills，本线无该前置 ⇒ 不覆写（玩家路径逐字等价），语义差异已登记在移植报告。
}
