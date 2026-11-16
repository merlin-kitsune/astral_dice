# CI 与文档一致性修复报告（评审跟进：P3 逻辑缺陷 + P2 口径矛盾）

- 仓库：`F:/MCProject/astral_dice_multiloader`
- 分支 / HEAD：`multi-main` @ `2bc97366b07e22ef4d66b2f794cd3f1442fe8b48`（2026-11-02 18:57:08 +0800，`docs(agent): require user authorization before any release action`）
- 改动文件（仅 2 个，均为本工作树**未提交**改动）：
  - `.github/workflows/build.yml`（纯 LF，691 行）
  - `docs/agent/AGENTS-dsh.md`（纯 CRLF，317 行）
- 纪律执行：未 commit / push / 打 tag / 建 Release / 上传 CurseForge / 跑 Gradle；仿真中的 `gh` 全程走 `work/_ci_sim/_binstub/gh`（`gh_stub.sh`），**真实 gh 一次未调用**。

---

## 1. 改动清单

### 1.1 缺陷 1 —— unified 判据：把「是否已设过基准」改成独立布尔量，并把「读不到 mod_version」升为异常

判据口径现在**两处一致**：都用独立布尔量判基准、都把空 `mod_version` 当异常、都只看在场线。

| 位置 | 内容 | 改前 | 改后 |
|---|---|---|---|
| `.github/workflows/build.yml:208` | Report step 名 | `Report per-line mod_version (consistency gate - report only, never fails)` | `Report per-line mod_version (consistency gate - mixed = warn, missing mod_version = fail)` |
| `.github/workflows/build.yml:230,236-266` | Report：四线三态 | `if [[ -f "$props" ]]; then 读版本; fi` —— 文件不存在与键缺失**同一个分支**，都当「该线没这条线」 | `declare -a T_LINE=() T_VER=() T_BASE=() T_STATE=()`；`line_state` ∈ {absent, present, noversion}，**两个语义不同分支拆开**并加注释 |
| `.github/workflows/build.yml:272,277-297` | Report：基准哨兵 | `if [[ "$PRESENT" -eq 0 ]]; then REF_BASE=<首线base>; fi`（用计数当哨兵） | `REF_SET=0` 独立布尔量；仅 `absent` 才 `continue`，`noversion` 进 `BAD_VERSION`、不参与 unified |
| `.github/workflows/build.yml:302-320` | Report：表格 + 判红 | 空版本打 `(absent at this ref)`，永不失败 | `case` 三态分支（`(absent at this ref)` / `**MISSING KEY**` / 正常行）；`BAD_VERSION` 非空 ⇒ 逐线 `::error::` + 汇总 `::error::` + `exit 1` |
| `.github/workflows/build.yml:495-496,510-513` | Release：基准哨兵 | `REF_BASE=""` + `if [[ -z "$REF_BASE" ]]; then REF_BASE="$b"; fi`（**拿空串当「尚未设置」的哨兵**） | `REF_SET=0` + `if [[ "$REF_SET" -eq 0 ]]; then REF_BASE="$b"; REF_SET=1; fi`（**无条件赋值，不看值是否为空**） |
| `.github/workflows/build.yml:496,501-503,520-529` | Release：空版本 | 首条在场线 base 为空 ⇒ 基准永远为空 ⇒ 之后任何非空 base 都「不等」⇒ `UNIFIED=0` ⇒ 走混版 `exit 0`（`:478-482` 的 cannot read 永远到不了） | 空版本进 `EMPTY_VER`；在混版判定**之前**的防御分支逐线 `::error::` + 汇总 `::error::` + `exit 1` |

（表中「位置」列的行号按**改后**文件，691 行口径；「改前」列里提到的 `:478-482` 按改动前 617 行口径，指旧文件里那段永远走不到的 `cannot read mod_version` 分支。）

`:550-553` 原 pass2 空版本分支保留为**第二道防线**（已加注释），行为不变（仍 `::error::` + 计入 `MISSING`）。

混版分支（`:530-540`）**未改**：四线对照表 + 政策说明 + `exit 0` + gh 0 次 + 不打 tag。

### 1.2 缺陷 2 —— 附件口径：文档原位补历史裁决 + 代码注释交叉引用

| 位置 | 改动 |
|---|---|
| `docs/agent/AGENTS-dsh.md:158`（`## 📍 生效红线区` 内，小节自 L155 起） | 在旧红线句 `需要 P1 发布时必须由用户逐次点名下达。` 之后**原位插入**一条历史裁决记录（不删旧文），见下 |
| `.github/workflows/build.yml:437-440` | 附件口径注释后追加 4 行交叉引用，指向该文档小节名与小节内那条裁决 |

插入文本（逐字；**行内插入、未新增换行** ⇒ CRLF 纯度不受影响）：

```
 **2026-11-02 用户裁决（取代本条后半）**：四线 jar 允许放入同一个 GitHub Release（按版本号打 tag、全部正式版）；CF 侧 `fabric` / `neoforge-26.1.2` 仍受 `--allow-p1` 显式放行闸门约束（保留为防误传互锁），上传时必须显式加该参数。
```

交叉引用注释（`.github/workflows/build.yml:437-440`）：

```
          #   📌 口径正本：`docs/agent/AGENTS-dsh.md`〈📍 生效红线区 —— 原文逐字〉里那条
          #      「**2026-11-02 用户裁决（取代本条后半）**」—— 旧红线「P1 两线…禁止上传、禁止建 Release/tag、
          #      禁止作为 P0 Release 的附件」的后半已被该裁决取代（CF 侧仍保留 `--allow-p1` 显式放行闸门）。
          #      改本段口径前先读那里，两处必须一致（这里与文档互相引用）。
```

`tools/curseforge_upload.py` 的 `guard_p0_only` **未改动**（该文件在本会话开始前已有他人的未提交改动，mtime 19:01:41 早于本会话启动 19:33，见 §2.4）。

### 1.3 前后 diff

`.github/workflows/build.yml`（`work/_ci_sim/baseline/build.yml.before` → 工作区；该 baseline 即本次改动的起点）：

