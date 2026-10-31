# DSH 在 Linux 原生宿主的规则正本（AGENTS-dsh-linux）

> **定位**：本文件是 **DSH 宿主在 Linux 原生环境**下的主规则。DSH 在 Linux 上先读本文件，再按根 `AGENTS.md` 的共同红线与按需索引执行；**Windows 规则**见 `docs/agent/AGENTS-dsh.md`（历史正本，仅对 Windows 宿主有效）。
> **冲突裁决**：同一宿主上两份文件冲突 ⇒ **Linux 宿主以本文件为准**，Windows 宿主以 `docs/agent/AGENTS-dsh.md` 为准；共同红线以根 `AGENTS.md` 为准。
> **红线原文不复制**：红线区逐字原文的唯一副本在 `docs/agent/AGENTS-dsh.md`〈📍 生效红线区〉（`docs/agent/verify-split.py` 项 1b 拿它和 `docs/agent/AGENTS-full.md` L2–L120 逐字比对）。本文件只给 **Linux 下的替换/补充口径**，复制第二份会让红线两处漂移。
> **事实源原则**：本文数字均为 **2026-10-31 实读快照**；宿主版本、路径、构建命令、日志路径一律以**实读命令**为准，文档只是当次快照。`[[PENDING:…]]` = 尚无实测依据，**不得**当已验证结论引用，也不得据此施工。

## 🚀 会话启动第一步：加载 using-superpowers（2026-10-24 用户裁决，每次会话必做）

- 回复任何内容、调用任何工具**之前**，先调用 `skill` 工具、`name="using-superpowers"`。
- 该技能规定「先查技能目录，1% 可能适用就须加载」，并负责其它流程类技能（brainstorming / writing-plans / systematic-debugging / verification-before-completion 等）的按任务加载。
- `skill` 工具不可用（未注册 / 加载失败）时，**必须**在回复里明写「本次会话未能加载 superpowers」，不得假装已加载。
- 派发子代理的 brief **必须**带一句「动手前先加载 using-superpowers（团队硬规则，任何动作之前）」。

## ⚙️ 环境与工具链口径（Linux 原生）

### 1. 主机与桌面（实读）

- 发行版 `Ubuntu 26.04.1 LTS`（`VERSION_CODENAME=resolute`），内核 `7.0.0-42-generic`；用户 `kitsune`，登录 shell `/bin/bash`。
- 桌面 = **KDE Plasma 6.6.6**，会话类型 **Wayland**：`XDG_SESSION_TYPE=wayland`、`WAYLAND_DISPLAY=wayland-0`、`DISPLAY=:0`、`XDG_RUNTIME_DIR=/run/user/1000`。
- **XWayland 是 MC 的硬前提**：MC 1.20.1 / 1.21.1 的 GLFW 只有 X11 后端，Wayland 会话下 `DISPLAY=:0` 必须存在（`run/start-*.sh` 在其为空时直接 `exit 1`）；**26.1.2 也已实测（2026-10-31 02:49，`xwininfo` 显示 `0x2200007 "Minecraft NeoForge* 26.1.2" 1920x1080+320+180`）⇒ 三条实机线全部走 XWayland/X11 窗口，GLFW 无 Wayland 后端；Wayland 下的唯一前提仍是 `DISPLAY=:0` 存在**（`[[PENDING:wayland-262]]` 已关闭）。
- SELinux / AppArmor 本机未涉及，不作为本文件的规则前提。
- **一台机器一种工作流**：Linux 宿主上不混用 Windows 工具链；没有路径转译层，也不假设 `/mnt/c` 存在（本机实测**不存在**）。

### 2. 路径（绝对路径纪律）

| 用途 | Linux 路径 | 与 Windows 的关系 |
|---|---|---|
| **本机盘工作副本（2026-10-31 起，工作正本）** | `/home/kitsune/Project/astral_dice_multiloader` | 由 hgfs 目录复制到 ext4 虚拟磁盘；`-next` 与 starengine 两线同名副本在 `~/Project/` 下 |
| hgfs 原始镜像（同步通道，非工作正本） | `/mnt/hgfs/MCProject/astral_dice_multiloader` | VMware 共享目录 `/mnt/hgfs` ⇒ Windows `F:\MCProject\astral_dice_multiloader`（**同一份文件**） |
| 前置库 starengine | `/mnt/hgfs/MCProject/starengine` | 同上 |
| 另一个常驻工作树 | `/mnt/hgfs/MCProject/astral_dice_multiloader-next` | 同上，2.0.0 开发线，**互不镜像** |
| 宿主互斥锁 | `/mnt/hgfs/MCProject/.agent-locks/` | 同上 |
| **整合包根（Minecraft 实例）** | `/mnt/hgfs/.minecraft`（`MC_HOME` 可覆盖） | Windows 宿主同一批文件为 `D:\.minecraft`；四线 `pushToGame` 由根 `build.gradle` 的 `mcHomePath` 统一解析，各版本在 `versions/<实例>/mods` |
| 本机 mavenLocal | `/home/kitsune/.m2/repository/` | Windows `%USERPROFILE%\.m2\repository`（**不是同一份**，见 §6） |
| DSH 用户级配置 | `/home/kitsune/.dsh/` | Windows `C:\Users\<user>\.dsh\` |

- **本机盘副本是工作正本**（2026-10-31 起）：日常命令在副本根内用相对路径（`./gradlew`、`bash scripts/test/linux/env.sh`），脚本自解析 `ROOT="$(cd "$(dirname "$0")/.." && pwd)"`；**跨副本/跨仓库**（hgfs 镜像、starengine 兄弟仓、`-next` 树）必须写绝对路径，避免落错树。
- 文档/交接一律写相对仓库根的路径；宿主绝对路径只放交接文档的「附录 A 快照」。
- **与 hgfs 原始镜像的同步**用 `scripts/dev/rsync-original.sh`（默认 dry-run；`pull` 原始→本机、`push` 本机→原始）：先 `pull` 再改、先 `git commit` 再 `push`、永不 rsync `.git`。详见 `docs/handover-2026-10-31-local-disk-workspace.md`。

### 3. hgfs 挂载特性（影响构建与 I/O）

- 挂载实测：`vmhgfs-fuse on /mnt/hgfs type fuse.vmhgfs-fuse (rw,relatime,user_id=0,group_id=0,allow_other)`。
- 已知代价：**I/O 明显慢于本机盘**（历史同类证据：WSL `/mnt/f` 9p 上 Gradle `:classes` 冷跑 9m22s、`≈275×`，见 `scripts/test/TESTING-RULES-OVERVIEW.md` §2.1 历史行）；**本机 hgfs 的倍数尚未系统实测**，不编数字；单次构建耗时见〈Gradle〉实测行，长期以构建台账为准。
- 权限位不可靠：hgfs 上文件普遍显示 `rwxrwxrwx`（`user_id=0,group_id=0,allow_other`），**不要**用 `chmod`/`stat` 的权限位判断可执行性。
- **链接与原子写实测（2026-10-31）**：`ln -s` ⇒ `Operation not supported`（**不支持符号链接**）；`ln` 硬链接 ⇒ `Permission denied`（**不支持硬链接**）——这正是 `write` / `edit` 工具在 hgfs 上报 `EPERM: ... link 'tmp' -> 'file'` 的根因。同目录 `mv` 重命名**可用**。
  - ⇒ 在 hgfs 上新增/改写文本一律用 shell：`cat > <绝对路径> <<'EOF' … EOF` 或 `python3 -c` 字节写；**不要**用依赖硬链接/符号链接的写入工具或 `sed -i` 之外的原子替换方案。改完 `file` + `wc -l` 自检编码与行数。
- 大临时文件优先放 `/tmp/dsh-<session>/`（ext4），需要交回仓库产物时再复制。

### 4. JDK（daemon 统一 21；toolchain 17/21/25 由 Gradle 自动选）

- `/usr/lib/jvm/` 实读有 `java-17-openjdk-amd64`、`java-21-openjdk-amd64`、`java-25-openjdk-amd64`。
- 系统默认 `/usr/bin/java` → `java-25-openjdk-amd64`，`java -version` = `openjdk version "25.0.4.1" 2026-08-18`（Ubuntu 26.04.4）。
- **Gradle daemon JVM 必须 ≥ 21：本仓所有 `./gradlew …` 一律先 `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`**（`gradle-launcher` 2026-10-31 01:46 实测裁决）。
- 反例证据（同一实测）：`cd /mnt/hgfs/MCProject/starengine && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :fabric-1.20.1:publishToMavenLocal` 在**配置期**即失败：`Could not resolve all artifacts for configuration 'classpath'` → `Could not resolve net.fabricmc:fabric-loom:1.14.10` → `Dependency requires at least JVM runtime version 21. This build uses a Java 17 JVM.`；又因 `settings.gradle` 无条件 include 四线，连 `:forge-1.20.1` 的任何命令也会在 `Configure project :fabric-1.20.1` 阶段一起崩。
- 下表是各子项目的 **`java.toolchain.languageVersion` 编译目标**，**不是 daemon 选择依据**（daemon 固定 21，Gradle 自动选对应 JDK 编译）：

| 子项目 | 工具链 Java | 依据 |
|---|---|---|
| `neoforge-1.21.1` | 21 | `neoforge-1.21.1/build.gradle:63` `JavaLanguageVersion.of(21)` |
| `forge-1.20.1` | 17 | `forge-1.20.1/build.gradle:63` `of(17)` |
| `neoforge-26.1.2` | 25 | `neoforge-26.1.2/build.gradle:75` `of(25)` |
| `fabric-1.20.1` | 17 | `fabric-1.20.1/build.gradle:25` `of(17)` |

- toolchain 由 `settings.gradle` 的 `org.gradle.toolchains.foojay-resolver-convention` 1.0.0 解析；**优先本机 `/usr/lib/jvm`**，foojay 仅在本机缺该版本时才下载。策略（lead 2026-10-31 裁决）：**以显式 `JAVA_HOME` 为主**，不把自动下载当默认兜底；本机三套 JDK 已在位，正常不需要 `-Porg.gradle.java.installations.paths`。
- 环境预检：`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --version` 已跑通，解析到 Gradle 9.2.1；实机侧核对：游戏 JVM（pid 78437）实测由 `/usr/lib/jvm/java-21-openjdk-amd64/bin/java` 启动，与 daemon 口径一致。**不要**按线把 `JAVA_HOME` 切成 17 去跑 gradle——17 会直接崩在 loom classpath（见上）。25 已实测可用：`JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew :neoforge-1.21.1:classes --console=plain` ⇒ `BUILD SUCCESSFUL in 50s`（Gradle 9.2.1 支持 Java 25；`[[PENDING:jdk25-daemon]]` 已关闭），但**统一仍用 21** 作 daemon，最稳且已被四线构建/实机共同验证。

### 5. Gradle（唯一构建入口）

- wrapper：`/mnt/hgfs/MCProject/astral_dice_multiloader/gradlew`（实读 8,733 B、可执行），`gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl=https\://mirrors.aliyun.com/gradle/distributions/v9.2.1/gradle-9.2.1-bin.zip` ⇒ **Gradle 9.2.1，aliyun 镜像**。
- 构建命令（**Linux 用 `./gradlew`，没有 `gradlew.bat`**）：

  ```bash
  cd /mnt/hgfs/MCProject/astral_dice_multiloader
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # daemon JVM，必须 ≥21；不要看到线是 17/25 就改这里
  ./gradlew :neoforge-1.21.1:classes --console=plain
  ./gradlew :neoforge-1.21.1:jar --console=plain
  ```

