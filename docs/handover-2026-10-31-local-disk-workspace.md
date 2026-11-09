# 本机盘工作副本交接 —— 2026-10-31
<!-- 历史记录：文中旧 shell 命令已于 2026-11-09 全部废弃，现用 bash 测试台（scripts/test/win/mt.sh）与 windows MCP，仅作溯源。 -->

> **本文口径**：仓库内文件一律用**相对路径**（相对仓库根，如 `docs/agent/AGENTS-dsh-linux.md`、`run/start-1.21.1.sh`），脚本自解析仓库根；只有「附录 A 绝对路径快照」登记宿主绝对路径。
> **本文的定位**：2026-10-31 13:2x 用户把 4 个工作副本（Astral Dice 两线 + StarEngine 两线）从 hgfs 共享盘复制到 **VM 本机虚拟磁盘**，后续开发在本机盘进行；hgfs 上的原始目录保留为**镜像/同步通道**。本文记录新布局、已做的修复、rsync 通道用法、以及两副本的纪律。
> 前置交接（VM 环境、hgfs 实测代价、当时各线实机证据、真机迁移清单）仍有效：`docs/handover-2026-10-31-linux-vm-adoption.md（已删除 2026-11-05）`。

## 0 一句话现状

本机盘（ext4，`/dev/sda2` 挂在 `/`）现有 4 个可用的 git 工作副本：`~/Project/{astral_dice_multiloader, astral_dice_multiloader-next, starengine, starengine_next}`。已修复两个 `-next` 工作树的 `.git` 指针（原来指向 `F:/...` Windows 路径），并给 starengine 设置 `core.autocrlf=true`（消除 20+ 个假改动）。新位置 **`./gradlew :neoforge-1.21.1:classes` 实测 57s 成功**（复用复制过来的 `build/moddev/artifacts` 与项目内 `.gradle`）。与原始 hgfs 路径的同步用 `scripts/dev/rsync-original.sh`（默认 dry-run）。

## 1 布局（本机盘 = 工作正本；hgfs = 镜像）

| 副本（本机盘） | 分支 / HEAD | 版本 | 体量 | 角色 |
|---|---|---|---|---|
| `~/Project/astral_dice_multiloader` | `multi-main` / `7c44cfbf`（2026-10-30） | 各线 `1.5.0-rc.7+*`；starengine 1.1.0 | 4.6 G（`run/` 2.1 G、`.gradle` 168 M、`build` 124 M） | **P0 主线工作副本（正本）** |
| `~/Project/astral_dice_multiloader-next` | `multi-dev-next` / `a5291f2e`（2026-10-29） | 各线 `26.4-SNAPSHOT.4+*` | 21 G | 2.0 开发线（worktree，见 §2.1） |
| `~/Project/starengine` | `main` / `16b48c4`（2026-10-25） | 1.1.0 | 863 M | 库 1.1 线（本仓构建的 mavenLocal 来源） |
| `~/Project/starengine_next` | `next` / `1b02b80`（2026-10-27） | 26.4-SNAPSHOT.1 | 331 M | 库 2.0 线（worktree，见 §2.1） |

原始镜像（hgfs，保持原样）：`/mnt/hgfs/MCProject/<同名目录>`，Windows 宿主侧仍是 `F:\MCProject\<同名目录>`。

## 2 本次为「复制到本机盘」做的改动

### 2.1 修复两个 `-next` 工作树（必须，否则 git 完全不可用）

`astral_dice_multiloader-next` 与 `starengine_next` 是 **git worktree**，`.git` 是文件而不是目录，原先内容指向 Windows 绝对路径：

```
astral_dice_multiloader-next/.git            -> gitdir: F:/MCProject/astral_dice_multiloader/.git/worktrees/astral_dice_multiloader-next
astral_dice_multiloader/.git/worktrees/.../gitdir -> F:/MCProject/astral_dice_multiloader-next/.git
starengine_next/.git                          -> gitdir: F:/MCProject/starengine/.git/worktrees/starengine_lib_next
starengine/.git/worktrees/starengine_lib_next/gitdir -> F:/MCProject/starengine_next/.git
```

改为 Linux 绝对路径（4 个文件，逐行替换）：

