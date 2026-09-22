package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.DiceCombatEvents;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.HannaDollCompleteEffect;
import com.merlinkitsune.astral_dice.effect.HannaDollCraftEffect;
import com.merlinkitsune.astral_dice.effect.HannaFloatEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * 人偶师立牌(hanna,稀有)。
 *
 * <h3>被动「幻想千金」</h3>
 * <ul>
 *   <li><b>战斗骰点 = 6</b> ⇒ 佩戴者获得 {@value #COIN_ON_SIX} 星币;</li>
 *   <li><b>路过友方玩家</b>(水平距离 ≤ {@value #PASS_RADIUS} 格,用户 2026-09-21 裁决;
 *       <b>脚底高差 ≤ {@value #PASS_VERTICAL_WINDOW} 格</b>,用户 2026-09-22 裁决 —— 「路过」只认同一层,
 *       上下台阶算,跨 2 格以上的高台/坑洞不算)⇒ 该玩家获得
 *       {@value #COIN_ON_PASS} 星币,佩戴者获得 1 层「人偶制作」;
 *       <b>若佩戴者处于「魔女漂浮」</b> ⇒ 该玩家改为获得 {@value #COIN_ON_PASS_FLOAT} 星币;</li>
 *   <li>「人偶制作」达 {@value #MAX_CRAFT} 层 ⇒ **归零**并转为「人偶完成」(用户裁决);
 *       此后路过友方玩家使该玩家**额外**获得 迅捷 II (1:00) 与 {@value #COIN_ON_COMPLETE} 星币;</li>
 *   <li>本被动**整体**每 {@value #PASSIVE_INTERVAL_TICKS} tick(= 1:00)仅触发 1 次。</li>
 * </ul>
 *
 * <h3>被动「挚友祝福」</h3>
 * 路过**装备「怪力侦探」立牌**的玩家 ⇒ 使该玩家获得 力量 II (1:00) + 抗性提升 (1:00) + 1 层「推理时间」;
 * 本被动每 {@value #PASSIVE_INTERVAL_TICKS} tick(= 1:00)仅触发 1 次(**与「幻想千金」各自独立计时**)。
 *
 * <h3>主动「漂浮魔法」</h3>
 * 使自身获得「魔女漂浮」({@value #FLOAT_DURATION_SECONDS}:00)。效果期间四条语义分别落在:
 * 移动速度 +20%({@link HannaFloatEffect} 的属性修饰器)、掉落伤害 -100%(本类取消 {@code DamageTypes.FALL})、
 * 任何近战攻击被闪避(走 {@link DiceCombatEvents#applyDodgeCancel} 的**最前置取消**,照枪匠破绽闪避同款入口)、
 * 无法使用末影珍珠(取消 {@code RightClickItem})。
 *
 * <p><b>层数真值在附件 {@code ModAttachments#HANNA_DOLL_CRAFT_LAYERS}</b>,效果实例只是 HUD 镜像
 * (图标 = 立牌同图 + 层数,照「狐光」「推理时间」同款写法)。**不跨死亡保留** —— 技能原文未声明
 * 死亡保留,故按未声明即不保留的既定口径处理(与「剑气」「弱点识破」一致)。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class HannaSignItem extends BaseSignItem {

    /** 立牌注册 id */
    public static final String SIGN_ID = AstralDiceMod.MODID + ":hanna_sign";
    /** 「挚友祝福」的判定目标立牌 = 怪力侦探(sherry),按注册 id 字符串匹配 */
    public static final String SHERRY_SIGN_ID = AstralDiceMod.MODID + ":sherry_sign";

    /** 「人偶制作」层数上限(= 满层转换阈值) */
    public static final int MAX_CRAFT = 7;
    /** 「路过友方玩家」的判定半径(格) —— 用户 2026-09-21 裁决 */
    public static final double PASS_RADIUS = 3.0D;
    /** 「路过」判定的**竖直窗口**(格,以双方**脚底**之差计) —— 用户 2026-09-22 裁决。
     *  候选集用的 `inflate(PASS_RADIUS)` 是三轴同时膨胀的 AABB,竖直容差实为「脚底 −3 … +4.8」
     *  ——比水平还宽,站上 4 格高台也会「路过」地面玩家(实机取证:+4 格被接受、+6 格被拒绝)。
     *  技能语义是「**路过**」,只允许同一层(含跳跃与上下台阶),故在粗筛后再做这层精筛。 */
    public static final double PASS_VERTICAL_WINDOW = 2.0D;
    /** 两个被动各自的触发间隔 = 1:00 */
    public static final int PASSIVE_INTERVAL_TICKS = 1200;
    /** 「人偶完成」附加给友方的迅捷 II 时长 = 1:00 */
    public static final int COMPLETE_BUFF_TICKS = 1200;
    /** 「挚友祝福」给目标的力量 / 抗性提升时长 = 1:00 */
    public static final int BLESSING_BUFF_TICKS = 1200;
    /** 主动「魔女漂浮」时长(秒) —— 只用于 javadoc 引用 */
    public static final int FLOAT_DURATION_SECONDS = 60;

    /** 骰点 = 6 时给佩戴者的星币数 */
    public static final int COIN_ON_SIX = 1;
    /** 路过友方玩家时给该玩家的星币数 */
    public static final int COIN_ON_PASS = 1;
    /** 自身处于「魔女漂浮」时,路过友方玩家给该玩家的星币数(替代 {@link #COIN_ON_PASS}) */
    public static final int COIN_ON_PASS_FLOAT = 3;
    /** 已「人偶完成」时,路过友方玩家**额外**给该玩家的星币数 */
    public static final int COIN_ON_COMPLETE = 3;

    public HannaSignItem(Properties properties) {
        super(properties);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  「人偶制作」层数 / 「人偶完成」状态(真值 = 附件;效果只是镜像)
    // ══════════════════════════════════════════════════════════════════════════

    /** 当前「人偶制作」层数(0..{@link #MAX_CRAFT}) */
    public static int getCraftLayers(Player holder) {
        if (holder == null) return 0;
        return ModAttachments.getHannaDollCraftLayers(holder);
    }

    /**
     * 直接写入「人偶制作」层数。达到 {@link #MAX_CRAFT} 时按用户裁决**归零并转为「人偶完成」**
     * (层数真值写 0、镜像效果移除,同时挂上「人偶完成」状态)。
     */
    public static void setCraftLayers(Player holder, int value) {
        if (holder == null || holder.level().isClientSide()) return;
        if (value >= MAX_CRAFT) {
            ModAttachments.setHannaDollCraftLayers(holder, 0);
            HannaDollCraftEffect.mirror(holder, 0);
            setComplete(holder, true);
            return;
        }
        int clamped = Math.max(0, Math.min(MAX_CRAFT - 1, value));
        ModAttachments.setHannaDollCraftLayers(holder, clamped);
        HannaDollCraftEffect.mirror(holder, clamped);
    }

    /** 层数增减(自动处理满层转换) */
    public static void addCraftLayers(Player holder, int amount) {
        if (holder == null || amount == 0) return;
        setCraftLayers(holder, getCraftLayers(holder) + amount);
    }

    /** 是否已进入「人偶完成」状态 */
    public static boolean isComplete(Player holder) {
        return holder != null && ModAttachments.getHannaDollComplete(holder);
    }

    /** 写入「人偶完成」状态(同时增删其显示效果) */
    public static void setComplete(Player holder, boolean value) {
        if (holder == null || holder.level().isClientSide()) return;
        ModAttachments.setHannaDollComplete(holder, value);
        if (value) {
            if (!holder.hasEffect(ModEffects.HANNA_DOLL_COMPLETE)) {
                holder.addEffect(new MobEffectInstance(ModEffects.HANNA_DOLL_COMPLETE,
                        HannaDollCompleteEffect.DURATION_TICKS, 0, false, false, true));
            }
        } else {
            ModEffectRemoval.remove(holder, ModEffects.HANNA_DOLL_COMPLETE);
        }
    }

    /** 玩家是否佩戴人偶师立牌 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.HANNA_SIGN.get())).isPresent();
    }

    @Override
    protected void onCurioTick(top.theillusivec4.curios.api.SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 层数镜像每 tick 校正(死亡重生后附件与效果都丢了 ⇒ 这里只保证"佩戴中"的显示正确)
        int layers = getCraftLayers(player);
        if (HannaDollCraftEffect.getStacks(player) != layers) {
            HannaDollCraftEffect.mirror(player, layers);
        }
        if (!isComplete(player) && HannaDollCompleteEffect.has(player)) {
            ModEffectRemoval.remove(player, ModEffects.HANNA_DOLL_COMPLETE);
        }
    }

    /** 卸下立牌:清「人偶制作」层数、「人偶完成」状态与两者的显示效果 */
    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        ModAttachments.setHannaDollCraftLayers(player, 0);
        ModAttachments.setHannaDollComplete(player, false);
        HannaDollCraftEffect.clear(player);
        ModEffectRemoval.remove(player, ModEffects.HANNA_DOLL_COMPLETE);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动「漂浮魔法」
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected InteractionResult handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // 有限时长效果经 EffectTimerGuard 记账 ⇒ HUD 计时器严格 20 t/s(照 ExpressDeliveryCardItem 同款)
        EffectTimerGuard.apply(player, new MobEffectInstance(ModEffects.HANNA_FLOAT,
                HannaFloatEffect.DURATION_TICKS, 0, false, true, true));
        sendSignActionBar(player, "msg.astral_dice.hanna_float");
        return InteractionResult.SUCCESS;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「幻想千金」:骰点 = 6
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 骰点结果处理(由 {@code combat/DiceCombatEvents} 在攻击方骰点定稿后调用,与风水师立牌同挂点):
     * 骰点 = 6 ⇒ 佩戴者获得 {@value #COIN_ON_SIX} 星币。
     *
     * <p>「每 1:00 仅触发 1 次」由 {@code hanna_fantasy_cooldown_end} 附件保证 ——
     * 同一次挥击命中多个目标会多次进入调用点,第二次起被冷却挡下(天然满足"一次结算一次判定")。
     */
    public static void onDiceRollResult(Player player, int dice) {
        if (player == null || player.level().isClientSide()) return;
        if (dice != 6) return;
        if (!isEquipped(player)) return;
        if (!tryUseFantasyCooldown(player)) return;
        giveCoins(player, COIN_ON_SIX);
        sendSignActionBar(player, "msg.astral_dice.hanna_dice_coin", COIN_ON_SIX);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「幻想千金」+「挚友祝福」:路过判定
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 路过判定(由 {@code event/PlayerTickEvents} 的玩家 tick 每 tick 驱动)。
     *
     * <p>两条被动**各自独立**的 1:00 冷却:冷却未到即整体早退(因此每 tick 的扫描成本可忽略);
     * 冷却已到时扫描 {@value #PASS_RADIUS} 格内的玩家(**水平距离口径**,且脚底高差须 ≤
     * {@value #PASS_VERTICAL_WINDOW} 格),命中则结算并立刻写入该被动的冷却。
     *
     * <p><b>「每 1:00 仅触发 1 次」的实现口径</b>:一次触发最多结算**一名**玩家(取距离最近者),
     * 随后该被动进入 1:00 冷却 —— 避免 3 格内站着一队人时被一次性多发。
     */
    public static void tickPassing(Player wearer) {
        if (wearer == null || wearer.level().isClientSide()) return;
        if (!isEquipped(wearer)) return;
        long now = wearer.level().getGameTime();
        // inflate(PASS_RADIUS) 只当**粗筛**(它三轴同时膨胀、竖直容差达「脚底 −3 … +4.8」,
        // 不能直接当命中判据);随后由 isWithinPassWindow 做「水平 ≤ 半径 + 脚底高差 ≤ 窗口」的精筛。
        List<Player> nearby = wearer.level().getEntitiesOfClass(Player.class,
                wearer.getBoundingBox().inflate(PASS_RADIUS),
                p -> p != wearer && p.isAlive() && isWithinPassWindow(wearer, p));

        // ① 挚友祝福:路过装备「怪力侦探」立牌的玩家
        if (now >= ModAttachments.getHannaBlessingCooldownEnd(wearer)) {
            Player sherryWearer = nearest(nearby, wearer, p -> wearsSign(p, SHERRY_SIGN_ID));
            if (sherryWearer != null) {
                EffectTimerGuard.apply(sherryWearer, new MobEffectInstance(MobEffects.STRENGTH,
                        BLESSING_BUFF_TICKS, 1, false, true));
                EffectTimerGuard.apply(sherryWearer, new MobEffectInstance(MobEffects.RESISTANCE,
                        BLESSING_BUFF_TICKS, 0, false, true));
                SherrySignItem.addLayers(sherryWearer, 1);
                ModAttachments.setHannaBlessingCooldownEnd(wearer, now + PASSIVE_INTERVAL_TICKS);
                sendSignActionBar(wearer, "msg.astral_dice.hanna_blessing");
            }
        }

        // ② 幻想千金:路过友方玩家
        if (now >= ModAttachments.getHannaFantasyCooldownEnd(wearer)) {
            Player friend = nearest(nearby, wearer, p -> isFriendly(wearer, p));
            if (friend != null) {
                boolean floating = HannaFloatEffect.has(wearer);
                giveCoins(friend, floating ? COIN_ON_PASS_FLOAT : COIN_ON_PASS);
                addCraftLayers(wearer, 1);
                if (isComplete(wearer)) {
                    // 已完成:额外 迅捷 II (1:00) + 3 星币
                    EffectTimerGuard.apply(friend, new MobEffectInstance(MobEffects.SPEED,
                            COMPLETE_BUFF_TICKS, 1, false, true));
                    giveCoins(friend, COIN_ON_COMPLETE);
                }
                ModAttachments.setHannaFantasyCooldownEnd(wearer, now + PASSIVE_INTERVAL_TICKS);
                sendSignActionBar(wearer, "msg.astral_dice.hanna_pass");
            }
        }
    }

    /** 冷却是否可用(不可用 = 本被动仍在 1:00 间隔内) */
    private static boolean tryUseFantasyCooldown(Player player) {
        long now = player.level().getGameTime();
        if (now < ModAttachments.getHannaFantasyCooldownEnd(player)) return false;
        ModAttachments.setHannaFantasyCooldownEnd(player, now + PASSIVE_INTERVAL_TICKS);
        return true;
    }

    /**
     * 「路过窗口」精筛:**水平距离 ≤ {@link #PASS_RADIUS}** 且 **脚底高差 ≤ {@link #PASS_VERTICAL_WINDOW}**。
     *
     * <p>水平用两玩家中心点的水平距离(不再是 AABB 的「半径 + 双方半宽」)⇒ 与文案「半径 N 格」一致;
     * 竖直按**脚底**差:跳跃约 1.25 格、台阶 1 格都在窗口内,2 格以上的高台/坑洞不再算「路过」。
     */
    private static boolean isWithinPassWindow(Player self, Player other) {
        if (horizontalDistanceSqr(self, other) > PASS_RADIUS * PASS_RADIUS) return false;
        return Math.abs(self.getY() - other.getY()) <= PASS_VERTICAL_WINDOW;
    }

    /** 两点之间的**水平**平方距离(忽略 Y) —— 与「路过窗口」同口径,「取最近」也用它。 */
    private static double horizontalDistanceSqr(Player a, Player b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** 在候选里按「**水平**距离最近」取第一个满足条件的玩家(平局按 UUID 保证稳定,与门限同口径) */
    private static Player nearest(List<Player> candidates, Player self, java.util.function.Predicate<Player> filter) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : candidates) {
            if (!filter.test(p)) continue;
            double d = horizontalDistanceSqr(self, p);
            if (d < bestDist || (d == bestDist && best != null
                    && p.getUUID().compareTo(best.getUUID()) < 0)) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }

    /** 友方判定:对方与自己同队(或任一方无队伍) —— 与肉弹战车、奢华大餐、史莱姆立牌同一口径 */
    private static boolean isFriendly(Player self, Player other) {
        return self.getTeam() == null || other.getTeam() == null
                || self.getTeam() == other.getTeam();
    }

    /** 该玩家是否在饰品槽里装着指定注册 id 的立牌(按 id 字符串匹配,与 sherry 同款) */
    private static boolean wearsSign(Player player, String itemId) {
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return false;
        return curios.get().findFirstCurio(s -> {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
            return key != null && itemId.equals(key.toString());
        }).isPresent();
    }

    /** 走既有发币漏斗(物品栏放不下则落地,与枪匠/大侦探的星币发放同款) */
    private static void giveCoins(Player player, int count) {
        if (player == null || count <= 0) return;
        ItemStack coins = new ItemStack(ModItems.STAR_COIN.get(), count);
        if (!player.getInventory().add(coins)) {
            player.drop(coins, false);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动「魔女漂浮」的四条语义:掉落免疫 + 近战闪避 + 禁用末影珍珠
    //  (移动速度 +20% 在 HannaFloatEffect 的属性修饰器里)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 魔女漂浮期间:① 掉落伤害 -100%(直接取消);② 任何近战攻击被闪避。
     *
     * <p>闪避必须走 {@link DiceCombatEvents#applyDodgeCancel}({@code LivingIncomingDamageEvent}
     * 的**最前置取消**)而不是把伤害改成 0 —— 只有前者能让攻击方 {@code Mob#doHurtTarget} 拿到
     * {@code hurt() == false},从而不施加命中附加效果、也不产生红屏/震动/受伤音效
     * (与枪匠破绽闪避同一入口)。反击链中的伤害不参与闪避判定,避免 A↔B 互相闪避造成无限递归。
     *
     * <p>近战判据 = 伤害源的**直接实体是生物本人**({@code getDirectEntity()}),因此弹射物、
     * 法术、AOE 与环境伤害都不算「近战攻击」,照常命中。
     */
    @SubscribeEvent
    public static void onHannaFloatIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!HannaFloatEffect.has(player)) return;
        DamageSource source = event.getSource();
        if (source.is(DamageTypes.FALL)) {
            event.setCanceled(true);
            return;
        }
        if (DiceCombatEvents.isInCounterChain()) return;
        if (!(source.getDirectEntity() instanceof LivingEntity attacker) || attacker == player) return;
        DiceCombatEvents.applyDodgeCancel(event);
        sendSignActionBar(player, "msg.astral_dice.hanna_dodge");
    }

    /**
     * 魔女漂浮期间无法使用末影珍珠进行转移 —— 在**使用当刻**取消(取消后珍珠不消耗、不射出)。
     */
    @SubscribeEvent
    public static void onHannaFloatRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!HannaFloatEffect.has(player)) return;
        if (!event.getItemStack().is(Items.ENDER_PEARL)) return;
        event.setCanceled(true);
        sendSignActionBar(player, "msg.astral_dice.hanna_no_pearl");
    }
}
