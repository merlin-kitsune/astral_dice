package com.merlinkitsune.astral_dice.event;


import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.network.DamageNumberPayload;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.item.sign.BonnieSignItem;
import com.merlinkitsune.starenginelib.item.BossEntityUtil;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.sign.HaiqingSignItem;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.ChargeManager;
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
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;
import vazkii.patchouli.common.item.ItemModBook;
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
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
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
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;

@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class PlayerLifecycleHandler {
    // 玩家死亡:移除全部治愈(清零点数并结束"治愈"效果)与骰神赐福效果,防止死亡残留
    // 优先级必须为 LOWEST(2026-09-15 裁决):保命方(末影骰子/安全气囊)在 NORMAL 取消死亡,
    // 本清理必须晚于它们执行,否则同优先级顺序反转时被救回的玩家仍被按死亡清理(玻璃骰销毁、计数清空)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeathClearEffects(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 不死图腾等取消死亡:不视为死亡,不执行任何清理
        if (event.isCanceled()) return;
        // 充能流派:死亡不丢失充能层数,先暂存等待重生恢复
        ChargeManager.preserveOnDeath(player);
        // 调查员/忍者立牌累计加成:死亡暂存(默认 gamerule 下立牌会因死亡掉落被 Curios 判定"已卸下",
        // 其 clearSignData 会在克隆之前清零这两个键,故必须在此先存下——见 DeathPreservedBonuses)
        com.merlinkitsune.astral_dice.component.DeathPreservedBonuses.preserveOnDeath(player);
        // 玻璃骰子死亡惩罚:丢失玻璃骰子本体及其已装备的全部卡牌(同时收缩筹码栏)
        DiceCurioItem.removeGlassDiceOnDeath(player);
        HealingManager.clear(player);
        // 计时器守卫:清空效果结束时刻记录,防止死亡后守卫重新施加效果(有真实副作用:阻止已移除的效果被重新施加)
        EffectTimerGuard.clear(player);
        // 附件类"计数器/状态"不再逐项写默认值(2026-09-15 用户裁决「S4-C6 清理无效项」):
        // copyOnDeath 只复制 rin_pages、komachi_damage_bonus 与 guide_book_given(见 component/ModAttachments),其余附件键
        // 在重生后的**新实体**上一律回默认值;而本清单唯一真正生效的"死亡被取消"路径已由上面的
        // isCanceled() 早退挡住 —— 即"把附件设为 0/false/空串"在真实死亡路径上是空操作(写了也没人读)。
        // 故原清单的 23 个附件键逐项清零(外加 EffectCardPeriod.clearRoundBonuses —— 它同样只是写 4 个
        // 附件默认值)全部删除;下面保留下来的调用都带有附件之外的真实副作用(静态暂存表 / 物品数据 /
        // 移除 MobEffect / 阻止效果被重新施加)。
        player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
        player.removeEffect(ModEffects.NANCY_LU_HACK);
        player.removeEffect(ModEffects.BLUE_CURSE);
        // 秘密侦探:死亡保留调查阶段进度(仅卸牌时清除)
        // 效果牌出牌轮状态(出牌数/冷却/一次性加成/可口糖果/探天卫星/活体书页本周期累计)、忍者与
        // 魔法秘典计数器、骰咒倍率同样无需在此清理:它们都是附件且不在 copyOnDeath 集合内。
        // 效果牌伤害加成(忍者立牌 KomachiDamageBonus/调查员立牌 RinPages)死亡保留,不清除
        // 护法立牌:死亡时丢失全部"剑气"层数(死亡时刻即清除装备中的立牌数据,不受 KeepInventory 影响)
        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            var misaki = handler.findFirstCurio(
                    s -> s.is(com.merlinkitsune.astral_dice.item.ModItems.MISAKI_SIGN.get()));
            if (misaki.isPresent()) {
                misaki.get().stack().set(
                        com.merlinkitsune.astral_dice.component.ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);
            }
            // 扫地机/上班族立牌:与护法同理——攻防/移动累计值写在**立牌物品数据组件**上,
            // 而死亡掉落走 Curios handleDrops(不回调 onUnequip),没有任何其它清理路径会归零它们
            // (2026-09-15 P5 审计发现),故在这里按 MISAKI 同款方式清除(不受 KeepInventory 影响)。
            var jasmine = handler.findFirstCurio(
                    s -> s.is(com.merlinkitsune.astral_dice.item.ModItems.JASMINE_SIGN.get()));
            if (jasmine.isPresent()) {
                jasmine.get().stack().set(
                        com.merlinkitsune.astral_dice.component.ModDataComponents.JASMINE_ATK_BONUS.get(), 0);
                jasmine.get().stack().set(
                        com.merlinkitsune.astral_dice.component.ModDataComponents.JASMINE_DEF_BONUS.get(), 0);
            }
            var padman = handler.findFirstCurio(
                    s -> s.is(com.merlinkitsune.astral_dice.item.ModItems.PADMAN_SIGN.get()));
            if (padman.isPresent()) {
                padman.get().stack().set(
                        com.merlinkitsune.astral_dice.component.ModDataComponents.PADMAN_ATK_BONUS.get(), 0);
                padman.get().stack().set(
                        com.merlinkitsune.astral_dice.component.ModDataComponents.PADMAN_DEF_BONUS.get(), 0);
                padman.get().stack().set(
                        com.merlinkitsune.astral_dice.component.ModDataComponents.PADMAN_LAST_REFRESH.get(), 0L);
            }
        });
        player.removeEffect(ModEffects.DICE_BLESSING);
        player.removeEffect(ModEffects.HAIQING_READY);
        player.removeEffect(ModEffects.BONNIE_READY);
        player.removeEffect(ModEffects.INVESTIGATION_BONUS);
        player.removeEffect(ModEffects.FATE_GUIDANCE);
        player.removeEffect(ModEffects.FEN_FRENZY);
        player.removeEffect(ModEffects.PAPARA_BITE);
        player.removeEffect(ModEffects.MAGIC_TOME_COUNT);
    }

    // 死亡重生克隆:恢复"死亡保留"的数据(充能层数 + 调查员/忍者累计加成)。
    // 必须最后执行(priority = LOWEST):附件克隆/复制在此之前完成,否则回写会被随之而来的复制覆盖成 0。
    // PlayerRespawnEvent 侧再兜底一次(暂存表项取走即为空操作,幂等)。
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onPlayerCloneRestoreDeathPreserved(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        ChargeManager.restoreAfterDeath(player);
        com.merlinkitsune.astral_dice.component.DeathPreservedBonuses.restoreAfterDeath(player);
    }

    // 玩家退出/重新登录:清除骰神赐福效果(防止退出后重进仍保留战斗状态)
    @SubscribeEvent
    public static void onPlayerLoggedInClearDiceBlessing(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        ModAttachments.setDefenseCardConsumedThisBlessing(player, false);
        // 计时器守卫:清空效果结束时刻记录,避免重登后守卫重新施加旧效果
        EffectTimerGuard.clear(player);
        ModEffectRemoval.remove(player, ModEffects.DICE_BLESSING);
        // 重连后刷新治愈体系(上限收缩/效果显示;赐福边沿 prev 标记初始 false,不会误触发减半)
        HealingManager.tick(player);
        // 首次加入世界赠送《恋的规则书》(开关见 common 配置)
        giveGuideBookOnFirstJoin(player);
    }

    // 死亡重生:刷新治愈体系(上限收缩/效果显示)
    @SubscribeEvent
    public static void onPlayerRespawnMedkit(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        HealingManager.tick(player);
        // 充能流派:死亡不丢失充能层数,重生后恢复
        ChargeManager.restoreAfterDeath(player);
        // 调查员/忍者立牌累计加成:重生后再兜底恢复(克隆已恢复过则此处空操作)
        com.merlinkitsune.astral_dice.component.DeathPreservedBonuses.restoreAfterDeath(player);
    }

    // 首次加入世界:若配置开启且玩家尚未领过,赠送《恋的规则书》(每个玩家在每个世界只发一次)
    private static void giveGuideBookOnFirstJoin(Player player) {
        if (!GameplayConstants.GIVE_GUIDE_BOOK_ON_FIRST_JOIN) return;
        if (ModAttachments.isGuideBookGiven(player)) return;
        if (!ModList.get().isLoaded("patchouli")) return;
        ItemStack book = ItemModBook.forBook(Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "astral_guide")).create();
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        ModAttachments.setGuideBookGiven(player, true);
    }
}
