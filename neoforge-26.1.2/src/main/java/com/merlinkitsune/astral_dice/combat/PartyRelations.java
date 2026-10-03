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
 *   <li><b>FTB Teams</b> —— 服务端 {@code TeamManager#arePlayersInSameTeam(UUID,UUID)}；
 *       客户端 {@code ClientTeamManager#getKnownPlayer(UUID)} → {@code KnownClientPlayer#teamId()}；</li>
 *   <li><b>OPAC</b> —— 服务端 {@code IPartyManagerAPI#getPartyByMember(UUID)} 指向同一个 party，
 *       或两个 party 互为盟友（{@code IServerPartyAPI#isAlly(UUID)}）。</li>
 * </ol>
 *
 * <h2>为什么用反射</h2>
 * 四条线的实际安装情况并不一致（2026-09-30 实测：1.21.1 包有 {@code ftb-teams} + {@code ftb-library}；
 * **1.20.1 包完全没有 FTB**；**26.1.2 只有 ftb-library、没有 ftb-teams**；四个包**都未安装** OPAC。
 * 2026-10-01 起 1.20.1 的**测试实例**已补装 FTB Teams + OPAC 以做正向冒烟，但**整合包仍可能不装**）
 * ⇒ **不能**编译期依赖它们（否则未装的那几条线直接编译不过）。故全部走 {@code Class.forName} + 方法句柄，
 * 且**惰性解析、解析或调用失败一次即永久关闭该后端**（绝不因为某个第三方模组改版而让本模组崩溃）。
 *
 * <h2>失败方向（安全）</h2>
 * 任一后端不可用 ⇒ 该后端视为「不提供队伍信息」⇒ 判定退回其余后端，最终退回原版计分板口径
 * ⇒ 最坏情况与改动前完全一致。后端的实际接入状态由 {@link #reportBackends()} 打出可断言的机器行
 * （{@code AP_PARTY:}），不再依赖 debug 级别的静默日志。
 *
 * <h2>⚠️ 反射契约必须按**发布产物**核验（2026-10-01 全量重校）</h2>
 * 本类此前的 FTB 反射契约**四处签名对不上**，而 {@code resolve()} 把所有解析放在同一个 try 内、
 * 失败只打一条 debug —— 结果是 **FTB 后端恒为未启用且毫无声响**（等于没写）。
 * 根因与结论（证据：上游源码 + 发布 jar 的 {@code javap}，见下「核验基准」）：
 * <ol>
 *   <li><b>访问器在嵌套接口上</b>：{@code isManagerLoaded/getManager/isClientManagerLoaded/getClientManager}
 *       声明在 {@code FTBTeamsAPI$API} 上，**不在**外层类 {@code FTBTeamsAPI} 上；
 *       {@code Class#getMethod} 不会跨到这个嵌套接口（外层类并未实现它）。</li>
 *   <li><b>客户端入口不是 {@code getTeamForPlayer(Player)}</b>：
 *       {@code ClientTeamManager} 的真实成员是 {@code getKnownPlayer(UUID) → Optional<KnownClientPlayer>}，
 *       而 {@code KnownClientPlayer} 是 <b>record</b>，访问器为 {@code teamId()}（**没有 get 前缀**）。</li>
 *   <li><b>「同队」必须比 party 团队 id</b>：{@code Team#getId()} 对玩家队伍等于该玩家自己的 UUID
 *       （同一 party 的两名成员 {@code getId()} 各不相同）⇒ 应比 {@code Team#getTeamId()}（= 生效队伍 id）。</li>
 *   <li><b>{@code hasTeam} 不能用「有 Team 对象」判定</b>：FTB 里**每个玩家**都有个人队伍
 *       ⇒ 恒为「有队伍」会把「未组队 ⇒ 友方作用于全服」的兜底彻底堵死。判据应为 party / server team。</li>
 * </ol>
 *
 * <h3>核验基准（可复跑，均以**发布产物** `javap -p` 为准，不用记忆也不用注释）</h3>
 * <ul>
 *   <li>FTB Teams：{@code 1.20.1/main} @ {@code f7dcaa9c}（{@code mod_version=2001.3.2}，并回溯最早
 *       1.20.1 版 {@code v2001.1.2-alpha}，两版 API 形态一致）；发布产物
 *       {@code ftb-teams-forge-2001.3.2.jar}（1.20.1 线）与 {@code ftb-teams-neoforge-2101.1.11.jar}
 *       （1.21.1 线）—— **两者 API 逐条同形**（含 {@code FTBTeamsAPI$API} 四个访问器、
 *       {@code getTeamForPlayerID} / {@code getKnownPlayer} / {@code KnownClientPlayer#teamId()} /
 *       {@code isPartyTeam} / {@code isServerTeam}）；</li>
 *   <li>OPAC：{@code 1.20} @ {@code 3d73aae}；发布产物
 *       {@code open-parties-and-claims-forge-1.20.1-0.30.1.jar} —— {@code xaero.pac.*} 三处入口与
 *       {@code getId()} / {@code isAlly(UUID)} **五处签名全部吻合**；</li>
 *   <li>脚本 {@code tools/verify_party_api.py}（自动从本类源码抽取反射契约、对真实 jar 逐个比对，
 *       四线分线报告 PASS/FAIL）；测试台用例 {@code PARTY-BACKENDS} 断言机器行形态。</li>
 * </ul>
 * <p>⚠️ 26.1.2 线本机暂无 {@code ftb-teams} / OPAC 发布产物 ⇒ 未逐条核验；反射为 fail-safe（解析失败
 * 即关闭该后端并记入 {@code why_*}），故不构成风险，但**不得**据此宣称该线已验证。
 * <p>⚠️ 本类在 1.20.1 forge / 1.21.1 neo / 26.1.2 neo 三条生产线上**内容完全一致**（同构要求）；
 * 逐线的产物证据见 {@code AGENTS.md} 与 {@code scripts/test/TESTING-SPEC.md}。
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
     *
     * <p>⚠️ FTB 的判据是 **party / server team**，<b>不是</b>「存在 Team 对象」：
     * FTB 给每个玩家都建了个人队伍，按后者判定会恒真、把兜底堵死（见类头 ④）。
     */
    public static boolean hasTeam(Entity entity) {
        if (entity == null) return false;
        if (entity.getTeam() != null) return true;
        if (!(entity instanceof Player player)) return false;
        return Ftb.hasTeam(player) || Opac.partyId(player) != null;
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
     * <p><b>为什么保留模组侧实现（而不是直接改调库那份）</b>：库侧 {@code EventTargetCollector} 曾有
     * 一处**同类**缺陷（FTB 分支在 {@code TeamManager} 上反射 {@code getTeamForPlayer(Player)} /
     * {@code getTeamForPlayer(UUID)} 两者都不存在；OPAC 分支查的 {@code dev.darkhax.opac.*} 整条包不存在）
     * —— 该缺陷已于 **库 1.0.6 / 1.0.6-alpha.1（2026-10-01）修复**，现网不再需要「用模组侧顶住」。
     * 本类仍保留自己的实现，是因为本类是模组侧「同队 / 盟友」判定的**唯一入口**，需额外承担三件事：
     * 三条队伍系统的**启用开关**口径、{@code hasTeam} 的 party/server-team 语义、以及
     * {@link #reportBackends()} 打出的**可断言机器行**（见类头）。
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

    /**
     * 「**事件效果目标**」的统一收集入口(2026-10-03 用户裁决口径)。
     *
     * <p>与 {@link #collectTeamPlayers(Player)} 的区别:后者是「队友 + 无队伍时全服」的**旧**口径,
     * 本方法按用户新规则给出**事件广播**的目标集合:
     *
     * <ul>
     *   <li><b>触发者已加入队伍</b> ⇒ 全体队友 ∪ 半径内**非队友**玩家
     *       (队友不再按距离判定,故不存在「同队重复判定」);</li>
     *   <li><b>触发者未加入任何队伍</b> ⇒ 全部**无队伍**在线玩家 ∪ 半径内**所有**玩家
     *       (范围内**不做**无队伍筛选 —— 有队伍的玩家同样收到)。</li>
     * </ul>
     *
     * <p>触发者本人**恒**在集合内;队伍判定沿用三条后端的三开关口径
     * ({@link #sameTeamAmongEnabledSystems}),与 {@link #collectTeamPlayers(Player)} 同源。
     *
     * @param radius 半径(格),按**三维距离**判定
     */
    public static List<Player> collectEventTargets(Player triggerer, double radius) {
        List<Player> targets = new ArrayList<>();
        if (triggerer == null || triggerer.level().isClientSide()) return targets;
        if (!(triggerer.level() instanceof ServerLevel serverLevel)) return targets;
        targets.add(triggerer);
        double r2 = radius * radius;
        boolean selfHasTeam = hasTeam(triggerer);
        for (ServerPlayer sp : serverLevel.players()) {
            if (sp == triggerer) continue;
            boolean inRange = sp.distanceToSqr(triggerer) <= r2;
            if (selfHasTeam) {
                // 有队伍:全队 ∪ 半径内非队友
                if (sameTeamAmongEnabledSystems(triggerer, sp) || inRange) targets.add(sp);
            } else {
                // 无队伍:无队伍者 ∪ 半径内所有玩家
                if (inRange || !hasTeam(sp)) targets.add(sp);
            }
        }
        return targets;
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

    // ══════════════════════════════ 诊断机器行 ══════════════════════════════

    /**
     * 打印一条**可断言的机器行**，报告三条后端的接入状态（供测试台 / 冒烟读日志断言）。
     *
     * <p>格式（全 ASCII，便于 grep）：
     * {@code AP_PARTY: sw_mc=true sw_ftb=true sw_opac=true back_ftb=on back_opac=off why_ftb=OK why_opac=ClassNotFoundException:xaero...}
     *
     * <ul>
     *   <li>{@code sw_*} —— 库侧三条启用开关（公共配置项）；</li>
     *   <li>{@code back_ftb} / {@code back_opac} —— 该后端的**反射契约是否解析成功**
     *       （{@code on} = 第三方模组在场且签名全部命中；{@code off} = 未安装或签名不符）；</li>
     *   <li>{@code why_*} —— 解析失败的原因（{@code OK} 表示未失败）。
     *       ⚠️ 原版计分板后端无「未启用」概念，故不为它设字段。</li>
     * </ul>
     *
     * <p>「装了 FTB Teams 却 {@code back_ftb=off}」时，{@code why_ftb} 会直接指出是哪一个
     * 类/方法没找到 —— 这正是本类 2026-10-01 之前静默失效的那一类问题。
     *
     * <p>本方法**只读**：不触碰任何玩家状态，可在 common setup 阶段调用。
     */
    public static String reportBackends() {
        Ftb.resolve();
        Opac.resolve();
        String line = "AP_PARTY: sw_mc=" + GameplayConstants.EVENT_APPLY_MC_TEAM
                + " sw_ftb=" + GameplayConstants.EVENT_APPLY_FTB_TEAM
                + " sw_opac=" + GameplayConstants.EVENT_APPLY_OPAC
                + " back_ftb=" + onOff(Ftb.enabled)
                + " back_opac=" + onOff(Opac.enabled)
                + " why_ftb=" + Ftb.failureReason
                + " why_opac=" + Opac.failureReason;
        LOGGER.info("{}", line);
        return line;
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    // ══════════════════════════════ FTB Teams 后端 ══════════════════════════════

    /**
     * FTB Teams 后端（惰性反射）。
     *
     * <h3>取哪一侧的管理器（按 {@code isClientSide} 分流，不用 isManagerLoaded 猜）</h3>
     * 客户端连远程服务器时，集成服务端可能同时存在 ⇒ 只看 {@code isManagerLoaded()} 会读到
     * **本地集成服务端**的队伍数据（与远程连接不符）。故：客户端走 {@code ClientTeamManager}
     * （数据由服务器同步而来），服务端走 {@code TeamManager}。
     *
     * <h3>服务端与客户端的等价判据（两处语义必须一致）</h3>
     * <ul>
     *   <li>服务端：{@code arePlayersInSameTeam(id1, id2)} —— FTB 自身的实现为
     *       {@code getTeamForPlayerID(id).getEffectiveTeam().getId()} 相等；</li>
     *   <li>客户端：{@code KnownClientPlayer#teamId()} 相等 —— 该字段在 FTB 侧就是
     *       {@code PlayerTeam#getTeamId()}（生效队伍 id），与服务端口径同源；</li>
     * </ul>
     * 两者都指向**生效队伍（party）**，而非玩家个人队伍。
     */
    private static final class Ftb {
        private static final String API_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";
        private static final String API_IFACE_CLASS = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API";
        private static final String TEAM_MANAGER_CLASS = "dev.ftb.mods.ftbteams.api.TeamManager";
        private static final String CLIENT_MANAGER_CLASS = "dev.ftb.mods.ftbteams.api.client.ClientTeamManager";
        private static final String KNOWN_PLAYER_CLASS = "dev.ftb.mods.ftbteams.api.client.KnownClientPlayer";
        private static final String TEAM_CLASS = "dev.ftb.mods.ftbteams.api.Team";

        private static volatile boolean resolved;
        private static volatile boolean enabled;
        private static volatile String failureReason = "NOT_RESOLVED";

        private static Method apiMethod;              // FTBTeamsAPI.api()
        private static Method isManagerLoaded;        // API.isManagerLoaded()
        private static Method getManager;             // API.getManager() -> TeamManager
        private static Method isClientManagerLoaded;  // API.isClientManagerLoaded()
        private static Method getClientManager;       // API.getClientManager() -> ClientTeamManager
        private static Method arePlayersInSameTeam;   // TeamManager.arePlayersInSameTeam(UUID, UUID)
        private static Method getTeamForPlayerID;     // TeamManager.getTeamForPlayerID(UUID) -> Optional<Team>
        private static Method getKnownPlayer;         // ClientTeamManager.getKnownPlayer(UUID) -> Optional<KnownClientPlayer>
        private static Method getTeamByID;            // ClientTeamManager.getTeamByID(UUID) -> Optional<Team>
        private static Method knownPlayerTeamId;      // KnownClientPlayer.teamId()   —— record 访问器，无 get 前缀
        private static Method teamIsPartyTeam;        // Team.isPartyTeam()  （可选：缺失不影响同队判定）
        private static Method teamIsServerTeam;       // Team.isServerTeam() （可选：同上）

        private Ftb() {
        }

        private static boolean sameTeam(Player a, Player b) {
            Object api = api();
            if (api == null) return false;
            try {
                if (a.level().isClientSide() || b.level().isClientSide()) {
                    Object manager = clientManager(api);
                    if (manager == null) return false;
                    UUID ta = clientTeamId(manager, a);
                    UUID tb = clientTeamId(manager, b);
                    return ta != null && ta.equals(tb);
                }
                Object manager = serverManager(api);
                if (manager == null) return false;
                return (boolean) arePlayersInSameTeam.invoke(manager, a.getUUID(), b.getUUID());
            } catch (Throwable t) {
                disable(t);
                return false;
            }
        }

        /**
         * 是否**加入了队伍**（FTB 语义：party / server team）。
         *
         * <p>不用「Team 对象非空」——FTB 给每个玩家都建了个人队伍，那样会恒为真。
         */
        private static boolean hasTeam(Player player) {
            Object team = team(player);
            if (team == null) return false;
            if (teamIsPartyTeam == null || teamIsServerTeam == null) {
                // 判据方法在假设的版本差异下缺失：退回「有生效队伍对象即算有队伍」。
                // 刻意的偏向 —— 宁可少走「全服皆友方」兜底，也不要把它放大成对全服生效。
                return true;
            }
            try {
                return (boolean) teamIsPartyTeam.invoke(team) || (boolean) teamIsServerTeam.invoke(team);
            } catch (Throwable t) {
                disable(t);
                return false;
            }
        }

        /** 该玩家当前**生效**的 Team 对象（个人队伍 / party / server team 皆可能）；任一步失败返回 null。 */
        private static Object team(Player player) {
            Object api = api();
            if (api == null) return null;
            try {
                if (player.level().isClientSide()) {
                    Object manager = clientManager(api);
                    if (manager == null) return null;
                    UUID teamId = clientTeamId(manager, player);
                    return teamId == null ? null : unwrap(getTeamByID.invoke(manager, teamId));
                }
                Object manager = serverManager(api);
                return manager == null ? null : unwrap(getTeamForPlayerID.invoke(manager, player.getUUID()));
            } catch (Throwable t) {
                disable(t);
                return null;
            }
        }

        /** 客户端算「同队」用的**生效队伍 id**（{@code KnownClientPlayer#teamId()}，record 访问器）。 */
        private static UUID clientTeamId(Object manager, Player player) throws Exception {
            Object known = unwrap(getKnownPlayer.invoke(manager, player.getUUID()));
            return known == null ? null : (UUID) knownPlayerTeamId.invoke(known);
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

        /** {@code Optional<?>} → 内部值；{@code empty} / 非 Optional / null 一律 null。 */
        private static Object unwrap(Object optional) {
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) return null;
            return opt.get();
        }

        private static synchronized void resolve() {
            if (resolved) return;
            resolved = true;
            try {
                Class<?> apiCls = Class.forName(API_CLASS);
                Class<?> managerCls = Class.forName(TEAM_MANAGER_CLASS);
                Class<?> clientCls = Class.forName(CLIENT_MANAGER_CLASS);
                Class<?> knownCls = Class.forName(KNOWN_PLAYER_CLASS);
                Class<?> teamCls = Class.forName(TEAM_CLASS);
                apiMethod = apiCls.getMethod("api");
                // 四个访问器在**嵌套接口** FTBTeamsAPI$API 上（1.20.1 自最早版起即如此）；
                // 万一某版把方法挪回外层类，下面的 fallback 仍能命中。
                Class<?> accessorCls;
                try {
                    accessorCls = Class.forName(API_IFACE_CLASS);
                    accessorCls.getMethod("isManagerLoaded");
                } catch (Throwable nestedMissing) {
                    accessorCls = apiCls;
                }
                isManagerLoaded = accessorCls.getMethod("isManagerLoaded");
                getManager = accessorCls.getMethod("getManager");
                isClientManagerLoaded = accessorCls.getMethod("isClientManagerLoaded");
                getClientManager = accessorCls.getMethod("getClientManager");
                arePlayersInSameTeam = managerCls.getMethod("arePlayersInSameTeam", UUID.class, UUID.class);
                getTeamForPlayerID = managerCls.getMethod("getTeamForPlayerID", UUID.class);
                getKnownPlayer = clientCls.getMethod("getKnownPlayer", UUID.class);
                getTeamByID = clientCls.getMethod("getTeamByID", UUID.class);
                knownPlayerTeamId = knownCls.getMethod("teamId");
                // 可选判据：缺失只降低 hasTeam 的精确度，不让整个后端失效。
                try {
                    teamIsPartyTeam = teamCls.getMethod("isPartyTeam");
                    teamIsServerTeam = teamCls.getMethod("isServerTeam");
                } catch (Throwable optionalMissing) {
                    teamIsPartyTeam = null;
                    teamIsServerTeam = null;
                }
                enabled = true;
                failureReason = "OK";
                LOGGER.debug("[Astral Dice] FTB Teams 队伍判定已接入");
            } catch (Throwable t) {
                enabled = false;
                failureReason = t.getClass().getSimpleName() + ":" + t.getMessage();
                // ⚠️ 区分「没装」与「装了但签名不符」：前者是绝大多数玩家的正常状态，
                //    打 WARN 会变成日志噪音；后者才是需要开发者关注的真问题。
                //    ClassNotFoundException = 入口类都不在 ⇒ 判定为「未安装」。
                //    真实状态始终记录在 why_ftb 里（reportBackends 的 INFO 机器行）。
                if (t instanceof ClassNotFoundException) {
                    LOGGER.debug("[Astral Dice] 未安装 FTB Teams，队友判定只用其余后端：{}", t.toString());
                } else {
                    LOGGER.warn("[Astral Dice] FTB Teams 在场但队伍判定接入失败（签名不符），退回其余后端：{}",
                            t.toString());
                }
            }
        }

        private static void disable(Throwable t) {
            if (enabled) {
                enabled = false;
                failureReason = "RUNTIME:" + t.getClass().getSimpleName();
                LOGGER.warn("[Astral Dice] FTB Teams 队伍判定已停用（运行时兼容性问题），退回其余后端", t);
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
     *
     * <p>✅ 2026-10-01 对发布产物 {@code open-parties-and-claims-fabric-1.20.1-0.31.6.jar}
     * 逐条 {@code javap} 核验：<b>五处签名全部吻合</b>，本后端未做改动。
     */
    private static final class Opac {
        private static final String API_CLASS = "xaero.pac.common.server.api.OpenPACServerAPI";
        private static final String MANAGER_CLASS = "xaero.pac.common.server.parties.party.api.IPartyManagerAPI";
        private static final String PARTY_CLASS = "xaero.pac.common.server.parties.party.api.IServerPartyAPI";

        private static volatile boolean resolved;
        private static volatile boolean enabled;
        private static volatile String failureReason = "NOT_RESOLVED";
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
                failureReason = "OK";
                LOGGER.debug("[Astral Dice] OPAC 队伍判定已接入");
            } catch (Throwable t) {
                enabled = false;
                failureReason = t.getClass().getSimpleName() + ":" + t.getMessage();
                // 与 FTB 同口径：「没装」静默（详见 Ftb.resolve 的说明）。
                if (t instanceof ClassNotFoundException) {
                    LOGGER.debug("[Astral Dice] 未安装 OPAC，队友判定只用其余后端：{}", t.toString());
                } else {
                    LOGGER.warn("[Astral Dice] OPAC 在场但队伍判定接入失败（签名不符），退回其余后端：{}",
                            t.toString());
                }
            }
        }

        private static void disable(Throwable t) {
            if (enabled) {
                enabled = false;
                failureReason = "RUNTIME:" + t.getClass().getSimpleName();
                LOGGER.warn("[Astral Dice] OPAC 队伍判定已停用（运行时兼容性问题）", t);
            }
        }
    }
}
