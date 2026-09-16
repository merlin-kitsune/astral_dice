package com.merlinkitsune.astral_dice.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 栏位校验与装备工具:立牌/筹码/骰子只能放入各自原本的饰品栏(由 Curios 物品标签与
 * canEquip 限制,见 BaseSignItem/DiceCurioItem/BaseChipItem),
 * 提供下蹲右键自动装备与重复装备限制的通用逻辑。
 */
public final class CurioSlotUtil {
    private CurioSlotUtil() {
    }

    // 是否已装备了与给定物品相同的物品(遍历玩家全部 Curios 槽位)
    // 排除"与传入栈引用相同"的槽位物品:Curios 对已装备物品重新校验 canEquip 时,
    // 传入的栈就是槽位中的栈本身,若不排除会误判"重复装备"导致物品被 Curios 弹出。
    public static boolean hasSameItemEquipped(LivingEntity entity, ItemStack stack) {
        var curios = CuriosApi.getCuriosInventory(entity);
        if (curios.isEmpty()) return false;
        var handler = curios.get();
        var curiosMap = handler.getCurios();
        for (var key : curiosMap.keySet()) {
            var stacks = curiosMap.get(key).getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                var s = stacks.getStackInSlot(i);
                if (!s.isEmpty() && s != stack && s.is(stack.getItem())) {
                    return true;
                }
            }
        }
        return false;
    }

    // 下蹲右键自动装备:将手中物品放入指定饰品栏的第一个空槽位(仅服务端执行)
    public static InteractionResult tryAutoEquip(Player player, ItemStack stack, String slotId) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // 立牌/筹码需要先佩戴骰子
        if (!"dice".equals(slotId) && !hasDiceEquipped(player)) {
            if (player instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp,
                        new ActionBarPayload(Component.translatable("msg.astral_dice.need_dice")
                                .withStyle(ChatFormatting.RED), GameplayConstants.ACTIONBAR_DURATION_TICKS));
            }
            return InteractionResult.FAIL;
        }

        // 重复装备限制:同类型饰品已装备时不允许自动装备
        if (hasSameItemEquipped(player, stack)) {
            return InteractionResult.FAIL;
        }
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) {
            return InteractionResult.PASS;
        }
        var handlerOpt = curios.get().getStacksHandler(slotId);
        if (handlerOpt.isEmpty()) {
            return InteractionResult.PASS;
        }
        var handler = handlerOpt.get();
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStacks().getStackInSlot(i).isEmpty()) {
                handler.getStacks().setStackInSlot(i, stack.copy());
                stack.shrink(1);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    // 是否为"玩家/系统真实移除该饰品"(2026-09-15 用户裁决:仅真实移除才清理,
    // Curios 自身原因在槽位内触发的回调不清理,否则累计值会被反复清零)。
    //
    // —— 参数语义(两加载器缓存 jar 反编译核实,非猜测)——
    // ICurioItem.onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack):
    //   ItemizedCurioCapability.onUnequip(ctx, newStack) 的实现是
    //     curioItem.onUnequip(ctx, newStack /*aload_2*/, this.getStack() /*getStack()*/)
    //   ⇒ 第 2 参 = 调用方交给 ICurio.onUnequip(ctx, X) 的那个 X("将要占用槽位的栈");
    //     第 3 参 = 该 ICurio 能力自己持有的栈 = **被卸下的那件饰品**(接口 LocalVariableTable 亦命名为 stack)。
    //   故"被卸下的那件"只能取第 3 参;旧实现把第 2 参当成被卸下的物品是绑定错误。
    //
    // —— 三个调用点(jar 内全部调用都只传 X,无第四处)——
    //   ① CuriosEventHandler 的 tick:闸门为 `!ItemStack.matches(当前槽位栈, getPreviousStackInSlot(i) 快照)`,
    //      随后 `prevCurio.onUnequip(ctx, 当前槽位栈)`、`currentCurio.onEquip(ctx, 快照)`,
    //      末尾 `setPreviousStackInSlot(i, 当前栈.copy())` 刷新快照。
    //      ⇒ 真实卸下时第 2 参 = EMPTY;换装 A→B 时第 2 参 = B(与被卸下的 A 不是同一物品)。
    //   ② CurioStacksHandler.loseStacks:`getCurio(槽位栈).onUnequip(ctx, ItemStack.EMPTY)`
    //      (回调之后才 setStackInSlot(i, EMPTY))⇒ 第 2 参 = EMPTY。
    //   ③ CPacketDestroy / CuriosServerPayloadHandler.handleDestroyPacket(玩家销毁饰品):
    //      `getCurio(槽位栈).onUnequip(ctx, 同一个槽位栈)` ⇒ 第 2 参 = 第 3 参(同物品同实例)。
    //
    // —— 为什么"同物品"这一支不能删(可达性证据)——
    //   ① 的闸门是 ItemStack.matches(含 NBT 比较:1.20.1 为 item+count+tag),而本模组自己会在
    //   **装备中的槽位栈**上写数据组件(如 PadmanSignItem.onCurioTick 写 PADMAN_*、JasmineSignItem 写移动累计、
    //   MisakiSignItem.handleUse 写剑气);Curios 在同一轮迭代里**先**调 curioTick 再**才**做 matches 比较
    //   ⇒ 写入当 tick 就会产生一次 `第2参=当前槽位栈(非空)`、`第3参=上一刻快照(同物品)` 的回调,
    //   而物品仍安稳在槽位中——属"Curios 自身原因"那一类;若在此清理,刚写入的累计值会被自己反复清零
    //   (正是旧注释警告的"治愈点数等累计值被反复清零")。故:同物品且第 3 参非空 ⇒ 不清理。
    //   (审计所称"重载不可达"仅对**同一个栈实例**成立——matches 相等时根本不回调;
    //     "同一物品、组件被改动"这一支可达,见上。)
    //
    // 结论:第 3 参为空 ⇒ 无可清理对象(也杜绝把组件写进 ItemStack.EMPTY 全局单例);
    //       第 2 参为空(真实卸下)或与被卸下者不是同一物品(换装)⇒ 真实移除 ⇒ 清理;
    //       第 2 参非空且与被卸下者同一物品 ⇒ Curios 在槽位内触发的回调 ⇒ 不清理。
    // 已知残留缺口:③ 销毁路径第 2/3 参同物品同实例,本判据按"不清理"处理——与改动前行为一致
    //       (旧实现同样跳过),且物品随即被销毁,仅"玩家级清理"被跳过。
    public static boolean isIntentionalUnequip(ItemStack newStack, ItemStack removedStack) {
        // 被卸下的那件本身为空 ⇒ 没有可清理的对象,不算真实移除
        // (同时从源头杜绝"对 ItemStack.EMPTY 调 set 污染全局单例"的问题)
        if (removedStack == null || removedStack.isEmpty()) return false;
        // newStack 为空(① 真实卸下 / ② loseStacks)⇒ 真实移除
        if (newStack == null || newStack.isEmpty()) return true;
        // 与第 3 参同一物品 ⇒ ① 槽位内组件变更引起的回调(物品仍在槽位)⇒ 不清理;换成别的物品(换装)⇒ 清理
        return !newStack.is(removedStack.getItem());
    }

    // 仅在"玩家有意卸除"时执行动作(判据见 isIntentionalUnequip;排除 Curios 重载场景,防止累计值被反复清零)
    public static void runOnIntentionalUnequip(ItemStack newStack, ItemStack removedStack, LivingEntity entity,
                                               java.util.function.Consumer<Player> action) {
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (isIntentionalUnequip(newStack, removedStack)) {
            action.accept(player);
        }
    }
    // 是否佩戴了任意骰子
    private static boolean hasDiceEquipped(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(DiceCurioItem::isDiceItem).isPresent();
    }
}
