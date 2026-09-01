package com.merlinkitsune.astral_dice.item.chip;
import com.merlinkitsune.astral_dice.item.CuriosCompat;

import com.merlinkitsune.astral_dice.item.HealingManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.Map;
import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 友情徽章筹码:对友方玩家施加任意治疗效果时,使双方各获得 2 点治愈。
 *
 * <p>触发来源:
 * - 本模组内明确调用 {@link #onHealApplied} 的治疗方法;
 * - 原版/部分模组通过治疗类状态效果(瞬间治疗、生命恢复)施加时,由
 *   本类自身的 MobEffectEvent.Added 监听分发。
 *
 * <p>去重:同一治疗者对同一目标短时间内(1 秒)只触发一次,避免混合/持续治疗重复触发。
 */
@Mod.EventBusSubscriber(modid = com.merlinkitsune.astral_dice.AstralDiceMod.MODID)
public class FriendshipBadgeChipItem extends BaseChipItem {
    /** 双方各获得的治愈点数 */
    public static final int HEALING_POINTS = 2;
    /** 同一治疗者-目标组合的触发冷却(tick,1 秒) */
    private static final long TRIGGER_COOLDOWN_TICKS = 20;

    private static final Map<String, Long> LAST_TRIGGER = new HashMap<>();

    public FriendshipBadgeChipItem(Properties properties) {
        super(properties);
    }

    // 玩家是否佩戴友情徽章
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosCompat.getCuriosInventory(player);
        return curios.isPresent() && curios.get().findFirstCurio(s -> s.is(ModItems.FRIENDSHIP_BADGE.get())).isPresent();
    }

    // 治疗者向友方玩家施加治疗时调用
    public static void onHealApplied(Player healer, Player target) {
        if (healer == null || target == null || healer == target) return;
        if (healer.level().isClientSide()) return;
        if (!isEquipped(healer)) return;
        if (!isFriendly(healer, target)) return;

        String key = healer.getUUID() + "|" + target.getUUID();
        long now = healer.level().getGameTime();
        Long last = LAST_TRIGGER.get(key);
        if (last != null && now - last < TRIGGER_COOLDOWN_TICKS) return;
        LAST_TRIGGER.put(key, now);
        if (LAST_TRIGGER.size() > 500) {
            LAST_TRIGGER.clear();
        }

        HealingManager.add(healer, HEALING_POINTS);
        HealingManager.add(target, HEALING_POINTS);
    }

    // 友方判定:同队伍,或任意一方无队伍(与奢华大餐/史莱姆立牌的治疗范围规则一致)
    private static boolean isFriendly(Player healer, Player target) {
        return healer.getTeam() == null || target.getTeam() == null || healer.getTeam() == target.getTeam();
    }

    // 友情徽章:友方玩家获得治疗类效果(瞬间治疗/生命恢复)时,若来源为佩戴徽章的玩家,双方各获得 2 点治愈
    @SubscribeEvent
    public static void onFriendlyHealingEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player target)) return;
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null) return;
        if (effect.getEffect() != MobEffects.HEAL
                && effect.getEffect() != MobEffects.REGENERATION) return;
        Player healer = resolvePlayerSource(event.getEffectSource());
        if (healer == null || healer == target) return;
        FriendshipBadgeChipItem.onHealApplied(healer, target);
    }


    // 从治疗效果来源实体解析出施治玩家(直接玩家或弹射物所有者)
    private static Player resolvePlayerSource(net.minecraft.world.entity.Entity source) {
        if (source instanceof Player player) return player;
        if (source instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof Player player) {
            return player;
        }
        if (source instanceof net.minecraft.world.entity.AreaEffectCloud cloud
                && cloud.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

}
