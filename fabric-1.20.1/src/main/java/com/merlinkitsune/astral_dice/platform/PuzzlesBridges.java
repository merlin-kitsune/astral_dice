package com.merlinkitsune.astral_dice.platform;

import com.merlinkitsune.astral_dice.platform.event.AnvilUpdateEvent;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
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
import fuzs.puzzleslib.api.event.v1.entity.player.AnvilUpdateCallback;
import fuzs.puzzleslib.api.event.v1.entity.player.PlayerEvents;
import fuzs.puzzleslib.api.event.v1.level.BlockEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

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
 *   <tr><td>{@code LivingChangeTargetCallback}</td><td>{@code Mob#setTarget}</td>
 *       <td>{@link LivingChangeTargetEvent}（targetType = {@code MOB_TARGET}）</td></tr>
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
                "[Astral Dice] Puzzles 事件桥已安装:伤害/死亡/掉落/目标/效果×3/方块/克隆/拾取/铁砧");
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
        LivingDropsCallback.EVENT.register((entity, source, drops, lootingLevel, recentlyHit) -> {
            LivingDropsEvent event = new LivingDropsEvent(entity, source, drops, lootingLevel, recentlyHit);
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
            MobEffectEvent.Remove event = new MobEffectEvent.Remove(entity, effectInstance);
            LoaderBus.INSTANCE.post(event);
            // Forge 取消 ⇒ 阻止这次移除
            return event.isCanceled() ? EventResult.INTERRUPT : EventResult.PASS;
        });

        // ── MobEffectEvent.Expired ← MobEffectEvents.Expire(tickEffects 的 Iterator.remove) ──
        MobEffectEvents.EXPIRE.register((entity, effectInstance) ->
                LoaderBus.INSTANCE.post(new MobEffectEvent.Expired(entity, effectInstance)));
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
        // ── PlayerEvent.Clone ← PlayerEvents.Copy ──（void 回调，无取消语义）
        PlayerEvents.COPY.register((newPlayer, oldPlayer, wasDeath) ->
                LoaderBus.INSTANCE.post(new PlayerEvent.Clone(newPlayer, oldPlayer, wasDeath)));

        // ⚠️ Puzzles 的 ItemPickup 是 void(只通知、不可取消)。Forge 的 EntityItemPickupEvent
        //    可取消(取消 = 不拾取)。本模组当前唯一的消费方(economy/StarCoinPickupHandler)
        //    只做「拾取后入账」，不取消 ⇒ 此处按通知语义接线；若将来需要取消拾取，
        //    必须改走自写 mixin（Puzzles 无能力）。已在 PORT_STATUS_HANDOVER 登记。
        PlayerEvents.ITEM_PICKUP.register((player, itemEntity, stack) ->
                LoaderBus.INSTANCE.post(new EntityItemPickupEvent(player, itemEntity)));

        // ── AnvilUpdateEvent ← AnvilUpdateCallback ──
        // Forge 语义：事件被取消 ⇒ 无输出；否则 output 非空 ⇒ 用事件给的 output/cost/materialCost；
        //              output 为空 ⇒ 走原版。这里逐条对照，避免把 EMPTY/0 误写回去覆盖原版计算。
        AnvilUpdateCallback.EVENT.register((left, right, output, name, xpCost, materialCost, player) -> {
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
            }
            return EventResult.PASS;
        });
    }

    private PuzzlesBridges() {
    }
}
