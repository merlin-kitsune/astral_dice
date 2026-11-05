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
| `inject_command` | `command` `no_esc` `via` `side` | 可用 | 原版命令注入；**默认**走 AutoJS 客户端 `sendCommand`（玩家身份，`@s` 有效、不占键鼠）；`via: "native"` 才落键鼠注入器。详见 §4.1 |
| `inject_key` | `key` `hold_ms` `no_esc` `via` `side` | 可用 | 键位须与产品绑定一致（绑定漂移会让用例假失败）；**默认**走 AutoJS 客户端动作，`via: "native"` 才落键鼠注入器。详见 §4.2 |
| `inject_mouse` | `button` `shift` `hold_ms` `no_esc` `via` `side` | 可用 | 有界面 ⇒ 投给 `Screen#mouseClicked`（窗口中心 GUI 坐标）；无界面 ⇒ 左键 = 攻击、右键 = 世界右键。详见 §4.2 |
| `screenshot` | `tag` `via` `timeout_ms` | 可用 | AutoJS 客户端 `Screenshot.grab`（异步落盘，轮询出图后按 `<tag>_<run_id>.png` 登记，进 `.mt_shots.json`）。**`vision` 断言仍恒 BLOCKED**，缺的是语义判定通道，不是截图本身 |
| `jar_stats` | `save_as` | 可用 | 产物 jar 统计；jar 缺失记 BLOCKED |
| `wait` | `ms` | 退役（仍可跑） | 固定睡眠；`audit` 计入 `fixed_wait_steps`，新用例改用 `await_state` |
| `kubejs_reload` | — | 废除 | `channel=autojs` 下出现即 `validate` 报错 |

### 4.1 命令通道（2026-11-05 Step 1：AutoJS 客户端优先）

```
mt_channel.plan(): [ send_command(client) ] + [ autojs_command(server console), perform_prefixed(server console) ]
```

- **默认 client-first**：命令以玩家身份执行，因此用例里的 `@s` 与玩家上下文全部成立（旧键鼠通道时代全仓有 667 处 `@s` / 225 个用例）。客户端一级走 `Client.getConnection().sendCommand(...)`（`ServerboundChatCommand` 包体**不带前导 `/`**，`mt_channel.py` 已归一化；用例里写不写 `/` 都行）。
- **server-first 仅作诊断**：`MT_CHANNEL_ORDER=server-first|server|console` 把 console 两级提到前面。console 源没有玩家，`@s` 会静默无效（`effect clear @s` 不生效），**此时必须写 `@a`**。
- **`via` 字段**（步级）：`autojs`（默认，等同不写）| `native`（落 `scripts/test/mt_inject.py` 键鼠注入）。`MT_INJECT_MODE=native` 是整轮环境级同义开关（优先级：`via` > `MT_INJECT_MODE` > `autojs`）。
- **native 回退面**：AutoJS 通道 `DELEGATED`（缺 jar / 三级全败）时自动回退键鼠注入并记 `MT_CASE_WARN`；`endpoint=unavailable` 判 BLOCKED（不是 FAIL）。native 侧会自动补前导 `/`（`mt_case.py:_native_command`）：**少了 `/` 会被当成聊天内容发送，而 `mt_inject.py` 仍打 `MT_INJECT_CMD` 并返回 0**（2026-11-05 实测的静默假绿）。
- 通道回归用例：`STEP1-SMOKE-1.20.1.json`（客户端优先 + `@s` 判据）、`STEP1-VIA-NATIVE-1.20.1.json`（`via=native` 回退面）。

### 4.2 输入与截图（2026-11-05 Step 2：AutoJS 客户端动作，不占键鼠）

`inject_key` / `inject_mouse` / `screenshot` 三者与用例 `channel` 无关，统一按
`via` 字段 > `MT_INJECT_MODE` > `autojs` 的优先级选通道。

**键位翻译表**（`mt_case.py` 的 `AUTOJS_KEY_JS`，未列出的键一律回退 native 并留 `MT_CASE_WARN`）：

| key | 实现 | 语义 |
|---|---|---|
| `e` | `KeyMapping.click(options.keyInventory)`（先关掉残留界面） | 开背包 / 创造栏 |
| `escape` | 有界面 ⇒ `keyPressed(256,…)` → `onClose()` → `setScreen(null)` 逐级回落；无界面 ⇒ `pauseGame(true)` | 关屏 / 暂停并存档（26.x 无 `keyPressed(int,int,int)`，必须靠回落链） |
| `f1` | `options.hideGui = !options.hideGui` | 切 HUD（该键没有 KeyMapping） |
| `1`..`9` | `KeyMapping.click(options.keyHotbarSlots[i])` | 切快捷栏 |
| `rclick` | 服务端 `p.pick(4.5,0,false)`：命中方块 ⇒ `AutoJS.place(...)`；否则 `AutoJS.use()` | 真实右键（开箱 / 使用机器 / 吃射翻书） |
| `cancel` | 服务端发 `ClientboundContainerClosePacket` + `doCloseContainer()` | 关容器（等价 ESC 关容器） |

- **`cancel` 不能用 `closeContainer()`**：Rhino 的 Java 包装器解析不到它——反射 `getClass().getMethods()` 能列出 `closeContainer`，但调用必抛 `TypeError: Cannot find function closeContainer`（1.20.1 的 `ServerPlayer` 与 `LocalPlayer` 都如此，2026-11-05 实测）。故按 `ServerPlayer#closeContainer` 的两步原样复刻；本来没开容器时返回 `no-container`，按幂等空操作判 PASS 并留痕。
- **`e` 会先关掉残留界面**（`clicked-reset`）：上一条用例失败时留下的 Screen 否则会让下一条的首个 `e` 读到 `screen-open` 假失败。用例另外可从 `run()` 起点的 `_prepare_ui()` 拿到同一次复位（`keep_initial_screen: true` 可关）。
- **空手对空气右键不是失败**：原版 `useItem` 返回 `FAIL`（`consumesAction=false`），但 `RightClickItem` 已派发。此时返回 `use-noeffect` 记 PASS + `MT_CASE_WARN`；`place-miss` / `use-miss` / `no-player` / `no-screen` / `screen-open` 仍一律判 ERROR（堵假绿）。
- **必须保留真输入的用例**（AutoJS 无等价动作或判据依赖真实按键路径）：`WALLET-VISION-26.1.2` 的 `k`（iris 光影开关，自动回退 native）、`CC-ESC-1.21.1` 的 ESC（显式 `via: native`）、`TARGETSEL-PROMPT-1.21.1` 的右键（显式 `via: native`，需右键+潜行真实路径）。
- **截图**：`Screenshot.grab` 是异步落盘（`RenderSystem.recordRenderCall`），python 侧轮询 `<shot_dir>` 新 png ≤3s 后重命名为 `<tag>_<run_id>.png` 并写 `.mt_shots.json`（后续断言/报告按 tag 取图）。抓不到即 BLOCKED，不冒充 PASS。

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