- 铁律：**所有 `./gradlew …` 之前 `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`**；编译目标由 `java.toolchain` 决定，与 daemon JVM 无关。

- 根 `gradle.properties` 实读：`org.gradle.jvmargs=-Xmx3G`、`daemon=true`、`parallel=true`、`caching=true`、`configuration-cache=true`。
- **单 gradle 并发位**：同一工作树同一时间只允许一个 gradle（`AGENT-SPLIT.md` 同款）；启动前查锁（§互斥锁）与 `pgrep -af gradle`。并行构建会互相踩 `build/` 与文件锁。
- **依赖镜像：本机出口 IP 被 Maven Central 封禁（403），必须走镜像，且不许改仓库文件**：
  - 实测：`https://repo.maven.apache.org/maven2/…` 与 `https://repo1.maven.org/maven2/…` 对 `org.jetbrains:annotations:26.0.2` 等坐标返回 **403 Forbidden**（响应 `content-type: text/plain; content-length: 415`）；加 `-x http://127.0.0.1:7897` 仍 403 —— **代理不解决此封禁**。
  - 受影响典型坐标：`org.jetbrains:annotations:26.0.2`、`org.ow2.asm:asm:9.10.1`、`org.ow2:ow2:1.5.1`；starengine fabric 编译会报 `Could not resolve … 403 Forbidden`（实测日志 `/tmp/sg-publish-fabric.log`）。
  - 可用镜像（实测全 200）：`https://maven.aliyun.com/repository/central/`、`https://maven.aliyun.com/repository/public/`（约 0.05s）、`https://repo.huaweicloud.com/repository/maven/`。
  - 落地方案 = **host 级增量，不改仓库、不提交**：`~/.gradle/init.d/00-aliyun-central-mirror.gradle`。**机制是「仓库创建瞬间把中央仓 URL 改写成 aliyun」**，不是简单追加镜像——因为 fabric-loom 的 `minecraftLibraries` 把 lwjgl 绑到独占的中央仓，单纯追加镜像不会被尝试。生效日志特征（首次实机启动实测）：
    ```
    [aliyun-mirror] repointed repo 'MavenRepo' (https://repo.maven.apache.org/maven2/) -> https://maven.aliyun.com/repository/public in :fabric-1.20.1
    [aliyun-mirror] repointed repo 'MavenCentralLWJGL' (https://repo.maven.apache.org/maven2/) -> https://maven.aliyun.com/repository/public in :fabric-1.20.1
    ```
    `~/.gradle/init.d/` 原不存在（本次新建）；`~/.gradle/gradle.properties` 不存在。
  - **铁律**：不要把镜像坐标写进各子项目 `build.gradle`——镜像只属于本机出网事实，不是仓库配置。
  - 首次构建前自检：`curl -s -o /dev/null -w '%{http_code}\n' 'https://repo.maven.apache.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.pom'` ⇒ **403 说明封禁仍在（镜像必须启用）**；再看 `'https://maven.aliyun.com/repository/public/org/jetbrains/annotations/26.0.2/annotations-26.0.2.pom'` ⇒ 应为 **200**。
  - **neoforged 仓库连通性（2026-10-31 两次实测）**：本机 DNS 由本地代理提供 fake-ip（`maven.neoforged.net` → `198.18.0.98`）。02:0x 该站 TLS 被对端切断（直连与 `-x http://127.0.0.1:7897` 均 `curl: (35) TLS connect error: error:0A000126:SSL routines::unexpected eof while reading`），26.1.2 首次构建因缺 `neoforge-26.1.2.109-moddev-config.json` 失败；**02:53 代理修复后同一 URL 实测 200**（直连 1.09 s / 代理 0.95 s），`./gradlew :neoforge-26.1.2:classes --no-configuration-cache` 走真仓库 **`BUILD SUCCESSFUL in 46s`**（日志无 mirror 行）⇒ **离线镜像已停用**，脚本改名 `~/.gradle/init.d/10-neoforged-offline-mirror.gradle.disabled`（`~/.mc-mirror`（597 M / 1,231 文件）与 `~/.local/bin/neoforge-mirror-sync.sh` 保留作 fallback；TLS 再次被切断时把 `.disabled` 改名回去即重新启用）。**备用机制**（离线镜像启用时）：只对 `:neoforge-26.1.2` 把 neoforged 仓库 URL 改写成 `file:///home/kitsune/.mc-mirror`，生效日志 `[neoforged-mirror] :neoforge-26.1.2 repo 'NeoForged Releases' -> file:///home/kitsune/.mc-mirror`；缺失的 `-moddev-config.json` 可从 `neoforge-<v>-userdev.jar` 根目录 `config.json` 生成（21.1.235 验证两者 sha256 同为 `4f7c8bdb90e1c3402d52eb746a37ec8bdcffb34b71973e2b0dff859456d831a8`）。
  - **26.1.2 构建实测（2026-10-31）**：`./gradlew :neoforge-26.1.2:classes --console=plain --no-configuration-cache` ⇒ `BUILD SUCCESSFUL in 59s`（4 actionable tasks executed；MDG 复用已落盘的 26.1.2 patched artifacts）。该线**必须**加 `--no-configuration-cache`：`createMinecraftArtifacts` 的 `DefaultConfigurableFileCollection` 在配置缓存序列化阶段报 `error writing value of type …`，与镜像无关。
  - **26.1.2 实机（2026-10-31 02:49，P1 线）**：`bash scripts/test/linux/launch.sh --version 26.1.2 --timeout 900` ⇒ `LAUNCH: READY`（`game_pid=110356`、`wrapper_pid=109625`、log `scripts/test/linux/logs/26.1.2-20261031-024957.log`）；`xwininfo` = `0x2200007 "Minecraft NeoForge* 26.1.2" 1920x1080+320+180` ⇒ **26.1.2 同样走 XWayland**（1.21.1 / 1.20.1 / 26.1.2 三条线的真实窗口都是 X11 窗口；`[[PENDING:wayland-262]]` 据此关闭）；`run/26.1.2/logs/latest.log` 记 `Game took 15.236 seconds to start`、`NeoForge mod loading, version 26.1.2.109, for MC 26.1.2`；截图 `/tmp/mc-2612-title.png`（1,584,077 B）。收停 `bash scripts/test/linux/stop.sh --version 26.1.2` ⇒ `LINUX_STOP: OK`。
    **已知遗留（P1 既有问题，非启动/网络）**：① 客户端停在 `KubeJSErrorScreen`，`run/26.1.2/logs/latest.log:144` 原文 `[KubeJS Client/]: astral_wallet_vision.js#38: dev.latvian.mods.rhino.EvaluatorException: Failed to load Java class 'com.merlinkitsune.starenginelib.economy.StarCoinWalletState': Class could not be found!`；同日志 `:43` 显示 `StarEngine 1.1.0+neoforge_26.1.2 (starengine)` 已加载、`:76` classpath 含 `~/.m2/.../starengine-neoforge-26.1.2-1.1.0.jar`、jar 内确有该类（`unzip -l | grep -c StarCoinWalletState` = 1）⇒ KubeJS Rhino 类加载器看不到 jarJar/嵌套 jar 中的类，待排查（P1）；② `run/26.1.2/mods/` 残留旧 `astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`，FML 选新版本未阻断，建议清理。
