package com.merlinkitsune.astral_dice.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 {@code EntityRenderer#getTextureLocation}（protected），供半透明渲染取贴图。 */
@Mixin(EntityRenderer.class)
public interface EntityRendererInvoker<T extends Entity> {
    @Invoker("getTextureLocation")
    ResourceLocation astral$texture(T entity);
}
