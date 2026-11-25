# Release 1.5.0 — forge-1.20.1 线 实机验收报告

- 仓库：`D:\MCProject\astral_dice_multiloader`（分支 `multi-main`，HEAD `fd990c15`，全程只读 git）
- 被测产物：`forge-1.20.1/build/libs/astral_dice-1.5.0+forge_1.20.1.jar`，2660134 B，sha256 `fd6d8d8205e290701a18af9cc489dd7efab6dc82d9a310f3079058efa4b2e9c4`（reobf，219 个 SRG 提示类；与整合包 `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods` 内的同名 jar 逐字节一致）
- 验收时间：2026-11-02 03:08–03:31（Asia/Hong_Kong）

## 0. 判定

**PASS** — 27/27 断言全 PASS（含 2 条 vision 由我本人 read_image 肉眼确认），用例连续 4 次运行（03:25 / 03:26 / 03:27 / 03:28 开始）均为 `MT_CASE_RESULT: RELEASE150-1.20.1 = PASS`；生产形态启动的日志判据全部满足；无本模组相关 ERROR/FAIL/崩溃。

| 项 | 结果 |
|---|---|
| A 前置 `mt_preflight.ps1` | PASS（`MT_PREFLIGHT: OK — 9 项全部满足（1.20.1）`，exit 0） |
| B 生产形态启动 + 日志判据 | PASS（err:5x=0 / Failed to load native lib=0 / attachment 崩溃=0 / 版本行在） |
| C 用例 27 断言 | PASS（27/27，`MT_VALIDATE: OK`） |
| D [REL150] 探针原文 | PASS（`latest.log:40514`，见 §4） |
| E 截图 3 张 + 肉眼确认 | PASS（见 §5） |
| F 收尾 | PASS（`MT_STOP: OK`，25585 已关闭，无本流程残留；见 §8） |
| G 归因 | 无本模组相关失败；第三方噪声已分类登记（见 §7） |

**本模组相关 ERROR/FAIL 数量：0**（仅有 1 条非阻断 WARN，见 §7 末）。

---

## 1. 通道选择：production 形态（唯一可用通道）

已知事实：YSM（yes_steve_model）在 dev/userdev 形态拒载原生库（`[YSM] Unsatisfied runtime environment: err: 54` → `Failed to load native lib` → Jade 拉 YSM 数据触发 `Data attachment type ... must be registered!` 客户端崩溃）。因此本次绕开 Gradle，用 production 形态启动：

- 启动器：`run/1.20.1/.tmp/prodlaunch.py`（由已验证的 `run/1.21.1/.tmp/prodlaunch.py` 改写：实例名 `1.20.1-Forge 模组测试`、java `C:\Program Files\Zulu\zulu-21\bin\java.exe`、`--quickPlaySingleplayer=testworld`、重复库去重以防 BootstrapLauncher `Duplicate key`）。
- **gameDir 采用 `D:\MCProject\astral_dice_multiloader\run\1.20.1`（即测试台自己的 run 目录），而不是独立 prodgame**：`scripts/test/lib/Mt.Paths.psm1` 把用例的日志根与截图根硬编码到 `run/<版本>\logs\latest.log` 与 `run/<版本>\screenshots`（无 env 覆盖），只有 gameDir 就是 run 目录时，`latest.log`、`.mt_shots.json`、`mt_stop` 才能原生对上；同时进程命令行含 `run\1.20.1`，命中 `Get-MtProcessMarkers`，收尾可被正确回收。
- 进程：PID **35632**，03:13:04 起，03:29 被 `mt.ps1 --phase stop` 收停。
- 启动命令证据：`run/1.20.1/logs/latest.log:18` `Launching target 'forgeclient' with ... --gameDir, D:\MCProject\astral_dice_multiloader\run\1.20.1, ..., --quickPlaySingleplayer=testworld`。

## 2. A 前置

