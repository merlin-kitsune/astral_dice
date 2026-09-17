package com.merlinkitsune.astral_dice.item.sign;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.combat.HostileTargets;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;

import com.merlinkitsune.astral_dice.combat.CardRegistry;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 骇客立牌(命名:nancy_lu)。
 *
 * <p>被动"网络防火墙":
 * - 免疫末影珍珠传送时的伤害;
 * - 骰神赐福结束后,若周围 6 格内没有敌对生物:攻击力 +3 并获得一张随机战斗牌;
 *   否则防御力 +3。被动类型每次刷新覆盖旧类型,不能叠加。
 *
 * <p>主动"远程侵入":
 * - 立即进入完全隐身状态(最多持续 30 秒);
 * - 攻击敌对目标或玩家时解除隐身,并消耗一张随机战斗牌;
 * - 按该牌费用 ×2 提升攻击力,持续 2:00;攻击力加成最低 +2,
 *   主物品栏无战斗牌可消耗时同样获得保底 +2。
 */
@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class NancyLuSignItem extends BaseSignItem {
    public static final int PASSIVE_NONE = 0;
    public static final int PASSIVE_ATTACK = 1;
    public static final int PASSIVE_DEFENSE = 2;
    public static final int PASSIVE_BONUS = 3;
    public static final double PASSIVE_RANGE = 6.0;
    public static final int ACTIVE_DURATION_TICKS = 2400;
    public static final int HIDDEN_DURATION_TICKS = 600;
    public static final int ACTIVE_BONUS_MULTIPLIER = 2;
    public static final int ACTIVE_MIN_BONUS = 2;
    public static final int ENDER_PEARL_IMMUNE_TICKS = 20;

    public NancyLuSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        long now = player.level().getGameTime();

        // 主动完全隐身到期(仅立牌自身授予的隐身到期时才移除,避免误清其他来源的隐身)
        long hiddenUntil = ModAttachments.getNancyLuHiddenUntil(player);
        if (hiddenUntil > 0 && now >= hiddenUntil) {
            ModAttachments.setNancyLuHiddenUntil(player, 0);
            player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        }
        // 主动攻击力加成到期
        if (now >= ModAttachments.getNancyLuActiveBonusUntil(player)) {
            ModAttachments.setNancyLuActiveBonus(player, 0);
            ModAttachments.setNancyLuActiveBonusUntil(player, 0);
            ModEffectRemoval.remove(player, ModEffects.NANCY_LU_HACK.get());
        }
        // 防御力折算为真实护甲(1 防御力 = 2 护甲值;被动类型为防御时护甲 +6)
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "nancy_lu_def_armor", getDefenseBonus(player));
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 仅清除立牌自身授予的状态(附件标记仍有效时),不触碰其他来源的公共数值:
        // 隐身可能由其他模组或原版机制授予,卸载立牌不得一并清除
        if (ModAttachments.getNancyLuHiddenUntil(player) > 0) {
            player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        }
        ModAttachments.setNancyLuPassiveType(player, PASSIVE_NONE);
        ModAttachments.setNancyLuActiveBonus(player, 0);
        ModAttachments.setNancyLuActiveBonusUntil(player, 0);
        ModAttachments.setNancyLuHiddenUntil(player, 0);
        ModAttachments.setNancyLuEnderPearlImmuneUntil(player, 0);
        ModEffectRemoval.remove(player, ModEffects.NANCY_LU_HACK.get());
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(player, "nancy_lu_def_armor", 0);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        long now = level.getGameTime();

        // 立即进入完全隐身状态(最多持续 30 秒)
        // visible=false:不产生药水粒子——1.20.1 的药水粒子颜色由 PotionUtils.getColor
        // 汇总(只统计 isVisible() 的效果实例,全部不可见时返回 0),而
        // LivingEntity.tickEffects 只在颜色 > 0 时生成 ENTITY_EFFECT 粒子;
        // 置 true 时隐身期间自身会持续冒粒子而暴露位置。
        // showIcon=true 只保留 HUD 图标,不影响世界可见性。
        EffectTimerGuard.apply(player, new MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY,
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

    // 隐身状态下攻击敌对目标/玩家时调用:解除隐身,尝试消耗战斗牌并按费用*2提升攻击力
    public static void onAttackWhileHidden(Player player) {
        if (player == null || player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        if (now >= ModAttachments.getNancyLuHiddenUntil(player)) return;
        if (!player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) return;

        // 解除完全隐身
        ModAttachments.setNancyLuHiddenUntil(player, 0);
        player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);

        // 消耗一张随机战斗牌并按费用×2 提升攻击力;攻击力加成最低 +2。
        // 主物品栏无战斗牌可消耗时,同样获得保底 +2 攻击力加成。
        ItemStack consumed = findAndConsumeRandomBattleCard(player);
        int bonus = ACTIVE_MIN_BONUS;
        if (consumed != null) {
            String typeId = CardRegistry.itemToType(consumed);
            int cost = typeId != null ? CardRegistry.cost(typeId, player) : 1;
            bonus = Math.max(ACTIVE_MIN_BONUS, cost * ACTIVE_BONUS_MULTIPLIER);
        }
        ModAttachments.setNancyLuActiveBonus(player, bonus);
        ModAttachments.setNancyLuActiveBonusUntil(player, now + ACTIVE_DURATION_TICKS);
        player.addEffect(new MobEffectInstance(ModEffects.NANCY_LU_HACK.get(),
                ACTIVE_DURATION_TICKS, 0, false, true, true));
    }

    // 主动技能 ActionBar:完全隐身提示(注册到主动技能响应事件)
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.starenginelib.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.NANCY_LU_SIGN.get())) {
            sendSignActionBar(event.getPlayer(), "msg.astral_dice.nancy_lu_active", HIDDEN_DURATION_TICKS / 20);
            event.setHandled();
        }
    }

    // === 被动 ===

    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.NANCY_LU_SIGN.get())).isPresent();
    }

    // 是否处于完全隐身(绝对无法被生物索敌)状态
    public static boolean isHidden(Player player) {
        if (player == null || player.level().isClientSide()) return false;
        if (!isEquipped(player)) return false;
        if (!player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) return false;
        return player.level().getGameTime() < ModAttachments.getNancyLuHiddenUntil(player);
    }

    // 客户端渲染抑制判定:玩家当前是否处于本立牌主动授予的完全隐身状态。
    // 服务端附件 nancy_lu_hidden_until 已加入 synced 键(登录/重生/切维度快照 + 写入即推送),
    // 客户端可读到权威状态;同时要求原版隐身效果仍在(效果被提前清除/技能被解除时立即停止抑制)。
    // 仅供 client/NancyLuClientEvents 抑制渲染使用,不参与任何伤害与数值结算。
    public static boolean isHiddenClient(Player player) {
        if (player == null) return false;
        long hiddenUntil = ModAttachments.getNancyLuHiddenUntil(player);
        if (hiddenUntil <= 0) return false;
        if (!player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) return false;
        return player.level().getGameTime() < hiddenUntil;
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
        if (player.hasEffect(ModEffects.NANCY_LU_HACK.get())) {
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
                e -> HostileTargets.isHostile(player, e) && e.isAlive()).isEmpty();
        if (hostileNearby) {
            ModAttachments.setNancyLuPassiveType(player, PASSIVE_DEFENSE);
        } else {
            ModAttachments.setNancyLuPassiveType(player, PASSIVE_ATTACK);
            RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.BATTLE);
        }
    }

    // === 主动辅助 ===

    // 清除附近已把该玩家设为目标生物的仇恨
    private static void clearNearbyMobTargets(Player player) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(64.0), mob -> mob.getTarget() == player)) {
            mob.setTarget(null);
        }
    }

    private static ItemStack findAndConsumeRandomBattleCard(Player player) {
        List<ItemStack> candidates = new ArrayList<>();
        // 仅主物品栏(平衡调整:不再从末影箱/精妙背包等容器能力中消耗)
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && CardRegistry.itemToType(stack) != null) {
                candidates.add(stack);
            }
        }
        if (candidates.isEmpty()) return null;
        ItemStack chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        chosen.shrink(1);
        return chosen.copy();
    }

    // 骇客立牌:完全隐身状态下攻击敌对目标/玩家 → 解除隐身并触发战斗牌加成
    @SubscribeEvent
    public static void onNancyLuAttackWhileHidden(
            net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!NancyLuSignItem.isEquipped(player)) return;
        net.minecraft.world.entity.Entity target = event.getTarget();
        // 敌对判定统一走带上下文的两参重载(全局规则):只有"非同队且曾主动攻击过本玩家的玩家"才算敌对,
        // 不再"任意玩家都算"(旧写法 `|| target instanceof Player` 会把队友与无关玩家也当作可攻击目标)
        if (!HostileTargets.isHostile(player, target)) return;
        NancyLuSignItem.onAttackWhileHidden(player);
    }


    // 骇客立牌:任何攻击行为(含远程/投掷物/魔法)命中敌对生物或玩家时,同样解除隐身并触发战斗牌加成
    @SubscribeEvent
    public static void onNancyLuAnyAttackWhileHidden(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player == event.getEntity()) return;
        LivingEntity victim = event.getEntity();
        // 同上:两参判定取代旧的 `|| victim instanceof Player`(队友/无关玩家不再算敌对)
        if (!HostileTargets.isHostile(player, victim)) return;
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
    // LivingAttackEvent 由 ForgeHooks.onLivingAttack 在 LivingEntity.hurt 的
    // 第一条语句派发(Forge 47.4.10 LivingEntity.java:1089,先于 isInvulnerableTo
    // 与全部伤害处理),取消后 hurt 直接 return false,于是
    // invulnerableTime/hurtDuration/hurtTime 不被赋值、不 markHurt(无击退同步)、
    // 不 indicateDamage(即不发送 ClientboundHurtAnimationPacket,无红屏与屏幕震动)、
    // 不 playHurtSound(无受伤音效)。旧实现只 setAmount(0),hurt 仍走完整个流程
    // 并播放全部受伤反馈。
    @SubscribeEvent
    public static void onNancyLuEnderPearlDamage(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!NancyLuSignItem.isEquipped(player)) return;
        if (!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) return;
        if (player.level().getGameTime() < ModAttachments.getNancyLuEnderPearlImmuneUntil(player)) {
            event.setCanceled(true);
        }
    }

}
