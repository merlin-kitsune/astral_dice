# DSH 在 Linux 原生宿主的规则正本（AGENTS-dsh-linux）

> **定位**：本文件是 **DSH 宿主在 Linux 原生环境**下的主规则。DSH 在 Linux 上先读本文件，再按根 `AGENTS.md` 的共同红线与按需索引执行；**Windows 规则**见 `docs/agent/AGENTS-dsh.md`（历史正本，仅对 Windows 宿主有效）。
> **冲突裁决**：同一宿主上两份文件冲突 ⇒ **Linux 宿主以本文件为准**，Windows 宿主以 `docs/agent/AGENTS-dsh.md` 为准；共同红线以根 `AGENTS.md` 为准。
> **红线原文不复制**：红线区逐字原文的唯一副本在 `docs/agent/AGENTS-dsh.md`〈📍 生效红线区〉（`docs/agent/verify-split.py` 项 1b 拿它和 `docs/agent/AGENTS-full.md` L2–L130 逐字比对）。本文件只给 **Linux 下的替换/补充口径**，复制第二份会让红线两处漂移。
> **事实源原则**：本文数字最后一次全量复核 = **2026-11-10 实读快照**（2026-10-31 迁移批 + 2026-11-10 校准批）；宿主版本、路径、构建命令、日志路径一律以**实读命令**为准，文档只是当次快照。`[[PENDING:…]]` = 尚无实测依据，**不得**当已验证结论引用，也不得据此施工。
> **🚚 2026-10-31 迁移批（本文件本轮改写的原因）**：宿主已从 **VMware 虚拟机 + hgfs 共享盘** 迁到 **原生 Linux 真机（双系统，与 Windows 共用同一块 NTFS 盘）**。旧的 `/mnt/hgfs/*`、`~/Project` 四副本、`rsync-original.sh` 通道、`computer-use`(mirsella)/`autojs` MCP 口径**全部作废**，一律以本文件为准；VM 时代文档只作历史（`docs/handover-2026-10-31-linux-vm-adoption.md（已删除 2026-11-05）`、`docs/handover-2026-10-31-local-disk-workspace.md`）。迁移实测见 `docs/handover-2026-10-31-native-linux-migration.md`。
> **🔧 2026-11-10 校准批（本文件本轮改写的原因）**：只做**事实源对齐**，不改任何判据。改动面：工作树改为 `-next`（`multi-dev-next` @ `3a6df49c`）、Gradle 9.2.1→9.5.1、`~/.gradle` 空→3.4 GB、`build/moddev/artifacts` 两树均无、starengine 1.1.0→`26.4-SNAPSHOT.{2,9,11}` 且 mavenLocal 缺件、未跟踪清单 4 条→715 条、子代理 provider →`deepseek-account`、复核通道 →独立 subagent + `review-agent`、AutoJS MCP 已注册、JDK 表补 `fabric-1.21.1`、红线区间 `L2–L130`、TOC 漂移与守门读数。下次复核请整体重走一遍「实读覆盖」而不是只改数字。

## 🚀 会话启动第一步：加载 using-superpowers（2026-10-24 用户裁决，每次会话必做）

- 回复任何内容、调用任何工具**之前**，先调用 `skill` 工具、`name="using-superpowers"`。
- 该技能规定「先查技能目录，1% 可能适用就须加载」，并负责其它流程类技能（brainstorming / writing-plans / systematic-debugging / verification-before-completion 等）的按任务加载。
- `skill` 工具不可用（未注册 / 加载失败）时，**必须**在回复里明写「本次会话未能加载 superpowers」，不得假装已加载。

## ⚙️ 环境与工具链口径（Linux 原生）

### 1. 主机与桌面（实读）

- **物理机**：`systemd-detect-virt` = `none`，DMI `MS-7E59` / `Micro-Star International Co., Ltd.`；**不是 VMware / WSL / 容器**。
- 发行版 `Ubuntu 26.04.1 LTS`（`VERSION_CODENAME=resolute`），内核 `7.0.0-43-generic`；用户 `kitsune`，登录 shell `/bin/bash`。
- 桌面 = **KDE Plasma**，会话类型 **Wayland**：`XDG_SESSION_TYPE=wayland`、`WAYLAND_DISPLAY=wayland-0`、`DISPLAY=:0`、`XDG_RUNTIME_DIR=/run/user/1000`。
- **XWayland 是 MC 的硬前提**：MC 1.20.1 / 1.21.1 / 26.1.2 的 GLFW 只有 X11 后端，Wayland 会话下 `DISPLAY=:0` 必须存在（`run/start-*.sh` 在其为空时直接 `exit 1`；各线窗口在 VM 期实测都是 X11 窗口，真机沿用同一结论）。
- **双系统互操作是本机的新前提**：本机 Linux 与 Windows 交替启动，共用同一块 NTFS 盘（Windows `F:` = `/mnt/f`，`D:` = `/mnt/d`）。用户硬约束（2026-10-31）：**Windows 下的开发配置与资产不可改**——本文件所有动作只动 Linux 侧、只动仓库内该动的文件。
- **AppArmor 已启用**（`/sys/module/apparmor/parameters/enabled=Y`，2026-11-10 实读）；无 SELinux；**没有** hgfs、9p、WSL、`~/Project`。

### 2. 路径（绝对路径纪律）

