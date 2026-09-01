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

    // 忍者立牌(komachi)主动:效果牌出牌数+1 累积银行(按实际出牌消耗;跨周期保留至用尽,
    // 不受出牌进度/冷却/满额影响,确保主动技能在任何情况下均生效)
    public static final AttachedDataKey<Integer> KOMACHI_EXTRA_PLAYS =
            register(AttachedDataKey.builder("komachi_extra_plays", Codec.INT, () -> 0).sync().build());

    public static int getKomachiExtraPlays(net.minecraft.world.entity.player.Player player) {
        return KOMACHI_EXTRA_PLAYS.get(player);
    }

    public static void setKomachiExtraPlays(net.minecraft.world.entity.player.Player player, int value) {
        KOMACHI_EXTRA_PLAYS.set(player, value);
    }

    // 效果牌公共冷却结束时刻(-1 表示待定冷却=伤害效果牌效果等待中;0 表示无)
    public static final AttachedDataKey<Long> EFFECT_CARD_COOLDOWN_END =
            register(AttachedDataKey.builder("effect_card_cooldown_end", Codec.LONG, () -> 0L).sync().build());

    // 事件系统:护甲 -30% 惩罚结束时刻(0 表示未生效)
    public static final AttachedDataKey<Long> ARMOR_PENALTY_END =
            register(AttachedDataKey.builder("armor_penalty_end", Codec.LONG, () -> 0L).build());

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
            register(AttachedDataKey.builder("magic_tome_use_count", Codec.INT, () -> 0).build());

    // 魔法秘典筹码:最后一张使用的效果牌类型(king_power/berserk/unwavering)
    public static final AttachedDataKey<String> MAGIC_TOME_LAST_CARD =
            register(AttachedDataKey.builder("magic_tome_last_card", Codec.STRING, () -> "").build());

    // 忍者立牌(komachi):效果牌使用计数(独立于魔法秘典与周期计数,每使用 3 张复制最后一张)
    public static final AttachedDataKey<Integer> KOMACHI_USE_COUNT =
            register(AttachedDataKey.builder("komachi_use_count", Codec.INT, () -> 0).build());

    // 忍者立牌(komachi):最后一张使用的效果牌类型
    public static final AttachedDataKey<String> KOMACHI_LAST_CARD =
            register(AttachedDataKey.builder("komachi_last_card", Codec.STRING, () -> "").build());

    // 忍者立牌(komachi):效果牌伤害增益(每使用 3 张效果牌 +1,上限见 GameplayConstants.KOMACHI_DAMAGE_BONUS_MAX)
    public static final AttachedDataKey<Integer> KOMACHI_DAMAGE_BONUS =
            register(AttachedDataKey.builder("komachi_damage_bonus", Codec.INT, () -> 0).sync().build());

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

    // 调查员立牌(rin):已使用的活体书页数量(活体书页伤害永久+1 的来源,移除立牌后重置)
    public static final AttachedDataKey<Integer> RIN_PAGES =
            register(AttachedDataKey.builder("rin_pages", Codec.INT, () -> 0).build());

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

    // 立牌主动技能"等待目标释放"状态类型:1=占星师(虚弱印记) 2=秘密侦探(隐匿调查);0=无等待
    public static final AttachedDataKey<Integer> SIGN_READY_TYPE =
            register(AttachedDataKey.builder("sign_ready_type", Codec.INT, () -> 0).sync().build());

    // 立牌主动技能等待到期时刻(0 表示无等待)
    public static final AttachedDataKey<Long> SIGN_READY_EXPIRE =
            register(AttachedDataKey.builder("sign_ready_expire", Codec.LONG, () -> 0L).sync().build());

    public static int getSignReadyType(net.minecraft.world.entity.player.Player player) {
        return SIGN_READY_TYPE.get(player);
    }

    public static void setSignReadyType(net.minecraft.world.entity.player.Player player, int value) {
        SIGN_READY_TYPE.set(player, value);
    }

    public static long getSignReadyExpire(net.minecraft.world.entity.player.Player player) {
        return SIGN_READY_EXPIRE.get(player);
    }

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

    public static long getArmorPenaltyEnd(net.minecraft.world.entity.player.Player player) {
        return ARMOR_PENALTY_END.get(player);
    }

    public static void setArmorPenaltyEnd(net.minecraft.world.entity.player.Player player, long value) {
        ARMOR_PENALTY_END.set(player, value);
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

    // 诅咒之剑筹码:累计击杀 20 血以上敌对目标获得的攻击力加成(移除筹码/死亡清除)
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
            register(AttachedDataKey.builder("nancy_lu_passive_type", Codec.INT, () -> 0).build());

    // 骇客立牌:主动"远程骇入"攻击力加成数值
    public static final AttachedDataKey<Integer> NANCY_LU_ACTIVE_BONUS =
            register(AttachedDataKey.builder("nancy_lu_active_bonus", Codec.INT, () -> 0).sync().build());

    // 骇客立牌:主动攻击力加成结束时刻
    public static final AttachedDataKey<Long> NANCY_LU_ACTIVE_BONUS_UNTIL =
            register(AttachedDataKey.builder("nancy_lu_active_bonus_until", Codec.LONG, () -> 0L).build());

    // 骇客立牌:主动无敌结束时刻
    public static final AttachedDataKey<Long> NANCY_LU_INVULNERABLE_UNTIL =
            register(AttachedDataKey.builder("nancy_lu_invulnerable_until", Codec.LONG, () -> 0L).build());

    // 骇客立牌:主动"完全隐身"结束时刻
    public static final AttachedDataKey<Long> NANCY_LU_HIDDEN_UNTIL =
            register(AttachedDataKey.builder("nancy_lu_hidden_until", Codec.LONG, () -> 0L).build());

    // 看板立牌:被动"主动技能返还"累计的战斗牌数量(每累计 25 张返还战斗牌获得一个随机筹码)
    public static final AttachedDataKey<Integer> MIMI_RETURNED_CARD_COUNT =
            register(AttachedDataKey.builder("mimi_returned_card_count", Codec.INT, () -> 0).build());

    // 夹心饼干-美味筹码:低生命值反击被动的触发冷却结束时刻(每 1:00 至多获得 1 层反击;0 表示无冷却)
    public static final AttachedDataKey<Long> SANDWICH_HIGH_COUNTER_COOLDOWN_END =
            register(AttachedDataKey.builder("sandwich_high_counter_cooldown_end", Codec.LONG, () -> 0L).build());

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

    public static long getNancyLuInvulnerableUntil(net.minecraft.world.entity.player.Player player) {
        return NANCY_LU_INVULNERABLE_UNTIL.get(player);
    }

    public static void setNancyLuInvulnerableUntil(net.minecraft.world.entity.player.Player player, long value) {
        NANCY_LU_INVULNERABLE_UNTIL.set(player, Math.max(0, value));
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

    public static long getSandwichHighCounterCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return SANDWICH_HIGH_COUNTER_COOLDOWN_END.get(player);
    }

    public static void setSandwichHighCounterCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        SANDWICH_HIGH_COUNTER_COOLDOWN_END.set(player, Math.max(0, value));
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

    // 战斗爽·扩散待命:主动消耗 2 层养精蓄锐后置位,下次骰神赐福期间启用
    public static final AttachedDataKey<Boolean> FEN_CLEAVE_PENDING =
            register(AttachedDataKey.builder("fen_cleave_pending", Codec.BOOL, () -> false).build());

    // 战斗爽·扩散生效:本次骰神赐福期间,每次攻击将总伤害的 80% 扩散给目标 6 格内敌对目标,赐福结束清除
    public static final AttachedDataKey<Boolean> FEN_CLEAVE_ACTIVE =
            register(AttachedDataKey.builder("fen_cleave_active", Codec.BOOL, () -> false).build());

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

    public static boolean isFenCleavePending(net.minecraft.world.entity.player.Player player) {
        return FEN_CLEAVE_PENDING.get(player);
    }

    public static void setFenCleavePending(net.minecraft.world.entity.player.Player player, boolean value) {
        FEN_CLEAVE_PENDING.set(player, value);
    }

    public static boolean isFenCleaveActive(net.minecraft.world.entity.player.Player player) {
        return FEN_CLEAVE_ACTIVE.get(player);
    }

    public static void setFenCleaveActive(net.minecraft.world.entity.player.Player player, boolean value) {
        FEN_CLEAVE_ACTIVE.set(player, value);
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

    // 计时器守卫:本模组有时长效果的结束时刻记录(效果注册名 → 结束 tick + 重施加参数)。
    // 仅服务端使用,序列化持久化;由 EffectTimerGuard 维护,保证效果严格按 20t/s 流动。
    public static final AttachedDataKey<Map<String,
            com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry>> EFFECT_TIMER_ENDS =
            register(AttachedDataKey.builder("effect_timer_ends",
                    Codec.unboundedMap(Codec.STRING,
                            com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry.CODEC),
                    HashMap::new).build());

    /** synced 键快照发送(登录/重生/切维度时)。 */
    public static void sendSyncSnapshot(ServerPlayer player) {
        com.merlinkitsune.astral_dice.network.ModNetwork.syncSnapshot(player, syncedKeys());
    }

    static List<AttachedDataKey<?>> syncedKeys() {
        if (SYNCED_KEYS.isEmpty()) {
            SYNCED_KEYS.add(PLAYER_STARLIGHT);
            SYNCED_KEYS.add(DAMAGE_EFFECT_BONUS);
            SYNCED_KEYS.add(EFFECT_CARD_PLAY_COUNT);
            SYNCED_KEYS.add(KOMACHI_EXTRA_PLAYS);
            SYNCED_KEYS.add(EFFECT_CARD_COOLDOWN_END);
            SYNCED_KEYS.add(HEALING_POINTS);
            SYNCED_KEYS.add(KOMACHI_DAMAGE_BONUS);
            SYNCED_KEYS.add(SIGN_ACTIVE_COOLDOWN_END);
            SYNCED_KEYS.add(SIGN_READY_TYPE);
            SYNCED_KEYS.add(SIGN_READY_EXPIRE);
            SYNCED_KEYS.add(CURSED_SWORD_BONUS);
            SYNCED_KEYS.add(CANDY_CHIP_PLAY_BONUS);
            SYNCED_KEYS.add(SATELLITE_PLAY_BONUS);
            SYNCED_KEYS.add(SATELLITE_PLAY_BONUS_COOLDOWN_END);
            SYNCED_KEYS.add(NANCY_LU_ACTIVE_BONUS);
            SYNCED_KEYS.add(FEN_RECHARGE);
        }
        return SYNCED_KEYS;
    }

    private ModAttachments() {
    }
}
