package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.chip.AirbagChipItem;
import com.merlinkitsune.astral_dice.item.RenShieldManager;
import com.merlinkitsune.astral_dice.combat.DiceCombatEvents;
import com.merlinkitsune.astral_dice.item.chip.WhetstoneChipItem;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDamageEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDeathEvent;
import com.merlinkitsune.astral_dice.platform.event.EventPriority;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;

/**
 * 筹码受击侧钩子(最终伤害阶段):
 * <ul>
 *   <li>安全气囊:受到致命伤害时消耗 6 点充能使本次伤害无效(1:00 冷却);</li>
 *   <li>磨刀石:低血量时受到伤害 -2;血量大于 1 点时伤害不超过剩余血量(不可被一次击倒)。</li>
 * </ul>
 *
 * 统一在 {@link LivingDamageEvent} 处理:生命扣减尚未发生,可直接改写伤害值(设置 0 即完全无效化);
 * <b>但本线该事件在护甲/吸收之前派发</b>(fabric 锚点见下),载荷是 {@code actuallyHurt} 的**原始入参**,
 * 不是「护甲与黄心结算之后」的最终值 —— 以「最终伤害」为口径的判定都要按此重看。
 *
 * <p><b>安全气囊对「无视无敌」的伤害源同样生效(2026-09-15 用户裁决)</b>:不再按
 * {@code DamageTypeTags.BYPASSES_INVULNERABILITY} 排除。本线依据:事件由 Puzzles Lib 8.1.33 的
 * {@code LivingEntityFabricMixin#actuallyHurt}({@code @Inject(method="actuallyHurt", at=@At("HEAD"))})
 * 派发,该处理器先判 {@code isInvulnerableTo}(intermediary {@code method_5679})再派发,而它对
 * bypasses_invulnerability 的伤害源为假 ⇒ 事件照常派发;改 0 后 {@code actuallyHurt} 在偏移
 * 107-113({@code fload_2};{@code fconst_0};{@code fcmpl};{@code ifne 114} 不成立即 {@code return})跳过扣血,
 * 与普通致命伤等价,故 {@code /kill} 与其它模组的真伤都会在气囊前止步。
 * <b>虚空伤害(out_of_world)例外</b>(用户裁决):虚空击杀优先级最高,气囊与磨刀石一律不阻止
 * (见 {@link #onLivingDamage})。另有 {@link #onLivingDeath} 兜底:绕过伤害管线直接致死时仍由气囊接管。
 *
 * <p><b>保命优先级(安全气囊 &gt; 不死图腾 &gt; 末影骰子)</b>:本处理器使用 {@link EventPriority#LOWEST},
 * 即排在所有同事件伤害修正(雨中/水下放大、狂暴/虚弱印记加成等)之后执行,保证按<b>最终伤害</b>
 * 判定是否致命;而原版不死图腾的保命判定(checkTotemDeathProtection)与末影骰子的伪图腾
 * (EnderDiceHandler,监听死亡事件)都发生在本次伤害落地之后 —— 只要安全气囊成功无效化伤害
 * (生命不会降到 0),两者均不会触发,气囊恒为第一顺位。死亡事件侧的兜底用
 * {@link EventPriority#HIGHEST},同样早于末影骰子的死亡处理(默认优先级)。
 */
public final class ChipDamageHandler {

    private ChipDamageHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;

