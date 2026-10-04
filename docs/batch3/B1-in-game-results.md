# B1 — 1.21.1 游戏内行为验证执行报告

- 执行时间：2026-09-15 18:42–19:1x（本机）
- 仓库：`F:\MCProject\astral_dice_multiloader`，分支 `multi-1.20.1-1.21.1`，HEAD `7b9e711`（开工/收工均 CLEAN，执行期间未改任何产品源码、lang、CHANGELOG、AGENTS.md，未改 `scripts/test/**` 任何脚本）
- 被测产物：`run/1.21.1/mods/astral_dice-1.2.1+neoforge_1.21.1.jar`
  - mtime `2026-09-15 17:38:38`，SHA256 `1BB0D28D387211AD4942CE2AA93BBE52AD6419524CF2865A17BDC350EF687E54`（与 `neoforge-1.21.1/build/libs/` 同名 jar 逐字节相同）
  - HEAD `7b9e711`（18:19:14）为其后的**文档-only** 提交；18:19:29 那次 build 三个拷贝任务全部 UP-TO-DATE（`temp/mt_build_1.21.1_1789467569.log`）⇒ jar 即当前 HEAD 源码产物
- 探针：`scripts/test/resources/kubejs/1.21.1/server_scripts/astral_bugfix_probe.js`（16:33:47 / 163475 B / SHA256 `82BA36BC…`，即 HEAD 内 `216f6cc` 提交的版本），按 TESTING-SPEC §6 复制进 `run/1.21.1/kubejs/server_scripts/`
- run id：沿用粘性 run id `20260915-091733`（`scripts/test/cases/.mt_run_state.json`）

---

## ① 存活判定的真相（为什么上次全流程的 cases 全线 ERROR）

### 结论：**存活探测没有误判**。上一轮 cases 阶段开始时，客户端确实已经不在运行了。

三条硬证据：

1. `scripts/test/cases/.mt_run_state.json` 里，同一轮（run id `20260915-091733`）的阶段时间戳：
   - `launch` PASS，`ts = 1789468863.841`（= 18:41:03.8）
   - `cases` FAIL，`ts = 1789468878.219`（= 18:41:18.2）
   - 两阶段相隔 **14.4 s**；而客户端在 **18:41:02.381** 已打 `Stopping!`（`run/1.21.1/logs/latest.log` 末行区，`[Render thread/INFO] [net.minecraft.client.Minecraft/]: Stopping!`）。
2. cases 阶段的状态文件落盘时间：`.mt_snapshot.json` 18:41:04、`.mt_run_state.json` 18:41:19 —— **晚于**客户端退出时刻。
3. 直接正控：本次我用同一条探测在**活客户端**上取数，判定正确：
   ```
   Get-MtClientStatus -Paths (Get-MtPaths -Version '1.21.1')
   → Alive=True Pids=2964 Titles=[Minecraft NeoForge* 1.21.1 - 单人游戏] CrashCount=0
   客户端 java 进程命令行（56 字符）：net.caffeinemc.sodium / net.minecraft.client.main.Main /
   ```
   命中路径是 `Mt.Proc.psm1:355` 的**窗口标题含版本号**分支（命令行 56 字符、不含任何 `Get-MtProcessMarkers` 标记，只靠路径②必失败——这正是 2026-09-15 那次修复加进来的回退）。

### 真正的根因：`mt.ps1` 的 `Invoke-MtChild` 用 `Start-Process … -Wait`，而 **PowerShell 的 `-Wait` 会等待整棵进程树**

- `scripts/test/mt.ps1:65-68`：`Start-Process -FilePath $script:PsExe … -NoNewWindow -PassThru -Wait`
- `scripts/test/mt_launch.ps1:139-141`：`Start-MtProcessToFile` 以 `-NoNewWindow` 起 `cmd.exe /c gradlew.bat :neoforge-1.21.1:runClient … > runclient_launch.log 2>&1`，客户端是**同一控制台的后代进程**。
- 因此 `mt_launch` 早已 `exit`（18:20:2x 打完 `MT_LAUNCH: OK`），但 mt.ps1 的 `-Wait` 一直阻塞到 **runClient 的整棵树退出**才返回 ⇒ `launch` PASS 被记在 18:41:03，`cases` 只能在客户端死后开始。
- 佐证：`run/1.21.1/runclient_launch.log` 末行 `BUILD SUCCESSFUL in 21m 18s`（gradle 认为 runClient 跑了 21 分 18 秒）；18:41:02 客户端被外部收停（日志是**干净关闭**，无 crash-report、无异常栈、无 taskkill 痕迹）。
- 本机实测判据（我在 `%TEMP%` 下做的最小复现）：
  - 子 pwsh 在 **0.4 s** 就退出（`CHILD_EXITED_AT=0.4s`）
  - 但父进程 `Start-Process … -Wait` 在 **12.4 s** 才返回（= 孙进程 ping 12 s 的时长）
  ⇒ `-Wait` 确为「等整棵进程树」，不是等子进程本身。

