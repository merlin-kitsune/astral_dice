package com.merlinkitsune.astral_dice.event;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import com.merlinkitsune.astral_dice.network.ModNetwork;


import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.item.sign.BonnieSignItem;
import com.merlinkitsune.astral_dice.item.BossEntityUtil;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.sign.HaiqingSignItem;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.InvestigationEventUtil;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.item.sign.MisakiSignItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.PadmanSignItem;
import com.merlinkitsune.astral_dice.item.sign.JasmineSignItem;
import com.merlinkitsune.astral_dice.item.sign.LuluSignItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.merlinkitsune.astral_dice.item.chip.StarCoinHammerChipItem;
import com.merlinkitsune.astral_dice.item.chip.BufferShieldChipItem;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.client.KeyBindingSetup;
import com.merlinkitsune.astral_dice.combat.DiceCombatContext;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.sign.FenSignItem;
import com.merlinkitsune.astral_dice.item.card.EffectCardPeriod;
import com.merlinkitsune.astral_dice.item.chip.BankCardUnlimitedChipItem;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.item.chip.CursedSwordChipItem;
import com.merlinkitsune.astral_dice.item.chip.FriendshipBadgeChipItem;
import com.merlinkitsune.astral_dice.item.chip.RevengeHalberdChipItem;
import com.merlinkitsune.astral_dice.item.chip.SatelliteChipItem;
import com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class PlayerTickEvents {
    @SubscribeEvent
    public static void onPlayerTickPre(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (player.level().isClientSide()) return;
        EffectTimerGuard.tick(player);
    }

    // 计时器守卫:本模组自定义效果被成功施加时记录结束时刻(有限时长效果;无限时长效果不记录)

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (player.level().isClientSide()) return;
        // 治愈:每 tick 驱动(内部按 30 秒结算 + 每 tick 刷新效果倒计时)
        HealingManager.tick(player);
        // 美工刀状态效果:装备且满血时显示效果图标,否则移除
        updateCutterEffect(player);
        // 复仇之戟:任意加成触发时显示效果图标,全部消失时移除
        RevengeHalberdChipItem.updateDisplayEffect(player);
        // 复仇之戟:防御力折算为真实护甲(1 防御力 = 2 护甲值)
        RevengeHalberdChipItem.updateArmorBonus(player);
        if (player.tickCount % 20 != 0) return;
        // 事件系统:护甲惩罚到期移除
        ArmorPenaltyHandler.tick(player);
        // 效果牌出牌周期计时
        com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.tick(player);
        // 以毒攻毒:中毒结束后给予隐藏图标的生命恢复 II
        com.merlinkitsune.astral_dice.item.card.FightPoisonWithPoisonCardItem.tick(player);
        // 大当家立牌:1 分钟内没有触发骰神赐福 → 养精蓄锐 +1 层
        com.merlinkitsune.astral_dice.item.sign.FenSignItem.tick(player);

    }

    // 美工刀-初级/锋利状态效果:佩戴对应筹码且生命值 ≥60% 或处于"嘬一口"状态时显示效果图标,否则移除
    private static void updateCutterEffect(Player player) {
        var curios = CuriosCompat.getCuriosInventory(player);
        boolean hasCutter = false;
        boolean hasBlade = false;
        if (curios.isPresent()) {
            hasCutter = curios.get().findFirstCurio(s -> s.is(ModItems.CUTTER_CHIP.get())).isPresent();
            hasBlade = curios.get().findFirstCurio(s -> s.is(ModItems.CUTTER_BLADE_CHIP.get())).isPresent();
        }
        boolean fullHp = player.getHealth() >= player.getMaxHealth() * 0.6f || player.hasEffect(ModEffects.PAPARA_BITE.get());
        // 效果存在且剩余时长充足时不重复施加,避免每 tick 触发效果更新/同步包
        refreshIndicator(player, ModEffects.CUTTER_READY.get(), hasCutter && fullHp);
        refreshIndicator(player, ModEffects.CUTTER_BLADE_READY.get(), hasBlade && fullHp);
    }

    // 显示指示器效果:需要显示且(缺失/即将到期)时施加 5 秒;不需要显示且存在时内部移除
    private static void refreshIndicator(Player player, net.minecraft.world.effect.MobEffect effect,
                                         boolean shouldShow) {
        if (shouldShow) {
            MobEffectInstance existing = player.getEffect(effect);
            if (existing == null || existing.getDuration() <= 20) {
                player.addEffect(new MobEffectInstance(effect, 100, 0, false, true, true));
            }
        } else if (player.hasEffect(effect)) {
            ModEffectRemoval.remove(player, effect);
        }
    }


}
