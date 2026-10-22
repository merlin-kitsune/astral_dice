# AutoJS 通道迁移欠账（legacy 用例 → eval 通道）

> ⛔ **2026-10-18 更新（本文件正文为迁移前快照，数字以本节为准）**：229 条 legacy 用例已由
> `scripts/test/convert_cases_autojs.py` 机械化迁移，`channel=autojs` 覆盖率 **231/231**，
> legacy 驱动原语（`inject_command/inject_key/inject_mouse/screenshot/kubejs_reload`）残留 **0**
> （`python3 scripts/test/lib/mt_case.py validate` ⇒ `files=231 errors=0`）。
> 通道口径与实机证据见 `docs/wsl-autojs-toolchain.md`；工具链已改为 WSL + bash/python（旧 pwsh 游戏流程脚本已删除）—— ⛔ **2026-10-22 起本句失效**：WSL 测试台与注入脚本全部作废，工具链已回 **Windows 原生 pwsh 7**（见 `AGENTS.md`〈回滚裁决〉与 `docs/agent/AGENTS-full.md` 末尾〈WSL 口径失效对照〉）。
> **仍未完成**：GUI 状态机类用例（`CC-*` / `BOT-2P` 等）的语义级改写、`vision` 断言（9 条，WSL 下 BLOCKED）、
> 1.20.1/26.1.2/fabric 三线的 AutoJS 端到端验证。


> 登记对象：`scripts/test/cases/**/*.json` 里**尚未**迁到 AutoJS 通道的用例。
> 权威规格：`docs/features/autojs-test-harness-spec.md`（§2 目标态 / §8 欠账）；通道规则：`TESTING-RULES-OVERVIEW.md` §15；技术细节与逐行落点：`TESTING-SPEC.md` §7A。
> 数字时点：**2026-10-18**（AutoJS 通道落地当天，实测；复算命令见 §8）。

## 1. 现状：230 条用例里只有 1 条走 eval 通道

### 1.1 用例数（按线）

| 范围 | 文件数 | 明细 |
|---|---|---|
| 顶层 `scripts/test/cases/*.json`（排除运行时产物 `.mt_*`） | **218** | `-1.21.1.json` = **100**、`-1.20.1.json` = **65**、`-26.1.2.json` = **53**、其它后缀 = **0** |
| `scripts/test/cases/blocked-26.1.2/*.json` | **12** | 全部 `-26.1.2.json`（BOT-2P / FUHUO-CARD / LULU-SIGN / MAMUSHI-ACTIVE / MAMUSHI-COMBAT / MAMUSHI-DRAGON / MAMUSHI-GUARD / MAMUSHI-PASSIVE / MAMUSHI-REG / TERU-EXTRA-ATTACK / TERU-SIGN / ZHAO-SIGN） |
| **合计** | **230** | |
| 其中 `"channel": "autojs"` | **1** | `scripts/test/cases/AUTOJS-SMOKE-1.21.1.json`（样板用例） |
| **其中 legacy（`channel=kubejs`，含缺省）** | **229** | 顶层 217（含 1.21.1 的 99 条）+ `blocked-26.1.2` 12 条 |

### 1.2 断言面（按类型，解析 `asserts` 数组实测）

| 断言类型 | 条数 | 涉及文件数 | 备注 |
|---|---|---|---|
| `log` | **3314** | 220 | 字段面只有 `type/source/pattern/scope` —— **没有次数与阈值字段**，语义就是「正则命中即 PASS」 |
| `absent` | **945** | 210 | 负向断言（应不出现），判据本身不弱 |
| `crash` | **222** | 222 | 进程级事实，**必须**读日志（见 §3） |
| `kubejs` | **198** | 198 | KubeJS 脚本加载 / 报错事实 |
| `mixin` | **53** | 53 | 类变换期事实，**必须**读日志（见 §3） |
| `vision` | **9** | 7 | 截图 + 语义问题，暂无 JVM 等价物 |
| `state` | **3** | 1 | 全部在 `AUTOJS-SMOKE-1.21.1.json` 里 |
| （注）内联 `op:assert` 步骤 | 另有 **4** 条 `vision` | 2 | `WALLET-GUI-1.21.1.json` / `WALLET-GUI-26.1.2.json` 各 2 条；本表只解析 `asserts` 数组，**未计入** |
| `eval` | **0** | 0 | 尚无用例使用 |
| **合计** | **4744** | 230 | 其中 legacy 侧 **4741** 条（4744 − 样板用例的 3 条） |

### 1.3 与需求书 §0（现状）旧口径对账