- `~/Project/astral_dice_multiloader-next/.git` → `gitdir: /home/kitsune/Project/astral_dice_multiloader/.git/worktrees/astral_dice_multiloader-next`
- `~/Project/astral_dice_multiloader/.git/worktrees/astral_dice_multiloader-next/gitdir` → `/home/kitsune/Project/astral_dice_multiloader-next/.git`
- `~/Project/starengine_next/.git` → `gitdir: /home/kitsune/Project/starengine/.git/worktrees/starengine_lib_next`
- `~/Project/starengine/.git/worktrees/starengine_lib_next/gitdir` → `/home/kitsune/Project/starengine_next/.git`

修复后实测：

```
git -C astral_dice_multiloader-next rev-parse --abbrev-ref HEAD   -> multi-dev-next
git -C starengine_next           rev-parse --abbrev-ref HEAD   -> next
```

> **注意**：这 4 个文件在 `.git/` 里，**不参与 rsync**（脚本默认排除 `/.git/`）。所以原始 hgfs 副本的 worktree 指针仍是 Windows 路径（Windows 侧继续用没问题）；如果以后再次从 hgfs 整体复制到别的机器，这套修复要重做一次。

### 2.2 starengine 的 `core.autocrlf=true`

hgfs 上的工作树是 CRLF、index 是 LF。在 hgfs 上此前已给主仓设过 `core.autocrlf=true`；**starengine 仓没有**，复制到本机后 `git status` 报 20+ 个假改动（全部 `M`）。已执行 `git -C ~/Project/starengine config core.autocrlf true` ⇒ `git status --short -uall` 回到 **0 行**。`starengine_next` 复制后本来就是 0 行。

### 2.3 本机盘 vs hgfs 的实测差异（为什么搬）

| 操作 | hgfs（原始） | 本机盘 ext4 |
|---|---|---|
| 写 64 MiB（`dd conv=fsync`） | 932 MB/s | 2.7 GB/s |
| 建 500 个小文件 | 0.39 s | <0.01 s |
| 符号链接 `ln -s` | `Operation not supported` | 可用 |
| 硬链接 | `Permission denied`（工具写文件报 `EPERM ... link`） | 可用 |

## 3 在新位置的验证结果（2026-10-31 13:2x）

1. `./gradlew --version` → Gradle 9.2.1 / Launcher JVM 21.0.12.1（daemon 指向 `/usr/lib/jvm/java-21-openjdk-amd64`）。
2. `./gradlew :neoforge-1.21.1:classes --console=plain` → **`BUILD SUCCESSFUL in 57s`**（`4 actionable tasks: 3 executed, 1 from cache`，Configuration cache entry stored）。复制过来的 `neoforge-1.21.1/build/moddev/artifacts/`（`neoforge-21.1.235-*.jar` 等）被直接复用，没有重跑 MDG decompile。
3. `bash scripts/test/linux/env.sh` → `ENV_RESULT: WARN — 0 项阻塞，2 项提示`（提示 = AutoJS 未启动 `health=000`、`scripts/test/mt.conf` 的 Windows 历史路径）。修了脚本里一处过时判断：现在按 `findmnt` 实测文件系统类型输出，本机盘显示 `hgfs_writable=OK — 文件系统=ext4`，不再硬编码 hgfs 文案。
4. `bash scripts/test/linux/launch.sh --version 1.21.1 --dry-run` → 解析出的 `start_source=run/start-1.21.1.sh`、`cmd=/home/kitsune/Project/astral_dice_multiloader/run/start-1.21.1.sh`、日志/pid 文件都在新根下 ⇒ 启动脚本的 `ROOT="$(cd "$(dirname "$0")/.." && pwd)"` 相对根解析在新位置成立，无需改脚本。
5. 整合包部署根目录切换（2026-10-31 13:5x）：根 `build.gradle` 新增 `ext.mcHomePath`（`MC_HOME` → `/mnt/hgfs/.minecraft` → Windows `D:/.minecraft`），各线 `packModsDir` 改用它；Gradle 探针实测 `mcHomePath=/mnt/hgfs/.minecraft`，四条 `versions/<实例>/mods` 全部 `exists=true`；`MC_HOME` 覆盖实测生效；`:neoforge-1.21.1:classes` 配置全绿（541ms，缓存命中）。**同一改动已同步到 `-next` 2.0 线**（5 个 build 文件，探针同样通过；该线正常配置需 26.4 starengine 制品，本机暂缺）。未跑真实 `pushToGame`（默认不部署）。
6. `git status --short -uall`（主仓，18 行）= 7 个已跟踪改动（`AGENTS.md`、`docs/agent/PROJECT-REFERENCE.md`、`docs/handover-1.5.0-main.md`、`docs/handover-new-session-context.md`、3 个 1.20.1 lang）+ 新增 `docs/agent/AGENTS-dsh-linux.md`、`docs/handover-2026-10-31-*.md`、`scripts/dev/rsync-original.sh`、`scripts/test/linux/*` + 用户既有 4 个未跟踪文件。

