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
 * <p>消耗档位:剩余冷却 ÷ {@link #MAX_COOLDOWN_SECONDS}(180 秒)得到占比,按占比切成
 * {@link #MAX_COOLDOWN_COST} 档(每档 1/6),向上取整后钳制在 1~6 点。
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
     * 立即完成冷却所需的充能点数:按剩余时长占 {@link #MAX_COOLDOWN_SECONDS} 的比例切成
     * {@link #MAX_COOLDOWN_COST} 档(向上取整),最低 1 点、最高 {@link #MAX_COOLDOWN_COST} 点。
     */
    public static int instantCooldownCost(long remainingTicks) {
        if (remainingTicks <= 0) return 0;
        double ratio = Math.min(1.0, remainingTicks / (double) (MAX_COOLDOWN_SECONDS * 20L));
        int cost = (int) Math.ceil(ratio * MAX_COOLDOWN_COST);
        return Math.max(1, Math.min(MAX_COOLDOWN_COST, cost));
    }

    /**
     * 立牌主动技能冷却中按下主动技能键时调用:按剩余冷却占比消耗充能并立即使冷却完成。
     *
     * @return {@link #FINISH_NONE}(未佩戴,交回默认冷却提示)、{@link #FINISH_DONE}(已完成)、
     *         {@link #FINISH_NOT_ENOUGH}(充能不足,已提示)
     */
    public static int tryFinishCooldown(Player player, long cooldownEnd, long now) {
        if (player == null || player.level().isClientSide()) return FINISH_NONE;
        if (!isEquipped(player)) return FINISH_NONE;
        long remaining = cooldownEnd - now;
        if (remaining <= 0) return FINISH_NONE;
        int cost = instantCooldownCost(remaining);
        if (ChargeManager.getStacks(player) < cost) {
            sendActionBar(player, "hud.astral_dice.current_core_not_enough", cost);
            return FINISH_NOT_ENOUGH;
        }
        for (int i = 0; i < cost; i++) {
            ChargeManager.consumeOne(player);
        }
        // 立即完成冷却:结束时刻置为当前时刻(后续判定 now < cdEnd 不再成立)
        ModAttachments.setSignActiveCooldownEnd(player, now);
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
