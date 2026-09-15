package com.merlinkitsune.astral_dice.item.sign;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import com.merlinkitsune.astral_dice.network.ModNetwork;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.ModEffectRemoval;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import com.merlinkitsune.astral_dice.item.chip.FanBigChipItem;
import com.merlinkitsune.astral_dice.item.chip.FanSmallChipItem;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;

public abstract class BaseSignItem extends Item implements ICurioItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseSignItem.class);
    public BaseSignItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        // 立牌只能放入"stand"饰品栏
        if (!"stand".equals(slotContext.identifier())) return false;
        // 禁止重复装备相同的立牌(服务端校验;客户端直接放行避免误判)
        if (slotContext.entity().level().isClientSide()) return true;
        boolean dup = CurioSlotUtil.hasSameItemEquipped(slotContext.entity(), stack);
        if (dup) {
            LOGGER.warn("[Astral Dice][canEquip] 立牌被判定为重复装备被拒: item={}",
                    stack);
        }
        return !dup;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity().level().isClientSide()) return;

        onCurioTick(slotContext, stack);
    }

    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
    }

    /**
     * 立牌主动技能触发(服务端):触发立牌栏(唯一槽位)中立牌的技能。
     * 判定顺序:
     * 0. 锁定(生效中)态(第二批「三态化」):本主动施加的计时器仍在跑时按键无效;
     * 1. 玩家级冷却(不受立牌装卸影响):冷却中按键无效;
     * 2. 等待状态(占星师/秘密侦探等需指定目标的技能):等待完成或超时前按键保持无效;
     * 3. 触发成功:施加了计时器的技能进入锁定态(生效中),锁定结束再起冷却;
     *    其余技能立即开始玩家级冷却;等待类技能待其完成指定目标/超时后再计算。
     */
    public static void performSkillForCurio(Player player) {
        if (player.level().isClientSide()) return;
        // 读取立牌栏(唯一槽位)的立牌:用于技能触发与提示前缀(立牌名称)
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return;
        var handlerOpt = curios.get().getStacksHandler("stand");
        if (handlerOpt.isEmpty()) return;
        var handler = handlerOpt.get();
        if (handler.getSlots() <= 0) return;
        ItemStack stack = handler.getStacks().getStackInSlot(0);
        performSkill(player, stack);
    }

    // 服务端统一执行立牌主动技能(立牌栏触发与手持立牌右键共用,保证冷却/等待/扇子筹码逻辑一致):
    // 0. 锁定(生效中)态(第二批「三态化」):本主动施加的计时器仍在跑时按键无效;
    // 1. 玩家级冷却(不受立牌装卸影响):冷却中按键无效;
    // 2. 等待状态(占星师/秘密侦探等需指定目标的技能):等待完成或超时前按键保持无效;
    // 3. 触发成功:施加了计时器的技能进入锁定态(生效中),锁定结束再起冷却;
    //    其余技能立即开始玩家级冷却;等待类技能待其完成指定目标/超时后再计算。
    private static void performSkill(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof BaseSignItem sign)) return;
        long now = player.level().getGameTime();
        net.minecraft.network.chat.Component signName = stack.getHoverName();
        // 0. 锁定(生效中)态检查(第二批「三态化」第 1/4 条):本主动技能施加的计时器仍在跑时按键无效,
        //    提示"<立牌名>主动技能生效中!"。判定**置于冷却分支之前**,因此锁定期内电流核心筹码
        //    根本走不到冷却分支(不触发、不扣充能),第 5 条自然满足。
        if (isSignActiveLocked(player)) {
            notifyActionBar(player, "msg.astral_dice.sign_active_in_effect", signName, ChatFormatting.YELLOW);
            return;
        }
        // 1. 玩家级冷却检查:冷却中按键默认无效,并明确提示"<立牌名>冷却中"(修复:触发成功与冷却拒绝的反馈混淆)
        //    电流核心筹码:冷却中按下主动技能键 → 按剩余冷却占比消耗充能并立即使冷却完成(佩戴且充能足够时)
        long cdEnd = ModAttachments.getSignActiveCooldownEnd(player);
        if (cdEnd > 0 && now < cdEnd) {
            int coreResult = com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem
                    .tryFinishCooldown(player, cdEnd, now);
            if (coreResult != com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.FINISH_NONE) {
                // 已完成冷却(等待玩家再次按键释放)或充能不足(已提示):不再叠加默认冷却提示
                return;
            }
            notifyActionBar(player, "hud.astral_dice.sign_active_cooldown", signName, ChatFormatting.RED);
            return;
        }
        // 2. 等待状态检查:存在等待目标释放的主动技能时按键无效
        if (isSkillWaiting(player)) return;
        // 3. 触发主动技能
        InteractionResultHolder<ItemStack> result = sign.handleUse(player.level(), player, stack);
        if (result.getResult() != InteractionResult.SUCCESS) return;
        // 4. 手持风扇-大筹码:使用主动技能后,获得一张随机效果牌(不含专属),并对周围范围内敌对目标施加标记
        FanBigChipItem.applyAfterSignSkill(player);
        FanSmallChipItem.applyAfterSignSkill(player);
        // 5. 立牌主动技能响应事件:立牌类订阅本事件注册自身 ActionBar 反馈(见 SignActiveTriggeredEvent);
        //    无任何处理器响应(未注册)时,发送默认提示"xxx立牌:主动技能已启动!"
        com.merlinkitsune.astral_dice.event.SignActiveTriggeredEvent triggered =
                new com.merlinkitsune.astral_dice.event.SignActiveTriggeredEvent(player, stack);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(triggered);
        if (!triggered.isHandled()) {
            notifyActionBar(player, "msg.astral_dice.sign_active_triggered", signName, ChatFormatting.YELLOW);
        }
        // 6. 冷却:等待类技能(激活了玩家级等待状态)待完成指定目标/超时后再开始冷却;其余立牌立即开始玩家级冷却
        //    2026-09-15 用户裁决(S6-C2,状态与计时器分离):门槛只看"当前是否处于待命状态"(sign_ready_type),
        //    不再看原始计时器数值(sign_ready_expire)——**陈旧的正计时器不得阻止冷却**:
        //    立牌离身后残留的计时器(如死亡掉落,Curios 不走 onUnequip)曾让旧门槛永远成立,
        //    使该玩家任何立牌的主动技能都不再进入冷却(可无限连发);
        //    待命超时(计时器归 0)现由玩家级 tick 自动重置状态,见 tickSignReadyTimeout。
        if (ModAttachments.getSignReadyType(player) <= 0) {
            // 诡异骰子:立牌主动冷却 -50%
            int signCooldownTicks = com.merlinkitsune.astral_dice.event.WeirdDiceHandler.signCooldownTicks(player);
            if (sign.startActiveLockOnUse(player, now)) {
                // ★ 本主动施加了"带时长效果/自身计时器"⇒ 进入锁定(生效中)态。
                //   锁定期间**不写** sign_active_cooldown_end:冷却要等锁定结束才起(第 13 条:无空档);
                //   但先把本次冷却基准写进 sign_active_max_cooldown,供锁定期间各减免方读它并累加进减免池。
                ModAttachments.setSignActiveMaxCooldown(player, signCooldownTicks);
            } else {
                ModAttachments.setSignActiveCooldownEnd(player, now + signCooldownTicks);
                // 路线 A:记录本次冷却实际使用的最大冷却值,所有减免方一律读它(不再各自重算基准)
                ModAttachments.setSignActiveMaxCooldown(player, signCooldownTicks);
            }
            // 电流核心筹码:主动技能实际生效时充能 +1(进入锁定时同样计一次;与是否立即起冷却无关)
            com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem.onActiveSkillUsed(player);
        }
    }

    // 立牌主动技能反馈统一发送入口(黄色;供立牌类注册的 SignActiveTriggeredEvent 处理器与 handleUse 调用)
    protected static void sendSignActionBar(Player player, String langKey, Object... args) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        net.minecraft.network.chat.Component msg =
                net.minecraft.network.chat.Component.translatable(langKey, args).withStyle(ChatFormatting.YELLOW);
        ModNetwork.sendToPlayer(serverPlayer,
                new ModNetwork.ActionBarMessage(msg,
                        GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    // 服务端发送立牌技能反馈(actionbar 提示,带立牌名称前缀;统一由服务端判定成功/拒绝,避免客户端推测混淆)
    private static void notifyActionBar(Player player, String langKey, net.minecraft.network.chat.Component signName, ChatFormatting color) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        net.minecraft.network.chat.Component msg =
                net.minecraft.network.chat.Component.translatable(langKey, signName).withStyle(color);
        ModNetwork.sendToPlayer(serverPlayer,
                new ModNetwork.ActionBarMessage(msg,
                        GameplayConstants.ACTIONBAR_DURATION_TICKS));
    }

    // 是否存在等待目标释放的主动技能(占星师/秘密侦探等,等待期间按键无效)
    // 说明(S6-C2):本方法语义保持不变——等待必须同时具备"待命状态"且"尚未过期";
    // 它与 performSkill 里的冷却门槛是两件事:冷却门槛只看状态(sign_ready_type),本方法还要求未过期。
    private static boolean isSkillWaiting(Player player) {
        long expire = ModAttachments.getSignReadyExpire(player);
        return ModAttachments.getSignReadyType(player) > 0 && expire > 0
                && player.level().getGameTime() < expire;
    }

    /**
     * 立牌"待命"状态与计时器分离(S6-C2,2026-09-15 用户裁决):计时器归 0/已过期即自动重置待命状态。
     *
     * <p>为什么必须挂在**玩家级 tick**(见 {@code event/PlayerTickEvents} 的每 tick 服务端处理):
     * 原来的"超时清除"写在各立牌自己的 {@code onCurioTick} 里,而 onCurioTick 只在立牌仍佩戴时执行
     * (Curios 的部分移除路径——如死亡掉落 {@code handleDrops}——根本不会回调 onUnequip)。
     * 立牌离身后残留的正计时器会让旧门槛({@code sign_ready_expire > 0})永远成立,
     * 使该玩家**任何立牌**的主动技能都不再进入冷却(可无限连发)。改为玩家级后,与立牌是否在槽位无关。
     *
     * <p>本方法只负责"归零状态 + 清计时器 + 移除对应提示效果",不涉及主动技能冷却
     * (冷却由攻击命中释放路径 {@code combat/DiceCombatEvents} 或 performSkill 开始)。
     */
    public static void tickSignReadyTimeout(Player player) {
        if (player == null) return;
        if (player.level().isClientSide()) return;
        int type = ModAttachments.getSignReadyType(player);
        if (type <= 0) return;
        long expire = ModAttachments.getSignReadyExpire(player);
        // 计时器仍有效(未归 0 且未到期):等待继续,不做处理
        if (expire > 0 && player.level().getGameTime() < expire) return;
        // 计时器归 0:自动重置待命状态并移除对应的"待命"提示效果
        ModAttachments.setSignReadyType(player, 0);
        ModAttachments.setSignReadyExpire(player, 0);
        if (type == HaiqingSignItem.READY_TYPE) {
            ModEffectRemoval.remove(player, ModEffects.HAIQING_READY.get());
        } else if (type == BonnieSignItem.READY_TYPE) {
            ModEffectRemoval.remove(player, ModEffects.BONNIE_READY.get());
        } else if (type == MosesSignItem.READY_TYPE) {
            ModEffectRemoval.remove(player, ModEffects.MOSES_READY.get());
        }
    }

    // ===== 立牌主动技能"三态化"(可用 / 锁定-生效中 / 冷却)=====
    // 第二批改动(见 docs/batch2/PLAN.md §3):主动技能施加的"带时长效果 / 自身计时器"跑完之前处于
    // **锁定(生效中)**态,期间按键无效(提示 msg.astral_dice.sign_active_in_effect);
    // 锁定结束**必起冷却**(无空档),并一次性抵扣锁定期间累计的冷却减免池。

    /** 忍者立牌的锁定标记(其主动无任何自身计时器,锁定跟随出牌周期,由周期完全重置结束) */
    public static final String KOMACHI_LOCK_ID = "astral_dice:komachi_sign";

    /** 正在生效中的主动所属立牌注册 id("" = 未锁定) */
    public static String getSignActiveLockSignId(Player player) {
        String signId = ModAttachments.getSignActiveLockSign(player);
        return signId == null ? "" : signId;
    }

    /**
     * 锁定(生效中)态判定(第 1/4 条):"本技能施加的计时器"是否仍在跑。
     *
     * <p>判据 = {@code sign_active_lock_end}(触发时刻算定的全部计时器到期刻取 max,**硬上界**)尚未到期,
     * <b>且</b>本立牌的门控效果实例仍在 —— 后者只让锁定**提前**结束(如骇客被攻击破隐、
     * 效果被其它路径移除),外部把同一效果刷新得更长**不会**延长锁定(硬上界不随刷新重算)。
     *
     * <p>效果严格按 20t/s 流动由 {@link com.merlinkitsune.astral_dice.event.EffectTimerGuard} 保证,
     * 故效果实例的 {@code getDuration()} 即权威剩余时长;本判定**不硬编码任何时长表**,
     * 也**不复用** {@link com.merlinkitsune.astral_dice.item.card.EffectCardPeriod} 的
     * {@code EFFECT_PENDING_SOURCES}(那是"效果牌出牌锁"的语义域,与立牌主动无关)。
     *
     * <p>忍者(无自身计时器,{@code lock_end == 0})只由"锁定标记是否仍在"判定,
     * 其结束有两条出口:宽限到期未出过任何效果牌(见 {@link #tickSignActiveLock})、
     * 或出牌周期完全重置(见 {@link #onEffectCardRoundReset})。
     */
    public static boolean isSignActiveLocked(Player player) {
        if (player == null) return false;
        String signId = getSignActiveLockSignId(player);
        if (signId.isEmpty()) return false;
        long lockEnd = ModAttachments.getSignActiveLockEnd(player);
        if (lockEnd > 0 && player.level().getGameTime() >= lockEnd) return false;
        BaseSignItem sign = lockSignItem(signId);
        if (sign == null) {
            // 立牌实例解析失败(锁定标记写的是物品注册 id,理论上不会发生):
            // 有硬上界时按上方已判过的上界继续锁定;无硬上界(忍者)时判为未锁定,避免永久锁死
            return lockEnd > 0;
        }
        return sign.isGateEffectActive(player);
    }

    // 按立牌注册 id 取回立牌实例(锁定态判定需要回调具体立牌覆写的"门控效果仍在")
    private static BaseSignItem lockSignItem(String signId) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(signId);
        if (id == null) return null;
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        return item instanceof BaseSignItem sign ? sign : null;
    }

    /**
     * 本主动施加的"门控效果"实例是否仍在生效中(默认 true ⇒ 只按 {@code sign_active_lock_end} 硬上界判定)。
     * 效果可被其它路径提前移除的立牌覆写本方法(如骇客的隐身被攻击解除),使锁定随之提前结束。
     */
    protected boolean isGateEffectActive(Player player) {
        return true;
    }

    /**
     * 各立牌覆写:本次主动触发成功时登记"本技能施加的计时器"(写 {@code sign_active_lock_end}),
     * 返回 true = 需要进入锁定(生效中)态(而非立即起冷却)。默认 false(无计时器 ⇒ 与改动前逐字一致)。
     *
     * <p>无法用固定效果常量覆盖的立牌(随机效果)在本方法内读回**刚施加的效果实例**剩余时长,
     * 不新增全局注册表、不硬编码时长表。
     */
    protected boolean startActiveLockOnUse(Player player, long now) {
        return false;
    }

    /** 进入锁定态:写锁定标记与硬上界(宽限/减免池归零,避免跨技能残留) */
    protected static void beginActiveLock(Player player, String signId, long lockEndTick) {
        ModAttachments.setSignActiveLockSign(player, signId);
        ModAttachments.setSignActiveLockEnd(player, lockEndTick);
        ModAttachments.setSignActiveLockGraceEnd(player, 0L);
        ModAttachments.setSignActiveLockPlayed(player, false);
        ModAttachments.setSignActiveReductionPool(player, 0L);
    }

    /**
     * 结束锁定并进入冷却(唯一出口,第 5/13 条):一次性抵扣锁定期间累计的减免池,
     * {@code effective = max(0, 基准 − 池)};写 {@code sign_active_cooldown_end = now + effective}
     * 并把 {@code sign_active_max_cooldown} 改写为 {@code effective}(电流核心档位分母取抵扣后的实际冷却),
     * 随后清空全部锁定键。锁定态下{@code cooldown_end} 为 0,故本方法一执行即"无空档"地进入冷却。
     */
    private static void endLockAndStartCooldown(Player player) {
        long now = player.level().getGameTime();
        long base = ModAttachments.getSignActiveMaxCooldown(player);
        long pool = ModAttachments.getSignActiveReductionPool(player);
        long effective = Math.max(0L, base - pool);
        ModAttachments.setSignActiveCooldownEnd(player, now + effective);
        ModAttachments.setSignActiveMaxCooldown(player, effective);
        ModAttachments.setSignActiveReductionPool(player, 0L);
        ModAttachments.setSignActiveLockSign(player, "");
        ModAttachments.setSignActiveLockEnd(player, 0L);
        ModAttachments.setSignActiveLockGraceEnd(player, 0L);
        ModAttachments.setSignActiveLockPlayed(player, false);
    }

    /**
     * 玩家级 tick(幂等):1. 忍者宽限保险;2. 其余立牌锁定结束时必起冷却。
     *
     * <p>为什么挂在**玩家级 tick**(见 {@code event/PlayerTickEvents}):与 {@link #tickSignReadyTimeout}
     * 同理,判定必须与立牌是否仍在饰品槽无关;并且玩家离线时不 tick ⇒ 锁定结束那一刻离线的话,
     * 由上线后的第一次判定迁移到冷却(等效"离线期间冷却不走")。
     *
     * <p>幂等性:本方法随 {@code TickEvent.PlayerTickEvent} 的 START/END 两个阶段**每 tick 被调用两次**,
     * 首行按"是否仍在锁定"早退,真正的迁移只发生一次(迁移后锁定标记被清空,第二次执行直接返回),
     * 与 tickSignReadyTimeout 同构。
     */
    public static void tickSignActiveLock(Player player) {
        if (player == null) return;
        if (player.level().isClientSide()) return;
        String signId = getSignActiveLockSignId(player);
        if (signId.isEmpty()) return;
        long now = player.level().getGameTime();
        if (KOMACHI_LOCK_ID.equals(signId)) {
            // 忍者:锁定跟随出牌周期,本方法只负责"宽限 1:00 内自始至终未出任何效果牌"的保险。
            // 其余出口是出牌周期完全重置(EffectCardPeriod -> onEffectCardRoundReset)。
            long graceEnd = ModAttachments.getSignActiveLockGraceEnd(player);
            if (graceEnd <= 0 || now < graceEnd) return;
            if (ModAttachments.getSignActiveLockPlayed(player)) {
                // 宽限期内出过效果牌 ⇒ 保险失效:清宽限刻,遵循出牌周期(等周期完全重置)
                ModAttachments.setSignActiveLockGraceEnd(player, 0L);
                return;
            }
            // 宽限期内自始至终未出任何效果牌 ⇒ 强制重置出牌状态,并让主动技能进入冷却
            com.merlinkitsune.astral_dice.item.card.EffectCardPeriod.forceResetRound(player);
            endLockAndStartCooldown(player);
            return;
        }
        if (isSignActiveLocked(player)) return;   // 门控计时器仍在跑:保持锁定
        endLockAndStartCooldown(player);          // 锁定结束:必起冷却(第 13 条,无空档)
    }

    /**
     * 出牌轮"完全重置"回调(忍者专用,第 4 条):由 {@link com.merlinkitsune.astral_dice.item.card.EffectCardPeriod}
     * 的**两处**周期边界调用({@code tick} 情形 1 与 {@code registerPlay} 的边界块),
     * 使忍者的主动冷却从"该轮出牌状态完全重置那一刻"开始。
     */
    public static void onEffectCardRoundReset(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!KOMACHI_LOCK_ID.equals(getSignActiveLockSignId(player))) return;
        endLockAndStartCooldown(player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 下蹲右键:自动装备到"stand"饰品栏
        if (player.isShiftKeyDown()) {
            return CurioSlotUtil.tryAutoEquip(player, stack, "stand");
        }
        // 右键行为仅为装备:主动技能统一由快捷键(立牌栏)触发,手持右键不触发任何技能
        return InteractionResultHolder.fail(stack);
    }

    // 立牌被移除时:清除该立牌获得的增益/计数器/累计值,防止反复更换立牌实现效果叠加。
    // 主动技能冷却为玩家级(ModAttachments.SIGN_ACTIVE_COOLDOWN_END),不受立牌装卸影响。
    // 语义(2026-09-15 用户裁决,S6-C1):只有"玩家有意卸除"才清理;Curios 自身原因导致的重载
    // (from=to 同一物品、物品仍留在槽位)不清理,否则治愈点数等累计值会被反复清零。
    // Curios 官方签名为 onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack):
    //   第 2 参 newStack 是"将要占用槽位的栈"(玩家真正卸下时为 EMPTY,换装时为新放入的那件),
    //   第 3 参 stack 才是**被卸下的那件饰品**。旧实现把第 2 参当成被卸下的物品,后果是:
    //   clearSignData 的组件归零写到了 newStack(常为 EMPTY,会污染 ItemStack.EMPTY 这个全局单例)上,
    //   被卸下的立牌自身反而没被清零;旧 stillInSlot 判据又用第 2 参比对槽位内容,换装时误判"仍在槽位"而跳过整段清理。
    // 现在:判据走 CurioSlotUtil.isIntentionalUnequip(只依赖 Curios 的两个参数,不再查槽位内容),
    //       清理对象固定为"被卸下的那个栈"(第 3 参),各立牌 clearSignData 的组件归零才会落在正确的物品上。
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!CurioSlotUtil.isIntentionalUnequip(newStack, stack)) return;
        clearSignData(player, stack);
    }

    // 各立牌覆写以清除自身累计数据
    protected void clearSignData(Player player, ItemStack stack) {
    }

    // === 战斗钩子(供事件系统统一分发;子类覆写以响应玩家级事件) ===

    /**
     * 佩戴本立牌的玩家造成击杀时触发(由 {@link #invokeKillHooks} 分发)。
     * 子类覆写以实现击杀类被动(如秘密侦探/占星师的击杀奖励)。
     */
    protected void onKill(Player killer, net.minecraft.world.entity.LivingEntity killed) {
    }

    /**
     * 佩戴本立牌的玩家受到伤害时触发(由 {@link #invokeHurtHooks} 分发)。
     * 子类覆写以实现受击类被动(如史莱姆立牌的受击 +1 治愈)。
     */
    protected void onHurt(Player player, float amount) {
    }

    // 分发:玩家造成击杀时,调用其全部已装备立牌的 onKill 钩子
    public static void invokeKillHooks(Player killer, net.minecraft.world.entity.LivingEntity killed) {
        if (killer == null || killer.level().isClientSide()) return;
        CuriosCompat.getCuriosInventory(killer).ifPresent(handler -> {
            var results = handler.findCurios(s -> s.getItem() instanceof BaseSignItem);
            for (var r : results) {
                if (r.stack().getItem() instanceof BaseSignItem sign) {
                    sign.onKill(killer, killed);
                }
            }
        });
    }

    // 分发:玩家受到伤害时,调用其全部已装备立牌的 onHurt 钩子
    public static void invokeHurtHooks(Player player, float amount) {
        if (player == null || player.level().isClientSide()) return;
        CuriosCompat.getCuriosInventory(player).ifPresent(handler -> {
            var results = handler.findCurios(s -> s.getItem() instanceof BaseSignItem);
            for (var r : results) {
                if (r.stack().getItem() instanceof BaseSignItem sign) {
                    sign.onHurt(player, amount);
                }
            }
        });
    }

    protected abstract InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack);
}