```diff
diff --git a/work/_ci_sim/baseline/build.yml.before b/.github/workflows/build.yml
index 9f21342f..80d24342 100644
--- a/work/_ci_sim/baseline/build.yml.before
+++ b/.github/workflows/build.yml
@@ -205,18 +205,20 @@ jobs:
           name: astral_dice-jars-fabric
           path: fabric-1.20.1/build/libs/astral_dice-*.jar
 
-      - name: Report per-line mod_version (consistency gate - report only, never fails)
+      - name: Report per-line mod_version (consistency gate - mixed = warn, missing mod_version = fail)
         id: version
         # 2026-11-02 新增（四线混版修复）。职责有二：
         #   ① 把四线各自读到的 mod_version 与「是否统一」打成一张表（VERSIONS: unified=1/0 + 每线一行），
         #      版本树长什么样一眼可见，不必等 Release step 的 missing jar 再反向推断；
         #   ② 输出 unified，供 Create Git tag 的门禁使用（混版 ⇒ 不打 tag）。
-        # ⛔ 本 step **只报告、永不失败**：对混版提交 exit 1 就等于让「小版本 / hotfix 提交」继续红，
-        #    与发布口径（见下）相悖。真正要红的两件事都不在这里：
-        #      · 「统一版本但某线缺 jar」⇒ 留在 Release step 内部（那里才有 missing jar 判定）；
-        #      · 「版本树不可解析」⇒ 也只在 Release step 里判红。
-        # unified 的判据与下方 Release step **同一口径**（两处都按 mod_version 去掉 + 之后的部分比较），
-        #    改动需两边同步。
+        # ⛔ 本 step 对**混版**提交永不失败：混版 exit 1 就等于让「小版本 / hotfix 提交」继续红，
+        #    与发布口径（见下）相悖。但 2026-11-02 复查后补了一条**确实要红**的判据（其余照旧）：
+        #      · 任意一条**在场**线读不到 `mod_version`（文件在、`mod_version=` 键缺失或为空）
+        #        ⇒ ::error:: 点名该线 + exit 1 —— 空版本不是「该 ref 没这条线」，不能静默跳过；
+        #      · 「统一版本但某线缺 jar」仍留在 Release step 内部（那里才有 missing jar 判定）；
+        #      · 「ref 上没有任何子项目」也仍由 Release step 判红。
+        # unified 的判据与下方 Release step **同一口径**（两处都按 mod_version 去掉 + 之后的部分比较、
+        #    都用独立布尔量判「基准是否已设过」、都把空 mod_version 当异常），改动需两边同步。
         run: |
           # 顺序与下方 Release step 的 LINES 数组一致；格式 线名|子项目目录。
           LINES=(
@@ -225,21 +227,38 @@ jobs:
             "neoforge-26.1.2|neoforge-26.1.2"
             "fabric-1.20.1|fabric-1.20.1"
           )
-          declare -a T_LINE=() T_VER=() T_BASE=()
+          declare -a T_LINE=() T_VER=() T_BASE=() T_STATE=()
           for entry in "${LINES[@]}"; do
             line="${entry%%|*}"
             proj="${entry##*|}"
             props="$proj/gradle.properties"
             line_ver=""
-            # 该 ref 上没有这个子项目时不判红（老 tag 上 fabric / 26.1.2 目录确实不存在），
-            # 与 Release step 的 git cat-file 判据同口径；本 step 只作报告，故只看工作区。
-            if [[ -f "$props" ]]; then
+            line_state=""    # absent / present / noversion —— 三态必须分开，见下
+            # ⛔ 2026-11-02 复查修复：两个语义不同的分支必须**拆开**，不能再混成一个 `[[ -z ... ]]` 判断：
+            #   ① absent    —— 该 ref 的提交里本来就没有这个子项目（老 tag 上 fabric / 26.1.2 目录确实不存在）
+            #                  ⇒ 与 Release step 的 git cat-file 判据同口径，**跳过、不判红**（本 step 只看工作区，故用 -f）；
+            #   ② noversion —— **文件在、但 `mod_version=` 键缺失或为空** ⇒ **异常**（不是「没这条线」），
+            #                  由本步（一致性门禁）在构建早期 ::error:: + exit 1 暴露（见下方 BAD_VERSION）。
+            #   旧写法把 ② 也当成 ① 跳过 ⇒ 空版本不进 unified ⇒ Report 可能判 unified=1（打 tag），
+            #   而 Release step 那边空 base 会把「基准是否已设过」的哨兵带偏、判成混版 exit 0（不发 Release）
+            #   ⇒ 同一棵树上出现「有 tag 没 Release」。
+            if [[ ! -f "$props" ]]; then
+              line_state="absent"
+            else
               raw=$(grep -m1 '^mod_version=' "$props" || true)
               line_ver="${raw#*=}"
               line_ver="${line_ver%$'\r'}"   # 万一 properties 是 CRLF，别把 \r 带进版本串
             fi
+            if [[ "$line_state" != "absent" ]]; then
+              if [[ -z "$line_ver" ]]; then
+                line_state="noversion"
+              else
+                line_state="present"
+              fi
+            fi
             T_LINE+=("$line")
             T_VER+=("$line_ver")
+            T_STATE+=("$line_state")
             if [[ -n "$line_ver" ]]; then
               T_BASE+=("${line_ver%%+*}")
             else
@@ -250,13 +269,26 @@ jobs:
           #   只看在场线，是为了保住老的「该 ref 上本来就没有某子项目」豁免（否则老 tag 会被误判成混版）。
           UNIFIED=1
           REF_BASE=""
+          REF_SET=0        # 独立布尔量：基准是否**已设过**（不看值是否为空；两处同口径）
           PRESENT=0
+          BAD_VERSION=()   # 在场线里读不到 mod_version 的线名
           for i in "${!T_LINE[@]}"; do
-            if [[ -z "${T_VER[$i]}" ]]; then
+            st="${T_STATE[$i]}"
+            # 只有「该 ref 上本来就没有这个子项目」才跳过；缺键（noversion）不参与 unified 计算，
+            #   而是收集起来点名判红 —— 「混版」与「读不到版本」是两件事，不能合并处理。
+            if [[ "$st" == "absent" ]]; then
               continue
             fi
-            if [[ "$PRESENT" -eq 0 ]]; then
+            if [[ "$st" == "noversion" ]]; then
+              BAD_VERSION+=("${T_LINE[$i]}")
+              continue
+            fi
+            # ⛔ 基准判据用**独立布尔量**，不能靠 `[[ -z "$REF_BASE" ]]` 当哨兵：一旦首条在场线的
+            #    base 恰为空串，REF_BASE 永远是空 ⇒ 后面任何非空 base 都「不等」⇒ 被静默判成混版。
+            #    空值现在根本走不到这里（上方已摘进 BAD_VERSION）。
+            if [[ "$REF_SET" -eq 0 ]]; then
               REF_BASE="${T_BASE[$i]}"
+              REF_SET=1
             fi
             if [[ "${T_BASE[$i]}" != "$REF_BASE" ]]; then
               UNIFIED=0
@@ -268,12 +300,24 @@ jobs:
           fi
           echo "VERSIONS: unified=$UNIFIED"
           for i in "${!T_LINE[@]}"; do
-            if [[ -z "${T_VER[$i]}" ]]; then
-              printf '  %-16s mod_version=(absent at this ref)\n' "${T_LINE[$i]}"
-            else
-              printf '  %-16s mod_version=%-32s base=%s\n' "${T_LINE[$i]}" "${T_VER[$i]}" "${T_BASE[$i]}"
-            fi
+            case "${T_STATE[$i]}" in
+              absent)
+                printf '  %-16s mod_version=(absent at this ref)\n' "${T_LINE[$i]}" ;;
+              noversion)
+                printf '  %-16s mod_version=**MISSING KEY** (gradle.properties exists but has no mod_version= line)\n' "${T_LINE[$i]}" ;;
+              *)
+                printf '  %-16s mod_version=%-32s base=%s\n' "${T_LINE[$i]}" "${T_VER[$i]}" "${T_BASE[$i]}" ;;
+            esac
           done
+          # 任意一条**在场**线读不到 mod_version ⇒ 异常（不是缺席、也不是混版）⇒ 本一致性门禁直接判红，
+          # 让问题在构建早期就暴露，而不是留到 Release step 里被当成混版静默跳过。
+          if [[ ${#BAD_VERSION[@]} -gt 0 ]]; then
+            for bad_line in "${BAD_VERSION[@]}"; do
+              echo "::error::mod_version missing/empty for $bad_line: gradle.properties exists but has no mod_version= key"
+            done
+            echo "::error::aborting: ${#BAD_VERSION[@]} present line(s) without a readable mod_version (${BAD_VERSION[*]}) - unreadable version is NOT 'line absent at this ref'"
+            exit 1
+          fi
           if [[ "$UNIFIED" -eq 1 ]]; then
             echo "  => all present lines share base $REF_BASE - unified mainline version"
           else
@@ -390,6 +434,10 @@ jobs:
           #   四线（neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / fabric-1.20.1）**全部硬校验**，
           #   任何一条线缺件 ⇒ ::error:: + exit 1 —— 不再有「静默缺席、Release 照样成功」的侥幸。
           #   唯一豁免见下：**该 ref 的提交里本来就没有这个子项目**（老 tag 上 fabric / 26.1.2 目录不存在）。
+          #   📌 口径正本：`docs/agent/AGENTS-dsh.md`〈📍 生效红线区 —— 原文逐字〉里那条
+          #      「**2026-11-02 用户裁决（取代本条后半）**」—— 旧红线「P1 两线…禁止上传、禁止建 Release/tag、
+          #      禁止作为 P0 Release 的附件」的后半已被该裁决取代（CF 侧仍保留 `--allow-p1` 显式放行闸门）。
+          #      改本段口径前先读那里，两处必须一致（这里与文档互相引用）。
           # 每线三元组：`线名|子项目目录|优先级`。
           LINES=(
             "neoforge-1.21.1|neoforge-1.21.1|P0"
@@ -444,17 +492,41 @@ jobs:
           echo "per-line versions (present at $SHA):"
           UNIFIED=1
           REF_BASE=""
+          REF_SET=0        # 独立布尔量：基准是否**已设过**（不看值是否为空；⛔ 不可再拿 [[ -z $REF_BASE ]] 当哨兵）
+          EMPTY_VER=()     # 在场线里读不到 mod_version 的「线名|props」——独立收集，下面点名判红
           for i in "${!P_LINE[@]}"; do
             v="${P_VER[$i]}"
             b="${v%%+*}"
             printf '  %-16s mod_version=%-34s base=%s\n' "${P_LINE[$i]}" "$v" "$b"
-            if [[ -z "$REF_BASE" ]]; then REF_BASE="$b"; fi
+            if [[ -z "$v" ]]; then
+              EMPTY_VER+=("${P_LINE[$i]}|${P_PROJ[$i]}/gradle.properties")
+            fi
+            # ⛔ 2026-11-02 复查修复：基准判据必须是**独立布尔量**，不能拿 `[[ -z "$REF_BASE" ]]` 当哨兵。
+            #    旧写法在「第一条在场线读出的 base 恰为空串」（该线 mod_version 缺键）时，永远认为
+            #    基准还没设过 ⇒ 之后任何非空 base 都「不等」⇒ UNIFIED=0 ⇒ 走混版 exit 0（静默判绿），
+            #    下方「cannot read mod_version」分支永远走不到；而上方 Report 一致性门禁却可能判
+            #    unified=1 并打出 tag ⇒ 同一棵树上出现「有 tag 没 Release」。
+            #    现在：空值根本不参与 unified 计算，而是进 EMPTY_VER，由下方防御分支判红。
+            if [[ "$REF_SET" -eq 0 ]]; then
+              REF_BASE="$b"
+              REF_SET=1
+            fi
             if [[ "$b" != "$REF_BASE" ]]; then UNIFIED=0; fi
           done
           if [[ ${#P_LINE[@]} -eq 0 ]]; then
             echo "::error::no subproject found at this ref - nothing to publish"
             exit 1
           fi
+          # 防御分支（与 Report 一致性门禁同一口径）：任意一条**在场**线读不到 mod_version ⇒ 异常 ⇒ 判红。
+          #   正常流程里 Report step 更早就 exit 1 了；这里保留是为了「即便前面那道门禁被跳过 / 被改坏」，
+          #   本步也绝不把空版本当成「该线缺席」而静默判绿（旧行为正是这条路）。
+          if [[ ${#EMPTY_VER[@]} -gt 0 ]]; then
+            for entry in "${EMPTY_VER[@]}"; do
+              echo "::error::cannot read mod_version for ${entry%%|*}: ${entry#*|} has no mod_version line"
+            done
+            echo "::error::aborting release: ${#EMPTY_VER[@]} present line(s) without a readable mod_version (${EMPTY_VER[*]}) - refusing to treat an unreadable version as a mixed-version build"
+            exit 1
+          fi
           if [[ "$UNIFIED" != "1" ]]; then
             echo "::notice::mixed per-line mod_version detected - SKIPPING GitHub Release (by policy)"
             echo "  四线 mod_version 去掉 + 之后不一致 ⇒ 这是**小版本 / hotfix 提交**（只有部分加载器线升号）。"
@@ -476,6 +548,8 @@ jobs:
             prio="${P_PRIO[$i]}"
             ver="${P_VER[$i]}"
             if [[ -z "$ver" ]]; then
+              # 第二道防线（2026-11-02 复查）：上方 EMPTY_VER 已按「异常」判红；此处保留，
+              # 防止将来那段被改坏后，空版本又悄悄落到 missing jar 的语义里被放过。
               echo "::error::cannot read mod_version for $line: $proj/gradle.properties has no mod_version line"
               MISSING+=("$line")
               continue

```

