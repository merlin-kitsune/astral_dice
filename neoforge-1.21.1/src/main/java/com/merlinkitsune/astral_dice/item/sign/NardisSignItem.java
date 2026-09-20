package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.effect.NardisPrivilegeEffect;
import com.merlinkitsune.astral_dice.event.EffectTimerGuard;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.TemporaryCardUtil;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 绿洲女王立牌(nardis,稀有 RARE)。
 *
 * <h2>被动「威压」</h2>
 * <b>每装备一张攻击牌 ⇒ 攻击力 +1;每装备一张防御牌 ⇒ 防御力 +1</b>
 * (「已装备」= 骰子物品的 {@code weapon_enhancement.appliedStones};背包里没装配的不算)。
 * <ul>
 *   <li>攻击力:走 {@code combat/DiceCombatModifiers} 静态注册块里的攻击修饰器 ——
 *       实战({@code attackPowerOf})与 tooltip/GUI({@code getDisplayAttackRange})同源,
 *       不需要第二条链路;</li>
 *   <li>防御力:防御修饰器链**不读** {@code appliedStones},故按仓库统一口径折算为真实护甲
 *       (1 防御力 = 2 护甲值),由 {@link #onCurioTick} **每 tick 现算现写**到 ARMOR 属性
 *       ({@link DiceCombatModifiers#setDefenseArmorBonus}),卸下时清零
 *       (写法与扫地机 jasmine 同款);</li>
 *   <li>临时牌**同样计入**(它就是一张普通战斗牌,只是被标记);临时牌被清空时加成自然同时回落
 *       —— 全程**读时现算、不落任何附件**。</li>
 * </ul>
 *
 * <h2>主动「女王特权」</h2>
 * <ol>
 *   <li>**安全门(2026-09-27 用户裁决⑦)**:主物品栏空槽 &lt; 3 ⇒ **拒绝释放**(零消耗,
 *       只发一条 `msg.astral_dice.nardis_inventory_full` 提示),位置在冻结/冷却拒绝之后;</li>
 *   <li>**先清空**全部旧临时牌(物品栏 + 副手 + 骰子已装配的) —— 用户裁决③「清空重发」,
 *       正常玩法下已被冻结挡住,保留为安全网(裁决⑦第 5 条);</li>
 *   <li>发 **2 张战斗牌 + 1 张效果牌**(战斗牌池 = {@code CardCategory.BATTLE} 攻击/防御混合随机,
 *       两张各自独立 ⇒ 允许两张同类;效果牌池 = {@code CardCategory.EFFECT}),先打临时牌标记
 *       再走 {@link com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem#giveCard} 发牌漏斗
 *       ⇒ 维生素药丸治愈、教主立牌狐光等「获得卡牌」触发器全部照常生效;
 *       背包放不下时**少发**,**绝不落地**;</li>
 *   <li>施加自身效果 {@link ModEffects#NARDIS_PRIVILEGE} **3:00**,六参且 {@code showIcon=true}
 *       ⇒ HUD 计时器 + 图标(立牌贴图)全由这条原生效果实例承担;</li>
 *   <li>ActionBar 反馈本次实际发放张数(见 {@code #onSignActiveTriggered},抑制默认提示)。</li>
 * </ol>
 *
 * <h2>主动的「冻结(使用中)」态(2026-09-27 用户裁决②,推翻先前「刻意不起锁定态」)</h2>
 * 复写 {@link #startActiveLockOnUse} / {@link #isGateEffectActive} 复用立牌统一的锁定态设施:
 * <ul>
 *   <li><b>冻结期</b>:从释放起直到"临时牌全部用光"或 3:00 到期;期间再次按键走
 *       {@code BaseSignItem#performSkill} 第 0 步 ⇒ actionbar 提示既有的
 *       {@code msg.astral_dice.sign_active_in_effect}(「…主动技能生效中!」),
 *       HUD 计时器仍由 {@code nardis_privilege} 效果本身提供(用户裁决⑤:不自造新文案);</li>
 *   <li><b>解冻 (a)</b>:临时牌剩 0 张(物品栏 0 且 骰子卡牌栏 0)⇒ {@code TemporaryCardUtil#tick}
 *       移除效果(计时器消失)⇒ 门控效果判据变 false ⇒ 冻结结束(用户裁决 3(a));</li>
 *   <li><b>解冻 (b)</b>:3:00 到期 ⇒ 照旧清空剩余临时牌并解冻(既有路径,未改;裁决 3(b));</li>
 *   <li><b>冷却</b>:裁决④ —— **释放那一刻就开始冷却**(默认 180 秒,沿用玩家级冷却与减免机制),
 *       冻结期间冷却照常流逝,解锁时**不追加**新冷却(见 {@link #cooldownRunsDuringLock}),
 *       只把冻结期间累计的减免池从剩余冷却里抵扣一次。</li>
 * </ul>
 * ⇒ 裁决③「重复释放 = 先清空旧临时牌再发新的」在正常玩法下已不可达(冻结挡住),但逻辑保留为安全网。
 *
 * <p>图标 = {@code images/绿洲女王立牌.png}(实装路径 {@code textures/item/nardis_sign.png});
 * 主动效果图标复用同一张图({@code textures/mob_effect/nardis_privilege.png})。
 */
@EventBusSubscriber(modid = AstralDiceMod.MODID)
public class NardisSignItem extends BaseSignItem {
    /** 立牌的物品注册 id(锁定态/调试读数用) */
    public static final String SIGN_ID = AstralDiceMod.MODID + ":nardis_sign";

    /** 被动防御力折算到 ARMOR 属性时使用的修饰器 id(卸下/清场时用同一个 key 归零) */
    public static final String DEFENSE_ARMOR_KEY = "nardis_def_armor";

    public NardisSignItem(Properties properties) {
        super(properties);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动「女王特权」
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        // 客户端早退(照 MimiSignItem:51-53):真正的判定与发牌一律服务端权威
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 0. 安全门(2026-09-27 用户裁决⑦):本次固定发 2 战斗 + 1 效果共 3 张 ⇒ 主物品栏空槽 < 3 时
        //    **拒绝释放**,并且必须是**零消耗**:不开始冷却、不施加效果、不进入冻结、不发任何牌、
        //    也不清空任何既有临时牌(玩家修好背包后立刻可以再释放)。
        //    顺序:冻结(使用中)与冷却的既有拒绝在 {@code BaseSignItem#performSkill} 第 0/1 步、
        //    先于本方法 ⇒ 既有拒绝优先级更高(裁决⑦第 4 条)。这里返回 fail ⇒ performSkill 第 3 步
        //    直接 return(不发风扇筹码、不抛立牌主动事件、不进冷却/不充能)。
        //    判据说明:`countFreeSlots` = 主物品栏(0..35)**空**槽数,与 {@code giveCard} 的入包路径同段。
        //    ⚠️ 卡牌物品是 {@code stacksTo(64)}(可堆叠),但发牌前刚做过 purgeAll(旧临时牌已清空)、
        //    同 id 的**普通**牌与临时牌组件不同 ⇒ 不会合并到普通牌堆上;只有本次**两张战斗牌
        //    随机到同一张**时才会并进同一格(概率约 1/9)。⇒ 该门槛比"理论最小格数"略保守
        //    (2 格时约 1/9 概率其实放得下 3 张),这是**有意**取的确定性口径:3 格必然放得下,
        //    不依赖随机结果,也不会出现"发不满 3 张"。
        if (TemporaryCardUtil.countFreeSlots(player) < TemporaryCardUtil.GRANT_COUNT) {
            sendSignActionBar(player, "msg.astral_dice.nardis_inventory_full");
            return InteractionResultHolder.fail(stack);
        }
        // 1. 裁决③(保留为安全网):先清空**全部**旧临时牌(物品栏 0..35 + 副手 + 骰子已装配的),幂等。
        //    正常玩法下冻结已挡住重复释放 ⇒ 这条路径只在"效果被外力移除等异常情形"下生效,不要删。
        TemporaryCardUtil.purgeAll(player);
        // 2. 发 2 张战斗牌(攻击 + 防御混合池,允许两张同类)+ 1 张效果牌(2026-09-27 用户裁决①);
        //    战斗牌**先发**,空槽不足则少发、绝不落地;走 VitaminPillChipItem.giveCard 发牌漏斗
        int granted = TemporaryCardUtil.grantNardisPrivilege(player);
        // 3. 自身效果 3:00:**必须六参且 showIcon=true**(照 JasmineSignItem:63)——
        //    HUD 计时器与立牌图标全靠这条原生效果实例;同时它是「临时牌仍在有效期」的唯一真值,
        //    也是冻结(使用中)态的门控效果({@link #isGateEffectActive} + {@link #startActiveLockOnUse})。
        //    ⚠️ 顺序:发牌(第 2 步)**先于**施加效果(本步)—— 见 TemporaryCardUtil#tick 的防抖说明。
        EffectTimerGuard.apply(player, new MobEffectInstance(ModEffects.NARDIS_PRIVILEGE,
                NardisPrivilegeEffect.DURATION_TICKS, 0, false, false, true));
        // 4. ActionBar:实际发放张数(默认「主动技能已启动」提示由 onSignActiveTriggered 抑制)
        sendSignActionBar(player, "msg.astral_dice.nardis_active", granted);
        return InteractionResultHolder.success(stack);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动的「冻结(使用中)」态 —— 2026-09-27 用户裁决②(推翻先前「刻意不起锁定态」)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 进入冻结(锁定/生效中)态:硬上界 = 本次施加的 {@code nardis_privilege} 剩余时长(3:00),
     * 与 jasmine / fen 同款(读回**刚施加的效果实例**的剩余时长,不硬编码时长表)。
     *
     * <p>冻结的提前出口 = 门控效果本身消失({@link #isGateEffectActive}):
     * <ol>
     *   <li>**临时牌被全部用光**(物品栏 0 张 且 骰子卡牌栏 0 张)⇒ {@code TemporaryCardUtil#tick}
     *       经内部通道移除本效果 ⇒ 冻结在同一条判定链上立刻结束(用户裁决 3(a));</li>
     *   <li>3:00 到期 ⇒ 照旧清空剩余临时牌并解冻(既有语义,未改;用户裁决 3(b))。</li>
     * </ol>
     */
    @Override
    protected boolean startActiveLockOnUse(Player player, long now) {
        MobEffectInstance instance = player.getEffect(ModEffects.NARDIS_PRIVILEGE);
        if (instance == null) return false;
        beginActiveLock(player, SIGN_ID, now + instance.getDuration());
        return true;
    }

    /** 门控效果实例仍在 = 冻结仍在(效果被移除时冻结提前结束;外部把效果刷新得更长不会延长锁定) */
    @Override
    protected boolean isGateEffectActive(Player player) {
        return player.hasEffect(ModEffects.NARDIS_PRIVILEGE);
    }

    /**
     * 冻结期间冷却**照常流逝**(2026-09-27 用户裁决④):
     * {@code BaseSignItem#performSkill} 第 6 步在进入锁定的同时就写 {@code cooldown_end}
     * (基准 = 玩家级冷却含诡异骰子减半),解锁迁移**不再追加**新冷却,只把冻结期间累计的减免池
     * 从剩余冷却里抵扣一次。默认参数下冷却 180 秒 < 冻结 3:00 ⇒ 冻结结束时冷却通常已过,
     * 解冻后立即可再次释放({@code BaseSignItem#performSkill} 第 1 步的冷却检查此时读到 now ≥ cdEnd)。
     */
    @Override
    protected boolean cooldownRunsDuringLock(Player player) {
        return true;
    }

    // 主动技能 ActionBar 注册:自带提示已在 handleUse 内发送,仅阻止默认提示(照 MimiSignItem:96-101)
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.starenginelib.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.NARDIS_SIGN.get())) {
            event.setHandled();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「威压」:防御力折算(攻击力在 DiceCombatModifiers 的攻击修饰器里)
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // 防御力 +1/防御牌:折算为真实护甲(1 防御力 = 2 护甲值;经 ARMOR 属性,骰战与原版伤害均生效)。
        // 现算现写:装配/卸下卡牌后最迟下一 tick 生效,不需要任何缓存或附件。
        DiceCombatModifiers.setDefenseArmorBonus(player, DEFENSE_ARMOR_KEY, equippedDefenseCardCount(player));
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        // 卸下立牌:移除被动折算出来的护甲(数值 ≤ 0 时 setDefenseArmorBonus 会移除修饰器)
        DiceCombatModifiers.setDefenseArmorBonus(player, DEFENSE_ARMOR_KEY, 0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「威压」的计数(读时现算)
    // ══════════════════════════════════════════════════════════════════════════

    /** 玩家是否佩戴绿洲女王立牌(被动「威压」的佩戴判定) */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.NARDIS_SIGN.get())).isPresent();
    }

    /** 玩家当前佩戴骰子的 {@code weapon_enhancement}(无骰子时返回 {@link WeaponEnhancement#EMPTY}) */
    public static WeaponEnhancement equippedEnhancement(Player player) {
        ItemStack dice = TemporaryCardUtil.findEquippedDice(player);
        if (dice.isEmpty()) return WeaponEnhancement.EMPTY;
        return dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
    }

    /** 已装配的攻击牌张数(不含防御牌;临时牌同样计入) */
    public static int equippedAttackCardCount(Player player) {
        return count(player, false);
    }

    /** 已装配的防御牌张数(临时牌同样计入) */
    public static int equippedDefenseCardCount(Player player) {
        return count(player, true);
    }

    /** 从**骰子当前组件**计数(供 tick 的防御折算与 tooltip 展示用) */
    private static int count(Player player, boolean defense) {
        if (player == null) return 0;
        return countStones(equippedEnhancement(player), defense);
    }

    /** 从给定的 {@code WeaponEnhancement} 计数(攻击修饰器直接用 {@code ctx.enhancement},与实战同源) */
    public static int countStones(WeaponEnhancement enhancement, boolean defense) {
        if (enhancement == null) return 0;
        var stones = enhancement.appliedStones();
        if (stones == null || stones.isEmpty()) return 0;
        int count = 0;
        for (AppliedStone stone : stones) {
            if (stone == null || stone.type() == null) continue;
            if (CardRegistry.isDefense(stone.type()) == defense) count++;
        }
        return count;
    }
}
