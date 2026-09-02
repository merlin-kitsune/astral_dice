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
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.20.1 Forge 网络层:SimpleChannel 承载 1.21 分支的 4 个载荷
 * (伤害数字/动作栏/立牌主动/卡牌栏打开)+ 附件同步消息。
 * 静态发送助手对应 1.21 的 PacketDistributor.sendTo* 调用面。
 */
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new net.minecraft.resources.ResourceLocation(AstralDiceMod.MODID, "main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

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
    }

    // === 发送助手(对应 1.21 PacketDistributor 静态方法) ===

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToPlayersTrackingEntity(Entity entity, Object message) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    // === 附件同步 ===

    /** 单键同步(AttachedDataKey.set 服务端写入后调用)。 */
    public static <T> void syncAttachment(ServerPlayer player, AttachedDataKey<T> key, net.minecraft.nbt.Tag tag) {
        CompoundTag payload = new CompoundTag();
        if (tag != null) {
            payload.put(key.name(), tag);
        }
        sendToPlayer(player, new AttachmentSyncMessage(payload));
    }

    /** synced 键全量快照(登录/重生/切维度)。 */
    public static void syncSnapshot(ServerPlayer player, List<AttachedDataKey<?>> keys) {
        CompoundTag payload = new CompoundTag();
        for (AttachedDataKey<?> key : keys) {
            net.minecraft.nbt.Tag tag = key.readRawTag(player);
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

    public static class DamageNumberMessage {
        private final int entityId;
        private final int bonusDamage;
        private final int color;

        public DamageNumberMessage(int entityId, int bonusDamage, int color) {
            this.entityId = entityId;
            this.bonusDamage = bonusDamage;
            this.color = color;
        }

        public static void encode(DamageNumberMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.entityId);
            buf.writeVarInt(msg.bonusDamage);
            buf.writeInt(msg.color);
        }

        public static DamageNumberMessage decode(FriendlyByteBuf buf) {
            return new DamageNumberMessage(buf.readVarInt(), buf.readVarInt(), buf.readInt());
        }

        public static void handle(DamageNumberMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    com.merlinkitsune.astral_dice.client.ClientDamageNumbers.add(msg.entityId, msg.bonusDamage, msg.color));
            ctx.get().setPacketHandled(true);
        }

        /** 向目标追踪客户端(含目标本人)发送跳数字。全部跳数字发送统一走本方法。 */
        public static void send(LivingEntity target, int damage, int color) {
            if (target.level().isClientSide()) return;
            var packet = new DamageNumberMessage(target.getId(), damage, color);
            sendToPlayersTrackingEntity(target, packet);
            if (target instanceof net.minecraft.server.level.ServerPlayer serverTarget) {
                sendToPlayer(serverTarget, packet);
            }
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
                    com.merlinkitsune.astral_dice.client.ActionBarManager.show(msg.message, msg.durationTicks));
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

        public TargetSelectStartMessage(int token, int targetType, double radius, int durationTicks, String actionId) {
            this.token = token;
            this.targetType = targetType;
            this.radius = radius;
            this.durationTicks = durationTicks;
            this.actionId = actionId;
        }

        public static void encode(TargetSelectStartMessage msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.token);
            buf.writeVarInt(msg.targetType);
            buf.writeDouble(msg.radius);
            buf.writeVarInt(msg.durationTicks);
            buf.writeUtf(msg.actionId);
        }

        public static TargetSelectStartMessage decode(FriendlyByteBuf buf) {
            return new TargetSelectStartMessage(buf.readVarInt(), buf.readVarInt(), buf.readDouble(),
                    buf.readVarInt(), buf.readUtf());
        }

        public static void handle(TargetSelectStartMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    com.merlinkitsune.astral_dice.client.TargetSelectionClient.start(
                            msg.token, msg.targetType, msg.radius, msg.durationTicks, msg.actionId));
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
}