`docs/agent/AGENTS-dsh.md`（行级：**只有 L158 变化**，317 → 317 行不变）：

```
CRLF lines before/after: 317/317; differing lines: [158]
line 158 opcode=insert
  context BEFORE: ...⇒ 禁止上传、禁止建 Release/tag、禁止作为 P0 Release 的附件、禁止对外交付；需要 P1 发布时必须由用户逐次点名下达。**② tag 与 Release 命名红线（同批裁决,强制）**：GitHub Release 的 tag **只按 mod 版本**创...
  context AFTER : ...⇒ 禁止上传、禁止建 Release/tag、禁止作为 P0 Release 的附件、禁止对外交付；需要 P1 发布时必须由用户逐次点名下达。 **2026-11-02 用户裁决（取代本条后半）**：四线 jar 允许放入同一个 GitHub Release（按版本号打 tag、全部正式版）；CF 侧 `fabric` / `neoforge-26.1.2` 仍受 `--allow-p1` 显式放行闸门约束（保留为防误传互锁），上传时必须显式加该参数。**② tag 与 Release 命名红线（同批裁决,强制）**：GitHub Release 的 tag **只按 mod 版本**创...
  inserted text : 。 **2026-11-02 用户裁决（取代本条后半）**：四线 jar 允许放入同一个 GitHub Release（按版本号打 tag、全部正式版）；CF 侧 `fabric` / `neoforge-26.1.2` 仍受 `--allow-p1` 显式放行闸门约束（保留为防误传互锁），上传时必须显式加该参数

```

