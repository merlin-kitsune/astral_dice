package com.merlinkitsune.astral_dice.mixin;

import com.merlinkitsune.astral_dice.damage.RailgunBolts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * 电磁炮雷击命中范围:把白名单**上移到 {@code LightningBolt#tick} 的目标筛选**。
 *
 * <p><b>为什么不能只挂在 {@code Entity#thunderHit}</b>:原版 {@code LightningBolt#tick}
 * 先取 3 格箱内的全部存活实体,再逐个
 * {@code if (!onEntityStruckByLightning(entity, this)) entity.thunderHit(level, this)},
 * 最后 {@code hitEntities.addAll(list)} 并触发 {@code CHANNELED_LIGHTNING} 成就。而
 * **海龟 / 村民 / 猪 / 蘑菇牛覆写了 {@code thunderHit} 且不调用 {@code super}**
 * (海龟自算 {@code Float.MAX_VALUE} 秒杀、村民转女巫、猪转僵尸猪灵、蘑菇牛换肤),所以在
 * {@code Entity#thunderHit} 里 {@code ci.cancel()} 对它们完全无效,事件与成就也不受实体白名单约束。
 *
 * <p><b>做法</b>:在 {@code tick} 里第二个 {@code Level#getEntities(Entity, AABB, Predicate)}
 * 调用(3 格目标筛选;第一个是 verdict 用的 15 格查询)上做 {@code @ModifyArg},把原谓词
 * {@code Entity::isAlive} 与白名单**与**起来。于是非敌对目标**在进入循环之前**就被剔除:
 * 不受伤、不转化、不触发 {@code onEntityStruckByLightning}、不进入成就的实体列表
 * (成就吃的是同一份 list)。只对本模组电磁炮降下的闪电生效,原版/其它模组闪电一律不受影响。
 *
 * <p>定点依据(已用 {@code javap -c} 从两版本合并 jar 逐条核对):{@code tick} 内对
 * {@code Level.getEntities} 共两次调用,{@code ordinal = 1} 即 3 格目标筛选那次;
 * 谓词是第 3 个参数({@code index = 2})。
 */
@Mixin(LightningBolt.class)
public abstract class LightningBoltStrikeScopeMixin {

    @ModifyArg(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
                    ordinal = 1),
            index = 2
    )
    private Predicate<Entity> astral$railgunStrikeScope(Predicate<Entity> original) {
        LightningBolt self = (LightningBolt) (Object) this;
        if (!RailgunBolts.isRailgunBolt(self)) return original;
        return original.and(e -> RailgunBolts.isValidLightningTarget(e, self));
    }
}
