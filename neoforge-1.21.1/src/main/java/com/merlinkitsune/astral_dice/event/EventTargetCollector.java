package com.merlinkitsune.astral_dice.event;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 事件目标收集器。
 * 收集事件应影响的实体:触发者自身、周围指定范围内所有玩家、玩家所在团队(Minecraft 原生 team / FTB Teams / OPAC),
 * 以及玩家拥有的已放出且位于作用范围内的女仆(车万女仆模组)。
 */
public final class EventTargetCollector {
    private EventTargetCollector() {
    }

    public static List<LivingEntity> collectTargets(Player triggerer) {
        List<LivingEntity> targets = new ArrayList<>();
        if (triggerer.level().isClientSide()) return targets;
        targets.add(triggerer);

        double range = GameplayConstants.EVENT_RANGE;
        // 范围内所有玩家(不含自身)
        targets.addAll(triggerer.level().getEntitiesOfClass(Player.class,
                triggerer.getBoundingBox().inflate(range), p -> p != triggerer));

        // 团队
        collectTeamTargets(triggerer, targets);
        // 女仆
        if (GameplayConstants.EVENT_APPLY_MAID) {
            collectMaids(triggerer, range, targets);
        }
        return targets.stream().distinct().toList();
    }

    private static void collectTeamTargets(Player triggerer, List<LivingEntity> targets) {
        targets.addAll(collectTeamPlayers(triggerer));
    }

    // 收集触发者的团队在线玩家(Minecraft 原生 team / FTB Teams / OPAC,按配置开关)
    public static List<Player> collectTeamPlayers(Player triggerer) {
        List<Player> members = new ArrayList<>();
        if (triggerer.level().isClientSide()) return members;
        if (GameplayConstants.EVENT_APPLY_MC_TEAM) {
            collectMcTeamPlayers(triggerer, members);
        }
        if (GameplayConstants.EVENT_APPLY_FTB_TEAM) {
            collectFtbTeamPlayers(triggerer, members);
        }
        if (GameplayConstants.EVENT_APPLY_OPAC) {
            collectOpacPartyPlayers(triggerer, members);
        }
        return members;
    }

    // Minecraft 原生 team:同队在线玩家
    private static void collectMcTeamPlayers(Player triggerer, List<Player> members) {
        if (!(triggerer.level() instanceof ServerLevel serverLevel)) return;
        var team = triggerer.getTeam();
        if (team == null) return;
        for (ServerPlayer sp : serverLevel.players()) {
            if (sp != triggerer && sp.getTeam() == team) {
                members.add(sp);
            }
        }
    }

    // FTB Teams:反射调用,未安装或 API 变化时静默跳过
    private static void collectFtbTeamPlayers(Player triggerer, List<Player> members) {
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            Object api = apiClass.getMethod("api").invoke(null);
            Object manager = api.getClass().getMethod("getManager").invoke(api);
            // 兼容不同版本:优先 getTeamForPlayer(Player),其次 getTeamForPlayer(UUID)
            Object team;
            try {
                team = manager.getClass().getMethod("getTeamForPlayer", Player.class).invoke(manager, triggerer);
            } catch (NoSuchMethodException e) {
                team = manager.getClass().getMethod("getTeamForPlayer", UUID.class).invoke(manager, triggerer.getUUID());
            }
            if (team == null) return;
            // 获取在线成员
            Method membersMethod;
            try {
                membersMethod = team.getClass().getMethod("getOnlineMembers");
            } catch (NoSuchMethodException e) {
                membersMethod = team.getClass().getMethod("getMembers");
            }
            Object membersObj = membersMethod.invoke(team);
            if (membersObj instanceof Iterable<?> iterable) {
                for (Object member : iterable) {
                    if (member instanceof Player p && p != triggerer) {
                        members.add(p);
                    } else if (member instanceof UUID uuid) {
                        // UUID 列表:按 UUID 从世界查找在线玩家(与 OPAC 分支一致)
                        Player p = triggerer.level().getPlayerByUUID(uuid);
                        if (p != null && p != triggerer) {
                            members.add(p);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    // OPAC(Open Parties and Claims):反射调用,未安装或 API 变化时静默跳过
    private static void collectOpacPartyPlayers(Player triggerer, List<Player> members) {
        try {
            // 常见 API:dev.darkhax.opac.api.OpenPartiesAndClaimsAPI 等,按实际版本调整
            Class<?> apiClass = Class.forName("dev.darkhax.opac.api.OpenPartiesAndClaimsAPI");
            Object party = apiClass.getMethod("getPartyOf", Player.class).invoke(null, triggerer);
            if (party == null) return;
            Method membersMethod = party.getClass().getMethod("getPartyMembers");
            Object membersObj = membersMethod.invoke(party);
            if (membersObj instanceof Iterable<?> iterable) {
                for (Object member : iterable) {
                    if (member instanceof Player p && p != triggerer) {
                        members.add(p);
                    } else if (member instanceof UUID uuid) {
                        Player p = triggerer.level().getPlayerByUUID(uuid);
                        if (p != null && p != triggerer) {
                            members.add(p);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    // 车万女仆:收集触发者拥有且已放出(非魂符内)、位于作用范围内的女仆
    private static void collectMaids(Player triggerer, double range, List<LivingEntity> targets) {
        try {
            Class<?> maidClass = Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");
            List<Entity> maids = triggerer.level().getEntities(triggerer,
                    triggerer.getBoundingBox().inflate(range), e -> maidClass.isInstance(e));
            for (Entity entity : maids) {
                if (isMaidOwnedBy(entity, triggerer)) {
                    targets.add((LivingEntity) entity);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isMaidOwnedBy(Entity maid, Player owner) {
        try {
            Method m = null;
            try {
                m = maid.getClass().getMethod("getOwnerId");
            } catch (NoSuchMethodException e) {
                m = maid.getClass().getMethod("getOwnerUUID");
            }
            Object val = m.invoke(maid);
            if (val instanceof UUID uuid) {
                return uuid.equals(owner.getUUID());
            }
            if (val instanceof Player p) {
                return p.getUUID().equals(owner.getUUID());
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