- **构建实测（2026-10-31，1.21.1 线，hgfs 工作树，daemon=21）**：`./gradlew :neoforge-1.21.1:classes --console=plain` ⇒ `BUILD SUCCESSFUL in 2m 44s`；`find neoforge-1.21.1/build/classes/java/main -name '*.class' | wc -l` = **534**；编译期仅 1 条 `-Xlint:deprecation` 提示（源码既有弃用 API，与主机无关，不算失败）。
- **`build/moddev/artifacts` 缓存口径（重要）**：主仓该目录里 Windows 时代已生成的 `neoforge-21.1.235-*` jar 被**复用**，所以本次**没有**重跑 decompile / patch；换机器、`clean` 或清缓存后会重跑 ModDevGradle（starengine 侧实测 decompile+patch 耗时 **12m23s / 12m46s**，本仓首次全量按此量级预估，不要按 2m44s 预期）。
- 换线构建耗时与失败模式以**构建台账**为准（每次记录线 / 命令 / 耗时 / 结果），单次耗时不当 SLA；慢的主因是 hgfs I/O（见 §3），临时产物优先放 `/tmp`。

### 6. 前置库 starengine（1.1.0，必须先进 mavenLocal）

- 四线 `gradle.properties` 实读：`starengine_group=com.merlinkitsune.starengine`、`starengine_version=1.1.0`（fabric 另有 `starengine_version_range=>=1.1.0`）；四线 `build.gradle` 均含 `mavenLocal()`（neo `:30` / forge `:29` / neo26 `:39` / fabric `:47`）。
- 正确顺序：先在 `/mnt/hgfs/MCProject/starengine`（独立仓，自带 gradlew 与四平台子项目）发布到**本机** mavenLocal，再构建本仓。缺哪条线的制品，哪条线就解析失败（`Could not resolve com.merlinkitsune.starengine:...`）。
- 发布命令（实测可用；源仓 `/mnt/hgfs/MCProject/starengine`，daemon JVM 必须 21）：
  ```bash
  cd /mnt/hgfs/MCProject/starengine
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :<平台子项目>:publishToMavenLocal
  ```
- 2026-10-31 实读本机 mavenLocal（`~/.m2/repository/com/merlinkitsune/starengine/<artifactId>/1.1.0/`）：**四件已全部发布** —— `starengine-neoforge-1.21.1:1.1.0`（jar **98,734 B**，pom 785 B / module 1,984 B）、`starengine-forge-1.20.1:1.1.0`（jar **103,605 B**）、`starengine-fabric-1.20.1:1.1.0`（jar **112,634 B**）、`starengine-neoforge-26.1.2:1.1.0`（jar **98,970 B**，含 module；26.1.2 首跑 `createMinecraftArtifacts` 曾报 `Accessing unreadable inputs or outputs is not supported` + `java.io.IOException: 错误的文件描述符`，MDG 产物已落盘后原命令重试 24s 成功）。四条线均可解析；若解析失败属环境问题（BLOCKED），不是产品缺陷。
- **Windows 的 mavenLocal 与本机不是同一份**：Windows 侧已发布不代表 Linux 侧可用；同样 Linux 侧发布不影响 Windows。

### 7. git（真实工作树 + autocrlf）

- 分支 `multi-main`，HEAD 快照 `7c44cfbf`。
- 仓库级 `core.autocrlf=true`（`core.eol` 未设）；`.gitattributes` 只对 `src/generated/**` 的 json/cache 与 `gradlew`、`*.sh`、`*.py`、`*.conf` 强制 LF，**`.md` 不强制**。
  - **坑**：index 里是 LF、hgfs 工作树里是 CRLF ⇒ 若不配 `autocrlf` 会把整仓报成改动（交接口径：曾出现 **3,334 个文件假改动**）。改动前先 `git -C <工作树> config --get core.autocrlf`，必须回 `true`。
  - **不要整份重写既有 `.md`**（会把 CRLF 全变 LF 造成假 diff）；新建文件用 LF 并如实报告编码。
