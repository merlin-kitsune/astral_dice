# 旧 shell 测试资产退役登记（multi-dev-next）
<!-- 历史记录：文中旧 shell 命令已于 2026-11-09 全部废弃，现用 bash 测试台（scripts/test/win/mt.sh）与 windows MCP，仅作溯源。 -->

状态：**在办**（替代物已陆续交付，物理删除在全部验收通过后一次性执行）。
口径来源：用户 2026-11-02 指示「测试资产引用主线所用的基于 bash 的测试脚本，原 旧 shell 测试资产全部废弃（删除处理）」+「先从主线获取目前已用的 bash 测试资产，再决定哪些资产需要补充」。

## 0 为什么删得掉：旧 shell 版本来就是 python/bash 原件的移植

`git show --stat 92fbeaf` = 「chore(scripts)!: W9 删除 28 个 bash/python 原件（工具链收敛为纯 旧 shell）」。
⇒ 这一轮是**反向移植**：`git show 92fbeaf^:<path>` 取回原件，按现状（`scripts/test/lib/mt_paths.py` 等新模块的签名、各线版本表、机器行契约）适配后落盘，再删 旧脚本。

## 1 不可动摇的契约（替代物必须逐字保持）

| 项 | 值 |
|---|---|
| 退出码 | PASS 0 / FAIL 1 / ERROR 2 / PREFLIGHT 10 / BLOCKED 11 / TIMEOUT 12（130=中断，由 mt.sh 的 trap 出）|
| 用例机器行 | `MT_CASE_RESULT: <case_id> = PASS|FAIL|ERROR|BLOCKED|TIMEOUT`（详情走 `MT_CASE_DETAIL`）|
| 阶段机器行 | `===== MT_PHASE: <name> =====` / `MT_<NAME>: OK|FAIL|BLOCKED|ERROR (<elapsed>) — <detail>` / `MT_INFO:` / `MT_WARN:` / 裸 `MT_ERROR:` |
| 能力缺失 | 一律 BLOCKED(11) 或 ERROR(2)，**禁止**静默 0、禁止 SKIP 当通过 |
| 用例 JSON | `scripts/test/cases/*.json` 297 个，`python scripts/test/lib/mt_case.py validate --cases-dir scripts/test/cases` ⇒ `errors=0` |

## 2 逐组处置表（62 个 git 跟踪的 旧脚本/旧脚本模块）

### 2.1 测试台编排与核心库（21 个）→ bash + python 全替代

| 已退役旧件 | 行 | 替代物 | 状态 |
|---|---|---|---|
| 旧件「mt」 | 998 | scripts/test/win/mt.sh（编排层）+ win/lib/phases.sh（阶段层） | 已替代 |
| 旧件「mt_case」 | 3332 | scripts/test/lib/mt_case.py（888 行，validate/audit/run + --channel/--allow-auto）+ mt_autojs.py + mt_channel.py + mt_paths.py | 已替代（vision/screenshot/inject_key/inject_mouse 记 BLOCKED） |
| 旧件「mt_assert」 | 1537 | scripts/test/mt_assert.py（snapshot/log/crash/kubejs，增量窗口语义） | 在办（子代理 6ad9d80f） |
| 旧件「mt_env」 | 2623 | scripts/test/win/env.sh（只读体检）+ scripts/test/mt_env.py（世界/环境准备） | 部分替代（体检已替代；世界准备在办 5547a545） |
| 旧件「mt_launch」 | 1226 | scripts/test/win/launch.sh（autojs 通道） | 已替代；`--channel native` **能力损失**→ BLOCKED(11) |
| 旧件「mt_stop」 | 111 | scripts/test/win/stop.sh | 已替代 |
| 旧件「mt_preflight」 | 763 | scripts/test/mt_preflight.py（`MT_PREFLIGHT: OK — 21 项全部满足`） | 已替代 |
| 旧件「mt_report」 | 1016 | scripts/test/mt_report.py（mark/summary/collect） | 已替代 |
| 旧件「mt_build」 | 193 | scripts/test/win/lib/phases.sh 的 build 阶段（`cmd //c gradlew.bat :<sub>:classes`） | 已替代 |
| 旧件「mt_inject」 | 950 | scripts/test/mt_inject.py（ctypes user32 注入，原件的复原） | 在办（6ad9d80f） |
| 旧件「mt_cleanup」 | 470 | scripts/test/mt_cleanup.py + scripts/test/win/stop.sh --purge-saves | 在办（5547a545） |
| 旧件「mt_capture」 | 482 | scripts/test/mt_capture.py | 在办（5547a545） |
| 旧件「mt_gen_case」 | 493 | scripts/test/mt_gen_case.py | 在办（5547a545） |
| 旧件「mt_ime」 | 427 | scripts/test/lib/mt_ime.py（370 行，已复原） | 已替代 |
| 旧件「mt_watchdog」 | 465 | — | **能力损失**（无 python 等价物；用例级超时由 mt_case.py 的 --case-timeout 覆盖） |
| 旧件「mt_loadergate」 | 558 | — | **能力损失**（加载器门控由 preflight 的模组来源闸门 + verify_forge_loader_gate.py 覆盖） |
| 旧件「mt_scope」 | 149 | — | **能力损失**（用例作用域窗口由 mt_case.py 的 scope=case/launch/whole 覆盖） |
| 旧件「mt_fire」 | 159 | — | **能力损失**（一次性触发脚本，无消费方） |
| 旧件「ref-repro」 | 106 | — | **能力损失**（参考测试台复现脚本） |
| 旧件「mt.paths」 | 838 | scripts/test/lib/mt_paths.py（129 行） | 已替代 |
| 旧件「mt.phase」 | 218 | scripts/test/win/lib/phases.sh（退出码/机器行常量） | 已替代 |
| 旧件「mt.autojs」 | 837 | `scripts/test/lib/mt_autojs.py`（374 行）+ `mt_channel.py`（170 行） | 已替代 |
| 旧件「mt.channel」 | 1351 | `scripts/test/lib/mt_channel.py`（三级探测链） | 已替代 |
| 旧件「mt.proc」 | 1523 | `scripts/test/win/stop.sh` + `common.sh`（windows MCP 的 Process(mode:"kill")/CIM 判线） | 部分替代（native 窗口/输入部分损失） |
| 旧件「mt.win32」 | 1229 | — | **能力损失**（窗口定位 + SendInput 合成输入；随 native 通道一起退役） |
| 旧件「mt.conf」 | 57 | `scripts/test/win/lib/common.sh` 的版本表 | 已替代 |

