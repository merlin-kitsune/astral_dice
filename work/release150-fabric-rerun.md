# RELEASE150 · fabric-1.20.1 复跑与两处缺陷修复报告

- 时间：2026-11-02 05:53–06:03 (+08:00)
- 现场：fabric-1.20.1 dev 客户端（java pid 20652，`net.fabricmc.devlaunchinjector.Main`，testworld），AutoJS 端点 `http://127.0.0.1:25585/mcp`（side=server/client 双 available）
- 目标用例：`scripts/test/cases/RELEASE150-fabric-1.20.1.json`（channel=autojs，side=client）
- 最终结果：**MT_CASE_RESULT: RELEASE150-fabric-1.20.1 = PASS**（24 条机器断言 PASS + 1 条 crash 断言 INCONCLUSIVE + 2 条 vision →VIS；0 FAIL / 0 BLOCKED）

## 1. 结论

| 项 | 状态 | 证据 |
|---|---|---|
| 缺陷 1（测试台判据：fabric 平台行被判成 echo） | 已修 | `scripts/test/mt_assert.ps1:405` Test-MtPlatformLine 三条判据 + `mt_assert.ps1:470` Test-MtEvalSourceEcho 两条判据；11 样本自测全绿 |
| 缺陷 2（用例探针取到注入前的状态） | 已修 | 新增 await_state「先等后拍」+ state/chip/screen 三探针重写；机器行 pos=100,105,100 chips=3 item=astral_dice:abyss_reagent_chip |
| 附加（探针在 1.20.1 用了错误 API，导致第 3 张截图内容错位） | 已修 | `ResourceLocation.fromNamespaceAndPath` → `new ResourceLocation(ns, path)`，guide_open=ok |
| validate | MT_VALIDATE: OK | 见 §6 |
| 实跑 | PASS | `fabric-1.20.1/.tmp-case-20261102-060223.log`（另有 060044 轮同为 PASS） |
| 三条截图取证 | 通过 | bytes>0、mtime 在本轮窗口内、sha256 见 §7、.mt_shots.json 三条 tag 齐全 |
| 本模组 WARN/ERROR 归属 | 无本模组 ERROR | 6 条 WARN 全部良性/环境级；3 条 ERROR 均第三方（见 §8） |

## 2. 缺陷 1：判据修复（只增不减）

文件：`scripts/test/mt_assert.ps1`（改前 sha256 `caa247d02ae5d3509ff0fa47cf30eb79f260df2fff6265447d8fb78d1f6a4e40`，改后 `d62c47adc7337133d8a31cd24b067cf2…`）
备份：`scripts/test/mt_assert.ps1.bak-fabricplatform-20261102-055800`

`Test-MtPlatformLine`（L405）由「一条 return」扩成**三条判据任一成立**，原口径仍排第一：

1. neo/forge（原样保留，L433）：`^\uFEFF?\[[^\]]+\]\s*\[[^\]]+/[A-Za-z]+\](\s*\[[^\]]*\])?\s*:`
2. fabric 圆括号来源（新增，L451）：`^\uFEFF?\[[^\]]+\]\s*\[[^\]]+/[A-Za-z]+\]\s*\([^)]+\)` —— 来源之后跟什么都算正文，**不要求冒号**
3. FabricLoader 模组清单纯数据续行（新增，L465）：`^\t- [A-Za-z0-9_.\-]+ \S+\s*$`

依据是实测的三种 fabric 真平台行（`fabric-1.20.1/run/client/logs/latest.log`）：

```
[05:51:33] [Server thread/INFO] (KubeJS Server) autojs-eval#23: [REL150] ver=fabric-1.20.1 ...
[05:51:33] [Render thread/INFO] (KubeJS Client) autojs-eval#19: [REL150] tip i=0 text=...
[05:51:35] [Server thread/INFO] (ChipShopOpener) [astral_dice] CHIP_SHOP_OPEN player=...   ← 来源后无冒号，判据一必落 echo
[05:49:55] [main/INFO] (FabricLoader) Loading 87 mods:                    ← latest.log:2
\t- astral_dice 1.5.0+fabric_1.20.1                                       ← latest.log:5，续行无时间戳
```

**为什么不会把 AutoJS 的 eval 源码回显放行成强证据**：回显行形状确实是 `[时间戳] [AutoJS-Mcp/INFO] (AutoJS/Eval) [side] (function(){…})()`，但分类点未动（`mt_assert.ps1:721-723`）—— `$isStrong = (-not $isSourceEcho) -and (Test-MtPlatformLine …)`，而 `Test-MtEvalSourceEcho`（L470）新增第二条判据 `\(AutoJS/Eval/?\)`（L498）按**来源名**识别 fabric 形状回显（neo/forge 的 `[AutoJS/Eval/]: … JSON.stringify((` 判据保留在 L491），命中即 `platform-source-echo` = 弱证据，永远凑不成 PASS。