- `git status` 里 **4 个用户未跟踪文件**（实读，**不得 add/commit/删除**）：`models/astral_anvil.bbmodel`、`scripts/test/cases/TMP-ATTRLESS-HYDRA-1.21.1.json`、`scripts/test/resources/kubejs/1.21.1/server_scripts/astral_batch3_probe.js`、`zz_screen_probe.py`。
- **git hooks 在 Linux 上是空转**：`.git/hooks/{post-commit,post-checkout,post-merge,post-rewrite}` 是三段探测版（PATH `pwsh` → PATH `pwsh.exe` → `/mnt/c/Program Files/PowerShell/7/pwsh.exe`），本机 **PATH 内无 pwsh、`/mnt/c` 也不存在**（pwsh 7.6.9 只装在 `~/.local/powershell`、未入 PATH）⇒ 一律静默 `exit 0`，松散引用自愈脚本**不会执行**。遇 `HEAD -> unknown revision` 先查 `git reflog`，不要依赖 hook。（lead 2026-10-31 裁决：**不安装、不启用** Linux 侧 git hook；提交前**手动**跑 `python3 -B docs/agent/verify-split.py`（四项 `[OK]`）与 `python3 tools/verify_sign_display_names.py`（`GATE: PASS`）。）

### 8. 测试收尾必须关闭游戏进程（2026-10-18 裁决的 Linux 版）

- 任何轮次（含中断/失败/超时）结束，**必须**把本轮起的 Minecraft 客户端进程收干净：`kill -TERM <PID>`（超过 10s 仍在 ⇒ `kill -KILL <PID>`），随后 `ps -eo pid,comm,args | grep -i '[j]ava'` 与 `pgrep -af 'net.minecraft|fabric|forge|neoforge'` 双核对，报告里贴**原文**与计数。
- **禁止无人观察驻留**；唯一例外是失败取证：沿用 `on_fail=keep_game_running` 语义，落盘标记 **沿用 `.mt_keep_alive-<版本>` 同名**（lead 2026-10-31 裁决；Linux 台由 `scripts/test/linux/` 实现，标记语义与 Windows 一致）；取证完立刻收停。
- **只杀自己起的 PID/进程组**：禁 `pkill -f java`、`killall java`（会误杀用户其它 Minecraft / IDE / Gradle daemon）。等价于 Windows 版「绝不 taskkill 其它 java 进程」。
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

- 通道：`subagent` / `subagent_fork` / `spawn_teammate` + `team_task_*`；复核走 `dsh_code_review({ input })`。
- 纪律：一次一件（一个可独立验收的改动面 + **一个**编译目标）；brief 宁可多写 3 行判据；同一任务最多重试 **1 次**；验收只看产物。
- 团队并行时 `write_scopes` 必须互不重叠；`send_message` 改需求，`interrupt_agent` 止血。

### C. 交接文档四层事实源

1. `docs/handover-1.5.0-main.md` —— 滚动正本（每轮更新 §4/§5）。
2. `docs/handover-new-session-context.md` —— 新会话寒暄提示词。
3. `docs/agent/AGENTS-full.md` 末尾「待办交接各批」—— **只追加**，不改旧条。
4. `docs/handover-<版本>-<主题>.md` —— 单批专题。
- ⛔ `docs/AGENT-HANDOFF.md` **不是现状**，只作历史参考。
- **接手顺序**：`git status --short -uall` + `git log --oneline -1` → 四线 `gradle.properties` 的 `mod_version` → handover §0/§5 → 按 §7 取路径。
- **交班 6 义务**（其中一项必做）：`python3 -B docs/agent/verify-split.py` 四项全 `[OK]`。
- 事实源快照会落后：2026-10-31 实读四线 `mod_version` 已是 `1.5.0-rc.7+<后缀>`（`neoforge_1.21.1` / `forge_1.20.1` / `neoforge_26.1.2` / `fabric_1.20.1`），比 `PROJECT-REFERENCE.md` 与根 `AGENTS.md` 的 `pre.6` 快照新 —— 这类数字**一律实读覆盖**。

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
| 工作树 `F:\MCProject\astral_dice_multiloader` | `/mnt/hgfs/MCProject/astral_dice_multiloader`（同一份文件的 hgfs 视图） |
| 前置库 `F:\MCProject\starengine` | `/mnt/hgfs/MCProject/starengine` |
| 「相对路径陷阱」 | **不变且更严**：hgfs 上多工作树共存，任何命令都必须绝对路径 |
| 「分支归属纪律（禁止镜像/回填）」 | **不变**（宿主无关） |
| 发布投放（整合包 `versions/<实例>/mods`） | Linux 宿主现在**可**投放：根 = `/mnt/hgfs/.minecraft`（hgfs 共享盘，`MC_HOME` 可覆盖，根 `build.gradle` 统一解析）；Windows 宿主同一批文件在 `D:\.minecraft`。**「默认不部署」红线不变**（仅用户明确下达时跑 `pushToGame`/`build`） |
| git 代理 `http://127.0.0.1:7897` | **不变**（本机 git 已配 `http.proxy`/`https.proxy`）；连通性以 `git ls-remote` 实测为准，不通先查代理客户端 |
| 默认不 commit / push / 打 tag | **不变**（最严格红线） |
| `models/` `images/` `sound/` 与 `run/` 内他人产物 | **不变**：出现不属于自己的改动 ⇒ 停手报告，不 add/commit/stash/checkout/删除 |

- 优先级与基线（P0 = `neoforge-1.21.1` + `forge-1.20.1`；P1 = `neoforge-26.1.2` + `fabric-1.20.1`，`1.5.0` 正式版前不验证、不移植）、包根 `com.merlinkitsune.astral_dice`、fabric 内嵌库前置 `python3 tools/loom_embedded_jars.py --apply`（KI-F23）—— 全部**与宿主无关，原文有效**。

## 二次验证规范（与宿主无关，命令 Linux 化）

- 触发面（5 类）：改产品源码、改规则/守门脚本、改构建配置、改数据/配方、任何「已修好/已通过」的结论。
- 复核一律 `dsh_code_review({ input })`：**不是** subagent 派活；`input` 为空则走原生问题选择范围；`input` 非空时整段作为自定义要求。**禁喂**主会话结论/倾向，让它自己 `git diff` 取证。
- 报告验收 5 条：① 结论有产物/行号级证据；② 每条判据可复现（给命令）；③ 区分 FAIL / ERROR / TIMEOUT / BLOCKED；④ 未做项与风险如实列出；⑤ 不给「大概/应该」。
- 两层守门不得互替：`verify-split.py` 管规则文件结构，`dsh_code_review` 管代码/文档实质；一层通过不能替代另一层。

## 子代理与测试环境硬规则（2026-10-08 裁决，8 条 Linux 化）

1. **一 subagent 一任务，用完即弃**；不串任务、不复用上下文。
2. **测试/演示一律用非亡灵生物**（`minecraft:pillager` / `minecraft:cow`），不要用亡灵类。
3. **流程结束必关游戏进程**（见〈测试收尾〉），并核对无残留 java；收尾报告必须贴实际命令与输出原文。
4. **复核一律 `dsh_code_review`**，不派 subagent 做复核。
5. **实机必须由用户提示词明确下达**；未下达时禁止自行开客户端 / 连拍 / 探针。
6. **改动后旧实机证据作废**：先停旧实例，再谈新证据。
7. **开发流程**：改 → 编译 → 静态守门 → 实机（获授权后）→ 复核（分级 a–e）→ 收尾关进程。
8. **派子代理一律屏蔽人格注入**（免人设句原文带，见〈代理协作契约 B〉）。

## 命令执行规范（bash，替代 Windows 版 PowerShell 规范）

