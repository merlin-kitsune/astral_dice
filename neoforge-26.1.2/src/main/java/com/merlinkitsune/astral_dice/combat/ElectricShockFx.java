package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.particle.LibraryParticleOptions;
import com.merlinkitsune.astral_dice.particle.LibraryVariants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 电击手套命中的观感（2026-10-22 用户裁决：给命中的目标加 FX）。
 *
 * <p>素材<b>全部复用</b>仓库内「铁-艾尔登法环像素粒子库」（{@code astral_dice:particle_library}，
 * 见 {@code particle/LibraryVariants}），不新增贴图：
 * <ul>
 *   <li>{@link LibraryVariants#COMET_AZUR_FILAMENT} —— 锯齿状蓝色电流，作为「电弧」；</li>
 *   <li>{@link LibraryVariants#GLINTSTONE_SPARK} —— 亮青火花芯，作为「电火花」主体；</li>
 *   <li>{@link LibraryVariants#FROST_SPARK} —— 细碎十字闪，作为「电裂」点綴。</li>
 * </ul>
 * 三者都染成青白色（{@code rgb} 与原贴图本色相乘），保证同一套电光配色、不掺白。
 *
 * <p><b>音效</b>取原版 {@link SoundEvents#LIGHTNING_BOLT_IMPACT}（落雷的「劈啪」冲击段，
 * 不含 {@code LIGHTNING_BOLT_THUNDER} 的雷鸣尾音），并用高音调 + 低音量播放 ⇒
 * 听感是电击而非天雷。该常量在 1.20.1 与 1.21.1 都存在（两线口径一致）。
 */
public final class ElectricShockFx {

    /** 电弧数量（每个受害目标）。 */
    private static final int BOLTS = 2;
    /** 电火花数量（每个受害目标）。 */
    private static final int SPARKS = 7;
    /** 细碎电裂数量（每个受害目标）。 */
    private static final int CRACKLES = 4;

    /** 电光配色（青白）；粒子渲染时与原贴图颜色相乘。 */
    private static final int ELECTRIC_RGB = 0x9BE8FF;
    /** 电裂用的更白一档配色。 */
    private static final int CRACKLE_RGB = 0xE8FBFF;

    private static final float BOLT_SCALE = 1.7F;
    private static final float SPARK_SCALE = 0.9F;
    private static final float CRACKLE_SCALE = 0.7F;

    /** 音效音量（0.5 ⇒ 明显但不会盖过出牌音）。 */
    private static final float SOUND_VOLUME = 0.5F;
    /** 音效音调（1.5 ⇒ 落雷冲击段变短变尖，听感接近电击）。 */
    private static final float SOUND_PITCH = 1.5F;

    private ElectricShockFx() {
    }

    /**
     * 受害目标身上的特效锚点：包围盒高度的中点（躯干），比脚底更贴「被电到」的位置。
     *
     * @return {@code null} 表示受害者无效（调用方直接跳过）
     */
    public static Vec3 victimCenter(LivingEntity victim) {
        if (victim == null) return null;
        return new Vec3(victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ());
    }

    /** 在一个位置撒出电火花（电弧 + 火花 + 电裂）。服务端调用，客户端自动收到。 */
    public static void sparksAt(ServerLevel level, Vec3 at) {
        if (level == null || at == null) return;
        level.sendParticles(
                new LibraryParticleOptions(LibraryVariants.COMET_AZUR_FILAMENT, BOLT_SCALE, ELECTRIC_RGB),
                at.x, at.y, at.z, BOLTS, 0.18D, 0.22D, 0.18D, 0.02D);
        level.sendParticles(
                new LibraryParticleOptions(LibraryVariants.GLINTSTONE_SPARK, SPARK_SCALE, ELECTRIC_RGB),
                at.x, at.y, at.z, SPARKS, 0.30D, 0.30D, 0.30D, 0.08D);
        level.sendParticles(
                new LibraryParticleOptions(LibraryVariants.FROST_SPARK, CRACKLE_SCALE, CRACKLE_RGB),
                at.x, at.y, at.z, CRACKLES, 0.24D, 0.24D, 0.24D, 0.12D);
    }

    /** 在整个电击结算的圆心播一次电击音效（高音调落雷冲击段）。 */
    public static void shockSound(ServerLevel level, Vec3 at) {
        if (level == null || at == null) return;
        SoundPlayback.playAt(level, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SOUND_VOLUME, SOUND_PITCH);
    }
}
