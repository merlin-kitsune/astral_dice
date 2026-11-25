# CI 守门本地复现 + build.yml 四线同批发布改动

- 仓库：`D:\MCProject\astral_dice_multiloader`
- 基线：`HEAD=fd990c15`，分支 `multi-main`（ahead 24）
- 作业时间：2026-11-02
- 唯一被写的仓库文件：`.github/workflows/build.yml`（本报告按交付要求另写）
- 宿主：Windows，pwsh 7.6.9，python 3.12.16，git（core.autocrlf=true）

---

## 0. 结论

1. **任务 A 完成**。artifact 与 Release 附件均覆盖四线；旧「只发 P0 两线」裁决注释已改写为**历史记录**（不再是指令）；tag 规则、prerelease 判定、正文取 `release/<tag>/`、`--clobber` 相关逻辑**一律未动**。YAML `safe_load` 通过，12 个 `run:` 块 `bash -n` 全过（§2.3）。
2. **任务 B：14 条守门 13 绿 1 红**。
   `RC SUMMARY: [('A1',0),('A2',0),('A3',0),('A4',0),('B',0),('C',0),('D1',0),('D2',0),('D3',0),('D4',0),('D5',0),('D6',0),('D7',1),('E',0)]`
3. 唯一红项 = `scripts/verify/verify_dice_combat_attrless_guard_p1.py` 的 `fabric-1.20.1-09 line endings` 子断言（该文件工作树 CRLF 2218 处）。
   **归因：本地工作树 EOL 漂移（既有技术债 / 环境债），非本次改动引入，也不会在 CI 上复现**——HEAD 里该文件是纯 LF（`CR=0`），CI 跑 `ubuntu-latest` + `actions/checkout@v4`（Linux 上 `core.autocrlf=false`）检出必然是 LF。详见 §3.3。

---

## 1. 只读前提与纪律执行

做过（只读）：

- `git -C D:\MCProject\astral_dice_multiloader status --porcelain` / `rev-parse` / `log` / `ls-files --eol` / `show HEAD:<file>` / `diff --numstat`
- 四线 `gradle.properties` 的 `mod_version`、四线 `build.gradle` 的 `archivesName`
- 14 条守门脚本**只运行**（先 `grep` 确认它们内部无 `Out-File/Set-Content/write_text/open(...,'w')/New-Item/mkdir` 等写操作，也无 `gradlew` 调用，才执行）

明确未做：

- 未运行 `gradlew` / 任何 Gradle 构建（工作树上有他人 `gradlew` / `KnotServer` / `KnotClient` 在跑，且本任务明令禁止）
- 未启动任何游戏实例，未触碰 25565 / 25585 / 25575
- 未 `git add / commit / push / tag`，未改 `.git` 任何状态（无 `stash`、无 `checkout`）
- 未修改任何守门脚本、未改判据；未做游戏测试
- 工作树里他人的未提交改动（`fabric-1.20.1/build.gradle`、各线 `gradle.properties`、大批 `src/generated` 删除、以及 §3.3 那个 `DiceCombatEvents.java`）**全部原样未动**
- 改动后 `git status` 新增条目只有 ` M .github/workflows/build.yml`

---

## 2. 任务 A：`.github/workflows/build.yml`

### 2.1 改动汇总

`git --no-pager diff --stat -- .github/workflows/build.yml`：

```
 .github/workflows/build.yml | 71 +++++++++++++++++++++++++++++++++------------
 1 file changed, 52 insertions(+), 19 deletions(-)
```

文件：**318 行 → 351 行**，20440 B → **22721 B**，行尾 `CR=0 / CRLF=0 / LF=351`（保持纯 LF，与 HEAD 一致）。

8 处按行替换，"旧行号"取 `git show HEAD:.github/workflows/build.yml`，"新行号"取现文件：

