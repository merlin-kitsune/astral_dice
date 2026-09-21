package com.merlinkitsune.astral_dice.item.sign;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.combat.DiceCombatModifiers;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.effect.HuguangEffect;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.effect.TeruDescentEffect;
import com.merlinkitsune.astral_dice.event.WeirdDiceHandler;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.chip.CurrentCoreChipItem;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.starenginelib.target.TargetSelectionAction;
import com.merlinkitsune.starenginelib.target.TargetSelectionRegistry;
import com.merlinkitsune.starenginelib.target.TargetType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 教主立牌(全局命名 {@code teru};传奇品质)。1.20.1 移植版,与 1.21.1 侧**功能逐条等价**
 * (差异只有平台写法:{@code Mod.EventBusSubscriber} / {@code CuriosCompat} / {@code ModNetwork}
 * / {@code ModEffects.X.get()} / 附件 {@code AttachedDataKey})。
 *
 * <h2>被动「狐光」</h2>
 * 层数资源(0..{@link #MAX_HUGUANG});持有者**(以及其降神目标)**合成 / 获得 / 装备**攻击牌**各 +1 层/张
 * (攻击牌 = {@code astral_dice:combat_cards} 标签中非防御的 7 张;判定统一走
 * {@link CardRegistry#itemToType} + {@link CardRegistry#isDefense});
 * 主动施放「降神」瞬间立即 {@link #HUGUANG_ON_ACTIVE} 层。
 *
 * <p><b>两条防刷守卫(2026-09-27 用户指令)</b>:
 * <ol>
 *   <li><b>拾取不计层</b> —— 本类**不订阅任何拾取事件**(1.20.1 侧对应
 *       {@code net.minecraftforge.event.entity.player.EntityItemPickupEvent} / {@code PlayerEvent.ItemPickupEvent})
 *       ⇒ 「丢弃 → 捡起」一层都刷不到(与 {@code item/chip/VitaminPillChipItem} 的既有注释口径一致);</li>
 *   <li><b>装备计层按「历史同时装备张数水位」去重</b> —— 见 {@link #onAttackCardsEquipped}:
 *       同一批战斗牌「插入 → 卸除 → 再插入」只计一次。
 *       ⚠️ 不能改用物品级标记:{@code screen/CardInventoryMenu#saveToDice} 只把卡牌写成
 *       {@code AppliedStone(type, uses)} 并销毁物品栈,再次打开卡牌栏由 {@code loadFromDice} 经
 *       {@code CardRegistry.typeToItem} **重建全新 ItemStack** ⇒ 物品标记必被抹掉。</li>
 * </ol>
 * 层数真值 = 附件 {@code ModAttachments#TERU_HUGUANG_LAYERS}(**跨死亡/重登保留**;
 * 1.20.1 侧经 {@code component/AstralData#onPlayerClone} 的死亡白名单实现),每 tick 镜像为
 * {@link HuguangEffect}(层数 = amplifier+1)。
 *
 * <h2>主动「降神」</h2>
 * 目标选择器类技能({@link TargetType#PLAYER} = **只能选其它玩家**,不含生物与自身;
 * 本类**不**实现 {@code SelfTargetable}):
 * <ol>
 *   <li>锁定目标 50% 的攻击力与防御力给施法者(施法瞬间快照;攻击加成经镜像缓存进骰战攻击力,
 *       防御加成经 {@link DiceCombatModifiers#setDefenseArmorBonus} 折算真实护甲);</li>
 *   <li>目标身上留下「降神」效果(图标复用教主立牌贴图),并记录狐光攻击基数
 *       {@code B = 施加时的施法者攻击力(基础) + ⌊目标攻击力×0.5⌋}(= 获得目标 50% 加成后的快照攻击力);</li>
 *   <li>目标**每攻击一个新目标**(本次降神期内未攻击过的目标,按 UUID 记集合)消耗 1 层狐光,
 *       按 {@code B + 消耗后剩余层数} 追加骰战攻击力(层数已为 0 时不消耗、不追加);</li>
 *   <li>持续到**该目标自己的下一次骰神赐福结束**(下降沿状态机,语义与
 *       {@code ZhaoSignItem#tickBlessing} 逐字相同)。</li>
 * </ol>
 * <p><b>生效中不可重复施放</b>(2026-09-27 用户裁决):按主动键时经
 * {@link BaseSignItem#canBeginSelectorSession} 拒绝 —— 只提示、不开选择会话、不进冷却、不发牌、不充能。
 *
 * <h2>加成归属与生命周期(需求口径)</h2>
 * 降神的**状态真值全部在目标身上**;施法者侧只有「层数」「目标指针」「攻击加成镜像缓存」⇒
 * 施法者死亡/登出不影响目标身上的降神,回来后自动重建;只有「目标效果结束 / 目标死亡 / 目标登出 / 目标重登」
 * 才会真正移除({@link #endDescent} 是唯一收敛点);狐光层数与装备水位跨死亡保留;
 * 卸下立牌**不清**已有层数与生效中的降神。
 *
 * <p>图标 = {@code images/教主立牌.png}(实装路径 {@code textures/item/teru_sign.png});
 * 降神效果图标复用同一张图;狐光效果图标 = {@code images/狐光.png}。
 */
@Mod.EventBusSubscriber(modid = AstralDiceMod.MODID)
public class TeruSignItem extends BaseSignItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeruSignItem.class);

    /** 主动技能的动作 id(注册表键;也是 actionbar 技能名 {@code msg.astral_dice.target_select.skill.<id>} 的来源) */
    public static final String ACTION_ID = "teru_descent";

    /** 立牌的物品注册 id(锁定态/调试读数用) */
    public static final String SIGN_ID = AstralDiceMod.MODID + ":teru_sign";

    /** 狐光层数上限(2026-09-27 用户裁决:上限 20;超出部分丢弃) */
    public static final int MAX_HUGUANG = 20;

    /** 主动施放瞬间立即获得的狐光层数 */
    public static final int HUGUANG_ON_ACTIVE = 3;

    /** 降神给施法者的攻/防加成比例(目标对应数值的 50%,向下取整) */
    public static final double DESCENT_RATIO = 0.5D;

    /** 施法者防御加成折算护甲时使用的属性修饰器 id 后缀(见 {@link DiceCombatModifiers#setDefenseArmorBonus}) */
    private static final String DEF_ARMOR_KEY = "teru_descent_def";

    /**
     * 主动「降神」:选**其它玩家** ⇒ 锁定其 50% 攻防给自己,并在其身上留下降神状态。
     *
     * <p>必须用具名类:本动作只实现 {@link TargetSelectionAction}(**不**实现 {@code SelfTargetable}),
     * 因此右键不提供"对自身使用",且库内 {@code TargetType.PLAYER.matches} 已排除选择者自身。
     */
    private static final class TeruDescentAction implements TargetSelectionAction {
        @Override
        public String id() {
            return ACTION_ID;
        }

        @Override
        public TargetType targetType() {
            return TargetType.PLAYER;
        }

        @Override
        public void onStarted(ServerPlayer player) {
            sendSignActionBar(player, "msg.astral_dice.teru_ready");
        }

        @Override
        public void apply(ServerPlayer player, LivingEntity target) {
            if (!castDescent(player, target)) return;
            // 主动成功施效:开始玩家级冷却(统一经 signCooldownTicks:含诡异骰子 -50% 与充能递减)
            ModAttachments.setSignActiveCooldownEnd(player,
                    player.level().getGameTime() + WeirdDiceHandler.signCooldownTicks(player));
            // 电流核心筹码:主动技能实际生效时充能 +1
            CurrentCoreChipItem.onActiveSkillUsed(player);
            LOGGER.debug("[Astral Dice][Teru] 降神 by {} -> {}",
                    player.getName().getString(), target.getName().getString());
        }
    }

    static {
        // 目标选择器动作注册(类加载即注册,与风水师/游戏大师等选择器类立牌同一写法)
        TargetSelectionRegistry.register(new TeruDescentAction());
    }

    public TeruSignItem(Properties properties) {
        super(properties);
    }

    // 目标选择器前置门控:按下主动键只开启选择会话并立即返回;确认合法目标后才由
    // resumeGatedActiveSkill 走「风扇筹码发牌 + 立牌主动响应事件」(冷却与充能由上面的 apply 写入)。
    @Override
    protected String selectorActionId() {
        return ACTION_ID;
    }

    /**
     * 「降神生效中不可重复施放」(2026-09-27 用户裁决):已有有效降神链接时**只提示**,
     * 不开选择会话、不发牌、不进冷却/锁定、不充能(冷却与充能都在动作的 {@code apply} 里,故天然不触发)。
     */
    @Override
    protected boolean canBeginSelectorSession(Player player) {
        if (player == null) return false;
        if (resolveTarget(player) == null) return true;
        sendSignActionBar(player, "msg.astral_dice.teru_descent_in_effect");
        return false;
    }

    /** 玩家是否佩戴教主立牌(被动增层/职业判定的佩戴口径) */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.TERU_SIGN.get())).isPresent();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  狐光层数(附件真值 0..MAX_HUGUANG)
    // ══════════════════════════════════════════════════════════════════════════

    /** 当前狐光层数(0..{@link #MAX_HUGUANG}) */
    public static int getLayers(Player holder) {
        if (holder == null) return 0;
        return ModAttachments.getTeruHuguangLayers(holder);
    }

    /** 直接写入层数(夹逼到 0..{@link #MAX_HUGUANG};同时刷新 HUD 镜像) */
    public static void setLayers(Player holder, int value) {
        if (holder == null || holder.level().isClientSide()) return;
        int clamped = Math.max(0, Math.min(MAX_HUGUANG, value));
        ModAttachments.setTeruHuguangLayers(holder, clamped);
        HuguangEffect.mirror(holder, clamped);
    }

    /** 层数增减(负数为消耗;夹逼到 0..{@link #MAX_HUGUANG};同时刷新 HUD 镜像) */
    public static void addLayers(Player holder, int amount) {
        if (holder == null || amount == 0) return;
        setLayers(holder, getLayers(holder) + amount);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  主动「降神」的施效
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 施放「降神」(服务端权威;目标选择器确认入口与调试/测试入口共用同一实现)。
     *
     * @return 是否成功(目标必须是**其它玩家**,且施法者当前没有生效中的降神)
     */
    public static boolean castDescent(ServerPlayer caster, LivingEntity target) {
        if (caster == null || target == null) return false;
        if (caster.level().isClientSide()) return false;
        if (!(target instanceof Player receiver)) return false;
        if (receiver == caster) return false;
        // 生效中不可重复施放(门控已在 canBeginSelectorSession 拦下;此处为服务端二次校验)
        if (resolveTarget(caster) != null) {
            sendSignActionBar(caster, "msg.astral_dice.teru_descent_in_effect");
            return false;
        }

        // ① 施法瞬间快照(全部 floor):施法者施法前攻击力 / 目标攻击力 / 目标防御力
        int casterAttack = DiceCombatModifiers.attackPowerOf(caster);
        int targetAttack = DiceCombatModifiers.attackPowerOf(receiver);
        int targetDefense = DiceCombatModifiers.defensePowerOf(receiver);
        int bonusAtk = (int) Math.floor(targetAttack * DESCENT_RATIO);
        int bonusDef = (int) Math.floor(targetDefense * DESCENT_RATIO);
        // 狐光攻击基数 B = 施加时的施法者攻击力(基础攻击力) + ⌊目标攻击力×50%⌋
        // = 施法者「获得目标 50% 加成后」的快照攻击力(后续攻击力成长不计入;2026-09-21 用户口径)
        int attackBase = Math.max(0, casterAttack) + bonusAtk;

        // ② 状态机初始化(语义与 zhao 的 skip_cycles 逐字相同):
        //    施加时目标已在骰神赐福 ⇒ skip=1(跳过当前这次结束);否则 0。prev 落成"此刻的骰神赐福真值"。
        boolean alreadyBlessed = receiver.hasEffect(ModEffects.DICE_BLESSING.get());
        ModAttachments.setTeruDescentCaster(receiver, Optional.of(caster.getUUID()));
        ModAttachments.setTeruDescentAtkBonus(receiver, bonusAtk);
        ModAttachments.setTeruDescentDefBonus(receiver, bonusDef);
        ModAttachments.setTeruDescentAttackBase(receiver, attackBase);
        ModAttachments.setTeruDescentSkipCycles(receiver, alreadyBlessed ? 1 : 0);
        ModAttachments.setTeruPrevBlessing(receiver, alreadyBlessed);
        ModAttachments.setTeruDescentNewTargets(receiver, "");

        // ③ 可见载体 + 施法者侧指针/镜像(攻击加成与护甲折算立即生效,不必等下一个 tick)
        TeruDescentEffect.apply(receiver);
        ModAttachments.setTeruDescentTarget(caster, Optional.of(receiver.getUUID()));
        ModAttachments.setTeruAtkBonusCache(caster, bonusAtk);
        DiceCombatModifiers.setDefenseArmorBonus(caster, DEF_ARMOR_KEY, bonusDef);

        // ④ 主动施放瞬间:+3 层狐光(夹逼上限)
        addLayers(caster, HUGUANG_ON_ACTIVE);

        ModNetwork.sendToPlayer(caster, new ModNetwork.ActionBarMessage(
                Component.translatable("msg.astral_dice.teru_descent_applied",
                                receiver.getDisplayName(), bonusAtk, bonusDef)
                        .withStyle(ChatFormatting.YELLOW), GameplayConstants.ACTIONBAR_DURATION_TICKS));
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  玩家级 tick(施法者侧派生 + 目标侧下降沿状态机 + 狐光镜像)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 玩家级 tick(由 {@code event/PlayerTickEvents} **每 tick** 驱动;仅服务端)。
     *
     * <p>职责:
     * <ol>
     *   <li><b>施法者侧</b>{@link #tickCasterSide}:解析/自愈降神链接,把目标记录里的攻击加成镜像进缓存、
     *       防御加成折算进护甲;链接失效(目标效果结束/死亡/登出)则两者同 tick 归零;</li>
     *   <li><b>目标侧</b>{@link #tickTargetSide}:骰神赐福**下降沿**检测(两分支)、效果自检与续期;</li>
     *   <li><b>狐光镜像</b>:把附件层数镜像为 HUD 效果(>0 显示层数,0 移除)。</li>
     * </ol>
     */
    public static void tick(Player player) {
        if (player == null || player.level().isClientSide()) return;
        tickCasterSide(player);
        tickTargetSide(player);
        HuguangEffect.mirror(player, getLayers(player));
    }

    /** 施法者侧:维护「我 → 目标」链接与两个派生值(攻击加成缓存 + 护甲折算) */
    private static void tickCasterSide(Player caster) {
        Player target = resolveTarget(caster);
        if (target == null) {
            // 链接失效:加成必须同 tick 清零,不留"效果结束仍吃加成"的残留
            if (ModAttachments.getTeruAtkBonusCache(caster) != 0) {
                ModAttachments.setTeruAtkBonusCache(caster, 0);
            }
            DiceCombatModifiers.setDefenseArmorBonus(caster, DEF_ARMOR_KEY, 0);
            return;
        }
        int bonusAtk = ModAttachments.getTeruDescentAtkBonus(target);
        int bonusDef = ModAttachments.getTeruDescentDefBonus(target);
        if (ModAttachments.getTeruAtkBonusCache(caster) != bonusAtk) {
            ModAttachments.setTeruAtkBonusCache(caster, bonusAtk);
        }
        DiceCombatModifiers.setDefenseArmorBonus(caster, DEF_ARMOR_KEY, bonusDef);
    }

    /** 目标侧:骰神赐福下降沿状态机(语义与 {@code ZhaoSignItem#tickBlessing} 逐字相同)+ 效果自检/续期 */
    private static void tickTargetSide(Player target) {
        boolean hasDice = target.hasEffect(ModEffects.DICE_BLESSING.get());
        if (ModAttachments.getTeruDescentCaster(target).isPresent()) {
            boolean prev = ModAttachments.isTeruPrevBlessing(target);
            if (prev && !hasDice) {
                int skip = ModAttachments.getTeruDescentSkipCycles(target);
                if (skip > 0) {
                    // 施加时目标已在骰神赐福 ⇒ 跳过当前这一次结束(skip 递减,降神保留)
                    ModAttachments.setTeruDescentSkipCycles(target, skip - 1);
                    LOGGER.debug("[Astral Dice][Teru] 跳过本次骰神赐福结束: player={} remainSkip={}",
                            target.getName().getString(), skip - 1);
                } else {
                    endDescent(target);
                    if (ModAttachments.isTeruPrevBlessing(target)) {
                        ModAttachments.setTeruPrevBlessing(target, false);
                    }
                    return;
                }
            }
            if (!TeruDescentEffect.has(target)) {
                // 自检:效果实例被外力移除(如 /effect clear)⇒ 真值复位(两者不允许长期不一致)
                endDescent(target);
            } else {
                TeruDescentEffect.refresh(target);
            }
        }
        // 同值不写:本方法是每 tick(1.20.1 每 tick 两次)调用,避免无意义的附件脏化
        if (ModAttachments.isTeruPrevBlessing(target) != hasDice) {
            ModAttachments.setTeruPrevBlessing(target, hasDice);
        }
    }

    /**
     * **结束降神(唯一收敛点)**:清目标侧全部记录 + 移除效果实例;施法者在线则同步清零其派生值。
     *
     * <p>狐光层数与装备水位**不清**(层数跨死亡/重登保留;水位是防刷守卫,见
     * {@link #onAttackCardsEquipped})。
     */
    public static void endDescent(Player target) {
        if (target == null || target.level().isClientSide()) return;
        Optional<UUID> casterId = ModAttachments.getTeruDescentCaster(target);
        ModAttachments.setTeruDescentCaster(target, Optional.empty());
        ModAttachments.setTeruDescentAtkBonus(target, 0);
        ModAttachments.setTeruDescentDefBonus(target, 0);
        ModAttachments.setTeruDescentAttackBase(target, 0);
        ModAttachments.setTeruDescentSkipCycles(target, 0);
        ModAttachments.setTeruPrevBlessing(target, false);
        ModAttachments.setTeruDescentNewTargets(target, "");
        if (TeruDescentEffect.has(target)) {
            TeruDescentEffect.remove(target);
        }
        if (casterId.isPresent()) {
            Player caster = onlinePlayer(target, casterId.get());
            if (caster != null) {
                ModAttachments.setTeruAtkBonusCache(caster, 0);
                DiceCombatModifiers.setDefenseArmorBonus(caster, DEF_ARMOR_KEY, 0);
                ModAttachments.setTeruDescentTarget(caster, Optional.empty());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  被动「狐光」增层(事件式 + 两条防刷守卫)
    // ══════════════════════════════════════════════════════════════════════════

    // 守卫 ①:拾取不计层 —— 本类**刻意不订阅**任何拾取事件
    // (1.20.1 侧对应 EntityItemPickupEvent / PlayerEvent.ItemPickupEvent;此处不出现这些 import 即为"未挂")。
    // 理由:丢弃 → 捡起 可零成本循环,与 VitaminPillChipItem 的既有注释(拾取不触发)同一口径。

    /** 是否为**攻击牌**(combat_cards 标签中非防御的 7 张;判定真值 = {@link CardRegistry}) */
    public static boolean isAttackCard(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String type = CardRegistry.itemToType(stack);
        return type != null && !CardRegistry.isDefense(type);
    }

    /** 合成攻击牌(ItemCraftedEvent 挂点;与维生素药丸/看板娘同一事件的机制) */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) return;
        ItemStack result = event.getCrafting();
        if (!isAttackCard(result)) return;
        onAttackCardCount(player, result.getCount());
    }

    /**
     * 获得攻击牌(唯一发牌漏斗 {@code item/chip/VitaminPillChipItem#giveCard} 的成功入包分支调用;
     * 覆盖随机发牌/立牌奖励/筹码/事件系统等全部模组发牌路径)。
     */
    public static void onAttackCardGained(Player actor, ItemStack card) {
        if (actor == null || actor.level().isClientSide()) return;
        if (!isAttackCard(card)) return;
        onAttackCardCount(actor, card.getCount());
    }

    /** 按**张数**计层(调用方已完成"是攻击牌"判定) */
    public static void onAttackCardCount(Player actor, int count) {
        if (actor == null || actor.level().isClientSide()) return;
        if (count <= 0) return;
        creditLayers(actor, count);
    }

    /**
     * 装备攻击牌计层(**守卫 ②:历史同时装备水位去重**)。
     *
     * <p>由 {@code screen/CardInventoryMenu#saveToDice} 在写入骰子数据后调用,传入**本次写入的攻击牌
     * 「类型 → 张数」**;本方法按类型与该玩家的历史水位比较,只对**超出历史最大值的增量**计层,
     * 然后把水位抬到当前值(只升不降)。
     *
     * <p>效果:同一批牌「插入 → 卸除 → 再插入」一层都刷不到;真的多持有/多合成后再装备照常 +1/张;
     * 水位跨死亡保留(1.20.1 侧见 {@code AstralData#onPlayerClone} 白名单)⇒ 也不能靠「死一次再装备一次」刷层。
     */
    public static void onAttackCardsEquipped(Player actor, Map<String, Integer> equippedAttackCards) {
        if (actor == null || actor.level().isClientSide()) return;
        if (equippedAttackCards == null || equippedAttackCards.isEmpty()) return;
        Map<String, Integer> watermark = parseWatermark(ModAttachments.getTeruEquipWatermark(actor));
        int delta = 0;
        boolean raised = false;
        for (Map.Entry<String, Integer> entry : equippedAttackCards.entrySet()) {
            int now = Math.max(0, entry.getValue());
            int previous = watermark.getOrDefault(entry.getKey(), 0);
            if (now > previous) {
                delta += now - previous;
                watermark.put(entry.getKey(), now);
                raised = true;
            }
        }
        if (raised) {
            ModAttachments.setTeruEquipWatermark(actor, formatWatermark(watermark));
        }
        if (delta > 0) {
            creditLayers(actor, delta);
        }
    }

    /**
     * 「行为者 → 狐光持有者」映射并计层:行为者佩戴教主立牌 ⇒ 本人 +N;
     * 行为者是某人的降神目标 ⇒ 该**施法者** +N(两者可同时成立,各自独立结算)。
     */
    private static void creditLayers(Player actor, int count) {
        if (isEquipped(actor)) {
            addLayers(actor, count);
        }
        Player caster = descentCasterOf(actor);
        if (caster != null && caster != actor) {
            addLayers(caster, count);
        }
    }

    /**
     * 降神目标「攻击新目标」时的额外攻击(**消耗 1 层狐光 + 返回攻击力加算**)。
     *
     * <p>由 {@code combat/DiceCombatModifiers} 的攻击修饰器调用;返回值直接加进骰战攻击力
     * (因此受目标防御力抵扣,且参与全力攻击等既有倍率)。
     *
     * @return 额外攻击力(0 = 本次不适用:非降神目标 / 已攻击过该目标 / 施法者离线 / 层数已为 0)
     */
    public static int consumeHuguangForNewTarget(Player attacker, LivingEntity victim) {
        if (attacker == null || victim == null) return 0;
        if (attacker.level().isClientSide()) return 0;
        // ⚠️ 自目标防护(**必须保留**):本方法会被「显示/快照」路径以 `ctx.target == attacker` 调用 ——
        //   `DiceCombatModifiers#getDisplayAttackRange`(卡牌栏 GUI 攻击力显示)与 `TeruSignItem` 自身的
        //   施法快照 `attackPowerOf` 都用「player 既是攻击方也是目标」的中立上下文跑**同一套攻击修饰器链**。
        //   若没有这一行,"打开卡牌栏看一眼攻击力"或"施法瞬间快照"都会**误消耗 1 层狐光并把施法者自己
        //   记进已攻击目标集**(真值被显示路径污染)。真实骰战链路不可能自目标
        //   (Forge 侧同为「target == player 直接 return」)⇒ 本防护对实战零影响。
        if (victim == attacker) return 0;
        if (ModAttachments.getTeruDescentCaster(attacker).isEmpty()) return 0;
        String recorded = ModAttachments.getTeruDescentNewTargets(attacker);
        String victimId = victim.getUUID().toString();
        if (containsTarget(recorded, victimId)) return 0;
        // 施法者离线时无法写离线玩家数据 ⇒ 本次不加成、不消耗(见规格文档 A3)
        Player caster = descentCasterOf(attacker);
        if (caster == null) return 0;
        int layers = getLayers(caster);
        if (layers <= 0) return 0;
        ModAttachments.setTeruDescentNewTargets(attacker, appendTarget(recorded, victimId));
        addLayers(caster, -1);
        int bonus = ModAttachments.getTeruDescentAttackBase(attacker) + getLayers(caster);
        LOGGER.debug("[Astral Dice][Teru] 新目标额外攻击: victim={} base={} remainLayers={} bonus={}",
                victim.getName().getString(), ModAttachments.getTeruDescentAttackBase(attacker),
                getLayers(caster), bonus);
        return bonus;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  生命周期(死亡 / 重登 / 登出)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * **死亡清场**(由 {@code event/PlayerLifecycleHandler} 的死亡处理在移除骰神赐福的同一段调用)。
     *
     * <p>① 本人是降神目标 ⇒ {@link #endDescent};② 本人是施法者 ⇒ 派生值就地清零
     * (层数与装备水位在 1.20.1 侧经 {@code AstralData#onPlayerClone} 白名单跨死亡保留)。
     */
    public static void onOwnerDeathCleanup(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (ModAttachments.getTeruDescentCaster(player).isPresent()) {
            endDescent(player);
        }
        ModAttachments.setTeruAtkBonusCache(player, 0);
        DiceCombatModifiers.setDefenseArmorBonus(player, DEF_ARMOR_KEY, 0);
        ModAttachments.setTeruDescentTarget(player, Optional.empty());
    }

    /**
     * **重登处理**(由 {@code PlayerLifecycleHandler#onPlayerLoggedInClearDiceBlessing} 在同一段调用)。
     *
     * <p>口径(规格文档 A2):目标重登 ⇒ 降神**不跨会话残留**(与白泽赐福同口径,{@link #endDescent});
     * 施法者重登 ⇒ 降神**保留**,并就地重建指针与镜像缓存(目标记录是唯一真值)。
     */
    public static void onOwnerRelogin(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (ModAttachments.getTeruDescentCaster(player).isPresent()) {
            endDescent(player);
        } else if (ModAttachments.getTeruDescentTarget(player).isPresent()) {
            resolveTarget(player);
        }
        HuguangEffect.mirror(player, getLayers(player));
    }

    /**
     * **登出**:本人是降神目标 ⇒ 立即结束降神(需求:「被附身者离开 ⇒ 施法者加成立即清除」);
     * 施法者登出无需额外清理(其加成本就是每 tick 派生,离线即自然停止;目标身上的降神不受影响)。
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 1.20.1 的 PlayerEvent#getEntity() 静态类型就是 Player(直接取用,不用 instanceof 模式)
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) return;
        if (ModAttachments.getTeruDescentCaster(player).isPresent()) {
            endDescent(player);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  内部工具
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 解析施法者当前有效的降神目标(仅在线);指针失效时**扫描在线玩家自愈重建**
     * (施法者死亡/重登会丢失指针,而目标记录仍在 ⇒ 必须能重建)。
     *
     * <p>⚠️ 必须用 {@code getServer().getPlayerList().getPlayer(uuid)} 而**不是** {@code level.getPlayerByUUID}:
     * 后者只在本维度查找,跨维度时会读成"链接失效"从而错误地清掉加成。
     */
    private static Player resolveTarget(Player caster) {
        if (caster == null || caster.level().isClientSide()) return null;
        UUID casterId = caster.getUUID();
        Optional<UUID> pointer = ModAttachments.getTeruDescentTarget(caster);
        if (pointer.isPresent()) {
            Player target = onlinePlayer(caster, pointer.get());
            if (target != null
                    && ModAttachments.getTeruDescentCaster(target).filter(casterId::equals).isPresent()) {
                return target;
            }
        }
        // 指针缺失/失效:扫描在线玩家,找回"把我记为施法者"的那个目标。
        // ⚠️ 本方法是**每 tick(1.20.1 每 tick 两次)对每个玩家**调用的,故扫描必须设便宜的前置门:
        // 只有"佩戴着教主立牌"或"仍有攻击加成缓存"的玩家才值得全服扫描(否则 N 个玩家就是 O(N²));
        // 已持有有效指针的路径在上方 O(1) 返回,不受本门影响(卸下立牌也不会打断已在生效的降神)。
        if (!isEquipped(caster) && ModAttachments.getTeruAtkBonusCache(caster) == 0) return null;
        if (caster.getServer() == null) return null;
        for (ServerPlayer candidate : caster.getServer().getPlayerList().getPlayers()) {
            if (ModAttachments.getTeruDescentCaster(candidate).filter(casterId::equals).isPresent()) {
                ModAttachments.setTeruDescentTarget(caster, Optional.of(candidate.getUUID()));
                ModAttachments.setTeruAtkBonusCache(caster, ModAttachments.getTeruDescentAtkBonus(candidate));
                DiceCombatModifiers.setDefenseArmorBonus(caster, DEF_ARMOR_KEY,
                        ModAttachments.getTeruDescentDefBonus(candidate));
                return candidate;
            }
        }
        return null;
    }

    /** 施法者(在线);离线返回 null(无法写离线玩家数据 ⇒ 本次不计层/不加成) */
    private static Player descentCasterOf(Player target) {
        if (target == null) return null;
        Optional<UUID> casterId = ModAttachments.getTeruDescentCaster(target);
        if (casterId.isEmpty()) return null;
        return onlinePlayer(target, casterId.get());
    }

    /** 跨维度解析在线玩家 */
    private static Player onlinePlayer(Player context, UUID id) {
        if (context == null || id == null || context.getServer() == null) return null;
        return context.getServer().getPlayerList().getPlayer(id);
    }

    // ---- 「已攻击目标集」的字符串集读写(逗号分隔;沿用 flashlight_granted_targets 的既有口径) ----

    private static boolean containsTarget(String set, String id) {
        if (set == null || set.isEmpty() || id == null) return false;
        for (String part : set.split(",")) {
            if (part.equals(id)) return true;
        }
        return false;
    }

    private static String appendTarget(String set, String id) {
        Set<String> ids = new LinkedHashSet<>();
        if (set != null && !set.isEmpty()) {
            for (String part : set.split(",")) {
                if (!part.isEmpty()) ids.add(part);
            }
        }
        ids.add(id);
        return String.join(",", ids);
    }

    // ---- 装备水位("type=count;type=count")的读写 ----

    private static Map<String, Integer> parseWatermark(String raw) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String part : raw.split(";")) {
            if (part.isEmpty()) continue;
            int eq = part.lastIndexOf('=');
            if (eq <= 0 || eq >= part.length() - 1) continue;
            try {
                map.put(part.substring(0, eq), Integer.parseInt(part.substring(eq + 1)));
            } catch (NumberFormatException ignored) {
                // 脏数据不致命:跳过该条(水位是"下限保护",缺失只会更保守地不再计层)
            }
        }
        return map;
    }

    private static String formatWatermark(Map<String, Integer> watermark) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : watermark.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }
}
