package com.merlinkitsune.astral_dice.mixin.jade;

import com.merlinkitsune.astral_dice.client.CombatPowerHud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把 Jade 护甲条的**图标源**从原版胸甲 sprite 换成本模组的盾形防御图标
 * （NeoForge 1.21.1 线）。
 *
 * <h2>为什么要换第三态</h2>
 * Jade 的 {@code ArmorElement#render}（源码
 * {@code snownee/jade/impl/ui/ArmorElement.java:55-80}）对每一个图标位**先铺底再叠层**：
 * <pre>
 * helper.blitSprite(guiGraphics, EMPTY_ARMOR,  x, y, 9, 9);   // ① 底色（第三态）
 * if (i &lt;= floor(armor))  helper.blitSprite(guiGraphics, ARMOR,      ...);   // ② 满
 * if (i &gt; armor &amp;&amp; i &lt; armor+1) helper.blitSprite(guiGraphics, HALF_ARMOR, ...); // ③ 半
 * </pre>
 * 三者的常量是本类的 {@code ARMOR/HALF_ARMOR/EMPTY_ARMOR} =
 * {@code minecraft:hud/armor_full|armor_half|armor_empty}（同文件 19-21 行）。
 * 满/半图标被换成本模组的盾形图后，**底色仍是原版胸甲轮廓**，会露出「盾形实心 + 胸甲灰底」的
 * 错配 —— 这就是用户要求的「Jade 需要有第三态图标」。第三态在
 * {@code CombatPowerHud.DEFENSE_EMPTY}（外形与满图标逐像素同形、内芯 {@code #3D3D3D} 深灰，
 * 派生规则照原版 {@code armor_empty}）。
 *
 * <h2>为什么注在 DisplayHelper 里而不是 ArmorElement 里</h2>
 * 三个调用点都走 {@code IDisplayHelper#blitSprite(GuiGraphics, ResourceLocation, I, I, I, I)}
 * → 实现 {@code snownee/jade/overlay/DisplayHelper.java:425-430}：
 * <pre>
 * RenderSystem.enableBlend();
 * guiGraphics.setColor(1, 1, 1, opacity());      // ← 透明度在这里已经设好
 * guiGraphics.blitSprite(resourceLocation, i, j, k, l);   // ← 只重定向这一句
 * guiGraphics.setColor(1, 1, 1, 1);
 * </pre>
 * 在**这一句**上重定向有三个好处：
 * <ol>
 *   <li>handler 的形参全是**原版类型**（{@code GuiGraphics}/{@code ResourceLocation}），
 *       不引入 Jade 的 {@code IDisplayHelper}/{@code IconUI}；</li>
 *   <li>透明度、混叠、主题等前置状态由调用方原样保留 —— 我们只换绘制源；</li>
 *   <li>一处覆盖满/半/第三态三个调用点，不必逐个改 {@code ArmorElement}。</li>
 * </ol>
 * 非护甲 sprite（血量心、其它元素）走 handler 里的回落分支，行为与原版逐字节相同。
 *
 * <h2>为什么按 path 匹配</h2>
 * 用 {@code sprite.getPath()} 而不是整 id 比较：命名空间变化（资源包/主题改命名空间）时仍然认得出。
 * 本线（1.21.1）的 {@code DisplayHelper} **不做**主题 sprite 重映射，路径即原样；
 * 26.1.2 线才有 {@code Theme#mapSprite}，两线统一按 path 匹配即可同时覆盖。
 */
@Mixin(targets = "snownee.jade.overlay.DisplayHelper")
public abstract class DisplayHelperArmorIconMixin {

    @Redirect(
            // 目标方法：DisplayHelper 的 4 个 int 形参重载（ArmorElement 调的就是它）
            method = "blitSprite(Lnet/minecraft/client/gui/GuiGraphics;"
                    + "Lnet/minecraft/resources/ResourceLocation;IIII)V",
            // 重定向的方法体里那一句 guiGraphics.blitSprite(...)
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;"
                            + "blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private static void astralDice$defenseIcons(GuiGraphics guiGraphics, ResourceLocation sprite,
                                                int x, int y, int width, int height) {
        ResourceLocation ours = astralDice$defenseSprite(sprite);
        if (ours == null) {
            guiGraphics.blitSprite(sprite, x, y, width, height);
            return;
        }
        // 我们的图标是普通贴图（textures/gui/*.png），不走 sprite 图集 ⇒ 直接 blit。
        // 调用方已 setColor(1,1,1,opacity()) ⇒ 这里不要再动颜色状态。
        guiGraphics.blit(ours, x, y, 0.0F, 0.0F, width, height, width, height);
    }

    /** 原版护甲三态 sprite → 本模组三态贴图；其余 sprite 返回 {@code null} 表示不接管。 */
    private static ResourceLocation astralDice$defenseSprite(ResourceLocation sprite) {
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
