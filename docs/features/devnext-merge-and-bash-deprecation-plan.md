# dev-next：反同步主线 1.5.0 + 全量 pwsh 废弃（执行计划）

> 状态：**待用户放行**（2026-11-02 制定）
> 分支：`multi-dev-next` @ `7efefac5`（worktree `F:/MCProject/astral_dice_multiloader-next`）
> 主线：`origin/multi-main` @ `6fe38138`（= tag `1.5.0`）
> 用户裁决（2026-11-02）：按方案 A 执行；**废弃范围为所有 pwsh 脚本**；规则只改本分支、不动主线；两份未跟踪计划文档一起带。

## 0. 两个必须先说清的前置事实

### 0.1 主线并未"用 bash 做完所有测试"，只到 P1

实读取证（只读，2026-11-02）：

- `scripts/test/win/mt.sh`（348 行）自述为「bash 编排层（P1 契约层）」。
- `scripts/test/win/lib/phases.sh:8-16` 的分工表：`env` / `build` / `launch`(autojs) / `stop` = bash 原生；
  **`cases` / `report` / `preflight` 仍委托 pwsh**（`mt_case.ps1` / `mt_report.ps1` / `mt_preflight.ps1`），
  运行期打 `MT_DEPRECATED:`（`phases.sh:179`）。
- `launch --channel native` 亦委托 `mt_launch.ps1`（窗口/输入注入 bash 无等价物）。
- 主线 `.ps1` **没有任何废弃文件头横幅**（`grep -rni 'DEPRECATED|废弃' --include=*.ps1` = 0 命中），
  按其自订 S1 判据（`bash-first-test-flow-plan.md:170-175`：文件头 + README 标注 + 运行期标记）**S1 未完成**。

⇒ 结论：本次移植带过来的是 **bash 编排层 + bash 原生 env/build/launch/stop**，
**cases/report/preflight 仍必然经 pwsh**。
⇒ 因此横幅措辞必须写「**已废弃，但过渡期仍由 bash 编排层调用，P2–P4 后物理删除**」，
不能写「禁止调用」——否则规则与实现当场矛盾。

### 0.2 沙箱阻断：git 对象库在 workspace 之外

实测：

```
git rev-parse --git-common-dir  ->  F:/MCProject/astral_dice_multiloader/.git
echo x | git hash-object -w     ->  error: insufficient permission for adding an object
                                    to repository database F:/MCProject/.../.git/objects
touch <commondir>/_probe_write  ->  Permission denied
```

本会话 file policy = **workspace-write**，workspace = `F:/MCProject/astral_dice_multiloader-next`；
worktree 的 `.git` 是指向父仓的**文件**，对象库在父仓内 ⇒ 一切**写对象**的 git 操作被拒。

影响面：`git merge` / `git commit` / `git merge-tree --write-tree` / `git hash-object -w` / `git checkout-index`（写索引）。

⇒ **硬前置**：这些步骤必须以 `danger-full-access` 逐次提权执行（ops_bash 的 sandbox_permissions 参数）。
只读 git（ls-tree / diff / show / rev-parse / merge-base）不需要提权。

## 1. 阶段划分

| 阶段 | 内容 | 是否需提权 |
|---|---|---|
| P0 | 预检：SHA 固定、合并干线、冲突面、删除数 | merge-tree 需 |
| P1 | 合并落地（普通 merge 或 plumbing） | 需 |
| P2 | 硬校验：四棵树 / 版本坐标 / CHANGELOG 双判据 | 否 |
| P3 | 四线构建 + 交接 §6 九条复核 | 否（构建写 build/，在 workspace 内） |
| P4 | 废弃层：两份计划文档 + 全量 pwsh 横幅 + 规则文档 | 否 |
| P5 | 提交（不 push）+ 独立 subagent 二次验证 | 提交需 |

## 2. P0 预检

