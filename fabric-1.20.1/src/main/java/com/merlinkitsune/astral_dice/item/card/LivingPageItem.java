package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.starenginelib.combat.CreatureTargets;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.event.LivingPageFlightScheduler;
import com.merlinkitsune.astral_dice.item.ModItems;
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
        registerSelectorAction(ACTION_ID, TargetType.CREATURE, false, LOCK_RANGE);
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
                && applyTo != null && applyTo != user && CreatureTargets.isCreatureTarget(applyTo)) {
            LivingPageFlightScheduler.launch(serverLevel, caster, applyTo);
        }
    }

    /**
     * 创建一枚属于 {@code owner} 的活体书页(专属牌)—— **所有产生路径的唯一入口**。
     *
     * <p>⚠️ 2026-10-03 修复(用户实报「调查员主动技能 / 队友事件获得的活体书页无法堆叠,
     * 必须完全统一 NBT」):此前 6 条产生路径各自 {@code new ItemStack(...)},其中
     * 「魔法箭袋返还」**漏绑** {@code OWNER_UUID} ⇒ 同一玩家手里会同时存在「有 owner / 无 owner」
     * 两种 NBT 形态,而堆叠判定用的是 {@code ItemStack.isSameItemSameComponents}
     * (逐组件比较) ⇒ 同物品**无法堆叠**。
     *
     * <p>本入口把「新建 + 绑定获得者」合成一步;凡**游戏内发放**活体书页都必须经它
     * (创造栏给出的是无主牌,故意不经本入口,由 {@link #applyEffect} 的
     * {@code bindIfAbsent} 在首次使用时补绑)。
     */
    public static ItemStack createFor(Player owner) {
        ItemStack page = new ItemStack(ModItems.LIVING_PAGE.get());
        ExclusiveCardUtil.setOwner(page, owner);
        return page;
    }
}
