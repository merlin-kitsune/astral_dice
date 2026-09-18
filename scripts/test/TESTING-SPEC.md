# astral_dice 自动化测试规范（TESTING SPEC）

> 本文件是 `astral_dice_multiloader` 仓库**唯一**的测试流程落地规范，替代已废弃的
> `scripts/test/FLOW_1.20.1_functional.md`（2026-09-12 删除，历史版本可 `git show` 取回）。
> 工作区总规范见 `AGENTS.md`「自动化测试流程」一节；两者冲突时**以本文件为准**（本文件随工具链一起入库、可被 review）。
> 适用分支：**任意分支** —— 2026-09-17 用户裁决「**移除二重验证白名单，允许该分支执行**」后，分支检查已由强断言改为**信息性回显 + WARN**（`multi-dev-next` 等开发分支可直接运行本流程）。发布线 `multi-1.20.1-1.21.1` 仅作提示基准：非发布线分支会在阶段 P 打印 WARN，并在报告「分支」一栏**如实标注运行时的实际分支名**。

---

## 1. 目标与适用范围

对**两个子项目**（`neoforge-1.21.1` 优先、`forge-1.20.1` 随后）做**真实游戏内**的自动化验证：

- 客户端渲染 / 输入类缺陷（跳字、角标、雷击渲染等）；
- 服务端权威数值与状态机（冷却、层数递减、资源消耗等）；
- 新增内容与用户指定内容的回归。

不在本规范内：纯静态校验（见 §9 守门脚本）、性能压测、多人联机双端测试（当前**无覆盖**，见 §11）。

---

## 2. 机器与环境前提

| 项 | 要求 |
|---|---|
| 分支 | **任意分支**（`multi-dev-next` 等开发分支同样可运行）。阶段 P 只**回显实际分支名 + WARN**，不拦停（2026-09-17 用户裁决「移除二重验证白名单，允许该分支执行」）；发布线 `multi-1.20.1-1.21.1` 仅作提示 |
| 脚本语言 | **纯 PowerShell 7**（`pwsh`）。2026-09-12 起 bash/python 版全部下架 |
| 游戏环境 | dev `runClient`（`run/<版本>/`，Mojmap 命名），模组经 `modImplementation` 引入 |
| 兼容栈 | 1.21.1：KubeJS / JEI / ModernFix /（可选）光影；1.20.1：KubeJS / JEI / ModernFix 经 build.gradle 注入 + 整合包 mods 复制 |
| 输入注入 | 需系统已安装「英语(美国)」键盘（KLID 00000409）：`mt_ime` 在注入前**按窗口线程**切换，不改系统默认 |
| 可选 MCP | `computer-control-mcp`（**唯一保留的 MCP**：窗口激活 / OCR / 截图 / 输入注入的降级通道），路径写在 `mt.conf`；mineflayer bot（minecraft-mcp-server）已于 2026-09-14 彻底移除 |
| 机器本地配置 | `scripts/test/mt.conf`（**不入库**）。缺失时回落到内置默认值；模板见 `mt.conf.example` |

**测试前清场（2026-09-15 起强制，工具链已自动执行）**：`mt_launch.ps1` 在「已进入世界」后自动连发两次 `/kill @e[type=!player,distance=..128]`（间隔 600ms），打印 `MT_PRECLEAN: OK — …`；`--no-preclean` 可跳过（仅限必须保留世界实体的特殊取证），跳过时打印 `MT_PRECLEAN: SKIPPED`。手工补做时的两种机制、理由与禁止事项见 `AGENTS.md`「测试前清场」；本条直接对应 §8.2-1（1.20.1 常驻蜘蛛污染 `self`）与 §10-18。⚠️ 1.21.1 的命令在 tick 末才生效，清场后要 `wait ≥500ms` 再摆靶；**禁止**在用例两次 read 之间清场。

**清场生效判据（2026-09-15 实机验证）**：客户端 locale 是**中文**，原版反馈为 `杀死了N个实体` / `未找到实体`（英文 locale 才是 `Killed N entities` / `No entity was found`）—— 判「清场真的执行了」就看这一行，别拿英文串去 grep。本次实测：1.20.1 清场 `杀死了94个实体`（该世界 128 格内当时**堆了 94 个非玩家实体** —— 这正是 `self`/`bolt_delta` 被污染的来源），1.21.1 两次分别为 `9` / `6`。两次注入中**可能只有一次落地**（§10-17 的注入丢失同族），所以双发是必要的冗余，不是装饰。

---

## 3. 唯一入口与阶段

```powershell
pwsh -NoProfile -File scripts/test/mt.ps1                      # 全流程（P→B→E→L→C→R，双版本 + 跨版本门控）
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1      # 全流程**只跑指定版本**（无跨版本门控）
pwsh -NoProfile -File scripts/test/mt.ps1 --phase <p> --version <v>
```

> **`--version` 语义（2026-09-15 B6 ⑤ 修正）**：全流程分支**同样尊重** `--version` ——
> 指定单版本时只跑该版本、**不做跨版本门控**（门控的前提是"两版本顺序执行"），`mt_report summary`
> 也只汇总实际执行过的版本。旧实现无条件跑两版本，`mt.ps1 --version 1.20.1` 会**先跑 1.21.1**，
> 1.21.1 一旦不通过就把 1.20.1 记成 `GATED` 而根本没跑 —— 与 `--version` 相反。

| 阶段 | 实现 | 职责 | 终态标记 |
|---|---|---|---|
| **P** 前置 | `mt_preflight.ps1` | 分支 / 输入法 / 遗留进程 / MCP 二进制 / 兼容栈 / 可写性；启动前兜底清理 | `MT_PREFLIGHT: OK/FAIL`（失败退出码 10） |
| **B** 构建 | `mt_build.ps1` | `gradlew :<子项目>:build`，60s 看门狗 + `BUILD SUCCESSFUL` 识别 + 产物 jar 校验 + 重试 | `MT_BUILD: OK/FAIL` |
| **E** 环境 | `mt_env.ps1` | `mods`（装兼容模组）/ `world`（重建测试世界，含原生 NBT 改写）/ `kill` | `MT_WORLD: OK/BLOCKED`（退出码 11） |
| **L** 启动 | `mt_launch.ps1` | 启动 `runClient`、轮询就绪日志、兼容栈信号；**进入世界后先跑 B6 ④ 的 OP / `dump` 前置闸门**（只读；前置不足 ⇒ `BLOCKED`，探针/dump 不可用 ⇒ `ERROR`） | `MT_LAUNCH: OK/FAIL`、`MT_preflight-op: BLOCKED/ERROR` |
| **C** 条目 | `mt_case.ps1` | 顺序执行 `cases/*.json`：注入命令/按键 → 等待 → 断言；每条结果**自动**写入报告状态 | 每条 `PASS/FAIL` |
| **R** 报告 | `mt_report.ps1` | 收集证据（日志 / 崩溃 / 截图）→ 单版本 `report.md` → 双版本/单版本 `SUMMARY.md` | `MT_REPORT: OK/FAIL`（未全绿退出码 1） |

**辅助脚本**：`mt_assert.ps1`（断言引擎）、`mt_inject.ps1`（键鼠注入）、`mt_ime.ps1`（输入法）、`mt_capture.ps1`（截图世代）、`mt_cleanup.ps1`（退出清理唯一实现）、`mt_stop.ps1`（薄封装）、`mt_gen_case.ps1`（条目生成器）。
**共享模块**：`lib/Mt.{Conf,Paths,Phase,Proc,Win32}.psm1`。
**开发辅助**：`scripts/devtools/Start-MtDetached.ps1`（脱离式执行）、`Test-MtSyntax.ps1`（脚本语法门）。迁移期的 `Compare-MtOutput.ps1` 已于 2026-09-15 删除——它比对的 python/bash 原件自 `92fbeaf`「工具链收敛为纯 pwsh」起已不存在。

> ⚠️ **长命后代阶段必须脱离执行**：`mt_build`（新起 Gradle 守护）、`mt_launch`、`mt_env world` 会留下 Gradle 守护 / 游戏客户端；非交互式调用方会按**整棵进程树**等待而卡死。统一用：
> ```powershell
> pwsh -File scripts/devtools/Start-MtDetached.ps1 -Script scripts/test/mt.ps1 -Out temp/xxx.log -Merge -Arg --phase launch --version 1.21.1
> ```

---

## 4. 测试顺序与门控

| 顺序 | 版本 | 子项目 | 执行条件 | 结论要求 |
|---|---|---|---|---|
| 1 | 1.21.1 | `neoforge-1.21.1` | 前置检查通过 | 独立 PASS / FAIL |
| 2 | 1.20.1 | `forge-1.20.1` | **1.21.1 判定 PASS**（门控） | 独立 PASS / FAIL |

- 1.21.1 未通过时，1.20.1 记 **`GATED`（未执行）**，报告显式标注为「未执行」而非「失败」。
- 汇总：`scripts/test/reports/<运行id>/SUMMARY.md`；明细：`<运行id>/<版本>/report.md`。
- 退出码：两版本均通过 `0`；任一失败或汇总未全通过 `1`。

---

## 5. 目录与产物

| 路径 | 内容 | 是否入库 |
|---|---|---|
| `scripts/test/cases/*.json` | 测试条目（回归套件） | ✅ 入库（`.mt_*` 状态文件除外，见 `.gitignore`） |
| `scripts/test/resources/kubejs/<版本>/server_scripts/` | **KubeJS 探针脚本**（服务端断言通道，见 §6） | ✅ 入库 |
| `scripts/test/resources/testworld-seed-1.20.1.zip` | 1.20.1 测试世界种子包 | ✅ 入库 |
| `scripts/test/reports/<运行id>/` | 报告 + 证据（日志 / 截图 / 崩溃） | ❌ 不入库（仅 `_template.md` 入库） |
| `scripts/test/cases/.mt_active_run` / `.mt_run_state.json` / `.mt_snapshot.json` / `.mt_keep_alive` | 运行态（粘性 run-id、结果状态、快照、失败取证标记） | ❌ 不入库 |
| `run/<版本>/**` | dev 游戏目录（世界 / 日志 / 截图 / mods） | ❌ 不入库 |

---

## 6. KubeJS 探针（服务端权威读数）

回归套件依赖探针提供的服务端读数（如 `AP_K1_AFTER`、`AP_F1_WINDOW`、`AP_P2_PEARL`）。

**安装（2026-09-16 起自动化，此前**只写在文档里、实际靠手工拷贝**）**：
- `pwsh -File scripts/test/mt_env.ps1 kubejs --version <版本>` —— 按**内容哈希**把
  `scripts/test/resources/kubejs/<版本>/**` 同步到 `run/<版本>/kubejs/**`（只碰 kubejs，不动世界/mods）；
- `mt_env.ps1 mods` 会**顺带**执行同一步；`mt.ps1 --phase launch` 也会在启动前自动执行，
  失败即 `MT_LAUNCH: BLOCKED`（返回 11）。
- ⚠️ **为什么必须自动化（实测事故根因）**：手工拷贝会静默漂移 —— 模板已更新、run 目录还是旧探针时，
  用例照样注入 `/astralprobe <新命令>`，而游戏侧**没有那条命令** ⇒ 所有断言读不到读数、
  每个断言都在等一个永不出现的标记。实测出现过「run 目录 218 KB / 模板 229.7 KB、哈希不一致」的状态。
- ⚠️ 探针**命令**的改动需**冷启动**（`--phase stop` → `--phase launch`）才生效；
  `/kubejs reload server-scripts` **不会**重绑已注册命令的 lambda。同步只保证**文件**就位，
  不保证**正在运行**的客户端已加载它。


| 探针 | 1.21.1 | 1.20.1 | 作用 |
|---|---|---|---|
| `astral_bugfix_probe.js` | ✅ | ✅ | 回归套件主探针：命令（`diag` / `equipslot` / `railguncd` / `railgunfriendly`·`railgunfriendlyread`·`railgunfriendlyend`（双版本）、`truedmg`/`railtruedmg`·`railtruedmgread`（双版本：真伤穿甲取证）、`fensplash`/`fensplashhit`/`fensplashread`（双版本：大当家溅射三段式——装备与摆靶、命中、读差值；判据=4.5 格内命中(旧 3 格打不到)、8 格外 0 伤害、重甲靶与无甲靶掉血相同(真伤)、溅射÷近战≈0.88 或被 5 点下限托住；靶子一律非亡灵、非苦力怕）+ `guidebook`（双版本：**只读**——《恋的规则书》首登唯一发放守卫 `given` + 背包内手册总本数，见 §8 专项说明）+ `glmcheck`（仅 1.20.1）+ `glovebase`（双版本：电击手套 3 格 AOE 的**伤害基准口径**——用注册进生产修饰器表的同路 `SpellDamageModifier` 读 `ctx.event` 的伤害值，同时给出护甲前/护甲后客观对照；判据见 §8 专项说明）+ 各用例专用命令）、状态读数 `AP_*` |
| `astral_dice_curios_check.js` | ✅ | ✅ | Curios 槽位 / 装备状态观测 |
| `astral_dice_target_select_check.js` | ✅ | ✅ | 待命等待器（占星师 / 秘密侦探 / 枪匠）观测 |
| `astral_dice_curio_watch.js` | ✅ | — | 立牌槽位变化观测（1.21.1 专用） |
| `astral_dice_heal_test.js` | ✅ | — | 治愈点读数（1.21.1 专用） |
| `astral_functional_test.js` | — | ✅ | 1.20.1 历史功能验证脚本（手工流程遗留，保留备用） |

> ⚠️ 两版本探针集合**并非完全镜像**（上表为实测现状）。改动探针后请同时核对
> `scripts/test/resources/kubejs/<版本>/server_scripts/`（入库副本）与 `run/<版本>/kubejs/server_scripts/`（运行副本）。

**两版本差异**（除下列行外逐字相同）：`opt.resolve().get()`（1.21.1）↔ `opt.get()`（1.20.1）；`ResourceLocation.parse(...)` ↔ `new ResourceLocation(...)`；`ModEffects.X` ↔ `ModEffects.X.get()`。

### 6.1 只读读数统一出口（2026-09-15 B6 ③）

| 命令 | 语义 |
|---|---|
| `/astralprobe dumpstate <tag>` | **只读**：转调产品侧 `/astralparty dump`（OP 级 2）。dump 把本模组自身状态按 `APDUMP\|<组>\|<键>=<值>` 同时写聊天栏与 `LOGGER.info`（必然进 `latest.log`） |
| `/astralprobe opprobe` | **只读**：打印 `AP_OP_PERM:has2=<0\|1>:level=<n>:src=<…>:dump=<…>`；`has2` 的判据与 `/astralparty` 完全相同（玩家命令源上的 `CommandSourceStack#hasPermission(2)`），由 `mt_launch` 作为 cases 阶段的前置闸门 |

6 个纯只读命令（`komachiread` / `nancystate` / `airbagread` / `railgunfriendlyread` / `railtruedmgread` / `fensplashread`）**全部**接上 `dumpState`。

**红线（必须遵守）**：

1. `dump` **只用于读数**，不得替代被测动作（真实出牌 / `registerPlay` / `tick` / 各相位真实生产入口照旧）；
2. 断言**只能锚定原始值组** `LOCKRAW` / `SIGN` / `EFFECTS` / `PENDING` —— `LOCKDERIVED` 与 `SIGN|is_sign_active_locked` 行尾带 `|assert=forbidden`，它们是判定入口，断言它们等于拿被测功能验证自身；
3. 命令缺失/失败**必须**显式失败：`dumpState` 在返回值不是 `rc=1`（1.20.1）或取不到读数时另打 `AP_<tag>_DUMP_ERR:`，用例对该标记做 `absent` 断言 ⇒ 落 FAIL；`mt_launch` 另有第二道闸门（`latest.log` 里找不到 `APDUMP|LOCKRAW|` ⇒ `ERROR`）。**绝不静默降级**；
4. **不得**用通用 `APDUMP|` 行顶替 tag 唯一的既有断言：断言窗口是「自 launch 快照起的增量」、**跨用例共享**（见 §10-14），不带 tag 的标记可被前序用例满足 ⇒ 那等于弱化断言。`dumpState` 一律**追加**而非替换。

> ⚠️ `/kubejs reload server-scripts` **不会重绑已注册命令的 lambda**。改动探针后必须**冷启动**（stop → launch）才会生效；`reload` 只用于确认脚本无语法错误。

---

## 7. 条目 schema 与断言类型

```json
{
  "case_id": "KOMACHI-EXTRA-PLAY-1.21.1",
  "title": "忍者立牌主动「忍术连击」：仅当前出牌周期 +1（本周期已生效时不再释放且不进冷却）",
  "version": "1.21.1",
  "target": { "type": "bugfix", "ref": "bugfix/komachi-extra-play" },
  "steps": [ { "op": "inject_command", "text": "/astralprobe komachicast K1" }, { "op": "wait", "ms": 800 }, { "op": "screenshot", "crop": true } ],
  "asserts": [ { "type": "log", "pattern": "AP_K1_AFTER:max=2:extra=1:cd=1", "scope": "whole" } ],
  "evidence": [],
  "on_fail": "keep_game_running"
}
```

**步骤 op**：`inject_command`、`inject_key`、`note`、`wait`、`screenshot`、`kubejs_reload`。
**会话期注入必须 `no_esc`（2026-09-18 t18 起，`mt_case.ps1` 结构校验强制，不静默）**：`no_esc` 的语义 = 「本步注入前**不得**先按一次归一化 Esc」（传给 `mt_inject.ps1` 的 `-NoEsc`）。三个注入 op（`inject_command` / `inject_key` / `inject_mouse`）都可声明该字段（真值）。凡用例满足下列任一条件，则**所有** `inject_*` 步骤都必须声明 `no_esc: true`，缺任一项即在 `mt_case.ps1 validate` / `run` 的**结构校验阶段**失败（`run` 直接拒绝执行、不触达游戏）：① 用例顶层声明 `"esc_sensitive": true`；② 用例任一 `inject_command.command` 命中会话命令表 `$script:EscSensitiveSessionCmds`（当前 = `targetselect`，即打开「Esc 会改语义」的目标选择会话）。**理由（实测，非推测）**：目标选择会话激活期间 Esc 就是「取消选择」，而未声明 `no_esc` 的 `inject_command` 会先走一次 Esc→Tab→Enter 界面归一化（`mt_inject.ps1` 的归一化段；pause-lock 只对 `-NoEsc` 生效）——那一次真实 Esc 当场取消会话（日志 `key=esc action=cancel`），并曾把客户端带出世界：`SELECTOR-KEYS-1.21.1` 因此 11 断言仅 7 PASS（`temp/t11/T11-REPORT.md` 的 F1），同一断言集补齐 `no_esc` 后 **11/11 PASS**。⚠️ 两点边界：① `inject_key` 按下的 Esc（如两次 `--key cancel`）是**被测输入本身**，该字段不阻止它、只阻止其前的归一化 Esc；② 声明必须真的生效 —— 声明会一路传到注入器，并由 key/mouse 子命令回显 `ESC_SKIP: … -NoEsc 声明成立`（`inject_command` 侧沿用既有 `ESC_SKIP: pause-lock 生效 + -NoEsc …` 行），故「用例写了字段却没传到注入器」不可能再被静默吞掉。**禁止**用放宽 `mt_inject` 的归一化逻辑来绕过本要求（那会掩盖真实 Esc 语义）。
**断言 type**：`log`（正/反向正则）、`absent`（反向正则，以「不得出现」为通过）、`crash`（无崩溃报告）、`kubejs`（server.log 0 error）、`mixin`（Mixin 应用行存在）、`vision`（截图视觉判定）。
**断言窗口（B7 起，`scope` 字段）**：`case`（**缺省**）只读「自**本用例**开始之后」的增量——修掉共用 launch 窗口时「前序用例把断言喂饱 ⇒ 假 PASS」（B6 实测 `APDUMP|LOCKRAW|` 命中数随用例递增 14→28→…→126）；`whole` = 整文件（启动期事实：Mixin 应用行、渲染栈加载行、`Could not decode GlobalLootModifier` 之类数据包解码行）；`launch` = 自 launch 起（介于两者之间的显式 opt-in）。
⚠️ **`log` 与 `absent` 都遵循该字段**：`absent` 的旧语义是「整文件不得出现」，收窄后**只检测本用例窗口内不得出现**——因此**凡「文本不是本用例产生」的反向断言（尤其启动/数据包解码期文本）必须显式写 `scope: whole`**（`LOOT-MODIFIER-1.20.1` 的 `Could not decode GlobalLootModifier` 即此类，已补标），否则会**静默丢掉启动期覆盖**。非法 `scope` 在 `mt_case.ps1 validate` 阶段被拦下，不在执行期静默落回默认。`mixin` **固定读 launch 窗口**（跟随 case 会退化成「窗口内无 Mixin 失败」= 恒真）。日志源不在快照偏移表内时（如离线用例的 `loadergate.log`；表内只有 `latest.log`/`debug.log`/`server.log`/`astral_probe.log`）偏移恒为 0 = 整文件，`scope` 对其无影响。
**失败取证**：`on_fail=keep_game_running` 会落 `.mt_keep_alive`；此时**退出清理不杀客户端也不停守护**（保留现场），取证后执行 `mt.ps1 --phase stop --force`。

---

## 8. 回归套件清单（1.2.0）

| 用例（双版本各一份） | 目的 | 规模 | 关键断言 |
|---|---|---|---|
| `DIRECTIONAL-BLAST-AOE` | 定向爆破 AOE 口径守卫：只吃「自身 5 点 + 效果牌伤害加成」，其它伤害效果牌不得计入 | 3 步 / 11 断言 | `AP_B1_AOE_FORMULA:5`（装齐激光/板砖/轨道炮后仍为 5）、`AP_B2_AOE_FORMULA:6`（再装书签 → +1）、`BONUS_OTHER_CARDS:0`、`absent`、`kubejs`、`crash` |
| `EMERALD-DICE-TRADE` | 绿宝石骰子：客户端报价必须是星币（本体仍是绿宝石）+ 成交后立即重发报价刷新经验 | 6 步 / 13 断言 | `AP_E1_SERVER_A:minecraft:emerald`（本体不变）、`AP_E1_CLIENT_A:astral_dice:star_coin`（客户端载荷）、`AP_E1_RESEND:ok`、`AP_E1_OPEN:ok`、`absent`、`kubejs`、`crash`；交易界面另出截图供人工核对 |
| `SMOKE-TOOLCHAIN` | 工具链自检（不依赖游戏） | 2 步 / 2 断言 | 反向断言 + `mixin` 通道可用 |
| `ANVIL-STAR-UPGRADE` | 铁砧升星端到端扣费：★0→★1/★1→★2/★2→★3 实扣 15/20/25 星币（物品栏实测） + `weapon_enhancement` 星级 +1；袋装星币 / 数量不足一律不产出 | 15 步 / 41 断言 | `AP_S1_FEE:15:0->1`、`AP_S2_FEE:20:1->2`、`AP_S3_FEE:25:2->3`、`_COINS_BEFORE:25/30/35`、`_RIGHT_LEFT:astral_dice:star_coin:3`、`_INV_AFTER_CLOSE:10`、`_STAR`、`_PUTBACK:ok:…`、`_TAKE:clicked:30:20`、`AP_Z1_BAG_RESULT:empty`、`AP_Z1_FEW_TOTAL:9`、`absent`、`kubejs`、`crash` |
| `KOMACHI-EXTRA-PLAY` | 忍者立牌主动「忍术连击」= **一次性** +1：只作用于**当前出牌轮**（不累积、不跨轮、**无出牌银行附件**、**无来源注册**）；释放前置三条 = 效果牌冷却进行中 / 出牌上限已达封顶 9 / 本轮已授予过 → 一律拒绝且**不消耗**主动技能冷却；正常释放 = 本轮上限 +1 且主动冷却起算；周期归零后一次性加成必须归零（用例文件 `cases/KOMACHI-EXTRA-PLAY-{1.21.1,1.20.1}.json`） | 13 步 / 28 断言 | `AP_K1_BEFORE:max=1:extra=0`、`AP_K1_AFTER:max=2:extra=1:cd=1`、`AP_K1_DELTA:max=+1:…:cd=started`、`AP_K1_RELEASED:1`、`AP_K2_REJECTED:1`（本轮已授予过：加成不变、主动冷却结束时刻逐字未变 `cd_same=1`/`cd_future=0`）、**`AP_K6_BEFORE:bonus=0:cd_active=1:sign_cd=0`、`AP_K6_AFTER:bonus=0:…:sign_cd=0:cd_kept=1`、`AP_K6_REJECTED:1`（**效果牌冷却进行中不得释放**，新规则）**、`AP_K3_CONST:9:bonus_one_shot=1`、`AP_K3_FILL:6`（干净基线 extra = 固定 2（大背包/忍术飞镖）+ 临时 3（命运的指引/可口糖果/探天卫星）= 5 → 上限 6；活体书页自改版起是"本周期累计"来源、**不再是效果驱动的临时来源**，故不计入）、`AP_K3_VERDICT:…`（封顶分支与未封顶正对照两形态一条正则锁死）、`AP_K5_READ:extra=0:count=0:cd=0`、`AP_K5_CLEARED:1`（周期归零 ⇒ 一次性加成归零 ＝ 无残留银行）、`absent`、`kubejs`、`crash` |
| `NANCY-LU-PEARL-IMMUNE` | 骇客「网络防火墙」：末影珍珠传送摔落伤害在伤害判定最前置处被取消（生命值不变 + `hurtTime`/`invulnerableTime`/`hurtMarked` 全未被写入），并带同构造对照相位 | 9 步 / 21 断言 | `AP_P1_FIELDS:ok`、`AP_P1_API:(hurt\|causeFallDamage)`、`AP_P1_CTRL_OK:1`（对照相位必须真受伤）、`AP_P1_IMM:…->…`（前后快照逐字一致）、`AP_P1_IMM_OK:1`、`AP_P2_PEARL:window=…:tel=1:drop=0:hurt=0:invul=0:marked=false`、`AP_P3_PEARL:window=0:tel=1:drop=5:hurt>0`、`absent`、`kubejs`、`crash` |
| `NANCY-LU-CLOAK` | 骇客主动「远程侵入」：完全隐身的机器可判定一半——隐身实例 `visible=false`（无粒子）、`nancy_lu_hidden_until>0`、到期与攻击两条解除路径都能清掉附件与效果 | 14 步 / 19 断言 | `AP_N1_STATE:hidden_until=…:vis=0:…:win=1`、`AP_N1_HIDDEN:1:1`（服务端 `isHidden` + 客户端 `isHiddenClient`）、`AP_N3_CLEARED:1`（到期路径）、`AP_N6_STATE:hidden_until=0:effect=0:vis=-1:…:hack=1:bonus=…`（攻击路径）、`absent`、`kubejs`、`crash`；**视觉半自动**（1 张截图，无 `vision` 断言） |
| `RAILGUN-AOE-SCOPE` | 电磁炮雷击命中范围取证：每个**可命中敌对目标**各生成 1 道原版雷击，而原版雷击对落点箱内**所有**存活实体生效——实测同时打中攻击者本人、中立生物与已驯服的宠物，证明该链路**没有任何阵营/所有权过滤** | 9 步 / 13 断言 | `AP_RG_TAME:tame=1:owner=1:src=`（狼已驯服并绑定主人，`src` 为命中的 UUID 取值器）、`AP_RG_BEFORE:…:charge=6:mode=forced_survival:weather=clear`、`AP_RG_CHARGE_AT_ATTACK:6`（充能**延迟到雷击落下**才结算）、`AP_RG_AFTER:…bolt_delta=3`（**只生成预期数量**的雷击：现行探针的 3 个可命中目标各 1 道；2026-09-15 裁决「方案 A」，基线由 `1` 更新为 `3`，见 §8.1 口径 2）、`AP_RG_AFTER:…:charge=0`、`AP_RG_VERDICT:enemy=1`（敌方必被命中）、`AP_RG_RESTORE:creative`、`absent`、`kubejs`、`crash` |
| `LOOT-MODIFIER` | 1.20.1 星盘战利品修饰符：Forge 47.4.10 **没有**内置 `add_table`，改用自带的 `astral_dice:add_table` 后 13 条修饰符必须全部解码成功并能真正注入星盘 | 5 步 / 6 断言 | `AP_GLM_SERIALIZERS:.*astral_dice:add_table`、`AP_GLM_ROLL:rolls=400:plates=([5-9]\|[1-9][0-9]+):items=[1-9][0-9]*:err=none`（实滚 400 次 `minecraft:chests/simple_dungeon`，子表 5% 出星盘）、`absent` `Could not decode GlobalLootModifier`、`kubejs`、`crash`（仅 1.20.1 一份） |
| `EFFECT-DECAY-FLICKER` | 层数递减类效果（治愈/标记/弱点识破/赋能）客户端 HUD **不再闪烁**：`mixin/client/GuiMixin` 把 `Gui#renderEffects` 的 `endsWithin(200)` 判定对四类效果恒定返回 false | 13 步 / 11 断言 + 1 张截图 | `AP_F1_SETUP:heal=ok:mark=ok:emp=ok:blessed_before=…`、`AP_F1_STATE:heal=amp=2:dur=200…\|mark=amp=1:dur=200…\|emp=amp=2:dur=200…`（三实例**同时**落入原版闪烁窗口）、`AP_F1_WINDOW:1:200`、`AP_F2_STATE:heal=absent\|mark=absent\|emp=absent`、`Mixing client.GuiMixin … into net.minecraft.client.gui.Gui`（`scope: whole`）、`mixin`/`kubejs`/`crash`；**闪烁与否不做机器判定**，附 1 张截图人工核对（2026-09-15 双版本 PASS） |
| `EFFECT-CARD-LOCK` | 效果牌出牌状态机**永久锁死**回归（2026-09-14 外部汇报的严重 BUG）：出牌数已达当轮上限却没有冷却在跑（"上限在周期中途下降"造成的不变量违例）时，`tick` 必须补上一轮冷却，且该冷却到期后出牌数与冷却双双清零、出牌锁解除 | 1 步 / 3 断言 | `AP_<tag>_LOCK:…:blocked_before=1:remain_before=0:cd_after_future=1:count_cleared=0:cd_cleared=0:blocked_after=0`、`AP_<tag>_LOCK_OK:1`、`AP_<tag>_DONE`、`absent`、`kubejs`、`crash` |
| `EFFECT-CARD-HOLD` | 效果牌「一次按下只出一张」：长按右键不得连续出牌（客户端 `Minecraft#startUseItem` 在 HEAD 被取消 ⇒ 既不调用 `use` 也不发 `ServerboundUseItemPacket`，服务端自然不多出牌） | 4 步 / 3 断言（含 1 步对照） | ① 对照（证明原版自动重复**真的**发生，否则修复判据不成立）：`/gamemode survival` + `/effect give @s minecraft:resistance 60 4 true` + `/item replace entity @s weapon.mainhand with minecraft:egg 16` → `mt_inject.ps1 key -Key rclick -HoldMs 3000` → `/data get entity @s SelectedItem` 的 `count` 必须**下降约 15**（16 → 1，原版 4 tick/次）；② 修复判据：`/astralprobe komachicast H` + `/astralprobe equipslot chip "astral_dice:big_backpack_chip" HB`（当轮上限 = 1+1+1 = 3）+ 手中持**无待定效果**的效果牌（`/item replace entity @s weapon.mainhand with astral_dice:effect_card_chocolate_cake 16`；王之力/激光一类「效果待定」牌会自我封锁，不可用于本用例）→ 同样 `-HoldMs 3000` → `AP_H_READ:extra=1:count=1:cd=0:max=3`（15 次尝试只出 1 张，且**不得**起周期冷却）；③ 复位判据：松键后再单击一次 → `count=2`（守卫按「同一次按下」复位，不是永久锁） |
| `HOSTILE-TARGET-NEUTRAL` | 「敌对目标」判定纳入**被激怒的中立生物**（北极熊/狼/铁傀儡/蜜蜂；末影人/僵尸猪灵本身即 `Enemy`）：统一入口 `HostileTargets.isHostile` 生效 | 复用 `railgunfriendly` 探针（2026-09-15 双版本 PASS） | `AP_<tag>_VERDICT:…:neutral=1…`（北极熊 `AngerTime>0` 时必被雷击） |
| `RAILGUN-PET-EXCLUDE` | 电磁炮雷击排除**施放者自己拥有的宠物**（`OwnableEntity` owner == 闪电 cause） | 复用 `railgunfriendly` 探针（2026-09-15 双版本 PASS） | `AP_<tag>_VERDICT:…:friendly=0…`（已驯服狼 `AngerTime>0` 仍不被雷击） |
| `RAILGUN-OVERRIDE-CLASS` | 雷击白名单**上移到 `LightningBolt#tick` 目标筛选**后，覆写 `thunderHit` 且不调 `super` 的原版生物（海龟/村民/猪/蘑菇牛）不再被伤害/转化 | 复用 `railgunfriendly` 探针（2026-09-15 双版本 PASS） | `AP_<tag>_VERDICT:…:turtle=0:villager=0:valive=1:talive=1`（村民不得被移除＝未被转女巫） |
| `SPELL-TRUE-DAMAGE` | 伤害效果牌（法伤）加成走 `astral_dice:true_damage`（只效果牌那部分穿甲，基础武器伤害照旧） | 探针 `/astralprobe spelltdsetup <tag>` + `spelltdhit <tag>`（**必须分两条命令**：属性命令晚一 tick 生效；2026-09-15 双版本 PASS） | `AP_SD_CTRL:bare=1:arm=0.21`（对照相位：无效果牌，护甲吃满）、`AP_SD_TEST:bare=4:arm=3.21`（试相位：+4 法伤后两相位差值相等 ⇒ 加成部分不吃护甲）、`AP_SD_VERDICT:base_armor_effective=1:bonus_true_damage=1:no_api=0` |
| `FEN-SPLASH-MAIN-TARGET` | 大当家溅射对主目标生效（原版 `hurt` 的 `lastHurt`/无敌帧顺序缺陷已修） | 复用 `fensplash` / `fensplashhit` / `fensplashread` 探针（2026-09-15 双版本 PASS） | `AP_<tag>_AFTER:tdealt=…:near_dealt=…:armored_dealt=…` + `VERDICT:true_damage=1:ratio_ok=1` |
| `LOADER-GATE-FORGE` | 1.20.1 Forge 加载器门槛：低于 47.4.10 必须在 FML 依赖排序阶段被拒（离线 `VersionRange` 实测，不需启动游戏） | `mt_loadergate.ps1` + 5 步 / 6 断言（2026-09-15 PASS） | 旧区间接纳 47.0.0（BUG 复现）／新区间拒绝 47.0.0、47.4.9 并接纳 47.4.10+（`AP_LG_VERDICT:bug_repro=1:gate_ok=1:loader_ok=1:overall=1`） |
| `AIRBAG-BYPASS-KILL` | 安全气囊对**无视无敌**的致死伤害依然有效(2026-09-15 用户裁决「使其始终保持有效」):用与 `/kill` **完全同一条原版代码路径**(`LivingEntity#kill` = `hurt(generic_kill, Float.MAX_VALUE)`)施加致死,气囊必须拦下;并带**同构造对照**证明该伤害源在本局内确实致命 | 13 步 / 26 断言(2026-09-15 双版本冷启动 PASS) | `AP_A_PREP:mode=forced_survival:…:equipped=1:chip=astral_dice:airbag_chip:charge=6:cd_end=0`、`AP_A_KILL:path=kill:hp_before=<h>:health=<h>:alive=1:…:charge=0:cd_left=(1200\|1[01]\d{2})`(血量**逐字不变**,由正则反向引用 `\2` 锁死)、`AP_A_STATE:…:charge=0:cd_left=1[01]\d{2}`、`AP_B_LETHAL:path=kill:hp_before=10:hp_after=0:alive=0`(对照:同一 `kill()` 打猪必死)、`AP_C_NOCHARGE:…:charge=0:cd_end=0`、`AP_Z_RESET:mode=creative`、`absent`、`kubejs`、`crash`;⚠️ 玩家侧「充能不足/冷却中真会死」分支未覆盖,见 §11 覆盖缺口 |
| `GUIDE-BOOK-FIRST-JOIN-ONLY` | 《恋的规则书》「**仅首次进入世界发放一次**」守卫:附件 `guide_book_given` 必须**随死亡保留**(1.21.1 `ModAttachments.GUIDE_BOOK_GIVEN` 加 `.copyOnDeath()`;1.20.1 `AstralData.onPlayerClone` 死亡白名单加该键)——首登发 1 本且守卫置位(`given=1`),死亡 + 重生后 `given` 仍为 1,死亡后**重登不得再补发**(背包仍只有 1 本);修补前「死亡后重生」读到 `given=0`、重登后 `count` 变 2 | 9 步 / 7 断言 | `AP_G1_GUIDE:given=1:count=1`(首登)、`AP_G3_GUIDE:given=1:count=1`(**死亡重生后**,修补前必 FAIL: `given=0`)、`AP_G1_GUIDE_DONE`、`AP_G3_GUIDE_DONE`、`absent`(`AP_G1_GUIDE:given=0` / `AP_G3_GUIDE:given=0` / 探针 `_ERR`/`_EX` / `AP_TICK_EX`)、`kubejs`、`crash`;⚠️ **需跑两次**:全流程(首登)+ 仅 `--phase launch`(死亡后重登),见下方专项说明 |
| `ELECTRIC-GLOVE-AOE-BASE` | 电击手套 3 格 AOE 波及伤害的**基准口径**双版本对等(KI-5):生产读到的那个基准必须是**护甲后**值 —— 同一条读数里 `base=`(探针注册进生产修饰器表的同路 `SpellDamageModifier` 读 `ctx.event`,非另算)必须与主目标实掉血 `self=`、3 格内敌对邻居实掉血 `nbr=` **逐字相等**,且严格小于无甲参照靶的护甲前值 `raw=`;修补前 1.20.1 的法伤主链路挂 `LivingHurtEvent`(护甲前)⇒ 读到 `base=raw=8:self=nbr=2.24` ⇒ **必 FAIL** | 6 步 / 8 断言(双版本各一份) | `AP_GB_GA:base=[0-9.]+:raw=8(?:\\.0+)?:self=[0-9.]+:nbr=[0-9.]+:armor=20(?:\\.0+)?:rarmor=0(?:\\.0+)?:mode=(forced_survival\|already_survival):weather=clear:armed=1:bonus=0:bsrc=modifier`(构造自证:`raw`=同额伤害在无甲靶上的掉血、`armor`=主目标护甲、`armed`=生产判定入口 `isAoeArmed`、`bonus=0` 排除第二发真伤、`bsrc=modifier` 证明基准确由生产同路修饰器读出)、**`AP_GB_GA:base=([0-9.]+):raw=8(?:\\.0+)?:self=\\1:nbr=\\1:`(核心不变量:生产基准 = 护甲后主目标掉血 = 邻居掉血;由正则反向引用 `\\1` 锁死)**、`AP_GB_GA:base=(?:0\\.[0-9]+\|[1-7](?:\\.[0-9]+)?):raw=8(?:\\.0+)?:`(数值区间断言:base 严格小于护甲前值 8)、`AP_GB_GA:.*:nbr=(?:0\\.[0-9]*[1-9][0-9]*\|[1-9][0-9]*(?:\\.[0-9]+)?):`(AOE 真的波及,`nbr>0`)、`AP_GB_GA_DONE`、`absent`(`AP_GB_ERR`/`AP_GB_EX:`/`AP_TICK_EX`)、`kubejs`、`crash` |

