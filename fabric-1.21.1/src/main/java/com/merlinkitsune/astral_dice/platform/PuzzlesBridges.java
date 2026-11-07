package com.merlinkitsune.astral_dice.platform;

import com.merlinkitsune.astral_dice.platform.event.AnvilUpdateEvent;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.event.entity.player.AnvilRepairEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingChangeTargetEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDamageEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDeathEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.LivingDropsEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.living.MobEffectEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.EntityItemPickupEvent;
import com.merlinkitsune.astral_dice.platform.event.entity.player.PlayerEvent;
import com.merlinkitsune.astral_dice.platform.event.level.BlockEvent;

import fuzs.puzzleslib.api.event.v1.core.EventResult;
import fuzs.puzzleslib.api.event.v1.entity.living.LivingChangeTargetCallback;
import fuzs.puzzleslib.api.event.v1.entity.living.LivingDeathCallback;
import fuzs.puzzleslib.api.event.v1.entity.living.LivingDropsCallback;
import fuzs.puzzleslib.api.event.v1.entity.living.LivingHurtCallback;
import fuzs.puzzleslib.api.event.v1.entity.living.MobEffectEvents;
import fuzs.puzzleslib.api.event.v1.entity.player.AnvilEvents;
import fuzs.puzzleslib.api.event.v1.entity.player.ItemEntityEvents;
import fuzs.puzzleslib.api.event.v1.entity.player.PlayerCopyEvents;
import fuzs.puzzleslib.api.event.v1.level.BlockEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import com.merlinkitsune.astral_dice.platform.util.Effects;

