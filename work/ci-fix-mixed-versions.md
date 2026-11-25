# 四线混版 Release 必红 —— CI 修复报告

- 目标文件：`.github/workflows/build.yml`（改后 **617 行** / 42203 B；改前 **437 行** / 29250 B）
- 改前逐字节备份：`work/_ci_sim/build.yml.orig`
- 仿真与取证目录：`work/_ci_sim/`（抽出的 shell、gh 桩、五场景夹具与日志）
- 状态：**改动已落盘在工作区，未提交**（按纪律：不 commit / 不 push / 不建 tag / 不碰 Release / 不碰 CurseForge / 未跑 Gradle）

---

## 0. 结论速览

| 提交形状 | 改前 | 改后 |
|---|---|---|
| 四线统一 `1.5.0+<loader>_<mc>`，四 jar 齐 | 建/更新 Release，绿 | **行为完全一致**（同批 gh 调用、同 tag 名、同 notes、同 prerelease 口径） |
| 四线统一，但某一线缺 jar | 红：`::error::missing jar` ⇒ `aborting release` exit 1 | **仍红**（同一条 err，另附该目录实际内容 + 同 base 候选，便于区分「版本偏移」与「真没构建」） |
| 混版（只有某线升号，如 fabric = `1.5.0-hotfix`） | **红**：`missing jar for fabric-1.20.1` ⇒ `aborting release` exit 1（#146/#147） | **绿**：打印四线版本表 + 跳过说明后 `exit 0`，**gh 一次都不调用**，也不打 tag |
| 同 tag 并发 delete/upload（#144/#145 形状） | 任一 `404` / `already exists` ⇒ exit 1 | delete 失败降级 `::warning::` 继续；republish 通道 upload 带 `--clobber`；冻结通道撞名后确认「附件确已存在」即视为成功（**绝不覆盖字节**） |

---

## 1. 根因（采信 `work/recon/ci-failure.md`，未重查）

1. **单一 BASE 复用给四条线**：旧 `:198` 只从 `neoforge-1.21.1/gradle.properties` 取 `mod_version`，旧 `:203` 截掉 `+` 之后得 `BASE=1.5.0`，旧 `:326` 用同一个 glob `astral_dice-"$BASE"+*.jar` 套四条线。
   ⇒ fabric 线是 `1.5.0-hotfix+fabric_1.20.1`，产物名 `astral_dice-1.5.0-hotfix+fabric_1.20.1.jar` **与 glob 不匹配** ⇒ `:329-333` 报 `missing jar for fabric-1.20.1` ⇒ `:344-347` `aborting release` + exit 1。
2. **并发红**：全文件无 `concurrency:`；`[republish-assets]` 通道先逐个 `gh release delete-asset`（无容错）再 `gh release upload`（无 `--clobber`）⇒ 同 tag 两个 run 相撞就红（#144 与 #145 同提交 `6fe38138`，仅 ref 不同）。
3. 已证伪项（不重查）：YAML 语法、gradlew 权限、gh 未安装、`GH_TOKEN`、`permissions: contents: write`、校验脚本缺失、glob 过窄、prerelease 推导。

**发布口径（用户裁决，决定本次修复方向）**：主线版本（四线统一 `X.Y.0`）⇒ GitHub Release + CF；小版本 / hotfix（只有某线升号）⇒ **只发 CF，不建 GitHub Release**。故混版提交**不该红、也不该产出 Release**；统一版本提交行为必须保持原样。本次只改 CI 脚本与门禁。

---

## 2. 改动清单（行号 = **改后**行号）

