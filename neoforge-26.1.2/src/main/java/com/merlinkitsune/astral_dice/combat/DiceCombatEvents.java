package com.merlinkitsune.astral_dice.combat;


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
import com.merlinkitsune.astral_dice.item.sign.MosesSignItem;
import com.merlinkitsune.astral_dice.item.sign.PandamanSignItem;
import com.merlinkitsune.astral_dice.effect.WeaknessRevealEffect;
import com.merlinkitsune.starenginelib.item.BossEntityUtil;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.InvestigationEventUtil;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.item.sign.MisakiSignItem;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.event.CrimsonDiceHandler;
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
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
import com.merlinkitsune.astral_dice.item.chip.PerpetualMotionChipItem;
import com.merlinkitsune.astral_dice.item.chip.AdvancedPeripheralsChipItem;
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
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.astral_dice.item.RenShieldManager;
import com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.item.card.FateGuidanceCardItem;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.starenginelib.combat.HostileTargets;

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
    // AOE(顺劈/溅射)波及伤害处理中:被波及目标不再进入骰战结算
    static boolean aoeProcessing = false;
    // 反击链深度(替代原单层布尔 counterProcessing,结构性阻止"反击→闪避→反击"递归):
    //   >0 表示当前正处于「injectCounterDamage → attacker.hurt(...)」的同步调用链中。
    //   ① 用"深度"而不是布尔:嵌套注入时内层 finally 只把深度减回 1,而不会把守卫整体清零,
    //      外层剩余的注入过程始终受保护(原 boolean 会被内层 finally 提前复位 = 守卫失效);
    //   ② 增减包在 try/finally 内:异常/提前返回都会复位,守卫不会卡死;
    //   ③ 所有能绕回 injectCounterDamage 的入口(骰战结算 / 破绽闪避 / 嘲讽反击 / 肾上腺素闪避)
    //      都先判定 isInCounterChain(),injectCounterDamage 自身也拒绝再入 →
    //      反击链内不可能再发起一次反击,递归在结构上不成立(不是"限制递归层数")。
    private static int counterDepth = 0;

    // 把两条内部窗口的开关讲给库听(库的 PlayerHostilityTracker 需要它来区分"主动攻击"与"内部波及",
    // 但窗口状态属本类的玩法实现,不下沉)。
    static {
        com.merlinkitsune.starenginelib.combat.InternalDamageWindows.install(
                DiceCombatEvents::isInternalAoe,
                DiceCombatEvents::isInCounterChain);
        // 再把「试验假人应当算敌对目标」讲给库听:库的 combat/HostileTargets 是「敌对目标」的唯一入口,
        // 而它的判定点有一部分落在库内 —— target/SelectorTargets 的可选中判定(客户端射线/半径高亮/
        // 服务端确认)直接调它 ⇒ 消费方无法插手,故由库留 seam、本模组在此注入。
        // 未注入时库退回原口径(假人不算敌对),即本注入是**纯增益**、不影响其它实体。
        com.merlinkitsune.starenginelib.combat.HostileTargets.installExtraHostileProbe(
                DiceCombatEvents::isTrainingDummy);
    }

    // 当前是否处于反击链中(供骰战结算 / 闪避 / 反击入口判定)
    public static boolean isInCounterChain() {
        return counterDepth > 0;
    }

    // 当前是否处于本模组内部 AOE(顺劈/溅射/法伤波及)结算窗口。
    // 语义化只读入口:供受击记录等外部判定区分"主动攻击"与"内部波及"(禁止复制该标志)。
    public static boolean isInternalAoe() {
        return aoeProcessing;
    }


    // 检测玩家是否佩戴了七咒之戒(按物品 ID 识别,未安装该模组时返回 false)
    public static boolean hasEnigmaticCurse(Player player) {
        Item ring = BuiltInRegistries.ITEM.get(Identifier.parse(ENIGMATIC_CURSED_RING)).map(net.minecraft.core.Holder::value).orElse(null);
        if (ring == Items.AIR) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ring)).isPresent();
    }

    // 检测玩家是否手持指定神秘遗物+ 物品(如启示之证)
    public static boolean isHoldingEnigmaticItem(Player player, String itemId) {
        Item item = BuiltInRegistries.ITEM.get(Identifier.parse(itemId)).map(net.minecraft.core.Holder::value).orElse(null);
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
        // A3:先把本次伤害实例的受击侧倍率固化到局部变量(前置 HIGH 监听器已登记)——
        // 后续若发生**嵌套**伤害实例(溅射/AOE/反击注入等),登记槽会被那些实例刷新,
        // 此处先取值可保证骰战路径搬运的仍是"本实例"的倍率(仅同一受害者的登记才会被读取)。
        double victimFactor = DiceCombatModifiers.instanceVictimFactor(target);

        // 立牌受击钩子分发(史莱姆立牌等受击类被动由各立牌 onHurt 实现,不再在此硬编码)
        if (!target.level().isClientSide() && target instanceof Player targetPlayer) {
            BaseSignItem.invokeHurtHooks(targetPlayer, event.getNewDamage());
            // 缓冲盾牌筹码:受到攻击时 +2 治愈 +3 星币(每 15 秒一次)
            com.merlinkitsune.astral_dice.item.chip.BufferShieldChipItem.onHurt(targetPlayer, event.getNewDamage());
        }

        // AOE(顺劈/溅射)波及的目标不进入骰战结算,避免二次吃到完整骰战;
        // 反击链中的伤害不进入骰战结算(已按反击公式自算),同时结构性阻止反击递归
        if (aoeProcessing || counterDepth > 0) return;
        if (!(directEntity instanceof Player player)) return;
        if (target == player) return;

        // 电磁炮筹码:对敌对目标发起攻击时消耗 6 层充能,延迟 1 秒对目标 3 格内敌对目标降下雷击。
        // 雷击伤害 = 本次攻击伤害的 50%:此处骰战尚未结算,先按即时伤害兜底登记,
        // 骰战最终伤害确定后(下方 setNewDamage 之后)回填。
        var railgunStrike = com.merlinkitsune.astral_dice.item.chip.RailgunChipItem.onAttack(
                player, target, event.getNewDamage());

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

        // 占星师/秘密侦探立牌主动已迁移至目标选择器(TargetSelectionManager + HaiqingSignItem/BonnieSignItem 的
        // TargetSelectionAction.apply),不再于攻击时自动释放,此处无攻击释放逻辑。
        // 枪匠立牌主动同样已迁移至目标选择器(见 MosesSignItem 注册的 TargetSelectionAction),此处仅保留其被动:
        if (!player.level().isClientSide() && attackerCurios.isPresent() && isBlessingTarget(target, player)) {
            // 枪匠立牌被动:攻击已带"破绽"的目标,每段破绽获得 1 层弱点识破
            // 触发条件与骰神赐福完全一致:近战武器(外层已判定)+ isBlessingTarget(外层已判定),
            // 因此不再额外限制"普通敌对生物"。
            if (MosesSignItem.isEquipped(player) && target.hasEffect(ModEffects.MOSES_BROKEN)) {
                MosesSignItem.onAttackBrokenTarget(player, target);
            }
        }

        // 本次攻击是否触发了骰神赐福(与赐福触发逻辑一致:仅在未拥有赐福时触发;同一挥击命中多目标也仅触发一次)
        boolean triggeredBlessing = false;
        // 大当家立牌被动"战斗爽·溅射":满层赐福触发时置位,本次攻击伤害定稿后立即引爆一次
        boolean fenSplashArmed = false;
        if (!player.level().isClientSide() && diceStack != null && !player.hasEffect(ModEffects.DICE_BLESSING)
                && isBlessingTarget(target, player)) {
            // 六参构造:visible=false 禁用粒子,showIcon=true 让赐福图标(含剩余时间)在 HUD 效果栏正常显示。
            // ⚠️ 五参构造 (…, ambient, visible) 内部等价于 showIcon=visible ⇒ 传 false 会把 HUD 图标一并隐藏
            // (物品栏效果面板不读 showIcon,故只在物品栏可见)——与本模组其它状态效果(充能/赋能/弱点识破/治愈)
            // 一律用 (…, false, false, true) 的口径保持一致,不得改回五参。
            player.addEffect(new MobEffectInstance(ModEffects.DICE_BLESSING,
                    GameplayConstants.DICE_BLESSING_DURATION_TICKS, 0, false, false, true));
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
                        // 同主分支:六参构造保持 HUD 图标可见(粒子仍由 visible=false 关闭)
                        targetPlayer.addEffect(new MobEffectInstance(ModEffects.DICE_BLESSING,
                                GameplayConstants.DICE_BLESSING_DURATION_TICKS, 0, false, false, true));
                        ModAttachments.setDefenseCardConsumedThisBlessing(targetPlayer, false);
                        ModAttachments.setCursedSwordBlessingTriggered(targetPlayer, false);
                    }
                }
            }
            // 标靶筹码:触发骰神赐福后,对距离最近的敌对目标施加一层标记(无固定范围常量)
            if (attackerCurios.isPresent()) {
                var targetChipResult = attackerCurios.get().findFirstCurio(s -> s.is(ModItems.TARGET_CHIP.get()));
                if (targetChipResult.isPresent()) {
                    net.minecraft.world.entity.LivingEntity nearest = null;
                    double nearestDistSqr = Double.MAX_VALUE;
                    net.minecraft.server.level.ServerLevel serverLevel =
                            (net.minecraft.server.level.ServerLevel) player.level();
                    for (net.minecraft.world.entity.Entity entity : serverLevel.getEntities().getAll()) {
                        if (entity instanceof net.minecraft.world.entity.LivingEntity living
                                // 上下文重载:攻击者"视谁为敌"(全局规则,含曾主动攻击过攻击者的非同队玩家)
                                && HostileTargets.isHostile(player, living) && living.isAlive()) {
                            double distSqr = living.distanceToSqr(player);
                            if (distSqr < nearestDistSqr) {
                                nearestDistSqr = distSqr;
                                nearest = living;
                            }
                        }
                    }
                    if (nearest != null) {
                        MarkManager.apply(nearest, 1200);
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
            // 永动机筹码:触发骰神赐福时,充能 +6
            PerpetualMotionChipItem.onBlessingStart(player);
            // 高级外设筹码:触发骰神赐福时,移除 1 层充能
            AdvancedPeripheralsChipItem.onBlessingStart(player);
            // 会员推荐信筹码:触发骰神赐福时,获得一张随机卡牌
            com.merlinkitsune.astral_dice.item.chip.MemberRecommendationChipItem.onBlessingStart(player);
            // 大当家立牌:触发骰神赐福 → 记录触发时刻;养精蓄锐满层则消耗 2 层并置位本次攻击的溅射
            fenSplashArmed = com.merlinkitsune.astral_dice.item.sign.FenSignItem.onBlessingTriggered(player);
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

        int baseDice = rollCombatDie(player); // 特殊骰子掷骰(诡异骰子低点数偏置/绯红骰子高点数偏置)

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
                int accum = ModAttachments.getEightSidedAccum(player) + roll;
                while (accum >= 8 && starlight < StarLightManager.getCap()) {
                    accum -= 8;
                    starlight++;
                }
                // 星光已满时**保留**累计点数(不清零),待星光回落后继续换算
                ModAttachments.setEightSidedAccum(player, accum);
                StarLightManager.set(player, Math.min(starlight, StarLightManager.getCap()));
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
                    // 特殊骰子掷骰(防御方:诡异骰子低点数偏置,绯红骰子高点数偏置)
                    defenseBaseDice = rollCombatDie(targetPlayer);
                    // 防御卡掷骰由注册表防御修饰器执行(读 ctx.targetEnhancement,写 ctx.defenseCardSum)
                    ItemStack targetDice = targetDiceResult.get().stack();
                    WeaponEnhancement targetEnh = targetDice.get(ModDataComponents.WEAPON_ENHANCEMENT.get());
                    ctx.targetEnhancement = targetEnh;
                    // 玻璃骰子:防御方佩戴时,防御牌点数始终取最大值
                    ctx.targetCardsMax = targetDice.is(ModItems.GLASS_DICE.get());
                }
            }
        } else if (!target.level().isClientSide() && !(target instanceof Player)) {
            // 怪物:始终防御,每次受击掷 1d6 防御骰(不再闪避)
            // 枪匠"破绽":目标骰点只能为 0
            if (target.hasEffect(ModEffects.MOSES_BROKEN)) {
                defenseBaseDice = 0;
            } else {
                defenseBaseDice = ThreadLocalRandom.current().nextInt(1, 7);
            }
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

            // 上班族立牌:攻击骰为 6 时无视目标防御力——按本模组「目标防御力」口径
            // (与贯穿之铳同一公式:2 + 护甲÷2 + 1.4×韧性;护甲已包含由防御力折算而来的部分)
            // 整项不计入防御,但保留目标的防御骰与防御牌加成。
            if (ctx.padmanDefBypass && !skipDefense) {
                defensePower = defenseBaseDice + ctx.defenseCardSum;
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

        // A3:受击侧伤害修饰器(末影骰子雨中/水下 +40% 等)**只搬运、不二次消费** ——
        // 骰战以 setNewDamage 覆盖式写入自算的最终伤害,前置 HIGH 监听器(修饰器唯一应用点)乘出的那份值
        // 会被整段替换(见 docs/interaction-audit-1.2.1.md 的 A3);若此处再消费一次修饰器,
        // 同一次伤害就会经过两套独立计算(把"丢弃"变成"重复"×1.96)。
        // instanceVictimFactor(target) 取回**同一伤害实例**由前置监听器登记的倍率(仅同一受害者有效),
        // 故整条链上修饰器只求值一次、倍率只落在最终落地的这个值上(恰好 ×1.4 一次)。
        // 用处理器开头固化的局部变量:避免被后续嵌套伤害实例刷新的登记槽影响。
        finalDmg *= victimFactor;

        event.setNewDamage((float) finalDmg);
        sendDamageNumber(event.getEntity(), (int) finalDmg);
        // 电磁炮:以本次骰战最终伤害回填雷击伤害(50%)
        com.merlinkitsune.astral_dice.item.chip.RailgunChipItem.applyFinalDamage(railgunStrike, (float) finalDmg);

        // 玩家对玩家:被攻击方若佩戴骰子且处于骰神赐福,则每个赐福期间消耗一次防御牌耐久
        if (!player.level().isClientSide() && target instanceof Player targetDefender
                && targetDefender.hasEffect(ModEffects.DICE_BLESSING)) {
            consumeDefenseCardDurabilityOnce(targetDefender);
        }

        // 大当家立牌被动(战斗爽·溅射):本次攻击触发骰神赐福且养精蓄锐满层时,触发块已置位;
        // 这里在本次攻击伤害定稿后**立即引爆一次**(单次效果:不再等待下一次赐福,也没有持续期),
        // 并在此刻才扣除养精蓄锐代价(攻击被取消时不会白扣)。
        // 伤害 = 本次攻击伤害的 88%(**下限 5 点**),范围为**目标及其 6 格范围内**(含主目标)的敌对目标,
        // 伤害类型为**真伤**(astral_dice:true_damage,登记于 minecraft:bypasses_armor →
        // 无视护甲值与盔甲韧性;保护附魔与抗性提升不在此口径内,仍会减免);
        // 只打敌对目标(**无友伤**)、不破坏方块,命中仍附带爆炸粒子与音效(视觉表现与伤害类型无关)。
        // 递归保护:溅射伤害不进入骰战结算(aoeProcessing 统一闸门),避免二次触发赐福/互相引爆。
        if (!player.level().isClientSide() && fenSplashArmed) {
            com.merlinkitsune.astral_dice.item.sign.FenSignItem.consumeSplashCost(player);
            aoeProcessing = true;
            try {
                // 下限取立牌常量(5 点),高于全局"按比例不足 1 时按 1 计"的兜底
                float splashDmg = (float) Math.max(
                        com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_DAMAGE_MIN,
                        finalDmg * com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_RATIO);
                net.minecraft.world.phys.AABB splashBox = target.getBoundingBox()
                        .inflate(com.merlinkitsune.astral_dice.item.sign.FenSignItem.SPLASH_RANGE);
                var splashVictims = target.level().getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class, splashBox,
                        // 上下文重载:施放者"视谁为敌" —— 溅射现在也会命中「非同队伍且曾主动攻击过施放者的玩家」
                        e -> HostileTargets.isHostile(player, e) && e.isAlive());
                if (!splashVictims.isEmpty()) {
                    // 真伤伤害源:直接伤害实体为空、击杀归属玩家(与旧 explosion(null, player) 同形状,
                    // 不会被本模组或其它模组再当成一次"玩家的直接攻击"重走命中判定,同时保留击杀归属);
                    // 类型 astral_dice:true_damage 登记于 minecraft:bypasses_armor → 无视护甲值与盔甲韧性。
                    var splashSource = com.merlinkitsune.astral_dice.damage.ModDamageTypes.trueDamage(
                            target.level(), player);
                    for (var victim : splashVictims) {
                        // 主目标此刻正处在**自己这次攻击的伤害事件内部**:原版 LivingEntity#hurt 先写
                        // lastHurt/invulnerableTime(用的是**骰战结算前**的武器伤害),再进 actuallyHurt →
                        // 本事件;于是"无敌帧内不更低的伤害被丢弃"规则会把溅射整段吞掉(实测主靶只掉近战
                        // 那 3 点、5 点溅射凭空消失)。这里只对主目标临时清零无敌帧,让溅射照常结算;
                        // 其余目标没有在飞的伤害,不动。
                        boolean isMainTarget = victim == target;
                        int savedInvulnerable = isMainTarget ? victim.invulnerableTime : 0;
                        try {
                            if (isMainTarget) victim.invulnerableTime = 0;
                            victim.hurt(splashSource, splashDmg);
                            sendDamageNumber(victim, (int) splashDmg);
                        } finally {
                            if (isMainTarget) victim.invulnerableTime = savedInvulnerable;
                        }
                    }
                    // 爆炸视觉效果:只发粒子与音效,不改动世界(不破坏方块)
                    if (target.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        double ex = target.getX();
                        double ey = target.getY(0.5);
                        double ez = target.getZ();
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                                ex, ey, ez, 1, 0.0, 0.0, 0.0, 0.0);
                        serverLevel.playSound(null, ex, ey, ez, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                                net.minecraft.sounds.SoundSource.BLOCKS, 4.0F,
                                (1.0F + (serverLevel.getRandom().nextFloat() - serverLevel.getRandom().nextFloat()) * 0.2F) * 0.7F);
                    }
                }
            } finally {
                aoeProcessing = false;
            }
        }

        // 吸血鬼立牌(papara)主动"汲取":攻击时恢复骰神赐福最终伤害的一半生命(取整,至少 1 点)
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

        // 手电筒筹码:攻击敌对目标时 +1 星光(同一目标仅 +1 层,去重记录见 FlashlightChipItem)
        com.merlinkitsune.astral_dice.item.chip.FlashlightChipItem.onAttack(player, target);
    }


    // 攻击牌耐久消耗(仅在触发骰神赐福的那次攻击执行一次;防御牌/蓄力不消耗)。
    // 普通近战触发与反击伤害注入共用(反击未赐福时作为触发攻击消耗一次耐久)。
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
        // 肉弹战车立牌「嘲讽」:带嘲讽的目标只能攻击施加嘲讽的玩家
        var tauntSource = ModAttachments.getPandamanTauntSource(event.getEntity());
        if (event.getEntity().hasEffect(ModEffects.PANDAMAN_TAUNT) && tauntSource.isPresent()) {
            Player taunter = event.getEntity().level().getPlayerByUUID(tauntSource.get());
            if (taunter != null && taunter.isAlive()) {
                event.setNewAboutToBeSetTarget(taunter);
                return;
            }
        }
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
        // 大碗炖肉筹码:赐福结束后,16 格范围内所有友方目标 +1 治愈并恢复 2 点生命值
        com.merlinkitsune.astral_dice.item.chip.BigBowlStewChipItem.onBlessingEnd(player);
        // 骇客立牌:赐福结束刷新被动(攻击/防御,覆盖旧类型)
        NancyLuSignItem.onDiceBlessingEnded(player);
        // 枪匠立牌:赐福结束弱点识破减少 1 层
        MosesSignItem.onDiceBlessingEnded(player);

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

    // 伤害放大须先于 ChipDamageHandler(安全气囊,LOWEST)执行,故用 LOW
    @SubscribeEvent(priority = EventPriority.LOW)
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


    // 伤害放大须先于 ChipDamageHandler(安全气囊,LOWEST)执行,故用 LOW
    @SubscribeEvent(priority = EventPriority.LOW)
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

    /**
     * 玩家战斗骰(d1-6)统一掷骰入口(骰子槽位仅一个):
     * 佩戴诡异骰子 → 低点数(1-3)偏置;佩戴绯红骰子 → 高点数(4-6)偏置(掷出 1 时自伤 6 点);
     * 未佩戴特殊骰子 → 均匀分布。
     */
    private static int rollCombatDie(Player roller) {
        int roll;
        if (roller == null) {
            roll = ThreadLocalRandom.current().nextInt(1, 7);
        } else if (WeirdDiceHandler.hasWeirdDice(roller)) {
            roll = WeirdDiceHandler.rollD6(roller);
        } else if (CrimsonDiceHandler.hasCrimsonDice(roller)) {
            roll = CrimsonDiceHandler.rollD6(roller);
        } else {
            roll = ThreadLocalRandom.current().nextInt(1, 7);
        }
        // 枪匠立牌:弱点识破每层使骰点最低数 +1
        if (roller != null && MosesSignItem.isEquipped(roller)) {
            roll = Math.max(roll, 1 + WeaknessRevealEffect.getStacks(roller));
        }
        return roll;
    }

    // 近战武器攻击判定:仅允许剑/斧/重锤/三叉戟/长矛等近战武器触发骰神赐福
    public static boolean isMeleeWeaponAttack(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return false;
        if (held.is(Items.SHIELD)) return false;
        return held.is(net.minecraft.tags.ItemTags.SWORDS)
                // 26.1.2 新增「长矛」(木/石/铜/铁/金/钻石/下界合金 共 7 种):与剑/斧同属近战武器,纳入骰神赐福判定。
                // 按 vanilla 物品标签 minecraft:spears 判定(而非逐个 Item / 按类判断):
                // 26.1.2 的长矛**没有独立物品类**,是 `Item.Properties#spear(...)` 参数化的普通 Item,
                // 用标签可自动覆盖后续版本新增的长矛与其它模组的长矛。
                || held.is(net.minecraft.tags.ItemTags.SPEARS)
                || held.getItem() instanceof AxeItem
                || held.getItem() instanceof MaceItem
                || held.getItem() instanceof TridentItem;
    }

    // 骰神赐福触发目标判定:敌对生物、非团队内玩家、中立生物(宠物除外),以及其余非被动动物实体
    // ⚠️ 试验假人不再在此单列特例 —— 它已由库的 HostileTargets「额外敌对判定」seam 统一计入敌对目标
    // (注入见本类 static 块),故下一行 HostileTargets.isHostile 即覆盖它,与其它调用点口径一致。
    public static boolean isBlessingTarget(LivingEntity target, Player player) {
        if (target instanceof Player other) {
            return other.getTeam() == null || other.getTeam() != player.getTeam();
        }
        if (HostileTargets.isHostile(target)) return true;
        if (target instanceof Mob mob) {
            // Boss 允许触发;其余生物仅在被激怒/正在攻击玩家时允许
            if (com.merlinkitsune.starenginelib.item.BossEntityUtil.isBossEntity(target)) return true;
            return mob.getTarget() == player || mob.isAggressive();
        }
        // 被动/友好生物不允许触发骰神赐福(中立生物已在上面由 HostileTargets 计入)
        return false;
    }

    // 试验假人(dummmmmmy)识别:实体注册 id 命名空间为 dummmmmmy,或类名包含 dummy(兼容不同版本/命名)。
    // 由本类 static 块注入给库的 combat/HostileTargets(ExtraHostileProbe)⇒ 该假人在**所有**
    // 「需要敌对目标」的判定里都算敌对(骰神赐福、法伤修饰符链、导弹/轨道类技能的波及选目标,以及
    // 目标选择器的可选中判定),不再是「骰神赐福」一处特例。参数取 Entity 是为满足 seam 签名
    // (库的判定入参是 Entity,不是 LivingEntity);null 返回 false。
    public static boolean isTrainingDummy(Entity target) {
        if (target == null) return false;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
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

    // === 闪避统一取消入口(必须在伤害判定最前置处"取消") ===
    // 为什么必须"取消"而不是"把伤害改成 0":
    //  - 派发点:NeoForge 的 LivingIncomingDamageEvent 由 CommonHooks.onEntityIncomingDamage
    //    在 LivingEntity.hurt 压入 DamageContainer 之后立即派发
    //    (neoforge 21.1.235 LivingEntity.java:1152-1153;位于 isInvulnerableTo / isDeadOrDying
    //    之后,无敌帧与全部伤害处理之前),setCanceled(true) 后 hurt 直接 return false。
    //  - 只有在这一层取消,攻击方 Mob#doHurtTarget 才拿到 false(1.21.1 Mob.java:1498-1519),
    //    从而不再执行"命中后附加效果"。原版尸壳 Husk#doHurtTarget 为:
    //      boolean flag = super.doHurtTarget(entity);
    //      if (flag && this.getMainHandItem().isEmpty() && entity instanceof LivingEntity)
    //          ((LivingEntity)entity).addEffect(new MobEffectInstance(MobEffects.HUNGER, 140 * (int)f), this);
    //    (1.21.1 Husk.java:56-64)flag 为 false 时饥饿不会被施加。
    //  - 同时不产生受伤反馈:hurt 在赋值 invulnerableTime / hurtDuration / hurtTime、
    //    broadcastDamageEvent、markHurt、knockback、indicateDamage(ClientboundHurtAnimationPacket)、
    //    playHurtSound 之前就已经返回。
    // 旧实现只在伤害阶段 setNewDamage(0):此时 hurt 早已走完全部流程并返回 true,
    // 所以命中附加效果与红屏/晃动/受伤音效照旧发生。
    public static void applyDodgeCancel(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        // 先取消:立牌 onHurt / 缓冲盾牌等受击联动即使抛异常,也不得让"闪避"退化成命中
        event.setCanceled(true);
        // 保留旧实现(伤害阶段)下的受击联动:旧代码只把伤害改成 0,hurt 仍走完全流程,
        // LivingDamageEvent.Pre 照常派发,因此 BaseSignItem.invokeHurtHooks 与
        // BufferShieldChipItem.onHurt 在"被闪避的那一击"上依然会触发。上移到最前置处后,
        // 取消会跳过整段伤害处理,故在此显式补发一次(取消后伤害阶段不再派发 → 不会重复触发)。
        // 注:此处传入的是减伤前原始值;两个钩子实现都不读取该数值(仅用于"是否受击"判定)。
        if (!target.level().isClientSide() && target instanceof Player player) {
            BaseSignItem.invokeHurtHooks(player, event.getAmount());
            com.merlinkitsune.astral_dice.item.chip.BufferShieldChipItem.onHurt(player, event.getAmount());
        }
    }

    // === 枪匠立牌(Moses)破绽闪避/反击 ===
    // 破绽持续 2:00,期间**每一次**目标攻击都会被闪避并触发反击(不再被"每目标已发放"标记拦掉);
    // 「弱点识破」层数的"每目标每段破绽只 +1"限制由 MosesSignItem.onDodgeCounter 内部判定。
    // 闪避改在伤害判定最前置处"取消"(LivingIncomingDamageEvent)而不是在伤害阶段把伤害改成 0:
    // 只有前者能让攻击方 Mob#doHurtTarget 拿到 hurt()==false,从而不施加尸壳饥饿等命中附加效果、
    // 也不产生红屏/屏幕震动/受伤音效与击退同步。详见 applyDodgeCancel 的注释。
    @SubscribeEvent
    public static void onMosesBrokenDodge(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(victim instanceof Player player)) return;
        if (!MosesSignItem.isEquipped(player)) return;
        // 反击链中的伤害不参与破绽闪避/反击判定(结构性递归截断):
        // 否则 A/B 双方各自都带破绽时会 A 闪避 B → 反击注入 B → B 闪避 → 反击注入 A → ... 无限互相递归
        if (isInCounterChain()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (!attacker.hasEffect(ModEffects.MOSES_BROKEN)) return;
        // 闪避本次攻击(最前置取消)
        applyDodgeCancel(event);
        // 获得弱点识破并标记该目标已闪避(每目标每段破绽最多 1 层)
        MosesSignItem.onDodgeCounter(player, attacker);
        // 单次反击伤害注入(不进入反击效果/层数体系)
        injectCounterDamage(player, attacker);
    }

    // 肉弹战车立牌(pandaman)主动「嘲讽」:被嘲讽目标攻击施加者时触发反击
    // (沿用反击伤害公式;不消耗“反击”层数,每次嘲讽目标成功攻击时触发)
    @SubscribeEvent
    public static void onPandamanTauntCounter(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        // 反击链中不再触发嘲讽反击(与破绽闪避共用同一结构性递归截断)
        if (isInCounterChain()) return;
        if (!(victim instanceof Player player)) return;
        if (!player.isAlive()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        // 视者 = 被攻击的玩家(player):被嘲讽目标若为"曾主动攻击过本玩家的非同队玩家"同样计入敌对
        if (!HostileTargets.isHostile(player, attacker)) return;
        if (!attacker.hasEffect(ModEffects.PANDAMAN_TAUNT)) return;
        Optional<UUID> tauntSource = ModAttachments.getPandamanTauntSource(attacker);
        if (tauntSource.isEmpty() || !tauntSource.get().equals(player.getUUID())) return;
        injectCounterDamage(player, attacker);
    }

    // 游戏大师立牌(ren)「鼠鼠护盾」自带的一次性反击:带盾玩家被攻击时消耗 1 层,对攻击者注入一次
    // 现有反击伤害(沿用同一公式)。事件与肉弹嘲讽同源(LivingDamageEvent.Pre,位于吸收结算之前),
    // 该钩子只表示「伤害已确认」,与吸收数值无关 ⇒ **被黄心完全吃掉的一击同样触发**;
    // 若这一击正好打空黄心,护盾的清空由 RenShieldManager 的每 tick 轮询在稍后完成(先反击、后破盾)。
    @SubscribeEvent
    public static void onRenShieldCounter(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        // 反击链中不再触发(与破绽闪避 / 嘲讽反击共用同一结构性递归截断)
        if (isInCounterChain()) return;
        if (!(victim instanceof Player player)) return;
        if (!player.isAlive()) return;
        if (!player.hasEffect(ModEffects.REN_SHIELD)) return;
        if (ModAttachments.getRenCounterCharges(player) <= 0) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == player) return;
        // 一次性充能:先消耗层数(并同步摘掉「反击」图标),再注入伤害
        ModAttachments.setRenCounterCharges(player, 0);
        RenShieldManager.refreshCounterEffect(player);
        injectCounterDamage(player, attacker);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new ActionBarPayload(
                    Component.translatable("msg.astral_dice.ren_counter_fired")
                            .withStyle(ChatFormatting.YELLOW),
                    GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    // === 反击伤害注入(Counterattack Damage Injection) ===
    // 反击不再作为效果/流派存在:没有 counterattack 效果、没有层数、没有持续反噬周期。
    // 具体触发源(肉弹战车嘲讽、枪匠破绽闪避)命中时调用 injectCounterDamage 做单次伤害计算并注入,
    // 不登记反噬目标、不持续返还。

    // 对当前目标注入一次反击伤害(视为玩家伤害来源,不进入骰战结算/不递归触发)
    // 结构性递归截断:本方法自身拒绝在反击链中再入——即使将来新增调用点忘记加守卫,
    // 反击链也不可能自我递归(而不是"限制递归层数"这类可被绕过的软限制)。
    private static void injectCounterDamage(Player player, LivingEntity attacker) {
        if (isInCounterChain()) return;
        // 整个「反击伤害计算 + 注入」都必须落在反击链内:计算过程本身也会造成伤害——
        // computeCounterDamage → rollCombatDie → CrimsonDiceHandler.rollD6 掷出 1 时会对掷骰者
        // 本人施加 6 点 astral_dice:dice_damage(CrimsonDiceHandler.java:63-65)。若把计算留在
        // 守卫之外,这份自伤仍可能再次进入破绽闪避/反击判定,构成第二条(概率性、无上界的)递归路径。
        counterDepth++;
        try {
            double dmg = computeCounterDamage(player, attacker);
            if (dmg <= 0) return;
            attacker.hurt(com.merlinkitsune.astral_dice.damage.ModDamageTypes.diceDamage(attacker.level(), player),
                    (float) dmg);
            sendDamageNumber(attacker, (int) dmg);
        } finally {
            // try/finally:异常/提前返回都会复位;计数器只减不置零 → 嵌套同样安全
            counterDepth--;
        }
    }

    // 反击返还伤害 = 手持最高近战武器基础伤害 + 骰战攻击力加成链 + 已装备攻击牌随机掷骰
    // (与正常攻击同一上下文以收集加成/掷骰,但不加入 1d6,也不自动赐福/施加诅咒/×1.5)
    private static double computeCounterDamage(Player player, LivingEntity attacker) {
        double weaponBase = highestHeldMeleeBaseDamage(player);

        // 骰子与卡牌(与正常攻击路径一致的上下文,仅用于攻击力加成链与攻击牌随机掷骰)
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

        int baseDice = rollCombatDie(player); // 特殊骰子掷骰(诡异骰子低点数偏置/绯红骰子高点数偏置)
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
        double total = weaponBase + ctx.attackCardSum + modifiersSum;
        // 七咒减益作用于反击总伤害(含修正物:启示之证/倒转之启/恩惠之典/护法爆发)
        total = applyCurseToDicePoints(player, total);
        // 可受到修正影响:装备「全力攻击」时返还伤害 ×1.5(与正常攻击结算一致)
        boolean hasFullPower = ctx.hasFullPower;
        if (!hasFullPower && enhancement != null) {
            for (AppliedStone stone : enhancement.appliedStones()) {
                if ("full_power".equals(stone.type())) {
                    hasFullPower = true;
                    break;
                }
            }
        }
        if (hasFullPower) {
            total = Math.ceil(total * 1.5);
        }
        // 肉弹战车立牌(pandaman)常驻被动:反击时若生命未满,附加缺失生命值等值的伤害
        if (PandamanSignItem.isEquipped(player)) {
            double missingHp = Math.max(0.0, player.getMaxHealth() - player.getHealth());
            if (missingHp > 0) {
                total += missingHp;
            }
        }
        return total;
    }

    // 手持(主手+副手)近战武器的基础伤害最大值(不含附魔/属性效果);无近战武器回退空手 1.0
    private static double highestHeldMeleeBaseDamage(Player player) {
        double best = 1.0;
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.isEmpty()) continue;
            for (var entry : stack.getItem().getDefaultAttributeModifiers(stack).modifiers()) {
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
