package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.effect.DorothyWarmthEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.sign.AlanaSignItem;
import com.merlinkitsune.astral_dice.item.sign.DorothySignItem;

import com.merlinkitsune.astral_dice.platform.event.EventPriority;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingChangeTargetEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingHealEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingHurtEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.MobEffectEvent;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import com.merlinkitsune.astral_dice.platform.util.Effects;

/**
 * 本批 4 个新立牌的通用事件接线。
 *
 * <ul>
 *   <li>铁处女 / 摸鱼：免疫一切伤害与负面效果；摸鱼还要「不被怪物索敌」。</li>
 *   <li>多萝西：每次有效治疗叠 1 层【温暖】；瞬间治疗 / 生命恢复按效果等级计算。</li>
 *   <li>修女：受击 &gt;10 点移除被动加成；骰神赐福结束始终移除。</li>
 * </ul>
 *
 * <h3>平台差异（Fabric 1.20.1 / 逐条理由）</h3>
 * <ul>
 *   <li>① 订阅者登记：删除 forge 的 {@code @Mod.EventBusSubscriber}，改由
 *       {@code AstralDiceMod} 用 {@code LoaderBus.INSTANCE.register(NewSignEvents.class)}
 *       显式登记（Fabric 无该注解的等价物；漏登记 = 编译通过、运行无任何反应）；</li>
 *   <li>② 事件类型 {@code net.minecraftforge.event.*} ⇒ 本线 {@code platform.event.*} shim
 *       （{@link LivingHurtEvent}、{@link LivingHealEvent}、{@link LivingChangeTargetEvent}、
 *       {@link MobEffectEvent.Remove}、{@link MobEffectEvent.Expired} 均有等价成员：
 *       {@code getEntity()}、{@code getAmount()}、{@code setCanceled(boolean)}、
 *       {@code isCanceled()}、{@code getNewTarget()}、{@code getEffectInstance()}）；</li>
 *   <li>③ {@code PlayerEffectProtection} 本线不存在 ⇒ 本类只发原版 {@code player.addEffect}（温暖/赐福
 *       均为本模组自有效果，计时由 {@code EffectTimerGuard} 的 Added 记录链覆盖）；</li>
 *   <li>④ {@code MobEffectEvent.Applicable} <b>已接线</b>（2026-11-07）：{@code platform/StarengineBridges} 的
 *       {@code installEffects} 把 Puzzles 的 {@code MobEffectEvents.AFFECTS} 桥接成本事件；
 *       该回调的注入点是 {@code LivingEntity#canBeAffected}（{@code method_6049}）的 HEAD，
 *       正是 forge {@code MobEffectEvent.Applicable} 的同一条原版判定路径。
 *       {@link com.merlinkitsune.astral_dice.platform.event.Event.Result} 的 {@code DENY} 经
 *       {@code EventResult.DENY}（interrupt + false）让 {@code canBeAffected} 返回 false ⇒
 *       该效果不施加，与 forge 的 {@code DO_NOT_APPLY} 逐值对等；</li>
 *   <li>⑤ 结果型事件（如 {@code Applicable}）用 shim 自带的
 *       {@link com.merlinkitsune.astral_dice.platform.event.Event.Result}
 *       （{@code DENY / DEFAULT / ALLOW}）；可取消事件仍用 {@code setCanceled(true)}。</li>
 * </ul>
 */
public final class NewSignEvents {

    private NewSignEvents() {
    }

    private static boolean immune(Player player) {
        return player.hasEffect(Effects.holder(ModEffects.ALANA_IRON_MAIDEN.get()))
                || player.hasEffect(Effects.holder(ModEffects.DEEPSEEK_FISHING.get()));
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (immune(player)) {
            event.setCanceled(true);
            return;
        }
        // 修女被动：赐福期间单次受击 >10 点 ⇒ 立即移除全部加成
        if (event.getAmount() > 10.0F && com.merlinkitsune.astral_dice.component.SignAttachments
                .getAlanaPassiveBonus(player) > 0) {
            AlanaSignItem.clearBonus(player);
        }
    }

