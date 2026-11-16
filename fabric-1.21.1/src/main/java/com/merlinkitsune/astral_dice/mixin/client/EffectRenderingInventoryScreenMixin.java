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
 * fabric-1.21.1 侧的两项效果面板改造:
 *
 * <h2>① 等级角标数字化</h2>
 * 将原版效果等级角标由「罗马数字、amplifier ≤ 9(等级 ≤ 10)」改为「阿拉伯数字、amplifier ≤ 99(等级 ≤ 100)」。
 * 覆盖物品栏效果面板标签与悬浮 tooltip 两处的等级角标。
 * 本线口径:{@code MobEffectInstance#getEffect()} 返回 {@code Holder<MobEffect>} ⇒ 调用点先取 {@code .value()};
 * 方法引用写 Mojmap 名,由 Loom 在构建期重映射为 intermediary。
 *
 * <h2>② 状态效果「注释」渲染</h2>
 * 把 {@code effect.astral_dice.<id>.description} 追加到悬浮 tooltip 末尾。
 * <b>为什么这里必须用 Mixin</b>:NeoForge 有官方事件 {@code GatherEffectScreenTooltipsEvent}
 * (1.21.1 / 26.1.2 那两线用它,见 {@code client/EffectTooltipDescriptions}),
 * 而 <b>Forge 1.20.1 与 Fabric 1.21.1 都没有这个事件</b>(本线已用 {@code javap -p -c} 核实 1.21.1 的
 * {@code renderEffects} 里 tooltip 构建段未被打过 patch:只有 {@code getEffectName} /
 * {@code formatDuration} / {@code List.of} / {@code GuiGraphics.renderTooltip} 四个调用,无任何 hook)。
 *
 * <p><b>注入手法(双 @Redirect + 一个 ThreadLocal)</b>:{@code renderEffects} 的 tooltip 是局部构造的
 * {@code List.of(getEffectName(inst), formatDuration(inst, 1.0F, tickRate))}(1.21.1 的第二参 factor 之后
 * 多一个 {@code TickRateManager#tickrate()} 的 tickRate),既拿不到 {@code inst} 的可注入参数,
 * 也不想依赖 LVT(LocalCapture)这种一改上游就崩的脆弱定位。利用 Java「实参从左到右求值」的保证:
 * {@code formatDuration} 一定先于 {@code List.of} 执行 ⇒ 前者把 {@code inst} 记进 ThreadLocal,
 * 后者取出并改写列表。两处都用 {@code require = 1}(本方法内这两条调用**各仅 1 处**)⇒ 上游签名一旦变化会<b>启动即报错</b>,
 * ⚠️ 勿把 {@code require} 当成「允许失败的次数」—— Mixin 的语义是「**最少**匹配数」,写成 2 而方法内只有 1 处 ⇒ 打开物品栏即抛 InjectionError(2026-09-30 实测复现并修正)。
 * 而不是被 Mixin 静默摘掉(本仓既有教训)。
 *
 * <p>⚠️ <b>2026-09-30 修正:原写法 {@code require = 2} 是错的</b>。Mixin 的 {@code require} 语义是
 * 「注入点<b>最少</b>命中次数」(未写时取 {@code injectors.defaultRequire}),<b>不是</b>「至少命中 1 次」——
 * 已用 {@code javap -p -c} 对 1.21.1 字节码核实:{@code renderEffects} 内
 * {@code MobEffectUtil.formatDuration} 与 {@code List.of} <b>各只出现 1 次</b>
 * (另一次 {@code formatDuration} 在 {@code renderLabels} 内,已被 {@code method = "renderEffects"} 排除)
 * ⇒ {@code require = 2} 会在<b>打开物品栏时</b>抛 {@code InjectionError} 崩客户端;本线按字节码证据取 1。
 *
 * <p>{@code I18n.exists(String)} 在 1.21.1 同名同签名(已核实 {@code net.minecraft.client.resources.language.I18n}),
 * 故「只在键存在时追加」的口径各线一致。
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
        MutableComponent name = effect.getEffect().value().getDisplayName().copy();
        int amplifier = effect.getAmplifier();
        boolean isAlwaysNumeric = effect.getEffect().value() == ModEffects.CHARGE.get()
                || effect.getEffect().value() == ModEffects.HEALING.get()
                || effect.getEffect().value() == ModEffects.MARKED.get();
        if (amplifier >= 0 && amplifier <= 99 && (isAlwaysNumeric || amplifier >= 1)) {
            name.append(CommonComponents.SPACE).append(Component.literal(String.valueOf(amplifier + 1)));
        }
        cir.setReturnValue(name);
    }

    /** ① 先捕获:原版 tooltip 第二行 {@code MobEffectUtil.formatDuration(inst, 1.0F, tickRate)} 拿到 inst。 */
    @Redirect(method = "renderEffects", require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffectUtil;formatDuration(Lnet/minecraft/world/effect/MobEffectInstance;FF)Lnet/minecraft/network/chat/Component;"))
    private Component astralDice$captureTooltipEffect(MobEffectInstance instance, float factor, float tickRate) {
        ASTRAL_DICE_TOOLTIP_EFFECT.set(instance);
        return MobEffectUtil.formatDuration(instance, factor, tickRate);
    }

    /** ② 再改写:{@code List.of(...)} 是<b>不可变</b>列表,换成可变列表后追加描述行。 */
    @Redirect(method = "renderEffects", require = 1,
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
        String id = effectId(instance.getEffect().value());
        if (id == null) return;
        String key = "effect." + id + ".description";
        if (!I18n.exists(key)) return;
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    /**
     * 本模组效果的 {@code "namespace.path"};非本模组注册的效果返回 {@code null}。
     * {@code ModEffects.ALL} 是 {@code Collection<RegistryObject<MobEffect>>} ⇒ 本方法吃裸值,调用方经 {@code get()} 取得。
     * 1.21.1 的 {@code MobEffectInstance#getEffect()} 返回 {@code Holder<MobEffect>} ⇒ 调用点先取 {@code .value()}。
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
