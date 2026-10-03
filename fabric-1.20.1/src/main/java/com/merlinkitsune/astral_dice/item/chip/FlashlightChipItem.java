package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.starenginelib.combat.HostileTargets;
import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDeathEvent;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.combat.PartyRelations;

/**
 * 手电筒-强光筹码。
 *
 * <ul>
 *   <li><b>击杀</b>「不少于 {@value #MIN_KILL_MAX_HEALTH} 血」的敌对目标时星光 +1
 *       （判据 = 目标的<b>最大生命值</b>，与「诅咒之剑」{@code CursedSwordChipItem} 一字不差）；</li>
 *   <li>每拥有 4 层星光,攻击力 +1(结算在 {@code DiceCombatModifiers} 攻击修饰器)。</li>
 * </ul>
 *
 * <p>2026-09-28 用户平衡性调整：原口径为「攻击敌对目标即 +1 星光，同一目标仅一次」
 * （靠 {@code ModAttachments#getFlashlightGrantedTargets} 记录已发放 UUID 去重），现改为<b>击杀</b>触发。
 * 「击杀」天然一次性（目标死亡后不再存在）⇒ <b>不再需要 UUID 去重记录</b>，也随之取消了
 * 「已记录目标达 256 上限后不再发放」这一隐性截断（该截断在击杀口径下会直接破坏
 * 「每击杀一个合格目标 +1」的语义）。星光上限仍由 {@link StarLightManager} 统一管理。
 */
public class FlashlightChipItem extends BaseChipItem {
    /** 击杀合格敌对目标时获得的星光层数。 */
    public static final int STARLIGHT_PER_KILL = 1;

    /** 「不少于 20 血」的判定阈值(比对目标**最大生命值**)。 */
    public static final float MIN_KILL_MAX_HEALTH = 20.0F;

    public FlashlightChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴本筹码
    public static boolean isEquipped(Player player) {
        // 总闸门(骰子装备和卸除调整):未佩戴骰子 ⇒ 本件功能一律不生效(数值保留)
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.FLASHLIGHT_CHIP.get())).isPresent();
    }

    /**
     * 击杀敌对目标时调用：击杀者佩戴本筹码、且目标最大生命 ≥ {@value #MIN_KILL_MAX_HEALTH} ⇒ 星光 +1。
     *
     * <p>判据与 {@code CursedSwordChipItem#onCursedSwordKill} 一字不差（同款「不少于 20 血的敌对目标」
     * 口径）：先取击杀者、再判定敌对（视者 = 击杀者），最后比对<b>最大生命值</b>。
     */
    @SubscribeEvent
    public static void onHostileKill(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        // 先取击杀者再判定敌对:视者 = 击杀者(全局敌对玩家规则)
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        if (!isEquipped(killer)) return;
        if (!PartyRelations.isHostileTo(killer, target)) return;
        if (target.getMaxHealth() < MIN_KILL_MAX_HEALTH) return;
        StarLightManager.add(killer, STARLIGHT_PER_KILL);
    }
}