> `DIRECTIONAL-BLAST-AOE` / `EMERALD-DICE-TRADE` 于 2026-09-13 追加，配套探针命令
> **真伤判据(2026-09-14 手工实测,两步走)**:`astral_dice:true_damage` 的穿甲能力**必须分两条命令**验证——
> 因为属性类命令的生效时机晚于同一 tick 内的后续代码(先 `attribute` 再立刻打伤害会读到旧护甲值)。
> ```text
> /kill @e[type=minecraft:husk]
> /summon minecraft:husk ~ ~ ~3
> /attribute @e[type=minecraft:husk,limit=1] minecraft:generic.armor base set 20
> /attribute @e[type=minecraft:husk,limit=1] minecraft:generic.armor_toughness base set 8
> /damage @e[type=minecraft:husk,limit=1] 10 minecraft:mob_attack      # 对照:应只掉 3.0
> /data get entity @e[type=minecraft:husk,limit=1] Health               # → 17.0f
> /data merge entity @e[type=minecraft:husk,limit=1] {Health:20.0f}
> /damage @e[type=minecraft:husk,limit=1] 10 astral_dice:true_damage   # 真伤:应掉满 10.0
> /data get entity @e[type=minecraft:husk,limit=1] Health               # → 10.0f
> ```
> 实测读数(1.21.1):对照 20.0→17.0(**3.0**),真伤 20.0→10.0(**10.0**) —— 同一只重甲靶、同为 10 点,
> 护甲减免对真伤完全不生效。
>
> **探针命令**:`/astralprobe truedmg <tag>`(重甲靶上分别施加本模组真伤与 `minecraft:mob_attack` 并读差值;
> 注意其 attribute 步骤落在下一 tick,故其判据只看「真伤是否全额」)、
> `/astralprobe railtruedmg <tag>` + `/astralprobe railtruedmgread <tag>`
> (装备电磁炮 + 6 充能,空手打一只无甲僵尸、另让一只重甲尸壳站在雷击箱内只吃雷击;`bolt_true_damage=1` 即穿甲)。
>
> `/astralprobe blastbonus|emeraldtrade|tradeclose`（见 §6 探针安装步骤，改探针后必须冷启动）。
> 两例的视觉面（交易列表图标、经验条）**不做机器断言**（`vision` 只输出提问请求），
> 报告内附截图作为人工核对证据。
>
> `ANVIL-STAR-UPGRADE` 于 2026-09-14 追加，配套探针命令
> `/astralprobe anvilstar <diceId> <tag> [fresh]` / `anvilbags <tag>` / `anvilclose <tag>`
> （见 §6 探针安装步骤，改探针后必须冷启动）。
> **交互半自动**：铁砧升星的真实链路是「`AnvilUpdateEvent` 只写 `output`/`materialCost`/`cost`
> → 原版 `AnvilMenu#onTake` 按 `repairItemCountCost` 从**铁砧右槽**扣材料、按 `cost` 扣经验」，
> 故该例由探针用 `player.openMenu`（真实铁砧方块的 `MenuProvider`）+ `AbstractContainerMenu#clicked`
> 驱动原版铁砧界面（与真人 GUI 的差别只有「点击由服务端发起，不经客户端网络包」）；
> `AP_<TAG>_SRC` 读数标明走的是真实铁砧方块（`block`）还是退化的直接构造菜单（`direct`），
> 后者两个读数都判 PASS、但报告里应人工留意。
> **视觉/交互面不做机器断言**：用例只附一张 HUD 截图作人工核对证据
> （末态快捷栏应为 ★0 骰子 + 1 个袋装星币 + 9 枚散装星币），机器判定全部来自聊天栏 `AP_` 读数。
> 探针会把玩家**临时切到生存再还原**（原版在创造模式 `instabuild` 下跳过经验扣除，不切就无法
> 断言 `TAKE:clicked:30:20`），实际路径由 `AP_<TAG>_MODE` 读数注明。

> `KOMACHI-EXTRA-PLAY` / `NANCY-LU-PEARL-IMMUNE` / `NANCY-LU-CLOAK` / `EFFECT-DECAY-FLICKER`
> 于 2026-09-14 追加，配套探针命令
> `/astralprobe komachicast|komachirepeat|komachicap|komachicooldown|komachicycle|komachiread`、
> `nancycloak|nancyexpire|nancystate|nancyfall`、`nancypearl|nancypearlctrl|nancypearlclose`、
> `decayflash|decayclear`（见 §6 探针安装步骤；**探针改动必须冷启动**：`/kubejs reload server-scripts`
> 不会重绑已注册命令的 lambda）。
> **「按主动」的取证口径**：三例统一由 `BaseSignItem.performSkillForCurio` 触发 —— 与客户端按键经网络包
> （1.21.1 `SignActivatePayload` / 1.20.1 `ModNetwork`）的服务端处理是**同一入口**，不是直接改附件。
> **忍者主动的现行语义（2026-09-14 按用户裁决重写）**：主动只调用 `EffectCardPeriod.grantBonusPlay(player)`
> —— 一次性把**当前出牌轮**的上限 +1（附件 `effect_card_bonus_plays`，0/1，随出牌轮归零清除）。
> **旧「出牌银行」与主动来源注册已全部删除**：`komachi_extra_plays` 附件、`KOMACHI_EXTRA_PLAYS_CAP` 常量、
> `EffectCardPeriod` 里为主动注册的 `ExtraPlaySource` 一律不复存在（残留检查：全仓 `grep komachi_extra_plays`
> 必须为 0 命中）；立牌装卸**不影响**已授予的加成（授予即已消耗；若在卸下时回收会造成"上限中途下降"的
> 不变量违例，见 `EffectCardPeriod#tick`）。
> **「不进入冷却」的取证口径**：先把主动冷却结束时刻置为「已过期但非 0」（既越过 `performSkill` 的冷却分支、
> 又留下可比的基线），按主动后该字段必须**逐字未变**（`cd_same=1` / `cd_future=0`）；若被重新写成未来时刻，
> 即说明冷却被起算。
> **封顶分支的现状（如实标注，勿当成已验证）**：忍者主动「出牌上限已达封顶 9」这一分支在当前内容下
> **不可达** —— 满配 extra = 固定来源 2（大背包 / 忍术飞镖）+ 临时来源 3（命运的指引 / 可口糖果 / 探天卫星）
> = 5 → `getMaxAllowed()` = 6；`komachicap` 再注册两个探针来源也只到 8，仍低于封顶 9。
> `komachicap` 会尝试注册两个「常态关闭」的
> 临时来源把上限推到 9 以真正覆盖该分支（两种 Rhino 写法都试：`new Iface({...})` 与
> `new JavaAdapter(Iface, {...})`；都不可用则**不注册**、读数落 `_SRC:unavailable:…`），
> 此时用例退化为「未封顶时主动必须正常释放、且不得超过封顶 9」
> 的正向对照；两种形态由 `AP_K3_VERDICT` 一条正则锁死，报告里应写明本机实际跑到哪条。
> **末影珍珠免疫的证据口径**：受伤音效与受伤动画包无法在服务端直接读，以
> 「`hurtTime`/`hurtDuration`/`invulnerableTime` 未被赋值 + `hurtMarked` 未被置位」为准 ——
> 四者与 `indicateDamage`（发 `ClientboundHurtAnimationPacket` = 红屏/屏幕震动）、`playHurtSound`、
> `markHurt`（击退同步）同处事件取消点之后的同一段代码，`hurt` 提前 `return false` 即全部不执行。
> 另设两条防「空跑」的对照：直接施加 FALL 伤害的对照相位必须真的掉血（`AP_P1_CTRL_OK:1`），
> 真实珍珠的对照相位（卸下立牌）必须 `drop=5` 且 `tel=1`（传送位移 > 1 格，排除「珍珠撞到玩家本体」
> 这种不产生摔落伤害的退化路径）。
> **视觉/交互半自动**：`NANCY-LU-CLOAK`（自身盔甲/手持/Curios 不渲染、无药水粒子）与
> `EFFECT-DECAY-FLICKER`（层数递减类效果图标恒定不闪）各附 1 张截图作人工核对证据，
> **不写 `vision` 断言**，其余判定全部来自聊天栏 `AP_` 读数与 Mixin 应用行
> （`debug.log` 不随每次运行清空，故 `Mixing` 行只作「本机曾成功应用」的证据，失败面由增量区间的
> `mixin` 断言兜底）。
> ✅ **本批四条用例已于 2026-09-15 在真实游戏中运行**（探针改动经冷启动生效），逐条结果见 §8.1。`EFFECT-DECAY-FLICKER` 在 1.20.1 首次运行失败，根因是**测试侧状态污染**（同会话前序用例残留的骰神赐福），**非产品回归**；已定位（见 §10 第 16 条）并修好探针后复跑 PASS。

> `GUIDE-BOOK-FIRST-JOIN-ONLY` 于 2026-09-15 追加（R1 独立代码验证发现缺陷、产品侧修补后的回归用例），配套探针命令
> `/astralprobe guidebook <tag>`（**只读**；与同族一致，**改探针后必须冷启动**才生效）。
> **⚠️ 特殊用法：本用例必须跑两次，且两次都必须 PASS** ——
> ① **第 1 次跑全流程**（`--phase build/env/launch/cases`，`env` 会**重建测试世界**）= 玩家**首次进入世界**：
> `AP_G1_GUIDE:given=1:count=1` ⇒ 证明「首次发放 1 本」且守卫已置位；随后 `/kill @s`（= `generic_kill`）让玩家**真死**，
> 重生后 `AP_G3_GUIDE:given=1:count=1` ⇒ **修补前此处必 FAIL（读到 `given=0`）**。
> ② **第 2 次只跑 `--phase launch` + 本用例**
> （`pwsh -NoProfile -File scripts/test/mt.ps1 --phase launch --version <版本>`，再
> `pwsh -NoProfile -File scripts/test/mt.ps1 --phase cases --version <版本> --case cases/GUIDE-BOOK-FIRST-JOIN-ONLY-<版本>.json`），
> **不跑 `env` ⇒ 不重建世界**，等价「**死亡后重新登录**」：**同一条** G1 断言必须仍然 `given=1:count=1`
> ⇒ 证明「死亡 + 重登**不再**补发」；若守卫没随死亡保留，登录时会再发一本 ⇒ G1 读到 `count=2`。
> **为什么不能只靠 `count` 判**：测试世界 `keepInventory=true`，死亡不丢物品 ⇒ 死亡相位 `count` 恒为 1，
> **决定性字段是 `given`**（修补前死亡后新实体回默认 `false`）。
> **死亡相位的读法**：玩家真死后停在死亡界面，**紧接死亡的那一次注入（含回车）会落在「重生」按钮上**（§10 第 21 条实测），
> 约 4 秒后完成重生 ⇒ 那一次读数**不可信**：用例把它当「触发重生」的注入（标 `G2`，**不做任何断言**），
> 留 ≥5 秒后再注入 `G3` 作**死亡后的权威读数**。
> **`count` 的取值口径**：探针 helper 统计**主物品栏 + 快捷栏 + 副手**（不含护甲 / Curios 槽）；
> 手册的**物品注册 id 是 `patchouli:guide_book`**，`astral_dice:astral_guide` 只是数据组件（1.20.1 为 NBT）里的**书籍 id**，
> 故 `count` =「物品 id 粗筛 + `ItemModBook.getBook(stack).id` 精确比对」（§10 第 19 条的同族坑：**书籍 id ≠ 物品 id**，
> 直接 `resolveItem("astral_dice:astral_guide")` 必返 `null`）。**不做视觉断言**、不生成截图。

> `ELECTRIC-GLOVE-AOE-BASE` 于 2026-09-25 追加（KI-5「电击手套 3 格 AOE 波及伤害基准跨版本不一致」的回归判据），
> 双版本各一份（`cases/ELECTRIC-GLOVE-AOE-BASE-{1.21.1,1.20.1}.json`），配套探针命令
> `/astralprobe glovebase <tag>`（**改探针后必须冷启动**才生效；本命令自造并自证全部构造，一条命令产出一行读数）。
> **被测的唯一来源（读生产代码定死，不许另算）**：`SpellDamageRegistry` 里电击手套修饰器的 `onHit` 用
> `float total = ctx.event.<事件伤害读取>` 作为本次 AOE 的**唯一伤害基准**，随后用
> `astral_dice:true_damage`（不吃护甲）对主目标 3 格内的敌对目标逐个 `hurt`。两版本的差异只在
> `DamageEffectCardHandler` 把 `ctx.event` 构造成哪个事件：1.21.1 = `LivingDamageEvent.Pre`（`getNewDamage()`，
> **护甲/附魔减免之后**）；1.20.1 修补前 = `LivingHurtEvent`（`getAmount()`，**护甲之前**）、修补后 =
> `LivingDamageEvent`（`getAmount()`，护甲后）。⇒ 修补前同一发法伤在 1.20.1 上会以**护甲前原始值**为基准做
> 3 格 AOE，带甲目标周围多打一截。
> **取证口径（为什么这样做不算"自己算"）**：探针用 KubeJS 的接口实现（`new Iface({...})` —— 与本仓
> `komachicap` 已实证的 `EffectCardPeriod$ExtraPlaySource`（`AP_K3_SRC:ok:2:form:kubejs`）同一机制）
> **自己实现一个 `SpellDamageModifier` 并注册进生产同一张修饰器表**，在它的 `onHit` 里用
> **与生产逐字相同的读取表达式**读 `ctx.event`：1.21.1 是 `getNewDamage()`；1.20.1 是 `getAmount()`
> （该表达式在修补前后的 `LivingHurtEvent`/`LivingDamageEvent` 上**同名同签名**，故读数自动跟随生产实际
> 挂载的事件，不存在"探针自己挑事件"的自由度）。生产修饰器注册在前 ⇒ 它的 `onHit` 先跑（先结算 AOE
> 再解除武装），探针随后读**同一事件对象的同一字段**。读不到（接口实现失败 / 未被调用 / 读取抛错）一律落
> `AP_<tag>_ERR:`，**绝不静默降级**。
> **构造自证（全在同一条命令内）**：三只非亡灵敌对靶 —— `ref` 无甲（与 `main` 相距 ≥6 格，同额伤害的掉血即
> 「护甲前」客观参照 `raw`）、`main` 护甲 20/韧性 8（主目标，`self` = 其实掉血 = 护甲后客观值）、`nbr` 在
> `main` 3 格内（`nbr` = AOE 实际造成的掉血）；护甲**直写属性实例**而非 `attribute` 命令，以规避本条 §10
> 实测的「属性命令生效时机晚于同一 tick 内后续代码」；玩家强制生存、天气 clear、5 张伤害效果牌效果清空 +
> 忍者「效果牌伤害增益」归零（⇒ 本次事件 `bonus=0`，`main` 只吃这一发箭伤，`self` 无歧义）；
> 武装由探针直接置位附件（与 `ELECTRIC-GLOVE-ROUND-RESET` 同一脚手架），读数 `armed=` 取**生产判定入口**
> `ElectricGloveChipItem.isAoeArmed`。
> **判据与鉴别力**：核心断言是 `base == self == nbr`（正则反向引用 `\1` 锁死）+ `base < raw`（数值区间）
> + `raw=8`/`armor=20`/`rarmor=0`/`nbr>0`。**修补前的 1.20.1 预期读数**为
> `AP_GB_GA:base=8:raw=8:self=2.24:nbr=8:armor=20:…`（AOE 以护甲前值 8 为基准，邻居多打 5.76），
> 此时第 2、3 条断言必不命中 ⇒ **FAIL**；修补后（挂 `LivingDamageEvent`）应为
> `base=2.24:raw=8:self=2.24:nbr=2.24` ⇒ 两版本均 PASS。离线预检（把上面两种读数行喂给本用例的全部正则）：
> 修补后形态 5/5 正断言命中、反断言 0 命中；修补前形态正断言 2 条不命中 ⇒ 两个方向都符合预期。

> **历史说明（2026-09-14）**：`BUG1-BLESSING-HUD`、`BUG2-EMPOWER-DECAY`、`BUG3-MIXIN-BADGE`、
> `BUG4-OCULUS-LIGHTNING`、`BUG5-RAILGUN-DELAY-CD` 五条用例**及其全部判定内容**（含仅服务它们的
> 13 条探针命令 `status` / `setup` / `railtest` / `cdguard` / `charge` / `empower` / `empowerclear` /
> `blessingclear` / `decayinterval` / `decaydue` / `boltvis` / `watch` / `hud`）已按用户裁决移除，
> **不再是在用用例**，后续运行不再产出它们的结论；`attack` 因仍被 `NANCY-LU-CLOAK` 的
> 「攻击解除隐身」相位复用而**保留**。它们的历史运行报告仍保留在 `scripts/test/reports/2026*`
> 作为证据存档（不再复跑）。

### 8.1 A 组复跑实测结果（2026-09-15，执行者独立于实现方）

- 运行 id：`20260913-175652`（sticky run）；产物：`scripts/test/reports/20260913-175652/{1.21.1,1.20.1}/report.md`、`SUMMARY.md`（含日志与截图副本）；条目状态契约：`cases/.mt_run_state.json`。
- 被测二进制**未改动**：沿用已部署的 `astral_dice-1.2.1+*.jar`（2026-09-15 01:17 构建），本批只改 `scripts/test/**`。
- 日志归属可证：每条用例都由 `mt_launch`（启动前预检全机无客户端 + `latest.log` 删除成功 + 只接受 `CreationTime` 晚于本次启动的日志）拉起本机客户端产生；1.21.1 窗口标题 `Minecraft NeoForge* 1.21.1`，1.20.1 为 `Minecraft* Forge 1.20.1`。

| # | 用例 | 1.21.1 | 1.20.1 | 关键原始读数（聊天栏 `AP_` 行） |
|---|---|---|---|---|
| 1 | `HOSTILE-TARGET-NEUTRAL` | PASS | PASS | `AP_<tag>_VERDICT:self=0:enemy=1:neutral=1:friendly=0:turtle=0:villager=0:valive=1:talive=1`；`neutral=25`（被激怒北极熊 `AngerTime>0` 确被雷击）；`bolt_delta=3` |
| 2 | `RAILGUN-PET-EXCLUDE` | PASS | PASS | `AP_PE_AFTER:…:friendly=0` + `SCOPE_OK:1`（施放者自己的已驯服狼未被自方雷击；`Fire=-1` 证明未被点燃） |
| 3 | `RAILGUN-OVERRIDE-CLASS` | PASS | PASS | `AP_<tag>_VERDICT:…:turtle=0:villager=0:valive=1:talive=1` + `SCOPE_OK:1`（村民未被转女巫、海龟未被伤害） |
| 4 | `SPELL-TRUE-DAMAGE` | PASS | PASS | `AP_SD_CTRL:bare=1:arm=0.21`、`AP_SD_TEST:bare=4:arm=3.21`、`AP_SD_VERDICT:base_armor_effective=1:bonus_true_damage=1:no_api=0`（两相位差值相等 ⇒ 只有效果牌那段法伤穿甲） |
| 5 | `FEN-SPLASH-MAIN-TARGET` | PASS | PASS | 1.20.1 末次：`AP_FS_BEFORE:thp=16` → `AP_FS_SNAP:thp=4.72`、`AP_FS_MELEE:player_attack:dealt=11.28:recharge=3`（5→3 = 满层引爆）、`AP_FS_AFTER:tdealt=11.28:near_dealt=5.28:armored_dealt=5.28:far_dealt=0:melee_est=6:ratio=0.88`、`AP_FS_VERDICT:in_range=1:out_range=1:true_damage=1:at_floor=0:ratio_ok=1` |
| 6 | `LOADER-GATE-FORGE`（仅 1.20.1，纯离线） | —（无此版本） | PASS | `AP_LG_VERDICT:bug_repro=1:gate_ok=1:loader_ok=1:fail=:fml_stage=dependency_sorting:msg=Missing or unsupported mandatory dependencies:overall=1` |
| 7 | `EFFECT-DECAY-FLICKER` | PASS | PASS | `AP_F1_STATE:heal=amp=2:dur=200:amb=0:vis=0\|mark=amp=1:dur=200:amb=0:vis=1\|emp=amp=2:dur=200:amb=0:vis=0`、`AP_F1_WINDOW:1:200`、`AP_F2_STATE:heal=absent\|mark=absent\|emp=absent`；截图 `run/1.20.1/screenshots/decay_flash_20260913-175652.png`、`run/1.21.1/screenshots/decay_flash_20260913-175652.png`（三效果图标恒定不闪，人工核对） |

- **2026-09-15 收尾批次（HEAD `e4b4bbe`，双版本 5/5 全绿）**：1.20.1 `RAILGUN-AOE-SCOPE`/`HOSTILE-TARGET-NEUTRAL`/`RAILGUN-PET-EXCLUDE`/`RAILGUN-OVERRIDE-CLASS` + 1.21.1 `RAILGUN-AOE-SCOPE` 共 5 例全 PASS（27/26/26/22/27 条断言，0 FAIL）；读数 `self=0`、`friendly`/`turtle`/`villager` 全 0、`SCOPE_OK:1`；`ANGER_POST` 两版均为 `mergeN=api:mergeF=api`。

- **2026-09-15 AOE 断言补强批次（HEAD `09a34e5`，双版本 2/2 全绿）**：按用户裁决「方案①」补上防误伤/范围断言后冷启动复跑 —— 1.20.1 `RAILGUN-AOE-SCOPE` 15/15 PASS、1.21.1 同名用例 16/16 PASS（0 FAIL / 0 ERROR）；新正断言全部命中、新反断言 0 命中；读数 `self=0`、`friendly`/`turtle`/`villager` 全 0、`SCOPE_OK:1`，`bolt_delta` = 1.20.1 `3`（两次 read 均 3）、1.21.1 `2`（两次 read 均 2）。探针两版逐字节未变、jar 未重建（hash 与开工一致）、无 crash、kubejs 0 error。

**必须一并阅读的五条口径**：

1. **`SUMMARY.md` 的版本结论显示 FAIL，那是全量条目聚合**：同一 sticky run 里仍留着上一轮会话的 `ANVIL-STAR-UPGRADE` / `DIRECTIONAL-BLAST-AOE` / `EMERALD-DICE-TRADE` / `KOMACHI-EXTRA-PLAY` / `NANCY-LU-*` / `RAILGUN-AOE-SCOPE`（`ERROR`/`SKIP`），以及执行者两次**调用姿势错误**留下的 `.mt_snapshot`、`EFFECT-DECAY-FLICKER`（漏 `--case` 路径）两条 `ERROR`。A 组 7 项在 `cases/.mt_run_state.json` 中逐条为 `PASS`。
2. `RAILGUN-AOE-SCOPE` 行内的 `bolt_delta` 期望**相对现行探针已过期**（原为 `1`），**2026-09-15 裁决走「方案 A」**：直接把 `RAILGUN-AOE-SCOPE-{1.21.1,1.20.1}.json` 的断言改为 `bolt_delta=3`（现行 `railgunfriendly` 探针摆 5 靶并激怒中立/宠物，其中可命中者为 3 个 → 3 道雷击），用例语义保持「只生成预期数量、没有多生成」。**「方案 B」记为待办**：下次因其它原因动探针输出格式时，顺手把 `bolt_delta` 改成**按目标类型分桶**（如 `extra_neutral` / `extra_pet` / `extra_override`），届时把期望逐项锁死——不要为这一条次要计数断言单独付「改探针格式 + 冷启动 + 读数失去可比性」的代价。该用例的鉴别力来自 `VERDICT` 的命中范围/防误伤（`turtle=0:villager=0:friendly=0` 等），`bolt_delta` 只是旁证，故方案 A 不降低鉴别力。⚠️ **本段为历史裁决记录（2026-09-15 早段），其期望值 `3` 与「鉴别力来自防误伤」的表述均已被下方「最终口径」取代 / 更正。**
   **2026-09-15 最终口径（三轮冷启动复跑后定案）**：推导值 `3` 被实测否证（推导把「已激怒的已驯服狼」误计为可命中者，与 `RAILGUN-PET-EXCLUDE` 要求狼被排除的口径**直接冲突**）。修正探针激怒步（改用 Java API `NeutralMob#setRemainingPersistentAngerTime`，替代「按类型选全世界最近实体」的选择器；实测两版 `ANGER_POST:mergeN=api:mergeF=api`）后，`VERDICT`/`SCOPE_OK` 在双版本全部符合设计，但 **`bolt_delta` 本身不是稳定量**：八次干净冷启动分别为 1.21.1 `2 / 4 / 4`、1.20.1 `6 / 3 / 5 / 3 / 5`——根因是它是**全局雷击生成计数器**的差值，世界里任何残留的可命中实体都会各自多生成一道（探针只在收尾按 `distance=..16` 清同类残留）。**故放弃固定值断言，两版统一改为下界** `AP_RG_AFTER:.*bolt_delta=[2-9][0-9]*`（=「至少为 2 个可命中目标各生成一道」，不设上限）；⚠️ **但「鉴别力来自 `VERDICT` / `SCOPE_OK`」这句结论本身不准确（2026-09-15 复核断言集时发现，详见 §10-18）**：`RAILGUN-AOE-SCOPE` 现有 13 条断言里只有 `AP_RG_VERDICT:.*:enemy=1:` + `absent …:enemy=0:`（外加 `bolt_delta` 下界、`charge=0`、`RESTORE`/`DONE`、`kubejs`/`crash`），**没有任何防误伤 / 范围断言**——`friendly`、`turtle`、`villager`、`self`、`SCOPE_OK` **全未断言**，故实测出现过的 `self=2→6:friendly=12:turtle=12:villager=12:SCOPE_OK:0`（雷击波及箱内全部实体）在该用例里**不会被判 FAIL**（用例 note 自己也写明「断言集不含防误伤项」）。防误伤目前由同族用例**族级**覆盖：`RAILGUN-PET-EXCLUDE`（`friendly=0` 正断言 + `absent …:friendly=[1-9]`）、`RAILGUN-OVERRIDE-CLASS`（`turtle=0:villager=0:valive=1:talive=1` + 反断言）、`HOSTILE-TARGET-NEUTRAL`（`friendly=0`、`neutral=1` + `absent …:neutral=0`）。「方案 B」（探针按目标类型分桶输出 `extra_neutral`/`extra_pet`/`extra_override`）仍记为待办。**收尾批次（HEAD `e4b4bbe`）双版本 5/5 全绿**（1.20.1 AOE/HN/PE/OC + 1.21.1 AOE）：`self=0`、`friendly`/`turtle`/`villager` 全 0、`SCOPE_OK:1`，`bolt_delta` = 1.20.1 `5/3/3/4`、1.21.1 `4`；每例 `AP_*_RESTORE:creative` ×2、`AP_*_DONE` ×5，证实**收尾双注入**（§10-17）生效。

3. `HOSTILE-TARGET-NEUTRAL` 在 1.20.1 首轮出现过一次 `self=2.67/4.67`（施放者间歇自伤），随后复跑 `self=0`；1.21.1 全程 `self=0`。间歇复现、根因未定，见 §8.2。
4. 1.20.1 的 `FEN-SPLASH-MAIN-TARGET` 三次运行中一次 FAIL：远靶（`blaze`，`placeAt(p.x, p.y, p.z+11)`）在**两次命令之间自行掉 8 血**——该窗口内玩家无任何攻击，且远靶距玩家 11 格、距主靶约 9 格，均在 6 格溅射半径之外 → 判为环境/放置位置导致的伤害，使 `far_dealt=0` 断言失守，**非产品面**；随后原样复跑 PASS。用例的远靶放置对地形敏感，属用例健壮性问题（待加固）。
5. 探针新增/修正的 `ModEffectRemoval` 通道（§10 第 16 条）对 1.21.1 已记录的 PASS 无实质影响：1.21.1 的 `EFFECT-DECAY-FLICKER` 读数为 `dur=200`，按 `HealingManager#updateEffect` 的分支逻辑**只可能来自 `blessing == null`**，故「清赐福」在该次运行中是空操作。若要求字节级 JSON↔运行一一对应，需再冷启动一次 1.21.1 复跑（本次未做，等交接）。

### 8.2 产品面存疑项（只登记，未改任何产品代码）

1. **1.20.1 侧 `self` 读数被测试世界污染（原「施放者间歇自伤」；2026-09-15 定性为**环境**，不指向产品白名单）**：曾观测到一次 `RAILGUN-AOE-SCOPE-1.20.1` 的「落点箱内全体被劈」（`self=2→6`、`friendly`/`turtle`/`villager` 各掉 12），但**第 3 批冷启动未复现** —— 同一批 1.20.1 四例（AOE/HN/PE/OC）的 `friendly`/`turtle`/`villager` **全为 0**，只剩 `self>0`（AOE `11→16`、HN `6.67→12.67`、PE/OC 为 0）。该 `self` 已由旁证定性：① HN 两次 read 之间 `php` −6.0，而同一区间 `bolt_delta` 保持 3 未变（**区间内没有新雷击**）；② 该会话 `Dev被蜘蛛杀死了` 出现三次（11:00:03 / 11:03:35 / 11:04:32），1.21.1 同批零死亡行；③ 探针把玩家置 `forced_survival` 后，测试世界里一只**常驻蜘蛛**持续攻击玩家。⇒ `self`、以及据此计算的 `SCOPE_OK`，在 1.20.1 上不可信，属**测试世界脏**（探针开场清场按类型清僵尸/骷髅/尸壳/溺尸与苦力怕，但**不能按类型清蜘蛛** —— 蜘蛛正是探针自己的敌方靶），**未改任何产品代码**。静态面亦已排除：两版 mixin 配置同样列出 `LightningBoltStrikeScopeMixin`、1.20.1 `debug.log` 显示它已应用且无任何报错、`RailgunBolts`/`HostileTargets`/`LightningBoltStrikeScopeMixin` 三文件两版逐字节相同、`mark()` 先于 `addFreshEntity()`。⇒ **处置规则（2026-09-15 起强制）**：每次冷启动进入世界后、跑任何条目前先做一轮清场（`/kill @e[type=!player]` 或难度切换法），见 `AGENTS.md`「测试前清场」与本文档 §2。**待办（可选）**：若要让 1.20.1 的 `self` 可信 —— 换敌方靶类型（如 `minecraft:zombie`）并把 `minecraft:spider` 纳入开场按距离清场，或给自摆靶打 tag 后用 `tag=!…` 排除，或在无怪世界跑该用例。
2. **`HealingManager#updateEffect` 的赐福分支**：赐福在场时治愈图标时长被写成**赐福剩余时长**（实测 `/effect give … dice_blessing 600`（秒）→ `heal dur=119943`）。这是设计内行为（图标与赐福同寿命），但会让「层数递减类效果」在赐福期间**永远**处于「剩余 > 200」，即原版闪烁窗口在此期间不可达——测试侧构造该前置时必须显式清赐福。
3. **本模组效果的移除被自身拦截**：`ModEffectEvents#onModEffectRemovalPrevented`（`EventPriority.HIGH`）取消玩家身上 `astral_dice:*` 效果的一切**普通**移除，含原版 `/effect clear`、牛奶，以及第三方/脚本的直接 `removeEffect`；只有 `ModEffectRemoval`（内部标志）、`EffectTimerGuard`（强制标志）、死亡三条通道放行。同族风险：任何「让玩家或其他模组清掉本模组效果」的诉求都会被**静默拒绝**（无日志、无反馈），排查者容易误判成「效果清不掉」。本次未改产品代码，仅登记。
4. **`ModEffects.X` 的 API 形态跨版本不对称**（测试侧已适配）：1.21.1 是 `Holder<MobEffect>`、1.20.1 是 `RegistryObject<MobEffect>`（探针必须 `.get()`），漏写会抛 `Could not create ID from 'RegistryObject…'` 并中断整条探针命令。

---

## 9. 静态守门（每次改动后必须全绿）

```powershell
pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir neoforge-1.21.1/src/main/resources/assets/astral_dice/lang
pwsh -NoProfile -File tools/check_lang_sync.ps1 -LangDir forge-1.20.1/src/main/resources/assets/astral_dice/lang
pwsh -NoProfile -File scripts/audit/tooltip_color_audit.ps1 --root .   # R1/R1b/R2/R3 + R0(回落码=行底色)/R4(%% 必须走 tt())
pwsh -NoProfile -File scripts/verify/verify_content_library.ps1
pwsh -NoProfile -File scripts/verify/verify_chip_recipes.ps1          # java / gen / jar 三档
pwsh -NoProfile -File scripts/verify/verify_chip_acquisition.ps1
pwsh -NoProfile -File scripts/verify/verify_bountiful_pools.ps1
pwsh -NoProfile -File scripts/verify/verify_bountiful_instance_exclusions.ps1
pwsh -NoProfile -File tools/check_mod_sources.ps1                    # 模组来源统一口径(Curse/Modrinth Maven);阶段 P 的「模组来源」一项共用本脚本
```

> ⚠️ `verify_content_library.ps1` 依赖 `docs/1.2.0-content.json`，该文件当前**被 .gitignore 排除**（详见 §11）。干净克隆下该守门脚本会因缺文件而无法运行。

---

## 10. 已知坑与规避（全部为实测结论）

