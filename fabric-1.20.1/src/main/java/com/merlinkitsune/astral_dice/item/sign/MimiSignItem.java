package com.merlinkitsune.astral_dice.item.sign;
import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.card.RandomCardHandler;
import com.merlinkitsune.astral_dice.item.chip.BaseChipItem;
import com.merlinkitsune.starenginelib.item.AstralRarities;
import com.merlinkitsune.starenginelib.item.Rarity;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;
import com.merlinkitsune.astral_dice.compat.curios.SlotContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.merlinkitsune.astral_dice.platform.event.SubscribeEvent;

/**
 * 看板娘立牌(mimi)。
 *
 * <p>被动:
 * - 合成或返还卡牌时,每获得一张战斗牌,增加 1 星币;
 * - 装备时,筹码栏位 +1;
 * - 每次主动技能返还累计 25 张战斗牌后,获得 1 个随机筹码
 *   (蓝色 60%,紫色 35%,金色 5%;筹码池**由物品注册表派生**,见 {@link #chipPool(Rarity)} 的说明)。
 *
 * <p>主动:将物品栏中所有卡牌回收(包括专属牌),并返还 N+1 张随机卡牌;
 * 返还的随机卡牌不会包含专属牌。
 */
public class MimiSignItem extends BaseSignItem {

    private static final Logger LOGGER = LoggerFactory.getLogger(MimiSignItem.class);
    /** 主动返还战斗牌累计阈值(达到后获得随机筹码) */
    public static final int RETURNED_CARD_THRESHOLD = 25;
    /** 随机筹码概率 */
    private static final double BLUE_CHANCE = 0.60;
    private static final double PURPLE_CHANCE = 0.35;

    public MimiSignItem(Properties properties) {
        super(properties);
    }

    @Override
    protected void clearSignData(Player player, ItemStack stack) {
        super.clearSignData(player, stack);
        ModAttachments.setMimiReturnedCardCount(player, 0);
    }

