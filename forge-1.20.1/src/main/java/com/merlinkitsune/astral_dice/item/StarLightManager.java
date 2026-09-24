package com.merlinkitsune.astral_dice.item;

import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import net.minecraft.world.entity.player.Player;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import top.theillusivec4.curios.api.CuriosApi;
import com.merlinkitsune.astral_dice.item.chip.BankCardChipItem;

/**
 * "星光"管理器(玩家级共享资源,与具体饰品解耦)。
 * 数据存储于玩家 attachment(PLAYER_STARLIGHT),本类统一提供获取/增加/设置与上限逻辑。
 *
 * <p>星光为固定点数(不随时间衰减,无计数器),只有增加与减少。
 * 与治愈流派一致,星光具有<b>基础值(下限)</b>:
 * <ul>
 *   <li>基础值默认 0,可由未来"固定增加星光"的筹码在装备期间提供(预留 {@link #getBasePoints} 接入点);</li>
 *   <li>星光被消耗并低于基础值时,自动补充回基础值。</li>
 * </ul>
 * 获取来源:经商立牌被动/赐福加成、手电筒筹码攻击加成、八面骰累计、看板娘立牌兑换等。
 */
public final class StarLightManager {
    private StarLightManager() {
    }

    /** 当前星光点数 */
    public static int get(Player player) {
        return ModAttachments.getStarlight(player);
    }

    /** 星光点数上限(配置控制) */
    public static int getCap() {
        return GameplayConstants.MAX_STARLIGHT;
    }

    /**
     * 星光基础值(下限),默认 0。
     * 由"固定增加星光"的筹码在装备期间提供常驻基础值(卸下自动回落,与治愈基础点模式一致):
     * - 银行卡-余额少:+4;
     * - 银行卡-余额多:+7。
     */
    public static int getBasePoints(Player player) {
        var curios = CuriosCompat.getCuriosInventory(player);
        if (curios.isEmpty()) return 0;
        var inventory = curios.get();
        int base = 0;
        if (inventory.findFirstCurio(s -> s.is(ModItems.BANK_CARD_LOW.get())).isPresent()) {
            base += BankCardChipItem.BASE_LOW;
        }
        if (inventory.findFirstCurio(s -> s.is(ModItems.BANK_CARD_HIGH.get())).isPresent()) {
            base += BankCardChipItem.BASE_HIGH;
        }
        return base;
    }

    /** 「装备时获得 N 层星光」类筹码/立牌的发放闸门位(见 {@link #claimEquipGrant});每位一枚。 */
    public static final int GRANT_BIT_ATM = 1;
    public static final int GRANT_BIT_STAR_COIN_HAMMER = 1 << 1;
    public static final int GRANT_BIT_BANK_CARD_UNLIMITED = 1 << 2;
    public static final int GRANT_BIT_FEN_SIGN = 1 << 3;
    /** 银行卡-余额少(下限 4)/ 余额多(下限 7):这两个位只用于「卸除即扣除」记账(FEN_SIGN 位非星光发放)。 */
    public static final int GRANT_BIT_BANK_CARD_LOW = 1 << 4;
    public static final int GRANT_BIT_BANK_CARD_HIGH = 1 << 5;

    /**
     * 申领「本次装备会话」的一次性星光发放:未申领过则置位并返回 true,已申领过返回 false。
     * <p>⚠️ **为什么必须要有闸门**:Curios 只持久化 {@code stacks}、**不持久化 {@code previousStacks}**
     * ⇒ 登录 / 重生 / 切维度后首 tick 的 {@code prevStack} 恒为空栈,而槽里有物品 ⇒
     * {@code !ItemStack.matches(stack, prevStack)} 成立,Curios 把这次"差异"当作一次装备变化并
     * **重放 {@code onEquip}**。所以"空槽守卫"({@code prevStack.isEmpty()})根本挡不住重放
     * (重放时它恰好就是空栈);只有玩家级、持久化的闸门能把"真的新装上"与"登录重放"区分开。
     * <p>装备时申领、卸下时由 {@link #releaseEquipGrant} 释放 ⇒ 再次装备可以再发。
     */
    public static boolean claimEquipGrant(Player player, int bit) {
        int flags = ModAttachments.getStarlightEquipGrantFlags(player);
        if ((flags & bit) != 0) return false;
        ModAttachments.setStarlightEquipGrantFlags(player, flags | bit);
        return true;
    }