### 2.2 fabric 专用旧测试台（11 个）→ 并入 win/lib/fabric.sh + python

| 已退役旧件 | 行 | 替代物 | 状态 |
|---|---|---|---|
| 旧件「ft」 | 166 | `scripts/test/win/mt.sh --version fabric-1.20.1` | 已替代 |
| 旧件「ft_env」 | 363 | `scripts/test/win/env.sh` + `win/lib/fabric.sh` | 已替代 |
| 旧件「ft_build」 | 245 | `win/lib/phases.sh` 的 build 阶段 | 已替代 |
| 旧件「ft_launch」 | 230 | `scripts/test/win/launch.sh --version fabric-1.20.1` | 已替代 |
| 旧件「ft_stop」 | 192 | `scripts/test/win/stop.sh` | 已替代 |
| 旧件「ft_case」 | 329 | `scripts/test/lib/mt_case.py`（fabric 通道） | 已替代 |
| 旧件「ft_assert」 | 356 | `scripts/test/mt_assert.py` + `win/lib/fabric.sh` 的 eval_assert | 在办 |
| 旧件「ft_prod」 | 369 | `scripts/test/prod_smoke.py`（354 行，生产形态启动） | 已替代 |
| 旧件「ft_inject」 | 295 | — | **能力损失**（手工逐条注入；`fabric.sh` 已把 screenshot/kubejs_reload 记 BLOCKED） |
| 旧件「ft_dispatchreport」 | 166 | — | **能力损失**（dispatchReport 抽取无等价物） |
| 旧件「ft.common」 | 794 | `scripts/test/win/lib/fabric.sh`（952 行） | 已替代 |

### 2.3 静态守门（13 个）→ python 守卫，结论必须逐字等价

| 已退役旧件 | 行 | .py | 状态 |
|---|---|---|---|
| 旧件「verify_chip_acquisition」 | 650 | `verify_chip_acquisition.py（380 行；**补 stale_page/no_note**，BYTE-IDENTICAL）` | 已替代 |
| 旧件「verify_chip_recipes」 | 371 | `verify_chip_recipes.py（246 行，BYTE-IDENTICAL）` | 已替代 |
| 旧件「verify_bountiful_pools」 | 517 | `verify_bountiful_pools.py（452 行，按 旧件 新口径重写，BYTE-IDENTICAL）` | 已替代（**仓库现状本就红 11 项**，见 §4） |
| 旧件「verify_bountiful_instance_exclusions」 | 483 | `verify_bountiful_instance_exclusions.py（254 行，1:1）` | 已替代 |
| 旧件「chipcommon」 | 565 | `chip_common.py（361 行）` | 已替代 |
| 旧件「verify_crafting_recipe_uniqueness」 | 281 | `verify_crafting_recipe_uniqueness.py` | 已交付待验收 |
| 旧件「verify_event_registry」 | 70 | `verify_event_registry.py` | 已交付待验收 |
| 旧件「verify_probe_class_refs」 | 568 | `verify_probe_class_refs.py` | 已交付待验收 |
| 旧件「verify_resource_integrity」 | 172 | `verify_resource_integrity.py` | 已交付待验收 |
| 旧件「verify_sign_registration」 | 61 | `verify_sign_registration.py` | 已交付待验收 |
| 旧件「verify_chip_shop_ui」 | 928 | `verify_chip_shop_ui.py` | 已交付待验收 |
| 旧件「mutate_chip_shop_ui」 | 282 | `mutate_chip_shop_ui.py（**改写型，默认 dry-run**）` | 已交付待验收 |
| 旧件「verify_forge_loader_gate」 | 859 | `verify_forge_loader_gate.py` | 在办（子代理 65b604d9） |

