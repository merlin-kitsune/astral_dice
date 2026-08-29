package com.merlinkitsune.astral_dice.resource;

import net.minecraft.world.entity.player.Player;

/**
 * 玩家级点数流派统一接口(治愈/星光/未来反击等)。
 * 所有筹码/立牌对点数的操作统一经由本接口调用,禁止直接操作数据存储。
 * 各流派实现见 {@link PlayerResourceRegistry}。
 */
public interface PlayerResource {

    /** 当前点数 */
    int get(Player player);

    /** 点数上限(随玩家属性/配置变化) */
    int getCap(Player player);

    /**
     * 增加点数(自动限制在上限内),返回增加后的值。
     *
     * @param amount 增加量(可为负,负值按消耗处理)
     */
    int add(Player player, int amount);

    /**
     * 消耗点数(不可为负),返回实际消耗的量。
     */
    int spend(Player player, int amount);

    /** 清零点数(死亡/卸载等场景) */
    void clear(Player player);
}
