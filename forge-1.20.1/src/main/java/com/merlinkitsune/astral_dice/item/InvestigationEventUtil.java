package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.AstralEventSystem;
import com.merlinkitsune.astral_dice.network.ModNetwork.ActionBarMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * "调查阶段"事件核心逻辑。
 * 阶段:调查阶段 I / II / III / 真相揭露。由击杀"隐匿调查"目标触发;大侦探立牌可抽取该事件(继承附近秘密侦探立牌玩家的进度,不推进)。
 * 调查阶段属于事件,触发时同样触发调查员立牌被动等事件附加效果。
 */
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
        // 真相揭露:同队伍内所有玩家,以及"参与 boss 战"的玩家(附近存在 boss 生物时,周围 32 格内的玩家)
        if (stage >= 4) {
            if (self.getTeam() != null) {
                for (Player p : self.level().players()) {
                    if (p.getTeam() == self.getTeam() && !recipients.contains(p)) {
                        recipients.add(p);
                    }
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

}
