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
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
 * <h2>主动「女王特权」(2026-09-27 用户裁决后的口径)</h2>
 * <ol>
 *   <li>**临时牌上限(2026-09-27 用户裁决⑦)**:当前临时牌总张数
 *       ({@code countTemporary} + {@code countTemporaryEquipped})≥
 *       {@link TemporaryCardUtil#MAX_TEMPORARY_CARDS}(= 9)⇒ **拒绝释放**(**零消耗**:
 *       不冷却、不施效果、不发牌、**不清**既有临时牌、不重置时长),
 *       只发一条 `msg.astral_dice.nardis_card_limit` 提示;</li>
 *   <li>**安全门**:主物品栏空槽 &lt; {@link TemporaryCardUtil#MIN_FREE_SLOTS_TO_CAST}(= 2)
 *       ⇒ 同样**拒绝释放**(零消耗),只发一条 `msg.astral_dice.nardis_inventory_full` 提示;</li>
 *   <li>**叠加补给**:发 **2 张战斗牌 + 1 张效果牌**(战斗牌池 = {@code CardCategory.BATTLE}
 *       攻击/防御混合随机,两张各自独立 ⇒ 允许两张同类;效果牌池 = {@code CardCategory.EFFECT}),
 *       先打临时牌标记再走 {@link com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem#giveCard}
 *       发牌漏斗 ⇒ 维生素药丸治愈、教主立牌狐光等「获得卡牌」触发器全部照常生效;
 *       **不再清空任何旧临时牌**(旧牌的有效期与旧牌本身都由同一条效果实例承载);
 *       发放张数夹取到上限预算({@code budget = 9 − current})⇒ 总数**永不超过 9**;
 *       背包放不下时**少发**,**绝不落地**;</li>
 *   <li>**有效期重置为 3:00**:把 {@link ModEffects#NARDIS_PRIVILEGE} 的剩余时长**确定性重置**为
 *       {@link NardisPrivilegeEffect#DURATION_TICKS}(= 3600 tick),**每次释放都重置**
 *       (实现与证据见 {@link #applyPrivilegeDuration(Player)});
 *       HUD 计时器 + 图标(立牌贴图)全由这一条原生效果实例承担;</li>
 *   <li>ActionBar 反馈本次实际发放张数(见 {@code #onSignActiveTriggered},抑制默认提示)。</li>
 * </ol>
 *
 * <h2>没有「使用中 / 冻结」态(2026-09-27 用户裁决,推翻上一版冻结机制)</h2>
 * 本立牌**不覆写** {@link #startActiveLockOnUse} / {@link #isGateEffectActive} ——
 * 它走的是「不进锁定、释放即起冷却」的普通立牌路径:
 * <ul>
 *   <li>释放当刻由 {@code BaseSignItem#performSkill} 第 6 步写入玩家级冷却
 *       (默认 180 秒,沿用既有减免机制与诡异骰子减半);</li>
 *   <li>效果生效期间**可以再次释放**,只受**冷却**限制(不再有「使用中」锁定态,
 *       也不再有解冻迁移与"不追加新冷却"的特例);</li>
 *   <li>再次释放**不清空旧临时牌**,新牌**叠加**在现有临时牌上,并把有效期重置为 3:00。</li>
 * </ul>
 *
 * <h2>效果结束口径(仅三件事)</h2>
 * 效果只由 ① 3:00 **自然到期**、② **外力移除**({@code /effect clear}、牛奶等)、③ **玩家死亡** 结束
 * —— 三者都由既有安全网收口:{@code item/card/TemporaryCardUtil#tick} 判定
 * 「玩家身上/骰子里还有临时牌,但已没有 {@code nardis_privilege} 效果」⇒ 清空全部临时牌
 * (死亡路径另有 {@code event/PlayerLifecycleHandler} 的显式清理,两者幂等)。
 * <p>⚠️ **牌被用光不再提前结束效果**:上一版为配合冻结而加的「一张临时牌都没有 ⇒ 立即移除效果」
 * 已按用户裁决**删除**。
 *
 * <p>图标 = {@code images/绿洲女王立牌.png}(实装路径 {@code textures/item/nardis_sign.png});
 * 主动效果图标复用同一张图({@code textures/mob_effect/nardis_privilege.png})。
 *
 * <h2>1.20.1 平台适配(相对 1.21.1 的镜像改写)</h2>
 * <ul>
 *   <li>事件订阅:{@code @Mod.EventBusSubscriber(modid = …)} + Forge {@code @SubscribeEvent}
 *       (1.20.1 无 {@code @EventBusSubscriber});</li>
 *   <li>Curios 经 {@link CuriosCompat#getCuriosInventory} 统一为 {@code Optional};</li>
 *   <li>效果常量 {@code ModEffects.NARDIS_PRIVILEGE.get()}(1.20.1 是 {@code RegistryObject});</li>
 *   <li>物品数据 {@code ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, …)}
 *       (1.20.1 {@code ItemDataKey} = ItemStack NBT,无 {@code stack.get(KEY.get())} 形式)。</li>
 * </ul>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class NardisSignItem extends BaseSignItem {
    /** 立牌的物品注册 id(调试读数用) */
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
        // 客户端早退(照 MimiSignItem:52-54):真正的判定与发牌一律服务端权威
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 0. 临时牌上限(2026-09-27 用户裁决⑦):**叠加补给必须配上限** —— 否则只要冷却好了就能再放,
        //    临时牌永不过期(每次释放都把有效期重置为 3:00)⇒ 每 180 秒净增 3 张,无上界。
        //    判据 = 物品栏(+副手)的临时牌 + 骰子已装配的临时牌,两者相加(见 TemporaryCardUtil 的两个计数方法)。
        //    达到上限 ⇒ 拒绝释放,并且必须是**零消耗**:不开始冷却、不施加/重置效果、不发任何牌、
        //    也不清空任何既有临时牌(玩家用掉几张后立刻可以再释放)。
        //    判据顺序:冷却拒绝在 {@code BaseSignItem#performSkill} 第 1 步(先于本方法)⇒
        //    「冷却 → 临时牌上限 → 物品栏空位」。
        int currentTemporary = TemporaryCardUtil.countTemporaryTotal(player);
        if (currentTemporary >= TemporaryCardUtil.MAX_TEMPORARY_CARDS) {
            sendSignActionBar(player, "msg.astral_dice.nardis_card_limit");
            return InteractionResultHolder.fail(stack);
        }
        // 1. 安全门(2026-09-27 用户裁决⑦;阈值 = {@link TemporaryCardUtil#MIN_FREE_SLOTS_TO_CAST} = 2):
        //    本次固定发 2 战斗 + 1 效果共 3 张 ⇒ 主物品栏空槽 < 2 时**拒绝释放**,同样**零消耗**
        //    (不冷却、不施效果、不发牌、不清既有临时牌)。这里返回 fail ⇒ performSkill 第 3 步
        //    直接 return(不发风扇筹码、不抛立牌主动事件、不进冷却/不充能)。
        //    判据说明:`countFreeSlots` = 主物品栏(0..35)**空**槽数,与 {@code giveCard} 的入包路径同段。
        //    ⚠️ 为什么门槛是 2(而不是 3):卡牌物品是 {@code stacksTo(64)}(可堆叠),同 id 的临时牌
        //    NBT 组件相同 ⇒ 会并进既有临时牌堆(叠加语义下比"先清空再发"更宽松);随机到不同牌时只是
        //    {@code grantRandom} 的"放不下就少发、绝不落地"截断少发,不落地也不报错。
        //    只有 < 2 格才必然只能发 ≤1 张,不值得消耗一次释放。
        if (TemporaryCardUtil.countFreeSlots(player) < TemporaryCardUtil.MIN_FREE_SLOTS_TO_CAST) {
            sendSignActionBar(player, "msg.astral_dice.nardis_inventory_full");
            return InteractionResultHolder.fail(stack);
        }
        // 2. 叠加发放(2026-09-27 用户裁决:取消「先清空再发」)。
        //    战斗牌**先发**、空槽不足则少发、绝不落地;发放张数按上限预算夹取
        //    (budget = MAX_TEMPORARY_CARDS − 当前张数 ⇒ 总数永不超过上限);
        //    走 VitaminPillChipItem.giveCard 发牌漏斗。
        //    ⚠️ 本方法**不**调用 {@code TemporaryCardUtil#purgeAll} —— 旧临时牌原样保留。
        int granted = TemporaryCardUtil.grantNardisPrivilege(player);
        // 3. 有效期**重置**为 3:00:六参且 showIcon=true(照 JasmineSignItem:64)——
        //    HUD 计时器与立牌图标全靠这条原生效果实例,它也是「临时牌仍在有效期」的唯一真值。
        //    ⚠️ 顺序:发牌(第 2 步)**先于**重置(本步)—— 与既有「效果一定在牌进包之后才出现」同序。
        applyPrivilegeDuration(player);
        // 4. ActionBar:实际发放张数(默认「主动技能已启动」提示由 onSignActiveTriggered 抑制)
        sendSignActionBar(player, "msg.astral_dice.nardis_active", granted);
        return InteractionResultHolder.success(stack);
    }

    /**
     * 把 {@code nardis_privilege} 的**剩余时长确定性重置**为 3:00
     * ({@link NardisPrivilegeEffect#DURATION_TICKS} = 3600 tick),并让计时守卫的记账与之对齐。
     *
     * <p><b>为什么不能只调一次 {@code EffectTimerGuard#apply}:</b>{@code apply} 内部走原版
     * {@code LivingEntity#addEffect} → 效果**已存在**时进 {@code MobEffectInstance#update} 的合并分支
     * (1.20.1 实测 {@code MobEffectInstance.java:84-110}:只有"新时长**严格更长**且放大器相同"
     * 才写 {@code this.duration = other.duration});更要紧的是守卫自己的记账 ——
     * {@code EffectTimerGuard#record} 记的是**绝对结束刻** {@code endTick = gameTime + duration},
     * 而 {@code EffectTimerGuard#tick} 每 tick 用
     * {@code expectedRemaining = endTick − now} 与实例实际剩余比对,**超出
     * {@code CLAMP_TOLERANCE = 20} 就把实例移除重加、裁回记账值**(1.20.1 实测
     * {@code EffectTimerGuard.java:135-140} 的"减速"分支)⇒ 只把实例拉长、不同步记账,下一 tick 就会被打回旧值。
     * 故这里三步一起做:
     * <ol>
     *   <li><b>效果已存在</b> ⇒ {@link Player#forceAddEffect}:它把 {@code activeEffects} 里的实例
     *       **无条件替换**为新实例(1.20.1 实测 {@code LivingEntity.java:978-988}:
     *       {@code activeEffects.put(...)} + {@code onEffectUpdated}),**不经过任何合并/取长规则**
     *       ⇒ 替换后剩余时长**必然**是 3600,与旧实例剩多少无关。同一条 API 也是原版客户端处理
     *       {@code ClientboundUpdateMobEffectPacket} 时用的写入途径(1.20.1 实测
     *       {@code ClientPacketListener.java:1533-1540})⇒ 服务端/客户端看到的剩余时长一致;</li>
     *   <li><b>效果不存在</b> ⇒ {@link EffectTimerGuard#apply}(常规施加,与既有实现逐字一致);
     *       但 {@code forceAddEffect} **不**触发 {@code MobEffectEvent.Added}
     *       (1.20.1 实测 {@code LivingEntity.java:978-988} 里没有 post),而本仓的守卫记录钩子
     *       {@code event/ModEffectEvents#onEffectTimerRecord}(1.20.1 实测该文件:140)正是挂在该事件上
     *       ⇒ 必须走第 3 步;</li>
     *   <li><b>守卫记账同步</b> ⇒ 显式 {@link EffectTimerGuard#record}(先例:
     *       {@code item/RenShieldManager#refreshShieldEffects} 的「addEffect + record」延长写法,
     *       1.20.1 实测该文件:224-231)。记账取 {@code max(旧 endTick, now + 3600)},
     *       而本立牌自己的旧记账必然 {@code ≤ now + 3600}(3600 就是本效果的最大时长)
     *       ⇒ 结果恒为 {@code now + 3600}。</li>
     * </ol>
     *
     * <p><b>不会误清临时牌</b>:本方法全程**不移除**效果(既不用 {@code removeEffect},
     * 也不走 {@code ModEffectRemoval} 内部通道)⇒ 不存在"效果缺失"的窗口,
     * {@code TemporaryCardUtil#tick} 的「无效果 ⇒ 清空临时牌」分支不可能被这一步触发。
     * 且本方法只在**释放当刻**执行一次(不是每 tick)⇒ 不产生任何周期性增删。
     *
     * <p><b>重登/换维度不会被打回旧值</b>:重登时 {@code event/PlayerLifecycleHandler} 会
     * {@code EffectTimerGuard.clear(player)} 清空全部记账(1.20.1 实测
     * {@code PlayerLifecycleHandler.java:224},重登处理器 {@code onPlayerLoggedInClearDiceBlessing}),
     * 效果实例则带着存档里的剩余时长(此处 = 3600 起算)原样回来,守卫不再介入 ⇒ 重置结果跨会话保留。
     *
     * <p>唯一的边界情况:效果被**外部**以更长时长施加(如 {@code /effect give … 99999})时,
     * 守卫的 {@code max} 记账会保留那个更长的结束刻,并把实例重新拉长到它 —— 这是本仓既有的
     * "外部延长一律尊重"口径(RenShieldManager 同款注释),正常玩法下不可达(本效果只有本立牌施加)。
     * <p>1.20.1 差异:效果常量是 RegistryObject ⇒ 取值必须 .get()。
     */
    private static void applyPrivilegeDuration(Player player) {
        MobEffectInstance refreshed = new MobEffectInstance(ModEffects.NARDIS_PRIVILEGE.get(),
                NardisPrivilegeEffect.DURATION_TICKS, 0, false, false, true);
        if (player.hasEffect(ModEffects.NARDIS_PRIVILEGE.get())) {
            // 已存在 ⇒ 无条件替换实例(剩余时长必然 = 3600,不依赖 update() 的"取更长"合并规则)
            player.forceAddEffect(refreshed, null);
        } else {
            // 首次释放 ⇒ 常规施加(与既有实现一致)
            EffectTimerGuard.apply(player, refreshed);
        }
        if (player.hasEffect(ModEffects.NARDIS_PRIVILEGE.get())) {
            // 守卫记账同步:forceAddEffect 不抛 MobEffectEvent.Added,不显式写就会被守卫按旧 endTick 裁回去
            EffectTimerGuard.record(player, refreshed);
        }
    }

    // 主动技能 ActionBar 注册:自带提示已在 handleUse 内发送,仅阻止默认提示(照 MimiSignItem:97-102)
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
        // ⚠️ 1.20.1 的 Curios curioTick 每 tick 调用一次(与 1.21.1 相同),setDefenseArmorBonus
        //    内部「数值未变则不增删」⇒ 不会产生每 tick 属性同步。
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
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.NARDIS_SIGN.get())).isPresent();
    }

    /** 玩家当前佩戴骰子的 {@code weapon_enhancement}(无骰子时返回 {@link WeaponEnhancement#EMPTY}) */
    public static WeaponEnhancement equippedEnhancement(Player player) {
        ItemStack dice = TemporaryCardUtil.findEquippedDice(player);
        if (dice.isEmpty()) return WeaponEnhancement.EMPTY;
        return ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, WeaponEnhancement.EMPTY);
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
