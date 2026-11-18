# B6 — fabric-1.20.1 资源守门收敛（assets gate）

2026-11-01 实测；branch `multi-main`，HEAD `fd990c15`。落点仅 `tools/verify_fabric_assets.py` + 三语 lang。未跑 gradle / 未启游戏 / 未 commit / 未删文件 / 未改 java / 未动 `network/` `client/` `audio/`。

## 0. 结论

| 项 | 判定 | 处置 |
|---|---|---|
| [1] 方块物品资源不全 / [2] 缺模型 / [3] 缺贴图 | **误报（守门读旧 jar）** | 不改资源（§3） |
| [5] `gui.astral_dice.card_choice.title_` | **误报（字符串拼接前缀）** | 守门跳过 `_` 结尾字面量（§2-a） |
| [5] `gui.astral_dice.card_shop.transfer_no_match` | **真缺失** | 三语各补 1 键（§1） |
| [7] 不在创造栏 `repulsion` | **三方一致口径，非本线缺陷** | 守门白名单（§2-b） |
| [5] `gui.astral_dice.event_choice.title` / `message.astral_dice.event_choice.esc_warning` | **真缺失，超出授权落点** | 遗留（§4.2） |

FAIL：**7 → 3（旧 jar 三项）+ 三语各 1 条（event_choice 两键）**；`title_`、`transfer_no_match`、`repulsion` 已从 FAIL 消失。

## 1. 落点 A：三语 lang 追加 1 键（真缺失）

基线（`forge-1.20.1/src/main/resources/assets/astral_dice/lang/`）：`zh_cn.json:970` = `没有匹配的玩家`、`en_us.json:969` = `No matching player`、`ja_jp.json:968` = `一致するプレイヤーがいません`；`neoforge-1.21.1` 同键同值（969/969/968）⇒ 三线一致，fabric 缺 = 真缺陷。

落盘：**逐行文本插入**（未 `json.load`+`dump`），插在 `gui.astral_dice.card_shop.transfer_hint` 之后（该段按字母序，hint↔refresh 之间），未移动任何既有键、新行与基线行**逐字节相同**、仍纯 CRLF + UTF-8 无 BOM：

```
zh_cn.json:958:  "gui.astral_dice.card_shop.transfer_no_match": "没有匹配的玩家",
en_us.json:958:  "gui.astral_dice.card_shop.transfer_no_match": "No matching player",
ja_jp.json:958:  "gui.astral_dice.card_shop.transfer_no_match": "一致するプレイヤーがいません",
```

## 2. 守门两处精度修正（393 → 411 行，本批 +22 / −5）

### 2-a 语言闭环：跳过以 `_` 结尾的字面量（脚本 251-256 行；注释原文 6 行，此处留首末）

```diff
-                literals |= set(key_re.findall(open(os.path.join(dirpath, name),
-                                                    encoding='utf-8').read()))
+                found = key_re.findall(open(os.path.join(dirpath, name),
+                                            encoding='utf-8').read())
+                # 「以 _ 结尾」的字面量是**字符串拼接前缀**、不是真实 lang 键，一律跳过：
+                #   "gui.astral_dice.card_choice.title_" + (slotCount == 1 ? "single_" : "triple_") + ...
+                # 而半截前缀 title_ 永远不可能出现在 lang 里 ⇒ 不过滤就是必然误报。
+                literals |= {k for k in found if not k.endswith('_')}
```

引用 `choice/CardChoiceType.java:59`（真键 = `title_<single|triple>_<combat|effect|any>` 6 键，lang 全齐）。只丢 `_` 结尾（拼接产物恒非键），其余照旧校验。**正向对照**：同一判据此刻仍抓出 `event_choice.title` / `esc_warning` 两条真缺失并让守门 exit 1 ⇒ 未放宽成静默绿灯。

### 2-b 创意标签闭环：基线一致白名单（脚本 70-77、313-320 行；注释原文 5 行，此处留 2 行）

