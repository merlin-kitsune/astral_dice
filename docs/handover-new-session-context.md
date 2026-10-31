# 新会话上下文（Linux 期）

> **重写于 2026-10-31**：替换旧 Windows 口径。本文件是**新会话三分钟上手**用的；细节与迁移方案见本文件指向的正本。
> **路径纪律**：仓库内文件一律写**相对仓库根的路径**（如 `docs/agent/AGENTS-dsh-linux.md`、`run/start-1.21.1.sh`），不写宿主绝对路径；只有附录/迁移章节才登记绝对路径快照。

## 0 接手第一步（顺序固定）

1. 读根 `AGENTS.md` 认领宿主规则：本机是 **Linux** ⇒ 规则正本 `docs/agent/AGENTS-dsh-linux.md`；`docs/agent/AGENTS-dsh.md` 是 **Windows 历史正本**，只作对照，不照抄其 pwsh / `gradlew.bat` / `mt.ps1` 口径。
2. 加载技能 `using-superpowers`（`docs/agent/AGENTS-dsh-linux.md` 第 8 行起有硬要求）。
3. 读 `docs/agent/AGENTS-dsh-linux.md` 全文（约 380 行）。
4. 读 `docs/handover-1.5.0-main.md` 的**最后两条** `### 4.1ax 本机盘工作副本与 rsync 通道`、`### 4.1ay 整合包部署根目录改为 hgfs .minecraft`，以及 `## 5 遗留与欠账`（`4.1aw` 是 Linux 适配批背景，可后读）。
5. 读 `docs/handover-2026-10-31-local-disk-workspace.md`（**本机盘工作副本布局 + rsync 通道 + 同步纪律**）与 `docs/handover-2026-10-31-linux-vm-adoption.md`（VM 环境背景 + 迁移清单）。
6. 动手前跑 `bash scripts/test/linux/env.sh` 与 `git status --short -uall` 核对现状。

## 1 实读现状（2026-10-31 13:5x 快照，以命令为准）

| 项 | 值 |
|---|---|
| 分支 / HEAD | `multi-main` / `7c44cfbf`（2026-10-30） |
| 版本 | 四线 `mod_version = 1.5.0-rc.7+<加载器后缀>` |
| Gradle | wrapper 9.2.1（aliyun 镜像），**daemon JVM 统一 JDK 21** |
| starengine | 1.1.0，四线制品已在本机 mavenLocal |
| 整合包根 | `/mnt/hgfs/.minecraft`（`MC_HOME` 可覆盖；四线 `pushToGame` 由根 `build.gradle` 的 `mcHomePath` 解析） |
| 工作树 | 有未提交改动（见 §5），**未 commit / 未 push**；Windows 宿主 2026-10-31 14:5x 复核过的**文件级清单 + 验收 + 提交建议**见 `docs/handover-1.5.0-main.md` §4.1az |

## 2 环境（当前是虚拟机！）

