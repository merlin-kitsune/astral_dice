# 实机复测报告：地雷女 ame_overdose 自然到期结算幂等修复（2.0.0-SNAPSHOT.128，2026-10-17）

- 被测提交：`6e06cf4c`（`fix(sign): 地雷女立牌 ame_overdose 自然到期只结算一次（.128，P0 两线）`，2026-10-17 12:40:19 +0800）
- 工作树：`/mnt/f/mcproject/astral_dice_multiloader-next`（分支 `multi-dev-next`，HEAD = `6e06cf4c`）
- 实测线：`neoforge-1.21.1`（MC 1.21.1 / NeoForge 21.1.235）与 `forge-1.20.1`（MC 1.20.1 / Forge 47.4.10），两线均逐项复测
- 测试世界：`testworld`（超平坦/创造/允许命令/keepInventory；两线测后已 purge，见 §6）
- 判据通道：服务端 KubeJS 探针 `/astralkame`（`sendFailure` → `latest.log` 的 `AP_*` 行）+ tick 级采样器 `/astralkame poll`
- 探针：`scripts/test/resources/kubejs/{1.21.1,1.20.1}/server_scripts/astral_kame_probe.js`
- 靶子：本轮各臂均**不需要**实体靶（未生成任何生物）；未使用亡灵生物

---

## 0. 结论速览

| # | 判定项 | neoforge-1.21.1 | forge-1.20.1 |
|---|---|---|---|
| 1 | 主用例：一次自然到期**恰好结算一次**（love 8→4、cap 20→21），之后不再二次/三次扣层 | **PASS** | **PASS** |
| 2 | 幂等键回归：同一玩家**第二次**自然到期仍正常再结算一次（love 4→0、cap 21→22） | **PASS** | **PASS** |
| 3 | 语义①：`/effect clear` 不清算（love/cap 不变） | **PASS**（附口径说明，见 §3） | **PASS**（同） |
| 4 | 语义②：到期前卸下立牌 ⇒ 不结算（重新佩戴后 cap 仍为 4，未变 5） | **PASS** | **PASS** |
| — | 日志中 `ConcurrentModificationException` / 本特性 ERROR | 0 处 | 0 处 |

> 两线结果一致；无 FAIL、无「无法判定」项。

---

## 1. 构建与产物核对（jar 确实重建，且含修复）

```
$ ls -la --time-style=full-iso run/1.21.1/mods/astral_dice-*.jar run/1.20.1/mods/astral_dice-*.jar
... 2026-10-17 12:42:32.286044500 +0800 run/1.21.1/mods/astral_dice-2.0.0-SNAPSHOT.128+neoforge_1.21.1.jar
... 2026-10-17 12:42:37.514421300 +0800 run/1.20.1/mods/astral_dice-2.0.0-SNAPSHOT.128+forge_1.20.1.jar
```

- 两线 jar mtime（12:42:32 / 12:42:37）均**晚于**提交时间 12:40:19。
- 构建入口与结果（Watchdog 口径 `mt_build.ps1`，60s 窗口）：
  - `temp/kame128/build-1.21.1.log`：`MT_BUILD: OK (2s) — 产物已更新：astral_dice-2.0.0-SNAPSHOT.128+neoforge_1.21.1.jar`
  - `temp/kame128/build-1.20.1.log`：`MT_BUILD: OK (3s) — 产物已更新：astral_dice-2.0.0-SNAPSHOT.128+forge_1.20.1.jar`
- 开包核对 `AmeSignItem.class` 常量池含修复标记：
  - 1.21.1：`SETTLED_OVERDOSE` / `PENDING_OVERDOSE_EXPIRY` / `weakKeys` / `onServerTickPost`
  - 1.20.1：`SETTLED_OVERDOSE` / `PENDING_OVERDOSE_EXPIRY` / `weakKeys` / `onServerTick`

---

## 2. 主用例（缺陷回归）：一次自然到期只结算一次

复现序列（两线相同，玩家 `Dev`，非亡灵靶未使用）：
```
/astralkame equip stand "astral_dice:ame_sign" Dev
/astralkame set cap 20 Dev ; /astralkame set love 8 Dev
/astralkame poll on
/astralkame ovd Dev 100 0 <tag>
（等待 7 秒后）/astralkame state ...
```

### 2.1 neoforge-1.21.1 — PASS
证据文件：`run/1.21.1/logs/latest.log`

