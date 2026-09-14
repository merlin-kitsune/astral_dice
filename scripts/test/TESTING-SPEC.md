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

---

## 3. 唯一入口与阶段

```powershell
pwsh -NoProfile -File scripts/test/mt.ps1                      # 全流程（P→B→E→L→C→R，双版本）
pwsh -NoProfile -File scripts/test/mt.ps1 --phase <p> --version <v>
```

| 阶段 | 实现 | 职责 | 终态标记 |
|---|---|---|---|
| **P** 前置 | `mt_preflight.ps1` | 分支 / 输入法 / 遗留进程 / MCP 二进制 / 兼容栈 / 可写性；启动前兜底清理 | `MT_PREFLIGHT: OK/FAIL`（失败退出码 10） |
| **B** 构建 | `mt_build.ps1` | `gradlew :<子项目>:build`，60s 看门狗 + `BUILD SUCCESSFUL` 识别 + 产物 jar 校验 + 重试 | `MT_BUILD: OK/FAIL` |
| **E** 环境 | `mt_env.ps1` | `mods`（装兼容模组）/ `world`（重建测试世界，含原生 NBT 改写）/ `kill` | `MT_WORLD: OK/BLOCKED`（退出码 11） |
| **L** 启动 | `mt_launch.ps1` | 启动 `runClient`、轮询就绪日志、兼容栈信号 | `MT_LAUNCH: OK/FAIL` |
| **C** 条目 | `mt_case.ps1` | 顺序执行 `cases/*.json`：注入命令/按键 → 等待 → 断言；每条结果**自动**写入报告状态 | 每条 `PASS/FAIL` |
| **R** 报告 | `mt_report.ps1` | 收集证据（日志 / 崩溃 / 截图）→ 单版本 `report.md` → 双版本 `SUMMARY.md` | `MT_REPORT: OK/FAIL`（未全绿退出码 1） |

**辅助脚本**：`mt_assert.ps1`（断言引擎）、`mt_inject.ps1`（键鼠注入）、`mt_ime.ps1`（输入法）、`mt_capture.ps1`（截图世代）、`mt_cleanup.ps1`（退出清理唯一实现）、`mt_stop.ps1`（薄封装）、`mt_gen_case.ps1`（条目生成器）。
**共享模块**：`lib/Mt.{Conf,Paths,Phase,Proc,Win32}.psm1`。
**开发辅助**：`scripts/devtools/Start-MtDetached.ps1`（脱离式执行）、`Test-MtSyntax.ps1`（脚本语法门）、`Compare-MtOutput.ps1`（迁移期逐字节比对）。

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

**安装**：把 `scripts/test/resources/kubejs/<版本>/server_scripts/*.js` 复制到 `run/<版本>/kubejs/server_scripts/`。

| 探针 | 1.21.1 | 1.20.1 | 作用 |
|---|---|---|---|
| `astral_bugfix_probe.js` | ✅ | ✅ | 回归套件主探针：命令（`diag` / `equipslot` / `railguncd` / `railgunfriendly`·`railgunfriendlyread`·`railgunfriendlyend`（双版本）、`truedmg`/`railtruedmg`·`railtruedmgread`（双版本：真伤穿甲取证）+ `glmcheck`（仅 1.20.1）+ 各用例专用命令）、状态读数 `AP_*` |
| `astral_dice_curios_check.js` | ✅ | ✅ | Curios 槽位 / 装备状态观测 |
| `astral_dice_target_select_check.js` | ✅ | ✅ | 待命等待器（占星师 / 秘密侦探 / 枪匠）观测 |
| `astral_dice_curio_watch.js` | ✅ | — | 立牌槽位变化观测（1.21.1 专用） |
| `astral_dice_heal_test.js` | ✅ | — | 治愈点读数（1.21.1 专用） |
| `astral_functional_test.js` | — | ✅ | 1.20.1 历史功能验证脚本（手工流程遗留，保留备用） |

