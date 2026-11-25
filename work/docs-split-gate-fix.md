# 文档门禁修复报告：`docs/agent/verify-split.py` 全绿

- 仓库：`D:\MCProject\astral_dice_multiloader`
- 执行环境：Windows 原生 / CPython（`python`）/ git 工作树（非 worktree 子目录）
- 时间：2026-11-02
- 未执行：`commit` / `push` / `stash` / `checkout` / 任何分支或索引操作

---

## 1. 前提核对（实测与任务书描述有两处偏差，按实测执行）

### 1.1 行尾现状与任务书假设相反

任务书写「md 应为 CRLF，py 应为 LF」。`git ls-files --eol` 实测（改前）：

```
i/lf    w/crlf  attr/                 docs/agent/AGENTS-dsh.md
i/lf    w/lf    attr/                 docs/agent/AGENTS-full.md
i/lf    w/crlf  attr/text eol=lf      docs/agent/verify-split.py
```

即工作区里 `AGENTS-full.md` 是**纯 LF**（CRLF=0），`verify-split.py` 是**纯 CRLF**（裸 LF=0），与任务书的描述正好互换。按「保持原样」这条硬约束执行：改后二者仍分别是纯 LF / 纯 CRLF，全程未做任何行尾转换。

### 1.2 dsh 侧新增内容的实际形态（不是「一条红线 + 一段裁决记录」）

`git diff docs/agent/AGENTS-dsh.md`（本会话早前的改动，非本次）实际是两处：

1. `docs/agent/AGENTS-dsh.md:158` 那条 `> **子项目优先级与默认规则…**` 长行**原位插入两段文字**（157 字符 + 209 字符，零删除）；
2. 其后**新增 9 行**：空行 + `## 发布流程硬规则（2026-11-02 用户裁决，强制）` + 空行 + 6 条 bullet。

因此「共同安全红线第 7 条」在 dsh 侧落成了**一整节**，而不是红线列表里多一行——这正是 `AGENTS-full.md` 侧 +9 行、且校验脚本的固定切片 `[1:120]` 也随之失效的原因。同步时按此实际形态逐字照抄，未做任何转述、重排或改写。

---

## 2. 改动清单（文件:行号）

### 2.1 `docs/agent/AGENTS-full.md`

只动了红线区（`L2`–`L129`，改前为 `L2`–`L120`）。

| 位置 | 改动 |
|---|---|
| `docs/agent/AGENTS-full.md:3` | 原长行**原位**插入 2 段（+157 / +209 字符，0 删除）；行尾由 1,399 → 1,766 字符 |
| `docs/agent/AGENTS-full.md:4` | 新增 1 空行 |
| `docs/agent/AGENTS-full.md:5` | 新增标题 `## 发布流程硬规则（2026-11-02 用户裁决，强制）` |
| `docs/agent/AGENTS-full.md:6` | 新增 1 空行 |
| `docs/agent/AGENTS-full.md:7`–`12` | 新增 6 条 bullet（没有详细测试就没有发布版本 / 发布动作必须是最后一步 / 覆盖全部渠道 / 违规处置 / 发布前须取得用户许可 / CF 站点显示名与 changelog 口径） |

落点与 dsh 侧同位置（红线区首行之后），不是追加到文件末尾；插入后 `AGENTS-full.md L2`–`L129` 与 `docs/agent/AGENTS-dsh.md` 红线区（`L158`–`L284`）逐字节相等，同为 14,476 字符。

插入的两段原文（从 `AGENTS-full.md:3` 原样摘出，含首部空格与加粗标记，共 157 / 209 字符）：

```
 **2026-11-02 用户裁决（取代本条后半）**：四线 jar 允许放入同一个 GitHub Release（按版本号打 tag、全部正式版）；CF 侧 `fabric` / `neoforge-26.1.2` 仍受 `--allow-p1` 显式放行闸门约束（保留为防误传互锁），上传时必须显式加该参数。
```

