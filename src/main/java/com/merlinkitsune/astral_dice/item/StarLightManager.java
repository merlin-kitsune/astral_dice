package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.resource.PlayerResource;
import com.merlinkitsune.astral_dice.item.chip.BankCardChipItem;
import com.merlinkitsune.astral_dice.resource.PlayerResourceRegistry;

/**
 * "星光"管理器(玩家级共享资源,与具体饰品解耦)。
 * 数据存储于玩家 attachment(PLAYER_STARLIGHT),本类统一提供获取/增加/设置与上限逻辑。
 *
 * <p>星光为固定点数(不随时间衰减,无计数器),只有增加与减少。
 * 与治愈流派一致,星光具有<b>基础值(下限)</b>:
 * <ul>
 *   <li>基础值默认 0,可由未来"固定增加星光"的筹码在装备期间提供(预留 {@link #getBasePoints} 接入点);</li>
 *   <li>星光被消耗并低于基础值时,自动补充回基础值。</li>
 * </ul>
 * 获取来源:经商立牌被动/赐福加成、手电筒筹码攻击加成、八面骰累计、看板立牌兑换等。
 */
public final class StarLightManager {
    // 流派注册实现(供 PlayerResourceRegistry 注册)
    public static final com.merlinkitsune.astral_dice.resource.PlayerResource RESOURCE =
            new com.merlinkitsune.astral_dice.resource.PlayerResource() {
                @Override
                public int get(net.minecraft.world.entity.player.Player player) {
                    return StarLightManager.get(player);
                }

                @Override
                public int getCap(net.minecraft.world.entity.player.Player player) {
                    return StarLightManager.getCap();
                }

                @Override
                public int add(net.minecraft.world.entity.player.Player player, int amount) {
                    return StarLightManager.add(player, amount);
                }

                @Override
                public int spend(net.minecraft.world.entity.player.Player player, int amount) {
                    return StarLightManager.spend(player, amount);
                }

                @Override
                public void clear(net.minecraft.world.entity.player.Player player) {
                    StarLightManager.set(player, 0);
                }
            };

    private StarLightManager() {
    }

    /** 当前星光点数 */
    public static int get(Player player) {
        return ModAttachments.getStarlight(player);
    }

    /** 星光点数上限(配置控制) */
    public static int getCap() {
        return GameplayConstants.MAX_STARLIGHT;
    }

    /**
     * 星光基础值(下限),默认 0。
     * 由"固定增加星光"的筹码在装备期间提供常驻基础值(卸下自动回落,与治愈基础点模式一致):
     * - 银行卡-余额少:+4;
     * - 银行卡-余额多:+7。
     */
    public static int getBasePoints(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return 0;
        var inventory = curios.get();
        int base = 0;
        if (inventory.findFirstCurio(s -> s.is(ModItems.BANK_CARD_LOW.get())).isPresent()) {
            base += BankCardChipItem.BASE_LOW;
        }
        if (inventory.findFirstCurio(s -> s.is(ModItems.BANK_CARD_HIGH.get())).isPresent()) {
            base += BankCardChipItem.BASE_HIGH;
        }
        return base;
    }

    public static void set(Player player, int value) {
        int base = getBasePoints(player);
        // 不低于基础值(下限),不高于上限
        ModAttachments.setStarlight(player, Math.max(base, Math.min(value, getCap())));
    }

    // 增加星光(自动限制在上限内),返回增加后的值
    public static int add(Player player, int amount) {
        int next = Math.min(get(player) + amount, getCap());
        set(player, next);
        return next;
    }

    // 消耗星光(不可为负),返回实际消耗的量;消耗后若低于基础值则自动补充回基础值
    public static int spend(Player player, int amount) {
        int current = get(player);
        int spent = Math.min(current, amount);
        set(player, current - spent);
        return spent;
    }
}
