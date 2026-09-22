package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.starenginelib.combat.HostileTargets;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.event.LivingPageFlightScheduler;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class LivingPageItem extends BaseEffectCardItem {
    private static final String ACTION_ID = "living_page";

    private static final double LOCK_RANGE = 32.0D;

    static {
        registerSelectorAction(ACTION_ID, TargetType.ENEMY, false, LOCK_RANGE);
    }

    public LivingPageItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String cardTypeId() {
        return ACTION_ID;
    }

    @Override
    public String selectorActionId() {
        return ACTION_ID;
    }

    @Override
    protected boolean isExclusive() {
        return true;
    }

    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        ExclusiveCardUtil.bindIfAbsent(stack, user);

        if (level instanceof ServerLevel serverLevel && user instanceof ServerPlayer caster
                && applyTo != null && applyTo != user && HostileTargets.isHostile(applyTo)) {
            LivingPageFlightScheduler.launch(serverLevel, caster, applyTo);
        }
    }
}