1. **Gradle 守护不退出**：`gradlew` 必须带 60s 看门狗；超时后按「日志含 BUILD SUCCESSFUL + 产物时间戳更新」双重验证，再强杀进程树。用 `mt_build.ps1` 即可。
2. **`fileHashes.lock` 拒绝访问**：是上一个守护进程占锁，不是编译错误；只杀 gradlew wrapper 与 Gradle daemon 两类 java 进程，**绝不误杀 Minecraft 客户端**。
3. **runData 遮蔽**：`run/<版本>/mods/astral_dice-*.jar` 会与 `build/classes` 同时加载，遮蔽新代码且不报错；生成前先移出旧 jar，比对生成物时间戳与类内新增字面量。
4. **run-id 粘性**：`mt_report mark` 复用 `.mt_run_state.json` 里的 `run_id`。要开新 run：备份并删除 `scripts/test/cases/.mt_run_state.json`（只删 `.mt_active_run` 不够）。
5. **`/kubejs reload server-scripts` 不重绑命令 lambda**：探针改动必须冷启动（见 §6）。
6. **测试世界规则**：`mt_env world` 强制写入 `allowCommands=1` 与 `GameRules.keepInventory="true"`（新建与种子恢复两条路径都写）；任一规则缺失即 `MT_WORLD: BLOCKED`（退出码 11）。缺 `keepInventory` 会让测试中死亡掉落物品、实验反复被打断。
7. **逐条结果必须自动入报告**：`mt_case.ps1` 执行完每条即调用 `mt_report mark --case`；漏记会让 `summary` 恒显示「未执行 / 0 条目」并把全绿误判为失败。
8. **脱离执行**：见 §3 末尾（Start-MtDetached）。
9. **DevLaunch 的客户端命令行不是原版形态**（2026-09-15 实测，导致 `mt_case` 拒绝运行 + `mt_stop` 杀不掉）：1.21.1 客户端命令行只剩 `net.caffeinemc.sodium … net.minecraft.client.main.Main`（≈56 字符，无 classpath、无版本目录）；1.20.1 客户端命令行里**根本没有** `net.minecraft.client.main.Main`（走 `cpw.mods.bootstraplauncher.BootstrapLauncher`，只含 `forge-1.20.1` 而不含 `:forge-1.20.1:` 或 `run\1.20.1`）。凡「按命令行含 `Main` 判客户端」的判据都会漏判。现行判据是 `Mt.Proc.psm1` 的 `Test-MtClientProcess`（窗口标题含版本号 **或**（`net.minecraft.client.main.Main`/`bootstraplauncher`）且（DevLaunch 标记 **或** 版本+子项目））；新增任何进程判定必须走它。
10. **`mt_launch` 假阳性 `MT_LAUNCH: OK`**（2026-09-15 实测后修复）：旧实现用 `-ErrorAction SilentlyContinue` 删 `latest.log`，且只认「已进入世界」这一行文本 —— 当**另一个版本/别人的客户端**正在运行时，删除会失败（句柄被占）却静默吞掉，随后读到的是**别人的 `latest.log`**，于是误判本机启动成功并断言到错误日志。现行实现：① 启动前若全机存在任一客户端进程即 `BLOCKED`（exit 11）；② `latest.log` 删不掉即硬失败（exit 11），启动前必须不存在；③ 只接受 `CreationTime` 晚于本次 `$launchStartedAt` 的 `latest.log` 里的「已进入世界」行。
11. **`performPrefixedCommand` 的返回值跨版本不一致**：1.21.1 上返回 `undefined`（命令**其实已执行**，探针日志照常出现），1.20.1 上返回 `0/1`。探针判定一律**不得**以返回值或 `rc=` 为准，改读聊天栏 `AP_` 行。
12. **1.21 起 `data get` 的 NBT 键变小写**：实体属性键 1.20.1 为 `Attributes`、1.21.1 为 `attributes`；跨版本断言不要依赖 NBT 键名（改用聊天栏读数，如 `的属性护甲值的基值已设置为 20.0`）。
13. **近战横扫 / 着火的混淆**（2026-09-15 实测）：把中立/宠物靶放在 ~1.5 格内，会被玩家的**近战横扫**打到（与雷击无关，判据是「伤害在雷击落下**之前**就已出现 + 靶 `Fire=-1`」）；雷击点燃草地也会给出额外伤害（曾被误算成施放者自伤 `self=18.83`）。规避：靶移到 **2.8 格**（出横扫箱、仍在 ±3 的雷击箱内），并加 `/gamerule doFireTick false` + 石地板。
14. **断言窗口与偏移（B7 起已**结构性解决**，旧绕法作废）**：`log`/`absent` 的窗口由 `scope` 决定（缺省 `case` = 自**本用例**开始），偏移**由用例执行器在每条用例开始时自动刷新**（`mt_case.ps1` 调 `snapshot --window case`），**不再需要人工在每条用例前跑 `snapshot`**。历史症状（偏移陈旧，如 1.20.1 残留 `@96917B` ⇒ 早期行被判「不存在」⇒ 假 FAIL）与「共用 launch 窗口 ⇒ 前序用例把断言喂饱 ⇒ 假 PASS」两端都已消除；**注意反向断言**：`absent` 收窄后只检测本用例窗口，凡「文本不是本用例产生」（尤其启动/数据包解码期文本）必须显式 `scope: whole`。表外日志源（如 `loadergate.log`）偏移恒为 0 = 整文件。
15. **1.20.1 探针里 `ModEffects.X` 必须 `.get()`**：1.20.1 是 `RegistryObject`、1.21.1 是 `Holder`；漏 `.get()` 会在运行期抛 `Could not create ID from 'RegistryObject…'` 并**中断该条探针命令**（实测 1.20.1 `fensplash` 第 2265 行），表现为该相位所有 `AP_` 行整段消失 —— 必须与前一条「命令未执行」的排查区分开。
16. **`HealingManager#updateEffect` 的骰神赐福分支**（2026-09-15 实测，`EFFECT-DECAY-FLICKER` 首次运行失败的真正根因）：治愈图标时长在**赐福在场时等于赐福剩余时长**（`HealingManager` 的 `blessing != null` 分支），与治愈计时器无关；因此只要玩家身上残留 `dice_blessing`（前一用例触发过赐福即可），`endsWithin(200)` 的原版闪烁窗口**不可能**成立，`_WINDOW:0` 与本模组修复无关。构造该窗口的探针命令必须**先 `removeEffect(DICE_BLESSING)`** 再 `setHealingTimerEnd(now+200)` + `add`；仅靠 `/effect clear @s` 或 `decayclear`（只清治愈点数/计时器/三类效果）**不够**。判定「赐福是否在场」可读 `AP_<tag>_SETUP:…:blessed_before=`。
17. **探针里禁止对「多同元重载」的 Java 方法做对象比较（Rhino 重载歧义；2026-09-18 t19 实测根因）**：KubeJS 的 Rhino（`dev.latvian.mods.rhino`）是**运行期**按「JS 侧类型信息」选重载的，不像 javac 有编译期静态类型；当被调方法有多个**同元**重载、且其中含**接口**参数时，判定会落空并**直接抛错、不调用**（`NativeJavaMethod.preferSignature` 返回 `PREFERENCE_AMBIGUOUS` → `msg.method.ambiguous`）。实测：`ItemStack.is(...)` 在 1.21.1 有 5 个同元重载（`Item`/`TagKey`/`Predicate`/`Holder`/`HolderSet`）、1.20.1 有 4 个，探针里写 `st.is(pool.get(j).getItem())` 报
    `InternalError: The choice of Java method net.minecraft.world.item.ItemStack.is matching JavaScript argument types (…EffectCardItem) is ambiguous; candidate methods are: boolean is(net.minecraft.core.HolderSet) / boolean is(net.minecraft.world.item.Item)`。
    **症状极易误读**：`AP_<tag>_EX:` 之后该相位所有 `AP_` 行**整段消失**（看起来像「命令没执行 / 相位没到达」），且 `MT_ASSERT_KUBEJS`/`CRASH` 全绿。**规避（口径）**：① 物品/方块/实体等**注册表对象一律按注册名比较**（探针既有的 `itemIdOf(stack)` = `BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()`），而不是 `is(...)`/`==`；② 需要等语义时先取**无重载**的取值方法（如 `getItem()`）再按注册名比；③ 任何 `try/catch` 都**不得**用来吞掉 `InternalError`（那会掩盖同一类缺陷）；④ 新增探针调用若目标方法有多个同元重载，必须显式说明消歧依据。排查入口：`Select-String -Path scripts/test/resources/kubejs/<版本>/server_scripts/*.js -Pattern '\.is\('`（逐条判断候选是否唯一可接受）。
17. **探针收尾命令 `railgunfriendlyend` 的注入偶发丢失**（2026-09-15 两次实测：`RAILGUN-OVERRIDE-CLASS-1.21.1` 首轮缺 `AP_OC_RESTORE:creative`、`HOSTILE-TARGET-NEUTRAL-1.20.1` 缺 `AP_HN_RESTORE:creative`）：表现是**该轮读数全部正常、唯独收尾读数一行都没有**，用例因 `RESTORE` 断言 FAIL，极易被误当成产品问题。
    - **历史处置（已废止）**：当初把 8 个探针用例的 `railgunfriendlyread` / `railgunfriendlyend` 统一改为**连续注入两次**（中间 `wait`）兜冷启动丢注入。
    - **现行处置（2026-09-15 B6 ①，已实跑回归）**：**删掉这些第二次注入**（共 16 次，≈42 s）。依据：这两个相位位于该用例第 4～7 次注入之后，冷启动期早已过去；且 `mt_inject.ps1` 每次注入前都做 `Esc→Tab→Enter` 状态归一化 + 强制前台（`:497-502`、`Assert-MtInjectForeground`），冷启动丢注入的前提（界面未收敛 / 窗口未获焦）此时不成立。`railgunfriendlyread` 通篇只有读 + `send`（幂等）；`railgunfriendlyend` 第二次进入时 `rgfState` 已置 `null`，只剩重复清场与重复报 `RESTORE`/`DONE`，无增量副作用。
    - **不要再按"一律注入两次"写新用例**：重复注入只对**真正处于冷启动窗口**的首批命令有意义；其余位置重复只是白花 ≥2.65 s/次。
    - 同族：**在同一会话内重跑同一用例会误伤 `absent` 断言**（快照按 launch 建立，粒度是会话不是用例）—— 重跑前先 `stop` → `launch` 重置快照。
    - 同族实测（2026-09-15，`mt_launch` 的测试前清场）：`MT_INJECT_CMD` 打印了**两次**，而 `latest.log` 里只有**一条** `/kill` 反馈（1.20.1 = `杀死了94个实体`；1.21.1 两次都在 = `9` / `6`）—— 即「注入函数跑完并打印」**不等于**「命令一定被客户端执行」。凡结论依赖「某条命令确实执行过」的场景，要么读原版反馈行，要么双发。
18. **`RAILGUN-AOE-SCOPE` 的断言集缺「防误伤 / 范围」项（2026-09-15 复核断言集时发现；同日用户裁决「方案①」，已实施并**经冷启动复跑确认**，见本项末）**：该用例名叫「电磁炮雷击命中范围取证」，但 13 条断言里只有 `AP_RG_VERDICT:.*:enemy=1:` + `absent …:enemy=0:` 覆盖「敌对标确实被劈」，**没有** `friendly`/`turtle`/`villager` 为 0 的断言，也没有 `SCOPE_OK:1`。后果很实在：**设计万一失效（雷击波及落点箱内全部实体），本用例照样 PASS** —— 实测出现过 `self=2→6:friendly=12:turtle=12:villager=12:SCOPE_OK:0` 而本用例断言全绿（当时把它当成「产品疑点」排查，其实是断言没覆盖）。补强方案（改完需一次冷启动复跑）：① 补正断言 `AP_RG_AFTER:.*:friendly=0:turtle=0:villager=0:valive=1:talive=1`（`friendly`→`talive` 在 AFTER 行里本就相邻，无需插 `.*`）、补 `AP_RG_VERDICT:.*:neutral=1:`，并照抄 `RAILGUN-OVERRIDE-CLASS` 的反断言写法；② 在 ① 之上再给 1.21.1 追加 `AP_RG_SCOPE_OK:1` 与反断言 `AP_RG_VERDICT:.*:self=[1-9]`。**1.20.1 不建议直接断言 `self=0` / `SCOPE_OK:1`**：`self` 是**施放者 HP 的原始差值**（`dealt(st.php, php)`，不区分伤害来源），测试世界里常驻的敌对生物（实测蜘蛛近战使 `php −6.0`，同期 `bolt_delta` 未变）会让它偶发非 0 —— 那是环境脏，不是产品白名单问题。若要让两版都能断言 `self`，须先三选一：把 `spider` 加进 setup 的**既有**敌对生物清场（⚠️ 1.21.1 命令延迟到 tick 末生效，清场必须排在自己摆靶之前的**不同 tick**，否则会清掉自己刚摆的靶，旧伤见 `6479603`）、或给探针自持靶打 tag 后用 `tag=!…` 反选清场、或把该用例挪到无怪世界运行。
   - ✅ **2026-09-15 用户裁决「方案①」并已实施**：1.20.1 断言 13→15、1.21.1 断言 13→16 —— 两版新增正断言 `AP_RG_AFTER:.*:friendly=0:turtle=0:villager=0:valive=1:talive=1` 与 `AP_RG_VERDICT:.*:neutral=1:`，反断言扩为 `…:enemy=0:|…:friendly=[1-9]|…:turtle=[1-9]|…:villager=[1-9]|…:valive=0|…:talive=0`；1.21.1 另加 `AP_RG_SCOPE_OK:1` + 反断言 `AP_RG_SCOPE_OK:0|AP_RG_VERDICT:.*:self=[1-9]`，1.20.1 侧则按上面的理由**刻意不**断言 `self`/`SCOPE_OK`（用例 note 已写明「施放者不自伤」由 1.21.1 同名用例承担）。**离线预检（把新正则喂给上一轮绿色批次的原始行）**：新正断言全部命中、新反断言 0 命中；再把那轮「箱内全体被劈」的异常读数喂进去 → 正断言不命中、反断言命中 ⇒ 两个方向都符合预期。改断言属测试资产变更，**仍需一次冷启动复跑**才算了结 —— **复跑确认（同 HEAD `09a34e5`，12:39 / 12:41 双版本各冷启动一次）**：1.20.1 15/15 PASS、1.21.1 16/16 PASS（0 FAIL / 0 ERROR），新正断言全部命中、新反断言 0 命中；读数 `self=0`、`friendly`/`turtle`/`villager` 全 0、`SCOPE_OK:1`、`bolt_delta` 1.20.1=`3`（两次 read 均 3）/1.21.1=`2`（两次 read 均 2）。⚠️ 1.20.1 本轮 `self=0` 只说明**该轮没有生物来打玩家**，不等于该版 `self` 已可断言 —— §8.2-1 那只常驻蜘蛛的间歇性并未消除。
19. **探针里取物品必须用全限定 id(`resolveItem`)**（2026-09-15 实测,`AIRBAG-BYPASS-KILL` 首轮 FAIL 的根因）:`resolveItem("airbag_chip")` 会被 `ResourceLocation.parse` 当成 `minecraft:airbag_chip` → 返回 null → 探针打印 `AP_<tag>_ERR:unknown_item:…` 并**中止该条命令**,表现为该相位**除 `_DONE` 外一条读数都没有**（DONE 由外层 guard 之外的分支照常发出）,极易被误读成「产品没生效」。凡新增探针命令取物品一律写 `astral_dice:<id>`（既有 `equipslot chip "astral_dice:…"` 即此约定）。
20. **1.20.1 的 Curios 查询必须经 `resolve()`**（同族平台差异,同日实测）:`CuriosApi.getCuriosInventory` 在 1.20.1 返回 `LazyOptional`,套用 1.21.1 的 `opt.get()` 会抛 `TypeError: Cannot find function get in object net.minecraftforge.common.util.LazyOptional@…`;因为探针把该字段包在 try/catch 里,读数只剩 `chip=<err:…>` 而**其余字段全部正常**,很容易被误判成「气囊没装上」这类产品问题。正确写法(与既有 `diceSlotItemId`/`ensureChipSlot` 一致):`opt.isPresent()` 判空 + `opt.resolve().get().getStacksHandler(...)`。
21. **测试中玩家真的死亡时,工具链会「自行恢复」,但期间读数不可信**（2026-09-15 实测,`AIRBAG-BYPASS-KILL` 首轮）:该轮因装备步骤失败,玩家被 `kill()` 打死（`health=0:alive=0`),而**下一次注入的按键（含回车）落在死亡界面的「重生」按钮上**,约 4 秒后的读数变成 `health=20:alive=1`（推断机制如此——未逐帧取证;`keepInventory=true` 使饰品槽内容保留,可作为旁证）。含义有二:① 「玩家死亡」**不会**让后续注入全部失效,不要据此断言用例必然卡死;② 但死亡与重生之间的读数一律不可信,凡要求玩家存活的用例必须**自证前置条件**（本用例读 `equipped=` / `charge=` / `chip=`）,并保留 `absent …_ERR` 反断言把「前置条件没建立」直接判 FAIL——否则「玩家没被致死」会被当成「气囊生效」。

22. **`Start-Process … -Wait` 会等整棵进程树 ⇒ 全流程必然卡在 build 阶段**（2026-09-15 B6 ⑥ 实测，**本轮三位执行者卡死的直接根因**）：`mt_build.ps1` 在需要时会让 `gradlew` **新起一个 Gradle 守护**（wrapper 的后代）⇒ 构建早已打印 `MT_BUILD: OK` 并退出，而 `mt.ps1` 的 `-Wait` 一直阻塞到守护退出。**触发条件**：`--phase stop --force` 杀掉守护之后的下一次全流程（守护不在场 ⇒ 本次必须新起 ⇒ 它成为后代）。**实测特征**：最后一行输出停在 `MT_BUILD: OK (4s)`，找不到任何 `mt_env`/`mt_report` 子进程，Gradle 守护仍活着，`mt.ps1` 永不返回。**现行处置**：`Invoke-MtChild` 的非脱离式分支**一律不用 `-Wait`**，改为「`-PassThru` 轮询 `HasExited`」+ `$TimeoutSec` 上限（超时只终止该子进程自身，**绝不** taskkill 进程树）。这与 A1 记录在 `launch` 阶段踩到的是**同一个坑**，只是这次落在 `build`。
23. **超时是独立结论，不得与 FAIL/ERROR 混同**（2026-09-15 B6 ⑥）：单条用例硬超时记 **`TIMEOUT`**（退出码 12），`mt_report` 汇总把它与 `FAIL` 分开列；`--run-timeout` 全局超时同样退出码 12。若把超时并进 FAIL，工具链/环境故障会被读成产品缺陷。

24. **`MT_PHASE: stop` 首轮报 `RESIDUAL` 属正常收停时序，不代表残留**（2026-09-15 B7 双版本实测两轮复现）：全流程收尾打印
    `MT_CLEANUP: 警告 — 仍有 1 个本流程进程未退出：[<pid>]` + `MT_CLEANUP_RESULT: RESIDUAL` + `MT_WARN: STOP: 收停后仍有残留`，
    而紧随其后的 `MT_PHASE: cleanup (auto)` 打印 `MT_CLEANUP: OK (1s) — 退出清理完成，无本流程残留进程`。
    **判据**：以**自动清理阶段**的结论为准，并用 `Get-Process java` 复核（两轮实测均无 java 残留）。原因是 JVM 收到终止请求到真正退出存在数百毫秒的窗口，`stop` 相位不等它。
    **不要**据此认为「没停干净」而手工 `taskkill`（会误伤其它 java 程序，见 §12 的安全红线）。

25. **目标选择器边框的「可见外框」是逐帧实测，两处退化属**已登记边界**（2026-09-18 t23 登记，**不改代码**）**：`TargetOutlineCapture.outlineOf(entity)` = 碰撞盒 ∪ **本帧实测**模型外框（`RenderLivingEvent.Post` 里把该实体当前姿态的模型画进「只记坐标」的 `VertexConsumer` 探针取 min/max，天然含部位动画旋转、幼年体内缩放与 `SCALE` 属性），边框再 `inflate(线宽/2)` ⇒ **正常（准星命中 / 该实体已被渲染过至少一帧）时绝不小于可见模型，绝不内缩**。两处退化：① 渲染器各自覆写的 `scale()` / `setupRotations()` 分支（充能苦力怕 2× 脉冲、死亡/睡眠/旋转攻击姿态等）无法在该类里重放 ⇒ 取「碰撞盒 ∪ 实测外框」；② **从未被渲染**的实体（视锥外 / 被其它模组取消渲染）没有实测值 ⇒ 退化为碰撞盒（此时该实体自己也**不在画面上**）。**判定口径**：看到「旁侧未渲染目标的框小于其模型」**不要**当同类缺陷重开 —— 先确认该实体是否曾被渲染；外框逐帧更新，实体进入画面即恢复。源码内注释见 `TargetOutlineCapture.java:52-55`、`:93-94`；口径文档同步写在 `AGENTS.md`「目标选择器规范 → 现行口径」第 11 条。

26. **「选择期间滚轮拦截」已自动化（2026-09-18 t23/F3 落地，选项①「补自动化」；不再只作代码复核）**：该语义此前只有源码级复核（`TargetSelectionClient` 的 `InputEvent.MouseScrollingEvent` 分支），本轮补上**端到端**验证，三件套齐备：
    - **注入原语**：`mt_inject.ps1 mouse --button wheel [--notches n]`（缺省 `-1` = 向下滚一格，`|n| ≤ 32`、不得为 0）。`sendinput` 通道走**真实** `SendInput(MOUSEEVENTF_WHEEL, mouseData = 格数 × 120)`；`postmessage` 通道走 `PostMessage(WM_MOUSEWHEEL, HIWORD = 带符号 delta, lParam = 窗口中心)`（干跑可逐行比对）。`mt_case.ps1` 的 `inject_mouse` 字段表本就按名放行 `button` 并**原样转发**，故用例直接写 `{"op":"inject_mouse","button":"wheel"}` 即可，**不需要**改 `mt_case.ps1`。
    - **读数**：探针（两线同构）新增只读 `selectedSlot` = `player.getInventory().selected`（0..8），即「滚轮是否被拦下」的**唯一可观测面**（拦截是纯客户端行为，不产生任何调试日志）。原版语义：会话外向下滚一格 ⇒ 槽位 **+1**（`MouseHandler.onScroll` → `swapPaint`，缺省 `notches=-1`/`sensitivity=1`）；会话内被取消 ⇒ 槽位不变。
    - **用例与断言**：`cases/SELECTOR-KEYS-1.21.1.json` 的 **⑥-a**（会话激活：热键 `3` 置位 ⇒ `AP_W0_DIAG:selectedSlot=2`，滚一档 ⇒ `AP_W1_DIAG:selectedSlot=2` = 拦截成立）与 **⑥-b**（会话已取消的**正对照**：`3` ⇒ `AP_W2_DIAG:selectedSlot=2`，滚一档 ⇒ `AP_W3_DIAG:selectedSlot=3` = 滚轮恢复生效），四条均为 `type=log` + `source=latest`。**为何必须两侧成对**：单看 W1「没变」无法区分「拦截生效」与「滚轮注入根本没进游戏」（假阳性）⇒ W3 必须变；W0/W2 是**前置置位断言**，排除「槽位本来恰好就是 2」这种巧合证据。⑥-a 插在 `/tp` 之前，原「`/tp` + 左键确认」次序保持不变。
    - **结构校验**：本用例 `esc_sensitive: true` ⇒ **全部 26 个注入步骤**（含该轮新增的 8 个对照步骤）都必须声明 `no_esc`，`pwsh -File scripts/test/mt_case.ps1 validate --case cases/SELECTOR-KEYS-1.21.1.json` → `MT_VALIDATE: OK`。

27. **「探针正常」类断言必须用能区分成功/失败的读数（2026-09-18 t23/F2 口径）**：断言只写前缀（如 `AP_SK_DIAG:levelClass=`）时，读数**退化为失败载荷**也一样 PASS ⇒ 该断言名存实亡，**禁止**再用这种写法证明「探针/客户端可用」。现行口径（`SELECTOR-KEYS-1.21.1` 已按此收紧）：正断言 `AP_SK_DIAG:lvlDataGGT=\d+`（拿得到数值才算正常）+ 反断言 `absent AP_SK_DIAG:lvlDataGGT=ERR:`（探针一旦失败即当场 FAIL）。
    ⚠️ **本环境已实测「恒为失败载荷」的读数（禁止用于断言；2026-09-18 t27 补注跨线差异）**：`levelClass`（**1.21.1 侧**不可见 ⇒ `ERR:TypeError: Cannot find function getClass in object ServerLevel[…]`；改 `java.lang.Object#getClass` 反射句柄亦拿不到 ⇒ `no-method`；**但同一行代码在 1.20.1 侧可取** —— 实测 `AP_W0_DIAG:levelClass=net.minecraft.server.level.ServerLevel`，`run/1.20.1/logs/latest.log:776`）、`getGameTime`（两线均 `ERR:TypeError: Cannot find function getGameTime in object ServerLevel[testworld].`）、`reflectGGT`（两线均 `no-method`）。它们**保留在探针里**（源码内已加 ⚠️ 注释），因为其成败本身是「Rhino 成员可见性」的证据；但**不得**作为任何 pass 条件，也**不得**跨线直接对比。可用的同级成功读数：`lvlDataGGT` / `getDayTime` / `srvTickCount` / `nowTickVal`(+`nowTickSrc`)。

28. **1.20.1 探针的 Rhino 陷阱：禁止对 `stream()…toList()` 的返回值直接调成员（2026-09-18 t27 修复 T14-F1，实测定性）**：`RandomCardHandler.getCardPool` 用 `items.stream().map(ItemStack::new).toList()`（`RandomCardHandler.java:171`，三线同源）；Java 16+ 的 `Stream.toList()` 返回 JDK **包私有**内部类 `java.util.ImmutableCollections$ListN`。1.20.1 侧的 `rhino-forge-2001.2.3-build.10` 成员分派经 `MemberBox` **反射调用** ⇒ 对另一模块（`java.base`）里的包私有类直接抛
    `AP_G1_EX:JavaException: java.lang.IllegalAccessException: class dev.latvian.mods.rhino.MemberBox (in module rhino) cannot access a member of class java.util.ImmutableCollections$ListN (in module java.base) with modifiers "public"`
    （复现：冷启动后注入 `/astralprobe sggate G1`；实测原文 `run/1.20.1/logs/latest.log:773`）⇒ `AP_G1_PREP/GATED/CANCEL/CONFIRM/VERDICT/DONE` **整段读数缺失**、`SELECTOR-GATE-1.20.1` 的 9 条断言只剩 2 条能过。这是**探针/工具链侧缺陷，不是模组或移植缺陷**——1.21.1 侧 `rhino-2101.2.8-build.91` 已修该路径，**逐字节同源**的探针代码在 1.21.1 全 PASS。
    - **现行口径（两线探针同形遵守）**：凡生产入口返回的 `List`/`Collection`，**先复制成 `java.util.ArrayList`** 再做任何成员调用 —— `var ArrayListClass = Java.loadClass("java.util.ArrayList"); var pool = new ArrayListClass(EffectCardUtilClass.getRandomEffectCardPool());`（构造函数声明在**公开类**上、复制由 Java 侧在构造函数内部完成，不经 Rhino 成员分派）。**禁止**改用 `pool.toArray()/iterator()/size()/get()`：它们同样声明在包私有类上，走 Rhino 一样抛。语义不变（同池、同元素、同顺序，每次一份新副本）。
    - **同一根因家族的三个成员**（症状都是「该相位 `AP_*` 读数整段缺失」，排查时先找有没有一条 `_EX`/`_ERR` 行）：成员分派对 JDK 内部类的非法访问（本项 · `ImmutableCollections$ListN`）／重载分派歧义（第 17 项 · `ItemStack.is(...)`）／类过滤器拒绝（`Java.loadClass("java.io.FileWriter")` ⇒ `ERR:InternalError: Class is not allowed by class filter!`）。`java.util.*` 不在拒绝名单内（既有脚本已在用 `java.util.Collections`）。

29. **截图取证口径：优先 `mode=f2`，跨秒差分不可用于像素量化（2026-09-18 t27 入档；证据来源 = t14 报告 T14-F2，非产品缺陷）**：t14 实测两条 —— ① `mode=window` 在**游戏窗口非前台**时抓到的是 **3840×2160 的整个桌面**（含 DSH/Harness 窗口遮挡 + 150% DPI 重采样）：边框顶点色被重采样后与预期色不再匹配、右半屏被别的窗口盖住 ⇒ **像素级判定直接失效**；改用 `mode=f2`（游戏自身帧缓冲落盘，1920×1080 干净帧）后逐例判读成立。② 同一相机下**相隔数秒**的「基线帧 / 带生物帧」差分会被**云层漂移**污染：噪声地板实测 **26384 px**，远大于阈值 **40** ⇒ 任何「跨秒帧差 ≤ 阈值」的覆盖性判据**不可用**。
    - **现行口径**：覆盖性/存在性结论采用**「读数算术 + 人工判读」双轨** —— 可机器判定的部分一律走探针读数与 `log`/`absent` 断言，画面本身交人工看 `mode=f2` 截图；不要把桌面级截图拿来做像素量化。

30. **两线 `SELECTOR-KEYS` 用例口径差异登记（2026-09-18 t27 关闭 T14-F3）**：两线 26 个注入步骤逐项一致、均声明 `no_esc`；**等价断言的正则已同形**（`\\[Astral Dice\\]\\[TargetSelection\\] cancel token=\\d+ player=` 等 16 条逐字节相同 —— 1.20.1 侧自 2026-09-18 t27 起同样带 `[Astral Dice]` 前缀，与真实日志行一致，见 `run/1.20.1/logs/debug.log` 的 `[Astral Dice][TargetSelection] cancel token=… player=Dev`）。**唯一保留的跨线差异 = 1.20.1 用例多一条 bounds 断言**，属平台事实、**不可同形化**：该读数行由**模组行为代码**打印，`neoforge-1.21.1` 是 `TargetOutlineCapture.java:177` 的 `entity={} scale={} baby={} collision={} model={} used={}`，`forge-1.20.1` 是 `:197` 的 `entity={} baby={} collision={} model={} used={}`（1.20.1 无 `getScale()`，源码内注释见 `:69`、`:175`）⇒ 1.20.1 侧断言只能写成不含 `scale=` 的形式。**禁止**为「看起来一致」去改任一侧的日志格式（那是行为代码）。

---

## 12. 超时机制与看门狗（2026-09-15 B6 ⑥；**2026-09-16 收紧为严格预算**，长流程必须遵守）

> **2026-09-16 用户裁决：「现有情况下不允许长时间等待，请重写控制脚本，严格控制等待时间，并完善监视器」。**
> 触发事故（实测）：一次 `--phase cases` 在 launch 正常结束后**零输出空转 7 分 45 秒**，期间
> ① 子进程预算是 600/900 秒、全局预算「0 = 不限」；② 等待期间**一行输出都没有**；
> ③ 看门狗一路报 `ALIVE（有进展）` —— 因为它的判据是 `runclient_launch.log` 的**大小/mtime**，
> 而游戏空闲时 ModernFix 仍会**每分钟**吐一条 `[Worker-ResourceReload-N/DEBUG] … shutdown`。
> ④ 事后**无法**从日志判定它卡在哪一步（该阶段输出在被截断的管道里）。
> 以下四条改动分别消灭这四点。

### 12.1 分层硬预算（默认值与覆写）

| 层 | 实现 | 默认 | 覆写 | 超时后的行为 |
|---|---|---|---|---|
| **单条用例** | `mt_case.ps1` 的 `$script:CaseTimeoutSec` | **180 s** | `MT_CASE_TIMEOUT_SEC` / `--case-timeout <秒>`（`0` = 关闭） | 该条记 **`TIMEOUT`**，**跳过剩余步骤**并**继续跑下一条**；`run-dir` 返回退出码 **12** |
| **单阶段子进程** | `mt.ps1` 的 `Invoke-MtChild -TimeoutSec` | 见下表（120~300 s） | `MT_<阶段>_TIMEOUT_SEC`（如 `MT_ENV_TIMEOUT_SEC=600`） | 终止**我们自己那个**子进程（绝不动进程树）→ 打印卡点 → 返回 **12** |
| **cases 阶段** | `mt.ps1` 自适应推导 | 单条 = `单条上限 + 90 s`；`run-dir` = `90 s × 用例数 + 90 s` | `MT_CASES_TIMEOUT_SEC` | 同上；`CASES_BUDGET:` 行在阶段开始时打印实际预算 |
| **全局运行** | `mt.ps1 --run-timeout <秒>` | **2700 s**（旧默认「0 = 不限」已废除） | CLI / `MT_RUN_TIMEOUT_SEC` | 走 `mt_stop.ps1 --force` 收停 → 报告写 `TIMEOUT` → 退出码 **12** |

| 阶段 | preflight | build | env | launch | report | stop | cleanup |
|---|---|---|---|---|---|---|---|
| 默认预算 | 120 s | 300 s | 240 s | 180 s | 90 s | 150 s | 180 s |

单条用例的预算由**两层**共同保证，缺一不可：
① 步骤循环在**每步之前**核对 deadline，超时即跳出循环（不再发起新动作）——
   且 `CaseDeadline` 现在**先于「本用例窗口快照」建立**（旧实现把 deadline 设在快照之后，
   于是那次快照调用退化到 600 s 兜底值，用例自身的硬超时形同虚设——已修）；
② `Invoke-MtCaseChild` 把**剩余预算**折算成子进程超时（`Get-MtCaseStepBudget`，下限 5 s），
   `Invoke-MtProcessFull` 超时会**强杀该子进程树** ⇒ 超时路径不留挂在等待里的子进程/句柄。

### 12.1.1 等待期的可见性（心跳与进度信标）

- **`MT_WAIT:` 心跳**：`mt.ps1` 等待任何子进程期间，每 10 s 打一行，含「脚本 / 已等待 / 硬上限 /
  子进程 CPU（非脱离式）或日志字节数与最后增长时刻（脱离式）」。脱离式（launch）还额外判定
  **日志 90 s 零增长 ⇒ `CHILD: STALL`**（进程存活 ≠ 有进展），放弃等待终态标记并返回 `TIMEOUT`。
- **进度信标 `cases/.mt_progress.json`**（`Set-MtProgress` / `Get-MtProgress`，实现在 `lib/Mt.Paths.psm1`）：
  `mt.ps1` 进入每个阶段、`mt_case.ps1` 每条用例开头与**每个步骤开跑前**写入
  `{ts, pid, phase, version, case, step_index, step_total, op, detail}`（原子写：临时文件 + 改名）。
  用途：**监视器与事后取证都只读这一处**即可回答「它现在卡在哪一步」。
  该文件以点开头，`cases/` 的 run-dir 用例发现按 `StartsWith('.')` 过滤，不会被当作用例（已有单测路径覆盖）。

`TIMEOUT` 的语义边界（**必须分清**）：`FAIL` = 断言不满足（产品可疑）；`ERROR` = 用例跑不起来（工具链/前置）；**`TIMEOUT` = 在预算内没有跑完**（可能是环境慢、注入卡住、客户端死了）。
⚠️ `TIMEOUT` **不**置位 `.mt_keep_alive`（否则 `mt_cleanup` 会拒绝收停、整套流程再无人清理）；它的现场诊断由进度信标 + `mt_watchdog.ps1` 的取证输出承担。

### 12.2 看门狗 `mt_watchdog.ps1`（可独立跑）

```powershell
pwsh -NoProfile -File scripts/test/mt_watchdog.ps1 -Version 1.21.1 [-StallSeconds 180] [-WarnSeconds 90] [-PollSeconds 10] [-IdleExitSeconds 60] [-Action report|stop] [-MaxSeconds N]
```

| 项 | 说明 |
|---|---|
| **进展判据 ①（最强）** | `cases/.mt_progress.json` 的**内容**（阶段/用例/步号/op/ts）——阶段、每条用例、每个步骤都会刷新它，因此它不变就是真没往前走 |
| **进展判据 ②** | `cases/.mt_run_state.json`、`cases/.mt_snapshot.json`、`cases/.mt_active_run`、当前活动报告目录下任何文件（大小 + mtime） |
| **进展判据 ③** | `run/<版本>/logs/latest.log` **尾部的语义标记**（`[CHAT]`、`AP_*:`、`Saving and pausing`、`logged in/out`、`joined/left the game`、`KubeJS Server/`、`已将截图保存为`…）。⚠️ **不再用「文件大小/mtime」，也不再认 DEBUG 行**——那正是旧判据被 ModernFix 每分钟一条的周期噪声骗过的地方 |
| **心跳** | 每 `-PollSeconds`（默认 10 s）打一行：`MT_WATCHDOG: ALIVE t=<秒> step=<阶段 用例 步/总 op> │ marker=<最后语义标记> │ client=alive|none（停滞 <秒>）` |
| **预警** | 停滞达 `-WarnSeconds`（默认 `min(90, Stall/2)`）⇒ `MT_WATCHDOG: WARN` + 卡点取证（**提前介入，不退出**） |
| **停滞** | 停滞达 `-StallSeconds`（默认由 360 收紧为 **180**）⇒ `MT_WATCHDOG: STALL` + 完整取证（进度信标原文 + 客户端/守护进程存活与 PID + 三个日志尾部） |
| **目标已结束** | 客户端进程已退出且再无进展达 `-IdleExitSeconds`（默认 60 s）⇒ 打印一行并**退出 0**（旧版此时会以莫名退出码结束，容易被误读成故障） |
| **动作** | `-Action report`（默认）只报告；`-Action stop` 额外调 `mt.ps1 --phase stop --force` 收停（该收停调用本身有 180 s 上限） |
| **退出码** | **42** = 检出停滞（独立值，与 `FAIL=1` / `ERROR=2` / `TIMEOUT=12` 都不同）；0 = 有进展 / 目标已结束 |
| **安全** | 收停**只**经 `mt.ps1 --phase stop --force`（唯一收停实现，按进程标记只杀本流程的客户端与 Gradle 守护）；本脚本**绝不**自己 `taskkill` 任何 java 进程 |

