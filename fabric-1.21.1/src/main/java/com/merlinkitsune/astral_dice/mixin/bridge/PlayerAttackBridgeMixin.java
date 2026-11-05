package com.merlinkitsune.astral_dice.mixin.bridge;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.player.AttackEntityEvent;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code Player#attack(Entity)} → {@link AttackEntityEvent}。
 *
 * <p>2026-09-29 补:此前无派发源 ⇒ {@code NancyLuSignItem#onNancyLuAttackWhileHidden}
 * (骇客立牌隐身期间攻击敌对目标时解除隐身并触发战斗牌加成)**永不执行**。
 *
 * <p>⚠️ {@code ServerPlayer} 覆写了 {@code attack} 但**会调 super**(javap 实证)⇒
 * 注入 {@code Player#attack} 即覆盖玩家;观众模式分支提前返回时本就不该触发。
 */
@Mixin(Player.class)
public abstract class PlayerAttackBridgeMixin {

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void astralDice$onAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        LoaderBus.INSTANCE.post(new AttackEntityEvent(self, target));
    }
}