1. **一律 bash**（`SHELL=/bin/bash`，实读）；脚本首行 `#!/usr/bin/env bash` + `set -euo pipefail`。**禁止**再写 PowerShell / `.ps1` 结论性命令（系统 PATH 无 pwsh；`~/.local/powershell/pwsh` 仅作只读排障，不用于产出结论）。
2. **禁止 `bash -c` 塞多行/嵌套引号**：多行逻辑落 `temp/tNN/<name>.sh`，显式 `bash /绝对路径/...sh` 执行，`tNN` = 本轮批次号（不入库）。
3. **长命令给超时**：`timeout --signal=TERM 300s <cmd>`；超时是独立结论（TIMEOUT ≠ FAIL ≠ ERROR）。长流程另落日志。
4. **后台任务**：`setsid bash /绝对路径/x.sh > temp/tNN/x.log 2>&1 &`，PID 记 `temp/tNN/x.pid`；禁止无日志裸跑；收尾按进程组杀 `kill -TERM -- -<PGID>`。
5. **日志纪律**：新日志落 `temp/tNN/` 或 `run/<线>/logs/` 的新文件；**禁止**覆盖 `run/<线>/logs/latest.log`（游戏在写）与既有 `run/datagen*.out.log`。
6. **不跑全仓 hgfs 扫描**：搜索用 grep 工具（ripgrep），读文件用 read/`sed -n`；全仓 `find`/`grep -r` 在 hgfs 上很慢且会把 `build/` 拖进来。
7. **权限与编码自检**：新建/改写文本后跑 `file <path>` 与 `wc -l`；`.sh` / `.py` / `.conf` 必须 LF（`.gitattributes` 已强制），`.md` 保持仓库既有形态（既有多为 CRLF，新建建议 LF 并报告）。
8. **不提权**：不自行 `sudo`；需要 root 权限时停下报用户（凭据由用户提供，**密码严禁**写入任何文档/日志/提交）。
9. **不读大文件全文**：`AGENTS-full.md` 等用区间 `sed -n`；> 200 行的目标文件先看标题/结构。

## Git 代理与操作规范
- 代理：`http://127.0.0.1:7897`（本机 git 已配 `http.proxy` / `https.proxy`）；失败先查代理客户端，再查网络。
  - 连通性实测（2026-10-31）：`curl -x http://127.0.0.1:7897 -o /dev/null -w "%{http_code}" https://github.com` ⇒ `200`；直连同为 `200`。**注意**：7897 代理**不能**绕过 Maven Central 对本机出口 IP 的 403（依赖问题见 §5〈依赖镜像〉，走 aliyun 镜像解决）。代理与直连都通，git 走代理仍是默认口径。
- 一律 `git -C /mnt/hgfs/MCProject/astral_dice_multiloader <子命令>`；**默认不 commit / push / tag**，远程操作须用户明确要求。
- 提交前 `git status --short -uall` 必须只剩用户未跟踪素材（4 个文件，见〈git〉）；有他人改动 ⇒ 停手报告。
- `git add` 只加本轮明确产物；**禁止** `git add -A`、`git stash`、`git checkout --` 清别人的改动。

## 桌面控制与实机通道（Wayland）

- 桌面驱动 = DSH MCP `computer-use`（`@mirsella/computer-use-mcp@2.1.3`，stdio；KDE Wayland 下经 `xdg-desktop-portal` RemoteDesktop(EIS) 注入）：截图、点击、键盘、窗口激活；**不用于**游戏内镜头与攻击（沿用 2026-10-18 裁决）。
- 工具（2026-10-31 实读已上线）：`list_desktop` / `launch_application` / `activate_window` / `observe` / `act` / `wait_for`。
  - **`list_desktop` 看不到 Minecraft 窗口**（GLFW 不暴露 AT-SPI）：只会列出 DSH Chromium / 桌面 / 系统监视器等。驱动 MC 只能 `observe` 传**桌面 target** 截整屏（PNG 1280x720），再用**桌面级坐标**注入；不要指望按窗口 id 定位。
  - 动作前先 `observe` 拿 `observation_id` + `frame_id`；**target / observation 是 session 级**，必须同一进程内 `list_desktop → observe → act` 连续完成；跨会话会失效（stale target 需重新发现）。
  - 首次截图触发 Portal 授权框（需人在桌面确认一次）；授权后 restore token 落 `~/.local/state/computer-use-mcp/portal-restore-token`，后续免弹窗。
  - 截图实测 PNG `1280x720`（逻辑屏 `1920x1080`、物理 `2560x1440`，PipeWire 2× 降采样）⇒ 坐标以返回 PNG 为准，**不要**用物理分辨率或 AT-SPI 边界换算。
  - AT-SPI 本会话 disabled，但 `list_desktop` 可用；`/dev/input` 只读权限缺失不影响 EIS 注入。
- 键鼠注入已实证（`act` pointer move 到 `(300,680)` 后截图可见光标，随后已恢复原位）；**游戏窗口内**的注入与镜头 / 攻击仍不属自动化范围（红线）。
- **GUI 级驱动（2026-10-31 实测可用）**：`xdotool` 已 apt 安装（`1:3.20160805.1-5.1build1`，`/usr/bin/xdotool`），走 **XTEST 真实事件**可驱动游戏窗口：
  - 先激活：`xdotool windowactivate --sync $(xdotool search --name Minecraft | head -1)`（或 `xdotool key alt+Tab`）；**不激活会点到前面重叠的窗口**（实测踩过）。
  - 再点击：`xdotool mousemove X Y click 1`×n。绝对坐标 = 窗口位置 `(332,203)` + GUI 坐标 × `guiScale`（=2）；GUI 坐标可用 AutoJS client 侧 `mc.screen.children().get(i).getX()/getY()/getWidth()/getHeight()` 反算。
  - 文字兜底截图：`spectacle -b -n -a -o /tmp/mc-world.png`（`/usr/bin/spectacle` 实读已装）。
  - 仍缺的工具：`ydotool` / `wtype` / `grim` / `kdotool` **未安装**，不要假设存在。
- **驱动优先级（2026-10-31 裁决）**：游戏内输入/状态读写**首选 AutoJS `eval_js`**（脚本上下文直接操作，绕过窗口焦点问题）；GUI 级（建世界、开关菜单）用 `xdotool` 或 computer-use 桌面坐标；computer-use 只作桌面兜底。
- `mt_inject.ps1`（SendInput / PostMessage）是 **Windows-only**，Linux 无等价实现；Linux 侧键鼠 / 命令注入只走 `computer-use` MCP（桌面输入）+ AutoJS（游戏内脚本上下文，见下节）。
- 截图证据一律落 `temp/tNN/`（不入库）；不得用固定外部目录。

## AutoJS 运行时通道（Linux 侧口径）

> 详细契约见 `docs/features/autojs-test-harness-spec.md`；操作口径见 `docs/features/autojs-testing-flow.md`。本节只定规则。

- 端点：Streamable HTTP `POST http://127.0.0.1:25585/mcp`，默认 bind `127.0.0.1`；探活 `GET /health` 期望 `200`：

  ```bash
  curl -fsS -m 3 http://127.0.0.1:25585/health
  ```

