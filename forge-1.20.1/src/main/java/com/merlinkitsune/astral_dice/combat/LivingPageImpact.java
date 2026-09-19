package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.card.EffectCardPeriod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「活体书页」**命中结算**(2026-09-25 重写;飞行部分见
 * {@code event/LivingPageFlightScheduler})。
 *
 * <p>结算顺序(**不可调换**,每一步都有明确理由):
 * <ol>
 *   <li><b>先读</b>目标当前标记层数({@code markBefore}) —— 连续出牌判定的口径是「本次命中施加 1 层**之前**」
 *       的层数(用户裁决 A:从 0 层起需第 4 次命中才首次触发);</li>
 *   <li>算本次伤害 = {@link SpellDamageRegistry#livingPageImpactDamage} = 基础 2 + 调查员已用页数
 *       + 伤害效果牌统一加成(忍者立牌 + 书签);本次使用已在出牌时把页数 +1 ⇒ 该值**自含本次**;</li>
 *   <li>以 {@link ModDamageTypes#cardSpell} **登记为法伤**结算基础值:命中
 *       {@code event/DamageEffectCardHandler} 的作用域判定后,自动跑**完整**法伤修饰器链
 *       (忍术飞镖 +目标标记层数 / 贯穿之铳 +目标防御力 / 紫晶骰子 +d6 / 标记喷罐 onHit 加层 /
 *       魔法箭袋 onHit 触发 / 忍者立牌 + 书签效果牌加成);加成部分仍按既有口径以**独立真伤**结算;</li>
 *   <li>跳命中伤害数字(**法伤绿**,与加成跳字同色;见 {@link #SPELL_DAMAGE_COLOR});</li>
 *   <li>施加本牌自带的 1 层标记(标记类筹码的 onHit 加层照旧额外生效,与旧口径一致);</li>
 *   <li>仅当 {@code markBefore >= 3} 时补记本出牌周期出牌数 +1
 *       ({@link EffectCardPeriod#grantLivingPageCycleBonus},内部已含跨轮保护与全局封顶 9)。</li>
 * </ol>
 *
 * <p><b>为什么用 {@code DiceCombatEvents.aoeProcessing} 窗口</b>:它是本模组「内部波及伤害不进入骰战结算」
 * 的统一既有闸门(定向爆破 / 电击手套 AOE 同款)。书页命中不是玩家的直接攻击(伤害源直接伤害实体为空),
 * 不得触发骰神赐福与骰战结算,否则「使用一张牌」会连带成为一次完整的骰战发起,属未请求的新机制。
 * 该闸门唯一的外部消费点是玩家→玩家的敌对立场记录,而敌对目标选择器永不含玩家 ⇒ 无副作用。
 */
public final class LivingPageImpact {

    /** 基础伤害(所有加成之外的固定值) */
    public static final int BASE_DAMAGE = 2;

    /**
     * 命中伤害数字颜色 = **法伤绿**(与 {@code DamageEffectCardHandler} 的法伤加成跳字、电击手套 AOE 同色 0x7CFC00)。
     *
     * <p>为什么不是普通伤害红 0xFF5555(2026-09-19 用户裁决):本牌命中**整体**登记为法伤
     * ({@code astral_dice:card_spell} 跑完整法伤修饰器链),数值颜色必须与既有法伤口径一致;
     * 跳红字会与紧随其后的绿色加成跳字混色,让人误读成「物理伤害 + 法伤加成」两段伤害。
     */
    public static final int SPELL_DAMAGE_COLOR = 0x7CFC00;

    private LivingPageImpact() {
    }

    /**
     * 结算一次书页命中(仅服务端;抵达判定与失效过滤见 {@code LivingPageFlightScheduler#advance})。
     *
     * <p>即使本次 {@code hurt} 被原版/其它模组完全无效化(hurt 返回 false),仍按「书页已触及目标」
     * 施加标记并参与连续出牌判定 —— 命中与否由**飞行是否抵达**决定,不由伤害是否掉血决定。
     */
    public static void resolve(ServerLevel level, ServerPlayer caster, LivingEntity target) {
        if (level == null || caster == null || target == null || !target.isAlive()) return;
        // 1. ★ 先读标记层数(本次 1 层与标记类筹码的 onHit 加层都在下面之后)
        int markBefore = MarkManager.getLevel(target);
        // 2. 本次伤害(自含本次使用带来的页数)
        int base = SpellDamageRegistry.livingPageImpactDamage(caster);
        if (base < 1) base = 1;
        // 3. 登记为法伤结算(完整修饰器链);闸门见类注释
        DiceCombatEvents.aoeProcessing = true;
        try {
            target.hurt(ModDamageTypes.cardSpell(level, caster), (float) base);
        } finally {
            DiceCombatEvents.aoeProcessing = false;
        }
        // 4. 命中跳字(法伤绿,见 SPELL_DAMAGE_COLOR 的说明)
        com.merlinkitsune.astral_dice.network.ModNetwork.DamageNumberMessage.send(target, base, SPELL_DAMAGE_COLOR);
        // 5. 命中施加 1 层标记
        MarkManager.apply(target);
        // 6. 连续出牌:仅命中前已 ≥3 层标记的目标
        if (markBefore >= 3) {
            EffectCardPeriod.grantLivingPageCycleBonus(caster);
        }
    }
}
