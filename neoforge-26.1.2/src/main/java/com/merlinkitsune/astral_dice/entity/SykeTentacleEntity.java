package com.merlinkitsune.astral_dice.entity;

import com.merlinkitsune.starengine.geckolib.animatable.GeoEntity;
import com.merlinkitsune.starengine.geckolib.animatable.instance.AnimatableInstanceCache;
import com.merlinkitsune.starengine.geckolib.animatable.manager.AnimatableManager;
import com.merlinkitsune.starengine.geckolib.animation.AnimationController;
import com.merlinkitsune.starengine.geckolib.animation.RawAnimation;
import com.merlinkitsune.starengine.geckolib.animation.object.PlayState;
import com.merlinkitsune.starengine.geckolib.util.GeckoLibUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Syke 触手实体（26.1.2 线，GeckoLib 5.5.2 版）：GeckoLib 动画实体的本线首个实现。
 *
 * <p><b>形态</b>：{@code extends LivingEntity}（不带 Mob AI），出生/存活/属性全部交给超类；
 * 本类只补三件事：GeckoLib 动画缓存、两条动画控制器、以及「受伤时播一次攻击动画」这一个触发点。
 *
 * <p><b>26.1.2 与 1.21.1 的三处差异</b>（均由 javap 对实装 jar 取证）：
 * <ul>
 *   <li>GeckoLib 5 的包根是 {@code com.geckolib.*}（v4 的 {@code software.bernie.geckolib.*} 已废弃），
 *       {@link PlayState} 迁到 {@code com.geckolib.animation.object} 包；</li>
 *   <li>动画状态回调的参数从 v4 的 {@code AnimationState} 变为 v5 的 {@code AnimationTest}
 *       （{@code setAndContinue}/{@code setAnimation} 仍在，方法名不变）；</li>
 *   <li>原版 26.1.2 的 {@code Entity#hurt(DamageSource, float)} 已变为 {@code final} 且委托给抽象的
 *       {@code hurtServer(ServerLevel, DamageSource, float)}，故受伤钩子必须覆写后者（见 {@link #hurtServer}）。</li>
 * </ul>
 *
 * <p><b>资源</b>（{@code models/sykestentacle/} 的原样拷贝，路径遵循 GeckoLib 约定）：
 * <ul>
 *   <li>几何：{@code assets/astral_dice/geo/sykestentacle.geo.json}</li>
 *   <li>贴图：{@code assets/astral_dice/textures/entity/sykestentacle.png}</li>
 *   <li>动画：{@code assets/astral_dice/animations/sykestentacle.animation.json}，
 *       内含 {@code animation.tentacleidle} 与 {@code animation.tentacleattack} 两支</li>
 * </ul>
 *
 * <p><b>动画控制器</b>：
 * <ul>
 *   <li>{@code idle} —— 过渡 5 tick，基础状态固定续播 {@code animation.tentacleidle}（循环）；</li>
 *   <li>{@code action} —— 基础状态返回 {@link PlayState#STOP}（不占基础层），只登记可触发动画
 *       {@code attack}；由 {@link #hurtServer} 在真实受伤时触发一次，播完自动回到 idle。</li>
 * </ul>
 *
 * <p><b>同步</b>：{@code GeoEntity#triggerAnim} 的服务端分支走 GeckoLib 自有网络通道
 * （javap 实证：{@code GeckoLibServices.NETWORK.triggerEntityAnim(entity, false, controller, anim)}），
 * 把触发广播给追踪该实体的客户端，故本类不需要手写同步包。
 *
 * <p>尺寸 {@code sized(2.6F, 3.0F)} 由 {@link ModEntityTypes#SYKE_TENTACLE} 声明，依据模型实测：
 * 底盘约 2.45 格宽、通高约 2.96 格。
 */
public class SykeTentacleEntity extends LivingEntity implements GeoEntity {

    /** 空闲控制器名（与 {@code triggers} 无关，仅作 GeckoLib 内部键）。 */
    private static final String CONTROLLER_IDLE = "idle";

    /** 动作控制器名：触发式动画专用，基础状态返回 STOP。 */
    private static final String CONTROLLER_ACTION = "action";

    /** 与 {@code animations/sykestentacle.animation.json} 里的动画键逐字一致。 */
    private static final String ANIM_IDLE = "animation.tentacleidle";

    /** 同上；由 {@link #hurtServer} 触发的可触发动画。 */
    private static final String ANIM_ATTACK = "animation.tentacleattack";

    /** {@code triggerableAnim} 登记名，{@code triggerAnim} 时按此名查找。 */
    private static final String TRIGGER_ATTACK = "attack";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SykeTentacleEntity(EntityType<? extends SykeTentacleEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // idle：构造函数第 2 个参数是 transitionLength（5 tick）；基础状态每 tick 续播同一支循环动画。
        // ⚠️ v5 的 AnimationController 构造器**不再收 animatable 实例**（javap 实证：只剩
        //    (AnimationStateHandler) / (String, AnimationStateHandler) / (String, int, AnimationStateHandler)），
        //    实体由 AnimatableInstanceCache → AnimatableManager 关联，回调里经 AnimationTest#animatable 取回。
        controllers.add(new AnimationController<SykeTentacleEntity>(CONTROLLER_IDLE, 5,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop(ANIM_IDLE))));

        // action：基础状态 STOP ⇒ 不与其他控制器抢基础层；只登记 triggerable，等待受伤触发。
        controllers.add(new AnimationController<SykeTentacleEntity>(CONTROLLER_ACTION,
                state -> PlayState.STOP)
                .triggerableAnim(TRIGGER_ATTACK, RawAnimation.begin().thenPlay(ANIM_ATTACK)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /**
     * 受伤时播一次攻击动画（本类唯一的行为钩子）。
     *
     * <p>26.1.2 的原版链是 {@code hurt(DamageSource, float)}（final，只做客户端/服务端分流）
     * → {@code hurtServer(ServerLevel, DamageSource, float)}；后者返回 true 才表示这次伤害真的生效。
     * 只认它的返回值：被无敌帧 / 免疫 / 已死亡挡下的伤害不触发。
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);

        if (hurt) {
            triggerAnim(CONTROLLER_ACTION, TRIGGER_ATTACK);
        }

        return hurt;
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
