package com.merlinkitsune.astral_dice.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * Syke 触手实体（2026-10-29；2026-10-29 由 neoforge-1.21.1 蓝本移植到本线）。
 *
 * <p><b>形态</b>：沿用 {@link BarricadeEntity} / {@link RepulsionEntity} 的最小实体范式 ——
 * {@code extends LivingEntity}（不走 Mob AI），出生 / 受伤 / 存活逻辑全部交给超类，本类只补三件事：
 * GeckoLib 的动画缓存、两条动画控制器、以及「受伤时播一次攻击动画」这一个真实触发点。
 * 生命周期里没有任何 AI 目标、寻路、掉落或生成规则。
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
 *   <li>{@code idle} —— 过渡 5 tick，基础状态固定播放 {@code animation.tentacleidle}（循环）；</li>
 *   <li>{@code action} —— 基础状态返回 {@link PlayState#STOP}（不占用基础层），只登记可触发动画
 *       {@code attack}；由 {@link #hurt} 在真实受伤时触发一次，播完自动回到 idle。</li>
 * </ul>
 *
 * <p><b>同步</b>：{@code triggerAnim} 在服务端调用时会走 GeckoLib 自己的网络通道，把触发广播给
 * 追踪该实体的客户端，因此这里不需要手写同步包。
 *
 * <p><b>1.20.1 平台差异</b>（相对 neo 蓝本）：
 * <ul>
 *   <li><b>GeckoLib 包名</b>：本线实装 4.8.4（1.20.1 Forge），动画相关类仍在 {@code core.} 子包下 ——
 *       {@code software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache}、
 *       {@code core.animation.}{@link AnimatableManager} / {@link AnimationController} / {@link RawAnimation}、
 *       {@code core.object.}{@link PlayState}；neo 蓝本用的 4.9.3 已去掉 {@code core.}。
 *       {@code animatable.GeoEntity}、{@code model.GeoModel}、{@code renderer.GeoEntityRenderer}、
 *       {@code util.GeckoLibUtil} 两线同路径；方法体（构造器重载 / {@code triggerableAnim} /
 *       {@code triggerAnim}）两线同形，均经 javap 核对本线 4.8.4 实装 jar。</li>
 *   <li><b>注册器 / 事件总线</b>：本线 {@code ModEntityTypes} 用 {@code RegistryObject}
 *       （1.21.1 蓝本是 {@code DeferredHolder}），属性注册走
 *       {@code net.minecraftforge.event.entity.EntityAttributeCreationEvent}（见 {@code event/ModEntityEvents}）。
 *       本类不直接接触这两处，故方法体与蓝本逐字一致。</li>
 * </ul>
 *
 * <p>尺寸 {@code sized(2.6F, 3.0F)} 由 {@link ModEntityTypes#SYKE_TENTACLE} 声明，依据是模型实测：
 * 底盘约 2.45 格宽、通高约 2.96 格。
 */
public class SykeTentacleEntity extends LivingEntity implements GeoEntity {

    /** 空闲控制器名。 */
    private static final String CONTROLLER_IDLE = "idle";

    /** 动作控制器名：触发式动画专用，基础状态返回 STOP。 */
    private static final String CONTROLLER_ACTION = "action";

    /** 与 {@code animations/sykestentacle.animation.json} 里的键逐字一致。 */
    private static final String ANIM_IDLE = "animation.tentacleidle";

    /** 同上；由 {@link #hurt} 触发的可触发动画名。 */
    private static final String ANIM_ATTACK = "animation.tentacleattack";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SykeTentacleEntity(EntityType<? extends SykeTentacleEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // idle：构造器第 3 个参数即 transitionLength（5 tick）；基础状态每 tick 续播同一支循环动画。
        controllers.add(new AnimationController<>(this, CONTROLLER_IDLE, 5,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop(ANIM_IDLE))));

        // action：基础状态 STOP ⇒ 不与其他控制器抢基础层；只登记 triggerable，等待 hurt 触发。
        controllers.add(new AnimationController<>(this, CONTROLLER_ACTION, state -> PlayState.STOP)
                .triggerableAnim("attack", RawAnimation.begin().thenPlay(ANIM_ATTACK)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /**
     * 受伤时播一次攻击动画（本类唯一的行为钩子）。
     *
     * <p>只认 {@code super.hurt} 的返回值：被无敌帧 / 免疫 / 已死亡挡下的伤害不触发，
     * 与「真的掉了血」保持同一口径。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);

        if (hurt) {
            triggerAnim(CONTROLLER_ACTION, "attack");
        }

        return hurt;
    }

    // 1.20.1 的 LivingEntity 仍是这 4 个抽象方法（与 1.21.1 同形，本线 BarricadeEntity /
    // RepulsionEntity 已实读确认）：没有装备栏 / 主手，全部给空实现。

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        // 没有装备栏：LivingEntity 的抽象方法必须实现，这里保持空实现。
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