### 12.3 纪律（**硬要求**）

1. **任何「启动客户端 + 跑用例」的长流程，必须**由 `mt_watchdog.ps1` 包裹（`-Action stop` 更好），**或**至少显式设置 `MT_CASE_TIMEOUT_SEC`；**禁止**无超时地等待客户端标记 / 等待用例完成。
2. 遇到 **`TIMEOUT`** 时的处置：先读 `cases/.mt_progress.json`（卡在第几阶段/哪条用例/第几步/什么 op）与 watchdog 的取证块，再决定是重跑单条（`--case`）还是整轮；**不要盲目重试整轮** —— 先 `--phase stop --force` 清干净。
3. 遇到 **`MT_WATCHDOG: STALL`** 的处置：它只说明"没有进展"，不等于产品缺陷；按第 2 条的卡点定位后再跑。
4. **后台/长任务不得用会缓冲的管道**（2026-09-16 实测教训）：`pwsh … | Select-Object -Last N` / `| Select-Object -First N` 会把子进程输出**全部缓冲到命令结束**，一旦外层作业被截断或超时，**整段日志消失**，事后无从取证。后台跑一律用 `*> <文件>` 落盘，再从文件读；**禁止**用 `Select-Object` 截断长流程的输出管道。


---

## 11. 清理规范与覆盖缺口

**判定依据（可删 / 必须留）**

| 类别 | 判定 | 处理 |
|---|---|---|
| 已删除工具链的编译缓存（`__pycache__` 等） | 对应源文件已不存在 | 可删 |
| 空目录 / IDE 生成物（`.vscode/launch.json`、`build/`） | 不参与发布、可再生成 | 可删/忽略 |
| 历史运行报告 `reports/<旧运行id>/` | 证据价值随时间衰减 | 保留最近一次 + 模板，其余可删 |
| `temp/` 会话期探针与验证脚本 | 一次性 | 可删（2026-09-15 已整体清空；旧脚本归档包已移至 `docs/archive/legacy_scripts_20260912.zip`） |
| **KubeJS 探针、测试世界种子包、回归条目 JSON** | **用例可复现性依赖** | **必须入库，禁止删除** |
| `run/<版本>/mods`、`run/<版本>/kubejs` | 下一次运行的现成环境 | 保留 |
| **测试世界**（`run/<版本>/saves/<世界名>`、`run/<版本>/<世界名>`） | 全局规则要求**每轮重建**（见阶段 E），旧存档只会掩盖「忘了重建」 | **测试任务收尾必须清理**（2026-09-17 用户规则，见下） |

**测试后收尾（关闭测试端 + 清理旧存档，2026-09-17 用户规则）**

> 规则原文：测试任务完成后，关闭测试端，清理旧存档数据，避免游戏进程长时间驻留。

- **标准收尾命令**：`pwsh -File scripts/test/mt.ps1 --version <版本> --phase stop --purge-saves`
  （`--purge-saves` 透传链：`mt.ps1` → `mt_stop.ps1` → `mt_cleanup.ps1`）。
- **全流程自动**：`mt.ps1 --version <V>`（不带 `--phase`）退出清理已带 `--quiet --purge-saves`，无需手工补命令。
- **单阶段分步必须手工收尾**：单阶段默认不清理（客户端要跨 launch/cases 存活），最后一次读数之后**必须**执行上面的命令。
- **删除范围**：`client_world`（`saves/<世界名>`）、`server_world`（`<run>/<世界名>`）、以及 `saves/` 下其它含 `level.dat` 的历史遗留世界；**不动** `run` 之外的东西、**不动** `resources/testworld-seed-<版本>.zip`。
- **回显**：`MT_CLEANUP_SAVES: PURGED <N>`（清理条数）或 `MT_CLEANUP_SAVES: KEPT（未指定 --purge-saves）`。
- ⚠️ **两阶段/重登类用例（`CHIP-RELOG-*`）中途的 stop 禁止 `--purge-saves`**（要跨 stop 保留存档：`saveall` → stop → launch 读回）。故 `--phase stop` 默认保留存档，`--purge-saves` 是显式开关。
- **失败取证优先**：`.mt_keep_alive` 存在时收停与清存档都只提示不执行；取证完用 `--phase stop --force --purge-saves` 释放。
- **收停是异步的**：`TerminateProcess` 返回 ≠ 进程已从进程表消失，故 `mt_cleanup` 在判残留前会**最多等 20 秒**再复核（否则慢退出的客户端会被误报成 `RESIDUAL`/退出码 1）。
- **收停范围已扩展到「专用服务端 / 数据生成 / run 任务包装器」**（2026-09-17 实测缺口）：旧判据只认**客户端入口**，于是 `mt_env world`（或两段式数据生成）起的 `runServer` 包装器与它的服务端 JVM **永远杀不掉** —— 实测跑完 env 后两者双双存活，且孙进程仍持有父进程 stdout 句柄，把 `mt.ps1 --phase env` **卡死**（子进程早已退出、父进程一直等）。现由 `Mt.Proc.psm1` 的 `Test-MtPipelineProcess` 统一判定：① 客户端（沿用最严格判定）；② 含 `gradle-wrapper.jar` **且**含 `:<子项目>:run` 选择器的包装器（**只认 run 家族**——`:neoforge-1.21.1:build` / `:neoforge-26.1.2:build` 之类的构建任务不属于测试流程，绝不能杀）；③ 入口为 `net.neoforged.devlaunch.Main` 的本版本 JVM（服务端/数据生成的命令行不含 run 目录与子项目选择器，靠「version + subproject」证据认领）。`mt_cleanup` 的「收停后自检」同步改用同一判据（否则既杀不掉也检不出）。

**覆盖缺口（如实标注，勿当成已验证）**

- 13 骰子 / 17 立牌 / 60 筹码的**逐特性玩法数值**没有 per-feature 自动化用例，目前只有静态守门 + 历史手测；
- 多人联机 / 服务端专用路径、存档跨版本兼容、性能表现均无覆盖。
- **安全气囊的玩家侧「真会死」对照**(`AIRBAG-BYPASS-KILL`):「充能 < 6 / 冷却进行中时气囊不生效」这一分支只有代码层结论(`AirbagChipItem.tryNegateFatal` 返回 false);玩家真死亡的相位会让相邻读数不可信(见 §10 第 21 条),故未纳入自动用例。「绕过 `die()` 直接 `remove(KILLED)` 的致死救不回来」这一死亡兜底边界同样只有代码层结论。

---

## 13. 26.1.2 线测试计划与「1.21.1 <=> 26.1.2 功能一致性测试」（2026-09-17 用户指示纳入后续测试计划）

**背景**：第三条线 `neoforge-26.1.2` 是 1.21.1 源码的整体迁移，**允许也不可避免存在平台差异**（见 `AGENTS.md` 的「第三条线规则边界」）。因此 26.1.2 线的测试目标不是「各跑各的冒烟」，而是 **同一功能在两个版本上的行为等价性**；差异必须被**显式登记**，而不是被沉默地接受。

### 13.1 现状盘点（2026-09-17）

| 项 | 1.21.1 | 26.1.2 |
|---|---|---|
| 探针 | `resources/kubejs/1.21.1/server_scripts/astral_bugfix_probe.js`（4089 行） | 同源机械移植 26.1.2 版（56 条子命令；迁移脚本 `temp/port_probe.ps1`） |
| 用例 | 回归套件（§8） | 5 条：`MIGRATION-SMOKE-26.1.2`、`PORTED-PROBE-SMOKE-26.1.2`、`CRAFT-SMOKE-26.1.2`、`CHIP-RELOG-A/B-26.1.2` |
| 已覆盖 | 全套 | 探针链路存活 / 物品注册 123 / Curios 装备 / 筹码栏尺寸与跨重登 / 121 份配方装载与真合成 / 长矛近战判定 |

### 13.2 一致性测试的方法（「同探针 + 同用例 + 双侧读数 diff」）

1. **探针同名同义**：两侧探针的**子命令名**、**读数键**与**格式**（`AP_<tag>_<KEY>:...`）逐字一致（26.1.2 侧只允许改变取值方式，不允许改变读数文本——如 `levelClass` 在 26.1.2 用两级判定但输出仍是 `net.minecraft.server.level.ServerLevel`）。
2. **用例成对落盘**：每个功能条目写成 `xxx-1.21.1.json` 与 `xxx-26.1.2.json`，**断言模式逐字相同**，仅 `version` 不同；差异只允许出现在 note 里并写明依据。
3. **对比流程**：同一用例 id 分别跑 `--version 1.21.1` / `--version 26.1.2` → 抽取两侧 `AP_*` 机器行 → **逐键 diff**。任何**非平台固有**的差异按缺陷登记（先复现、再交用户定夺，不得直接改断言迁就）。
4. **读数可比性前提**（必须逐条核对，否则 diff 无意义）：时间基准统一用 `level.getLevelData().getGameTime()`；伤害口径按各版本事件语义（1.21.1 `LivingDamageEvent.Pre` 在**吸收前**、1.20.1 在**吸收后**；本线对比时需标注）；物品/实体/效果 id 两侧一致（`Identifier` vs `ResourceLocation` 只是类名差异）。
5. **「允许差异」清单**（必须在本节维护，逐条给依据，禁止无限扩大）：
   - 26.1.2 **无** Iron's Spells 'n Spellbooks 联动（上游无 26.1.x 构建）；
   - 26.1.2 **有** 长矛（1.21.1 无该物品）⇒ 近战判定读数多 `spear/dspear/nspear` 三项；
   - 权限/命令 API 形态差异导致 `opprobe` 的 `level` 读数在 26.1.2 退化为 `-1`（`getProfilePermissions(NameAndId)` 在 Rhino 下取不到），`has2` 与 `dump` 判据不受影响；
   - Curios 主版本不同（1.21.1 为 9.x 语义、26.1.2 为 15.x）⇒ 槽位尺寸写法不同但**对外行为（槽位数、物品留存）必须等价**——这正是 `CHIP-RELOG-*` 的判据。
6. **门控**：26.1.2 线**发布前**，一致性测试与「三线构建 + 各自冒烟」并列为必过项；未通过项必须在发布说明中列明。
7. **排期**：① 先把 1.21.1 现有回归条目按 §13.2 成对迁移（当前只迁移了探针与 5 条冒烟）；② 双版本各跑通；③ 再补 26.1.2 独有行为的条目（长矛、26.1 数据包格式、Curios 15 尺寸/跨重登）。

### 13.3 本轮已固化的 26.1.2 专属用例（同日新增）

| 用例 | 判据要点 |
|---|---|
| `CRAFT-SMOKE-26.1.2` | ① 日志 `Couldn't parse data file` 计数 0；② `astral_dice:` 配方装载数 == 磁盘文件数（**121**）；③ 13 份手写配方按 `placementInfo` 自建 `CraftingInput` 跑 `matches()+assemble()` **真合成**；④ `meleecheck` 长矛判定 |
| `CHIP-RELOG-A-26.1.2` | 装 2★ 骰子 + 放筹码（**必须是 `curios:chip` 标签内的物品**）+ `/astralprobe saveall`；断言槽数/cosmetic 相等且筹码在位 |
| `CHIP-RELOG-B-26.1.2` | 强杀重登后再读：槽数/cosmetic 仍相等（**已 PASS**）；筹码仍在槽内 —— **当前 FAIL，代表 26.1.2 的「重登后筹码被移出栏位」缺陷仍在**（取证与已排除项见 `docs/compat-26.1.2-neoforge.md` §7.6） |

> ⚠️ **写筹码类用例的硬规则**：探针 `equipslot` 是**直接写栏位、绕过 Curios 校验**，所以必须使用 `curios:chip` 标签内的物品；用标签外的物品（如材料 `astral_dice:blank_chip`）会得到「放进去了、重登就没了」的**假缺陷**（Curios 迁移会按标签把非法物品退回背包）。此坑已实际踩过一次。

---

## 附录 A：工具链与发布工程变更记录（自 CHANGELOG 移出）

**2026-09-18：2.0.0 开发线「强力胶式目标选择器」整轮收尾归档（t15；实现 + 1.20.1 移植 + 验证 + 工具链修复逐项）**

- 工程:**本轮归属与主线洁净性（硬事实）**：本轮全部改动只落在 **dev 工作树 `F:\MCProject\astral_dice_multiloader-next`（分支 `multi-dev-next`）**；主线工作树 `F:\MCProject\astral_dice_multiloader` 保持封包版本 **`854a6c9`**（`status --short --untracked-files=all` 仅 `?? .agent-teams/**`、无新提交；`git rev-parse HEAD:scripts/test/mt_case.ps1 HEAD:scripts/test/mt_inject.ps1` = **`cb6ed2352faed226b206fe287409da5be5d13c94`** / **`2309285724124fdd2133af8f1332043aac10e8d2`**，与封包一致）⇒ **主线本轮零改动、零提交**；`mt_inject mouse`（含 t23 新增的 `wheel`）与用例 op `inject_mouse` **只存在于 dev 分支**（dev 引入于 `38eb1a5`，扩展于 `f84146a`），**未**镜像回主线。`neoforge-26.1.2/` 本轮**未改动**（最近提交仍 `f514dbe`）。未提交的测试产物/日志（`temp/**`、`scripts/test/reports/**`、`run/**`、`scripts/test/cases/.mt_*.json`）均被 `.gitignore` 覆盖，故 `git status` 干净。
- 工程:**本轮提交清单（dev，均为本地提交、未 push）**：`3865955` 1.21.1 实现（强力胶式按键语义 + 实体棱柱边框 + 删除 Enter 确认键与客户端键盘拦截 Mixin + 11 个 lang 键 + 新增 `SELECTOR-KEYS-1.21.1`）→ `38eb1a5` `mt_inject mouse` 子命令 + `inject_mouse` op → `a035e13` 可见外框 `TargetOutlineCapture` 取代碰撞箱 → `fd5312d` t18 → `243979e` t19 → `29a3508` t20 → `7c29679` t21 → `2318b63` t22 → `f84146a` t23 → `d1355c9` 1.20.1 移植（`forge-1.20.1` 对等 + 删该线 Enter 键与 `KeyboardHandlerMixin`）→ `d4a2e18` t27 → `17e8981` t26。
- 工程:**新增/改造的用例清单**：`cases/SELECTOR-KEYS-1.21.1.json`（16 断言 / 26 注入步骤，全部 `no_esc`，`esc_sensitive: true`）、`cases/SELECTOR-KEYS-1.20.1.json`（17 断言：比 1.21.1 多一条 `[TargetSelectBounds]` 存在性断言 = 该线唯一平台差异）、`cases/SELECTOR-GATE-1.21.1.json` / `cases/SELECTOR-GATE-1.20.1.json`（各 9 断言：六行 `AP_G1_*` 读数 + `absent AP_G1_ERR|AP_G1_EX|AP_G1_VERDICT:0` + kubejs + crash）、`cases/SELECTOR-VISUAL*`（1.21.1 边框视觉取证，人工判读，见第 29 项口径）。
- 工程:**实现结论（按键语义，两线对等）**：左键 = 确认（无有效目标只提示 `no_target`、不提交）；右键（不潜行）= 对自身使用 ⇒ 只提示 `self_unsupported`、**不发包、会话保留**；右键 + 潜行 = 取消；ESC = 原版照常开暂停菜单、`ScreenEvent.Opening` 收到 `PauseScreen` 即取消；J（`ACTIVE_SIGN_KEY`）经 `KeyMapping#consumeClick` 取消；选择期间 `InputEvent.MouseButton.Pre` 一律 `cancel`、`InputEvent.MouseScrollingEvent` 拦截滚轮、`ChatScreen` 豁免（命令聊天通道保留）。**已删除**：Enter 确认键 `CONFIRM_TARGET_KEY`、客户端键盘拦截 `mixin/client/KeyboardHandlerMixin`（两线 `src` grep 零残留）；键盘不再被吞。口径见 `AGENTS.md`「目标选择器规范 → ### 现行口径」第 1–6、10、12 条。
- 工程:**实现结论（边框渲染）**：`RenderType.create("astral_dice_target_prism", NEW_ENTITY, QUADS, 256, false, false)` 实体棱柱（**非** `LevelRenderer.renderLineBox`、**非** `RenderType.lines()`），框盒 = **碰撞盒 ∪ 逐帧实测模型外框** 再 `inflate(线宽/2)`（只外扩、绝不内缩）；线宽 命中 1/16、不可选 1/24、半径内其它 1/64；**1.20.1 的平台差异**：`RenderStateShard` 常量为 `protected static final` ⇒ 子类只读再导出同一批对象（不需 mixin/AT、refmap 零风险）、无 `Entity#getScale()`（读数行不含 `scale=`）、渲染器覆写 `scale()/setupRotations()` 的实体无法重放 ⇒ 退化为「碰撞盒 ∪ 未缩放实测外框」。口径见 §10 第 25/28–30 项与 `AGENTS.md` 同节第 7–8、11 条与「渲染口径的平台差异 (a)(b)(c)」。
- 工程:**t20 归档 —— `mt_launch` 环境闸门「跨零点日志日切」假阴性修复（原文照录，原文见 `temp/t20-evidence.md` 第七节）**：现象 = 跨零点冷启动时史莱姆压制**硬闸门**误报「未检测到「Superflat World No Slimes」模组」（exit 2），重跑即 OK。根因 = `mt_launch.ps1` 的环境/装载类判据只读 `logs/latest.log`（修复前 `:443`），而该文件 log4j filePattern 带日期（`logs/%d{yyyy-MM-dd}-%i.log.gz`）⇒ 跨零点日切把启动块（`Mod List:` 与 `显示名 版本 (modId)`）切进 `logs/2026-09-17-1.log.gz`（实测 mtime 00:00:00、只含 23:59:53→23:59:59.932），新 latest.log 里一条括号清单行都没有；mt_launch 启动前又删 latest.log（保证只读本轮）⇒ 假阴性。修复 = `lib/Mt.Proc.psm1` 新增 **`Read-MtLogWithRotation`**（latest.log + **`LastWriteTime -ge -Since`** 的 latest 家族轮转件、`.gz` 解压；不碰 `debug-*.log.gz`），`mt_launch` 环境闸门改用它、`-Since` = `$launchStartedAt`。**判据未放宽（三条硬证）**：① 仍匹配**带括号 modId**（`\(superflatworldnoslimes\)`）—— MISSING 行 `… (3.5 -> MISSING)` 与裸名字都不命中；② 真缺失仍 FAIL（窗口内无候选即照常 `exit 2`）；③ `-Since` 时间窗保证**不串上一会话**（否则「上一会话装过、本会话已移除」会被误判已加载）。另：**就绪判据（`:368`，含 `CreationTime > $launchStartedAt` 新鲜度校验）不参与兜底** —— 它必须只认本次会话。1.20.1（Forge）**不受影响、无需兜底**：其模组清单读 `debug.log`，filePattern `debug-%i`（无日期）⇒ 只在启动时轮转。已知残留（同源、未修）：`mt_assert`/`mt_report` 按字节偏移读日志，用例跨零点运行会日切错位 ⇒ 由 **t22（B8）** 修掉。取证：`temp/t20-verify.ps1`/`.log`（A 跨零点不再误报 / B 真缺失仍 FAIL / C 不串会话 / D 判据未减弱 / E 真实目录选中真实日切件）+ 真实冷启动 `SLIMEGUARD_LOADED=true`、`MT_LAUNCH: OK (36s)`（`temp/t20-launch-normal.log`）。
- 工程:**t21 归档 —— `mt_inject` 鼠标注入 SHIFT 常驻修复（原文照录，原文见 `temp/t21-evidence.md` 第七节）**：缺陷 = `mouse --button right -Shift`（及 `key --key shift-rclick`）的 SHIFT **按下走 SendInput、抬起走 PostMessage**（修复前 `mt_inject.ps1:260` vs `:289-290`），输入栈收不到抬起 ⇒ OS 级 SHIFT 逻辑键**常驻按下**（实测 `GetAsyncKeyState(VK_SHIFT)` 的 0x8000 位：修复前 20/20 残留、对照 0/20；且该残留**跨冷启动持久**，某轮首次读数即为 DOWN）。GLFW 侧因收到投递的 WM_KEYUP 而释放，故游戏当时看不出异常 —— **潜伏期长**。修复 = 抬起改走 `Send-MtInjectKeyUp`（与按下同一分发函数 ⇒ sendinput→SendInput、postmessage→WM_KEYUP 且 lParam 逐位不变），并用 `try/finally` + 局部 `$shiftDown` 保证「按下过必抬起」（异常路径也复位），**不新增任何全局状态**；CLI 形状与 `MT_INJECT_MOUSE:` 回显不变。验据 = 通道打桩（两种通道各 2 条成对、sendinput 下 0 条 PostMessage；异常路径仍抬起）+ 实机 20+20 次（含 `--hold-ms 500`）注入后 SHIFT 全为 up、退出世界标记 0。⚠️ 如实记录：t16 报告的「退到标题画面」在本轮**未**由该机制单独复现（修复前 40 次世界内注入 0 退出标记；「SHIFT 常驻 + 归一化 cmd」组合 2 次也 0）—— 本修复的依据是**状态残留**本身，不是「已复现退出世界」。判据（复现/回归通用）：注入后 `GetAsyncKeyState(VK_SHIFT) & 0x8000 == 0`；对照 = 不带 `-Shift` 的同批注入。
- 工程:**t22 归档 —— 断言窗口跨轮转锚定（B8）+ 报告聚合轮次世代（B9）（原文照录，原文见 `temp/t22-evidence.md` §四）**：**B8** = `log`/`absent`/`mixin` 的窗口起点原先只记「用例开始时的字节偏移」，跨零点日切后活动文件更名、新建更小的 latest.log ⇒ 偏移越界时旧实现 `if ($Offset -gt $size) { $Offset = 0 }` **静默**改成整读新文件（窗口前半段丢失 ⇒ `log` 假 FAIL、`absent` 假 PASS），偏移未越界时更糟（拿另一文件的同一字节号切片）。修法 = `snapshot` 除 `offsets` 外写 **`anchors`**（键 = 日志文件名；值 = `len`/`ctime_ms`/`mtime_ms`/`prefix_len`/`prefix_sha`；「launch 窗口」对应 `launch_anchors`，与 `launch_offsets` 同步冻结），读取时 `Read-MtLogWindow` 按**文件身份**（创建时间 + 头部 64 KiB 指纹）把窗口跨轮转拼回来：同一文件 → 与旧实现逐字节同义；被日切 → 只从**身份匹配**的那一段的同一偏移接续（更晚的轮转段不并入）；锚点文件已不在（重登删 latest.log）→ 不猜、退回既有语义并打 `MT_ASSERT_WINDOW: WARN`；无锚点的旧快照 → 逐字节同义。与 t20 的 `Read-MtLogWithRotation` 共用 `Get-MtRotatedLatestLogs`（唯一口径）但语义不同、禁止混用（前者判「窗口起点在哪」、后者判「是否存在某行」）。**debug 家族差异**：`debug.log` 的 filePattern 是 `debug-%i`（无日期），只在启动时轮转、不跨零点 ⇒ 1.20.1 侧无同类问题，也不得把 `debug-*.log.gz` 当窗口候选。**B9** = `SUMMARY.md`/`report.md` 原先把 `.mt_run_state.json` 里**所有**历史标记算成本次结果（`.mt_active_run` 是粘性 id、从不轮换）⇒ 工具链**故意造的负例**（如 `NEG-A-missing-no-esc: ERROR`）与上一轮/上一版本旧标记会让「本次全 PASS」的跑批显示 ❌、退出码非 0（实测 `report.md` ✅ 而 `SUMMARY.md` ❌）。修法 = 状态文件加 `gen`/`gen_started`/`closed_at`/`history[]`，`mark` 是唯一写入口，只在三个时机开新一轮（① 无 `gen` 的旧口径文件 ② `closed_at > 0` ③ 本版本本轮已记过 `preflight`）；开新一轮 = 把上一轮 `versions` **归档**进 `history` 再清空（只排除、不销毁）。**判据未放宽**：`PASS`/`SKIP` 之外仍是 `bad`，本轮内真实失败一条不少地让 SUMMARY ❌ 且退出码非 0。证据：`temp/t22/window-test.ps1`（80 项）、`compare-test.ps1`（32 次真实断言对照、结论行逐字符相同）、`cli-test.ps1`、`round-test.ps1`（23 项）。
- 工程:**t23 / t26 / t27 归档索引（口径条目已落地，此处只记归属与落点）**：**t23**（repair-round-2，`f84146a`）= ① AGENTS.md 现行口径改写 ② 用例弱断言收紧 ③ 滚轮拦截自动化 ④ `build.gradle` 过期注释 + 重复 mixin 块清理 ⑤ 可见外框边界登记 ⇒ §10 第 25–27 项。**t26**（`17e8981`）= AGENTS.md「### 现行口径」三处偏差修正：`target/` 实际清单含 **`SignSelectionGate`**（移植必须一并带上，第 12 条）、行号锚改「文件 + 符号名」、第 11 条按源码口径改写；并补记 **1.20.1 渲染平台差异 (a)(b)(c)**。**t27**（`d4a2e18`）= 1.20.1 探针 Rhino 成员分派对 JDK 包私有 `ImmutableCollections$ListN` 的非法访问修复（两线探针同形：先复制成 `java.util.ArrayList`）+ 截图取证口径 + 两线用例口径差异 ⇒ §10 第 28–30 项。
- 工程:**验证与评审结论归档**：**t12** 评审 `needs_revision`（5 findings，F1 high）→ **t23** 逐条关闭 → **t24** 复审 **verdict=pass**。**t14** 1.20.1 完整冒烟 = `task failed`，**唯一失败项** `SELECTOR-GATE-1.20.1` 已定性为**探针侧缺陷**（旧 Rhino 成员分派 × JDK 内部类），非实现缺陷；**t27** 修复后 **t28** 重验 **PASS**：`SELECTOR-GATE-1.20.1` **9/9**、`SELECTOR-KEYS-1.20.1` **17/17**、`SELECTOR-GATE-1.21.1` **9/9**，三链一致（用例 PASS → `report.md` PASS → `MT_ROUND` 结算 → `MT_RUN` rc=0），静态闸门全绿（`Test-MtSyntax` 36 文件 0 失败 / `MOD_SOURCE_GATE` violations=0 / lang 三线 630·630·622 一致）。1.21.1 侧本轮的端到端读数：`SELECTOR-KEYS-1.21.1` **16/16**（含滚轮 ⑥-a/⑥-b 正反对照与 `AP_SK_DIAG:lvlDataGGT=1743`）。
- 工程:**scope 例外入档（两条，均由 captain 逐行核验；原规格要求「移除 mixin 写入」）**：① **t10** 为实现「旧 Mixin 名零残留」，删除 `neoforge-1.21.1/src/main/templates/META-INF/neoforge.mods.toml` 中 **1 行**含旧 Mixin 名的注释；② **t13 同源处理**，删除 `forge-1.20.1/src/main/templates/META-INF/mods.toml` 的 **1 行**注释 `# 目标选择器键盘输入锁定(KeyboardHandlerMixin)依赖本 Mixin 配置。`（不删则无法满足「`-- forge-1.20.1/src` 下 grep 零残留」）。两条理由相同：删除的仅是**注释**（不改任何 metadata 语义），构建已通过 `generateModMetadata` 重生成并 `BUILD SUCCESSFUL`。
- 工程:**越界事故与复原（如实登记）**：本轮曾发生一次「**相对路径写入落到主线**」的越界 —— 一条用例文件被写到**主线工作树**（0 字节），当场发现并**复原**（删除该文件）。复原证据 = 当前主线 `git status --short --untracked-files=all` 除 `.agent-teams/**` 外无输出、`git status --short -- scripts/` 为空、`scripts/` 下 **0 字节文件数 = 0**、`rev-parse --short HEAD` 仍 `854a6c9`（`reflog` 无新提交）。纪律已固化进 `AGENTS.md`（见「分支归属」与「相对路径陷阱」两条）。

**2026-09-18：跨 launch 证据必须留档 —— `mt_launch` 每次启动前清空 `run/<ver>/logs/latest.log`（t30 入档，源自 t29 的工具链教训）**

- 工程:**为什么（根因：证据文件每次 launch 被清空）**：`mt_launch.ps1` 在启动前**主动删除** `run/<版本>/logs/latest.log`（`:280-291`，`Remove-Item -LiteralPath $p.latest_log -Force -ErrorAction Stop`；删不掉即 `MT_EXIT_BLOCKED` —— 这是为了消灭「读到上一轮/他人日志 ⇒ 假阳性 `MT_LAUNCH: OK`」），并且**同样清空** `logs/debug.log`（`:298-301`）、`astral_probe.log`（`:304-306`）与 `crash-reports/`（`:307`）。⇒ **任何以「某次 launch 的 `latest.log`（或 `debug.log`）行号」为证据的验收项，在同一工作树里再跑一次 launch（或再跑一条会重启客户端的用例）后必然不可复核** —— 文件已换、行号已失效，事后连「证据是否曾经存在」都无法从盘上确认。
- 工程:**正确做法（本轮定为口径）**：**用例跑完、`--phase stop` 之前**把证据复制到 `temp/<task>/`，并在同目录留 `sha256.txt` 与一份「**留档先于 stop**」的先后顺序证据（留档文件的 mtime/自记时间 + stop 日志的 mtime/内容）。建议清单：① 整份（或含关键行号的区段）`latest.log` / `debug.log`；② 当次冷启动日志里的 `PROBE_DEPLOY` 与 `MT_LAUNCH:` 行；③ `SUMMARY.md` 与 `report.md` 副本；④ 用例执行日志（逐条断言 + `MT_CASE_RESULT` + `rc`）。**顺序不可颠倒**：先 stop 再留档会复制到「即将被下一次 launch 清空」或「已不存在」的文件（`--phase stop --purge-saves` 还会清掉存档/运行目录）。留档后若**再**做 launch，须在报告里注明「留档对象是第 N 次 launch 的日志，run 目录现状已不是它」，不得把留档内容说成当前盘上内容。
- 工程:**适用范围**：**凡验收项引用 `run/<版本>/logs/**` 的行号（或整文件内容）者，都必须留档** —— 包括用例断言命中行、探针读数行（`AP_*`）、崩溃/异常计数、`MT_ASSERT_*` 结果行。**不受影响的两类**：① 只引用**构建产物 / 探针模板哈希**的验收项（可随时重算：`Get-FileHash`、`PROBE_DEPLOY: … <hash> up-to-date`、jar sha256）；② 引用**已留档文件**的验收项（证据已在 `temp/<task>/`，与 run 目录是否被清空无关）。另注：`mt_report` 的 `reports/<run_id>/**` 副本本身也是一份留档（含日志/截图副本），但它是**汇总期**产物、且在重跑或 `--purge-saves` 下同样可能被新版覆盖 ⇒ **不能替代** `temp/<task>/` 的当次留档。
- 工程:**实例（本轮闭环）**：**t28** 的 `SELECTOR-GATE-1.20.1` 六行读数（`run/1.20.1/logs/latest.log:774-779`）在**同一任务的后续 KEYS 运行**（同一工作树再次 launch ⇒ 清空 latest.log）时被销毁，captain 复核时**已无法从盘上确认**，只能另开 **t29** 复跑取证；t29 随即**按本口径**闭环：`temp/t29/` **13 个文件**（`latest.log` 114273 B、`debug.log`、`latest_section_690_800.txt`、`g1_readings_with_lineno.txt`（774-779 六行）、`mt_launch_detached.log`、`gate_case.log`、`SUMMARY.md`、`report-1.20.1.md`、`exceptions_and_provenance.txt`、`post_archive_ordering.txt`、`stop_after_archive.log`、`README.md`、`sha256.txt`（12 条）），留档时点 **03:36:03 早于** `--phase stop` 的 **03:36:25**（`post_archive_ordering.txt` 记录因果顺序），且留档的 `latest.log` 与当时 `run/1.20.1/logs/latest.log` 的 sha256 **完全相同** = `0B9621D86C4E8FC129FBB1D1FBFFF512A7F1CB056748CB3F059C8C72D2A4BB28` ⇒ 复核者可凭留档独立复算、不再依赖 run 目录的现状。

**2026-09-18：探针 `ItemStack.is(...)` Rhino 重载歧义消解（1.21.1 + 1.20.1 对等）—— 解除 `SELECTOR-GATE-*` 的验证阻塞**

- 工程:**问题（t11 的 F2）**：`SELECTOR-GATE-1.21.1` 9 断言仅 2 PASS，聊天栏回 `AP_G1_EX:InternalError: … is ambiguous`，`AP_G1_*` 读数**整段缺失** ⇒ 用例无法判 PASS（缺陷先于 t10 存在，属验证阻塞而非实现缺陷）。
- 工程:**歧义定位（全量实测文案 + 源码级候选集）**：本轮冷启动**先复现后修复**，在 `run/1.21.1/logs/latest.log:608` 取到未截断的完整报错：
  `The choice of Java method net.minecraft.world.item.ItemStack.is matching JavaScript argument types (com.merlinkitsune.astral_dice.item.card.EffectCardItem) is ambiguous; candidate methods are: boolean is(net.minecraft.core.HolderSet) / boolean is(net.minecraft.world.item.Item) (server_scripts:astral_bugfix_probe.js#1362)`。
  与之对应：① 调用点 = 1.21.1 资源副本 `astral_bugfix_probe.js:1363`（`countEffectCards()` 内 `if (st.is(pool.get(j).getItem()))`；报错里的 `#1362` 是该行**0 基**行号）；② 原版 `ItemStack` 有 **5 个同元重载**（`ItemStack.java:339 is(TagKey)` / `:343 is(Item)` / `:347 is(Predicate<Holder<Item>>)` / `:351 is(Holder<Item>)` / `:355 is(HolderSet<Item>)`，1.20.1 为 4 个：`:230/:234/:238/:242`）；③ Rhino 侧是**运行期**按 JS 类型信息选重载（`dev.latvian.mods.rhino.NativeJavaMethod#preferSignature` 返回 `PREFERENCE_AMBIGUOUS` ⇒ `resources/Messages.properties` 的 `msg.method.ambiguous`），**判不出来就不调用**（而非猜一个）；候选集恰好剩两个：实参 `EffectCardItem` 可赋值的类参数 `is(Item)`，以及被 Rhino 类型层视为可转换的接口参数 `is(HolderSet)`（1.20.1 侧同一处接口候选是 `is(Holder)`）—— 终类参数（`TagKey`/`ResourceKey`）与函数式接口参数（`Predicate`，Rhino 有专用 `JSFunctionTypeInfo`）已在前一步被滤掉。**未**用 `try/catch` 吞掉该错误（禁用手法）。
