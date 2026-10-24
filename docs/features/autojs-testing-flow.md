# AutoJS 实机测试流程（现行版 · 2026-10-24 复验）

> 本文是「怎么把 AutoJS 用起来」的操作口径（端点 / MCP / 技能 / 流程 / 排障）。
> 通道契约与用例 schema 的正本仍是 `docs/features/autojs-test-harness-spec.md` 与 `scripts/test/TESTING-SPEC.md` §7A；
> 本文只补它们没写的那两块：**DSH 侧的 MCP 注册**与**技能安装**。
> 本批（2026-10-24）**未改任何代码与测试资产**（`scripts/test/**` 的 ps1/psm1/用例一律未动）。

## 0. 一句话

游戏里装 AutoJS mod → 它在 `127.0.0.1:25585/mcp` 开一个 MCP 端点 → DSH 用 `mcp__autojs__*` 四个工具直接对**活着的游戏**求值 JS：读任意状态、模拟任意操作、逐条假设试错，秒级往返，不需要重启游戏。

## 1. 三层组成（缺一层就用不了）

| 层 | 载体 | 判据 |
|---|---|---|
| ① 游戏侧 mod | AutoJS jar（四个加载器各一件，放在 `mods/`） | `GET http://127.0.0.1:25585/health` = `200 ok` |
| ② DSH 侧 MCP 注册 | `C:\Users\xmace\.dsh\profiles\desktop\cordis.patch.yml` 的 `mcp-autojs` 条目 | DSH 工具面出现 `mcp__autojs__status` 等四个工具 |
| ③ DSH 侧技能 | `C:\Users\xmace\.dsh\skills\autojs\SKILL.md` | `skill({ name: "autojs" })` 能加载 |

### 1.1 ① 游戏侧 AutoJS jar

| 线 | 文件（相对 `F:\MCProject\astral_dice_multiloader\`） | 备注 |
|---|---|---|
| 1.21.1 | `run/1.21.1/mods/AutoJS-neoforge-1.21.1-1.0.0.jar` | 包根 `cc.sighs` |
| 1.20.1 | `run/1.20.1/mods/AutoJS-forge-1.20.1-1.0.0-dev.jar` | **必须是 devlibs 的 named jar**，包根 `com.sighs` |
| 26.1.2 | `run/26.1.2/mods/AutoJS-neoforge-26.1-1.0.0.jar` | P1，1.5.0 正式版前不验证 |
| fabric-1.20.1 | `run/fabric-1.20.1/mods/AutoJS-fabric-1.20.1-1.0.0.jar` | P1；SRG/intermediary 产物不可手放 |

vendor 副本在 `scripts/test/resources/mods/autojs/`（四件），由测试台的 `E` 阶段投放；上游 GPL-3.0，<https://github.com/MerrySnow404/AutoJS> 。

**jar 根目录自带 `SKILL.md`**（本机实测 17,745 字符，frontmatter `name: autojs`），这就是「SKILL（mod jar 中提供）」的来源——不要另写一份。

游戏侧配置在 `<gameDir>/config/autojs.json`：`enabled` / `bindAddress` / `port` / `token` / `unrestricted` / `evalTimeoutMs`。
端口覆盖序：`-Dautojs.port=NNNNN` > `AUTOJS_PORT` > 配置文件；`0` = 系统分配。默认 `bindAddress=127.0.0.1`、`unrestricted=true`、`evalTimeoutMs=5000`。

### 1.2 ② DSH 侧 MCP 注册

`cordis.patch.yml` 里**只能有一条** `mcp-autojs`：

```yaml
- insert:
    - id: mcp-autojs
      name: '@deepseek-ai/dsh-mcp-client'
      config:
        serverName: autojs
        transport: streamable-http
        url: http://127.0.0.1:25585/mcp
        headers:
          Accept: application/json, text/event-stream
        failOnStartupError: false          # 端点只在游戏跑着时存在 ⇒ 必须软失败
        reconnect:
          enabled: true
          initialDelayMs: 500
          maxDelayMs: 30000
          maxAttempts: 240                 # ≈2h 覆盖：先开 DSH 后开游戏也能挂上
