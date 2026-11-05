# fabric-1.21.1 测试台可用性 + 实机计划表（2026-11-05 修订 v2）

> **本版按用户 2026-11-05 12:10 的四条裁决重写**（m08538）：① 饰品后端只做 Trinkets、Accessories 与 ACL 进不兼容列表；② 测试台 mod 限定 12 项；③ **先确保测试台可用**；④ 重新规划范围。
> 上一版（v1，P0–P9 全量规划）已被本版取代；v1 里仍成立的取证逐条并入本文件，不另存。

## §0 一页结论

| 项 | 现状（实读） | 本版裁决后的处置 |
|---|---|---|
| 产物 | `fabric-1.21.1/build/libs/astral_dice-26.4-SNAPSHOT.6+fabric_1.21.1.jar` = 2,994,223 B / 2331 条目 / 809 class | 不动 |
| 客户端 | **已跑通**（2026-11-05 12:59 首启、13:48 回退版复验、18:36 本轮 L0 取证）：超平坦世界、85 mods、iris 1.8.8 光影生效、截图三张（§10.2 / §12.3） | 阶段 B 只差用例化（L0 已补，见 §13） |
| 测试台 | **不具备跑第五线的能力**（版本表四线、fabric 车道常量写死 1.20.1、`mt_env.py` 无 fabric 分支） | **阶段 A：本阶段唯一目标 = 让它可用** |
| 饰品后端 | 代码里同时有 Trinkets / Accessories 双后端（11 文件 / 122 处引用） | **只保 Trinkets**；Accessories + ACL 进黑名单（§2） |
| 用例 | **L0 三条已落盘并实跑 PASS**（2026-11-05，§13）；其余 26 条为 0 | **3 / 29**；L1–L5 + 专属按手写 autojs 重写（§5） |
| 实机 | 阶段 B 的启动与 L0 已执行（用户已放行首启与用例跑动） | 阶段 C 的 L1–L5 仍等放行 |

**阶段顺序（裁决 3 + 4 的落点）**：A 测试台可用（改脚本 + 门禁 + env 接线，不启动游戏）→ B 首启与 L0 冒烟（实机）→ C 用例扩面与回归（实机）。

## §1 四条裁决落表

| # | 裁决 | 落点 |
|---|---|---|
| 1 | 饰品后端只做 Trinkets；Accessories / ACL 进不兼容列表 | §2 代码改动清单 + `ModCompatibilityCheck.BLACKLIST` 两条新增；`build.gradle` 去 accessories/cloth/owo |
| 2 | 测试台 mod 限定 12 项（不再需要 indium） | §4 E1 表；`gradle.properties` 删 debugify/modmenu/carpet/indium，新增 ferritecore/mezzconfig 钉版 |
| 3 | 先确保测试台可用 | §3 阶段 A（B1–B15）+ §6 计划表 |
| 4 | 重新规划范围 | 本文件整体结构；v1 的 P0–P9 拆成 A/B/C 三阶段，实机后置 |

## §2 饰品后端 = Trinkets only（含不兼容列表）

### 2.1 决策
- fabric-1.21.1 线**只支持 Trinkets**（1.21.1 上 Accessories 要 owo 库，兼容性灾难 —— 用户裁决原文）。
- `accessories` 与 `accessories_compat_layer` **无条件**拒绝启动（不走「星币钱包启用」这类条件门控）。
- **限制范围裁决**：只拦这两个 modId，不动其它模组；依据见 2.2。

### 2.2 事实（本轮实测）
- ACL 的 Modrinth slug = `accessories-compat-layer`，最新 `0.1.13`（1.21/1.21.1），**modId = `accessories_compat_layer`**；其 `fabric.mod.json` 的 depends = fabricloader >=0.16.10 / fabric-api * / minecraft ~1.21.1 / owo >=0.12.15.4+1.21 / accessories >=1.1.0-beta.53+1.21.1。
- 即 **ACL 硬依赖 Accessories 与 owo**：没有 Accessories 时 ACL 在 Loader 解析阶段就起不来。故 `accessories` 一条已足以阻断 ACL；仍单列 ACL 一条，是为了给玩家**指名**的拒绝文案，而不是让 Loader 抛「缺前置」。
- 已弃用的 `accessories-tc-layer`（Trinkets→Accessories 桥）不单列：同样硬依赖 Accessories，被 `accessories` 覆盖。

### 2.3 机制与代码改动清单（fabric-1.21.1 线）
既有机制（1.20.1 / 1.21.1 同源）：`init/ModCompatibilityCheck.java` 的 `private record Entry(String modId, String displayName, boolean onlyWhenWalletEnabled)` + `BLACKLIST`（现只有 `magic_coins` / `sg_economy`，两条均 `onlyWhenWalletEnabled=true`），`verifyOrThrow()` 在 `AstralDiceMod` 里调用。

| 文件 | 改动 |
|---|---|
| `init/ModCompatibilityCheck.java` | `BLACKLIST` 增 `new Entry("accessories", "Accessories", false)`、`new Entry("accessories_compat_layer", "Accessories Compatibility Layer", false)`；`verifyAccessoryProviderOrThrow()` 改为「Trinkets 必须存在」，Accessories-only 按黑名单优先拒绝 |
| `AstralDiceMod.java` | `:52` 校验点保留；`:92-100` 删 `isAccessoriesPresent()` 注册分支 |
| `compat/accessories/AccessoriesCompat.java`、`AccessoriesClientIconCheck.java` | 整包删除 |
| `compat/curios/TrinketBridge.java`、`CuriosApi.java` | 去掉 Accessories 分支 |
| `AstralDiceClient.java`、`client/gui/StarCoinWalletButtons.java`、`event/InvestigationArbitration.java`、`item/card/TemporaryCardUtil.java`、`trade/CardTraderTrades.java` | 去掉 Accessories 调用点 |
| `build.gradle` | 删 `accessories` / `cloth-config` / `owo` 三条 `modImplementation`；保留 `trinkets` |
| `gradle.properties` | 删 `accessories_version` / `cloth_config_version` / `owo_version`（若仅此线使用） |
| `src/main/resources/fabric.mod.json` | `recommends`/`depends` 里如有 accessories 一并移除；`breaks` 优先写 `{"accessories": "*", "accessories_compat_layer": "*"}`（Loader 层先拒，Java 层是二次保险） |

