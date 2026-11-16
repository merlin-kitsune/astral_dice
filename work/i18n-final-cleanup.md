# i18n 收口：回滚 26.1.2 的 D 项 + 修正 fabric-1.20.1 两处中文标题

- 工作树：`F:\MCProject\astral_dice_multiloader`（分支 `multi-main`，HEAD `fd990c15`）
- 范围：只动 lang JSON 与本文档；**未**改任何 Java/资源行为、**未**改审计判据、**未** add/commit/push/tag、**未**触碰他人的进程与改动。
- 判定口径（单一事实源）：`docs/tooltip-color-rules.md` §6 例外 1；本线实现与手册条目（fabric）。

---

## 1. 回滚 D 项：`neoforge-26.1.2` 三语 `tooltip.astral_dice.sign.fanny_active`

### 1.1 裁决依据（文档原文）

`docs/tooltip-color-rules.md:152-160`：

```text
## 6. 例外（优先级：例外 > 规则 3 > 规则 2 > 规则 1）

1. **负面红保留**：条目已用 `§c` 的，`§c` 优先，规则 1/2/3 不再改写它。
   - 负面效果条目：`§c中毒 (0:15)§7`、`§c虚弱 (0:15)§7`、`§c饥饿 (0:30)§7` 等保持红色
     （时间虽为时间，但不改蓝）。
   - 负面数值：红色行上的数值保持红色（如 `雨中/水下受到的伤害 §c+40%%§r`），
     行内回落码仍按规则 0 写回本行红 `§c`。
   - 状态警示行：`冷却中: §c%s§c 秒` 保持红色（该行行级底色即 RED）。
   - 名称类红色：`§c青之诅咒§7`、`§c全力攻击`、`§c瞬间伤害§7` 保持红色。
```

文档示例即`§c中毒 (0:15)§7`：时间为时间，但该条目已 `§c`，**不得**改成蓝色。因此 D 项（把 7 对「红名 + 时间」拆成 `§c名§r §9(时间)§尾码`）与仓库规则冲突，按文档回滚。

### 1.2 改动点（3 文件 × 1 键，均在第 568 行）

| 文件 | 行号（改前=改后） | 键 |
|---|---|---|
| `neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/zh_cn.json` | 568 | `tooltip.astral_dice.sign.fanny_active` |
| `neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/en_us.json` | 568 | 同上 |
| `neoforge-26.1.2/src/main/resources/assets/astral_dice/lang/ja_jp.json` | 568 | 同上 |

**改前形态（D 项）**：受影响的子串（每语 7 个效果，红名与时间被拆开、时间改蓝）。原文取自 D 批次的自己的改动记录 `work/fix-i18n-tooltip.md:199-207`「改后」列：

- zh：`§c中毒§r §9(0:15)§r` / `§c饥饿§r §9(0:30)§7 和 §c反胃§r §9(0:07)§r` / `§c凋灵§r §9(0:07)§7 和 §c黑暗§r §9(0:05)§r` / `§c虚弱§r §9(0:15)§7 和 §c挖掘疲劳§r §9(0:30)§r`
- en：`§cPoison§r §9(0:15)§r` / `§cHunger§r §9(0:30)§7 + §cNausea§r §9(0:07)§r` / `§cWither§r §9(0:07)§7 + §cDarkness§r §9(0:05)§r` / `§cWeakness§r §9(0:15)§7 + §cMining Fatigue§r §9(0:30)§r`
- ja：`§c毒§r §9(0:15)§r` / `§c空腹§r §9(0:30)§7 と §c吐き気§r §9(0:07)§r` / `§c衰弱§r §9(0:07)§7 と §c暗闇§r §9(0:05)§r` / `§c弱体化§r §9(0:15)§7 と §c採掘速度低下§r §9(0:30)§r`