自测：`temp/selftest_platformline.ps1`（dot-source 断言脚本，11 条样本，逐条比对期望 kind）：

```
neo-platform platform / forge-platform platform / fabric-platform platform / fabric-platform-nocolon platform / fabric-loader-modlist platform
indented-continuation echo / kubejs-source-echo-2space echo / fml-modlist-2tab echo / stack-frame echo
autojs-source-echo platform-source-echo / autojs-source-echo-legacy platform-source-echo
MT_SELFTEST: ALL 11 SAMPLES OK   (exit 0)
```

## 3. 缺陷 2：用例探针修复（真根因三条，均有 live 实测）

用例：`scripts/test/cases/RELEASE150-fabric-1.20.1.json`（改前 sha256 `67ef9edcd0c80e1f780ca32f4ce0fdd5039440f184cb5ade020ecccacee2eb1c`，15642 B / 31 steps；改后 `4b72e0061404790a…`，17632 B / 32 steps）
备份：`scripts/test/cases/RELEASE150-fabric-1.20.1.json.bak-fabricprobe-20261102-055800`

1. **fabric 运行时 `String(ItemStack)` 不带命名空间** —— 实测 `["1 abyss_reagent_chip","1 dice","1 attack_card_medium",…]`，旧探针 `String(inv[i]).indexOf('astral_dice:abyss_reagent_chip')` 恒 0 命中（chips=0），`String(held).replace(/^[0-9]+ /,'')` 得到裸 `dice`。正解：`AutoJS.id(stack)` 返回 `astral_dice:abyss_reagent_chip` / `astral_dice:dice`。
2. **旧探针 `Math.round` 且 `AutoJS.inventory()` 只含非空堆、index≠槽位** —— 玩家站在 100.5/100.5，round 得 101；`selected` 落在 3（= 骰子格）所以 `item=dice`。正解：`Math.floor`；用 `getInventory().getContainerSize()`(=41) 遍历真实槽位，找首个落在快捷栏(<9)的筹码槽后 `inv.selected = chipSlot` 把选中格对到筹码格，再 `AutoJS.id(AutoJS.held(p))` 读真实手持。
3. **chip 探针用 `new ItemStack(item)` 计数恒 1、且纯字符串返回被 JSON 编码带引号** —— 断言要 `count=3`（筹码 maxStackSize=1，`/give … 3` 得 3 个独立 1 堆）、要显示名不带引号。正解：遍历真实堆求和 + 取首个真实堆出 `getHoverName()`/tooltip；探针 return **对象** `{ok,id,count,name,tipn,path,slot}` / `{ok,screen}`，断言改字段口径（与已通过的 1.21.1 用例一致）。

同时按「先等后拍」新增 readiness 步：`wait 800` 之后插 `await_state`（side=server，`timeout_ms 8000` / `interval_ms 250`），条件 = 四件物品全到齐（筹码合计 ≥3、dice、attack_card_medium、jill_sign）且 `gamemode=creative`；求值必须返回布尔（`-Mode Bool` 下 `!!()`；字符串 "false" 判假）。

未改动的部分（守约束）：未删任何断言（27 条全在）、未放宽任何阈值（models_min 120 / recipes_min 100 / signs_min 25 / trinkets_min 6 / entries_min 1800 / bbox 等原样）、未把断言改成注释、未改另三条线用例（`RELEASE150-1.21.1.json` mtime 2026-11-01T19:53Z、`RELEASE150-1.20.1.json` 20:56Z、`RELEASE150-26.1.2.json` 20:57Z，均早于本次会话 21:53Z）、未动 `scripts/test/mt.conf`、未改 `.java`/资源、未做任何 git 写操作。

## 4. 附加修复：guide 截图的 API 版本错

旧探针在 1.20.1 上调用了 1.21+ 才有的 `ResourceLocation.fromNamespaceAndPath`，实测 `guide_open=ERR:InternalError: Java class "net.minecraft.resources.ResourceLocation" has no public instance field or method named "fromNamespaceAndPath"`（`latest.log:1764`、`:2720`），帕秋莉手册没打开，第 3 张截图内容与第 2 张重复。改为 1.20.1 构造器后实测 `guide_open=ok`，screen 变为 `vazkii.patchouli.client.book.gui.GuiBookLanding`（`latest.log:3470`）。该步本身不参与任何断言（用例 note 明确「打开失败不作 PASS 判据，只留档」），修复只提升取证质量。

