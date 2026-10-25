package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.starengine.combat.HostileTargets;
import com.merlinkitsune.starengine.event.ModEffectRemoval;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.neoforged.bus.api.SubscribeEvent;
import com.merlinkitsune.starengine.combat.PartyRelations;
import com.merlinkitsune.astral_dice.event.PlayerEffectProtection;

/**
 * 骇客立牌(命名:nancy_lu)。
 *
 * <p>被动"网络防火墙":
 * - 免疫末影珍珠传送时的伤害;
 * - 骰神赐福结束后,若周围 3 格内没有敌对生物:攻击力 +3 并获得一张随机战斗牌;
 *   否则防御力 +3。被动类型每次刷新覆盖旧类型,不能叠加。
 *
 * <p>主动"远程侵入":
 * - 立即进入完全隐身状态(最多持续 30 秒);
 * - 隐身期间攻击敌对目标并破除隐身时,固定获得 +6 攻击力,持续 2:00;
 * - 按下主动本身不给攻击力加成,隐身自然到期而未攻击同样不给;不消耗任何卡牌。
 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class NancyLuSignItem extends BaseSignItem implements SignHooks.BlessingEnded {
    public static final int PASSIVE_NONE = 0;
    public static final int PASSIVE_ATTACK = 1;
    public static final int PASSIVE_DEFENSE = 2;
    public static final int PASSIVE_BONUS = 3;
    public static final double PASSIVE_RANGE = 3.0;
    public static final int ACTIVE_ATTACK_BONUS = 6;
    public static final int ACTIVE_DURATION_TICKS = 2400;
    public static final int HIDDEN_DURATION_TICKS = 600;
    public static final int ENDER_PEARL_IMMUNE_TICKS = 20;

    public NancyLuSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        long now = player.level().getGameTime();

        // 主动完全隐身到期(仅立牌自身授予的隐身到期时才移除,避免误清其他来源的隐身)。
        // ⚠️ 到期判据必须取**已补偿**的锁定硬上界:触发当刻 sign_active_lock_end == nancy_lu_hidden_until
        // (见 startActiveLockOnUse),但只有硬上界会被 BaseSignItem#tickSignActiveLock 的离线补偿后移
        // ⇒ 若仍按本键判定,跨离线重登的第一拍就会清掉仍有效的隐身实例、把刚补偿好的三态锁定一并打死
        // (该锁定的门控效果正是这个隐身实例)。取 max 保留原语义:锁定已提前结束(硬上界为 0)时回退本键。
        long hiddenUntil = ModAttachments.getNancyLuHiddenUntil(player);
        long hiddenExpiry = Math.max(hiddenUntil, ModAttachments.getSignActiveLockEnd(player));
        if (hiddenUntil > 0 && now >= hiddenExpiry) {
            ModAttachments.setNancyLuHiddenUntil(player, 0);
            PlayerEffectProtection.removeNow(player, net.minecraft.world.effect.MobEffects.INVISIBILITY);
        }
        // 主动攻击力加成到期
        if (now >= ModAttachments.getNancyLuActiveBonusUntil(player)) {
            ModAttachments.setNancyLuActiveBonus(player, 0);
            ModAttachments.setNancyLuActiveBonusUntil(player, 0);
            ModEffectRemoval.remove(player, ModEffects.NANCY_LU_HACK);
        }
        // 防御力折算为真实护甲(1 防御力 = 1÷护甲系数 护甲值;被动类型为防御时护甲 +6)
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "nancy_lu_def_armor", getDefenseBonus(player));
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 仅清除立牌自身授予的状态(附件标记仍有效时),不触碰其他来源的公共数值:
        // 隐身可能由其他模组或原版机制授予,卸载立牌不得一并清除
        if (ModAttachments.getNancyLuHiddenUntil(player) > 0) {
            PlayerEffectProtection.removeNow(player, net.minecraft.world.effect.MobEffects.INVISIBILITY);
        }
        ModAttachments.setNancyLuPassiveType(player, PASSIVE_NONE);
        ModAttachments.setNancyLuActiveBonus(player, 0);
        ModAttachments.setNancyLuActiveBonusUntil(player, 0);
        ModAttachments.setNancyLuHiddenUntil(player, 0);
        ModAttachments.setNancyLuEnderPearlImmuneUntil(player, 0);
        ModEffectRemoval.remove(player, ModEffects.NANCY_LU_HACK);
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(player, "nancy_lu_def_armor", 0);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        long now = level.getGameTime();

        // 立即进入完全隐身状态(最多持续 30 秒)
        // visible=false:不产生药水粒子——1.21.1 的效果粒子由 LivingEntity 的
        // DATA_EFFECT_PARTICLES 承载(updateSynchronizedMobEffectParticles 只收集
        // isVisible() 的效果实例),置 true 时隐身期间自身会持续冒粒子而暴露位置。
        // showIcon=true 只保留 HUD 图标,不影响世界可见性。
        PlayerEffectProtection.apply(player, new MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY,
                HIDDEN_DURATION_TICKS, 0, false, false, true));
        ModAttachments.setNancyLuHiddenUntil(player, now + HIDDEN_DURATION_TICKS);
        // 清除附近已经锁定该玩家的生物目标,确保“绝对无法被生物索敌”
        clearNearbyMobTargets(player);

        return InteractionResultHolder.success(stack);
    }

    // 第二批「三态化」:主动施加"完全隐身"(效果实例 + 附件 nancy_lu_hidden_until 同长 0:30)⇒ 进入锁定态。
    // 硬上界取**本技能自己写入的附件**(而不是回读隐身实例剩余时长)——隐身是原版效果,回读会把
    // 外部来源的隐身药水误算成本技能的计时器
    @Override
    protected boolean startActiveLockOnUse(Player player, long now) {
        long hiddenUntil = ModAttachments.getNancyLuHiddenUntil(player);
        if (hiddenUntil <= now) return false;
        beginActiveLock(player, "astral_dice:nancy_lu_sign", hiddenUntil);
        return true;
    }

    // 门控效果实例仍在:攻击破隐(player.removeEffect)或外力清除时锁定提前结束(硬上界不延长)
    @Override
    protected boolean isGateEffectActive(Player player) {
        return player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
    }

    // 隐身状态下攻击敌对目标时调用:先解除隐身,再固定给 +6 攻击力(不消耗战斗牌)
    public static void onAttackWhileHidden(Player player) {
        if (player == null || player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        // 判据改用**已补偿**的锁定态(硬上界未到 且 隐身效果仍在 = 仍在隐身窗口内):触发当刻两个
        // 截止刻相等,但只有 sign_active_lock_end 会被离线补偿后移 ⇒ 旧写法(now < nancy_lu_hidden_until)
        // 在跨离线重登后立刻判假,隐身仍在却不给"攻击破隐"加成。
        if (!isSignActiveLocked(player)) return;
        if (!player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) return;

        // 解除完全隐身
        ModAttachments.setNancyLuHiddenUntil(player, 0);
        PlayerEffectProtection.removeNow(player, net.minecraft.world.effect.MobEffects.INVISIBILITY);

        // 破隐奖励固定 +6 / 2:00:只有"攻击敌对目标并破除隐身"这一条路径给加成,
        // 按下主动本身与隐身自然到期都不给,也不消耗任何卡牌。
        ModAttachments.setNancyLuActiveBonus(player, ACTIVE_ATTACK_BONUS);
        ModAttachments.setNancyLuActiveBonusUntil(player, now + ACTIVE_DURATION_TICKS);
        PlayerEffectProtection.apply(player, new MobEffectInstance(ModEffects.NANCY_LU_HACK,
                ACTIVE_DURATION_TICKS, 0, false, true, true));
    }

    // 主动技能 ActionBar:完全隐身提示(注册到主动技能响应事件)
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.starengine.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.NANCY_LU_SIGN.get())) {
            sendSignActionBar(event.getPlayer(), "msg.astral_dice.nancy_lu_active", HIDDEN_DURATION_TICKS / 20);
            event.setHandled();
        }
    }

    // === 被动 ===

    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.NANCY_LU_SIGN.get());
    }

    // 是否处于完全隐身(绝对无法被生物索敌)状态
    public static boolean isHidden(Player player) {
        if (player == null || player.level().isClientSide()) return false;
        if (!isEquipped(player)) return false;
        if (!player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) return false;
        // 隐身窗口判据改用**已补偿**的锁定态(理由见 onAttackWhileHidden):效果仍在 + 上界未到
        // 才算"仍在隐身"——否则跨离线重登后,生物会重新索敌一个实际仍在隐身的玩家。
        return isSignActiveLocked(player);
    }

    // 客户端渲染抑制判定:玩家当前是否处于本立牌主动授予的完全隐身状态。
    // 服务端附件 nancy_lu_hidden_until 已 .sync() 到客户端(键非零 = 服务端仍认这个窗口)。
    // **刻意不比较绝对刻**(本轮裁决的 α 方案):该截止刻跨离线后会被服务端 BaseSignItem 的离线补偿
    // 后移,而本键是**未补偿**的原值,客户端又拿不到未同步的 sign_active_lock_end ⇒ 比较绝对刻会在
    // 跨离线重登后立刻判假、本机自身渲染抑制提前失效。改为「键非零 + 原版隐身实例仍在」两个**都已同步
    // 到客户端**的事实:服务端在对称到期刻(onCurioTick 的 max 判据)必清零该键并推送 ⇒「键仍在」等价于
    // 「窗口仍在」;效果被提前解除(攻击破隐/外力清除)时 hasEffect 立假 ⇒ 立即停止抑制,不会多盖。
    // 仅供 client/NancyLuClientEvents 抑制渲染使用,不参与任何伤害与数值结算。
    public static boolean isHiddenClient(Player player) {
        if (player == null) return false;
        return ModAttachments.getNancyLuHiddenUntil(player) > 0
                && player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
    }

    public static int getAttackBonus(Player player) {
        return isEquipped(player) && ModAttachments.getNancyLuPassiveType(player) == PASSIVE_ATTACK
                ? PASSIVE_BONUS : 0;
    }

    public static int getDefenseBonus(Player player) {
        return isEquipped(player) && ModAttachments.getNancyLuPassiveType(player) == PASSIVE_DEFENSE
                ? PASSIVE_BONUS : 0;
    }

    public static int getActiveAttackBonus(Player player) {
        if (!isEquipped(player)) return 0;
        if (player.hasEffect(ModEffects.NANCY_LU_HACK)) {
            return ModAttachments.getNancyLuActiveBonus(player);
        }
        return 0;
    }

    // 骰神赐福结束时调用:刷新被动类型(覆盖旧类型,不能叠加)
    public static void onDiceBlessingEnded(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        boolean hostileNearby = !player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(PASSIVE_RANGE),
                e -> PartyRelations.isHostileTo(player, e) && e.isAlive()).isEmpty();
        if (hostileNearby) {
            ModAttachments.setNancyLuPassiveType(player, PASSIVE_DEFENSE);
        } else {
            ModAttachments.setNancyLuPassiveType(player, PASSIVE_ATTACK);
            RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.BATTLE);
        }
    }

    // === B1 注册式钩子分发入口（实例转发；静态方法保留兼容外部直调） ===

    @Override
    public void handleDiceBlessingEnded(Player player) {
        onDiceBlessingEnded(player);
    }

    // === 主动辅助 ===

    // 清除附近已把该玩家设为目标生物的仇恨
    private static void clearNearbyMobTargets(Player player) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(64.0), mob -> mob.getTarget() == player)) {
            mob.setTarget(null);
        }
    }

    // 骇客立牌:完全隐身状态下攻击敌对目标 → 解除隐身并获得固定攻击力加成
    @SubscribeEvent
    public static void onNancyLuAttackWhileHidden(
            net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!NancyLuSignItem.isEquipped(player)) return;
        net.minecraft.world.entity.Entity target = event.getTarget();
        // 敌对判定统一走带上下文的两参重载(全局规则):只有"非同队且曾主动攻击过本玩家的玩家"才算敌对,
        // 不再"任意玩家都算"(旧写法 `|| target instanceof Player` 会把队友与无关玩家也当作可攻击目标)
        if (!PartyRelations.isHostileTo(player, target)) return;
        NancyLuSignItem.onAttackWhileHidden(player);
    }


    // 骇客立牌:任何攻击行为(含远程/投掷物/魔法)命中敌对目标时,同样解除隐身并获得固定攻击力加成
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onNancyLuAnyAttackWhileHidden(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player == event.getEntity()) return;
        LivingEntity victim = event.getEntity();
        // 同上:两参判定取代旧的 `|| victim instanceof Player`(队友/无关玩家不再算敌对)
        if (!PartyRelations.isHostileTo(player, victim)) return;
        if (!isEquipped(player)) return;
        onAttackWhileHidden(player);
    }

    // 骇客立牌:末影珍珠落地时记录短时免疫窗口,免疫随后的传送摔落伤害
    @SubscribeEvent
    public static void onNancyLuEnderPearlImpact(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl pearl
                && pearl.getOwner() instanceof Player player
                && NancyLuSignItem.isEquipped(player)) {
            ModAttachments.setNancyLuEnderPearlImmuneUntil(player,
                    player.level().getGameTime() + com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem.ENDER_PEARL_IMMUNE_TICKS);
        }
    }


    // 骇客立牌:免疫末影珍珠传送产生的摔落伤害
    // 必须在伤害判定最前置处"取消",而不是把伤害改成 0:
    // LivingIncomingDamageEvent 由 CommonHooks.onEntityIncomingDamage 在
    // LivingEntity.hurt 压入 DamageContainer 后立即派发(NeoForge 21.1
    // LivingEntity.java:1152-1153),取消后 hurt 直接 return false,于是
    // invulnerableTime/hurtDuration/hurtTime 不被赋值、不 broadcastDamageEvent、
    // 不 markHurt(无击退同步)、不 indicateDamage(即不发送
    // ClientboundHurtAnimationPacket,无红屏与屏幕震动)、不 playHurtSound(无受伤音效)。
    // 旧实现只 setNewDamage(0),hurt 仍走完整个流程并播放全部受伤反馈。
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onNancyLuEnderPearlDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!NancyLuSignItem.isEquipped(player)) return;
        if (!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) return;
        if (player.level().getGameTime() < ModAttachments.getNancyLuEnderPearlImmuneUntil(player)) {
            event.setCanceled(true);
        }
    }

}
