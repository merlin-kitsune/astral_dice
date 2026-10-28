package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.PlayerEffectProtection;

/**
 * 吸血鬼立牌(命名:papara)。
 * 被动:生命值 ≤ 最大生命值一半时,攻击力/防御力 +3(血量高于一半后效果消失,动态判断)。
 * 主动:获得"汲取"效果 3:00,期间攻击时恢复骰神赐福最终伤害的一半、受伤时恢复单次受到伤害的一半(取整)。
 */
// 2026-10-27 复核(N5):受击回血已改为由 event/ChipDamageHandler 的 finally 显式调用 settleBiteHeal,
// 本类不再订阅事件总线 —— 原先的 @Mod.EventBusSubscriber 会变成空注册,已移除。
public class PaparaSignItem extends BaseSignItem {
    public PaparaSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动:获得"汲取"效果 3:00(visible=true 使效果图标在 HUD 正常显示)
        PlayerEffectProtection.apply(player, new MobEffectInstance(ModEffects.PAPARA_BITE.get(), 3600, 0, false, true, true));
        return InteractionResultHolder.success(stack);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 被动:半血或"汲取"期间防御力 +3 → 护甲 +6(动态判断,经 ARMOR 属性生效)
        boolean active = player.getHealth() <= player.getMaxHealth() / 2.0f || player.hasEffect(ModEffects.PAPARA_BITE.get());
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "papara_def_armor", active ? 3 : 0);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(player, "papara_def_armor", 0);
    }

    // 第二批「三态化」:主动施加 "汲取" 3:00 ⇒ 进入锁定(生效中)态;锁定结束才起主动技能冷却
    @Override
    protected boolean startActiveLockOnUse(Player player, long now) {
        net.minecraft.world.effect.MobEffectInstance instance = player.getEffect(ModEffects.PAPARA_BITE.get());
        if (instance == null) return false;
        beginActiveLock(player, "astral_dice:papara_sign", now + instance.getDuration());
        return true;
    }

    // 门控效果实例仍在:效果被外力提前移除时锁定提前结束(硬上界不延长)
    @Override
    protected boolean isGateEffectActive(Player player) {
        return player.hasEffect(ModEffects.PAPARA_BITE.get());
    }

    // 吸血鬼立牌(papara)主动"汲取":受伤时恢复单次受到伤害的一半生命(取整,至少 1 点)
    /**
     * 受击汲取结算(2026-10-27 近战审计 F5):由 {@code event/ChipDamageHandler} 的 finally **显式调用**。
     *
     * <p>原先本方法是自订阅的 {@code @LOWEST} 监听器 —— 与筹码减伤 / 安全气囊同档,先后取决于注册顺序,
     * 回血基数可能读到「减伤之前」或「归零之前」的中间值。挪到金额改动的最后一站之后即可确定。
     */
    public static void settleBiteHeal(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!player.hasEffect(ModEffects.PAPARA_BITE.get())) return;
        // 2026-09-29 用户裁决:只有**敌对目标**的攻击才回血(环境伤害与自伤不算)
        if (!com.merlinkitsune.astral_dice.combat.DiceCombatEvents
                .isHostileAttack(player, event.getSource())) return;
        // 阶段口径(N9,2026-10-27 两线统一):本线 LivingDamageEvent#getAmount() 已是「实际扣红心量」,与 neo 侧 Post 同义。
        // 取证(sources jar,net/minecraft/world/entity/LivingEntity.java#actuallyHurt):
        //   :1669 f1 = max(damageAmount - absorption, 0)(护甲 / 药水 / 吸收均已结算)
        //   → :1680 f1 = ForgeHooks.onLivingDamage(this, source, f1)(即本事件 getAmount())
        //   → :1683 setHealth(getHealth() - f1);事件 javadoc 亦写明「already been applied ... this is FINAL value」。
        // 残留不确定:同档(LOWEST)内仍有 RenShield 钳制等处理器,若它们在本次之后改 amount,读到的是本处理器时点
        // 的值(与仓内既有的「同档顺序不可依赖」一致);不调优先级,保持既有触发面不变。
        // 金额已被归零(气囊 / 护盾吃满 / 磨刀石)时不再回血 —— 否则一记被完全无效化的攻击反而白送生命。
        if (event.getAmount() <= 0.0F) return;
        int heal = Math.max(1, (int) event.getAmount() / 2);
        player.heal(heal);
    }

}
