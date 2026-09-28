package com.merlinkitsune.astral_dice.platform;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.EntityTravelToDimensionEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.item.ItemTossEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingAttackEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDropsEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingUseTotemEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.EntityItemPickupEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerEvent;
import com.merlinkitsune.astral_dice.platform.event.level.BlockEvent;
import com.merlinkitsune.astral_dice.platform.event.AnvilUpdateEvent;
// (无对应物:Forge 内部工具类 / 嵌套枚举 Event.Result)
import com.merlinkitsune.astral_dice.platform.event.RegisterCommandsEvent;
import com.merlinkitsune.astral_dice.platform.event.TickEvent;
import com.merlinkitsune.astral_dice.platform.fml.LogicalSide;
import com.merlinkitsune.astral_dice.platform.network.SimpleChannel;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;

/**
 * Fabric 回调 → 自建事件总线的桥接层。
 *
 * <h2>设计</h2>
 * 消费方 125 个处理器全部注册在 {@link LoaderBus} 上(保留 Forge 的
 * {@code EventPriority} 排序语义,见该总线类注释)。本类负责把 **Fabric API 的等价回调**
 * 转成对应事件并派发。凡 FAPI **没有等价回调**的事件(尤其是伤害/死亡/效果这类需要
 * 改写数值或取消原版流程的),改由 mixin 在对应注入点派发 —— 见
 * {@code com.merlinkitsune.astral_dice.mixin} 下的 {@code *BridgeMixin}。
 *
 * <h2>覆盖状态(必须逐条维护,否则事件会「静默永不触发」)</h2>
 * <table border="1">
 *   <caption>桥接覆盖表</caption>
 *   <tr><th>事件</th><th>来源</th></tr>
 *   <tr><td>{@code TickEvent.ServerTickEvent / LevelTickEvent / PlayerTickEvent}</td><td>本类(FAPI tick)</td></tr>
 *   <tr><td>{@code PlayerEvent.PlayerLoggedInEvent / PlayerLoggedOutEvent}</td><td>本类(FAPI 连接事件)</td></tr>
 *   <tr><td>{@code PlayerEvent.PlayerRespawnEvent}</td><td>本类(FAPI AFTER_RESPAWN)</td></tr>
 *   <tr><td>{@code LivingAttackEvent}</td><td>本类(FAPI ALLOW_DAMAGE)</td></tr>
 *   <tr><td>{@code RegisterCommandsEvent}</td><td>本类(FAPI 命令回调)</td></tr>
 *   <tr><td>{@code LivingHurtEvent / LivingDamageEvent / LivingHealEvent / LivingDeathEvent}</td>
 *       <td>mixin(DamageBridgeMixin / DeathBridgeMixin / HealBridgeMixin)</td></tr>
 *   <tr><td>{@code MobEffectEvent.Added / Remove / Expired}</td><td>mixin(EffectBridgeMixin)</td></tr>
 * </table>
 */
public final class FabricBridges {

    /** 是否已安装(幂等)。 */
    private static boolean installed = false;

    /** 早期安装:只做 server 句柄登记(供 {@code PacketDistributor.ALL} 广播使用)。 */
    public static void installEarly() {
        ServerLifecycleEvents.SERVER_STARTING.register(SimpleChannel::setServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> SimpleChannel.setServer(null));
    }

    /** 安装全部 FAPI 回调桥接。 */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        installTicks();
        installPlayerLifecycle();
        installDamage();
        installCommands();
    }

    private static void installTicks() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // 服务端 tick(Phase.END 与 Forge 侧派发点一致:世界状态已更新完毕)
            LoaderBus.INSTANCE.post(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
            // 世界 tick
            for (ServerLevel level : server.getAllLevels()) {
                LoaderBus.INSTANCE.post(new TickEvent.LevelTickEvent(
                        LogicalSide.SERVER, TickEvent.Phase.END, level, () -> true));
            }
            // 玩家 tick(Forge 按玩家派发;Fabric 无该回调 ⇒ 在服务端 tick 内逐玩家派发)
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                LoaderBus.INSTANCE.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            }
        });
    }

    private static void installPlayerLifecycle() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                LoaderBus.INSTANCE.post(new PlayerEvent.PlayerLoggedInEvent(handler.getPlayer())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LoaderBus.INSTANCE.post(new PlayerEvent.PlayerLoggedOutEvent(handler.getPlayer())));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                LoaderBus.INSTANCE.post(new PlayerEvent.PlayerRespawnEvent(newPlayer, alive)));
        // ⚠️ 未接线(必须补齐,否则 PlayerChangedDimensionEvent 永不触发):
        //    Fabric API 1.20.1 没有「玩家切换维度」回调(ENTITY_LOAD 无法区分「同维度重载」
        //    与「跨维度」,用它派发会给出错误的 from/to)。计划由 mixin 注入
        //    `ServerPlayer#changeDimension` 派发。影响面:跨维度后客户端 synced 键快照不刷新
        //    (登录/重生路径仍会刷新),属已知功能缺口,登记在 PORT_STATUS 文档。
    }

    private static void installDamage() {
        // LivingAttackEvent:可取消;FAPI 的 ALLOW_DAMAGE 正是这一语义
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            LivingAttackEvent event = new LivingAttackEvent(entity, source, amount);
            LoaderBus.INSTANCE.post(event);
            return !event.isCanceled();
        });
    }

    private static void installCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                LoaderBus.INSTANCE.post(new RegisterCommandsEvent(dispatcher, environment, registryAccess)));
    }

    private FabricBridges() {
    }
}
