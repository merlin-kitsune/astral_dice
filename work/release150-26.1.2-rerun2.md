# RELEASE150 / neoforge-26.1.2 复跑报告（第 3 轮：rerun2）

- 仓库：`D:/MCProject/astral_dice_multiloader`，线 `neoforge-26.1.2`，版本 1.5.0
- 时间：2026-11-02 04:43 – 05:10（Asia/Hong_Kong）
- 被测 jar：`run/26.1.2/prodgame/mods/astral_dice-1.5.0+neoforge_26.1.2.jar`，2,877,947 B，sha256 `B422D57D2103219522EEF792822DA1656A1F9E197F91C6B0D8E82D381EC4878D`（与任务书 b422d57d…878d 一致，起跑前复核通过）
- 环境：`run/26.1.2/prodgame` 现成实例，**97 个 jar**（玩家实例 95 + kubejs + AutoJS + rhino + better-advanced-tooltips；mru / sounds 因需 NeoForge 26.1.2.114 仍排除在外）

## 0. 最终判定

| 项 | 结果 |
|---|---|
| 用例 `RELEASE150-26.1.2` | **PASS**（`MT_CASE_RESULT: RELEASE150-26.1.2 = PASS`） |
| 27 条断言 | 25 PASS + 2 条 vision 交 lead（`MT_CHANNEL: delegated_count=2`），**0 FAIL / 0 BLOCKED** |
| 截图通道 | 两处真实缺陷已修：**不再只扫 `run/<ver>`**（补 gameDir），**不再用路径黑名单**（改按 mtime 判旧图）；三张图全部由测试台自己收上来并登记 |
| 断言 #32 | 旧判据（jar 文件名回显）已替换为**版本门平台行**；旧判据实测 `FAIL(SOURCE_ECHO)`，新判据实测 `PASS` 且锚的是模组自身输出的强证据行 |
| 产品侧回归 | A 类 `Missing sound for event` = **0 行**；patchouli 解析错误 = **0 行**；无新增崩溃报告 |
| 收停 | `MT_STOP: OK`，25585 释放，无 `run/26.1.2` java 残留 |

## 1. 端口门与启动取证

- 端口门（**不用 netstat 文本**）：`Get-NetTCPConnection -State Listen` 过滤 25565 / 25575 / 25585 → **无监听**（25585 空闲）。
- 进程门：`Get-CimInstance Win32_Process -Filter "Name='java.exe'"` 6 个进程，命令行**均不含 `run/26.1.2`**（均为 Gradle / other line 的 dev 进程）。未对任何他人进程做操作（无 kill）。
- 启动：`python run/26.1.2/.tmp/prodlaunch.py --quickplay testworld --log run/26.1.2/.tmp/prod-run-r2.log`
  → `started pid 33508`（classpath 102 / missing 0 / argv 48 / mainClass `net.neoforged.fml.startup.Client` / gamedir `D:\MCProject\astral_dice_multiloader\run\26.1.2\prodgame`，pid 落 `run/26.1.2/.tmp/prod-run.pid`）。
- 就绪判据：`curl -s -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:25585/health` → **200**（不是只看端口）。
- 起跑前轮转（stamp `20261102-0455`）：`prod-run.log`→`.prev-20261102-0455`；`prodgame/logs/latest.log`→`.prev-20261102-0455`；`prodgame/screenshots` 全部 18 张 png → 各加 `.prev-20261102-0455`；`run/26.1.2/screenshots` 同 tag 旧图同样轮转。`.mt_shots.json` 中本轮三个 tag 无历史条目（原 12 条历史条目未动）。

## 2. 任务 1：截图通道修复（`scripts/test/lib/Mt.Channel.psm1`）

### 2.1 先纠正一个前提：KubeJS 沙箱**禁止反射**，`getDeclaredMethods()` 这条路走不通

任务书写的是「遍历 `Screenshot.getDeclaredMethods()`，取名为 grab、参数个数为 4 的那个」。在本线实机（client 侧 AutoJS/KubeJS eval）逐条实测，这条路根本不可达：

| 尝试 | 实机返回 |
|---|---|
| `Java.loadClass('net.minecraft.client.Screenshot').class` | `InternalError: Java class "net.minecraft.client.Screenshot" has no public instance field or method named "class".` |
| `…getDeclaredMethods()` | `InternalError: … has no public instance field or method named "getDeclaredMethods".` |
| `java.lang.Class.forName('net.minecraft.client.Screenshot')` | `JavaException: java.lang.IllegalAccessException: Attempt to lookup caller-sensitive method using restricted lookup object` |
| `Thread.currentThread().getContextClassLoader()` | 同上 `IllegalAccessException` |
| `Client.getClass()` | `TypeError: Cannot find function getClass in object net.minecraft.client.Minecraft@…`（Rhino 包装器不暴露 Object 方法） |