| 用途 | Linux 路径（实读） | Windows 侧同一批文件 |
|---|---|---|
| **主线工作树（`multi-main`，封版线）** | `/mnt/f/MCProject/astral_dice_multiloader` | `F:\MCProject\astral_dice_multiloader` |
| 前置库 starengine | `/mnt/f/MCProject/starengine` | `F:\MCProject\starengine` |
| **当前开发树（`multi-dev-next`，本会话树）** | `/mnt/f/MCProject/astral_dice_multiloader-next` | 同名 |
| 宿主互斥锁 | `/mnt/f/MCProject/.agent-locks/` | `F:\MCProject\.agent-locks\` |
| **整合包根（Minecraft 实例）** | `/mnt/d/.minecraft`（`MC_HOME` 可覆盖） | `D:\.minecraft` |
| 本机 mavenLocal | `/home/kitsune/.m2/repository/` | `%USERPROFILE%\.m2\repository`（**不是同一份**） |
| DSH 用户级配置 | `/home/kitsune/.dsh/` | `C:\Users\<user>\.dsh\`（**不是同一份**） |
| 盘挂载 | `/mnt/c`(ntfs3 **ro**) `/mnt/d` `/mnt/e` `/mnt/f`(ntfs3 rw) | `C:` `D:` `E:` `F:` |

- **仓库就是正本，不再有副本层**：`~/Project/*` 四副本（VM 期产物）**已不存在**；`scripts/dev/rsync-original.sh` 已停用（默认 `exit 3`，仅 `ALLOW_LEGACY_HGFS_RSYNC=1` 可越过）。
- 日常命令在仓库根内用相对路径（`./gradlew`、`bash scripts/test/linux/env.sh`），脚本自解析 `ROOT="$(cd "$(dirname "$0")/.." && pwd)"`；**两棵树共用同一个 `.git`**——`-next/.git` 是**文件**，内容 `gitdir: ../astral_dice_multiloader/.git/worktrees/astral_dice_multiloader-next`，⇒ 分支/HEAD/未跟踪清单一律按树分别 `git -C <树>` 实读，不要以为是一份仓库；**跨仓库/跨盘**（starengine 兄弟仓、`-next` 树、`/mnt/d/.minecraft`）必须写绝对路径。
- 文档/交接一律写相对仓库根的路径；宿主绝对路径只放交接文档的「附录 A 快照」。
- `/mnt/c` 是 Windows 系统盘、**只读挂载**；任何产物都不要往 `/mnt/c` 写。

### 3. NTFS 挂载特性（替代旧 hgfs 节）

- 挂载实读：`/dev/nvme2n1p1 on /mnt/f type ntfs3 (rw,uid=1000,gid=1000,dmask=0077,fmask=0077,acl,iocharset=utf8,prealloc)`；`/mnt/d`（`/dev/nvme0n1p5`）同参数；`/mnt/c`（`/dev/nvme0n1p3`）为 `ro`；根 `/` 为 ext4（`/dev/sda2`）。
- **链接与原子写实测（2026-10-31，`/mnt/f` 临时目录）**：`ln -s` **OK**、硬链接 **OK**、同目录 `mv` **OK**、`sed -i` **OK**、`python3 os.replace` 原子替换 **OK**、`chmod +x` 后可直接执行 **OK**。
  - ⇒ VM hgfs 上「不能用 `write`/`edit`（硬链接 EPERM）、只能 `cat > <<'EOF'`」的限制**已消失**：Linux 侧文本编辑可直接用 `write`/`edit` 工具，改完仍按〈命令执行规范〉做 `file` / `wc -l` 自检。
- **目录属主坑（已修复，留作复发预案）**：2026-10-31 用户明确授权后，已对本机 4 个仓库（`astral_dice_multiloader` / `astral_dice_multiloader-next` / `starengine` / `starengine_next`）执行 `sudo chown -R kitsune:kitsune`，`uid 1001` / `gid 1002` 归零，并配好免密 sudo（`/etc/sudoers.d/90-kitsune-nopasswd`）。**历史症状**：`docs/agent/` 曾是 `1001:1002`/`755` ⇒ `kitsune`(1000) 对目录无写权限，`write` 的 tmpdir+rename 报 `EACCES`，只能覆盖已属自己的既有文件。**若复发**（从 Windows 侧新建/复制进来的文件可能带 Windows 侧 uid）：先 `stat -c '%u:%g %n' <路径>` 确认，再 `find <repo> -uid 1001` 列全，**报用户后再 chown**，不要自行扩大范围。
- **同一坑的第二面（构建树写不进去）**：症状是 `publishToMavenLocal` 报 `Could not write to file '.../build/publications/mavenJava/pom-default.xml'`，或 Gradle 写 `build/reports/problems/problems-report.html` 时 `AccessDeniedException`（**多线同一处失败，不是依赖问题**）。构建树已随上面的 chown 修好；**无需 root 的应急绕法**仍是：`mv <line>/build <line>/build.vmresidue-<日期>` → `cp -a` 复制回 `build/`（新目录属自己，mtime 保留）⇒ `createMinecraftArtifacts` 仍判 `UP-TO-DATE`，不重新 decompile；这样留下的残留目录要 root 才能删（2026-10-31 已清掉 7 个、约 478 MB）。
- **大小写**：Linux 的 ntfs3 视图**区分大小写**（`CaseTest.txt` 存在时 `casetest.txt` 不存在）；Windows 侧不区分。仓库 `core.ignorecase=true`（Windows 克隆遗留）⇒ **只改大小写的重命名**可能被 git 当成无改动；需要改名时用 `git mv` 并 `git status` 复核真实文件名。
- **权限位不可信**：挂载带 `dmask=0077,fmask=0077,acl`，文件常显示 `700`/`600`；**不要**用权限位判断可执行性，`gradlew`/`*.sh` 直接试跑（`bash gradlew` 永远可用）。
- **I/O 比 ext4 慢**：本机 ntfs3 的倍数**尚未系统实测**，不编数字；Gradle 缓存（`~/.gradle`，在 ext4 家目录）与 `build/`（在 NTFS）分居两盘。大临时文件优先 `/tmp/dsh-<session>/`。
- **双系统互操作**：同一时刻只有一个 OS 在跑；不要在 Windows 休眠/快速启动占用该盘时写盘。中文/日文目录名正常（`/mnt/d/.minecraft/versions/狐の航空学 Voxy Edition` 实读存在）。

### 4. JDK（daemon 统一 21；toolchain 17/21/25 由 Gradle 自动选）

- `/usr/lib/jvm/` 实读有 `java-17-openjdk-amd64`、`java-21-openjdk-amd64`、`java-25-openjdk-amd64`。
- **系统默认 `/usr/bin/java` = `openjdk 21.0.13.0.3 2026-10-24`**（2026-11-10 实读；**VM 期默认是 25，已变**——不要沿用旧文档的 25 口径）。
- **Gradle daemon JVM 必须 ≥ 21：本仓所有 `./gradlew …` 一律先 `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`**。
- 反例证据（VM 期实测，机制未变）：`JAVA_HOME=…java-17 ./gradlew :fabric-1.20.1:publishToMavenLocal` 配置期失败：`Could not resolve net.fabricmc:fabric-loom:1.14.10` → `Dependency requires at least JVM runtime version 21. This build uses a Java 17 JVM.`；`settings.gradle` 无条件 include 各线，故连 `:forge-1.20.1` 也会在 `Configure project :fabric-1.20.1` 崩。
- 下表是各子项目的 **`java.toolchain.languageVersion` 编译目标**，**不是 daemon 选择依据**（daemon 固定 21，Gradle 自动选对应 JDK 编译）：

| 子项目 | 工具链 Java | 依据 |
|---|---|---|
| `neoforge-1.21.1` | 21 | `neoforge-1.21.1/build.gradle:63` `JavaLanguageVersion.of(21)` |
| `forge-1.20.1` | 17 | `forge-1.20.1/build.gradle:63` `of(17)` |
| `neoforge-26.1.2` | 25 | `neoforge-26.1.2/build.gradle:75` `of(25)` |
| `fabric-1.20.1` | 17 | `fabric-1.20.1/build.gradle:25` `of(17)` |
| `fabric-1.21.1` | 21 | `fabric-1.21.1/build.gradle:24` `of(21)`（2026-11-10 补：旧文档只有四线） |

- `settings.gradle:29-33` 无条件 include **五条线**（`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1` / `fabric-1.21.1`）⇒ 任一线配置期失败会拖垮整仓任意目标。
- toolchain 由 `settings.gradle` 的 `org.gradle.toolchains.foojay-resolver-convention` 1.0.0 解析；**优先本机 `/usr/lib/jvm`**，foojay 仅在本机缺该版本时才下载。策略：**以显式 `JAVA_HOME` 为主**，不把自动下载当默认兜底；本机三套 JDK 已在位。
- **不要**按线把 `JAVA_HOME` 切成 17 去跑 gradle（17 会崩在 loom classpath）；**统一 21** 作 daemon，各线构建与实机共同验证过（25 亦可跑，不作默认）。
- **wrapper 自检（本批实跑通过）**：`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --version` ⇒ `Gradle 9.5.1` / `Launcher JVM: 21.0.13.0.3` / `Daemon JVM: /usr/lib/jvm/java-21-openjdk-amd64` / `OS: Linux 7.0.0-43-generic amd64`（2026-11-10 复核读数）。

### 5. Gradle（唯一构建入口）

- wrapper：每棵树自带 `gradlew`（`<树>/gradlew`，实读 8,733 B、可执行；`-next` 与主线树各一份，别混用），`gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl=https\://mirrors.aliyun.com/gradle/distributions/v9.5.1/gradle-9.5.1-bin.zip` ⇒ **Gradle 9.5.1，aliyun 镜像**（两棵树一致；旧文档 9.2.1 已过期）。
- **`~/.gradle` 已 3.4 GB 非空**（2026-11-10 实读：`dists/` 同时有 `9.2.1` 与 `9.5.1`，另有 `caches/`、`daemon/`、`native/`、`wrapper/`）⇒ 首次下 dist 的一次性成本**已付**。旧文档「迁移后为空」已过期；若哪次报 dist 缺失，先 `ls ~/.gradle/wrapper/dists/` 再决定重下，别误判成构建失败。
- 构建命令（**Linux 用 `./gradlew`，没有 `gradlew.bat`**）：

  ```bash
  cd /mnt/f/MCProject/astral_dice_multiloader-next   # 本会话树；要动主线树必须显式改成本仓主树路径
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # daemon JVM，必须 ≥21；不要看到线是 17/25 就改这里
  ./gradlew :neoforge-1.21.1:classes --console=plain
  ./gradlew :neoforge-1.21.1:jar --console=plain
  ```

- 铁律：**所有 `./gradlew …` 之前 `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`**；编译目标由 `java.toolchain` 决定，与 daemon JVM 无关。
- 根 `gradle.properties` 实读：`org.gradle.jvmargs=-Xmx3G`、`daemon=true`、`parallel=true`、`caching=true`、`configuration-cache=true`。
- **代理只覆盖 Gradle 进程，不覆盖游戏 JVM**（2026-11-10 实机取证）：`~/.gradle/gradle.properties` 的 `systemProp.*.proxy*` 只对 Gradle 生效；实机客户端 JVM 仍是直连，于是 Forge 自带 `[Forge Version Check/WARN] net.minecraftforge.fml.VersionChecker` 拉 `https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json` 会抛 `java.net.http.HttpConnectTimeoutException: HTTP connect timed out`（1.20.1 那轮共 6 行 ERROR 级噪声）。**这是网络口径差异，不是回归、不阻断加载**。要验证真伪看有没有新 crash-report 与 `AP_*` 标记，而不是数 ERROR 行数。
- **单 gradle 并发位**：同一工作树同一时间只允许一个 gradle；启动前查锁（〈互斥锁与并发〉）与 `pgrep -af gradle`。
- **依赖镜像：真机已不需要 aliyun init.d**（本批最重要的出网变化）：
  - 2026-10-31 真机实测：Central / aliyun / neoforged / github **全部 200**（`annotations-26.0.2.pom` 实拉）。**VM 期的 Central 403 封禁不存在了。**
  - ⇒ **不要创建 `~/.gradle/init.d/00-aliyun-central-mirror.gradle`**，也不要往仓库写镜像坐标；`~/.gradle/init.d/`、`~/.mc-mirror`、`~/.local/bin/neoforge-mirror-sync.sh` 不存在都是正常状态。
  - 首次构建前跑一次自检（坏了才动手，别预防性加镜像）：`curl -s -o /dev/null -w '%{http_code}\n' https://repo.maven.apache.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.pom` ⇒ 期望 **200**；若再 403，才按 `docs/handover-2026-10-31-linux-vm-adoption.md（已删除 2026-11-05）` §6 第 2 步临时启用 host 级镜像（不改仓库）。
- **26.1.2 必须加 `--no-configuration-cache`**：`createMinecraftArtifacts` 的 `DefaultConfigurableFileCollection` 在配置缓存序列化阶段报 `error writing value of type …`（历史实测，与镜像无关）。
- **构建耗时：热缓存已实测，冷构建仍未测**（`[[PENDING:build-time-native]]`）。2026-10-31 真机热缓存：`:neoforge-1.21.1:classes` 32s、`:forge-1.20.1:classes` 30s（含各线配置期，`createMinecraftArtifacts UP-TO-DATE`），日志同时打印 `整合包根目录 (MC_HOME): /mnt/d/.minecraft`。`build/moddev/artifacts` **两棵树都不存在**（2026-11-10 实读）⇒ 旧文档「可复用、省掉 decompile」已失效，首次构建会真的走 decompile+patch，别拿旧的低耗时当基线。**禁止**把 VM 的 `2m44s` 当本机基线，也不得假定 `12m23s` decompile 必然发生。
- 换线构建耗时与失败模式以**构建台账**为准（每次记录线 / 命令 / 耗时 / 结果），单次耗时不当 SLA。

- **fabric-loom 1.17.21 要求 Gradle ≥ 9.5**（2026-11-10 两向实测）：本树 wrapper = 9.5.1 ⇒ 正确；改用 9.2.1 立刻报 `No matching variant of net.fabricmc:fabric-loom:1.17.21 ... attribute 'org.gradle.plugin.api-version' with value '9.2.1'`。⇒ **不要为了「稳」把 wrapper 降到 9.2.1**（starengine_next 用 9.2.1 能过，是因为它引的 loom 版本更旧）。
- **出网修正（2026-11-10，本批最重要的构建变化）**：TUN **并不**通 maven.neoforged.net（直连 30s 超时 `code=000`，走 `-x http://127.0.0.1:7897` 则 1.06s / 200），maven.fabricmc.net 直连被限速到 ~27 KB/s。⇒ 已建 **`~/.gradle/gradle.properties`**（原先不存在），内容只有：`systemProp.http.proxyHost=127.0.0.1` / `systemProp.http.proxyPort=7897` / 同款 `https.` 两项 / `systemProp.http.nonProxyHosts=localhost|127.0.0.1`，带注明「[dsh 2026-11-10] … 走 clash HTTP 终点」。**这是本机构建能通的前提之一，别当垃圾删**；**2026-11-10 用户裁决：此代理配置长期保留**（不随会话清理）。git 侧口径不变（仍直连，见〈7. git〉）。
  - **误报陷阱**：代理没配好/缓存残缺时，loom 解析会以**伪装成依赖冲突**的样子失败 —— `Could not resolve net.fabricmc:tiny-remapper:0.14.0 ... Cannot choose between the available variants ... The only attribute distinguishing these variants is 'org.gradle.dependency.bundling'`（`external` / `shadowed`）。看到这条**先查网络与 Gradle 缓存**（`curl -x http://127.0.0.1:7897 -o /dev/null -w '%{http_code}' <artifact-url>`），**不要**改 fabric 依赖、也不要排除其它线。
  - 订正：本文件上一版把这条报错判成「Gradle 9.5.1 变体歧义 / 版本缺陷」并写了临时排除 `settings.gradle` 的绕行，**结论错误，已作废**（9.5.1 在代理修好 + 缓存补齐后完全正常）。
  - **2026-11-10 复核实据**：代理与缓存补齐后 `JAVA_HOME=…21 ./gradlew :fabric-1.20.1:tasks --console=plain -q` ⇒ **EXIT=0**（fabric 线路由/插件解析正常）。⇒ 当时临时注释 `settings.gradle` 里 fabric 两行属**多余动作**，已原样恢复（`git checkout -- settings.gradle`，sha256 `f0e8c268…dccb`）；不要再照抄那个绕行。
- 2026-11-10 收尾实测（活动树，`settings.gradle` 已还原为 5 行 include）：`./gradlew help` = BUILD SUCCESSFUL 1m26s；`./gradlew :neoforge-1.21.1:classes` = **1.4s**（4 executed / 1 from cache）；收尾零残留 java（daemon 已 `--stop`）。⇒ 网络 + mavenLocal 两处修好后，**P0 线可直接构建，不需要改任何仓库文件**。

### 5.1 datagen（各线入口不同，2026-11-10 全部实跑通）

- **入口按加载器分**：fabric 两线 = `runDatagen`（Loom 数据生成，工作目录 `fabric-<线>/run/datagen`，产物落 `fabric-<线>/src/generated/...`）；neo26 = **`runServerData` + `runClientData` 两个任务**（服务端产物 `neoforge-26.1.2/src/generated/resources`，客户端产物 `neoforge-26.1.2/src/generated/clientResources`）。四条任务可一条命令跑完：

  ```bash
  cd /mnt/f/MCProject/astral_dice_multiloader-next
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  ./gradlew --no-daemon :fabric-1.20.1:runDatagen :fabric-1.21.1:runDatagen \
    :neoforge-26.1.2:runServerData :neoforge-26.1.2:runClientData --console=plain
  ```

  2026-11-10 实测：`BUILD SUCCESSFUL in 13s`（热缓存），日志 `temp/datagen/run5-idempotent.log`。**幂等验证法**：连跑两遍 → `git add -A` → `git diff --cached --stat | md5sum` 两次一致（本批 `7dfbf0e31550fc41f5034b9fa585df91`）。
- **26.1.2 的 `runClientData` 在 Sodium 下必崩**（2026-11-10 实测，exit 134 / SIGBUS）：Sodium 探到 `LinuxPciAdapterInfo[vendor=NVIDIA, name=GB202 [GeForce RTX 5090]]` 后打 `[NVIDIA_THREADED_OPTIMIZATIONS_BROKEN, NO_ERROR_CONTEXT_UNSUPPORTED]`、"Modifying process environment"，随后 JVM 崩在 `ld-linux-x86-64.so.2`（JRE 25.0.5）。绕法：跑前把 `run/26.1.2/mods/sodium-neoforge-*.jar` **与** `iris-neoforge-*.jar` 一起临时移出（只移 sodium 会因 iris 硬依赖报 `Mod iris requires sodium 0.6 or above`），跑完必须放回。
- **谁在跑 datagen 之前必须先补 starengine**（见〈6. 前置库 starengine〉），否则配置期就挂。
- **仓里 generated JSON 由 Gson 写出、无尾换行**；手抄 JSON 带尾换行 ⇒ 重跑 datagen 会以「去尾换行 / 键顺序」形式产生大量纯格式 diff。**以 datagen 输出为真值**，不要手工把手抄格式改回去。语义漂移（判据键 `has_star_coin_dust`→`has_blank_chip` 等）也要跟着接受。
- **provider 缺失会让 datagen「静默删文件」**：2026-11-10 fabric-1.21.1 重跑时删掉了 `sykes_sign` 的 recipe/model（该线 provider 没登记）。⇒ 看到 generated 里 `D` 了既有产物，先查对应线 `datagen/Mod{Recipe,ItemModel}Provider.java` 是否漏登记，而不是把删除当正常产出。
- **fabric-1.20.1 的 datagen 长期跑不通**（本批已修）：该线 `ModItemModelProvider extends FabricModelProvider`，原版 `net.minecraft.data.models.ModelProvider` 会用 `BuiltInRegistries.BLOCK` 与本次生成的 blockstate 求差集，而本模组方块是手写 blockstate/model ⇒ 必报 `Missing blockstate definitions for: [card_shop, chip_shop, event_drawer, barricade_collision, abyss_tentacle_collision]`。修法：该文件改 `implements DataProvider`（照 fabric-1.21.1 同文件），用 `DataProvider.saveStable` 手写 item model，不再继承 FabricModelProvider。
- **NTFS 上的假 M**：datagen 重写文件后 `git status` 会冒出几百条 M，但 `git diff` 为空、`git hash-object` 与索引一致、`git update-index --really-refresh` 仍报 needs update。处置：对目标路径 `git add` 一次即恢复干净；**判真正改动只看 `git diff --numstat`**。
- 跨线债务：`abyss_tentacle_collision` 的 blockstate/model 目前**只有 26.1.2** 有（P0 两线与 fabric 两线都缺）⇒ 该占位方块在缺的线上渲染为缺失模型；补与不补待裁决。

### 6. 前置库 starengine（`26.4-SNAPSHOT.{2,9,11}`，必须先进 mavenLocal）

- 各线 `gradle.properties` 实读（2026-11-10）：`starengine_group=com.merlinkitsune.starengine`；引脚 **`neoforge-1.21.1` = `26.4-SNAPSHOT.2`**、**`forge-1.20.1` = `26.4-SNAPSHOT.2`**、**`neoforge-26.1.2` = `26.4-SNAPSHOT.2`**、**`fabric-1.20.1` = `26.4-SNAPSHOT.11`**（`starengine_version_range=>=26.4-SNAPSHOT.11 <27.0.0`）、**`fabric-1.21.1` = `26.4-SNAPSHOT.9`**（range `>=26.4-SNAPSHOT.9 <27.0.0`）。旧文档的 `1.1.0` 已过期。各线 `build.gradle` 均含 `mavenLocal()`（neo `:30` / forge `:29` / neo26 `:39` / fabric `:47`）。
- 正确顺序：先在**库仓**发布到本机 mavenLocal，再构建本仓。缺哪条线的制品，哪条线就解析失败（`Could not resolve com.merlinkitsune.starengine:...`）。
- **库仓要选对（2026-11-10 实读）**：本开发树对应的库仓是 **`/mnt/f/MCProject/starengine_next`**（settings.gradle 有 5 个 include，含 `fabric-1.21.1`）；旧的 `/mnt/f/MCProject/starengine` 只有 4 个 include（**无 fabric-1.21.1**）。两条都自带 gradlew（wrapper 均为 9.2.1）。
- **R1 阻塞已销账（2026-11-10 本会话实做）**：在 `starengine_next` 依次发布五平台，`:neoforge-1.21.1` 1m10s、其余四平台合计 1m36s，均 BUILD SUCCESSFUL；`~/.m2/repository/com/merlinkitsune/starengine/` 现为 **`neoforge-1.21.1` .2 / `forge-1.20.1` .2 / `neoforge-26.1.2` .2 / `fabric-1.20.1` .11 / `fabric-1.21.1` .9**（各三件齐，与五线引脚逐字符相等）。⇒ 换机/清 `~/.m2` 后重做这一批即可，**不必**再猜版本。
- **mavenLocal 缺件史（2026-11-10 上午实读，现已销账）**：当时 `~/.m2/repository/com/merlinkitsune/starengine/` 下只有 4 个平台目录、各只含 `1.1.0` 与 `26.4-SNAPSHOT.1`，新版 `.2`/`.9`/`.11` 全缺、`starengine-fabric-1.21.1` 整件缺失 ⇒ 配成 `Could not resolve`。**当日下午已按上一条完成五平台发布，此阻塞不再存在**；换机 / 清 `~/.m2` 后会复现，届时照上面顺序重发。starengine 实件在兄弟仓 `/mnt/f/MCProject/starengine_next/build/libs/`。
- 发布命令（daemon JVM 必须 21）：
  ```bash
  cd /mnt/f/MCProject/starengine_next   # 注意是本开发树对应的库仓，不是旧的 starengine
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  ./gradlew :neoforge-1.21.1:publishToMavenLocal --console=plain
  ./gradlew :forge-1.20.1:publishToMavenLocal    --console=plain
  ./gradlew :neoforge-26.1.2:publishToMavenLocal --console=plain
  ./gradlew :fabric-1.20.1:publishToMavenLocal   --console=plain
  ./gradlew :fabric-1.21.1:publishToMavenLocal   --console=plain
  ```
  本机首次已发布成功（2026-10-31，各线 `BUILD SUCCESSFUL`，各约 1s——因为 Windows 时代的 `build/moddev` 产物被 `cp -a` 保留，未重新 decompile；若 `build/` 被清空，首次会触发 decompile+patch，VM 量级 12m23s / 12m46s）。发布完成的判据：`ls ~/.m2/repository/com/merlinkitsune/starengine/starengine-<线>/<引脚版本>/` **三件齐**（jar + pom + module），且版本号与该线 `starengine_version` 逐字符相等（2026-11-10 实读旧目录只有 3 件，旧文档写「四件」是错的）。
- **若解析失败属环境问题（BLOCKED），不是产品缺陷。**

### 7. git（真实工作树 + autocrlf + 无全局代理）

- **按树分别实读**（2026-11-10）：本会话树 `-next` = 分支 `multi-dev-next`、HEAD `3a6df49c`；主线树 = 分支 `multi-main`、HEAD `74c1e932`。旧文档「分支 `multi-main` / HEAD `c31d36f6`」是本文件迁入时的快照，已过期。
- 仓库级 `core.autocrlf=true`、`core.ignorecase=true`（Windows 克隆遗留）；`core.eol` 未设。
  - `.gitattributes` 只对 `src/generated/**` 的 json/cache 与 `gradlew`、`*.sh`、`*.py`、`*.conf` 强制 LF，**`.md` 不强制**。
  - **行尾现状（本批实读）**：`AGENTS.md`、`docs/agent/PROJECT-REFERENCE.md`、`build.gradle` 是 **CRLF**；`docs/agent/AGENTS-dsh-linux.md`、`scripts/test/linux/*.sh` 是 **LF**。改 CRLF 文件要按字节替换保 CRLF（`edit` 工具按 `\r\n` 匹配容易失手），改 LF 文件直接写 LF。
  - **不要整份重写既有 `.md`**（会把 CRLF 全变 LF 造成假 diff）；新建文件用 LF 并如实报告编码。
  - **只改大小写**的重命名在 `core.ignorecase=true` 下可能被 git 忽略 ⇒ 用 `git mv` + `git status` 复核。
- `git status --short -uall` 实读（2026-11-10，`-next` 树）= **715 条，全部 `??`、0 条已跟踪改动**：709 条在 `work/`（audit 日志与脚本）、`models/{inkshadoweffect,littleslime,vacuumhead,winegourd,winegourdbetter}.bbmodel` 五个、根 `立牌计划表.txt`。**全部属用户素材/待裁决，不得 add/commit/stash/删除**（旧清单里的 `TMP-ATTRLESS-HYDRA-1.21.1.json` 与 `zz_screen_probe.py` 已不存在）。
  - 主树同期另有 **427 条 `??` + 2 条 `M`**（`docs/agent/AGENTS-dsh-linux.md`、`docs/archive/card-recipes-2026-10-09/README.md`）——那是**另一棵树的他人未提交改动**，本会话只读不碰，遇到就报用户。
- **出网 = clash-verge-rev TUN 全局接管，直连**（2026-10-31 用户裁决 + 实读 `/configs`：`tun.enable=true`、`device=Meta`、`auto-route=true`、`inet4-address=198.18.0.1/30`，内核 mihomo `v1.19.32`）⇒ 远程 git **直接执行，不加 `-c http.proxy=…`**，也不要 `export *_proxy`（环境里本来就没有）。
  - **仓库 `.git/config` 仍有 `http.proxy`/`https.proxy` = `http://127.0.0.1:7897`**（Windows 期遗留、与 Windows 共用，**不要改**）。7897 仍在听，走它也能通；要单条命令**严格直连**就 `git -c http.proxy= -c https.proxy= <子命令>` 覆盖，**不要** `git config --unset`。
  - 查代理状态用 clash-verge-rev 外部控制：`curl -s -H 'Authorization: Bearer <secret>' http://127.0.0.1:9097/configs`（`/version` 看内核）；`secret` 取 `~/.local/share/io.github.clash-verge-rev.clash-verge-rev/config.yaml` 的 `secret:` 字段 —— **不要把明文 secret 写进仓库或日志**。`401 Unauthorized` 是 secret 不对，不代表断网。
- **git hooks 在 Linux 上空转**：四个 hook 是三段 旧 shell 探测版，本机 PATH 无 旧 shell（`/mnt/c` 里是 Windows PE，不可执行）⇒ 静默 `exit 0`，松散引用自愈不执行；遇 `HEAD -> unknown revision` 先查 `git reflog`。提交前**手动**跑 `python3 -B docs/agent/verify-split.py`（四项 `[OK]`）与 `python3 tools/verify_sign_display_names.py`（`GATE: PASS`）。

### 8. 测试收尾必须关闭游戏进程（2026-10-18 裁决的 Linux 版）

- 任何轮次（含中断/失败/超时）结束，**必须**把本轮起的 Minecraft 客户端进程收干净：`kill -TERM <PID>`（超过 10s 仍在 ⇒ `kill -KILL <PID>`），随后 `ps -eo pid,comm,args | grep -i '[j]ava'` 与 `pgrep -af 'net.minecraft|fabric|forge|neoforge'` 双核对，报告里贴**原文**与计数。
- **禁止无人观察驻留**；唯一例外是失败取证：标记 **`.mt_keep_alive-<版本>`**（语义与 Windows 一致）；取证完立刻收停。
- **只杀自己起的 PID/进程组**：禁 `pkill -f java`、`killall java`（会误杀用户其它 Minecraft / IDE / Gradle daemon）。
- 标准收停 = `bash scripts/test/linux/stop.sh --version <线>`（输出 `LINUX_STOP: OK` 才算无残留；`--purge-saves` 按需清测试世界）。
- 收尾还要确认没有残留 gradle daemon 占住构建位（`./gradlew --stop` 或保留 daemon 但记录 PID）。

## 下沉到 StarEngine 的判定规则（2026-10-19 裁决，**与宿主无关**，原文口径保留）

1. **不含饰品 API**：涉及 Curios / Trinkets / Accessories 的类不下沉；`item/CurioSlotUtil` 明确排除。
2. **不下沉筹码流派体系类**：`item/ChargeManager`、`item/HealingManager`、`item/StarLightManager` 排除。
3. **第三方 API 注册放 starengine**，本仓只保留下沉后的关键联动点。
- 候选（按安全度）：`target/HoldToSelect`（最安全）> `event/EffectTimerGuard` > `combat/PartyRelations` > `combat/EliteTargets` > `target/SelfTargetable`。
- 下沉后必须回跑两线编译与守门；Linux 侧守门现状见〈测试台现状〉。

## ⛔ 代理协作契约 —— A 方案 + 子代理职业指定（Linux 宿主同样生效）

### A. 主 Agent 的额度与动作口径

- 主 Agent **每回合 ≤ 6 个请求**；**不亲自执行任何命令**（构建、测试、改源码一律派子代理）。
- **一次派发 + 一次等待**：`wait_agent` 用长超时（**≥ 600s**），**禁止**轮询（反复 `list_agents` / `wait_agent` 短超时）。
- **验收只看产物**：`文件:行号` + 命令输出片段 + `git rev-parse --short HEAD`；不认口头「已完成」。
- **上下文闸门**：> 15–20 回合或 > 25 万 tokens ⇒ 落盘 `plan.md` / `findings.md`（放 `temp/`，不入库）后新开会话。
- **子代理模型固定且唯一**：派活时显式写 `provider: "deepseek-account"` + `model: "deepseek-flash"`（= DeepSeek-V4.1-Flash，2026-11-10 实读本机注册表）。本机**没有** `deepseek-official` 这个 provider，旧文档写的那个会派活失败；换环境先用 `list_subagent_models` 实读再写。**不得**换其它 model/provider；路由失效 ⇒ 停下报用户，不自行降级。

### B. 子代理职业指定（brief 第一句）

- brief **第一句**必须是「你是一位<职业>（<加载器>/MC <版本>），<该职业的交付标准>」。
- 任务 → 职业对照：模组开发 / 跨加载器移植 / 项目文件分析员 / 本地化工程师 / 构建 CI 工程师 / JVM 崩溃排查 / 独立功能审查员 / 技术文档工程师 / 资源包与手册工程师。
- **免人设句必须原文带**（2026-10-22 起）：「不要使用任何人设 / 角色扮演 / 语气修饰 / 昵称自称，按你的职业身份以中性技术口吻输出」。
- 审查取证类一律用**全新** `subagent`（不吃本会话上下文，避免自证）。
- 职业指定的三个作用：约束动作边界、给质量标准、固定模型。

### B-2. 派活通道（只走 DSH 自带）

- 通道：`subagent` / `subagent_fork`；团队并行用 `spawn_teammate` + `team_task_*`（`write_scopes` 互不重叠、`blocked_by` 定序，写完由 Lead 复核最终 diff）。**复核不走 `code_review_*`**（2026-11-07 起弃用），见〈二次验证规范〉。
- 纪律：一次一件（一个可独立验收的改动面 + **一个**编译目标）；brief 宁可多写 3 行判据；同一任务最多重试 **1 次**；验收只看产物。
- 团队并行时写范围（子代理 brief 里的文件白名单）必须互不重叠；`send_message` 改需求，`interrupt_agent` 止血。

### C. 交接文档四层事实源

1. `docs/handover-1.5.0-main.md` —— 滚动正本（每轮更新 §4/§5）。
2. `docs/handover-new-session-context.md` —— 新会话寒暄提示词。
3. `docs/agent/AGENTS-full.md` 末尾「待办交接各批」—— **只追加**，不改旧条。
4. `docs/handover-<版本>-<主题>.md` —— 单批专题（本批：`docs/handover-2026-10-31-native-linux-migration.md`）。
- ⛔ `docs/AGENT-HANDOFF.md（已删除 2026-11-05）` **不是现状**，只作历史参考。
- **接手顺序**：`git status --short -uall` + `git log --oneline -1` → 各线 `gradle.properties` 的 `mod_version` → handover §0/§5 → 按 §7 取路径。
- **交班 6 义务**（其中一项必做）：`python3 -B docs/agent/verify-split.py` 四项全 `[OK]`。
- 事实源快照会落后：2026-11-10 实读**五线** `mod_version` 均 `26.4-SNAPSHOT.10+<后缀>`（`neoforge_1.21.1` / `forge_1.20.1` / `neoforge_26.1.2` / `fabric_1.20.1` / `fabric_1.21.1`），与之配套的 starengine 引脚 `.2/.2/.2/.11/.9`；同日随后升版为 `26.4-SNAPSHOT.11`（引脚不变，产物仍是 `.10`）—— 这类数字**一律实读覆盖**，不要引用本文。

## 📌 本文件的定位与自检

- 本文件 = **DSH on Linux 的规则正本**；`docs/agent/AGENTS-dsh.md` = **Windows 原生**历史正本（不删、不改红线区，`verify-split.py` 项 1b 依赖它）。
- 规则全文按需查：`docs/agent/AGENTS-full.md`（2026-11-10 实读 4,162 行）+ 标题行号总表 `docs/agent/AGENTS-TOC.md`（288 行）。**TOC 当前与正文漂移**：`python3 tools/gen_agents_toc.py --check` ⇒ `TOC: DRIFT`、`exit=1`（表内 L105 `@13169` vs 实际 `@13352`）⇒ 查区间前先用 `grep -n '^#' AGENTS-full.md` 校准，别盲信总表。定位：`grep -n '^#' docs/agent/AGENTS-full.md`，按总表查区间后 `sed -n '<a>,<b>p'`。
- 派活 brief 里写区间 `L<a>–L<b>`（查 `AGENTS-TOC.md`），读规则由**子代理**执行，不要把大段原文贴进主会话。
- 自检：`python3 -B docs/agent/verify-split.py`（四项：快照保序包含 + `AGENTS-dsh.md` 红线区与快照 L2–L130 逐字一致 / 根 `AGENTS.md` 结构完整 / docs 引用存在 / 体量 < 32K 且 using-superpowers 段未删）。`--strict` 只把「docs 引用」提为致命。
- 改动本文件后同样跑一遍。`[[PENDING:…]]` 应清零；若某项事实源长期不可得（如某线尚未实跑），**保留标记并列为已知缺口**，不得删标记假装已验证。

## 📍 红线区 —— Linux 替换口径（原文见 `docs/agent/AGENTS-dsh.md`〈📍 生效红线区〉）

> 本节为 **Linux 替换口径**，**不参与** `verify-split.py` 的逐字校验；红线文字正本仍是 `AGENTS-full.md` L2–L130，Linux 只替换其中的路径 / 工具通道。

红线**文字**不重写，只替换其中的 Windows 口径；下列条目按原文顺序对应：

| 红线区原文中的 Windows 口径 | Linux 宿主替换为 |
|---|---|
| 工作树 `F:\MCProject\astral_dice_multiloader` | `/mnt/f/MCProject/astral_dice_multiloader`（**同一块 NTFS 盘的原生挂载**，不是副本、不是 hgfs） |
| 前置库 `F:\MCProject\starengine` | `/mnt/f/MCProject/starengine` |
| 「相对路径陷阱」 | **不变且更严**：双系统同盘 + 多工作树共存，任何跨仓命令都必须绝对路径 |
| 「分支归属纪律（禁止镜像/回填）」 | **不变**（宿主无关）；`~/Project` 副本层已不存在，不要按旧文档重建 |
| 发布投放（整合包 `versions/<实例>/mods`） | Linux 宿主**可**投放：根 = `/mnt/d/.minecraft`（`MC_HOME` 可覆盖，根 `build.gradle` 的 `mcHomePath` 统一解析：`MC_HOME` > `/mnt/d/.minecraft` > `D:/.minecraft`）；Windows 宿主同一批文件在 `D:\.minecraft`。**「默认不部署」红线不变**（仅用户明确下达时跑 `pushToGame`/`build`） |
| 出网 / git 代理 | **2026-10-31 用户裁决改写**：clash-verge-rev **TUN 模式**全局接管（`tun.enable=true` / `198.18.0.1/30`）⇒ **直连，不再带 `-c http.proxy=`**；仓库 `.git/config` 的 `http(s).proxy=127.0.0.1:7897` 与 Windows 共用、**不改**，严格直连用命令级 `-c http.proxy= -c https.proxy=`；状态查 `127.0.0.1:9097`（Bearer secret） |
| 默认不 commit / push / 打 tag | **不变**（最严格红线） |
| `models/` `images/` `sound/` 与 `run/` 内他人产物 | **不变**：出现不属于自己的改动 ⇒ 停手报告，不 add/commit/stash/checkout/删除 |
| 「Windows 开发配置不可动」 | **新增（2026-10-31 用户硬约束）**：本机与 Windows 共用同一块盘；只改 Linux 侧需要的文件，不删 / 不改 Windows 专属配置（`mt.conf` 的 Windows 路径、旧脚本（已删除）、`gradlew.bat`、`run/start-*.bat`、`.vscode` 的 `F:\` 配置一律保留原样） |

- 优先级与基线（P0 = `neoforge-1.21.1` + `forge-1.20.1`；P1 = `neoforge-26.1.2` + `fabric-1.20.1`，`1.5.0` 正式版前不验证、不移植）、包根 `com.merlinkitsune.astral_dice`、fabric 内嵌库前置 `python3 tools/loom_embedded_jars.py --apply`（KI-F23）—— 全部**与宿主无关，原文有效**。

## 二次验证规范（与宿主无关，命令 Linux 化）

- 触发面（5 类）：改产品源码、改规则/守门脚本、改构建配置、改数据/配方、任何「已修好/已通过」的结论。
- **复核 = 一次性独立 `subagent` + `review-agent` 技能**（2026-11-07 用户裁决；`dsh_code_review` / `code_review_preview` / `code_review_execute` **已弃用**，不要再按旧文档走）。要点：全新 subagent（不吃本会话上下文）→ 加载 `review-agent` 技能 → 只给范围不给结论；**禁喂**主会话结论/倾向，让它自己 `git diff` 取证。
- 报告验收 5 条：① 结论有产物/行号级证据；② 每条判据可复现（给命令）；③ 区分 FAIL / ERROR / TIMEOUT / BLOCKED；④ 未做项与风险如实列出；⑤ 不给「大概/应该」。
- 两层守门不得互替：`verify-split.py` 管规则文件结构，独立复核子代理管代码/文档实质；一层通过不能替代另一层。

## 子代理与测试环境硬规则（2026-10-08 裁决，9 条 Linux 化）

1. **一 subagent 一任务，用完即弃**；不串任务、不复用上下文。
2. **测试/演示一律用非亡灵生物**（`minecraft:pillager` / `minecraft:cow`），不要用亡灵类。
3. **流程结束必关游戏进程**（见〈测试收尾〉），并核对无残留 java；收尾报告必须贴实际命令与输出原文。
4. **复核走一次性独立 `subagent` + `review-agent` 技能**（2026-11-07 起；旧口径「复核一律 `code_review_*`、不派 subagent」已作废）。
5. **实机必须由用户提示词明确下达**；未下达时禁止自行开客户端 / 连拍 / 探针。
6. **改动后旧实机证据作废**：先停旧实例，再谈新证据。
7. **开发流程**：改 → 编译 → 静态守门 → 实机（获授权后）→ 复核（分级 a–e）→ 收尾关进程。
8. **派子代理一律屏蔽人格注入**（免人设句原文带，见〈代理协作契约 B〉）。

## 命令执行规范（bash；Windows 宿主同一套 bash 口径，见 `docs/agent/AGENTS-dsh.md`〈⚙️ 环境与工具链口径〉）

1. **通道 = `run_code` 里的 `tools.bash`**（DSH PTC；一次调用一个新 shell，**不继承上一条的 `cd`/`export`**）⇒ 每条命令自带 `workdir` 或显式 `cd`，`JAVA_HOME` 逐条给。解释器 `SHELL=/bin/bash`（实读）；脚本首行 `#!/usr/bin/env bash` + `set -euo pipefail`。**禁止**再写 Windows 旧 shell 结论性命令（系统 PATH 无该解释器；旧件是 Windows 历史工具，Linux 侧不调用）。**不再新增**旧脚本 / 旧脚本模块（旧测试资产已按 `docs/features/` 下的测试资产退役登记文档退役；Windows 侧同一口径，入口 `bash scripts/test/win/mt.sh`）。
2. **禁止 `bash -c` 塞多行/嵌套引号**：多行逻辑落 `temp/tNN/<name>.sh`，显式 `bash /绝对路径/...sh` 执行，`tNN` = 本轮批次号（不入库）。
3. **长命令给超时**：`timeout --signal=TERM 300s <cmd>`；超时是独立结论（TIMEOUT ≠ FAIL ≠ ERROR）。长流程另落日志。
4. **后台任务**：`setsid bash /绝对路径/x.sh > temp/tNN/x.log 2>&1 &`，PID 记 `temp/tNN/x.pid`；禁止无日志裸跑；收尾按进程组杀 `kill -TERM -- -<PGID>`。
5. **日志纪律**：新日志落 `temp/tNN/` 或 `run/<线>/logs/` 的新文件；**禁止**覆盖 `run/<线>/logs/latest.log`（游戏在写）与既有 `run/datagen*.out.log`。
6. **不做无界全仓扫描**：搜索用 grep 工具（ripgrep），读文件用 read/`sed -n`；全仓 `find` / `grep -r` 会把 `build/`、`run/`、`.git` 拖进来（NTFS 上尤其慢）。
7. **权限与编码自检**：新建/改写文本后跑 `file <path>` 与 `wc -l`；`.sh` / `.py` / `.conf` 必须 LF（`.gitattributes` 已强制），`.md` 保持仓库既有形态（既有 CRLF 的别整份重写，新建建议 LF 并报告）。
8. **不提权**：不自行 `sudo`；需要 root 权限时停下报用户（凭据由用户提供，**密码严禁**写入任何文档/日志/提交）。目录权限不足（如 `docs/agent/`）时按 §3 的办法处理，不 chown。
9. **不读大文件全文**：`AGENTS-full.md` 等用区间 `sed -n`；> 200 行的目标文件先看标题/结构。

## Git 代理与操作规范

- **出网走 TUN 直连（2026-10-31 用户裁决）**：clash-verge-rev 以 TUN 接管全部路由（实读 `/configs`：`tun.enable=true`、`device=Meta`、`auto-route=true`、`198.18.0.1/30`，内核 mihomo `v1.19.32`）⇒ **远程 git 直接跑，不加 `-c http.proxy=…`**，也不要 export `*_proxy`。
  - 仓库 `.git/config` 仍有 `http.proxy`/`https.proxy` = `http://127.0.0.1:7897`（**Windows 共用配置，保留不动**）；`7897` 在听 ⇒ 走它也能通，严格直连用 `git -c http.proxy= -c https.proxy= <子命令>` 覆盖。
  - 排障：`curl -s -H 'Authorization: Bearer <secret>' http://127.0.0.1:9097/configs` 确认 `tun.enable=true`（secret 见 `~/.local/share/io.github.clash-verge-rev.clash-verge-rev/config.yaml`）；`/version` 看内核；`401` 是 secret 问题不是断网。Maven 侧 Central / aliyun / neoforged 直连 200，不需要镜像也不需要代理。
- 一律 `git -C /mnt/f/MCProject/astral_dice_multiloader <子命令>`；**默认不 commit / push / tag**，远程操作须用户明确要求。
- 提交前 `git status --short -uall` 必须只剩用户未跟踪素材（4 个文件，见〈git〉）；有他人改动 ⇒ 停手报告。
- `git add` 只加本轮明确产物；**禁止** `git add -A`、`git stash`、`git checkout --` 清别人的改动。

## 桌面控制与实机通道（Wayland）

- 桌面驱动 = DSH MCP **`computer-use-linux`**（bin `/home/kitsune/.local/bin/computer-use-linux-mcp`，stdio；KDE Wayland 下 AT-SPI + KWin 脚本后端 + `xdg-desktop-portal` RemoteDesktop/InputCapture）。**旧的 `computer-use`（`@mirsella/computer-use-mcp`）在本机不存在，不要按它写命令。**
- 2026-10-31 `doctor` 实读：AT-SPI `IsEnabled=true`、`can_build_accessibility_tree=true`、window backend `kwin` 可用（`list_windows`/`focused_window`/`activate_window` 可列可聚焦）、RemoteDesktop portal 可用（`AvailableDeviceTypes=7`，指针+键盘）、`can_send_development_input=true`；**screenshot 走 portal，capture 状态 `unverified`**（首次截图可能弹 portal 授权，需要人在桌面确认一次）。
- 工具面：`get_app_state` / `screenshot` / `list_windows` / `focused_window` / `activate_window` / `click` / `scroll` / `drag` / `type_text` / `press_key` / `set_value` / `perform_action` / `move_window` / `resize_window`；配 `mcp__screen-ocr__ocr_screen|ocr_find|ocr_image` 做屏幕文字定位（比纯坐标稳）。
- **本机缺的输入工具**（实读，不要假设存在）：`xdotool`、`wtype`、`wmctrl`、`grim`、`kdotool` **未安装**；`ydotool` 已装且 **socket 现已存在**（`/run/user/1000/.ydotool_socket`，2026-11-10 实读）——但按键语义未实测（布局/修饰键映射未知），**仍不要**拿它当默认通道。`spectacle` 已装（`/usr/bin/spectacle`），可做 KDE 原生截图兜底：`spectacle -b -n -a -o /tmp/mc-world.png`。
- **GUI 级驱动只在需要时用**：文字/坐标注入优先 `computer-use-linux` 的语义选择器（role/name），退而用坐标；坐标以**返回截图的 `coordinate_width/height`** 为准（可能被降采样），不要拿物理分辨率换算。
- **驱动优先级（2026-10-31 沿用）**：游戏内输入/状态读写**首选 AutoJS `eval_js`**（脚本上下文直接操作，绕过窗口焦点问题）；GUI 级（建世界、开关菜单）用 `computer-use-linux` / `spectacle` + OCR；Windows 侧的 `scripts/test/mt_inject.py`（ctypes user32 注入；原 `windows MCP 的 Click/Move/Type 输入` 的替代物，旧件仅作溯源）是 **Windows-only**，Linux 无等价实现。
- 游戏窗口是 **X11（XWayland）窗口**，`xwininfo -root -tree | grep -i minecraft` 可核对标题/几何；**GLFW 不暴露 AT-SPI**，`list_windows` 里可能看不到 MC —— 看不到就走整屏截图 + 坐标，不要卡在这里。
- 截图证据一律落 `temp/tNN/`（不入库）；不得用固定外部目录。

## AutoJS 运行时通道（Linux 侧口径）

> 详细契约见 `docs/features/autojs-test-harness-spec.md（已删除 2026-11-05）`；操作口径见 `docs/features/autojs-testing-flow.md（已删除 2026-11-05）`。本节只定规则。

- 端点：Streamable HTTP `POST http://127.0.0.1:25585/mcp`，默认 bind `127.0.0.1`；探活 `GET /health` 期望 `200`：

  ```bash
  curl -fsS -m 3 http://127.0.0.1:25585/health
  ```

- 配置：`run/<线>/config/autojs.json`（1.21.1 / 1.20.1 实读存在）；端口覆盖序 **`-Dautojs.port=` > `AUTOJS_PORT` > 配置文件**（`0` = 系统分配）。
- **同一时刻只能一条线占用 25585**（否则 `BindException`）；切线前先停旧客户端。
- **MCP 注册（2026-11-10 实读）**：`~/.dsh/profiles/web/cordis.patch.yml` 现注册 **4 个** server：`blockbench`（L34）/ `computer-use-linux`（L50）/ `screen-ocr`（L66）/ **`autojs`（L82）**——`transport: streamable-http`、`url: http://127.0.0.1:25585/mcp`、`headers.Accept: application/json, text/event-stream`、`failOnStartupError: false`。⇒ `mcp__autojs__{status,eval_js,kubejs_log,reload_scripts}` 已可用，**不必**再走 `curl` 打 JSON-RPC（`curl` 仍作兜底）。
  - 前提是 **25585 在听**：游戏/专用服没起时 `list_mcp_resources(autojs)` 报 `server is disconnected`，属**环境问题不是缺陷**（红线 1 仍适用：不得为验通道而自行起实机）。`~/.dsh/skills/` 仍不存在。
- 工具四件（HTTP 协议不变）：`status` / `eval_js { code, side: auto|server|client, timeout_ms }` / `kubejs_log { side, kind, limit }` / `reload_scripts { side, timeout_ms }`；返回 `{ ok, side, elapsedMs, type, value, diagnostics }`，**必看 `diagnostics`**。
- 铁律：`side` 必须明确（读权威数据用 `server`，读 HUD/FPS/输入用 `client`，`auto` 仅在两侧等价时用）；运行时不能新建注册表条目；只求值最后一个表达式；Rhino 不是 JS 反射；返回值必须 `JSON.stringify`。
- 错误归一：`isError` / `ok=false` 记 **BLOCKED**（环境问题），**不得**判成功能缺陷；端点没起同此。
- 历史实测（VM 期，1.21.1，协议未变）：client 侧 **KubeJS 里没有裸 `Minecraft` 全局**，必须 `Java.loadClass('net.minecraft.client.Minecraft').getInstance()`；`status.endpoints` = `{"bindAddress":"127.0.0.1","port":25585,"mcpPath":"/mcp"}`。（`[[PENDING:autojs-native]]` **2026-11-10 已销账**：1.21.1 实跑中 `GET /health` = 200、`status` 两侧均 `registered:true`/`available:true`（server「1 player online」、client「world loaded」）。端点未起时仍是 `env.sh` 的 `autojs_health=WARN`，属环境问题。）
- **1.21.1 真机 server 侧 Rhino 实测（2026-11-10，本会话逐条踩过）**：
  - 仍然**没有全局 `server`** ⇒ `Java.loadClass("net.neoforged.neoforge.server.ServerLifecycleHooks").getCurrentServer()`；`MinecraftServer.getInstance()/getServer()` 都不存在。
  - **KubeJS `Item.of("astral_dice:<id>")` 对本模组物品静默返回空气**，`new ItemStack(item)` 同样（拿到 `block.minecraft.air` / count 0，不报错）⇒ 别据此判「物品没注册」。正确取法：
    `Java.loadClass("net.minecraft.core.registries.BuiltInRegistries").ITEM.get(Java.loadClass("net.minecraft.resources.ResourceLocation").parse("astral_dice:souri_sign")).getDefaultInstance()`，再 `player.getInventory().add(stack)`。
  - **tooltip 读取（1.21.1 是三参重载）**：`stack.getTooltipLines(Java.loadClass("net.minecraft.world.item.Item$TooltipContext").of(p.serverLevel()), p, Java.loadClass("net.minecraft.world.item.TooltipFlag").NORMAL)`。坑：`TooltipFlag.getDefault()` 不存在（只有 `NORMAL`/`ADVANCED` 常量）；`player.level()` 在 Rhino 里是**属性不是方法**，用 `p.serverLevel()`；`getTooltipLines(player, flag)` 两参签名不存在。
  - `obj.getClass()` 对 Rhino 包装的 Java 对象不可用（报 `Cannot find function getClass`），要看类型用 `String(obj)` 或 `Java.type` 反射。
  - curl 兜底调用格式（实测可用）：`POST http://127.0.0.1:25585/mcp`，头 `Content-Type: application/json` + `Accept: application/json, text/event-stream`，体 `{"jsonrpc":"2.0","id":N,"method":"tools/call","params":{"name":"eval_js","arguments":{"side":"server","code":"…"}}}`，结果在 `result.content[0].text`（JSON 字符串，含 `diagnostics`）。
- 真机 server 侧 Rhino 实测坑（2026-10-31，26.1.2 专用服）：没有全局 `server`，须 `Java.loadClass("net.neoforged.neoforge.server.ServerLifecycleHooks").getCurrentServer()`；**没有 `Java.from`**；`registry.keySet()` 元素**直接是 `Identifier`**（不是 `ResourceKey`），用 `String(x)` 取值；`MappedRegistry#get/containsKey` 因 `Identifier` 与 `ResourceKey` 重载在 Rhino 里报 ambiguous ⇒ 改用 `keySet().toArray()` 遍历；`performPrefixedCommand(cmd)` 有副作用但返回值读不到，命令回显去 `logs/latest.log` 里 grep。
- **判「方块模型是否解析」的实机方法（2026-11-10 在 1.21.1 与 1.20.1 两线实测）**：先在 server 侧 `level.setBlockAndUpdate(pos, block.defaultBlockState())` 把方块放下，再在 client 侧
  `mc.getBlockRenderer().getBlockModel(state).getQuads(state, d, level.random).size()` 对 `d = null, DOWN, UP, NORTH, SOUTH, WEST, EAST` 七个桶求和：**合计 0 = 不可见模型（`elements: []`）**、`>0` 且出现紫黑棋盘 = missing model（资产缺失）。资产是否进包用 `mc.getResourceManager().getResource(RL.parse("astral_dice:blockstates/<id>.json")).isPresent()` 复查。1.20.1 的 `ResourceLocation` 也要走 `Java.loadClass`，但 `parse` 静态方法可直接调。
- 安全边界：`unrestricted=true` 时**只绑 loopback**，禁 `0.0.0.0`。

## 🧪 测试台现状（Linux）

- Windows 测试台（**迁移前实读快照 = 历史记录；现行执行口径见上**）：原 `scripts/test/win/mt.sh` 现 354 行（旧文档 851 行已过期），入口 `scripts/test/win/mt.sh --phase <preflight|build|env|launch|cases|report|stop>`；全部 旧 shell 合计约 19,384 行。**整台不可移植**，且按红线**不改它**（该 旧 shell 台已按 `docs/features/devnext-test-asset-retirement-register.md` 退役，仅作溯源）。Windows 侧现行入口 = `bash scripts/test/win/mt.sh --phase <preflight|env|build|launch|cases|report|stop>`（bash 由 MSYS2 / git bash 提供）；Linux 侧只用 bash 台。
- **Linux 测试入口 = `scripts/test/linux/{env,launch,stop}.sh`**（bash，零 旧 shell 依赖；`scripts/test/linux/README.md` 为完整操作说明）：
  - `env.sh [--version <线>] [--quiet]`：**只读**环境检查（仓库/挂载、各线 JDK 与 `run/`、`mods/` 与 autojs·rhino jar、AutoJS `/health`、客户端残留、锁、Gradle 守护）；机器行 `CHECK: <key>=OK|WARN|FAIL` + 收尾 `ENV_RESULT: OK|WARN|FAIL`；退出码 `0` 无 FAIL / `1` 有 FAIL / `2` 用法错误。
    - 2026-11-10 复跑：`ENV_RESULT: WARN — 0 项阻塞，2 项提示`（`autojs_health=WARN`（`/health`=000）+ `locks_stale=WARN`（锁 PID 已退出）），24 条 `CHECK`，退出码 `0`。2026-10-31 首跑为「WARN — 0 项阻塞，3 项提示」。
    - 键名变化：`hgfs_writable` → **`repo_writable`**（`ntfs3/ext4` 走 OK 分支并注明链接实测可用）；新增 `locks_stale`（陈旧锁检测，只报不删）；`mt.conf` 的 Windows 路径提示从 WARN 降为 INFO（Windows 台配置，Linux 不动）。
  - `launch.sh --version <线> [--timeout 600] [--quickplay <世界名>] [--kill-on-timeout] [--log-dir <dir>] [--dry-run]`：**优先复用 `run/start-<线>.sh`**（存在且可执行），否则回退 `bash ./gradlew :<线>:runClient --console=plain`；`setsid` 独立进程组启动，日志落 `scripts/test/linux/logs/<线>-<ts>.log`，PID 落 `<RUN_DIR>/linux-run.pid`。 `--wait-world <秒>` 是本日实跑必加项（等世界真正加载完再返回，本会话用 240）。
  - **五线全支持（2026-11-10 补）**：`env.sh` / `launch.sh` / `stop.sh` 的版本表都已是 `1.21.1 1.20.1 26.1.2 fabric-1.20.1 fabric-1.21.1`（`fabric-1.21.1` 的 toolchain = 21）。**fabric 线的 `RUN_DIR` 不是 `run/<ver>`**，而是 Loom 的 `fabric-<ver>/run/client`（日志/pid/健康检查/残留扫描都按这个口径；之前按 `run/<ver>` 会直接判 BLOCKED）。
  - **fabric 线的 AutoJS 必须显式注入**：`launch.sh` 对 `fabric-*` 自动追加 `-PautojsTestMod=1`（`build.gradle` 里该 property 才加 `modRuntimeOnly AutoJS`；`fabric-1.21.1` 另带 rhino 满足其 HARD_DEP），并把 AutoJS 计数置 1 以保持「AutoJS 就绪」为主判据（fabric 的 run 目录里本来没有 jar）。
  - **另一个前置**：`~/.m2` 里必须有 `cc.sighs.autojs:AutoJS-fabric-<线>:1.0.0`，否则 fabric 线在建配置阶段就 `Could not resolve`。2026-11-10 用仓内 `scripts/test/**/publish_autojs_maven.py --root <临时 m2 根>` 装入（仓库零改动）后才跑通；换机/清 `~/.m2` 后要重做。
  - **2026-11-10 实跑基线（1.21.1，本会话）**：`bash scripts/test/linux/launch.sh --version 1.21.1 --quickplay testworld --timeout 600 --wait-world 240` ⇒ 约 90s 输出 `LAUNCH: READY` + `WORLD_READY`，日志 `scripts/test/linux/logs/1.21.1-20261110-111530.log`，`wrapper_pid=65471` / `game_pid=66504`；收停 `stop.sh --version 1.21.1` ⇒ `LINUX_STOP: OK`（3 行 + `pidfile-removed`），随后 25585/25565/25575 全无监听、零残留 java。该轮 `logs/latest.log` 只有 **3 条 ERROR**，全是 Curios 槽位噪声（`scroll/feet/spellbook is not a registered slot type!`），无新 crash-report。
  - **2026-11-10 实跑基线（1.20.1，本会话）**：`launch.sh --version 1.20.1 --quickplay testworld --timeout 900 --wait-world 300` ⇒ `LAUNCH: READY` + `WORLD_READY`，日志 `scripts/test/linux/logs/1.20.1-20261110-112805.log`（3,756 行），`wrapper_pid=83909` / `game_pid=85638`；`stop.sh --version 1.20.1` ⇒ `LINUX_STOP: OK`，三端口全空、无残留 java。服务端读数：`BR.ITEM` 共 **1593** 项，`souri` 相关 id 只有 `astral_dice:souri_sign`；`SouriSignManager.getGoldScales(p)=2`、`SouriSignItem.isEquipped(p)=true`、`hasDragon(p)=false`、`GOLD_SCALE_MAX=4`；`BR.MOB_EFFECT` 有 `souri_gold_scale` / `souri_dragon`，`BR.ENTITY_TYPE` 有 `souri_drink`（物品只能走 `BR.ITEM.get(new RL("astral_dice:souri_sign")).getDefaultInstance()` 取，`Item.of("id")` 会静默返回 air）。
  - **1.20.1 的 Rhino 够不到枚举静态量**：`TooltipFlag$Default.NORMAL` 与 `valueOf(...)` 都报 `InternalError: Java class "net.minecraft.world.item.TooltipFlag$Default" has no public instance field or method named "NORMAL"`，且 1.20.1 KubeJS **没有** `Java.type`（`TypeError: Cannot find function type in object dev.latvian.mods.kubejs.bindings.JavaWrapper`）。⇒ tooltip 全文只在 **1.21.1** 侧取证（`stack.getTooltipLines(Java.loadClass("net.minecraft.world.item.Item$TooltipContext").of(p.serverLevel()), p, TooltipFlag.NORMAL)`，注意 `p.level()` 是属性不是方法）；1.20.1 侧改用 lang key 实读 + 注册表/状态读数，不要在 1.20.1 上死磕 tooltip。
  - 就绪判据（**以 AutoJS 为准**）：`GET http://127.0.0.1:25585/health` = 200 且 `status` 里 client `registered:true` / `available:true`。日志 marker（`Total time to load game and open world was`、`logged in with entity id`）**只能**作为「已开世界」的附加证据——dev 客户端默认停在标题画面，错用会让每次启动都误判 TIMEOUT。
  - launch.sh 退出码：`0` 就绪 / `1` 启动失败或进程提前退出 / `2` AutoJS 端点未就绪（ERROR，逃生阀 `MT_ALLOW_NO_AUTOJS=1`）/ `11` BLOCKED / `12` TIMEOUT / `130` 用户中断。
  - `stop.sh --version <线> | --all [--force] [--purge-saves] [--stop-daemon] [--dry-run]`：标准收停。先按 `linux-run.pid` 收进程组（`TERM` → 15s → `KILL`），再 `/proc` 扫描补漏，复核无残留才输出 `LINUX_STOP: OK`；有残留输出 `LINUX_STOP: RESIDUAL` 并 `exit 1`。
  - **未复刻的 Windows 能力**：进程防撞锁、测试前世界清场、OP / 禁 AI 闸门。这些前置在 Linux 侧仍需人工确认，不得当作「已验证」。
  - **2026-11-10 实跑基线（P1 三线，串行，报告 `temp/live-p1/report.md`）**：fabric-1.20.1 开世界 **16.794s**、fabric-1.21.1 **21.449s**、neoforge-26.1.2 **14.125s**，三线 `LAUNCH: READY` + `WORLD_READY`，AutoJS 两侧 `registered/available=true`，`stop.sh` 全 `LINUX_STOP: OK`，收尾三端口全空、无 java 残留、crash-report 计数等于基线。souri 注册读数：物品恰 1 个 `astral_dice:souri_sign` + `souri_drink` 实体 + `souri_gold_scale`/`souri_dragon` 双效果；三条新线 `goldScales=0`、`hasDragon=false`（fabric-1.20.1 无 `isEquipped` API，属移植差异不是缺陷）。
  - **26.1.2 的 Rhino 口径与 1.20.1/1.21.1 不同**（2026-11-10 实测）：`ResourceLocation` → `Identifier.parse`；`BuiltInRegistries.ITEM.get()` 返回 `Optional<Holder<Item>>`；`Inventory.selected` → `setSelectedSlot/getSelectedSlot`；服务端实例只能 `Java.loadClass("net.neoforged.neoforge.server.ServerLifecycleHooks").getCurrentServer()`（`Utils.getServer()` 在服务端为 null）。**fabric-1.21.1 的 AutoJS 是另一套 Rhino 内核**（无 KubeJS/`BR`/`Utils`）：`eval_js side=server` 里用 `Java.loadClass("net.minecraft.client.Minecraft").getInstance().getSingleplayerServer()` 取服务端。
  - **26.1.2 的 GeckoLib 5（StarEngine 包装）会 eager bake 动画文件**：空 `{"animations":{}}` 会报 `No animation definitions found in animation file!`（`StarEngine-GeckoLib … GeckoLibGsonLoader`，非致命，仅资源加载 ERROR）。⇒ 新增动画文件时不要留空壳；本批已给 `geckolib/animations/souridrink.animation.json` 补最小 idle 动画，复跑确认该 ERROR 消失。1.21.x 线同为空文件但不 eager bake，故不报错。
