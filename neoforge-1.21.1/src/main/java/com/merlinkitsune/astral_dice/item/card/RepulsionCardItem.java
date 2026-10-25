package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.audio.SoundPlayback;
import com.merlinkitsune.astral_dice.entity.ModEntityTypes;
import com.merlinkitsune.astral_dice.entity.RepulsionEntity;
import com.merlinkitsune.astral_dice.item.RepulsionPlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 强化拒止（专属效果牌，史诗 · 2026-10-18 新牌；属「怪力乱神」立牌，该立牌会在后续添加）。
 *
 * <p>用户需求（原文）：「创建效果牌：强化拒止（模型："强化拒止 - Converted.bbmodel"，品质：史诗，
 * 专属效果牌（属于怪力乱神立牌，该立牌会在后续添加））
 * <ul>
 *   <li>功能（描述文本）：在玩家指定地面上放置一个强化拒止，强化拒止会自动吸附8格范围内的敌对目标，
 *       并对其持续施加缓慢和虚弱效果。强化拒止每10秒消耗吸附目标数量的耐久度（单次最大消耗5点）</li>
 *   <li>功能补充：强化拒止是一个实体，没有碰撞箱。使用该效果牌时遵循原版放置方块规则将强化拒止实体
 *       放置在指定位置，强化拒止拥有100点耐久，会以1秒为频率将半径8格范围敌对目标向强化拒止的位置
 *       逐渐缓慢拉扯（但目标无法逃离强化拒止影响范围），强化拒止不会吸引仇恨，但会持续为怪物施加
 *       缓慢 (0:10)和虚弱 (0:10)，强化拒止耐久耗尽会有碎裂粒子效果，主动攻击并不会对齐造成伤害。」</li>
 * </ul>
 *
 * <p><b>为什么预检放在 {@code super.use} 之前</b>：与「路障」同款结构 —— 基类
 * {@link BaseEffectCardItem#use} 的顺序是「客户端预检 → 服务端 tryUseCard（专属校验 → 出牌锁 →
 * {@link #applyEffect} → 出牌音效 → 出牌登记 → 各使用钩子）→ {@code stack.consume(1, player)}」，
 * 即**效果与消耗是先后两个语句**，基类无法在 {@code applyEffect} 返回后回滚消耗。
 * 因此在 {@code use} 里先做一次落点预检、不合法直接 {@code fail} 返回（**不进基类 ⇒ 不消耗、
 * 不受出牌锁影响**），合法才交给基类走完整流程 —— 「放不下却吃掉卡」在结构上不可能出现。
 *
 * <p><b>落点规则（与原版放置方块等价，与路障同一份判据）</b>：
 * <ol>
 *   <li>视线射线 {@code level.clip(eye → eye + look × 4.5, COLLIDER, NONE, player)} 命中方块
 *       （4.5 格 = **原版方块交互距离**）；</li>
 *   <li>落点 = 命中方块 + 命中面方向；</li>
 *   <li>落点在世界边界内（{@code getWorldBorder().isWithinBounds(target)}），且玩家对该位置有
 *       交互权限（{@code level.mayInteract(player, target)} = 出生点保护 + 世界边界，覆写该方法的
 *       第三方领地插件同样生效）；</li>
 *   <li>落点可替换（{@code canBeReplaced()}）且**下方方块有朝上的实心面**
 *       （{@code isFaceSturdy(level, below, Direction.UP)}）；</li>
 *   <li>⚠️ **本牌不放置任何方块**（需求原文「强化拒止是一个实体，没有碰撞箱」）⇒ 无法像路障那样
 *       靠「落点方块不可替换」挡住同格重复放置。这里补一条**同格占用**判据：落点格已有存活的
 *       {@link RepulsionEntity} ⇒ 视为放不下（否则同一格可以无限叠放实体）。判据只看
 *       {@code blockPosition()}，**不**用实体 AABB（1.6 宽的 AABB 会溢出到四邻 / 斜邻格，
 *       导致相邻格放不下第二座 —— 与路障 2026-10-18 审查踩过的坑同源）。</li>
 * </ol>
 *
 * <p>合法后：实体生成在落点方块中心（{@code x+0.5, y, z+0.5}），朝向按玩家 yaw 四舍五入到 90° 的
 * 倍数（本模型是一块竖直薄片，朝向影响可见面，属**观感选择**，冻结设计未规定）；
 * 播 {@code SoundEvents.GLASS_PLACE} 并给动作栏绿字反馈。
 *
 * <p><b>专属牌</b>：{@link #isExclusive()} 恒 true；{@code ExclusiveCardUtil.isExclusive} /
 * {@code RandomCardHandler.registerExclusiveCard} / {@code is_exclusive} 标签三处同步登记
 * （不进随机池、无配方、不进赏金池）。无所有者时在首次成功放置后绑定获得者
 * （{@link ExclusiveCardUtil#bindIfAbsent}，与活体书页 / 命运的指引同款）。
 */
public class RepulsionCardItem extends BaseEffectCardItem {

    /** 卡牌类型 id（忍者立牌 / 魔法秘典 / 魔法箭袋的复制计数与「按类型找回本体」共用）。 */
    public static final String CARD_TYPE_ID = "repulsion";


    public static final String MSG_INVALID = "msg.astral_dice.effect_card_repulsion_invalid";
    public static final String MSG_PLACED = "msg.astral_dice.effect_card_repulsion_placed";

    public RepulsionCardItem(Properties properties) {
        super(properties);
    }

    @Override
    protected String cardTypeId() {
        return CARD_TYPE_ID;
    }

    /** 专属效果牌：仅限获得者使用（用户需求「专属效果牌」）。 */
    @Override
    protected boolean isExclusive() {
        return true;
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
     * <p>这里**再算一次**落点（物品是单例、可能被多名玩家同时右键，实例字段会串台）；
     * 两次计算发生在同一调用栈（{@code use → tryUseCard → applyEffect}），中间没有世界变更。
     */
    @Override
    protected void applyEffect(Level level, Player user, LivingEntity applyTo, ItemStack stack) {
        if (level.isClientSide) return;
        BlockPos target = resolvePlacement(level, user);
        if (target == null) return;

        RepulsionEntity repulsion = ModEntityTypes.REPULSION.get().create(level);
        if (repulsion == null) return;

        // 朝向：90° 的倍数（本模型是竖直薄片，朝向决定「宽面」朝哪边；属观感选择）
        float yaw = Math.round(user.getYRot() / 90.0F) * 90.0F;
        repulsion.moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, yaw, 0.0F);
        repulsion.setYHeadRot(yaw);
        repulsion.yBodyRot = yaw;
        // §4.6 前置：记录放置者（怪力乱神立牌的额外充能层只统计「自己放置的」力场）；随实体 NBT 落盘
        if (user != null) repulsion.setOwnerUUID(user.getUUID());
        // 没有碰撞方块 ⇒ 落实体失败（区块拒绝 / 实体上限等）时无需回滚任何方块
        if (!level.addFreshEntity(repulsion)) return;

        // 专属牌：无所有者时绑定获得者（首次成功放置即绑定）
        ExclusiveCardUtil.bindIfAbsent(stack, user);

        // 反馈：玻璃放置音 + 动作栏绿字
        SoundPlayback.playAt(level, target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                SoundEvents.GLASS_PLACE);
        user.displayClientMessage(Component.translatable(MSG_PLACED).withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * 落点解析：判据本体已抽到 {@link RepulsionPlacement}（2026-10-25 用户裁决「物品形态与效果牌
     * 放置行为一致」），这里只转发 —— 规则只存在一份，效果牌与物品形态永不漂移。
     *
     * @return 可放置的方块坐标；{@code null} = 当前视线落点不合法
     */
    private static BlockPos resolvePlacement(Level level, Player player) {
        return RepulsionPlacement.resolve(level, player);
    }
}