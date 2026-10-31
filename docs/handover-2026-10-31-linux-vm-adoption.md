# Linux（虚拟机）适配交接 —— 2026-10-31

> **本文口径**：所有仓库内文件一律用**相对路径**（相对仓库根，例如 `docs/agent/AGENTS-dsh-linux.md`、`run/start-1.21.1.sh`），脚本自身用 `ROOT="$(cd "$(dirname "$0")/.." && pwd)"` 自解析，**不含宿主绝对路径** ⇒ 迁移到真实 Linux 后无需改脚本/文档。
> 仅附录 A 登记当前虚拟机的宿主绝对路径，作为 2026-10-31 快照，迁移后作废。
> 本批**没有** commit / push（用户未放行）；工作树差异见 §2。
> **2026-10-31 13:2x 更新**：用户已把 4 个仓复制到 **VM 本机盘** `~/Project/`，日常开发已迁到本机盘；本文的 hgfs 环境/迁移清单转为**背景资料**，最新工作副本口径见 `docs/handover-2026-10-31-local-disk-workspace.md`。

## 0 一句话现状

在 **VMware 虚拟机里的 Kubuntu（Wayland/KDE）** 上，本仓已完成 Linux 适配：Gradle 9.2.1 四线可构建（三条线已编译、starengine 四线已发布到本机 mavenLocal）、`run/` 下三条 bash 启动脚本可用、`scripts/test/linux/{env,launch,stop}.sh` 可用、Wayland 下 computer-use MCP 与 AutoJS MCP+Skill 已接入 DSH；**1.21.1 / 1.20.1 / 26.1.2 三条线均实机 `LAUNCH: READY` 通过并干净收停**。fabric-1.20.1 因 Sodium/LWJGL 版本冲突未起（P1，与 Linux 无关），26.1.2 有 KubeJS 类加载遗留（P1）。

**下一步（用户已定方向）**：把仓库与工具链迁到**真实 Linux 机器**，以消除 hgfs 的性能损耗和「符号链接/硬链接不可用」限制。迁移清单见 §6。

## 1 开发环境（VM 快照，真实机迁移后作废）

| 项 | 值 |
|---|---|
| 发行版 / 内核 | Ubuntu 26.04.1 / `7.0.0-42-generic` |
| 桌面 | KDE Plasma 6.6.6，**Wayland 会话**（`WAYLAND_DISPLAY=wayland-0`、`XDG_SESSION_TYPE=wayland`、`DISPLAY=:0` 走 XWayland） |
| 用户 | `kitsune`（uid 1000，有 sudo） |
| 宿主 | **VMware 虚拟机**；仓库经 hgfs 共享盘访问，`findmnt` 实测 `fuse.vmhgfs-fuse rw,relatime,user_id=0,group_id=0,allow_other` |
| 仓库位置 | 见附录 A（宿主 Windows 侧原路径 `F:\MCProject\...`） |
| 兄弟仓 | starengine 源码仓在**仓库的兄弟目录**（相对：`../starengine`），本仓构建只依赖它的 mavenLocal 制品，不依赖路径 |

### 1.1 hgfs 的实测代价（迁移的核心理由）

| 操作 | hgfs | 本机 ext4（`/tmp`） |
|---|---|---|
| 写 64 MiB（`dd conv=fsync`） | 932 MB/s | 2.7 GB/s |
| 建 500 个小文件 | 0.39 s | < 0.01 s |
| stat 500 个文件 | 1.09 s | 0.96 s |
| 符号链接 `ln -s` | `Operation not supported` | 正常 |
| 硬链接 | `Permission denied`（edit/write 落盘报 `EPERM ... link 'tmp' -> 'file'`） | 正常 |
| 同目录 `mv` | 可用（走「写临时文件+rename」的编辑器可工作） | 正常 |

仓库体量实测：整树 **4.4 G**（含 `build/` 约 503 MB、`.git` 112 MB），java 源文件 1,501 个。Gradle 的增量编译/资源处理是海量小文件 + rename，因此 hgfs 上明显偏慢（1.21.1 首次 `:classes` 2m44s 是在 MDG 产物已缓存的前提下；starengine 那次 MDG decompile+patch 单线 **12m23s / 12m46s**）。迁移到本机盘后这些耗时预期显著下降，且 `ln -s`/硬链接恢复可用（VSCode、loom、部分工具链会受益）。