```
pwsh -NoProfile -File scripts/test/mt_preflight.ps1 --version 1.20.1
  → MT_CHANNEL: mode=autojs
  → MT_PREFLIGHT_AUTOJS: OK
  → MT_PREFLIGHT: OK — 9 项全部满足（1.20.1）      exit 0
```
要点：branch WARN「非发布线」(multi-main，2026-09-17 裁决放行)；MOD_SOURCE_GATE `violations=0 exceptions=1`；run 目录可写；starengine 1.1.0 匹配。前置另报告「另有 2 个 Minecraft 客户端在跑（PID 11644 / 13864）」——不是本流程进程，未动。

## 3. B 生产形态启动 + 日志判据

| 判据 | 实测（`run/1.20.1/logs/latest.log` 与 `run/1.20.1/.tmp/prod-run.log`） |
|---|---|
| `err: 5x` | **0**（两文件各自 grep `err: 5` 计数 0） |
| `Failed to load native lib` | **0** |
| attachment 崩溃（`must be registered`） | **0**（另 `Unsatisfied runtime environment` 亦 0） |
| 本模组版本行 | `latest.log:375` `[Astral Dice] 版本互通门槛(Version Gate):mod_version=1.5.0+forge_1.20.1 → 网络互通号=1.5;…`；`latest.log:742` `Astral Dice mod loaded.` |

时序（同一 latest.log）：`:18` 启动 → `:926` `Sound engine started` → `:1383` `MCP endpoint listening on http://127.0.0.1:25585/mcp` → `:1394` `DevTester[local:E:55b63e78] logged in … at (-2.5, -60.0, -2.5)` → `:1655` `Time from main menu to in-game was 13.04 s`（总启动到进世界 88.9 s）。

**口径说明（必须记录）**：验收条目里写的字面标记 `Astral Dice 1.5.0+forge_1.20.1 (astral_dice)` 在本线日志中**不存在**（该格式是 NeoForge 1.21.1 的 Mod List 段命名格式）。1.20.1 的等价证据是上面两条 + 探针 `modver=1.5.0+forge_1.20.1`（§4）三处互证，不构成验收缺口。

## 4. C/D 用例结果与玩家数据

### 4.1 机器结论

```
MT_CASE_RESULT: RELEASE150-1.20.1 = PASS
MT_CHANNEL: delegated_count=5          （3× screenshot + 2× vision）
MT_CASES_SUMMARY: RELEASE150-1.20.1=PASS
```
完整 stdout 存档：`run/1.20.1/.tmp/case-run5.out.txt`（100 行）；`scripts/test/cases/.mt_run_state.json:48` `"RELEASE150-1.20.1": "PASS"`。

### 4.2 [REL150] 探针原文（D）

> `run/1.20.1/logs/latest.log:40514`
> ```
> [0211月2026 03:28:24.379] [Server thread/INFO] [KubeJS Server/]: autojs-eval#23: [REL150] ver=1.20.1 gamemode=creative pos=100,108,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+forge_1.20.1
> ```

客户端侧同批取证（`latest.log:40553`、`:40554–40560`）：

> `[REL150] chip id=astral_dice:abyss_reagent_chip count=3 name=深渊试剂 tipn=7 path=screen`
> `tip i=0 text=深渊试剂` / `tip i=1 text=槽位： 筹码` / `tip i=3 text=装备时治愈层数 +2。触发治愈回血时：获得实际治疗量 × 2 的伤害吸收` / `tip i=5 text=当前治愈：0 / 32` / `tip i=6 text=Astral Dice`
> `guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=装备时治愈层数 +2；触发治愈回血时，获得「实际治疗量 ×2」的伤害吸收。`

商店打开为模组自身日志：`latest.log:40656` `[astral_dice] CHIP_SHOP_OPEN player=DevTester offers=target_chip+sandwich_low_chip+candy_chip purchased=0 refreshes=3`。

### 4.3 逐条断言