### 2.4 tools / devtools / audit / maintenance（11 个）

| 文件 | 行 | 替代物 | 状态 |
|---|---|---|---|
| 旧件「check_lang_sync」 | 577 | tools/check_lang_sync.py（CLI 为 `--line <line>` 可重复，不是 `-LangDir`；CI 已改指 python） | 已替代 |
| 旧件「check_mod_sources」 | 317 | tools/check_mod_sources.py（442 行，归一化后 BYTE-IDENTICAL；已接进 preflight） | 已替代 |
| 旧件「run_mixin_stack_sanitizer_test」 | 65 | `tools/run_mixin_stack_sanitizer_test.py`（170 行） | 已替代（a6905670） |
| 旧件「verify_content_library」 | 456 | `scripts/verify/verify_content_library.py`（425 行）+ `scripts/devtools/verify_content_library.py`（102 行薄包装） | 已替代（5547a545 + a6905670） |
| 旧件「test-mtsyntax」 | 83 | `scripts/devtools/test_mt_syntax.py`（368 行；对象改为 .sh/.py 语法与契约自检） | 已替代（a6905670） |
| 旧件「start-mtdetached」 | 100 | `scripts/devtools/start_mt_detached.py`（215 行；后台化仍由调用方负责） | 已替代（a6905670） |
| 旧件「start-selftest」 | 650 | `scripts/devtools/start_self_test.py`（832 行；`--dry-run` 422 行逐字节相同） | 已替代（a6905670） |
| 旧件「tooltip_color_audit」 | 343 | `scripts/audit/tooltip_color_audit.py`（365 行） | 已替代（a6905670） |
| 旧件「repair-loose-refs」 | 139 | `scripts/maintenance/repair-loose-refs.sh`（64 行，`/bin/sh`） | 已替代（5547a545） |
| 旧件「lp_offline_assert」 | 41 | `scripts/test/tools/lp_offline_assert.py` | 已替代（父代理） |
| 旧件「publish_autojs_maven」 | 294 | `scripts/test/tools/publish_autojs_maven.py` | 已移植（父代理；实跑 `MT_AUTOJS_MAVEN_RESULT: OK published=2 skipped=0 failed=0`） |
| 旧件「selftest_shot_channel」 | 282 | — | **能力损失**（截图通道自检；截图能力本身也随 native 通道退役） |

## 3 当时仍存在的 旧 shell **解释器**依赖（不是测试资产，不删）

| 位置 | 用途 | 回退顺序 |
|---|---|---|
| `scripts/test/win/lib/common.sh` 里旧件的两个解释器定位函数 | `jps -lvm` 的进程行取进程命令行，做「按线判进程」 | ① PATH 上的旧解释器 ② 旧 shell 7 的安装路径 ③ 系统自带旧 shell 5.1 的路径（5.1，Win10/11 恒有）；可用解释器变量钉死 |
| `scripts/test/lib/mt_ps.py` | 同上（python 侧） | 同上顺序；无解释器时 ERROR(2) |
| `scripts/test/win/env.sh` 的 旧 shell 检查 | 报告走的是哪个解释器 | 无解释器记 FAIL |

判据：`旧 shell 解释器变量=/nonexistent/旧 shell` 跑 `mt.sh --phase preflight` 必须 **rc=2**（证明没有静默降级）。

## 4 已知就地红（不是本轮迁移造成，需用户排优先级）

`python scripts/verify/verify_bountiful_pools.py` 与 旧件 版**结论一致地红**（11 项致命），原因：
- 跨线 `ModItems` 不一致：`card_shop` / `chip_shop` / `event_drawer` 只在 forge-1.20.1 与 neoforge-26.1.2 有，**n21 没有**（属 P0 内部不一致，该修）
- `astral_rews.json` 缺条目：n21/forge 各缺 `alana_sign`、`extra_battery_chip`；**26.1.2 缺 9 条**（属 P1 滞后，按口径只移植不擅动）
- rarity 映射错误：`fanny_sign` / `haiqing_sign` 判成 RARE（应 EPIC）；26.1.2 `misaki_sign` LEGENDARY→EPIC
- 各线 `astral_rews.json` md5 不一致；卡牌标签仅 n21 有 `effect_card_barricade` / `effect_card_repulsion`

