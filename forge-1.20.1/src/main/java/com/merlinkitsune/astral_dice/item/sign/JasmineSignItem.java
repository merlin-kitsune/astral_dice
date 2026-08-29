package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.event.EffectTimerGuard;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.SlotContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class JasmineSignItem extends BaseSignItem {
    // 被动:每移动 300 米触发一次增益
    private static final float DISTANCE_THRESHOLD = 300f;

    private static final Map<UUID, Vec3> lastPosMap = new HashMap<>();
    private static final Map<UUID, Float> walkAccumMap = new HashMap<>();

    public JasmineSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 被动:移动距离累计
        tickJasmineWalk(player, stack);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 卸下立牌:清除攻击力/防御力增益计数与移动累计
        ModDataComponents.JASMINE_ATK_BONUS.set(stack,  0);
        ModDataComponents.JASMINE_DEF_BONUS.set(stack,  0);
        lastPosMap.remove(player.getUUID());
        walkAccumMap.remove(player.getUUID());
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (!level.isClientSide) {
            // 主动:获得"清扫"效果 2 分钟(迅捷 + 护甲 -30%)
            player.addEffect(new MobEffectInstance(ModEffects.JASMINE_SWEEP.get(), 2400, 0, false, false, true));
            // 随机获得以下任一效果(时长与主动技能同步 2 分钟)
            if (ThreadLocalRandom.current().nextBoolean()) {
                EffectTimerGuard.apply(player, new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2400, 0, false, true)); // 抗性提升 2 分钟
            } else {
                EffectTimerGuard.apply(player, new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2400, 0, false, true)); // 力量 2 分钟
            }
        }
        return InteractionResultHolder.success(stack);
    }

    // === 被动:移动距离累计 ===
    private static void tickJasmineWalk(Player player, ItemStack stack) {
        UUID uuid = player.getUUID();
        Vec3 pos = player.position();
        Vec3 lastPos = lastPosMap.get(uuid);
        if (lastPos == null) {
            lastPosMap.put(uuid, pos);
            walkAccumMap.put(uuid, 0f);
            return;
        }
        double dx = pos.x - lastPos.x;
        double dz = pos.z - lastPos.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        lastPosMap.put(uuid, pos);

        float total = walkAccumMap.getOrDefault(uuid, 0f) + dist;
        while (total >= DISTANCE_THRESHOLD) {
            total -= DISTANCE_THRESHOLD;
            applyMovementBonus(player, stack);
        }
        walkAccumMap.put(uuid, total);
    }

    // === 被动:交替增加攻击力/防御力(骰神赐福点数,各自上限 GameplayConstants.JASMINE_MAX_BONUS) ===
    private static void applyMovementBonus(Player player, ItemStack stack) {
        int atkBonus = ModDataComponents.JASMINE_ATK_BONUS.getOrDefault(stack,  0);
        int defBonus = ModDataComponents.JASMINE_DEF_BONUS.getOrDefault(stack,  0);

        // 按交替顺序决定本次增加项;若该项已达上限则改加另一项
        boolean tryAtk = (atkBonus + defBonus) % 2 == 0;
        if (tryAtk && atkBonus >= GameplayConstants.JASMINE_MAX_BONUS) {
            tryAtk = false;
        } else if (!tryAtk && defBonus >= GameplayConstants.JASMINE_MAX_BONUS) {
            tryAtk = true;
        }

        if (tryAtk) {
            if (atkBonus >= GameplayConstants.JASMINE_MAX_BONUS) return; // 两者均已到上限
            ModDataComponents.JASMINE_ATK_BONUS.set(stack,  atkBonus + 1);
        } else {
            if (defBonus >= GameplayConstants.JASMINE_MAX_BONUS) return; // 两者均已到上限
            ModDataComponents.JASMINE_DEF_BONUS.set(stack,  defBonus + 1);
        }
    }

    public static int getAttackBonus(ItemStack stack) {
        return ModDataComponents.JASMINE_ATK_BONUS.getOrDefault(stack,  0);
    }

    public static int getDefenseBonus(ItemStack stack) {
        return ModDataComponents.JASMINE_DEF_BONUS.getOrDefault(stack,  0);
    }
}