| # | 断言（原文摘要） | 结果 | 证据 |
|---|---|---|---|
| 1 | log whole `mod_version=1\.5\.0\+forge_1\.20\.1` | PASS | `latest.log:375`（whole 窗口，命中 1） |
| 2 | `\[REL150\] ver=1\.20\.1 gamemode=creative ` | PASS | :40514 |
| 3 | `pos=100,-?[0-9]+,100 ` | PASS | :40514（`pos=100,108,100`） |
| 4 | `hp=20 ` | PASS | :40514 |
| 5 | `item=astral_dice:abyss_reagent_chip` | PASS | :40514 |
| 6 | `xp=30 ` | PASS | :40514 |
| 7 | `slotted=[1-9][0-9]* ` | PASS | :40514（`slotted=6`） |
| 8 | `chips=3 ` | PASS | :40514（3 个 count=1 的堆求和） |
| 9 | `modver=1\.5\.0\+` | PASS | :40514 |
| 10 | `chip id=… count=3 name=(深渊试剂|Abyss Reagent) ` | PASS | :40553 |
| 11 | `tip i=0 text=.+` | PASS | :40554 |
| 12 | `guide key=astral_dice\.guide\.entry\.abyss_reagent_chip\.1 text=.+` | PASS | :40560 后的 guide 行（命中 2 次） |
| 13 | `inventory open=1` | PASS | `[REL150] inventory open=1 hover=480,270` |
| 14 | `CHIP_SHOP_OPEN player=` | PASS | :40656 |
| 15 | `screen=\S*ChipShopScreen` | PASS | `[REL150] screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen` |
| 16–19 | 4× `sys id=astral_dice:{dice,attack_card_medium,abyss_reagent_chip,jill_sign} ok=1` | PASS×4 | `sys … name=骰子 / 攻击(中) / 深渊试剂 / 调酒师立牌` |
| 20 | eval from=state 综合正则 | PASS | 实际 `"[REL150] ver=1.20.1 gamemode=creative pos=100,108,100 hp=20 … item=astral_dice:abyss_reagent_chip"` |
| 21 | eval from=chip `^["]?(深渊试剂|Abyss Reagent)["]?$` | PASS | 实际 `"深渊试剂"` |
| 22 | eval from=screen `ChipShopScreen["]?$` | PASS | 实际 `"com.merlinkitsune.astral_dice.screen.ChipShopScreen"` |
| 23 | absent 命令错配话术族 | PASS | 未出现（`No player was found` 等 0 次） |
| 24 | jar 阈值 models≥120 / recipes≥100 / signs≥25 / entries≥1700 | PASS | `jar=astral_dice-1.5.0+forge_1.20.1.jar models=158 recipes=125 entries=2032 signs=32` |
| 25 | crash | PASS | 本次运行无新增崩溃报告（基线 5） |
| 26 | vision abyss_reagent_tooltip | DELEGATED → 我已肉眼确认 | 见 §5 |
| 27 | vision chip_shop_ui | DELEGATED → 我已肉眼确认 | 见 §5 |

### 4.4 用例改动清单（仅用例自身缺陷；已备份 + 校验）

备份：`scripts/test/cases/RELEASE150-1.20.1.json.bak-before-release150-run`（15380 B，sha256 `bd9f996f…b34714`，与改前原文件一致）。
改后：15889 B，sha256 `c9d0cb3bc9de81a8f61ad5dfc7abf46987a6b7456ff76edfe93d4e7c92b74f59`。
校验：`pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case …/RELEASE150-1.20.1.json` → `MT_VALIDATE: OK — RELEASE150-1.20.1`（exit 0）。
**未删除、未放宽任何断言**；阈值 120/100/25/1700 原样保留。