该 D 形态确实存在过的直接证据：回滚前的审计输出（`work/i18n-final-cleanup-evidence/audit-before.txt`）在 `neoforge-26.1.2/zh_cn.json` 段落逐条命中 7 个时间令牌——第 130–136 行为 `[R3] ... code=§c token='中毒 (0:15)'`、`饥饿 (0:30)`、`反胃 (0:07)`、`凋灵 (0:07)`、`黑暗 (0:05)`、`虚弱 (0:15)`、`挖掘疲劳 (0:30)`；en 段第 153 行起为 `Poison (0:15)` 等同构 7 条，ja 段同样 7 条。

> 注：D 形态的整行字节不再存在于工作树（已被回滚），故此处按「该批次的改动记录 + 审计命中令牌」两路取证，未凭记忆复写整行。

**改后（= `git HEAD` 原文，整段 `§c` 红、时间不蓝），第 568 行：**

zh_cn：
```text
  "tooltip.astral_dice.sign.fanny_active": "立即触发以下随机事件之一：\n  获得 §9生命恢复 (0:30)§r\n  获得 §9力量 (0:30)§r\n  获得一张：攻击-特大\n  获得一张随机效果牌（不含专属）+ §e3§7 星币\n  获得 §9滋养 (2:00)§7（需安装农夫乐事）和 §9饱和 (0:30)§r\n  获得 §9迅捷 (0:30)§r\n  获得 §c瞬间伤害§r\n  获得 §c中毒 (0:15)§r\n  获得 §c饥饿 (0:30)§7 和 §c反胃 (0:07)§r\n  获得 §c凋灵 (0:07)§7 和 §c黑暗 (0:05)§r\n  获得 §c虚弱 (0:15)§7 和 §c挖掘疲劳 (0:30)§r",
```

en_us：
```text
  "tooltip.astral_dice.sign.fanny_active": "Immediately trigger one random event:\n  §9Regeneration (0:30)§r\n  §9Strength (0:30)§r\n  Gain: Attack Card XL\n  Gain a random effect card (excl. exclusive) + §e3§7 Star Coins\n  §9Nourishment (2:00)§7 (Farmer's Delight only) + §9Saturation (0:30)§r\n  §9Speed (0:30)§r\n  §cInstant Damage§r\n  §cPoison (0:15)§r\n  §cHunger (0:30)§7 + §cNausea (0:07)§r\n  §cWither (0:07)§7 + §cDarkness (0:05)§r\n  §cWeakness (0:15)§7 + §cMining Fatigue (0:30)§r",
```

ja_jp：
```text
  "tooltip.astral_dice.sign.fanny_active": "以下のランダムイベントを 1 つ即座に発動：\n  §9再生能力 (0:30)§r を獲得\n  §9攻撃力上昇 (0:30)§r を獲得\n  1 枚獲得：攻撃 - 特大\n  ランダムな効果カード（専用を除く）を 1 枚 + §e3§7 スターコインを獲得\n  §9滋養 (2:00)§7（Farmer's Delight 導入時）と §9飽和 (0:30)§r を獲得\n  §9移動速度上昇 (0:30)§r を獲得\n  §c即時ダメージ§r を獲得\n  §c毒 (0:15)§r を獲得\n  §c空腹 (0:30)§7 と §c吐き気 (0:07)§r を獲得\n  §c衰弱 (0:07)§7 と §c暗闇 (0:05)§r を獲得\n  §c弱体化 (0:15)§7 と §c採掘速度低下 (0:30)§r を獲得",
```

### 1.3 结构不变量（实测）

- 行尾：三份文件均为 LF（0 个 CR、无 BOM），回滚未引入行尾变化。
- 键数不变：三份均 1166 键。
- 文件字节数（回滚前 → 回滚后）：zh `150451 → 150409`、en `155612 → 155570`、ja `191186 → 191144`（各 −42 字节，即只有该行变化）。
- 回滚后的键行与 `git HEAD` **逐字节相同**（`work/i18n-final-cleanup-evidence/verify_revert.py`，三份 `byte_identical: true`，各自 1 处命中）。
- `git diff` 复扫：三份文件的 diff 中已 **0 行**包含 `sign.fanny_active`、**0 行**包含 `§9(`；diffstat 由 `229 insertions / 13 deletions` 变为 `226 / 10`。

---

## 2. 审计计数（`scripts/audit/tooltip_color_audit.ps1`，改前 / 改后实跑原文）