| # | 旧行号 (HEAD) | 新行号 | 位置 | 改动 |
|---|---|---|---|---|
| 1 | 12–15 | 12–18 | `on.push.branches` 下的 ⛔ 2026-10-25 裁决注释 | 4 行指令性注释 → 7 行【历史裁决，2026-11-02 已被取代】记录：原文照录 + 说明 1.5.0 四线同 tag 同 Release + 明写「旧『只发 P0 两线』不再有效」 |
| 2 | 20 | 22 | `tags` 注释「现为 P0 两线 …」 | 改为「现为**四线** neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / fabric-1.20.1」 |
| 3 | 172–180 | 174–193 | `Upload Astral Dice JARs (P0 only)` | 拆成 **2 个 upload-artifact step**（见 2.2-A） |
| 4 | 228–230 | 241–244 | `Create/Update GitHub Release (P0 two JARs, …)` | 步骤名改 `(four JARs, …)`，注释改四线并注明 11-02 裁决取代 10-25 |
| 5 | 250–252 | 264–283 | Release 步骤内的 `JARS=(…)` 选件 | 两条硬 glob → `JARS=()` + P0 强校验循环 + 四线逐条 `[[ -f ]]` 收集（见 2.2-B） |
| 6 | 275 | 304–305 | 正文兜底注释「至少列出本次构建的**三个**附件」 | 改为「附件(实际命中的各线 jar)」 |
| 7 | 290 | 319 | 「同一 tag 下**只挂 P0 两线**的多平台 jar」 | 改为「同一 tag 下挂**四线**的多平台 jar(2026-11-02 起 26.1.2 / fabric 与 P0 两线同批)」 |
| 8 | 316–318 | 348–351 | 文件末尾 ⛔ 2026-10-25「已删除 Fabric pre-release 步骤」说明 | 改为【历史记录】：保留「删除独立 pre-release 步骤仍成立」，作废「P1 产物不可用 / 需逐次点名」 |

**未动的部分**（逐项核过 occurrence 计数未变）：`skip_tag` 逻辑（3 处）、`REL_KIND=(--prerelease --latest=false)` 与 `REL_KIND=(--latest --prerelease=false)` 各 1 处、`PLAYER_CHANGELOG`（5 处，正文仍取 `release/<tag>/`）、`NOTES_FILE`（9 处）、第 253 行的 2026-10-30「去掉 `--clobber`」裁决注释、所有 `if:` 条件、三个 JDK setup、`Setup Gradle`、StarEngine checkout/publish 步骤。

> 说明：第 253 行仍保留 `⛔` 字样，但那条是 **2026-10-30「去掉 --clobber」** 的裁决，与「P0 两线」无关、且任务要求 `--clobber` 逻辑一律不动，故原样保留。

### 2.2 两处关键改动的 before / after

**A. Upload artifact（旧 172–180 → 新 174–193）**

before：

```yaml
      - name: Upload Astral Dice JARs (P0 only)
        # ⛔ 2026-10-25 用户裁决：artifact 也只产出 P0 两线（neoforge-1.21.1 / forge-1.20.1）。
        #    P1（neoforge-26.1.2 / fabric-1.20.1）未获授权移植、产物不可用 ⇒ 不再产出 artifact。
        uses: actions/upload-artifact@v4
        with:
          name: astral_dice-jars
          path: |
            neoforge-1.21.1/build/libs/astral_dice-*.jar
            forge-1.20.1/build/libs/astral_dice-*.jar
```

after（拆两步：26.1.2 与 P0 同一步、不经守卫；fabric 单独一步带守卫）：

```yaml
      - name: Upload Astral Dice JARs (neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2)
        # 2026-11-02 用户裁决（取代 2026-10-25「artifact 只产出 P0 两线」）：四线同批发布 ⇒ artifact 亦覆盖四线。
        #    本 step 放三条**恒存在**的线（settings.gradle 恒 include，目录必在）；fabric-1.20.1 因需保留
        #    目录存在性守卫而单列下一步 —— 两个不同守卫不能塞进同一个 step，且 26.1.2 的产物不经过该守卫。
        uses: actions/upload-artifact@v4
        with:
          name: astral_dice-jars
          path: |
            neoforge-1.21.1/build/libs/astral_dice-*.jar
            forge-1.20.1/build/libs/astral_dice-*.jar
            neoforge-26.1.2/build/libs/astral_dice-*.jar

      - name: Upload Astral Dice JARs (fabric-1.20.1)
        # 守卫与上方 `Check lang sync (fabric-1.20.1)` 同口径：只为兼容「fabric 子项目尚不存在」的
        #    历史分支 / 旧 tag 检出。该守卫只作用于本 step，不影响上一步 26.1.2 的产物。
        if: hashFiles('fabric-1.20.1/gradle.properties') != ''
        uses: actions/upload-artifact@v4
        with:
          name: astral_dice-jars-fabric
          path: fabric-1.20.1/build/libs/astral_dice-*.jar
```