| # | 位置 | 改前 → 改后 | 理由（实测） |
|---|---|---|---|
| 1 | step `inject_command` | `/gamemode creative` → `/gamemode creative @a` | `AutoJS.command()` 走 `Commands.performPrefixedCommand(server.createCommandSourceStack(), …)`，命令源是控制台；不带选择器会报 `No player was found`，直接踩中 #23 absent 断言 |
| 2 | step `eval_js(side=server)` save_as=state | 物品 id 改用 `AutoJS.id(AutoJS.held(p))`；筹码改为遍历背包按 `AutoJS.id` 匹配并对 `getCount()` 求和；坐标 `Math.round`→`Math.floor` | ① 该上下文 `String(ItemStack)` = `"1 abyss_reagent_chip"`（**无命名空间**），原 indexOf 永远不匹配 → `chips=0`；② 筹码 maxStackSize=1，`/give … 3` 产生 3 个 count=1 的堆，必须求和才是 3；③ `/tp … 100 120 100` 后玩家中心在 100.5 → `Math.round(100.5)=101`，而断言要求 `pos=100,…`（用 floor 符合方块坐标约定，**未动断言**） |
| 3 | step `eval_js(side=client)` save_as=chip | 改为读取 `mc.player.getInventory()` 真实堆（36 格、跳过空堆、按 `AutoJS.id` 匹配、累加 count、取首个真实堆做名称与 tooltip，id 用 `AutoJS.id`），兜底才 `new ItemStack(item)` | 原实现 `new ItemStack(item)` 计数恒为 1，与断言 `count=3` 自相矛盾；且缺命名空间 id |
| 4 | assert from=chip | `^(深渊试剂|Abyss Reagent)$` → `^["]?(深渊试剂|Abyss Reagent)["]?$` | eval 断言比较对象是 **JSON 编码后的值文本**（实际含首尾引号），原锚定正则对正确值也永不匹配 |
| 5 | assert from=screen | `ChipShopScreen$` → `ChipShopScreen["]?$` | 同上（同一原因，值文本带引号） |

## 5. E 截图（3 张，均已本人 read_image 肉眼确认）

全部 1920×1080 PNG，落在 `run/1.20.1/screenshots/`，副本留存 `work/release150-1.20.1-shots/`，并在 `run/1.20.1/screenshots/.mt_shots.json` 追加了 tag 映射（用例的 vision 断言即通过该映射解析到文件，见 4.3 #26/#27 的 `VISION_PATH`）。

| tag | 路径 | 字节 | sha256 |
|---|---|---|---|
| `abyss_reagent_tooltip` | `run/1.20.1/screenshots/abyss_reagent_tooltip.png` | 1261572 | `2b5c882f64b41f2edee90819049c2c0040b95bd449ee1b72b07f7b899cc7b183` |
| `chip_shop_ui` | `run/1.20.1/screenshots/chip_shop_ui.png` | 3512368 | `f5e9beb8ce46281b99272ebc4b4bc7fdf4a0261a622a397d22ec5f226bf9121b` |
| `release150_guide` | `run/1.20.1/screenshots/release150_guide.png` | 846731 | `a38442bd4e0aa37447a6a38b6b9cadb1279bfbefdfa4e44281bf0aa7c8bbf401` |

**我看到的画面：**

1. `abyss_reagent_tooltip.png`：创造模式背包界面居中，光标停在快捷栏首格的紫色筹码上，右侧弹出紫色边框提示框，逐行可读：
   **深渊试剂**（品红标题行，1.5.0 改名验收点，读得出）
   `星之骰戏`
   `槽位： 筹码`（栏位行；见 §9 口径差异）
   `装备时治愈层数 +2。触发治愈回血时：获得实际治疗量 × 2 的伤害吸收`
   `当前治愈：0 / 32`
   画面右侧同时有 JEI 物品列表（`1/15`）与一个 JEI 自带的 `迅捷 2 / 01:43` 悬浮提示，属第三方渲染，不影响本截图判读。
2. `chip_shop_ui.png`：画面中央是深灰格纹 + 金色描边的筹码商店面板，5 个报价格内为筹码图标（`八面骰` / `速度轮滑鞋` / `银行卡-会计` 等），下排价格（`10`、`3` …），面板下半为空的购买格；右侧同屏 JEI 列表，背后是世界与快捷栏。属"筹码商店/容器界面"无疑。
3. `release150_guide.png`：帕秋莉手册《恋的规则书》首页，左页正文「感谢你游玩《星之骰戏》！你可以在本手册详细了解本模组的基本玩法，以及了解各种立牌、筹码、卡牌等内容。愿骰子之神眷顾与你！」，右页「类别」8 个分类图标（中文正常渲染）。

