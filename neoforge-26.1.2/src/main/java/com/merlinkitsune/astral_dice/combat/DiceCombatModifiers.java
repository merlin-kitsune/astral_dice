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
import com.merlinkitsune.astral_dice.item.chip.ElectricSwordChipItem;
import com.merlinkitsune.astral_dice.item.chip.AdvancedPeripheralsChipItem;
import com.merlinkitsune.astral_dice.item.sign.FenSignItem;
import com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem;
import com.merlinkitsune.astral_dice.item.sign.MosesSignItem;
import com.merlinkitsune.astral_dice.effect.WeaknessRevealEffect;
import com.merlinkitsune.astral_dice.item.sign.JasmineSignItem;
import com.merlinkitsune.astral_dice.item.sign.NardisSignItem;
import com.merlinkitsune.astral_dice.item.sign.MamushiSignItem;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.PadmanSignItem;
import com.merlinkitsune.starenginelib.item.BossEntityUtil;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.starenginelib.combat.HostileTargets;

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
 *
 * <p>另含**受击侧(受伤方)伤害修饰器**注册表({@link VictimDamageModifier}):作用于**任何来源**的
 * 最终伤害,与攻击方是否触发骰神赐福无关(内置:末影骰子雨中/水下 +40%)。
 */
public final class DiceCombatModifiers {

    // 额外加伤修饰器表(与攻击力修饰器**分离**:见 extraDamageOf 的口径说明)
    private static final List<ExtraDamageModifier> EXTRA_DAMAGE_MODIFIERS = new ArrayList<>();

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

    /**
     * **额外加伤**唯一求值入口:命中落地后按独立伤害类型({@code astral_dice:extra_damage})单独结算的加伤合计。
     *
     * <p>与 {@link #attackModifiers()} **分离**:这些加伤不得并进「攻击力」—— 否则会污染依赖「攻击力快照」
     * 的效果(教主立牌降神的「狐光攻击基数」)⇒ 2026-09-25 用户裁决拆成独立通道,文案一律称「攻击伤害」
     * (加到玩家基础伤害的才叫「攻击力」)。
     */
    public static int extraDamageOf(DiceCombatContext ctx) {
        if (ctx == null) return 0;
        int sum = 0;
        for (ExtraDamageModifier modifier : EXTRA_DAMAGE_MODIFIERS) {
            sum += Math.max(0, modifier.extraDamage(ctx));
        }
        return sum;
    }

    /** 额外加伤修饰器:返回本次命中应额外结算的点数(0 = 不适用)。 */
    @FunctionalInterface
    public interface ExtraDamageModifier {
        int extraDamage(DiceCombatContext ctx);
    }

    /** 注册额外加伤修饰器(按注册顺序累加) */
    public static void registerExtraDamageModifier(ExtraDamageModifier modifier) {
        EXTRA_DAMAGE_MODIFIERS.add(modifier);
    }


    public static List<DefensePowerModifier> defenseModifiers() {
        return List.copyOf(DEFENSE_MODIFIERS);
    }

    // ===== 受击侧(受伤方)伤害修饰器注册表 =====

    /**
     * 受击侧伤害修饰器:按「受伤方 + 伤害来源」给出**伤害倍率**(1.0 = 不变)。
     *
     * <p>与攻击/防御修饰器不同,本表作用于**任何来源**的伤害(近战/远程/怪物/摔落/溺水/虚空等),
     * 因为"受伤方减益"与攻击方是否触发骰神赐福无关。
     *
     * <p>约定:修饰器必须是**实时谓词**(每次调用基于当前状态重新判定),不得缓存状态 ——
     * 条件不再成立时倍率立即失效(例:末影骰雨中/水下 +40% 在脱下骰子或离开雨/水后立刻消失)。
     */
    @FunctionalInterface
    public interface VictimDamageModifier {
        double multiplier(LivingEntity victim, DamageSource source);
    }

    private static final List<VictimDamageModifier> VICTIM_DAMAGE_MODIFIERS = new ArrayList<>();

