# Windows 测试通道解耦规格（2026-11-01，B 口径）

> 本文件是本批改造的**唯一契约源**。实现方（mt.ps1 重写 / 通道模块 / git bash 资产）以本文件为准，不得各自发明参数名与机器行。

## 0. 决策（用户 2026-11-01 选定 B）

- `scripts/test/mt.ps1` 主流程**彻底不做 GUI 键鼠注入与截图**；键鼠与截图改由 git bash 侧（AutoJS HTTP / computer-use MCP）驱动。
- `user32` 原生层（`scripts/test/lib/Mt.Win32.psm1`、`mt_inject.ps1`、`mt_capture.ps1`、`mt_ime.ps1`）**保留但不再被主流程默认路径加载**，只服务旧线/legacy 通道。
- 依据：`scripts/test/mt_case.ps1:110-135`（原语封闭表）、`:1629-1759`（执行分支）、`docs/features/autojs-test-harness-spec.md:42`（客户端侧才能驱动 Minecraft/Screen/KeyMapping）。

## 1. 通道抽象（新模块 `scripts/test/lib/Mt.Channel.psm1`）

导出函数（**只导出这些名字**）：

| 函数 | 签名 | 说明 |
|---|---|---|
| `Get-MtChannelMode` | `-Requested <string>` → `'autojs'|'native'` | 解析顺序：显式参数 > 环境变量 `MT_CHANNEL` > 默认 `'autojs'`；非法值 → `throw` |
| `Test-MtChannelAutoJs` | `-Version <v> [-TimeoutMs <n>]` → `bool` | 走 `lib/Mt.AutoJs.psm1` 的 `Test-MtAutoJsEndpoint`；输出机器行 |
| `Send-MtChannelCommand` | `-Version <v> -Command <string> [-Side server|client] [-TimeoutMs <n>]` | 命令通道 |
| `Send-MtChannelKey` | `-Version <v> -Key <string> [-HoldMs <n>] [-NoEsc]` | 按键通道 |
| `Send-MtChannelMouse` | `-Version <v> -Button <left|right> [-Shift] [-HoldMs <n>]` | 鼠标通道 |
| `Invoke-MtChannelShot` | `-Version <v> -Tag <string> -Mode <f2|window> [-Crop]` | 截图通道 |
| `Get-MtChannelPlan` | `-Version <v>` | 诊断用：当前模式 + autojs 可用性 + 各 op 预期去向 |

返回值统一为 `[pscustomobject]`：`@{ Status = 'OK'|'DELEGATED'|'ERROR'; Channel = 'autojs'|'native'|'delegated'; Detail = <string>; Raw = <object> }`。
`Status='DELEGATED'` 表示该 op 在 autojs 通道下**无法等价表达**，已记录委派清单但不算失败。

### 1.1 autojs 通道实现口径（命令/按键/鼠标）

- `inject_command` → `eval_js {side:'server'}`，脚本体 `AutoJS.command(<js 字面量>)`（AutoJS 提供 `AutoJS.command(cmd)`：以服务端身份执行原版命令，任何 mod 命令都能下）。命令字符串必须用 **JSON 转义后的 JS 字符串字面量**，禁止字符串拼接注入。
- `Side=client` 时改为 `Client.getConnection().sendCommand(...)` 等价路径**仅作后备**，首选仍是 server。
- `kubejs_reload` → `lib/Mt.AutoJs.psm1` 的 `Invoke-MtAutoJsReload`（已有，`mt_case.ps1:413`）；**必须把失败计入用例结果**（现状 `:1701-1707` 用 `[void]` 吞掉 rc，属缺陷）。
- `inject_key` → 按键映射表（下表），命中即执行，未命中 → `DELEGATED`：

| 用例 key | autojs 等价实现（客户端侧 eval_js） |
|---|---|
| `escape` / `esc` | `Client.setScreen(null)`（关闭当前界面；无界面时等价 `KeyMapping` 无效，直接视为 OK） |
| `e` | `Client.setScreen(new InventoryScreen(Client.player))`（`net.minecraft.client.gui.screens.inventory.InventoryScreen`） |
| `f1` | `Client.options.hideGui = !Client.options.hideGui` |
| `rclick` / `use` / `right` | `AutoJS.use()`（服务端侧，走真实右键路径） |
| `lclick` / `attack` | 准星命中实体 → `AutoJS.attack(e)`；命中方块 → `AutoJS.mine(pos)`；未命中 → `DELEGATED` |
| 其它单键（`k`/`1` 等） | `DELEGATED`（写清单，附 key 名） |

