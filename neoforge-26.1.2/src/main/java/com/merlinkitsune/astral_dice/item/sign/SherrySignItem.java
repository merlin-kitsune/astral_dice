// SHERRY_AIM_PATCH 2026-09-22（怪力侦探：法伤结算 / 准星落点 / 隔墙过滤 / 落地冻结）
package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.combat.HostileTargets;
import com.merlinkitsune.astral_dice.combat.SherryThrowManager;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.SherryReasoningEffect;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.starenginelib.event.EventTargetCollector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * 怪力侦探立牌(sherry,史诗)。
 *
 * <h3>主动「怪力投掷」</h3>
 * 把 {@value #THROW_RADIUS} 格内**所有敌对目标**按抛物线扔到**准星指向的地面**
 * (距玩家 {@value #AIM_MIN_DISTANCE}–{@value #AIM_MAX_DISTANCE} 格,须为**地面或水面**;
 * 准星未指向合格面则退至**面前 {@value #AIM_MAX_DISTANCE} 格**,被掩体/高方块阻挡时**向内逐格靠近**,
 * 直到面前 {@value #AIM_MIN_DISTANCE} 格也不可落才拒绝施放、零消耗);**被方块遮挡(视线不可达)的目标不参与投掷**
 * (用户 2026-09-22 裁决「阻止怪被从墙后拉出来」);
 * **落地之后**再对被投掷目标造成 {@value #THROW_BASE_DAMAGE} 点伤害(走本模组**法伤**类型
 * {@code astral_dice:card_spell},并显示法伤数字)并施加 1 层「标记」;
 * 落地后目标保留 **2 秒 noAi**(保险冻结,见 {@code SherryThrowManager#LANDING_FREEZE_TICKS});
 * 「推理时间」满 {@value #MAX_REASONING} 层时**额外**造成 {@value #THROW_BONUS_DAMAGE} 点伤害。
 * 冷却基础 {@value #ACTIVE_COOLDOWN_SECONDS} 秒(与枪匠立牌同款:仍受诡异骰子/充能链的既有减免)。
 * 投掷的飞行过程由 {@link SherryThrowManager} 负责(逐 tick 插值抛物线,期间目标 noAi)。
 *
 * <h3>被动「侦探出击」</h3>
 * 每攻击一个**最大生命值 ≥ {@value #HIGH_HEALTH_THRESHOLD} 的敌对目标**,获得 1 层「推理时间」
 * (上限 {@value #MAX_REASONING} 层);每层 **+1 攻击力、减少 1 点受到的伤害**;
 * **骰神赐福结束后扣除 1 层**。
 *
 * <p><b>层数真值在附件 {@code ModAttachments#SHERRY_REASONING_LAYERS}</b>(带 {@code .copyOnDeath()},
 * ⇒ **死亡不清**「推理时间」,与「弱点识破」那种"层数放效果里"的写法**不同**);
 * 效果实例 {@link SherryReasoningEffect} 只是**显示载体**(图标 = 立牌同图 + 层数),由每 tick 镜像维护。
 *
 * <h3>被动「挚友守护」</h3>
 * 同队伍内若有玩家装备**人偶师立牌**,则使**该玩家**受到的伤害 −1。
 * ⚠️ 人偶师立牌(`astral_dice:hanna_sign`)尚未落地 ⇒ 本判定按**物品注册 id 字符串**匹配
 * (不引用 {@code ModItems.HANNA_SIGN},以免编译期依赖未创建的物品);hanna 落地后本被动**自动生效**。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class SherrySignItem extends BaseSignItem {

    /** 立牌注册 id(锁定态/调试用) */
    public static final String SIGN_ID = AstralDiceMod.MODID + ":sherry_sign";

    /** 「推理时间」上限 */
    public static final int MAX_REASONING = 5;
    /** 主动冷却基础秒数(与枪匠立牌一致:120 秒) */
    public static final int ACTIVE_COOLDOWN_SECONDS = 120;
    /** 主动投掷范围(格) */
    public static final double THROW_RADIUS = 12.0D;
    /** 落点距玩家的**最小**距离(格) —— 也是向内回退的下限(用户 2026-09-22 裁决「面前 1–6 格」) */
    public static final double AIM_MIN_DISTANCE = 1.0D;
    /** 落点距玩家的**最大**距离(格) —— 也是准星未命中地面时的**默认落点**(面前 6 格) */
    public static final double AIM_MAX_DISTANCE = 6.0D;
    /** 向内回退的步长(格) —— 基准落点被掩体/高方块阻挡时逐格靠近 */
    public static final double DROP_STEP = 1.0D;
    /** 落点上方需要的净空(格) —— 不足即视为「被高于该值的方块阻挡」(用户 2026-09-22 补充) */
    public static final double DROP_CLEARANCE = 3.0D;
    /** 面前 {@value #AIM_MIN_DISTANCE}–{@value #AIM_MAX_DISTANCE} 格内**均无可落地面**时 {@link #castThrow} 的返回值 */
    public static final int THROW_BAD_GROUND = -1;
    /** 落地基础伤害 */
    public static final int THROW_BASE_DAMAGE = 2;
    /** 「推理时间」满层时的额外伤害 */
    public static final int THROW_BONUS_DAMAGE = 5;
    /** 「侦探出击」计层所需的目标最大生命值门槛 */
    public static final float HIGH_HEALTH_THRESHOLD = 20.0F;
    /** 人偶师立牌注册 id(尚未落地 ⇒ 用字符串匹配,见类 javadoc) */
    public static final String HANNA_SIGN_ID = AstralDiceMod.MODID + ":hanna_sign";

    public SherrySignItem(Properties properties) {
        super(properties);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  「推理时间」层数(真值 = 附件;效果只是镜像)
    // ══════════════════════════════════════════════════════════════════════════

    /** 当前「推理时间」层数(0..{@link #MAX_REASONING}) */
    public static int getLayers(Player holder) {
        if (holder == null) return 0;
        return ModAttachments.getSherryReasoningLayers(holder);
    }

    /** 直接写入层数(夹逼到 0..MAX;同时刷新 HUD 镜像) */
    public static void setLayers(Player holder, int value) {
        if (holder == null || holder.level().isClientSide()) return;
        int clamped = Math.max(0, Math.min(MAX_REASONING, value));
        ModAttachments.setSherryReasoningLayers(holder, clamped);
        SherryReasoningEffect.mirror(holder, clamped);
    }

    /** 层数增减(夹逼;同时刷新 HUD 镜像) */
    public static void addLayers(Player holder, int amount) {
        if (holder == null || amount == 0) return;
        setLayers(holder, getLayers(holder) + amount);
    }

    /** 玩家是否佩戴怪力侦探立牌 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.SHERRY_SIGN.get())).isPresent();
    }

    @Override
    protected void onCurioTick(top.theillusivec4.curios.api.SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 层数镜像每 tick 校正(死亡重生后附件仍在而效果实例已丢 ⇒ 靠这里重建)
        int layers = getLayers(player);
        if (SherryReasoningEffect.getStacks(player) != layers) {
            SherryReasoningEffect.mirror(player, layers);
        }
    }

    /** 卸下立牌:清「推理时间」层数与显示效果(与「弱点识破」同款「卸下即归零」口径) */
    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        ModAttachments.setSherryReasoningLayers(player, 0);
        SherryReasoningEffect.clear(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动「怪力投掷」
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected InteractionResult handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        int thrown = castThrow(player);
        if (thrown == THROW_BAD_GROUND) {
            // 面前 1–6 格内均无可落地面(被方块阻挡) ⇒ 提示 + 拒绝使用主动(返回 FAIL = 不进冷却、不消耗)
            sendSignActionBar(player, "msg.astral_dice.sherry_bad_ground");
            return InteractionResult.FAIL;
        }
        if (thrown <= 0) {
            sendSignActionBar(player, "msg.astral_dice.sherry_no_target");
            return InteractionResult.FAIL;
        }
        sendSignActionBar(player, "msg.astral_dice.sherry_throw", thrown);
        return InteractionResult.SUCCESS;
    }

    /**
     * 主动冷却 tick:佩戴时基础 {@value #ACTIVE_COOLDOWN_SECONDS} 秒 —— **与枪匠立牌同款链**
     * (先按充能上限封顶,再按诡异骰子减半;低于充能上限 160 秒 ⇒ 有充能时不受影响)。
     */
    @Override
    protected int activeCooldownBaseTicks(Player player) {
        int ticks = isEquipped(player)
                ? ACTIVE_COOLDOWN_SECONDS * 20
                : GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
        ticks = (int) ChargeManager.signCooldownTicks(player, ticks);
        if (WeirdDiceHandler.hasWeirdDice(player)) {
            ticks = Math.max(1, ticks / 2);
        }
        return ticks;
    }

    /**
     * 收集 {@value #THROW_RADIUS} 格内的全部敌对目标并登记投掷。
     *
     * <p><b>候选过滤</b>：敌对 + **视线可达**（{@code hasLineOfSight}）—— 隔墙的目标不投掷
     * （用户 2026-09-22 裁决「阻止怪被从墙后拉出来」）。
     *
     * <p><b>落点</b> = {@link #resolveThrowDestination} 解析出的地面/水面（准星指向处优先，
     * 否则退到面前 {@value #AIM_MAX_DISTANCE} 格并被掩体向内逼退）；多个目标围绕该落点沿
     * （距玩家 {@value #AIM_MIN_DISTANCE}–{@value #AIM_MAX_DISTANCE} 格）；多个目标围绕该落点沿
     * 与视线垂直的水平方向按 ±0.9 格均匀铺开（避免全部叠在同一格）。
     *
     * @return 被投掷的目标数；{@link #THROW_BAD_GROUND}（-1）= 面前无任何可落地面 ⇒ 调用方发提示并拒绝施放；
     *         0 = 范围内无可投掷目标（两者都按「零消耗」处理）
     */
    public static int castThrow(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        Level level = player.level();
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(THROW_RADIUS), e -> e != player && e.isAlive())) {
            if (!HostileTargets.isHostile(player, candidate)) continue;
            // 隔墙不拉:只投掷玩家**视线可达**的目标(与「从墙后把怪拉出来」互斥)
            if (!player.hasLineOfSight(candidate)) continue;
            targets.add(candidate);
        }
        if (targets.isEmpty()) return 0;

        // 落点:准星指向的地面/水面(1–6 格)优先,否则退到面前 6 格并被掩体逐格向内逼退;
        // 面前 1–6 格内全部不可落 ⇒ 拒绝施放(零消耗)
        Vec3 aim = resolveThrowDestination(player);
        if (aim == null) return THROW_BAD_GROUND;

        boolean maxed = getLayers(player) >= MAX_REASONING;
        int bonus = maxed ? THROW_BONUS_DAMAGE : 0;

        // 水平视线方向(去掉俯仰,仅用于多点散布的垂直向量)
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize();
        // 与视线垂直的水平向量,用于多点散布
        Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);

        int n = targets.size();
        for (int i = 0; i < n; i++) {
            // 单目标 ⇒ 准星落点;多目标 ⇒ 围绕落点沿垂直方向 ±0.9 格铺开
            double offset = n == 1 ? 0.0D
                    : -0.9D + 1.8D * ((double) i / (double) (n - 1));
            Vec3 dest = aim.add(side.scale(offset));
            SherryThrowManager.schedule(targets.get(i), dest, player, bonus);
        }
        return n;
    }

    /**
     * 解析投掷落点（用户 2026-09-22 裁决 + 当晚补充）：
     * <ol>
     *   <li>准星在 {@value #AIM_MAX_DISTANCE} 格内指向<b>地面或水面</b> ⇒ 基准落点 = 该处；</li>
     *   <li>准星指向更远处或天空（{@value #AIM_MAX_DISTANCE} 格内未命中地面/水面）⇒ 基准落点 =
     *       <b>面前 {@value #AIM_MAX_DISTANCE} 格</b>；</li>
     *   <li>基准落点被掩体/高方块阻挡 ⇒ <b>向内逐 {@value #DROP_STEP} 格靠近</b>后重试；</li>
     *   <li>退到面前 {@value #AIM_MIN_DISTANCE} 格仍无可落地面（被高于 {@value #DROP_CLEARANCE} 格的
     *       方块阻挡）⇒ 返回 {@code null} ⇒ 调用方发提示并<b>拒绝施放</b>（零消耗）。</li>
     * </ol>
     *
     * <p>「可落地面」的三条判据见 {@link #groundBelow}。
     *
     * @return 落点坐标；面前 {@value #AIM_MIN_DISTANCE}–{@value #AIM_MAX_DISTANCE} 格内全部不可落 ⇒ {@code null}
     */
    private static Vec3 resolveThrowDestination(Player player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 feet = player.position();

        // 水平前方(去掉俯仰;垂直俯视导致水平分量退化时用 +Z 兜底,与 castThrow 的多点散布同款)
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize();

        // ① 准星射线(含俯仰,最长 6 格):命中地面/水面且距离落在 [1,6] ⇒ 基准距离取该处
        double base = AIM_MAX_DISTANCE;
        Vec3 end = eye.add(look.scale(AIM_MAX_DISTANCE));
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
                eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.ANY, player));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            double dist = hit.getLocation().distanceTo(feet);
            if (dist >= AIM_MIN_DISTANCE && dist <= AIM_MAX_DISTANCE) {
                base = dist;
            }
        }

        // ②③④ 自基准距离向内逐格靠近,取第一个可落地面;全部失败 ⇒ null(拒绝施放)
        for (double d = base; ; d -= DROP_STEP) {
            double probe = Math.max(d, AIM_MIN_DISTANCE);
            Vec3 dest = groundBelow(level, player, feet.x + flat.x * probe, feet.z + flat.z * probe);
            if (dest != null) return dest;
            if (probe <= AIM_MIN_DISTANCE + 1.0E-6D) return null;
        }
    }

    /**
     * 求水平坐标 {@code (x, z)} 处的「可落地面」；三条判据任一不满足即返回 {@code null}：
     * <ol>
     *   <li>该列向下 {@code 8} 格内存在碰撞面（地面 / 水面），且面高 ≤ 玩家脚底 +1 格
     *       （更高 ⇒ 那是墙顶或高台，不是「面前的地面」）；</li>
     *   <li>该面之上 {@value #DROP_CLEARANCE} 格内无碰撞 —— 即用户所说「超过 3 格高的方块阻挡」的反面；</li>
     *   <li>自玩家<b>眼睛</b>到该落点的路径无方块遮挡（掩体判据）。</li>
     * </ol>
     */
    private static Vec3 groundBelow(Level level, Player player, double x, double z) {
        double feetY = player.getY();
        net.minecraft.world.phys.BlockHitResult down = level.clip(new net.minecraft.world.level.ClipContext(
                new Vec3(x, feetY + 1.0D, z), new Vec3(x, feetY - 8.0D, z),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.ANY, player));
        if (down.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        double surfaceY = down.getLocation().y;
        // 面高上限:高于玩家脚底 1 格 ⇒ 该列是墙/高台(被方块阻挡),向内回退
        if (surfaceY > feetY + 1.0D) return null;
        // 净空:落点之上 DROP_CLEARANCE 格内无碰撞(被高方块盖住 ⇒ 不合格)
        if (!level.noCollision(player, new net.minecraft.world.phys.AABB(
                x - 0.3D, surfaceY, z - 0.3D, x + 0.3D, surfaceY + DROP_CLEARANCE, z + 0.3D))) {
            return null;
        }
        Vec3 dest = new Vec3(x, surfaceY + 0.05D, z);
        // 掩体:自眼睛到落点的路径不得被方块挡住(命中点明显早于落点 ⇒ 其间有遮挡物)
        net.minecraft.world.phys.BlockHitResult los = level.clip(new net.minecraft.world.level.ClipContext(
                player.getEyePosition(), dest, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (los.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && los.getLocation().distanceToSqr(dest) > 0.25D) {
            return null;
        }
        return dest;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「侦探出击」/「挚友守护」
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 攻击命中 ≥{@value #HIGH_HEALTH_THRESHOLD} 血敌对目标 ⇒ +1 层。
     * 由 {@link #onAttackHostile} 在本模组的伤害前事件里分发(只认玩家近战/直接攻击)。
     */
    public static void onAttackedHighHealthHostile(Player attacker) {
        if (attacker == null || attacker.level().isClientSide()) return;
        if (!isEquipped(attacker)) return;
        if (getLayers(attacker) >= MAX_REASONING) return;
        addLayers(attacker, 1);
    }

    /** 骰神赐福结束后扣除 1 层(由 {@code combat/DiceCombatEvents} 的赐福结束段调用) */
    public static void onDiceBlessingEnded(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (getLayers(player) > 0) {
            addLayers(player, -1);
        }
    }

    /**
     * 「挚友守护」:玩家 {@code victim} 若与某个佩戴怪力侦探立牌的玩家**同队**,则其受到的伤害 −1。
     *
     * @return 应当扣减的固定点数(0 = 不适用)
     */
    public static float guardianReductionFor(Player victim) {
        if (victim == null || victim.level().isClientSide()) return 0.0F;
        if (!hasTeam(victim)) return 0.0F;      // 无队伍 ⇒ 不生效
        for (Player ally : EventTargetCollector.collectTeamPlayers(victim)) {
            if (ally == victim) continue;
            if (!isEquipped(ally)) continue;
            if (wearsSign(victim, HANNA_SIGN_ID)) return 1.0F;
        }
        return 0.0F;
    }

    /** 是否存在队伍(无队伍时「同队伍内」条件不成立) */
    private static boolean hasTeam(Player player) {
        return EventTargetCollector.hasAnyTeam(player);
    }

    /** 该玩家是否在 curios 槽里装着指定注册 id 的立牌(按 id 字符串匹配,见类 javadoc) */
    private static boolean wearsSign(Player player, String itemId) {
        var curios = CuriosApi.getCuriosInventory(player);
        if (curios.isEmpty()) return false;
        return curios.get().findFirstCurio(s -> {
            var key = BuiltInRegistries.ITEM.getKey(s.getItem());
            return key != null && itemId.equals(key.toString());
        }).isPresent();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  全局钩子:「侦探出击」计层
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 攻击方侧伤害前钩子:玩家**直接攻击**(近战)命中敌对目标且其最大生命 ≥ 门槛 ⇒ +1 层。
     *
     * <p>只认 {@code getDirectEntity()} 是玩家本人的近战 —— 弹射物、法术、AOE 与环境伤害一律不计
     * (与「每攻击一个」的语义一致;本模组既有的「玩家近战」判据同款)。
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onAttackHostile(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (victim.getMaxHealth() < HIGH_HEALTH_THRESHOLD) return;
        if (!HostileTargets.isHostile(victim)) return;
        if (!(event.getSource().getDirectEntity() instanceof Player attacker)) return;
        if (attacker == victim) return;
        onAttackedHighHealthHostile(attacker);
    }
}
