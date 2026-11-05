package com.merlinkitsune.astral_dice.mixin.bridge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingEntityUseItemEvent;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code LivingEntity#completeUsingItem()} 里的 {@code ItemStack#finishUsingItem} → {@link LivingEntityUseItemEvent.Finish}。
 *
 * <p>2026-09-29 补:此前无派发源 ⇒ {@code FateGuidanceCardItem#onEatSaturationDouble}
 * (命运的指引·福运:进食额外补一份饱和度)**永不执行**。
 *
 * <p><b>为何包 {@code finishUsingItem} 而不是注入 {@code completeUsingItem} 的 HEAD</b>:
 * Forge 的 {@code Finish} 事件在「食物数据已应用之后、回调返回之前」派发,事件构造器还带
 * {@code result}(使用后的结果物品)。包住该调用才能同时拿到 {@code result} 并与 Forge 的时序一致。
 *
 * <p>⚠️ {@code ServerPlayer} 覆写了 {@code completeUsingItem},但 javap 实证它
 * **invokespecial 调 {@code Player/LivingEntity.completeUsingItem}** ⇒ 注入基类即覆盖玩家路径,
 * 无需为 {@code ServerPlayer} 另写一份(重复注入会导致 Finish 事件发两次)。
 */
@Mixin(LivingEntity.class)
public abstract class LivingUseItemFinishBridgeMixin {

    @WrapOperation(
            method = "completeUsingItem()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack astralDice$onItemUseFinish(ItemStack instance, Level level, LivingEntity entity,
                                                 Operation<ItemStack> original) {
        ItemStack result = original.call(instance, level, entity);
        LoaderBus.INSTANCE.post(new LivingEntityUseItemEvent.Finish(
                entity, instance, entity.getUseItemRemainingTicks(), result));
        return result;
    }
}
