# P1：四线版本号统一 1.5.0 + 物品「试剂-深渊」→「深渊试剂」

- 工作树：`D:\MCProject\astral_dice_multiloader`（分支 `multi-main`，动手前 `## multi-main...origin/multi-main [ahead 24]`）
- 执行时间：2026-11-02 01:46 ~ 01:5x（Asia/Hong_Kong）
- 结论：**G1/G3/G4/G5 全绿；G2a 全绿；G2b 非 0（残留全部落在 brief 明令禁改/测试台资产内，未改动）；四线 build 全成功并产 jar**。
- 全程未 `git push` / 未打 tag / 未动 `.git` / 未手工复制 jar 到整合包。

---

## 0. 前置：收停正在跑的 fabric 开发实例

### 0.1 取证（判定口径：GradleDaemon 不动；KnotClient/KnotServer 要停）
`pwsh -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Select-Object ProcessId,CommandLine | Format-List"`

| PID | 命令行关键片段 | 判定 | 处置 |
|---|---|---|---|
| 37728 | GradleDaemon 9.5.1 | Gradle 守护进程 | **不动** |
| 17052 | `GradleWorkerMain` = "Gradle Worker Daemon 1" | 守护进程工作线程 | **不动** |
| 27308 | `-jar ...\gradle\wrapper\gradle-wrapper.jar :fabric-1.20.1:runServer --console=plain -Pquickplay=testworld` | MC 服务端启动器 | **停** |
| 40268 | `-Dfabric.dli.config=...\fabric-1.20.1\launch.cfg -Dfabric.dli.env=server ... net.fabricmc.devlaunchinjector.Main nogui nogui` = KnotServer | MC 服务端 | **停** |

### 0.2 停止（仓内口径优先，脚本适用）
`pwsh -NoProfile -File D:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version fabric-1.20.1 --phase stop --force` → exit=0

```
MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
MT_CLEANUP_SAVES: KEPT（未指定 --purge-saves）
MT_CLEANUP: 已收停 1 个本流程进程（范围：fabric-1.20.1）
MT_CLEANUP_RESULT: OK
MT_STOP: OK (1s) — 收停完成，无本流程残留进程
```

脚本适用依据：`scripts/test/lib/Mt.Paths.psm1:41` `$script:VERSIONS = @('1.21.1','1.20.1','26.1.2','fabric-1.20.1')` 已登记 fabric ⇒ **未走到**手工 `Stop-Process`，未杀任何 daemon。
（备选路径 `scripts/test/win/README.md` 的 bash stop 未使用。）

### 0.3 停完核对
- `java.exe` 仅剩 37728（GradleDaemon）、17052（Worker Daemon）。
- `netstat -ano | grep -E ':(25585|25575)\b'` → 无输出（两端口无监听/占用）。

---

## 1. 改动 1：mod_version 去 `-rc.7`（4 处，各 1 行）

改前实读与 brief 快照一致（:37 / :41 / :54 / :29）；`neoforge-26.1.2:52` 只是**注释**里提到 mod_version，非键值，未动。
只改 `mod_version` 一行，`starengine_version` / `minecraft` / loader 版本 / `mod_name` 未动（`git diff --stat` 四线各 `2 +-` = 1 插入 1 删除）。

改后（G1）：

```
37:mod_version=1.5.0+neoforge_1.21.1        # neoforge-1.21.1/gradle.properties
41:mod_version=1.5.0+forge_1.20.1           # forge-1.20.1/gradle.properties
54:mod_version=1.5.0+neoforge_26.1.2        # neoforge-26.1.2/gradle.properties
29:mod_version=1.5.0+fabric_1.20.1          # fabric-1.20.1/gradle.properties
```

---

## 2. 改动 2：显示名「试剂-深渊」→「深渊试剂」

基准：id `abyss_reagent_chip`、标签、手册条目**文件**、tooltip 键全部未动；只改 lang 值与 java 注释文字。

### 2.1 lang（2a zh_cn / 2b en_us / 2c ja_jp，四线 × 三语 = 12 处）
逐行定点替换（`newline=''` 保行尾；未使用 `json.dump`）。改后（G3，grep -n）：

