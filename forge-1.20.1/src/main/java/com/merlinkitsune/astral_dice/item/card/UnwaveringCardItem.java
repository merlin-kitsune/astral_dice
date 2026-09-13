package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;

import com.merlinkitsune.astral_dice.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 岿然不动(功能效果牌):使用后获得"岿然不动"效果——每层护甲 +8(对应骰战防御力 +4,按"防御 = 2 + 护甲÷2"折算),
 * 最多叠 3 层(amplifier 上限取 GameplayConstants.MAX_EFFECT_STACKS - 1);并获得 抗性提升 II,持续 3:00。
 *
 * 叠层口径与狂暴/王之力一致:重复使用叠 1 层并把时长刷新为 max(旧, 3:00);
 * 护甲修饰器随 amplifier 线性放大(每层 +8,即 8.0 × (amplifier + 1),由 UnwaveringEffect 覆写 getAttributeModifierValue 提供)。
 * 「抗性提升 II」固定为 II,不随层数提高,随重复使用刷新 3:00。
 */
public class UnwaveringCardItem extends BaseEffectCardItem {
    public UnwaveringCardItem(Properties properties) {
        super(properties);
    }


    @Override
    protected String cardTypeId() {
        return "unwavering";
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        // 叠层:每层 amplifier +1,上限 MAX_EFFECT_STACKS - 1(最多 3 层);重复使用刷新时长为 max(旧, 3:00)
        var existing = applyTo.getEffect(ModEffects.UNWAVERING.get());
        int newAmp = existing != null
                ? Math.min(existing.getAmplifier() + 1, GameplayConstants.MAX_EFFECT_STACKS - 1) : 0;
        int newDuration = existing != null ? Math.max(existing.getDuration(), 3600) : 3600;
        applyTo.addEffect(new MobEffectInstance(ModEffects.UNWAVERING.get(), newDuration, newAmp, false, false, true));
        // 抗性提升 II:固定 amplifier 1(不随层数提高),随重复使用刷新 3:00
        EffectTimerGuard.apply(applyTo, new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 3600, 1, false, true));
    }
}