        // N2 收口(批次 D-10):本处理器有 4 个早退点,若把「本击受击快照失效」写在各出口上,
        // 早晚漏一个 ⇒ 统一放进 finally,与 forge-1.20.1 同名文件(:49-67)同形。
        // 早退点全表(行号为本次改动后的本文件):①伤害<=0 ②护盾剩余>0 ③虚空伤害 ④安全气囊生效;
        // 另加「正常走完」一条 —— 五条出口都会经过 finally。
        try {
            applyChipMitigation(player, event);
        } finally {
            // 与 forge 的差别:forge 这里还有 PaparaSignItem.settleBiteHeal(event);
            // 本线的咬合回血走独立的 @SubscribeEvent(priority=LOWEST) onPaparaBiteHurtHeal,
            // 不挂在本 finally 内(见 B20B 报告)。
            DiceCombatEvents.compensateAbsorption(player, event.getAmount());
        }
    }

    /**
     * 筹码受击侧主体(原 onLivingDamage 的内联体逐行搬出,批次 D-10)。
     * 早退点全部落在调用方的 try 内 ⇒ 由 finally 统一收口。
     */
    private static void applyChipMitigation(Player player, LivingDamageEvent event) {
        float damage = event.getAmount();
        if (damage <= 0.0F) return;

        // 鼠鼠护盾(ren)由 item/RenShieldManager#onRenShieldAbsorb 在 LivingHurtEvent 阶段钳制 ⇒ 本事件
        // (吸收之后)拿到的伤害已经是 0;此处保留同一条守卫只为三线同形(将来若换锚点,顺序同样无关)。
        if (RenShieldManager.remainingShieldAbsorption(player) > 0.0F) return;

        // 虚空击杀优先级最高(用户裁决):掉入虚空的 out_of_world 伤害不得被保命阻止,
        // 故整段保命处理(安全气囊 + 磨刀石"不可被一次击倒"的不可击杀保护)直接跳过。
        // 只按伤害类型排除 out_of_world:bypasses_invulnerability 标签同时含 generic_kill(/kill),
        // 一律查标签会把 /kill 也变成不可救(用户裁决:/kill 维持现状 = 气囊照旧能救)。
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;

        // 安全气囊:致命伤害(伤害 ≥ 当前血量)时消耗 6 点充能使本次伤害无效。
        // 不排除「无视无敌」(bypasses_invulnerability)的伤害源:气囊必须始终有效(见类注释)。
        // A6 口径(致命判定按**吸收(黄心)之后**)在本线**并不天然成立**:下面的 damage 是
        // actuallyHurt 的原始入参 —— 事件由 Puzzles 的 LivingEntityFabricMixin#actuallyHurt
        // (@Inject(method="actuallyHurt", at=@At("HEAD")))在偏移 0 派发,而护甲(偏移 12
        // getDamageAfterArmorAbsorb)、魔抗(19 getDamageAfterMagicAbsorb)、吸收(25-45)全在其后
        // ⇒ 此处与生命值比较的是护甲/吸收**之前**的原始值,本线不做换算。
        // ⚠️ 本线平台注(批次 D-10 取证):本线该事件由 Puzzles 的 LIVING_HURT
        //    回调桥接(platform/PuzzlesBridges.java:100-112),注入点在 actuallyHurt 的**方法入口**,
        //    载荷是护甲 / 抗性 / 吸收**之前**的原始入参(字节码:fuzs.puzzleslib.mixin.LivingEntityFabricMixin
        //    的 actuallyHurt 处理器先判 isInvulnerableTo 再派发,偏移 0-54)⇒「致命判定天然按吸收之后」
        //    在本线**并不成立**;此处沿用本线既有口径(与骰战两条写终值路径同一口径),
        //    该平台差异的完整取证与后续处理建议登记在 B20B 报告。
        if (damage >= player.getHealth() && AirbagChipItem.tryNegateFatal(player, event.getSource())) {
            event.setAmount(0.0F);
            return;
        }

        // 磨刀石:低血量减伤 + 血量 > 1 时不可被一次伤害击倒
        // 「不可削减」伤害段（王之力代价 card_cost / 狂暴反噬 unreducible_damage，2026-10-02 用户裁决）：
        // 跳过本模组的**固定点数减伤**（下面磨刀石 -2、怪力侦探立牌 -N）—— 但**保命**机制照常：
        // 安全气囊在上面已处理（致命即 return），磨刀石「不可被一次击倒」在下面仍然生效。
        boolean unreducible = event.getSource().is(com.merlinkitsune.astral_dice.damage.ModDamageTypes.CARD_COST)
                || event.getSource().is(com.merlinkitsune.astral_dice.damage.ModDamageTypes.UNREDUCIBLE_DAMAGE);

        float modified = WhetstoneChipItem.modifyIncomingDamage(player, damage, !unreducible);
        if (modified != damage) {
            event.setAmount(modified);
        }

        // 怪力侦探立牌(sherry):「推理时间」每层受到伤害 −1;外加「挚友守护」(同队有装备人偶师立牌的
        // 玩家时再 −1)。两者都是**固定点数减法**,与磨刀石同阶段(LOWEST = 最终伤害阶段)结算;
        // 刻意**不**附带"不可致死保护"—— 那是磨刀石/安全气囊的专属口径,本立牌只做纯减伤。
        // ⚠️ 与骰战的先后顺序(2026-09-26 固化):骰战在 LivingDamageEvent(@NORMAL)自算并
        //    setAmount(骰战层下限 1 点),本处理器 @LOWEST 在其**之后**执行 ⇒ 固定值减伤作用在
        //    「骰战最终伤害」之上,且 `Math.max(0, …)` **允许扣到 0**(用户裁决:算完最终伤害再扣除)。
        int sherryCut = com.merlinkitsune.astral_dice.item.sign.SherrySignItem.getLayers(player);
        float guardian = com.merlinkitsune.astral_dice.item.sign.SherrySignItem.guardianReductionFor(player);
        float totalCut = sherryCut + guardian;
        if (!unreducible && totalCut > 0.0F) {
            float current = event.getAmount();
            float capped = Math.max(0.0F, current - totalCut);
            if (capped != current) {
                event.setAmount(capped);
            }
        }
        // 超天酱立牌(kangel)被动「小天使请安」+ 地雷女立牌(ame)备注「一心同体」(2026-10-16):
        // 带「粉丝」(kangel_fan)的目标攻击佩戴者(或与其一心同体的地雷女佩戴者（队内须有超天酱立牌佩戴者）)时,
        // 造成的伤害 -1。⚠️ 判据只认**目标标记** kangel_fan —— 佩戴者自己的计数指示器是另一个 id
        // (kangel_fan_indicator,2026-10-17 拆分),绝不参与本段,否则超天酱互殴会互相误判 -1。
        // 与上面 sherry 的段落**同一口径**:固定点数减法、与磨刀石同阶段(LOWEST = 最终伤害
        // 阶段)、`Math.max(0, …)` **允许扣到 0**、**不可减免**伤害类型(CARD_COST / UNREDUCIBLE_DAMAGE)
        // 一律跳过(「不可削减」段已在上面算好 unreducible);刻意**不**附带"不可致死保护"。
        if (!unreducible && com.merlinkitsune.astral_dice.item.sign.KangelSignItem
                .fanMitigationActive(player, event.getSource())) {
            float current = event.getAmount();
            float capped = Math.max(0.0F, current - 1.0F);
            if (capped != current) {
                event.setAmount(capped);
            }
        }
    }

    /**
     * 安全气囊兜底:能走到死亡事件,说明这次致死<b>没有</b>经过 {@link LivingDamageEvent}
     * (例如外部直接把血量清零、或原版 {@code LivingEntity#tick} 因血量 ≤ 0 直接 {@code die()}),
     * 气囊同样必须有效 —— 取消死亡并把血量抬到至少 1(与不死图腾同款做法,但不清效果:
     * 气囊的口径是「本次伤害无效」,不是图腾的净化+回复)。
     * <p>若伤害管线已由上面无效化,则不会产生死亡事件,此处不会重复结算。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (event.isCanceled()) return;
        // 虚空击杀不可被保命阻止(见 onLivingDamage);/kill(generic_kill)维持现状(照旧可救)
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        if (!AirbagChipItem.tryNegateFatal(player, event.getSource())) return;

        event.setCanceled(true);
        player.setHealth(Math.max(1.0F, player.getHealth()));
    }
}
