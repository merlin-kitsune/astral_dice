package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.chip.BoxingGlovesChipItem;
import com.merlinkitsune.astral_dice.item.chip.RevengeHalberdChipItem;
import com.merlinkitsune.astral_dice.item.sign.FenSignItem;
import com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem;
import com.merlinkitsune.astral_dice.item.sign.JasmineSignItem;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.PadmanSignItem;
import com.merlinkitsune.astral_dice.item.BossEntityUtil;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.astral_dice.event.ModEventHandlers;

/**
 * 骰神赐福攻防修饰器注册表:管理攻击力/防御力修饰器的有序注册与内置修饰器。
 *
 * 内置修饰器覆盖:
 * - 效果类加成(王之力/狂暴/力量 / 岿然不动/抗性);
 * - 卡牌掷骰(攻击/防御);
 * - 全部立牌与筹码的战斗加成(护法 misaki/扫地机 jasmine/吸血鬼 papara/秘密侦探 bonnie/
 *   上班族 padman/调查阶段,以及美工刀/瞄具/标靶/手电筒等筹码)。
 *
 * 附属内容(新立牌/筹码/效果/联动)实现 {@link AttackPowerModifier} / {@link DefensePowerModifier}
 * 并通过 register 注册即可影响攻防,无需修改 ModEventHandlers 主流程。
 */
public final class DiceCombatModifiers {

    private static final List<AttackPowerModifier> ATTACK_MODIFIERS = new ArrayList<>();
    private static final List<DefensePowerModifier> DEFENSE_MODIFIERS = new ArrayList<>();

    private DiceCombatModifiers() {
    }

    // 注册攻击力修饰器(按注册顺序执行)
    public static void registerAttackModifier(AttackPowerModifier modifier) {
        ATTACK_MODIFIERS.add(modifier);
    }

    // 注册防御力修饰器(按注册顺序执行)
    public static void registerDefenseModifier(DefensePowerModifier modifier) {
        DEFENSE_MODIFIERS.add(modifier);
    }

    public static List<AttackPowerModifier> attackModifiers() {
        return List.copyOf(ATTACK_MODIFIERS);
    }

    public static List<DefensePowerModifier> defenseModifiers() {
        return List.copyOf(DEFENSE_MODIFIERS);
    }

    // 通用掷骰:1~max(含)(public:供 CardRegistry 等外部使用)
    public static int rollDice(int max) {
        return ThreadLocalRandom.current().nextInt(1, max + 1);
    }