```

- 规格 §2 第 8 条写的是 `failOnStartupError: true`，**现行配置是 `false`**：端点随游戏进程生灭，硬失败会把整个 profile 拖下水。这条差异以本文与配置文件为准。
- **2026-10-24 修掉的坑**：配置文件里曾同时存在**两条** `mcp-autojs`（一条挤在 blockbench 的 `- insert:` 列表里、一条在文件尾部独立块），同一 `serverName` 重复注册 ⇒ 工具面拿不到 `mcp__autojs__*`。已删掉前者，并把两条完全相同的 `mcp-code-review disabled` 去重成一条；备份 `cordis.patch.yml.bak-20261024-autojs`。
- **改完要重载 DSH 会话**：MCP 工具面在会话启动时定形，热改配置不会给进行中的会话补工具。新开一个 DSH 会话即可看到 `mcp__autojs__status` / `eval_js` / `kubejs_log` / `reload_scripts`。
- 没有工具面时**不阻塞工作**：端点本身就是标准 Streamable-HTTP MCP，可用任何 HTTP 客户端直连（`initialize` → `notifications/initialized` → `tools/call`）。本批复验即用这条路径取证（见 §7）。

### 1.3 ③ DSH 侧技能

```
C:\Users\xmace\.dsh\skills\autojs\SKILL.md     # 从 mod jar 根目录的 SKILL.md 原样抽出
```

- 装好后 `skill({ name: "autojs" })` 立即可用（技能目录运行时扫描，无需重启 DSH；本批实测安装后当轮即进入技能目录）。
- **升级 AutoJS 后要重抽**：`SKILL.md` 随 mod 版本走，旧技能会与新端点行为脱节。抽法（Python 标准库即可）：
  `python -c "import zipfile; z=zipfile.ZipFile(r'<jar>'); open(r'<目标目录>\\SKILL.md','wb').write(z.read('SKILL.md'))"`
- 技能正文是**方法论**（读/写/模拟/复杂案例拼法/查本地源码 jar），工具说明只有 4 个；两层互补，缺一不可。

## 2. 四个工具（端点真实工具面）

| 工具 | 参数 | 用途 |
|---|---|---|
| `status` | 无 | 端点摘要：`sides[].available` / `state` / `limits` |
| `eval_js` | `code`（必填，JS 源码）、`side`（`auto`/`server`/`client`，默认 auto）、`timeout_ms` | 在目标侧主线程求值，返回**最后一个表达式的值** |
| `kubejs_log` | `side`、`kind`、`limit` | 读该侧 KubeJS 控制台（脚本加载错误与警告 + 自己 console 打的东西） |
| `reload_scripts` | `side` | 重跑该侧脚本，改文件后免重启 |

返回体：`{ ok, side, elapsedMs, type, value, diagnostics }`；失败带 `error`/`stack`。

## 3. 标准流程（六步）

1. **起游戏**（`mt.ps1 --phase env|run`，或用户自己起的客户端），等世界就绪——集成服务器的 server level 只有世界就绪后才存在。
2. **探活**：`GET http://127.0.0.1:25585/health` → `200 ok`。`GET /mcp` 返回 405 是**正常**的（无服务端 SSE）。
3. **看哪侧活着**：`status` → `sides`。server 与 client 都可能 `available: true`。
4. **迭代求值**：一次一个小假设 → `eval_js` → 看 `value` 与 **`diagnostics`** → 改 → 再跑。需要「随时间观察」时才用事件/轮询，一次性求值不必。
5. **固化成断言**：把验证过的那段包成 IIFE 放进用例（`"channel": "autojs"`，`eval_js` 取 `save_as`，断言走 `type: state`），用 `await_state` 代替固定 `wait{ms}`。样板见 `scripts/test/cases/AUTOJS-SMOKE-1.21.1.json`（通道自检：任何一环不通会报 **BLOCKED**，不会给出假 FAIL）。
6. **收尾**：`mt.ps1 --version <线> --phase stop --force`（归档轮次再加 `--purge-saves`），核对无本流程 java 残留。

## 4. 六条铁律

1. **side 语义**：改世界状态/权威数据走 `server`；界面/输入/HUD/FPS 走 `client`；不确定用 `auto`（服务端优先）。客户端侧才能驱动 `Minecraft`/`Screen`/`KeyMapping`；真实键鼠仍走 `mt_inject`。
2. **必须看 `diagnostics`**：部分失败只写日志、不抛异常（典型：在 snippet 里注册事件处理器/配方/标签/命令 → KubeJS 静默拒绝）。`ok: true` 不代表副作用发生。
3. **运行时不能新建注册表条目**（物品/方块/流体在启动时冻结）⇒ 测新注册内容只能重启游戏；snippet 里定义的全局变量在 reload 后丢弃。
4. **`eval_js` 只求值最后一个表达式**：要返回结构体就写 `(function(){ ... return {...}; })()`；顶层 `return` 会抛 `invalid return`。
5. **Rhino 不是 JS 反射**：`obj.getClass()` 直接抛 `TypeError: Cannot find function getClass`；Java **字段按属性读**（`level.dimension` 可以，`level.dimension()` 抛 `It is not a function, it is "object"`）；`ServerLevel.getGameTime()` 不可调用 ⇒ 用 `server.getTickCount()`（实测 `Cannot find function getGameTime in object ServerLevel[...]`）。
6. **返回值必须 `JSON.stringify`**：直接 `return {a:1}` 时结果 `type:"object"`、`value` 只有 `"[object Object]"`，数据全丢；写成 `JSON.stringify((function(){ ... })())` 才拿到 `type:"string"` 的真 JSON 串，调用方再 `JSON.parse`。

