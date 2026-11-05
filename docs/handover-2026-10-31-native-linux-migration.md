# 交接：从 VMware 虚拟机迁到原生 Linux 真机（2026-10-31，DSH 宿主）

> **状态**：环境已就绪，可正常构建与测试；**Windows 侧配置与资产未改动**（同一块 NTFS 盘，Windows 侧看到的仍是原样）。
> **本批性质**：宿主迁移 + 规则文件 Linux 化 + 测试台路径接管。**不含**任何游戏内容/功能改动，未改版本号。
> **正文口径**：本文数字均为 2026-10-31 17:5x–18:1x 实读快照；后续以命令实测为准。

## 0 一句话结论

同一份仓库（`/mnt/f/MCProject/astral_dice_multiloader`，Windows 侧 `F:\MCProject\astral_dice_multiloader`）在原生 Ubuntu 26.04.1 真机上 **可自举构建**：Gradle 9.2.1 wrapper 落地 → starengine 1.1.0 各线发布进本机 `~/.m2` → `:neoforge-1.21.1:classes` 与 `:forge-1.20.1:classes` 均 `BUILD SUCCESSFUL`；`env.sh` 只剩 1 条预期提示（AutoJS 未运行）。

## 1 宿主实读（与 VM 的差别）

| 项 | VM 期（旧口径） | 原生真机（本批实测） |
|---|---|---|
| 虚拟化 | VMware 虚拟机 | `systemd-detect-virt` = `none`，DMI `MS-7E59`（MSI 主板） |
| 系统 | Kubuntu 26.04.1 | `Ubuntu 26.04.1 LTS`（resolute），内核 `7.0.0-42-generic` |
| 桌面 | KDE Plasma Wayland | 同为 KDE Plasma Wayland（`XDG_SESSION_TYPE=wayland`, `WAYLAND_DISPLAY=wayland-0`, `DISPLAY=:0`），XWayland 在跑 |
| 仓库 | `/mnt/hgfs/MCProject/...`（vmhgfs-fuse） | `/mnt/f/MCProject/...`（`ntfs3`，`/dev/nvme2n1p1`，rw） |
| 游戏目录 | `/mnt/hgfs/.minecraft` | `/mnt/d/.minecraft`（`ntfs3`，`/dev/nvme0n1p5`，rw） |
| 工作副本层 | `~/Project/*` 四副本 + rsync 回镜像 | **无副本层**，仓库即正本（`~/Project` 已不存在） |
| Maven Central | 对本机 IP **403**，靠 `~/.gradle/init.d` 镜像 | **200 直连正常**，`init.d` 不需要 |
| 系统默认 java | 25 | **21.0.12.1** |
| 符号链接/硬链接 | `ln -s` 不支持、硬链接 `EPERM` | **均可用**（实测 rc=0） |
| 桌面输入 | `computer-use`(mirsella) + `xdotool` | `computer-use-linux` MCP（KWin + portal）；**无** `xdotool`/`wtype`/`wmctrl` |

- 盘挂载（`findmnt` 实读）：`/mnt/c` ntfs3 **ro**、`/mnt/d` ntfs3 rw、`/mnt/e` ntfs3 rw、`/mnt/f` ntfs3 rw（`uid=1000,gid=1000,dmask=0077,fmask=0077,acl`）；根 `/` = ext4。
- 工具链：`/usr/lib/jvm/java-{17,21,25}-openjdk-amd64` 三套在位；`~/.gradle` 与 `~/.m2` 迁移后为空，本批已重建。

## 2 路径重映射（Windows ↔ Linux，同一批文件）