| # | 位置 | 内容 | 性质 |
|---|---|---|---|
| P1 | `:32-44` | job `build` 加 `concurrency`（`group: release-${{ github.ref }}`、`cancel-in-progress: false`）+ 说明注释 | 并发保护 |
| P2 | `:208-304`（name `:208`、注释 `:210-219`、run `:221-292`、outputs `:300-304`） | 「Read base version」改为「Report per-line mod_version (consistency gate - report only, never fails)」：逐线读 `mod_version`、打表、输出 `unified` / `mv_<key>` | 前置门禁（只报告） |
| P3 | `:335-338` | Create Git tag 的 `if` 末尾追加 `&& steps.version.outputs.unified == '1'` | 混版不打 tag（延伸） |
| P4 | `:368-370` | Release step 的 `if` 显式放行 `unified != '1'`（否则混版时该 step 会被跳过，连说明都打不出来） | 让混版能进 step 判绿 |
| P5 | `:382-388` | 删除「从 version 输出取共用 `BASE`」，换成修复说明注释（**2 行删除 = 根因所在**） | 移除根因 |
| P6a | `:417-421` + `:432-436` + `:437-469` | 第 1 遍：从**提交对象**逐线读自己的 `mod_version`；混版判定 + 打印 + `exit 0` | 逐线版本 + 混版跳过 |
| P6b | `:471-500`（`:483-499` 为定位/报错段） | 第 2 遍：按**精确产物名** `build/libs/astral_dice-<该线 mod_version>.jar` 定位，替代 BASE glob | 去掉版本偏移致命性 |
| P7a | `:569-575` | republish 通道的 `delete-asset` 失败降级 `::warning::` 继续 | 并发容错 |
| P7b | `:578-605`（`:583-589` republish、`:590-603` 冻结通道） | upload 三分支：已存在则跳过；republish 通道 `--clobber`；冻结通道失败后确认已存在即视为成功（否则 exit 1） | 并发容错 |

### P1 job 级 concurrency（`:32-44`）

```diff
       contents: write
+    # 并发保护（2026-11-02 新增，针对 #144/#145 那类「同一个 Release 被两个 run 同时 delete/upload」的红）：
+    #   ⚠️ GitHub 的 concurrency 只能挂在 **job** 或 **workflow** 上，没有 step 级语法 ⇒ 只能挂在 build 上。
+    #   ...
+    #   ⚠️ **已知边界**：github.ref 在「分支 push」与「tag push」上不同 ⇒ 同一个提交的
+    #   「multi-main push + 手工 tag push」（正是 #144/#145 的形状）不会被本组收敛（recon §P1 已指出）。
+    #   那一类跨 ref 竞态由 Release step 的三处容错兜住 ...
+    concurrency:
+      group: release-${{ github.ref }}
+      cancel-in-progress: false
     steps:
```

### P2 前置门禁（`:208-304`）

```diff
-      - name: Read base version
+      - name: Report per-line mod_version (consistency gate - report only, never fails)
       id: version
+        # ⛔ 本 step **只报告、永不失败**：对混版提交 exit 1 就等于让「小版本 / hotfix 提交」继续红，
+        #    与发布口径（见下）相悖。真正要红的两件事都不在这里：
+        #      · 「统一版本但某线缺 jar」⇒ 留在 Release step 内部（那里才有 missing jar 判定）；
+        #      · 「版本树不可解析」⇒ 也只在 Release step 里判红。
       run: |
-          VERSION=$(grep '^mod_version=' neoforge-1.21.1/gradle.properties | cut -d'=' -f2)
+          LINES=(
+            "neoforge-1.21.1|neoforge-1.21.1"
+            "forge-1.20.1|forge-1.20.1"
+            "neoforge-26.1.2|neoforge-26.1.2"
+            "fabric-1.20.1|fabric-1.20.1"
+          )
+          ... 每线 grep mod_version、去 CR、取 base（判据与下方 Release step 同款）
+          echo "VERSIONS: unified=$UNIFIED"
+          printf '  %-16s mod_version=%-32s base=%s\n' ...
+          echo "unified=$UNIFIED" >> "$GITHUB_OUTPUT"
+          # 参考版本沿用旧口径：取第一条可读的线（neoforge-1.21.1）
+          ...
+          for i in "${!T_LINE[@]}"; do
+            echo "mv_${key}=${T_VER[$i]}" >> "$GITHUB_OUTPUT"
+          done
```

该 step 的打表口径示意（`unified` 判据与 Release step 相同；**非**仿真原始输出，原始输出见 §3）：