**因此改成等价手段：运行期类型协商（runtime type negotiation）**——候选表只声明「方法名 + 参数类型名序列」，实参在 JS 里**按参数类型名现造**，调用统一走 `f.apply(S, args)`，是否成立只由「文件真的落盘」判定。这样同样满足「不写死类名/签名」的意图：代码里没有 `com.mojang.blaze3d.pipeline.*` 全名，也没有任何 `S.grab(...)` 直调（自测 D7/D8 就是守这两条）。**这一点与任务书字面写法不同，特此声明。**

### 2.2 尺寸/行数变化

| | 改前 | 改后 |
|---|---|---|
| 行数 | 1170 | 1351 |
| 字节 | 58,136 | 70,648 |
| EOL | CRLF | CRLF（`lone_lf=0`） |
| sha256 | `c2a079159690797d1dee2cb87084abff7fd50c897440f1c0b408bc30cd74f316` | `0544e67e74524fe506d3d368567a42085dd6ac36930cfd547785d02f595df521` |
| 备份 | — | `scripts/test/lib/Mt.Channel.psm1.bak-shot-reflect-20261102-0500` |

每次落盘后都跑语法门：`pwsh -NoProfile -File run/26.1.2/.tmp/_parse_check.ps1` → `PARSE_OK …/Mt.Channel.psm1`。

### 2.3 候选表（`$script:ShotCandidates`，8 档，**不写死类名**）

| 档 | 方法名 + 参数类型名 | 说明 |
|---|---|---|
| T1 | `grab(File,String,Target,int,Consumer)` | 26.1.2 实证可跑；**能指定文件名**，本线应命中这档 |
| T2 | `grab(File,String,Target,Consumer)` | 1.21.1 世代的 4 参（26.1.2 不存在，用于快速失败并留痕） |
| T3 | `grab(File,Target,Consumer)` | 26.1.2 真实 3 参；**按时间戳命名**，只能靠落盘兜底收 |
| T4 | `m_92289_(File,Target,Consumer)` | 1.20.1 SRG 历史唯一能落盘的一档 |
| T5 | `m_92295_(File,String,Target,Consumer)` | 1.20.1 SRG 4 参 |
| T6 | `grab(File,String,Target)` | 无回调变体 |
| T7 | `grab(File,Target)` | 无回调变体 |
| T8 | `grab(File)` | 最老形态 |

实参构造（JS 侧 `valForType(tn)`，按类型名分词，**未知类型直接抛错不猜**）：
- 含 `file` → `mc.gameDirectory`
- 含 `string` → 目标文件名（`<tag>.png`）
- 含 `target`（`*Target` 结尾，26.1.2 实测类型名是 `com.mojang.blaze3d.pipeline.MainTarget`）→ `mc.mainRenderTarget` 等候选字段 `pick()` 取到的对象
- `int`/`float`/`double` → 1；`boolean` → true
- 含 `consumer`/`callback`/`function`/`listener` → `new JavaAdapter(Java.loadClass('java.util.function.Consumer'),{accept:function(x){}})`

调用统一 `var f = S[METHOD]; f.apply(S, args)`；档位改动只改候选表，不改 JS 模板。

### 2.4 落盘兜底（三段）

1. **扫描面扩到真实落盘点**：每档 eval 返回后，把 JS 报回的 `out.work`（= gameDir 绝对路径）与 `<gameDir>/screenshots` 追加进 `$scanDirs`。
   （上一轮的 `called_no_file` 就是这里漏了：`$scanDirs` 只有 `run/<ver>/screenshots` 与 `run/<ver>`，而 26.1.2 的 grab 落在 `<gameDir>/screenshots`。）
2. **认领判据**（`Wait-MtShotFile`，4s / 120ms 轮询）：
   - **精确名**（`<tag>.png`）：mtime ≥ 本档起点 − 30s 即认（宽限抵文件系统精度），**不受旧图快照限制**；
   - **非精确名**（时间戳命名）：mtime ≥ 本档起点 − 1s、`Length ≥ 20480`、且不在/未命中旧图快照；同轮取**最新 mtime** 那张；
   - 两者都必须过 `Test-MtShotFileReady`（`Length>0` 且尾部 8 字节含 `IEND`），避免把 ioPool 异步写出的半成品当证据。