`python scripts/verify/verify_sign_registration.py` 同样**结论一致地红**（py 与 旧件 归一化输出逐字节相同，rc 均 1）：
- `FAIL [neoforge-26.1.2] registered=32 signs=25 stand=31` —— `data/astral_dice/tags/item/signs.json` 只有 25 项，缺 alana / ame / deepseek / dorothy / jill / kangel / lingling 七张立牌；
- `FAIL [fabric-1.20.1] registered=32 signs=32 stand=24` —— `data/curios/tags/items/stand.json` 只有 24 项，同样缺这七张（`blank_sign` 按设计不入 stand 表）；
- P0 两线（n21 / forge）均 `OK registered=32 signs=32 stand=31`；
- 性质：`git show origin/multi-main:<同上路径>` 与 HEAD 的条目数**完全相同**（25 / 24 / 32）⇒ 主线本就是红的，不是本轮合并造成；两条都是 P1 线内容，按用户口径「只移植不擅动」登记。

`python scripts/verify/verify_forge_loader_gate.py` rc=2（**无法判定**，非产品红）：`fmlloader` 的 POM 不在 Gradle 缓存目录里 ⇒ `maven-artifact` / `commons-lang3` 版本取不到 ⇒ 离线判定器 classpath 不完整。补救命令写在输出里：`gradlew :forge-1.20.1:dependencies --refresh-dependencies`。

## 5 删除前门禁（全绿才动手）

1. 本文件 §2 每行状态都不是「在办」。
2. `for f in scripts/test/win/*.sh scripts/test/win/lib/*.sh scripts/test/linux/*.sh; do bash -n "$f"; done` 全绿。
3. `python scripts/test/lib/mt_case.py validate --cases-dir scripts/test/cases` ⇒ `errors=0`。
4. `grep -rn '旧 shell 解释器调用\|旧 shell 委托函数\|mt_phase_deprecated' scripts/test/win/ --include=*.sh` ⇒ 0。
5. `grep -rn '旧 shell' .github/` ⇒ 0；`.github/workflows/build.yml` 五处守门全部 `python`。
6. 反向判据：`旧 shell 解释器变量=/nonexistent/旧 shell bash scripts/test/win/mt.sh --version 1.21.1 --phase preflight` ⇒ **rc=2**。
7. 真跑：`bash scripts/test/win/mt.sh --version 1.21.1 --phase preflight` ⇒ rc 0 或 10，**不得 2**。
8. 删除后 `git status` 里不得出现「本应保留却变 D」的源文件；`git diff --stat HEAD` 只应包含本轮预期文件。

## 6 待用户裁决

1. `vision` 类断言（17 处）在 python 侧记 BLOCKED(11) —— 是否接受（视觉判定在测试台之外，全仓只有 `VISION_PATH` 生产者、无消费者）。
2. native 注入通道（窗口定位 + SendInput）—— **不再是「必须退役」**：`scripts/test/mt_inject.py`（33,969 B）已用 ctypes 直调 `user32.SendInput` 重建了真实键鼠通道（`key` / `mouse` / `cmd` 三个子命令，含 `--hwnd` 与 `--transport sendinput|postmessage`），`scripts/test/lib/mt_ps.py` 提供了 旧 shell-free 的进程/窗口定位。当前 `phases.sh` 的 `launch --channel native` 仍记 BLOCKED(11)，**是否本轮把它接回 `mt_inject.py` 需要用户裁决**（不接 = 只能走 AutoJS 端点 `http://127.0.0.1:25585/mcp`）。
3. §4 的 `verify_bountiful_pools` 就地红 11 项：P0 内部不一致是否本批修，26.1.2 缺条目是否本批移植。
## 7 本轮验收读数（2026-11-02 21:0x，工作树 = merge `175420bd` + bump `bc36495e` 之上的未提交改动）

| 守门 | rc | 末行 / 结论 |
|---|---|---|
| `scripts/verify/verify_chip_acquisition.py` | 0 | `RESULT: PASS`（致命项 0） |
| `scripts/verify/verify_chip_recipes.py` | 0 | `RESULT: ALL OK` |
| `scripts/verify/verify_crafting_recipe_uniqueness.py` | 0 | `RECIPE_UNIQ: OK` |
| `scripts/verify/verify_event_registry.py` | 0 | `EVENT_REG: OK line=neoforge-1.21.1 events=13 ids=13 icons=ok langs=3` |
| `scripts/verify/verify_probe_class_refs.py` | 0 | `RESULT: PASS — 所有探针 Java.loadClass 目标都能在运行时 classpath 解析` |
| `scripts/verify/verify_resource_integrity.py` | 0 | `RESULT: PASS — 模型链闭合` |
| `scripts/verify/verify_bountiful_instance_exclusions.py` | 0 | `RESULT: ALL CLEAR` |
| `scripts/verify/verify_chip_shop_ui.py` | 0 | `CHIPUI_RESULT: OK`（`CHIPUI_TOTAL_FAIL: 0`） |
| `scripts/verify/verify_bountiful_pools.py` | **1** | 就地红 11 项（§4） |
| `scripts/verify/verify_sign_registration.py` | **1** | 就地红 2 条线（§4） |
| `scripts/verify/verify_forge_loader_gate.py` | **2** | 无法判定：fmlloader POM 不在缓存（§4） |
| `tools/check_lang_sync.py` | 0 | 各线 `[OK]`，en_us=1143/1143/1167/1152 |
| `tools/check_mod_sources.py` | 0 | `MOD_SOURCE_GATE: OK violations=0 exceptions=1 infos=0` |
| `tools/check_changelog_parity.py` | 0 | `CHANGELOG_PARITY: OK sections=32 entries=822/822` |

