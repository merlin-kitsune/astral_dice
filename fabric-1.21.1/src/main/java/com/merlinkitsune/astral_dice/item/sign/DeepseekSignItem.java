package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.compat.curios.SlotContext;
import com.merlinkitsune.astral_dice.component.SignAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import com.merlinkitsune.starengine.economy.StarEngineEconomy;
import com.merlinkitsune.starengine.event.ModEffectRemoval;

import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import com.merlinkitsune.astral_dice.platform.util.Effects;

/**
 * 大肥鱼立牌(deepseek,奇特,仅创造获得)。Fabric 1.20.1 移植版,语义与 forge-1.20.1 蓝本逐条等价。
 *
 * <p><b>被动「摸鱼中」</b>:仅按精确位置判定 —— 每 tick 与上一 tick 的 (x,y,z) 快照比对,
 * 连续静止 {@link #IDLE_TICKS}(200 tick = 10 秒)⇒ 获得 deepseek_fishing
 * (免疫伤害/负面效果、不被怪物索敌、半透明、HUD 指示器),并在获得那一刻清除附近已锁定该玩家的怪物仇恨;
 * 获得后累计移动 {@link #MOVE_LIMIT_TICKS}(100 tick = 5 秒)⇒ 解除(中途停下不清零累计;转头不算移动)。
 * 效果本身是有限时长且静止时逐 tick 滚动续期(安全网:漏 tick / 摘下骰子后自然到期)。
 *
 * <p><b>主动「大口干饭」</b>:掷 1..20 星币消耗并按当前余额夹取,按实际消耗等量获得攻击力/防御力
 * ({@link #BUFF_TICKS} = 2:00),随后随机获得 {@link #CARD_COUNT} 张牌;
 * 余额为 0 / 扣款失败 ⇒ 直接失败返回(不加成、不发牌、不进冷却)。
 *
 * <h3>平台差异(Fabric 1.20.1 / 逐条理由)</h3>
 * <ul>
 *   <li>① 附件:forge 的 ModAttachments.getDeepseek* / setDeepseek*(5 个键)⇒ {@link SignAttachments}
 *       同名方法(键名/codec/默认值/inMemory 标记逐字一致);</li>
 *   <li>② <b>攻击力加成通道</b>:forge 用 DiceCombatModifiers.setAttackPowerBonus(player,key,int),
 *       本线 DiceCombatModifiers **没有**该方法(全仓 0 命中)⇒ 改走本线立牌通用写法
 *       {@link SignAttributeUtil#apply}(ATTACK_DAMAGE 属性 + 字符串键 + ADD_VALUE)。效果等价:
 *       本线 DiceCombatModifiers.attackPowerOf 的起点就是 ATTACK_DAMAGE 属性值
 *       (enchantedAttackDamage,见该文件 L760-763)⇒ 属性修饰器自然进入攻击力口径。
 *       防御力仍走 setDefenseArmorBonus(本线存在,1 防御力 = 1÷护甲系数 护甲值);</li>
 *   <li>③ 施效通道:forge 的 event.PlayerEffectProtection#apply 本线不存在 ⇒
 *       {@link EffectTimerGuard#apply}(本线立牌共用);移除走 starengine.event.ModEffectRemoval;</li>
 *   <li>④ Curios:top.theillusivec4.curios.api.SlotContext ⇒ 门面 {@link SlotContext};</li>
 *   <li>⑤ level.isClientSide 字段 ⇒ 本线方法 level.isClientSide();</li>
 *   <li>⑥ {@link #tickBuff} 的每 tick 调用点属 event/PlayerTickEvents(forge 蓝本
 *       PlayerTickEvents.java:50);本批在 Fabric 同名类里按相邻立牌行插入一行,见 B4-SIGNS-REPORT。</li>
 * </ul>
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
    /** 主动加成的修饰符键（攻击走 ATTACK_DAMAGE 属性，防御走 setDefenseArmorBonus）。 */
    private static final String BUFF_ATTACK_KEY = "deepseek_buff_attack";
    private static final String BUFF_ARMOR_KEY = "deepseek_buff_armor";

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
        long lastTick = SignAttachments.getDeepseekLastTick(player);
        if (lastTick > 0L && now - lastTick > 1L) {
            SignAttachments.setDeepseekLastPos(player, List.of());
            SignAttachments.setDeepseekMoveTicks(player, 0);
            SignAttachments.setDeepseekIdleSince(player, now);
        }
        SignAttachments.setDeepseekLastTick(player, now);
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        List<Double> last = SignAttachments.getDeepseekLastPos(player);
        boolean hasLast = last.size() >= 3;
        boolean moved = !hasLast
                || Double.compare(last.get(0), x) != 0
                || Double.compare(last.get(1), y) != 0
                || Double.compare(last.get(2), z) != 0;
        // 先读上一 tick 快照再立即更新：下一 tick 比较的永远是"上一 tick 的位置"
        SignAttachments.setDeepseekLastPos(player, List.of(x, y, z));
        if (!hasLast) {
            // 首 tick（刚装备 / 刚上线）：只建立快照，静止从此刻起算
            SignAttachments.setDeepseekIdleSince(player, now);
            SignAttachments.setDeepseekMoveTicks(player, 0);
            return;
        }
        if (moved) {
            // 移动 ⇒ 静止起点重置；只有摸鱼中才累计移动 tick（停下不清零累计）
            SignAttachments.setDeepseekIdleSince(player, now);
            if (player.hasEffect(Effects.holder(ModEffects.DEEPSEEK_FISHING.get()))) {
                int walked = SignAttachments.getDeepseekMoveTicks(player) + 1;
                if (walked >= MOVE_LIMIT_TICKS) {
                    removeFishing(player);
                    SignAttachments.setDeepseekMoveTicks(player, 0);
                } else {
                    SignAttachments.setDeepseekMoveTicks(player, walked);
                }
            }
            return;
        }
        if (player.hasEffect(Effects.holder(ModEffects.DEEPSEEK_FISHING.get()))) {
            // 摸鱼中且静止：滚动续期（安全网）；仇恨只在首次获得时清，避免反复打断
            applyFishing(player, false);
            return;
        }
        long since = SignAttachments.getDeepseekIdleSince(player);
        if (since <= 0L) {
            SignAttachments.setDeepseekIdleSince(player, now);
            return;
        }
        if (now - since >= IDLE_TICKS) {
            applyFishing(player, true);
            SignAttachments.setDeepseekMoveTicks(player, 0);
        }
    }

    /**
     * 施加/续期摸鱼效果。
     *
     * @param clearAggro 只在**首次获得**时传 true：清掉附近已把该玩家设为目标生物的仇恨；
     *                   逐 tick 续期传 false（已锁定玩家的怪物不该被反复清空仇恨）。
     */
    private static void applyFishing(Player player, boolean clearAggro) {
        EffectTimerGuard.apply(player, new MobEffectInstance(Effects.holder(ModEffects.DEEPSEEK_FISHING.get()) , FISHING_TICKS, 0,
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
        ModEffectRemoval.remove(player, Effects.holder(ModEffects.DEEPSEEK_FISHING.get()));
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) return InteractionResultHolder.sidedSuccess(stack, true);
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
        // 攻击力:本线无 DiceCombatModifiers.setAttackPowerBonus ⇒ 走 ATTACK_DAMAGE 属性修饰器(见类注释 ②)
        SignAttributeUtil.apply(player, Attributes.ATTACK_DAMAGE, BUFF_ATTACK_KEY, actual,
                AttributeModifier.Operation.ADD_VALUE);
        DiceCombatModifiers.setDefenseArmorBonus(player, BUFF_ARMOR_KEY, actual);
        SignAttachments.setDeepseekBuffEnd(player, now + BUFF_TICKS);
        for (int i = 0; i < CARD_COUNT; i++) {
            RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.ALL);
        }
        sendSignActionBar(player, "msg.astral_dice.deepseek_done");
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * 攻击/防御加成到期清理。本线 event/PlayerTickEvents 每玩家 tick 调用一次
     * (本线与 forge 不同:无 START/END 双派发,故只调用一次),本方法仍幂等。
     */
    public static void tickBuff(Player player) {
        if (player.level().isClientSide()) return;
        long end = SignAttachments.getDeepseekBuffEnd(player);
        if (end <= 0L) return;
        if (player.level().getGameTime() >= end) {
            SignAttachments.setDeepseekBuffEnd(player, 0L);
            SignAttributeUtil.remove(player, Attributes.ATTACK_DAMAGE, BUFF_ATTACK_KEY);
            DiceCombatModifiers.setDefenseArmorBonus(player, BUFF_ARMOR_KEY, 0);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player) {
            // 卸下 = 立即结束：摸鱼、攻/防加成、buff 结束刻、位置快照、移动累计与 tick 戳全部归零
            removeFishing(player);
            SignAttributeUtil.remove(player, Attributes.ATTACK_DAMAGE, BUFF_ATTACK_KEY);
            DiceCombatModifiers.setDefenseArmorBonus(player, BUFF_ARMOR_KEY, 0);
            SignAttachments.setDeepseekBuffEnd(player, 0L);
            SignAttachments.setDeepseekLastPos(player, List.of());
            SignAttachments.setDeepseekMoveTicks(player, 0);
            SignAttachments.setDeepseekLastTick(player, 0L);
            SignAttachments.setDeepseekIdleSince(player, 0L);
        }
        super.onUnequip(slotContext, newStack, stack);
    }

    // 2026-11-11 P1 补齐：与 P0 同口径的机器可读拒绝。
    @Override
    protected boolean activateForEntity(net.minecraft.world.entity.LivingEntity owner) {
        return com.merlinkitsune.astral_dice.item.sign.SignEntitySkills.deny("deepseek", owner, "needs_player_wallet");
    }
}