/**
 * <b>Puzzles Lib 回调 → 自建 Forge 事件</b>的服务端桥。
 *
 * <h2>为什么可以用「别人的 mixin」</h2>
 * 本模组 109 个处理器全部注册在 {@link LoaderBus} 上（保留 Forge 的 {@code EventPriority}
 * 排序语义）。本类只负责把 Puzzles Lib 的回调转成对应事件再派发 —— 排序仍然由我们自己的总线做，
 * 所以「气囊保命优先、磨刀石次之、LOWEST = 最终伤害阶段」这套承重顺序**不受影响**。
 *
 * <h2>⚠️ 映射必须按**注入点**核对，不能按名字</h2>
 * 实测 Puzzles 8.1.33 的 {@code LivingEntityFabricMixin} 字节码后确认：
 * <table border="1">
 *   <caption>关键映射（已逐条核对注入位置）</caption>
 *   <tr><th>Puzzles 回调</th><th>注入点</th><th>对应 Forge 事件</th></tr>
 *   <tr><td>{@code LivingHurtCallback}</td>
 *       <td><b>{@code LivingEntity#actuallyHurt} 的 HEAD</b></td>
 *       <td><b>{@link LivingDamageEvent}</b> —— Forge 的 {@code ForgeHooks.onLivingDamage}
 *           正是插在这个位置（{@code isInvulnerableTo} 之后、吸收减免之前）</td></tr>
 *   <tr><td>{@code LivingAttackCallback}</td><td>{@code LivingEntity#hurt} 的 HEAD</td>
 *       <td>{@link com.merlinkitsune.astral_dice.platform.event.entity.living.LivingAttackEvent}
 *           —— 本线已由 FAPI {@code ALLOW_DAMAGE} 覆盖，此处不重复注册</td></tr>
 *   <tr><td>{@code LivingDeathCallback}</td><td>{@code LivingEntity#die} 的 HEAD</td>
 *       <td>{@link LivingDeathEvent}</td></tr>
 *   <tr><td>{@code LivingDropsCallback}</td><td>{@code dropAllDeathLoot} 的 TAIL</td>
 *       <td>{@link LivingDropsEvent}</td></tr>
 *   <tr><td>{@code MobEffectEvents.Apply / Remove / Expire}</td>
 *       <td>{@code addEffect} STORE / {@code removeEffect} HEAD / {@code tickEffects} 的 Iterator.remove</td>
 *       <td>{@link MobEffectEvent}.{@code Added} / {@code Remove} / {@code Expired}</td></tr>
 *   <tr><td>{@code MobEffectEvents.AFFECTS}</td>
 *       <td><b>{@code LivingEntity#canBeAffected} 的 HEAD（可中断）</b></td>
 *       <td><b>{@link MobEffectEvent}.{@code Applicable}</b> —— Forge / Neo 的
 *           {@code MobEffectEvent.Applicable} 正是插在这个位置（{@code addEffect} 内先判，
 *           不通过直接返回 false）。puzzles-lib 21.1.66 实测：
 *           {@code LivingEntityFabricMixin#canBeAffected} 上是
 *           {@code @Inject(method="method_6049", at=@At("HEAD"), cancellable=true)}，
 *           回调返回 {@code EventResult}，{@code isInterrupt()} 为真时直接
 *           {@code cir.setReturnValue(result.getAsBoolean())}</td></tr>
 *   <tr><td>{@code LivingChangeTargetCallback}</td><td>{@code Mob#setTarget}</td>
 *       <td>{@link LivingChangeTargetEvent}（targetType = {@code MOB_TARGET}）</td></tr>
 *   <tr><td>{@code AnvilEvents.Use}</td><td>{@code AnvilMenu#onTake}（Container#setItem 之前）</td>
 *       <td>{@link AnvilRepairEvent}（取走成品；{@code breakChance} 可改并写回）</td></tr>
 * </table>
 *
 * <p>⚠️ <b>{@code LivingHurtCallback} 的名字有误导性</b>：它拿到的 {@code MutableFloat}
 * 是 {@code actuallyHurt} 的**入参**，也就是 {@code hurt()} 里已经算好、正要应用的那个值
 * —— 语义是 Forge 的 {@code LivingDamageEvent}，**不是** {@code LivingHurtEvent}
 * （后者在 {@code hurt()} 内、{@code actuallyHurt} 调用之前，且拿到的是同一份值）。
 * Forge 的 {@code LivingHurtEvent} 因此仍需自写 mixin：
 * 见 {@code mixin/bridge/LivingHurtBridgeMixin}。
 *
 * <h2>取消语义对照</h2>
 * <ul>
 *   <li>Forge {@code LivingDamageEvent.setCanceled(true)} ⇒ {@code ForgeHooks.onLivingDamage}
 *       返回 0 ⇒ 不掉血 ⇒ 这里返回 {@link EventResult#INTERRUPT}（Puzzles 会 cancel 整个 actuallyHurt）。</li>
 *   <li>Forge {@code LivingDeathEvent} 取消 ⇒ 不死亡 ⇒ {@code INTERRUPT}。</li>
 *   <li>Forge {@code MobEffectEvent.Remove} 取消 ⇒ 不移除 ⇒ {@code INTERRUPT}。</li>
 *   <li>Forge / Neo {@code MobEffectEvent.Applicable} 判为「不施加」（{@code DENY} /
 *       {@code DO_NOT_APPLY}）⇒ 该效果压根不会被施加 ⇒ {@link EventResult#DENY}
 *       （interrupt + false：Puzzles 让 {@code canBeAffected} 直接返回 false，
 *       {@code addEffect} 随即返回 false）；判为 {@code ALLOW} ⇒ {@link EventResult#ALLOW}；
 *       {@code DEFAULT} ⇒ {@link EventResult#PASS}（交回原版 {@code canBeAffected}）。</li>
 *   <li>Forge {@code LivingDropsEvent} 取消 ⇒ 无掉落 ⇒ {@code INTERRUPT}。</li>
 *   <li>Forge {@code BlockEvent.BreakEvent} 取消 ⇒ 不破坏 ⇒ {@code INTERRUPT}。</li>
 *   <li>其余：只有真的改过值/取消过才回 {@code INTERRUPT}，否则一律 {@link EventResult#PASS}
 *       （{@code PASS} = 不干预，让其它监听者与原版继续）。</li>
 * </ul>
 */
public final class PuzzlesBridges {

    /** 安装全部服务端桥（幂等由 {@link FabricBridges#install()} 的安装标志保证）。 */
    public static void install() {
        installLiving();
        installEffects();
        installWorld();
        installPlayers();
        // 取证:桥装没装必须能从日志看出来 —— 「代码在但没接上」是本项目踩过的最大一类坑
        //(见 AstralDiceMod 里 install() 从未被调用那一处)。
        com.merlinkitsune.astral_dice.AstralDiceMod.LOGGER.info(
                "[Astral Dice] Puzzles 事件桥已安装:伤害/死亡/掉落/目标/效果×4/方块/克隆/拾取/铁砧×2");
    }