---

## 2. 验证

### 2.1 YAML 解析 + steps 数

```
$ python -c "import yaml;d=yaml.safe_load(open('.github/workflows/build.yml',encoding='utf-8'));print(len(d['jobs']))"
1

$ python work/_ci_sim/yaml_check.py
len(d["jobs"]) = 1
jobs = ['build']
job build: steps = 21
   1. Checkout repository
   2. Setup JDK 21 (1.21.1 NeoForge)
   3. Setup JDK 17 (1.20.1 Forge)
   4. Setup JDK 25 (26.1.2 NeoForge)
   5. Setup Gradle
   6. Checkout StarEngine (required prerequisite)
   7. Publish StarEngine to mavenLocal
   8. Check lang sync (neoforge-1.21.1)
   9. Check lang sync (forge-1.20.1)
  10. Check lang sync (neoforge-26.1.2)
  11. Check lang sync (fabric-1.20.1)
  12. Setup Python (changelog parity gate)
  13. Check CHANGELOG parity (en/zh)
  14. Check chip acquisition gate (P0)
  15. Check event-choice & combat static gates
  16. Build with Gradle (all targets)
  17. Upload Astral Dice JARs (neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2)
  18. Upload Astral Dice JARs (fabric-1.20.1)
  19. Report per-line mod_version (consistency gate - mixed = warn, missing mod_version = fail)
  20. Create Git tag (release branch push only)
  21. Create/Update GitHub Release (four JARs, notes from release/<tag>/)

```

### 2.2 仿真（`work/_ci_sim/`，7 个场景全绿；**gh 全程走桩**）

夹具与桩：
- `work/_ci_sim/run_sim.sh`：每个场景先跑 **Report**（`report_step.sh`）再跑 **Release**（`release_step.sh`），分别收集 exit code 与日志；fixture 造 `<proj>/gradle.properties` + `build/libs/astral_dice-<ver>.jar`，`git init` 后以 `GITHUB_SHA` 供 Release step 的 `git cat-file` 使用。
- `work/_ci_sim/extract_report_step.py`（**本次新增**）、`extract_release_step.py`：从 `build.yml` 按 step 名抽取 `run:` 正文、按 10 空格去缩进，逐字落成可执行脚本（仅替换模板表达式）。
- 新增场景：`missing-key`（某线 `gradle.properties` 在、但无 `mod_version=` 键）、`old-ref`（该 ref 上 fabric 子项目不存在）。

#### 场景 1：统一版本 + 4 jar ⇒ exit 0