| 线 | zh_cn | en_us | ja_jp |
|---|---|---|---|
| neoforge-1.21.1 | :379 `"深渊试剂"` | :379 `"Abyss Reagent"` | :378 `"深淵試薬"` |
| forge-1.20.1 | :385 `"深渊试剂"` | :385 `"Abyss Reagent"` | :384 `"深淵試薬"` |
| neoforge-26.1.2 | :888 `"深渊试剂"` | :888 `"Abyss Reagent"` | :888 `"深淵試薬"` |
| fabric-1.20.1 | :921 `"深渊试剂"` | :921 `"Abyss Reagent"` | :921 `"深淵試薬"` |

键名均为 `item.astral_dice.abyss_reagent_chip`。行尾风格保持原样（neo/forge/neo26 的 lang 纯 LF；fabric 三份纯 CRLF），12 文件 `json.loads` 全通过。

### 2.2 java 注释（2d）
只改注释行（strip 后以 `//` / `*` / `/*` 开头），非注释行出现旧串则脚本拒绝落盘。实测命中 **12 / 12 / 6 / 6 = 36 行**（brief 估「约 30 行」），每线 6 个文件、共 24 个文件：
`component/ModAttachments.java`、`datagen/ModRecipeProvider.java`、`event/ModTooltipHandler.java`、`item/HealingManager.java`、`item/ModItems.java`、`item/chip/AbyssReagentChipItem.java`。

行号分布：neo 线 HealingManager 19/21/237/252/272/291/298、ModItems 914、Abyss 20、Attachments 449、Recipe 996、Tooltip 775；forge 线 HealingManager 20/22/238/253/273/292/299、ModItems 915、Abyss 20、Attachments 384、Recipe 1056、Tooltip 798；neo26 线 HealingManager 378、ModItems 1132、Abyss 20、Attachments 454、Recipe 995、Tooltip 845；fabric 线 HealingManager 291、ModItems 868、Abyss 19、Attachments 406、Recipe 1001、Tooltip 776。
（fabric 的 `AbyssReagentChipItem.java` 是仓库既有**未跟踪**新文件，故不出现在 `git diff` 的 +/- 统计里。）

### 2.3 未改动（2e 红线）
`docs/**`、`CHANGELOG*`、`README*`、`.superpowers/**`、`build/**`、`bin/**`、`run/**`、`temp/**`、`release/**`、`porting/**`、`*.bak*`、`scripts/test/cases` 下的用例 JSON —— 一处未碰。

---

## 3. 改动 3：四线构建

```
pwsh -NoProfile -Command "$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'; Set-Location 'D:\MCProject\astral_dice_multiloader';
  & .\gradlew.bat --console=plain :neoforge-1.21.1:build :forge-1.20.1:build :neoforge-26.1.2:build :fabric-1.20.1:build"
```

结果：**BUILD SUCCESSFUL in 25s，35 actionable tasks: 30 executed, 5 up-to-date**（完整日志 `work/p1-build.log`）。
四线 `compileJava` 均 executed（非全 up-to-date 的假绿），四线 `:build` 均本批执行。

> 注：stderr 有一行 `:JAVA_HOME=... 无法识别为 cmdlet` —— 是本条复合命令里 `$env:JAVA_HOME` 赋值被外层引号吞掉产生的**非致命噪声**；实际编译用的 JDK 为已热身的 Gradle daemon（9.5.1 / zulu-21），构建结果与产物均正常（见 G4/G5）。

---

## 4. 验收（G1–G6）

### G1 ✅ mod_version 全为 `1.5.0+<后缀>`，无 `-rc.7`
见 §1 四行输出；`grep -n 'mod_version'` 四线均无 `rc.7`。

### G2a ✅ src/main 与 src/generated：旧名 0 命中
```
neoforge-1.21.1 src/main = 0    src/generated = 0
forge-1.20.1    src/main = 0    src/generated = 0
neoforge-26.1.2 src/main = 0    src/generated = 0
fabric-1.20.1   src/main = 0    src/generated = 0
```
（构建后复核仍为 0，未被 datagen 回写。）

