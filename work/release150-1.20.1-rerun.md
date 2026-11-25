# RELEASE150 · forge-1.20.1 复跑验收（2026-11-02）

> 执行者：实机验证工程师（DSH 子会话）。判据来自 `work/testbench-judgement-fix.md`（508 行，修复面仅 `scripts/test/**`）。
> 交付性质：**vision 两条不由本报告判 PASS**，只给 DELEGATED + 图路径，交 lead 看图裁决。

## 0. 结论速览

| 项 | 结果 |
|---|---|
| 用例最终判 | **PASS**（`MT_CASE_RESULT: RELEASE150-1.20.1 = PASS`，`MT_CHANNEL: delegated_count=2`） |
| 27 条断言 | 25 PASS + 2 DELEGATED（vision）。**0 FAIL / 0 BLOCKED / 0 INCONCLUSIVE** |
| 三张 PNG 本轮自产 | **是**（mtime 04:14:22.991 / 04:14:24.726 / 04:14:26.046，均 ≥ 窗口起点 04:14:20.057；894 760 B / 3 738 010 B / 893 901 B，均 ≥ 20 480 B） |
| 截图六档签名 | 第 1 档即打通：`tier=T1 sig=grab(File,String,RenderTarget,Consumer)`；上一轮的三条 `java() is no longer supported` 全部消失 |
| `.mt_shots.json` 本轮登记 | 3 条，`ts` 分别在 04:14:22.883 / 04:14:24.453 / 04:14:25.926（均在窗口内） |
| 判据 bug | 发现 **2 条**（B1 阻塞级、B2 失真级）+ 2 条潜伏项（B3/B4）。**未擅自改 `scripts/test/**`** |
| 收尾 | 端口全释放、无本流程 java 残留、mods 复原为 7 项 dev 集、`saves/testworld` 保留、GradleDaemon 未动 |

### 必须先告诉 lead 的两件事

1. **本轮不是「直接照任务里的命令跑一遍」就能成**：`run/1.20.1/mods` 在上一轮收尾后是 **dev 形态（7 项）**，prod 模组集被移到了 `run/1.20.1/.tmp/mods-prod-used/`（36 个 jar）。按任务给的 `prodlaunch.py --quickplay testworld` 直接启动，会在 04:06:40 崩在 `Mod autojs requires rhino 2001 or above`（dev 形态的 rhino/KubeJS 由 `build.gradle` 提供，不落在 `run/mods`）。本轮按上一轮 `assemble_env.ps1` 的口径重建了 prod 环境（见 §3），**这是「production 形态」的前提，不是可选步骤**。
2. **修好的判据对「`.mt_shots.json` 清空成 `[]`」这一状态会直接崩**（B1，§11）：`Get-MtShots` 对 `[]` 返回 `$null`，`Add-MtShot` 静默失败、`Get-MtCurrentShots` 抛 `不能对值为 Null 的表达式调用方法`，用例在 vision 断言处整轮 **ERROR**（第 1 次跑就是这样，91 行输出停在 assert 56 之后）。本轮的处理是**把清单改造成同语义的空集群 `{"shots":[]}`**（不是手写条目），让 `Add-MtShot` 自己登记 3 条——登记动作仍由判据代码完成，没有手工登记/伪造任何一条。

---

## 1. 端口预检

启动前（04:06 与 04:09 两次实测）与收尾后（04:16）三次均为：

```
PORT 25565 free
PORT 25585 free
PORT 25575 free
```

- 无占用 ⇒ **没有等待**，也没有换 autojs 端口（沿用 `config/autojs.json` 的 `port=25585`）。
- 本轮**没有 kill 任何进程**（无论在预检期还是收尾期）。

## 2. 日志 / 截图 / 清单轮转清单（全部 Move-Item，无删除）

### stamp = `20261102-040616`（首次启动前）

| 对象 | 轮转前 | mtime | sha256（前 8…后 5） | 去向 |
|---|---|---|---|---|
| `run/1.20.1/logs/latest.log` | 7 108 016 B | 2026-11-02 03:29:47 | `36c2f5b1…7cea3` | `latest.log.prev-20261102-040616` |
| `run/1.20.1/.tmp/prod-run.log` | 6 234 212 B | 2026-11-02 03:29:47 | `82133956…dc17e` | `prod-run.log.prev-20261102-040616` |
| `screenshots/abyss_reagent_tooltip.png` | 1 261 572 B | 03:24:47.923 | `2b5c882f…7b183` | `*.prev-20261102-040616` |
| `screenshots/chip_shop_ui.png` | 3 512 368 B | 03:22:17.571 | `f5e9beb8…9111b` | `*.prev-20261102-040616` |
| `screenshots/release150_guide.png` | 846 731 B | 03:21:17.262 | `a38442bd…bf401` | `*.prev-20261102-040616` |
| `screenshots/.mt_shots.json` | 10 244 B（旧格式 `{run_id,shots}`，**85 条**历史条目，含 2026-09/10 的 diag_* / chip-logic 等） | 03:25:09 | `7797790c…813866` | `.mt_shots.json.bak-20261102-040616`，随后写成 `[]`（2 B，UTF8 无 BOM） |