    @Override
    protected InteractionResultHolder<ItemStack> handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        // 回收物品栏中所有卡牌(含专属牌),返还 N+1 张随机卡牌(不含专属)
        int recycled = recycleAllCards(player);
        int battleBefore = countBattleCards(player);
        int coinsBefore = countStarCoins(player);
        for (int i = 0; i < recycled + 1; i++) {
            RandomCardHandler.giveCardTo(player, RandomCardHandler.CardCategory.ALL);
        }
        // 被动:返还的战斗牌每张 +1 星币,并累计返还计数(每 25 张获得随机筹码)
        int battleGained = countBattleCards(player) - battleBefore;
        for (int i = 0; i < battleGained; i++) {
            onBattleCardGained(player);
            onBattleCardReturned(player);
        }
        // 主动技能 ActionBar:新卡牌数(回收数+1)与被动触发的星币数
        sendSignActionBar(player, "msg.astral_dice.mimi_active",
                recycled + 1, countStarCoins(player) - coinsBefore);
        return InteractionResultHolder.success(stack);
    }

    // 统计物品栏中的星币数量
    private static int countStarCoins(Player player) {
        int count = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.STAR_COIN.get())) {
                count += s.getCount();
            }
        }
        return count;
    }

    // 统计物品栏中的战斗牌数量
    private static int countBattleCards(Player player) {
        int count = 0;
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty() && com.merlinkitsune.astral_dice.combat.CardRegistry.itemToType(s) != null) {
                count += s.getCount();
            }
        }
        return count;
    }

    // 主动技能 ActionBar 注册:自带提示已在 handleUse 内发送,仅阻止默认提示
    @SubscribeEvent
    public static void onSignActiveTriggered(com.merlinkitsune.starenginelib.event.SignActiveTriggeredEvent event) {
        if (event.getSignStack().is(ModItems.MIMI_SIGN.get())) {
            event.setHandled();
        }
    }

    // 玩家是否佩戴看板娘立牌
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.MIMI_SIGN.get())).isPresent();
    }

    // 被动:每获得一张战斗牌时调用(合成或主动返还;与维生素药丸相同触发机制,不包含拾取/奖励/复制)
    public static void onBattleCardGained(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        giveStarCoin(player);
    }

    // 被动:主动技能返还的战斗牌计数(每累计 25 张获得一个随机筹码)
    public static void onBattleCardReturned(Player player) {
        if (player == null || player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        int counter = ModAttachments.getMimiReturnedCardCount(player) + 1;
        if (counter >= RETURNED_CARD_THRESHOLD) {
            ModAttachments.setMimiReturnedCardCount(player, 0);
            giveRandomChip(player);
        } else {
            ModAttachments.setMimiReturnedCardCount(player, counter);
        }
    }

    // 回收物品栏中所有卡牌(包括专属牌),返回回收数量
    private static int recycleAllCards(Player player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.isEmpty()) continue;
            if (ModItems.isCardItem(stack)) {
                count += stack.getCount();
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        return count;
    }

    private static void giveStarCoin(Player player) {
        ItemStack coin = new ItemStack(ModItems.STAR_COIN.get());
        if (!player.getInventory().add(coin)) {
            player.drop(coin, false);
        }
    }

    // 随机筹码:蓝色 60%,紫色 35%,金色 5%
    private static void giveRandomChip(Player player) {
        double roll = ThreadLocalRandom.current().nextDouble();
        List<ItemStack> pool;
        if (roll < BLUE_CHANCE) {
            pool = chipPool(Rarity.RARE);
        } else if (roll < BLUE_CHANCE + PURPLE_CHANCE) {
            pool = chipPool(Rarity.EPIC);
        } else {
            pool = chipPool(Rarity.LEGENDARY);
        }
        if (pool.isEmpty()) return;
        // ⚠️ 池里存的是**模板栈** ⇒ 必须 copy 后再交给背包:原版 Inventory#add 成功时会
        //    对传入栈 setCount(0)(Inventory.java 的 add/addResource 路径) ⇒ 直接交出池中对象
        //    会把池里那一条清空,之后抽到同一档的该条目就**什么都拿不到**(add(empty)=false、
        //    drop(empty)=null,静默失败,且 pool.isEmpty() 拦不住)。
        ItemStack chip = pool.get(ThreadLocalRandom.current().nextInt(pool.size())).copy();
        if (!player.getInventory().add(chip)) {
            player.drop(chip, false);
        }
    }

    // === 筹码池:由物品注册表**派生**,不是手写清单(2026-10-01 重构) ===

    /**
     * 按档位取随机筹码池(**派生式**)。
     *
     * <h2>为什么不再手写清单</h2>
     * <p>原实现是三张手写的 {@code List<ItemStack>}(蓝 13 / 紫 16 / 金 12),**没有任何守门**核对
     * 「表 ⊇ 注册筹码按档位分组的全集」⇒ 每加一个筹码就会漏一个。2026-10-01 实测:注册筹码 **61** 个,
     * 三张表只有 **41** 个,**缺 20 个**(含充能类 10 个、飞星 2 个) —— 见 `KNOWN-ISSUES.md` KI-G1。
     * 现改为**运行时遍历物品注册表派生**:取所有 {@link BaseChipItem} 的子类,按
     * {@link AstralRarities#tierOf(net.minecraft.world.item.Rarity)} 的档位分桶
     * ⇒ **以后新增筹码自动进池,不需要再动本文件**。
     *
     * <h2>口径(与手写清单时代保持一致)</h2>
     * <ul>
     *   <li>只认**继承 {@link BaseChipItem} 的物品** ⇒ `blank_chip`(空白筹码,普通 {@code Item})天然不进池,
     *       与旧行为一致;也不依赖物品 id 的命名约定(`eight_sided_dice_chip` 这类照样覆盖);</li>
     *   <li>**进阶筹码照样进池**(旧清单里本来就有 `cutter_blade_chip` / `eagle_scope_chip` /
     *       `medkit_complete_chip` / `ninja_star_chip` 等,&nbsp;本实现保持同一口径);</li>
     *   <li>**巅峰 / 奇特不进池**(与「不进池 = 巅峰 + 奇特」的既有裁决一致;当前无此类筹码。
     *       若将来出现,会在首次建池时打一条 warn,**不静默**);</li>
     *   <li>三档概率不变:蓝(RARE) 60% / 紫(EPIC) 35% / 金(LEGENDARY) 5%。</li>
     * </ul>
     *
     * <p>⚠️ 池**惰性构建一次并缓存**:注册表内容在加载完成后稳定,而本方法只会在服务端线程被调用。
     *
     * @param tier 本模组档位({@link Rarity#RARE} / {@link Rarity#EPIC} / {@link Rarity#LEGENDARY})
     * ⚠️ **池里存的是「模板栈」**:调用方取出后**必须 {@link ItemStack#copy()}** 再交给背包 ——
     * 原版 {@code Inventory#add} 在成功时会 {@code setCount(0)} 改写传入的那个栈,直接交出池中对象会把
     * 池里那一条清空(之后抽到它就静默拿不到任何东西)。
     *
     * @return 本类内部持有的**模板栈列表**(只读使用,不得改写其中的栈);非三档档位返回空表
     */
    private static List<ItemStack> chipPool(Rarity tier) {
        if (!chipPoolsBuilt) {
            // 先在**局部**表里建好、全部成功后再一次性发布 ⇒ 中途抛异常不会留下"半成品池"
            //(标志位最后才置,下次调用会重试)。
            List<ItemStack> blue = new ArrayList<>();
            List<ItemStack> purple = new ArrayList<>();
            List<ItemStack> gold = new ArrayList<>();
            int unbucketed = 0;
            int noTier = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                if (!(item instanceof BaseChipItem)) {
                    continue; // 非筹码(含 blank_chip)不进池
                }
                ItemStack stack = new ItemStack(item);
                Rarity itemTier = AstralRarities.tierOf(stack.getRarity());
                if (itemTier == Rarity.RARE) {
                    blue.add(stack);
                } else if (itemTier == Rarity.EPIC) {
                    purple.add(stack);
                } else if (itemTier == Rarity.LEGENDARY) {
                    gold.add(stack);
                } else if (itemTier == null) {
                    noTier++; // 筹码没标 .rarity(...) —— 属配置遗漏
                } else {
                    unbucketed++; // 巅峰/奇特:按「不进池」口径排除
                }
            }
            blueChipPool.addAll(blue);
            purpleChipPool.addAll(purple);
            goldChipPool.addAll(gold);
            chipPoolsBuilt = true; // 置位必须在填充**之后**
            LOGGER.info("[Astral Dice] AP_MIMI_CHIP_POOL: blue={} purple={} gold={} total={} unbucketed={} no_tier={}",
                    blueChipPool.size(), purpleChipPool.size(), goldChipPool.size(),
                    blueChipPool.size() + purpleChipPool.size() + goldChipPool.size(), unbucketed, noTier);
            if (blueChipPool.isEmpty() || purpleChipPool.isEmpty() || goldChipPool.isEmpty()) {
                LOGGER.warn("[Astral Dice] 看板娘筹码池有一档为空 —— 随机筹码会退化,检查筹码注册与 rarity 标注");
            }
            if (noTier > 0) {
                LOGGER.warn("[Astral Dice] 有 {} 个筹码没有档位(未标 .rarity(...)),它们不会进随机池", noTier);
            }
            if (unbucketed > 0) {
                LOGGER.info("[Astral Dice] 有 {} 个筹码是巅峰/奇特档,按既有「不进池」口径排除", unbucketed);
            }
        }
        if (tier == Rarity.RARE) return blueChipPool;
        if (tier == Rarity.EPIC) return purpleChipPool;
        if (tier == Rarity.LEGENDARY) return goldChipPool;
        return List.of();
    }

    /** 蓝/紫/金三档的派生池:**模板栈**(惰性构建一次,见 {@link #chipPool};取出必须 copy)。 */
    private static final List<ItemStack> blueChipPool = new ArrayList<>();
    private static final List<ItemStack> purpleChipPool = new ArrayList<>();
    private static final List<ItemStack> goldChipPool = new ArrayList<>();
    private static boolean chipPoolsBuilt;
}
