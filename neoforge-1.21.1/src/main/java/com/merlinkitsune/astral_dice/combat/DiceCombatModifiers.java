package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.chip.BoxingGlovesChipItem;
import com.merlinkitsune.astral_dice.item.chip.AdrenalineChipItem;
import com.merlinkitsune.astral_dice.item.chip.RevengeHalberdChipItem;
import com.merlinkitsune.astral_dice.item.sign.FenSignItem;
import com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem;
import com.merlinkitsune.astral_dice.item.sign.JasmineSignItem;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.PadmanSignItem;
import com.merlinkitsune.astral_dice.item.BossEntityUtil;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 骰神赐福攻防修饰器注册表:管理攻击力/防御力修饰器的有序注册与内置修饰器。
 *
 * 内置修饰器覆盖:
 * - 效果类加成(王之力/狂暴/力量 / 抗性);
 * - 卡牌掷骰(攻击/防御);
 * - 全部立牌与筹码的战斗加成(护法 misaki/扫地机 jasmine/吸血鬼 papara/秘密侦探 bonnie/
 *   上班族 padman/调查阶段,以及美工刀/瞄具/标靶/手电筒等筹码)。
 *
 * 防御力规范(必须遵守):**仅战斗牌(防御牌)参与骰战防御修饰器**(只有防御牌数值是区间变动,
 * 由 {@link CardRegistry} 掷骰);效果牌/立牌/筹码提供的防御力一律折算为真实护甲
 * (1 防御力 = 2 护甲值),经 {@link #setDefenseArmorBonus} 挂到玩家 ARMOR 属性——
 * 骰战经护甲项(护甲÷2)自动计入,原版伤害管线(骰战伤害无穿透标志)同样按真实护甲减伤。
 *
 * 附属内容(新立牌/筹码/效果/联动)实现 {@link AttackPowerModifier} / {@link DefensePowerModifier}
 * 并通过 register 注册即可影响攻防,无需修改 DiceCombatEvents 主流程。
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

    // 注册防御力修饰器(按注册顺序执行;仅战斗防御牌使用)
    public static void registerDefenseModifier(DefensePowerModifier modifier) {
        DEFENSE_MODIFIERS.add(modifier);
    }

    public static List<AttackPowerModifier> attackModifiers() {
        return List.copyOf(ATTACK_MODIFIERS);
    }

    public static List<DefensePowerModifier> defenseModifiers() {
        return List.copyOf(DEFENSE_MODIFIERS);
    }

    /**
     * 效果牌/立牌/筹码的防御力统一折算为真实护甲(1 防御力 = 2 护甲值)。
     * 通过瞬态 ARMOR 属性修饰器施加:仅当数值变化时才增删(避免每 tick 属性同步)。
     * 数值 ≤ 0 时移除修饰器(护甲属性下限 0,负防御自然失效)。
     */
    public static void setDefenseArmorBonus(Player player, String modifierKey, int defensePoints) {
        if (player == null || player.level().isClientSide()) return;
        AttributeInstance attr = player.getAttribute(Attributes.ARMOR);
        if (attr == null) return;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, modifierKey);
        double armor = defensePoints * 2.0;
        var existing = attr.getModifier(id);
        if (armor <= 0) {
            if (existing != null) attr.removeModifier(id);
            return;
        }
        if (existing == null || existing.amount() != armor) {
            attr.removeModifier(id);
            attr.addTransientModifier(new AttributeModifier(id, armor, AttributeModifier.Operation.ADD_VALUE));
        }
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

        // === 内置:夹心饼干-美味(最大生命值超过 20 点的部分,每 4 点 +1 攻击力) ===
        registerAttackModifier((ctx, ap) -> {
            Player p = ctx.attacker;
            if (p.level().isClientSide()) return ap;
            if (hasCurio(p, ModItems.SANDWICH_HIGH.get())) {
                ap += com.merlinkitsune.astral_dice.item.chip.SandwichChipItem.getAttackBonus(p);
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

        // === 内置:骇客立牌(nancy_lu)被动攻击/主动远程侵入攻击力 ===
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

        // === 内置:肾上腺素-高效筹码(生命值低于最大生命值一半时攻击力 +8) ===
        registerAttackModifier((ctx, ap) -> {
            Player p = ctx.attacker;
            if (p.level().isClientSide()) return ap;
            if (p.getHealth() >= p.getMaxHealth() / 2.0f) return ap;
            if (hasCurio(p, ModItems.ADRENALINE_HIGH.get())) ap += AdrenalineChipItem.BONUS_HIGH;
            return ap;
        });

        // === 内置:防御卡掷骰(收集结果写入上下文;目标无骰子时 targetEnhancement 为 null,结果 0)。
        // 防御力规范:骰战防御修饰器仅保留战斗防御牌(区间变动);效果牌/立牌/筹码的防御力
        // 统一折算为真实护甲(1 防御力 = 2 护甲值),由各自 tick 经 setDefenseArmorBonus 挂到 ARMOR 属性,
        // 骰战经护甲项自动计入(见 DiceCombatEvents 防御结算) ===
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
        // 效果牌/立牌/筹码的防御力已折算为真实护甲(1 防御力 = 2 护甲值,见 setDefenseArmorBonus),
        // getArmorValue() 已包含其瞬态修饰器;此处 modifierDefense 恒为 0(仅防御卡掷骰写 ctx.defenseCardSum)
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
