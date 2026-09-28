package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 临时牌的**丢弃兜底守卫**(绿洲女王 nardis 主动「女王特权」)。
 *
 * <h2>为什么还需要这一道(已经有 {@code CardItem#onDroppedByPlayer} 拒绝)</h2>
 * {@code onDroppedByPlayer} 只覆盖「走 {@code ServerPlayer#drop(boolean)}」的 Q 键路径
 * (1.20.1 实测:第一行判定在 {@code ServerPlayer.java:1726},早于 {@code :1728 removeFromSelected})。
 * 而 {@code ItemTossEvent} 覆盖的是**更宽的集合**:① Q 键、② **把栈拖拽到物品栏 GUI 之外**
 * (原版 javadoc 原话 "or drag-n-drops a stack of items outside the inventory GUI screens")、
 * ③ 其它模组直接调 {@code Player#drop(ItemStack, boolean)} 的路径。临时牌的语义是
 * 「只能在物品栏和手中」,这些口子全部要堵住。
 *
 * <h2>1.20.1 时序与「尽力退还」</h2>
 * 1.20.1 的抛出链是 {@code ServerPlayer#drop(boolean)}
 * → {@code ForgeHooks.onPlayerTossEvent} → 先 {@code player.drop(item,false,name)} 造出实体,
 * **再**在 {@code ForgeHooks.java:392-394} 抛出本事件;取消 ⇒ 直接 {@code return null},
 * {@code :396-397} 的 {@code addFreshEntity} **不执行** ⇒ 该实体**永不**进入世界(不落地)。
 * ⚠️ 与 1.21.1 一致:取消**不会把物品退回背包**(javadoc 明写 "will not prevent them being
 * removed from the inventory")⇒ 本处理器必须自己把栈**尽力退还**;退还失败(背包满)时**直接销毁**,
 * 绝不 {@code player.drop(...)} —— 临时牌不得进入世界。
 *
 * <p>{@code stack.setCount(0)} 是双保险:即使有其它处理器在取消后仍拿到同一个实体,它的栈也已经是空的。
 *
 * <h2>1.20.1 平台适配</h2>
 * 事件类与订阅注解来自 Forge({@code net.minecraftforge.event.entity.item.ItemTossEvent} /
 * {@code @Mod.EventBusSubscriber});{@code getEntity()}/{@code getPlayer()}/{@code setCanceled}
 * 的语义与 1.21.1 的 NeoForge {@code ItemTossEvent} **逐条相同**。
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class TemporaryCardEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryCardEvents.class);

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemEntity entity = event.getEntity();
        if (entity == null) return;
        ItemStack stack = entity.getItem();
        if (!TemporaryCardUtil.isTemporary(stack)) return;
        // 临时牌:取消抛出(不进入世界)
        event.setCanceled(true);
        ItemStack toReturn = stack.copy();
        stack.setCount(0);
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (!player.getInventory().add(toReturn)) {
            // 背包放不下 ⇒ 销毁(需求:临时牌只能在物品栏和手中,不得掉落进世界)
            LOGGER.warn("[Astral Dice][TemporaryCard] 背包放不下,临时牌已销毁(不落地): player={} item={}",
                    player.getName().getString(), toReturn);
        }
    }
}