### G2b ⚠️ 全仓（排除 docs/ .superpowers/ build/ bin/ run/ temp/ release/ porting/ + CHANGELOG* README* *.bak*）**非 0**，残留 9 个文件、全部在测试台资产内：
```
181  ./scripts/test/reports/20260926-230116/1.21.1/runclient_launch.log
181  ./scripts/test/reports/20260926-230116/1.21.1/latest.log
181  ./scripts/test/reports/20260926-230116/1.21.1/debug.log
  8  ./scripts/test/cases/.mt_snapshot.json
  8  ./scripts/test/cases/.mt_autojs.json
  4  ./scripts/test/cases/PMFIX-TIP-1.21.1.json
  4  ./scripts/test/cases/PMFIX-TIP-1.20.1.json
  1  ./scripts/test/resources/kubejs/1.21.1/client_scripts/astral_pmfix_client.js
  1  ./scripts/test/resources/kubejs/1.20.1/client_scripts/astral_pmfix_client.js
```
性质与不处理理由：
- `reports/**` 是 2026-09-26 那次运行的**历史运行日志**（KubeJS 行 `AP_PMFIX|tip|abyss|i=0|strip=试剂-深渊|END`），运行时输出快照，非源码。
- `cases/PMFIX-TIP-*.json`、`.mt_snapshot.json`、`.mt_autojs.json` 属 brief 2e 明令禁改的「scripts/test/cases 下的用例 JSON」。
- `resources/kubejs/**/astral_pmfix_client.js:4` 仅一行**注释**（`// · 试剂-深渊 tooltip 去括注`）；该探针的实际取值是运行时 `pmfLog(... 'strip=' + pmfStrip(raw))`，**无硬编码旧名断言** ⇒ 改名不会让该探针变红。
⇒ 未改动，按「只报告」处理。若需要全仓 0 命中，需另行授权改测试台资产。

### G3 ✅ 三语值（12 行）——见 §2.1 表；四线均为 深渊试剂 / Abyss Reagent / 深淵試薬。

### G4 ✅ 四线 build/libs 存在 `astral_dice-1.5.0+<后缀>.jar`；旧 rc.7 jar 清理情况分三类

`ls -la`（子项目 `<线>/build/libs`，新 jar 01:50/01:50 生成）：
```
neoforge-1.21.1/build/libs:
  -rw-r--r-- 2611856 Nov  2 01:50 astral_dice-1.5.0+neoforge_1.21.1.jar
  -rw-r--r-- 2607086 Oct 29 18:07 astral_dice-1.5.0-rc.6+neoforge_1.21.1.jar      <-- 旧，未清
  -rw-r--r-- 2611872 Nov  1 23:43 astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar      <-- 旧，未清
forge-1.20.1/build/libs:
  -rw-r--r-- 2660134 Nov  2 01:50 astral_dice-1.5.0+forge_1.20.1.jar
  -rw-r--r-- 2660152 Nov  1 23:43 astral_dice-1.5.0-rc.7+forge_1.20.1.jar         <-- 旧，未清
neoforge-26.1.2/build/libs:
  -rw-r--r-- 2875199 Nov  2 01:50 astral_dice-1.5.0+neoforge_26.1.2.jar
  -rw-r--r-- 2875210 Nov  1 23:31 astral_dice-1.5.0-rc.7+neoforge_26.1.2.jar      <-- 旧，未清
  （另有 1.2.1-beta ~ 1.3.7-beta.2 共 12 个历史 jar）
fabric-1.20.1/build/libs:
  -rw-r--r-- 3027671 Nov  2 01:50 astral_dice-1.5.0+fabric_1.20.1.jar
  -rw-r--r-- 3027461 Nov  1 23:16 astral_dice-1.5.0-rc.7+fabric_1.20.1.jar        <-- 旧，未清
```

`Get-FileHash -Algorithm SHA256`（新 jar）：
```
astral_dice-1.5.0+neoforge_1.21.1.jar size=2611856 sha256=24220D06A07A2CC6155B981682A080BDD191E1E79A89574ED4C5B4404903B113
astral_dice-1.5.0+forge_1.20.1.jar    size=2660134 sha256=FD6D8D8205E290701A18AF9CC489DD7EFAB6DC82D9A310F3079058EFA4B2E9C4
astral_dice-1.5.0+neoforge_26.1.2.jar size=2875199 sha256=57F39E13AE325E19BA9C080886A247588CA98728E09BD7EFE9D08ECF04E0374A
astral_dice-1.5.0+fabric_1.20.1.jar   size=3027671 sha256=280DF3C11A2F5E111F633C16D44317568851B56400359B9BFB50C6D2813B047E
```

