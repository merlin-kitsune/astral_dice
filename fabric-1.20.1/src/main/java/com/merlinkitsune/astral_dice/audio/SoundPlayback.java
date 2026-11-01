package com.merlinkitsune.astral_dice.audio;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * 自定义音效的**播放通道**：把「听得到谁」这件事收敛成两个入口，
 * 调用点不再各写一遍 {@code level.playSound} / {@code playNotifySound}。
 *
 * <ul>
 *   <li>{@link #playAt} —— 世界内动作（用牌 / 命中）：**附近玩家都听得到**，按距离衰减、可 3D 定位；</li>
 *   <li>{@link #playTo} —— 私有反馈（GUI 操作）：**只发给该玩家本人**，不广播。</li>
 * </ul>
 *
 * <p>两者都固定 {@link SoundSource#PLAYERS}（受「玩家」音量滑块控制）与 1.0 音量 / 音调；
 * 需要不同音源或音调时再开重载，不要绕过本类。
 *
 * <p>⚠️ 两个入口都在**服务端**调用（{@code level.isClientSide()} 时 {@link #playAt} 直接返回，
 * 避免同一声音被客户端再播一遍）。
 */
public final class SoundPlayback {

    private SoundPlayback() {
    }

    /** 在世界中播放（附近玩家都能听到）；坐标 = 音源位置，音量 / 音调取默认 1.0 / 1.0。 */
    public static void playAt(Level level, double x, double y, double z, SoundEvent sound) {
        playAt(level, x, y, z, sound, 1.0F, 1.0F);
    }

    /**
     * 在世界中播放（附近玩家都能听到），可指定音量 / 音调。
     *
     * <p>用于「需要压低音量、或靠音调换质感」的一次性世界音效（例：电击手套的落雷劈啪声
     * 用原版 {@code LIGHTNING_BOLT_IMPACT} + 高音调，避免与真的落雷混淆、也不至于一次电击
     * 在多个受害者处叠加成轰鸣）。两者都必须 &gt; 0，否则原版直接丢弃该声音实例。
     */
    public static void playAt(Level level, double x, double y, double z, SoundEvent sound,
                              float volume, float pitch) {
        if (level == null || sound == null || level.isClientSide()) return;
        if (volume <= 0.0F || pitch <= 0.0F) return;
        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** 只发给该玩家本人（GUI / 私有反馈），不广播给附近玩家。 */
    public static void playTo(ServerPlayer player, SoundEvent sound) {
        if (player == null || sound == null) return;
        player.playNotifySound(sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