> 取读纪律（本轮踩过）：① 一次 `python -u temp/p0/run_gates.py` 批量跑、只取 `returncode`，**不要**用 `cmd | grep …; echo rc=$?` 取退出码（管道/命令替换会给出别的进程状态，本轮因此误报 `verify_chip_shop_ui` 为红）。② `mutate_chip_shop_ui.py` 的变异自测会在**真实源文件**上做「改一处 → 跑守门 → 还原」，运行期间任何并发守门都会被读到变异态 ⇒ 看到 `CHIPUI_FAIL` 先查 `git status --porcelain -- '*.java'` 是否非空，非空即变异未还原/正在跑，不得据此判红。

## 8 迁移等价性证据（批次 A / B，2026-11-02 21:0x）

判据 = 「同一输入下 旧件 与 py 的 stdout 原始字节相同（md5 相同）、退出码相同、反例同时变红、还原后文件 md5 前后一致」。

| 脚本 | 绿跑 md5（旧件 == py） | rc | 反例（同时变红） |
|---|---|---|---|
| `verify_crafting_recipe_uniqueness.py` | `3e325471a1e7a1a6e04ccda605725266` | 0/0 | 复制 `card_shop.json` → 重叠 ⇒ 两边 rc=1，`RECIPE_UNIQ: TOTAL collisions=1`（`6ba9bc7f…`） |
| `verify_event_registry.py` | `8d19c19a18e0b18a435448f85931a635` | 0/0 | 图标改名 ⇒ 两边 rc=1，`EVENT_REG: FAIL failures=1`（`58249d14…`） |
| `verify_forge_loader_gate.py` | `05323fa3fd8c2c51c020fdf8962e367a` | 2/2 | `mixinbooster mandatory=true` ⇒ 两边 rc=1（fail 优先于 undecidable，`fb7b1421…`） |
| `verify_probe_class_refs.py` | `14288a17e5950f661fe4f04643c99da9` | 0/0 | 探针指向杜撰 FQN ⇒ 两边 rc=1（`0c0200f3…`） |
| `verify_resource_integrity.py` | 归一化后 TEXT-IDENTICAL | 0/0 | 删 jar 内模型 ⇒ 两边 rc=1，`RESULT: FAIL` |
| `verify_sign_registration.py` | 归一化后 TEXT-IDENTICAL | 1/1 | 删 `jasmine_sign` 标签 ⇒ 两边 rc=1 |
| `mutate_chip_shop_ui.py` | **BYTE-IDENTICAL**（7074 B，md5 `949b0b4551d4b4f574e6a62c1283481a`） | 0/0 | 39 条变异全部 39/39 判红 |

**深跑**：给 fmlloader 补合成 POM 后 `verify_forge_loader_gate.py` 真跑 javac+java 判定器 ⇒ 两边 rc=0、md5 `83654196…` 相同，关键行 `AP_LG_NEW:range=[47.4.10,48):v47.4.10=accept:v48.0.0=reject:gate_ok=1`，py 写出的 `LoaderGateProbe.java` 与 旧件 的副本逐字节相同（7219 B，`f39a1655…`）。

**新增的两条反假绿守卫（唯一允许「py 比 旧件 多」的地方，都复用脚本已有状态词）**：
1. `verify_crafting_recipe_uniqueness.py`：配方文件数为 0 ⇒ `RECIPE_UNIQ: ERROR …` + rc=2（旧件为 vacuous PASS rc=0）。
2. `verify_probe_class_refs.py`：各线参与判据的 `loadClass` 总数为 0 ⇒ `RESULT: ERROR（无法判定）…拒绝 vacuous PASS` + rc=2（旧件为 PASS rc=0）。

**已知正当差异（非逻辑）**：`verify_event_registry.py` / `verify_probe_class_refs.py` 的旧件用自带输出函数、未 pin 编码，本机重定向时落 GBK；py 一律 UTF-8，强制 UTF-8 控制台下两边原始字节相同。`verify_chip_shop_ui.py:1273-1274` 的 `CHIPUI_NEXT:` 提示行**有意**改为 `bash scripts/test/win/mt.sh …`（父代理裁定：py 不应再打印一条本分支正在删除的命令）。

**旧件 侧已确认的实现缺陷（py 不再复现，属修正而非偏离）**：`scripts/verify/verify_sign_registration.py:28-37` 的 `undecidable` 是全局计数器，任一线不可判定会 `continue` 掉后续所有线；py 按线独立判定。

