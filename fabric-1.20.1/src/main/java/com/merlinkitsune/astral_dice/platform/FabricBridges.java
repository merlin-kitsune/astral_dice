package com.merlinkitsune.astral_dice.platform;

import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.EntityTravelToDimensionEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.item.ItemTossEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingAttackEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDropsEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingUseTotemEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.EntityItemPickupEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerInteractEvent;
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
import net.minecraft.world.InteractionResult;
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
 *   <caption>桥接覆盖表(2026-09-29 全量复核)</caption>
 *   <tr><th>事件</th><th>派发来源</th></tr>
 *   <tr><td>{@code TickEvent.ServerTickEvent / LevelTickEvent / PlayerTickEvent}</td><td>本类 installTicks(FAPI tick)</td></tr>
 *   <tr><td>{@code PlayerEvent.PlayerLoggedInEvent / PlayerLoggedOutEvent}</td><td>本类 installPlayerLifecycle(FAPI 连接事件)</td></tr>
 *   <tr><td>{@code PlayerEvent.PlayerRespawnEvent}</td><td>本类 installPlayerLifecycle(FAPI AFTER_RESPAWN)</td></tr>
 *   <tr><td>{@code LivingAttackEvent}</td><td>本类 installDamage(FAPI ALLOW_DAMAGE)</td></tr>
 *   <tr><td>{@code RegisterCommandsEvent} / {@code OnDatapackSyncEvent}</td><td>本类 installCommands</td></tr>
 *   <tr><td>{@code PlayerInteractEvent.RightClickBlock / EntityInteract / RightClickItem}</td><td>本类 installInteractions(FAPI Use*Callback)</td></tr>
 *   <tr><td>{@code LivingDamageEvent / LivingDeathEvent / LivingDropsEvent / LivingChangeTargetEvent}</td><td>PuzzlesBridges</td></tr>
 *   <tr><td>{@code MobEffectEvent.Added / Remove / Expired}</td><td>PuzzlesBridges</td></tr>
 *   <tr><td>{@code BlockEvent.BreakEvent / AnvilUpdateEvent / EntityItemPickupEvent}</td><td>PuzzlesBridges</td></tr>
 *   <tr><td>{@code PlayerEvent.Clone}</td><td>PuzzlesBridges</td></tr>
 *   <tr><td>{@code LivingHurtEvent}</td><td>mixin bridge/LivingHurtBridgeMixin</td></tr>
 *   <tr><td>{@code LivingUseTotemEvent}</td><td>mixin bridge/LivingUseTotemBridgeMixin</td></tr>
 *   <tr><td>{@code PlayerEvent.ItemCraftedEvent}</td><td>mixin bridge/ItemCraftedBridgeMixin</td></tr>
 *   <tr><td>{@code LivingHealEvent}</td><td>mixin bridge/LivingHealBridgeMixin</td></tr>
 *   <tr><td>{@code AttackEntityEvent}</td><td>mixin bridge/PlayerAttackBridgeMixin</td></tr>
 *   <tr><td>{@code ProjectileImpactEvent}</td><td>mixin bridge/ProjectileImpactBridgeMixin(+ 珍珠专用桥)</td></tr>
 *   <tr><td>{@code EntityTeleportEvent.EnderPearl} / {@code ProjectileImpactEvent}(珍珠)</td><td>mixin bridge/EnderPearlTeleportBridgeMixin</td></tr>
 *   <tr><td>{@code EntityTravelToDimensionEvent}(玩家 / 非玩家两条)</td><td>mixin bridge/{ServerPlayer,Entity}DimensionTravelBridgeMixin</td></tr>
 *   <tr><td>{@code LivingEntityUseItemEvent.Finish}</td><td>mixin bridge/LivingUseItemFinishBridgeMixin</td></tr>
 *   <tr><td>{@code ItemTossEvent}</td><td>mixin bridge/ItemTossBridgeMixin</td></tr>
 *   <tr><td>{@code SignActiveTriggeredEvent}(前置库)</td><td>消费方自身({@code item/sign/BaseSignItem})</td></tr>
 * </table>
 * <p>客户端侧见 {@code platform/client/FabricClientBridges} 与 {@code mixin/bridge/Client*}。
 * <p>回归护栏:{@code LoaderBus#dispatchReport()} 会列出**每个已注册事件类的派发次数(0 次的也列)** ——
 * 新增事件后必须用它证伪「桥装了但从不触发」。
 */
public final class FabricBridges {

    /** 是否已安装(幂等)。 */
    private static boolean installed = false;

    /** 早期安装:只做 server 句柄登记(供 {@code PacketDistributor.ALL} 广播使用)。 */
    public static void installEarly() {
        ServerLifecycleEvents.SERVER_STARTING.register(SimpleChannel::setServer);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> SimpleChannel.setServer(null));
        // 事件派发计数报告(诊断):关服时打印一次。
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                com.merlinkitsune.astral_dice.AstralDiceMod.LOGGER.info(
                        "[Astral Dice] 事件派发统计(关服){}", LoaderBus.INSTANCE.dispatchReport()));
        // ⚠️ 再加一个「开局 600 tick 后」的取样点:开发/冒烟环境常常是**强杀进程**收尾
        //    (拿不到 SERVER_STOPPING),没有这个取样点就永远看不到报告。
        //    判据:ServerTickEvent 必须为正数 —— 它与所有其它事件走同一条 post 路径,
        //    它通即桥通;其余为 0 属正常(无玩家交互)。
        //    600 tick(30s)而非 200:给 kubejs 取证探针(默认 300 tick 触发)留出时间。
        ServerTickEvents.END_SERVER_TICK.register(new ServerTickEvents.EndTick() {
            private int ticks = 0;

            @Override
            public void onEndTick(net.minecraft.server.MinecraftServer server) {
                if (++this.ticks == 600) {
                    com.merlinkitsune.astral_dice.AstralDiceMod.LOGGER.info(
                            "[Astral Dice] 事件派发统计(开局 600 tick){}", LoaderBus.INSTANCE.dispatchReport());
                }
            }
        });
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
        installInteractions();
        // Puzzles Lib 回调 → 自建事件(伤害/死亡/掉落/目标/效果/铁砧/方块/克隆/拾取)。
        // ⚠️ Puzzles 是**硬依赖**(fabric.mod.json depends)⇒ 无需守卫,也绝不能被守卫掉:
        //    否则这些事件会静默永不触发(项目最忌的假绿)。
        PuzzlesBridges.install();
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
        // 跨维度:由 mixin 在 `ServerPlayer#changeDimension` 的 HEAD 派发
        // EntityTravelToDimensionEvent(见 mixin/bridge/ServerPlayerDimensionTravelBridgeMixin),
        // 那条路径同时覆盖非玩家的 `Entity#changeDimension`。
        // ⚠️ `PlayerEvent.PlayerChangedDimensionEvent`(切换**之后**的那个)当前**没有任何订阅者**,
        //    故未派发 —— 需要时在对应 mixin 的 @At("RETURN") 处补一行即可,不要凭猜测预先派发。
        // ⚠️ 本注释此前写「未接线、跨维度后客户端 synced 键快照不刷新」,已于 2026-09-29 按实际
        //    订阅面订正:无订阅者 ⇒ 不存在可见影响。
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
        // OnDatapackSyncEvent:Forge 在「玩家进服」与「/reload」时触发(下发标签/配方之前)。
        // FAPI 的对应回调是 ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS —— 它**逐玩家**触发,
        // 故这里对每个玩家各派发一次;消费方(PlayerLifecycleHandler)的 getPlayer()!=null 分支
        // 与 getAllPlayers() 分支都零改动可用。
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) ->
                LoaderBus.INSTANCE.post(new com.merlinkitsune.astral_dice.platform.event.OnDatapackSyncEvent(
                        player.getServer().getPlayerList(), player)));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                LoaderBus.INSTANCE.post(new RegisterCommandsEvent(dispatcher, environment, registryAccess)));
    }

    /**
     * 玩家交互三回调(FAPI {@code Use*Callback} → {@code PlayerInteractEvent.*})。
     *
     * <p>2026-09-29 补:此前本类**已 import** 这三个回调却从未 register ⇒
     * {@code RightClickBlock / EntityInteract / RightClickItem} 三个事件「有 handler、无派发源」,
     * 表现为「首箱赠礼不触发」「Hanna 立牌漂浮期仍能用末影珍珠」。
     *
     * <p>返回语义对齐 Forge 的 {@code cancellationResult}:
     * <ul>
     *   <li>只读型事件(前两者)**恒返回 PASS** —— 返回 SUCCESS 会吞掉原版后续处理
     *       (箱子打不开等),这不是消费方的意图;</li>
     *   <li>可取消的事件(RightClickItem)取消时返回 {@code fail} —— 原版据此
     *       不消耗物品、不进入 {@code Item#use}。</li>
     * </ul>
     * ⚠️ {@code UseItemCallback} 在**双端**触发;服务端取消即可阻止实际使用
     * (客户端侧的预测由原版自身的 server-authoritative 流程收敛)。
     */
    private static void installInteractions() {
        // 方块容器右键(首箱赠礼:箱子/木桶/潜影盒/漏斗…)
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            LoaderBus.INSTANCE.post(new PlayerInteractEvent.RightClickBlock(
                    player, hand, hitResult.getBlockPos(), hitResult));
            return InteractionResult.PASS;
        });
        // 实体交互(首箱赠礼:运输矿车)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            LoaderBus.INSTANCE.post(new PlayerInteractEvent.EntityInteract(player, hand, entity));
            return InteractionResult.PASS;
        });
        // 物品右键(Hanna 立牌:魔女漂浮期间禁止使用末影珍珠)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            PlayerInteractEvent.RightClickItem event =
                    new PlayerInteractEvent.RightClickItem(player, hand);
            LoaderBus.INSTANCE.post(event);
            if (event.isCanceled()) {
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });
    }

    private FabricBridges() {
    }
}
