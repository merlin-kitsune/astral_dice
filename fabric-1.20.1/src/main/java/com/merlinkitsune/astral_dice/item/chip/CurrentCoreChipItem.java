package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * 电流核心筹码(充能类,史诗):
 * <ul>
 *   <li>使用主动技能时,获得 {@link #CHARGE_PER_SKILL} 点充能;</li>
 *   <li>主动技能仍在冷却中时,按下主动技能键 → 按剩余冷却时长占比消耗充能,
 *       并立即使主动技能冷却完成(不直接释放技能,需再次按键使用)。</li>
 * </ul>
 *
 * <p>消耗档位:剩余冷却 ÷ 本次冷却实际使用的最大冷却(路线 A:起冷却时记录的
 * {@code sign_active_max_cooldown} 附件;记录缺失时回退 {@link #MAX_COOLDOWN_SECONDS}(180 秒))
 * 得到占比,按占比切成 {@link #MAX_COOLDOWN_COST} 档(每档 1/6),向上取整后钳制在 1~6 点。
 */
public class CurrentCoreChipItem extends BaseChipItem {
    /** 每次使用主动技能获得的充能层数 */
    public static final int CHARGE_PER_SKILL = 1;
    /** 消耗档位换算参考的最大冷却时长(秒) */
    public static final int MAX_COOLDOWN_SECONDS = 180;
    /** 立即完成冷却的充能消耗上限(按占比切成 6 档) */
    public static final int MAX_COOLDOWN_COST = 6;

    /** {@link #tryFinishCooldown} 返回值:未佩戴本筹码(按默认冷却提示处理) */
    public static final int FINISH_NONE = 0;
    /** {@link #tryFinishCooldown} 返回值:已消耗充能并立即完成冷却 */
    public static final int FINISH_DONE = 1;
    /** {@link #tryFinishCooldown} 返回值:已佩戴但充能不足(已发送提示) */
    public static final int FINISH_NOT_ENOUGH = -1;

    public CurrentCoreChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴本筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.CURRENT_CORE_CHIP.get())).isPresent();
    }

    /**
     * 主动技能实际生效(开始玩家级冷却)时调用:佩戴电流核心则充能 +1。
     * 普通立牌在 {@code BaseSignItem.performSkill} 触发成功处调用;
     * 需指定目标的立牌(占星师/秘密侦探/枪匠)在其等待释放成功处(见 {@code DiceCombatEvents})调用。
     */
    public static void onActiveSkillUsed(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        ChargeManager.addStacks(player, CHARGE_PER_SKILL);
    }

    /**
     * 立即完成冷却所需的充能点数:按剩余时长占"本次冷却实际使用的最大冷却"的比例切成
     * {@link #MAX_COOLDOWN_COST} 档(向上取整),最低 1 点、最高 {@link #MAX_COOLDOWN_COST} 点。
     *
     * @param maxCooldownTicks 本次冷却实际使用的最大冷却 tick(≤0 表示记录缺失,回退
     *                         {@link #MAX_COOLDOWN_SECONDS} 的旧行为)
     */
    public static int instantCooldownCost(long remainingTicks, long maxCooldownTicks) {
        if (remainingTicks <= 0) return 0;
        long base = maxCooldownTicks > 0 ? maxCooldownTicks : MAX_COOLDOWN_SECONDS * 20L;
        double ratio = Math.min(1.0, remainingTicks / (double) base);
        int cost = (int) Math.ceil(ratio * MAX_COOLDOWN_COST);
        return Math.max(1, Math.min(MAX_COOLDOWN_COST, cost));
    }

    /**
     * 立牌主动技能冷却中按下主动技能键时调用:按剩余冷却占比消耗充能并立即使冷却完成。
     *
     * <p>2026-09-27 蛟龙立牌(mamushi)规格 §3.4 新增一条**拒绝**分支:当前佩戴 mamushi 且其
     * 强制冷却(1:00,不可被任何减免绕过)尚未到期 ⇒ **拒绝**本次"立即完成冷却" ——
     * 不扣充能、不写冷却,提示走该立牌专属文案 {@code msg.astral_dice.mamushi_cooldown_locked}
     * (F7 收口:返回 {@link #FINISH_NOT_ENOUGH},与 1.21.1 的
     * {@code CurrentCoreChipItem#tryFinishCooldown} 逐字对齐;此前本线误写成"返回
     * {@link #FINISH_NONE} + 走既有冷却文案")。该分支在正常路径上不可达 ——
     * {@code BaseSignItem#performSkill} 第 ② 步的硬闸门在冷却分支之前就早退了 ——
     * 它是纵深防御。
     *
     * @return {@link #FINISH_NONE}(未佩戴,交回默认冷却提示)、
     *         {@link #FINISH_DONE}(已完成)、
     *         {@link #FINISH_NOT_ENOUGH}(充能不足 / 强制冷却被拒,均已提示)
     */
    public static int tryFinishCooldown(Player player, long cooldownEnd, long now) {
        if (player == null || player.level().isClientSide()) return FINISH_NONE;
        // 第二批「三态化」:主动技能仍在锁定(生效中)态时**严格禁用**本筹码——
        // 不触发、不扣充能、不入减免池;用户可见提示由 BaseSignItem.performSkill 的锁定分支统一发出
        // (该分支判定在冷却分支之前,故正常路径下根本走不到这里;此处仅为纵深防御)
        if (com.merlinkitsune.astral_dice.item.sign.BaseSignItem.isSignActiveLocked(player)) return FINISH_NONE;
        // 强制冷却硬闸门(规格 §3.4,2026-09-27 蛟龙立牌;F7 = 与 1.21.1 对齐的写法):
        // 当前佩戴的是 mamushi 立牌且其强制冷却(1:00,不可被任何减免绕过)尚未到期 ⇒ **拒绝**
        // 本次"立即完成冷却" —— 不扣充能、不写冷却,并给出本立牌的专属提示文案。
        // 正常路径下走不到这里(BaseSignItem#performSkill 第 ② 步的闸门在冷却分支之前就早退了),
        // 本判据是纵深防御,保证任何到达本方法的路径都无法用充能绕过强制冷却。
        if (com.merlinkitsune.astral_dice.item.sign.MamushiSignItem.isEquipped(player)
                && now < com.merlinkitsune.astral_dice.item.sign.MamushiSignItem.getForcedCooldownUntil(player)) {
            sendActionBar(player, "msg.astral_dice.mamushi_cooldown_locked");
            return FINISH_NOT_ENOUGH;
        }
        if (!isEquipped(player)) return FINISH_NONE;
        long remaining = cooldownEnd - now;
        if (remaining <= 0) return FINISH_NONE;
        // 路线 A:档位分母取起冷却时记录的"本次冷却实际使用的最大冷却"(记录缺失时回退硬编码 180 秒)
        int cost = instantCooldownCost(remaining, ModAttachments.getSignActiveMaxCooldown(player));
        if (ChargeManager.getStacks(player) < cost) {
            sendActionBar(player, "hud.astral_dice.current_core_not_enough", cost);
            return FINISH_NOT_ENOUGH;
        }
        for (int i = 0; i < cost; i++) {
            ChargeManager.consumeOne(player);
        }
        // 立即完成冷却:结束时刻置为当前时刻(后续判定 now < cdEnd 不再成立),并让"本次最大冷却"记录随冷却一起失效
        ModAttachments.setSignActiveCooldownEnd(player, now);
        ModAttachments.setSignActiveMaxCooldown(player, 0);
        sendActionBar(player, "hud.astral_dice.current_core_finish", cost);
        return FINISH_DONE;
    }

    // 服务端 ActionBar 提示
    private static void sendActionBar(Player player, String langKey, Object... args) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        Component msg = Component.translatable(langKey, args).withStyle(ChatFormatting.AQUA);
        ModNetwork.sendToPlayer(serverPlayer,
                new ModNetwork.ActionBarMessage(msg, GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }
}