- 启动脚本 `run/start-{1.20.1,1.21.1,26.1.2}.sh`（2026-11-10 实读 8,658 / 8,673 / 9,644 B，均 `rwx`；**两条 fabric 线都没有 start 脚本**，只能走 `launch.sh` 的 `./gradlew :<线>:runClient` 回退）：
  - 用法 `./run/start-1.21.1.sh [<额外 gradlew 参数…>]`；脚本自行 `cd` 到仓库根，`set -euo pipefail`；前台 `bash run/start-1.21.1.sh`，后台 `setsid nohup bash run/start-1.21.1.sh > /tmp/launch-1211.out 2>&1 &`。Wayland 会话下环境须就位：`DISPLAY=:0 WAYLAND_DISPLAY=wayland-0 XDG_RUNTIME_DIR=/run/user/1000`。
  - **`run/` 整体在 `.gitignore:61` 里** ⇒ `run/start-*.sh` 是**未跟踪/被忽略**文件；确实要入库才 `git add -f`，默认**不 add**。
  - **JDK：`DEFAULT_JDK` 三脚本统一 `/usr/lib/jvm/java-21-openjdk-amd64`**；`ASTRA_JAVA_HOME` **只覆盖 daemon JVM**，不改变 toolchain。
  - **`DISPLAY` 为空直接 `exit 1`**：Wayland 会话必须靠 XWayland（本机 `DISPLAY=:0`）。
  - 收尾只收本实例（按 `/proc/<pid>/cwd` 与 `--gameDir` 精确匹配），先 `SIGTERM` → 20s → `SIGKILL`；**绝不**全机 `pkill -f java`。
  - 1.20.1（legacyforge dev run）加载的是 `run/1.20.1/mods/astral_dice-<ver>+forge_1.20.1.jar`，不是 `build/resources/main`：改了 `src/main/resources` 后**必须** `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :forge-1.20.1:pushToDevRun`（约 60s）再启动。1.21.1（MDG NeoForge）无此问题。
