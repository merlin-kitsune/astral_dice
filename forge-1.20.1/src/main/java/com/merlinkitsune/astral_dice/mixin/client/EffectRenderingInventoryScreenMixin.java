package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.20.1 侧的两项效果面板改造:
 *
 * <h2>① 等级角标数字化</h2>
 * 将原版效果等级角标由「罗马数字、amplifier ≤ 9(等级 ≤ 10)」改为「阿拉伯数字、amplifier ≤ 99(等级 ≤ 100)」。
 * 与 neoforge-1.21.1 的同名 Mixin 保持一致:覆盖物品栏效果面板标签与悬浮 tooltip 两处的等级角标。
 * 1.20.1 差异:{@code MobEffectInstance#getEffect()} 直接返回 {@code MobEffect}(无 Holder 包装)。
 * 方法引用使用 Mojmap 名,由 Mixin Booster 在运行时重映射为 SRG。
 *
 * <h2>② 状态效果「注释」渲染</h2>
 * 把 {@code effect.astral_dice.<id>.description} 追加到悬浮 tooltip 末尾。
 * <b>为什么这里必须用 Mixin</b>:NeoForge 有官方事件 {@code GatherEffectScreenTooltipsEvent}
 * (1.21.1 / 26.1.2 那两线用它,见 {@code client/EffectTooltipDescriptions}),
 * 而 <b>Forge 1.20.1 没有这个事件</b>(官方 1.20.1 源码 {@code renderEffects} 里 tooltip 构建段
 * 未被打过 patch,已用 {@code javap -c} 核实:只有 {@code getEffectName} /{@code formatDuration} /
 * {@code List.of} / {@code GuiGraphics.renderTooltip} 四个调用,无任何 hook)。
 *
 * <p><b>注入手法(双 @Redirect + 一个 ThreadLocal)</b>:{@code renderEffects} 的 tooltip 是局部构造的
 * {@code List.of(getEffectName(inst), formatDuration(inst, 1.0F))},既拿不到 {@code inst} 的可注入参数,
 * 也不想依赖 LVT(LocalCapture)这种一改上游就崩的脆弱定位。利用 Java「实参从左到右求值」的保证:
 * {@code formatDuration} 一定先于 {@code List.of} 执行 ⇒ 前者把 {@code inst} 记进 ThreadLocal,
 * 后者取出并改写列表。两处都用 {@code require = 2}(至少命中 1 次)⇒ 上游签名一旦变化会<b>启动即报错</b>,
 * 而不是被 Mixin 静默摘掉(本仓既有教训)。
 *
 * <p>1.20.1 的 {@code I18n.exists} 与 1.21.1 同名同签名(已核实 {@code net.minecraft.client.resources.language.I18n}),
 * 故「只在键存在时追加」的口径三线一致。
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectRenderingInventoryScreenMixin {

    /**
     * 正被渲染 tooltip 的效果实例(仅在 {@code renderEffects} 的一次 tooltip 构建内有效)。
     * 用 ThreadLocal 而非普通静态字段:与渲染线程隔离,且写-读-清三步紧邻、不会跨帧残留。
     */
    private static final ThreadLocal<MobEffectInstance> ASTRAL_DICE_TOOLTIP_EFFECT = new ThreadLocal<>();

    @Inject(method = "getEffectName", at = @At("RETURN"), cancellable = true)
    private void astralDice$numericEffectLevelBadge(MobEffectInstance effect, CallbackInfoReturnable<Component> cir) {
        MutableComponent name = effect.getEffect().getDisplayName().copy();
        int amplifier = effect.getAmplifier();
        boolean isAlwaysNumeric = effect.getEffect() == ModEffects.CHARGE.get()
                || effect.getEffect() == ModEffects.HEALING.get()
                || effect.getEffect() == ModEffects.MARKED.get();
        if (amplifier >= 0 && amplifier <= 99 && (isAlwaysNumeric || amplifier >= 1)) {
            name.append(CommonComponents.SPACE).append(Component.literal(String.valueOf(amplifier + 1)));
        }
        cir.setReturnValue(name);
    }

    /** ① 先捕获:原版 tooltip 第二行 {@code MobEffectUtil.formatDuration(inst, 1.0F)} 拿到 inst。 */
    @Redirect(method = "renderEffects", require = 2,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffectUtil;formatDuration(Lnet/minecraft/world/effect/MobEffectInstance;F)Lnet/minecraft/network/chat/Component;"))
    private Component astralDice$captureTooltipEffect(MobEffectInstance instance, float factor) {
        ASTRAL_DICE_TOOLTIP_EFFECT.set(instance);
        return MobEffectUtil.formatDuration(instance, factor);
    }

    /** ② 再改写:{@code List.of(...)} 是<b>不可变</b>列表,换成可变列表后追加描述行。 */
    @Redirect(method = "renderEffects", require = 2,
            at = @At(value = "INVOKE",
                    target = "Ljava/util/List;of(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"))
    private List<Component> astralDice$appendEffectDescription(Object first, Object second) {
        List<Component> tooltip = new ArrayList<>(3);
        tooltip.add((Component) first);
        tooltip.add((Component) second);
        MobEffectInstance instance = ASTRAL_DICE_TOOLTIP_EFFECT.get();
        ASTRAL_DICE_TOOLTIP_EFFECT.remove();
        appendDescription(tooltip, instance);
        return tooltip;
    }

    /** 追加描述行(仅当对应 lang 键存在;否则原样保留两行) */
    private static void appendDescription(List<Component> tooltip, MobEffectInstance instance) {
        if (instance == null) return;
        String id = effectId(instance.getEffect());
        if (id == null) return;
        String key = "effect." + id + ".description";
        if (!I18n.exists(key)) return;
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    /**
     * 本模组效果的 {@code "namespace.path"};非本模组注册的效果返回 {@code null}。
     * 1.20.1 的 {@code ModEffects.ALL} 是 {@code Collection<RegistryObject<MobEffect>>} ⇒ 用 {@code get()}
     * (NeoForge 两线是 {@code DeferredHolder} ⇒ {@code value()})。
     */
    private static String effectId(MobEffect effect) {
        for (var holder : ModEffects.ALL) {
            if (holder.get() == effect) {
                var loc = holder.getId();
                return loc.getNamespace() + "." + loc.getPath();
            }
        }
        return null;
    }
}