### stamp = `20261102-040949`（A1 崩溃后、重建环境前）

| 对象 | 轮转前 | sha256 | 去向 |
|---|---|---|---|
| `logs/latest.log` | 25 512 B（A1 崩溃日志） | `91438f63…7e249` | `latest.log.prev-20261102-040949` |
| `.tmp/prod-run.log` | 32 999 B（A1 启动器输出） | `05bcac0f…574e6` | `prod-run.log.prev-20261102-040949` |

> 旧图与旧清单**全部保留在同目录**（改名而非删除），可直接对账上一轮的 03:21–03:24 三张图。

## 3. 环境前置条件（production 形态的必要重建）

`scripts/test/mt.conf` 实测（本地、gitignored）：`MT_PACK_MODS_FORGE="D:/.minecraft/versions/1.20.1-Forge 模组测试/mods"`、`MT_AUTOJS_ENDPOINT="http://127.0.0.1:25585/mcp"`、`MT_AUTOJS_TIMEOUT_MS=15000`。

**A1（04:06:27，按任务命令直跑）失败原文**：

```
net.minecraftforge.fml.ModLoadingException: Mod 'autojs' requires 'rhino' 2001 or above
Currently, 'rhino' is not installed
Mod ID: 'rhino', Requested by: 'autojs', Expected range: '[2001,)', Actual version: '[MISSING]'
```

崩溃报告：`run/1.20.1/crash-reports/crash-2026-11-02_04.06.40-fml.txt`（10 516 B，**保留**，并计入 crash 断言基线 → 本轮基线 6 个）。

**重建动作（stamp2，与上一轮 `assemble_env.ps1` 同口径）**：

1. `run/1.20.1/mods` 现存 7 项 → `Move-Item` 到 `run/1.20.1/.tmp/mods-dev-backup-rr2/`（astral_dice 2 630 735 B、AutoJS-dev 165 964 B、`.bak-20261022b` 164 593 B、`.bak-20261022c` 164 777 B、`.reobf-20261022` 167 358 B、starengine 102 013 B、ysm 63 269 843 B）。
2. `run/1.20.1/.tmp/mods-prod-used/*`（36 个 jar）→ 复制回 `mods/`。结果 **MODS_COUNT=36 / 137 705 444 B**。
3. testworld：`saves/` 为空 ⇒ 从 `scripts/test/resources/testworld-seed-1.20.1.zip` 解到 `.tmp/seedx-rr2/`，内层 `testworld` 复制到 `run/1.20.1/saves/testworld`（11 文件 / 2 227 455 B）。

关键 jar 指纹（本轮实读）：

| jar | 字节 | 说明 |
|---|---|---|
| `astral_dice-1.5.0+forge_1.20.1.jar` | 2 660 134 | 被测模组（prod 构建，sha256 `FD6D8D82…`） |
| `starengine-forge-1.20.1-1.1.0.jar` | 103 605 | 共享库 |
| `AutoJS-forge-1.20.1-1.0.0.jar` | 167 358 | 实机通道 |
| `[犀牛] rhino-forge-2001.2.3-build.10.jar` | 1 798 244 | KubeJS 前置（**注意文件名的 `[犀牛] ` 前缀**，见 B3） |
| `kubejs-forge-2001.6.5-build.26.jar` | 1 658 792 | 脚本内核 |
| `patchouli-1.20.1-85-forge.jar` | 642 732 | 手册（pack 里那份 `[帕秋莉手册] Patchouli-…` 重名未复制） |

`config/autojs.json` 实读：`enabled=true, unrestricted=true, bindAddress=127.0.0.1, port=25585, enableServerSide=true, enableClientSide=true, token="", evalTimeoutMs=5000, maxResultChars=100000, logEval=true`。

## 4. 启动

