package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.RenShieldVisibility;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：鼠鼠护盾的「他人可见」状态（{@code RenShieldRenderer} 的渲染条件）。
 *
 * <p><b>为什么不能直接用 {@code entity.hasEffect(REN_SHIELD)}</b>：原版从不同步
 * {@code MobEffectInstance} 给「本人 + 自己乘客」以外的玩家（详见 {@link RenShieldVisibility}
 * 类头与三线 {@code RenShieldRenderer} 的说明），因此他人客户端上该判定恒为 false ⇒
 * 护盾球只在持有者自己（第三人称）可见。本载荷是护盾状态的<b>唯一跨客户端来源</b>。
 *
 * <h2>全量语义（而非增量）</h2>
 * 每次都下发「当前全服护盾持有者的完整 entityId 列表」：
 * <ul>
 *   <li><b>天然幂等</b>：重复广播同一状态不产生副作用；</li>
 *   <li><b>天然覆盖清除</b>：某玩家不再持有护盾 ⇒ 他不在列表里 ⇒ 客户端整体替换后自然移除，
 *       不需要单独的「取消」消息，也不会因丢包而残留；</li>
 *   <li><b>登录即清残留</b>：服务端在玩家登录时单独补发一份全量 ⇒ 每个客户端在新会话的
 *       第一份包就把本地镜像整体刷新（entityId 只在单个服务端会话内唯一，跨服务器会撞号）。</li>
 * </ul>
 *
 * <p>开销可忽略：护盾授予/清空是低频事件（被动每 5 分钟一次、主动技能按冷却），
 * 且列表长度 = 当前护盾持有者人数（通常 0～3）。因此发送目标取<b>全服广播</b>而非追踪范围 ——
 * entityId 在全服唯一，非本维度的 id 在客户端不会命中任何已加载玩家，故无副作用。
 *
 * <p>handler 落到 {@link RenShieldVisibility}（无客户端专有类型），
 * 因此本类在专用服务端也能安全加载。
 */
public record RenShieldStatePayload(List<Integer> shieldedEntityIds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenShieldStatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "ren_shield_state"));

    /**
     * 手写编解码（不用 {@code StreamCodec.composite}）：载荷是**变长整型列表**，
     * 且解码必须能对长度做上界校验（见 {@link RenShieldVisibility#MAX_ENTRIES}），
     * 直接手写比套 {@code ByteBufCodecs.list()} 更可控，也免去三线之间的 API 差异。
     */
    public static final StreamCodec<ByteBuf, RenShieldStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RenShieldStatePayload decode(ByteBuf buf) {
            int count = ByteBufCodecs.VAR_INT.decode(buf);
            if (count < 0 || count > RenShieldVisibility.MAX_ENTRIES) {
                throw new io.netty.handler.codec.DecoderException(
                        "ren_shield_state: 非法的实体数量 " + count);
            }
            List<Integer> ids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ids.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            return new RenShieldStatePayload(List.copyOf(ids));
        }

        @Override
        public void encode(ByteBuf buf, RenShieldStatePayload payload) {
            List<Integer> ids = payload.shieldedEntityIds();
            ByteBufCodecs.VAR_INT.encode(buf, ids.size());
            for (int id : ids) {
                ByteBufCodecs.VAR_INT.encode(buf, id);
            }
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RenShieldStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RenShieldVisibility.replaceAll(payload.shieldedEntityIds()));
    }
}