    // A3「恰好一次」口径:本次伤害实例已登记的受击侧倍率与受害者。
    // 仅在**单次伤害事件的同步处理链**内存活(下一个伤害实例的前置消费点会整槽刷新,
    // 且读取方必须校验受害者同一),不跨 tick/跨实例持久化;服务端主线程假设与
    // aoeProcessing / counterDepth 一致(见 docs/scan2/P3 的 A17)。
    private static LivingEntity instanceVictim;
    private static double instanceFactor = 1.0;

    /** 注册受击侧伤害修饰器(按注册顺序连乘) */
    public static void registerVictimDamageModifier(VictimDamageModifier modifier) {
        VICTIM_DAMAGE_MODIFIERS.add(modifier);
    }

    /** 本次伤害应应用的受击侧倍率乘积(纯函数,不落地状态) */
    private static double victimDamageFactor(LivingEntity victim, DamageSource source) {
        if (victim == null) return 1.0;
        double factor = 1.0;
        for (VictimDamageModifier modifier : VICTIM_DAMAGE_MODIFIERS) {
            factor *= modifier.multiplier(victim, source);
        }
        return factor;
    }

    /**
     * **修饰器的唯一应用点**:把受击侧修饰器应用到当前伤害值,并把本次实例的倍率登记下来
     * (供骰战路径搬运)。
     *
     * <p><b>A3 口径(恰好一次)</b>:修饰器在整个伤害实例内只被求值一次、倍率只落在**最终落地的那个值**上。
     * 骰战在 {@code LivingDamageEvent.Pre}/{@code LivingDamageEvent} 以
     * {@code setNewDamage}/{@code setAmount} **覆盖式**写入自算的最终伤害,前置消费点乘出的那份值会被整段替换
     * (见 {@code docs/interaction-audit-1.2.1.md} 的 A3)—— 若骰战路径再消费一次修饰器,
     * 同一次伤害就经过两套独立计算("丢弃"变成"重复");故骰战路径只调用
     * {@link #instanceVictimFactor(LivingEntity)} 搬运同一倍率。
     *
     * <p>damage ≤ 0 时仍刷新登记(只是不乘),避免骰战路径读到上一次实例的陈旧倍率。
     */
    public static double applyVictimDamageModifiers(LivingEntity victim, DamageSource source, double damage) {
        if (victim == null) return damage;
        double factor = victimDamageFactor(victim, source);
        instanceVictim = victim;
        instanceFactor = factor;
        return damage > 0 ? damage * factor : damage;
    }

    /**
     * 取**本次伤害实例**已登记的受击侧倍率(仅当受害者与登记对象相同时有效,否则返回 1.0)。
     *
     * <p>只做"搬运",不重新消费修饰器:保证同一次伤害恰好应用一次(见
     * {@link #applyVictimDamageModifiers})。
     */
    public static double instanceVictimFactor(LivingEntity victim) {
        return victim != null && victim == instanceVictim ? instanceFactor : 1.0;
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
        Identifier id = Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, modifierKey);
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
            var strength = ctx.attacker.getEffect(MobEffects.STRENGTH);
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
                // 玻璃骰子:攻击牌点数始终取最大值
                sum += CardRegistry.roll(stone.type(), ctx, ctx.attackerCardsMax);
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

        // === 额外加伤:美工刀 / 美工刀-锋利(生命值不低于 60% 时,按当前治愈点数加伤) ===
        // 2026-09-25 用户裁决:由「攻击力修饰器」改为**额外加伤修饰器** —— 独立伤害类型结算,
        // 不进攻击力(故不会污染降神的攻击力快照),文案称「攻击伤害」。
        registerExtraDamageModifier(ctx -> {
            if (ctx.attacker.level().isClientSide()) return 0;
            boolean fullHp = ctx.attacker.getHealth() >= ctx.attacker.getMaxHealth() * 0.6f
                    || ctx.attacker.hasEffect(ModEffects.PAPARA_BITE);
            if (!fullHp) return 0;
            int healing = HealingManager.getPoints(ctx.attacker);
            int extra = 0;
            if (hasCurio(ctx.attacker, ModItems.CUTTER_CHIP.get())) {
                extra += 2 + healing;
            }
            if (hasCurio(ctx.attacker, ModItems.CUTTER_BLADE_CHIP.get())) {
                extra += 4 + healing;
            }
            return extra;
        });