| 用途 | Windows（未变，勿改） | 原生 Linux（本批启用） |
|---|---|---|
| 仓库 | `F:\MCProject\astral_dice_multiloader` | `/mnt/f/MCProject/astral_dice_multiloader` |
| 前置库 | `F:\MCProject\starengine` | `/mnt/f/MCProject/starengine` |
| 2.0 线 | `F:\MCProject\astral_dice_multiloader-next` | `/mnt/f/MCProject/astral_dice_multiloader-next` |
| 验证锁 | `F:\MCProject\.agent-locks\` | `/mnt/f/MCProject/.agent-locks/` |
| 整合包根 | `D:\.minecraft` | `/mnt/d/.minecraft` |
| mavenLocal | `%USERPROFILE%\.m2\repository` | `/home/kitsune/.m2/repository`（**两份独立，互不影响**） |

## 3 本批改动清单（11 改 + 1 新增）

1. **`build.gradle`**（根）：`mcHomePath` 的 Linux 分支由 `/mnt/hgfs/.minecraft` 改为 `/mnt/d/.minecraft`，仍保持 `MC_HOME` > Linux 路径 > `D:/.minecraft` 的解析顺序 ⇒ Windows 侧行为不变。实跑验证：构建日志打印 `整合包根目录 (MC_HOME): /mnt/d/.minecraft`。
2. **各线 `build.gradle`**（`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`）：`packModsDir` 注释里的 `/mnt/hgfs/.minecraft` 全部改指 `/mnt/d/.minecraft`；逻辑不动。
3. **`scripts/test/linux/env.sh`**：新增 `ntfs3|ntfs` 挂载分支；可写检查键 `hgfs_writable` → **`repo_writable`**；`conf_windows` 由 WARN 降为 INFO；`mc_home` 默认 `/mnt/d/.minecraft`；锁目录改 `/mnt/f/MCProject/.agent-locks`；新增 **`locks_stale`**（按锁文件 `PID=` 做 `kill -0`，陈旧锁只报不删）。
4. **`scripts/test/linux/launch.sh`**：一处 `（hgfs 常见）` 文案改 `（NTFS/FUSE 挂载常见）`。
5. **`run/start-{1.20.1,1.21.1,26.1.2}.sh`**：注释 `hgfs 上` → `NTFS(/mnt/f) 上`（`run/` 被 `.gitignore` 忽略，不入库）。
6. **`scripts/dev/rsync-original.sh`**：整脚本**停用**——默认打印两行 ERROR 并 `exit 3`，仅 `ALLOW_LEGACY_HGFS_RSYNC=1` 可越过；实测 `bash -n` 通过、直接执行 exit=3。
7. **`AGENTS.md`**：本仓路径行改「原生 Linux = 同一块 NTFS 盘的原生挂载 `/mnt/f/...`，无 hgfs」；红线 5 锁路径改 `/mnt/f/MCProject/.agent-locks/`；索引行改指本文；桌面通道行改 `computer-use-linux` + `screen-ocr`。
8. **`docs/agent/AGENTS-dsh-linux.md`**：**整篇重写为原生 Linux 口径**（361 行 / 45,641 B / 30,206 字符，LF），保留全部既有小节名与红线替换口径表，删掉 hgfs/`~/Project`/VM 工具链口径；见 §6。
9. **`docs/agent/PROJECT-REFERENCE.md`**：投放根改 `/mnt/d/.minecraft`；`starengine-*-1.1.0-pre.2.jar` 标题改 `1.1.0.jar`；§6.1 桌面通道改 `computer-use-linux` + `screen-ocr`；迁移指针改本文。
10. **`scripts/test/linux/README.md`**：§8 坑位、§9.1 实测记录、§9.5 假 WARN 条目改原生口径；新增 §9.8「原生 Linux 真机迁移：路径与键名变更」。
11. **`docs/handover-new-session-context.md`**：整篇按原生 Linux 重写（原为「当前是虚拟机！」+ 四副本 rsync 口径）。
12. **新增本文** + `docs/handover-1.5.0-main.md` §4.1ba 批次条目（只追加）。

**明确没动（Windows 侧资产）**：`scripts/test/mt.ps1` 及 `scripts/**/*.ps1`（52 个）、`scripts/test/mt.conf`（仍含 `D:/`、`C:/`）、`*.bat`、`gradlew.bat`、`.vscode/launch.json`（仍是 `F:\...` 绝对路径）、`run/start-*.bat`、`models/` `images/` `sound/` 素材、以及用户 4 个未跟踪文件（`models/astral_anvil.bbmodel`、`scripts/test/cases/TMP-ATTRLESS-HYDRA-1.21.1.json`、`scripts/test/resources/kubejs/1.21.1/server_scripts/astral_batch3_probe.js`、`zz_screen_probe.py`）。

## 4 NTFS 语义与权限坑（本批实测）

- **链接/原子写全部可用**（`/mnt/f` 临时目录实测后已清理）：`ln -s` rc=0、硬链接 rc=0、同目录 `mv`、`sed -i`、`python3 os.replace`、`chmod +x` 后直接执行 —— 都 OK。⇒ VM 期「只能用 `cat > <<'EOF'` 写文件」的限制消失，`write`/`edit` 工具可直接用。
- **大小写**：Linux 视图区分大小写（`CaseTest.txt` 存在时 `casetest.txt` 不存在）；但仓库 `core.ignorecase=true`（Windows 克隆遗留）⇒ 纯大小写改名可能被 git 忽略，需 `git mv` + `git status` 复核。
- **权限位不可信**：挂载带 `dmask=0077,fmask=0077,acl`，文件常显示 `700`/`600`。
- **⚠ Windows 会话遗留的 1001:1002 属主目录**（本批真实踩到两次，且会复发）：
  - `docs/agent/` 目录属主 `1001:1002`、`755` ⇒ 当前 `kitsune`(1000) **不能在该目录新建/删除文件**，但**可覆盖其中已属 kitsune 的既有文件**。`write` 工具的「临时目录 + rename」会 `EACCES` ⇒ 绕法：写到 `/tmp/...` 再 `cat … > docs/agent/<file>`。
  - `starengine` 四个子项目的 `build/` 树同样是 `1001:1002` ⇒ `publishToMavenLocal` 报 `Could not write to file '.../build/publications/mavenJava/pom-default.xml'`（**各线全挂在同一处**，不是依赖问题）。
  - 绕法（无需 root）：`mv <line>/build <line>/build.vmresidue-20261031`（改名只需父目录可写）→ `cp -a` 复制回新 `build/`（新目录属 kitsune，内容/mtime 保留）⇒ 之后 Gradle 判定 `createMinecraftArtifacts UP-TO-DATE`，**没有触发重新 decompile**，各线发布合计 4 秒完成。
  - 残留：`starengine/{neoforge-1.21.1,forge-1.20.1,fabric-1.20.1,neoforge-26.1.2}/build.vmresidue-20261031/` 共约 300 MB，**删除需 root**（属主 1001 的目录内条目不可 unlink）。要么保留、要么用户授权 `sudo chown -R kitsune:kitsune` 后清理。
  - 经验：遇到 `Permission denied` 先 `ls -ld` 看属主；**不要** chown/chmod（需 root，且动的是 Windows ACL 映射）。

## 5 构建自举实测（本批）

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64        # daemon 必须 ≥21，各线统一
./gradlew --version
# Gradle 9.2.1 / Launcher JVM 21.0.12.1 / Daemon JVM /usr/lib/jvm/java-21-openjdk-amd64
# OS Linux 7.0.0-42-generic amd64；首次自举把 dist（约 130 MB）下到 ~/.gradle/wrapper/dists
```

