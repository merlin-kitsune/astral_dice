package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * 骇客立牌(nancy_lu)主动"远程侵入"的客户端渲染抑制(游戏事件总线,仅客户端)。
 *
 * <p>原版隐身只隐藏实体本体:1.21.1 {@code LivingEntityRenderer.render} 用
 * {@code isBodyVisible}(= !isInvisible())判断本体,但渲染层不检查隐身
 * ({@code HumanoidArmorLayer.render} 与 Curios 的 {@code CuriosLayer.render} 都无 isInvisible 判断),
 * 所以隐身玩家的盔甲/手持物/饰品依然可见。本类因此:
 * <ul>
 *   <li>{@link RenderPlayerEvent.Pre}:在 {@code PlayerRenderer.render} 进入 {@code super.render}
 *       之前取消整次渲染,连带取消全部渲染层(盔甲/手持/Curios/披风/鞘翅/箭/名牌/影子);</li>
 *   <li>{@link RenderHandEvent}:该事件由 {@code ClientHooks.renderSpecificFirstPersonHand} 在
 *       {@code ItemInHandRenderer.renderHandsWithItems} 中派发,取消后第一人称手持物不再渲染
 *       (原版仅对手臂与地图手做了 isInvisible() 抑制,非空手持物不受隐身影响)。</li>
 * </ul>
 *
 * <p>仅对本机玩家生效:nancy_lu_hidden_until 在 1.20.1 侧的客户端缓存按附件名保存(只承载
 * 本地玩家数据),且"不影响其他玩家"要求不抑制其他人的渲染。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public class NancyLuClientEvents {

    private NancyLuClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre<?> event) {
        // 26.1.2:渲染全面改为「render state」模型,RenderPlayerEvent.Pre 不再持有实体
        // (旧的 getEntity() 已删除),改为经 getRenderState() 取本次渲染的状态对象。
        // ⚠️ 形参必须写成 `Pre<?>`(通配符):裸 `Pre` 会连**父类**的类型参数一起擦除,
        //    getRenderState() 退化成 LivingEntityRenderState,取不到 AvatarRenderState#id。
        // AvatarRenderState#id 由原版 AvatarRenderer 写入 = entity.getId()(AvatarRenderer.java:198),
        // 故用它与本机玩家 id 比对即可精确锁定「本机玩家」。
        LocalPlayer self = Minecraft.getInstance().player;
        if (self == null) return;
        if (event.getRenderState().id != self.getId()) return;
        if (NancyLuSignItem.isHiddenClient(self)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && NancyLuSignItem.isHiddenClient(player)) {
            event.setCanceled(true);
        }
    }
}
