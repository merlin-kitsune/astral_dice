package com.merlinkitsune.astral_dice.mixin.client;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 抑制「层数递减类」效果(治愈 / 标记 / 弱点识破 / 赋能)在客户端的「即将到期」闪烁。
 *
 * <p><b>闪烁来源(原版)</b>:{@code net.minecraft.client.gui.Gui#renderEffects}
 * (1.21.1 反编译源码 {@code net/minecraft/client/gui/Gui.java} 第 548~553 行)对 HUD 效果图标
 * 做即将到期脉冲:
 * <pre>
 * if (mobeffectinstance.endsWithin(200)) {          // 实例剩余时长 &lt;= 200 tick(10 秒)
 *     int k = mobeffectinstance.getDuration();
 *     int l = 10 - k / 20;
 *     f = Mth.clamp(k / 10.0F / 5.0F * 0.5F, 0.0F, 0.5F)
 *         + Mth.cos(k * (float) Math.PI / 5.0F) * Mth.clamp(l / 10.0F * 0.25F, 0.0F, 0.25F);
 * }
 * ...
 * guiGraphics.setColor(1.0F, 1.0F, 1.0F, f);        // 图标 alpha 按 cos 振荡 = 闪烁
 * guiGraphics.blit(..., textureatlassprite);
 * </pre>
 * 触发条件只有「非 ambient」+「{@code endsWithin(200)}」两条,<b>与层数无关</b>;
 * alpha 只作用于图标,背景不受影响。
 *
 * <p><b>物品栏效果面板本身不闪烁</b>:{@code EffectRenderingInventoryScreen#renderIcons}
 * (同版本第 112~127 行)只有一次 {@code guiGraphics.blit(...)},既无 {@code setColor} 也无
 * {@code endsWithin},面板侧不存在闪烁逻辑,故只需压制上面这一处 HUD 脉冲。
 *
 * <p><b>为什么不把实例时长改长</b>:这四类效果的实例时长与递减节奏绑定 ——
 * 标记的实例时长本身就是递减计时(1200 tick 到期即减 1 层,见
 * {@code MarkManager#onMarkExpired});治愈的实例时长跟随骰神赐福/治愈计时器剩余
 * (见 {@code HealingManager#updateEffect});赋能为「递减间隔 600 + 20 余量」的既有设计
 * ({@code EmpowerEffect.DURATION_TICKS = 620});弱点识破为 {@code Integer.MAX_VALUE}。
 * 拉长时长必然改动递减间隔或倒计时语义,故只在渲染侧消除闪烁表现。
 *
 * <p><b>影响面</b>:只有本模组这四类效果返回 {@code false}(其中弱点识破原本就因无限时长
 * 不进入闪烁窗口,这里使其与其余三类口径统一),其余效果一律走原方法 ——
 * 原版效果与其他模组效果不受影响。四类效果的「实例存在」即等价于「层数 &gt; 0」
 * (amplifier ≥ 0 ⇒ 层数 ≥ 1;归零时由各自管理器移除效果),因此本注入直接给出
 * 「层数 &gt; 0 时永不闪烁、归零正常消失」。注入不修改实例的任何字段
 * (时长 / 等级 / ambient / visible / showIcon 均保持原样),层数机制与归零行为不变。
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Redirect(method = "renderEffects",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffectInstance;endsWithin(I)Z"))
    private boolean astralDice$suppressExpiryFlash(MobEffectInstance instance, int duration) {
        MobEffect effect = instance.getEffect().value();
        if (effect == ModEffects.HEALING.get()
                || effect == ModEffects.MARKED.get()
                || effect == ModEffects.WEAKNESS_REVEAL.get()
                || effect == ModEffects.EMPOWER.get()) {
            return false;
        }
        return instance.endsWithin(duration);
    }
}