| 项 | 值 |
|---|---|
| 命令 | `python run/1.20.1/.tmp/prodlaunch.py --quickplay testworld`（cwd=`run/1.20.1/.tmp`） |
| 调用时刻 | 2026-11-02 **04:09:53** |
| PID | **33212**（脚本打印 `started pid 33212`；`.tmp/prod-run.pid` 同步落盘） |
| 日志 | `run/1.20.1/.tmp/prod-run.log` |
| classpath | 94 项，`missing: 0`；`unresolved placeholders: none` |
| argv | 62 项；`--quickPlaySingleplayer=testworld`（`=` 形式，避免 BootstrapLauncher 重复 key） |
| 启动方式 | `DETACHED_PROCESS`（0x00000200）+ `stdin=DEVNULL`，调用即返回，客户端独立存活 |
| 进世界 | 04:11:03 前（Embeddium `OpenGL 4.6.0 NVIDIA 621.01` / `RTX 5090`，36 mods 正常加载） |
| AutoJS 端点 | **04:11:03 就绪**（poll 第 6 次 `ok=True`，`Test-MtAutoJsEndpoint` 与 `mt_launch.ps1:1124` 同一入口） |
| 端口 | 沿用 25585（未被并发流程占用，无 `--autojs-port`） |

## 5. 用例执行

```
# 前置：validate 一次
pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case scripts/test/cases/RELEASE150-1.20.1.json
MT_VALIDATE: OK — RELEASE150-1.20.1

# 正式复跑（04:14:19 起，约 100 s 完成）
pwsh -NoProfile -File scripts/test/mt_case.ps1 run --version 1.20.1 --case scripts/test/cases/RELEASE150-1.20.1.json
```

运行头两行原文：

```
MT_INFO: CASE_EVIDENCE_WINDOW: RELEASE150-1.20.1 截图证据窗口起点 2026-11-02 04:14:20.057
MT_CASE_WINDOW: RELEASE150-1.20.1 — 断言窗口自本用例起（MT_SNAPSHOT: OK — 1.20.1 run=20260926-230116 window=case latest=1671105B debug=3150625B probe=0B anchors=3）
```

> 前后一共跑了 **两次**：第 1 次（04:11:24–04:12:59）在 vision 断言处 `MT_CASE: ERROR — 不能对值为 Null 的表达式调用方法`（B1），但**三张图已由六档签名成功落盘**；第 2 次（04:14:19–04:16:04）在清单改造后跑完整，本文所有断言状态取自第 2 次。

## 6. `MT_CHANNEL` 原文

### 6.1 通道与命令原语

```
MT_CHANNEL: mode=autojs
MT_CHANNEL: op=command channel=autojs status=ok detail=cmd_via=autojs_command cmd=/gamemode creative @a
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
```

### 6.2 截图三连（`op=shot`，tier / sig / bytes 原样）

```
MT_CHANNEL: op=shot channel=autojs status=ok detail=file=abyss_reagent_tooltip.png mode=f2 bytes=0 tier=T1 sig=grab(File,String,RenderTarget,Consumer)
MT_CHANNEL: op=shot channel=autojs status=ok detail=file=chip_shop_ui.png mode=f2 bytes=0 tier=T1 sig=grab(File,String,RenderTarget,Consumer)
MT_CHANNEL: op=shot channel=autojs status=ok detail=file=release150_guide.png mode=f2 bytes=0 tier=T1 sig=grab(File,String,RenderTarget,Consumer)
```

### 6.3 收口

```
MT_CASE_RESULT: RELEASE150-1.20.1 = PASS
MT_CHANNEL: delegated_count=2

MT_CASES_SUMMARY: RELEASE150-1.20.1=PASS
MT_CHANNEL: delegated_count=2
```

> **`bytes=0` 是假读数，不是空图**（见 B2）：截图端口轮询到「文件刚被创建、内容还没写完」就认成功，`Get-Item` 那一刻读到 0 字节；三张图最终 894 760 / 3 738 010 / 893 901 B，vision 硬门重新 stat 时拿到的是真尺寸。**六档没有全败**，第 1 档 `T1` 就落盘了。

## 7. 27 条断言逐条状态（含与上一轮的差异）

编号＝用例 `asserts` 顺序；`step`＝stdout 里的步骤号（32–56 对应 1–25，57/58 对应 26/27）。

