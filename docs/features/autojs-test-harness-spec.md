# AutoJS 实机测试台重写规格（autojs-test-harness-spec）

> 用户指令（2026-10-18）：把 AutoJS（https://github.com/MerrySnow404/AutoJS）作为实机测试**专用库**导入所有测试环境；
> 重写整个实机测试流程，用 AutoJS 替换旧的纯 KubeJS 通道；把 AutoJS 的 MCP 服务加进 DSH。
> 用户裁决：范围 = **A 档（核心通道重写 + 样板用例）**；实机验证 = **已授权**（收尾带 `--purge-saves`）。

## 0. 现状（取证来源：`temp/t1/harness_map.md`，只读测绘）
- 旧台 = `scripts/test/mt*.ps1`（约 1.1 万行）+ `scripts/test/lib/Mt.*.psm1`；阶段 P→B→E→L→C→R→stop，退出码 0/1/2/10/11/12。
- 旧取证通道：`scripts/test/resources/kubejs/<ver>/**` 冷启动注入 `run/<ver>/kubejs/**`（`mt_env.ps1:1004-1094`），读数全靠 `[CHAT] AP_*` 行（`logs/latest.log`）与 `logs/kubejs/client.log`；断言类型 `log/absent/crash/kubejs/mixin/vision`（`mt_case.ps1:125`）。
- 规模：用例 220 个 json（1.21.1=99 / 1.20.1=65 / 26.1.2=53 / 无后缀 3）+ `blocked-26.1.2/` 12；探针 43 个 .js（`astral_bugfix_probe.js` 单文件 1.6 万行）。
- 常量约束（必须继承，见 harness_map §2.3/§3/§9）：1.20.1 生产 jar 是 SRG，不能手放 `run/1.20.1/mods`；同族 jar 只能一份（`Duplicate mods:`）；`mt_scope.ps1` 不是启动器；就绪判据按版本分裂（1.21.1/1.20.1 认 `Total time to load game and open world was`，26.1.2 必须 `logged in with entity id` + `Loaded <N> advancements`）；主台**没有任何** HTTP/端口探测工具（要新写）。

## 1. AutoJS 的事实（源码取证 + 自建产物）
- 端点：Streamable HTTP，`POST http://127.0.0.1:25585/mcp`，默认 bind `127.0.0.1`；`GET /health` 探活、`GET /` 摘要；无服务端 SSE（`GET /mcp` → 405）。配置 `config/autojs.json`（首启生成；`enabled/bindAddress/port/token/unrestricted/evalTimeoutMs`）。端口覆盖序：`-Dautojs.port=` > `AUTOJS_PORT` > 配置文件；`0` = 系统分配。
- 工具面（只有 4 个）：`status` / `eval_js {code, side: auto|server|client, timeout_ms}` / `kubejs_log {side, kind: errors|warnings|all, limit}` / `reload_scripts {side, timeout_ms}`。返回 `{ok,side,elapsedMs,type,value,diagnostics}`，失败带 `error/stack`；**必须看 diagnostics**（部分失败只写诊断不抛异常）。
- 依赖：**rhino 必装**（各线区间：1.21.1 `[2101,)` / 26.1 `[2101,)` / forge 1.20.1 `[2001,)` / fabric `>=2001.2.2-build.1`）；**KubeJS 可选**（装了就用 KubeJS 内核，没装用自建 Rhino 内核）。fabric 另需 `fabric-api >=0.92.11`。
- 上游**不发布 jar**（无 GitHub release、Modrinth 404、CurseForge 被 Cloudflare 挡）⇒ 本仓自建并从源码构建。已构建（`temp/AutoJS-src`，浅克隆）：
  - `targets/neoforge-1.21.1/build/libs/AutoJS-neoforge-1.21.1-1.0.0.jar`（163541 B，mojmap，可直接进 `run/1.21.1/mods`）
  - `targets/neoforge-26.1/build/libs/AutoJS-neoforge-26.1-1.0.0.jar`（164568 B，可直接进 `run/26.1.2/mods`）
  - `targets/forge-1.20.1/build/libs/AutoJS-forge-1.20.1-1.0.0.jar`（164386 B，**SRG**，不能手放 run/mods）
  - `targets/fabric-1.20.1/build/libs/AutoJS-fabric-1.20.1-1.0.0.jar`（intermediary，dev 用 named，不能手放 run/mods）
  - 构建口径：各 target 目录内 `.\gradlew.bat --no-daemon --console=plain build`（**不要**传 `-Pautojs.localToolchain`：那会把 `libs-maven` 占位仓库排到最前，导致 `Could not find kubejs-forge/rhino-forge/architectury`；本机经 `http://127.0.0.1:7897` 代理能连上游 maven）。
