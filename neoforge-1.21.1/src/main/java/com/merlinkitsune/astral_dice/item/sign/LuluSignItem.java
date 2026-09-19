package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.combat.HostileTargets;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.astral_dice.item.chip.FriendshipBadgeChipItem;
import com.merlinkitsune.astral_dice.network.ActionBarPayload;
import com.merlinkitsune.astral_dice.target.SelfTargetable;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.starenginelib.target.TargetSelectionAction;
import com.merlinkitsune.starenginelib.target.TargetSelectionRegistry;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 史莱姆立牌。
 * 治愈点数已解耦至 {@link HealingManager}(玩家级共享资源),本类仅负责:
 * <ul>
 *   <li><b>被动「细胞分裂」</b>:受到伤害 +1 点治愈(受击钩子 {@link #onHurt},本类不持有治愈数据),
 *       并使主动技能冷却 -10 秒;</li>
 *   <li><b>主动「治愈粘液」</b>:走<b>目标选择器</b>({@link TargetType#PLAYER} + {@link SelfTargetable#allowSelf()}
 *       ⇒ 可选任意玩家,或右键对自身使用),确认后把效果施加到**被指向的目标**(自身目标时即原来「对自己」的行为):
 *       ① 主体 +3 点治愈 + 瞬间治疗;② 以目标为中心 {@link GameplayConstants#LULU_ACTIVE_RANGE} 格内的
 *       玩家/宠物/可骑乘生物获得瞬间治疗;③ 同范围内敌对生物获得缓慢 1:00;</li>
 *   <li>卸载立牌不做任何扣除(治愈点数保留,避免卸下/重载丢失点数)。</li>
 * </ul>
 *
 * <p><b>2026-09-19 用户裁决</b>:主动改为目标选择器技能,并把原本「自身 +3 治愈 + 瞬间治疗」改为
 * 「向自身或选择的玩家目标施加」,另两项范围能力(友方瞬间治疗 / 敌对缓慢)改为以**被指向的目标**为中心
 * (选自身时即以自己为中心,与旧行为逐字一致)。范围判定的两条口径**有意保持不变**:
 * 宠物归属仍按**施放者**判定({@link #isHealTarget}),敌对判定仍以**施放者**为参照
 * ({@code HostileTargets.isHostile(player, entity)}) —— 本次只把范围中心从施放者换成目标。
 *
 * <p>主动为「门控」技能:按下主动键只开启选择会话,冷却与电流核心充能由本类注册的
 * {@link TargetSelectionAction#apply} 在确认时写入({@code BaseSignItem} 的门控分支会 return,
 * 基础流程第 6 步不会替我们写);取消/超时 ⇒ 该次主动等同未使用、不进冷却、不发牌。
 */
public class LuluSignItem extends BaseSignItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuluSignItem.class);

    /** 主动技能的动作 id(注册表键;也是 actionbar 技能名 {@code msg.astral_dice.target_select.skill.<id>} 的来源) */
    public static final String ACTION_ID = "lulu_healing_slime";

    /** 缓慢时长(60 秒;与旧行为同值,不引入新常量) */
    private static final int SLOWDOWN_TICKS = 1200;

    /**
     * 主动「治愈粘液」:选任意玩家(或右键对自身)⇒ 主体治愈施加到该目标,两项范围能力以该目标为中心。
     *
     * <p>用具名类而不是匿名类:匿名类只能实现被 new 的那一个接口,而本动作要同时实现
     * {@link TargetSelectionAction} 与 {@link SelfTargetable}(自选目标)。
     */
    private static final class HealingSlimeAction implements TargetSelectionAction, SelfTargetable {
        @Override
        public String id() {
            return ACTION_ID;
        }

        @Override
        public TargetType targetType() {
            return TargetType.PLAYER;
        }

        @Override
        public boolean allowSelf() {
            return true;
        }

        @Override
        public void apply(ServerPlayer player, LivingEntity target) {
            // ── 1. 主体效果:目标获得 3 点治愈 + 瞬间治疗 ───────────────────────────────
            // 治愈点数是玩家级资源 ⇒ 只有玩家目标能加点;瞬间治疗对任意生物都有效。
            if (target instanceof Player receiver) {
                HealingManager.add(receiver, 3);
                // 友情徽章:治疗者对**友方玩家**施加治疗时双方各 +2 治愈(自疗不触发,钩子内部自判)
                FriendshipBadgeChipItem.onHealApplied(player, receiver);
            }
            target.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, 0, false, true));

            // ── 2/3. 两项范围能力:以**被指向的目标**为中心(旧实现以施放者为中心)──────────
            // 目标自身已吃到主体效果,故与旧实现排除施放者同构,这里排除目标本身。
            AABB aabb = target.getBoundingBox().inflate(GameplayConstants.LULU_ACTIVE_RANGE);
            List<LivingEntity> nearby = target.level().getEntitiesOfClass(LivingEntity.class, aabb,
                    e -> e != target);
            for (LivingEntity entity : nearby) {
                if (HostileTargets.isHostile(player, entity)) {
                    // 敌对生物:缓慢 1:00
                    EffectTimerGuard.apply(entity,
                            new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWDOWN_TICKS, 0, false, true));
                } else if (isHealTarget(entity, player)) {
                    // 玩家/宠物/可骑乘生物:瞬间治疗 1
                    entity.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, 0, false, true));
                    // 友情徽章:对友方玩家施加治疗时,双方各获得 2 点治愈
                    if (entity instanceof Player healedPlayer) {
                        FriendshipBadgeChipItem.onHealApplied(player, healedPlayer);
                    }
                }
            }

            // ── 4. 主动成功施加:开始玩家级冷却 ──────────────────────────────────────────
            // 统一经 signCooldownTicks(含诡异骰子 -50% 与充能递减);门控路径不会走
            // BaseSignItem 第 6 步,故此处必须自己写。**同时**写 sign_active_max_cooldown:
            // 非门控路径(:145-147)原本就会写它,而电流核心的消耗档位分母读的正是它
            // ⇒ 只写冷却不写基准会让档位算错(迁移到门控时最容易漏的一步)。
            long now = player.level().getGameTime();
            int cooldown = WeirdDiceHandler.signCooldownTicks(player);
            ModAttachments.setSignActiveCooldownEnd(player, now + cooldown);
            ModAttachments.setSignActiveMaxCooldown(player, cooldown);
            // 电流核心筹码:主动技能实际生效时充能 +1
            CurrentCoreChipItem.onActiveSkillUsed(player);

            // 主动成功施加的 actionbar 反馈(带目标名;对自身时名字即自己)
            PacketDistributor.sendToPlayer(player, new ActionBarPayload(
                    Component.translatable("msg.astral_dice.lulu_healing_slime_applied", target.getDisplayName())
                            .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
            LOGGER.debug("[Astral Dice][TargetSelection] {} applied to {}({}) by {} self={}",
                    ACTION_ID, target.getId(), target.getName().getString(), player.getName().getString(),
                    target == player);
        }
    }

    static {
        TargetSelectionRegistry.register(new HealingSlimeAction());
    }

    public LuluSignItem(Properties properties) {
        super(properties);
    }

    // 目标选择器前置门控:本立牌主动为选择器类 —— 按下主动键只开启目标选择会话并立即返回,
    // 会话时长取自 GameplayConstants.SKILL_WAIT_SECONDS(秒),此处不写死数字;
    // 确认合法目标后才由 resumeGatedActiveSkill 走「风扇筹码发牌 + 立牌主动响应事件」。
    @Override
    protected String selectorActionId() {
        return ACTION_ID;
    }

    // 被动:受到伤害时,获得 1 点"治愈"(上限为玩家最大生命值的一半,即 ♥ 数),并使主动技能冷却 -10 秒。
    // 任何来源的伤害均触发(近战/远程/环境等),与骰神赐福的玩家攻击链路相互独立。
    // 受击 +1 有 1 秒冷却(20 tick),防止被围攻时治愈点数暴涨。
    @Override
    protected void onHurt(Player player, float amount) {
        long nowTick = player.level().getGameTime();
        if (nowTick - com.merlinkitsune.astral_dice.component.ModAttachments.getLuluLastHurtTick(player) < 20) {
            return;
        }
        com.merlinkitsune.astral_dice.component.ModAttachments.setLuluLastHurtTick(player, nowTick);
        // 主动技能冷却 -10 秒(200 tick,绝对量,与最大冷却无关);
        // 夹底取 now:cdEnd == 0 是"无冷却"哨兵值,不得写出 0。
        // 第二批「三态化」:主动仍在锁定(生效中)态时冷却尚未起算 ⇒ 把这 200 tick 绝对量累加进锁定减免池,
        // 由锁定结束起冷却时一次性抵扣(不在这里改任何冷却数值)
        long cdEnd = com.merlinkitsune.astral_dice.component.ModAttachments.getSignActiveCooldownEnd(player);
        if (BaseSignItem.isSignActiveLocked(player)) {
            com.merlinkitsune.astral_dice.component.ModAttachments.addSignActiveReductionPool(player, 200L);
        } else if (cdEnd > nowTick) {
            com.merlinkitsune.astral_dice.component.ModAttachments.setSignActiveCooldownEnd(player,
                    Math.max(nowTick, cdEnd - 200));
        }
        // 治愈点 +1(上限为玩家最大生命值的一半,即 ♥ 数)
        HealingManager.add(player, 1);
    }

    // 判定可治疗的友好目标:玩家、玩家驯服的宠物、可骑乘生物(马/驴/骡等、猪、炽足兽、骆驼)
    // ⚠️ 归属与参照**仍是施放者**(2026-09-19 迁移到目标选择器时有意不改):本次只把范围中心从施放者
    //    换成被指向的目标,治疗判定规则逐字保持,避免「换个目标就换一套规则」的隐性扩张。
    private static boolean isHealTarget(LivingEntity entity, Player player) {
        if (entity instanceof Player) return true;
        if (entity instanceof TamableAnimal tame && tame.isOwnedBy(player)) return true;
        return entity instanceof AbstractHorse
                || entity instanceof Pig
                || entity instanceof Strider
                || entity instanceof Camel;
    }
}
