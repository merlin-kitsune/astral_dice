# Waystone 联动结论修正：6 份发布文档修订记录（1.5.0）

日期：2026-11-02　工作树：`D:/MCProject/astral_dice_multiloader`（Windows）
范围：把上一批（`work/release-docs-waystone-update.md` 记录的 6 个文件）里「NeoForge 26.1.2 上「跃迁引擎 × Waystone（传送石碑）」联动注册失败 / 功能缺席」的结论，改为**已通过 Balm 事件通道注册成功、功能与 1.21.1 一致**，并保留「为什么不能走 NeoForge 总线」作为实现差异记录。

## 0. 结论一句话

26.1.2 的 Waystone 联动**已正常工作**，与 1.21.1 行为一致：同维度传送照常给跃迁引擎充能与迅捷，跨维度传照常按 Waystone 传送结算。上一批的「注册失败/功能缺席」是**错误结论**，本次全部文档已按实机证据改写完成。

## 1. 逐文件 diff 摘要

| # | 文件 | 行尾 | 改前 | 改后 | 主要改动 |
|---|---|---|---|---|---|
| 1 | `release/1.5.0/PLAYER_CHANGELOG_ZH.md` | CRLF | 408 行 / 54,351 B | 409 行 / 54,256 B | 删 1 行旧错误条目；在「## 平台差异」前新增 1 条玩家可读条目（+1 空行）；「五处」→「四处」、「五点」→「四点」 |
| 2 | `release/1.5.0/PLAYER_CHANGELOG.md` | CRLF | 415 行 / 56,459 B | 416 行 / 56,359 B | 同上（英文）：「five behaviours」→「four behaviours」，「the five listed under」→「the four listed under」 |
| 3 | `release/1.5.0/PLAYER_CHANGELOG_MCMOD.html` | LF | 385 行 / 56,902 B | 388 行 / 56,817 B | 删旧 `<li>`；新建独立 `<ul>` 块放新条目（+3 行）；「但有五处行为」→「但有四处行为」、「列出的五点」→「列出的四点」 |
| 4 | `CHANGELOG.md` | CRLF | 2865 行 / 565,091 B | 2850 行 / 566,353 B | `### Platform differences and known issues` 条目 17 行→5 行（含机制说明）；`### Engineering` 受测集 95→106 模组并补实机证据 |
| 5 | `CHANGELOG_ZH.md` | CRLF | 2435 行 / 505,495 B | 2424 行 / 506,779 B | 同上（中文）：`### 平台差异与已知问题` 条目 14 行→5 行；`### 工程` 受测集与证据 |
| 6 | `docs/porting/MAPPING-1.5.0.md` | LF | 330 行 / 60,335 B | 332 行 / 62,024 B | 差异表第 31 行（`:113`）n26 列改写 + 性质改「平台替代」；R-17（`:185`）改写为已收口；§6 第 16 项与计数、§6.1 受测集（`:306`）+ 新增 2 条 bullet |

行数差与编辑量自洽性交叉核对：

- PLAYER_ZH：−1（删旧条）+2（新条 + 空行）= +1 ✓；PLAYER：同 ✓；MCMOD html：−1 +4 = +3 ✓
- CHANGELOG.md：平台条目 −17+5=−12，工程条目 −5+2=−3，合计 −15 ✓（2865→2850）
- CHANGELOG_ZH.md：−14+5=−9，工程 −4+2=−2，合计 −11 ✓（2435→2424）
- MAPPING：§6.1 新增 2 条 bullet = +2 ✓（330→332）

### 1.1 玩家侧三份（不出现 Java 类名 / 栈）

- 删除的旧条目：原 `PLAYER_CHANGELOG_ZH.md:372`（「…联动无法接入：受上游事件机制限制…」）、原 `PLAYER_CHANGELOG.md:379`（「…linkage cannot be hooked in…」）、原 `PLAYER_CHANGELOG_MCMOD.html:348`（同文 `<li>`）。
- 新增条目（中文，`PLAYER_CHANGELOG_ZH.md:364`，MCMOD html `:342` 同文但包为 `<li><b>…</b>：…</li>`）：

```
- **NeoForge 26.1.2 上「跃迁引擎 × Waystone（传送石碑）」的联动已正常启用**（2026-11-02）：用传送石碑传送会照常被当作「Waystone 传送」结算 —— 同维度传送照常给跃迁引擎充能与迅捷，跨维度传送照常按 Waystone 传送处理；NeoForge 1.21.1 / Forge 1.20.1 / Fabric 1.20.1 三线行为与之一致，四条线没有差异。
```

- 新增条目（英文，`PLAYER_CHANGELOG.md:371`）：