- 工程:**消歧写法（按注册名比较，不含 `is(...)` 重载解析）**：`countEffectCards()` 改为每格物品先取一次 `itemIdOf(st)`（本文件**既有**工具，1.21.1 `:182` / 1.20.1 `:171`，实现 = `BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()`），再与 `itemIdOf(pool.get(j))` 做**字符串**相等比较。理由：① 注册表 key 与 item 一一对应 ⇒ 语义与 `is(Item)` **完全等价**（`is(Item)` 的方法体就是 `this.getItem() == item`，`ItemStack.java:343-345` / 1.20.1 `:234-236`）；② 全程零重载解析（`getKey(...)`/`toString()` 均无歧义），也不依赖 Rhino 的 Java 对象 `===` 语义（跨包装实例是否同一由 Rhino 的 `shallowEq` 决定，探针不应依赖）；③ 版本一致：**不**用 `Item.toString()` 当 id —— 实测 1.20.1 的 `Item.toString()` 只返回 `getPath()`（无命名空间，跨命名空间会误判），而 `ResourceLocation.toString()` 返回完整 `namespace:path`。
- 工程:**1.20.1 对等**：同一函数在同一位置存在**同源**模式（资源副本 `:1344`，`node --check` 通过），已按同一写法修掉（t14 的 `SELECTOR-GATE-1.20.1` 会跑同一函数）；26.1.2 副本经 grep 复核**不含**该模式（只有 `src.is(DamageTypeTags…BYPASSES_INVULNERABILITY)`），本轮按范围不动该线。
- 工程:**同文件第二处 `.is(` 复核（判为无风险，未改）**：`srcBypassesInvuln(src)` 的 `src.is(DamageTypeTagsClass.BYPASSES_INVULNERABILITY)` —— `DamageSource` 只有两个同元重载 `is(TagKey<DamageType>)` / `is(ResourceKey<DamageType>)`（1.21.1 `DamageSource.java:133/137`，1.20.1 `:141/145`），而实参是 `TagKey` 常量 ⇒ 只有 `is(TagKey)` 可接受，**候选唯一**，与歧义场景不同类。
- 工程:**冷启动实测（stop → launch，`mt_launch` 的 `PROBE_DEPLOY` 逐文件 SHA256 校验部署）**：修复前同一命令 `/astralprobe sggate G1` → `AP_G1_EX:… is ambiguous`（读数缺失）；修复后（部署哈希 `063A1D316F3A`）同一命令**一次全出**且无 `ambiguous`/`InternalError`：
  `AP_G1_PREP:chipSlots=1:cards=0` / `AP_G1_GATED:session=1:cd=0:cards_delta=0:armed=1` / `AP_G1_CANCEL:token_seen=1:session=0:cd=0:cards_delta=0:armed=0` / `AP_G1_CONFIRM:mob=1:session=0:broken=1:cd=1:cards_delta=1:armed=0` / `AP_G1_VERDICT:1` / `AP_G1_DONE`（`latest.log:606-612`，2026-09-18 01:07:33）。其中 `CONFIRM:cards_delta=1` 与 `GATED/CANCEL:cards_delta=0` **同时**证明新比较式的语义正确（发牌后确实数到 1 张池内卡、未发牌时不误数），不是「不报错但恒假」。
- 工程:**探针语义不变**：只是把一次 `st.is(item)` 换成等价的注册名比较，**未删任何读数、未改任何 `AP_` 输出格式**（`countEffectCards` 是 `cards_delta` 的观测口径，值语义不变）；探针改动与 t10 的实现无关（不碰模组源码）。规则已写入本文件 §10 第 17 条（已知坑/规避）。正式用例复跑（`SELECTOR-GATE-1.21.1` / t14 的 `SELECTOR-GATE-1.20.1`）由 t17/t14 汇总。

**2026-09-18：会话期注入必须 `no_esc` —— `SELECTOR-KEYS-1.21.1` 假失败修复 + `mt_case` 结构校验保护**

- 工程:**问题（t11 的 F1，`temp/t11/T11-REPORT.md`）**：`SELECTOR-KEYS-1.21.1` 11 断言仅 7 PASS，失败项（`key=left action=confirm`、`confirm … -> SUCCESS`、`key=j action=cancel`、`AP_SK_DIAG:levelClass=`）**全部**是实现侧无关的连带后果 —— 用例的命令/按键注入没声明 `no_esc`，注入器于是先按了一次归一化 Esc，而那一次 Esc 在新语义下就是「取消选择」（`00:04:52.746 key=esc action=cancel`），随后 Tab+Enter 还把客户端带出了世界；只加 `no_esc` 的等价副本 11/11 PASS ⇒ **实现侧六项语义无缺陷，缺陷在测试工件**。
- 工程:**用例修复（`scripts/test/cases/SELECTOR-KEYS-1.21.1.json`）**：**全部 18 个注入步骤**（12 `inject_command` + 3 `inject_key` + 3 `inject_mouse`）都声明 `"no_esc": true`；新增顶层 `"esc_sensitive": true` 作为该用例的显式声明（也是结构校验的触发条件之一）。**与「11/11 PASS 诊断副本」`temp/t11/SELECTOR-KEYS-NOESC-1.21.1.json` 的差异对照（脚本 `temp/t18-compare.ps1`，结论 `temp/t18-compare.log`）**：除 3 个鼠标步骤多出的（惰性）`no_esc` 外，**18 个注入步骤逐字段签名差异 = 0**、`wait` 序列逐项相同（`1500,1500,800,400,1200,1500,1200,1500,1200,1500,1500,1200,1500,600`，合计 17100 ms）、**11 条断言逐条相同**（`ASSERTS_IDENTICAL: True`）；剩余差异仅为 `case_id` / `title` / 8 条 `note` 文档步骤。⇒ 正式用例与已验证基线**无实质差异**。同轮把 `wait` 时序按基线对齐（含 `/tp` 后补 400 ms 再左键），并把 `cancel` 断言的日志前缀补全为 `\[Astral Dice\]\[TargetSelection\]`（与 `confirm` 断言一致；实测日志行确为 `[Astral Dice][TargetSelection] cancel token=… player=…`，见 `TargetSelectionManager.java:197`）。
- 工程:**工具链保护（`mt_case.ps1`，结构校验阶段，禁止静默）**：`Test-MtCaseValid` 新增「会话期 Esc 保护」段 —— 判据 = ① 用例声明 `esc_sensitive: true`，或 ② 任一 `inject_command.command` 命中 `$script:EscSensitiveSessionCmds`（当前 `targetselect`，子串匹配）；命中后**每个 `inject_*` 步骤都必须 `no_esc` 真值**，否则 `validate` 报 `MT_VALIDATE: FAIL — N 项`（退出码 1）、`run` 报 `MT_CASE: ERROR — … 校验失败`（退出码 2，**校验先于客户端检查 ⇒ 不触达游戏**）。错误文案带步骤序号、op 与触发来源（`用例声明 esc_sensitive=true` / `命令 '…' 命中会话命令表 …`）。同时把声明**真的接通**到注入器：`inject_key` / `inject_mouse` 此前**根本没有** `no_esc`（字段表未列、代码不转发、子命令不接收），现三处一起补齐（`$script:Primitives['inject_mouse'] += 'no_esc'`；key/mouse 分支转发 `-NoEsc`；`mt_inject.ps1` 的 key/mouse 子命令接收 `-NoEsc` 并回显 `ESC_SKIP: … 无 Esc 归一化 ⇒ -NoEsc 声明成立`）。**`inject_mouse` 增列 `no_esc` 的理由**：鼠标路径本身不按 Esc（只有 shift/光标/左右键），该字段对它是「声明层断言」而非开关；之所以必须可声明，是因为保护按统一的 `inject*` 口径收取声明，且声明要被回显证据覆盖（否则只能靠读源码确认，正是本次假失败能藏住的原因之一）。既有 `no_esc` 语义与其它用例行为**零改动**（不声明即不追加参数，默认 `$NoEsc = $false`）。
- 工程:**实测回显（`temp/t18-verify.ps1` / `temp/t18-verify.log`，全程无游戏客户端）**：① 正式用例 `MT_VALIDATE: OK` rc=0；② 反例 A（声明 `esc_sensitive` 但 1 个 `inject_command` 漏 `no_esc`）→ `MT_VALIDATE: FAIL — 1 项` + `步骤 3: 'inject_command' 缺少 no_esc=true（用例声明 esc_sensitive=true …）` rc=1；③ 反例 B（去掉声明后仍由**自动识别**触发）→ `FAIL — 18 项`，文案为 `命令 '/astral_dice targetselect enemy' 命中会话命令表 targetselect`；④ 反例 C（对缺 `no_esc` 的用例走 `run`）→ `MT_CASE: ERROR — … 校验失败` + `MT_CASES_SUMMARY: NEG-A-missing-no-esc=ERROR`，**rc=2 且未触达游戏**；⑤ 无假阳性：`cases/` 下**48 个真实用例**（`Get-MtCaseFiles` 会排除 `.mt_*.json` 状态文件）全部 `MT_VALIDATE: OK`；⑥ 传递证明（dot-source `mt_case.ps1` 后给 `Invoke-MtCaseChild` 打桩，打印真实 argv）：`key --key j --version 1.21.1 -NoEsc` / `mouse --button right --version 1.21.1 -NoEsc` / `mouse --button right --version 1.21.1 -NoEsc -Shift` / `cmd --command /time set midnight --version 1.21.1 -NoEsc`，而**不声明时不追加**（`key --key j --version 1.21.1`、`mouse --button right --version 1.21.1`、`cmd … --version 1.21.1`）；⑦ `mt_inject` 侧接受性：`key/mouse/cmd … -NoEsc` 均通过参数解析（报「客户端未在运行」而非「未知参数」），而 `--bogus-flag` 仍 `MT_ERROR: 未知参数`（无回归）。
- 工程:**口径入档**：本条规则写入 `TESTING-SPEC.md` §7（条目 schema 段，「会话期注入必须 `no_esc`」）。玩家侧两份 CHANGELOG **零改动**（纯测试工件/工具链，按约定只进本附录）。用例正式复跑由 t17 执行（本轮只做自证：与已验证基线逐字段等价 + 校验器正反例回显）。
- 工程:**顺带登记（本轮未改，留给工具链后续任务）**：`mt_inject.ps1` 的 `Send-MtInjectMouseCenter` 在 `-Shift` 时，**shift 按下**走 `Send-MtInjectKeyDown`（sendinput ⇒ 真实 `SendInput`），而**shift 抬起**走 `Send-MtInjectMessage` ⇒ `PostMessage(WM_KEYUP)`（`:287-291` 与 `:179`）—— 两条通路的投递方式不一致（真实按下 / 消息抬键），是「注入 shift+右键后客户端退到标题画面」现象的**待查疑点**（t16 取证时两次复现，隔离实验证明与本模组改动无关）。本轮**不修**（不在 t18 的修复范围，且改注入通路会影响所有 shift 类用例），只登记并与 key/mouse 的 `ESC_SKIP` 回显一起留待专项处理。

**2026-09-17：26.1.2 线并入主线 + 模组来源统一口径 + 内容更新优先级规则**

- **第三条线（26.1.2）并入主线目录（用户要求「把当前 workflow 合并到主线目录，核对完整后移除」）**：在主线 worktree `F:/MCProject/astral_dice_multiloader`（分支 `multi-1.20.1-1.21.1`，合并前 `460975c`）以 `git merge --no-ff` 收编 `multi-26.1.2-neoforge`（`f29f51a`，merge-base `fda8ca9`），合并提交 **`c846f83`**（2 父提交已核），规模 **1126 文件 / +43543 / −48**（其中 1109 个新增）。
  - **冲突 6 处全部为文档/工具链注释**：`AGENTS.md`、`scripts/test/TESTING-SPEC.md`、`scripts/test/{mt.ps1,mt_stop.ps1,mt_cleanup.ps1,lib/Mt.Proc.psm1}`；两份 CHANGELOG **无冲突**。解决口径 = **保留双方语义并统一术语**（「两阶段/重登类用例（`CHIP-RELOG-*`）」；`run` 家族默认示例同时列出 `:neoforge-1.21.1:build` 与 `:neoforge-26.1.2:build`），不删任何一侧的独有内容。
  - **完整性硬证（四棵树集合比对，非人工目测）**：`base=2075 / ours=2075 / theirs=3184 / HEAD=3184 / union=3184`，**`lost=0`、`extra=0`**（`lost` 应等于 `(ours \ theirs) ∩ base`），`git diff --name-only --diff-filter=U` 为空；合并结果与 `theirs` 的差异**仅有**上述 6 个冲突文件（15 插入 / 12 删除）。
  - **独立 worktree 已移除**：`git worktree remove F:/MCProject/astral_dice_multiloader-26.1.2`（无需 `--force`，退出码 0），`git worktree list` 只剩主线与 2.0.0 开发线；全仓 `git grep` 对 worktree 路径 **0 命中**（无残留引用）。**移除前先按「只补不缺」把该 worktree 的本地忽略资产迁入主线**（391 个文件：`docs/upstream/curios-26.1.2-loadinventoryconfiguration.md`、`temp/curios_src`（含上游补丁提交 `9704c4c`）、`temp/curios-fix-26.1.2-loadinv-size.patch`、`temp/probe_mods`、运行日志等）；`run/`、`build/`、`.gradle/` 属可再生未迁移。分支 `multi-26.1.2-neoforge` 未删除（保留为回滚/对照入口，其内容已完整含于 `c846f83`）。
- **26.1.2 子项目版本号单独改为 `1.2.1-beta`（2026-09-17 用户裁决「其他支线版本号不动」）**：`neoforge-26.1.2/gradle.properties` 的 `mod_version` = **`1.2.1-beta+neoforge_26.1.2`**（按仓库硬规则「版本号自带加载器后缀」保留后缀 —— `pushToRootBuild`/`pushToGame` 的旧产物清理正是按 `contains('+neoforge_26.1.2')` 匹配，省掉后缀会留下旧 jar 并在整合包里造成同 modId 双 jar）。**判据**：`gradlew :neoforge-26.1.2:generateModMetadata` 产出的 `build/generated/sources/modMetadata/META-INF/neoforge.mods.toml` 为 `version="1.2.1-beta+neoforge_26.1.2"`（实跑通过）；`forge-1.20.1` / `neoforge-1.21.1` 的 `mod_version` 未改动。
- **模组添加规则统一口径 + 纳入同一套闸门（用户要求，1.20.1 + 1.21.1）**：模组只能经 **Curse Maven** 或 **Modrinth Maven** 获取（新章节见 `AGENTS.md`「模组依赖添加规则(统一口径)」）。落地改动：
  - `neoforge-1.21.1/build.gradle` **补声明 Curse Maven**（此前 1.21.1 侧没有该仓库 ⇒ 根本无法按统一口径引入 Curse Maven 模组）；`forge-1.20.1` / `neoforge-1.21.1` 各自的第二个 `repositories {}` 块里**重复声明**的 Modrinth Maven 一并去重（Gradle 项目级仓库本就累加）。
  - **换源去本地 jar**：1.21.1 的 Iron's Spellbooks 由 `base-mod-compile-libs/irons_spellbooks.jar`（13.9 MB、随仓库入库的本地 jar）改为 `compileOnly "maven.modrinth:irons-spells-n-spellbooks:RtvqnbKi"`。**等价性实测**：原本地 jar sha1 = `09907e3b4bfdabd7f1f44bfd25aa6432183f39bb`（13874139 字节），与 Modrinth 上 `irons_spellbooks-1.21.1-3.16.2.jar`（version id `RtvqnbKi`，project `s4OWxYQQ`）**同哈希同尺寸**；Gradle 实际解析到的缓存文件 sha1 也复算为该值，`gradlew :neoforge-1.21.1:compileJava` **BUILD SUCCESSFUL**（该任务 `UP-TO-DATE`，正因 classpath 内容逐字节不变）。本地 jar 文件**未删除**（不再被任何配置引用，是否清理留待用户裁决）。
  - **新守门脚本 `tools/check_mod_sources.ps1`（唯一实现，阶段 P 与 §9 共用）**：R1 两个发布线都必须声明 Curse + Modrinth 两个仓库且同一文件内不得重复声明；R2 模组坐标只能是 `curse.maven:` / `maven.modrinth:`，**实际命中**的本地 jar 兜底（`fileTree`/`files`）一律 FAIL，非模组库按 `$LibraryGroups` 白名单放行，官方 maven 的模组依赖（`mezz.jei`、`dev.architectury`）以**例外**每次回显（待用户裁决是否迁移坐标，不静默）；R3 未被引用的本地 jar 只作 INFO（脚本不删文件）。结论行 `MOD_SOURCE_GATE: OK|FAIL violations=N exceptions=N infos=N`。
  - **接入闸门的实测**：`mt_preflight.ps1` 新增第 12 项检查「模组来源」（`[OK] MOD_SOURCE_GATE: OK violations=0 exceptions=4 infos=2`，`MT_PREFLIGHT: OK — 12 项全部满足（1.21.1, 1.20.1, 26.1.2）`）；`TESTING-SPEC.md` §9 静态守门清单同步加入该命令。
- **Gradle 侧「模组来源」硬规则 + 本地模组 jar 出库 + README 同步（2026-09-17 用户要求）**：
  - **`exclusiveContent` 硬约束（三线）**：`forge-1.20.1` / `neoforge-1.21.1` / `neoforge-26.1.2` 的 `repositories` 均改为用 `exclusiveContent` 把 `curse.maven` 与 `maven.modrinth` 两个 group **独占**给 `https://www.cursemaven.com` 与 `https://api.modrinth.com/maven`，即这两个 group 不再允许从任何其它仓库（含仓库内本地镜像 `.oss-basemod-repo`）解析。**变异测试证明规则真的生效**：把独占声明里的 Modrinth Maven 临时指向无效地址后，`:neoforge-1.21.1:compileJava` 直接 `Could not resolve maven.modrinth:curios:yohfFbgD` / `…:patchouli:…` / `…:irons-spells-n-spellbooks:RtvqnbKi`（**尽管本地镜像里就有 curios 的 maven/modrinth 缓存**，说明独占语义确实把其它仓库排除了）；恢复后文件 sha1 与变异前一致，三线 `compileJava` **BUILD SUCCESSFUL**。该规则与 `tools/check_mod_sources.ps1`（阶段 P「模组来源」+ §9）构成「解析期 + 静态」双闸门。
  - **第三方模组 jar 不再入库**：`.gitignore` 新增 `base-mod-libs/` 与 `base-mod-compile-libs/`（无斜杠前缀 ⇒ 三个子项目同名目录一并生效），并 `git rm -r --cached neoforge-1.21.1/base-mod-compile-libs` 把那条随仓库入库的 `irons_spellbooks.jar`（13874139 B）移出索引（**文件保留在磁盘**，仅不再跟踪）；此后该目录只作为本机遗留物，编译不再引用（Iron's Spellbooks 已改 Modrinth Maven）。
  - **`README.md`（中英双份）同步当前三线状态**：支持表新增 `neoforge-26.1.2`（26.1.2 / NeoForge 26.1.2.109 / Java 25 / `1.2.1-beta`，标注为低优先级线、不发 Release、无 Iron's Spells 联动）；前置表按线分档写明 Curios 版本（1.20.1 = 5.x、1.21.1 = 9+、26.1.2 = 15+）并把 1.20.1 的 Mixin Booster 单独成行；下载段补第三个 jar 名、构建段补 `:neoforge-26.1.2:build`，并写明「模组只经 Curse Maven / Modrinth Maven 获取、由 `exclusiveContent` 与守门脚本双重强制、第三方模组 jar 不入库」。
  - **两份 CHANGELOG 本批零改动**：以上全部属工程/工具/依赖口径类，按既有约定只记入本附录（玩家侧 1.2.1 小节一条不改、条目数保持中英一致）。
- **JEI / Architectury API 一并改走 Curse Maven（2026-09-17 用户裁决「JEI / Architectury API 使用 curse maven 获取」）**：原 `mezz.jei:*`（官方 maven `maven.blamejared.com`）与 `dev.architectury:architectury-forge`（官方 maven `maven.architectury.dev`）全部改为 Curse Maven 坐标：
  | 依赖 | 原坐标 | 新坐标（Curse Maven） | CF 文件 |
  |---|---|---|---|
  | JEI 1.20.1 | `mezz.jei:jei-1.20.1-forge:15.56.0.205` | `curse.maven:jei-238222:8778011` | `jei-1.20.1-forge-15.56.0.205.jar`（1778129 B） |
  | JEI 1.21.1 | `mezz.jei:jei-1.21.1-neoforge:19.39.0.372` | `curse.maven:jei-238222:8512040` | `jei-1.21.1-neoforge-19.39.0.372.jar`（1572695 B） |
  | JEI 26.1.2 | `mezz.jei:jei-26.1.2-neoforge:29.37.0.99` | `curse.maven:jei-238222:8886511` | `jei-26.1.2-neoforge-29.37.0.99.jar`（1908683 B） |
  | Architectury 1.20.1 | `dev.architectury:architectury-forge:9.2.14` | `curse.maven:architectury-api-419699:5137938` | `architectury-9.2.14-forge.jar`（580602 B） |
  - **等价性证据（JEI：逐字节相同）**：改源后 Gradle 从 `cursemaven.com` 解析到的三个 jar，sha1 与原先官方 maven 缓存逐一相同 —— `7f64b7f8fde7f001ef054dabe3618a8780ba2650`（1.20.1）/ `6e703a82a229d269855c3e0260c38aebc7f01abb`（1.21.1）/ `2c3ee3d2312d9d73e9320699d24b3bd0c6e6b493`（26.1.2），尺寸亦一一相同。另核实 26.1.2 的 `jei-26.1.2-neoforge` 官方 maven POM 虽声明 `jei-26.1.2-{common,lib,gui}` 为 runtime 依赖，但**主 jar 自身即自包含**（含 `mezz/jei/{api,common,gui,library,neoforge,modshade}` 全部包，`library` = lib 模块的 333 个类），故单文件坐标不丢类。
  - **等价性证据（Architectury：产物形态不同，比 MDG 重映射产物）**：CF 提供的是**生产(SRG)jar**、官方 maven 提供的是 dev jar，二者原始文件本不相同（580602 B vs 599211 B）⇒ 不能比 SHA1。改由「比 MDG 重映射后的产物」取证：两个来源经 MDG 变换得到的 jar（`9.2.1/transforms/…/transformed/architectury-forge-9.2.14.jar` 与 `…/architectury-api-419699-5137938.jar`）**条目数均 482、逐条 CRC32 全等（0 处不同）、382 个 class 名集合零差异**，且 `:forge-1.20.1:compileJava` 保持 `UP-TO-DATE`（编译期 ABI 指纹未变）。同类换源规则已写入 `AGENTS.md`「模组依赖添加规则」第 6 条。
  - **顺带发现的真实缺口（已修 + 已加守门）**：`neoforge-26.1.2/build.gradle` **此前没有任何 Curse Maven 仓库声明**，改用它取 JEI 时 Gradle 直接 `Could not find curse.maven:jei-238222:8886511`（`BUILD FAILED`，搜索位置里没有 `cursemaven.com`）⇒ 已为其补声明（`maven { url 'https://www.cursemaven.com' }`）。同时给守门脚本加了 **R1b「坐标 ↔ 仓库一致性」**（对三条线全部生效）：用了某来源的坐标却没声明对应仓库即 FAIL；**变异测试**已做——临时删掉 26.1.2 的 Curse Maven 行 → `[FAIL] neoforge-26.1.2 R1b 使用了 curse.maven 坐标但未声明 Curse Maven 仓库`、`MOD_SOURCE_GATE: FAIL violations=1`、退出码 1；恢复后文件 sha1 与变异前相同。
  - **验证**：`gradlew :neoforge-1.21.1:compileJava :forge-1.20.1:compileJava :neoforge-26.1.2:compileJava` **BUILD SUCCESSFUL**（1.21.1 / 1.20.1 保持 `UP-TO-DATE`；26.1.2 因依赖变化真重编译，只有一条既有的 `IItemHandler#isItemValid` 过时警告）；守门结论由 `exceptions=4` 变为 **`MOD_SOURCE_GATE: OK violations=0 exceptions=0 infos=2`**（两条官方 maven 例外清零，`$ModExceptions` 保留为空表机制）。
- **新增「模组内容更新规则（三线优先级）」（用户要求）**：`AGENTS.md` 新章节明确 —— ① 第一优先级 = `neoforge-1.21.1` + `forge-1.20.1` 发布线对（功能对等、CHANGELOG 同步）；② 第二优先级 = `neoforge-26.1.2`（**低优先级版本**，主线内容更新未完成前不得动，完成后才按 `docs/compat-26.1.2-neoforge.md` 迁移）；③ 迁移后必须做**功能实现一致性测试**（§13.2 的「同探针 + 同用例 + 双侧读数 diff」，差异要么修掉要么作为平台差异登记）；④ 26.1.2 不发版（CI 只跑构建守门）；⑤ 内容更新的验收口径；⑥ 禁止「顺路先改 26.1.2」。
- **测试工具链路径修正（随并入暴露）**：`scripts/test/lib/Mt.Paths.psm1` 的内置默认值与 `scripts/test/mt.conf.example` 里 26.1.2 的整合包 mods 目录一直指向**本机并不存在**的 `D:\.minecraft\versions\26.1.2-NeoForge_26.1.2.109\mods`（`neoforge-26.1.2/build.gradle` 的 `packModsDir` 早已改为 `26.1.2 模组测试`，测试侧未跟改）⇒ 两处统一改为 **`D:\.minecraft\versions\26.1.2 模组测试\mods`**；本地 `mt.conf` 补 `MT_PACK_MODS_NEOFORGE_26_1_2` 键；`deploy.ps1`（本地忽略文件）补 `-Target 26.1.2` 映射，使三条线共用同一部署入口。
- **CHANGELOG 口径**：本批全部属**工程/工具/文档口径**变更（合并、worktree 移除、工具链路径、守则），按既有约定**不进玩家侧 CHANGELOG**（两份 CHANGELOG 本批零改动），统一归档到本附录。

**2026-09-17：26.1.2 线测试能力扩展**

- **新增优化类模组 ImmediatelyFast + ModernFix（用户要求「进一步验证优化类模组兼容性」）**：`mt_env.ps1 mods --version 26.1.2` 从 Modrinth Maven 装 `immediatelyfast:adbrNJLm`（**1.15.3+26.1-neoforge**，`ImmediatelyFast-NeoForge-1.15.3+26.1.jar`，sha1 `bb10bdde…`）与 `modernfix:j7EoxpYe`（**5.27.22+mc26.1.2**，`modernfix-neoforge-5.27.22+mc26.1.2.jar`，sha1 `334500dd…`），缓存 `temp/probe_mods/26.1.2/`。
  - **侧别不同、处理分开**：ImmediatelyFast 在 Modrinth 标为 `client_only` ⇒ 已加入 `Invoke-MtEnvWorld` 的「纯客户端模组移出」子串名单（`immediatelyfast`），生成世界时移出、结束自动恢复（实测生成后文件已回归、无 `.disabled` 残留）；ModernFix 是 `client_or_server_prefers_both` ⇒ 生成世界时**保留**（服务端同样拿到启动期优化）。两段式数据生成（`runClientData`/`runServerData`）前仍须手工移出 run/mods 的纯客户端模组（含 ImmediatelyFast）。
  - **装载可见性**：`mt_launch` 回显 `IMMEDIATELYFAST_LOADED` / `MODERNFIX_LOADED`，判据取**已加载模组列表行**里的括号 modId（`(immediatelyfast)` / `(modernfix)`）—— 与史莱姆压制闸门同一教训：只搜名字会把存档里的 `<modId> (version X -> MISSING)` 误判成「已加载」。这两条是 **WARN 级**（与 Sodium/Iris 一致，不做硬失败），但缺失时必须看得见，否则「兼容性验证」会静默地什么都没验证。
  - **实测（2026-09-17 冷启动，两个模组均装载：`IMMEDIATELYFAST_LOADED=true` / `MODERNFIX_LOADED=true`）**：`MIGRATION-SMOKE` **27/27 PASS**、`PORTED-PROBE-SMOKE` **41/41 PASS**、`CRAFT-SMOKE` **21/21 PASS**、`SHADER-VISION` **11/11 PASS**；四例均无新增崩溃报告、KubeJS 0 错误 ⇒ ImmediatelyFast 1.15.3 + ModernFix 5.27.22 与本模组、Sodium 0.9.1、Iris 1.11.4、Complementary Unbound 光影**无冲突**（光影用例的视觉读数仍为「体积云/大气散射/色调映射 + 游戏存活」）。
  - ⚠️ **工具链踩坑（已修，务必保持按族清理）**：为复用下载逻辑把安装抽成了 `Install-MtSpecList`，第一版给它传了**一张全局族前缀表**，于是「装史莱姆压制」那一趟把上一趟刚装好的 Sodium/Iris 当成「不在本次规格里」删掉了（每趟只知道自己的 `$installed`），run/mods 里渲染栈凭空消失、看起来像下载失败。现改为**每次调用按族显式传 `-Prefixes`**（渲染栈 `sodium-`/`iris-`、史莱姆 `superflatworldnoslimes-`/`collective-`、优化 `immediatelyfast-`/`modernfix-`），并用 `mt_env mods` 复跑确认三族 jar 共存（10 个 jar 全在）。

- **【用户硬性要求】测试环境必须装「Superflat World No Slimes」——超平坦世界的史莱姆会严重干扰测试流程（已完成，含 A/B 实测）**：测试世界是超平坦、玩家常驻 `y=-60`、难度 `EASY` ⇒ y<40 的史莱姆区块持续刷怪。**A/B 实测证据（同一世界同一位置，`/astralprobe slimecheck`，128 格半径）**：
  | 状态 | 读数 |
  |---|---|
  | **未装**该模组（对照，用 `MT_ALLOW_NO_SLIMEGUARD=1` 显式放行） | `slimes=103:mobs=115:radius=128:difficulty=EASY:y=-60` |
  | **已装**（`mt_env mods` 装好） | `slimes=0:mobs=14:radius=128:difficulty=EASY:y=-60` |
  对照行里 `mobs=115`/`mobs=14` 与 `difficulty=EASY` 一并报出，正是为了排除「整体不刷怪 / peaceful」这类假阴性 ⇒ 结论：该模组**只**压掉史莱姆，其余刷怪照常，故不能用「关掉刷怪」替代。
  - **安装**：`mt_env.ps1 mods --version 26.1.2` 从 **Modrinth Maven** 拉 `superflat-world-no-slimes`（`maven.modrinth:superflat-world-no-slimes:Onb8latt`，`superflatworldnoslimes-26.1.2-3.6.jar`，sha1 `d47af65d…`）**及其 required 前置** `collective`（`maven.modrinth:collective:iXqgYZEw`，`collective-26.1.2-8.32.jar`，sha1 `13887a5d…`），缓存 `temp/probe_mods/26.1.2/`，与渲染栈共用新提取的 `Install-MtRemoteMod`（尺寸 + sha1 幂等，失败硬报 14，**不静默降级**）。两者都带 `META-INF/neoforge.mods.toml`（modId `superflatworldnoslimes` / `collective`），且都是**服务端/运行期**模组 ⇒ `mt_env world` 的「纯客户端模组移出」名单（imblocker/sodium/iris/embeddium/oculus 子串匹配）**不含**它们，专用服务器生成世界时照常保留。
  - **强制闸门**：`mt_launch` 进入世界后检查启动日志的**已加载模组列表行**，缺 `(superflatworldnoslimes)` 即 `MT_LAUNCH: ERROR`（拒绝带着会被史莱姆污染的现场继续跑用例）；报 `SLIMEGUARD_LOADED=true` / `COLLECTIVE_LOADED=true|false`。确需「没有该模组」的对照实验时用 **`MT_ALLOW_NO_SLIMEGUARD=1`** 显式放行（留 WARN 痕迹）。⚠️ **判据必须匹配带括号的 modId**（`\(superflatworldnoslimes\)`）：存档 `level.dat` 记着上次带着它跑过，缺失时 NeoForge 打印 `superflatworldnoslimes (version 3.6 -> MISSING)`，只搜名字会把「缺失」误判成「已加载」——**实测踩坑**：那次对照实验被误报成 `SLIMEGUARD_LOADED=true`，闸门形同虚设。变异验证（同一构建、仅移出两个 jar）已确认修复后**双向**成立：装好 → `SLIMEGUARD_LOADED=true`；移出 → `MT_LAUNCH: ERROR — 未检测到「Superflat World No Slimes」模组…`。
  - **取证命令**：探针新增 `/astralprobe slimecheck <tag>` → `AP_<tag>_SLIME:slimes=n:mobs=m:radius=128:difficulty=<d>:y=<y>`（只读；同时报同半径内的 `Mob` 总数与世界难度，作为「刷怪确实开着」的对照）。

- **筹码重登缺陷的修复与最终验证（用户裁决 = ① 产品侧自管迁移 + ③ 上游补丁；插桩已移除）**：产品侧新增 `event/ChipSlotMigrationHandler`（`OnDatapackSyncEvent` **HIGHEST** 快照并清空筹码栏 → **LOWEST** 按骰子重算尺寸后按索引还原；放不下的交还背包；快照先于清空、按索引覆盖写、残留快照下次先交还 ⇒ 不复制不丢失），并把 `DiceCurioItem#setSlotCount` 的非强制分支从「目标瞬时读成 0 就收缩」改为「抬到最靠后非空槽位 + 1」（根除第二条弹出路径）。上游补丁在 `temp/curios_src` 分支 `fix/26.1.2-loadinv-size`（`9704c4c`，`git format-patch` 产物 `temp/curios-fix-26.1.2-loadinv-size.patch`），缺陷报告与推送/PR 命令在 `docs/upstream/curios-26.1.2-loadinventoryconfiguration.md`（本机无 token/gh，未推送）。**验证（插桩移除后的最终口径，2026-09-17 实跑）**：`CHIP-RELOG-A-26.1.2` 21/21 PASS（0 号位 `flashlight_chip` + **1 号位 `cutter_chip`**，`chipItems=[0:…x1,1:…x1]`，含 `saveall`）→ `--phase stop --force`（保留存档）→ `--phase launch` → `CHIP-RELOG-B-26.1.2` **10/10 PASS**（`chipSlots=2:chipCosmetic=2`、两个槽位筹码均在、无异常行、无新增崩溃报告）。定位期用的只读插桩 `debug/CurioSlotTrace`（`AP_CURIOTRACE|`）已按用户「完成定位后移除」的要求删除，判据改由用例断言给出；探针相应增补 `equipslotat <slotId> <index> <itemId> <tag>`（往指定索引写，多槽位留存回归必需）与 `readstate` 的 `chipItems=[索引:id,…]`（全量非空槽位，**追加在行尾**，历史断言不受影响）。
- **按用户要求新增「光影开启」验证项 `SHADER-VISION-26.1.2`（视觉识别 + 游戏内手动开启 + 崩溃判定）**：用例 = `screenshot(crop SH_OFF)` → **vision**「是否已开启光影」→ `inject_key k`（Iris `iris.keybind.toggleShaders`，与界面 Apply 同一入口 `IrisApiV0ConfigImpl.setShadersEnabledAndApply → Iris.reload → createPipeline`）→ `wait 5s` → `assert log "Using shaderpack:.*ComplementaryUnbound"` → `screenshot(SH_ON)` → **vision**「游戏窗口是否还在/是否已呈现光影」→ `assert absent "Missing sampler Sampler1|Unreported exception thrown"` → `assert crash` → `assert kubejs`。**实测结论（2026-09-17，两轮）**：
  - **Sodium 0.9.2 + Iris 1.11.4（首轮）**：`Using shaderpack:` 命中（开启动作确实走到建管线）→ 立即 `IllegalStateException: Missing sampler Sampler1` → 新增崩溃报告 `crash-2026-09-17_10.53.08-client.txt` ⇒ 用例 = FAIL；视觉读数 `SH_ON`（当时未带 crop，游戏窗口在第二显示器 ⇒ 抓到的是主显示器桌面）**画面里没有游戏**，与崩溃报告一致。
  - **Sodium 0.9.1 + Iris 1.11.4（二轮，用户要求降级并与整合包对齐）**：**用例 = PASS（11/11）** —— `Using shaderpack: ComplementaryUnbound_r5.9.3.zip` 命中、`Missing sampler Sampler1|Unreported exception thrown` **未出现**、**无新增崩溃报告**、KubeJS 0 错误；视觉读数（两张都带 `crop`，相隔约 5 秒、同一机位、白天）：`SH_OFF` = 原版观感（**方块状原版云**、无阴影/无大气散射、均匀草地受光），`SH_ON` = **光影生效**（体积云 + 大气散射/地平线雾 + 色调映射），且游戏内聊天栏出现 Iris 的「光影包已切换到 ComplementaryUnbound_r5.9.3.zip！」⇒ **游戏存活并已渲染光影**。
  ⇒ 结论更正：**Sodium 0.9.2 是 26.1.2 上「开光影即崩」的元凶**（0.9.1 正常）。首轮的「上游无解缺陷、与版本无关」判断是在**只换光影包、没换 Sodium 版本**的对照下作出的，属误判，已作废。本用例的价值因此变成**渲染栈版本的回归守卫**：任何 Sodium/Iris 版本变更都必须复跑它。
  ⚠️ 抓图必须带 `crop: true`：游戏窗口在第二显示器，`mt_capture` 不带 crop 时抓的是主显示器整屏，只会拿到桌面（实测一次被误读成「游戏已消失」）。
  - 工具链随之新增三项：① `mt_case` 支持用例级键 `"expect_crash": true` —— 被验证的就是「会不会崩」的用例（客户端必崩、崩=缺陷仍在）在该键下不再被收尾校验改判为 `ERROR`（原先会伪装成工具链故障并消耗一次自动重启预算去重跑），判定完全交给用例断言，且不置位 `.mt_keep_alive`；② `mt_inject` 键表新增 `k`/`o`/`r` 与语义键 `shadertoggle`/`shaderscreen`/`shaderreload`（键位取自安装 jar 字节码：`Iris#onEarlyInitialize` 里 `toggleShaders`=GLFW 75、`shaderPackSelection`=79、`reload`=82）；③ `mt_env.ps1` 新增**离线幂等**子命令 `shaders --state off|on|status`（只改 `config/iris.properties` 的 `enableShaders` 一行，不碰缓存不联网）。
  - ⚠️ **为什么必须有 ③（实测踩坑）**：游戏内的光影开关会**持久化** `enableShaders=true` ⇒ 跑完光影验证（或手动点过 Apply）后，**下一次冷启动会在进入世界的第一帧崩**（launch 阶段 `MT_LAUNCH: ERROR — 进入世界后立即崩溃`，指向 `crash-reports/crash-2026-09-17_10.49.50-client.txt`），后续任何用例都跑不起来。故光影回归的前置与收尾都应是 `pwsh -File scripts/test/mt_env.ps1 shaders --version 26.1.2 --state off` + 冷启动。

