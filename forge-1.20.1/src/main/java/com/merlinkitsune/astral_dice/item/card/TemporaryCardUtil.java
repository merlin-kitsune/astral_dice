package com.merlinkitsune.astral_dice.item.card;

import com.merlinkitsune.astral_dice.component.AppliedStone;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.effect.NardisPrivilegeEffect;
import com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 「临时牌」统一工具类(绿洲女王立牌 nardis 主动「女王特权」)。
 *
 * <h2>临时牌的语义</h2>
 * 带 {@link ModDataComponents#TEMPORARY_CARD} 键的卡牌 = 临时牌:有效期 3:00(真值 = 原生效果实例
 * {@link ModEffects#NARDIS_PRIVILEGE}),期间**不可丢弃**、**不可移入其它容器**、带附魔光效;
 * 效果结束(自然到期 / 被外力移除 / 死亡 / 重登自检)即**整体清空**(含已装配到骰子里的那些);
 * 同时持有的张数**上限** = {@link #MAX_TEMPORARY_CARDS}(= 9,叠加补给的上限,2026-09-27 用户裁决⑦)。
 *
 * <h2>为什么「只有物品标记」不够</h2>
 * 装备卡牌会**销毁**物品栈、卸除时按 {@code (type, uses)} 重建全新栈 —— 见
 * {@code screen/CardInventoryMenu#saveToDice} 内既有注释。故临时性在「已装配」状态下由
 * {@link AppliedStone#temporary()} 承载,本类清理时两者一起处理。
 *
 * <h2>落点</h2>
 * <ul>
 *   <li>发放:{@link #grantNardisPrivilege(Player)}(= 2 张战斗牌 + 1 张效果牌,2026-09-27 用户裁决①)
 *       与通用的 {@link #grantRandom(Player, RandomCardHandler.CardCategory, int)}
 *       (两者都走 {@link VitaminPillChipItem#giveCard} 发牌漏斗,保证「获得卡牌」类触发器全部生效);</li>
 *   <li>清理:{@link #purgeAll(Player)}(幂等,调用点 = 释放路径之外的死亡与到期清牌)与玩家级 tick 自检
 *       {@link #tick(Player)}(收口条件只有一条:**无效果 ⇒ 清牌**;
 *       上一版的「无牌 ⇒ 移除效果」反向分支已按 2026-09-27 用户裁决删除 —— 牌被用光**不再**提前结束效果);</li>
 *   <li>保护:不可丢弃见 {@link CardItem#onDroppedByPlayer} 与
 *       {@code event/TemporaryCardEvents};不可移入容器见 {@link #isPlacementBlocked(ItemStack, boolean)}
 *       (全部 mixin 共用这一条判据)。</li>
 * </ul>
 *
 * <p>全类不新增任何玩家附件:计数与清理都是**读时现算**,清理动作全程 try/catch(Throwable) 只记日志,
 * 绝不让 tick / 死亡流程崩。
 *
 * <h2>1.20.1 平台适配(相对 1.21.1 的镜像改写)</h2>
 * <ul>
 *   <li>物品数据走 {@link ModDataComponents#TEMPORARY_CARD}({@code ItemDataKey} = ItemStack NBT):
 *       读 {@code get(stack)}(缺省 {@code null} ⇒ 必须 {@code Boolean.TRUE.equals(...)} 判定)、
 *       写 {@code set(stack, v)}、删 {@code remove(stack)};</li>
 *   <li>Curios 经本仓库包装 {@link CuriosCompat#getCuriosInventory} 统一为 {@code Optional}
 *       (1.20.1 原生返回 {@code LazyOptional});</li>
 *   <li>效果引用一律 {@code ModEffects.NARDIS_PRIVILEGE.get()}(1.20.1 是 {@code RegistryObject});</li>
 *   <li>骰子组件的读写 {@code ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, …)} /
 *       {@code .set(dice, …)}。</li>
 * </ul>
 */
public final class TemporaryCardUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemporaryCardUtil.class);

    /** 女王特权的固定发放张数 = {@value #GRANT_BATTLE_COUNT} 战斗牌 + {@value #GRANT_EFFECT_COUNT} 效果牌 */
    public static final int GRANT_COUNT = 3;

    /**
     * 其中**先**发放的战斗牌张数(2026-09-27 用户裁决①)。
     * 池 = {@link RandomCardHandler.CardCategory#BATTLE}(攻击牌 + 防御牌**混合池**,
     * 两张各自独立随机 ⇒ 允许两张都是攻击或都是防御)。
     */
    public static final int GRANT_BATTLE_COUNT = 2;

    /** 战斗牌之后发放的效果牌张数(池 = {@link RandomCardHandler.CardCategory#EFFECT}) */
    public static final int GRANT_EFFECT_COUNT = 1;

    /** 主动技能的最低可用格数门槛(2026-09-27 用户裁决放宽为 2)。
     *  卡牌物品可堆叠({@code stacksTo(64)}),且同 id 的临时牌 NBT 相同 ⇒ {@code Inventory#add} 走合并分支
     *  并进既有临时牌堆(叠加语义下比"先清空再发"更宽松)⇒ 恰好 2 格时两张战斗牌常并进一格,
     *  效果牌仍可能放得下;少于 2 格则必然只能发 ≤1 张,不值得消耗一次释放。 */
    public static final int MIN_FREE_SLOTS_TO_CAST = 2;

    /** 临时牌同时持有的**上限**(2026-09-27 用户裁决⑦:叠加补给下必须设上限 —— 否则只要在冷却好了
     *  就再放一次,而每次释放都会把有效期重置为 3:00 ⇒ 临时牌永不过期、每 180 秒净增 3 张,无上界)。
     *
     *  <p>两个作用点(缺一不可):
     *  <ol>
     *    <li>**拒绝释放**:{@code NardisSignItem#handleUse} 第 0 步读 {@link #countTemporaryTotal(Player)},
     *        {@code >= MAX_TEMPORARY_CARDS} 即 actionbar 提示 + {@code fail}(**零消耗**);</li>
     *    <li>**发放夹取**:{@link #grantNardisPrivilege(Player)} 按
     *        {@code budget = MAX_TEMPORARY_CARDS − 当前张数} 夹取本轮发放张数
     *        ⇒ 总数**永不超过**本值(即使并发/异常路径绕过第 1 条也不会溢出)。</li>
     *  </ol> */
    public static final int MAX_TEMPORARY_CARDS = 9;

    private TemporaryCardUtil() {
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  标记读写
    // ══════════════════════════════════════════════════════════════════════════

    /** 该物品栈是否为临时牌(唯一的物品侧判据;光效/丢弃/容器拦截全部走这里) */
    public static boolean isTemporary(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return Boolean.TRUE.equals(ModDataComponents.TEMPORARY_CARD.get(stack));
    }

    /** 打上临时牌标记(幂等) */
    public static void mark(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ModDataComponents.TEMPORARY_CARD.set(stack, Boolean.TRUE);
    }

    /** 去掉临时牌标记(幂等) */
    public static void unmark(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ModDataComponents.TEMPORARY_CARD.remove(stack);
    }

    /**
     * 「到期刻」缺省值(= 不设限)。
     *
     * <p>旧存档里已经存在的临时牌没有这个戳 ⇒ 按"不设限"处理 —— 它们仍受全部**既有**收口约束
     * (非法位置立即销毁 / 玩家死亡 / 效果结束 ⇒ 整体清空),只是不额外走"到点自毁"。
     * 这是刻意的向后兼容取舍:不给老牌凭空定一个过去时刻,否则重登即被清。
     */
    public static final long NO_EXPIRY = 0L;

    /** 自毁巡查对**地面掉落物**的扫描半径(格):只扫玩家附近,避免做全维度实体查询 */
    private static final double DROP_SWEEP_RADIUS = 16.0D;

    /** 该栈的到期刻(绝对 gameTime;{@link #NO_EXPIRY} = 不设限) */
    public static long expiryOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return NO_EXPIRY;
        Long v = ModDataComponents.TEMPORARY_CARD_EXPIRES.get(stack);
        return v == null ? NO_EXPIRY : v;
    }

    /** 是否已过到期刻(无戳 = 不过期) */
    public static boolean isExpired(ItemStack stack, long now) {
        long expiry = expiryOf(stack);
        return expiry > NO_EXPIRY && now >= expiry;
    }

    /** 打上临时牌标记,并把到期刻写成 {@code expiresAt}(绝对 gameTime;{@code <= 0} 时只打标记) */
    public static void mark(ItemStack stack, long expiresAt) {
        if (stack == null || stack.isEmpty()) return;
        mark(stack);
        if (expiresAt > NO_EXPIRY) {
            ModDataComponents.TEMPORARY_CARD_EXPIRES.set(stack, expiresAt);
        }
    }

    /** 打上标记,并把到期刻对齐到「现在 + 效果剩余」(效果不在时按满时长兜底) */
    public static void mark(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return;
        mark(stack, player.level().getGameTime() + remainingTicks(player));
    }

    /** 女王特权的剩余 tick;效果不在时返回满时长({@link NardisPrivilegeEffect#DURATION_TICKS}) */
    private static int remainingTicks(Player player) {
        MobEffectInstance fx = player == null ? null : player.getEffect(ModEffects.NARDIS_PRIVILEGE.get());
        return fx != null ? Math.max(1, fx.getDuration()) : NardisPrivilegeEffect.DURATION_TICKS;
    }

    /**
     * 把**够得着的**临时牌的到期刻确定性对齐到「现在 + 效果剩余」,返回处理的张数(幂等)。
     *
     * <p><b>为什么需要对齐</b>:到期刻是「发放刻 + 3:00」的绝对时间,而效果时长只在玩家在线时流逝
     * (多人服务器上 `gameTime` 却照走)⇒ 不对齐的话,长时间离线后重登会把仍然有效的牌判成过期。
     * 对齐 = 以**效果实例的剩余时长**为唯一真值重新写戳,与
     * `BaseSignItem#realignLockEndToGateEffect` 是同一手法。
     *
     * <p><b>三个调用点</b>(缺一会出问题):
     * <ol>
     *   <li>发放(`grantRandom` 发牌时已写初值);</li>
     *   <li>**再次释放**(`NardisSignItem#handleUse` 重置效果时长之后)—— 需求「再次释放把有效期重置为
     *       3:00」对**已存在的牌**同样成立,不重写就会让旧牌先于效果到期;</li>
     *   <li>**玩家登录**(`PlayerLifecycleHandler`)—— 抵消离线墙钟漂移。</li>
     * </ol>
     *
     * <p>扫描面 = 当前容器菜单里**允许临时牌**的槽位 + 光标({@link #isPlayerOwnedOrPermissive} 判据);
     * 骰子已装配的牌**不在**此列(它们的临时性在 `AppliedStone` 里、由效果实例直接承载);
     * 非法位置的牌不对齐(交给 {@link #purgeOutOfPlace} 直接销毁)。
     * 只写组件不标脏槽位:该组件不上网,客户端看不到差异,不需要发包。
     */
    public static int realignExpiry(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        if (!player.hasEffect(ModEffects.NARDIS_PRIVILEGE.get())) return 0;        // 无效果 ⇒ 牌马上会被 tick 清掉,不对齐
        long deadline = player.level().getGameTime() + remainingTicks(player);
        int touched = 0;
        try {
            var menu = player.containerMenu;
            if (menu != null) {
                for (Slot slot : menu.slots) {
                    ItemStack stack = slot.getItem();
                    if (!isTemporary(stack) || !isPlayerOwnedOrPermissive(slot)) continue;
                    mark(stack, deadline);
                    touched++;
                }
                ItemStack carried = menu.getCarried();
                if (isTemporary(carried)) {
                    mark(carried, deadline);
                    touched++;
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice][TemporaryCard] 对齐临时牌到期刻失败", t);
        }
        return touched;
    }

    /**
     * **自毁巡查**(2026-09-24 用户裁决「为临时效果牌增加自毁机制」),返回移除张数(幂等)。
     *
     * <h2>为什么需要它(拦截在先、自毁兜底)</h2>
     * 临时牌的语义是「只能存在于玩家物品栏 0..35 / 副手 / 手中 / 本模组卡牌栏 / 骰子已装配」。
     * 槽位面已有两道守卫({@code SlotPlaceGuardMixin} / {@code ContainerMoveGuardMixin})与
     * 1.21.1 的栈级钩子,但**第三方容器的插入路径可以完全绕过槽位校验** ——
     * 最典型的是 AE2 的存储总线(直接写自己的存储实现)与机械动力的物品舱口/仓库(走
     * {@code IItemHandler#insertItem})。已实测:NeoForge 自己的 {@code ItemStackHandler#insertItem}
     * **只**问 {@code isItemValid}(缺省恒真),**不**问 {@code canFitInsideContainerItems}
     * ⇒ 这类容器**无法**用物品钩子拦住(只有 {@code ComponentItemHandler} 会问)。
     * 因此改为:拦不住就**让它自毁** —— 凡在允许位置之外被"看见"一次,立即销毁。
     *
     * <h2>两个巡查面</h2>
     * <ol>
     *   <li><b>当前打开的容器菜单的全部槽位</b>:允许位置({@link #isPlayerOwnedOrPermissive})之外的
     *       临时牌一律销毁 —— 覆盖箱子 / 末影箱 / 潜影盒 / 各类模组界面(玩家一打开就清);
     *       合法位置上的牌则按 {@link #isExpired} 收口(杀掉"存进容器过夜、事后取回"的旧牌);</li>
     *   <li><b>玩家附近({@value #DROP_SWEEP_RADIUS} 格)的地面掉落物</b>:临时牌不得落地 ⇒ 直接
     *       {@code discard}(兜住第三方 {@code player.drop} 与老版本残留)。</li>
     * </ol>
     *
     * <p>⚠️ **明确够不着的场景**:第三方存储内部(AE2 网络里的存储元件 / 未打开的仓库)不会被主动
     * 清空 —— 那里我们既扫不到也不该去扫。后果是它**可能占着对方的一个格子**,但**任何一次取出**
     * (无论落到玩家物品栏、外部槽位还是地面)都会在下一次巡查被销毁 ⇒ "永不消失"不再成立。
     *
     * <p>成本:由调用方每 20 tick 调一次;单次 = 遍历一个菜单的槽位(几十个)+ 一次 AABB 实体查询。
     */
    private static int purgeOutOfPlace(Player player, long now) {
        int removed = 0;
        try {
            var menu = player.containerMenu;
            if (menu != null) {
                for (Slot slot : menu.slots) {
                    if (slot.container == null) continue;         // 无容器槽位无法安全写入,跳过
                    ItemStack stack = slot.getItem();
                    if (!isTemporary(stack)) continue;
                    if (isPlayerOwnedOrPermissive(slot) && !isExpired(stack, now)) continue;
                    removed += stack.getCount();
                    slot.set(ItemStack.EMPTY);                    // Slot#set 内部已 setChanged
                }
                ItemStack carried = menu.getCarried();
                if (isTemporary(carried) && isExpired(carried, now)) {
                    removed += carried.getCount();
                    menu.setCarried(ItemStack.EMPTY);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice][TemporaryCard] 清理非法位置临时牌失败", t);
        }
        try {
            var level = player.level();
            var box = player.getBoundingBox().inflate(DROP_SWEEP_RADIUS);
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
                ItemStack stack = entity.getItem();
                if (!isTemporary(stack)) continue;
                removed += stack.getCount();
                entity.discard();
            }
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice][TemporaryCard] 清理地面临时牌失败", t);
        }
        if (removed > 0) {
            LOGGER.debug("[Astral Dice][TemporaryCard] 自毁巡查: player={} removed={}",
                    player.getName().getString(), removed);
        }
        return removed;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  物品级覆写判据(普通牌 CardItem 与效果牌 BaseEffectCardItem 共用同一份实现)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 「附魔光效」判据(唯一实现):临时牌常亮,其余原样交回原版判定(1.20.1 侧)。
     *
     * <p>牌有**两个根类**:战斗牌 {@link CardItem}(直接 {@code extends Item})与效果牌
     * {@link BaseEffectCardItem}({@code abstract extends Item})—— 两者没有共同父类,
     * 而「接口 default 方法被类方法优先规则压住」⇒ 无法靠接口给 {@code Item#isFoil} 提供默认实现,
     * 只能**每个根类各覆写一次**。覆写体只允许 `return TemporaryCardUtil.glint(stack, super.isFoil(stack));`
     * —— 判据只此一份,不允许在覆写里另写条件(否则会再次出现「普通牌有光效、效果牌没有」的漂移;
     * 临时牌池 {@link RandomCardHandler.CardCategory#ALL} **包含效果牌**,故两处缺一不可)。
     *
     * @param vanillaGlint 原版判定结果({@code super.isFoil(stack)});原版实现(1.20.1 实测
     *                     {@code Item.java:340} {@code return stack.isEnchanted();})无副作用,
     *                     故允许先行求值
     */
    public static boolean glint(ItemStack stack, boolean vanillaGlint) {
        return isTemporary(stack) || vanillaGlint;
    }

    // ⚠️ 1.21.1 另有一个**栈级**钩子 {@code Item#canFitInsideContainerItems(ItemStack)},
    //    潜影盒/收纳袋/组件容器四类入口都汇聚到它,故 1.21.1 侧的 TemporaryCardUtil 还有一份
    //    {@code fitsInsideContainer(stack, vanillaAllows)} 供两个牌根类覆写。
    //    1.20.1 **没有**栈级版本(实测 {@code Item.java:452} 只有类型级
    //    {@code canFitInsideContainerItems()},覆写它会把同 id 的**永久牌**一并挡掉),
    //    容器拦截全部由五个 mixin 按 {@link #isPlacementBlocked} 承担(见本类「容器拦截」段),
    //    因此这一侧**故意不提供**该判据,避免出现无人调用的死代码。

    // ══════════════════════════════════════════════════════════════════════════
    //  发放(主动技能与探针共用)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 发放最多 {@code maxCount} 张随机临时牌,返回**实际发放张数**。
     *
     * <p>规则(与需求「临时牌只能在物品栏和手中」一致):
     * <ol>
     *   <li>先算物品栏可用空槽数,最多发 {@code min(maxCount, 空槽数)} 张 —— **绝不落地**
     *       (满了就少发,不发到地上);</li>
     *   <li>每张走 {@link RandomCardHandler#randomCard}({@link RandomCardHandler.CardCategory#ALL})
     *       —— 专属牌由既有池构建逻辑自动排除,**不要**在这里另写专属牌过滤;</li>
     *   <li>先 {@link #mark} 再交给 {@link VitaminPillChipItem#giveCard} —— 后者是本模组**唯一的发牌漏斗**
     *       (维生素药丸治愈 + 教主立牌狐光计层都在里面),必须走它才能「激活所有获取卡牌的触发器」。
     *       ⚠️ 看板娘 mimi 的 +1 星币**刻意不随奖励发牌触发**(仅合成与看板娘主动返还两条路),
     *       不为此新增触发。</li>
     * </ol>
     */
    public static int grantRandom(Player player, int maxCount) {
        return grantRandom(player, RandomCardHandler.CardCategory.ALL, maxCount);
    }

    /**
     * 发放最多 {@code maxCount} 张**指定类别**的随机临时牌,返回**实际发放张数**。
     *
     * <p>专属牌由既有池构建逻辑({@code RandomCardHandler#getCardPool} 末尾的
     * {@code items.removeIf(... EXCLUSIVE_CARDS ...)})自动排除,**不要**在这里另写专属牌过滤。
     */
    public static int grantRandom(Player player, RandomCardHandler.CardCategory category, int maxCount) {
        if (player == null || player.level().isClientSide()) return 0;
        if (maxCount <= 0) return 0;
        int toGrant = Math.min(maxCount, countFreeSlots(player));
        int granted = 0;
        for (int i = 0; i < toGrant; i++) {
            ItemStack card = RandomCardHandler.randomCard(category);
            if (card.isEmpty()) break;
            // 到期刻初值 = 现在 + 满时长;释放流程随后会用 realignExpiry 按效果实际剩余再写一次
            mark(card, player.level().getGameTime()
                    + com.merlinkitsune.astral_dice.effect.NardisPrivilegeEffect.DURATION_TICKS);
            VitaminPillChipItem.giveCard(player, card);
            granted++;
        }
        return granted;
    }

    /**
     * 女王特权主动的发放(2026-09-27 用户裁决①;上限口径见裁决⑦):**先 2 张战斗牌,再 1 张效果牌**
     * (战斗牌优先),返回实际发放张数。
     *
     * <p><b>上限夹取(2026-09-27 用户裁决⑦)</b>:本轮预算
     * {@code budget = MAX_TEMPORARY_CARDS − countTemporaryTotal(player)},实际发放 {@code ≤ budget}
     * ⇒ 与既有临时牌叠加后总数**永不超过 9 张**。例:当前 7 张 ⇒ 最多补 2 张;当前 8 张 ⇒ 最多补 1 张;
     * 当前 ≥ 9 张 ⇒ 一张都不发({@code NardisSignItem#handleUse} 第 0 步已先按同一判据拒绝整次释放)。
     *
     * <p>顺序与"只剩 N 格"的行为(硬约束,两线逐字一致):
     * <ol>
     *   <li>先发 {@code min(}{@value #GRANT_BATTLE_COUNT}{@code , budget)} 张战斗牌(池 = {@code CardCategory.BATTLE},
     *       攻击 + 防御混合随机,两张各自独立 ⇒ 允许两张同类);</li>
     *   <li>再发 {@code min(}{@value #GRANT_EFFECT_COUNT}{@code , budget − 实际已发战斗牌)} 张效果牌
     *       (池 = {@code CardCategory.EFFECT});② 的可用格数是**调用时重新统计**的(战斗牌已先占格)
     *       ⇒ 只剩 1 格时只发 1 张战斗牌、效果牌因无空格而不发;0 格时一张都不发 —— **绝不落地**。
     *       正常情况下这条"少发"路径不可达
     *       ({@code NardisSignItem#handleUse} 第 1 步的安全门已要求空槽 ≥ {@value #MIN_FREE_SLOTS_TO_CAST}),
     *       它只是安全网(2026-09-27 用户裁决⑦第 5 条:保留不删)。</li>
     * </ol>
     *
     * <p>两张战斗牌若随机到**同一张**牌,会合并进同一个空格 / 既有临时牌堆(卡牌 {@code stacksTo(64)},
     * 与临时标记 NBT 相同 ⇒ {@code Inventory#add} 走合并分支)—— 不影响"发满 3 张"的语义。
     *
     * <p>⚠️ 本方法**不清空**任何既有临时牌(2026-09-27 用户裁决:叠加补给,取消「先清空再发」)。
     */
    public static int grantNardisPrivilege(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int budget = MAX_TEMPORARY_CARDS - countTemporaryTotal(player);
        if (budget <= 0) return 0;                    // 已达上限:一张都不发(与 handleUse 的拒绝同一判据)
        int granted = grantRandom(player, RandomCardHandler.CardCategory.BATTLE,
                Math.min(GRANT_BATTLE_COUNT, budget));
        int effectBudget = Math.min(GRANT_EFFECT_COUNT, Math.max(0, budget - granted));
        granted += grantRandom(player, RandomCardHandler.CardCategory.EFFECT, effectBudget);
        return granted;
    }

    /** 物品栏(主物品栏 0..35)可用空槽数;{@code giveCard} 的入包路径只使用这一段 */
    public static int countFreeSlots(Player player) {
        if (player == null) return 0;
        int free = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) free++;
        }
        return free;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  计数(读时现算,不落附件)
    // ══════════════════════════════════════════════════════════════════════════

    /** 当前玩家**身上**(主物品栏 0..35 + 副手;主手是物品栏中的一格)带临时标记的卡牌总张数 */
    public static int countTemporary(Player player) {
        if (player == null) return 0;
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (isTemporary(stack)) count += stack.getCount();
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isTemporary(stack)) count += stack.getCount();
        }
        return count;
    }

    /** 装备中骰子的 {@code weapon_enhancement.appliedStones} 里 {@code temporary()==true} 的卡牌张数 */
    public static int countTemporaryEquipped(Player player) {
        if (player == null) return 0;
        ItemStack dice = findEquippedDice(player);
        if (dice.isEmpty()) return 0;
        WeaponEnhancement enh = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, WeaponEnhancement.EMPTY);
        int count = 0;
        for (AppliedStone stone : enh.appliedStones()) {
            if (stone != null && stone.temporary()) count++;
        }
        return count;
    }

    /**
     * 当前临时牌**总张数**(上限判据):{@link #countTemporary(Player)} + {@link #countTemporaryEquipped(Player)}
     * —— 与玩家级 tick 自检的"还有没有牌"用的是**同一对**计数方法,口径完全一致。
     */
    public static int countTemporaryTotal(Player player) {
        if (player == null) return 0;
        return countTemporary(player) + countTemporaryEquipped(player);
    }

    /** 玩家当前佩戴的骰子(骰子饰品槽只有 1 个,不存在多骰子) */
    public static ItemStack findEquippedDice(Player player) {
        if (player == null) return ItemStack.EMPTY;
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return ItemStack.EMPTY;
        var result = curios.get().findFirstCurio(DiceCurioItem::isDiceItem);
        return result.isPresent() ? result.get().stack() : ItemStack.EMPTY;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  容器拦截(全部 mixin 共用的唯一判据)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 放置拦截判据(唯一实现):临时牌 **且** 目标槽不是「玩家自己的槽 / 本模组卡牌栏槽」⇒ 拦截。
     *
     * <p>{@code SlotPlaceGuardMixin}(目标 {@code Slot#mayPlace})、{@code ContainerMoveGuardMixin}
     * (目标 {@code AbstractContainerMenu#moveItemStackTo})以及 1.20.1 专有的三个入口守卫
     * ({@code ShulkerBoxSlotGuardMixin} / {@code ShulkerBoxBlockEntityGuardMixin} /
     * {@code BundleInsertGuardMixin})都必须调用本方法,**不得**在 mixin 里另写一份判据。</p>
     *
     * <p>为什么必须有两个通用 mixin:{@code moveItemStackTo} 先尝试「与同类栈合并」,该分支
     * (1.20.1 实测 {@code AbstractContainerMenu.java:631-687})**完全不查 {@code mayPlace}** ⇒ 只改
     * {@code mayPlace} 挡不住「Shift 点击把临时牌并进箱子里的同类栈」。
     */
    public static boolean isPlacementBlocked(ItemStack stack, boolean targetIsPlayerOwnedSlot) {
        return isTemporary(stack) && !targetIsPlayerOwnedSlot;
    }

    /**
     * 该槽是否「临时牌可以进」的槽:玩家自己的物品栏槽,或本模组卡牌栏槽
     * (后者实现 {@link TemporaryCardPermissiveSlot} 标记接口 —— **不要**用
     * {@code instanceof SimpleContainer} 这种过宽判据,那会误放行其它用 {@code SimpleContainer} 的容器)。
     */
    public static boolean isPlayerOwnedOrPermissive(Slot slot) {
        if (slot == null) return false;
        if (slot instanceof TemporaryCardPermissiveSlot) return true;
        return slot.container instanceof net.minecraft.world.entity.player.Inventory;
    }

    /** 槽区间 {@code [startIndex, endIndex)} 是否**全部**为可放临时牌的槽(区间取空/越界时按 startIndex 单点判定) */
    public static boolean isRangePlayerOwnedOrPermissive(List<Slot> slots, int startIndex, int endIndex) {
        if (slots == null) return false;
        int from = Math.max(0, Math.min(startIndex, endIndex));
        int to = Math.min(slots.size(), Math.max(startIndex, endIndex));
        if (to <= from) {
            // 空区间:没有目标槽,不构成"移入其它容器",按放行处理(实际也不会写入任何槽)
            return true;
        }
        for (int i = from; i < to; i++) {
            if (!isPlayerOwnedOrPermissive(slots.get(i))) return false;
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  清理
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 清空该玩家的**全部**临时牌(幂等),返回移除的总张数。
     *
     * <p>清理范围(缺一不可):主物品栏 0..35、副手、**骰子
     * {@code weapon_enhancement.appliedStones} 中 {@code temporary()==true} 的项**
     * (过滤后重建 record,并按被移除卡的费用重算 {@code usedCost}/{@code usedDefenseCost},
     * {@code maxCost} 不变;费用口径 = {@code CardRegistry.cost(type, player)},与
     * {@code CardInventoryMenu#saveToDice} 的重算路径同源)。
     *
     * <p>全程 try/catch(Throwable) 只记日志:本方法会被 tick 自检与死亡流程调用,
     * 不允许把异常抛进那些路径。
     */
    public static int purgeAll(Player player) {
        if (player == null || player.level().isClientSide()) return 0;
        int removed = 0;
        try {
            removed += purgeInventory(player);
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice][TemporaryCard] 清空物品栏临时牌失败", t);
        }
        try {
            removed += purgeEquipped(player);
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice][TemporaryCard] 清空骰子装配栏临时牌失败", t);
        }
        try {
            removed += purgeOpenMenuAndCursor(player);
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice][TemporaryCard] 清空打开的容器/光标临时牌失败", t);
        }
        return removed;
    }

    /**
     * 玩家**当前打开的容器**里挂着的临时牌 + **鼠标光标**上那一张(2026-09-24 补,死亡清理范围)。
     *
     * <p>为什么必须补(两条「跨死亡存活」的路径):
     * <ol>
     *   <li>本模组卡牌栏菜单({@code screen/CardInventoryMenu})把自己那份 {@code cardContainer}
     *       当作「骰子卡牌栏的真值」,而**牌进了这个容器就不在物品栏里** ⇒ {@link #purgeInventory}
     *       扫不到;更糟的是该菜单关闭时({@code removed()})会把容器内容**写回骰子**
     *       ({@code saveToDice()} 会透传 {@code temporary} 标记)。死亡流程里 {@code purgeAll} 跑在
     *       {@code dropAllDeathLoot} **之前**(见 {@code event/PlayerLifecycleHandler} 的时序注释),
     *       而容器内容根本不在掉落清单里 ⇒ 死亡时若卡牌栏还开着,这些临时牌会**跨死亡存活**
     *       并借「关闭菜单」回到骰子里 —— 正是「死亡应强制清空全部临时牌」的反例;</li>
     *   <li>光标栈({@code containerMenu#getCarried()})既不在物品栏也不在骰子里 ⇒ 同样扫不到;
     *       容器关闭时原版会把光标栈退回物品栏 ⇒ 又是一条存活路径
     *       (AGENTS 里登记的 F4 早已指出「光标不计入也不被 purgeAll 清理」)。</li>
     * </ol>
     * 卡牌栏容器的清理委托给它自己的 {@code purgeTemporaryCards()}(判据同源);
     * 光标只清空**临时牌**那一栈,不影响任何其它物品。
     */
    private static int purgeOpenMenuAndCursor(Player player) {
        var menu = player.containerMenu;
        if (menu == null) return 0;
        int removed = 0;
        if (menu instanceof com.merlinkitsune.astral_dice.screen.CardInventoryMenu cardMenu) {
            removed += cardMenu.purgeTemporaryCards();
        }
        ItemStack carried = menu.getCarried();
        if (isTemporary(carried)) {
            removed += carried.getCount();
            menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
        }
        return removed;
    }

    // 主物品栏 0..35 + 副手
    private static int purgeInventory(Player player) {
        int removed = 0;
        List<ItemStack> items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!isTemporary(stack)) continue;
            removed += stack.getCount();
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        List<ItemStack> offhand = player.getInventory().offhand;
        for (int i = 0; i < offhand.size(); i++) {
            ItemStack stack = offhand.get(i);
            if (!isTemporary(stack)) continue;
            removed += stack.getCount();
            offhand.set(i, ItemStack.EMPTY);
        }
        return removed;
    }

    // 骰子已装配的临时牌:过滤 + 按被移除卡的费用重算 usedCost/usedDefenseCost(maxCost 不变)
    private static int purgeEquipped(Player player) {
        ItemStack dice = findEquippedDice(player);
        if (dice.isEmpty()) return 0;
        WeaponEnhancement enh = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, WeaponEnhancement.EMPTY);
        List<AppliedStone> stones = enh.appliedStones();
        if (stones == null || stones.isEmpty()) return 0;
        List<AppliedStone> kept = new ArrayList<>(stones.size());
        int freedAttackCost = 0;
        int freedDefenseCost = 0;
        int removed = 0;
        for (AppliedStone stone : stones) {
            if (stone == null || !stone.temporary()) {
                kept.add(stone);
                continue;
            }
            int cost = CardRegistry.cost(stone.type(), player);
            if (CardRegistry.isDefense(stone.type())) {
                freedDefenseCost += cost;
            } else {
                freedAttackCost += cost;
            }
            removed++;
        }
        if (removed <= 0) return 0;
        ModDataComponents.WEAPON_ENHANCEMENT.set(dice, new WeaponEnhancement(
                Math.max(0, enh.usedCost() - freedAttackCost),
                enh.maxCost(),
                Math.max(0, enh.usedDefenseCost() - freedDefenseCost),
                enh.maxDefenseCost(),
                enh.starLevel(),
                kept));
        return removed;
    }

    /**
     * 玩家级 tick 自检(**幂等**,由 {@code event/PlayerTickEvents} 在 {@code % 20} 早退**之前**调用)。
     *
     * <p>真值 = 原生效果实例 {@link ModEffects#NARDIS_PRIVILEGE};本方法只剩**一个**收口条件:
     * <ol>
     *   <li>「玩家身上/骰子里还有临时牌,但该玩家**没有**女王特权效果」⇒ 清空全部临时牌。
     *       效果自然到期、{@code /effect clear} 移除、离线到期后重登、异常残留**都会**走到这一条,
     *       因此不需要在任何其它地方补第二套清理逻辑(死亡路径另有一次显式清理,两者都幂等)。</li>
     * </ol>
     *
     * <p>⚠️ **反向分支已删除(2026-09-27 用户裁决)**:上一版为配合「冻结(使用中)」态加的
     * 「效果还在、但一张临时牌都没有 ⇒ 立刻移除效果」(连同其"施加当拍"防抖)已全部删除 ——
     * 牌被用光**不再**提前结束效果;效果只由 3:00 自然到期 / 外力移除 / 玩家死亡结束,
     * 届时本方法的第 1 条清空剩余临时牌。
     *
     * <p>⚠️ 1.20.1 的 {@code PlayerTickEvent} 每 tick 派发 START+END **两次** ⇒ 本方法会被调两遍。
     * 幂等性由「无效果 + 无牌时以 {@code return} 早退 / 清空本身幂等」保证:第二遍在已清空后自然早退。
     */
    public static void tick(Player player) {
        if (player == null || player.level().isClientSide()) return;
        try {
            // 自毁巡查(2026-09-24 用户裁决「为临时效果牌增加自毁机制」):临时牌只允许存在于
            // 「玩家物品栏 0..35 / 副手 / 手中 / 本模组卡牌栏 / 骰子已装配」五处 ——
            // 第三方容器(不经槽位校验的插入路径)拦不住,就改成"看见即销毁";另杀过期的旧牌。
            // 成本受控:每 20 tick 一次;下面"无效果 ⇒ 清空"那一条仍需**每 tick** 判定,不动。
            if (player.tickCount % 20 == 0) {
                purgeOutOfPlace(player, player.level().getGameTime());
            }
            MobEffectInstance fx = player.getEffect(ModEffects.NARDIS_PRIVILEGE.get());
            if (fx == null) {
                boolean hasCards = countTemporary(player) > 0 || countTemporaryEquipped(player) > 0;
                if (!hasCards) return;                       // 无效果 + 无牌:无事可做
                int removed = purgeAll(player);
                if (removed > 0) {
                    LOGGER.debug("[Astral Dice][TemporaryCard] 效果已不在,自检清空临时牌: player={} removed={}",
                            player.getName().getString(), removed);
                }
            }
            // 效果还在 ⇒ 什么都不做(剩余时长由 EffectTimerGuard 与效果实例本身维持)
        } catch (Throwable t) {
            LOGGER.error("[Astral Dice][TemporaryCard] tick 自检失败", t);
        }
    }
}
