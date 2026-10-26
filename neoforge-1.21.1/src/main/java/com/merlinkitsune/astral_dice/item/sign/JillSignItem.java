package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.choice.CardChoiceManager;
import com.merlinkitsune.astral_dice.choice.CardChoiceType;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.astral_dice.shop.CardShopCatalog;
import com.merlinkitsune.astral_dice.shop.CardTradeFilter;
import com.merlinkitsune.starengine.target.SelfTargetable;
import com.merlinkitsune.starengine.economy.StarEngineEconomy;
import com.merlinkitsune.starengine.target.TargetSelectionAction;
import com.merlinkitsune.starengine.target.TargetSelectionRegistry;
import com.merlinkitsune.starengine.target.TargetType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.merlinkitsune.astral_dice.event.PlayerEffectProtection;

/**
 * 调酒师立牌（jill，奇特）。
 *
 * <p><b>被动「改变生活」</b>：调制结算时 3 张牌**全部同类型** ⇒ 自身与目标各获迅捷 II(1:00)；
 * 否则各 +3 星币。
 *
 * <p><b>主动「调制人生」</b>：目标选择器选一名玩家 → 选卡界面选 3 张（背包现有牌，不足 3 张直接取消、
 * 不进冷却）→ 按槽位顺序结算：攻击牌 +2 攻/张、防御牌 +2 防/张（均叠加，2:00）；效果牌每张各结算
 * 1 次 {@code heal(HEAL_AMOUNT)}（2 点/次；3 张 = 3 次治疗 = 6 点）—— **刻意逐张调用、不合并成一次
 * heal(6)**，这样治疗事件按张数各触发一次（多萝西「温暖」按次数叠层；赵赐福溢出 / 治疗失效等下游
 * 按每个治疗事件各结算一次，即 3 张共 3 次），无状态图标；随后消耗这 3 张、
 * 从商店可售池补 3 张。非全效果牌时目标另获 {@code jill_mixed}(2:00)，攻/防修饰符与内部的
 * 目标 + 到期刻只在攻击牌 + 防御牌 &gt; 0 时记录（全效果牌不写计时器）。
 */
