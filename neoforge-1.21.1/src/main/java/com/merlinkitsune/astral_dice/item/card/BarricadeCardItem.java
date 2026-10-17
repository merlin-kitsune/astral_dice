package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.block.ModBlocks;
import com.merlinkitsune.astral_dice.entity.BarricadeEntity;
import com.merlinkitsune.astral_dice.entity.ModEntityTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 路障（效果牌，稀有 · 2026-10-17 新牌）。
 *
 * <p>用户需求（原文）：「在玩家指定地面上放置一个路障，路障会吸引6格范围内非精英和Boss敌对生物的仇恨」
 * ＋「使用该效果牌时遵循原版放置方块规则将路障实体放置在指定位置，路障本身30点耐久，
 * 被怪物攻击会降低一点，会像栅栏一样阻止玩家和生物越过」。
 *
 * <p><b>为什么预检放在 {@code super.use} 之前</b>：基类 {@link BaseEffectCardItem#use} 的顺序是
 * 「客户端预检 → 服务端 tryUseCard（专属校验 → 出牌锁 → {@link #applyEffect} → 出牌音效 →
 * 出牌登记 → 各使用钩子）→ {@code stack.consume(1, player)}」，即**效果与消耗是先后两个语句**，
 * 基类无法在 {@code applyEffect} 返回后回滚消耗。因此在 {@code use} 里先做一次落点预检、
 * 不合法直接 {@code fail} 返回（**不进基类 ⇒ 不消耗、不受出牌锁影响**），
 * 合法才交给基类走完整流程 —— 这样「放不下却吃掉卡」在结构上就不可能出现。
 *
 * <p>落点规则（与原版放置方块等价）：
 * <ol>
 *   <li>视线射线 {@code level.clip(eye → eye + look × 4.5, COLLIDER, NONE, player)} 命中方块；
 *       （4.5 格 = **原版方块交互距离**，不是 5 格）</li>
 *   <li>落点 = 命中方块 + 命中面方向；</li>
 *   <li>落点在世界边界内（{@code getWorldBorder().isWithinBounds(target)}），且玩家对该位置有
 *       交互权限（{@code level.mayInteract(player, target)} —— 两线的 {@code ServerLevel} 实现 =
 *       **出生点保护 + 世界边界**；覆写该方法的第三方领地插件同样一并生效）；</li>
 *   <li>落点可替换（{@code canBeReplaced()}）且**下方方块有朝上的实心面**
 *       （{@code isFaceSturdy(level, below, Direction.UP)}）；</li>
 *   <li>落点方块不是 {@code barricade_collision}（同格重复放置由「方块不可替换」挡住）。</li>
 * </ol>
 *
 * <p><b>为什么**不再**判「落点与现存路障实体 AABB 重叠」</b>：路障实体 AABB 宽 1.2 格，
 * 拿 {@code new AABB(target)} 取交集时会溢出到四邻 / 斜邻格 ⇒ **相邻格再也放不下第二座**
 * （2026-10-18 审查实测：只能隔 1 格放）。「落点方块必须可替换」这一条已经足以挡住同格重复放置，
 * 故该判据已删除。
 *
 * <p>合法后：碰撞方块落到落点，实体生成在方块中心（{@code x+0.5, y, z+0.5}），
 * 朝向按玩家 yaw 四舍五入到 180° 的倍数（两块板沿世界轴对齐）；
 * 播 {@code SoundEvents.WOOD_PLACE} 并给动作栏绿字反馈。
 */
public class BarricadeCardItem extends BaseEffectCardItem {

    /** 卡牌类型 id（忍者立牌 / 魔法秘典的复制计数与「按类型找回本体」共用）。 */
    public static final String CARD_TYPE_ID = "barricade";

    /** 放置距离（格；视线射线长度）= 4.5 格，即**原版方块交互距离**（玩家手能触及的方块面距离）。 */
    public static final double PLACE_REACH = 4.5D;

    public static final String MSG_INVALID = "msg.astral_dice.effect_card_barricade_invalid";
    public static final String MSG_PLACED = "msg.astral_dice.effect_card_barricade_placed";

    public BarricadeCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String cardTypeId() {
        return CARD_TYPE_ID;
    }

    /**
     * 放置路径（**普通右键**，不是目标选择器牌 ⇒ {@link #selectorActionId()} 保持 null）。
     *
     * <p>预检失败：客户端与服务端都直接 fail（服务端额外发一条红字动作栏）；
     * 预检通过：交给基类，由基类完成出牌锁 / 出牌数 / 冷却 / 消耗。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (resolvePlacement(level, player) == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable(MSG_INVALID).withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }

    /**
     * 实际放置（**仅服务端**；基类的 tryUseCard 只在服务端调用本方法）。
     *
     * <p>这里**再算一次**落点，而不是把 {@link #use} 里的结果缓存在字段上：
     * 物品是单例、可能被多名玩家同时右键，任何实例字段都会串台；
     * 两次计算发生在**同一调用栈**（{@code use → tryUseCard → applyEffect}），中间没有世界变更，
     * 落点因而一致；真出现差异就静默不放置（与基类「applyEffect 无法回滚消耗」的结构一致）。
     */
    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        if (level.isClientSide) return;
        BlockPos target = resolvePlacement(level, user);
        if (target == null) return;

        // ① 隐形碰撞方块（挡路 / 阻断寻路）：先落方块再落实体，保证实体首 tick 校验能过
        BlockState collision = ModBlocks.BARRICADE_COLLISION.get().defaultBlockState();
        if (!level.setBlockAndUpdate(target, collision)) return;

        // ② 可见实体：方块中心，朝向沿世界轴对齐（墙面与轴线平行）
        BarricadeEntity barricade = ModEntityTypes.BARRICADE.get().create(level);
        if (barricade == null) {
            // 实体创建失败（类型未注册等）：把刚落的方块撤掉，绝不留一个"看不见的墙"
            level.removeBlock(target, false);
            return;
        }
        float yaw = Math.round(user.getYRot() / 180.0F) * 180.0F;
        barricade.moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, yaw, 0.0F);
        barricade.setYHeadRot(yaw);
        barricade.yBodyRot = yaw;
        barricade.setCollisionPos(target);
        if (!level.addFreshEntity(barricade)) {
            // 实体没能加入世界（区块拒绝 / 实体上限等）：撤掉刚落的方块，绝不留一个"看不见的墙"
            level.removeBlock(target, false);
            return;
        }

        // ③ 反馈：方块放置音 + 动作栏绿字
        SoundPlayback.playAt(level, target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                SoundEvents.WOOD_PLACE);
        user.displayClientMessage(Component.translatable(MSG_PLACED).withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * 落点解析（客户端 / 服务端**同一份**判据）。
     *
     * @return 可放置的方块坐标；{@code null} = 当前视线落点不合法
     */
    private static BlockPos resolvePlacement(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(PLACE_REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return null;

        BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        if (!level.isInWorldBounds(target)) return null;

        // ① 世界边界 + 交互权限（ServerLevel#mayInteract = 出生点保护 + 世界边界；覆写该方法的
        //    第三方领地插件同样生效）—— 与原版放置方块同一道前置
        if (!level.getWorldBorder().isWithinBounds(target)) return null;
        if (!level.mayInteract(player, target)) return null;

        // ② 落点本身可替换（空气 / 水 / 草…）
        BlockState atTarget = level.getBlockState(target);
        if (!atTarget.canBeReplaced()) return null;

        // ③ 下方必须是「朝上的实心面」= 原版「放在方块面上」的等价判据
        BlockPos below = target.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return null;

        // ④ 该格不是路障碰撞方块。**不再**判实体 AABB 重叠：实体宽 1.2 格会溢出到四邻 / 斜邻格，
        //    导致相邻格放不下第二座（审查实测只能隔 1 格放）；「可替换」已足够挡住同格重复放置
        if (atTarget.is(ModBlocks.BARRICADE_COLLISION.get())) return null;

        return target;
    }
}