    // 带下限掷骰:min~max(含)(public:供 CardRegistry 等外部使用)
    public static int rollDice(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    // 玩家是否佩戴指定 Curios 物品
    private static boolean hasCurio(Player player, net.minecraft.world.item.Item item) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(item)).isPresent();
    }

    static {
        // === 内置:效果类攻击加成(王之力/狂暴/力量) ===
        registerAttackModifier((ctx, ap) -> {
            var kingPower = ctx.attacker.getEffect(ModEffects.KING_POWER);
            if (kingPower != null) {
                ap += 5 * (kingPower.getAmplifier() + 1);
            }
            var berserk = ctx.attacker.getEffect(ModEffects.BERSERK);
            if (berserk != null) {
                ap += 3 * (berserk.getAmplifier() + 1);
            }
            return ap;
        });
        registerAttackModifier((ctx, ap) -> {
            var strength = ctx.attacker.getEffect(MobEffects.DAMAGE_BOOST);
            if (strength != null) {
                ap += (strength.getAmplifier() + 1) * 2;
            }
            return ap;
        });

        // === 内置:攻击卡掷骰(仅收集结果写入上下文,不直接修改攻击力;
        //       骰点+卡牌总和作为 diceAttackBonus 统一经七咒减益后在主方法加入) ===
        registerAttackModifier((ctx, ap) -> {
            int sum = 0;
            for (AppliedStone stone : ctx.enhancement.appliedStones()) {
                // 掷骰逻辑统一由 CardRegistry 提供(含 shadow_strike/charge/full_power/meito 等特殊卡)
                sum += CardRegistry.roll(stone.type(), ctx);
            }
            ctx.attackCardSum = sum;
            return ap;
        });

        // === 内置:护法立牌(misaki)被动与爆发 ===
        registerAttackModifier((ctx, ap) -> {
            // 被动:层数基础伤害加成(1层+1,2层+2,3层+5)
            if (ctx.misakiStacks > 0) {
                ap += switch (ctx.misakiStacks) {
                    case 1 -> 1;
                    case 2 -> 2;
                    case 3 -> 5;
                    default -> 0;
                };
            }
            // 爆发期间伤害 +4
            if (ctx.misakiBurst) {
                    ap += 4;
            }
            return ap;
        });

        // === 内置:美工刀/美工刀-锋利(满血时按当前治愈点数增伤) ===
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            boolean fullHp = ctx.attacker.getHealth() >= ctx.attacker.getMaxHealth() * 0.6f || ctx.attacker.hasEffect(ModEffects.PAPARA_BITE);
            if (fullHp) {
                int healing = HealingManager.getPoints(ctx.attacker);
                if (hasCurio(ctx.attacker, ModItems.CUTTER_CHIP.get())) {
                    ap += 2 + healing;
                }
                if (hasCurio(ctx.attacker, ModItems.CUTTER_BLADE_CHIP.get())) {
                    ap += 4 + healing;
                }
            }
            return ap;
        });

        // === 内置:普通瞄具/鹰眼瞄具(攻击力+2/+标记层数*2,并施加 1 层标记) ===
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            if (hasCurio(ctx.attacker, ModItems.SCOPE_CHIP.get())) {
                ap += 2;
                if (ctx.event != null) MarkManager.apply(ctx.target);
            }
            if (hasCurio(ctx.attacker, ModItems.EAGLE_SCOPE_CHIP.get())) {
                int markLevel = MarkManager.getLevel(ctx.target);
                ap += markLevel * 2;
                if (ctx.event != null) MarkManager.apply(ctx.target);
            }
            return ap;
        });

        // === 内置:标靶(攻击力+1) / 手电筒-强光(每 4 星光 +1) ===
        registerAttackModifier((ctx, ap) -> {
            if (hasCurio(ctx.attacker, ModItems.TARGET_CHIP.get())) {
                ap += 1;
            }
            if (hasCurio(ctx.attacker, ModItems.FLASHLIGHT_CHIP.get())) {
                ap += StarLightManager.get(ctx.attacker) / 4;
            }
            return ap;
        });

        // === 内置:扫地机立牌(jasmine)攻击力增益 ===
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            var curios = CuriosApi.getCuriosInventory(ctx.attacker);
            if (curios.isPresent()) {
                var r = curios.get().findFirstCurio(s -> s.is(ModItems.JASMINE_SIGN.get()));
                if (r.isPresent()) {
                    ap += JasmineSignItem.getAttackBonus(r.get().stack());
                }
            }
            return ap;
        });

        // === 内置:吸血鬼立牌(papara)被动(半血或"嘬一口"期间攻击力+3) ===
        registerAttackModifier((ctx, ap) -> {
            Player p = ctx.attacker;
            if (p.level().isClientSide()) return ap;
            boolean active = p.getHealth() <= p.getMaxHealth() / 2.0f || p.hasEffect(ModEffects.PAPARA_BITE);
            if (active && hasCurio(p, ModItems.PAPARA_SIGN.get())) {
                ap += 3;
            }
            return ap;
        });

        // === 内置:拳击手套(初级/中级/高级:骰神赐福攻击力 +1/+3/+5) ===
        registerAttackModifier((ctx, ap) -> {
            if (hasCurio(ctx.attacker, ModItems.BOXING_GLOVES_LOW.get())) {
                ap += BoxingGlovesChipItem.BONUS_LOW;
            }
            if (hasCurio(ctx.attacker, ModItems.BOXING_GLOVES_MEDIUM.get())) {
                ap += BoxingGlovesChipItem.BONUS_MEDIUM;
            }
            if (hasCurio(ctx.attacker, ModItems.BOXING_GLOVES_HIGH.get())) {
                ap += BoxingGlovesChipItem.BONUS_HIGH;
            }
            return ap;
        });

        // === 内置:星币锤(进入骰神赐福消耗星币,按持有总数 30% 提升攻击力,赐福结束清除) ===
        registerAttackModifier((ctx, ap) -> {
            if (hasCurio(ctx.attacker, ModItems.STAR_COIN_HAMMER.get())) {
                ap += ModAttachments.getStarCoinHammerBonus(ctx.attacker);
            }
            return ap;
        });

        // === 内置:诅咒之剑(装备时受青之诅咒;每击杀 1 个 20 血以上敌对目标攻击力 +1,上限由配置决定) ===
        registerAttackModifier((ctx, ap) -> {
            if (hasCurio(ctx.attacker, ModItems.CURSED_SWORD.get())) {
                ap += ModAttachments.getCursedSwordBonus(ctx.attacker);
            }
            return ap;
        });

        // === 内置:复仇之戟(拥有指定负面/诅咒效果时攻击力 +6,只触发一次不叠加) ===
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            if (RevengeHalberdChipItem.isEquipped(ctx.attacker)
                    && RevengeHalberdChipItem.hasAttackTriggerEffect(ctx.attacker)) {
                ap += RevengeHalberdChipItem.BONUS;
            }
            return ap;
        });

        // === 内置:大当家立牌(boss)被动:拥有养精蓄锐时攻击力 +2 ===
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            if (FenSignItem.isEquipped(ctx.attacker)
                    && ModAttachments.getFenRecharge(ctx.attacker) > 0) {
                ap += 2;
            }
            return ap;
        });

        // === 内置:大当家立牌(fen)主动"战斗爽":攻击力 +3(持续 1:00) ===
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.hasEffect(ModEffects.FEN_FRENZY)) {
                ap += 3;
            }
            return ap;
        });

        // === 内置:骇客立牌(nancy_lu)被动攻击/主动远程骇入攻击力 ===
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            ap += NancyLuSignItem.getAttackBonus(ctx.attacker);
            ap += NancyLuSignItem.getActiveAttackBonus(ctx.attacker);
            return ap;
        });

        // === 内置:秘密侦探立牌(bonnie)被动(攻击带"标记"目标攻击力+3) ===
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            if (MarkManager.getLevel(ctx.target) > 0 && hasCurio(ctx.attacker, ModItems.BONNIE_SIGN.get())) {
                ap += 3;
            }
            return ap;
        });

        // === 内置:调查阶段增益(阶段 II 及以上对非 boss 敌对目标/真相揭露对 boss) ===
        registerAttackModifier((ctx, ap) -> {
            var investigation = ctx.attacker.getEffect(ModEffects.INVESTIGATION_BONUS);
            if (investigation == null) return ap;
            int stage = investigation.getAmplifier(); // 1=I,2=II,3=III,4=真相揭露(I 无攻击加成)
            int markLevel = MarkManager.getLevel(ctx.target);
            boolean isBoss = BossEntityUtil.isBossEntity(ctx.target);
            boolean isHostile = ctx.target instanceof Enemy;
            if (!isBoss && isHostile) {
                if (stage >= 3) {
                    ap += 2 + markLevel;
                } else if (stage == 2) {
                    ap += 2;
                }
            } else if (isBoss && stage == 4) {
                ap += 2 + markLevel * 2;
            }
            return ap;
        });

        // === 内置:上班族立牌(padman)攻击力增益 + 破防标志 ===
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            var curios = CuriosApi.getCuriosInventory(ctx.attacker);
            if (curios.isPresent()) {
                var r = curios.get().findFirstCurio(s -> s.is(ModItems.PADMAN_SIGN.get()));
                if (r.isPresent()) {
                    ItemStack stack = r.get().stack();
                    ap += PadmanSignItem.getAttackBonus(stack);
                    if (ctx.baseDice == 6) {
                        ctx.padmanDefBypass = true;
                    }
                }
            }
            return ap;
        });

        // === 内置:效果类防御加成(岿然不动/抗性) ===
        registerDefenseModifier((ctx, dp) -> {
            var unwavering = ctx.target.getEffect(ModEffects.UNWAVERING);
            if (unwavering != null) {
                dp += 2 * (unwavering.getAmplifier() + 1);
            }
            var resistance = ctx.target.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (resistance != null) {
                dp += (resistance.getAmplifier() + 1) * 2;
            }
            return dp;
        });

        // === 内置:防御卡掷骰(收集结果写入上下文;目标无骰子时 targetEnhancement 为 null,结果 0) ===
        registerDefenseModifier((ctx, dp) -> {
            int sum = 0;
            if (ctx.targetEnhancement != null) {
                // 防御牌在赐福期间持续生效,每次受击独立随机判定;耐久在佩戴者自身触发赐福时统一消耗
                for (AppliedStone stone : ctx.targetEnhancement.appliedStones()) {
                    // 防御牌掷骰统一由 CardRegistry 提供(未知类型返回 0)
                    sum += CardRegistry.roll(stone.type(), ctx);
                }
            }
            ctx.defenseCardSum = sum;
            return dp;
        });

        // === 内置:上班族立牌(padman)防御力增益(目标佩戴时) ===
        registerDefenseModifier((ctx, dp) -> {
            if (ctx.target.level().isClientSide()) return dp;
            if (!(ctx.target instanceof Player tp)) return dp;
            var curios = CuriosApi.getCuriosInventory(tp);
            if (curios.isPresent()) {
                var r = curios.get().findFirstCurio(s -> s.is(ModItems.PADMAN_SIGN.get()));
                if (r.isPresent()) {
                    dp += PadmanSignItem.getDefenseBonus(r.get().stack());
                }
            }
            return dp;
        });

        // === 内置:扫地机立牌(jasmine)防御力增益(目标佩戴时) ===
        registerDefenseModifier((ctx, dp) -> {
            if (ctx.target.level().isClientSide()) return dp;
            if (!(ctx.target instanceof Player tp)) return dp;
            var curios = CuriosApi.getCuriosInventory(tp);
            if (curios.isPresent()) {
                var r = curios.get().findFirstCurio(s -> s.is(ModItems.JASMINE_SIGN.get()));
                if (r.isPresent()) {
                    dp += JasmineSignItem.getDefenseBonus(r.get().stack());
                }
            }
            return dp;
        });

        // === 内置:吸血鬼立牌(papara)防御力被动(目标半血或"嘬一口"期间防御力+3) ===
        registerDefenseModifier((ctx, dp) -> {
            if (ctx.target.level().isClientSide()) return dp;
            if (!(ctx.target instanceof Player tp)) return dp;
            boolean active = tp.getHealth() <= tp.getMaxHealth() / 2.0f || tp.hasEffect(ModEffects.PAPARA_BITE);
            if (active && hasCurio(tp, ModItems.PAPARA_SIGN.get())) {
                dp += 3;
            }
            return dp;
        });

        // === 内置:复仇之戟(拥有指定负面效果时防御力 +6,只触发一次不叠加) ===
        registerDefenseModifier((ctx, dp) -> {
            if (ctx.target.level().isClientSide()) return dp;
            if (!(ctx.target instanceof Player tp)) return dp;
            if (RevengeHalberdChipItem.isEquipped(tp)
                    && RevengeHalberdChipItem.hasDefenseTriggerEffect(tp)) {
                dp += RevengeHalberdChipItem.BONUS;
            }
            return dp;
        });

        // === 内置:骇客立牌(nancy_lu)被动防御 ===
        registerDefenseModifier((ctx, dp) -> {
            if (ctx.target.level().isClientSide()) return dp;
            if (!(ctx.target instanceof Player tp)) return dp;
            dp += NancyLuSignItem.getDefenseBonus(tp);
            return dp;
        });

        // === 内置:大当家立牌(fen)被动:拥有养精蓄锐时防御力 +2(目标佩戴时) ===
        registerDefenseModifier((ctx, dp) -> {
            if (ctx.target.level().isClientSide()) return dp;
            if (!(ctx.target instanceof Player tp)) return dp;
            if (FenSignItem.isEquipped(tp) && ModAttachments.getFenRecharge(tp) > 0) {
                dp += 2;
            }
            return dp;
        });
    }

    // === GUI 显示用:计算当前攻击力/防御力(不包含随机骰点与卡牌掷骰,仅基础值+修饰器) ===
    public static int getDisplayAttackPower(Player player, ItemStack diceStack, WeaponEnhancement enhancement) {
        if (player == null) return 0;
        if (enhancement == null) enhancement = WeaponEnhancement.EMPTY;
        int misakiStar = enhancement.starLevel();
        int misakiStacks = 0;
        boolean misakiBurst = player.hasEffect(ModEffects.MISAKI_BURST);
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isPresent()) {
            var r = curios.get().findFirstCurio(s -> s.is(ModItems.MISAKI_SIGN.get()));
            if (r.isPresent()) {
                misakiStacks = r.get().stack().getOrDefault(ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);
            }
        }
        DiceCombatContext ctx = new DiceCombatContext(
                player, player, null, 0, diceStack, enhancement, false,
                misakiBurst, misakiStar, misakiStacks);
        double ap = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (AttackPowerModifier modifier : attackModifiers()) {
            ap = modifier.apply(ctx, ap);
        }
        return (int) Math.floor(ap);
    }

    public static int getDisplayDefensePower(Player player) {
        if (player == null) return 0;
        DiceCombatContext ctx = new DiceCombatContext(
                player, player, null, 0, ItemStack.EMPTY, WeaponEnhancement.EMPTY, false,
                false, 0, 0);
        double modifierDefense = 0;
        for (DefensePowerModifier modifier : defenseModifiers()) {
            modifierDefense = modifier.apply(ctx, modifierDefense);
        }
        // 效果牌/立牌/事件/筹码的防御力始终按 1 防御力 = 2 护甲值折算为护甲值
        double rawArmor = Math.min(player.getArmorValue(), 20);
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double effectiveArmor = Math.max(0, Math.min(rawArmor + modifierDefense * 2.0, 20));
        double dp = 2 + effectiveArmor / 2.0 + 1.4 * toughness;
        return (int) Math.floor(dp);
    }

    public record PowerRange(int min, int max) {
    }

    // === GUI 显示用:攻击/防御范围(基础值+修饰器+卡牌下限/上限) ===
    public static PowerRange getDisplayAttackRange(Player player, ItemStack diceStack, WeaponEnhancement enhancement) {
        if (player == null) return new PowerRange(0, 0);
        if (enhancement == null) enhancement = WeaponEnhancement.EMPTY;
        int misakiStar = enhancement.starLevel();
        int misakiStacks = 0;
        boolean misakiBurst = player.hasEffect(ModEffects.MISAKI_BURST);
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isPresent()) {
            var r = curios.get().findFirstCurio(s -> s.is(ModItems.MISAKI_SIGN.get()));
            if (r.isPresent()) {
                misakiStacks = r.get().stack().getOrDefault(ModDataComponents.MISAKI_SIGN_STACKS.get(), 0);
            }
        }
        DiceCombatContext ctx = new DiceCombatContext(
                player, player, null, 0, diceStack, enhancement, false,
                misakiBurst, misakiStar, misakiStacks);
        double ap = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (AttackPowerModifier modifier : attackModifiers()) {
            ap = modifier.apply(ctx, ap);
        }
        int base = (int) Math.floor(ap);
        int min = base;
        int max = base;
        for (AppliedStone stone : enhancement.appliedStones()) {
            if (CardRegistry.isDefense(stone.type())) continue;
            min += CardRegistry.minRoll(stone.type());
            max += CardRegistry.maxRoll(stone.type());
        }
        return new PowerRange(min, max);
    }

    public static PowerRange getDisplayDefenseRange(Player player, WeaponEnhancement enhancement) {
        if (player == null) return new PowerRange(0, 0);
        if (enhancement == null) enhancement = WeaponEnhancement.EMPTY;
        DiceCombatContext ctx = new DiceCombatContext(
                player, player, null, 0, ItemStack.EMPTY, enhancement, false,
                false, 0, 0);
        double modifierDefense = 0;
        for (DefensePowerModifier modifier : defenseModifiers()) {
            modifierDefense = modifier.apply(ctx, modifierDefense);
        }
        // 效果牌/立牌/事件/筹码的防御力始终按 1 防御力 = 2 护甲值折算为护甲值
        double rawArmor = Math.min(player.getArmorValue(), 20);
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double effectiveArmor = Math.max(0, Math.min(rawArmor + modifierDefense * 2.0, 20));
        double dp = 2 + effectiveArmor / 2.0 + 1.4 * toughness;
        int base = (int) Math.floor(dp);
        int min = base;
        int max = base;
        // 防御卡仅在骰神赐福期间作为防御点生效
        if (player.hasEffect(ModEffects.DICE_BLESSING)) {
            for (AppliedStone stone : enhancement.appliedStones()) {
                if (!CardRegistry.isDefense(stone.type())) continue;
                min += CardRegistry.minRoll(stone.type());
                max += CardRegistry.maxRoll(stone.type());
            }
        }
        return new PowerRange(min, max);
    }


}
