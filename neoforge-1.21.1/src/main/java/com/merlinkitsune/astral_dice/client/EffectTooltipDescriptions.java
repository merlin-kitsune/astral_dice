package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.GatherEffectScreenTooltipsEvent;

import java.util.List;

/**
 * 状态效果「注释」渲染:把 {@code effect.astral_dice.<id>.description} 追加到
 * **物品栏「效果」面板**（{@code EffectRenderingInventoryScreen}）悬停某个效果图标时弹出的 tooltip 末尾。
 *
 * <p><b>为什么需要这个类</b>:本模组的语言文件里早已写好 21 条 {@code effect.astral_dice.*.description},
 * 但原版效果 tooltip 只由「效果名 + 剩余时长」两行构成 —— 描述文本<b>没有任何渲染通道</b>,
 * 玩家在游戏里永远看不到（此前只有狂暴 / 岿然不动 / 以毒攻毒三张<b>效果牌物品</b>的 tooltip 读过描述键）。
 *
 * <p><b>平台口径</b>:NeoForge 提供了官方扩展点 {@code GatherEffectScreenTooltipsEvent}
 * （原版 {@code renderEffects} 里 {@code renderTooltip} 调用之前派发,{@code getTooltip()} 返回可变列表）
 * ⇒ 无需 Mixin。1.20.1 的 Forge <b>没有</b>该事件,那边走 Mixin（见
 * {@code mixin/client/EffectRenderingInventoryScreenMixin}）。
 *
 * <p><b>只在键存在时追加</b>:用 {@code I18n.exists} 判定,未写描述的效果不加空行、不留占位。
 * 文案颜色固定 {@link ChatFormatting#GRAY}(与物品 tooltip 的补充说明段同色)。
 *
 * <p>本类 {@code @EventBusSubscriber(value = Dist.CLIENT)} ⇒ 专用服务端整体不加载,
 * 其中的 {@code I18n} / 事件类都是客户端专属类型。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
public final class EffectTooltipDescriptions {

    private EffectTooltipDescriptions() {
    }

    @SubscribeEvent
    public static void onGatherEffectTooltips(GatherEffectScreenTooltipsEvent event) {
        append(event.getTooltip(), event.getEffectInstance());
    }

    /** 追加描述行(仅当对应 lang 键存在;否则原样返回,不做任何改动) */
    public static void append(List<Component> tooltip, MobEffectInstance instance) {
        if (tooltip == null || instance == null) return;
        String id = effectId(instance.getEffect().value());
        if (id == null) return;
        String key = "effect." + id + ".description";
        if (!I18n.exists(key)) return;
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    /**
     * 本模组效果的 {@code "namespace.path"};非本模组注册的效果返回 {@code null}。
     *
     * <p>用 {@link ModEffects#ALL}（{@code DeferredRegister#getEntries()} 的活视图）反查,
     * 而不是读注册表的 key —— 后者在三个 MC 版本上返回类型不同（{@code ResourceLocation} /
     * {@code Identifier}）,反查写法三线一致且不依赖任何注册表 API。
     */
    private static String effectId(MobEffect effect) {
        for (var holder : ModEffects.ALL) {
            if (holder.value() == effect) {
                var loc = holder.getId();
                return loc.getNamespace() + "." + loc.getPath();
            }
        }
        return null;
    }
}
