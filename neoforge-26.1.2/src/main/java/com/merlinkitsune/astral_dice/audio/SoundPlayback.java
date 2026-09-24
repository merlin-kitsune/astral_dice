package com.merlinkitsune.astral_dice.audio;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * 自定义音效的**播放通道**：把「听得到谁」这件事收敛成两个入口，
 * 调用点不再各写一遍 {@code level.playSound}，也不自己拼网络包。
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
 *
 * <p>⚠️ **本线的平台差异**：26.1.2 已**移除** {@code ServerPlayer#playNotifySound}
 * （1.21.1 / 1.20.1 的「只发给本人」便利方法），故 {@link #playTo} 改为手工发
 * {@link ClientboundSoundPacket}（与原版那两线的实现等价：同一个包、seed 取玩家随机数）。
 */
public final class SoundPlayback {

    private SoundPlayback() {
    }

    /** 在世界中播放（附近玩家都能听到）；坐标 = 音源位置。 */
    public static void playAt(Level level, double x, double y, double z, SoundEvent sound) {
        if (level == null || sound == null || level.isClientSide()) return;
        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** 只发给该玩家本人（GUI / 私有反馈），不广播给附近玩家。 */
    public static void playTo(ServerPlayer player, SoundEvent sound) {
        if (player == null || sound == null) return;
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                1.0F, 1.0F,
                player.getRandom().nextLong()));
    }
}
