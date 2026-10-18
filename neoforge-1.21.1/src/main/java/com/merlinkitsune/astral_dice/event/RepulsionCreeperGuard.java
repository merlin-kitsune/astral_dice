package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.entity.RepulsionEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * 强化拒止 · 苦力怕「禁止自爆」兜底（2026-10-18 用户追加规则）。
 *
 * <p><b>为什么需要事件层</b>：{@code RepulsionEntity#suppressTrackedCreeperFuses()} 每 tick 把被吸附
 * 苦力怕的 {@code swellDir} 压成 -1，但那只对**自然膨胀**有效。一旦苦力怕被**打火石 / 火焰**点燃，
 * 原版 {@code Creeper#tick} 会在读值之前**每 tick 无条件** {@code setSwellDir(1)}
 * （{@code if (this.isIgnited()) this.setSwellDir(1);}）⇒ 压制必然被覆盖、引信照烧到底。
 * 因此必须再叠一层「爆炸开始」拦截。
 *
 * <p><b>判据（只吃被吸附的苦力怕）</b>：
 * <ol>
 *   <li>爆炸的直接来源实体或间接来源实体是 {@code Creeper} —— {@code Creeper#explodeCreeper()} 调用
 *       {@code level().explode(this, ...)}，source = 苦力怕自身，故
 *       {@code Explosion#getDirectSourceEntity()} 即命中；{@code getIndirectSourceEntity()} 只是兜底
 *       （两线同签名，均返回 {@code LivingEntity}/{@code Entity}）；</li>
 *   <li>该苦力怕 {@link #LINK_RADIUS} 格内存在一座 {@link RepulsionEntity}，且它的
 *       {@link RepulsionEntity#isTracking(java.util.UUID)} 对该苦力怕的 UUID 返回 true。</li>
 * </ol>
 * 命中 ⇒ {@code event.setCanceled(true)} + {@code creeper.setSwellDir(-1)}
 * + 在苦力怕处播 {@code SoundEvents.FIRE_EXTINGUISH}（引信被掐灭的听觉反馈）。
 *
 * <p><b>取消后的真实行为（已核两线 {@code Level#explode} / {@code Creeper#explodeCreeper()} 源码）</b>：
 * 取消发生在 {@code Level#explode} 里 {@code new Explosion(...)} 之后、{@code explosion.explode()}
 * **之前**（NeoForge {@code EventHooks.onExplosionStart} / Forge {@code ForgeEventFactory.onExplosionStart}
 * 返回 true 时直接 {@code return explosion;}）⇒ 不炸方块、不造成伤害、无爆炸粒子与音效；
 * 但 {@code explodeCreeper()} 里 {@code dead = true} 在调用 explode **之前**就已设置，随后的
 * {@code spawnLingeringCloud()} / {@code discard()} 仍会执行 ⇒ 表现是**苦力怕无声消失**
 * （不掉战利品、不走死亡动画）。这是「阻止自爆」的既定代价，不是缺陷；若需要「取消爆炸且苦力怕
 * 继续存活」，原版没有可取消 {@code discard()} 的钩子，须另行裁决（例如改在
 * {@code ExplosionEvent.Start} 里重建实体，代价远大于收益）。
 *
 * <p><b>边界（不得扩大）</b>：非被吸附苦力怕、TNT、其他任何爆炸**一律放行** —— 本类只订阅
 * {@code ExplosionEvent.Start}、只做上述两条判据，不修改爆炸强度 / 半径 / 掉落 / 方块列表。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public final class RepulsionCreeperGuard {

    /** 「苦力怕 ↔ 强化拒止」关联查询的包围盒余量（格）：正常被吸附目标一直被钳在 8 格内，
     * 16 格只是给「刚越过 8 格、同一 tick 内开始引爆」留的余量，与 {@code FORGET_RADIUS}（32 格，集合剪枝）无关。 */
    private static final double LINK_RADIUS = 16.0D;

    private RepulsionCreeperGuard() {
    }

    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        Level level = event.getLevel();
        // 爆炸开始事件由 Level#explode 发出（两侧平台同点位）；客户端侧不干涉，也不该取消
        if (level.isClientSide) return;

        Creeper creeper = resolveCreeper(event);
        if (creeper == null) return;
        if (!isTrackedByRepulsion(level, creeper)) return;

        // 取消爆炸：Level#explode 会在此直接 return（方块 / 伤害 / 粒子 / 音效全部不发生）
        event.setCanceled(true);
        // 顺手把引信方向压回静止，避免客户端继续播放膨胀动画
        creeper.setSwellDir(-1);
        // 听觉反馈：引信被掐灭（给玩家一个可感知的否定反馈）
        level.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    /** 爆炸的来源实体（直接优先、间接兜底）；两者都不是苦力怕 ⇒ {@code null}。 */
    private static Creeper resolveCreeper(ExplosionEvent.Start event) {
        Entity direct = event.getExplosion().getDirectSourceEntity();
        if (direct instanceof Creeper creeper) return creeper;
        LivingEntity indirect = event.getExplosion().getIndirectSourceEntity();
        if (indirect instanceof Creeper creeper) return creeper;
        return null;
    }

    /** 周围 {@link #LINK_RADIUS} 格内是否有强化拒止正在吸附这颗苦力怕（跨维度不会同 level，天然隔离）。 */
    private static boolean isTrackedByRepulsion(Level level, Creeper creeper) {
        AABB area = creeper.getBoundingBox().inflate(LINK_RADIUS);
        for (RepulsionEntity repulsion : level.getEntitiesOfClass(RepulsionEntity.class, area)) {
            if (repulsion.isTracking(creeper.getUUID())) return true;
        }
        return false;
    }
}
