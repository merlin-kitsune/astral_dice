# 测试用例规范（Case Schema Spec）— bash + python 口径

> 本文件 2026-11-05 重置。原 4555 行版本里 §8「回归套件清单（1.2.0）」、§13「26.1.2 一致性测试计划」与 60 余条「附录 A 续 N」工程批次记录已删除（工程记录恒以 `CHANGELOG.md` / `CHANGELOG_ZH.md` 为准）。

## 0. 权威关系（改之前先看这一节）

三层，冲突时以上层为准、以下层为读数：

| 层 | 载体 | 管什么 |
|---|---|---|
| 规则 | `docs/agent/AGENTS-full.md`〈自动化测试流程（Automated Testing）〉章 | 阶段划分、测试顺序与门控、清场/收停纪律、全局测试环境要求、超时预算、禁 AI 审计。**本文件不复述这些规则。** |
| 截面 | 本文件 | 用例 schema、原语与断言封闭集、validate/audit、机器行与退出码、静态守门清单 |
| 实现 | `scripts/test/win/*.sh`、`scripts/test/lib/*.py`、`scripts/test/mt_*.py` | 真实行为。代码与本文不一致 = 缺陷，改代码或改本文，不许两边各说一套 |

机器行（`MT_*` / `LAUNCH:` / `WIN_STOP:` 等）是断言与报告的**唯一**锚点：新增行为必须先定机器行，再写用例。

## 1. 入口

```bash
# 编排（唯一入口）
bash scripts/test/win/mt.sh --version <线> --phase <env|build|launch|cases|report|stop> [--channel autojs|native]

# 单条用例（已有 run 目录与在跑的游戏时）
python scripts/test/lib/mt_case.py run --case <CASE_ID> --version <线> --run-dir run/<线>

# 用例合法性（改完用例先跑这个，不要等到实机）
python scripts/test/lib/mt_case.py validate --cases-dir scripts/test/cases [--version <线>] [--require-judgment]

# 判据覆盖率审计
python scripts/test/lib/mt_case.py audit --cases-dir scripts/test/cases [--version <线>] [--list]
```

- 线名以 `scripts/test/lib/mt_paths.py` 的版本表为准（当前 `neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`）。
- 编排层 `mt.sh` **只接受** `--channel autojs|native`；传 `kubejs` 直接 `MT_ERROR` + exit 2。`kubejs` 只是**用例 JSON 内的 channel 取值**，由 `cases` 阶段透传给 `mt_case.py`。
- 平台分工与开关见 `scripts/test/win/README.md`、`scripts/test/linux/README.md`、`scripts/test/fabric/README.md`。

## 2. 用例文件

- 位置：`scripts/test/cases/**.json`（允许子目录，如 `blocked-26.1.2/`）。
- 命名：`<CASE_ID>-<version>.json`。`validate --version <线>` 只收 `-<version>.json` 后缀；`.mt_` 前缀与 `.mt_results/` 一律跳过。
- 编码 UTF-8（无 BOM）；`case_id` 与文件名前缀一致最省事。

## 3. JSON 骨架

```json
{
  "case_id": "AUTOJS-SMOKE-1.21.1",
  "title": "一句话标题（可带日期）",
  "version": "1.21.1",
  "channel": "autojs",
  "target": { "type": "feature", "ref": "target-select-actionbar-prompts" },
  "steps": [
    { "op": "inject_command", "command": "/gamemode creative", "no_esc": true },
    { "op": "eval_js", "side": "server", "code": "(function(){ return AutoJS.players().length; })()",
      "save_as": "players", "expect": { "min": 1 } },
    { "op": "assert", "type": "crash" }
  ],
  "asserts": [
    { "type": "log", "source": "latest", "scope": "case", "pattern": "AP_[A-Z]+:evt=ok" },
    { "type": "kubejs" }
  ]
}
```

必填：`case_id`、`title`、`version`、`steps`（数组）。

可选：

| 字段 | 取值 | 说明 |
|---|---|---|
| `channel` | `kubejs`（缺省）/ `autojs` | `autojs` 下废除原语（见 §4）直接报错；`validate --require-judgment` 只对 `autojs` 要求明确判据 |
| `target` | `{type, ref}` | 只是登记，不参与执行 |
| `on_fail` | 字符串 | **新用例不得写 `keep_game_running`**：会落 `.mt_keep_alive` 堵后续运行（规则层硬纪律） |
| `setup` / `teardown` | 步骤数组 | 清场、复位等；清场已不在编排层（`win/**` 无 preclean），由用例自己承担 |
| `asserts` | 断言数组 | 与 `steps` 里 `op: assert` 同语义，在这里写更省事 |

## 4. 原语封闭集（`scripts/test/lib/mt_case.py:57-72`）

表外原语 / 表外字段一律 `validate` 报错，**不许**自行扩字段。