**B. Release 附件选件（旧 250–252 → 新 264–283）**

before：

```bash
          # ⚠️ 两个 glob 都必须至少命中一个文件,否则 bash 会把未展开的模式原样传给 gh 并报错(故意让它失败)。
          # ⛔ 2026-10-25 用户裁决：Release 附件**只放 P0 两线**（neoforge-1.21.1 + forge-1.20.1）。
          JARS=(neoforge-1.21.1/build/libs/astral_dice-*.jar forge-1.20.1/build/libs/astral_dice-*.jar)
```

after：

```bash
          # 附件 = 四线 jar（2026-11-02 用户裁决，取代 2026-10-25「附件只放 P0 两线」）。
          # 逐条 glob 判定：未命中的模式保持字面量 ⇒ `[[ -f ]]` 为假即跳过，因此「fabric-1.20.1
          #   目录不存在」只会让该线 jar 缺席，不会退化成硬失败。P0 两线则必须命中：逐个校验，
          #   缺任一即 ::error:: + exit 1（宁可不发布，也不发缺件 / 错件）。
          JARS=()
          for p in neoforge-1.21.1/build/libs/astral_dice-*.jar forge-1.20.1/build/libs/astral_dice-*.jar; do
            if [[ ! -f "$p" ]]; then
              echo "::error::required P0 jar not found: $p"
              exit 1
            fi
          done
          for p in \
            neoforge-1.21.1/build/libs/astral_dice-*.jar \
            forge-1.20.1/build/libs/astral_dice-*.jar \
            neoforge-26.1.2/build/libs/astral_dice-*.jar \
            fabric-1.20.1/build/libs/astral_dice-*.jar; do
            if [[ -f "$p" ]]; then JARS+=("$p"); fi
          done
          echo "release jars (${#JARS[@]}):"
          for j in "${JARS[@]}"; do echo "  ${j##*/}"; done
```

设计要点：下游 `gh release create/edit … "${JARS[@]}"` 与正文附件清单循环**完全复用** `JARS`，因此"四线附件"只需改这一处；P0 缺失仍硬红（保留原"宁可失败也不发缺件"语义），而 26.1.2 / fabric 目录缺失只让该线缺席。

### 2.3 YAML 解析与语法证据

系统 python 受 PEP 668 保护且无 pyyaml，已装到仓库外（`--target /c/Users/xmace/AppData/Local/Temp/dsh-yaml`，pyyaml 6.0.3），解析命令：

```
export PYTHONPATH=/c/Users/xmace/AppData/Local/Temp/dsh-yaml
python -c "import yaml;yaml.safe_load(open('.github/workflows/build.yml',encoding='utf-8'))"
```

实际输出：

```
PYAML 6.0.3
safe_load: OK
top-level keys: ['name', True, 'jobs']
steps count: 21
  01 Checkout repository                                   uses=actions/checkout@v4
  …
  11 Check lang sync (fabric-1.20.1)                       if=hashFiles('fabric-1.20.1/gradle.properties') != ''
  12 Setup Python (changelog parity gate)                  uses=actions/setup-python@v5
  …
  17 Upload Astral Dice JARs (neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2) uses=actions/upload-artifact@v4 artifact=astral_dice-jars paths=["neoforge-1.21.1/build/libs/astral_dice-*.jar", "forge-1.20.1/build/libs/astral_dice-*.jar", "neoforge-26.1.2/build/libs/astral_dice-*.jar"]
  18 Upload Astral Dice JARs (fabric-1.20.1)               if=hashFiles('fabric-1.20.1/gradle.properties') != '' uses=actions/upload-artifact@v4 artifact=astral_dice-jars-fabric paths=["fabric-1.20.1/build/libs/astral_dice-*.jar"]
  19 Read base version
  20 Create Git tag (release branch push only)             if=github.event_name == 'push' && github.ref == 'refs/heads/multi-main' && steps.version.outputs.skip_tag == '0'
  21 Create/Update GitHub Release (four JARs, notes from release/<tag>/) if=startsWith(github.ref, 'refs/tags/') || (…)
```