| # | 类型 | 断言内容（要点） | 本轮 | 证据 | 与上一轮差异 |
|---|---|---|---|---|---|
| 1 | log(whole) | `mod_version=1.5.0+forge_1.20.1` | PASS | `latest.log@0B(win=whole)` L333 `astral_dice-version-gate`,平台行 1 | 无变化 |
| 2 | log | `[REL150] ver=1.20.1 gamemode=creative` | PASS | L… `autojs-eval#23`，平台行 1 | 无变化 |
| 3 | log | `pos=100,-?[0-9]+,100` | PASS | 同上（`pos=100,108,100`） | 无变化 |
| 4 | log | `hp=20` | PASS | 同上 | 无变化 |
| 5 | log | `item=astral_dice:abyss_reagent_chip` | PASS | 同上 | 无变化 |
| 6 | log | `xp=30` | PASS | 同上 | 无变化 |
| 7 | log | `slotted=[1-9][0-9]*` | PASS | `slotted=6` | 无变化 |
| 8 | log | `chips=3` | PASS | `chips=3` | 无变化 |
| 9 | log | `modver=1.5.0+` | PASS | 同上 | 无变化 |
| 10 | log | `chip id=… count=3 name=(深渊试剂\|Abyss Reagent)` | PASS | client 行 `tip…`/`chip…` 平台行 1 | 无变化 |
| 11 | log | `tip i=0 text=.+` | PASS | `[REL150] tip i=0 text=深渊试剂` | 无变化 |
| 12 | log | `guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=.+` | PASS | **命中 2 行：1 条 `[echo]`（KubeJS 源码回显）+ 1 条 `[platform]`**；只认平台行 | 无变化（回显剔除按新判据生效） |
| 13 | log | `inventory open=1` | PASS | `inventory open=1 hover=480,270` | 无变化 |
| 14 | log | `CHIP_SHOP_OPEN player=` | PASS | `CHIP_SHOP_OPEN player=DevTester offers=… purchased=0 refreshes=3` | 无变化 |
| 15 | log | `.*ChipShopScreen` | PASS | `screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen` | 无变化 |
| 16 | log | `sys id=astral_dice:dice ok=1` | PASS | `autojs-eval#9` 平台行 1 | 无变化 |
| 17 | log | `sys id=astral_dice:attack_card_medium ok=1` | PASS | 同上 | 无变化 |
| 18 | log | `sys id=astral_dice:abyss_reagent_chip ok=1` | PASS | 同上 | 无变化 |
| 19 | log | `sys id=astral_dice:jill_sign ok=1` | PASS | 同上 | 无变化 |
| 20 | eval(from=state) | ver/gamemode/pos/hp/item 组合正则 | PASS | 实值 `"[REL150] ver=1.20.1 gamemode=creative pos=100,108,100 hp=20 food=20 xp=30 arm=0 slotted=6 c…` | 无变化 |
| 21 | eval(from=chip) | `^("?)(深渊试剂\|Abyss Reagent)("?)$` | PASS | 实值 `"深渊试剂"` | 无变化 |
| 22 | eval(from=screen) | `ChipShopScreen"?$` | PASS | 实值 `"com.merlinkitsune.astral_dice.screen.ChipShopScreen"` | 无变化 |
| 23 | absent | 命令/选择器错误话术 0 命中 | PASS | 未出现（`win=case`） | 无变化 |
| 24 | jar | models≥120 / recipes≥100 / entries≥1700 / signs≥25 | PASS | `models=158 recipes=125 entries=2032 signs=32` | **recipes=125**（新判据把 `recipe`/`recipes` 两个目录都数；上一轮未单列此值） |
| 25 | crash | 无新增崩溃报告 | PASS | 基线 **6 个**（含 A1 崩溃 `crash-2026-11-02_04.06.40-fml.txt`） | **基线 +1**（A1 dev 形态崩溃进基线）；判定口径无变化 |
| 26 | vision | `image=abyss_reagent_tooltip` | **DELEGATED** | 四道硬门全过（见 §9）：`VISION_PATH=D:\MCProject\astral_dice_multiloader\run\1.20.1\screenshots\abyss_reagent_tooltip.png` | **上一轮是「DELEGATED 通过」但其实无本轮证据**（图 mtime 03:24 < 窗口 03:28 且当轮截图 op 全失败）；本轮图 mtime 04:14:22.991 ≥ 04:14:20.057 |
| 27 | vision | `image=chip_shop_ui` | **DELEGATED** | 同上：`…\screenshots\chip_shop_ui.png` | 同上（上一轮 03:22:17.571 < 03:28；本轮 04:14:24.726 ≥ 04:14:20.057） |

**合计：25 PASS + 2 DELEGATED**。两条 vision 的最终裁决权在 lead：