## 5. 复跑结果

```
$ pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case scripts/test/cases/RELEASE150-fabric-1.20.1.json
MT_VALIDATE: OK — RELEASE150-fabric-1.20.1

$ pwsh -NoProfile -File scripts/test/mt_case.ps1 run --version fabric-1.20.1 --case scripts/test/cases/RELEASE150-fabric-1.20.1.json
  …（24 条 [PASS] 机器断言）
  [INCONCL] 57. assert — MT_ASSERT_CRASH: INCONCLUSIVE — crash-reports 目录不存在（fabric-1.20.1/run/client/crash-reports）
  [ →VIS] 58. assert — VISION_PATH=…/abyss_reagent_tooltip.png | 画面里是否看得到物品提示框（tooltip）或背包界面？
  [ →VIS] 59. assert — VISION_PATH=…/chip_shop_ui.png | 画面是否是筹码商店/容器界面？
MT_CASE_RESULT: RELEASE150-fabric-1.20.1 = PASS
MT_CASES_SUMMARY: RELEASE150-fabric-1.20.1=PASS
```

- 最终轮日志：`fabric-1.20.1/.tmp-case-20261102-060223.log`（16159 B）
- 上一轮（guide 修复前，同为 PASS）：`fabric-1.20.1/.tmp-case-20261102-060044.log`（18768 B，由 temp/mt_run_fabric_20261102-060044.log 归档）
- crash 断言 INCONCLUSIVE 的原因：本线 `run/client/crash-reports` 与 `run/crash-reports` **都不存在**（从未崩过，目录不会被创建），属工具既有行为，不是 FAIL；判据未被放宽。
- 现场 `[.../INFO]` 机器行（本轮，latest.log:3011）：
  ```
  [06:02:25] [Server thread/INFO] (KubeJS Server) autojs-eval#30: [REL150] ver=fabric-1.20.1 gamemode=creative pos=100,105,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+fabric_1.20.1
  ```
  （y=105 因上一轮跳落/站立高度不同；断言只约束 x/z=100 与 y 为整数，故不受影响。）

## 6. 取证

### 6.1 三条截图（本轮新产，窗口起点 06:02:2x）

| tag | bytes | mtime (+08:00) | sha256（前 16 位） |
|---|---|---|---|
| abyss_reagent_tooltip | 621964 | 2026-11-02 06:02:26 | `9b894b3dfaff9bac` |
| chip_shop_ui | 460564 | 2026-11-02 06:02:28 | `e21fe636d66f6ccc` |
| release150_guide | 327205 | 2026-11-02 06:02:30 | `18f1a6d4c7f87e1c` |

- 全部 1920×1080、可正常打开；`screenshots/.mt_shots.json` 三条 tag 齐全（ts 1793570547.093 / …548.8 / …550.411，run_id 20260926-230116）。
- 上一轮（06:00:47/49/51，sha256 `8a90ccb70d74c119`/`ed3814cbfb077105`/`d80ebcdef72faa09`）的 PNG 已按「不删历史、改名轮转」复制为 `*.prev-run0600.png` 留存。
- 画面内容：① 背包/创造页界面 + 底部快捷栏三枚筹码（tooltip 内容由 `[REL150] tip i=…` 机器行单独作证）；② 筹码商店界面（金色边框 5 个报价槽 + 价格 10/…）；③ 帕秋莉手册「恋的规则书 · 类别」页（本轮 guide 修复后真正打开）。

### 6.2 探针行原文（本轮 latest.log）

```
[06:02:25] [Server thread/INFO] (KubeJS Server) autojs-eval#19: [REL150] ready chips=3 dice=true medium=true sign=true gamemode=creative
[06:02:25] [Render thread/INFO] (KubeJS Client) autojs-eval#31: [REL150] chip id=astral_dice:abyss_reagent_chip count=3 name=深渊试剂 tipn=7 path=inventory slot=0
[06:02:25] [Render thread/INFO] (KubeJS Client) autojs-eval#32: [REL150] tip i=0 text=深渊试剂
[06:02:25] [Render thread/INFO] (KubeJS Client) autojs-eval#32: [REL150] tip i=1 text=槽位：筹码
[06:02:25] [Render thread/INFO] (KubeJS Client) autojs-eval#32: [REL150] tip i=3 text=装备时治愈层数 §e+2§7。触发治愈回血时：获得实际治疗量 × §e2§7 的伤害吸收
[06:02:25] [Render thread/INFO] (KubeJS Client) autojs-eval#32: [REL150] tip i=5 text=当前治愈：§e0§7 / §e32§r
[06:02:25] [Render thread/INFO] (KubeJS Client) autojs-eval#32: [REL150] tip i=6 text=§9§oAstral Dice
[06:02:27] [Server thread/INFO] (ChipShopOpener) [astral_dice] CHIP_SHOP_OPEN player=AstralTester offers=energy_recycler_chip+boxing_gloves_low_chip+advanced_peripherals_chip purchased=0 refreshes=3
[06:02:28] [Render thread/INFO] (KubeJS Client) autojs-eval#5: [REL150] screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen
[06:02:28] [Render thread/INFO] (KubeJS Client) autojs-eval#9: [REL150] guide_open=ok
```

