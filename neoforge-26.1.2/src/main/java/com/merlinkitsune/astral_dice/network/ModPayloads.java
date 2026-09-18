package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.client.ActionBarManager;
import com.merlinkitsune.starenginelib.client.ClientDamageNumbers;
import com.merlinkitsune.astral_dice.client.EnderDieTotemAnimator;
import com.merlinkitsune.astral_dice.client.TargetSelectionClient;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class ModPayloads {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // 版本互通门槛(见 AGENTS.md):注册器的网络版本号 = mod_version 的 major.minor(自动派生,禁止硬编码)。
        // NeoForge 在配置阶段逐通道比较两端版本号,不等即握手失败 → 1.1.X 客户端无法加入 1.2.0 服务端;
        // 同二号位的 1.2.x ↔ 1.2.y 版本号字符串相同,照常放行。
        PayloadRegistrar registrar = event.registrar(VersionGate.interopVersion());
        registrar.playToClient(
                DamageNumberPayload.TYPE,
                DamageNumberPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientDamageNumbers.add(payload.entityId(), payload.bonusDamage(), payload.color()))
        );
        registrar.playToClient(
                ActionBarPayload.TYPE,
                ActionBarPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ActionBarManager.show(payload.message(), payload.durationTicks()))
        );
        registrar.playToClient(
                EnderDieTotemPayload.TYPE,
                EnderDieTotemPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        EnderDieTotemAnimator.play(payload.entityId()))
        );
        registrar.playToServer(
                SignActivatePayload.TYPE,
                SignActivatePayload.STREAM_CODEC,
                SignActivatePayload::handle
        );
        registrar.playToServer(
                OpenCardInventoryPayload.TYPE,
                OpenCardInventoryPayload.STREAM_CODEC,
                OpenCardInventoryPayload::handle
        );
        // === 目标选择器(26.1.2 移植 B1+B2:服务端 + 网络 + 客户端状态机;无渲染 API)===
        // 与 1.21.1 基准 `neoforge-1.21.1/.../network/ModPayloads.java` 第 50-66 行**同序同形**:
        // Start(S→C) + Confirm/Cancel(C→S) 三条通道,处理器逐字一致。
        // 通道注册器版本号仍取 VersionGate.interopVersion()(版本互通门槛,见上方注释与 AGENTS.md)。
        registrar.playToClient(
                TargetSelectStartPayload.TYPE,
                TargetSelectStartPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        TargetSelectionClient.start(payload.token(), payload.targetType(),
                                payload.radius(), payload.durationTicks(), payload.actionId(), payload.allowSelf()))
        );
        registrar.playToServer(
                TargetSelectConfirmPayload.TYPE,
                TargetSelectConfirmPayload.STREAM_CODEC,
                TargetSelectConfirmPayload::handle
        );
        registrar.playToServer(
                TargetSelectCancelPayload.TYPE,
                TargetSelectCancelPayload.STREAM_CODEC,
                TargetSelectCancelPayload::handle
        );
    }
}
