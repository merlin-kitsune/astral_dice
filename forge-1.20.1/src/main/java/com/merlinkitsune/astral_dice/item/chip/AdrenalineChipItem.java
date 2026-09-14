package com.merlinkitsune.astral_dice.item.chip;
import com.merlinkitsune.astral_dice.combat.HostileTargets;
import com.merlinkitsune.astral_dice.item.CuriosCompat;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 肾上腺素筹码(一般/高效):生命值为 50% 或更低时,攻击力/防御力 +3/+8。
 * - 攻击力经骰战攻击修饰器注册表(DiceCombatModifiers)计入;
 * - 防御力按「1 防御力 = 2 护甲值」折算为真实护甲(curioTick 维护,见防御力折算规范);
 * - 触发加成时被敌方攻击,有 20% 概率闪避单次攻击伤害。
 * - 一般:仅攻防加成(+3,防御按 1 点 = 2 点护甲折算);
 * - 高效额外:触发加成时被敌方攻击,有 20% 概率闪避单次攻击伤害。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class AdrenalineChipItem extends BaseChipItem {
    /** 肾上腺素-一般攻防加成 */
    public static final int BONUS_LOW = 3;
    /** 肾上腺素-高效攻防加成 */
    public static final int BONUS_HIGH = 8;

    private final int bonus;

    public AdrenalineChipItem(Properties properties, int bonus) {
        super(properties);
        this.bonus = bonus;
    }

    // 是否处于触发加成状态(生命值为 50% 或更低)
    // 吸血鬼立牌「汲取」生效期间:以血量条件决定是否生效的效果视为条件通过(无条件触发)
    public static boolean isLowHp(Player player) {
        if (player == null) return false;
        return player.hasEffect(com.merlinkitsune.astral_dice.effect.ModEffects.PAPARA_BITE.get())
                || player.getHealth() <= player.getMaxHealth() / 2.0f;
    }

    public static boolean hasLowEquipped(Player player) {
        return hasCurio(player, ModItems.ADRENALINE_LOW.get());
    }

    public static boolean hasHighEquipped(Player player) {
        return hasCurio(player, ModItems.ADRENALINE_HIGH.get());
    }

    private static boolean hasCurio(Player player, net.minecraft.world.item.Item item) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(item)).isPresent();
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 防御力折算真实护甲(1 防御力 = 2 护甲值):触发加成状态生效,否则移除
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "adrenaline_def_armor" + bonus, isLowHp(player) ? bonus : 0);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.setDefenseArmorBonus(
                player, "adrenaline_def_armor" + bonus, 0);
    }

    // 高效闪避判定:触发加成时,有 20% 概率闪避单次攻击
    private static boolean tryDodge() {
        return ThreadLocalRandom.current().nextInt(100) < 20;
    }

    // 肾上腺素-高效:触发加成时被敌方攻击 → 20% 概率闪避本次攻击
    // (触发条件 50% 血量 + 佩戴高效 + 来源为敌对生物、概率 20%、文案均与旧实现完全一致)
    // 必须在伤害判定最前置处"取消"(LivingAttackEvent)而不是在伤害阶段把伤害改成 0:
    // 否则攻击方 Mob#doHurtTarget 仍会拿到 hurt()==true,继续施加命中附加效果(如尸壳的饥饿)
    // 并播放红屏/屏幕震动/受伤音效。详见 DiceCombatEvents.applyDodgeCancel 的注释。
    @SubscribeEvent
    public static void onAdrenalineDodge(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 平台差异补位:LivingAttackEvent 早于无敌/濒死判定,复刻 1.21.1 的事件阶段
        if (com.merlinkitsune.astral_dice.combat.DiceCombatEvents.isImmuneToDamage(player, event.getSource())) return;
        // 反击链中的伤害不参与闪避判定(结构性递归截断,与 DiceCombatEvents 的守卫一致)
        if (com.merlinkitsune.astral_dice.combat.DiceCombatEvents.isInCounterChain()) return;
        if (!isLowHp(player)) return;
        if (!hasHighEquipped(player)) return;
        // 敌方攻击(来源为敌对生物;排除摔落/火焰等环境伤害)
        if (!(event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)) return;
        if (!HostileTargets.isHostile(attacker)) return;
        if (tryDodge()) {
            com.merlinkitsune.astral_dice.combat.DiceCombatEvents.applyDodgeCancel(event);
            // 枪匠立牌:任意来源的闪避都会尝试获得 1 层弱点识破(每目标一次)
            com.merlinkitsune.astral_dice.item.sign.MosesSignItem.onDodgeCounter(player, attacker);
        }
    }
}
