package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.entity.RepulsionEntity;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.level.ExplosionEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 强化拒止 · 苦力怕「禁止自爆」兜底（2026-10-18 用户追加规则；forge-1.20.1 → fabric-1.20.1 移植）。
 *
 * <p><b>为什么需要事件层</b>：{@code RepulsionEntity#suppressTrackedCreeperFuses()} 每 tick 把被吸附
 * 苦力怕的 {@code swellDir} 压成 -1，但那只对**自然膨胀**有效。一旦苦力怕被**打火石 / 火焰**点燃，
 * 原版 {@code Creeper#tick} 会在读值之前**每 tick 无条件** {@code setSwellDir(1)}
 * （{@code if (this.isIgnited()) this.setSwellDir(1);}）⇒ 压制必然被覆盖、引信照烧到底。
 * 因此必须再叠一层「爆炸开始」拦截。
 *
 * <p><b>判据（只吃被吸附的苦力怕）</b>：
 * <ol>
 *   <li>爆炸的来源实体是 {@code Creeper} —— {@code Creeper#explodeCreeper()} 调用
 *       {@code level().explode(this, ...)}，source = 苦力怕自身，故<b>直接来源</b>即命中；
 *       <b>间接来源</b>只是兜底（与基线同顺序）；</li>
 *   <li>该苦力怕 {@link #LINK_RADIUS} 格内存在一座 {@link RepulsionEntity}，且它的
 *       {@link RepulsionEntity#isTracking(java.util.UUID)} 对该苦力怕的 UUID 返回 true。</li>
 * </ol>
 * 命中 ⇒ 调用方取消爆炸 + {@code creeper.setSwellDir(-1)}
 * + 在苦力怕处播 {@code SoundEvents.FIRE_EXTINGUISH}（引信被掐灭的听觉反馈）。
 *
 * <p><b>取消后的真实行为（已核 1.20.1 原版源码）</b>：取消发生在 {@code Level#explode} 里
 * {@code new Explosion(...)} 之后、{@code explosion.explode()} **之前** ⇒ 不炸方块、不造成伤害、
 * 无爆炸粒子与音效；但 {@code explodeCreeper()} 里 {@code dead = true} 在调用 explode **之前**
 * 就已设置，随后的 {@code discard()} / {@code spawnLingeringCloud()} 仍会执行 ⇒ 表现是
 * **苦力怕无声消失**（不掉战利品、不走死亡动画）。这是「阻止自爆」的既定代价，不是缺陷。
 *
 * <p><b>边界（不得扩大）</b>：非被吸附苦力怕、TNT、其他任何爆炸**一律放行** —— 本类只做上述两条判据，
 * 不修改爆炸强度 / 半径 / 掉落 / 方块列表。
 *
 * <p><b>fabric-1.20.1 平台差异</b>（相对 forge-1.20.1 基线；B-10 批次已补齐）：
 * <ul>
 *   <li>forge 基线是本类的 {@code @SubscribeEvent onExplosionStart(ExplosionEvent.Start)}；本线的
 *       {@code ExplosionEvent} 是自建垫片（{@code platform/event/level/ExplosionEvent.java}，类名 /
 *       构造器 / 访问器与 Forge 逐字对齐），由 {@code mixin/bridge/ExplosionStartBridgeMixin} 在
 *       {@code Level#explode} 的 {@code new Explosion(...)} 之后、{@code explosion.explode()}
 *       <b>之前</b>派发 ⇒ 消费方代码与 forge 基线逐行同构（只换 import）。原 B-10 缺口至此关闭；
 *       该缺口当时的记录见 {@code porting/fabric-1.20.1/MAPPING-1.5.0.md}。</li>
 *   <li>本类保留<b>两套入口</b>：带 {@code @SubscribeEvent} 的
 *       {@link #onExplosionStart(ExplosionEvent.Start)}（总线路径，与 forge 基线同名、同判据），
 *       以及无注解的静态入口 {@link #handleExplosionStart(Level, Entity, LivingEntity)}
 *       （B3 批次的历史 API，{@code porting/fabric-1.20.1/B3-EFFECTCARDS.md} 已引用，保留不删；
 *       它现在是前者的实现体，判据只有一份）。</li>
 *   <li>取消语义见 {@code ExplosionStartBridgeMixin}：取消 ⇒ {@code Explosion#explode()} 与
 *       {@code Explosion#finalizeExplosion(boolean)} 都不执行，而 {@code Level#explode} 仍返回该
 *       {@code Explosion} ⇒ 与 Forge 的 {@code return explosion;} 等价。</li>
 *   <li>本类<b>必须</b>在 {@code AstralDiceMod#registerListeners()} 登记，否则 {@code @SubscribeEvent}
 *       方法不会被调用（静默失效）。</li>
 * </ul>
 */
public final class RepulsionCreeperGuard {

    /** 「苦力怕 ↔ 强化拒止」关联查询的包围盒余量（格）：正常被吸附目标一直被钳在 8 格内，
     * 16 格只是给「刚越过 8 格、同一 tick 内开始引爆」留的余量，与 {@code FORGET_RADIUS}（32 格，集合剪枝）无关。 */
    private static final double LINK_RADIUS = 16.0D;

    private RepulsionCreeperGuard() {
    }

    /** 「爆炸开始」订阅入口（与 forge 基线同名同判据）；判据体在 {@link #handleExplosionStart}。 */
    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        if (handleExplosionStart(event.getLevel(), event.getExplosion().getDirectSourceEntity(),
                event.getExplosion().getIndirectSourceEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * 「爆炸开始」判据（**服务端**）.
     *
     * <p>等价于 forge 基线的 {@code onExplosionStart(ExplosionEvent.Start)} 方法体：
     * 判据相同、副作用相同、判定顺序相同（直接来源优先、间接来源兜底、然后查附近力场是否在吸附它）。
     *
     * @param level          爆炸所在的 level（客户端侧直接放行，与基线一致）
     * @param directSource   爆炸的直接来源实体（{@code Explosion#getDirectSourceEntity()}）
     * @param indirectSource 爆炸的间接来源实体（{@code Explosion#getIndirectSourceEntity()}）
     * @return {@code true} = 应取消这次爆炸（调用方 {@code event.setCanceled(true)}）；
     *         {@code false} = 放行
     */
    public static boolean handleExplosionStart(Level level, Entity directSource, LivingEntity indirectSource) {
        // 爆炸开始事件由 Level#explode 发出；客户端侧不干涉，也不该取消
        if (level == null || level.isClientSide) return false;

        Creeper creeper = resolveCreeper(directSource, indirectSource);
        if (creeper == null) return false;
        if (!isTrackedByRepulsion(level, creeper)) return false;

        // 顺手把引信方向压回静止，避免客户端继续播放膨胀动画
        creeper.setSwellDir(-1);
        // 听觉反馈：引信被掐灭（给玩家一个可感知的否定反馈）
        level.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.2F);
        return true;
    }

    /** 爆炸的来源实体（直接优先、间接兜底）；两者都不是苦力怕 ⇒ {@code null}。 */
    private static Creeper resolveCreeper(Entity direct, LivingEntity indirect) {
        if (direct instanceof Creeper creeper) return creeper;
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
