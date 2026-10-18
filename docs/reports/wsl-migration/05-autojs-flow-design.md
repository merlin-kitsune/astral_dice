# WSL + AutoJS 实机自动化资产设计（替代模拟键鼠流程）

> 目标：把「Windows pwsh + 模拟键鼠/窗口焦点/截图识别」的实机测试，换成 **WSL 内跑客户端 + AutoJS MCP 求值驱动**。
> 输入事实：`scripts/test/lib/Mt.AutoJs.psm1`（现存 MCP 客户端）、`docs/features/autojs-test-harness-spec.md`、交接正本 §6-25 / §6-26、`scripts/test/mt.conf:27`。
> 状态：设计正本（施工见 §7 任务表）。

---

## 1. 目标形态（唯一通道）

```
mt.sh --version <线> --phase preflight   # 静态前置：jar 就位/端点配置/世界模板
mt.sh --version <线> --phase build       # ./gradlew :<线>:jar（不改部署到整合包）
mt.sh --version <线> --phase env         # 世界模板重建 + AutoJS jar 投放（SHA256 幂等）
mt.sh --version <线> --phase launch      # 分离式启动 → 世界就绪 → AutoJS 三道闸门
mt.sh --version <线> --phase case --case <用例>   # 用例执行（eval_js/await_state/assert）
mt.sh --version <线> --phase report      # 汇总 MT_CASE_RESULT / 标记抽取
mt.sh --version <线> --phase stop --purge-saves   # 收停 + 清存档 + 判据打印
```

**废除面（目标：用例侧计数 = 0）**：`inject_key`、`inject_mouse`、`screenshot`、`vision`、`mt_ime`、`mt_capture`、`Mt.Win32`、窗口焦点/`taskkill`/`Start-Process`。

---

## 2. 通道契约（python3 标准库实现 `scripts/test/lib/autojs.py`）

1. **探活三步**：TCP 连 `127.0.0.1:25585` → `initialize` → `tools/call status`；任一失败 = **端点未就绪**。
2. **HTTP**：`POST <endpoint>`，`Content-Type: application/json`、`Accept: application/json, text/event-stream`、有则带 `Mcp-Session-Id`。响应两形态：直接 JSON `result`，或 `text/event-stream` 取**最后一个** `data:` JSON-RPC 消息。
3. **工具面**：`eval_js`（脚本求值）、`status`（两端心跳）、`kubejs_log`（读 KubeJS 日志）、`reload_scripts`（热重载）。
4. **错误归一**：`result.isError` 或内容 `ok=false` ⇒ 统一 `{error, stack, diagnostics}`。**端点未起 = BLOCKED，不是 FAIL**（历史假失败主因）。
5. **side 语义**：改世界/权威数据 = `server`；界面/输入/HUD/FPS = `client`；不确定 `auto`（服务端优先）。`side=server` 的求值**必须**在世界就绪闸门之后。

### 2.1 求值语义硬约束（照抄实测坑，写代码前必读）
- `eval_js` **只求最后一个表达式**；顶层 `return` ⇒ `EvaluatorException: invalid return` ⇒ 片段必须是 **IIFE 表达式**。
- 非标量返回值被序列化成 `"[object Object]"` ⇒ 必须 `JSON.stringify((function(){…})())`（`-Mode Json` 已代包，用例里**不要**再自己 stringify）。
- 布尔必须 `!!(…)`；`-Mode Bool` 返回的是**字符串** `"true"/"false"` ⇒ 只能比字面量。

---

## 3. 原语替换映射

