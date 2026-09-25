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
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 蛟龙立牌(mamushi,传奇 {@code ASTRAL_DICE_LEGENDARY})。
 *
 * <p>实现基准 = {@code docs/features/mamushi-sign-spec.md}(2026-09-27 冻结规格 + 用户 4 项裁决)。
 * 与 {@code neoforge-1.21.1} 侧**功能对等**,差异只在平台写法(见文末「1.20.1 平台适配」)。
 *
 * <h2>被动「湖沼之王」——觉醒层数(规格 §2.1)</h2>
 * <ul>
 *   <li><b>触发源</b>:佩戴者**使其他角色获得卡牌**时。唯一漏斗 =
 *       {@code item/chip/VitaminPillChipItem#giveCard(Player giver, Player receiver, ItemStack)}
 *       (2026-09-27 批次新增的带发牌者重载),成功入包那一支调用
 *       {@link #onCardGivenToOther(Player, Player)};</li>
 *   <li><b>计数</b>:同一**游戏刻**内由同一佩戴者连续发放的多次 giveCard 视为**一次事件**
 *       (冻结口径),事件内按**受益人去重**,每名新受益人 +1 层,单次封顶
 *       {@link #AWAKEN_GRANT_CAP_PER_EVENT}(3 层);自己给自己不计({@code giver == receiver} 直接返回);</li>
 *   <li><b>实现</b>:服务端静态小表 {@link #BATCHES}(UUID → {@link Batch}),按 {@code gameTime}
 *       判断是否开新批次;顺带惰性清理过旧批次;</li>
 *   <li><b>转换</b>:任意加层后觉醒 ≥ {@link #AWAKEN_MAX}(8) 且此前未处于真龙形态 ⇒ 立即
 *       {@link #transformToDragon(Player)}(§2.3);</li>
 *   <li><b>死亡不重置、卸牌重置</b>(裁决 D4):{@code mamushi_awakening} 同时进
 *       {@code component/AstralData#onPlayerClone} 死亡白名单与本线 {@code component/DeathPreservedBonuses}
 *       第 3 槽位;{@link #clearSignData} 归零。</li>
 * </ul>
 *
 * <h2>真龙形态(锁存态,规格 §2.2)</h2>
 * 判据 {@link #isDragonForm(Player)} = 佩戴立牌 **且** 觉醒 ≥ 8。收益:①攻击力
 * {@link #DRAGON_FORM_ATTACK_BONUS}(+5,由 {@code combat/DiceCombatModifiers} 的实时谓词提供);
 * ②主动技能**取消范围限制**(同维度全体,仍 32 人上限 + 距离升序,裁决 3);
 * ③牌转换(背包/副手 + 骰子卡牌栏的撕咬 → 龙之咆哮,放不下的丢弃,绑定保留);
 * ④此后主动技给自己发的是龙之咆哮。可见载体 = {@link ModEffects#MAMUSHI_DRAGON}(常驻效果,图标 = 立牌贴图)。
 *
 * <h2>主动「连锁反应」(规格 §2.4)</h2>
 * <b>非选择器类</b>:只覆写 {@link #handleUse}(首行客户端早退),**不覆写**
 * {@code selectorActionId} / {@code startActiveLockOnUse}(裁决 D5:不起锁定态)。
 * <ol>
 *   <li>发放前判定一次真龙形态(裁决 D6);</li>
 *   <li>先给自己 1 张({@code dragon ? 龙之咆哮 : 撕咬}),走专属发放形状
 *       ({@code ExclusiveCardUtil.setOwner} + 发牌漏斗,giver 透传);</li>
 *   <li>再对收集到的目标各发 1 张随机牌;目标**当前没有任何本模组卡牌**时额外再发 1 张(裁决 D1);</li>
 *   <li>无任何合格目标仍算成功(自身那张照发、照常进冷却);</li>
 *   <li>强制冷却 {@link #ACTIVE_FORCED_COOLDOWN_TICKS}(1:00),**不可被任何减免绕过**:
 *       {@code BaseSignItem#forcedActiveCooldownTicks()} + {@code #forcedCooldownUntil(Player)}
 *       两个钩子覆写,并由 {@link com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem}
 *       拒绝电流核心的"立即完成冷却"。</li>
 * </ol>
 *
 * <h2>撕咬 / 龙之咆哮(规格 §2.6 / §2.7)</h2>
 * <ul>
 *   <li><b>撕咬</b>:费用 2 / 耐久 1 / 攻击贡献 +3。装备中且**触发骰神赐福**时,每张 +1 层觉醒
 *       (裁决 2:不受 3 层封顶约束),并置「撕咬加成锁存」
 *       ({@link ModAttachments#MAMUSHI_BITE_BONUS_ACTIVE});加成 = {@code min(觉醒, 4)},
 *       **实时**读取(裁决 4),赐福结束由 {@link #onDiceBlessingEnded(Player)} 清锁存;</li>
 *   <li><b>龙之咆哮</b>:费用 3 / 耐久 5 / 攻击贡献 +3;命中对**受击者**施加
 *       {@code 缓慢 III 1:00} + {@link ModEffects#DRAGON_ROAR_BREAK}(减 4 点防御,1:00),
 *       重复命中只刷新时长(裁决 D8)。施加入口 {@link #applyRoarDebuff(LivingEntity)},
 *       由 {@code combat/DiceCombatEvents} 在伤害定稿附近按 `攻击方装备了龙之咆哮` 调用。</li>
 * </ul>
 *
 * <h2>卸下(规格 §2.5)</h2>
 * {@link #clearSignData}:觉醒归零、撕咬锁存清除、移除 {@code mamushi_dragon} 效果、清静态批次表;
 * **不清** {@code sign_active_cooldown_end} / {@code mamushi_forced_cooldown_until}
 * (冷却为玩家级,既有口径不随装卸重置)。
 *
 * <h2>1.20.1 平台适配(相对 1.21.1 的镜像改写)</h2>
 * <ul>
 *   <li>玩家数据走 {@link ModAttachments}({@code AttachedDataKey} + AstralData Capability),
 *       无数据组件;物品侧数据走 {@code ModDataComponents};</li>
 *   <li>效果常量 {@code ModEffects.X} 是 {@code RegistryObject<MobEffect>} ⇒ 一律 {@code .get()};</li>
 *   <li>六参 {@code MobEffectInstance} 施加(1.20.1 的五参等价于 {@code showIcon = visible},
 *       会连图标一起隐藏 —— 踩过的坑);</li>
 *   <li>骰子装配栏的读写走 {@code ModDataComponents.WEAPON_ENHANCEMENT}
 *       ({@code getOrDefault}/{@code set}),转换逻辑集中在 {@link DragonCardUtil};</li>
 *   <li>{@code RandomCardHandler.giveCardTo(Player giver, Player receiver, CardCategory)} 与
 *       {@code GiveoutScope.aroundWithTeam(double, int)} / {@code wholeDimensionWithTeam(int)}
 *       是本批次新增的冻结接口(规格 §3.1 / §3.3),与本类同批落地;
 *       ⚠️ 但本类的目标收集**不使用**后两个作用域(它们是「范围内全部玩家 ∪ 队友(不限距离)」的并集),
 *       见 {@link #collectActiveTargets} —— 工厂本体仍保留为通用 API,勿删。</li>
 * </ul>
 */
public class MamushiSignItem extends BaseSignItem {

    // ══════════════════════════════════════════════════════════════════════════
    //  常量(规格 §1 冻结:两线同名同值)
    // ══════════════════════════════════════════════════════════════════════════

    /** 立牌的物品注册 id(调试读数用) */
    public static final String SIGN_ID = "astral_dice:mamushi_sign";

    /** 真龙形态阈值:觉醒 ≥ 本值且佩戴立牌 ⇒ 真龙形态 */
    public static final int AWAKEN_MAX = 8;
    /** 单次发牌事件(同一 tick、同一佩戴者)的封顶层数(裁决 1) */
    public static final int AWAKEN_GRANT_CAP_PER_EVENT = 3;
    /** 撕咬攻击加成上限:加成 = min(当前觉醒, 本值)(裁决 2 / 4) */
    public static final int BITE_BONUS_CAP = 4;
    /** 真龙形态攻击力加成 */
    public static final int DRAGON_FORM_ATTACK_BONUS = 5;
    /** 连锁反应范围(格);真龙形态取消该限制(改用同维度全体) */
    public static final double ACTIVE_RANGE = 12.0;
    /** 安全上限(人数) */
    public static final int ACTIVE_MAX_TARGETS = 32;
    /** 强制冷却 1:00(不可被任何减免绕过) */
    public static final int ACTIVE_FORCED_COOLDOWN_TICKS = 1200;

    /** 撕咬:费用 */
    public static final int BITE_COST = 2;
    /** 撕咬:耐久 */
    public static final int BITE_USES = 1;
    /** 撕咬:基础攻击贡献(+3,对齐同费用的「暗影突袭」) */
    public static final int BITE_ATTACK = 3;

    /** 龙之咆哮:费用 */
    public static final int ROAR_COST = 3;
    /** 龙之咆哮:耐久 */
    public static final int ROAR_USES = 5;
    /** 龙之咆哮:基础攻击贡献(+3) */
    public static final int ROAR_ATTACK = 3;

    /** 龙之咆哮命中减益时长:1:00 */
    public static final int ROAR_DEBUFF_TICKS = DragonRoarBreakEffect.DURATION_TICKS;
    /** 龙之咆哮命中减速等级:缓慢 III ⇒ amplifier 2 */
    public static final int ROAR_SLOW_AMPLIFIER = 2;
    /** 龙之咆哮命中护甲修正(−8 护甲 = −4 点防御) */
    public static final double ROAR_ARMOR_DELTA = DragonRoarBreakEffect.ARMOR_DELTA;

    // ══════════════════════════════════════════════════════════════════════════
    //  觉醒计数的服务端静态批次表(规格 §2.1 第 2 条)
    // ══════════════════════════════════════════════════════════════════════════

    /** 一次发牌事件的批次:同一个 gameTime 内的受益人集合 */
    private static final class Batch {
        final long tick;
        final Set<UUID> receivers = new HashSet<>();

        Batch(long tick) {
            this.tick = tick;
        }
    }

    /** 佩戴者 UUID → 当前发牌批次(仅服务端;惰性清理过旧条目) */
    private static final Map<UUID, Batch> BATCHES = new ConcurrentHashMap<>();

    /** 批次过期时长(tick):比它更旧的条目会被惰性清理 —— 只是防止静态表无界增长,不参与任何判定 */
    private static final long BATCH_STALE_TICKS = 200L;

    public MamushiSignItem(Properties properties) {
        super(properties);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动「连锁反应」(非选择器类;规格 §2.4)
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        // 客户端早退(照 NardisSignItem#handleUse):真正的判定与发牌一律服务端权威
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 强制冷却硬闸门(不可被任何减免绕过):写在这里而不是只依赖 performSkill 的入账 ——
        // 手持右键/二次调用等任何到达本方法的路径都会被同一判据挡住(幂等,只提示一次)。
        if (isForcedCooldownActive(player)) {
            notifyForcedCooldown(player);
            return InteractionResultHolder.fail(stack);
        }
        // D6:真龙形态在**发放开始前判定一次**(本次发放过程中因给牌涨到 8 层不改变本次目标集)
        boolean dragon = isDragonForm(player);
        // ① 自身:走专属发放形状(setOwner 绑定 + 发牌漏斗 + giver 透传 ⇒ 觉醒计数生效)
        DragonCardUtil.giveExclusiveCard(player, player, dragon, 1);
        // ② 其他目标:12 格内友方队友(真龙形态 ⇒ 同维度全体),距离升序,32 人上限
        //    目标集合见 collectActiveTargets(严格同维度 → 排除自己 → 有队伍只取同队 / 未组队不做队伍过滤
        //    → 距离 ≤ 12(真龙形态不限)→ 距离升序 → 32 人上限)
        for (Player target : collectActiveTargets(player, dragon)) {
            // D1「目标当前未持有卡牌 ⇒ 额外一张」必须在**第一次发牌之前**求值:
            // 第一次 giveCardTo 成功入包后目标就已经持有卡牌,事后判据恒为 false ⇒ 额外那张永远发不出去。
            // 口径 = 主物品栏 + 双手 + 骰子卡牌栏(见 MamushiSignItem#hasAnyCard)。
            boolean hadNoCards = !hasAnyCard(target);
            RandomCardHandler.giveCardTo(player, target, RandomCardHandler.CardCategory.ALL);
            if (hadNoCards) {
                RandomCardHandler.giveCardTo(player, target, RandomCardHandler.CardCategory.ALL);
            }
        }
        // ③ 释放当刻写强制冷却硬闸门(now + 1:00);**不写** sign_active_cooldown_end ——
        //    那笔由 performSkill 第 ⑧ 步按 forcedActiveCooldownTicks() 入账,避免事后覆盖既有机制。
        ModAttachments.setMamushiForcedCooldownUntil(player, level.getGameTime() + ACTIVE_FORCED_COOLDOWN_TICKS);
        return InteractionResultHolder.success(stack);
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
    //  强制冷却(规格 §3.4;BaseSignItem 的两个钩子 + 手动路径兜底)
    // ══════════════════════════════════════════════════════════════════════════

    /** 强制冷却 1:00(0 = 无强制;本立牌恒为 1200,不经过诡异骰子的减免) */
    @Override
    protected int forcedActiveCooldownTicks() {
        return ACTIVE_FORCED_COOLDOWN_TICKS;
    }

    /** 强制冷却硬闸门的绝对到期刻(0 = 未设) */
    @Override
    protected long forcedCooldownUntil(Player player) {
        return getForcedCooldownUntil(player);
    }

    /** 当前是否被强制冷却挡住(供 {@code CurrentCoreChipItem} 与探针复用) */
    public static boolean isForcedCooldownActive(Player player) {
        if (player == null) return false;
        long until = getForcedCooldownUntil(player);
        return until > 0 && player.level().getGameTime() < until;
    }

    /** 强制冷却到期刻(附件读值;0 = 未设) */
    public static long getForcedCooldownUntil(Player player) {
        return ModAttachments.getMamushiForcedCooldownUntil(player);
    }

    private static void notifyForcedCooldown(Player player) {
        sendSignActionBar(player, "msg.astral_dice.mamushi_cooldown_locked");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  觉醒层数 / 真龙形态(规格 §2.1 / §2.2)
    // ══════════════════════════════════════════════════════════════════════════

    /** 佩戴本立牌(stand 饰品槽) */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.MAMUSHI_SIGN.get())).isPresent();
    }

    /** 真龙形态(锁存态):佩戴立牌 **且** 觉醒 ≥ {@link #AWAKEN_MAX} */
    public static boolean isDragonForm(Player player) {
        return player != null && isEquipped(player) && getAwakening(player) >= AWAKEN_MAX;
    }

    /** 觉醒层数(0 起,不封顶;真龙形态是锁存态而非消耗,裁决 D4) */
    public static int getAwakening(Player player) {
        return player == null ? 0 : ModAttachments.getMamushiAwakening(player);
    }

    /** 直接设置觉醒层数(不做转换判定,供探针/调试与内部回滚用) */
    public static void setAwakening(Player player, int value) {
        if (player == null) return;
        ModAttachments.setMamushiAwakening(player, Math.max(0, value));
    }

    /** 撕咬加成锁存是否置位(置位 + 仍有骰神赐福 ⇒ 加成 = min(觉醒, 4)) */
    public static boolean getBiteBonusActive(Player player) {
        return player != null && ModAttachments.getMamushiBiteBonusActive(player);
    }

    /** 设置撕咬加成锁存 */
    public static void setBiteBonusActive(Player player, boolean value) {
        if (player == null) return;
        ModAttachments.setMamushiBiteBonusActive(player, value);
    }

    /** 当前应生效的撕咬攻击加成(供 tooltip/探针复用;不含真龙形态的 +5) */
    public static int currentBiteBonus(Player player) {
        if (!getBiteBonusActive(player) || !player.hasEffect(ModEffects.DICE_BLESSING.get())) return 0;
        return Math.min(getAwakening(player), BITE_BONUS_CAP);
    }

    /**
     * 发牌漏斗的觉醒计数入口(规格 §2.1 第 3 条)——
     * 由 {@code VitaminPillChipItem#giveCard(Player giver, Player receiver, ItemStack)} 在**成功入包**那一支调用。
     *
     * <p>自己给自己不计({@code giver == receiver});不在服务端不计;未佩戴本立牌不计。
     * 同一 tick 内同一佩戴者的多次调用视为**一次事件**,按受益人去重、单次封顶
     * {@link #AWAKEN_GRANT_CAP_PER_EVENT} 层。
     */
    public static void onCardGivenToOther(Player giver, Player receiver) {
        if (giver == null || receiver == null) return;
        if (giver == receiver) return;                       // 自己给自己不计(规格 §2.1 第 1 条)
        if (giver.level().isClientSide()) return;
        if (!isEquipped(giver)) return;
        boolean wasDragon = isDragonForm(giver);
        if (!grantAwakeningFromBatch(giver, receiver)) return;   // 本次未加层(重复受益人/已封顶)⇒ 不做转换判定
        // 任意加层后觉醒 ≥ 8 且此前未处于真龙形态 ⇒ 立即转换(幂等) + actionbar 提示
        triggerDragonFormIfNeeded(giver, wasDragon);
    }

    /**
     * 加层后的「首次进入真龙形态」判定(本线两条加层路径的唯一出口,与 1.21.1 的
     * {@code addAwakening} 尾段同语义):此前不在形态、加层后 {@code 觉醒 >= }{@link #AWAKEN_MAX}
     * ⇒ 发 actionbar {@code msg.astral_dice.mamushi_dragon_form}(F6b:该键本线此前注册却从未使用)
     * 并执行幂等转换 {@link #transformToDragon(Player)}。
     */
    private static void triggerDragonFormIfNeeded(Player player, boolean wasDragon) {
        if (!wasDragon && isDragonForm(player)) {
            sendSignActionBar(player, "msg.astral_dice.mamushi_dragon_form");
            transformToDragon(player);
        }
    }

    /**
     * 批次计数:同一 tick 内按受益人去重 +1 层,单次封顶 {@link #AWAKEN_GRANT_CAP_PER_EVENT}。
     *
     * <p>F3(2026-09-27 收口):写入值**受 {@link #AWAKEN_MAX}(8)封顶** —— 用户需求原文为
     * 「觉醒层数受 8 层上限约束」,不封顶会让 tooltip 的 {@code addSignCounter} 显示 {@code 9/8}。
     * 封顶不改变任何行为:真龙形态判据是 {@code >= AWAKEN_MAX}(锁存)、撕咬加成是
     * {@code min(觉醒, 4)}(4 ≤ 8)。
     *
     * @return true = 本次确实加了 1 层
     */
    private static boolean grantAwakeningFromBatch(Player giver, Player receiver) {
        // 真龙形态是锁存态:形态成立即表示「觉醒」计数器**已失效** ⇒ 不再累计(值恒为 AWAKEN_MAX)。
        if (isDragonForm(giver)) return false;
        long now = giver.level().getGameTime();
        Batch batch = BATCHES.get(giver.getUUID());
        if (batch == null || batch.tick != now) {
            batch = new Batch(now);
            BATCHES.put(giver.getUUID(), batch);
            pruneStaleBatches(now);                          // 顺带惰性清理过旧批次(静态表不无界增长)
        }
        if (!batch.receivers.add(receiver.getUUID())) return false;   // 同一事件内同一受益人只算 1 层
        if (batch.receivers.size() > AWAKEN_GRANT_CAP_PER_EVENT) return false;  // 单次封顶 3 层
        setAwakening(giver, Math.min(getAwakening(giver) + 1, AWAKEN_MAX));
        return true;
    }

    /** 惰性清理过旧批次(仅防静态表增长;被清掉只意味着"那一次事件早已结束") */
    private static void pruneStaleBatches(long now) {
        if (BATCHES.size() < 16) return;
        Iterator<Map.Entry<UUID, Batch>> it = BATCHES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Batch> e = it.next();
            if (now - e.getValue().tick > BATCH_STALE_TICKS) it.remove();
        }
    }

    /** 清空该玩家的批次表(卸下立牌时调用;规格 §2.5) */
    public static void clearBatches(Player player) {
        if (player == null) return;
        BATCHES.remove(player.getUUID());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  牌转换:撕咬 → 龙之咆哮(规格 §2.3)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 进入真龙形态时的**一次性、幂等**转换:背包/副手 + 骰子卡牌栏里的撕咬全部转成龙之咆哮。
     *
     * <p>背包侧的实现在 {@link DragonCardUtil#convertInventoryBites(Player)}(保张数、保内置绑定),
     * 骰子侧在 {@link DragonCardUtil#convertEquippedBites(Player)}(逐个转换,超费用的丢弃);
     * 转换后立即补齐 {@code mamushi_dragon} 效果(若尚未施加)。
     *
     * <p>调用点(与 1.21.1 同):① 加层后首次达到 {@link #AWAKEN_MAX} 的唯一出口
     * {@link #triggerDragonFormIfNeeded}(被动发牌 + 撕咬赐福两条路径);② {@link #onCurioTick}
     * 每 20 tick 的幂等自愈(死亡重生/重登等旁路留下撕咬时兜底)。本方法自身**不发**提示。
     */
    public static void transformToDragon(Player player) {
        if (player == null || player.level().isClientSide()) return;
        try {
            DragonCardUtil.convertInventoryBites(player);
            DragonCardUtil.convertEquippedBites(player);
        } catch (Throwable ignored) {
            // 转换失败不应打断发牌主链路(规格未要求日志等级;此处静默,效果与层数仍然成立)
        }
        MamushiDragonEffect.apply(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  撕咬:赐福触发/结束(规格 §2.6)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 骰神赐福**触发**时调用(挂点 = {@code combat/DiceCombatEvents} 的赐福触发块,与既有
     * 6 个 {@code onBlessingStart} 同位):装备中每有 **1 张**撕咬 ⇒ 觉醒 **+1 层**
     * (裁决 2;不受"给他人发牌"的 3 层封顶约束),并置「撕咬加成锁存」。
     *
     * <p><b>为什么必须锁存</b>:撕咬耐久 1,同一击稍后就会被消耗掉;不锁存则加成会在本应生效的那一刻失效。
     * 锁存由 {@link #onDiceBlessingEnded(Player)} 在赐福结束时清除(立牌 tick 另有兜底)。
     */
    public static void onDiceBlessingTriggered(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        int bites = DragonCardUtil.countEquippedType(player, DragonCardUtil.TYPE_BITE);
        if (bites <= 0) return;
        setBiteBonusActive(player, true);
        // 真龙形态是锁存态:形态成立即表示「觉醒」计数器**已失效** ⇒ 不再累计(值恒为 AWAKEN_MAX);
        // 撕咬加成锁存照旧写入 —— 加成按 `min(觉醒, BITE_BONUS_CAP)` 实时取值,与计数器失效与否无关。
        if (isDragonForm(player)) return;
        setAwakening(player, Math.min(getAwakening(player) + bites, AWAKEN_MAX));
        // 上方已早退「形态已成立」的分支 ⇒ 走到这里必为「首次进入」,等价旧 wasDragon == false
        triggerDragonFormIfNeeded(player, false);
    }

    /** 骰神赐福**结束**时调用:清除撕咬加成锁存(裁决 4:加成实时读取,锁存清掉即失效) */
    public static void onDiceBlessingEnded(Player player) {
        if (player == null || player.level().isClientSide()) return;
        setBiteBonusActive(player, false);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  龙之咆哮:命中减益(规格 §2.7)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 对**被攻击的目标**施加「缓慢 III 1:00」+「破防({@code ARMOR -8} = 减 4 点防御)1:00」。
     *
     * <p>D8:重复命中只刷新时长(不叠层)—— 两条效果都以 {@code amplifier = 0} 施加,原版
     * {@code MobEffectInstance#update} 对同放大器实例只取较长时长;
     * {@link DragonRoarBreakEffect} 另把修饰器取值固定为 −8,双重保证"层数不会放大减甲"。
     * 六参构造:关粒子、留图标。缓慢是原版效果,按仓库既有写法经
     * {@link EffectTimerGuard#apply} 施加以纳入"严格 20 t/s"守卫;破防是本模组效果,
     * 由 {@code MobEffectEvent.Added} 自动登记计时。
     */
    public static void applyRoarDebuff(LivingEntity victim) {
        if (victim == null || victim.level().isClientSide()) return;
        EffectTimerGuard.apply(victim, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                ROAR_DEBUFF_TICKS, ROAR_SLOW_AMPLIFIER, false, false, true));
        victim.addEffect(new MobEffectInstance(ModEffects.DRAGON_ROAR_BREAK.get(),
                ROAR_DEBUFF_TICKS, 0, false, false, true));
    }

    /** 玩家骰子卡牌栏里指定类型的装配张数(规格 §3.2;委托 {@link DragonCardUtil}) */
    public static int countEquippedType(Player player, String typeId) {
        return DragonCardUtil.countEquippedType(player, typeId);
    }

    /**
     * 规格 §3.2 的 D1 判据:玩家当前**未持有任何本模组卡牌**。
     *
     * <p>口径(裁决 D1 + 主代理默认口径 D1):主物品栏(0..35)+ **双手** + **骰子卡牌栏**
     * ({@code appliedStones})里没有任何 {@code ModItems.isCardItem} ⇒ 额外发 1 张。
     * 卡牌判据走物品标签({@code combat_cards} / {@code effect_cards}),与既有实现同源。
     */
    public static boolean hasAnyCard(Player player) {
        if (player == null) return false;
        if (DragonCardUtil.hasAnyCardInInventory(player)) return true;
        if (DragonCardUtil.hasAnyCardInHands(player)) return true;
        return DragonCardUtil.countEquippedCards(player) > 0;
    }

    /** 发放一张已绑定获得者的专属牌(规格 §3.2;委托 {@link DragonCardUtil}) */
    public static void giveExclusiveCard(Player giver, Player receiver, boolean roar, int count) {
        DragonCardUtil.giveExclusiveCard(giver, receiver, roar, count);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  立牌 tick / 卸下(规格 §3.7 / §2.5)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 立牌 tick:①真龙形态 ⇒ 效果 {@code refresh},否则 {@code remove};
     * ②兜底清撕咬锁存(无骰神赐福而锁存仍为真 ⇒ 清);③清理静态批次表过旧条目。
     *
     * <p>本方法只在**立牌仍在 stand 槽**时被 Curios 调用(见 {@code BaseSignItem#curioTick})。
     * 卸载那一刻的清理走 {@link #clearSignData};因此正常路径下不存在"效果与判据长期不一致"的窗口。
     */
    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        // ① 真龙形态的可见载体维护
        if (isDragonForm(player)) {
            MamushiDragonEffect.refresh(player);
            // ①b 幂等自愈保底(对齐 1.21.1):形态成立却仍有撕咬(死亡重生/重登等旁路未走加层路径)
            //     ⇒ 每 20 tick 补做一次转换。转换本身幂等且廉价,不必每 tick。
            if (now % 20L == 0L) {
                transformToDragon(player);
            }
        } else {
            MamushiDragonEffect.remove(player);
        }
        // ② 兜底清锁存:赐福结束钩子若被漏掉(外力移除/死亡/重登),最迟下一 tick 收敛
        if (getBiteBonusActive(player) && !player.hasEffect(ModEffects.DICE_BLESSING.get())) {
            setBiteBonusActive(player, false);
        }
        // ③ 静态批次表惰性清理
        pruneStaleBatches(now);
    }

    /**
     * 卸下立牌(只在"玩家有意卸除"时到达,见 {@code BaseSignItem#onUnequip} +
     * {@code CurioSlotUtil.isIntentionalUnequip}):觉醒归零、撕咬锁存清除、移除真龙形态效果、清批次表。
     *
     * <p>**不清**玩家级冷却({@code sign_active_cooldown_end} / {@code mamushi_forced_cooldown_until})
     * —— 冷却为玩家级,既有口径不随装卸重置(规格 §2.5)。
     */
    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        setAwakening(player, 0);
        setBiteBonusActive(player, false);
        MamushiDragonEffect.remove(player);
        clearBatches(player);
    }
}