> `top-level keys` 里的 `True` 是 pyyaml 按 YAML 1.1 把 `on:` 解析成布尔键的已知行为，**不是错误**；jobs.build 结构完整、21 步全部就位。

每个 `run:` 块单独落盘后 `bash -n`：

```
  step 07 bash -n rc=0   …   step 21 bash -n rc=0
run blocks: 12  failures: 0
```

### 2.4 「fabric 目录不存在」不退化为硬失败的仿真证据

把新 `JARS` 片段从 YAML 里原样抽出，在临时目录用 `bash -e` 跑 6 个场景（不碰仓库）：

| 场景 | rc | 收集到 |
|---|---|---|
| 四线 jar 齐备 | 0 | 4 件（neo1.21.1 / forge / neo26.1.2 / fabric） |
| `fabric-1.20.1/build/libs` 目录**不存在** | **0** | 3 件（fab 缺席，不报错） |
| `neoforge-26.1.2/build/libs` 目录**不存在** | **0** | 3 件（26.1.2 缺席，不报错） |
| P0 `forge-1.20.1` jar 缺失 | **1** | 输出 `::error::required P0 jar not found: forge-1.20.1/build/libs/astral_dice-*.jar` |
| P0 `neoforge-1.21.1` jar 缺失 | **1** | 同上（对应路径） |
| 混入旧 `1.5.0-rc.6 / rc.7` 残留 | 0 | 6 件（**松散 glob 会捎旧件**，见 §5 建议 1） |

---

## 3. 任务 B：本地复现 CI 守门（顺序与 `.github/workflows/build.yml` 第 112–166 行一致）

### 3.1 逐条 exit code

| # | 守门 | exit code | 关键输出 |
|---|---|---|---|
| A1 | `tools/check_lang_sync.ps1 -LangDir neoforge-1.21.1/…/lang` | **0** | `OK: zh_cn/en_us/ja_jp 1142 keys 一致`（仅 1 条 `[WARN] tooltip.astral_dice.sign.jill_active` 结构差异） |
| A2 | `tools/check_lang_sync.ps1 -LangDir forge-1.20.1/…/lang` | **0** | 1142 keys，同上 WARN |
| A3 | `tools/check_lang_sync.ps1 -LangDir neoforge-26.1.2/…/lang` | **0** | 1166 keys，`[WARN] tooltip.astral_dice.sign.dorothy_passive` |
| A4 | `tools/check_lang_sync.ps1 -LangDir fabric-1.20.1/…/lang` | **0** | 1149 keys，同上 WARN |
| B | `python tools/check_changelog_parity.py` | **0** | `CHANGELOG_PARITY: OK sections=26 entries=785/785`（两条 P2 WARN 明确标注 `not a failure`：`## 1.3.0` 与 `## 2.0.0-SNAPSHOT.5` 的历史加粗排版差异） |
| C | `pwsh -NoProfile -File scripts/verify/verify_chip_acquisition.ps1` | **0** | `RESULT: PASS（问题 0）` |
| D1 | `verify_event_choice_exit.py` | **0** | `RESULT: PASS`（forge-10 `CR=0`） |
| D2 | `verify_event_choice_first_event_dispatchable.py` | **0** | `RESULT: PASS`（`EventTimerStore.start @L132 < effect().apply @L136`） |
| D3 | `verify_event_choice_first_event_dispatchable_forge.py` | **0** | `RESULT: PASS`（`@L130 < @L134`） |
| D4 | `verify_event_timer_stack.py` | **0** | `RESULT: PASS` |
| D5 | `verify_event_timer_stack_forge.py` | **0** | `RESULT: PASS` |
| D6 | `verify_dice_combat_attrless_guard.py` | **0** | `RESULT: PASS`（含 `forge…DiceCombatEvents.java: CR bytes = 0`） |
| **D7** | `verify_dice_combat_attrless_guard_p1.py` | **1** | **1 条子断言 FAIL（行尾），其余 22 条 OK —— 见 3.2 / 3.3** |
| E | `python tools/verify_sign_display_names.py`（本次额外单跑的命名门禁） | **0** | `GATE: PASS`（四线 1149 / 1166 / 1142 keys 对齐，79250 个检查点） |

