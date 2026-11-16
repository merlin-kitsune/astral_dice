# 新会话上下文（Windows 原生宿主 · 五线）

<!-- 2026-11-16 整份重写，废除「原生 Linux 真机期」口径。本文件是 DSH 新会话的唯一入口上下文；
     滚动实机读数与批次记录在 docs/handover-1.5.0-main.md（「## 5 遗留与欠账」为续做清单）。 -->

> **事实源原则**：分支 / HEAD / `mod_version` / 是否有未提交改动，一律以**实读命令**为准；本文件数字是 2026-11-16 的快照。

## 0 接手第一步（顺序固定）

1. 读根 `AGENTS.md`（宿主分派 + 共同红线 + 索引）→ 再读 `docs/agent/AGENTS-dsh.md`（Windows 宿主规则正本）。
2. 实读复核：`git rev-parse --abbrev-ref HEAD` / `git rev-parse --short HEAD`；各线 `gradle.properties` 的 `mod_version` / `starengine_version`。
3. 实机、部署、`commit` / `push` / `tag` 一律等用户明确下达（红线 1–3）。

## 1 宿主与路径

| 项 | 值 |
|---|---|
| 宿主 | **Windows 原生**；命令一律 MSYS bash（**禁 PowerShell**） |
| 仓库根 | `F:\MCProject\astral_dice_multiloader`（MSYS：`/f/MCProject/astral_dice_multiloader`） |
| 前置库 | `F:\MCProject\starengine`（MSYS：`/f/MCProject/starengine`） |
| 开发线工作树 | `F:/MCProject/astral_dice_multiloader-next` |
| 库开发线工作树 | `F:/MCProject/starengine_next` |
| 测试台唯一入口 | `bash scripts/test/win/mt.sh` |
| 互斥锁目录 | `F:/MCProject/.agent-locks/`（`kimi.lock` / `dsh.lock`） |
| 整合包投放根 | `D:\.minecraft`（环境变量 `MC_HOME` 可覆盖） |

- Linux 原生宿主口径**不在本机执行**；对应规则留在 `docs/agent/AGENTS-dsh-linux.md`（保持原样，不据其下现状结论）。

## 2 分支与工作树拓扑（2026-11-16 实读）

| 位置 | 分支 | 尖端 | 版本 | 角色 |
|---|---|---|---|---|
| `F:/MCProject/astral_dice_multiloader` | `multi-main` | `01c52fd3` | `1.6.0-pre.1` | 主线（五线同树，本仓） |
| 同上（同仓分支） | `release/1.6.0-pre.1` | `fdd08f9e` | `1.6.0-pre.1` | 发布线尖端，**已并入 `multi-main`** |
| `F:/MCProject/astral_dice_multiloader-next` | `multi-dev-next` | `5120738c` | `26.4-SNAPSHOT.76` | 开发线，与本树**互不镜像** |
| `F:/MCProject/starengine` | `main` | `26ab1df` | `1.2.0-pre.1` | 前置库发布线（mavenLocal 供本仓消费） |
| `F:/MCProject/starengine` | `next` | `f86b9b9` | `26.4-SNAPSHOT.13` | 前置库开发线 |
| `F:/MCProject/starengine_next` | — | — | — | 库开发线的检出工作树 |

- 合并完整性（2026-11-16 体检）：`multi-main` 已含 `release/1.6.0-pre.1` 全量，`COPY-DEVNEXT=0`。

## 3 五条线与交付优先级

