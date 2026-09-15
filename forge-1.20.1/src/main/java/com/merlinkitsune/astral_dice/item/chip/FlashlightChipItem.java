package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.combat.HostileTargets;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 手电筒-强光筹码。
 *
 * <ul>
 *   <li>攻击敌对目标时星光 +1,<b>同一目标仅 +1 层</b>——已发放过的目标 UUID 记录在
 *       {@link ModAttachments#getFlashlightGrantedTargets},再次攻击同一目标不再发放;</li>
 *   <li>每拥有 4 层星光,攻击力 +1(结算在 {@code DiceCombatModifiers} 攻击修饰器)。</li>
 * </ul>
 *
 * <p>已记录目标上限 {@link #MAX_TRACKED_TARGETS}(记录满后不再发放,以保证"同一目标仅 +1 层"严格成立);
 * 卸下筹码或玩家死亡时清空记录。星光上限由 {@link StarLightManager} 统一管理。
 */
public class FlashlightChipItem extends BaseChipItem {
    /** 同一目标仅发放 1 层星光:已发放目标记录上限(先进先出) */
    public static final int MAX_TRACKED_TARGETS = 256;
    /** 攻击敌对目标时获得的星光层数 */
    public static final int STARLIGHT_PER_TARGET = 1;

    public FlashlightChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴本筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.FLASHLIGHT_CHIP.get())).isPresent();
    }

    /**
     * 攻击敌对目标时调用(由骰战伤害入口分发):该目标尚未发放过星光时 +1 层并记录其 UUID,
     * 已记录过的目标不再发放(同一目标仅 +1 层)。
     */
    public static void onAttack(Player player, LivingEntity target) {
        if (player == null || target == null) return;
        if (player.level().isClientSide()) return;
        if (!HostileTargets.isHostile(player, target)) return;
        if (!isEquipped(player)) return;
        String uuid = target.getUUID().toString();
        List<String> granted = readGrantedTargets(player);
        if (granted.contains(uuid)) return;
        // 严格保证「同一目标仅 +1 层」:记录已满时**不再发放**(不做先进先出淘汰,
        // 否则被淘汰的目标可再次获得,破坏"仅一次"语义);卸下筹码或死亡后重置记录。
        if (granted.size() >= MAX_TRACKED_TARGETS) return;
        granted.add(uuid);
        writeGrantedTargets(player, granted);
        StarLightManager.add(player, STARLIGHT_PER_TARGET);
    }

    /** 清空已发放目标记录(卸下筹码/玩家死亡时调用) */
    public static void clearGrantedTargets(Player player) {
        if (player == null) return;
        ModAttachments.setFlashlightGrantedTargets(player, "");
    }

    private static List<String> readGrantedTargets(Player player) {
        String raw = ModAttachments.getFlashlightGrantedTargets(player);
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    private static void writeGrantedTargets(Player player, List<String> granted) {
        ModAttachments.setFlashlightGrantedTargets(player, String.join(",", granted));
    }

    // 卸下筹码:清空已发放目标记录(重新装备后每个目标可再次获得 1 层)
    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        clearGrantedTargets(player);
    }
}
