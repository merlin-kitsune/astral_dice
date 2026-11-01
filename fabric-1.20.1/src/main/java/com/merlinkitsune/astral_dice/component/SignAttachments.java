package com.merlinkitsune.astral_dice.component;

import com.mojang.serialization.Codec;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * B4-SIGNS 批次 7 个新立牌(修女 / 地雷女 / 大肥鱼 / 多萝西 / 调酒师 / 超天酱 / 怪力乱神)的
 * **玩家级附件键**。
 *
 * <h2>为什么单独一个类,而不是并进 {@link ModAttachments}</h2>
 * <p>本批次进行时 {@code component/ModAttachments.java} 正被其它批次(卡牌商店)改写(工作树 M 状态),
 * 且任务书把该文件列为**禁止改动**。于是这些键集中注册在本类里:键名(name)、codec、默认值、
 * 同步标记与死亡保留标记全部与 forge-1.20.1 侧 {@code ModAttachments} 里的同名键**逐字一致**
 * (forge 源见 {@code component/ModAttachments.java} L1948-2240 与 L615-632)。
 *
 * <h2>⚠️ 注册时序(与 ModAttachments 同一个坑,见 KI-F17)</h2>
 * <p>Fabric 在玩家 NBT 反序列化时按 id 查附件注册表:查不到就
 * {@code Unknown attachment type astral_dice:xxx found when deserializing, skipping} 并**静默丢弃**。
 * 而 {@code LoaderBus.register(Class)} 走 {@code getDeclaredMethods()},**不会触发目标类的
 * {@code <clinit>}** ⇒ 「登记事件宿主」不能顺带完成本类的注册。
 *
 * <p>本线可用的早期触发点是物品注册({@code ModItems.<clinit>} 远早于任何玩家数据加载):
 * {@code item/sign/BaseSignItem} 的静态块调用 {@link #ensureRegistered()}。
 * <b>长期正解</b>是把这次调用并进 {@code ModAttachments#ensureRegistered()} 或
 * {@code AstralDiceMod#onInitialize()}(两者在本批次写权之外),届时可删掉 BaseSignItem 里的静态块。
 *
 * <h2>同步</h2>
 * <p>{@code .sync()} 的键在服务端 {@code set} 时即时下发;登录/重生/换维度的**全量快照**走
 * {@code ModAttachments#syncedKeys()},因此 {@link #ensureRegistered()} 会把本类的同步键追加进那张表
 * (该表在 {@code ModAttachments} 内是包级可见的同一个可变 {@code List} 实例)。
 */
public final class SignAttachments {
    private SignAttachments() {
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 立牌主动技能「未折电池」基准冷却(forge: ModAttachments.SIGN_ACTIVE_MAX_COOLDOWN_BASE)
    // ══════════════════════════════════════════════════════════════════════════

    /** 本次冷却基准的**未折电池筹码**版本(0 = 缺失 ⇒ 读取方按老存档回退)。仅服务端,不 .sync()。 */
    public static final AttachedDataKey<Long> SIGN_ACTIVE_MAX_COOLDOWN_BASE =
            AttachedDataKey.builder("sign_active_max_cooldown_base", Codec.LONG, () -> 0L).build();

    public static long getSignActiveMaxCooldownBase(Player player) {
        return SIGN_ACTIVE_MAX_COOLDOWN_BASE.get(player);
    }

    public static void setSignActiveMaxCooldownBase(Player player, long value) {
        SIGN_ACTIVE_MAX_COOLDOWN_BASE.set(player, value);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 修女立牌(alana)
    // ══════════════════════════════════════════════════════════════════════════

    /** 修女：被动累计攻击加成(每次「鼓起勇气」+5)。需同步(客户端 tooltip 读)。 */
    public static final AttachedDataKey<Integer> ALANA_PASSIVE_BONUS =
            AttachedDataKey.builder("alana_passive_bonus", Codec.INT, () -> 0).sync().build();

    public static int getAlanaPassiveBonus(Player player) {
        return ALANA_PASSIVE_BONUS.get(player);
    }

    public static void setAlanaPassiveBonus(Player player, int value) {
        ALANA_PASSIVE_BONUS.set(player, Math.max(0, value));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 超天酱立牌(kangel)
    // ══════════════════════════════════════════════════════════════════════════

    /** 超天酱：粉丝数真值(0..10)。**跨死亡保留**;需同步(客户端 tooltip 计数器行)。 */
    public static final AttachedDataKey<Integer> KANGEL_FAN_COUNT =
            AttachedDataKey.builder("kangel_fan_count", Codec.INT, () -> 0).sync().copyOnDeath().build();

    /**
     * 超天酱：**已登记目标 UUID 集**(逗号分隔) —— 「每个目标对每个超天酱玩家只计 1 个粉丝」的判据。
     * 与计数同寿命(**跨死亡保留**),否则重生后计数保留而目标集清空 ⇒ 同一目标被重复计成新粉丝。
     * 仅服务端判定,不 .sync()。
     */
    public static final AttachedDataKey<String> KANGEL_FAN_TARGETS =
            AttachedDataKey.builder("kangel_fan_targets", Codec.STRING, () -> "").copyOnDeath().build();

    public static int getKangelFanCount(Player player) {
        return KANGEL_FAN_COUNT.get(player);
    }

    public static void setKangelFanCount(Player player, int value) {
        KANGEL_FAN_COUNT.set(player, Math.max(0, value));
    }

    public static String getKangelFanTargets(Player player) {
        return KANGEL_FAN_TARGETS.get(player);
    }

    public static void setKangelFanTargets(Player player, String value) {
        KANGEL_FAN_TARGETS.set(player, value == null ? "" : value);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 地雷女立牌(ame)
    // ══════════════════════════════════════════════════════════════════════════

    /** 地雷女：「爱」层数(0..上限)。**跨死亡保留**、**卸下清零**。需同步(客户端计数器行)。 */
    public static final AttachedDataKey<Integer> AME_LOVE_LAYERS =
            AttachedDataKey.builder("ame_love_layers", Codec.INT, () -> 0).sync().copyOnDeath().build();

    /** 地雷女：层数上限(默认 4;主动自然到期后 +1,最高 32)。**跨死亡保留**。需同步。 */
    public static final AttachedDataKey<Integer> AME_LOVE_CAP =
            AttachedDataKey.builder("ame_love_cap", Codec.INT, () -> 4).sync().copyOnDeath().build();

    /** 地雷女：「爱」获得闸门的下次可获层刻(绝对 gameTime;0 = 无闸门)。不跨死亡、不 .sync()。 */
    public static final AttachedDataKey<Long> AME_LOVE_GAIN_CD_END =
            AttachedDataKey.builder("ame_love_gain_cd_end", Codec.LONG, () -> 0L).build();

    public static int getAmeLoveLayers(Player player) {
        return AME_LOVE_LAYERS.get(player);
    }

    public static void setAmeLoveLayers(Player player, int value) {
        AME_LOVE_LAYERS.set(player, Math.max(0, value));
    }

    public static int getAmeLoveCap(Player player) {
        return AME_LOVE_CAP.get(player);
    }

    public static void setAmeLoveCap(Player player, int value) {
        AME_LOVE_CAP.set(player, Math.max(1, value));
    }

    public static long getAmeLoveGainCdEnd(Player player) {
        return AME_LOVE_GAIN_CD_END.get(player);
    }

    public static void setAmeLoveGainCdEnd(Player player, long value) {
        AME_LOVE_GAIN_CD_END.set(player, Math.max(0L, value));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 调酒师立牌(jill)
    // ══════════════════════════════════════════════════════════════════════════

    /** 调酒师：目标选择确认后暂存的目标 UUID(空串 = 无)。选卡取消即清空。 */
    public static final AttachedDataKey<String> JILL_PENDING_TARGET =
            AttachedDataKey.builder("jill_pending_target", Codec.STRING, () -> "").build();

    /** 调酒师：主动 buff 受惠目标 UUID(空串 = 无;到期/卸下清除)。 */
    public static final AttachedDataKey<String> JILL_BUFF_TARGET =
            AttachedDataKey.builder("jill_buff_target", Codec.STRING, () -> "").build();

    /** 调酒师：主动 buff 到期刻(绝对 gameTime;0 = 无)。 */
    public static final AttachedDataKey<Long> JILL_BUFF_END =
            AttachedDataKey.builder("jill_buff_end", Codec.LONG, () -> 0L).build();

    public static String getJillPendingTarget(Player player) {
        return JILL_PENDING_TARGET.get(player);
    }

    public static void setJillPendingTarget(Player player, String value) {
        JILL_PENDING_TARGET.set(player, value == null ? "" : value);
    }

    public static String getJillBuffTarget(Player player) {
        return JILL_BUFF_TARGET.get(player);
    }

    public static void setJillBuffTarget(Player player, String value) {
        JILL_BUFF_TARGET.set(player, value == null ? "" : value);
    }

    public static long getJillBuffEnd(Player player) {
        return JILL_BUFF_END.get(player);
    }

    public static void setJillBuffEnd(Player player, long value) {
        JILL_BUFF_END.set(player, value);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 多萝西立牌(dorothy)
    // ══════════════════════════════════════════════════════════════════════════

    /** 多萝西：主动窗口结束刻(0 = 无窗口)。需同步。 */
    public static final AttachedDataKey<Long> DOROTHY_WINDOW_END =
            AttachedDataKey.builder("dorothy_window_end", Codec.LONG, () -> 0L).sync().build();

    /** 多萝西：窗口内已结算「每人一次」的友方玩家 UUID(逗号分隔)。 */
    public static final AttachedDataKey<String> DOROTHY_PARTNERS_ONCE =
            AttachedDataKey.builder("dorothy_partners_once", Codec.STRING, () -> "").build();

    /** 多萝西：当前攻击加成总量(窗口内每人 1 点 + 满层转化值)。需同步。 */
    public static final AttachedDataKey<Integer> DOROTHY_ATTACK_BONUS =
            AttachedDataKey.builder("dorothy_attack_bonus", Codec.INT, () -> 0).sync().build();

    /** 多萝西：被动回血的冷却结束刻。 */
    public static final AttachedDataKey<Long> DOROTHY_HEAL_COOLDOWN_END =
            AttachedDataKey.builder("dorothy_heal_cooldown_end", Codec.LONG, () -> 0L).build();

    public static long getDorothyWindowEnd(Player player) {
        return DOROTHY_WINDOW_END.get(player);
    }

    public static void setDorothyWindowEnd(Player player, long value) {
        DOROTHY_WINDOW_END.set(player, value);
    }

    public static int getDorothyAttackBonus(Player player) {
        return DOROTHY_ATTACK_BONUS.get(player);
    }

    public static void setDorothyAttackBonus(Player player, int value) {
        DOROTHY_ATTACK_BONUS.set(player, Math.max(0, value));
    }

    public static void addDorothyAttackBonus(Player player, int delta) {
        setDorothyAttackBonus(player, getDorothyAttackBonus(player) + delta);
    }

    public static long getDorothyHealCooldownEnd(Player player) {
        return DOROTHY_HEAL_COOLDOWN_END.get(player);
    }

    public static void setDorothyHealCooldownEnd(Player player, long value) {
        DOROTHY_HEAL_COOLDOWN_END.set(player, value);
    }

    public static void clearDorothyPartners(Player player) {
        DOROTHY_PARTNERS_ONCE.set(player, "");
    }

    /**
     * 返回 true 表示该友方玩家本窗口首次结算(每人每窗口一次)。
     *
     * <p>2026-10-25 用户裁决：删掉 10 秒节流 —— 「每人每窗口一次」本身已足够防刷；队友拿到的攻击力
     * 改由【交际花】效果承载(固定 1:00,与窗口解耦)。
     */
    public static boolean tryTriggerDorothyPartner(Player player, UUID other) {
        String key = other.toString();
        String onceRaw = DOROTHY_PARTNERS_ONCE.get(player);
        Set<String> once = new LinkedHashSet<>();
        if (onceRaw != null && !onceRaw.isEmpty()) {
            for (String part : onceRaw.split(",")) {
                if (!part.isEmpty()) once.add(part);
            }
        }
        if (once.contains(key)) return false;
        once.add(key);
        StringBuilder onceSb = new StringBuilder();
        for (String p : once) {
            if (onceSb.length() > 0) onceSb.append(',');
            onceSb.append(p);
        }
        DOROTHY_PARTNERS_ONCE.set(player, onceSb.toString());
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 大肥鱼立牌(deepseek) —— 位置/移动计数为**仅内存态**(forge: .inMemory())
    // ══════════════════════════════════════════════════════════════════════════

    /** 大肥鱼：上一 tick 的精确位置快照(x,y,z;空 = 尚无快照)。仅内存态。 */
    public static final AttachedDataKey<List<Double>> DEEPSEEK_LAST_POS =
            AttachedDataKey.builder("deepseek_last_pos", Codec.DOUBLE.listOf(), List::<Double>of)
                    .inMemory().build();

    /** 大肥鱼：摸鱼中累计的移动 tick(满 100 解除摸鱼;中途停下不清零)。仅内存态。 */
    public static final AttachedDataKey<Integer> DEEPSEEK_MOVE_TICKS =
            AttachedDataKey.builder("deepseek_move_ticks", Codec.INT, () -> 0).inMemory().build();

    /** 大肥鱼：主动攻/防加成结束刻(0 = 无加成)。仅内存态(与瞬态修饰符同寿命)。 */
    public static final AttachedDataKey<Long> DEEPSEEK_BUFF_END =
            AttachedDataKey.builder("deepseek_buff_end", Codec.LONG, () -> 0L).inMemory().build();

    /** 大肥鱼：上一次 Curio tick 的 gameTime(0 = 尚未记录)。仅内存态(检测 tick 中断)。 */
    public static final AttachedDataKey<Long> DEEPSEEK_LAST_TICK =
            AttachedDataKey.builder("deepseek_last_tick", Codec.LONG, () -> 0L).inMemory().build();

    /** 大肥鱼：静止起始刻。 */
    public static final AttachedDataKey<Long> DEEPSEEK_IDLE_SINCE =
            AttachedDataKey.builder("deepseek_idle_since", Codec.LONG, () -> 0L).build();

    public static List<Double> getDeepseekLastPos(Player player) {
        return DEEPSEEK_LAST_POS.get(player);
    }

    public static void setDeepseekLastPos(Player player, List<Double> value) {
        DEEPSEEK_LAST_POS.set(player, value == null ? List.<Double>of() : value);
    }

    public static int getDeepseekMoveTicks(Player player) {
        return DEEPSEEK_MOVE_TICKS.get(player);
    }

    public static void setDeepseekMoveTicks(Player player, int value) {
        DEEPSEEK_MOVE_TICKS.set(player, Math.max(0, value));
    }

    public static long getDeepseekBuffEnd(Player player) {
        return DEEPSEEK_BUFF_END.get(player);
    }

    public static void setDeepseekBuffEnd(Player player, long value) {
        DEEPSEEK_BUFF_END.set(player, value);
    }

    public static long getDeepseekLastTick(Player player) {
        return DEEPSEEK_LAST_TICK.get(player);
    }

    public static void setDeepseekLastTick(Player player, long value) {
        DEEPSEEK_LAST_TICK.set(player, value);
    }

    public static long getDeepseekIdleSince(Player player) {
        return DEEPSEEK_IDLE_SINCE.get(player);
    }

    public static void setDeepseekIdleSince(Player player, long value) {
        DEEPSEEK_IDLE_SINCE.set(player, value);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 怪力乱神立牌(lingling)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 怪力乱神：首次装备赠 2 张强化拒止的**一生一次**标记。
     *
     * <p>**跨死亡保留、卸下不清** —— 刻意不走基类 {@code claimEquipGrant}(那条通道卸下即释放
     * ⇒ 卸下再装能刷牌)。forge 侧同键同样在死亡保留白名单里(没有清零路径)。
     */
    public static final AttachedDataKey<Boolean> LINGLING_GRANTED =
            AttachedDataKey.builder("lingling_granted", Codec.BOOL, () -> false).copyOnDeath().build();

    public static boolean isLinglingGranted(Player player) {
        return LINGLING_GRANTED.get(player);
    }

    public static void setLinglingGranted(Player player, boolean value) {
        LINGLING_GRANTED.set(player, value);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 注册 / 同步登记
    // ══════════════════════════════════════════════════════════════════════════

    /** 需要客户端可见(登录/重生/换维度全量快照)的键 —— 与 forge 侧 {@code SYNCED_KEYS} 的成员一致。 */
    private static final List<AttachedDataKey<?>> SYNCED = List.of(
            ALANA_PASSIVE_BONUS,
            KANGEL_FAN_COUNT,
            AME_LOVE_LAYERS,
            AME_LOVE_CAP,
            DOROTHY_WINDOW_END,
            DOROTHY_ATTACK_BONUS);

    private static boolean registered;

    /**
     * <b>强制本类的静态初始化</b>(= 把本类全部附件键注册进 Fabric 附件注册表),
     * 并把同步键追加进 {@code ModAttachments#syncedKeys()} 那张表。
     *
     * <p>⚠️ 必须在**任何玩家 NBT 反序列化之前**调用(理由见类头)。幂等。
     */
    public static void ensureRegistered() {
        if (registered) return;
        registered = true;
        // 触碰 ModAttachments 的 clinit(它会打印 "附件键已注册 N 个"),并把同步键并入同一张表
        List<AttachedDataKey<?>> shared = ModAttachments.syncedKeys();
        Set<String> existing = new HashSet<>();
        for (AttachedDataKey<?> key : shared) {
            existing.add(key.name());
        }
        for (AttachedDataKey<?> key : SYNCED) {
            if (existing.add(key.name())) {
                shared.add(key);
            }
        }
    }
}