- **VMware 虚拟机里的 Kubuntu 26.04.1，KDE Plasma Wayland 会话**。
- **工作副本在本机盘 ext4**（2026-10-31 13:2x 起）：`~/Project/{astral_dice_multiloader, astral_dice_multiloader-next, starengine, starengine_next}` 是**工作正本**；hgfs 上的 `/mnt/hgfs/MCProject/<同名目录>` 是**原始镜像/同步通道**（Windows 侧仍可访问）。
- 同步：任意副本根下 `bash scripts/dev/rsync-original.sh pull|push [--apply]`（默认 dry-run）。纪律：先 pull 再改、先 commit 再 push、永不 rsync `.git`（worktree 指针是机器相关，已在本机修好）。
- **整合包（Minecraft 实例）根 = `/mnt/hgfs/.minecraft`**（Windows 宿主同一批文件为 `D:\.minecraft`）；四线部署目标统一为 `versions/<实例>/mods`，由根 `build.gradle` 的 `mcHomePath` 解析（`MC_HOME` 可覆盖）。**主仓与 `-next` 两条工作树同批生效**。仍受「默认不部署」红线约束。
- 本机盘相对 hgfs 的收益：写 64 MiB 2.7 GB/s vs 932 MB/s；建 500 小文件 <0.01 s vs 0.39 s；**符号链接/硬链接可用**（hgfs 上 `ln -s` 报 `Operation not supported`、硬链接 `EPERM`）。
- JDK：`/usr/lib/jvm/java-{17,21,25}-openjdk-amd64` 均在；无独立 gradle（只用 `./gradlew`）；node 26 + pnpm 可用。
- 图形：`DISPLAY=:0`、`WAYLAND_DISPLAY=wayland-0`；MC 三线都是 **XWayland 窗口**（GLFW 无 Wayland 后端）⇒ `DISPLAY` 必须是硬前提。
- 桌面工具：`xdotool`（XTEST，注入前必须 `windowactivate`）、`spectacle`（截图）；computer-use = `@mirsella/computer-use-mcp`（KDE Wayland）。
- **迁移方向（用户 m00876 已定）**：后续迁到真实 Linux，消除 hgfs 性能与符号链接限制；迁移步骤见 `docs/handover-2026-10-31-linux-vm-adoption.md` §6，本文所有仓库内路径迁移后无需改写。

## 3 基本盘

| 线 | 加载器 / MC | Java | runClient 目录 | 状态 |
|---|---|---|---|---|
| `neoforge-1.21.1` (P0) | NeoForge 21.1.235 / 1.21.1 | 21 | `run/1.21.1` | :classes ✅、实机 ✅ |
| `forge-1.20.1` (P0) | Forge 47.4.10 / 1.20.1 | 17 | `run/1.20.1` | :classes ✅、实机 ✅ |
| `neoforge-26.1.2` (P1) | NeoForge 26.1.2.109 / 26.1.2 | 25 | `run/26.1.2` | :classes ✅（必须 `--no-configuration-cache`）、实机 ✅ 但 KubeJS 报错屏 |
| `fabric-1.20.1` (P1) | Fabric 0.19.5 + FAPI 0.92.12 | 17 | `run/fabric-1.20.1` | :classes ✅、**dev 启动失败**（Sodium/LWJGL） |

- 构建依赖：`starengine:1.1.0` 走 `mavenLocal()`；制品在 `~/.m2/repository/com/merlinkitsune/starengine/*/1.1.0/`（四件）。
- 本机 Maven Central 对本出口 IP **403** ⇒ `~/.gradle/init.d/00-aliyun-central-mirror.gradle` 在仓库创建瞬间改写中央仓 URL（不改仓库文件，必须保留；真机需重测）。
- 26.1.2 另需：`--no-configuration-cache`；neoforged 仓库曾 TLS 被切断，离线镜像 `~/.mc-mirror` + `10-neoforged-offline-mirror.gradle.disabled`（现已恢复直连，保持停用）。

## 4 Linux 测试台（取代 Windows 的 `scripts/test/mt.ps1`）

```bash
bash scripts/test/linux/env.sh                     # 只读检查，机器行 CHECK: key=..., ENV_RESULT: OK|WARN|FAIL
bash scripts/test/linux/env.sh --version 1.21.1 --quiet
bash scripts/test/linux/launch.sh --version 1.21.1 # 就绪 = AutoJS /health 200 + status client available → LAUNCH: READY
bash scripts/test/linux/launch.sh --version 26.1.2 --timeout 900
bash scripts/test/linux/stop.sh  --version 1.21.1  # → LINUX_STOP: OK
bash scripts/test/linux/stop.sh  --all
```