- **已知遗留（迁移前既有，不随换机消失）**：
  - ~~`fabric-1.20.1` 实机未通过~~ **已不成立（2026-11-10 实机通过，16.8s 开世界）**：旧失败是 Loom dev runtime 给 LWJGL `3.3.2-snapshot`、Sodium 0.5.x 只接受 `3.3.1`（`Sodium-EarlyDriverScanner … Installed version: 3.3.2-snapshot / Required version: 3.3.1`）。当前 `fabric-1.20.1/run/client/mods` 没有 Sodium；若日后显式加 Sodium，仍需在构建侧 pin LWJGL 或换 Sodium 版本。
  - 26.1.2 的 `KubeJSErrorScreen` 已**降级为非阻断**（2026-11-10 实机）：KubeJS 客户端脚本 `astral_wallet_vision.js#38` 仍报 `EvaluatorException: Failed to load Java class 'com.merlinkitsune.starenginelib.economy.StarCoinWalletState': Class could not be found!`（jar 内确有该类；Rhino 类加载器看不到 jarJar 嵌套类），但世界正常加载、游戏可玩，归为既有噪声；修不修属 P1 欠账。
  - 1.20.1 首启曾因 Jade 11.13.3 的 dev-only `Missing config translation` 断言崩在标题屏（已在三语 lang 补 `config.jade.plugin_astral_dice.{shop_cooldown,event_drawer_cost}` 修复）；1.21.1 是 Jade 15.10.6 无该断言。
  - `run/26.1.2/mods/` 残留旧 `astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`，建议清理（未做）。