| 行号 | 时间戳 | 原文（节选） |
|---|---|---|
| 2376 | 12:45:21.038 | `AP_M0_ST:Dev:...:love=8:cap=20:...:ovdE=none:...:tick=1665` |
| 2547 | 12:45:29.167 | `AP_N1_OVD:who=Dev:ticks=100:amp=0:res=ok:ovdE=amp=0;dur=100;...:love=8` |
| 2855 | 12:45:34.093 | `AP_POLL:tick=1907:who=Dev:love=8:cap=20:ovdDur=1` |
| 2857 | 12:45:34.143 | `AP_POLL:tick=1908:who=Dev:love=4:cap=21:ovdDur=none` |
| 2941 | 12:45:39.160 | `AP_M1_ST:Dev:...:love=4:cap=21:...:loveE=amp=3;dur=-1:ovdE=none:...:tick=1997` |
| 3025 | 12:45:43.543 | `AP_POLL:on=0` |

判据：tick 1907→1908 只发生**一次** `love 8→4 / cap 20→21` 跃迁；此后直到 `poll off`（约 180 tick 后）采样器**没有任何**后续 love/cap 变化行 ⇒ 不存在第二/三次结算。M1 独立读数同为 `love=4:cap=21`。

### 2.2 forge-1.20.1 — PASS
证据文件：`run/1.20.1/logs/latest.log`

| 行号 | 时间戳 | 原文（节选） |
|---|---|---|
| 1462 | 12:53:24.062 | `AP_M0_ST:Dev:...:love=8:cap=20:...:ovdE=none:...:tick=2437` |
| 1603 | 12:53:33.056 | `AP_N1_OVD:who=Dev:ticks=100:amp=0:res=ok:ovdE=amp=0;dur=100;...:love=8` |
| 1891 | 12:53:37.966 | `AP_POLL:tick=1008:who=Dev:love=8:cap=20:ovdDur=1` |
| 1893 | 12:53:38.016 | `AP_POLL:tick=1009:who=Dev:love=4:cap=21:ovdDur=none` |
| 1964 | 12:53:43.355 | `AP_M1_ST:Dev:...:love=4:cap=21:...:loveE=amp=3;dur=-1:ovdE=none:...:tick=2791` |
| 2064 | 12:53:47.983 | `AP_POLL:on=0` |

判据同 1.21.1：tick 1008→1009 单次跃迁，之后约 200 tick 无任何 love/cap 变化行，M1 读数 `love=4:cap=21`。

---

## 3. 幂等键回归：第二次自然到期必须正常再结算一次

在 §2 结束后（1.21.1：love=4/cap=21；1.20.1：love=4/cap=21）再次 `/astralkame ovd Dev 100 0 N2`。

### 3.1 neoforge-1.21.1 — PASS
| 行号 | 时间戳 | 原文（节选） |
|---|---|---|
| 3305 | 12:45:56.425 | `AP_N2_OVD:who=Dev:ticks=100:amp=0:res=ok:ovdE=amp=0;dur=100;...:love=4` |
| 3580 | 12:46:01.339 | `AP_POLL:tick=2409:who=Dev:love=4:cap=21:ovdDur=1` |
| 3582 | 12:46:01.390 | `AP_POLL:tick=2410:who=Dev:love=0:cap=22:ovdDur=none` |
| 3704 | 12:46:06.988 | `AP_M2_ST:Dev:...:love=0:cap=22:...:loveE=none:ovdE=none:...:tick=2510` |
| 3788 | 12:46:11.033 | `AP_POLL:on=0` |

判据：第二次到期**正常再结算恰好一次**（4→0、21→22），且之后 100 tick 内无第三次变化。证明弱键**身份** Map 没有因「参数相同的到期实例」误吞后续真实结算。

### 3.2 forge-1.20.1 — PASS
| 行号 | 时间戳 | 原文（节选） |
|---|---|---|
| 2266 | 12:54:01.434 | `AP_N2_OVD:who=Dev:ticks=100:amp=0:res=ok:ovdE=amp=0;dur=100;...:love=4` |
| 2555 | 12:54:06.370 | `AP_POLL:tick=1533:who=Dev:love=4:cap=21:ovdDur=1` |
| 2557 | 12:54:06.420 | `AP_POLL:tick=1534:who=Dev:love=0:cap=22:ovdDur=none` |
| 2626 | 12:54:11.387 | `AP_M2_ST:Dev:...:love=0:cap=22:...:loveE=none:ovdE=none:...:tick=3310` |
| 2698 | 12:54:15.638 | `AP_POLL:on=0` |

判据同 1.21.1。

---

## 4. 语义回归①：`/effect clear` 不清算（love/cap 不变）