```
==== scenario: unified-upload ====
-- fixture: per-line gradle.properties + built jars --
   neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              jars=astral_dice-1.5.0+neoforge_1.21.1.jar 
   forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 jars=astral_dice-1.5.0+forge_1.20.1.jar 
   neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              jars=astral_dice-1.5.0+neoforge_26.1.2.jar 
   fabric-1.20.1    mod_version=1.5.0+fabric_1.20.1                jars=astral_dice-1.5.0+fabric_1.20.1.jar 
-- run: REPORT step (consistency gate) --
VERSIONS: unified=1
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1            base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1               base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2            base=1.5.0
  fabric-1.20.1    mod_version=1.5.0+fabric_1.20.1              base=1.5.0
  => all present lines share base 1.5.0 - unified mainline version
-- report exit code: 0 (expected 0) --
-- run: bash -e -o pipefail release_step.sh --
publishing release for tag: 1.5.0
per-line versions (present at 4737ab3d222a2d35bb5fcd0dd7142ac347a76bf6):
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              base=1.5.0
  fabric-1.20.1    mod_version=1.5.0+fabric_1.20.1                base=1.5.0
all present lines share base 1.5.0 - unified mainline version, publishing release
jar ok (P0) neoforge-1.21.1: astral_dice-1.5.0+neoforge_1.21.1.jar
jar ok (P0) forge-1.20.1: astral_dice-1.5.0+forge_1.20.1.jar
jar ok (P1) neoforge-26.1.2: astral_dice-1.5.0+neoforge_26.1.2.jar
jar ok (P1) fabric-1.20.1: astral_dice-1.5.0+fabric_1.20.1.jar
release jars (4):
  astral_dice-1.5.0+neoforge_1.21.1.jar
  astral_dice-1.5.0+forge_1.20.1.jar
  astral_dice-1.5.0+neoforge_26.1.2.jar
  astral_dice-1.5.0+fabric_1.20.1.jar
::warning::player changelog not found: release/1.5.0/PLAYER_CHANGELOG_ZH.md
::warning::player changelog not found: release/1.5.0/PLAYER_CHANGELOG.md
::warning::no release/1.5.0/PLAYER_CHANGELOG*.md found — falling back to an attachment list
release notes: 207 bytes
uploading missing asset: astral_dice-1.5.0+neoforge_1.21.1.jar
uploading missing asset: astral_dice-1.5.0+forge_1.20.1.jar
uploading missing asset: astral_dice-1.5.0+neoforge_26.1.2.jar
uploading missing asset: astral_dice-1.5.0+fabric_1.20.1.jar
-- exit code: 0 (expected 0) --
-- gh stub calls: 7 --
gh release view 1.5.0
gh release edit 1.5.0 --title Astral Dice 1.5.0 --notes-file /f/MCProject/astral_dice_multiloader/work/_ci_sim/sim/unified-upload/runner_temp/astral-dice-release-notes-1.5.0.md --latest --prerelease=false
gh release view 1.5.0 --json assets --jq .assets[].name
gh release upload 1.5.0 neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar
gh release upload 1.5.0 forge-1.20.1/build/libs/astral_dice-1.5.0+forge_1.20.1.jar
gh release upload 1.5.0 neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar
gh release upload 1.5.0 fabric-1.20.1/build/libs/astral_dice-1.5.0+fabric_1.20.1.jar
-- assertions --
   PASS  report exit code == 0
   PASS  report says unified=1
   PASS  exit code == 0
   PASS  4 jars accepted (jar ok x4)
   PASS  4 uploads went out
   PASS  freeze path: no --clobber
   PASS  no release create in this path
RESULT: all assertions passed
```

#### 场景 2：混版（fabric = `1.5.0-hotfix`）⇒ exit 0、gh 0 次、打印 skip 说明、不打 tag

```
==== scenario: mixed ====
-- fixture: per-line gradle.properties + built jars --
   neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              jars=astral_dice-1.5.0+neoforge_1.21.1.jar 
   forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 jars=astral_dice-1.5.0+forge_1.20.1.jar 
   neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              jars=astral_dice-1.5.0+neoforge_26.1.2.jar 
   fabric-1.20.1    mod_version=1.5.0-hotfix+fabric_1.20.1         jars=astral_dice-1.5.0-hotfix+fabric_1.20.1.jar 
-- run: REPORT step (consistency gate) --
VERSIONS: unified=0
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1            base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1               base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2            base=1.5.0
  fabric-1.20.1    mod_version=1.5.0-hotfix+fabric_1.20.1       base=1.5.0-hotfix
  => bases differ - mixed / line-specific version: GitHub Release will be SKIPPED (CurseForge only)
::notice::mixed per-line mod_version - the Release step will skip publication (see its output)
-- report exit code: 0 (expected 0) --
-- run: bash -e -o pipefail release_step.sh --
publishing release for tag: 1.5.0
per-line versions (present at 57ae5811eda18f72f57fe95d6ae969b668b5939c):
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              base=1.5.0
  fabric-1.20.1    mod_version=1.5.0-hotfix+fabric_1.20.1         base=1.5.0-hotfix
::notice::mixed per-line mod_version detected - SKIPPING GitHub Release (by policy)
  四线 mod_version 去掉 + 之后不一致 ⇒ 这是**小版本 / hotfix 提交**（只有部分加载器线升号）。
  按 2026-11-02 发布口径：
    · 小版本 / hotfix 只发 CurseForge，**不建 GitHub Release**；
    · 只有四线统一的主线版本才建 GitHub Release（tag 名取基版本号）。
  所以本 step 不做任何发布动作：不建 Release、不上传资产、不删资产，直接判绿。
  等四线 mod_version 对齐成同一个 base 后再推一次，即可正常出 Release。
release skipped: mixed versions (see above). exiting 0 on purpose.
-- exit code: 0 (expected 0) --
-- gh stub calls: 0 --
-- assertions --
   PASS  report exit code == 0
   PASS  report says unified=0 (warn only)
   PASS  report exit code == 0
   PASS  gh called ZERO times
   PASS  skip notice printed
   PASS  says exit 0 on purpose
   PASS  CurseForge-only explanation
   PASS  no tag/upload/delete attempted
RESULT: all assertions passed
```

#### 场景 3（新增）：某线缺 `mod_version=` 键 ⇒ Report exit 1 且点名该线；Release 防御分支也 exit 1

```
==== scenario: missing-key ====
-- fixture: per-line gradle.properties + built jars --
   neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              jars=astral_dice-1.5.0+neoforge_1.21.1.jar 
   forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 jars=astral_dice-1.5.0+forge_1.20.1.jar 
   neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              jars=astral_dice-1.5.0+neoforge_26.1.2.jar 
   fabric-1.20.1    mod_version=                                   jars=astral_dice-1.5.0.jar 
-- run: REPORT step (consistency gate) --
VERSIONS: unified=1
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1            base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1               base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2            base=1.5.0
  fabric-1.20.1    mod_version=**MISSING KEY** (gradle.properties exists but has no mod_version= line)
::error::mod_version missing/empty for fabric-1.20.1: gradle.properties exists but has no mod_version= key
::error::aborting: 1 present line(s) without a readable mod_version (fabric-1.20.1) - unreadable version is NOT 'line absent at this ref'
-- report exit code: 1 (expected 1) --
-- run: bash -e -o pipefail release_step.sh --
publishing release for tag: 1.5.0
per-line versions (present at 79d459b5febc1283c53085b0870b753fe35c8f0b):
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              base=1.5.0
  fabric-1.20.1    mod_version=                                   base=
::error::cannot read mod_version for fabric-1.20.1: fabric-1.20.1/gradle.properties has no mod_version line
::error::aborting release: 1 present line(s) without a readable mod_version (fabric-1.20.1|fabric-1.20.1/gradle.properties) - refusing to treat an unreadable version as a mixed-version build
-- exit code: 1 (expected 1) --
-- gh stub calls: 0 --
-- assertions --
   PASS  report exit code == 1
   PASS  report names the line (fabric-1.20.1)
   PASS  report abort summary names 1 line
   PASS  report table marks MISSING KEY
   PASS  report does NOT call it absent
   PASS  release defensive branch names the line
   PASS  release abort summary names 1 line
   PASS  release did NOT judge mixed/skip
   PASS  gh never reached
RESULT: all assertions passed
```

