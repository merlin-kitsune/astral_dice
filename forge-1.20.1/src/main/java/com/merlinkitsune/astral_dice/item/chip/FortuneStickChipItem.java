package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starengine.combat.PartyRelations;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import com.merlinkitsune.starengine.item.CuriosCompat;

/**
 * 财运签筹码(史诗 / 星光)。
 *
 * <p><b>骰神赐福生效期间,装备者击杀第一个怪物时触发一次</b>(每赐福周期一次):
 * 自身 + 周围 {@value #RANGE} 格内友方玩家各随机获得 {@value #MIN_STARLIGHT}~
 * {@value #MAX_STARLIGHT} 点星光。
 *
 * <ul>
 *   <li><b>周期闸门</b> = 玩家级持久化附件 {@code fortune_stick_blessing_claimed}:置位于本类的击杀钩子,
 *       复位于 {@code DiceCombatEvents} 的**骰神赐福新周期开始处**(与「诅咒之剑」的
 *       {@code cursed_sword_blessing_triggered} 同批同处复位 ⇒ 同一赐福周期内只发一次);</li>
 *   <li><b>击杀口径</b> = **怪物**({@code target instanceof net.minecraft.world.entity.monster.Monster})
 *       —— 按需求「骰神赐福期间击杀第一个**怪物**」逐字落地:{@link PartyRelations#isHostileTo} 那条「敌对目标」口径
 *       会把**被激怒的中立生物**也放进来,故不再使用;订阅形态仍与「手电筒-强光 / 诅咒之剑」同款
 *       ({@code LivingDeathEvent} 先取击杀者);</li>
 *   <li><b>友方口径</b>:装备者有队伍 ⇒ 仅同队;无队伍 ⇒ 范围内所有玩家(自身恒在列)。</li>
 * </ul>
 *
 * <p><b>加权随机口径</b>(逐获得者按**其自身**当前星光 s 计算):
 * {@code b = clamp(s / 10, 0, 1)},取 v ∈ 1..7 的权重 {@code w(v) = v·(1-b) + (8-v)·b}
 * ⇒ s = 0 时权重就是 1,2,…,7(偏大号),s 越大越偏小号;{@code s ≥ 10 ⇒ 恒为 1}。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class FortuneStickChipItem extends BaseChipItem {
    /** 友方发放半径(格),按三维距离判定。 */
    public static final int RANGE = 16;

    /** 随机星光下限。 */
    public static final int MIN_STARLIGHT = 1;

    /** 随机星光上限。 */
    public static final int MAX_STARLIGHT = 7;

    /** 权重饱和点:{@code s ≥ 该值 ⇒ 恒为下限}(即"星光越多越容易抽到小号")。 */
    public static final int WEIGHT_SATURATION = 10;

    /** 权重式里的常数项 {@code (8 - v)} 的 8 = 下限 + 上限。 */
    private static final int WEIGHT_PIVOT = MIN_STARLIGHT + MAX_STARLIGHT;

    public FortuneStickChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴本筹码(总闸门:未佩戴骰子 ⇒ 本件功能一律不生效)。 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        if (!CurioSlotUtil.hasDiceEquipped(player)) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.FORTUNE_STICK_CHIP.get())).isPresent();
    }

    /** 骰神赐福期间击杀第一个**怪物**({@code Monster} 类)⇒ 触发一次(每赐福周期一次)。 */
    @SubscribeEvent
    public static void onHostileKill(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        // 先取击杀者(与 FlashlightChipItem/CursedSwordChipItem 同款的取法;本筹码的判据是「怪物」、与队伍无关,
        // 击杀者仅用于佩戴 / 赐福 / 友方发放)
        if (!(event.getSource().getEntity() instanceof Player killer)) return;
        if (!isEquipped(killer)) return;
        // 仅在骰神赐福生效期间生效
        if (!killer.hasEffect(ModEffects.DICE_BLESSING.get())) return;
        // 击杀口径 = 怪物(需求「击杀第一个怪物」的逐字落地):不再用「敌对目标」口径
        // PartyRelations.isHostileTo —— 后者会把被激怒的中立生物也算成合格目标,与需求不符
        if (!(target instanceof Monster)) return;
        // 每个赐福周期只触发一次(复位见 DiceCombatEvents 的赐福新周期开始处)
        if (ModAttachments.isFortuneStickBlessingClaimed(killer)) return;
        ModAttachments.setFortuneStickBlessingClaimed(killer, true);
        grantToAllies(killer);
    }

    /** 自身 + 范围内友方玩家各获得一次加权随机星光。 */
    private static void grantToAllies(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        StarLightManager.add(player, roll(player));
        double rangeSqr = (double) RANGE * RANGE;
        boolean selfHasTeam = PartyRelations.hasTeam(player);
        for (ServerPlayer other : serverLevel.players()) {
            if (other == player) continue;
            if (other.distanceToSqr(player) > rangeSqr) continue;
            if (selfHasTeam && !PartyRelations.isSameTeam(player, other)) continue;
            StarLightManager.add(other, roll(other));
        }
    }

    /** 加权随机抽 1~7 点星光(权重按该玩家**自身**当前星光计算;星光 ≥ {@value #WEIGHT_SATURATION} 时恒为 1)。 */
    public static int roll(Player player) {
        int starlight = StarLightManager.get(player);
        if (starlight >= WEIGHT_SATURATION) return MIN_STARLIGHT;
        double b = Math.max(0.0, Math.min(1.0, starlight / (double) WEIGHT_SATURATION));
        double total = 0.0;
        for (int value = MIN_STARLIGHT; value <= MAX_STARLIGHT; value++) {
            total += weight(value, b);
        }
        double roll = player.getRandom().nextDouble() * total;
        for (int value = MIN_STARLIGHT; value <= MAX_STARLIGHT; value++) {
            roll -= weight(value, b);
            if (roll < 0.0) return value;
        }
        return MAX_STARLIGHT;
    }

    /** v 的权重:{@code v·(1-b) + (8-v)·b}。 */
    private static double weight(int value, double b) {
        return value * (1.0 - b) + (WEIGHT_PIVOT - value) * b;
    }
}