需求书 §0 记的旧口径是「220 个用例 json（1.21.1 = 99 / 1.20.1 = 65 / 26.1.2 = 53 / 无后缀 3）+ `blocked-26.1.2/` 12」。今天实测顶层 218：`1.21.1` 100（= 旧 99 + 今日新增的 `AUTOJS-SMOKE-1.21.1.json`）、`1.20.1` 65、`26.1.2` 53、**无后缀 0**（旧口径的 3 条已不存在）。即 `220 − 3 + 1 = 218`，与实测一致；按 legacy 计 `1.21.1` 仍是 99 条，与旧口径对得上。

## 2. 欠账规模

- **229 / 230 条用例仍是 legacy 通道**（99.6%），全部依赖「探测注入 → 日志落盘 → 正则匹配」这条长链条。
- **4741 条 legacy 断言**里，`3314` 条是「命中即 PASS」的弱判据（`1.2` 已实测：`log` 断言没有次数/阈值字段）。
- 迁移**不是**全量替换：`222` 条 `crash`、`53` 条 `mixin`、`198` 条 `kubejs`、`9` 条 `vision`、部分 `absent` 属于「不得改成 eval」或「改了没意义」的类别（见 §3）。

## 3. 不迁移的例外（三类，外加一类自然延伸）

1. **`mixin` 断言（53 条 / 53 文件）** —— 事实发生在类变换期（`Attach error`、注入失败、`@Redirect` 目标缺失）。JVM 里没有可求值的句柄；而且这类失败常常直接崩在启动期，端点根本没起来。改成 eval 只会得到「端点不可用 ⇒ BLOCKED」或「恒真」。
2. **`crash` 断言（222 条 / 211 文件）** —— 崩溃 / 非正常退出 / 崩溃报告只存在于文件层；AutoJS 端点随进程一起死，崩溃后没有任何求值面。
3. **禁 AI 审计的四层保证** —— 审计对象是「产品里不存在 AI 调用路径」这类由**产品自己** `LOGGER` 打印的行。产品 `LOGGER` 行只能读日志（它不在任何可求值对象里），这条口径在需求书 §4 与 `TESTING-RULES-OVERVIEW.md` §15 第 3 条都写了。
4. **（自然延伸）`vision` 断言（9 条 / 7 文件）** —— 截图 + 语义判定，目前没有 JVM 等价物，保留原样。

除这四类之外，其余 legacy 断言都**应当**迁移。

## 4. 迁移优先级

| 档 | 对象 | 数量 | 理由与验收 |
|---|---|---|---|
| **P0** | 只靠 `log` 弱判据证明功能的用例（「某行出现过即 PASS」） | 3314 条断言 / 209 文件，集中在 1.21.1 | 判据最弱、收益最大：换成 `eval_js + state` 后能从「日志里出现过」升级到「状态确实是 X」。验收 = 新断言里**至少一条**是 `eval/state` 且 `expect` 有明确判据（不能是 `{ok:true}` 这种形式判据）。 |
| **P1** | 混用 `log` + `absent` 的行为用例 | 945 条 `absent` 所在文件 | 保留 `absent` 作旁证，把主判据换成 eval；迁移后可顺手删掉为「打印事实」而存在的探针步骤。 |
| **P2** | 1.20.1 / fabric-1.20.1 / 26.1.2 线 | 65 + 12（blocked）+ 53 | 先确认该线 AutoJS 可跑：neo 两线由 `Install-MtAutoJs` 投放；**1.20.1 / fabric 必须先按 `TESTING-SPEC.md` §7A.7 走 build.gradle 路线**，否则一律 BLOCKED（不是 FAIL）。 |
| **不移** | `crash` / `mixin` / `kubejs` / `vision` / 禁 AI 审计 | 见 §3 | 原样保留。 |

同一批迁移内的顺序建议：**读注册表/配方表的（最稳） → 读世界状态的 → 读客户端对象的（受渲染帧率影响）→ 需要输入驱动的**。

## 5. 迁移方式：日志断言 → eval/state 对照表

先在旧用例里找到「探针把事实打印成 `AP_*` 行」的那一步，然后把三步换成两步：**求值 → 比较**。