## 9 mt_inject / mt_assert / lib.mt_ps 迁移（子代理 6ad9d80f，2026-11-02 21:0x）

| 文件 | 行数 | sha256 前 16 | 行尾 |
|---|---|---|---|
| `scripts/test/mt_inject.py` | 974 | `5c0177cd047dfc60` | CRLF=0 / loneCR=0 / 无 BOM / 结尾 LF |
| `scripts/test/mt_assert.py` | 945 | `623ebb806ca63b05` | 同上 |
| `scripts/test/lib/mt_ps.py` | 307 | `e075411d2fc5de09` | 同上 |

**lib/mt_ps.py 的定位**：脱旧 shell 依赖的「按线判进程/窗口」helper（`jps_exe()` / `java_rows()` / `java_procs(version)` / `markers_for()`）。数据源三态：`jps -lvm` 的进程行（优先）→ 旧解释器内联命令取进程表（旧路径）→ tab 兜底。**不调用任何旧件、不加载旧脚本模块**（AST 复核：非 docstring 字符串里零命中；唯一 subprocess 点在 `:126 run_tolerant`）。判线标记串沿用 `stop.sh:59-60` 与 `common.sh:256-279` 口径：`:<子项目>:` / `run\<ver>` / `run/<ver>`，fabric 追加 `run\client|server` 与 `runclient|runserver` —— **刻意不用裸子项目名**（实测 Gradle daemon 的 cmdline 命中裸名即为反例）。

**等价性读数**：`snapshot --version 1.21.1` 两边同 rc=0 且机器行逐字相同 `MT_SNAPSHOT: OK — 1.21.1 run=20260921-191522 window=launch latest=1537237B debug=2687362B probe=0B anchors=3`；写出的 `.mt_snapshot.json` 仅 ts 行不同（其余逐字节相同，已把 `scripts/test/cases/.mt_snapshot.json` 恢复到跑前 sha256 `2725f7068acb7601…`）。`log --pattern 不存在的标记XYZ` 两边 rc=1 且行形相同。`inject cmd` 无游戏时两边 rc=2、stderr 逐字节相同。27 条错误/只读路径矩阵 **SAME=26 / DIFF=1**（唯一 DIFF 见下第 2 条）。`--dry-run` 的 `MT_INJECT_DRYRUN` 消息元组三例两边逐字节相同。

**三处与 旧件 的差异（父代理裁决：全部保留）**
1. 新增 `--help` / `-h` / `help` → 用法 + rc=0（旧件 会把 `--help` 当未知参数 rc=2）。保留：迁移后 CLI 必须自描述，且不改变任何既有机器行/退出码路径。
2. 非法正则的**详情**文案不同（.NET [regex] 异常原文 vs re.error）。保留：前缀 `MT_ASSERT: ERROR — 非法正则 (<Pattern>)：` 与 rc=2 完全一致，伪造 .NET 文案反而是假证据。
3. **修 旧件 的按键表缺陷**：`windows MCP 的 Click/Move/Type 输入:99` 把 q 的 Scan 写成 `0x51`、`:104` 的 Vk 写成 `0x10`，而 US 布局 Q 应为 VK=`0x51`、set-1 扫描码=`0x10`（`0x51` 是 Numpad3、`0x10` 是 SHIFT）⇒ 旧件 的 drop 别名（指向 q）实际注入的是错键。py 按正确值写（`mt_inject.py:58` 里 q→0x10、`:63` 里 q→0x51）。保留修正；其余 20 余键与 旧件 逐字一致。

**未覆盖分支（无法判定，如实登记）**：真实注入的 PASS 路径（前台断言 / SendInput 真按键 / 鼠标中心 / 滚轮）需运行中的客户端，本轮未启动游戏；eval / state 的 PASS/FAIL 分支需 AutoJS 端点（仅验到 BLOCKED(11)）。

## 10 收口记录（2026-11-02 21:1x）：62/62 全部删除

**状态**：`git ls-files` 里已无旧脚本件 = **0**。工作树里唯一剩下的 `旧脚本` 是仓库根的 `旧件「deploy」`，它在 `.gitignore:68`（本人个人脚本，git 未跟踪，非测试资产）⇒ 不动。

删除分两批：`temp/p0/do_delete_旧 shell.py` 先 `git rm` **57** 个（62 − 5 个 HOLD）；最后 5 个待移植件（`start_mt_detached` / `test_mt_syntax` / `devtools/verify_content_library` / `audit/tooltip_color_audit` / `tools/run_mixin_stack_sanitizer_test`）在 a6905670 交付后由父代理 `git rm` ⇒ 合计 **62**。

### 10.1 收口复核读数（`python temp/p0/post_delete_verify.py` ⇒ rc=0 / `FAILED = 0`）

