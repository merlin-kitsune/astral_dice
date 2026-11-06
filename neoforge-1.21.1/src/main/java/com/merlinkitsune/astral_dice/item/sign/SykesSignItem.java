package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.entity.ModEntityTypes;
import com.merlinkitsune.astral_dice.entity.SykeTentacleEntity;
import com.merlinkitsune.astral_dice.item.AbyssMarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 魔渊幼体立牌（sykes，传奇）。
 *
 * <p><b>主动「魔域转化」</b>：对着方块右键，在准星指向的相邻格放置一根 {@code SykeTentacleEntity}
 * （同时占格：放下 {@code abyss_tentacle_collision} 隐形方块），触须居中的 5×5 格地表即「魔渊区域」，
 * 任何进入的敌对目标都会被触须主动攻击。
 *
 * <p><b>被动「触须深渊」</b>：佩戴者攻击目标时使其获得 1 层「渊蚀印记」（2:00，每层使目标受到
 * 魔渊幼体与魔渊触须的伤害 +1，上限 20 层）；该被动每 10 秒最多触发一次（计时写在佩戴者持久数据上）。
 *
 * <p><b>附带的自身抗性</b>（用户裁决 m12998）：无法触发幽匿感测体 / 尖啸体（振动源被静音，
 * 见 {@code mixin/sculk/*}）、不会被尖啸体施加黑暗、受到来自监守者的伤害 -50%。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class SykesSignItem extends BaseSignItem {

    /** 立牌注册 id。 */
    public static final String SIGN_ID = AstralDiceMod.MODID + ":sykes_sign";

    /** 主动施放距离（格）。 */
    private static final int PLACE_RANGE = 12;

    /** 饰品槽判定（与其它立牌同款）。 */
    private static final String SLOT_ID = "stand";

    public SykesSignItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴着魔渊幼体立牌。 */
    public static boolean wearsSign(Player player) {
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return false;
        var handler = curios.get().getStacksHandler(SLOT_ID);
        if (handler.isEmpty()) return false;
        var stacks = handler.get().getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (stacks.getStackInSlot(i).is(ModItems.SYKES_SIGN.get())) return true;
        }
        return false;
    }

    /** 供振动拦截 mixin 使用：该实体的振动不应触发幽匿感测体 / 尖啸体。 */
    public static boolean isSilencedVibrationSource(Entity entity) {
        return entity instanceof Player player && wearsSign(player);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);

        if (!(player.pick(PLACE_RANGE, 0.0F, false) instanceof BlockHitResult hit)) {
            sendSignActionBar(player, "msg.astral_dice.abyss_no_ground");
            return InteractionResultHolder.fail(stack);
        }
        BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
        BlockState target = level.getBlockState(pos);
        if (!target.canBeReplaced()) {
            sendSignActionBar(player, "msg.astral_dice.abyss_no_ground");
            return InteractionResultHolder.fail(stack);
        }
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            sendSignActionBar(player, "msg.astral_dice.abyss_no_ground");
            return InteractionResultHolder.fail(stack);
        }
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > (double) (PLACE_RANGE * PLACE_RANGE)) {
            sendSignActionBar(player, "msg.astral_dice.abyss_no_ground");
            return InteractionResultHolder.fail(stack);
        }

        SykeTentacleEntity tentacle = ModEntityTypes.SYKE_TENTACLE.get().create(level);
        if (tentacle == null) {
            return InteractionResultHolder.fail(stack);
        }
        tentacle.bindTo(pos, player.getUUID());
        level.setBlockAndUpdate(pos, ModBlocks.ABYSS_TENTACLE_COLLISION.get().defaultBlockState());
        level.addFreshEntity(tentacle);

        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SCULK_CATALYST_BLOOM,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
        sendSignActionBar(player, "msg.astral_dice.abyss_summoned");
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * 被动「触须深渊」：佩戴者造成伤害时给目标上 1 层印记（10 秒冷却），并按已有层数追加伤害。
     *
     * <p>顺序很重要：**先按现有层数加伤、再施印**，否则本次命中就会把刚加上的那一层也算进去。
     */
    @SubscribeEvent
    public static void onAttackHostile(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(event.getSource().getDirectEntity() instanceof Player attacker)) return;
        if (attacker == victim) return;
        if (!wearsSign(attacker)) return;
        if (!(victim instanceof Enemy)) return;

        int stacks = AbyssMarkManager.getStacks(victim);
        if (stacks > 0) {
            event.setNewDamage(event.getNewDamage() + stacks);
        }
        if (AbyssMarkManager.readyToApply(attacker)) {
            AbyssMarkManager.apply(victim);
            AbyssMarkManager.markApplied(attacker);
        }
    }

    /** 受到来自监守者的伤害 -50%。 */
    @SubscribeEvent
    public static void onWardenDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!wearsSign(player)) return;
        if (!(event.getSource().getEntity() instanceof Warden)) return;
        event.setAmount(event.getAmount() * 0.5F);
    }

    /**
     * 状态下发闸门（1.21.1 的 {@code LivingEntity#addEffect(MobEffectInstance)} 是 final，无法覆写）：
     * 魔渊触须一律不吃任何状态效果；佩戴者不吃尖啸体的黑暗。
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof SykeTentacleEntity) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) return;
        if (instance.getEffect().value() != MobEffects.DARKNESS.value()) return;
        if (event.getEntity() instanceof Player player && wearsSign(player)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        // 立牌本身不持有状态：印记挂在目标身上、施印冷却挂在佩戴者持久数据上，跨卸下保留
        super.onUnequip(slotContext, newStack, stack);
    }
}