## 2 当前工作树状态（2026-10-31 03:0x 实读，未提交）

- 分支 `multi-main`；HEAD `7c44cfbf`（2026-10-30，`docs(handover): 去掉 rc.6 增量段的游离反引号…`）。
- 四线 `mod_version = 1.5.0-rc.7+<加载器后缀>`；`starengine_version=1.1.0`。
- 仓库级 `core.autocrlf=true`（index 存 LF、hgfs 工作树是 CRLF；不配会把整仓报成 3,334 个假改动）。
- `git status --short -uall` 只有以下内容（**全部未 add、未 commit**）：

```
 M AGENTS.md                                                 # 分派表补 Linux 正本 + 索引两行
 M forge-1.20.1/src/main/resources/assets/astral_dice/lang/{en_us,zh_cn,ja_jp}.json   # 各 +2 条 Jade 配置翻译
?? docs/agent/AGENTS-dsh-linux.md                            # Linux 规则正本（370 行）
?? docs/handover-2026-10-31-linux-vm-adoption.md             # 本文件
?? scripts/test/linux/{README.md,env.sh,launch.sh,stop.sh}   # Linux 测试台（bash）
?? scripts/test/linux/logs/.gitignore
?? models/astral_anvil.bbmodel                               # 用户既有素材，禁动
?? scripts/test/cases/TMP-ATTRLESS-HYDRA-1.21.1.json         # 用户既有，禁动
?? scripts/test/resources/kubejs/1.21.1/server_scripts/astral_batch3_probe.js  # 用户既有，禁动
?? zz_screen_probe.py                                        # 用户既有，禁动
```

- `run/` 整体在 `.gitignore:61` ⇒ `run/start-*.sh`、`run/start-*.bat` 都是**被忽略文件**；本批新增的三个 bash 启动脚本不会出现在 `git status`，迁移时要单独拷 `run/`（见 §6 第 0 步）。
- 用户既有 4 个未跟踪文件（上表最后 4 行）本批未改动，也**不允许**动。

## 3 本批交付物（全部相对路径）

| 交付 | 路径 | 说明 |
|---|---|---|
| Linux 规则正本 | `docs/agent/AGENTS-dsh-linux.md` | 370 行；环境/工具链/红线替换口径/代理契约/桌面控制/AutoJS/测试台/互斥锁/交接 |
| 根入口 | `AGENTS.md` | 宿主分派表同时列 Windows/Linux 正本；索引补 Linux 测试台与桌面/AutoJS 通道 |
| 启动脚本 | `run/start-{1.21.1,1.20.1,26.1.2}.sh` | 191/191/210 行、777；`gitignored`；精确 `/proc` 匹配回收，绝不裸 `pkill` |
| Linux 测试台 | `scripts/test/linux/{env,launch,stop}.sh` + `README.md` | bash、零 pwsh 依赖；`env.sh` 只读检查 / `launch.sh` 以 AutoJS health+status 为就绪判据 / `stop.sh` 进程组收停 |
| Jade 翻译补齐 | `forge-1.20.1/src/main/resources/assets/astral_dice/lang/{en_us,zh_cn,ja_jp}.json` | dev 标题屏断言需要的两条 `config.jade.plugin_astral_dice.*`，各语言 2 条 |

**仓库外（不进 git，迁移需重建）**：

