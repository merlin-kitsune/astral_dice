package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：<b>强拆目标选择会话</b>（S2C，2026-10-29 补齐 D5 的客户端侧收尾）。
 *
 * <p>服务端 {@code TargetSelectionManager#cancel(ServerPlayer, String)} 强拆会话时下发（当前唯一调用方 =
 * 事件动画窗开始时）。带 token 是为了避免误关「已经被新会话顶替」的选择模式 —— 客户端只在 token
 * 与当前活动会话一致时退出（见 {@code client/TargetSelectionClient#forceClose(int, String)}）。
 *
 * <p>本类方法体里**不**出现客户端专属类型（理由同 {@link EventChoiceClosePayload}：payload 类双端都会
 * 加载，客户端逻辑放在 {@code client/} 包内由处理器转发）。客户端退出时**不**回发取消包：服务端会话
 * 此时已经清掉，回包只会多写一条 token_mismatch 告警。
 */
public record TargetSelectClosePayload(int token) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TargetSelectClosePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "target_select_close"));

    public static final StreamCodec<ByteBuf, TargetSelectClosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TargetSelectClosePayload::token,
            TargetSelectClosePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：token 与当前活动会话一致才退出选择模式（见
     * {@code client/TargetSelectionClient#forceClose(int, String)}）。
     *
     * <p>⚠️ 本类**双端都会加载**（注册阶段即触碰），故方法体里不得直引 {@code Minecraft} 等客户端
     * 专属类型 —— 客户端逻辑全部落在 {@code client/} 包内，这里只做 dist 判断后转发（口径同
     * {@link EventChoiceClosePayload}）。
     */
    public static void handle(TargetSelectClosePayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                com.merlinkitsune.astral_dice.client.TargetSelectionClient.forceClose(payload.token(), "server");
            }
        });
    }
}
