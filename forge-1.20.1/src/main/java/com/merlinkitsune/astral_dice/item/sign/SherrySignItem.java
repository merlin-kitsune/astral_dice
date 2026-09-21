package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.HostileTargets;
import com.merlinkitsune.astral_dice.combat.SherryThrowManager;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.SherryReasoningEffect;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.starenginelib.event.EventTargetCollector;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import top.theillusivec4.curios.api.SlotContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 怪力侦探立牌(sherry,史诗)1.20.1 Forge 移植版。
 *
 * <h3>主动「怪力投掷」</h3>
 * 把 {@value #THROW_RADIUS} 格内**所有敌对目标**按抛物线扔到**玩家面前 {@value #THROW_FACE_DISTANCE} 格内**,
 * **落地之后**再对被投掷目标造成 {@value #THROW_BASE_DAMAGE} 点伤害并施加 1 层「标记」;
 * 「推理时间」满 {@value #MAX_REASONING} 层时**额外**造成 {@value #THROW_BONUS_DAMAGE} 点伤害。
 * 冷却基础 {@value #ACTIVE_COOLDOWN_SECONDS} 秒(与枪匠立牌同款:仍受诡异骰子/充能链的既有减免)。
 * 投掷的飞行过程由 {@link SherryThrowManager} 负责(逐 tick 插值抛物线,期间目标 noAi)。
 *
 * <h3>被动「侦探出击」</h3>
 * 每攻击一个**最大生命值 ≥ {@value #HIGH_HEALTH_THRESHOLD} 的敌对目标**,获得 1 层「推理时间」
 * (上限 {@value #MAX_REASONING} 层);每层 **+1 攻击力、减少 1 点受到的伤害**;
 * **骰神赐福结束后扣除 1 层**。
 *
 * <p><b>层数真值在附件 {@code ModAttachments#SHERRY_REASONING_LAYERS}</b>(1.20.1 经
 * {@code AstralData#onPlayerClone} 的死亡保留白名单,⇒ **死亡不清**「推理时间」,与「弱点识破」
 * 那种"层数放效果里"的写法**不同**);效果实例 {@link SherryReasoningEffect} 只是**显示载体**
 * (图标 = 立牌同图 + 层数),由每 tick 镜像维护。
 *
 * <h3>被动「挚友守护」</h3>
 * 同队伍内若有玩家装备**人偶师立牌**,则使**该玩家**受到的伤害 −1。
 * ⚠️ 人偶师立牌(`astral_dice:hanna_sign`)尚未落地 ⇒ 本判定按**物品注册 id 字符串**匹配
 * (不引用 {@code ModItems.HANNA_SIGN},以免编译期依赖未创建的物品);hanna 落地后本被动**自动生效**。
 *
 * <p><b>平台差异(与 1.21.1 逐字等价)</b>:
 * <ul>
 *   <li>{@code @Mod.EventBusSubscriber}(非 {@code @EventBusSubscriber});</li>
 *   <li>「伤害前」钩子按 AGENTS「伤害事件映射」口径,1.21.1 {@code LivingDamageEvent.Pre}
 *       ↔ 本线 {@code LivingDamageEvent}(护甲/附魔减免**之后**)⇒ 事件体同为 {@code getEntity()}
 *       + {@code getSource()} + {@code getDirectEntity()};</li>
 *   <li>Curios 经 {@link CuriosCompat#getCuriosInventory} 统一为 {@code Optional};</li>
 *   <li>效果/物品注册项为 {@code RegistryObject},故 {@code ModEffects.SHERRY_REASONING.get()}
 *       (本类内引用见 {@link SherryReasoningEffect})。</li>
 * </ul>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class SherrySignItem extends BaseSignItem {

    /** 立牌注册 id(锁定态/调试用) */
    public static final String SIGN_ID = AstralDiceMod.MODID + ":sherry_sign";

    /** 「推理时间」上限 */
    public static final int MAX_REASONING = 5;
    /** 主动冷却基础秒数(与枪匠立牌一致:120 秒) */
    public static final int ACTIVE_COOLDOWN_SECONDS = 120;
    /** 主动投掷范围(格) */
    public static final double THROW_RADIUS = 12.0D;
    /** 落点距玩家(格)——「玩家面前 2 格内」 */
    public static final double THROW_FACE_DISTANCE = 2.0D;
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
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.SHERRY_SIGN.get())).isPresent();
    }

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
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
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        int thrown = castThrow(player);
        if (thrown <= 0) {
            sendSignActionBar(player, "msg.astral_dice.sherry_no_target");
            return InteractionResultHolder.fail(stack);
        }
        sendSignActionBar(player, "msg.astral_dice.sherry_throw", thrown);
        return InteractionResultHolder.success(stack);
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
     * <p>落点 = 玩家水平视线方向前 {@value #THROW_FACE_DISTANCE} 格;多个目标按**环形均匀分布**散开
     * (半径同上,避免全部叠在同一格)。
     *
     * @return 被投掷的目标数(0 = 无可投掷目标 ⇒ 调用方按「零消耗」处理)
     */
    public static int castThrow(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(THROW_RADIUS), e -> e != player && e.isAlive())) {
            if (HostileTargets.isHostile(player, candidate)) {
                targets.add(candidate);
            }
        }
        if (targets.isEmpty()) return 0;

        boolean maxed = getLayers(player) >= MAX_REASONING;
        int bonus = maxed ? THROW_BONUS_DAMAGE : 0;

        // 水平视线方向(去掉俯仰,避免落点跑到脚下/头顶)
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize();
        // 与视线垂直的水平向量,用于多点散布
        Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
        Vec3 base = player.position().add(flat.scale(THROW_FACE_DISTANCE));

        int n = targets.size();
        for (int i = 0; i < n; i++) {
            // 单目标 ⇒ 正前方;多目标 ⇒ 沿垂直方向按 -0.9/0/0.9 格均匀铺开(仍在「面前 2 格内」的语义范围)
            double offset = n == 1 ? 0.0D
                    : -0.9D + 1.8D * ((double) i / (double) (n - 1));
            Vec3 dest = base.add(side.scale(offset));
            SherryThrowManager.schedule(targets.get(i), dest, player, bonus);
        }
        return n;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「侦探出击」/「挚友守护」
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 攻击命中 ≥{@value #HIGH_HEALTH_THRESHOLD} 血敌对目标 ⇒ +1 层。
     * 由 {@link #onAttackHostile} 在本模组的伤害事件里分发(只认玩家近战/直接攻击)。
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
        var curios = CuriosCompat.getCuriosInventory(player);
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
     * 攻击方侧伤害钩子:玩家**直接攻击**(近战)命中敌对目标且其最大生命 ≥ 门槛 ⇒ +1 层。
     *
     * <p>只认 {@code getDirectEntity()} 是玩家本人的近战 —— 弹射物、法术、AOE 与环境伤害一律不计
     * (与「每攻击一个」的语义一致;本模组既有的「玩家近战」判据同款)。
     *
     * <p>挂点 = {@code LivingDamageEvent}(护甲/附魔减免之后),对应 1.21.1 的
     * {@code LivingDamageEvent.Pre};残余平台差异(1.20.1 在吸收结算之后派发)对本判定无影响
     * —— 这里只读实体与伤害源,不看伤害数值。
     */
    @SubscribeEvent
    public static void onAttackHostile(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        if (victim.getMaxHealth() < HIGH_HEALTH_THRESHOLD) return;
        if (!HostileTargets.isHostile(victim)) return;
        if (!(event.getSource().getDirectEntity() instanceof Player attacker)) return;
        if (attacker == victim) return;
        onAttackedHighHealthHostile(attacker);
    }
}
