package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import com.merlinkitsune.starengine.economy.StarEngineEconomy;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import com.merlinkitsune.astral_dice.event.PlayerEffectProtection;

/**
 * 大肥鱼立牌（deepseek，奇特，仅创造获得）。
 *
 * <p><b>被动「摸鱼中」</b>：仅按精确位置判定 —— 每 tick 与上一 tick 的 (x,y,z) 快照比对，
 * 连续静止 {@link #IDLE_TICKS}（200 tick = 10 秒）⇒ 获得 {@code deepseek_fishing}
 * （免疫伤害/负面效果、不被怪物索敌、半透明、HUD 指示器），并在获得那一刻清除附近已锁定该玩家的怪物仇恨；
 * 获得后累计移动 {@link #MOVE_LIMIT_TICKS}（100 tick = 5 秒）⇒ 解除（中途停下不清零累计；转头不算移动）。
 * 效果本身是有限时长且静止时逐 tick 滚动续期（安全网：漏 tick / 摘下骰子后自然到期）。
 *
 * <p><b>主动「大口干饭」</b>：掷 1..20 星币消耗并按当前余额夹取，按实际消耗等量获得攻击力/防御力
 * （{@link #BUFF_TICKS} = 2:00），随后随机获得 {@link #CARD_COUNT} 张牌；
 * 余额为 0 / 扣款失败 ⇒ 直接失败返回（不加成、不发牌、不进冷却）。
 */
public class DeepseekSignItem extends BaseSignItem {
    private static final int IDLE_TICKS = 200;
    private static final int MOVE_LIMIT_TICKS = 100;
    private static final int BUFF_TICKS = 2400;
    private static final int CARD_COUNT = 3;
    /** 摸鱼效果时长：静止判定 + 1 秒余量；静止时逐 tick 滚动续期。 */
    private static final int FISHING_TICKS = IDLE_TICKS + 20;
    /** 获得摸鱼时清理仇恨的半径（与骇客立牌同一口径）。 */
    private static final double MOB_CLEAR_RANGE = 64.0D;

