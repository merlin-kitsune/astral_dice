package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.DragonRoarBreakEffect;
import com.merlinkitsune.astral_dice.effect.MamushiDragonEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.DragonCardUtil;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import com.merlinkitsune.starenginelib.event.EventTargetCollector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蛟龙立牌(mamushi,传奇 UNCOMMON)。
 *
 * <h2>被动「湖沼之王」</h2>
 * 佩戴者**使其他角色获得卡牌**时累计「觉醒」(0 起,封顶 {@link #AWAKEN_MAX}):同一发牌事件内按**受益人去重**,
 * 每名新受益人 +1 层,单次事件封顶 {@link #AWAKEN_GRANT_CAP_PER_EVENT} 层(裁决 1)。
 * 事件边界 = **同一游戏刻内由同一佩戴者连续发放的多次发牌**(规格 §2.1.2 冻结口径)⇒ 用服务端静态
 * 批次表 {@link #GIVE_BATCH} 表达;唯一挂点是发牌漏斗
 * {@code item/chip/VitaminPillChipItem#giveCard(giver, receiver, card)}。
 *
 * <h2>真龙形态(锁存态)</h2>
 * 判据 {@link #isDragonForm}:{@code 佩戴立牌 && 觉醒 >= }{@link #AWAKEN_MAX};效果
 * {@link ModEffects#MAMUSHI_DRAGON}(图标 = 立牌贴图)由 {@link #onCurioTick} 每 tick 续期/移除。
 * 收益:攻击力 {@code +5}(骰战攻击修饰器实时谓词)、主动技取消范围限制、撕咬转龙之咆哮。
 * 觉醒**不消耗、不回落**(D4),只有**卸下立牌**才归零;死亡由
 * {@code .copyOnDeath()} + {@code DeathPreservedBonuses} 第 3 槽位两层机制保留。
 *
 * <h2>主动「连锁反应」(非选择器类,D5)</h2>
 * 直接生效:自身获得 1 张专属战斗牌(真龙形态给龙之咆哮,否则给撕咬),随后向
 * {@link #ACTIVE_RANGE} 格内的友方队友(无队伍 ⇒ 同维度其他玩家;真龙形态 ⇒ 同维度全体,裁决 3)
 * 各发 1 张随机牌,目标原本没有本模组卡牌时(判据见 {@link #hasAnyCard})追加 1 张;
 * 距离升序取最近 {@link #ACTIVE_MAX_TARGETS} 人。释放当刻写**强制冷却**
 * {@link #ACTIVE_FORCED_COOLDOWN_TICKS}(1:00),不可被诡异骰子/充能/命运的指引/电流核心筹码绕过
 * (硬闸门见 {@link #forcedCooldownUntil} 与 {@code CurrentCoreChipItem#tryFinishCooldown})。
 *
 * <h2>专属战斗牌「撕咬 / 龙之咆哮」</h2>
 * 撕咬:费用 2 / 耐久 1 / 定值攻击 +3,装备且触发骰神赐福时每张 <b>+1 层觉醒</b>并**锁存**撕咬加成
 * (耐久 1 ⇒ 同一击稍后就被消耗,不锁存则加成当拍失效,见 {@link #onDiceBlessingTriggered});
 * 加成 = {@code min(当前觉醒, 4)} 实时取值(裁决 4),赐福结束即清。
 * 龙之咆哮:费用 3 / 耐久 5 / 定值攻击 +3,命中对受击者施加 缓慢 III 1:00 + 破防(护甲 −8 = 减 4 防御)
 * 1:00({@link #applyRoarDebuff},重复命中只刷新时长)。
 * 两张牌均为专属牌(不进随机池、无配方),只能由本立牌发放/转换获得。
 *
 * <p>常量与键名 = {@code docs/features/mamushi-sign-spec.md} §1(冻结,不得改名改值)。
 * 图标 = {@code images/蛟龙立牌.png}(实装路径 {@code textures/item/mamushi_sign.png});
 * 真龙形态图标 = 同一张图({@code textures/mob_effect/mamushi_dragon.png},逐字节相同)。
 */
public class MamushiSignItem extends BaseSignItem {

    // ══════════════════════════════════════════════════════════════════════════
    //  常量(规格 §1 冻结表;两线同名同值)
    // ══════════════════════════════════════════════════════════════════════════

    /** 真龙形态阈值(觉醒层数) */
    public static final int AWAKEN_MAX = 8;
    /** 单次发牌事件的觉醒封顶层数(按受益人去重后计数) */
    public static final int AWAKEN_GRANT_CAP_PER_EVENT = 3;
    /** 撕咬加成的攻击力上限(加成 = {@code min(当前觉醒, 本值)},实时) */
    public static final int BITE_BONUS_CAP = 4;
    /** 真龙形态的攻击力加成 */
    public static final int DRAGON_FORM_ATTACK_BONUS = 5;
    /** 主动「连锁反应」的连锁范围(格);真龙形态取消该限制(裁决 3) */
    public static final double ACTIVE_RANGE = 12.0;
    /** 主动「连锁反应」的目标人数安全上限 */
    public static final int ACTIVE_MAX_TARGETS = 32;
    /** 主动「连锁反应」的强制冷却(1:00,不可被任何减免绕过) */
    public static final int ACTIVE_FORCED_COOLDOWN_TICKS = 1200;

    /** 撕咬:费用(真值 = {@code CardRegistry} 的 {@code "bite"} 注册项,此处为规格冻结常量) */
    public static final int BITE_COST = 2;
    /** 撕咬:耐久 */
    public static final int BITE_USES = 1;
    /** 撕咬:定值攻击贡献 */
    public static final int BITE_ATTACK = 3;

    /** 龙之咆哮:费用(真值 = {@code CardRegistry} 的 {@code "dragon_roar"} 注册项) */
    public static final int ROAR_COST = 3;
    /** 龙之咆哮:耐久 */
    public static final int ROAR_USES = 5;
    /** 龙之咆哮:定值攻击贡献 */
    public static final int ROAR_ATTACK = 3;
    /** 龙之咆哮命中施加的减益时长(1:00) */
    public static final int ROAR_DEBUFF_TICKS = 1200;
    /** 龙之咆哮命中施加的缓慢等级(amplifier 2 = 缓慢 III) */
    public static final int ROAR_SLOW_AMPLIFIER = 2;
    /** 龙之咆哮的破防护甲变化量(−8 护甲 = 减 4 点防御;单一真值在 {@link DragonRoarBreakEffect#ARMOR_DELTA}) */
    public static final double ROAR_ARMOR_DELTA = DragonRoarBreakEffect.ARMOR_DELTA;

    /** 静态批次表的惰性清理窗口(tick):超过该时长的批次只可能是残留(每批次只在一个 gameTime 内有效) */
    private static final long BATCH_STALE_TICKS = 100L;

    /**
     * 「同一发牌事件」的服务端批次表:佩戴者 UUID → 本 tick 的发放批次。
     *
     * <p>只记"本 tick 已收到牌的受益人集合":同一 gameTime 内再次发放沿用同一批次(按受益人去重),
     * 进入下一 tick 立即开新批次(事件边界 = 同一游戏刻,规格 §2.1.2)。表项按
     * {@link #BATCH_STALE_TICKS} 惰性清理;卸下立牌时显式清掉本人的项(规格 §2.5)。
     */
    private static final Map<UUID, Batch> GIVE_BATCH = new ConcurrentHashMap<>();

    /** 一次发牌事件内已收到牌的受益人(单次封顶判定见 {@link #AWAKEN_GRANT_CAP_PER_EVENT}) */
    private static final class Batch {
        private final long tick;
        private final Set<UUID> receivers = ConcurrentHashMap.newKeySet();

        private Batch(long tick) {
            this.tick = tick;
        }
    }

    public MamushiSignItem(Properties properties) {
        super(properties);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  觉醒层数(附件真值)
    // ══════════════════════════════════════════════════════════════════════════

    /** 玩家是否佩戴蛟龙立牌(被动/真龙形态/撕咬加成的佩戴判定) */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.MAMUSHI_SIGN.get())).isPresent();
    }

    /** 当前觉醒层数(0 起,封顶 {@link #AWAKEN_MAX};缺省 0) */
    public static int getAwakening(Player player) {
        if (player == null) return 0;
        return ModAttachments.getMamushiAwakening(player);
    }

    /**
     * 直接写入觉醒层数(仅钳制非负;**不**钳制 {@link #AWAKEN_MAX}、**不**触发形态转换)。
     *
     * <p>**加层请走** {@link #addAwakening}(它施加 {@link #AWAKEN_MAX} 封顶并处理进入真龙形态的转换);
     * 本方法只服务"归零"(卸下立牌)与需要原样落库的旁路。
     */
    public static void setAwakening(Player player, int value) {
        if (player == null || player.level().isClientSide()) return;
        ModAttachments.setMamushiAwakening(player, value);
    }

    /** 真龙形态判据(锁存态):{@code 佩戴立牌 && 觉醒 >= }{@link #AWAKEN_MAX} */
    public static boolean isDragonForm(Player player) {
        return isEquipped(player) && getAwakening(player) >= AWAKEN_MAX;
    }

    /**
     * 被动「湖沼之王」的计数入口(由唯一发牌漏斗
     * {@code VitaminPillChipItem#giveCard(giver, receiver, card)} 的成功入包分支调用)。
     *
     * <p>计入条件(规格 §2.1):发牌者**佩戴着**蛟龙立牌、且受益人是**其他角色**(自己给自己不计);
     * 同一发牌事件内按受益人去重,单次封顶 {@link #AWAKEN_GRANT_CAP_PER_EVENT} 层;
     * 加层后立即判 {@code >= }{@link #AWAKEN_MAX} 触发 {@link #transformToDragon}。
     *
     * @param giver    发牌者(携带蛟龙立牌者)
     * @param receiver 实际收到牌的玩家
     */
    public static void onCardGivenToOther(Player giver, Player receiver) {
        if (giver == null || receiver == null) return;
        if (giver.level().isClientSide() || receiver.level().isClientSide()) return;
        if (giver == receiver) return;
        if (!isEquipped(giver)) return;
        long now = giver.level().getGameTime();
        cleanupBatches(now);
        Batch batch = GIVE_BATCH.compute(giver.getUUID(),
                (id, previous) -> (previous == null || previous.tick != now) ? new Batch(now) : previous);
        if (!batch.receivers.add(receiver.getUUID())) return;
        if (batch.receivers.size() > AWAKEN_GRANT_CAP_PER_EVENT) return;
        addAwakening(giver, 1);
    }

    /**
     * 加层并处理"首次进入真龙形态"的转换与提示(唯一的加层出口)。
     *
     * <p>层数**封顶** {@link #AWAKEN_MAX}(用户需求:撕咬的加层"受 8 层上限约束";无封顶会让
     * tooltip 显示成 {@code 9/8} / {@code 12/8})。真龙形态本身是锁存态,故封顶不影响形态判定,
     * 撕咬加成仍按 {@code min(觉醒, }{@link #BITE_BONUS_CAP}{@code )} 实时取值。
     */
    private static void addAwakening(Player player, int delta) {
        if (player == null || delta <= 0) return;
        boolean wasDragon = isDragonForm(player);
        setAwakening(player, Math.min(getAwakening(player) + delta, AWAKEN_MAX));
        if (!wasDragon && isDragonForm(player)) {
            sendSignActionBar(player, "msg.astral_dice.mamushi_dragon_form");
            transformToDragon(player);
        }
    }

    // 惰性清理过旧批次(表只服务"同一游戏刻"的事件边界,旧批次不清理就会按玩家 UUID 无限积累)
    private static void cleanupBatches(long now) {
        if (GIVE_BATCH.isEmpty()) return;
        GIVE_BATCH.entrySet().removeIf(entry -> now - entry.getValue().tick > BATCH_STALE_TICKS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  真龙形态:牌转换(§2.3,幂等)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 转换为真龙形态(幂等):把玩家的撕咬全部转成龙之咆哮(背包/副手 + 骰子卡牌栏,
     * 骰子侧费用溢出者丢弃、绑定保留 —— 实现在 {@link DragonCardUtil#convertBiteToRoar}),
     * 并立即补上 {@link ModEffects#MAMUSHI_DRAGON} 效果。
     *
     * <p>调用点:① 觉醒首次达到 {@link #AWAKEN_MAX} 的加层路径({@link #addAwakening},同时发进入形态提示);
     * ② 立牌 tick 的幂等保底(死亡重生/重登等旁路导致"形态成立却仍有撕咬")。本方法自身**不发**提示。
     */
    public static void transformToDragon(Player player) {
        if (player == null || player.level().isClientSide()) return;
        DragonCardUtil.convertBiteToRoar(player);
        MamushiDragonEffect.refresh(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  专属战斗牌:计数 / 持有判据 / 发放(纯工具在 item/card/DragonCardUtil)
    // ══════════════════════════════════════════════════════════════════════════

    /** 骰子卡牌栏中指定 typeId({@code bite} / {@code dragon_roar})的装配张数 */
    public static int countEquippedType(Player player, String typeId) {
        return DragonCardUtil.countEquippedType(player, typeId);
    }

    /** D1 判据:玩家主物品栏 + 双手 + 骰子卡牌栏内是否**没有任何**本模组卡牌 */
    public static boolean hasAnyCard(Player player) {
        return DragonCardUtil.hasAnyCard(player);
    }

    /**
     * 发放 {@code count} 张专属战斗牌给获得者({@code roar} 为真取龙之咆哮,否则撕咬):
     * {@code setOwner} 绑定获得者 + 走唯一发牌漏斗(发牌者 {@code giver} 透传 ⇒ 觉醒计数)。
     */
    public static void giveExclusiveCard(Player giver, Player receiver, boolean roar, int count) {
        DragonCardUtil.giveExclusiveCard(giver, receiver, roar, count);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  撕咬(§2.6:赐福触发计层 + 锁存加成)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 骰神赐福触发时调用(挂点 = {@code combat/DiceCombatEvents} 的赐福触发块):
     * 装备中的每张撕咬 **+1 层觉醒**(裁决 2;不受 {@link #AWAKEN_GRANT_CAP_PER_EVENT} 的被动封顶约束),
     * 并置位撕咬加成锁存 {@code mamushi_bite_bonus_active}(耐久 1 ⇒ 必须锁存,否则同一击内即失效)。
     *
     * <p>加层后 7 层 + 撕咬 ⇒ 8 层 ⇒ 加层路径自然触发真龙形态转换,无需特判。
     */
    public static void onDiceBlessingTriggered(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        int bites = countEquippedType(player, DragonCardUtil.TYPE_BITE);
        if (bites <= 0) return;
        setBiteBonusActive(player, true);
        addAwakening(player, bites);
    }

    /** 骰神赐福结束时调用(挂点 = {@code DiceCombatEvents} 的赐福结束块):清除撕咬加成锁存 */
    public static void onDiceBlessingEnded(Player player) {
        if (player == null || player.level().isClientSide()) return;
        setBiteBonusActive(player, false);
    }

    /** 撕咬加成是否处于锁存态(真值 = 附件 {@code mamushi_bite_bonus_active});由骰战攻击修饰器读取 */
    public static boolean getBiteBonusActive(Player player) {
        if (player == null) return false;
        return ModAttachments.isMamushiBiteBonusActive(player);
    }

    /** 写入撕咬加成锁存位 */
    public static void setBiteBonusActive(Player player, boolean value) {
        if (player == null || player.level().isClientSide()) return;
        ModAttachments.setMamushiBiteBonusActive(player, value);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  龙之咆哮(§2.7:命中施加 缓慢 III + 破防)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 对**被攻击的目标**施加减益(挂点 = {@code CombatEvents} 的伤害定稿处,条件 = 攻击方骰子卡牌栏
     * 装备了任意张龙之咆哮):缓慢 III {@link #ROAR_DEBUFF_TICKS} +
     * {@link ModEffects#DRAGON_ROAR_BREAK}(护甲 {@link #ROAR_ARMOR_DELTA} = 减 4 点防御)同时长。
     *
     * <p>六参构造 = 关粒子、留图标(仓库统一口径);D8:重复命中只**刷新时长**、不叠层(amplifier 恒 0,
     * 原版 {@code addEffect} 对同等级效果只续期)。缓慢是原版效果,按仓库既有写法经
     * {@link EffectTimerGuard#apply} 施加以纳入"严格 20 t/s"守卫;破防是本模组效果,
     * 由 {@code MobEffectEvent.Added} 自动登记计时。
     */
    public static void applyRoarDebuff(LivingEntity victim) {
        if (victim == null || victim.level().isClientSide()) return;
        EffectTimerGuard.apply(victim, new MobEffectInstance(MobEffects.SLOWNESS,
                ROAR_DEBUFF_TICKS, ROAR_SLOW_AMPLIFIER, false, false, true));
        victim.addEffect(new MobEffectInstance(ModEffects.DRAGON_ROAR_BREAK,
                ROAR_DEBUFF_TICKS, 0, false, false, true));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  强制冷却(§3.4:不可被任何减免绕过)
    // ══════════════════════════════════════════════════════════════════════════

    /** 强制冷却时长 = {@link #ACTIVE_FORCED_COOLDOWN_TICKS}(1:00;不走诡异骰子/充能减免) */
    @Override
    protected int forcedActiveCooldownTicks() {
        return ACTIVE_FORCED_COOLDOWN_TICKS;
    }

    /**
     * 强制冷却的绝对截止刻:释放当刻由 {@link #handleUse} 写
     * {@code now + }{@link #ACTIVE_FORCED_COOLDOWN_TICKS}。
     * {@code BaseSignItem.performSkill} 的冷却判定优先读它 ⇒ 窗口内**无条件**视为冷却中
     * (优先于电流核心筹码的"立即完成冷却")。
     */
    @Override
    protected long forcedCooldownUntil(Player player) {
        return getForcedCooldownUntil(player);
    }

    /** 强制冷却截止刻(绝对 gameTime;0 = 无)。供 {@code CurrentCoreChipItem#tryFinishCooldown} 拒绝判定 */
    public static long getForcedCooldownUntil(Player player) {
        if (player == null) return 0L;
        return ModAttachments.getMamushiForcedCooldownUntil(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动「连锁反应」(非选择器类:D5 ⇒ 不覆写 selectorActionId / startActiveLockOnUse)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 主动「连锁反应」(规格 §2.4,逐字实现):
     * <ol>
     *   <li>自身获得 1 张专属战斗牌:真龙形态 ⇒ 龙之咆哮,否则 ⇒ 撕咬;</li>
     *   <li>其他目标各发 1 张随机牌({@code giver} 透传 ⇒ 按受益人计觉醒);目标原本没有任何本模组
     *       卡牌时追加 1 张(D1);</li>
     *   <li>目标集合 = 友方队友(无队伍 ⇒ 同维度其他玩家)按距离升序取最近
     *       {@link #ACTIVE_MAX_TARGETS} 人;真龙形态取消 {@link #ACTIVE_RANGE} 范围限制(裁决 3);
     *       集合为空(单机无其他玩家)**不算失败**;</li>
     *   <li>释放当刻写强制冷却 ({@code now + }{@link #ACTIVE_FORCED_COOLDOWN_TICKS})。</li>
     * </ol>
     *
     * <p>形态判定在**发放开始前**做一次(D6):本次发放过程中因给牌涨到 8 层不改变本次的牌种与目标集。
     */
    @Override
    protected InteractionResult handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        // D6:形态与目标集在发放开始前判定一次
        boolean dragon = isDragonForm(player);
        // ① 自身 1 张专属战斗牌(自己给自己不计觉醒,由漏斗侧的 giver != receiver 判定)
        giveExclusiveCard(player, player, dragon, 1);
        // ② 其他目标:每人 1 张随机牌;原先没有任何本模组卡牌者追加 1 张
        //    目标集合见 collectActiveTargets(严格同维度 → 排除自己 → 有队伍只取同队 / 未组队不做队伍过滤
        //    → 距离 ≤ 12(真龙形态不限)→ 距离升序 → 32 人上限)
        for (Player target : collectActiveTargets(player, dragon)) {
            // D1:判据必须在**第一次发牌之前**求值 —— 否则第一张牌只要成功入包,
            // 后判的 !hasAnyCard(target) 就必然为 false,追加的那张只剩"第一张掉地"时才命中。
            boolean hadNoCards = !hasAnyCard(target);
            RandomCardHandler.giveCardTo(player, target, RandomCardHandler.CardCategory.ALL);
            if (hadNoCards) {
                RandomCardHandler.giveCardTo(player, target, RandomCardHandler.CardCategory.ALL);
            }
        }
        // ③ 强制冷却:释放当刻落笔(硬闸门,见 forcedCooldownUntil)
        ModAttachments.setMamushiForcedCooldownUntil(player,
                level.getGameTime() + ACTIVE_FORCED_COOLDOWN_TICKS);
        return InteractionResult.SUCCESS;
    }

    /**
     * 主动「连锁反应」的目标集合(两线同法;归一平台 API 后两线逐行等价):
     * <ol>
     *   <li>候选 = 佩戴者**所在维度**的在线玩家(**严格同维度**,绝不并入其它维度的队友);</li>
     *   <li>排除自己(已死亡者同样排除 —— 沿用原 {@code RandomCardHandler.collectTargets} 的既有口径);</li>
     *   <li>佩戴者**有队伍** ⇒ 只保留**同队**成员({@link EventTargetCollector#hasAnyTeam} 为真时取
     *       {@link EventTargetCollector#collectTeamPlayers});**未组队** ⇒ **不做**队伍过滤
     *       (该维度所有其他玩家都算"符合条件");未组队分支自己从本维度取候选,**不依赖**库侧
     *       "无队伍 ⇒ 全服在线玩家"的既有回退;</li>
     *   <li>距离过滤:非真龙形态 ⇒ 距离 ≤ {@link #ACTIVE_RANGE};真龙形态 ⇒ 不限制(同维度全体);</li>
     *   <li>按距离**升序**排序(平局按 UUID 稳定);</li>
     *   <li>最多 {@link #ACTIVE_MAX_TARGETS} 人(**截断发生在排序之后**)。</li>
     * </ol>
     *
     * <p>⚠️ **不得**改回 {@code GiveoutScope.aroundWithTeam} / {@code wholeDimensionWithTeam}:
     * 那两个作用域是「范围内全部玩家 ∪ 队友(不限距离)」的**并集** —— 与需求「12 格内所有**友方队友**」
     * 不符(队伍外的近处玩家会拿到牌、超出 12 格的队友也会拿到牌),真龙形态下还会并入其它维度的
     * FTB/OPAC 队友(见规格 §7 第 1 条)。
     */
    private static List<Player> collectActiveTargets(Player wearer, boolean dragon) {
        List<Player> candidates = new ArrayList<>();
        if (EventTargetCollector.hasAnyTeam(wearer)) {
            // ③ 有队伍 ⇒ 只取同队成员(库侧可能返回其它维度的 FTB/OPAC 队友 ⇒ 交给下方同维度过滤)
            candidates.addAll(EventTargetCollector.collectTeamPlayers(wearer));
        } else if (wearer.level() instanceof ServerLevel serverLevel) {
            // ③ 未组队 ⇒ 不做队伍过滤:本维度在线玩家(自己稍后排除)
            candidates.addAll(serverLevel.players());
        }
        double rangeSqr = ACTIVE_RANGE * ACTIVE_RANGE;
        List<Player> sorted = candidates.stream()
                .distinct()
                .filter(p -> p != wearer && p.isAlive())                    // ② 排除自己
                .filter(p -> p.level() == wearer.level())                   // ① 严格同维度
                .filter(p -> dragon || p.distanceToSqr(wearer) <= rangeSqr) // ④ 距离过滤(真龙形态不限)
                .sorted(Comparator.comparingDouble((Player p) -> p.distanceToSqr(wearer))  // ⑤ 距离升序
                        .thenComparing((Player p) -> p.getUUID().toString()))              //   平局按 UUID
                .toList();
        return sorted.size() > ACTIVE_MAX_TARGETS                                  // ⑥ 截断(排序之后)
                ? new ArrayList<>(sorted.subList(0, ACTIVE_MAX_TARGETS))
                : sorted;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  立牌 tick(形态效果维护 + 锁存兜底 + 批次清理)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 立牌 tick(服务端;仅佩戴期间):
     * <ol>
     *   <li>真龙形态成立 ⇒ {@link MamushiDragonEffect#refresh}(每 tick 续期);不成立 ⇒ {@code remove}
     *       (条件消失立刻摘下图标);</li>
     *   <li>形态成立时每秒做一次幂等转换保底(死亡重生/重登等旁路可能留下撕咬);</li>
     *   <li>兜底清锁存:已无骰神赐福却仍锁存撕咬加成 ⇒ 清(赐福结束钩子未覆盖的旁路,如死亡/重登);</li>
     *   <li>惰性清理过旧的发牌批次。</li>
     * </ol>
     */
    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        if (isDragonForm(player)) {
            MamushiDragonEffect.refresh(player);
            // 幂等保底:形态成立却仍有撕咬(旁路)⇒ 转换;每 20 tick 检查一次即可(转换本身幂等且廉价)
            if (now % 20L == 0L) transformToDragon(player);
        } else {
            MamushiDragonEffect.remove(player);
        }
        if (!player.hasEffect(ModEffects.DICE_BLESSING) && getBiteBonusActive(player)) {
            setBiteBonusActive(player, false);
        }
        cleanupBatches(now);
    }

    /**
     * 卸下立牌(仅"玩家有意卸除"时到达,见 {@code BaseSignItem#onUnequip}):
     * 觉醒归零(D4:只有卸下立牌才归零)、清撕咬加成锁存、移除真龙形态效果、清本人的发牌批次。
     *
     * <p>**有意不清** {@code sign_active_cooldown_end} 与 {@code mamushi_forced_cooldown_until}:
     * 冷却为玩家级,既有口径是"不随立牌装卸重置"(规格 §2.5)。
     */
    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        setAwakening(player, 0);
        setBiteBonusActive(player, false);
        MamushiDragonEffect.remove(player);
        GIVE_BATCH.remove(player.getUUID());
    }
}
