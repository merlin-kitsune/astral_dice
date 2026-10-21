package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.component.AttachedDataKey;
import com.merlinkitsune.astral_dice.component.ClientAstralData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.20.1 Forge 网络层:SimpleChannel 承载 1.21 分支的 4 个载荷
 * (伤害数字/动作栏/立牌主动/卡牌栏打开)+ 附件同步消息。
 * 静态发送助手对应 1.21 的 PacketDistributor.sendTo* 调用面。
 */
public final class ModNetwork {
    /**
     * 版本互通门槛(见 AGENTS.md):通道协议版本号 = mod_version 的 major.minor(自动派生,禁止硬编码)。
     * FML 登录握手会交换本通道版本号并由两端各自的谓词校验,不匹配即拒绝连接;
     * 同二号位的 1.2.x ↔ 1.2.y 互通号相同,照常放行。
     */
    private static final String PROTOCOL_VERSION = VersionGate.interopVersion();
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new net.minecraft.resources.ResourceLocation(AstralDiceMod.MODID, "main"),
            () -> PROTOCOL_VERSION, VersionGate::accepts, VersionGate::accepts);

    private ModNetwork() {
    }

    /** 在 FMLCommonSetupEvent.enqueueWork 中调用。 */
    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, DamageNumberMessage.class,
                DamageNumberMessage::encode, DamageNumberMessage::decode, DamageNumberMessage::handle);
        CHANNEL.registerMessage(id++, ActionBarMessage.class,
                ActionBarMessage::encode, ActionBarMessage::decode, ActionBarMessage::handle);
        CHANNEL.registerMessage(id++, SignActivateMessage.class,
                SignActivateMessage::encode, SignActivateMessage::decode, SignActivateMessage::handle);
        CHANNEL.registerMessage(id++, OpenCardInventoryMessage.class,
                OpenCardInventoryMessage::encode, OpenCardInventoryMessage::decode, OpenCardInventoryMessage::handle);
        CHANNEL.registerMessage(id++, AttachmentSyncMessage.class,
                AttachmentSyncMessage::encode, AttachmentSyncMessage::decode, AttachmentSyncMessage::handle);
        CHANNEL.registerMessage(id++, TargetSelectStartMessage.class,
                TargetSelectStartMessage::encode, TargetSelectStartMessage::decode, TargetSelectStartMessage::handle);
        CHANNEL.registerMessage(id++, TargetSelectConfirmMessage.class,
                TargetSelectConfirmMessage::encode, TargetSelectConfirmMessage::decode, TargetSelectConfirmMessage::handle);
        CHANNEL.registerMessage(id++, TargetSelectCancelMessage.class,
                TargetSelectCancelMessage::encode, TargetSelectCancelMessage::decode, TargetSelectCancelMessage::handle);
        CHANNEL.registerMessage(id++, EnderDieTotemMessage.class,
                EnderDieTotemMessage::encode, EnderDieTotemMessage::decode, EnderDieTotemMessage::handle);
        CHANNEL.registerMessage(id++, StarCoinWalletMessage.class,
                StarCoinWalletMessage::encode, StarCoinWalletMessage::decode, StarCoinWalletMessage::handle);
        CHANNEL.registerMessage(id++, StarCoinBalanceMessage.class,
                StarCoinBalanceMessage::encode, StarCoinBalanceMessage::decode, StarCoinBalanceMessage::handle);
        CHANNEL.registerMessage(id++, RenShieldStateMessage.class,
                RenShieldStateMessage::encode, RenShieldStateMessage::decode, RenShieldStateMessage::handle);
        // 卡牌商店(1.20.1 移植):id 12 = S→C 候选名单、id 13 = C→S 确认转账。
        // 两条一律**追加在末尾**,不得插进既有 payload 的顺序里(顺序即协议,改序会让旧客户端错包)。
        CHANNEL.registerMessage(id++, CardShopTransferListMessage.class,
                CardShopTransferListMessage::encode, CardShopTransferListMessage::decode, CardShopTransferListMessage::handle);
        CHANNEL.registerMessage(id++, CardShopTransferMessage.class,
                CardShopTransferMessage::encode, CardShopTransferMessage::decode, CardShopTransferMessage::handle);
        // 拖拽购买(1.20.1 移植):id 14 = C→S「货架索引 + 目标物品格索引」。
        // 同样**只能追加在末尾**:顺序即协议,插队会让旧客户端错包。
        CHANNEL.registerMessage(id++, CardShopBuyMessage.class,
                CardShopBuyMessage::encode, CardShopBuyMessage::decode, CardShopBuyMessage::handle);
        // 拒绝信号(1.20.1 移植):id 15 = S→C「本次请求被拒 + 原因」。
        // **只能追加在末尾**:顺序即协议,插队会让旧客户端错包。
        CHANNEL.registerMessage(id++, CardShopDenyMessage.class,
                CardShopDenyMessage::encode, CardShopDenyMessage::decode, CardShopDenyMessage::handle);
        // 事件效果卡（S2C，2026-10-10）：与 1.21.1 的 EventCardPayload 同语义；**追加在末尾**（顺序即协议）。
        CHANNEL.registerMessage(id++, EventCardMessage.class,
                EventCardMessage::encode, EventCardMessage::decode, EventCardMessage::handle);
        // 大侦探立牌主动技能「事件选择界面」(2026-10-11 移植):id 17 = S→C 打开界面(含 2 选项)、
        // id 18 = C→S 确认选项、id 19 = S→C 关闭界面、id 20 = S→C 倒计时同步。
        // 四条一律**追加在末尾**,不得插进既有 payload 的顺序里(顺序即协议,改序会让旧客户端错包)。
        CHANNEL.registerMessage(id++, EventChoiceStartMessage.class,
                EventChoiceStartMessage::encode, EventChoiceStartMessage::decode, EventChoiceStartMessage::handle);
        CHANNEL.registerMessage(id++, EventChoiceConfirmMessage.class,
                EventChoiceConfirmMessage::encode, EventChoiceConfirmMessage::decode, EventChoiceConfirmMessage::handle);
        CHANNEL.registerMessage(id++, EventChoiceCloseMessage.class,
                EventChoiceCloseMessage::encode, EventChoiceCloseMessage::decode, EventChoiceCloseMessage::handle);
        CHANNEL.registerMessage(id++, EventChoiceTickMessage.class,
                EventChoiceTickMessage::encode, EventChoiceTickMessage::decode, EventChoiceTickMessage::handle);
        // 筹码商店(1.20.1 移植):id 21 = C→S 购买第 N 个候选、id 22 = C→S 刷新候选、id 23 = S→C 拒绝信号。
        // 三条一律**追加在末尾**,不得插进既有 payload 的顺序里(顺序即协议,改序会让旧客户端错包)。
        CHANNEL.registerMessage(id++, ChipShopBuyPayload.class,
                ChipShopBuyPayload::encode, ChipShopBuyPayload::decode, ChipShopBuyPayload::handle);
        CHANNEL.registerMessage(id++, ChipShopRefreshPayload.class,
                ChipShopRefreshPayload::encode, ChipShopRefreshPayload::decode, ChipShopRefreshPayload::handle);
        CHANNEL.registerMessage(id++, ChipShopDenyPayload.class,
                ChipShopDenyPayload::encode, ChipShopDenyPayload::decode, ChipShopDenyPayload::handle);
        // 通用选卡界面(2026-10-17):id 24 = C→S 确认(只带会话 token)、id 25 = S→C token 作用域关闭。
        // 同样**只能追加在末尾**:顺序即协议,插队会让旧客户端错包。
        CHANNEL.registerMessage(id++, CardChoiceConfirmMessage.class,
                CardChoiceConfirmMessage::encode, CardChoiceConfirmMessage::decode, CardChoiceConfirmMessage::handle);
        CHANNEL.registerMessage(id++, CardChoiceCloseMessage.class,
                CardChoiceCloseMessage::encode, CardChoiceCloseMessage::decode, CardChoiceCloseMessage::handle);
        // 光束 FX（激光 / 轨道炮，1.20.1 移植，2026-10-21）：id 26 = S→C 一条已冻结两端点的光线。
        // **只能追加在末尾**：顺序即协议，插队会让旧客户端错包。
        CHANNEL.registerMessage(id++, BeamFxPayload.class,
                BeamFxPayload::encode, BeamFxPayload::decode, BeamFxPayload::handle);
    }

    // === 发送助手(对应 1.21 PacketDistributor 静态方法) ===

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToPlayersTrackingEntity(Entity entity, Object message) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), message);
    }

    /**
     * 全服广播(含所有维度)。
     *
     * <p>鼠鼠护盾可见性用它而不是追踪范围:entityId 在**全服**唯一,列表落在别的维度不会命中
     * 任何已加载玩家 ⇒ 无需按维度/追踪范围裁剪;而按追踪范围裁剪反而会在「登录时目标在视野外」
     * 漏发。详见 {@code combat.RenShieldVisibility} 类头。
     */
    public static void sendToAllPlayers(Object message) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    // === 附件同步 ===

    /** 单键同步(AttachedDataKey.set 服务端写入后调用)。 */
    public static <T> void syncAttachment(ServerPlayer player, AttachedDataKey<T> key, net.minecraft.nbt.Tag tag) {
        CompoundTag payload = new CompoundTag();
        if (tag == null) {
            // 服务端已移除该键(set 中编码失败走 store.remove):必须下发**显式默认值**,
            // 否则客户端缓存里仍是旧值(与全量快照 syncSnapshot 同一口径)。
            tag = key.defaultRawTag();
        }
        if (tag != null) {
            payload.put(key.name(), tag);
        }
        sendToPlayer(player, new AttachmentSyncMessage(payload));
    }

    /**
     * synced 键全量快照(登录/重生/切维度)。
     *
     * <p>服务端缺失的键**必须下发显式默认值**:客户端缓存是静态字段(不随客户端玩家实体重建),
     * 只发"存在的键"会让上一会话/上一个世界的残留值继续生效(tooltip 显示上一局计数,而服务端已归零)。
     */
    public static void syncSnapshot(ServerPlayer player, List<AttachedDataKey<?>> keys) {
        CompoundTag payload = new CompoundTag();
        for (AttachedDataKey<?> key : keys) {
            net.minecraft.nbt.Tag tag = key.readRawTag(player);
            if (tag == null) {
                tag = key.defaultRawTag();
            }
            if (tag != null) {
                payload.put(key.name(), tag);
            }
        }
        sendToPlayer(player, new AttachmentSyncMessage(payload));
    }

    /** 客户端收到同步包:合并进本地缓存。 */
    public static class AttachmentSyncMessage {
        private final CompoundTag payload;

        public AttachmentSyncMessage(CompoundTag payload) {
            this.payload = payload;
        }

        public static void encode(AttachmentSyncMessage msg, FriendlyByteBuf buf) {
            buf.writeNbt(msg.payload);
        }

        public static AttachmentSyncMessage decode(FriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            return new AttachmentSyncMessage(tag != null ? tag : new CompoundTag());
        }

        public static void handle(AttachmentSyncMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                for (String key : msg.payload.getAllKeys()) {
                    ClientAstralData.put(key, msg.payload.get(key));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // === 伤害数字(S→C) ===

    /**
     * 跳数字(S→C)。
     *
     * <p>2026-09-26 起新增 <b>冻结坐标</b>({@code x/y/z}):由服务端
     * {@code combat/DamageNumberAggregator} 在命中那一刻取好,客户端不再自行读实体坐标 ——
     * ① 满足「数字不跟随目标跳动」;② 聚合包在 tick 末尾才发,击杀那一下目标可能已被客户端移除,
     * 若靠客户端取实体就会整条丢数字。
     *
     * <p>⚠️ 与 neo 两线的 {@code network/DamageNumberPayload}(record + StreamCodec)**不可互抄**,
     * 本线走 SimpleChannel 的 encode/decode。
     */
    public static class DamageNumberMessage {
        private final int entityId;
        private final int bonusDamage;
        private final int color;
        private final float x;
        private final float y;
        private final float z;

        public DamageNumberMessage(int entityId, int bonusDamage, int color, float x, float y, float z) {
            this.entityId = entityId;
            this.bonusDamage = bonusDamage;
            this.color = color;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public static void encode(DamageNumberMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.entityId);
            buf.writeVarInt(msg.bonusDamage);
            buf.writeInt(msg.color);
            buf.writeFloat(msg.x);
            buf.writeFloat(msg.y);
            buf.writeFloat(msg.z);
        }

        public static DamageNumberMessage decode(FriendlyByteBuf buf) {
            return new DamageNumberMessage(buf.readVarInt(), buf.readVarInt(), buf.readInt(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        public static void handle(DamageNumberMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    com.merlinkitsune.astral_dice.client.DamageNumberStore.add(
                            msg.entityId, msg.bonusDamage, msg.color, msg.x, msg.y, msg.z));
            ctx.get().setPacketHandled(true);
        }

        /**
         * 向造成这次伤害的玩家本人发送跳数字。全部跳数字发送统一走本方法。
         *
         * <p>⚠️ 唯一调用方是 {@code combat/DamageNumberAggregator} —— 各伤害点不再各自发包,
         * 否则仍会在客户端互相覆盖(见该类的类头)。
         */
        public static void send(ServerPlayer recipient, LivingEntity target, int damage, int color, double x, double y, double z) {
            if (recipient == null || target == null) return;
            if (target.level().isClientSide()) return;
            var packet = new DamageNumberMessage(target.getId(), damage, color, (float) x, (float) y, (float) z);
            sendToPlayer(recipient, packet);
        }
    }


    // === 动作栏消息(S→C) ===

    public static class ActionBarMessage {
        private final Component message;
        private final int durationTicks;

        public ActionBarMessage(Component message, int durationTicks) {
            this.message = message;
            this.durationTicks = durationTicks;
        }

        public static void encode(ActionBarMessage msg, FriendlyByteBuf buf) {
            buf.writeComponent(msg.message);
            buf.writeVarInt(msg.durationTicks);
        }

        public static ActionBarMessage decode(FriendlyByteBuf buf) {
            return new ActionBarMessage(buf.readComponent(), buf.readVarInt());
        }

        public static void handle(ActionBarMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    com.merlinkitsune.starengine.client.ActionBarManager.show(msg.message, msg.durationTicks));
            ctx.get().setPacketHandled(true);
        }
    }

    // === 立牌主动技能(C→S) ===

    public static class SignActivateMessage {
        public static void encode(SignActivateMessage msg, FriendlyByteBuf buf) {
        }

        public static SignActivateMessage decode(FriendlyByteBuf buf) {
            return new SignActivateMessage();
        }

        public static void handle(SignActivateMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var player = ctx.get().getSender();
                if (player != null) {
                    com.merlinkitsune.astral_dice.item.sign.BaseSignItem.performSkillForCurio(player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // === 打开卡牌栏(C→S) ===

    public static class OpenCardInventoryMessage {
        public static void encode(OpenCardInventoryMessage msg, FriendlyByteBuf buf) {
        }

        public static OpenCardInventoryMessage decode(FriendlyByteBuf buf) {
            return new OpenCardInventoryMessage();
        }

        public static void handle(OpenCardInventoryMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer serverPlayer = ctx.get().getSender();
                if (serverPlayer != null) {
                    com.merlinkitsune.astral_dice.screen.ModMenuTypes.openCardInventory(serverPlayer);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // === 目标选择器(S→C 会话开始 / C→S 确认 / C→S 取消) ===

    /** 服务端下发目标选择会话开始(S→C):由 TargetSelectionManager.start 调用,客户端进入选择模式。 */
    public static class TargetSelectStartMessage {
        private final int token;
        private final int targetType;
        private final double radius;
        private final int durationTicks;
        private final String actionId;
        /**
         * 本次会话是否允许对自身使用(消费方接口
         * com.merlinkitsune.starengine.target.SelfTargetable#allowSelf() 的取值;
         * 当前 {@code ren_privilege} 与三张可自用效果牌动作（express_delivery / luxury_feast / berserk）为 true，其余动作 false)。
         */
        private final boolean allowSelf;
        /**
         * 本会话是否由「主手手持物品」驱动且**没有倒计时**（消费方接口
         * com.merlinkitsune.starengine.target.HoldToSelect 的取值，当前 = 四张效果牌动作
         * express_delivery / luxury_feast / you_have_i_have / berserk）。
         *
         * <p>为真时 {@code durationTicks} 恒为 0：客户端不显示「（剩余 N 秒）」、提示口径改为
         * 「移出手持退出选择」，并在物品离开主手时自行退出选择模式。
         */
        private final boolean holdToSelect;

        public TargetSelectStartMessage(int token, int targetType, double radius, int durationTicks, String actionId,
                                        boolean allowSelf, boolean holdToSelect) {
            this.token = token;
            this.targetType = targetType;
            this.radius = radius;
            this.durationTicks = durationTicks;
            this.actionId = actionId;
            this.allowSelf = allowSelf;
            this.holdToSelect = holdToSelect;
        }

        public static void encode(TargetSelectStartMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.token);
            buf.writeVarInt(msg.targetType);
            buf.writeDouble(msg.radius);
            buf.writeVarInt(msg.durationTicks);
            buf.writeUtf(msg.actionId);
            buf.writeBoolean(msg.allowSelf);
            buf.writeBoolean(msg.holdToSelect);
        }

        public static TargetSelectStartMessage decode(FriendlyByteBuf buf) {
            return new TargetSelectStartMessage(buf.readVarInt(), buf.readVarInt(), buf.readDouble(),
                    buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readBoolean());
        }

        public static void handle(TargetSelectStartMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    com.merlinkitsune.astral_dice.client.TargetSelectionClient.start(
                            msg.token, msg.targetType, msg.radius, msg.durationTicks, msg.actionId, msg.allowSelf,
                            msg.holdToSelect));
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端确认目标(C→S):由 TargetSelectionClient.confirm 发送,服务端 TargetSelectionManager.confirm 权威校验。 */
    public static class TargetSelectConfirmMessage {
        private final int token;
        private final int targetId;

        public TargetSelectConfirmMessage(int token, int targetId) {
            this.token = token;
            this.targetId = targetId;
        }

        public static void encode(TargetSelectConfirmMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.token);
            buf.writeVarInt(msg.targetId);
        }

        public static TargetSelectConfirmMessage decode(FriendlyByteBuf buf) {
            return new TargetSelectConfirmMessage(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(TargetSelectConfirmMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer serverPlayer = ctx.get().getSender();
                if (serverPlayer != null) {
                    com.merlinkitsune.astral_dice.target.TargetSelectionManager.confirm(
                            serverPlayer, msg.token, msg.targetId);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端取消选择(C→S):由 TargetSelectionClient.cancel 发送,服务端立即清除会话。 */
    public static class TargetSelectCancelMessage {
        private final int token;

        public TargetSelectCancelMessage(int token) {
            this.token = token;
        }

        public static void encode(TargetSelectCancelMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.token);
        }

        public static TargetSelectCancelMessage decode(FriendlyByteBuf buf) {
            return new TargetSelectCancelMessage(buf.readVarInt());
        }

        public static void handle(TargetSelectCancelMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer serverPlayer = ctx.get().getSender();
                if (serverPlayer != null) {
                    com.merlinkitsune.astral_dice.target.TargetSelectionManager.cancel(serverPlayer, msg.token);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // === 末影骰子不死图腾动画(S→C) ===

    public static class EnderDieTotemMessage {
        private final int entityId;

        public EnderDieTotemMessage(int entityId) {
            this.entityId = entityId;
        }

        public static void encode(EnderDieTotemMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.entityId);
        }

        public static EnderDieTotemMessage decode(FriendlyByteBuf buf) {
            return new EnderDieTotemMessage(buf.readVarInt());
        }

        public static void handle(EnderDieTotemMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    com.merlinkitsune.astral_dice.client.EnderDieTotemAnimator.play(msg.entityId));
            ctx.get().setPacketHandled(true);
        }

        /** 向目标及所有追踪客户端广播图腾动画(含目标本人) */
        public static void send(LivingEntity target) {
            if (target.level().isClientSide()) return;
            var packet = new EnderDieTotemMessage(target.getId());
            sendToPlayersTrackingEntity(target, packet);
            if (target instanceof ServerPlayer serverTarget) {
                sendToPlayer(serverTarget, packet);
            }
        }
    }

    // === 星币钱包按钮点击(C→S) ===

    /**
     * 客户端只表达「点了哪个按钮」,不带任何数量/金额 —— 存多少、能取多少由服务端按真实
     * 物品栏与账本决定(见 economy/StarCoinWalletActions)。序数越界时服务端静默丢弃。
     */
    public static class StarCoinWalletMessage {
        private final int action;

        public StarCoinWalletMessage(int action) {
            this.action = action;
        }

        public static void encode(StarCoinWalletMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.action);
        }

        public static StarCoinWalletMessage decode(FriendlyByteBuf buf) {
            return new StarCoinWalletMessage(buf.readVarInt());
        }

        public static void handle(StarCoinWalletMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    com.merlinkitsune.astral_dice.economy.StarCoinWalletActions.execute(
                            player,
                            com.merlinkitsune.astral_dice.economy.StarCoinWalletActions.Action.byOrdinal(msg.action));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // === 钱包余额(S→C) ===

    /**
     * 只承载**一个显示用数字**(余额条):客户端拿它渲染,不参与任何判定。
     * 每玩家最多每秒一次、且只在值变化时发送(见 economy/StarCoinBalanceSync)。
     */
    public static class StarCoinBalanceMessage {
        private final long balance;

        public StarCoinBalanceMessage(long balance) {
            this.balance = balance;
        }

        public static void encode(StarCoinBalanceMessage msg, FriendlyByteBuf buf) {
            buf.writeVarLong(msg.balance);
        }

        public static StarCoinBalanceMessage decode(FriendlyByteBuf buf) {
            return new StarCoinBalanceMessage(buf.readVarLong());
        }

        public static void handle(StarCoinBalanceMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    com.merlinkitsune.starengine.economy.StarCoinWalletState.setBalance(msg.balance));
            ctx.get().setPacketHandled(true);
        }
    }

    // === 鼠鼠护盾可见性(S→C) ===

    /**
     * 鼠鼠护盾「他人可见」状态的**全量**列表(entityId)。
     *
     * <p>⚠️ 原版**不同步** mob effect 给「本人 + 自己乘客」以外的玩家 —— 全 jar 构造
     * {@code ClientboundUpdateMobEffectPacket} 只有 4 处,全部只发本人或乘客;
     * {@code ServerEntity} 内不含效果同步代码;原版为「他人可见」单开的发光轮廓与效果粒子
     * 两条通道都走 {@code SynchedEntityData}。⇒ 他人客户端 {@code entity.hasEffect(REN_SHIELD)}
     * **恒为 false**,护盾球曾只在持有者自己(第三人称)可见(2026-09-23 用户实测)。
     * 本消息是该状态的唯一跨客户端来源。
     *
     * <p>全量语义(而非增量):天然幂等、天然覆盖「清除」(不在列表即失效)、且登录时整体刷新
     * 掉跨服务器 entityId 撞号的残留。
     */
    public static class RenShieldStateMessage {
        private final int[] shieldedEntityIds;

        public RenShieldStateMessage(int[] shieldedEntityIds) {
            this.shieldedEntityIds = shieldedEntityIds;
        }

        public static void encode(RenShieldStateMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.shieldedEntityIds.length);
            for (int id : msg.shieldedEntityIds) {
                buf.writeVarInt(id);
            }
        }

        public static RenShieldStateMessage decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > com.merlinkitsune.astral_dice.combat.RenShieldVisibility.MAX_ENTRIES) {
                throw new io.netty.handler.codec.DecoderException(
                        "ren_shield_state: 非法的实体数量 " + count);
            }
            int[] ids = new int[count];
            for (int i = 0; i < count; i++) {
                ids[i] = buf.readVarInt();
            }
            return new RenShieldStateMessage(ids);
        }

        public static void handle(RenShieldStateMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                java.util.List<Integer> ids = new java.util.ArrayList<>(msg.shieldedEntityIds.length);
                for (int id : msg.shieldedEntityIds) {
                    ids.add(id);
                }
                com.merlinkitsune.astral_dice.combat.RenShieldVisibility.replaceAll(ids);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // === 卡牌商店(S→C 候选名单 / C→S 确认转账) ===

    /**
     * S→C:卡牌商店「二级转账界面」的候选玩家名单(索引即转账目标索引)。
     *
     * <p>每项 = UUID + 玩家名 + 星币余额(2026-10-08 增补二 §3):UUID 供头像取真实皮肤,
     * 名字供搜索过滤,余额画在列表右列。
     *
     * <p>编码方式与 1.21.1 的
     * {@code StreamCodec.composite(UUIDUtil.STREAM_CODEC, ..., STRING_UTF8, ..., VAR_LONG, ...)} 逐字节等价:
     * UUID(16 字节) + UTF 字符串 + VarLong,外层 {@code writeCollection}/{@code readList}
     * (= VarInt 数量 + 每项)。
     * 客户端收下后写入 {@code client/CardShopClientState}(刻意不做任何队伍过滤)。
     */
    public static class CardShopTransferListMessage {
        /**
         * 名单条目。对应 1.21.1 的 {@code network/CardShopTransferListPayload.Entry} record;
         * 1.20.1 无独立载荷类,故内嵌于此消息类(record ⇒ 访问器同名 id()/name()/balance(),
         * 与 1.21.1 侧调用点逐字一致)。
         */
        public record Entry(UUID id, String name, long balance) {
        }

        private final List<Entry> entries;

        public CardShopTransferListMessage(List<Entry> entries) {
            this.entries = List.copyOf(entries);
        }

        public static void encode(CardShopTransferListMessage msg, FriendlyByteBuf buf) {
            buf.writeCollection(msg.entries, (buf1, entry) -> {
                buf1.writeUUID(entry.id());
                buf1.writeUtf(entry.name());
                buf1.writeVarLong(entry.balance());
            });
        }

        public static CardShopTransferListMessage decode(FriendlyByteBuf buf) {
            return new CardShopTransferListMessage(buf.readList(
                    buf1 -> new Entry(buf1.readUUID(), buf1.readUtf(), buf1.readVarLong())));
        }

        public static void handle(CardShopTransferListMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                // 只在接收侧为客户端时落静态态(本包只会由服务端发给单个玩家)
                if (ctx.get().getDirection().getReceptionSide().isClient()) {
                    com.merlinkitsune.astral_dice.client.CardShopClientState.setTransferCandidates(msg.entries);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * C→S:「确认转账」(索引取 {@link CardShopTransferListMessage} 的同序名单)。
     *
     * <p>金额只允许 5..20(4 档 5/10/15/20 + 自定义);服务端在
     * {@code shop/CardShopMenu#handleTransfer} 内二次校验(含菜单归属、60 秒玩家级冷却、目标有效性与余额),
     * 本包不做任何结算、也不信任客户端传来的数值。
     */
    public static class CardShopTransferMessage {
        private final int targetIndex;
        private final int amount;

        public CardShopTransferMessage(int targetIndex, int amount) {
            this.targetIndex = targetIndex;
            this.amount = amount;
        }

        public static void encode(CardShopTransferMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.targetIndex);
            buf.writeVarInt(msg.amount);
        }

        public static CardShopTransferMessage decode(FriendlyByteBuf buf) {
            return new CardShopTransferMessage(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(CardShopTransferMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer serverPlayer = ctx.get().getSender();
                if (serverPlayer != null
                        && serverPlayer.containerMenu instanceof com.merlinkitsune.astral_dice.shop.CardShopMenu menu) {
                    menu.handleTransfer(serverPlayer, msg.targetIndex, msg.amount);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * C→S:「拖拽购买」(2026-10-08 v4;对应 1.21.1 的独立载荷 {@code network/CardShopBuyPayload})。
     *
     * <p>玩家把货架第 {@code shelfIndex} 格的卡面拖到**玩家物品栏第 {@code targetSlot} 格**并松手时发送。
     * {@code targetSlot} 用**物品栏索引**口径(0..8 = 快捷栏、9..35 = 主物品栏),服务端经
     * {@code shop/CardShopMenu#playerSlotOffset(int)} 换算成菜单槽序号。
     *
     * <p>{@code targetSlot == -1}(双击快速移入):由服务端**先并入同类未满栈、再**挑第一个空格。
     *
     * <p>本包只承载「哪一格拖到哪一格」;价格、限购、目标格占用、余额与扣款一律由服务端
     * {@code shop/CardShopMenu#handleBuy} 现算并二次校验(**忽略客户端任何数值**)。
     */
    public static class CardShopBuyMessage {
        private final int shelfIndex;
        private final int targetSlot;

        public CardShopBuyMessage(int shelfIndex, int targetSlot) {
            this.shelfIndex = shelfIndex;
            this.targetSlot = targetSlot;
        }

        public static void encode(CardShopBuyMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.shelfIndex);
            buf.writeVarInt(msg.targetSlot);
        }

        public static CardShopBuyMessage decode(FriendlyByteBuf buf) {
            return new CardShopBuyMessage(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(CardShopBuyMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer serverPlayer = ctx.get().getSender();
                if (serverPlayer != null
                        && serverPlayer.containerMenu instanceof com.merlinkitsune.astral_dice.shop.CardShopMenu menu) {
                    menu.handleBuy(serverPlayer, msg.shelfIndex, msg.targetSlot);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
    /**
     * S→C:「请求被拒」信号(对应 1.21.1 的 {@code network/CardShopDenyPayload})。
     *
     * <p>只承载一个原因码({@code CardShopMenu.DENY_NO_COIN} / {@code DENY_INVENTORY_FULL}),
     * 客户端收到后起 30t 倒计时,把钱包数字红白闪 3 次(服务端权威触发)。
     */
    public static class CardShopDenyMessage {
        private final int reason;

        public CardShopDenyMessage(int reason) {
            this.reason = reason;
        }

        public static void encode(CardShopDenyMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.reason);
        }

        public static CardShopDenyMessage decode(FriendlyByteBuf buf) {
            return new CardShopDenyMessage(buf.readVarInt());
        }

        public static void handle(CardShopDenyMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    com.merlinkitsune.astral_dice.client.CardShopClientState.notifyDeny(msg.reason));
            ctx.get().setPacketHandled(true);
        }
    }
}
