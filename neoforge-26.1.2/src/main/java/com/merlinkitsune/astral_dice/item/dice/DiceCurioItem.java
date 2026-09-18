package com.merlinkitsune.astral_dice.item.dice;

import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.sign.MimiSignItem;

public class DiceCurioItem extends Item implements ICurioItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiceCurioItem.class);
    // 未佩戴骰子时的筹码栏位数量(对应 curios/slots/chip.json 的 size:0,需求:必须佩戴骰子才有筹码栏)
    private static final int CHIP_NO_DICE_SLOTS = 0;
    // 筹码栏尺寸的槽位修饰符 id(Curios 15:尺寸 = baseSize + Σ ADD_VALUE 修饰符;见 applySlotCount)
    private static final Identifier CHIP_SLOT_MODIFIER = Identifier.fromNamespaceAndPath("astral_dice", "chip_slots");

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
    public InteractionResult use(net.minecraft.world.level.Level level,
                                                                      Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 下蹲右键:自动装备到"dice"饰品栏
        if (player.isShiftKeyDown()) {
            return CurioSlotUtil.tryAutoEquip(player, stack, "dice");
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        // Curios 官方签名:第 2 参 prevStack = **槽位原内容**(普通装备时是空栈),第 3 参 stack = 刚装上的骰子。
        // 旧实现把第 2 参当骰子用 ⇒ targetChipSlots(EMPTY) 恒为 0、「装备即加筹码栏」整条路径是空操作,
        // 只能等 curioTick 每 20 tick(≈1 秒)补上 —— 与 1.21.1/1.20.1 侧同源同形的缺陷(2026-09-18 三线统一修)。
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
        // 正常路径(装备/卸下/登录迁移恢复)已即时生效,不再依赖这条 20 tick 轮询。
        if (slotContext.entity().tickCount % 20 != 0) return;
        if (slotContext.entity() instanceof Player player) {
            refreshChipSlotCount(player, null, false, true);
        }
    }

    // === 筹码栏位 ===

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
                    // 看板立牌被动:装备骰子时筹码栏位 +1;未佩戴骰子时不给,维持「必须佩戴骰子才有筹码栏」
                    if (MimiSignItem.isEquipped(player)) {
                        target += 1;
                    }
                }
            }
            setSlotCount(player, chip, target, forceRemove);
        });
    }

    // 找不到 chip 栏位处理器时不能静默:旧实现直接 return,这种情况什么都不做、日志里也没有痕迹。
    // 该支(Curios 时序 / 槽位表问题)与「有 chip 但尺寸没长」(本模组逻辑问题)的修复方向完全不同,
    // 必须能一眼区分,故带上当前 curiosKeys。
    private static void warnMissingChipHandler(Player player, ICuriosItemHandler inventory, boolean throttled) {
        if (throttled && player.tickCount % 200 != 0) return;
        LOGGER.warn("[Astral Dice][chip] 未找到 chip 槽位处理器,筹码栏位未调整:player={}, curiosKeys={}",
                player.getGameProfile().name(), inventory.getCurios().keySet());
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

    /**
     * 按**当前佩戴的骰子**重算并应用筹码栏尺寸（供登录迁移的恢复阶段 {@code ChipSlotMigrationHandler#restore}
     * 与登录/数据包同步后的对账调用）。
     *
     * <p>迁移刚结束时 Curios 才重建完栏位、骰子的 {@code onEquip} 还没跑，
     * 故必须由这里主动把尺寸调到「应有的值」再往栏内放物品 —— 否则 0 格的栏位放不下任何东西，
     * 筹码又会被交还到背包（等于没修）。
     */
    public static void refreshChipSlotCount(Player player) {
        refreshChipSlotCount(player, null, false, false);
    }

    // 通用槽位调整:
    // forceRemove=true(佩戴/卸下骰子时):收缩前把将被移除槽位中的物品归还玩家物品栏;
    // forceRemove=false(每 tick 维持 / 登录迁移后恢复):**不缩掉仍在使用的槽位**——
    //   目标值可能来自「骰子槽内容瞬时为空」的读数(迁移当拍就会发生),若照收会把还在使用的
    //   槽位连同物品一起交给背包;故把目标值抬到「最靠后的非空槽位 + 1」,只允许增长或保持。
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

    // 尺寸改写必须走 Curios 的「槽位修饰符 → update() → resize()」链路(2026-09-17 修,崩溃级)。
    //   - Curios 15 的 ICurioStacksHandler 已**没有** grow/shrink(1.21.1 的 handler.grow/shrink 即此法,
    //     由 CurioStacksHandler 一并调整 stacks 与 cosmeticStacks);尺寸 = baseSize + Σ(ADD_VALUE 修饰符),
    //     由 CurioStacksHandler#update() 重算并调用其私有 resize()。
    //   - resize() 同时调整 stackHandler / cosmeticStackHandler / renderHandler / activeStates。
    // ⚠️ 反面写法(移植初版)直接 `handler.getStacks().grow/shrink(...)`,只改 stackHandler:
    //     Curios 自身 tick 循环以 getSlots()(=stackHandler 尺寸)为界,却**无保护地**读
    //     getCosmeticStacks().getStackInSlot(i)(实测 CuriosCommonEvents 行 599)→ 一旦两者不等,
    //     玩家一装备 ≥1★ 骰子(筹码栏 0→N)即抛 `Slot 0 not in valid range - [0,0)` 并**崩服**。
    //
    // ⚠️ 用 addPermanentModifier 而不是 addTransientModifier(2026-09-17):
    //     permanent 会随存档保留(`CurioStacksHandler#deserialize` 逐条 addPermanentModifier;序列化键实测为
    //     `PermanentModifiers`),于是**登录第一刻**槽位数就是目标值,而不是先按数据包尺寸(0)装载、
    //     再由 DiceCurioItem#curioTick 在 20 tick 内补回来 —— 槽数与「玩家实际拥有的筹码栏」从登录起就一致,
    //     也避免了登录窗口内 Curios 登录迁移(`CurioInventory#loadInventoryConfiguration`,按数据包重建默认栏位
    //     并按新栏位槽数搬移物品)看到新旧槽数不一致而走补偿分支。
    //     ⚠️ 槽内**合法**筹码在重登后被搬出到玩家背包的缺陷已定位并修复（2026-09-17）：
    //     根因在 Curios 15.0.0 的登录迁移 `CurioInventory#loadInventoryConfiguration()` ——
    //     其搬移循环上界取 `curioStacksHandler.getSlots()`，而 `CurioStacksHandler#getSlots()`
    //     → `update()` 首行是 `if (this.dataLoaded)`，`setDataLoaded()` 又只在该方法**末尾**
    //     才调用 ⇒ 循环期间读到的是构造函数里的**数据包原始尺寸**（本模组 chip 槽写死 0），
    //     于是上界为 0、一次都不搬，旧内容全部进 `invalidStacks` 并被交还玩家背包。
    //     本模组的对策见 `event/ChipSlotMigrationHandler`（数据包同步 HIGHEST 快照并清空 /
    //     LOWEST 重算尺寸后按索引还原）；上游修法见 `docs/upstream/` 的补丁与缺陷报告。
    //     回归用例：`scripts/test/cases/CHIP-RELOG-{A,B}-26.1.2.json`（两个槽位都要留存）。
    private static void applySlotCount(ICurioStacksHandler handler, int target) {
        int wanted = Math.max(0, target);
        // ⚠️ 本线**不做**「值未变就早退」的优化(1.21.1/1.20.1 侧做了):Curios 15 的
        //    `getSlots()` = `stackHandler.getSlots()`,而 `update()` 只在 `this.update` 脏标记为真时
        //    才重算并 `resize()`;实测(2026-09-18)经 `/curios reset` 重建后的 handler 会出现
        //    「修饰符已写入(2)但 stackHandler 仍为 0」的失配,此时只有再写一次(remove+add → flagUpdate)
        //    才能把它拉回一致。故本线保持**无条件写入**(每 20 tick 一次,tick 路径不早退也只会
        //    在尺寸真的一致时多推一次同步包,代价可接受;判据与实测见 TESTING-SPEC 附录 A 的 t34 条)。
        handler.removeModifier(CHIP_SLOT_MODIFIER);
        // addPermanentModifier 内部先 addTransientModifier(会 flagUpdate())再登记到 persistentModifiers。
        // 归零时也保留这个 0 值修饰符:它同时是「本模组接管该栏位尺寸」的标记,移除后无修饰符可 flagUpdate,
        // 尺寸会停在旧值(Curios 只在被 flag 时才重算)。
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
                    LOGGER.info("[Astral Dice] 玻璃骰子死亡丢失: {} 的玻璃骰子及其卡牌已移除", player.getGameProfile().name());
                }
            });
        });
    }
}