| 项 | 读数 |
|---|---|
| tracked 旧脚本 / 旧脚本模块 | **0** |
| 可执行 旧 shell 解释器调用引用（*.sh / *.py / *.yml） | **0** |
| `bash -n` | 13 文件 / 0 失败 |
| `py_compile` 全量 | **90 文件 / 0 失败** |
| 11 条常规门禁 | 全 `[OK]`：lang_sync 各线键集一致 · mod_sources `violations=0 exceptions=1` · changelog `sections=32 entries=822/822` · chip_acq PASS · chip_recipes ALL OK · recipe_uniq OK · event_reg OK · probe_refs PASS · res_integrity PASS · bount_excl ALL CLEAR · chip_ui `CHIPUI_TOTAL_FAIL: 0` |
| `mt_case validate` | `files=297 errors=0` |
| `mt_preflight --all` | `MT_PREFLIGHT: OK — 21 项全部满足（1.21.1, 1.20.1, 26.1.2, fabric-1.20.1）` |
| 已知就地红（回显、不判失败） | bountiful_pools rc=1（11 项）· sign_registration rc=1（2 条线）· forge_loader_gate rc=2（无法判定）· content_library rc=1（1.2.0 冻结快照，**非常规门禁**） |

### 10.2 变异自测污染（第二次踩到，判据固化）

