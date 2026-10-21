package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * 立牌主动技能「三态化」状态机(可用 / 锁定-生效中 / 冷却)—— 自 {@link BaseSignItem} 原样抽出。
 *
 * <p><b>本类只承载状态机本体,不含任何判定口径变化</b>:锁定/宽限/离线补偿/减免池抵扣/
 * 忍者宽限保险的判定顺序、阈值、附件键与写入时机与抽出前逐字一致。
 *
 * <p>立牌侧仍需保留在 {@link BaseSignItem} 的**可覆写**钩子({@code isGateEffectActive} /
 * {@code startActiveLockOnUse} / {@code forcedActiveCooldownTicks} / {@code forcedCooldownUntil})
 * 由本类经实例回调读取 —— 它们是虚方法,搬走会破坏 35 个立牌的覆写。
 */
public final class SignActiveLockMachine {
    private SignActiveLockMachine() {
    }

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
     * <p>效果严格按 20t/s 流动由 {@link com.merlinkitsune.starengine.event.EffectTimerGuard} 保证,
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
        ResourceLocation id = ResourceLocation.tryParse(signId);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item instanceof BaseSignItem sign ? sign : null;
    }

    /** 进入锁定态:写锁定标记与硬上界(宽限/减免池归零,避免跨技能残留) */
    public static void beginActiveLock(Player player, String signId, long lockEndTick) {
        ModAttachments.setSignActiveLockSign(player, signId);
        ModAttachments.setSignActiveLockEnd(player, lockEndTick);
        ModAttachments.setSignActiveLockGraceEnd(player, 0L);
        ModAttachments.setSignActiveLockPlayed(player, false);
        ModAttachments.setSignActiveReductionPool(player, 0L);
        // 离线补偿基准必须在此一并落笔:上一次锁定可能早已结束(lastSeen 陈值/0),若留到 tick 里首次
        // 写入,则"上一次锁定结束时的 lastSeen"与"本次触发时刻"之间会被误判成一次离线 gap,把上界凭空后移。
        ModAttachments.setSignActiveLockLastSeen(player, player.level().getGameTime());
    }

    /**
     * 结束锁定并进入冷却(唯一出口,第 5/13 条):一次性抵扣锁定期间累计的减免池,
     * {@code effective = max(0, 基准 − 池)};写 {@code sign_active_cooldown_end = now + effective}
     * 并把 {@code sign_active_max_cooldown} 改写为 {@code effective}(电流核心档位分母取抵扣后的实际冷却),
     * 随后清空全部锁定键。锁定态下{@code cooldown_end} 为 0,故本方法一执行即"无空档"地进入冷却。
     *
     * <p><b>锁定后免冷却(2026-10-19 FX1 回归修复)</b>:所属立牌覆写
     * {@link BaseSignItem#skipCooldownAfterLock()} 返回 true 时(当前:蛟龙 mamushi)本次迁移
     * **不写冷却** —— {@code effective = 0} ⇒ {@code cooldown_end = now},冷却判定
     * {@code now < cdEnd} 恒为假 ⇒ 锁定态跑完即可再次释放,等价旧「强制冷却 1:00、不可减免」:
     * 锁定期间第 0 步 {@link #isSignActiveLocked} 已先拦下按键,电流核心/诡异骰子/充能减免全都走不到。
     * 立牌实例解析失败(理论上不发生)时按缺省口径起冷却,不会把"免冷却"误用到别的立牌。
     */
    private static void endLockAndStartCooldown(Player player) {
        long now = player.level().getGameTime();
        BaseSignItem lockSign = lockSignItem(getSignActiveLockSignId(player));
        boolean skipCooldown = lockSign != null && lockSign.skipCooldownAfterLock();
        long base = ModAttachments.getSignActiveMaxCooldown(player);
        long pool = ModAttachments.getSignActiveReductionPool(player);
        long effective = skipCooldown ? 0L : Math.max(0L, base - pool);
        // 同处把「未折电池」基准改写成同一算式的结果(基础值换成**该立牌**的 no-battery 版本,
        // 减去同一个 pool、Math.max(0,...) 兜底):命运指引卡只读 sign_active_max_cooldown_base,
        // 必须与 sign_active_max_cooldown 同源同步,否则锁定期入池的减免与基准脱节。
        // 立牌实例解析失败(理论上不发生)时退回锁定起点记录的基准值。
        long baseNoBattery = lockSign != null
                ? lockSign.activeCooldownBaseTicksNoBattery(player)
                : ModAttachments.getSignActiveMaxCooldownBase(player);
        long effectiveBase = skipCooldown ? 0L : Math.max(0L, baseNoBattery - pool);
        ModAttachments.setSignActiveCooldownEnd(player, now + effective);
        ModAttachments.setSignActiveMaxCooldown(player, effective);
        ModAttachments.setSignActiveMaxCooldownBase(player, effectiveBase);
        ModAttachments.setSignActiveReductionPool(player, 0L);
        ModAttachments.setSignActiveLockSign(player, "");
        ModAttachments.setSignActiveLockEnd(player, 0L);
        ModAttachments.setSignActiveLockGraceEnd(player, 0L);
        ModAttachments.setSignActiveLockPlayed(player, false);
        // 解锁即清零离线补偿基准:下一次锁定由 beginActiveLock 重新落笔,避免"本次锁定结束刻"被
        // 之后某次 tick 当成离线起点算出巨大 gap(冷却是墙钟语义,不参与补偿,故此处一并归零)。
        ModAttachments.setSignActiveLockLastSeen(player, 0L);
        // 诡异骰子(2026-10-20):主动技能**结束**(锁定态寿终 = 技能效果真正到期)⇒ 获得 5 点治愈。
        // 此时冷却刚入账,但结算口径是「生效期结束」而不是「冷却开始」——本方法是锁定态唯一出口。
        com.merlinkitsune.astral_dice.event.WeirdDiceHandler.onSignActiveEnded(player);
    }

    /**
     * 玩家级 tick(幂等):0. 离线补偿;1. 忍者宽限保险;2. 其余立牌锁定结束时必起冷却。
     *
     * <p>为什么挂在**玩家级 tick**(见 {@code event/PlayerTickEvents}):
     * 判定必须与立牌是否仍在饰品槽无关;并且玩家离线时不 tick ⇒ 锁定结束那一刻离线的话,
     * 由上线后的第一次判定迁移到冷却(等效"离线期间冷却不走")。
     *
     * <p><b>步骤 0 = 让上面这条本意对"硬上界"也成立</b>:门控效果的剩余时长只在
     * {@code LivingEntity#tickEffects} 里递减(玩家离线期间冻结),而 {@code sign_active_lock_end} /
     * {@code sign_active_lock_grace_end} 是**绝对 gameTime**(服务器在跑就照常推进)⇒ 多人服务器上
     * 「离线时长 &gt; 上界剩余」后重登会看到"上界已过、效果却还在",{@link #isSignActiveLocked} 一过界即判
     * 未锁定 ⇒ 锁定被墙钟单方面提前结束。补偿**不读任何效果实例**,只把玩家错过的 gameTime 间隔 gap
     * 整体后移锁定自己的两个截止刻,使两个时钟对称。冷却({@code sign_active_cooldown_end})**刻意不补偿**:
     * 冷却照旧离线也流逝(既有预期)。
     *
     * <p>幂等性(forge 线 {@code TickEvent.PlayerTickEvent} 每 tick 触发两次 = START/END):
     * ① 首行按"是否仍在锁定"早退,真正的迁移只发生一次(迁移后锁定标记被清空,第二次执行直接返回);
     * ② 步骤 0 的补偿只在 {@code gap > 1} 时执行 —— 正常在线时相邻两拍 gap 恰为 1(同 tick 内第二拍
     * gap == 0)⇒ 一次都不动;补偿过的第一拍已把 lastSeen 写成 now ⇒ 同 tick 第二拍 gap == 0 不再补偿。
     */
    public static void tickSignActiveLock(Player player) {
        if (player == null) return;
        if (player.level().isClientSide()) return;
        long now = player.level().getGameTime();

        // ===== 步骤 0:离线补偿(必须在任何早退/分支之前,含 isSignActiveLocked 判定)=====
        // seen = 上一次结算时刻的 gameTime。0 = 无锁定/宽限计时(或旧存档/探针未写该键)⇒ 不补偿。
        long seen = ModAttachments.getSignActiveLockLastSeen(player);
        if (seen > 0) {
            long gap = now - seen;
            // gap > 1 才补偿:正常在线相邻两拍 = 1 ⇒ 一次都不动(否则每 tick 自我延长,锁定永不解)。
            if (gap > 1) {
                long lockEnd = ModAttachments.getSignActiveLockEnd(player);
                if (lockEnd > 0) {
                    ModAttachments.setSignActiveLockEnd(player, lockEnd + gap);
                }
                long graceEnd = ModAttachments.getSignActiveLockGraceEnd(player);
                if (graceEnd > 0) {
                    ModAttachments.setSignActiveLockGraceEnd(player, graceEnd + gap);
                }
            }
        }
        // 每拍刷新 lastSeen:仍在硬上界/宽限计时内 ⇒ now(离线检测基准必须每 tick 都是新的);
        // 两者都不在(含忍者的宽限已被"出过效果牌"清掉、只剩周期出口)⇒ 0。
        // 只在取值真的变化时才写:无锁定期间(0 → 0)一次都不写,不做无谓的附件写入;
        // 锁定期间 now 每 tick 递增 ⇒ 每拍照写,基准始终是最新值(否则离线检测失效)。
        // 陈值自愈:锁定结束后的第一拍就把陈值写成 0,此后不再写。
        long lockEndNow = ModAttachments.getSignActiveLockEnd(player);
        long graceEndNow = ModAttachments.getSignActiveLockGraceEnd(player);
        long desiredSeen = (lockEndNow > 0 || graceEndNow > 0) ? now : 0L;
        if (desiredSeen != seen) {
            ModAttachments.setSignActiveLockLastSeen(player, desiredSeen);
        }

        String signId = getSignActiveLockSignId(player);
        if (signId.isEmpty()) return;
        if (KOMACHI_LOCK_ID.equals(signId)) {
            // 忍者:锁定跟随出牌周期,本方法只负责"宽限 1:00 内自始至终未出任何效果牌"的保险。
            // 其余出口是出牌周期完全重置(EffectCardPeriod -> onEffectCardRoundReset)。
            // 宽限刻已在步骤 0 一并按 gap 后移 ⇒ 1:00 宽限与门控效果同为"在线才走"的时钟。
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
}