> ⚠️ 两版本探针集合**并非完全镜像**（上表为实测现状）。改动探针后请同时核对
> `scripts/test/resources/kubejs/<版本>/server_scripts/`（入库副本）与 `run/<版本>/kubejs/server_scripts/`（运行副本）。

**两版本差异**（除下列行外逐字相同）：`opt.resolve().get()`（1.21.1）↔ `opt.get()`（1.20.1）；`ResourceLocation.parse(...)` ↔ `new ResourceLocation(...)`；`ModEffects.X` ↔ `ModEffects.X.get()`。

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
**断言 type**：`log`（正/反向正则，`scope: whole` = 对整文件求值，用于启动期行）、`absent`（整文件不得出现）、`crash`（无崩溃报告）、`kubejs`（server.log 0 error）、`mixin`（Mixin 应用行存在）、`vision`（截图视觉判定）。
**失败取证**：`on_fail=keep_game_running` 会落 `.mt_keep_alive`；此时**退出清理不杀客户端也不停守护**（保留现场），取证后执行 `mt.ps1 --phase stop --force`。

---

## 8. 回归套件清单（1.2.0）

| 用例（双版本各一份） | 目的 | 规模 | 关键断言 |
|---|---|---|---|
| `DIRECTIONAL-BLAST-AOE` | 定向爆破 AOE 口径守卫：只吃「自身 5 点 + 效果牌伤害加成」，其它伤害效果牌不得计入 | 3 步 / 11 断言 | `AP_B1_AOE_FORMULA:5`（装齐激光/板砖/轨道炮后仍为 5）、`AP_B2_AOE_FORMULA:6`（再装书签 → +1）、`BONUS_OTHER_CARDS:0`、`absent`、`kubejs`、`crash` |
| `EMERALD-DICE-TRADE` | 绿宝石骰子：客户端报价必须是星币（本体仍是绿宝石）+ 成交后立即重发报价刷新经验 | 6 步 / 13 断言 | `AP_E1_SERVER_A:minecraft:emerald`（本体不变）、`AP_E1_CLIENT_A:astral_dice:star_coin`（客户端载荷）、`AP_E1_RESEND:ok`、`AP_E1_OPEN:ok`、`absent`、`kubejs`、`crash`；交易界面另出截图供人工核对 |
| `SMOKE-TOOLCHAIN` | 工具链自检（不依赖游戏） | 2 步 / 2 断言 | 反向断言 + `mixin` 通道可用 |
| `ANVIL-STAR-UPGRADE` | 铁砧升星端到端扣费：★0→★1/★1→★2/★2→★3 实扣 15/20/25 星币（物品栏实测） + `weapon_enhancement` 星级 +1；袋装星币 / 数量不足一律不产出 | 15 步 / 41 断言 | `AP_S1_FEE:15:0->1`、`AP_S2_FEE:20:1->2`、`AP_S3_FEE:25:2->3`、`_COINS_BEFORE:25/30/35`、`_RIGHT_LEFT:astral_dice:star_coin:3`、`_INV_AFTER_CLOSE:10`、`_STAR`、`_PUTBACK:ok:…`、`_TAKE:clicked:30:20`、`AP_Z1_BAG_RESULT:empty`、`AP_Z1_FEW_TOTAL:9`、`absent`、`kubejs`、`crash` |
| `KOMACHI-EXTRA-PLAY` | 忍者立牌主动「忍术连击」：仅当前周期 +1（正常释放 / 本周期已生效时不再释放且不进冷却 / 出牌上限不得超过封顶 9 / 周期归零后附件被清除） | 11 步 / 24 断言 | `AP_K1_BEFORE:max=1:extra=0`、`AP_K1_AFTER:max=2:extra=1:cd=1`、`AP_K1_DELTA:max=+1:…:cd=started`、`AP_K1_RELEASED:1`、`AP_K2_REJECTED:1`（冷却结束时刻逐字未变 `cd_same=1`/`cd_future=0`）、`AP_K3_CONST:9:1`、`AP_K3_FILL:7`、`AP_K3_VERDICT:…`（封顶分支与未封顶正对照两形态一条正则锁死）、`AP_K5_READ:extra=0:count=0:cd=0`、`AP_K5_CLEARED:1`、`absent`、`kubejs`、`crash` |
| `NANCY-LU-PEARL-IMMUNE` | 骇客「网络防火墙」：末影珍珠传送摔落伤害在伤害判定最前置处被取消（生命值不变 + `hurtTime`/`invulnerableTime`/`hurtMarked` 全未被写入），并带同构造对照相位 | 9 步 / 21 断言 | `AP_P1_FIELDS:ok`、`AP_P1_API:(hurt\|causeFallDamage)`、`AP_P1_CTRL_OK:1`（对照相位必须真受伤）、`AP_P1_IMM:…->…`（前后快照逐字一致）、`AP_P1_IMM_OK:1`、`AP_P2_PEARL:window=…:tel=1:drop=0:hurt=0:invul=0:marked=false`、`AP_P3_PEARL:window=0:tel=1:drop=5:hurt>0`、`absent`、`kubejs`、`crash` |
| `NANCY-LU-CLOAK` | 骇客主动「远程侵入」：完全隐身的机器可判定一半——隐身实例 `visible=false`（无粒子）、`nancy_lu_hidden_until>0`、到期与攻击两条解除路径都能清掉附件与效果 | 14 步 / 19 断言 | `AP_N1_STATE:hidden_until=…:vis=0:…:win=1`、`AP_N1_HIDDEN:1:1`（服务端 `isHidden` + 客户端 `isHiddenClient`）、`AP_N3_CLEARED:1`（到期路径）、`AP_N6_STATE:hidden_until=0:effect=0:vis=-1:…:hack=1:bonus=…`（攻击路径）、`absent`、`kubejs`、`crash`；**视觉半自动**（1 张截图，无 `vision` 断言） |
| `RAILGUN-AOE-SCOPE` | 电磁炮雷击命中范围取证：单个可命中敌对目标只生成 **1 道**原版雷击，而原版雷击对落点箱内**所有**存活实体生效——实测同时打中攻击者本人、中立生物与已驯服的宠物，证明该链路**没有任何阵营/所有权过滤** | 8 步 / 13 断言 | `AP_RG_TAME:tame=1:owner=1:src=`（狼已驯服并绑定主人，`src` 为命中的 UUID 取值器）、`AP_RG_BEFORE:…:charge=6:mode=forced_survival:weather=clear`、`AP_RG_CHARGE_AT_ATTACK:6`（充能**延迟到雷击落下**才结算）、`AP_RG_AFTER:…bolt_delta=1`（只 1 道雷击）、`AP_RG_AFTER:…:charge=0`、`AP_RG_VERDICT:enemy=1`（敌方必被命中）、`AP_RG_RESTORE:creative`、`absent`、`kubejs`、`crash` |
| `LOOT-MODIFIER` | 1.20.1 星盘战利品修饰符：Forge 47.4.10 **没有**内置 `add_table`，改用自带的 `astral_dice:add_table` 后 13 条修饰符必须全部解码成功并能真正注入星盘 | 5 步 / 6 断言 | `AP_GLM_SERIALIZERS:.*astral_dice:add_table`、`AP_GLM_ROLL:rolls=400:plates=([5-9]\|[1-9][0-9]+):items=[1-9][0-9]*:err=none`（实滚 400 次 `minecraft:chests/simple_dungeon`，子表 5% 出星盘）、`absent` `Could not decode GlobalLootModifier`、`kubejs`、`crash`（仅 1.20.1 一份） |
| `EFFECT-DECAY-FLICKER` |**视觉半自动**（1 张截图，不做机器判定） |

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
> `/astralprobe komachicast|komachirepeat|komachicap|komachicycle|komachiread`、
> `nancycloak|nancyexpire|nancystate|nancyfall`、`nancypearl|nancypearlctrl|nancypearlclose`、
> `decayflash|decayclear`（见 §6 探针安装步骤；**探针改动必须冷启动**：`/kubejs reload server-scripts`
> 不会重绑已注册命令的 lambda）。
> **「按主动」的取证口径**：三例统一由 `BaseSignItem.performSkillForCurio` 触发 —— 与客户端按键经网络包
> （1.21.1 `SignActivatePayload` / 1.20.1 `ModNetwork`）的服务端处理是**同一入口**，不是直接改附件。
> **「不进入冷却」的取证口径**：先把主动冷却结束时刻置为「已过期但非 0」（既越过 `performSkill` 的冷却分支、
> 又留下可比的基线），按主动后该字段必须**逐字未变**（`cd_same=1` / `cd_future=0`）；若被重新写成未来时刻，
> 即说明冷却被起算。
> **封顶分支的现状（如实标注，勿当成已验证）**：忍者主动「出牌上限已达封顶 9」这一分支在当前内容下
> **不可达** —— 满配 extra = 固定来源 2（大背包 / 忍术飞镖）+ 临时来源 4（活体书页 / 命运的指引 /
> 可口糖果 / 探天卫星）= 6 → `getMaxAllowed()` = 7。`komachicap` 会尝试注册两个「常态关闭」的
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
> ⚠️ **本批四条用例尚未在真实游戏中运行**（新增探针命令需冷启动一次），首次运行结果请回填本文件。

