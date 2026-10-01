package com.merlinkitsune.astral_dice.mixin.bridge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.item.ItemTossEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code ServerPlayer#drop(ItemStack, boolean, boolean)} 里的
 * {@code Level#addFreshEntity} → {@link ItemTossEvent}。
 *
 * <p>2026-09-29 补:此前该事件无派发源 ⇒ {@code TemporaryCardEvents#onItemToss}
 * (临时牌取消抛出并退还进物品栏)**永不执行** —— 临时牌本应「不能被丢出」，实际会落地。
 *
 * <h2>⚠️ 为什么目标类是 {@code ServerPlayer} 而不是 {@code Player}</h2>
 * 这是本轮**实测踩到**的坑(javap 逐条核对):
 * <pre>
 *   Player#drop(ItemStack,ZZ)        → 只 new ItemEntity + setPickUpDelay/setThrower/setDeltaMovement,
 *                                      **整段字节码里没有任何 Level#addFreshEntity**(grep 零命中),
 *                                      物品**不进入世界**,方法就直接 areturn。
 *   ServerPlayer#drop(ItemStack,ZZ)  → invokespecial Player.drop(...) 拿到 ItemEntity,
 *                                      itemEntity == null 时直接返回;否则
 *                                      `level().addFreshEntity(itemEntity)` ← **落地真正发生在这里**。
 * </pre>
 * 一开始按「Forge 的 patch 位置 = Player#drop」直译,写成 {@code @Mixin(Player.class)} +
 * {@code @At(INVOKE, Level.addFreshEntity)} ⇒ 运行期直接
 * {@code InjectionError: … failed injection check, (0/1) succeeded. Scanned 0 target(s)} ⇒
 * **`Player` 类加载失败、服务端起不来**(datagen 首次实跑即崩,`Failed to start the minecraft server`)。
 *
 * <p>⚠️ 与 {@code PlayerItemDropGuardMixin}(注入 {@code Player#drop} 的 HEAD)是**两条独立规则**:
 * 前者管「物品自带的抛出否决权」(在 super 调用前就返回 null,故对所有端生效),
 * 本 mixin 管「事件层面的抛出否决」(只在真正落地的 ServerPlayer 上生效 —— 与消费方的
 * {@code player.level().isClientSide()} 检查一致)。
 *
 * <p><b>取消语义</b>:消费方在事件里已完成「把栈 copy 进背包 + 把原栈 setCount(0)」,
 * 故本 mixin 只需在取消时**不调用** {@code addFreshEntity}(返回 false)即可阻止物品进入世界。
 */
@Mixin(ServerPlayer.class)
public abstract class ItemTossBridgeMixin {

    @WrapOperation(
            method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean astralDice$onItemToss(Level level, Entity entity, Operation<Boolean> original) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (entity instanceof ItemEntity itemEntity) {
            ItemTossEvent event = new ItemTossEvent(itemEntity, self);
            LoaderBus.INSTANCE.post(event);
            if (event.isCanceled()) {
                return false;
            }
        }
        return original.call(level, entity);
    }
}