**这条正是工具链文档自己写过的坑，但文档只把它归给「调用方」**：`scripts/devtools/Start-MtDetached.ps1:8-23` 记「非交互式调用方按整棵进程树等待 → 脚本卡滞」，而实测 **mt.ps1 内部的 `Invoke-MtChild -Wait` 同样如此**。所以：

- **全流程（单次 `mt.ps1` 调用）在结构上不可能让 launch 与 cases 共存**：launch 返回时客户端必已死；
- **「分步 `--phase launch` → `--phase cases`」同样不行**（`--phase launch` 走同一 `Invoke-MtChild`，会阻塞到客户端退出）——这一点与任务书里「分步路线优先」的假设相反，必须纠正。

### 可行的正确用法（本次实际采用，且与 TESTING-SPEC §3/§10.8 一致）

1. **launch 必须脱离执行**：`scripts/devtools/Start-MtDetached.ps1`（`-WindowStyle Hidden` + stdout/stderr 落文件，调用方立即返回），再**按日志终态标记轮询**；
2. 客户端起来后，**前台**逐条跑 `--phase cases --case …`（cases 阶段不产生长命后代，可正常返回；单阶段默认不清理，客户端跨调用保持存活）；
3. 每跑 `--phase cases`（单阶段）**不会**写快照，必须先手工 `mt_assert.ps1 snapshot`（见 ⑥-2）；
4. 收尾 `--phase stop --force`。

其余判据（`mt_inject.ps1:314` 的报错文案、`Mt.Proc.psm1:322` 的旧注释、`mt_case.ps1:741-746` 的自动重启注释）均**不是**本次跑不通的原因：`mt_inject.ps1:314` 只是在窗口找不到时才打印那句文案，它读的是 `Get-MtClientStatus`；`mt_case.ps1:814` 的 ERROR 是客户端真死之后的正确判定。

---

## ② 实际执行的命令序列

```powershell
# 0) 前置：无任何 Minecraft 客户端 java 进程；jar = 1BB0D28D…（17:38:38）
# 1) 第一轮：脱离式启动（此时 run/ 里的探针还是旧的 14:54 版本 —— 见 ④/⑥-4）
pwsh -NoProfile -File scripts/devtools/Start-MtDetached.ps1 -Script scripts/test/mt.ps1 `
     -Out temp/b1_launch_1211.log -Merge -Arg --version 1.21.1 --phase launch
#    → MT_LAUNCH: OK (39s)；随后逐条 cases（结果无效，探针陈旧，见 ④）
pwsh -NoProfile -File scripts/test/mt_assert.ps1 snapshot --version 1.21.1     # 每条用例前
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase cases --case cases/<CASE>.json

