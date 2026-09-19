package com.merlinkitsune.astral_dice.component;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 玩家附件注册中心(1.20.1 Forge 移植版):常量名与静态包装器签名与 1.21 分支保持一致,
 * 内部由 {@link AttachedDataKey}(AstralData Capability / ForgeData)承载,
 * synced 键经 {@link com.merlinkitsune.astral_dice.network.ModNetwork} 同步到客户端。
 */
public class ModAttachments {

    /** 需要客户端同步的键(登录/重生/切维度时发送完整快照)。 */
    private static final List<AttachedDataKey<?>> SYNCED_KEYS = new ArrayList<>();

    private static <T> AttachedDataKey<T> register(AttachedDataKey<T> key) {
        return key;
    }

    public static final AttachedDataKey<Integer> PLAYER_STARLIGHT =
            register(AttachedDataKey.builder("player_starlight", Codec.INT, () -> 0).sync().build());

    // 八面骰筹码:骰神赐福触发时累计的骰点总和(每满 8 点 +1 星光,达到 32 星光后归 0)
    public static final AttachedDataKey<Integer> EIGHT_SIDED_ROLL_ACCUM =
            register(AttachedDataKey.builder("eight_sided_roll_accum", Codec.INT, () -> 0).build());

    // 伤害效果牌:当前生效的远程/魔法攻击追加伤害数值(由伤害效果牌使用后设置)
    public static final AttachedDataKey<Integer> DAMAGE_EFFECT_BONUS =
            register(AttachedDataKey.builder("damage_effect_bonus", Codec.INT, () -> 0).sync().build());

    // 效果牌:当前周期内已连续出牌数
    public static final AttachedDataKey<Integer> EFFECT_CARD_PLAY_COUNT =
            register(AttachedDataKey.builder("effect_card_play_count", Codec.INT, () -> 0).sync().build());

    // 出牌轮一次性追加的出牌数(0/1):由立牌主动技能(忍者「忍术连击」)授予,**仅当前出牌轮有效**——
    // 不是可累积、可跨轮保留的"出牌银行";周期结束时由 EffectCardPeriod 统一清除,
    // 立牌装卸不影响(授予即已消耗)。授予入口见 EffectCardPeriod#grantBonusPlay。
    public static final AttachedDataKey<Integer> EFFECT_CARD_BONUS_PLAYS =
            register(AttachedDataKey.builder("effect_card_bonus_plays", Codec.INT, () -> 0).sync().build());

    public static int getEffectCardBonusPlays(net.minecraft.world.entity.player.Player player) {
        return EFFECT_CARD_BONUS_PLAYS.get(player);
    }

    public static void setEffectCardBonusPlays(net.minecraft.world.entity.player.Player player, int value) {
        EFFECT_CARD_BONUS_PLAYS.set(player, Math.max(0, value));
    }

    // 活体书页(effect_card_living_page):本周期活体书页累计的出牌数加成(0,1,2,…)。
    // 每次使用活体书页 +1(可累计;不是"效果存在即 +1"的开关式),周期归零时由 EffectCardPeriod 清除。
    // 注意:与"调查员已用页数"(rin_pages,永久累计;作为加成生效时静默上限 120)无关,不可复用后者做本周期计数。
    public static final AttachedDataKey<Integer> LIVING_PAGE_CYCLE_BONUS =
            register(AttachedDataKey.builder("living_page_cycle_bonus", Codec.INT, () -> 0).sync().build());

    public static int getLivingPageCycleBonus(net.minecraft.world.entity.player.Player player) {
        return LIVING_PAGE_CYCLE_BONUS.get(player);
    }

    // 计数器只增不减(归零由周期清理负责),此处仅钳制非负
    public static void setLivingPageCycleBonus(net.minecraft.world.entity.player.Player player, int value) {
        LIVING_PAGE_CYCLE_BONUS.set(player, Math.max(0, value));
    }

    // 效果牌公共冷却结束时刻(-1 表示待定冷却=伤害效果牌效果等待中;0 表示无)
    public static final AttachedDataKey<Long> EFFECT_CARD_COOLDOWN_END =
            register(AttachedDataKey.builder("effect_card_cooldown_end", Codec.LONG, () -> 0L).sync().build());

    // 史莱姆立牌:上次受击获得治愈的游戏时刻(限制受击 +1 的频率,防止围攻时点数暴涨)
    public static final AttachedDataKey<Long> LULU_LAST_HURT_TICK =
            register(AttachedDataKey.builder("lulu_last_hurt_tick", Codec.LONG, () -> 0L).build());

    public static long getLuluLastHurtTick(net.minecraft.world.entity.player.Player player) {
        return LULU_LAST_HURT_TICK.get(player);
    }

    public static void setLuluLastHurtTick(net.minecraft.world.entity.player.Player player, long value) {
        LULU_LAST_HURT_TICK.set(player, value);
    }

    // 治愈点数(玩家级共享资源的单一数值池):由医疗箱(赐福触发时加点)/史莱姆立牌被动/主动/
    // 缓冲盾牌等获取,由 HealingManager 统一管理。治愈体系已无独立计时器:
    // 触发骰神赐福时按当前治愈点×2 回血,赐福结束时治愈点减半(向下取整)。
    public static final AttachedDataKey<Integer> HEALING_POINTS =
            register(AttachedDataKey.builder("healing_points", Codec.INT, () -> 0).sync().build());

    public static int getHealingPoints(net.minecraft.world.entity.player.Player player) {
        return HEALING_POINTS.get(player);
    }

    public static void setHealingPoints(net.minecraft.world.entity.player.Player player, int value) {
        HEALING_POINTS.set(player, Math.max(0, value));
    }

    // 治愈:上一检测周期玩家是否处于"骰神赐福"(服务端边沿检测用,判断赐福结束时刻以执行治愈减半;
    // 仅服务端使用,无需同步)
    public static final AttachedDataKey<Boolean> HEALING_PREV_BLESSING =
            register(AttachedDataKey.builder("healing_prev_blessing", Codec.BOOL, () -> false).build());

    public static boolean isHealingPrevBlessing(net.minecraft.world.entity.player.Player player) {
        return HEALING_PREV_BLESSING.get(player);
    }

    public static void setHealingPrevBlessing(net.minecraft.world.entity.player.Player player, boolean value) {
        HEALING_PREV_BLESSING.set(player, value);
    }

    // 治愈:独立 30 秒计时器结束 tick(服务端使用;0 表示无计时器)
    public static final AttachedDataKey<Long> HEALING_TIMER_END =
            register(AttachedDataKey.builder("healing_timer_end", Codec.LONG, () -> 0L).build());

    public static long getHealingTimerEnd(net.minecraft.world.entity.player.Player player) {
        return HEALING_TIMER_END.get(player);
    }

    public static void setHealingTimerEnd(net.minecraft.world.entity.player.Player player, long value) {
        HEALING_TIMER_END.set(player, Math.max(0, value));
    }


    // 魔法秘典筹码:效果牌使用计数(每使用 3 张复制最后一张)
    public static final AttachedDataKey<Integer> MAGIC_TOME_USE_COUNT =
            register(AttachedDataKey.builder("magic_tome_use_count", Codec.INT, () -> 0).sync().build());

    // 魔法秘典筹码:最后一张使用的效果牌类型(king_power/berserk/unwavering)
    public static final AttachedDataKey<String> MAGIC_TOME_LAST_CARD =
            register(AttachedDataKey.builder("magic_tome_last_card", Codec.STRING, () -> "").build());

    // 忍者立牌(komachi):效果牌使用计数(独立于魔法秘典与周期计数,每使用 3 张复制最后一张)
    public static final AttachedDataKey<Integer> KOMACHI_USE_COUNT =
            register(AttachedDataKey.builder("komachi_use_count", Codec.INT, () -> 0).sync().build());

    // 忍者立牌(komachi):最后一张使用的效果牌类型
    public static final AttachedDataKey<String> KOMACHI_LAST_CARD =
            register(AttachedDataKey.builder("komachi_last_card", Codec.STRING, () -> "").build());