```
**③ 发布时序红线（2026-11-02 用户裁决，强制）**：**未经详细测试（含实机冒烟）的版本一律不得作为发布版本**；**生产版本的发布动作必须是整条流程的最后一步** —— 固定顺序为「构建 → 本地详细测试 / 实机冒烟取证 → 审核项全过 → 才允许 push / tag / Release / CurseForge 上传」。在测试结论齐全之前执行的任何对外发布动作都算违规，发现即回滚或下架并记录。
```

### 2.2 `docs/agent/verify-split.py`

| 位置 | 改动 | 原因 |
|---|---|---|
| `docs/agent/verify-split.py:8` | docstring：`与快照 L2–L120 仍逐字一致` → `L2–L129` | 区间下界随 +9 行位移 |
| `docs/agent/verify-split.py:9` | docstring：`共同安全红线 1–6 条` → `1–7 条` | 合法状态变更 |
| `docs/agent/verify-split.py:102`–`103` | 新增 2 行注释，说明 dsh 侧新增整节「发布流程硬规则」（+9 行）已同步进 full，故比较区间 `L2–L120` → `L2–L129`，**判据仍是逐字节相等** | 记录改动依据，避免后来者误以为放宽了判据 |
| `docs/agent/verify-split.py:106` | `-    _exp = '…'.split('\\n')[1:120]…` → `split('\\n')[1:129]` | **隐性的第三处耦合**：固定切片上界；不改则红线区一致性判据永远 FAIL |
| `docs/agent/verify-split.py:110` | 检查项标题 `AGENTS-full.md L2–L120 逐字一致` → `L2–L129` | 文案与判据对齐 |
| `docs/agent/verify-split.py:123`–`124` | `want_red` 追加关键字 `'没有详细测试就没有发布版本'` | 第 7 条的关键词集合 |
| `docs/agent/verify-split.py:127` | 检查项标题 `共同安全红线 1–6 条齐全且编号连续` → `1–7`；`nums == [1, 2, 3, 4, 5, 6]` → `[1, 2, 3, 4, 5, 6, 7]` | 编号集合与文案 |

**没有放开的判据**：红线区「逐字节一致」（`_act == _exp`）、编号严格等于列出的序列、关键词命中率 100%（`not miss_red`）三处逻辑分支均一字未改，只改了期望值本身。

### 2.3 明确未改动

- `docs/agent/AGENTS-dsh.md`：md5 仍为 `ac5d26397fb118cb49ef2f54c7b591c1`，CRLF 仍为 316（本会话早前的改动保持原状，本次一行未碰）。
- `docs/agent/AGENTS-dsh-linux.md`、`docs/archive/card-recipes-2026-10-09/README.md`、fabric 的 3 个 java、`tools/curseforge_upload.py`、`.github/workflows/build.yml`：未打开、未写、未暂存。
- 根 `AGENTS.md` 未改：它在本次开工前就已含第 7 条红线（`AGENTS.md:30`：`7. **没有详细测试就没有发布版本**：…（2026-11-02 用户裁决）。`），且相对 HEAD 无改动。

---

## 3. `git diff` 关键片段

### 3.1 `docs/agent/AGENTS-full.md`（4 个 hunk，合计 +13 / −4）

红线区所在的第 1 个 hunk（超长行按首尾截断显示，已标注原长）：

