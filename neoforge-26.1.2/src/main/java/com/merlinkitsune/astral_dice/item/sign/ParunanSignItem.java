package com.merlinkitsune.astral_dice.item.sign;

import top.theillusivec4.curios.api.SlotContext;

import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.CurioSlotUtil;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.item.StarLightManager;
import com.merlinkitsune.astral_dice.resource.ResourceConversion;
import java.util.concurrent.ThreadLocalRandom;
import com.merlinkitsune.astral_dice.shop.CardShopOpener;
import com.merlinkitsune.starengine.component.GameplayConstants;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 经商立牌(parunan,史诗)。
 *
 * <h3>主动「网络购物」</h3>
 * 右键 / 立牌栏按键:**远程打开卡牌商店**界面(购买卡牌、星光兑换星币、向其他在线玩家转账星币
 * 全部在界面内完成),本身不施加任何效果、不消耗星光。玩家级冷却 {@value #ACTIVE_COOLDOWN_SECONDS} 秒
 * ——与其余立牌的冷却链同款(仅充能上限封顶)。
 *
 * <h3>被动「传奇商人」(2026-10-08 用户裁决,整条重写)</h3>
 * 五条内容**全部结算在** {@link com.merlinkitsune.astral_dice.shop.CardShopMenu}:
 * <ol>
 *   <li>每次进入商店时获得 1 星币(**仅佩戴者本人**);</li>
 *   <li>自身与团队成员的商店中可购买战斗牌数量 +1(**全队光环**);</li>
 *   <li>自身与团队成员进入商店后,每张牌有 10% 概率降至 1 星币(**全队光环**);</li>
 *   <li>转账后使当前商店内第一张未降价卡牌降至 1 星币(**仅佩戴者本人**);</li>
 *   <li>星光兑换额外获得 50% 收入(**仅佩戴者本人**;截断后不足 1 枚时至少 +1)。</li>
 * </ol>
 *
 * <p>⚠️ 旧被动「每 60 秒 +1 星光 / 触发骰神赐福时获得首次骰点 ×2 星光」与
 * {@code gainStarlightOnBlessing} 已按同一次用户裁决**整条移除**,本类不再产出星光。
 *
 * <p>沿革:2026-10-06 用户裁决把原主动「星光兑换 + 三选一随机增益」整体迁入卡牌商店
 * (随机增益与锁定(生效中)态一并取消);2026-10-08 再按新裁决重写主动名与整条被动。
 */
public class ParunanSignItem extends BaseSignItem {

    public ParunanSignItem(Properties properties) {
        super(properties);
    }

    protected void onCurioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) return;
        long gameTime = player.level().getGameTime();
        if (gameTime % (GameplayConstants.PARUNAN_PASSIVE_INTERVAL_SECONDS * 20L) == 0) {
            // 被动:每 N 秒星光 +1(上限由 StarLightManager 统一管理)
            StarLightManager.add(player, 1);
        }
    }

    protected InteractionResult handleUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        int starlight = StarLightManager.get(player);
        if (starlight <= 0) {
            return InteractionResult.FAIL;
        }

        // 每 2 点星光返还 1 个星币(转化比例集中管理,余数部分保留)。
        // 2026-09-15 用户裁决(F-1):兑换一律**按结果判定**——银行卡等提供的基础值(下限)不可兑换出去,
        // 故"亮着银行卡但没有任何可支配星光"时兑换产出为 0;此时整个主动技能作废(返回 fail,
        // 与"技能未释放"一致,不进入主动技能冷却、不发随机增益、也不触发技能响应事件),
        // 并给出 ActionBar 提示,避免"3 选 1 增益 + 180 秒冷却"被白嫖。
        int gained = ResourceConversion.starlightToStarCoins(player, -1);
        if (gained <= 0) {
            sendSignActionBarColored(player, net.minecraft.ChatFormatting.RED, "msg.astral_dice.parunan_active_no_starlight");
            return InteractionResult.FAIL;
        }

        // 主动技能:随机获得以下任一效果
        int choice = ThreadLocalRandom.current().nextInt(3);
        MobEffectInstance effect;
        if (choice == 0) {
            effect = new MobEffectInstance(MobEffects.SATURATION, 600, 0, false, true); // 饱和 30 秒
        } else if (choice == 1) {
            effect = new MobEffectInstance(MobEffects.LUCK, 6000, 0, false, true); // 幸运 5 分钟
        } else {
            effect = new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 18000, 0, false, true); // 村庄英雄 15 分钟
        }
        boolean applied = EffectTimerGuard.apply(player, effect);
        // 第二批「三态化」:只登记本次**实际施加成功**的计时器 ⇒ 进入锁定(生效中)态;锁定结束才起主动冷却。
        // 到期刻取本次随机到的效果实例时长(不硬编码时长表);施加失败(已有更长同类效果)则不锁、立即起冷却,
        // 也**不回读**实例剩余时长——那会把无关来源的同名效果(如袭击给的村庄英雄)误算成本技能的计时器
        if (applied) {
            beginActiveLock(player, "astral_dice:parunan_sign",
                    level.getGameTime() + effect.getDuration());
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * 主动冷却 tick:佩戴时基础 {@value #ACTIVE_COOLDOWN_SECONDS} 秒 —— 与怪力侦探/枪匠立牌
     * **同款链**(仅按充能上限封顶)。
     */
    @Override
    protected int activeCooldownBaseTicks(Player player) {
        int ticks = isEquipped(player)
                ? ACTIVE_COOLDOWN_SECONDS * 20
                : GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
        ticks = (int) ChargeManager.signCooldownTicks(player, ticks);
        return ticks;
    }

    /**
     * 同上,但**不折电池**:{@code signCooldownTicks} → {@code signCooldownTicksBase},
     * 其余(isEquipped 三元 / Math.max 夹底 / 兜底常量)**逐字保留**。
     * 供 {@code sign_active_max_cooldown_base}(命运指引卡减半基准)使用。
     */
    @Override
    protected int activeCooldownBaseTicksNoBattery(Player player) {
        int ticks = isEquipped(player)
                ? ACTIVE_COOLDOWN_SECONDS * 20
                : GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS;
        ticks = (int) ChargeManager.signCooldownTicksBase(player, ticks);
        return ticks;
    }

    // 触发骰神赐福后立即获得 骰点*2 星光(上限由 StarLightManager 统一管理)
    public static void gainStarlightOnBlessing(Player player, int dicePoint) {
        if (player.level().isClientSide()) return;
        StarLightManager.add(player, dicePoint * 2);
    }
}
