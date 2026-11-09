# P1 fabric 线 · 前置收敛（Puzzles 去依赖）专用会话 prompt
<!-- 历史记录：文中旧 shell 命令已于 2026-11-09 全部废弃，现用 bash 测试台（scripts/test/win/mt.sh）与 windows MCP，仅作溯源。 -->

> 用法：新会话第一段消息整段粘贴本文件「=== 以下为 prompt ===」之后的内容。
> 生成时间：2026-11-08；生成方：multi-dev-next 主会话（HEAD `cb57df4b`）。

---

=== 以下为 prompt ===

你是本仓（`F:\MCProject\astral_dice_multiloader-next`，分支 `multi-dev-next`）的资深 Minecraft 模组工程师。
本会话**只负责一件事**：fabric 线的第三方前置收敛——评估并把 Puzzles Lib（及其传递前置 Forge Config API Port）
从 fabric 依赖里去掉，把等价能力下沉到前置库 starengine（fabric 子项目）。先设计、后实施，动手前必须先向用户确认范围。

## 0. 先读（按顺序，不要跳）

1. 根 `AGENTS.md` → 认领 DSH 规则正本 `docs/agent/AGENTS-dsh.md`（Windows 原生口径；红线区必读）。
2. `docs/handover-2026-11-07-devnext-clean-session.md`（上一轮交接：位置/分支/版本/测试台口径/环境坑）。
3. `work/audit/fabric1211/J-puzzles-configport-audit.md`（275 行，本议题的**取证底稿**：依赖链实读、Puzzles 用法全公开 API、`testPuzzles` 开关缺陷、README 漂移）。
4. `fabric-1.21.1/src/main/java/com/merlinkitsune/astral_dice/platform/PuzzlesBridges.java`（300 行，13 个注册点全在这里，注释里带着逐条注入点的字节码取证）。
5. 需要改动的另一仓：`F:\MCProject\starengine_next`（分支 `next`，子项目 `fabric-1.21.1/`）——**前置库**，版本契约 `26.4-SNAPSHOT.1`，消费方区间 `>=26.4-SNAPSHOT.1 <27.0.0`；改库要按 `mc-prereq-lib-version-contract` 的流程走（bump + mavenLocal + 消费方引脚/区间同步），且「主版本号不变 ⇒ 禁止破坏性更新」。

## 1. 实读状态（截至 2026-11-08，自己复核一遍再动手）

- 分支 `multi-dev-next`，HEAD `cb57df4b`，**未推送 45 个提交**（默认不 push，需用户明确放行；远程走代理 `http://127.0.0.1:7897`，本分支不做 CurseForge）。
- 工作树：仅 `?? work/audit/` 与 `?? 立牌计划表.txt`（后者**禁止 add**）。
- `fabric-1.21.1/gradle.properties`：`mod_version=26.4-SNAPSHOT.6+fabric_1.21.1`、`starengine_version=26.4-SNAPSHOT.1`、
  `puzzleslib_version=21.1.66`（Modrinth `IV6k1yRp`）、`forgeconfigapiport_version=v21.1.6-1.21.1-Fabric`（`N5qzq0XV`）。
- 依赖声明：`fabric-1.21.1/build.gradle` 的 `modImplementation puzzles-lib` + 无条件 `modRuntimeOnly forge-config-api-port`（`testPuzzles` 开关已于 `cb57df4b` 移除）；`fabric.mod.json` 的 `depends` 只有 `puzzleslib`（**没有** FCAP，且不应加——它是 Puzzles 自己的硬前置）。

## 2. 本议题的事实底稿（不要重复审计，直接用）

**我们对 Puzzles 的用法 100% 是公开 API**：只 import 10 个类，全在 `fuzs.puzzleslib.api.event.v1.*`，`.impl./.internal.` 零引用；对 FCAP 的直接引用 **0 处**（`platform/config/ForgeConfigSpec.java` 是我们自写的 shim）。

**13 个注入点**（`PuzzlesBridges.java` 的 13 处 `EVENT.register`）：

