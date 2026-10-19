package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.starengine.combat.PartyRelations;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 手电筒-爆闪筹码(传奇 / 星光)。
 *
 * <ul>
 *   <li><b>首次攻击</b>:攻击一个「**该玩家**未攻击过的目标（玩家×目标判重）」时星光 +1 —— 去重载体 =
 *       **玩家级**附件 {@code flashlight_burst_targets}(逗号分隔 UUID 串,写法照同族的
 *       {@code ModAttachments#FLASHLIGHT_GRANTED_TARGETS} / {@code SHERRY_REASONING_TARGETS} 的字符串集口径);</li>
 *   <li><b>被动</b>:每拥有 {@value #STARLIGHT_PER_DAMAGE} 层星光,攻击伤害 +1
 *       (走额外加伤通道 {@code astral_dice:extra_damage},照「手电筒-强光」先例
 *       的 {@code StarLightManager.get(attacker) / 4} 改为 {@code / 2})。</li>
 * </ul>
 *
 * <p>⚠️ 去重键是 (**攻击者, 目标**):同一目标被 A 打过之后,B 再打**照常**获得星光 ——
 * 旧口径挂在**目标**身上({@code moses_broken_attack_rewarded} 那种目标实体级附件),目标被**任何**玩家首次攻击后即置位,
 * 故多人同佩戴时**后出手者拿不到**(那份目标级附件已随本次改动删除)。
 * 记录上限 {@link #MAX_TRACKED_TARGETS}:登记到上限后**不再发放**(照「手电筒-强光」改版前那份严格口径 ——
 * 发的是**星光货币**,不做先进先出淘汰,否则被淘汰的目标可再次获得、破坏「同一目标仅 +1 层」语义);
 * 记录**不随卸下筹码重置**(与旧目标级附件「粘在目标身上」同寿命),随玩家死亡重生一并归零。
 *
 * <p><b>目标口径 = 敌对</b>({@link PartyRelations#isHostileTo}:队友豁免 + 全局敌对规则,**含被激怒的中立生物**)
 * ⇒ 打被动动物(牛 / 猪 / 村民等)不刷星光 —— 与同族「手电筒-强光 / 诅咒之剑」的触发闸门同一口径。
 *
 * <p>触发落点 = {@code combat/DiceCombatEvents#onLivingDamagePre} 的「玩家直接攻击另一实体」处
 * (与电磁炮 {@code RailgunChipItem.onAttack} 同一分段,位于「骰神赐福 / 近战武器」闸门**之前** ⇒
 * 不要求触发赐福、不要求主手持近战武器,但**要求直接伤害来源是该玩家本人**,远程弹射物不计)。
 */
public class FlashlightBurstChipItem extends BaseChipItem {
    /** 首次攻击「新目标」时获得的星光层数。 */
    public static final int STARLIGHT_PER_FIRST_ATTACK = 1;

    /** 每多少层星光 +1 点攻击伤害。 */
    public static final int STARLIGHT_PER_DAMAGE = 2;

    /**
     * 「该玩家已发放过的目标」记录上限(登记满后**不再发放**)。
     *
     * <p>口径与淘汰策略照「手电筒-强光」改版前的那份严格实现:发的是**星光货币**,不做先进先出淘汰
     * (淘汰会让旧目标重新可获得 ⇒ 破坏「同一目标仅 +1 层」语义),故取「满则停发」。
     */
    public static final int MAX_TRACKED_TARGETS = 256;

    public FlashlightBurstChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴本筹码(总闸门:未佩戴骰子 ⇒ 本件功能一律不生效,与 {@code FlashlightChipItem} 同款)。 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        // 总闸门(骰子装备和卸除调整):未佩戴骰子 ⇒ 本件功能一律不生效(数值保留)
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.FLASHLIGHT_BURST_CHIP.get())).isPresent();
    }

    /**
     * 攻击落点调用(见类注释的落点说明):佩戴者攻击「**该玩家**未攻击过的**敌对**目标」
     * ⇒ +1 星光并把目标登记进**该玩家自己的**集合。
     *
     * <p>三道闸门:① 佩戴且装备骰子({@link #isEquipped});② **目标敌对**({@link PartyRelations#isHostileTo},
     * 与同族「手电筒-强光 / 诅咒之剑」同一口径,含被激怒的中立生物 ⇒ 打被动动物不刷星光);
     * ③ 该 (**玩家, 目标**) 组合是新的({@link #markNewTarget})。
     */
    public static void onAttack(Player attacker, LivingEntity target) {
        if (attacker == null || target == null) return;
        if (attacker.level().isClientSide()) return;
        if (target == attacker) return;
        if (!isEquipped(attacker)) return;
        // 目标口径 = 敌对(与同族先例一致:先取攻击者为视者,队友豁免 + 全局敌对规则;含被激怒的中立生物)
        if (!PartyRelations.isHostileTo(attacker, target)) return;
        if (!markNewTarget(attacker, target)) return;
        StarLightManager.add(attacker, STARLIGHT_PER_FIRST_ATTACK);
    }

    /** 额外加伤点数:每 {@value #STARLIGHT_PER_DAMAGE} 层星光 +1(结算见 {@code DiceCombatModifiers} 额外加伤修饰器)。 */
    public static int extraDamage(Player player) {
        if (!isEquipped(player)) return 0;
        return StarLightManager.get(player) / STARLIGHT_PER_DAMAGE;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  「该玩家已发放过的目标」记录(「玩家 × 目标」去重的唯一判据)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 登记「该目标已向**该玩家**发放过 1 层星光」;返回 {@code true} = **本次是新目标**(调用方据此 +1 星光),
     * {@code false} = 该 (**玩家, 目标**) 组合已登记过、或记录已满(两者都不再发放)。
     *
     * <p>记录键 = 目标的**实体 UUID**(随附件的逗号分隔字符串集持久化);满 {@link #MAX_TRACKED_TARGETS} 时**不再发放**。
     */
    private static boolean markNewTarget(Player player, LivingEntity target) {
        String uuid = target.getUUID().toString();
        List<String> granted = readGrantedTargets(player);
        if (granted.contains(uuid)) return false;
        if (granted.size() >= MAX_TRACKED_TARGETS) return false;
        granted.add(uuid);
        ModAttachments.setFlashlightBurstTargets(player, String.join(",", granted));
        return true;
    }

    private static List<String> readGrantedTargets(Player player) {
        String raw = ModAttachments.getFlashlightBurstTargets(player);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }
}