#### 场景 4（新增）：老 ref 场景（该 ref 上 fabric 文件不存在）⇒ Report 跳过该线、不报错

```
==== scenario: old-ref ====
-- fixture: per-line gradle.properties + built jars --
   neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              jars=astral_dice-1.5.0+neoforge_1.21.1.jar 
   forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 jars=astral_dice-1.5.0+forge_1.20.1.jar 
   neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              jars=astral_dice-1.5.0+neoforge_26.1.2.jar 
   fabric-1.20.1    <subproject absent at this ref>
-- run: REPORT step (consistency gate) --
VERSIONS: unified=1
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1            base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1               base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2            base=1.5.0
  fabric-1.20.1    mod_version=(absent at this ref)
  => all present lines share base 1.5.0 - unified mainline version
-- report exit code: 0 (expected 0) --
-- run: bash -e -o pipefail release_step.sh --
publishing release for tag: 1.5.0
::warning::skipping fabric-1.20.1 (subproject not present at this ref)
per-line versions (present at 50bbaa84e9fb008cbfca82dbbb73fbb918d997c5):
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              base=1.5.0
all present lines share base 1.5.0 - unified mainline version, publishing release
jar ok (P0) neoforge-1.21.1: astral_dice-1.5.0+neoforge_1.21.1.jar
jar ok (P0) forge-1.20.1: astral_dice-1.5.0+forge_1.20.1.jar
jar ok (P1) neoforge-26.1.2: astral_dice-1.5.0+neoforge_26.1.2.jar
release jars (3):
  astral_dice-1.5.0+neoforge_1.21.1.jar
  astral_dice-1.5.0+forge_1.20.1.jar
  astral_dice-1.5.0+neoforge_26.1.2.jar
::warning::player changelog not found: release/1.5.0/PLAYER_CHANGELOG_ZH.md
::warning::player changelog not found: release/1.5.0/PLAYER_CHANGELOG.md
::warning::no release/1.5.0/PLAYER_CHANGELOG*.md found — falling back to an attachment list
release notes: 167 bytes
uploading missing asset: astral_dice-1.5.0+neoforge_1.21.1.jar
uploading missing asset: astral_dice-1.5.0+forge_1.20.1.jar
uploading missing asset: astral_dice-1.5.0+neoforge_26.1.2.jar
-- exit code: 0 (expected 0) --
-- gh stub calls: 6 --
gh release view 1.5.0
gh release edit 1.5.0 --title Astral Dice 1.5.0 --notes-file /f/MCProject/astral_dice_multiloader/work/_ci_sim/sim/old-ref/runner_temp/astral-dice-release-notes-1.5.0.md --latest --prerelease=false
gh release view 1.5.0 --json assets --jq .assets[].name
gh release upload 1.5.0 neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar
gh release upload 1.5.0 forge-1.20.1/build/libs/astral_dice-1.5.0+forge_1.20.1.jar
gh release upload 1.5.0 neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar
-- assertions --
   PASS  report exit code == 0
   PASS  report skips absent line (no error)
   PASS  report has zero ::error::
   PASS  report says unified=1
   PASS  release warns about skipped line
   PASS  3 jars accepted
   PASS  release has zero ::error::
   PASS  3 gh uploads went out
RESULT: all assertions passed
```

#### 场景 5：统一版本缺某个 jar ⇒ 仍 exit 1 且报 missing jar（回归）

```
==== scenario: unified-missing ====
-- fixture: per-line gradle.properties + built jars --
   neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              jars=astral_dice-1.5.0+neoforge_1.21.1.jar 
   forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 jars=
   neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              jars=astral_dice-1.5.0+neoforge_26.1.2.jar 
   fabric-1.20.1    mod_version=1.5.0+fabric_1.20.1                jars=astral_dice-1.5.0+fabric_1.20.1.jar 
-- run: REPORT step (consistency gate) --
VERSIONS: unified=1
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1            base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1               base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2            base=1.5.0
  fabric-1.20.1    mod_version=1.5.0+fabric_1.20.1              base=1.5.0
  => all present lines share base 1.5.0 - unified mainline version
-- report exit code: 0 (expected 0) --
-- run: bash -e -o pipefail release_step.sh --
publishing release for tag: 1.5.0
per-line versions (present at 6f6951fdc33f288755d3bfcedf54ee225e076832):
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              base=1.5.0
  fabric-1.20.1    mod_version=1.5.0+fabric_1.20.1                base=1.5.0
all present lines share base 1.5.0 - unified mainline version, publishing release
jar ok (P0) neoforge-1.21.1: astral_dice-1.5.0+neoforge_1.21.1.jar
::error::missing jar for forge-1.20.1: forge-1.20.1/build/libs/astral_dice-1.5.0+forge_1.20.1.jar
jar ok (P1) neoforge-26.1.2: astral_dice-1.5.0+neoforge_26.1.2.jar
jar ok (P1) fabric-1.20.1: astral_dice-1.5.0+fabric_1.20.1.jar
::error::aborting release: 1 line(s) without a usable jar (forge-1.20.1)
-- exit code: 1 (expected 1) --
-- gh stub calls: 0 --
-- assertions --
   PASS  report exit code == 0
   PASS  names the missing line
   PASS  aborts with 1 line
   PASS  other three jars still resolved
   PASS  gh never reached
RESULT: all assertions passed
```

#### 附：两个既有场景回归（未被本次改动影响）

