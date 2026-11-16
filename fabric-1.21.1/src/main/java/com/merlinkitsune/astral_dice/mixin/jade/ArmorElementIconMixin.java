package com.merlinkitsune.astral_dice.mixin.jade;

import com.merlinkitsune.astral_dice.client.CombatPowerHud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把 Jade 护甲条的**图标源**从原版胸甲 sprite 换成本模组的盾形防御图标
 * （Fabric 1.21.1 线，Jade 15.10.6）。
 *
 * <h2>为什么不再是 1.20.1 的 IconUI / UV 写法</h2>
 * 11.x 的 {@code ArmorElement#render} 走 UV 图集：{@code DisplayHelper.renderIcon(GuiGraphics, F, F, I, I, IconUI)}，
 * 三个图标常量是 {@code snownee.jade.overlay.IconUI} 的枚举值（全部指向 {@code textures/gui/icons.png}）。
 * **15.10.6 的 jar 里已经不存在 {@code snownee/jade/overlay/IconUI.class}**（反编译实证），改成 sprite 常量：
 * <pre>
 * helper.blitSprite(guiGraphics, EMPTY_ARMOR, x, y, 9, 9);   // ① 底色（第三态）
 * if (i &lt;= floor(armor))  helper.blitSprite(guiGraphics, ARMOR,      ...);   // ② 满
 * if (i &gt; armor &amp;&amp; i &lt; armor+1) helper.blitSprite(guiGraphics, HALF_ARMOR, ...); // ③ 半
 * </pre>
 * 三者 = {@code snownee/jade/impl/ui/ArmorElement} 的 {@code ARMOR/HALF_ARMOR/EMPTY_ARMOR}
 * = {@code minecraft:hud/armor_full|armor_half|armor_empty}。
 *
 * <h2>为什么必须同时接管第三态</h2>
 * 每个图标位都是「先铺 EMPTY 底色、再叠满/半」。只换满/半会让底色继续画原版胸甲轮廓
 * （盾形实心 + 胸甲灰底错配）⇒ 第三态用 {@code CombatPowerHud.DEFENSE_EMPTY}
 * （外形与满图标逐像素同形、内芯 {@code #3D3D3D} 深灰，派生规则照原版 {@code armor_empty}）。
 *
 * <h2>为什么注在 DisplayHelper 而不是 ArmorElement</h2>
 * 三个调用点都走 {@code IDisplayHelper#blitSprite(GuiGraphics, ResourceLocation, I, I, I, I)}
 * → 实现 {@code snownee/jade/overlay/DisplayHelper.java}：
 * <pre>
 * RenderSystem.enableBlend();
 * guiGraphics.setColor(1, 1, 1, opacity());            // ← 透明度在这里已经设好
 * guiGraphics.blitSprite(resourceLocation, i, j, k, l); // ← 只重定向这一句
 * guiGraphics.setColor(1, 1, 1, 1);
 * </pre>
 * 好处：handler 形参全是**原版类型**（{@code GuiGraphics}/{@code ResourceLocation}），不引入 Jade 的类型；
 * 透明度/混叠等前置状态由调用方原样保留；一处覆盖满/半/第三态三个调用点。
 * 非护甲 sprite（血量心等）走回落分支，与原版逐字节相同。
 *
 * <h2>为什么按 path 匹配</h2>
 * 用 {@code sprite.getPath()} 而不是整 id 比较：命名空间变化（资源包改名）时仍认得出；
 * 1.21.1 的 {@code DisplayHelper} 不做主题 sprite 重映射，路径即原样。
 *
 * <h2>两处 Fabric 专有约束（**不要**照抄 neoforge-1.21.1 的同功能 mixin）</h2>
 * <ol>
 *   <li><b>{@code method} 这里写**纯方法名**（{@code "blitSprite"}）</b>：本类 4 个 {@code blitSprite}
 *       重载里只有 6 形参那个调 {@code GuiGraphics.blitSprite(RL,IIII)}，其余 3 个调 5/9/10 形参版本,
 *       故纯名选择器不会误伤（命中数量由 {@code @At} 的 redirect 目标再收窄一层）。
 *       <br><b>勘误（2026-11-04）</b>：原注释称「Loom 只重映射纯方法名、带描述符者原样透传，
 *       所以写死描述符会永远匹配不到」——**与实测不符**。生产 jar
 *       {@code fabric-1.21.1/build/libs/astral_dice-26.4-SNAPSHOT.6+fabric_1.21.1.jar} 里本注解的
 *       {@code method} 已被 Loom 由纯名**展开**为完整描述符且类型改写成 intermediary
 *       （{@code blitSprite(Lnet/minecraft/class_332;Lnet/minecraft/class_2960;IIII)V}）。
 *       即两种写法都能正确重映射；保留纯名只是为了少写一个易错的长描述符，不是硬约束。</li>
 *   <li><b>**不要**写 {@code remap = false}</b>：{@code @At} target 里全是原版成员
 *       （{@code GuiGraphics}），**必须**经重映射才能命中 class_332。</li>
 * </ol>
 * <p>处理器写 {@code static} 是安全的：本线 Mixin 0.8.7 的 {@code checkTargetModifiers(target, false)}
 * 只拦「非 static 处理器打 static 方法」。
 */
@Mixin(targets = "snownee.jade.overlay.DisplayHelper")
public abstract class ArmorElementIconMixin {

    @Redirect(
            // ⚠️ 纯方法名 —— 描述符含原版 GuiGraphics，而 Loom 只重映射纯方法名（详见类头）
            method = "blitSprite",
            // 重定向方法体里那一句 guiGraphics.blitSprite(resourceLocation, i, j, k, l)
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
        // 我们的图标是普通贴图（textures/gui/*.png，9×9），不走 sprite 图集 ⇒ 直接 blit 到给定方块。
        // 调用方已 setColor(1,1,1,opacity()) ⇒ 这里不要再动颜色/混合状态，否则 tooltip 淡入淡出会断。
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
