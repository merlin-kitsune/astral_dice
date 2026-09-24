package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.starenginelib.event.AmethystDiceHandler;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrowableProjectile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.chip.MagicQuiverChipItem;
import com.merlinkitsune.astral_dice.item.chip.PiercingGunChipItem;
import com.merlinkitsune.starenginelib.combat.HostileTargets;

/**
 * 法伤(远程/魔法伤害)模块:作用域判定(白名单 matcher + 军火黑名单保险)与加成修饰器注册表。
 *
 * 作用域(白名单):
 * 1. 原生弹射物:弓/弩箭矢、三叉戟(AbstractArrow)、投掷物/投掷药水(ThrowableProjectile);
 * 2. 原版魔法:magic / indirectMagic;
 * 3. 新生魔艺(ars_nouveau):generic_spell_damage/windshear/cold_snap/flare/crush;
 * 4. 诡厄巫法(goety):summon/shock/freeze/hellfire/magic_fire/magic_fireball/magic_bolt 等法术伤害类型
 *    (排除近战类 goety:sword);
 * 5. Iron 的法术与魔法书(irons_spellbooks):fire_magic/ice_magic/lightning_magic/holy_magic/ender_magic/
 *    blood_magic/evocation_magic/eldritch_magic/nature_magic 等;
 * 6. 本模组「活体书页」命中伤害(astral_dice:card_spell,2026-09-25 起;见 {@link LivingPageImpact})。
 * 排除:枪械/炮弹/炸药/火箭等军火类(tacZ、维克斯的武器、卓越前线、气动工艺、机械动力:火炮、通用机械:武器、
 * 沉浸工程等)——其弹丸实体不属于白名单,黑名单关键词仅作"弹丸继承原生类"场景的保险。
 * 该排除由公共配置 {@code allow_firearm_damage} 控制(**默认 false 即默认继续排除**);设为 true 时,
 * 弹丸实体类名或伤害类型关键词命中的军火类伤害也会进入下方白名单 matcher 判定。
 */
public final class SpellDamageRegistry {

    // === 伤害类型精确匹配(ResourceKey) ===
    private static final List<ResourceKey<DamageType>> MAGIC_DAMAGE_TYPES = List.of(
            // 新生魔艺 (ars_nouveau)
            key("ars_nouveau", "generic_spell_damage"),
            key("ars_nouveau", "windshear"),
            key("ars_nouveau", "cold_snap"),
            key("ars_nouveau", "flare"),
            key("ars_nouveau", "crush"),
            // 诡厄巫法 (goety)
            key("goety", "summon"),
            key("goety", "shock"),
            key("goety", "direct_shock"),
            key("goety", "indirect_shock"),
            key("goety", "lightning"),
            key("goety", "direct_freeze"),
            key("goety", "indirect_freeze"),
            key("goety", "ice_spike"),
            key("goety", "drench"),
            key("goety", "direct_drench"),
            key("goety", "indirect_drench"),
            key("goety", "wind_blast"),
            key("goety", "ice_bouquet"),
            key("goety", "hellfire"),
            key("goety", "indirect_hellfire"),
            key("goety", "magic_fire"),
            key("goety", "magic_fireball"),
            key("goety", "no_owner_magic_fireball"),
            key("goety", "fire_breath"),
            key("goety", "frost_breath"),
            key("goety", "bubble_stream"),
            key("goety", "magic_bolt"),
            // Iron 的法术与魔法书 (irons_spellbooks)
            key("irons_spellbooks", "fire_magic"),
            key("irons_spellbooks", "ice_magic"),
            key("irons_spellbooks", "lightning_magic"),
            key("irons_spellbooks", "holy_magic"),
            key("irons_spellbooks", "ender_magic"),
            key("irons_spellbooks", "blood_magic"),
            key("irons_spellbooks", "evocation_magic"),
            key("irons_spellbooks", "eldritch_magic"),
            key("irons_spellbooks", "nature_magic"),
            key("irons_spellbooks", "cauldron"),
            key("irons_spellbooks", "heartstop"),
            key("irons_spellbooks", "dragon_breath_pool"),
            key("irons_spellbooks", "fire_field"),
            key("irons_spellbooks", "poison_cloud"));

    // === 作用域 matcher 注册表(附属模组可注册自定义判定) ===
    @FunctionalInterface
    public interface SpellDamageMatcher {
        boolean matches(DamageSource source, Entity direct);
    }

