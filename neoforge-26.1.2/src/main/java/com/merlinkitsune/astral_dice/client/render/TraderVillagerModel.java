// 不加 @OnlyIn(Dist.CLIENT)：NeoForge 26.1 已移除该注解的运行时剥离，留着会在加载期弹警告并阻断进主菜单。
// 纯度由调用点保证（ModClientEvents 带 @EventBusSubscriber(value = Dist.CLIENT)）。
package com.merlinkitsune.astral_dice.client.render;

import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * 与原版 {@link VillagerModel} 完全一致的村民模型，只把 {@code head/hat/hat_rim} 两个盒关掉。
 *
 * <p>{@code hat}（{@code texOffs(32,0)}，inflate 0.51）与它下面的 {@code hat_rim}
 * （{@code texOffs(30,47)}，16×16×1 平板、绕 X 轴 -90°）就是实机里那道「粉红帽沿 + 眼高两条细横条」。
 * bbmodel（{@code models/cardshop_villager.bbmodel}）里没有这两件，所以要让它消失只能在这里关。
 *
 * <p>{@code ModelPart.render} 开头就有 {@code if (!this.visible) return;}，因此把 {@code hat} 置为不可见，
 * 其子节点 {@code hat_rim} 一并不会被画。除此之外几何、UV、{@code setupAnim}（继承自 {@link VillagerModel}）
 * 全部沿用原版，没有新增任何盒子。
 */
public class TraderVillagerModel extends VillagerModel {

    public TraderVillagerModel(ModelPart root) {
        super(root);
        ModelPart head = root.getChild("head");
        if (head.hasChild("hat")) {
            head.getChild("hat").visible = false;
        }
    }
}
