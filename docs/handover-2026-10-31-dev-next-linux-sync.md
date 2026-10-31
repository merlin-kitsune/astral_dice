# multi-dev-next 线：原生 Linux 同步与开发交接（2026-10-31）

> **一句话**：dev 线（2.0 开发线，`26.4-SNAPSHOT.5`）已把 `multi-main` 的 1.5.0-rc.7 内容、**Linux 规则正本**与 **Linux 测试台**全部并入；本工作树 = `/mnt/f/MCProject/astral_dice_multiloader-next`（**主仓的 git worktree**，不是独立 clone）。
> **与主线的关系**：`docs/handover-new-session-context.md`、`docs/handover-1.5.0-main.md` 描述的是**主线**（`multi-main` / 1.5.0-rc.7）；涉及版本号、库引脚、发布节奏的段落**对本线不成立**，以本文件为准。

## 0 接手第一步

1. 读根 `AGENTS.md` 认领宿主 → 本机是**原生 Linux 真机** ⇒ 规则正本 `docs/agent/AGENTS-dsh-linux.md`（`docs/agent/AGENTS-dsh.md` 是 Windows 历史正本，只作对照）。
2. 读本文件（dev 线专属口径）与 `docs/handover-2026-10-31-native-linux-migration.md`（宿主迁移实测、权限坑、构建自举）。
3. 跑 `bash scripts/test/linux/env.sh` 与 `git status --short -uall` 核对现状（数字一律实读）。

## 1 本次同步的实读快照（2026-10-31 19:3x）

| 项 | 值 |
|---|---|
| 工作树 | `/mnt/f/MCProject/astral_dice_multiloader-next`（主仓 worktree） |
| 分支 / 基线 | `multi-dev-next` / `a5291f2e`（GeckoLib 硬前置 + `sykestentacle` 动画实体） |
| 合并来源 | `multi-main` 的 `06fef75a`（rc.7 线，18 个提交） |
| 合并规模 | **98 文件，+28,008 / −18,914**，22 处冲突全部人工处置 |
| 版本 | 四线 `mod_version = 26.4-SNAPSHOT.5+<加载器后缀>`（同步后按本线惯例 bump） |
| 库引脚 | `starengine_version = 26.4-SNAPSHOT.1`（**与主线 1.1.0 是两套东西**） |

## 2 worktree 指针：Windows 绝对路径 → 相对路径

`astral_dice_multiloader-next/.git` 原本写着 `gitdir: F:/MCProject/...`。Linux 下 git 把 `F:/` 当相对路径拼在 worktree 目录后面，于是**每条命令都报** `fatal: 不是 Git 仓库：.../next/F:/MCProject/...`；`git status` 输出空是假象。

改成**相对路径**（Windows 与 Linux **都能解析**，这是与 VM 期「改成 Linux 绝对路径」的关键差别——绝对路径会让另一侧坏掉）：

```
astral_dice_multiloader-next/.git
  → gitdir: ../astral_dice_multiloader/.git/worktrees/astral_dice_multiloader-next
astral_dice_multiloader/.git/worktrees/astral_dice_multiloader-next/gitdir
  → ../../../../astral_dice_multiloader-next/.git
```

`starengine_next` 同样是 worktree，同一手法：

```
starengine_next/.git                              → gitdir: ../starengine/.git/worktrees/starengine_lib_next
starengine/.git/worktrees/starengine_lib_next/gitdir → ../../../../starengine_next/.git
```

> ⚠️ **不要**用 `git worktree repair` 修这两个指针：它会写回**绝对路径**，Windows 侧随即不可用。手改上面 4 个文件即可。

## 3 一条被保留而非丢弃的改动

dev 工作树原有 5 个**未提交**的 `build.gradle` 改动（VM 期 `/mnt/hgfs/.minecraft` 版 `mcHomePath` 半成品）。它们已被 rc.7 的 `c31d36f6` + `06fef75a` 完全取代（新版解析 `MC_HOME` > `/mnt/d/.minecraft` > `D:/.minecraft`）。处置：

- 补丁备份：`temp/sync-devnext-20261031/devnext-dirty-buildgradle.patch`（主仓 `temp/`）
- 原始文件备份：同目录 5 个 `*.gradle`
- git 侧：`stash@{0}` = `devnext dirty build.gradle (VM-era mcHomePath, 2026-10-31)` —— **未 drop**，确认无用后可自行 `git stash drop`

## 4 22 处冲突的处置口径

