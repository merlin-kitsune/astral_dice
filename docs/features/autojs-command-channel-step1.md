# AutoJS 命令通道 Step 1（客户端优先）— 落地与实测

> 日期：2026-11-05 22:0x–22:2x（Windows 原生，Forge 47.1.47，1.20.1 线）
> 触发：用例批次里 `inject_command` 走键鼠注入静默失效（命令没送达但注入器报 PASS），用户裁决分三步改造，本文件只覆盖 **Step 1 = 命令通道改 AutoJS 客户端优先**。

## 0. 结论（一页）

- 已落地：`inject_command` **默认**走 AutoJS 客户端 `Client.getConnection().sendCommand(...)`，命令以**玩家身份**执行 ⇒ 全仓 667 处 `@s` / 225 个用例的语义保持不变；键鼠注入降为回退面（`via: "native"` 或 `MT_INJECT_MODE=native`）。
- 实测：命令通道与键鼠/窗口前台**无关**（窗口最小化、前台是别的程序时命令照样生效）。
- 顺带挖出**两个静默假绿**（都报 PASS 但游戏没收到命令），已修：客户端通道带前导 `/`；native 通道缺前导 `/`。
- 之前因通道失效而 FAIL/BLOCKED 的 4 条 1.20.1 用例，修复后 3 条 PASS（`FORGE-TAG` / `CMDSURF` / `DST-P0`），`DST-NESTED` 卡在一条**探针数值**断言（见 §5），与通道无关。

## 1. 改动面

| 文件 | 改动 |
|---|---|
| `scripts/test/lib/mt_channel.py` | `plan()` 默认 `[send_command(client)] + [autojs_command, perform_prefixed]`；新增 `chain_order()`（读 `MT_CHANNEL_ORDER`）；`Injector.command(..., order=)`；客户端一级**剥掉前导 `/`** |
| `scripts/test/lib/mt_case.py` | `PRIMITIVES["inject_command"]` 字段扩为 `command/no_esc/via/side`；新增 `_inject_mode()`（步级 `via` > `MT_INJECT_MODE` > `autojs`）与 `_native_command()`（**自动补 `/`**）；`_do_inject_command` 改为 AutoJS 优先 + DELEGATED 回退键鼠 |
| `scripts/test/TESTING-SPEC.md` | §4 原语表 `inject_command` 行改写；新增 §4.1「命令通道」 |
| `scripts/test/cases/STEP1-SMOKE-1.20.1.json` | 新增：通道回归（客户端优先 + `@s` 判据） |
| `scripts/test/cases/STEP1-VIA-NATIVE-1.20.1.json` | 新增：回退面回归（`via: native`） |

## 2. 通道设计

```
mt_channel.plan(cmd, side="server"):
  [ ("send_command",     "client", Client.getConnection().sendCommand(L)) ]
+ [ ("autojs_command",   "server", AutoJS.command(L)) ]
+ [ ("perform_prefixed", "server", commands.performPrefixedCommand(console, L)) ]
```

- **默认 client-first**：`send_command` 走 `ServerboundChatCommand`（包体不带 `/`），身份是玩家 ⇒ `@s` 有效、不需要窗口前台、不占键鼠。
- **server-first 只作诊断**：`MT_CHANNEL_ORDER=server-first`。console 源没有玩家，`@s` 静默无效（实测 `effect clear @s` 不生效），必须写 `@a`。
- **回退面**：`via: "native"` / `MT_INJECT_MODE=native` ⇒ `mt_inject.py`（SendInput 键鼠）。AutoJS 三级全败（DELEGATED）时也自动回退并记 `MT_CASE_WARN`；`endpoint=unavailable` 判 BLOCKED。

## 3. 两个静默假绿（本次最值钱的发现）

