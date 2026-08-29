package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrowableProjectile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.item.chip.MagicQuiverChipItem;
import com.merlinkitsune.astral_dice.item.chip.PiercingGunChipItem;
import com.merlinkitsune.astral_dice.network.ModNetwork.DamageNumberMessage;

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
 *    blood_magic/evocation_magic/eldritch_magic/nature_magic 等。
 * 排除:枪械/炮弹/炸药/火箭等军火类(tacZ、维克斯的武器、卓越前线、气动工艺、机械动力:火炮、通用机械:武器、
 * 沉浸工程等)——其弹丸实体不属于白名单,黑名单关键词仅作"弹丸继承原生类"场景的保险。
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
     * 作用域判定:先排除军火类(保险),再按白名单 matcher 依次判定。
     */
    public static boolean isSpellDamage(DamageSource source, Entity direct) {
        if (isFirearmDamage(source)) return false;
        for (SpellDamageMatcher matcher : MATCHERS) {
            if (matcher.matches(source, direct)) return true;
        }
        return false;
    }

    private static ResourceKey<DamageType> key(String namespace, String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                new ResourceLocation(namespace, path));
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

        // === 内置修饰器 ===
        // 活体书页:对敌对目标远程/魔法伤害增加(基础 2 + 调查员(rin)已使用数量 + 忍者立牌效果牌伤害增益,上限),并施加 1 层标记
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return ctx.attacker.hasEffect(ModEffects.LIVING_BOOK_PAGE.get());
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                int pages = Math.min(ModAttachments.getRinPages(ctx.attacker),
                        GameplayConstants.LIVING_BOOK_PAGE_BONUS_CAP);
                return bonus + 2 + pages + ModAttachments.getKomachiDamageBonus(ctx.attacker);
            }

            @Override
            public void onHit(SpellDamageContext ctx, double bonus) {
                MarkManager.apply(ctx.target);
            }
        });
        // 对怪激光:远程和魔法伤害 +4(+忍者立牌效果牌伤害增益)
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return ctx.attacker.hasEffect(ModEffects.MONSTER_LASER.get());
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + 4 + ModAttachments.getKomachiDamageBonus(ctx.attacker);
            }
        });
        // 对怪板砖:远程和魔法伤害 +6(+忍者立牌效果牌伤害增益)
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return ctx.attacker.hasEffect(ModEffects.MONSTER_BRICK.get());
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + 6 + ModAttachments.getKomachiDamageBonus(ctx.attacker);
            }
        });
        // 轨道炮:远程和魔法伤害 +8(+忍者立牌效果牌伤害增益)
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return ctx.attacker.hasEffect(ModEffects.ORBITAL_STRIKE.get());
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + 8 + ModAttachments.getKomachiDamageBonus(ctx.attacker);
            }
        });
        // 定向爆破:远程和魔法伤害 +5(+忍者立牌效果牌伤害增益),并对目标周围 6 格敌对目标造成同样伤害
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                return ctx.attacker.hasEffect(ModEffects.DIRECTIONAL_BLAST.get());
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + 5 + ModAttachments.getKomachiDamageBonus(ctx.attacker);
            }

            @Override
            public void onHit(SpellDamageContext ctx, double bonus) {
                if (bonus <= 0) return;
                net.minecraft.world.phys.AABB aabb = ctx.target.getBoundingBox().inflate(6);
                var nearby = ctx.target.level().getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class, aabb,
                        e -> e instanceof net.minecraft.world.entity.monster.Enemy
                                && e != ctx.target && e.isAlive());
                var blastSource = com.merlinkitsune.astral_dice.damage.ModDamageTypes
                        .diceDamage(ctx.target.level(), ctx.attacker);
                for (var e : nearby) {
                    e.hurt(blastSource, 5);
                    sendAoeDamageNumber(e, 5, 0x7CFC00);
                }
            }
        });
        // 忍术飞镖:已使用伤害效果牌(任一效果生效)且造成远程/魔法伤害时,获得目标标记层数的伤害加成
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                if (!ctx.hasCurio(ModItems.NINJA_STAR_CHIP.get())) return false;
                return ctx.attacker.hasEffect(ModEffects.LIVING_BOOK_PAGE.get())
                        || ctx.attacker.hasEffect(ModEffects.MONSTER_LASER.get())
                        || ctx.attacker.hasEffect(ModEffects.MONSTER_BRICK.get())
                        || ctx.attacker.hasEffect(ModEffects.ORBITAL_STRIKE.get())
                        || ctx.attacker.hasEffect(ModEffects.DIRECTIONAL_BLAST.get());
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + MarkManager.getLevel(ctx.target);
            }
        });
        // 贯穿之铳:伤害效果牌生效时,对敌对目标远程/魔法伤害额外增加目标防御力点数
        registerModifier(new SpellDamageModifier() {
            @Override
            public boolean isActive(SpellDamageContext ctx) {
                if (!ctx.hasCurio(ModItems.PIERCING_GUN.get())) return false;
                if (!(ctx.target instanceof Enemy)) return false;
                return ctx.attacker.hasEffect(ModEffects.LIVING_BOOK_PAGE.get())
                        || ctx.attacker.hasEffect(ModEffects.MONSTER_LASER.get())
                        || ctx.attacker.hasEffect(ModEffects.MONSTER_BRICK.get())
                        || ctx.attacker.hasEffect(ModEffects.ORBITAL_STRIKE.get())
                        || ctx.attacker.hasEffect(ModEffects.DIRECTIONAL_BLAST.get());
            }

            @Override
            public double apply(SpellDamageContext ctx, double bonus) {
                return bonus + PiercingGunChipItem.getTargetDefense(ctx.target);
            }
        });
        // 标记喷灌:对目标造成远程或魔法伤害后,使目标获得一层"标记"
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
    }

    // 溅射/范围伤害跳数字(颜色由调用方指定;定向爆破使用效果牌绿色)
    private static void sendAoeDamageNumber(LivingEntity target, int damage, int color) {
        if (target.level().isClientSide()) return;
        var packet = new ModNetwork.DamageNumberMessage(target.getId(), damage, color);
        com.merlinkitsune.astral_dice.network.ModNetwork.sendToPlayersTrackingEntity(target, packet);
        if (target instanceof net.minecraft.server.level.ServerPlayer serverTarget) {
            com.merlinkitsune.astral_dice.network.ModNetwork.sendToPlayer(serverTarget, packet);
        }
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
