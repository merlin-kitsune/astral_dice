package com.merlinkitsune.astral_dice.component;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.shop.CardShopShelf;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家附件注册中心(1.20.1 Forge 移植版):常量名与静态包装器签名与 1.21 分支保持一致,
 * 内部由 {@link AttachedDataKey}(AstralData Capability / ForgeData)承载,
 * synced 键经 {@link com.merlinkitsune.astral_dice.network.ModNetwork} 同步到客户端。
 */
public class ModAttachments {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModAttachments.class);

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

    // 抽奖盒(event_drawer):上次购买后的冷却结束时刻(level.getGameTime() + 6000,即 5:00;
    // now < until 表示冷却中,0 = 无冷却)。写入与判定都在服务端;.sync() 只为与 1.21.1 的
    // .sync(ByteBufCodecs.VAR_LONG) 等价(本仓对 EFFECT_CARD_COOLDOWN_END 亦两线同口径地同步)。
    public static final AttachedDataKey<Long> EVENT_DRAWER_COOLDOWN_UNTIL =
            register(AttachedDataKey.builder("event_drawer_cooldown_until", Codec.LONG, () -> 0L).sync().build());

    public static long getEventDrawerCooldownUntil(net.minecraft.world.entity.player.Player player) {
        return EVENT_DRAWER_COOLDOWN_UNTIL.get(player);
    }

    public static void setEventDrawerCooldownUntil(net.minecraft.world.entity.player.Player player, long value) {
        EVENT_DRAWER_COOLDOWN_UNTIL.set(player, value);
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

    // 手电筒-爆闪筹码:**该玩家**已发放过星光的**目标 UUID 集**(逗号分隔;**玩家级**附件)。
    // 判重粒度 = **玩家 × 目标**:同一目标对不同佩戴者各可发放一次(旧口径的**目标实体级**附件
    // 已随本次改动删除 —— 它让目标被**任何**玩家首次攻击后即置位,后出手的佩戴者拿不到)。
    // 写法沿用同族的玩家级字符串集口径(见 {@link #FLASHLIGHT_GRANTED_TARGETS} / {@link #SHERRY_REASONING_TARGETS});
    // 读写方 = FlashlightBurstChipItem#onAttack,记录上限见该类 MAX_TRACKED_TARGETS(满则不再发放)。
    public static final AttachedDataKey<String> FLASHLIGHT_BURST_TARGETS =
            register(AttachedDataKey.builder("flashlight_burst_targets", Codec.STRING, () -> "").build());

    public static String getFlashlightBurstTargets(net.minecraft.world.entity.player.Player player) {
        return FLASHLIGHT_BURST_TARGETS.get(player);
    }

    public static void setFlashlightBurstTargets(net.minecraft.world.entity.player.Player player, String value) {
        FLASHLIGHT_BURST_TARGETS.set(player, value == null ? "" : value);
    }

    // 财运签筹码:当前骰神赐福周期是否已发放过(玩家级,每个赐福周期一次)。
    // 由 DiceCombatEvents 在**骰神赐福新周期开始处**复位(与 cursed_sword_blessing_triggered 同批同处复位),
    // 由 FortuneStickChipItem#onHostileKill 置位(照 CursedSwordChipItem 的「每赐福周期一次」先例)。
    public static final AttachedDataKey<Boolean> FORTUNE_STICK_BLESSING_CLAIMED =
            register(AttachedDataKey.builder("fortune_stick_blessing_claimed", Codec.BOOL, () -> false).build());

    public static boolean isFortuneStickBlessingClaimed(net.minecraft.world.entity.player.Player player) {
        return FORTUNE_STICK_BLESSING_CLAIMED.get(player);
    }

    public static void setFortuneStickBlessingClaimed(net.minecraft.world.entity.player.Player player, boolean value) {
        FORTUNE_STICK_BLESSING_CLAIMED.set(player, value);
    }

    // 试剂-深渊筹码:「装备时 +2 治愈点」的装备会话闸门(玩家级,持久化)。
    // ⚠️ 为什么必须持久化:Curios 只持久化 stacks、**不持久化 previousStacks** ⇒ 登录 / 重生 / 切维度后
    //    首 tick 的 prevStack 恒为空栈而槽里有物品,Curios 把这判成一次装备变化并**重放 onEquip**
    //    (与医疗箱 GRANT_BIT / StarLightManager GRANT_BIT 同一根因)⇒ "空槽守卫"挡不住,会重复加分。
    // 申领 = 真的新装上;释放 = 卸下 / 死亡(死亡时 HealingManager.clear 已把治愈点清零,
    // 重生后由 Curios 重放 onEquip 重新发放,故这里释放即可)。
    public static final AttachedDataKey<Boolean> ABYSS_REAGENT_GRANTED =
            register(AttachedDataKey.builder("abyss_reagent_granted", Codec.BOOL, () -> false).build());

    /** 申领装备闸门:未申领过则置位并返回 true,已申领过(重复 onEquip / Curios 重放)返回 false。 */
    public static boolean claimAbyssReagentGrant(net.minecraft.world.entity.player.Player player) {
        if (ABYSS_REAGENT_GRANTED.get(player)) return false;
        ABYSS_REAGENT_GRANTED.set(player, true);
        return true;
    }

    /** 释放装备闸门(卸下 / 死亡时调用,与 {@link #claimAbyssReagentGrant} 配对)。 */
    public static void releaseAbyssReagentGrant(net.minecraft.world.entity.player.Player player) {
        if (ABYSS_REAGENT_GRANTED.get(player)) {
            ABYSS_REAGENT_GRANTED.set(player, false);
        }
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

    // ===== 立牌玩家级状态复合键 sign_state(2026-10-19 B2)=====
    // 原先 10 个独立键(sign_active_* / sign_ready_*)折叠为一条记录,字段同名同类型、默认值逐一相同
    // ⇒ 数值语义 / 判定顺序 / 键名大小写均无变化。旧 10 键**仍注册**(见下方,禁止删除):
    // 只用于登录时的一次性折叠迁移(migrateSignStateFromLegacy),迁移之后所有读写点(即下面这些
    // get*/set* 包装器)一律走本键 ⇒ 调用点零改动。
    // 同步:客户端读的是 cooldown_end(tooltip 倒计时) —— 但**并非只有它同步**:遗留键 sign_ready_type /
    // sign_ready_expire 也各自 .sync() 并进 SYNCED_KEYS(见下方遗留注册,已 @Deprecated 但仍同步)。
    // 复合键整体同步后这 3 个字段语义不变,其余字段客户端本来就不读。
    public static final AttachedDataKey<SignState> SIGN_STATE =
            register(AttachedDataKey.builder("sign_state", SignState.CODEC, () -> SignState.EMPTY).sync().build());

    private static SignState signState(net.minecraft.world.entity.player.Player player) {
        SignState state = SIGN_STATE.get(player);
        return state == null ? SignState.EMPTY : state;
    }

    private static void updateSignState(net.minecraft.world.entity.player.Player player,
                                        java.util.function.UnaryOperator<SignState> update) {
        SignState current = signState(player);
        SignState next = update.apply(current);
        // 值未变则不写(等价于旧实现"写同一个值",但省掉无谓的存档/同步开销)
        if (!next.equals(current)) {
            SIGN_STATE.set(player, next);
        }
    }

    /**
     * 登录迁移(幂等):旧 10 键 → {@code sign_state}。
     *
     * <p>判据 = 「{@code sign_state} 仍是默认值」且「旧 10 键中至少一个非默认」。
     * 迁移后旧键**不删除、不清写**(旧存档仍可读;且值保持原样作为历史留痕),
     * 幂等由「{@code sign_state} 非默认即早退」保证 —— 重复登录不会用旧值覆盖新值。
     *
     * <p>只在服务端执行;日志格式固定 {@code AP_SIGN_STATE_MIGRATED:player=<名字>:from=legacy}。
     */
    public static void migrateSignStateFromLegacy(net.minecraft.world.entity.player.Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!SignState.EMPTY.equals(signState(player))) return;
        // 注意:这里必须**直接读旧键**(不能走上面的 get* 包装器,它们已改为读 sign_state)
        String legacyLockSign = SIGN_ACTIVE_LOCK_SIGN.get(player);
        SignState legacy = new SignState(
                SIGN_ACTIVE_COOLDOWN_END.get(player),
                SIGN_ACTIVE_MAX_COOLDOWN.get(player),
                legacyLockSign == null ? "" : legacyLockSign,
                SIGN_ACTIVE_LOCK_END.get(player),
                SIGN_ACTIVE_REDUCTION_POOL.get(player),
                SIGN_ACTIVE_LOCK_GRACE_END.get(player),
                SIGN_ACTIVE_LOCK_PLAYED.get(player),
                SIGN_ACTIVE_LOCK_LAST_SEEN.get(player),
                SIGN_READY_TYPE.get(player),
                SIGN_READY_EXPIRE.get(player));
        if (SignState.EMPTY.equals(legacy)) return;
        SIGN_STATE.set(player, legacy);
        LOGGER.info("AP_SIGN_STATE_MIGRATED:player={}:from=legacy", player.getGameProfile().getName());
    }

    // 立牌主动技能冷却结束时刻(玩家级,不受立牌装卸影响;0 表示无冷却)。
    // 注册保留(旧存档可读 + 迁移来源);读写一律走 sign_state。
    public static final AttachedDataKey<Long> SIGN_ACTIVE_COOLDOWN_END =
            register(AttachedDataKey.builder("sign_active_cooldown_end", Codec.LONG, () -> 0L).sync().build());

    public static long getSignActiveCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return signState(player).sign_active_cooldown_end();
    }

    public static void setSignActiveCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        updateSignState(player, state -> state.withSignActiveCooldownEnd(value));
    }

    // 怪力乱神立牌(lingling)被动「拦截者」:首次装备赠 2 张强化拒止的**一生一次**标记(玩家级;
    // 跨死亡保留、卸下不清 —— 刻意不走基类 claimEquipGrant(那条通道卸下即释放 ⇒ 卸下再装能刷牌)。
    // 1.21.1 侧是同名键的 .copyOnDeath();本线无 DataAttachmentType ⇒ 用玩家 NBT 键 + AstralData 白名单。
    public static final AttachedDataKey<Boolean> LINGLING_GRANTED =
            register(AttachedDataKey.builder("lingling_granted", Codec.BOOL, () -> false).build());

    public static boolean isLinglingGranted(net.minecraft.world.entity.player.Player player) {
        return LINGLING_GRANTED.get(player);
    }

    public static void setLinglingGranted(net.minecraft.world.entity.player.Player player, boolean value) {
        LINGLING_GRANTED.set(player, value);
    }

    // 卡牌商店「商店大酬宾」窗口的结束时刻(玩家级;0 = 未生效)。本批只留钩子,事件卡系统尚未实装。
    // 仅服务端使用(开店抽池时读一次),故**不 .sync()、不加入 SYNCED_KEYS**(与 1.21.1 同口径)。
    public static final AttachedDataKey<Long> SHOP_PROMOTION_UNTIL =
            register(AttachedDataKey.builder("shop_promotion_until", Codec.LONG, () -> 0L).build());

    public static long getShopPromotionUntil(net.minecraft.world.entity.player.Player player) {
        return SHOP_PROMOTION_UNTIL.get(player);
    }

    public static void setShopPromotionUntil(net.minecraft.world.entity.player.Player player, long value) {
        SHOP_PROMOTION_UNTIL.set(player, value);
    }

    // 卡牌商店「二级转账」的玩家级冷却结束时刻(gameTime;0 = 无冷却)。
    // 服务端写入与判定;客户端除菜单 DataSlot 外,还要在 **Jade 悬浮框**显示「补货倒计时」
    // (方块无 BlockEntity ⇒ 1.20.1 的 Jade 不收服务端数据,只能读同步值)⇒ 本条 **.sync()**
    // 并进 SYNCED_KEYS(登录/重生/切维度时补发快照)。只同步一个 long,开销可忽略。
    public static final AttachedDataKey<Long> SHOP_TRANSFER_COOLDOWN_END =
            register(AttachedDataKey.builder("shop_transfer_cooldown_end", Codec.LONG, () -> 0L).sync().build());

    public static long getShopTransferCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return SHOP_TRANSFER_COOLDOWN_END.get(player);
    }

    public static void setShopTransferCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        SHOP_TRANSFER_COOLDOWN_END.set(player, value);
    }

    // 卡牌商店:货架存档(1.20.1 移植,对应 1.21.1 的 ModAttachments.SHOP_SHELF)。
    // 改造前货架只活在菜单字段里,开店时无条件重抽 ⇒ 关店再开店就换一批;现在货架落在这里(随玩家存档持久化),
    // 只有「沙漏归 0」那一刻(服务端 tick: shop/CardShopCycleTicker)或大酬宾事件才会重抽覆写,开店只读(shop/CardShopShelf)。
    // 仅服务端使用,不同步客户端(客户端照旧靠货架槽位同步读同一批卡) ⇒ 不 .sync()、不加入 SYNCED_KEYS。
    public static final AttachedDataKey<CardShopShelf> SHOP_SHELF =
            register(AttachedDataKey.builder("shop_shelf", CardShopShelf.CODEC, () -> CardShopShelf.EMPTY).build());

    // 筹码商店:候选存档(2026-10-12 移植,对应 1.21.1 的 ModAttachments.CHIP_SHOP_STATE)。
    // 与 SHOP_SHELF 完全同型:菜单只读、重抽入口只有三处(shop/ChipShopOpener#ensureOffers 的进店补抽、
    // ChipShopMenu#handleRefresh、购买成功),服务端 tick(shop/ChipShopRegenTicker)只恢复刷新次数。
    // 客户端菜单靠隐藏槽位读同一批候选 + DataSlot 读价格/次数/品质码;另需在 **Jade 悬浮框**显示
    // 「可进入倒计时」——方块无 BlockEntity ⇒ 1.20.1 的 Jade 不收服务端数据,只能读同步值,
    // 故本条 **.sync()** 并进 SYNCED_KEYS(整条记录含 3 个候选 id,体积很小)。
    // 注意:客户端只读其中的 {@code cooldownUntil},其余字段在客户端没有消费方。
    public static final AttachedDataKey<ChipShopState> CHIP_SHOP_STATE =
            register(AttachedDataKey.builder("chip_shop_state", ChipShopState.CODEC, () -> ChipShopState.EMPTY)
                    .sync().build());

    // 立牌主动技能"本次冷却实际使用的最大冷却 tick"(路线 A:所有减免方一律读它作基准,不再各自重算;
    // 0 表示缺失/无冷却,减免方回退旧行为;仅服务端使用,故不 .sync()、不加入 SYNCED_KEYS)。
    // 写入时机(第二批「立牌主动技能三态化」):① 直接起冷却时与 SIGN_ACTIVE_COOLDOWN_END 成对写入;
    // ② 进入"锁定(生效中)"态时先写入基准(锁定期间减免方照旧读它并累加进 SIGN_ACTIVE_REDUCTION_POOL),
    //    冷却起点火时再改写为扣池后的实际冷却 effective = max(0, 基准 − 池)。
    public static final AttachedDataKey<Long> SIGN_ACTIVE_MAX_COOLDOWN =
            register(AttachedDataKey.builder("sign_active_max_cooldown", Codec.LONG, () -> 0L).build());

    public static long getSignActiveMaxCooldown(net.minecraft.world.entity.player.Player player) {
        return signState(player).sign_active_max_cooldown();
    }

    public static void setSignActiveMaxCooldown(net.minecraft.world.entity.player.Player player, long value) {
        updateSignState(player, state -> state.withSignActiveMaxCooldown(value));
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
        return signState(player).sign_active_lock_sign();
    }

    public static void setSignActiveLockSign(net.minecraft.world.entity.player.Player player, String value) {
        updateSignState(player, state -> state.withSignActiveLockSign(value == null ? "" : value));
    }

    public static long getSignActiveLockEnd(net.minecraft.world.entity.player.Player player) {
        return signState(player).sign_active_lock_end();
    }

    public static void setSignActiveLockEnd(net.minecraft.world.entity.player.Player player, long value) {
        updateSignState(player, state -> state.withSignActiveLockEnd(value));
    }

    public static long getSignActiveReductionPool(net.minecraft.world.entity.player.Player player) {
        return signState(player).sign_active_reduction_pool();
    }

    public static void setSignActiveReductionPool(net.minecraft.world.entity.player.Player player, long value) {
        updateSignState(player, state -> state.withSignActiveReductionPool(Math.max(0L, value)));
    }

    public static void addSignActiveReductionPool(net.minecraft.world.entity.player.Player player, long delta) {
        updateSignState(player, state -> state.withSignActiveReductionPool(
                Math.max(0L, state.sign_active_reduction_pool() + delta)));
    }

    public static long getSignActiveLockGraceEnd(net.minecraft.world.entity.player.Player player) {
        return signState(player).sign_active_lock_grace_end();
    }

    public static void setSignActiveLockGraceEnd(net.minecraft.world.entity.player.Player player, long value) {
        updateSignState(player, state -> state.withSignActiveLockGraceEnd(value));
    }

    public static boolean getSignActiveLockPlayed(net.minecraft.world.entity.player.Player player) {
        return signState(player).sign_active_lock_played();
    }

    public static void setSignActiveLockPlayed(net.minecraft.world.entity.player.Player player, boolean value) {
        updateSignState(player, state -> state.withSignActiveLockPlayed(value));
    }

    // 锁定态的"离线补偿基准":本方法最后一次见到该玩家的 gameTime(0 = 无锁定/宽限计时,不参与补偿)。
    // 为什么需要它:两个时钟在多人服务器上不同步 —— 门控效果的剩余时长只在 LivingEntity#tickEffects
    // 里递减(玩家离线期间**冻结**),而 SIGN_ACTIVE_LOCK_END / SIGN_ACTIVE_LOCK_GRACE_END 是**绝对
    // gameTime**(服务器只要在跑就照常推进)⇒ 「离线时长 > 上界剩余」后重登会看到"上界已过而效果仍在",
    // BaseSignItem#isSignActiveLocked 一过界即判未锁定 ⇒ 锁定被墙钟单方面提前结束。
    // BaseSignItem#tickSignActiveLock 用本键算出两次结算之间错过的间隔 gap,把锁定自己的两个截止刻
    // **整体后移** gap,使两个时钟对称(等效"离线期间锁定不走"),**不读任何效果实例**。
    // 仅服务端使用(与上面 5 个锁定键同性质),故一律不 .sync()、不加入 SYNCED_KEYS;
    // 未 .inMemory() ⇒ 落 AstralData 的 persistent compound,随玩家 NBT(players/<uuid>.dat)保存。
    public static final AttachedDataKey<Long> SIGN_ACTIVE_LOCK_LAST_SEEN =
            register(AttachedDataKey.builder("sign_active_lock_last_seen", Codec.LONG, () -> 0L).build());

    public static long getSignActiveLockLastSeen(net.minecraft.world.entity.player.Player player) {
        return signState(player).sign_active_lock_last_seen();
    }

    public static void setSignActiveLockLastSeen(net.minecraft.world.entity.player.Player player, long value) {
        updateSignState(player, state -> state.withSignActiveLockLastSeen(value));
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
        return signState(player).sign_ready_type();
    }

    /** @deprecated 已废弃,由目标选择器会话替代;禁止新代码使用 */
    @Deprecated
    public static void setSignReadyType(net.minecraft.world.entity.player.Player player, int value) {
        updateSignState(player, state -> state.withSignReadyType(value));
    }

    /** @deprecated 已废弃,由目标选择器会话替代;禁止新代码使用 */
    @Deprecated
    public static long getSignReadyExpire(net.minecraft.world.entity.player.Player player) {
        return signState(player).sign_ready_expire();
    }

    /** @deprecated 已废弃,由目标选择器会话替代;禁止新代码使用 */
    @Deprecated
    public static void setSignReadyExpire(net.minecraft.world.entity.player.Player player, long value) {
        updateSignState(player, state -> state.withSignReadyExpire(value));
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

    // 魔法箭袋筹码:是否已记录"本次使用的效果牌"(一次触发周期内)
    public static final AttachedDataKey<Boolean> MAGIC_QUIVER_TRACKING =
            register(AttachedDataKey.builder("magic_quiver_tracking", Codec.BOOL, () -> false).build());

    // 魔法箭袋筹码:记录的**本次使用**的效果牌类型(living_page/railgun/... 由 cardTypeId 给出)
    // ⚠️ 附件注册 id 仍为历史名 `magic_quiver_first_card`(2026-10-15 语义由「第一张」改为
    //    「本次使用的那张」,刻意**不改 key** 以保留既有存档中的值)。
    public static final AttachedDataKey<String> MAGIC_QUIVER_LAST_CARD =
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

    // 「装备时获得 N 层星光」类筹码/立牌的**发放闸门**(位掩码;位含义见 StarLightManager.GRANT_BIT_*)。
    // 为什么需要:Curios 只持久化 stacks、**不持久化 previousStacks** ⇒ 登录 / 重生 / 切维度后
    // 首 tick 的 prevStack 恒为空栈而槽里有物品,Curios 会把这判成一次装备变化并**重放 onEquip**
    // (1.20.1 curios 5.14.1;1.21.1 9.5.1 同构)。所以"空槽守卫"挡不住这条路径
    // (重放时它恰好就是空栈),只有玩家级、持久化的闸门能区分"真的新装上"与"登录重放"。
    // 必须持久化(随附件存档),且**不 `.sync()`、不进 SYNCED_KEYS**(仅服务端判定,客户端不读)。
    public static final AttachedDataKey<Integer> STARLIGHT_EQUIP_GRANT_FLAGS =
            register(AttachedDataKey.builder("starlight_equip_grant_flags", Codec.INT, () -> 0).build());

    // 「装备时获得 N 层星光」的**发放账本**:每枚 4 bit 存「本次装备**实际**获得的星光量」(0..15),
    // 槽位顺序 = `StarLightManager.GRANT_BIT_*` 的位号(bit0 → 最低 4 bit)。
    // 为什么必须记账:「卸除即扣除」这条**全筹码底线**要按实际值扣 —— 星光已到上限时装备**一点没涨**,
    // 照名义值扣就是白扣玩家自己攒的星光。必须持久化且**不 `.sync()`/不进 `SYNCED_KEYS`**(仅服务端判定)。
    public static final AttachedDataKey<Integer> STARLIGHT_EQUIP_GRANT_AMOUNTS =
            register(AttachedDataKey.builder("starlight_equip_grant_amounts", Codec.INT, () -> 0).build());

    // 医疗箱筹码「装备触发」的**发放闸门**(位掩码;位含义见 HealingManager.GRANT_BIT_MEDKIT_*)。
    // 为什么需要:Curios 只持久化 stacks、**不持久化 previousStacks** ⇒ 登录 / 重生 / 切维度后首 tick 的
    // prevStack 恒为空栈而槽里有物品,Curios 会把这判成一次装备变化并**重放 onEquip** ⇒ "空槽守卫"挡不住。
    // 闸门的作用是把「Curios 重放」与「我们自己发起的生命周期触发」**二选一**(否则同一时点会触发两次)。
    // 必须持久化(随附件存档)且**不 `.sync()`/不进 SYNCED_KEYS**(仅服务端判定,客户端不读)。
    public static final AttachedDataKey<Integer> MEDKIT_EQUIP_GRANT_FLAGS =
            register(AttachedDataKey.builder("medkit_equip_grant_flags", Codec.INT, () -> 0).build());

    // 医疗箱筹码「装备触发」的**发放账本**:每件 4 bit 存「本次装备会话**实际**获得的治愈层数」(0..15),
    // 槽位顺序 = `HealingManager.GRANT_BIT_MEDKIT_*` 的位号(bit0 → 最低 4 bit)。
    // 为什么必须记账:层数已到上限(32)时装备**一点没涨**,照名义值回撤就是白扣玩家自己攒的层数。
    // 必须持久化且**不 `.sync()`/不进 SYNCED_KEYS**(仅服务端判定)。
    public static final AttachedDataKey<Integer> MEDKIT_EQUIP_GRANT_AMOUNTS =
            register(AttachedDataKey.builder("medkit_equip_grant_amounts", Codec.INT, () -> 0).build());

    // 诅咒之剑筹码:累计击杀不少于 20 血的敌对目标获得的攻击力加成(只有真正卸下筹码才清除;死亡不清)
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

    public static String getMagicQuiverLastCard(net.minecraft.world.entity.player.Player player) {
        return MAGIC_QUIVER_LAST_CARD.get(player);
    }

    public static void setMagicQuiverLastCard(net.minecraft.world.entity.player.Player player, String value) {
        MAGIC_QUIVER_LAST_CARD.set(player, value);
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

    public static int getStarlightEquipGrantFlags(net.minecraft.world.entity.player.Player player) {
        return STARLIGHT_EQUIP_GRANT_FLAGS.get(player);
    }

    public static void setStarlightEquipGrantFlags(net.minecraft.world.entity.player.Player player, int value) {
        STARLIGHT_EQUIP_GRANT_FLAGS.set(player, value);
    }

    public static int getStarlightEquipGrantAmounts(net.minecraft.world.entity.player.Player player) {
        return STARLIGHT_EQUIP_GRANT_AMOUNTS.get(player);
    }

    public static void setStarlightEquipGrantAmounts(net.minecraft.world.entity.player.Player player, int value) {
        STARLIGHT_EQUIP_GRANT_AMOUNTS.set(player, value);
    }

    public static int getMedkitEquipGrantFlags(net.minecraft.world.entity.player.Player player) {
        return MEDKIT_EQUIP_GRANT_FLAGS.get(player);
    }

    public static void setMedkitEquipGrantFlags(net.minecraft.world.entity.player.Player player, int value) {
        MEDKIT_EQUIP_GRANT_FLAGS.set(player, value);
    }

    public static int getMedkitEquipGrantAmounts(net.minecraft.world.entity.player.Player player) {
        return MEDKIT_EQUIP_GRANT_AMOUNTS.get(player);
    }

    public static void setMedkitEquipGrantAmounts(net.minecraft.world.entity.player.Player player, int value) {
        MEDKIT_EQUIP_GRANT_AMOUNTS.set(player, value);
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
            com.merlinkitsune.starengine.event.EffectTimerGuard.TimerEntry>> EFFECT_TIMER_ENDS =
            register(AttachedDataKey.builder("effect_timer_ends",
                    Codec.unboundedMap(Codec.STRING,
                            com.merlinkitsune.starengine.event.EffectTimerGuard.TimerEntry.CODEC),
                    HashMap::new).build());

    // === 新筹码:原初核心 / 电击手套 / 安全气囊 ===

    /** 赋能:下一层递减的到期时刻(gameTime;0 表示无计时器) */
    public static final AttachedDataKey<Long> EMPOWER_DECAY_AT =
            register(AttachedDataKey.builder("empower_decay_at", Codec.LONG, () -> 0L).sync().build());

    // ⚠️ 电击手套的「本周期已武装」键 `electric_glove_aoe` 已于 2026-10-05 **整个移除**:
    //    扩散改为绑定「该牌本次造成的那一次伤害」(见 item/chip/ElectricGloveChipItem 类头),
    //    不再存在任何玩家级武装状态 ⇒ 既不需要附件键、也不需要跨线同步键。
    //    旧存档里残留的同名 NBT 不会被读取(本线 Capability 只按本类**已注册**的键读写)⇒ 无副作用。

    /** 安全气囊:触发冷却结束时刻(1:00;0 表示无冷却) */
    public static final AttachedDataKey<Long> AIRBAG_COOLDOWN_END =
            register(AttachedDataKey.builder("airbag_cooldown_end", Codec.LONG, () -> 0L).sync().build());

    /**
     * 安全气囊:**受击充能**冷却结束时刻(15 秒;0 表示无冷却;2026-10-15 用户裁决追加能力)。
     *
     * <p>⚠️ 与「保命无效化」的 {@link #AIRBAG_COOLDOWN_END}(1:00)**完全独立**:两条能力各自计时,
     * 互不干扰(保命冷却中照样能靠受击攒充能,反之亦然)。仅服务端使用,不 {@code .sync()}、
     * 不进 {@code SYNCED_KEYS}。
     */
    public static final AttachedDataKey<Long> AIRBAG_HIT_CHARGE_COOLDOWN_END =
            register(AttachedDataKey.builder("airbag_hit_charge_cooldown_end", Codec.LONG, () -> 0L).build());

    /**
     * 磨刀石「保留 1 血」（不可被一次伤害击倒）的**触发冷却结束时刻**（1:00；0 表示无冷却）。
     *
     * <p>2026-09-24 用户裁决：该保命能力此前**无冷却**，而它的生效条件与安全气囊的「致命伤害」完全
     * 重叠 ⇒ 戴着磨刀石几乎不会被单次伤害打死，气囊的资源优势被抹平。现改为**一次保命耗一次冷却**
     * （1:00，与安全气囊同档）；保命优先级明确为 **安全气囊 &gt; 磨刀石**（见 {@code event/ChipDamageHandler}）。
     *
     * <p>只在 cap **实际削减了伤害**时写入（不致命的攻击不耗冷却）；低血量减伤（-2）不受本冷却影响。
     * **仅服务端使用** ⇒ 不 {@code .sync()}。
     */
    public static final AttachedDataKey<Long> WHETSTONE_GUARD_COOLDOWN_END =
            register(AttachedDataKey.builder("whetstone_guard_cooldown_end", Codec.LONG, () -> 0L).build());
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

    public static long getAirbagCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return AIRBAG_COOLDOWN_END.get(player);
    }

    public static void setAirbagCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        AIRBAG_COOLDOWN_END.set(player, Math.max(0, value));
    }

    public static long getAirbagHitChargeCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return AIRBAG_HIT_CHARGE_COOLDOWN_END.get(player);
    }

    public static void setAirbagHitChargeCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        AIRBAG_HIT_CHARGE_COOLDOWN_END.set(player, Math.max(0, value));
    }

    public static long getWhetstoneGuardCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return WHETSTONE_GUARD_COOLDOWN_END.get(player);
    }

    public static void setWhetstoneGuardCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        WHETSTONE_GUARD_COOLDOWN_END.set(player, Math.max(0, value));
    }

    public static long getRailgunCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return RAILGUN_COOLDOWN_END.get(player);
    }

    public static void setRailgunCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        RAILGUN_COOLDOWN_END.set(player, Math.max(0, value));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 蛟龙立牌(mamushi):觉醒层数 / 撕咬加成锁存 / 强制冷却硬闸门(2026-09-27)
    //
    // 与 1.21.1 侧**同名同型、同语义**(先例见上方「白泽赐福」「教主立牌」两段):
    // 只有 mamushi_awakening 是 Tooltip 需要读取的显示值 ⇒ 唯一登记进 SYNCED_KEYS 的键;
    // 另两键纯服务端判定,一律不 .sync()。三个键都**不** .inMemory()(随玩家 NBT 持久化)。
    // ⚠️ mamushi_awakening 需**跨死亡保留**,1.20.1 没有 copyOnDeath():必须加入
    // component/AstralData#onPlayerClone 死亡分支白名单(对应 1.21.1 的 .copyOnDeath()),
    // 并且作为第 3 槽位进入 component/DeathPreservedBonuses(立牌掉落导致的卸载会先清零)。
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 蛟龙立牌(mamushi)「觉醒」层数(0..{@code MamushiSignItem.AWAKEN_MAX} 之后继续累加,不回落)。
     *
     * <p>**同步**:立牌 tooltip 的计数行({@code tooltip.astral_dice.sign.mamushi_awaken})要读它,
     * 与 {@code fen_recharge} 同一口径 ⇒ 必须进 {@code SYNCED_KEYS}。
     * **跨死亡保留** ⇒ 同时进 {@link AstralData#onPlayerClone} 白名单与
     * {@link DeathPreservedBonuses} 第 3 槽位(两层缺一不可,理由同 {@code rin_pages})。
     */
    public static final AttachedDataKey<Integer> MAMUSHI_AWAKENING =
            register(AttachedDataKey.builder("mamushi_awakening", Codec.INT, () -> 0).sync().build());

    /**
     * 撕咬加成**锁存**(纯服务端):触发骰神赐福当刻装备了撕咬 ⇒ 置 true;赐福结束时清除。
     *
     * <p>为什么必须锁存:撕咬耐久 1,同一击稍后就会被消耗掉;若不锁存,"加成需要撕咬仍在装备中"
     * 会在加成本该生效的那一刻立即失效(规格 §2.6)。
     * 不 {@code .sync()}、不加入 {@code SYNCED_KEYS}(客户端不参与该判定)。
     */
    public static final AttachedDataKey<Boolean> MAMUSHI_BITE_BONUS_ACTIVE =
            register(AttachedDataKey.builder("mamushi_bite_bonus_active", Codec.BOOL, () -> false).build());

    public static int getMamushiAwakening(net.minecraft.world.entity.player.Player player) {
        return MAMUSHI_AWAKENING.get(player);
    }

    public static void setMamushiAwakening(net.minecraft.world.entity.player.Player player, int value) {
        MAMUSHI_AWAKENING.set(player, Math.max(0, value));
    }

    public static boolean getMamushiBiteBonusActive(net.minecraft.world.entity.player.Player player) {
        return MAMUSHI_BITE_BONUS_ACTIVE.get(player);
    }

    public static void setMamushiBiteBonusActive(net.minecraft.world.entity.player.Player player, boolean value) {
        MAMUSHI_BITE_BONUS_ACTIVE.set(player, value);
    }

    /**
     * 怪力侦探立牌(sherry)「推理时间」**层数真值**(0..5)。
     *
     * <p><b>为什么必须进死亡保留名单</b>:用户要求「玩家**死亡不清**推理时间」——
     * 1.21.1 侧是 {@code AttachmentType.Builder#copyOnDeath()} 键;本线对应
     * {@link AstralData#onPlayerClone} 死亡分支白名单 **+** {@link DeathPreservedBonuses}
     * 第 4 槽位(立牌掉落触发的 {@code onUnequip → clearSignData} 会抢先清零,两层缺一不可,
     * 与 {@code mamushi_awakening} 同构)。层数的 HUD 显示走
     * {@code effect/SherryReasoningEffect} 的镜像效果(该效果在死亡时被清空,重生后由
     * {@code SherrySignItem#onCurioTick} 按本键重建)。
     *
     * <p>不 {@code .sync()}、不加入 {@code SYNCED_KEYS}:客户端 tooltip 的层数计数器读的是
     * **已同步的效果实例**(SherryReasoningEffect.getStacks),不读本键。
     */
    public static final AttachedDataKey<Integer> SHERRY_REASONING_LAYERS =
            register(AttachedDataKey.builder("sherry_reasoning_layers", Codec.INT, () -> 0).build());

    public static int getSherryReasoningLayers(net.minecraft.world.entity.player.Player player) {
        return SHERRY_REASONING_LAYERS.get(player);
    }

    public static void setSherryReasoningLayers(net.minecraft.world.entity.player.Player player, int value) {
        SHERRY_REASONING_LAYERS.set(player, Math.max(0, value));
    }

    /**
     * **已推理目标 UUID 集**(逗号分隔) —— 「侦探出击」被动「每个目标只提供 1 层」的唯一判据。
     *
     * <p>「攻击一个**新**目标」= 该 UUID 不在本集中;首次命中即登记,已登记的直接跳过(不再 +1 层)。
     * 写法与寿命都沿用 {@link #FLASHLIGHT_GRANTED_TARGETS} 的字符串集口径:**卸下立牌即清空**。
     * ⚠️ 层数 {@link #SHERRY_REASONING_LAYERS} 本身**跨死亡保留**({@code .copyOnDeath()} / 1.20.1 白名单),
     * 而本记录**有意不跨死亡**(不加 {@code .copyOnDeath()})—— 死亡属重置类事件,重生后同一目标可重新
     * 提供 1 层;口径与手电筒筹码一致(理由见 {@code SherrySignItem#MAX_TRACKED_TARGETS})。
     *
     * <p>不 {@code .sync()}:仅服务端判定,客户端不读。
     */
    public static final AttachedDataKey<String> SHERRY_REASONING_TARGETS =
            register(AttachedDataKey.builder("sherry_reasoning_targets", Codec.STRING, () -> "").build());

    public static String getSherryReasoningTargets(net.minecraft.world.entity.player.Player player) {
        return SHERRY_REASONING_TARGETS.get(player);
    }

    public static void setSherryReasoningTargets(net.minecraft.world.entity.player.Player player, String value) {
        SHERRY_REASONING_TARGETS.set(player, value == null ? "" : value);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  人偶师立牌(hanna,2026-09-21)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 人偶师立牌(hanna)「人偶制作」**层数真值**(0..{@code HannaSignItem.MAX_CRAFT − 1} = 0..6;
     * 达到 7 时由立牌**归零并转为「人偶完成」**,故本键**永不为 7**)。
     *
     * <p>层数的 HUD 显示走 {@code effect/HannaDollCraftEffect} 的镜像效果(层数 = {@code amplifier + 1})。
     * <b>不 {@code .sync()}</b> —— 客户端 tooltip 的层数计数器读的是**已同步的效果实例**,不读本键。
     *
     * <p><b>不跨死亡保留</b>:技能原文未声明死亡保留 ⇒ 按"未声明即不保留"的既定口径处理
     * (照「剑气」「弱点识破」),故**不**进 {@code AstralData#onPlayerClone} 白名单、
     * 也**不**占 {@code DeathPreservedBonuses} 槽位。
     */
    public static final AttachedDataKey<Integer> HANNA_DOLL_CRAFT_LAYERS =
            register(AttachedDataKey.builder("hanna_doll_craft_layers", Codec.INT, () -> 0).build());

    /** 人偶师立牌(hanna)「人偶完成」状态(「人偶制作」满 7 层归零转换而来;卸下立牌时清除)。不 {@code .sync()}。 */
    public static final AttachedDataKey<Boolean> HANNA_DOLL_COMPLETE =
            register(AttachedDataKey.builder("hanna_doll_complete", Codec.BOOL, () -> false).build());

    /** 人偶师立牌(hanna)被动「幻想千金」的 1:00 触发冷却截止刻(绝对 gameTime;骰点 6 与路过共享)。仅服务端。 */
    public static final AttachedDataKey<Long> HANNA_FANTASY_COOLDOWN_END =
            register(AttachedDataKey.builder("hanna_fantasy_cooldown_end", Codec.LONG, () -> 0L).build());

    /** 人偶师立牌(hanna)被动「挚友祝福」的 1:00 触发冷却截止刻(与「幻想千金」各自独立)。仅服务端。 */
    public static final AttachedDataKey<Long> HANNA_BLESSING_COOLDOWN_END =
            register(AttachedDataKey.builder("hanna_blessing_cooldown_end", Codec.LONG, () -> 0L).build());

    public static int getHannaDollCraftLayers(net.minecraft.world.entity.player.Player player) {
        return HANNA_DOLL_CRAFT_LAYERS.get(player);
    }

    public static void setHannaDollCraftLayers(net.minecraft.world.entity.player.Player player, int value) {
        HANNA_DOLL_CRAFT_LAYERS.set(player, Math.max(0, value));
    }

    public static boolean getHannaDollComplete(net.minecraft.world.entity.player.Player player) {
        return HANNA_DOLL_COMPLETE.get(player);
    }

    public static void setHannaDollComplete(net.minecraft.world.entity.player.Player player, boolean value) {
        HANNA_DOLL_COMPLETE.set(player, value);
    }

    public static long getHannaFantasyCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return HANNA_FANTASY_COOLDOWN_END.get(player);
    }

    public static void setHannaFantasyCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        HANNA_FANTASY_COOLDOWN_END.set(player, Math.max(0L, value));
    }

    public static long getHannaBlessingCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return HANNA_BLESSING_COOLDOWN_END.get(player);
    }

    public static void setHannaBlessingCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        HANNA_BLESSING_COOLDOWN_END.set(player, Math.max(0L, value));
    }

    /**
     * 「飞星」筹码(紫色飞星 / 金色飞星)的 **共享**触发冷却截止刻(绝对 gameTime)。
     * <p>用户裁决:两枚筹码同时装备时共用同一计时器 ⇒ 只此一键。该计时器**不创建任何效果**,
     * 仅在 tooltip 中显示剩余秒数。仅服务端。
     */
    public static final AttachedDataKey<Long> SHOOTING_STAR_COOLDOWN_END =
            register(AttachedDataKey.builder("shooting_star_cooldown_end", Codec.LONG, () -> 0L).build());

    public static long getShootingStarCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return SHOOTING_STAR_COOLDOWN_END.get(player);
    }

    public static void setShootingStarCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        SHOOTING_STAR_COOLDOWN_END.set(player, Math.max(0L, value));
    }

    /**
     * 机械师立牌 megas 被动「物资补充」的下一次发放基准刻（绝对 gameTime，long）。
     *
     * <p>手牌 &lt; 6 张时每 1:00（1200 tick）自动获得 1 张随机卡牌；本键记录「下一次允许发放」的
     * 绝对刻，由 {@code MegasSignItem#onCurioTick} 每 tick 判定到期并推进。仅服务端使用，
     * <b>不 sync</b>（客户端无需预检，tooltip 不读它）。
     */
    public static final AttachedDataKey<Long> MEGAS_RESUPPLY_NEXT_TICK =
            register(AttachedDataKey.builder("megas_resupply_next_tick", Codec.LONG, () -> 0L).build());

    public static long getMegasResupplyNextTick(net.minecraft.world.entity.player.Player player) {
        return MEGAS_RESUPPLY_NEXT_TICK.get(player);
    }

    public static void setMegasResupplyNextTick(net.minecraft.world.entity.player.Player player, long value) {
        MEGAS_RESUPPLY_NEXT_TICK.set(player, Math.max(0L, value));
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
            // 立牌玩家级状态复合键(B2):客户端要读的 cooldown_end 已折叠进它 ⇒ 必须进登录快照。
            SYNCED_KEYS.add(SIGN_STATE);
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
            SYNCED_KEYS.add(AIRBAG_COOLDOWN_END);
            SYNCED_KEYS.add(RAILGUN_COOLDOWN_END);
            SYNCED_KEYS.add(RIN_PAGES);
            SYNCED_KEYS.add(PANDAMAN_MAX_HEALTH_BONUS);
            SYNCED_KEYS.add(NANCY_LU_PASSIVE_TYPE);
            // 蛟龙立牌(mamushi):仅觉醒层数需客户端可见(tooltip 计数行);另两键纯服务端
            SYNCED_KEYS.add(MAMUSHI_AWAKENING);
            // 超天酱/地雷女立牌(kangel/ame,2026-10-16):三个计数器行真值需客户端可见;
            // 另两键(kangel_fan_targets / ame_love_gain_cd_end)纯服务端,不登记。
            SYNCED_KEYS.add(KANGEL_FAN_COUNT);
            SYNCED_KEYS.add(AME_LOVE_LAYERS);
            SYNCED_KEYS.add(AME_LOVE_CAP);
            // 商店冷却 / 抽奖盒冷却(Jade 悬浮框倒计时):方块无 BlockEntity ⇒ 1.20.1 的 Jade
            // 只能读同步值,故这三个键必须进登录快照(否则重登后冷却中也不显示该行)。
            SYNCED_KEYS.add(SHOP_TRANSFER_COOLDOWN_END);
            SYNCED_KEYS.add(CHIP_SHOP_STATE);
            SYNCED_KEYS.add(EVENT_DRAWER_COOLDOWN_UNTIL);
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
     * 「白泽赐福」的 **2 分钟倒计时是否已启动**(2026-09-27 用户裁决新增)。
     *
     * <p><b>需求</b>:白泽赐福/降神不再绑定骰神赐福;改为固定时长 <b>2:00</b>,但**计时必须等
     * 「被施加者实施一次合格的近战攻击」之后才启动** —— 施加瞬间效果**立即生效**(溢出治疗转攻击力等
     * 全部照常)，只是**还没开始倒计时**。
     *
     * <p><b>为什么单独用本键而不是读效果剩余时长</b>:未启动期间效果的时长被写成
     * {@code -1}(原版 {@code isInfiniteDuration()} 的判据，见 {@code MobEffectInstance}：
     * 该值下 {@code tickDownDuration} 直接跳过 ⇒ 永不走动)，而"已启动但刚好被 tick 走到 0"也会是
     * 0/负数 —— 两种状态在时长字段上无法区分，故必须有一个显式真值。
     *
     * <p>写入时机:施加时置 {@code false}；被施加者首次满足条件(主手近战武器 + 目标是骰神赐福合法目标)
     * 的近战攻击那一刻置 {@code true}，并**只置一次**(之后不再重置 ⇒ 计时不因再次攻击而回满)。
     * 仅服务端使用,故不 {@code .sync()}(与 1.21.1 侧同名同型)。
     */
    public static final AttachedDataKey<Boolean> ZHAO_BLESSING_TIMER_STARTED =
            register(AttachedDataKey.builder("zhao_blessing_timer_started", Codec.BOOL, () -> false).build());

    public static boolean isZhaoBlessingTimerStarted(net.minecraft.world.entity.player.Player player) {
        return ZHAO_BLESSING_TIMER_STARTED.get(player);
    }

    public static void setZhaoBlessingTimerStarted(net.minecraft.world.entity.player.Player player, boolean value) {
        ZHAO_BLESSING_TIMER_STARTED.set(player, value);
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
     * <p>不 {@code .sync()}:同步会让每 tick 都写包(同口径先例见下方 {@code zhao_prev_blessing})。
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

    // ═══════════════════════════════════════════════════════════════════════════
    // 教主立牌(teru):主动「降神」+ 被动「狐光」(2026-09-27)
    //
    // 与 1.21.1 侧**同名同型、同语义**(先例见上方「白泽赐福」段):真值全部在**被指定目标**身上,
    // 施法者侧只有「狐光层数」「当前目标指针」「攻击加成镜像缓存」三个派生/持有值。
    // 全部键仅服务端使用(可见载体是效果实例,客户端由原生效果同步)⇒ 一律不 {@code .sync()}、
    // 不加入 {@link #SYNCED_KEYS}。
    // ⚠️ {@code teru_huguang_layers} 与 {@code teru_equip_watermark} 需**跨死亡保留**,1.20.1 侧
    // 没有 {@code copyOnDeath()}:必须加入 {@code component/AstralData#onPlayerClone} 死亡分支的白名单
    // (对应 1.21.1 的 {@code .copyOnDeath()})。
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 降神**施加者**的 UUID(挂在被指定目标身上)= 降神是否生效的**唯一真值**(非空即生效中)。
     * 目标死亡/登出/重登一律走 {@code TeruSignItem#endDescent} 收敛。
     */
    public static final AttachedDataKey<Optional<UUID>> TERU_DESCENT_CASTER =
            register(AttachedDataKey.builder("teru_descent_caster",
                    UUIDUtil.CODEC.optionalFieldOf("id").codec(), Optional::empty).build());

    public static Optional<UUID> getTeruDescentCaster(net.minecraft.world.entity.player.Player player) {
        return TERU_DESCENT_CASTER.get(player);
    }

    public static void setTeruDescentCaster(net.minecraft.world.entity.player.Player player, Optional<UUID> value) {
        TERU_DESCENT_CASTER.set(player, value);
    }

    /** 降神给施法者的攻击力加成快照 = ⌊目标攻击力 × 50%⌋(施法瞬间锁定) */
    public static final AttachedDataKey<Integer> TERU_DESCENT_ATK_BONUS =
            register(AttachedDataKey.builder("teru_descent_atk_bonus", Codec.INT, () -> 0).build());

    public static int getTeruDescentAtkBonus(net.minecraft.world.entity.player.Player player) {
        return TERU_DESCENT_ATK_BONUS.get(player);
    }

    public static void setTeruDescentAtkBonus(net.minecraft.world.entity.player.Player player, int value) {
        TERU_DESCENT_ATK_BONUS.set(player, Math.max(0, value));
    }

    /** 降神给施法者的防御力加成快照 = ⌊目标防御力 × 50%⌋(消费点 = setDefenseArmorBonus,1 防御 = 2 护甲) */
    public static final AttachedDataKey<Integer> TERU_DESCENT_DEF_BONUS =
            register(AttachedDataKey.builder("teru_descent_def_bonus", Codec.INT, () -> 0).build());

    public static int getTeruDescentDefBonus(net.minecraft.world.entity.player.Player player) {
        return TERU_DESCENT_DEF_BONUS.get(player);
    }

    public static void setTeruDescentDefBonus(net.minecraft.world.entity.player.Player player, int value) {
        TERU_DESCENT_DEF_BONUS.set(player, Math.max(0, value));
    }

    /**
     * **狐光攻击基数** {@code B = 施加时的施法者攻击力(基础) + ⌊目标攻击力 × 50%⌋}（= 施法者「获得目标 50% 加成后」的快照攻击力;施法瞬间快照）。
     * 目标攻击新目标时的额外攻击 = {@code B + 消耗 1 层后剩余狐光层数}(计入骰战攻击力)。
     */
    public static final AttachedDataKey<Integer> TERU_DESCENT_ATTACK_BASE =
            register(AttachedDataKey.builder("teru_descent_attack_base", Codec.INT, () -> 0).build());

    public static int getTeruDescentAttackBase(net.minecraft.world.entity.player.Player player) {
        return TERU_DESCENT_ATTACK_BASE.get(player);
    }

    public static void setTeruDescentAttackBase(net.minecraft.world.entity.player.Player player, int value) {
        TERU_DESCENT_ATTACK_BASE.set(player, Math.max(0, value));
    }

    /** 降神待跳过的骰神赐福结束次数(0/1;语义与 {@link #ZHAO_BLESSING_SKIP_CYCLES} 逐字相同) */
    public static final AttachedDataKey<Integer> TERU_DESCENT_SKIP_CYCLES =
            register(AttachedDataKey.builder("teru_descent_skip_cycles", Codec.INT, () -> 0).build());

    public static int getTeruDescentSkipCycles(net.minecraft.world.entity.player.Player player) {
        return TERU_DESCENT_SKIP_CYCLES.get(player);
    }

    public static void setTeruDescentSkipCycles(net.minecraft.world.entity.player.Player player, int value) {
        TERU_DESCENT_SKIP_CYCLES.set(player, Math.max(0, value));
    }

    /** 上一 tick 被指定目标是否处于骰神赐福(下降沿检测专用;仅服务端,每 tick 读写) */
    public static final AttachedDataKey<Boolean> TERU_PREV_BLESSING =
            register(AttachedDataKey.builder("teru_prev_blessing", Codec.BOOL, () -> false).build());

    public static boolean isTeruPrevBlessing(net.minecraft.world.entity.player.Player player) {
        return TERU_PREV_BLESSING.get(player);
    }

    public static void setTeruPrevBlessing(net.minecraft.world.entity.player.Player player, boolean value) {
        TERU_PREV_BLESSING.set(player, value);
    }

    /**
     * 「降神」的 **2 分钟倒计时是否已启动**(2026-09-27 用户裁决新增;挂在**被指定目标**身上)。
     *
     * <p>语义与 {@link #ZHAO_BLESSING_TIMER_STARTED} 逐字相同(同一批裁决):效果施加瞬间即生效,
     * 但倒计时必须等**被施加者首次实施合格近战攻击**后才启动，且**只启动一次**。
     * 仅服务端使用,故不 {@code .sync()}。
     */
    public static final AttachedDataKey<Boolean> TERU_DESCENT_TIMER_STARTED =
            register(AttachedDataKey.builder("teru_descent_timer_started", Codec.BOOL, () -> false).build());

    public static boolean isTeruDescentTimerStarted(net.minecraft.world.entity.player.Player player) {
        return TERU_DESCENT_TIMER_STARTED.get(player);
    }

    public static void setTeruDescentTimerStarted(net.minecraft.world.entity.player.Player player, boolean value) {
        TERU_DESCENT_TIMER_STARTED.set(player, value);
    }

    /** 本次降神期间已被该目标攻击过的目标 UUID 集(逗号分隔;效果结束时清空) */
    public static final AttachedDataKey<String> TERU_DESCENT_NEW_TARGETS =
            register(AttachedDataKey.builder("teru_descent_new_targets", Codec.STRING, () -> "").build());

    public static String getTeruDescentNewTargets(net.minecraft.world.entity.player.Player player) {
        return TERU_DESCENT_NEW_TARGETS.get(player);
    }

    public static void setTeruDescentNewTargets(net.minecraft.world.entity.player.Player player, String value) {
        TERU_DESCENT_NEW_TARGETS.set(player, value == null ? "" : value);
    }

    /**
     * 狐光层数(0..{@code TeruSignItem.MAX_HUGUANG} = 20;持有者 = 施法者自身)。
     * **跨死亡保留** ⇒ 必须进 {@code AstralData#onPlayerClone} 死亡白名单。
     */
    public static final AttachedDataKey<Integer> TERU_HUGUANG_LAYERS =
            register(AttachedDataKey.builder("teru_huguang_layers", Codec.INT, () -> 0).build());

    public static int getTeruHuguangLayers(net.minecraft.world.entity.player.Player player) {
        return TERU_HUGUANG_LAYERS.get(player);
    }

    public static void setTeruHuguangLayers(net.minecraft.world.entity.player.Player player, int value) {
        TERU_HUGUANG_LAYERS.set(player, Math.max(0, value));
    }

    /** 施法者侧当前降神目标指针(拒绝重复施放的判据;失效可由玩家级 tick 扫描在线玩家自愈重建) */
    public static final AttachedDataKey<Optional<UUID>> TERU_DESCENT_TARGET =
            register(AttachedDataKey.builder("teru_descent_target",
                    UUIDUtil.CODEC.optionalFieldOf("id").codec(), Optional::empty).build());

    public static Optional<UUID> getTeruDescentTarget(net.minecraft.world.entity.player.Player player) {
        return TERU_DESCENT_TARGET.get(player);
    }

    public static void setTeruDescentTarget(net.minecraft.world.entity.player.Player player, Optional<UUID> value) {
        TERU_DESCENT_TARGET.set(player, value);
    }

    /** 施法者侧攻击力加成**镜像缓存**(每 tick 由目标记录派生写入;攻击修饰器唯一读取方) */
    public static final AttachedDataKey<Integer> TERU_ATK_BONUS_CACHE =
            register(AttachedDataKey.builder("teru_atk_bonus_cache", Codec.INT, () -> 0).build());

    public static int getTeruAtkBonusCache(net.minecraft.world.entity.player.Player player) {
        return TERU_ATK_BONUS_CACHE.get(player);
    }

    public static void setTeruAtkBonusCache(net.minecraft.world.entity.player.Player player, int value) {
        TERU_ATK_BONUS_CACHE.set(player, Math.max(0, value));
    }

    /**
     * **装备计层防刷水位**(挂在行为者身上):`类型=历史最大同时装备张数;…`(只升不降)。
     *
     * <p>为什么去重落在玩家侧:{@code screen/CardInventoryMenu#saveToDice} 只把卡牌写成
     * {@code AppliedStone(type, uses)} 并销毁物品栈,卸除时由 {@code loadFromDice} 经
     * {@code CardRegistry.typeToItem} **重建全新 ItemStack** ⇒ 任何物品级标记都会被抹掉。
     * **跨死亡保留**(否则"死一次再装备一次"可刷层)⇒ 同样进 {@code AstralData#onPlayerClone} 白名单。
     */
    public static final AttachedDataKey<String> TERU_EQUIP_WATERMARK =
            register(AttachedDataKey.builder("teru_equip_watermark", Codec.STRING, () -> "").build());

    public static String getTeruEquipWatermark(net.minecraft.world.entity.player.Player player) {
        return TERU_EQUIP_WATERMARK.get(player);
    }

    public static void setTeruEquipWatermark(net.minecraft.world.entity.player.Player player, String value) {
        TERU_EQUIP_WATERMARK.set(player, value == null ? "" : value);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  超天酱立牌(kangel)+ 地雷女立牌(ame)(2026-10-16 冻结规格)
    //
    //  与 1.21.1 侧**同名同型、同语义**(先例见上方「怪力侦探立牌 sherry」段):
    //  ❗ kangel_fan_count / ame_love_layers / ame_love_cap 三个键需**跨死亡保留**
    //     —— 1.21.1 是 AttachmentType.Builder#copyOnDeath(),本线没有该方法,必须同时加入
    //     (a) {@link AstralData#onPlayerClone} 死亡分支白名单,且 (b) {@link DeathPreservedBonuses}
    //     暂存/回写表(死亡时立牌从饰品槽掉出会先触发 onUnequip → clearSignData 清零,**早于**克隆复制;
    //     两层缺一不可,理由同 rin_pages / mamushi_awakening)。kangel_fan_targets 同为 copyOnDeath 键
    //     (用户原话「粉丝数永久有效,且死亡后不会清空」⇒ 目标集必须与计数同进同退),故一并进白名单 +
    //     字符串并行表。
    //  ❗ kangel_fan_count / ame_love_layers / ame_love_cap 需**同步**(客户端 tooltip 的计数器行读它们)
    //     ⇒ 三个键进 SYNCED_KEYS;另两键(目标集 / 30 秒闸门)纯服务端判定,不 .sync()。
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 超天酱立牌(kangel)被动「小天使请安」的**粉丝数真值**(0..10,上限见
     * {@code KangelSignItem#MAX_FANS})。
     *
     * <p><b>必须跨死亡保留</b>:用户要求「粉丝数永久有效,且死亡后不会清空。即使目标被击杀,
     * 其拥有的粉丝数一样保留」(玩家拆卸立牌后重置为 0)。目标被击杀**不**减少计数
     * (计数只随「新目标首次登记」增长)。
     *
     * <p><b>需同步</b>:客户端 tooltip 的计数器行读它(与 sherry 推理时间「读已同步效果」的口径不同
     * —— 本立牌同时把粉丝数镜像成 {@code kangel_fan} 效果图标,但**计数器行**取附件真值,
     * 由 {@code ModTooltipHandler} 在客户端直接读)⇒ 必须进 {@code SYNCED_KEYS}。
     */
    public static final AttachedDataKey<Integer> KANGEL_FAN_COUNT =
            register(AttachedDataKey.builder("kangel_fan_count", Codec.INT, () -> 0).sync().build());

    public static int getKangelFanCount(net.minecraft.world.entity.player.Player player) {
        return KANGEL_FAN_COUNT.get(player);
    }

    public static void setKangelFanCount(net.minecraft.world.entity.player.Player player, int value) {
        KANGEL_FAN_COUNT.set(player, Math.max(0, value));
    }

    /**
     * **已登记目标 UUID 集**(逗号分隔) —— 「每个目标对每个超天酱玩家只计 1 个粉丝」的唯一判据。
     *
     * <p>写法与寿命都沿用 {@link #SHERRY_REASONING_TARGETS} 的字符串集口径(超
     * {@code KangelSignItem#MAX_TRACKED_TARGETS} 按最旧淘汰);**卸下立牌即清空**。
     * <b>跨死亡保留</b>(用户原话「粉丝数永久有效,且死亡后不会清空」⇒ 与计数同寿命,
     * 否则重生后计数保留而目标集清空,同一目标会被重复计成新粉丝)。
     * 不 {@code .sync()}、不进 {@code SYNCED_KEYS}:仅服务端判定,客户端不读。
     */
    public static final AttachedDataKey<String> KANGEL_FAN_TARGETS =
            register(AttachedDataKey.builder("kangel_fan_targets", Codec.STRING, () -> "").build());

    public static String getKangelFanTargets(net.minecraft.world.entity.player.Player player) {
        return KANGEL_FAN_TARGETS.get(player);
    }

    public static void setKangelFanTargets(net.minecraft.world.entity.player.Player player, String value) {
        KANGEL_FAN_TARGETS.set(player, value == null ? "" : value);
    }

    /**
     * 地雷女立牌(ame)「爱」**层数真值**(0..上限)。**跨死亡保留**(用户原话「爱层数死亡后不会清空」);
     * **卸下清零**(见 {@code AmeSignItem#clearSignData})。层数的 HUD 显示走
     * {@code effect/AmeLoveEffect} 的镜像效果(层数 = {@code amplifier + 1},无限时长)。
     * <b>需同步</b>:客户端 tooltip 的「爱：当前/上限」计数器行读它 ⇒ 进 {@code SYNCED_KEYS}。
     */
    public static final AttachedDataKey<Integer> AME_LOVE_LAYERS =
            register(AttachedDataKey.builder("ame_love_layers", Codec.INT, () -> 0).sync().build());

    public static int getAmeLoveLayers(net.minecraft.world.entity.player.Player player) {
        return AME_LOVE_LAYERS.get(player);
    }

    public static void setAmeLoveLayers(net.minecraft.world.entity.player.Player player, int value) {
        AME_LOVE_LAYERS.set(player, Math.max(0, value));
    }

    /**
     * 地雷女立牌(ame)「爱」的**层数上限**(默认 {@code AmeSignItem#AME_LOVE_CAP_DEFAULT} = 4;
     * 主动「感情过量摄入」自然到期后 +1,最高 {@code AmeSignItem#AME_LOVE_CAP_MAX} = 32)。
     * **跨死亡保留**;**卸下清零**回默认值。需同步(同 {@link #AME_LOVE_LAYERS} 的计数器行)。
     */
    public static final AttachedDataKey<Integer> AME_LOVE_CAP =
            register(AttachedDataKey.builder("ame_love_cap", Codec.INT, () -> 4).sync().build());

    public static int getAmeLoveCap(net.minecraft.world.entity.player.Player player) {
        return AME_LOVE_CAP.get(player);
    }

    public static void setAmeLoveCap(net.minecraft.world.entity.player.Player player, int value) {
        AME_LOVE_CAP.set(player, Math.max(1, value));
    }

    /**
     * 地雷女立牌(ame)「爱」获得闸门的**下次可获层刻**(绝对 gameTime;0 = 无闸门)。
     * 每 {@code AmeSignItem#LOVE_GAIN_INTERVAL_TICKS}(= 30 秒)最多 1 层,
     * **该闸门同时约束一心同体供给方送入的层数**。不跨死亡(重生后闸门归零 ⇒ 立刻可再获 1 层);
     * 不 {@code .sync()}、不进 {@code SYNCED_KEYS}(仅服务端判定,客户端不读)。
     */
    public static final AttachedDataKey<Long> AME_LOVE_GAIN_CD_END =
            register(AttachedDataKey.builder("ame_love_gain_cd_end", Codec.LONG, () -> 0L).build());

    public static long getAmeLoveGainCdEnd(net.minecraft.world.entity.player.Player player) {
        return AME_LOVE_GAIN_CD_END.get(player);
    }

    public static void setAmeLoveGainCdEnd(net.minecraft.world.entity.player.Player player, long value) {
        AME_LOVE_GAIN_CD_END.set(player, Math.max(0L, value));
    }

    private ModAttachments() {
    }

    // ── 本批 4 个新立牌的玩家级状态（键原由 NewSignAttachments 内联至此，id/默认值/同步标记逐字不变）──
    /** 调酒师：目标选择确认后暂存的目标 UUID（空串 = 无）。选卡取消即清空。 */
    public static final AttachedDataKey<String> JILL_PENDING_TARGET =
            register(AttachedDataKey.builder("jill_pending_target", Codec.STRING, () -> "").build());

    /** 调酒师：主动 buff 受惠目标 UUID（空串 = 无；到期/卸下清除）。 */
    public static final AttachedDataKey<String> JILL_BUFF_TARGET =
            register(AttachedDataKey.builder("jill_buff_target", Codec.STRING, () -> "").build());

    /** 调酒师：主动 buff 到期刻（绝对 gameTime；0 = 无）。 */
    public static final AttachedDataKey<Long> JILL_BUFF_END =
            register(AttachedDataKey.builder("jill_buff_end", Codec.LONG, () -> 0L).build());

    /** 多萝西：主动窗口结束刻（0 = 无窗口）。 */
    public static final AttachedDataKey<Long> DOROTHY_WINDOW_END =
            register(AttachedDataKey.builder("dorothy_window_end", Codec.LONG, () -> 0L).sync().build());

    /** 多萝西：窗口内已触发过的友方玩家 UUID 与最近触发刻（"uuid=tick" 逗号分隔；10 秒节流）。 */
    public static final AttachedDataKey<String> DOROTHY_PARTNERS =
            register(AttachedDataKey.builder("dorothy_partners", Codec.STRING, () -> "").build());

    /** 多萝西：窗口内已结算「每人一次」的友方玩家 UUID（"uuid,uuid" 逗号分隔；窗口重置时与节流映射一并清零）。 */
    public static final AttachedDataKey<String> DOROTHY_PARTNERS_ONCE =
            register(AttachedDataKey.builder("dorothy_partners_once", Codec.STRING, () -> "").build());

    /** 多萝西：当前攻击加成总量（窗口内每人 1 点 + 满层转化值）。 */
    public static final AttachedDataKey<Integer> DOROTHY_ATTACK_BONUS =
            register(AttachedDataKey.builder("dorothy_attack_bonus", Codec.INT, () -> 0).sync().build());

    /** 多萝西：被动回血的冷却结束刻。 */
    public static final AttachedDataKey<Long> DOROTHY_HEAL_COOLDOWN_END =
            register(AttachedDataKey.builder("dorothy_heal_cooldown_end", Codec.LONG, () -> 0L).build());

    /** 修女：被动累计攻击加成。 */
    public static final AttachedDataKey<Integer> ALANA_PASSIVE_BONUS =
            register(AttachedDataKey.builder("alana_passive_bonus", Codec.INT, () -> 0).sync().build());

    /** 大肥鱼：静止锚点（BlockPos.asLong；0 = 未锚定）。 */
    public static final AttachedDataKey<Long> DEEPSEEK_ANCHOR =
            register(AttachedDataKey.builder("deepseek_anchor", Codec.LONG, () -> 0L).build());

    /** 大肥鱼：静止起始刻。 */
    public static final AttachedDataKey<Long> DEEPSEEK_IDLE_SINCE =
            register(AttachedDataKey.builder("deepseek_idle_since", Codec.LONG, () -> 0L).build());

    public static String getJillPendingTarget(net.minecraft.world.entity.player.Player player) {
        return JILL_PENDING_TARGET.get(player);
    }

    public static void setJillPendingTarget(net.minecraft.world.entity.player.Player player, String value) {
        JILL_PENDING_TARGET.set(player, value == null ? "" : value);
    }

    public static String getJillBuffTarget(net.minecraft.world.entity.player.Player player) {
        return JILL_BUFF_TARGET.get(player);
    }

    public static void setJillBuffTarget(net.minecraft.world.entity.player.Player player, String value) {
        JILL_BUFF_TARGET.set(player, value == null ? "" : value);
    }

    public static long getJillBuffEnd(net.minecraft.world.entity.player.Player player) {
        return JILL_BUFF_END.get(player);
    }

    public static void setJillBuffEnd(net.minecraft.world.entity.player.Player player, long value) {
        JILL_BUFF_END.set(player, value);
    }

    public static long getDorothyWindowEnd(net.minecraft.world.entity.player.Player player) {
        return DOROTHY_WINDOW_END.get(player);
    }

    public static void setDorothyWindowEnd(net.minecraft.world.entity.player.Player player, long value) {
        DOROTHY_WINDOW_END.set(player, value);
    }

    public static int getDorothyAttackBonus(net.minecraft.world.entity.player.Player player) {
        return DOROTHY_ATTACK_BONUS.get(player);
    }

    public static void setDorothyAttackBonus(net.minecraft.world.entity.player.Player player, int value) {
        DOROTHY_ATTACK_BONUS.set(player, Math.max(0, value));
    }

    public static void addDorothyAttackBonus(net.minecraft.world.entity.player.Player player, int delta) {
        setDorothyAttackBonus(player, getDorothyAttackBonus(player) + delta);
    }

    public static long getDorothyHealCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return DOROTHY_HEAL_COOLDOWN_END.get(player);
    }

    public static void setDorothyHealCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        DOROTHY_HEAL_COOLDOWN_END.set(player, value);
    }

    public static void clearDorothyPartners(net.minecraft.world.entity.player.Player player) {
        DOROTHY_PARTNERS.set(player, "");
        DOROTHY_PARTNERS_ONCE.set(player, "");
    }

    /** 返回 true 表示该友方玩家本次可结算：节流(距上次触发 ≥ throttleTicks)与每人一次(本窗口未结算过)同时成立。 */
    public static boolean tryTriggerDorothyPartner(net.minecraft.world.entity.player.Player player, UUID other, long now, long throttleTicks) {
        String raw = DOROTHY_PARTNERS.get(player);
        java.util.Map<String, Long> seen = new java.util.LinkedHashMap<>();
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split(",")) {
                if (part.isEmpty()) continue;
                int eq = part.indexOf('=');
                String key = eq >= 0 ? part.substring(0, eq) : part;
                long last = 0L;
                if (eq >= 0) {
                    try {
                        last = Long.parseLong(part.substring(eq + 1));
                    } catch (NumberFormatException ignored) {
                    }
                }
                seen.put(key, last);
            }
        }
        String key = other.toString();
        // ① 每人一次：本窗口已结算过该队友 ⇒ 拒绝（窗口重置时 clearDorothyPartners 一并清零）
        String onceRaw = DOROTHY_PARTNERS_ONCE.get(player);
        java.util.Set<String> once = new java.util.LinkedHashSet<>();
        if (onceRaw != null && !onceRaw.isEmpty()) {
            for (String part : onceRaw.split(",")) {
                if (!part.isEmpty()) once.add(part);
            }
        }
        if (once.contains(key)) return false;
        // ② 节流：距上次触发不足 throttleTicks ⇒ 拒绝
        Long prev = seen.get(key);
        if (prev != null && now - prev < throttleTicks) return false;
        // 两条闸门都通过：记入「每人一次」标记 + 更新节流时间
        once.add(key);
        StringBuilder onceSb = new StringBuilder();
        for (String p : once) {
            if (onceSb.length() > 0) onceSb.append(',');
            onceSb.append(p);
        }
        DOROTHY_PARTNERS_ONCE.set(player, onceSb.toString());
        seen.put(key, now);
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Long> e : seen.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        DOROTHY_PARTNERS.set(player, sb.toString());
        return true;
    }

    /** 当前窗口内已触发过的友方玩家 UUID 集合(用于窗口结束/卸下时扫除队友加成)。 */
    public static Set<UUID> dorothyPartnerUuids(net.minecraft.world.entity.player.Player player) {
        Set<UUID> out = new HashSet<>();
        String raw = DOROTHY_PARTNERS.get(player);
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(",")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            String key = eq >= 0 ? part.substring(0, eq) : part;
            try {
                out.add(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    public static int getAlanaPassiveBonus(net.minecraft.world.entity.player.Player player) {
        return ALANA_PASSIVE_BONUS.get(player);
    }

    public static void setAlanaPassiveBonus(net.minecraft.world.entity.player.Player player, int value) {
        ALANA_PASSIVE_BONUS.set(player, Math.max(0, value));
    }

    public static long getDeepseekAnchor(net.minecraft.world.entity.player.Player player) {
        return DEEPSEEK_ANCHOR.get(player);
    }

    public static void setDeepseekAnchor(net.minecraft.world.entity.player.Player player, long value) {
        DEEPSEEK_ANCHOR.set(player, value);
    }

    public static long getDeepseekIdleSince(net.minecraft.world.entity.player.Player player) {
        return DEEPSEEK_IDLE_SINCE.get(player);
    }

    public static void setDeepseekIdleSince(net.minecraft.world.entity.player.Player player, long value) {
        DEEPSEEK_IDLE_SINCE.set(player, value);
    }
}
