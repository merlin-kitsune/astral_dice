package com.merlinkitsune.astral_dice.mixin.jade;

import com.merlinkitsune.astral_dice.client.CombatPowerHud;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把 Jade 护甲条的**图标源**从原版胸甲 sprite 换成本模组的盾形防御图标
 * （NeoForge 26.1.2 线）。
 *
 * <h2>第三态从哪来</h2>
 * Jade 26.1.11 的 {@code ArmorElement#extractRenderState} 同样是「先铺底色再叠层」：
 * {@code EMPTY_ARMOR}（{@code minecraft:hud/armor_empty}）铺底、{@code ARMOR}/{@code HALF_ARMOR}
 * 叠在上面（源码 {@code snownee/jade/impl/ui/ArmorElement.java:63-79}）。把满/半换成本模组盾形图后，
 * 底色仍是原版胸甲轮廓会错配 ⇒ 第三态用的是 {@code CombatPowerHud.DEFENSE_EMPTY}
 * （与满图标逐像素同形、内芯 {@code #3D3D3D} 深灰，照原版 {@code armor_empty} 规则派生）。
 *
 * <h2>为什么注在 DisplayHelper 里</h2>
 * 三个调用点都走 {@code IDisplayHelper#blitSprite(GuiGraphicsExtractor, RenderPipeline,
 * Identifier, I, I, I, I)} → 实现 {@code snownee/jade/overlay/DisplayHelper.java:333-343}：
 * <pre>
 * sprite = IThemeHelper.get().theme().mapSprite(sprite);          // 主题重映射（本线独有）
 * graphics.blitSprite(renderPipeline, sprite, i, j, k, l, ARGB.white(opacity()));  // ← 只重定向这一句
 * </pre>
 * 在这一句上重定向：handler 形参全是原版/Blaze3D 类型（{@code GuiGraphicsExtractor} /
 * {@code RenderPipeline} / {@code Identifier}），不引入 Jade 的 {@code IDisplayHelper}；
 * 透明度（{@code color} 形参就是 {@code ARGB.white(opacity())}）与主题映射由调用方保留，
 * 我们只把绘制源换成普通贴图。非护甲 sprite 走回落分支，与原版逐字节相同。
 *
 * <h2>为什么按 path 匹配</h2>
 * 本线 {@code DisplayHelper} 在调用前做了 {@code Theme#mapSprite}，传入的 id 可能已被主题换过
 * 命名空间/前缀 ⇒ 比对 {@code sprite.getPath()} 而**不是**整个 id，主题换名后仍认得出；
 * 且 1.21.1 线没有 mapSprite，同一套 path 规则两线通用。
 */
@Mixin(targets = "snownee.jade.overlay.DisplayHelper")
public abstract class DisplayHelperArmorIconMixin {

    @Redirect(
            // 目标方法：DisplayHelper 的 4 个 int 形参重载（ArmorElement 调的就是它）
            method = "blitSprite(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                    + "Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
                    + "Lnet/minecraft/resources/Identifier;IIII)V",
            // 重定向的方法体里那一句 graphics.blitSprite(renderPipeline, sprite, i, j, k, l, color)
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
                            + "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
                            + "Lnet/minecraft/resources/Identifier;IIIII)V"))
    private static void astralDice$defenseIcons(GuiGraphicsExtractor graphics, RenderPipeline renderPipeline,
                                                Identifier sprite, int x, int y, int width, int height,
                                                int color) {
        Identifier ours = astralDice$defenseSprite(sprite);
        if (ours == null) {
            graphics.blitSprite(renderPipeline, sprite, x, y, width, height, color);
            return;
        }
        // 我们的图标是普通贴图（textures/gui/*.png），不走 sprite 图集 ⇒ 直接 blit，
        // 并把调用方算好的 ARGB（含 tooltip 淡入淡出 alpha）原样透传。
        graphics.blit(renderPipeline, ours, x, y, 0.0F, 0.0F, width, height, width, height, color);
    }

    /** 原版护甲三态 sprite → 本模组三态贴图；其余 sprite 返回 {@code null} 表示不接管。 */
    private static Identifier astralDice$defenseSprite(Identifier sprite) {
        if (sprite == null) {
            return null;
        }
        return switch (sprite.getPath()) {
            case "hud/armor_full" -> CombatPowerHud.DEFENSE_FULL;
            case "hud/armor_half" -> CombatPowerHud.DEFENSE_HALF;
            case "hud/armor_empty" -> CombatPowerHud.DEFENSE_EMPTY;
            default -> null;
        };
    }
}