| 位置 | 内容 |
|---|---|
| `~/.gradle/init.d/00-aliyun-central-mirror.gradle` | Maven Central 对本机出口 IP 返回 403 ⇒ 仓库创建瞬间把 Central URL 改写为 aliyun（不改仓库文件） |
| `~/.gradle/init.d/10-neoforged-offline-mirror.gradle.disabled` | neoforged 仓库 TLS 曾被切断时的离线镜像，现**停用**（保留 fallback） |
| `~/.mc-mirror/`（597 M / 1,231 文件） | 由 Gradle 模块缓存同步出的本地 maven 仓 |
| `~/.local/bin/neoforge-mirror-sync.sh` | 刷新 `~/.mc-mirror` 的同步脚本 |
| `~/.m2/repository/com/merlinkitsune/starengine/*/1.1.0/` | 四线 starengine 制品：98,734 / 103,605 / 112,634 / 98,970 B |
| `~/.dsh/profiles/web/cordis.patch.yml` | DSH MCP 条目：`mcp-computer-use`（stdio）、`mcp-autojs`（streamable-http `http://127.0.0.1:25585/mcp`）；备份与 sha256 见 `docs/agent/AGENTS-dsh-linux.md` 桌面/AutoJS 两节 |
| `~/.dsh/skills/autojs/SKILL.md` | AutoJS 技能（28,134 B，与 jar 内 SKILL.md 逐字节一致） |
| `~/.local/powershell/pwsh` | pwsh 7.6.9（**可选**只读工具；Linux 测试台不依赖） |

## 4 实测证据摘要

| 线 | 启动 | 就绪证据 | 收停 |
|---|---|---|---|
| neoforge-1.21.1 | `run/start-1.21.1.sh` | AutoJS `/health`=200；窗口 `1280x662+332+203`；`Game took 18.113 seconds to start` | `LINUX_STOP: OK` |
| forge-1.20.1 | `run/start-1.20.1.sh` | `/health`=200；窗口 `1920x1080+320+180`；`Game took 20.971 seconds to start`；25 mods | `LINUX_STOP: OK` |
| neoforge-26.1.2 | `run/start-26.1.2.sh` | `/health`=200；窗口 `1920x1080+320+180`；`Game took 15.236 seconds to start` | `LINUX_STOP: OK` |
| fabric-1.20.1 | `gradlew :fabric-1.20.1:runClient` 回退 | **失败**：Sodium `EarlyDriverScanner` 要 LWJGL 3.3.1，Loom dev runtime 给 3.3.2-snapshot | — |

- 三条通过线的窗口都是 X11 窗口（`xwininfo` 有 id）⇒ **MC 全走 XWayland**，GLFW 无 Wayland 后端；`DISPLAY=:0` 是硬前提。
- 构建：`:neoforge-1.21.1:classes` 2m44s/534 class；`:forge-1.20.1:classes` 2m9s/558 class；`:neoforge-26.1.2:classes --no-configuration-cache` 46–59s（该线**必须**关配置缓存）。
- 26.1.2 另需注意：dev client 已能到标题屏，但停在 `KubeJSErrorScreen`（`astral_wallet_vision.js#38` 报 `StarCoinWalletState: Class could not be found`，而 starengine jar 内确有该类）⇒ KubeJS Rhino 类加载器看不到 jarJar/嵌套 jar 类，P1 遗留；`run/26.1.2/mods/` 还残留旧 `astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`。

## 5 已知缺口与未验证项

1. **fabric-1.20.1 dev 启动失败**（Sodium 要 LWJGL 3.3.1 vs Loom 3.3.2-snapshot）：需在 fabric 构建侧 pin LWJGL 或换 Sodium 版本；与主机无关。
2. **26.1.2 KubeJS 类加载遗留**（见上）：P1 线待排查；`event_ticket` 在该线注册表查询为 `Optional.empty` 属同源现象（KubeJS 错误屏截断了后续初始化）。
3. **Maven Central 仍 403**（本机出口 IP）：aliyun init 必须保留；换到真实机后需**重新实测**，若不再 403 可移除该 init。
4. `scripts/test/mt.conf` 里仍是 Windows 历史路径（Linux 脚本只读取少量键，`env.sh` 会报 WARN 提示，不阻断）。
5. `run/` 被 gitignore：三个 bash 启动脚本不入库，迁移要手动拷（这是仓库既有口径，不改）。
6. 1.20.1 的两份 crash report（`run/1.20.1/crash-reports/`，02:36 / 02:37）保留作 Jade 断言证据，未删除。
7. 本批未 commit：`AGENTS.md` + 三个 lang 文件是已跟踪文件的改动，迁移/接手后需先按用户裁决提交，否则 `git status` 一直脏。