    private static final List<SpellDamageMatcher> MATCHERS = new ArrayList<>();

    // === 加成修饰器注册表 ===
    private static final List<SpellDamageModifier> MODIFIERS = new ArrayList<>();

    private SpellDamageRegistry() {
    }

    public static void registerMatcher(SpellDamageMatcher matcher) {
        MATCHERS.add(matcher);
    }

    public static void registerModifier(SpellDamageModifier modifier) {
        MODIFIERS.add(modifier);
    }

    public static List<SpellDamageModifier> modifiers() {
        return List.copyOf(MODIFIERS);
    }

    /**
     * 作用域判定:先按公共配置 {@code allow_firearm_damage} 决定是否排除军火类(保险),
     * 再按白名单 matcher 依次判定。
     *
     * <p>军火类排除默认生效({@code allow_firearm_damage = false}),即与既有行为一致;
     * 仅当显式开启该配置时,弹丸/伤害类型关键词命中的军火类伤害才会继续走白名单判定。
     */
    public static boolean isSpellDamage(DamageSource source, Entity direct) {
        if (!GameplayConstants.ALLOW_FIREARM_DAMAGE && isFirearmDamage(source)) return false;
        for (SpellDamageMatcher matcher : MATCHERS) {
            if (matcher.matches(source, direct)) return true;
        }
        return false;
    }

    /**
     * 本次法伤是否来自「活体书页」的**即时命中**。
     *
     * <p>2026-09-19 用户裁决「活体书页不应该有持续效果,应转换为及时伤害,移除所有原本效果器」之后,该牌
     * **不再给玩家施加任何效果** ⇒ 原先靠 `hasEffect(ModEffects.LIVING_PAGE)` 判定的「使用了伤害效果牌」
     * 改由**伤害类型本身**识别(`astral_dice:card_spell`)。这样忍术飞镖/贯穿之铳对书页自身的命中照旧生效
     * (它们本就是"伤害效果牌生效期间的加成"),又不引入任何玩家可见状态或额外字段。
     */
    private static boolean isLivingPageImpact(SpellDamageContext ctx) {
        return ctx != null && ctx.source != null && ctx.source.is(ModDamageTypes.CARD_SPELL);
    }

    /**
     * 立牌「效果牌伤害加成」的**静默安全上限**(2026-09-19 用户要求「增加忍者立牌和调查员立牌的
     * 效果牌伤害加成上限,设置为 120,**不说明不提示**」)。
     *
     * <p>只做**数值夹取**,不新增任何 UI 文案 / actionbar 提示 / tooltip 说明 / lang 键:超过该值后
     * 继续按 {@value #SIGN_DAMAGE_BONUS_CAP} 计入,玩家侧只看到数值不再增长。
     * 覆盖两个来源:忍者立牌 {@code komachi_damage_bonus}(见 {@link #effectCardDamageBonus})与
     * 调查员立牌累计页数 {@code rin_pages}(见 {@link #livingPageBonusPages});
     * 书签筹码的固定加成**不在**此列(用户只点名两个立牌)。
     * ⚠️ tooltip 显示与伤害结算同源(两处都走上面两个方法),故显示值同样是夹取后的值 —— 两处始终一致。
     */
    public static final int SIGN_DAMAGE_BONUS_CAP = 120;

    /** 把**立牌来源**的伤害加成夹到 {@link #SIGN_DAMAGE_BONUS_CAP} 以内(负值按 0;只读、无副作用) */
    public static int cappedSignDamageBonus(int raw) {
        return Math.max(0, Math.min(raw, SIGN_DAMAGE_BONUS_CAP));
    }