**判据**：① 装 `accessories` 启动 ⇒ 拒绝且文案含「Accessories」（不是 `NoClassDefFoundError`）；② 只装 `trinkets` ⇒ 正常进游戏；③ 产物 jar 内 grep `accessories` 命中 0；④ 顺序：先改 build/代码 → 阶段 A 构建 → 才允许实机。

## §3 阶段 A：测试台可用性（B1–B15，本阶段全部做完）

改造轴 = 把 `fabric-1.20.1` 这个**字面量**换成**参数**，不是复制一份新逻辑。

| # | 文件 | 现状 | 要做的事 |
|---|---|---|---|
| B1 | `scripts/test/lib/mt_paths.py` | `:26` 四线 VERSIONS；`:118` fabric 分侧 run 目录判断 | 增 `fabric-1.21.1`；fabric 侧判定改「以 `fabric-` 开头」 |
| B2 | `scripts/test/lib/mt_compat.py` | `:49,54-62` 版本表/子项目映射；`:69` fabric 组；`:175,189,195,242` 分支；`:748-788` 进程判据 | 同上；进程判据已按 `fabric.dli.env` 判侧，保持通用 |
| B3 | `scripts/test/lib/mt_ps.py` | `:49-62` VERSIONS；`:62` `_MARKER_ORDER` | 增线；**`fabric-1.21.1` 必须排在 `1.21.1` 之前**（子串陷阱） |
| B4 | `scripts/test/win/lib/common.sh` | `:26` 版本列表；`:38-62` 版本→子项目/run 目录 | 增线；fabric 判定改前缀 |
| B5 | `scripts/test/win/lib/fabric.sh` | `:30-31` `MT_FABRIC_VERSION/SUBPROJECT` 常量；`:41` `mt_is_fabric()`；`:90` run root；`:425` 用例 glob；`:554` 日志；`:659-661/728/880` | 常量改由 `--version` 现算；`mt_is_fabric()` 改前缀 |
| B6 | `scripts/test/win/phases.sh` | `:148` 分派；`:259-261` fabric 提前分流 | 前缀判定（native BLOCKED 只对 Neoforge/Forge，已核实） |
| B7 | `scripts/test/win/mt.sh` | `:22-23,55-56` 用法串；`:138-156` 守卫只认 `fabric-1.20.1` | 守卫改前缀；用法串补新线 |
| B8 | `scripts/test/win/launch.sh` | `:210-227` AutoJS artifact 名写死 + 重复即 BLOCKED | 按版本拼 artifact 名；判据保留 |
| B9 | `scripts/test/win/env.sh` | `:163-300` fabric 双 run 目录 + RCON | 前缀判定；RCON 段只对真有 RCON 的线生效 |
| B10 | `scripts/test/mt_env.py` | `:696-760` `cmd_mods` 无 fabric 分支；`:848` world fabric 判定等值 | 加 fabric 分支；world 判定改前缀 |
| B11 | `scripts/test/mt_capture.py` | `:91` 用法串只列四线（功能侧早已按 `mt_paths.VERSIONS` 工作）；`:249-251` 窗口标题回落 | 增线；`fabric-1.21.1` 回落成 `1.21.1`；**用法串已于 2026-11-05 补齐第五线**（提示文字，非功能） |
| B12 | `tools/check_lang_sync.py` | `:26` 只认四线 | 增第五线 |
| B13 | `tools/fetch_shaderpacks.py` | `:40-45` `RUN_DIRS` | 增 `fabric-1.21.1/run/client` |
| B14 | `tools/verify_fabric_assets.py` | `:140-147` 写死读 1.20.1；`:179-187` 版本不符**静默降级扫源码** | 按 `--subproject` 参数化；版本不符必须 **FAIL** |
| B15 | `tools/verify_sign_display_names.py` | `:18` 只三线（连 26.1.2 都没） | 补第五线或显式声明不适用并写原因 |

**阶段 A 完成判据**：
1. `mt.sh --version fabric-1.21.1` 的版本解析不抛 `KeyError`；
2. `python scripts/test/mt_preflight.py --version fabric-1.21.1` ⇒ 9 项全满足（不要求启动游戏）；
3. 四线回归：`mt_preflight.py --version <四线>` 仍全 OK（证明参数化没打坏老线）；
4. `verify_fabric_assets.py --subproject fabric-1.21.1 --jar <产物>` ⇒ PASS 且输出**不含**「改为扫描源码资源目录」；
5. 五线 `check_lang_sync.py` / `check_changelog_parity.py` / `verify_sign_display_names.py` 全绿。

## §4 运行期环境（E1–E10）

### E1 测试台 mod 表（裁决 2：12 项，去 indium）

| # | mod | Modrinth version id | 版本号 | 备注 |
|---|---|---|---|---|
| 1 | sodium | `u1OEbNKx` | mc1.21.1-0.6.13-fabric | iris 1.8.8 硬要求 0.6.x；0.8.13 见 §11/§12 |
| 2 | iris | `zsoi0dso` | 1.8.8+1.21.1 | 1.8.14-beta.1 在 dev 必崩（§11.4），已回退（§12） |
| 3 | immediatelyfast | `aLnBqxFW` | 1.6.15+1.21.1 | |
| 4 | lithium | `N08Z8wog` | mc1.21.1-0.15.4 | |
| 5 | jei | `r1Jkfnic` | 19.56.0.441 | **硬依赖 mezzconfig**；19.57+ 的 jar 声明 Loom 1.18.2 ⇒ 需 Gradle ≥9.7 + JVM 25，本线封顶 19.56 |
| 6 | jade | `adpNvTZS` | 15.10.6+fabric | 本线 `modImplementation`（mixin 需编译期引用） |
| 7 | ferrite-core | `sOzRw3CG` | 7.0.3-fabric | 本轮新钉（原无 1.21.1 钉版） |
| 8 | collective | `qSQaLB3S` | 1.21.1-8.77 | superflat 硬依赖 |
| 9 | superflat-world-no-slimes | `OHCdhqlM` | 1.21.1-3.7 | 史莱姆压制闸门 |
| 10 | forge-config-api-port | `N5qzq0XV` | v21.1.6-1.21.1 | puzzles-lib 硬依赖 |
| 11 | puzzles-lib | `IV6k1yRp` | 21.1.66 | |
| 12 | modernfix | `NnNX8LBn` | 5.25.1+mc1.21.1 | |

