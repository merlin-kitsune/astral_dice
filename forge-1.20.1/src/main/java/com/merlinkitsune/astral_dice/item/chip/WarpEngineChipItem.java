package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 跃迁引擎筹码:触发指定传送后,获得 2 层充能并获得迅捷 0:10。
 *
 * <p>计入触发的传送:
 * <ul>
 *   <li>末影珍珠传送;</li>
 *   <li>末影骰子不死图腾后的安全瞬移;</li>
 *   <li>进入维度传送门(EntityTravelToDimensionEvent);</li>
 *   <li>Waystone 传送(在 NeoForge 分支通过可选联动接入)。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class WarpEngineChipItem extends BaseChipItem {
    /** 每次触发传送获得的充能层数 */
    public static final int STACK_GAIN = 2;
    /** 迅捷持续时间(0:10) */
    public static final int SPEED_DURATION_TICKS = 200;

    public WarpEngineChipItem(Properties properties) {
        super(properties);
    }

    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.WARP_ENGINE_CHIP.get())).isPresent();
    }

    /** 传送成功后调用:佩戴跃迁引擎时获得充能 + 迅捷 */
    public static void onTeleport(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        ChargeManager.addStacks(player, STACK_GAIN);
        EffectTimerGuard.apply(player, new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
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
            onTeleport(player);
        }
    }
}
