package com.merlinkitsune.astral_dice.mixin.container;

import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 临时牌:容器内「丢弃」守卫(目标 {@code AbstractContainerMenu#clicked})。
 *
 * <h2>定点依据</h2>
 * {@code clicked} 是**所有**容器界面点击的唯一入口:{@code ServerGamePacketListenerImpl}
 * 在 1.21.1 实测 {@code :1718} 直接调 {@code containerMenu.clicked(slotNum, button, clickType, player)}
 * (它只做 try/catch 后转调**私有**的 {@code doClick} ⇒ mixin 只能挂在 {@code clicked} 上;
 * 1.20.1 / 26.1.2 同构)。
 *
 * <h2>要堵的两条「把临时牌丢出容器」的路径</h2>
 * <ol>
 *   <li><b>槽内丢弃</b>(GUI 里对着槽位按 Q = {@code THROW};1.21.1 实测
 *       {@code AbstractContainerMenu.java:494-498}):
 *       <pre>
 *         Slot slot3 = this.slots.get(slotId);
 *         int j1 = button == 0 ? 1 : slot3.getItem().getCount();
 *         ItemStack itemstack6 = slot3.safeTake(j1, Integer.MAX_VALUE, player);
 *         player.drop(itemstack6, true);
 *       </pre>
 *       牌**先被摘出槽位**,随后才进 {@code ItemTossEvent};本模组的
 *       {@code event/TemporaryCardEvents} 只取消实体、再**尽力退还进物品栏**
 *       ({@code Inventory#add} 失败时按既定口径**直接销毁** —— 因为「绝不落地」)。于是:
 *       ① 物品栏已满时(刚放完女王特权很常见:安全门只要求 2 个空格)临时牌**被销毁**,
 *       这就是用户实报的「装备到骰子内的临时战斗牌直接消失」;
 *       ② 即使退还成功,卡牌栏里那张也被**静默卸下**(菜单关闭时 {@code saveToDice} 随即把它从
 *       骰子里抹掉)。两种结果都违反「不可丢弃」。</li>
 *   <li><b>拖出 GUI</b>(把光标上的栈拖到界面外 = {@code PICKUP} + 伪槽位 {@code -999};
 *       1.21.1 实测 {@code :382-390}):{@code player.drop(this.getCarried(), true)} +
 *       {@code setCarried(EMPTY)} —— 光标栈不在物品栏也不在骰子里,退还同样可能撞上满包销毁。</li>
 * </ol>
 * 两处都在 HEAD 直接 {@code cancel}:什么都不会被摘出、不产生掉落物、也不进退还分支
 * (客户端对这种点击**没有**本地预测 —— 容器点击只发包、以服务端为准 ⇒ 取消即「无事发生」)。
 *
 * <p>判据只有一份:{@link TemporaryCardUtil#isTemporary(net.minecraft.world.item.ItemStack)}
 * (与 {@link SlotPlaceGuardMixin} / {@link ContainerMoveGuardMixin} 同源),
 * **不得**在本 mixin 里另写条件。
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerClickGuardMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void astralDice$blockTemporaryCardThrow(int slotId, int button, ContainerInput clickType,
                                                   Player player, CallbackInfo ci) {
        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (clickType == ContainerInput.THROW) {
            // 槽内丢弃:槽号越界(含 -1 / -999)时原版本本就无事发生,不必拦
            if (slotId < 0 || slotId >= self.slots.size()) return;
            if (TemporaryCardUtil.isTemporary(self.slots.get(slotId).getItem())) {
                ci.cancel();
            }
        } else if (clickType == ContainerInput.PICKUP && slotId == -999) {
            // 拖出 GUI 丢弃光标栈
            if (TemporaryCardUtil.isTemporary(self.getCarried())) {
                ci.cancel();
            }
        }
    }
}