**不在 12 项里但缺了跑不起来的必需依赖**：`fabric-api 0.116.17+1.21.1 / Mys3P7lK`（基础）、`trinkets 3.10.0 / JagCscwi`（饰品后端唯一实现）、`mezzconfig 0.6.10 / VudGGZKL`（JEI 19.70 的 required 依赖，1.21.1 fabric 产物实测存在）。
**删除项**：`indium`（sodium 0.8 不再需要）、`debugify`、`carpet`（裁决 2 未列入）。
**表外例外（2026-11-05 追加裁决）**：`modmenu 11.0.5 / 6lgOkclV` + 硬依赖 `cloth-config 15.0.140+fabric / HpMb5wGb` 已装 —— 用户要求用 modmenu 验证「测试环境下 mod 安装情况」，属测试台工具而非玩法依赖（§11.7 有截图取证）。故本线实际加载 **85** mods。

### E2–E10（其余环境项，仍成立）
- E2 世界：超平坦、`allowCommands=1`、`keepInventory=true`、`mobGriefing=false`；fabric 分侧目录 ⇒ 世界写 `run/client/saves` 或 `run/server`，搬档前删 `session.lock`。
- E3 光影：`ComplementaryReimagined_r5.9.3.zip` 就位（`tools/fetch_shaderpacks.py`）。
- E4 前置库：`starengine-fabric-1.21.1:26.4-SNAPSHOT.1` 在 mavenLocal（已验证存在）。
- E5 AutoJS：`cc.sighs.autojs:AutoJS-fabric-1.21.1:1.0.0` 在 mavenLocal；`--enable-autojs` 时投放；端点 25585 与其它线互斥。
- E6 Loom 内嵌件：`python tools/loom_embedded_jars.py --subproject fabric-1.21.1 --side client --apply --clean`。
- E7 窗口模式：`fullscreen=false`，否则注入送不到。
- E8 端口：RCON 缺省 25575（fabric 车道），与 1.20.1 线不能同时占用。
- E9 日志：`scripts/test/win/logs/fabric-1.21.1-<ts>.log`。
- E10 资产门禁：`verify_fabric_assets.py` 必须显式 `--subproject`，禁止静默降级（B14）。

## §5 用例范围（Trinkets-only 后收敛）

| 批次 | 内容 | 条数 |
|---|---|---|
| L0 | 启动/就绪/注册面（BOOT-READY、JAR-ASSETS、MODLIST）—— **2026-11-05 已落盘并实跑 PASS，见 §13** | 3 |
| L1 | 核心玩法冒烟（骰子/卡牌/立牌） | 6 |
| L2 | 事件与战斗（伤害链、破绽闪避、免疫） | 4 |
| L3 | **饰品后端（Trinkets only）**：`TRINKET-BACKEND`、`TRINKET-SLOTS`（骰子/立牌/筹码可装入且槽位数正确）、`INCOMPAT-ACCESSORIES`（装 accessories ⇒ 启动被拒且文案指名） | 3 |
| L4 | GUI / 同步 / 存档 | 6 |
| L5 | 渲染与视觉（含光影） | 5 |
| 专属 | `AUTOJS-CHANNEL`、`SYKESTENTACLE-ANIM` | 2 |
| 合计 | | **29** |

v1 的 `ACCESSORIES-ONLY` / `DUAL-BACKEND` 两条**删除**（后端只剩一个，无对照）。

## §6 计划表（A / B / C 三阶段）

| 阶段 | 内容 | 出口判据 | 预算 |
|---|---|---|---|
| **A 测试台可用** | B1–B15 + E1 mod 装配接线 + §2 的 Trinkets-only 代码改动 + 五线构建 | §3 五条判据全绿；`./gradlew build -x test` BUILD SUCCESSFUL | 3–5h |
| B 首启与 L0 | `runClient` 首启、世界就绪、L0 三条 | 3 条 PASS、日志无 `astral_dice` 帧异常 | 40–60min |
| C 用例扩面 | L1–L5 + 专属 + 另四线回归 | 29 条按批次 PASS；四线回归不退化 | 3–4h |

全程串行（25585 / 25565 互斥），每轮结束 `bash scripts/test/win/mt.sh --version <线> --phase stop --purge-saves`。

## §7 风险
- R1–R12（v1 保留）：进程判据、AutoJS 双份、LWJGL 钉版、分侧目录、端口互斥、世界锁、光影包、Loom 内嵌件、门禁假绿、用例 glob、日志路径、收停残留。
- **R13 参数化打坏老线**：前缀判定可能让老线匹配变宽。判据 = 阶段 A 第 3 条（四线 preflight 回归）。
- **R14 删 Accessories 时残留引用**：11 文件 122 处，删漏一处即编译失败或运行期 `NoClassDefFoundError`。判据 = `grep -rn accessories fabric-1.21.1/src` 命中 0 + `compileJava` 非 UP-TO-DATE。

## §8 记录位置
- 本文件：范围与判据。
- `docs/features/devnext-bash-testbed-decisions.md`：测试台技术决策与实机读数（追加节）。
- `docs/features/fabric-1211-port-feasibility-plan.md`：移植阶段记录（§14 已落）。

## §9 仍未决（收敛后仅 2 条）
1. `fabric-1.21.1` 是否进发布矩阵 / CI（发布侧，不影响阶段 A）。
2. 阶段 B 的实机放行时间（用户明确放行后才启动游戏）。

## §10 阶段 A 验收记录（2026-11-05 12:46–13:01，Fabric 1.21.1 客户端首次真正进世界）

### 10.1 缺口与处置（按出现顺序）

