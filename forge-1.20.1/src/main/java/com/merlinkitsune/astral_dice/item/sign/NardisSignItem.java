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
 * <h2>主动「女王特权」</h2>
 * <ol>
 *   <li>**先清空**全部旧临时牌(物品栏 + 副手 + 骰子已装配的) —— 用户裁决③「清空重发」;</li>
 *   <li>发 3 张随机牌({@code CardCategory.ALL},专属牌由既有池逻辑自动排除),先打临时牌标记
 *       再走 {@link com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem#giveCard} 发牌漏斗
 *       ⇒ 维生素药丸治愈、教主立牌狐光等「获得卡牌」触发器全部照常生效;
 *       背包放不下时**少发**(最多 {@code min(3, 空槽数)} 张),**绝不落地**;</li>
 *   <li>施加自身效果 {@link ModEffects#NARDIS_PRIVILEGE} **3:00**,六参且 {@code showIcon=true}
 *       ⇒ HUD 计时器 + 图标(立牌贴图)全由这条原生效果实例承担;</li>
 *   <li>ActionBar 反馈本次实际发放张数(见 {@code #onSignActiveTriggered},抑制默认提示)。</li>
 * </ol>
 *
 * <h2>为什么**不**覆写 {@code startActiveLockOnUse}(即不起「锁定/生效中」态)</h2>
 * 本主动**刻意保持默认 {@code false}**:触发成功后**立即**开始玩家级冷却。
 * 理由:用户裁决③「重复释放 = 先清空旧临时牌再发 3 张新的」只有在**冷却被减免之后**能在
 * 3:00 效果仍生效时再次释放才有意义(诡异骰子 -50% / 充能递减 / 电流核心立即完成冷却 三条路);
 * 若像 jasmine / fen / papara / teru 那样「挂了自身计时器就进锁定态」,冷却要等效果结束后才起算
 * —— 下次可释放时刻在 6:00 之后,裁决③将**永不可达**。
 * ⚠️ 这与那几张立牌的惯例**不同**,属本技能特有的取舍(已登记在
 * {@code docs/features/nardis-sign-spec.md} §6.3)。两线口径一致(1.20.1
 * {@code BaseSignItem#startActiveLockOnUse} 默认同样为 {@code false})。
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
        // 客户端早退(照 MimiSignItem:52-54):真正的判定与发牌一律服务端权威
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 1. 裁决③:先清空**全部**旧临时牌(物品栏 0..35 + 副手 + 骰子已装配的),幂等
        TemporaryCardUtil.purgeAll(player);
        // 2. 发 3 张(空槽不足则少发,绝不落地);走 VitaminPillChipItem.giveCard 发牌漏斗
        int granted = TemporaryCardUtil.grantRandom(player, TemporaryCardUtil.GRANT_COUNT);
        // 3. 自身效果 3:00:**必须六参且 showIcon=true**(照 JasmineSignItem:64)——
        //    HUD 计时器与立牌图标全靠这条原生效果实例;同时它是「临时牌仍在有效期」的唯一真值。
        //    1.20.1 差异:效果常量是 RegistryObject ⇒ 必须 .get()。
        EffectTimerGuard.apply(player, new MobEffectInstance(ModEffects.NARDIS_PRIVILEGE.get(),
                NardisPrivilegeEffect.DURATION_TICKS, 0, false, false, true));
        // 4. ActionBar:实际发放张数(默认「主动技能已启动」提示由 onSignActiveTriggered 抑制)
        sendSignActionBar(player, "msg.astral_dice.nardis_active", granted);
        return InteractionResultHolder.success(stack);
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