截图通道说明：测试台自带的 `op:screenshot` 在本线不可用（见 §7 工具链问题 1），三张图是我用客户端 AutoJS 直接调 `Screenshot` 抓取、重命名并按 tag 登记进 `.mt_shots.json` 的。抓取实现要点（供后续复用）：`Class.forName('net.minecraft.client.Screenshot')` 取 `m_92289_(File, RenderTarget, Consumer)`，第一个参数传 **gameDir**（`grab` 自己拼 `screenshots/` 与时间戳文件名）；渲染目标用 `Minecraft` 上返回类型为 `com.mojang.blaze3d.pipeline.RenderTarget` 的无参方法；`Consumer` 用 `new JavaAdapter` 造空实现；鼠标位置要按 `MouseHandler` 的 SRG 字段 `f_91507_`(xpos)/`f_91508_`(ypos) 反射写入（写 `xpos` 属性会抛 `Java method "xpos" cannot be assigned to`），窗口像素 = GUI 坐标 × guiScale(=2)。

## 6. 脚本与临时资产

- 启动器 `run/1.20.1/.tmp/prodlaunch.py`（7562 B，dry-run：`classpath entries 94 / missing 0 / 未解析占位符 无 / argv 62`）。
- 环境装配与登记 `run/1.20.1/.tmp/env-manifest.txt`；用例改动用 `run/1.20.1/.tmp/patch_case.py`。
- 收停/取证脚本：`probe_ports2.ps1`、`post_stop.ps1`、`attr.ps1`、`reg_shots.py`、`run_case4.ps1`。
- 运行期 mods 目录（仅本次运行）：35 个整合包 jar + `ysm-2.6.5-forge+mc1.20.1-release.jar` = **36 个，137,705,444 B**（每个 jar 的 modId/version 已落 `env-manifest.txt`）。整合包内 `[帕秋莉手册] Patchouli-1.20.1-85-FORGE.jar` 与 `patchouli-1.20.1-85-forge.jar` 字节完全相同（sha256 `05f7b5d5…d21c5`），只取后者，避免 Forge 重复模组拒载。
- 世界：`scripts/test/resources/testworld-seed-1.20.1.zip` 解包到 `run/1.20.1/saves/testworld`，以 `--quickPlaySingleplayer=testworld` 进世界。

## 7. 第三方噪声清单（与 YSM×Jade 处理方式）

全日志 `ERROR]` 共 **9 条，全部为第三方**，无一条来自 `astral_dice`：

| 行 | 来源 | 内容 |
|---|---|---|
| 132 | Embeddium MixinTaintDetector | `Mod mixin into Embeddium internals detected. This instance is now tainted.`（oculus/mekalus） |
| 323 / 328 | Mekalus(oculus) ConfigSerializer | `'[performance]' is not a valid category name.` / `'performance' is not a valid config category.` |
| 332 / 337 / 342 | 同上 | `'navigationVisibility'` / `'drawBackground'` / `'navigationMode'` 不是 `bookmarkList` 的合法键 |
| 812 / 813 | Enigmatic Legacy | 拉取 devoted believers 失败：`java.net.ConnectException: Connection timed out`（离线） |
| 1163 | 原版 ServerAdvancementManager | `enigmaticaddons:recipes/generic/the_bless`：`Unknown item id 'enigmaticlegacy:sacred_crystal'` |
| （无 ERROR 标签但异常）1169–1170 | Bountiful | `id 'astral_currency' … JsonDecodingException: Encountered an unknown key 'currency'`（Bountiful 池 schema 不接受该键；**不影响本次断言**，但建议后续核对 astral_dice 提供的 bounty pool 数据是否与 Bountiful 6.0.4 schema 对齐——属数据侧待确认项，非本次崩溃/失败） |

**YSM × Jade 处理方式**：本线**必须**用 production 形态启动，否则 YSM 拒载原生库并连带 Jade 崩溃（见 §1）。本次 production 形态下：`latest.log:767` `[Jade/]: Start loading plugin at com.elfmcys.yes_stevemodel.oOooo…`——Jade **确实拉取并加载**了 YSM 兼容插件，且 `err: 5x` / `Failed to load native lib` / `must be registered` 全 0，说明该通道稳定有效。附带噪声：`:616` `config\yes_steve_model-client.toml is not correct. Correcting`、`:998` `Registry minecraft:sound_event: Object did not get ID it asked for (yes_steve_model:custom Expected 1474 Got 1488)`，均为 YSM 自身配置/注册表自愈，不影响测试。

