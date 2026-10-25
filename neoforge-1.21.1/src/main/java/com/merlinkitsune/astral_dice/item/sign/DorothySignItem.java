package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.starengine.combat.PartyRelations;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.DorothySocialiteEffect;
import com.merlinkitsune.astral_dice.effect.DorothyWarmthEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.starengine.target.TargetSelectionAction;
import com.merlinkitsune.starengine.target.TargetSelectionRegistry;
import com.merlinkitsune.starengine.target.TargetType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 多萝西立牌（dorothy，奇特）。
 *
 * <p><b>被动「交际花」</b>：每次有效治疗叠 1 层【温暖】（瞬间治疗 / 生命恢复按效果等级计算），上限 5，每层防御 +1；
 * 3 格内出现友方玩家时双方 +2 生命（10 秒一次）。
 *
 * <p><b>主动「真正的我」</b>：传送到目标身边安全落点，随后 1:00 内 3 格内每个友方玩家**首次**进入范围时，
 * 自身 +1 攻（可叠，窗口结束清除）；队友改挂【交际花】——攻击力 +1、**固定 1:00**，与主动窗口解耦
 * （2026-10-25 用户裁决：加成由效果自带的属性修饰符承载，窗口结束不回收，重复获得只把时长刷回满）。
 * 施放时若温暖满 5 层 ⇒ 消耗全部，额外获得「当前防御力」点攻击 1:00。
 */
public class DorothySignItem extends BaseSignItem {
    public static final String ACTION_ID = "astral_dice:dorothy_bond";

    private static final int COOLDOWN_TICKS = 2400;
    private static final int WINDOW_TICKS = 1200;
    private static final int PASSIVE_HEAL_COOLDOWN = 200;
    private static final double PASSIVE_RADIUS = 3.0D;

    static {
        TargetSelectionRegistry.register(new DorothyBondAction());
    }

    public DorothySignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String selectorActionId() {
        return ACTION_ID;
    }

    @Override
    protected int activeCooldownBaseTicks(Player player) {
        return (int) com.merlinkitsune.astral_dice.item.ChargeManager.signCooldownTicks(player, COOLDOWN_TICKS);
    }

    /**
     * 同上,但**不折电池**({@link ChargeManager#signCooldownTicksBase} = 只做充能封顶);
     * 供 {@code sign_active_max_cooldown_base}(命运指引卡减半基准)使用。
     * 与 {@link #activeCooldownBaseTicks(Player)} 逐行镜像,仅把 signCooldownTicks 换成 signCooldownTicksBase。
     */
    @Override
    protected int activeCooldownBaseTicksNoBattery(Player player) {
        return (int) com.merlinkitsune.astral_dice.item.ChargeManager.signCooldownTicksBase(player, COOLDOWN_TICKS);
    }

