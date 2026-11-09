package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.entity.SouriDrinkEntity;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 金鲤立牌（souri，传奇；配方待定）。
 *
 * <h2>主动「把酒言欢」</h2>
 * 在准星指定的地块上方放置一个【葫芦饮】；若自身已获得【金鲤化龙】，改放【仙酿】。
 * 随后自身 +1 层【觉醒】、+1 层【反击】。主动为瞬时型，冷却 {@link #ACTIVE_COOLDOWN_TICKS}
 * （1:00，与其它立牌同档；无锁定态）。
 *
 * <h2>被动「不醉不归」</h2>
 * <ul>
 *   <li>反击时（{@code combat/DiceCombatEvents} 消耗 1 层反击的出口）+1 层【金鳞】；</li>
 *   <li>任意角色拾取【葫芦饮】/【仙酿】后，佩戴者 +1 层【反击】。</li>
 * </ul>
 *
 * <h2>联动「留给你的」</h2>
 * 拾取者佩戴蛟龙立牌时：拾取葫芦饮 +1 层蛟龙觉醒，拾取仙酿 +2 层蛟龙觉醒
 * （见 {@link SouriSignManager#onDrinkPicked}）。
 */
public class SouriSignItem extends BaseSignItem {

    /** 立牌物品注册 id */
    public static final String SIGN_ID = SouriSignManager.SIGN_ID;

    /** 主动冷却 1:00 */
    public static final int ACTIVE_COOLDOWN_TICKS = 1200;

    /** 放置射程（格） */
    public static final double PLACE_RANGE = 8.0D;

    public SouriSignItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴金鲤立牌 */
    public static boolean isEquipped(Player player) {
        return SignEquipCheck.isEquipped(player, ModItems.SOURI_SIGN.get());
    }

    /** 是否已获得【金鲤化龙】 */
    public static boolean hasDragon(Player player) {
        return player != null && player.hasEffect(ModEffects.SOURI_DRAGON);
    }

    @Override
    protected int activeCooldownBaseTicks(Player player) {
        return ACTIVE_COOLDOWN_TICKS;
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        BlockPos target = resolvePlacePos(player);
        if (target == null) {
            sendSignActionBar(player, "msg.astral_dice.souri_no_ground");
            return InteractionResultHolder.fail(stack);
        }
        boolean dragon = hasDragon(player);
        SouriDrinkEntity drink = new SouriDrinkEntity(level, player.getUUID(),
                dragon ? SouriDrinkEntity.Variant.BREW : SouriDrinkEntity.Variant.GOURD);
        drink.setPos(target.getX() + 0.5D, target.getY() + 0.25D, target.getZ() + 0.5D);
        level.addFreshEntity(drink);

        SouriSignManager.addGoldScale(player);
        SouriSignManager.addCounter(player, 1);
        sendSignActionBar(player, dragon
                ? "msg.astral_dice.souri_place_brew"
                : "msg.astral_dice.souri_place_gourd");
        return InteractionResultHolder.success(stack);
    }

    /** 准星命中方块的上表面；命中液体/空气或超出射程 ⇒ null。 */
    private static BlockPos resolvePlacePos(Player player) {
        HitResult hit = player.pick(PLACE_RANGE, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = blockHit.getBlockPos().relative(blockHit.getDirection());
        if (!player.level().getBlockState(pos).canBeReplaced()) return null;
        return pos;
    }

    /** 非玩家（类玩家生物）的主动入口：在自身前方放置饮品并 +1 反击。 */
    @Override
    protected boolean activateForEntity(LivingEntity owner) {
        if (owner == null || owner.level().isClientSide()) return false;
        boolean dragon = owner instanceof Player p && hasDragon(p);
        SouriDrinkEntity drink = new SouriDrinkEntity(owner.level(), owner.getUUID(),
                dragon ? SouriDrinkEntity.Variant.BREW : SouriDrinkEntity.Variant.GOURD);
        drink.setPos(owner.getX() + owner.getLookAngle().x * 1.5D,
                owner.getY() + 0.25D,
                owner.getZ() + owner.getLookAngle().z * 1.5D);
        owner.level().addFreshEntity(drink);
        if (owner instanceof Player p) {
            SouriSignManager.addGoldScale(p);
            SouriSignManager.addCounter(p, 1);
        }
        return true;
    }

    @Override
    public void curioTick(top.theillusivec4.curios.api.SlotContext slotContext, ItemStack stack) {
        super.curioTick(slotContext, stack);
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide()
                && player.tickCount % 20 == 0) {
            SouriSignManager.resync(player);
        }
    }

    @Override
    public void onUnequip(top.theillusivec4.curios.api.SlotContext slotContext,
                          ItemStack newStack, ItemStack stack) {
        super.onUnequip(slotContext, newStack, stack);
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide()) {
            SouriSignManager.clearAll(player);
        }
    }
}