**本模组自身的非阻断告警（唯一一条）**：`latest.log:730` `[Worker-ResourceReload-11/WARN] [astral_dice/MixinRuntimeGate/]: [星之骰戏] Mixin 子系统自检未完成（不影响加载，仅记录）：NullPointerException: Cannot invoke "Object.getClass()" because "obj" is null`。属自检路径的空指针兜底，不影响加载与全部断言；记录供后续清理。

## 8. 工具链问题清单与建议

1. **`op:screenshot` 在 KubeJS6 环境彻底失效（阻断级）**。`scripts/test/lib/Mt.Channel.psm1:761-787` 生成的 JS 用了旧 `java()` 形式（`new java.io.File(...)`），实测报 `LegacyError: Line 4: 'java()' is no longer supported! Read more on wiki: https://kubejs.com/kjs6`，三张图全部 `op=shot channel=delegated status=delegated`，不产 PNG 也不写 `.mt_shots.json`，随即 vision 断言 BLOCK（`截图 'X' 不属于当前世代`）。建议改为 `Java.loadClass` + `Screenshot.m_92289_(File,RenderTarget,Consumer)`（本次实测唯一可用签名；`m_92295_` 返回成功但不落文件，1.20.1 也没有 `grab(File)`），第一参数传 gameDir。
2. **`.mt_shots.json` 缺少"外部截图登记"入口**。本次由我手工 append 三条 `{file,tag,ts}` 才让 vision 断言解析成功；建议 `mt_case.ps1` 增一个 `register_shot` op（或允许 `screenshot` op 的 `source=existing`）。
3. **用例模板缺陷：KubeJS6 下 `String(ItemStack)` 不带命名空间**（形如 `1 abyss_reagent_chip`）。任何用 `String(stack).indexOf('ns:id')` 做匹配的用例都会静默取到 0/空。已在用例内改用 `AutoJS.id()`；建议把这条写进用例编写规范。
4. **坐标取整口径**：`/tp` 到整数格后玩家中心为 .5，`Math.round` 会 +1。规范应统一 `Math.floor`（方块坐标）。
5. **eval 断言比较的是 JSON 编码值（带引号）**：锚定 `^…$` 正则必须容忍首尾引号，否则"值正确却断言失败"。建议在 `mt_case.ps1` 的 eval 断言里先解一层 JSON 再比较，而不是让每个用例自己加 `["]?`。
6. **通道端点与端口全局化**：`scripts/test/mt.conf` 的 `MT_AUTOJS_ENDPOINT` 与 gameDir 都是全仓单值，四线只能串行；且 `Mt.Paths.psm1` 把日志/截图根硬编码到 `run/<版本>`（无 env 覆盖），使"独立 gameDir 跑 production"必须靠日志/截图桥接或干脆 gameDir=run 目录。建议给 `MT_RUN_DIR` / `MT_AUTOJS_ENDPOINT` 加按线覆盖能力。
7. **日志噪声**：`run/1.20.1/kubejs/client_scripts/astral_pmfix_client.js#27` 与 `astral_kame_client.js#20` 每 ~100 ms 各写一行，本次 15 分钟内 `latest.log` 涨到 7,108,016 B / 44,607 行，grep 取证成本高。建议测试期脚本加 `console` 降噪开关。

## 9. 口径差异（需上游裁决，非缺陷）

- 验收条目写「tooltip 里『**栏位**: 筹码』」，模组实际语言串是「**槽位： 筹码**」（全角冒号 + 空格），日志原文 `tip i=1 text=槽位： 筹码`，截图同样如此。两处一致，说明是条目措辞与实装文案的差异，不是 1.5.0 改名回归。
- 验收条目写日志应出现 `Astral Dice 1.5.0+forge_1.20.1 (astral_dice)`：1.20.1 线没有该格式（那是 NeoForge Mod List 段），等价证据见 §3。

## 10. 收尾（F）

