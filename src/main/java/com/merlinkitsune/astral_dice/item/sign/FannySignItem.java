package com.merlinkitsune.astral_dice.item.sign;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResultHolder;
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

public class FannySignItem extends BaseSignItem {
    public FannySignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 主动:随机获得以下任一效果(固定 11 项,不含"调查阶段"事件)
        int roll = ThreadLocalRandom.current().nextInt(1, 12);
        applyEvent(player, roll);
        sendEventActionBar(player, roll);
        // 触发统一事件附加效果:大侦探立牌被动(+3 星币)与调查员立牌联动(活体书页)
        // (带独立事件 ID,避免与调查阶段事件在同 tick 触发时互相串扰去重)
        com.merlinkitsune.astral_dice.event.AstralEventSystem.onEventTriggered(player, "fanny_active");
        return InteractionResultHolder.success(stack);
    }

    private static void applyEvent(Player player, int roll) {
        switch (roll) {
            case 1 -> player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 0, false, true)); // 生命恢复 0:30
            case 2 -> player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 0, false, true)); // 力量 0:30
            case 3 -> giveItem(player, new ItemStack(ModItems.ATTACK_CARD_EPIC.get())); // 攻击-特大
            case 4 -> { // 随机效果牌(不含专属)+3星币
                giveRandomEffectCard(player);
                giveCoins(player, 3);
            }
            case 5 -> { // 滋养 2:00(农夫乐事) + 饱和 0:30
                giveNourishment(player);
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 600, 0, false, true));
            }
            case 6 -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0, false, true)); // 迅捷 0:30
            case 7 -> player.addEffect(new MobEffectInstance(MobEffects.HARM, 1, 0, false, true)); // 瞬间伤害
            case 8 -> player.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 0, false, true)); // 中毒 0:15
            case 9 -> { // 饥饿 0:30 + 反胃 0:07
                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0, false, true));
            }
            case 10 -> { // 凋灵 0:07 + 黑暗 0:05
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 140, 0, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, true));
            }
            case 11 -> { // 虚弱 0:15 + 挖掘疲劳 0:30
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 0, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 0, false, true));
            }
        }
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
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse("farmersdelight:nourishment"));
            if (holder.isPresent()) {
                player.addEffect(new MobEffectInstance(holder.get(), 2400, 0, false, true));
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
}