| # | 症状 | 真因（取证） | 处置 |
|---|---|---|---|
| 1 | `InjectionError: Redirector astralDice$suppressVanillaArmorBar … (0/1) succeeded. Scanned 0 target(s)` | 1.21.1 把护甲条绘制从 `Gui#renderPlayerHealth` 拆到新的 `private static Gui#renderArmor(GuiGraphics, Player, IIIII)`；`Player#getArmorValue()I` 唯一调用点在该静态方法内（`javap -p -c net.minecraft.client.gui.Gui`） | `mixin/client/ArmorBarCancelMixin.java` 注入目标改 `renderArmor`，handler 改 `private static`（Mixin 只允许「静态 handler → 实例方法」，反向非法） |
| 2 | `NoClassDefFoundError: io/github/fablabsmc/fablabs/api/fiber/v1/schema/type/derived/ConfigType`（栈顶 puzzleslib `FabricClientEventInvokers.registerLoadingHandlers`） | patchouli 的 JiJ 依赖（fiber 等 7 件）由 Loom 剥离，客户端侧此前只给 server 侧补过 | `python tools/loom_embedded_jars.py --project fabric-1.21.1 --side client --apply --hosts-from fabric-1.21.1/build/loom-cache/argFiles/runClient` ⇒ `written=7` |
| 3 | `MixinApplyError: mixins.iris.json:MixinLevelRenderer … InvalidMixinException: Unexpected NullPointerException parsing @ModifyArg` | iris 1.8.14-beta.1 的 `iris$writeRainAndSnowToDepthBuffer` 注解目标只写 intermediary 名（`class_761;method_22714`，无描述符），fabric-loader dev 重映射器 `unmapDesc` 拿到 null ⇒ NPE；iris 1.8.8/1.8.7 写的是带描述符的 named 目标，dev 可跑 | 降到 **iris 1.8.8** + **sodium 0.6.13**（iris 1.8.8 的 `fabric.mod.json` 硬要求 `sodium: 0.6.x`；1.8.14-beta.1 才配 0.8.x）。这是 1.21.1 上 dev 唯一可跑组合 |
| 4 | `LAUNCH: WORLD_READY` 打了但 AutoJS 读到 `player:false / dim:none`，日志无 quickplay 字样 | 客户端停在 **`AccessibilityOnboardingScreen`**（辅助功能引导页）。`fabric-1.21.1/run/client/options.txt` 是测试台手工生成的空壳（只有 fullscreen/pauseOnLostFocus/两个 keybind），缺 `onboardAccessibility:false` ⇒ 首次进游戏必走引导页，quickplay 静默不执行 | 用 `run/1.21.1/options.txt`（同为 MC 1.21.1）补齐并置 `fullscreen:false / pauseOnLostFocus:false / onboardAccessibility:false`；同时把 `scripts/test/mt_env.py` 的 `set_fullscreen_disabled()` 改成**新建 options 时一并写 `onboardAccessibility:false`** |
| 5 | `Failed to load options: NumberFormatException: For input string: "o:CONTROL"`（`Options.processOptions → InputConstants.getKey`） | NeoForge 线会把带修饰键的绑定写成 `key_key.jei.toggleOverlay:key.keyboard.o:CONTROL`；原版/fabric 的解析器对第二个冒号后段做 `Integer.parseInt` ⇒ 整份 options 放弃加载 | 删掉 11 条这类行；`mt_env.py` 的 `set_fullscreen_disabled()` 增加过滤（`key_` 开头且含 ≥2 个冒号的行直接丢弃，游戏内以默认绑定重建） |

> 教训：`LAUNCH: WORLD_READY` 的 marker 是 `Sound engine started` —— 它在**启动期**就会打印，**不能证明进了世界**。判据必须是 AutoJS 读 `Minecraft.getInstance().player != null && level != null`。

### 10.2 验收读数（2026-11-05 12:59:35 那轮，日志 `scripts/test/win/logs/fabric-1.21.1-20261105-125935.log`）

| 判据 | 读数 |
|---|---|
| 启动 | `Loading 83 mods:` 通过；`LAUNCH: READY` + `WORLD_READY`，`game_pid=81108` |
| 真正进世界 | AutoJS client：`player=true`、`dim=minecraft:overworld`、`hp=20.0`、`screen=null`、`fps≈118`、`dayTime=135` |
| 光影 | `(Iris) Using shaderpack: ComplementaryReimagined_r5.9.3.zip`；ImmediatelyFast 报 `Found Iris 1.8.8+mc1.21.1. Enabling compatibility.` |
| options | `Failed to load options` 计数 **0**（修前 1） |
| 本模组 | `AP_FAB_ACCESSORY_PROVIDER: OK (trinkets=true accessories=false acl=false)`、`AP_FAB_ATTACHMENTS: 附件键已注册 119 个`、`Astral Dice mod loaded.`、Jade 插件加载成功 |
| 实体与动画 | client 侧 `sendCommand("summon astral_dice:sykestentacle")` ⇒ 实体渲染正常（GeckoLib 动画姿态 + 地面装饰贴图）；截图 `fabric-1.21.1/run/client/screenshots/2026-11-05_13.00.28.png` |
| 测试台 | `MT_PREFLIGHT: OK — 9 项全部满足（fabric-1.21.1）`；`MT_DEBUG: pause-lock=on / keybinds=OK / shaders=enableShaders=true`；`MT_CAPTURE: OK`（`--mode f2`） |
| 收停 | `WIN_STOP: OK — 四线范围内无本 repo 的 Minecraft 客户端` |

### 10.3 仍未收口的点
1. **`--mode window` 对 GL 窗口只能抓到白屏**（`PrintWindow` 拿不到 OpenGL 后缓冲）⇒ fabric 线视觉取证一律用 `--mode f2`（游戏内 F2 落盘 + `.mt_shots.json` 世代映射）。
2. **用例文件仍为 0**：`scripts/test/cases/*-fabric-1.21.1.json` 尚未编写，阶段 C 的 29 条待写。**（已被 §13 取代：L0 三条已落盘，剩 26 条。）**
3. `MT_CAPTURE` 打出的 run_id 沿用旧世代（`20260921-191522`），不影响 F2 实际落盘路径，但阶段 C 前应核一次 `.mt_active_run` 的生成逻辑。


## §11 sodium / iris 升到最新 + modmenu 接入（2026-11-05 13:38，用户裁决）

### 11.1 用户原话与解释

> 需要在fabric环境中补充modmenu模组（以用于验证测试环境下mod安装情况）。另外fabric-1.21.1的sodium版本不是0.8.13，iris不是1.8.14-beta。需要将这2个mod更新到最新版本

即：把 §10.1-3 里为绕开 dev 崩溃而临时降下来的 **sodium 0.6.13 / iris 1.8.8** 升回 1.21.1 的 Modrinth 最新公开版，并新增 modmenu（含其硬依赖 cloth-config）。

### 11.2 钉版（Modrinth 实读，经代理 127.0.0.1:7897）

| mod | 版本 | version id | 说明 |
|---|---|---|---|
| sodium | `mc1.21.1-0.8.13-fabric` | `SMxNOGZ6` | 2026-08-28 release，1.21.1 fabric 最新 |
| iris | `1.8.14-beta.1+1.21.1-fabric` | `bAo1Qhte` | Modrinth version_type=release；required 依赖 sodium |
| modmenu | `11.0.5` | `6lgOkclV` | required 依赖 fabric-api + cloth-config |
| cloth-config | `15.0.140+fabric` | `HpMb5wGb` | modmenu 硬依赖，必须同批装 |