- 26 的问题：*画面里是否看得到物品提示框（tooltip）或背包界面？只回答「看到了」或「没看到」，并用一句话说明画面主体。*
- 27 的问题：*画面是否是筹码商店/容器界面（而不是普通世界画面）？只回答「是商店」或「不是商店」。*

## 8. `[REL150]` 探针行原文

本轮（第 2 次跑，窗口内）server 侧整行：

```
autojs-eval#23: [REL150] ver=1.20.1 gamemode=creative pos=100,108,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+forge_1.20.1
```

同一次会话里第 1 次跑留下的那一行（同一 `latest.log`，因客户端全程未重启）：

```
autojs-eval#23: [REL150] ver=1.20.1 gamemode=creative pos=100,106,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+forge_1.20.1
```

其余探针（client 侧，**平台行原文，逐字**）：

```
[REL150] chip id=astral_dice:abyss_reagent_chip count=3 name=深渊试剂 tipn=7 path=screen
[REL150] tip i=0 text=深渊试剂
[REL150] guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=装备时治愈层数 +2；触发治愈回血时，获得「实际治疗量 ×2」的伤害吸收。
[REL150] inventory open=1 hover=480,270
[REL150] screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen
[REL150] sys id=astral_dice:dice ok=1 name=骰子
[REL150] sys id=astral_dice:attack_card_medium ok=1 name=攻击(中)
[REL150] sys id=astral_dice:abyss_reagent_chip ok=1 name=深渊试剂
[REL150] sys id=astral_dice:jill_sign ok=1 name=调酒师立牌
[astral_dice] CHIP_SHOP_OPEN player=DevTester offers=piggy_bank_chip+electric_glove_chip+cursed_sword_chip purchased=0 refreshes=3
[astral_dice] CHIP_SHOP_OPEN player=DevTester offers=buffer_shield_chip+atm_chip+airbag_chip purchased=0 refreshes=3
```

> ⚠️ `latest.log` 里**同时存在这些字符串的 KubeJS 源码回显行**（形如 `autojs-eval#N: [REL150] chip id=' + sid + ' count=' + total …`），即探针 JS 源码被回显进日志。上面引的是 `[platform]` 行；断言侧已由 `Test-MtEvalSourceEcho` 把回显行剔出计数（见 B4），但**人工读日志时必须区分**，否则会把源码模板误当证据。

## 9. 三张本轮新图

| 用例断言 | 文件 | 字节 | mtime | sha256 |
|---|---|---|---|---|
| #26 vision `abyss_reagent_tooltip` | `run/1.20.1/screenshots/abyss_reagent_tooltip.png` | **894 760** | **2026-11-02 04:14:22.991** | `9af4d20e451db4fa6e1e7a5a7dcc396e17ede62ee5db0438d0ff7c3a6f295f98` |
| #27 vision `chip_shop_ui` | `run/1.20.1/screenshots/chip_shop_ui.png` | **3 738 010** | **2026-11-02 04:14:24.726** | `442885205a9814932ec736e1c35a512cee07d01d2f0d18df5a0c5e95f764b116` |
| （无对应断言，留档：手册页） | `run/1.20.1/screenshots/release150_guide.png` | **893 901** | **2026-11-02 04:14:26.046** | `76a2c67759fad1dd23d1a07d9f31c48587addb78630aa8c06fc976103b2add10` |

三张均为合法 PNG、`1920x1080`（读 IHDR 验证），`bytes ≥ 20 480`、`mtime ≥ 窗口起点 2026-11-02 04:14:20.057`。

`.mt_shots.json`（本轮由 `Add-MtShot` 自行写入，`run_id=20260926-230116`）：

```json
{
  "run_id": "20260926-230116",
  "shots": [
    { "file": "abyss_reagent_tooltip.png", "tag": "abyss_reagent_tooltip", "mode": "f2", "ts": 1793564062.883 },
    { "file": "chip_shop_ui.png", "tag": "chip_shop_ui", "mode": "f2", "ts": 1793564064.453 },
    { "file": "release150_guide.png", "tag": "release150_guide", "mode": "f2", "ts": 1793564065.926 }
  ]
}
```

（`path` 字段已省略显示，实际三条都带绝对路径；`ts` 分别 = 04:14:22.883 / 04:14:24.453 / 04:14:25.926，均在本轮窗口内。）

vision 四道硬门的实际取值：