命令（两次完全相同）：

```powershell
pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1
```

改前（D 项在树上）：

```text
覆盖矩阵： neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / fabric-1.20.1  x  zh_cn.json, en_us.json, ja_jp.json  (实审 lang 文件 12 个)
tooltip 染色审计： FAIL（251 处违规）
```

改后（回滚 D 项之后）：

```text
覆盖矩阵： neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / fabric-1.20.1  x  zh_cn.json, en_us.json, ja_jp.json  (实审 lang 文件 12 个)
tooltip 染色审计： FAIL（230 处违规）
```

按规则拆分（逐条 count，来源 `audit-before.txt` / `audit-after-revert.txt`）：

| | R1 | R2 | R3 | 合计 |
|---|---|---|---|---|
| 改前 | 66 | 72 | **113** | **251** |
| 改后 | 66 | 72 | **92** | **230** |
| 差 | 0 | 0 | **−21** | **−21** |

12 份文件的逐文件前后（total 处数）：

| 文件 | 改前 | 改后 | 其中 `sign.fanny_active` 命中数 前→后 |
|---|---|---|---|
| neoforge-1.21.1/zh_cn.json | 17 | 17 | 2 → 2 |
| neoforge-1.21.1/en_us.json | 20 | 20 | 2 → 2 |
| neoforge-1.21.1/ja_jp.json | 24 | 24 | 2 → 2 |
| forge-1.20.1/zh_cn.json | 17 | 17 | 2 → 2 |
| forge-1.20.1/en_us.json | 20 | 20 | 2 → 2 |
| forge-1.20.1/ja_jp.json | 24 | 24 | 2 → 2 |
| **neoforge-26.1.2/zh_cn.json** | **22** | **15** | **7 → 0** |
| **neoforge-26.1.2/en_us.json** | **23** | **16** | **7 → 0** |
| **neoforge-26.1.2/ja_jp.json** | **27** | **20** | **7 → 0** |
| fabric-1.20.1/zh_cn.json | 17 | 17 | 2 → 2 |
| fabric-1.20.1/en_us.json | 18 | 18 | 2 → 2 |
| fabric-1.20.1/ja_jp.json | 22 | 22 | 2 → 2 |

结论：R3 恰好 −21（7 对 × 3 语），R1/R2 完全未动 —— 回滚**没有**制造新的 R2（时间应着蓝）违例，正是 §6 例外 1 所要求的效果；其余 10 份文件一字未变。

**判据未被放宽**：`git diff --numstat -- scripts/audit/tooltip_color_audit.ps1` 为空（无任何改动）；`docs/tooltip-color-rules.md`、`tools/check_lang_sync.py` 与 HEAD 同 md5。（若用 `git show HEAD:<file> | md5sum` 与工作区 md5 对比可能出现差异，那是 `core.autocrlf` 下 LF/CRLF 的管道假象，判定以 `git status`/`git diff` 为准——本次两者均显示未修改。）

当前审计仍是 FAIL（230 处）：这 230 处分布全部四线、属于本次任务之前既已存在的未提交批次（本次唯一增量 +21 已归零，见上表；`work/fix-i18n-tooltip.md:342` 记录的基线同为 230）。

---

## 3. fabric-1.20.1 两处中文标题

### 3.1 取证一：本线 Java 实现（决定「这线到底做什么」）

`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/ParunanSignItem.java`

- 第 16–19 行 javadoc：主动技能「网络购物」——`远程打开卡牌商店`界面，本身不施加任何效果、不消耗星光。
- 第 83 行：`CardShopOpener.open(player, true);`（`handleUse` 内实际行为）。
- `ACTIVE_COOLDOWN_SECONDS = 120`。

`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/FannySignItem.java`

- 第 11–20 行 javadoc：2026-10-10 裁决**删除了本立牌注册的全部 11 个随机事件**，主动技能改为「事件选择界面」，并逐条列出被删内容（生命恢复/力量(0:30)、攻击-特大、随机效果牌+3 星币、滋养+饱和、迅捷、瞬间伤害、中毒、饥饿+反胃、凋灵+黑暗、虚弱+挖掘疲劳——**恰好是 neo26 `fanny_active` 的那一整串**）。
- 第 46 行：`boolean opened = com.merlinkitsune.astral_dice.event.EventChoiceManager.start(serverPlayer);`。