复现：`set cap 20 / set love 8` → `ovd 1200`（长时长，避免与本臂抢时间）→ 2s 后读 C0 → `/effect clear Dev` → 2s 后读 C1 → 卸下立牌读 C2。

### 4.1 neoforge-1.21.1 — PASS（附口径说明）
| 行号 | 时间戳 | 原文（节选） |
|---|---|---|
| 4362 | 12:46:34.458 | `AP_C0_ST:...:love=8:cap=20:...:ovdE=amp=0;dur=1109;...:tick=3008` |
| 4692 | 12:46:43.540 | `AP_C1_ST:...:love=8:cap=20:...:ovdE=amp=0;dur=948;...:tick=3169` |
| 4969 | 12:46:51.945 | `AP_C2_ST:...:sign=-:...:love=0:cap=4:...:ovdE=none:...:tick=3317` |

### 4.2 forge-1.20.1 — PASS（附口径说明）
| 行号 | 时间戳 | 原文（节选） |
|---|---|---|
| 3164 | 12:54:37.185 | `AP_C0_ST:...:love=8:cap=20:...:ovdE=amp=0;dur=1104;...:tick=3774` |
| 3496 | 12:54:46.306 | `AP_C1_ST:...:love=8:cap=20:...:ovdE=amp=0;dur=943;...:tick=3935` |
| 3708 | 12:54:54.361 | `AP_C2_ST:...:sign=-:...:love=0:cap=4:...:ovdE=none:...:tick=4075` |

**口径说明（两线同）**：`/effect clear` 执行后 overdose **仍在**（C1 的 `ovdE` 时长仍在递减），因为 `ModEffectEvents#onModEffectRemovalPrevented` 会取消对 `astral_dice:*` 的外部移除（既有全局策略，前次报告 §4.1 已登记）。因此本臂可判定的是「**发出清除命令不会触发结算**（love/cap 逐字不变）」；「真正移除但不结算」由 §5 的卸下立牌（内部 `ModEffectRemoval`）覆盖。字面「喝奶」臂未单独跑（同一 `removeAllEffects` 路径且会先被该守卫拦下，重跑无增量信息）。

---

## 5. 语义回归②：到期前卸下立牌 ⇒ 不结算

复现：`set cap 20 / set love 8` → `ovd Dev 400`（20s）→ 读 U4a（剩余 ~330t）→ **立即卸下**立牌 → 读 U4b → 等待 20s（越过原到期时刻）→ 重新佩戴 → 读 U4c。

判据：若卸下后仍发生结算，`cap` 会 +1（重新佩戴后应为 5）；实际必须仍为默认 4。

### 5.1 neoforge-1.21.1 — PASS
| 行号 | 时间戳 | 原文（节选） |
|---|---|---|
| 7775 | 12:48:55.594 | `AP_U4a_ST:...:sign=astral_dice:ame_sign:...:love=8:cap=20:...:ovdE=amp=0;dur=330;...:tick=5571` |
| 8053 | 12:49:03.684 | `AP_U4b_ST:...:sign=-:...:love=0:cap=4:...:ovdE=none:...:tick=5712` |
| 8596 | 12:49:32.237 | `AP_U4c_ST:...:sign=astral_dice:ame_sign:...:love=2:cap=4:...:ovdE=none:...:tick=6262` |

时间/刻核算：U4a 时剩余 330t ⇒ 原到期约 tick 5901；卸下发生在 U4a–U4b 之间（≤5712），**早于到期约 190 tick**；U4c 在 tick 6262（已越过到期 361 tick）`cap` 仍为 **4**。

### 5.2 forge-1.20.1 — PASS
| 行号 | 时间戳 | 原文（节选） |
|---|---|---|
| 4221 | 12:55:20.315 | `AP_U4a_ST:...:sign=astral_dice:ame_sign:...:love=8:cap=20:...:ovdE=amp=0;dur=330;...:tick=4541` |
| 4434 | 12:55:29.209 | `AP_U4b_ST:...:sign=-:...:love=0:cap=4:...:ovdE=none:...:tick=4697` |
| 4909 | 12:55:59.343 | `AP_U4c_ST:...:sign=astral_dice:ame_sign:...:love=2:cap=4:...:ovdE=none:...:tick=5280` |

时间/刻核算：U4a 剩余 330t ⇒ 原到期约 tick 4871；卸下 ≤4697（早约 174 tick）；U4c 在 tick 5280（越过到期 409 tick）`cap` 仍为 **4**。

