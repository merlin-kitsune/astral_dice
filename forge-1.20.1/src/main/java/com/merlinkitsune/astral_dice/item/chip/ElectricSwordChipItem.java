package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.starenginelib.combat.HostileTargets;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 电流剑筹码:
 * <ul>
 *   <li>每拥有 4 点充能,攻击力 +1(经 DiceCombatModifiers 攻击修饰器结算);</li>
 *   <li>击杀 10 个敌对目标后,获得 2 点充能。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class ElectricSwordChipItem extends BaseChipItem {
    /** 每多少点充能提供 1 点攻击力 */
    public static final int CHARGE_PER_ATTACK = 4;
    /** 击杀多少个敌对目标后回充 */
    public static final int KILLS_REQUIRED = 10;
    /** 回充获得的充能层数 */
    public static final int CHARGE_GAIN = 2;

    private static final Map<UUID, Integer> KILL_COUNTS = new HashMap<>();

    public ElectricSwordChipItem(Properties properties) {
        super(properties);
    }

    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.ELECTRIC_SWORD.get())).isPresent();
    }

    /** 当前充能提供的攻击力加成(向下取整:每 4 点 +1) */
    public static int getAttackBonus(Player player) {
        if (!isEquipped(player)) return 0;
        return ChargeManager.getStacks(player) / CHARGE_PER_ATTACK;
    }

    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        if (player != null) {
            KILL_COUNTS.remove(player.getUUID());
        }
    }

    /** 玩家击杀敌对目标后累计;每满 10 个获得 2 点充能并重置计数 */
    public static void onHostileKilled(Player killer) {
        if (killer == null || killer.level().isClientSide()) return;
        if (!isEquipped(killer)) return;

        UUID uuid = killer.getUUID();
        int kills = KILL_COUNTS.getOrDefault(uuid, 0) + 1;
        if (kills >= KILLS_REQUIRED) {
            KILL_COUNTS.remove(uuid);
            ChargeManager.addStacks(killer, CHARGE_GAIN);
        } else {
            KILL_COUNTS.put(uuid, kills);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity().level().isClientSide()) return;
        // 先取击杀者再判定敌对:视者 = 击杀者(全局敌对玩家规则)
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        if (!HostileTargets.isHostile(killer, event.getEntity())) return;
        onHostileKilled(killer);
    }
}