```text
VERSIONS: unified=0
  neoforge-1.21.1  mod_version=1.5.0+neoforge_1.21.1      base=1.5.0
  forge-1.20.1     mod_version=1.5.0+forge_1.20.1         base=1.5.0
  neoforge-26.1.2  mod_version=1.5.0+neoforge_26.1.2      base=1.5.0
  fabric-1.20.1    mod_version=1.5.0-hotfix+fabric_1.20.1 base=1.5.0-hotfix
  => bases differ - mixed / line-specific version: GitHub Release will be SKIPPED (CurseForge only)
::notice::mixed per-line mod_version - the Release step will skip publication (see its output)
```

### P3 混版不打 tag（`:335-338`）

```diff
-        if: github.event_name == 'push' && github.ref == 'refs/heads/multi-main' && steps.version.outputs.skip_tag == '0'
+        # ⛔ 2026-11-02：**混版不打 tag**（unified == '1' 才算）。理由：混版按口径只发 CF、不建 Release，
+        #   而 tag 是 Release 的身份 —— 给一个 hotfix 提交建出 1.5.0，之后 tag 只会被「已存在则复用」
+        #   （tag 永不移动），会让下一次真正统一的发布挂到错误的提交上。故 tag 与 Release 同进同退。
+        if: github.event_name == 'push' && github.ref == 'refs/heads/multi-main' && steps.version.outputs.skip_tag == '0' && steps.version.outputs.unified == '1'
```

### P4 Release step 的 if（`:368-370`）

```diff
-        if: startsWith(github.ref, 'refs/tags/') || (github.event_name == 'push' && github.ref == 'refs/heads/multi-main' && steps.tag.outputs.tag != '')
+        # 混版时上一步 Create Git tag 会被跳过（outputs.tag 为空），但本 step **仍要进来**：
+        #   它要在日志里打印混版说明并 exit 0（判绿）。故 if 里显式放行 unified != '1'。
+        if: startsWith(github.ref, 'refs/tags/') || (github.event_name == 'push' && github.ref == 'refs/heads/multi-main' && (steps.version.outputs.unified != '1' || steps.tag.outputs.tag != ''))
```

### P5 移除根因（`:382-388`）

```diff
-          # 版本基准：tag 名 = BASE；产物文件名里的版本段也是 BASE 起头（见下方收紧的 glob）。
-          BASE="${{ steps.version.outputs.base }}"
+          # 版本基准：tag 名 = BASE（tag 名仍由上方版本步骤推导，本步不改 tag 命名口径）。
+          # ⛔ 2026-11-02 修复（四线混版必红）：这里**不再**取一个共用 BASE 去套四条线。
+          #   旧写法 ... 只反映 neoforge-1.21.1 一个线的版本，却被拿来给四线套同一个 glob ...
+          #   现在改为**逐线读自己的 mod_version、按各自完整产物名定位**（见下方两遍循环），
+          #   版本偏移不再致命；四线版本不一致时整步按「混版只发 CF」口径**跳过 Release 并 exit 0**。
```

### P6a 逐线读版本 + 混版跳过（`:417-469`）

```diff
+          declare -a P_LINE=() P_PROJ=() P_PRIO=() P_VER=()
+          for entry in "${LINES[@]}"; do
+            ...
+            if ! git cat-file -e "${SHA}:${props}" 2>/dev/null; then
+              echo "::warning::skipping $line (subproject not present at this ref)"
+              continue
+            fi
+            raw_ver=$(git cat-file -p "${SHA}:${props}" | grep -m1 '^mod_version=' || true)
+            line_ver="${raw_ver#*=}"
+            line_ver="${line_ver%$'\r'}"
+            P_LINE+=("$line"); P_PROJ+=("$proj"); P_PRIO+=("$prio"); P_VER+=("$line_ver")
+          done
+          echo "per-line versions (present at $SHA):"
+          ... 逐线 printf '  %-16s mod_version=%-34s base=%s\n'
+          if [[ "${#P_LINE[@]}" -eq 0 ]]; then
+            echo "::error::no subproject found at this ref - nothing to publish"; exit 1
+          fi
+          if [[ "$UNIFIED" != "1" ]]; then
+            echo "::notice::mixed per-line mod_version detected - SKIPPING GitHub Release (by policy)"
+            echo "  ... 小版本 / hotfix 只发 CurseForge，**不建 GitHub Release** ..."
+            echo "release skipped: mixed versions (see above). exiting 0 on purpose."
+            exit 0
+          fi
+          echo "all present lines share base $REF_BASE - unified mainline version, publishing release"
```