- 单工作树**五条线同树**（`settings.gradle` 五行 `include`，各线同一工作目录）：
  - **P0**（必交付）：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`
  - **P1**（次优先）：`fabric-1.20.1` / `fabric-1.21.1`
- `fabric-1.21.1` 是**第五条线**：`src` 完整、**未发布**（无产物、无实机读数）。

## 4 五线基线表（2026-11-16 实读）

| 线 | MC | 加载器 | Java | 构建插件 | 优先级 |
|---|---|---|---|---|---|
| `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.265 | 21 | ModDevGradle `net.neoforged.moddev` 2.0.141 | P0 |
| `forge-1.20.1` | 1.20.1 | Forge 47.1.47 | 17 | ModDevGradle LegacyForge 2.0.144 | P0 |
| `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.126 | 25 | ModDevGradle 2.0.147 | P0 |
| `fabric-1.20.1` | 1.20.1 | Fabric Loader 0.19.5 + FAPI 0.92.12 | 17 | Fabric Loom | P1 |
| `fabric-1.21.1` | 1.21.1 | Fabric Loader 0.20.1 | 21 | Fabric Loom | P1 |

- Gradle wrapper：**9.5.1**（阿里云镜像）。
- `neoforge-26.1.2` 线**必须** `--no-configuration-cache`（与根 `mcHomePath` 解析冲突）。

## 5 版本口径（两套并存，勿混用）

| 口径 | 发布线（本仓 `multi-main` / `release/1.6.0-pre.1`） | 开发线（`astral_dice_multiloader-next`） |
|---|---|---|
| `mod_version` | `1.6.0-pre.1+<线名>` | `26.4-SNAPSHOT.76` |
| 线名后缀 | `+neoforge_1.21.1` / `+forge_1.20.1` / `+neoforge_26.1.2` / `+fabric_1.20.1` / `+fabric_1.21.1` | — |
| 前置库版本 | `starengine 1.2.0-pre.1`（`F:/MCProject/starengine` 的 `main`） | `starengine 26.4-SNAPSHOT.13`（`next`） |

- fabric 两线另带 `starengine_version_range = >=1.2.0-pre.1 <2.0.0`；jar 内硬前置区间同为 `[1.2.0-pre.1,2.0.0)`，`META-INF/jarjar/` 计数 0（库**不内嵌**，必须成对投放）。
- 改产品代码并重编译后先 bump 版本、记 `CHANGELOG.md` / `CHANGELOG_ZH.md`，再开实机（口径见 `~/.dsh/AGENTS.md`〈实机改动版本纪律〉）。

## 6 构建与测试入口

```bash
export JAVA_HOME="C:/Program Files/Zulu/zulu-21"   # daemon JVM 需 ≥21；toolchain 17/21/25 自动发现
./gradlew :neoforge-1.21.1:classes --console=plain
./gradlew :forge-1.20.1:classes   --console=plain
./gradlew :neoforge-26.1.2:classes --console=plain --no-configuration-cache
./gradlew :fabric-1.20.1:build    --console=plain
./gradlew :fabric-1.21.1:build    --console=plain
./gradlew build --console=plain   # 五条线全量
```

测试台（**唯一入口**；线名 `1.21.1` / `1.20.1` / `26.1.2` / `fabric-1.20.1` / `fabric-1.21.1`）：

```bash
bash scripts/test/win/mt.sh --version 1.21.1 --phase env
bash scripts/test/win/mt.sh --version 1.21.1 --phase build
bash scripts/test/win/mt.sh --version 1.21.1 --phase launch
bash scripts/test/win/mt.sh --version 1.21.1 --phase cases --case scripts/test/cases/X-1.21.1.json
bash scripts/test/win/mt.sh --version 1.21.1 --phase report
bash scripts/test/win/mt.sh --version 1.21.1 --phase stop --purge-saves
bash scripts/test/win/mt.sh --version fabric-1.21.1 --side server --phase launch
```

- 阶段：`env | build | launch | cases | report | stop`（`preflight` 为 P2 迁移项）。
- 退出码：0=OK · 1=FAIL · 2=用法错误 · 10=PREFLIGHT · 11=BLOCKED · 12=TIMEOUT · 130=中断；机器行 `MT_*`。
- 批量闸门：无 `--phase` 的全流程 / `cases` 未带 `--case` / `report` / 无 `--version` 的单阶段一律 exit 2，放行需 `--allow-auto`。
- 用例共 **348** 个（顶层 336 + `scripts/test/cases/blocked-26.1.2/` 12）；本轮接管新增 18 个缺口用例（P0 10 / P1 8，末条 `WALLET-CREDIT-fabric-1.20.1` 补齐矩阵最后一格），`mt_case validate` → files=348 errors=0 / with_judgment=345。
- fabric dev 前置：先 `python tools/loom_embedded_jars.py --apply`（KI-F23：内嵌库被 Loom 剥离 `jars` 声明）。
- 端口互斥：`25585` AutoJS / `25565` 游戏 / `25575` fabric RCON，同一时刻只允许**一条线**。

## 7 静态守门清单与当前状态（2026-11-16 实读）

| 门禁 | 命令 | 状态 |
|---|---|---|
| 规则文件四项自检 | `python -B docs/agent/verify-split.py` | **绿**：4 项 `[OK]`；仅 1a 既有 `[WARN]`（`AGENTS.md.bak-a-plan-*` 缺失） |
| 规则 TOC 一致性 | `python -B tools/gen_agents_toc.py --check` | **绿**：`TOC: OK headings=272` |
| lang 同步（key 集 / 颜色码 / 占位符） | `python tools/check_lang_sync.py --lang-dir <线>/src/main/resources/assets/astral_dice/lang` | **绿**（2026-11-16 修：`z3000` 两键的 30 处字面 `%` 已转义为 `%%`，0 处残留） |
| CHANGELOG 标题 | 见 `docs/agent/PROJECT-REFERENCE.md` §5 清单 | **绿**（`CHANGELOG_ZH.md` 标题全角括号改为半角，parity sections=95 entries=1137/1137） |
| 立牌 / 物品三语命名表 | `python tools/verify_sign_display_names.py`（表 `tools/sign_display_names.json`） | **绿**（2026-11-16 22:3x 按吉星派对 wiki 定名：`z3000` = zh 垃圾箱立牌 / en Z3000 Sign / ja ゼット3000のスタンド；表增至 37 条；五线 `mamushi_note` 的 Golden Koi 指代统一为 `Amakawa Souri Sign` / `天川蒼鯉のスタンド`；`GATE: PASS`，166,961 项检查） |
| meito 专项门禁 | 见 §5 清单 | **绿**（原 7 红全是工具缺陷：26.x `Identifier` 改写 + Javadoc 误判；已加 `(?:ResourceLocation|Identifier)` 与注释剔除，GATE: PASS） |
| 其余补充门禁 | 见 `docs/agent/PROJECT-REFERENCE.md` §5 | 43 条复跑 = P0 22 PASS / 18 FAIL / 2 SKIPPED / 1 路径错；本轮只修两条口径红（fabric 创意栏白名单 `vacuum_piece`、资源完整性 26.1.2 旧装载路径降 WARN，均做变异反假绿），其余待分流 |

## 8 当前欠账（按优先级）

1. **五线无 `1.6.0-pre.1` 实机证据**：红线 7「没有详细测试就没有发布版本」未满足前，`1.6.0-pre.1` 一律不得作为发布版本。
2. **jar 已重建（2026-11-16 22:33，含命名校正）**：五线 `astral_dice-1.6.0-pre.1+<loader>.jar` 齐（neo/forge 走 `:jar`，fabric 两线须 `:remapJar`）；1.5.0 / 1.5.1 历史 jar 仍并存未清。
3. **补充门禁红点未全分流**：43 条复跑 P0 22 PASS / 18 FAIL，本轮只修两条工具口径红，其余逐条判「真缺陷 / 口径」后再动。
4. ~~**`z3000` / Golden Koi 三语命名待裁决**~~ → **已裁决（2026-11-16，用户 m00480「从吉星派对 wiki 获取 Z3000 的各语言名称决定实际命名」）**：按 wiki 定名并落地，见 `docs/handover-1.5.0-main.md` 第 29 条。
5. **版本 bump 待裁决**：本轮仅改门禁工具、文档与 lang 转义，未改 Java 源码、未 bump `mod_version`（仍 `1.6.0-pre.1`）；若要发布投放需先裁决 bump。
6. 发布线远端分支清理待放行：`origin/1.20.1-fabric` 的删除需要 `push`，尚未执行。

## 9 文档导航

| 需要什么 | 去哪里 |
|---|---|
| 宿主分派 / 共同红线 / 索引 | `AGENTS.md` |
| Windows 宿主规则正本（工作流、委派、测试台、桌面通道） | `docs/agent/AGENTS-dsh.md` |
| 工程拓扑 / 各线基线 / 目录职责 / 常用命令 | `docs/agent/PROJECT-REFERENCE.md` |
| 滚动交接（实机读数、批次表、`## 5 遗留与欠账`） | `docs/handover-1.5.0-main.md` |
| 单仓结构说明 | `docs/multiloader-layout.md` |
| 规则全文 / 逐批历史 | `docs/agent/AGENTS-full.md` + 行号总表 `docs/agent/AGENTS-TOC.md` |
| Linux 宿主（不在本机执行） | `docs/agent/AGENTS-dsh-linux.md`、`docs/handover-2026-10-31-native-linux-migration.md` |
| 26.1.2 兼容映射 | `docs/compat-26.1.2-neoforge.md` |
| 本轮接管体检产物 | `work/takeover/`（`docs-audit.md` / `build-verify.md` / `fix-batch1.md` / `fix-batch2.md` / `test-matrix.md` / `test-cases-final.md` / `verify-batch1.md`） |

## 10 不许做的事（速查）

- 不自行开实机、不连拍、不跑用例；实机须用户明确下达。
- 不部署（不拷 jar 到整合包、不上传 CurseForge、不改发布配置）。
- 不 `commit` / `push` / `tag`（除非用户明确要求；远程 git 操作走本地代理 `http://127.0.0.1:7897`）。
- 不改 `models/` / `images/` / `sound/`；不删、不改他人改动。
- 同一文件单写者；同一条线不得两个实机实例。
