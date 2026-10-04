package com.merlinkitsune.astral_dice.event;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.starenginelib.item.CuriosCompat;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.chip.AdrenalineChipItem;
import com.merlinkitsune.astral_dice.item.chip.CursedSwordChipItem;
import com.merlinkitsune.astral_dice.item.chip.ElectricSwordChipItem;
import com.merlinkitsune.astral_dice.item.chip.RailgunChipItem;
import com.merlinkitsune.astral_dice.item.chip.WhetstoneChipItem;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.MobEffectEvent;

import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import com.merlinkitsune.astral_dice.item.chip.RevengeHalberdChipItem;
import com.merlinkitsune.astral_dice.item.chip.FlashlightChipItem;
import com.merlinkitsune.astral_dice.item.StarLightManager;

import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class PlayerTickEvents {
    @SubscribeEvent
    public static void onPlayerTickPre(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (player.level().isClientSide()) return;
        EffectTimerGuard.tick(player);
        // 立牌"待命"等待器已随目标选择器并入而移除(2026-09-17:主线 → dev-next 合并裁决),原先在此的
        // BaseSignItem.tickSignReadyTimeout(player) 不再存在;立牌主动的冷却门槛改由
        // BaseSignItem#performSkill 第 6 步按"是否已进入目标选择会话"判定。
        // 立牌主动技能"三态化"(第二批):锁定(生效中)态的玩家级判定——
        // ① 忍者宽限 1:00 内未出任何效果牌 ⇒ 强制重置出牌状态并起冷却;
        // ② 其余立牌门控计时器跑完 ⇒ 必起冷却(无空档);与立牌是否仍在饰品槽无关。
        // (本方法每 tick 被 START/END 两个阶段各调用一次,迁移逻辑幂等)
        BaseSignItem.tickSignActiveLock(player);
    }

    // 计时器守卫:本模组自定义效果被成功施加时记录结束时刻(有限时长效果;无限时长效果不记录)

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (player.level().isClientSide()) return;
        // 总闸门（需求「骰子装备和卸除调整」）：未佩戴骰子 ⇒ 立牌与筹码的玩法功能都不生效，
        // 数值仍保留在附件 / 物品数据上。此处拦的是**不经 isEquipped 判定**的玩家级状态机。
        final boolean diceGated = !CurioSlotUtil.hasDiceEquipped(player);
        // 治愈:每 tick 驱动(内部按 30 秒结算 + 每 tick 刷新效果倒计时)
        if (!diceGated) HealingManager.tick(player);
        // 状态图标的维护**不受 diceGated 短路**,必须每 tick 都跑 ——
        // 否则卸下骰子后这些指示器再没有人负责移除,会留下「功能已关、图标还在」的假象。
        // 口径:三处的判据内部都含「是否佩戴骰子」(筹码侧的 isEquipped 自带该闸门;
        //       美工刀的 hasCutter/hasBlade 由 updateCutterEffect 里的 onDice 补上),
        //       故未佩戴骰子时条件为假 ⇒ 图标被正常移除,而累计数值仍保留在附件/物品数据上。
        updateCutterEffect(player);
        updateChipBonusIndicators(player);
        RevengeHalberdChipItem.updateDisplayEffect(player);
        // 复仇之戟:防御力折算为真实护甲(1 防御力 = 2 护甲值)
        if (!diceGated) RevengeHalberdChipItem.updateArmorBonus(player);
        // 原初核心:赋能层数折算为真实护甲(1 防御力 = 2 护甲值)
        if (!diceGated) com.merlinkitsune.astral_dice.item.chip.PrimordialCoreChipItem.updateArmorBonus(player);
        // 效果牌「手持即选择」(2026-09-25 用户裁决):主手持有选择器类效果牌 ⇒ 自动开启目标选择会话
        // (门槛与按键兜底同源;移出手持的收官在 TargetSelectionManager.tick 侧,reason=released)
        // ⚠️ Forge 的 PlayerTickEvent 每 tick 触发两次(START/END),本入口靠「已在选择中即早退」保证幂等
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.merlinkitsune.astral_dice.item.card.BaseEffectCardItem.tickHeldSelector(serverPlayer);
        }
        // 星币钱包余额 → 客户端(余额条显示):1 秒节流 + 值变化才发包,覆盖一切改动来源
        // (自身按钮 / 发币漏斗 / 拾取吸收 / 库的 /starcoin / 第三方 API),见 economy/StarCoinBalanceSync
        // ⚠️ Forge 每 tick 派发 START+END 两次;同步侧靠「与上次已发送值相同即早退」保证幂等
        if (player instanceof net.minecraft.server.level.ServerPlayer balanceSyncTarget) {
            com.merlinkitsune.astral_dice.economy.StarCoinBalanceSync.tick(balanceSyncTarget);
        }
        // 风水师立牌「白泽赐福」状态机:**每 tick** 做骰神赐福的下降沿检测 + 自检 + 效果续期
        // (不用 MobEffectEvent.Expired:该事件在外力移除/死亡/重连清场时不触发,会漏掉"赐福结束";
        //  下降沿把两条结束路径统一,且不会重复消费跳过计数 —— 见 ZhaoSignItem#tickBlessing)
        // ⚠️ Forge 每 tick 派发 START+END 两次,tickBlessing 内部靠 prev 落值保证重复调用幂等
        if (!diceGated) com.merlinkitsune.astral_dice.item.sign.ZhaoSignItem.tickBlessing(player);
        // 教主立牌「降神 / 狐光」:**每 tick** 驱动 —— 施法者侧派生加成缓存与护甲折算、目标侧骰神赐福下降沿
        // 状态机、狐光层数镜像为 HUD 效果(见 TeruSignItem#tick)。必须放在 tickCount % 20 早退之前:
        // 下降沿检测一旦漏 tick 就会错过"赐福结束"这一拍。
        // ⚠️ Forge 每 tick 派发 START+END 两次 ⇒ 本方法必须幂等:下降沿在第一次调用即落 prev=false,
        // 第二次调用看到 prev=false 不会再消费一次 skip;其余分支都是"同值不写"的镜像写入。
        if (!diceGated) com.merlinkitsune.astral_dice.item.sign.TeruSignItem.tick(player);
        // 绿洲女王立牌(nardis)「女王特权」:临时牌自检(**幂等**)——真值 = 原生效果实例;
        // 「身上/骰子里还有临时牌,但玩家已没有 nardis_privilege 效果」⇒ 清空全部临时牌
        // (效果自然到期 / 被 /effect clear / 离线到期后重登 / 异常残留,四条路径都走这一条)。
        // 必须放在 tickCount % 20 早退**之前**:漏 tick 就会让"效果已结束而临时牌还在"多挂一拍。
        // ⚠️ Forge 每 tick 派发 START+END 两次 ⇒ tick 会被调两遍;幂等由「有效果 / 无临时牌即早退」保证
        //    (第二遍在清空后自然早退,不会重复扣 usedCost/usedDefenseCost)。
        TemporaryCardUtil.tick(player);
        // 充能状态图标的可见性(2026-10-03 用户裁决):未装备任何**充能类筹码**时隐藏 HUD 图标,
        // 装备回筹码后恢复。前置 hasCharge 早退 ⇒ 没有充能的玩家零开销;showIcon 未变时
        // setIconVisibility 内部直接 return,不会每 tick 重建效果实例。
        if (com.merlinkitsune.astral_dice.item.ChargeManager.hasCharge(player)) {
            com.merlinkitsune.astral_dice.effect.ChargeEffect.setIconVisibility(
                    player, com.merlinkitsune.astral_dice.item.ChargeManager.isChargeChipEquipped(player));
        }
        // 人偶师立牌(hanna)「幻想千金」/「挚友祝福」:路过友方玩家的判定。
        // 两条被动各有独立的 1:00 冷却 ⇒ 冷却内只读两个 long 即早退,每 tick 调用安全;
        // 放在 % 20 早退**之前**,避免"擦身而过只停留几拍"被 20 tick 采样漏掉。
        if (!diceGated) com.merlinkitsune.astral_dice.item.sign.HannaSignItem.tickPassing(player);
        if (player.tickCount % 20 != 0) return;
        // 「历史常驻写法」残留归一(2026-10-04):把旧版本写入存档、仍是 Integer.MAX_VALUE
        // 递减产物的本模组常驻效果改写为原版无限时长(-1),使其在界面显示 ∞。
        // 判据与理由见 normalizeLegacyInfiniteDurations 的 javadoc。放在 % 20 早退之后 = 每秒一次。
        normalizeLegacyInfiniteDurations(player);
        // 赋能:每 0:30 减少 1 层(剩余 1 层时直接归 0)
        if (!diceGated) com.merlinkitsune.astral_dice.item.EmpowerManager.tick(player);
        // 效果牌出牌周期计时
        com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.tick(player);
        // 以毒攻毒:中毒结束后给予隐藏图标的生命恢复 II
        com.merlinkitsune.astral_dice.item.card.FightPoisonWithPoisonCardItem.tick(player);
        // 大当家立牌:1 分钟内没有触发骰神赐福 → 养精蓄锐 +1 层
        if (!diceGated) com.merlinkitsune.astral_dice.item.sign.FenSignItem.tick(player);
        // 符卡-祸「厄运」:层数镜像 == 当前持有张数 + 每 2:00 按结算时刻张数的周期伤害
        // (计时器只在首次持有时起算一次,张数增减不改写它 —— 计时器与结算分离)
        com.merlinkitsune.astral_dice.item.card.HuoCardItem.tick(player);

    }

    // 美工刀-初级/锋利状态效果:佩戴对应筹码且生命值 ≥60%(或处于「汲取」)时**常驻显示**。
    // 2026-09-30 用户裁决:显示与骰神赐福**完全解绑**、时长改为无限 —— 只要自身触发条件成立就一直显示。
    // (其额外加伤仍只在骰战结算内生效:DiceCombatEvents 的赐福门控决定是否真的加伤。)
    private static void updateCutterEffect(Player player) {
        var curios = CuriosCompat.getCuriosInventory(player);
        // ⚠️ hasCutter/hasBlade 走的是**直接查 Curios**(不经各筹码的 isEquipped),
        //    因此必须自己带上「是否佩戴骰子」这道总闸门 —— 否则卸下骰子后
        //    美工刀指示器会因为条件恒真而永远摘不掉(本方法现在每 tick 都会执行)。
        final boolean onDice = CurioSlotUtil.hasDiceEquipped(player);
        boolean hasCutter = false;
        boolean hasBlade = false;
        if (curios.isPresent()) {
            hasCutter = onDice && curios.get().findFirstCurio(s -> s.is(ModItems.CUTTER_CHIP.get())).isPresent();
            hasBlade = onDice && curios.get().findFirstCurio(s -> s.is(ModItems.CUTTER_BLADE_CHIP.get())).isPresent();
        }
        boolean fullHp = player.getHealth() >= player.getMaxHealth() * 0.6f || player.hasEffect(ModEffects.PAPARA_BITE.get());
        boolean blessed = player.hasEffect(ModEffects.DICE_BLESSING.get());
        // 效果存在且剩余时长充足时不重复施加,避免每 tick 触发效果更新/同步包
        // 2026-09-30 用户裁决:美工刀的**显示**与骰神赐福完全解绑,计时器改为无限 ——
        // 只要自身触发条件(生命值 ≥60% 或处于「汲取」)成立就常驻显示;
        // 其额外加伤仍只在骰战结算内生效(DiceCombatEvents 的赐福门控内)。
        refreshIndicatorInfinite(player, ModEffects.CUTTER_READY.get(), hasCutter && fullHp);
        refreshIndicatorInfinite(player, ModEffects.CUTTER_BLADE_READY.get(), hasBlade && fullHp);
        // 手电筒-强光:佩戴筹码、处于骰神赐福状态且**确有加伤**(星光/4 ≥ 1)时显示效果图标
        // 2026-10-01 用户裁决:伤害增加型筹码的「生效中」指示器**一律无限时长**
        // (可生效即常驻显示),手电筒随之从 5 秒倒计时改为无限。
        refreshIndicatorInfinite(player, ModEffects.FLASHLIGHT_READY.get(),
                FlashlightChipItem.isEquipped(player) && blessed && StarLightManager.get(player) / 4 >= 1);
    }

    /**
     * 伤害增加型筹码的「就位 / 生效中」指示器（2026-10-01 用户裁决）。
     *
     * <p>口径：**只要加成可生效就常驻显示，时长一律无限**（不走倒计时）；条件消失即移除。
     * 与 {@link #updateCutterEffect} 同一范式，同属 {@code !diceGated} 分支 ——
     * 未佩戴骰子时这些筹码的功能一律不生效，图标随之熄灭。
     *
     * <p>6 枚的判据各自取「加成真的 > 0」那一档，与各自的攻击修饰器同源：
     * 磨刀石/肾上腺素 = 低血阈值；诅咒之剑 = 累计加成 > 0；
     * 电流剑 = 充能 ≥ 4（每 4 点 +1）；电磁炮 = 充能 ≥ 6（+5 生效，"就位"）。
     */
    private static void updateChipBonusIndicators(Player player) {
        refreshIndicatorInfinite(player, ModEffects.WHETSTONE_READY.get(),
                WhetstoneChipItem.isEquipped(player) && WhetstoneChipItem.isLowHealth(player));
        refreshIndicatorInfinite(player, ModEffects.ADRENALINE_READY.get(),
                AdrenalineChipItem.hasLowEquipped(player) && AdrenalineChipItem.isLowHp(player));
        refreshIndicatorInfinite(player, ModEffects.ADRENALINE_HIGH_READY.get(),
                AdrenalineChipItem.hasHighEquipped(player) && AdrenalineChipItem.isLowHp(player));
        refreshIndicatorInfinite(player, ModEffects.CURSED_SWORD_READY.get(),
                CursedSwordChipItem.isEquipped(player)
                        && ModAttachments.getCursedSwordBonus(player) > 0);
        refreshIndicatorInfinite(player, ModEffects.ELECTRIC_SWORD_READY.get(),
                ElectricSwordChipItem.isEquipped(player)
                        && ChargeManager.getStacks(player) >= ElectricSwordChipItem.CHARGE_PER_ATTACK);
        refreshIndicatorInfinite(player, ModEffects.RAILGUN_READY.get(),
                RailgunChipItem.isEquipped(player)
                        && ChargeManager.getStacks(player) >= RailgunChipItem.CHARGE_REQUIRED);
    }

    /**
     * 显示指示器效果(无限时长版本):需要显示且缺失时施加 ∞;不需要显示且存在时移除。
     *
     * <p>⚠️ **仅供「自身条件型」指示器使用**:这类指示器的存在与否完全由玩家自身状态决定、
     * 与任何计时器无关,故用 ∞ 常驻。当前调用方 = 美工刀-初级/锋利、手电筒-强光、
     * 以及 {@link #updateChipBonusIndicators} 的 6 枚(磨刀石 / 肾上腺素两档 / 诅咒之剑 /
     * 电流剑 / 电磁炮)。
     *
     * <p>2026-10-01 用户裁决后,**伤害增加型筹码的指示器一律无限时长**(可生效即常驻),
     * 因此原先的「有限时长版」{@code refreshIndicator}(5 秒倒计时)已无调用方,随之删除;
     * 唯一仍需要绑定倒计时的指示器是「治愈」,它走 {@code HealingManager#updateEffect} 自己的计时器。
     */
    private static void refreshIndicatorInfinite(Player player, net.minecraft.world.effect.MobEffect effect,
                                         boolean shouldShow) {
        if (shouldShow) {
            if (!player.hasEffect(effect)) {
                player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION,
                        0, false, true, true));
            }
        } else if (player.hasEffect(effect)) {
            ModEffectRemoval.remove(player, effect);
        }
    }
    /**
     * 把「历史常驻写法」的残留实例归一为原版无限时长。
     *
     * <p><b>为什么需要这一步</b>:1.3.4 起本模组所有「常驻效果」一律改用原版
     * {@code MobEffectInstance.INFINITE_DURATION}({@code -1}) 表达无限,但那次只改了
     * <b>施加点</b> —— 更早版本写进玩家存档的实例仍是 {@code Integer.MAX_VALUE} 的<b>递减产物</b>
     * (实测存档 {@code astral_dice:charge Duration=2147482289}) ⇒ 物品栏效果面板会显示
     * {@code 29826:08:34} 而不是 {@code ∞}。这些实例不会被任何既有路径修正:
     * <ul>
     *   <li>{@code EffectTimerGuard.record()} 对 {@code >= INFINITE_THRESHOLD} 的值直接
     *       {@code return}(视为永续、不登记) ⇒ 守卫永远不会注意到它;</li>
     *   <li>{@code EffectTimerGuard.tick()} 只遍历**已登记**的有限时长条目,且重施加一律写有限值;</li>
     *   <li>各效果的施加点只在玩家「重新获得 / 消耗 / 切换装备」时才走到,残留可无限期留存。</li>
     * </ul>
     *
     * <p><b>判据刻意只用「超长阈值」而不用效果清单</b>:本模组合法的有限时长最大 24000 tick
     * ({@code RenShieldManager} 的护盾 / 抗性刷新窗口;其余一律 ≤ 3600),
     * 与阈值 {@code Integer.MAX_VALUE / 2}(约 1.07e9)相差
     * 约 4.6 个数量级 ⇒ 不会误伤任何设计上的倒计时;而历史常驻值恰在阈值之上。
     * 双态效果({@code zhao_blessing} / {@code teru_descent} 的「已启动」态 2400 tick)同样
     * 远低于阈值、不受影响。再用「效果必须由本模组注册」把原版 / 第三方效果排除在外。
     *
     * <p><b>快照遍历</b>:{@code getActiveEffects()} 返回活跃效果表的<b>实时视图</b>,
     * 边遍历边移除会抛 {@code ConcurrentModificationException} ⇒ 先复制到
     * {@code ArrayList}(与 {@code FightPoisonWithPoisonCardItem} 的既有做法同源)。
     *
     * <p>只改写**时长**,层数 / 环境粒子 / 可见性 / 图标位原样保留,故不影响任何效果语义。
     */
    private static void normalizeLegacyInfiniteDurations(Player player) {
        for (MobEffectInstance instance :
                new java.util.ArrayList<>(player.getActiveEffects())) {
            if (instance.getDuration() < EffectTimerGuard.INFINITE_THRESHOLD) continue;
            net.minecraft.world.effect.MobEffect effect = instance.getEffect();
            if (!isModEffect(effect)) continue;
            int amplifier = instance.getAmplifier();
            boolean ambient = instance.isAmbient();
            boolean visible = instance.isVisible();
            boolean showIcon = instance.showIcon();
            ModEffectRemoval.remove(player, effect);
            player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION,
                    amplifier, ambient, visible, showIcon));
        }
    }

    /** 该效果是否由本模组注册(用于把原版 / 第三方效果排除在时长归一之外)。 */
    private static boolean isModEffect(net.minecraft.world.effect.MobEffect effect) {
        for (var holder : ModEffects.ALL) {
            if (holder.get() == effect) return true;
        }
        return false;
    }
}