| 门 | 26 | 27 |
|---|---|---|
| ① 截图 op 无失败登记 | 通过（`status=ok`） | 通过 |
| ② 当代清单含该 tag 且文件在当前世代 | 通过（`ts` 在窗口内、`Get-MtCurrentShots -SinceUnix` 收录） | 通过 |
| ③ 文件存在 | 存在 | 存在 |
| ④ `bytes≥20480` 且 `mtime≥窗口起点` | 894 760 B / 04:14:22.991 | 3 738 010 B / 04:14:24.726 |

## 10. 日志归类

统计对象：`run/1.20.1/logs/latest.log`（本轮会话，2 570 587 B；`latest.log@1671105B` 为用例断言时的大小）。

### 10.1 硬禁串（必须 0）

| 串 | 命中 |
|---|---|
| `err: 5x` | **0** |
| `Failed to load native lib` | **0** |
| `must be registered` | **0** |
| `Unsatisfied runtime environment` | **0** |
| `试剂-深渊`（全日志） | **0** |

### 10.2 生效版本

```
[0211月2026 04:10:05.596] [modloading-worker-0/INFO] [astral_dice-version-gate/]: [Astral Dice] 版本互通门槛(Version Gate):mod_version=1.5.0+forge_1.20.1 → 网络互通号=1.5;正式版按二号位(major.minor)互通,含 SNAPSHOT 的构建只与完整版本串相同的快照互通。
```

### 10.3 ERROR（共 4 条，逐条归属）

| # | 原文摘录 | 归属 | 是否本模组 |
|---|---|---|---|
| 1 | `[Render thread/ERROR] [Embeddium-MixinTaintDetector/]: Mod mixin into Embeddium internals detected. This instance is now tainted.` + 随后 `Mod(s) [oculus] are modifying Embeddium class …SodiumGameOptions` | Embeddium 的第三方 mixin 污点检测（`oculus`，整合包自带） | 否 |
| 2 | `[Worker-ResourceReload-3/ERROR] [Enigmatic Legacy/ERROR]: Encountered exception when trying to fetch devoted believers from Enigmatic Legacy's repository:` | Enigmatic Legacy 联网取数据失败（离线环境） | 否 |
| 3 | `[Worker-ResourceReload-3/ERROR] [Enigmatic Legacy/CATCHING]: Catching` | 同上（catch 语句所在行） | 否 |
| 4 | `[Render thread/ERROR] [net.minecraft.server.ServerAdvancementManager/]: Parsing error loading custom advancement enigmaticaddons:recipes/generic/the_bless: Unknown item id 'enigmaticlegacy:sacred_crystal'` | enigmaticaddons 的进度数据引用了不存在的物品 id（第三方数据错误） | 否 |

**`com.merlinkitsune.astral_dice` 的 ERROR = 0**（`grep '/ERROR] \[astral_dice/'` 计数 0；含 `merlinkitsune` 的 ERROR/WARN 亦为 0）。

### 10.4 WARN（共 264 条）

- **本模组 logger（`astral_dice/…`）= 1 条**：

```
[0211月2026 04:10:14.772] [Worker-ResourceReload-12/WARN] [astral_dice/MixinRuntimeGate/]: [星之骰戏] Mixin 子系统自检未完成（不影响加载，仅记录）：NullPointerException: Cannot invoke "Object.getClass()" because "obj" is null
```

  → **同一行在上一轮日志 `latest.log.prev-20261102-040616` 里也有 1 条**，属既有行为、非本轮新增；自报「不影响加载，仅记录」。严格按「本模组 WARN 必须为 0」的口径，这条**算 1 条偏离**，如实列出，不粉饰。
- **Forge 侧、但点名本模组物品的 `WARN` = 23 条**，全部同一形态（注册表 ID 与存档既存分配不一致，加物品后必然出现）：

```
[Render thread/WARN] [net.minecraftforge.registries.ForgeRegistry/REGISTRIES]: Registry minecraft:item: Object did not get ID it asked for. Name: astral_dice:abyss_reagent_chip Expected: 1379 Got: 1406
```

  归属：Forge 注册表（logger 不是本模组），触发原因是测试存档早于本轮物品表；不阻断注册（全部物品 `sys … ok=1` 已证）。
- 其余 240 条为 Embeddium / ModernFix / Enigmatic Legacy / I18nUpdateMod 等第三方噪声（含 `Static binding violation`、`Enigmatic Legacy` 网络失败伴随栈等）。

## 11. 判据 bug（只报告，未改 `scripts/test/**`）

### B1【阻塞级】清空成 `[]` 的清单会让 vision 断言整轮 ERROR