## 6 迁移到真实 Linux 的清单（按顺序执行，命令全部相对仓库根）

**第 0 步 · 冻结与拷贝**
1. 在 VM 上确认无游戏/gradle 进程：`pgrep -af 'net.neoforged.devlaunch|net.minecraft.client.main|gradle-wrapper'`；`bash scripts/test/linux/stop.sh --all`。
2. 拷贝仓库（**带上被忽略的 `run/`**，否则三个 `start-*.sh` 会丢）：
   ```bash
   rsync -a --exclude='.git' --exclude='*/build/' --exclude='.gradle/' \
     <VM仓库根>/ <新机:目标目录>/
   ```
   `.git` 建议另拷（112 MB）以保留历史与 autocrlf 配置；只拷工作树时新机需重新 `git init` 或 clone。
3. 拷贝兄弟仓 starengine 源码（迁移后要用它发布制品）。
4. 把 `run/` 一起拷过去（`run/` 不在 git 里，但启动脚本与 mods 配置都在）。

**第 1 步 · 工具链**
- JDK **17 / 21 / 25**（apt 或 temurin 均可）：`/usr/lib/jvm/java-{17,21,25}-openjdk-amd64`；确认 `./gradlew --version`（daemon 统一用 21）。
- Node ≥20（本机 26.13.0）+ pnpm（DSH 用）、Python 3、`jq`、`curl`、`unzip`、`git`。
- 桌面工具：`xdotool`（XTEST 点击/激活窗口）、`spectacle`（KDE 截图）；KDE Wayland 是 computer-use MCP 的前提。
- 可选：pwsh 7（只读工具用，Linux 测试台不需要）。

**第 2 步 · Gradle 出网适配（按实测决定）**
- 先测 Central：`curl -s -o /dev/null -w '%{http_code}\n' https://repo.maven.apache.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.pom`。若 **200** ⇒ 不需要 aliyun init；若 **403** ⇒ 把 `~/.gradle/init.d/00-aliyun-central-mirror.gradle` 拷过去。
- 真机通常不需要 `~/.mc-mirror` / `10-neoforged-offline-mirror.gradle`；只有当 neoforged 再次出现 TLS 被切断时才启用（把 `.disabled` 改回，并先跑 `neoforge-mirror-sync.sh`）。

**第 3 步 · 发布 starengine 到本机 mavenLocal（必须先做，否则四线都过不了配置期）**
```bash
cd ../starengine
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :neoforge-1.21.1:publishToMavenLocal --console=plain --no-configuration-cache
./gradlew :forge-1.20.1:publishToMavenLocal    --console=plain --no-configuration-cache
./gradlew :fabric-1.20.1:publishToMavenLocal   --console=plain --no-configuration-cache
./gradlew :neoforge-26.1.2:publishToMavenLocal --console=plain --no-configuration-cache
```
每条线首次 MDG 量级 ~13 min（decompile+patch），之后走缓存。

**第 4 步 · 构建**
```bash
cd <仓库根>
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :neoforge-1.21.1:classes --console=plain
./gradlew :forge-1.20.1:classes   --console=plain
./gradlew :neoforge-26.1.2:classes --console=plain --no-configuration-cache   # 该线必须
# fabric-1.20.1 若仍要 dev 运行，先解决 Sodium/LWJGL
```

**第 5 步 · 环境自检与实机（真机无 hgfs 后应更快）**
```bash
bash scripts/test/linux/env.sh
bash scripts/test/linux/launch.sh --version 1.21.1
bash scripts/test/linux/stop.sh  --version 1.21.1
```
（1.20.1 / 26.1.2 同理；就绪判据是 AutoJS `/health`=200 且 `status` 里 client available。）