- Windows 的 `mt.ps1`（19,384 行、19 个模块）在 Linux **整台不可移植**：`user32.dll` P/Invoke、`taskkill`、`Get-CimInstance`、`cmd.exe /c gradlew.bat`、GBK 控制台、D: 盘路径。**Linux 侧一律不调用 `scripts/**/*.ps1`**（共 52 个）；pwsh 7 只是可选只读工具（`~/.local/powershell/pwsh`，未入 PATH）。
- 未复刻的 Windows 能力：进程防撞锁、世界清场、OP/禁 AI 闸门、`mt_inject`（PostMessage 键鼠）、`mt_ime`、`mt_capture`。替代通道：游戏内用 **AutoJS eval_js**，GUI 级用 **computer-use MCP**。

## 5 当前工作树（未提交，2026-10-31 13:5x）

```
 M AGENTS.md                                      # 分派表补 Linux 正本 + 索引
 M build.gradle                                   # 新增 ext.mcHomePath（整合包根统一解析）
 M neoforge-1.21.1/build.gradle
 M forge-1.20.1/build.gradle
 M neoforge-26.1.2/build.gradle
 M fabric-1.20.1/build.gradle
 M docs/agent/PROJECT-REFERENCE.md                # 投放目标改 hgfs 路径
 M docs/handover-1.5.0-main.md                    # 4.1aw/4.1ax/4.1ay + §5 Major 23–28
 M docs/handover-new-session-context.md           # 本文件
 M forge-1.20.1/src/main/resources/assets/astral_dice/lang/{en_us,zh_cn,ja_jp}.json  # 各 +2 条 Jade 翻译
?? docs/agent/AGENTS-dsh-linux.md                 # Linux 规则正本
?? docs/handover-2026-10-31-linux-vm-adoption.md  # VM 环境背景 + 真机迁移清单
?? docs/handover-2026-10-31-local-disk-workspace.md  # 本机盘副本 + rsync 通道
?? scripts/dev/rsync-original.sh                  # 与 hgfs 原始镜像同步
?? scripts/test/linux/{README.md,env.sh,launch.sh,stop.sh}
?? scripts/test/linux/logs/.gitignore
```

- `astral_dice_multiloader-next`（`multi-dev-next`）有同款 5 个 build 文件改动 + `scripts/dev/rsync-original.sh`（未跟踪），均**未 commit**。
- 用户既有的 4 个未跟踪文件（`models/astral_anvil.bbmodel`、`scripts/test/cases/TMP-ATTRLESS-HYDRA-1.21.1.json`、`scripts/test/resources/kubejs/1.21.1/server_scripts/astral_batch3_probe.js`、`zz_screen_probe.py`）**不是本批产物，禁止改动**。
- `run/` 整个被 `.gitignore:61` 忽略 ⇒ `run/start-*.sh`（191/191/210 行，777）与 `.bat` 都不入库；迁移要单独拷 `run/`。

