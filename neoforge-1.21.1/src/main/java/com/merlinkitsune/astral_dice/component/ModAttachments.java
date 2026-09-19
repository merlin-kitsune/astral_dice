package com.merlinkitsune.astral_dice.component;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.sign.FenSignItem;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AstralDiceMod.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> PLAYER_STARLIGHT =
            ATTACHMENTS.register("player_starlight", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    // 八面骰筹码:骰神赐福触发时累计的骰点总和(每满 8 点 +1 星光,达到 32 星光后归 0)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> EIGHT_SIDED_ROLL_ACCUM =
            ATTACHMENTS.register("eight_sided_roll_accum", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .build());

    // 效果牌:当前周期内已连续出牌数
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> EFFECT_CARD_PLAY_COUNT =
            ATTACHMENTS.register("effect_card_play_count", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    // 出牌轮一次性追加的出牌数(0/1):由立牌主动技能(忍者「忍术连击」)授予,**仅当前出牌轮有效**——
    // 不是可累积、可跨轮保留的"出牌银行";周期结束时由 EffectCardPeriod 统一清除,
    // 立牌装卸不影响(授予即已消耗)。授予入口见 EffectCardPeriod#grantBonusPlay。
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> EFFECT_CARD_BONUS_PLAYS =
            ATTACHMENTS.register("effect_card_bonus_plays", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    public static int getEffectCardBonusPlays(net.minecraft.world.entity.player.Player player) {
        return player.getData(EFFECT_CARD_BONUS_PLAYS.get());
    }

    public static void setEffectCardBonusPlays(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(EFFECT_CARD_BONUS_PLAYS.get(), Math.max(0, value));
    }

    // 活体书页(effect_card_living_page):本周期活体书页累计的出牌数加成(0,1,2,…)。
    // 每次使用活体书页 +1(可累计;不是"效果存在即 +1"的开关式),周期归零时由 EffectCardPeriod 清除。
    // 注意:与"调查员已用页数"(rin_pages,永久累计;作为加成生效时静默上限 120)无关,不可复用后者做本周期计数。
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> LIVING_PAGE_CYCLE_BONUS =
            ATTACHMENTS.register("living_page_cycle_bonus", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    public static int getLivingPageCycleBonus(net.minecraft.world.entity.player.Player player) {
        return player.getData(LIVING_PAGE_CYCLE_BONUS.get());
    }

    // 计数器只增不减(归零由周期清理负责),此处仅钳制非负
    public static void setLivingPageCycleBonus(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(LIVING_PAGE_CYCLE_BONUS.get(), Math.max(0, value));
    }

    // 「符卡-福」(风水师立牌专属牌,2026-09-26 用户裁决 B)本周期专属出牌数计数器:
    // 语义 = **本出牌周期内打出符卡-福的次数**,每次打出 +1(按次累加,不是"每轮一次"的开关式),
    // 与忍者立牌主动的一次性槽位(EFFECT_CARD_BONUS_PLAYS)**彻底解耦** —— 两者可叠加。
    // 由 EffectCardPeriod#grantFuCardBonusPlay 写入、getMaxAllowed 计入 extra(受 min(9, 1+extra) 封顶)、
    // clearRoundBonuses 周期归零;**不复用** LIVING_PAGE_CYCLE_BONUS(那是活体书页的计数)。
    // .sync 依据:客户端预检 BaseEffectCardItem#isBlockedOnClient → EffectCardPeriod#isBurstFull
    // → getMaxAllowed 需要在本轮上限上看到同一份额外出牌数(否则客户端会误判"已打满"),
    // 与 EFFECT_CARD_BONUS_PLAYS / LIVING_PAGE_CYCLE_BONUS 两个同级计数器同址同步。
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> FU_CARD_CYCLE_BONUS =
            ATTACHMENTS.register("fu_card_cycle_bonus", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    public static int getFuCardCycleBonus(net.minecraft.world.entity.player.Player player) {
        return player.getData(FU_CARD_CYCLE_BONUS.get());
    }

    public static void setFuCardCycleBonus(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(FU_CARD_CYCLE_BONUS.get(), Math.max(0, value));
    }

    // 效果牌公共冷却结束时刻(-1 表示待定冷却=伤害效果牌效果等待中;0 表示无)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> EFFECT_CARD_COOLDOWN_END =
            ATTACHMENTS.register("effect_card_cooldown_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    // 史莱姆立牌:上次受击获得治愈的游戏时刻(限制受击 +1 的频率,防止围攻时点数暴涨)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> LULU_LAST_HURT_TICK =
            ATTACHMENTS.register("lulu_last_hurt_tick", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    public static long getLuluLastHurtTick(net.minecraft.world.entity.player.Player player) {
        return player.getData(LULU_LAST_HURT_TICK.get());
    }

    public static void setLuluLastHurtTick(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(LULU_LAST_HURT_TICK.get(), value);
    }

    // 治愈点数(玩家级共享资源的单一数值池):由医疗箱(赐福触发时加点)/史莱姆立牌被动/主动/
    // 缓冲盾牌等获取,由 HealingManager 统一管理。治愈体系已无独立计时器:
    // 触发骰神赐福时按当前治愈点×2 回血,赐福结束时治愈点减半(向下取整)。
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> HEALING_POINTS =
            ATTACHMENTS.register("healing_points", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    public static int getHealingPoints(net.minecraft.world.entity.player.Player player) {
        return player.getData(HEALING_POINTS.get());
    }

    public static void setHealingPoints(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(HEALING_POINTS.get(), Math.max(0, value));
    }

    // 治愈:上一检测周期玩家是否处于"骰神赐福"(服务端边沿检测用,判断赐福结束时刻以执行治愈减半;
    // 仅服务端使用,无需同步)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> HEALING_PREV_BLESSING =
            ATTACHMENTS.register("healing_prev_blessing", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

    public static boolean isHealingPrevBlessing(net.minecraft.world.entity.player.Player player) {
        return player.getData(HEALING_PREV_BLESSING.get());
    }

    public static void setHealingPrevBlessing(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(HEALING_PREV_BLESSING.get(), value);
    }

    // 治愈:独立 30 秒计时器结束 tick(服务端使用;0 表示无计时器)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> HEALING_TIMER_END =
            ATTACHMENTS.register("healing_timer_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    public static long getHealingTimerEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(HEALING_TIMER_END.get());
    }

    public static void setHealingTimerEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(HEALING_TIMER_END.get(), Math.max(0, value));
    }


    // 魔法秘典筹码:效果牌使用计数(每使用 3 张复制最后一张)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> MAGIC_TOME_USE_COUNT =
            ATTACHMENTS.register("magic_tome_use_count", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    // 魔法秘典筹码:最后一张使用的效果牌类型(king_power/berserk/unwavering)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> MAGIC_TOME_LAST_CARD =
            ATTACHMENTS.register("magic_tome_last_card", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .build());

    // 忍者立牌(komachi):效果牌使用计数(独立于魔法秘典与周期计数,每使用 3 张复制最后一张)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> KOMACHI_USE_COUNT =
            ATTACHMENTS.register("komachi_use_count", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    // 忍者立牌(komachi):最后一张使用的效果牌类型
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> KOMACHI_LAST_CARD =
            ATTACHMENTS.register("komachi_last_card", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .build());

    // 忍者立牌(komachi):效果牌伤害增益(每使用 3 张效果牌 +1,累计无上限;作为加成生效时静默上限 120,卸下立牌重置;死亡重生保留)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> KOMACHI_DAMAGE_BONUS =
            ATTACHMENTS.register("komachi_damage_bonus", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .copyOnDeath()
                    .build());

    // 小猪存钱罐筹码:效果牌使用计数(每使用 2 张获得 3 星币;卸下筹码重置)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> PIGGY_BANK_USE_COUNT =
            ATTACHMENTS.register("piggy_bank_use_count", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .build());

    public static int getPiggyBankUseCount(net.minecraft.world.entity.player.Player player) {
        return player.getData(PIGGY_BANK_USE_COUNT.get());
    }

    public static void setPiggyBankUseCount(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(PIGGY_BANK_USE_COUNT.get(), Math.max(0, value));
    }

    // 手电筒-强光筹码:已发放过星光的敌对目标 UUID(逗号分隔;同一目标仅 +1 层星光)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> FLASHLIGHT_GRANTED_TARGETS =
            ATTACHMENTS.register("flashlight_granted_targets", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .build());

    public static String getFlashlightGrantedTargets(net.minecraft.world.entity.player.Player player) {
        return player.getData(FLASHLIGHT_GRANTED_TARGETS.get());
    }

    public static void setFlashlightGrantedTargets(net.minecraft.world.entity.player.Player player, String value) {
        player.setData(FLASHLIGHT_GRANTED_TARGETS.get(), value == null ? "" : value);
    }

    public static int getMagicTomeUseCount(net.minecraft.world.entity.player.Player player) {
        return player.getData(MAGIC_TOME_USE_COUNT.get());
    }

    public static void setMagicTomeUseCount(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(MAGIC_TOME_USE_COUNT.get(), Math.max(0, value));
    }

    public static String getMagicTomeLastCard(net.minecraft.world.entity.player.Player player) {
        return player.getData(MAGIC_TOME_LAST_CARD.get());
    }

    public static void setMagicTomeLastCard(net.minecraft.world.entity.player.Player player, String value) {
        player.setData(MAGIC_TOME_LAST_CARD.get(), value);
    }

    public static int getKomachiUseCount(net.minecraft.world.entity.player.Player player) {
        return player.getData(KOMACHI_USE_COUNT.get());
    }
    public static void setKomachiUseCount(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(KOMACHI_USE_COUNT.get(), Math.max(0, value));
    }

    public static String getKomachiLastCard(net.minecraft.world.entity.player.Player player) {
        return player.getData(KOMACHI_LAST_CARD.get());
    }

    public static void setKomachiLastCard(net.minecraft.world.entity.player.Player player, String value) {
        player.setData(KOMACHI_LAST_CARD.get(), value);
    }

    public static int getKomachiDamageBonus(net.minecraft.world.entity.player.Player player) {
        return player.getData(KOMACHI_DAMAGE_BONUS.get());
    }

    public static void setKomachiDamageBonus(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(KOMACHI_DAMAGE_BONUS.get(), Math.max(0, value));
    }

    // 命运的指引激活截止时刻(gameTime 毫秒? 否:tick)。使用后写入 now+6000;功能检查用 now < until
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> FATE_ACTIVE_UNTIL =
            ATTACHMENTS.register("fate_active_until", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    public static long getFateActiveUntil(net.minecraft.world.entity.player.Player player) {
        return player.getData(FATE_ACTIVE_UNTIL.get());
    }

    public static void setFateActiveUntil(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(FATE_ACTIVE_UNTIL.get(), value);
    }

    // 骰战七咒倍率捕获:神秘遗物+ 模组在 LivingIncomingDamageEvent 应用第一诅咒倍率(含其配置 painMultiplier
    // 与修正物品,如大地誓约)后,由本模组 LOWEST 处理器捕获实际倍率供骰战最终伤害使用;
    // 仅内存态(不序列化),骰战结算使用后清零;非骰战攻击不使用。
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> DICE_CURSE_RATIO =
            ATTACHMENTS.register("dice_curse_ratio", () -> AttachmentType.builder(() -> 1.0f)
                    .build());

    public static float getDiceCurseRatio(net.minecraft.world.entity.player.Player player) {
        return player.getData(DICE_CURSE_RATIO.get());
    }

    public static void setDiceCurseRatio(net.minecraft.world.entity.player.Player player, float value) {
        player.setData(DICE_CURSE_RATIO.get(), value);
    }

    // 调查员立牌(rin):已使用的活体书页数量(活体书页伤害永久+1 的来源,移除立牌后重置;死亡重生保留)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> RIN_PAGES =
            ATTACHMENTS.register("rin_pages", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .copyOnDeath()
                    .build());

    // 调查员立牌(rin):最近一次获得活体书页的事件签名(触发者 UUID + "|" + 事件 ID)。
    // 用于同一事件在极短窗口(2 tick)内被重复分发时去重(如多立牌槽重复调用 onKill),
    // 保证"同一玩家发出的同一 ID 事件"只给一次牌。
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> RIN_GIFT_SIGNATURE =
            ATTACHMENTS.register("rin_gift_signature", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .build());

    public static String getRinGiftSignature(net.minecraft.world.entity.player.Player player) {
        return player.getData(RIN_GIFT_SIGNATURE.get());
    }

    public static void setRinGiftSignature(net.minecraft.world.entity.player.Player player, String value) {
        player.setData(RIN_GIFT_SIGNATURE.get(), value);
    }

    // 调查员立牌(rin):记录 RIN_GIFT_SIGNATURE 对应的游戏时刻(去重窗口判定用)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> RIN_GIFT_TICK =
            ATTACHMENTS.register("rin_gift_tick", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    public static long getRinGiftTick(net.minecraft.world.entity.player.Player player) {
        return player.getData(RIN_GIFT_TICK.get());
    }

    public static void setRinGiftTick(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(RIN_GIFT_TICK.get(), value);
    }

    // 虚弱印记来源:施加该印记的玩家 UUID(仅该玩家获得击杀后奖励,印记结束/目标死亡后清除)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Optional<UUID>>> WEAK_MARK_SOURCE =
            ATTACHMENTS.register("weak_mark_source", () -> AttachmentType.<Optional<UUID>>builder(Optional::empty)
                    .serialize(UUIDUtil.CODEC.optionalFieldOf("id").codec())
                    .build());

    public static Optional<UUID> getWeakMarkSource(net.minecraft.world.entity.LivingEntity entity) {
        return entity.getData(WEAK_MARK_SOURCE.get());
    }

    public static void setWeakMarkSource(net.minecraft.world.entity.LivingEntity entity, Optional<UUID> value) {
        entity.setData(WEAK_MARK_SOURCE.get(), value);
    }

    // 隐匿调查来源:施加"隐匿调查"的玩家 UUID(击杀该目标触发调查阶段事件时,奖励归属施加者)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Optional<UUID>>> UNDERCOVER_SOURCE =
            ATTACHMENTS.register("undercover_source", () -> AttachmentType.<Optional<UUID>>builder(Optional::empty)
                    .serialize(UUIDUtil.CODEC.optionalFieldOf("id").codec())
                    .build());

    // 调查阶段进度:1=调查阶段I 2=II 3=III 4=真相揭露(进度归属施加"隐匿调查"的玩家)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> INVESTIGATION_STAGE =
            ATTACHMENTS.register("investigation_stage", () -> AttachmentType.builder(() -> 1)
                    .serialize(Codec.INT)
                    .build());

    // 破绽(枪匠立牌 Moses)期间,目标是否已被攻击方获得过弱点识破(每段破绽一次)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> MOSES_BROKEN_ATTACK_REWARDED =
            ATTACHMENTS.register("moses_broken_attack_rewarded", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

    // 枪匠立牌 Moses:目标是否已因闪避/反击获得过弱点识破(每个目标一次,不依赖破绽)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> MOSES_DODGE_COUNTER_REWARDED =
            ATTACHMENTS.register("moses_dodge_counter_rewarded", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

    public static boolean isMosesBrokenAttackRewarded(net.minecraft.world.entity.LivingEntity entity) {
        return entity.getData(MOSES_BROKEN_ATTACK_REWARDED.get());
    }

    public static void setMosesBrokenAttackRewarded(net.minecraft.world.entity.LivingEntity entity, boolean value) {
        entity.setData(MOSES_BROKEN_ATTACK_REWARDED.get(), value);
    }

    public static boolean isMosesDodgeCounterRewarded(net.minecraft.world.entity.LivingEntity entity) {
        return entity.getData(MOSES_DODGE_COUNTER_REWARDED.get());
    }

    public static void setMosesDodgeCounterRewarded(net.minecraft.world.entity.LivingEntity entity, boolean value) {
        entity.setData(MOSES_DODGE_COUNTER_REWARDED.get(), value);
    }

    public static Optional<UUID> getUndercoverSource(net.minecraft.world.entity.LivingEntity entity) {
        return entity.getData(UNDERCOVER_SOURCE.get());
    }

    public static void setUndercoverSource(net.minecraft.world.entity.LivingEntity entity, Optional<UUID> value) {
        entity.setData(UNDERCOVER_SOURCE.get(), value);
    }

    public static int getInvestigationStage(net.minecraft.world.entity.player.Player player) {
        return player.getData(INVESTIGATION_STAGE.get());
    }

    public static void setInvestigationStage(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(INVESTIGATION_STAGE.get(), Math.max(1, value));
    }

    public static int getStarlight(net.minecraft.world.entity.player.Player player) {
        return player.getData(PLAYER_STARLIGHT.get());
    }

    public static void setStarlight(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(PLAYER_STARLIGHT.get(), value);
    }

    public static int getEightSidedAccum(net.minecraft.world.entity.player.Player player) {
        return player.getData(EIGHT_SIDED_ROLL_ACCUM.get());
    }

    public static void setEightSidedAccum(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(EIGHT_SIDED_ROLL_ACCUM.get(), value);
    }

    // 立牌主动技能冷却结束时刻(玩家级,不受立牌装卸影响;0 表示无冷却)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SIGN_ACTIVE_COOLDOWN_END =
            ATTACHMENTS.register("sign_active_cooldown_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    public static long getSignActiveCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_ACTIVE_COOLDOWN_END.get());
    }

    public static void setSignActiveCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(SIGN_ACTIVE_COOLDOWN_END.get(), value);
    }

    // 立牌主动技能"本次冷却实际使用的最大冷却 tick"(路线 A:所有减免方一律读它作基准,不再各自重算;
    // 0 表示缺失/无冷却,减免方回退旧行为;仅服务端使用,无需同步客户端)。
    // 写入时机(第二批「立牌主动技能三态化」):① 直接起冷却时与 SIGN_ACTIVE_COOLDOWN_END 成对写入;
    // ② 进入"锁定(生效中)"态时先写入基准(锁定期间减免方照旧读它并累加进 SIGN_ACTIVE_REDUCTION_POOL),
    //    冷却起点火时再改写为扣池后的实际冷却 effective = max(0, 基准 − 池)。
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SIGN_ACTIVE_MAX_COOLDOWN =
            ATTACHMENTS.register("sign_active_max_cooldown", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    public static long getSignActiveMaxCooldown(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_ACTIVE_MAX_COOLDOWN.get());
    }

    public static void setSignActiveMaxCooldown(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(SIGN_ACTIVE_MAX_COOLDOWN.get(), value);
    }

    // ===== 立牌主动技能"三态化"(可用 / 锁定-生效中 / 冷却)的玩家级状态 =====
    // 2026-09-25 用户裁决(第二批):主动技能施加的"带时长效果/自身计时器"跑完之前处于**锁定(生效中)**态,
    // 期间按键无效;锁定结束**必起冷却**(无空档)。全部键仅服务端使用(判定只在服务端 performSkill /
    // 各减免方 / 玩家级 tick),故一律不 .sync()(客户端 tooltip 不显示"生效中",保持零客户端改动)。

    // 锁定态标记:正在生效中的主动所属立牌的**物品注册 id**("" = 未锁定)。判定见 BaseSignItem#isSignActiveLocked
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> SIGN_ACTIVE_LOCK_SIGN =
            ATTACHMENTS.register("sign_active_lock_sign", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .build());

    // 锁定态的硬上界:触发时刻算定的"本技能施加的全部计时器到期刻取 max"
    // (0 = 无自身计时器,仅忍者使用——其锁定跟随出牌周期,由周期完全重置结束)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SIGN_ACTIVE_LOCK_END =
            ATTACHMENTS.register("sign_active_lock_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    // 锁定期间累计的冷却减免池(tick):锁定结束起冷却时一次性抵扣并归零
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SIGN_ACTIVE_REDUCTION_POOL =
            ATTACHMENTS.register("sign_active_reduction_pool", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    // 忍者立牌专用:宽限到期刻(触发主动时刻 + 1:00;0 = 宽限已失效/不适用)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SIGN_ACTIVE_LOCK_GRACE_END =
            ATTACHMENTS.register("sign_active_lock_grace_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    // 忍者立牌专用:本次锁定/宽限期内是否已出过任何效果牌(true = 宽限保险失效,遵循出牌周期)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SIGN_ACTIVE_LOCK_PLAYED =
            ATTACHMENTS.register("sign_active_lock_played", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

    public static String getSignActiveLockSign(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_ACTIVE_LOCK_SIGN.get());
    }

    public static void setSignActiveLockSign(net.minecraft.world.entity.player.Player player, String value) {
        player.setData(SIGN_ACTIVE_LOCK_SIGN.get(), value == null ? "" : value);
    }

    public static long getSignActiveLockEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_ACTIVE_LOCK_END.get());
    }

    public static void setSignActiveLockEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(SIGN_ACTIVE_LOCK_END.get(), value);
    }

    public static long getSignActiveReductionPool(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_ACTIVE_REDUCTION_POOL.get());
    }

    public static void setSignActiveReductionPool(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(SIGN_ACTIVE_REDUCTION_POOL.get(), Math.max(0L, value));
    }

    public static void addSignActiveReductionPool(net.minecraft.world.entity.player.Player player, long delta) {
        setSignActiveReductionPool(player, player.getData(SIGN_ACTIVE_REDUCTION_POOL.get()) + delta);
    }

    public static long getSignActiveLockGraceEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_ACTIVE_LOCK_GRACE_END.get());
    }

    public static void setSignActiveLockGraceEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(SIGN_ACTIVE_LOCK_GRACE_END.get(), value);
    }

    public static boolean getSignActiveLockPlayed(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_ACTIVE_LOCK_PLAYED.get());
    }

    public static void setSignActiveLockPlayed(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(SIGN_ACTIVE_LOCK_PLAYED.get(), value);
    }

    // 末影骰子:不死图腾效果冷却结束时刻(玩家级,0 表示未进入冷却)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> ENDER_DIE_TOTEM_COOLDOWN_END =
            ATTACHMENTS.register("ender_die_totem_cooldown_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    public static long getEnderDieTotemCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(ENDER_DIE_TOTEM_COOLDOWN_END.get());
    }

    public static void setEnderDieTotemCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(ENDER_DIE_TOTEM_COOLDOWN_END.get(), value);
    }

    // 跃迁引擎:传送门/Waystone 传送获得充能的共享冷却结束时刻(玩家级,0 表示无冷却;仅服务端使用)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> WARP_ENGINE_PORTAL_COOLDOWN_END =
            ATTACHMENTS.register("warp_engine_portal_cooldown_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    public static long getWarpEnginePortalCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(WARP_ENGINE_PORTAL_COOLDOWN_END.get());
    }

    public static void setWarpEnginePortalCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(WARP_ENGINE_PORTAL_COOLDOWN_END.get(), value);
    }

    // @Deprecated 已废弃:立牌主动技能"等待目标释放"机制已被目标选择器(TargetSelectionManager 会话)替代,
    // 占星师/秘密侦探不再读写本附件。定义保留(已 serialize 持久化)以避免旧存档附件数据异常,禁止新代码使用。
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> SIGN_READY_TYPE =
            ATTACHMENTS.register("sign_ready_type", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    // @Deprecated 已废弃:见 SIGN_READY_TYPE
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SIGN_READY_EXPIRE =
            ATTACHMENTS.register("sign_ready_expire", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    /** @deprecated 已废弃,由目标选择器会话替代;禁止新代码使用 */
    @Deprecated
    public static int getSignReadyType(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_READY_TYPE.get());
    }

    /** @deprecated 已废弃,由目标选择器会话替代;禁止新代码使用 */
    @Deprecated
    public static void setSignReadyType(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(SIGN_READY_TYPE.get(), value);
    }

    /** @deprecated 已废弃,由目标选择器会话替代;禁止新代码使用 */
    @Deprecated
    public static long getSignReadyExpire(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_READY_EXPIRE.get());
    }

    /** @deprecated 已废弃,由目标选择器会话替代;禁止新代码使用 */
    @Deprecated
    public static void setSignReadyExpire(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(SIGN_READY_EXPIRE.get(), value);
    }

    // 效果牌出牌周期计时相关
    public static int getEffectCardPlayCount(net.minecraft.world.entity.player.Player player) {
        return player.getData(EFFECT_CARD_PLAY_COUNT.get());
    }

    public static void setEffectCardPlayCount(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(EFFECT_CARD_PLAY_COUNT.get(), value);
    }

    public static long getEffectCardCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(EFFECT_CARD_COOLDOWN_END.get());
    }

    public static void setEffectCardCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(EFFECT_CARD_COOLDOWN_END.get(), value);
    }

    public static int getRinPages(net.minecraft.world.entity.player.Player player) {
        return player.getData(RIN_PAGES.get());
    }

    public static void setRinPages(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(RIN_PAGES.get(), value);
    }

    // === 新筹码附件(魔法箭袋/缓冲盾牌/星币锤) ===

    // 魔法箭袋筹码:是否已记录"第一张使用的效果牌"(一次触发周期内)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> MAGIC_QUIVER_TRACKING =
            ATTACHMENTS.register("magic_quiver_tracking", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

    // 魔法箭袋筹码:记录的第一张使用的效果牌类型(king_power/berserk/unwavering)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> MAGIC_QUIVER_FIRST_CARD =
            ATTACHMENTS.register("magic_quiver_first_card", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .build());

    // 魔法箭袋筹码:触发冷却结束时刻(1 分钟;0 表示无冷却)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> MAGIC_QUIVER_COOLDOWN_END =
            ATTACHMENTS.register("magic_quiver_cooldown_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    // 缓冲盾牌筹码:触发冷却结束时刻(1 分钟;0 表示无冷却)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> BUFFER_SHIELD_COOLDOWN_END =
            ATTACHMENTS.register("buffer_shield_cooldown_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    // 星币锤筹码:本次骰神赐福的攻击力加成(进入赐福时设置,赐福结束/卸下清除)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> STAR_COIN_HAMMER_BONUS =
            ATTACHMENTS.register("star_coin_hammer_bonus", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .build());

    // 诅咒之剑筹码:累计击杀不少于 20 血的敌对目标获得的攻击力加成(移除筹码/死亡清除)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> CURSED_SWORD_BONUS =
            ATTACHMENTS.register("cursed_sword_bonus", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    // 诅咒之剑筹码:当前骰神赐福期间是否已触发过击杀加成(每个赐福周期最多一次)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> CURSED_SWORD_BLESSING_TRIGGERED =
            ATTACHMENTS.register("cursed_sword_blessing_triggered", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

    // 可口糖果筹码:当前效果牌出牌轮次是否已触发过"满血时出牌数+1"(每个轮次最多一次)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> CANDY_CHIP_PLAY_BONUS =
            ATTACHMENTS.register("candy_chip_play_bonus", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .sync(ByteBufCodecs.BOOL)
                    .build());

    // 探天卫星筹码:补充轨道炮冷却结束时刻
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SATELLITE_GIVE_COOLDOWN_END =
            ATTACHMENTS.register("satellite_give_cooldown_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    // 探天卫星筹码:当前效果牌出牌轮次是否已触发过"使用轨道炮后出牌数+1"(每轮最多一次;
    // 触发时机受 SATELLITE_PLAY_BONUS_COOLDOWN_END 限制,每 1:00 至多触发一次)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SATELLITE_PLAY_BONUS =
            ATTACHMENTS.register("satellite_play_bonus", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .sync(ByteBufCodecs.BOOL)
                    .build());

    // 探天卫星筹码:"使用轨道炮后出牌数+1"的触发冷却结束时刻(每 1:00 至多触发一次;0 表示无冷却)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SATELLITE_PLAY_BONUS_COOLDOWN_END =
            ATTACHMENTS.register("satellite_play_bonus_cooldown_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    // 骇客立牌:被动类型(0=无,1=攻击,2=防御)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> NANCY_LU_PASSIVE_TYPE =
            ATTACHMENTS.register("nancy_lu_passive_type", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    // 骇客立牌:主动"远程侵入"攻击力加成数值
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> NANCY_LU_ACTIVE_BONUS =
            ATTACHMENTS.register("nancy_lu_active_bonus", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    // 骇客立牌:主动攻击力加成结束时刻
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> NANCY_LU_ACTIVE_BONUS_UNTIL =
            ATTACHMENTS.register("nancy_lu_active_bonus_until", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    // 骇客立牌:主动"完全隐身"结束时刻
    // (同步到客户端:client/NancyLuClientEvents 据此在隐身期间取消自身渲染)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> NANCY_LU_HIDDEN_UNTIL =
            ATTACHMENTS.register("nancy_lu_hidden_until", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    // 看板娘立牌:被动"主动技能返还"累计的战斗牌数量(每累计 25 张返还战斗牌获得一个随机筹码)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> MIMI_RETURNED_CARD_COUNT =
            ATTACHMENTS.register("mimi_returned_card_count", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .build());

    // 骇客立牌:末影珍珠传送伤害免疫结束时刻
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> NANCY_LU_ENDER_PEARL_IMMUNE_UNTIL =
            ATTACHMENTS.register("nancy_lu_ender_pearl_immune_until", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    public static boolean getMagicQuiverTracking(net.minecraft.world.entity.player.Player player) {
        return player.getData(MAGIC_QUIVER_TRACKING.get());
    }

    public static void setMagicQuiverTracking(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(MAGIC_QUIVER_TRACKING.get(), value);
    }

    public static String getMagicQuiverFirstCard(net.minecraft.world.entity.player.Player player) {
        return player.getData(MAGIC_QUIVER_FIRST_CARD.get());
    }

    public static void setMagicQuiverFirstCard(net.minecraft.world.entity.player.Player player, String value) {
        player.setData(MAGIC_QUIVER_FIRST_CARD.get(), value);
    }

    public static long getMagicQuiverCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(MAGIC_QUIVER_COOLDOWN_END.get());
    }

    public static void setMagicQuiverCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(MAGIC_QUIVER_COOLDOWN_END.get(), value);
    }

    public static long getBufferShieldCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(BUFFER_SHIELD_COOLDOWN_END.get());
    }

    public static void setBufferShieldCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(BUFFER_SHIELD_COOLDOWN_END.get(), value);
    }

    public static int getStarCoinHammerBonus(net.minecraft.world.entity.player.Player player) {
        return player.getData(STAR_COIN_HAMMER_BONUS.get());
    }

    public static void setStarCoinHammerBonus(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(STAR_COIN_HAMMER_BONUS.get(), Math.max(0, value));
    }

    public static int getCursedSwordBonus(net.minecraft.world.entity.player.Player player) {
        return player.getData(CURSED_SWORD_BONUS.get());
    }

    public static void setCursedSwordBonus(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(CURSED_SWORD_BONUS.get(), Math.max(0, value));
    }

    public static boolean getCursedSwordBlessingTriggered(net.minecraft.world.entity.player.Player player) {
        return player.getData(CURSED_SWORD_BLESSING_TRIGGERED.get());
    }

    public static void setCursedSwordBlessingTriggered(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(CURSED_SWORD_BLESSING_TRIGGERED.get(), value);
    }

    public static boolean isCandyChipPlayBonusActive(net.minecraft.world.entity.player.Player player) {
        return player.getData(CANDY_CHIP_PLAY_BONUS.get());
    }

    public static void setCandyChipPlayBonusActive(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(CANDY_CHIP_PLAY_BONUS.get(), value);
    }

    public static long getSatelliteGiveCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(SATELLITE_GIVE_COOLDOWN_END.get());
    }

    public static void setSatelliteGiveCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(SATELLITE_GIVE_COOLDOWN_END.get(), Math.max(0, value));
    }

    public static boolean isSatellitePlayBonusActive(net.minecraft.world.entity.player.Player player) {
        return player.getData(SATELLITE_PLAY_BONUS.get());
    }

    public static void setSatellitePlayBonusActive(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(SATELLITE_PLAY_BONUS.get(), value);
    }

    public static long getSatellitePlayBonusCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(SATELLITE_PLAY_BONUS_COOLDOWN_END.get());
    }

    public static void setSatellitePlayBonusCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(SATELLITE_PLAY_BONUS_COOLDOWN_END.get(), Math.max(0, value));
    }

    public static int getNancyLuPassiveType(net.minecraft.world.entity.player.Player player) {
        return player.getData(NANCY_LU_PASSIVE_TYPE.get());
    }

    public static void setNancyLuPassiveType(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(NANCY_LU_PASSIVE_TYPE.get(), Math.max(0, value));
    }

    public static int getNancyLuActiveBonus(net.minecraft.world.entity.player.Player player) {
        return player.getData(NANCY_LU_ACTIVE_BONUS.get());
    }

    public static void setNancyLuActiveBonus(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(NANCY_LU_ACTIVE_BONUS.get(), Math.max(0, value));
    }

    public static long getNancyLuActiveBonusUntil(net.minecraft.world.entity.player.Player player) {
        return player.getData(NANCY_LU_ACTIVE_BONUS_UNTIL.get());
    }

    public static void setNancyLuActiveBonusUntil(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(NANCY_LU_ACTIVE_BONUS_UNTIL.get(), Math.max(0, value));
    }

    public static long getNancyLuHiddenUntil(net.minecraft.world.entity.player.Player player) {
        return player.getData(NANCY_LU_HIDDEN_UNTIL.get());
    }

    public static void setNancyLuHiddenUntil(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(NANCY_LU_HIDDEN_UNTIL.get(), Math.max(0, value));
    }

    public static int getMimiReturnedCardCount(net.minecraft.world.entity.player.Player player) {
        return player.getData(MIMI_RETURNED_CARD_COUNT.get());
    }

    public static void setMimiReturnedCardCount(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(MIMI_RETURNED_CARD_COUNT.get(), Math.max(0, value));
    }

    public static long getNancyLuEnderPearlImmuneUntil(net.minecraft.world.entity.player.Player player) {
        return player.getData(NANCY_LU_ENDER_PEARL_IMMUNE_UNTIL.get());
    }

    public static void setNancyLuEnderPearlImmuneUntil(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(NANCY_LU_ENDER_PEARL_IMMUNE_UNTIL.get(), Math.max(0, value));
    }

    // === 大当家立牌(fen)附件 ===

    // 养精蓄锐层数(玩家级,上限见 FenSignItem.MAX_RECHARGE)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> FEN_RECHARGE =
            ATTACHMENTS.register("fen_recharge", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    // 上次触发骰神赐福的时刻(用于"1 分钟未触发赐福 → 养精蓄锐 +1 层")
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> FEN_LAST_BLESSING_TICK =
            ATTACHMENTS.register("fen_last_blessing_tick", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    // 注:"战斗爽·溅射"已改为被动单次效果,由 DiceCombatEvents 的局部变量承载,不再需要附件

    public static int getFenRecharge(net.minecraft.world.entity.player.Player player) {
        return player.getData(FEN_RECHARGE.get());
    }

    public static void setFenRecharge(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(FEN_RECHARGE.get(), Math.max(0, value));
    }

    public static long getFenLastBlessingTick(net.minecraft.world.entity.player.Player player) {
        return player.getData(FEN_LAST_BLESSING_TICK.get());
    }

    public static void setFenLastBlessingTick(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(FEN_LAST_BLESSING_TICK.get(), value);
    }

    // 以毒攻毒:记录生命恢复 II 的触发时刻(中毒 8 秒后)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> FIGHT_POISON_WITH_POISON_REGEN_AT =
            ATTACHMENTS.register("fight_poison_with_poison_regen_at", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    public static long getFightPoisonWithPoisonRegenAt(net.minecraft.world.entity.player.Player player) {
        return player.getData(FIGHT_POISON_WITH_POISON_REGEN_AT.get());
    }

    public static void setFightPoisonWithPoisonRegenAt(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(FIGHT_POISON_WITH_POISON_REGEN_AT.get(), value);
    }

    // 防御牌是否已在当前骰神赐福期间消耗过耐久(怪物近战攻击触发,每个赐福期间最多一次)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> DEFENSE_CARD_CONSUMED_BLESSING =
            ATTACHMENTS.register("defense_card_consumed_blessing", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

    public static boolean isDefenseCardConsumedThisBlessing(net.minecraft.world.entity.player.Player player) {
        return player.getData(DEFENSE_CARD_CONSUMED_BLESSING.get());
    }

    public static void setDefenseCardConsumedThisBlessing(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(DEFENSE_CARD_CONSUMED_BLESSING.get(), value);
    }

    // 肉弹战车立牌(pandaman)被动:吃汉堡累计的生命值上限加成(卸下立牌时清除)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> PANDAMAN_MAX_HEALTH_BONUS =
            ATTACHMENTS.register("pandaman_max_health_bonus", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    public static int getPandamanMaxHealthBonus(net.minecraft.world.entity.player.Player player) {
        return player.getData(PANDAMAN_MAX_HEALTH_BONUS.get());
    }

    public static void setPandamanMaxHealthBonus(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(PANDAMAN_MAX_HEALTH_BONUS.get(), Math.max(0, value));
    }

    // 嘲讽来源:肉弹战车立牌(pandaman)主动施加"嘲讽"的玩家 UUID
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Optional<UUID>>> PANDAMAN_TAUNT_SOURCE =
            ATTACHMENTS.register("pandaman_taunt_source", () -> AttachmentType.<Optional<UUID>>builder(Optional::empty)
                    .serialize(UUIDUtil.CODEC.optionalFieldOf("id").codec())
                    .build());

    public static Optional<UUID> getPandamanTauntSource(net.minecraft.world.entity.LivingEntity entity) {
        return entity.getData(PANDAMAN_TAUNT_SOURCE.get());
    }

    public static void setPandamanTauntSource(net.minecraft.world.entity.LivingEntity entity, Optional<UUID> value) {
        entity.setData(PANDAMAN_TAUNT_SOURCE.get(), value);
    }

    // 恋的规则书:是否已在当前世界为玩家发放过首次加入的规则书(仅服务端持久化,无需同步)
    // 「死亡重生保留」(2026-09-15 用户裁决,必须遵守):口径是「仅在玩家第一次进入世界发放一次,
    // 此后任何情况下都不再自动发放」⇒ 本键**必须随死亡复制**:否则死亡后新实体回默认 false,
    // 而发放挂在 PlayerLoggedInEvent ⇒ 下次登录必再发一本(实测缺陷,且 keepInventory 下书仍在背包)。
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> GUIDE_BOOK_GIVEN =
            ATTACHMENTS.register("guide_book_given", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .copyOnDeath()
                    .build());

    public static boolean isGuideBookGiven(net.minecraft.world.entity.player.Player player) {
        return player.getData(GUIDE_BOOK_GIVEN.get());
    }

    public static void setGuideBookGiven(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(GUIDE_BOOK_GIVEN.get(), value);
    }

    // 计时器守卫:本模组有时长效果的结束时刻记录(效果注册名 → 结束 tick + 重施加参数)。
    // 仅服务端使用,序列化持久化;由 EffectTimerGuard 维护,保证效果严格按 20t/s 流动。
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Map<String,
            com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry>>> EFFECT_TIMER_ENDS =
            ATTACHMENTS.register("effect_timer_ends",
                    () -> AttachmentType.<Map<String,
                            com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry>>builder(
                                    () -> new HashMap<>())
                            .serialize(Codec.unboundedMap(Codec.STRING,
                                    com.merlinkitsune.astral_dice.event.EffectTimerGuard.TimerEntry.CODEC))
                            .build());

    // === 新筹码:原初核心 / 电击手套 / 安全气囊 ===

    /** 赋能:下一层递减的到期时刻(gameTime;0 表示无计时器) */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> EMPOWER_DECAY_AT =
            ATTACHMENTS.register("empower_decay_at", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    /** 电击手套:本效果牌周期内是否已武装法伤扩散(触发后/周期结束清除) */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ELECTRIC_GLOVE_AOE =
            ATTACHMENTS.register("electric_glove_aoe", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .sync(ByteBufCodecs.BOOL)
                    .build());

    /** 安全气囊:触发冷却结束时刻(1:00;0 表示无冷却) */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> AIRBAG_COOLDOWN_END =
            ATTACHMENTS.register("airbag_cooldown_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    /** 电磁炮:雷击触发冷却结束时刻(1:00;0 表示无冷却;仅第二能力雷击,不影响充能攻击力加成) */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> RAILGUN_COOLDOWN_END =
            ATTACHMENTS.register("railgun_cooldown_end", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    /**
     * 游戏大师立牌(ren):最后一次「持有鼠鼠护盾」的世界时刻 —— 被动「鼠鼠救我」的 5 分钟计时基准。
     * 玩家级、**非同步**(仅服务端使用);跨重登继续累加(persisted 于 level.dat 的 Time)。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> REN_SHIELD_LAST_SEEN_TICK =
            ATTACHMENTS.register("ren_shield_last_seen_tick", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    /**
     * 游戏大师立牌(ren):授予护盾时玩家已有的吸收值(基线)。护盾只在此基础上 +20(10 黄心),
     * 清空时也只回收这 20 ⇒ 不吞掉金苹果/不死图腾等外部来源的吸收。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> REN_SHIELD_BASELINE_ABSORPTION =
            ATTACHMENTS.register("ren_shield_baseline_absorption", () -> AttachmentType.builder(() -> 0.0F)
                    .serialize(Codec.FLOAT)
                    .build());

    /**
     * 游戏大师立牌(ren):当前那份「抗性提升」是否由本护盾施加(放大器 0 且带此标记才在清空时移除,
     * 玩家的药水或更高等级一律保留)。玩家级、非同步。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> REN_SHIELD_OWN_RESISTANCE =
            ATTACHMENTS.register("ren_shield_own_resistance", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

    /**
     * 游戏大师立牌(ren):一次性反击层数(0/1)。获得鼠鼠护盾时 +1,被攻击时消耗 1 层并对攻击者
     * 注入一次现有反击伤害;护盾清空时归零。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> REN_COUNTER_CHARGES =
            ATTACHMENTS.register("ren_counter_charges", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .build());

    public static long getEmpowerDecayAt(net.minecraft.world.entity.player.Player player) {
        return player.getData(EMPOWER_DECAY_AT.get());
    }

    public static void setEmpowerDecayAt(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(EMPOWER_DECAY_AT.get(), Math.max(0, value));
    }

    public static boolean isElectricGloveAoe(net.minecraft.world.entity.player.Player player) {
        return player.getData(ELECTRIC_GLOVE_AOE.get());
    }

    public static void setElectricGloveAoe(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(ELECTRIC_GLOVE_AOE.get(), value);
    }

    public static long getAirbagCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(AIRBAG_COOLDOWN_END.get());
    }

    public static void setAirbagCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(AIRBAG_COOLDOWN_END.get(), Math.max(0, value));
    }

    public static long getRailgunCooldownEnd(net.minecraft.world.entity.player.Player player) {
        return player.getData(RAILGUN_COOLDOWN_END.get());
    }

    public static void setRailgunCooldownEnd(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(RAILGUN_COOLDOWN_END.get(), Math.max(0, value));
    }

    public static long getRenShieldLastSeenTick(net.minecraft.world.entity.player.Player player) {
        return player.getData(REN_SHIELD_LAST_SEEN_TICK.get());
    }

    public static void setRenShieldLastSeenTick(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(REN_SHIELD_LAST_SEEN_TICK.get(), Math.max(0L, value));
    }

    public static float getRenShieldBaselineAbsorption(net.minecraft.world.entity.player.Player player) {
        return player.getData(REN_SHIELD_BASELINE_ABSORPTION.get());
    }

    public static void setRenShieldBaselineAbsorption(net.minecraft.world.entity.player.Player player, float value) {
        player.setData(REN_SHIELD_BASELINE_ABSORPTION.get(), Math.max(0.0F, value));
    }

    public static boolean isRenShieldOwnResistance(net.minecraft.world.entity.player.Player player) {
        return player.getData(REN_SHIELD_OWN_RESISTANCE.get());
    }

    public static void setRenShieldOwnResistance(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(REN_SHIELD_OWN_RESISTANCE.get(), value);
    }

    public static int getRenCounterCharges(net.minecraft.world.entity.player.Player player) {
        return player.getData(REN_COUNTER_CHARGES.get());
    }

    public static void setRenCounterCharges(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(REN_COUNTER_CHARGES.get(), Math.max(0, value));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  风水师立牌(zhao)/符卡-福·祸 本批新增的玩家级状态(2026-09-26)
    //  键名与口径 = docs/features/fengshui-sign-spec.md §9.1(**冻结**)。
    //  同步策略:五个键一律**只** .serialize(...),**不** .sync(...) —— 它们的读取方全在服务端
    //  (玩家级 tick 的状态机/周期伤害、骰战攻击修饰器),玩家可见载体是**效果实例**(由原版效果
    //  同步包呈现),故不额外写包(同口径先例:healing_prev_blessing 的"仅服务端使用,无需同步")。
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 「白泽赐福」是否处于**生效期**(状态机真值,§4.4)。
     *
     * <p>与"效果实例是否存在"是两件事:效果实例是玩家可见载体(图标/时长),本键是服务端状态机的
     * 真值 —— 例如断线重登时效果被强制移除而本键被显式复位(§4.7),两者靠玩家级 tick 的自检对齐。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ZHAO_BLESSING_ACTIVE =
            ATTACHMENTS.register("zhao_blessing_active", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

    public static boolean isZhaoBlessingActive(net.minecraft.world.entity.player.Player player) {
        return player.getData(ZHAO_BLESSING_ACTIVE.get());
    }

    public static void setZhaoBlessingActive(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(ZHAO_BLESSING_ACTIVE.get(), value);
    }

    /**
     * 「白泽赐福」待跳过的骰神赐福**结束次数**(0/1;§4.5 两分支)。
     *
     * <p>语义(需求文本「持续到下一次骰神赐福结束」):
     * <ul>
     *   <li>施加时目标**不在**骰神赐福 ⇒ 写 0:待其触发骰神赐福、该次进度**结束后**移除赐福;</li>
     *   <li>施加时目标**已在**骰神赐福 ⇒ 写 1:**跳过当前这次**结束,等**下一次**骰神赐福结束后移除。</li>
     * </ul>
     * 由玩家级 tick 的**下降沿**每读到一次骰神赐福结束就读一次(§4.4):&gt;0 则减 1 并保留,否则移除。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> ZHAO_BLESSING_SKIP_CYCLES =
            ATTACHMENTS.register("zhao_blessing_skip_cycles", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .build());

    public static int getZhaoBlessingSkipCycles(net.minecraft.world.entity.player.Player player) {
        return player.getData(ZHAO_BLESSING_SKIP_CYCLES.get());
    }

    public static void setZhaoBlessingSkipCycles(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(ZHAO_BLESSING_SKIP_CYCLES.get(), Math.max(0, value));
    }

    /**
     * **上一 tick**是否处于骰神赐福(下降沿检测专用;§4.4 冻结决定)。
     *
     * <p>为什么不用 {@code MobEffectEvent.Expired}:该事件在"效果被外力移除(ModEffectRemoval /
     * 其它 mod / 死亡 / 重连清场)"时**不触发**(先例 {@code item/HealingManager} 明确不可依赖);
     * 而"上一 tick 有、这一 tick 没有"的下降沿把**两条结束路径统一**,且不会像同时订阅 Expired 那样
     * **重复消费**跳过计数。本键只有服务端 tick 读写,不显示,故不 .sync()。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ZHAO_PREV_BLESSING =
            ATTACHMENTS.register("zhao_prev_blessing", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

    public static boolean isZhaoPrevBlessing(net.minecraft.world.entity.player.Player player) {
        return player.getData(ZHAO_PREV_BLESSING.get());
    }

    public static void setZhaoPrevBlessing(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(ZHAO_PREV_BLESSING.get(), value);
    }

    /**
     * 「白泽赐福」期间由**溢出治疗**等量转化而来的攻击力加成(**整数**,§5.2/§5.3)。
     *
     * <p>写入方 = {@code item/sign/ZhaoSignItem#onLivingHeal}(溢出量 = 请求治疗量 − 实际恢复量,
     * 溢出 ≤ 0 时不写);读取方 = {@code combat/DiceCombatModifiers} 的攻击修饰器(加算项);
     * **唯一的回收点** = 「白泽赐福」被移除/复位时归 0 —— 不留残留,也不影响任何其它来源的攻击力。
     *
     * <p><b>取整余数</b>:溢出量按 {@code (int) Math.floor(...)} 整数化写入本键,余数留在
     * {@link #ZHAO_OVERFLOW_REMAINDER} 的浮点累加器里继续累积(§5.2「禁止无声丢数」的选项一)。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> ZHAO_OVERFLOW_BONUS =
            ATTACHMENTS.register("zhao_overflow_bonus", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .build());

    /** 溢出治疗取整后的**余数累加器**(&lt; 1 的尾数;见 {@link #ZHAO_OVERFLOW_BONUS}) */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> ZHAO_OVERFLOW_REMAINDER =
            ATTACHMENTS.register("zhao_overflow_remainder", () -> AttachmentType.builder(() -> 0.0F)
                    .serialize(Codec.FLOAT)
                    .build());

    public static int getZhaoOverflowBonus(net.minecraft.world.entity.player.Player player) {
        return player.getData(ZHAO_OVERFLOW_BONUS.get());
    }

    public static void setZhaoOverflowBonus(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(ZHAO_OVERFLOW_BONUS.get(), Math.max(0, value));
    }

    public static float getZhaoOverflowRemainder(net.minecraft.world.entity.player.Player player) {
        return player.getData(ZHAO_OVERFLOW_REMAINDER.get());
    }

    public static void setZhaoOverflowRemainder(net.minecraft.world.entity.player.Player player, float value) {
        player.setData(ZHAO_OVERFLOW_REMAINDER.get(), Math.max(0.0F, value));
    }

    /**
     * 把一次**溢出治疗量**累加进攻击力加成:整数部分进 {@link #ZHAO_OVERFLOW_BONUS},
     * 小数部分留在 {@link #ZHAO_OVERFLOW_REMAINDER}(下一次溢出可能与余数凑成新的整数点)
     * ⇒ 逐次治疗不会因反复向下取整而无声丢数。
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

    /** 一次性清空溢出治疗加成的两个键(整数部分 + 余数累加器) */
    public static void clearZhaoOverflowBonus(net.minecraft.world.entity.player.Player player) {
        setZhaoOverflowBonus(player, 0);
        setZhaoOverflowRemainder(player, 0.0F);
    }

    /**
     * 「厄运」(符卡-祸 的镜像效果)下一次周期伤害的**绝对结算刻**(gameTime;0 = 未起算)。
     *
     * <p><b>计时器与结算分离</b>(验收第 6 条):该键只在「持有张数 0 → &gt;0」时起算一次,此后
     * **只由结算推进**(每次结算后 += 2:00),持卡张数在 &gt;0 区间内的增减**一律不写本键**
     * —— 所以张数变化不会重置/推迟计时器,而每次结算造成的伤害取「**结算时刻**的当前张数」。
     * 张数归 0 时属于"整段清除"(同时移除厄运效果),此时把本键一并归 0(§9.2)。
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> HUO_CARD_NEXT_DAMAGE_TICK =
            ATTACHMENTS.register("huo_card_next_damage_tick", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    public static long getHuoCardNextDamageTick(net.minecraft.world.entity.player.Player player) {
        return player.getData(HUO_CARD_NEXT_DAMAGE_TICK.get());
    }

    public static void setHuoCardNextDamageTick(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(HUO_CARD_NEXT_DAMAGE_TICK.get(), Math.max(0L, value));
    }
}
