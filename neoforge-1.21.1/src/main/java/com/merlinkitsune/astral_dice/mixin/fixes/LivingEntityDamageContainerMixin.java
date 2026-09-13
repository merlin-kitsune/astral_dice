package com.merlinkitsune.astral_dice.mixin.fixes;

import java.util.Stack;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修补 NeoForge 21.1.x 的 {@code LivingEntity#hurt} 伤害容器栈泄漏。
 *
 * <p>NeoForge 21.1.235 的 {@code hurt} 在 push 之后调用
 * {@code CommonHooks.onEntityIncomingDamage(...)}，事件被取消时直接 {@code return false} 而**不 pop**，
 * {@code damageContainers} 因此每取消一次就多残留一层 {@code DamageContainer}
 * （本模组有三处取消点：骇客「网络防火墙」末影珍珠免疫、枪匠破绽闪避、肾上腺素-高效闪避）。
 * 上游已用「给取消分支补 pop」修复（PR #3101），但 1.21.x 全线未 backport。
 *
 * <p>本注入器**不补 pop，而是恢复栈深**：HEAD 记录进入时的栈深，RETURN 把栈截断回该深度。
 * 这样在上游 backport 之后自动退化为 no-op（上游已 pop 到位 → 本补丁 0 次 pop），
 * 永远不会多弹一层、也不会抛 {@code EmptyStackException}。截断逻辑见
 * {@link DamageStackSanitizer#sanitize(java.util.Stack, int)}。
 *
 * <p>两个注入器均为 {@code require = 0}，且本类所在 mixin 配置为 {@code "required": false}
 * （{@code astral_dice.neoforge_fixes.mixins.json}）：若未来上游重命名/重构 {@code hurt}
 * 或删除 {@code damageContainers}，本补丁最多**静默失效**，不会抛 {@code MixinApplyError}、
 * 不会让模组启动失败。
 *
 * <p>为区分「补丁活着」与「静默失效」：{@link DamageStackSanitizer#restoreEntry(java.util.Stack)}
 * 首次被调用时经 {@link NeoForgeFixesLog} 输出一行 INFO（每 JVM 一次，不刷屏）——
 * <code>[astral_dice/neoforge_fixes] DamageContainer leak patch active (first hurt: entryDepth=..,
 * beforeRestore=.., popped=..)</code>，可直接 <code>grep neoforge_fixes logs/latest.log</code> 判定。
 * 注意：若上游把 {@code hurt} 改名/重构，两个注入器都找不到目标 → **不打印任何日志**（静默失效），
 * 这正是「宁可静默也不启动失败」的取舍（见交付报告「不确定项」）。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageContainerMixin {

    /**
     * NeoForge patch 后的真实字段（{@code javap -p -v} 证据）：
     * {@code protected @Nullable java.util.Stack<net.neoforged.neoforge.common.damagesource.DamageContainer> damageContainers;}
     * —— 可见性 protected、非 final、带 {@code javax.annotation.Nullable}（构造器里初始化为 new Stack<>）。
     */
    @Shadow
    protected Stack<DamageContainer> damageContainers;

    /** 进入 hurt（含全部 3 个出口之前）：接入 slf4j 存活日志落点，并记录进入深度。 */
    @Inject(method = DamageStackSanitizer.HURT_DESCRIPTOR, at = @At("HEAD"), require = 0)
    private void astral_dice$recordDamageContainerDepth(DamageSource source, float amount, CallbackInfo callback) {
        NeoForgeFixesLog.install(); // 幂等：把一次性存活日志接到 slf4j，使其进入 logs/latest.log
        DamageStackSanitizer.recordEntry(this.damageContainers);
    }

    /**
     * 任一出口返回前：把栈截断回进入深度。
     *
     * <p>{@code @At("RETURN")} 注入在所有 {@code IRETURN} 之前，且总是在上游自己的 {@code pop} 之后执行，
     * 因此「方法结尾出口」与「无敌帧提前返回出口」都是 0 次 pop，只有「取消出口」补 1 次。
     */
    @Inject(method = DamageStackSanitizer.HURT_DESCRIPTOR, at = @At("RETURN"), require = 0)
    private void astral_dice$restoreDamageContainerDepth(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        DamageStackSanitizer.restoreEntry(this.damageContainers);
    }
}
