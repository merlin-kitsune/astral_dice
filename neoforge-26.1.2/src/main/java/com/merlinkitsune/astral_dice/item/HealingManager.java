package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.ModAttachments;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.starenginelib.event.ModEffectRemoval;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * "治愈"点数管理器(玩家级共享资源,与具体饰品解耦)。
 *
 * <p><b>治愈计时器(2026-09-30 用户裁决起与骰神赐福彻底解绑)</b>:由独立的 1:00 计时器驱动
 * ({@link #HEALING_TIMER_SECONDS} 秒一轮):
 * <ul>
 *   <li><b>触发</b>(骰神赐福触发,不再是唯一入口) → 先追加医疗箱筹码的治愈点(紧急 +1、完备 +3,受上限),
 *       再按 当前治愈点 ×2 回血(回血在点数结算之后),并启动/重置计时器;</li>
 *   <li><b>每 1:00 结算</b> → 治愈点减半(向下取整) → <b>再</b>追加医疗箱点数 → 结算后点数 &gt; 0
 *       则按 点数 ×2 回血并重起计时器;点数归 0 则计时器清零,{@link #updateEffect} 随之移除效果。</li>
 * </ul>
 * 例:4 点 + 完备医疗箱(3 点) ⇒ 减半 2 → +3 = 5 ⇒ 回血 10 点 ⇒ 开始下一轮 1:00 计时。
 *
 * <p><b>医疗箱「装备触发」(2026-10-01 用户裁决)</b>:医疗箱筹码在**装备时**、以及**死亡重生 / 重新登录 /
 * 切换维度后**(筹码仍在槽位)各**完整触发**一次治愈(加点 → 按当前层数×2 回血 → 起/重置 1:00 计时器);
 * **卸下时按账本回撤**由装备触发累计获得的层数(「卸除即扣除」,已结算过的回血不追回)。
 * 闸门与账本的机制见 {@link #triggerMedkitOnEquip} 与 {@link #revokeMedkitOnUnequip}。
 *
 * <p>治愈点为单一数值池(附件 healing_points),由史莱姆立牌被动/主动、缓冲盾牌、
 * 医疗箱等来源增加;点数 &gt; 0 即显示「治愈」效果(时长 = 治愈计时器**剩余倒计时**,恒为有限值,**不再使用无限时长**),归 0 即移除。
 *
 * <p>执行优先级:触发时的回血结算由 {@link #onBlessingTriggered} 统一在
 * 事件块末尾调用,晚于所有影响治愈点数量的效果(史莱姆受击 +1、缓冲盾牌 +2 等
 * 在伤害事件更早处已执行;医疗箱加点在本方法内先于回血完成)。
 */
public final class HealingManager {
    /** 治愈点上限(固定 32 点,不再随最大生命值变化) */
    public static final int HEALING_POINT_CAP = 32;
    /** 治愈计时器周期(秒):2026-09-30 用户裁决 30 → 60,且与骰神赐福解绑 */
    public static final int HEALING_TIMER_SECONDS = 60;
    /** 紧急医疗箱触发骰神赐福时增加的治愈点 */
    public static final int MEDKIT_EMERGENCY_POINTS = 1;


    // ── 医疗箱「装备触发」：闸门 + 账本 + 触发 / 回撤(2026-10-01 用户裁决) ────────────
    //
    // 需求:医疗箱筹码在**装备时**、以及**死亡重生 / 重新登录 / 切换维度后**(筹码仍在槽位)各**完整触发**
    // 一次治愈 —— 加点 → 按当前层数×2 回血 → 起/重置 1:00 计时器;**卸下时按账本回撤**由装备触发累计的层数。
    //
    // ⚠️ 为什么必须有闸门:Curios 只持久化 stacks、**不持久化 previousStacks** ⇒ 登录 / 重生 / 切维度后
    // 首 tick 的 prevStack 恒为空栈而槽里有物品,Curios 把这判成一次装备变化并**重放 onEquip**
    // (1.21.1 curios 9.5.1 / 1.20.1 5.14.1 同构)⇒ "空槽守卫"挡不住。闸门的作用是把「Curios 重放」与
    // 「生命周期事件里的显式触发」**二选一**,否则同一时点会被触发两次(一次重放、一次显式)。
    // 释放时机 = 卸下 / 死亡({@link #clear}) / 登录与切维度(视为新会话),逐处语义见各调用点注释。
    /** 装备触发闸门位:紧急医疗箱(见 {@link #claimMedkitEquipGrant}) */
    public static final int GRANT_BIT_MEDKIT_EMERGENCY = 1;
    /** 装备触发闸门位:完备医疗箱 */
    public static final int GRANT_BIT_MEDKIT_COMPLETE = 1 << 1;
    /** 账本每件占 4 bit(名义值最大 3,4 bit 足够) */
    private static final int MEDKIT_GRANT_AMOUNT_BITS = 4;

    private static int medkitGrantShift(int bit) {
        return Integer.numberOfTrailingZeros(bit) * MEDKIT_GRANT_AMOUNT_BITS;
    }

    /** 申领本件的一次性装备触发:未申领过则置位并返回 true(重复调用 / Curios 重放返回 false)。 */
    public static boolean claimMedkitEquipGrant(Player player, int bit) {
        int flags = ModAttachments.getMedkitEquipGrantFlags(player);
        if ((flags & bit) != 0) return false;
        ModAttachments.setMedkitEquipGrantFlags(player, flags | bit);
        return true;
    }

    /** 释放本件的装备触发闸门(卸下 / 死亡 / 登录与切维度视为新会话时调用)。 */
    public static void releaseMedkitEquipGrant(Player player, int bit) {
        int flags = ModAttachments.getMedkitEquipGrantFlags(player);
        if ((flags & bit) != 0) {
            ModAttachments.setMedkitEquipGrantFlags(player, flags & ~bit);
        }
    }

    /** 读出本件「本次装备会话实际获得的治愈层数」(0 = 无待回撤额度)。 */
    public static int grantedMedkitPoints(Player player, int bit) {
        return (ModAttachments.getMedkitEquipGrantAmounts(player) >>> medkitGrantShift(bit)) & 0xF;
    }

    private static void addGrantedMedkitPoints(Player player, int bit, int amount) {
        int shift = medkitGrantShift(bit);
        int packed = ModAttachments.getMedkitEquipGrantAmounts(player);
        int next = Math.max(0, Math.min(0xF, ((packed >>> shift) & 0xF) + amount));
        ModAttachments.setMedkitEquipGrantAmounts(player, (packed & ~(0xF << shift)) | (next << shift));
    }

    private static void clearGrantedMedkitPoints(Player player, int bit) {
        int shift = medkitGrantShift(bit);
        int packed = ModAttachments.getMedkitEquipGrantAmounts(player);
        ModAttachments.setMedkitEquipGrantAmounts(player, packed & ~(0xF << shift));
    }

    /**
     * **装备触发**(2026-10-01 用户裁决):对该玩家**当前装备着的**每一件医疗箱完整触发一次治愈 ——
     * ① 追加该件的治愈点(紧急 +1 / 完备 +3,受上限,**按实际抬升量记账**);
     * ② 按当前治愈点 ×2 回血;③ 起/重置 1:00 治愈计时器。
     *
     * <p>本入口由两条路径共用:**筹码的 {@code onEquip}**(真的新装上)与
     * **{@code PlayerLifecycleHandler} 的登录 / 重生 / 切维度事件**(筹码仍在槽位时也算一次触发)。
     * 幂等由 {@link #claimMedkitEquipGrant} 保证 —— 同一次装备会话内只触发一次,挡掉 Curios 的重放。
     *
     * <p>与 {@link #onBlessingTriggered}(赐福触发)/ {@link #onTimerEnded}(1:00 结算)的区别:
     * 那两条路径**不记账**(它们给的点数是对战局表现的奖励,不随卸下回撤);本方法给的点数是
     * 「装备发放」,卸下时按账本回撤(见 {@link #revokeMedkitOnUnequip})。
     */
    public static void triggerMedkitOnEquip(Player player) {
        if (player.level().isClientSide()) return;
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        var inventory = curios.get();
        if (inventory.findFirstCurio(s -> s.is(ModItems.MEDKIT_EMERGENCY_CHIP.get())).isPresent()) {
            equipTrigger(player, GRANT_BIT_MEDKIT_EMERGENCY, MEDKIT_EMERGENCY_POINTS);
        }
        if (inventory.findFirstCurio(s -> s.is(ModItems.MEDKIT_COMPLETE_CHIP.get())).isPresent()) {
            equipTrigger(player, GRANT_BIT_MEDKIT_COMPLETE, MEDKIT_COMPLETE_POINTS);
        }
    }

    /** 单件的完整装备触发。已申领过闸门(重复调用 / Curios 重放)时整体空操作。 */
    private static void equipTrigger(Player player, int bit, int points) {
        if (!claimMedkitEquipGrant(player, bit)) return;
        int before = getPoints(player);
        add(player, points);
        int granted = getPoints(player) - before;
        if (granted > 0) addGrantedMedkitPoints(player, bit, granted);
        triggerHealing(player);
        updateEffect(player);
    }

    /**
     * 卸下医疗箱筹码:「卸除即扣除」—— 按账本把本次装备会话期间由**装备触发**累计获得的治愈层数回撤,
     * 并释放闸门(再次装备可再触发)。
     *
     * <p>⚠️ 层数是**共享的单一数值池**,回撤可能超过当前层数(已被 1:00 结算减半、被其它来源消耗)
     * ⇒ 按 {@code max(0, ...)} 截断,差额不再追讨(绝不回退到负值)。
     */
    public static void revokeMedkitOnUnequip(Player player, int bit) {
        int granted = grantedMedkitPoints(player, bit);
        clearGrantedMedkitPoints(player, bit);
        releaseMedkitEquipGrant(player, bit);
        if (granted <= 0) return;
        ModAttachments.setHealingPoints(player, Math.max(0, getPoints(player) - granted));
        updateEffect(player);
    }

    /**
     * 把两件医疗箱的装备触发闸门全部释放 —— 「登录 / 切换维度 = 新装备会话」的语义。
     *
     * <p>⚠️ **重生路径只 claim、不调用本方法**——本方法的语义是「**新的装备会话开始**」,
     * 而这个边界已由**死亡**({@link #clear},它本身就会调本方法)划定;重生只是同一次死亡会话的收尾。
     * <p>⚠️ **不要把它读成「不 release 是为了防双触发」**:实测时序是
     * {@code PlayerRespawnEvent} / {@code ServerPlayerEvents.AFTER_RESPAWN} 在 {@code PlayerList#respawn}
     * 里**同步**发出, 而 Curios 的重放发生在**其后一个 tick**
     * ({@code CuriosCommonEvents#tick} 的 `EntityTickEvent.Post`)⇒ 无论这里是否 release,
     * 显式触发都会先 {@code claim} 并置位闸门、把重放挡掉。
     * 保持不 release 只是为了让「释放 = 新会话开始」只有**一个**语义落点。
     */
    public static void refreshMedkitEquipSession(Player player) {
        releaseMedkitEquipGrant(player, GRANT_BIT_MEDKIT_EMERGENCY);
        releaseMedkitEquipGrant(player, GRANT_BIT_MEDKIT_COMPLETE);
    }
    /** 完备医疗箱触发骰神赐福时增加的治愈点 */
    public static final int MEDKIT_COMPLETE_POINTS = 3;

    private HealingManager() {
    }

    // ── 点数读取 ──────────────────────────────────────────────────────────────

    /** 当前治愈点(单一数值池,恒 ≥ 0),供显示/回血/美工刀增伤使用 */
    public static int getPoints(Player player) {
        return ModAttachments.getHealingPoints(player);
    }

    /**
     * 治愈点上限 = 固定 32 点(不再随最大生命值变化)。
     */
    public static int getCap(Player player) {
        return HEALING_POINT_CAP;
    }

    // ── 点数增减 ─────────────────────────────────────────────────────────────

    /**
     * 获得治愈点(纯加点,受上限;不立即回血——回血统一在触发骰神赐福时结算)。
     * 返回增加后的总治愈点。
     */
    public static int add(Player player, int amount) {
        if (player.level().isClientSide()) return getPoints(player);
        int cap = getCap(player);
        int newPoints = Math.min(getPoints(player) + amount, cap);
        ModAttachments.setHealingPoints(player, newPoints);
        updateEffect(player);
        return newPoints;
    }

    /**
     * 消耗治愈点(按总点扣除,下限 0),返回实际消耗的量。
     */
    public static int spend(Player player, int amount) {
        if (player.level().isClientSide()) return 0;
        int total = getPoints(player);
        int spent = Math.min(total, amount);
        if (spent > 0) {
            ModAttachments.setHealingPoints(player, total - spent);
            updateEffect(player);
        }
        return spent;
    }

    /** 清零治愈点并移除"治愈"效果(死亡时调用) */
    public static void clear(Player player) {
        if (player.level().isClientSide()) return;
        ModAttachments.setHealingPoints(player, 0);
        ModAttachments.setHealingTimerEnd(player, 0);
        ModEffectRemoval.remove(player, ModEffects.HEALING);
        // 医疗箱装备触发:死亡即本次装备会话结束 —— 释放闸门(重生后由重生事件再触发一次),
        // 并清零账本(层数已随上方清零,账本再留着会让下次卸下回撤到玩家自己攒的层数)。
        refreshMedkitEquipSession(player);
        clearGrantedMedkitPoints(player, GRANT_BIT_MEDKIT_EMERGENCY);
        clearGrantedMedkitPoints(player, GRANT_BIT_MEDKIT_COMPLETE);
    }

    // ── 治愈计时器/骰神赐福结算 ──────────────────────────────────────────────

    /**
     * 触发骰神赐福时调用(治愈体系的**触发点之一**,不再是唯一触发点):
     * 1. 先追加所有筹码提供的治愈点(医疗箱);
     * 2. 再按当前治愈点 ×2 回血;
     * 3. 启动/重置 {@link #HEALING_TIMER_SECONDS} 治愈计时器。
     */
    public static void onBlessingTriggered(Player player) {
        if (player.level().isClientSide()) return;
        addChipPoints(player);
        triggerHealing(player);
        updateEffect(player);
    }


    /**
     * 治愈计时器到期(每 {@link #HEALING_TIMER_SECONDS} 一次,**与骰神赐福无关**):
     * 1. 治愈点减半(向下取整);
     * 2. 追加医疗箱筹码点数(**在减半之后、回血之前** —— 2026-09-30 用户裁决的顺序);
     * 3. 结算后点数 > 0 ⇒ 按 点数 ×2 回血并重起计时器;点数归 0 ⇒ 计时器清零,
     *    {@link #updateEffect} 随即移除「治愈」效果。
     */
    public static void onTimerEnded(Player player) {
        if (player.level().isClientSide()) return;
        int total = getPoints(player);
        ModAttachments.setHealingPoints(player, total / 2);
        addChipPoints(player);
        if (getPoints(player) > 0) {
            triggerHealing(player);
        } else {
            ModAttachments.setHealingTimerEnd(player, 0);
        }
        updateEffect(player);
    }

    /** 按当前治愈点×2 回血,并启动/重置 30 秒治愈计时器 */
    private static void triggerHealing(Player player) {
        int total = getPoints(player);
        if (total > 0) {
            player.heal(total * 2);
        }
        ModAttachments.setHealingTimerEnd(player,
                player.level().getGameTime() + (long) HEALING_TIMER_SECONDS * 20L);
    }

    /** 追加所有筹码提供的初始治愈点(仅在触发治愈效果条件时调用) */
    private static void addChipPoints(Player player) {
        addMedkitPoints(player);
    }

    /** 装备的医疗箱筹码触发赐福加点(紧急 +1、完备 +3,可叠加,受上限) */
    private static void addMedkitPoints(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        var inventory = curios.get();
        int points = 0;
        if (inventory.findFirstCurio(s -> s.is(ModItems.MEDKIT_EMERGENCY_CHIP.get())).isPresent()) {
            points += MEDKIT_EMERGENCY_POINTS;
        }
        if (inventory.findFirstCurio(s -> s.is(ModItems.MEDKIT_COMPLETE_CHIP.get())).isPresent()) {
            points += MEDKIT_COMPLETE_POINTS;
        }
        if (points > 0) {
            add(player, points);
        }
    }

    // ── 每 tick 驱动:上限收缩 + 治愈计时器到期结算 + 效果刷新 ────────────────────

    /**
     * 每 tick 调用(由统一事件驱动,服务端):
     * <ul>
     *   <li>上限动态跟随玩家当前最大生命值:最大生命降低时,现有治愈点收缩到新上限;</li>
     *   <li>治愈计时器到期结算(2026-09-30 起与骰神赐福解绑 —— 原 {@code onBlessingEnded} 边沿检测已删除,改由独立 1:00 计时器驱动,见 {@link #onTimerEnded});</li>
     *   <li>刷新"治愈"效果显示。</li>
     * </ul>
     */
    public static void tick(Player player) {
        if (player.level().isClientSide()) return;

        // 上限收缩(最大生命降低时)
        int total = getPoints(player);
        int cap = getCap(player);
        if (total > cap) {
            ModAttachments.setHealingPoints(player, cap);
            total = cap;
        }

        // 治愈独立计时器到期处理
        long timerEnd = ModAttachments.getHealingTimerEnd(player);
        if (timerEnd > 0 && player.level().getGameTime() >= timerEnd) {
            onTimerEnded(player);
        }

        // 2026-09-30:治愈体系与骰神赐福解绑 ⇒ 不再做赐福边沿检测,
        // 减半统一由独立的 1:00 计时器到期处理(onTimerEnded)。
        updateEffect(player);
    }

    // ── 效果显示 ───────────────────────────────────────────────────────────────

    /**
     * 刷新「治愈」效果:等级 = 当前治愈点(层数),时长 = 治愈计时器**剩余 tick**(HUD 显示 1:00 倒计时)。
     *
     * <p>2026-09-30 用户裁决(第 2 版):与骰神赐福**解绑**,且指示器**不再使用原版「无限时长」** ——
     * 无限时长只留给美工刀这类「自身条件型」指示器({@code PlayerTickEvents#refreshIndicatorInfinite})。
     * 因此点数 &gt; 0 而计时器未在跑时(仅靠受击/护盾攒点、从未触发过赐福等),
     * 此处立即起一轮完整 {@link #HEALING_TIMER_SECONDS} 计时器,保证剩余时长恒为正常倒计时;
     * 点数归 0 则移除效果(计时器由 {@link #onTimerEnded} 归零)。
     */
    public static void updateEffect(Player player) {
        if (player.level().isClientSide()) return;
        int total = getPoints(player);
        if (total <= 0) {
            ModEffectRemoval.remove(player, ModEffects.HEALING);
            return;
        }
        long now = player.level().getGameTime();
        long timerEnd = ModAttachments.getHealingTimerEnd(player);
        if (timerEnd <= now) {
            // 点数 > 0 却没有在跑的计时器(仅靠受击/护盾攒点、尚未触发过赐福)
            // ⇒ 立即起一轮 1:00 计时,使指示器始终绑定倒计时而不是 ∞。
            timerEnd = now + (long) HEALING_TIMER_SECONDS * 20L;
            ModAttachments.setHealingTimerEnd(player, timerEnd);
        }
        int remain = (int) Math.max(1L, timerEnd - now);
        // 效果已存在、层级一致且剩余充足时不重复施加,避免每 tick 触发效果更新/同步包。
        MobEffectInstance existing = player.getEffect(ModEffects.HEALING);
        if (existing != null && existing.getAmplifier() == total - 1 && existing.getDuration() > 20) return;
        // 层级下降(治愈点被减半/消耗)时必须先移除旧实例:原版 MobEffectInstance#update 只接受
        // 更高的 amplifier,直接 addEffect 低层实例会被忽略(只进 hiddenEffect),HUD 等级会停在旧值。
        if (existing != null && existing.getAmplifier() > total - 1) {
            ModEffectRemoval.remove(player, ModEffects.HEALING);
        }
        MobEffectInstance instance =
                new MobEffectInstance(ModEffects.HEALING, remain, total - 1, false, false, true);
        player.addEffect(instance);
        // ⚠️ 本效果自 2026-09-30 起是**有限时长**,会进入 EffectTimerGuard 的记账
        // (自定义效果由 MobEffectEvent.Added 统一记录);而效果已存在时原版只发 Changed、
        // Added 不触发 ⇒ 守卫的结束刻不会刷新,下一 tick 会把时长截断回旧值。
        // 故此处显式同步一次(record 内部取 max,不会缩短)。
        if (existing != null) {
            EffectTimerGuard.record(player, instance);
        }
    }
}