落点：`fabric-1.21.1/gradle.properties`（`sodium_*` / `iris_*` 改写，新增 `modmenu_*` / `clothconfig_*`）、`fabric-1.21.1/build.gradle` testMods 块（iris 单独一档 `testIris` 开关，其余九项 + modmenu + cloth-config 同组）。

### 11.3 §10.1 第 3 条已被本节取代

`iris 1.8.8 + sodium 0.6.13` 不再是钉版组合；保留在 §10.1 只作历史。新的组合是「最新钉版 + dev 默认关 iris」（见 11.5）。

### 11.4 iris 1.8.14-beta.1 在 Loom dev 下必崩（复现与取证）

- 崩溃报告：`fabric-1.21.1/run/client/crash-reports/crash-2026-11-05_13.39.57-client.txt`
- 链路：`Could not execute entrypoint stage 'client' … provided by 'sodium' at 'net.caffeinemc.mods.sodium.fabric.SodiumFabricMod'` → `ExceptionInInitializerError at net.fabricmc.fabric.api.renderer.v1.material.BlendMode.<clinit>` → `Mixin transformation of net.minecraft.client.renderer.LevelRenderer failed` → `MixinApplyError: Mixin [mixins.iris.json:MixinLevelRenderer from mod iris] … FAILED during APPLY` → `InvalidMixinException: Unexpected NullPointerException parsing @ModifyArg annotation on iris$writeRainAndSnowToDepthBuffer [INJECT_PREPARE_LEGACY … -> Parse -> Parse Selectors]`
- 根因：iris jar 常量池 #436 = `Lnet/minecraft/class_761;method_22714`，**方法引用不带描述符**；dev 的 intermediary→named 重映射器在 desc=null 处 NPE。`iris.refmap.json` 本身完整（含 `Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V`）⇒ 是**注解侧缺描述符**，不可逆。
- 判定：**dev 环境限定的上游缺陷**，与本模组无关；生产形态（jar 直装、无 dev 重映射）不受影响。可绕路径已排除：Loom 1.17.21 / Gradle 9.5.1 / mixin 0.8.7 均已是当前上限（抬 Loom 1.18.2 需 Gradle ≥9.7 + 构建 JVM 25，见移植计划 §6）。

### 11.5 两道开关

| 层 | 开关 | 默认 | 语义 |
|---|---|---|---|
| gradle | `fabric-1.21.1/build.gradle` testMods 块 `testIris` | 开（`!= 'false'` 即装） | 关掉即不把 iris 放进运行期 classpath |
| 编排 | `scripts/test/win/launch.sh` 环境变量 `MT_TEST_IRIS` | **`false` ⇒ 追加 `-PtestIris=false`** | 只对 fabric 线生效；`MT_TEST_IRIS=true` 才带 iris；其它值报用法错误 exit 2 |

设计意图：**钉版保持最新（用户要求），但 dev 测试台默认不装 iris**，否则每次 `launch.sh` 都会崩在 Mixin 应用阶段。需要复现光影/iris 侧问题时显式 `MT_TEST_IRIS=true`。

### 11.6 验收读数（2026-11-05 13:41 与 13:43 两次启动，均走新开关默认值）

- 第一次（手工 `MT_GRADLE_PRE_ARGS=-PtestIris=false`）：`LAUNCH: READY` + `LAUNCH: WORLD_READY`，`game_pid=171236`，日志 `scripts/test/win/logs/fabric-1.21.1-20261105-134152.log`。
- 第二次（**不再手工传参**，验证 launch.sh 新默认）：命令行实读 `cmd //c gradlew.bat :fabric-1.21.1:runClient --console=plain -Pquickplay=testworld -PautojsTestMod=1 -PtestIris=false`，`LAUNCH: READY` + `LAUNCH: WORLD_READY`，`game_pid=172328`，日志 `scripts/test/win/logs/fabric-1.21.1-20261105-134329.log`。
- 两轮 `Loading 84 mods:` 实测：**sodium 0.8.13+mc1.21.1**、**modmenu 11.0.5**、**cloth-config 15.0.140**、jei 19.56.0.441、trinkets 3.10.0、lithium 0.15.4+mc1.21.1、ferritecore 7.0.3、immediatelyfast 1.6.15+1.21.1、modernfix 5.25.1+mc1.21.1、collective 8.77、superflatworldnoslimes 3.7；iris 如期不在列表。
- `InvalidMixinException|MixinApplyError|Failed to load` 命中 **0**；`Reference map 'astral_dice.refmap.json' … could not be read` 属 dev 常规告警。
- AutoJS client：`FabricLoader.getInstance().getAllMods().size()` = **84**，`screen` = null（在世界内）。

### 11.7 modmenu 用途验证（用户目的是「验证测试环境下 mod 安装情况」）

- 用 AutoJS 客户端侧 SetScreen 打开 `com.terraformersmc.modmenu.gui.ModsScreen(null)`，`Minecraft.getInstance().screen` 回读为 `com.terraformersmc.modmenu.gui.ModsScreen@…`。
- 截图：`fabric-1.21.1/run/client/screenshots/2026-11-05_13.44.08.png`（`MT_CAPTURE: OK`，mode=f2，attempt=1）。界面实测：标题「模组」、计数「当前显示 18 个模组和 14 个前置模组」、列表首项 `antlr4-runtime 前置 v4.13.1`、第二项 **Astral Dice**（描述为 Fabric 1.21.1 移植线文案）、其后 AutoJS / Cardinal Components API / Cloth Config v15 / Collective / core / Fabric API / Fabric Loader / FerriteCore / fiber / Forge Config API Port …，并显示「将文件拖放至此窗口以添加模组」「打开模组文件夹」。
- 口径：modmenu 只列**用户可见模组 + 前置**，fabric-api 的数十个子模块不计入计数，所以 18+14 ≠ 84；这是 modmenu 的正常收窄，不是缺件。

### 11.8 未收口

1. **光影渲染取证**：dev 下 iris 不可用 ⇒ 只能在生产形态（jar 直装）或临时回退 iris 1.8.8 + sodium 0.6.13 时验证。取哪条路径待用户裁决。
2. modmenu 目前只作人工/截图验证；若要进用例，需要新增 autojs 通道的 `*-fabric-1.21.1.json`（当前该线用例仍为 0 条）。

## §12 裁决「回退」：sodium/iris 回到 dev 可跑配对（2026-11-05 13:46，同轮收尾）

