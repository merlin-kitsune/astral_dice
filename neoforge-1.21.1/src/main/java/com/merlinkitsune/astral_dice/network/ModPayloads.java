package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.client.ActionBarManager;
import com.merlinkitsune.astral_dice.client.ClientDamageNumbers;
import com.merlinkitsune.astral_dice.client.TargetSelectionClient;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = AstralDiceMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModPayloads {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
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
                                payload.radius(), payload.durationTicks(), payload.actionId()))
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
