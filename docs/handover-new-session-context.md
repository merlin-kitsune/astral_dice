# 新会话上下文（原生 Linux 真机期）


> **口径提示**：本文件描述**主线**（`multi-main` / 1.5.0-rc.7）。若你工作在 `multi-dev-next` 工作树（`/mnt/f/MCProject/astral_dice_multiloader-next`，2.0 线 / `26.4-SNAPSHOT.5`），版本号与库引脚以 `docs/handover-2026-10-31-dev-next-linux-sync.md` 为准。

> **重写于 2026-10-31（宿主从 VMware 虚拟机迁到真机之后）**：替换旧的「当前是虚拟机 + hgfs + `~/Project` 四副本」口径。
> **路径纪律**：仓库内文件一律写**相对仓库根的路径**（如 `docs/agent/AGENTS-dsh-linux.md`、`run/start-1.21.1.sh`）；宿主绝对路径只在迁移/附录章节登记。

## 0 接手第一步（顺序固定）

1. 读根 `AGENTS.md` 认领宿主规则：本机是**原生 Linux 真机** ⇒ 规则正本 `docs/agent/AGENTS-dsh-linux.md`（361 行）；`docs/agent/AGENTS-dsh.md` 是 **Windows 历史正本**，只作对照，不照抄其 pwsh / `gradlew.bat` / `mt.ps1` 口径。
2. 加载技能 `using-superpowers`（规则正本第 8 行起是硬要求）。
3. 读 `docs/agent/AGENTS-dsh-linux.md` 全文。
4. 读**最新交接三件**：`docs/handover-2026-10-31-native-linux-migration.md`（本批：真机迁移实测 + 路径重映射 + 权限坑）、`docs/handover-1.5.0-main.md` 的最后两条 `### 4.1az` / `### 4.1ba` 与 `## 5 遗留与欠账`。
5. 动手前跑 `bash scripts/test/linux/env.sh` 与 `git status --short -uall` 核对现状。

## 1 实读现状（2026-10-31 18:1x 快照，以命令为准）

| 项 | 值 |
|---|---|
| 分支 / HEAD | `multi-main` / `c31d36f6` |
| 版本 | 四线 `mod_version = 1.5.0-rc.7+<加载器后缀>` |
| Gradle | wrapper **9.2.1**（aliyun 镜像），**daemon JVM 统一 JDK 21**；`~/.gradle` 已由本批重建 |
| starengine | **1.1.0，四线制品已发布进本机 `~/.m2`**（`publishToMavenLocal` 四条均 `BUILD SUCCESSFUL`） |
| 整合包根 | `/mnt/d/.minecraft`（`MC_HOME` 可覆盖；根 `build.gradle` 的 `mcHomePath` 解析：`MC_HOME` > `/mnt/d/.minecraft` > `D:/.minecraft`） |
| 可构建性 | `:neoforge-1.21.1:classes` 32s / `:forge-1.20.1:classes` 30s（热缓存，**不是冷基线**）均 `BUILD SUCCESSFUL` |
| 工作树 | 本批 13 改 + 1 新增，**未 commit / 未 push**（见 §5） |

## 2 环境（原生 Linux 真机，不是虚拟机）