    /**
     * 伤害效果牌的统一伤害加成(不含各牌自身基础值):
     * 忍者立牌「效果牌伤害增益」计数(附件 {@code komachi_damage_bonus})
     * + 书签筹码固定 +{@link com.merlinkitsune.astral_dice.item.chip.BookmarkChipItem#DAMAGE_BONUS}(装备时)。
     *
     * <p>伤害计算(本类各修饰器)与 tooltip 显示统一走本方法,保证两处数值一致;
     * 新增"提升伤害效果牌伤害"的筹码/立牌时在本方法内累加,勿散落到各修饰器。
     */
    public static int effectCardDamageBonus(net.minecraft.world.entity.player.Player attacker) {
        if (attacker == null) return 0;
        // 忍者立牌的伤害增益只在**佩戴立牌**时生效(2026-09-15 裁决):死亡保留的累计值不因
        // "立牌死亡掉落、尚未重新装备"而继续加成。故此处统一按佩戴判定,勿在别处直接读原值。
        // 超过 {@link #SIGN_DAMAGE_BONUS_CAP} 的部分不生效(静默夹取,2026-09-19)。
        int komachi = cappedSignDamageBonus(
                com.merlinkitsune.astral_dice.item.sign.KomachiSignItem.isEquipped(attacker)
                        ? ModAttachments.getKomachiDamageBonus(attacker) : 0);
        return komachi
                + com.merlinkitsune.astral_dice.item.chip.BookmarkChipItem.damageBonus(attacker);
    }

    /**
     * 活体书页的**有效**累计页数:只在佩戴调查员立牌时计入(2026-09-15 裁决,与
     * {@link #effectCardDamageBonus} 同一口径)。伤害结算与 tooltip 显示统一走本方法,
     * 禁止在别处直接读 {@code rin_pages} 原值来做加成或显示加成。
     *
     * <p>超过 {@link #SIGN_DAMAGE_BONUS_CAP} 的部分不生效(静默夹取,2026-09-19)。
     */
    public static int livingPageBonusPages(net.minecraft.world.entity.player.Player attacker) {
        if (attacker == null) return 0;
        return cappedSignDamageBonus(com.merlinkitsune.astral_dice.item.sign.RinSignItem.isEquipped(attacker)
                ? ModAttachments.getRinPages(attacker) : 0);
    }

    /**
     * 「活体书页」命中伤害 = {@link LivingPageImpact#BASE_DAMAGE 基础 2}
     * + {@link #livingPageBonusPages 调查员已用页数}(仅佩戴 rin 立牌时计入)
     * + {@link #effectCardDamageBonus 伤害效果牌统一加成}(忍者立牌 + 书签)。
     *
     * <p><b>伤害结算与 tooltip 显示统一走本方法</b>(与 {@code effectCardDamageBonus} 同一口径):
     * 命中结算见 {@link LivingPageImpact#resolve},tooltip 见 {@code event/ModTooltipHandler}。
     * 页数在**命中结算的最后**才 +1(2026-09-19 用户裁决「先执行伤害,后施加标记,最后使活体书页伤害+1」)
     * ⇒ <b>本值不含本次使用</b>:首张命中 = 2,tooltip 与实际命中数值一致。
     */
    public static int livingPageImpactDamage(net.minecraft.world.entity.player.Player attacker) {
        if (attacker == null) return LivingPageImpact.BASE_DAMAGE;
        return LivingPageImpact.BASE_DAMAGE + livingPageBonusPages(attacker) + effectCardDamageBonus(attacker);
    }

