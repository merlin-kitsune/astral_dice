package com.merlinkitsune.astral_dice.mixin.jade;

import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把 Jade 护甲条读到的数值从**原版护甲值**换成**本模组防御力**（Forge 1.20.1 线）。
 *
 * <h2>落点与 neo 两线不同</h2>
 * Jade 11.13.3（1.20-forge）**没有** {@code EntityHealthAndArmorProvider}，护甲由独立枚举
 * {@code snownee.jade.addon.vanilla.EntityArmorProvider} 提供（源码
 * {@code Jade-120-forge/src/main/java/snownee/jade/addon/vanilla/EntityArmorProvider.java:21-27}）：
 * <pre>
 * LivingEntity living = (LivingEntity) accessor.getEntity();
 * float armor = living.getArmorValue();      // ← 唯一一次读取
 * if (armor == 0) return;                    // 「0 就不显示」的门控用返回值局部变量，不用二次调用
 * tooltip.add(new ArmorElement(armor));
 * </pre>
 * 因此这里 {@code getArmorValue()} 只有**一次**调用，重定向后门控与数值同时落在防御力上 ——
 * 防御力下限为 2（{@code DiceCombatModifiers#defensePowerOf}）恒 &gt; 0 ⇒ 护甲行永远显示。
 *
 * <h2>只对玩家生效</h2>
 * 防御力是本模组的玩家侧派生值 ⇒ 非玩家回落原版 {@code getArmorValue()}，生物显示保持原样。
 *
 * <h2>1.20 的方法名重映射</h2>
 * 本线产物是**生产 SRG** jar（目标 jar 里 {@code getArmorValue} 实际是 {@code m_21230_}），
 * {@code @At} 里的 {@code Lnet/minecraft/world/entity/LivingEntity;getArmorValue()I} 由
 * MDG Legacy 生成的 {@code astral_dice.refmap.json} 负责映射（同 {@code LightningBoltStrikeScopeMixin}
 * 对 {@code Level#getEntities} 的既有做法）。
 *
 * <h2>⚠️ 本类能编译，靠的是 AP 的「挂起错误抑制」——别动 {@code @At}</h2>
 * 上面的 {@code method}（{@code EntityArmorProvider.appendTooltip}）是 **Jade 自己的方法**，
 * MDG Legacy 的 {@code namedToIntermediate.tsrg} 里没有任何映射能命中它 ⇒ AP 在
 * {@code AnnotatedMixinElementHandlerInjector#registerInjector} 里会挂起一条
 * {@code NO_OBFDATA_FOR_TARGET} 错误。它之所以没有变成编译失败，是因为同一次注入里的
 * {@code @At} 指向的是**原版方法**（可重映射）：重映射成功会调
 * {@code InjectorRemap#notifyRemapped()} → {@code clearMessage()} 把那条错误丢掉，
 * 而编译收尾的 {@code dispatchPendingMessages} 只在 {@code remappedCount == 0} 时才真正抛出
 * （依据 = {@code org.spongepowered.tools.obfuscation.struct.InjectorRemap} 的类头注释）。
 * <p>⇒ **若把 {@code @At} 改成同样不可重映射的目标（例如某个 Jade 方法），本类会立刻以
 * {@code Unable to locate obfuscation mapping for @Redirect target appendTooltip(...)} 编译失败**
 * （{@link ArmorElementIconMixin} 正是这种情形）。届时正确做法是给该注入器显式写
 * {@code remap = false}，而不是去改 {@code method}。
 * <p>同名的 neo 两线（1.21.1 的 {@code EntityHealthAndArmorProvider}、26.1.2 的
 * {@code EntityHealthAndArmorProvider$Client}）不受此影响 —— 那两线未注册任何混淆环境，
 * AP 整体不做重映射处理（编译日志里没有 {@code Supported obfuscation types} 那一行）。
 *
 * <h2>为什么用字符串 {@code targets}</h2>
 * 本配置是可选兼容（{@code required:false}）；用字符串可让 mixin 类在 Jade 缺席时仍能加载。
 *
 * <h2>⚠️ 处理器必须是**非 static**——本线 Mixin 是 0.8.5，别照抄 neo 的 static 写法</h2>
 * （2026-09-26 实机取证 + 反编译确证）Mixin <b>0.8.5</b>（本线 dev 由 mixinbooster 提供，
 * 实例：{@code mixin-0.8.5.jar:0.8.5+Jenkins-b310…}）里
 * {@code Injector#checkTargetForNode(target, node, ALLOW_ALL)} 收尾调的是
 * {@code checkTargetModifiers(target, true)}——{@code exactMatch=true} 要求
 * <b>处理器的 static 修饰符与「被注入方法」严格一致</b>：
 * <pre>
 * if (exactMatch &amp;&amp; target.isStatic != this.isStatic) {
 *     throw new InvalidInjectionException(... "'static' modifier of handler method does not match target" ...);
 * }
 * </pre>
 * 本类的被注入方法 {@code EntityArmorProvider#appendTooltip} 是<b>实例方法</b>（javap 实证：
 * {@code public void appendTooltip(ITooltip, EntityAccessor, IPluginConfig)}）⇒ 处理器写成
 * {@code static} 会在<b>类加载那一刻</b>抛
 * {@code InvalidInjectionException: 'static' modifier of handler method does not match target}，
 * 整条 mixin 被摘掉；因本配置是 {@code required:false}，只留一条 WARN，游戏照常启动、
 * 但「Jade 护甲行显示防御力」静默失效。
 *
 * <p><b>为什么 neo 两线写 static 却能跑</b>：它们的 dev 用 <b>Mixin 0.8.7</b>
 * （{@code net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7}），同一处收尾改成了
 * {@code checkTargetModifiers(target, false)} ⇒ 只拦「非 static 处理器打 static 方法」，
 * 「static 处理器打实例方法」被<b>放行</b>。⇒ <b>同一份源码在 neo 上能跑，靠的是 0.8.7 放宽了这条</b>；
 * 本线 0.8.5 会严格拒绝，故<b>不得与本线之外的两线互抄写法</b>。
 *
 * <p><b>去掉 {@code static} 为何不改变形参表</b>：Redirect 期望的处理器形参由
 * {@code RedirectInjector.RedirectedInvokeData} 从<b>被重定向调用自身</b>的字节码推导——
 * {@code handlerArgs = opcode == INVOKESTATIC ? targetArgs : [owner] + targetArgs}，
 * <b>与处理器是否 static 无关</b> ⇒ 仍是 {@code (LivingEntity)}。非 static 处理器调用点由
 * {@code Injector#invokeHandlerWithArgs} 用 {@code ALOAD 0} 取目标实例（= 该枚举实例，本类不使用它）。
 * 同线可工作先例：{@code GuiMixin#astralDice$suppressExpiryFlash}（同为
 * {@code INVOKEVIRTUAL} 重定向、同为非 static、形参 {@code (接收者, 实参…)}）。
 */
@Mixin(targets = "snownee.jade.addon.vanilla.EntityArmorProvider")
public abstract class ArmorValueProviderMixin {

    @Redirect(
            // 完整描述符（避开同名的合成桥接重载 `(ITooltip, Accessor, IPluginConfig)V`）
            method = "appendTooltip(Lsnownee/jade/api/ITooltip;Lsnownee/jade/api/EntityAccessor;"
                    + "Lsnownee/jade/api/config/IPluginConfig;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getArmorValue()I"))
    // ⚠️ 必须**非 static**：被注入方法 appendTooltip 是实例方法，而本线 Mixin 0.8.5 要求二者
    //    static 修饰符严格一致（详见类头 javadoc）。写成 static ⇒ 类加载时抛
    //    InvalidInjectionException，整条 mixin 被摘掉（只留 WARN，防御力显示静默失效）。
    private int astralDice$armorAsDefense(LivingEntity entity) {
        if (entity instanceof Player player) {
            return DiceCombatModifiers.defensePowerOf(player);
        }
        return entity.getArmorValue();
    }
}
