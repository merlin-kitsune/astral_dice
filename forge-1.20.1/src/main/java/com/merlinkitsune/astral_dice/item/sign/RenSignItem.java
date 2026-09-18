package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.RenShieldManager;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.target.SelfTargetable;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.starenginelib.target.TargetSelectionAction;
import com.merlinkitsune.starenginelib.target.TargetSelectionRegistry;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 游戏大师立牌(命名:ren)。
 *
 * <p><b>被动「鼠鼠救我」</b>:佩戴者超过 5:00 没有鼠鼠护盾时自动获得 **1 张随机卡牌 + 鼠鼠护盾**
 * (作用对象就是佩戴者本人;计时与授予全部在 {@link RenShieldManager} 内,本类只提供佩戴判定与被动发奖)。
 *
 * <p><b>主动「熊孩子特权」</b>:目标选择器选择**任意玩家**(或右键对自身使用)⇒ 该玩家获得
 * 1 张随机卡牌 + 鼠鼠护盾。目标类型是 {@link TargetType#PLAYER}(可选中集合 = 除自己以外的玩家),
 * 自身目标由 {@link SelfTargetable#allowSelf()} 打开(右键路径;消费方侧在
 * {@code target/SelectorTargets} 的 4 参重载 + {@code TargetSelectionManager#confirm} 放行,
 * 不改共享库的 {@code TargetType#matches})。
 *
 * <p><b>鼠鼠护盾</b> = 5 黄心(10 点吸收)+ 抗性提升 + 1 层一次性反击;黄心被打空即立即清空
 * (见 {@link RenShieldManager})。
 *
 * <p>主动为「目标选择器类」技能:按下主动键只开启选择会话(门控),确认目标后才施效并进入玩家级冷却
 * —— 冷却与电流核心充能由本类注册的 {@link TargetSelectionAction#apply} 在确认时写入
 * (BaseSignItem 的门控分支会 return,基础流程不会替我们写)。
 *
 * <p>⚠️ 本类**没有**任何 {@code @SubscribeEvent} 方法,故**不得**标注订阅者注解
 * (1.21.1 的 NeoForge 会对「空订阅者」在模组构造阶段直接抛异常;1.20.1 的 Forge 不抛但同属死标注)。
 */
public class RenSignItem extends BaseSignItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenSignItem.class);

    /** 主动技能的动作 id(注册表键;也是 actionbar 技能名 {@code msg.astral_dice.target_select.skill.<id>} 的来源) */
    public static final String ACTION_ID = "ren_privilege";

    /**
     * 主动「熊孩子特权」:选任意玩家(或右键对自身)⇒ 目标 +1 张随机卡牌 + 鼠鼠护盾。
     *
     * <p>必须用具名类而不是匿名类:匿名类只能实现被 new 的那一个接口,而本动作要同时实现
     * {@link TargetSelectionAction} 与 {@link SelfTargetable}(自选目标)。
     */
    private static final class RenPrivilegeAction implements TargetSelectionAction, SelfTargetable {
        @Override
        public String id() {
            return ACTION_ID;
        }

        @Override
        public TargetType targetType() {
            return TargetType.PLAYER;
        }

        @Override
        public boolean allowSelf() {
            return true;
        }

        @Override
        public void apply(ServerPlayer player, LivingEntity target) {
            if (!(target instanceof Player receiver)) return;
            // 1 张随机卡牌(战斗牌 + 效果牌;专属牌不在池内)
            RandomCardHandler.giveCardTo(receiver, RandomCardHandler.CardCategory.ALL);
            // 鼠鼠护盾(+1 层反击);护盾提示由下面的分支按对象分别发送,避免同 tick 两条 actionbar 互相覆盖
            RenShieldManager.grantShield(receiver, false);
            if (receiver != player) {
                RenShieldManager.notifyShieldGained(receiver);
            }
            // 主动成功施加:开始玩家级冷却(统一经 signCooldownTicks:含诡异骰子 -50% 与充能递减)
            ModAttachments.setSignActiveCooldownEnd(player,
                    player.level().getGameTime() + WeirdDiceHandler.signCooldownTicks(player));
            // 电流核心筹码:主动技能实际生效时充能 +1
            CurrentCoreChipItem.onActiveSkillUsed(player);
            ModNetwork.sendToPlayer(player, new ModNetwork.ActionBarMessage(
                    Component.translatable("msg.astral_dice.ren_privilege_applied", target.getDisplayName())
                            .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
            LOGGER.debug("[Astral Dice][TargetSelection] {} applied to {}({}) by {} self={}",
                    ACTION_ID, target.getId(), target.getName().getString(), player.getName().getString(),
                    target == player);
        }
    }

    static {
        TargetSelectionRegistry.register(new RenPrivilegeAction());
    }

    public RenSignItem(Properties properties) {
        super(properties);
    }

    // 目标选择器前置门控:本立牌主动为选择器类 —— 按下主动键只开启目标选择会话并立即返回,
    // 会话时长取自 GameplayConstants.SKILL_WAIT_SECONDS(秒),此处不写死数字;
    // 确认合法目标后才由 resumeGatedActiveSkill 走「风扇筹码发牌 + 立牌主动响应事件」。
    @Override
    protected String selectorActionId() {
        return ACTION_ID;
    }

    /**
     * 被动「鼠鼠救我」触发(佩戴者超过 5:00 没有鼠鼠护盾):获得 1 张随机卡牌 + 鼠鼠护盾。
     *
     * <p>2026-09-25 用户裁决:被动与主动的施效口径统一为「1 张随机卡牌 + 鼠鼠护盾」;作用对象
     * 仍只有**佩戴者本人**(由 {@link RenShieldManager} 的佩戴判定决定,文案不再写「仅佩戴者本人」)。
     */
    public static void onPassiveTriggered(Player player) {
        if (player == null || player.level().isClientSide() || !player.isAlive()) return;
        // 1 张随机卡牌(战斗牌 + 效果牌;专属牌不在池内)
        RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.ALL);
        // 鼠鼠护盾(+1 层反击);提示用被动专属文案,与主动的汇总提示区分
        RenShieldManager.grantShield(player, false);
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendToPlayer(serverPlayer, new ModNetwork.ActionBarMessage(
                    Component.translatable("msg.astral_dice.ren_passive_granted")
                            .withStyle(ChatFormatting.YELLOW),
                    GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    /** 玩家是否佩戴游戏大师立牌(被动「鼠鼠救我」的佩戴判定) */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        return CuriosCompat.getCuriosInventory(player)
                .map(h -> h.findFirstCurio(s -> s.is(ModItems.REN_SIGN.get())).isPresent())
                .orElse(false);
    }
}