| legacy 写法（示意） | 迁移后 | 说明 |
|---|---|---|
| `{"type":"log","source":"latest.log","pattern":"AP_<探针>_<字段>=…"}` | 步骤 `{"op":"eval_js","side":"server","save_as":"x","code":"…return {…};"}` + 断言 `{"type":"state","from":"x","expect":{<字段>:<判据>}}` | 主判据从「日志里出现过」变成「值等于/在区间内」 |
| `source:"logs/kubejs/client.log"` 的客户端事实 | 步骤 `{"op":"eval_js","side":"client",…}`（如 `Minecraft.getInstance()` 读 `screen` / `level` / `getFps()`） | 客户端侧事实直接在渲染线程读，不再靠 client_scripts 落盘 |
| `{"type":"log",…}` 里等「玩家上线 / 世界已加载」 | `{"op":"await_state","side":"server","timeout_ms":60000,"interval_ms":500,"code":"…"}` | 把固定 `wait{ms}` 睡眠换成条件轮询 |
| `{"type":"log","pattern":"<注册 id 出现>"}` | `BuiltInRegistries.ITEM/MOB_EFFECT.containsKey(ResourceLocation.parse("…"))` → `expect:{…:true}` | 注册表存在性判据 |
| `{"type":"log","pattern":"<配方条数>"}` | 遍历 `server.getRecipeManager().getRecipes()` 数 `id.getPath()` 后缀 → `expect:{chips:{gte:0}}` | 计数类判据改用 `min/max/gte/lte`，不再是「出现了数字」 |
| `{"type":"log","pattern":"<集合内容>"}` | 返回数组 → `expect:{list:{set:[…]}}`（顺序无关） | 集合判定 |
| `{"type":"absent","pattern":"…"}` | 保留（负向断言），必要时补一条 `eval` 计数器 `== 0` | 双保险 |
| `{"op":"kubejs_reload"}` | **删除**：`channel=autojs` 下该原语已退役（`mt_case.ps1` 的 `Test-MtCaseValid` 退役原语分支；行号会漂，按函数名检索） | 改用 `eval_js` / `await_state` |

写完后必须做的三件事：

1. 顶层显式加 `"channel": "autojs"`（缺省是 `kubejs`，不写等于没迁）。
2. `eval` 断言必须给 `from` 或内联 `code`，且 `expect` 不能省（`eval` 缺 `expect` ⇒ ERROR 2；只有 `state` 允许「无判据」只打印证据）。
3. 跑 `pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case <用例绝对路径>`（**位置参数 `validate`**，没有 `--validate`；封闭原语表会立刻拦住非法字段）。

## 6. 单条用例迁移 checklist

1. 读原用例，找出每条 `log` 断言背后「原本想证明什么」（探针打印的那行对应的真实状态）。
2. 写 `eval_js` 求值片段：`side` 按「改状态/权威数据 = server、界面/输入/FPS = client」定；用 `save_as` 命名存档。
3. 用 `state` 断言 `from` 引用存档，写明确的 `expect`（标量 / 区间 / 集合 / 嵌套）。
4. 删掉只为打印而存在的探针步骤与 `kubejs_reload`；保留 `crash/mixin/kubejs` 断言。
5. `mt_case.ps1 validate` 过结构校验。
6. 实机跑：端点没起 ⇒ BLOCKED（**不要**当功能缺陷去改代码）；判据不符 ⇒ FAIL（这才是要看的东西）。
7. 在本文 §1 表格里更新数字（本文是完成度的唯一事实源）。

## 7. 完成度怎么算

`完成度 = channel=autojs 的用例数 / 230`。今天是 `1 / 230`（`AUTOJS-SMOKE-1.21.1` 一条）。**不要**用「断言条数」当分母 —— 一条用例里可以有很多断言，跨用例混算会让进度看起来虚高。

## 8. 复算命令（只读，可重跑）

~~~powershell
$d = "F:\MCProject\astral_dice_multiloader-next\scripts\test\cases"
# 按线统计顶层用例数
Get-ChildItem $d -File -Filter *.json | Where-Object { $_.Name -notlike ".mt_*" } |
  Group-Object { switch -Wildcard ($_.Name) {
    "*-1.21.1.json" { "1.21.1" }; "*-1.20.1.json" { "1.20.1" }; "*-26.1.2.json" { "26.1.2" }; default { "other" } } } |
  Select-Object Name, Count
# blocked 子目录
(Get-ChildItem "$d\blocked-26.1.2" -File -Filter *.json).Count
# 已迁到 eval 通道的用例数
(Select-String -Path "$d\*.json","$d\blocked-26.1.2\*.json" -Pattern '"channel"\s*:\s*"autojs"' -List |
  Where-Object { $_.Filename -notlike '.mt_*' }).Count   # 必须排除运行时产物 .mt_autojs.json / .mt_snapshot.json
~~~

~~~powershell
# 断言面（按类型，解析 asserts 数组）
python -c "import glob,json,os,collections;c=collections.Counter();[c.update(a['type'] for a in json.load(open(f,encoding='utf-8'))['asserts']) for f in glob.glob('scripts/test/cases/*.json')+glob.glob('scripts/test/cases/blocked-26.1.2/*.json') if not os.path.basename(f).startswith('.mt_')];print(c.most_common())"   # 必须 encoding='utf-8'（本机默认 GBK 会崩）
~~~
