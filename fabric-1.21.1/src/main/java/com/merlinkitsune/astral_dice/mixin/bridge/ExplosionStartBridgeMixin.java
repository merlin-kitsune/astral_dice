package com.merlinkitsune.astral_dice.mixin.bridge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.level.ExplosionEvent;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code ExplosionEvent.Start} 桥 —— <b>Forge 有、Fabric 没有，必须自写</b>（MAPPING B-10）。
 *
 * <h2>为什么注入 {@code Level#explode} 而【不是】{@code Explosion#explode} 的 HEAD</h2>
 * Forge 的 {@code Level.java.patch} 是在终态 {@code explode} 重载里写下
 * {@code if (ForgeEventFactory.onExplosionStart(this, explosion)) return explosion;}，
 * 位置在 {@code new Explosion(...)} 之后、{@code explosion.explode()} <b>之前</b>。
 * 按 1.20.1 字节码逐方法归因，<b>只取消 {@code Explosion#explode()} 的 HEAD 并不等价</b>：
 * <pre>
 *   Explosion#explode()V             → GameEvent.EXPLODE + 实体 hurt（伤害）
 *   Explosion#finalizeExplosion(Z)V  → setBlock / dropFromExplosion+popResource / 火 /
 *                                      playLocalSound / addParticle / spawnAfterBreak
 * </pre>
 * 二者是 {@code Level#explode} 里<b>两次独立调用</b>（偏移 129 / 136）。只掐前者，后者照跑 ⇒
 * 方块照炸、火照点、客户端照播爆炸音与粒子 —— 那就不是「这次爆炸不发生」了。
 * 因此这里在 {@code Level#explode} 内把两次调用<b>一起</b>拦下。
 *
 * <h2>注入形态</h2>
 * 两处 {@link WrapOperation}（MixinExtras，与 {@code LivingHurtBridgeMixin} 同款）—— 相对
 * {@code @Inject + LocalCapture} 的好处是<b>不依赖 LVT</b>：{@code explode()} 是<b>无参</b>实例调用，
 * {@code finalizeExplosion} 只有 1 个实参，{@code WrapOperation} 直接把<b>接收者</b>当第一个处理器参数给到，
 * 一次局部变量捕获都不需要（本仓已有教训：LVT 随上游改动即失配，见
 * {@code client/EffectRenderingInventoryScreenMixin} 的注入手法说明）。
 *
 * <p>两处 {@code require = 1} 是本方法的<b>真实命中数</b>（已用 {@code javap -c} 对终态重载全文核实：
 * {@code Level} 整个类里 {@code Explosion.explode:()V} 与 {@code Explosion.finalizeExplosion:(Z)V} 各只出现 1 次）。
 * ⚠️ Mixin 的 {@code require} 语义是「<b>最少</b>命中次数」，写成 2 而实际只有 1 处 ⇒ 目标类加载时抛
 * {@code InjectionError}（编译与构建都不报错）。上游改结构时宁可此处炸掉，也不要静默漏注。
 *
 * <h2>「两次调用」的配对</h2>
 * 取消后必须让<b>同一颗</b> {@code Explosion} 的 {@code finalizeExplosion} 也不跑，故用
 * {@link ThreadLocal} 记下被取消的那个实例，第二处按<b>引用相等</b>读取并清空。
 * 不会串味：取消时第一处<b>根本不调用</b> {@code explode()}，两次调用之间只剩 {@code iload}（无重入点）；
 * 即使外部注入器在两者之间抛异常留下残值，下一颗爆炸的第一处也会先 {@code remove()} 覆盖掉。
 *
 * <h2>目标描述符</h2>
 * 写 Mojang 名（Loom 在 {@code remapJar} 阶段连描述符一并重映射为 intermediary）。
 * 用<b>完整描述符</b>而不是 {@code "explode"}：{@code Level} 里有 <b>6 个</b> {@code explode} 重载
 * （6 参 / 7 参 / 8 参 Vec3 版 / 9 参 / 12 参 / 13 参终态），裸名会同时命中全部重载。
 *
 * <p><b>1.21.1 口径</b>：1.20.1 的终态重载是 10 参 {@code (..., ExplosionInteraction, Z)}，1.21.1 把
 * {@code new Explosion(...)} + {@code explosion.explode()} + {@code explosion.finalizeExplosion(Z)}
 * 整体挪到了 13 参重载 {@code (..., ExplosionInteraction, Z, ParticleOptions, ParticleOptions, Holder<SoundEvent>)}
 * 里（javap -c 实测：这两处 invokevirtual 只在该重载的偏移 143 / 150 各出现 1 次，其余重载只是转发）。
 */
@Mixin(Level.class)
public abstract class ExplosionStartBridgeMixin {

    /** {@code Level#explode} 1.21.1 终态重载：{@code (Entity, DamageSource, ExplosionDamageCalculator, D, D, D, F, Z, ExplosionInteraction, Z, ParticleOptions, ParticleOptions, Holder)Explosion}。 */
    private static final String ASTRAL_DICE_LEVEL_EXPLODE =
            "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;"
                    + "Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZ"
                    + "Lnet/minecraft/world/level/Level$ExplosionInteraction;Z"
                    + "Lnet/minecraft/core/particles/ParticleOptions;"
                    + "Lnet/minecraft/core/particles/ParticleOptions;"
                    + "Lnet/minecraft/core/Holder;)"
                    + "Lnet/minecraft/world/level/Explosion;";

    /** 已被「爆炸开始」否决、等待跳过 {@code finalizeExplosion} 的那颗爆炸（仅本线程、两次调用之间有效）。 */
    private static final ThreadLocal<Explosion> ASTRAL_DICE_SUPPRESSED_EXPLOSION = new ThreadLocal<>();

    /** ① 派发 {@code ExplosionEvent.Start}；取消则连 {@code Explosion#explode()} 一起不调用。 */
    @WrapOperation(
            method = ASTRAL_DICE_LEVEL_EXPLODE,
            require = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Explosion;explode()V"))
    private void astralDice$bridgeExplosionStart(Explosion explosion, Operation<Void> original) {
        ExplosionEvent.Start event = new ExplosionEvent.Start((Level) (Object) this, explosion);
        LoaderBus.INSTANCE.post(event);
        if (event.isCanceled()) {
            // Forge:onExplosionStart 为 true ⇒ Level#explode 直接 return explosion ⇒ explode() 不执行
            ASTRAL_DICE_SUPPRESSED_EXPLOSION.set(explosion);
            return;
        }
        ASTRAL_DICE_SUPPRESSED_EXPLOSION.remove();
        original.call(explosion);
    }

    /** ② 同一颗爆炸被否决时，{@code finalizeExplosion} 也不执行（否则方块 / 火 / 音效 / 粒子照旧）。 */
    @WrapOperation(
            method = ASTRAL_DICE_LEVEL_EXPLODE,
            require = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Explosion;finalizeExplosion(Z)V"))
    private void astralDice$skipFinalizeWhenCanceled(Explosion explosion, boolean spawnParticles,
                                                    Operation<Void> original) {
        if (ASTRAL_DICE_SUPPRESSED_EXPLOSION.get() == explosion) {
            ASTRAL_DICE_SUPPRESSED_EXPLOSION.remove();
            return;
        }
        original.call(explosion, spawnParticles);
    }
}