- 配置：`run/<线>/config/autojs.json`（1.21.1 实读存在，`enabled:true`、`bindAddress:127.0.0.1`、`port:25585`、`unrestricted:true`、`token:""`、`evalTimeoutMs:5000`）；端口覆盖序 **`-Dautojs.port=` > `AUTOJS_PORT` > 配置文件**（`0` = 系统分配）。
- **同一时刻只能一条线占用 25585**（否则 `BindException` + 注入判定失败）；切线路前先停旧客户端。
- MCP 注册：serverName `autojs`，`@deepseek-ai/dsh-mcp-client`，`transport: streamable-http`，`url: http://127.0.0.1:25585/mcp`，`headers.Accept: application/json, text/event-stream`，`failOnStartupError: false`；同一 serverName 只能一条；改完重载 DSH 会话。
- 工具四件：`status` / `eval_js { code, side: auto|server|client, timeout_ms }` / `kubejs_log { side, kind, limit }` / `reload_scripts { side, timeout_ms }`；返回 `{ ok, side, elapsedMs, type, value, diagnostics }`，**必看 `diagnostics`**。
- 铁律：`side` 必须明确（读权威数据用 `server`，读 HUD/FPS/输入用 `client`，`auto` 仅在两侧等价时用）；运行时不能新建注册表条目；只求值最后一个表达式；Rhino 不是 JS 反射；返回值必须 `JSON.stringify`。
- 错误归一：`isError` / `ok=false` 记 **BLOCKED**（环境问题），**不得**判成功能缺陷；端点没起同此。
- **2026-10-31 两端实测（1.21.1，`linux_smoke` 世界已开）**：`status` → client `running, world loaded`；server `running with 1 player(s) online`。
  - client 侧：**KubeJS 里没有裸 `Minecraft` 全局**，必须 `Java.loadClass('net.minecraft.client.Minecraft').getInstance()`；实测 `mc.getWindow().getWidth()/getHeight()` = `1280x662`、`getGuiScale()` = `2`、`mc.getFps()` = `59`。注册表校验用 `BuiltInRegistries.ITEM/BLOCK`：取到 `astral_dice:event_ticket`、`Block{astral_dice:card_shop}`，ITEM 总数 **1939**。
  - server 侧：`AutoJS.where()` = `in world minecraft:overworld at 126,68,-39, no container open`；全局键有 `AutoJS/Item/Blocks/Client/Java/NBT/…`，但 **KubeJS 1.21 风格没有 `player()` / `server()` 这类旧 helper**，1.20 时代写法不适用。
  - 端点绑定：`status.endpoints` = `{"bindAddress":"127.0.0.1","port":25585,"mcpPath":"/mcp","url":"http://127.0.0.1:25585/mcp"}`，`serverInfo` = `{"name":"autojs","title":"AutoJS","version":"1.0.0"}`，`limits` = `{"evalTimeoutMs":5000,"maxResultChars":100000,"tokenRequired":false,"logEval":true}`。
- 技能：DSH 侧技能 `~/.dsh/skills/autojs/SKILL.md` ⇒ Linux 为 `/home/kitsune/.dsh/skills/autojs/SKILL.md`（2026-10-31 实读：28,134 B，frontmatter `name: autojs`，本会话技能目录已出现 `autojs`；`~/.dsh/skills/` 同刻创建。Windows 文档写的是 `C:\Users\xmace\.dsh\skills\autojs\SKILL.md`）；`mods/` 里的 AutoJS jar 自带同名 `SKILL.md`，升级后须重抽。
- 安全边界：`unrestricted=true` 时**只绑 loopback**，禁 `0.0.0.0`。

## 🧪 测试台现状（Linux）

- Windows 测试台规模（实读）：`scripts/test/mt.ps1` 851 行，入口 `mt.ps1 --phase <preflight|build|env|launch|cases|report|stop>`；全部 PowerShell 合计约 19,384 行。**整台不可移植**，Windows-only 依赖实读位置：
  - `user32` / keybd / mouse P/Invoke：`scripts/test/lib/Mt.Win32.psm1` L349、L945；`scripts/test/mt_inject.ps1` L349
  - `taskkill` / `Get-CimInstance`：`scripts/test/lib/Mt.Proc.psm1` L157、L1052、L299
  - `cmd.exe /c gradlew.bat`：`scripts/test/mt_launch.ps1` L461 等
  - `C:\Program Files\Zulu` / `Adoptium`、`D:\.minecraft\...`：`scripts/test/lib/Mt.Paths.psm1` L50-52
  - GBK 控制台：`scripts/test/lib/Mt.Phase.psm1`
- Linux 下 mt.ps1 只可跑**外壳子集**：参数解析、`--help`、批量闸门、退出码语义、纯文件检查、`lib/Mt.AutoJs.psm1` 的 HTTP 客户端（实读 `Import-Module` 成功、9 个函数、endpoint 读到 `http://127.0.0.1:25585/mcp`）。**不要**用 mt.ps1 跑 launch / build / cases。
- pwsh 7 已在位但**仅作可选只读工具**：`/home/kitsune/.local/powershell/pwsh`（v7.6.9 linux-x64，非 apt，未入 PATH）。批量闸门在 Linux pwsh 实测仍生效：`--phase report --version 1.21.1`、`--phase cases --version`（无 `--case`）、无 `--version` 三条均 `exit 2` + 首行 `MT_AUTO_DISABLED:`。
- **Linux 测试入口 = `scripts/test/linux/{env,launch,stop}.sh`**（bash，零 pwsh 依赖；2026-10-31 实读，task-4 维护；同目录 `README.md`=13,465 B 为完整操作说明，脚本头注释与之一致）：
  - `env.sh [--version <线>] [--quiet]`：**只读**环境检查（仓库/挂载、各线 JDK 与 `run/`、`mods/` 与 autojs·rhino jar、AutoJS `/health`、客户端残留、锁、Gradle 守护）；机器行 `CHECK: <key>=OK|WARN|FAIL` + 收尾 `ENV_RESULT: OK|WARN|FAIL`；退出码 `0` 无 FAIL / `1` 有 FAIL / `2` 用法错误。
  - `launch.sh --version <线> [--timeout 600] [--quickplay <世界名>] [--kill-on-timeout] [--log-dir <dir>] [--dry-run]`：**优先复用 `run/start-<线>.sh`**（存在且可执行），否则回退 `bash ./gradlew :<线>:runClient --console=plain`；`setsid` 独立进程组启动（脚本退出不杀游戏），日志落 `scripts/test/linux/logs/<线>-<ts>.log`（默认，绝不写 `run/` 既有日志），PID 落 `run/<线>/linux-run.pid`。
  - 就绪判据（**2026-10-31 修正，以 AutoJS 为准**）：`GET http://127.0.0.1:25585/health` = 200 且 `status` 里 client `registered:true` / `available:true`。日志 marker（`Total time to load game and open world was`、`logged in with entity id` + `Loaded <N> advancements`）**只能**作为「已开世界」的附加证据，不再是就绪判据——dev 客户端默认停在标题画面，错用会让每次启动都误判 TIMEOUT（`scripts/test/linux/launch.sh` 已按此判据实现 `aj_probe()`：health → `initialize` → `tools/call status`，2026-10-31 定稿）。
  - launch.sh 退出码：`0` 就绪 / `1` 启动失败或进程提前退出 / `2` AutoJS 端点未就绪（ERROR，逃生阀 `MT_ALLOW_NO_AUTOJS=1`）/ `11` BLOCKED（同线已有客户端、缺 JDK、`run/` 目录缺失）/ `12` TIMEOUT / `130` 用户中断。
  - `stop.sh --version <线> | --all [--force] [--purge-saves] [--stop-daemon] [--dry-run]`：标准收停。先按 `linux-run.pid` 收进程组（`TERM` → 15s → `KILL`，`--force` 宽限 3s），再 `/proc` 扫描补漏，复核无残留才输出 `LINUX_STOP: OK`；有残留输出 `LINUX_STOP: RESIDUAL` 并 `exit 1`。`--purge-saves` 只删 `run/<线>/saves/*` 与 `run/<线>/*/level.dat` 世界目录；`--stop-daemon` 会停全机 Gradle 守护，默认不做。
  - **收停实测（2026-10-31）**：`bash scripts/test/linux/stop.sh --version 1.21.1 --purge-saves` ⇒ 输出 `LINUX_STOP: OK`；随后 `pgrep -af net.minecraft` 为空、`pgrep -af GradleDaemon` 只剩 daemon 本身。测试世界 `linux_smoke` 由 `--purge-saves` 清掉（`run/1.21.1/saves/linux_smoke`）。
  - **未复刻的 Windows 能力**（脚本头注释明确）：进程防撞锁、测试前世界清场、OP / 禁 AI 闸门。这些前置在 Linux 侧仍需人工确认，或按 Windows 口径理解；不得当作「已验证」。
