package com.merlinkitsune.astral_dice.item.dice;

import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.MimiSignItem;

public class DiceCurioItem extends Item implements ICurioItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiceCurioItem.class);
    // 未佩戴骰子时的筹码栏位数量(对应 curios/slots/chip.json 的 size:0,需求:必须佩戴骰子才有筹码栏)
    private static final int CHIP_NO_DICE_SLOTS = 0;
    // 筹码栏绝对尺寸的修饰符 id:自有命名,与 Curios 私有的 curios:legacy 区分开
    private static final ResourceLocation CHIP_SLOT_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "chip_slots");
    // Curios 的 grow/shrink 累加器使用的私有修饰符 id(CurioStacksHandler.LEGACY_ID),
    // 1.2.x 的本模组实现曾依赖它;迁移时必须清掉,否则会与上面的绝对修饰符叠加(筹码栏翻倍)
    private static final ResourceLocation CURIO_LEGACY_MODIFIER =
            ResourceLocation.fromNamespaceAndPath("curios", "legacy");

    public DiceCurioItem(Properties properties) {
        super(properties);
    }

    // 判断物品栈是否是任意一种骰子,供其它逻辑统一识别
    public static boolean isDiceItem(ItemStack stack) {
        return DiceTierRegistry.isDice(stack);
    }

    // 卡牌放置栏总槽位数:由"卡牌配置星级"决定,与骰子品阶无关——0★=4(攻防各2)、1★=6(各3)、
    // 2★=8(各4)、3★=12(各6);星级超出 0-3 时按最近档钳制。
    // 下界之星骰子(T4):卡牌槽与费用点数始终按最高档 3★ 配置(12 格 / 费用上限各 6)。
    private static final int[] CARD_SLOTS_BY_STAR = {4, 6, 8, 12};

    /** 卡牌配置星级:下界之星骰子恒为 3★(最高档),其余骰子取实际星级 */
    public static int configStarLevel(ItemStack stack) {
        if (!stack.isEmpty() && stack.is(ModItems.NETHER_STAR_DICE.get())) {
            return 3;
        }
        return Math.max(0, Math.min(3, starLevel(stack)));
    }

    public static int getCardSlots(ItemStack stack) {
        return CARD_SLOTS_BY_STAR[configStarLevel(stack)];
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        // 骰子只能放入"dice"饰品栏
        return "dice".equals(slotContext.identifier());
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
                                                                      Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 下蹲右键:自动装备到"dice"饰品栏
        if (player.isShiftKeyDown()) {
            return CurioSlotUtil.tryAutoEquip(player, stack, "dice");
        }
        return net.minecraft.world.InteractionResultHolder.pass(stack);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        // Curios 官方签名:第 2 参 prevStack = **槽位原内容**(普通装备时是空栈),第 3 参 stack = 刚装上的骰子。
        // 旧实现把第 2 参当骰子用 ⇒ targetChipSlots(EMPTY) 恒为 0、「装备即加筹码栏」整条路径是空操作,
        // 只能等 curioTick 每 20 tick(≈1 秒)补上 —— 玩家反馈的「装备骰子时偶发不增加筹码栏位」即此。
        if (!stack.isEmpty() && !stack.has(ModDataComponents.WEAPON_ENHANCEMENT.get())) {
            stack.set(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
        }
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide()) {
            // 防御式调整(forceRemove=false):目标值在「骰子槽瞬时为空」的读数下会算成 0,
            // 强制收缩会移出 chip 等槽位的合法物品(弹出 bug);有物品的槽位保持不动,物品安全。
            // 立牌栏固定 1(stand.json size=1),不做动态调整。
            refreshChipSlotCount(player);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (!slotContext.entity().level().isClientSide()) {
            // Curios 官方签名:第 2 参 newStack = 将要占用槽位的栈,第 3 参 stack = **被卸下的那件骰子**。
            // 旧实现误把第 2 参(newStack)传给了 tryRemoveChipBonus——那不是被卸下的骰子。
            tryRemoveChipBonus(slotContext, stack);
        }
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity().level().isClientSide()) return;
        // 每 20 tick 对账一次(兜底:/curios reset、同步包丢失、旧存档漂移);
        // 正常路径(装备/卸下/登录/数据包同步)已即时生效,不再依赖这条 20 tick 轮询。
        if (slotContext.entity().tickCount % 20 != 0) return;
        if (slotContext.entity() instanceof Player player) {
            refreshChipSlotCount(player, null, false, true);
        }
    }

    // === 筹码栏位 ===

    /**
     * 按**当前真正佩戴在 dice 栏里的骰子**重算筹码栏尺寸。
     *
     * <p>不依赖任何事件入参 —— Curios 的 {@code onEquip(SlotContext, ItemStack prevStack, ItemStack stack)}
     * 第 2 参是槽位原内容(普通装备时为空栈),旧实现把它当骰子用,导致目标恒为 0、
     * 「装备即加筹码栏」整条路径是空操作,只能等 {@link #curioTick} 每 20 tick(≈1 秒)补上。
     * 入口同时供登录 / 数据包同步 / 复活克隆后的对账使用。
     */
    public static void refreshChipSlotCount(Player player) {
        refreshChipSlotCount(player, null, false, false);
    }

    /**
     * 骰子已离开槽位:目标固定为 0。
     *
     * <p>不在 {@code onUnequip} 里回读 dice 栏 —— 该回调发生时那件骰子可能还没从栏位里摘掉,
     * 读数会把它算进去,导致筹码栏不归零。
     */
    private static void clearChipSlotCount(Player player, boolean forceRemove) {
        refreshChipSlotCount(player, CHIP_NO_DICE_SLOTS, forceRemove, false);
    }

    /**
     * @param forcedTarget  非 null 时直接使用该目标值;null 表示按当前 dice 栏内容推算
     * @param throttledWarn true 时节流「chip 处理器缺失」的告警(供每 20 tick 的 tick 路径使用)
     */
    private static void refreshChipSlotCount(Player player, Integer forcedTarget, boolean forceRemove, boolean throttledWarn) {
        if (player == null || player.level().isClientSide()) return;
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            ICurioStacksHandler chip = inventory.getStacksHandler("chip").orElse(null);
            if (chip == null) {
                warnMissingChipHandler(player, inventory, throttledWarn);
                return;
            }
            int target;
            if (forcedTarget != null) {
                target = forcedTarget;
            } else {
                ItemStack dice = inventory.getStacksHandler("dice")
                        .map(handler -> handler.getStacks().getStackInSlot(0))
                        .orElse(ItemStack.EMPTY);
                target = CHIP_NO_DICE_SLOTS;
                if (!dice.isEmpty()) {
                    target = targetChipSlots(dice);
                    // 看板娘立牌被动:装备骰子时筹码栏位 +1;未佩戴骰子时不给,维持「必须佩戴骰子才有筹码栏」
                    if (MimiSignItem.isEquipped(player)) {
                        target += 1;
                    }
                }
            }
            setSlotCount(player, chip, target, forceRemove);
        });
    }

    // 找不到 chip 栏位处理器时不能静默:旧实现是 flatMap(...).ifPresent(...),这种情况什么都不做,
    // 日志里也没有任何痕迹。该支(Curios 时序 / 槽位表问题)与「有 chip 但尺寸没长」(本模组逻辑问题)
    // 的修复方向完全不同,必须能一眼区分,故带上当前 curiosKeys。
    private static void warnMissingChipHandler(Player player, ICuriosItemHandler inventory, boolean throttled) {
        if (throttled && player.tickCount % 200 != 0) return;
        LOGGER.warn("[Astral Dice][chip] 未找到 chip 槽位处理器,筹码栏位未调整:player={}, curiosKeys={}",
                player.getGameProfile().getName(), inventory.getCurios().keySet());
    }

    private static int targetChipSlots(ItemStack stack) {
        // 筹码栏规则统一由 DiceTierRegistry 提供(必须佩戴骰子才有筹码栏)
        DiceTier tier = DiceTierRegistry.get(stack);
        if (tier == null) return CHIP_NO_DICE_SLOTS;
        return tier.targetChipSlots(starLevel(stack));
    }

    private static int starLevel(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY).starLevel();
    }

    private void tryRemoveChipBonus(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        // 骰子已卸下(或即将离开槽位):目标 0。仍走防御式收缩 —— 栏内还有筹码时保持占用它的那些槽位,
        // 不会像强制收缩那样把合法筹码交给背包。
        clearChipSlotCount(player, false);
    }

    // 通用槽位调整:
    // forceRemove=true(玻璃骰死亡等主动回收):收缩前把将被移除槽位中的物品归还玩家物品栏;
    // forceRemove=false(装备/卸下/登录/数据包同步/每 tick 对账):**不缩掉仍在使用的槽位** ——
    //   目标值在「骰子槽瞬时为空」的读数下会算成 0(登录、数据包同步当拍都会发生),照收会把还在使用的
    //   槽位连同筹码一起交给背包;故把目标抬到「最靠后的非空槽位 + 1」,只允许增长或保持。
    private static void setSlotCount(Player player, ICurioStacksHandler handler, int target, boolean forceRemove) {
        int current = handler.getSlots();
        if (current > target) {
            if (!forceRemove) {
                int highestOccupied = 0;
                for (int i = 0; i < current; i++) {
                    try {
                        if (!handler.getStacks().getStackInSlot(i).isEmpty()) {
                            highestOccupied = i + 1;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (highestOccupied > target) {
                    target = highestOccupied;
                }
            } else {
                // 主动调整:将被移除槽位中的物品归还玩家物品栏
                for (int i = target; i < current; i++) {
                    try {
                        ItemStack s = handler.getStacks().getStackInSlot(i);
                        if (!s.isEmpty()) {
                            handler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                            if (!player.getInventory().add(s)) {
                                player.drop(s, false);
                            }
                            LOGGER.warn("[Astral Dice][setSlotCount] forceRemove: 槽位物品被移出并归还背包: {} (slot index {}), current={} target={}", s, i, current, target);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        applySlotCount(handler, target);
    }

    // 尺寸改写走 Curios 的「槽位修饰符 → update() → resize()」链路(2026-09-17 修,与 26.1.2 线同口径)。
    // Curios 的 grow/shrink 只是在**它自己的 legacy 累加器**上做相对加减(尺寸是存档里的缓存值,
    // 一旦偏了不会自愈);改用自有 id 的**绝对**修饰符后,尺寸 = baseSize(0) + 本修饰符,写入即覆盖、幂等。
    //   - addPermanentModifier 内部先 addTransientModifier(会 flagUpdate())再登记到 persistentModifiers
    //     ⇒ 随存档保留,登录第一刻尺寸就是目标值,不必等 curioTick 补;
    //   - 归零时也保留 0 值修饰符:它同时是「本模组接管该栏位尺寸」的标记(移除后无修饰符可 flagUpdate,
    //     尺寸会停在旧值);
    //   - 值未变时直接返回:否则每 20 tick 的 tick 路径都会 flagUpdate 一次,Curios 会每秒推一次同步包。
    private static void applySlotCount(ICurioStacksHandler handler, int target) {
        int wanted = Math.max(0, target);
        boolean migratedLegacy = false;
        // 1.2.x 旧实现用 Curios 的 grow/shrink,其累加器以 Curios 私有的 legacy 修饰符留在存档里;
        // 不清理就会与下面的绝对修饰符叠加(筹码栏翻倍)。不存在时 removeModifier 是 no-op。
        if (handler.getModifiers().containsKey(CURIO_LEGACY_MODIFIER)) {
            handler.removeModifier(CURIO_LEGACY_MODIFIER);
            migratedLegacy = true;
            LOGGER.info("[Astral Dice][chip] 迁移:清除 Curios legacy 槽位修饰符(旧 grow/shrink 累加器残留),改由 {} 绝对控制, target={}",
                    CHIP_SLOT_MODIFIER, wanted);
        }
        AttributeModifier existing = handler.getModifiers().get(CHIP_SLOT_MODIFIER);
        if (!migratedLegacy && existing != null && (int) existing.amount() == wanted && handler.getSlots() == wanted) {
            return;
        }
        handler.removeModifier(CHIP_SLOT_MODIFIER);
        handler.addPermanentModifier(new AttributeModifier(CHIP_SLOT_MODIFIER, wanted,
                AttributeModifier.Operation.ADD_VALUE));
        handler.update();
    }

    // 玻璃骰子死亡惩罚:移除骰子本体(连同其 WEAPON_ENHANCEMENT 中已装备的全部卡牌),
    // 并把筹码栏收缩归零(forceRemove=true,槽内筹码归还物品栏)。
    public static void removeGlassDiceOnDeath(Player player) {
        if (player == null || player.level().isClientSide()) return;
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            handler.getStacksHandler("dice").ifPresent(diceHandler -> {
                ItemStack dice = diceHandler.getStacks().getStackInSlot(0);
                if (!dice.isEmpty() && dice.is(ModItems.GLASS_DICE.get())) {
                    diceHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                    handler.getStacksHandler("chip").ifPresent(chip ->
                            setSlotCount(player, chip, CHIP_NO_DICE_SLOTS, true));
                    LOGGER.info("[Astral Dice] 玻璃骰子死亡丢失: {} 的玻璃骰子及其卡牌已移除", player.getGameProfile().getName());
                }
            });
        });
    }
}
