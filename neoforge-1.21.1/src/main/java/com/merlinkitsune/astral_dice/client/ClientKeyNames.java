package com.merlinkitsune.astral_dice.client;

import net.minecraft.client.KeyMapping;

/**
 * 按键「显示名」的客户端侧入口。
 *
 * <h2>为什么需要这个类</h2>
 * <p>{@code event/ModTooltipHandler} 是**双端都会加载**的类（{@code @EventBusSubscriber} 未限定
 * {@code Dist}，且 {@code ItemTooltipEvent} 在专用服务端也会派发）。若它在自己的方法体里直接
 * {@code KeyBindingSetup.ACTIVATE_SIGN_KEY}，该字段的**声明类型**（{@code net.minecraft.client.KeyMapping}）
 * 就会出现在 {@code ModTooltipHandler} 的常量池里 —— 而
 * {@code FMLEnvironment.dist == Dist.CLIENT} 这类**运行期**判断挡得住「执行」、**挡不住「符号解析」**：
 * {@code getstatic} 的字段声明类型须在方法被调用时解析，{@code catch (Throwable)} 只包住执行、不包住解析。
 * 也就是说，那种写法是「靠 JIT 内联时机」才侥幸能跑，不是保证。
 *
 * <p>把取值逻辑收到本类（只在 {@code client/} 包内、只有客户端会加载）之后，
 * {@code ModTooltipHandler} 调用的只是本类的方法名，字节码里不再出现任何客户端类型。
 *
 * <h2>调用约定</h2>
 * <p>调用方必须先做 dist 判断（{@code FMLEnvironment.dist == Dist.CLIENT}）再调用本类的方法 ——
 * 本类本身不重复判断，因为它一旦被加载就已经意味着类解析发生过了，判断放这里没有意义。
 * 提供 {@link #activateSignKey()} / {@link #cardInventoryKeyName()} 两个方法；
 * 服务端侧的回退字面量（{@code "J"} / {@code "H"}）留在各自的调用方，不在本类里兜底。
 */
public final class ClientKeyNames {

    private ClientKeyNames() {
    }

    /**
     * 立牌主动技能键的当前显示名（如 {@code J}，或玩家改键后的实际按键名）。
     * <p>取值失败时返回 {@code null}，由调用方决定回退字面量。
     */
    public static String activateSignKey() {
        return keyName(KeyBindingSetup.ACTIVATE_SIGN_KEY);
    }

    /**
     * 卡牌栏开启键的当前显示名（如 {@code H}）。
     * <p>取值失败时返回 {@code null}，由调用方决定回退字面量。
     */
    public static String cardInventoryKeyName() {
        return keyName(KeyBindingSetup.OPEN_CARD_INVENTORY_KEY);
    }

    /**
     * 读某个映射的「已翻译按键名」。
     *
     * <p>整体 try/catch 是**有意**的：本方法在 tooltip 渲染路径上被调用（可能每帧多次），
     * 任何异常都不该冒泡到渲染/事件分发层。取不到就返回 {@code null} 让调用方走回退。
     */
    private static String keyName(KeyMapping mapping) {
        try {
            return mapping.getTranslatedKeyMessage().getString();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