### P6b 精确产物名定位（`:483-499`，替换旧 BASE glob 段）

```diff
-            # 产物名 = astral_dice-<mod_version>.jar，mod_version 形如 1.5.0+neoforge_1.21.1。
-            # 用 BASE（已剥掉 +后缀）收紧 glob ⇒ 只命中**本版本**的 jar。⛔ 不能退回
-            #   astral_dice-*.jar：build/libs 从不清理，实测 neoforge-26.1.2/build/libs 同时躺着
-            #   1.2.1~1.5.0 共 14 个 jar，松散 glob 会把历次旧 jar 一并塞进本次 Release。
-            hits=()
-            for j in "$proj"/build/libs/astral_dice-"$BASE"+*.jar; do
-              if [[ -f "$j" ]]; then hits+=("$j"); fi
-            done
-            if [[ "${#hits[@]}" -eq 0 ]]; then
-              echo "::error::missing jar for $line: $proj/build/libs/astral_dice-$BASE+*.jar"
-              ls -1 "$proj/build/libs" 2>/dev/null | sed 's/^/    present: /' || true
-              MISSING+=("$line")
-            elif [[ "${#hits[@]}" -gt 1 ]]; then
-              echo "::error::ambiguous jars for $line (expected 1, got ${#hits[@]})"
+            if [[ -z "$ver" ]]; then
+              echo "::error::cannot read mod_version for $line: $proj/gradle.properties has no mod_version line"
+              MISSING+=("$line"); continue
+            fi
+            jar="$proj/build/libs/astral_dice-$ver.jar"
+            if [[ -f "$jar" ]]; then
+              JARS+=("$jar")
+              echo "jar ok ($prio) $line: $(basename "$jar")"
+            else
+              echo "::error::missing jar for $line: $jar"
+              ls -1 "$proj/build/libs" 2>/dev/null | sed 's/^/    present: /' || true
+              ls -1 "$proj"/build/libs/astral_dice-"${ver%%+*}"*.jar 2>/dev/null | sed 's/^/    same-base candidate: /' || true
+              MISSING+=("$line")
+            fi
```

保留未动：`::error::aborting release: N line(s) without a usable jar (...)` + `exit 1`、`::error::no jars collected at all` + `exit 1`。

### P7a / P7b 并发容错（`:569-575` / `:583-603`）

```diff
-                gh release delete-asset "$TAG" "$name" --yes
+                if ! gh release delete-asset "$TAG" "$name" --yes; then
+                  echo "::warning::could not delete asset $name (already gone, or a concurrent run removed it) - continuing"
+                fi
...
-                gh release upload "$TAG" "$j"
+              elif [[ "$REPUBLISH" == "1" ]]; then
+                echo "uploading asset (republish channel, --clobber): $name"
+                gh release upload "$TAG" "$j" --clobber
+              else
+                echo "uploading missing asset: $name"
+                # 冻结口径：**不加 --clobber**，绝不覆盖已有附件字节（2026-10-30 用户裁决）。
+                if ! gh release upload "$TAG" "$j"; then
+                  if gh release view "$TAG" --json assets --jq '.assets[].name' 2>/dev/null | grep -Fxq -- "$name"; then
+                    echo "::warning::upload of $name lost a race but the asset exists now - treating as published (bytes NOT overwritten)"
+                  else
+                    echo "::error::failed to upload $name and it is still absent from the release"
+                    exit 1
+                  fi
+                fi
+              fi
```

`--clobber` **只出现在 republish 通道**；冻结口径那支仍无 `--clobber`，且撞名只「确认已存在」不覆盖字节。

---

## 3. 本地仿真

### 3.0 方法