### 12.1 裁决与解释

用户在 §11 交付后回复「回退」⇒ 取 §11.8 的选项 ②：**临时回退 iris 1.8.8 + sodium 0.6.13**，保住 dev 环境的光影取证能力；modmenu / cloth-config 保留（与 iris 无关）。

### 12.2 落盘改动

| 文件 | 改动 |
|---|---|
| `fabric-1.21.1/gradle.properties` | `sodium_version=mc1.21.1-0.6.13-fabric` / `sodium_mr_version=u1OEbNKx`、`iris_version=1.8.8+1.21.1` / `iris_mr_version=zsoi0dso`（= 提交 `59f58b99` 里的原值，回退即恢复这一组）；注释改写为「已升过一次最新版 → 实测 dev 必崩 → 用户裁决回退」，并留上游缺陷的完整记述 |
| `scripts/test/win/launch.sh` | `MT_TEST_IRIS` 默认值由 `false` 改回 **`true`（即默认装 iris，不再自动追加 -PtestIris=false）**；仅显式 `MT_TEST_IRIS=false` 才排除。注释改为指向 §11 的取证 |

`bash -n scripts/test/win/launch.sh` OK；行尾 CRLF 0 / bareLF 522 / bareCR 0。

### 12.3 验收读数（2026-11-05 13:46–13:48，三次启动）

| # | 目的 | 读数 |
|---|---|---|
| 1 | 回退后首启 | 命令行 `cmd //c gradlew.bat :fabric-1.21.1:runClient --console=plain -Pquickplay=testworld -PautojsTestMod=1`（**无** -PtestIris），`LAUNCH: READY` + `LAUNCH: WORLD_READY`，`game_pid=73324`，日志 `scripts/test/win/logs/fabric-1.21.1-20261105-134634.log`：`Loading 85 mods:` 含 **iris 1.8.8+mc1.21.1** / **sodium 0.6.13+mc1.21.1** / modmenu 11.0.5 / cloth-config 15.0.140；`(Iris) Using shaderpack: ComplementaryReimagined_r5.9.3.zip`；`InvalidMixinException|MixinApplyError|Failed to load` = **0** |
| 2 | 顺带查世界 | 发现当时 `saves/testworld` 是 **`minecraft:noise`**（level.dat 内 flat=0 / noise=5）⇒ 违反超平坦规范，已 `python scripts/test/mt_env.py world --version fabric-1.21.1` 重建（`MT_WORLD: OK — generator=minecraft:flat`；注意该子命令**没有** `--force`，传了会 `MT_ERROR: 未知参数 --force` exit 2） |
| 3 | 回退 + 平坦世界复验 | `LAUNCH: READY` + `LAUNCH: WORLD_READY`，`game_pid=79588`，日志 `scripts/test/win/logs/fabric-1.21.1-20261105-134750.log`：`Loading 85 mods:` + `Using shaderpack: ComplementaryReimagined_r5.9.3.zip`，错误计数 0；AutoJS client `fps=119`、`screen=null`、`dim=minecraft:overworld`、`held=1 astral_dice:*` |

截图（均 `MT_CAPTURE: OK`，mode=f2）：
- `fabric-1.21.1/run/client/screenshots/2026-11-05_13.47.04.png` —— 回退后含光影（当时世界仍是 noise 地形，湖水/植被正常上光）。
- `fabric-1.21.1/run/client/screenshots/2026-11-05_13.48.18.png` —— 重建超平坦后：日出天光、大气雾、远景村庄剪影、平滑草地，光影链路完整。

### 12.4 与 §11 的口径关系（不要混读）

- §11.5/§11.6 记录的「dev 默认 `-PtestIris=false`」**已被本节 12.2 覆盖**：现行默认是**装 iris**。
- §11 的上游缺陷记述（iris 1.8.14-beta.1 在 dev 必崩、refmap 完整但注解缺描述符）**仍然成立**，现在它是「为什么必须停在 1.8.8」的依据。
- §11.7 的 modmenu 接入与验证不受影响（截图 `2026-11-05_13.44.08.png`）。

### 12.5 遗留

1. 升级到 iris 0.8.x/sodium 最新只在**生产形态**可行；若哪天要在 dev 里验 iris 新版，必须先解决其注解缺描述符问题（只能等上游，或自己重打 jar 补描述符）。
2. `fabric-1.21.1` 用例仍为 0 条（`scripts/test/cases/*-fabric-1.21.1.json`），光影/modmenu 目前都是人工读数 + 截图取证。**（已被 §13 取代：L0 三条已落盘；光影仍是人工 + 截图。）**
## §13 L0 三条用例落盘与实跑（2026-11-05 18:3x，m09832 指令 ①）

### 13.1 三条用例（`scripts/test/cases/`，均 CRLF、裸 LF 0）

| case_id | channel | side | 判据 |
|---|---|---|---|
| `BOOT-READY-fabric-1.21.1` | 日志判据（默认） | client | 4 条 log + crash + mixin：`Astral Dice mod loaded\.` / `May the god of the dice be with you!` / `Sound engine (started\|finished)` / `AP_FAB_ACCESSORY_PROVIDER: OK \(trinkets=true accessories=false acl=false\)`；log 断言 **scope=whole** |
| `JAR-ASSETS-fabric-1.21.1` | 离线（不启动游戏） | server | `op=jar_stats` + `type=jar`：models≥158 / recipes≥125 / trinkets≥8 / entries≥2315 / embed≤0 |
| `MODLIST-fabric-1.21.1` | **autojs** | client | `mcp_status` + `eval_js` 读 `FabricLoader.getInstance().getAllMods()` ⇒ `{count, missing, forbidden, astral}`，4 条 `state` 断言：count≥80 / missing 长度 0（17 项必需 id）/ forbidden 长度 0（`accessories`、`accessories_compat_layer`）/ astral=true |

相对 `BOOT-READY-fabric-1.20.1` 的两处**有意差异**：① 本线不装 KubeJS ⇒ 去掉 `kubejs` 断言（该断言在本线读不到日志会恒真，属假绿）；② 追加饰品后端判据行（对应 §2 的 Trinkets-only 裁决）。

门禁读数：`python scripts/test/lib/mt_case.py validate --cases-dir scripts/test/cases --version fabric-1.21.1` ⇒ `MT_JUDGMENT: with_judgment=2 without=1` / `MT_CASE_VALIDATE: files=3 errors=0`。

### 13.2 两个运行器缺陷（本轮实测踩出，已修）