- **Ubuntu 26.04.1 LTS 物理机**（`systemd-detect-virt=none`，DMI `MS-7E59`），内核 `7.0.0-42-generic`；KDE Plasma **Wayland** 会话（`DISPLAY=:0` + XWayland 供 MC 用）。
- **双系统、与 Windows 共用同一块 NTFS 盘**：`/mnt/f` = Windows `F:`、`/mnt/d` = `D:`、`/mnt/e` = `E:`、`/mnt/c` = `C:`（**只读**）；全部 `ntfs3` rw（除 c）。仓库就在 `/mnt/f/MCProject/astral_dice_multiloader`，**没有 hgfs、没有副本层**。
- **`ln -s` / 硬链接 / `write`·`edit` 工具全部可用**（VM hgfs 的限制已消失）。
- **Windows 会话遗留的 1001:1002 属主目录不可写**：`docs/agent/`（新建/删除会 `EACCES`，只能覆盖既有文件 ⇒ 写 `/tmp` 再 `cat >`）、`starengine/*/build/`（发布 pom 会失败 ⇒ `mv` 改名 + `cp -a` 重建，详见迁移文档 §4）。遇 `Permission denied` 先 `ls -ld`，**不要** chown/chmod。
- 出网：clash-verge-rev **TUN 模式**全局接管（`tun.enable=true` / `auto-route` / `198.18.0.1/30`）⇒ **直连，不带 `-c http.proxy=`**；Maven Central / aliyun / neoforged / github 均实测 200，**不需要 `~/.gradle/init.d` 镜像**。仓库 `.git/config` 里 `http(s).proxy=127.0.0.1:7897` 是与 Windows 共用的遗留项（**不要改**），要严格直连用 `git -c http.proxy= -c https.proxy=`；代理状态查 `http://127.0.0.1:9097`（Bearer secret 在 clash-verge 的 `config.yaml`）。
- JDK：`/usr/lib/jvm/java-{17,21,25}-openjdk-amd64` 三套在位；**系统默认 java 是 21**（VM 期是 25）；所有 `./gradlew` 先 `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`。
- 桌面通道：**`computer-use-linux` MCP**（KWin + portal；AT-SPI 可用）+ `screen-ocr` + `spectacle`；**无** `xdotool`/`wtype`/`wmctrl`/`grim`，`ydotool` 无 socket。
- AutoJS：端点 `http://127.0.0.1:25585/mcp`（`GET /health` 探活）；**本机未注册 `autojs` MCP**，在 DSH 里用 `curl` 打 JSON-RPC。
- 互斥锁：`/mnt/f/MCProject/.agent-locks/dsh.lock`（当前 PID 26899；接管前是陈旧锁 PID 2103）。

## 3 基本盘

| 线 | 加载器 / MC | Java | runClient 目录 | 状态（宿主迁移后） |
|---|---|---|---|---|
| `neoforge-1.21.1` (P0) | NeoForge 21.1.235 / 1.21.1 | 21 | `run/1.21.1` | `:classes` ✅（真机 32s）、实机证据为 VM 期，真机未复跑 |
| `forge-1.20.1` (P0) | Forge 47.4.10 / 1.20.1 | 17 | `run/1.20.1` | `:classes` ✅（真机 30s）、实机证据为 VM 期 |
| `neoforge-26.1.2` (P1) | NeoForge 26.1.2.109 / 26.1.2 | 25 | `run/26.1.2` | `:classes` ✅（需 `--no-configuration-cache`）、实机停在 KubeJS 报错屏 |
| `fabric-1.20.1` (P1) | Fabric 0.19.5 + FAPI 0.92.12 | 17 | `run/fabric-1.20.1` | `:classes` ✅、**dev 启动失败**（Sodium/LWJGL） |

- 构建依赖 `starengine:1.1.0` 走 `mavenLocal()`；四线制品在 `~/.m2/repository/com/merlinkitsune/starengine/*/1.1.0/`。
- 投放目标：`狐の航空学 Voxy Edition`(1.21.1) / `1.20.1-Forge 模组测试` / `26.1.2-NeoForge 模组测试` / `1.20.1-Fabric 模组测试`，均在 `/mnt/d/.minecraft/versions/<实例>/mods`。

## 4 Linux 测试台（取代 Windows 的 `scripts/test/mt.ps1`）