# 2) 按 TESTING-SPEC §6 安装**当前**探针 + 冷启动（改探针必须冷启动，§6 注释）
pwsh -NoProfile -File scripts/test/mt.ps1 --phase stop --force
Copy-Item scripts/test/resources/kubejs/1.21.1/server_scripts/*.js run/1.21.1/kubejs/server_scripts/ -Force
#    校验：源=部署 SHA256 均 82BA36BCDABD821CCDD3DA8D28DBEFAE2761B78AD1042CB5CC4ABA73DE381C2A
pwsh -NoProfile -File scripts/devtools/Start-MtDetached.ps1 -Script scripts/test/mt.ps1 `
     -Out temp/b1_launch2_1211.log -Merge -Arg --version 1.21.1 --phase launch
#    → MT_LAUNCH: OK (39s) @18:54:26

# 3) 正式轮：逐条（每条前一次 snapshot；每条后确认客户端存活）
foreach ($c in <15 个 1.21.1 条目>) {
  pwsh -NoProfile -File scripts/test/mt_assert.ps1 snapshot --version 1.21.1
  pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase cases --case "cases/$c.json"
}
# 4) 汇总 + 收停
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase report
pwsh -NoProfile -File scripts/test/mt.ps1 --phase stop --force
```

- 全程 15 条用例每条耗时 2–43 s；客户端全程存活（每条用例后 `clientAlive=True`），无 crash-report、kubejs server.log 0 error。
- 收尾后 `Get-CimInstance Win32_Process -Filter "Name='java.exe'"` **0 条**（`mt_cleanup` 曾报 `RESIDUAL [19252]` 的 gradlew wrapper，数秒后自行退出）；`git status --porcelain` 空、HEAD 仍为 `7b9e711`。
- 本次新增/改动的**非产品**文件：`docs/batch3/B1-in-game-results.md`（本文件）、`temp/b1_cases/*.log`（逐条完整输出）、`temp/b1_launch{,2}_1211.log`、`run/1.21.1/kubejs/server_scripts/*.js`（§6 规定的探针部署副本，`run/` 不入库）。

---

## ③ 逐条用例结果（正式轮，探针 = 82BA36BC…，客户端 = 18:54:26 冷启动）

| # | 用例 | 工具链判定 | 断言 通过/总数 | 我的分类 | 证据 |
|---|---|---|---|---|---|
| 1 | RAILGUN-PET-EXCLUDE-1.21.1 | **PASS** | 26/26 | PASS | `temp/b1_cases/RAILGUN-PET-EXCLUDE-1.21.1.log`；`AP_PE_AFTER:…:friendly=0…`、`AP_PE_VERDICT:self=0:enemy=1:neutral=1:friendly=0…`、`SCOPE_OK:1` |
| 2 | RAILGUN-OVERRIDE-CLASS-1.21.1 | **PASS** | 22/22 | PASS | 同名 `.log`；`turtle=0:villager=0:valive=1:talive=1` + `SCOPE_OK:1` |
| 3 | RAILGUN-AOE-SCOPE-1.21.1 | **PASS** | 30/30 | PASS | 同名 `.log`（**基线 FAIL → 本次 PASS**） |
| 4 | HOSTILE-TARGET-NEUTRAL-1.21.1 | **FAIL** | 25/26 | FAIL（**用例侧缺陷**，非产品面） | 唯一失守：`北极熊拥有以下实体数据：\d+` 未命中；`run/1.21.1/logs/latest.log` 该步回显 `未找到实体` |
| 5 | KOMACHI-EXTRA-PLAY-1.21.1 | **PASS** | 41/41 | PASS | `AP_K1_AFTER:max=2:extra=1:cd=0:locked=1`、`AP_K1_GRACE:cd=1:locked=0`、`AP_K1_RELEASED:1`、`AP_K3_CAP_OK:1`、`AP_K3_VERDICT:…:uncapped_release:1`（**陈旧探针下为 34/41 FAIL**） |
| 6 | NANCY-LU-PEARL-IMMUNE-1.21.1 | **FAIL** | 17/29 | **ERROR（探针异常，产品不可判）** | `AP_P2_EX / AP_P3_EX: InternalError: Can't find method net.minecraft.world.entity.Entity.setDeltaMovement(number,number,number). (server_scripts:astral_bugfix_probe.js#1559)`；新探针同语句在 **L1613** |
| 7 | SPELL-TRUE-DAMAGE-1.21.1 | **PASS** | 17/17 | PASS | 同名 `.log`（**基线 FAIL → 本次 PASS**） |
| 8 | FEN-SPLASH-MAIN-TARGET-1.21.1 | **PASS**（复跑） | 19/19（首轮 18/19 FAIL） | PASS（**不稳定**，见 ⑥-7） | 复跑 `AP_FS_MELEE:dealt=11.28`、`AP_FS_AFTER:tdealt=11.28:near_dealt=5.28:armored_dealt=5.28:far_dealt=0:melee_est=6:ratio=0.88`、`VERDICT:…:ratio_ok=1`；首轮 `dealt=16 / melee_est=7.2 / ratio=1.222 / ratio_ok=0`。`temp/b1_cases/FEN-SPLASH-MAIN-TARGET-1.21.1{,.rerun}.log`（**基线 FAIL → 本次 PASS**） |
| 9 | AIRBAG-BYPASS-KILL-1.21.1 | **PASS** | 25/25 | PASS | 同名 `.log` |
| 10 | EFFECT-DECAY-FLICKER-1.21.1 | **PASS** | 22/22 | PASS | 同名 `.log` |
| 11 | NANCY-LU-CLOAK-1.21.1 | **PASS** | 32/32 | PASS | 同名 `.log` |
| 12 | ANVIL-STAR-UPGRADE-1.21.1 | **FAIL** | 45/53 | FAIL（**新发现，待裁定**） | `AP_Z1_ERR:lost_after_close:-1:5`、`AP_S1/S2/S3_INV_AFTER_CLOSE:7`（用例期望 `10`）、`AP_S3_RIGHT_LEFT:astral_dice:star_coin:3` |
| 13 | DIRECTIONAL-BLAST-AOE-1.21.1 | **PASS** | 17/17 | PASS | 同名 `.log` |
| 14 | EMERALD-DICE-TRADE-1.21.1 | **PASS** | 18/18 | PASS | 同名 `.log` |
| 15 | SMOKE-TOOLCHAIN（无客户端） | **PASS** | 3/3 | PASS | 同名 `.log` |

**合计：PASS 12 / FAIL 3**（其中 NANCY-LU-PEARL-IMMUNE 属「探针跑不起来」⇒ ERROR，HOSTILE-TARGET-NEUTRAL 的唯一失守属用例侧，ANVIL-STAR-UPGRADE 为真失守待裁定）。

### 三条 FAIL 的逐条归因

1. **HOSTILE-TARGET-NEUTRAL**（25/26）：`mt_case` 判定 = FAIL（断言未命中）；产品面 25 条断言全绿，含 `AP_HN_VERDICT:self=0:enemy=1:neutral=1:friendly=0:turtle=0:villager=0:valive=1:talive=1`、`SCOPE_OK:1`。
   失守断言 `北极熊拥有以下实体数据：\d+` 对应用例 step 11 `/data get entity @e[type=minecraft:polar_bear,limit=1] AngerTime`，日志回显 `未找到实体`（活体取证：本次我手工重放同一序列，`AP_DBG_BEFORE:…:nhp=30` → 4 s 后同一条 `data get` 仍回 `未找到实体`）。
   机制（证据链）：`railgunfriendly` 的**臂装步自己就打了那一记近战**（`AP_HN_MELEE:playerAttack:dealt=…`），赐福随即触发、落雷在 ~1 s 后结算；`AP_HN_CHARGE_AT_ATTACK:6`（攻击瞬间未扣）→ `AP_HN_AFTER:…:charge=0`（读步已扣）证明落雷正是发生在「臂装 → 读步」这段窗口里；30 HP 的北极熊在该窗口内 hp 30→0 并从世界消失，而同距离的已驯服狼（owner 排除）毫发无伤 ⇒ 与「落雷按敌对口径选择目标」完全一致。
   而**注入器每条命令的固定按键序列约 2.5–3 s**（Esc/Tab/Enter → T → 文本 → Enter），落雷只等 ~1 s ⇒ 这条 NBT 回读断言在时序上**永远不可能命中**。
   ⇒ 判断：**用例 step 顺序缺陷**（不是产品缺陷）。建议把「怒气前置」证据移回探针读数（16:33 版探针已能用 Java API 正确报出 `nAngry=true`，故用例 NOTE 4 里「nAngry 是 Rhino 失真」的理由已过期），或由探针直接打印 `getRemainingPersistentAngerTime()`。**我未改任何用例文件。**

2. **NANCY-LU-PEARL-IMMUNE**（17/29）：12 条失守全部源于探针在 P2/P3 阶段抛 `InternalError: Can't find method …Entity.setDeltaMovement(number,number,number)`（`AP_P2_EX`/`AP_P3_EX`），末地珍珠相位根本没跑起来；`AP_P1_CTRL:…hp=20→18:hurt=10…`、`AP_P1_CTRL_OK:1`、`AP_P1_API:causeFallDamage` 说明对照组正常。
   ⇒ 判断：**ERROR（测试探针 API 缺陷，产品不可判）**，与产品行为无关。1.21.1 的 `Entity#setDeltaMovement` 只收 `Vec3`，探针必须改成 `pearl.setDeltaMovement(new Vec3(0.0, 0.0, 0.8))`（`scripts/test/resources/kubejs/1.21.1/server_scripts/astral_bugfix_probe.js:1613`；1.20.1 同款探针需一并核查）。

3. **ANVIL-STAR-UPGRADE**（45/53）：三条 `AP_S{1,2,3}_INV_AFTER_CLOSE` 实际 `7`、用例期望 `10`；同一读数里 `AP_S*_RIGHT_LEFT:astral_dice:star_coin:3`（铁砧附加槽仍留 3 枚星币）恰好补齐差额（`AP_S3_COINS_BEFORE:35`、`AP_S3_FEE:25:2->3`）；Z1 相位探针**自报物品丢失**并提前返回：`AP_Z1_ERR:lost_after_close:-1:5`（探针 L955 的 `ds<0||fs<0` 判定），导致 `AP_Z1_FEW_*`/`AP_Z1_DONE` 整段缺失、且 `absent` 断言因该 ERR 出现而失守。
   ⇒ **真失守（断言不满足）**，但「关界面后铁砧内残留星币未回到背包」是**产品缺陷**还是**用例期望写错**，需要你裁定；该用例 git 最后一次改动是 `0a9a9ef`（09-13），历次全流程里它一直记 ERROR（从未真正执行过），这是**首次实跑**。

---

## ④ 与上一轮基线的对比

上一轮基线 = `scripts/test/reports/20260915-091733/REGRESSION-REPORT.md`（09:12–09:33，被测 jar = 02:13:32 构建，SHA256 `483CE48F…`）。
**注意：本次被测 jar 已变为 17:38:38（`1BB0D28D…`），探针与多条用例也在 11:12–16:33 之间更新过**，故下面的 PASS 不能单独归因给「这一批产品改动」。

| 用例 | 基线（09:33，旧 jar） | 本次（17:38:38 jar + 新探针） | 说明 |
|---|---|---|---|
| SPELL-TRUE-DAMAGE-1.21.1 | **FAIL 11/13**（`base_armor_effective=0`、`bonus_true_damage=0`，已归因探针读数缺陷 + 用例期望） | **PASS 17/17** | 已修好（用例/探针侧口径已对齐） |
| FEN-SPLASH-MAIN-TARGET-1.21.1 | **FAIL 9/11**（根因 `Missing key in ResourceKey[…minecraft:1.0]`，`fensplashhit` 抛异常） | **PASS 19/19**（第一次 18/19，复跑通过） | 异常已消失；`ratio_ok` 仍不稳定（⑥-7） |
| RAILGUN-AOE-SCOPE-1.21.1 | **FAIL 11/13** | **PASS 30/30** | 已修好 |
| RAILGUN-PET-EXCLUDE-1.21.1 | PASS 10/10 | PASS 26/26 | 断言集已扩充，仍全绿 |
| RAILGUN-OVERRIDE-CLASS-1.21.1 | PASS 8/8（首轮 7/8，注入丢失） | PASS 22/22 | 一致 |
| HOSTILE-TARGET-NEUTRAL-1.21.1 | PASS 10/10 | FAIL 25/26（用例侧） | 断言集由 10 扩到 26，新增的「北极熊 NBT 回读」在时序上不可达（③-1） |
| EFFECT-DECAY-FLICKER-1.21.1 | PASS 11/11 | PASS 22/22 | 一致 |
| KOMACHI-EXTRA-PLAY / NANCY-LU-* / AIRBAG / ANVIL / DIRECTIONAL-BLAST-AOE / EMERALD-DICE-TRADE | 基线为 ERROR / 未执行 | 本次见 ③ | 新增覆盖 |
| 1.20.1 全部 | `RAILGUN-AOE-SCOPE-1.20.1` 被阻塞未判 | 本次未跑 | 见 ⑤ |

**本轮唯一「新出现」的 FAIL 是 ANVIL-STAR-UPGRADE（首次实跑）与 HOSTILE-TARGET-NEUTRAL 的新增断言**；三条已知基线 FAIL 中，两条（SPELL-TRUE-DAMAGE、RAILGUN-AOE-SCOPE）已明确转为 PASS，FEN-SPLASH 亦转 PASS（但存在不稳定）。

### 本轮改动覆盖面（任务书点名的三处）

| 本批改动 | 游戏内是否被真正验证 |
|---|---|
| 电磁炮敌对口径（PET-EXCLUDE / OVERRIDE-CLASS / AOE-SCOPE / HOSTILE-TARGET-NEUTRAL） | **是**，4 条用例 103/104 断言通过（唯一失守是用例侧时序缺陷） |
| 轮次归零解除电击手套武装 | **否（无对应断言）**：全部 1.21.1 用例与探针中检索 `electric_glove / ELECTRIC_GLOVE / 电击手套` **0 命中**；KOMACHI-EXTRA-PLAY 只覆盖了出牌轮归零/锁定三态的读数（41/41） |
| 末影骰保命清 GLOWING | **否（无对应用例）**：全部 1.21.1 用例检索 `ender_dice / 末影骰 / GLOWING` **0 命中**；探针里仅 `doDecayFlash`(L1711)/`doDecayClear`(L1760) 为 EFFECT-DECAY-FLICKER 的脚手架而清 GLOWING，与末影骰无关 |

⇒ 这两处改动**目前没有游戏内证据**，需要补用例（或明确标注「仅代码验证」）。

---

## ⑤ 未跑到的用例清单

| 未跑条目 | 数量 | 原因 |
|---|---|---|
| 全部 1.20.1 条目（AIRBAG-BYPASS-KILL / ANVIL-STAR-UPGRADE / DIRECTIONAL-BLAST-AOE / EFFECT-DECAY-FLICKER / EMERALD-DICE-TRADE / FEN-SPLASH-MAIN-TARGET / HOSTILE-TARGET-NEUTRAL / KOMACHI-EXTRA-PLAY / LOADER-GATE-FORGE / LOOT-MODIFIER / NANCY-LU-CLOAK / NANCY-LU-PEARL-IMMUNE / RAILGUN-AOE-SCOPE / RAILGUN-OVERRIDE-CLASS / RAILGUN-PET-EXCLUDE / SPELL-TRUE-DAMAGE = 16 条） | 16 | **不在 B1 范围**（B1 只要求 1.21.1 游戏内验证）；1.20.1 的 env/launch/cases 本轮完全未启动（全流程的门控逻辑也会在 1.21.1 未通过时把 1.20.1 记为 GATED） |
| `.mt_run_state` / `.mt_snapshot` | 2 | **不是用例**，是 `cases/` 下的运行态文件被扫描进来的伪条目（⑥-3） |
| 1.21.1 其余条目 | 0 | 1.21.1 的 14 条 + SMOKE-TOOLCHAIN 已**全部执行** |

---

## ⑥ 需要你裁定 / 需要修的工具链点（我只报告，未改任何脚本）

1. **`Invoke-MtChild -Wait` 等整棵进程树 → 全流程与「分步 launch」都不可能留下活客户端**（`scripts/test/mt.ps1:65-68`；现象与实测见 ①）。建议：launch 阶段改用脱离式启动（`-WindowStyle Hidden` + 日志文件）并由 `mt.ps1` 轮询 `MT_LAUNCH: OK` 终态标记；否则 `--phase launch` 的正确用法必须**强制**经 `Start-MtDetached.ps1`，且 TESTING-SPEC 要写明这一点（现文档只在 §3 末尾/§10.8 提到 detached，未点明「`--phase launch` 前台直调必然卡到客户端退出」）。
2. **单阶段 `--phase launch` 不写快照 → 分步路线会静默用陈旧 offset 判「未命中」**：只有全流程在 `mt.ps1:330` 调 `mt_assert snapshot`。后果实测：本次第一次 RAILGUN-PET-EXCLUDE 因 `.mt_snapshot.json` 仍留着上一轮 `latest.log=74375` 的偏移，把 18:46 早段的 `AP_PE_TAME:tame=1:owner=1:src=2`（确实存在于日志）判为未命中 → 假 FAIL（复跑 26/26 PASS）。建议把快照动作放进 `mt_launch.ps1` 的收尾（或 `--phase launch` 分支），不要再让调用方手工补。
3. **`cases/` 扫描把点文件当用例**：`mt_case.ps1:705-711` 用 `Get-ChildItem … | Where-Object { $_.Name -like '*.json' }`，PowerShell 的 `-like '*.json'` **会**匹配 `.mt_run_state.json` / `.mt_snapshot.json`（Python 的 `glob('*.json')` 不匹配点文件——这是 pwsh 移植引入的回归），于是它们被当成用例并记 ERROR。建议加一条 `-not $_.Name.StartsWith('.')`。**这不是本次跑不通的原因**，按要求未改。
4. **探针必须手工安装（TESTING-SPEC §6），而 `mt_env` 不会刷新 `run/<版本>/kubejs/`**：本轮全流程 18:19 启动时用的仍是 **14:54:59 / 160592 B** 的旧探针（源 `16:33:47 / 163475 B`，SHA256 不同），直接后果是 KOMACHI-EXTRA-PLAY 在旧探针下 34/41 FAIL（读数缺 `locked=` 字段、`AP_K1_GRACE` 整段不存在、`AP_K1_RELEASED:0`），换上新探针后 41/41 PASS。建议让 `--phase launch`（或 env）自动同步并**在哈希不一致时告警**。
5. **探针 bug（测试资产）**：`astral_bugfix_probe.js:1613` 的 `pearl.setDeltaMovement(0.0, 0.0, 0.8)` 在 1.21.1 无法解析（`Entity#setDeltaMovement(Vec3)`），导致 NANCY-LU-PEARL-IMMUNE 的 P2/P3 相位全灭（产品不可判）。1.20.1 同名探针需一并核查（该版本同样是 `Vec3` 形参）。
6. **HOSTILE-TARGET-NEUTRAL 用例缺陷**：见 ③-1，step 11 的 NBT 回读在时序上不可达（注入器 ~2.5–3 s/条 vs 落雷 ~1 s）。修法二选一：把怒气证据并进探针读数，或删除/前移该断言。**我未改用例文件。**
7. **FEN-SPLASH-MAIN-TARGET 的 `ratio_ok` 不稳定**：`ratio = near_dealt / melee_est`，主靶被**过量击杀**时观测溅射反映的是真实伤害、而 `melee_est` 不跟随 ⇒ 首轮 `dealt=16 / melee_est=7.2 / ratio=1.222 → ratio_ok=0`（FAIL），复跑 `dealt=11.28 / melee_est=6 / ratio=0.88 → ratio_ok=1`（PASS）。产品口径本身无误（首轮 8.8 = 0.88×10，真伤 `armored_dealt=near_dealt=8.8`、`far_dealt=0`）。建议给主靶足够血量避免过量击杀，或让期望值由「实际攻击伤害」推导。
8. **ANVIL-STAR-UPGRADE 首次实跑暴露的问题**（③-3）：需要你裁定「铁砧关闭后附加槽残留星币未回背包」是产品缺陷还是用例期望写错。
9. 次要：`scripts/devtools/Start-MtDetached.ps1:66-67` 解析了 `-Merge` 却从未使用（L95-97 恒为 stdout/stderr 分离），与 TESTING-SPEC §3 的示例写法不一致（仅文档/行为不一致，不影响本次执行）。

---

## ⑦ 本次执行对工具链的「调用姿势」备忘（供下一次直接复用）

```powershell
cd F:\MCProject\astral_dice_multiloader
# 0) 确认无客户端：Get-CimInstance Win32_Process -Filter "Name='java.exe'" | % CommandLine 里无 net.minecraft.client.main.Main
# 1) 探针必须是当前版本（§6 手工安装）+ 冷启动
Copy-Item scripts/test/resources/kubejs/1.21.1/server_scripts/*.js run/1.21.1/kubejs/server_scripts/ -Force
pwsh -NoProfile -File scripts/devtools/Start-MtDetached.ps1 -Script scripts/test/mt.ps1 `
     -Out temp/b1_launch2_1211.log -Merge -Arg --version 1.21.1 --phase launch
#    轮询 temp\b1_launch2_1211.log 出现 'MT_LAUNCH: OK'
# 2) 每条用例：先 snapshot 再 cases（单阶段默认不清理，客户端保持存活）
pwsh -NoProfile -File scripts/test/mt_assert.ps1 snapshot --version 1.21.1
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase cases --case cases/<CASE>.json
# 3) 收尾
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase report
pwsh -NoProfile -File scripts/test/mt.ps1 --phase stop --force
```