```bash
cd F:/MCProject/astral_dice_multiloader-next
git fetch origin                       # 走 http.proxy=127.0.0.1:7897
OURS=$(git rev-parse HEAD)             # 期望 7efefac5...
THEIRS=$(git rev-parse origin/multi-main)   # 期望 6fe38138
BASE=$(git merge-base $OURS $THEIRS)   # 期望 06fef75a（交接给的合并点，需复核）
git rev-list --left-right --count $THEIRS...$OURS   # 期望 "36  8"
git status --porcelain -uall           # 只允许 7 项既有未跟踪；否则停手
```

**删除数判定（按 skill §19 口径，不要用 `diff --diff-filter=D ours theirs`）**：

```bash
git merge-tree --write-tree --no-messages $OURS $THEIRS > temp/mt.txt   # 需提权
MT=$(head -1 temp/mt.txt)                       # 合并树 OID
tail -n +2 temp/mt.txt | wc -l                  # 冲突条目数
git diff --name-status $OURS $MT | awk '{print $1}' | sort | uniq -c    # 期望 A/M/D
```

已知我方独有的 5 个 `scripts/test` 文件（TLMDICE 用例 + 1.21.1 kubejs 探针）在 3-way 里**天然保留**
（base 无、theirs 无 ⇒ 不构成删除）⇒ **预计 D = 0 ⇒ 走普通 `git merge`**。

若 D ≠ 0 且必须回补 → 改走 skill §12 的 plumbing 剧本（脚本化、默认 dry-run、`--apply` 才落地）。

## 3. P1 合并

```bash
git merge --no-ff origin/multi-main     # timeout 900s；本机删除单文件约 80ms 病理，本合并以新增为主
```

### 3.1 冲突处置表（唯一口径）

| 文件 | 取向 | 依据 |
|---|---|---|
| `*/gradle.properties` `mod_version` | **保本线** `26.4-SNAPSHOT.5+<line>` | 交接 §5 |
| `starengine_version` | **保本线** `26.4-SNAPSHOT.1` | 交接 §8 |
| `starengine_version_range` | 采纳主线**区间写法**，值填本线版本（fabric 线保留该键） | 交接 §3-C/§5 |
| `*/build.gradle` 的 `jarJar(...)` / `include "..."` | **采纳删除**（本线也改外置前置） | 交接 §3-C |
| `neoforge.mods.toml` `versionRange` | 采纳区间写法 `[26.4-SNAPSHOT.1,2.0.0)` | 交接 §3-C |
| `CHANGELOG.md` / `CHANGELOG_ZH.md` | 本线 `26.4-SNAPSHOT.*` 段保留 + 吃进 1.5.0 段 | 交接 §5 |
| `docs/handover-*` | 双向保留 | 交接 §5 |
| `scripts/test/cases/*` | 双向保留（含 24 个 `*-fabric-1.20.1.json`） | 交接 §5 |
| lang `*.json` | **逐键决策**（禁整块选边） | skill §17/§27-4 |

🚨 **禁止** `git checkout --ours <path>`：会整份丢弃对方在**非冲突区**的改动，且四棵树/编译**都不会报警**（skill §17）。
正解 = 逐文件 `git merge-file` 三方合并 + 按块选边；解完对每个冲突文件跑
`git diff <theirs> -- <path>`，确认剩余差异**只有**本线有意保留的部分。

### 3.2 收尾提交

```bash
git diff --name-only --diff-filter=U    # 必须空
grep -c -E '^(<<<<<<<|=======|>>>>>>>)' CHANGELOG.md CHANGELOG_ZH.md   # 双 0
git commit --no-edit
git log -1 --format='%h %p %s'          # 必须 2 个 parent
```

## 4. P2 硬校验

本树**没有** `scripts/verify_merge_trees.sh` / `scripts/resolve_changelog_merge.py`（主线也没有）⇒ 手写等价。

