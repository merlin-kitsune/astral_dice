package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.astral_dice.combat.PartyRelations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * 队伍目标收集：在 {@link PartyRelations} 的玩家队伍口径之上，补上「经 DiceCombatParticipants
 * 注册、且归属于自己或同队玩家」的生物（例如车万女仆的骰战参与者）。
 */
public final class TeamTargets {

    private TeamTargets() {
    }

    /** 是否为自己 / 同队玩家 / 自己或同队玩家拥有的已注册骰战生物。 */
    public static boolean isFriendly(Player viewer, Entity other) {
        if (viewer == null || other == null) return false;
        if (viewer == other) return true;
        if (PartyRelations.isSameTeam(viewer, other)) return true;
        if (!(other instanceof LivingEntity living)) return false;
        if (!DiceCombatParticipants.isParticipant(living)) return false;
        if (!(living instanceof OwnableEntity ownable)) return false;
        LivingEntity owner = ownable.getOwner();
        if (!(owner instanceof Player ownerPlayer)) return false;
        return ownerPlayer == viewer || PartyRelations.isSameTeam(viewer, ownerPlayer);
    }

    /**
     * 收集「自身 + 同队在线玩家 + 他们附近指定半径内归属自己/同队的已注册骰战生物」。
     * 未加入任何队伍时只收自己与自己的生物，不使用 {@link PartyRelations#collectTeamPlayers}
     * 的「无队伍 ⇒ 全服在线玩家」兜底。
     */
    public static List<LivingEntity> collectFriendlyLiving(Player player, double radius) {
        LinkedHashMap<UUID, LivingEntity> result = new LinkedHashMap<>();
        if (player == null || player.level().isClientSide()) return List.of();
        result.put(player.getUUID(), player);
        if (PartyRelations.hasTeam(player)) {
            for (Player teammate : PartyRelations.collectTeamPlayers(player)) {
                if (teammate != null && teammate.isAlive()) {
                    result.putIfAbsent(teammate.getUUID(), teammate);
                }
            }
        }
        List<Player> anchors = new ArrayList<>();
        for (LivingEntity entity : result.values()) {
            if (entity instanceof Player anchor) anchors.add(anchor);
        }
        for (Player anchor : anchors) {
            for (LivingEntity ally : anchor.level().getEntitiesOfClass(
                    LivingEntity.class,
                    anchor.getBoundingBox().inflate(radius),
                    entity -> entity != anchor && entity.isAlive() && isFriendly(anchor, entity))) {
                result.putIfAbsent(ally.getUUID(), ally);
            }
        }
        return new ArrayList<>(result.values());
    }

    /** 敌对目标：先排除自己/队友/队友生物，再走库的全局敌对判定。 */
    public static boolean isHostileTo(Player viewer, Entity target) {
        if (target == null) return false;
        if (isFriendly(viewer, target)) return false;
        return PartyRelations.isHostileTo(viewer, target);
    }
}
