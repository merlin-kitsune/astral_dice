package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.client.ActionBarManager;
import com.merlinkitsune.astral_dice.client.DamageNumberStore;
import com.merlinkitsune.astral_dice.client.CardShopClientState;
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
        // 跳数字 → 客户端。⚠️ 载荷自带**冻结的世界坐标**(服务端在命中那一刻取好),
        // 客户端不读实体坐标:见 DamageNumberStore 类头(不跟随目标 + 击杀那一下也不丢数字)。
        registrar.playToClient(
                DamageNumberPayload.TYPE,
                DamageNumberPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        DamageNumberStore.add(payload.entityId(), payload.bonusDamage(), payload.color(),
                                payload.x(), payload.y(), payload.z()))
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
        // 事件效果卡（S2C）：客户端弹卡动画；事件结算由服务端在 40t 后执行（见 EventCardService）。
        registrar.playToClient(
                com.merlinkitsune.astral_dice.network.EventCardPayload.TYPE,
                com.merlinkitsune.astral_dice.network.EventCardPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.merlinkitsune.astral_dice.client.EventCardOverlay.show(
                                payload.eventId(), payload.durationTicks(), payload.tier()))
        );
        registrar.playToServer(
                SignActivatePayload.TYPE,
                SignActivatePayload.STREAM_CODEC,
                SignActivatePayload::handle
        );
        // 通用选卡界面（2026-10-17）:C2S 确认（只带会话 token）
        registrar.playToServer(
                CardChoiceConfirmPayload.TYPE,
                CardChoiceConfirmPayload.STREAM_CODEC,
                CardChoiceConfirmPayload::handle
        );
        // 通用选卡界面（2026-10-17）:S2C token 作用域关闭
        registrar.playToClient(
                CardChoiceClosePayload.TYPE,
                CardChoiceClosePayload.STREAM_CODEC,
                CardChoiceClosePayload::handle
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
        // 卡牌商店:候选玩家名单 → 客户端(打开/刷新转账视图时重发)
        registrar.playToClient(
                CardShopTransferListPayload.TYPE,
                CardShopTransferListPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        CardShopClientState.setTransferCandidates(payload.entries()))
        );
        // 卡牌商店:二级转账界面确认(目标索引 + 金额)
        registrar.playToServer(
                CardShopTransferPayload.TYPE,
                CardShopTransferPayload.STREAM_CODEC,
                CardShopTransferPayload::handle
        );
        // 卡牌商店:拖拽购买(货架索引 + 目标物品栏格索引;targetSlot=-1 ⇒ 双击快速移入)
        registrar.playToServer(
                CardShopBuyPayload.TYPE,
                CardShopBuyPayload.STREAM_CODEC,
                CardShopBuyPayload::handle
        );
        // 卡牌商店:请求被拒信号(余额不足 / 物品栏已满 ⇒ 客户端钱包数字红白闪 3 次)
        registrar.playToClient(
                CardShopDenyPayload.TYPE,
                CardShopDenyPayload.STREAM_CODEC,
                CardShopDenyPayload::handle
        );
        // 大侦探立牌主动技能:事件选择界面(服务端下发 2 个候选事件 -> 客户端打开 Screen)
        registrar.playToClient(
                EventChoiceStartPayload.TYPE,
                EventChoiceStartPayload.STREAM_CODEC,
                EventChoiceStartPayload::handle
        );
        // 大侦探立牌主动技能:玩家在界面里点了确认(会话 token + 选中索引)
        registrar.playToServer(
                EventChoiceConfirmPayload.TYPE,
                EventChoiceConfirmPayload.STREAM_CODEC,
                EventChoiceConfirmPayload::handle
        );
        // 大侦探立牌主动技能:服务端权威关闭选择界面(等待 1 秒后)
        registrar.playToClient(
                EventChoiceClosePayload.TYPE,
                EventChoiceClosePayload.STREAM_CODEC,
                EventChoiceClosePayload::handle
        );
        // 大侦探立牌主动技能:选择界面倒计时同步(服务端权威剩余 tick,剩余秒数变化时下发)
        registrar.playToClient(
                EventChoiceTickPayload.TYPE,
                EventChoiceTickPayload.STREAM_CODEC,
                EventChoiceTickPayload::handle
        );
        // 筹码商店:点确认按钮(候选格索引 0..2;服务端二次校验后扣款发货 —— 唯一购买入口)
        registrar.playToServer(
                ChipShopBuyPayload.TYPE,
                ChipShopBuyPayload.STREAM_CODEC,
                ChipShopBuyPayload::handle
        );
        // 筹码商店:点刷新按钮(消耗 1 次重抽;次数为 0 时服务端兜底拒绝)
        registrar.playToServer(
                ChipShopRefreshPayload.TYPE,
                ChipShopRefreshPayload.STREAM_CODEC,
                ChipShopRefreshPayload::handle
        );
        // 筹码商店:拒绝信号(服务端权威;客户端据 reason 红闪花费文本或 N/N 文本)
        registrar.playToClient(
                ChipShopDenyPayload.TYPE,
                ChipShopDenyPayload.STREAM_CODEC,
                ChipShopDenyPayload::handle
        );
    }
}