| # | 我们派的 Forge 事件 | Puzzles 回调 | 原版注入点 |
|---|---|---|---|
| 1 | `LivingDamageEvent` | `LivingHurtCallback` | `LivingEntity#actuallyHurt` HEAD + `MutableFloat` |
| 2 | `LivingDeathEvent` | `LivingDeathCallback` | `LivingEntity#die` HEAD |
| 3 | `LivingDropsEvent` | `LivingDropsCallback` | `dropAllDeathLoot` TAIL（drops + looting 还原） |
| 4 | `LivingChangeTargetEvent` | `LivingChangeTargetCallback` | `Mob#setTarget` + `DefaultedValue<LivingEntity>` |
| 5 | `MobEffectEvent.Added` | `MobEffectEvents.APPLY` | `addEffect` STORE |
| 6 | `MobEffectEvent.Remove` | `MobEffectEvents.REMOVE` | `removeEffect` HEAD（可中断；含 null / 已不在身上两道守卫） |
| 7 | `MobEffectEvent.Expired` | `MobEffectEvents.EXPIRE` | `tickEffects` 迭代器移除处 |
| 8 | `MobEffectEvent.Applicable` | `MobEffectEvents.AFFECTS` | `canBeAffected` HEAD（ALLOW/DENY/PASS 三态） |
| 9 | `BlockEvent.BreakEvent` | `BlockEvents.BREAK` | `Block#playerDestroy` 链 |
| 10 | `PlayerEvent.Clone` | `PlayerCopyEvents.COPY` | `ServerPlayer` 克隆/`restoreFrom` |
| 11 | `EntityItemPickupEvent` | `ItemEntityEvents.TOUCH` | `ItemEntity#playerTouch` HEAD（**不能用 PICKUP**，那个点在入包之后、物品已清空） |
| 12 | `AnvilUpdateEvent` | `AnvilEvents.UPDATE` | `AnvilMenu#createResult`（`MutableValue`+`MutableInt`；**必须返回中断类结果才写回**） |
| 13 | `AnvilRepairEvent` | `AnvilEvents.USE` | `AnvilMenu#onTake`（`MutableFloat breakChance` 回写） |

**已存在、可复用/可上移的自写桥（7 个）**：`ProjectileImpactBridgeMixin`、`EnderPearlTeleportBridgeMixin`、
`ExplosionStartBridgeMixin`、`EntityDimensionTravelBridgeMixin` + `ServerPlayerDimensionTravelBridgeMixin`、
`ItemTossBridgeMixin`、`PlayerAttackBridgeMixin`、`LivingUseItemFinishBridgeMixin`。
其中 4 个在 Puzzles 里有语义相近的公开回调（`ProjectileImpactCallback` / `ExplosionEvents.Start` / `ItemEntityEvents.TOSS` / `AfterChangeDimensionCallback`），
但 `EntityDimensionTravel`（非玩家实体）与 `PlayerAttack`（Puzzles 那条带 `InteractionHand`，疑似客户端交互路径）**不可直接替换**。
`LivingHurtBridgeMixin`（`LivingHurtEvent`，`hurt()` 内）与 Puzzles 的 `LivingHurtCallback`（`actuallyHurt` HEAD）**本来就不是同一位置**，不可互换。

## 3. 你要做的事

**第一步（必须）：出设计稿，先给用户审，不要落盘代码。**
设计稿至少包含：
- 范围选项与影响面：**(a) fabric-1.21.1 + fabric-1.20.1 全踢**（两条注入点，1.20.1 方法名/字节码不同）、**(b) 只 1.21.1 试点**（1.20.1 保持 Puzzles 作对照组）。默认按 (b) 出方案。
- 每个注入点的 starengine 实现形态：目标类/方法、注入点（HEAD/TAIL/STORE/`@Redirect`/`@ModifyVariable`）、可变值类型、取消语义、与 Forge patch 位置的对照结论（**逐条 javap 取证，不要凭记忆**）。
- 需要新建的配套类型：`EventResult`（PASS/ALLOW/DENY/INTERRUPT+value）、`MutableFloat/MutableInt/MutableValue<T>/DefaultedValue<T>`。
- mixin 优先级与加载序：现在 Puzzles 是独立 jar，搬进 starengine 后与我们自有桥（`LivingHurtBridgeMixin`、`LivingEntityAbyssDarknessGateMixin`、7 个 bridge）的先后会变，必须重新标定并写明理由。
- 库侧版本与发布：starengine fabric 子项目的 mixin 配置、`build.gradle`、`fabric.mod.json`、版本 bump 路径。
- 回滚方案：保留旧路径的开关或分支策略。