1. **客户端通道带 `/`**：`Client.getConnection().sendCommand("/astraldst snap D9")` ⇒ 服务端回 `未知或不完整的命令`；`sendCommand("astraldst snap D8")` ⇒ `AP_D8_SNAP`。用例里的命令普遍带 `/`（旧键鼠通道要求），故 `mt_channel.plan()` 内归一化。
2. **native 通道缺 `/`**：`mt_inject.py cmd --command 'effect give @s ...'`（无 `/`）把文本当**聊天内容**发出，却照样打 `MT_INJECT_CMD` 并 `return 0`。`_native_command()` 现自动补 `/`。实测：补 `/` 后 `glow=true`，不补则 `glow=false` 而 rc=0。

> 旧批次里那些「注入器 PASS、断言全 hit=False」的读数，根因就是第 2 条（命令变成聊天）。

## 4. 实测证据（1.20.1 / Forge 47.1.47，`game_pid` 见 `run/1.20.1/win-run.pid`）

启动：`bash scripts/test/win/launch.sh --version 1.20.1 --enable-autojs --wait-world 600 --timeout 900`

```
0 start          : False
1 client-first give @s   via=send_command    -> True
2 server-first clear @s  via=autojs_command  -> True   (console 无 @s，clear 无效)
3 server-first clear @a  via=autojs_command  -> False  (@a 命中玩家)
4 client-first clear @s  via=send_command    -> False
```

窗口最小化（`ShowWindow(hwnd, SW_MINIMIZE)`，前台变成别的程序）后：`give @s -> send_command => True`、`clear @s -> send_command => False` ⇒ **不依赖前台/键鼠**。

用例：`STEP1-SMOKE-1.20.1` PASS、`STEP1-VIA-NATIVE-1.20.1` PASS。

## 5. 抽样与待 triage

抽样 6 条「只用 `inject_command`」的 1.20.1 用例：`STEP1-SMOKE` PASS / `STEP1-VIA-NATIVE` PASS / `CARD-NOCOST-CREATIVE` PASS；`BOT-2P`、`CC-FILTER`、`CHIP-ECON-ON` FAIL。
三条 FAIL 都**不是通道问题**（`MT_CHANNEL: … cmd_via=send_command`、探针有回显），而是探针/内容数值对不上：

| 用例 | 实际读数 | 断言期望 |
|---|---|---|
| `CHIP-ECON-ON-1.20.1` | `AP_EO_CRECIPES:chips=0:config=0:ex=-` | `chips=61:config=1`（探针取不到 registry/config） |
| `CC-FILTER-1.20.1` | `AP_CC2C` **无输出** | 若干 `phase=…` 读数（GUI 探针没被触发） |
| `BOT-2P-1.20.1` | `AP_CRE_CASTREAL:cast=1` 截断 | 全字段长模式（属性数值漂移） |
| `DST-NESTED-1.20.1` | `AP_ND_NDMEAS:C:n=16:median=-1.5:near5=4:tracked=0:marks=1:hasSign=0:eq=false` | `median=0:` |

`DST-NESTED` 的 C 组（卸掉立牌）疑似**探针自身污染**：B 组给玩家加过 12000 tick 的 `DICE_BLESSING`，C 组没清 ⇒ `near5=4`（4 对仍吃到 +5），median 被拉成 -1.5。修法候选：C 组先 `effect clear` / 移除 blessing 再测，或把断言改成「`near5` 显著小于 B 组」而不是 `median=0`。**未改动**，留待裁决。

## 6. 未做（Step 2 / Step 3）

- Step 2：`inject_key` 18 个用例 / 65 步、`inject_mouse` 3 个用例 / 4 步按类改 AutoJS（`e`/`esc`/`1`/`f1`/`rclick`/容器点击），`screenshot` 原语改客户端 `Screenshot.grab`；**保留 3–5 条真输入用例**防假绿。
- Step 3：全量回归 + 两通道判定差异逐条裁决；上面 4 条探针/内容 FAIL 归入内容侧 triage。