    /** 卸下筹码/立牌时释放闸门位(与 {@link #claimEquipGrant} 配对)。 */
    public static void releaseEquipGrant(Player player, int bit) {
        int flags = ModAttachments.getStarlightEquipGrantFlags(player);
        if ((flags & bit) != 0) {
            ModAttachments.setStarlightEquipGrantFlags(player, flags & ~bit);
        }
    }

    /** 账本每位占 4 bit(名义值最大 7,4 bit 足够)。 */
    private static final int GRANT_AMOUNT_BITS = 4;

    private static int grantAmountShift(int bit) {
        return Integer.numberOfTrailingZeros(bit) * GRANT_AMOUNT_BITS;
    }

    /** 读出某位「本次装备**实际**获得的星光量」(0 = 无待扣回的额度)。 */
    public static int grantedStarlight(Player player, int bit) {
        return (ModAttachments.getStarlightEquipGrantAmounts(player) >>> grantAmountShift(bit)) & 0xF;
    }

    private static void setGrantedStarlight(Player player, int bit, int amount) {
        int shift = grantAmountShift(bit);
        int clamped = Math.max(0, Math.min(0xF, amount));
        int packed = ModAttachments.getStarlightEquipGrantAmounts(player);
        packed = (packed & ~(0xF << shift)) | (clamped << shift);
        ModAttachments.setStarlightEquipGrantAmounts(player, packed);
    }

    /**
     * 「装备时获得 N 层星光」类筹码的**统一发放入口**:申领装备会话闸门 → 增加 → 把**实际**抬升量记账。
     * <p>已申领过(重复 {@code onEquip} / 登录重放)时**既不发放、也不覆盖账本** ⇒ 与卸除时的扣回严格一一对应。
     * @return 本次实际获得的星光量(上限已满时为 0)
     */
    public static int grantStarlightOnEquip(Player player, int bit, int nominal) {
        if (!claimEquipGrant(player, bit)) return 0;
        int before = get(player);
        add(player, nominal);
        int granted = get(player) - before;
        setGrantedStarlight(player, bit, granted);
        return granted;
    }

    /**
     * 银行卡等「装备期间提供星光下限」型筹码的发放入口:把当前值抬到下限,并按**实际**抬升量记账。
     */
    public static int grantStarlightFloorOnEquip(Player player, int bit) {
        if (!claimEquipGrant(player, bit)) return 0;
        int before = get(player);
        set(player, before);
        int granted = get(player) - before;
        setGrantedStarlight(player, bit, granted);
        return granted;
    }

    /**
     * 卸除时的**严格扣除**(「卸除即扣除」全筹码底线):按账本把本次装备实际获得的星光扣回,并释放闸门。
     * <p>⚠️ **必须按账本扣、不能按名义值扣**:星光已到上限时装备一点没涨,照名义值扣就是扣玩家自己攒的星光。
     * <p>⚠️ **直接写附件、不走 {@link #set}**:扣回发生在 Curios 的卸下回调里,此刻槽位状态未必已更新,
     * 走 {@code set} 的下限(基础值)会被尚未移除的银行卡重新抬回去 ⇒ 扣不动(银行卡永不掉星光的根因之一)。
     */
    public static void revokeStarlightOnUnequip(Player player, int bit) {
        int granted = grantedStarlight(player, bit);
        setGrantedStarlight(player, bit, 0);
        releaseEquipGrant(player, bit);
        if (granted <= 0) return;
        ModAttachments.setStarlight(player, Math.max(0, get(player) - granted));
    }

    public static void set(Player player, int value) {
        int base = getBasePoints(player);
        // 不低于基础值(下限),不高于上限
        ModAttachments.setStarlight(player, Math.max(base, Math.min(value, getCap())));
    }

    // 增加星光(自动限制在上限内),返回增加后的值
    public static int add(Player player, int amount) {
        int next = Math.min(get(player) + amount, getCap());
        set(player, next);
        return next;
    }

    // 消耗星光(不可为负),返回实际消耗的量;消耗后若低于基础值则自动补充回基础值
    public static int spend(Player player, int amount) {
        int current = get(player);
        int spent = Math.min(current, amount);
        set(player, current - spent);
        return spent;
    }
}
