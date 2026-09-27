package com.merlinkitsune.astral_dice.effect;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 「白泽赐福」(风水师立牌 zhao 主动):无层数、无属性修饰符的**状态载体**。
 *
 * <p><b>时长模型(2026-09-27 用户裁决重写)</b>:不再绑定「骰神赐福」,改为**固定 2:00 倒计时**,
 * 且倒计时必须等**被施加者首次实施合格近战攻击**后才启动 —— 施加瞬间效果**立即生效**。
 * 两态用一个常量区分(见 {@code item/sign/ZhaoSignItem#tickBlessing} 的状态机):
 * <ul>
 *   <li><b>未启动</b>({@link #PENDING_DURATION_TICKS} = {@code -1}):原版
 *       {@code MobEffectInstance#isInfiniteDuration()} 只认 {@code -1},该值下
 *       {@code tickDownDuration} 直接跳过 ⇒ **不会走动**,效果常驻且完全生效;</li>
 *   <li><b>已启动</b>({@link #DURATION_TICKS} = 2400):正常每 tick −1;归零/被外力移除都由
 *       {@code ZhaoSignItem#tickBlessing} 的自检收敛到 {@code endBlessing}。</li>
 * </ul>
 * ⚠️ 子代理/外部若用 {@code Integer.MAX_VALUE} 来表达"常驻",那**不是**原版无限时长
 * ({@code isInfiniteDuration} 只认 {@code -1})，{@code tickDownDuration} 会每 tick −1(需约 1243 天)。
 *
 * <p><b>溢出治疗 → 攻击力</b>的加成值不挂在本效果上,而是玩家附件
 * {@code ModAttachments#ZHAO_OVERFLOW_BONUS}(整数)+ {@code #ZHAO_OVERFLOW_REMAINDER}(余数);
 * 本效果被移除(含外力移除 / 死亡清场 / 状态机收尾)时由
 * {@code MobEffectEvent.Remove} 与 tick 自检**幂等**回收 —— 回收动作单一,不留残留。
 *
 * <p>图标 = {@code images/风水师立牌.png}(与立牌本体同一张图;实装路径
 * {@code textures/mob_effect/zhao_blessing.png})。
 */
public class ZhaoBlessingEffect extends MobEffect {
    /** **已启动倒计时**后的效果时长 = 2:00(2400 tick);由 {@code ZhaoSignItem} 在首次合格近战攻击时写入 */
    public static final int DURATION_TICKS = 2400;

    /**
     * **未启动倒计时**的占位时长 = {@code -1}(原版无限时长)。
     *
     * <p>该值下 {@code tickDownDuration()} 不扣减 ⇒ 效果在"等待被施加者攻击"期间**永不自然到期**,
     * 但图标与全部加成照常生效(需求:「施加效果会立即生效,但是计时器需要等待…」)。
     */
    public static final int PENDING_DURATION_TICKS = -1;

    public ZhaoBlessingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x4FC3A1);
    }

    /** 是否已获得「白泽赐福」(唯一判据:效果实例是否存在) */
    public static boolean has(Player player) {
        return player != null && player.hasEffect(ModEffects.ZHAO_BLESSING);
    }

    /** 施加「白泽赐福」(已存在时不重复施加);时长 = 未启动占位值,由状态机在首次攻击时改写 */
    public static void apply(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (player.hasEffect(ModEffects.ZHAO_BLESSING)) return;
        // 六参构造:ambient=false, visible=false, showIcon=true ⇒ HUD 效果栏可见(与本模组其它状态效果一致)
        player.addEffect(new MobEffectInstance(ModEffects.ZHAO_BLESSING,
                PENDING_DURATION_TICKS, 0, false, false, true));
    }

    /**
     * 把「白泽赐福」**切换到已启动态**(时长 = {@link #DURATION_TICKS}),并把剩余时长重置为满值。
     *
     * <p>只在「首次合格近战攻击」那一刻调用一次(由状态机保证 {@code timer_started})。重复调用会
     * 重置剩余时长 ⇒ 只允许一次,勿在 tick 里调用。
     *
     * <p>⚠️ <b>必须「先移除再施加」,不能只调 {@code addEffect}</b>:原版
     * {@code LivingEntity#addEffect} 对已存在的同名效果走 {@code MobEffectInstance#update},
     * 而 {@code update} 的判据是 {@code isShorterDurationThan(other)} —— 其首项
     * {@code !this.isInfiniteDuration()} 在**当前是无限时长**时直接为 false ⇒
     * 「无限 ⇒ 有限」这条路径拿不到任何分支命中,时长**原封不动留在 -1**,
     * 倒计时永远不启动(三平台 `update` 实现一致,已逐条核对源码)。
     * 故这里走本模组统一移除通道后重新施加,确保写入 2400。
     */
    public static void startTimer(Player player) {
        if (player == null || player.level().isClientSide()) return;
        // 先移除再施加(移除走统一通道,保证 MobEffectEvent.Remove 的回收逻辑照常触发)
        ModEffectRemoval.remove(player, ModEffects.ZHAO_BLESSING);
        player.addEffect(new MobEffectInstance(ModEffects.ZHAO_BLESSING,
                DURATION_TICKS, 0, false, false, true));
    }

    /**
     * 玩家级 tick 的**自检/补齐**:效果缺失则重新施加(时长按「是否已启动」取对应值)。
     *
     * <p>⚠️ 本方法**不刷新**已存在效果的剩余时长 —— 时长由原版每 tick 自行扣减,刷新会
     * 让 2:00 永远走不完。仅用于「真值仍在而效果实例没了」这一条自愈路径。
     *
     * @param timerStarted 倒计时是否已启动(决定补齐时写入哪种时长)
     */
    public static void refresh(Player player, boolean timerStarted) {
        if (player == null || player.level().isClientSide()) return;
        if (player.hasEffect(ModEffects.ZHAO_BLESSING)) return;
        player.addEffect(new MobEffectInstance(ModEffects.ZHAO_BLESSING,
                timerStarted ? DURATION_TICKS : PENDING_DURATION_TICKS, 0, false, false, true));
    }

    /** 移除「白泽赐福」(走本模组统一移除通道,保证 {@code MobEffectEvent.Remove} 的回收逻辑照常触发) */
    public static void remove(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModEffectRemoval.remove(player, ModEffects.ZHAO_BLESSING);
    }
}