```
- **On NeoForge 26.1.2 the “Warp Engine × Waystone” linkage is now enabled** (2026-11-02): a teleport-stone teleport is settled as a “Waystone teleport” there - a same-dimension teleport charges the Warp Engine and grants Speed as usual, and a cross-dimension one runs the Waystone path. NeoForge 1.21.1, Forge 1.20.1 and Fabric 1.20.1 are unchanged, so all four lines now behave the same.
```

- 计数回退（该条目已不属「平台差异」，故差异回到 4 条）：`PLAYER_CHANGELOG_ZH.md:368`「但有四处行为」、`:377`「列出的四点」；`PLAYER_CHANGELOG.md:375`「but four behaviours differ」、`:384`「the four listed under」；`PLAYER_CHANGELOG_MCMOD.html:346`「但有四处行为」、`:356`「列出的四点」。
- 新条目落在 `## BUG 修复` 节之后、`## 平台差异（如实说明）` 之前（MCMOD html 为独立 `<ul>` 块 `:341-343`），`## ` 小节数与 `- ` 顶层条目数均未变 → 不影响 parity。

### 1.2 工程侧两份 CHANGELOG

- `CHANGELOG_ZH.md:665` 新首行：「**NeoForge 26.1.2 上 Waystone（传送石碑）联动改为经 Balm 事件通道注册，已实机验证生效**（2026-11-02 修正本批先前的错误结论）：…」（1 条顶层 `- ` + 4 行两空格续行）。续行写清：
  - 为什么不能走 NeoForge 总线：Balm bound custom event 的抽象父类链以 `java.lang.Object` 为直接父，`EventBus.validateAbstractChain(EventBus.java:301)`（NeoForge `bus-8.0.5`）直接抛 `IllegalArgumentException: Abstract event class ... has a non-abstract superclass class java.lang.Object.` ⇒ 这些事件在 `NeoForge.EVENT_BUS` 上没有任何可用的注册路径。
  - 正确通道：事件类自带 `public static final BidirectionalEventMapper<Consumer<X>> EVENT`；`BidirectionalEventMapper` 实现 `net.blay09.mods.balm.platform.event.EventMapper`，其 `register(TCallback)` 才是 Balm 注册入口。
  - 本版实现（`neoforge-26.1.2/.../event/WaystoneWarpCompat.java`）：`:161` `registerWaystoneTeleportEvent`、`:180` `findBalmEventMapper`（反射读 `EVENT` + 接口名判定）、`:205` / `:223` `EventMapper.register`、`:169-172` 回退 `NeoForge.EVENT_BUS.addListener`、`:49` `TELEPORT_PRE_EVENT_CANDIDATES`、`:127` `isPlatformUnregistrable`（仅平台确实不可注册时 WARN→DEBUG）；1.21.1 仍走总线 = 同文件 `:92-94`。
  - 实机证据：`run/26.1.2/prodgame/logs/latest.log:525` INFO 行、NeoForge 26.1.2.109、构建 2,879,891 B / sha256 `0a069d24b94fe33df891c9fdc3a2b978fd9c3b1c23e0b2bf41e6f9fd75a80fcd`、无 `Failed to register Waystone` / 无 `not registrable`。
- `CHANGELOG_ZH.md:673-674`（`### 工程`）：受测集改为**本轮 106 个模组**（含 `waystones-neoforge-26.1.2-26.1.2.16`、`rhino`、`better-advanced-tooltips`），排除 `mru`（要 ≥ 26.1.2.114，测试台 .109）与 `sounds`（同源）；`RELEASE150-26.1.2` = **PASS**（25 条机器断言 + 2 条视觉断言）。
- `CHANGELOG.md:808` / `:816-817` 为同构英文版（首行「The Waystones integration on NeoForge 26.1.2 now registers through Balm’s event channel and is verified live (2026-11-02; corrects the earlier conclusion in this batch)」）。

### 1.3 `docs/porting/MAPPING-1.5.0.md`

- 差异总表第 31 行 `:113`（跃迁引擎 × Waystone（传送石碑）传送联动）：
  - n26 列改为「经 Balm 事件通道注册」实现描述：`registerWaystoneTeleportEvent`（`:161`）→ `findBalmEventMapper`（`:180`，反射 `EVENT` + 判接口 `net.blay09.mods.balm.platform.event.EventMapper`）→ `EventMapper.register(...)`（`:205` / `:223`）→ 未命中回退 `NeoForge.EVENT_BUS.addListener(...)`（`:169-172`）；候选链 `:49`、`isPlatformUnregistrable` `:127`、成功 INFO `:71`；并保留「为什么不走 NeoForge 总线：`EventBus.validateAbstractChain(EventBus.java:301)` 拒绝」。
  - 差异性质列从「**缺失**」改为 §1.3 五词之一的「**平台替代**（26.1.2 改走 Balm 事件通道注册；行为与基准线一致…）」；表行仍为 7 个 cell 且以 `|` 结尾（结构未破）。