    /** 是否佩戴本立牌（温暖叠层的前提）。 */
    public static boolean isEquippedForWarmth(Player player) {
        if (player == null) return false;
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(com.merlinkitsune.astral_dice.item.ModItems.DOROTHY_SIGN.get())).isPresent();
    }

    /** 层数 = 温暖效果 amplifier + 1（唯一事实源）。 */
    public static int warmthStacks(Player player) {
        MobEffectInstance instance = player.getEffect(ModEffects.DOROTHY_WARMTH);
        return instance == null ? 0 : Math.min(DorothyWarmthEffect.MAX_STACKS, instance.getAmplifier() + 1);
    }

    /** 按层数刷写防御修饰符（层数变化 / 卸下 / 效果结束时都要走）。 */
    public static void refreshWarmthModifier(Player player) {
        int stacks = warmthStacks(player);
        // 文案「每层防御力 +1」⇒ 护甲按玩家系数倒数折算(2026-10-25 口径修正)
        SignAttributeUtil.apply(player, Attributes.ARMOR, "dorothy_warmth_armor",
                stacks * com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.PLAYER_ARMOR_PER_DEFENSE,
                AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, net.minecraft.world.item.ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        refreshWarmthModifier(player);
        long now = level.getGameTime();

        // 被动：3 格内友方玩家 ⇒ 双方 +2 生命（10 秒一次）
        long nextHeal = ModAttachments.getDorothyHealCooldownEnd(player);
        if (now >= nextHeal) {
            for (Player other : level.players()) {
                if (other == player || !PartyRelations.isSameTeam(player, other)) continue;
                if (player.distanceToSqr(other) > PASSIVE_RADIUS * PASSIVE_RADIUS) continue;
                player.heal(2.0F);
                other.heal(2.0F);
                ModAttachments.setDorothyHealCooldownEnd(player, now + PASSIVE_HEAL_COOLDOWN);
                break;
            }
        }

        // 主动窗口：3 格内友方玩家首次进入 ⇒ 自身 +1 攻（可叠）、队友挂【交际花】1:00
        long windowEnd = ModAttachments.getDorothyWindowEnd(player);
        if (windowEnd > now) {
            for (Player other : level.players()) {
                if (other == player || !PartyRelations.isSameTeam(player, other)) continue;
                if (player.distanceToSqr(other) > PASSIVE_RADIUS * PASSIVE_RADIUS) continue;
                if (!ModAttachments.tryTriggerDorothyPartner(player, other.getUUID())) continue;
                ModAttachments.addDorothyAttackBonus(player, 1);
                refreshAttackBonus(player);
                applySocialite(other);
            }
        } else if (windowEnd != 0L) {
            ModAttachments.setDorothyWindowEnd(player, 0L);
            ModAttachments.clearDorothyPartners(player);
            int bonus = ModAttachments.getDorothyAttackBonus(player);
            refreshAttackBonus(player);
            ModAttachments.setDorothyAttackBonus(player, 0);
            if (bonus != 0) {
                com.merlinkitsune.astral_dice.item.sign.SignAttributeUtil.remove(player, Attributes.ATTACK_DAMAGE,
                        "dorothy_bond_attack");
            }
        }
    }

    /** 攻击加成 = 窗口内每人 1 点 + 满层转化值；统一刷写同一个修饰符。 */
    public static void refreshAttackBonus(Player player) {
        int bonus = ModAttachments.getDorothyAttackBonus(player);
        SignAttributeUtil.apply(player, Attributes.ATTACK_DAMAGE, "dorothy_bond_attack", bonus,
                AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * 给受惠队友挂【交际花】：攻击力 +1、固定 1:00（与主动窗口解耦 —— 窗口结束不回收；重复获得只把时长刷回满）。
     *
     * <p>加成唯一事实源 = {@link DorothySocialiteEffect} 自带的 ATTACK_DAMAGE 属性修饰符；
     * 2026-10-25 用户裁决后不再使用瞬态修饰符 {@code dorothy_partner_attack}。
     */
    private static void applySocialite(Player other) {
        if (other == null || other.level().isClientSide()) return;
        other.addEffect(new MobEffectInstance(ModEffects.DOROTHY_SOCIALITE,
                DorothySocialiteEffect.DURATION_TICKS, 0, false, true, true));
    }

    @Override
    public void onUnequip(SlotContext slotContext, net.minecraft.world.item.ItemStack newStack,
                             net.minecraft.world.item.ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            SignAttributeUtil.remove(player, Attributes.ARMOR, "dorothy_warmth_armor");
            SignAttributeUtil.remove(player, Attributes.ATTACK_DAMAGE, "dorothy_bond_attack");
            ModAttachments.setDorothyWindowEnd(player, 0L);
            ModAttachments.setDorothyAttackBonus(player, 0);
            ModAttachments.clearDorothyPartners(player);
        }
        super.onUnequip(slotContext, newStack, stack);
    }

    private static final class DorothyBondAction implements TargetSelectionAction {
        @Override
        public String id() {
            return ACTION_ID;
        }

        @Override
        public TargetType targetType() {
            return TargetType.PLAYER;
        }

        @Override
        public void onStarted(ServerPlayer player) {
            sendSignActionBar(player, "msg.astral_dice.dorothy_ready");
        }

        @Override
        public void apply(ServerPlayer player, LivingEntity target) {
            if (!(target instanceof Player other)) return;
            BlockPos safe = findSafeSpot(other);
            if (safe == null) {
                sendSignActionBarColored(player, net.minecraft.ChatFormatting.RED,
                        "msg.astral_dice.dorothy_no_spot");
                return;
            }
            player.teleportTo((net.minecraft.server.level.ServerLevel) other.level(), safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            long now = player.level().getGameTime();
            ModAttachments.setDorothyWindowEnd(player, now + WINDOW_TICKS);
            ModAttachments.clearDorothyPartners(player);
            // 传送落地即刻算一次「路过」：双方回血；同队才按 tick 循环同一入口给 +1 攻（队友走【交际花】效果）并记入「每人一次」集合
            player.heal(2.0F);
            other.heal(2.0F);
            if (PartyRelations.isSameTeam(player, other)
                    && ModAttachments.tryTriggerDorothyPartner(player, other.getUUID())) {
                ModAttachments.addDorothyAttackBonus(player, 1);
                refreshAttackBonus(player);
                applySocialite(other);
            }
            // 满层转化：消耗全部温暖，额外获得「当前防御力」点攻击
            if (warmthStacks(player) >= DorothyWarmthEffect.MAX_STACKS) {
                com.merlinkitsune.starengine.event.ModEffectRemoval.remove(player, ModEffects.DOROTHY_WARMTH);
                // 「当前防御力」= 当前护甲折算回的防御力点数(1 防御力 = 1÷玩家护甲系数 护甲值;2026-10-25 口径修正:
                // 原先写死 armor / 2.0 是旧公式「2 + 护甲÷2」的遗留)
                double armor = player.getAttributeValue(Attributes.ARMOR);
                int converted = (int) Math.floor(
                        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.defensePointsForArmor(armor, true)
                                + 1.0E-6D);
                if (converted > 0) {
                    ModAttachments.addDorothyAttackBonus(player, converted);
                }
                refreshAttackBonus(player);
                refreshWarmthModifier(player);
            }
            ModAttachments.setSignActiveCooldownEnd(player, now
                    + (int) com.merlinkitsune.astral_dice.item.ChargeManager.signCooldownTicks(player, COOLDOWN_TICKS));
            // 冷却口径一致(2026-10-22 补):记录本次实际最大冷却,供「瞬时冷却」/减免换算剩余比例
            ModAttachments.setSignActiveMaxCooldown(player, (long) ((int) com.merlinkitsune.astral_dice.item.ChargeManager.signCooldownTicks(player, COOLDOWN_TICKS)));
            CurrentCoreChipItem.onActiveSkillUsed(player);
            sendSignActionBar(player, "msg.astral_dice.dorothy_done");
        }
    }

    /** 目标周围 3 格内找可站立落点（脚位与头顶皆空、脚下为固体）；找不到返回 null。 */
    public static BlockPos findSafeSpot(LivingEntity target) {
        Level level = target.level();
        BlockPos origin = target.blockPosition();
        BlockPos best = null;
        for (int dy = 0; dy >= -2; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) continue;
                    if (!level.getBlockState(pos.below()).canOcclude()) continue;
                    if (!level.getBlockState(pos).isAir()) continue;
                    if (!level.getBlockState(pos.above()).isAir()) continue;
                    if (dx == 0 && dz == 0 && dy == 0) return pos;
                    if (best == null) best = pos;
                }
            }
        }
        return best;
    }
}