即：fabric 线 `parunan.active` = 远程开店，`fanny.active` = 事件 2 选 1；**与 neo26 的「套现（2:1 星光换星币 + 随机效果）」/「麻烦制造者（随机事件）」不是同一行为**。

### 3.2 取证二：本线帕秋莉手册条目（zh 与 en/ja 的既有措辞）

`fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json`

- 第 99 行 `astral_dice.guide.entry.fanny_sign.1`：`主动技能-事件选择：从当前事件池随机抽出 2 个全体事件……`
- 第 142 行 `astral_dice.guide.entry.parunan_sign.1`：`经商立牌。主动「网络购物」：远程打开卡牌商店界面……`

同文件 `en_us.json` 第 99 行 `Active Skill - Event Choice: …`、第 142 行 `Active "Online Shopping": remotely opens the Card Shop screen…`；`ja_jp.json` 第 99 行 `アクティブスキル-イベント選択…`、第 142 行 `アクティブ「ネット通販」…`。

→ en/ja 的**名称键**已与本线手册名对齐（Online Shopping / Event Choice、ネット通販 / イベント選択），只有 zh 名称键还留着 neo26 的值。

### 3.3 取证三：跨线三语对照

| 键 | neoforge-1.21.1 | forge-1.20.1 | fabric-1.20.1（改前） | neoforge-26.1.2 |
|---|---|---|---|---|
| `sign.parunan.active` zh / en / ja | 网络购物 / Online Shopping / ネット通販 | 同左 | **套现** / Online Shopping / ネット通販 | 套现 / Cash Out / 現金化 |
| `sign.fanny.active` zh / en / ja | 事件选择 / Event Choice / イベント選択 | 同左 | **麻烦制造者** / Event Choice / イベント選択 | 麻烦制造者 / Trouble Maker / トラブルメーカー |

fabric 的这两个名称键在 `git HEAD` 中并不存在（是本轮未提交批次新增），其描述键（`parunan_active` = 「远程打开卡牌商店」、`fanny_active` = 「在2个全体事件中选择1个全体事件并触发」）已写成本线行为——只有 zh 名称键与描述/手册/Java 三处相互矛盾。`套现` 在 fabric 树中仅出现在 zh_cn.json:670，`麻烦制造者` 仅出现在 zh_cn.json:684（fabric 的 `*.java` 中 0 次），可确认属移植残留。

### 3.4 结论：走「改」这一支

取证显示 fabric 行为 **不等于** neo26（远程开店 / 事件 2 选 1，且本线手册与描述键早已这么写），故不是「维持原状」分支；按本线手册条目名把 zh 名称定为 `网络购物` / `事件选择`。

### 3.5 改动点（只改 zh 两键）

文件：`fabric-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json`

| 行号 | 键 | 改前 | 改后 |
|---|---|---|---|
| 670 | `tooltip.astral_dice.sign.parunan.active` | `"套现"` | `"网络购物"` |
| 684 | `tooltip.astral_dice.sign.fanny.active` | `"麻烦制造者"` | `"事件选择"` |

不变量（实测）：文件 149046 字节、CRLF 1151 处、裸 LF 0 处、无 BOM、以 `}\r\n` 结尾（改前后一致）；键数 1149 不变；变更键集合恰为上述两个。

---

## 4. 门禁实跑

### 4.1 `tools/check_lang_sync.py`

```text
$ python tools/check_lang_sync.py   ; exit=0
[OK]   neoforge-1.21.1: en_us=1142 键，3 份语言键集一致
[OK]   forge-1.20.1: en_us=1142 键，3 份语言键集一致
[OK]   neoforge-26.1.2: en_us=1166 键，3 份语言键集一致
[OK]   fabric-1.20.1: en_us=1149 键，3 份语言键集一致
```

计数与改动前对照（1142 / 1142 / 1166 / 1149）完全一致：本次只改值与一处键的行内容，未增删任何键。