- R-17 `:185`：风险描述改为「**26.1.2 的「跃迁引擎 × 传送石碑」联动已通过 Balm 事件通道注册成功（2026-11-02 收口，上一批「该联动不可用」的判定已作废）**」，影响列「无玩家可见差异…」，需同步列「否」，证据列给出源码行号 + `latest.log:525` 原文行 + NeoForge 26.1.2.109。
- §6 第 16 项 `:298` 末句：「顺带确认 Waystone 联动仍打 INFO「Waystone teleport integration enabled」（R-17 已收口，不应出现 WARN/DEBUG 降级）」；计数行 `:300` **仍为 16 项**（`mru`/`sounds` 共存仍未验证，不应虚假减少）。
- §6.1 `:306` 受测集 → 「本轮 **106 个模组**… − `mru` − `sounds`」（注明上一批「玩家实例 95 件」为早期口径）；`:309` 新增本轮结果 bullet（PASS、构建字节数与 sha256、平台 .109）；`:310` 新增 Waystone 证据 bullet（第 525 行原文、无 `Failed to register Waystone` / 无 `not registrable`）。

## 2. 自检：命令原文与结果

### 2.1 CHANGELOG 中英一致性

```
$ python tools/check_changelog_parity.py
WARN P2 ## 1.3.0: entries equal (58/58) but bold-start counts differ EN=53 ZH=40 (historical style difference; not a failure)
WARN P2 ## 2.0.0-SNAPSHOT.5: entries equal (2/2) but bold-start counts differ EN=2 ZH=1 (historical style difference; not a failure)
CHANGELOG_PARITY: OK sections=26 entries=787/787
EXIT=0
```

> 基线对照：对 `HEAD` 版本（`work/_parity_head/` 临时导出）跑同一门禁得 `CHANGELOG_PARITY: OK sections=26 entries=780/780`；工作树 787 是上一批（未提交）累积所致。本次系列替换均保持「每 `##` 段 1 条顶层 `- `、`###` 小节数不变」，故本次 delta = **0**（787→787，sections 26→26）。两条 WARN 为历史样式差异，非失败。

### 2.2 行尾 / 裸 CR / BOM（最终态）

```
[EOL] 文件                                           bytes   crlf  lf_only  bare_cr  bom
release/1.5.0/PLAYER_CHANGELOG_ZH.md                 54256   409   0        0        False
release/1.5.0/PLAYER_CHANGELOG.md                    56359   416   0        0        False
release/1.5.0/PLAYER_CHANGELOG_MCMOD.html            56817   0     388      0        False
CHANGELOG.md                                         566353  2850  0        0        False
CHANGELOG_ZH.md                                      506779  2424  0        0        False
docs/porting/MAPPING-1.5.0.md                        62024   0     332      0        False
```

结论：四个 CRLF 文件 `lf_only=0`、`bare_cr=0`；两个 LF 文件 `crlf=0`、`bare_cr=0`；六份均无 BOM。行尾形态与改前一致。

### 2.3 过时表述扫描

扫描模式：`注册失败` / `无法注册` / `功能缺席` / `无法接入` / `注册不上` / `指路石` / `cannot be hooked in` / `cannot register on NeoForge` / `five behaviours` / `five listed`。

逐文件结果（六份目标文档）：

```
release/1.5.0/PLAYER_CHANGELOG_ZH.md       hits=0
release/1.5.0/PLAYER_CHANGELOG.md          hits=0
release/1.5.0/PLAYER_CHANGELOG_MCMOD.html  hits=0
CHANGELOG.md                               hits=0
CHANGELOG_ZH.md                            hits=0
docs/porting/MAPPING-1.5.0.md              hits=0
```

（已修的两处初稿命中：`CHANGELOG_ZH.md` 的「一个都注册不上」→「没有任何可用的注册路径」；MAPPING R-17 的「原判「注册不上」已作废」→「上一批「该联动不可用」的判定已作废」。）

> 说明：本报告自身包含上述扫描模式的字面（模式清单与被删措辞的转述），对 `work/release-docs-waystone-final.md` 单独跑同一扫描会命中 28 处——这属元描述，不是发布文档内容；判定口径只看上表六份。

### 2.4 正向断言（关键句存在性）