- 现象：第 1 次跑（04:11:24 起）25 条断言全 PASS、三张图全部落盘，但随后 `MT_CASE: ERROR — RELEASE150-1.20.1.json: 不能对值为 Null 的表达式调用方法。`，`MT_CASES_SUMMARY: RELEASE150-1.20.1=ERROR`；vision 两条**既没有 PASS 也没有 BLOCKED**。
- 根因链（代码级）：`scripts/test/lib/Mt.Paths.psm1:300-306` `Get-MtShots` 用 `Get-Content | ConvertFrom-Json -AsHashtable`；当清单内容为 `[]`（顶层空数组）时返回 **`$null`**，不是 `@{}`（`catch` 只兜异常，不兜空值）。于是：
  1. `Add-MtShot`（`:326-329`）第一句 `$data.ContainsKey('run_id')` → `$null` 上调方法 → 抛错 → **被 `Mt.Channel.psm1:1010-1013` 的 `try { } catch { }` 静默吞掉** ⇒ 清单永远保持 `[]`，三条截图**全部没有 tag 登记**（这正是「静默失败」类缺陷，与本次修复目标同源）。
  2. `Get-MtCurrentShots`（`:365-366`）同样 `$data.ContainsKey(...)` → 抛 `不能对值为 Null 的表达式调用方法` → 冒泡到 vision 断言（`mt_case.ps1:1964`）→ **用例整轮 ERROR**。
- 影响面：任何「按任务口径把 `.mt_shots.json` 清空成 `[]`」的复跑都会踩到；且表现是 ERROR 而不是 BLOCKED，`on_fail=keep_game_running` 之外的清理/汇总逻辑也不会走完。
- 建议（未实施）：`Get-MtShots` 里对 `$null`/数组结果统一回落 `@{}`；`Add-MtShot` 的 `try/catch` 至少要 `Write-Warning` 落到 `MT_CHANNEL`；vision 断言对 `Get-MtCurrentShots` 的异常应转 BLOCKED。
- 本轮绕过方式（**不是**手工登记）：把清单改造为同语义空集群 `{"shots":[]}`（对象、无条目），使 `Add-MtShot` 的 `ContainsKey` 可用、按其原有逻辑自行 append；3 条条目全部由判据代码写入（§9 的 JSON 即其产物）。原文件已备份为 `.mt_shots.json.bak-20261102-040616`。

### B2【失真级】截图端口把「0 字节半成品」认成成功，`MT_CHANNEL … bytes=` 读成 0

- 现象：三条 `op=shot` 全部 `status=ok` 但 `bytes=0`，而实际文件 894 760 / 3 738 010 / 893 901 B。
- 根因：`Mt.Channel.psm1:983-997` 的轮询只要「目录里出现 eval 前不存在且 `mtime≥sinceUnix` 的 PNG」就认成功，**不校验长度**；`grab` 在 `Util.ioPool` 异步落盘，先建文件后写内容，轮询在写内容前就命中。随后 `:1008-1009` 立刻 `Get-Item … Length` → 0。
- 影响：`bytes=` 字段不可作为证据（本轮实际靠 vision 硬门重新 `stat` 才拿到真尺寸 894 KB 级）；若某档只落 0 字节空文件且后续再无写入，会被误判为成功。
- 建议（未实施）：轮询命中后要求 `Length -gt 0`（更稳：PNG 尾块 `IEND` 存在）再认成功，或移除已落盘的空文件重试下一档。

### B3【潜伏】`rhino` 存在性复核会被文件名前缀误伤

`run/1.20.1/mods` 里的犀牛 jar 名为 `[犀牛] rhino-forge-2001.2.3-build.10.jar`，**不匹配 `rhino*` glob**。`scripts/test/mt_env.ps1:1560-1564` 若在 E 阶段跑，会把「rhino 已装」判成缺前置 → BLOCKED。本轮未跑 E 阶段，故未触发；记录备查。

### B4【观察项】`[echo]` 行确实会进 `latest.log`

断言 #12（guide）命中 2 行：`[echo] console.info('…')` 与 `[platform] … autojs-eval#…`。新判据的 `Test-MtEvalSourceEcho` + `Show-MtHitLines` 处理正确（只认平台行，PASS），本轮复核通过。

## 12. 收尾

```
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.20.1 --phase stop --force

MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
MT_CLEANUP_SAVES: KEPT（未指定 --purge-saves）
MT_CLEANUP: 已收停 1 个本流程进程（范围：1.20.1）
MT_CLEANUP_RESULT: OK
MT_STOP: OK (0s) — 收停完成，无本流程残留进程
```