```diff
@@ -3 +3,10 @@
-> **子项目优先级与默认规则(必须遵守,2026-10-04 用户裁决重新划级)**:⛔ …〔全长 1400 字符〕… 附件）；带 `-rc` / `-pre` / `-alpha` / `-beta` 的版本一律标 prerelease、不占 latest。
+> **子项目优先级与默认规则(必须遵守,2026-10-04 用户裁决重新划级)**:⛔ …〔全长 1766 字符〕… / tag / Release / CurseForge 上传」。在测试结论齐全之前执行的任何对外发布动作都算违规，发现即回滚或下架并记录。
+
+## 发布流程硬规则（2026-11-02 用户裁决，强制）
+
+- **没有详细测试就没有发布版本**：构建成功不等于可发布。任何版本在对外分发前必须完成本地详细测试与**实机冒烟**取证（进世界、mod 清单与版本号、版本门行、关键功能日志/截图），证据缺失即视为未测试。
+- **发布动作必须是最后一步**：固定顺序 = 构建 → 本地详细测试 → 实机冒烟取证 → 审核项全过 → 才允许 push / tag / GitHub Release / CurseForge 上传。禁止「先上传再补测」「边测边发」。
+- **覆盖全部渠道**：GitHub Release、CurseForge、任何对外分发点一视同仁；构建产物本地生成不算发布。
+- **违规处置**：若发现已在测试结论齐全之前发布 ⇒ 立即下架/归档该件并登记在案，不得用「后续补测通过」掩盖已发生的越序发布。
+- **发布前须取得用户许可（2026-11-02 补充）**：测试与实机取证完成后，先向用户汇报证据，**由用户明确点头后**才执行 push / tag / Release / CurseForge；用户未许可前不得对外发布，用户已撤回的发布件不得自行重新上传。
+- **CF 站点显示名与 changelog 口径（2026-11-02）**：上传件的显示名一律 `Astral Dice <版本> (<加载器> <MC>)`（加载器写 `NeoForge`/`Forge`/`Fabric`）；小版本更新（补丁 / hotfix / 预发布）不在 CF 写 changelog，只有主线版本 `X.Y.0` 才附带。
```

另外 3 个 hunk（**均为他人原有改动，本次只被 +9 行位移，内容未变**）：

```diff
@@ -798 +807 @@ When extending this workspace:
- **伤害事件映射(2026-09-15 校正,旧写法作废)**:…〔全长 1124 字符〕
+ **伤害事件映射(2026-09-15 校正,旧写法作废)**:…〔全长 1720 字符〕
@@ -869 +878 @@ When extending this workspace:
-**卡牌栏星级规则(平衡,与品阶无关)**:…〔全长 584 字符〕
+**卡牌栏规则(2026-10-28 起按品阶固定,与星级无关)**:…〔全长 584 字符〕
@@ -1864 +1873 @@ When extending this workspace:
-- 卡牌栏槽位**只由骰子星级决定**…〔全长 284 字符〕
+- 卡牌栏槽位**由骰子品阶固定**…〔全长 284 字符〕
```

> 这三处即任务书提到的「他人的未提交改动」（任务书写的是约 L795，实测为 L798/869/1864 起，改前 diffstat 为 3+/3−）。它们相对 HEAD 的 `+`/`−` 内容本次完全未触碰，只是行号被红线区的 +9 行整体下移；四个 hunk 的 `+`/`−` 合计 = 13+/4− = 我的 10+/1−（红线区）+ 他人的 3+/3−，算术上排除了任何第三处改动。

### 3.2 `docs/agent/verify-split.py`（3 个 hunk，合计 +9 / −6）

