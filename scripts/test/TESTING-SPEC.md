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
| 可选 MCP | `computer-control-mcp`（窗口激活 / OCR / 截图的降级通道），路径写在 `mt.conf` |
| 机器本地配置 | `scripts/test/mt.conf`（**不入库**）。缺失时回落到内置默认值；模板见 `mt.conf.example` |

---

## 3. 唯一入口与阶段

```powershell
pwsh -NoProfile -File scripts/test/mt.ps1                      # 全流程（P→B→E→L→C→R，双版本）
pwsh -NoProfile -File scripts/test/mt.ps1 --phase <p> --version <v>
```

| 阶段 | 实现 | 职责 | 终态标记 |
|---|---|---|---|
| **P** 前置 | `mt_preflight.ps1` | 分支 / 输入法 / 遗留进程 / MCP / 兼容栈 / 可写性；启动前兜底清理 | `MT_PREFLIGHT: OK/FAIL`（失败退出码 10） |
| **B** 构建 | `mt_build.ps1` | `gradlew :<子项目>:build`，60s 看门狗 + `BUILD SUCCESSFUL` 识别 + 产物 jar 校验 + 重试 | `MT_BUILD: OK/FAIL` |
| **E** 环境 | `mt_env.ps1` | `mods`（装兼容模组）/ `world`（重建测试世界，含原生 NBT 改写）/ `kill` | `MT_WORLD: OK/BLOCKED`（退出码 11） |
| **L** 启动 | `mt_launch.ps1` | 启动 `runClient`、轮询就绪日志、兼容栈信号、收尾 `/publish 25565` | `MT_LAUNCH: OK/FAIL` |
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

回归套件依赖探针提供的服务端读数（如 `AP_B5_DELAY_MEASURED`、`AP_B2_EMPOWER_VIS`）。

**安装**：把 `scripts/test/resources/kubejs/<版本>/server_scripts/*.js` 复制到 `run/<版本>/kubejs/server_scripts/`。

| 探针 | 1.21.1 | 1.20.1 | 作用 |
|---|---|---|---|
| `astral_bugfix_probe.js` | ✅ | ✅ | 回归套件主探针：命令（`cdguard` 等）、状态读数 `AP_*`、雷击延迟量测窗口 |
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
  "case_id": "BUG5-RAILGUN-DELAY-CD-1.21.1",
  "title": "电磁炮雷击应延迟 1 秒并进入 1:00 冷却（冷却期内不得再次触发）",
  "version": "1.21.1",
  "target": { "type": "bugfix", "ref": "bugfix/bug5" },
  "steps": [ { "op": "inject_command", "text": "/astral cdguard" }, { "op": "wait", "ms": 2000 }, { "op": "screenshot", "crop": true } ],
  "asserts": [ { "type": "log", "pattern": "AP_B5_DELAY_MEASURED:2[01]", "scope": "whole" } ],
  "evidence": [],
  "on_fail": "keep_game_running"
}
```

**步骤 op**：`inject_command`、`inject_key`、`note`、`wait`、`screenshot`、`kubejs_reload`。
**断言 type**：`log`（正/反向正则，`scope: whole` = 对整文件求值，用于启动期行）、`absent`（整文件不得出现）、`crash`（无崩溃报告）、`kubejs`（server.log 0 error）、`mixin`（Mixin 应用行存在）、`mcp`、`vision`（截图视觉判定）。
**失败取证**：`on_fail=keep_game_running` 会落 `.mt_keep_alive`；此时**退出清理不杀客户端也不停守护**（保留现场），取证后执行 `mt.ps1 --phase stop --force`。

---

## 8. 回归套件清单（1.2.0）

| 用例（双版本各一份） | 目的 | 规模 | 关键断言 |
|---|---|---|---|
| `BUG1-BLESSING-HUD` | 骰神赐福触发 + 伤害跳字 HUD 显示 | 16 步 / 9 断言 | 赐福日志、跳字日志、`absent` 反向、`vision` 截图、`kubejs`、`crash` |
| `BUG2-EMPOWER-DECAY` | 赋能每 0:30 递减 1 层（含自然递减正例与时长读数） | 17 步 / 12 断言 | `AP_B2_EMPOWER_DUR`、层数/可见性读数、`absent`、`kubejs`、`crash` |
| `BUG3-MIXIN-BADGE` | 物品栏效果角标 Mixin 应被加载（阿拉伯数字角标） | 8 步 / 7 断言 | `mixin` 应用行、角标日志、`absent`、`kubejs`、`crash` |
| `BUG4-OCULUS-LIGHTNING` | Oculus + 光影启用时雷击可正常渲染 | 9 步 / 9 断言 | 雷击实体日志、`vision` 截图比对、`mixin`、`crash` |
| `BUG5-RAILGUN-DELAY-CD` | 电磁炮雷击延迟 1 秒 + 1:00 冷却内不得再触发 | 25 步 / 26 断言 | 真实延迟量测 `DELAY_MEASURED:2[01]`、`cdguard` 冷却守卫（充能不消耗/不登记/不重置剩余）、两段负向观测窗、末端复查 |
| `SMOKE-TOOLCHAIN` | 工具链自检（不依赖游戏） | 2 步 / 2 断言 | 反向断言 + `mixin` 通道可用 |

**最近一次全量结果**：运行 `20260912-200747` —— 双版本 **BUG1–BUG5 全部 PASS**；运行 `20260912-215424` —— 双版本 **BUG2 + BUG5 PASS**（充能/赋能禁用粒子改动后的回归）。

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

- pwsh 工具链 9 项修复：env 种子包按版本判定；`Find-MtMinecraftWindow` 增加「标题含版本号」回退（DevLaunch 用短命令行 + args 文件，原判据永远失败）；断言新增 `scope: whole` 与 screenshot `crop`；BUG2 增加自然递减正例；探针增补 `AP_<tag>_EMPOWER_DUR`；新增 `Start-MtDetached.ps1`；`mt_env world` 强制写世界规则（退出码 11）；`mt_case` 自动写报告状态；BUG5 扩展为完整电磁炮验证（真实延迟量测 + 冷却守卫）。
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
