package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ClientAstralData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端会话边界清理:断开连接时清空附件同步缓存。
 *
 * <p>1.20.1 的附件同步是手写 shim(见 {@link ClientAstralData}),缓存为**静态字段**、
 * 不随客户端玩家实体重建。若不清空,上一会话(或上一个世界)的残留值会一直生效到该键
 * 被再次同步为止——表现为 tooltip 显示上一局的计数,而服务端早已归零。
 * 1.21.1 用原生附件、客户端数据随加入世界重建,无此问题。
 *
 * <p>本清理是「快照对全部 synced 键下发显式默认值」({@code ModNetwork.syncSnapshot})
 * 之外的兜底:两者互不依赖,任一单独存在即可覆盖大部分场景(快照覆盖登录/重生/切维度,
 * 本清理覆盖"以后再也不写该键"的离线残留)。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public class ClientSessionEvents {

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAstralData.clear();
    }
}