> 实机启动（`launch.sh` 真正拉起游戏）本次**没有**跑——规则仍是「实机须用户明确下达」；dry-run 已证明路径/参数正确。

## 4 与原始路径的 rsync 通道

脚本：`scripts/dev/rsync-original.sh`（本机盘副本里；原始副本没有这个文件，见 §6）。

```bash
cd ~/Project/astral_dice_multiloader      # 或 -next / starengine 任一副本

bash scripts/dev/rsync-original.sh pull              # 原始 → 本机（只显示差异，不改文件）
bash scripts/dev/rsync-original.sh pull --apply      # 真正同步
bash scripts/dev/rsync-original.sh push              # 本机 → 原始（dry-run）
bash scripts/dev/rsync-original.sh push --apply      # 真正写回原始
```

- **路径映射**：原始目录 = `${ORIGINAL_ROOT:-/mnt/hgfs/MCProject}/$(basename <本机仓库根>)`；所以每个副本里都直接可用，无需传参。hgfs 换了挂载点时用 `ORIGINAL_ROOT=/path github/... bash scripts/dev/rsync-original.sh pull`。
- **默认排除**：`/.git/`、`/build/`、`/*/build/`、`/.gradle/`、`/temp/`、`/.idea/`、`/logs/`、`run/*/logs/`、`run/*/saves/`、`run/*/crash-reports/`、`run/*-launch-*.log`、`.DS_Store`。⇒ 代码/资源/文档/`run` 的 mods 与配置会同步；构建产物、游戏存档、日志、git 元数据不同步。
- **默认不同步 `.git`**：两个副本各留自己的 git 元数据（原始副本要继续 Windows 侧使用，本机副本要修好的 worktree 指针）。因此同步是**内容级**的：pull 进来的差异会以普通工作树改动出现，由你 `git status` 复核后自行 commit；push 前建议先 commit（脚本会提示本机有未提交改动）。
- **默认不 `--delete`**：对侧多余的文件不会被删。确实要镜像删除时加 `--delete`（先 dry-run 看清单；会删到原始路径，慎用）。
- **已验证**：`pull` dry-run 仅输出 `scripts/` 一类目录时间差异，无内容差异；`push` dry-run 输出 `scripts/dev/` + `scripts/dev/rsync-original.sh` 新增（正是本次新增文件）。

### 4.1 同步纪律（重要）

1. **本机盘是工作正本**：日常改代码/文档都在 `~/Project/...`。
2. **动手前先 `pull`**（尤其 Windows 侧可能改过东西时），确认没有意外差异再开工。
3. **push 前先 `git status` + commit**：rsync 只搬文件内容，不搬 git 历史；不 commit 就 push 会让原始路径出现「未提交改动」。
4. **不要两边同时改**：同一时刻只在一侧编辑；冲突无法自动合并（rsync 是覆盖式）。
5. **不要 rsync `.git`**：worktree 指针、index、hooks 都是机器/路径相关，互相同步会互相破坏。
6. 原始路径的 `docs/` 会随 push 更新；**原始副本没有** `scripts/dev/rsync-original.sh`（本机专属），忽略即可。

## 5 在新位置怎么干活（和之前一致）

```bash
cd ~/Project/astral_dice_multiloader
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64          # 所有 gradlew 的 daemon 必须 ≥21
./gradlew :neoforge-1.21.1:classes --console=plain           # P0；复制来的 MDG 产物可复用
./gradlew :forge-1.20.1:classes   --console=plain
./gradlew :neoforge-26.1.2:classes --console=plain --no-configuration-cache   # P1，必须关配置缓存
./gradlew :forge-1.20.1:pushToDevRun --console=plain          # legacyforge dev 运行加载 run/1.20.1/mods/*.jar
bash scripts/test/linux/env.sh
bash scripts/test/linux/launch.sh --version 1.21.1            # 实机须用户下达；就绪=AutoJS health+status
bash scripts/test/linux/stop.sh  --version 1.21.1
python3 -B docs/agent/verify-split.py
```

