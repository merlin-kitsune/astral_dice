# DSH 在 Linux 原生宿主的规则正本（AGENTS-dsh-linux）

> **定位**：本文件是 **DSH 宿主在 Linux 原生环境**下的主规则。DSH 在 Linux 上先读本文件，再按根 `AGENTS.md` 的共同红线与按需索引执行；**Windows 规则**见 `docs/agent/AGENTS-dsh.md`（历史正本，仅对 Windows 宿主有效）。
> **冲突裁决**：同一宿主上两份文件冲突 ⇒ **Linux 宿主以本文件为准**，Windows 宿主以 `docs/agent/AGENTS-dsh.md` 为准；共同红线以根 `AGENTS.md` 为准。
> **红线原文不复制**：红线区逐字原文的唯一副本在 `docs/agent/AGENTS-dsh.md`〈📍 生效红线区〉（`docs/agent/verify-split.py` 项 1b 拿它和 `docs/agent/AGENTS-full.md` L2–L120 逐字比对）。本文件只给 **Linux 下的替换/补充口径**，复制第二份会让红线两处漂移。
> **事实源原则**：本文数字均为 **2026-10-31 实读快照**；宿主版本、路径、构建命令、日志路径一律以**实读命令**为准，文档只是当次快照。`[[PENDING:…]]` = 尚无实测依据，**不得**当已验证结论引用，也不得据此施工。
> **🚚 2026-10-31 迁移批（本文件本轮改写的原因）**：宿主已从 **VMware 虚拟机 + hgfs 共享盘** 迁到 **原生 Linux 真机（双系统，与 Windows 共用同一块 NTFS 盘）**。旧的 `/mnt/hgfs/*`、`~/Project` 四副本、`rsync-original.sh` 通道、`computer-use`(mirsella)/`autojs` MCP 口径**全部作废**，一律以本文件为准；VM 时代文档只作历史（`docs/handover-2026-10-31-linux-vm-adoption.md`、`docs/handover-2026-10-31-local-disk-workspace.md`）。迁移实测见 `docs/handover-2026-10-31-native-linux-migration.md`。

## 🚀 会话启动第一步：加载 using-superpowers（2026-10-24 用户裁决，每次会话必做）

- 回复任何内容、调用任何工具**之前**，先调用 `skill` 工具、`name="using-superpowers"`。
- 该技能规定「先查技能目录，1% 可能适用就须加载」，并负责其它流程类技能（brainstorming / writing-plans / systematic-debugging / verification-before-completion 等）的按任务加载。
- `skill` 工具不可用（未注册 / 加载失败）时，**必须**在回复里明写「本次会话未能加载 superpowers」，不得假装已加载。
- 派发子代理的 brief **必须**带一句「动手前先加载 using-superpowers（团队硬规则，任何动作之前）」。

## ⚙️ 环境与工具链口径（Linux 原生）

### 1. 主机与桌面（实读）

- **物理机**：`systemd-detect-virt` = `none`，DMI `MS-7E59` / `Micro-Star International Co., Ltd.`；**不是 VMware / WSL / 容器**。
- 发行版 `Ubuntu 26.04.1 LTS`（`VERSION_CODENAME=resolute`），内核 `7.0.0-42-generic`；用户 `kitsune`，登录 shell `/bin/bash`。
- 桌面 = **KDE Plasma**，会话类型 **Wayland**：`XDG_SESSION_TYPE=wayland`、`WAYLAND_DISPLAY=wayland-0`、`DISPLAY=:0`、`XDG_RUNTIME_DIR=/run/user/1000`。
- **XWayland 是 MC 的硬前提**：MC 1.20.1 / 1.21.1 / 26.1.2 的 GLFW 只有 X11 后端，Wayland 会话下 `DISPLAY=:0` 必须存在（`run/start-*.sh` 在其为空时直接 `exit 1`；三线窗口在 VM 期实测都是 X11 窗口，真机沿用同一结论）。
- **双系统互操作是本机的新前提**：本机 Linux 与 Windows 交替启动，共用同一块 NTFS 盘（Windows `F:` = `/mnt/f`，`D:` = `/mnt/d`）。用户硬约束（2026-10-31）：**Windows 下的开发配置与资产不可改**——本文件所有动作只动 Linux 侧、只动仓库内该动的文件。
- 无 SELinux / AppArmor 前提；**没有** hgfs、9p、WSL、`~/Project`。

### 2. 路径（绝对路径纪律）

