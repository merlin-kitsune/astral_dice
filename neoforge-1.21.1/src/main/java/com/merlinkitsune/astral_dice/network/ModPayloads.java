package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.client.ActionBarManager;
import com.merlinkitsune.starenginelib.client.ClientDamageNumbers;
import com.merlinkitsune.astral_dice.client.TargetSelectionClient;
import com.merlinkitsune.astral_dice.client.EnderDieTotemAnimator;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = AstralDiceMod.MODID, bus = EventBusSubscriber.Bus.MOD)
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
        registrar.playToClient(
                TargetSelectStartPayload.TYPE,
                TargetSelectStartPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        TargetSelectionClient.start(payload.token(), payload.targetType(),
                                payload.radius(), payload.durationTicks(), payload.actionId(), payload.allowSelf(),
                                payload.holdToSelect()))
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
        registrar.playToServer(
                StarCoinWalletPayload.TYPE,
                StarCoinWalletPayload.STREAM_CODEC,
                StarCoinWalletPayload::handle
        );
        // 钱包余额 → 客户端（余额条显示；值变化才发，见 economy/StarCoinBalanceSync）
        registrar.playToClient(
                StarCoinBalancePayload.TYPE,
                StarCoinBalancePayload.STREAM_CODEC,
                StarCoinBalancePayload::handle
        );
        // 鼠鼠护盾可见性 → 客户端（护盾球渲染条件）。⚠️ 原版**不同步** mob effect 给
        // 「本人 + 乘客」以外的玩家 ⇒ 他人客户端 entity.hasEffect(REN_SHIELD) 恒为 false，
        // 故可见性必须走本载荷（见 combat/RenShieldVisibility 类头）。
        registrar.playToClient(
                RenShieldStatePayload.TYPE,
                RenShieldStatePayload.STREAM_CODEC,
                RenShieldStatePayload::handle
        );
    }
}
