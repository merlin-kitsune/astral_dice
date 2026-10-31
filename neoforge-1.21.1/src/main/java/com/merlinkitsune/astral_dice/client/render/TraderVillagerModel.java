package com.merlinkitsune.astral_dice.client.render;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.npc.Villager;

/**
 * 原版 {@link VillagerModel} 的等价模型，只把 {@code head/hat} 与其子节点 {@code hat_rim} 关掉。
 *
 * <p>{@code hat}（{@code texOffs(32,0)}，inflate 0.51）与它下面的 {@code hat_rim}
 * （{@code texOffs(30,47)}，16x16x1 平板、绕 X 轴 -90 度）是原版给村民戴帽子的两个盒；
 * Blockbench 源 {@code models/cardshop_villager.bbmodel} 里没有这两件，所以本模组的商人
 * 立牌村民要把它们隐掉。{@code ModelPart.render} 开头即 {@code if (!this.visible) return;}，
 * 因此隐藏 {@code hat} 等于连同 {@code hat_rim} 一起不画；其余几何、UV、动画全部沿用原版。
 */
public class TraderVillagerModel extends VillagerModel<Villager> {

    public TraderVillagerModel(ModelPart root) {
        super(root);
        ModelPart head = root.getChild("head");
        if (head.hasChild("hat")) {
            head.getChild("hat").visible = false;
        }
    }
}
