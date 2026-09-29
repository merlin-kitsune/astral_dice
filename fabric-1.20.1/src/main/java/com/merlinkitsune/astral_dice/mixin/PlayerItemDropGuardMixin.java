package com.merlinkitsune.astral_dice.mixin;

import com.merlinkitsune.astral_dice.platform.item.DropGuardItem;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code Player#drop(ItemStack, boolean, boolean)} 守卫 —— 覆盖**不经 Q 键**的其它抛出路径。
 *
 * <p>对应 Forge 侧 {@code Player#drop(ItemStack, boolean, boolean)} 里的
 * {@code ForgeHooks.onPlayerTossEvent} 与 {@code event/TemporaryCardEvents} 的
 * {@code ItemTossEvent} 兜底。Forge 的兜底语义是「取消事件 + 尽力退还进物品栏」,
 * 本实现更直接:**根本不生成掉落物**(在 HEAD 返回 null),于是不存在「退还失败即销毁」
 * 的那条丢失路径 —— 语义比原实现更强,且与 {@link DropGuardItem} 的契约一致。
 */
@Mixin(Player.class)
public abstract class PlayerItemDropGuardMixin {

    // ⚠️ method 必须写**纯方法名**（不带描述符），理由见 ServerPlayerDropGuardMixin。
    //    Player 有多个 drop 重载 ⇒ Mixin 会逐个尝试，只有签名匹配的那个会被注入，
    //    其余跳过；defaultRequire=1 只要求至少一个成功。
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void astralDice$refuseGuardedDrop(ItemStack stack, boolean dropAround, boolean noDelay,
                                              CallbackInfoReturnable<ItemEntity> cir) {
        Player self = (Player) (Object) this;
        if (stack.getItem() instanceof DropGuardItem guard && !guard.onDroppedByPlayer(stack, self)) {
            cir.setReturnValue(null);
        }
    }
}