```
==== scenario: unified-create ====
-- fixture: per-line gradle.properties + built jars --
   neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              jars=astral_dice-1.5.0+neoforge_1.21.1.jar 
   forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 jars=astral_dice-1.5.0+forge_1.20.1.jar 
   neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              jars=astral_dice-1.5.0+neoforge_26.1.2.jar 
   fabric-1.20.1    mod_version=1.5.0+fabric_1.20.1                jars=astral_dice-1.5.0+fabric_1.20.1.jar 
-- run: REPORT step (consistency gate) --
VERSIONS: unified=1
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1            base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1               base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2            base=1.5.0
  fabric-1.20.1    mod_version=1.5.0+fabric_1.20.1              base=1.5.0
  => all present lines share base 1.5.0 - unified mainline version
-- report exit code: 0 (expected 0) --
-- run: bash -e -o pipefail release_step.sh --
publishing release for tag: 1.5.0
per-line versions (present at 7f9bbd3afa62f070ba05eace01f27ace51b31d83):
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              base=1.5.0
  fabric-1.20.1    mod_version=1.5.0+fabric_1.20.1                base=1.5.0
all present lines share base 1.5.0 - unified mainline version, publishing release
jar ok (P0) neoforge-1.21.1: astral_dice-1.5.0+neoforge_1.21.1.jar
jar ok (P0) forge-1.20.1: astral_dice-1.5.0+forge_1.20.1.jar
jar ok (P1) neoforge-26.1.2: astral_dice-1.5.0+neoforge_26.1.2.jar
jar ok (P1) fabric-1.20.1: astral_dice-1.5.0+fabric_1.20.1.jar
release jars (4):
  astral_dice-1.5.0+neoforge_1.21.1.jar
  astral_dice-1.5.0+forge_1.20.1.jar
  astral_dice-1.5.0+neoforge_26.1.2.jar
  astral_dice-1.5.0+fabric_1.20.1.jar
::warning::player changelog not found: release/1.5.0/PLAYER_CHANGELOG_ZH.md
::warning::player changelog not found: release/1.5.0/PLAYER_CHANGELOG.md
::warning::no release/1.5.0/PLAYER_CHANGELOG*.md found — falling back to an attachment list
release notes: 207 bytes
-- exit code: 0 (expected 0) --
-- gh stub calls: 2 --
gh release view 1.5.0
gh release create 1.5.0 neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar forge-1.20.1/build/libs/astral_dice-1.5.0+forge_1.20.1.jar neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar fabric-1.20.1/build/libs/astral_dice-1.5.0+fabric_1.20.1.jar --title Astral Dice 1.5.0 --notes-file /f/MCProject/astral_dice_multiloader/work/_ci_sim/sim/unified-create/runner_temp/astral-dice-release-notes-1.5.0.md --latest --prerelease=false
-- assertions --
   PASS  report exit code == 0
   PASS  report says unified=1
   PASS  release create called once
   PASS  notes file used
RESULT: all assertions passed
```

```
==== scenario: republish ====
-- fixture: per-line gradle.properties + built jars --
   neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              jars=astral_dice-1.5.0+neoforge_1.21.1.jar 
   forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 jars=astral_dice-1.5.0+forge_1.20.1.jar 
   neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              jars=astral_dice-1.5.0+neoforge_26.1.2.jar 
   fabric-1.20.1    mod_version=1.5.0+fabric_1.20.1                jars=astral_dice-1.5.0+fabric_1.20.1.jar 
-- run: REPORT step (consistency gate) --
VERSIONS: unified=1
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1            base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1               base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2            base=1.5.0
  fabric-1.20.1    mod_version=1.5.0+fabric_1.20.1              base=1.5.0
  => all present lines share base 1.5.0 - unified mainline version
-- report exit code: 0 (expected 0) --
-- run: bash -e -o pipefail release_step.sh --
publishing release for tag: 1.5.0
per-line versions (present at 9ed4fb637ab44e30ad8dca05c4dee6c7e8d47e61):
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1              base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1                 base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2              base=1.5.0
  fabric-1.20.1    mod_version=1.5.0+fabric_1.20.1                base=1.5.0
all present lines share base 1.5.0 - unified mainline version, publishing release
jar ok (P0) neoforge-1.21.1: astral_dice-1.5.0+neoforge_1.21.1.jar
jar ok (P0) forge-1.20.1: astral_dice-1.5.0+forge_1.20.1.jar
jar ok (P1) neoforge-26.1.2: astral_dice-1.5.0+neoforge_26.1.2.jar
jar ok (P1) fabric-1.20.1: astral_dice-1.5.0+fabric_1.20.1.jar
release jars (4):
  astral_dice-1.5.0+neoforge_1.21.1.jar
  astral_dice-1.5.0+forge_1.20.1.jar
  astral_dice-1.5.0+neoforge_26.1.2.jar
  astral_dice-1.5.0+fabric_1.20.1.jar
::warning::player changelog not found: release/1.5.0/PLAYER_CHANGELOG_ZH.md
::warning::player changelog not found: release/1.5.0/PLAYER_CHANGELOG.md
::warning::no release/1.5.0/PLAYER_CHANGELOG*.md found — falling back to an attachment list
release notes: 207 bytes
::notice::republish marker found in commit message - replacing existing assets
deleting existing asset: astral_dice-1.5.0+neoforge_1.21.1.jar
deleting existing asset: astral_dice-1.5.0+forge_1.20.1.jar
gh-stub: simulated delete-asset failure for astral_dice-1.5.0+forge_1.20.1.jar
::warning::could not delete asset astral_dice-1.5.0+forge_1.20.1.jar (already gone, or a concurrent run removed it) - continuing
deleting existing asset: astral_dice-1.5.0+neoforge_26.1.2.jar
deleting existing asset: astral_dice-1.5.0+fabric_1.20.1.jar
uploading asset (republish channel, --clobber): astral_dice-1.5.0+neoforge_1.21.1.jar
uploading asset (republish channel, --clobber): astral_dice-1.5.0+forge_1.20.1.jar
uploading asset (republish channel, --clobber): astral_dice-1.5.0+neoforge_26.1.2.jar
uploading asset (republish channel, --clobber): astral_dice-1.5.0+fabric_1.20.1.jar
-- exit code: 0 (expected 0) --
-- gh stub calls: 11 --
gh release view 1.5.0
gh release edit 1.5.0 --title Astral Dice 1.5.0 --notes-file /f/MCProject/astral_dice_multiloader/work/_ci_sim/sim/republish/runner_temp/astral-dice-release-notes-1.5.0.md --latest --prerelease=false
gh release view 1.5.0 --json assets --jq .assets[].name
gh release delete-asset 1.5.0 astral_dice-1.5.0+neoforge_1.21.1.jar --yes
gh release delete-asset 1.5.0 astral_dice-1.5.0+forge_1.20.1.jar --yes
gh release delete-asset 1.5.0 astral_dice-1.5.0+neoforge_26.1.2.jar --yes
gh release delete-asset 1.5.0 astral_dice-1.5.0+fabric_1.20.1.jar --yes
gh release upload 1.5.0 neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar --clobber
gh release upload 1.5.0 forge-1.20.1/build/libs/astral_dice-1.5.0+forge_1.20.1.jar --clobber
gh release upload 1.5.0 neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar --clobber
gh release upload 1.5.0 fabric-1.20.1/build/libs/astral_dice-1.5.0+fabric_1.20.1.jar --clobber
-- assertions --
   PASS  report exit code == 0
   PASS  delete-asset failure tolerated
   PASS  4 assets still uploaded
   PASS  4 uploads carry --clobber
   PASS  republish notice present
RESULT: all assertions passed
```

