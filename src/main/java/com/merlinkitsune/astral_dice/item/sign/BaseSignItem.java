package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import com.merlinkitsune.astral_dice.item.chip.FanBigChipItem;
import com.merlinkitsune.astral_dice.item.chip.FanSmallChipItem;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;

public abstract class BaseSignItem extends Item implements ICurioItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseSignItem.class);
    public BaseSignItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        // 立牌只能放入"stand"饰品栏
        if (!"stand".equals(slotContext.identifier())) return false;
        // 禁止重复装备相同的立牌(服务端校验;客户端直接放行避免误判)
        if (slotContext.entity().level().isClientSide()) return true;
        boolean dup = CurioSlotUtil.hasSameItemEquipped(slotContext.entity(), stack);
        if (dup) {
            LOGGER.warn("[Astral Dice][canEquip] 立牌被判定为重复装备被拒: item={}",
                    stack);
        }
        return !dup;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity().level().isClientSide()) return;

        onCurioTick(slotContext, stack);
    }

    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
    }

    /**
     * 立牌主动技能触发(服务端):触发立牌栏(唯一槽位)中立牌的技能。
     * 判定顺序:
     * 1. 玩家级冷却(不受立牌装卸影响):冷却中按键无效;
     * 2. 等待状态(占星师/秘密侦探等需指定目标的技能):等待完成或超时前按键保持无效;
     * 3. 触发成功:非等待类技能立即开始玩家级冷却;等待类技能待其完成指定目标/超时后再计算。
     */
    public static void performSkillForCurio(Player player) {
        if (player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        // 读取立牌栏(唯一槽位)的立牌:用于技能触发与提示前缀(立牌名称)
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        var handlerOpt = curios.get().getStacksHandler("stand");
        if (handlerOpt.isEmpty()) return;
        var handler = handlerOpt.get();
        if (handler.getSlots() <= 0) return;
        ItemStack stack = handler.getStacks().getStackInSlot(0);
        if (!(stack.getItem() instanceof BaseSignItem sign)) return;
        String signName = stack.getHoverName().getString();
        // 1. 玩家级冷却检查:冷却中按键无效,并明确提示"<立牌名>冷却中"(修复:触发成功与冷却拒绝的反馈混淆)
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > 0 && now < cdEnd) {
            notifyActionBar(player, "hud.astral_dice.sign_active_cooldown", signName, ChatFormatting.RED);
            return;
        }
        // 2. 等待状态检查:存在等待目标释放的主动技能时按键无效
        if (isSkillWaiting(player)) return;
        // 3. 触发主动技能
        InteractionResultHolder<ItemStack> result = sign.handleUse(player.level(), player, stack);
        if (result.getResult() != InteractionResult.SUCCESS) return;
        // 4. 手持风扇-大筹码:使用主动技能后,获得一张随机效果牌(不含专属),并对周围范围内敌对目标施加标记
        // 注意:不再发送通用"技能已激活"ActionBar,避免覆盖各立牌自身的特殊 ActionBar 提示
        FanBigChipItem.applyAfterSignSkill(player);
        FanSmallChipItem.applyAfterSignSkill(player);
        // 5. 冷却:等待类技能(激活了玩家级等待状态)待完成指定目标/超时后再开始冷却;其余立牌立即开始玩家级冷却
        if (ModAttachments.getSignReadyExpire(player) <= 0) {
            ModAttachments.setSignActiveCooldownEnd(player,
                    now + GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS);
        }
    }

    // 服务端发送立牌技能反馈(actionbar 提示,带立牌名称前缀;统一由服务端判定成功/拒绝,避免客户端推测混淆)
    private static void notifyActionBar(Player player, String langKey, String signName, ChatFormatting color) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        net.minecraft.network.chat.Component msg =
                net.minecraft.network.chat.Component.translatable(langKey, signName).withStyle(color);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                new com.merlinkitsune.astral_dice.network.ActionBarPayload(msg,
                        GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    // 是否存在等待目标释放的主动技能(占星师/秘密侦探等,等待期间按键无效)
    private static boolean isSkillWaiting(Player player) {
        long expire = ModAttachments.getSignReadyExpire(player);
        return ModAttachments.getSignReadyType(player) > 0 && expire > 0
                && player.level().getGameTime() < expire;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 下蹲右键:自动装备到"stand"饰品栏
        if (player.isShiftKeyDown()) {
            return CurioSlotUtil.tryAutoEquip(player, stack, "stand");
        }
        if (!(this instanceof ParunanSignItem)) {
            return InteractionResultHolder.fail(stack);
        }
        return handleUse(level, player, stack);
    }

    // 立牌被移除时:清除该立牌获得的增益/计数器/累计值,防止反复更换立牌实现效果叠加。
    // 主动技能冷却为玩家级(ModAttachments.SIGN_ACTIVE_COOLDOWN_END),不受立牌装卸影响。
    // 注意:Curios 在攻击/受击等场景会对已装备物品触发 onUnequip+onEquip 重载(from=to 同一物品,
    // 此时物品仍在槽位)——重载场景不应清除立牌数据,否则治愈点数等累计值会被反复清零。
    // 仅在物品真正离开槽位(玩家主动卸下)时清理。
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack stack, ItemStack prevStack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        boolean stillInSlot = CuriosApi.getCuriosInventory(player)
                .flatMap(h -> h.getStacksHandler(slotContext.identifier()))
                .map(h -> slotContext.index() < h.getSlots()
                        && !h.getStacks().getStackInSlot(slotContext.index()).isEmpty()
                        && h.getStacks().getStackInSlot(slotContext.index()).getItem() == stack.getItem())
                .orElse(false);
        if (stillInSlot) {
            // 重载场景(物品仍在槽位):不清理数据
            return;
        }
        clearSignData(player, stack);
    }

    // 各立牌覆写以清除自身累计数据
    protected void clearSignData(Player player, ItemStack stack) {
    }

    // === 战斗钩子(供事件系统统一分发;子类覆写以响应玩家级事件) ===

    /**
     * 佩戴本立牌的玩家造成击杀时触发(由 {@link #invokeKillHooks} 分发)。
     * 子类覆写以实现击杀类被动(如秘密侦探/占星师的击杀奖励)。
     */
    protected void onKill(Player killer, net.minecraft.world.entity.LivingEntity killed) {
    }

    /**
     * 佩戴本立牌的玩家受到伤害时触发(由 {@link #invokeHurtHooks} 分发)。
     * 子类覆写以实现受击类被动(如史莱姆立牌的受击 +1 治愈)。
     */
    protected void onHurt(Player player, float amount) {
    }

    // 分发:玩家造成击杀时,调用其全部已装备立牌的 onKill 钩子
    public static void invokeKillHooks(Player killer, net.minecraft.world.entity.LivingEntity killed) {
        if (killer == null || killer.level().isClientSide()) return;
        CuriosApi.getCuriosInventory(killer).ifPresent(handler -> {
            var results = handler.findCurios(s -> s.getItem() instanceof BaseSignItem);
            for (var r : results) {
                if (r.stack().getItem() instanceof BaseSignItem sign) {
                    sign.onKill(killer, killed);
                }
            }
        });
    }

    // 分发:玩家受到伤害时,调用其全部已装备立牌的 onHurt 钩子
    public static void invokeHurtHooks(Player player, float amount) {
        if (player == null || player.level().isClientSide()) return;
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            var results = handler.findCurios(s -> s.getItem() instanceof BaseSignItem);
            for (var r : results) {
                if (r.stack().getItem() instanceof BaseSignItem sign) {
                    sign.onHurt(player, amount);
                }
            }
        });
    }

    protected abstract InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack);
}