> A1–A4 / E 的输出在本机控制台里中文出现乱码（`�ṹ��ǲ�һ��`），是 pwsh 输出经 MSYS 管道时的 GBK/UTF-8 转码问题，**不影响 exit code 与判定**；同一脚本的 `OK:` / `GATE: PASS` 结论可读。

### 3.2 D7 失败原始输出（尾部 30 行；该脚本总输出 25 行，即全量）

```
[root] D:\MCProject\astral_dice_multiloader
[target:neo-26.1.2] D:\MCProject\astral_dice_multiloader\neoforge-26.1.2\src\main\java\com\merlinkitsune\astral_dice\combat\DiceCombatEvents.java
[target:fabric-1.20.1] D:\MCProject\astral_dice_multiloader\fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\combat\DiceCombatEvents.java


[OK] neo-26.1.2-01 no unguarded mob.getAttributeValue(ATTACK_DAMAGE)  [hits [none] (expected [none]); resolveMobMeleeAttack @ combat/DiceCombatEvents.java:L1037-L1105]
[OK] neo-26.1.2-02 C1: AttributeInstance null-check (attrless flag)  [AttributeInstance mobAttackAttr = mob.getAttribute(...) @ [L1076, L1140]; attrless = mobAttackAttr == null @ [L1077]]
[OK] neo-26.1.2-03 C2: attrless branch exists, proxy = this hit's vanilla damage  [proxy 'event.getOriginalDamage()' @ [L1079] (inside resolveMobMeleeAttack: [L1079])]
[OK] neo-26.1.2-04 magicAbsorbFactor NOT skipped on the attrless path  [magicAbsorbFactor( @ [L1095]; gated-by-attrless: block=False tern=False]
[OK] neo-26.1.2-04b in-code criterion note above magicAbsorbFactor  [markers ok/ok within 10 lines above combat/DiceCombatEvents.java:L1095]
[OK] neo-26.1.2-05 C1 early return precedes any attribute value read  [enchantedMobAttack @ combat/DiceCombatEvents.java:L1138-L1150: null-return @ combat/DiceCombatEvents.java:L1141 < getValue @ combat/DiceCombatEvents.java:L1144]
[OK] neo-26.1.2-06 player-side getAttributeValue(ATTACK_DAMAGE) intact  [player.getAttributeValue(ATTACK_DAMAGE) @ [L613]]
[OK] neo-26.1.2-09 line endings: LF only (crlf=0, bare_cr=0)  [LF-only baseline: crlf=0, bare_cr=0, cr_bytes=0 (expected cr_bytes=0; index and worktree are both LF in this repo)]
[OK] fabric-1.20.1-01 no unguarded mob.getAttributeValue(ATTACK_DAMAGE)  [hits [none] (expected [none]); resolveMobMeleeAttack @ combat/DiceCombatEvents.java:L1196-L1274]
[OK] fabric-1.20.1-02 C1: AttributeInstance null-check (attrless flag)  [AttributeInstance mobAttackAttr = mob.getAttribute(...) @ [L1235]; attrless = mobAttackAttr == null @ [L1236]]
[OK] fabric-1.20.1-03 C2: attrless branch exists, proxy = this hit's vanilla damage  [proxy 'event.getAmount() / <victimFactor>' @ [L1245] (inside resolveMobMeleeAttack: [L1245]); divide-by-factor guard @ [L1244]]
[OK] fabric-1.20.1-04 magicAbsorbFactor NOT skipped on the attrless path  [magicAbsorbFactor( @ [L1263]; gated-by-attrless: block=False tern=False]
[OK] fabric-1.20.1-04b in-code criterion note above magicAbsorbFactor  [markers ok/ok within 10 lines above combat/DiceCombatEvents.java:L1263]
[OK] fabric-1.20.1-05 C1 early return precedes any attribute value read  [attrless branch @ combat/DiceCombatEvents.java:L1237; getValue( inside it = absent (expected)]
[OK] fabric-1.20.1-06 player-side getAttributeValue(ATTACK_DAMAGE) intact  [player.getAttributeValue(ATTACK_DAMAGE) @ [L760]]
[FAIL] fabric-1.20.1-09 line endings: LF only (crlf=0, bare_cr=0)  [LF-only baseline: crlf=2218, bare_cr=0, cr_bytes=2218 (expected cr_bytes=0; index and worktree are both LF in this repo)]
[OK] neo-26.1.2-08 negative control (embedded pre-fix fixture must FAIL)  [fixture=d91fa390:neoforge-26.1.2 (64 lines); verdict on fixture=RED (expected); failing sub-assertions=['01 no unguarded mob.getAttributeValue(ATTACK_DAMAGE)', '02 C1: AttributeInstance null-check (attrless flag)', "03 C2: attrless branch exists, proxy = this hit's vanilla damage", '04b in-code criterion note above magicAbsorbFactor', '05 C1 early return precedes any attribute value read']; 01 crash-read detector red=True]
[OK] fabric-1.20.1-08 negative control (embedded pre-fix fixture must FAIL)  [fixture=d91fa390:fabric-1.20.1 (56 lines); verdict on fixture=RED (expected); failing sub-assertions=['01 no unguarded mob.getAttributeValue(ATTACK_DAMAGE)', '02 C1: AttributeInstance null-check (attrless flag)', "03 C2: attrless branch exists, proxy = this hit's vanilla damage", '04b in-code criterion note above magicAbsorbFactor', '05 C1 early return precedes any attribute value read']; 01 crash-read detector red=True]

RESULT: FAIL
```