旧 rc.7 jar 是否被推送任务清掉 —— **只在推送目标目录被清，子项目 build/libs 不参与清理**：
- 仓根 `build/libs`：✅ 已清。只剩四个 1.5.0 jar（此前存在的 4 个 rc.7 / 1.3.7-alpha.2 jar 均已被删）。
  ```
  -rw-r--r-- 3027671 Nov  2 01:50 astral_dice-1.5.0+fabric_1.20.1.jar
  -rw-r--r-- 2660134 Nov  2 01:50 astral_dice-1.5.0+forge_1.20.1.jar
  -rw-r--r-- 2611856 Nov  2 01:50 astral_dice-1.5.0+neoforge_1.21.1.jar
  -rw-r--r-- 2875199 Nov  2 01:50 astral_dice-1.5.0+neoforge_26.1.2.jar
  ```
- `run/*/mods`：✅ 已清，四目录各只剩新 jar（`run/26.1.2/mods` 另留两个 `*.bak`/`.old-*` 备份，文件名不以 `.jar` 结尾，不匹配清理 glob）。
- 整合包 `D:\...\mods`：✅ 已清，四目录各只剩新 jar。
- 子项目 `<线>/build/libs`：❌ 未清（rc.6/rc.7/历史 jar 都在）。原因（机制，不是失败）：清理逻辑只对三个**推送目标目录**生效 ——
  `neoforge-1.21.1/build.gradle:338-341`（devRunModsDir）、`:425`（packModsDir）、`:473`（rootBuildLibsDir）均为
  `listFiles().findAll { it.isFile() && it.name.startsWith('astral_dice-') && it.name.endsWith('.jar') } → delete()`，**没有**针对 `<line>/build/libs` 的任务。
  ⇒ 分发口径本身正确（外部目录不留旧版），仅本地 build 目录留档。**本批未改动构建脚本**（brief 未授权）。

### G5 ✅ 开包核验（`unzip -p <jar> assets/astral_dice/lang/zh_cn.json | grep -n abyss_reagent_chip`）
四条线均输出「深渊试剂」（同时附 en_us / ja_jp 一并核验）：
```
neoforge-1.21.1: zh_cn:379 "item.astral_dice.abyss_reagent_chip": "深渊试剂"
                 en_us:379 "Abyss Reagent"   ja_jp:378 "深淵試薬"
forge-1.20.1:    zh_cn:385 "深渊试剂"  en_us:385 "Abyss Reagent"  ja_jp:384 "深淵試薬"
neoforge-26.1.2: zh_cn:888 "深渊试剂"  en_us:888 "Abyss Reagent"  ja_jp:888 "深淵試薬"
fabric-1.20.1:   zh_cn:921 "深渊试剂"  en_us:921 "Abyss Reagent"  ja_jp:921 "深淵試薬"
```
**未被 datagen 覆盖回旧值**（四线均无自建 LanguageProvider；实测 grep `*LanguageProvider*.java` 只命中 `ref/` 下第三方源码）⇒ 无生成源需要修正。
另核 jar 内元数据版本：`neoforge.mods.toml:17 version="1.5.0+neoforge_1.21.1"`、`mods.toml:21 version="1.5.0+forge_1.20.1"`、`neoforge.mods.toml:17 version="1.5.0+neoforge_26.1.2"`、`fabric.mod.json:1 "version":"1.5.0+fabric_1.20.1"`。

### G6 ✅ 推送类任务**随 build 自动执行**（finalizedBy），并写入以下外部路径

四线均 `build.finalizedBy pushToDevRun / pushToGame / pushToRootBuild`（neoforge-1.21.1:484/485/487、forge-1.20.1:631/632/634、neoforge-26.1.2:507/508/510、fabric-1.20.1:524/525/526）；根 `build.gradle:18` 注明四线共用同一套分发口径。