3. **改名/复制 + 登记**：命中后若源不在 `run/<ver>/screenshots`，`Copy-Item` 成 `<tag>.png`（gameDir 里的原件**保留**，不动游戏的落盘点）；登记**只走 `Add-MtShot`**，不手写清单。vision 判据未改动（仍只判 DELEGATED/BLOCKED，内容判读交 lead）。

### 2.5 顺带修掉的真缺陷：旧图判据从「路径黑名单」改成「mtime 是否被刷新」

- 旧实现把「快照时已存在的 PNG 路径」永久记进 `$Pre`。游戏**覆盖同名文件**时 mtime 变新但路径仍在黑名单 → 能指定文件名的 T1 白白超时 → 退到 T3（时间戳命名）再靠兜底收，于是同一批里「有时 T1、有时 T3」。
- 现场证据（本轮用例日志与落盘文件对账）：`run/26.1.2/prodgame/screenshots/chip_shop_ui.png` = **149,169 B @ 04:58:25.215**、`release150_guide.png` = **128,434 B @ 04:58:31.112**（两张都 ready=True），但机器行报的是 T3 + 时间戳源（04:58:29.412 / 04:58:35.313）⇒ **T1 其实已经把正确命名的图写好了，是认领逻辑没接住**，不是签名问题。
- 修法两处：
  - `$pre` 存**快照那一刻的 mtime**；`Wait-MtShotFile` 里只有 `mtime_now ≤ mtime_snap + 0.001`（文件压根没变）才当旧图跳过；
  - 逐档取 `$tierSinceUnix`（在**发 eval 之前**），新发现目录的补快照只登记 `mtime < $tierSinceUnix` 的文件 ⇒ 本档刚写出来的文件永远不会被补快照误标。
- 修复后实机回归（同 tag 连拍 3 次，每次上游都留着上一次的同名图）：
  ```
  CALL1 status=OK detail=… bytes=128779 tier=T1 sig=grab(File,String,Target,int,Consumer) png_ok=1 rt=com.mojang.blaze3d.pipeline.MainTarget registered src=fallback_copy:…\prodgame\screenshots\probe_race.png
  CALL2 status=OK detail=… bytes=128632 tier=T1 …
  CALL3 status=OK detail=… bytes=129225 tier=T1 …
  ```
  另一次带「3 小时前同名旧图诱饵」的回归：修复前会退 T3，修复后 `tier=T1 bytes=127995`、`oldlen=128594`、`same_as_seed=False`。
- 成功 detail 新增 `attempts=`（前面各档的失败原因，400 字上限），失败时逐档留痕；机器行字段只增不改，`tier=`/`sig=`/`png_ok=`/`registered` 语义不变。

### 2.6 离线自测（`scripts/test/tools/selftest_shot_channel.ps1`，纯离线、不碰实机）

命令：`pwsh -NoProfile -File scripts/test/tools/selftest_shot_channel.ps1`（输出 UTF-8 存档：`run/26.1.2/.tmp/_selftest_out.txt`）