**失败原因判断（脚本语义）**：`fabric-1.20.1-09` 只检查 `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java` 的行尾，要求 `cr_bytes == 0`（仓库基线声明 index 与 worktree 都是 LF）。实测该文件 CR 字节 **2218**，故判 FAIL；**功能类子断言（01–06、04b、negative control）全部通过**，说明属性缺席守卫本身没被破坏。

### 3.3 归因：既有技术债（本地工作树 EOL 漂移），非本次改动引入

只读取证（未动任何文件）：

| 证据 | 命令 | 结果 |
|---|---|---|
| 该文件状态 | `git status --porcelain -- fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java` | ` M `（**他人**未提交改动的文件，不是我改的） |
| 索引/工作树行尾 | `git ls-files --eol -- <file>` | `i/lf    w/crlf  attr/`（索引 LF、工作树 CRLF、无 gitattributes 规则） |
| HEAD 内容行尾 | `git show HEAD:<file> \| python -c "…count(b'\\r')"` | 122632 B，**CR=0**、CRLF=0 ⇒ **提交进去的内容是纯 LF** |
| 工作树内容行尾 | `python -c "open(…,'rb').read()"` | 145426 B，**CR=2218**、CRLF=2218 ⇒ 整文件被 CRLF 重写 |
| 是否只有行尾差异 | `git diff --numstat` vs `git diff --ignore-cr-at-eol --numstat` | 两者都是 **338 / 25** ⇒ 除行尾漂移外**还有真实内容改动**（1.5.0 进行中的改动） |
| 宿主行尾配置 | `git config --get core.autocrlf` | `true` |
| 全仓行尾画像 | `git ls-files --eol \| awk '{print $1,$2}' \| sort \| uniq -c` | `i/lf w/crlf` **3197** 个文件，`i/lf w/lf` 2120 个，`w/mixed` 24 个 ⇒ 本机工作树大面积 CRLF 是**常态**，LF 干净反而是少数 |
| 该文件最后提交 | `git log -1 --format='%h %ci %s' -- <file>` | `58c51fd8 2026-10-30 00:27:27 +0800 fix(combat): 四线补属性缺席守卫…`（该提交进库后 blob 仍是 LF） |
| 文件 mtime | `stat -c '%y %s %n'` | fabric 侧 `2026-11-01 23:13`，neo-26.1.2 侧 `2026-11-01 23:30`（后者通过 09 断言、为 LF） |

结论链：