        // === 内置:普通瞄具/鹰眼瞄具(攻击力+2;骰神赐福期间攻击时施加 1 层标记 / 按标记层数×2 加攻击力) ===
        // ⚠️ 2026-09-24 用户裁决:「瞄具类攻击实际上受**骰神赐福门控** —— 不按骰神赐福模式攻击目标
        //   (近战类)则不应该触发」⇒ 标记与鹰眼的 ×2 都要求 `DICE_BLESSING` 生效;
        //   普通瞄具的固定 +2 仍**无条件**(与 tooltip 同源:「攻击力 +2。骰神赐福期间攻击时对目标施加 1 层标记」)。
        //   旧实现只判「是否佩戴」⇒ 未处于赐福期间也会施加标记/加成,与文案不符,本次补齐门控。
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            boolean blessed = ctx.attacker.hasEffect(ModEffects.DICE_BLESSING);
            if (hasCurio(ctx.attacker, ModItems.SCOPE_CHIP.get())) {
                ap += 2;
                if (blessed && ctx.event != null) MarkManager.apply(ctx.target);
            }
            if (hasCurio(ctx.attacker, ModItems.EAGLE_SCOPE_CHIP.get())) {
                if (blessed) {
                    int markLevel = MarkManager.getLevel(ctx.target);
                    ap += markLevel * 2;
                    if (ctx.event != null) MarkManager.apply(ctx.target);
                }
            }
            return ap;
        });

        // === 内置:标靶(攻击力+1) ===
        // 手电筒-强光(每 4 星光 +1)自 2026-09-25 起改为**额外加伤**(见下方专属块)。
        registerAttackModifier((ctx, ap) -> {
            if (hasCurio(ctx.attacker, ModItems.TARGET_CHIP.get())) {
                ap += 1;
            }
            return ap;
        });

        // === 额外加伤:手电筒-强光(每 4 层星光 +1 点) ===
        registerExtraDamageModifier(ctx -> {
            if (!hasCurio(ctx.attacker, ModItems.FLASHLIGHT_CHIP.get())) return 0;
            return StarLightManager.get(ctx.attacker) / 4;
        });

        // === 内置:电流剑(每 4 点充能 +1 攻击力) ===
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            ap += ElectricSwordChipItem.getAttackBonus(ctx.attacker);
            return ap;
        });

        // === 内置:高级外设(充能 ≥ 4 时攻击力 +4) ===
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            ap += AdvancedPeripheralsChipItem.getAttackBonus(ctx.attacker);
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

        // === 内置:吸血鬼立牌(papara)被动(半血或"汲取"期间攻击力+3) ===
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

        // === 额外加伤:星币锤(进入骰神赐福消耗星币,按持有总数 30% 加伤,赐福结束清除) ===
        // 2026-09-25 用户裁决:由「攻击力修饰器」改为**额外加伤修饰器**(独立伤害类型)。
        registerExtraDamageModifier(ctx -> {
            if (!hasCurio(ctx.attacker, ModItems.STAR_COIN_HAMMER.get())) return 0;
            return ModAttachments.getStarCoinHammerBonus(ctx.attacker);
        });

        // === 内置:诅咒之剑(装备时受青之诅咒;每击杀 1 个不少于 20 血的敌对目标攻击力 +1,上限由配置决定) ===
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
        // === 内置:枪匠立牌(moses)弱点识破攻击力(每层 +1) ===
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            if (MosesSignItem.isEquipped(ctx.attacker)) {
                ap += WeaknessRevealEffect.getStacks(ctx.attacker);
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
            // 2026-09-24 口径统一:改用**法伤链同一闸门** isBlessingTarget(比 HostileTargets 更宽:
            // 含非同队玩家 / Boss / 会反击的中立怪)⇒ 与骰神赐福、贯穿之铳、忍术飞镖一致
            boolean isHostile = com.merlinkitsune.astral_dice.combat.DiceCombatEvents
                    .isBlessingTarget(ctx.target, ctx.attacker);
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

        // === 内置:肾上腺素-高效筹码(生命值为 50% 或更低时攻击力 +8;汲取期间无条件触发) ===
        registerAttackModifier((ctx, ap) -> {
            Player p = ctx.attacker;
            if (p.level().isClientSide()) return ap;
            if (!com.merlinkitsune.astral_dice.item.chip.AdrenalineChipItem.isLowHp(p)) return ap;
            if (hasCurio(p, ModItems.ADRENALINE_LOW.get())) ap += AdrenalineChipItem.BONUS_LOW;
            if (hasCurio(p, ModItems.ADRENALINE_HIGH.get())) ap += AdrenalineChipItem.BONUS_HIGH;
            return ap;
        });

        // === 内置:原初核心筹码(每层"赋能"攻击力 +1;防御力经 tick 折算为真实护甲,不在骰战修饰器内) ===
        registerAttackModifier((ctx, ap) -> {
            Player p = ctx.attacker;
            if (p.level().isClientSide()) return ap;
            return ap + com.merlinkitsune.astral_dice.item.chip.PrimordialCoreChipItem.getAttackBonus(p);
        });

        // === 内置:电磁炮筹码(充能不少于 6 层时攻击力 +5) ===
        registerAttackModifier((ctx, ap) -> {
            Player p = ctx.attacker;
            if (p.level().isClientSide()) return ap;
            return ap + com.merlinkitsune.astral_dice.item.chip.RailgunChipItem.getAttackBonus(p);
        });

        // === 内置:磨刀石筹码(生命值为 50% 或更低时攻击力 +4;减伤在受击侧处理) ===
        registerAttackModifier((ctx, ap) -> {
            Player p = ctx.attacker;
            if (p.level().isClientSide()) return ap;
            return ap + com.merlinkitsune.astral_dice.item.chip.WhetstoneChipItem.getAttackBonus(p);
        });


        // === 内置:风水师立牌(zhao)主动「白泽赐福」—— 溢出治疗等量转化的攻击力(2026-09-26) ===
        // 加成本身是玩家附件(整数化 + 取整余数留在另一个浮点累加器;唯一写入方 =
        // ZhaoSignItem#onLivingHeal,唯一回收动作 = clearZhaoOverflowBonus,在"赐福结束/被移除/
        // 死亡/重登"四条路径上幂等调用),故赐福不存在时该加算项必为 0(回收彻底、与其它来源互不影响)。
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            ap += ModAttachments.getZhaoOverflowBonus(ctx.attacker);
            return ap;
        });

        // === 内置:绿洲女王立牌(nardis)被动「威压」—— 每装备一张**攻击牌**攻击力 +2 ===
        // 计数直接取**攻击方骰子的 enhancement**(ctx.enhancement)⇒ 与实战同源;
        // tooltip/GUI 走 getDisplayAttackRange 时传的是按卡牌栏实时构建的 enhancement,口径一致
        // (所以不需要第二条链路)。临时牌**同样计入**它只是一张普通战斗牌,只是被打了标记;
        // 被动防御力(+2/防御牌)走真实护甲折算,见 NardisSignItem#onCurioTick。
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            if (!NardisSignItem.isEquipped(ctx.attacker)) return ap;
            WeaponEnhancement enh = ctx.enhancement != null
                    ? ctx.enhancement
                    : NardisSignItem.equippedEnhancement(ctx.attacker);
            return ap + NardisSignItem.BONUS_PER_CARD * NardisSignItem.countStones(enh, false);
        });

        // === 内置:教主立牌(teru)主动「降神」—— 施法者获得目标 50% 攻击力(镜像缓存) ===
        // 攻击加成真值在**目标**身上(施法瞬间快照),施法者侧只读每 tick 派生出来的镜像缓存
        // (见 item/sign/TeruSignItem#tickCasterSide):目标效果结束/死亡/登出 ⇒ 同 tick 归 0,不留残留。
        // 防御加成同源,但走真实护甲(setDefenseArmorBonus),不在攻击修饰器内。
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            return ap + ModAttachments.getTeruAtkBonusCache(ctx.attacker);
        });

        // === 内置:教主立牌(teru)「狐光」—— 降神目标每攻击一个**新目标**,消耗 1 层并追加攻击力 ===
        // 额外攻击 = 狐光攻击基数(施法者快照攻击力 = 施加时的基础攻击力 + 从目标获得的 50%,施法瞬间快照)
        //           + 消耗 1 层后的剩余层数;计入骰战**攻击力**(受目标防御力抵扣,并参与全力攻击等既有倍率)。
        // 「新目标」判定与消耗/登记全部收敛在 TeruSignItem#descendExtraAttack
        // (层数已为 0 ⇒ 不消耗、不追加;施法者离线 ⇒ 不加成、不消耗)。
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            return ap + com.merlinkitsune.astral_dice.item.sign.TeruSignItem
                    .descendExtraAttack(ctx.attacker, ctx.target);
        });

        
        // === 内置:蛟龙立牌(mamushi)攻击力加成(2026-09-27) ===
        // 全部走 MamushiSignItem 的冻结查询方法,**实时谓词**、不落地任何状态:
        // ① 真龙形态(觉醒 ≥ 8 且佩戴立牌)⇒ 攻击力 +5;
        // ② 撕咬锁存(bite_bonus_active,由「触发骰神赐福且装备撕咬」置位、赐福结束清除)
        //    且**仍有骰神赐福** ⇒ +min(觉醒层数, 4)(裁决 4:层数变化即时体现)。
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            if (MamushiSignItem.isDragonForm(ctx.attacker)) {
                ap += MamushiSignItem.DRAGON_FORM_ATTACK_BONUS;
            }
            if (MamushiSignItem.getBiteBonusActive(ctx.attacker)
                    && ctx.attacker.hasEffect(ModEffects.DICE_BLESSING)) {
                ap += Math.min(MamushiSignItem.getAwakening(ctx.attacker), MamushiSignItem.BITE_BONUS_CAP);
            }
            return ap;
        });

        // === 内置:怪力侦探立牌(sherry)「推理时间」—— 每层攻击力 +1 ===
        // 层数真值在附件(死亡不清),这里以**实时谓词**读取、不落地任何状态 ⇒ 层数变化立即体现,
        // 卸下立牌(clearSignData 归零)后即刻失效。与「弱点识破」同款写法。
        registerAttackModifier((ctx, ap) -> {
            if (ctx.attacker.level().isClientSide()) return ap;
            return ap + com.merlinkitsune.astral_dice.item.sign.SherrySignItem.getLayers(ctx.attacker);
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
                    // 玻璃骰子:防御牌点数始终取最大值
                    sum += CardRegistry.roll(stone.type(), ctx, ctx.targetCardsMax);
                }
            }
            ctx.defenseCardSum = sum;
            return dp;
        });

        // === 内置:受击侧伤害修饰器 —— 末影骰子「雨中/水下受伤 +40%」(A3) ===
        // 判定口径**原样复用**原实现:佩戴末影骰子 + isInWaterOrRain()(雨/水/气泡柱)。
        // 以"实时谓词"注册、不落地任何状态 ⇒ 脱下末影骰子或离开雨/水后该倍率立即失效。
        // 唯一应用点是 applyVictimDamageModifiers(EnderDiceHandler@HIGH 调用):覆盖环境/怪物/弹射物/
        // 非赐福攻击等全部来源;骰战路径只搬运 instanceVictimFactor,不再二次消费(见 applyVictimDamageModifiers 注释)。
        registerVictimDamageModifier((victim, source) -> {
            if (victim.level().isClientSide()) return 1.0;
            if (!(victim instanceof Player player)) return 1.0;
            if (!com.merlinkitsune.astral_dice.event.EnderDiceHandler.hasEnderDie(player)) return 1.0;
            if (!player.isInWaterOrRain()) return 1.0;
            return com.merlinkitsune.astral_dice.event.EnderDiceHandler.RAIN_WATER_DAMAGE_MULTIPLIER;
        });
    }

    public record PowerRange(int min, int max) {
    }

    /**
     * 玩家的**攻击力**(与 GUI/tooltip 同源的口径):属性攻击力 + 全部已注册攻击修饰器。
     *
     * <p>唯一用途是「施法瞬间快照」(教主立牌 teru 的降神:目标攻击力 50%、狐光攻击基数),
     * 故这里取的是**显示口径的整数基值**(不含骰点与卡牌加成),与
     * {@link #getDisplayAttackRange} 的基础项**逐字同源**——两者都由
     * {@link #attackPowerBase} 计算,不存在"tooltip 一个值、结算另一个值"的漂移。
     *
     * <p>上下文按既有显示口径构造({@code ctx.target == player} 自己):与 GUI 显示同款近似,
     * 依赖"目标状态"的修饰器(如秘密侦探对带标记目标的加成)在自身身上自然取 0。
     */
    public static int attackPowerOf(Player player) {
        if (player == null) return 0;
        WeaponEnhancement enhancement = WeaponEnhancement.EMPTY;
        ItemStack diceStack = ItemStack.EMPTY;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isPresent()) {
            var r = curios.get().findFirstCurio(com.merlinkitsune.astral_dice.item.dice.DiceCurioItem::isDiceItem);
            if (r.isPresent()) {
                diceStack = r.get().stack();
                enhancement = diceStack.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
            }
        }
        return (int) Math.floor(attackPowerBase(player, diceStack, enhancement));
    }

    /**
     * 玩家的**防御力**(与 GUI/tooltip 同源的口径):{@code 2 + 有效护甲÷2 + 1.4×盔甲韧性}。
     *
     * <p>效果牌/立牌/筹码的防御力都已折算为**真实护甲**(1 防御 = 2 护甲值,见
     * {@link #setDefenseArmorBonus}),故 {@code getArmorValue()} 已包含它们 ⇒ 本方法自动反映
     * 「当前实际防御力」。与 {@link #getDisplayDefenseRange} 的基础项逐字同源。
     */
    public static int defensePowerOf(Player player) {
        if (player == null) return 0;
        WeaponEnhancement enhancement = WeaponEnhancement.EMPTY;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isPresent()) {
            var r = curios.get().findFirstCurio(com.merlinkitsune.astral_dice.item.dice.DiceCurioItem::isDiceItem);
            if (r.isPresent()) {
                enhancement = r.get().stack()
                        .getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
            }
        }
        return (int) Math.floor(defensePowerBase(player, enhancement));
    }

    /** 攻击力基础值(属性 + 攻击修饰器链);{@link #getDisplayAttackRange} 与 {@link #attackPowerOf} 共用 */
    private static double attackPowerBase(Player player, ItemStack diceStack, WeaponEnhancement enhancement) {
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
        return ap;
    }

    /** 防御力基础值(2 + 护甲÷2 + 1.4×韧性);{@link #getDisplayDefenseRange} 与 {@link #defensePowerOf} 共用 */
    private static double defensePowerBase(Player player, WeaponEnhancement enhancement) {
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
        return 2 + effectiveArmor / 2.0 + 1.4 * toughness;
    }

    // === GUI 显示用:攻击/防御范围(基础值+修饰器+卡牌下限/上限) ===
    public static PowerRange getDisplayAttackRange(Player player, ItemStack diceStack, WeaponEnhancement enhancement) {
        if (player == null) return new PowerRange(0, 0);
        if (enhancement == null) enhancement = WeaponEnhancement.EMPTY;
        double ap = attackPowerBase(player, diceStack, enhancement);
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
        double dp = defensePowerBase(player, enhancement);
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
