package com.merlinkitsune.astral_dice.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@code EntityRenderer#getTextureLocation}（protected），供半透明渲染取贴图。
 *
 * <p><b>移植说明</b>:{@link SignTranslucencyMixin} 依赖本接口取实体贴图,fabric 线原本没有该 mixin
 * (forge / neoforge 有);本文件自 forge-1.20.1 逐字搬入并登记进 {@code astral_dice.mixins.json}
 * 的 {@code client} 段(与 forge 的位置一致)。
 */
@Mixin(EntityRenderer.class)
public interface EntityRendererInvoker<T extends Entity> {
    @Invoker("getTextureLocation")
    ResourceLocation astral$texture(T entity);
}
