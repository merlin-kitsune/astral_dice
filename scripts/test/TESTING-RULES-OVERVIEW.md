# astral_dice 测试流程与规则总览（2026-09-20）

> 用途：**供用户核验的完整测试流程与规则展示**（2026-09-20 用户指令）。
> 本文是 `scripts/test/TESTING-SPEC.md`（唯一落地规范）与 `AGENTS.md`「自动化测试流程」节的**总览**；两者冲突时以 `TESTING-SPEC.md` 为准。
> ⚠️ 现行状态：全部用例与探针已于 2026-09-20 **清零作废**，本文描述的是**保留的流程与规则**，重建测试资产须待用户重新授权。

## 1. 目标与适用范围

对子项目做**真实游戏内**的自动化验证：客户端渲染/输入类缺陷、服务端权威数值与状态机、新增内容与用户指定内容的回归。
不在范围内：纯静态校验（另有 §守门脚本）、性能压测、多人联机双端测试（无覆盖）。

## 2. 机器与环境前提

| 项 | 要求 |
|---|---|
| 分支 | `multi-1.20.1-1.21.1`（发布线，前置检查强断言）；开发线 `multi-dev-next` 工作树同工具链 |
| 脚本语言 | 纯 PowerShell 7（`pwsh`），Windows-only |
| 游戏环境 | dev `runClient`（`run/<版本>/`），测试世界为超平坦、`allowCommands=1` + `keepInventory=true`（由环境阶段强制写入） |
| 输入注入 | 系统需装「英语(美国)」键盘（KLID 00000409）；`mt_ime` 按窗口线程切换输入法 |
| 机器本地配置 | `scripts/test/mt.conf`（不入库；整合包 mods 路径、MCP 二进制路径等） |
| 测试环境模组 | 兼容栈（KubeJS/Rhino/JEI/ModernFix 等）、Sodium/Iris 光影栈、「Superflat World No Slimes」+ Collective（史莱姆压制，硬闸门） |

**测试前清场（强制）**：冷启动进入世界后由 `mt_launch` 自动连发两次 `/kill @e[type=!player,distance=..128]`（`MT_PRECLEAN: OK`）；`--no-preclean` 仅限特殊取证。
**禁用生物 AI（强制硬闸门）**：`astral_test_noai.js` 每 2 tick 强制玩家 128 格内 Mob `setNoAi(true)`；进入世界 30 秒内必须读到 `AP_NOAI:` 且 `mobs == noai`，否则 `MT_LAUNCH: ERROR`（对照实验可 `MT_ALLOW_MOB_AI=1`）。**禁止**用 `doMobSpawning false` 代替（会把史莱姆压制闸门变成假证）。

## 3. 唯一入口与阶段

```powershell
pwsh -NoProfile -File scripts/test/mt.ps1                      # 全流程（P→B→E→L→C→R，三版本 + 跨版本门控）
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1      # 只跑指定版本（无跨版本门控）
pwsh -NoProfile -File scripts/test/mt.ps1 --phase <p> --version <v>
pwsh -NoProfile -File scripts/test/mt.ps1 --version <v> --phase stop --purge-saves   # 收尾
```

| 阶段 | 实现 | 职责 | 终态标记 |
|---|---|---|---|
| P 前置 | `mt_preflight.ps1` | 分支/输入法/遗留进程/MCP/兼容栈/模组来源/可写性 | `MT_PREFLIGHT: OK/FAIL`（失败退出 10） |
| B 构建 | `mt_build.ps1` | `gradlew :<子项目>:build`，超时看门狗 + `BUILD SUCCESSFUL` 识别 + 产物校验 + 重试 | `MT_BUILD: OK/FAIL` |
| E 环境 | `mt_env.ps1` | `mods` 装兼容模组 / `world` 重建测试世界 / `kill` | `MT_WORLD: OK/BLOCKED`（11） |
| L 启动 | `mt_launch.ps1` | 启动 runClient、轮询就绪日志、清场、闸门（OP/dump/禁AI/史莱姆压制） | `MT_LAUNCH: OK/FAIL`、`MT_preflight-op: BLOCKED/ERROR` |
| C 条目 | `mt_case.ps1` | 顺序执行 `cases/*.json`：注入命令/按键 → 等待 → 断言；逐条自动写入报告 | 每条 `PASS/FAIL` |
| R 报告 | `mt_report.ps1` | 收集证据（日志/崩溃/截图）→ `report.md` → `SUMMARY.md` | `MT_REPORT: OK/FAIL` |