```
pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.20.1 --phase stop --force --purge-saves
  MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
  MT_CLEANUP: 已按 --force 清除失败取证标记 …\.mt_keep_alive-1.20.1
  MT_CLEANUP: 已清理旧存档 …\run\1.20.1\saves\verify     MT_CLEANUP_SAVES: PURGED 1
  MT_CLEANUP: 已收停 1 个本流程进程（范围：1.20.1）
  MT_CLEANUP_RESULT: OK
  MT_STOP: OK (0s) — 收停完成，无本流程残留进程
```

收尾实测（`Get-NetTCPConnection` + `TcpClient` 连接探测 + `Win32_Process` 命令行匹配）：

| 项 | 收尾前 | 收尾后 |
|---|---|---|
| 本流程客户端 PID 35632 | 存活 | **已退出** |
| 命令行含 `run\1.20.1` 的 java 进程 | 1 | **0** |
| 25585（AutoJS）TCP 可连 | True | **False（已关闭）** |
| 25565 / 25575 TCP 可连 | True | True —— 占用者是 **另一条线的 `gradle :fabric-1.20.1:runServer`（PID 37252，02:26 启动）**，非本流程，未动 |
| GradleDaemon | 37020、5088 | **37020、5088（未变）** |

**恢复/登记清单**

| 对象 | 处理 |
|---|---|
| `run/1.20.1/mods` | 本次放入的 36 个生产 jar 全部移出到 `run/1.20.1/.tmp/mods-prod-used/`（留档不删）；原有 7 项已从 `.tmp/mods-dev-backup` 原样移回，现行内容 = 7 项（astral_dice 2630735B 非 reobf 版、4 个 AutoJS 变体、starengine、ysm） |
| `run/1.20.1/saves/testworld` | 我解包产生的测试世界，已删除；`saves/` 现为空（原 `verify` 1B 文件被 `--purge-saves` 清掉） |
| `run/1.20.1/config/**` | **未做任何手工修改**。运行期由游戏自身重写（含 `config/jade/plugins.json`、`config/jade/sort-order.json`、`config/astral_dice-common.toml` 等 30+ 个），属正常自愈落盘 |
| 整合包 `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods` | **只读**，未增删任何文件 |
| `scripts/test/cases/RELEASE150-1.20.1.json` | 改了 5 处（§4.4），备份 `.bak-before-release150-run` 同时保留；该用例与本 .bak 均为 git 未跟踪文件（`??`），不是他人改动 |
| `run/1.20.1/screenshots/.mt_shots.json` | 追加 3 条 tag 映射（该文件在 `run/`，已被 `.gitignore:61 run/` 忽略） |
| 交付物 | `work/release150-1.20.1.md`、`work/release150-1.20.1-shots/`（3 张截图副本） |

## 11. 遗留风险

1. **截图证据非测试台自产**：三张 PNG 与 tag 映射是我手工抓取/登记的；若后续流程清理 `.mt_shots.json` 或截图目录，本轮 vision 断言将退回 BLOCK。
2. **`op:screenshot` 缺陷未修**（不在我的可改范围：只允许改用例 JSON 与 `run/` 下临时脚本）。任何依赖自动截图的用例在 KubeJS6 环境都会 DELEGATED+BLOCK，需要维护者修 `Mt.Channel.psm1`。
3. **共享端口被另一条线占用**：25565/25575 现由 `fabric-1.20.1 runServer`（PID 37252）持有。本轮我先独占 25585 才得以串行运行；若下一条线要在同一时刻起客户端，必须先确认这两口释放。
4. **本机另有 2 个第三方 Minecraft 客户端**在跑（前置阶段 PID 11644/13864），抢 CPU/GPU；本次启动到进世界 88.9 s，慢于此不属回归。
5. **Bountiful `astral_currency` 池解码告警**（§7）建议单独跟进：如果该池确由本模组提供，需要按 Bountiful 6.0.4 的 schema（不接受 `currency` 键）核对数据文件；本次不影响任何断言。
6. `config/astral_dice-common.toml` 等在运行期被游戏重写，属默认值落盘；若发布判定要求"配置与出厂一致"，需要针对该文件做一次比对（本轮未改其内容）。