- `inject_mouse` → `left` 走 `AutoJS.attack/mine`，`right` 走 `AutoJS.use()`；`-Shift` 先 `AutoJS.crouch(true)` 再执行、finally `crouch(false)`。GUI 内点击（如界面按钮）→ `DELEGATED`。
- `screenshot` → 客户端侧尝试 `net.minecraft.client.Screenshot.grab(...)`（目标 `run/<version>/screenshots/<tag>.png`）；**无论成功与否**都要向 `run/<version>/screenshots/.mt_shots.json` 追加 `{file,tag,ts}` 条目（schema 见 `lib/Mt.Paths.psm1:201-205`），否则 `assert.type=vision` 全 BLOCKED。失败 → `DELEGATED`。

### 1.1b 通道能力事实（2026-11-01 取证：AutoJS jar 字节码 + 客户端模块 + 既有运行记录）

- 端点 `POST http://127.0.0.1:25585/mcp`（`lib/Mt.AutoJs.psm1:45-49`）；`GET /health` → `ok`；`GET /mcp` → 405（**无 SSE**）；鉴权只有可选 `Authorization: Bearer`（`mt.conf` 键 `MT_AUTOJS_TOKEN`）。bash/curl **必须加 `--noproxy '*'`**，否则系统代理会把 loopback 打成 502 假阴性。
- 工具面只有 4 个：`status` / `eval_js` / `kubejs_log` / `reload_scripts`；**无输入原语、无截图原语**。
- `eval_js` 的 `code` 必须是表达式（顶层 `return` 抛 invalid return），一律写 `(function(){...})()`；`ok:true` 不代表没出错，必须看 `diagnostics`。
- 命令下发用**探测链**（按序试，首个成功即缓存，机器行写 `detail=cmd_via=<name>`）：
  1. `AutoJS.command("<cmd>")`（AutoJS 脚本助手，服务端身份）
  2. `server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "<cmd>")`（原版 API）
  3. 客户端侧 `Client.getConnection().sendCommand("<cmd>")`
  三条都不成立 → `Status='DELEGATED'`。
- 输入：`use()` / `attack(e)` / `mine(pos)` / `crouch(on)` / `move(...)` 属 AutoJS 助手面；**GUI 内点击与真实键位事件无等价物**（jar 内 70 个类常量池 0 命中 KeyMapping/MouseHandler/InputConstants），一律 `DELEGATED`。
- 截图：AutoJS 无截图原语；autojs 通道下先试客户端 `Screenshot.grab(...)`，失败即 `DELEGATED`（**禁止**写假的 `.mt_shots.json` 条目）。
- **AutoJS 仅投放 1.21.1 与 26.1.2**（`mt_env.psm1:137-151`；1.20.1 / fabric 投放清单为空）。故 1.20.1 线的 `inject_command` 在 autojs 通道下必然是 `DELEGATED`：该线需要 GUI 注入时必须显式 `--channel native`，且报告必须体现降级计数。

### 1.2 native 通道（legacy）

仅当 `Get-MtChannelMode` 返回 `'native'`（`--channel native` 或 `MT_CHANNEL=native`）时，才调用既有 `mt_inject.ps1` / `mt_capture.ps1`，行为与改造前**逐字节一致**。

### 1.3 机器行（新增，不复用旧行）

```
MT_CHANNEL: mode=autojs|native
MT_CHANNEL: op=<command|key|mouse|shot|reload> channel=autojs|native|delegated status=ok|delegated|error detail=<单行文本>
MT_CHANNEL: delegated_count=<n>
```

`detail` 内禁止换行；含空格与中文照常。委派清单落盘 `run/<version>/.mt_delegated.json`（数组，元素 `{op,key|button|tag,reason,ts}`）。

## 2. `scripts/test/mt.ps1` 重写契约

