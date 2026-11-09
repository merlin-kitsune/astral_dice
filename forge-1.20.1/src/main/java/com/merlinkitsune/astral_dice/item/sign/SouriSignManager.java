package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.GourdDrinkEffect;
import com.merlinkitsune.astral_dice.effect.ImmortalBrewEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.effect.SouriGoldScaleEffect;
import com.merlinkitsune.astral_dice.entity.SouriDrinkEntity;
import com.merlinkitsune.astral_dice.event.PlayerEffectProtection;
import com.merlinkitsune.astral_dice.item.RenShieldManager;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.starengine.event.ModEffectRemoval;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 金鲤立牌（souri，传奇）的服务端权威管理器：觉醒 / 金鳞 / 金鲤化龙 / 反击层数 / 饮品拾取。
 *
 * <h2>状态真值</h2>
 * <ul>
 *   <li>觉醒 = 玩家附件 {@code SOURI_AWAKENING}（独立计数器，用户 2026-11-09 裁决：不复用蛟龙觉醒）；</li>
 *   <li>金鳞 = 玩家附件 {@code SOURI_GOLD_SCALES}（0..{@link #GOLD_SCALE_MAX}），HUD 由
 *       {@code souri_gold_scale} 效果镜像；</li>
 *   <li>金鲤化龙 = 锁存态效果 {@code souri_dragon}（金鳞首次达到 4 层时授予，卸下立牌时清除）；</li>
 *   <li>反击 = 复用 {@code RenShieldManager} 的层数附件 {@code REN_COUNTER_CHARGES}，
 *       但金鲤在佩戴时把上限放宽到 {@link #COUNTER_MAX}（用户裁决：允许叠加，上限 4，
 *       每次反击消耗 1 层并 +1 金鳞）。</li>
 * </ul>
 *
 * <h2>属性收益</h2>
 * 防御力（每层金鳞 +1）走 {@link DiceCombatModifiers#setDefenseArmorBonus}（固定 id ⇒ 幂等，
 * 折算真实护甲）；攻击力（金鲤化龙后每层金鳞 +1）与葫芦饮/仙酿的定值攻击加成走
 * {@link DiceCombatModifiers} 的实时谓词（见该类内的注册块），本类不重复写属性修饰符。
 *
 * <p><b>1.20.1 平台差异</b>（相对 neoforge-1.21.1 蓝本）：
 * <ul>
 *   <li>{@code ModEffects} 在本线是 {@code RegistryObject<MobEffect>} ⇒ 所有效果引用补 {@code .get()}；</li>
 *   <li>actionbar 走本线 {@code ModNetwork.ActionBarMessage} + {@code GameplayConstants#ACTIONBAR_DURATION_TICKS}；</li>
 *   <li>本线蛟龙立牌没有 {@code addAwakening(Player,int)} 入口 ⇒ 联动加成按蛟龙既有语义就地实现
 *       （见 {@link #addMamushiAwakening}），不新增跨类入口。</li>
 * </ul>
 */
public final class SouriSignManager {

    /** 立牌物品注册 id（调试读数用） */
    public static final String SIGN_ID = "astral_dice:souri_sign";

    /** 觉醒计数器上限（与蛟龙同名上限对齐；觉醒本身不触发形态，仅作资源读数） */
    public static final int AWAKENING_MAX = 8;

    /** 金鳞层数上限（用户需求冻结值） */
    public static final int GOLD_SCALE_MAX = SouriGoldScaleEffect.MAX_STACKS;

    /** 每层金鳞提供的防御力 */
    public static final int DEFENSE_PER_SCALE = 1;

    /** 金鲤化龙后，每层金鳞提供的攻击力 */
    public static final int ATTACK_PER_SCALE = 1;

    /** 金鲤佩戴时的反击层数上限（用户裁决：与金鳞上限对齐） */
    public static final int COUNTER_MAX = 4;

    /** 防御力修饰器的固定 key（幂等覆盖） */
    public static final String DEFENSE_KEY = "souri_gold_scale";

    private SouriSignManager() {
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  觉醒（独立计数器）
    // ══════════════════════════════════════════════════════════════════════════

    public static int getAwakening(Player player) {
        if (player == null) return 0;
        return ModAttachments.getSouriAwakening(player);
    }

    public static void addAwakening(Player player, int delta) {
        if (player == null || player.level().isClientSide() || delta <= 0) return;
        ModAttachments.setSouriAwakening(player,
                Math.min(AWAKENING_MAX, ModAttachments.getSouriAwakening(player) + delta));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  反击（复用 Ren 层数附件，佩戴金鲤时上限放宽到 4）
    // ══════════════════════════════════════════════════════════════════════════

    /** 增加反击层数（仅在佩戴金鲤时由本立牌调用；上限 {@link #COUNTER_MAX}）。 */
    public static void addCounter(Player player, int delta) {
        if (player == null || player.level().isClientSide() || delta <= 0) return;
        int total = Math.min(COUNTER_MAX, ModAttachments.getRenCounterCharges(player) + delta);
        ModAttachments.setRenCounterCharges(player, total);
        RenShieldManager.refreshCounterEffect(player);
    }

    /** 消耗 1 层反击（由 {@code combat/DiceCombatEvents} 的反击出口调用）；返回是否真的消耗。 */
    public static boolean consumeCounter(Player player) {
        if (player == null || player.level().isClientSide()) return false;
        int charges = ModAttachments.getRenCounterCharges(player);
        if (charges <= 0) return false;
        ModAttachments.setRenCounterCharges(player, charges - 1);
        RenShieldManager.refreshCounterEffect(player);
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  金鳞 / 金鲤化龙
    // ══════════════════════════════════════════════════════════════════════════

    public static int getGoldScales(Player player) {
        if (player == null) return 0;
        return Math.min(GOLD_SCALE_MAX, ModAttachments.getSouriGoldScales(player));
    }

    public static boolean hasDragon(Player player) {
        return player != null && player.hasEffect(ModEffects.SOURI_DRAGON.get());
    }

    /**
     * 增加 1 层金鳞（满 {@link #GOLD_SCALE_MAX} 时不再增加：
     * 「达到 4 层后获得金鲤化龙」是锁存态，层数到顶即停）。
     *
     * <p>每次增减后立刻重挂防御力修饰符（固定 id ⇒ 幂等），并刷新 HUD 镜像效果。
     */
    public static void addGoldScale(Player player) {
        if (player == null || player.level().isClientSide() || !player.isAlive()) return;
        int scales = getGoldScales(player);
        if (scales >= GOLD_SCALE_MAX) return;
        scales++;
        ModAttachments.setSouriGoldScales(player, scales);
        refreshGoldScaleHud(player);
        refreshDefense(player);
        if (scales >= GOLD_SCALE_MAX) {
            grantDragon(player);
        }
    }

    /** 重挂防御力（每层金鳞 +{@link #DEFENSE_PER_SCALE}），层数 0 时自动摘除修饰符。 */
    public static void refreshDefense(Player player) {
        if (player == null || player.level().isClientSide()) return;
        DiceCombatModifiers.setDefenseArmorBonus(player, DEFENSE_KEY, getGoldScales(player) * DEFENSE_PER_SCALE);
    }

    /** 把附件层数镜像到 HUD 效果（amplifier = 层数 - 1；层数 0 时摘除图标）。 */
    public static void refreshGoldScaleHud(Player player) {
        if (player == null || player.level().isClientSide()) return;
        int scales = getGoldScales(player);
        if (scales <= 0) {
            if (player.hasEffect(ModEffects.SOURI_GOLD_SCALE.get())) {
                ModEffectRemoval.remove(player, ModEffects.SOURI_GOLD_SCALE.get());
            }
            return;
        }
        MobEffectInstance current = player.getEffect(ModEffects.SOURI_GOLD_SCALE.get());
        if (current == null || current.getAmplifier() != scales - 1) {
            if (current != null) {
                ModEffectRemoval.remove(player, ModEffects.SOURI_GOLD_SCALE.get());
            }
            PlayerEffectProtection.apply(player, new MobEffectInstance(
                    ModEffects.SOURI_GOLD_SCALE.get(), -1, scales - 1, false, false, true));
        }
    }

    /** 授予「金鲤化龙」锁存态，并把本人场上未拾取的葫芦饮全部转为仙酿。 */
    public static void grantDragon(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!player.hasEffect(ModEffects.SOURI_DRAGON.get())) {
            PlayerEffectProtection.apply(player, new MobEffectInstance(
                    ModEffects.SOURI_DRAGON.get(), -1, 0, false, false, true));
        }
        int converted = convertFieldGourds(player);
        if (player instanceof ServerPlayer sp) {
            sendActionBar(sp, "msg.astral_dice.souri_dragon_gained");
            if (converted > 0) {
                sendActionBar(sp, "msg.astral_dice.souri_gourd_converted", converted);
            }
        }
    }

    /** 把场上所有未拾取的【葫芦饮】就地转为【仙酿】（用户文案：场上未拾取的葫芦饮转换）；返回转换数量。 */
    public static int convertFieldGourds(Player trigger) {
        if (trigger == null || trigger.level().isClientSide()) return 0;
        var server = trigger.getServer();
        if (server == null) return 0;
        List<SouriDrinkEntity> doomed = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof SouriDrinkEntity drink
                        && drink.getVariant() == SouriDrinkEntity.Variant.GOURD) {
                    doomed.add(drink);
                }
            }
        }
        for (SouriDrinkEntity drink : doomed) {
            drink.setVariant(SouriDrinkEntity.Variant.BREW);
        }
        return doomed.size();
    }

    /** 卸下立牌 / 立牌离身：清空本立牌的全部状态与属性修饰符（幂等）。 */
    public static void clearAll(Player player) {
        if (player == null || player.level().isClientSide()) return;
        ModAttachments.setSouriAwakening(player, 0);
        ModAttachments.setSouriGoldScales(player, 0);
        ModAttachments.setRenCounterCharges(player, 0);
        RenShieldManager.refreshCounterEffect(player);
        DiceCombatModifiers.setDefenseArmorBonus(player, DEFENSE_KEY, 0);
        if (player.hasEffect(ModEffects.SOURI_GOLD_SCALE.get())) {
            ModEffectRemoval.remove(player, ModEffects.SOURI_GOLD_SCALE.get());
        }
        if (player.hasEffect(ModEffects.SOURI_DRAGON.get())) {
            ModEffectRemoval.remove(player, ModEffects.SOURI_DRAGON.get());
        }
    }

    /** 每 20t 的自愈：死亡/重登/外部清除后按附件真值把防御与 HUD 重新挂上。 */
    public static void resync(Player player) {
        if (player == null || player.level().isClientSide()) return;
        refreshDefense(player);
        refreshGoldScaleHud(player);
        if (getGoldScales(player) >= GOLD_SCALE_MAX && !hasDragon(player)) {
            PlayerEffectProtection.apply(player, new MobEffectInstance(
                    ModEffects.SOURI_DRAGON.get(), -1, 0, false, false, true));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  饮品拾取
    // ══════════════════════════════════════════════════════════════════════════

    /** 拾取方身上已有葫芦饮或仙酿 ⇒ 拒绝拾取（用户需求：一次只能拾取一个）。 */
    public static boolean isDrinkBlocked(LivingEntity entity) {
        return entity != null
                && (entity.hasEffect(ModEffects.GOURD_DRINK.get())
                || entity.hasEffect(ModEffects.IMMORTAL_BREW.get()));
    }

    /**
     * 拾取结算：给拾取方施加对应效果（仙酿额外迅捷 II 2:00 + 立即回复 6 点），
     * 给**立牌持有者（放置者）** +1 层反击，并按联动给**持蛟龙立牌者**加蛟龙觉醒。
     */
    public static void onDrinkPicked(Player picker, SouriDrinkEntity drink) {
        if (picker == null || drink == null || picker.level().isClientSide()) return;
        SouriDrinkEntity.Variant variant = drink.getVariant();
        if (variant == SouriDrinkEntity.Variant.GOURD) {
            PlayerEffectProtection.apply(picker, new MobEffectInstance(
                    ModEffects.GOURD_DRINK.get(), GourdDrinkEffect.DURATION_TICKS, 0, false, false, true));
            sendActionBar(picker, "msg.astral_dice.souri_gourd_drink");
        } else {
            PlayerEffectProtection.apply(picker, new MobEffectInstance(
                    ModEffects.IMMORTAL_BREW.get(), ImmortalBrewEffect.DURATION_TICKS, 0, false, false, true));
            PlayerEffectProtection.apply(picker, new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED, ImmortalBrewEffect.DURATION_TICKS,
                    ImmortalBrewEffect.SPEED_AMPLIFIER, false, false, true));
            picker.heal(ImmortalBrewEffect.PICKUP_HEAL);
            sendActionBar(picker, "msg.astral_dice.souri_immortal_brew");
        }

        // 被动「不醉不归」：任意角色拾取 ⇒ 立牌持有者（放置者）+1 层反击
        UUID ownerId = drink.getOwnerUUID();
        if (ownerId != null) {
            var server = picker.getServer();
            ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(ownerId);
            if (owner != null && SouriSignItem.isEquipped(owner)) {
                addCounter(owner, 1);
            }
        }

        // 联动「留给你的」：拾取者佩戴蛟龙立牌 ⇒ 蛟龙觉醒 +1（葫芦饮）/ +2（仙酿）
        if (MamushiSignItem.isEquipped(picker)) {
            addMamushiAwakening(picker, variant == SouriDrinkEntity.Variant.BREW ? 2 : 1);
        }

        // 白色粒子（拾取反馈）
        if (picker.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD,
                    picker.getX(), picker.getY() + picker.getBbHeight() * 0.5D, picker.getZ(),
                    16, 0.35D, 0.35D, 0.35D, 0.02D);
            level.sendParticles(ParticleTypes.END_ROD,
                    picker.getX(), picker.getY() + picker.getBbHeight() * 0.5D, picker.getZ(),
                    6, 0.25D, 0.25D, 0.25D, 0.0D);
        }
    }

    /**
     * 蛟龙立牌觉醒 +delta（本线没有 {@code addAwakening(Player,int)} 入口，按蛟龙立牌的既有语义
     * 就地实现：已化龙则不再累加；未化龙则累加到 {@link MamushiSignItem#AWAKEN_MAX} 上限，
     * 本次跨过上限时播化龙提示并执行化龙转换）。
     */
    private static void addMamushiAwakening(Player player, int delta) {
        if (player == null || player.level().isClientSide() || delta <= 0) return;
        if (MamushiSignItem.isDragonForm(player)) return;
        int before = MamushiSignItem.getAwakening(player);
        int after = Math.min(MamushiSignItem.AWAKEN_MAX, before + delta);
        MamushiSignItem.setAwakening(player, after);
        if (before < MamushiSignItem.AWAKEN_MAX && after >= MamushiSignItem.AWAKEN_MAX) {
            sendActionBar(player, "msg.astral_dice.mamushi_dragon_form");
            MamushiSignItem.transformToDragon(player);
        }
    }

    public static void sendActionBar(Player player, String key, Object... args) {
        if (!(player instanceof ServerPlayer sp)) return;
        ModNetwork.sendToPlayer(sp, new ModNetwork.ActionBarMessage(
                Component.translatable(key, args), GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }
}