```diff
 EXTRA_MODEL_ALLOW = {'astral_guide'}
+# 创意标签闭环的**基线一致**白名单：fabric 的 ModCreativeTabs 只有 EFFECT_CARD_REPULSION、
+# 没有 ModItems.REPULSION，而 neoforge-1.21.1(:87) / forge-1.20.1(:88) 也同样只有它 ⇒ 三方一致。
+#   ⚠️ 若基线将来补齐（三条线都把 REPULSION 放进创造栏），这里要同步删掉，否则会漏报回归。
+CREATIVE_TAB_BASELINE_OMISSIONS = {'repulsion'}
-    missing = sorted(ids - covered)
+    uncovered = ids - covered
+    exempt = sorted(uncovered & CREATIVE_TAB_BASELINE_OMISSIONS)
+    missing = sorted(uncovered - CREATIVE_TAB_BASELINE_OMISSIONS)
+    if exempt:
+        print('    已知与基线一致（不阻塞）: %s' % exempt)   # 仍打印，避免白名单=静默掩盖
```

取证：`neoforge-1.21.1/.../init/ModCreativeTabs.java:87`、`forge-1.20.1/.../init/ModCreativeTabs.java:88` 都只有 `output.accept(ModItems.EFFECT_CARD_REPULSION.get())`，无 `ModItems.REPULSION`；fabric `:84` 同（`ModItems.java:1104` / `:1112`）。

## 3. [1][2][3] 误报取证：守门读的是旧 jar

`fabric-1.20.1/build/libs/astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar`：mtime `2026-10-05T11:55:28.910703`、size **1,836,403 B**（同目录 1.3.5-alpha.1 `10-01`、1.3.6-alpha.1 `10-02`、1.3.7-alpha.1 `10-04`）。jar 内 `models/item` **138** / `textures/item` **138** / `blockstates` **0** / `models/block` **0** / lang `zh_cn` **839 键**（现值 968）⇒ 早于本批移植。

源码侧实物：`src/generated/resources/assets/astral_dice/models/item/` **153** json（19 个「缺模型」里只有 `card_shop`/`chip_shop`/`event_drawer` 不在此处，它们本走 blockstate）；`textures/item/` **154** png，16 个「缺贴图」**全在位**；三方块物品 `blockstates/`、`models/block/`、`textures/block/` **三件全在位**（另有 `card_shop_particle.png`、`chip_shop_particle.png`；`event_drawer` 模型只引用 `astral_dice:block/event_drawer`、不引用 particle，故无 `event_drawer_particle.png` 不算缺失）。⇒ **未删/未建任何资源文件**。

## 4. 遗留项

1. **[1][2][3] 需重建 jar**（本次禁 gradle）：建议构建批次跑 `:fabric-1.20.1:build` 后复跑守门。
2. **`event_choice` 两键真缺失（跨批次）**：B2-6 于 18:43–18:44 落地的 `client/EventChoiceScreen.java:82` / `:280` 引用这两键；基线 `forge zh_cn.json:1025`/`:1026`（en_us `:1024`/`:1025`、ja_jp `:1023`/`:1024`）有值，fabric 三语全无。授权为「每语 +1 键」且 `client/` 禁动 ⇒ 未擅扩，建议 B2-6 随手补（3 文件各 +2 键）。
3. **`python scripts/__javac_check.py` 取不到「REAL 4 条」**，见 §5.3。

## 5. 自检原始输出

### 5.1 守门前后（`python tools/verify_fabric_assets.py`，两次均 exit 1）