**第 6 步 · DSH 接入重建（仓库外）**
- 安装/复用 DSH（profile `web`），把 `~/.dsh/profiles/web/cordis.patch.yml` 里的两个 marker 块（`mcp-integrator:computer-use`、`mcp-integrator:autojs`）誊过去；**`computer-use-mcp` 的 bin 绝对路径随 node 安装位置变化，必须改成新机实际路径**。
- `npm i -g @mirsella/computer-use-mcp@2.1.3`（KDE Wayland）；首次 `observe` 会弹 KDE portal 授权，勾「未来会话恢复」后 token 存 `~/.local/state/computer-use-mcp/portal-restore-token`。
- 把 `~/.dsh/skills/autojs/SKILL.md` 拷到新机同相对位置；AutoJS MCP 的 URL 不变（`http://127.0.0.1:25585/mcp`），随游戏启动自动连。
- 重启 DSH web 后验证 MCP 工具面出现 `mcp__computer-use__*` 与 `mcp__autojs__*`。

**第 7 步 · 迁移后复验清单（全过才算迁移完成）**
1. `./gradlew --version` → Gradle 9.2.1 + Launcher JVM 21。
2. `ls ~/.m2/repository/com/merlinkitsune/starengine/*/1.1.0/` → 四件齐。
3. 三线 `:classes` 成功（26.1.2 带 `--no-configuration-cache`）。
4. `bash scripts/test/linux/env.sh` → `ENV_RESULT: OK`（无 FAIL）。
5. 三线 `launch.sh` → `LAUNCH: READY` + `/health`=200。
6. 三线 `stop.sh` → `LINUX_STOP: OK`，`pgrep -af net.minecraft` 空。
7. `ln -s` / 硬链接在真机可用（hgfs 限制消失）。
8. `git status --short -uall` 与 §2 一致（迁移不应引入新改动）。

**第 8 步 · 迁移后可做的清理（本批**未做**，需用户裁决）**
- 提交 `AGENTS.md`、`docs/agent/AGENTS-dsh-linux.md`、`scripts/test/linux/**`、三份 lang 修改。
- 清理历史 Windows 绝对路径：四线 `build.gradle` 的 `D:/.minecraft/versions/...` 推送块（`neoforge-1.21.1/build.gradle:306`、`forge-1.20.1/build.gradle:450`、`neoforge-26.1.2/build.gradle:311`、`fabric-1.20.1/build.gradle:322`）、`.vscode/launch.json` 的 `F:\MCProject\...`。
- 删除 `run/26.1.2/mods/` 旧 `astral_dice-1.3.7-beta.2` jar（与 dev 入口版本混装）。
- 修复 fabric 的 Sodium/LWJGL、26.1.2 的 KubeJS 类加载（P1）。

## 附录 A · 当前虚拟机宿主绝对路径（2026-10-31 快照，迁移后作废）

| 用途 | VM 绝对路径 |
|---|---|
| 仓库根 | `/mnt/hgfs/MCProject/astral_dice_multiloader`（宿主 Windows `F:\MCProject\astral_dice_multiloader`） |
| starengine 源码仓 | `/mnt/hgfs/MCProject/starengine` |
| JDK | `/usr/lib/jvm/java-{17,21,25}-openjdk-amd64` |
| Gradle init | `~/.gradle/init.d/{00-aliyun-central-mirror.gradle, 10-neoforged-offline-mirror.gradle.disabled}` |
| 本地 maven 镜像 | `~/.mc-mirror/`；同步脚本 `~/.local/bin/neoforge-mirror-sync.sh` |
| mavenLocal 制品 | `~/.m2/repository/com/merlinkitsune/starengine/` |
| DSH profile | `~/.dsh/profiles/web/cordis.patch.yml`（备份 `~/.dsh/backup-20261031-013357/`） |
| DSH Skill | `~/.dsh/skills/autojs/SKILL.md` |
| pwsh（可选） | `~/.local/powershell/pwsh` |
| 证据截图（临时） | `/tmp/mc-{title-window,world,1201-title,2612-title}.png` |
| 互斥锁目录 | `/mnt/hgfs/MCProject/.agent-locks/`（本批已清空；三行口径 `PID=/TIME=/TASK=`） |

## 附录 B · 当前状态一条命令自检

```bash
cd <仓库根>
git log -1 --oneline; git status --short -uall | head -20
grep -h '^mod_version' */gradle.properties
ls ~/.m2/repository/com/merlinkitsune/starengine/*/1.1.0/*.jar 2>/dev/null | wc -l   # 期望 4
bash scripts/test/linux/env.sh --quiet; echo "env_exit=$?"
```