1. **不是我的改动造成的**：我全程只写过 `.github/workflows/build.yml`；该文件 `git status` 显示的是**他人**的进行中改动（` M `）。
2. **不是提交内容缺陷**：HEAD blob `CR=0`，1.5.0 相关提交 `58c51fd8` 入库内容是 LF。红的是**工作树**这一份被重写成 CRLF 的副本。
3. **不会在 CI 上复现**：`.github/workflows/build.yml:155-156` 确实把该脚本纳入守门，但 job 是 `runs-on: ubuntu-latest` + `actions/checkout@v4`，Linux 上 `core.autocrlf=false`，检出的是索引里的 LF 字节 ⇒ 同一脚本在 CI 上 `cr_bytes=0`，该子断言为 [OK]。
4. 定性：**既有技术债（Windows 宿主 `core.autocrlf=true` + 工作树 CRLF 漂移）**，属"本地运行环境与 CI 运行环境不一致"这一类，不是 1.5.0 功能改动引入的回归。按任务纪律**未修改门脚本、未改判据、未 normalize 该文件**（它属他人未提交改动，红线禁止触碰）。

### 3.4 其余 WARN 的处理

- A1–A4 的 `[WARN] tooltip.astral_dice.sign.jill_active` / `…dorothy_passive`：占位符/参数/颜色码结构差异，**脚本判为 WARN 非 FAIL**，rc=0；未改 lang 文件（不在授权范围）。
- B 的两条 `WARN P2`：脚本自述 `historical style difference; not a failure`，rc=0。

---

## 4. 未做项与理由

| 未做 | 理由 |
|---|---|
| 未跑 Gradle / 未验证"四线 jar 真能被构建出来" | 任务硬纪律禁止（他人 `gradlew` / `KnotServer` / `KnotClient` 正在跑，会互相踩踏）。产物存在性只能以静态证据支撑：四线 `archivesName='astral_dice'`、四线 `mod_version=1.5.0+<平台>`，故各线 `astral_dice-*.jar` glob 只会命中本线产物 |
| 未在真 CI 上跑一遍 Action | 需要 push/tag，任务禁止；改用了 `yaml.safe_load` + `bash -n` + JARS 片段 6 场景仿真 |
| 未修 D7 的失败 / 未把该文件转成 LF | 门脚本与判据禁止改；该文件是他人未提交改动，红线禁止 add/commit/checkout/normalize |
| 未改 `release/` 发布说明、未建 tag、未改版本号 | 不在授权范围（只允许写 build.yml） |
| 未做游戏测试 | 任务禁止 |

---

## 5. 建议（均**未落盘**，供 lead 决策）

1. **附件 glob 的"旧件"风险（本地/复用 runner 场景）**：`astral_dice-*.jar` 会连带命中同目录下的历史构建件（本机 `neoforge-26.1.2/build/libs` 有 14 个，含 `1.2.1-beta`…`1.5.0-rc.7`；三线各有 `1.5.0-rc.6/rc.7`）。GitHub 托管的全新 runner 上 `build/libs` 只有本次产物，**当前 CI 不受影响**；若将来改用自托管 / 带缓存的 runner，建议把 glob 收紧为 `astral_dice-<version>+*.jar`（需要从 `gradle.properties` 读版本，属独立改动）。
2. **行尾一致性**：建议在 `.gitattributes` 固定 `*.java text eol=lf`，可一次性消除 §3.3 这类"本地红、CI 绿"的噪声；同时避免提交时把整文件变成 CRLF 造成的巨型 diff（该文件当前 diff 338/25 里混着 2218 行行尾变化）。`.gitattributes` 是他人维护范围，未动。
3. **artifact 命名**：fabric 走独立 artifact 名（`astral_dice-jars-fabric`）是"两步不同守卫"的必然结果；若下游有依赖单一名 artifact 的脚本，需要一并调整（当前仓内未发现此类消费方）。

---

## 附：证据文件位置（均在仓库外，未污染工作树）

- `C:\Users\xmace\AppData\Local\Temp\dsh-gates.json` —— 14 条守门的 rc + 输出尾部
- `C:\Users\xmace\AppData\Local\Temp\dsh-run-gates.py` —— 守门驱动（只读调用）
- `C:\Users\xmace\AppData\Local\Temp\dsh-yaml-evidence.py` / `dsh-bashn.py` —— YAML 解析 + `bash -n` 取证
- `C:\Users\xmace\AppData\Local\Temp\dsh-buildyml-edit.py` —— 8 处按行替换的编辑脚本（含逐块断言）
- `C:\Users\xmace\AppData\Local\Temp\dsh-ci-sim.py` —— JARS 片段 6 场景仿真
