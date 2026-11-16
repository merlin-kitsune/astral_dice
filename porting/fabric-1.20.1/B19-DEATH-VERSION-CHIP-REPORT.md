# B19 — 死亡保留补齐 / 版本门口径 / 摩托头盔筹码 / D8 收紧 / 行尾规整

- 基准线 **forge-1.20.1** → 目标线 **fabric-1.20.1**；对应审计条目 D-13 / D-14 / B15 §5-c / D8 / B15 §5-d。
- 所有改动文件均 CRLF + UTF-8 无 BOM（逐个实测 nl == crlf 且首字节非 EF BB BF）。
- 未触碰：build.gradle、gradle.properties、AstralDiceMod.java、shop/**、event/ModTooltipHandler.java、fabric.mod.json；lang/** 仅第 3 条的 6 行取值。
- 平台差异全部保留：`isClientSide()`、`new ResourceLocation(a,b)`、`compat/curios/CuriosApi`。

## 1 死亡保留补齐（D-13）

forge `component/DeathPreservedBonuses.java` 保存/恢复的**全部**字段 = 8 个 int 槽 + 1 个字符串槽：槽常量组 L38-70（RIN_PAGES=0 / KOMACHI_DAMAGE_BONUS=1 / MAMUSHI_AWAKENING=2 / SHERRY_REASONING_LAYERS=3 / CURSED_SWORD_BONUS=4 / KANGEL_FAN_COUNT=5 / AME_LOVE_LAYERS=6 / AME_LOVE_CAP=7），`PRESERVED` L72-73，`PRESERVED_TARGETS`（kangel_fan_targets，逗号串 UUID）L75-82，`preserveOnDeath` L87-103，`restoreAfterDeath` L105-148（L110 起 int 块、L142-147 字符串块）。
forge 结构特征：两个**独立** null 判、无早退；int 槽逐个「取较大值」回写；字符串槽只在「暂存非空且当前为空」时回写。
fabric 改前：86 行，只有槽 0..4，且用 `if (preserved == null) return;` 早退 ⇒ Kangel 扇计数/目标、Ame 爱层数/上限四项丢失。
fabric 改后（`component/DeathPreservedBonuses.java`）：槽常量 L43-76、`PRESERVED` L79、`PRESERVED_TARGETS` L88、`preserveOnDeath` L94-109、`restoreAfterDeath` L112-155（int 块 L116-147、字符串块 L150-154）。取值顺序与 forge 逐槽一致。

**附件键核查（先 grep 再动手）**：fabric 侧四个键**已注册**，位置在 `component/SignAttachments.java`（不是 ModAttachments）：KANGEL_FAN_COUNT L79-80（`Codec.INT` / 0 / `.sync().copyOnDeath()`）、KANGEL_FAN_TARGETS L88（`Codec.STRING` / `""` / `.copyOnDeath()` 不 sync，与 forge 同属性）、AME_LOVE_LAYERS L112（`Codec.INT` / 0 / `.sync().copyOnDeath()`）、AME_LOVE_CAP L115-116（`Codec.INT` / 4 / `.sync().copyOnDeath()`）。
⇒ 无需新增键；`SYNCED_KEYS` 名单 L357-359 早已含 KANGEL_FAN_COUNT / AME_LOVE_LAYERS / AME_LOVE_CAP。**未改 AstralDiceMod.java，未新增任何注册**。
存取口差异（移植适配）：forge 在 `ModAttachments` L1951/L1971/L1988/L2004；fabric 是 `SignAttachments` 静态方法 L90/L94/L98/L102/L122/L126/L130/L134 ⇒ 槽 5..7 与字符串槽走 `SignAttachments.*`，槽 0..4 仍走 `ModAttachments.*`。
「保留而非常驻」三段时序**已存在、未改动**：`event/PlayerLifecycleHandler.java` L41（LivingDeathEvent，LOWEST，`isCanceled()` 之后）、L130（Clone + `isWasDeath()`，LOWEST）、L180（PlayerRespawnEvent 幂等兜底）——先取走即删（保留），再回写（恢复），表项被取走后重复调用自然空操作（清标记）。
静态表仍必要的理由：`KangelSignItem.java:375`、`AmeSignItem.java:395` 覆写 `clearSignData`（卸下即清），`.copyOnDeath()` 兜不住「死亡清数据」这一步。

## 2 版本门判定口径对齐（D-14）

| 元素 | forge 行号 | fabric 行号（改后） |
|---|---|---|
| SNAPSHOT_PREFIX | L56 | L55 |
| INTEROP_VERSION | L58 | L57 |
| accepts | L85-93 | L84-92 |
| isSnapshot | L96-98 | L95-97 |
| interopNumberOf | L106-115 | L105-114 |
| majorMinor | L124-145 | 保留原实现（语义未改） |

口径（与 forge 逐句一致）：
```java
if (isSnapshot(FULL_VERSION) || isSnapshot(remoteVersion)) {
    return interopNumberOf(FULL_VERSION).equals(interopNumberOf(remoteVersion));
}
return INTEROP_VERSION.equals(majorMinor(remoteVersion));
```
`isSnapshot` = `toUpperCase(Locale.ROOT).contains("SNAPSHOT")`；`interopNumberOf` 对已是 `SNAPSHOT:` 前缀的入参幂等（`regionMatches(true,...)`），否则 SNAPSHOT → `SNAPSHOT:<完整串>`、正式版 → `major.minor`。类头 L14 与 `accepts` javadoc L81-82 已写明「与 forge 同口径」并列出两条规则（原先只有 major.minor 一条）。
其他版本比较点核查：全 `src/main/java` 只有 `network/ModNetwork.java` L32 `PROTOCOL_VERSION = VersionGate.interopVersion()`、L35 `newSimpleChannel(..., VersionGate::accepts, VersionGate::accepts)` 引用；`AstralDiceMod` 内 `version` 命中均为 config_version 重写，与版本门无关；`fabric.mod.json` 依赖声明**未改**。
**BLOCKED（平台能力缺口，不发明）**：`platform/network/NetworkRegistry.java` L18-22 的 `newSimpleChannel(ResourceLocation, Supplier<String>, Predicate, Predicate)` 直接 `return new SimpleChannel(name);` —— 版本供给与两个判定谓词被**丢弃**，且 `SimpleChannel` 无版本字段。故 `VersionGate.accepts(...)` 目前**没有运行时调用点**，本次严格化只落在判定口径本身；要真正生效需自建握手包桥（登录期自定义 payload 交换互通号 + 不一致即断开），本批未实现。旁证：`ModNetwork.java` L27-31 javadoc 仍写「FML 登录握手会交换本通道版本号」（fabric 不成立，属预存在陈旧注释，未改）。

## 3 摩托头盔筹码数值对齐（B15 §5-c）

| 位置 | forge | fabric 改前 | fabric 改后 |
|---|---|---|---|
| `MotoHelmetChipItem.java` DEFENSE_HIGH | L27 = 8 | L27 = 6 | L27 = 8 |
| `MotoHelmetChipItem.java` TOUGHNESS_BONUS | L29 = 0 | L29 = 2 | L29 = 0 |
| 类 javadoc | L12 `+2/+4/+8` | L12 `+2/+4/+6` | L12 `+2/+4/+8` |

`DEFENSE_LOW=2` / `DEFENSE_MEDIUM=4` / `ARMOR_PER_DEFENSE` 未动；`ModItems` 接线无需改（forge L683-698 / fabric L635-650 传 `(DEFENSE_HIGH, TOUGHNESS_BONUS)`）。
文案仅存于 lang，三语各 2 键，取值逐字照 forge：

| 文件 | forge 行 / 值 | fabric 行 | fabric 改前 → 改后 |
|---|---|---|---|
| zh_cn.json | L163 `防御力 +8。` / L614 `防御力 §e+8§7` | L128 / L560 | `…+6，盔甲韧性 +2。` → `防御力 +8。`；`…§e+6§7，盔甲韧性 §e+2§r` → `防御力 §e+8§7` |
| en_us.json | L163 `Defense +8.` / L614 `Defense §e+8§7` | L128 / L560 | `Defense +6, armor toughness +2.` → `Defense +8.`；`Defense §e+6§7, Armor Toughness §e+2§r` → `Defense §e+8§7` |
| ja_jp.json | L163 `防御力 +8。` / L613 `防御力 §e+8§7` | L128 / L560 | `防御力 +6、防具強度 +2。` → `防御力 +8。`；`防御力 §e+6§7、防具強度 §e+2§r` → `防御力 §e+8§7` |

forge 的 lang **也写 +8**（与 forge java 的 8 一致）⇒ 不存在「forge lang 落后 forge java」需要单列；fabric 独有的「盔甲韧性」子句按 forge 原文剔除（`TOUGHNESS_BONUS=0` 后描述不应再有韧性）。改后三语各 984 行、全 CRLF、JSON 解析通过。
残留扫描（均无关、未动）：`盔甲韧性` 其余命中属青之诅咒 zh_cn L57/L226/L543（与 forge zh_cn L72/L258/L591 完全一致）；`+6` 命中属 full_power（L34/L514）与 revenge_halberd（L153/L154/L566）；ja_jp 多一条 L43 为战斗骰公式文本。

## 4 守门 D8 收紧（用户裁决：收紧）

`tools/verify_defense_unit_parity.py`：新增 `D8_ENFORCED = (neoforge-1.21.1, forge-1.20.1, fabric-1.20.1)` 与 `D8_WARN_ONLY = (neoforge-26.1.2,)`；`report_d8()` 改为逐线统计 + 命中时附带 `文件:行: 源码`，非 P1 线命中 > 0 即 `record(..., False, ...)` → GATE FAIL，26.1.2 只进 `warnings` 并打印 `WARN:`；docstring D8 段同步改写为「三线判红 / 26.1.2 只统计」。另将本工具文件本身转为 CRLF（sha256 `4af1a48c…`→`bd13224a…`，`\r\n→\n` 归一化内容 sha256 不变）。
与 D1 的关系：D1 已用同一组字面量扫 P0 两线，D8 对 neoforge-1.21.1 / forge-1.20.1 属**重叠登记**（判据独立，互不冲突）。

三次运行（① 改完即跑 → ② 临时插违规行 → ③ 删掉后复跑）：
```
# ① python tools/verify_defense_unit_parity.py   → EXIT=0
[PASS] D8[neoforge-1.21.1]  旧折算字面量 0 处
[PASS] D8[forge-1.20.1]  旧折算字面量 0 处
[PASS] D8[fabric-1.20.1]  旧折算字面量 0 处
[PASS] D8[neoforge-26.1.2]  WARN 只统计：旧折算命中 1 处（P1 待修，不计入 GATE）
WARN: D8[neoforge-26.1.2] P1 线待修（旧折算命中 1 处）—— 只统计不判 FAIL，须登记在 docs/compat-1.5.0-porting.md
GATE: PASS（44 项全部满足）

# ② 在 fabric MotoHelmetChipItem 构造函数内临时插一行 double x = defensePoints * 2.0;  → EXIT=1
[FAIL] D8[fabric-1.20.1]  1 处旧折算(×2.0 口径，应为系数倒数): fabric-1.20.1\src\main\java\com\merlinkitsune\astral_dice\item\chip\MotoHelmetChipItem.java:38: double x = defensePoints * 2.0;
[PASS] D8[neoforge-26.1.2]  WARN 只统计：旧折算命中 1 处（P1 待修，不计入 GATE）
GATE: FAIL（44 项中 1 项不满足）
  - D8[fabric-1.20.1]: 1 处旧折算(×2.0 口径，应为系数倒数): …\item\chip\MotoHelmetChipItem.java:38: double x = defensePoints * 2.0;

# ③ 删掉临时行后复跑（sha256 回到 cc7b9c69…a06a0）  → EXIT=0
[PASS] D8[neoforge-1.21.1]  旧折算字面量 0 处
[PASS] D8[forge-1.20.1]  旧折算字面量 0 处
[PASS] D8[fabric-1.20.1]  旧折算字面量 0 处
[PASS] D8[neoforge-26.1.2]  WARN 只统计：旧折算命中 1 处（P1 待修，不计入 GATE）
WARN: D8[neoforge-26.1.2] P1 线待修（旧折算命中 1 处）—— 只统计不判 FAIL，须登记在 docs/compat-1.5.0-porting.md
GATE: PASS（44 项全部满足）
```
三次运行除上述 D8 行与 FAIL 段外，其余 40 行（D1/D2/D3/D4/D5/D6/D7/D9 与数值自检表）逐字相同；26.1.2 始终只统计、从未把 GATE 弄红。

## 5 行尾规整（B15 §5-d）

实测：`fabric-1.20.1` 全树（跳过 build/.gradle/run/bin/out）仍有 **29** 个裸 LF 文本文件，其中 **27 个为源文件**（17 个 `src/main/java` + 10 个 `src/generated` 模型 json，全部未跟踪新文件）。已全部按 `\r?\n → \r\n` 转换，零 BOM、零内容变更（归一化 sha256 逐个相等）。跳过 2 个并单列：`gradle.properties`（本批禁改，nl=94 全裸 LF）、`__dsh_probe.txt`（1 行、非源码探针残留）。
与 B15 原文「20 个」的核对差异：B15 只给数字未列举，当前实测 27（较其快照多了 `src/generated` 模型等），以实测为准、逐个列明如下。

| # | 文件 | before sha256 | after sha256 | nl/crlf | normEq |
|---|---|---|---|---|---|
| 1 | `src/main/java/com/merlinkitsune/astral_dice/audio/SoundPlayback.java` | `421e10514fff6ad8…` | `c7ca84ed997e967b…` | 52/52 | ✓ |
| 2 | `src/main/java/com/merlinkitsune/astral_dice/component/SignAttachments.java` | `508ed2f66a562a2b…` | `db2478ec562fdbbd…` | 386/386 | ✓ |
| 3 | `src/main/java/com/merlinkitsune/astral_dice/component/SignState.java` | `c03c1507d1be7370…` | `2ce8e1200e00a53b…` | 127/127 | ✓ |
| 4 | `src/main/java/com/merlinkitsune/astral_dice/component/SignTimerSourceAdapter.java` | `cdee18f8896b5837…` | `7ae4c97f05fe69b7…` | 87/87 | ✓ |
| 5 | `src/main/java/com/merlinkitsune/astral_dice/event/LinglingSignEvents.java` | `ce2919272a262f3b…` | `380ec2338adfd260…` | 82/82 | ✓ |
| 6 | `src/main/java/com/merlinkitsune/astral_dice/event/NewSignEvents.java` | `607fc9f04485be61…` | `1739953aff625d45…` | 124/124 | ✓ |
| 7 | `src/main/java/com/merlinkitsune/astral_dice/item/sign/AlanaSignItem.java` | `b9ba4f912d4ea6b5…` | `973f6a5d157ee25e…` | 100/100 | ✓ |
| 8 | `src/main/java/com/merlinkitsune/astral_dice/item/sign/KangelSignItem.java` | `701207e82f353904…` | `c51a77e17cefa0f3…` | 384/384 | ✓ |
| 9 | `src/main/java/com/merlinkitsune/astral_dice/item/sign/LinglingSignItem.java` | `9a13137d7fd283a3…` | `6897cf76b7ecddbb…` | 92/92 | ✓ |
| 10 | `src/main/java/com/merlinkitsune/astral_dice/item/sign/LinglingSignManager.java` | `326640bcdf3807a7…` | `8ef59776a06a90ca…` | 426/426 | ✓ |
| 11 | `src/main/java/com/merlinkitsune/astral_dice/item/sign/SignActionBar.java` | `d735de424e3af5cc…` | `42e86c5576ef11a8…` | 62/62 | ✓ |
| 12 | `src/main/java/com/merlinkitsune/astral_dice/item/sign/SignAttributeUtil.java` | `8e971d7fc2a43390…` | `cb3ce416e34d6c65…` | 51/51 | ✓ |
| 13 | `src/main/java/com/merlinkitsune/astral_dice/item/sign/SignEquipCheck.java` | `22b87718cd876168…` | `e5a1699f2f26e9cc…` | 42/42 | ✓ |
| 14 | `src/main/java/com/merlinkitsune/astral_dice/item/sign/SignHookDispatcher.java` | `3e7fb0b29f7b6b07…` | `561d128338eeb68f…` | 189/189 | ✓ |
| 15 | `src/main/java/com/merlinkitsune/astral_dice/item/sign/SignHooks.java` | `345e4d7dadeed7e8…` | `9d63cbbadc21cbbd…` | 73/73 | ✓ |
| 16 | `src/main/java/com/merlinkitsune/astral_dice/mixin/client/EntityRendererInvoker.java` | `731229a765c1592e…` | `6aac926a2daed5b2…` | 20/20 | ✓ |
| 17 | `src/main/java/com/merlinkitsune/astral_dice/mixin/client/SignTranslucencyMixin.java` | `0ae67bc11fa53275…` | `b94403f5c56abf2a…` | 88/88 | ✓ |
| 18 | `src/generated/resources/assets/astral_dice/models/item/alana_sign.json` | `4373632e6f527ca2…` | `71558efa0b64c7a0…` | 5/5 | ✓ |
| 19 | `src/generated/resources/assets/astral_dice/models/item/ame_sign.json` | `076b9dd4874a0587…` | `735b9a98c248f670…` | 5/5 | ✓ |
| 20 | `src/generated/resources/assets/astral_dice/models/item/deepseek_sign.json` | `d1ab4518c650f256…` | `d1303e5cf2cd1f13…` | 5/5 | ✓ |
| 21 | `src/generated/resources/assets/astral_dice/models/item/dorothy_sign.json` | `88d570fc57cfe894…` | `4340e7f043ca380d…` | 5/5 | ✓ |
| 22 | `src/generated/resources/assets/astral_dice/models/item/effect_card_barricade.json` | `f6d94b1c18fb8154…` | `878d86f6f7aff878…` | 5/5 | ✓ |
| 23 | `src/generated/resources/assets/astral_dice/models/item/effect_card_repulsion.json` | `f9ee718b7afda55e…` | `f6cc3e990ca729bb…` | 5/5 | ✓ |
| 24 | `src/generated/resources/assets/astral_dice/models/item/jill_sign.json` | `e9a25c15a6ad3a03…` | `389558df2a3d4f87…` | 5/5 | ✓ |
| 25 | `src/generated/resources/assets/astral_dice/models/item/kangel_sign.json` | `24d29f8d3d08737f…` | `50522a7c21d99bbe…` | 5/5 | ✓ |
| 26 | `src/generated/resources/assets/astral_dice/models/item/lingling_sign.json` | `2de8becad7be6a48…` | `b0afe68a5c628593…` | 5/5 | ✓ |
| 27 | `src/generated/resources/assets/astral_dice/models/item/repulsion.json` | `dd77272ef772613e…` | `f44d758214671605…` | 5/5 | ✓ |

表中 sha256 取前 16 位；完整 64 位对照存于 `porting/fabric-1.20.1/.b19-crlf-table.json`（同一脚本生成，normEq 全 true）。转换后复扫：全树只剩 `gradle.properties` 与 `__dsh_probe.txt` 两个裸 LF 文件。

## 6 自检输出（原始）

`python scripts/__javac_check.py`：
```
EXIT 1 ERRORS 171 jars 685 srcs 522
=== buckets (NOISE_* = 由 Loom AW / 接口注入 提供，javac 侧无法复现) ===
  164  NOISE_AW
    7  NOISE_IFACE_INJECTION
=== REAL errors ===
=== REAL by file ===
saved errors.txt lines= 984
```
REAL errors 为空（171 条全部归入 Loom AW / 接口注入噪声）。

`python tools/check_lang_sync.py --line fabric-1.20.1`：`[OK]   fabric-1.20.1: en_us=980 …`（三语键集一致），EXIT=0。
`python tools/verify_defense_unit_parity.py`：见第 4 节运行③（GATE: PASS 44 项，EXIT=0）。
`python tools/verify_fabric_assets.py`：**FAIL，但为预存在状态，与本批无关** —— 失败项全部在手册侧的：`手册引用了不存在的配方` 9 条（alana_sign / ame_sign / card_shop / chip_shop / dorothy_sign / event_drawer / jill_sign / kangel_sign / lingling_sign）、`手册引用但 lang 缺键` 25 条（battle_system_changes.*、card_shop.*、integration.* 等）。证据：这些键在 **HEAD 版 lang 里同样缺失**（`git show HEAD:…zh_cn.json | grep -c battle_system_changes` = 0），而本批只改两个 moto 键的**取值**（moto_helmet_high 出现次数 HEAD=3 / 现在=3，键未丢）。本批未触碰 `patchouli_books/**`、配方注册与这些键。

## 7 遗留 / BLOCKED

- **BLOCKED-1（第 2 条）**：fabric 无 handshake 通道版本交换（`NetworkRegistry.newSimpleChannel` 丢弃谓词，`SimpleChannel` 无版本字段），`VersionGate.accepts` 无运行时调用点。所需桥：登录期自定义 payload 交换互通号 + 不一致即断开；本批未实现（不发明）。
- **待他人处理**：`verify_fabric_assets.py` 的手册/配方 FAIL（预存在）、`gradle.properties` 裸 LF（禁改）、`__dsh_probe.txt` 探针残留（非本批）。