```bash
for t in $BASE $OURS $THEIRS HEAD; do git ls-tree -r --name-only $t > temp/tree_$t.txt; done
sort temp/tree_$OURS.txt temp/tree_$THEIRS.txt | uniq > temp/union.txt
comm -13 temp/union.txt <(sort temp/tree_HEAD.txt) > temp/extra.txt    # 期望空
comm -23 temp/union.txt <(sort temp/tree_HEAD.txt) > temp/lost.txt     # 期望 = expected_lost
comm -23 temp/tree_$OURS.txt <(sort temp/tree_$THEIRS.txt) > temp/only_ours.txt
comm -12 temp/only_ours.txt <(sort temp/tree_$BASE.txt) > temp/expected_lost.txt
diff temp/lost.txt temp/expected_lost.txt
```

- 判据：`extra` / `lost` **双空**（lost 恰等于 expected_lost）。
- **CHANGELOG 双判据**（skill §27-2）：段列表 `grep -n '^## '` + 条目集合 `grep -oE '^- \*\*[^*]+\*\*'`，
  两侧都要跑；只跑条目集合会漏掉"整段被吞"。
- 版本坐标**四值并排**（base/ours/theirs/worktree）核对，不靠 3-way 自动取向。

## 5. P3 构建与九条复核

四线各 `build` 一次（fabric 线 `-x pushToGame`），**必须确认 `compileJava` 不是 UP-TO-DATE**
（skill §14：四棵树全绿也可能存在符号级 API 断裂，只有编译器看得见）。

交接 §6 九条：立牌 tooltip 分支四线均 31 / 旧名 `试剂-深渊` 零命中 / 内嵌库零命中且 jar 内无 starengine /
`check_lang_sync` 四线 OK / `check_changelog_parity` OK / 四线构建成功且 jar 落位 / fabric runClient /
26.1.2 production 启动 / 用例合法性。

## 6. P4 废弃层（本次核心新增）

### 6.1 带进两份未跟踪计划文档

主线磁盘上已有、但 **git 未跟踪**（合并不会带）：
`docs/features/bash-first-test-flow-plan.md`（25,047 B）、`docs/features/windows-channel-decoupling-spec.md`（13,851 B）。
从主线工作树原样复制。它们是"pwsh 废弃"这一裁决的**事实源**，缺了规则引用就悬空。

### 6.2 全量 pwsh 加废弃横幅

范围 = **本仓所有被 git 跟踪的 `*.ps1` / `*.psm1`**（含 `scripts/verify`、`tools`、`scripts/devtools`、
`scripts/audit`、`scripts/maintenance`、根 `deploy.ps1`）。
基线实测（合并前）：`git ls-files '*.ps1'` = **52**，`'*.psm1'` = **8**；按目录
`scripts/test/ 38 · scripts/verify/ 13 · scripts/devtools/ 4 · tools/ 3 · scripts/maintenance/ 1 · scripts/audit/ 1`。
合并后会增至约 84（主线侧多出 `Mt.Channel.psm1`、`selftest_shot_channel.ps1` 等）⇒ **合并后重新盘点**。

- 位置：文件首行（**必须在 `param()` 之前，注释不影响 `param` 必须是首条可执行语句**）；
  `.psm1` 同理。
- 幂等：已含 `DEPRECATED:` 横幅则 skip。
- 行尾：**按文件名逐个检测**（本仓 `*.ps1` 工作区行尾未统一，禁止假定）；写入后对每个文件
  查「裸 LF」与「双 CR」**双 0**。
- 控制字符自检：`0x07/0x08/0x0c/0x0b` 计数必须 0（skill §23-1，路径里的 `\a`/`\b` 会被静默吃掉）。
- 措辞（三态，按文件实际处境分）：

