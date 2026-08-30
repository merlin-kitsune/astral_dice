package com.merlinkitsune.astral_dice.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.merlinkitsune.astral_dice.component.GameplayConstants;

/**
 * 团队玩家收集器:收集触发者的团队在线玩家(Minecraft 原生 team / FTB Teams / OPAC,
 * 按配置开关;用于随机卡牌发放等场景)。
 */
public final class EventTargetCollector {
    private EventTargetCollector() {
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
}