| op | 允许字段 | 状态 | 说明 |
|---|---|---|---|
| `note` | `text` | 可用 | 只写记录，不执行 |
| `mcp_status` | `timeout_ms` `save_as` | 可用 | 通道探活；不算判据 |
| `eval_js` | `side` `code` `script` `args` `save_as` `timeout_ms` `mode` `expect` | 可用 | `side` ∈ `auto`/`server`/`client`；`code` **不得以顶层 `return ` 开头**（改 IIFE） |
| `await_state` | `side` `code` `script` `args` `timeout_ms` `interval_ms` | 可用 | 轮询到条件成立或超时 |
| `assert` | `type` `pattern` `source` `image` `question` `from` `code` `side` `expect` | 可用 | 见 §5 |
| `inject_command` | `command` `no_esc` | 可用 | 原版命令注入；`@s` 会解析成 console，**选择器一律 `@a`** |
| `inject_key` | `key` `hold_ms` `no_esc` | 可用 | 键位须与产品绑定一致（绑定漂移会让用例假失败） |
| `inject_mouse` | `button` `shift` `hold_ms` `no_esc` | 可用 | |
| `screenshot` | `tag` `mode` `crop` | 表内但**恒 BLOCKED** | python 侧无截图通道（`mt_case.py:430-433` `raise Blocked`）；视觉取证改用客户端探针自截图 + debug 通道 |
| `jar_stats` | `save_as` | 可用 | 产物 jar 统计；jar 缺失记 BLOCKED |
| `wait` | `ms` | 退役（仍可跑） | 固定睡眠；`audit` 计入 `fixed_wait_steps`，新用例改用 `await_state` |
| `kubejs_reload` | — | 废除 | `channel=autojs` 下出现即 `validate` 报错 |

## 5. 断言（`ASSERT_TYPES`，共 11 种）

| type | 必填 | 判什么 |
|---|---|---|
| `log` | `pattern`（正则） | 日志窗口内命中；`source` ∈ `latest`/`kubejs`/`client` 等 |
| `absent` | `pattern` | 窗口内**不**出现；启动期文本必须显式 `scope: whole` |
| `crash` | — | 本用例窗口内无新增 crash-report；`crash-reports/` 不存在 ⇒ INCONCLUSIVE（不冒充 PASS） |
| `kubejs` | — | KubeJS 脚本无加载/运行错误 |
| `mixin` | — | 无 `Mixin apply failed` / `InvalidInjectionException`；固定读 launch 窗口 |
| `vision` | `image` `question` | 语义判图；python 侧无等价物 ⇒ **恒 BLOCKED(11)**，不计 PASS 也不计 FAIL |
| `eval` | `expect`（另需 `code`） | 从运行中 JVM 取值并与 `expect` 比对 |
| `state` | `from` `expect` | 与先前 `save_as` 的状态快照比对 |
| `jar` | 判据字段 | 产物 jar 统计判据：`models_min` `recipes_min` `trinkets_min` `entries_min` `signs_min` `bountiful_max` `embed_max` `embed_lib` |
| `bytecode` | `pattern` | 导出字节码中命中（编译期证据，不需要开游戏） |
| `bytecode_absent` | `pattern` | 导出字节码中不出现 |

窗口：`scope` ∈ `case`（默认，只读本用例起点之后的日志）/ `launch`（启动阶段冻结的基线）/ `whole`。**绝不静默把 launch 断言降级成 case 断言**——那会凭空制造假 FAIL。

## 6. `expect` 谓词

字面量相等，或下列谓词之一（可嵌套到对象字段）：`min` `max` `gte` `lte` `regex` `set` `contains` `len` `not` `type`。

```json
{ "expect": { "hp": { "gte": 1, "lte": 20 }, "state": { "regex": "RUNNING|STOPPED" }, "names": { "len": 31 } } }
```

## 7. 判据纪律（写用例时逐条自查）

1. `channel=autojs` 的用例**至少 1 条结构化判据**：带 `expect` 的 `eval_js`/`await_state`，或结构化 `assert`。`vision` 不算判据。
2. 判据必须来自**运行中的 JVM**（服务端状态、客户端探针行），不许把「日志里有这行」当成功能正确。
3. 断言锚点写机器行正则（`AP_*:` / `MT_*` / `LAUNCH:` / `WIN_*`），不要锚自然语言提示文案。
4. 瞬态（< 2 s）状态用 `await_state` 或客户端探针自采样，不要靠 `screenshot`。
5. 一条用例只判一件事；多条件拆多条，`MT_CASE_RESULT` 才能定位。

## 8. 机器行与退出码

