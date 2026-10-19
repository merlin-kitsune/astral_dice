package com.merlinkitsune.astral_dice.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 立牌"玩家级状态"复合键(2026-10-19,B2):把原先 10 个独立附件键
 * {@code sign_active_cooldown_end / sign_active_max_cooldown / sign_active_lock_sign / sign_active_lock_end /
 * sign_active_reduction_pool / sign_active_lock_grace_end / sign_active_lock_played /
 * sign_active_lock_last_seen / sign_ready_type / sign_ready_expire} 折叠成一条记录。
 *
 * <p><b>字段名、类型、默认值与旧键逐一相同</b>(record 组件名直接沿用旧键名),因此序列化语义、
 * 判定顺序与数值语义都没有任何变化;旧 10 键仍然注册(见 {@link ModAttachments}),
 * 仅用于登录时的一次性折叠迁移({@link ModAttachments#migrateSignStateFromLegacy}),
 * 迁移之后一切读写都走本记录。
 *
 * <p>{@link #CODEC} 的每个字段都用 {@code optionalFieldOf}:新存档缺字段、或将来增删字段时
 * 都按各字段默认值回退(等价于旧"键缺失 ⇒ 默认值"的行为)。
 *
 * <p>1.20.1 没有 {@code StreamCodec}(1.20.5 才引入)⇒ 本线只有 {@link #CODEC},
 * 同步走 {@link AttachedDataKey} 的 NBT tag 通道({@code .sync()}),与 1.21.1 侧的
 * {@code SignState.STREAM_CODEC} 表达同一份字段顺序/类型。
 */
public record SignState(
        long sign_active_cooldown_end,
        long sign_active_max_cooldown,
        String sign_active_lock_sign,
        long sign_active_lock_end,
        long sign_active_reduction_pool,
        long sign_active_lock_grace_end,
        boolean sign_active_lock_played,
        long sign_active_lock_last_seen,
        int sign_ready_type,
        long sign_ready_expire) {

    /** 全部字段取旧键默认值(= 旧存档"十个键都不存在"的状态;迁移幂等判据也用它)。 */
    public static final SignState EMPTY =
            new SignState(0L, 0L, "", 0L, 0L, 0L, false, 0L, 0, 0L);

    public static final Codec<SignState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.LONG.optionalFieldOf("sign_active_cooldown_end", 0L)
                    .forGetter(SignState::sign_active_cooldown_end),
            Codec.LONG.optionalFieldOf("sign_active_max_cooldown", 0L)
                    .forGetter(SignState::sign_active_max_cooldown),
            Codec.STRING.optionalFieldOf("sign_active_lock_sign", "")
                    .forGetter(SignState::sign_active_lock_sign),
            Codec.LONG.optionalFieldOf("sign_active_lock_end", 0L)
                    .forGetter(SignState::sign_active_lock_end),
            Codec.LONG.optionalFieldOf("sign_active_reduction_pool", 0L)
                    .forGetter(SignState::sign_active_reduction_pool),
            Codec.LONG.optionalFieldOf("sign_active_lock_grace_end", 0L)
                    .forGetter(SignState::sign_active_lock_grace_end),
            Codec.BOOL.optionalFieldOf("sign_active_lock_played", false)
                    .forGetter(SignState::sign_active_lock_played),
            Codec.LONG.optionalFieldOf("sign_active_lock_last_seen", 0L)
                    .forGetter(SignState::sign_active_lock_last_seen),
            Codec.INT.optionalFieldOf("sign_ready_type", 0)
                    .forGetter(SignState::sign_ready_type),
            Codec.LONG.optionalFieldOf("sign_ready_expire", 0L)
                    .forGetter(SignState::sign_ready_expire)
    ).apply(inst, SignState::new));

    public SignState withSignActiveCooldownEnd(long value) {
        return new SignState(value, sign_active_max_cooldown, sign_active_lock_sign, sign_active_lock_end,
                sign_active_reduction_pool, sign_active_lock_grace_end, sign_active_lock_played,
                sign_active_lock_last_seen, sign_ready_type, sign_ready_expire);
    }

    public SignState withSignActiveMaxCooldown(long value) {
        return new SignState(sign_active_cooldown_end, value, sign_active_lock_sign, sign_active_lock_end,
                sign_active_reduction_pool, sign_active_lock_grace_end, sign_active_lock_played,
                sign_active_lock_last_seen, sign_ready_type, sign_ready_expire);
    }

    public SignState withSignActiveLockSign(String value) {
        return new SignState(sign_active_cooldown_end, sign_active_max_cooldown, value == null ? "" : value,
                sign_active_lock_end, sign_active_reduction_pool, sign_active_lock_grace_end,
                sign_active_lock_played, sign_active_lock_last_seen, sign_ready_type, sign_ready_expire);
    }

    public SignState withSignActiveLockEnd(long value) {
        return new SignState(sign_active_cooldown_end, sign_active_max_cooldown, sign_active_lock_sign, value,
                sign_active_reduction_pool, sign_active_lock_grace_end, sign_active_lock_played,
                sign_active_lock_last_seen, sign_ready_type, sign_ready_expire);
    }

    public SignState withSignActiveReductionPool(long value) {
        return new SignState(sign_active_cooldown_end, sign_active_max_cooldown, sign_active_lock_sign,
                sign_active_lock_end, value, sign_active_lock_grace_end, sign_active_lock_played,
                sign_active_lock_last_seen, sign_ready_type, sign_ready_expire);
    }

    public SignState withSignActiveLockGraceEnd(long value) {
        return new SignState(sign_active_cooldown_end, sign_active_max_cooldown, sign_active_lock_sign,
                sign_active_lock_end, sign_active_reduction_pool, value, sign_active_lock_played,
                sign_active_lock_last_seen, sign_ready_type, sign_ready_expire);
    }

    public SignState withSignActiveLockPlayed(boolean value) {
        return new SignState(sign_active_cooldown_end, sign_active_max_cooldown, sign_active_lock_sign,
                sign_active_lock_end, sign_active_reduction_pool, sign_active_lock_grace_end, value,
                sign_active_lock_last_seen, sign_ready_type, sign_ready_expire);
    }

    public SignState withSignActiveLockLastSeen(long value) {
        return new SignState(sign_active_cooldown_end, sign_active_max_cooldown, sign_active_lock_sign,
                sign_active_lock_end, sign_active_reduction_pool, sign_active_lock_grace_end,
                sign_active_lock_played, value, sign_ready_type, sign_ready_expire);
    }

    public SignState withSignReadyType(int value) {
        return new SignState(sign_active_cooldown_end, sign_active_max_cooldown, sign_active_lock_sign,
                sign_active_lock_end, sign_active_reduction_pool, sign_active_lock_grace_end,
                sign_active_lock_played, sign_active_lock_last_seen, value, sign_ready_expire);
    }

    public SignState withSignReadyExpire(long value) {
        return new SignState(sign_active_cooldown_end, sign_active_max_cooldown, sign_active_lock_sign,
                sign_active_lock_end, sign_active_reduction_pool, sign_active_lock_grace_end,
                sign_active_lock_played, sign_active_lock_last_seen, sign_ready_type, value);
    }
}
