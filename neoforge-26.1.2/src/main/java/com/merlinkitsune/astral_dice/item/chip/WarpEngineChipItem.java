package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.event.WaystoneWarpCompat;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 跃迁引擎筹码:触发指定传送后,获得 2 层充能并获得迅捷 0:10。
 *
 * <p>计入触发的传送:
 * <ul>
 *   <li>末影珍珠传送;</li>
 *   <li>末影骰子不死图腾后的安全瞬移;</li>
 *   <li>进入维度传送门(EntityTravelToDimensionEvent);</li>
 *   <li>Waystone 传送(通过 WaystoneWarpCompat 可选接入,避免跨维度时双重计数)。</li>
 * </ul>
 *
 * <p>传送门与 Waystone 触发共享 5:00 冷却:通过任意一种方式获得充能后,
 * 5 分钟内不能再通过传送门或 Waystone 触发;末影珍珠与末影骰子瞬移不受此冷却限制。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class WarpEngineChipItem extends BaseChipItem {
    /** 每次触发传送获得的充能层数 */
    public static final int STACK_GAIN = 2;
    /** 迅捷持续时间(0:10) */
    public static final int SPEED_DURATION_TICKS = 200;
    /** 传送门/Waystone 触发的共享冷却时长(5:00) */
    public static final int PORTAL_WAYSTONE_COOLDOWN_TICKS = 20 * 60 * 5;

    public WarpEngineChipItem(Properties properties) {
        super(properties);
    }

    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.WARP_ENGINE_CHIP.get())).isPresent();
    }

    /** 通用传送成功结算(末影珍珠/末影骰子等不受传送门冷却限制的来源) */
    public static void onTeleport(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        grantTeleportReward(player);
    }

    /** 传送门/Waystone 专属结算:共享 5:00 冷却 */
    public static void onPortalOrWaystoneTeleport(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        long now = player.level().getGameTime();
        if (now < ModAttachments.getWarpEnginePortalCooldownEnd(player)) return;
        ModAttachments.setWarpEnginePortalCooldownEnd(player, now + PORTAL_WAYSTONE_COOLDOWN_TICKS);
        grantTeleportReward(player);
    }

    private static void grantTeleportReward(Player player) {
        ChargeManager.addStacks(player, STACK_GAIN);
        EffectTimerGuard.apply(player, new MobEffectInstance(MobEffects.SPEED,
                SPEED_DURATION_TICKS, 0, false, true));
    }

    // 末影珍珠落地传送(事件在服务端传送前触发)
    @SubscribeEvent
    public static void onEnderPearlTeleport(EntityTeleportEvent.EnderPearl event) {
        if (event.isCanceled()) return;
        onTeleport(event.getPlayer());
    }

    // 维度传送门(EntityTravelToDimensionEvent 在真正进入新维度前触发)
    @SubscribeEvent
    public static void onDimensionTravel(EntityTravelToDimensionEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity() instanceof Player player) {
            // Waystone 跨维度传送也会经过此事件;Pre 标记后这里只消费标记,不重复给充能,
            // 由 WaystoneTeleportEntityEvent.Post 在实际传送成功后统一结算。
            if (WaystoneWarpCompat.isPendingAndClear(player)) return;
            onPortalOrWaystoneTeleport(player);
        }
    }
}
