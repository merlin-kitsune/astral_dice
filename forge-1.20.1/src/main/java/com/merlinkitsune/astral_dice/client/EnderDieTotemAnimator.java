package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 末影骰子不死图腾动画(客户端):复刻原版实体事件 35
 * (图腾粒子 + 不死图腾音效 + 手持高亮动画),但高亮图标替换为末影骰子。
 */
public final class EnderDieTotemAnimator {

    private EnderDieTotemAnimator() {
    }

    public static void play(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        Entity entity = level.getEntity(entityId);
        if (entity == null) return;

        // 图腾粒子(与原版一致)
        mc.particleEngine.createTrackingEmitter(entity, ParticleTypes.TOTEM_OF_UNDYING, 30);
        // 不死图腾音效(与原版一致)
        level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.TOTEM_USE, entity.getSoundSource(), 1.0F, 1.0F, false);
        // 手持高亮动画:只有本人看到,图标使用末影骰子
        if (entity == mc.player) {
            mc.gameRenderer.displayItemActivation(new ItemStack(ModItems.ENDER_DICE.get()));
        }
    }
}
