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

**测试前清场（2026-09-15 起强制）**：每次冷启动**进入世界后、跑任何条目前**先清场一次 —— `/kill @e[type=!player]`（或 `/kill @e[type=!player,distance=..128]`），或 `/difficulty peaceful` → 等 ≥1s → `/difficulty easy`。理由与禁止事项见 `AGENTS.md`「测试前清场」；本条直接对应 §8.2-1（1.20.1 常驻蜘蛛污染 `self`）与 §10-18。⚠️ 1.21.1 的命令在 tick 末才生效，清场后要 `wait ≥500ms` 再摆靶；**禁止**在用例两次 read 之间清场。

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
| `astral_bugfix_probe.js` | ✅ | ✅ | 回归套件主探针：命令（`diag` / `equipslot` / `railguncd` / `railgunfriendly`·`railgunfriendlyread`·`railgunfriendlyend`（双版本）、`truedmg`/`railtruedmg`·`railtruedmgread`（双版本：真伤穿甲取证）、`fensplash`/`fensplashhit`/`fensplashread`（双版本：大当家溅射三段式——装备与摆靶、命中、读差值；判据=4.5 格内命中(旧 3 格打不到)、8 格外 0 伤害、重甲靶与无甲靶掉血相同(真伤)、溅射÷近战≈0.88 或被 5 点下限托住；靶子一律非亡灵、非苦力怕）+ `glmcheck`（仅 1.20.1）+ 各用例专用命令）、状态读数 `AP_*` |
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
14. **`.mt_snapshot.json` 的残留偏移会静默漏行**：`log`/`absent` 断言默认只扫「自快照以来的增量」，偏移陈旧（如 1.20.1 残留 `@96917B`）会把早期行判为「不存在」。每条用例前先跑 `pwsh -NoProfile -File scripts/test/mt_assert.ps1 snapshot --version <版本>`。
15. **1.20.1 探针里 `ModEffects.X` 必须 `.get()`**：1.20.1 是 `RegistryObject`、1.21.1 是 `Holder`；漏 `.get()` 会在运行期抛 `Could not create ID from 'RegistryObject…'` 并**中断该条探针命令**（实测 1.20.1 `fensplash` 第 2265 行），表现为该相位所有 `AP_` 行整段消失 —— 必须与前一条「命令未执行」的排查区分开。
16. **`HealingManager#updateEffect` 的骰神赐福分支**（2026-09-15 实测，`EFFECT-DECAY-FLICKER` 首次运行失败的真正根因）：治愈图标时长在**赐福在场时等于赐福剩余时长**（`HealingManager` 的 `blessing != null` 分支），与治愈计时器无关；因此只要玩家身上残留 `dice_blessing`（前一用例触发过赐福即可），`endsWithin(200)` 的原版闪烁窗口**不可能**成立，`_WINDOW:0` 与本模组修复无关。构造该窗口的探针命令必须**先 `removeEffect(DICE_BLESSING)`** 再 `setHealingTimerEnd(now+200)` + `add`；仅靠 `/effect clear @s` 或 `decayclear`（只清治愈点数/计时器/三类效果）**不够**。判定「赐福是否在场」可读 `AP_<tag>_SETUP:…:blessed_before=`。
17. **探针收尾命令 `railgunfriendlyend` 的注入偶发丢失**（2026-09-15 两次实测：`RAILGUN-OVERRIDE-CLASS-1.21.1` 首轮缺 `AP_OC_RESTORE:creative`、`HOSTILE-TARGET-NEUTRAL-1.20.1` 缺 `AP_HN_RESTORE:creative`）：表现是**该轮读数全部正常、唯独收尾读数一行都没有**，用例因 `RESTORE` 断言 FAIL，极易被误当成产品问题。`doRailgunFriendlyEnd` 是幂等的（`rgfState == null` 时仍会清同类残留、回创造模式、清充能并输出 `RESTORE`/`DONE`），故**8 个探针用例统一改为连续注入两次 `railgunfriendlyend`（中间 `wait 500ms`）**。同族：读命令同样建议注入两次（冷启动后前若干次注入偶发丢失，多个用例 note 已记录），以及在**同一会话内重跑同一用例会误伤 `absent` 断言**（快照按 launch 建立，粒度是会话不是用例）—— 重跑前先 `stop` → `launch` 重置快照。
18. **`RAILGUN-AOE-SCOPE` 的断言集缺「防误伤 / 范围」项（2026-09-15 复核断言集时发现；同日用户裁决「方案①」，已实施并待冷启动复跑确认）**：该用例名叫「电磁炮雷击命中范围取证」，但 13 条断言里只有 `AP_RG_VERDICT:.*:enemy=1:` + `absent …:enemy=0:` 覆盖「敌对标确实被劈」，**没有** `friendly`/`turtle`/`villager` 为 0 的断言，也没有 `SCOPE_OK:1`。后果很实在：**设计万一失效（雷击波及落点箱内全部实体），本用例照样 PASS** —— 实测出现过 `self=2→6:friendly=12:turtle=12:villager=12:SCOPE_OK:0` 而本用例断言全绿（当时把它当成「产品疑点」排查，其实是断言没覆盖）。补强方案（改完需一次冷启动复跑）：① 补正断言 `AP_RG_AFTER:.*:friendly=0:turtle=0:villager=0:valive=1:talive=1`（`friendly`→`talive` 在 AFTER 行里本就相邻，无需插 `.*`）、补 `AP_RG_VERDICT:.*:neutral=1:`，并照抄 `RAILGUN-OVERRIDE-CLASS` 的反断言写法；② 在 ① 之上再给 1.21.1 追加 `AP_RG_SCOPE_OK:1` 与反断言 `AP_RG_VERDICT:.*:self=[1-9]`。**1.20.1 不建议直接断言 `self=0` / `SCOPE_OK:1`**：`self` 是**施放者 HP 的原始差值**（`dealt(st.php, php)`，不区分伤害来源），测试世界里常驻的敌对生物（实测蜘蛛近战使 `php −6.0`，同期 `bolt_delta` 未变）会让它偶发非 0 —— 那是环境脏，不是产品白名单问题。若要让两版都能断言 `self`，须先三选一：把 `spider` 加进 setup 的**既有**敌对生物清场（⚠️ 1.21.1 命令延迟到 tick 末生效，清场必须排在自己摆靶之前的**不同 tick**，否则会清掉自己刚摆的靶，旧伤见 `6479603`）、或给探针自持靶打 tag 后用 `tag=!…` 反选清场、或把该用例挪到无怪世界运行。
   - ✅ **2026-09-15 用户裁决「方案①」并已实施**：1.20.1 断言 13→15、1.21.1 断言 13→16 —— 两版新增正断言 `AP_RG_AFTER:.*:friendly=0:turtle=0:villager=0:valive=1:talive=1` 与 `AP_RG_VERDICT:.*:neutral=1:`，反断言扩为 `…:enemy=0:|…:friendly=[1-9]|…:turtle=[1-9]|…:villager=[1-9]|…:valive=0|…:talive=0`；1.21.1 另加 `AP_RG_SCOPE_OK:1` + 反断言 `AP_RG_SCOPE_OK:0|AP_RG_VERDICT:.*:self=[1-9]`，1.20.1 侧则按上面的理由**刻意不**断言 `self`/`SCOPE_OK`（用例 note 已写明「施放者不自伤」由 1.21.1 同名用例承担）。**离线预检（把新正则喂给上一轮绿色批次的原始行）**：新正断言全部命中、新反断言 0 命中；再把那轮「箱内全体被劈」的异常读数喂进去 → 正断言不命中、反断言命中 ⇒ 两个方向都符合预期。改断言属测试资产变更，**仍需一次冷启动复跑**才算了结 —— **复跑确认（同 HEAD `09a34e5`，12:39 / 12:41 双版本各冷启动一次）**：1.20.1 15/15 PASS、1.21.1 16/16 PASS（0 FAIL / 0 ERROR），新正断言全部命中、新反断言 0 命中；读数 `self=0`、`friendly`/`turtle`/`villager` 全 0、`SCOPE_OK:1`、`bolt_delta` 1.20.1=`3`（两次 read 均 3）/1.21.1=`2`（两次 read 均 2）。⚠️ 1.20.1 本轮 `self=0` 只说明**该轮没有生物来打玩家**，不等于该版 `self` 已可断言 —— §8.2-1 那只常驻蜘蛛的间歇性并未消除。

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