    // 忍者立牌(komachi):效果牌伤害增益(每使用 3 张效果牌 +1,累计无上限;作为加成生效时静默上限 120,卸下立牌重置;死亡重生保留)
    // 「死亡重生保留」由 AstralData.onPlayerClone 的死亡分支复制本键实现(对应 1.21.1 的 .copyOnDeath())
    public static final AttachedDataKey<Integer> KOMACHI_DAMAGE_BONUS =
            register(AttachedDataKey.builder("komachi_damage_bonus", Codec.INT, () -> 0).sync().build());

    // 小猪存钱罐筹码:效果牌使用计数(每使用 2 张获得 3 星币;卸下筹码重置)
    public static final AttachedDataKey<Integer> PIGGY_BANK_USE_COUNT =
            register(AttachedDataKey.builder("piggy_bank_use_count", Codec.INT, () -> 0).build());

    public static int getPiggyBankUseCount(net.minecraft.world.entity.player.Player player) {
        return PIGGY_BANK_USE_COUNT.get(player);
    }

    public static void setPiggyBankUseCount(net.minecraft.world.entity.player.Player player, int value) {
        PIGGY_BANK_USE_COUNT.set(player, Math.max(0, value));
    }

    // 手电筒-强光筹码:已发放过星光的敌对目标 UUID(逗号分隔;同一目标仅 +1 层星光)
    public static final AttachedDataKey<String> FLASHLIGHT_GRANTED_TARGETS =
            register(AttachedDataKey.builder("flashlight_granted_targets", Codec.STRING, () -> "").build());

    public static String getFlashlightGrantedTargets(net.minecraft.world.entity.player.Player player) {
        return FLASHLIGHT_GRANTED_TARGETS.get(player);
    }

    public static void setFlashlightGrantedTargets(net.minecraft.world.entity.player.Player player, String value) {
        FLASHLIGHT_GRANTED_TARGETS.set(player, value == null ? "" : value);
    }

    public static int getMagicTomeUseCount(net.minecraft.world.entity.player.Player player) {
        return MAGIC_TOME_USE_COUNT.get(player);
    }

    public static void setMagicTomeUseCount(net.minecraft.world.entity.player.Player player, int value) {
        MAGIC_TOME_USE_COUNT.set(player, Math.max(0, value));
    }

    public static String getMagicTomeLastCard(net.minecraft.world.entity.player.Player player) {
        return MAGIC_TOME_LAST_CARD.get(player);
    }

    public static void setMagicTomeLastCard(net.minecraft.world.entity.player.Player player, String value) {
        MAGIC_TOME_LAST_CARD.set(player, value);
    }

    public static int getKomachiUseCount(net.minecraft.world.entity.player.Player player) {
        return KOMACHI_USE_COUNT.get(player);
    }
    public static void setKomachiUseCount(net.minecraft.world.entity.player.Player player, int value) {
        KOMACHI_USE_COUNT.set(player, Math.max(0, value));
    }

    public static String getKomachiLastCard(net.minecraft.world.entity.player.Player player) {
        return KOMACHI_LAST_CARD.get(player);
    }

    public static void setKomachiLastCard(net.minecraft.world.entity.player.Player player, String value) {
        KOMACHI_LAST_CARD.set(player, value);
    }

    public static int getKomachiDamageBonus(net.minecraft.world.entity.player.Player player) {
        return KOMACHI_DAMAGE_BONUS.get(player);
    }

    public static void setKomachiDamageBonus(net.minecraft.world.entity.player.Player player, int value) {
        KOMACHI_DAMAGE_BONUS.set(player, Math.max(0, value));
    }

    // 命运的指引激活截止时刻(gameTime 毫秒? 否:tick)。使用后写入 now+6000;功能检查用 now < until
    public static final AttachedDataKey<Long> FATE_ACTIVE_UNTIL =
            register(AttachedDataKey.builder("fate_active_until", Codec.LONG, () -> 0L).build());

    public static long getFateActiveUntil(net.minecraft.world.entity.player.Player player) {
        return FATE_ACTIVE_UNTIL.get(player);
    }

    public static void setFateActiveUntil(net.minecraft.world.entity.player.Player player, long value) {
        FATE_ACTIVE_UNTIL.set(player, value);
    }

    // 骰战七咒倍率捕获:神秘遗物 模组在 LivingIncomingDamageEvent 应用第一诅咒倍率(含其配置 painMultiplier
    // 与修正物品,如大地誓约)后,由本模组 LOWEST 处理器捕获实际倍率供骰战最终伤害使用;
    // 仅内存态(不序列化),骰战结算使用后清零;非骰战攻击不使用。
    public static final AttachedDataKey<Float> DICE_CURSE_RATIO =
            register(AttachedDataKey.builder("dice_curse_ratio", Codec.FLOAT, () -> 1.0f).inMemory().build());

    public static float getDiceCurseRatio(net.minecraft.world.entity.player.Player player) {
        return DICE_CURSE_RATIO.get(player);
    }

    public static void setDiceCurseRatio(net.minecraft.world.entity.player.Player player, float value) {
        DICE_CURSE_RATIO.set(player, value);
    }

    // 七咒倍率捕获辅助(1.20.1):LivingAttackEvent(HIGHEST)记录的原始伤害,供
    // LivingHurtEvent(LOWEST)计算实际倍率;仅内存态,每次受击覆盖。
    public static final AttachedDataKey<Float> CURSE_ORIGINAL_AMOUNT =
            register(AttachedDataKey.builder("curse_original_amount", Codec.FLOAT, () -> 0.0f).inMemory().build());

    public static float getCurseOriginalAmount(net.minecraft.world.entity.player.Player player) {
        return CURSE_ORIGINAL_AMOUNT.get(player);
    }

    public static void setCurseOriginalAmount(net.minecraft.world.entity.player.Player player, float value) {
        CURSE_ORIGINAL_AMOUNT.set(player, value);
    }

    // 调查员立牌(rin):已使用的活体书页数量(活体书页伤害永久+1 的来源,移除立牌后重置;死亡重生保留)
    // 「死亡重生保留」由 AstralData.onPlayerClone 的死亡分支复制本键实现(对应 1.21.1 的 .copyOnDeath())
    public static final AttachedDataKey<Integer> RIN_PAGES =
            register(AttachedDataKey.builder("rin_pages", Codec.INT, () -> 0).sync().build());

    // 调查员立牌(rin):最近一次获得活体书页的事件签名(触发者 UUID + "|" + 事件 ID)。
    // 用于同一事件在极短窗口(2 tick)内被重复分发时去重(如多立牌槽重复调用 onKill),
    // 保证"同一玩家发出的同一 ID 事件"只给一次牌。
    public static final AttachedDataKey<String> RIN_GIFT_SIGNATURE =
            register(AttachedDataKey.builder("rin_gift_signature", Codec.STRING, () -> "").build());

    public static String getRinGiftSignature(net.minecraft.world.entity.player.Player player) {
        return RIN_GIFT_SIGNATURE.get(player);
    }

    public static void setRinGiftSignature(net.minecraft.world.entity.player.Player player, String value) {
        RIN_GIFT_SIGNATURE.set(player, value);
    }

    // 调查员立牌(rin):记录 RIN_GIFT_SIGNATURE 对应的游戏时刻(去重窗口判定用)
    public static final AttachedDataKey<Long> RIN_GIFT_TICK =
            register(AttachedDataKey.builder("rin_gift_tick", Codec.LONG, () -> 0L).build());

    public static long getRinGiftTick(net.minecraft.world.entity.player.Player player) {
        return RIN_GIFT_TICK.get(player);
    }

    public static void setRinGiftTick(net.minecraft.world.entity.player.Player player, long value) {
        RIN_GIFT_TICK.set(player, value);
    }

    // 虚弱印记来源:施加该印记的玩家 UUID(仅该玩家获得击杀后奖励,印记结束/目标死亡后清除)
    public static final AttachedDataKey<Optional<UUID>> WEAK_MARK_SOURCE =
            register(AttachedDataKey.builder("weak_mark_source",
                    UUIDUtil.CODEC.optionalFieldOf("id").codec(), Optional::empty).build());

    public static Optional<UUID> getWeakMarkSource(net.minecraft.world.entity.LivingEntity entity) {
        return WEAK_MARK_SOURCE.get(entity);
    }