### 4.2 `scripts/audit/tooltip_color_audit.ps1`

```text
$ pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1   ; exit=1
tooltip 染色审计： FAIL（230 处违规）
```

R1 = 66 / R2 = 72 / R3 = 92。其中 `neoforge-26.1.2` 三份由 22/23/27 降为 15/16/20，`sign.fanny_active` 相关 R3 命中由 7/7/7 降为 0/0/0；fabric 的改动对染色无影响（fabric 三份文件计数前后都是 17/18/22）。审计整体仍 FAIL 的原因见 §2 末（230 处为既存基线，非本次引入）。

---

## 5. 没改的 / 与理由

1. **`scripts/audit/tooltip_color_audit.ps1`** —— 一行未动（`git diff --numstat` 为空）。为了「让门禁变绿」而放宽判据属于降级，本次明确不做。
2. **`docs/tooltip-color-rules.md`** —— 未动（md5 与 HEAD 相同）。本次的裁决就是按它的 §6 例外 1 执行的，文档无需改写。
3. **fabric 的 en/ja 名称键** —— 未动。已与本线手册名一致（Online Shopping / Event Choice、ネット通販 / イベント選択），任务要求「三语保持一致」在此即「只有 zh 需要修」。
4. **fabric 的帕秋莉手册条目（第 99 / 142 行等）** —— 未动。它们是本次判定 zh 名称的**依据**，措辞已正确。
5. **fabric 的 `parunan_active` / `fanny_active` 描述长句** —— 未动。它们已是本线行为（远程打开卡牌商店 / 在 2 个全体事件中选择 1 个并触发），不在本次两键范围内。
6. **其余三线（neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2）除 D 项那一行以外的任何 lang 内容** —— 未动；审计逐文件表证明这 9 份文件前后计数完全一致。
7. **审计里剩下的 230 处违规** —— 未动。它们分布在四线、属本次任务之前既存在的未提交批次；本次任务的唯一增量（+21 R3）已归零。若要清理它们需另立裁决（每一条都涉及文案取舍，不能顺手改）。
8. **未跑 Gradle 构建** —— 两个原因：① 本次只改 lang 值（JSON 语法与键集已由 `check_lang_sync.py` 实际解析验证），无任何 Java 语义变更；② fabric 线的 `KnotServer/KnotClient` 与 `gradlew` 进程属于仓库里另一个并发工作流，运行或终止构建都可能与之互相踩踏（任务明令禁止干扰）。如需资源打包级复验，建议由持有该构建的工作流在空闲时执行一次 `processResources`。

---

## 6. 证据文件

目录 `work/i18n-final-cleanup-evidence/`：

| 文件 | 内容 |
|---|---|
| `audit-before.txt` | 回滚前的审计完整输出（251 处；含 D 形态的 7 对命中） |
| `audit-after-revert.txt` | 回滚后审计完整输出（230 处） |
| `audit-final.txt` | fabric 改动后的最终审计输出（230 处，逐文件与上一份一致） |
| `langsync-after-revert.txt` / `langsync-final.txt` | 回滚后 / 最终的 `check_lang_sync.py` 输出（四线 [OK]） |
| `revert_fanny_active.py` | 回滚脚本（从 `git show HEAD:<path>` 取该行、断言无 BOM/无 CR/命中 1 次、保 LF） |
| `verify_revert.py` | 独立复核：三份文件该行与 HEAD 逐字节相同 |
| `fix_fabric_zh.py` | fabric zh 两键替换脚本（断言仅 2 键变更、CRLF/键数不变） |
| `fabric-fix.json` | fabric 改动的 before/after 与不变量断言记录 |

---

## 7. 复核命令（可直接粘贴）

```powershell
cd F:\MCProject\astral_dice_multiloader
python tools/check_lang_sync.py
pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1
git -C F:\MCProject\astral_dice_multiloader diff --numstat -- scripts/audit/tooltip_color_audit.ps1   # 应为空
python work/i18n-final-cleanup-evidence/verify_revert.py                                              # 三份 byte_identical: true
```
