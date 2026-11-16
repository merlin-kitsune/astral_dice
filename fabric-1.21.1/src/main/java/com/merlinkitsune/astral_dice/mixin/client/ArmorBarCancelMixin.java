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
 * <h2>注入点是 {@code renderArmor},不是 {@code renderPlayerHealth}(1.21.1 实测)</h2>
 * 1.20.1 的护甲渲染**内联在** {@code Gui#renderPlayerHealth} 里;1.21.1 已把它拆成独立的
 * **私有静态**方法:
 * <pre>
 * private static void renderArmor(GuiGraphics, Player, int, int, int, int)
 * </pre>
 * 取证({@code javap -p -c net.minecraft.client.gui.Gui},1.21.1 Loom named 产物):
 * {@code renderPlayerHealth} 体内只剩一句 {@code invokestatic renderArmor},
 * 而 {@code Player#getArmorValue()} 的**唯一**调用点在 {@code renderArmor} 内(字节码偏移 1)。
 * <p>仍打 {@code renderPlayerHealth} 会在 1.21.1 上**硬失败**:
 * {@code InjectionError: Critical injection failure ... (0/1) succeeded. Scanned 0 target(s)}
 * (实测崩溃报告 {@code crash-2026-11-05_12.48.14-client.txt})。
 *
 * <h2>为什么是 {@code @Redirect} 而不是事件</h2>
 * Fabric 没有 overlay 派发机制(Forge 线走 {@code RenderGuiOverlayEvent.Pre} 取消
 * {@code VanillaGuiOverlay.ARMOR_LEVEL})。原版「该不该画护甲、画几格」的唯一判据就是
 * {@code getArmorValue()} 的返回值 ⇒ 重定向它返回 {@code 0},护甲整段自然跳过,
 * **不触碰任何其它逻辑**(血量/饱食/空气/坐骑血量都走各自的 {@code getFoodData}
 * / {@code getMaxAirSupply},不受影响)。
 *
 * <h2>为什么不重定向 blit</h2>
 * 护甲图标通过 {@code GuiGraphics#blitSprite} 绘制,而该方法在同一方法体内还被
 * 血量/饱食/空气共用 ⇒ 重定向它必须按调用序号区分(脆弱),任何一处序号漂移都会静默错杀。
 *
 * <h2>处理器必须是 static</h2>
 * {@code renderArmor} 是静态方法;Mixin 只允许「静态处理器 → 实例方法」,
 * **不允许**「实例处理器 → 静态方法」⇒ handler 声明为 {@code private static}。
 *
 * <h2>OAB(护甲上限突破)</h2>
 * 本注入**只**压制原版路径;OAB 若装了,它占用的护甲行属第三方 overlay,需另行处理
 * (见 KNOWN-ISSUES 的 fabric 条目)。
 *
 * <p>方法名使用**纯方法名**(不含描述符):Loom 只在 {@code remapJar} 阶段重映射
 * **纯方法名**形式的 {@code method},带描述符者会原样透传 ⇒ 生产环境注入失败。
 */
@Mixin(Gui.class)
public abstract class ArmorBarCancelMixin {

    /**
     * 把原版护甲数量读成 0 ⇒ {@code renderArmor} 里的护甲绘制分支不执行。
     *
     * @param player 原调用的接收者(注入点参数就是它,不可省略)
     * @return 恒 0
     */
    @Redirect(method = "renderArmor",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getArmorValue()I"))
    private static int astralDice$suppressVanillaArmorBar(Player player) {
        return 0;
    }
}