| 用途 | Linux 路径（实读） | Windows 侧同一批文件 |
|---|---|---|
| **仓库根（唯一工作树）** | `/mnt/f/MCProject/astral_dice_multiloader` | `F:\MCProject\astral_dice_multiloader` |
| 前置库 starengine | `/mnt/f/MCProject/starengine` | `F:\MCProject\starengine` |
| 2.0 开发线（互不镜像） | `/mnt/f/MCProject/astral_dice_multiloader-next` | 同名 |
| 宿主互斥锁 | `/mnt/f/MCProject/.agent-locks/` | `F:\MCProject\.agent-locks\` |
| **整合包根（Minecraft 实例）** | `/mnt/d/.minecraft`（`MC_HOME` 可覆盖） | `D:\.minecraft` |
| 本机 mavenLocal | `/home/kitsune/.m2/repository/` | `%USERPROFILE%\.m2\repository`（**不是同一份**） |
| DSH 用户级配置 | `/home/kitsune/.dsh/` | `C:\Users\<user>\.dsh\`（**不是同一份**） |
| 盘挂载 | `/mnt/c`(ntfs3 **ro**) `/mnt/d` `/mnt/e` `/mnt/f`(ntfs3 rw) | `C:` `D:` `E:` `F:` |

- **仓库就是正本，不再有副本层**：`~/Project/*` 四副本（VM 期产物）**已不存在**；`scripts/dev/rsync-original.sh` 已停用（默认 `exit 3`，仅 `ALLOW_LEGACY_HGFS_RSYNC=1` 可越过）。
- 日常命令在仓库根内用相对路径（`./gradlew`、`bash scripts/test/linux/env.sh`），脚本自解析 `ROOT="$(cd "$(dirname "$0")/.." && pwd)"`；**跨仓库/跨盘**（starengine 兄弟仓、`-next` 树、`/mnt/d/.minecraft`）必须写绝对路径。
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
- **系统默认 `/usr/bin/java` = `openjdk 21.0.12.1 2026-08-18`**（2026-10-31 真机实读；**VM 期默认是 25，已变**——不要沿用旧文档的 25 口径）。
- **Gradle daemon JVM 必须 ≥ 21：本仓所有 `./gradlew …` 一律先 `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`**。
- 反例证据（VM 期实测，机制未变）：`JAVA_HOME=…java-17 ./gradlew :fabric-1.20.1:publishToMavenLocal` 配置期失败：`Could not resolve net.fabricmc:fabric-loom:1.14.10` → `Dependency requires at least JVM runtime version 21. This build uses a Java 17 JVM.`；`settings.gradle` 无条件 include 四线，故连 `:forge-1.20.1` 也会在 `Configure project :fabric-1.20.1` 崩。
- 下表是各子项目的 **`java.toolchain.languageVersion` 编译目标**，**不是 daemon 选择依据**（daemon 固定 21，Gradle 自动选对应 JDK 编译）：

| 子项目 | 工具链 Java | 依据 |
|---|---|---|
| `neoforge-1.21.1` | 21 | `neoforge-1.21.1/build.gradle:63` `JavaLanguageVersion.of(21)` |
| `forge-1.20.1` | 17 | `forge-1.20.1/build.gradle:63` `of(17)` |
| `neoforge-26.1.2` | 25 | `neoforge-26.1.2/build.gradle:75` `of(25)` |
| `fabric-1.20.1` | 17 | `fabric-1.20.1/build.gradle:25` `of(17)` |

- toolchain 由 `settings.gradle` 的 `org.gradle.toolchains.foojay-resolver-convention` 1.0.0 解析；**优先本机 `/usr/lib/jvm`**，foojay 仅在本机缺该版本时才下载。策略：**以显式 `JAVA_HOME` 为主**，不把自动下载当默认兜底；本机三套 JDK 已在位。
- **不要**按线把 `JAVA_HOME` 切成 17 去跑 gradle（17 会崩在 loom classpath）；**统一 21** 作 daemon，四线构建与实机共同验证过（25 亦可跑，不作默认）。
- **wrapper 自检（本批实跑通过）**：`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --version` ⇒ `Gradle 9.2.1` / `Launcher JVM: 21.0.12.1` / `Daemon JVM: /usr/lib/jvm/java-21-openjdk-amd64` / `OS: Linux 7.0.0-42-generic amd64`。

### 5. Gradle（唯一构建入口）

- wrapper：`/mnt/f/MCProject/astral_dice_multiloader/gradlew`（实读 8,733 B、可执行），`gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl=https\://mirrors.aliyun.com/gradle/distributions/v9.2.1/gradle-9.2.1-bin.zip` ⇒ **Gradle 9.2.1，aliyun 镜像**。
- **`~/.gradle` 迁移后为空**（2026-10-31 实读：新家目录没有 `.gradle`）⇒ 首次 `./gradlew --version` 要下约 130 MB 的 dist（aliyun 源，本批已下完并跑通），随后所有依赖重新下载。全仓**最大的一次性成本**就在这里，别误判成构建失败。
- 构建命令（**Linux 用 `./gradlew`，没有 `gradlew.bat`**）：

  ```bash
  cd /mnt/f/MCProject/astral_dice_multiloader
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # daemon JVM，必须 ≥21；不要看到线是 17/25 就改这里
  ./gradlew :neoforge-1.21.1:classes --console=plain
  ./gradlew :neoforge-1.21.1:jar --console=plain
  ```

- 铁律：**所有 `./gradlew …` 之前 `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`**；编译目标由 `java.toolchain` 决定，与 daemon JVM 无关。
- 根 `gradle.properties` 实读：`org.gradle.jvmargs=-Xmx3G`、`daemon=true`、`parallel=true`、`caching=true`、`configuration-cache=true`。
- **单 gradle 并发位**：同一工作树同一时间只允许一个 gradle；启动前查锁（〈互斥锁与并发〉）与 `pgrep -af gradle`。
- **依赖镜像：真机已不需要 aliyun init.d**（本批最重要的出网变化）：
  - 2026-10-31 真机实测：Central / aliyun / neoforged / github **全部 200**（`annotations-26.0.2.pom` 实拉）。**VM 期的 Central 403 封禁不存在了。**
  - ⇒ **不要创建 `~/.gradle/init.d/00-aliyun-central-mirror.gradle`**，也不要往仓库写镜像坐标；`~/.gradle/init.d/`、`~/.mc-mirror`、`~/.local/bin/neoforge-mirror-sync.sh` 不存在都是正常状态。
  - 首次构建前跑一次自检（坏了才动手，别预防性加镜像）：`curl -s -o /dev/null -w '%{http_code}\n' https://repo.maven.apache.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.pom` ⇒ 期望 **200**；若再 403，才按 `docs/handover-2026-10-31-linux-vm-adoption.md` §6 第 2 步临时启用 host 级镜像（不改仓库）。
- **26.1.2 必须加 `--no-configuration-cache`**：`createMinecraftArtifacts` 的 `DefaultConfigurableFileCollection` 在配置缓存序列化阶段报 `error writing value of type …`（历史实测，与镜像无关）。
- **构建耗时：热缓存已实测，冷构建仍未测**（`[[PENDING:build-time-native]]`）。2026-10-31 真机热缓存：`:neoforge-1.21.1:classes` 32s、`:forge-1.20.1:classes` 30s（含四线配置期，`createMinecraftArtifacts UP-TO-DATE`），日志同时打印 `整合包根目录 (MC_HOME): /mnt/d/.minecraft`。`build/moddev/artifacts` 里 Windows 时代产物仍在（`forge-1.20.1-47.4.10-merged.jar`、`neoforge-21.1.235-merged.jar` 等）⇒ 可复用、省掉 decompile；`~/.gradle` 已由本批重建。**禁止**把 VM 的 `2m44s` 当本机基线，也不得假定 `12m23s` decompile 必然发生。
- 换线构建耗时与失败模式以**构建台账**为准（每次记录线 / 命令 / 耗时 / 结果），单次耗时不当 SLA。

### 6. 前置库 starengine（1.1.0，必须先进 mavenLocal）

- 四线 `gradle.properties` 实读：`starengine_group=com.merlinkitsune.starengine`、`starengine_version=1.1.0`（fabric 另有 `starengine_version_range=>=1.1.0`）；四线 `build.gradle` 均含 `mavenLocal()`（neo `:30` / forge `:29` / neo26 `:39` / fabric `:47`）。
- 正确顺序：先在 `/mnt/f/MCProject/starengine`（独立仓，自带 gradlew 与四平台子项目）发布到**本机** mavenLocal，再构建本仓。缺哪条线的制品，哪条线就解析失败（`Could not resolve com.merlinkitsune.starengine:...`）。
- **本机 mavenLocal 当前为空**（2026-10-31 真机实读：`~/.m2` 不存在）⇒ **首次必须自己发布四条线**，否则四线全部卡在配置期。starengine 仓的 `build/libs` 里已有 1.1.0 jar（fabric 112,634 B / forge 103,605 B，2026-10-31 02:0x 构建），但那是 `build/libs`，**不等于** mavenLocal 已有制品。
- 发布命令（daemon JVM 必须 21）：
  ```bash
  cd /mnt/f/MCProject/starengine
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  ./gradlew :neoforge-1.21.1:publishToMavenLocal --console=plain
  ./gradlew :forge-1.20.1:publishToMavenLocal    --console=plain
  ./gradlew :fabric-1.20.1:publishToMavenLocal   --console=plain
  ./gradlew :neoforge-26.1.2:publishToMavenLocal --console=plain
  ```
  本机首次已发布成功（2026-10-31，四线 `BUILD SUCCESSFUL`，各约 1s——因为 Windows 时代的 `build/moddev` 产物被 `cp -a` 保留，未重新 decompile；若 `build/` 被清空，首次会触发 decompile+patch，VM 量级 12m23s / 12m46s）。发布完成的判据：`ls ~/.m2/repository/com/merlinkitsune/starengine/*/1.1.0/` 四件齐（jar + pom + module）。
- **若解析失败属环境问题（BLOCKED），不是产品缺陷。**

### 7. git（真实工作树 + autocrlf + 无全局代理）

- 分支 `multi-main`，HEAD 快照 `c31d36f6`（`build(gradle): 整合包投放根改 mcHomePath 解析；Jade 插件键两线补齐`）。
- 仓库级 `core.autocrlf=true`、`core.ignorecase=true`（Windows 克隆遗留）；`core.eol` 未设。
  - `.gitattributes` 只对 `src/generated/**` 的 json/cache 与 `gradlew`、`*.sh`、`*.py`、`*.conf` 强制 LF，**`.md` 不强制**。
  - **行尾现状（本批实读）**：`AGENTS.md`、`docs/agent/PROJECT-REFERENCE.md`、`build.gradle` 是 **CRLF**；`docs/agent/AGENTS-dsh-linux.md`、`scripts/test/linux/*.sh` 是 **LF**。改 CRLF 文件要按字节替换保 CRLF（`edit` 工具按 `\r\n` 匹配容易失手），改 LF 文件直接写 LF。
  - **不要整份重写既有 `.md`**（会把 CRLF 全变 LF 造成假 diff）；新建文件用 LF 并如实报告编码。
  - **只改大小写**的重命名在 `core.ignorecase=true` 下可能被 git 忽略 ⇒ 用 `git mv` + `git status` 复核。
- `git status` 里 **4 个用户未跟踪文件**（实读，**不得 add/commit/删除**）：`models/astral_anvil.bbmodel`、`scripts/test/cases/TMP-ATTRLESS-HYDRA-1.21.1.json`、`scripts/test/resources/kubejs/1.21.1/server_scripts/astral_batch3_probe.js`、`zz_screen_probe.py`。
- **出网 = clash-verge-rev TUN 全局接管，直连**（2026-10-31 用户裁决 + 实读 `/configs`：`tun.enable=true`、`device=Meta`、`auto-route=true`、`inet4-address=198.18.0.1/30`，内核 mihomo `v1.19.32`）⇒ 远程 git **直接执行，不加 `-c http.proxy=…`**，也不要 `export *_proxy`（环境里本来就没有）。
  - **仓库 `.git/config` 仍有 `http.proxy`/`https.proxy` = `http://127.0.0.1:7897`**（Windows 期遗留、与 Windows 共用，**不要改**）。7897 仍在听，走它也能通；要单条命令**严格直连**就 `git -c http.proxy= -c https.proxy= <子命令>` 覆盖，**不要** `git config --unset`。
  - 查代理状态用 clash-verge-rev 外部控制：`curl -s -H 'Authorization: Bearer <secret>' http://127.0.0.1:9097/configs`（`/version` 看内核）；`secret` 取 `~/.local/share/io.github.clash-verge-rev.clash-verge-rev/config.yaml` 的 `secret:` 字段 —— **不要把明文 secret 写进仓库或日志**。`401 Unauthorized` 是 secret 不对，不代表断网。
- **git hooks 在 Linux 上空转**：四个 hook 是三段 pwsh 探测版，本机 PATH 无 pwsh（`/mnt/c` 里是 Windows PE，不可执行）⇒ 静默 `exit 0`，松散引用自愈不执行；遇 `HEAD -> unknown revision` 先查 `git reflog`。提交前**手动**跑 `python3 -B docs/agent/verify-split.py`（四项 `[OK]`）与 `python3 tools/verify_sign_display_names.py`（`GATE: PASS`）。

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
- **子代理模型固定且唯一**：派活时显式写 `provider: "deepseek-official"` + `model: "deepseek-flash"`；**不得**换用其它 model/provider；路由失效 ⇒ 停下报用户，不自行降级。

### B. 子代理职业指定（brief 第一句）

- brief **第一句**必须是「你是一位<职业>（<加载器>/MC <版本>），<该职业的交付标准>」。
- 任务 → 职业对照：模组开发 / 跨加载器移植 / 项目文件分析员 / 本地化工程师 / 构建 CI 工程师 / JVM 崩溃排查 / 独立功能审查员 / 技术文档工程师 / 资源包与手册工程师。
- **免人设句必须原文带**（2026-10-22 起）：「不要使用任何人设 / 角色扮演 / 语气修饰 / 昵称自称，按你的职业身份以中性技术口吻输出」。
- 审查取证类一律用**全新** `subagent`（不吃本会话上下文，避免自证）。
- 职业指定的三个作用：约束动作边界、给质量标准、固定模型。

### B-2. 派活通道（只走 DSH 自带）

- 通道：`subagent` / `subagent_fork` / `workflow`；复核走 `code_review_*`（`code_review_preview` → `code_review_execute`）。
- 纪律：一次一件（一个可独立验收的改动面 + **一个**编译目标）；brief 宁可多写 3 行判据；同一任务最多重试 **1 次**；验收只看产物。
- 团队并行时写范围（子代理 brief 里的文件白名单）必须互不重叠；`send_message` 改需求，`interrupt_agent` 止血。

### C. 交接文档四层事实源

1. `docs/handover-1.5.0-main.md` —— 滚动正本（每轮更新 §4/§5）。
2. `docs/handover-new-session-context.md` —— 新会话寒暄提示词。
3. `docs/agent/AGENTS-full.md` 末尾「待办交接各批」—— **只追加**，不改旧条。
4. `docs/handover-<版本>-<主题>.md` —— 单批专题（本批：`docs/handover-2026-10-31-native-linux-migration.md`）。
- ⛔ `docs/AGENT-HANDOFF.md` **不是现状**，只作历史参考。
- **接手顺序**：`git status --short -uall` + `git log --oneline -1` → 四线 `gradle.properties` 的 `mod_version` → handover §0/§5 → 按 §7 取路径。
- **交班 6 义务**（其中一项必做）：`python3 -B docs/agent/verify-split.py` 四项全 `[OK]`。
- 事实源快照会落后：2026-10-31 实读四线 `mod_version` 已是 `1.5.0-rc.7+<后缀>`（`neoforge_1.21.1` / `forge_1.20.1` / `neoforge_26.1.2` / `fabric_1.20.1`）—— 这类数字**一律实读覆盖**。

## 📌 本文件的定位与自检

- 本文件 = **DSH on Linux 的规则正本**；`docs/agent/AGENTS-dsh.md` = **Windows 原生**历史正本（不删、不改红线区，`verify-split.py` 项 1b 依赖它）。
- 规则全文按需查：`docs/agent/AGENTS-full.md`（实读 4,244 行）+ 标题行号总表 `docs/agent/AGENTS-TOC.md`（198 行）。定位：`grep -n '^#' docs/agent/AGENTS-full.md`，按总表查区间后 `sed -n '<a>,<b>p'`。
- 派活 brief 里写区间 `L<a>–L<b>`（查 `AGENTS-TOC.md`），读规则由**子代理**执行，不要把大段原文贴进主会话。
- 自检：`python3 -B docs/agent/verify-split.py`（四项：快照保序包含 + `AGENTS-dsh.md` 红线区与快照 L2–L120 逐字一致 / 根 `AGENTS.md` 结构完整 / docs 引用存在 / 体量 < 32K 且 using-superpowers 段未删）。`--strict` 只把「docs 引用」提为致命。
- 改动本文件后同样跑一遍。`[[PENDING:…]]` 应清零；若某项事实源长期不可得（如某线尚未实跑），**保留标记并列为已知缺口**，不得删标记假装已验证。

## 📍 红线区 —— Linux 替换口径（原文见 `docs/agent/AGENTS-dsh.md`〈📍 生效红线区〉）

> 本节为 **Linux 替换口径**，**不参与** `verify-split.py` 的逐字校验；红线文字正本仍是 `AGENTS-full.md` L2–L120，Linux 只替换其中的路径 / 工具通道。

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
| 「Windows 开发配置不可动」 | **新增（2026-10-31 用户硬约束）**：本机与 Windows 共用同一块盘；只改 Linux 侧需要的文件，不删 / 不改 Windows 专属配置（`mt.conf` 的 Windows 路径、`*.ps1`、`gradlew.bat`、`run/start-*.bat`、`.vscode` 的 `F:\` 配置一律保留原样） |

- 优先级与基线（P0 = `neoforge-1.21.1` + `forge-1.20.1`；P1 = `neoforge-26.1.2` + `fabric-1.20.1`，`1.5.0` 正式版前不验证、不移植）、包根 `com.merlinkitsune.astral_dice`、fabric 内嵌库前置 `python3 tools/loom_embedded_jars.py --apply`（KI-F23）—— 全部**与宿主无关，原文有效**。

## 二次验证规范（与宿主无关，命令 Linux 化）

- 触发面（5 类）：改产品源码、改规则/守门脚本、改构建配置、改数据/配方、任何「已修好/已通过」的结论。
- 复核一律 `code_review_preview` → `code_review_execute`：**不是** subagent 派活；预览只读、不发送源码，执行需 `confirmed=true`。**禁喂**主会话结论/倾向，让它自己 `git diff` 取证。
- 报告验收 5 条：① 结论有产物/行号级证据；② 每条判据可复现（给命令）；③ 区分 FAIL / ERROR / TIMEOUT / BLOCKED；④ 未做项与风险如实列出；⑤ 不给「大概/应该」。
- 两层守门不得互替：`verify-split.py` 管规则文件结构，`code_review_*` 管代码/文档实质；一层通过不能替代另一层。

## 子代理与测试环境硬规则（2026-10-08 裁决，8 条 Linux 化）

1. **一 subagent 一任务，用完即弃**；不串任务、不复用上下文。
2. **测试/演示一律用非亡灵生物**（`minecraft:pillager` / `minecraft:cow`），不要用亡灵类。
3. **流程结束必关游戏进程**（见〈测试收尾〉），并核对无残留 java；收尾报告必须贴实际命令与输出原文。
4. **复核一律走 `code_review_*`**，不派 subagent 做复核。
5. **实机必须由用户提示词明确下达**；未下达时禁止自行开客户端 / 连拍 / 探针。
6. **改动后旧实机证据作废**：先停旧实例，再谈新证据。
7. **开发流程**：改 → 编译 → 静态守门 → 实机（获授权后）→ 复核（分级 a–e）→ 收尾关进程。
8. **派子代理一律屏蔽人格注入**（免人设句原文带，见〈代理协作契约 B〉）。

## 命令执行规范（bash；Windows 宿主同一套 bash 口径，见 `docs/agent/AGENTS-dsh.md`〈⚙️ 环境与工具链口径〉）

1. **一律 bash**（`SHELL=/bin/bash`，实读）；脚本首行 `#!/usr/bin/env bash` + `set -euo pipefail`。**禁止**再写 PowerShell / `.ps1` 结论性命令（系统 PATH 无 pwsh；`*.ps1` 是 Windows 历史工具，Linux 侧不调用）。**不再新增** `.ps1` / `.psm1`（pwsh 测试资产已按 `docs/features/devnext-pwsh-retirement-register.md` 退役；Windows 侧同一口径，入口 `bash scripts/test/win/mt.sh`）。
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
- **本机缺的输入工具**（实读，不要假设存在）：`xdotool`、`wtype`、`wmctrl`、`grim`、`kdotool` **未安装**；`ydotool` 已装但**没有 socket**（`/run/user/1000/.ydotool_socket` 不存在），不要用它。`spectacle` 已装（`/usr/bin/spectacle`），可做 KDE 原生截图兜底：`spectacle -b -n -a -o /tmp/mc-world.png`。
- **GUI 级驱动只在需要时用**：文字/坐标注入优先 `computer-use-linux` 的语义选择器（role/name），退而用坐标；坐标以**返回截图的 `coordinate_width/height`** 为准（可能被降采样），不要拿物理分辨率换算。
- **驱动优先级（2026-10-31 沿用）**：游戏内输入/状态读写**首选 AutoJS `eval_js`**（脚本上下文直接操作，绕过窗口焦点问题）；GUI 级（建世界、开关菜单）用 `computer-use-linux` / `spectacle` + OCR；Windows 侧的 `scripts/test/mt_inject.py`（ctypes user32 注入；原 `mt_inject.ps1` 的替代物，旧件仅作溯源）是 **Windows-only**，Linux 无等价实现。
- 游戏窗口是 **X11（XWayland）窗口**，`xwininfo -root -tree | grep -i minecraft` 可核对标题/几何；**GLFW 不暴露 AT-SPI**，`list_windows` 里可能看不到 MC —— 看不到就走整屏截图 + 坐标，不要卡在这里。
- 截图证据一律落 `temp/tNN/`（不入库）；不得用固定外部目录。

## AutoJS 运行时通道（Linux 侧口径）

> 详细契约见 `docs/features/autojs-test-harness-spec.md`；操作口径见 `docs/features/autojs-testing-flow.md`。本节只定规则。

- 端点：Streamable HTTP `POST http://127.0.0.1:25585/mcp`，默认 bind `127.0.0.1`；探活 `GET /health` 期望 `200`：

  ```bash
  curl -fsS -m 3 http://127.0.0.1:25585/health
  ```

- 配置：`run/<线>/config/autojs.json`（1.21.1 / 1.20.1 实读存在）；端口覆盖序 **`-Dautojs.port=` > `AUTOJS_PORT` > 配置文件**（`0` = 系统分配）。
- **同一时刻只能一条线占用 25585**（否则 `BindException`）；切线前先停旧客户端。
- **MCP 注册现状（2026-10-31 真机实读）**：`~/.dsh/profiles/web/cordis.patch.yml` 只注册了 `blockbench` / `computer-use-linux` / `screen-ocr`，**没有 `autojs` MCP server**；`~/.dsh/skills/` 目录也不存在（旧 `~/.dsh/skills/autojs/SKILL.md` 不在）。
  - ⇒ 在 DSH 里调 AutoJS **要么**用 `curl` 直接打 JSON-RPC（`initialize` → `tools/call`），**要么**由用户确认后再把 `autojs` server 注册进 `cordis.patch.yml`（`transport: streamable-http`、`url: http://127.0.0.1:25585/mcp`、`headers.Accept: application/json, text/event-stream`、`failOnStartupError: false`）。**不要**假设 `mcp__autojs__*` 工具存在。
- 工具四件（HTTP 协议不变）：`status` / `eval_js { code, side: auto|server|client, timeout_ms }` / `kubejs_log { side, kind, limit }` / `reload_scripts { side, timeout_ms }`；返回 `{ ok, side, elapsedMs, type, value, diagnostics }`，**必看 `diagnostics`**。
- 铁律：`side` 必须明确（读权威数据用 `server`，读 HUD/FPS/输入用 `client`，`auto` 仅在两侧等价时用）；运行时不能新建注册表条目；只求值最后一个表达式；Rhino 不是 JS 反射；返回值必须 `JSON.stringify`。
- 错误归一：`isError` / `ok=false` 记 **BLOCKED**（环境问题），**不得**判成功能缺陷；端点没起同此。
- 历史实测（VM 期，1.21.1，协议未变）：client 侧 **KubeJS 里没有裸 `Minecraft` 全局**，必须 `Java.loadClass('net.minecraft.client.Minecraft').getInstance()`；`status.endpoints` = `{"bindAddress":"127.0.0.1","port":25585,"mcpPath":"/mcp"}`。真机复测属迁移后的待办（`[[PENDING:autojs-native]]`）。
- 安全边界：`unrestricted=true` 时**只绑 loopback**，禁 `0.0.0.0`。

## 🧪 测试台现状（Linux）

- Windows 测试台（**迁移前实读快照 = 历史记录；现行执行口径见上**）：原 `scripts/test/mt.ps1` 851 行，入口 `mt.ps1 --phase <preflight|build|env|launch|cases|report|stop>`；全部 PowerShell 合计约 19,384 行。**整台不可移植**，且按红线**不改它**（该 pwsh 台已按 `docs/features/devnext-pwsh-retirement-register.md` 退役，仅作溯源）。Windows 侧现行入口 = `bash scripts/test/win/mt.sh --phase <preflight|env|build|launch|cases|report|stop>`（bash 由 MSYS2 / git bash 提供）；Linux 侧只用 bash 台。
- **Linux 测试入口 = `scripts/test/linux/{env,launch,stop}.sh`**（bash，零 pwsh 依赖；`scripts/test/linux/README.md` 为完整操作说明）：
  - `env.sh [--version <线>] [--quiet]`：**只读**环境检查（仓库/挂载、各线 JDK 与 `run/`、`mods/` 与 autojs·rhino jar、AutoJS `/health`、客户端残留、锁、Gradle 守护）；机器行 `CHECK: <key>=OK|WARN|FAIL` + 收尾 `ENV_RESULT: OK|WARN|FAIL`；退出码 `0` 无 FAIL / `1` 有 FAIL / `2` 用法错误。
    - 2026-10-31 真机首跑（本批改路径前）：`ENV_RESULT: WARN — 0 项阻塞，3 项提示`；改后重跑见 `docs/handover-2026-10-31-native-linux-migration.md`。
    - 键名变化：`hgfs_writable` → **`repo_writable`**（`ntfs3/ext4` 走 OK 分支并注明链接实测可用）；新增 `locks_stale`（陈旧锁检测，只报不删）；`mt.conf` 的 Windows 路径提示从 WARN 降为 INFO（Windows 台配置，Linux 不动）。
  - `launch.sh --version <线> [--timeout 600] [--quickplay <世界名>] [--kill-on-timeout] [--log-dir <dir>] [--dry-run]`：**优先复用 `run/start-<线>.sh`**（存在且可执行），否则回退 `bash ./gradlew :<线>:runClient --console=plain`；`setsid` 独立进程组启动，日志落 `scripts/test/linux/logs/<线>-<ts>.log`，PID 落 `run/<线>/linux-run.pid`。
  - 就绪判据（**以 AutoJS 为准**）：`GET http://127.0.0.1:25585/health` = 200 且 `status` 里 client `registered:true` / `available:true`。日志 marker（`Total time to load game and open world was`、`logged in with entity id`）**只能**作为「已开世界」的附加证据——dev 客户端默认停在标题画面，错用会让每次启动都误判 TIMEOUT。
  - launch.sh 退出码：`0` 就绪 / `1` 启动失败或进程提前退出 / `2` AutoJS 端点未就绪（ERROR，逃生阀 `MT_ALLOW_NO_AUTOJS=1`）/ `11` BLOCKED / `12` TIMEOUT / `130` 用户中断。
  - `stop.sh --version <线> | --all [--force] [--purge-saves] [--stop-daemon] [--dry-run]`：标准收停。先按 `linux-run.pid` 收进程组（`TERM` → 15s → `KILL`），再 `/proc` 扫描补漏，复核无残留才输出 `LINUX_STOP: OK`；有残留输出 `LINUX_STOP: RESIDUAL` 并 `exit 1`。
  - **未复刻的 Windows 能力**：进程防撞锁、测试前世界清场、OP / 禁 AI 闸门。这些前置在 Linux 侧仍需人工确认，不得当作「已验证」。
- 启动脚本 `run/start-{1.20.1,1.21.1,26.1.2}.sh`（实读 8,650 / 8,665 / 9,636 B，均 `rwx`；**fabric-1.20.1 暂无 start 脚本**）：
  - 用法 `./run/start-1.21.1.sh [<额外 gradlew 参数…>]`；脚本自行 `cd` 到仓库根，`set -euo pipefail`；前台 `bash run/start-1.21.1.sh`，后台 `setsid nohup bash run/start-1.21.1.sh > /tmp/launch-1211.out 2>&1 &`。Wayland 会话下环境须就位：`DISPLAY=:0 WAYLAND_DISPLAY=wayland-0 XDG_RUNTIME_DIR=/run/user/1000`。
  - **`run/` 整体在 `.gitignore:61` 里** ⇒ `run/start-*.sh` 是**未跟踪/被忽略**文件；确实要入库才 `git add -f`，默认**不 add**。
  - **JDK：`DEFAULT_JDK` 三脚本统一 `/usr/lib/jvm/java-21-openjdk-amd64`**；`ASTRA_JAVA_HOME` **只覆盖 daemon JVM**，不改变 toolchain。
  - **`DISPLAY` 为空直接 `exit 1`**：Wayland 会话必须靠 XWayland（本机 `DISPLAY=:0`）。
  - 收尾只收本实例（按 `/proc/<pid>/cwd` 与 `--gameDir` 精确匹配），先 `SIGTERM` → 20s → `SIGKILL`；**绝不**全机 `pkill -f java`。
  - 1.20.1（legacyforge dev run）加载的是 `run/1.20.1/mods/astral_dice-<ver>+forge_1.20.1.jar`，不是 `build/resources/main`：改了 `src/main/resources` 后**必须** `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :forge-1.20.1:pushToDevRun`（约 60s）再启动。1.21.1（MDG NeoForge）无此问题。
- **已知遗留（迁移前既有，不随换机消失）**：
  - `fabric-1.20.1` 实机未通过：Loom dev runtime 给 LWJGL `3.3.2-snapshot`，Sodium 0.5.x 只接受 `3.3.1`（`Sodium-EarlyDriverScanner … Installed version: 3.3.2-snapshot / Required version: 3.3.1`）⇒ 构建侧 pin 或换 Sodium，P1。
  - 26.1.2 客户端停在 `KubeJSErrorScreen`：`EvaluatorException: Failed to load Java class 'com.merlinkitsune.starenginelib.economy.StarCoinWalletState': Class could not be found!`（jar 内确有该类；Rhino 类加载器看不到 jarJar 嵌套类），P1。
  - 1.20.1 首启曾因 Jade 11.13.3 的 dev-only `Missing config translation` 断言崩在标题屏（已在三语 lang 补 `config.jade.plugin_astral_dice.{shop_cooldown,event_drawer_cost}` 修复）；1.21.1 是 Jade 15.10.6 无该断言。
  - `run/26.1.2/mods/` 残留旧 `astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`，建议清理（未做）。
- 规则层继续有效（与实现无关）：禁批量编排、单条手动、`FAIL` / `ERROR` / `TIMEOUT` / `BLOCKED` 语义不混同、测试顺序 1.21.1 先行、1.20.1 门控（未过记 `GATED`）、断言以服务端权威日志 `AP_<tag>_<KEY>` 为准、报告不得放宽口径。
- 静态守门：纯 python 守门**可跑**——`python3 -B docs/agent/verify-split.py`（2026-10-31 实跑：全部通过）、`python3 tools/verify_sign_display_names.py`（`GATE: PASS`）、`python3 tools/audit_patchouli_keys.py`、`python3 -B tools/loom_embedded_jars.py`、`python3 scripts/test/lib/mt_case.py validate`。`scripts/` 与 `tools/` 下的 `.ps1` 是 **Windows 历史工具**，Linux 侧**不调用**；其余门禁在 Linux 下**手段缺失**，不得假装通过，也不得把「没跑」写成「已通过」。
- 实机一律等用户明确下达；未授权时只做编译 + 静态守门。

## 互斥锁与并发

- 锁目录 `/mnt/f/MCProject/.agent-locks/`；DSH 动工作树前建 `dsh.lock`，收尾必删。**锁文件为三行口径**：`PID=` / `TIME=` / `TASK=`（正本 `docs/agent/AGENT-SPLIT.md` L25），可附加 `NOTE=` 说明行。
- 2026-10-31 迁移批实读：目录里原本只有 `dsh.lock`，内容 `PID=2103` / `TIME=2026-10-31T14:30:50+08:00` / `TASK=26.1.2-1.5.0-sync-W0`，而 **PID 2103 已不存在 ⇒ 陈旧锁**。按规则「陈旧锁不自行删除，报用户」；本批在报告里说明后，由当前 DSH 会话（`PID=26899`）接管该锁并写入 `TASK=native-linux-adoption` + `NOTE=`，收尾时删除。
- 动工作树或起 gradle 前：① 看锁是否存在、`kill -0 <pid>` 是否存活（`env.sh` 的 `locks_stale` 会自动报）；② 看 `build/` 最近 30 分钟有无修改；③ 存在他宿主的活跃锁（`kimi.lock` 等）⇒ 停手等待，**陈旧锁不自行删除**，报用户。
- 同一工作树同一时间**只允许一个 gradle**；团队并行时写范围互不重叠。

## 交接与事实源（Linux 侧）

- 四层事实源见〈代理协作契约 C〉；每轮收尾更新 `docs/handover-1.5.0-main.md` §4/§5 + `docs/handover-new-session-context.md`。
- 接手先实读：`git -C <工作树> status --short -uall`、`git log --oneline -1`、四线 `mod_version`、`~/.m2` 是否有 `com.merlinkitsune`（决定能否直接构建）、`.agent-locks/` 状态、`findmnt -T` 的文件系统类型。
- 报告「已完成」前必须有一次实跑证据（命令 + 原文输出片段），不接受「应该可以」。
- **本批专题**：`docs/handover-2026-10-31-native-linux-migration.md` —— 原生 Linux 真机实测（挂载/工具链/出网/链接语义）、VM→真机路径重映射、游戏测试资产接管、以及剩余 checklist。
- **相对路径纪律**：交接文档、仓库内脚本、规则文本一律相对仓库根书写（脚本用 `ROOT="$(cd "$(dirname "$0")/.." && pwd)"` 自解析）；宿主绝对路径只允许出现在专题交接文档的附录快照里。

## 📇 规则索引

- 区间表来自 `docs/agent/AGENTS-dsh.md`〈📇 规则索引〉（对应 `AGENTS-full.md` 实读 4,244 行，`AGENTS-TOC.md` 198 行标题表；注意 `AGENTS-TOC.md` 自称 source 3,665 行是**旧快照**，行数以 `wc -l` 实读为准）；**Linux 宿主同样适用**，命中条目的 Windows 命令按〈Gradle〉/〈命令执行规范〉改写：

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
| VM 时代历史（hgfs 代价 / 本机盘副本） | `docs/handover-2026-10-31-linux-vm-adoption.md` · `docs/handover-2026-10-31-local-disk-workspace.md` |
| 工程拓扑 / 四线基线 / 构建耗时台账 | `docs/agent/PROJECT-REFERENCE.md` |
| 委派操作手册（模板/失败模式） | `docs/agent/delegation-runbook.md` |
| 测试流程与规则总览（Windows-only 实现） | `scripts/test/TESTING-RULES-OVERVIEW.md` |
| Linux 测试台操作说明 | `scripts/test/linux/README.md` |
| AutoJS 通道契约 | `docs/features/autojs-test-harness-spec.md` · `docs/features/autojs-testing-flow.md` |
| 规则库自身说明 | `docs/agent/README.md` |

- 维护约定：① 每次必读的红线 → 写根 `AGENTS.md` 红线区并同步 `AGENTS-full.md`；② 情境规则 → 追加 `AGENTS-full.md` 并更新 `AGENTS-TOC.md` 与本索引；③ 本文件体量上限 **~30K 字符**（超了拆索引 + 子代理读取），改完跑 `python3 -B docs/agent/verify-split.py`；④ 长驻外部内容走索引 + 子代理读取，不塞进主会话。