- 许可证 GPL-3.0（内部测试用途，随附来源与提交哈希）。

## 2. 目标态（本规格要交付的）
1. **AutoJS 成为主通道**：判据从「读 `AP_*` 日志行」改成「`eval_js` 返回结构 + MCP 状态」，日志断言降级为旁证（`mixin/kubejs/crash` 保留，报告标注「日志通道」）。
2. **新 PowerShell 模块 `scripts/test/lib/Mt.AutoJs.psm1`**：MCP 客户端（initialize → tools/call，处理 JSON 与 SSE 两种响应、session id）、端点探活、`eval_js`/`status`/`kubejs_log`/`reload_scripts` 四个包装、错误归一（`ok=false` / `isError` / 超时 → 明确错误对象，不静默）。
3. **ENV 装配**：按线把 AutoJS jar（+ rhino 视需要）落进 `run/<ver>/mods`（1.21.1 / 26.1.2）或经 `build.gradle`（1.20.1 / fabric，路线见 §6）；幂等（SHA256）+ 同族前缀清理 + 报告登记来源。
4. **PREFLIGHT**：新增静态项（jar 已就位 + `MT_AUTOJS_ENDPOINT` 已配置 + `config/autojs.json` 存在性）；**不改**既有前置检查项（注：当时保留的 `Test-MtPreflightMcpBinary` 已于 2026-10-18 随 `computer-control-mcp` 退役一并删除）。
5. **LAUNCH**：世界就绪后新增硬闸门 `Test-MtAutoJsEndpoint`（TCP + initialize + `status`），逃生阀 `MT_ALLOW_NO_AUTOJS=1`；闸门文案与既有五道一致（`MT_LAUNCH: ERROR — …`）。
6. **CASE 原语与断言**：原语表新增 `eval_js` / `await_state`（**封闭表**，未知 op 立刻 ERROR）；断言类型新增 `eval`（结构化比较）与 `state`（引用前一步 `save_as`）；`scope` 与窗口语义对返回值型断言改为「按 case/save_as 过滤」，日志型断言保持原窗口机制。
7. **样板用例**：迁移 1~2 条代表用例（首选 `CHIP-ECON-OFF-1.21.1` 的 AutoJS 版 + 一条战斗类），放在 `scripts/test/cases/` 并打 `"channel":"autojs"`；其余 220 条**保留**并登记为 legacy（`"channel":"kubejs"` 默认或省略），文档写明欠账。
8. **DSH MCP**：把 AutoJS 端点加进 `C:\Users\xmace\.dsh\profiles\desktop\cordis.patch.yml`（`mcp-autojs`，`streamable-http`，url `http://127.0.0.1:25585/mcp`，`failOnStartupError: true` + `reconnect` 仿 blockbench 条）。
9. **文档**：`scripts/test/TESTING-RULES-OVERVIEW.md` 与 `TESTING-SPEC.md` 增 AutoJS 通道章节；`docs/agent/AGENTS-full.md` 测试章节登记「AutoJS = 实机测试专用库」。

## 3. 通道契约（实现必须遵守）
- **端点探测**：先 TCP 连 `127.0.0.1:25585`（`MT_AUTOJS_TIMEOUT_MS` 默认 15000，轮询 500ms），再 `initialize`，再 `tools/call status`；三步任一失败即「端点未就绪」。
- **MCP 客户端**：`POST <endpoint>`，headers `Content-Type: application/json` / `Accept: application/json, text/event-stream` / 有 session 时带 `Mcp-Session-Id`。响应可能是 `application/json`（直接取 `result`）或 `text/event-stream`（按 `data:` 行取最后一个 JSON-RPC 消息）。`initialize` 结果里的 `protocolVersion` 原样回填，随后发 `notifications/initialized`。
- **错误归一**：`result.isError=true` 或 `result.content[].text` 解析出 `ok=false` ⇒ 抛/返回带 `error`+`stack`+`diagnostics` 的统一对象；**禁止**把「端点没起」记成断言 FAIL（应记 BLOCKED）。
- **side 语义**：改世界状态/权威数据 = `server`；界面/输入/HUD/FPS = `client`；不确定用 `auto`（服务端优先）。客户端侧才能驱动 `Minecraft/Screen/KeyMapping`，需要真实键鼠的用例继续走 `mt_inject`。
- **时序**：集成服务器的 server level 只有在**世界就绪后**才存在 ⇒ 任何 `side=server` 的 `eval_js` 必须在就绪闸门之后发；首个 eval 前先用 `status` 确认两端心跳。