```diff
@@ -6,6 +6,6 @@
      + 红线区的**新落点**（docs/agent/AGENTS-dsh.md 标记块）与快照 L2–L120 仍逐字一致；
-  2. 结构完整：宿主分派表 4 个宿主 / 共同安全红线 1–6 条 / 按需文档索引节；
+     + 红线区的**新落点**（docs/agent/AGENTS-dsh.md 标记块）与快照 L2–L129 仍逐字一致；
+  2. 结构完整：宿主分派表 4 个宿主 / 共同安全红线 1–7 条 / 按需文档索引节；
@@ -100,11 +100,13 @@
 # 老脚本第 2 项守的「红线区 == 快照 L2–L120 逐字」不能丢，这里按新落点原样续守。
+# 2026-11-02：dsh 侧红线区内新增整节「## 发布流程硬规则（2026-11-02 用户裁决，强制）」（+9 行）并同步进
+# AGENTS-full.md，故比较区间的下界随之后移 L2–L120 → L2–L129（判据仍是逐字节相等，未放宽）。
-_exp = '\n'.join(norm(FULL).split('\n')[1:120]).strip('\n')
+_exp = '\n'.join(norm(FULL).split('\n')[1:129]).strip('\n')
-check('AGENTS-dsh.md 生效红线区块与 AGENTS-full.md L2–L120 逐字一致', _act == _exp,
+check('AGENTS-dsh.md 生效红线区块与 AGENTS-full.md L2–L129 逐字一致', _act == _exp,
@@ -119,8 +121,9 @@
-want_red = ['默认不启动实机', '默认不部署', '默认不 commit', '保护素材与他人改动', '互斥锁', '不代改他人职责']
+want_red = ['默认不启动实机', '默认不部署', '默认不 commit', '保护素材与他人改动', '互斥锁', '不代改他人职责',
+            '没有详细测试就没有发布版本']
-check('共同安全红线 1–6 条齐全且编号连续', nums == [1, 2, 3, 4, 5, 6] and not miss_red,
+check('共同安全红线 1–7 条齐全且编号连续', nums == [1, 2, 3, 4, 5, 6, 7] and not miss_red,
```

---

## 4. `verify-split` 改前 / 改后输出与退出码

### 4.1 改前（exit=1，2 个 FAIL）

```
== 1. 全文快照 ==
  [OK]   docs/agent/AGENTS-full.md 保序包含拆分前备份（AGENTS.md.bak-a-plan-183829；缺失/乱序 = 0）  缺失行数=0 / 乱序行数=0 / 原地改写行数=45 / 新增行数=758 / 行数 快照 4,244 vs 备份 3,480 / md5 快照=54bbf530a7fb 备份=9065ff9653fc
  [OK]   快照行数 ≥ 3480  lines=4244
  [FAIL] AGENTS-dsh.md 生效红线区块与 AGENTS-full.md L2–L120 逐字一致  红线区 14,476 字符
== 2. AGENTS.md 结构完整（宿主分派 / 共同红线 / 按需索引）==
  [OK]   宿主分派表含 4 个宿主（Kimi Code / DSH / Codex / 其他宿主）  hosts=['Kimi Code', 'DSH', 'Codex', '其他宿主']
  [FAIL] 共同安全红线 1–6 条齐全且编号连续  编号=[1, 2, 3, 4, 5, 6, 7] / 命中 6/6 条关键词
  [OK]   「按需文档索引」节存在且 ≥ 6 行有效引用  rows=10
== 3. 规则库引用有效 ==
  [OK]   14 条 docs 引用全部存在
== 4. 体量与驻留规则 ==
  [OK]   AGENTS.md 未超 32K 字符  3,876 字符
  [OK]   AGENTS-dsh.md 保留用户裁决段〈会话启动第一步：加载 using-superpowers〉  AGENTS-dsh.md 28,410 字符

结果：有失败项
exit=1
```

### 4.2 改后（exit=0，9 项全 OK）

```
== 1. 全文快照 ==
  [OK]   docs/agent/AGENTS-full.md 保序包含拆分前备份（AGENTS.md.bak-a-plan-183829；缺失/乱序 = 0）  缺失行数=0 / 乱序行数=0 / 原地改写行数=45 / 新增行数=758 / 行数 快照 4,253 vs 备份 3,480 / md5 快照=ee3ec6ea8df9 备份=9065ff9653fc
  [OK]   快照行数 ≥ 3480  lines=4253
  [OK]   AGENTS-dsh.md 生效红线区块与 AGENTS-full.md L2–L129 逐字一致  红线区 14,476 字符
== 2. AGENTS.md 结构完整（宿主分派 / 共同红线 / 按需索引）==
  [OK]   宿主分派表含 4 个宿主（Kimi Code / DSH / Codex / 其他宿主）  hosts=['Kimi Code', 'DSH', 'Codex', '其他宿主']
  [OK]   共同安全红线 1–7 条齐全且编号连续  编号=[1, 2, 3, 4, 5, 6, 7] / 命中 7/7 条关键词
  [OK]   「按需文档索引」节存在且 ≥ 6 行有效引用  rows=10
== 3. 规则库引用有效 ==
  [OK]   14 条 docs 引用全部存在
== 4. 体量与驻留规则 ==
  [OK]   AGENTS.md 未超 32K 字符  3,876 字符
  [OK]   AGENTS-dsh.md 保留用户裁决段〈会话启动第一步：加载 using-superpowers〉  AGENTS-dsh.md 28,410 字符

结果：全部通过
exit=0
```