1. **顶层 `assert`（单数）被静默忽略**：python 运行器只执行 `doc["asserts"]`（复数，`scripts/test/lib/mt_case.py:592 _asserts()`），写成 `assert` 的用例**断言一条都不跑**，而 `validate_case_strict`（`:302` 只看 `asserts`）也不报错 ⇒ 静默 PASS。存量 **17** 个用例中招（全在 fabric-1.20.1 线：`BOOT-READY-fabric-1.20.1`、`CARD-CHIP-UI-fabric-1.20.1`、`CURIOS-SLOT-SIZING-fabric-1.20.1`、`JAR-RESOURCE-fabric-1.20.1`、`LT-*-fabric-1.20.1` 等）。fabric 内嵌运行器两种键都收（`scripts/test/win/lib/fabric.sh` 内 `:263` 有注释），所以那条线侥幸没红。
2. **fabric 内嵌 cases 运行器两处能力缺口**（`scripts/test/win/lib/fabric.sh` 的 heredoc python，不是 `mt_case.py`）：
   - `jar_stats()` 只数 `data/astral_dice/recipes/`，1.21+ 的 `recipe/` 不数 ⇒ `JAR-ASSETS` 的 recipes 断言恒 0、恒 FAIL。
   - 没有 AutoJS 客户端通道 ⇒ 遇 `eval_js / mcp_status / await_state` 一律判 BLOCKED ⇒ `MODLIST` 走编排入口必 BLOCKED。
   修法（已落盘）：recipes 双前缀计数（与 `mt_paths.RECIPES_PREFIXES` 同口径）；`channel=autojs` 的条目转交 `mt_case.py run --case … --run-dir <sub>/run/<side>`，并按退出码映射（0→OK / 11→BLOCKED / 其它→FAIL）。修前实测：`JAR-ASSETS` FAIL、`MODLIST` BLOCKED；修后三条全 OK。

### 13.3 正确命令行（本轮实跑过）

```bash
# 1) 起客户端（注意 --wait-world 会吞下一个参数；--side client 必须显式给）
bash scripts/test/win/launch.sh --version fabric-1.21.1 --side client --enable-autojs --wait-world 600 --timeout 900

# 2) 单条跑（编排入口；缺 --side client 时 fabric 默认 side=server，客户端条目会 BLOCKED）
bash scripts/test/win/mt.sh --version fabric-1.21.1 --phase cases --side client --case scripts/test/cases/MODLIST-fabric-1.21.1.json

# 3) 收停
bash scripts/test/win/mt.sh --version fabric-1.21.1 --phase stop --force
```

**整批不放行**：`--phase cases` 不带 `--case` 会被 `MT_AUTO_DISABLED` 拦下（纯手动下达命令规则），需要时显式 `--allow-auto`。

### 13.4 本轮实跑读数（日志 `scripts/test/win/logs/fabric-1.21.1-20261105-183615.log`）

| 判据 | 读数 |
|---|---|
| 启动 | `LAUNCH: READY` + `LAUNCH: WORLD_READY`，`game_pid=156920`，AutoJS client `available=true` |
| BOOT-READY | 6 条断言 PASS（4 log hit=True + crash PASS + mixin PASS） |
| JAR-ASSETS | `jar=astral_dice-26.4-SNAPSHOT.6+fabric_1.21.1.jar models=158>=158 recipes=125>=125 trinkets=8>=8 entries=2315>=2315 embed=0<=0` PASS |
| MODLIST | `count=85 / missing=[] / forbidden=[] / astral=true`，4 条 state 全 PASS |
| 编排入口复跑 | 三条各 `MT_CASES: OK (1–2s)` |
| 老线回归 | `fabric-1.20.1` 的 `JAR-ASSETS` 仍 `OK`（recipes 双前缀计数没打坏 1.20.1） |
| 收停 | `WIN_STOP: OK` / `MT_STOP: OK` |

### 13.5 遗留

1. 存量 **16** 个单数 `assert` 用例未改（机械改键名即可，但属另一批 fabric-1.20.1 用例，本轮未动）。
2. L1–L5 + 专属共 **26** 条未写（估 6–9h，需按 autojs 重写，kubejs 用例不可复用）。
3. **E8 RCON 仍未验证**（`env.sh` 的 RCON 段只探第一条 fabric 线）。
4. `JAR-ASSETS` 里 `bountiful=4` 是自 1.20.1 带过来的死数据（本线不装 Bountiful），未清、也不设判据。


## 14. fabric-1.21.1 用例全量落地与回归（2026-11-05 夜）

本节收口 §13.5 遗留第 1、2 条：存量单数 `assert` 已清、L1–L5 + 专属 26 条已写并跑绿，**29 条用例全 PASS**。

### 14.1 用例清单（`scripts/test/cases/*-fabric-1.21.1.json`，29 个文件）

| 批次 | 用例 | 取证要点 |
|---|---|---|
| L0 | BOOT-READY / JAR-ASSETS / MODLIST | 启动行 + 产物条目/配方/饰品数 + 已装模组集合（§13） |
| L1 | DICE-ROLL / CARD-USE / SIGN-EQUIP / WALLET-STARCOIN / CRAFT-CHIP | 骰子契约与自动装槽、卡的最大堆叠/耐久条、立牌是 stand 槽饰品（不是方块）、星币面额与账本、chip_shop 真合成 |
| L2 | EFFECT-APPLY / DAMAGE-CHAIN / DODGE-BREAK / ENDERPEARL-IMMUNITY / EFFECT-STACK | 效果施加、易伤 ×1.10、moses 闪避反制、珍珠 20 tick fall 免疫、charge 上限 20 |
| L3 | TRINKET-BACKEND / TRINKET-SLOTS / INCOMPAT-ACCESSORIES | Trinkets-only 后端、槽位上限、`breaks` 声明与不兼容拒绝 |
| L4 | CARD-CHOICE-UI / CHIP-SHOP-UI / CHIP-SHOP-BUY / SIGN-ACTIVE-LOCK / ATTACHMENT-PERSIST / MENU-SYNC | 选卡界面、筹码商店 UI/购买、立牌主动锁定状态机、附件持久化、菜单同步 |
| L5 | RENDER-SHADER-ACTIVE / RENDER-SIGN-TRANSLUCENT / RENDER-HUD-ASTRAL / RENDER-PARTICLE / RENDER-TOOLTIP-FRAME | 光影生效、立牌半透明、战斗 HUD、自定义粒子、tooltip 取色 |
| 专属 | AUTOJS-CHANNEL / SYKESTENTACLE-ANIM | AutoJS 端点原语、GeckoLib 触手实体召唤/动画/掉血 |

