# B3-SOUND-REPORT — fabric-1.20.1 补齐 8 条事件音效（event_*）

- 批次：B3-SOUND · 2026-11-01 · 分支 `multi-main` · 蓝本（**只读，零改动**）：`forge-1.20.1` 的 `audio/ModSounds.java`、`assets/astral_dice/sounds.json`、`assets/astral_dice/sounds/*.ogg`
- 结论：8 条**三处齐落**（ogg 文件名 = sounds.json key = SoundEvent id）；`__javac_check.py` 的 REAL 桶里 `variable EVENT_*` 类 `cannot find symbol` **已归零**。剩余 9 条 REAL 全是 `network/Event*Message` 并行批次缺口，本批未触碰 `network/`。

## 1 落盘清单（只动这 3 处）

| # | 路径 | 改动 |
|---|---|---|
| 1 | `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/audio/ModSounds.java` | 追加 8 个 `RegistryObject<SoundEvent>`（逐条 javadoc + 分节注释，沿用本文件风格）；同文件头 javadoc `（15 条）`→`（23 条）` 顺带订正 |
| 2 | `fabric-1.20.1/src/main/resources/assets/astral_dice/sounds/event_*.ogg` | 从 forge 同路径 `cp -p` 拷入 8 个文件，**字节原样**（未重编码，`cmp` 全通） |
| 3 | `fabric-1.20.1/src/main/resources/assets/astral_dice/sounds.json` | 末尾追加 8 条同名条目，**无 volume 字段**（forge 对应条目亦无 volume ⇒ 逐字段一致） |

- 编码/行尾：两文件改后仍 **CRLF + 无 BOM + 末行 CRLF**（§3.4）。键顺序：既有 15 键顺序与内容原样未动，新键追加在 `directional_blast_hit` 之后（§3.2）。
- ModSounds：既有 15 字段与 `register()` 一字未动，新字段插在末个既有字段与 `private ModSounds()` 之间。

## 2 逐项对照表（8 条）

| # | id（ogg = json key = SoundEvent id） | ogg 字节 forge / fabric（`cmp` 通过） | sounds.json 条目（归一化原文，与 forge 逐字段一致） | ModSounds 字段:行号 | 触发语义（取自调用点注释） |
|---|---|---|---|---|---|
| 1 | `event_bonus` | 32448 / 32448 | `"event_bonus": {"sounds": ["astral_dice:event_bonus"]}` | `EVENT_BONUS`:102 | 「天降之物」受影响玩家获得奖励 |
| 2 | `event_damage` | 36220 / 36220 | `"event_damage": {"sounds": ["astral_dice:event_damage"]}` | `EVENT_DAMAGE`:105 | 「天崩地裂」全体／「取长补短」受伤方 |
| 3 | `event_fade_in` | 21881 / 21881 | `"event_fade_in": {"sounds": ["astral_dice:event_fade_in"]}` | `EVENT_FADE_IN`:108 | 发放弹卡包（淡入） |
| 4 | `event_fade_out` | 6531 / 6531 | `"event_fade_out": {"sounds": ["astral_dice:event_fade_out"]}` | `EVENT_FADE_OUT`:111 | 结算淡出（两侧均无调用点，预留） |
| 5 | `event_heal` | 33359 / 33359 | `"event_heal": {"sounds": ["astral_dice:event_heal"]}` | `EVENT_HEAL`:114 | 「天使降临」／「取长补短」治疗方 |
| 6 | `event_poisoning` | 23348 / 23348 | `"event_poisoning": {"sounds": ["astral_dice:event_poisoning"]}` | `EVENT_POISONING`:117 | 「问题外卖」中毒 |
| 7 | `event_tick` | 7105 / 7105 | `"event_tick": {"sounds": ["astral_dice:event_tick"]}` | `EVENT_TICK`:120 | 持续计时（两侧均无调用点，预留） |
| 8 | `event_transport` | 21630 / 21630 | `"event_transport": {"sounds": ["astral_dice:event_transport"]}` | `EVENT_TRANSPORT`:123 | 「拥挤通道」传送 |