- `work/_ci_sim/extract_release_step.py`：按 step 名精确取 `run: |` 块，剥掉 10 空格 YAML 缩进，**只**替换两个 Actions 模板表达式（`steps.tag.outputs.tag` / `steps.version.outputs.is_prerelease` → 环境变量），其余逐字保留 ⇒ `work/_ci_sim/release_step.sh`（237 行，`bash -n` 通过，剩余模板表达式 0）。混版判据**不做任何替换**，由同一段 shell 自己算。
- `work/_ci_sim/gh_stub.sh`：PATH 前置的假 `gh`，每次调用写入 `GH_LOG`；可用 `GH_VIEW_RC` / `GH_VIEW_ASSETS` / `GH_FAIL_DELETE` / `GH_FAIL_UPLOAD` 制造分支。**真实 gh 从未被调用。**
- `work/_ci_sim/run_sim.sh <scenario>`：为每个场景建 `sim/<scenario>/repo`（新建 git 仓库、单提交、`core.autocrlf false`），写入四份 `gradle.properties` 与各线 `build/libs` 产物，导出 `GITHUB_SHA` / `GITHUB_REF=refs/heads/multi-main` / `RUNNER_TEMP` / `HEAD_COMMIT_MSG` / `SIM_TAG_OUT=1.5.0` / `SIM_IS_PRERELEASE=0`，然后按 runner 默认 `bash -e -o pipefail` 执行抽出的 shell，最后断言。
- 复跑：`cd work/_ci_sim && bash run_sim.sh <scenario>`（五场景各一次，全部 `RESULT: all assertions passed`，harness 退出码 0）。

---

### 场景 1 —— 统一版本 + 四个 jar 齐（必须 exit 0 且走到上传分支）

**Release step 输出**（shell 由 build.yml 逐字抽出，`bash -e -o pipefail` 执行）：

```text
publishing release for tag: 1.5.0
per-line versions (present at cab0b5068f42579f1035462f43271af23c2a4b3d):
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
```

**退出码 / gh 桩调用记录 / 断言**：

```text
-- exit code: 0 (expected 0) --
-- gh stub calls: 7 --
gh release view 1.5.0
gh release edit 1.5.0 --title Astral Dice 1.5.0 --notes-file /d/MCProject/astral_dice_multiloader/work/_ci_sim/sim/unified-upload/runner_temp/astral-dice-release-notes-1.5.0.md --latest --prerelease=false
gh release view 1.5.0 --json assets --jq .assets[].name
gh release upload 1.5.0 neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar
gh release upload 1.5.0 forge-1.20.1/build/libs/astral_dice-1.5.0+forge_1.20.1.jar
gh release upload 1.5.0 neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar
gh release upload 1.5.0 fabric-1.20.1/build/libs/astral_dice-1.5.0+fabric_1.20.1.jar
-- assertions --
   PASS  exit code == 0
   PASS  4 jars accepted (jar ok x4)
   PASS  4 uploads went out
   PASS  freeze path: no --clobber
   PASS  no release create in this path
RESULT: all assertions passed
```

---

### 场景 2 —— 混版（fabric = `1.5.0-hotfix`，另三线 `1.5.0`）：必须 exit 0 且 gh 调用 0 次

**Release step 输出**（shell 由 build.yml 逐字抽出，`bash -e -o pipefail` 执行）：

```text
publishing release for tag: 1.5.0
per-line versions (present at 27bcd34038ce912e73f2be1b6c0645bd74dc3ad1):
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
```

**退出码 / gh 桩调用记录 / 断言**：

```text
-- exit code: 0 (expected 0) --
-- gh stub calls: 0 --
-- assertions --
   PASS  exit code == 0
   PASS  gh called ZERO times
   PASS  skip notice printed
   PASS  says exit 0 on purpose
   PASS  CurseForge-only explanation
   PASS  no tag/upload/delete attempted
RESULT: all assertions passed
```

---

### 场景 3 —— 统一版本但缺一个 jar（forge）：必须仍 exit 1 并指出缺哪线

**Release step 输出**（shell 由 build.yml 逐字抽出，`bash -e -o pipefail` 执行）：

