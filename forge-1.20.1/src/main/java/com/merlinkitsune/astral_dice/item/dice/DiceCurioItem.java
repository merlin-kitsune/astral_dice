package com.merlinkitsune.astral_dice.item.dice;

import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.MimiSignItem;

public class DiceCurioItem extends Item implements ICurioItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiceCurioItem.class);
    // 未佩戴骰子时的筹码栏位数量(对应 curios/slots/chip.json 的 size:0,需求:必须佩戴骰子才有筹码栏)
    private static final int CHIP_NO_DICE_SLOTS = 0;

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
    public void onEquip(SlotContext slotContext, ItemStack curio, ItemStack prevStack) {
        if (!ModDataComponents.WEAPON_ENHANCEMENT.has(curio)) {
            ModDataComponents.WEAPON_ENHANCEMENT.set(curio,  WeaponEnhancement.EMPTY);
        }
        if (!slotContext.entity().level().isClientSide()) {
            // 防御式调整(forceRemove=false):Curios 重载/进入世界等场景 onEquip 触发时,
            // target 槽位数可能被瞬时计算错误,强制收缩会移出 chip 等槽位的合法物品(弹出 bug)。
            // 槽位只会 grow 或按需收缩;有物品的槽位保持不动,物品安全。
            // 立牌栏固定 1(stand.json size=1),不做动态调整。
            tryApplyChipBonus(slotContext, curio, false);
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
        // 每 20 tick 维持一次筹码槽位数;防御模式:被移除槽位有物品时不收缩,避免战斗等场景误弹出合法装备
        if (slotContext.entity().tickCount % 20 != 0) return;
        tryApplyChipBonus(slotContext, stack, false);
    }

    // === 筹码栏位 ===
    private void tryApplyChipBonus(SlotContext slotContext, ItemStack stack, boolean forceRemove) {
        if (!(slotContext.entity() instanceof Player player)) return;
        int target = targetChipSlots(stack);
        // 看板立牌被动:装备时筹码栏位 +1
        if (MimiSignItem.isEquipped(player)) {
            target += 1;
        }
        int finalTarget = target;
        CuriosCompat.getCuriosInventory(player)
                .flatMap(h -> h.getStacksHandler("chip"))
                .ifPresent(handler -> setChipSlotCount(player, handler, finalTarget, forceRemove));
    }

    private static int targetChipSlots(ItemStack stack) {
        // 筹码栏规则统一由 DiceTierRegistry 提供(必须佩戴骰子才有筹码栏)
        DiceTier tier = DiceTierRegistry.get(stack);
        if (tier == null) return CHIP_NO_DICE_SLOTS;
        return tier.targetChipSlots(starLevel(stack));
    }

    private static int starLevel(ItemStack stack) {
        return ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(stack,  WeaponEnhancement.EMPTY).starLevel();
    }

    private void tryRemoveChipBonus(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        // 取下骰子后筹码栏归零,必须佩戴骰子饰品才能拥有筹码栏。
        // 同样使用防御式收缩(forceRemove=false)防止 Curios 重载场景下筹码被移出(弹出 bug)。
        CuriosCompat.getCuriosInventory(player)
                .flatMap(h -> h.getStacksHandler("chip"))
                .ifPresent(handler -> setChipSlotCount(player, handler, CHIP_NO_DICE_SLOTS, false));
    }

    private static void setChipSlotCount(Player player, ICurioStacksHandler handler, int target, boolean forceRemove) {
        setSlotCount(player, handler, target, forceRemove);
    }

    // 通用槽位调整:
    // forceRemove=true(佩戴/卸下骰子时):收缩前把将被移除槽位中的物品归还玩家物品栏;
    // forceRemove=false(每 tick 维持):被移除槽位有物品时不收缩,避免误弹出合法装备
    private static void setSlotCount(Player player, ICurioStacksHandler handler, int target, boolean forceRemove) {
        int current = handler.getSlots();
        if (current > target) {
            if (!forceRemove) {
                // 防御模式:被移除的槽位(索引 target..current-1)中有物品时跳过收缩
                boolean hasItems = false;
                for (int i = target; i < current; i++) {
                    try {
                        if (!handler.getStacks().getStackInSlot(i).isEmpty()) {
                            hasItems = true;
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (hasItems) return;
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
            handler.shrink(current - target);
        } else if (current < target) {
            handler.grow(target - current);
        }
    }

    // 玻璃骰子死亡惩罚:移除骰子本体(连同其 WEAPON_ENHANCEMENT 中已装备的全部卡牌),
    // 并把筹码栏收缩归零(forceRemove=true,槽内筹码归还物品栏)。
    public static void removeGlassDiceOnDeath(Player player) {
        if (player == null || player.level().isClientSide()) return;
        CuriosCompat.getCuriosInventory(player).ifPresent(handler -> {
            handler.getStacksHandler("dice").ifPresent(diceHandler -> {
                ItemStack dice = diceHandler.getStacks().getStackInSlot(0);
                if (!dice.isEmpty() && dice.is(ModItems.GLASS_DICE.get())) {
                    diceHandler.getStacks().setStackInSlot(0, ItemStack.EMPTY);
                    handler.getStacksHandler("chip").ifPresent(chip ->
                            setChipSlotCount(player, chip, CHIP_NO_DICE_SLOTS, true));
                    LOGGER.info("[Astral Dice] 玻璃骰子死亡丢失: {} 的玻璃骰子及其卡牌已移除", player.getGameProfile().getName());
                }
            });
        });
    }
}