    public static void setWeakMarkSource(net.minecraft.world.entity.LivingEntity entity, Optional<UUID> value) {
        WEAK_MARK_SOURCE.set(entity, value);
    }

    // 隐匿调查来源:施加"隐匿调查"的玩家 UUID(击杀该目标触发调查阶段事件时,奖励归属施加者)
    public static final AttachedDataKey<Optional<UUID>> UNDERCOVER_SOURCE =
            register(AttachedDataKey.builder("undercover_source",
                    UUIDUtil.CODEC.optionalFieldOf("id").codec(), Optional::empty).build());

    // 调查阶段进度:1=调查阶段I 2=II 3=III 4=真相揭露(进度归属施加"隐匿调查"的玩家)
    public static final AttachedDataKey<Integer> INVESTIGATION_STAGE =
            register(AttachedDataKey.builder("investigation_stage", Codec.INT, () -> 1).build());

    // 破绽(枪匠立牌 Moses)期间,目标是否已被攻击方获得过弱点识破(每段破绽一次)
    public static final AttachedDataKey<Boolean> MOSES_BROKEN_ATTACK_REWARDED =
            register(AttachedDataKey.builder("moses_broken_attack_rewarded", Codec.BOOL, () -> false).build());

    // 枪匠立牌 Moses:目标是否已因闪避/反击获得过弱点识破(每个目标一次,不依赖破绽)
    public static final AttachedDataKey<Boolean> MOSES_DODGE_COUNTER_REWARDED =
            register(AttachedDataKey.builder("moses_dodge_counter_rewarded", Codec.BOOL, () -> false).build());

    public static boolean isMosesBrokenAttackRewarded(net.minecraft.world.entity.LivingEntity entity) {
        return MOSES_BROKEN_ATTACK_REWARDED.get(entity);
    }

    public static void setMosesBrokenAttackRewarded(net.minecraft.world.entity.LivingEntity entity, boolean value) {
        MOSES_BROKEN_ATTACK_REWARDED.set(entity, value);
    }

    public static boolean isMosesDodgeCounterRewarded(net.minecraft.world.entity.LivingEntity entity) {
        return MOSES_DODGE_COUNTER_REWARDED.get(entity);
    }

    public static void setMosesDodgeCounterRewarded(net.minecraft.world.entity.LivingEntity entity, boolean value) {
        MOSES_DODGE_COUNTER_REWARDED.set(entity, value);
    }

    public static Optional<UUID> getUndercoverSource(net.minecraft.world.entity.LivingEntity entity) {
        return UNDERCOVER_SOURCE.get(entity);
    }

    public static void setUndercoverSource(net.minecraft.world.entity.LivingEntity entity, Optional<UUID> value) {
        UNDERCOVER_SOURCE.set(entity, value);
    }

    public static int getInvestigationStage(net.minecraft.world.entity.player.Player player) {
        return INVESTIGATION_STAGE.get(player);
    }

    public static void setInvestigationStage(net.minecraft.world.entity.player.Player player, int value) {
        INVESTIGATION_STAGE.set(player, Math.max(1, value));
    }

    public static int getStarlight(net.minecraft.world.entity.player.Player player) {
        return PLAYER_STARLIGHT.get(player);
    }

    public static void setStarlight(net.minecraft.world.entity.player.Player player, int value) {
        PLAYER_STARLIGHT.set(player, value);
    }

    public static int getEightSidedAccum(net.minecraft.world.entity.player.Player player) {
        return EIGHT_SIDED_ROLL_ACCUM.get(player);
    }

    public static void setEightSidedAccum(net.minecraft.world.entity.player.Player player, int value) {
        EIGHT_SIDED_ROLL_ACCUM.set(player, value);
    }

    public static int getDamageEffectBonus(net.minecraft.world.entity.player.Player player) {
        return DAMAGE_EFFECT_BONUS.get(player);
    }

    public static void setDamageEffectBonus(net.minecraft.world.entity.player.Player player, int value) {
        DAMAGE_EFFECT_BONUS.set(player, value);
    }

    // 立牌主动技能冷却结束时刻(玩家级,不受立牌装卸影响;0 表示无冷却)
    public static final AttachedDataKey<Long> SIGN_ACTIVE_COOLDOWN_END =
            register(AttachedDataKey.builder("sign_active_cooldown_end", Codec.LONG, () -> 0L).sync().build());