- **volume**：forge 这 8 条**都没有** `volume` ⇒ fabric 同样不写（缺省 1.0）。fabric 既有 `coin_bag_withdraw` 的 `"volume": 0.6` 系 2026-09-25 独立裁决，**未动**。
- **结构**：forge 是单行 `{"sounds": ["…"]}`，fabric 既有风格为多行展开 ⇒ 按「保持原文件缩进风格」落成 5 行展开式，字段集合与取值不变。落盘原文（首条，其余 7 条同构，仅 id 不同）：

```json
  "event_bonus": {
    "sounds": [
      "astral_dice:event_bonus"
    ]
  },
```

## 3 自检原始输出（真跑）

### 3.1 ogg 数量 + 逐文件 `cmp`
```
$ ls fabric-1.20.1/src/main/resources/assets/astral_dice/sounds/event_*.ogg | wc -l
8
OK  event_bonus.ogg  bytes=32448      OK  event_heal.ogg  bytes=33359
OK  event_damage.ogg  bytes=36220     OK  event_poisoning.ogg  bytes=23348
OK  event_fade_in.ogg  bytes=21881    OK  event_tick.ogg  bytes=7105
OK  event_fade_out.ogg  bytes=6531    OK  event_transport.ogg  bytes=21630
（8 条 cmp 全 0 退出，即与 forge 同名文件逐字节相同）
```

### 3.2 sounds.json 键
```
$ python -c "import json;d=json.load(open('fabric-1.20.1/src/main/resources/assets/astral_dice/sounds.json',encoding='utf-8'));print(len(d));print([k for k in d if k.startswith('event_')])"
23
['event_bonus', 'event_damage', 'event_fade_in', 'event_fade_out', 'event_heal', 'event_poisoning', 'event_tick', 'event_transport']
```
既有键顺序（未重排，前 15 键）：`["wallet_deposit","coin_withdraw","coin_bag_withdraw","damage_effect_card_hit","damage_effect_card_bighit","boost_effect_card_use","effect_card_use","skill_use","glass_dice_broken","megas_blast","damage_effect_card_cast","monster_laser_hit","monster_brick_hit","orbital_strike_hit","directional_blast_hit"]` ⇒ 与改前一致。

### 3.3 `python tools/verify_fabric_assets.py`（音效闭环项）
```
jar = fabric-1.20.1\build\libs\astral_dice-1.3.7-alpha.2+fabric_1.20.1.jar
[3] 音效闭环: 注册 23 / sounds.json 23 / ogg 23
=== FAIL ===
  ✗ 方块物品资源不全(blockstate/model/texture = ['card_shop','chip_shop','event_drawer'])
  ✗ 缺模型(紫黑格): 19 项 · ✗ 缺贴图: 16 项 · ✗ 不在创造栏的物品: ['repulsion']
  ✗ zh_cn/en_us/ja_jp 缺 java 引用的键: ['gui.astral_dice.card_choice.title_','gui.astral_dice.card_shop.transfer_no_match']
```
- **音效闭环 PASS**：注册 23 = sounds.json 23 = ogg 23，三路互含、无「仅左／仅右」差异，**未进 FAIL 列表**。
- 脚本能跑全（未因缺 jar 中断）：物品环读 `build/libs` 最新 jar，而 `check_sounds()` 走**源码** `ModSounds.java` + `sounds.json` + `sounds/*.ogg` 三路比对、**不读 jar** ⇒ 本批结论不受该 jar（早于本批）影响。其余 FAIL 全属并行批次（方块模型/贴图、lang 键、创造栏），本批未处置。

### 3.4 行尾 / BOM（node 字节级复核）
```
fabric-…/audio/ModSounds.java          bytes=8199 CRLF=134 LF=134 bare_LF=0 BOM=false endsWithCRLF=true
fabric-…/assets/astral_dice/sounds.json bytes=2153 CRLF=117 LF=117 bare_LF=0 BOM=false endsWithCRLF=true
（改前：ModSounds 6538B/108 行；sounds.json 1465B/77 行；均纯 CRLF、无 BOM）
```

