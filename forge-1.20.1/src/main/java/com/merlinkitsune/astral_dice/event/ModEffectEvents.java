package com.merlinkitsune.astral_dice.event;
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
public class ModEffectEvents {
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onModEffectRemovalPrevented(MobEffectEvent.Remove event) {
        if (event.getEntity().level().isClientSide()) return;
        // 本模组内部移除(ModEffectRemoval)/计时器守卫的强制移除(时长校正)放行
        if (ModEffectRemoval.isInternal()) return;
        if (EffectTimerGuard.isForcedRemoval()) return;
        // 死亡时允许清除,保证死亡后效果状态能正常重置
        if (event.getEntity().isDeadOrDying()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null) return;
        // 标记携带的发光效果:标记仍存在时同步保留发光(牛奶/effect clear 不得单独清除),
        // 保证发光与标记同寿命——标记自然到期/死亡时两者一起移除(此时标记已不存在,此处自动放行)
        if (effect.getEffect() == net.minecraft.world.effect.MobEffects.GLOWING
                && event.getEntity().hasEffect(ModEffects.MARKED.get())) {
            event.setCanceled(true);
            return;
        }
        String effectId = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect.getEffect()).toString();
        if (effectId != null && effectId.startsWith(AstralDiceMod.MODID + ":")) {
            event.setCanceled(true);
        }
    }

    // 隐匿调查效果移除(目标死亡/被清除):清除来源

    @SubscribeEvent
    public static void onEffectTimerRecord(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || instance.getEffect() == null) return;
        String id = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect()).toString();
        if (id == null || !id.startsWith(AstralDiceMod.MODID + ":")) return;
        EffectTimerGuard.record(player, instance);
    }

    // 计时器守卫:本模组效果被成功移除(未被拦截/非守卫自身/非死亡)时遗忘计时记录,
    // 避免守卫把本模组主动结束的效果(如卸下骇客立牌结束隐身)重新施加回来

    @SubscribeEvent
    public static void onEffectTimerForget(MobEffectEvent.Remove event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.isCanceled()) return;
        if (EffectTimerGuard.isForcedRemoval()) return;
        if (event.getEntity().isDeadOrDying()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || instance.getEffect() == null) return;
        EffectTimerGuard.forget(player, net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect()).toString());
    }


}
