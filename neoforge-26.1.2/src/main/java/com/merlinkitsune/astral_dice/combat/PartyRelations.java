package com.merlinkitsune.astral_dice.combat;

import com.merlinkitsune.starenginelib.combat.HostileTargets;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 「同队 / 盟友」判定的**统一入口**（2026-09-30 新增，用户裁决）。
 *
 * <h2>为什么需要本类</h2>
 * 本模组原先的队友判定一律是裸的原版计分板判定
 * （{@code player.getTeam() != null && player.getTeam() == other.getTeam()}）。而整合包里的队伍
 * **不一定**走计分板：{@code FTB Teams} 用自己的一套 team/party 数据（这类玩家的计分板
 * {@code getTeam()} 恒为 {@code null}），{@code Open Parties and Claims}（OPAC）同理。
 * ⇒ 队友判定**完全失效**：同一 FTB 队伍内仍能互相造成伤害、电磁炮的 AoE 仍把队友算进敌对目标
 * （用户实报）。
 *
 * <h2>判定顺序（并集，任一命中即为真）</h2>
 * <ol>
 *   <li><b>原版计分板队伍</b> —— {@code Entity#getTeam()} 为同一实例；</li>
 *   <li><b>FTB Teams</b> —— 服务端 {@code TeamManager#arePlayersInSameTeam(UUID,UUID)} /
 *       {@code getTeamForPlayerID(UUID)}；客户端 {@code ClientTeamManager#getTeamForPlayer(Player)}；</li>
 *   <li><b>OPAC</b> —— 服务端 {@code IPartyManagerAPI#getPartyByMember(UUID)} 指向同一个 party，
 *       或两个 party 互为盟友（{@code IServerPartyAPI#isAlly(UUID)}）。</li>
 * </ol>
 *
 * <h2>为什么用反射</h2>
 * 三条线的实际安装情况并不一致（2026-09-30 实测：1.21.1 包有 {@code ftb-teams} + {@code ftb-library}；
 * **1.20.1 包完全没有 FTB**；**26.1.2 只有 ftb-library、没有 ftb-teams**；三个包**都未安装** OPAC）
 * ⇒ **不能**编译期依赖它们（否则另外两条线直接编译不过）。故全部走 {@code Class.forName} + 方法句柄，
 * 且**惰性解析、解析或调用失败一次即永久关闭该后端**（绝不因为某个第三方模组改版而让本模组崩溃）。
 *
 * <h2>失败方向（安全）</h2>
 * 任一后端不可用 ⇒ 该后端视为「不提供队伍信息」⇒ 判定退回其余后端，最终退回原版计分板口径
 * ⇒ 最坏情况与改动前完全一致。
 *
 * <p>⚠️ 本类**不得**引用任何客户端专属类（它是双端类，会被专用服务端加载）；FTB 客户端路径同样
 * 经反射调用，故本类常量池内不含客户端类型。
 *
 * <p>⚠️ OPAC 后端**仅服务端**：客户端侧 OPAC 只暴露「本地玩家自己的 party」，无法查询任意两名玩家
 * 的归属；且三个整合包目前均未安装 OPAC ⇒ 该后端在现网恒为未启用状态，属「接口已备、装上即生效」。
 */
public final class PartyRelations {

    private static final Logger LOGGER = LoggerFactory.getLogger("astral_dice.PartyRelations");

    private PartyRelations() {
    }

    // ══════════════════════════════ 公开判定 ══════════════════════════════

    /**
     * 两名实体是否属于**同一队伍**（原版计分板 ∪ FTB Teams ∪ OPAC，含 OPAC 的盟友队伍）。
     *
     * <p>目标不是玩家时只可能命中原版计分板（第三方队伍系统只覆盖玩家）。
     */
    public static boolean isSameTeam(Entity a, Entity b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        var teamA = a.getTeam();
        if (teamA != null && teamA == b.getTeam()) return true;
        if (!(a instanceof Player pa) || !(b instanceof Player pb)) return false;
        return Ftb.sameTeam(pa, pb) || Opac.sameTeam(pa, pb);
    }

    /**
     * 该实体是否**已在任一队伍系统中加入队伍**（原版计分板 ∪ FTB ∪ OPAC）。
     *
     * <p>用于「任一方无队伍即视为友方」这类宽松口径：若只看计分板，FTB 队伍成员会被误判为
     * 「无队伍」，从而对任何玩家都算友方 —— 那正是队伍系统失效的另一面。
     */
    public static boolean hasTeam(Entity entity) {
        if (entity == null) return false;
        if (entity.getTeam() != null) return true;
        if (!(entity instanceof Player player)) return false;
        return Ftb.teamId(player) != null || Opac.partyId(player) != null;
    }

    /**
     * 「盟友豁免 + 敌对判定」的统一入口：先做 {@link #isSameTeam} 豁免，再委托库的全局唯一入口
     * {@link HostileTargets#isHostile(Entity, Entity)}。
     *
     * <p><b>本模组所有「带视谁为敌上下文」的敌对判定都应改调本方法</b>（而非直接调库），
     * 否则 FTB / OPAC 的队友会被当成敌对目标（电磁炮命中友方、队友可互相伤害）。
     *
     * <p>{@code viewer} 为 {@code null}（如无归属的落雷）时不做豁免，直接委托库的既有语义。
     */
    public static boolean isHostileTo(Entity viewer, Entity target) {
        if (target == null) return false;
        if (isSameTeam(viewer, target)) return false;
        return HostileTargets.isHostile(viewer, target);
    }

    /**
     * 收集「与 {@code triggerer} 同队」的在线玩家（排除自己）；语义与库的
     * {@code EventTargetCollector#collectTeamPlayers} 一致：<b>未加入任何队伍 ⇒ 返回全服在线玩家</b>。
     *
     * <p><b>为什么不用库那份</b>（2026-09-30 实测）：库侧 {@code EventTargetCollector} 的 FTB 分支反射
     * {@code TeamManager#getTeamForPlayer(Player)} / {@code getTeamForPlayer(UUID)}，而 FTB Teams 的真实
     * 服务器端签名是 {@code getTeamForPlayer(ServerPlayer)} 与 {@code getTeamForPlayerID(UUID)}
     * ⇒ 两次 {@code NoSuchMethodException} 都被最外层 {@code catch (Exception ignored)} 吞掉、恒返回
     * {@code null}；OPAC 分支查的类名 {@code dev.darkhax.opac.*} 也不存在（真实为 {@code xaero.pac.*}）。
     * ⇒ 装了 FTB Teams 的玩家被库判为「无队伍」，从而落进「全服皆友方」兜底 —— 队友判定失效的另一面。
     * 该缺陷属**库侧**，应在其下一次发版中一并修（本类不改库）；此处先用模组侧实现顶住。
     *
     * <p>三个队伍系统的启用开关沿用库的 {@code GameplayConstants.EVENT_APPLY_MC_TEAM / _FTB_TEAM / _OPAC}
     * （三者默认均 {@code true}），口径与库完全一致。
     */
    public static List<Player> collectTeamPlayers(Player triggerer) {
        List<Player> members = new ArrayList<>();
        if (triggerer == null || triggerer.level().isClientSide()) return members;
        if (!(triggerer.level() instanceof ServerLevel serverLevel)) return members;
        boolean anyEnabled = GameplayConstants.EVENT_APPLY_MC_TEAM
                || GameplayConstants.EVENT_APPLY_FTB_TEAM
                || GameplayConstants.EVENT_APPLY_OPAC;
        for (ServerPlayer sp : serverLevel.players()) {
            if (sp == triggerer) continue;
            if (sameTeamAmongEnabledSystems(triggerer, sp)) members.add(sp);
        }
        if (members.isEmpty() && anyEnabled && !hasTeam(triggerer)) {
            for (ServerPlayer sp : serverLevel.players()) {
                if (sp != triggerer) members.add(sp);
            }
        }
        return members;
    }

    /** 按「已启用的队伍系统」判定同队（与库 {@code EventTargetCollector} 的三开关口径一致）。 */
    private static boolean sameTeamAmongEnabledSystems(Player a, Player b) {
        if (GameplayConstants.EVENT_APPLY_MC_TEAM) {
            var teamA = a.getTeam();
            if (teamA != null && teamA == b.getTeam()) return true;
        }
        if (GameplayConstants.EVENT_APPLY_FTB_TEAM && Ftb.sameTeam(a, b)) return true;
        return GameplayConstants.EVENT_APPLY_OPAC && Opac.sameTeam(a, b);
    }

    // ══════════════════════════════ FTB Teams 后端 ══════════════════════════════

    /**
     * FTB Teams 后端（惰性反射）。
     *
     * <h3>取哪一侧的管理器（按 {@code isClientSide} 分流，不用 isManagerLoaded 猜）</h3>
     * 客户端连远程服务器时，集成服务端可能同时存在 ⇒ 只看 {@code isManagerLoaded()} 会读到
     * **本地集成服务端**的队伍数据（与远程连接不符）。故：客户端走 {@code ClientTeamManager}
     * （数据由服务器同步而来），服务端走 {@code TeamManager}。
     */
    private static final class Ftb {
        private static final String API_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";
        private static final String TEAM_MANAGER_CLASS = "dev.ftb.mods.ftbteams.api.TeamManager";
        private static final String CLIENT_MANAGER_CLASS = "dev.ftb.mods.ftbteams.api.client.ClientTeamManager";
        private static final String TEAM_CLASS = "dev.ftb.mods.ftbteams.api.Team";

        private static volatile boolean resolved;
        private static volatile boolean enabled;
        private static Method apiMethod;              // FTBTeamsAPI.api()
        private static Method isManagerLoaded;        // API.isManagerLoaded()
        private static Method getManager;             // API.getManager() -> TeamManager
        private static Method isClientManagerLoaded;  // API.isClientManagerLoaded()
        private static Method getClientManager;       // API.getClientManager() -> ClientTeamManager
        private static Method arePlayersInSameTeam;   // TeamManager.arePlayersInSameTeam(UUID, UUID)
        private static Method getTeamForPlayerID;     // TeamManager.getTeamForPlayerID(UUID)
        private static Method getTeamForPlayer;       // ClientTeamManager.getTeamForPlayer(Player)
        private static Method teamGetId;              // Team.getId()

        private Ftb() {
        }

        private static boolean sameTeam(Player a, Player b) {
            Object api = api();
            if (api == null) return false;
            try {
                if (a.level().isClientSide() || b.level().isClientSide()) {
                    Object manager = clientManager(api);
                    if (manager == null) return false;
                    UUID ta = teamIdOf(getTeamForPlayer.invoke(manager, a));
                    if (ta == null) return false;
                    UUID tb = teamIdOf(getTeamForPlayer.invoke(manager, b));
                    return ta.equals(tb);
                }
                Object manager = serverManager(api);
                if (manager == null) return false;
                return (boolean) arePlayersInSameTeam.invoke(manager, a.getUUID(), b.getUUID());
            } catch (Throwable t) {
                disable(t);
                return false;
            }
        }

        private static UUID teamId(Player player) {
            Object api = api();
            if (api == null) return null;
            try {
                if (player.level().isClientSide()) {
                    Object manager = clientManager(api);
                    return manager == null ? null : teamIdOf(getTeamForPlayer.invoke(manager, player));
                }
                Object manager = serverManager(api);
                return manager == null ? null : teamIdOf(getTeamForPlayerID.invoke(manager, player.getUUID()));
            } catch (Throwable t) {
                disable(t);
                return null;
            }
        }

        private static Object serverManager(Object api) throws Exception {
            return (boolean) isManagerLoaded.invoke(api) ? getManager.invoke(api) : null;
        }

        private static Object clientManager(Object api) throws Exception {
            return (boolean) isClientManagerLoaded.invoke(api) ? getClientManager.invoke(api) : null;
        }

        private static Object api() {
            resolve();
            if (!enabled) return null;
            try {
                return apiMethod.invoke(null);
            } catch (Throwable t) {
                disable(t);
                return null;
            }
        }

        /** {@code Optional<Team>} → team UUID；empty / 非 Optional 一律 null。 */
        private static UUID teamIdOf(Object optionalTeam) throws Exception {
            if (!(optionalTeam instanceof Optional<?> opt) || opt.isEmpty()) return null;
            return (UUID) teamGetId.invoke(opt.get());
        }

        private static synchronized void resolve() {
            if (resolved) return;
            resolved = true;
            try {
                Class<?> apiCls = Class.forName(API_CLASS);
                Class<?> managerCls = Class.forName(TEAM_MANAGER_CLASS);
                Class<?> clientCls = Class.forName(CLIENT_MANAGER_CLASS);
                Class<?> teamCls = Class.forName(TEAM_CLASS);
                apiMethod = apiCls.getMethod("api");
                isManagerLoaded = apiCls.getMethod("isManagerLoaded");
                getManager = apiCls.getMethod("getManager");
                isClientManagerLoaded = apiCls.getMethod("isClientManagerLoaded");
                getClientManager = apiCls.getMethod("getClientManager");
                arePlayersInSameTeam = managerCls.getMethod("arePlayersInSameTeam", UUID.class, UUID.class);
                getTeamForPlayerID = managerCls.getMethod("getTeamForPlayerID", UUID.class);
                getTeamForPlayer = clientCls.getMethod("getTeamForPlayer", Player.class);
                teamGetId = teamCls.getMethod("getId");
                enabled = true;
                LOGGER.debug("[Astral Dice] FTB Teams 队伍判定已接入");
            } catch (Throwable t) {
                enabled = false;
                LOGGER.debug("[Astral Dice] 未接入 FTB Teams，队友判定退回原版计分板：{}", t.toString());
            }
        }

        private static void disable(Throwable t) {
            if (enabled) {
                enabled = false;
                LOGGER.warn("[Astral Dice] FTB Teams 队伍判定已停用（运行时兼容性问题），退回原版计分板", t);
            }
        }
    }

    // ══════════════════════════ Open Parties and Claims 后端 ══════════════════════════

    /**
     * OPAC 后端（惰性反射，**仅服务端**）。
     *
     * <p>入口链（取自 {@code thexaero/open-parties-and-claims} 分支 {@code 1.21} @ {@code c0d97b3}
     * 与 {@code 1.20} @ {@code 3d73aae}，两分支包路径一致）：
     * {@code OpenPACServerAPI.get(MinecraftServer)} → {@code IPartyManagerAPI#getPartyByMember(UUID)}
     * → {@code IServerPartyAPI}；同队判 {@code getId()} 相等，盟友判 {@code isAlly(UUID)}。
     */
    private static final class Opac {
        private static final String API_CLASS = "xaero.pac.common.server.api.OpenPACServerAPI";
        private static final String MANAGER_CLASS = "xaero.pac.common.server.parties.party.api.IPartyManagerAPI";
        private static final String PARTY_CLASS = "xaero.pac.common.server.parties.party.api.IServerPartyAPI";

        private static volatile boolean resolved;
        private static volatile boolean enabled;
        private static Method getApi;             // OpenPACServerAPI.get(MinecraftServer)
        private static Method getPartyManager;    // IPartyManagerAPI opac.getPartyManager()
        private static Method getPartyByMember;   // IServerPartyAPI getPartyByMember(UUID)
        private static Method partyGetId;         // UUID party.getId()
        private static Method partyIsAlly;        // boolean party.isAlly(UUID)

        private Opac() {
        }

        private static boolean sameTeam(Player a, Player b) {
            Object partyA = party(a);
            Object partyB = party(b);
            if (partyA == null || partyB == null) return false;
            try {
                UUID idA = (UUID) partyGetId.invoke(partyA);
                UUID idB = (UUID) partyGetId.invoke(partyB);
                if (idA != null && idA.equals(idB)) return true;
                // 盟友队伍同样视为「同队」（OPAC 的 party 之间可结盟，盟友间不应互相伤害）
                return (boolean) partyIsAlly.invoke(partyA, idB);
            } catch (Throwable t) {
                disable(t);
                return false;
            }
        }

        private static UUID partyId(Player player) {
            Object party = party(player);
            if (party == null) return null;
            try {
                return (UUID) partyGetId.invoke(party);
            } catch (Throwable t) {
                disable(t);
                return null;
            }
        }

        /** 该玩家所属的 OPAC party；未装 OPAC / 客户端 / 无队伍 / 出任何错一律 {@code null}。 */
        private static Object party(Player player) {
            resolve();
            if (!enabled) return null;
            // 客户端不接入（客户端 API 只暴露本地玩家自己的 party，无法查询任意两名玩家）
            if (player.level().isClientSide()) return null;
            MinecraftServer server = player.level().getServer();
            if (server == null) return null;
            try {
                Object api = getApi.invoke(null, server);
                Object manager = api == null ? null : getPartyManager.invoke(api);
                return manager == null ? null : getPartyByMember.invoke(manager, player.getUUID());
            } catch (Throwable t) {
                disable(t);
                return null;
            }
        }

        private static synchronized void resolve() {
            if (resolved) return;
            resolved = true;
            try {
                Class<?> apiCls = Class.forName(API_CLASS);
                Class<?> managerCls = Class.forName(MANAGER_CLASS);
                Class<?> partyCls = Class.forName(PARTY_CLASS);
                getApi = apiCls.getMethod("get", MinecraftServer.class);
                getPartyManager = apiCls.getMethod("getPartyManager");
                getPartyByMember = managerCls.getMethod("getPartyByMember", UUID.class);
                partyGetId = partyCls.getMethod("getId");
                partyIsAlly = partyCls.getMethod("isAlly", UUID.class);
                enabled = true;
                LOGGER.debug("[Astral Dice] OPAC 队伍判定已接入");
            } catch (Throwable t) {
                enabled = false;
                LOGGER.debug("[Astral Dice] 未接入 OPAC，队友判定退回其余后端：{}", t.toString());
            }
        }

        private static void disable(Throwable t) {
            if (enabled) {
                enabled = false;
                LOGGER.warn("[Astral Dice] OPAC 队伍判定已停用（运行时兼容性问题）", t);
            }
        }
    }
}