- 启动脚本 `run/start-{1.20.1,1.21.1,26.1.2}.sh`（2026-10-31 02:39 定版实读：**191 / 191 / 210 行，均 777 可执行**；含 devlaunch 进程匹配修正；**fabric-1.20.1 暂无 start 脚本**）：
  - 用法 `./run/start-1.21.1.sh [<额外 gradlew 参数…>]`（如 `-Pquickplay=testworld`）；脚本自行 `cd` 到仓库根，`set -euo pipefail`。
  - 前台跑法：`bash run/start-1.21.1.sh`；后台跑法（task-1 实测）：`setsid nohup bash run/start-1.21.1.sh > /tmp/launch-1211.out 2>&1 &`。Wayland 会话下环境须就位：`DISPLAY=:0 WAYLAND_DISPLAY=wayland-0 XDG_RUNTIME_DIR=/run/user/1000`。
  - **`run/` 整体在 `.gitignore:61`（`run/`）里** ⇒ `run/start-*.sh` 与 `start-*.bat` 同待遇，是**未跟踪/被忽略**文件；确实要入库才 `git add -f run/start-*.sh`，默认**不 add**（避免把本机启动参数带进仓库）。
  - **JDK：`DEFAULT_JDK` 三脚本统一 `/usr/lib/jvm/java-21-openjdk-amd64`**（daemon JVM 必须 ≥21；`gradle-launcher` 2026-10-31 01:46 修正，**旧版按线 17/25 是错误口径，不要沿用**）；`java.toolchain` 自动选 17/21/25 作为编译目标。`ASTRA_JAVA_HOME` **只覆盖 daemon JVM**，不改变 toolchain。
  - **`DISPLAY` 为空直接 `exit 1`**：MC 1.20.1 / 1.21.1 的 GLFW 只有 X11 后端，Wayland 会话必须靠 XWayland（本机 `DISPLAY=:0`）。
  - 启动前若已有 `net.minecraft.client.main.Main` 在跑只告警、不阻断；输出 `tee` 到 `run/<线>-launch-<ts>.log`；游戏自身日志 `run/<线>/logs/latest.log`、崩溃报告 `run/<线>/crash-reports/`；**不覆盖** `run/` 下其它既有日志。
  - Ctrl-C / SIGTERM：trap 后**只收本实例**——按 `/proc/<pid>/cwd` 与该进程 `cmdline` 里的 `--gameDir` 精确匹配（`readlink -f` 后比对本线 `run/<线>` 真实路径），**绝不**全机 `pkill -f java`；先 `SIGTERM` → 等 20s → `SIGKILL`，以 `exit 130` 退出。`gradlew` 失败原样透传其退出码；正常退出后若仍有残留**本实例**进程则 `exit 3`。该精确匹配是 2026-10-31 01:49 修正版（旧版裸 `pkill -f` 有全机误伤风险，已废弃）。
  - 26.1.2 特有前置：检查 `run/26.1.2/mods` 下第三方 runtime jar 是否在位（缺失只告警，提示客户端可能缺前置/配方）。
  - **首次实机启动实测（2026-10-31，1.21.1 线，task-1 执行 / Lead 验收）**：脚本 02:11:39 起 → 游戏 JVM 02:13:16（≈97s，含首次 **3888 个 assets** 下载；此前 `:classes` 已 2m44s 预编译）→ `curl -fsS -m 3 http://127.0.0.1:25585/health` 返回 `ok` / HTTP **200**（02:13:4x）。旁证原文：`ss -ltnp | grep 25585` = `LISTEN 0 50 [::ffff:127.0.0.1]:25585 … users:(("java",pid=78437,fd=304))`；`xwininfo -root -tree | grep -i minecraft` = `0x220000d "Minecraft NeoForge* 1.21.1": ("Minecraft* 1.21.1" …) 1280x662+332+203`；`run/1.21.1/logs/latest.log` 内 `[02:13:34] [ModernFix/]: Game took 18.113 seconds to start`。
  - **fabric-1.20.1 实机未通过（2026-10-31 02:55 实测，P1 线既有问题）**：`bash scripts/test/linux/launch.sh --version fabric-1.20.1` 因无 start 脚本而回退 `./gradlew :fabric-1.20.1:runClient` ⇒ `BUILD FAILED in 2m 44s`，`Process ... finished with non-zero exit value 1`；日志 `scripts/test/linux/logs/fabric-1.20.1-20261031-025520.log:139` 原文 `[main/ERROR] (Sodium-EarlyDriverScanner) The game failed to start because the currently active LWJGL version is not compatible. Installed version: 3.3.2-snapshot / Required version: 3.3.1`（Loom 1.14.10 的 dev runtime 提供 LWJGL 3.3.2-snapshot，Sodium 0.5.x 只接受 3.3.1）⇒ **与 Linux/Wayland 无关**，需在 fabric 构建侧 pin LWJGL 3.3.1 或更换 Sodium 版本（未做，P1）。`run/fabric-1.20.1/mods/` 实读仅 `AutoJS-fabric-1.20.1-1.0.0.jar` 与 `astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar`。
  - **1.20.1（legacyforge dev run）加载的是 `run/1.20.1/mods/astral_dice-1.5.0-rc.7+forge_1.20.1.jar`，不是 `build/resources/main`**：dev 启动只认 `pushToDevRun` 推过去的未重混淆 jar；改了 `src/main/resources` 后**必须** `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :forge-1.20.1:pushToDevRun`（约 60s）再启动，否则验证的仍是旧 jar。1.21.1（MDG NeoForge）无此问题，直接用 `build/resources/main`。
  - **1.20.1 首次实机实测（2026-10-31，Lead 验收）**：`bash scripts/test/linux/launch.sh --version 1.20.1` → exit 0、`LAUNCH: READY — 1.20.1 标题画面就绪`、`game_pid=103088`、log `scripts/test/linux/logs/1.20.1-20261031-023905.log`；`xwininfo` = `0x220000d "Minecraft* Forge 1.20.1" 1920x1080+320+180`；`latest.log` `[02:39:28] [ModernFix/]: Game took 20.971 seconds to start`；AutoJS client eval `{"w":1920,"h":1080,"guiScale":2.0,"fps":59,"screen":"…TitleScreen@53301a7","item":"event_ticket","itemCount":1590}`；截图 `/tmp/mc-1201-title.png`（`Forge 47.4.10 / Minecraft 1.20.1 / 25 个 Mod 已加载`）。收停 `stop.sh --version 1.20.1` ⇒ `LINUX_STOP: OK`、health=000、无残留。
  - **Jade dev-only 断言坑（1.20.1 首启崩溃根因，非 Linux 问题）**：Jade **11.13.3** 在 **dev 环境**（`CommonProxy.isDevEnv()`）标题屏 `onGui` 里遍历 `PluginConfig`，对每个已注册 component 检查 `config.jade.plugin_<ns>.<path>` 的 `I18n.exists`，缺任一就 `throw new AssertionError("Missing config translation: …")`（字节码位置 `snownee.jade.JadeClient.onGui`）。本模组两个 client provider（`compat/jade/ShopCooldownClientProvider.java:37`、`EventDrawerCostProvider.java:23`）注册了 `astral_dice:shop_cooldown` / `astral_dice:event_drawer_cost`，1.20.1 三语 lang 原缺这两条 ⇒ 标题屏必崩（crash `Description: Rendering overlay`，报告留在 `run/1.20.1/crash-reports/` 作证据）。已在 `forge-1.20.1/src/main/resources/assets/astral_dice/lang/{en_us,zh_cn,ja_jp}.json` 各补两条（`config.jade.plugin_astral_dice.shop_cooldown` / `…event_drawer_cost`）并重建 dev jar 解决。1.21.1 线是 Jade 15.10.6，无该断言。
  - **就绪判据修正（覆盖旧口径）**：dev 客户端默认停在**标题画面**，不会出现 Windows 时代的 `Total time to load game and open world was`（那条要进世界才有）。Linux 就绪判据 = **AutoJS `GET /health` = 200 且 `status` 里 client `registered:true` / `available:true`**；日志 marker（`Game took …`、`Total time to load game…`、`logged in with entity id`）只能作为「已开世界」的**附加**证据，不能当就绪判据——错用会让每次启动都误判 TIMEOUT。（`scripts/test/linux/launch.sh` 已按此判据实现 `aj_probe()`：health → `initialize` → `tools/call status`；1.21.1 / 1.20.1 均已于 2026-10-31 实机验证。以本条为准。）
  - 游戏内核对（2026-10-31 实读）：窗口标题栏 `Minecraft NeoForge* 1.21.1`；F3 类信息 `Minecraft 1.21.1 / ModernFix 5.27.27+mc1.21.1 / NeoForge 21.1.235（29 个模组）`；HUD 左下可见 `AP_NOAI mobs=0 noai=0 radius=128 …` 覆盖层。
