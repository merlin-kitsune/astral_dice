package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.combat.ShootingStarManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 「飞星」筹码（紫色飞星 / 金色飞星）的**物品类**。
 *
 * <p>两枚筹码共用本类：物品层只负责「被装备着」这件事 —— 每 tick 把玩家交给
 * {@link ShootingStarManager#tick(ServerPlayer)}。二者的差异（1 点 / 2 点基础伤害、
 * 金色对精英/Boss 的额外星光层数伤害、粒子颜色）**全部集中在执行器里**，
 * 且触发逻辑必须**共享同一 10 秒冷却**（用户裁决）—— 所以不该、也不能拆成两份实现。
 *
 * <p>重复调用安全：两枚同时装备时 {@code curioTick} 会被调用两次，
 * 执行器在首次触发时即写入共享冷却，第二次自然早退（幂等）。
 */
public class ShootingStarChipItem extends BaseChipItem {

    public ShootingStarChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof ServerPlayer player) {
            ShootingStarManager.tick(player);
        }
    }
}
