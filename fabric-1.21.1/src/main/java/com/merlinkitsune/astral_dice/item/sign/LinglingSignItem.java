package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.SignAttachments;
import com.merlinkitsune.astral_dice.effect.LinglingSurgeEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.starengine.event.SignActiveTriggeredEvent;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.merlinkitsune.astral_dice.platform.util.Effects;

/**
 * 「怪力乱神立牌」(lingling)主动技能「能量突击」。
 *
 * <p>规格 §5(逐字):「立即获得 1 张强化拒止,当前充能层数转化为攻击力与移动速度
 * (每层攻击力 +1、移动速度 +1%),持续 1:00;冷却 2:00」。
 *
 * <p>蓝本 = {@code forge-1.20.1/.../item/sign/LinglingSignItem.java}(逐条语义一致)。
 *
 * <p><b>平台差异(Fabric 1.20.1 / 逐条理由)</b>:
 * <ul>
 *   <li>① 订阅者注解 {@code @Mod.EventBusSubscriber} 在 fabric 不存在 ⇒ 删注解,改由
 *       {@code AstralDiceMod} 里 {@code LoaderBus.INSTANCE.register(LinglingSignItem.class)}
 *       显式登记(<b>漏登记 = 编译通过、运行无声</b>:主动技能的动作栏去重会失效);</li>
 *   <li>② {@code ModEffects.LINGLING_SURGE} 在本线同为 {@code RegistryObject} ⇒ {@code .get()};</li>
 *   <li>③ 「一生一次」赠牌标记走 {@link SignAttachments#isLinglingGranted}(forge 侧是
 *       {@code ModAttachments} 的同名键,键名/codec/死亡保留标记逐字相同);</li>
 *   <li>④ 施效通道:{@code PlayerEffectProtection#apply} ⇒ {@link EffectTimerGuard#apply}
 *       (与本线既有立牌同一写法,见 AlanaSignItem 的同类说明);</li>
 *   <li>⑤ 冷却**不覆写基准** ⇒ 走基类默认口径 {@code WeirdDiceHandler.signCooldownTicks}
 *       (默认 180 秒 = 3600t,含充能封顶 + 额外电池筹码减成),与默认立牌同一条代码路径。</li>
 * </ul>
 *
 * <p>主动技能由「立牌主动键」触发({@code BaseSignItem#performSkill}),不是右键 —— 右键(潜行)只自动装备。
 */
public class LinglingSignItem extends BaseSignItem {

    static {
        // 见 component/SignAttachments 类头:附件键必须早于任何玩家 NBT 反序列化注册(KI-F17)
        SignAttachments.ensureRegistered();
    }

    public LinglingSignItem(Properties properties) {
        super(properties);
    }

    /**
     * 主动:立即得 1 张强化拒止 + 施加「能量突击」+ 立刻按实时层数挂修饰器。
     *
     * <p>动作栏文案用 {@code msg.astral_dice.lingling_active}(带当前有效充能层数,与蓝本同款)。
     */
    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        LinglingSignManager.giveRepulsion(player, LinglingSignManager.ACTIVE_GRANT);
        EffectTimerGuard.apply(player,
                new MobEffectInstance(Effects.holder(ModEffects.LINGLING_SURGE.get()) , LinglingSurgeEffect.DURATION_TICKS, 0,
                        false, true, true));
        LinglingSignManager.refreshActiveModifiers(player);
        sendSignActionBar(player, "msg.astral_dice.lingling_active", LinglingSignManager.effectiveCharge(player));
        return InteractionResultHolder.success(stack);
    }

    /** 卸下 / 死亡:摘净修饰器 + 移除「能量突击」(不回收已到账充能,2026-10-21 用户裁决) */
    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        LinglingSignManager.clearAll(player);
        // 2026-10-27 用户裁决:立牌主动卸下 ⇒ 卡牌变回等张数路障、身上的强化拒止物品销毁、
        // 本人已放置的强化拒止立即销毁(死亡掉牌不算主动卸下,由 onSignLost 内部兜住)。
        LinglingSignManager.onSignLost(player);
    }

    /**
     * 本立牌自己发动作栏文案 ⇒ 把基类默认的「立牌主动技能已触发」压掉
     * (与蓝本 {@code event.setHandled()} 同口径,照 NancyLuSignItem 先例)。
     */
    @SubscribeEvent
    public static void onSignActiveTriggered(SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.LINGLING_SIGN.get())) {
            event.setHandled();
        }
    }
}