    /**
     * 免疫 HARMFUL 效果的施加（forge {@code MobEffectEvent.Applicable} 的对等实现）。
     *
     * <p>派发点：{@code platform/StarengineBridges#installEffects} 注册的
     * {@code MobEffectEvents.AFFECTS}，注入点是 {@code LivingEntity#canBeAffected}
     * （{@code method_6049}）的 HEAD —— 与 forge / neo 的 {@code Applicable} 同一路径
     * （{@code addEffect} 内先判，不通过直接返回 false，效果不入体）。
     *
     * <p>逐条对照 neo {@code NewSignEvents#onEffectApplicable}：魔渊触须 ⇒ 任何效果都不施加；
     * 路障 / 强化拒止 ⇒ 任何药水都不施加；
     * 玩家携带铁处女 / 摸鱼 ⇒ 拦下所有 HARMFUL，但放行青之诅咒（它是玩家自己装备的筹码施加的）。
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        // 2026-11-07 二次复核：魔渊触须不吃任何状态效果（对齐 neo SykesSignItem#onEffectApplicable 的
        // SykeTentacleEntity 分支）。此前只在 SykeTentacleEntity 每 tick removeAllEffects 兜底，
        // 瞬时效果会完整结算一次。
        if (event.getEntity() instanceof com.merlinkitsune.astral_dice.entity.SykeTentacleEntity) {
            event.setResult(com.merlinkitsune.astral_dice.platform.event.Event.Result.DENY);
            return;
        }
        // 2026-10-21 用户裁决：路障 / 强化拒止不吃任何药水
        if (event.getEntity() instanceof com.merlinkitsune.astral_dice.entity.BarricadeEntity
                || event.getEntity() instanceof com.merlinkitsune.astral_dice.entity.RepulsionEntity) {
            event.setResult(com.merlinkitsune.astral_dice.platform.event.Event.Result.DENY);
            return;
        }
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!immune(player)) return;
        var holder = event.getEffectInstance().getEffect();
        // 2026-10-21: 青之诅咒由玩家**自己装备**的筹码施加，不属于「外来负面效果」
        // ⇒ 铁处女 / 摸鱼的 HARMFUL 免疫不得拦它（否则 addEffect 返回 false、效果直接消失）。
        // 本线 ModEffects.* 是 RegistryObject<MobEffect> ⇒ 用 .get()（neo 是 Holder ⇒ .value()）。
        if (holder.value() == ModEffects.BLUE_CURSE.get()) return;
        if (holder.value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
            event.setResult(com.merlinkitsune.astral_dice.platform.event.Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player player
                && player.hasEffect(Effects.holder(ModEffects.DEEPSEEK_FISHING.get()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!player.isAlive()) return;
        if (event.isCanceled()) return;
        // 过疗 / 空治疗不算有效治疗：满血或治疗量 <= 0 时 heal() 不会改变生命值
        if (event.getAmount() <= 0.0F) return;
        if (player.getHealth() >= player.getMaxHealth()) return;
        if (!DorothySignItem.isEquippedForWarmth(player)) return;

        // 有标注来源（瞬间治疗 / 生命恢复）取其效果等级，其余治疗（自然恢复 / 其他 heal 调用）每次 1 层
        DorothyWarmthHealContext.Source source = DorothyWarmthHealContext.current();
        grantWarmth(player, source == null ? 1 : source.level());
    }

    private static void grantWarmth(Player player, int stacks) {
        int current = DorothySignItem.warmthStacks(player);
        int next = Math.min(DorothyWarmthEffect.MAX_STACKS, current + Math.max(1, stacks));
        if (next <= current) return;
        player.addEffect(new MobEffectInstance(Effects.holder(ModEffects.DOROTHY_WARMTH.get()) , -1,
                next - 1, false, false, true));
        DorothySignItem.refreshWarmthModifier(player);
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (event.getEffectInstance() != null
                && event.getEffectInstance().getEffect().value().equals(ModEffects.DICE_BLESSING.get())) {
            AlanaSignItem.clearBonus(player);
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getEffectInstance() != null
                && event.getEffectInstance().getEffect().value().equals(ModEffects.DICE_BLESSING.get())) {
            AlanaSignItem.clearBonus(player);
        }
    }
}