- 2.0 线（`-next` 副本）：先在其根下 `bash scripts/dev/rsync-original.sh pull` 对齐原始，再按该线自己的 `AGENTS.md`/`gradle.properties`（`26.4-SNAPSHOT.*`）构建；它消费的 starengine 2.0 制品来自 `starengine_next` 的 `:publishToMavenLocal`（版本会覆盖/并存于 `~/.m2`，注意别让 1.5 线误解析 26.4 版本，必要时先清 `~/.m2/repository/com/merlinkitsune` 再按线发布）。
- 仓库外依赖**没有**随复制搬走的地方：`~/.m2`（starengine 制品）、`~/.gradle/init.d`（aliyun 镜像）、`~/.dsh`（MCP/skill）都在本机 `$HOME`，仍然有效。

## 6 已知问题 / 未验证

1. `fabric-1.20.1` dev 启动失败（Sodium 要 LWJGL 3.3.1 vs Loom 3.3.2-snapshot，P1，非本机盘问题）。
2. `neoforge-26.1.2` 标题屏停在 `KubeJSErrorScreen`（`astral_wallet_vision.js#38` 的 `StarCoinWalletState` 类加载，P1）。
3. 本机 Maven Central 对本出口 IP 403 ⇒ 依赖 `~/.gradle/init.d/00-aliyun-central-mirror.gradle`（换机需重测）。
4. `scripts/test/mt.conf` 仍含 Windows 历史路径（Linux 脚本只读少量键，`env.sh` 报 WARN 不阻断）。
5. 本机盘副本的改动**未 commit / 未 push**（`AGENTS.md`、3 个 lang 等已跟踪改动）；需要提交时请用户放行。
6. `astral_dice_multiloader-next` 21 G 里包含大量 `build/`；rsync 默认不同步它，别用 rsync 当备份。
7. 再次「从 hgfs 整体复制到新机器」时，§2.1 的 4 个 worktree 指针文件要重做修复；直接拷 `.git` 不会自动修正。
8. 原始 hgfs 副本的文档会随 push 更新，但**它的 worktree 指针未修**（Windows 侧正常），不要用 Linux 在那份上开 `-next` 工作树。

## 附录 A · 绝对路径快照（2026-10-31 13:2x，换机后作废）

| 用途 | 路径 |
|---|---|
| 本机盘工作副本根 | `/home/kitsune/Project/{astral_dice_multiloader, astral_dice_multiloader-next, starengine, starengine_next}` |
| 原始镜像根 | `/mnt/hgfs/MCProject/<同名目录>`（Windows `F:\MCProject\<同名目录>`） |
| JDK | `/usr/lib/jvm/java-{17,21,25}-openjdk-amd64` |
| mavenLocal | `~/.m2/repository/com/merlinkitsune/starengine/` |
| Gradle init | `~/.gradle/init.d/{00-aliyun-central-mirror.gradle, 10-neoforged-offline-mirror.gradle.disabled}` |
| 本地 maven 镜像 | `~/.mc-mirror/`；同步脚本 `~/.local/bin/neoforge-mirror-sync.sh` |
| DSH profile / skill | `~/.dsh/profiles/web/cordis.patch.yml`；`~/.dsh/skills/autojs/SKILL.md` |
| 互斥锁 | `/mnt/hgfs/MCProject/.agent-locks/`（跨副本全局；三行 `PID=/TIME=/TASK=`） |

## 附录 B · 状态自检（任意副本根下）

```bash
git rev-parse --abbrev-ref HEAD; git log -1 --format='%h %ad %s' --date=short; git status --short -uall | head
grep -h '^mod_version' */gradle.properties | head -4
findmnt -n -o FSTYPE -T .            # 期望 ext4（本机盘）
bash scripts/test/linux/env.sh --quiet; echo "env_exit=$?"
bash scripts/dev/rsync-original.sh pull | tail -5      # 期望只剩目录时间/本机专属文件差异
```
