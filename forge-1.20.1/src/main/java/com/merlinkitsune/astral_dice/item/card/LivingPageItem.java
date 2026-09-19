package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.combat.HostileTargets;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.LivingPageFlightScheduler;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 活体书页(专属效果牌):调查员(rin)立牌专属。
 *
 * <p><b>使用(2026-09-25 重写)</b>:目标选择器类效果牌,**仅敌对目标**
 * ({@link TargetType#ENEMY} = 敌对生物 ∪ 已被激怒的中立生物,不含玩家,不可对自己使用)。
 * 走既有「手持即选择」约定:主手手持即自动进入选择会话、**无倒计时**、移出手持即关闭、
 * 未确认不消耗卡牌(见 {@link BaseEffectCardItem#tickHeldSelector});确认目标后:
 * <ol>
 *   <li>调查员已使用数量 +1(永久、无上限;命中伤害基数 = 2 + 该计数);</li>
 *   <li>获得「活体书页」标记效果 1:00 —— 语义已改为「本轮已使用伤害效果牌」标记
 *       (忍术飞镖/贯穿之铳的判据与 {@code EffectCardPeriod} 的出牌锁都依赖它,**时长不变**);</li>
 *   <li>登记一次飞行打击({@link LivingPageFlightScheduler}):书页以箭矢 4/5 的速度飞向目标,
 *       逐 tick 跟踪修正航线、必定命中、可穿透方块;</li>
 *   <li>命中:造成 2 + 调查员已用页数 点伤害(**登记为法伤**,受全部法伤加成影响)并施加 1 层标记;
 *       仅当命中**前**目标已有 ≥3 层标记时,本出牌周期出牌数 +1(封顶 9)
 *       —— 出牌数补记在命中时完成({@code EffectCardPeriod#grantLivingPageCycleBonus}),本方法不写。</li>
 * </ol>
 *
 * <p><b>原有的「效果期间所有远程/魔法伤害增加」被动行为已移除</b>
 * ({@code SpellDamageRegistry} 内的 LIVING_PAGE 修饰器已删除)。
 */
public class LivingPageItem extends BaseEffectCardItem {
    /** 目标选择器动作 id(与效果注册 id 一致;actionbar 技能名键 = `msg.astral_dice.target_select.skill.living_page`) */
    private static final String ACTION_ID = "living_page";

    static {
        // 敌对目标选择器(2026-09-25):仅敌对目标可选,不可对自己使用
        registerSelectorAction(ACTION_ID, TargetType.ENEMY, false);
    }

    public LivingPageItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String cardTypeId() {
        return ACTION_ID;
    }

    @Override
    public String selectorActionId() {
        return ACTION_ID;
    }

    @Override
    protected boolean isExclusive() {
        return true;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        // 专属牌:绑定获得者(发放路径已绑定本人;指令/创造栏等未绑定副本在此兜底,首位使用者即获得者)
        ExclusiveCardUtil.bindIfAbsent(stack, user);
        // 调查员(rin)已使用数量 +1(永久、无上限;用于命中伤害 = 2 + 已用页数);这是累计页数,不是本周期出牌数
        ModAttachments.setRinPages(user, ModAttachments.getRinPages(user) + 1);
        // 「活体书页」标记效果 1:00:本轮已使用伤害效果牌的证据(忍术飞镖/贯穿之铳判据 + 出牌锁的效果待定来源)
        user.addEffect(new MobEffectInstance(ModEffects.LIVING_PAGE.get(), 1200, 0, false, true));
        // 飞行打击:仅敌对目标(选择器已二次校验,此处为防御性兜底;非敌对/自身一律不登记)
        if (level instanceof ServerLevel serverLevel && user instanceof ServerPlayer caster
                && applyTo != null && applyTo != user && HostileTargets.isHostile(applyTo)) {
            LivingPageFlightScheduler.launch(serverLevel, caster, applyTo);
        }
    }
}