public class JillSignItem extends BaseSignItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(JillSignItem.class);

    public static final String ACTION_ID = "astral_dice:jill_mix";

    private static final int DURATION_TICKS = 2400;
    /** 每张效果牌提供的即时治疗量（每张各结算 1 次治疗：1 张 1 次 = 2 点、3 张 3 次 = 6 点）。 */
    private static final float HEAL_AMOUNT = 2.0F;
    private static final int COIN_PER_PROC = 3;
    private static final String TARGET_ATTACK_KEY = "jill_target_attack";
    private static final String TARGET_ARMOR_KEY = "jill_target_armor";

    public JillSignItem(Properties properties) {
        super(properties);
    }

    static {
        TargetSelectionRegistry.register(new JillMixAction());
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

    private static final class JillMixAction implements TargetSelectionAction, SelfTargetable {
        @Override
        public String id() {
            return ACTION_ID;
        }

        @Override
        public TargetType targetType() {
            return TargetType.PLAYER;
        }

        @Override
        public boolean allowSelf() {
            return true;
        }

        @Override
        public void onStarted(ServerPlayer player) {
            sendSignActionBar(player, "msg.astral_dice.jill_ready");
        }

        @Override
        public void apply(ServerPlayer player, LivingEntity target) {
            if (!(target instanceof Player other)) return;
            if (countCards(player) < 3) {
                sendSignActionBarColored(player, ChatFormatting.RED, "msg.astral_dice.jill_not_enough_cards");
                return;
            }
            ModAttachments.setJillPendingTarget(player, other.getUUID().toString());
            boolean opened = CardChoiceManager.start(player, CardChoiceManager.SLOTS_TRIPLE,
                    CardChoiceType.ANY, chosen -> resolve(player, chosen)) > 0;
            if (!opened) {
                ModAttachments.setJillPendingTarget(player, "");
                sendSignActionBarColored(player, ChatFormatting.RED, "msg.astral_dice.jill_not_enough_cards");
            }
        }
    }

    /** 选卡确认后的结算（服务端）。chosen 少于 3 ⇒ 视为取消：不结算、不进冷却。 */
    private static void resolve(ServerPlayer caster, List<ItemStack> chosen) {
        String uuid = ModAttachments.getJillPendingTarget(caster);
        ModAttachments.setJillPendingTarget(caster, "");
        if (chosen == null || chosen.size() < 3 || uuid.isEmpty()) return;
        Player target = resolveTarget(caster, uuid);
        if (target == null) return;

        int attack = 0, defense = 0, effect = 0;
        for (ItemStack stack : chosen) {
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (path.startsWith("attack_card")) attack++;
            else if (path.startsWith("defense_card")) defense++;
            else if (ModItems.isEffectCard(stack)) effect++;
        }
        int total = attack + defense + effect;
        if (total < 3) return;

        // 全效果牌(3 张效果牌)只免去 jill_mixed 与内部计时器;被动(迅捷/星币)照常按「同类型 / 混搭」判定。
        boolean allEffect = effect == 3;
        boolean allSame = attack == 3 || defense == 3 || effect == 3;

        clearTargetBuff(caster);
        if (attack > 0) {
            SignAttributeUtil.apply(target, Attributes.ATTACK_DAMAGE, TARGET_ATTACK_KEY, attack * 2.0D,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
        }
        if (defense > 0) {
            // 每张防御牌 = 防御力 +2 ⇒ 护甲按玩家系数倒数折算(2026-10-25 口径修正)
            SignAttributeUtil.apply(target, Attributes.ARMOR, TARGET_ARMOR_KEY,
                    defense * 2 * com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.PLAYER_ARMOR_PER_DEFENSE,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
        }
        if (effect > 0) {
            // 每张效果牌各结算 1 次 heal(2 点):3 张 = 3 次治疗 = 6 点。
            // 刻意逐张调用(不合并成一次 heal(6)) —— 治疗事件按张数各触发一次,多萝西「温暖」因此按次数叠层
            // (3 张 = +3 层),赵赐福溢出 / 治疗失效事件等下游也各结算一次(2026-10-26 用户裁决)。
            for (int i = 0; i < effect; i++) {
                target.heal(HEAL_AMOUNT);
            }
        }
        // 全效果牌不施加 jill_mixed(混合牌标记只属于非全效果牌的组合);旧实例不主动清除。
        if (!allEffect) {
            PlayerEffectProtection.apply(target, new MobEffectInstance(ModEffects.JILL_MIXED, DURATION_TICKS, 0, false, true, true));
        }
        // 记录受惠目标与到期刻(2:00):由 onCurioTick 到期摘除 / onUnequip 立即清。
        // 全效果牌没有攻/防修饰符,不写内部计时器(攻击 + 防御 == 0)。
        if (attack + defense > 0) {
            ModAttachments.setJillBuffTarget(caster, target.getUUID().toString());
            ModAttachments.setJillBuffEnd(caster, caster.level().getGameTime() + DURATION_TICKS);
        }

        if (allSame) {
            PlayerEffectProtection.apply(caster, new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1, false, true, true));
            PlayerEffectProtection.apply(target, new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1, false, true, true));
        } else {
            StarEngineEconomy.deposit(caster, COIN_PER_PROC);
            StarEngineEconomy.deposit(target, COIN_PER_PROC);
        }

        grantRandomCards(caster, 3);

        ModAttachments.setSignActiveCooldownEnd(caster, caster.level().getGameTime()
                + com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(caster));
        // 冷却口径一致(2026-10-22 补):记录本次实际最大冷却,供「瞬时冷却」/减免换算剩余比例
        ModAttachments.setSignActiveMaxCooldown(caster, (long) (com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(caster)));
        CurrentCoreChipItem.onActiveSkillUsed(caster);
        sendSignActionBar(caster, "msg.astral_dice.jill_done");
        LOGGER.info("AP_JILL: player={}:target={}:atk={}:def={}:eff={}:same={}:allEffect={}", caster.getName().getString(),
                target.getName().getString(), attack, defense, effect, allSame, allEffect);
    }

    /** 从商店可售池随机补 {@code count} 张牌（战斗牌 + 效果牌）。 */
    private static void grantRandomCards(ServerPlayer player, int count) {
        List<CardShopCatalog.PoolEntry> pool = new ArrayList<>();
        for (CardShopCatalog.PoolEntry entry : CardShopCatalog.entries()) {
            ItemStack probe = new ItemStack(entry.item().get());
            if (CardTradeFilter.isSellable(probe)) pool.add(entry);
        }
        if (pool.isEmpty()) return;
        for (int i = 0; i < count; i++) {
            CardShopCatalog.PoolEntry pick = CardShopCatalog.pickWeighted(player.getRandom(), pool);
            if (pick == null) return;
            ItemStack stack = new ItemStack(pick.item().get());
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    /** 清除当前受惠目标身上的 jill 攻/防修饰符,并复位目标/到期刻记录(改投新目标前、到期、卸下时调用)。 */
    private static void clearTargetBuff(Player caster) {
        String uuid = ModAttachments.getJillBuffTarget(caster);
        ModAttachments.setJillBuffTarget(caster, "");
        ModAttachments.setJillBuffEnd(caster, 0L);
        if (uuid.isEmpty()) return;
        Player target = resolveTarget(caster, uuid);
        if (target != null) {
            SignAttributeUtil.remove(target, Attributes.ATTACK_DAMAGE, TARGET_ATTACK_KEY);
            SignAttributeUtil.remove(target, Attributes.ARMOR, TARGET_ARMOR_KEY);
        }
    }

    private static Player resolveTarget(Player caster, String uuid) {
        try {
            UUID id = UUID.fromString(uuid);
            return caster.getServer() == null ? null : caster.getServer().getPlayerList().getPlayer(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player caster)) return;
        Level level = caster.level();
        if (level.isClientSide()) return;
        long end = ModAttachments.getJillBuffEnd(caster);
        if (end > 0L && level.getGameTime() >= end) {
            clearTargetBuff(caster);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player caster) {
            clearTargetBuff(caster);
        }
        super.onUnequip(slotContext, newStack, stack);
    }
}
