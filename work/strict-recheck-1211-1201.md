# RELEASE150 两线实机验收 · 严格复算报告

- 审计对象：run_id 20260926-230116 下 1.21.1（neoforge）与 1.20.1（forge）两条线的 RELEASE150 用例结果
- 审计性质：只读复算。未修改任何用例、日志、截图、jar 或测试台代码；未执行 git add / commit / push
- 复算时间：2026-11-02
- 复算脚本（可原样重跑）：work/strict-recheck-checker.py
- 脚本原始输出（逐条命中行号、eval 三分支、jar 计数、截图 mtime、窗口偏移）：work/strict-recheck-raw.json
- 判据实现依据（只读引用，未改动）：scripts/test/mt_assert.ps1、scripts/test/mt_case.ps1、scripts/test/lib/Mt.Paths.psm1

---

## 1. 严格判据定义（本报告使用的判据）

| 编号 | 断言类型 | 严格判据 |
|---|---|---|
| S1 | log | **逐物理行**匹配：每一行独立 re.search，绝不把整段文本拼起来匹配；命中须给出完整原行 + 1-based 行号 |
| S2 | log | 证据形态分级：行首为 「[」 且带 「[时间] [线程/级别] [来源]」 的**平台输出行** = 强证据；KubeJS 因为源码回显而产生的**脚本源码行**（行首为缩进、无时间戳）或模组清单**续行**（无独立时间戳）= 弱证据，单独标注 |
| S3 | log | 扫描窗口：scope=whole → 整文件；缺省 case → 自 scripts/test/cases/.mt_snapshot.json 的 offsets.latest.log 起（1.21.1 = 1051236 B ⇒ 第 7561 行；1.20.1 = 6434375 B ⇒ 第 40377 行） |
| S4 | eval | 三分支逐一实现：A 顶层操作符（单键且键名属 min/max/gte/lte/regex/set/contains）；B 字段级（实际值须为对象，逐字段求操作符或精确值）；C 精确值。regex 分支只接受字符串主体，对象判「类型不符」 |
| S5 | absent | 整个窗口内正则 0 命中才算 PASS |
| S6 | jar / crash / vision | jar：独立打开 jar 重新计数（models / recipes / signs / entries）；crash：crash-reports 目录内文件相对基线的**新增**；vision：PNG 存在 + 字节数 > 20 KB + mtime 落在本轮运行窗口内 + .mt_shots.json 有对应 tag 且文件名一致（**不**判画面内容，交 lead 看图） |

备注：re.search + re.S 的跨行拼接在本批用例上**不可复现**——两线 27 条 pattern 里没有任何 \s / \s+ / 否定字符类，且 ConvertTo-MtUniversalNewlines（scripts/test/mt_assert.ps1:273-288）已把 CRLF 折成 LF，. 不跨行。真正的「整段匹配」证据在 scripts/test/mt_assert.ps1:594（$pat.Matches($text).Count 对整段增量文本求值），本轮实测未造成假 PASS（见 §5）。

---

## 2. 断言总数表

| 用例 | log | eval | absent | jar | crash | vision | 合计 |
|---|---|---|---|---|---|---|---|
| scripts/test/cases/RELEASE150-1.21.1.json | 19 | 3 | 1 | 1 | 1 | 2 | **27** |
| scripts/test/cases/RELEASE150-1.20.1.json | 19 | 3 | 1 | 1 | 1 | 2 | **27** |
| 两线合计 | 38 | 6 | 2 | 2 | 2 | 4 | **54** |

两线断言结构完全对称：唯一 scope=whole 的都是第 1 条 log（1.21.1 用 Mod List 行，1.20.1 用版本互通门槛行）；第 2-19 条 log 全部覆盖 [REL150] 探针族与 CHIP_SHOP_OPEN；第 20-22 条 eval 分别 from=state / chip / screen；第 23 条 absent；第 24 条 jar；第 25 条 crash；第 26/27 条 vision（abyss_reagent_tooltip、chip_shop_ui）。

---

## 3. 1.21.1 逐条严格判定（窗口：第 7561 行起，全文件 12090 行，窗口起点 2026-11-02 03:05:19.215）

| # | 类型 | scope/from | 断言要点 | 严格判定 | 命中行 |
|---|---|---|---|---|---|
| 1 | log | whole | Astral Dice 1\.5\.0\+neoforge_1\.21\.1 | **PASS（弱证据）** | L24（模组清单续行，无独立时间戳） |
| 2 | log | case | ver=1.21.1 gamemode=creative | PASS | L7710 |
| 3 | log | case | pos=100,<数字>,100 | PASS | L7710 |
| 4 | log | case | hp=20 | PASS | L7710 |
| 5 | log | case | item=astral_dice:abyss_reagent_chip | PASS | L7710 |
| 6 | log | case | xp=30 | PASS | L7710 |
| 7 | log | case | slotted=[1-9][0-9]* | PASS | L7710 |
| 8 | log | case | chips=3 | PASS | L7710 |
| 9 | log | case | modver=1\.5\.0\+ | PASS | L7710 |
| 10 | log | case | chip id=…abyss_reagent_chip count=3 name=(深渊试剂\|Abyss Reagent) | PASS | L7751 |
| 11 | log | case | tip i=0 text=.+ | PASS | L7752 |
| 12 | log | case | guide key=…abyss_reagent_chip.1 text=.+ | PASS | L7759（**另 L7743 脚本源码回显也命中**） |
| 13 | log | case | inventory open=1 | PASS | L7795（**另 L7785 脚本源码回显也命中**） |
| 14 | log | case | CHIP_SHOP_OPEN player= | PASS | L7922 |
| 15 | log | case | screen=\S*ChipShopScreen | PASS | L8023 |
| 16 | log | case | sys id=astral_dice:dice ok=1 | PASS | L8204 |
| 17 | log | case | sys id=astral_dice:attack_card_medium ok=1 | PASS | L8205 |
| 18 | log | case | sys id=astral_dice:abyss_reagent_chip ok=1 | PASS | L8206 |
| 19 | log | case | sys id=astral_dice:jill_sign ok=1 | PASS | L8207 |
| 20 | eval | state | {regex:\[REL150\] ver=\S+ gamemode=creative pos=100,-?[0-9]+,100 hp=20 .*item=…} | PASS（分支 A，主体为字符串） | — |
| 21 | eval | chip | {name:{regex:^(深渊试剂\|Abyss Reagent)}} | PASS（分支 B，实际值为对象） | — |
| 22 | eval | screen | {screen:{regex:ChipShopScreen$}} | PASS（分支 B，实际值为对象） | — |
| 23 | absent | — | Unknown or incomplete command\|…\|没有与选择器匹配的实体 | PASS（窗口内 0 命中） | — |
| 24 | jar | — | models_min=120 / signs_min=25 / entries_min=1700 | PASS（models=158 / signs=32 / entries=2016 含目录、1882 纯文件） | — |
| 25 | crash | — | 无新增崩溃报告 | **PASS（弱证据）**：run/1.21.1/crash-reports 目录不存在 | — |
| 26 | vision | — | abyss_reagent_tooltip | PASS（1698376 B > 20 KB，mtime 03:05:22.047 ∈ 窗口，tag→file 一致） | — |
| 27 | vision | — | chip_shop_ui | PASS（1873586 B，mtime 03:05:23.855 ∈ 窗口，tag→file 一致） | — |

结论：**27/27 PASS**（其中 #1、#25 为弱证据，#26/#27 只过机械判据、画面内容未判）。

### 3.1 1.21.1 命中原文（整行照抄）

第 1 条（whole，模组清单续行）：

    L24  (两个制表符缩进) Astral Dice 1.5.0+neoforge_1.21.1 (astral_dice)

第 2-9 条（同一条 state 探针行）：

    L7710  [0211月2026 03:05:20.763] [Server thread/INFO] [KubeJS Server/]: autojs-eval#23: [REL150] ver=1.21.1 gamemode=creative pos=100,108,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+neoforge_1.21.1

第 10 条：

    L7751  [0211月2026 03:05:20.828] [Render thread/INFO] [KubeJS Client/]: autojs-eval#28: [REL150] chip id=astral_dice:abyss_reagent_chip count=3 name=深渊试剂 tipn=7 path=screen

第 11 条：

    L7752  [0211月2026 03:05:20.828] [Render thread/INFO] [KubeJS Client/]: autojs-eval#29: [REL150] tip i=0 text=深渊试剂

第 12 条（真证据）：

    L7759  [0211月2026 03:05:20.828] [Render thread/INFO] [KubeJS Client/]: autojs-eval#31: [REL150] guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=装备时治愈层数 +2；触发治愈回血时，获得「实际治疗量 ×2」的伤害吸收。

第 12 条的假阳性通道（KubeJS 把脚本源码整行回显，无时间戳、行首缩进）：

    L7743    console.info('[REL150] guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=' + guide);

第 13 条（真证据）：

    L7795  [0211月2026 03:05:20.888] [Render thread/INFO] [KubeJS Client/]: autojs-eval#25: [REL150] inventory open=1 hover=399,322 slot=45 win=798,644

第 13 条的假阳性通道：

    L7785      console.info('[REL150] inventory open=1 hover=' + out.guiX + ',' + out.guiY + ' slot=' + out.slot + ' win=' + out.winX + ',' + out.winY);

第 14 条：

    L7922  [0211月2026 03:05:22.151] [Server thread/INFO] [com.merlinkitsune.astral_dice.shop.ChipShopOpener/]: [astral_dice] CHIP_SHOP_OPEN player=Dev offers=cursed_sword_chip+hand_fan_small_chip+sensor_chip purchased=0 refreshes=3

第 15 条：

    L8023  [0211月2026 03:05:23.607] [Render thread/INFO] [KubeJS Client/]: autojs-eval#5: [REL150] screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen

第 16-19 条：

    L8204  [0211月2026 03:05:25.647] [Server thread/INFO] [KubeJS Server/]: autojs-eval#9: [REL150] sys id=astral_dice:dice ok=1 name=骰子
    L8205  [0211月2026 03:05:25.647] [Server thread/INFO] [KubeJS Server/]: autojs-eval#9: [REL150] sys id=astral_dice:attack_card_medium ok=1 name=攻击(中)
    L8206  [0211月2026 03:05:25.647] [Server thread/INFO] [KubeJS Server/]: autojs-eval#9: [REL150] sys id=astral_dice:abyss_reagent_chip ok=1 name=深渊试剂
    L8207  [0211月2026 03:05:25.647] [Server thread/INFO] [KubeJS Server/]: autojs-eval#9: [REL150] sys id=astral_dice:jill_sign ok=1 name=调酒师立牌

### 3.2 1.21.1 的窗口边界证据

- 窗口起点 1051236 B 对应第 7561 行（该行时间戳 03:05:19.265）。日志里 [REL150] 序列存在**两轮**：run A 在第 3762 行（03:04:08，pos=100,**104**,100），run B 在第 7710 行（03:05:20，pos=100,**108**,100）。窗口起点切在 run A 之后，故 1.21.1 第 2-9 条只在 run B 命中；保存的 eval state（pos=108）与 run B 一致。
- run A 的 state 行原文（窗口外，仅作对照，说明为何必须卡窗口）：

    L3762  [0211月2026 03:04:08.057] [Server thread/INFO] [KubeJS Server/]: autojs-eval#23: [REL150] ver=1.21.1 gamemode=creative pos=100,104,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+neoforge_1.21.1

---

## 4. 1.20.1 逐条严格判定（窗口：第 40377 行起，全文件 44608 行，窗口起点 2026-11-02 03:28:22.994）

| # | 类型 | scope/from | 断言要点 | 严格判定 | 命中行 |
|---|---|---|---|---|---|
| 1 | log | whole | mod_version=1\.5\.0\+forge_1\.20\.1 | PASS | L375 |
| 2 | log | case | ver=1.20.1 gamemode=creative | PASS | L40514 |
| 3 | log | case | pos=100,<数字>,100 | PASS | L40514 |
| 4 | log | case | hp=20 | PASS | L40514 |
| 5 | log | case | item=astral_dice:abyss_reagent_chip | PASS | L40514 |
| 6 | log | case | xp=30 | PASS | L40514 |
| 7 | log | case | slotted=[1-9][0-9]* | PASS | L40514 |
| 8 | log | case | chips=3 | PASS | L40514 |
| 9 | log | case | modver=1\.5\.0\+ | PASS | L40514 |
| 10 | log | case | chip id=… count=3 name=(深渊试剂\|Abyss Reagent) | PASS | L40553 |
| 11 | log | case | tip i=0 text=.+ | PASS | L40554 |
| 12 | log | case | guide key=… text=.+ | PASS | L40561（**另 L40550 脚本源码回显也命中**） |
| 13 | log | case | inventory open=1 | PASS | L40593（本窗口内源码回显未命中） |
| 14 | log | case | CHIP_SHOP_OPEN player= | PASS | L40656 |
| 15 | log | case | screen=\S*ChipShopScreen | PASS | L40750 |
| 16 | log | case | sys id=astral_dice:dice ok=1 | PASS | L40915 |
| 17 | log | case | sys id=astral_dice:attack_card_medium ok=1 | PASS | L40916 |
| 18 | log | case | sys id=astral_dice:abyss_reagent_chip ok=1 | PASS | L40917 |
| 19 | log | case | sys id=astral_dice:jill_sign ok=1 | PASS | L40918 |
| 20 | eval | state | {regex:\[REL150\] ver=\S+ …} | PASS（分支 A，主体为带引号字符串） | — |
| 21 | eval | chip | {regex:^["]?(深渊试剂\|Abyss Reagent)["]?$} | PASS（分支 A，主体为**带字面引号**的字符串） | — |
| 22 | eval | screen | {regex:ChipShopScreen["]?$} | PASS（分支 A，同上） | — |
| 23 | absent | — | 同上四类报错 | PASS（窗口内 0 命中） | — |
| 24 | jar | — | models_min=120 / recipes_min=100 / signs_min=25 / entries_min=1700 | PASS（models=158 / recipes=125 / signs=32 / entries=2032 含目录、1901 纯文件） | — |
| 25 | crash | — | 无新增崩溃报告 | PASS（目录内恰为基线 5 个，无新增） | — |
| 26 | vision | — | abyss_reagent_tooltip | **FAIL（时效）**：文件在、1261572 B、tag 一致，但 mtime 03:24:47.923 **早于**窗口起点 03:28:22 | — |
| 27 | vision | — | chip_shop_ui | **FAIL（时效）**：3512368 B，mtime 03:22:17.571 **早于**窗口起点 | — |

结论：**25/27 PASS**，第 26/27 条严格不成立。

### 4.1 1.20.1 命中原文（整行照抄）

第 1 条（whole，版本互通门槛行）：

    L375  [0211月2026 03:13:21.630] [modloading-worker-0/INFO] [astral_dice-version-gate/]: [Astral Dice] 版本互通门槛(Version Gate):mod_version=1.5.0+forge_1.20.1 → 网络互通号=1.5;正式版按二号位(major.minor)互通,含 SNAPSHOT 的构建只与完整版本串相同的快照互通。

第 2-9 条：

    L40514  [0211月2026 03:28:24.379] [Server thread/INFO] [KubeJS Server/]: autojs-eval#23: [REL150] ver=1.20.1 gamemode=creative pos=100,108,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+forge_1.20.1

第 10-11 条：

    L40553  [0211月2026 03:28:24.432] [Render thread/INFO] [KubeJS Client/]: autojs-eval#32: [REL150] chip id=astral_dice:abyss_reagent_chip count=3 name=深渊试剂 tipn=7 path=screen
    L40554  [0211月2026 03:28:24.432] [Render thread/INFO] [KubeJS Client/]: autojs-eval#33: [REL150] tip i=0 text=深渊试剂

第 12 条（真证据）与假阳性通道：

    L40561  [0211月2026 03:28:24.432] [Render thread/INFO] [KubeJS Client/]: autojs-eval#35: [REL150] guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=装备时治愈层数 +2；触发治愈回血时，获得「实际治疗量 ×2」的伤害吸收。
    L40550    console.info('[REL150] guide key=astral_dice.guide.entry.abyss_reagent_chip.1 text=' + guide);

第 13 条：

    L40593  [0211月2026 03:28:24.501] [Render thread/INFO] [KubeJS Client/]: autojs-eval#28: [REL150] inventory open=1 hover=480,270

第 14-15 条：

    L40656  [0211月2026 03:28:25.567] [Server thread/INFO] [com.merlinkitsune.astral_dice.shop.ChipShopOpener/]: [astral_dice] CHIP_SHOP_OPEN player=DevTester offers=target_chip+sandwich_low_chip+candy_chip purchased=0 refreshes=3
    L40750  [0211月2026 03:28:27.038] [Render thread/INFO] [KubeJS Client/]: autojs-eval#5: [REL150] screen=com.merlinkitsune.astral_dice.screen.ChipShopScreen

第 16-19 条：

    L40915  [0211月2026 03:28:28.580] [Server thread/INFO] [KubeJS Server/]: autojs-eval#9: [REL150] sys id=astral_dice:dice ok=1 name=骰子
    L40916  [0211月2026 03:28:28.580] [Server thread/INFO] [KubeJS Server/]: autojs-eval#9: [REL150] sys id=astral_dice:attack_card_medium ok=1 name=攻击(中)
    L40917  [0211月2026 03:28:28.580] [Server thread/INFO] [KubeJS Server/]: autojs-eval#9: [REL150] sys id=astral_dice:abyss_reagent_chip ok=1 name=深渊试剂
    L40918  [0211月2026 03:28:28.580] [Server thread/INFO] [KubeJS Server/]: autojs-eval#9: [REL150] sys id=astral_dice:jill_sign ok=1 name=调酒师立牌

### 4.2 1.20.1 截图证据链（vision 判 FAIL 的依据）

| 文件 | 字节 | mtime | 是否落在本轮窗口（≥03:28:22.994） |
|---|---|---|---|
| abyss_reagent_tooltip.png | 1261572 | 2026-11-02 03:24:47.923 | 否 |
| chip_shop_ui.png | 3512368 | 2026-11-02 03:22:17.571 | 否 |
| release150_guide.png | 846731 | 2026-11-02 03:21:17.262 | 否（本文件无 vision 断言） |

- .mt_shots.json（run_id=20260926-230116，共 85 条）针对三个 tag 的**最后三条**条目 ts 全部等于 1793561109.0（= 03:25:09.000），即同一时刻被批量追加，与三张 PNG 的真实 mtime 无对应关系；追加者是 run/1.20.1/.tmp/reg_shots.py（03:25:09 执行），改名者是 run/1.20.1/.tmp/rename_shot.ps1（03:21:19）。
- 本轮（03:28 那轮）三个 screenshot op **全部失败且未产出新图**，stdout 原文见 run/1.20.1/.tmp/case-run5.out.txt：MT_CHANNEL op=shot channel=delegated status=delegated detail=<tag> — Screenshot.grab 未产出 <tag>.png：LegacyError: Line 4: 'java()' is no longer supported! Read more on wiki: https://kubejs.com/kjs6
- 因为 Get-MtCurrentShots 在 run_id 命中时只按清单条目判「当前世代」（scripts/test/lib/Mt.Paths.psm1:355-368，无 per-entry ts 过滤），旧图被当成当前世代证据，vision 断言拿到 DELEGATED→PASS。
- 对比：1.21.1 三张 PNG 的 mtime（03:05:22.047 / 03:05:23.855 / 03:05:25.637）与 .mt_shots.json 末三条 ts（1793559922.058 / 1793559923.861 / 1793559925.642）逐毫秒吻合，且晚于窗口起点 03:05:19.215 ⇒ 1.21.1 的证据链自洽。

---

## 5. 与测试台结论的差异清单

测试台结论口径：scripts/test/cases/.mt_run_state.json 记录 versions.1.21.1.cases.RELEASE150-1.21.1 = PASS（72 条，累计）、versions.1.20.1.cases.RELEASE150-1.20.1 = PASS（41 条）；1.20.1 那轮的逐条裁决见 run/1.20.1/.tmp/case-run5.out.txt（第 32-58 步 27 条断言全 PASS，MT_CASE_RESULT: RELEASE150-1.20.1 = PASS，MT_WRAPPER_EXIT: 0）。**1.21.1 没有任何 case-run*.out.txt 留存**（全树只有 run/1.20.1/.tmp/case-run.out.txt、case-run4.out.txt、case-run5.out.txt）。

| 编号 | 差异 | 测试台 | 严格复算 | 影响 |
|---|---|---|---|---|
| D1 | 两线共 38 条 log 断言的判定 | 全 PASS | 全 PASS：37 条有平台输出行强证据，1 条（1.21.1 #1）为模组清单续行的弱证据 | 无差异，功能证据成立 |
| D2 | 1.20.1 vision #26/#27 | DELEGATED → 计入 PASS（delegated_count=5，最终 PASS） | **FAIL（时效）**：截图 mtime 早于本轮窗口，且由人工脚本补登记 | **本轮唯一实质差异**：两条 vision 断言不成立 |
| D3 | 命中次数口径（假阳性通道真实存在） | 1.20.1 第 43 步（guide key）计「命中 2 次」 | 2 次中 1 次是脚本源码回显（L40550），真证据仅 L40561 | 本轮未翻转判定，但通道有效 |
| D4 | 1.21.1 crash 断言 | PASS（无 crash-reports 目录） | PASS（弱证据）：目录从未创建 ≠ 无崩溃 | 降级为「不可核」更准确 |
| D5 | jar entries 计数口径 | entries=2032（1.20.1）/ 2016（1.21.1） | 含目录条目 2032/2016；**纯文件** 1901/1882 | 两者都 ≥1700，判定不变；口径需在文档里固定 |
| D6 | 1.21.1 jar 的 recipes 计数 | 未断言（1.21.1 用例无 recipes_min） | recipes=0：jar 内是 data/astral_dice/recipe/（单数，125 个），而统计口径写死 data/astral_dice/recipes/（复数） | 潜在缺陷（neo 线该字段恒 0），本轮无后果 |
| D7 | 1.20.1 用例 note 的引用 | scripts/test/cases/RELEASE150-1.20.1.json:22 称「latest.log:144 实测 …mod_version=1.5.0-rc.7+forge_1.20.1」 | L144 实际是 [0211月2026 03:13:18.960] [Render thread/INFO] [Arc3D/]: Use NV_conservative_raster；正确引用是 L375 的 1.5.0+forge_1.20.1；全日志无任何 rc 版本行 | 文档陈旧引用，建议改 |

补充观察（不影响判定）：两线 eval 返回值编码不一致——1.21.1 的 chip/screen 求值返回 **JSON 对象**（故用例用字段级 regex），1.20.1 同名步骤返回 **双层编码的带字面引号字符串**（故用例必须写 [" ]? 容错）。若把 1.20.1 的顶层 regex 套到 1.21.1 的对象上，测试台会判「类型不符」而不是静默通过。

另注（超出本次范围，仅如实登记）：scripts/test/cases/.mt_autojs.json 里还存有 26.1.2 的 RELEASE150 eval 记录，其中 state/chip 两条为 FAIL（pos=101,107,101 与 expect 的 pos=100 不符）。本次只审计 1.21.1 与 1.20.1，故不展开。

---

## 6. 无法复判项

| 编号 | 事项 | 缺什么证据 |
|---|---|---|
| U1 | 1.21.1 的逐条命中次数/展示行无法与测试台对账 | 1.21.1 那轮的 wrapper stdout（case-run*.out.txt）未留存；仅存 .mt_run_state.json / .mt_autojs.json 的聚合 PASS |
| U2 | vision 断言所述的**画面内容**（tooltip 是否可见、是否为商店界面） | 按任务约定不做内容判定，交 lead 看图；本报告只给机械判据结论 |
| U3 | eval 断言的「日志内返回值原文」 | 1.21.1 的 chip/screen 返回对象、日志里只有脚本 console.info 拼出的子串，没有返回值原文；复判依赖 .mt_autojs.json 的 record.value（1.21.1 ts=1793559945.774、1.20.1 ts=1793561346.6） |
| U4 | 1.20.1 那三张 PNG 究竟出自哪一轮 | rename 不改 mtime、当轮 stdout 未保留该截图段；只能确定「不早于 03:21:17、早于 03:28:22.994 那轮」 |

---

## 7. 三条硬事实（与断言无关，单独核）

### ① 生效版本串为 1.5.0+后缀，且无 rc/beta 作为生效版本

- 1.21.1：L271（启动期，03:00:01.530）——

    [0211月2026 03:00:01.530] [Render thread/INFO] [astral_dice-version-gate/]: [Astral Dice] 版本互通门槛(Version Gate):mod_version=1.5.0+neoforge_1.21.1 → 网络互通号=1.5;正式版按二号位(major.minor)互通,含 SNAPSHOT 的构建只与完整版本串相同的快照互通。

- 1.21.1：L24 模组清单行 —— Astral Dice 1.5.0+neoforge_1.21.1 (astral_dice)
- 1.20.1：L375（启动期，03:13:21.630）——

    [0211月2026 03:13:21.630] [modloading-worker-0/INFO] [astral_dice-version-gate/]: [Astral Dice] 版本互通门槛(Version Gate):mod_version=1.5.0+forge_1.20.1 → 网络互通号=1.5;正式版按二号位(major.minor)互通,含 SNAPSHOT 的构建只与完整版本串相同的快照互通。

- rc/beta 全量扫描（按版本形态取词，不看裸子串——裸 rc 会误命中 Searching/Resource 之类）：
  - 形如 rc.<数字> / rc<数字> 的版本片段：1.21.1 = **0** 行，1.20.1 = **0** 行
  - 单词 beta：1.21.1 = 105 行，**全部**是 Iris 1.8.14-beta.1+mc1.21.1（模组清单行 L40、ImmediatelyFast 兼容提示 L111、以及 L314 起的一整段 Iris 栈帧）；1.20.1 = **0** 行
  - SNAPSHOT：两线各 1 行（L271 / L375），都是版本互通门槛自身文案「含 SNAPSHOT 的构建只与完整版本串相同的快照互通」
  - astral_dice-1.5.0-rc 形式：两线均 **0** 行；形如 mod_version=1.5.0-rc/beta 的行：两线均 **0** 行
  **结论：没有任何一行把 rc/beta 版本串作为 Astral Dice 的生效版本。**
- jar 内元数据同证：META-INF/neoforge.mods.toml 的 version="1.5.0+neoforge_1.21.1"；META-INF/mods.toml 的 version="1.5.0+forge_1.20.1"。
- 注意：build/libs 里仍躺着 rc 旧产物（astral_dice-1.5.0-rc.6+neoforge_1.21.1.jar 10-29 18:07、astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar 11-01 23:43、astral_dice-1.5.0-rc.7+forge_1.20.1.jar 11-01 23:43），但都比 1.5.0 正式产物（11-02 01:50）旧，且 jar 断言按 mtime 取最新，故取样与运行的都是 1.5.0+。

### ② abyss_reagent_chip 名称为「深渊试剂」，全日志「试剂-深渊」0 命中

- 语言文件：neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/zh_cn.json:379 与 forge-1.20.1/src/main/resources/assets/astral_dice/lang/zh_cn.json:385 均为 "item.astral_dice.abyss_reagent_chip": "深渊试剂"；en_us 为 Abyss Reagent；ja_jp 为 深淵試薬。
- 日志计数：1.21.1 深渊试剂 = 128 命中、试剂-深渊 = **0**、Abyss Reagent = 0、abyss_reagent_chip = 596；1.20.1 深渊试剂 = 483、试剂-深渊 = **0**、Abyss Reagent = 0、abyss_reagent_chip = 996。
- id 与名称同行的行：1.21.1 共 4 条（L3803、L4208、L7751、L8206）；1.20.1 共 10 条（L5028、L5393、L30835、L31200、L34381、L34744、L37682、L38047、L40553、L40917）。
- guide 文案与语言文件逐字一致（两线 zh_cn.json:132 与日志 L7759 / L40561 相同）。

### ③ [REL150] 探针行原文

1.21.1（L7710）：

    [0211月2026 03:05:20.763] [Server thread/INFO] [KubeJS Server/]: autojs-eval#23: [REL150] ver=1.21.1 gamemode=creative pos=100,108,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+neoforge_1.21.1

1.20.1（L40514）：

    [0211月2026 03:28:24.379] [Server thread/INFO] [KubeJS Server/]: autojs-eval#23: [REL150] ver=1.20.1 gamemode=creative pos=100,108,100 hp=20 food=20 xp=30 arm=0 slotted=6 chips=3 item=astral_dice:abyss_reagent_chip dim=minecraft:overworld modver=1.5.0+forge_1.20.1

两行除 ver / modver 外逐字段相同（pos、hp、food、xp、arm、slotted=6、chips=3、item、dim），说明两线探针脚本输出结构一致，可直接横向比对。

---

## 8. 若把上述严格判据反灌回测试台：需要改的行（只列清单，未改动）

| 编号 | 文件:行号 | 一句话改法 |
|---|---|---|
| R1 | scripts/test/mt_assert.ps1:594 | 把 $pat.Matches($text).Count 对整段文本的求值改为**逐行**循环匹配（每行独立求值、分别累计命中数与行号），彻底断掉跨行拼接的可能性 |
| R2 | scripts/test/mt_assert.ps1:597-600 | 命中后要求「至少一行是平台输出行」（行首为 [」，带 [时间] 前缀）；若只命中脚本源码回显行，判 SOURCE_ECHO 并在汇总里单列，不得直接记 PASS |
| R3 | scripts/test/mt_assert.ps1:602-606 | INCONCLUSIVE 现在 return 0（不计 FAIL），需在最终汇总里**单列计数**，否则「窗口不可判」会被读成全绿 |
| R4 | scripts/test/mt_assert.ps1:679-682 | crash 断言在 crash-reports 目录不存在时改判 INCONCLUSIVE（目录从未创建不等于「无崩溃」），只有目录存在且无新增才 PASS |
| R5 | scripts/test/mt_case.ps1:1898-1935 | vision 断言加机械判据：Get-Item 后校验 Length ≥ 20480 且 LastWriteTime ≥ 本窗口起点（取 offsets 的 anchor mtime），不满足直接 BLOCKED 并给出实测值 |
| R6 | scripts/test/lib/Mt.Paths.psm1:355-368 | run_id 命中分支里对每条清单条目比较其 ts 与运行开始时间，早于窗口起点的条目不得计入「当前世代」（现只按文件名去重、不看 ts） |
| R7 | scripts/test/mt_case.ps1:2104-2111 | screenshot op 返回 delegated/ERROR 时登记「该 tag 本轮未产出」；对应 vision 断言见此登记即 BLOCKED，避免用旧图冒充本轮证据 |
| R8 | scripts/test/mt_case.ps1:1685 | recipes 计数同时接受 data/astral_dice/recipe/（1.21+ 单数）与 data/astral_dice/recipes/（1.20 复数），否则 neo 线 recipes 恒 0 |
| R9 | scripts/test/mt_case.ps1:1693 | entries 现为 @($entry.names).Count（含目录条目）；要么过滤目录条目统一为「文件数」，要么在 TESTING-SPEC 里把口径固定为「含目录条目」（本轮 2032 vs 纯文件 1901） |
| R10 | scripts/test/cases/RELEASE150-1.20.1.json:22 | note 里的「latest.log:144 实测 …1.5.0-rc.7+forge_1.20.1」改为「latest.log:375 实测 …1.5.0+forge_1.20.1」（当前引用行号与版本串都陈旧） |

---

## 9. 结论

**一句话收口：两线严格复算在「功能与注册证据」层面支持 PASS——1.21.1 = 27/27 PASS（其中 #1 模组清单续行、#25 crash 无目录两项为弱证据，#26/#27 仅过机械判据）；1.20.1 = 25/27 PASS，第 26/27 两条 vision 断言因截图 mtime（03:22:17 / 03:24:47）早于本轮窗口起点（03:28:22.994）、且 .mt_shots.json 条目由人工脚本于 03:25:09 批量补登记，严格不成立——即「视觉留档属本轮」只有 1.21.1 成立。**

补充口径建议（供 lead 决策，不影响上述判定）：本轮 1.20.1 的功能证据（log 17 条真证据 + jar + absent + crash）是完好的；缺的只是「本轮截图未产出」。若接受「用上一轮截图 + 本轮日志佐证」的口径，需在验收记录里显式降级 vision 为「非本轮留档」，并说明本轮 screenshot op 失败原因（KubeJS Screenshot.grab 命中 LegacyError: 'java()' is no longer supported!）。