辅助：`mt_assert`（断言引擎）、`mt_inject`（键鼠注入）、`mt_ime`（输入法）、`mt_capture`（截图）、`mt_cleanup`（退出清理唯一实现）、`mt_stop`、`mt_gen_case`（条目生成器）。
共享模块：`lib/Mt.{Conf,Paths,Phase,Proc,Win32}.psm1`。开发辅助：`scripts/devtools/Start-MtDetached.ps1`、`Test-MtSyntax.ps1`。
⚠️ 长命后代阶段（build/launch/env world）必须经 `Start-MtDetached.ps1` 脱离执行，否则调用方按整棵进程树等待而卡死。

## 4. 测试顺序与门控

| 顺序 | 版本 | 执行条件 | 结论 |
|---|---|---|---|
| 1 | 1.21.1 | 前置检查通过 | 独立 PASS/FAIL |
| 2 | 1.20.1 | **1.21.1 PASS**（门控） | 独立 PASS/FAIL；未执行记 `GATED`（不是失败） |
| 3 | 26.1.2 | 按 §一致性测试方法另行安排 | 一致性 diff 结论 |

退出码：全过 `0`；任一失败或汇总未全过 `1`。汇总 `reports/<运行id>/SUMMARY.md`，明细 `<运行id>/<版本>/report.md`。

## 5. 断言通道与读数纪律（原探针机制的规则）

- 断言读数以**服务端权威读数**为准：探针命令向玩家聊天通道打印 `AP_<tag>_<KEY>:...` 机器行，落 `logs/latest.log`；断言在日志上做正/反向正则。**禁止**以命令返回值（`rc=`）为判据（两版本返回值语义不一致）。
- 只读命令（`dumpstate`/`opprobe`）只用于读数，不得替代被测动作；判定入口行（`LOCKDERIVED` 等）**禁止**作断言锚点（拿被测功能验证自身）。
- 命令缺失/失败必须显式失败（`absent` 断言 + `AP_..._DUMP_ERR` + launch 二道闸门），**绝不静默降级**。
- 断言窗口 `scope`：`case`（缺省，自本用例起的增量）/ `whole`（整文件，启动期事实）/ `launch`。「文本不是本用例产生」的反向断言必须显式 `scope: whole`。
- 探针改动需**冷启动**才生效（`/kubejs reload` 不重绑已注册命令）。

## 6. 条目 schema

```json
{ "case_id": "...", "title": "...", "version": "1.21.1",
  "steps": [ {"op":"inject_command","text":"..."}, {"op":"wait","ms":800}, {"op":"screenshot","crop":true} ],
  "asserts": [ {"type":"log","pattern":"AP_...","scope":"case"} ],
  "on_fail": "keep_game_running" }
```
步骤 op：`inject_command` / `inject_key` / `note` / `wait` / `screenshot` / `kubejs_reload`。
断言 type：`log`（正则）、`absent`（反向）、`crash`（无新增崩溃报告）、`kubejs`（server.log 0 error）、`mixin`（应用行存在，固定 `launch` 窗口）、`vision`（截图人工判定）。
`on_fail=keep_game_running` 落 `.mt_keep_alive` 保留现场；取证完用 `--phase stop --force` 释放。
长按类用例必须真按住（`-HoldMs`）并附**对照步**证明重复确实发生（生存模式、物品会消耗）。

## 7. 超时与看门狗（严格预算，不允许长时间等待）

| 层 | 默认 | 覆写 | 超时行为 |
|---|---|---|---|
| 单条用例 | 180 s | `MT_CASE_TIMEOUT_SEC` | 该条记 `TIMEOUT`（12），跳过并继续下一条 |
| 单阶段子进程 | preflight 120 / build 300 / env 240 / launch 180 / report 90 / stop 150 / cleanup 180 s | `MT_<阶段>_TIMEOUT_SEC` | 只终止本方子进程（不动进程树），返回 12 |
| cases 阶段 | 单条+90 s；全目录 90s×条数+90 s | `MT_CASES_TIMEOUT_SEC` | 同上 |
| 全局运行 | 2700 s | `--run-timeout` / `MT_RUN_TIMEOUT_SEC` | 强制收停 → 报告 `TIMEOUT` → 12 |

- 等待期可见性：`MT_WAIT:` 心跳（10s）+ 进度信标 `cases/.mt_progress.json`（阶段/用例/步号/op）。
- `mt_watchdog.ps1` 可独立包裹长流程（`-Action stop`）：进展判据=进度信标内容→run-state→日志**语义标记**（非文件大小）；停滞 180 s ⇒ `MT_WATCHDOG: STALL` + 取证，退出码 **42**。
- 语义边界：`FAIL`=断言不满足；`ERROR`=跑不起来（工具链/前置）；`TIMEOUT`=预算内没跑完。三者不得混同。