### 2.3 行尾

```
$ python work/_ci_sim/gen_evidence.py
.github/workflows/build.yml: bytes=48451 CR=0 LF=691 CRLF=0 bare_LF=691 lone_CR=0 => pure LF
docs/agent/AGENTS-dsh.md: bytes=51375 CR=316 LF=316 CRLF=316 bare_LF=0 lone_CR=0 => pure CRLF
```

```
$ git ls-files --eol .github/workflows/build.yml docs/agent/AGENTS-dsh.md
i/lf    w/lf    attr/                 	.github/workflows/build.yml
i/lf    w/crlf  attr/                 	docs/agent/AGENTS-dsh.md
```

- `.github/workflows/build.yml`：`CR=0` ⇒ **纯 LF**（691 个 LF、0 个 CR）。
- `docs/agent/AGENTS-dsh.md`：`bare_LF=0` / `lone_CR=0` ⇒ **纯 CRLF**（316 个 CRLF）。

### 2.4 改动范围（只动了这两个文件）

```
$ git status --porcelain -- .github/workflows/build.yml docs/agent/AGENTS-dsh.md
 M .github/workflows/build.yml
 M docs/agent/AGENTS-dsh.md
```

工作树里其余 7 个 `M` 文件（`docs/agent/AGENTS-dsh-linux.md`、`docs/agent/AGENTS-full.md`、`docs/archive/card-recipes-2026-10-09/README.md`、`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/client/CombatPowerHud.java`、`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/client/FabricClientBridges.java`、`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/platform/client/gui/overlay/ForgeGui.java`、`tools/curseforge_upload.py`）**均为本会话开始前既存改动**，mtime 全部早于本会话启动，未被我批改：

```
2026-11-02 19:35:51  .github/workflows/build.yml                 <- 本次改动
2026-11-02 19:36:56  docs/agent/AGENTS-dsh.md                   <- 本次改动
2026-11-02 19:01:41  tools/curseforge_upload.py                 <- 既存（会话前）
2026-11-02 18:55:20  fabric-1.20.1/.../CombatPowerHud.java      <- 既存（会话前）
2026-11-01 20:23:39  docs/archive/card-recipes-2026-10-09/README.md
2026-11-01 20:17:25  docs/agent/AGENTS-full.md
2026-10-31 22:00:22  docs/agent/AGENTS-dsh-linux.md
```

未跟踪条目数 154 → 155，唯一新增是本次交付物 `work/ci-fix-review-followups.md`（其余仿真脚本/日志全部落在既已未跟踪的 `work/_ci_sim/` 目录下，该目录仍只算 1 个条目）；**源码树没有任何新增未跟踪文件**。

### 2.5 文档门禁（`python docs/agent/verify-split.py`）

改动前基线（`work/_ci_sim/baseline/verify-split.before.txt`）与改动后**同一状态**：`rc=1`，两个 FAIL —— ①「AGENTS-dsh.md 生效红线区块与 AGENTS-full.md L2–L120 逐字一致」（红线区字符数 14,319 → 14,476，只增不减是因为原位插入 157 字符）；②「共同安全红线 1–6 条齐全」（编号 `[1,2,3,4,5,6,7]`）。

⇒ 本次改动**没有把原本绿的门禁改红**：该门禁在本会话开始前已红，且其修法需要改 `docs/agent/AGENTS-full.md`（本次禁改范围）。按用户「原位补记录、不删旧文」的要求执行后维持既有状态，未做额外尝试。

---

## 3. 未验证项

1. **未在真实 GitHub Actions 上运行**：全部结论来自离线仿真（抽取的 step 脚本 + `gh` 桩）。真实 workflow 的 `if:` 表达式（`steps.version.outputs.unified` / `steps.tag.outputs.tag`）、`GITHUB_OUTPUT` 跨 step 传递、`::error::` 在 Actions UI 的注解分组，均未实跑。
2. **Report 与 Release 的存在性判据来源不同**（既有口径，本次未改）：Report 用工作区 `[[ -f "$props" ]]`，Release 用 `git cat-file -e` 读 `<SHA>:<props>`。checkout 后二者等价；若中间步骤删改工作区文件，理论上可能分叉。
3. **未真跑 tag push / `workflow_dispatch` / `[republish-assets]` 通道**：`republish` 场景复用的是既有桩，未接真实 Release。
4. **`mod_version` 的其他异常形态未逐一造场景**：`mod_version=`（值全空白）、`mod_version= `、注释行 `# mod_version=1.5.0`。前两者走同一条 `-z` 判据（`noversion`）；后者因 `grep '^mod_version='` 不匹配而同样归 `noversion`——均按代码路径推得，未逐条执行。
5. **`verify-split.py` 未变绿**：属他人范围（需改 `AGENTS-full.md`），本次按用户指示只做原位插入，未去修该门禁。
6. **CF 上传链路未验证**：`tools/curseforge_upload.py` 的 `guard_p0_only` 与 `--allow-p1` 闸门本次未执行、未改动（按要求保留为互锁）。
7. **未验证混版 + 缺 jar 的组合**：当前顺序是先判「空版本」判红、再判混版、再判 missing jar；混版且同时缺 jar 时按既有口径走混版 `exit 0`（不发 Release，jar 不参与判定）——这是既有行为，未被本次改动影响，但未单独造场景固定下来。

---

## 4. 结论

两处缺陷均已闭合：缺陷 1 的两处 unified 判据现在同口径（独立布尔量 + 空版本一律异常），缺陷 2 的文档旧红线已原位补上 2026-11-02 裁决并与 `build.yml` 注释互相交叉引用；混版（四条都读到但不全相等）⇒ 打印四线对照表 + 政策说明 + exit 0 + gh 调用 0 次 + 不打 tag；统一版本 ⇒ unified=1、打 tag、四 jar 进同一 Release（缺 jar 仍 `::error::` + exit 1）；缺键（文件在、`mod_version=` 缺失或为空）⇒ Report 步骤 `::error::` 点名该线 + exit 1，Release 防御分支同样 `::error::` + exit 1，两条路径都不再静默判绿。
