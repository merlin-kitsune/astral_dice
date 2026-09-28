package com.merlinkitsune.astral_dice.platform.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 收件人选择器(对齐 Forge 的 {@code network.PacketDistributor})。
 *
 * <p>本模组只用到 3 个目标(实测):{@code PLAYER} / {@code TRACKING_ENTITY} / {@code ALL}。
 * Fabric 侧把它们解析为「具体的 {@link ServerPlayer} 列表」后逐个发包
 * (FAPI 1.20.1 的 {@code ServerPlayNetworking.send} 是按玩家发的)。
 */
public final class PacketDistributor {

    /** 一个收件人目标。 */
    public static final class Target {
        private final Kind kind;
        private Supplier<?> arg;

        private Target(Kind kind) {
            this.kind = kind;
        }

        public Target with(Supplier<?> supplier) {
            this.arg = supplier;
            return this;
        }

        public Target noArg() {
            return this;
        }

        List<ServerPlayer> resolve() {
            Object value = arg == null ? null : arg.get();
            List<ServerPlayer> out = new ArrayList<>();
            switch (kind) {
                case PLAYER -> {
                    if (value instanceof ServerPlayer sp) {
                        out.add(sp);
                    }
                }
                case TRACKING_ENTITY, TRACKING_ENTITY_AND_SELF -> {
                    if (value instanceof Entity entity && entity.level() != null) {
                        var server = entity.getServer();
                        if (server != null) {
                            var level = server.getLevel(entity.level().dimension());
                            if (level != null) {
                                // 追踪范围:原版以 48 格(4 区块)为基础;这里用 256 格保守覆盖,
                                // 并在去重后下发。⚠️ 与 Forge 的精确追踪列表有差异(已登记):
                                // 本模组的追踪发包只用于「鼠鼠护盾外观」等表现层,超出即无效,
                                // 多发不会产生错误状态(客户端按 entityId 查实体,查不到就忽略)。
                                double radius = 256.0D;
                                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                    if (p != entity && p.level() == level
                                            && p.distanceToSqr(entity) <= radius * radius) {
                                        out.add(p);
                                    }
                                }
                                if (kind == Kind.TRACKING_ENTITY_AND_SELF && entity instanceof ServerPlayer self) {
                                    out.add(self);
                                }
                            }
                        }
                    }
                }
                case ALL -> {
                    if (value instanceof net.minecraft.server.MinecraftServer server) {
                        out.addAll(server.getPlayerList().getPlayers());
                    }
                }
                default -> {
                }
            }
            return out;
        }
    }

    private enum Kind {
        PLAYER,
        TRACKING_ENTITY,
        TRACKING_ENTITY_AND_SELF,
        ALL
    }

    /** 单个玩家(用 {@code .with(() -> player)})。 */
    public static final Target PLAYER = new Target(Kind.PLAYER);
    /** 正在追踪该实体,但不含该实体自身。 */
    public static final Target TRACKING_ENTITY = new Target(Kind.TRACKING_ENTITY);
    /** 正在追踪该实体,且含该实体自身。 */
    public static final Target TRACKING_ENTITY_AND_SELF = new Target(Kind.TRACKING_ENTITY_AND_SELF);
    /** 全服所有玩家(用 {@code .noArg()},由 SimpleChannel 补当前 server)。 */
    public static final Target ALL = new Target(Kind.ALL);

    private PacketDistributor() {
    }
}