本次 build **实际执行并写盘**（build 日志原文，四线各 3 条）：
```
pushToDevRun: pushed astral_dice-1.5.0+neoforge_1.21.1.jar -> F:\...\run\1.21.1\mods
pushToRootBuild: pushed astral_dice-1.5.0+neoforge_1.21.1.jar -> F:\...\build\libs
pushToGame: pushed astral_dice-1.5.0+neoforge_1.21.1.jar -> D:\.minecraft\versions\狐の航空学 Voxy Edition\mods
pushToDevRun: pushed astral_dice-1.5.0+forge_1.20.1.jar -> F:\...\run\1.20.1\mods
pushToRootBuild: pushed astral_dice-1.5.0+forge_1.20.1.jar -> F:\...\build\libs
pushToGame: pushed astral_dice-1.5.0+forge_1.20.1.jar -> D:\.minecraft\versions\1.20.1-Forge 模组测试\mods
pushToDevRun: pushed astral_dice-1.5.0+neoforge_26.1.2.jar -> F:\...\run\26.1.2\mods
pushToRootBuild: pushed astral_dice-1.5.0+neoforge_26.1.2.jar -> F:\...\build\libs
pushToGame: pushed astral_dice-1.5.0+neoforge_26.1.2.jar -> D:\.minecraft\versions\26.1.2-NeoForge 模组测试\mods
pushToDevRun: pushed astral_dice-1.5.0+fabric_1.20.1.jar -> F:\...\run\fabric-1.20.1\mods
pushToRootBuild: pushed astral_dice-1.5.0+fabric_1.20.1.jar -> F:\...\build\libs
pushToGame: pushed astral_dice-1.5.0+fabric_1.20.1.jar -> D:\.minecraft\versions\1.20.1-Fabric 模组测试\mods
```
- **工作树内**：`run/<mcver>/mods` 与仓根 `build/libs`。
- **工作树外（外部路径）**：`D:\.minecraft\versions\{狐の航空学 Voxy Edition, 1.20.1-Forge 模组测试, 26.1.2-NeoForge 模组测试, 1.20.1-Fabric 模组测试}\mods`。
  `mcHomePath` 解析（根 `build.gradle:31-38`）：`MC_HOME` > `/mnt/d/.minecraft` > `D:/.minecraft`，本机落到 `D:\.minecraft`。
- 分支白名单 `packPushBranches = ['multi-main']`（四线 :316/:460/:334/:348）⇒ 当前分支在白名单内，pushToGame **真推送**（不是 skipped）。
- 前置库口径自检输出（只读信息）：neo/forge 线 `整合包内已有独立库 jar [starengine-*-1.1.0.jar](请与 starengine_version=1.1.0 对账)`；neo26 线 `整合包内无独立库 jar(符合内嵌口径)`；fabric 线 `内嵌前置自检 OK — META-INF/jars/starengine-fabric-1.20.1-1.1.0.jar`。
- 依 brief：**只报告，未回滚、未手工复制到整合包、未改发布配置**。

---

## 5. 残留 / 风险 / 未做项（如实登记）

1. **G2b 非 0**：9 个文件命中旧名，全部在 `scripts/test/reports/**`（历史运行日志）、`scripts/test/cases/**`（2e 禁改）、`scripts/test/resources/kubejs/**`（探针注释，无断言）。未修改，需授权才能清。
2. **子项目 `<线>/build/libs` 保留旧 jar**（rc.7 及更早），分发目录已清。属既有构建口径，未改脚本。
3. 四线 `src/main/java` 注释改名 36 行（brief 估 ~30）；无标识符/字符串/逻辑改动。
4. 收停只针对 fabric 线的 `runServer`；四线中其他线的开发实例在取证时**不存在**（java.exe 列表仅上述 4 个）。
5. 构建 stderr 的 `JAVA_HOME` 噪声行（见 §3 注），不影响产物。

## 6. 证据文件
- 构建全量日志：`D:\MCProject\astral_dice_multiloader\work\p1-build.log`（74 KB，74,203 B）
- 本报告：`D:\MCProject\astral_dice_multiloader\work\p1-version-and-rename.md`