```
=== 场景 A：调用成功，但落盘名是时间戳（26.1.2 的 3 参 grab 行为）===
MT_CHANNEL: op=shot channel=autojs status=ok detail=file=selftest_ts.png mode=f2 bytes=4722124 tier=T1 sig=grab(File,String,Target,int,Consumer) png_ok=1 rt=com.mojang.blaze3d.pipeline.MainTarget registered src=fallback_copy:…\_selftest-shot-20261102-050357\game\screenshots\2026-11-02_05.11.02.png
  [PASS] A1 结果 Status=OK（兜底收上来才算成功）
  [PASS] A2 <tag>.png 落在 run/<ver>/screenshots
  [PASS] A3 目标是真 PNG 且 > 20480 B
  [PASS] A4 Test-MtShotFileReady 通过（尾部有 IEND）
  [PASS] A5 detail 里 tier 明确（tier=T1）
  [PASS] A6 detail 里 sig 是候选表第一档
  [PASS] A7 detail 标出兜底来源 src=fallback_copy:<gameDir 路径>
  [PASS] A8 登记成功（无 add_shot_err）
  [PASS] A9 .mt_shots.json 新增 1 条（只经 Add-MtShot 登记）
  [PASS] A10 游戏原本那张（时间戳名）仍在 gameDir/screenshots（复制而非搬走）

=== 场景 B：调用抛异常（26.1.2 的 4 参 grab，复现上一轮 T1 报错）===
MT_CHANNEL: op=shot channel=delegated status=delegated detail=selftest_throw — 各档签名均未产出 selftest_throw.png：T1 not_called: InternalError: Can't find method net.minecraft.client.Screenshot.grab(java.io.File,string,com.mojang.blaze3d.pipeline.MainTarget,adapter1).; T2 not_called: …
  [PASS] B1 抛异常时**不得**报 OK
  [PASS] B2 报 DELEGATED（通道表达不了，而不是假成功）
  [PASS] B3 没有产出 <tag>.png
  [PASS] B4 .mt_shots.json 零新增（不登记假条目）
  [PASS] B5 失败理由逐档留痕（not_called）

=== 场景 C：调用成功但一个文件都没落 ⇒ 仍须报失败（防假成功）===
MT_CHANNEL: op=shot channel=delegated status=delegated detail=selftest_nofile — 各档签名均未产出 selftest_nofile.png：T1 called_no_file: work=…\_selftest-shot-20261102-050357\game rt=com.mojang.blaze3d.pipeline.MainTarget scanned=4 err=; T2 called_no_file: …
  [PASS] C1 无文件时 Status=DELEGATED（ok:true 不等于成功）
  [PASS] C2 .mt_shots.json 零新增
  [PASS] C3 失败理由写明 called_no_file（且带 work=/rt=/scanned=）
  [PASS] C4 called_no_file 里带 gameDir（下次排查一眼看出扫过哪儿）

=== 场景 E：gameDir/screenshots 里已有同名旧图，本次覆盖同名新图（T1 不该被路径黑名单误杀）===
MT_CHANNEL: op=shot channel=autojs status=ok detail=file=selftest_collide.png … bytes=4722124 tier=T1 … src=fallback_copy:…\game\screenshots\selftest_collide.png
  [PASS] E1 存在同名旧图时仍须 Status=OK
  [PASS] E2 不许因路径黑名单退档（仍须 tier=T1）
  [PASS] E3 收上来的是**新图**（字节数 != 3 小时前那张旧图）
  [PASS] E4 .mt_shots.json 新增 1 条（只经 Add-MtShot）
  [PASS] E5 目标 PNG 完好（尾部 IEND）

  [PASS] D1 候选表 8 档
  [PASS] D2 第一档 = 26.1.2 实证可跑的 5 参 grab
  [PASS] D3 JS 里 SHAPE 是 JSON 数组（不是裸串）
  [PASS] D4 JS 里 NAME 原样注入字符串字面量
  [PASS] D5 裸文件名（不带引号）被拒绝（否则会生成 var NAME = a.png 语法错误）
  [PASS] D6 JS 走 f.apply(S, args) 的统一调用（方法名/类型序来自候选表）
  [PASS] D7 JS 里没有写死渲染目标类的全名（com.mojang.blaze3d.pipeline…）
  [PASS] D8 JS 里没有 S.grab(...) 直调（方法名与类型序都来自候选表，走 f.apply）
  [PASS] D9 实参按参数类型名现造（valForType）

MT_SELFTEST_RESULT PASS pass=33 fail=0 total=33
EXITCODE=0
```

自测里踩到并修掉的两个「假失败」，都记在脚本注释里：
- `Copy-Item` **保留源 mtime**，模板是 9 月旧图 ⇒ 刚「落盘」的图被判成上一世代残留（场景 A 首跑全红）。修法：复制后显式 `LastWriteTime = Get-Date`。
- 场景 C 首跑曾被判成功：`$sinceUnix` 在进循环前算一次且给了 2s 宽限，把**上一场景**留下的时间戳图收成了本次结果。修法即 §2.5 的逐档 mtime 边界。

### 2.7 签名行为的实机取证（本轮现场产生，供复核）

| 调用 | 结果 | 落点/字节 |
|---|---|---|
| `grab(File,'proof_named_5arg.png',Target,int,Consumer)` | 成功，forceName **原样当文件名**（不追加 .png） | `prodgame/screenshots/proof_named_5arg.png` 416,533 B |
| 通用 `f.apply` 5 参路径 | 成功；`out.rtType = com.mojang.blaze3d.pipeline.MainTarget` | `proof_apply_5arg.png` 418,541 B |
| `grab(File,'four_arg_should_fail.png',Target,Consumer)` | `InternalError: Can't find method …grab(java.io.File,string,com.mojang.blaze3d.pipeline.MainTarget,adapter1).` | 无文件（复现上一轮 T1 报错） |
| `grab(File,Target,Consumer)`（3 参） | 成功但**时间戳命名** | `2026-11-02_04.47.19.png` 418,738 B |

> 结论：26.1.2.109 **根本不存在 4 参 `grab(File,String,RenderTarget,Consumer)`**；只有 3 参与 5 参两个重载（读 `neoforge-26.1.2/build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar` 的 `net/minecraft/client/Screenshot.java` 逐行确认：`grab(File,RenderTarget,Consumer)`、`grab(File,@Nullable String,RenderTarget,int,Consumer)`、`takeScreenshot(…)`、`private static File getFile(File)`）。任务书里「第四个参数是 MainTarget 的 4 参」是对 Rhino 报错文本的误读——那条报错恰好说明「这个 4 参组合不存在」。