- 规则层继续有效（与实现无关）：禁批量编排、单条手动、`FAIL` / `ERROR` / `TIMEOUT` / `BLOCKED` 语义不混同、测试顺序 1.21.1 先行、1.20.1 门控（未过记 `GATED`）、断言以服务端权威日志 `AP_<tag>_<KEY>` 为准、报告不得放宽口径。
- 静态守门：纯 python 守门**可跑**——`python3 -B docs/agent/verify-split.py`（2026-11-10 实跑：全部通过、`exit=0`，1 个警告「未找到 `AGENTS.md.bak-a-plan-*` 备份，跳过快照比对」——**该项实际被跳过**）、`python3 tools/verify_sign_display_names.py`（`GATE: PASS`，检查项 108,811，5 线含 `fabric-1.21.1`）、`python3 tools/audit_patchouli_keys.py`、`python3 -B tools/loom_embedded_jars.py`、`python3 scripts/test/lib/mt_case.py validate`。`scripts/` 与 `tools/` 下已无旧件（Windows 历史工具已删除），Linux 侧**不调用**；其余门禁在 Linux 下**手段缺失**，不得假装通过，也不得把「没跑」写成「已通过」。
- 实机一律等用户明确下达；未授权时只做编译 + 静态守门。

## 互斥锁与并发