- **筹码重登缺陷定位（源码级 + 实测双证据；只读插桩，不修产品行为）**：clone Curios 源码（GitHub `TheIllusiveC4/Curios`，`26.1.2` 分支 = `8f2f132`/15.0.0，本地 `temp/curios_src`）后确认根因 = `CurioInventory#loadInventoryConfiguration()` 的搬移循环上界取「**数据包原始尺寸**」：`CurioStacksHandler#getSlots()` → `update()` 首行 `if (this.dataLoaded)`，而 `setDataLoaded()` 在方法**末尾**才调用 ⇒ 循环期间读到的是构造函数里的 `stackHandler` 尺寸（= 数据包 base），与 `copyModifiers()` 刚复制来的修饰符无关；本模组 chip 槽 `size:0` ⇒ 上界 0 ⇒ 旧内容全部进 `invalidStacks` → `handleInvalidStacks()` → `ItemHandlerHelper.giveItemToPlayer()`。
  新增**只读插桩** `debug/CurioSlotTrace`（`AP_CURIOTRACE|` 前缀；订阅 `OnDatapackSyncEvent` `HIGHEST`=迁移前 / `LOWEST`=迁移后、`PlayerLoggedInEvent` 与随后 1/2/3/10/20/40 tick、`CurioCanEquipEvent` 仅在迁移窗口内记录；开关 `-Dastral_dice.curioTrace`，默认「开发环境开、生产关」；**只读、不抛异常、不改状态**，关闭时为空操作），并在 `DiceCurioItem#applySlotCount` 增只读打点 `NOTE|applySlotCount`。判据性证据：迁移期间**只出现 `slot=dice` 的 `CAN_EQUIP`，`slot=chip` 一次都没有**（⇒ 不是校验器/标签挡下）＋ `SYNC_AFTER` 的 `chipSlots=4`、`chipStacks` 全空、`invChips=[2:astral_dice:flashlight_chipx1]`、`groundChips=[]`。另发现两条同源事实：① `size_shift` 使登录瞬间筹码栏 4 格（几 tick 后由 `CuriosCommonEvents:613 clearCachedSlotModifiers()` 抹回 2）；② 本模组 `curioTick` 在迁移当拍把目标读成 0 而防御式收缩到 0 → **第二条独立弹出路径**。
  探针新增只读命令 `/astralprobe invdump <tag>`（主物品栏 + 副手 + 16 格内地面掉落物里的筹码），用于区分「交还背包 / 掉地上 / 被销毁」。用例 `CHIP-RELOG-B-26.1.2` 保持红色（缺陷仍在），其 note 与 `AGENTS.md` 已写入根因与三条候选修法（自管迁移 stash&restore / 数据包 base 改 1 / 上游修复）。

- **新增「测试后收尾」能力（用户规则：测试任务完成后关闭测试端、清理旧存档数据、避免进程长时间驻留）**：`mt_cleanup.ps1` 新增 `--purge-saves`（收停进程**之后**删除 `run/<版本>/saves/<世界名>`、`run/<版本>/<世界名>` 及 `saves/` 下其它含 `level.dat` 的历史遗留世界，回显 `MT_CLEANUP_SAVES: PURGED <N>` / `KEPT`）；`mt_stop.ps1` 与 `mt.ps1 --phase stop` 逐层透传，`mt.ps1` 的全流程退出清理**默认**带 `--purge-saves`。`--phase stop` 默认**不**清（重登类用例要跨 stop 保留存档：`saveall` → stop → launch），失败取证标记 `.mt_keep_alive` 在场时收停与清存档一并 SKIP。

- 探针新增子命令（26.1.2 版 `astral_bugfix_probe.js`，共 56 条）：`recipecheck`（配方装载总数/本模组数/关键 id 存在性）、`craftcheck`（按 `Recipe#placementInfo()` 自建 `CraftingInput` 跑 `matches()+assemble()` 的**真合成**体检）、`meleecheck`（直接调用产品静态方法逐个换手物品验证近战判定，含 26.1.2 新增长矛）、`saveall`（显式 `MinecraftServer#saveEverything`，为「强杀式重登」测试提供存档点）；`readstate` 增补 `chipCosmetic`（stacks/cosmetic 尺寸恒等断言）与 `chipItem`（筹码留存断言）。
- 新增用例 3 条：`CRAFT-SMOKE-26.1.2`、`CHIP-RELOG-A-26.1.2`、`CHIP-RELOG-B-26.1.2`（后者为两阶段「重登」流程；`mt.ps1 --phase stop` 是强杀不存档，故准备阶段必须显式 `saveall`）。
- 修工具链踩坑：「注入命令 + 长命令链」在超时被强杀时会连带杀掉**同一进程树里的游戏客户端**（实测一次），长流程请拆成多次调用、不要把 `stop → launch → case` 串在一条命令里。
- 探针 `equipslot`/`putInSlot` 为**绕过校验的直接写入**，与真人操作不等价；用它做「跨存档留存」类断言时必须选**校验通过**的物品（筹码 → `curios:chip` 标签内），否则会得到假缺陷。
- **测试环境渲染栈（26.1.2，按用户裁决全部改走 Modrinth Maven）**：`mt_env.ps1 mods --version 26.1.2` 新增 `Install-MtRenderStack`，从 `https://api.modrinth.com/maven/maven/modrinth/<slug>/<version>/<file>` 拉取并**体积 + SHA1 双校验**（缓存 `temp/probe_mods/26.1.2/`）：**Sodium `mc26.1.2-0.9.1-neoforge`**（2026-09-17 用户要求「降到 0.9.1，与整合包一致」；见下方光影条目的更正）、Iris `1.11.4+26.1-neoforge`、光影包 Complementary Shaders - Unbound `r5.9.3`（落到 `run/26.1.2/shaderpacks/`），并写 `run/26.1.2/config/iris.properties`。**`enableShaders=false` 是刻意默认**（测试不需要光影、省性能），**不是因为崩**——Sodium 0.9.1 下开光影正常（见下）。阶段 L 回显 `SODIUM_LOADED` / `IRIS_LOADED` / `SHADERS=… pack=…` / `SHADERPACK_LOADED=…`（仅启用时才校验 `Using shaderpack:`，关闭时报 `n/a` 而非 WARN）。⚠️ 同批新增**渲染栈旧版本清理**：换了版本号以后 `Install-MtRemoteMod` 只放新文件、不删旧文件，`sodium-*`/`iris-*` 会新旧并存 ⇒ FML 报重复模组拒绝启动；故安装后按前缀 `sodium-`/`iris-` 删除不在本次规格中的 jar（前缀不带通配符，不会误删 `reeses-sodium-options-*`）。
- **测试客户端搬到第二显示器并固定窗口尺寸（`--monitor` / `--size`，2026-09-17 用户要求「移到第二显示器，避免干扰观察」＋「窗口大小应控制在 1920x1080」）**：`lib/Mt.Win32.psm1` 新增 `Get-MtMonitors`（WinForms `Screen::AllScreens` → Index/Device/Primary/X/Y/W/H）、`Get-MtClientRect`（客户区=渲染区）与 `Move-MtWindowToMonitor`（**SW_RESTORE → SetWindowPos(居中) → 视参数 SW_MAXIMIZE**；已最大化的窗口直接 `SetWindowPos` 不会跨屏）；`mt_launch.ps1` 进入世界后（注入之前）查找窗口并搬移，默认显示器 **#1**、默认尺寸 **客户区 1920x1080**（`--monitor 0` 不搬移；`--size maximize|keep|<W>x<H>`；越界/单屏静默 `SKIP`）。`MT_WINDOW: OK` 回显「窗口矩形 + 客户区尺寸」，实测 1920x1080 客户区 ⇒ 外框 1936x1119。⚠️ **`--size` 按客户区口径**（外框 1920x1080 只会得到约 1904x1041 渲染区），边框差值由「外框 − 客户区」实测补足。⚠️ `Get-MtMonitors` 必须用 `@(...)` 接收：**不要**在函数里 `return ,$out`，那会把数组包成「一个元素」，`$mons.Count` 恒为 1、`$mons[1]` 取到整个数组（实测踩坑，已修正并有注释留痕）。
- **就绪标记「先满足、后崩溃」的收口（2026-09-17）**：`mt_launch` 在就绪闸门之后新增一次崩溃报告复查（按 `LastWriteTime` 过滤本次启动之后的报告）。背景：26.1.2 开启光影时客户端在世界渲染首帧崩（`Missing sampler Sampler1`），而 `logged in with entity id` / `Loaded N advancements` **早已写入日志** ⇒ 旧实现会判「已就绪」并继续搬窗口/注入，下游只报出一串与真因无关的「客户端未在运行 / 注入失败」；现在直接 `MT_LAUNCH` 失败并指向具体 crash 文件。

- **修正 26.1.2 侧的整合包推送目标（2026-09-17 用户要求「添加整合包目录 `D:\.minecraft\versions\26.1.2 模组测试`，遵循与其他版本同规则」）**：`neoforge-26.1.2/build.gradle` 的 `packModsDir` 原指向 `D:/.minecraft/versions/26.1.2-NeoForge_26.1.2.109/mods`，该目录在本机**并不存在** ⇒ `pushToGame` 每次都只打印 `pushToGame: pack dir not found, skipped`，整合包里的 jar 长期停留在旧时间戳（实测残留 `2026-09-17 02:44:57`）。现按 1.20.1 侧 `1.20.1 模组测试` 的同名规则改为 **`D:/.minecraft/versions/26.1.2 模组测试/mods`**，并把该任务的旧产物清理匹配从「任意 `astral_dice-*.jar`」收紧为带完整后缀 `contains('+neoforge_26.1.2')`（整合包目录属用户环境，不得误删其它分支放进去的产物）。判定依据：构建日志出现 `pushToGame: pushed astral_dice-<版本>+neoforge_26.1.2.jar -> D:\.minecraft\versions\26.1.2 模组测试\mods`，且该目录内本模组 jar 有且仅有一个、时间戳等于本次构建。⚠️ 教训：`pushToGame` **静默跳过**是最容易发生的形态，只看 `BUILD SUCCESSFUL` 无法发现「其实没部署」，必须核对 `pushed` 行（已写入 `AGENTS.md` 的「编译产物上传规则」第 3 条）。

