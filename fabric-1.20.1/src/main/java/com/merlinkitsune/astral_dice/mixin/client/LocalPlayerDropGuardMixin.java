package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 临时牌:客户端 Q 键守卫(绿洲女王 nardis「女王特权」)。
 *
 * <h2>为什么必须补这一道(只靠服务端拒绝不够)</h2>
 * 服务端 {@code ServerPlayer#drop(boolean)} 的**第一件事**就是问物品能不能被丢
 * (1.21.1 / 1.20.1 / 26.1.2 三线**字节码同形**:先读选中栈 → 调
 * {@code ItemStack#onDroppedByPlayer(Player)} → 为假直接跳出,**之后才**走
 * {@code Inventory#removeFromSelected})⇒ 临时战斗牌({@code CardItem#onDroppedByPlayer} 覆写)
 * 在服务端**根本不会被丢**。
 *
 * <p>但**客户端**的 Q 键路径完全不问这件事 —— {@code LocalPlayer#drop(boolean)} 三线同形:
 * <pre>
 *   ItemStack itemstack = this.getInventory().removeFromSelected(fullStack);   // 先本地删掉
 *   this.connection.send(new ServerboundPlayerActionPacket(...));              // 再发包
 *   return !itemstack.isEmpty();
 * </pre>
 * 先本地移除、再发包,且客户端**从不**产生掉落物实体(实体只在服务端
 * {@code CommonHooks#onPlayerTossEvent} 的 {@code addFreshEntity} 里被创建)。于是临时牌按 Q 的
 * 完整表现是:客户端格子被清空 + 服务端静默拒绝 —— 服务端槽位没变 ⇒ {@code remoteSlots} 与真实
 * 内容仍相等 ⇒ {@code broadcastChanges} **永不**补发 {@code ClientboundContainerSetSlotPacket}
 * ⇒ 客户端会长期显示「牌没了、地上也没有掉落物」,直到某次全量同步(重登 / 重开容器界面)。
 * 这正是用户实报的「战斗牌能被按Q丢弃且不会有掉落物」。
 *
 * <p>为什么用户只在**战斗牌**上看到:效果牌根类
 * {@link com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem} 当时**没有**覆写
 * {@code onDroppedByPlayer} ⇒ 服务端**会**走完丢弃流程 → {@code ItemTossEvent} 被
 * {@code event/TemporaryCardEvents} 取消并把栈**退还进物品栏** ⇒ 表现为「牌跳到别的格子」
 * 而非「凭空消失」(两者都不符合「不可丢弃」的语义,本批一并修正:效果牌补上覆写 + 本 mixin
 * 让客户端连包都不发)。
 *
 * <h2>口径:只拦临时牌(刻意比服务端补丁窄)</h2>
 * 服务端补丁对**所有**物品问 {@code onDroppedByPlayer};本 mixin 只对
 * {@link TemporaryCardUtil#isTemporary(ItemStack)} 为真的牌拦,判据与全仓其它临时牌守卫
 * **同一份**。不在客户端替第三方模组调用其 {@code onDroppedByPlayer}(客户端非权威,
 * 且不越界干涉它模组的物品语义)。
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerDropGuardMixin {

    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void astralDice$refuseTemporaryCardDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        ItemStack selected = self.getInventory().getSelected();
        if (!TemporaryCardUtil.isTemporary(selected)) return;
        // 与 ServerPlayer#drop(boolean) 的拒绝同语义:不移除、不发包、不产掉落物
        cir.setReturnValue(false);
    }
}