### 3.5 `python scripts/__javac_check.py`
```
EXIT 1 ERRORS 179 jars 674 srcs 496
  163  NOISE_AW     9  REAL     7  NOISE_IFACE_INJECTION
=== REAL errors ===
  3  cannot find symbol | symbol: class EventChoiceCloseMessage  location: class EventChoiceManager
  1  cannot find symbol | symbol: class EventChoiceCloseMessage  location: package com.merlinkitsune.astr
  1  cannot find symbol | symbol: class EventChoiceStartMessage  location: package com.merlinkitsune.astr
  1  cannot find symbol | symbol: class EventChoiceTickMessage   location: package com.merlinkitsune.astra
  1  cannot find symbol | symbol: class EventCardMessage         location: package com.merlinkitsune.astral_dice
  1  cannot find symbol | symbol: class EventChoiceStartMessage  location: class EventChoiceManager
  1  cannot find symbol | symbol: class EventChoiceTickMessage   location: class EventChoiceManager
$ grep -c 'EVENT_'   fabric-1.20.1/build/javac-check/errors.txt  → 0
$ grep -c 'ModSounds' fabric-1.20.1/build/javac-check/errors.txt  → 0
```
⇒ **目标达成**：REAL 桶里不再有 `variable EVENT_*` / `ModSounds` 相关 `cannot find symbol`；剩 9 条全是 `network.Event*Message`（6 个类）缺失，属并行批次，按要求未动 `network/`。

### 3.6 禁词与改动面
```
$ grep -c 'net.minecraftforge\|net.neoforged' <两处被改文件>        → 0 / 0
$ git status --porcelain -- forge-1.20.1/…                          → 空（蓝本零改动）
$ git status --porcelain -- fabric-…/audio/ fabric-…/sounds.json fabric-…/sounds/
 M …/audio/ModSounds.java        （SoundPlayback.java 亦为 M，属并行批次既有改动，本批未动）
 M …/assets/astral_dice/sounds.json
?? …/sounds/event_{bonus,damage,fade_in,fade_out,heal,poisoning,tick,transport}.ogg  （8 个新文件）
```

## 4 未做项 / 遗留

1. **未编译、未打包、未进游戏**（禁令：禁 gradle、禁启动游戏）。`__javac_check.py` 是本批唯一编译级判据，但它用裸 `javac` + Loom 产物自建 classpath，**不含** Loom 的 Access Widener／接口注入 ⇒ 163+7 条 NOISE_* 属该工具已知限制，不代表真实构建状态。
2. `EVENT_FADE_OUT` / `EVENT_TICK` **目前两侧均无调用点**（forge 全仓 grep 仅命中 ModSounds 定义处）。按「与蓝本一致」登记，**未**擅自接到任何 tick／淡出逻辑——那是玩法决策。
3. **未加字幕键**（`subtitles.astral_dice.event_*`）：forge 也没有，两侧一致缺，不在本批范围。
4. `verify_fabric_assets.py` 的其它 FAIL（方块模型/贴图、3 个 lang 键、创造栏 `repulsion`）属并行批次，未处置。

## 5 蓝本瑕疵记录（照抄事实，未改蓝本）

- `forge-1.20.1/…/audio/ModSounds.java` L103–112：8 个字段被插在 `register()` 的 javadoc 与 `register()` 方法体**之间**，且 L113 `private static RegistryObject<SoundEvent> register(...)` 顶格（缩进丢失）⇒ 该 javadoc 实际挂到了新字段上。
- fabric 侧**未复刻**该形态：字段插在既有字段之后、`private ModSounds()` 之前，缩进与 javadoc 归属正常；字段名、id、顺序与蓝本一致。

**给后续批次**：新增可播放 id 8 条（均无 volume）＝ `astral_dice:event_{bonus,damage,fade_in,fade_out,heal,poisoning,tick,transport}`；`fabric-…/event/EventCardService.java` 既有 6 处 `ModSounds.EVENT_*.get()`（L80/140/141/143/144/145/146/147）**现可编译**，`EVENT_FADE_OUT`／`EVENT_TICK` 仍无调用方。
