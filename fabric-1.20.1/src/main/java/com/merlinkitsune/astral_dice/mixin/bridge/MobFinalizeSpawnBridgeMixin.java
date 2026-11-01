package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.living.MobSpawnEvent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code MobSpawnEvent.FinalizeSpawn} 桥 —— <b>Fabric 侧整套事件都必须自建,这一项也不例外</b>。
 *
 * <h2>取证:Forge 到底在哪里派发这个事件</h2>
 * Forge 47.4.10 的 {@code MobSpawnEvent.FinalizeSpawn} javadoc 原文:
 * <pre>
 * In vanilla code, this event is injected by a transformer and not via patch,
 * so calls cannot be traced via call hierarchy (it is not source-visible).
 * </pre>
 * 实证与之一致 —— 本仓 {@code forge-1.20.1/build/moddev/artifacts/forge-1.20.1-47.4.10-merged.jar} 里:
 * <ul>
 *   <li>{@code net/minecraft/world/entity/Mob} 的 {@code finalizeSpawn} 只有 vanilla 三步
 *       (FOLLOW_RANGE 随机加成 / {@code nextFloat() < 0.05f} 左右手 / {@code putfield spawnType})
 *       加 {@code areturn},<b>没有任何事件调用</b>;</li>
 *   <li>{@code NaturalSpawner} 两处 spawn 循环都直接 {@code invokevirtual Mob.finalizeSpawn};</li>
 *   <li>全 jar 内引用 {@code ForgeEventFactory.onFinalizeSpawn} 的类只有 {@code BaseSpawner}
 *       (且用的是带 {@code BaseSpawner} 参数的 {@code onFinalizeSpawnSpawner},spawnType = SPAWNER)。</li>
 * </ul>
 * ⇒ 自然刷怪路径上的事件派发由 Forge 的 <b>transformer 在调用点注入</b>,发生在
 * {@code Mob#finalizeSpawn} <b>之前</b>。所以本桥选 {@code @At("HEAD")},而不是「TAIL 事后补一刀」:
 * 这才是 {@code MobSpawnEvent.FinalizeSpawn} 的相对时序(见 {@code EventEffects} 的 TODO 注释修订记录)。
 *
 * <h2>为什么 HEAD + cancellable</h2>
 * 复刻 Forge 的三条契约(其 javadoc 逐字给出):
 * <ol>
 *   <li>取消 ⇒ {@code finalizeSpawn} 不被调用、返回值恒为 {@code null},<b>实体仍会入世</b>
 *       ⇒ {@code cir.setReturnValue(null)} 后 return;</li>
 *   <li>{@code setSpawnCancelled(true)} ⇒ 平台阻止该次生成
 *       ⇒ {@code discard()}(vanilla {@code ServerLevel.addEntity} 对 {@code isRemoved()} 的实体
 *       直接 {@code return false} 并打 {@code Tried to add entity {} but it was marked as removed already})</li>
 *   <li>事件先于 vanilla 初始化 ⇒ 本模组监听器造复制体时,原件尚未跑 finalizeSpawn 的三步;</li>
 * </ol>
 * 「复制体不再派发事件」这一条 Forge 靠「调用 {@code Mob#finalizeSpawn} 而非
 * {@code ForgeEventFactory.onFinalizeSpawn}」达成;本线注入点就在 {@code Mob#finalizeSpawn} 里,
 * 只能靠 <b>ThreadLocal 重入闸</b>复现同一语义(否则 {@code placeStormCopy} 里对复制体的
 * {@code finalizeSpawn} 会重入本注入器 ⇒ 无限翻倍)。闸门覆盖<b>整个派发期</b>(post 期间置位),
 * 而不只是 set/clear 两侧。
 *
 * <h2>注入点与 require</h2>
 * 目标方法(1.20.1 mojmap,{@code javap -p -c} 实读):
 * <pre>
 * public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
 *     net.minecraft.world.level.ServerLevelAccessor, net.minecraft.world.DifficultyInstance,
 *     net.minecraft.world.entity.MobSpawnType, net.minecraft.world.entity.SpawnGroupData,
 *     net.minecraft.nbt.CompoundTag);
 * </pre>
 * 方法体 71 字节、<b>唯一</b>返回点 {@code 71: areturn} ⇒(若改用 TAIL)无歧义;本桥取 HEAD,
 * 命中次数 = 1(每个 {@code Mob} 类只有这一个方法体)⇒ {@code require = 1}。
 * {@code require} 是「最少命中次数」,写大只在目标类加载时抛 {@code InjectionError}。
 *
 * <p>⚠️ 目标方法名写 <b>Mojang 名</b>,由 Loom 在 {@code remapJar} 阶段重映射为 intermediary
 * (与 {@code LivingHurtBridgeMixin} 同款,产物级实证)。
 *
 * <p>⚠️ 已知边界(如实登记):完全覆写 {@code finalizeSpawn} 且<b>不调 {@code super}</b>的子类不会命中本注入
 * (注入的是 {@code Mob} 自己的方法体;子类覆写后不回调父类即绕过)。本仓 {@code forge} 侧同样如此。
 */
@Mixin(Mob.class)
public abstract class MobFinalizeSpawnBridgeMixin {

    /** 重入闸:true 表示当前线程正处于「本桥派发期」,期间任何 {@code finalizeSpawn} 都不再派发事件。 */
    private static final ThreadLocal<Boolean> ASTRAL_DICE_FINALIZE_SPAWN_GUARD = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "finalizeSpawn", at = @At("HEAD"), cancellable = true, require = 1)
    private void astralDice$bridgeFinalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                                @Nullable CompoundTag spawnTag,
                                                CallbackInfoReturnable<SpawnGroupData> cir) {
        if (ASTRAL_DICE_FINALIZE_SPAWN_GUARD.get()) {
            return; // 复制体/嵌套调用:不派发,与 Forge「直接调 Mob#finalizeSpawn」等价
        }
        Mob self = (Mob) (Object) this;
        ASTRAL_DICE_FINALIZE_SPAWN_GUARD.set(Boolean.TRUE);
        try {
            MobSpawnEvent.FinalizeSpawn event = new MobSpawnEvent.FinalizeSpawn(
                    self, level, self.getX(), self.getY(), self.getZ(),
                    difficulty, spawnType, spawnData, spawnTag, null);
            LoaderBus.INSTANCE.post(event);
            if (event.isSpawnCancelled()) {
                // Forge:setSpawnCancelled ⇒ 平台阻止入世(vanilla addEntity 对 isRemoved 实体的拒绝)
                self.discard();
                cir.setReturnValue(null);
                return;
            }
            if (event.isCanceled()) {
                // Forge:取消 ⇒ 不调 finalizeSpawn、返回值恒 null;但**实体仍会入世**
                cir.setReturnValue(null);
            }
        } finally {
            ASTRAL_DICE_FINALIZE_SPAWN_GUARD.set(Boolean.FALSE);
        }
    }
}