```text
publishing release for tag: 1.5.0
per-line versions (present at f61bc3a70c9aa9eb6e5e53fa9f390611cd09ea8e):
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
```

**退出码 / gh 桩调用记录 / 断言**：

```text
-- exit code: 1 (expected 1) --
-- gh stub calls: 0 --
-- assertions --
   PASS  exit code == 1
   PASS  names the missing line
   PASS  aborts with 1 line
   PASS  other three jars still resolved
   PASS  gh never reached
RESULT: all assertions passed
```

---

### 场景 4 —— `[republish-assets]` 通道 + delete-asset 失败一次：必须容错继续，upload 带 `--clobber`

**Release step 输出**（shell 由 build.yml 逐字抽出，`bash -e -o pipefail` 执行）：

```text
publishing release for tag: 1.5.0
per-line versions (present at 6b327604df3854459c099601730758b0c2336651):
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
```

**退出码 / gh 桩调用记录 / 断言**：

```text
-- exit code: 0 (expected 0) --
-- gh stub calls: 11 --
gh release view 1.5.0
gh release edit 1.5.0 --title Astral Dice 1.5.0 --notes-file /d/MCProject/astral_dice_multiloader/work/_ci_sim/sim/republish/runner_temp/astral-dice-release-notes-1.5.0.md --latest --prerelease=false
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
   PASS  exit code == 0
   PASS  delete-asset failure tolerated
   PASS  4 assets still uploaded
   PASS  4 uploads carry --clobber
   PASS  republish notice present
RESULT: all assertions passed
```

---

### 场景 5（补充回归）—— 统一版本、Release 不存在：走 `gh release create`

**Release step 输出**（shell 由 build.yml 逐字抽出，`bash -e -o pipefail` 执行）：

```text
publishing release for tag: 1.5.0
per-line versions (present at 26aecf21c2159744eff57cc7bf6dc154f9704eab):
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
```

**退出码 / gh 桩调用记录 / 断言**：

```text
-- exit code: 0 (expected 0) --
-- gh stub calls: 2 --
gh release view 1.5.0
gh release create 1.5.0 neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar forge-1.20.1/build/libs/astral_dice-1.5.0+forge_1.20.1.jar neoforge-26.1.2/build/libs/astral_dice-1.5.0+neoforge_26.1.2.jar fabric-1.20.1/build/libs/astral_dice-1.5.0+fabric_1.20.1.jar --title Astral Dice 1.5.0 --notes-file /d/MCProject/astral_dice_multiloader/work/_ci_sim/sim/unified-create/runner_temp/astral-dice-release-notes-1.5.0.md --latest --prerelease=false
-- assertions --
   PASS  exit code == 0
   PASS  release create called once
   PASS  notes file used
RESULT: all assertions passed
```

---

## 4. 行尾统计

| 文件 | 字节 | 裸 LF | CRLF | CR | 外层（`git ls-files --eol`） |
|---|---|---|---|---|---|
| 改前 `work/_ci_sim/build.yml.orig` | 29250 | 437 | 0 | 0 | — |
| 改后 `.github/workflows/build.yml` | 42203 | 617 | 0 | 0 | `i/lf  w/lf  attr/` |

- 改前改后**都是纯 LF、CR=0**，裸 LF 数 = 总行数 − 1（437 / 617），未引入任何 CR。
- 改法：python `open(newline='')` 读 → 断言「无 CR 字节」→ 按锚点打补丁（每个锚点必须**恰好命中 1 次**）→ 以 `newline='\n'` 写回，全程不经任何换行转换工具。
- `git` 在 diff 时提示 `LF will be replaced by CRLF the next time Git touches it`（宿主 `core.autocrlf`）；该提示**改前对同一文件同样出现**，工作区字节仍是 LF。
- YAML 解析：`python -c "import yaml;d=yaml.safe_load(open('.github/workflows/build.yml',encoding='utf-8'));print(len(d['jobs']))"` ⇒ `1`（无异常）；`jobs.build.concurrency` ⇒ `{group: 'release-${{ github.ref }}', cancel-in-progress: False}`。

---

## 5. 未验证项 / 已知限制

