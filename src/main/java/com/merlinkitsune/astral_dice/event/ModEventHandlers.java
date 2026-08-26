package com.merlinkitsune.astral_dice.event;

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
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class ModEventHandlers {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModEventHandlers.class);

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


    // 检测玩家是否佩戴了七咒之戒(按物品 ID 识别,未安装该模组时返回 false)
    private static boolean hasEnigmaticCurse(Player player) {
        Item ring = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ENIGMATIC_CURSED_RING));
        if (ring == Items.AIR) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ring)).isPresent();
    }

    // 检测玩家是否手持指定神秘遗物+ 物品(如启示之证)
    private static boolean isHoldingEnigmaticItem(Player player, String itemId) {
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

    // 已装卡牌点数上限之和(骰子点数上限 6 之外的"攻击点数最大值"部分)。
    // 与攻击卡掷骰一致遍历 appliedStones(当前攻击点数结算包含全部已装卡,含防御卡)。
    // 注:闪避失败结算已改为使用实际掷出的 骰点+卡牌加成,本方法暂未被调用,保留备用。
    private static int maxAttackCardPoints(WeaponEnhancement enhancement) {
        int sum = 0;
        if (enhancement == null) return 0;
        for (AppliedStone stone : enhancement.appliedStones()) {
            sum += com.merlinkitsune.astral_dice.combat.CardRegistry.maxRoll(stone.type());
        }
        return sum;
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
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 6000, 0, false, true));
                // 主动成功施加:移除"待命"提示效果并开始玩家级冷却
                player.removeEffect(ModEffects.HAIQING_READY);
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
                player.removeEffect(ModEffects.BONNIE_READY);
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
            // 标靶筹码:触发骰神赐福后,对附近(标靶常量范围)随机一个敌对目标施加一层标记
            if (attackerCurios.isPresent()) {
                var targetChipResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.TARGET_CHIP.get()));
                if (targetChipResult.isPresent()) {
                    net.minecraft.world.phys.AABB aabb =
                            player.getBoundingBox().inflate(GameplayConstants.TARGET_CHIP_RANGE);
                    var nearby = player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, aabb,
                            e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive());
                    if (!nearby.isEmpty()) {
                        var randTarget = nearby.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(nearby.size()));
                        MarkManager.apply(randTarget, 1200);
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

                // 效果牌/立牌/事件/筹码提供的防御力始终按 1 防御力 = 2 护甲值折算,
                // 即使处于骰神赐福也作为护甲值计入基础防御;仅战斗防御牌直接作为防御点加入。
                double rawArmor = Math.min(target.getArmorValue(), 20);
                double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
                double effectiveArmor = Math.max(0, Math.min(rawArmor + modifierDefense * 2.0, 20));
                defensePower = 2
                        + effectiveArmor / (target instanceof Player ? 2.0 : 4.0)
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
            if (isFateGuidanceActive(player)) {
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
            }
        }

        // 吸血鬼立牌(papara)主动"嘬一口":攻击时恢复骰神赐福最终伤害的一半生命(取整,至少 1 点)
        if (!player.level().isClientSide() && player.hasEffect(ModEffects.PAPARA_BITE)) {
            player.heal(Math.max(1, (int) finalDmg / 2));
        }

        if (hasShadowStrike && !player.level().isClientSide()) {
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        }

        // === DICE STONE CONSUMPTION (与赐福触发逻辑一致:仅在触发骰神赐福的那次攻击消耗一次耐久;
        //      赐福效果期间卡牌攻击/防御加成持续生效、每次攻击独立随机判定,不再消耗耐久) ===
        if (!player.level().isClientSide() && triggeredBlessing && !enhancement.appliedStones().isEmpty()) {
            List<AppliedStone> newStones = new ArrayList<>();
            int attackCostFreed = 0;
            int defenseCostFreed = 0;
            boolean dirty = false;
            for (AppliedStone stone : enhancement.appliedStones()) {
                // 防御牌:玩家主动触发骰神赐福时不再消耗耐久,仅保留在骰子内
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
                    if (stone.type().startsWith("defense_")) {
                        defenseCostFreed += MisakiSignItem.effectiveCost(player, stone.type());
                    } else {
                        attackCostFreed += MisakiSignItem.effectiveCost(player, stone.type());
                    }
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
                                enhancement.usedDefenseCost() - defenseCostFreed,
                                enhancement.maxDefenseCost(),
                                enhancement.starLevel(),
                                newStones
                        ));
            }
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

    // 防御牌耐久消耗:玩家处于骰神赐福中受到伤害时,消耗所有防御卡 1 点耐久
    private static void consumeDefenseCardDurability(Player defender, ItemStack diceStack, WeaponEnhancement enh) {
        if (diceStack.isEmpty() || enh == null) return;
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

    // 骰神赐福结束时:若骰子内装备了"蓄力",消耗蓄力并返还一张"全力攻击"卡
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

    // 蓄力兜底:当玩家没有骰神赐福但骰子仍残留蓄力时,移除蓄力并返还全力攻击
    private static void returnChargeCardIfBlessingEnded(Player player) {
        if (player.level().isClientSide()) return;
        if (player.hasEffect(ModEffects.DICE_BLESSING)) return;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        var diceResult = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        if (diceResult.isEmpty()) return;
        ItemStack dice = diceResult.get().stack();
        WeaponEnhancement enh = dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), null);
        if (enh == null) return;
        boolean foundCharge = false;
        int costFreed = 0;
        List<AppliedStone> newStones = new ArrayList<>();
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
    @SubscribeEvent
    public static void onMarkExpired(MobEffectEvent.Expired event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null
                || effect.getEffect().value() != ModEffects.MARKED.get()) {
            return;
        }
        if (effect.getAmplifier() > 0) {
            entity.addEffect(new MobEffectInstance(ModEffects.MARKED, 1200, effect.getAmplifier() - 1, false, true));
        } else {
            // 标记层数归零:同时移除伴随的"高亮"效果
            entity.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
        }
    }

    // 狂暴:受到任意来源伤害 +1×等级(非骰战伤害也生效,LOWEST 优先级在骰战结算后追加)
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
                    damage *= isFateGuidanceActive(attacker) ? (1.0 + (ratio - 1.0) * 0.5) : ratio;
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
                && isFateGuidanceActive(attacker2)) {
            multiplier += 0.20f;
        }
        event.setNewDamage(event.getNewDamage() * multiplier);
    }

    // 玩家是否持有骰子(curio 骰子槽)
    private static boolean attackerHasDiceCurio(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(DiceCurioItem::isDiceItem).isPresent();
    }

    // 吸血鬼立牌(papara)主动"嘬一口":受伤时恢复单次受到伤害的一半生命(取整,至少 1 点)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPaparaBiteHurtHeal(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!player.hasEffect(ModEffects.PAPARA_BITE)) return;
        int heal = Math.max(1, (int) event.getNewDamage() / 2);
        player.heal(heal);
    }

    // 占星师立牌被动:带"虚弱印记"的目标被击杀时,仅释放该印记的玩家获得 3 星币与一张"命运的指引"(绑定获得者)
    @SubscribeEvent
    public static void onWeakMarkKill(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!target.hasEffect(ModEffects.WEAK_MARK)) return;
        Optional<UUID> source = ModAttachments.getWeakMarkSource(target);
        if (source.isEmpty()) return;
        if (target.level().getPlayerByUUID(source.get()) instanceof Player applier) {
            HaiqingSignItem.grantWeakMarkKillReward(applier);
        }
    }

    // 虚弱印记结束(计时归零或目标死亡):清除印记来源
    @SubscribeEvent
    public static void onWeakMarkExpired(MobEffectEvent.Expired event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null
                || effect.getEffect().value() != ModEffects.WEAK_MARK.get()) return;
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        ModAttachments.setWeakMarkSource(entity, Optional.empty());
    }

    // 秘密侦探立牌被动:击杀带"标记"目标获得随机战斗牌。
    // 通过立牌击杀钩子分发(见 BonnieSignItem.onKill);"隐匿调查"击杀事件由下方全局处理器统一触发。
    @SubscribeEvent
    public static void onBonnieKill(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        BaseSignItem.invokeKillHooks(killer, target);
    }

    // 击杀"隐匿调查"目标 → 触发调查阶段事件(全局处理,不要求击杀者佩戴秘密侦探立牌)
    @SubscribeEvent
    public static void onUndercoverInvestigationKill(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!target.hasEffect(ModEffects.UNDERCOVER_INVESTIGATION)) return;
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        java.util.Optional<java.util.UUID> source = ModAttachments.getUndercoverSource(target);
        if (source.isEmpty()) return;
        Player applier = target.level().getPlayerByUUID(source.get());
        if (applier == null) return;
        int markLevel = MarkManager.getLevel(target);
        InvestigationEventUtil.triggerByKill(killer, applier, markLevel);
    }

    // 诅咒之剑:每击杀 1 个 20 血以上敌对目标,攻击力 +1(上限由配置决定)
    @SubscribeEvent
    public static void onCursedSwordKill(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!(target instanceof Enemy) || target.getMaxHealth() <= 20) return;
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        CursedSwordChipItem.onKill(killer);
    }

    // 本 Mod 创建的自定义效果不可被牛奶/蜂蜜/effect clear 等外部方式清除;仅玩家死亡可清除
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onModEffectRemovalPrevented(MobEffectEvent.Remove event) {
        if (event.getEntity().level().isClientSide()) return;
        // 死亡时允许清除,保证死亡后效果状态能正常重置
        if (event.getEntity().isDeadOrDying()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null) return;
        // 诅咒之剑卸下筹码时允许主动移除青之诅咒
        if (effect.getEffect().value() == ModEffects.BLUE_CURSE.get()
                && CursedSwordChipItem.isRemovingBlueCurse()) {
            return;
        }
        String effectId = effect.getEffect().getRegisteredName();
        if (effectId != null && effectId.startsWith(AstralDiceMod.MODID + ":")) {
            event.setCanceled(true);
        }
    }

    // 隐匿调查效果移除(目标死亡/被清除):清除来源
    @SubscribeEvent
    public static void onUndercoverRemoved(MobEffectEvent.Remove event) {
        if (event.isCanceled()) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null || effect.getEffect() == null
                || effect.getEffect().value() != ModEffects.UNDERCOVER_INVESTIGATION.get()) return;
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        ModAttachments.setUndercoverSource(entity, Optional.empty());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        // 治愈:每 tick 驱动(内部按 30 秒结算 + 每 tick 刷新效果倒计时)
        HealingManager.tick(player);
        // 美工刀状态效果:装备且满血时显示效果图标,否则移除
        updateCutterEffect(player);
        if (player.tickCount % 20 != 0) return;
        // 事件系统:护甲惩罚到期移除
        ArmorPenaltyHandler.tick(player);
        // 效果牌出牌周期计时
        com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.tick(player);
        // 以毒攻毒:中毒结束后给予隐藏图标的生命恢复 II
        com.merlinkitsune.astral_dice.item.card.FightPoisonWithPoisonCardItem.tick(player);
        // 大当家立牌:1 分钟内没有触发骰神赐福 → 养精蓄锐 +1 层
        com.merlinkitsune.astral_dice.item.sign.FenSignItem.tick(player);
        // 蓄力兜底:若赐福已结束但骰子仍残留蓄力,返还全力攻击
        returnChargeCardIfBlessingEnded(player);

    }

    // 美工刀-初级/锋利状态效果:佩戴对应筹码且生命值 ≥60% 或处于"嘬一口"状态时显示效果图标,否则移除
    private static void updateCutterEffect(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        boolean hasCutter = false;
        boolean hasBlade = false;
        if (curios.isPresent()) {
            hasCutter = curios.get().findFirstCurio(s -> s.is(ModItems.CUTTER_CHIP.get())).isPresent();
            hasBlade = curios.get().findFirstCurio(s -> s.is(ModItems.CUTTER_BLADE_CHIP.get())).isPresent();
        }
        boolean fullHp = player.getHealth() >= player.getMaxHealth() * 0.6f || player.hasEffect(ModEffects.PAPARA_BITE);
        if (hasCutter && fullHp) {
            player.addEffect(new MobEffectInstance(ModEffects.CUTTER_READY, 100, 0, false, true, true));
        } else {
            player.removeEffect(ModEffects.CUTTER_READY);
        }
        if (hasBlade && fullHp) {
            player.addEffect(new MobEffectInstance(ModEffects.CUTTER_BLADE_READY, 100, 0, false, true, true));
        } else {
            player.removeEffect(ModEffects.CUTTER_BLADE_READY);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        if (entity instanceof WitherBoss || entity instanceof Warden) {
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                    entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                    new ItemStack(ModItems.STAR_PLATE.get(), 1)));
        } else if (entity instanceof Monster) {
            if (ThreadLocalRandom.current().nextFloat() < 0.003f) {
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                        entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                        new ItemStack(ModItems.STAR_PLATE.get(), 1)));
            }
        }
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (left.is(ModItems.DICE.get()) || left.is(ModItems.GOLDEN_DICE.get()) || left.is(ModItems.DIAMOND_DICE.get())
                || left.is(ModItems.NETHERITE_DICE.get())) {
            if (!right.is(ModItems.STAR_COIN.get())) return;
            WeaponEnhancement enhancement = left.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
            if (enhancement.starLevel() >= 3) return;
            int req = switch (enhancement.starLevel()) {
                case 0 -> 15;
                case 1 -> 20;
                case 2 -> 25;
                default -> -1;
            };
            if (right.getCount() < req) return;
            ItemStack output = left.copy();
            output.set(ModDataComponents.WEAPON_ENHANCEMENT.get(),
                    new WeaponEnhancement(
                            enhancement.usedCost(),
                            GameplayConstants.cardCostForStar(enhancement.starLevel() + 1),
                            enhancement.usedDefenseCost(),
                            GameplayConstants.cardCostForStar(enhancement.starLevel() + 1),
                            enhancement.starLevel() + 1,
                            enhancement.appliedStones()
                    ));
            event.setOutput(output);
            event.setMaterialCost(req);
            event.setCost(10);
            return;
        }
    }

    // ============ 立牌 tooltip 统一格式辅助 ============
    // 格式模板:
    //   <按键提示>
    //
    //   主动技能（技能名）:
    //   <标准项>
    //   <带子项>:
    //   - <子项>
    //   被动技能（技能名）:
    //   <标准项>
    //   <带子项>:
    //   - <子项>
    //
    //   <备注信息(紫色,无符号)>
    //
    //   <立牌计数器>
    // 颜色约定:标题=金(§6)、时间/冷却=黄(§e)、效果/数值=青(§b)/绿(§a)、负面=红(§c)、普通=灰(§7)

    // 主动技能按键提示:置于 tooltip 最上方独立一行,并在末尾追加一个空行
    private static void addSignKeyHint(List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.astral_dice.sign.key_hint", signKeyName())
                .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.empty());
    }

    // 主动技能标题(金色,含技能名;冷却倒计时由下方"冷却中"行单独展示)
    private static void addSignActiveTitle(List<Component> tooltip, String skillName) {
        tooltip.add(Component.translatable("tooltip.astral_dice.sign.active_title", skillName)
                .withStyle(ChatFormatting.GOLD));
    }

    // 被动技能标题(金色,含技能名)
    private static void addSignPassiveTitle(List<Component> tooltip, String skillName) {
        tooltip.add(Component.translatable("tooltip.astral_dice.sign.passive_title", skillName)
                .withStyle(ChatFormatting.GOLD));
    }

    // 备注区多行内容(紫色;无列表符号;无缩进;行内 § 码可覆盖,时间保持黄色)
    private static void addSignNoteLines(List<Component> tooltip, String langKey, Object... args) {
        String text = Component.translatable(langKey, args).getString();
        for (String line : text.split("\n")) {
            if (line.isBlank()) continue;
            tooltip.add(Component.literal("§d" + line.trim()));
        }
    }

    // 读取 lang key 的多行描述,逐行添加(前缀灰色;行内 § 码着色重点)
    // 约定:lang 中每行以 "\n" 分隔;以两个空格开头的行视为子项(带 "- " 符号),其余为普通项(无符号)。
    // 渲染:无缩进;子项加 "§7- " 前缀。
    private static void addSignLines(List<Component> tooltip, String langKey, Object... args) {
        String text = Component.translatable(langKey, args).getString();
        for (String line : text.split("\n")) {
            if (line.isBlank()) continue;
            if (line.startsWith("  ")) {
                // 子项:带符号(前缀灰色,行内 § 码覆盖)
                tooltip.add(Component.literal("§7- " + line.trim()));
            } else {
                // 普通项:无符号
                tooltip.add(Component.literal("§7" + line));
            }
        }
    }

    // 立牌自身计数器(金色数值,其余灰色)
    private static void addSignCounter(List<Component> tooltip, String langKey, Object... args) {
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable(langKey, args).withStyle(ChatFormatting.GRAY));
    }

    // 冷却中提示(红色)
    private static void addSignCooldownRemaining(List<Component> tooltip, Player p) {
        if (p == null) return;
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(p);
        int remainingTicks = cdEnd > 0 ? (int) (cdEnd - p.level().getGameTime()) : 0;
        if (remainingTicks > 0) {
            tooltip.add(Component.translatable("tooltip.astral_dice.sign.cooldown_remaining", remainingTicks / 20)
                    .withStyle(ChatFormatting.RED));
        }
    }

    // 秒数 → 立牌 tooltip 时间格式(蓝):§9MM:SS§7(如 60 → §91:00§7)
    private static String formatSignTime(int seconds) {
        return String.format("§9%d:%02d§7", seconds / 60, seconds % 60);
    }

    // 立牌主动技能按键显示名(客户端取实际映射,服务端/异常回退 "J")
    private static String signKeyName() {
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            try {
                return com.merlinkitsune.astral_dice.client.KeyBindingSetup.ACTIVATE_SIGN_KEY
                        .getTranslatedKeyMessage().getString();
            } catch (Throwable ignored) {
            }
        }
        return "J";
    }

    // 卡牌栏按键显示名(客户端取实际映射,服务端/异常回退 "H")
    private static String cardInventoryKeyName() {
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            try {
                return com.merlinkitsune.astral_dice.client.KeyBindingSetup.OPEN_CARD_INVENTORY_KEY
                        .getTranslatedKeyMessage().getString();
            } catch (Throwable ignored) {
            }
        }
        return "H";
    }

    // 效果牌 tooltip:当前出牌周期出牌数(current/max)
    private static void addEffectCardPlayCountTooltip(List<Component> tooltip, Player p) {
        if (p == null) {
            tooltip.add(Component.translatable("tooltip.astral_dice.card.play_count", "?", "?")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.astral_dice.card.play_count",
                            EffectCardPeriod.getPlayCount(p), EffectCardPeriod.getMaxAllowed(p))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // 效果牌 tooltip:当前周期已激活伤害效果牌的总伤害加成
    private static void addActiveDamageBonusTooltip(List<Component> tooltip, Player p) {
        if (p == null) {
            tooltip.add(Component.translatable("tooltip.astral_dice.card.active_damage_bonus", "?")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        int bonus = 0;
        int komachi = ModAttachments.getKomachiDamageBonus(p);
        if (p.hasEffect(ModEffects.MONSTER_LASER)) bonus += 4 + komachi;
        if (p.hasEffect(ModEffects.MONSTER_BRICK)) bonus += 6 + komachi;
        if (p.hasEffect(ModEffects.ORBITAL_STRIKE)) bonus += 8 + komachi;
        if (p.hasEffect(ModEffects.DIRECTIONAL_BLAST)) bonus += 5 + komachi;
        if (p.hasEffect(ModEffects.LIVING_BOOK_PAGE)) {
            int pages = Math.min(ModAttachments.getRinPages(p), GameplayConstants.LIVING_BOOK_PAGE_BONUS_CAP);
            bonus += 2 + pages + komachi;
        }
        tooltip.add(Component.translatable("tooltip.astral_dice.card.active_damage_bonus", bonus)
                .withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        var tooltip = event.getToolTip();
        Player player = event.getEntity() instanceof Player p ? p : null;

        if (stack.is(ModItems.DICE.get()) || stack.is(ModItems.GOLDEN_DICE.get()) || stack.is(ModItems.DIAMOND_DICE.get())
                || stack.is(ModItems.NETHERITE_DICE.get())) {
            WeaponEnhancement enhancement = stack.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), null);
            int starLevel = 0;
            int maxCost = 3;
            List<AppliedStone> stones = List.of();
            int usedCost = 0;
            int maxDefenseCost = 3;
            int usedDefenseCost = 0;
            if (enhancement != null) {
                starLevel = enhancement.starLevel();
                maxCost = GameplayConstants.cardCostForStar(enhancement.starLevel());
                stones = enhancement.appliedStones();
                usedCost = enhancement.usedCost();
                maxDefenseCost = GameplayConstants.cardCostForStar(enhancement.starLevel());
                usedDefenseCost = enhancement.usedDefenseCost();
            }
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.dice_desc",
                    GameplayConstants.DICE_BLESSING_DURATION_SECONDS, cardInventoryKeyName())
                    .withStyle(ChatFormatting.GOLD));
            if (starLevel > 0) {
                tooltip.add(Component.translatable("tooltip.astral_dice.star_level", starLevel)
                        .withStyle(ChatFormatting.GOLD));
            }
            if (starLevel < 3) {
                int req = switch (starLevel) {
                    case 0 -> 15;
                    case 1 -> 20;
                    case 2 -> 25;
                    default -> -1;
                };
                tooltip.add(Component.translatable("tooltip.astral_dice.card.upgrade_hint", starLevel, starLevel + 1, req)
                        .withStyle(ChatFormatting.YELLOW));
            }
            String cost = usedCost + "/" + maxCost;
            tooltip.add(Component.translatable("tooltip.astral_dice.cost", cost)
                    .withStyle(ChatFormatting.GRAY));
            String defCost = usedDefenseCost + "/" + maxDefenseCost;
            tooltip.add(Component.translatable("tooltip.astral_dice.defense_cost", defCost)
                    .withStyle(ChatFormatting.GRAY));
            if (!stones.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.astral_dice.applied_stones")
                        .withStyle(ChatFormatting.GREEN));
                for (AppliedStone stone : stones) {
                    if ("shadow_strike".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §5暗影突袭 §e+3§7 固定 §7| 黑暗(§93秒§7) §7[剩余:§e" + stone.uses() + "§7]")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    if ("meito".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §d名刀·噶呜切 §e1~20§7 骰子 §7[剩余:§e" + stone.uses() + "§7]")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    if ("charge".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §e蓄力 §e+5§7 固定(赐福期间) §7| 赐福结束后返还§c全力攻击")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    if ("full_power".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §c全力攻击 §e+6§7 攻击力 §e本次攻击的最终攻击力+50%§7 §7[剩余:§e" + stone.uses() + "§7]")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    if ("defense_medium".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §b中 §e1~3§7 防御 §7[剩余:§e" + stone.uses() + "§7]")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    if ("defense_large".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §d大 §e1~6§7 防御 §7[剩余:§e" + stone.uses() + "§7]")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    if ("defense_epic".equals(stone.type())) {
                        tooltip.add(Component.literal(" §7- §6特大 §e1~10§7 防御 §7[剩余:§e" + stone.uses() + "§7]")
                                .withStyle(ChatFormatting.GRAY));
                        continue;
                    }
                    String stoneName = switch (stone.type()) {
                        case "medium" -> "§b中";
                        case "large" -> "§d大";
                        case "epic" -> "§6特大";
                        default -> stone.type();
                    };
                    String range = switch (stone.type()) {
                        case "medium" -> "1~3";
                        case "large" -> "1~6";
                        case "epic" -> "1~10";
                        default -> "?";
                    };
                    tooltip.add(Component.literal(" §7- " + stoneName + " §e+" + range + "§7 骰子 §7[剩余:§e" + stone.uses() + "§7]")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }

        if (stack.is(ModItems.ATTACK_CARD_MEDIUM.get())) {
            tooltip.add(Component.empty());
            // 费用:黄色 "Cost: " + ⨀(每 1 费一个符号),置于 tooltip 最上方
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("medium", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("medium"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.attack_medium", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_LARGE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("large", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("large"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.attack_large", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_EPIC.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("epic", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("epic"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.attack_epic", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_SHADOW_STRIKE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("shadow_strike", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("shadow_strike"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.shadow_strike", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_MEITO.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("meito", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("meito"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.meito", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_CHARGE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("charge", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("charge"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.charge", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.ATTACK_CARD_FULL_POWER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("full_power", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("full_power"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.full_power", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.DEFENSE_CARD_MEDIUM.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("defense_medium", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("defense_medium"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.defense_medium", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.DEFENSE_CARD_LARGE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("defense_large", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("defense_large"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.defense_large", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.DEFENSE_CARD_EPIC.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("Cost: " + "⨀".repeat(
                            com.merlinkitsune.astral_dice.combat.CardRegistry.cost("defense_epic", player)))
                    .withStyle(ChatFormatting.YELLOW));
            int uses = stack.getOrDefault(ModDataComponents.CARD_USES.get(), AppliedStone.defaultUses("defense_epic"));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.defense_epic", uses)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.EFFECT_CARD_KING_POWER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.king_power").withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS).withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.EFFECT_CARD_BERSERK.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("effect.astral_dice.berserk.description")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS).withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.EFFECT_CARD_UNWAVERING.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("effect.astral_dice.unwavering.description")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS).withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.EFFECT_CARD_FIGHT_POISON_WITH_POISON.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("effect.astral_dice.fight_poison_with_poison.description")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown", GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS).withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.BLANK_SIGN.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.blank_sign")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.BLANK_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.blank_chip")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.PARUNAN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "套现");
            addSignLines(tooltip, "tooltip.astral_dice.card.parunan_active");
            addSignPassiveTitle(tooltip, "传奇商人");
            addSignLines(tooltip, "tooltip.astral_dice.card.parunan_passive",
                    formatSignTime(GameplayConstants.PARUNAN_PASSIVE_INTERVAL_SECONDS),
                    GameplayConstants.MAX_STARLIGHT);
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.card.parunan_starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
            }
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.JASMINE_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "能量过载");
            addSignLines(tooltip, "tooltip.astral_dice.card.jasmine_active");
            addSignPassiveTitle(tooltip, "移动充能");
            addSignLines(tooltip, "tooltip.astral_dice.card.jasmine_passive",
                    GameplayConstants.JASMINE_MAX_BONUS);
            int atkBonus = JasmineSignItem.getAttackBonus(stack);
            int defBonus = JasmineSignItem.getDefenseBonus(stack);
            addSignCounter(tooltip, "tooltip.astral_dice.card.jasmine_bonus", atkBonus, defBonus);
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.MISAKI_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "樱花裂空斩");
            addSignLines(tooltip, "tooltip.astral_dice.card.misaki_active");
            addSignPassiveTitle(tooltip, "剑气");
            addSignLines(tooltip, "tooltip.astral_dice.card.misaki_passive");
            // 神秘遗物+ 联动描述:仅当安装神秘遗物+ 模组时展示(置于备注区,紫色,无标题)
            if (net.neoforged.fml.ModList.get().isLoaded("enigmaticlegacyplus")) {
                tooltip.add(Component.empty());
                addSignNoteLines(tooltip, "tooltip.astral_dice.card.misaki_enigmatic");
            }
            int stacks = stack.getOrDefault(ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);
            addSignCounter(tooltip, "tooltip.astral_dice.card.misaki_stacks", stacks);
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.MIMI_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "商品补货");
            addSignLines(tooltip, "tooltip.astral_dice.card.mimi_active");
            addSignPassiveTitle(tooltip, "过期回收");
            addSignLines(tooltip, "tooltip.astral_dice.card.mimi_passive");
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.LULU_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "治愈粘液");
            addSignLines(tooltip, "tooltip.astral_dice.card.lulu_active",
                    GameplayConstants.LULU_ACTIVE_RANGE, GameplayConstants.LULU_ACTIVE_RANGE);
            addSignPassiveTitle(tooltip, "细胞分裂");
            addSignLines(tooltip, "tooltip.astral_dice.card.lulu_passive");
            if (event.getEntity() instanceof Player p) {
                int healingPoints = HealingManager.getPoints(p);
                if (healingPoints > 0) {
                    addSignCounter(tooltip, "tooltip.astral_dice.card.lulu_healing", healingPoints);
                }
            }
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.KOMACHI_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "忍术连击");
            addSignLines(tooltip, "tooltip.astral_dice.card.komachi_active");
            addSignPassiveTitle(tooltip, "复制者");
            addSignLines(tooltip, "tooltip.astral_dice.card.komachi_passive");
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.card.komachi_damage_bonus",
                        ModAttachments.getKomachiDamageBonus(p),
                        com.merlinkitsune.astral_dice.component.GameplayConstants.KOMACHI_DAMAGE_BONUS_MAX);
            }
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.FLASHLIGHT_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.flashlight")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.CUTTER_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.cutter")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.CUTTER_BLADE_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.cutter_blade")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.SCOPE_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.scope")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.EAGLE_SCOPE_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.eagle_scope")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.MEDKIT_EMERGENCY_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.medkit_emergency")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.MEDKIT_COMPLETE_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.medkit_complete")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.VITAMIN_PILL_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.vitamin_pill")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.TARGET_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.target",
                            GameplayConstants.TARGET_CHIP_RANGE)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.MARKER_SPRAYER_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.marker_sprayer")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.MAGIC_TOME_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.magic_tome")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.BIG_BACKPACK_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.big_backpack")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.NINJA_STAR_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.ninja_star")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.HAND_FAN_SMALL_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.hand_fan_small")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.HAND_FAN_BIG_CHIP.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.hand_fan_big",
                            GameplayConstants.HAND_FAN_BIG_RANGE)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.EIGHT_SIDED_DICE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.eight_sided_dice",
                            GameplayConstants.MAX_STARLIGHT)
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
            }
        }

        // === 新筹码 tooltip ===
        if (stack.is(ModItems.ATM.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.atm")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.BANK_CARD_LOW.get()) || stack.is(ModItems.BANK_CARD_HIGH.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(stack.is(ModItems.BANK_CARD_LOW.get())
                            ? "tooltip.astral_dice.chip.bank_card_low"
                            : "tooltip.astral_dice.chip.bank_card_high")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.BANK_CARD_UNLIMITED.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.bank_card_unlimited")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.BOXING_GLOVES_LOW.get()) || stack.is(ModItems.BOXING_GLOVES_MEDIUM.get())
                || stack.is(ModItems.BOXING_GLOVES_HIGH.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(stack.is(ModItems.BOXING_GLOVES_LOW.get())
                            ? "tooltip.astral_dice.chip.boxing_gloves_low"
                            : stack.is(ModItems.BOXING_GLOVES_MEDIUM.get())
                            ? "tooltip.astral_dice.chip.boxing_gloves_medium"
                            : "tooltip.astral_dice.chip.boxing_gloves_high")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.SPEED_SKATES_LOW.get()) || stack.is(ModItems.SPEED_SKATES_MEDIUM.get())
                || stack.is(ModItems.SPEED_SKATES_HIGH.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(stack.is(ModItems.SPEED_SKATES_LOW.get())
                            ? "tooltip.astral_dice.chip.speed_skates_low"
                            : stack.is(ModItems.SPEED_SKATES_MEDIUM.get())
                            ? "tooltip.astral_dice.chip.speed_skates_medium"
                            : "tooltip.astral_dice.chip.speed_skates_high")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.MOTO_HELMET_LOW.get()) || stack.is(ModItems.MOTO_HELMET_MEDIUM.get())
                || stack.is(ModItems.MOTO_HELMET_HIGH.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(stack.is(ModItems.MOTO_HELMET_LOW.get())
                            ? "tooltip.astral_dice.chip.moto_helmet_low"
                            : stack.is(ModItems.MOTO_HELMET_MEDIUM.get())
                            ? "tooltip.astral_dice.chip.moto_helmet_medium"
                            : "tooltip.astral_dice.chip.moto_helmet_high")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.SANDWICH_LOW.get()) || stack.is(ModItems.SANDWICH_MEDIUM.get())
                || stack.is(ModItems.SANDWICH_HIGH.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable(stack.is(ModItems.SANDWICH_LOW.get())
                            ? "tooltip.astral_dice.chip.sandwich_low"
                            : stack.is(ModItems.SANDWICH_MEDIUM.get())
                            ? "tooltip.astral_dice.chip.sandwich_medium"
                            : "tooltip.astral_dice.chip.sandwich_high")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.MAGIC_QUIVER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.magic_quiver")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.BUFFER_SHIELD.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.buffer_shield")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.STAR_COIN_HAMMER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.star_coin_hammer")
                    .withStyle(ChatFormatting.GRAY));
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.starlight",
                        StarLightManager.get(p), StarLightManager.getCap());
            }
        }
        if (stack.is(ModItems.CURSED_SWORD.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.cursed_sword",
                            GameplayConstants.CURSED_SWORD_BONUS_MAX)
                    .withStyle(ChatFormatting.GRAY));
            if (net.neoforged.fml.ModList.get().isLoaded("enigmaticlegacyplus")) {
                tooltip.add(Component.translatable("tooltip.astral_dice.chip.cursed_sword_enigmatic")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.chip.cursed_sword_bonus",
                        ModAttachments.getCursedSwordBonus(p));
            }
        }
        if (stack.is(ModItems.REVENGE_HALBERD.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.chip.revenge_halberd")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stack.is(ModItems.PADMAN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "真的生气了");
            addSignLines(tooltip, "tooltip.astral_dice.card.padman_active");
            addSignPassiveTitle(tooltip, "毫无主见");
            addSignLines(tooltip, "tooltip.astral_dice.card.padman_passive",
                    formatSignTime(GameplayConstants.PADMAN_REFRESH_SECONDS));
            int atkBonus = stack.getOrDefault(ModDataComponents.PADMAN_ATK_BONUS.get(), 0);
            int defBonus = stack.getOrDefault(ModDataComponents.PADMAN_DEF_BONUS.get(), 0);
            addSignCounter(tooltip, "tooltip.astral_dice.card.padman_bonus", atkBonus, defBonus);
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.FANNY_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "麻烦制造者");
            addSignLines(tooltip, "tooltip.astral_dice.card.fanny_active");
            addSignPassiveTitle(tooltip, "华点发现");
            addSignLines(tooltip, "tooltip.astral_dice.card.fanny_passive");
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.RIN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "活体书页");
            addSignLines(tooltip, "tooltip.astral_dice.card.rin_active");
            addSignPassiveTitle(tooltip, "调查发现");
            addSignLines(tooltip, "tooltip.astral_dice.card.rin_passive", 32);
            if (event.getEntity() instanceof Player p) {
                int pages = Math.min(ModAttachments.getRinPages(p), GameplayConstants.LIVING_BOOK_PAGE_BONUS_CAP);
                addSignCounter(tooltip, "tooltip.astral_dice.card.rin_bonus", pages);
            }
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.LIVING_BOOK_PAGE.get())) {
            tooltip.add(Component.empty());
            if (event.getEntity() instanceof Player p) {
                // 活体书页伤害 = 基础 2 + 调查员(rin)已使用数量 + 忍者立牌效果牌伤害增益
                int pages = Math.min(ModAttachments.getRinPages(p), GameplayConstants.LIVING_BOOK_PAGE_BONUS_CAP);
                // 组件基础色为灰(普通文本);行内颜色码:数值=黄 §e、时间=蓝 §9
                tooltip.add(Component.translatable("tooltip.astral_dice.card.living_book_page",
                                2 + pages + ModAttachments.getKomachiDamageBonus(p))
                        .withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("tooltip.astral_dice.card.living_book_page", "?")
                        .withStyle(ChatFormatting.GRAY));
            }
            addEffectCardPlayCountTooltip(tooltip, player);
            addActiveDamageBonusTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.exclusive_owner")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (stack.is(ModItems.MONSTER_LASER_CARD.get()) || stack.is(ModItems.MONSTER_BRICK_CARD.get())
                || stack.is(ModItems.ORBITAL_STRIKE_CARD.get()) || stack.is(ModItems.DIRECTIONAL_BLAST_CARD.get())) {
            String tooltipKey = stack.is(ModItems.MONSTER_LASER_CARD.get()) ? "tooltip.astral_dice.card.monster_laser"
                    : stack.is(ModItems.MONSTER_BRICK_CARD.get()) ? "tooltip.astral_dice.card.monster_brick"
                    : stack.is(ModItems.ORBITAL_STRIKE_CARD.get()) ? "tooltip.astral_dice.card.orbital_strike"
                    : "tooltip.astral_dice.card.directional_blast";
            // 伤害数值显示:基础 + 忍者立牌效果牌伤害增益(观看者佩戴忍者立牌时显示加成后的数值)
            int baseDamage = stack.is(ModItems.MONSTER_LASER_CARD.get()) ? 4
                    : stack.is(ModItems.MONSTER_BRICK_CARD.get()) ? 6
                    : stack.is(ModItems.ORBITAL_STRIKE_CARD.get()) ? 8 : 5;
            int ninjaBonus = event.getEntity() instanceof Player p ? ModAttachments.getKomachiDamageBonus(p) : 0;
            tooltip.add(Component.empty());
            // 组件基础色为灰(普通文本);行内颜色码:数值=黄 §e、时间=蓝 §9
            tooltip.add(Component.translatable(tooltipKey, baseDamage + ninjaBonus)
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            addActiveDamageBonusTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
        }
        // === 新效果牌(治疗/互动) ===
        if (stack.is(ModItems.CHOCOLATE_CAKE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.chocolate_cake")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.HAMBURGER.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.hamburger")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.LUXURY_FEAST.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.luxury_feast")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.YOU_HAVE_I_HAVE.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.you_have_i_have")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.EXPRESS_DELIVERY.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.express_delivery")
                    .withStyle(ChatFormatting.GRAY));
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
        }
        if (stack.is(ModItems.HAIQING_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "虚弱印记");
            addSignLines(tooltip, "tooltip.astral_dice.card.haiqing_active");
            addSignPassiveTitle(tooltip, "幸运星");
            addSignLines(tooltip, "tooltip.astral_dice.card.haiqing_passive");
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.FATE_GUIDANCE_CARD.get())) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.astral_dice.card.fate_guidance_desc")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.fate_saturation")
                    .withStyle(ChatFormatting.GRAY));
            // 联动条目:仅安装相关模组时显示(紫色,无编号)
            if (net.neoforged.fml.ModList.get().isLoaded("enigmaticlegacyplus")) {
                tooltip.add(Component.translatable("tooltip.astral_dice.card.fate_curse_mitigation")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks")) {
                tooltip.add(Component.translatable("tooltip.astral_dice.card.fate_spell_mana")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            addEffectCardPlayCountTooltip(tooltip, player);
            tooltip.add(Component.translatable("tooltip.astral_dice.card.effect_cooldown",
                            GameplayConstants.EFFECT_CARD_COOLDOWN_SECONDS)
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.astral_dice.card.exclusive_owner")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (stack.is(ModItems.PAPARA_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "嘬你一口");
            addSignLines(tooltip, "tooltip.astral_dice.card.papara_active");
            addSignPassiveTitle(tooltip, "可爱即正义");
            addSignLines(tooltip, "tooltip.astral_dice.card.papara_passive");
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.BONNIE_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "隐匿行动");
            addSignLines(tooltip, "tooltip.astral_dice.card.bonnie_active");
            addSignPassiveTitle(tooltip, "关键线索");
            addSignLines(tooltip, "tooltip.astral_dice.card.bonnie_passive");
            // 调查阶段事件说明:置于备注区(紫色,无标题)
            tooltip.add(Component.empty());
            addSignNoteLines(tooltip, "tooltip.astral_dice.card.investigation_desc");
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
        if (stack.is(ModItems.FEN_SIGN.get())) {
            tooltip.add(Component.empty());
            addSignKeyHint(tooltip);
            addSignActiveTitle(tooltip, "运攻");
            addSignLines(tooltip, "tooltip.astral_dice.card.fen_active");
            addSignPassiveTitle(tooltip, "养精蓄锐");
            addSignLines(tooltip, "tooltip.astral_dice.card.fen_passive");
            if (event.getEntity() instanceof Player p) {
                addSignCounter(tooltip, "tooltip.astral_dice.card.fen_recharge",
                        ModAttachments.getFenRecharge(p),
                        com.merlinkitsune.astral_dice.item.sign.FenSignItem.MAX_RECHARGE);
            }
            addSignCooldownRemaining(tooltip, event.getEntity() instanceof Player p ? p : null);
        }
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        var name = event.getName();
        if (!name.getPath().startsWith("chests/")) return;

        LootTable table = event.getTable();

        // Prevent duplicate pool addition on reload
        if (table.getPool("astral_dice:star_coin") != null) return;

        boolean isBuriedTreasure = name.toString().equals("minecraft:chests/buried_treasure");
        boolean isEndCity = name.toString().equals("minecraft:chests/end_city_treasure");

        // Star Coin: 5% all chests (1-2), 9% in end city
        table.addPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(isEndCity ? 0.09f : 0.05f))
                .add(LootItem.lootTableItem(ModItems.STAR_COIN.get())
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2))))
                .name("astral_dice:star_coin")
                .build());

        // Blank Chip: ONLY in buried treasure (always)
        if (isBuriedTreasure) {
            table.addPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(LootItem.lootTableItem(ModItems.BLANK_CHIP.get()))
                    .name("astral_dice:blank_chip")
                    .build());
        }

        // Star Plate: 1% all chests, 5% in end city
        table.addPool(LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(isEndCity ? 0.05f : 0.01f))
                .add(LootItem.lootTableItem(ModItems.STAR_PLATE.get()))
                .name("astral_dice:star_plate")
                .build());
    }

    private static int rollDice(int max) {
        return ThreadLocalRandom.current().nextInt(1, max + 1);
    }

    private static int rollDice(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    // 近战武器攻击判定:仅允许剑/斧/重锤/三叉戟等近战武器触发骰神赐福
    private static boolean isMeleeWeaponAttack(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return false;
        if (held.is(Items.SHIELD)) return false;
        return held.getItem() instanceof SwordItem
                || held.getItem() instanceof AxeItem
                || held.getItem() instanceof MaceItem
                || held.getItem() instanceof TridentItem;
    }

    // 骰神赐福触发目标判定:敌对生物、非团队内玩家、已被激怒的中立生物,以及其余非被动动物实体
    static boolean isBlessingTarget(LivingEntity target, Player player) {
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

    // 维生素药丸:通过合成卡牌获得时触发
    @SubscribeEvent
    public static void onCardCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        ItemStack result = event.getCrafting();
        if (!result.isEmpty()) {
            VitaminPillChipItem.onCardGained(player, result);
        }
    }

    // 玩家死亡:移除全部治愈(清零点数并结束"治愈"效果)与骰神赐福效果,防止死亡残留
    @SubscribeEvent
    public static void onPlayerDeathClearEffects(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        HealingManager.clear(player);
        // 死亡时统一重置效果相关状态,避免效果被清除后附件残留
        ModAttachments.setDefenseCardConsumedThisBlessing(player, false);
        ModAttachments.setSignReadyType(player, 0);
        ModAttachments.setSignReadyExpire(player, 0);
        ModAttachments.setFenCleavePending(player, false);
        ModAttachments.setFenCleaveActive(player, false);
        ModAttachments.setKomachiExtraPlayActive(player, false);
        ModAttachments.setMagicQuiverTracking(player, false);
        ModAttachments.setMagicQuiverFirstCard(player, "");
        ModAttachments.setMagicQuiverCooldownEnd(player, 0);
        ModAttachments.setFateActiveUntil(player, 0);
        ModAttachments.setStarCoinHammerBonus(player, 0);
        ModAttachments.setCursedSwordBonus(player, 0);
        ModAttachments.setCursedSwordBlessingTriggered(player, false);
        player.removeEffect(ModEffects.BLUE_CURSE);
        ModAttachments.setInvestigationStage(player, 1);
        ModAttachments.setEffectCardPlayCount(player, 0);
        ModAttachments.setEffectCardCooldownEnd(player, 0);
        ModAttachments.setKomachiUseCount(player, 0);
        ModAttachments.setMagicTomeUseCount(player, 0);
        ModAttachments.setDamageEffectBonus(player, 0);
        ModAttachments.setDiceCurseRatio(player, 1.0f);
        player.removeEffect(ModEffects.DICE_BLESSING);
        player.removeEffect(ModEffects.HAIQING_READY);
        player.removeEffect(ModEffects.BONNIE_READY);
        player.removeEffect(ModEffects.INVESTIGATION_BONUS);
        player.removeEffect(ModEffects.FATE_GUIDANCE);
        player.removeEffect(ModEffects.FEN_FRENZY);
        player.removeEffect(ModEffects.KOMACHI_COUNT);
        player.removeEffect(ModEffects.MAGIC_TOME_COUNT);
    }

    // 命运的指引是否激活(功能由 attachment 截止时刻驱动;FATE_GUIDANCE 效果仅作状态显示)
    public static boolean isFateGuidanceActive(Player player) {
        long until = com.merlinkitsune.astral_dice.component.ModAttachments.getFateActiveUntil(player);
        return until > 0 && player.level().getGameTime() < until;
    }

    // 七咒第一诅咒处理:神秘遗物+ 模组(默认 NORMAL 优先级)先应用其配置/修正物品后的伤害倍率,
    // 本处理器在 LOWEST 捕获该【实际倍率】(amount/original,动态适配 painMultiplier 配置、大地誓约、
    // 救赎之戒转换等模组内修正),而非固化倍率。
    // - 骰战攻击(攻击者赐福激活+骰子+近战):不修改模组倍率,仅捕获存至目标侧,由骰战最终伤害使用;
    // - 非骰战攻击:命运的指引激活时按加幅减半(第一诅咒影响 -50%),未激活则保持模组倍率。
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onCurseMitigation(
            net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!hasEnigmaticCurse(player)) {
            // 未佩戴七咒(含已转换为救赎之戒):清理捕获,保持无倍率
            com.merlinkitsune.astral_dice.component.ModAttachments.setDiceCurseRatio(player, 1.0f);
            return;
        }
        float original = event.getOriginalAmount();
        float current = event.getAmount();
        float ratio = current > original ? current / original : 1.0f;

        // 是否骰战攻击(骰战攻击的最终伤害由骰战接管,此处仅捕获倍率,不修改伤害链)
        boolean diceCombat = event.getSource().getEntity() instanceof Player attacker
                && attacker.hasEffect(ModEffects.DICE_BLESSING)
                && attackerHasDiceCurio(attacker)
                && isMeleeWeaponAttack(attacker);
        if (diceCombat) {
            com.merlinkitsune.astral_dice.component.ModAttachments.setDiceCurseRatio(player, ratio);
            return;
        }

        // 非骰战攻击:命运的指引激活时第一诅咒影响 -50%(加幅减半)
        if (ratio > 1.0f
                && event.getSource().getEntity() instanceof Player attacker2
                && isFateGuidanceActive(attacker2)) {
            event.setAmount(original + (current - original) * 0.5f);
        }
        // 非骰战攻击不产生骰战捕获,清理
        com.merlinkitsune.astral_dice.component.ModAttachments.setDiceCurseRatio(player, 1.0f);
    }

    // 命运的指引·福运:激活期间所有食物提供的饱和度翻倍。
    // Finish 事件在原版 eat(更新食物数据)之前触发,此处预先补一份饱和度增量。
    @SubscribeEvent
    public static void onEatSaturationDouble(
            net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!isFateGuidanceActive(player)) return;
        net.minecraft.world.item.ItemStack stack = event.getItem();
        if (stack.isEmpty()) return;
        net.minecraft.world.food.FoodProperties food = stack.getItem().getFoodProperties(stack, player);
        if (food == null) return;
        // 原版单次进食的饱和度增量 = 营养 × 饱食度修正 × 2
        float delta = food.nutrition() * food.saturation() * 2.0f;
        net.minecraft.world.food.FoodData foodData = player.getFoodData();
        float newSat = Math.min(foodData.getFoodLevel(), foodData.getSaturationLevel() + delta);
        foodData.setSaturation(newSat);
    }

    // 玩家退出/重新登录:清除骰神赐福效果(防止退出后重进仍保留战斗状态)
    @SubscribeEvent
    public static void onPlayerLoggedInClearDiceBlessing(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        ModAttachments.setDefenseCardConsumedThisBlessing(player, false);
        player.removeEffect(ModEffects.DICE_BLESSING);
        // 重连后刷新治愈体系(上限收缩/效果显示;赐福边沿 prev 标记初始 false,不会误触发减半)
        HealingManager.tick(player);
    }

    // 死亡重生:刷新治愈体系(上限收缩/效果显示)
    @SubscribeEvent
    public static void onPlayerRespawnMedkit(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        HealingManager.tick(player);
    }

    // 骰战最终伤害跳数字(红色)
    private static void sendDamageNumber(LivingEntity target, int bonusDamage) {
        sendDamageNumber(target, bonusDamage, 0xFF5555);
    }

    // 通用跳数字发送:指定 ARGB 颜色(0xRRGGBB 将被叠加透明度)
    private static void sendDamageNumber(LivingEntity target, int bonusDamage, int color) {
        if (target.level().isClientSide()) return;

        var packet = new DamageNumberPayload(target.getId(), bonusDamage, color);
        PacketDistributor.sendToPlayersTrackingEntity(target, packet);

        if (target instanceof ServerPlayer serverTarget) {
            PacketDistributor.sendToPlayer(serverTarget, packet);
        }
    }
}
