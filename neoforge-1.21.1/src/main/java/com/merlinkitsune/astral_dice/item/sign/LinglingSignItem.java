package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.effect.LinglingSurgeEffect;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.starenginelib.component.GameplayConstants;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 怪力乱神立牌（{@code astral_dice:lingling_sign}，史诗）。
 *
 * <p>主动「能量突击」（§5）：立即获得 1 张强化拒止 + 施加 {@link LinglingSurgeEffect}（1200t）；
 * buff 期间攻击力 / 移速跟随**有效充能层数**实时变化（数值刷新在
 * {@link LinglingSignManager#refreshActiveModifiers}）。技能链本身完全沿用
 * {@link BaseSignItem}（激活键 → {@code SignActivatePayload} → {@code performSkillForCurio} →
 * {@code performSkill} → 本类 {@link #handleUse}），**不走右键**。
 *
 * <p>被动「拦截者」的其余逻辑在 {@link LinglingSignManager} + {@code event.LinglingSignEvents}；
 * 本类只提供立牌自己的三个钩子：{@link #handleUse}、{@link #activeCooldownBaseTicks}
 * （充能减免链）、{@link #clearSignData}（卸下清理）。**佩戴态的 10t 主动刷新统一由
 * {@code event.PlayerTickEvents} 驱动**（那里同时覆盖「骰子被摘掉」的清场路径，
 * 而 {@code onCurioTick} 在骰子闸门之上、够不到该路径）。
 */
@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class LinglingSignItem extends BaseSignItem {

    public LinglingSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        // 仅服务端权威：发牌 / 上效果 / 刷属性都只在服务端做，客户端只收同步包
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        // ① 立即获得 1 张强化拒止（§5，口径同 §4.1）
        LinglingSignManager.giveRepulsion(player, LinglingSignManager.ACTIVE_GRANT);
        // ② 施加「能量突击」1200t（覆盖时长与数值，不叠层 ⇒ amplifier 恒为 0）
        EffectTimerGuard.apply(player, new MobEffectInstance(ModEffects.LINGLING_SURGE,
                LinglingSurgeEffect.DURATION_TICKS, 0, false, true, true));
        // ③ 立刻按当前有效充能刷一次数值（不等下一个 10t）
        LinglingSignManager.refreshActiveModifiers(player);
        // ④ 专属动作栏文案（§5）：报「本次生效的有效充能层数」（真实充能 + 额外层，已夹上限）；
        //    通用兜底 msg.astral_dice.sign_active_triggered 由本类 onSignActiveTriggered 抑制。
        sendSignActionBar(player, "msg.astral_dice.lingling_active",
                LinglingSignManager.effectiveCharge(player));
        return InteractionResultHolder.success(stack);
    }

    /**
     * §5 主动冷却：不覆写基准 ⇒ 沿用**全局立牌主动冷却** {@link GameplayConstants#SIGN_ACTIVE_COOLDOWN_TICKS}（默认 180 s = 3600t），再走 {@code ChargeManager.signCooldownTicks} 既有充能减免链
     * （配置项 {@code SIGN_ACTIVE_COOLDOWN_SECONDS}，默认 180 秒；原稿误写 120 秒，已按 2026-10-18 实机实测订正）。
     * 冷却与 buff 时长（1200t）是两层，互不影响；buff 期间允许再次触发。
     */
    @Override
    protected int activeCooldownBaseTicks(Player player) {
        int ticks = GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
        return (int) com.merlinkitsune.astral_dice.item.ChargeManager.signCooldownTicks(player, ticks);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 卸下 / 失效：额外层归零 + 摘修饰符 + 摘 buff（Curios 会在登录/同步时重放，全部幂等）
        LinglingSignManager.clearAll(player);
    }

    // 主动技能 ActionBar 注册：自带提示已在 handleUse 内发送，仅阻止默认提示
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.starenginelib.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.LINGLING_SIGN.get())) {
            event.setHandled();
        }
    }
}