- 锁目录 `/mnt/f/MCProject/.agent-locks/`；DSH 动工作树前建 `dsh.lock`，收尾必删。**锁文件为三行口径**：`PID=` / `TIME=` / `TASK=`（正本 `docs/agent/AGENT-SPLIT.md` L25），可附加 `NOTE=` 说明行。
- 2026-11-10 实读：目录里只有 `dsh.lock`（`PID` 写的是当次 `bash -c` 的 shell，命令结束即变陈旧），`kill -0 <PID>` ⇒ 已退出，`env.sh` 报 `locks_stale=WARN`。⇒ **锁的 PID 字段不要写一次性 shell 的 PID**（写长驻会话/agent 主进程），否则每次刚建锁就被判陈旧、误导后续会话。
  - **教训（本会话踩到）**：锁里的 `PID=` 若写的是「执行 `bash -c` 那条命令的 shell PID」，命令一结束就变成陈旧锁；应写**长驻进程**（会话/agent 主进程）。陈旧锁按规则**不自行删除**，报用户后接管并改写，收尾再删。
- 动工作树或起 gradle 前：① 看锁是否存在、`kill -0 <pid>` 是否存活（`env.sh` 的 `locks_stale` 会自动报）；② 看 `build/` 最近 30 分钟有无修改；③ 存在他宿主的活跃锁（`kimi.lock` 等）⇒ 停手等待，**陈旧锁不自行删除**，报用户。
- 同一工作树同一时间**只允许一个 gradle**；团队并行时写范围互不重叠。