    // ------------------------------------------------------------------
    // 生物:伤害 / 死亡 / 掉落 / 目标
    // ------------------------------------------------------------------

    private static void installLiving() {
        // ── LivingDamageEvent ← Puzzles LivingHurtCallback(actuallyHurt HEAD) ──
        LivingHurtCallback.EVENT.register((entity, source, amount) -> {
            LivingDamageEvent event = new LivingDamageEvent(entity, source, amount.getAsFloat());
            LoaderBus.INSTANCE.post(event);
            if (event.isCanceled()) {
                return EventResult.INTERRUPT;
            }
            if (event.getAmount() != amount.getAsFloat()) {
                amount.accept(event.getAmount());
            }
            return EventResult.PASS;
        });

        // ── LivingDeathEvent ← LivingDeathCallback(die HEAD) ──
        LivingDeathCallback.EVENT.register((entity, source) -> {
            LivingDeathEvent event = new LivingDeathEvent(entity, source);
            LoaderBus.INSTANCE.post(event);
            return event.isCanceled() ? EventResult.INTERRUPT : EventResult.PASS;
        });

        // ── LivingDropsEvent ← LivingDropsCallback(dropAllDeathLoot TAIL) ──
        LivingDropsCallback.EVENT.register((entity, source, drops, recentlyHit) -> {
            // 1.21.1 的 LivingDropsCallback 只有 4 参:(entity, source, drops, recentlyHit),lootingLevel 形参已删。
            // 第 4 参的实际来源是「lootingLevel > 0」——puzzles-lib 21.1.66 的
            // FabricEventImplHelper#tryOnLivingDrops 字节码:iload_2 / ifle / iconst_1,
            // 即把 int 型 lootingLevel 压成 boolean 再传。故这里按「有没有抢夺等级」还原成 1/0;
            // 本线没有任何消费方读 getLootingLevel()/isRecentlyHit()(已 grep 核实),还原只用于事件构造。
            LivingDropsEvent event = new LivingDropsEvent(entity, source, drops, recentlyHit ? 1 : 0, recentlyHit);
            LoaderBus.INSTANCE.post(event);
            return event.isCanceled() ? EventResult.INTERRUPT : EventResult.PASS;
        });

        // ── LivingChangeTargetEvent ← LivingChangeTargetCallback(Mob#setTarget) ──
        LivingChangeTargetCallback.EVENT.register((entity, newTarget) -> {
            // DefaultedValue 的「默认值」= 原版打算设置的目标 ⇒ 它就是 Forge 的 originalTarget
            LivingChangeTargetEvent event = new LivingChangeTargetEvent(entity, newTarget.getAsDefault(),
                    LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
            LoaderBus.INSTANCE.post(event);
            if (event.isCanceled()) {
                // Forge 取消 ⇒ 保持原目标不变(Puzzles 的 INTERRUPT 正是不执行 setTarget)
                return EventResult.INTERRUPT;
            }
            if (event.getNewTarget() != newTarget.getAsDefault()) {
                newTarget.accept(event.getNewTarget());
            }
            return EventResult.PASS;
        });
    }

    // ------------------------------------------------------------------
    // 状态效果
    // ------------------------------------------------------------------

    private static void installEffects() {
        // ── MobEffectEvent.Added ← MobEffectEvents.Apply(addEffect STORE) ──
        MobEffectEvents.APPLY.register((entity, oldEffect, newEffect, source) ->
                LoaderBus.INSTANCE.post(new MobEffectEvent.Added(entity, oldEffect, newEffect, source)));

        // ── MobEffectEvent.Remove ← MobEffectEvents.Remove(removeEffect HEAD) ──
        MobEffectEvents.REMOVE.register((entity, effectInstance) -> {
            // ⚠️ effectInstance **可为 null**:Puzzles 的 REMOVE 回调在「实体本就没有该效果」时同样触发
            //    (库的 ModEffectRemoval.remove 直接走 LivingEntity#removeEffect,不先判存在性)。
            //    Forge 侧同一路径**不派发**事件 —— 其 removeEffect 补丁形如
            //    `MobEffectInstance i = activeEffects.remove(effect); if (i != null) post(new Remove(this, i, effect));`
            //    ⇒ 这里同样跳过:既对齐 Forge 语义,也避免构造器解引用 null。
            //    (2026-09-29 实测:玩家登录时 PlayerLifecycleHandler 清理骰神赐福 → 库里 remove →
            //     本回调传 null → MobEffectEvent.Remove 构造器 NPE → "无效的玩家数据" → **无法进入存档**)
            if (effectInstance == null) {
                return EventResult.PASS;
            }
            // ★ 2026-10-04 修复（Q3 第二道防线）：实例非 null 但该效果**已不在实体身上**时同样跳过派发。
            //    本回调的返回值会被 Puzzles 翻译成 `EventResult.INTERRUPT`（= 阻止移除），而该注入点
            //    不可取消时会抛 `CancellationException: The call removeEffect is not cancellable`。
            //    既然效果已经不在身上，就没有任何东西需要"阻止" ⇒ 直接放行，既对齐 Forge 语义
            //    （其补丁在 `activeEffects.remove(effect)` 返回 null 时同样不派发事件），也彻底堵死该路径。
            if (!entity.hasEffect(Effects.holder(effectInstance.getEffect()))) {
                return EventResult.PASS;
            }
            MobEffectEvent.Remove event = new MobEffectEvent.Remove(entity, effectInstance);
            LoaderBus.INSTANCE.post(event);
            // Forge 取消 ⇒ 阻止这次移除
            return event.isCanceled() ? EventResult.INTERRUPT : EventResult.PASS;
        });

        // ── MobEffectEvent.Expired ← MobEffectEvents.Expire(tickEffects 的 Iterator.remove) ──
        MobEffectEvents.EXPIRE.register((entity, effectInstance) ->
                LoaderBus.INSTANCE.post(new MobEffectEvent.Expired(entity, effectInstance)));

        // ── MobEffectEvent.Applicable ← MobEffectEvents.AFFECTS(canBeAffected HEAD，可中断) ──
        // 取证(puzzles-lib 21.1.66，javap -p -c 于 fuzs.puzzleslib.fabric.mixin.LivingEntityFabricMixin):
        //   @Inject(method = "method_6049", at = @At("HEAD"), cancellable = true)
        //   void canBeAffected(MobEffectInstance, CallbackInfoReturnable<Boolean>)
        //   body: Affects.onMobEffectAffects(...) → if (result.isInterrupt()) cir.setReturnValue(...)
        // 映射：ALLOW = interrupt+true（强制施加）/ DENY = interrupt+false（不施加）/
        //       PASS = 不干预（走原版）。因此无需自写 mixin，也不需要 EventResult.INTERRUPT
        //       （后者 value 恒为 FALSE，语义上等于 DENY）。
        MobEffectEvents.AFFECTS.register((entity, effectInstance) -> {
            MobEffectEvent.Applicable event = new MobEffectEvent.Applicable(entity, effectInstance);
            LoaderBus.INSTANCE.post(event);
            // 枚举 switch 不限定常量名 ⇒ 无需 import 本线的 Event.Result
            return switch (event.getResult()) {
                case ALLOW -> EventResult.ALLOW;
                case DENY -> EventResult.DENY;
                case DEFAULT -> EventResult.PASS;
            };
        });
    }

    // ------------------------------------------------------------------
    // 世界:方块破坏
    // ------------------------------------------------------------------

    private static void installWorld() {
        BlockEvents.BREAK.register((level, pos, state, player, tool) -> {
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
            LoaderBus.INSTANCE.post(event);
            return event.isCanceled() ? EventResult.INTERRUPT : EventResult.PASS;
        });
    }

    // ------------------------------------------------------------------
    // 玩家:克隆 / 拾取 / 铁砧
    // ------------------------------------------------------------------

    private static void installPlayers() {
        // ── PlayerEvent.Clone ← PlayerCopyEvents.Copy ──（void 回调，无取消语义）
        PlayerCopyEvents.COPY.register((newPlayer, oldPlayer, wasDeath) ->
                LoaderBus.INSTANCE.post(new PlayerEvent.Clone(newPlayer, oldPlayer, wasDeath)));

        // ── EntityItemPickupEvent ← ItemEntityEvents.TOUCH（2026-11-05 换接线） ──
        // 为什么不是 PICKUP：Puzzles 的 PICKUP 回调在 `Inventory#add` **之后**、`Player#take` 之前触发，
        // 此时 `ItemEntity#getItem()` 已被原版合并**清空**（实测 count=0），消费方读到的永远是空栈
        // ⇒ 星币「获得即入钱包」在 fabric 线静默失效（neo 线同一场景入账 3，fabric 线入账 0、物品照常进包）。
        // TOUCH 是 `playerTouch` 的 HEAD 注入，早于任何消费 ⇒ 语义与 Forge/Neo 的
        // `EntityItemPickupEvent`（入包之前、可取消）对齐。
        // 取消语义：TOUCH 的回调返回 EventResult —— INTERRUPT 才会 `CallbackInfo.cancel()`。
        // 本模组唯一会取消的消费方是 economy/StarCoinPickupHandler（它自己 discard 掉实体），
        // 其余消费方（LinglingSignEvents / TemporaryCardEvents）只改掉落物内容、不取消。
        ItemEntityEvents.TOUCH.register((player, itemEntity) -> {
            EntityItemPickupEvent event = new EntityItemPickupEvent(player, itemEntity);
            LoaderBus.INSTANCE.post(event);
            return event.isCanceled() ? EventResult.DENY : EventResult.PASS;
        });

        // ── AnvilUpdateEvent ← AnvilUpdateCallback ──
        // Forge 语义：事件被取消 ⇒ 无输出；否则 output 非空 ⇒ 用事件给的 output/cost/materialCost；
        //              output 为空 ⇒ 走原版。这里逐条对照，避免把 EMPTY/0 误写回去覆盖原版计算。
        AnvilEvents.UPDATE.register((left, right, output, name, xpCost, materialCost, player) -> {
            AnvilUpdateEvent event = new AnvilUpdateEvent(left, right, name, xpCost.getAsInt(), player);
            LoaderBus.INSTANCE.post(event);
            if (event.isCanceled()) {
                return EventResult.INTERRUPT;
            }
            ItemStack produced = event.getOutput();
            if (produced != null && !produced.isEmpty()) {
                output.accept(produced);
                xpCost.accept(event.getCost());
                materialCost.accept(event.getMaterialCost());
                // ⚠️ 这里必须回 **interrupt 类**结果(ALLOW)，不能是 PASS：
                //    Puzzles 的 AnvilMenuFabricMixin#createResult 开头是 `if (result.isPass()) return;`
                //    —— PASS = 不干预、直接放行原版，上面写进 MutableValue/MutableInt 的
                //    output / cost / materialCost **全部被丢弃**。实测后果：铁砧升星在 fabric 线
                //    根本不产出成品（点击无反应、输入不被消耗）。
                //    ALLOW = interrupt + true，才会走到 `resultSlots.setItem(0, output.get())`
                //    并写回 cost / repairItemCountCost。
                return EventResult.ALLOW;
            }
            return EventResult.PASS;
        });

        // ── AnvilRepairEvent ← AnvilRepairCallback ──（void 回调，无取消语义）
        // Forge 的 AnvilRepairEvent 在「玩家真的取走成品」时派发一次（ForgeHooks.onAnvilRepair，
        // 位于 AnvilMenu#onTake 里扣材料 / 清空输入槽之前）。Puzzles 的注入点是
        // AnvilMenuFabricMixin#onTake$0（Container#setItem 之前），语义位置一致；
        // 其 onTake$1 再用 MutableFloat 的**终值**判铁砧损坏 ⇒ 这里把事件改过的 breakChance 写回。
        AnvilEvents.USE.register((player, left, right, output, breakChance) -> {
            AnvilRepairEvent event = new AnvilRepairEvent(player, left, right, output);
            LoaderBus.INSTANCE.post(event);
            if (event.getBreakChance() != breakChance.getAsFloat()) {
                breakChance.accept(event.getBreakChance());
            }
        });
    }

    private PuzzlesBridges() {
    }
}