## 3. 任务 2：断言 #32 前后对照

### 3.1 旧断言（假证据）

- 原文：`{"type":"log","scope":"whole","pattern":"astral_dice-1\\.5\\.0\\+neoforge_26\\.1\\.2\\.jar"}`
- 实测：`mt_assert.ps1 log … --window whole` → rc=1，`FAIL(SOURCE_ECHO)`，命中 2 行、**强证据 0 行**：
  - `L228 [echo] - astral_dice (jar(mods/astral_dice-1.5.0+neoforge_26.1.2.jar))`
  - `L346 [echo] - starengine (jar(mods/astral_dice-1.5.0+neoforge_26.1.2.jar > starengine-neoforge-26.1.2-1.1.0.jar))`
- 为什么是假证据：这两行是 **NeoForge 启动期模组清单的缩进续行**，前面没有 `[时间戳] [线程/LEVEL] [来源]:` 平台前缀，按 `Test-MtPlatformLine` 不算输出行；而它们的文本又恰好来自**文件名**，也就是说这条断言证明的是「mods 目录里有这个文件名的 jar」，**不是**「运行中的游戏加载的版本是 1.5.0」。它把「文件名回显」当成了「版本事实」。

### 3.2 新断言（真证据）

- 判据（逻辑正则；写进 JSON 时每个反斜杠双写）：`^\[[^\]]+\]\s*\[[^\]]+/[A-Za-z]+\]\s*\[astral_dice-version-gate/\]\s*:.*\(Version Gate\):mod_version=1\.5\.0\+neoforge_26\.1\.2\b`
- 命中的原文行（本轮实跑，平台行）：

  `[0211月2026 04:46:31.300] [modloading-sync-worker/INFO] [astral_dice-version-gate/]: [Astral Dice] 版本互通门槛(Version Gate):mod_version=1.5.0+neoforge_26.1.2 → 网络互通号=1.5;正式版按二号位(major.minor)互通,含 SNAPSHOT 的构建只与完整版本串相同的快照互通。`

- 为什么是真证据：
  1. 它是**平台输出行**（`[时间戳] [线程/INFO] [logger/]:` 前缀齐全），不是 eval 源码回显（不命中 `Test-MtEvalSourceEcho` 的弱证据形状）；
  2. 它由**模组自身**在 modloading 阶段用自有版本常量打印（logger `astral_dice-version-gate`），即「运行时加载到的 mod 版本」这个事实本身，而不是文件名或路径；
  3. 正则把版本串锚在 `mod_version=` 之后并要求结尾边界，`1.5.0+neoforge_26.1.2` 必须整段匹配 ⇒ 语义**不放宽**（SNAPSHOT / 其它版本串不会命中）。
- 与 #55 的分工：#55 读构建产物（`models=158>=120 entries=2452>=1700 signs=32>=25`）证明「被测 jar 内容达标」；#32 证明「游戏里跑的就是 1.5.0+neoforge_26.1.2」。两条互补，不重复。
- `scope` 保留 `whole` 的理由（不改 type/scope，只换 pattern）：`scripts/test/cases/.mt_snapshot.json` 里的 `launch_offsets` 是上一轮遗留（latest.log=118436），本轮没有走 `mt_launch` 所以没有合法 launch 窗口；用 `launch` 会从 118436B 起读而**越过**版本门行。`whole` = 偏移 0，读的是**本轮起跑前已轮转**的 latest.log，等价于「仅本轮」。

### 3.3 落地与门禁

| 项 | 值 |
|---|---|
| 备份 | `scripts/test/cases/RELEASE150-26.1.2.json.bak-assert32-20261102-045741` |
| sha256 改前 → 改后 | `04e09f59…150e6` → `a054c76bd49ea42c92f7cc1d859243fd67cbdc8434d8dba0916f376ded10c02d`（16,308 B） |
| EOL | 该 JSON 是 **LF-only**（`lone_lf=299, CRLF=0`），按原样保持，未做 CRLF 化 |
| 门禁 | `pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case …` → `MT_VALIDATE: OK — RELEASE150-26.1.2`（rc=0） |

未删任何断言，未把任何断言改成恒真形式；#32 的 `type`/`scope` 未动，只换证据源。

## 4. 任务 3：26.1.2 复跑结果