| legacy 原语 | 新形态 | 说明 |
|---|---|---|
| `inject_command`（聊天注入，4846 次） | `eval_js(side=server)` 直调 Java API | 容器屏开着时聊天命令会被吞（交接 §6-17）⇒ 这本身就是迁移的硬理由 |
| `inject_key`（65 次） | `eval_js(side=client)` 走 `KeyMapping`/`Screen` 回调 | 真按键路径仍要覆盖 `KeyMapping.click`，不许直接改状态冒充 |
| `inject_mouse`（4 次） | `eval_js(side=client)` → `Screen.mouseClicked(x,y,btn)` | 命中坐标必须按屏内相对口径（筹码屏 `STRIP_X/STRIP_Y` 偏移，交接 §6-16） |
| `screenshot` + `vision`（111 + 4） | ① 结构化读数（槽位/`screen` 类名/实体列表/FPS）；② 真需要像素时用**游戏内自截图**（AutoJS 侧发起 + 落盘 + 哈希比对） | 判据从「看图像」升级为「读状态」；像素判据只用于无法结构化的视觉项 |
| `wait {ms}`（4791 次） | `await_state`（轮询条件） | 固定睡眠是 flake 主源 |
| `kubejs_reload` | 删除（语义已退役） | spec §5 |
| 日志断言 `log/absent` | 降级为旁证 | 仅 `crash/mixin/kubejs/禁AI 四层` 保留为**唯一**判据 |

### 3.1 tick 级瞬态（保留 KubeJS 采样器）
服务端 2 秒级窗口（冻结/无敌帧/冷却/刚落地）用「AutoJS 发起 + KubeJS `ServerEvents.tick` 自持计数采样 + `console.info` 落 `latest.log` + `type=log` 断言」——与 `mc-testbench-tick-sampling` skill 一致；AutoJS 负责**触发与读回**，采样器负责**不丢帧**。

---

## 4. 用例 schema（`channel=autojs`）

```jsonc
{ "case_id": "…", "version": "1.21.1", "channel": "autojs",
  "steps": [
    {"op":"eval_js","side":"server","script":"…js","args":{…},"save_as":"s1","timeout_ms":20000},
    {"op":"await_state","side":"server","code":"…","until":"true","timeout_ms":10000,"interval_ms":250},
    {"op":"assert","type":"state","from":"s1","expect":{"mats":"4/4","regen":1}},
    {"op":"assert","type":"log","source":"latest","pattern":"CHIP_SHOP_OPEN_DENIED .+"}   // 仅产品 LOGGER 行
  ]}
```

硬规则：① 缺省 `channel` = `kubejs` ⇒ 迁移**必须**显式写 `autojs`；② `assert.type=eval` 必须给 `from` 或内联 `code`，`expect` 必填；③ `{ok:true}` 这类形式判据**不算**迁移完成；④ 封闭原语表（未知 `op` 立刻 ERROR）。

---

## 5. 失败取证与分桶

| 现象 | 归类 | 处置 |
|---|---|---|
| 端点连不上 / 世界未就绪 | **BLOCKED** | 查 launch 闸门，不改产品代码 |
| 求值返回与 `expect` 不符 | **FAIL** | 产品缺陷或判据写错，需人工判 |
| 用例结构非法 | **ERROR(2)** | `mt_case validate` |
| 进程崩溃 / mixin 注入失败 | **CRASH**（日志通道） | 读 `crash-reports/` + `latest.log` |

失败现场必留：`latest.log` 尾部 200 行、AutoJS `status` 原文、需求值的 `JSON.stringify` 原文。

---

## 6. 验收（本设计落地的判据）

1. `AUTOJS-SMOKE-1.21.1` 在 **WSL 链路** PASS（贴 `MT_CASE_RESULT` + eval 原文）。
2. 新增 3–5 条 M0 代表用例 PASS，且每条至少 1 条 `eval/state` 明确判据。
3. 用例侧 `inject_*/screenshot/vision` 计数 = 0（`grep` 原文）。
4. `stop --purge-saves` ⇒ `MT_STOP: OK` + `MT_CLEANUP_SAVES: PURGED <n>`。
5. 全流程无 pwsh 依赖（`grep -rn 'pwsh' scripts/test` 只在「读旧脚本分析」注释里）。

---

## 7. 施工任务（对应 00-req-analysis §5 的 W5–W7）

| 任务 | 交付 | 判据 |
|---|---|---|
| W5 | `mt.sh` + `lib/{paths,proc,phase}.sh` | `--phase preflight` 可跑空转并打印阶段表 |
| W6 | `lib/{autojs,case,report}.py` | 单测：SSE/JSON 双形态解析、`expect` 嵌套比较、退出码分桶 |
| W7 | M0 用例迁移 + WSL 实机 | §6 五条 |