## 6 最常用命令（相对仓库根）

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64       # 所有 gradlew 的 daemon 都必须是 21
./gradlew :neoforge-1.21.1:classes --console=plain
./gradlew :forge-1.20.1:classes   --console=plain
./gradlew :neoforge-26.1.2:classes --console=plain --no-configuration-cache
./gradlew :forge-1.20.1:pushToDevRun --console=plain      # legacyforge dev 运行加载 run/1.20.1/mods/*.jar，改代码后必须重建
bash run/start-1.21.1.sh                                  # 也可直接双击用（日志 run/<线>-launch-<ts>.log）
python3 -B docs/agent/verify-split.py                     # 规则库守门（期望全 [OK]）
python3 tools/verify_sign_display_names.py                # 立牌命名守门（改动命名时）
bash scripts/dev/rsync-original.sh pull              # 与 hgfs 原始镜像对差异（默认 dry-run）
bash scripts/dev/rsync-original.sh push --apply      # 把本机改动写回原始镜像（先 commit）
MC_HOME=/path/to/.minecraft ./gradlew :neoforge-1.21.1:pushToGame --dry-run   # 换整合包根不带 --configuration-cache 时才生效
```

## 7 已知缺口 / 未验证（照抄，勿当成已完成）

1. **fabric-1.20.1 dev 启动失败**：Sodium `EarlyDriverScanner` 要 LWJGL 3.3.1，Loom dev runtime 给 3.3.2-snapshot；与 Linux 无关，P1 待修。
2. **26.1.2 KubeJS 类加载遗留**：标题屏停在 `KubeJSErrorScreen`，`astral_wallet_vision.js#38` 报 `StarCoinWalletState: Class could not be found`（jar 内确有该类）⇒ Rhino 看不到 jarJar/嵌套 jar；`run/26.1.2/mods/` 还有旧 `astral_dice-1.3.7-beta.2` jar。
3. **Maven Central 403**：靠 aliyun init 绕过，换机需重测，403 消失则删除该 init。
4. `scripts/test/mt.conf` 仍是 Windows 历史路径（Linux 脚本只读少量键，`env.sh` 报 WARN 不阻断）。
5. 本批**未提交**（`AGENTS.md` + 3 个 lang 是已跟踪改动，需用户裁决后再提交）。
6. `run/1.20.1/crash-reports/` 保留两份 Jade 断言事故报告作证据。
7. **VM → 真实 Linux 迁移未执行**（用户 m00876 已定方向）：见 `docs/handover-2026-10-31-linux-vm-adoption.md` §6。
8. **两副本同步纪律**：本机盘=正本、hgfs=镜像；rsync 只搬内容不搬 `.git`，pull 进来的改动以普通工作树改动出现，需人工 `git status` 复核后提交；`-next` 两份是 worktree，其 `.git` 指针只在本机盘修好（原始副本仍指 Windows 路径，Windows 侧正常）。
9. **`-next` 2.0 线暂不能正常配置**：需要 `starengine 26.4-SNAPSHOT.1` 制品（本机 `~/.m2` 只有 1.1.0），且当前 fabricmc/neoforged 站点 TLS 抖动；本次只验证到「加 `-Pstarengine_version=1.1.0 -Pstarengine_version_range='>=1.1.0'` 时路径解析正确」，正式构建前先发布 starengine_next。
10. **`pushToGame` 未实跑**：主仓与 `-next` 的部署目标都已指向 hgfs `.minecraft`，但只做过探针/`--dry-run`；真正投放会按白名单（`multi-main`）或 `-PdeployToPack` 触发，属「默认不部署」，需用户明确下达。

## 8 纪律红线（本批踩过的坑，必须遵守）

- **实机必须用户明确下达**；测试结束必须 `stop.sh` 收停，贴 `LINUX_STOP: OK` + 无 java/`net.minecraft` 残留。
- 禁止裸 `pkill -f`（会杀别的 MC 实例）；脚本一律 `/proc/<pid>/cwd` + cmdline 精确匹配。
- **脚本运行中不要编辑它**：bash 会按字节流重解析，可能把启动脚本再跑一遍（本批真实发生过一次，靠 PID 补杀收场）。
- `legacyforge` dev 运行加载 `run/1.20.1/mods/*.jar` ⇒ 改 java/资源后要 `:forge-1.20.1:pushToDevRun`，只 build 不 push 不生效。
- 26.1.2 构建必须 `--no-configuration-cache`；四线 gradle daemon 必须 ≥JDK 21。
- AutoJS eval：在 KubeJS 下用 `Java.loadClass('net.minecraft.client.Minecraft')`，**没有裸 `Minecraft` 全局**；先 `mcp__autojs__status` 看哪一侧可用。
- computer-use：`observe` 必须同时给 `view`（`screenshot|accessibility|both`）+ `target` 对象；MC 窗口在 AT-SPI 里不可见，操作前用 `xdotool windowactivate` 激活。
- 默认不 commit / push / tag；默认不部署；保护 `models/`、`images/`、`sound/`；互斥锁用三行 `PID=/TIME=/TASK=`；发现非自己改动立即停手报告。
