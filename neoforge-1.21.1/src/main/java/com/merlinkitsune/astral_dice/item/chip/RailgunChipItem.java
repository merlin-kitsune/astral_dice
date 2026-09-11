package com.merlinkitsune.astral_dice.item.chip;

import com.merlinkitsune.astral_dice.item.ChargeManager;
import com.merlinkitsune.astral_dice.item.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * 电磁炮筹码:
 * <ul>
 *   <li>充能层数不少于 6 时,攻击力 +5;</li>
 *   <li>对敌对目标发起攻击时,消耗 6 层充能,并对目标 3 格范围内所有敌对目标
 *       额外降下雷击(延迟 1 秒触发,使用 Minecraft 原生雷击,基础伤害固定 5 点,
 *       与本次攻击的伤害无关;可生成闪电苦力怕)。</li>
 * </ul>
 * <p>雷击使用原版 {@code DamageTypes.LIGHTNING_BOLT} 伤害源(伤害实体为空),
 * 既不是近战攻击,也不进入骰战结算,因此不会触发骰神赐福。</p>
 */
public class RailgunChipItem extends BaseChipItem {
    /** 攻击力加成所需的充能层数 */
    public static final int CHARGE_REQUIRED = 6;
    /** 满足条件时提供的攻击力 */
    public static final int ATTACK_BONUS = 5;
    /** 雷击覆盖半径(格) */
    public static final int AOE_RADIUS = 3;
    /** 雷击延迟(1 秒) */
    public static final int STRIKE_DELAY_TICKS = 20;

    public RailgunChipItem(Properties properties) {
        super(properties);
    }

    /** 玩家是否佩戴电磁炮 */
    public static boolean isEquipped(Player player) {
        if (player == null) return false;
        var curios = CuriosApi.getCuriosInventory(player);
        return curios.isPresent()
                && curios.get().findFirstCurio(s -> s.is(ModItems.RAILGUN_CHIP.get())).isPresent();
    }

    /** 当前充能是否满足攻击力加成条件;满足则返回 +5 */
    public static int getAttackBonus(Player player) {
        if (!isEquipped(player)) return 0;
        return ChargeManager.getStacks(player) >= CHARGE_REQUIRED ? ATTACK_BONUS : 0;
    }

    /**
     * 对敌对目标发起攻击时调用:消耗 6 层充能,并延迟 1 秒对目标 3 格内的敌对目标降下雷击。
     */
    public static void onAttack(Player player, LivingEntity target) {
        if (player == null || target == null) return;
        if (player.level().isClientSide()) return;
        if (!isEquipped(player)) return;
        if (!(target instanceof Enemy)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (ChargeManager.getStacks(player) < CHARGE_REQUIRED) return;

        ChargeManager.consume(player, CHARGE_REQUIRED);
        scheduleLightning(level, player, target);
    }

    // 延迟 1 秒(原版 TickTask,双版本零差异)后,对中心 3 格内的敌对目标逐一降下雷击
    private static void scheduleLightning(ServerLevel level, Player player, LivingEntity target) {
        MinecraftServer server = level.getServer();
        if (server == null) return;
        final Vec3 fallbackCenter = target.position();
        server.tell(new TickTask(server.getTickCount() + STRIKE_DELAY_TICKS, () -> {
            Vec3 center = target.isAlive() ? target.position() : fallbackCenter;
            AABB aabb = new AABB(center, center).inflate(AOE_RADIUS);
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, aabb,
                    e -> e instanceof Enemy && e.isAlive());
            for (LivingEntity victim : victims) {
                strike(level, victim.position(), player);
            }
        }));
    }

    // 原生雷击:setVisualOnly(false) 才会造成雷击伤害(基础 5 点)并把苦力怕转化为闪电苦力怕
    private static void strike(ServerLevel level, Vec3 pos, Player cause) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(pos);
        bolt.setVisualOnly(false);
        bolt.setCause(cause instanceof ServerPlayer serverPlayer ? serverPlayer : null);
        level.addFreshEntity(bolt);
    }
}