命令：`pwsh -NoProfile -File scripts/test/mt_case.ps1 run --version 26.1.2 --case scripts/test/cases/RELEASE150-26.1.2.json`
日志：`run/26.1.2/.tmp/case-run-rerun2.log`（exit 0）

### 4.1 MT_CHANNEL 原文

```
MT_CHANNEL: mode=autojs
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/gamemode creative
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/clear @a
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/effect clear @a
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/tp @a 100 120 100 0 0
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/time set 1000
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/xp set @a 30 levels
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/effect give @a minecraft:speed 600 1 true
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/give @a astral_dice:abyss_reagent_chip 3
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/give @a astral_dice:dice 1
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/give @a astral_dice:attack_card_medium 1
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/give @a astral_dice:jill_sign 1
MT_CHANNEL: op=shot channel=autojs status=ok detail=file=abyss_reagent_tooltip.png mode=f2 bytes=147457 tier=T1 sig=grab(File,String,Target,int,Consumer) png_ok=1 rt=com.mojang.blaze3d.pipeline.MainTarget registered src=fallback_copy:D:\MCProject\astral_dice_multiloader\run\26.1.2\prodgame\screenshots\abyss_reagent_tooltip.png
MT_CHANNEL: op=shot channel=autojs status=ok detail=file=chip_shop_ui.png mode=f2 bytes=129813 tier=T3 sig=grab(File,Target,Consumer) png_ok=1 rt=com.mojang.blaze3d.pipeline.MainTarget registered src=fallback_copy:D:\MCProject\astral_dice_multiloader\run\26.1.2\prodgame\screenshots\2026-11-02_04.58.29.png
MT_CHANNEL: op=shot channel=autojs status=ok detail=file=release150_guide.png mode=f2 bytes=128679 tier=T3 sig=grab(File,Target,Consumer) png_ok=1 rt=com.mojang.blaze3d.pipeline.MainTarget registered src=fallback_copy:D:\MCProject\astral_dice_multiloader\run\26.1.2\prodgame\screenshots\2026-11-02_04.58.35.png
MT_CASE_RESULT: RELEASE150-26.1.2 = PASS
MT_CHANNEL: delegated_count=2
MT_CASES_SUMMARY: RELEASE150-26.1.2=PASS
```

**这三次 shot 发生在本轮的两处修复之前**（先跑用例、后修 `$pre`/补快照语义）。所以第 2、3 张走了 T3 + 兜底；修复后同场景复现为 T1（见 §2.5 的 CALL1–3 实证）。三张图全部由测试台自己收上来、`bytes` 非零、`tier`/`sig` 明确、`registered` 无 `add_shot_err`。

### 4.2 27 条断言逐条状态

| 流水号 | 状态 | 关键证据（测试台原文摘要） |
|---|---|---|
| 32 | PASS | `MT_ASSERT_LOG: PASS` 命中 1 行（平台输出行 1 行）`latest.log@0B(win=whole)`，明细 `[platform] … [astral_dice-version-gate/]: … mod_version=1.5.0+neoforge_26.1.2` |
| 33 | PASS | `[REL150] ver=26.1.2 gamemode=creative`（L48，KubeJS Server） |
| 34 | PASS | 同 L48，`pos=100,109,100` |
| 35 | PASS | 同 L48，`hp=20`（`food=20 xp=30 arm=0`） |
| 36 | PASS | 同 L48，`item=astral_dice:abyss_reagent_chip` |
| 37 | PASS | 同 L48，`xp=30` |
| 38 | PASS | 同 L48，`slotted=[1-9][0-9]*` 命中 |
| 39 | PASS | 同 L48，`chips=3` |
| 40 | PASS | 同 L48，`modver=1.5.0+` |
| 41 | PASS | `chip id=astral_dice:abyss_reagent_chip count=3 name=深渊试剂`（L86，KubeJS Client） |
| 42 | PASS | `tip i=0 text=深渊试剂`（L87） |
| 43 | PASS | `guide key=astral_dice.guide.entry.abyss_reagent_chip.1`：命中 2 行，**平台行 1 行**（另 1 行是 echo 源码，按弱证据记但不影响 PASS） |
| 44 | PASS | `inventory open=1 hover=`（L135） |
| 45 | PASS | `CHIP_SHOP_OPEN player=`（L244，`ChipShopOpener`） |
| 46 | PASS | `screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen`（L254） |
| 47 | PASS | `sys id=astral_dice:dice ok=1`（L816） |
| 48 | PASS | `sys id=astral_dice:attack_card_medium ok=1`（L817） |
| 49 | PASS | `sys id=astral_dice:abyss_reagent_chip ok=1`（L818） |
| 50 | PASS | `sys id=astral_dice:jill_sign ok=1`（L819） |
| 51 | PASS | `MT_ASSERT_EVAL` from=state：期望 `^"?\[REL150\] ver=\S+ gamemode=creative pos=100,-?[0-9]+,100 hp=20 .*item=astral_dice:abyss_reagent_chip`，实际 `"[REL150] ver=26.1.2 gamemode=creative pos=100,109,100 hp=20 food=20 xp=30 arm=0 slotted…` |
| 52 | PASS | from=chip：`深渊试剂` 匹配 `^"?(深渊试剂\|Abyss Reagent)"?$` |
| 53 | PASS | from=screen：`"com.merlinkitsune.astral_dice.screen.ChipShopScreen"` 匹配 `ChipShopScreen"?$` |
| 54 | PASS | `MT_ASSERT_ABSENT`：命令失败话术族（`Unknown or incomplete command` / `未找到玩家` 等）**未出现** |
| 55 | PASS | `jar=astral_dice-1.5.0+neoforge_26.1.2.jar models=158>=120 entries=2452>=1700 signs=32>=25` |
| 56 | PASS | `MT_ASSERT_CRASH: PASS` — 本次运行无新增崩溃报告（基线 1 个） |
| 57 | →VIS（DELEGATED） | `VISION_PATH=\…\run\26.1.2\screenshots\abyss_reagent_tooltip.png`；问题：画面里是否看得到物品提示框或背包界面 |
| 58 | →VIS（DELEGATED） | `VISION_PATH=\…\run\26.1.2\screenshots\chip_shop_ui.png`；问题：是否是筹码商店/容器界面 |