```
# BEFORE（本批开始时：964 键、7 条 FAIL）
[5] 语言闭环: zh_cn 964 / en_us 964 / ja_jp 964
[7] 创意标签覆盖: 155/156
  ✗ 方块物品资源不全(blockstate=['card_shop','chip_shop','event_drawer'] / model=[同] / texture=[同])
  ✗ 缺模型(紫黑格): [19 项]        ✗ 缺贴图: [16 项]
  ✗ zh_cn 缺 java 引用的键: ['gui.astral_dice.card_choice.title_', 'gui.astral_dice.card_shop.transfer_no_match']
  ✗ en_us / ja_jp 同上              ✗ 不在创造栏的物品: ['repulsion']
# AFTER（968 键；[1]~[4]、[6]、[8] 与 BEFORE 相同，只摘变动。隔离验证：只回退脚本两处时 [5] 仅剩 title_ + event_choice、[7] 仍报 repulsion）
[5] 语言闭环: zh_cn 968 / en_us 968 / ja_jp 968
[7] 创意标签覆盖: 155/156
    已知与基线一致（不阻塞）: ['repulsion']
  ✗ 方块物品资源不全(blockstate=['card_shop','chip_shop','event_drawer'] / model=['card_shop','chip_shop','event_drawer'] / texture=['card_shop','chip_shop','event_drawer'])
  ✗ 缺模型(紫黑格): ['abyss_reagent_chip','alana_sign','ame_sign','card_shop','chip_shop','deepseek_sign','dorothy_sign','effect_card_barricade','effect_card_repulsion','event_drawer','extra_battery_chip','flashlight_burst_chip','fortune_stick_chip','infinite_grid_chip','jill_sign','kangel_sign','lingling_sign','repulsion','sensor_chip']
  ✗ 缺贴图: ['abyss_reagent_chip','alana_sign','ame_sign','deepseek_sign','dorothy_sign','effect_card_barricade','effect_card_repulsion','extra_battery_chip','flashlight_burst_chip','fortune_stick_chip','infinite_grid_chip','jill_sign','kangel_sign','lingling_sign','repulsion','sensor_chip']
  ✗ zh_cn 缺 java 引用的键: ['gui.astral_dice.event_choice.title', 'message.astral_dice.event_choice.esc_warning']
  ✗ en_us / ja_jp 同上
```

### 5.2 `git diff --stat`（4 个文件）与三语 key 数

```
$ git diff --stat -- tools/verify_fabric_assets.py .../lang/*.json
 .../lang/en_us.json | 126 ++++++-   .../lang/ja_jp.json | 126 ++++++-   .../lang/zh_cn.json | 126 ++++++-   tools/verify_fabric_assets.py | 54 ++++-
 4 files changed, 424 insertions(+), 8 deletions(-)
$ python -c "import json;[print(f, len(json.load(open('.../lang/%s.json'%f,encoding='utf-8')))) for f in ('zh_cn','en_us','ja_jp')]"
zh_cn 968 / en_us 968 / ja_jp 968        ← 三者相等
```

**说明**：工作树另有 **322** 条未提交条目（并行批次），这 4 个文件在本批开始前已是 `M`，故 `vs HEAD` 数字绝大部分非本批。本批**增量**经隔离实测：lang 各 **+1 行**（`git diff -U0 | grep -c '^+.*transfer_no_match'` = 1/1/1）、脚本 **+22 / −5 行**（393→411）；未写第 5 个文件。968 = 起点 964 + 并行 B6-JADE 3 键 + 本批 1 键。

### 5.3 `python scripts/__javac_check.py`：本次**取不到**「REAL 4 条」

```
EXIT 4 ERRORS 5000 jars 684 srcs 516      ← 被 -Xmaxerrs 5000 截断；buckets 5000 REAL；REAL by file 5000 F
 408  package net.minecraft.world.entity.player does not exist |    195  package net.minecraft.world.item does not exist |
```

根因（与本批无关）：脚本 classpath 依赖的 `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.20.1-loom.mappings.1_20_1.layered+hash.2198-v2/` **目录为空**（mtime `2026-11-01 18:45`，`ls` 只有 `.` `..`），应有的 `minecraft-merged-….jar` 不在 ⇒ `os.path.exists(mc)` 假 ⇒ classpath 无 MC 类 ⇒ 所有 `net.minecraft.*` 解析失败；同时刻多个 gradle/java 进程在跑，该缓存正被并行构建重建。本批只改 lang 与守门脚本，均不参与 javac。

替代诊断（仅把缺失的 fabric merged jar 换成同版本 Mojmap 合并 jar，其余逻辑一字未改、副本置仓库外）：`EXIT 1 ERRORS 152 jars 685 srcs 516`，buckets = `145 NOISE_AW` + `7 NOISE_IFACE_INJECTION`，**REAL 0** —— 原先 4 条（`EventCardOverlay` / `EventChoiceClientHandler` 缺失）已随 B2-6 落地消失，两文件实测存在：`…/client/EventCardOverlay.java`（16396 B, 18:44:49）、`…/client/EventChoiceClientHandler.java`（4172 B, 18:43:37）。canonical 数字需等 loom 缓存恢复后复跑。