```
[POS]
release/1.5.0/PLAYER_CHANGELOG_ZH.md       四处行为                       x1
release/1.5.0/PLAYER_CHANGELOG_ZH.md       列出的四点                      x1
release/1.5.0/PLAYER_CHANGELOG.md          four behaviours differ         x1
release/1.5.0/PLAYER_CHANGELOG.md          the four listed under          x1
release/1.5.0/PLAYER_CHANGELOG_MCMOD.html  但有四处行为                     x1
release/1.5.0/PLAYER_CHANGELOG_MCMOD.html  列出的四点                      x1
CHANGELOG.md                               now registers through Balm     x1
CHANGELOG.md                               latest.log:525                 x1
CHANGELOG.md                               106 mods                       x1
CHANGELOG_ZH.md                            改为经 Balm 事件通道注册           x1
CHANGELOG_ZH.md                            latest.log:525                 x1
CHANGELOG_ZH.md                            106 个模组                      x1
docs/porting/MAPPING-1.5.0.md              经 Balm 事件通道注册              x1
docs/porting/MAPPING-1.5.0.md              106 个模组                      x1
docs/porting/MAPPING-1.5.0.md              R-17 已收口                     x1
```

及表结构：`row31 cells=7`、`endswith_pipe=True`、`rows_total=152`。

### 2.5 实机证据复核（只读）

- `run/26.1.2/prodgame/logs/latest.log`（共 1870 行）第 **525** 行：

```
[0211月2026 05:42:39.922] [modloading-worker-0/INFO] [com.merlinkitsune.astral_dice.event.WaystoneWarpCompat/]: [Astral Dice] Waystone teleport integration enabled for 跃迁引擎.
```

  （`:524` 为 NeoForgeBalmConfig craftingtweaks 行、`:526` 起为 titanium 扫描 ⇒ 行号上下文吻合。）
- 日志全文无 `Failed to register Waystone`、无 `not registrable`。
- `neoforge-1.21.1/.../event/WaystoneWarpCompat.java` 未被本批修改（仍 `:92-94` 走 `NeoForge.EVENT_BUS`）—— 基线线与 26.1.2 的行为一致性由两端实现共同保证。

## 3. 未动的东西（边界）

- 未执行 `git add` / `commit` / `push` / `tag`；未跑 Gradle；未改任何 `.java` / 资源；未触碰 `release/1.5.0-rc.*`；未改 `tools/` 下任何脚本。
- 工作树里他人 / 上一批遗留的改动（fabric/forge/neo 源码、`porting/*.md`、`work/*`、release150 用例等）一律未触碰；本批改动只限上表 6 个文件 + 本报告。
- `CHANGELOG_ZH.md:2127` 有一处含「五处」的历史旧条目，与 26.1.2 平台差异无关，未改（修它会改历史版本内容）。
- 术语：本次新增/改写段落一律用「Waystone（传送石碑）」，未引入「指路石」。旧段落里原本没有「指路石」，全仓目标文件扫描为 0。

## 4. 遗留问题

1. **`mru` / `sounds` 共存仍未验证**（MAPPING §6 第 16 项，计数仍为 16）：测试台 NeoForge 为 26.1.2.109，而 `mru` 的 `neoforge.mods.toml` 要求 ≥ 26.1.2.114，无法同台。升级后需再跑一轮，并顺带复核 Waystone 联动仍打 INFO（不应降 WARN/DEBUG）。
2. **上一批的 787/787 entries 尚未提交**：本次在未提交的工作树上继续改，若最终只提交其中一部分，parity 计数会偏移；提交前应重跑 `python tools/check_changelog_parity.py` 确认仍 OK。
3. **受测集口径已从「95 件」修正为「106 个模组」**：若其他文档（如 `work/` 下更早的验收记录、`docs/porting/` 其他 wiki）仍写 95，属历史记录未回填，本次未扫全仓统一。
4. **玩家侧三份文档的「平台差异」表述现在是四点**：如后续另有差异增减，需同步 `PLAYER_CHANGELOG_ZH.md` / `PLAYER_CHANGELOG.md` / `PLAYER_CHANGELOG_MCMOD.html` 三处数字与列表（parity 门禁只守两省 CHANGELOG，不守这三份）。

## 5. 复现自检的方法

```
# 1) 中英一致
python tools/check_changelog_parity.py

# 2) 行尾 / BOM（对 6 个文件逐个）：统计 b'\r\n'、b'\n'-crlf、b'\r'-crlf 与首 3 字节是否 EF BB BF
# 3) 过时措辞：grep -n '注册失败\|无法注册\|功能缺席\|无法接入\|注册不上\|指路石' 六个文件
```

> 注（环境教训）：本机 `ops_bash` 是 bash（非 pwsh），Windows 控制台为 GBK —— 让 python 自己以 `encoding='utf-8'` 写文件再读，否则中文重定向产物不是合法 UTF-8。