`gate chip_ui` 两次变红的根因**不是迁移缺陷**，而是变异脚本残留：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/shop/ChipShopRoller.java:77` 被留成 `pool.remove( chosen );`（正确 `pool.remove(chosen);`）；`ChipShopCatalog.java` 只是 CRLF/LF 归一化（`git diff --stat` 为空，属正常）。

⇒ **固化纪律**：看到 `CHIPUI_*`:FAIL 先跑 `git status --porcelain -- '*.java'`；非空 = 变异在跑或未还原，**不得判红**；提交前必须确认为空。

### 10.3 能力缺口（删除的代价，如实登记）

1. **`mt_env.py mods` 的 `Install-*` 族未复原**（KubeJS/Rhino 探针运行时、Sodium/Iris/ModernFix、光影包、AutoJS、Carpet、SlimeGuard、探针工程）⇒ 改状态判据：`run/<v>/mods` 缺 `kubejs*` / `rhino*` / `AutoJS*` 即 `MT_MODS: BLOCKED — …` **rc 11**（绝不静默 0）；jar 已就位则 OK + 一行「安装器未复原」。**本轮最大缺口**：干净环境下 26.1.2 / 1.20.1 的 `mods` 子命令会直接 BLOCKED。
2. **四个 CLI 未接进 bash 编排**：`mt_env.py` / `mt_cleanup.py` / `mt_capture.py` / `mt_gen_case.py` 与 旧 shell 期一样是独立 CLI；bash 台的 `env` 阶段走 `scripts/test/win/env.sh`（bash 原生自检），`stop` 阶段走 `scripts/test/win/stop.sh`（内含自己的 `--purge-saves` 白名单实现）⇒ **能力等价、调用面不同**，不构成静默退化。
3. `launch --channel native` 仍记 BLOCKED(11)（接回需用户裁决，见 §6-2）。
4. `vision` 类断言（17 处）记 BLOCKED(11)（见 §6-1）。
5. `selftest_shot_channel`（282 行截图通道自检）无 python 等价物 ⇒ 能力损失，仅 `git show 175420bd:<path>` 可取回（`Start-SelfTest` 已由 `scripts/devtools/start_self_test.py` 替代）。
### 10.5 最后 6 个 devtools / audit / tools 移植（a6905670，判据 = HEAD 版 旧件 对照）

因旧件在 21:10 已被 staged 删除，旧件侧对照改从 `git archive HEAD` 取（8424 文件），在 `/tmp/evid` 内同根跑两侧；旧自检入口另用硬链接镜像 `/tmp/st_mirror`（385 文件）。统一判据 `diff --strip-trailing-cr`（旧件是 CRLF、py 按纪律 LF）。

| py | 行 / 字节 | 等价读数 |
|---|---|---|
| `scripts/devtools/test_mt_syntax.py` | 368 / 13743 | 全仓 116 行 **diff rc=0**，末行 `RESULT: 115 个文件（旧脚本 62 / js 53），解析失败 0 个`；反转：不存在目录两侧 rc=2 同文案，坏语法两侧 rc=1 同 `PARSE FAIL`（诊断正文不同，见已知差异 1） |
| `scripts/devtools/start_mt_detached.py` | 215 / 9344 | 机器行 `MT_DETACHED: pid=<PID> script=… log=…` 与 `MT_DETACHED_HINT:` 归一化后 diff rc=0；`-Merge` 两侧 rc=0 |
| `scripts/devtools/start_self_test.py` | 832 / 41635 | `--dry-run` 各 422 行 **diff rc=0**；N1 缺 jar 两侧 rc=1 且 stdout 逐字节相同（`[FAIL] 找不到本次构建的本模组 jar：astral_dice-26.4-SNAPSHOT.6+neoforge_1.21.1.jar`）；N2 假 jar 两侧 rc=1，归一化时间戳后 diff rc=0 |
| `scripts/devtools/verify_content_library.py` | 102 / 4300 | 薄包装（转发 `--root`，stdout/rc 透传）；`scripts/devtools/verify_content_library.py` 在 git 与工作树均不存在 ⇒ 判重结论 = 与 `scripts/verify/verify_content_library.py` 常量同源（START_COMMIT=68dbd59 / VERS / BASE_TAG_COUNTS / BASE_SLOT_COUNTS / BLANK_ITEMS / NEW_EFFECTS） |
| `scripts/audit/tooltip_color_audit.py` | 365 / 16380 | 真仓 245 行 **diff rc=0**，末行 `tooltip 染色审计： FAIL（230 处违规）`（旧件/py rc 均 1）；空根反转两侧 rc=2 同文案「覆盖度不足：12 份 lang 文件不存在」 |
| `tools/run_mixin_stack_sanitizer_test.py` | 170 / 6533 | 两侧 `MSS_RESULT: PASS (10/10)` rc=0；反转（测试源塞非 Java 文本）两侧 rc=1 `MSS_RESULT: FAIL (javac)`，未假绿 |

**四处已知差异（父代理裁决：全部保留 py 行为，均已登记在各文件 docstring）**
1. `test_mt_syntax` 的解析诊断正文不同（旧件 真 旧 shell 解析器 vs py 结构化检查器）；机器行（`PARSE FAIL` / 缩进 / `RESULT` / rc）完全一致；正文等价只能调 旧 shell，与退役目标冲突。
2. `start_mt_detached` / `start_self_test` 的参数错误 rc：旧件 因 `$ErrorActionPreference='Stop'` 让 `exit 2` 成为死代码、实测一律 rc=1；py 按「未知参数 rc=2」通用契约 ⇒ **保留 py 的 rc=2**（不复制缺陷）。
3. `start_mt_detached` 的目标解释器按扩展名选（`.py`→`sys.executable`、`.sh`→`bash`），其它扩展名 rc=2 并提示 `--interpreter` 显式覆盖；属退役带来的收窄，不是遗漏。
4. `start_mt_detached` 的 `-Merge`：旧件 解析后从未使用（死开关），py 按头部文档语义真正把 stderr 并入 `--out` ⇒ 保留实做。

### 10.4 新增待裁决（来自 5547a545 交付报告）

1. `mt_env mods` 的 `Install-*` 是否另批复原，还是接受「jar 已就位即通过、缺则 BLOCKED(11)」。
2. `mt_capture` 的 f2 是否补 sendinput 通道与更严的前台抢占（现为 PostMessage + 3 次抢前台 + en-US 语言就绪检查）。
3. `repair-loose-refs.sh` 是否保持只修 `refs/heads/**`（`refs/remotes/**` 绝不能复活，否则 `git fetch --prune` 失效、出现幽灵分支）。
4. `verify_content_library.py` 是否重新进门禁（现状 rc=1 属预期，且 `docs/` 被 gitignore，干净 clone 跑不了）。
5. `mt_cleanup` 的 20s settle 轮询（旧件 有、python 原件无）是否保留。

## 11 2026-11-09 全仓收口（旧 shell 口径彻底清零）

- 物理脚本：仓内 `.ps1/.psm1/.psd1` = **0**（含仓库根 `deploy.ps1` 删除、两份 `AGENTS.md.bak-*` 备份删除）。
- 运行期依赖：测试台脚本不再调用任何外部 shell —— 进程表统一 `jps -lvm`（`MT_JPS_EXE` 可钉），
  判线 = jps 行 + `run/<ver>/win-run.pid` + 25585/25565/25575 的 LISTENING。
- 收停语义：`stop.sh` 只**发现并打印** `MT_STOP: PENDING pid=…`；终止由 Agent 经 `mcp__windows__Process(mode:"kill")` 执行；
  `--phase stop --verify` 复核端口与 jps 行输出 `MT_STOP: OK`；`--force` 为 no-op。
- 引用清理：docs 全量（60 文件 + 20 份历史文档溯源横幅）、三份测试台 README、`KNOWN-ISSUES.md`、
  用例 JSON、Java / gradle 注释、探针与生成报告、`.gitignore`、历史 html 报告全部改为现役口径。
- 归档件：`docs/archive/legacy_scripts_20260912.zip`（旧脚本归档，含 MANIFEST + 逐文件 sha256）
  已于 2026-11-09 删除；内容仍可从 git 历史 `d73cf841` 取回，不再作为仓内载体。
- 守门：`python docs/agent/verify-split.py` 全绿；`mt.sh --version 1.21.1 --phase env` ⇒ `MT_ENV: OK`；
  `stop.sh --dry-run` / `--verify` ⇒ `MT_STOP: OK`；脚本 `bash -n` 与 `python -m py_compile` 全绿；
  已跟踪文本文件对旧关键词命中 **0**（唯余 `models/eventblock.bbmodel` 的 base64 纹理串，属假阳性）。