（对照：修复前同口径行的错误形态 —— `pos=101,104,101 … chips=0 item=dice …`（latest.log:1303）、`chip … count=1 … path=screen`（:1332）。）

## 7. 本模组 WARN/ERROR 归属逐条判定（latest.log，本轮全量）

本模组相关 WARN 共 6 条，全部**非功能缺陷**：

| 行 | 原文（截断） | 归属判定 |
|---|---|---|
| 98 | `[main/WARN] (FabricLoader/Mixin) Reference map 'astral_dice.refmap.json' for astral_dice.mixins.json could not be read. If this is a development environment you can ignore this message` | 开发环境常态（Loom dev 不产 refmap），jade / trinkets 三个 mixin 配置同源同因（98–100）；三条线一致 |
| 99 | 同上，`astral_dice.jade.mixins.json` | 同一环境事实 |
| 100 | 同上，`astral_dice.trinkets.mixins.json` | 同一环境事实 |
| 142 | `[Render thread/WARN] (astral_dice-version-gate) [Astral Dice] 版本互通门槛:类路径上找不到 /astral_dice_version.properties,改用加载器元数据解析版本号` | 本模组自身设计内的降级路径（写版本属性文件只在发布 jar 里），dev 下预期出现，且解析结果正确（modver=1.5.0+fabric_1.20.1） |
| 297 | `[Render thread/WARN] (Minecraft) Missing data pack mod:starengine` | 原版对「世界 datapack 清单里列了、但当前未启用的模组数据包」的常规提示（同一批 290–312 共 20+ 条覆盖 kubejs/jei/forge 等），testworld 固定清单所致，非本模组行为 |
| 298 | `[Render thread/WARN] (Minecraft) Missing data pack mod:astral_dice` | 同上（该行由原版 Minecraft 打，来源不是本模组） |

ERROR 共 3 条，**均非本模组**：

| 行 | 原文 | 归属判定 |
|---|---|---|
| 347/348 | `[Server thread/ERROR] (Minecraft) Tried to load unrecognized recipe: enigmaticlegacy:the_acknowledgment removed now.` | 第三方 Enigmatic Legacy 数据包配方在 x 版本被移除（来源=Minecraft 原版加载器，对象=enigmaticlegacy 的 id），与本模组无关，重复两条 |
| 357 | `[Render thread/ERROR] (SyncEntireContainer) Unable to Sync Container Data for a given Entity as its Capability is null on the Client! [EntityId: 2]` | 归属 **io.wispforest.accessories**（Accessories 模组）—— debug.log:5891 的完整 logger 名是 `io.wispforest.accessories.networking.client.SyncEntireContainer`；发生在 05:50:06 进服握手期（用例尚未开始），属该模组既有的「客户端 capability 未就绪」噪声 |

=> 无本模组 ERROR，**不需要停手上报产品问题**。

## 8. 现场核查与硬约束遵守

- 端口 25585 只有 pid 20652 的 dev 客户端（`/health` 200，side server/client 双 available，状态 `1 player(s) online`），未启动第二个实例。
- 未执行任何 git add/commit/push/tag；未跑 Gradle 构建任务；未改 `.java`/资源/版本号/依赖；未动 `scripts/test/mt.conf`。
- 工作树里 `fabric-1.20.1/src/generated/…` 的 `D` 项与 `scripts/test` 下其它 `M` 项均为**接手前既有**改动，本报告未触碰。
- 历史日志与旧截图未删除（旧截图改名 `*.prev-run0600.png` 轮转）。

## 9. 复现命令

```bash
pwsh -NoProfile -File temp/selftest_platformline.ps1                                    # 判据 11 样本自测
pwsh -NoProfile -File scripts/test/mt_case.ps1 validate --case scripts/test/cases/RELEASE150-fabric-1.20.1.json
pwsh -NoProfile -File scripts/test/mt_case.ps1 run --version fabric-1.20.1 --case scripts/test/cases/RELEASE150-fabric-1.20.1.json
```
