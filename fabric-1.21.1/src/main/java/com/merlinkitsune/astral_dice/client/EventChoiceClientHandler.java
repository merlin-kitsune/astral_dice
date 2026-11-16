package com.merlinkitsune.astral_dice.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * 大侦探立牌主动技能：「事件选择界面」的客户端侧入口（fabric：1:1 移植自 forge-1.20.1 同名文件，2026-11-01）。
 *
 * <h2>为什么需要这个类</h2>
 * <p>{@code network/EventChoiceStartMessage} / {@code EventChoiceCloseMessage} / {@code EventChoiceTickMessage}
 * 是**双端都会加载**的类 —— 本线 {@code ModNetwork#register} 同样以 {@code EventChoiceStartMessage::encode}
 * 这类方法引用建注册项，建引用时即触发这些消息类的类加载。若这些消息类的方法体里直接出现
 * {@code net.minecraft.client.Minecraft} / {@link EventChoiceScreen}（其父类
 * {@code net.minecraft.client.gui.screens.Screen}），这些**客户端专属类型就会进消息类的常量池**，
 * 专用服务端在加载该消息类时即抛
 * 「Attempted to load class net/minecraft/client/gui/screens/Screen for invalid dist DEDICATED_SERVER」
 * 直接崩服（1.21.1 侧 2026-10-11 实机确证；本移植线复用同一隔离口径）。
 *
 * <p>运行期 dist 判断（{@code FMLEnvironment.dist == Dist.CLIENT}，本线走平台垫片
 * {@code platform.fml.loading.FMLEnvironment} / {@code platform.api.distmarker.Dist}）挡得住「执行」、
 * **挡不住「符号解析」**：与 {@code client/ClientKeyNames} 完全同一口径。把界面开关逻辑收到本类
 * （只在 {@code client/} 包内、只有客户端会加载）之后，消息类的字节码里不再出现任何
 * {@code net.minecraft.client.*} 类型。
 *
 * <h2>调用约定</h2>
 * <p>调用方必须先做 dist 判断（{@code FMLEnvironment.dist == Dist.CLIENT}）再调用本类的方法 ——
 * 本类本身不重复判断，因为它一旦被加载就已经意味着类解析发生过了。
 *
 * <h2>平台差异（fabric-1.20.1）</h2>
 * <p><b>零改动</b>：本类只用到 {@code Minecraft} / {@code ResourceLocation} / {@link EventChoiceScreen}
 * 三样，全部与加载器无关；方法体与签名（{@link #open} 六参、{@link #updateCountdown} 两参、
 * {@link #closeIfToken} 一参）与 forge 蓝本逐字一致，供 3 个消息类的调用点直接对上。
 */
public final class EventChoiceClientHandler {

    private EventChoiceClientHandler() {
    }

    /** 打开（或顶替）事件选择界面；重新投递时直接顶替旧界面。 */
    public static void open(int token, ResourceLocation firstEvent, ResourceLocation secondEvent,
                            int firstDurationTicks, int secondDurationTicks, int timeoutTicks) {
        Minecraft.getInstance().setScreen(
                new EventChoiceScreen(token, firstEvent, secondEvent, firstDurationTicks, secondDurationTicks,
                        timeoutTicks));
    }

    /** 服务端权威倒计时同步：仅当当前界面就是同一 token 的选择界面时更新剩余 tick。 */
    public static void updateCountdown(int token, int remainingTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EventChoiceScreen screen && screen.token() == token) {
            screen.setServerRemainingTicks(remainingTicks);
        }
    }

    /**
     * 当前界面是本 token 的选择界面才关闭（避免误关玩家后来打开的其它界面）。
     *
     * <p>关之前先打 {@code closedByServer} 标记：服务端自己发起的关屏不该再发退出意图
     * （{@link EventChoiceScreen#removed()} 会读这个标记）。类型 + token 双重校验还挡掉了
     * 换维度时把维度切换遮罩等其它界面顶掉的情况（1.20.1 侧是 {@code ReceivingLevelScreen}，
     * 1.21.1 侧是 {@code DimensionTransitionScreen}）。
     */
    public static void closeIfToken(int token) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EventChoiceScreen screen && screen.token() == token) {
            screen.markClosedByServer();
            mc.setScreen(null);
        }
    }
}
