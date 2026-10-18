# WSL + AutoJS 测试工具链（现行唯一口径）

> 生效：2026-10-18（用户裁决）｜施工线 `multi-dev-next`｜取代「Windows 原生 pwsh + 模拟键鼠/截图识别」旧流程。
> 本文件是**唯一入口说明**；旧 `TESTING-SPEC.md` / `TESTING-RULES-OVERVIEW.md` 中与本文件冲突的段落一律作废。

## 0. 一句话

**开发与构建在 WSL ext4 上做，实机测试用 `scripts/test/mt.sh` 驱动，游戏内动作与判据一律走 AutoJS 求值通道；不存在键鼠注入、窗口焦点、截图比对。**

## 1. 工作区

| 项 | 值 |
|---|---|
| 唯一施工副本 | `~/work/astral_dice_multiloader-next`（ext4，原生 `.git`，分支 `multi-dev-next`） |
| 源目录 | `/mnt/f/MCProject/astral_dice_multiloader-next`（收尾前**只读**） |
| 克隆来源 | `git clone -b multi-dev-next /mnt/f/MCProject/astral_dice_multiloader` |
| 首次准备 | `bash scripts/dev/wsl-bootstrap.sh`（幂等） |
| 收尾回写 | `bash scripts/dev/sync-back.sh`（默认 `--dry-run`） |
| 推送 | **只在 Windows 侧** `cmd.exe /c "... git push"`（代理 `127.0.0.1:7897`） |

为什么不在 `/mnt/f` 上跑：9p 的 stat 极慢（实测 `:classes` 冷跑 9m22s）。ext4 上同一构建 **32s**。

## 2. 测试台

```bash
scripts/test/mt.sh --version 1.21.1 --phase preflight          # 工具链 / 用例结构 / 纯度 / 端点
scripts/test/mt.sh --version 1.21.1 --phase build              # :neoforge-1.21.1:jar → run/1.21.1/mods
scripts/test/mt.sh --version 1.21.1 --phase env                # run 目录 / autojs.json / 世界检查
scripts/test/mt.sh --version 1.21.1 --phase launch             # 脱离式 runClient + AutoJS 闸门
scripts/test/mt.sh --version 1.21.1 --phase case --case <json> # 单条
scripts/test/mt.sh --version 1.21.1 --phase case --all         # 本线全量
scripts/test/mt.sh --version 1.21.1 --phase report             # 汇总
scripts/test/mt.sh --version 1.21.1 --phase stop --purge-saves # 收停 + 清存档
```

阶段实现：

| 文件 | 职责 |
|---|---|
| `scripts/test/mt.sh` | 阶段入口（preflight/build/env/launch/case/report/stop） |
| `scripts/test/lib/mt_common.sh` | 配置解析（`mt.conf`）、版本→目录/JDK 映射、日志原语 |
| `scripts/test/lib/mt_autojs.py` | AutoJS MCP 客户端（stdlib HTTP+SSE、JSON-RPC、会话重握手、错误归一） |
| `scripts/test/lib/mt_case.py` | 用例校验 + 执行 + 断言 + 分桶（PASS/FAIL/ERROR/BLOCKED/TIMEOUT） |
| `scripts/test/convert_cases_autojs.py` | legacy→AutoJS 机械化迁移器（保留可复算的映射表） |

## 3. 通道契约（AutoJS MCP）

端点 `http://127.0.0.1:25585/mcp`（游戏目录 `config/autojs.json`），工具面仅四个：
`status` / `eval_js{code,side,timeout_ms}` / `kubejs_log` / `reload_scripts`。

求值硬约束（实测，写用例前必读）：

1. `eval_js` **只求值最后一个表达式**，顶层 `return` 抛 `invalid return` ⇒ 代码必须是 IIFE。
2. 非标量返回值会被序列化成 `"[object Object]"` ⇒ 执行器 `mode=json` 自动包 `JSON.stringify((...))`。
3. 布尔必须 `mode=bool`（包 `!!(...)`）；`JSON.stringify(false)` 得到字符串 `"false"`，非空即真。
4. 端点没起来 = **BLOCKED**，不是 FAIL。
5. 探针类命令（`/astralprobe …`）**必须以玩家为命令源**且**不得** `withSuppressedOutput()`：探针用
   `source.sendFailure` 回显 → 客户端聊天 → `latest.log`。用控制台源或静默输出会让 `AP_*` 行直接消失
   （2026-10-18 实测，迁移器已按此生成）。

## 4. 用例 schema

```jsonc
{ "case_id":"…","version":"1.21.1","channel":"autojs",
  "steps":[
    {"op":"note","text":"…"},
    {"op":"mcp_status","save_as":"st"},
    {"op":"eval_js","side":"server","save_as":"s1","code":"(function(){…})()"},
    {"op":"await_state","side":"server","code":"…","timeout_ms":30000,"interval_ms":500}
  ],
  "asserts":[
    {"type":"state","from":"s1","expect":{"chips":{"gte":0}}},
    {"type":"log","source":"latest","pattern":"AP_…"},
    {"type":"absent","pattern":"AP_.*_EX:"},
    {"type":"crash"},{"type":"kubejs"},{"type":"mixin"}
  ] }
```

- `channel` 缺省 = `kubejs`；**AutoJS 用例必须显式写 `autojs`**。
- 废除原语（出现即校验 ERROR）：`inject_command` / `inject_key` / `inject_mouse` / `screenshot` / `kubejs_reload`。
- `wait{ms}` 仅迁移期兼容（执行器会打 deprecation 警告），新用例用 `await_state`。
- 断言窗口：`log/absent/kubejs/mixin` **只看本条用例开始后的日志字节**（避免上一轮/上一条污染）。
- `vision` 断言在 WSL 下记 **BLOCKED**（无截图语义通道），不静默通过。

## 5. 迁移映射（`convert_cases_autojs.py`）

| legacy | AutoJS | 说明 |
|---|---|---|
| `inject_command` | `eval_js(side=server)` | 同一命令串 → 玩家命令源 → `performPrefixedCommand` |
| `inject_key` | `eval_js(side=client)` | GLFW 键码 → `InputConstants.Type.KEYSYM` → `KeyMapping.click` |
| `inject_mouse` | `eval_js(side=client)` | `Screen.mouseClicked(屏幕中心, button)` |
| `screenshot` | 删除 | 像素判据不再作为驱动/证据 |
| `kubejs_reload` | 删除 | 原语已退役 |

`rclick` 特殊处理：有界面 → `Screen.mouseClicked(…,1)`；无界面 → `MultiPlayerGameMode.useItem(MAIN_HAND)`。

## 6. 现状与欠账（2026-10-18 实测）

- 用例：**231/231** 已 `channel=autojs`，legacy 驱动原语 **0**，schema 校验 0 错。
- 实机（WSL，1.21.1）：`MT_LAUNCH: OK` 87s；`AUTOJS-SMOKE` PASS；`LINGLING` 22 断言全 PASS；
  抽样迁移用例：`JADE-NOJADE` / `CHIP-LOGIC` / `HOSTILE-TARGETS` PASS，GUI 交互类（`CC-*`、`BOT-2P`）仍需逐条语义重写。
- 欠账：GUI 驱动类用例的「打开界面 → 选牌 → 点击」链路需要 client 侧等价求值（当前机械迁移只能覆盖到
  `KeyMapping`/`Screen.mouseClicked`，无法替代真实界面状态机）；26.1.2 / 1.20.1 线的 AutoJS 端到端未验证。
