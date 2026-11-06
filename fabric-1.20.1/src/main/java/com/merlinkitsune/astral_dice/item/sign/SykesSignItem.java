package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.compat.curios.SlotContext;
import com.merlinkitsune.astral_dice.entity.ModEntityTypes;
import com.merlinkitsune.astral_dice.entity.SykeTentacleEntity;
import com.merlinkitsune.astral_dice.item.AbyssMarkManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDamageEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingHurtEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.MobEffectEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

/**
 * 魔渊幼体立牌（sykes，传奇）。移植自 neoforge-1.21.1 同路径（提交 34f5a8f3），语义逐条等价。
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
 *
 * <h3>平台差异（fabric 1.20.1 / 逐条理由）</h3>
 * <ul>
 *   <li>① 订阅：P0 用 {@code @EventBusSubscriber}；本线**不存在**该注解，必须由
 *       {@code AstralDiceMod#registerListeners} 显式 {@code LoaderBus.INSTANCE.register(SykesSignItem.class)}
 *       （漏登记 = 编译通过、被动与抗性永不触发）；</li>
 *   <li>② 佩戴判定：P0 手写 Curios 遍历；本线既有 {@link SignEquipCheck#hasSign}（纯 stand 槽、不含骰子闸门）
 *       ⇒ 直接复用，同时避免与本线 Curios 门面脱钩；</li>
 *   <li>③ 被动加伤：P0 走 {@code LivingDamageEvent.Pre#setNewDamage}；本线的 {@code LivingDamageEvent}
 *       没有 {@code getNewDamage/setNewDamage} ⇒ 用 {@code event.setAmount(event.getAmount() + stacks)}
 *       （桥接点同为 {@code actuallyHurt} 头部，仍在护甲/抗性之前，口径一致）；</li>
 *   <li>④ 监守者减伤：P0 用 {@code LivingIncomingDamageEvent}（本线无此事件）⇒ 改 {@code LivingHurtEvent}
 *       （{@code LivingEntity#hurt} 内对 {@code actuallyHurt} 的调用点，同样在护甲之前）；</li>
 *   <li>⑤ 效果闸门：P0 用 {@code MobEffectEvent.Applicable}；本线**不派发** Applicable
 *       （见 {@code event/NewSignEvents} 的注释与 {@code platform/PuzzlesBridges#installEffects}）⇒
 *       拆成两条：触须免疫在 {@code SykeTentacleEntity#canBeAffected}（1.20.1 该方法非 final 可覆写，
 *       1.21.1 起是 final 才逼出 P0 的事件写法）+ 每 tick {@code removeAllEffects()}；
 *       黑暗则订阅 {@code MobEffectEvent.Added} 后同 tick 移除（最多差 1 tick 的可视残留，见报告 E 段）；</li>
 *   <li>⑥ {@code level.isClientSide} 字段 ⇒ 本线方法 {@code level.isClientSide()}；</li>
 *   <li>⑦ {@code SlotContext} ⇒ 本线 Curios 门面 {@code compat.curios.SlotContext}。</li>
 * </ul>
 */
public class SykesSignItem extends BaseSignItem {

    /** 立牌注册 id。 */
    public static final String SIGN_ID = "astral_dice:sykes_sign";

    /** 主动施放距离（格）。 */
    private static final int PLACE_RANGE = 12;

    /** 饰品槽判定（与其它立牌同款）。 */
    private static final String SLOT_ID = "stand";

    public SykesSignItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴着魔渊幼体立牌（纯 stand 槽判定，不含骰子闸门 —— 与振动静音的口径一致）。 */
    public static boolean wearsSign(Player player) {
        return SignEquipCheck.hasSign(player, ModItems.SYKES_SIGN.get());
    }

    /** 供振动拦截 mixin 使用：该实体的振动不应触发幽匿感测体 / 尖啸体。 */
    public static boolean isSilencedVibrationSource(Entity entity) {
        return entity instanceof Player player && wearsSign(player);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) return InteractionResultHolder.sidedSuccess(stack, true);

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
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                > (double) (PLACE_RANGE * PLACE_RANGE)) {
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

        level.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 1.0F, 0.8F);
        sendSignActionBar(player, "msg.astral_dice.abyss_summoned");
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * 被动「触须深渊」：佩戴者造成伤害时给目标上 1 层印记（10 秒冷却），并按已有层数追加伤害。
     *
     * <p>顺序很重要：**先按现有层数加伤、再施印**，否则本次命中就会把刚加上的那一层也算进去。
     */
    @SubscribeEvent
    public static void onAttackHostile(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (!(event.getSource().getDirectEntity() instanceof Player attacker)) return;
        if (attacker == victim) return;
        if (!wearsSign(attacker)) return;
        if (!(victim instanceof Enemy)) return;

        int stacks = AbyssMarkManager.getStacks(victim);
        if (stacks > 0) {
            event.setAmount(event.getAmount() + stacks);
        }
        if (AbyssMarkManager.readyToApply(attacker)) {
            AbyssMarkManager.apply(victim);
            AbyssMarkManager.markApplied(attacker);
        }
    }

    /** 受到来自监守者的伤害 -50%。 */
    @SubscribeEvent
    public static void onWardenDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!wearsSign(player)) return;
        if (!(event.getSource().getEntity() instanceof Warden)) return;
        event.setAmount(event.getAmount() * 0.5F);
    }

    /**
     * 佩戴者不吃尖啸体的黑暗（P0 的 {@code MobEffectEvent.Applicable} 分支在本线等价改写）。
     *
     * <p>本线不派发 Applicable ⇒ 改在 {@code Added} 之后同 tick 移除。效果确实已进过
     * {@code activeEffects}，但玩家端在同一 tick 内就被清掉，只在极端卡顿的一帧里可能短暂可见。
     * 只针对原版 {@code minecraft:darkness} ⇒ 不触碰任何本模组命名空间的效果保护逻辑。
     */
    @SubscribeEvent
    public static void onDarknessApplied(MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || instance.getEffect() != MobEffects.DARKNESS) return;
        if (!(entity instanceof Player player)) return;
        if (!wearsSign(player)) return;
        player.removeEffect(MobEffects.DARKNESS);
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        // 立牌本身不持有状态：印记挂在目标身上、施印冷却挂在佩戴者持久数据上，跨卸下保留
        super.onUnequip(slotContext, newStack, stack);
    }
}
