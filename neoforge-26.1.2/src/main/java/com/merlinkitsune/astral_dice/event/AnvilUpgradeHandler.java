package com.merlinkitsune.astral_dice.event;


import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starengine.component.GameplayConstants;
import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.component.WeaponEnhancement;
import com.merlinkitsune.astral_dice.network.DamageNumberPayload;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.item.sign.ParunanSignItem;
import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.item.sign.BonnieSignItem;
import com.merlinkitsune.starengine.item.BossEntityUtil;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.item.card.ExclusiveCardUtil;
import com.merlinkitsune.astral_dice.item.sign.HaiqingSignItem;
import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.InvestigationEventUtil;
import com.merlinkitsune.astral_dice.item.MarkManager;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.item.sign.MisakiSignItem;
import com.merlinkitsune.astral_dice.item.DiceStarUpgrade;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.shop.ChipShopCatalog;
import com.merlinkitsune.astral_dice.shop.ChipShopState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class AnvilUpgradeHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnvilUpgradeHandler.class);

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (!DiceStarUpgrade.isDice(left)) return;
        if (!right.is(ModItems.STAR_COIN.get())) return;
        WeaponEnhancement enhancement = left.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
        int req = DiceStarUpgrade.starCoinCost(enhancement.starLevel());
        if (req < 0) return;
        if (right.getCount() < req) return;
        // 产出形态 / 星币成本 / 经验等级全部集中在 item.DiceStarUpgrade(唯一事实源)。
        // 铁砧任何模式都只星级 +1、卡牌随骰子保留;实验开关只把经验改为按输入骰子的分级标签取。
        ItemStack output = DiceStarUpgrade.resultFor(left);
        if (output.isEmpty()) return;
        event.setOutput(output);
        event.setMaterialCost(req);
        event.setXpCost(DiceStarUpgrade.xpCostFor(left));
    }

    /**
     * 从铁砧**取走**升星成品时才授予 1 次筹码商店刷新（2026-10-24 用户裁决）。
     *
     * <p><b>为什么必须挂在「取走」而不是预览</b>：{@link AnvilUpdateEvent} 在每次点击/改名/悬停重算时
     * 都会触发（装备查询也可能间接触发），只有 {@link AnvilCraftEvent.Post} 对应玩家真的把成品从输出槽拿走
     * 这一次。若挂在预览上，反复点铁砧、换装备或复制高星骰子都能白刷次数。
     *
     * <p><b>校验</b>（任一不满足即不授予）：服务端玩家；左槽与产物是**同一种**本模组骰子（`output.is(left.getItem())`）、
     * 右槽是星币；星币数量达到该星阶成本；输入星阶在可升范围（0..2★）；产物星阶恰好比输入 +1。
     *
     * <p><b>授予语义</b> = {@link ChipShopState#withRefreshGranted()}：在存档现有次数上 +1 并封顶
     * {@value ChipShopCatalog#REFRESH_MAX}，不重置、不清零（已有存档次数照旧保留）。
     *
     * <p>实验开关只影响经验等级；铁砧任何模式都要求产物星阶恰好比输入 +1，卡牌随骰子保留。
     */
    @SubscribeEvent
    public static void onAnvilRepair(AnvilCraftEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (!DiceStarUpgrade.isDice(left)) {
            return;
        }
        if (!right.is(ModItems.STAR_COIN.get())) {
            return;
        }
        WeaponEnhancement input = left.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
        // shift-click 时事件 output 可能已被搬空,必须以输入重算产物(与星愿铁砧共用 DiceStarUpgrade)
        ItemStack expected = DiceStarUpgrade.resultFor(left);
        if (expected.isEmpty()) {
            return;
        }
        WeaponEnhancement upgraded = expected.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), WeaponEnhancement.EMPTY);
        int required = DiceStarUpgrade.starCoinCost(input.starLevel());
        if (required < 0) {
            return;
        }
        if (right.getCount() < required) {
            return;
        }
        // 铁砧只做星级 +1；实验性重置已移到骰子升阶合成，不在铁砧发生。
        if (upgraded.starLevel() != input.starLevel() + 1) {
            return;
        }
        ChipShopState next = ChipShopState.get(player).withRefreshGranted();
        ChipShopState.store(player, next);
        LOGGER.info("[astral_dice] CHIP_SHOP_REFRESH_GRANTED player={} star={}->{} coins={} refreshes={}/{}",
                player.getGameProfile().name(), input.starLevel(), upgraded.starLevel(), right.getCount(),
                next.refreshesLeft(), ChipShopCatalog.REFRESH_MAX);
    }

    // isDice(13 种骰子白名单)与 starCoinCost(0★ 15 / 1★ 20 / 2★ 25,不可升 -1)已下沉到
    // item.DiceStarUpgrade —— 预览(onAnvilUpdate)、授予(onAnvilRepair)与星愿铁砧菜单必须共用
    // 同一份数值,不得在本类里再留第二套实现。

    // ============ 立牌 tooltip 统一格式辅助 ============
    // 格式模板:
    //   <按键提示>
    //
    //   主动技能（技能名）:
    //   <标准项>
    //   <带子项>:
    //   - <子项>
    //   被动技能（技能名）:
    //   <标准项>
    //   <带子项>:
    //   - <子项>
    //
    //   <备注信息(紫色,无符号)>
    //
    //   <立牌计数器>
    // 颜色约定:见 ModTooltipHandler 类头「物品 tooltip 统一染色规则」
    //   (数值=黄 §e、时间=蓝 §9、效果条目「名 (时间)」整段蓝 §9;§c 红色语义保留、§r/§f 例外)

    // 主动技能按键提示:置于 tooltip 最上方独立一行,并在末尾追加一个空行
}
