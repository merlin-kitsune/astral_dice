package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.entity.GrapplingHookEntity;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.astral_dice.target.TargetSelectionManager;
import com.merlinkitsune.starengine.combat.PartyRelations;
import com.merlinkitsune.starengine.target.TargetSelectionAction;
import com.merlinkitsune.starengine.target.TargetSelectionRegistry;
import com.merlinkitsune.starengine.target.TargetType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.merlinkitsune.astral_dice.compat.curios.SlotContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 垃圾桶立牌(z3000,稀有)。
 *
 * <p>主动「过来把你」:选择一个 16 格内的敌对目标,用抓钩把它拖到自身面前并造成 5 点技能伤害;
 * 被拖拽过的目标被击杀时(不限击杀时机),获得 1 层「反击」。
 *
 * <p>被动「回收利用」:
 * <ul>
 *   <li>每击杀一个敌对目标,使主动技能剩余冷却 -33%,每分钟最多触发一次;</li>
 *   <li>每击杀 2 个最大生命不少于 20 的敌对目标,攻击力 +1,最多 +12。</li>
 * </ul>
 */
public class Z3000SignItem extends BaseSignItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(Z3000SignItem.class);

    public static final double RANGE = 16.0D;
    public static final float ACTIVE_DAMAGE = 5.0F;
    public static final int ATTACK_CAP = 12;
    public static final int HEALTHY_KILLS_PER_STACK = 2;
    public static final double HEALTHY_KILL_THRESHOLD = 20.0D;
    public static final long COOLDOWN_REDUCTION_INTERVAL_TICKS = 1200L;
    private static final String ATTACK_MODIFIER_KEY = "z3000_attack_bonus";
    /** 被拖拽标记前缀；后缀是拖拽者 UUID，击杀时据此补发反击层。 */
    private static final String DRAGGED_TAG_PREFIX = "astral_dice.z3000_dragged.";

    /** 抓钩开始拖拽时打到目标身上的标记。 */
    public static String draggedTag(Player dragger) {
        return DRAGGED_TAG_PREFIX + dragger.getUUID();
    }

    static {
        TargetSelectionRegistry.register(new TargetSelectionAction() {
            @Override
            public String id() {
                return "z3000_pull";
            }

            @Override
            public TargetType targetType() {
                return TargetType.ENEMY;
            }

            @Override
            public void apply(ServerPlayer player, LivingEntity target) {
                if (target.level() != player.level() || !target.isAlive()) {
                    return;
                }
                if (target.distanceToSqr(player) > RANGE * RANGE) {
                    sendSignActionBar(player, "msg.astral_dice.z3000_too_far");
                    LOGGER.info("AP_Z3000_REJECT reason=too_far player={} target={}",
                            player.getName().getString(), target.getName().getString());
                    return;
                }
                GrapplingHookEntity hook = new GrapplingHookEntity(player.serverLevel(), player, target,
                        ACTIVE_DAMAGE);
                player.serverLevel().addFreshEntity(hook);
                long cooldown = WeirdDiceHandler.signCooldownTicks(player);
                ModAttachments.setSignActiveCooldownEnd(player, player.level().getGameTime() + cooldown);
                ModAttachments.setSignActiveMaxCooldown(player, cooldown);
                CurrentCoreChipItem.onActiveSkillUsed(player);
                sendSignActionBar(player, "msg.astral_dice.z3000_pull", target.getDisplayName());
                LOGGER.info("AP_Z3000_ACTIVE player={} target={} dist={} cooldown={}",
                        player.getName().getString(), target.getName().getString(),
                        String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(target.distanceToSqr(player))),
                        cooldown);
            }
        });
    }

    public Z3000SignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String selectorActionId() {
        return "z3000_pull";
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        refreshAttackBonus(player);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        ModAttachments.setZ3000AttackStacks(player, 0);
        ModAttachments.setZ3000HealthyKills(player, 0);
        ModAttachments.setZ3000LastCooldownReduction(player, 0L);
        refreshAttackBonus(player);
    }

    @Override
    protected void onKill(Player killer, LivingEntity killed) {
        if (killed instanceof Player || !PartyRelations.isHostileTo(killer, killed)) return;
        if (wasDraggedBy(killed, killer)) {
            SouriSignManager.addCounter(killer, 1);
            LOGGER.info("AP_Z3000_DRAG_KILL player={} target={} counter={}",
                    killer.getName().getString(), killed.getName().getString(),
                    ModAttachments.getRenCounterCharges(killer));
        }
        if (killed.getMaxHealth() >= HEALTHY_KILL_THRESHOLD) {
            int progress = ModAttachments.getZ3000HealthyKills(killer) + 1;
            if (progress >= HEALTHY_KILLS_PER_STACK) {
                progress = 0;
                int stacks = Math.min(ATTACK_CAP, ModAttachments.getZ3000AttackStacks(killer) + 1);
                ModAttachments.setZ3000AttackStacks(killer, stacks);
                refreshAttackBonus(killer);
            }
            ModAttachments.setZ3000HealthyKills(killer, progress);
        }

        long now = killer.level().getGameTime();
        long last = ModAttachments.getZ3000LastCooldownReduction(killer);
        if (now - last < COOLDOWN_REDUCTION_INTERVAL_TICKS) return;
        long end = ModAttachments.getSignActiveCooldownEnd(killer);
        if (end <= now) return;
        long remaining = end - now;
        long reduced = Math.max(0L, (remaining * 67L + 99L) / 100L);
        ModAttachments.setSignActiveCooldownEnd(killer, now + reduced);
        ModAttachments.setZ3000LastCooldownReduction(killer, now);
        LOGGER.info("AP_Z3000_RECYCLE player={} target={} remaining={}->{}",
                killer.getName().getString(), killed.getName().getString(), remaining, reduced);
    }

    /** 目标是否被该玩家拖拽过；标记写在实体 tags 上，拖拽结束或超时都不会清除。 */
    private static boolean wasDraggedBy(LivingEntity killed, Player killer) {
        return killed.getTags().contains(DRAGGED_TAG_PREFIX + killer.getUUID());
    }

    public static void refreshAttackBonus(Player player) {
        if (player == null || player.level().isClientSide()) return;
        SignAttributeUtil.apply(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER_KEY,
                ModAttachments.getZ3000AttackStacks(player), AttributeModifier.Operation.ADD_VALUE);
    }

    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.Z3000_SIGN.get());
    }
}