> 备注（透明性）：首轮曾用 100t 做同臂，因注入链路本身耗时导致「卸下」晚于到期（读数 `AP_U0_ST` 显示 `ovdE dur=3` 时随即到期），该臂**作废不计入结论**；改用 400t + 20s 等待的干净臂（上表）复测。另有一次 200t 臂（`AP_U3a/b/c`）方向一致，但余量较小，最终以 400t 臂为正式证据。

---

## 6. 收尾（收停 / 清存档 / 无残留）

1）两线标准收尾（`--phase stop --purge-saves`，经 cmd 包装以规避 WSL 互操作下嵌套 `& pwsh` 的环境缺陷，见备注）：
```
===== MT_PHASE: stop =====
MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
MT_CLEANUP_SAVES: PURGED 0
MT_CLEANUP: 已收停 0 个本流程进程（范围：1.20.1）
MT_CLEANUP_RESULT: OK
MT_STOP: OK (1s) — 收停完成，无本流程残留进程

===== MT_PHASE: stop =====
MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
MT_CLEANUP_SAVES: PURGED 0
MT_CLEANUP: 已收停 0 个本流程进程（范围：1.21.1）
MT_CLEANUP_RESULT: OK
MT_STOP: OK (1s) — 收停完成，无本流程残留进程
```
`PURGED 0` 的原因：本轮先用 `mt.ps1 --phase stop --purge-saves` 收停并已清空世界，随后再次收尾时无存档可清；实测两线存档目录此时为空：
```
run/1.20.1/saves: total 0
run/1.21.1/saves: total 0
```
2）无测试客户端残留：`tasklist` 仅剩 2 个 java（Gradle 守护 / Gradle worker），命令行分别为 `Zulu\zulu-21 ... --add-opens` 与 `eclipse_adoptium-17 ... gradle-worker`，**无 runClient**。
3）备注（环境缺陷，非产品问题）：在 WSL 互操作 + 管道 stdio 下，`pwsh` 内用调用运算符 `& pwsh ...` 启子进程会**静默不执行**（`CHILDHI; exit 7` 无输出且 `$LASTEXITCODE` 为空），导致 `mt_stop.ps1` 直跑时误报 `MT_WARN: STOP: 收停后仍有残留`；改用 `cmd.exe /c` 启动外层 pwsh 后同一脚本输出 `MT_STOP: OK`。同一环境用 `Start-Process -Wait -PassThru` 可正常拿到 `ExitCode=7`，故确认是调用方式问题而非清理逻辑问题。

---

## 7. 测试资产改动（探针修复，非产品代码）

1.20.1 探针的 `state` 子命令此前**静默无输出**（前次报告 §3 已登记）。本轮定位到根因并修复：
- `run/1.20.1/logs/kubejs/server.log:464`：
  `astral_kame_probe.js#126: Could not create ID from 'net.minecraftforge.registries.RegistryObject@...'!: Non [a-z0-9/._-] character in path...`
- 根因：1.20.1 的 `ModEffects.*` 字段是 `RegistryObject`，`kStateLineBody` 直接把它传给 `p.getEffect(...)`，Rhino 强转失败并中止整个命令。
- 修复（`scripts/test/resources/kubejs/1.20.1/server_scripts/astral_kame_probe.js`）：`kFxText(p, fx.KANGEL_FAN.get())` / `fx.AME_LOVE.get()` / `fx.AME_OVERDOSE.get()`。
- 复测：冷启动后 `AP_B4_ST:...:love=8:cap=20:...:fanE=none:loveE=amp=7...:ovdE=none`（12:52:57.796）即恢复正常输出；此后 §2–§5 的 1.20.1 读数全部使用 `state` 断言。

---

## 8. 复跑命令（单线）

```powershell
pwsh -NoProfile -File scripts/test/mt_build.ps1 --version 1.21.1 --timeout 60 --retries 2
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase env
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase launch
pwsh -NoProfile -File scripts/test/mt_inject.ps1 cmd --version 1.21.1 --command "/astralkame equip stand \"astral_dice:ame_sign\" Dev"
pwsh -NoProfile -File scripts/test/mt_inject.ps1 cmd --version 1.21.1 --command "/astralkame set cap 20 Dev"
pwsh -NoProfile -File scripts/test/mt_inject.ps1 cmd --version 1.21.1 --command "/astralkame set love 8 Dev"
pwsh -NoProfile -File scripts/test/mt_inject.ps1 cmd --version 1.21.1 --command "/astralkame poll on"
pwsh -NoProfile -File scripts/test/mt_inject.ps1 cmd --version 1.21.1 --command "/astralkame ovd Dev 100 0 N1"
# 等 7s 后读 state / poll 行，期望恰好一次 love 8→4、cap 20→21
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --purge-saves
```

