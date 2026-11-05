# fabric-1.21.1 测试台可用性 + 实机计划表（2026-11-05 修订 v2）

> **本版按用户 2026-11-05 12:10 的四条裁决重写**（m08538）：① 饰品后端只做 Trinkets、Accessories 与 ACL 进不兼容列表；② 测试台 mod 限定 12 项；③ **先确保测试台可用**；④ 重新规划范围。
> 上一版（v1，P0–P9 全量规划）已被本版取代；v1 里仍成立的取证逐条并入本文件，不另存。

## §0 一页结论

| 项 | 现状（实读） | 本版裁决后的处置 |
|---|---|---|
| 产物 | `fabric-1.21.1/build/libs/astral_dice-26.4-SNAPSHOT.6+fabric_1.21.1.jar` = 2,994,223 B / 2331 条目 / 809 class | 不动 |
| 客户端 | **从未跑过**：`fabric-1.21.1/run/` 只有 `datagen`，`run/fabric-1.21.1/mods/` 只有产物 jar、无 logs | 阶段 A 结束后才允许首启（属实机，需放行） |
| 测试台 | **不具备跑第五线的能力**（版本表四线、fabric 车道常量写死 1.20.1、`mt_env.py` 无 fabric 分支） | **阶段 A：本阶段唯一目标 = 让它可用** |
| 饰品后端 | 代码里同时有 Trinkets / Accessories 双后端（11 文件 / 122 处引用） | **只保 Trinkets**；Accessories + ACL 进黑名单（§2） |
| 用例 | 24 条 fabric 基线 + 3 条专属（含双后端四态） | 收敛为 **24 + 3**（§5） |
| 实机 | 未执行 | 阶段 B/C，仍等放行 |

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
| B11 | `scripts/test/mt_capture.py` | `:91` 版本；`:249-251` 窗口标题回落 | 增线；`fabric-1.21.1` 回落成 `1.21.1` |
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
| 1 | sodium | `SMxNOGZ6` | 0.8.13+mc1.21.1 | iris 硬依赖 |
| 2 | iris | `bAo1Qhte` | 1.8.14-beta.1+1.21.1 | 依赖 sodium |
| 3 | immediatelyfast | `aLnBqxFW` | 1.6.15+1.21.1 | |
| 4 | lithium | `N08Z8wog` | mc1.21.1-0.15.4 | |
| 5 | jei | `WkRLMWAY` | 19.70.0.469 | **硬依赖 mezzconfig** |
| 6 | jade | `adpNvTZS` | 15.10.6+fabric | 本线 `modImplementation`（mixin 需编译期引用） |
| 7 | ferrite-core | `sOzRw3CG` | 7.0.3-fabric | 本轮新钉（原无 1.21.1 钉版） |
| 8 | collective | `qSQaLB3S` | 1.21.1-8.77 | superflat 硬依赖 |
| 9 | superflat-world-no-slimes | `OHCdhqlM` | 1.21.1-3.7 | 史莱姆压制闸门 |
| 10 | forge-config-api-port | `N5qzq0XV` | v21.1.6-1.21.1 | puzzles-lib 硬依赖 |
| 11 | puzzles-lib | `IV6k1yRp` | 21.1.66 | |
| 12 | modernfix | `NnNX8LBn` | 5.25.1+mc1.21.1 | |

**不在 12 项里但缺了跑不起来的必需依赖**：`fabric-api 0.116.17+1.21.1 / Mys3P7lK`（基础）、`trinkets 3.10.0 / JagCscwi`（饰品后端唯一实现）、`mezzconfig 0.6.10 / VudGGZKL`（JEI 19.70 的 required 依赖，1.21.1 fabric 产物实测存在）。
**删除项**：`indium`（sodium 0.8 不再需要）、`debugify`、`modmenu`、`carpet`（裁决 2 未列入）。

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
| L0 | 启动/就绪/注册面（BOOT-READY、JAR-ASSETS、MODLIST） | 3 |
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
2. **用例文件仍为 0**：`scripts/test/cases/*-fabric-1.21.1.json` 尚未编写，阶段 C 的 29 条待写。
3. `MT_CAPTURE` 打出的 run_id 沿用旧世代（`20260921-191522`），不影响 F2 实际落盘路径，但阶段 C 前应核一次 `.mt_active_run` 的生成逻辑。