## 交接与事实源（Linux 侧）

- 四层事实源见〈代理协作契约 C〉；每轮收尾更新 `docs/handover-1.5.0-main.md` §4/§5 + `docs/handover-new-session-context.md`。
- 接手先实读：`git -C <工作树> status --short -uall`、`git log --oneline -1`、各线 `mod_version`、`~/.m2` 是否有 `com.merlinkitsune`（决定能否直接构建）、`.agent-locks/` 状态、`findmnt -T` 的文件系统类型。
- 报告「已完成」前必须有一次实跑证据（命令 + 原文输出片段），不接受「应该可以」。
- **本批专题**：`docs/handover-2026-10-31-native-linux-migration.md` —— 原生 Linux 真机实测（挂载/工具链/出网/链接语义）、VM→真机路径重映射、游戏测试资产接管、以及剩余 checklist。
- **相对路径纪律**：交接文档、仓库内脚本、规则文本一律相对仓库根书写（脚本用 `ROOT="$(cd "$(dirname "$0")/.." && pwd)"` 自解析）；宿主绝对路径只允许出现在专题交接文档的附录快照里。

## 📇 规则索引

- 区间表来自 `docs/agent/AGENTS-dsh.md`〈📇 规则索引〉（对应 `AGENTS-full.md` 2026-11-10 实读 4,162 行、`AGENTS-TOC.md` 288 行标题表；注意 `AGENTS-TOC.md` 自称 source 行数是**旧快照**，且当前 `--check` 报 `DRIFT`，行数以 `wc -l` 实读为准）；**Linux 宿主同样适用**，命中条目的 Windows 命令按〈Gradle〉/〈命令执行规范〉改写：