1. **未在真实 GitHub Actions 上跑过**（纪律禁止 push / tag / 发布）。全部证据来自本地对**逐字抽出的 shell** 的执行；上传/删除等外部动作由桩替代。
2. **`concurrency` 挂不上 step 级**：GitHub 只支持 job / workflow 级，故挂在 job `build` 上 ⇒ 同一 ref 的多次运行会被**串行**（代价换「不打断正在跑的发布」，`cancel-in-progress: false`）。
3. **跨 ref 竞态未被 concurrency 收敛**：`group: release-${{ github.ref }}` 在「分支 push」与「tag push」上是两个组 ⇒ #144/#145 那种「同一提交、分支 + 手工 tag」仍可能并行。该形状由三处容错兜住（delete 容忍 404 / republish 用 `--clobber` / 冻结通道撞名确认已存在），**不是**互斥保证。要绝对串行只能改固定 group（整条流水线串行）或把 Release 拆成独立 job —— 两者都改变运行结构，本次不做。
4. **P3「混版不打 tag」是超出字面要求的延伸**：按口径混版只发 CF；若仍打 tag，tag 永不移动 ⇒ 会把下一次真正统一的发布锚到错误提交。理由已写进注释。
5. **未把 Release 拆成独立 job**：需要 artifact 下载 + job outputs + 重写约 200 行，回归风险大于收益。
6. **`gh release create` 的竞态未加重试**：同 tag 两个 run 同时 create 时后者会失败；此形状不在已证实的红里，留作后续项。
7. **`unified` 判据存在两处**（门禁 step 读工作区、Release step 读提交对象），必须同步改（两处注释已互相指向）。
8. 各线 `build/libs` 里的历史 jar 仍在（未清理，属既有状态）；本次改用精确文件名后不再回退到 `astral_dice-*.jar`，因此不受影响。

---

## 6. 统一版本 vs 混版 行为对照表

| 维度 | 统一版本（四线 base 一致） | 混版（base 不一致） |
|---|---|---|
| 门禁 step | `VERSIONS: unified=1` + 四线表 + `=> all present lines share base X` | `VERSIONS: unified=0` + 四线表 + `=> bases differ ...` + `::notice::` |
| Create Git tag | 同改前：`skip_tag == '0'` 时建 tag（tag 名 = base） | **跳过**（`unified != '1'`）⇒ 不建 tag、不推 tag |
| Release step 是否进入 | 进入 | 进入（`if` 显式放行） |
| Release step 首个动作 | 逐线读版本 ⇒ 打表 ⇒ `all present lines share base ...` | 逐线读版本 ⇒ 打表 ⇒ `::notice::mixed ... SKIPPING` |
| 是否建/改 Release | 是（`create` 或 `edit` + notes） | **否** |
| 是否上传/删除资产 | 是（冻结口径只补缺失；republish 通道删后 `--clobber` 重传） | **否**（gh 调用 0 次） |
| jar 定位 | 每线 `astral_dice-<自己的 mod_version>.jar`，四线全硬校验 | 不定位 jar（提前 `exit 0`） |
| 缺 jar | `::error::missing jar` + `aborting release` ⇒ **exit 1** | 不适用（不校验、不发布） |
| 退出码 | 0（成功）/ 1（缺件等真失败） | **0**（判绿） |
| CurseForge | 由既有 CF 通道负责（本次未改） | 由既有 CF 通道负责（本次未改） |

---

## 7. 结论

以后混版提交（例如只有 fabric 是 `1.5.0-hotfix`）不会再红：Release step 打印四线版本表与「只发 CF、不建 Release」的说明后 `exit 0`，不打 tag、不建 Release、不上传也不删除任何资产（gh 调用 0 次）；四线统一的主线版本提交行为与修复前完全一致（同一套 tag 推导、notes、prerelease 口径、四 jar 硬校验与 `missing jar` 判红），并发相撞另有三处容错（delete 容忍 / republish `--clobber` / 冻结通道撞名确认）。

**一句话结论：混版提交从现在起改为「打印说明 + 判绿 + 完全不碰 GitHub」，统一版本提交的 Release 行为与修复前逐项一致。**