```bash
bash scripts/test/linux/env.sh                      # 只读检查；机器行 CHECK: key=..., ENV_RESULT: OK|WARN|FAIL
bash scripts/test/linux/env.sh --version 1.21.1 --quiet
bash scripts/test/linux/launch.sh --version 1.21.1  # 就绪 = AutoJS /health 200 + status client available → LAUNCH: READY
bash scripts/test/linux/launch.sh --version 26.1.2 --timeout 900
bash scripts/test/linux/stop.sh  --version 1.21.1   # → LINUX_STOP: OK
bash scripts/test/linux/stop.sh  --all
```

- 当前读数：`ENV_RESULT: WARN — 0 项阻塞，1 项提示`（唯一提示 = AutoJS 未运行，游戏没起时属正常）。
- 键名变更：`hgfs_writable` → `repo_writable`；新增 `locks_stale`；`conf_windows` 降为 INFO。
- Windows 的 `mt.ps1`（约 19.4k 行）在 Linux **整台不可移植且不调用**；未复刻：进程防撞锁、世界清场、OP/禁 AI 闸门、`mt_inject`/`mt_ime`/`mt_capture`。替代：游戏内 **AutoJS `eval_js`**，GUI 级 **`computer-use-linux`**。

## 5 当前工作树（未提交，2026-10-31 18:1x）

```
 M AGENTS.md                                   # 分派/红线/索引改原生 Linux
 M build.gradle                                # mcHomePath 的 Linux 分支改 /mnt/d/.minecraft
 M neoforge-1.21.1/build.gradle  M forge-1.20.1/build.gradle
 M neoforge-26.1.2/build.gradle  M fabric-1.20.1/build.gradle   # 仅注释里的投放根路径
 M docs/agent/AGENTS-dsh-linux.md               # 整篇重写为原生 Linux 口径（361 行 / 45,641 B）
 M docs/agent/PROJECT-REFERENCE.md              # 投放根、starengine jar 版本、桌面通道
 M docs/handover-new-session-context.md         # 本文件
 M scripts/dev/rsync-original.sh                # 停用（默认 exit 3）
 M scripts/test/linux/README.md                 # §8/§9 口径 + 新增 §9.8
 M scripts/test/linux/env.sh                    # ntfs3 分支 / repo_writable / locks_stale / mc_home
 M scripts/test/linux/launch.sh                 # 一处文案
?? docs/handover-2026-10-31-native-linux-migration.md   # 本批专题交接
```

- 用户既有 4 个未跟踪文件（**不是本批产物，禁止改动**）：`models/astral_anvil.bbmodel`、`scripts/test/cases/TMP-ATTRLESS-HYDRA-1.21.1.json`、`scripts/test/resources/kubejs/1.21.1/server_scripts/astral_batch3_probe.js`、`zz_screen_probe.py`。
- `run/` 整个被 `.gitignore:61` 忽略 ⇒ `run/start-*.sh`（8,650 / 8,665 / 9,636 B，fabric 无 start 脚本）不入库；`.bat` 与 `start-*.sh` 并存，Windows 侧继续用 `.bat`。
- **`docs/handover-1.5.0-main.md` 与 `4.1ba` 条目由本批追加**（只追加，不改历史条目）。