- 出网：`repo.maven.apache.org` 200、`maven.aliyun.com` 200、`maven.neoforged.net` 200、`github.com` 200 ⇒ **不建 init.d 镜像**；宿主的 clash-verge-rev 是 **TUN 模式**（`tun.enable=true`、`device=Meta`、`auto-route`、`198.18.0.1/30`，mihomo `v1.19.32`）⇒ **远程操作直连，不加 `-c http.proxy=`**（详见规则正本〈Git 代理与操作规范〉）。
- starengine 各线发布（`publishToMavenLocal`，均 `BUILD SUCCESSFUL`，各约 1 s，因 `build/moddev` 产物被复制保留）：

  ```
  ~/.m2/repository/com/merlinkitsune/starengine/starengine-neoforge-1.21.1/1.1.0/{jar,pom,module}
  ~/.m2/repository/com/merlinkitsune/starengine/starengine-forge-1.20.1/1.1.0/{jar,pom}
  ~/.m2/repository/com/merlinkitsune/starengine/starengine-fabric-1.20.1/1.1.0/{jar,pom}
  ~/.m2/repository/com/merlinkitsune/starengine/starengine-neoforge-26.1.2/1.1.0/{jar,pom,module}
  ```

- 主仓编译（daemon 21，热缓存）：

  ```
  ./gradlew :neoforge-1.21.1:classes  --console=plain   → BUILD SUCCESSFUL in 32s（EXIT=0，含配置各线）
  ./gradlew :forge-1.20.1:classes     --console=plain   → BUILD SUCCESSFUL in 30s（EXIT=0）
  ```
  期间打印 `整合包根目录 (MC_HOME): /mnt/d/.minecraft` ⇒ 投放根解析已指向原生路径。