## 4. 用例 schema 增量（CASE 侧）
```jsonc
{
  "case_id": "...", "version": "1.21.1",
  "channel": "autojs",              // 缺省 = kubejs（legacy）
  "steps": [
    { "op": "eval_js", "side": "server",
      "script": "chip_econ/ctab_state.js",   // 与 "code" 二选一；相对 scripts/test/resources/autojs/<ver>/
      "args": { "tag": "EC" },
      "save_as": "ctab",
      "timeout_ms": 20000 },
    { "op": "await_state", "side": "server", "code": "return true",
      "until": "true", "timeout_ms": 10000, "interval_ms": 250 },
    { "op": "assert", "type": "eval", "from": "ctab",
      "expect": { "mats": "4/4", "regen": 1, "wire": 1, "dust": 1, "paint": 1 } },
    { "op": "assert", "type": "log", "source": "latest",
      "pattern": "CHIP_SHOP_OPEN_DENIED player=.+ reason=cooldown" }   // 保留：产品 LOGGER 行只能读日志
  ]
}
```
- `expect` 支持：标量相等、`{"min":n}/`max`/`regex`/`set` 数组（顺序无关）、`{"gte"/"lte"}`；嵌套对象逐键比较；缺失键 = FAIL。
- `assert.type=eval` 必须给 `from`（前序 `save_as`）或内联 `code`；**禁止**「返回非空即 PASS」。

## 5. 保留 / 改造 / 退役
- **保留**：`Mt.Phase`、`Get-MtPaths`、`Mt.Proc`、`mt_build`、`mt_cleanup`/`mt_stop`、`mt_capture`、`mt_ime`/`Mt.Win32`、`mt_watchdog`、启动锁与就绪判据、超平坦+allowCommands+keepInventory 世界前提。
- **改造**：CASE 原语表与断言分派、ASSERT 通道表（增 `autojs` 源）、INJECT（`cmd` 降级为兜底）、ENV 模组装配、LAUNCH 闸门（增 AutoJS 就绪闸门）、REPORT 标记抽取。
- **退役（默认关闭，保留 legacy 兼容）**：KubeJS 文件注入作为**主**通道、`AP_*` 聊天行作为唯一读数、`probe` 通道（历史已废）、`kubejs_reload` 原语（语义已失效）、「探针必须冷启动」的时序债。
- **禁删**：`mixin/kubejs/crash` 三个日志断言（第三方启动期与产品 LOGGER 行不可替代）；禁 AI 四层保证（史莱姆压制模组 + `astral_test_noai.js` + 30s 闸门 + 全轮审计）。

## 6. 1.20.1 / fabric 的接入路线（待取证确认，见 `temp/t1/mapping_recon.md`）
- forge-1.20.1：产物是 SRG ⇒ **不能**进 `run/1.20.1/mods`；走 `forge-1.20.1/build.gradle` 的 `modImplementation`（本地 maven 仓库 / `mavenLocal`），并在 `tools/check_mod_sources.ps1` 的 `$ModExceptions` 登记（门禁 R2 只认 curse.maven / maven.modrinth，本地 jar 一律 FAIL）。
- fabric-1.20.1：产物是 intermediary ⇒ 同样经 `fabric-1.20.1/build.gradle` 的 `modImplementation`（Loom 会对 maven 依赖做 remap；`files(...)` 不会）。
- 若取证判定两条路线均不可行 ⇒ 该线**显式不支持** AutoJS，登记为已知限制（报告与 RULES 都要写），不得静默跳过。

## 7. 验收（本批必须给出证据）
1. `pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase preflight` 输出含新 AutoJS 项；
2. 冷启动 1.21.1 客户端后：`Test-MtAutoJsEndpoint` PASS（贴 initialize + status 原始返回）；
3. 至少 1 条样板用例 PASS（贴 `MT_CASE_RESULT` 与 eval 实际返回）；
4. 收尾 `pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --purge-saves`：`MT_STOP: OK` + `MT_CLEANUP_SAVES: PURGED <n>` + `MT_CLEANUP_RESULT: OK`；
5. `git status --short -uall` 原文（含临时探针已清理）；
6. 静态守门：`:neoforge-1.21.1:classes` exit 0、`tools/check_mod_sources.ps1` 结论行、`check_lang_sync`（若有 lang 改动）。

## 8. 欠账（本批不做，如实登记）
- 220 条用例与 43 个 KubeJS 探针的**全量**迁移（本批只做样板）。
- fabric 线测试台（`scripts/test/fabric/ft_*.ps1`，RCON 那套）与主台合并。
- 26.1.2 线实机（本批只验 1.21.1）。
