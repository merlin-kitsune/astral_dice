package com.merlinkitsune.astral_dice.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 取消**原版护甲条**的绘制,把那一行让给本模组的「防御力条」
 * ({@code client/CombatPowerHud} 的 {@code DEFENSE_OVERLAY})。
 *
 * <h2>为什么是 {@code @Redirect} 而不是事件</h2>
 * Forge 线走的是 {@code RenderGuiOverlayEvent.Pre}(取消 {@code VanillaGuiOverlay.ARMOR_LEVEL}
 * 与 OAB 的同名图层)。1.20.1 的 Fabric **没有** overlay 派发机制,而原版 {@code Gui} 也**没有**
 * 独立的 {@code renderArmor} 方法 —— 护甲渲染**内联在** {@code renderPlayerHealth} 里。
 * <p>经字节码核实({@code javap -c},1.20.1 named 产物):{@code renderPlayerHealth} 方法体内
 * {@code Player#getArmorValue()} **恰好只出现 1 次**(字节码偏移 320),
 * 其返回值即原版「该不该画护甲、画几格」的唯一判据。
 * ⇒ 重定向它返回 {@code 0},原版护甲整段自然跳过,**不触碰任何其它逻辑**
 * (血量/饱食/空气/坐骑血量都走各自的 {@code getFoodData}/{@code getMaxAirSupply},不受影响)。
 *
 * <h2>为什么不重定向 blit</h2>
 * 护甲图标通过 {@code GuiGraphics#blit} 绘制,而该方法被同一方法体里的血量/饱食/空气共用
 * ⇒ 重定向它必须按调用序号区分(脆弱),且任何一处序号漂移都会静默错杀。
 *
 * <h2>OAB(护甲上限突破)</h2>
 * Forge 线额外取消了 {@code overloadedarmorbar:overloadedarmorbar} 图层。Fabric 侧若装了 OAB,
 * 它同样占用护甲行 —— 本注入**只**压制原版路径,OAB 属第三方 overlay,需另行处理
 * (见 KNOWN-ISSUES 的 fabric 条目)。
 *
 * <p>方法名使用**纯方法名**(不含描述符):Loom 1.14 起只在 {@code remapJar} 阶段重映射
 * **纯方法名**形式的 {@code method},带描述符者会原样透传 ⇒ 生产环境注入失败。
 */
@Mixin(Gui.class)
public abstract class ArmorBarCancelMixin {

    /**
     * 把原版护甲数量读成 0 ⇒ {@code renderPlayerHealth} 里的护甲绘制分支不执行。
     *
     * @param player 原调用的接收者(注入点参数就是它,不可省略)
     * @return 恒 0
     */
    @Redirect(method = "renderPlayerHealth",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getArmorValue()I"))
    private int astralDice$suppressVanillaArmorBar(Player player) {
        return 0;
    }
}