- **冷构建/首次 decompile 耗时仍未测**（`[[PENDING:build-time-native]]`）：本次复用 Windows 时代的 `build/moddev/artifacts`，因此 32s/30s **不是**冷基线，禁止当 SLA。

## 6 规则文件（`docs/agent/AGENTS-dsh-linux.md`）重写要点

- 保留：全部小节名、红线「替换口径」表结构、代理协作契约 A/B/B-2/C、二次验证、子代理 8 条硬规则、命令执行规范（bash 版）、Git 代理、AutoJS 通道、测试台现状、互斥锁、交接事实源、规则索引。
- 清除：`/mnt/hgfs/*` 路径、`~/Project` 四副本、rsync 通道、`vmhgfs-fuse` 特性、JDK 默认 25、aliyun init 必需、`computer-use`(mirsella)、`xdotool`、`autojs` MCP 已注册等旧事实。
- 新增：真机 DMI/内核、四盘 ntfs3 挂载、`docs/agent` 目录不可写与 `starengine/build` 1001 属主的绕法、`locks_stale`、`repo_writable`、AutoJS 无 MCP（走 HTTP JSON-RPC）、`spectacle` 截图兜底、Windows 共用盘互不破坏的红线。
- 保 3 个 `[[PENDING:…]]`：`build-time-native`、`autojs-native`（真机 AutoJS 未复测）；`sg-publish-native` 本批已消除。

## 7 游戏测试资产接管（`/mnt/d/.minecraft`）

`versions/` 实读 6 个实例；各线投放目标与当前 jar 版本：

| 线 | 投放实例（`versions/<实例>/mods`） | mods 数 | 现有 astral_dice / starengine |
|---|---|---|---|
| `neoforge-1.21.1` | `狐の航空学 Voxy Edition` | 508 | `astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar` + `starengine-neoforge-1.21.1-1.1.0.jar` |
| `forge-1.20.1` | `1.20.1-Forge 模组测试` | 36 | `astral_dice-1.5.0-rc.7+forge_1.20.1.jar` + `starengine-forge-1.20.1-1.1.0.jar` |
| `neoforge-26.1.2` | `26.1.2-NeoForge 模组测试` | 95 | `astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`（**旧版，缺 starengine jar**） |
| `fabric-1.20.1` | `1.20.1-Fabric 模组测试` | 45 | `astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar`（旧版） |

- 另两个实例：`狐の航空学`（496 mods）、`狐の航空学 低配优化版`（464 mods，含 1.5.0-rc.4 jar）——非各线默认目标，未动。
- `env.sh` 断言 `pack_dirs=OK — 整合包根=/mnt/d/.minecraft，四线目标 mods 目录齐全`。
- **未部署**：本批只做「路径解析 + 目录可达」，**没有** `pushToGame` / `build` 投放（红线：默认不部署，需用户明确下达）。26.1.2 实例里的旧 jar 属既有遗留，未清理。