## 6 最常用命令（相对仓库根）

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64        # 所有 gradlew 的 daemon 必须是 21
./gradlew :neoforge-1.21.1:classes --console=plain
./gradlew :forge-1.20.1:classes    --console=plain
./gradlew :neoforge-26.1.2:classes --console=plain --no-configuration-cache
./gradlew :forge-1.20.1:pushToDevRun --console=plain       # legacyforge dev 加载 run/1.20.1/mods/*.jar
bash run/start-1.21.1.sh                                   # 直接起客户端（需 DISPLAY=:0）
python3 -B docs/agent/verify-split.py                      # 规则库守门（期望全 [OK]）
python3 tools/verify_sign_display_names.py                 # 立牌命名守门（GATE: PASS）
bash scripts/test/linux/env.sh                             # 环境体检
```

- starengine 侧（需要重发制品时）：
  ```bash
  cd /mnt/f/MCProject/starengine && export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  ./gradlew :neoforge-26.1.2:publishToMavenLocal --console=plain --no-configuration-cache
  ```
  若报 `Could not write .../build/publications/mavenJava/pom-default.xml` ⇒ `build/` 是 Windows 遗留的 1001 属主，按迁移文档 §4 的 `mv` + `cp -a` 绕法处理。

## 7 已知缺口 / 未验证（照抄，勿当成已完成）

1. **冷构建耗时未测**：真机只跑了热缓存（32s/30s），首次 decompile 未测（`[[PENDING:build-time-native]]`）。
2. **真机 AutoJS 未复测**（`[[PENDING:autojs-native]]`）：端点/类加载/端口占用；本机无 `autojs` MCP，只能 `curl` JSON-RPC。
3. **桌面注入未验证**：`computer-use-linux` 的 screenshot 走 portal（首次可能弹授权）；缺 `xdotool`/`wtype`/`grim`。
4. **fabric-1.20.1 dev 启动失败**：Sodium 要 LWJGL 3.3.1，Loom dev runtime 给 3.3.2-snapshot（与宿主无关，P1）。
5. **26.1.2 KubeJS 类加载**：`astral_wallet_vision.js#38` 报 `StarCoinWalletState: Class could not be found`；`run/26.1.2/mods/` 还残留旧 `astral_dice-1.3.7-beta.2` jar（未清）。
6. **`starengine/*/build.vmresidue-20261031/`**（约 300 MB）是 Windows 属主残留，删除需 root。
7. **`.vscode/launch.json` 仍是 `F:\...` 绝对路径**——Windows 侧配置，按红线保留；Linux 用 `run/start-*.sh`。
8. `scripts/test/mt.conf` 仍是 Windows 历史路径（`env.sh` 只报 INFO，不阻断、不修改）。
9. **`-next` 2.0 线**需要 `starengine 26.4-SNAPSHOT.1`（本机 `~/.m2` 只有 1.1.0），暂不能正式配置。
10. **`pushToGame` 未实跑**：投放根已指向 `/mnt/d/.minecraft`，但只验证到「解析正确 + 目录齐全」，真正投放属「默认不部署」，需用户明确下达。
11. 本批**未提交**（等用户裁决）。

## 8 纪律红线（本批踩过的坑，必须遵守）

- **实机必须用户明确下达**；测试结束必须 `stop.sh` 收停，贴 `LINUX_STOP: OK` + 无 java/`net.minecraft` 残留。
- 禁止裸 `pkill -f`（会杀别的 MC 实例）；脚本一律 `/proc/<pid>/cwd` + cmdline 精确匹配。
- **脚本运行中不要编辑它**：bash 按字节流重解析，可能把启动脚本再跑一遍。
- `legacyforge` dev 运行加载 `run/1.20.1/mods/*.jar` ⇒ 改 java/资源后必须 `:forge-1.20.1:pushToDevRun`。
- 26.1.2 构建必须 `--no-configuration-cache`；四线 gradle daemon 必须 ≥JDK 21。
- **同一时间只允许一个 gradle**（全队一个并发位）；动工作树或起 gradle 前查 `.agent-locks/`。
- **不破坏 Windows 侧**：`*.ps1` / `*.bat` / `gradlew.bat` / `mt.conf` / `.vscode` / `run/*.bat` 一律保留原样；不删、不改、不「顺手清理」。
- 权限坑：`docs/agent/` 与 `starengine/*/build/` 是 1001 属主目录，**不停留去找 chown**，用文档里的绕法。
- AutoJS eval：KubeJS 下用 `Java.loadClass('net.minecraft.client.Minecraft')`，**没有裸 `Minecraft` 全局**；`side` 必须明确（server/client）。
- 默认不 commit / push / tag；默认不部署；保护 `models/` `images/` `sound/`；互斥锁三行 `PID=/TIME=/TASK=`；发现非自己改动立即停手报告。