| 场景 | 机器行 |
|---|---|
| 校验 | 逐条 `MT_CASE_VALIDATE: ERROR <path>: …`；收尾 `MT_CASE_VALIDATE: files=N errors=M` + `MT_JUDGMENT: with_judgment=N without=M` |
| 审计 | 逐条 `MT_JUDGMENT_ITEM: JUDGED|NO-JUDGMENT|BROKEN <case_id> — evals=.. waits=.. vision=..`；收尾 `MT_JUDGMENT: files=.. with_judgment=.. without=.. vision_files=.. fixed_wait_steps=..` |
| 执行 | `MT_CASE_BEGIN: <id>` / `MT_CASE_RESULT: <id> = PASS|FAIL|ERROR|BLOCKED|TIMEOUT` / `MT_CASE_DETAIL:` / `MT_CASE_WARN:` / `MT_CASE_CHANNEL:` / `MT_CHANNEL: delegated_count=N` / `MT_CASE_SUMMARY: total=.. pass=.. nonpass=..` |
| 断言 | `MT_ASSERT_LOG` / `MT_ASSERT_ABSENT` / `MT_ASSERT_MIXIN:` / `MT_ASSERT_CRASH:` / `MT_ASSERT_INCONCLUSIVE:` |

退出码：`PASS 0` / `FAIL 1` / `ERROR 2` / `BLOCKED 11` / `TIMEOUT 12`。升级序：`ERROR > TIMEOUT > FAIL > BLOCKED`（BLOCKED 不覆盖已判定的 FAIL）。

整轮汇总另由 `scripts/test/mt_report.py` 出 `scripts/test/reports/<run_id>/<version>/report.md` 与 `SUMMARY.md`，机器行 `MT_REPORT: OK|ERROR`。

## 9. 静态守门（不需要开游戏，改完必跑）

| 命令 | 判据 |
|---|---|
| `python scripts/test/lib/mt_case.py validate --cases-dir scripts/test/cases` | `errors=0`（rc 0） |
| `python tools/check_lang_sync.py` | 各线 lang 各自 `[OK]` |
| `python tools/check_changelog_parity.py` | `CHANGELOG_PARITY: OK` |
| `python tools/verify_sign_display_names.py` | 立牌英日命名与权威表一致 |
| `python tools/verify_effect_icons.py` | 效果图标各线齐备 |
| `python tools/check_mod_sources.py` | 模组源码结构一致 |
| `python docs/agent/verify-split.py` | `AGENTS-dsh.md` 红线区与 `AGENTS-full.md` 逐字一致 |
| `python tools/gen_agents_toc.py --check` | `TOC: OK` |
| `python scripts/verify/verify_*.py` | 各专题守门（内容库、事件注册、Bountiful 池、配方唯一性等） |

## 10. 已知坑（2026-11-05 仍然成立的实测结论）

- **控制台编码**：Windows 中文控制台是 GBK，直接打印 `⇒` / `—` 会 `UnicodeEncodeError` 崩掉整条用例（`mt_case.safe_print` 已兜底）。脚本一律带 `PYTHONIOENCODING=utf-8`。
- **落盘时序**：用例首条断言可能在本轮日志落盘前读，出现假 FAIL，隔几秒单跑同一断言即 PASS（2026-11-02 `HOSTILE-TARGETS-1.20.1` 实测）。
- **crash 证据**：`run/<线>/crash-reports/` 不存在时 crash 断言记 INCONCLUSIVE，只有存在且新增才判 FAIL。
- **窗口不可判**：日志窗口起点越过当前文件长度 ⇒ `MT_ASSERT_INCONCLUSIVE`，`log` 类型不计 FAIL。
- **AutoJS 端点独占**：`25585` 同一时刻只允许一条线占用；端口被占时**不得 launch**，否则注入打进别人那台游戏。
- **命令选择器**：`AutoJS.command()` 走 `createCommandSourceStack()`，`@s` 解析成 console；一律用 `@a`。
- **fabric 通道**：走 RCON，缺省 `127.0.0.1:25575` / `astralft`（`scripts/test/win/lib/fabric.sh:34-35`），但优先读 `server.properties` 的 `rcon.port` / `rcon.password`。
- **收停只有一条路**：`bash scripts/test/win/mt.sh --version <线> --phase stop`。`stop.sh --force` 在 bash 侧是 no-op；`trap` 只退编排层，不动游戏进程。

## 11. 覆盖缺口（如实标注，别当已支持）

1. **视觉取证整体缺失**：`vision` 断言与 `screenshot` 原语在 python 侧恒 BLOCKED；需要画面证据时只能靠客户端探针自截图 + debug 通道路径判据。
2. **无全局超时与全局清理钩子**：`mt.sh:228` 明记 P1 简化点，`--run-timeout` / `--cleanup-on-exit` 一律 `MT_ERROR` + exit 2。
3. **`.mt_keep_alive` 在 bash 链路上无人消费**：只有 `mt_preflight.py` 与 `mt_cleanup.py` 读它，`win/**` 不读。
4. **历史用例含退役原语**：`wait` / `vision` / `on_fail: keep_game_running` 在既有用例里仍大量存在，`audit` 会如实统计；迁移按需逐条进行，不做一次性改写。