- 完全按 PowerShell 7 格式重写（`#Requires -Version 7.0`、`Set-StrictMode -Version Latest`、`[CmdletBinding()]`、类型化参数、`param([Parameter(ValueFromRemainingArguments=$true)][string[]]$ArgList)` + 查表解析，禁止散落的 `$args[$i]` 裸下标）。
- **外部契约不变**：全部既有开关（`--version/--phase/--case/--new/--feature/--spec/--run-timeout/--case-timeout/--allow-auto/--cleanup-on-exit/--no-cleanup-on-exit/--force/--purge-saves` 等，以实现时 `mt.ps1` 实际支持的全集为准）、阶段集合（`preflight|build|env|launch|cases|report|stop`）、阶段预算、跨版本门控、退出码常量（`Mt.Phase.psm1:29-36`）、全部既有机器行（`MT_LAUNCH:`/`MT_STOP:`/`MT_CLEANUP_*`/`MT_ERROR:` 等）与提示文案语义。
- 新增开关：`--channel autojs|native`（默认 autojs），透传给 `mt_preflight/mt_launch/mt_case`（子进程参数名同为 `--channel`）。
- **零 user32 耦合**：`mt.ps1` 不得 `Add-Type`、不得 `Import-Module Mt.Win32.psm1`，且其导入链（`Mt.Phase`/`Mt.Paths`/`Mt.Proc`）不得传递引入 `Mt.Win32`。判据命令见 §4。

## 3. git bash 资产（新目录 `scripts/test/win/`，**不得覆写 `scripts/test/linux/`**）

```
scripts/test/win/
  README.md          # Linux 机制 → Windows 对应物 对照表 + 机器行契约 + 用法
  lib/common.sh      # repo/版本表/机器行/密码机行 helper、pwsh 调用、CIM 进程查询、taskkill、AutoJS curl
  env.sh             # 只读环境检查（无写操作）
  launch.sh          # 启动 + 就绪判定（AutoJS 优先，日志 marker 兜底）
  stop.sh            # 收停（CIM 判线 + taskkill /T /F）+ --purge-saves
  logs/              # 运行日志落盘（.gitignore 里忽略 *.log 由实现方决定）
```

- 机器行：`CHECK: <key>=OK|WARN|FAIL — <detail>`、`INFO: <key> — <detail>`、`ENV_RESULT: ...`；启动 `LAUNCH: ...`；收停 `WIN_STOP: ...`、清档 `PURGE: ...`。
- 退出码沿用 Linux 契约：`0=OK/WARN`、`1=失败`、`2=用法错误`、`11=BLOCKED`、`12=TIMEOUT`、`130=中断`。
- Linux 专有机制一律按「CIM 命令行匹配 + `taskkill /PID /T /F`」替代 `/proc`；`df -T` 替代 `findmnt`；临时文件禁止落 `/tmp`（本机被沙箱拒绝），落 `$TEMP` 或 `run/<ver>/`。
- 版本表必须四线齐全：`1.21.1`(neoforge) / `1.20.1`(forge) / `26.1.2`(neoforge) / `fabric-1.20.1`(fabric)（`lib/Mt.Paths.psm1:32` 现缺 fabric，bash 侧必须自带全表）。

## 4. 判据（验收命令，任何一方交付前必须自查）

```bash
# 1) 全部 ps1/psm1 语法可解析
pwsh -NoProfile -Command "Get-ChildItem scripts/test -Recurse -Include *.ps1,*.psm1 | ForEach-Object { $e=$null; [System.Management.Automation.Language.Parser]::ParseFile($_.FullName,[ref]$null,[ref]$e) > $null; if ($e.Count) { \"PARSE_FAIL $($_.FullName) $($e[0].Message)\" } }; 'PARSE_DONE'"

# 2) mt.ps1 零原生耦合（必须 0 命中）
grep -n "Mt\.Win32\|user32\|Add-Type" scripts/test/mt.ps1

# 3) bash 语法
bash -n scripts/test/win/env.sh && bash -n scripts/test/win/launch.sh && bash -n scripts/test/win/stop.sh

# 4) 只读实跑（不得启动游戏）
bash scripts/test/win/env.sh
bash scripts/test/win/stop.sh --dry-run
bash scripts/test/win/launch.sh --version 1.21.1 --dry-run
```

## 5. 红线

- 不 `git add/commit/push`；不改 `scripts/test/linux/**`；不启动游戏（实机需用户明确下达）；不跑 `gradlew`。
- 不删既有脚本；`mt_inject/mt_capture/mt_ime/Mt.Win32` 一律保留（legacy 通道仍引用）。
- 每个写文件动作前先读；改动只落在自己的写域内（见任务卡 `write_scopes`）。

## 6. 增补裁决（2026-11-01 用户三条，覆盖前文冲突处）

