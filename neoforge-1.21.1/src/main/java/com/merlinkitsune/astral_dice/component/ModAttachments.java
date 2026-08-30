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

    // 伤害效果牌:当前生效的远程/魔法攻击追加伤害数值(由伤害效果牌使用后设置)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> DAMAGE_EFFECT_BONUS =
            ATTACHMENTS.register("damage_effect_bonus", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    // 效果牌:当前周期内已连续出牌数
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> EFFECT_CARD_PLAY_COUNT =
            ATTACHMENTS.register("effect_card_play_count", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    // 忍者立牌(komachi)主动:临时出牌数+1 标记(仅当前效果牌周期内生效,周期归零时清除)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> KOMACHI_EXTRA_PLAY_ACTIVE =
            ATTACHMENTS.register("komachi_extra_play_active", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .sync(ByteBufCodecs.BOOL)
                    .build());

    public static boolean isKomachiExtraPlayActive(net.minecraft.world.entity.player.Player player) {
        return player.getData(KOMACHI_EXTRA_PLAY_ACTIVE.get());
    }

    public static void setKomachiExtraPlayActive(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(KOMACHI_EXTRA_PLAY_ACTIVE.get(), value);
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
                    .build());

    // 忍者立牌(komachi):最后一张使用的效果牌类型
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> KOMACHI_LAST_CARD =
            ATTACHMENTS.register("komachi_last_card", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .build());

    // 忍者立牌(komachi):效果牌伤害增益(每使用 3 张效果牌 +1,上限见 GameplayConstants.KOMACHI_DAMAGE_BONUS_MAX)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> KOMACHI_DAMAGE_BONUS =
            ATTACHMENTS.register("komachi_damage_bonus", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

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

    // 调查员立牌(rin):已使用的活体书页数量(活体书页伤害永久+1 的来源,移除立牌后重置)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> RIN_PAGES =
            ATTACHMENTS.register("rin_pages", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
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

    public static int getDamageEffectBonus(net.minecraft.world.entity.player.Player player) {
        return player.getData(DAMAGE_EFFECT_BONUS.get());
    }

    public static void setDamageEffectBonus(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(DAMAGE_EFFECT_BONUS.get(), value);
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

    // 立牌主动技能"等待目标释放"状态类型:1=占星师(虚弱印记) 2=秘密侦探(隐匿调查);0=无等待
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> SIGN_READY_TYPE =
            ATTACHMENTS.register("sign_ready_type", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.INT)
                    .build());

    // 立牌主动技能等待到期时刻(0 表示无等待)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> SIGN_READY_EXPIRE =
            ATTACHMENTS.register("sign_ready_expire", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(ByteBufCodecs.VAR_LONG)
                    .build());

    public static int getSignReadyType(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_READY_TYPE.get());
    }

    public static void setSignReadyType(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(SIGN_READY_TYPE.get(), value);
    }

    public static long getSignReadyExpire(net.minecraft.world.entity.player.Player player) {
        return player.getData(SIGN_READY_EXPIRE.get());
    }

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

    // 诅咒之剑筹码:累计击杀 20 血以上敌对目标获得的攻击力加成(移除筹码/死亡清除)
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

    // 探天卫星筹码:当前效果牌出牌轮次是否已触发过"使用轨道炮后出牌数+1"(每轮最多一次)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SATELLITE_PLAY_BONUS =
            ATTACHMENTS.register("satellite_play_bonus", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .sync(ByteBufCodecs.BOOL)
                    .build());

    // 骇客立牌:被动类型(0=无,1=攻击,2=防御)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> NANCY_LU_PASSIVE_TYPE =
            ATTACHMENTS.register("nancy_lu_passive_type", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
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

    // 骇客立牌:主动无敌结束时刻
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> NANCY_LU_INVULNERABLE_UNTIL =
            ATTACHMENTS.register("nancy_lu_invulnerable_until", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    // 骇客立牌:主动"完全隐身"结束时刻
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> NANCY_LU_HIDDEN_UNTIL =
            ATTACHMENTS.register("nancy_lu_hidden_until", () -> AttachmentType.builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .build());

    // 看板立牌:被动累计获得的星币数(每 25 个星币获得一个随机筹码)
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> MIMI_STAR_COIN_COUNTER =
            ATTACHMENTS.register("mimi_star_coin_counter", () -> AttachmentType.builder(() -> 0)
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

    public static long getNancyLuInvulnerableUntil(net.minecraft.world.entity.player.Player player) {
        return player.getData(NANCY_LU_INVULNERABLE_UNTIL.get());
    }

    public static void setNancyLuInvulnerableUntil(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(NANCY_LU_INVULNERABLE_UNTIL.get(), Math.max(0, value));
    }

    public static long getNancyLuHiddenUntil(net.minecraft.world.entity.player.Player player) {
        return player.getData(NANCY_LU_HIDDEN_UNTIL.get());
    }

    public static void setNancyLuHiddenUntil(net.minecraft.world.entity.player.Player player, long value) {
        player.setData(NANCY_LU_HIDDEN_UNTIL.get(), Math.max(0, value));
    }

    public static int getMimiStarCoinCounter(net.minecraft.world.entity.player.Player player) {
        return player.getData(MIMI_STAR_COIN_COUNTER.get());
    }

    public static void setMimiStarCoinCounter(net.minecraft.world.entity.player.Player player, int value) {
        player.setData(MIMI_STAR_COIN_COUNTER.get(), Math.max(0, value));
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

    // 战斗爽·扩散待命:主动消耗 2 层养精蓄锐后置位,下次骰神赐福期间启用
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> FEN_CLEAVE_PENDING =
            ATTACHMENTS.register("fen_cleave_pending", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

    // 战斗爽·扩散生效:本次骰神赐福期间,每次攻击将总伤害的 80% 扩散给目标 6 格内敌对目标,赐福结束清除
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> FEN_CLEAVE_ACTIVE =
            ATTACHMENTS.register("fen_cleave_active", () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build());

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

    public static boolean isFenCleavePending(net.minecraft.world.entity.player.Player player) {
        return player.getData(FEN_CLEAVE_PENDING.get());
    }

    public static void setFenCleavePending(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(FEN_CLEAVE_PENDING.get(), value);
    }

    public static boolean isFenCleaveActive(net.minecraft.world.entity.player.Player player) {
        return player.getData(FEN_CLEAVE_ACTIVE.get());
    }

    public static void setFenCleaveActive(net.minecraft.world.entity.player.Player player, boolean value) {
        player.setData(FEN_CLEAVE_ACTIVE.get(), value);
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
}