## 5. 与测试台（`mt.ps1`）的关系

- 用例侧：只有 `"channel": "autojs"` 的用例允许 `eval_js` / `await_state` / `mcp_status`；其余 220+ 条 legacy 用例（`channel=kubejs` 或缺省）照旧走日志断言。
- 实现侧：`scripts/test/lib/Mt.AutoJs.psm1`（会话与 session-id、protocolVersion 回填、事件流解析、健康探针、工具调用、状态归一）。
- 启动闸门：`mt_launch.ps1` 在世界就绪后做 TCP + `initialize` + `status` 三步探针；逃生阀 `MT_ALLOW_NO_AUTOJS=1`。
- 报错口径：端点没起/协议不通/模块缺失 ⇒ **BLOCKED**，不得记成功能 FAIL。

## 6. 排障表

| 症状 | 先查什么 |
|---|---|
| 工具面没有 `mcp__autojs__*` | ① `cordis.patch.yml` 是否只有一条 `mcp-autojs`（重复注册会顶掉）；② 本会话是否启动早于游戏；③ 改完配置有没有重载 DSH 会话 |
| `/health` 连不上 | 游戏是否在跑（端点随进程生灭）；`mods/` 里有没有 autojs jar；端口是否被 `-Dautojs.port` 改过 |
| `GET /mcp` 返回 405 | 正常（只支持 POST，无服务端 SSE） |
| `ok: true` 但「什么也没发生」 | 看 `diagnostics` 与 `kubejs_log`：大概率是在 snippet 里注册事件/配方/命令被 KubeJS 静默拒绝 |
| 求值报 `invalid return` | 代码写成了顶层 `return ...`，改 IIFE |
| 1.20.1 dev 起不来 / `NoSuchMethodError` | 用了 SRG 或 reobf jar；forge dev 只认 `build/devlibs` 的 **named** jar |
| 需要「第二个人点一下」的流程 | 不必：`AutoJS.background(true)` 放出鼠标但世界照常 tick；客户端走真实 `Input`/`Screen` 路径模拟 |

## 7. 2026-10-24 复验读数（本机实测）

- `GET http://127.0.0.1:25585/health` → **200**，body `ok`。
- `initialize` → `protocolVersion 2025-06-18`，`serverInfo { name: "autojs", title: "AutoJS", version: "1.0.0" }`。
- `tools/list` → **4 个工具**：`status` / `eval_js` / `reload_scripts` / `kubejs_log`。
- `tools/call status` → `sides`：server（`server running with 1 player(s) online, scripts loaded`）+ client（`client running, world loaded, scripts loaded`）；`limits`：`evalTimeoutMs 5000` / `maxResultChars 100000` / `tokenRequired false` / `logEval true`。
- 技能：`~/.dsh/skills/autojs/SKILL.md` 安装后 `skill("autojs")` 返回 16,775 字符正文（工具裁掉首尾空行，源文件 17,745 字符）。
- 配置：去重后 `mcp-autojs` 计数 = 1、`mcp-code-review` 计数 = 1、无 tab、238 行；备份 `cordis.patch.yml.bak-20261024-autojs`。
- **实际求值（两侧都通）**：server `eval_js` → `ok:true / type:"string"`、`value` = `{"players":1,"player":"Dev","ticks":15070,"lingling":true}`；client `eval_js` → `ok:true`、`value` = `{"fps":118,"in_world":true}`。`diagnostics` 里各有一条 `Loaded Java class '...'`（证明 `Java.loadClass` 生效）。
- `kubejs_log`（server, limit 3）→ `totalLines 261 / errorCount 0 / warningCount 2 / logFile logs/kubejs/server.log`。

## 8. 边界

- 本流程只覆盖**实机运行时求值**；实机本身仍需用户明确下达（见 `docs/agent/AGENTS-dsh.md` 硬规则第 5 条）。
- P1 两线（`neoforge-26.1.2` / `fabric-1.20.1`）的 AutoJS 通道在 1.5.0 正式版前不验证。
- 端点默认 `unrestricted=true` ⇒ 脚本够得到 `Runtime`/`ProcessBuilder`/游戏目录外路径；这也是它只绑 loopback 的原因，**不要** bind 到 `0.0.0.0`。