    private static ResourceKey<DamageType> key(String namespace, String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    static {
        // === 内置 matcher ===
        // 1. 原生弹射物:弓/弩箭矢、三叉戟、投掷物/投掷药水
        registerMatcher((source, direct) -> direct instanceof AbstractArrow || direct instanceof ThrowableProjectile);
        // 2. 原版魔法
        registerMatcher((source, direct) -> {
            String msgId = source.getMsgId();
            return "magic".equals(msgId) || "indirectMagic".equals(msgId);
        });
        // 3. 魔法模组精确伤害类型
        registerMatcher((source, direct) -> {
            for (ResourceKey<DamageType> type : MAGIC_DAMAGE_TYPES) {
                if (source.is(type)) return true;
            }
            return false;
        });
        // 4. 本模组「活体书页」命中伤害(astral_dice:card_spell,2026-09-25 活体书页重写):
        //    该伤害必须登记为法伤,才能原样跑完整修饰器链(见 LivingPageImpact 的结算说明)。
        //    注意:这里**不**依赖任何"效果存在"的开关 —— 书页命中本身就是一次法伤事件。
        registerMatcher((source, direct) -> source.is(ModDamageTypes.CARD_SPELL));

        // === 内置修饰器 ===
        // (活体书页原有的「效果期间远程/魔法伤害 +2+页数」修饰器已于 2026-09-25 删除:
        //  该牌已改为「飞向目标并必定命中」的打击牌,伤害在命中时经 LivingPageImpact 登记为
        //  astral_dice:card_spell 结算,不再附着于其它远程/魔法伤害之上。)
        // 对怪激光:远程和魔法伤害 +4(+忍者立牌效果牌伤害增益)
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return ctx.attacker.hasEffect(ModEffects.MONSTER_LASER);
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + 4 + effectCardDamageBonus(ctx.attacker);
            }
        });
        // 对怪板砖:远程和魔法伤害 +6(+忍者立牌效果牌伤害增益)
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return ctx.attacker.hasEffect(ModEffects.MONSTER_BRICK);
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + 6 + effectCardDamageBonus(ctx.attacker);
            }
        });
        // 轨道炮:远程和魔法伤害 +8(+忍者立牌效果牌伤害增益)
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return ctx.attacker.hasEffect(ModEffects.ORBITAL_STRIKE);
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + 8 + effectCardDamageBonus(ctx.attacker);
            }
        });
        // 定向爆破:远程和魔法伤害 +5(+忍者立牌效果牌伤害增益),并对目标周围 6 格敌对目标造成同样伤害
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return ctx.attacker.hasEffect(ModEffects.DIRECTIONAL_BLAST);
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + 5 + effectCardDamageBonus(ctx.attacker);
            }

            @Override
            public void onHit(SpellDamageContext ctx, double bonus) {
                if (bonus <= 0) return;
                net.minecraft.world.phys.AABB aabb = ctx.target.getBoundingBox().inflate(6);
                var nearby = ctx.target.level().getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class, aabb,
                        e -> HostileTargets.isHostile(ctx.attacker, e)
                                && e != ctx.target && e.isAlive());
                var blastSource = com.merlinkitsune.astral_dice.damage.ModDamageTypes
                        .trueDamage(ctx.target.level(), ctx.attacker);   // 真伤:效果牌范围波及伤害同样无视护甲值/盔甲韧性
                // AOE 造成与主目标「同样的伤害」:基础 5 + 效果牌伤害加成(与主目标一致,不吃忍者/书签加成之外的其它修饰器)
                int aoeDamage = (int) Math.max(1.0, 5 + effectCardDamageBonus(ctx.attacker));
                // AOE 波及伤害不进入骰战结算(见 DiceCombatEvents.aoeProcessing)
                DiceCombatEvents.aoeProcessing = true;
                try {
                    for (var e : nearby) {
                        e.hurt(blastSource, aoeDamage);
                        sendAoeDamageNumber(e, aoeDamage, 0x7CFC00);
                    }
                } finally {
                    DiceCombatEvents.aoeProcessing = false;
                }
            }
        });
        // 忍术飞镖:只要佩戴者对目标造成**远程/魔法伤害**即获得「目标标记层数」的伤害加成。
        // (2026-09-24 用户裁决「移除『已使用伤害效果牌』前提,只要是对目标造成远程和魔法伤害就应该生效」)
        //
        // ⚠️ 本修饰器**只在法伤链内被求值** —— DamageEffectCardHandler 已先用
        // SpellDamageRegistry.isSpellDamage 筛过作用域,故「进入本方法」本身就等价于
        // 「本次是远程/魔法伤害」⇒ 判据退化为**仅检查是否佩戴筹码**。
        // 旧实现额外要求「已使用伤害类效果牌」(激光/板砖/轨道炮/爆破四个效果在身,或本次是活体书页法伤),
        // 导致箭矢 / 投掷物 / 联动模组法术等**未先打效果牌的法伤**全部吃不到加成。
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return ctx.hasCurio(ModItems.NINJA_STAR_CHIP.get());
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + MarkManager.getLevel(ctx.target);
            }
        });
        // 贯穿之铳:伤害效果牌生效时(同上,含活体书页的即时法伤),对敌对目标远程/魔法伤害额外增加目标防御力点数
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                if (!ctx.hasCurio(ModItems.PIERCING_GUN.get())) return false;
                if (!HostileTargets.isHostile(ctx.attacker, ctx.target)) return false;
                return isLivingPageImpact(ctx)
                        || ctx.attacker.hasEffect(ModEffects.MONSTER_LASER)
                        || ctx.attacker.hasEffect(ModEffects.MONSTER_BRICK)
                        || ctx.attacker.hasEffect(ModEffects.ORBITAL_STRIKE)
                        || ctx.attacker.hasEffect(ModEffects.DIRECTIONAL_BLAST);
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + PiercingGunChipItem.getTargetDefense(ctx.target);
            }
        });
        // 标记喷罐:对目标造成远程或魔法伤害后,使目标获得一层"标记"
        registerModifier(new SpellDamageModifier() {
            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus;
            }

            @Override
            public void onHit(SpellDamageContext ctx, double bonus) {
                if (ctx.hasCurio(ModItems.MARKER_SPRAYER_CHIP.get())) {
                    MarkManager.apply(ctx.target);
                }
            }
        });
        // 魔法箭袋:使用过效果牌并对带标记目标造成法伤 → 施加一层标记并返还第一张使用的效果牌(每分钟一次)
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                if (!ctx.hasCurio(ModItems.MAGIC_QUIVER.get())) return false;
                if (!ModAttachments.getMagicQuiverTracking(ctx.attacker)) return false;
                if (ctx.attacker.level().getGameTime() < ModAttachments.getMagicQuiverCooldownEnd(ctx.attacker)) {
                    return false;
                }
                return MarkManager.getLevel(ctx.target) > 0;
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus;
            }

            @Override
            public void onHit(SpellDamageContext ctx, double bonus) {
                com.merlinkitsune.astral_dice.item.chip.MagicQuiverChipItem.tryProc(ctx);
            }
        });
        // 紫晶骰子:远程/魔法攻击命中时也触发战斗骰(1-6)并追加骰点伤害;不触发骰神赐福、不消耗卡牌耐久
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return ctx.hasCurio(ModItems.AMETHYST_DICE.get());
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + AmethystDiceHandler.rollD6(ctx.attacker);
            }
        });
        // 电击手套:武装期间(使用伤害效果牌时消耗 4 层充能置位),本次远程/魔法伤害同时命中目标 3 格内的
        // 其他敌对目标(每个效果牌周期仅触发一次,触发后解除武装)
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem.isAoeArmed(ctx.attacker);
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus;
            }

            @Override
            public void onHit(SpellDamageContext ctx, double bonus) {
                float total = ctx.event.getNewDamage();
                if (total <= 0) return;
                net.minecraft.world.phys.AABB aabb = ctx.target.getBoundingBox()
                        .inflate(com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem.AOE_RADIUS);
                var nearby = ctx.target.level().getEntitiesOfClass(LivingEntity.class, aabb,
                        e -> HostileTargets.isHostile(ctx.attacker, e) && e != ctx.target && e.isAlive());
                var source = com.merlinkitsune.astral_dice.damage.ModDamageTypes
                        .trueDamage(ctx.target.level(), ctx.attacker);   // 真伤:效果牌范围波及伤害同样无视护甲值/盔甲韧性
                // AOE 波及伤害不进入骰战结算(见 DiceCombatEvents.aoeProcessing)
                DiceCombatEvents.aoeProcessing = true;
                try {
                    for (LivingEntity e : nearby) {
                        e.hurt(source, total);
                        sendAoeDamageNumber(e, (int) total, 0x00E5FF);
                    }
                } finally {
                    DiceCombatEvents.aoeProcessing = false;
                }
                // 每周期仅触发一次:触发后解除武装
                com.merlinkitsune.astral_dice.item.chip.ElectricGloveChipItem.disarmAoe(ctx.attacker);
            }
        });
    }

    // 溅射/范围伤害跳数字(颜色由调用方指定;定向爆破使用效果牌绿色)
    private static void sendAoeDamageNumber(LivingEntity target, int damage, int color) {
        com.merlinkitsune.astral_dice.network.DamageNumberPayload.send(target, damage, color);
    }

    // 枪械/火炮类远程弹丸判定(保险):伤害类型与弹丸类名关键词识别
    private static boolean isFirearmDamage(DamageSource source) {
        String msgId = source.getMsgId().toLowerCase(Locale.ROOT);
        if (msgId.contains("bullet") || msgId.contains("gun") || msgId.contains("firearm")
                || msgId.contains("cannon") || msgId.contains("shell") || msgId.contains("missile")) {
            return true;
        }
        Entity direct = source.getDirectEntity();
        if (direct != null) {
            String name = direct.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (name.contains("bullet") || name.contains("shell") || name.contains("cannon") || name.contains("gun")) {
                return true;
            }
        }
        return false;
    }
}
