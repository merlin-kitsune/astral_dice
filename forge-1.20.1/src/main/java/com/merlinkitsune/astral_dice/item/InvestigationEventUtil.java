package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.AstralEventSystem;
import com.merlinkitsune.astral_dice.event.EventTargetCollector;
import com.merlinkitsune.astral_dice.network.ModNetwork.ActionBarMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "调查阶段"事件核心逻辑。
 * 阶段:调查阶段 I / II / III / 真相揭露。由击杀"隐匿调查"目标触发;大侦探立牌可抽取该事件(继承附近秘密侦探立牌玩家的进度,不推进)。
 * 调查阶段属于事件,触发时同样触发调查员立牌被动等事件附加效果。
 */
@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public final class InvestigationEventUtil {
    private InvestigationEventUtil() {
    }

    // 秘密侦探立牌被动:击杀"隐匿调查"目标后触发调查阶段事件(推进施加者进度)
    public static void triggerByKill(Player killer, Player applier, int markLevel) {
        if (killer.level().isClientSide()) return;
        if (applier == null) return;
        int stage = ModAttachments.getInvestigationStage(applier);
        if (killer == applier) {
            // 释放者自己击杀隐匿调查目标:不存在其他攻击者,仅触发一次事件且仅自己获得效果
            applyStageEffects(killer, null, stage, markLevel);
            ModAttachments.setInvestigationStage(applier, Math.min(stage + 1, 4));
            AstralEventSystem.triggerInvestigationEvent(killer);
            sendInvestigationActionBar(killer);
            return;
        }
        applyStageEffects(killer, applier, stage, markLevel);
        // 推进进度(达到真相揭露阶段后永久保持,仅卸下立牌时清除)
        ModAttachments.setInvestigationStage(applier, Math.min(stage + 1, 4));
        AstralEventSystem.triggerInvestigationEvent(killer);
            sendInvestigationActionBar(killer);
    }

    // 应用对应阶段的效果:隐身;阶段 II 及以上施加调查增益(攻击加成在攻击事件中按阶段/目标标记层数结算)
    private static void applyStageEffects(Player self, Player applier, int stage, int markLevel) {
        int duration = switch (stage) {
            case 1 -> 300;   // I: 15 秒
            case 2 -> 400;   // II: 20 秒
            case 3 -> 600;   // III: 30 秒
            default -> 1200; // 真相揭露: 1:00
        };
        List<Player> recipients = new ArrayList<>();
        recipients.add(self);
        if (applier != null && applier != self) {
            recipients.add(applier);
        }
        // 真相揭露:队伍/友方内所有玩家,以及"参与 boss 战"的玩家(附近存在 boss 生物时,周围 32 格内的玩家)
        if (stage >= 4) {
            // 触发者已加入队伍时只影响同队玩家;未加入任何队伍时 collectTeamPlayers 返回全服在线玩家
            for (Player ally : EventTargetCollector.collectTeamPlayers(self)) {
                if (!recipients.contains(ally)) {
                    recipients.add(ally);
                }
            }
            boolean bossNearby = !self.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    self.getBoundingBox().inflate(64), e -> e.isAlive() && BossEntityUtil.isBossEntity(e)).isEmpty();
            if (bossNearby) {
                for (Player p : self.level().getEntitiesOfClass(Player.class,
                        self.getBoundingBox().inflate(32), p -> !recipients.contains(p))) {
                    recipients.add(p);
                }
            }
        }
        for (Player p : recipients) {
            EffectTimerGuard.apply(p, new MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, true));
            // 调查阶段效果:所有受影响的玩家均显示(amplifier = 阶段序号 1=I,2=II,3=III,4=真相揭露)
            p.addEffect(new MobEffectInstance(ModEffects.INVESTIGATION_BONUS.get(), duration, stage, false, false, true));
        }
    }

    private static void sendInvestigationActionBar(Player player) {
        if (player instanceof ServerPlayer sp) {
            ModNetwork.sendToPlayer(sp,
                    new ModNetwork.ActionBarMessage(Component.translatable("msg.astral_dice.investigation_event_triggered")
                            .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    // 击杀"隐匿调查"目标 → 触发调查阶段事件(全局处理,不要求击杀者佩戴秘密侦探立牌)
    @SubscribeEvent
    public static void onUndercoverInvestigationKill(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!target.hasEffect(ModEffects.UNDERCOVER_INVESTIGATION.get())) return;
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        Optional<java.util.UUID> source = ModAttachments.getUndercoverSource(target);
        if (source.isEmpty()) return;
        Player applier = target.level().getPlayerByUUID(source.get());
        if (applier == null) return;
        int markLevel = MarkManager.getLevel(target);
        InvestigationEventUtil.triggerByKill(killer, applier, markLevel);
    }

    // "隐匿调查"被移除(自然到期/死亡/清除)时清空来源,避免残留 UUID 后续误触发
    @SubscribeEvent
    public static void onUndercoverRemoved(MobEffectEvent.Remove event) {
        if (event.isCanceled()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null
                || effect.getEffect() != ModEffects.UNDERCOVER_INVESTIGATION.get()) return;
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        ModAttachments.setUndercoverSource(entity, Optional.empty());
    }

}
