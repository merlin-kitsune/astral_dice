package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 能量回收器筹码:每移动 150 米获得 1 点充能;充能不少于 5 点时移动速度 +5%。
 *
 * <p>按**三维位移(含垂直)**累计移动距离(与扫地机立牌 jasmine 的移动累计一致);
 * 传送等单 tick 大幅位移不会计入移动距离。
 *
 * <p>移动速度加成为**条件属性**(取决于当前充能层数,可随时升降),
 * 因此不使用 Curios 的 {@code getAttributeModifiers}(仅在装备变更时求值),
 * 改为在 {@link #curioTick} 中实时维护**瞬态属性修饰器**:达到门槛时添加、
 * 低于门槛或卸下时立即移除,保证加成随充能变化即时生效/消失。
 */
public class EnergyRecyclerChipItem extends BaseChipItem {
    /** 每获得 1 点充能所需移动距离(米/方块) */
    public static final float DISTANCE_THRESHOLD = 150f;
    /** 单 tick 位移超过该值视为传送/维度跳转,不累计为移动(避免传送刷充能) */
    private static final float MAX_MOVE_PER_TICK = 10f;
    /** 触发移动速度加成所需的充能层数门槛 */
    public static final int SPEED_BONUS_CHARGE_REQUIRED = 5;
    /** 充能达到门槛时的移动速度加成(+5%) */
    public static final double SPEED_BONUS = 0.05;
    /** 移动速度修饰器 id 后缀(经 BaseChipItem.attributeModifierId 按物品派生) */
    private static final String SPEED_MODIFIER_SUFFIX = "charge_speed";

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
        updateSpeedBonus(player);
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        clearTracking(player);
        // 卸下立即清除移动速度加成,防止残留
        removeSpeedBonus(player);
    }

    /**
     * 充能 ≥ {@link #SPEED_BONUS_CHARGE_REQUIRED} 时给予移动速度加成(+5%),
     * 低于门槛时立即移除;修饰器数值变化时重建(仅在需要时改动,避免每 tick 同步)。
     */
    private void updateSpeedBonus(Player player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        Identifier id = attributeModifierId(SPEED_MODIFIER_SUFFIX);
        var existing = attr.getModifier(id);
        if (ChargeManager.getStacks(player) < SPEED_BONUS_CHARGE_REQUIRED) {
            if (existing != null) attr.removeModifier(id);
            return;
        }
        if (existing == null || existing.amount() != SPEED_BONUS) {
            if (existing != null) attr.removeModifier(id);
            attr.addTransientModifier(new AttributeModifier(id, SPEED_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private void removeSpeedBonus(Player player) {
        if (player == null) return;
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) attr.removeModifier(attributeModifierId(SPEED_MODIFIER_SUFFIX));
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
        double dy = pos.y - lastPos.y;
        double dz = pos.z - lastPos.z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
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
