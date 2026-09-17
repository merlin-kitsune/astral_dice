# astral_dice 自动化测试规范（TESTING SPEC）

> 本文件是 `astral_dice_multiloader` 仓库**唯一**的测试流程落地规范，替代已废弃的
> `scripts/test/FLOW_1.20.1_functional.md`（2026-09-12 删除，历史版本可 `git show` 取回）。
> 工作区总规范见 `AGENTS.md`「自动化测试流程」一节；两者冲突时**以本文件为准**（本文件随工具链一起入库、可被 review）。
> 适用分支：`multi-1.20.1-1.21.1`（唯一允许运行本流程的分支；前置检查会强断言）。

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
| 分支 | `multi-1.20.1-1.21.1`（强断言，其它分支直接拒绝） |
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

**2026-09-17：测试环境三条线统一（史莱姆压制 / 优化类 / 探针宿主 / 光影包）＋ 全局测试规则落地（禁止失焦 ESC 菜单 / 进程捕获时长受控 / 冗余 Esc 精简 / 窗口搬移移除）**

- 工程:**1.21.1 补全「超平坦无史莱姆」硬前置，并把优化类模组铺到三条线**（用户两轮要求：「1.21.1 环境缺少没有史莱姆的超平坦世界模组，这是必需的模组，没有会使史莱姆干扰测试，补全该模组然后重新运行 1.21.1 测试」＋「所有测试环境增加 ImmediatelyFast、FerriteCore 模组，用于优化模组兼容性测试」）。① `mt_env.ps1` 新增 `$script:PerfModsByVersion` / `$script:PerfPrefixesByVersion` / `$script:SlimeGuardByVersion` 三张**按版本**表，`Install-MtPerfMods` / `Install-MtSlimeGuard` 去掉 `if ($Paths.version -ne '26.1.2') { return 0 }` 早退，`Invoke-MtEnvMods` 在 1.20.1 / 1.21.1 分支也调用它们。② 坐标一律 **Modrinth Maven** 且版本 id 钉死（sha1 + size 幂等校验）：1.21.1 史莱姆压制 `superflat-world-no-slimes:5VtNIDJA`(1.21.1-3.5) + 前置 `collective:4XRlrKGN`(1.21.1-8.39)；优化类 1.21.1 `immediatelyfast:OUpXxw4n`(1.6.14+1.21.1-neoforge) + `ferrite-core:x7kQWVju`(7.0.3-neoforge)、1.20.1 `immediatelyfast:rvsLEEZU`(1.5.5+1.20.4-forge，声明支持 1.20.1) + `ferrite-core:DG5Fn9Sz`(6.0.1-forge)、26.1.2 补 `ferrite-core:LtVvw4uS`(9.0.0-neoforge)。③ **1.20.1 的史莱姆压制故意不入表**（`forge-1.20.1/build.gradle` 的 `modImplementation` 已提供 collective + superflat-world-no-slimes，重复放入 `run/1.20.1/mods` 会被 FML 判重复模组），函数在该版本回显来源说明并跳过。④ `mt_launch.ps1` 的史莱姆硬闸门由 26.1.2 分支移到**版本无关公共段**（错误信息改为按 `$Version` 生成），并新增 1.20.1 / 1.21.1 的 `IMMEDIATELYFAST_LOADED` / `FERRITECORE_LOADED`（1.21.1 另含 `MODERNFIX_LOADED`）读数。⑤ **实测**：`mt_env.ps1 mods` 三线落位成功（1.21.1 新装 4 个、1.20.1 新装 2 个、26.1.2 补 1 个）；1.21.1 `--phase launch` 全绿：`SLIMEGUARD_LOADED=true` / `COLLECTIVE_LOADED=true` / `IMMEDIATELYFAST_LOADED=true` / `FERRITECORE_LOADED=true` / `MODERNFIX_LOADED=true` / `MT_ASSERT_KUBEJS: PASS — server.log 0 errors` / `PREFLIGHT_OP: OK` / `MT_LAUNCH: OK (45s)`。
- 工程:**1.21.1 探针宿主（KubeJS + Rhino + Architectury API）补全**（同一轮发现的第二个环境缺口）。`neoforge-1.21.1/build.gradle` 有意不依赖 KubeJS（会让 datagen 也装载它），约定「dev run 直接从 `run/mods` 装载整合包同版 kubejs」——此前**只靠手工复制**，dev-next 工作树的 `run/1.21.1/mods` 里没有 ⇒ `MT_ASSERT_KUBEJS: BLOCKED`、`/astralprobe` 命令不存在、`MT_preflight-op` ERROR。新增 `Install-MtProbeHost`（`$script:ProbeHostMods1211`：按整合包复制 `kubejs-neoforge-*` / `*rhino-*` / `architectury-*-neoforge.jar`，落位时剥掉整合包文件名里的中文方括号前缀，尺寸 + 时间戳幂等，缺任一即 BLOCKED 返回 11）。**实测**：`MT_MODS: OK — 1.21.1 探针宿主 新装 3 个（kubejs-neoforge-2101.7.2-build.374.jar / rhino-2101.2.8-build.91.jar / architectury-13.0.11-neoforge.jar）`，随后 launch 的 `MT_ASSERT_KUBEJS: PASS`、`PREFLIGHT_OP: OK — has2=1 level=4 src=cmdsource+profile dump=rc=undefined`、`ASTRALPARTY_DUMP: rc=rc=undefined lockraw_rows=28`。
- 工程:**光影包就位并默认启用**（用户要求「游戏环境缺少光影包，添加光影包并设置默认启用」）。`Install-MtRenderStack` 改为**版本感知**（新增 `$script:RenderModsByVersion`：26.1.2 含 Sodium/Iris 规格、1.21.1 为空数组因渲染模组由整合包复制、1.20.1 回显 SKIP 因 Embeddium/Oculus 在 mojmap 下无法解析），并把 `ComplementaryUnbound_r5.9.3.zip` 落位 `run/<V>/shaderpacks/`、把 `config/iris.properties` 写成 `shaderPack=ComplementaryUnbound_r5.9.3.zip` + **`enableShaders=true`**。**实测 1.21.1**：`run/1.21.1/shaderpacks/ComplementaryUnbound_r5.9.3.zip`(553400B) + `iris.properties` 两键正确 + 启动日志 `[Iris/]: Using shaderpack: ComplementaryUnbound_r5.9.3.zip`（无崩溃报告）。⚠️ 同轮实测到 Iris 1.8.14-beta.1 对 Complementary r5.9.3 的一条 **WARN + 栈**：`Failed to resolve uniform inPaleGarden, reason: Unknown variable: BIOME_PALE_GARDEN`（`CustomUniforms.<init>` / `IrisRenderingPipeline.<init>`）——**不崩**（无 `Unreported exception`、无崩溃报告），管线仍建起（`Using shaderpack` 命中 1 次）；属光影包比 Iris 新的版本错位，登记在案，`SHADER-VISION-26.1.2` 仍是回归守卫。
- 工程:**新增全局调试命令 `mt_env.ps1 debug`，并据此精简测试流程里的 Esc 按键**（用户裁决：「添加全局调试命令，禁止游戏失焦打开 ESC 菜单」＋「如检测到已使用禁止失焦ESC菜单命令，则从测试流程中删除不必要的ESC按键操作」）。① `lib/Mt.Paths.psm1` 新增并导出 `Get-MtPauseOnLostFocus` / `Set-MtPauseOnLostFocus`（只动 `options.txt` 的 `pauseOnLostFocus` 一个键，ASCII/无 BOM）；② `mt_env.ps1 debug --version <V> [--pause-lock on|off|status] [--shaders on|off|status]` 为统一入口（`--pause-lock on` ⇒ `pauseOnLostFocus:false`；不带开关即回显两项状态；`off` 会明确 WARN 属对照实验）；③ `mt_launch.ps1` 在**每次冷启动之前**强制 `Set-MtPauseOnLostFocus -Enabled $false` 并回显 `PAUSE_LOCK: on`（游戏退出会重写 options.txt，故只能在启动前写）；④ `mt_inject.ps1` 在「pause-lock 生效 + `-NoEsc`」时**跳过** `Esc→Tab→Enter` 归一化并回显 `ESC_SKIP: pause-lock 生效 + -NoEsc ⇒ 跳过 …`（该归一化唯一必要场景是清空容器/聊天界面，Esc 仍是唯一安全的关容器键，故去掉 `-NoEsc` 即恢复旧行为）。**实测**：`mt_env.ps1 debug --version 1.21.1` → `MT_DEBUG: 1.21.1 pause-lock=on (pauseOnLostFocus=false)` + `MT_SHADERS: true …`；launch 日志含 `PAUSE_LOCK: on — 失焦不再打开 ESC 暂停菜单…`；`mt_inject.ps1 cmd --command '/astralprobe slimecheck SL1' --version 1.21.1 --no-esc` 回显 `ESC_SKIP` 且命令照常送达（游戏回 `错误的命令参数` + 错误定位行 —— 注入通道正常，仅因 1.21.1 探针脚本无 `slimecheck` 子命令）。
- 工程:**游戏进程捕获/等待时长受控**（用户裁决「再次游戏进程检测等待时间过长的问题，请检查流程，并严格控制游戏进程捕获时长」）。① `mt_launch.ps1` 取消就绪判据前的固定 `Start-Sleep 30`，改为**立即 3s 轮询**（读日志是纯文件读、成本可忽略），硬上限仍 180s、可用 `MT_LAUNCH_READY_TIMEOUT_SEC`（≥30）覆写，并新增 `READY_WAIT: budget=…` 与 `MT_TIMING: world-ready=…s` 两条读数。**实测（同机同场景）**：`MT_TIMING: world-ready=27s`，旧「固定空等 30s + 15s 轮询」版本为 `MT_LAUNCH: OK (47s)` ⇒ 省下约 20s 纯等待。② 既有各阶段硬上限保持并在 AGENTS 全局规则中列表化：env 240s / build 300s / preflight 120s / launch 180s / 单用例 180s，launch 日志 90s 零增长即判 `CHILD: STALL`。③ AGENTS 追加调用方约定：长驻阶段（launch）**不得**用管道捕获输出（`| Select-Object`），须改文件重定向，否则命令行要等子进程放开 stdout 句柄。
- 工程:**「把游戏窗口搬到第二显示器」规则全局移除**（用户裁决：「全局移除移动游戏窗口到第二屏幕的规则」）。① `mt_launch.ps1`：删除搬移代码块、`--monitor` / `--size` 两个参数及其启动前校验、`lib/Mt.Win32.psm1` 的导入；不再改窗口位置/尺寸，日志里不再有 `MT_WINDOW:` 读数。② `lib/Mt.Win32.psm1`：删除 `Get-MtMonitors` / `Move-MtWindowToMonitor` 两个函数及其导出项（只服务该规则，已核对 `mt_capture` / `mt_inject` / `mt_ime` / `mt_preflight` 与全部用例均无调用），保留 `Find-MtMinecraftWindow` / `Get-MtWindowRect` / `Get-MtClientRect`（截图 crop 与注入坐标映射仍依赖）。③ `AGENTS.md` 的用法行与阶段说明改写为「已全局移除」并写明**禁止加回**（含「默认搬到第二屏」的隐式默认）。④ 两仓同步改（改动前两仓 HEAD 逐字节相同，已核对）；语法自检：`mt_launch.ps1` / `mt_inject.ps1` / `mt_env.ps1` / `lib/Mt.Paths.psm1` / `lib/Mt.Win32.psm1` 全部 PowerShell 解析通过。
- 工程:**「全局测试规则（测试环境硬性要求）」写入 `AGENTS.md` 并严格执行**（用户要求「将以上要求写入全局测试规则，并严格执行」）。四条规则：① 三类模组必须装齐（史莱姆压制=硬闸门 / 优化类 ImmediatelyFast+FerriteCore / 1.21.1 探针宿主 KubeJS+Rhino+Architectury），并写明 1.20.1 的史莱姆压制来自 build.gradle 的例外；② 光影包必须就位且**默认启用**（含 1.20.1 跳过原因、以及「需关光影冷启动的用例自己显式 `shaders --state off`」）；③ **禁止游戏失焦打开 ESC 菜单**（`PAUSE_LOCK: on`，统一入口 `mt_env.ps1 debug`），并据此删除流程里不必要的 Esc（`ESC_SKIP` 闸门）；④ 游戏进程捕获/等待时长硬上限表 + 耗时回显 + 「禁止无限等待」。规则明确：任一条不满足 ⇒ 该轮测试**无效**，禁止静默降级或手工绕过。