- **光影崩溃的上游定性复核（2026-09-17，用户问「为何启用光影立即崩、是否用了最新 Sodium/Iris」）**：⚠️ **本条结论已被同日后续实测推翻，保留作为方法与教训记录** —— 真正的元凶是 **Sodium 0.9.2**（见「按用户要求新增『光影开启』验证项」条目的二轮结果：降到整合包同款 0.9.1 后光影正常）。本条当时的判断是「已是最新版本、属上游 Iris/Sodium 未修缺陷、与版本无关」，错在**对照只换了光影包、没换 Sodium 版本**。保留下来的部分仍然成立且有用：① **版本核对方法**（Modrinth API 实测）：Sodium 在 `26.1.2` 的最新发布是 `mc26.1.2-0.9.2-neoforge`（release，2026-09-11），Iris 最新为 `1.11.4+26.1-neoforge`（release，2026-09-13），其 Modrinth 前置声明 `version_id=zg4YQ9EL` 指向 Sodium `mc26.1.2-0.9.2-neoforge`（**注意：Modrinth 的依赖只是「构建时对齐」，FML 元数据里 Iris 的 sodium 区间是宽松的 `[0.6,)` ⇒ 装 0.9.1 不会被依赖闸门拒绝**，这也是降级可行的前提）。② **崩点源码**（本地 `minecraft-patched-26.1.2.109-sources.jar` 实读）：`GlCommandEncoder#trySetup` 第 526~532 行要求 `renderPass.pipeline.program()` 里每个 `Uniform.Sampler` 都能在本次 draw 的 `renderPass.samplers` 里找到同名绑定，否则 `IllegalStateException: Missing sampler Sampler1`（`Sampler1` 是原版管线的自动命名）——崩在**启用后的第一帧 draw**，而不是建管线时；这正是「Sodium 版本换了以后采样器绑定表不再一一对应」的表现。③ **为什么「立即」崩**：本机两次崩溃调用链分别是 `GuiRenderer.executeDraw`（点 Apply 那帧）与 `LevelRenderer.addMainPass → MultiBufferSource.endBatch → RenderType.draw`；上游 Iris 同刻自报 `[Iris/FATAL]: Missing program minecraft:pipeline/gui_text in override list. This is likely an Iris bug!!!`（`gui`/`gui_textured`/`panorama`/`blur/0..5` 同样缺失）。④ 上游同类 issue：Iris [#3314「26.1.2 Game Crash」](https://github.com/IrisShaders/Iris/issues/3314)（open，2026-08-31，报告者用的是 **Iris 1.11.3 + Sodium 0.9.1** —— 与我们的二轮组合不同，故**不能**据此推断 0.9.1 一定有问题；当时把它当成「0.9.1 也崩」的证据是**过度外推**）、[#3182](https://github.com/IrisShaders/Iris/issues/3182)、[#2719](https://github.com/IrisShaders/Iris/issues/2719)。⑤ 次要发现（**不是**崩溃原因）：`Failed to resolve uniform inSulfurCaves, reason: Unknown variable: BIOME_SULFUR_CAVES` —— Complementary Unbound r5.9.3 的自定义 uniform 引用了 Iris 1.11.4 不认识的地物常量。（原第 ⑥ 条「维持 `enableShaders=false`、光影测试阻塞于上游」已作废：现在光影可用，默认关闭只是「测试不需要 + 省性能」。）

以下条目描述的是**测试工具链与发布流程**（非模组内容），自 1.2.0 的 `CHANGELOG*` 移出并归档于此：

- pwsh 工具链 9 项修复：env 种子包按版本判定；`Find-MtMinecraftWindow` 增加「标题含版本号」回退（DevLaunch 用短命令行 + args 文件，原判据永远失败）；断言新增 `scope: whole` 与 screenshot `crop`；赋能用例增加自然递减正例；探针增补 `AP_<tag>_EMPOWER_DUR`；新增 `Start-MtDetached.ps1`；`mt_env world` 强制写世界规则（退出码 11）；`mt_case` 自动写报告状态；电磁炮用例扩展为完整验证（真实延迟量测 + 冷却守卫）。
- 测试环境集成 JEI（forge 15.56.0.205 / neoforge 19.39.0.372）；1.20.1 dev 的整合包模组改为 curse maven `modImplementation` 依赖，修复 dev 环境无法加载生产 mixin 模组的问题。
- 测试环境集成 ModernFix：1.21.1 由 `install_test_mods.ps1` 复制进 `run/1.21.1/mods`；1.20.1 经 build.gradle 注入 dev classpath；launch 验证改为识别 ModernFix 的加载完成日志。
- 工具链由 bash + python 全面迁移为 PowerShell 7：28 个脚本改写为 `.ps1`（抽出 `lib/Mt.{Conf,Paths,Phase,Proc,Win32}.psm1`），入口统一为 `mt.ps1`，判定逻辑与输出逐字节等价（比对时仅归一 CRLF）；CI 的 lang 同步检查改 `pwsh`；原 bash/python 原件已删除（历史保留在 git）。
- 构建默认自动推送整合包：双版本 `gradlew build` 同时推送 `run/mods`、根目录 `build/libs` 与各自整合包 mods 目录（原需 `-PdeployToPack`）。
- 修复 CI 分支过滤器指向已废弃分支名：`.github/workflows/build.yml` 由带斜杠旧名改为 `multi-1.20.1-1.21.1`（否则推送不触发 CI，lang 同步与双版本构建守门失效）。
- 修复 CI 自动打 tag 的版本解析（2026-09-12）：原 `BASE=${VERSION%%-*}` 只剥 `-rc/-pre`、**不剥 `+加载器` 后缀**，因此 `mod_version=1.2.0+neoforge_1.21.1`（不含 `-`）会被原样当作 tag，生成违反「tag = 无后缀基础版本号」规范的名字；现改为先 `%%+*` 再 `%%-*`，并新增裸 `x.y.z` 守门（不合规则跳过打 tag 并 `::warning::`）。
- 发布流程改为**同 job 打 tag 后直接发 Release**（2026-09-12，承接上一条暴露的缺口）：原设计把发布拆成两步——「tag push 才发 Release」，而 tag 由 `GITHUB_TOKEN` 推送、**不会再触发本 workflow**，于是每次自动打 tag 后 `Create/Update GitHub Release` 都被 skipped（实测 Run #36：tag 步骤 success、Release 步骤 skipped，`1.2.0` 的 Release 只能事后人工补推 tag 才产生）。现改为：打 tag 步骤加 `id: tag` 并输出 `tag=<基础版本号>`，Release 步骤的条件改为「ref 本身是 tag（tag push，或在 tag 上手动 `workflow_dispatch` 补发）**或** 发布线分支 push 且上一步已产出 tag」，与构建同 job 完成；已存在同名 Release 时走 `gh release edit` + `gh release upload --clobber` 刷新附件，使重复发布幂等（附件始终等于本次构建产物）。
  - 条件真值表（本地按 GitHub 表达式语义逐条核对，7/7 通过）：发布线分支 push 且版本合规 → 打 tag + 发 Release；版本含 `-rc/-pre`（守门跳过）→ 均不执行；tag push → 发 Release；PR → 均不执行；分支上 `workflow_dispatch` → 只构建；tag 上 `workflow_dispatch` → 补发 Release；其它分支 push → 不发布。
  - `run:` 脚本已用 `bash -n` 校验语法，并以桩 `gh` 在本地实跑「Release 已存在 / 不存在」×「分支 push / tag push」四种组合，命令行参数与预期完全一致（`edit` + `upload --clobber` / `create` + `--latest`，两个 jar 均正确展开）。

**2026-09-12 仓库清理记录**：删除已失效的事件系统链路（`AstralEvents`/`AstralEventType`/`EventContext`/`EventEffect`/`trigger`）、`ArmorPenaltyHandler` 与附件 `armor_penalty_end`、女仆目标收集链路与配置项 `event_range`/`event_apply_maid`（配置版本 1→2）、孤儿 `lib/junit-platform-console-standalone-6.1.2.jar`、GameTest 残留（`SignSkillTests` + 结构 NBT + run 配置）、3 个无引用语言键；删除弃用的 `FLOW_1.20.1_functional.md`（内容并入本文件）。

### 1.2.1 工程记录（自 CHANGELOG 移出）

- 工程:版本号升至 **1.2.1**(`mod_version=1.2.1+neoforge_1.21.1` / `1.2.1+forge_1.20.1`);互通号仍为 `1.2`,故 **1.2.0 ↔ 1.2.1 保持双向互通**(Version Gate 只比较二号位,不受补丁号影响);中英更新日志同步开启 `未发布(1.2.1)` 小节,1.2.0 小节冻结为已发布状态。
- 工具:修复 `scripts/test/mt_build.ps1` 的产物提示——版本号切换后子项目 `build/libs` 会同时留有**旧版本** jar(Gradle 不清理改名前的旧产物),而成功提示取的是**序数排序快照的首行**,于是打印出旧版本文件名(本轮实测:实际产出 `1.2.1`,提示却写 `1.2.0`);现改为取 mtime 最新者(新增 `Get-MtNewestJarName`),提示与实际产出对应。判定逻辑未变(仍是 before/after 快照差异),且**只影响提示**。
- 工程:自动化测试套件新增两条回归条目及配套探针命令——`RAILGUN-AOE-SCOPE`(双版本:取证电磁炮雷击对落点箱内**自己/中立/友方/敌方**四方目标的实际命中范围,并复核「充能与冷却只在雷击真正落下时结算」)与 `LOOT-MODIFIER`(1.20.1:断言序列化器注册表含 `astral_dice:add_table`、`getAllLootMods() = 13`、400 次宝箱实滚的星盘产出数,并要求日志无解码失败);探针命令为 `/astralprobe railgunfriendly|railgunfriendlyread|railgunfriendlyend`(双版本)与 `/astralprobe glmcheck`(仅 1.20.1);另增真伤取证命令 `/astralprobe truedmg|railtruedmg|railtruedmgread`(双版本)并把「真伤判据」写入 `TESTING-SPEC.md`——两条实测结论同时记录在案:① `MinecraftServer#runCommandSilent` 在 Rhino 下返回 undefined 且**命令不执行**,探针统一改走 `performPrefixedCommand`;② 属性类命令的生效时机晚于同一 tick 的后续代码,故穿甲对照必须分两条命令做
- 工具:自动化测试流程**彻底移除 mineflayer/MCP 机器人路线**——`scripts/test` 不再包含 `--publish`(本地端口映射)、`mcp_call` 步骤原语、`mcp` 断言类型,以及经 `cases/.mcp-pending.json` ↔ `.mcp-results.json` 委托给会话层的 MCP 调用与其 `Get-MtMcpResults`/`Add-MtMcpPending`/`Wait-MtMcpResult` 辅助函数;`Mt.Paths.psm1` 的 `$PUBLISH_PORT`/`Get-MtPublishPort`、`mt_launch.ps1` 的 `/publish` 启动参数及各处文档说明一并删除,**其余键鼠注入与用例链路不变**(移除后 `mt_preflight.ps1` 9 项、`SMOKE-TOOLCHAIN` 用例仍全绿)。移除依据是实测该路线不可行:NeoForge 1.21.1 的局域网服务器在**配置阶段**直接拒绝原版客户端(`你正在尝试连接一个安装了 NeoForge 的服务器…请安装 NeoForge 版本 21.1.235`),Forge 1.20.1 的 `NetworkRegistry` 以 `Channels [astral_dice:main,patchouli:main,curios:main] rejected vanilla connections` 拒绝未携带各通道 `ACCEPTVANILLA` 标记的握手(`patchouli`/`curios` 通道不声明该标记,本模组无法代为放行);机器人侧一律表现为 `ECONNRESET`/`socketClosed`。游戏内输入继续由 `computer-control` 键鼠注入(`mt_inject.ps1`)承担。
- 工具:修复 `scripts/verify/verify_chip_recipes.ps1` 的 jar 档——其产物 jar 名此前硬编码 `$VER = '1.2.0'`,版本升到 1.2.1 后一直在找不存在的旧 jar 而**误报失败**(源码档与生成资源档本身一直是全绿);现改为从各子项目 `gradle.properties` 的 `mod_version` 派生,三档 × 双版本恢复全绿(122 个配方文件 / 59 个筹码一致)。
- 工具:修复 `scripts/test/mt_inject.ps1` 的**右键长按缺失**——`key -Key rclick -HoldMs N` 此前**静默忽略** `-HoldMs`(始终 down→100ms→up 的单击),于是「长按右键」类回归(效果牌一次按下只出一张)在自动化里根本走不到原版 4 tick 自动重复分支,单击也能"通过"。现按 `w` 键同一口径实现按住语义(按下 → 停顿 HoldMs → 抬起,`-HoldMs 0` 保持旧行为),输出行回显按住时长;并在 `EFFECT-CARD-HOLD` 用例里加入**对照步**——生存模式下以 16 枚鸡蛋长按 3 秒后必须只剩 1 枚(即原版 15 次右键),该对照不成立则修复判据不成立。
- 工程:`AGENTS.md` **纳入版本库**(`.gitignore` 中针对它的排除项移除,自 2026-09-15 起随提交入库;是否推送 GitHub 仍按「仅在用户明确要求时执行」),文件内两处「AGENTS.md 不入库/为本地共享文件」的说明同步改写;并删除 1.20.1 `KomachiSignItem` 中**未使用**的 `CuriosApi` import(该文件实际走 `CuriosCompat`)。
- 工程:`AGENTS.md` 的玩家附件清单补齐**漏列**的 `effect_card_bonus_plays`(出牌轮一次性 +1 标记;该行自称 61 个却只列了 60 个),并补记附件侧的机制约束——**死亡重生保留的键仅 `rin_pages` 与 `komachi_damage_bonus` 两个**、两版本保留集合必须一致、且死亡清理不得清除这两个键。
- 工程:把 **1.20.1 的硬前置 Mixin Booster 纳入回归守卫**,并纠正一处会误导后来者的注释——Forge 1.20.1 的 FML **没有 Mixin 集成**,Sponge Mixin 0.8.5 完全由 `mixinbooster`(纯 ModLauncher 服务 jar,内嵌 `fabric-mixin.jar`;模组条目与版本号由自带 `IModLocator` 读 jar 根 `mixinbooster_version.txt` 得到,实装 `0.1.3+1.20.1`)提供,**未安装时本模组不报任何错、全部 Mixin 静默失效**,因此它自 1.2.0 起就是 `mandatory=true` 的强依赖;本轮补的是**防回归与取证**:① `scripts/test/mt_loadergate.ps1` 新增两步读数——`AP_LG_MB`(模板/生成资源/构建产物**三段一致**地声明 `modId="mixinbooster"` + `mandatory=true` + `versionRange="[0.1.3,)"` + `ordering="AFTER"` + `side="BOTH"`,并断言 `build.gradle` 仍以 `modImplementation` 装配该前置、模板内不得出现未注释的 `reason=` 行)与 `AP_LG_MB_RANGE`(用 FML 依赖排序所用的**同一** `maven-artifact` 判定器,拿产物里真实声明的区间对前置 jar 内的**真实版本** `0.1.3+1.20.1` 判定:必须接纳,`0.1.3` 接纳,`0.1.2`/`0.1.0` 拒绝——旧版本前置同样是静默失效,故区间不能写成 `"*"`);`cases/LOADER-GATE-FORGE-1.20.1.json` 由 6 断言扩到 8 断言(离线实跑 8/8 PASS,并做**变异测试**:仅从模板删掉该依赖段即报 `fail=mb_absent_in_template`、`overall=0`,证明断言不是空转)。② 修正 `mods.toml` 里沿用 MDK 的注释 `# reason="..."`——Forge 1.20.1 的依赖段**根本不支持 `reason`**(实测 `ModInfo$ModVersion` 构造器常量池只读 `modId`/`mandatory`/`versionRange`/`ordering`/`side`/`referralUrl`;`reason` 是 NeoForge 的字段),写了也不会显示给用户;现改写为记录 FML 原生拒绝文案(`Missing or unsupported mandatory dependencies:` + Mod ID / Requested by / Expected range / Actual version),并补一条警示:该模板由 Groovy 模板引擎展开,**注释里也不得出现「美元符号 + 标识符」的裸写法**(只有「美元符号 + 花括号 + 键名」合法)——2026-09-15 实测:注释里写 `$ModVersion` 会让 `:forge-1.20.1:generateModMetadata` 以 `Missing property (ModVersion) for Groovy template expansion` 失败,并把 `build/generated/.../mods.toml` **截断为 0 字节**,随后 1.20.1 客户端直接起不来(已修复并重建验证)。③ `AGENTS.md` 把该前置同时写入「加载器版本门槛」与「forge-1.20.1 子项目关键差异速记」首条(含必须保持同步的三处点位,以及禁止写成 `"*"` 的理由)。④ `README.md` 的前置表(中英两表)把 Mixin Booster 写明为**强制**前置并标注版本下限 `≥ 0.1.3`、未安装/版本过低会在依赖排序阶段被拒。
- 工程:修复 `RAILGUN-AOE-SCOPE-{1.21.1,1.20.1}` 里两处**永不命中**的判定正则(与产品行为无关——旧写法下产品再对也必 FAIL):第 8 条断言原写 `AP_RG_VERDICT:enemy=1`,而探针实际输出 `AP_RG_VERDICT:self=0:enemy=1:…`,即「`AP_RG_VERDICT:` 后紧跟 `enemy=`」这个子串根本不存在;absent 断言里的 `AP_RG_VERDICT:enemy=0` 同病(**反向断言恒为真、形同虚设**)。现分别改为 `AP_RG_VERDICT:.*:enemy=1:` 与 `AP_RG_VERDICT:.*:enemy=0:`,与同族用例 `AP_HN_VERDICT:.*:enemy=1:` 的写法一致(由本轮回归执行体在复跑中定位;两处笔误均为用例编写期引入,与产品代码无关)。同批还修了两处**测试资产缺陷**(产品代码零改动):① `RAILGUN-AOE-SCOPE-{1.21.1,1.20.1}` 缺了同族用例标注为「实测必需」的防污染步骤(`/gamerule doFireTick false` + `/fill … stone`,读命令注入两次)——落雷会在玩家周围点燃地面,`self` 读数混入火焰伤害(实测 1.20.1 `self=3.5` 全部来自火焰),使探针的 `SCOPE_OK` 恒为 0;② `railgunfriendly` 探针的激怒步用 `@e[type=…,limit=1]` 选中的是**全世界最近的同类实体**而非本探针刚摆下的靶——上一轮若 `railgunfriendlyend` 的注入丢失,被激怒的残留靶会留在雷击判定箱内并额外各生成一道雷击(同一探针同一轮实测 `bolt_delta` 1.20.1=5 / 1.21.1=2,且 1.20.1 的 `ANGER_PRE` 竟直接读到 `nAngry=true`,而新摆的熊不可能自带愤怒);现改为**直接调 Java API**(`NeutralMob#setRemainingPersistentAngerTime` —— 与探针一直在用的 `isAngry()` 同接口,既不依赖选择器也不依赖 UUID;API 失败才回退到按类型选择器的命令,并把实际路径暴露在读数里 `api`/`cmd:…`/`ERR:…`);**同类残留的清场改放在 `railgunfriendlyend`**:开场清场在 1.21.1 上会误杀同一 tick 新摆的靶(该版 `performPrefixedCommand` **延迟到本 tick 末**执行,实测 4 个靶全灭、`valive=0:talive=0`、`bolt_delta=0`、充能不消耗),故开场只清**预先存在**的敌对生物(僵尸/骷髅/尸壳/溺尸,24 格内)与原有的苦力怕清场。第一轮曾改用 `e.getStringUUID()` 寻址,实测**两个版本都被 Rhino 拒绝**(`Cannot find function getStringUUID`,与 `playerUuid` 注释里记的 `getUUID` 可见性同源),故未采用。双版本同改。**回填与定性(同日)**:修正后冷启动复跑 —— **1.21.1 `bolt_delta=2`(两次 read 均 2)、`SCOPE_OK:1`、`VERDICT:self=0:enemy=1:neutral=1:friendly=0:turtle=0:villager=0:valive=1:talive=1` 完全符合设计,同批 `HOSTILE-TARGET-NEUTRAL`(24/24)/`RAILGUN-PET-EXCLUDE`(24/24)/`RAILGUN-OVERRIDE-CLASS`(20/20) 三例全绿,故该版期望已回填为 `2`**;1.20.1 实测 `bolt_delta=3` 但**同一轮命中范围异常**(`self=2→6`、`friendly`/`turtle`/`villager` 各掉 12、`SCOPE_OK:0`,即非敌对目标与施放者本人都被雷击命中),该版期望**暂不回填**,异常升级登记为 `TESTING-SPEC` §8.2-1(静态面已排除:两版 mixin 配置同样列出且 1.20.1 应用无报错、`RailgunBolts`/`HostileTargets`/`LightningBoltStrikeScopeMixin` 三文件两版逐字节相同、标记顺序符合要求 → 按该节待办在 1.20.1 上复跑含防误伤断言的三个用例来定性)。**第 3 批冷启动复核（同日）结案**：① 1.20.1 四例（AOE/HN/PE/OC）的 `friendly`/`turtle`/`villager` **全为 0** —— 上一轮「箱内全体被劈」**未复现**，残留的 `self>0` 由旁证定性为**测试世界常驻蜘蛛攻击 `forced_survival` 玩家**（HN 两次 read 之间 `php` −6.0 而 `bolt_delta` 未变；`Dev被蜘蛛杀死了` 出现三次），故**撤回**先前的产品嫌疑升级、改为环境项（`TESTING-SPEC` §8.2-1）；② `bolt_delta` 在干净轮次里也不稳定（1.21.1 两次独立冷启动 4 / 2；1.20.1 四例 6/3/5/3），根因是**世界残留的可命中实体各自多生成雷击**，故**放弃固定值断言**，两版统一改为下界 `bolt_delta=[2-9][0-9]*`；③ 收尾注入丢失已第二次命中（`AP_OC_RESTORE` / `AP_HN_RESTORE` 缺失导致假 FAIL），8 个探针用例统一改为**连续注入两次 `railgunfriendlyend`**（中间 `wait 500ms`，该函数幂等），并新增 `TESTING-SPEC` §10 第 17 条。以上均只动测试资产，**产品代码零改动**。
- 工程:自动化测试套件新增**安全气囊回归条目** `AIRBAG-BYPASS-KILL`(双版本)——用与 `/kill` **完全同一条原版代码路径**(`LivingEntity#kill` = `hurt(generic_kill, Float.MAX_VALUE)`)验证气囊对「无视无敌」的致死伤害依然有效:装备气囊 + 6 层充能 + 冷却归零时被致死 → 玩家**存活且血量逐字不变**、充能 6→0、进入 1:00 冷却(`cd_left=1200`);并带**同构造对照**(同一个 `kill()` 调用打一只新生成的猪 → 10→0 必死),排除「伤害根本没生效」的伪阳性。探针新增 6 条命令(`airbagprep`/`airbagkill`/`airbaglethal`/`airbagread`/`airbagnocharge`/`airbagreset`),双版本冷启动各 26/26 断言 PASS。
- **清理过时指向并清空 `temp/` 工作目录(2026-09-15)**:① 全仓扫描断链路径(`.md`/`.ps1`/`.py`/`.js`/`.json` 等文本文件内的相对路径),修掉全部指向已删除文件的引用——21 个 `.ps1` 与 6 个 `.psm1` 模块(共 27 个脚本,含 `scripts/test/lib/*.psm1` 与 `scripts/verify/ChipCommon.psm1`)的「迁移前源文件」注释改为说明「原件已在 `92fbeaf`(工具链收敛为纯 pwsh)删除,`git show 92fbeaf^:<路径>` 可取回」,`mt_case`/`mt_cleanup`/`mt_gen_case`/`mt_report` 的迁移期「文案偏差」注释标注旧 bash 入口已删除,`mt_env.ps1` 去掉对已删原型 `temp/nbt_gate.ps1` 的指向,`verify_content_library.ps1` 的缺文件提示不再指向已删的 `temp/gen_content_library.py`(改为指向 `TESTING-SPEC.md §11`),KubeJS 探针出处注释去掉对已删 `temp/_adapt_probe_1211.py` 的指向;② 删除迁移期比对器 `scripts/devtools/Compare-MtOutput.ps1`(其比对对象 python/bash 原件自 `92fbeaf` 起已不存在,已无法运行),`AGENTS.md` 的 `.ps1` 清单同步更正为 28 个并补记 6 个 `.psm1` 模块;③ 旧脚本归档包 `legacy_scripts_20260912.zip` 从 `temp/` 移出到 `docs/archive/`(保住 `AGENTS.md`/`TESTING-SPEC.md` 记载的取回路径);④ `temp/` 目录**整体清空**(全部会话期脚本与产物移入回收站,只保留目录本身供工具链写运行日志)。
- 工程:自动化测试套件的探针与用例**同步三态化**(`scripts/test` 双版本)——KubeJS 探针新增读数命令 `lockSignId` / `lockRemaining` / `lockGraceRemaining` 与脚手架 `resetActiveLock`(清 `sign_active_lock_sign` / `sign_active_lock_end` / `sign_active_reduction_pool` / `sign_active_lock_grace_end` / `sign_active_lock_played` 这 5 个新键,`resetEffectCardCycle` 一并调用,避免锁定态跨用例残留使读数顺序相关);`doKomachiCast` / `doKomachiRepeat` / `doKomachiCap` 的断言按新口径改写(释放成功不再等于立即起冷却);用例 `KOMACHI-EXTRA-PLAY-{1.21.1,1.20.1}.json` 新增 `K1_GRACE` 断言(`AP_K1_GRACE:cd=1:locked=0`,宽限到期未出牌 ⇒ 强制重置并起冷却、锁定标记清空)并更新被测语义说明。
- 工程:同步文档口径并修正一处类名笔误(2026-09-15)。① `forge-1.20.1` 的 `event/ModEffectRemoval` javadoc 指向**不存在**的 `ModEventHandlers`,改为实际拦截器 `ModEffectEvents#onModEffectRemovalPrevented`(1.21.1 侧本就正确);② `AGENTS.md` 按本批口径更新三处——电磁炮落雷参与带上下文的双参敌对判定(判据点统计由 22→**23** 个两参 / 3→**2** 个单参)、`clearRoundBonuses` 现同时解除电击手套本周期武装且"轮次归零"共**三处**调用、末影骰子保命清理补记"额外移除 `GLOWING`";③ 「饰品卸下」条经逐条核对字节码后**如实改写**:Curios 的 tick 轮询把「第 2 参 = 槽位当前内容、第 3 参 = `getPreviousStackInSlot` 的 copy 快照」交给本模组,故 **GUI 卸下时物品组件归零写进副本会丢失**(玩家侧清理仍生效、死亡路径已有覆盖)——该缺口**仍未修复**,并记录三种候选修法待裁决(本批不改行为,纯文档)。
- 工程:自动化测试工具链**提速与防卡死改造**,以及**断言窗口改为「自本用例起」**、末影珍珠传送判据收紧(`scripts/test`,双版本)。① **卡死根因修复**:`mt.ps1` 的 `Invoke-MtChild` 曾以 `Start-Process -NoNewWindow -PassThru -Wait` 启动子阶段,而 `-Wait` 会等待**整棵进程树**,`mt_build` 新起的 Gradle 守护进程是其**长期存活的后代** ⇒ 全流程永久卡在 `build` 相位;现改为需要长期驻留的阶段**脱离式启动 + 轮询终态标记**。② **三层超时**:单条用例 `MT_CASE_TIMEOUT_SEC`(默认 **300 秒**;超时记为**独立状态 `TIMEOUT`** 而非 `FAIL`,继续跑下一条,退出码 **12**)、全局 `mt.ps1 --run-timeout`、外部看门狗 `scripts/test/mt_watchdog.ps1`(`-StallSeconds` 默认 360 / `-PollSeconds` 默认 10 / `-Action report|stop`;以 `.mt_run_state.json`、活动报告目录、`run/<版本>/logs/latest.log` 等**进展信号**判定,定期打印 `MT_WATCHDOG: ALIVE` 心跳,停滞时打印 `MT_WATCHDOG: STALL` + 日志尾部诊断,退出码 **42**;**只经 `mt.ps1 --phase stop` 收停,绝不自行 `taskkill` 任意 java 进程**)。③ **提速**:删除 16 处冗余重复注入(163→125)、把 22 条原版命令折叠进探针 `runCmd`(同进程 `performPrefixedCommand`,省去 ≥2.65 秒/条的键盘注入)、6 个只读命令改走 `/astralparty dump`;`mt.ps1 --version` **已尊重**指定单版本(此前忽略该参数、恒受 1.21.1 门控);新增 OP 前置闸门(权限级 2 不满足记 **`BLOCKED(11)`**,不伪装成 `FAIL`)。断言**一条未删**:515→579,本批再增至 **587**。④ **断言窗口改为「自本用例起」**:此前快照只在 launch 收尾写一次,全部 `log`/`absent` 断言的窗口是「自 launch 起」⇒ 任何不带 tag 唯一标识的标记都能被前序用例满足(实测 `APDUMP|LOCKRAW|` 命中数随用例递增 14→28→…→126)。现由用例执行器在**每条用例开始**刷新偏移(`snapshot --window case`),并把自 launch 起偏移另存为 `launch_offsets` 供 Mixin 断言与整轮报告摘要使用(`mixin` 仍读 launch 窗口,避免收窄后变成恒真);用例 JSON 侧新增 `assert.scope`(`case` 缺省 / `launch` / `whole`,非法值在校验期即被拦下)。双版本全量 **35 条用例全 PASS、断言 587 PASS / 0 FAIL**(288 + 299),`win=case` 覆盖 250/252 行,**未暴露任何「被前序用例喂饱」的假 PASS**。⑤ **末影珍珠传送判据收紧**(用例 `NANCY-LU-PEARL-IMMUNE`,双版本):旧竞技场接珠柱立在 `dz=3`、珍珠从 `p.z + 1.0` 起飞 ⇒ 第 0 tick 命中时位移恰为 **1.000**、第 1 tick 为 **1.8**,而旧判据是 `位移 > 1.0` ⇒ **正好骑在临界值上**:同一函数、同一几何的免疫相位与对照相位会因「命中落在第几 tick」而一个读到 0、一个读到 1(**与装不装立牌无关**;1.20.1 实测通道内还残留 3 只前序用例的猪,同样造成「起飞即命中」的退化路径)。现改为:玩家吸到方块中心、通道加长到 6 格、接珠柱移到 `dz=5`、清除通道内非玩家实体,位移改取**观察窗内峰值**,阈值由 `>1.0` **收紧为 `>=2.0`**;两版本实测位移均为 **4.152**(留 2.15 格余量),免疫相位 `window_max=20 / drop=0 / hurt=0`,对照相位 `drop=5 / hurt=9 / invul=20` ⇒ **产品面无缺陷候选**。⑥ `AGENTS.md` 新增「测试流程超时与看门狗」小节(卡死根因、三层超时表、看门狗用法与安全红线、OP 前提、`--version` 语义)。⑦ **交付后遗漏审计**另发现 **11 项未修/未决项**(数值修饰器 7 项:`P2-C2`/`C5`/`C6`/`C7`/`C8`/`C10`/`C13`;未核验 1 项:`P1-U5`;更早扫描未决 3 项:`project-scan-report §八` 的 #1/#3/#5),经用户裁定**作为未来版本修补内容**,已登记进**版本化清单 `KNOWN-ISSUES.md`**(含来源、文档定性、待复核点、建议动作与验证入口;`docs/` 不入库,故未修项一律登记在该文件)——**本版本不实施**;同批还把「审计中已实测闭环、但当日扫描文档未回写」的项(P5 的 13 处 `✘1需改` 实为已全部改双参、`NancyLuSignItem` 的 `|| instanceof Player` 已替换等)记入该清单,供后来者避免重复劳动。
- 工程:CI 与 Release 纳入第三条线(26.1.2)(2026-09-17,用户要求「将 26.1.2 加入 Action 和 Release」)。① `.github/workflows/build.yml` 的触发/构建/上传产物三步**本就覆盖三线**(JDK 25 步骤供 26.1.2 的 toolchain、`tools/check_lang_sync.ps1 -LangDir neoforge-26.1.2/...`、`./gradlew build`、`upload-artifact` 的第三个 glob),缺口只在 Release 附件 ⇒ 现把发布步骤改名 `Create/Update GitHub Release (three JARs)`,附件由两个 jar 扩为三个(第三个 = `neoforge-26.1.2/build/libs/astral_dice-*.jar`),`--notes` 改为多行 `$NOTES`(逐条列出三个附件的平台与前置,并标注 26.1.2 为低优先级 `-beta` 线),`edit` / `create` 两条路径共用同一份说明与同一组附件(`--clobber` 覆盖)。② **tag 与 Release 的创建仍只在发布线分支**(`multi-1.20.1-1.21.1`):26.1.2 的 `mod_version=1.2.1-beta+neoforge_26.1.2` 经「先剥 +后缀 再剥 -rc/-pre」的解析得到 `1.2.1-beta`,不是裸 `x.y.z`,按发布规范的裸版本守门不会也不应生成 tag ⇒ 26.1.2 **不拥有自己的 tag/Release,只作为附件随发布线 Release 发布**;workflow 顶部触发注释与 `AGENTS.md` 的「发布规范」「模组内容更新规则」第 4 条、`README.md` 中英三处口径同步改写(原文「26.1.2 线不发版 / 不发布 Release」易被误读成「其 jar 不进 Release」)。③ **验证口径**(本机无 token/gh,不查 GitHub 侧状态):用 `bash -n` 校验从 workflow 抽出的全部 `run:` 脚本(3/3 通过);再用 bash 桩 `gh` 在本地按真实 jar 名重放发布步骤,「Release 不存在 → `gh release create 1.2.1 <三个 jar> --title … --notes <多行说明> --latest`」与「Release 已存在 → `gh release edit 1.2.1 --title … --notes …` + `gh release upload 1.2.1 <三个 jar> --clobber`」两条路径的参数与预期逐字一致(第三个附件确为 `astral_dice-1.2.1-beta+neoforge_26.1.2.jar`)。④ **26.1.2 在 CI 上的首次真跑风险已排查**:本机确认 `settings.gradle` 三条 `include` 齐全、已注册 `org.gradle.toolchains.foojay-resolver-convention`(toolchain 25 可自动下载),且 `neoforge-26.1.2/build.gradle` 的**构建链路不引用任何本机专属路径**(`D:/.minecraft/versions/26.1.2 模组测试/mods` 只出现在 `pushToGame` 类任务里,目录不存在时打印 skipped,不影响 `build`)⇒ 无「本地能过、CI 必挂」的结构性依赖。⚠️ 26.1.2 子项目此前从未在 GitHub 上构建过(原 `multi-26.1.2-neoforge` 分支无远端),本轮推送是它第一次进 CI:首次运行耗时会长于发布线(含 `createMinecraftArtifacts` 的解压/打补丁/重编译),且**若 Actions 变红必须看远端日志**——本机无法观测 CI 状态。
- 工程:补记「根 `build/libs` 三版本共存」的**实际触发条件**(2026-09-17,用户反馈「26.1.2 版本未放到公共 build 目录中」)。**现象**:26.1.2 并入主线后根 `build/libs/` 只有两条发布线的 jar(时间戳 2026-09-16 21:10),26.1.2 的 jar 缺失。**根因(非配置缺陷)**:三个子项目的 `pushToRootBuild` 一律是 `tasks.named('build')` 的 `finalizedBy`,而 **`:neoforge-26.1.2:jar` 不触发任何 push 任务** ⇒ 合并后只跑过 `:neoforge-26.1.2:jar`(只产出该子项目自己的 `build/libs`),共享目录因此一直没有被刷新。**处置与判据**:先 `mt_build.ps1 --version 26.1.2`(1s,`MT_BUILD: OK`),再跑**全量** `./gradlew build`(2s,`BUILD SUCCESSFUL`,25 actionable tasks:10 executed / 15 up-to-date),日志逐条确认三线 push 任务均已执行(`:neoforge-26.1.2:pushToRootBuild|pushToDevRun|pushToGame`、`:neoforge-1.21.1:pushToDevRun|pushToRootBuild|pushToGame`、`:forge-1.20.1:reobfJar` → `pushToGame|pushToRootBuild|pushToDevRun`);根 `build/libs/` 现有 **3 个 jar**(`1.2.1+neoforge_1.21.1` 934894 B、`1.2.1+forge_1.20.1` 980279 B、`1.2.1-beta+neoforge_26.1.2` 967109 B,时间戳均为本次构建),三者与各自子项目 `build/libs` 产物 **SHA1 逐字节一致**;三个部署目标各恰有 1 个本模组 jar(`run/<版本>/mods` 与 `D:\.minecraft\versions\{狐の航空学 Voxy Edition, 1.20.1 模组测试, 26.1.2 模组测试}\mods`)。⚠️ **口径**(与 `AGENTS.md`「编译产物集中规则」一致,本轮**不改任务依赖**):「产物集中到根 `build/libs`」只在跑 **`build`** 时兑现,只跑 `jar`/`compileJava` 时共享目录**不会**更新 ⇒ 此时「共享目录里缺某线 jar」属预期行为,不要据此改 Gradle。
- 工程:三线构建规则对齐(2026-09-17,用户要求「将 26.1.2 也纳入主线,遵循与其他版本相同的构建规则」)。**审计结论:26.1.2 的构建/产物规则与另两线本已同规则** —— 三个子项目的 `plugins` / `repositories`(含 `exclusiveContent`)/ `java.toolchain` / `jar` / 三件套推送任务(`pushToDevRun` / `pushToRootBuild` / `pushToGame` **均 `finalizedBy build`**、先删后拷、按后缀过滤)一一对应,`settings.gradle` 三条 `include`,`./gradlew build` 实跑会同时构建三线并把三个 jar 写进根 `build/libs`(证据见上一条记录:2s、`BUILD SUCCESSFUL`、三线 push 任务全部执行、三 jar 与各子项目产物 SHA1 一致)。**差异只余平台必需项与按目标目录收紧的过滤**:forge 走 `reobfJar`(dev 取 `build/devlibs`、生产取 `build/libs`);26.1.2 无 parchment、数据生成两段式(`runClientData` + `runServerData`)、Mixin 注解类取自 Fabric maven、`gradle.properties` 多 `neo_version_range`/`curios_version_range` 而少 `parchment_*`;清理过滤上,26.1.2 的 `pushToGame` 与两条 neoforge 线的 `pushToRootBuild` 带**各自完整后缀**(防三线在共享目录互删),1.20.1 三个任务统一用 `+forge_1.20.1`。**本轮处置 = 把残留的「两版本」表述与缺口补齐,让规则口径上 26.1.2 成为第一等线**:① 根 `build.gradle` 注释由「同时构建两个版本」改为三线并列,并补 `:neoforge-26.1.2:build` 示例;② `AGENTS.md`「Gradle 构建守护规则」第 6 条构建入口补 26.1.2、第 7 条 `runData` 遮蔽提醒补 `run/26.1.2/mods` 与 26.1.2 两段式数据生成;③「编译产物上传规则」第 7 条的 `run/mods` 清单补 `run/26.1.2/mods`;④「新版本发布流程」第 4 条「同时编译并部署两个版本」改为三个版本;⑤「版本互通门槛」由「复跑双版本构建与冒烟」改为「复跑三线构建与冒烟」(该门槛在三个子项目各有一份实现);⑥「自动化测试流程」适用范围由两个子项目改为三个子项目;⑦ lang 同步条目里「CI 对两个子项目执行该检查」改为三线各一步(`build.yml` 实际已是三步)。**本轮零行为改动**:未改任何 Gradle 任务依赖、清理过滤或产物来源(推送仍只在 `build` 之后触发),改的只是构建/发布口径文档与根项目注释;验证 = `./gradlew build` 重跑 `BUILD SUCCESSFUL`、根 `build/libs` 三个 jar 俱全、`Test-MtSyntax.ps1` 36 文件 0 失败、`mt_preflight.ps1 --all` 12 项 OK。⚠️ **未改动**「子项目修改默认规则」与「模组内容更新规则(三线优先级)」——那两条约束的是**内容**优先级(26.1.2 仍为低优先级、发布线完成后迁移),与构建规则无关;是否把内容也升级为三线对等属策略变更,待用户单独裁决。**同日用户裁决:本轮范围 = 仅构建/产物/CI 规则对齐(内容优先级仍按「低优先级、发布线完成后迁移」,规则文本不改);26.1.2 的 `mod_version` 继续保留 `-beta`(其 jar 作为第三个附件随发布线 Release 发布,不生成自己的 tag)。**
- 工程:发布流水线的说明来源改为**玩家侧发布说明文件**、清理本地兜底 jar、并固化「CI 状态由用户自行观察」(2026-09-17,用户三项要求)。① **Release 正文改取文件**:`.github/workflows/build.yml` 的发布步骤改名 `Create/Update GitHub Release (three JARs, notes from release/<tag>/)`,正文 = `release/<tag>/PLAYER_CHANGELOG_ZH.md` + `release/<tag>/PLAYER_CHANGELOG.md`(中文在前、中间插 `---`、英文在后),拼到 `$RUNNER_TEMP/astral-dice-release-notes-<tag>.md` 后用 `gh release create|edit --notes-file` **整文件**传入 —— 旧的 35 行内联 `--notes "$NOTES"` 写法废弃(玩家版正文实测 **77238 字节**,内联会踩长度与引号问题);两份文件都缺失时只打三条 `::warning::` 并退回「附件清单」兜底(列出本次构建的三个 jar),**不阻断发布**;`release/1.2.1/` 两份文件按三附件现状补齐(中英各加「GitHub Release 另附 26.1.2 第三附件 + 该线需 Curios 15+、无 Iron's Spells 联动」)。**验证**:`bash -n` 3/3 通过;桩 `gh` 重放四个场景(**create / update / tag push / 无发布说明兜底**)共 **12/12 断言 PASS** —— create 路径 `gh release create 1.2.1 <三个 jar> --title … --notes-file … --latest`、update 路径 `gh release edit … --notes-file …` + `gh release upload … --clobber`、tag push 走 `GITHUB_REF` 的 tag、兜底正文含 `附件 / Attachments` 与三个 jar 名;三条正常路径捕获到的正文逐字节相同(中文 H1 开头、含英文 H1、含分隔线、77238 字节)。② **删除本地兜底 jar**:按用户裁决删除 `neoforge-1.21.1/base-mod-compile-libs/irons_spellbooks.jar`(13874139 B,自 2026-09-17 改走 `maven.modrinth:irons-spells-n-spellbooks:RtvqnbKi` 后已无引用;该文件本就未入库)。删除后该目录为空,`tools/check_mod_sources.ps1` 由 `infos=2` 降为 **`infos=1`**(仅剩 `base-mod-libs` 的 `fileTree` 指向空/不存在目录的提示),`violations=0 exceptions=0` 不变。③ **CI 状态观察口径(用户裁决)**:本机无 token、不装 `gh`,**代理不监视/不轮询/不查询 Actions 与 Release 状态**,推送后只在交付说明里列预期结果与排查入口,由用户到 Actions 页面核对;已写入 `AGENTS.md` 的「发布规范」段落(含禁止安装 CLI、禁止改用 API 轮询、禁止把「本机看不到 CI」反复登记为未完成事项)。

**2026-09-17：整合包推送分支口径（用户裁决）＋ `AGENTS.md` 失真表述修正**

- 工程:**用户裁决(2026-09-17)** —— 原话「**该分支内所有内容均不推送整合包(严格),只推送到游戏测试目录。除非用户另行规定。**」。三项口径:① **严格不推整合包**：`multi-dev-next` 上 `pushToGame` 在**执行期**判定当前分支不在白名单即 `return`，只打印 `pushToGame: skipped — 分支 '…' 不在整合包推送白名单 …`，整合包目录不被写入、旧 jar 也不被删；② **只推游戏测试目录**：仓库根 `run/<版本>/mods`（`pushToDevRun`）与仓库根 `build/libs`（`pushToRootBuild`）**保留**、随 `build` 照常触发、不受分支限制；③ **需要时由用户另行规定**：仅用户显式要求时才出包（`-PdeployToPack` / `-PpackPush` 手动强推，或把分支加入 `packPushBranches` 白名单）。**本轮零构建代码改动**：三条线现状已满足该裁决，`build.gradle` / 守卫条件 / `-P` 语义 / 整合包与 `run/*/mods` 内容一律未动。
- 工程:**代码 ↔ 文档矛盾（发现与修正）**。`AGENTS.md`「编译产物上传规则」原第 1 条称 `-PdeployToPack` **不再被任何任务读取**（源码中仅存注释），与代码不符 —— 三条线 `build.gradle` 均有 `def forcePackPush = project.hasProperty('deployToPack') || project.hasProperty('packPush')`（`neoforge-1.21.1/build.gradle:295`、`forge-1.20.1/build.gradle:333`、`neoforge-26.1.2/build.gradle:277`），并在 `pushToGame` 的 `doLast` 里以 `if (!forcePackPush) { … }` 包裹分支白名单判定（`:335` / `:381` / `:314`）⇒ 该参数**确实被读取**，作用正是**跳过白名单手动强推**。现**以代码为准**改写该条（参数保留、作用=跳过白名单强推、强推时同时触发「库 jar 成对自检」告警），并在同段表格的「pushToGame 触发条件」列与「发送到整合包」引用块补分支白名单口径（仅发布线 `multi-1.20.1-1.21.1` 自动推送，其它分支含 `multi-dev-next` 严格跳过）；「编译产物上传规则」段内新增裁决原文 + 三项口径的引用块，另同步「子项目修改默认规则」的自动收尾条与「新版本发布流程」第 4 条。
- 工程:**证据来源**（本轮**不跑 Gradle**，避免与验证任务争用守护进程，直接引用已落盘日志）。t12 提交 **`19d7471`** 的三线构建日志（报告 `temp/t12-26.1.2-migration-report.md` §1/§5；原始日志 `temp/merge-t2/t12-verify-build-2612.log`、`t12-verify-build-others.log`、`t12-build-stage4.log`）：三条线的 `pushToGame` **各打印一行 `pushToGame: skipped — 分支 'multi-dev-next' 不在整合包推送白名单 [multi-1.20.1-1.21.1] 内;如需强制推送请加 -PdeployToPack`**；同期真正写入的是另两个任务 —— `pushToDevRun: pushed astral_dice-….jar -> F:\MCProject\astral_dice_multiloader-next\run\<版本>\mods`（即 `pushed … -> run/<版本>/mods`）与 `pushToRootBuild: pushed … -> …\build\libs`；三个整合包 `mods` 目录内 jar 的**时间戳未被刷新**（26.1.2 仍为 2026-09-17 19:36:39.179），与「严格不推整合包」一致。
- 工程:`KNOWN-ISSUES.md` 的 **KI-M5①**（整合包 `mods` 内无 `starengine_lib-*.jar`）按本裁决补**限域说明**：该风险**在 `multi-dev-next` 上不会触发**（此分支严格不推整合包），仅当**发布线**推送整合包时才需「库 jar 与本模组 jar 成对推送」。

**2026-09-17：选择器立牌主动技能「前置门控」（t15）＋ 玩家侧文案同步**

- 工程:**改动范围 = `neoforge-1.21.1` + `forge-1.20.1` 两个发布线子项目**(26.1.2 无目标选择器、未改动;未动共享库 `starengine_lib`、未 bump 库版本、未改 CI 引用、未改整合包推送守卫)。目的:把三个「目标选择器类」立牌(占星师 `haiqing_weak_mark` / 秘密侦探 `bonnie_undercover` / 枪匠 `moses_apply_broken`)的主动技能改为**前置门控** —— 按下主动键只开启选择会话,发牌 / 主动响应提示 / 冷却·锁定 / 电流核心充能 / 技能效果全部推迟到确认合法目标之后;取消 / 超时 / 会话被替换 / 登出 / 死亡一律等同「未使用」。
- 工程:**实现点位(两线同构)** —— ① `item/sign/BaseSignItem`:新增 `protected String selectorActionId()`(缺省 `null` = 非选择器类,原流程逐字不变)与「第 2.5 步」门控分支(位于第 2 步 `isSelecting` 守卫**之后**、第 3 步 `handleUse` **之前**;`TargetSelectionManager.start` 成功才 `SignSelectionGate.arm`),并新增确认恢复点 `public static void resumeGatedActiveSkill(Player, String)`(取走待执行记录 → 风扇筹码发牌 → 抛 `SignActiveTriggeredEvent` → 无处理器时发默认提示);② 新增 `target/SignSelectionGate`(record `Pending(String actionId, ItemStack stack)` + `ConcurrentHashMap`;`arm` / `isArmed` / `take`(原子取走,`actionId` 不匹配即丢弃,避免跨会话误触发) / `clear`),两条线该文件**逐字节相同**(sha256 `1fb67db7b9130e33…`);③ `target/TargetSelectionManager` 接线:**8 处 `SignSelectionGate.clear`**(测试清理 / 会话被替换 / 确认时已过期 / 确认时动作缺失 / 取消 / tick 超时 / 登出 / 死亡)+ 确认成功后 **1 处** `resumeGatedActiveSkill`(在 `action.apply` **之后**、`target_select.applied` 提示之前 —— 成功路径**不得**提前 clear,否则恢复点退化为空操作);④ 三个立牌各覆写 `selectorActionId()` 返回自己的 action id(`TargetSelectionAction#apply` 已负责写入玩家级冷却与电流核心充能,恢复点不重复写)。**窗口时长**一律读 `GameplayConstants.SKILL_WAIT_SECONDS`(会话 `expireTick = gameTime + SKILL_WAIT_SECONDS * 20L`);本轮新增的 **Java 源码与注释**里**不出现**写死的窗口数字(判据 `git diff -- '*.java' | Select-String '^\+.*\b30\b'` **0 命中**;玩家侧 CHANGELOG 与本附录中出现的 `30 秒` 属文档叙述与旧文案引用,不受该判据约束)。`handleUse` 仍是唯一入口的旁证:`use()` 对立牌恒返回 fail、三个立牌 `handleUse` 体内只有 `start(serverPlayer, "<actionId>")` 一句 ⇒ 门控分支直接调 `start` 与旧路径等价,无第三处入口。
- 工程:**枪匠「请选择目标」提示改挂会话开始**(行为一致性的必要修正):该提示原先由 `MosesSignItem#onSignActiveTriggered`(订阅 `SignActiveTriggeredEvent`)发送,而该事件在门控后**只在确认成功后**抛出 ⇒ 会在「破绽」已施加之后再要求玩家「请选择敌对目标」,同时按键那一刻反而没有任何提示。现改为:注册动作新增 `onStarted(ServerPlayer)` 覆写 → `sendReadyPrompt(player)`(恢复「进入选择模式瞬间提示」的原意),并删除该事件处理器(连带删除不再使用的 `SubscribeEvent` import;类上的 `@EventBusSubscriber` 注解保留)。判据:产物 jar 内 `MosesSignItem$1.class` 含 `onStarted`、`MosesSignItem.class` **不含** `onSignActiveTriggered`(两线实测一致)。
- 工程:**玩家侧文案同步(手持风扇手册正文)**:两条手册条目(`astral_dice.guide.entry.hand_fan_{big,small}_chip.1`,中英 × 双线 = **8 处**)原写「需指定目标的立牌在按下进入 30 秒待命时即触发」/“(for target-waiting signs this triggers the moment you press the key to enter the 30-second standby)”,与门控后的实际行为相反 ⇒ 改为「需指定目标的立牌改为确认目标后才触发」/“(for target-waiting signs this only triggers after the target is confirmed)”。**lang 键集合零变化** ⇒ `tools/check_lang_sync.ps1` 结论不变(两条发布线各 619/619;26.1.2 622/622 未触碰);立牌自身的 tooltip/手册(「激活后进入目标选择模式……确认后向其施加……取消或超时不消耗冷却」)门控后**仍然逐字成立**,未改。
- 工程:**验证(本轮实测)** —— `gradlew :forge-1.20.1:build :neoforge-1.21.1:build` **BUILD SUCCESSFUL**(日志 `temp/merge-t2/t15-build-both.log` / `t15-build-both2.log`;产物 `astral_dice-2.0.0-SNAPSHOT.5+neoforge_1.21.1.jar` 945703 B、`+forge_1.20.1.jar` 975785 B 均含新类 `target/SignSelectionGate.class`);`tools/check_mod_sources.ps1` → **`MOD_SOURCE_GATE: OK violations=0 exceptions=0 infos=0`**;`tools/check_lang_sync.ps1` → 三线全 OK;forge 产物内 `astral_dice.refmap.json` 仍在且 `astral_dice.mixins.json` 的 `refmap` 声明未变(12 条目);所有改动文件行尾仍为纯 CRLF(`i/lf w/crlf`)。
- 工程:**本轮按「二轮验证冻结」口径不改 `AGENTS.md`**(其「目标选择器规范」的「立牌选择器类主动的冷却约定」仍以「`BaseSignItem.performSkill` 经 `TargetSelectionManager.isSelecting` 判断 ⇒ 进入选择模式时不开始冷却」描述,门控后改由第 2.5 步 `return` 达成同一净效果,机制表述待用户定夺后于第二轮补记;`AGENTS.md` 的白名单/守卫/门槛口径本轮一字未动)。交付报告:`temp/t16-selector-gate-report.md`。

**2026-09-17：移除 `scripts/test` 的「测试分支白名单」强断言（用户裁决；为选择器门控的游戏内验证铺路）**

- 工程:**用户裁决(2026-09-17)** —— 原话「**移除二重验证白名单，允许该分支执行**」。目的:让 `multi-dev-next`（以及其它工作分支）能运行 `scripts/test` 自动化流程，从而可对选择器前置门控（t15）做**游戏内行为验证（B1/R2）**；此前该分支会在阶段 P 被分支强断言直接拒绝（`MT_PREFLIGHT: FAIL`、退出码 10），根本进不到游戏。
- 工程:**分支检查由强断言改为「信息性回显 + WARN」，且只动分支这一项**。`scripts/test/mt_preflight.ps1`：原 `:39-41` 的注释与分支白名单常量（值为发布线 `multi-1.20.1-1.21.1` + `multi-26.1.2-neoforge`；原文与标识符可用 `git show 1bd5f7f:scripts/test/mt_preflight.ps1` 取回）、原 `:46-64` 的分支检查函数（其 `:49` 的说明文字原为「当前分支必须在白名单内」，现已随函数整体删除）整段改写为 —— `$script:ReleaseLineBranches = @('multi-1.20.1-1.21.1')`（现 `:42`，**仅用于回显与告警标注、不参与任何判定**；原有的 `multi-26.1.2-neoforge` 已随该分支并入主线而不再列出）与 `Get-MtPreflightBranch`（现 `:46-75`：读分支 → 读不到/为空/非发布线一律返回 `$true`；发布线回显 `multi-1.20.1-1.21.1（发布线分支）`，非发布线回显 `multi-dev-next —— WARN: 非发布线分支（发布线 multi-1.20.1-1.21.1）；按 2026-09-17 用户裁决放行，不拦停，仅告警`）。消费点同步改名（`:324`，函数名由 `Test-*` 改为 `Get-*` 以免继续暗示「判定」）。**可失败断言数 12 → 10**，减少的两条正是该函数原有的两个 `return , @($false, …)`（白名单不符 / git 读取失败）—— 其余检查项（输入法 / 遗留进程 / MCP 二进制 / 模组来源闸门 `tools/check_mod_sources.ps1` / Gradle 包装器 / 兼容栈×版本 / run 可写×版本）与其断言**一字未改**，未删除、未放宽。
- 工程:**报告「分支」不再写死发布线**。`scripts/test/mt_report.ps1`：删除原 `:39` 的 `$script:BranchName = 'multi-1.20.1-1.21.1'`，改为新增 `Get-MtReportBranchName`（现 `:45-67`：`git -C <root> rev-parse --abbrev-ref HEAD`，任何失败/空值回落 `$script:BranchNameFallback = 'unknown'`；为此新增 `Import-Module … Mt.Proc.psm1`，现 `:33`，复用仓库唯一的进程调用实现而不另写一份 git 封装），两处调用点 `:368`（单版本报告元数据）与 `:437`（总览元数据）改为 `$(Get-MtReportBranchName)` ⇒ `$script:BranchName` 在全仓 **0 引用**，报告不会再出现「在 dev-next 上跑却记成 `multi-1.20.1-1.21.1`」。
- 工程:**文档四处按新口径改写并收录裁决原话**：`scripts/test/TESTING-SPEC.md:6`（「适用分支」改为**任意分支** + 裁决原话 + 信息性说明）、`:26`（§2 表格「分支」行由「强断言，其它分支直接拒绝」改为「任意分支；只回显 + WARN，不拦停」）、`AGENTS.md:897`（原「唯一测试分支…强断言…将被拒绝」的口径改为「任意分支均可 + 信息性回显 + WARN」并补「其余前置断言逐条保留」）、`AGENTS.md:1118`（失败处理表原有的「分支失败 → `git switch multi-1.20.1-1.21.1`」行改为「分支（仅告警、不拦停）| 无需处理」）。**两份玩家侧 CHANGELOG 未动**（工程/工具条目按约定只进本附录）；`AGENTS.md` 仅动上述两处，选择器门控的机制表述仍按「二轮验证冻结」口径等待用户定夺。
- 工程:**实跑证据（本次，在 worktree `multi-dev-next`）** —— ① **改前对照**（取出 `git show HEAD:scripts/test/mt_preflight.ps1` 同目录执行同一入口）：`[FAIL] 分支: 当前分支 multi-dev-next ≠ multi-1.20.1-1.21.1 / multi-26.1.2-neoforge` ⇒ `MT_PREFLIGHT: FAIL — 1 项不满足：分支（…）`，**退出码 10**；② **改后实跑**（`pwsh -NoProfile -File scripts/test/mt_preflight.ps1`）：`[OK  ] 分支: multi-dev-next —— WARN: 非发布线分支（发布线 multi-1.20.1-1.21.1）；按 2026-09-17 用户裁决放行，不拦停，仅告警`，其余 11 项全 OK（含 `模组来源: MOD_SOURCE_GATE: OK violations=0 exceptions=0 infos=0`），`MT_PREFLIGHT: OK — 12 项全部满足（1.21.1, 1.20.1, 26.1.2）`，**退出码 0**（原始输出留档 `temp/t17/preflight-multi-dev-next.log`；**与分支无关的 FAIL 数 = 0**，无需用户裁决的遗留失败）；③ **报告侧端到端**：以沙箱 `TestDir` 调 `Invoke-MtReportSummary` ⇒ `SUMMARY.md` 元数据行实测渲染为 `- **分支**: multi-dev-next`；④ `scripts/devtools/Test-MtSyntax.ps1` → **36 文件、解析失败 0**；⑤ 全 `scripts/` 再扫 `rev-parse|symbolic-ref|分支白名单常量|BranchName|测试分支` **无第二处隐藏强断言**（`scripts/maintenance/repair-loose-refs.ps1` 的 `git rev-parse` 是松散引用修复，与分支门槛无关；另有一处**性质不同**的同名白名单 = `packPushBranches` 整合包推送守卫，位于三条线 `build.gradle`，属 t14 的推送口径，**本轮一律未动**）。交付报告：`temp/t17-branch-whitelist-removal.md`。

**2026-09-17：选择器立牌「前置门控」收口（O1 提示去重 / O2 死代码清理 / O3 两线归一 / 新增门控用例）＋ 定夺口径入 `AGENTS.md`**

- 工程:**用户裁决(2026-09-17，对 t16-R1 三条 low 观察的定夺)** —— 选定 ①修 O1 ②修 O2 ③更新 `AGENTS.md` 冷却约定表述 ④补一条 `scripts/test` 门控用例 ⑤归一 `MosesSignItem` 两线插入顺序；**未选定**「清理 `*_ready` 残留 lang 键」⇒ 本轮**明确不做**（两侧 `msg.astral_dice.{haiqing,bonnie,moses}_ready` 保持原样；26.1.2 线同名键与 `*_ready` 效果一律不动）。
- 工程:**O1 提示去重**。`BaseSignItem.resumeGatedActiveSkill`（两线）删除「无处理器 ⇒ 补发默认提示」分支（`if (!triggered.isHandled()) notifyActionBar(…, "msg.astral_dice.sign_active_triggered", …)`），事件本身**照旧 post**（订阅方语义不变）。同 tick 行为与理由：确认成功路径 = `action.apply`（各动作自带 ActionBar，如 `moses_apply` / `haiqing_weak_mark_applied`）→ `resumeGatedActiveSkill`（旧：默认提示）→ `TargetSelectionManager#confirm` 末尾 `msg.astral_dice.target_select.applied`；ActionBar 后发者覆盖先发者 ⇒ 那条默认提示**玩家本就看不到**，属纯冗余。非门控立牌的原流程（`performSkill` 第 5 步）仍保留默认提示 ⇒ **文本可见结果不变**，故玩家侧 CHANGELOG 无需新增条目。
- 工程:**O2 死代码清理（先证明不可达，再删）**。门控在 `BaseSignItem.performSkill` **第 2.5 步**（`:113` 取 `selectorActionId()`；`:114-120` 非 null ⇒ `TargetSelectionManager.start` + `SignSelectionGate.arm` + `return`）；`handleUse` 的**唯一调用点**是 `:122`，位于该 `return` **之后**；`use()`（`:368-376`）对非潜行恒 `return InteractionResultHolder.fail(stack)`、潜行只走 `CurioSlotUtil.tryAutoEquip`，两条路径都不经 `handleUse` ⇒ 三个门控立牌（`HaiqingSignItem` / `BonnieSignItem` / `MosesSignItem`）的 `handleUse` 覆写**不可达**，且其体内是**第二处** `TargetSelectionManager.start` 入口。处置：`BaseSignItem#handleUse` 由 `protected abstract` 改为**默认实现**（返回 `fail` + 一条 WARN，弥补去掉 `abstract` 后失去的编译期约束，javadoc 写明「门控立牌不会到达此处」），两线删除 6 个覆写（3 立牌 × 2 线）并清掉随之失去引用的 `InteractionResultHolder` / `Level` import。**其余 14 个立牌仍各自覆写** `handleUse`（改后 `git grep -n handleUse` 逐条核对 + `javap -p` 字节码核对：三个门控立牌已无该方法、`BaseSignItem#handleUse` 不再是抽象方法）。门控仍在本步，行为逐条不变。
- 工程:**O3 两线归一**。`MosesSignItem` 的 `selectorActionId()` 插入锚点两线不同（neo 在 `clearSignData` 之后、forge 在构造函数之后）⇒ 把 forge 侧移到与 neo 同一锚点（紧跟 `clearSignData`）。**同批发现 `BonnieSignItem` 存在同类锚点差异**（t15 插入时 neo 在 `clearSignData` 之后、forge 在构造函数之后）——按同一口径**一并归一**（内容零变化、仅位置），使「三个门控立牌的 `selectorActionId()` 在两条线同锚点」成立；归一后两线逐行 diff 仅剩平台差异（`CuriosCompat` 取法 / `@Mod.EventBusSubscriber` / `ModEffects.X.get()` 等）。
- 工程:**文档口径（本轮解冻）**。`AGENTS.md`「目标选择器规范」的「立牌选择器类主动的冷却约定」整条改写为**前置门控机制**表述（第 2.5 步只开会话即 `return`；冷却/充能由确认时 `TargetSelectionAction#apply` 写入；取消/超时/会话被替换/登出/死亡 ⇒ 等同「未使用」，不发牌/不进冷却/不施效/不充能；事件照旧 post 但门控路径不补发默认提示；边界=仅这三个立牌；新增门控立牌只覆写 `selectorActionId()`、不要再写 `handleUse`），并同步 `BaseSignItem` 内文一处旧口径注释（「冷却门槛见第 6 步，依据是否已进入选择会话判定」→ 分两类表述）。
- 工程:**新增门控用例（两发布线各一条）**。`scripts/test/cases/SELECTOR-GATE-{1.21.1,1.20.1}.json` + 探针新命令 `/astralprobe sggate <tag>`（`scripts/test/resources/kubejs/{1.21.1,1.20.1}/server_scripts/astral_bugfix_probe.js`，两线同构、仅 `ModEffects.MOSES_BROKEN` 取法按平台不同）：按主动 = `BaseSignItem.performSkillForCurio`、取消/确认 = `TargetSelectionManager.{cancel,confirm}`（与客户端按键 / 网络载荷处理器**同一入口**，不依赖客户端按键与选择 UI，不发明新框架）。三类断言读数 —— `AP_<tag>_GATED:session=1:cd=0:cards_delta=0:armed=1`（门控态：开会话 + 登记记录，不发牌/不冷却/不施效）、`AP_<tag>_CANCEL:token_seen=1:session=0:cd=0:cards_delta=0:armed=0`（取消 ⇒ 等同未使用）、`AP_<tag>_CONFIRM:mob=1:session=0:broken=1:cd=1:cards_delta=1:armed=0` + `AP_<tag>_VERDICT:1`（确认 ⇒ 破绽 + 冷却 + 发牌）；发牌口径 = 背包内 `EffectCardUtil.getRandomEffectCardPool()` 卡牌数增量（生产同一入口）。为支持服务端驱动确认，`TargetSelectionManager` 两线新增**只读**测试辅助 `sessionTokenForTests(Player)`（返回当前会话 token，无会话 -1；不改任何生产路径）。既有用例语义**零改动**；探针新增行经 `node --check` 语法校验、两条用例经 `pwsh -File scripts/test/mt_case.ps1 validate --case …` → `MT_VALIDATE: OK`。
- 工程:**验证（本轮实测）** —— 两线 `./gradlew :neoforge-1.21.1:build` / `:forge-1.20.1:build` **BUILD SUCCESSFUL**（`NEO_EXIT=0` / `FORGE_EXIT=0`；日志 `temp/t18/build*-*.log`；`pushToGame: skipped — 分支 'multi-dev-next' 不在整合包推送白名单 …` 照旧、`pushToDevRun`/`pushToRootBuild` 已刷新产物：`astral_dice-2.0.0-SNAPSHOT.5+neoforge_1.21.1.jar` 944932 B @20:35:59、`+forge_1.20.1.jar` 974968 B @20:36:03）；`tools/check_mod_sources.ps1` → `MOD_SOURCE_GATE: OK violations=0`；`tools/check_lang_sync.ps1` 三线全 OK（本轮**未动**任何 lang 文件）；`scripts/test/mt_preflight.ps1` 12 项全满足。游戏内行为回归交由 R2 执行（**探针改动必须冷启动**：`stop` → `launch`）。交付报告：`temp/t18-selector-gate-followup-report.md`。

**2026-09-17：测试环境三条线统一（史莱姆压制 / 优化类 / 探针宿主 / 光影包）＋ 全局测试规则落地（禁止失焦 ESC 菜单 / 进程捕获时长受控 / 冗余 Esc 精简 / 窗口搬移移除）**

- 工程:**1.21.1 补全「超平坦无史莱姆」硬前置，并把优化类模组铺到三条线**（用户两轮要求：「1.21.1 环境缺少没有史莱姆的超平坦世界模组，这是必需的模组，没有会使史莱姆干扰测试，补全该模组然后重新运行 1.21.1 测试」＋「所有测试环境增加 ImmediatelyFast、FerriteCore 模组，用于优化模组兼容性测试」）。① `mt_env.ps1` 新增 `$script:PerfModsByVersion` / `$script:PerfPrefixesByVersion` / `$script:SlimeGuardByVersion` 三张**按版本**表，`Install-MtPerfMods` / `Install-MtSlimeGuard` 去掉 `if ($Paths.version -ne '26.1.2') { return 0 }` 早退，`Invoke-MtEnvMods` 在 1.20.1 / 1.21.1 分支也调用它们。② 坐标一律 **Modrinth Maven** 且版本 id 钉死（sha1 + size 幂等校验）：1.21.1 史莱姆压制 `superflat-world-no-slimes:5VtNIDJA`(1.21.1-3.5) + 前置 `collective:4XRlrKGN`(1.21.1-8.39)；优化类 1.21.1 `immediatelyfast:OUpXxw4n`(1.6.14+1.21.1-neoforge) + `ferrite-core:x7kQWVju`(7.0.3-neoforge)、1.20.1 `immediatelyfast:rvsLEEZU`(1.5.5+1.20.4-forge，声明支持 1.20.1) + `ferrite-core:DG5Fn9Sz`(6.0.1-forge)、26.1.2 补 `ferrite-core:LtVvw4uS`(9.0.0-neoforge)。③ **1.20.1 的史莱姆压制故意不入表**（`forge-1.20.1/build.gradle` 的 `modImplementation` 已提供 collective + superflat-world-no-slimes，重复放入 `run/1.20.1/mods` 会被 FML 判重复模组），函数在该版本回显来源说明并跳过。④ `mt_launch.ps1` 的史莱姆硬闸门由 26.1.2 分支移到**版本无关公共段**（错误信息改为按 `$Version` 生成），并新增 1.20.1 / 1.21.1 的 `IMMEDIATELYFAST_LOADED` / `FERRITECORE_LOADED`（1.21.1 另含 `MODERNFIX_LOADED`）读数。⑤ **实测**：`mt_env.ps1 mods` 三线落位成功（1.21.1 新装 4 个、1.20.1 新装 2 个、26.1.2 补 1 个）；1.21.1 `--phase launch` 全绿：`SLIMEGUARD_LOADED=true` / `COLLECTIVE_LOADED=true` / `IMMEDIATELYFAST_LOADED=true` / `FERRITECORE_LOADED=true` / `MODERNFIX_LOADED=true` / `MT_ASSERT_KUBEJS: PASS — server.log 0 errors` / `PREFLIGHT_OP: OK` / `MT_LAUNCH: OK (45s)`。
- 工程:**1.21.1 探针宿主（KubeJS + Rhino + Architectury API）补全**（同一轮发现的第二个环境缺口）。`neoforge-1.21.1/build.gradle` 有意不依赖 KubeJS（会让 datagen 也装载它），约定「dev run 直接从 `run/mods` 装载整合包同版 kubejs」——此前**只靠手工复制**，dev-next 工作树的 `run/1.21.1/mods` 里没有 ⇒ `MT_ASSERT_KUBEJS: BLOCKED`、`/astralprobe` 命令不存在、`MT_preflight-op` ERROR。新增 `Install-MtProbeHost`（`$script:ProbeHostMods1211`：按整合包复制 `kubejs-neoforge-*` / `*rhino-*` / `architectury-*-neoforge.jar`，落位时剥掉整合包文件名里的中文方括号前缀，尺寸 + 时间戳幂等，缺任一即 BLOCKED 返回 11）。**实测**：`MT_MODS: OK — 1.21.1 探针宿主 新装 3 个（kubejs-neoforge-2101.7.2-build.374.jar / rhino-2101.2.8-build.91.jar / architectury-13.0.11-neoforge.jar）`，随后 launch 的 `MT_ASSERT_KUBEJS: PASS`、`PREFLIGHT_OP: OK — has2=1 level=4 src=cmdsource+profile dump=rc=undefined`、`ASTRALPARTY_DUMP: rc=rc=undefined lockraw_rows=28`。
- 工程:**光影包就位并默认启用**（用户要求「游戏环境缺少光影包，添加光影包并设置默认启用」）。`Install-MtRenderStack` 改为**版本感知**（新增 `$script:RenderModsByVersion`：26.1.2 含 Sodium/Iris 规格、1.21.1 为空数组因渲染模组由整合包复制、1.20.1 回显 SKIP 因 Embeddium/Oculus 在 mojmap 下无法解析），并把 `ComplementaryUnbound_r5.9.3.zip` 落位 `run/<V>/shaderpacks/`、把 `config/iris.properties` 写成 `shaderPack=ComplementaryUnbound_r5.9.3.zip` + **`enableShaders=true`**。**实测 1.21.1**：`run/1.21.1/shaderpacks/ComplementaryUnbound_r5.9.3.zip`(553400B) + `iris.properties` 两键正确 + 启动日志 `[Iris/]: Using shaderpack: ComplementaryUnbound_r5.9.3.zip`（无崩溃报告）。⚠️ 同轮实测到 Iris 1.8.14-beta.1 对 Complementary r5.9.3 的一条 **WARN + 栈**：`Failed to resolve uniform inPaleGarden, reason: Unknown variable: BIOME_PALE_GARDEN`（`CustomUniforms.<init>` / `IrisRenderingPipeline.<init>`）——**不崩**（无 `Unreported exception`、无崩溃报告），管线仍建起（`Using shaderpack` 命中 1 次）；属光影包比 Iris 新的版本错位，登记在案，`SHADER-VISION-26.1.2` 仍是回归守卫。
- 工程:**新增全局调试命令 `mt_env.ps1 debug`，并据此精简测试流程里的 Esc 按键**（用户裁决：「添加全局调试命令，禁止游戏失焦打开 ESC 菜单」＋「如检测到已使用禁止失焦ESC菜单命令，则从测试流程中删除不必要的ESC按键操作」）。① `lib/Mt.Paths.psm1` 新增并导出 `Get-MtPauseOnLostFocus` / `Set-MtPauseOnLostFocus`（只动 `options.txt` 的 `pauseOnLostFocus` 一个键，ASCII/无 BOM）；② `mt_env.ps1 debug --version <V> [--pause-lock on|off|status] [--shaders on|off|status]` 为统一入口（`--pause-lock on` ⇒ `pauseOnLostFocus:false`；不带开关即回显两项状态；`off` 会明确 WARN 属对照实验）；③ `mt_launch.ps1` 在**每次冷启动之前**强制 `Set-MtPauseOnLostFocus -Enabled $false` 并回显 `PAUSE_LOCK: on`（游戏退出会重写 options.txt，故只能在启动前写）；④ `mt_inject.ps1` 在「pause-lock 生效 + `-NoEsc`」时**跳过** `Esc→Tab→Enter` 归一化并回显 `ESC_SKIP: pause-lock 生效 + -NoEsc ⇒ 跳过 …`（该归一化唯一必要场景是清空容器/聊天界面，Esc 仍是唯一安全的关容器键，故去掉 `-NoEsc` 即恢复旧行为）。**实测**：`mt_env.ps1 debug --version 1.21.1` → `MT_DEBUG: 1.21.1 pause-lock=on (pauseOnLostFocus=false)` + `MT_SHADERS: true …`；launch 日志含 `PAUSE_LOCK: on — 失焦不再打开 ESC 暂停菜单…`；`mt_inject.ps1 cmd --command '/astralprobe slimecheck SL1' --version 1.21.1 --no-esc` 回显 `ESC_SKIP` 且命令照常送达（游戏回 `错误的命令参数` + 错误定位行 —— 注入通道正常，仅因 1.21.1 探针脚本无 `slimecheck` 子命令）。
- 工程:**游戏进程捕获/等待时长受控**（用户裁决「再次游戏进程检测等待时间过长的问题，请检查流程，并严格控制游戏进程捕获时长」）。① `mt_launch.ps1` 取消就绪判据前的固定 `Start-Sleep 30`，改为**立即 3s 轮询**（读日志是纯文件读、成本可忽略），硬上限仍 180s、可用 `MT_LAUNCH_READY_TIMEOUT_SEC`（≥30）覆写，并新增 `READY_WAIT: budget=…` 与 `MT_TIMING: world-ready=…s` 两条读数。**实测（同机同场景）**：`MT_TIMING: world-ready=27s`，旧「固定空等 30s + 15s 轮询」版本为 `MT_LAUNCH: OK (47s)` ⇒ 省下约 20s 纯等待。② 既有各阶段硬上限保持并在 AGENTS 全局规则中列表化：env 240s / build 300s / preflight 120s / launch 180s / 单用例 180s，launch 日志 90s 零增长即判 `CHILD: STALL`。③ AGENTS 追加调用方约定：长驻阶段（launch）**不得**用管道捕获输出（`| Select-Object`），须改文件重定向，否则命令行要等子进程放开 stdout 句柄。
- 工程:**「把游戏窗口搬到第二显示器」规则全局移除**（用户裁决：「全局移除移动游戏窗口到第二屏幕的规则」）。① `mt_launch.ps1`：删除搬移代码块、`--monitor` / `--size` 两个参数及其启动前校验、`lib/Mt.Win32.psm1` 的导入；不再改窗口位置/尺寸，日志里不再有 `MT_WINDOW:` 读数。② `lib/Mt.Win32.psm1`：删除 `Get-MtMonitors` / `Move-MtWindowToMonitor` 两个函数及其导出项（只服务该规则，已核对 `mt_capture` / `mt_inject` / `mt_ime` / `mt_preflight` 与全部用例均无调用），保留 `Find-MtMinecraftWindow` / `Get-MtWindowRect` / `Get-MtClientRect`（截图 crop 与注入坐标映射仍依赖）。③ `AGENTS.md` 的用法行与阶段说明改写为「已全局移除」并写明**禁止加回**（含「默认搬到第二屏」的隐式默认）。④ 两仓同步改（改动前两仓 HEAD 逐字节相同，已核对）；语法自检：`mt_launch.ps1` / `mt_inject.ps1` / `mt_env.ps1` / `lib/Mt.Paths.psm1` / `lib/Mt.Win32.psm1` 全部 PowerShell 解析通过。
- 工程:**「全局测试规则（测试环境硬性要求）」写入 `AGENTS.md` 并严格执行**（用户要求「将以上要求写入全局测试规则，并严格执行」）。四条规则：① 三类模组必须装齐（史莱姆压制=硬闸门 / 优化类 ImmediatelyFast+FerriteCore / 1.21.1 探针宿主 KubeJS+Rhino+Architectury），并写明 1.20.1 的史莱姆压制来自 build.gradle 的例外；② 光影包必须就位且**默认启用**（含 1.20.1 跳过原因、以及「需关光影冷启动的用例自己显式 `shaders --state off`」）；③ **禁止游戏失焦打开 ESC 菜单**（`PAUSE_LOCK: on`，统一入口 `mt_env.ps1 debug`），并据此删除流程里不必要的 Esc（`ESC_SKIP` 闸门）；④ 游戏进程捕获/等待时长硬上限表 + 耗时回显 + 「禁止无限等待」。规则明确：任一条不满足 ⇒ 该轮测试**无效**，禁止静默降级或手工绕过。


- 工程:**「构建只花几秒、命令却几分钟不返回」的根因定位与修复 —— 长驻子进程与父 pwsh 共用控制台导致收尾阻塞**（2026-09-17，用户提出「检查为什么构建时间这么长」）。**现象**：`mt_build.ps1` / `mt.ps1 --phase launch` 的日志早已打印 `MT_BUILD: OK (4s)` / `MT_LAUNCH: OK`，调用方（`| Select-Object`、同步等待）却要等 20+ 分钟甚至永不返回。**取证**：① 构建日志 `BUILD SUCCESSFUL in 5s`、脚本自身 `MT_BUILD: OK (4s)`；② 父 pwsh 已执行到脚本末尾的 `exit`（进程 CPU 仅 0.44s、18 线程）却仍存活；③ 其子进程列表里挂着一个 `conhost.exe`；④ `Start-Job` 复现：同一条构建走**管道捕获** 90s 仍未结束（判超时），而把输出改为**文件重定向**时不复现 ⇒ 与构建本身无关。**根因**：`lib/Mt.Proc.psm1` 的 `Start-MtProcessToFile` 用 `Start-Process -NoNewWindow` 启动 `cmd.exe → gradlew → Gradle 守护进程 / 游戏客户端`，**子进程链与父 pwsh 共用同一个控制台**；长驻子进程持续持有它 ⇒ 父 pwsh 在**控制台拆卸**上阻塞，任何等待 EOF 的调用方都跟着挂住。（注意：这与「Gradle 守护进程构建完成后不自行退出」是**两个不同问题**，后者由既有 60s 看门狗 + 日志/产物双重验证覆盖，本条不能靠提高超时解决。）**修复**：`Start-MtProcessToFile` 的两个分支 `-NoNewWindow` → **`-WindowStyle Hidden`**（为新进程**新建**一个隐藏控制台，不再共用），日志仍由 `> 文件 2>&1` / `-RedirectStandardOutput` 落盘，抓取口径与合并 stderr 语义完全不变。**验证**：同一条管道调用 `pwsh -File scripts/test/mt_build.ps1 --version 1.21.1 --timeout 60 | Select-Object -Last 3` 由「90s 超时 / 20+ 分钟不返回」变为 **1.6s 返回**（`MT_BUILD: OK (2s)`）；1.20.1 侧管道构建 **9.1s 返回**并产出 `astral_dice-2.0.0-SNAPSHOT.10+forge_1.20.1.jar`。⚠️ 隐藏窗口只作用于**控制台**本身：游戏窗口由 GLFW 自建，实机冷启动进入世界正常（见同轮验证）。同轮把「长驻阶段不要用管道捕获、改文件重定向」写进 `AGENTS.md` 的全局测试规则第 4 条作为调用方约定。


**2026-09-17：1.20.1 的「模组已装载」判据修正（Forge 日志格式与 NeoForge 不同；史莱姆硬闸门曾把已装载模组判成缺失）**

- 工程:**现象**：`pwsh -File scripts/test/mt.ps1 --version 1.20.1 --phase launch` 在**成功进入世界之后**以 `MT_LAUNCH: ERROR — 未检测到「Superflat World No Slimes」模组（测试环境硬性要求：超平坦世界的史莱姆会干扰测试流程）` 退出（退出码 2）；同轮 `.err` 里还有 `IMMEDIATELYFAST_LOADED=false(优化模组兼容性验证未生效)` / `FERRITECORE_LOADED=false`。而 `run/1.20.1/logs/debug.log` 明确写着 `Found valid mod file superflat-world-no-slimes-344185-6184996.jar with {superflatworldnoslimes} mods - versions {3.5}`、`… ferrite-core-DG5Fn9Sz.jar with {ferritecore} mods - versions {6.0.1}`、`… collective-342584-7148968.jar with {collective} mods - versions {8.13}`。
- 工程:**根因**：三个闸门共用的判据 `\(modId\)` 是 **NeoForge** 的「已加载模组清单行」格式；**Forge 1.20.1 不打印该行**（实测 `run/1.20.1/logs/latest.log` 里与模组清单相关的只有一行 `[modloading-worker-0/INFO] [Collective/]: Loading Collective version 8.13.`）—— Forge 的清单只出现在 `logs/debug.log`（以及启动控制台）里，格式是 `Found valid mod file <file> with {modId,…} mods - versions {…}` ⇒ 1.20.1 上三个判据**全部假阴性**，其中史莱姆那条是**硬失败**，直接把整轮测试判死（属工具链缺陷，与模组本身无关）。
- 工程:**修复（`scripts/test/mt_launch.ps1`）**：① 新增**版本感知**判据函数 `Test-MtModLoaded`（NeoForge 线 = `latest.log` 的 `\(modId\)`；1.20.1 = `logs/debug.log` + `runclient_launch.log` 的 `\{modId\}`，HashSet 去重），五处调用点（`superflatworldnoslimes` / `collective` / `immediatelyfast` / `ferritecore` / `modernfix`）统一改用它；② 启动前**同时删除 `logs/debug.log`**（与 `latest.log` 同口径；Forge 的 log4j2 带 `OnStartupTriggeringPolicy`，即使删不掉启动时也会被卷成 `debug-%i.log.gz` ⇒ 读到的必然是**本轮**内容），避免上一轮的 debug.log 把**本轮没装**的模组判成已装载；③ 两种格式都仍然**只认清单行、不认裸名字** —— 存档 `level.dat` 记着上次带着这些模组跑过时加载器会打印 `<modId> (version X -> MISSING)`，只搜名字会把「缺失」误判成「已加载」（此前踩过）。
- 工程:**同步补齐光影读数（1.20.1 / 1.21.1）**：`SHADERS=enabled pack=<包名>` + `SHADERPACK_LOADED=true`，判据取 `Using shaderpack:` 行，缺则 WARN。⚠️ 实测该行**出现在进入世界之后**（1.20.1：启动第 12s 先打印 `Shaders are disabled because no valid shaderpack is selected`，进世界后第 36s 才打印 `Using shaderpack: ComplementaryUnbound_r5.9.3.zip`）⇒ 必须放在已进世界的闸门里判，放启动早期会误报。**同轮修正光影配置路径（`mt_env.ps1`）**：1.20.1 的加载器是 **Oculus，它读写的是 `config/oculus.properties`**（实测 `run/1.20.1/config/` 下只有该文件，内含 `shaderPack=ComplementaryUnbound_r5.9.3.zip` + `enableShaders=true`，**没有** `iris.properties`），而旧代码一律写 `iris.properties` ⇒ 1.20.1 的「默认启用光影」此前只是被 Oculus 自身默认值兜住、并未被工具链真正设定过。新增 `Get-MtIrisConfigFile`（按版本返回 `iris.properties` / `oculus.properties`），并在 `Install-MtRenderStack`、`Invoke-MtEnvShaders`（`--state on|off|status`）、`debug --shaders status` 三处改用它；旧注释「1.20.1 不装渲染栈 ⇒ 光影不可用」**作废**（该结论来自「手工把 SRG jar 放进 run/mods」那条错路；改走 `modImplementation` 后 1.20.1 的 Embeddium + Oculus 与光影均正常）。
- 工程:**实测（本轮）**：`mt_env.ps1 mods --version 1.20.1` 后 `--phase launch` **全绿** —— `PAUSE_LOCK: on` / `READY_WAIT: budget=180s` / `MT_TIMING: world-ready=18s` / `EMBEDDIUM_LOADED=true` / `OCULUS_LOADED=true` / `FERRITECORE_LOADED=true` / `SLIMEGUARD_LOADED=true` / `COLLECTIVE_LOADED=true` / `MT_ASSERT_KUBEJS: PASS — server.log 0 errors` / `PREFLIGHT_OP: OK — has2=1 level=4 src=cmdsource+profile dump=rc=1` / **`MT_LAUNCH: OK (35s)`**；唯一 WARN = `IMMEDIATELYFAST_LOADED=false`（原因见下条）。
- 工程:**三条线复验（同一轮，均走改后的 `Test-MtModLoaded`）**：① `1.21.1`（先 `--phase env` 重建测试世界）→ `SODIUM_LOADED=true` / `IRIS_LOADED=true` / `SHADERS=enabled pack=ComplementaryUnbound_r5.9.3.zip` / `SHADERPACK_LOADED=true` / `IMMEDIATELYFAST_LOADED=true` / `FERRITECORE_LOADED=true` / `MODERNFIX_LOADED=true` / `SLIMEGUARD_LOADED=true` / `COLLECTIVE_LOADED=true` / `MT_ASSERT_KUBEJS: PASS — server.log 0 errors` / `MT_TIMING: world-ready=21s` / **`MT_LAUNCH: OK (38s)`**，`.err` **完全为空**（无任何 WARN）；② `26.1.2`（同轮 `--phase env`）→ `KUBEJS_LOADED=true` / `RHINO_LOADED=true` / `SODIUM_LOADED=true` / `IRIS_LOADED=true` / `IMMEDIATELYFAST_LOADED=true` / `MODERNFIX_LOADED=true` / `FERRITECORE_LOADED=true` / `SHADERS=enabled` / `SHADERPACK_LOADED=true` / `SLIMEGUARD_LOADED=true` / `COLLECTIVE_LOADED=true` / `MT_TIMING: world-ready=18s` / **`MT_LAUNCH: OK (36s)`** —— 这是 `mt_env` 把该线 `enableShaders` 默认值翻成 `true` **之后**的首次启动复验（光影正常加载、无崩溃报告）；③ `1.20.1` 见上一条（`MT_LAUNCH: OK (35s)`）。⇒ 判据重构对 NeoForge 两条线为零行为变化（仍走 `\(modId\)` 分支），对 Forge 线则是「假阴性 → 真阳性」的修正。
- 工程:**光影配置路径修复的实机验证（1.20.1）**：`mt_env.ps1 mods --version 1.20.1` 后 `run/1.20.1/config/oculus.properties` 被写入 `shaderPack=ComplementaryUnbound_r5.9.3.zip` + `enableShaders=true`（此前该文件由 Oculus 自己在首次启动时创建、**工具链从未真正设定过**），`mt_env.ps1 debug --version 1.20.1` → `MT_DEBUG: 1.20.1 pause-lock=on (pauseOnLostFocus=false)` + `MT_SHADERS: true (shaderPack=ComplementaryUnbound_r5.9.3.zip)`（读的就是 `oculus.properties`）；随后 launch **`MT_LAUNCH: OK (39s)`**、`SHADERS=enabled pack=ComplementaryUnbound_r5.9.3.zip` / `SHADERPACK_LOADED=true`、`MT_TIMING: world-ready=21s`，`.err` 仅剩 `IMMEDIATELYFAST_LOADED=false` 一条 WARN（上游冲突，见下条）。

**2026-09-17：ImmediatelyFast 在 1.20.1 上不可用（上游硬冲突，非配置问题）—— 取舍为「保光影」，1.20.1 线豁免**

- 工程:**结论**：`ImmediatelyFast` 的 **1.20.1 构建与 Oculus 1.8.0 互斥**，装上客户端就起不来；`FerriteCore` 不受影响（在 1.20.1 走 `modImplementation` 由 MDG 重映射，实测 `FERRITECORE_LOADED=true`）。⇒ 1.20.1 线按 WARN 回显 `IMMEDIATELYFAST_LOADED=false` 并记明原因（**不做硬失败**，与 Sodium/Iris 同口径）；1.21.1 / 26.1.2 两线照装（`1.6.14+1.21.1` / `1.15.3+26.1-neoforge`）。
- 工程:**三版复测（全部失败，形态不同）**：① `1.5.5+1.20.4`（Modrinth 最新、`game_versions` 也声明 1.20.1，但产物是 1.20.4 的）⇒ 启动即 `MixinApplyError: Mixin [immediatelyfast-common.mixins…]` → `InvalidInjectionException: Critical injection failure`；② `1.2.4+1.20.1`（`NJ17fqEK`）与 ③ `1.2.3+1.20.1`（`bHjLCRu6`，均经 `modImplementation` 重映射）⇒ 能过 mixin/bootstrap，但**在模组构造期硬崩**：
  `[ImmediatelyFast]: Loading ImmediatelyFast 1.2.3+1.20.1` → `[ImmediatelyFast]: Found Oculus 1.8.0. Enabling compatibility.` → `[ImmediatelyFast]: Failed to initialize Iris compatibility. Try updating Iris and ImmediatelyFast before reporting this on GitHub` → `java.lang.ClassNotFoundException: net.coderbot.iris.vertices.ImmediateState`（`at forge.net.raphimc.immediatelyfast.compat.IrisCompat.init(IrisCompat.java:41)` → `…ImmediatelyFastForge.<init>(ImmediatelyFastForge.java:32)` → `net.minecraftforge.fml.javafmlmod.FMLModContainer.constructMod(FMLModContainer.java:77)`）⇒ `:forge-1.20.1:runClient` **11 秒**即以 `Process … finished with non-zero exit value -1` 结束（**无崩溃报告、无 hs_err**，现象极易被误读成「Gradle/GLFW 问题」）。
- 工程:**字节码取证（不靠推测）**：`javap -p -c` 反编译 jar 内 `forge/net/raphimc/immediatelyfast/compat/IrisCompat.class` —— `init()` 的**第 0 条指令**就是 `Class.forName("net.coderbot.iris.vertices.ImmediateState")`，紧随 `…ExtendingBufferBuilder`，即它对接的是 **Iris 1.6 的包名**；1.20.1 的光影加载器 Oculus 1.8.0 基于 Iris 1.7+，包名已改为 `net.irisshaders.iris`。`ImmediatelyFastForge.<init>` 只是 `Optional.ifPresent(→ IrisCompat.init())`（探测到 `oculus` 即无条件调用），**没有任何配置项能跳过**，且 `init()` 开头就置 `IRIS_LOADED=true` ⇒ 「用配置关掉 Iris 兼容」这条路不存在。（Modrinth 上声明支持 1.20.1 的 Forge 构建**只有 1.2.0~1.2.4 五个**，全部同源同缺陷。）
- 工程:**若日后要两边都满足**：唯一可行改法是**把 Oculus 降级到 1.7.0**（Iris 1.6 包名 = IF 1.2.x 的目标版本）。可行性初查：光影包自身兼容 Iris 1.6 —— `shaders/shaders.properties` 内含 `# Check for block emission attribute to detect Iris 1.7 and above because the ref-res causes an error in 1.6.10 and below`，且 `iris.features.optional = CUSTOM_IMAGES SSBO BLOCK_EMISSION_ATTRIBUTE FADE_VARIABLE ENTITY_TRANSLUCENT` 把该特性列为**可选** ⇒ 理论可行；但会改动**已验证通过**的光影栈，**须先经用户裁决**，换版后必须复跑 `SHADER-VISION` 用例并核对 `SHADERPACK_LOADED=true`。
- 工程:**文档/配置口径同步**：`AGENTS.md` 全局测试规则第 1 条（优化类模组）写入 1.20.1 豁免与完整根因，第 2 条（光影）改写为「**三条线都生效**」并写明 1.20.1 渲染栈来自 `modImplementation`、光影实际加载；`forge-1.20.1/build.gradle` 的 ImmediatelyFast 注释块改写为完整取证（三版失败形态 + javap 结论 + 唯一备选方案 + 复测入口）；`mt_env.ps1` 的 `$script:PerfModsByVersion['1.20.1']` 注释与 `MT_MODS: SKIP` 文案同步改写。

**2026-09-18：目标选择器中置 HUD 类型标签 + actionbar 四态提示与剩余时间（t38；仅两条发布线，26.1.2 未改）**

- 工程:**HUD 口径**（用户裁决）：中央 HUD 仍只画**一行**，组成 = `hud.astral_dice.target_select.target`（`目标：%s（%s 格）`，**参数个数不变**）+ 字面量 `" · "` + `hud.astral_dice.target_select.tag.<后缀>`，整行仍用 `TargetSelectionClient.highlightColor` 的颜色绘制；后缀由新增只读访问器 `TargetSelectionClient.targetTagKey(LivingEntity)` 推导 —— 玩家按 `isFriendly`（同队）分 `teammate`/`player`，其余生物按「自己拥有的（`OwnableEntity#getOwnerUUID()` 等于选择者）→ `pet`、`isHostile` → `hostile`、其余 → `neutral`」。**无有效目标时不画**（`currentTarget == null` 直接 return，与改前一致）；`TargetSelectOverlay` 在两线的注册点（`ModClientEvents` 的 `registerGuiLayers` / `registerGuiOverlays`，id `target_select`）**改动前就在**，本轮未注销、未改注册方式。
- 工程:**actionbar 四态稳态提示**（用户裁决）：`TargetSelectionClient.refreshActionBarPrompt` 复写的稳态分支由旧的单一 `msg.astral_dice.target_select.active` 改为 `steadyPrompt()` 四态判定 —— ① `currentTarget != null` → 绿 `prompt.valid`；② 否则 `rejectedTarget != null` → 红 `prompt.rejected`（参数 = 会话 `TargetType` 枚举名对应的 `valid_target.<枚举名>`，`translatableWithFallback` 的 fallback = 枚举名）；③ 否则 → 白 `prompt.no_target_self`（`allowSelf()` 真）/ `prompt.no_target`（假），参数 = 技能名（`skill.<actionId>`，fallback = actionId 本身，故 `test_echo_*` 不显示裸键）。四态末尾**统一**追加黄色 `msg.astral_dice.target_select.time`（`§e` 语义，`Component.empty().append(main).append(time)` 保证两段各自上色）。**剩余秒数** = 新增只读访问器 `TargetSelectionClient.remainingSeconds()` = `ceil((expireTick - level.getGameTime()) / 20)`、最小 0，与超时判据 `tick()` 的 `gameTime >= expireTick` **同源**。瞬态 `showPrompt`（`transientPrompt`/`transientPromptUntil` 的 40 tick 窗口）机制**原样保留**且仍优先于稳态提示。**删除**了 `updateRaycastTarget` 里对 `msg.astral_dice.target_select.rejected` 的瞬态 `showPrompt(...)`（新口径下「对准错误目标」是稳态红字，留着会与新文案来回跳）；旧键 `msg.astral_dice.target_select.rejected` 与 `…active` 的 lang 条目**保留不删**，只是代码不再调用。
- 工程:**「允许对自身使用」管线（消费方侧，前置库 `F:\MCProject\starengine_lib` 零改动、未 bump 版本）**：① 两线各新增 `target/SelfTargetable.java`（`default boolean allowSelf() { return false; }`，两条线该文件除类 javadoc 里对 payload 类名的平台指涉外**逐字节等价**）；② `TargetSelectionManager.start` 计算 `boolean allowSelf = action instanceof SelfTargetable st && st.allowSelf();`，写入 `Session.allowSelf`（供将来服务端校验自身目标）并随下行包下发；③ 网络：1.21.1 的 `network/TargetSelectStartPayload`（record）追加 `boolean allowSelf` 字段（`ByteBufCodecs.BOOL`）、`ModPayloads` 处理器透传；1.20.1 的 `ModNetwork.TargetSelectStartMessage` 追加同名字段（`buf.writeBoolean`/`readBoolean`）并在处理器里透传；④ 客户端 `start(...)` 增参并保存，新增 `allowSelf()`；`useOnSelfBySecondaryClick()` 在 `allowSelf()` 为真时发 `TargetSelectConfirmPayload(token, mc.player.getId())`（1.20.1 为 `ModNetwork.TargetSelectConfirmMessage`）并 `deactivate()`，为假时**行为与改前逐字相同**（弹 `self_unsupported`、会话保留、不提交）。**当前无任何动作实现 `SelfTargetable`** ⇒ 全部现有会话 `allowSelf=false`，玩家可见行为不变（`test_echo_*`、三个立牌动作均未实现）。**未放宽** `TargetType.matches`（仍排除选择者自身）⇒ 将来真正启用需同时改前置库的目标类型校验，该约束写进了 `SelfTargetable` 的 javadoc。
- 工程:**lang**：两线 `zh_cn.json` / `en_us.json` 各新增 **17** 个键（5 个 HUD 类型标签 + 4 个四态 prompt + 3 个技能名 + 1 个时间 + 4 个有效目标名），四条 lang 文件改动后均为 **647** 键且 `tools/check_lang_sync.ps1` 三线全绿、零结构标记 WARN。
- 工程:**工具链同步**：`cases/SELECTOR-KEYS-1.21.1.json` 的「证据口径」note 里「默认常驻提示→…active」一句改写为四态键名映射（**只改叙述文本，16 条断言与 58 个步骤一字未动**，用例语义不变）；1.20.1 侧该用例本就没有这处映射文本，故未改。四态逻辑本身**未新增**自动化用例（本轮无游戏内验证，见 `temp/t38/t38-report.md` 的自证边界一节）。
