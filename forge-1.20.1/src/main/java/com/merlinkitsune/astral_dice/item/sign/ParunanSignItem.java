package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import com.merlinkitsune.astral_dice.item.StarLightManager;

public class ParunanSignItem extends BaseSignItem {
    public ParunanSignItem(Properties properties) {
        super(properties);
    }

    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        long gameTime = player.level().getGameTime();
        if (gameTime % (GameplayConstants.PARUNAN_PASSIVE_INTERVAL_SECONDS * 20L) == 0) {
            // 被动:每 N 秒星光 +1(上限由 StarLightManager 统一管理)
            StarLightManager.add(player, 1);
        }
    }

    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        int starlight = StarLightManager.get(player);
        if (starlight <= 0) {
            return InteractionResultHolder.fail(stack);
        }

        // 每 2 点星光返还 1 个星币(转化比例集中管理,余数部分保留)。
        // 2026-09-15 用户裁决(F-1):兑换一律**按结果判定**——银行卡等提供的基础值(下限)不可兑换出去,
        // 故"亮着银行卡但没有任何可支配星光"时兑换产出为 0;此时整个主动技能作废(返回 fail,
        // 与"技能未释放"一致,不进入主动技能冷却、不发随机增益、也不触发技能响应事件),
        // 并给出 ActionBar 提示,避免"3 选 1 增益 + 180 秒冷却"被白嫖。
        int gained = ResourceConversion.starlightToStarCoins(player, -1);
        if (gained <= 0) {
            sendSignActionBar(player, "msg.astral_dice.parunan_active_no_starlight");
            return InteractionResultHolder.fail(stack);
        }

        // 主动技能:随机获得以下任一效果
        int choice = ThreadLocalRandom.current().nextInt(3);
        MobEffectInstance effect;
        if (choice == 0) {
            effect = new MobEffectInstance(MobEffects.SATURATION, 600, 0, false, true); // 饱和 30 秒
        } else if (choice == 1) {
            effect = new MobEffectInstance(MobEffects.LUCK, 6000, 0, false, true); // 幸运 5 分钟
        } else {
            effect = new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 18000, 0, false, true); // 村庄英雄 15 分钟
        }
        boolean applied = EffectTimerGuard.apply(player, effect);
        // 第二批「三态化」:只登记本次**实际施加成功**的计时器 ⇒ 进入锁定(生效中)态;锁定结束才起主动冷却。
        // 到期刻取本次随机到的效果实例时长(不硬编码时长表);施加失败(已有更长同类效果)则不锁、立即起冷却,
        // 也**不回读**实例剩余时长——那会把无关来源的同名效果(如袭击给的村庄英雄)误算成本技能的计时器
        if (applied) {
            beginActiveLock(player, "astral_dice:parunan_sign",
                    level.getGameTime() + effect.getDuration());
        }

        return InteractionResultHolder.success(stack);
    }

    // 主动施加的三选一效果(饱和/幸运/村庄英雄):锁定(生效中)态的门控效果来源
    private static final java.util.List<net.minecraft.world.effect.MobEffect> LOCK_GATE_EFFECTS =
            java.util.List.of(MobEffects.SATURATION, MobEffects.LUCK, MobEffects.HERO_OF_THE_VILLAGE);

    // 第二批「三态化」:锁定已在 handleUse 内登记(仅当本次实际施加成功);此处只回答"是否已进入锁定"
    @Override
    protected boolean startActiveLockOnUse(Player player, long now) {
        return isSignActiveLocked(player);
    }

    // 门控效果实例仍在:效果被外力提前移除(如牛奶桶)时锁定提前结束(硬上界不延长)
    @Override
    protected boolean isGateEffectActive(Player player) {
        for (net.minecraft.world.effect.MobEffect effect : LOCK_GATE_EFFECTS) {
            if (player.hasEffect(effect)) return true;
        }
        return false;
    }

    // 触发骰神赐福后立即获得 骰点*2 星光(上限由 StarLightManager 统一管理)
    public static void gainStarlightOnBlessing(Player player, int dicePoint) {
        if (player.level().isClientSide()) return;
        StarLightManager.add(player, dicePoint * 2);
    }
}