**根因**：dev 侧部分 blob 是 CRLF、main 侧是 LF ⇒ git 把**整文件**当冲突。做法：逐文件 CR 归一后 `git merge-file` 三路合并（消除行尾噪声），再按语义定夺：

| 文件 | 处置 |
|---|---|
| `*/gradle.properties`（4） | 取 **dev**：保留 `26.4-SNAPSHOT.x` 版本与新库引脚；rc.7 的注释更新一并收下 |
| `*/build.gradle`（4） | **两边都要**：保住 dev 的 GeckoLib 依赖块，收下 rc.7 的 `packModsDir = new File(rootProject.ext.mcHomePath, …)` |
| `*/combat/DiceCombatEvents.java`（4） | **联合**：保留 dev 的「防守方可以是任意 `LivingEntity` + `TargetBattleStats.baseDefense`」分支；采纳 rc.7 的 `attrless` 守卫（无 `ATTACK_DAMAGE` 生物用 `getOriginalDamage()`／forge 用 `getAmount()` 当攻击力代理；fabric 另外把 `victimFactor` 除回去）。`magicAbsorbFactor` 的 C2 口径按各线原注释保留 |
| `lang/{en_us,ja_jp,zh_cn}.json`（6） | 三路自动合并干净，逐份 `json.load` 校验通过 |
| `AGENTS.md` | 取 main（含 Linux 规则正本新增行与 TUN 直连例外） |
| `CHANGELOG{,_ZH}.md`、`docs/agent/AGENTS-full.md` | 自动合并干净 |

合并后核对：**无残留冲突标记**；四线 `DiceCombatEvents` 同时含 `attrless`（rc.7）与 `DiceCombatParticipants`（dev）。

## 5 Linux 规则与测试资产同步清单

| 目标 | 来源 | 说明 |
|---|---|---|
| `docs/agent/AGENTS-dsh-linux.md` | 主仓工作树 | **Linux 规则正本**（47,012 B）。含 2026-10-31 迁移批：路径表、NTFS 特性、1001 属主坑、JDK/Gradle 口径、TUN 直连代理规则 |
| `AGENTS.md` | 主仓工作树 | 共同红线 3 的 **Linux 真机例外**（clash-verge-rev TUN 直连） |
| `docs/handover-new-session-context.md` | 主仓工作树 | 原生 Linux 版主线交接（本线只作参考，版本段落不适用） |
| `docs/handover-2026-10-31-native-linux-migration.md` | 主仓工作树 | 迁移实测：路径重映射、权限坑、构建自举 |
| `docs/agent/PROJECT-REFERENCE.md` | 合并带入 | 投放根 `/mnt/d/.minecraft`、桌面通道 `computer-use-linux` + `screen-ocr` |
| `scripts/test/linux/{env,launch,stop}.sh`、`README.md` | 合并带入 | Linux 测试台（取代 Windows 的 `scripts/test/mt.ps1`） |
| `scripts/dev/rsync-original.sh` | 合并带入 | 已停用，非 `ALLOW_LEGACY_HGFS_RSYNC=1` 直接 `exit 3` |
| `run/start-{1.20.1,1.21.1,26.1.2}.sh` | 主仓复制 | **`run/` 在 `.gitignore` 里**（本地资产，不入库）；`launch.sh` 会优先复用它们 |

## 6 dev 线在 Linux 上怎么干活

```bash
cd /mnt/f/MCProject/astral_dice_multiloader-next
bash scripts/test/linux/env.sh                    # 体检（只读）
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :neoforge-1.21.1:classes                # 四线：neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / fabric-1.20.1
```

- **Gradle daemon 统一 JDK 21**；各线 toolchain 21 / 17 / 25 由 Gradle 自动发现，不要用 `java -version` 推断。
- **库**：本线依赖 `starengine_next`（`/mnt/f/MCProject/starengine_next`，`next` 分支，`26.4-SNAPSHOT.1`）。首次或库变更后必须：
  ```bash
  cd /mnt/f/MCProject/starengine_next
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  ./gradlew :neoforge-1.21.1:publishToMavenLocal   # 四线各一次
  ```
- **测试台**：`bash scripts/test/linux/launch.sh --line 1.21.1`（`--dry-run` 先看计划）；实机启动必须用户明确下达。
- **整合包投放**：目标仍是 `/mnt/d/.minecraft/versions/` 下四个实例（与主线同一批目录，由根 `build.gradle` 的 `mcHomePath` 解析）。
- **互斥锁**：`/mnt/f/MCProject/.agent-locks/dsh.lock`（写文件/起 Gradle 前检查）。
- **出网**：clash-verge-rev **TUN 全局接管**，直连即可；**不要**给 git 加 `-c http.proxy=`（仓库级 `.git/config` 里的 `http.proxy` 是 Windows 期遗留，两边共用，按红线不改）。

