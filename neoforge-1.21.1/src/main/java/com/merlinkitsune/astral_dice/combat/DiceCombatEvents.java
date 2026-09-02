package com.merlinkitsune.astral_dice.combat;


import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.network.DamageNumberPayload;
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
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
import net.neoforged.neoforge.network.PacketDistributor;
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
import com.merlinkitsune.astral_dice.item.card.FateGuidanceCardItem;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.event.ModEffectRemoval;

@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class DiceCombatEvents {
    // === 神秘遗物+ (Enigmatic Legacy+) / 神秘遗物扩展 (Enigmatic Addons) 联动 ===
    // 七咒之戒(神秘遗物+);启示之证(神秘遗物+);倒转之启(神秘遗物+);恩惠之典(神秘遗物扩展)
    private static final String ENIGMATIC_CURSED_RING = "enigmaticlegacyplus:cursed_ring";
    private static final String ENIGMATIC_ACKNOWLEDGMENT = "enigmaticlegacyplus:the_acknowledgment";
    private static final String ENIGMATIC_TWIST = "enigmaticlegacyplus:the_twist";
    private static final String ENIGMATIC_BLESS = "enigmaticaddons:the_bless";

    /**
     * 玩家侧闪避判定开关:当前 false(玩家侧闪避已移除,目标未佩戴骰子时直接进入常规防御结算)。
     * 未来如需恢复闪避,改为 true 即可——闪避对骰与闪避失败结算代码保留在
     * {@code onLivingDamagePre} 的 targetDiceResult.isEmpty() 分支内。
     */
    private static final boolean PLAYER_DODGE_ENABLED = false;
    // 大当家立牌“战斗爽·扩散”递归保护:防止群体伤害再次触发扩散造成无限递归
    private static boolean cleaveProcessing = false;
    // AOE(顺劈/溅射)波及伤害处理中:被波及目标不再进入骰战结算
    static boolean aoeProcessing = false;
    // 反击流派:反击伤害结算进行中(防止反击伤害再次进入骰战结算/递归触发)
    private static boolean counterProcessing = false;


    // 检测玩家是否佩戴了七咒之戒(按物品 ID 识别,未安装该模组时返回 false)
    public static boolean hasEnigmaticCurse(Player player) {
        Item ring = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ENIGMATIC_CURSED_RING));
        if (ring == Items.AIR) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ring)).isPresent();
    }

    // 检测玩家是否手持指定神秘遗物+ 物品(如启示之证)
    public static boolean isHoldingEnigmaticItem(Player player, String itemId) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (item == Items.AIR) return false;
        return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
    }

    // 七咒减益:对骰子/卡牌点数施加 -40%(手持启示之证再 -20%;护法爆发/倒转之启/恩惠之典完全免疫)。
    // 用于攻击点数(骰点+卡牌)与闪避失败的"攻击点数最大值"结算。
    private static double applyCurseToDicePoints(Player player, double points) {
        if (points <= 0 || !hasEnigmaticCurse(player)) return points;
        if (player.hasEffect(ModEffects.MISAKI_BURST)
                || isHoldingEnigmaticItem(player, ENIGMATIC_TWIST)
                || isHoldingEnigmaticItem(player, ENIGMATIC_BLESS)) {
            // 爆发期间/持有免疫物品:不施加减益,造成全额点数
            return points;
        }
        double cursePenalty = 0.4;
        if (isHoldingEnigmaticItem(player, ENIGMATIC_ACKNOWLEDGMENT)) {
            cursePenalty = Math.max(0, cursePenalty - 0.2);
        }
        return points * (1 - cursePenalty);
    }


    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        DamageSource source = event.getSource();
        Entity directEntity = source.getDirectEntity();

        LivingEntity target = event.getEntity();

        // 立牌受击钩子分发(史莱姆立牌等受击类被动由各立牌 onHurt 实现,不再在此硬编码)
        if (!target.level().isClientSide() && target instanceof Player targetPlayer) {
            BaseSignItem.invokeHurtHooks(targetPlayer, event.getNewDamage());
            // 缓冲盾牌筹码:受到攻击时 +2 治愈 +3 星币(每分钟一次)
            com.merlinkitsune.astral_dice.item.chip.BufferShieldChipItem.onHurt(targetPlayer, event.getNewDamage());
        }

        // AOE(顺劈/溅射)波及的目标不进入骰战结算,避免二次吃到完整骰战;
        // 反击流派:反击伤害不进入骰战结算(已按反击公式自算)
        if (aoeProcessing || counterProcessing) return;
        if (!(directEntity instanceof Player player)) return;
        if (target == player) return;

        // 骰神赐福仅能由近战武器攻击触发与生效:直接伤害来源必须为玩家(已排除弓/弩/三叉戟投掷等远程),
        // 主手必须持有近战武器(排除空手/盾牌/非近战类武器)
        if (!isMeleeWeaponAttack(player)) return;

        // === ATTACKER DICE (unique, via curios dice slot) ===
        ItemStack diceStack = null;
        WeaponEnhancement enhancement = null;
        var attackerCurios = CuriosApi.getCuriosInventory(player);
        if (attackerCurios.isPresent()) {
            var diceResult = attackerCurios.get().findFirstCurio(DiceCurioItem::isDiceItem);
            if (diceResult.isPresent()) {
                diceStack = diceResult.get().stack();
                enhancement = diceStack.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), null);
            }
        }

        // 占星师立牌主动:对本次攻击的第一个目标施加"虚弱印记"5:00(须符合骰神赐福触发条件)+ 虚弱效果。
        // 印记持续生效至目标被击杀或计时结束;记录释放者,击杀后仅释放者获得奖励。
        if (!player.level().isClientSide() && attackerCurios.isPresent() && isBlessingTarget(target, player)) {
            var haiqingResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.HAIQING_SIGN.get()));
            if (haiqingResult.isPresent() && ModAttachments.getSignReadyType(player) == HaiqingSignItem.READY_TYPE) {
                ModAttachments.setSignReadyType(player, 0);
                ModAttachments.setSignReadyExpire(player, 0);
                ModAttachments.setWeakMarkSource(target, Optional.of(player.getUUID()));
                target.addEffect(new MobEffectInstance(ModEffects.WEAK_MARK, 6000, 0, false, true));
                EffectTimerGuard.apply(target, new MobEffectInstance(MobEffects.WEAKNESS, 6000, 0, false, true));
                // 主动成功施加:移除"待命"提示效果并开始玩家级冷却
                ModEffectRemoval.remove(player, ModEffects.HAIQING_READY);
                ModAttachments.setSignActiveCooldownEnd(player,
                        player.level().getGameTime() + GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS);
            }
            // 秘密侦探立牌主动:对本次攻击的第一个目标施加"隐匿调查"(永久,直到目标死亡/消失);若目标带"标记",按标记层数*2 获得星币
            var bonnieResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.BONNIE_SIGN.get()));
            if (bonnieResult.isPresent() && ModAttachments.getSignReadyType(player) == BonnieSignItem.READY_TYPE) {
                ModAttachments.setSignReadyType(player, 0);
                ModAttachments.setSignReadyExpire(player, 0);
                ModAttachments.setUndercoverSource(target, Optional.of(player.getUUID()));
                target.addEffect(new MobEffectInstance(ModEffects.UNDERCOVER_INVESTIGATION,
                        Integer.MAX_VALUE, 0, false, true));
                int markLevel = MarkManager.getLevel(target);
                if (markLevel > 0) {
                    ItemStack coinStack = new ItemStack(ModItems.STAR_COIN.get(), markLevel * 2);
                    if (!player.getInventory().add(coinStack)) {
                        player.drop(coinStack, false);
                    }
                }
                // 主动成功施加:移除"待命"提示效果并开始玩家级冷却
                ModEffectRemoval.remove(player, ModEffects.BONNIE_READY);
                ModAttachments.setSignActiveCooldownEnd(player,
                        player.level().getGameTime() + GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS);
            }
        }

        // 本次攻击是否触发了骰神赐福(与赐福触发逻辑一致:仅在未拥有赐福时触发;同一挥击命中多目标也仅触发一次)
        boolean triggeredBlessing = false;
        if (!player.level().isClientSide() && diceStack != null && !player.hasEffect(ModEffects.DICE_BLESSING)
                && isBlessingTarget(target, player)) {
            player.addEffect(new MobEffectInstance(ModEffects.DICE_BLESSING,
                    GameplayConstants.DICE_BLESSING_DURATION_TICKS, 0, false, false));
            triggeredBlessing = true;
            // 新赐福周期:重置“防御牌已消耗”标记,确保本次赐福期间最多消耗一次防御牌耐久
            ModAttachments.setDefenseCardConsumedThisBlessing(player, false);
            // 新赐福周期:重置诅咒之剑“本次赐福已触发”标记
            ModAttachments.setCursedSwordBlessingTriggered(player, false);
            // 玩家对玩家:若被攻击方也佩戴骰子,则同时触发其骰神赐福(双方都拥有骰子时)
            if (target instanceof Player targetPlayer) {
                var targetCurios = CuriosApi.getCuriosInventory(targetPlayer);
                if (targetCurios.isPresent()) {
                    var targetDiceResult = targetCurios.get().findFirstCurio(DiceCurioItem::isDiceItem);
                    if (targetDiceResult.isPresent() && !targetPlayer.hasEffect(ModEffects.DICE_BLESSING)) {
                        targetPlayer.addEffect(new MobEffectInstance(ModEffects.DICE_BLESSING,
                                GameplayConstants.DICE_BLESSING_DURATION_TICKS, 0, false, false));
                        ModAttachments.setDefenseCardConsumedThisBlessing(targetPlayer, false);
                        ModAttachments.setCursedSwordBlessingTriggered(targetPlayer, false);
                    }
                }
            }
            // 标靶筹码:触发骰神赐福后,对标靶范围内最近的一个敌对目标施加一层标记
            if (attackerCurios.isPresent()) {
                var targetChipResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.TARGET_CHIP.get()));
                if (targetChipResult.isPresent()) {
                    net.minecraft.world.phys.AABB aabb =
                            player.getBoundingBox().inflate(GameplayConstants.TARGET_CHIP_RANGE);
                    var nearby = player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, aabb,
                            e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive());
                    if (!nearby.isEmpty()) {
                        var nearest = nearby.stream()
                                .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                                .orElse(null);
                        if (nearest != null) {
                            MarkManager.apply(nearest, 1200);
                        }
                    }
                }
            }
            // 星币锤筹码:每次进入骰神赐福时,若持有星币超过 20 枚,则消耗 3 星币并按持有总数 30% 提升攻击力
            if (attackerCurios.isPresent()) {
                var hammerResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.STAR_COIN_HAMMER.get()));
                if (hammerResult.isPresent()) {
                    com.merlinkitsune.astral_dice.item.chip.StarCoinHammerChipItem.onBlessingStart(player);
                }
            }
            // 大当家立牌:触发骰神赐福 → 养精蓄锐 -1 层并记录触发时刻;"战斗爽·扩散"待命则本次赐福启用
            com.merlinkitsune.astral_dice.item.sign.FenSignItem.onBlessingTriggered(player);
            // 治愈体系:触发骰神赐福 → 医疗箱加点(先)+ 按当前治愈点×2 回血(后)。
            // 置于触发块末尾,确保晚于本事件内所有影响治愈点数量的效果(立牌受击钩子/缓冲盾牌在前部已执行)
            com.merlinkitsune.astral_dice.item.HealingManager.onBlessingTriggered(player);
        }

        // Dice combat mechanics require the Dice Blessing effect
        if (!player.hasEffect(ModEffects.DICE_BLESSING)) return;
        if (diceStack == null) return;
        if (enhancement == null) {
            enhancement = WeaponEnhancement.EMPTY;

            if (!diceStack.has(ModDataComponents.WEAPON_ENHANCEMENT.get())) {
                diceStack.set(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
            }
        }

        int baseDice = ThreadLocalRandom.current().nextInt(1, 7);

        // === MISAKI SIGN (护法立牌, via curios stand slot) ===
        boolean misakiFound = false;
        // 护法立牌星级 = 玩家装备骰子的星级(立牌无独立升星;此处 enhancement 已兜底非 null)
        int misakiStar = enhancement.starLevel();
        int misakiStacks = 0;
        ItemStack misakiStack = null;
        if (attackerCurios.isPresent()) {
            var result = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.MISAKI_SIGN.get()));
            if (result.isPresent()) {
                misakiFound = true;
                misakiStack = result.get().stack();
                misakiStacks = misakiStack.getOrDefault(ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);
            }
        }
        // 护法立牌(misaki):爆发状态(主动技能触发,持续 60 秒)
        boolean misakiBurst = misakiFound && player.hasEffect(ModEffects.MISAKI_BURST);

        // 护法立牌(misaki)被动:触发骰神赐福时累积层数(最大 3 层)
        if (triggeredBlessing && misakiFound && !player.level().isClientSide()) {
            int newStacks = Math.min(misakiStacks + 1, 3);
            misakiStack.set(ModDataComponents.MISAKI_SIGN_STACKS.get(), newStacks);
            misakiStacks = newStacks;
        }

        // 护法立牌(misaki):爆发期间战斗骰点按星级追加基础数字(1⭐+1,2⭐+2,3⭐+3)
        if (misakiBurst) {
            int starBonus = switch (misakiStar) {
                case 1 -> 1;
                case 2 -> 2;
                case 3 -> 3;
                default -> 0;
            };
            baseDice += starBonus;
        }

        // 上班族立牌:赐福期间骰点为1时,下次攻击骰点必为6
        if (attackerCurios.isPresent()) {
            var padmanResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.PADMAN_SIGN.get()));
            if (padmanResult.isPresent()) {
                ItemStack padmanStack = padmanResult.get().stack();
                if (padmanStack.getOrDefault(ModDataComponents.PADMAN_FORCE_SIX.get(), false)) {
                    baseDice = 6;
                    padmanStack.set(ModDataComponents.PADMAN_FORCE_SIX.get(), false);
                } else if (baseDice == 1) {
                    padmanStack.set(ModDataComponents.PADMAN_FORCE_SIX.get(), true);
                }
            }
        }

        // 经商立牌(parunan):触发骰神赐福后立即获得 触发时骰点*2 的星光
        if (triggeredBlessing && attackerCurios.isPresent()) {
            var parunanResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.PARUNAN_SIGN.get()));
            if (parunanResult.isPresent()) {
                ParunanSignItem.gainStarlightOnBlessing(player, baseDice);
            }
        }

        // 占星师立牌被动:骰神赐福期间骰点=6 时立即获得 6 星币
        if (!player.level().isClientSide() && baseDice == 6 && attackerCurios.isPresent()) {
            var haiqingResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.HAIQING_SIGN.get()));
            if (haiqingResult.isPresent()) {
                ItemStack coinStack = new ItemStack(ModItems.STAR_COIN.get(), 6);
                if (!player.getInventory().add(coinStack)) {
                    player.drop(coinStack, false);
                }
            }
        }

        // 八面骰筹码:触发骰神赐福后,使用通用掷骰方法掷 1d10 并提示玩家,累计点数;
        // 每满 8 点 +1 星光(上限 MAX_STARLIGHT),本次骰点恰为 8 时立即获得 8 个星币
        if (triggeredBlessing && attackerCurios.isPresent()) {
            var eightResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.EIGHT_SIDED_DICE.get()));
            if (eightResult.isPresent()) {
                int roll = rollDice(10);
                notifyRoll(player, roll);
                if (roll == 8) {
                    ItemStack coinStack = new ItemStack(ModItems.STAR_COIN.get(), 8);
                    if (!player.getInventory().add(coinStack)) {
                        player.drop(coinStack, false);
                    }
                }
                int starlight = StarLightManager.get(player);
                if (starlight < StarLightManager.getCap()) {
                    int accum = ModAttachments.getEightSidedAccum(player) + roll;
                    while (accum >= 8 && starlight < StarLightManager.getCap()) {
                        accum -= 8;
                        starlight++;
                    }
                    if (starlight >= StarLightManager.getCap()) {
                        ModAttachments.setEightSidedAccum(player, 0);
                        StarLightManager.set(player, StarLightManager.getCap());
                    } else {
                        ModAttachments.setEightSidedAccum(player, accum);
                        StarLightManager.set(player, starlight);
                    }
                } else {
                    // 星光已满,不再累计
                    ModAttachments.setEightSidedAccum(player, 0);
                }
            }
        }

        // === ATTACK POWER (基础值 + 修饰器注册表) ===
        com.merlinkitsune.astral_dice.combat.DiceCombatContext ctx =
                new com.merlinkitsune.astral_dice.combat.DiceCombatContext(
                        player, target, event, baseDice, diceStack, enhancement,
                        triggeredBlessing, misakiBurst, misakiStar, misakiStacks);

        double attackPower = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (var modifier : com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.attackModifiers()) {
            attackPower = modifier.apply(ctx, attackPower);
        }
        int attackCardSum = ctx.attackCardSum;
        boolean hasShadowStrike = ctx.hasShadowStrike;
        boolean hasFullPower = ctx.hasFullPower;
        // 防御性兜底:即使未来调整掷骰注册顺序/逻辑,只要骰子已装载全力攻击,就必须应用最终攻击力 +50%
        if (!hasFullPower && enhancement != null) {
            for (AppliedStone stone : enhancement.appliedStones()) {
                if ("full_power".equals(stone.type())) {
                    hasFullPower = true;
                    break;
                }
            }
        }

        // === 神秘遗物+ 联动:七咒之戒 ===
        // 佩戴七咒之戒时,骰子伤害加成(骰点 + 卡牌点数)降低 40%;手持"启示之证"攻击时,减益再降低 20%;
        // 装备"倒转之启"或"恩惠之典"时修正第四诅咒,骰子总能造成全额伤害(完全免疫七咒减益);
        // 护法立牌"爆发"效果期间同样修正第四诅咒:总能造成全额伤害
        double diceAttackBonus = applyCurseToDicePoints(player, baseDice + attackCardSum);
        // 基础伤害值(属性 + 立牌/筹码/效果攻击修饰器,不含骰点/卡牌加成):供闪避失败结算使用
        double baseDamage = attackPower;
        attackPower += diceAttackBonus;

        // === 立牌/筹码攻击加成已全部迁移至 DiceCombatModifiers 攻击修饰器注册表 ===

        if (hasFullPower) {
            attackPower = Math.ceil(attackPower * 1.5);
        }

        // === DODGE / DEFENSE POWER ===
        // 玩家侧闪避判定已停用(PLAYER_DODGE_ENABLED=false):未佩戴骰子的玩家不再进行闪避对骰,
        // 直接进入常规防御结算(与佩戴骰子但无赐福的玩家一致)。
        // 闪避代码保留供未来使用(见下方 targetDiceResult.isEmpty() 分支与 PLAYER_DODGE_ENABLED)。
        // 怪物(含无护甲):始终防御——每次受击掷 1d6 防御骰,最终伤害按双方骰点计算。
        boolean skipDefense = false;
        boolean dodgeFailed = false;
        double dodgeFailDamage = 0;
        int defenseBaseDice = 0;
        if (!player.level().isClientSide() && target instanceof Player targetPlayer) {
            var targetCurios = CuriosApi.getCuriosInventory(targetPlayer);
            if (targetCurios.isPresent()) {
                var targetDiceResult = targetCurios.get().findFirstCurio(DiceCurioItem::isDiceItem);
                if (PLAYER_DODGE_ENABLED && targetDiceResult.isEmpty()) {
                    // === 玩家侧闪避对骰(保留供未来使用) ===
                    // 攻击方与目标各掷 1d6;目标骰点更高或为 6 → 闪避成功,忽略本次伤害(return);
                    // 否则闪避失败 → 伤害 = 基础伤害值 + 攻击方骰点 + 卡牌加成
                    // (基础伤害值 = 属性攻击 + 立牌/筹码/效果攻击修饰器;骰点/卡牌均取本次实际掷出的值),
                    // 跳过防御;全力攻击倍率在最终伤害处适用。
                    int attackRoll = ThreadLocalRandom.current().nextInt(1, 7);
                    int dodgeRoll = ThreadLocalRandom.current().nextInt(1, 7);
                    if (dodgeRoll > attackRoll || dodgeRoll == 6) {
                        return;
                    }
                    dodgeFailed = true;
                    dodgeFailDamage = baseDamage + baseDice + attackCardSum;
                } else if (targetPlayer.hasEffect(ModEffects.DICE_BLESSING)) {
                    defenseBaseDice = ThreadLocalRandom.current().nextInt(1, 7);
                    // 防御卡掷骰由注册表防御修饰器执行(读 ctx.targetEnhancement,写 ctx.defenseCardSum)
                    ItemStack targetDice = targetDiceResult.get().stack();
                    WeaponEnhancement targetEnh = targetDice.get(ModDataComponents.WEAPON_ENHANCEMENT.get());
                    ctx.targetEnhancement = targetEnh;
                }
            }
        } else if (!target.level().isClientSide() && !(target instanceof Player)) {
            // 怪物:始终防御,每次受击掷 1d6 防御骰(不再闪避)
            defenseBaseDice = ThreadLocalRandom.current().nextInt(1, 7);
        }

        double finalDmg;
        if (dodgeFailed) {
            // 闪避失败结算(与上方闪避对骰一起保留供未来使用;当前 dodgeFailed 恒为 false)
            finalDmg = hasFullPower ? Math.ceil(dodgeFailDamage * 1.5) : dodgeFailDamage;
        } else {
            double defensePower;
            if (skipDefense) {
                defensePower = 0;
            } else {
                // 先累计所有防御修饰器加成(防御卡掷骰会写入 ctx.defenseCardSum,不直接改变 modifierDefense)
                double modifierDefense = 0;
                for (var modifier : com.merlinkitsune.astral_dice.combat.DiceCombatModifiers.defenseModifiers()) {
                    modifierDefense = modifier.apply(ctx, modifierDefense);
                }

                // 效果牌/立牌/筹码的防御力已折算为真实护甲(1 防御力 = 2 护甲值,
                // 见 DiceCombatModifiers.setDefenseArmorBonus),getArmorValue() 已包含;
                // modifierDefense 恒为 0(仅防御卡掷骰写入 ctx.defenseCardSum 作为防御点直接加入)。
                // 怪物与玩家公式同步:防御 = 2 + 护甲÷2 + 1.4×韧性 + 防御骰 + 防御卡(1 防御 = 2 护甲)。
                double rawArmor = Math.min(target.getArmorValue(), 20);
                double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
                double effectiveArmor = Math.max(0, Math.min(rawArmor + modifierDefense * 2.0, 20));
                defensePower = 2
                        + effectiveArmor / 2.0
                        + 1.4 * toughness
                        + defenseBaseDice
                        + ctx.defenseCardSum;
            }

            // Padman sign: attack dice == 6 bypass — ignore all defense except defense cards
            if (ctx.padmanDefBypass && !skipDefense) {
                defensePower = ctx.defenseCardSum;
            }

            finalDmg = Math.max(1, attackPower - defensePower);

        }

        if (MarkManager.getLevel(target) > 0) {
            finalDmg += 1;
        }

        // 虚弱印记:目标受到任意伤害 +10%;命运指引激活时额外 +20%(骰战自包含结算,
        // 不依赖外部处理器顺序;onWeakMarkDamage 仅对非骰战攻击生效)
        if (target.hasEffect(ModEffects.WEAK_MARK)) {
            float weakMultiplier = 1.10f;
            if (FateGuidanceCardItem.isFateGuidanceActive(player)) {
                weakMultiplier += 0.20f;
            }
            finalDmg *= weakMultiplier;
        }

        // 接管外部伤害影响:按原始设计应用最终伤害数值(各因子相乘)。
        // 附属内容可通过 registerDiceCombatFactor 注册自定义因子,影响骰战最终伤害。
        for (DiceCombatFactor factor : EXTERNAL_DAMAGE_FACTORS) {
            finalDmg = factor.modify(player, target, finalDmg);
        }

        event.setNewDamage((float) finalDmg);
        sendDamageNumber(event.getEntity(), (int) finalDmg);

        // 玩家对玩家:被攻击方若佩戴骰子且处于骰神赐福,则每个赐福期间消耗一次防御牌耐久
        if (!player.level().isClientSide() && target instanceof Player targetDefender
                && targetDefender.hasEffect(ModEffects.DICE_BLESSING)) {
            consumeDefenseCardDurabilityOnce(targetDefender);
        }

        // 大当家立牌(战斗爽·扩散):本次赐福期间,每次攻击将总伤害的 80% 施加给目标 6 格内其他敌对目标
        // 使用递归保护:扩散造成的伤害不会再触发二次扩散,避免多目标互炸导致栈溢出
        if (!player.level().isClientSide() && !cleaveProcessing && com.merlinkitsune.astral_dice.item.sign.FenSignItem.isCleaveActive(player)) {
            cleaveProcessing = true;
            aoeProcessing = true;
            try {
                double cleaveDmg = finalDmg * com.merlinkitsune.astral_dice.item.sign.FenSignItem.CLEAVE_RATIO;
                if (cleaveDmg > 0) {
                    net.minecraft.world.phys.AABB cleaveBox =
                            target.getBoundingBox().inflate(com.merlinkitsune.astral_dice.item.sign.FenSignItem.CLEAVE_RANGE);
                    var nearby = target.level().getEntitiesOfClass(
                            net.minecraft.world.entity.LivingEntity.class, cleaveBox,
                            e -> e != target && e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive());
                    var cleaveSource = com.merlinkitsune.astral_dice.damage.ModDamageTypes
                            .diceDamage(target.level(), player);
                    for (var e : nearby) {
                        e.hurt(cleaveSource, (float) cleaveDmg);
                        sendDamageNumber(e, (int) cleaveDmg);
                    }
                }
            } finally {
                cleaveProcessing = false;
                aoeProcessing = false;
            }
        }

        // 吸血鬼立牌(papara)主动"嘬一口":攻击时恢复骰神赐福最终伤害的一半生命(取整,至少 1 点)
        if (!player.level().isClientSide() && player.hasEffect(ModEffects.PAPARA_BITE)) {
            player.heal(Math.max(1, (int) finalDmg / 2));
        }

        if (hasShadowStrike && !player.level().isClientSide()) {
            EffectTimerGuard.apply(target, new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        }

        // === DICE STONE CONSUMPTION (与赐福触发逻辑一致:仅在触发骰神赐福的那次攻击消耗一次耐久;
        //      赐福效果期间卡牌攻击/防御加成持续生效、每次攻击独立随机判定,不再消耗耐久) ===
        if (!player.level().isClientSide() && triggeredBlessing) {
            consumeAttackCardDurabilityOnce(player, diceStack, enhancement);
        }

        // Flashlight chip: 攻击单个敌对目标时 +1 星光(每个目标仅增加 1 点,不超过上限)
        if (!player.level().isClientSide() && target instanceof Enemy && attackerCurios.isPresent()) {
            var flashlightResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.FLASHLIGHT_CHIP.get()));
            if (flashlightResult.isPresent()) {
                // 手电筒:攻击单个敌对目标时 +1 星光(上限由 StarLightManager 统一管理)
                StarLightManager.add(player, 1);
            }
        }
    }


    // 攻击牌耐久消耗(仅在触发骰神赐福的那次攻击执行一次;防御牌/蓄力不消耗)。
    // 普通近战触发与反击流派共用(反击未赐福时作为触发攻击消耗一次耐久)。
    private static void consumeAttackCardDurabilityOnce(Player player, ItemStack diceStack, WeaponEnhancement enhancement) {
        if (diceStack == null || diceStack.isEmpty() || enhancement == null
                || enhancement.appliedStones().isEmpty()) return;
        List<AppliedStone> newStones = new ArrayList<>();
        int attackCostFreed = 0;
        boolean dirty = false;
        for (AppliedStone stone : enhancement.appliedStones()) {
            // 防御牌:不在此消耗(消耗见 consumeDefenseCardDurability)
            if (stone.type().startsWith("defense_")) {
                newStones.add(stone);
                continue;
            }
            // 蓄力:赐福期间持续生效不消耗耐久,在骰神赐福结束时返还"全力攻击"
            if ("charge".equals(stone.type())) {
                newStones.add(stone);
                continue;
            }
            int newUses = stone.uses() - 1;
            if (newUses <= 0) {
                attackCostFreed += MisakiSignItem.effectiveCost(player, stone.type());
                dirty = true;
            } else {
                newStones.add(new AppliedStone(stone.type(), newUses));
                dirty = true;
            }
        }
        if (dirty) {
            diceStack.set(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                    new WeaponEnhancement(
                            enhancement.usedCost() - attackCostFreed,
                            enhancement.maxCost(),
                            enhancement.usedDefenseCost(),
                            enhancement.maxDefenseCost(),
                            enhancement.starLevel(),
                            newStones
                    ));
        }
    }

    private static void consumeDefenseCardDurability(Player defender, ItemStack diceStack, WeaponEnhancement enh) {
        if (diceStack == null || diceStack.isEmpty() || enh == null) return;
        List<AppliedStone> newStones = new ArrayList<>();
        int defenseCostFreed = 0;
        boolean dirty = false;
        for (AppliedStone stone : enh.appliedStones()) {
            if (!stone.type().startsWith("defense_")) {
                newStones.add(stone);
                continue;
            }
            int newUses = stone.uses() - 1;
            if (newUses <= 0) {
                defenseCostFreed += MisakiSignItem.effectiveCost(defender, stone.type());
                dirty = true;
            } else {
                newStones.add(new AppliedStone(stone.type(), newUses));
                dirty = true;
            }
        }
        if (dirty) {
            diceStack.set(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                    new WeaponEnhancement(
                            enh.usedCost(),
                            enh.maxCost(),
                            enh.usedDefenseCost() - defenseCostFreed,
                            enh.maxDefenseCost(),
                            enh.starLevel(),
                            newStones
                    ));
        }
    }

    // 带骰神赐福的玩家受到攻击时:每个赐福期间仅消耗一次防御牌耐久,不修改原版伤害
    private static void consumeDefenseCardDurabilityOnce(Player defender) {
        if (defender.level().isClientSide()) return;
        if (ModAttachments.isDefenseCardConsumedThisBlessing(defender)) return;
        var curios = CuriosApi.getCuriosInventory(defender);
        if (curios.isEmpty()) return;
        var diceResult = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        if (diceResult.isEmpty()) return;
        ItemStack dice = diceResult.get().stack();
        WeaponEnhancement enh = dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), null);
        if (enh == null) return;
        consumeDefenseCardDurability(defender, dice, enh);
        ModAttachments.setDefenseCardConsumedThisBlessing(defender, true);
    }



    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.isCanceled()) return;
        var newTarget = event.getNewAboutToBeSetTarget();
        if (!(newTarget instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 骇客立牌完全隐身:绝对禁止生物将该玩家设为索敌目标
        if (NancyLuSignItem.isHidden(player)) {
            event.setCanceled(true);
            return;
        }
        // 秘密侦探"调查阶段":隐身 + 调查阶段加成期间同样不被生物索敌
        if (player.hasEffect(MobEffects.INVISIBILITY)
                && player.hasEffect(ModEffects.INVESTIGATION_BONUS)) {
            event.setCanceled(true);
            return;
        }
        if (!player.hasEffect(ModEffects.DICE_BLESSING)) return;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        var diceResult = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        if (diceResult.isEmpty()) return;
        WeaponEnhancement enhancement = diceResult.get().stack().getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), null);
        if (enhancement == null) return;
        for (var stone : enhancement.appliedStones()) {
            if ("shadow_strike".equals(stone.type())) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onDiceBlessingExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null
                || effect.getEffect().value() != ModEffects.DICE_BLESSING.get()) {
            return;
        }

        // 赐福结束:重置防御牌消耗标记
        ModAttachments.setDefenseCardConsumedThisBlessing(player, false);

        // 星币锤筹码:赐福结束清除本次攻击加成
        com.merlinkitsune.astral_dice.item.chip.StarCoinHammerChipItem.onBlessingEnd(player);
        // 银行卡-用不完:赐福结束后使自身及团队所有成员获得 3 星币(死亡清场等已死亡时不发放)
        com.merlinkitsune.astral_dice.item.chip.BankCardUnlimitedChipItem.onBlessingEnd(player);
        // 大当家立牌:赐福结束清除"战斗爽·扩散"生效状态
        com.merlinkitsune.astral_dice.item.sign.FenSignItem.onBlessingEnd(player);
        // 骇客立牌:赐福结束刷新被动(攻击/防御,覆盖旧类型)
        NancyLuSignItem.onDiceBlessingEnded(player);

        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        var diceResult = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        if (diceResult.isEmpty()) return;
        ItemStack dice = diceResult.get().stack();
        WeaponEnhancement enh = dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), null);
        if (enh == null) return;

        List<AppliedStone> newStones = new ArrayList<>();
        boolean foundCharge = false;
        int costFreed = 0;
        // 蓄力:本次赐福结束后一律转换为全力攻击(赐福期间卡牌栏锁定,蓄力只可能预先放置)
        for (AppliedStone stone : enh.appliedStones()) {
            if ("charge".equals(stone.type())) {
                foundCharge = true;
                costFreed += AppliedStone.cost(stone.type());
            } else {
                newStones.add(stone);
            }
        }
        if (!foundCharge) return;

        dice.set(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                new WeaponEnhancement(
                        enh.usedCost() - costFreed,
                        enh.maxCost(),
                        enh.usedDefenseCost(),
                        enh.maxDefenseCost(),
                        enh.starLevel(),
                        newStones
                ));

        ItemStack card = new ItemStack(ModItems.ATTACK_CARD_FULL_POWER.get());
        VitaminPillChipItem.giveCard(player, card);
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp,
                    new ActionBarPayload(Component.translatable("msg.astral_dice.charge_refund_full_power")
                            .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    // 标记效果自然结束时:每分钟减少 1 层标记(层数>1 时重新施加并重置计时,否则标记消失)

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBerserkDamageTaken(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        var berserk = target.getEffect(ModEffects.BERSERK);
        if (berserk == null) return;
        event.setNewDamage(event.getNewDamage() + 1 * (berserk.getAmplifier() + 1));
    }


    // 虚弱印记:目标受到任意伤害 +10%;攻击者拥有"命运的指引"效果时对带虚弱印记的目标额外 +20%
    /**
     * 骰战外部伤害影响因子:作用于骰神赐福最终伤害的乘算修饰器。
     * 附属内容/联动(如"命运的指引")可通过 {@link #registerDiceCombatFactor} 注册,
     * 按注册顺序依次作用于最终伤害;因子负责自行判断生效条件,不生效时原样返回。
     */
    @FunctionalInterface
    public interface DiceCombatFactor {
        double modify(Player attacker, LivingEntity target, double damage);
    }

    private static final List<DiceCombatFactor> EXTERNAL_DAMAGE_FACTORS = new ArrayList<>();

    // 注册骰战外部伤害影响因子(供附属内容/联动扩展)
    public static void registerDiceCombatFactor(DiceCombatFactor factor) {
        EXTERNAL_DAMAGE_FACTORS.add(factor);
    }

    // 内置因子:目标佩戴七咒之戒时,应用神秘遗物+ 已计算的第一诅咒【实际倍率】
    // (由 onCurseMitigation 在 LivingIncomingDamageEvent 捕获,动态适配模组配置/修正物品/救赎转换);
    // "命运的指引"激活时第一诅咒影响 -50%(加幅减半)。使用后清零捕获,避免残留。
    static {
        registerDiceCombatFactor((attacker, target, damage) -> {
            if (target instanceof Player cursed) {
                float ratio = com.merlinkitsune.astral_dice.component.ModAttachments.getDiceCurseRatio(cursed);
                if (ratio > 1.0f) {
                    damage *= FateGuidanceCardItem.isFateGuidanceActive(attacker) ? (1.0 + (ratio - 1.0) * 0.5) : ratio;
                }
                com.merlinkitsune.astral_dice.component.ModAttachments.setDiceCurseRatio(cursed, 1.0f);
            }
            return damage;
        });
    }


    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWeakMarkDamage(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!target.hasEffect(ModEffects.WEAK_MARK)) return;
        // 骰战攻击(攻击者赐福激活 + 佩戴骰子 + 近战):虚弱印记倍率已并入骰战结算,跳过以免双重应用
        if (event.getSource().getEntity() instanceof Player attacker
                && attacker.hasEffect(ModEffects.DICE_BLESSING)
                && attackerHasDiceCurio(attacker)
                && isMeleeWeaponAttack(attacker)) {
            return;
        }
        float multiplier = 1.10f;
        if (event.getSource().getEntity() instanceof Player attacker2
                && FateGuidanceCardItem.isFateGuidanceActive(attacker2)) {
            multiplier += 0.20f;
        }
        event.setNewDamage(event.getNewDamage() * multiplier);
    }

    // 玩家是否持有骰子(curio 骰子槽)
    public static boolean attackerHasDiceCurio(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(DiceCurioItem::isDiceItem).isPresent();
    }


    private static int rollDice(int max) {
        return ThreadLocalRandom.current().nextInt(1, max + 1);
    }

    // 近战武器攻击判定:仅允许剑/斧/重锤/三叉戟等近战武器触发骰神赐福
    public static boolean isMeleeWeaponAttack(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return false;
        if (held.is(Items.SHIELD)) return false;
        return held.getItem() instanceof SwordItem
                || held.getItem() instanceof AxeItem
                || held.getItem() instanceof MaceItem
                || held.getItem() instanceof TridentItem;
    }

    // 骰神赐福触发目标判定:敌对生物、非团队内玩家、已被激怒的中立生物,以及其余非被动动物实体
    public static boolean isBlessingTarget(LivingEntity target, Player player) {
        // dummmmmmy 训练人偶:允许触发骰神赐福与伤害效果牌(用于伤害/效果测试)
        if (isTrainingDummy(target)) return true;
        if (target instanceof Player other) {
            return other.getTeam() == null || other.getTeam() != player.getTeam();
        }
        if (target instanceof Enemy) return true;
        if (target instanceof Mob mob) {
            // Boss 允许触发;其余生物仅在被激怒/正在攻击玩家时允许
            if (com.merlinkitsune.astral_dice.item.BossEntityUtil.isBossEntity(target)) return true;
            return mob.getTarget() == player || mob.isAggressive();
        }
        // 被动/友好/未激怒的中立生物不允许触发骰神赐福
        return false;
    }

    // dummmmmmy 训练人偶识别:实体注册 id 命名空间为 dummmmmmy,或类名包含 dummy(兼容不同版本/命名)
    private static boolean isTrainingDummy(LivingEntity target) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if ("dummmmmmy".equals(id.getNamespace())) return true;
        String name = target.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("dummy");
    }

    // 通用掷骰提示:自定义 actionbar(5s+1s淡出)显示骰点结果
    private static void notifyRoll(Player player, int roll) {
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Component msg = Component.translatable("msg.astral_dice.dice_roll", roll).withStyle(ChatFormatting.YELLOW);
        PacketDistributor.sendToPlayer(serverPlayer,
                new com.merlinkitsune.astral_dice.network.ActionBarPayload(msg,
                        GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    // 骰战最终伤害跳数字(红色)
    private static void sendDamageNumber(LivingEntity target, int bonusDamage) {
        sendDamageNumber(target, bonusDamage, 0xFF5555);
    }

    // 通用跳数字发送:指定 ARGB 颜色(0xRRGGBB 将被叠加透明度)
    private static void sendDamageNumber(LivingEntity target, int bonusDamage, int color) {
        com.merlinkitsune.astral_dice.network.DamageNumberPayload.send(target, bonusDamage, color);
    }

    // === 反击流派(Counterattack) ===
    // 拥有反击层数的玩家被近战敌方攻击时触发:视为玩家近战攻击,按
    // "手持最高近战武器基础伤害 + 1d6 骰点 + 自动赐福攻击牌加成 + 攻击力加成"计算总伤害,
    // 对攻击目标造成一次伤害并移除 1 层;未处于骰神赐福时自动触发赐福(消耗攻击牌耐久)。
    @SubscribeEvent
    public static void onCounterattackTriggered(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!(target instanceof Player player)) return;
        if (counterProcessing) return;
        if (!player.isAlive()) return;
        if (!player.hasEffect(ModEffects.COUNTERATTACK)) return;
        DamageSource source = event.getSource();
        // 近战敌方攻击:来源为敌对生物且为直接接触(非间接/投射/爆炸)
        Entity attackerEntity = source.getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker)) return;
        if (!(attacker instanceof Enemy)) return;
        if (source.getDirectEntity() != attacker) return;
        // 魔法伤害(唤魔者尖牙/守卫者光束/药水等)不属于近战,不触发反击
        if (com.merlinkitsune.astral_dice.combat.SpellDamageRegistry.isSpellDamage(source, source.getDirectEntity())) return;
        if (!attacker.isAlive()) return;

        double dmg = computeCounterattackDamage(player, attacker);
        if (dmg <= 0) return;
        counterProcessing = true;
        try {
            attacker.hurt(com.merlinkitsune.astral_dice.damage.ModDamageTypes.diceDamage(attacker.level(), player),
                    (float) dmg);
            sendDamageNumber(attacker, (int) dmg);
        } finally {
            counterProcessing = false;
        }
        // 移除 1 层"反击"
        com.merlinkitsune.astral_dice.effect.CounterattackEffect.consumeOne(player);
    }

    // 反击伤害计算(视为玩家近战攻击):手持最高近战武器基础伤害 + 1d6 骰点
    // + 攻击牌加成 + 攻击力加成;未赐福时自动触发骰神赐福并消耗攻击牌耐久
    private static double computeCounterattackDamage(Player player, LivingEntity attacker) {
        double weaponBase = highestHeldMeleeBaseDamage(player);

        // 自动赐福:仅在未处于骰神赐福时触发(施加效果 + 重置赐福周期标记 + 本次作为触发攻击消耗耐久)
        boolean blessingTriggered = false;
        if (!player.hasEffect(ModEffects.DICE_BLESSING)) {
            player.addEffect(new MobEffectInstance(ModEffects.DICE_BLESSING,
                    GameplayConstants.DICE_BLESSING_DURATION_TICKS, 0, false, false));
            ModAttachments.setDefenseCardConsumedThisBlessing(player, false);
            ModAttachments.setCursedSwordBlessingTriggered(player, false);
            blessingTriggered = true;
        }

        // 骰子与卡牌
        ItemStack diceStack = null;
        WeaponEnhancement enhancement = null;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isPresent()) {
            var diceResult = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
            if (diceResult.isPresent()) {
                diceStack = diceResult.get().stack();
                enhancement = diceStack.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), null);
            }
        }
        if (enhancement == null) enhancement = WeaponEnhancement.EMPTY;

        // 1d6 骰点 + 攻击牌掷骰(构造与 getDisplayAttackRange 一致的上下文)
        int baseDice = ThreadLocalRandom.current().nextInt(1, 7);
        int misakiStar = enhancement.starLevel();
        int misakiStacks = 0;
        boolean misakiBurst = false;
        if (curios.isPresent()) {
            var misakiResult = curios.get().findFirstCurio(s -> s.is(ModItems.MISAKI_SIGN.get()));
            if (misakiResult.isPresent()) {
                misakiStacks = misakiResult.get().stack().getOrDefault(ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);
                misakiBurst = player.hasEffect(ModEffects.MISAKI_BURST);
            }
        }
        DiceCombatContext ctx = new DiceCombatContext(
                player, attacker, null, baseDice, diceStack, enhancement, false,
                misakiBurst, misakiStar, misakiStacks);
        double modifiersSum = 0;
        for (var modifier : DiceCombatModifiers.attackModifiers()) {
            modifiersSum = modifier.apply(ctx, modifiersSum);
        }
        int attackCardSum = ctx.attackCardSum;

        // 攻击牌耐久:仅本次为触发赐福的攻击时消耗
        if (blessingTriggered) {
            consumeAttackCardDurabilityOnce(player, diceStack, enhancement);
        }

        // 七咒减益作用于 骰点+卡牌(与正常攻击路径一致);武器基础/攻击力加成不受减益
        double diceAndCards = applyCurseToDicePoints(player, baseDice + attackCardSum);
        double total = weaponBase + diceAndCards + modifiersSum;
        if (ctx.hasFullPower) {
            total = Math.ceil(total * 1.5);
        }
        return total;
    }

    // 手持(主手+副手)近战武器的基础伤害最大值(不含附魔/属性效果);无近战武器回退空手 1.0
    private static double highestHeldMeleeBaseDamage(Player player) {
        double best = 1.0;
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.isEmpty()) continue;
            for (var entry : stack.getItem().getDefaultAttributeModifiers().modifiers()) {
                if (entry.slot().test(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                        && entry.attribute().is(Attributes.ATTACK_DAMAGE)
                        && entry.modifier().operation()
                        == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                    best = Math.max(best, entry.modifier().amount());
                }
            }
        }
        return best;
    }

}
