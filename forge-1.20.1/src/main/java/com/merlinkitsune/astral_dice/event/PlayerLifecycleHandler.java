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
public class PlayerLifecycleHandler {
    // 玩家死亡:移除全部治愈(清零点数并结束"治愈"效果)与骰神赐福效果,防止死亡残留
    @SubscribeEvent
    public static void onPlayerDeathClearEffects(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 不死图腾等取消死亡:不视为死亡,不执行任何清理
        if (event.isCanceled()) return;
        HealingManager.clear(player);
        // 计时器守卫:清空效果结束时刻记录,防止死亡后守卫重新施加效果
        EffectTimerGuard.clear(player);
        // 死亡时统一重置效果相关状态,避免效果被清除后附件残留
        ModAttachments.setDefenseCardConsumedThisBlessing(player, false);
        ModAttachments.setSignReadyType(player, 0);
        ModAttachments.setSignReadyExpire(player, 0);
        ModAttachments.setFenCleavePending(player, false);
        ModAttachments.setFenCleaveActive(player, false);
        ModAttachments.setKomachiExtraPlays(player, 0);
        ModAttachments.setMagicQuiverTracking(player, false);
        ModAttachments.setMagicQuiverFirstCard(player, "");
        ModAttachments.setMagicQuiverCooldownEnd(player, 0);
        ModAttachments.setFateActiveUntil(player, 0);
        ModAttachments.setStarCoinHammerBonus(player, 0);
        ModAttachments.setCursedSwordBonus(player, 0);
        ModAttachments.setCursedSwordBlessingTriggered(player, false);
        ModAttachments.setCandyChipPlayBonusActive(player, false);
        ModAttachments.setSatellitePlayBonusActive(player, false);
        ModAttachments.setSatelliteGiveCooldownEnd(player, 0);
        ModAttachments.setNancyLuPassiveType(player, 0);
        ModAttachments.setNancyLuActiveBonus(player, 0);
        ModAttachments.setNancyLuActiveBonusUntil(player, 0);
        ModAttachments.setNancyLuInvulnerableUntil(player, 0);
        ModAttachments.setNancyLuHiddenUntil(player, 0);
        ModAttachments.setNancyLuEnderPearlImmuneUntil(player, 0);
        player.setInvulnerable(false);
        player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        player.removeEffect(ModEffects.NANCY_LU_HACK.get());
        player.removeEffect(ModEffects.BLUE_CURSE.get());
        // 秘密侦探:死亡保留调查阶段进度(仅卸牌时清除)
        // 效果牌出牌相关计数复位
        ModAttachments.setEffectCardPlayCount(player, 0);
        ModAttachments.setEffectCardCooldownEnd(player, 0);
        ModAttachments.setKomachiUseCount(player, 0);
        ModAttachments.setMagicTomeUseCount(player, 0);
        // 效果牌伤害加成(忍者立牌 KomachiDamageBonus/调查员立牌 RinPages)死亡保留,不清除
        ModAttachments.setDiceCurseRatio(player, 1.0f);
        // 护法立牌:死亡时丢失全部"剑气"层数(死亡时刻即清除装备中的立牌数据,不受 KeepInventory 影响)
        com.merlinkitsune.astral_dice.item.CuriosCompat.getCuriosInventory(player).ifPresent(handler -> {
            var misaki = handler.findFirstCurio(
                    s -> s.is(com.merlinkitsune.astral_dice.item.ModItems.MISAKI_SIGN.get()));
            if (misaki.isPresent()) {
                ModDataComponents.MISAKI_SIGN_STACKS.set(misaki.get().stack(), 0);
            }
        });
        player.removeEffect(ModEffects.DICE_BLESSING.get());
        player.removeEffect(ModEffects.HAIQING_READY.get());
        player.removeEffect(ModEffects.BONNIE_READY.get());
        player.removeEffect(ModEffects.INVESTIGATION_BONUS.get());
        player.removeEffect(ModEffects.FATE_GUIDANCE.get());
        player.removeEffect(ModEffects.FEN_FRENZY.get());
        player.removeEffect(ModEffects.PAPARA_BITE.get());
        player.removeEffect(ModEffects.KOMACHI_COUNT.get());
        player.removeEffect(ModEffects.MAGIC_TOME_COUNT.get());
    }

    // 玩家退出/重新登录:清除骰神赐福效果(防止退出后重进仍保留战斗状态)
    @SubscribeEvent
    public static void onPlayerLoggedInClearDiceBlessing(
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (player.level().isClientSide()) return;
        ModAttachments.setDefenseCardConsumedThisBlessing(player, false);
        // 计时器守卫:清空效果结束时刻记录,避免重登后守卫重新施加旧效果
        EffectTimerGuard.clear(player);
        ModEffectRemoval.remove(player, ModEffects.DICE_BLESSING.get());
        // 重连后刷新治愈体系(上限收缩/效果显示;赐福边沿 prev 标记初始 false,不会误触发减半)
        HealingManager.tick(player);
    }

    // 死亡重生:刷新治愈体系(上限收缩/效果显示)
    @SubscribeEvent
    public static void onPlayerRespawnMedkit(
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (player.level().isClientSide()) return;
        HealingManager.tick(player);
    }

}
