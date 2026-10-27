package com.merlinkitsune.astral_dice.item;


import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ConcealmentEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.AstralEventDispatcher;
import com.merlinkitsune.astral_dice.event.AstralEventIds;
import com.merlinkitsune.astral_dice.event.api.AstralEventTrigger;
import com.merlinkitsune.starengine.event.EventTargetCollector;
import com.merlinkitsune.astral_dice.network.ModNetwork.ActionBarMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.merlinkitsune.starengine.combat.PartyRelations;
/**
 * "调查阶段"事件核心逻辑。
 * 阶段:调查阶段 I / II / III / 真相揭露。仅由击杀"隐匿调查"目标触发(大侦探立牌的 11 项随机事件不包含该事件)。
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
            // 阶级推进改由 AstralEventDispatcher 的阶级闸门负责（仅 INVESTIGATION_KILL，归属施加者）。
            AstralEventDispatcher.trigger(killer, applier, AstralEventIds.INVESTIGATION_STAGE, AstralEventTrigger.INVESTIGATION_KILL);
            sendInvestigationActionBar(killer);
            return;
        }
        applyStageEffects(killer, applier, stage, markLevel);
        // 推进进度(达到真相揭露阶段后永久保持,仅卸下立牌时清除)
        // 阶级推进由 AstralEventDispatcher 的阶级闸门执行（仅 INVESTIGATION_KILL 可提升，归属施加者 applier）。
        AstralEventDispatcher.trigger(killer, applier, AstralEventIds.INVESTIGATION_STAGE, AstralEventTrigger.INVESTIGATION_KILL);
            sendInvestigationActionBar(killer);
    }

    /** 隐匿时长(调查阶段四阶段统一 1:00;2026-09-28 用户裁决) */
    public static final int CONCEALMENT_TICKS = 1200;

    /** 阶段效果的作用半径(格)。2026-10-03 用户裁决:取代旧口径的「仅真相揭露 + 需 boss 附近时 32 格」。 */
    public static final double EVENT_TARGET_RADIUS = 64.0D;

    // 应用对应阶段的效果:隐匿(2026-09-28 用户裁决:取代原版「隐身」);
    // 阶段 II 及以上同时施加调查增益(攻击加成在攻击事件中按阶段/目标标记层数结算)。
    // ⚠️ 四阶段时长**统一为 1:00**:原来 I/II/III 分别是 0:15 / 0:20 / 0:30。
    private static void applyStageEffects(Player self, Player applier, int stage, int markLevel) {
        int duration = CONCEALMENT_TICKS;  // 全线 1:00(2026-09-28 用户裁决)
        List<Player> recipients = new ArrayList<>();
        recipients.add(self);
        if (applier != null && applier != self) {
            recipients.add(applier);
        }
        // 2026-10-03 用户裁决:阶段效果按「队伍 + 64 格」广播(口径见 PartyRelations#collectEventTargets)——
        //   触发者有队伍 = 全队 ∪ 64 格内非队友(同队不重复判定);无队伍 = 全部无队伍玩家 ∪ 64 格内所有玩家。
        //   取代旧口径:仅 stage>=4 才广播、且必须先满足「64 格内存在 boss」才取 32 格内玩家。
        for (Player p : PartyRelations.collectEventTargets(self, EVENT_TARGET_RADIUS)) {
            if (!recipients.contains(p)) {
                recipients.add(p);
            }
        }
        for (Player p : recipients) {
            ConcealmentEffect.apply(p, duration);
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
    //
    // ⚠️ 幂等闸门(2026-10-27 修复「法伤链击杀触发两次」):
    //   平台把 LivingDeathEvent 派发在 LivingEntity#die 的 `!dead` 守卫**之前**
    //   (NeoForge CommonHooks.onLivingDeath / Forge ForgeHooks.onLivingDeath 均为 die() 首行),
    //   而法伤加成走的是「外层伤害事件 Pre 阶段里嵌套 hurt」(DamageEffectCardHandler)——
    //   加成致死时内层 die() 先派发一次,返回后外层 hurt 仍会再 die() 一次 ⇒ 本处理器被调用两次
    //   (调查阶段事件结算两次、阶段 +2、阶段效果与 actionbar 各两遍)。
    //   一次死亡只能算一次:结算完之后**立刻清空** undercover_source,第二次调用就在 isEmpty() 处返回。
    //   清空语义与 onUndercoverRemoved 一致(目标已死,来源不再有意义)。
    //
    //   清空必须放在 triggerByKill **之后**:若本次死亡事件随后被别的处理器取消(die() 会直接 return,
    //   实体其实还活着),提前清空会让这只目标后续的真实击杀再也不能触发调查阶段。
    //   放在 triggerByKill 之后不影响幂等:第二次派发发生在本处理器返回**之后**,那时来源已被清空。
    @SubscribeEvent
    public static void onUndercoverInvestigationKill(LivingDeathEvent event) {
        if (event.isCanceled()) return;
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
        // 幂等闸门:结算完即清空来源,挡住同一死亡的第二次派发(见方法头注释;必须放在结算之后)
        ModAttachments.setUndercoverSource(target, Optional.empty());
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