```
# DEPRECATED: <日期> —— 测试台唯一编排入口已改为 bash scripts/test/win/mt.sh（Windows: git bash/MSYS2）；
# Linux 为 bash scripts/test/linux/{env,launch,stop}.sh。本文件不再演进，P2–P4 迁移完成后物理删除。
# 过渡期：仍由 bash 编排层以 pwsh 委托调用，不得新增功能。
```

  对 `scripts/verify` / `tools` / `devtools` 这类**非测试台**脚本，措辞改为
  「本仓已裁决 PowerShell 全局禁用（见 docs/features/windows-channel-decoupling-spec.md），
  本文件待评估改写成 bash 或 Python；暂冻结，不得新增功能」。

### 6.3 规则文档改口径（只改本分支）

| 文件 | 处 | 改法 |
|---|---|---|
| `docs/agent/AGENTS-dsh.md` | :16 | 「测试台唯一口径 = PowerShell 7 …… 不写 `.sh`」→「编排入口 = `bash scripts/test/win/mt.sh`（git bash/MSYS2）；cases/report/preflight 过渡期委托 pwsh 并打 `MT_DEPRECATED`」 |
| 同上 | :18 / :22 | 实机与收停命令改 `bash scripts/test/win/mt.sh …`；收停仍要求贴 `MT_STOP: OK` + java 进程计数 |
| 同上 | :252 / :256-262 | 「一律使用 PowerShell 7」→「pwsh 仅作为过渡依赖与历史资产，禁新增功能」 |
| `AGENTS.md` | :44 索引 | 加一行「Windows bash 测试台 | `scripts/test/win/README.md`」 |
| `scripts/test/TESTING-SPEC.md` | :95-111 | 〈唯一入口与阶段〉首选入口改 bash，保留 pwsh 作过渡并注明 `mt.ps1` 已废弃 |

⚠️ 行尾：`AGENTS.md` = CRLF、`docs/*.md` = LF、`TESTING-SPEC.md` 需实测。改完按字节核。

### 6.4 废弃层验证

```bash
bash scripts/test/win/mt.sh --help                    # usage 自打印（sed -n '2,30p' $0）
bash scripts/test/win/mt.sh --version 1.21.1 --phase env --dry-run
bash scripts/test/win/env.sh --version 1.21.1         # 只读体检
grep -rn 'pwsh -NoProfile -File scripts/test/mt.ps1' --include=*.md .   # 应只剩历史/沿革类
```

## 7. P5 交付

- 提交策略：**两个提交** —— ① 反向合并提交（`--no-edit`，保留 2 个 parent）；② 废弃层提交
  （横幅 + 规则 + 两份计划文档）。若合并后需修 API 断裂（skill §14），**另起第三个提交**，不改合并提交。
- **不 push**（红线）。
- 二次验证：合并类任务"全绿"必须包含一次**独立 subagent 对账**（skill §25）——
  brief 只给「目标 + 路径 + 期望不变量 + 可独立执行的判据命令」，**不给结论**。

## 8. 风险清单

1. **提权依赖**：所有写对象的 git 操作需逐次 `danger-full-access`。若某次被拒，不要重试同一条命令，先看是否已部分完成。
2. **合并把 n26/fabric 的两处滞后一起治**：立牌 tooltip 各缺 7 张、java 文件数差 146/139 —— 合并后会补齐，属预期。
3. **本机删除病理**（skill §5b）：本合并以新增为主（39A / 11M），预计不触发；一旦 `git status` 与 `.git` mtime
   连续两次采样都不动 = 卡死，转 plumbing。
4. **CRLF 铁律**：`core.autocrlf=true` ⇒ blob LF / 工作区 CRLF。任何脚本化写盘后必须查裸 LF 与双 CR **双 0**。
5. **中文环境 GBK 编码**：守门脚本重定向到文件时可能 `UnicodeEncodeError` 伪装成 FAIL（skill §26-6）⇒ 失败项先直跑复核。

## 9. 待用户放行

1. `danger-full-access` 逐次提权（对象库在 workspace 外，无法绕过）。
2. `git merge` 会产生合并提交 —— 红线默认不 commit，需明确放行。
3. 横幅措辞按 6.2 的三态（测试台 = 过渡期仍被调用；其余 = 冻结待改写）。