- FAIL 行数：2 → **0**；WARN 行数：0 → 0；`[OK]` 行数：7 → 9；退出码：1 → **0**。
- `python docs/agent/verify-split.py --strict` ⇒ **exit=0**（无 FAIL、无 WARN）。

### 4.3 工作树状态

`git status --porcelain docs/agent`：

```
 M docs/agent/AGENTS-dsh-linux.md
 M docs/agent/AGENTS-dsh.md
 M docs/agent/AGENTS-full.md
 M docs/agent/verify-split.py
```

`git diff --stat docs/agent`：

```
 docs/agent/AGENTS-dsh-linux.md |  8 +++++---
 docs/agent/AGENTS-dsh.md       |  3 ++-
 docs/agent/AGENTS-full.md      | 17 +++++++++++++----
 docs/agent/verify-split.py     | 15 +++++++++------
 4 files changed, 29 insertions(+), 14 deletions(-)
```

改前基线为 2 个已被修改的文件（`AGENTS-dsh-linux.md` 5+/3−、`AGENTS-dsh.md` 2+/1−，属他人/本会话早前改动）。本次**新增**的只有 `AGENTS-full.md`（13+/4−，其中 3+/3− 是他人原有）与 `verify-split.py`（9+/6−）两处；合计 29+/14− 恰等于上述四项之和，`docs/agent` 下没有第三个文件因本次操作发生变化。

---

## 5. 行尾统计（改前 → 改后）

```
docs/agent/AGENTS-full.md     CRLF=0    bare_LF=4244 -> 4253   (纯 LF，未转换)   1,051,543 B -> 1,053,823 B
docs/agent/AGENTS-dsh.md      CRLF=316  bare_LF=0               (未修改)            51,375 B -> 51,375 B
docs/agent/verify-split.py    CRLF=157  bare_LF=0 -> 160/0      (纯 CRLF，未转换)    9,232 B -> 9,560 B
```

- `AGENTS-full.md`：CRLF 0 → 0，裸 LF 4,244 → 4,253（正好 +9，等于新增行数），**满足「与改前一致（纯 LF）」**。
- `verify-split.py`：裸 LF 0 → 0，CRLF 157 → 160（新增的 3 行注释 + `want_red` 拆行 −2/−相同行数，净 +3），**满足「与改前一致（纯 CRLF）」**。
- md5：`AGENTS-full.md` `54bbf530a7fb787c49ba07bafda134c5` → `ee3ec6ea8df92c054ba911717fa46509`；`verify-split.py` `918b75af6f590d11c5e67149dcffb6e7` → `d33e26e1f112ad975972af06dc917489`；`AGENTS-dsh.md` 全程 `ac5d26397fb118cb49ef2f54c7b591c1` 不变。

---

## 6. 判据未被放宽的证明

### 6.1 回环校验：除红线区外零改动

把改后的 `AGENTS-full.md` 还原为「`L1`–`L2` + HEAD 版 `L3` + `L13`–末尾」（HEAD 版取 `git show :0:docs/agent/AGENTS-full.md` 的第 3 行，该行不被他人改动涉及），重建结果 md5 = `54bbf530a7fb787c49ba07bafda134c5`，与改前 md5 **逐位相同** ⇒ 证明本次只改了 `L3`–`L12` 这 10 行，其余字节完全没动。