### 14.2 Rhino / AutoJS 互操作硬事实（写用例必读，本轮实测）

1. **反射一律不可用**：`getDeclaredMethods` / `getDeclaredFields` / `setAccessible` / `invoke` 全报 `Java class ... has no public instance field or method named getDeclaredFields`；`Packages.java.lang.Class.forName` 兜底同样不可用。
2. **`obj.getClass()` 不可用**（`TypeError: Cannot find function getClass`）⇒ 类名判定一律 `String(obj)`（得 `类名@hash`）。
3. **包名前缀全局不解析**（`net.minecraft.client.Minecraft.getInstance()` 直接抛错）⇒ 所有类都走 `Java.loadClass(...)`。
4. **`eval_js` mode=json 包成 `JSON.stringify((<code>))`** ⇒ 代码必须是**单个表达式**（多语句包 IIFE），且**裸字符串返回值恒为 null**，只能返回对象/数组。
5. `mt_autojs.AutoJs.eval_js(self, code, side="auto", mode="raw", timeout_ms=None)` —— 第一个位置参数是 **code**。
6. `InteractionResultHolder` 上不能直接调 `consumesAction()` ⇒ 写 `xxx.getResult().consumesAction()`。
7. JS 里写 `10.0F` / `3.0F` 这类 Java float 字面量会报 `EvaluatorException: missing ) after argument list` ⇒ 去掉 `F` 后缀。
8. **跨 eval 传状态用 `AutoJS.write(path, text)` / `AutoJS.read(path)`**（实测可用）；1.21.1 已无 `getPersistentData`。
9. **`bytecode` / `bytecode_absent` 断言匹配的是产物 jar 条目名**（正则，`mt_case.py:748-762`），不是字节码内容。
10. **mixin 类不能 `Java.loadClass`**：直接加载 `SignTranslucencyMixin` 会抛 `RuntimeException: Mixin transformation of ... failed` ⇒ mixin 只能做静态取证（jar 条目 + 配置 + `type: mixin` 全窗口无失败）。
11. `screenshot` 原语当前恒 `raise Blocked`（`mt_case.py:430-433`）⇒ 视觉断言一律改静态/公开 API 路线。

### 14.3 三处判据改写（不是因为产品有问题，而是通道限制）

| 用例 | 原设想 | 实际取证方式 |
|---|---|---|
| RENDER-SIGN-TRANSLUCENT | 运行期直调 `AlphaContext.push/current` | 包私有 + mixin 不可加载 ⇒ jar 条目 + `astral_dice.mixins.json` 在产物内 + mixin 无失败 + `LivingEntityRenderer` 可加载 |
| RENDER-HUD-ASTRAL | 反射读私有 defense/attack 数值 | 公开静态 `DEFENSE_OVERLAY` / `ATTACK_OVERLAY` / `DEFENSE_FULL` + jar 条目 |
| RENDER-PARTICLE | 反射读 `ParticleEngine` 的 provider/spriteSet | 注册表命中 + `ResourceManager.getResource('astral_dice:particles/*.json')` 存在 + jar 条目 |

### 14.4 全量回归读数（2026-11-05 夜，`temp/run_final_1211.log`）

- 运行方式：逐条 `python scripts/test/lib/mt_case.py run --case scripts/test/cases/<X>-fabric-1.21.1.json --version fabric-1.21.1 --run-dir fabric-1.21.1/run/client --case-timeout 150`；活客户端 = `bash scripts/test/win/launch.sh --version fabric-1.21.1 --side client --enable-autojs --wait-world 600 --timeout 900`。
- 结果：**TOTAL pass=29 fail=0 other=0**。
- 首跑对比：26 条里 PASS 13 / FAIL 13；修复轮次共改 13 条（12 条重写/机械修 + RENDER-SIGN-TRANSLUCENT 改静态路线）。
- 用例合法性：`python scripts/test/lib/mt_case.py validate --cases-dir scripts/test/cases --version fabric-1.21.1` ⇒ `MT_CASE_VALIDATE: files=29 errors=0`。

### 14.5 本轮暴露的产品级问题（**未改代码，待裁决**）

1. **玩家侧骰子闪避整体关闭**：`fabric-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java:261` `PLAYER_DODGE_ENABLED = false`（注释 :256-260 说明已移除），闪避只剩 moses / ren_shield / pandaman 三条立牌反制。
2. **无 firewall/骇客防火墙类**：珍珠 20 tick fall 免疫的真实实现是 `item/sign/NancyLuSignItem.java`（`ENDER_PEARL_IMMUNE_TICKS = 20`，:60、:288-296、:308-317）。
3. **`item/card/CardItem.java` 无 `use()`**：战斗卡只能在骰子卡槽里被战斗结算消耗（`DiceCombatEvents.java:1095 consumeAttackCardDurabilityOnce`），"右键用卡"不成立。
4. **星币拾取桥是通知式、不可取消**（`platform/PuzzlesBridges.java:210-215`）⇒ 拾取入包与入账可能同时发生 = 潜在刷钱；WALLET-STARCOIN 走 `/give + depositAll` 绕开了拾取路径，未覆盖。
5. **`item/ChargeManager.java:82-90` `addStacks` 返回叠加后总值而非增量**，与 `ChargeEffect.addStacks` 同名不同义。
6. **`compat/curios/TrinketBridge.java:79-113`** 把 `onEquip` 的 prevStack / `onUnequip` 的 newStack 硬传 `ItemStack.EMPTY`，与 neo/forge 线语义不一致。
7. 注释漂移两处已就地订正：`fabric-1.21.1/build.gradle:113`（sodium/iris 版本描述）与 `mixin/client/SignTranslucencyMixin.java:20`（写成 Fabric 1.20.1）。

### 14.6 遗留

1. `fabric-1.20.1` 的 `LT-ENTITY-SWEEP` 需活客户端，本轮未复跑（离线判 BLOCKED 属预期）。
2. **E8 RCON 仍未验证**（`env.sh` 的 RCON 段只探第一条 fabric 线）。
3. 视觉取证通道仍缺：`screenshot` 原语恒 BLOCKED，像素级断言（半透明、HUD 数值、粒子外观）待通道恢复后补。
4. 上述 14.5 的 1–6 条未改代码，需用户裁决后另开批次。