## 8. 清理与收尾（2026-09-17 用户规则）

- 标准收尾：`mt.ps1 --version <V> --phase stop --purge-saves`（全流程退出时自动带 `--quiet --purge-saves`；分步执行必须手工收尾）。
- 删除范围 = 测试世界存档（不动 `run` 之外与种子包）；`--phase stop` 默认**保留**存档（重登类两段用例需要）。
- `.mt_keep_alive` 存在时收停/清档只提示不执行（保留现场优先）。
- 收停只杀本流程的客户端 / Gradle 守护 / run 包装器，**绝不** taskkill 其它 java 进程（含用户 Minecraft）。

## 9. 静态守门（每次改动后必须全绿，不依赖游戏）

`tools/check_lang_sync.ps1`（双语同步）、`scripts/audit/tooltip_color_audit.ps1`（染色规则）、`verify_chip_recipes.ps1`、`verify_chip_acquisition.ps1`、`verify_bountiful_pools.ps1`、`verify_bountiful_instance_exclusions.ps1`、`tools/check_mod_sources.ps1`（模组来源 Curse/Modrinth 独占口径）、`scripts/devtools/Test-MtSyntax.ps1`（工具链语法门）。

> ℹ️ `scripts/verify/verify_content_library.ps1` **不属本清单**（2026-09-23 裁出）：它是 **1.2.0 冻结期一次性验收工具**，对照件是 1.2.0 的冻结快照 ⇒ 工程推进后必然报偏差（1.3.0 开发期 15 项），不代表回归。详见 `TESTING-SPEC.md` §9。

## 10. 退出码总表

| 码 | 含义 |
|---|---|
| 0 | PASS（该层全部通过） |
| 1 | FAIL（断言不满足 / 汇总未全绿） |
| 2 | ERROR / INVALID（跑不起来、用例非法） |
| 10 | preflight FAIL |
| 11 | BLOCKED（环境/启动被闸门挡下） |
| 12 | TIMEOUT |
| 42 | watchdog 检出停滞 |

## 11. 26.1.2 一致性测试方法（该线验收口径）

「同探针 + 同用例 + 双侧读数 diff」：探针子命令名/读数键/格式两侧逐字一致；用例成对落盘（断言逐字相同，仅 `version` 不同）；分别跑 `--version 1.21.1` / `--version 26.1.2` 后逐键 diff；非平台固有差异按缺陷登记，**禁止**改断言迁就。允许差异须逐条登记依据（无 Iron's Spells、有长矛、`opprobe.level=-1`、Curios 主版本差异）。26.1.2 发布前一致性测试为必过项。

## 12. 覆盖缺口（如实标注）

13 骰子 / 17 立牌 / 60 筹码的逐特性玩法数值无 per-feature 自动用例（仅静态守门 + 历史手测）；多人联机 / 服务端专用路径 / 存档跨版本兼容 / 性能无覆盖。

## 13. 已知关键坑（摘自 TESTING-SPEC §10，重建资产前必读）

Gradle 守护不退出（60s 看门狗 + 双重验证）；`fileHashes.lock` 占锁只杀 wrapper/daemon；runData 被 `run/mods` 旧 jar 遮蔽且不报错；run-id 粘性（开新 run 删 `.mt_run_state.json`）；`/kubejs reload` 不重绑命令；探针取物品必须全限定 id；1.20.1 `RegistryObject` 必须 `.get()`；近战横扫/着火会污染雷击读数（靶距 ≥2.8 格 + `doFireTick false`）；同会话重跑同用例会误伤 `absent` 断言（先 stop→launch 重置快照）；玩家死亡与重生之间的读数不可信；`-Wait` 会等整棵进程树；超时是独立结论。

## 14. 现行状态（2026-09-20）

全部用例（`cases/*.json`）、全部探针（`resources/kubejs/**/astral_*.js` 及 run 内注入副本）、历史报告与 temp 证据**已删除作废**；流程实现（`mt*.ps1`、`lib/`、`devtools/`）与本文/`TESTING-SPEC.md` 规则**保留待核验**。在用户核验并重新授权前：`--phase cases` 无条目可跑、探针类闸门不可用，**不得**把本流程任何输出当验收证据；重建时的口径变化（若有）应先更新 `TESTING-SPEC.md` 再落用例。