1. **AutoJS 就绪判据放宽至所有版本**。判据 = 本线 `run/<版本>/mods` 里 `AutoJS-*.jar` 是否在位（四线实测都在位：`AutoJS-neoforge-1.21.1-1.0.0.jar`、`AutoJS-forge-1.20.1-1.0.0-dev.jar`、`AutoJS-neoforge-26.1-1.0.0.jar`、`AutoJS-fabric-1.20.1-1.0.0.jar`）。§1.1b 末尾「1.20.1 / fabric 未投放」作废；缺 jar 的线自动退回日志 marker（`env.sh` 出 WARN，不阻塞整体 env；`launch.sh` 置 `probe_enabled=0`）。
2. **默认测试流程转 bash 控制，逐步淘汰所有 PowerShell**。新默认入口为 `bash scripts/test/win/mt.sh`（阶段编排在 bash），过渡期 cases/report 仍委托 pwsh 并打 `MT_DEPRECATED:`；淘汰路线见 `docs/features/bash-first-test-flow-plan.md`。
3. **尝试把 pwsh 资产转为 CRLF**。现状（阻塞）：bash 子进程对 `scripts/**`、`run/**`、`docs/**` 无写权限——根目录的沙箱授权 `S-1-4-402697963-114571753:(OI)(CI)(W,D,DC)` 未下传到子目录，受限令牌没有 WRITE_DAC 因而 `icacls /grant` 被拒；DSH 文件工具可写且**保留 CRLF**（`od -c` 实测）。待用户在自身终端补权限后执行字节安全转换（`\r?\n`→`\r\n`、无 BOM、转后复核 CRLF / bare-LF 计数）。权限备份：`acl-backup-20261101-153946.txt`。

4. **（2026-11-01 追加）PowerShell 全局禁用 ⇒ `scripts/test/mt.ps1` 废弃**。按用户裁决，`mt.ps1` 的问题（含 `'^\\d+$'` 双反斜杠导致 `--run-timeout` 类开关恒 exit 2）**一律不修**，该文件不再演进；测试台唯一编排入口是 `bash scripts/test/win/mt.sh`，pwsh 资产在 P2–P4 内被逐个替换后物理删除（路线见 `docs/features/bash-first-test-flow-plan.md`）。
5. **（2026-11-01 追加）实机暂不验证**：`launch.sh` 的 AutoJS 主判据、1.20.1 dev jar 的 `status(client available=true)` 能力，全部推迟到用户下达实机指令后再验。

## 7. 实施状态（本节写入时的快照）

- 已落盘：`lib/Mt.Channel.psm1` 864 行（7 导出）、`mt.ps1` 960 行（零 user32）、`mt_case.ps1` 2979 行、`mt_launch.ps1` 1226 行、`mt_preflight.ps1` 763 行；`scripts/test/win/{mt.sh 316, lib/phases.sh 449, lib/common.sh 595, env.sh 416, launch.sh 397, stop.sh 343, README.md}`。`win/mt.sh` 为默认编排入口（P1 契约层），cases/report/preflight 过渡期委托 pwsh 并打 `MT_DEPRECATED:`。
- 待完成：P2/P3/P4 与 fabric 平行轨（见 `docs/features/bash-first-test-flow-plan.md`）；CRLF 转换（受阻，等权限）；`scripts/test/linux/launch.sh` 与 `mt_env.psm1` 的 `AutoJsModsByVersion` 版本投放白名单（另批）。
- 换行实测基线（2026-11-01，字节级）：`scripts/test/**` 39 个 pwsh 资产中 **37 个 CRLF / 2 个 LF**——LF 的两个是 `scripts/test/mt.ps1`（960 行）与 `scripts/test/lib/Mt.Channel.psm1`（864 行），其余全部 CRLF；`scripts/test/win/**` 全部 LF（`crlf=0`）。⚠️ 注意 `grep -c $'\r'` 在本机 ops_bash 里不可靠（CR 被传输层吃掉 ⇒ 模式变空 ⇒ 全行命中），换行必须用字节级判定（`python3 -c "open(p,'rb').read().count(b'\r\n')"` 或 `od -c`）。
- 保留待裁决的既有缺陷：`mt.ps1:172` 的 `$script:MtNonNegativeIntPattern = '^\\d+$'` 恒为 False ⇒ `--run-timeout` / `--case-timeout` / `MT_*_TIMEOUT_SEC` 从未生效（修法：`\\d`→`\d`）。
