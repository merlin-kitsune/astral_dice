package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.PlayerEffectProtection;
import com.merlinkitsune.astral_dice.item.sign.SouriSignItem;
import com.merlinkitsune.astral_dice.item.sign.SouriSignManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 10 秒反击状态的唯一权威。
 *
 * <h2>语义</h2>
 * <ul>
 *   <li>反击状态 = {@code ren_counter} 效果，固定 {@link #DURATION_TICKS} = 200 tick（10 秒）；
 *       效果实例只表示状态，不再镜像可激活次数。</li>
 *   <li>可激活次数仍是附件 {@code REN_COUNTER_CHARGES}（游戏大师 1 层；佩戴金鲤时上限 4 层），
 *       但次数本身不再显示图标，也不决定状态时长。</li>
 *   <li>激活必须由一次**近战**命中触发，且攻击者不是玩家 / 女仆；激活时消耗 1 层可激活次数。
 *       状态已存在时不刷新、不重复扣层。</li>
 *   <li>状态期间每次符合条件的近战命中都免费反击一次，不再消耗可激活次数。</li>
 *   <li>金鳞只在状态**真正被激活**（效果从无到有）时由 {@link SouriSignManager#addGoldScale(Player)}
 *       增加；反击命中本身不再加金鳞。</li>
 * </ul>
 */
public final class CounterStateManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("AstralDice/Counter");

    /** 10 秒（200 tick） */
    public static final int DURATION_TICKS = 200;

    /** 车万女仆的实体命名空间；女仆视为友方，不激活、不反击。 */
    private static final String MAID_NAMESPACE = "touhou_little_maid";

    private CounterStateManager() {
    }

    /** 当前是否处于 10 秒反击状态。 */
    public static boolean isActive(Player player) {
        return player != null && player.hasEffect(ModEffects.REN_COUNTER.get());
    }

    /** 剩余状态 tick；未激活返回 0。 */
    public static int remainingTicks(Player player) {
        if (player == null) return 0;
        MobEffectInstance effect = player.getEffect(ModEffects.REN_COUNTER.get());
        return effect == null ? 0 : effect.getDuration();
    }

    /**
     * 消耗 1 层可激活次数并进入 10 秒状态。
     *
     * @return 是否**本次真正激活**（已在状态中、没有层数、客户端/死亡都返回 false）
     */
    public static boolean tryActivateWithCharge(Player player) {
        if (!canActivate(player)) return false;
        if (ModAttachments.getRenCounterCharges(player) <= 0) return false;
        if (!activate(player)) return false;
        ModAttachments.setRenCounterCharges(player,
                Math.max(0, ModAttachments.getRenCounterCharges(player) - 1));
        return true;
    }

    /**
     * 不消耗层数的强制激活（安全气囊的保命触发）。
     * 只在效果从无到有时返回 true；状态已存在时返回 false，但调用方仍可结算当前这一击。
     */
    public static boolean activateForced(Player player) {
        return activate(player);
    }

    private static boolean activate(Player player) {
        if (!canActivate(player)) return false;
        if (isActive(player)) return false;
        PlayerEffectProtection.apply(player, counterInstance());
        if (!isActive(player)) return false;
        // 用户裁决：金鳞只按「成功激活反击状态」增加；反击命中本身不再加。
        boolean souri = SouriSignItem.isEquipped(player);
        if (souri) {
            SouriSignManager.addGoldScale(player);
        }
        LOGGER.info("[Astral Dice][Counter] AP_COUNTER_ACTIVATE player={} souri={} duration={}",
                player.getName().getString(), souri, DURATION_TICKS);
        return true;
    }

    private static boolean canActivate(Player player) {
        return player != null && !player.level().isClientSide() && player.isAlive();
    }

    private static MobEffectInstance counterInstance() {
        return new MobEffectInstance(ModEffects.REN_COUNTER.get(), DURATION_TICKS, 0, false, false, true);
    }

    /**
     * 本次伤害是否为**近战**：归属者与直接制造者必须是同一个活体实体，且不是弹射物 / 爆炸 /
     * 魔法 / 技能类伤害。
     *
     * <p>1.21.1 与 26.1.2 有 {@code IS_PLAYER_ATTACK}，但那是「玩家攻击」而不是「近战」；
     * 这里不能拿它当判据（怪物近战不满足）。三线统一用「直接制造者 == 归属者 + 排除标签/类型」。
     * 本模组的 {@code skill_damage}/{@code card_spell}/{@code true_damage} 都是直接实体为空、
     * 只有击杀归属，天然落在 {@code direct != causing} 之外。
     */
    public static boolean isMeleeDamage(DamageSource source) {
        if (source == null) return false;
        var causing = source.getEntity();
        var direct = source.getDirectEntity();
        if (!(causing instanceof LivingEntity)) return false;
        if (direct != causing) return false;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return false;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        if (source.is(DamageTypes.MAGIC)) return false;
        if (source.is(DamageTypes.INDIRECT_MAGIC)) return false;
        if (source.is(DamageTypes.SONIC_BOOM)) return false;
        if (source.is(DamageTypes.THORNS)) return false;
        // 本模组的技能 / 卡牌 / 真伤 / 额外伤害 / 反击伤害即使将来把 directEntity 改成施放者，
        // 也不能算近战——激活只认原版近战攻击。
        if (source.is(ModDamageTypes.SKILL_DAMAGE)) return false;
        if (source.is(ModDamageTypes.CARD_SPELL)) return false;
        if (source.is(ModDamageTypes.TRUE_DAMAGE)) return false;
        if (source.is(ModDamageTypes.EXTRA_DAMAGE)) return false;
        if (source.is(ModDamageTypes.CARD_COST)) return false;
        if (source.is(ModDamageTypes.UNREDUCIBLE_DAMAGE)) return false;
        if (source.is(ModDamageTypes.DICE_DAMAGE)) return false;
        return true;
    }

    /** 友方角色：玩家与车万女仆。状态只反击这两类之外的目标。 */
    public static boolean isFriendlyTarget(LivingEntity target) {
        if (target == null) return true;
        if (target instanceof Player) return true;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        return id != null && MAID_NAMESPACE.equals(id.getNamespace());
    }
}