**第二步（用户批准后）：实施 + 回归。**
- 本仓侧改动面很小：`PuzzlesBridges` → `StarengineBridges`（换 import/类型）、`build.gradle` 去两依赖、`fabric.mod.json` 去 `puzzleslib` depends。
- 验收判据：`./gradlew --no-daemon :fabric-1.21.1:compileJava --rerun-tasks` 通过；13 条链实机回归（伤害/死亡/掉落/索敌/效果×4/方块/克隆/拾取/铁砧×2）；`python tools/check_changelog_parity.py` OK；行尾口径复核。
- 提交纪律：一个可独立验证的单元一个本地提交，**不 push**。

## 4. 红线（违反即停手）

- 默认不起实机、不部署、不 commit/push（本地提交照「早提交勤提交」，push 需授权）。
- 写文件或跑 Gradle 前查 `F:/MCProject/.agent-locks/`；DSH 用 `dsh.lock`，一次一个文件写者。
- 发现别的实机占着 `25585`/`25565` 或在跑本仓 java ⇒ **搁置并报告**，不要 launch。
- 解释器写裸名（`bash` / `python`），旧 shell 已废弃。行尾：`docs/*.md`、`*.sh`、`*.py`、`README*.md` 用 LF；Java/json/gradle 用 CRLF（**注意**：`fabric-1.21.1/build.gradle` 工作树当前是 LF，动手前先实测再定，改完用 python 数字复核 bareLF/bareCR）。
- 不代改他人职责；`models/ images/ sound/` 属素材目录，出现不属于自己的改动立即停手上报。
- 只碰 fabric 线；neo 1.21.1 / forge 1.20.1 / neo 26.1.2 三线不动。

## 5. 环境坑（都踩过，别再踩）

- **网络**：`HTTPS_PROXY=http://127.0.0.1:7897` 对本机 HTTPS 已失效（schannel 握手失败/SSLEOF），`web_fetch` 也会 `fetch failed` ⇒ 取 Modrinth 用 `curl -sS --noproxy '*'`。
- **Gradle**：`./gradlew --no-daemon ... | tail` 更稳；带 daemon 时进程会挂住管道，要 `job_kill`；`> /tmp/x.log` 会被 MSYS2 路径转换搞坏（报 `Unable to access jarfile F;/...`），改用 `| tee`。
- **落盘**：`ops_write_file` 对本仓新建文件可能返回 `PUBLICATION_DENIED`（非 ACL）⇒ 退回 `bash` heredoc。
- **实机/测试台**：`bash scripts/test/win/mt.sh --version fabric-1.21.1 --side client --enable-autojs --phase launch`，收停必须 `--phase stop --force`。
- **AutoJS**：must `side:"server"`；Rhino 里没有 `Java.type`（用 `Java.loadClass`）、不认 `0.0f` 字面量、`getClass()` 不可用（用 `String(obj)`）。
- **Carpet 假人**：`AutoJS.command('player BT spawn')` → 实体名小写 `bt`，默认 CREATIVE ⇒ 先 `setGameMode(SURVIVAL)`，否则 `hurt()` 恒 false。jar 已在 `fabric-1.21.1/run/client/mods/fabric-carpet-1.21-1.4.147+v240613.jar`（保留）。
- **测试台收停缺陷已修**（`1110a2df`）：windows MCP 的 `Process(mode:"kill", pid=…)` //PID` 的 MSYS2 转换问题已改成 `参数转译开关='*' windows MCP 的 `Process(mode:"kill", pid=…)` /PID ...`。

## 6. 起步命令

```bash
cd /f/MCProject/astral_dice_multiloader-next
git rev-parse --abbrev-ref HEAD && git log --oneline -3 && git status --short
git rev-list --count origin/multi-dev-next..HEAD
grep -nE "puzzleslib|forgeconfigapiport|starengine" fabric-1.21.1/gradle.properties fabric-1.21.1/build.gradle
sed -n '1,60p' work/audit/fabric1211/J-puzzles-configport-audit.md
# 库侧
cd /f/MCProject/starengine_next && git rev-parse --abbrev-ref HEAD && ls fabric-1.21.1
```

## 7. 明确不要做的事

- 不要重复做「Puzzles 用法审计」——`J-puzzles-configport-audit.md` + `PuzzlesBridges.java` 注释已经是结论。
- 不要顺手改 README 前置表（那是另一件待办，等本议题定案后再一起改）。
- 不要把 `work/audit/**` 或 `立牌计划表.txt` 加进 git。
- 不要在没出设计稿、没拿到用户对 (a)/(b) 的裁决前写任何源码。