    public static long getSignActiveCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return SIGN_ACTIVE_COOLDOWN_END.get(player);
    }

    public static void setSignActiveCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        SIGN_ACTIVE_COOLDOWN_END.set(player, value);
    }

    // 立牌主动技能"本次冷却实际使用的最大冷却 tick"(路线 A:所有减免方一律读它作基准,不再各自重算;
    // 0 表示缺失/无冷却,减免方回退旧行为;仅服务端使用,故不 .sync()、不加入 SYNCED_KEYS)。
    // 写入时机(第二批「立牌主动技能三态化」):① 直接起冷却时与 SIGN_ACTIVE_COOLDOWN_END 成对写入;
    // ② 进入"锁定(生效中)"态时先写入基准(锁定期间减免方照旧读它并累加进 SIGN_ACTIVE_REDUCTION_POOL),
    //    冷却起点火时再改写为扣池后的实际冷却 effective = max(0, 基准 − 池)。
    public static final AttachedDataKey<Long> SIGN_ACTIVE_MAX_COOLDOWN =
            register(AttachedDataKey.builder("sign_active_max_cooldown", Codec.LONG, () -> 0L).build());

    public static long getSignActiveMaxCooldown(net.minecraft.world.entity.player.Player player) {
        return SIGN_ACTIVE_MAX_COOLDOWN.get(player);
    }

    public static void setSignActiveMaxCooldown(net.minecraft.world.entity.player.Player player, long value) {
        SIGN_ACTIVE_MAX_COOLDOWN.set(player, value);
    }

    // ===== 立牌主动技能"三态化"(可用 / 锁定-生效中 / 冷却)的玩家级状态 =====
    // 2026-09-25 用户裁决(第二批):主动技能施加的"带时长效果/自身计时器"跑完之前处于**锁定(生效中)**态,
    // 期间按键无效;锁定结束**必起冷却**(无空档)。全部键仅服务端使用(判定只在服务端 performSkill /
    // 各减免方 / 玩家级 tick),故一律不 .sync()、不加入 SYNCED_KEYS(客户端 tooltip 不显示"生效中")。

    // 锁定态标记:正在生效中的主动所属立牌的**物品注册 id**("" = 未锁定)。判定见 BaseSignItem#isSignActiveLocked
    public static final AttachedDataKey<String> SIGN_ACTIVE_LOCK_SIGN =
            register(AttachedDataKey.builder("sign_active_lock_sign", Codec.STRING, () -> "").build());

    // 锁定态的硬上界:触发时刻算定的"本技能施加的全部计时器到期刻取 max"
    // (0 = 无自身计时器,仅忍者使用——其锁定跟随出牌周期,由周期完全重置结束)
    public static final AttachedDataKey<Long> SIGN_ACTIVE_LOCK_END =
            register(AttachedDataKey.builder("sign_active_lock_end", Codec.LONG, () -> 0L).build());

    // 锁定期间累计的冷却减免池(tick):锁定结束起冷却时一次性抵扣并归零
    public static final AttachedDataKey<Long> SIGN_ACTIVE_REDUCTION_POOL =
            register(AttachedDataKey.builder("sign_active_reduction_pool", Codec.LONG, () -> 0L).build());

    // 忍者立牌专用:宽限到期刻(触发主动时刻 + 1:00;0 = 宽限已失效/不适用)
    public static final AttachedDataKey<Long> SIGN_ACTIVE_LOCK_GRACE_END =
            register(AttachedDataKey.builder("sign_active_lock_grace_end", Codec.LONG, () -> 0L).build());

    // 忍者立牌专用:本次锁定/宽限期内是否已出过任何效果牌(true = 宽限保险失效,遵循出牌周期)
    public static final AttachedDataKey<Boolean> SIGN_ACTIVE_LOCK_PLAYED =
            register(AttachedDataKey.builder("sign_active_lock_played", Codec.BOOL, () -> false).build());

    public static String getSignActiveLockSign(net.minecraft.world.entity.player.Player player) {
        return SIGN_ACTIVE_LOCK_SIGN.get(player);
    }

    public static void setSignActiveLockSign(net.minecraft.world.entity.player.Player player, String value) {
        SIGN_ACTIVE_LOCK_SIGN.set(player, value == null ? "" : value);
    }

    public static long getSignActiveLockEnd(net.minecraft.world.entity.player.Player player) {
        return SIGN_ACTIVE_LOCK_END.get(player);
    }

    public static void setSignActiveLockEnd(net.minecraft.world.entity.player.Player player, long value) {
        SIGN_ACTIVE_LOCK_END.set(player, value);
    }

    public static long getSignActiveReductionPool(net.minecraft.world.entity.player.Player player) {
        return SIGN_ACTIVE_REDUCTION_POOL.get(player);
    }

    public static void setSignActiveReductionPool(net.minecraft.world.entity.player.Player player, long value) {
        SIGN_ACTIVE_REDUCTION_POOL.set(player, Math.max(0L, value));
    }

    public static void addSignActiveReductionPool(net.minecraft.world.entity.player.Player player, long delta) {
        setSignActiveReductionPool(player, SIGN_ACTIVE_REDUCTION_POOL.get(player) + delta);
    }

    public static long getSignActiveLockGraceEnd(net.minecraft.world.entity.player.Player player) {
        return SIGN_ACTIVE_LOCK_GRACE_END.get(player);
    }

    public static void setSignActiveLockGraceEnd(net.minecraft.world.entity.player.Player player, long value) {
        SIGN_ACTIVE_LOCK_GRACE_END.set(player, value);
    }

    public static boolean getSignActiveLockPlayed(net.minecraft.world.entity.player.Player player) {
        return SIGN_ACTIVE_LOCK_PLAYED.get(player);
    }

    public static void setSignActiveLockPlayed(net.minecraft.world.entity.player.Player player, boolean value) {
        SIGN_ACTIVE_LOCK_PLAYED.set(player, value);
    }

    // 末影骰子:不死图腾效果冷却结束时刻(玩家级,0 表示未进入冷却)
    public static final AttachedDataKey<Long> ENDER_DIE_TOTEM_COOLDOWN_END =
            register(AttachedDataKey.builder("ender_die_totem_cooldown_end", Codec.LONG, () -> 0L).sync().build());

    public static long getEnderDieTotemCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return ENDER_DIE_TOTEM_COOLDOWN_END.get(player);
    }

    public static void setEnderDieTotemCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        ENDER_DIE_TOTEM_COOLDOWN_END.set(player, value);
    }

    // 跃迁引擎:传送门/Waystone 传送获得充能的共享冷却结束时刻(玩家级,0 表示无冷却;仅服务端使用)
    public static final AttachedDataKey<Long> WARP_ENGINE_PORTAL_COOLDOWN_END =
            register(AttachedDataKey.builder("warp_engine_portal_cooldown_end", Codec.LONG, () -> 0L).build());

    public static long getWarpEnginePortalCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return WARP_ENGINE_PORTAL_COOLDOWN_END.get(player);
    }

    public static void setWarpEnginePortalCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        WARP_ENGINE_PORTAL_COOLDOWN_END.set(player, value);
    }

    // @Deprecated 已废弃:立牌主动技能"等待目标释放"机制已被目标选择器(TargetSelectionManager 会话)替代,
    // 占星师/秘密侦探不再读写本附件。定义保留(已持久化)以避免旧存档附件数据异常,禁止新代码使用。
    // 立牌主动技能"等待目标释放"状态类型:1=占星师(虚弱印记) 2=秘密侦探(隐匿调查);0=无等待
    public static final AttachedDataKey<Integer> SIGN_READY_TYPE =
            register(AttachedDataKey.builder("sign_ready_type", Codec.INT, () -> 0).sync().build());

    // @Deprecated 已废弃:见 SIGN_READY_TYPE
    // 立牌主动技能等待到期时刻(0 表示无等待)
    public static final AttachedDataKey<Long> SIGN_READY_EXPIRE =
            register(AttachedDataKey.builder("sign_ready_expire", Codec.LONG, () -> 0L).sync().build());

    /** @deprecated 已废弃,由目标选择器会话替代;禁止新代码使用 */
    @Deprecated
    public static int getSignReadyType(net.minecraft.world.entity.player.Player player) {
        return SIGN_READY_TYPE.get(player);
    }

    /** @deprecated 已废弃,由目标选择器会话替代;禁止新代码使用 */
    @Deprecated
    public static void setSignReadyType(net.minecraft.world.entity.player.Player player, int value) {
        SIGN_READY_TYPE.set(player, value);
    }

    /** @deprecated 已废弃,由目标选择器会话替代;禁止新代码使用 */
    @Deprecated
    public static long getSignReadyExpire(net.minecraft.world.entity.player.Player player) {
        return SIGN_READY_EXPIRE.get(player);
    }

    /** @deprecated 已废弃,由目标选择器会话替代;禁止新代码使用 */
    @Deprecated
    public static void setSignReadyExpire(net.minecraft.world.entity.player.Player player, long value) {
        SIGN_READY_EXPIRE.set(player, value);
    }

    // 效果牌出牌周期计时相关
    public static int getEffectCardPlayCount(net.minecraft.world.entity.player.Player player) {
        return EFFECT_CARD_PLAY_COUNT.get(player);
    }

    public static void setEffectCardPlayCount(net.minecraft.world.entity.player.Player player, int value) {
        EFFECT_CARD_PLAY_COUNT.set(player, value);
    }

    public static long getEffectCardCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return EFFECT_CARD_COOLDOWN_END.get(player);
    }

    public static void setEffectCardCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        EFFECT_CARD_COOLDOWN_END.set(player, value);
    }

    public static int getRinPages(net.minecraft.world.entity.player.Player player) {
        return RIN_PAGES.get(player);
    }

    public static void setRinPages(net.minecraft.world.entity.player.Player player, int value) {
        RIN_PAGES.set(player, value);
    }

    // === 新筹码附件(魔法箭袋/缓冲盾牌/星币锤) ===

    // 魔法箭袋筹码:是否已记录"第一张使用的效果牌"(一次触发周期内)
    public static final AttachedDataKey<Boolean> MAGIC_QUIVER_TRACKING =
            register(AttachedDataKey.builder("magic_quiver_tracking", Codec.BOOL, () -> false).build());

    // 魔法箭袋筹码:记录的第一张使用的效果牌类型(king_power/berserk/unwavering)
    public static final AttachedDataKey<String> MAGIC_QUIVER_FIRST_CARD =
            register(AttachedDataKey.builder("magic_quiver_first_card", Codec.STRING, () -> "").build());

    // 魔法箭袋筹码:触发冷却结束时刻(1 分钟;0 表示无冷却)
    public static final AttachedDataKey<Long> MAGIC_QUIVER_COOLDOWN_END =
            register(AttachedDataKey.builder("magic_quiver_cooldown_end", Codec.LONG, () -> 0L).build());

    // 缓冲盾牌筹码:触发冷却结束时刻(1 分钟;0 表示无冷却)
    public static final AttachedDataKey<Long> BUFFER_SHIELD_COOLDOWN_END =
            register(AttachedDataKey.builder("buffer_shield_cooldown_end", Codec.LONG, () -> 0L).build());

    // 星币锤筹码:本次骰神赐福的攻击力加成(进入赐福时设置,赐福结束/卸下清除)
    public static final AttachedDataKey<Integer> STAR_COIN_HAMMER_BONUS =
            register(AttachedDataKey.builder("star_coin_hammer_bonus", Codec.INT, () -> 0).build());

    // 诅咒之剑筹码:累计击杀不少于 20 血的敌对目标获得的攻击力加成(移除筹码/死亡清除)
    public static final AttachedDataKey<Integer> CURSED_SWORD_BONUS =
            register(AttachedDataKey.builder("cursed_sword_bonus", Codec.INT, () -> 0).sync().build());

    // 诅咒之剑筹码:当前骰神赐福期间是否已触发过击杀加成(每个赐福周期最多一次)
    public static final AttachedDataKey<Boolean> CURSED_SWORD_BLESSING_TRIGGERED =
            register(AttachedDataKey.builder("cursed_sword_blessing_triggered", Codec.BOOL, () -> false).build());

    // 可口糖果筹码:当前效果牌出牌轮次是否已触发过"满血时出牌数+1"(每个轮次最多一次)
    public static final AttachedDataKey<Boolean> CANDY_CHIP_PLAY_BONUS =
            register(AttachedDataKey.builder("candy_chip_play_bonus", Codec.BOOL, () -> false).sync().build());

    // 探天卫星筹码:补充轨道炮冷却结束时刻
    public static final AttachedDataKey<Long> SATELLITE_GIVE_COOLDOWN_END =
            register(AttachedDataKey.builder("satellite_give_cooldown_end", Codec.LONG, () -> 0L).build());

    // 探天卫星筹码:当前效果牌出牌轮次是否已触发过"使用轨道炮后出牌数+1"(每轮最多一次;
    // 触发时机受 SATELLITE_PLAY_BONUS_COOLDOWN_END 限制,每 1:00 至多触发一次)
    public static final AttachedDataKey<Boolean> SATELLITE_PLAY_BONUS =
            register(AttachedDataKey.builder("satellite_play_bonus", Codec.BOOL, () -> false).sync().build());

    // 探天卫星筹码:"使用轨道炮后出牌数+1"的触发冷却结束时刻(每 1:00 至多触发一次;0 表示无冷却)
    public static final AttachedDataKey<Long> SATELLITE_PLAY_BONUS_COOLDOWN_END =
            register(AttachedDataKey.builder("satellite_play_bonus_cooldown_end", Codec.LONG, () -> 0L).sync().build());

    // 骇客立牌:被动类型(0=无,1=攻击,2=防御)
    public static final AttachedDataKey<Integer> NANCY_LU_PASSIVE_TYPE =
            register(AttachedDataKey.builder("nancy_lu_passive_type", Codec.INT, () -> 0).sync().build());

    // 骇客立牌:主动"远程骇入"攻击力加成数值
    public static final AttachedDataKey<Integer> NANCY_LU_ACTIVE_BONUS =
            register(AttachedDataKey.builder("nancy_lu_active_bonus", Codec.INT, () -> 0).sync().build());

    // 骇客立牌:主动攻击力加成结束时刻
    public static final AttachedDataKey<Long> NANCY_LU_ACTIVE_BONUS_UNTIL =
            register(AttachedDataKey.builder("nancy_lu_active_bonus_until", Codec.LONG, () -> 0L).build());

    // 骇客立牌:主动"完全隐身"结束时刻
    // (同步到客户端:client/NancyLuClientEvents 据此在隐身期间取消自身渲染)
    public static final AttachedDataKey<Long> NANCY_LU_HIDDEN_UNTIL =
            register(AttachedDataKey.builder("nancy_lu_hidden_until", Codec.LONG, () -> 0L).sync().build());

    // 看板娘立牌:被动"主动技能返还"累计的战斗牌数量(每累计 25 张返还战斗牌获得一个随机筹码)
    public static final AttachedDataKey<Integer> MIMI_RETURNED_CARD_COUNT =
            register(AttachedDataKey.builder("mimi_returned_card_count", Codec.INT, () -> 0).build());

    // 骇客立牌:末影珍珠传送伤害免疫结束时刻
    public static final AttachedDataKey<Long> NANCY_LU_ENDER_PEARL_IMMUNE_UNTIL =
            register(AttachedDataKey.builder("nancy_lu_ender_pearl_immune_until", Codec.LONG, () -> 0L).build());

    public static boolean getMagicQuiverTracking(net.minecraft.world.entity.player.Player player) {
        return MAGIC_QUIVER_TRACKING.get(player);
    }

    public static void setMagicQuiverTracking(net.minecraft.world.entity.player.Player player, boolean value) {
        MAGIC_QUIVER_TRACKING.set(player, value);
    }

    public static String getMagicQuiverFirstCard(net.minecraft.world.entity.player.Player player) {
        return MAGIC_QUIVER_FIRST_CARD.get(player);
    }

    public static void setMagicQuiverFirstCard(net.minecraft.world.entity.player.Player player, String value) {
        MAGIC_QUIVER_FIRST_CARD.set(player, value);
    }

    public static long getMagicQuiverCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return MAGIC_QUIVER_COOLDOWN_END.get(player);
    }

    public static void setMagicQuiverCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        MAGIC_QUIVER_COOLDOWN_END.set(player, value);
    }

    public static long getBufferShieldCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return BUFFER_SHIELD_COOLDOWN_END.get(player);
    }

    public static void setBufferShieldCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        BUFFER_SHIELD_COOLDOWN_END.set(player, value);
    }

    public static int getStarCoinHammerBonus(net.minecraft.world.entity.player.Player player) {
        return STAR_COIN_HAMMER_BONUS.get(player);
    }

    public static void setStarCoinHammerBonus(net.minecraft.world.entity.player.Player player, int value) {
        STAR_COIN_HAMMER_BONUS.set(player, Math.max(0, value));
    }

    public static int getCursedSwordBonus(net.minecraft.world.entity.player.Player player) {
        return CURSED_SWORD_BONUS.get(player);
    }

    public static void setCursedSwordBonus(net.minecraft.world.entity.player.Player player, int value) {
        CURSED_SWORD_BONUS.set(player, Math.max(0, value));
    }

    public static boolean getCursedSwordBlessingTriggered(net.minecraft.world.entity.player.Player player) {
        return CURSED_SWORD_BLESSING_TRIGGERED.get(player);
    }

    public static void setCursedSwordBlessingTriggered(net.minecraft.world.entity.player.Player player, boolean value) {
        CURSED_SWORD_BLESSING_TRIGGERED.set(player, value);
    }

    public static boolean isCandyChipPlayBonusActive(net.minecraft.world.entity.player.Player player) {
        return CANDY_CHIP_PLAY_BONUS.get(player);
    }

    public static void setCandyChipPlayBonusActive(net.minecraft.world.entity.player.Player player, boolean value) {
        CANDY_CHIP_PLAY_BONUS.set(player, value);
    }

    public static long getSatelliteGiveCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return SATELLITE_GIVE_COOLDOWN_END.get(player);
    }

    public static void setSatelliteGiveCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        SATELLITE_GIVE_COOLDOWN_END.set(player, Math.max(0, value));
    }

    public static boolean isSatellitePlayBonusActive(net.minecraft.world.entity.player.Player player) {
        return SATELLITE_PLAY_BONUS.get(player);
    }

    public static void setSatellitePlayBonusActive(net.minecraft.world.entity.player.Player player, boolean value) {
        SATELLITE_PLAY_BONUS.set(player, value);
    }

    public static long getSatellitePlayBonusCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return SATELLITE_PLAY_BONUS_COOLDOWN_END.get(player);
    }

    public static void setSatellitePlayBonusCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        SATELLITE_PLAY_BONUS_COOLDOWN_END.set(player, Math.max(0, value));
    }

    public static int getNancyLuPassiveType(net.minecraft.world.entity.player.Player player) {
        return NANCY_LU_PASSIVE_TYPE.get(player);
    }

    public static void setNancyLuPassiveType(net.minecraft.world.entity.player.Player player, int value) {
        NANCY_LU_PASSIVE_TYPE.set(player, Math.max(0, value));
    }

    public static int getNancyLuActiveBonus(net.minecraft.world.entity.player.Player player) {
        return NANCY_LU_ACTIVE_BONUS.get(player);
    }

    public static void setNancyLuActiveBonus(net.minecraft.world.entity.player.Player player, int value) {
        NANCY_LU_ACTIVE_BONUS.set(player, Math.max(0, value));
    }

    public static long getNancyLuActiveBonusUntil(net.minecraft.world.entity.player.Player player) {
        return NANCY_LU_ACTIVE_BONUS_UNTIL.get(player);
    }

    public static void setNancyLuActiveBonusUntil(net.minecraft.world.entity.player.Player player, long value) {
        NANCY_LU_ACTIVE_BONUS_UNTIL.set(player, Math.max(0, value));
    }

    public static long getNancyLuHiddenUntil(net.minecraft.world.entity.player.Player player) {
        return NANCY_LU_HIDDEN_UNTIL.get(player);
    }

    public static void setNancyLuHiddenUntil(net.minecraft.world.entity.player.Player player, long value) {
        NANCY_LU_HIDDEN_UNTIL.set(player, Math.max(0, value));
    }

    public static int getMimiReturnedCardCount(net.minecraft.world.entity.player.Player player) {
        return MIMI_RETURNED_CARD_COUNT.get(player);
    }

    public static void setMimiReturnedCardCount(net.minecraft.world.entity.player.Player player, int value) {
        MIMI_RETURNED_CARD_COUNT.set(player, Math.max(0, value));
    }

    public static long getNancyLuEnderPearlImmuneUntil(net.minecraft.world.entity.player.Player player) {
        return NANCY_LU_ENDER_PEARL_IMMUNE_UNTIL.get(player);
    }

    public static void setNancyLuEnderPearlImmuneUntil(net.minecraft.world.entity.player.Player player, long value) {
        NANCY_LU_ENDER_PEARL_IMMUNE_UNTIL.set(player, Math.max(0, value));
    }

    // === 大当家立牌(fen)附件 ===

    // 养精蓄锐层数(玩家级,上限见 FenSignItem.MAX_RECHARGE)
    public static final AttachedDataKey<Integer> FEN_RECHARGE =
            register(AttachedDataKey.builder("fen_recharge", Codec.INT, () -> 0).sync().build());

    // 上次触发骰神赐福的时刻(用于"1 分钟未触发赐福 → 养精蓄锐 +1 层")
    public static final AttachedDataKey<Long> FEN_LAST_BLESSING_TICK =
            register(AttachedDataKey.builder("fen_last_blessing_tick", Codec.LONG, () -> 0L).build());

    // 注:"战斗爽·溅射"已改为被动单次效果,由 DiceCombatEvents 的局部变量承载,不再需要附件

    public static int getFenRecharge(net.minecraft.world.entity.player.Player player) {
        return FEN_RECHARGE.get(player);
    }

    public static void setFenRecharge(net.minecraft.world.entity.player.Player player, int value) {
        FEN_RECHARGE.set(player, Math.max(0, value));
    }

    public static long getFenLastBlessingTick(net.minecraft.world.entity.player.Player player) {
        return FEN_LAST_BLESSING_TICK.get(player);
    }

    public static void setFenLastBlessingTick(net.minecraft.world.entity.player.Player player, long value) {
        FEN_LAST_BLESSING_TICK.set(player, value);
    }

    // 以毒攻毒:记录生命恢复 II 的触发时刻(中毒 8 秒后)
    public static final AttachedDataKey<Long> FIGHT_POISON_WITH_POISON_REGEN_AT =
            register(AttachedDataKey.builder("fight_poison_with_poison_regen_at", Codec.LONG, () -> 0L).build());

    public static long getFightPoisonWithPoisonRegenAt(net.minecraft.world.entity.player.Player player) {
        return FIGHT_POISON_WITH_POISON_REGEN_AT.get(player);
    }

    public static void setFightPoisonWithPoisonRegenAt(net.minecraft.world.entity.player.Player player, long value) {
        FIGHT_POISON_WITH_POISON_REGEN_AT.set(player, value);
    }

    // 防御牌是否已在当前骰神赐福期间消耗过耐久(怪物近战攻击触发,每个赐福期间最多一次)
    public static final AttachedDataKey<Boolean> DEFENSE_CARD_CONSUMED_BLESSING =
            register(AttachedDataKey.builder("defense_card_consumed_blessing", Codec.BOOL, () -> false).build());

    public static boolean isDefenseCardConsumedThisBlessing(net.minecraft.world.entity.player.Player player) {
        return DEFENSE_CARD_CONSUMED_BLESSING.get(player);
    }

    public static void setDefenseCardConsumedThisBlessing(net.minecraft.world.entity.player.Player player, boolean value) {
        DEFENSE_CARD_CONSUMED_BLESSING.set(player, value);
    }

    // 蓄力卡:在骰神赐福进行中放入骰子时置位。置位期间蓄力不提供 +5 固定攻击,
    // 且本次赐福结束时不转换为"全力攻击";下次触发骰神赐福时正常生效并在其结束时转换。

    // 肉弹战车立牌(pandaman)被动:吃汉堡累计的生命值上限加成(卸下立牌时清除)
    public static final AttachedDataKey<Integer> PANDAMAN_MAX_HEALTH_BONUS =
            register(AttachedDataKey.builder("pandaman_max_health_bonus", Codec.INT, () -> 0).sync().build());

    public static int getPandamanMaxHealthBonus(net.minecraft.world.entity.player.Player player) {
        return PANDAMAN_MAX_HEALTH_BONUS.get(player);
    }

    public static void setPandamanMaxHealthBonus(net.minecraft.world.entity.player.Player player, int value) {
        PANDAMAN_MAX_HEALTH_BONUS.set(player, Math.max(0, value));
    }

    // 嘲讽来源:肉弹战车立牌(pandaman)主动施加"嘲讽"的玩家 UUID
    public static final AttachedDataKey<Optional<UUID>> PANDAMAN_TAUNT_SOURCE =
            register(AttachedDataKey.builder("pandaman_taunt_source",
                    UUIDUtil.CODEC.optionalFieldOf("id").codec(), Optional::empty).build());

    public static Optional<UUID> getPandamanTauntSource(net.minecraft.world.entity.LivingEntity entity) {
        return PANDAMAN_TAUNT_SOURCE.get(entity);
    }

    public static void setPandamanTauntSource(net.minecraft.world.entity.LivingEntity entity, Optional<UUID> value) {
        PANDAMAN_TAUNT_SOURCE.set(entity, value);
    }

    // 恋的规则书:是否已在当前世界为玩家发放过首次加入的规则书(仅服务端持久化,无需同步)
    // 「死亡重生保留」(2026-09-15 用户裁决,必须遵守):口径是「仅在玩家第一次进入世界发放一次,
    // 此后任何情况下都不再自动发放」⇒ 本键必须出现在 AstralData.onPlayerClone 的死亡保留白名单里
    // (对应 1.21.1 的 .copyOnDeath());否则死亡后回默认 false,下次登录会再发一本。
    public static final AttachedDataKey<Boolean> GUIDE_BOOK_GIVEN =
            register(AttachedDataKey.builder("guide_book_given", Codec.BOOL, () -> false).build());

    public static boolean isGuideBookGiven(net.minecraft.world.entity.player.Player player) {
        return GUIDE_BOOK_GIVEN.get(player);
    }

    public static void setGuideBookGiven(net.minecraft.world.entity.player.Player player, boolean value) {
        GUIDE_BOOK_GIVEN.set(player, value);
    }

    // 计时器守卫:本模组有时长效果的结束时刻记录(效果注册名 → 结束 tick + 重施加参数)。
    // 仅服务端使用,序列化持久化;由 EffectTimerGuard 维护,保证效果严格按 20t/s 流动。
    public static final AttachedDataKey<Map<String,
            com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry>> EFFECT_TIMER_ENDS =
            register(AttachedDataKey.builder("effect_timer_ends",
                    Codec.unboundedMap(Codec.STRING,
                            com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry.CODEC),
                    HashMap::new).build());

    // === 新筹码:原初核心 / 电击手套 / 安全气囊 ===

    /** 赋能:下一层递减的到期时刻(gameTime;0 表示无计时器) */
    public static final AttachedDataKey<Long> EMPOWER_DECAY_AT =
            register(AttachedDataKey.builder("empower_decay_at", Codec.LONG, () -> 0L).sync().build());

    /** 电击手套:本效果牌周期内是否已武装法伤扩散(触发后/周期结束清除) */
    public static final AttachedDataKey<Boolean> ELECTRIC_GLOVE_AOE =
            register(AttachedDataKey.builder("electric_glove_aoe", Codec.BOOL, () -> false).sync().build());

    /** 安全气囊:触发冷却结束时刻(1:00;0 表示无冷却) */
    public static final AttachedDataKey<Long> AIRBAG_COOLDOWN_END =
            register(AttachedDataKey.builder("airbag_cooldown_end", Codec.LONG, () -> 0L).sync().build());

    /** 电磁炮:雷击触发冷却结束时刻(1:00;0 表示无冷却;仅第二能力雷击,不影响充能攻击力加成) */
    public static final AttachedDataKey<Long> RAILGUN_COOLDOWN_END =
            register(AttachedDataKey.builder("railgun_cooldown_end", Codec.LONG, () -> 0L).sync().build());

    /**
     * 游戏大师立牌(ren):最后一次「持有鼠鼠护盾」的世界时刻 —— 被动「鼠鼠救我」的 5 分钟计时基准。
     * 玩家级、**非同步**(仅服务端使用);跨重登继续累加(persisted 于 level.dat 的 Time)。
     */
    public static final AttachedDataKey<Long> REN_SHIELD_LAST_SEEN_TICK =
            register(AttachedDataKey.builder("ren_shield_last_seen_tick", Codec.LONG, () -> 0L).build());

    /**
     * 游戏大师立牌(ren):授予护盾时玩家已有的吸收值(基线)。护盾只在此基础上 +20(10 黄心),
     * 清空时也只回收这 20 ⇒ 不吞掉金苹果/不死图腾等外部来源的吸收。
     */
    public static final AttachedDataKey<Float> REN_SHIELD_BASELINE_ABSORPTION =
            register(AttachedDataKey.builder("ren_shield_baseline_absorption", Codec.FLOAT, () -> 0.0f).build());

    /**
     * 游戏大师立牌(ren):当前那份「抗性提升」是否由本护盾施加(放大器 0 且带此标记才在清空时移除,
     * 玩家的药水或更高等级一律保留)。玩家级、非同步。
     */
    public static final AttachedDataKey<Boolean> REN_SHIELD_OWN_RESISTANCE =
            register(AttachedDataKey.builder("ren_shield_own_resistance", Codec.BOOL, () -> false).build());

    /**
     * 游戏大师立牌(ren):一次性反击层数(0/1)。获得鼠鼠护盾时 +1,被攻击时消耗 1 层并对攻击者
     * 注入一次现有反击伤害;护盾清空时归零。
     */
    public static final AttachedDataKey<Integer> REN_COUNTER_CHARGES =
            register(AttachedDataKey.builder("ren_counter_charges", Codec.INT, () -> 0).build());

    public static long getEmpowerDecayAt(net.minecraft.world.entity.player.Player player) {
        return EMPOWER_DECAY_AT.get(player);
    }

    public static void setEmpowerDecayAt(net.minecraft.world.entity.player.Player player, long value) {
        EMPOWER_DECAY_AT.set(player, Math.max(0, value));
    }

    public static boolean isElectricGloveAoe(net.minecraft.world.entity.player.Player player) {
        return ELECTRIC_GLOVE_AOE.get(player);
    }

    public static void setElectricGloveAoe(net.minecraft.world.entity.player.Player player, boolean value) {
        ELECTRIC_GLOVE_AOE.set(player, value);
    }

    public static long getAirbagCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return AIRBAG_COOLDOWN_END.get(player);
    }

    public static void setAirbagCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        AIRBAG_COOLDOWN_END.set(player, Math.max(0, value));
    }

    public static long getRailgunCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return RAILGUN_COOLDOWN_END.get(player);
    }

    public static void setRailgunCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        RAILGUN_COOLDOWN_END.set(player, Math.max(0, value));
    }

    /** synced 键快照发送(登录/重生/切维度时)。 */
    public static void sendSyncSnapshot(ServerPlayer player) {
        com.merlinkitsune.astral_dice.network.ModNetwork.syncSnapshot(player, syncedKeys());
    }

    static List<AttachedDataKey<?>> syncedKeys() {
        if (SYNCED_KEYS.isEmpty()) {
            SYNCED_KEYS.add(PLAYER_STARLIGHT);
            SYNCED_KEYS.add(DAMAGE_EFFECT_BONUS);
            SYNCED_KEYS.add(EFFECT_CARD_PLAY_COUNT);
            SYNCED_KEYS.add(EFFECT_CARD_BONUS_PLAYS);
            SYNCED_KEYS.add(LIVING_PAGE_CYCLE_BONUS);
            SYNCED_KEYS.add(FU_CARD_CYCLE_BONUS);
            SYNCED_KEYS.add(EFFECT_CARD_COOLDOWN_END);
            SYNCED_KEYS.add(HEALING_POINTS);
            SYNCED_KEYS.add(KOMACHI_DAMAGE_BONUS);
            SYNCED_KEYS.add(KOMACHI_USE_COUNT);
            SYNCED_KEYS.add(MAGIC_TOME_USE_COUNT);
            SYNCED_KEYS.add(SIGN_ACTIVE_COOLDOWN_END);
            SYNCED_KEYS.add(SIGN_READY_TYPE);
            SYNCED_KEYS.add(SIGN_READY_EXPIRE);
            SYNCED_KEYS.add(ENDER_DIE_TOTEM_COOLDOWN_END);
            SYNCED_KEYS.add(CURSED_SWORD_BONUS);
            SYNCED_KEYS.add(CANDY_CHIP_PLAY_BONUS);
            SYNCED_KEYS.add(SATELLITE_PLAY_BONUS);
            SYNCED_KEYS.add(SATELLITE_PLAY_BONUS_COOLDOWN_END);
            SYNCED_KEYS.add(NANCY_LU_ACTIVE_BONUS);
            SYNCED_KEYS.add(NANCY_LU_HIDDEN_UNTIL);
            SYNCED_KEYS.add(FEN_RECHARGE);
            SYNCED_KEYS.add(EMPOWER_DECAY_AT);
            SYNCED_KEYS.add(ELECTRIC_GLOVE_AOE);
            SYNCED_KEYS.add(AIRBAG_COOLDOWN_END);
            SYNCED_KEYS.add(RAILGUN_COOLDOWN_END);
            SYNCED_KEYS.add(RIN_PAGES);
            SYNCED_KEYS.add(PANDAMAN_MAX_HEALTH_BONUS);
            SYNCED_KEYS.add(NANCY_LU_PASSIVE_TYPE);
        }
        return SYNCED_KEYS;
    }

    public static long getRenShieldLastSeenTick(net.minecraft.world.entity.player.Player player) {
        return REN_SHIELD_LAST_SEEN_TICK.get(player);
    }

    public static void setRenShieldLastSeenTick(net.minecraft.world.entity.player.Player player, long value) {
        REN_SHIELD_LAST_SEEN_TICK.set(player, Math.max(0L, value));
    }

    public static float getRenShieldBaselineAbsorption(net.minecraft.world.entity.player.Player player) {
        return REN_SHIELD_BASELINE_ABSORPTION.get(player);
    }

    public static void setRenShieldBaselineAbsorption(net.minecraft.world.entity.player.Player player, float value) {
        REN_SHIELD_BASELINE_ABSORPTION.set(player, Math.max(0.0F, value));
    }

    public static boolean isRenShieldOwnResistance(net.minecraft.world.entity.player.Player player) {
        return REN_SHIELD_OWN_RESISTANCE.get(player);
    }

    public static void setRenShieldOwnResistance(net.minecraft.world.entity.player.Player player, boolean value) {
        REN_SHIELD_OWN_RESISTANCE.set(player, value);
    }

    public static int getRenCounterCharges(net.minecraft.world.entity.player.Player player) {
        return REN_COUNTER_CHARGES.get(player);
    }

    public static void setRenCounterCharges(net.minecraft.world.entity.player.Player player, int value) {
        REN_COUNTER_CHARGES.set(player, Math.max(0, value));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 风水师立牌(zhao)+ 两张符卡(fu_card / huo_card)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 「白泽赐福」状态机**真值**:是否处于生效期(附件与效果实例两处一致,由玩家级 tick 维持)。
     *
     * <p>与 1.21.1 侧同名同型(规格 §9.1 冻结名)。仅服务端使用(可见载体是效果实例,客户端由原生效果同步),
     * 故不 {@code .sync()}、不加入 {@code SYNCED_KEYS}。
     */
    public static final AttachedDataKey<Boolean> ZHAO_BLESSING_ACTIVE =
            register(AttachedDataKey.builder("zhao_blessing_active", Codec.BOOL, () -> false).build());

    public static boolean isZhaoBlessingActive(net.minecraft.world.entity.player.Player player) {
        return ZHAO_BLESSING_ACTIVE.get(player);
    }

    public static void setZhaoBlessingActive(net.minecraft.world.entity.player.Player player, boolean value) {
        ZHAO_BLESSING_ACTIVE.set(player, value);
    }

    /**
     * 「白泽赐福」的时序计数器:**仍需跳过的「骰神赐福结束」次数**(0 或 1)。
     *
     * <p>两种分支(与 1.21.1 逐条等价,规格 §4.5):
     * <ul>
     *   <li>施放时<b>不在</b>骰神赐福中 ⇒ 写 0 ⇒ **下一次**赐福结束即移除「白泽赐福」;</li>
     *   <li>施放时<b>已在</b>骰神赐福中 ⇒ 写 1 ⇒ **跳过当前这次**赐福结束,下一次赐福结束后移除。</li>
     * </ul>
     * 仅由玩家级 tick 的下降沿消费({@code ZhaoSignItem#tickBlessing});仅服务端使用,故不 {@code .sync()}。
     */
    public static final AttachedDataKey<Integer> ZHAO_BLESSING_SKIP_CYCLES =
            register(AttachedDataKey.builder("zhao_blessing_skip_cycles", Codec.INT, () -> 0).build());

    public static int getZhaoBlessingSkipCycles(net.minecraft.world.entity.player.Player player) {
        return ZHAO_BLESSING_SKIP_CYCLES.get(player);
    }

    public static void setZhaoBlessingSkipCycles(net.minecraft.world.entity.player.Player player, int value) {
        ZHAO_BLESSING_SKIP_CYCLES.set(player, Math.max(0, value));
    }

    /**
     * 上一拍的「骰神赐福存在性」(下降沿检测的**基准**;仅服务端,每 tick 读取)。
     *
     * <p>不 {@code .sync()}:同步会让每 tick 都写包(同口径先例:{@code healing_prev_blessing})。
     */
    public static final AttachedDataKey<Boolean> ZHAO_PREV_BLESSING =
            register(AttachedDataKey.builder("zhao_prev_blessing", Codec.BOOL, () -> false).build());

    public static boolean isZhaoPrevBlessing(net.minecraft.world.entity.player.Player player) {
        return ZHAO_PREV_BLESSING.get(player);
    }

    public static void setZhaoPrevBlessing(net.minecraft.world.entity.player.Player player, boolean value) {
        ZHAO_PREV_BLESSING.set(player, value);
    }

    /**
     * 风水师立牌「白泽赐福」期间**溢出治疗等量转化的攻击力加成**(整数部分)。
     *
     * <p>写入方 = {@code item/sign/ZhaoSignItem#onLivingHeal} 经 {@link #addZhaoOverflowBonus}(整数化 +
     * 余数留档,禁止无声丢数);读取方 = {@code combat/DiceCombatModifiers} 的攻击力修饰器;
     * **唯一回收动作** = {@link #clearZhaoOverflowBonus}(整数 + 余数一并归零,幂等)。
     * 仅服务端使用,故不 {@code .sync()}(与 1.21.1 侧同名同型)。
     */
    public static final AttachedDataKey<Integer> ZHAO_OVERFLOW_BONUS =
            register(AttachedDataKey.builder("zhao_overflow_bonus", Codec.INT, () -> 0).build());

    /** 溢出治疗取整后的**余数累加器**(< 1 的尾数;见 {@link #ZHAO_OVERFLOW_BONUS}) */
    public static final AttachedDataKey<Float> ZHAO_OVERFLOW_REMAINDER =
            register(AttachedDataKey.builder("zhao_overflow_remainder", Codec.FLOAT, () -> 0.0F).build());

    public static int getZhaoOverflowBonus(net.minecraft.world.entity.player.Player player) {
        return ZHAO_OVERFLOW_BONUS.get(player);
    }

    public static void setZhaoOverflowBonus(net.minecraft.world.entity.player.Player player, int value) {
        ZHAO_OVERFLOW_BONUS.set(player, Math.max(0, value));
    }

    public static float getZhaoOverflowRemainder(net.minecraft.world.entity.player.Player player) {
        return ZHAO_OVERFLOW_REMAINDER.get(player);
    }

    public static void setZhaoOverflowRemainder(net.minecraft.world.entity.player.Player player, float value) {
        ZHAO_OVERFLOW_REMAINDER.set(player, Math.max(0.0F, value));
    }

    /**
     * 把一次**溢出治疗量**累加进攻击力加成:整数部分进 {@link #ZHAO_OVERFLOW_BONUS},
     * 小数部分留在 {@link #ZHAO_OVERFLOW_REMAINDER}(下一次溢出可能与余数凑成新的整数点)
     * —— 规格 §5.2「禁止无声丢数」的选项一(余数也存进附件),与 1.21.1 逐字同形。
     */
    public static void addZhaoOverflowBonus(net.minecraft.world.entity.player.Player player, float overflow) {
        if (overflow <= 0.0F) return;
        float total = getZhaoOverflowRemainder(player) + overflow;
        int whole = (int) Math.floor(total);
        setZhaoOverflowRemainder(player, total - whole);
        if (whole > 0) {
            setZhaoOverflowBonus(player, getZhaoOverflowBonus(player) + whole);
        }
    }

    /** 溢出加成的**唯一回收动作**(整数 + 余数一并归零;幂等) */
    public static void clearZhaoOverflowBonus(net.minecraft.world.entity.player.Player player) {
        setZhaoOverflowBonus(player, 0);
        setZhaoOverflowRemainder(player, 0.0F);
    }

    /**
     * 「厄运」(符卡-祸 的镜像效果)下一次周期伤害的**绝对结算刻**(gameTime;0 = 未起算)。
     *
     * <p><b>计时器与结算分离</b>:该键只在「持有张数 0 → &gt;0」时起算一次,此后**只由结算推进**
     * (每次结算后 += 2:00),持卡张数的增减**一律不写本键** ⇒ 张数变化不会重置/推迟计时器,
     * 而每次结算造成的伤害取「**结算时刻**的当前张数」。仅服务端使用,故不 {@code .sync()}。
     */
    public static final AttachedDataKey<Long> HUO_CARD_NEXT_DAMAGE_TICK =
            register(AttachedDataKey.builder("huo_card_next_damage_tick", Codec.LONG, () -> 0L).build());

    public static long getHuoCardNextDamageTick(net.minecraft.world.entity.player.Player player) {
        return HUO_CARD_NEXT_DAMAGE_TICK.get(player);
    }

    public static void setHuoCardNextDamageTick(net.minecraft.world.entity.player.Player player, long value) {
        HUO_CARD_NEXT_DAMAGE_TICK.set(player, Math.max(0L, value));
    }

    /**
     * 「符卡-福」本出牌周期累计的出牌数加成(0,1,2,…):**每打出一次 +1**(可累计,不是"每轮一次"的开关)。
     *
     * <p>与活体书页的 {@link #LIVING_PAGE_CYCLE_BONUS} 同级、同址清理:
     * 计入 {@code EffectCardPeriod#getMaxAllowed} 的 extra(受 {@code min(9, 1+extra)} 全局封顶),
     * 由 {@code EffectCardPeriod#clearRoundBonuses} 在周期归零时清除。
     *
     * <p><b>必须同步</b>:客户端预检 {@code BaseEffectCardItem#isBlockedOnClient} →
     * {@code EffectCardPeriod#isBurstFull} → {@code getMaxAllowed} 会在客户端读本键;
     * 不同步会让客户端按较低的上限误判"本轮已打满"(与 {@code living_page_cycle_bonus} 的处理一致)。
     */
    public static final AttachedDataKey<Integer> FU_CARD_CYCLE_BONUS =
            register(AttachedDataKey.builder("fu_card_cycle_bonus", Codec.INT, () -> 0).sync().build());

    public static int getFuCardCycleBonus(net.minecraft.world.entity.player.Player player) {
        return FU_CARD_CYCLE_BONUS.get(player);
    }

    // 计数器只增不减(归零由周期清理负责),此处仅钳制非负
    public static void setFuCardCycleBonus(net.minecraft.world.entity.player.Player player, int value) {
        FU_CARD_CYCLE_BONUS.set(player, Math.max(0, value));
    }

    private ModAttachments() {
    }
}