## 7 已知坑（本批实际踩到）

> **2026-10-31 更新**：下列 1、4 两个属主坑已由用户授权 `sudo chown -R kitsune:kitsune` 修复（4 仓库 `uid 1001`/`gid 1002` 归零），残留目录也已清掉（7 个 `*.vmresidue-*` ≈ 478 MB、78 个 `*.foreign1001`、`temp/t122/stop1211_b.log` 1.1 GB）。下面保留原始症状与绕法，供 Windows 侧再导入文件时复发参考。

1. **1001:1002 属主目录**：`docs/agent/`、`scripts/dev/`（worktree 内）与 `starengine_next/{forge-1.20.1,neoforge-1.21.1}/build` 属主不是 `kitsune` ⇒ git 无法在其中创建/删除文件，Gradle 也无法写 POM。绕法：**改名 + `cp -a` 复制回**（新目录属 kitsune，内容逐字节不变），无需 root；但留下的 `*.vmresidue-20261031/` 目录**删不掉**，需 root 清理。
2. **CRLF/LF 混用**是本仓冲突放大器：dev 侧历史 blob 有 CRLF、main 侧是 LF。遇到「整文件冲突」先怀疑行尾，用 `git show :2:<path> | tr -d '\r'` 归一后再三路合并。
3. **`run/` 被 gitignore**：`run/start-*.sh` 与 `run/*/mods` 都是本地资产，换树/换机必须重新投放（本批已复制 3 个启动脚本）。
4. **`docs/agent.ownfix-20261031/`** 一次权限绕法的残留（1001 属主，无法删除）已写入主仓 `.git/info/exclude`；如需彻底清掉仍要 root。

## 8 待办（本批未做，勿当成已完成）

- [ ] **提交**：合并目前停在「已暂存、未 commit」状态（`MERGE_HEAD = 06fef75a`），等用户下令。
- [ ] 编译验证（见 §9 实测结果；未跑 = 不得声称可构建）。
- [ ] push：任何远程操作都需用户明确要求。
- [ ] 清理 `*.vmresidue-20261031/`（需 root）与 `stash@{0}`（确认无用后）。

## 9 实测结果（2026-10-31 19:3x，实机未启动）

**库发布（`starengine_next` → mavenLocal）**

```bash
cd /mnt/f/MCProject/starengine_next
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
./gradlew :neoforge-1.21.1:publishToMavenLocal   # 四线各一次
```

四线全部 `BUILD SUCCESSFUL`，`~/.m2/repository/com/merlinkitsune/starengine/*/26.4-SNAPSHOT.1/` 已就位（jar + pom，neo 两线另有 `.module`）。

> 首跑四线全 `BUILD FAILED`，原因**不是编译**：根目录 `starengine_next/build/reports` 属 `1001:1002`，Gradle 写 `problems-report.html` / 配置缓存报告时 `AccessDeniedException`。产物其实已写进 `~/.m2`，只是构建结尾报错。绕法同 §7.1（改名 + `cp -a` 重建 `build/`）后复跑即绿。

**四线编译（dev 工作树）**

| 线 | 命令 | 结果 |
|---|---|---|
| `neoforge-1.21.1` | `./gradlew :neoforge-1.21.1:classes` | `BUILD SUCCESSFUL`（`compileJava` 12 警告，其中 `SignTranslucencyMixin` 未检查操作） |
| `forge-1.20.1` | `./gradlew :forge-1.20.1:classes` | `BUILD SUCCESSFUL in 10s` |
| `neoforge-26.1.2` | `./gradlew :neoforge-26.1.2:classes` | `BUILD SUCCESSFUL in 8s` |
| `fabric-1.20.1` | `./gradlew :fabric-1.20.1:classes` | `BUILD SUCCESSFUL in 1s` |

⇒ **合并后的 22 处冲突处置在四线上都编译通过**（含 `DiceCombatEvents` 的 `attrless` + dev 防守方分支联合体）。

**测试台体检（dev 工作树）**：`ENV_RESULT: WARN — 0 项阻塞，1 项提示`（唯一提示 `autojs_health=000`，因游戏未启动）。

**未做**：实机启动、jar 打包、投放整合包、任何 git commit/push。