### 6.2 变异测试：两条判据仍然严格

在 `work/.tmp/docsplit/` 下复制三文件沙箱（脚本按自身路径推导 `W`，可独立运行）：

| 用例 | 操作 | 结果 |
|---|---|---|
| 基线 | 照抄改后三文件 | exit=0，全 OK |
| 红线区多 1 个字符 | `构建 → 本地详细测试` 改成 `…测试X` | **红线区一致性 FAIL**，exit=1（证明该判据未放宽） |
| 伪造第 8 条红线 | 根 `AGENTS.md` 追加 `8. **伪造红线**：用于变异测试。` | **仅** `[FAIL] 共同安全红线 1–7 条齐全且编号连续  编号=[1, 2, 3, 4, 5, 6, 7, 8]`，exit=1；红线区一致性仍 `[OK]` |

---

## 7. 未验证 / 未处理项

### 7.1 因 +9 行位移而变得陈旧的跨文件行号引用（只报告，未改）

红线区 +9 行使 `AGENTS-full.md` 中 `L3` 之后的所有行号 +9，下列引用因此失准。它们分布在本次**禁止改动**的文件、或属**历史记录**，故只登记：

- `docs/agent/AGENTS-dsh.md:155`（`## 📍 生效红线区` 标题）写 `AGENTS-full.md` L1–L120 → 现应为 L1–L129
- `docs/agent/AGENTS-dsh.md:286`（`<!-- END 生效红线区` 标记）写 `L1–L120` → L1–L129
- `docs/agent/AGENTS-dsh.md:144` 写 `L4107–L4177` → 该节标题实测现在 `AGENTS-full.md:4116`，整段应为 L4116–L4186
- `docs/agent/AGENTS-dsh-linux.md:187` / `:192` 写 `L2–L120` → L2–L129（他人未提交改动，未碰）
- `AGENTS.md:42` 写 `（L4107 起）` → 现为 L4116 起（根索引，未改）
- `docs/agent/AGENTS-full.md:3648`（历史批次记录）写「红线区仍与快照 L2–L120 逐字一致」—— 位于红线区外，按约束**故意保留原样**，它记录的是当时的判定结果
- `docs/agent/AGENTS-TOC.md`（2026-10-22 全表重算，形如 `| L20 | 3598 | ## 工作树与路径纪律… |`）：`L3` 之后的「行号」列全部 +9 失准。未重新生成（没有获得运行生成器的授权）

### 7.2 其它

- 只在 Windows 原生 CPython（`python`）下实跑；`docs/agent/AGENTS-dsh-linux.md:187` 记录的 Linux 自检口径 `python3 -B docs/agent/verify-split.py` **未执行**。脚本无平台相关逻辑，但 Linux 侧行为未经实测。
- `--strict` 的 PASS 分支已在真实树上验证（exit=0）；其「引用不存在 → FAIL」的失败分支未构造用例（变异测试只覆盖了红线区与编号两项）。
- 仓库其它门禁（`tools/verify_sign_display_names.py`、`tools/verify_effect_icons.py` 等）**未运行**。本次改动只涉及两份文档与一个自检脚本，预期无影响，但未取证。
- 遗留物：`work/.tmp/docsplit/`（`patch_full.py`、`patch_verify.py`、`roundtrip.py`、`mutation.py`、`mutation2.py`、`eol.py`、`hunkstats.py`、`dumpdiff.py`、`locate_names.py`、`mdEol.py`，以及 `before.*` / `after.*` / `strict.txt` / `full_diff_detail.txt` 等取证产物）。该目录位于本就未跟踪的 `work/.tmp/` 下，未删除（删除会改动工作树状态，留待清理）。

---

结论：门禁全绿 —— `python docs/agent/verify-split.py` 与 `--strict` 均 0 FAIL、退出码 0；红线区逐字节一致性判据未放宽，`AGENTS-full.md` 红线区外零改动。
