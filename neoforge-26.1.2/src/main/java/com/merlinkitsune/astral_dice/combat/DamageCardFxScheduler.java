package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.audio.ModSounds;
import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.particle.GlowingDustOptions;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 「伤害效果牌」四种 FX 的**唯一执行器**（2026-10-04 用户裁决）。
 *
 * <h2>为什么需要它</h2>
 * 对怪激光 / 对怪板砖 / 轨道炮 / 定向爆破 四张牌由「使用后获得 1:00 的远程/魔法增伤」
 * 改为「**目标选择器** + 对指定目标直接造成法术伤害」⇒ 伤害**不再是附着态**，
 * 而是一次**延迟到 FX 命中时点**才落地的独立结算。本类统一承担
 * 「起手延迟 → FX 动画 → 命中瞬间结算音效 / 粒子 / 伤害」三段。
 *
 * <h2>四种 FX（用户原文）</h2>
 * <ul>
 *   <li><b>LASER</b>（对怪激光）：释放后延迟 {@value #DELAY_TICKS} tick（0.5 秒），在目标头顶生成
 *       **红色激光线（视觉无限高）**并射中目标，光束持续 {@value #BEAM_TICKS} tick（1 秒）；</li>
 *   <li><b>BRICK</b>（对怪板砖）：释放后向目标**投掷一块大号砖头**（原版砖块贴图，体积明显大于砖头物品），
 *       飞行速度 {@value #BRICK_SPEED} 格/tick（**快于活体书页的 2.4**），命中后产生碎裂粒子；</li>
 *   <li><b>ORBITAL</b>（轨道炮）：延迟 0.5 秒后在目标头顶生成**金色信标光束（视觉无限高）**并射中目标，
 *       持续 1 秒，命中时产生发光飞溅粒子；</li>
 *   <li><b>BLAST</b>（定向爆破）：延迟 0.5 秒后在目标处爆开，**对选中目标及其 {@value #BLAST_RADIUS} 格内的
 *       其他目标同时造成伤害**。</li>
 * </ul>
 *
 * <h2>伤害口径（必须遵守）</h2>
 * 伤害 = 各牌**固定基础值**（{@value #LASER_DAMAGE} / {@value #BRICK_DAMAGE} / {@value #ORBITAL_DAMAGE} /
 * {@value #BLAST_DAMAGE}）+ {@link SpellDamageRegistry#effectCardDamageBonus} 的「效果牌伤害加成」
 * （忍者立牌「效果牌伤害增益」+ 书签筹码）。伤害类型 = {@link ModDamageTypes#cardSpell}（**法术伤害**，
 * 与活体书页同一条链：仍会被忍术飞镖 / 贯穿之铳 / 标记等法伤修饰器放大）。
 * ⚠️ 四张牌**自身**原有的「附在其它远程/魔法伤害上的 +N 增伤」已按用户裁决移除（修饰器骨架保留但不生效，
 * 见 {@link SpellDamageRegistry}）。
 *
 * <h2>视觉实现</h2>
 * 光束 / 爆裂 / 拖尾全部用粒子（本模组既有口径：{@link GlowingDustOptions} 全亮发光尘 + 原版粒子），
 * **不用自定义实体**；仅「大号砖头」用原版 {@link Display.BlockDisplay}（砖块贴图 + 放大），
 * 经 NBT 装配 —— 因为 {@code Display} 的 setter 在三个 MC 版本里都是 private，走 NBT 可**零 AT/AW**。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class DamageCardFxScheduler {

    /** FX 种类（与四张伤害效果牌一一对应）。 */
    public enum Kind {
        /** 对怪激光：红色激光线（延迟 0.5s → 光束 1s） */
        LASER,
        /** 对怪板砖：投掷大号砖头（飞行快于活体书页） */
        BRICK,
        /** 轨道炮：金色信标光束（延迟 0.5s → 光束 1s） */
        ORBITAL,
        /** 定向爆破：目标处爆开 + 6 格 AOE */
        BLAST
    }

    // ── 伤害（固定基础值 + effectCardDamageBonus） ──────────────────────────────
    public static final int LASER_DAMAGE = 4;
    public static final int BRICK_DAMAGE = 6;
    public static final int ORBITAL_DAMAGE = 8;
    public static final int BLAST_DAMAGE = 5;

    // ── 时序 ────────────────────────────────────────────────────────────────
    /** 激光 / 信标 / 爆破的起手延迟（tick）= 0.5 秒。 */
    public static final int DELAY_TICKS = 10;
    /** 光束持续（tick）= 1 秒。 */
    public static final int BEAM_TICKS = 20;
    /** 砖头飞行速度（格/tick）。⚠️ 必须**大于**活体书页的 {@code LivingPageFlightScheduler.SPEED_BLOCKS_PER_TICK = 2.4}。 */
    public static final double BRICK_SPEED = 3.2D;
    /** 定向爆破的波及半径（格），与改造前的 AOE 口径一致。 */
    public static final double BLAST_RADIUS = 6.0D;

    /** 光束粒子柱高度（格）—— 视觉上「无限高」。 */
    private static final double BEAM_HEIGHT = 96.0D;
    /** 光束每 tick 粒子数。 */
    private static final int BEAM_PARTICLES = 84;
    /** 砖头投掷起点：从眼睛沿视线前移（避免贴脸穿模）。 */
    private static final double BRICK_LAUNCH_OFFSET = 0.8D;
    /** 砖头体积放大倍数（原版砖块已经远大于「砖头」物品，再放大一档）。 */
    private static final float BRICK_SCALE = 1.35F;
    /** 命中判定吸附距离（格）。 */
    private static final double SNAP_DISTANCE = 0.4D;
    /** 超龄保护：维度卸载等极端情形下丢弃滞留条目。 */
    private static final long MAX_AGE_TICKS = 400L;

    private static final int LASER_COLOR = 0xFF2626;
    private static final int BEACON_COLOR = 0xFFCF4A;
    private static final int BRICK_TRAIL_COLOR = 0xB0693F;
    private static final int BLAST_COLOR = 0xFF8C3A;

    private static final Logger LOGGER = LoggerFactory.getLogger(DamageCardFxScheduler.class);

    private static final List<Pending> PENDING = new ArrayList<>();

    private static final class Pending {
        private final Kind kind;
        private final ServerLevel level;
        private final ServerPlayer caster;
        private final LivingEntity target;
        private final long startTick;
        private final int flightTicks;
        private Vec3 pos;
        private int elapsed;
        private boolean hit;
        private Display.BlockDisplay display;
        /** 本次这一条 FX 是否为「电击手套已付费」的那一次伤害 ⇒ 只有它的**主目标**命中可扩散(见 markLatestGlovePaid)。 */
        private boolean glovePaid;

        private Pending(Kind kind, ServerLevel level, ServerPlayer caster, LivingEntity target,
                        Vec3 pos, int flightTicks) {
            this.kind = kind;
            this.level = level;
            this.caster = caster;
            this.target = target;
            this.pos = pos;
            this.flightTicks = flightTicks;
            this.startTick = level.getGameTime();
        }
    }

    private DamageCardFxScheduler() {
    }

    /**
     * 把「本次可扩散」认领给该玩家**这一张牌**最近一次投出、且尚未命中的伤害载体。
     *
     * <p>由 {@code item/chip/ElectricGloveChipItem#onEffectCardUsed} 在 {@code applyEffect} **之后**调用
     * (那时本次 FX 已经登记)⇒ 认领的必然是**本次这张牌自己**那一条;按 {@link Kind} 配对 ⇒ 不会误认领到
     * 同一玩家另一张仍在飞行中的牌(例:板砖 0.5s 飞行窗口内又打出一张激光)。
     *
     * @return 是否认领成功(false = 本次没投出可用载体 ⇒ 调用方不应扣充能)
     */
    public static boolean markLatestGlovePaid(ServerPlayer caster, ItemStack cardStack) {
        Kind kind = kindOf(cardStack);
        if (kind == null || caster == null) return false;
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending p = PENDING.get(i);
            if (p.caster == caster && p.kind == kind && !p.glovePaid) {
                p.glovePaid = true;
                return true;
            }
        }
        return false;
    }

    /** 四张伤害效果牌 → {@link Kind};非本调度器的牌返回 {@code null}。 */
    private static Kind kindOf(ItemStack cardStack) {
        if (cardStack == null || cardStack.isEmpty()) return null;
        if (cardStack.is(com.merlinkitsune.astral_dice.item.ModItems.MONSTER_LASER_CARD.get())) return Kind.LASER;
        if (cardStack.is(com.merlinkitsune.astral_dice.item.ModItems.MONSTER_BRICK_CARD.get())) return Kind.BRICK;
        if (cardStack.is(com.merlinkitsune.astral_dice.item.ModItems.ORBITAL_STRIKE_CARD.get())) return Kind.ORBITAL;
        if (cardStack.is(com.merlinkitsune.astral_dice.item.ModItems.DIRECTIONAL_BLAST_CARD.get())) return Kind.BLAST;
        return null;
    }

    /**
     * 释放一张伤害效果牌：起手音效 + 登记本次 FX（伤害在命中时点才落地）。
     *
     * @param kind   牌种
     * @param level  施放维度（服务端）
     * @param caster 施放者
     * @param target 选中的目标
     * @return 是否成功登记
     */
    public static boolean launch(Kind kind, ServerLevel level, ServerPlayer caster, LivingEntity target) {
        if (kind == null || level == null || caster == null || target == null) return false;
        if (!target.isAlive() || target.level() != level) return false;

        // 释放音效（四张牌共用；取自 sound/effect_card_use1.wav）
        SoundPlayback.playAt(level, caster.getX(), caster.getY(), caster.getZ(),
                ModSounds.DAMAGE_EFFECT_CARD_CAST.get());

        if (kind == Kind.BRICK) {
            Vec3 origin = caster.getEyePosition().add(caster.getLookAngle().scale(BRICK_LAUNCH_OFFSET));
            Pending pending = new Pending(kind, level, caster, target, origin,
                    (int) Math.max(1L, (long) Math.ceil(origin.distanceTo(target.getBoundingBox().getCenter())
                            / BRICK_SPEED)));
            pending.display = spawnBrick(level, origin);
            PENDING.add(pending);
            return true;
        }

        PENDING.add(new Pending(kind, level, caster, target, target.getBoundingBox().getCenter(), 0));
        return true;
    }

    /** 当前维度待结算的 FX 条目数（测试 / 调试用）。 */
    public static int pendingCount(ServerLevel level) {
        if (level == null) return 0;
        int count = 0;
        for (Pending p : PENDING) {
            if (p.level == level) count++;
        }
        return count;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        process(event.getServer());
    }

    private static void process(MinecraftServer server) {
        if (server == null || PENDING.isEmpty()) return;
        List<Pending> due = new ArrayList<>();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            due.add(it.next());
            it.remove();
        }
        for (Pending p : due) {
            try {
                advance(p);
            } catch (Exception ex) {
                discardDisplay(p);
                LOGGER.warn("[Astral Dice] 伤害效果牌 FX 结算失败，已丢弃本次", ex);
            }
        }
    }

    private static void advance(Pending p) {
        if (p.level.getGameTime() - p.startTick > MAX_AGE_TICKS) {
            discardDisplay(p);
            return;
        }
        if (!targetUsable(p)) {
            discardDisplay(p);
            return;
        }
        switch (p.kind) {
            case LASER -> advanceBeam(p, LASER_COLOR);
            case ORBITAL -> advanceBeam(p, BEACON_COLOR);
            case BLAST -> advanceBlast(p);
            case BRICK -> advanceBrick(p);
        }
    }

    private static boolean targetUsable(Pending p) {
        return p.target != null && !p.target.isRemoved() && p.target.isAlive()
                && p.target.level() == p.level;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  光束类（对怪激光 / 轨道炮）：延迟 0.5s → 命中 → 光束持续 1s
    // ══════════════════════════════════════════════════════════════════════════

    private static void advanceBeam(Pending p, int color) {
        if (p.elapsed < DELAY_TICKS) {
            p.elapsed++;
            PENDING.add(p);
            return;
        }
        if (!p.hit) {
            p.hit = true;
            resolveBeamHit(p, color);
        }
        drawBeam(p, color);
        p.elapsed++;
        if (p.elapsed < DELAY_TICKS + BEAM_TICKS) {
            PENDING.add(p);
        }
    }

    /** 命中时点：命中音效 + 命中粒子 + 伤害落地。 */
    private static void resolveBeamHit(Pending p, int color) {
        Vec3 center = p.target.getBoundingBox().getCenter();
        boolean orbital = color == BEACON_COLOR;
        p.level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF), center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        // 命中爆点
        p.level.sendParticles(dust(color, 1.1F), center.x, center.y + 0.4D, center.z,
                26, 0.35D, 0.45D, 0.35D, 0.05D);
        if (orbital) {
            // 「发光飞溅粒子」：END_ROD 全亮飞溅 + 金色尘
            p.level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 0.4D, center.z,
                    34, 0.55D, 0.55D, 0.55D, 0.16D);
            p.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, center.x, center.y + 0.5D, center.z,
                    18, 0.5D, 0.5D, 0.5D, 0.25D);
        } else {
            p.level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 0.4D, center.z,
                    14, 0.35D, 0.35D, 0.35D, 0.06D);
        }
        SoundPlayback.playAt(p.level, center.x, center.y, center.z,
                orbital ? ModSounds.ORBITAL_STRIKE_HIT.get() : ModSounds.MONSTER_LASER_HIT.get());
        applySpellDamage(p.level, p.caster, p.target,
                orbital ? ORBITAL_DAMAGE : LASER_DAMAGE, p.glovePaid);
    }

    /**
     * 绘制「从目标头顶向上」的光束（视觉无限高）。
     *
     * <p>每 tick **只发一次** {@code sendParticles}：把 {@value #BEAM_PARTICLES} 枚粒子按
     * {@code [head, head + }{@value #BEAM_HEIGHT}{@code ]} 的竖直区间均匀铺开（dx/dz 给一点点厚度），
     * 比逐格发包便宜得多，观感同样是「一根光柱」。
     */
    private static void drawBeam(Pending p, int color) {
        double headY = p.target.getY() + p.target.getBbHeight();
        Vec3 pos = p.target.position();
        p.level.sendParticles(dust(color, 0.75F),
                pos.x, headY + BEAM_HEIGHT * 0.5D, pos.z,
                BEAM_PARTICLES, 0.14D, BEAM_HEIGHT * 0.5D, 0.14D, 0.0D);
        // 贴近头顶的一段加一条更亮的核心，强调「射中」
        p.level.sendParticles(dust(0xFFFFFF, 0.45F),
                pos.x, headY + 1.5D, pos.z, 6, 0.10D, 1.4D, 0.10D, 0.0D);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  定向爆破：延迟 0.5s → 目标处爆开 + 6 格 AOE 同时结算
    // ══════════════════════════════════════════════════════════════════════════

    private static void advanceBlast(Pending p) {
        if (p.elapsed < DELAY_TICKS) {
            p.elapsed++;
            PENDING.add(p);
            return;
        }
        resolveBlast(p);
    }

    private static void resolveBlast(Pending p) {
        Vec3 center = p.target.getBoundingBox().getCenter();
        p.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        p.level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF), center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        p.level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + 0.3D, center.z,
                24, 0.7D, 0.5D, 0.7D, 0.05D);
        p.level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.3D, center.z,
                20, 0.8D, 0.5D, 0.8D, 0.08D);
        p.level.sendParticles(dust(BLAST_COLOR, 1.0F), center.x, center.y + 0.3D, center.z,
                30, 0.85D, 0.55D, 0.85D, 0.06D);
        SoundPlayback.playAt(p.level, center.x, center.y, center.z, ModSounds.DIRECTIONAL_BLAST_HIT.get());

        int damage = BLAST_DAMAGE + SpellDamageRegistry.effectCardDamageBonus(p.caster);
        // 2026-10-05 用户裁决「已经吃到一次溅伤的目标应避免重复吃」:本次用量开一个**扩散登记窗口**。
        // ① 的主目标命中会先触发**电击手套的 3 格扩散**,被扩散到的目标登记进来
        // (见 SpellDamageRegistry 的手套修饰器)⇒ ② 的 6 格 AOE **跳过**它们(3 ⊂ 6 ⇒ 交集必非空)。
        // 顺序不能反:手套扩散是在 ① 的 hurt 内部同步跑完的,故「先到」的一定是手套那一次;
        // 交集目标按手套口径结算(= 主目标实际承伤),② 只负责填 3~6 格那一圈。
        DiceCombatEvents.runAsCardSplashResolution(() -> {
            // ① 选中目标(唯一允许电击手套扩散的那一次命中 —— 见 p.glovePaid)
            applySpellDamageAmount(p.level, p.caster, p.target, damage, p.glovePaid);
            // ② 半径内其他可命中目标（用法伤链同一闸门：敌对 ∪ 中立(宠物除外) ∪ 非同队玩家）
            //    e != caster / e != target 显式排除（与改造前的口径逐字一致）
            AABB aabb = p.target.getBoundingBox().inflate(BLAST_RADIUS);
            List<LivingEntity> nearby = p.level.getEntitiesOfClass(LivingEntity.class, aabb,
                    e -> DiceCombatEvents.isBlessingTarget(e, p.caster) && e != p.target && e != p.caster && e.isAlive());
            if (nearby.isEmpty()) return;
            final DamageSource source = ModDamageTypes.cardSpell(p.level, p.caster);
            // ⚠️ 本段刻意**不**走 applySpellDamageAmount ⇒ 不带 runAsGloveSplashInstance:
            //    定向爆破自身的 6 格波及**不是**「电击手套付费的那一次主目标命中」⇒ 不二次触发手套扩散
            //    (2026-10-05 用户裁决:手套只从命中的主目标算一次、与爆破自己的 AOE 各自独立结算,避免递归)。
            DiceCombatEvents.runAsInternalAoe(() -> {
                for (LivingEntity e : nearby) {
                    // 手套 3 格扩散已经结算过的目标,本次 AOE 不再重复结算(先到先得)
                    if (!DiceCombatEvents.markCardSplashHit(e)) continue;
                    DamageNumberAggregator.tag(e, source, DamageNumberAggregator.Group.SPELL);
                    e.hurt(source, (float) damage);
                }
            });
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  对怪板砖：投掷大号砖头 → 命中结算
    // ══════════════════════════════════════════════════════════════════════════

    private static void advanceBrick(Pending p) {
        if (p.display == null || p.display.isRemoved()) {
            return;     // 实体被外部移除（/kill 等）⇒ 本次作废
        }
        Vec3 dest = p.target.getBoundingBox().getCenter();
        Vec3 delta = dest.subtract(p.pos);
        double remaining = delta.length();
        if (remaining <= SNAP_DISTANCE || p.elapsed >= p.flightTicks) {
            p.pos = dest;
            resolveBrickHit(p, dest);
            return;
        }
        double step = Math.min(BRICK_SPEED, remaining);
        Vec3 prev = p.pos;
        p.pos = p.pos.add(delta.normalize().scale(step));
        p.display.setPos(p.pos.x, p.pos.y, p.pos.z);
        // 翻滚感：绕自身旋转
        p.display.setYRot(p.display.getYRot() + 46.0F);
        p.display.setXRot(p.display.getXRot() + 17.0F);
        emitBrickTrail(p, prev, p.pos);
        p.elapsed++;
        PENDING.add(p);
    }

    private static void emitBrickTrail(Pending p, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(length / 0.5D));
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.lerp(to, (double) i / (double) steps);
            p.level.sendParticles(dust(BRICK_TRAIL_COLOR, 0.45F),
                    at.x, at.y, at.z, 2, 0.05D, 0.05D, 0.05D, 0.0D);
        }
        // 尾随的原版砖头贴图碎片（「贴图为原版样式」的延续）
        p.level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.BRICK),
                to.x, to.y, to.z, 2, 0.06D, 0.06D, 0.06D, 0.01D);
    }

    private static void resolveBrickHit(Pending p, Vec3 center) {
        discardDisplay(p);
        // 「碎裂粒子」：原版砖块碎块 + 砖头贴图碎片 + 尘土
        p.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BRICKS.defaultBlockState()),
                center.x, center.y, center.z, 42, 0.35D, 0.35D, 0.35D, 0.12D);
        p.level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.BRICK),
                center.x, center.y, center.z, 22, 0.3D, 0.3D, 0.3D, 0.12D);
        p.level.sendParticles(dust(BRICK_TRAIL_COLOR, 0.9F), center.x, center.y, center.z,
                20, 0.4D, 0.4D, 0.4D, 0.03D);
        SoundPlayback.playAt(p.level, center.x, center.y, center.z, ModSounds.MONSTER_BRICK_HIT.get());
        applySpellDamage(p.level, p.caster, p.target, BRICK_DAMAGE, p.glovePaid);
    }

    /**
     * 生成「大号砖头」= 原版 {@link Display.BlockDisplay}（砖块贴图）+ 放大 {@value #BRICK_SCALE} 倍。
     *
     * <p>⚠️ {@code Display} 系列 setter 在 1.20.1 / 1.21.1 / 26.1.2 **都是 private**（只能由 NBT 读取路径调用）
     * ⇒ 这里用 {@code Entity#load} 从 NBT 装配，**无需任何 AT / AW**。NBT 键名三线一致：
     * {@code block_state}（{@link Display.BlockDisplay#TAG_BLOCK_STATE}）与 {@code transformation}
     * （{@code Transformation.EXTENDED_CODEC}，四个字段都必填）。
     */
    private static Display.BlockDisplay spawnBrick(ServerLevel level, Vec3 origin) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        tag.put(Display.BlockDisplay.TAG_BLOCK_STATE,
                NbtUtils.writeBlockState(Blocks.BRICKS.defaultBlockState()));
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floats(0.0F, 0.0F, 0.0F));
        transformation.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        transformation.put("scale", floats(BRICK_SCALE, BRICK_SCALE, BRICK_SCALE));
        transformation.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        tag.put("transformation", transformation);
        // 剔除盒（width/height 缺省为 0 ⇒ 极薄的剔除盒会被误判为不可见）
        tag.putFloat("width", BRICK_SCALE);
        tag.putFloat("height", BRICK_SCALE);
        tag.putFloat("view_range", 2.0F);
        // 26.1.2：实体 NBT 已改为 ValueInput/ValueOutput ⇒ 用 loadEntityRecursive(CompoundTag, …) 装配
        Entity loaded = EntityType.loadEntityRecursive(tag, level, EntitySpawnReason.COMMAND, e -> e);
        if (!(loaded instanceof Display.BlockDisplay display)) {
            if (loaded != null) loaded.discard();
            return null;
        }
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setPos(origin.x, origin.y, origin.z);
        level.addFreshEntity(display);
        return display;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }

    private static void discardDisplay(Pending p) {
        if (p.display != null && !p.display.isRemoved()) {
            p.display.discard();
        }
        p.display = null;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  伤害结算（法术伤害；⚠️ 不自行发包 —— 组别登记后由 DamageNumberAggregator 统一出数）
    // ══════════════════════════════════════════════════════════════════════════

    private static void applySpellDamage(ServerLevel level, ServerPlayer caster, LivingEntity target, int base,
                                        boolean gloveSplash) {
        applySpellDamageAmount(level, caster, target, base + SpellDamageRegistry.effectCardDamageBonus(caster),
                gloveSplash);
    }

    /**
     * 结算一次法术伤害。
     *
     * @param gloveSplash 该次命中是否为「这张牌本次投出、且已为电击手套付过充能的那一次**主目标**伤害」。
     *                    true ⇒ 用 {@link DiceCombatEvents#runAsGloveSplashInstance} 包住 hurt,
     *                    法伤链里的电击手套修饰器只在这**一个伤害实例**上扩散。
     *                    ⚠️ 定向爆破的 6 格 AOE **不走本方法**(见 {@link #resolveBlast}),
     *                    因此不会二次触发手套 ⇒ 不递归。
     */
    private static void applySpellDamageAmount(ServerLevel level, ServerPlayer caster, LivingEntity target,
                                               int amount, boolean gloveSplash) {
        if (target == null || !target.isAlive()) return;
        final DamageSource source = ModDamageTypes.cardSpell(level, caster);
        final Runnable hit = () -> {
            DamageNumberAggregator.tag(target, source, DamageNumberAggregator.Group.SPELL);
            target.hurt(source, (float) Math.max(1, amount));
        };
        // 两层窗口都改用「保存 / 恢复」版本(原实现是裸 boolean 直写 ⇒ 与外层 AOE 嵌套时会提前关掉
        // 外层窗口,只有 runAsInternalAoe 做了 save/restore):
        //   runAsInternalAoe         = 波及伤害不进骰战结算;
        //   runAsGloveSplashInstance = 本次命中可由电击手套扩散。
        DiceCombatEvents.runAsInternalAoe(() -> {
            if (gloveSplash) {
                DiceCombatEvents.runAsGloveSplashInstance(hit);
            } else {
                hit.run();
            }
        });
    }

    /**
     * ⚠️ 26.1.2 线的 {@link GlowingDustOptions} 构造取 {@code (int rgb, float scale)}；
     * 1.20.1 / 1.21.1 / fabric 三条线取 {@code (Vector3f, float)}，两式不可互抄。
     */
    private static ParticleOptions dust(int rgb, float scale) {
        return new GlowingDustOptions(rgb, scale);
    }
}