- `launch.sh` 优先复用上述脚本；`run/start-fabric-1.20.1.sh` 不存在时走 `./gradlew :fabric-1.20.1:runClient` 回退路径。`launch.sh` 注入的 `JAVA_HOME` 是 **daemon JVM，必须是 21**（不是按线 17/25）；`gradle_daemon_jvm` 由 `env.sh` 检查，`toolchain` 17/21/25 由 Gradle 自动发现（2026-10-31 定稿）。Linux 宿主**不得**自行发明测试流程，也不得把「跑不通」写成产品缺陷。
- 规则层继续有效（与实现无关）：禁批量编排、单条手动、`FAIL` / `ERROR` / `TIMEOUT` / `BLOCKED` 语义不混同、测试顺序 1.21.1 先行、1.20.1 门控（未过记 `GATED`）、断言以服务端权威日志 `AP_<tag>_<KEY>` 为准、报告不得放宽口径。
- 另一条独立 RCON 台：`scripts/test/fabric/ft.ps1`（同样 PowerShell）；Linux 侧不承诺可用。
- 静态守门：纯 python 守门**可跑**——`python3 docs/agent/verify-split.py`、`python3 tools/audit_patchouli_keys.py`、`python3 tools/verify_sign_display_names.py`、`python3 -B tools/loom_embedded_jars.py`、`python3 scripts/test/lib/mt_case.py validate`。`scripts/` 与 `tools/` 下 **52 个 `.ps1`**（实读：`scripts/` 49 + `tools/` 3）是 **Windows 历史工具**，Linux 侧**不调用**；Linux 可用的守门只有 `python3` 脚本（以实读目录为准：`docs/agent/verify-split.py`、`tools/verify_sign_display_names.py`、`tools/audit_patchouli_keys.py`、`tools/loom_embedded_jars.py`、`scripts/test/lib/mt_case.py`）；其余门禁在 Linux 下**手段缺失**，不得假装通过，也不得把「没跑」写成「已通过」。
- 实机一律等用户明确下达；未授权时只做编译 + 静态守门。

## 互斥锁与并发

- 锁目录 `/mnt/hgfs/MCProject/.agent-locks/`；DSH 动工作树前建 `dsh.lock`，收尾必删。**锁文件为三行口径**：`PID=` / `TIME=` / `TASK=`（正本 `docs/agent/AGENT-SPLIT.md` L25；`docs/agent/PROJECT-REFERENCE.md` L11 同口径），可附加 `NOTE=` 之类的说明行。2026-10-31 实读 `dsh.lock` = **125 B / 4 行**（`PID=22177` / `TIME=2026-10-31T01:34:00+08:00` / `TASK=linux-adoption-lead` / `NOTE=…`）。
- 动工作树或起 gradle 前：① 看锁是否存在、`kill -0 <pid>` 是否存活；② 看 `build/` 最近 30 分钟有无修改；③ 存在他宿主的活跃锁（`kimi.lock` 等）⇒ 停手等待，**陈旧锁不自行删除**，报用户。
- 同一工作树同一时间**只允许一个 gradle**；团队并行时 `write_scopes` 互不重叠（本仓立牌/文档/构建各有独立范围）。

## 交接与事实源（Linux 侧）

- 四层事实源见〈代理协作契约 C〉；每轮收尾更新 `docs/handover-1.5.0-main.md` §4/§5 + `docs/handover-new-session-context.md`。
- 接手先实读：`git -C <工作树> status --short -uall`、`git log --oneline -1`、四线 `mod_version`、`~/.m2` 是否有 `com.merlinkitsune`（决定能否直接构建）、`.agent-locks/` 状态。
- 报告「已完成」前必须有一次实跑证据（命令 + 原文输出片段），不接受「应该可以」。
- 2026-10-31 交接专文：`docs/handover-2026-10-31-linux-vm-adoption.md` —— VM hgfs 实测代价（写 932 MB/s vs 2.7 GB/s、建 500 小文件 0.39 s vs <0.01 s、`ln -s` 不支持）、三线实机证据、仓库外产物清单，以及**迁移到真实 Linux 的 8 步 checklist**（拷贝含被忽略的 `run/`、工具链、starengine 四线发布、三线构建、测试台自检、DSH MCP 重建、8 条复验、迁移后清理）。
- **相对路径纪律**：交接文档、仓库内脚本、规则文本一律相对仓库根书写（脚本用 `ROOT="$(cd "$(dirname "$0")/.." && pwd)"` 自解析）；宿主绝对路径只允许出现在该交接文档的附录 A（VM 快照，迁移后作废）。
- `docs/handover-new-session-context.md` 已按 Linux 口径重写（新会话三分钟上手：规则认领 → 实读现状 → 基本盘 → 测试台 → 工作树 → 常用命令 → 缺口 → 纪律）。

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
| 工程拓扑 / 四线基线 / 构建耗时台账 | `docs/agent/PROJECT-REFERENCE.md` |
| 委派操作手册（模板/失败模式） | `docs/agent/delegation-runbook.md` |
| 测试流程与规则总览（Windows-only 实现） | `scripts/test/TESTING-RULES-OVERVIEW.md` |
| AutoJS 通道契约 | `docs/features/autojs-test-harness-spec.md` · `docs/features/autojs-testing-flow.md` |
| 规则库自身说明 | `docs/agent/README.md` |
| WSL 口径失效对照（仅历史） | `docs/agent/AGENTS-full.md`〈⛔ WSL 口径失效对照〉（L3505 起） |

- 维护约定：① 每次必读的红线 → 写根 `AGENTS.md` 红线区并同步 `AGENTS-full.md`；② 情境规则 → 追加 `AGENTS-full.md` 并更新 `AGENTS-TOC.md` 与本索引；③ 本文件体量上限 **~30K 字符**（超了拆索引 + 子代理读取），改完跑 `python3 -B docs/agent/verify-split.py`；④ 长驻外部内容走索引 + 子代理读取，不塞进主会话。