    public DeepseekSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        long now = level.getGameTime();
        // Curio tick 中断检测（摘骰子 / 卸下再戴 / 跨维度切换）：BaseSignItem#curioTick 的骰子闸门
        // 会让 onCurioTick 整段停跑，lastTick 随之中断。缺口 > 1 tick ⇒ 旧位置快照与静止起点作废，
        // 否则同一坐标重戴即可沿用旧静止时间白拿摸鱼。
        long lastTick = ModAttachments.getDeepseekLastTick(player);
        if (lastTick > 0L && now - lastTick > 1L) {
            ModAttachments.setDeepseekLastPos(player, List.of());
            ModAttachments.setDeepseekMoveTicks(player, 0);
            ModAttachments.setDeepseekIdleSince(player, now);
        }
        ModAttachments.setDeepseekLastTick(player, now);
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        List<Double> last = ModAttachments.getDeepseekLastPos(player);
        boolean hasLast = last.size() >= 3;
        boolean moved = !hasLast
                || Double.compare(last.get(0), x) != 0
                || Double.compare(last.get(1), y) != 0
                || Double.compare(last.get(2), z) != 0;
        // 先读上一 tick 快照再立即更新：下一 tick 比较的永远是"上一 tick 的位置"
        ModAttachments.setDeepseekLastPos(player, List.of(x, y, z));
        if (!hasLast) {
            // 首 tick（刚装备 / 刚上线）：只建立快照，静止从此刻起算
            ModAttachments.setDeepseekIdleSince(player, now);
            ModAttachments.setDeepseekMoveTicks(player, 0);
            return;
        }
        if (moved) {
            // 移动 ⇒ 静止起点重置；只有摸鱼中才累计移动 tick（停下不清零累计）
            ModAttachments.setDeepseekIdleSince(player, now);
            if (player.hasEffect(ModEffects.DEEPSEEK_FISHING.get())) {
                int walked = ModAttachments.getDeepseekMoveTicks(player) + 1;
                if (walked >= MOVE_LIMIT_TICKS) {
                    removeFishing(player);
                    ModAttachments.setDeepseekMoveTicks(player, 0);
                } else {
                    ModAttachments.setDeepseekMoveTicks(player, walked);
                }
            }
            return;
        }
        if (player.hasEffect(ModEffects.DEEPSEEK_FISHING.get())) {
            // 摸鱼中且静止：滚动续期（安全网）；仇恨只在首次获得时清，避免反复打断
            applyFishing(player, false);
            return;
        }
        long since = ModAttachments.getDeepseekIdleSince(player);
        if (since <= 0L) {
            ModAttachments.setDeepseekIdleSince(player, now);
            return;
        }
        if (now - since >= IDLE_TICKS) {
            applyFishing(player, true);
            ModAttachments.setDeepseekMoveTicks(player, 0);
        }
    }

    /**
     * 施加/续期摸鱼效果。
     *
     * @param clearAggro 只在**首次获得**时传 true：清掉附近已把该玩家设为目标生物的仇恨；
     *                   逐 tick 续期传 false（已锁定玩家的怪物不该被反复清空仇恨）。
     */
    private static void applyFishing(Player player, boolean clearAggro) {
        PlayerEffectProtection.apply(player, new MobEffectInstance(ModEffects.DEEPSEEK_FISHING.get(), FISHING_TICKS, 0,
                false, true, true));
        if (clearAggro) clearNearbyMobTargets(player);
    }

    /** 清除附近已把该玩家设为目标生物的仇恨（获得摸鱼那一刻调用一次）。 */
    private static void clearNearbyMobTargets(Player player) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(MOB_CLEAR_RANGE), mob -> mob.getTarget() == player)) {
            mob.setTarget(null);
        }
    }

    private static void removeFishing(Player player) {
        com.merlinkitsune.starengine.event.ModEffectRemoval.remove(player, ModEffects.DEEPSEEK_FISHING.get());
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        long now = level.getGameTime();
        int roll = player.getRandom().nextInt(20) + 1;
        long balance = StarEngineEconomy.getBalance(player);
        int actual = (int) Math.min(roll, balance);
        if (actual <= 0) {
            sendSignActionBarColored(player, ChatFormatting.RED, "msg.astral_dice.deepseek_no_coins");
            return InteractionResultHolder.fail(stack);
        }
        if (!StarEngineEconomy.withdraw(player, actual)) {
            sendSignActionBarColored(player, ChatFormatting.RED, "msg.astral_dice.deepseek_no_coins");
            return InteractionResultHolder.fail(stack);
        }
        DiceCombatModifiers.setAttackPowerBonus(player, "deepseek_buff_attack", actual);
        DiceCombatModifiers.setDefenseArmorBonus(player, "deepseek_buff_armor", actual);
        ModAttachments.setDeepseekBuffEnd(player, now + BUFF_TICKS);
        for (int i = 0; i < CARD_COUNT; i++) {
            RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.ALL);
        }
        sendSignActionBar(player, "msg.astral_dice.deepseek_done");
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * 攻击/防御加成到期清理。两线 {@code PlayerTickEvents#onPlayerTickPre} 每 tick 调用；
     * Forge 每 tick 派发 START/END 两次 ⇒ 本方法幂等（结束刻归零后第二次调用直接返回）。
     */
    public static void tickBuff(Player player) {
        if (player.level().isClientSide()) return;
        long end = ModAttachments.getDeepseekBuffEnd(player);
        if (end <= 0L) return;
        if (player.level().getGameTime() >= end) {
            ModAttachments.setDeepseekBuffEnd(player, 0L);
            DiceCombatModifiers.setAttackPowerBonus(player, "deepseek_buff_attack", 0);
            DiceCombatModifiers.setDefenseArmorBonus(player, "deepseek_buff_armor", 0);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            // 卸下 = 立即结束：摸鱼、攻/防加成、buff 结束刻、位置快照、移动累计与 tick 戳全部归零
            removeFishing(player);
            DiceCombatModifiers.setAttackPowerBonus(player, "deepseek_buff_attack", 0);
            DiceCombatModifiers.setDefenseArmorBonus(player, "deepseek_buff_armor", 0);
            ModAttachments.setDeepseekBuffEnd(player, 0L);
            ModAttachments.setDeepseekLastPos(player, List.of());
            ModAttachments.setDeepseekMoveTicks(player, 0);
            ModAttachments.setDeepseekLastTick(player, 0L);
            ModAttachments.setDeepseekIdleSince(player, 0L);
        }
        super.onUnequip(slotContext, newStack, stack);
    }
}
