package com.merlinkitsune.astral_dice.mixin;

import com.merlinkitsune.astral_dice.damage.ModDamageTypes;
import com.merlinkitsune.astral_dice.damage.RailgunBolts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 电磁炮雷击改为**真伤**(无视护甲值与盔甲韧性)。
 *
 * <p><b>为什么必须在这里改</b>:原版闪电本身不带伤害——{@code LightningBolt#tick} 只负责挑选目标
 * (1.21.1 反编译源码 {@code LightningBolt.java:155-171},箱体 ±3 格、垂直 +6+3,谓词
 * {@code Entity::isAlive})并逐个调用 {@code entity.thunderHit(level, this)};真正的伤害结算在
 * {@code Entity#thunderHit}({@code Entity.java:2511-2518}):
 * <pre>
 *   this.setRemainingFireTicks(this.remainingFireTicks + 1);
 *   if (this.remainingFireTicks == 0) this.igniteForSeconds(8.0F);
 *   this.hurt(this.damageSources().lightningBolt(), lightning.getDamage());
 * </pre>
 * 而 {@code minecraft:lightning_bolt} **不在** {@code minecraft:bypasses_armor} 标签里
 * (该标签内容已从本机 client-extra 资源 jar 逐条核对),所以原版雷击会被护甲值与盔甲韧性减免——
 * 这正是电磁炮此前"不是真伤"的原因。
 *
 * <p><b>接管范围</b>:只对**本模组电磁炮降下的闪电**(见 {@link RailgunBolts})生效,在方法 HEAD 处
 * 复刻原版的点火两行、把伤害换成 {@link ModDamageTypes#trueDamage(net.minecraft.world.level.Level)}
 * 后取消原版方法体。原版流程的其余部分完全不变:目标筛选、{@code onEntityStruckByLightning} 事件、
 * 苦力怕充能({@code Creeper#thunderHit} 在 {@code super} 之后无条件置充能)、僵尸猪灵/女巫转化
 * ({@code Pig}/{@code Villager} 覆写不调用 super,天然不受影响)、海龟({@code Turtle} 覆写自算
 * {@code Float.MAX_VALUE} 雷击伤害,不经此处)、盔甲架/悬挂物(空覆写,不受伤)。
 */
@Mixin(Entity.class)
public abstract class EntityThunderHitMixin {

    @Inject(
            method = "thunderHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LightningBolt;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void astral$railgunTrueDamage(ServerLevel level, LightningBolt bolt, CallbackInfo ci) {
        if (!RailgunBolts.isRailgunBolt(bolt)) return;
        Entity self = (Entity) (Object) this;
        // 命中范围(口径后经库 HostileTargets 统一重写):只对**敌对生物 / 中立生物(宠物除外)**生效,并排除
        // 施放者自己拥有的宠物;其余实体整段取消 —— 既不受伤也不点燃。
        // 注意:这里只是**纵深防御**——覆写 thunderHit 且不调 super 的原版生物(海龟/村民/猪/
        // 蘑菇牛)根本进不到本方法,真正的范围收窄在 LightningBoltStrikeScopeMixin 的目标筛选里。
        if (!RailgunBolts.isValidLightningTarget(self, bolt)) {
            ci.cancel();
            return;
        }
        // 复刻原版点火(Entity.java:2512-2515):先 +1 tick,再在"原本未着火"(== 0)时点燃 8 秒
        self.setRemainingFireTicks(self.getRemainingFireTicks() + 1);
        if (self.getRemainingFireTicks() == 0) {
            self.igniteForSeconds(8.0F);
        }
        // 真伤伤害源:无来源实体(与旧的原版闪电一致,不构成玩家攻击、无击杀归属)
        self.hurt(ModDamageTypes.trueDamage(level), bolt.getDamage());
        ci.cancel();
    }
}
