package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.starenginelib.event.EventTargetCollector;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;

/**
 * 银行卡-用不完筹码:装备时获得 3 点星光(一次性);
 * 每次骰神赐福效果结束后,使自身及友方玩家(团队内成员;未加入任何队伍时为全服在线玩家)获得 3 星币
 * (由 {@link #onBlessingEnd} 在赐福结束时调用)。
 */
public class BankCardUnlimitedChipItem extends BaseChipItem {
    /** 赐福结束后发放的星币数量 */
    public static final int REWARD_COINS = 3;

    public BankCardUnlimitedChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴本筹码
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.BANK_CARD_UNLIMITED.get())).isPresent();
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // ⚠️ 第 3 参 stack 才是刚装上的那件(恒非空);旧代码把空槽守卫写在第 3 参上 ⇒ 恒 return、星光从未发放。
        // 一次性发放改由「装备会话闸门」判定:Curios 登录 / 重生 / 切维度后会重放 onEquip
        // (重放时 prevStack 恰好是空栈,空槽守卫拦不住),没有闸门就会每次登录重发 3 层
        // ⇒ 见 StarLightManager#claimEquipGrant。
        // 装备时星光 +3(上限由 StarLightManager 统一管理);走统一发放入口 —— 把**实际**抬升量记账,
        // 卸除时按账本严格扣回(见 StarLightManager#revokeStarlightOnUnequip)。
        StarLightManager.grantStarlightOnEquip(player, StarLightManager.GRANT_BIT_BANK_CARD_UNLIMITED, 3);
    }

    // 卸下筹码:释放发放闸门 ⇒ 再次装备可再发
    @Override
    protected void onChipUnequip(Player player, ItemStack stack) {
        // 「卸除即扣除」全筹码底线:按账本把装备时**实际**获得的星光扣回(顺带释放发放闸门)。
        StarLightManager.revokeStarlightOnUnequip(player, StarLightManager.GRANT_BIT_BANK_CARD_UNLIMITED);
    }

    /**
     * 骰神赐福结束时调用:使自身及友方玩家获得 3 星币。
     * 友方 = 已加入队伍时同队在线玩家(MC/FTB/OPAC);未加入任何队伍时 = 全服在线玩家。
     * 死亡清场时(玩家已死亡)不发放。
     */
    public static void onBlessingEnd(Player player) {
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (player.isDeadOrDying()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        java.util.List<Player> allies = EventTargetCollector.collectTeamPlayers(player);
        for (ServerPlayer sp : serverLevel.players()) {
            if (sp == player || allies.contains(sp)) {
                giveCoins(sp);
            }
        }
    }

    private static void giveCoins(Player player) {
        ResourceConversion.giveItem(player, new ItemStack(ModItems.STAR_COIN.get(), REWARD_COINS));
    }
}
