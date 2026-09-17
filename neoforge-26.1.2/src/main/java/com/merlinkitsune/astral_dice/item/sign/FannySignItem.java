package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.event.SignActiveTriggeredEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.event.AstralEventSystem;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.item.card.EffectCardUtil;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class FannySignItem extends BaseSignItem {
    public FannySignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // 主动:随机获得以下任一效果(固定 11 项,不含"调查阶段"事件)
        int roll = ThreadLocalRandom.current().nextInt(1, 12);
        applyEvent(player, roll);
        sendEventActionBar(player, roll);
        // 触发统一事件附加效果:大侦探立牌被动(+3 星币)与调查员立牌联动(活体书页)
        // (带独立事件 ID,避免与调查阶段事件在同 tick 触发时互相串扰去重)
        com.merlinkitsune.astral_dice.event.AstralEventSystem.onEventTriggered(player, "fanny_active");
        return InteractionResult.SUCCESS;
    }

    private static void applyEvent(Player player, int roll) {
        long now = player.level().getGameTime();
        long lockEnd = 0L;
        switch (roll) {
            case 1 -> lockEnd = applyTimed(player, MobEffects.REGENERATION, 600); // 生命恢复 0:30
            case 2 -> lockEnd = applyTimed(player, MobEffects.STRENGTH, 600); // 力量 0:30
            case 3 -> giveItem(player, new ItemStack(ModItems.ATTACK_CARD_EPIC.get())); // 攻击-特大
            case 4 -> { // 随机效果牌(不含专属)+3星币
                giveRandomEffectCard(player);
                giveCoins(player, 3);
            }
            case 5 -> { // 滋养 2:00(农夫乐事) + 饱和 0:30
                giveNourishment(player);
                lockEnd = applyTimed(player, MobEffects.SATURATION, 600);
            }
            case 6 -> lockEnd = applyTimed(player, MobEffects.SPEED, 600); // 迅捷 0:30
            case 7 -> lockEnd = applyTimed(player, MobEffects.INSTANT_DAMAGE, 1); // 瞬间伤害(1 tick,等同不锁)
            case 8 -> lockEnd = applyTimed(player, MobEffects.POISON, 300); // 中毒 0:15
            case 9 -> { // 饥饿 0:30 + 反胃 0:07
                lockEnd = Math.max(applyTimed(player, MobEffects.HUNGER, 600),
                        applyTimed(player, MobEffects.NAUSEA, 140));
            }
            case 10 -> { // 凋灵 0:07 + 黑暗 0:05
                lockEnd = Math.max(applyTimed(player, MobEffects.WITHER, 140),
                        applyTimed(player, MobEffects.DARKNESS, 100));
            }
            case 11 -> { // 虚弱 0:15 + 挖掘疲劳 0:30
                lockEnd = Math.max(applyTimed(player, MobEffects.WEAKNESS, 300),
                        applyTimed(player, MobEffects.MINING_FATIGUE, 600));
            }
        }
        // 第二批「三态化」:只登记本次**实际施加成功**的计时器(取 max)⇒ 进入锁定(生效中)态;
        // 只发物品的分支(3/4,分支内不登记任何效果)与未施加成功时都不锁 ⇒ 由 performSkill 立即起冷却。
        // 判据用"实际施加结果"而不是回读实例剩余时长:后者会把无关来源的同名效果(如金苹果的生命恢复)算进来
        if (lockEnd > now) {
            beginActiveLock(player, "astral_dice:fanny_sign", lockEnd);
        }
    }

    // 施加一个带时长效果(经计时器守卫)并返回其到期刻(0 = 未施加成功,不构成门控计时器)
    private static long applyTimed(Player player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                   int durationTicks) {
        boolean applied = EffectTimerGuard.apply(player,
                new MobEffectInstance(effect, durationTicks, 0, false, true));
        return applied ? player.level().getGameTime() + durationTicks : 0L;
    }

    // 11 项随机事件里会施加到自身的带时长效果(3/4 两项只发物品,不在此列):锁定态的门控效果来源。
    // 门控只用于"提前结束"(效果被外力清除),硬上界由 applyEvent 按**实际施加**的时长登记
    private static final java.util.List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> LOCK_GATE_EFFECTS =
            List.of(MobEffects.REGENERATION, MobEffects.STRENGTH, MobEffects.SPEED,
                    MobEffects.INSTANT_DAMAGE, MobEffects.POISON, MobEffects.HUNGER, MobEffects.NAUSEA,
                    MobEffects.WITHER, MobEffects.DARKNESS, MobEffects.WEAKNESS, MobEffects.MINING_FATIGUE,
                    MobEffects.SATURATION);

    // 第二批「三态化」:锁定已在 applyEvent 内登记(仅当本次实际施加成功);此处只回答"是否已进入锁定"
    @Override
    protected boolean startActiveLockOnUse(Player player, long now) {
        return isSignActiveLocked(player);
    }

    // 门控效果实例仍在:效果被外力提前移除时锁定提前结束(硬上界不延长)
    @Override
    protected boolean isGateEffectActive(Player player) {
        for (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect : LOCK_GATE_EFFECTS) {
            if (player.hasEffect(effect)) return true;
        }
        return false;
    }

    // 随机一张功能效果牌(通过随机黑名单排除专属效果牌,如活体书页)
    private static void giveRandomEffectCard(Player player) {
        List<ItemStack> pool = EffectCardUtil.getRandomEffectCardPool();
        if (pool.isEmpty()) return;
        giveItem(player, pool.get(ThreadLocalRandom.current().nextInt(pool.size())).copy());
    }

    // 农夫乐事"滋养"效果(仅安装农夫乐事 Mod 时生效)
    private static void giveNourishment(Player player) {
        try {
            var holder = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse("farmersdelight:nourishment"));
            if (holder.isPresent()) {
                EffectTimerGuard.apply(player, new MobEffectInstance(holder.get(), 2400, 0, false, true));
            }
        } catch (Exception ignored) {
        }
    }

    private static void giveCoins(Player player, int count) {
        giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), count));
    }

    private static void giveItem(Player player, ItemStack item) {
        if (ModItems.isCardItem(item)) {
            VitaminPillChipItem.giveCard(player, item);
        } else if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
    }
    private static void sendEventActionBar(Player player, int roll) {
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp,
                    new ActionBarPayload(
                            Component.translatable("msg.astral_dice.fanny_event",
                                    Component.translatable("msg.astral_dice.fanny_event." + roll))
                                    .withStyle(ChatFormatting.YELLOW),
                            GameplayConstants.ACTIONBAR_DURATION_TICKS));
        }
    }

    // 大侦探主动自带 ActionBar 反馈(事件提示,依赖随机事件结果,仍在 handleUse 内发送):
    // 注册到主动技能响应事件,阻止默认提示
    @SubscribeEvent
    public static void onSignActiveTriggered(SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.FANNY_SIGN.get())) {
            event.setHandled();
        }
    }
}