> **历史说明（2026-09-14）**：`BUG1-BLESSING-HUD`、`BUG2-EMPOWER-DECAY`、`BUG3-MIXIN-BADGE`、
> `BUG4-OCULUS-LIGHTNING`、`BUG5-RAILGUN-DELAY-CD` 五条用例**及其全部判定内容**（含仅服务它们的
> 13 条探针命令 `status` / `setup` / `railtest` / `cdguard` / `charge` / `empower` / `empowerclear` /
> `blessingclear` / `decayinterval` / `decaydue` / `boltvis` / `watch` / `hud`）已按用户裁决移除，
> **不再是在用用例**，后续运行不再产出它们的结论；`attack` 因仍被 `NANCY-LU-CLOAK` 的
> 「攻击解除隐身」相位复用而**保留**。它们的历史运行报告仍保留在 `scripts/test/reports/2026*`
> 作为证据存档（不再复跑）。

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

---

## 11. 清理规范与覆盖缺口

**判定依据（可删 / 必须留）**

| 类别 | 判定 | 处理 |
|---|---|---|
| 已删除工具链的编译缓存（`__pycache__` 等） | 对应源文件已不存在 | 可删 |
| 空目录 / IDE 生成物（`.vscode/launch.json`、`build/`） | 不参与发布、可再生成 | 可删/忽略 |
| 历史运行报告 `reports/<旧运行id>/` | 证据价值随时间衰减 | 保留最近一次 + 模板，其余可删 |
| `temp/` 会话期探针与验证脚本 | 一次性 | 可删（`legacy_scripts_*.zip` 归档除外） |
| **KubeJS 探针、测试世界种子包、回归条目 JSON** | **用例可复现性依赖** | **必须入库，禁止删除** |
| `run/<版本>/mods`、`run/<版本>/kubejs` | 下一次运行的现成环境 | 保留 |

**覆盖缺口（如实标注，勿当成已验证）**

- 13 骰子 / 17 立牌 / 60 筹码的**逐特性玩法数值**没有 per-feature 自动化用例，目前只有静态守门 + 历史手测；
- 多人联机 / 服务端专用路径、存档跨版本兼容、性能表现均无覆盖。

---

## 附录 A：工具链与发布工程变更记录（自 CHANGELOG 移出）

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
