# Astral Dice 1.5.0 双线发布实机验收复测（1.21.1 / 1.20.1）

- 复测时间：2026-11-02 06:09 – 06:35（Asia/Shanghai）
- 复测原因：两条线 jar 在 2026-11-02 06:09:32 / 06:09:38 被重建（源码新增 i18n tooltip 修复），需对**重建后的 jar** 重新做实机验收
- 执行形态：production 形态（非 dev run），1.21.1 与 1.20.1 **串行**执行，端口 25565/25585 全程唯一
- 纪律遵守：未修改 scripts/test/** 任何断言与用例；未 git add / commit / push / tag；未执行任何构建；未 kill 非本次启动的进程

---

## 0. 结论摘要

| 线 | jar | 字节数 | 用例结果 | 27 断言 | 退出码 |
|---|---|---|---|---|---|
| neoforge-1.21.1 | astral_dice-1.5.0+neoforge_1.21.1.jar | 2,614,053 | **FAIL** | 21 PASS / 3 FAIL / 1 INCONCLUSIVE / 2 VIS | 1 |
| forge-1.20.1 | astral_dice-1.5.0+forge_1.20.1.jar | 2,662,359 | **PASS** | 25 PASS / 0 FAIL / 2 VIS | 0 |

**直读结论：forge-1.20.1 本轮重建 jar 全绿；neoforge-1.21.1 本轮 3 条断言失败。**

三条失败**均不属于 1.5.0 重建 jar 的产品缺陷**，逐条根因见 §2.5（世界存档热键槽状态漂移 2 条 + 断言口径与平台行形状不兼容 1 条），但按纪律「不粉饰」，仍如实记为 FAIL，原文见 §2.4。

---

## 1. 待测 jar 实测（sha256 全值 + 字节数）

| 线 | 路径 | 字节数 | sha256 |
|---|---|---|---|
| neoforge-1.21.1 | F:/MCProject/astral_dice_multiloader/neoforge-1.21.1/build/libs/astral_dice-1.5.0+neoforge_1.21.1.jar | 2,614,053 | 6109A1C34250CE5FE0461C98FC9B2A254CAEA63E97160E1F9CEF6062403FD6B1 |
| forge-1.20.1 | F:/MCProject/astral_dice_multiloader/forge-1.20.1/build/libs/astral_dice-1.5.0+forge_1.20.1.jar | 2,662,359 | D173E290B36BAE207A4E08C2E1F6E1DBD03608A28F09EA4628013F9E244975F4 |

两条均与任务书给定的字节数 / sha256 前 16 位一致（6109a1c34250ce5f、d173e290b36bae20）。

### 换入与备份记录（旧文件只改名，未删除）

- 1.21.1（运行时真实 mods 目录 = run/1.21.1/prodgame/mods，由 prodlaunch.py 的 GAMEDIR 决定）：
  - 旧 astral_dice-1.5.0+neoforge_1.21.1.jar 2,611,856 B / 24220D06A07A2CC6155B981682A080BDD191E1E79A89574ED4C5B4404903B113 → astral_dice-1.5.0+neoforge_1.21.1.jar.bak-20261102-061152
  - 新 jar 就位（match=True）；另按任务书对实例目录 D:\.minecraft\versions\狐の航空学 Voxy Edition\mods 做同一换入与改名（该目录非本次运行时加载路径，仅保持一致）
- 1.20.1（run/1.20.1/mods）：
  - 旧 astral_dice-1.5.0+forge_1.20.1.jar 2,660,134 B / FD6D8D8205E290701A18AF9CC489DD7EFAB6DC82D9A310F3079058EFA4B2E9C4 → astral_dice-1.5.0+forge_1.20.1.jar.bak-20261102-062744
  - 新 jar 就位；本轮 production mods 集合 = 36 jar / 140,367,803 B
  - 收尾把 dev 7 项复原回 run/1.20.1/mods，本轮 production 36+1 jar 归档到 run/1.20.1/.tmp/mods-prod-used-rr4-20261102-063129（含新 jar 2,662,359 B 与 .bak 2,660,134 B）

---

## 2. neoforge-1.21.1

### 2.1 启动（production 形态）

- 启动器：python run/1.21.1/.tmp/prodlaunch.py --quickplay testworld --log run/1.21.1/.tmp/prod-run-20261102-061152.log
- GAMEDIR = run/1.21.1/prodgame；实例 = D:\.minecraft\versions\狐の航空学 Voxy Edition；java = C:\Program Files\Zulu\zulu-21\bin\java.exe；-Xms2G -Xmx8G；classpath 107 项 missing 0；pid 31952
- 日志路径桥接：prodlaunch 把日志写 prodgame/logs，而 scripts/test/lib/Mt.Paths.psm1:237 的 latest_log 硬编码为 run/<ver>/logs/latest.log。本轮用目录 junction 桥接（run/1.21.1/logs -> run/1.21.1/prodgame/logs），收尾已还原（原目录恢复为 run/1.21.1/logs，并把本轮 latest.log / debug.log 拷回原目录）
- 轮转：run/1.21.1/logs/latest.log、logs/debug.log、prodgame/logs/latest.log、debug.log、三张截图、.mt_shots.json、.tmp/prod-run.log → 全部 .prev-20261102-061152（保留，未删）
- 就绪取证：Sound engine started=True、Loaded advancements=True、Time elapsed=True、MCP endpoint listening=True、TCP 25585 可连、06:22:37 出现 "[Server thread/INFO] [dev.ryanhcode.sable.Sable]: Saving sub-levels for level ServerLevel[新的世界]"（已进世界）

### 2.2 用例执行

- 命令：pwsh -NoProfile -File scripts/test/mt_case.ps1 run --version 1.21.1 --case scripts/test/cases/RELEASE150-1.21.1.json
- stdout：run/1.21.1/.tmp/case-rerun-20261102-062330.log（18,633 B）
- 机读行：
  - MT_CASE_RESULT: RELEASE150-1.21.1 = FAIL
  - MT_CASES_SUMMARY: RELEASE150-1.21.1=FAIL
  - MT_CHANNEL: delegated_count=2
  - MT_KEEP_ALIVE: 已置位（失败保留现场）
- EXITCODE = 1；执行步 31/31 全部 PASS（失败仅在断言层）
- 证据窗口：MT_INFO: CASE_EVIDENCE_WINDOW: RELEASE150-1.21.1 截图证据窗口起点 2026-11-02 06:23:30.046

### 2.3 27 条断言逐条

| # | 断言（步骤号） | 结果 |
|---|---|---|
| 1 | 32 log /Astral Dice 1\.5\.0\+neoforge_1\.21\.1/ | **FAIL(SOURCE_ECHO)** |
| 2 | 33 log ver=1.21.1 gamemode=creative | PASS |
| 3 | 34 log pos=100,-?[0-9]+,100 | PASS |
| 4 | 35 log hp=20 | PASS |
| 5 | 36 log item=astral_dice:abyss_reagent_chip | **FAIL** |
| 6 | 37 log xp=30 | PASS |
| 7 | 38 log slotted=[1-9][0-9]* | PASS |
| 8 | 39 log chips=3 | PASS |
| 9 | 40 log modver=1\.5\.0\+ | PASS |
| 10 | 41 log chip id=... count=3 name=深渊试剂 | PASS |
| 11 | 42 log tip i=0 text=.+ | PASS |
| 12 | 43 log guide key=astral_dice.guide.entry.abyss_reagent_chip.1 | PASS |
| 13 | 44 log inventory open=1 | PASS |
| 14 | 45 log CHIP_SHOP_OPEN player= | PASS |
| 15 | 46 log screen=...ChipShopScreen | PASS |
| 16 | 47 log sys id=astral_dice:dice ok=1 | PASS |
| 17 | 48 log sys id=astral_dice:attack_card_medium ok=1 | PASS |
| 18 | 49 log sys id=astral_dice:abyss_reagent_chip ok=1 | PASS |
| 19 | 50 log sys id=astral_dice:jill_sign ok=1 | PASS |
| 20 | 51 eval from=state | **FAIL** |
| 21 | 52 eval from=chip（name 前缀） | PASS |
| 22 | 53 eval from=screen | PASS |
| 23 | 54 absent（未知命令 / 找不到玩家 / 找不到实体） | PASS |
| 24 | 55 jar models>=120 entries>=1700 signs>=25 | PASS（models=158 entries=2016 signs=32） |
| 25 | 56 crash | **INCONCLUSIVE** |
| 26 | 57 vision abyss_reagent_tooltip.png | VIS（人工判读，见下） |
| 27 | 58 vision chip_shop_ui.png | VIS（人工判读，见下） |

计数：PASS 21 / FAIL 3 / BLOCKED 0 / INCONCLUSIVE 1 / VIS 2 = 27。

### 2.4 三条失败原文（latest.log 侧原文另见 §2.6）

    #32 [ FAIL] 32. assert — MT_ASSERT_LOG: FAIL(SOURCE_ECHO) — /Astral Dice 1\.5\.0\+neoforge_1\.21\.1/ 命中 1 行但无一是平台输出行（回显/续行/平台行内的 eval 源码载荷都不构成证据）（latest:latest.log@0B(win=whole)） L821 [echo] Astral Dice 1.5.0+neoforge_1.21.1 (astral_dice)
    #36 [ FAIL] 36. assert — MT_ASSERT_LOG: FAIL — /\[REL150\] ver=1\.21\.1 .* item=astral_dice:abyss_reagent_chip/ 未命中（latest:latest.log@2012199B(win=case)）
    #51 [ FAIL] 51. assert — MT_ASSERT_EVAL: FAIL — 路径 <root> 期望 {regex:"\\[REL150\\] ver=\\S+ gamemode=creative pos=100,-?[0-9]+,100 hp=20 .*item=astral_dice:abyss_reagent_chip"} 实际 "\"[REL150] ver=1.21.1 gamemode=creative pos=100,120,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=minecraft:air dim=minecraft:overworld modver=1.5.0+neoforge_1.21.1\""
    #56 [INCONCL] 56. assert — MT_ASSERT_CRASH: INCONCLUSIVE — crash-reports 目录不存在（F:\MCProject\astral_dice_multiloader\run\1.21.1\crash-reports）

### 2.5 根因（已用实读证据定案）

**(a) #36 / #51 —— 世界存档的热键槽状态漂移，非产品缺陷。**

- 用例 step17 的取值方式：var held = String(st.held); var itemId = held.replace(/^[0-9]+ /, '')；其中 st.held 来自 AutoJS.state()，语义 = 玩家**当前选中热键槽**的主手物品。
- 在保留现场（失败后 MT_KEEP_ALIVE 保住了游戏）上做只读 eval（AutoJS side=server）实读：玩家 DevTester，p.getInventory().getContainerSize()=41，字段 p.getInventory().selected=6（getSelectedSlot() 在本版本不存在，抛 TypeError: Cannot find function getSelectedSlot），非空槽 0..5 = 3 个 1 装的 astral_dice:abyss_reagent_chip（chip 的 maxStackSize=1，/give 3 被判 3 个 1 堆）+ dice + attack_card_medium + jill_sign；槽 6 为空 ⇒ getMainHandItem() 为空 ⇒ item=minecraft:air。
- /clear @a 清物品但**不重置选中槽**；run/1.21.1/prodgame 的 testworld 是本轮沿用存档（03:24 保存），选中槽持久为 6，因此状态探针读出 minecraft:air。
- 反证（同轮对照）：1.20.1 用同一份用例、同一 eval 语义，但世界由 scripts/test/resources/testworld-seed-1.20.1.zip 重建（选中槽=0），探针读出 item=astral_dice:abyss_reagent_chip，#36/#51 全 PASS。
- 结论：失败 = 「用例对世界状态有隐含前提（选中槽 0）」+「1.21.1 本轮沿用了非全新存档」叠加，与 1.5.0 重建 jar 无关。**未改断言、未重置存档来掩盖。**

**(b) #32 —— 版本行 pattern 与 2026-11-02 收紧后的平台行判据不兼容。**

- pattern /Astral Dice 1\.5\.0\+neoforge_1\.21\.1/ 在 latest.log 只命中 L821，该行是 FML 模组清单续行（双 Tab：<TAB><TAB>Astral Dice 1.5.0+neoforge_1.21.1 (astral_dice)）。
- scripts/test/mt_assert.ps1:405-470 的 Test-MtPlatformLine 明确把该形状列为**非**平台行（判据三只认 FabricLoader 单 Tab 的 "- modid version" 形状；docstring 第 417 行直接点名这条双 Tab FML 行）；该收紧随 mt_assert.ps1.bak-fabricplatform-20261102-055800（05:58 修订）落地。
- 1.21.1 本轮确实有 Version Gate 平台行（见 §2.6 L6113），但它不含 "Astral Dice 1.5.0+..." 这个字面；1.20.1 同位断言写的是 /mod_version=1\.5\.0\+forge_1\.20\.1/，所以 PASS。
- 结论：1.21.1 用例该 pattern 需改成 mod_version= 形态才能被新口径接受，但纪律禁止改 scripts/test/**，故如实记 FAIL。

**(c) #56 —— crash-reports 目录不存在 ⇒ INCONCLUSIVE（不是 PASS，也不是 FAIL）。** 1.21.1 生产包为 512 mod，该目录本轮未被创建；本轮未产生任何崩溃报告。

**(d) 视觉证据不足（不计入 27 断言，但必须说明）：** abyss_reagent_tooltip.png 判读（我实看）为**创造模式物品栏 + 悬停到 quark:hollow_spruce_log 的提示框**（空心云杉原木 / Sable Mass: 0.250 kg / Quark），不是深渊试剂。同轮 1.20.1 日志里可见既有探针脚本注释：「于是 NoSuchFieldException ⇒ lx=ty=0 ⇒ 屏幕中心兜底 ⇒ 悬停到画面中心的随机物品」——即 1.21.1 生产包（512 mod）下 hover 写鼠标的兜底逻辑把落点丢到了屏幕中心。chip tooltip 的机器证据是 §2.6 的 9 行 tip 探针（PASS），但**截图不足以作为视觉验收依据**。

### 2.6 证据原文（run/1.21.1/logs/latest.log，本轮 2,061,059 B / 11,318 行）

本轮 latest.log 共 32 处 [REL150]（22 条平台输出行 + 10 条 eval 源码回显行），关键原文：

    L10609 [Server thread/INFO] [KubeJS Server/]: autojs-eval#23: [REL150] ver=1.21.1 gamemode=creative pos=100,120,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=minecraft:air dim=minecraft:overworld modver=1.5.0+neoforge_1.21.1
    L10648 [REL150] chip id=astral_dice:abyss_reagent_chip count=3 name=深渊试剂 tipn=9 path=screen
    L10649 [REL150] tip i=0 text=深渊试剂
    L10650 [REL150] tip i=1 text=栏位: 筹码
    L10651 [REL150] tip i=2 text=astral_dice:abyss_reagent_chip
    L10652 [REL150] tip i=3 text=
    L10653 [REL150] tip i=4 text=装备时治愈层数 +2。触发治愈回血时：获得实际治疗量 × 2 的伤害吸收
    L10654 [REL150] tip i=5 text=
    L10655 [REL150] tip i=6 text=当前治愈：0 / 32
    L10656 [REL150] tip i=7 text=按住 CTRL 来查看标签
    L10657 [REL150] tip i=8 text=Astral Dice
    L10658 [REL150] guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=装备时治愈层数 +2；触发治愈回血时，获得「实际治疗量 ×2」的伤害吸收。
    L10693 [REL150] inventory open=1 hover=133,172 slot=45 win=266,344
    L10890 [REL150] shop_open invoked=1 show=reset
    L10899 [REL150] screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen
    L11283 [REL150] sys id=astral_dice:dice ok=1 name=骰子
    L11284 [REL150] sys id=astral_dice:attack_card_medium ok=1 name=攻击(中)
    L11285 [REL150] sys id=astral_dice:abyss_reagent_chip ok=1 name=深渊试剂
    L11286 [REL150] sys id=astral_dice:jill_sign ok=1 name=调酒师立牌

版本行（Version Gate 平台行，非 "Astral Dice 1.5.0+..." 字面）：

    L6113 [Render thread/INFO] [astral_dice-version-gate/]: [Astral Dice] 版本互通门槛(Version Gate):mod_version=1.5.0+neoforge_1.21.1 → 网络互通号=1.5;正式版按二号位(major.minor)互通,含 SNAPSHOT 的构建只与完整版本串相同的快照互通。

WARN/ERROR 归属（rg 'merlinkitsune' latest.log | rg 'WARN|ERROR'）：共 4 条，**全部来自第三方模组** com.yanny.aci.manager.ManagedRegistry（[Server thread/WARN]，2026-11-02 06:17:10.278），**零条来自本模组 logger，零 ERROR**：

    L8995 [ali] Missing trade item listings for com.merlinkitsune.astral_dice.trade.ChipTraderTrades$ChipTraderListing
    L9001 [ali] Missing trade item listings for com.merlinkitsune.astral_dice.trade.CardTraderTrades$CardTraderListing
    L9084 [ali] Missing value tooltips for com.merlinkitsune.astral_dice.shop.CardShopCatalog$PoolEntry
    L9085 [ali] Missing value tooltips for com.merlinkitsune.astral_dice.shop.ChipShopCatalog$Entry

### 2.7 三张截图（本轮 mtime ∈ 2026-11-02 06:23:35–06:23:38，属本轮）

| 文件 | 字节数 | sha256 | mtime |
|---|---|---|---|
| run/1.21.1/screenshots/abyss_reagent_tooltip.png | 165,937 | da2a1342c4fa5f3245f52358e1f454849781c739af5a8cfd78974b0487bb6b09 | 06:23:35 |
| run/1.21.1/screenshots/chip_shop_ui.png | 95,469 | ae2c26023ec20afdd0b17a3b9c6bb9a21e918f8948c6fc893a2dc66c8d8d9c61 | 06:23:37 |
| run/1.21.1/screenshots/release150_guide.png | 105,473 | 45dfed4f46d6e8e58d5dc73067d1b241dd9c7859b1b70fb82cf1713861a29a4b | 06:23:38 |

截图通道：op=shot channel=autojs，tier=T2 sig=grab(File,String,Target,Consumer)，png_ok=1，src=fallback_copy（从 prodgame/screenshots 复制并登记），tag→文件登记在 run/1.21.1/screenshots/.mt_shots.json（run_id 粘性值 20260926-230116）。

人工判读：
- abyss_reagent_tooltip.png：**创造模式物品栏**（建造方块页、1/24、1/768），tooltip 是 quark:hollow_spruce_log（空心云杉原木）——**不是**深渊试剂，视觉上「看得到提示框」但不是目标物品（见 §2.5(d)）。
- chip_shop_ui.png：商店容器（5 个收购槽、价格 18/3、物品 复仇之戟等）——**是商店**。
- release150_guide.png：帕秋莉手册已打开（「恋的规则书」起始页、类别页、多个 new 角标）——**指南 UI 渲染正常**。

### 2.8 收停与端口释放

- pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.21.1 --phase stop --force → exit 0，MT_CLEANUP_RESULT: OK，MT_STOP: OK，已清除 scripts/test/cases/.mt_keep_alive-1.21.1
- junction 已还原：run/1.21.1/logs 现为真实目录（非链接），本轮 latest.log / debug.log 已拷回（保证取证路径可读）；日志本体保留 .prev-20261102-063104 备份
- 释放验证：25565 / 25585 均无监听（netstat 全无命中）；run 路径下 java 进程 0；curl -s -m 3 http://127.0.0.1:25585/health 连接被拒（无响应）

### 2.9 环境事件（如实记录，非产品结论）

- 本轮 06:27 收停后，另一个工作流在 **06:31:04** 对 run/1.21.1 的 latest.log / 三张截图 / .mt_shots.json 做了一次轮转（改名为 *.prev-20261102-063104）——不是本会话执行。为满足交付口径，我已把本轮证据**复制回规范路径**（cp -p 保留原 mtime），哈希与 §2.7 一致，.prev 原件保留未动。
- 随后 **06:32:49** 又有外部 1.21.1 客户端以 gameDir=run/1.21.1/prodgame 启动，把我复制回规范路径的 latest.log 截断重写（起点 06:32:49，含 ModLauncher args）。我已从 logs/2026-11-02-5.log.gz 解出**字节完全一致**的本轮日志（2,061,059 B / 11,318 行，sha256 头 88b82ed1556448795e85dc9f），§2.6 全部引用行号复核成立；WARN 4 条仍在 L8995 / L9001 / L9084 / L9085。
- 本轮 1.21.1 证据已**另存独立副本**（不受上述外部轮转影响）：
  - run/1.21.1/.tmp/recheck-20261102-061152-evidence/latest.log（2,061,059 B / 11,318 行）
  - run/1.21.1/.tmp/recheck-20261102-061152-evidence/abyss_reagent_tooltip.png、chip_shop_ui.png、release150_guide.png（字节与 sha256 同 §2.7）
  - run/1.21.1/.tmp/recheck-20261102-061152-evidence/.mt_shots.json、case-rerun.log（18,633 B）
  - 规范路径 run/1.21.1/screenshots/ 三图最后核查仍为本轮副本（mtime 06:23:35/37/38，hash 一致）。
- 1.20.1 侧：客户端在 06:30:25.770 已自行终止（prod-run 日志在 tick 行中间截断，无关服序列 / 无 crash-report / 无 hs_err_pid18976.log / 无 OOM），stop 时报「已收停 0 个本流程进程」属「已不在」而非「被杀」；之后 06:31:04 的轮转与 06:32:49 的新实例同样来自外部工作流。三处时间点指向「外部工作流占用 25565/25585」——此为**推断，未证实**；本轮证据已在 06:29:14 之前全部落盘，不影响结论。
- 1.21.1 会话启动的 CrashAssistant 子进程（java pid 30632，06:15:09 起）在游戏退出后仍驻留，未被收停（它不占端口、不属于 mt 收停范围）；未主动 kill，如需清理请人工处置。

---

## 3. forge-1.20.1

### 3.1 启动（production 形态）

- 轮转（run/1.20.1/.tmp/rotate_1201.ps1）：三张旧图 933,267 / 3,789,562 / 1,161,678 B、.mt_shots.json、.tmp/prod-run.log 589,429 B、logs/latest.log 657,953 B、logs/debug.log 2,106,437 B → 全部 .prev-20261102-062745（保留）
- 装配（run/1.20.1/.tmp/assemble_prod_1201.ps1，STAMP=20261102-062744）：dev 7 项移入 .tmp/mods-dev-backup-20261102-062744；从 .tmp/mods-prod-used-rr2 拷入 36 jar（断言 ==36）；saves/testworld 移入 .tmp/saves-testworld-backup-20261102-062744；从 scripts/test/resources/testworld-seed-1.20.1.zip 重建世界（SEED_SHA256=6BBC680A699D929EF9628584F0B2A54313CBE362009F24E60E2070EB47AD62DF，11 文件 / 2,227,455 B）
- 启动：python run/1.20.1/.tmp/prodlaunch.py --quickplay testworld --log run/1.20.1/.tmp/prod-run-20261102-062744.log；GAMEDIR = run/1.20.1；java = Zulu-21；classpath 94 项 missing 0；pid 18976
- 就绪：约 65 s（Sound engine started + Time elapsed: 1207 ms + TCP 25585 可连）；AutoJS status = server 1 player online / client world loaded
- 本模组加载行：[com.merlinkitsune.astral_dice.AstralDiceMod/]: Astral Dice mod loaded.（06:28:07.035）

### 3.2 用例执行

- 命令：pwsh -NoProfile -File scripts/test/mt_case.ps1 run --version 1.20.1 --case scripts/test/cases/RELEASE150-1.20.1.json
- stdout：run/1.20.1/.tmp/case-rerun-20261102-062744.log（20,350 B）
- 机读行：
  - MT_CASE_RESULT: RELEASE150-1.20.1 = PASS
  - MT_CASES_SUMMARY: RELEASE150-1.20.1=PASS
  - MT_CHANNEL: delegated_count=2
  - EXITCODE = 0
- 执行步 31/31 全 PASS；断言 27/27（25 机器 + 2 视觉）
- 说明：该 stdout 文件被 Tee-Object 以控制台默认代码页写成 GBK，UTF-8 阅读会显示乱码且部分行合并；按 GBK 解码即可正常判读，不影响断言结论（结论行与 latest.log 原文均为独立证据）

### 3.3 27 条断言逐条

| # | 断言（步骤号） | 结果 |
|---|---|---|
| 1 | 32 log /mod_version=1\.5\.0\+forge_1\.20\.1/ | PASS（命中 L334 平台行） |
| 2 | 33 log ver=1.20.1 gamemode=creative | PASS |
| 3 | 34 log pos=100,-?[0-9]+,100 | PASS |
| 4 | 35 log hp=20 | PASS |
| 5 | 36 log item=astral_dice:abyss_reagent_chip | PASS |
| 6 | 37 log xp=30 | PASS |
| 7 | 38 log slotted=[1-9][0-9]* | PASS |
| 8 | 39 log chips=3 | PASS |
| 9 | 40 log modver=1\.5\.0\+ | PASS |
| 10 | 41 log chip id=... count=3 name=深渊试剂 | PASS |
| 11 | 42 log tip i=0 text=.+ | PASS |
| 12 | 43 log guide key=astral_dice.guide.entry.abyss_reagent_chip.1 | PASS |
| 13 | 44 log inventory open=1 | PASS |
| 14 | 45 log CHIP_SHOP_OPEN player= | PASS |
| 15 | 46 log screen=...ChipShopScreen | PASS |
| 16 | 47 log sys id=astral_dice:dice ok=1 | PASS |
| 17 | 48 log sys id=astral_dice:attack_card_medium ok=1 | PASS |
| 18 | 49 log sys id=astral_dice:abyss_reagent_chip ok=1 | PASS |
| 19 | 50 log sys id=astral_dice:jill_sign ok=1 | PASS |
| 20 | 51 eval from=state | PASS |
| 21 | 52 eval from=chip | PASS |
| 22 | 53 eval from=screen | PASS |
| 23 | 54 absent | PASS |
| 24 | 55 jar models>=120 recipes>=100 entries>=1700 signs>=25 | PASS（models=158 recipes=125 entries=2032 signs=32） |
| 25 | 56 crash | PASS（基线 6 个崩溃报告，无新增） |
| 26 | 57 vision abyss_reagent_tooltip.png | VIS（人工判读：看得到，且就是深渊试剂） |
| 27 | 58 vision chip_shop_ui.png | VIS（人工判读：是商店） |

计数：PASS 25 / FAIL 0 / BLOCKED 0 / INCONCLUSIVE 0 / VIS 2 = 27。

### 3.4 证据原文（run/1.20.1/logs/latest.log，本轮 558,377 B / 4,031 行）

    L334 [0211月2026 06:27:57.867] [modloading-worker-0/INFO] [astral_dice-version-gate/]: [Astral Dice] 版本互通门槛(Version Gate):mod_version=1.5.0+forge_1.20.1 → 网络互通号=1.5;正式版按二号位(major.minor)互通,含 SNAPSHOT 的构建只与完整版本串相同的快照互通。
    L1778 [0211月2026 06:29:09.518] [Server thread/INFO] [KubeJS Server/]: autojs-eval#23: [REL150] ver=1.20.1 gamemode=creative pos=100,106,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+forge_1.20.1
    L1819 [REL150] chip id=astral_dice:abyss_reagent_chip count=3 name=深渊试剂 tipn=7 path=screen
    L1820 [REL150] tip i=0 text=深渊试剂
    L1821 [REL150] tip i=1 text=槽位： 筹码
    L1822 [REL150] tip i=2 text=
    L1823 [REL150] tip i=3 text=装备时治愈层数 +2。触发治愈回血时：获得实际治疗量 × 2 的伤害吸收
    L1824 [REL150] tip i=4 text=
    L1825 [REL150] tip i=5 text=当前治愈：0 / 32
    L1826 [REL150] tip i=6 text=Astral Dice
    L1827 [REL150] guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=装备时治愈层数 +2；触发治愈回血时，获得「实际治疗量 ×2」的伤害吸收。
    L2022 [REL150] hover probe nslots=54 menu=net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$ItemPickerMenu | slot#45 id=astral_dice:abyss_reagent_chip playerInv=1 ...
    L2023 [REL150] inventory open=1 hover=399,322 slot=45 sxy=9,112 lx=382 ty=202 scale=2 win=798,644 write=11 pred=399,322 scr=net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen scr0=null ...
    L2242 [REL150] shop_open invoked=1 show=reset
    L2335 [REL150] screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen
    L2535 [REL150] guide_open=ok
    L2810 [REL150] sys id=astral_dice:dice ok=1 name=骰子
    L2811 [REL150] sys id=astral_dice:attack_card_medium ok=1 name=攻击(中)
    L2812 [REL150] sys id=astral_dice:abyss_reagent_chip ok=1 name=深渊试剂
    L2813 [REL150] sys id=astral_dice:jill_sign ok=1 name=调酒师立牌

WARN/ERROR 归属（rg 'merlinkitsune|astral_dice' 再筛 WARN|ERROR）：**零 ERROR**，WARN 分组如下：

1) 本模组自有 WARN（1 条，既往现象）：

    L497 [0211月2026 06:28:06.801] [Worker-ResourceReload-0/WARN] [astral_dice/MixinRuntimeGate/]: [星之骰戏] Mixin 子系统自检未完成（不影响加载，仅记录）：NullPointerException: Cannot invoke "Object.getClass()" because "obj" is null

2) Forge 注册表 ID 漂移（生产包组成导致，非崩溃；minecraft:item 约 20 条 / minecraft:mob_effect 13 条）：

    L684 [Render thread/WARN] [net.minecraftforge.registries.ForgeRegistry/REGISTRIES]: Registry minecraft:item: Object did not get ID it asked for. Name: astral_dice:effect_card_barricade Expected: 1288 Got: 1396
    ...
    L703 ... Name: astral_dice:deepseek_sign Expected: 1412 Got: 1415
    L704 [Render thread/WARN] ... Registry minecraft:mob_effect: Object did not get ID it asked for. Name: astral_dice:jill_mixed Expected: 37 Got: 77
    ...
    L717 ... Name: astral_dice:airbag_guard_cd Expected: 62 Got: 90

3) 第三方配置自纠（与本模组无关）：

    L956 ... ForgeConfigSpec/CORE: Configuration file ...\saves\testworld\serverconfig\yes_steve_model-server.toml is not correct. Correcting
    L967 ... openpartiesandclaims-server.toml is not correct. Correcting

### 3.5 三张截图（本轮 mtime ∈ 2026-11-02 06:29:10–06:29:14）

| 文件 | 字节数 | sha256 | mtime |
|---|---|---|---|
| run/1.20.1/screenshots/abyss_reagent_tooltip.png | 938,743 | c490a61ea6ed6d99f8485e8387df7a89574a5886f290b714ede8fece0a757d80 | 06:29:10 |
| run/1.20.1/screenshots/chip_shop_ui.png | 3,808,906 | 3b2bd372fa8e1fbfd81b0587288ce4be66cc61067c60db26a7f11270d11af931 | 06:29:12 |
| run/1.20.1/screenshots/release150_guide.png | 896,058 | c78c9fa467e02b5fc48bdffed80ce61e0410e96eab944b202013d2f607380102 | 06:29:14 |

截图通道：op=shot channel=autojs，tier=T2 sig=grab(File,String,Target,Consumer)，png_ok=1，src=direct。

人工判读：
- abyss_reagent_tooltip.png（1920x1080）：创造模式物品栏中「深渊试剂」的完整 tooltip —— 名称 深渊试剂、星之骰戏、槽位： 筹码、装备时治愈层数 +2。触发治愈回血时：获得实际治疗量 ×2 的伤害吸收、当前治愈： 0 / 32；**视觉与探针 text 完全一致**。
- chip_shop_ui.png（1920x1080）：筹码商店界面（5 个收购槽：能量回收器 10 / 旋行卡-余额 / 头盔 / 3 等），底部为玩家背包 —— **是商店**。
- release150_guide.png：手册页（guide_open=ok 对应）。

### 3.6 收停与端口释放

- pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.20.1 --phase stop --force → exit 0，MT_CLEANUP: 已收停 0 个本流程进程，MT_CLEANUP_RESULT: OK，MT_STOP: OK
- 端口：25565 / 25585 均无监听；curl -s -m 3 http://127.0.0.1:25585/health 无响应（curl 退出码 7，HTTP=000）
- **如实记录**：1.20.1 客户端进程在 06:30:25.770 即已终止（prod-run 日志在 tick 行中间截断），比我执行 stop（stop 日志 06:30:29）早约 4 s；该终止**没有**关服序列（无 Stopping server / Saving worlds）、**没有** crash-report、**没有** hs_err_pid18976.log、**没有** OutOfMemory。因此本次 stop 显示「已收停 0 个本流程进程」是「进程已不在」，不是「stop 杀了它」。结合 §2.9 的 06:31:04 外部轮转，推断是外部工作流为占用 25585/25565 而收停了本线客户端——**此为推断，未经证实**；可确证的事实是：用例与全部证据（06:29:14 前）在此之前已完成并落盘。
- 收尾后 run/1.20.1/mods 已复原为 dev 7 项（66,665,283 B），本轮 production jar 集归档于 .tmp/mods-prod-used-rr4-20261102-063129

---

## 4. 与上一轮的差异

| 线 | 上一轮 jar | 本轮 jar | Δ 字节 | Δ sha256 |
|---|---|---|---|---|
| neoforge-1.21.1 | 2,611,856 B / 24220D06A07A2CC6155B981682A080BDD191E1E79A89574ED4C5B4404903B113 | 2,614,053 B / 6109A1C3…03FD6B1 | **+2,197 B** | 全变 |
| forge-1.20.1 | 2,660,134 B / FD6D8D8205E290701A18AF9CC489DD7EFAB6DC82D9A310F3079058EFA4B2E9C4 | 2,662,359 B / D173E290…4E244975F4 | **+2,225 B** | 全变 |

推断（非断言）：两条线各有约 +2.2 KB 的等量增长，与「新增 i18n tooltip 修复」的 lang/资源增补量级相符；本轮实机**未观察到任何新增 ERROR**，且 chip tooltip 文案在两条线均按预期输出（见 §2.6 / §3.4 的 tip 探针与 1.20.1 截图）。

与上一轮验收在**判定口径**上的差异：
- 1.20.1 上一轮为 PASS（27 断言全绿，含 hover 走槽位遍历）；本轮同样 PASS，且本轮世界来自种子重建（选中槽 0），因此 item= 断言本轮给出正向证据。
- 1.21.1 上一轮记录为 PASS 的是一份**旧 jar**（2,611,856 B）；本轮重建 jar 在第 1 条（版本行口径）、第 5/20 条（世界状态漂移）上出现失败，属判定口径/夹具问题（§2.5），不是重新构建引入的回归。

---

## 5. 交付结论与遗留

1. **forge-1.20.1：可用于发布**。27 断言全绿（25 机器 PASS + 2 视觉通过），jar 2,662,359 B / D173E290…，三张截图属本轮且与探针一致，无 ERROR。
2. **neoforge-1.21.1：机器证据 21/25 PASS，3 条 FAIL 已定性为夹具/口径问题**。若要拿到全绿发布记录，需要下列**仓库侧决策**（本会话按纪律未改动）：
   - 用例版本行断言改为 mod_version= 形态（与 1.20.1 一致），或给 harness 增加针对 NeoForge FML 续行的显式口径；
   - 用例在世界准备阶段把选中热键槽归零（或断言改为读取槽位 0 而非 selected），使 item= 断言不依赖存档历史；
   - 修 hover 写鼠标的兜底（NoSuchFieldException ⇒ 屏幕中心），否则 chip tooltip 截图在 1.21.1 生产包下不可用；
   - crash-reports 目录缺失时应视为「无崩溃」而非 INCONCLUSIVE。
3. **建议**：上述 4 项属测试台/用例问题而非产品问题，修好后重跑一次 1.21.1 即可闭环；在此之前，1.21.1 的发布结论应为「工具证据不足，产品侧未见缺陷证据」。