| 主题 | `AGENTS-full.md` 区间 |
|---|---|
| 第三方取源 | L121–L206 |
| Gradle 构建守护 | L207–L236 |
| 多版本矩阵 | L237–L531 |
| 版本优先级 | L532–L582 |
| 平衡性红线 | L583–L613 |
| 模组依赖 | L614–L657 |
| 版本互通 | L658–L700 |
| 更新日志 / 迁移 | L701–L809 |
| 包结构 | L810–L942 |
| 立牌 / 物品命名 | L943–L1136 |
| 筹码 | L1137–L1310 |
| 流派 / 目标 / tooltip | L1311–L1569 |
| tooltip / 语言 / 骰槽 | L1570–L1842 |
| 星币 / Bountiful / 战利品 | L1843–L2067 |
| 闪避 / 图标 / 手册 | L2068–L2197 |
| Mixin / 产物上传 | L2198–L2308 |
| 自动化测试必读 | L2309–L2909 |
| 上游补丁 / 敌对 / 调试命令 | L2910–L3046 |
| 版本历史 + 待办交接各批 | L3047–L4243 |

- **Linux 专属条目**：

| 主题 | 去哪 |
|---|---|
| Windows 原生规则（历史正本） | `docs/agent/AGENTS-dsh.md` |
| 原生 Linux 迁移实测与 checklist | `docs/handover-2026-10-31-native-linux-migration.md` |
| VM 时代历史（hgfs 代价 / 本机盘副本） | `docs/handover-2026-10-31-linux-vm-adoption.md（已删除 2026-11-05）` · `docs/handover-2026-10-31-local-disk-workspace.md` |
| 工程拓扑 / 各线基线 / 构建耗时台账 | `docs/agent/PROJECT-REFERENCE.md` |
| 委派操作手册（模板/失败模式） | `docs/agent/delegation-runbook.md` |
| 测试流程与规则总览（Windows-only 实现） | `scripts/test/TESTING-RULES-OVERVIEW.md（已删除 2026-11-05）` |
| Linux 测试台操作说明 | `scripts/test/linux/README.md` |
| AutoJS 通道契约 | `docs/features/autojs-test-harness-spec.md（已删除 2026-11-05）` · `docs/features/autojs-testing-flow.md（已删除 2026-11-05）` |
| 规则库自身说明 | `docs/agent/README.md` |

- 维护约定：① 每次必读的红线 → 写根 `AGENTS.md` 红线区并同步 `AGENTS-full.md`；② 情境规则 → 追加 `AGENTS-full.md` 并更新 `AGENTS-TOC.md` 与本索引；③ 本文件体量上限 **~30K 字符**（超了拆索引 + 子代理读取），改完跑 `python3 -B docs/agent/verify-split.py`；④ 长驻外部内容走索引 + 子代理读取，不塞进主会话。
