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

/**
 * 魔渊幼体立牌（sykes，传奇）—— fabric-1.21.1 移植版，语义与 neoforge-1.21.1 蓝本逐条等价。
 *
 * <p><b>主动「魔域转化」</b>：对着方块右键，在准星指向的相邻格放置一根 {@link SykeTentacleEntity}
 * （同时占格：放下 {@code abyss_tentacle_collision} 隐形方块），触须居中的 5×5 格地表即「魔渊区域」，
 * 任何进入的敌对目标都会被触须主动攻击。
 *
 * <p><b>被动「触须深渊」</b>：佩戴者攻击目标时使其获得 1 层「渊蚀印记」（2:00，每层使目标受到
 * 魔渊幼体与魔渊触须的伤害 +1，上限 20 层）；该被动每 10 秒最多触发一次（计时写在佩戴者的持久附件上，
 * 见 {@link AbyssMarkManager#readyToApply}）。
 *
 * <p><b>附带的自身抗性</b>：无法触发幽匿感测体 / 尖啸体（振动源被静音，见 {@code mixin/sculk/*}）、
 * 不会被尖啸体施加黑暗（{@code mixin/bridge/LivingEntityAbyssDarknessGateMixin}）、
 * 受到来自监守者的伤害 -50%。
 *
 * <h3>与 P0（neoforge-1.21.1）的平台差异</h3>
 * <ol>
 *   <li><b>事件订阅</b>：P0 是 {@code @EventBusSubscriber(modid = ...)} 自动注册；本线是自建 Forge 语义
 *       shim —— 类上标 {@link SubscribeEvent} 的 static 单参方法，<b>必须在
 *       {@code AstralDiceMod#registerListeners()} 里 {@code LoaderBus.INSTANCE.register(SykesSignItem.class)}</b>
 *       才会被派发。漏登记不会报错，只会静默不生效。</li>
 *   <li><b>装备判定</b>：P0 走 Curios 的 {@code getStacksHandler("stand")} 逐槽扫描；本线饰品后端是
 *       Trinkets（{@code compat/curios/TrinketBridge} 适配成 Curios 形态），统一走本线既有门面
 *       {@link SignEquipCheck#hasSign(Player, net.minecraft.world.item.Item)}（纯 stand 槽判定，
 *       不带骰子总闸门，与 P0 的纯槽位语义一致）。</li>
 *   <li><b>加伤事件</b>：P0 的 {@code LivingDamageEvent.Pre}（护甲、魔抗之后，吸收之前）在本线对应
 *       {@link LivingDamageEvent}，但它由 Puzzles 的 {@code LivingHurtCallback} 在 {@code actuallyHurt}
 *       <b>HEAD（偏移 0）</b>桥接，而护甲（偏移 12）/ 魔抗（19）/ 吸收（25-45）都在其之后 ⇒
 *       <b>本线载荷是护甲之前</b>，与 P0 不是同一结算位置（B27 已登记残余）；取
 *       {@code getEntity() / getSource() / getAmount() / setAmount()}。</li>
 *   <li><b>监守者减伤</b>：P0 的 {@code LivingIncomingDamageEvent}（{@code hurt} 内、{@code actuallyHurt}
 *       之前）在本线对应 {@link LivingHurtEvent}（由 {@code mixin/bridge/LivingHurtBridgeMixin} 用
 *       MixinExtras {@code @WrapOperation} 包裹 {@code actuallyHurt} 调用派发，同一位置）。</li>
 *   <li><b>黑暗免疫</b>：本线已接线 {@code MobEffectEvent.Applicable}（2026-11-07，
 *       {@code platform/StarengineBridges} 桥 {@code MobEffectEvents.AFFECTS}，派发点 =
 *       {@code LivingEntity#canBeAffected} HEAD）；但魔渊黑暗的门<b>仍保留</b> mixin 拦
 *       {@code LivingEntity#addEffect}，判定函数同为 {@link #blocksDarkness}。两者不冲突：
 *       mixin 在 {@code addEffect} HEAD 直接返回 false 时 {@code canBeAffected} 不跑，事件不会为该实例派发；
 *       移除该 mixin 属行为面变更（需重验黑暗免疫与触须路径），未做。</li>
 *   <li><b>主动分发</b>：本线 {@link BaseSignItem#use} 接管右键（潜行 = 自动装备），主动技能经
 *       {@code BaseSignItem} 的 selector 分发到 {@link #handleUse} —— 与 P0 相同，无差异。</li>
 * </ol>
 */
public class SykesSignItem extends BaseSignItem {

    /** 立牌注册 id。 */
    public static final String SIGN_ID = "astral_dice:sykes_sign";

    /** 主动施放距离（格）。 */
    private static final int PLACE_RANGE = 12;

    public SykesSignItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴着魔渊幼体立牌（纯 {@code stand} 槽判定）。 */
    public static boolean wearsSign(Player player) {
        return player != null && SignEquipCheck.hasSign(player, ModItems.SYKES_SIGN.get());
    }

    /** 供振动拦截 mixin 使用：该实体的振动不应触发幽匿感测体 / 尖啸体。 */
    public static boolean isSilencedVibrationSource(Entity entity) {
        return entity instanceof Player player && wearsSign(player);
    }

    /**
     * 供黑暗闸门 mixin 使用：佩戴者不吃黑暗。
     *
     * @param entity   被施加效果的实体
     * @param instance 待施加的效果实例（可为 null）
     */
    public static boolean blocksDarkness(LivingEntity entity, MobEffectInstance instance) {
        if (entity == null || instance == null || instance.getEffect() == null) return false;
        if (instance.getEffect().value() != MobEffects.DARKNESS.value()) return false;
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

        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SCULK_CATALYST_BLOOM,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
        sendSignActionBar(player, "msg.astral_dice.abyss_summoned");
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * 被动「触须深渊」：佩戴者造成伤害时给目标上 1 层印记（10 秒冷却），并按已有层数追加伤害。
     *
     * <p>顺序很重要：<b>先按现有层数加伤、再施印</b>，否则本次命中就会把刚加上的那一层也算进去。
     */
    @SubscribeEvent
    public static void onAttackHostile(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide()) return;
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

    /** 受到来自监守者的伤害 -50%（{@code hurt} 内、{@code actuallyHurt} 之前的同位钩子）。 */
    @SubscribeEvent
    public static void onWardenDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!wearsSign(player)) return;
        if (!(event.getSource().getEntity() instanceof Warden)) return;
        event.setAmount(event.getAmount() * 0.5F);
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        // 立牌本身不持有状态：印记挂在目标身上、施印冷却挂在佩戴者持久附件上，跨卸下保留
        super.onUnequip(slotContext, newStack, stack);
    }

    // 2026-11-11 P1 补齐：与 P0 同口径的机器可读拒绝。
    @Override
    protected boolean activateForEntity(net.minecraft.world.entity.LivingEntity owner) {
        return com.merlinkitsune.astral_dice.item.sign.SignEntitySkills.deny("sykes", owner, "needs_player_aim");
    }
}
