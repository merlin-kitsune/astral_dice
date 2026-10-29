package com.merlinkitsune.astral_dice.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * 大侦探立牌主动技能：「事件选择界面」的客户端侧入口。
 *
 * <h2>为什么需要这个类</h2>
 * <p>{@code network/EventChoiceStartPayload} 与 {@code network/EventChoiceClosePayload} 是**双端都会加载**
 * 的类 —— NeoForge 在 {@code ModPayloads#register} 里对 {@code EventChoiceStartPayload.TYPE} /
 * {@code EventChoiceStartPayload::handle} 建方法引用时就会触发它们的类加载。若这两个 payload 类的方法体里
 * 直接出现 {@code net.minecraft.client.Minecraft} / {@code EventChoiceScreen}（其父类
 * {@code net.minecraft.client.gui.screens.Screen}），这些**客户端专属类型就会进 payload 类的常量池**，
 * 专用服务端在 {@code RuntimeDistCleaner.processClassWithFlags} 阶段加载该 payload 类时即抛
 * 「Attempted to load class net/minecraft/client/gui/screens/Screen for invalid dist DEDICATED_SERVER」
 * 直接崩服（2026-10-11 实机确证）。
 *
 * <p>运行期 dist 判断（{@code FMLEnvironment.dist == Dist.CLIENT}）挡得住「执行」、**挡不住「符号解析」**：
 * 与 {@code client/ClientKeyNames} 完全同一口径。把界面开关逻辑收到本类（只在 {@code client/} 包内、
 * 只有客户端会加载）之后，两个 payload 类的字节码里不再出现任何 {@code net.minecraft.client.*} 类型。
 *
 * <h2>调用约定</h2>
 * <p>调用方必须先做 dist 判断（{@code FMLEnvironment.dist == Dist.CLIENT}）再调用本类的方法 ——
 * 本类本身不重复判断，因为它一旦被加载就已经意味着类解析发生过了。
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
     * （{@code EventChoiceScreen#removed()} 会读这个标记）。类型 + token 双重校验还挡掉了
     * 换维度时把 {@code ReceivingLevelScreen} 等其它界面顶掉的情况。
     */
    public static void closeIfToken(int token) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EventChoiceScreen screen && screen.token() == token) {
            screen.markClosedByServer();
            mc.setScreen(null);
        }
    }
}
