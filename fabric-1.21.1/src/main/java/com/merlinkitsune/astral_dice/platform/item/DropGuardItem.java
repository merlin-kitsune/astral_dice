package com.merlinkitsune.astral_dice.platform.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 「拒绝丢弃」契约 —— 替代 Forge/NeoForge 给 {@code Item} 打的
 * {@code onDroppedByPlayer(ItemStack, Player)} 补丁方法。
 *
 * <h2>为什么必须自建</h2>
 * 原版 1.20.1 的 {@code Item} **没有**该方法(javap 实证),Forge 在
 * {@code ServerPlayer#drop(boolean)} 的第一件事就插入了
 * {@code selected.onDroppedByPlayer(this)} 判定。Fabric 侧没有这条补丁,
 * 故把契约抽成本接口,由服务端 mixin 在**同一时机**询问:
 * <ul>
 *   <li>{@code mixin/server/ServerPlayerDropGuardMixin} — 注 {@code ServerPlayer#drop(boolean)}
 *       的 HEAD(早于 {@code removeFromSelected},⇒ 拒绝时物品**留在原槽**);</li>
 *   <li>{@code mixin/PlayerItemDropGuardMixin} — 注 {@code Player#drop(ItemStack, boolean, boolean)}
 *       的 HEAD,覆盖不经 Q 键的其它抛出路径(对应 Forge 侧的
 *       {@code ForgeHooks.onPlayerTossEvent} + {@code ItemTossEvent} 兜底)。</li>
 * </ul>
 * 客户端 Q 键另由 {@code mixin/client/LocalPlayerDropGuardMixin} 拦截
 * (客户端是「先删本地栈、再发包」,不拦就会表现为「牌凭空消失」)。
 */
public interface DropGuardItem {

    /**
     * @return {@code false} = 拒绝丢弃(物品留在原地、不产生掉落物实体)
     */
    boolean onDroppedByPlayer(ItemStack stack, Player player);
}