收尾后复核（04:16 之后）：

| 项 | 结果 |
|---|---|
| 端口 25565 / 25585 / 25575 | 全 **free** |
| 本流程 java 残留 | **0**（`Win32_Process` 中无 `run\1.20.1` / `1.20.1-Forge` 命令行进程） |
| GradleDaemon | **未动**：PID 37020、5088 仍在（`--version` 保留守护进程，`--force` 只管本流程） |
| 会话前既有的其他 java | 29924（gradlew 启动器）、37252（fabric loom server）、28820（zulu-25 javac）**仍在且未被我触碰**（与 04:06 基线一致） |
| `saves/` | **保留**（未加 `--purge-saves`）：`saves/testworld` 存在，44 个文件（世界被本轮写入过，seed 展开时 11 个） |
| `mods/` | **已复原**：36 个 prod jar 移入 `.tmp/mods-prod-used-rr2/`（不删），dev 集 7 项移回 `mods/`，`MODS_COUNT_NOW=7`（与 04:06 会话开始时一致） |
| 三张新图 | 在盘未动（894 760 / 3 738 010 / 893 901 B，mtime 04:14:2x） |
| `.mt_shots.json` | 保留本轮 3 条登记（证据） |
| 崩溃报告 | A1 的 `crash-2026-11-02_04.06.40-fml.txt` **保留**（未删除，已计入 crash 断言基线） |

## 13. 最终判定

**PASS**（限定条件见下）。

理由：

1. 25 条机器断言全部 PASS，且**没有一条是靠回显/历史残留通过的**（#12 的回显行已被新判据剔出计数）。
2. 两条 vision 断言**本轮第一次有了合格的强证据**：图是本次会话内由 `Screenshot.grab` 现产（mtime 04:14:2x ≥ 窗口起点 04:14:20.057）、≥20 KB、且当代清单里有对应 tag。上一轮「DELEGATED 通过」的两条违例（旧图 mtime 03:21–03:24 + 当轮截图 op 全失败）**在本轮不成立**。
3. 硬禁串全 0；`试剂-深渊` 全日志 0；`abyss_reagent_chip` 名称为 `深渊试剂`；生效版本 `1.5.0+forge_1.20.1`。
4. 本模组无 ERROR；`com.merlinkitsune.astral_dice` 的 ERROR/WARN 计数 = **0/1**（那 1 条 WARN 是既有的 Mixin 自检记录行，上一轮同样存在，非本轮回归）。
5. 收尾干净：端口释放、无残留进程、saves 保留、mods 复原、GradleDaemon 未动。

**限定**：两条 vision 的**内容**我没有裁决权，只做取证交接（DELEGATED + 图路径 + sha256）。lead 看图若否，则整体判定要按 lead 的结论下调；本报告的 PASS 是「机器可判部分 + 证据齐备」意义上的 PASS。

## 14. 遗留风险

1. **B1 未修**：下一次复跑若仍按「清空成 `[]`」操作，会再次在 vision 处 ERROR。要么先把 `Get-MtShots` 的空数组回落修掉，要么沿用本轮的空集群写法（`{"shots":[]}`）。
2. **prod / dev 模组集在 `run/1.20.1/mods` 里互斥**：prod 集现在落在 `.tmp/mods-prod-used-rr2/`，`mods/` 是 dev 7 项。任何人只拿 `prodlaunch.py` 直跑都会撞 `rhino [MISSING]`（本轮 A1 即证据）。建议把「换装脚本」固化进 `mt.ps1` 的某个 phase，或在 `mt.conf` 里显式声明 prod mods 目录。
3. **`bytes=0` 字段（B2）**：下游若有人解析 `MT_CHANNEL … bytes=` 做体积门，会读到 0 而误判空图。
4. **crash 断言基线已涨到 6**：A1 的 dev 形态崩溃被计入基线，后续若想恢复 5，需要移动（不是删除）那份 `crash-*.txt`。
5. **23 条 Forge 注册表 ID 漂移 WARN**：只要测试存档早于当轮物品表就会重现；本轮不阻断（物品全部注册成功），但会持续污染 WARN 计数。干净复跑需要一份与当轮物品表同源的 `saves/testworld`（当前 seed zip 展开后会在首存时重新分配 ID）。
6. **`release150_guide.png` 没有对应断言**：用例里手册页只「尽力打开、失败不作判据」；图已产出（893 901 B），但没有任何断言锁它，视觉是否真的落在手册页仍需 lead 顺手看一眼。