合计：**25 PASS + 2 delegated（vision，交 lead 判读）+ 0 FAIL + 0 BLOCKED**。

### 4.3 三张图（路径 / 字节 / mtime / sha256）与**我看到的画面内容**

| tag | 路径 | bytes | mtime | sha256 |
|---|---|---|---|---|
| abyss_reagent_tooltip | `run/26.1.2/screenshots/abyss_reagent_tooltip.png` | 147,457 | 2026-11-02 04:58:23.516 | `855b2ebe03cbcaea21842ae0df3e6373aecc15997788f0f455a1146b2739be2d` |
| chip_shop_ui | `run/26.1.2/screenshots/chip_shop_ui.png` | 129,813 | 2026-11-02 04:58:29.412 | `6a96d75ce9bdcbab1849450ea03156eb843842a207d44391a5e4e0b62589a44a` |
| release150_guide | `run/26.1.2/screenshots/release150_guide.png` | 128,679 | 2026-11-02 04:58:35.313 | `a7812af5a8f65fd78c69645961d5678a8a6445cb794b9f1060c2fd730557ed12` |

三张都是 854×480（与启动参数 1280×720 按 2/3 缩减一致）、PNG 尾部 IEND 完好（`png_ok=1`）。以下是我看到的画面（只描述观察，不代替 lead 判 PASS）：

1. **abyss_reagent_tooltip.png**：画面主体是**创造模式物品栏**（分页标题「1 / 3」，左侧分类标签、左上角「建筑方块」页签被选中，网格里是木板/木头/台阶/楼梯等方块），右下角浮着一个**黑底紫红描边的物品提示框**：标题行 `深渊试剂`（紫红）、副标题 `星之骰戏` + 英文 `Astral Dice`、`栏位: 筹码`、下方效果描述 `装备时治愈层数 +8。触发治愈回血时: 获得实际治疗量 ×2 的伤害吸收`，底部一行 `当前治愈: 8 / ?`。右上角有 Jade/信息面板样式的紫框文字「用 W、A、S 和 D 来移动 用空格来跳跃」，左侧站着玩家皮肤模型（红黑立领外套 + 粉白长发）。底部是快捷栏与背包图标。
2. **chip_shop_ui.png**：画面主体是**自定义容器界面**——金色描边、黑金棋盘格背景，顶部有金色标题条（两字，字形偏糊，我按形状读作「筹码」，不敢逐字保证），中间一排 5 个带金框的槽位：最左是叠放的硬币状物品并标「100」，第 2 槽是头像方块（下方标「托头盔」?)、第 3 槽是蓝白弹体状物品（标「跃迁引擎」?)、第 4 槽红白靶心图标（标「标靶」）、第 5 槽硬币标「3」；下方是一个大的空网格容器，**玩家的快捷栏就嵌在这个空网格里**（能看到星之骰戏的物品）。背景是世界（草地、天空、右侧建筑工地）。
3. **release150_guide.png**：画面主体是**打开的帕秋莉手册**式书本——书壳是玫红色，左右两页米黄纸。左页标题「恋的规则书」，正文是中文段落，开头可见「感谢你游玩《星之骰☆》！」「…可以在本书手册详细了…」等（字号小，我只能确认到这几处）；右页标题「类别」，下方是 8 个分类图标（2 行 × 4 列，含书卷/包裹/方块等小图）。背景是世界（草地、天空、右侧紫色与青色方块建筑）。