## 8 测试台现状（`bash scripts/test/linux/env.sh`，改后首跑）

```
CHECK: repo=OK / mount=INFO(ntfs3, /dev/nvme2n1p1) / gradlew=OK
CHECK: jdk_1.21.1=OK(21.0.12.1) / jdk_1.20.1=OK(17.0.20.1) / jdk_26.1.2=OK(25.0.4.1) / jdk_fabric-1.20.1=OK(17.0.20.1)
CHECK: autojs_jar_*=OK（各线均有 AutoJS jar）/ autojs_health=WARN（000，游戏未启动）
CHECK: residue=OK / conf_windows=INFO（mt.conf 的 D:/、C:/ 属 Windows 台配置，Linux 忽略不改）
CHECK: cwd=OK / repo_writable=OK（ntfs3）/ jdk_paths=OK / gradle_daemon_jvm=OK
CHECK: git_autocrlf=OK / pack_dirs=OK / locks_other=OK（仅 dsh.lock，属本宿主）
INFO:  locks — /mnt/f/MCProject/.agent-locks: dsh.lock
ENV_RESULT: WARN — 0 项阻塞，1 项提示（仅 autojs_health：游戏未启动）
```

- 锁：接管前 `dsh.lock` 是陈旧锁（`PID=2103`，进程已不存在，`TASK=26.1.2-1.5.0-sync-W0`）；按「陈旧锁只报不删、由本宿主接管」的裁决，已改写为当前 DSH 会话 PID `26899`（`TASK=native-linux-adoption`）。
- launch/stop 协议未变：READY = AutoJS `health` 200 + `status` client `available=true`；`stop.sh` 输出 `LINUX_STOP: OK` 才算无残留。

## 9 剩余 checklist / 未验证

1. `[[PENDING:build-time-native]]` 冷构建（含首次 decompile）耗时未测 —— 要测就删/移 `build/moddev` 后单线跑一次，**别在各线同时跑**。
2. `[[PENDING:autojs-native]]` 真机 AutoJS 未复测（端点、`Minecraft` 类加载方式、端口占用）；本机 **未注册 `autojs` MCP**，只能用 `curl` 打 JSON-RPC。
3. 桌面注入：`computer-use-linux` 的 screenshot 走 portal，状态 `unverified`，首次截图可能弹授权；`xdotool`/`wtype`/`wmctrl`/`grim` 缺失，`ydotool` 无 socket，**不要**假设可用。
4. `starengine/*/build.vmresidue-20261031/`（约 300 MB）需 root 才能删。
5. `.vscode/launch.json` 仍是 `F:\...` 绝对路径（Windows 侧配置，按红线**保留**）；Linux 侧用 `run/start-*.sh` 或 `scripts/test/linux/launch.sh`。
6. 既有功能遗留（与宿主无关）：fabric-1.20.1 dev 启动 LWJGL/Sodium 版本冲突；26.1.2 KubeJS `StarCoinWalletState` 类加载失败；26.1.2 构建需 `--no-configuration-cache`。
7. `-next` 2.0 线需要 `starengine 26.4-SNAPSHOT.1`，本机 mavenLocal 只有 1.1.0。
8. 本批**未 commit / 未 push**（等用户裁决）。

## 10 与 Windows 的互操作纪律（本批新增红线）

- 同一块 NTFS 盘：**不要**为了 Linux 方便去删 Windows 专属文件（`*.ps1`、`*.bat`、`mt.conf`、`.vscode`、`gradlew.bat`），也不要改 `run/` 下的 Windows 产物。
- Windows 侧已发布的 mavenLocal 与 Linux 侧独立；Windows 侧能构建不代表 Linux 侧能，反之亦然。
- 只改大小写的文件名在 Windows 上等价、在 Linux 上不等价 ⇒ 涉及重命名时两边都要 `git status` 复核。
- 双系统交替启动：Windows 快速启动/休眠占用该盘时不要写盘。
