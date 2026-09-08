package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 能量回收器筹码:每移动 50 米获得 1 点充能。
 *
 * <p>只累计水平移动距离(与扫地机立牌 jasmine 的移动累计一致);
 * 传送等单 tick 大幅位移不会计入移动距离。
 */
public class EnergyRecyclerChipItem extends BaseChipItem {
    /** 每获得 1 点充能所需移动距离(米/方块) */
    public static final float DISTANCE_THRESHOLD = 50f;
    /** 单 tick 位移超过该值视为传送/维度跳转,不累计为移动(避免传送刷充能) */
    private static final float MAX_MOVE_PER_TICK = 10f;

    private static final Map<UUID, Vec3> lastPosMap = new HashMap<>();
    private static final Map<UUID, Float> walkAccumMap = new HashMap<>();

    public EnergyRecyclerChipItem(Properties properties) {
        super(properties);
    }

    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.ENERGY_RECYCLER.get())).isPresent();
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        tickMovement(player);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        clearTracking(player);
    }

    private static void tickMovement(Player player) {
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

        // 传送/切维度等瞬移不视为移动,保留已有进度但不累计瞬移距离
        if (dist > MAX_MOVE_PER_TICK) {
            return;
        }

        float total = walkAccumMap.getOrDefault(uuid, 0f) + dist;
        while (total >= DISTANCE_THRESHOLD) {
            total -= DISTANCE_THRESHOLD;
            ChargeManager.addStacks(player, 1);
        }
        walkAccumMap.put(uuid, total);
    }

    private static void clearTracking(Player player) {
        if (player == null) return;
        lastPosMap.remove(player.getUUID());
        walkAccumMap.remove(player.getUUID());
    }
}