### 4.4 日志核查（新日志 = 本轮 latest.log，起跑前已轮转）

| 项 | 结果 |
|---|---|
| A 类 `Missing sound for event` | **0 行**（`grep -c` = 0）——上一轮 8 条已归零，本轮复现为 0 |
| 版本门行（#32 证据） | `grep/awk` 物理行 **760**；测试台当次报 `L762`（两者内容同一行，工具行号计数差 2，按内容核对过） |
| patchouli 解析错误 | **0**（`grep -ciE 'patchouli.*(error\|failed to parse)'` = 0） |
| 本模组 WARN/ERROR（logger 含 astral） | **1 条**：`L506 [0211月2026 04:46:00.045] [modloading-worker-0/WARN] [com.merlinkitsune.astral_dice.event.WaystoneWarpCompat/]: [Astral Dice] Failed to register Waystone teleport integration: java.lang.ClassNotFoundException: net.blay09.mods.waystones.api.event.WaystoneTeleportEvent$Pre`（与上一轮同一条，属可选联动缺失，不影响加载） |
| 崩溃报告 | `run/26.1.2/prodgame/crash-reports` 仅基线 1 个，本轮无新增（断言 #56） |

### 4.5 收停核验

- `pwsh -NoProfile -File scripts/test/mt.ps1 --version 26.1.2 --phase stop --force` → `MT_CLEANUP: 已收停 1 个本流程进程（范围：26.1.2）`、`MT_CLEANUP_RESULT: OK`、`MT_STOP: OK (1s)`。
- 收停后实测：`Get-NetTCPConnection -State Listen`（25565/25575/25585）→ **无监听**；`java.exe` 7 个进程，**无一个命令行含 `run/26.1.2`**；`prod-run.pid` 里 33508 → `alive=0`；`/health` 无响应。
- 未对任何他人进程执行 kill；`run/1.20.1`、`run/1.21.1`、`fabric-1.20.1` 未触碰。

## 5. 遗留与说明（不改结论，但要留痕）

1. **`.mt_shots.json` 新增了通道验证用的探针 tag**（`probe_race`×3、`probe_collide`、`probe_seq1..3`、`probe_channel_r2`）。都经 `Add-MtShot` 登记（硬门未被绕过），但清单里因此多了探针条目；如需干净清单，按 tag 删除即可（本轮未删，以免动到他人/历史条目）。
2. `run/26.1.2/screenshots` 里还有 9 月留下的 `fix3_on_20260926-230116.png` 等旧图（本轮只轮转了三张 tag 图与 gameDir 侧）。它们不再会污染认领（认领要求 mtime ≥ 本档起点），但体积较大，可择日清理。
3. 日志行号在两套工具间有 ±2 的差异（grep/awk 的物理行 vs 测试台的行号），本报告已注明具体行文，按**行内容**核对即可。
4. vision 57/58 按纪律交 lead 判读；上面的画面描述只是观察记录。
5. 本轮**没有**改动模组源码 / lang / 资源 / `build.gradle`；**没有** `git add/commit/push/tag`。改动的文件只有 `scripts/test/lib/Mt.Channel.psm1`、`scripts/test/tools/selftest_shot_channel.ps1`（新增自测）、`scripts/test/cases/RELEASE150-26.1.2.json`（仅 #32 的 pattern）。

## 6. 附件索引（本轮产生的取证文件）

- `run/26.1.2/.tmp/case-run-rerun2.log` — 用例完整输出（MT_CHANNEL / 27 条断言 / MT_CASE_RESULT）
- `run/26.1.2/.tmp/_selftest_out.txt` — 33 项离线自测输出（UTF-8）
- `run/26.1.2/.tmp/_probe_race.txt` — 同 tag 连拍 3 次全部 T1 的实机回归
- `run/26.1.2/.tmp/_live_collide.txt` — 同名旧图诱饵回归
- `run/26.1.2/.tmp/_shot_audit.txt` — 两处 screenshots 目录清单与 ready 判定
- `run/26.1.2/.tmp/_stop_verify.txt` — 收停后端口/进程/health 核验
- `run/26.1.2/.tmp/_assert32_probe.txt` — 断言 #32 改判前后对照探针
- `run/26.1.2/.tmp/prod-run-r2.log` — 本轮游戏侧启动/运行日志
