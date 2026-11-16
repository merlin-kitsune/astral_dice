package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.entity.InkShadowPortalEntity;
import com.merlinkitsune.astral_dice.event.PlayerEffectProtection;
import com.merlinkitsune.starengine.event.ModEffectRemoval;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * 小猎手立牌「暗影之门 / 双生之影」的服务端状态机（1.20.1 等价实现）。
 *
 * <p>所有状态存放在玩家附件（仅服务端、不参与同步）：pending 起始门 uuid / 维度 / 窗口截止刻、
 * 放置窗口内被强制锁定的快捷栏槽位、被动层数与「主人名下存活门对」的到期刻列表。
 *
 * <p>生命周期：
 * <ol>
 *   <li>首门：生成 PENDING 传送门实体 + 写 pending + 加移速 + 锁快捷栏；</li>
 *   <li>终点门：取起始门实体 → 双向 {@code activate} 配对（含跨维度坐标）→ 清 pending / 移速 / 槽位锁；</li>
 *   <li>窗口超时 / 死亡 / 登出：销毁起始门并清空全部临时状态（{@link #voidPortal}）。</li>
 * </ol>
 *
 * <p><b>1.20.1 平台差异</b>：{@code ADD_VALUE → ADDITION}、{@code ADD_MULTIPLIED_TOTAL → MULTIPLY_TOTAL}、
 * 修饰符 UUID 由 {@link SignAttributeUtil} 按 key 派生；{@code ModEffects.INKSHADOW_STACK} 是
 * {@code RegistryObject} ⇒ 引用处 {@code .get()}。
 */
public final class InkShadowSignManager {

    /** 立牌物品注册 id */
    public static final String SIGN_ID = InkShadowSignItem.SIGN_ID;

    private InkShadowSignManager() {
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  放置
    // ══════════════════════════════════════════════════════════════════════════

    /** 放置窗口的截止刻（未进入窗口时为 0）。 */
    public static long pendingDeadline(Player player) {
        return player == null ? 0L : ModAttachments.getInkShadowPendingDeadline(player);
    }

    /**
     * 主动技能键的**前置钩子**（由 {@code ModNetwork.SignActivateMessage#handle} 在
     * {@code BaseSignItem.performSkillForCurio} 之前调用）。
     *
     * <p>窗口内按第二下时 {@code isSignActiveLocked} 会因锁定态把按键拦死，故这里绕过整条
     * {@code performSkill} 直接放终点门：一次主动只结算一次（不重复触发风扇筹码 / 电流核心 /
     * {@code SignActiveTriggeredEvent}）。返回 true 表示本按键已被本立牌消费。
     */
    public static boolean tryPlaceEndFromKey(ServerPlayer player) {
        if (player == null || !hasPending(player)) return false;
        if (!InkShadowSignItem.isEquipped(player)) {
            voidPortal(player);
            return true;
        }
        BlockPos pos = InkShadowSignItem.resolvePlacePos(player);
        if (pos == null) {
            SignActionBar.sendSignActionBarColored(player, ChatFormatting.RED, "msg.astral_dice.inkshadow_no_ground");
            return true;
        }
        placeEndPortal((ServerLevel) player.level(), player, pos, player.level().getGameTime());
        return true;
    }

    /** 一次按键：窗口内 ⇒ 放终点门；否则 ⇒ 放起始门。返回 false 时本次主动不结算（不进冷却）。 */
    public static boolean placePortal(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (player == null || level == null || pos == null) return false;
        long now = level.getGameTime();
        if (hasPending(player)) {
            return placeEndPortal(level, player, pos, now);
        }
        // 窗口已过期但 tick 还没清掉残留 pending：先销毁旧起始门，本次按新的起始门处理
        if (!ModAttachments.getInkShadowPendingUuid(player).isEmpty()) {
            voidPortal(player);
        }
        return placeStartPortal(level, player, pos, now);
    }

    private static boolean placeStartPortal(ServerLevel level, ServerPlayer player, BlockPos pos, long now) {
        InkShadowPortalEntity portal = new InkShadowPortalEntity(level, player.getUUID());
        portal.setPending(now + InkShadowSignItem.WINDOW_TICKS);
        // 实体锚在地块底面（pos.getY()），模型由渲染器的 GROUND_LIFT 抬到正好贴地
        portal.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        portal.setYRot(player.getYRot());
        level.addFreshEntity(portal);
        // 放置即钉区块：门可能在别的维度/远处，没有玩家 ticket 时连第一次 tick 都跑不到，
        // 区块一卸载门就丢（2026-11-11 跨维度实机）。实体每 tick 也会再钉一次，到期/作废时释放。
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);

        ModAttachments.setInkShadowPendingUuid(player, portal.getUUID().toString());
        ModAttachments.setInkShadowPendingDim(player, level.dimension().location().toString());
        ModAttachments.setInkShadowPendingDeadline(player, now + InkShadowSignItem.WINDOW_TICKS);
        ModAttachments.setInkShadowPendingPos(player, pos.asLong());
        // 放置窗口内锁死快捷栏：切武器走的是 selected 槽位，强行回写即可（背包重排后下一 tick 继续回写）
        ModAttachments.setInkShadowLockedSlot(player, player.getInventory().selected);
        SignAttributeUtil.apply(player, Attributes.MOVEMENT_SPEED, InkShadowSignItem.SPEED_MODIFIER_KEY,
                InkShadowSignItem.SPEED_AMPLIFIER, AttributeModifier.Operation.MULTIPLY_TOTAL);
        // 本 tick 的唯一 actionbar 由 event/InkShadowSignEvents#onSignActiveTriggered 走公用
        // SignActiveTriggeredEvent 通道发送（此处再发会与默认「主动技能已启动」争同一槽位）
        return true;
    }

    private static boolean placeEndPortal(ServerLevel level, ServerPlayer player, BlockPos pos, long now) {
        InkShadowPortalEntity start = resolvePendingPortal(player);
        if (start == null) {
            // 起始门所在区块未加载 ⇒ 无法建立双向引用；保留窗口让玩家回到起始门附近重试
            SignActionBar.sendSignActionBarColored(player, ChatFormatting.RED, "msg.astral_dice.inkshadow_no_start");
            return false;
        }
        InkShadowPortalEntity end = new InkShadowPortalEntity(level, player.getUUID());
        end.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        end.setYRot(player.getYRot());
        level.addFreshEntity(end);
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);

        long expireTick = now + InkShadowSignItem.PAIR_LIFETIME_TICKS;
        String startDim = start.level().dimension().location().toString();
        String endDim = level.dimension().location().toString();
        Vec3 startPos = new Vec3(start.getX(), start.getY(), start.getZ());
        Vec3 endPos = new Vec3(end.getX(), end.getY(), end.getZ());
        start.activate(expireTick, end.getUUID(), endDim, endPos);
        end.activate(expireTick, start.getUUID(), startDim, startPos);
        registerLivePair(player, expireTick);

        clearPending(player);
        SignActionBar.sendSignActionBar(player, "msg.astral_dice.inkshadow_place_end");
        return true;
    }

    /** 窗口/死亡/登出：销毁起始门并清空一切临时状态（幂等）。 */
    public static void voidPortal(Player player) {
        if (player == null) return;
        InkShadowPortalEntity start = resolvePendingPortal(player);
        if (start != null && !start.isRemoved()) start.discard();
        clearPending(player);
    }

    private static void clearPending(Player player) {
        ModAttachments.setInkShadowPendingUuid(player, "");
        ModAttachments.setInkShadowPendingDim(player, "");
        ModAttachments.setInkShadowPendingDeadline(player, 0L);
        ModAttachments.setInkShadowPendingPos(player, Long.MIN_VALUE);
        ModAttachments.setInkShadowLockedSlot(player, -1);
        SignAttributeUtil.remove(player, Attributes.MOVEMENT_SPEED, InkShadowSignItem.SPEED_MODIFIER_KEY);
    }

    /** pending 是否仍在 30s 窗口内（判定含截止刻，服务端/客户端同值）。 */
    public static boolean hasPending(Player player) {
        if (player == null) return false;
        if (ModAttachments.getInkShadowPendingUuid(player).isEmpty()) return false;
        long deadline = ModAttachments.getInkShadowPendingDeadline(player);
        return deadline > 0L && player.level().getGameTime() < deadline;
    }

    private static InkShadowPortalEntity resolvePendingPortal(Player player) {
        if (player == null) return null;
        String uuid = ModAttachments.getInkShadowPendingUuid(player);
        if (uuid.isEmpty()) return null;
        MinecraftServer server = player.getServer();
        if (server == null) return null;
        ServerLevel level = dimensionLevel(server, ModAttachments.getInkShadowPendingDim(player));
        if (level == null) return null;
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
        InkShadowPortalEntity portal = level.getEntity(id) instanceof InkShadowPortalEntity found ? found : null;
        if (portal != null) return portal;
        // 起点门所在区块可能已卸载(玩家跨维度 / 远走)：按记录的坐标强制加载区块后再解析一次
        long packed = ModAttachments.getInkShadowPendingPos(player);
        if (packed == Long.MIN_VALUE) return null;
        BlockPos pendingPos = BlockPos.of(packed);
        level.getChunk(pendingPos.getX() >> 4, pendingPos.getZ() >> 4);
        return level.getEntity(id) instanceof InkShadowPortalEntity reloaded ? reloaded : null;
    }

    private static ServerLevel dimensionLevel(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  每 tick（服务端）
    // ══════════════════════════════════════════════════════════════════════════

    public static void tickPlayer(ServerPlayer player) {
        if (player == null || player.level().isClientSide()) return;
        // 1) 窗口超时：销毁起始门、解除移速与槽位锁
        if (!ModAttachments.getInkShadowPendingUuid(player).isEmpty() && !hasPending(player)) {
            voidPortal(player);
            return;
        }
        // 2) 窗口内强锁快捷栏（玩家可改 selected，但每 tick 回写）；未佩戴 ⇒ 窗口作废
        if (hasPending(player)) {
            if (!InkShadowSignItem.isEquipped(player)) {
                voidPortal(player);
                return;
            }
            int locked = ModAttachments.getInkShadowLockedSlot(player);
            int size = player.getInventory().items.size();
            if (locked >= 0 && locked < size && locked != player.getInventory().selected) {
                player.getInventory().selected = locked;
                // 必须通知客户端，否则 HUD 槽位与真实手持不一致
                player.connection.send(new ClientboundSetCarriedItemPacket(locked));
            }
        }
        // 3) 被动层数到期/离身时撤掉攻击修饰符（幂等）
        refreshStacks(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「双生之影」
    // ══════════════════════════════════════════════════════════════════════════

    /** 门主人 +1 层：层数不再按时间过期，只要主人名下还有存活门对就持续生效（用户 2026-11-10 口径）。 */
    public static void addStack(Player owner) {
        if (owner == null || owner.level().isClientSide()) return;
        if (!InkShadowSignItem.isEquipped(owner)) return;
        int stacks = ModAttachments.getInkShadowStacks(owner) + 1;
        ModAttachments.setInkShadowStacks(owner, stacks);
        refreshStacks(owner);
    }

    /** 按附件真值重算攻击加成与 HUD 指示器（幂等；没有存活门对 / 立牌离身 ⇒ 移除修饰符与图标）。 */
    public static void refreshStacks(Player player) {
        if (player == null || player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        int stacks = ModAttachments.getInkShadowStacks(player);
        long deadline = livePairDeadline(player);
        if (stacks <= 0 || deadline <= 0L) {
            // 门对全部到期 ⇒ 层数清零：被动只在「有属于自己的传送门存在」时生效
            ModAttachments.setInkShadowStacks(player, 0);
            SignAttributeUtil.remove(player, Attributes.ATTACK_DAMAGE, InkShadowSignItem.ATTACK_MODIFIER_KEY);
            refreshStackHud(player, 0, 0L, now);
            return;
        }
        if (!InkShadowSignItem.isEquipped(player)) {
            // 离身只是暂不生效；门对还在，重新装回即恢复当前层数
            SignAttributeUtil.remove(player, Attributes.ATTACK_DAMAGE, InkShadowSignItem.ATTACK_MODIFIER_KEY);
            refreshStackHud(player, 0, 0L, now);
            return;
        }
        SignAttributeUtil.apply(player, Attributes.ATTACK_DAMAGE, InkShadowSignItem.ATTACK_MODIFIER_KEY,
                InkShadowSignItem.STACK_ATTACK_BONUS * stacks, AttributeModifier.Operation.ADDITION);
        refreshStackHud(player, stacks, deadline, now);
    }

    /** 门对建立：登记到期刻（同一主人可同时存在多对，逐条记录，过期逐条剪掉）。 */
    private static void registerLivePair(Player owner, long expireTick) {
        List<Long> expiries = parseExpiries(ModAttachments.getInkShadowPairExpiries(owner));
        expiries.add(expireTick);
        writeExpiries(owner, expiries);
    }

    /**
     * 属于该玩家的存活门对的最晚到期刻（0 = 一对都没有）。
     * 顺手把已过期的记录剪掉，因此不依赖门实体反过来通知：登出、重启、跨维度都自洽。
     */
    public static long livePairDeadline(Player player) {
        if (player == null || player.level().isClientSide()) return 0L;
        long now = player.level().getGameTime();
        List<Long> all = parseExpiries(ModAttachments.getInkShadowPairExpiries(player));
        List<Long> alive = new ArrayList<>();
        long deadline = 0L;
        for (long expiry : all) {
            if (expiry > now) {
                alive.add(expiry);
                if (expiry > deadline) deadline = expiry;
            }
        }
        if (alive.size() != all.size()) writeExpiries(player, alive);
        return deadline;
    }

    private static List<Long> parseExpiries(String raw) {
        List<Long> expiries = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return expiries;
        for (String part : raw.split(",")) {
            if (part.isEmpty()) continue;
            try {
                long expiry = Long.parseLong(part.trim());
                if (expiry > 0L) expiries.add(expiry);
            } catch (NumberFormatException ignored) {
                // 脏数据直接丢弃
            }
        }
        return expiries;
    }

    private static void writeExpiries(Player player, List<Long> expiries) {
        StringJoiner joiner = new StringJoiner(",");
        for (long expiry : expiries) joiner.add(Long.toString(expiry));
        ModAttachments.setInkShadowPairExpiries(player, joiner.toString());
    }

    /**
     * 被动层数的 HUD 指示器：图标 = 小猎手立牌贴图（{@code textures/mob_effect/inkshadow_stack.png}），
     * amplifier = 层数 - 1，持续时间 = 最晚存活门对的剩余时间；无门对 / 离身 ⇒ 摘掉图标。
     */
    private static void refreshStackHud(Player player, int stacks, long expire, long now) {
        boolean present = player.hasEffect(ModEffects.INKSHADOW_STACK.get());
        int duration = stacks <= 0 || expire <= now ? 0 : (int) Math.max(1L, expire - now);
        if (duration <= 0) {
            if (present) ModEffectRemoval.remove(player, ModEffects.INKSHADOW_STACK.get());
            return;
        }
        MobEffectInstance current = player.getEffect(ModEffects.INKSHADOW_STACK.get());
        int amplifier = stacks - 1;
        // 层数不变时只让原效果自然倒计时；差 1 秒以上才重挂，避免每 tick 刷包
        if (current != null && current.getAmplifier() == amplifier && current.getDuration() >= duration - 20) {
            return;
        }
        if (current != null) ModEffectRemoval.remove(player, ModEffects.INKSHADOW_STACK.get());
        PlayerEffectProtection.apply(player, new MobEffectInstance(
                ModEffects.INKSHADOW_STACK.get(), duration, amplifier, false, false, true));
    }

    /** 卸下立牌：放置窗口作废 + 被动层数清零（层数是「门主人」身份绑定，离身即失效）。 */
    public static void onUnequip(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (hasPending(player)) clearPending(player);
        if (ModAttachments.getInkShadowStacks(player) > 0) {
            ModAttachments.setInkShadowStacks(player, 0);
        }
        SignAttributeUtil.remove(player, Attributes.ATTACK_DAMAGE, InkShadowSignItem.ATTACK_MODIFIER_KEY);
        refreshStackHud(player, 0, 0L, player.level().getGameTime());
    }
}
