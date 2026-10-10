package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 大侦探立牌主动技能：<b>关闭事件选择界面</b>（服务端 → 客户端，S2C）。
 *
 * <p>由服务端在「已确认」与「强制触发第一个选项」（超时 / 死亡 / 登出）两条路径上发出；
 * 带 token 是为了避免误关玩家后来打开的**其它**界面（仅当当前界面就是同一 token 的选择界面时才关）。
 */
public record EventChoiceClosePayload(int token) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EventChoiceClosePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "event_choice_close"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, EventChoiceClosePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, EventChoiceClosePayload::token, EventChoiceClosePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：当前界面是本 token 的选择界面才关闭。
     *
     * <p>⚠️ 本类**双端都会加载**（NeoForge 注册阶段即触碰），故关闭逻辑必须经
     * {@code client/EventChoiceClientHandler} 转发：本方法体里**不得**出现
     * {@code Minecraft} / {@code EventChoiceScreen} 等客户端专属类型的 direct reference，
     * 否则专服会在 RuntimeDistCleaner 阶段直接崩服（2026-10-11 实机确证）。详见该 helper 类头。
     */
    public static void handle(EventChoiceClosePayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                com.merlinkitsune.astral_dice.client.EventChoiceClientHandler.closeIfToken(payload.token());
            }
        });
    }
}
